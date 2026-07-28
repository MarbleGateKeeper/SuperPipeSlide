package dev.marblegate.superpipeslide.client.core.navigation;

import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import dev.marblegate.superpipeslide.common.core.geometry.SlideGeometry;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationTransferLink;
import dev.marblegate.superpipeslide.common.core.route.service.RouteLayoutNavigator;
import dev.marblegate.superpipeslide.common.core.slide.ResolvedPipeSpeedRules;
import dev.marblegate.superpipeslide.config.Config;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Route-planning half of client navigation, extracted from {@link ClientNavigationController}:
 * builds the navigation graph from the synced route/pipe caches, searches it for the
 * cheapest path to a destination station, compresses the path into ride segments with
 * transfer and final-walk instructions, and answers destination-search queries for the
 * full-route-map screen. All methods must be called on the client main thread; the graph
 * and reachability caches are keyed by the client data-cache revisions and are dropped
 * via {@link #clearCache()} when the controller is cleared.
 */
final class NavigationPlanner {
    static final double WALK_TICKS_PER_BLOCK = 8.0D;
    private static final double MIN_TRANSFER_WALK_TICKS = 40.0D;
    private static final double BOARDING_PENALTY_TICKS = 4.0D * 20.0D;
    /** Ride speed assumed when a section's connections (or their attributes) are not available on the client. */
    private static final double FALLBACK_RIDE_SPEED = 0.30D;
    private static final double SEARCH_COST_EPSILON = 1.0E-6D;
    /**
     * Compact pinyin-initial groups for destination search (see pinyinInitials):
     * '|'-separated groups, each starting with the lowercase initial letter followed
     * by the hanzi it covers. Coverage is limited to common station-name characters
     * on purpose; multi-pronunciation characters map to their most common reading
     * only. Kept as one literal so the Spotless array-initializer layout is not in
     * play.
     */
    private static final String PINYIN_INITIAL_GROUPS = "a阿啊安岸暗昂奥澳|b八巴白百柏班板半邦包宝保堡贝北奔本碧壁边滨冰兵波博布不步|c才彩苍草层茶长常城池赤冲川船春磁村翠车场|d大丹岛道德灯地东洞都杜渡多|e鹅峨恩二|f法凡方飞丰风枫峰凤福富|g干岗港高歌阁工公宫古谷观光辉桂国广馆|h海寒汉航河和鹤黑红湖虎花华黄火货|j机鸡基吉急家江角街金京晶九巨军|k卡开康空口库快矿昆|l拉兰浪老乐冷黎里丽利连林灵龙楼鹿路绿罗|m马麦满梅美门梦米密苗庙明摩木牧码|n南泥内农暖女|o欧偶|p帕盘平坡浦普瀑|q七奇旗前桥青清秋泉全|r人日荣融瑞|s三沙山商上深神石市双水寺松苏速|t塔台太天田铁厅通土团头堂|w瓦外万王望围文乌五雾湾|x西溪下夏仙香小新星雪学|y亚岩阳洋叶一银樱永雨玉园原月云央|z站张镇中终珠竹主庄紫左";
    private static final Map<Character, Character> PINYIN_INITIALS = buildPinyinInitials();
    @Nullable
    private static NavigationGraph cachedGraph;
    @Nullable
    private static ReachabilityCache cachedReachability;
    private static long cachedRouteRevision = Long.MIN_VALUE;
    private static long cachedPipeRevision = Long.MIN_VALUE;

    private NavigationPlanner() {}

    static void clearCache() {
        cachedGraph = null;
        cachedReachability = null;
        cachedRouteRevision = Long.MIN_VALUE;
        cachedPipeRevision = Long.MIN_VALUE;
    }

    static List<ClientNavigationController.DestinationSearchResult> searchDestinations(LocalPlayer player, String query, int limit) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Vec3 playerPosition = player.position();
        ResourceKey<Level> playerLevel = player.level().dimension();
        Set<UUID> reachableStations = reachableStationGroups(player);
        return ClientRouteDataCache.stationGroups().stream()
                .map(station -> destinationResult(playerLevel, playerPosition, station, normalized, reachableStations.contains(station.id())))
                .filter(result -> normalized.isBlank() || result.matchScore() > 0)
                .sorted(Comparator
                        .comparingInt(ClientNavigationController.DestinationSearchResult::matchScore).reversed()
                        .thenComparingInt(result -> result.reachable() ? 0 : 1)
                        .thenComparingDouble(ClientNavigationController.DestinationSearchResult::distanceBlocks)
                        .thenComparing(result -> result.primaryName().toLowerCase(Locale.ROOT)))
                .limit(limit)
                .toList();
    }

    static Optional<ClientNavigationController.NavigationPlan> buildPlan(LocalPlayer player, UUID destinationStationGroupId) {
        NavigationGraph graph = graph();
        List<PlatformStop> destinationStops = ClientRouteDataCache.platformStopsInStation(destinationStationGroupId);
        Set<UUID> destinationStopIds = new HashSet<>();
        destinationStops.forEach(stop -> destinationStopIds.add(stop.id()));
        AccessDistances accessDistances = new AccessDistances(player.position());
        boolean allowDestinationAsStart = ClientNavigationController.isPhysicallyAtDestination(player, destinationStationGroupId);

        List<PlatformStop> preferredStarts = preferredStartCandidates(player, destinationStationGroupId, accessDistances, allowDestinationAsStart);
        CandidatePlan best = bestCandidatePlan(graph, preferredStarts, destinationStopIds, destinationStationGroupId, accessDistances);
        if (best == null) {
            best = bestCandidatePlan(graph, fallbackStartCandidates(player, destinationStationGroupId, accessDistances, allowDestinationAsStart), destinationStopIds, destinationStationGroupId, accessDistances);
        }
        if (best == null) {
            return Optional.empty();
        }

        List<ClientNavigationController.NavigationSegment> segments = compressSegments(best.search().edges());
        if (segments.isEmpty() && !allowDestinationAsStart) {
            return Optional.empty();
        }
        UUID boardingPlatformStopId = segments.isEmpty() ? best.start().id() : segments.getFirst().boardingPlatformStopId();
        StationGroup startStation = ClientRouteDataCache.platformStop(boardingPlatformStopId)
                .flatMap(stop -> ClientRouteDataCache.stationGroup(stop.stationGroupId()))
                .orElse(null);
        if (startStation == null) {
            return Optional.empty();
        }
        int estimatedTicks = (int) Math.round(best.cost());
        int sameStationTransferCount = 0;
        int outOfStationTransferCount = 0;
        int crossDimensionTransferCount = 0;
        boolean finalWalk = false;
        boolean crossDimensionFinalWalk = false;
        for (ClientNavigationController.NavigationSegment segment : segments) {
            if (segment.transferInstruction().isPresent()) {
                switch (segment.transferInstruction().get().kind()) {
                    case SAME_STATION -> sameStationTransferCount++;
                    case OUT_OF_STATION -> outOfStationTransferCount++;
                    case CROSS_DIMENSION_OUT_OF_STATION -> crossDimensionTransferCount++;
                }
            }
            if (segment.finalWalkInstruction().isPresent()) {
                finalWalk = true;
                crossDimensionFinalWalk = segment.finalWalkInstruction().get().kind() == ClientNavigationController.TransferKind.CROSS_DIMENSION_OUT_OF_STATION;
            }
        }
        int transferCount = sameStationTransferCount + outOfStationTransferCount + crossDimensionTransferCount;
        List<Integer> primaryColors = segments.isEmpty() ? List.of(0xFF47A6FF) : segments.getFirst().colors();
        return Optional.of(new ClientNavigationController.NavigationPlan(
                UUID.randomUUID(),
                ClientRouteDataCache.revision(),
                ClientPipeNetworkCache.aggregateRevision(),
                startStation.id(),
                destinationStationGroupId,
                best.start().id(),
                segments,
                estimatedTicks,
                transferCount,
                sameStationTransferCount,
                outOfStationTransferCount,
                crossDimensionTransferCount,
                finalWalk,
                crossDimensionFinalWalk,
                best.walkDistance(),
                primaryColors));
    }

    @Nullable
    private static CandidatePlan bestCandidatePlan(NavigationGraph graph, List<PlatformStop> candidates, Set<UUID> destinationStopIds, UUID destinationStationGroupId, AccessDistances accessDistances) {
        if (candidates.isEmpty()) {
            return null;
        }
        SearchResult search = solve(graph, candidates, destinationStopIds, destinationStationGroupId, accessDistances);
        if (search.start().isEmpty()) {
            return null;
        }
        double walk = accessDistances.platformDistance(search.start().get());
        return new CandidatePlan(search.start().get(), search, search.cost(), walk);
    }

    private static List<PlatformStop> preferredStartCandidates(LocalPlayer player, UUID destinationStationGroupId, AccessDistances accessDistances, boolean allowDestinationAsStart) {
        ResourceKey<Level> level = player.level().dimension();
        LinkedHashMap<UUID, PlatformStop> nearbyDestinationStops = new LinkedHashMap<>();
        if (ClientRouteDataCache.stationGroup(destinationStationGroupId)
                .filter(station -> station.levelKey().equals(level))
                .filter(station -> allowDestinationAsStart)
                .isPresent()) {
            ClientRouteDataCache.platformStopsInStation(destinationStationGroupId).stream()
                    .sorted(Comparator.comparingDouble(accessDistances::platformDistance))
                    .forEach(stop -> nearbyDestinationStops.put(stop.id(), stop));
            if (!nearbyDestinationStops.isEmpty()) {
                return List.copyOf(nearbyDestinationStops.values());
            }
        }
        LinkedHashMap<UUID, PlatformStop> localStationCandidates = new LinkedHashMap<>();
        ClientRouteDataCache.stationGroups().stream()
                .filter(station -> station.levelKey().equals(level))
                .filter(station -> accessDistances.stationGroupDistance(station.id()) <= ClientNavigationController.BOARDING_LOCAL_RANGE)
                .filter(station -> allowDestinationAsStart || !station.id().equals(destinationStationGroupId))
                .sorted(Comparator.comparingDouble(station -> accessDistances.stationGroupDistance(station.id())))
                .forEach(station -> ClientRouteDataCache.platformStopsInStation(station.id()).stream()
                        .filter(stop -> !routeEdgesFrom(stop.id()).isEmpty())
                        .sorted(Comparator.comparingDouble(accessDistances::platformDistance))
                        .forEach(stop -> localStationCandidates.put(stop.id(), stop)));
        if (!localStationCandidates.isEmpty()) {
            return List.copyOf(localStationCandidates.values());
        }
        return List.of();
    }

    private static List<PlatformStop> fallbackStartCandidates(LocalPlayer player, UUID destinationStationGroupId, AccessDistances accessDistances, boolean allowDestinationAsStart) {
        ResourceKey<Level> level = player.level().dimension();
        LinkedHashMap<UUID, PlatformStop> candidates = new LinkedHashMap<>();
        ClientRouteDataCache.platformStops().stream()
                .filter(stop -> ClientRouteDataCache.stationGroup(stop.stationGroupId()).map(group -> group.levelKey().equals(level)).orElse(false))
                .filter(stop -> allowDestinationAsStart || !stop.stationGroupId().equals(destinationStationGroupId))
                .filter(stop -> !routeEdgesFrom(stop.id()).isEmpty())
                .sorted(Comparator
                        .comparingDouble((PlatformStop stop) -> accessDistances.platformDistance(stop))
                        .thenComparingDouble(stop -> accessDistances.stationGroupDistance(stop.stationGroupId())))
                .forEach(stop -> candidates.put(stop.id(), stop));
        return List.copyOf(candidates.values());
    }

    private static NavigationGraph graph() {
        long routeRevision = ClientRouteDataCache.revision();
        long pipeRevision = ClientPipeNetworkCache.aggregateRevision();
        if (cachedGraph != null && cachedRouteRevision == routeRevision && cachedPipeRevision == pipeRevision) {
            return cachedGraph;
        }
        Map<NodeKey, List<GraphEdge>> edges = new LinkedHashMap<>();
        for (RouteLayout layout : ClientRouteDataCache.routeLayouts()) {
            addLayoutEdges(edges, layout, 1);
            if (layout.bidirectional()) {
                addLayoutEdges(edges, layout, -1);
            }
        }
        Set<UUID> rideConnectedStops = rideConnectedStops(edges);
        addSameStationTransferEdges(edges, rideConnectedStops);
        addConfiguredOutOfStationTransferEdges(edges, rideConnectedStops);
        cachedGraph = new NavigationGraph(edges, rideConnectedStops);
        cachedRouteRevision = routeRevision;
        cachedPipeRevision = pipeRevision;
        return cachedGraph;
    }

    private static Set<UUID> rideConnectedStops(Map<NodeKey, List<GraphEdge>> edges) {
        Set<UUID> result = new HashSet<>();
        for (List<GraphEdge> outgoing : edges.values()) {
            for (GraphEdge edge : outgoing) {
                if (edge.kind() == EdgeKind.RIDE) {
                    result.add(edge.from().id());
                    result.add(edge.to().id());
                }
            }
        }
        return result;
    }

    private static void addSameStationTransferEdges(Map<NodeKey, List<GraphEdge>> edges, Set<UUID> rideConnectedStops) {
        for (StationGroup station : ClientRouteDataCache.stationGroups()) {
            List<PlatformStop> stops = ClientRouteDataCache.platformStopsInStation(station.id());
            ArrayList<PlatformStop> connectedStops = new ArrayList<>();
            for (PlatformStop stop : stops) {
                if (!rideConnectedStops.contains(stop.id())) {
                    continue;
                }
                connectedStops.add(stop);
                edges.computeIfAbsent(NodeKey.platform(stop.id()), ignored -> new ArrayList<>()).add(GraphEdge.stationAccess(stop.id(), station));
            }
            // Same-station transfers go platform to platform directly so the penalty
            // can use the actual distance between the two platforms (with a floor)
            // instead of a flat constant routed through the station transfer node.
            for (PlatformStop from : connectedStops) {
                for (PlatformStop to : connectedStops) {
                    if (from.id().equals(to.id())) {
                        continue;
                    }
                    edges.computeIfAbsent(NodeKey.platform(from.id()), ignored -> new ArrayList<>()).add(GraphEdge.sameStationTransfer(from, to, station, sameStationTransferTicks(from, to)));
                }
            }
        }
    }

    private static double sameStationTransferTicks(PlatformStop from, PlatformStop to) {
        return Math.max(MIN_TRANSFER_WALK_TICKS, platformPosition(from).distanceTo(platformPosition(to)) * WALK_TICKS_PER_BLOCK);
    }

    private static double transferWalkTicks(StationGroup station, PlatformStop stop) {
        double distance = platformPosition(stop).distanceTo(Vec3.atCenterOf(station.stationBlockPos()));
        return Math.max(MIN_TRANSFER_WALK_TICKS, distance * WALK_TICKS_PER_BLOCK);
    }

    private static void addConfiguredOutOfStationTransferEdges(Map<NodeKey, List<GraphEdge>> edges, Set<UUID> rideConnectedStops) {
        for (StationTransferLink link : ClientRouteDataCache.stationTransferLinks()) {
            Optional<StationGroup> firstStation = ClientRouteDataCache.stationGroup(link.firstStationGroupId());
            Optional<StationGroup> secondStation = ClientRouteDataCache.stationGroup(link.secondStationGroupId());
            if (firstStation.isEmpty() || secondStation.isEmpty()) {
                continue;
            }
            ClientNavigationController.TransferKind forwardKind = firstStation.get().levelKey().equals(secondStation.get().levelKey())
                    ? ClientNavigationController.TransferKind.OUT_OF_STATION
                    : ClientNavigationController.TransferKind.CROSS_DIMENSION_OUT_OF_STATION;
            addTransferLinkArrivalEdges(edges, link, forwardKind, firstStation.get(), secondStation.get(), rideConnectedStops);
            addTransferLinkArrivalEdges(edges, link, forwardKind, secondStation.get(), firstStation.get(), rideConnectedStops);
        }
    }

    /**
     * Adds both directions of a transfer link. The station-to-station edge preserves
     * routing towards platform-less arrival stations (and destination arrival at the
     * station node, charged with the plain link walk); the fan-out edges reach every
     * ride-connected platform stop of the arrival station directly, charging the
     * link walk plus the actual on-foot distance from the arrival station block to
     * the target platform (with a floor) instead of a flat re-board penalty.
     */
    private static void addTransferLinkArrivalEdges(Map<NodeKey, List<GraphEdge>> edges, StationTransferLink link, ClientNavigationController.TransferKind kind, StationGroup fromStation, StationGroup toStation, Set<UUID> rideConnectedStops) {
        NodeKey fromNode = NodeKey.stationTransfer(fromStation.id());
        edges.computeIfAbsent(fromNode, ignored -> new ArrayList<>()).add(GraphEdge.stationTransferLink(link.estimatedWalkTicks(), kind, link.id(), fromStation, toStation));
        for (PlatformStop stop : ClientRouteDataCache.platformStopsInStation(toStation.id())) {
            if (!rideConnectedStops.contains(stop.id())) {
                continue;
            }
            double cost = link.estimatedWalkTicks() + transferWalkTicks(toStation, stop);
            edges.computeIfAbsent(fromNode, ignored -> new ArrayList<>()).add(GraphEdge.stationTransfer(cost, kind, link.id(), fromStation, toStation, stop));
        }
    }

    private static void addLayoutEdges(Map<NodeKey, List<GraphEdge>> edges, RouteLayout layout, int direction) {
        double dwellTicks = Config.NAVIGATION_STOP_DWELL_TICKS.getAsInt();
        for (UUID platformStopId : layout.orderedPlatformStops()) {
            RouteLayoutNavigator.nextStep(layout, platformStopId, direction, ClientRouteDataCache::routeSection)
                    .filter(step -> step.section().statusForDirection(direction) == RouteSectionStatus.VALID)
                    .ifPresent(step -> {
                        RouteSection section = step.section();
                        double length = Math.max(1.0D, section.lengthForDirection(direction));
                        double cost = rideCostTicks(section, direction, length) + dwellTicks;
                        List<Integer> colors = ClientRouteDataCache.routeLine(layout.routeLineId())
                                .map(RouteLine::themeColors)
                                .filter(values -> !values.isEmpty())
                                .orElse(List.of(0xFF47A6FF));
                        String lineName = ClientRouteDataCache.routeLine(layout.routeLineId()).map(RouteLine::displayName).orElse("Route");
                        edges.computeIfAbsent(NodeKey.platform(platformStopId), ignored -> new ArrayList<>()).add(GraphEdge.ride(
                                platformStopId,
                                step.nextPlatformStopId(),
                                layout.routeLineId(),
                                layout.id(),
                                direction,
                                section.id(),
                                step.sectionIndex(),
                                cost,
                                colors,
                                lineName));
                    });
        }
    }

    /**
     * Ride time of one section in ticks: per-connection length divided by the
     * connection's actual max speed from its resolved speed rules. Any section
     * length not covered by the synced section path (missing connections or a
     * missing path) falls back to FALLBACK_RIDE_SPEED.
     */
    private static double rideCostTicks(RouteSection section, int direction, double sectionLength) {
        List<PipeConnectionRef> refs = ClientRouteDataCache.routeSectionPath(section.id())
                .map(path -> direction < 0 ? path.reverseConnections() : path.forwardConnections())
                .orElse(List.of());
        double cost = 0.0D;
        double coveredLength = 0.0D;
        for (PipeConnectionRef ref : refs) {
            Optional<PipeConnection> connection = ClientPipeNetworkCache.connection(ref);
            if (connection.isEmpty()) {
                continue;
            }
            double length = connection.get().length();
            double speed = Math.max(0.05D, ResolvedPipeSpeedRules.from(connection.get().resolvedAttributes()).maxSpeed());
            cost += length / speed;
            coveredLength += length;
        }
        double uncoveredLength = Math.max(0.0D, sectionLength - coveredLength);
        return cost + uncoveredLength / FALLBACK_RIDE_SPEED;
    }

    private static List<GraphEdge> routeEdgesFrom(UUID platformStopId) {
        return graph().edgesFrom(NodeKey.platform(platformStopId)).stream().filter(edge -> edge.kind() == EdgeKind.RIDE).toList();
    }

    private static SearchResult solve(NavigationGraph graph, List<PlatformStop> starts, Set<UUID> destinations, UUID destinationStationGroupId, AccessDistances accessDistances) {
        PriorityQueue<SearchNode> open = new PriorityQueue<>();
        Map<SearchState, Double> bestCost = new HashMap<>();
        Map<SearchState, PathBackref> backrefs = new HashMap<>();
        Map<SearchState, PlatformStop> sourceByState = new HashMap<>();
        for (PlatformStop start : starts) {
            SearchState state = new SearchState(NodeKey.platform(start.id()), false);
            double walk = accessDistances.platformDistance(start);
            double cost = walk * WALK_TICKS_PER_BLOCK + BOARDING_PENALTY_TICKS;
            if (cost >= bestCost.getOrDefault(state, Double.MAX_VALUE)) {
                continue;
            }
            bestCost.put(state, cost);
            sourceByState.put(state, start);
            open.add(new SearchNode(state, cost, stableStateKey(state)));
        }
        SearchState reached = null;
        while (!open.isEmpty()) {
            SearchNode current = open.poll();
            if (current.cost() > bestCost.getOrDefault(current.state(), Double.MAX_VALUE) + SEARCH_COST_EPSILON) {
                continue;
            }
            if (isTargetState(current.state(), destinations, destinationStationGroupId)) {
                reached = current.state();
                break;
            }
            for (GraphEdge edge : graph.edgesFrom(current.state().node())) {
                if (!current.state().hasRide() && edge.kind() != EdgeKind.RIDE) {
                    continue;
                }
                boolean nextHasRide = current.state().hasRide() || edge.kind() == EdgeKind.RIDE;
                SearchState nextState = new SearchState(edge.to(), nextHasRide);
                double nextCost = current.cost() + edge.cost();
                if (nextCost >= bestCost.getOrDefault(nextState, Double.MAX_VALUE)) {
                    continue;
                }
                bestCost.put(nextState, nextCost);
                backrefs.put(nextState, new PathBackref(current.state(), edge));
                PlatformStop source = sourceByState.get(current.state());
                if (source != null) {
                    sourceByState.put(nextState, source);
                }
                open.add(new SearchNode(nextState, nextCost, current.tieKey() + "|" + edge.stableKey()));
            }
        }
        if (reached == null) {
            return new SearchResult(List.of(), Double.MAX_VALUE, Optional.empty());
        }
        ArrayList<GraphEdge> path = new ArrayList<>();
        SearchState cursor = reached;
        while (backrefs.containsKey(cursor)) {
            PathBackref backref = backrefs.get(cursor);
            if (backref == null) {
                return new SearchResult(List.of(), Double.MAX_VALUE, Optional.empty());
            }
            path.add(0, backref.edge());
            cursor = backref.previous();
        }
        return new SearchResult(path, bestCost.getOrDefault(reached, Double.MAX_VALUE), Optional.ofNullable(sourceByState.get(reached)));
    }

    /**
     * Deterministic identity of a search state, used to break cost ties the same
     * way PipePathfinder.BestState does, so equal-cost routes always resolve to the
     * same path for the same underlying data.
     */
    private static String stableStateKey(SearchState state) {
        return state.node().type() + ":" + state.node().id() + ":" + (state.hasRide() ? 1 : 0);
    }

    private static boolean isTargetState(SearchState state, Set<UUID> destinations, UUID destinationStationGroupId) {
        if (state.node().type() == NodeType.PLATFORM && destinations.contains(state.node().id())) {
            return true;
        }
        return state.hasRide()
                && state.node().type() == NodeType.STATION_TRANSFER
                && destinationStationGroupId.equals(state.node().id());
    }

    private static List<ClientNavigationController.NavigationSegment> compressSegments(List<GraphEdge> edges) {
        ArrayList<SegmentBuilder> builders = new ArrayList<>();
        SegmentBuilder current = null;
        ArrayList<GraphEdge> transferAfterCurrent = new ArrayList<>();
        for (GraphEdge edge : edges) {
            if (!(edge instanceof RideEdge rideEdge)) {
                transferAfterCurrent.add(edge);
                continue;
            }
            if (current != null && current.matches(rideEdge) && transferAfterCurrent.isEmpty()) {
                current.add(rideEdge);
                continue;
            }
            if (current != null) {
                current.transferAfterEdges.addAll(transferAfterCurrent);
                transferAfterCurrent.clear();
                builders.add(current);
            } else {
                transferAfterCurrent.clear();
            }
            current = new SegmentBuilder(rideEdge);
        }
        if (current != null) {
            current.transferAfterEdges.addAll(transferAfterCurrent);
            builders.add(current);
        }
        ArrayList<ClientNavigationController.NavigationSegment> segments = new ArrayList<>();
        for (int i = 0; i < builders.size(); i++) {
            SegmentBuilder builder = builders.get(i);
            boolean finalSegment = i == builders.size() - 1;
            Optional<ClientNavigationController.TransferInstruction> transferInstruction = Optional.empty();
            Optional<ClientNavigationController.FinalWalkInstruction> finalWalkInstruction = Optional.empty();
            if (!finalSegment) {
                SegmentBuilder next = builders.get(i + 1);
                transferInstruction = transferInstruction(builder, next);
            } else if (!builder.transferAfterEdges.isEmpty()) {
                finalWalkInstruction = finalWalkInstruction(builder.transferAfterEdges);
            }
            segments.add(builder.build(i, finalSegment, transferInstruction, finalWalkInstruction));
        }
        return List.copyOf(segments);
    }

    private static Optional<ClientNavigationController.TransferInstruction> transferInstruction(SegmentBuilder builder, SegmentBuilder next) {
        Optional<TransferEdge> semanticEdge = transferSemanticEdge(builder.transferAfterEdges);
        if (semanticEdge.isPresent()) {
            return Optional.of(ClientNavigationController.TransferInstruction.fromEdge(semanticEdge.get(), next.boardingPlatformStopId, next.lineName, next.colors));
        }
        Optional<PlatformStop> fromStop = ClientRouteDataCache.platformStop(builder.alightingPlatformStopId());
        Optional<PlatformStop> toStop = ClientRouteDataCache.platformStop(next.boardingPlatformStopId);
        Optional<StationGroup> fromStation = fromStop.flatMap(stop -> ClientRouteDataCache.stationGroup(stop.stationGroupId()));
        Optional<StationGroup> toStation = toStop.flatMap(stop -> ClientRouteDataCache.stationGroup(stop.stationGroupId()));
        if (fromStation.isEmpty() || toStation.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ClientNavigationController.TransferInstruction.sameStation(
                fromStation.get(),
                toStation.get(),
                next.boardingPlatformStopId,
                next.lineName,
                next.colors));
    }

    private static Optional<ClientNavigationController.FinalWalkInstruction> finalWalkInstruction(List<GraphEdge> transferEdges) {
        return transferEdges.stream()
                .filter(TransferEdge.class::isInstance)
                .map(TransferEdge.class::cast)
                .filter(edge -> edge.transferKind() == ClientNavigationController.TransferKind.OUT_OF_STATION || edge.transferKind() == ClientNavigationController.TransferKind.CROSS_DIMENSION_OUT_OF_STATION)
                .reduce((ignored, edge) -> edge)
                .flatMap(edge -> {
                    if (edge.transferLinkId().isEmpty()) {
                        return Optional.empty();
                    }
                    return Optional.of(new ClientNavigationController.FinalWalkInstruction(
                            edge.transferKind(),
                            edge.fromStationGroupId(),
                            edge.toStationGroupId(),
                            edge.transferLinkId(),
                            edge.fromLevelKey(),
                            edge.toLevelKey()));
                });
    }

    private static Optional<TransferEdge> transferSemanticEdge(List<GraphEdge> transferEdges) {
        Optional<TransferEdge> outOfStation = transferEdges.stream()
                .filter(TransferEdge.class::isInstance)
                .map(TransferEdge.class::cast)
                .filter(edge -> edge.transferKind() == ClientNavigationController.TransferKind.OUT_OF_STATION || edge.transferKind() == ClientNavigationController.TransferKind.CROSS_DIMENSION_OUT_OF_STATION)
                .reduce((ignored, edge) -> edge);
        if (outOfStation.isPresent()) {
            return outOfStation;
        }
        return transferEdges.stream()
                .filter(TransferEdge.class::isInstance)
                .map(TransferEdge.class::cast)
                .filter(edge -> edge.transferKind() == ClientNavigationController.TransferKind.SAME_STATION)
                .reduce((ignored, edge) -> edge);
    }

    private static ClientNavigationController.DestinationSearchResult destinationResult(ResourceKey<Level> playerLevel, Vec3 playerPosition, StationGroup station, String query, boolean reachable) {
        int score = query.isBlank() ? 1 : matchScore(station, query);
        double distance = station.levelKey().equals(playerLevel) ? Vec3.atCenterOf(station.stationBlockPos()).distanceTo(playerPosition) : Double.MAX_VALUE / 4.0D;
        return new ClientNavigationController.DestinationSearchResult(station.id(), station.primaryName(), station.translatedNames(), station.levelKey(), distance, reachable, score);
    }

    private static Set<UUID> reachableStationGroups(LocalPlayer player) {
        NavigationGraph graph = graph();
        long routeRevision = ClientRouteDataCache.revision();
        long pipeRevision = ClientPipeNetworkCache.aggregateRevision();
        ResourceKey<Level> level = player.level().dimension();
        if (cachedReachability != null
                && cachedReachability.routeRevision() == routeRevision
                && cachedReachability.pipeRevision() == pipeRevision
                && cachedReachability.levelKey().equals(level)) {
            return cachedReachability.stationGroupIds();
        }
        LinkedHashSet<UUID> reachable = new LinkedHashSet<>();
        ArrayDeque<SearchState> queue = new ArrayDeque<>();
        HashSet<SearchState> visited = new HashSet<>();
        for (PlatformStop start : ClientRouteDataCache.platformStops()) {
            if (!graph.hasRideConnection(start.id())) {
                continue;
            }
            if (ClientRouteDataCache.stationGroup(start.stationGroupId()).map(station -> station.levelKey().equals(level)).orElse(false)) {
                SearchState state = new SearchState(NodeKey.platform(start.id()), false);
                if (visited.add(state)) {
                    queue.add(state);
                }
            }
        }
        while (!queue.isEmpty()) {
            SearchState current = queue.removeFirst();
            if (current.hasRide()) {
                addReachableStation(current.node(), reachable);
            }
            for (GraphEdge edge : graph.edgesFrom(current.node())) {
                if (!current.hasRide() && edge.kind() != EdgeKind.RIDE) {
                    continue;
                }
                SearchState next = new SearchState(edge.to(), current.hasRide() || edge.kind() == EdgeKind.RIDE);
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        cachedReachability = new ReachabilityCache(routeRevision, pipeRevision, level, Set.copyOf(reachable));
        return cachedReachability.stationGroupIds();
    }

    private static void addReachableStation(NodeKey node, Set<UUID> reachable) {
        if (node.type() == NodeType.STATION_TRANSFER) {
            reachable.add(node.id());
            return;
        }
        if (node.type() == NodeType.PLATFORM) {
            ClientRouteDataCache.platformStop(node.id()).ifPresent(stop -> reachable.add(stop.stationGroupId()));
        }
    }

    private static int matchScore(StationGroup station, String query) {
        String primary = station.primaryName().toLowerCase(Locale.ROOT);
        if (primary.equals(query)) {
            return 100;
        }
        if (primary.startsWith(query)) {
            return 80;
        }
        if (pinyinInitials(primary).startsWith(query)) {
            return 70;
        }
        if (primary.contains(query)) {
            return 60;
        }
        if (isSubsequence(query, primary)) {
            return 40;
        }
        for (String translated : station.translatedNames()) {
            String value = translated.toLowerCase(Locale.ROOT);
            if (value.equals(query)) {
                return 95;
            }
            if (value.startsWith(query)) {
                return 75;
            }
            if (pinyinInitials(value).startsWith(query)) {
                return 65;
            }
            if (value.contains(query)) {
                return 55;
            }
            if (isSubsequence(query, value)) {
                return 35;
            }
        }
        return 0;
    }

    private static boolean isSubsequence(String query, String value) {
        if (query.isEmpty()) {
            return false;
        }
        int index = 0;
        for (int i = 0; i < value.length() && index < query.length(); i++) {
            if (value.charAt(i) == query.charAt(index)) {
                index++;
            }
        }
        return index == query.length();
    }

    /**
     * Maps a name to its pinyin-initial form: every hanzi covered by
     * PINYIN_INITIAL_GROUPS becomes its initial letter, every other character is
     * kept as-is. The table is intentionally tiny - it only covers a few hundred
     * common characters seen in station names (directions, terrain, landmarks,
     * settlement words); uncovered hanzi stay untouched, in which case an initials
     * query simply does not match and scoring falls back to plain text matching.
     * Full-syllable pinyin queries are not supported.
     */
    private static String pinyinInitials(String value) {
        StringBuilder builder = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            Character initial = PINYIN_INITIALS.get(c);
            if (initial == null) {
                if (builder != null) {
                    builder.append(c);
                }
                continue;
            }
            if (builder == null) {
                builder = new StringBuilder(value.length());
                builder.append(value, 0, i);
            }
            builder.append(initial.charValue());
        }
        return builder == null ? value : builder.toString();
    }

    private static Map<Character, Character> buildPinyinInitials() {
        Map<Character, Character> map = new HashMap<>();
        for (String group : PINYIN_INITIAL_GROUPS.split("\\|")) {
            char initial = group.charAt(0);
            for (int i = 1; i < group.length(); i++) {
                map.put(group.charAt(i), initial);
            }
        }
        return Map.copyOf(map);
    }

    static Vec3 platformPosition(PlatformStop platformStop) {
        return ClientPipeNetworkCache.connection(platformStop.connectionRef())
                .map(connection -> connection.positionAt(connection.length() * 0.5D))
                .orElseGet(() -> ClientRouteDataCache.stationGroup(platformStop.stationGroupId())
                        .map(group -> Vec3.atCenterOf(group.stationBlockPos()))
                        .orElse(Vec3.ZERO));
    }

    static Vec3 platformTargetPosition(PlatformStop platformStop, Vec3 playerPosition) {
        return ClientPipeNetworkCache.connection(platformStop.connectionRef())
                .map(connection -> SlideGeometry.project(connection, playerPosition).closestPoint())
                .orElseGet(() -> ClientRouteDataCache.stationGroup(platformStop.stationGroupId())
                        .map(group -> Vec3.atCenterOf(group.stationBlockPos()))
                        .orElse(Vec3.ZERO));
    }

    private static double platformAccessDistance(PlatformStop platformStop, Vec3 playerPosition) {
        return ClientPipeNetworkCache.connection(platformStop.connectionRef())
                .map(connection -> SlideGeometry.project(connection, playerPosition).distance())
                .orElseGet(() -> platformPosition(platformStop).distanceTo(playerPosition));
    }

    private record NavigationGraph(Map<NodeKey, List<GraphEdge>> edges, Set<UUID> rideConnectedStops) {
        private NavigationGraph {
            rideConnectedStops = Set.copyOf(rideConnectedStops);
        }

        private List<GraphEdge> edgesFrom(NodeKey node) {
            return this.edges.getOrDefault(node, List.of());
        }

        private boolean hasRideConnection(UUID platformStopId) {
            return this.rideConnectedStops.contains(platformStopId);
        }
    }

    private enum NodeType {
        PLATFORM,
        STATION_TRANSFER
    }

    private record NodeKey(NodeType type, UUID id) {
        private static NodeKey platform(UUID platformStopId) {
            return new NodeKey(NodeType.PLATFORM, platformStopId);
        }

        private static NodeKey stationTransfer(UUID stationGroupId) {
            return new NodeKey(NodeType.STATION_TRANSFER, stationGroupId);
        }
    }

    private enum EdgeKind {
        RIDE,
        STATION_ACCESS,
        TRANSFER
    }

    private sealed interface GraphEdge permits RideEdge, StationAccessEdge, TransferEdge {
        EdgeKind kind();

        NodeKey from();

        NodeKey to();

        double cost();

        /** Deterministic identity used for tie-breaking equal-cost paths in solve(). */
        String stableKey();

        static RideEdge ride(UUID from, UUID to, UUID routeLineId, UUID layoutId, int routeDirection, UUID routeSectionId, int layoutIndex, double cost, List<Integer> colors, String lineName) {
            return new RideEdge(NodeKey.platform(from), NodeKey.platform(to), routeLineId, layoutId, routeDirection, routeSectionId, layoutIndex, cost, colors, lineName);
        }

        static StationAccessEdge stationAccess(UUID platformStopId, StationGroup station) {
            return new StationAccessEdge(NodeKey.platform(platformStopId), NodeKey.stationTransfer(station.id()), station.id(), station.levelKey());
        }

        static TransferEdge sameStationTransfer(PlatformStop from, PlatformStop to, StationGroup station, double cost) {
            return new TransferEdge(
                    NodeKey.platform(from.id()),
                    NodeKey.platform(to.id()),
                    cost,
                    ClientNavigationController.TransferKind.SAME_STATION,
                    Optional.empty(),
                    station.id(),
                    station.id(),
                    station.levelKey(),
                    station.levelKey());
        }

        static TransferEdge stationTransfer(double cost, ClientNavigationController.TransferKind transferKind, UUID transferLinkId, StationGroup fromStation, StationGroup toStation, PlatformStop toStop) {
            return new TransferEdge(
                    NodeKey.stationTransfer(fromStation.id()),
                    NodeKey.platform(toStop.id()),
                    cost,
                    transferKind,
                    Optional.of(transferLinkId),
                    fromStation.id(),
                    toStation.id(),
                    fromStation.levelKey(),
                    toStation.levelKey());
        }

        static TransferEdge stationTransferLink(double cost, ClientNavigationController.TransferKind transferKind, UUID transferLinkId, StationGroup fromStation, StationGroup toStation) {
            return new TransferEdge(
                    NodeKey.stationTransfer(fromStation.id()),
                    NodeKey.stationTransfer(toStation.id()),
                    cost,
                    transferKind,
                    Optional.of(transferLinkId),
                    fromStation.id(),
                    toStation.id(),
                    fromStation.levelKey(),
                    toStation.levelKey());
        }
    }

    private record RideEdge(
            NodeKey from,
            NodeKey to,
            UUID routeLineId,
            UUID layoutId,
            int routeDirection,
            UUID routeSectionId,
            int layoutIndex,
            double cost,
            List<Integer> colors,
            String lineName) implements GraphEdge {
        private RideEdge {
            routeDirection = routeDirection < 0 ? -1 : 1;
            colors = List.copyOf(colors);
        }

        @Override
        public EdgeKind kind() {
            return EdgeKind.RIDE;
        }

        @Override
        public String stableKey() {
            return "R:" + this.routeSectionId + ":" + this.layoutIndex + ">" + this.to.id();
        }
    }

    private record StationAccessEdge(
            NodeKey from,
            NodeKey to,
            UUID stationGroupId,
            ResourceKey<Level> levelKey) implements GraphEdge {
        @Override
        public EdgeKind kind() {
            return EdgeKind.STATION_ACCESS;
        }

        @Override
        public double cost() {
            return 0.0D;
        }

        @Override
        public String stableKey() {
            return "A:" + this.stationGroupId + ">" + this.to.id();
        }
    }

    record TransferEdge(
            NodeKey from,
            NodeKey to,
            double cost,
            ClientNavigationController.TransferKind transferKind,
            Optional<UUID> transferLinkId,
            UUID fromStationGroupId,
            UUID toStationGroupId,
            ResourceKey<Level> fromLevelKey,
            ResourceKey<Level> toLevelKey) implements GraphEdge {
        TransferEdge {
            transferLinkId = transferLinkId == null ? Optional.empty() : transferLinkId;
        }

        @Override
        public EdgeKind kind() {
            return EdgeKind.TRANSFER;
        }

        @Override
        public String stableKey() {
            return "T:" + this.transferKind + ":" + this.transferLinkId.map(UUID::toString).orElse("-") + ":" + this.fromStationGroupId + ">" + this.to.id();
        }
    }

    private record SearchState(NodeKey node, boolean hasRide) {}

    private record SearchNode(SearchState state, double cost, String tieKey) implements Comparable<SearchNode> {
        @Override
        public int compareTo(SearchNode other) {
            if (this.cost + SEARCH_COST_EPSILON < other.cost) {
                return -1;
            }
            if (this.cost > other.cost + SEARCH_COST_EPSILON) {
                return 1;
            }
            int tie = this.tieKey.compareTo(other.tieKey);
            if (tie != 0) {
                return tie;
            }
            return stableStateKey(this.state).compareTo(stableStateKey(other.state));
        }
    }

    private record PathBackref(SearchState previous, GraphEdge edge) {}

    private record SearchResult(List<GraphEdge> edges, double cost, Optional<PlatformStop> start) {
        private SearchResult {
            edges = List.copyOf(edges);
            start = start == null ? Optional.empty() : start;
        }
    }

    private record ReachabilityCache(long routeRevision, long pipeRevision, ResourceKey<Level> levelKey, Set<UUID> stationGroupIds) {
        private ReachabilityCache {
            stationGroupIds = Set.copyOf(stationGroupIds);
        }
    }

    private record CandidatePlan(PlatformStop start, SearchResult search, double cost, double walkDistance) {}

    private static final class AccessDistances {
        private final Vec3 playerPosition;
        private final Map<UUID, Double> platformDistances = new HashMap<>();
        private final Map<UUID, Double> stationGroupDistances = new HashMap<>();

        private AccessDistances(Vec3 playerPosition) {
            this.playerPosition = playerPosition;
        }

        private double platformDistance(PlatformStop platformStop) {
            return this.platformDistances.computeIfAbsent(platformStop.id(), ignored -> platformAccessDistance(platformStop, this.playerPosition));
        }

        private double stationGroupDistance(UUID stationGroupId) {
            return this.stationGroupDistances.computeIfAbsent(stationGroupId, ignored -> {
                List<PlatformStop> stops = ClientRouteDataCache.platformStopsInStation(stationGroupId);
                if (stops.isEmpty()) {
                    return ClientRouteDataCache.stationGroup(stationGroupId)
                            .map(station -> Vec3.atCenterOf(station.stationBlockPos()).distanceTo(this.playerPosition))
                            .orElse(Double.MAX_VALUE / 4.0D);
                }
                return stops.stream()
                        .mapToDouble(this::platformDistance)
                        .min()
                        .orElse(Double.MAX_VALUE / 4.0D);
            });
        }
    }

    private static final class SegmentBuilder {
        private final UUID routeLineId;
        private final UUID layoutId;
        private final int routeDirection;
        private final UUID boardingPlatformStopId;
        private final ArrayList<UUID> stationSequence = new ArrayList<>();
        private final ArrayList<UUID> sectionIds = new ArrayList<>();
        private final ArrayList<ClientNavigationController.NavigationSectionRef> sectionRefs = new ArrayList<>();
        private final ArrayList<Integer> colors;
        private final String lineName;
        private double cost;
        private final ArrayList<GraphEdge> transferAfterEdges = new ArrayList<>();

        private SegmentBuilder(RideEdge first) {
            this.routeLineId = first.routeLineId();
            this.layoutId = first.layoutId();
            this.routeDirection = first.routeDirection();
            this.boardingPlatformStopId = first.from().id();
            this.colors = new ArrayList<>(first.colors());
            this.lineName = first.lineName();
            this.stationSequence.add(first.from().id());
            this.add(first);
        }

        private boolean matches(RideEdge edge) {
            return this.routeLineId.equals(edge.routeLineId())
                    && this.layoutId.equals(edge.layoutId())
                    && this.routeDirection == edge.routeDirection()
                    && this.stationSequence.getLast().equals(edge.from().id());
        }

        private void add(RideEdge edge) {
            this.stationSequence.add(edge.to().id());
            this.sectionIds.add(edge.routeSectionId());
            this.sectionRefs.add(new ClientNavigationController.NavigationSectionRef(edge.routeSectionId(), edge.layoutIndex()));
            this.cost += edge.cost();
        }

        private UUID alightingPlatformStopId() {
            return this.stationSequence.getLast();
        }

        private ClientNavigationController.NavigationSegment build(int index, boolean finalSegment, Optional<ClientNavigationController.TransferInstruction> transferInstruction, Optional<ClientNavigationController.FinalWalkInstruction> finalWalkInstruction) {
            return new ClientNavigationController.NavigationSegment(
                    index,
                    this.routeLineId,
                    this.layoutId,
                    this.routeDirection,
                    this.boardingPlatformStopId,
                    this.alightingPlatformStopId(),
                    this.stationSequence,
                    this.sectionIds,
                    this.sectionRefs,
                    transferInstruction,
                    finalWalkInstruction,
                    finalSegment,
                    (int) Math.round(this.cost),
                    this.colors,
                    this.lineName);
        }
    }
}
