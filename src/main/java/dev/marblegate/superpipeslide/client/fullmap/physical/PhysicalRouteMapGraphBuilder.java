package dev.marblegate.superpipeslide.client.fullmap.physical;

import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.diagnostic.DiagnosticType;
import dev.marblegate.superpipeslide.client.fullmap.diagnostic.MapBuildDiagnostic;
import dev.marblegate.superpipeslide.client.fullmap.model.FullRouteMapSourceSnapshot;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorNode;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionPath;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class PhysicalRouteMapGraphBuilder {
    private static final double PATH_SAMPLE_STEP_BLOCKS = 2.0D;
    private static final int MAX_CONNECTION_SAMPLES = 48;
    private static final double SIMPLIFY_EPSILON_BLOCKS = 0.12D;

    private final FullRouteMapSourceSnapshot source;
    private final Map<UUID, StationGroup> stationsById = new LinkedHashMap<>();
    private final Map<UUID, PlatformStop> platformsById = new LinkedHashMap<>();
    private final Map<UUID, RouteLine> linesById = new LinkedHashMap<>();
    private final Map<UUID, RouteSection> sectionsById = new LinkedHashMap<>();
    private final Map<UUID, Set<UUID>> routeLineIdsByPlatform = new LinkedHashMap<>();
    private final Map<UUID, Set<UUID>> routeLineIdsByStation = new LinkedHashMap<>();
    private final Map<ResourceKey<Level>, Map<String, PhysicalMapNode>> nodesByLevel = new LinkedHashMap<>();
    private final Map<ResourceKey<Level>, List<PhysicalMapEdge>> edgesByLevel = new LinkedHashMap<>();
    private final Map<ResourceKey<Level>, List<PhysicalMissingCrossDimensionPathHint>> missingCrossDimensionHintsByLevel = new LinkedHashMap<>();
    private final Map<ResourceKey<Level>, List<MapBuildDiagnostic>> diagnosticsByLevel = new LinkedHashMap<>();

    public PhysicalRouteMapGraphBuilder(FullRouteMapSourceSnapshot source) {
        this.source = source;
    }

    public Map<ResourceKey<Level>, PhysicalRouteMapGraph> build(Collection<ResourceKey<Level>> knownDimensions) {
        this.indexSource();
        this.indexRouteLineUsage();
        this.buildPlatformNodes();
        this.buildLayoutEdges();
        return this.finishGraphs(knownDimensions);
    }

    private void indexSource() {
        this.source.stationGroups().forEach(station -> this.stationsById.put(station.id(), station));
        this.source.platformStops().forEach(platform -> this.platformsById.put(platform.id(), platform));
        this.source.routeLines().forEach(line -> this.linesById.put(line.id(), line));
        this.source.routeSections().forEach(section -> this.sectionsById.put(section.id(), section));
    }

    private void indexRouteLineUsage() {
        for (RouteLayout layout : this.source.routeLayouts()) {
            RouteLine line = this.linesById.get(layout.routeLineId());
            if (line == null) {
                this.diagnostic(null, DiagnosticType.MISSING_ROUTE_LINE, "Physical map layout " + layout.id() + " references missing route line " + layout.routeLineId());
                continue;
            }
            for (UUID platformStopId : layout.orderedPlatformStops()) {
                PlatformStop platform = this.platformsById.get(platformStopId);
                if (platform == null) {
                    this.diagnostic(null, DiagnosticType.MISSING_PLATFORM_STOP, "Physical map layout " + layout.id() + " references missing platform stop " + platformStopId);
                    continue;
                }
                this.routeLineIdsByPlatform.computeIfAbsent(platform.id(), ignored -> new LinkedHashSet<>()).add(line.id());
                this.routeLineIdsByStation.computeIfAbsent(platform.stationGroupId(), ignored -> new LinkedHashSet<>()).add(line.id());
            }
        }
    }

    private void buildPlatformNodes() {
        for (PlatformStop platform : this.source.platformStops()) {
            List<UUID> routeLineIds = this.routeLineIdsByPlatform.getOrDefault(platform.id(), Set.of()).stream().sorted().toList();
            if (routeLineIds.isEmpty()) {
                continue;
            }
            Optional<PipeConnection> connection = ClientPipeNetworkCache.connection(platform.connectionRef());
            if (connection.isEmpty()) {
                this.diagnostic(platform.connectionRef().levelKey(), DiagnosticType.MISSING_PIPE_CONNECTION, "Physical map platform " + platform.id() + " references missing pipe connection " + platform.connectionRef().connectionId());
                continue;
            }
            StationGroup station = this.stationsById.get(platform.stationGroupId());
            if (station == null) {
                this.diagnostic(platform.connectionRef().levelKey(), DiagnosticType.MISSING_STATION_GROUP, "Physical map platform " + platform.id() + " references missing station group " + platform.stationGroupId());
                continue;
            }
            Vec3 center = midpoint(connection.get());
            String label = platform.displayName().filter(name -> !name.isBlank()).orElse(station.primaryName() + " " + platform.platformNumber());
            PhysicalMapNode node = new PhysicalMapNode(
                    platformNodeId(platform.id()),
                    PhysicalNodeKind.PLATFORM,
                    connection.get().levelKey(),
                    center.x,
                    center.z,
                    center.y,
                    label,
                    Optional.of(platform.id()),
                    Optional.of(platform.stationGroupId()),
                    Optional.empty(),
                    routeLineIds
            );
            this.addNode(node);
        }
    }

    private void buildLayoutEdges() {
        for (RouteLayout layout : this.source.routeLayouts()) {
            RouteLine line = this.linesById.get(layout.routeLineId());
            if (line == null) {
                this.diagnostic(null, DiagnosticType.MISSING_ROUTE_LINE, "Physical map layout " + layout.id() + " references missing route line " + layout.routeLineId());
                continue;
            }
            List<UUID> stops = layout.orderedPlatformStops();
            if (stops.size() < 2) {
                continue;
            }
            List<UUID> sectionIds = layout.orderedSectionRefs().stream().map(ref -> ref.routeSectionId()).toList();
            int sectionCount = layout.loop() ? stops.size() : Math.max(0, stops.size() - 1);
            for (int index = 0; index < sectionCount; index++) {
                UUID fromStopId = stops.get(index);
                UUID toStopId = stops.get((index + 1) % stops.size());
                Optional<UUID> sectionId = this.sectionIdFor(layout, sectionIds, index, fromStopId, toStopId);
                if (sectionId.isEmpty()) {
                    this.buildFallbackEdge(line, layout, stableUuid("physical-missing-section:" + layout.id() + ":" + index), index, fromStopId, toStopId, true);
                    continue;
                }
                this.buildSectionEdge(line, layout, sectionId.get(), index, fromStopId, toStopId);
            }
        }
    }

    private Optional<UUID> sectionIdFor(RouteLayout layout, List<UUID> sectionIds, int index, UUID fromStopId, UUID toStopId) {
        if (index < sectionIds.size()) {
            return Optional.of(sectionIds.get(index));
        }
        return this.sectionsById.values().stream()
                .filter(section -> section.routeLayoutId().equals(layout.id()))
                .filter(section -> section.fromPlatformStopId().equals(fromStopId) && section.toPlatformStopId().equals(toStopId))
                .map(RouteSection::id)
                .findFirst();
    }

    private void buildSectionEdge(RouteLine line, RouteLayout layout, UUID sectionId, int layoutIndex, UUID fromStopId, UUID toStopId) {
        RouteSection section = this.sectionsById.get(sectionId);
        if (section == null) {
            this.buildFallbackEdge(line, layout, sectionId, layoutIndex, fromStopId, toStopId, true);
            return;
        }
        RouteSectionPath path = this.source.sectionPaths().get(sectionId);
        if (path == null || path.forwardConnections().isEmpty()) {
            this.buildFallbackEdge(line, layout, sectionId, layoutIndex, fromStopId, toStopId, true);
            return;
        }
        this.buildPhysicalPathEdges(line, layout, section, layoutIndex, fromStopId, toStopId, path.forwardConnections());
    }

    private void buildFallbackEdge(RouteLine line, RouteLayout layout, UUID sectionId, int layoutIndex, UUID fromStopId, UUID toStopId, boolean missingPath) {
        PlatformStop from = this.platformsById.get(fromStopId);
        PlatformStop to = this.platformsById.get(toStopId);
        if (from == null || to == null) {
            this.diagnostic(null, DiagnosticType.MISSING_PLATFORM_STOP, "Physical map fallback edge for layout " + layout.id() + " has missing endpoints");
            return;
        }
        Optional<PipeConnection> fromConnection = ClientPipeNetworkCache.connection(from.connectionRef());
        Optional<PipeConnection> toConnection = ClientPipeNetworkCache.connection(to.connectionRef());
        if (fromConnection.isEmpty() || toConnection.isEmpty()) {
            ResourceKey<Level> levelKey = fromConnection.map(PipeConnection::levelKey).orElseGet(() -> to.connectionRef().levelKey());
            this.diagnostic(levelKey, DiagnosticType.MISSING_PIPE_CONNECTION, "Physical map fallback edge for section " + sectionId + " has missing platform connection");
            return;
        }
        if (!fromConnection.get().levelKey().equals(toConnection.get().levelKey())) {
            if (missingPath) {
                this.diagnostic(fromConnection.get().levelKey(), DiagnosticType.MISSING_ROUTE_SECTION_PATH, "Physical map cannot fallback across dimensions for section " + sectionId);
                this.diagnostic(toConnection.get().levelKey(), DiagnosticType.MISSING_ROUTE_SECTION_PATH, "Physical map cannot fallback across dimensions for section " + sectionId);
                Vec3 fromCenter = midpoint(fromConnection.get());
                Vec3 toCenter = midpoint(toConnection.get());
                this.addMissingCrossDimensionHint(line, layout, sectionId, layoutIndex, fromStopId, toStopId, fromConnection.get().levelKey(), toConnection.get().levelKey(), fromCenter, toCenter);
                this.addMissingCrossDimensionHint(line, layout, sectionId, layoutIndex, toStopId, fromStopId, toConnection.get().levelKey(), fromConnection.get().levelKey(), toCenter, fromCenter);
            }
            return;
        }
        Vec3 fromCenter = midpoint(fromConnection.get());
        Vec3 toCenter = midpoint(toConnection.get());
        List<Vec2> points = List.of(new Vec2(fromCenter.x, fromCenter.z), new Vec2(toCenter.x, toCenter.z));
        ResourceKey<Level> levelKey = fromConnection.get().levelKey();
        this.addEdge(new PhysicalMapEdge(
                edgeId(layout.id(), sectionId, layoutIndex, 0, levelKey),
                levelKey,
                Optional.of(platformNodeId(fromStopId)),
                Optional.of(platformNodeId(toStopId)),
                points,
                new PhysicalEdgeMetadata(line.id(), layout.id(), sectionId, layoutIndex, 1, layout.bidirectional(), fromStopId, toStopId, List.of(), true, fromCenter.distanceTo(toCenter)),
                boundsFor(points).inflate(8.0D)
        ));
    }

    private void buildPhysicalPathEdges(RouteLine line, RouteLayout layout, RouteSection section, int layoutIndex, UUID fromStopId, UUID toStopId, List<PipeConnectionRef> pathRefs) {
        List<PathConnection> path = new ArrayList<>();
        for (PipeConnectionRef ref : pathRefs) {
            Optional<PipeConnection> connection = ClientPipeNetworkCache.connection(ref);
            if (connection.isEmpty()) {
                this.diagnostic(ref.levelKey(), DiagnosticType.MISSING_PIPE_CONNECTION, "Physical map section " + section.id() + " references missing pipe connection " + ref.connectionId());
                continue;
            }
            path.add(new PathConnection(ref, connection.get()));
        }
        if (path.isEmpty()) {
            this.buildFallbackEdge(line, layout, section.id(), layoutIndex, fromStopId, toStopId, true);
            return;
        }

        int edgeIndex = 0;
        int start = 0;
        while (start < path.size()) {
            ResourceKey<Level> levelKey = path.get(start).connection().levelKey();
            int end = start;
            while (end + 1 < path.size() && path.get(end + 1).connection().levelKey().equals(levelKey)) {
                end++;
            }
            FoldTransition before = start > 0 ? this.findFoldTransition(path.get(start - 1).connection(), path.get(start).connection()).orElse(null) : null;
            FoldTransition after = end + 1 < path.size() ? this.findFoldTransition(path.get(end).connection(), path.get(end + 1).connection()).orElse(null) : null;
            Optional<String> fromNode = start == 0 ? Optional.of(platformNodeId(fromStopId)) : Optional.ofNullable(before).map(fold -> this.ensureFoldNode(fold.peerAnchor()).id());
            Optional<String> toNode = end + 1 >= path.size() ? Optional.of(platformNodeId(toStopId)) : Optional.ofNullable(after).map(fold -> this.ensureFoldNode(fold.localAnchor()).id());
            List<List<Vec2>> connectionPaths = this.sampleRunConnections(path.subList(start, end + 1), fromStopId, toStopId, before, after);
            for (int localIndex = 0; localIndex < connectionPaths.size(); localIndex++) {
                int pathIndex = start + localIndex;
                if (pathIndex > end) {
                    break;
                }
                List<Vec2> points = connectionPaths.get(localIndex);
                if (points.size() < 2) {
                    edgeIndex++;
                    continue;
                }
                PathConnection connection = path.get(pathIndex);
                this.addEdge(new PhysicalMapEdge(
                        edgeId(layout.id(), section.id(), layoutIndex, edgeIndex, levelKey),
                        levelKey,
                        pathIndex == start ? fromNode : Optional.empty(),
                        pathIndex == end ? toNode : Optional.empty(),
                        points,
                        new PhysicalEdgeMetadata(line.id(), layout.id(), section.id(), layoutIndex, 1, layout.bidirectional(), fromStopId, toStopId, List.of(connection.ref()), false, lengthOf(List.of(connection))),
                        boundsFor(points).inflate(8.0D)
                ));
                edgeIndex++;
            }
            start = end + 1;
        }
    }

    private List<List<Vec2>> sampleRunConnections(List<PathConnection> run, UUID fromStopId, UUID toStopId, FoldTransition before, FoldTransition after) {
        if (run.isEmpty()) {
            return List.of();
        }
        Vec3 firstReference = before == null
                ? this.platformCenter(fromStopId).orElse(run.getFirst().connection().fromSurface())
                : anchorCenter(before.peerAnchor());
        Vec3 finalReference = after == null
                ? this.platformCenter(toStopId).orElse(run.getLast().connection().toSurface())
                : anchorCenter(after.localAnchor());
        List<List<Vec2>> result = new ArrayList<>();
        Vec3 previousEnd = null;
        for (int i = 0; i < run.size(); i++) {
            PipeConnection connection = run.get(i).connection();
            List<Vec3> connectionSamples = sampleConnection(connection);
            if (connectionSamples.size() < 2) {
                result.add(List.of());
                continue;
            }
            boolean reverse;
            if (previousEnd == null) {
                Vec3 start = connectionSamples.getFirst();
                Vec3 end = connectionSamples.getLast();
                double forwardCost = start.distanceTo(firstReference) + (run.size() == 1 ? end.distanceTo(finalReference) : 0.0D);
                double backwardCost = end.distanceTo(firstReference) + (run.size() == 1 ? start.distanceTo(finalReference) : 0.0D);
                reverse = backwardCost < forwardCost;
            } else {
                reverse = connectionSamples.getFirst().distanceTo(previousEnd) > connectionSamples.getLast().distanceTo(previousEnd);
            }
            if (reverse) {
                connectionSamples = reversed(connectionSamples);
            }
            previousEnd = connectionSamples.getLast();
            result.add(simplify(connectionSamples.stream().map(point -> new Vec2(point.x, point.z)).toList(), SIMPLIFY_EPSILON_BLOCKS));
        }
        return result;
    }

    private Optional<Vec3> platformCenter(UUID platformStopId) {
        PlatformStop platform = this.platformsById.get(platformStopId);
        if (platform == null) {
            return Optional.empty();
        }
        return ClientPipeNetworkCache.connection(platform.connectionRef()).map(PhysicalRouteMapGraphBuilder::midpoint);
    }

    private PhysicalMapNode ensureFoldNode(PipeAnchorId anchorId) {
        String id = foldNodeId(anchorId);
        PhysicalMapNode existing = this.nodesByLevel.getOrDefault(anchorId.levelKey(), Map.of()).get(id);
        if (existing != null) {
            return existing;
        }
        PhysicalMapNode node = new PhysicalMapNode(
                id,
                PhysicalNodeKind.FOLD_ANCHOR,
                anchorId.levelKey(),
                anchorId.blockPos().getX(),
                anchorId.blockPos().getZ(),
                anchorId.blockPos().getY(),
                this.foldAnchorLabel(anchorId),
                Optional.empty(),
                Optional.empty(),
                Optional.of(anchorId),
                List.of()
        );
        this.addNode(node);
        return node;
    }

    private String foldAnchorLabel(PipeAnchorId anchorId) {
        Optional<String> ownName = ClientPipeNetworkCache.foldAnchorAt(anchorId)
                .map(FoldAnchorNode::displayName)
                .filter(name -> !name.isBlank());
        if (ownName.isPresent()) {
            return ownName.get();
        }
        return ClientPipeNetworkCache.globalFoldCounterpart(anchorId)
                .flatMap(ClientPipeNetworkCache::foldAnchorAt)
                .map(FoldAnchorNode::displayName)
                .filter(name -> !name.isBlank())
                .orElse(anchorId.blockPos().toShortString());
    }

    private Optional<FoldTransition> findFoldTransition(PipeConnection currentConnection, PipeConnection nextConnection) {
        List<PipeAnchorId> currentAnchors = List.of(currentConnection.fromAnchor(), currentConnection.toAnchor());
        List<PipeAnchorId> nextAnchors = List.of(nextConnection.fromAnchor(), nextConnection.toAnchor());
        for (PipeAnchorId currentAnchor : currentAnchors) {
            if (nextAnchors.contains(currentAnchor)) {
                return Optional.empty();
            }
        }
        for (PipeAnchorId currentAnchor : currentAnchors) {
            for (PipeAnchorId nextAnchor : nextAnchors) {
                if (ClientPipeNetworkCache.globalFoldCounterpart(currentAnchor).filter(nextAnchor::equals).isPresent()
                        || ClientPipeNetworkCache.globalFoldCounterpart(nextAnchor).filter(currentAnchor::equals).isPresent()) {
                    return Optional.of(new FoldTransition(currentAnchor, nextAnchor));
                }
            }
        }
        return Optional.empty();
    }

    private Map<ResourceKey<Level>, PhysicalRouteMapGraph> finishGraphs(Collection<ResourceKey<Level>> knownDimensions) {
        Map<ResourceKey<Level>, PhysicalRouteMapGraph> result = new LinkedHashMap<>();
        Set<ResourceKey<Level>> dimensions = new LinkedHashSet<>(knownDimensions);
        dimensions.addAll(this.nodesByLevel.keySet());
        dimensions.addAll(this.edgesByLevel.keySet());
        dimensions.addAll(this.missingCrossDimensionHintsByLevel.keySet());
        for (ResourceKey<Level> levelKey : dimensions.stream().sorted(Comparator.comparing(level -> level.identifier().toString())).toList()) {
            Map<String, PhysicalMapNode> nodes = new LinkedHashMap<>(this.nodesByLevel.getOrDefault(levelKey, Map.of()));
            List<PhysicalMapEdge> edges = List.copyOf(this.edgesByLevel.getOrDefault(levelKey, List.of()));
            List<PhysicalMissingCrossDimensionPathHint> missingHints = List.copyOf(this.missingCrossDimensionHintsByLevel.getOrDefault(levelKey, List.of()));
            List<PhysicalStationFrame> frames = this.stationFramesFor(levelKey, nodes.values());
            Map<UUID, PhysicalStationFrame> framesByStation = frames.stream()
                    .collect(Collectors.toMap(PhysicalStationFrame::stationGroupId, frame -> frame, (a, b) -> a, LinkedHashMap::new));
            Aabb2 bounds = Aabb2.empty();
            for (PhysicalMapNode node : nodes.values()) {
                bounds = bounds.include(node.worldX(), node.worldZ());
            }
            for (PhysicalMapEdge edge : edges) {
                bounds = bounds.include(edge.worldBounds());
            }
            for (PhysicalStationFrame frame : frames) {
                bounds = bounds.include(frame.worldBounds());
            }
            if (bounds.isEmpty()) {
                bounds = new Aabb2(-64.0D, -64.0D, 64.0D, 64.0D);
            } else {
                bounds = bounds.inflate(96.0D);
            }
            Map<String, PhysicalMapEdge> edgesById = edges.stream()
                    .collect(Collectors.toMap(PhysicalMapEdge::id, edge -> edge, (a, b) -> a, LinkedHashMap::new));
            result.put(levelKey, new PhysicalRouteMapGraph(
                    levelKey,
                    List.copyOf(nodes.values()),
                    nodes,
                    edges,
                    edgesById,
                    missingHints,
                    frames,
                    framesByStation,
                    List.copyOf(this.diagnosticsByLevel.getOrDefault(levelKey, List.of())),
                    bounds,
                    this.source.routeRevision(),
                    this.source.pipeRevision()
            ));
        }
        return result;
    }

    private List<PhysicalStationFrame> stationFramesFor(ResourceKey<Level> levelKey, Collection<PhysicalMapNode> nodes) {
        Map<UUID, List<PhysicalMapNode>> byStation = nodes.stream()
                .filter(node -> node.kind() == PhysicalNodeKind.PLATFORM)
                .filter(node -> node.stationGroupId().isPresent())
                .collect(Collectors.groupingBy(node -> node.stationGroupId().get(), LinkedHashMap::new, Collectors.toList()));
        List<PhysicalStationFrame> frames = new ArrayList<>();
        for (Map.Entry<UUID, List<PhysicalMapNode>> entry : byStation.entrySet()) {
            StationGroup station = this.stationsById.get(entry.getKey());
            if (station == null) {
                continue;
            }
            Aabb2 bounds = Aabb2.empty();
            double y = 0.0D;
            for (PhysicalMapNode node : entry.getValue()) {
                bounds = bounds.include(node.worldX(), node.worldZ());
                y += node.worldY();
            }
            double inflate = Math.max(12.0D, 18.0D / Math.max(0.5D, FullRouteMapConfig.DEFAULT_ZOOM));
            bounds = bounds.inflate(inflate);
            List<String> nodeIds = entry.getValue().stream().map(PhysicalMapNode::id).sorted().toList();
            List<UUID> routeLineIds = this.routeLineIdsByStation.getOrDefault(station.id(), Set.of()).stream().sorted().toList();
            frames.add(new PhysicalStationFrame(
                    station.id(),
                    levelKey,
                    station.primaryName(),
                    nodeIds,
                    routeLineIds,
                    bounds.centerX(),
                    bounds.centerY(),
                    entry.getValue().isEmpty() ? station.stationBlockPos().getY() : y / entry.getValue().size(),
                    bounds
            ));
        }
        return frames;
    }

    private void addNode(PhysicalMapNode node) {
        this.nodesByLevel.computeIfAbsent(node.levelKey(), ignored -> new LinkedHashMap<>()).put(node.id(), node);
    }

    private void addEdge(PhysicalMapEdge edge) {
        this.edgesByLevel.computeIfAbsent(edge.levelKey(), ignored -> new ArrayList<>()).add(edge);
    }

    private void addMissingCrossDimensionHint(
            RouteLine line,
            RouteLayout layout,
            UUID sectionId,
            int layoutIndex,
            UUID fromStopId,
            UUID toStopId,
            ResourceKey<Level> levelKey,
            ResourceKey<Level> targetLevelKey,
            Vec3 fromCenter,
            Vec3 toCenter
    ) {
        Vec2 direction = missingCrossDimensionDirection(fromCenter, toCenter, sectionId, fromStopId);
        PhysicalMissingCrossDimensionPathHint hint = new PhysicalMissingCrossDimensionPathHint(
                "physical-missing-cross:" + sectionId + ":" + levelKey.identifier() + ":" + fromStopId,
                levelKey,
                platformNodeId(fromStopId),
                line.id(),
                layout.id(),
                sectionId,
                layoutIndex,
                fromStopId,
                toStopId,
                targetLevelKey,
                direction.x(),
                direction.y()
        );
        this.missingCrossDimensionHintsByLevel.computeIfAbsent(levelKey, ignored -> new ArrayList<>()).add(hint);
    }

    private static Vec2 missingCrossDimensionDirection(Vec3 fromCenter, Vec3 toCenter, UUID sectionId, UUID fromStopId) {
        double dx = toCenter.x - fromCenter.x;
        double dz = toCenter.z - fromCenter.z;
        double length = Math.hypot(dx, dz);
        if (length >= 0.001D) {
            return new Vec2(dx / length, dz / length);
        }
        int hash = sectionId.hashCode() * 31 + fromStopId.hashCode();
        double angle = (hash & 0xFFFF) / 65535.0D * Math.PI * 2.0D;
        return new Vec2(Math.cos(angle), Math.sin(angle));
    }

    private void diagnostic(ResourceKey<Level> levelKey, DiagnosticType type, String message) {
        if (levelKey == null) {
            this.source.stationGroups().stream()
                    .map(StationGroup::levelKey)
                    .distinct()
                    .forEach(key -> this.diagnosticsByLevel.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new MapBuildDiagnostic(type, message)));
            return;
        }
        this.diagnosticsByLevel.computeIfAbsent(levelKey, ignored -> new ArrayList<>()).add(new MapBuildDiagnostic(type, message));
    }

    private static List<Vec3> sampleConnection(PipeConnection connection) {
        double length = connection.length();
        int samples = Math.max(2, Math.min(MAX_CONNECTION_SAMPLES, (int) Math.ceil(length / PATH_SAMPLE_STEP_BLOCKS) + 1));
        List<Vec3> result = new ArrayList<>(samples + 1);
        for (int i = 0; i <= samples; i++) {
            double distance = length * i / samples;
            result.add(connection.positionAt(distance));
        }
        return result;
    }

    private static List<Vec3> reversed(List<Vec3> values) {
        List<Vec3> result = new ArrayList<>(values);
        java.util.Collections.reverse(result);
        return result;
    }

    private static Vec3 midpoint(PipeConnection connection) {
        return connection.positionAt(connection.length() * 0.5D);
    }

    private static Vec3 anchorCenter(PipeAnchorId anchorId) {
        return Vec3.atCenterOf(anchorId.blockPos());
    }

    private static double lengthOf(List<PathConnection> connections) {
        return connections.stream().mapToDouble(path -> path.connection().length()).sum();
    }

    private static Aabb2 boundsFor(List<Vec2> points) {
        Aabb2 bounds = Aabb2.empty();
        for (Vec2 point : points) {
            bounds = bounds.include(point.x(), point.y());
        }
        return bounds;
    }

    private static List<Vec2> simplify(List<Vec2> points, double epsilon) {
        if (points.size() <= 2 || epsilon <= 0.0D) {
            return points;
        }
        boolean[] keep = new boolean[points.size()];
        keep[0] = true;
        keep[points.size() - 1] = true;
        simplifyRange(points, keep, 0, points.size() - 1, epsilon);
        List<Vec2> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (keep[i]) {
                result.add(points.get(i));
            }
        }
        return result.size() < 2 ? points : result;
    }

    private static void simplifyRange(List<Vec2> points, boolean[] keep, int start, int end, double epsilon) {
        if (end <= start + 1) {
            return;
        }
        Vec2 a = points.get(start);
        Vec2 b = points.get(end);
        double bestDistance = -1.0D;
        int bestIndex = -1;
        for (int i = start + 1; i < end; i++) {
            double distance = distanceToSegment(points.get(i), a, b);
            if (distance > bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        if (bestDistance > epsilon && bestIndex >= 0) {
            keep[bestIndex] = true;
            simplifyRange(points, keep, start, bestIndex, epsilon);
            simplifyRange(points, keep, bestIndex, end, epsilon);
        }
    }

    private static double distanceToSegment(Vec2 p, Vec2 a, Vec2 b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double lengthSqr = dx * dx + dy * dy;
        if (lengthSqr <= 1.0E-9D) {
            return p.distanceTo(a);
        }
        double t = Math.max(0.0D, Math.min(1.0D, ((p.x() - a.x()) * dx + (p.y() - a.y()) * dy) / lengthSqr));
        return p.distanceTo(new Vec2(a.x() + dx * t, a.y() + dy * t));
    }

    private static String platformNodeId(UUID platformStopId) {
        return "platform:" + platformStopId;
    }

    private static String foldNodeId(PipeAnchorId anchorId) {
        return "fold:" + anchorId.levelKey().identifier() + ":" + anchorId.blockPos().asLong();
    }

    private static String edgeId(UUID layoutId, UUID sectionId, int layoutIndex, int runIndex, ResourceKey<Level> levelKey) {
        return "physical:" + levelKey.identifier() + ":" + layoutId + ":" + sectionId + ":" + layoutIndex + ":" + runIndex;
    }

    private static UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private record PathConnection(PipeConnectionRef ref, PipeConnection connection) {
    }

    private record FoldTransition(PipeAnchorId localAnchor, PipeAnchorId peerAnchor) {
    }
}
