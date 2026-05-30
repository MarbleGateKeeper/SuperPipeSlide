package dev.marblegate.superpipeslide.client.fullmap.builder;

import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.diagnostic.DiagnosticType;
import dev.marblegate.superpipeslide.client.fullmap.diagnostic.MapBuildDiagnostic;
import dev.marblegate.superpipeslide.client.fullmap.diagnostic.MissingCrossDimensionPathHint;
import dev.marblegate.superpipeslide.client.fullmap.model.FullRouteMapSourceSnapshot;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.model.MapCluster;
import dev.marblegate.superpipeslide.client.fullmap.model.MapDimensionGraph;
import dev.marblegate.superpipeslide.client.fullmap.model.MapEdge;
import dev.marblegate.superpipeslide.client.fullmap.model.MapEdgeOccurrence;
import dev.marblegate.superpipeslide.client.fullmap.model.MapNode;
import dev.marblegate.superpipeslide.client.fullmap.model.MapTransferHint;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeKind;
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
import dev.marblegate.superpipeslide.common.core.route.model.station.StationTransferLink;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class FullRouteMapBuilder {
    private final FullRouteMapSourceSnapshot source;
    private final Map<UUID, StationGroup> stationById = new LinkedHashMap<>();
    private final Map<UUID, PlatformStop> platformById = new LinkedHashMap<>();
    private final Map<UUID, RouteLine> lineById = new LinkedHashMap<>();
    private final Map<UUID, RouteLayout> layoutById = new LinkedHashMap<>();
    private final Map<UUID, RouteSection> sectionById = new LinkedHashMap<>();
    private final Map<UUID, Set<UUID>> platformIdsByStation = new LinkedHashMap<>();
    private final Map<UUID, Set<UUID>> routeLineIdsByStation = new LinkedHashMap<>();
    private final Map<UUID, NodeId> stationNodeIds = new HashMap<>();
    private final Map<PipeAnchorId, NodeId> foldNodeIds = new HashMap<>();
    private final Map<NodeId, PipeAnchorId> foldAnchorIdsByNode = new HashMap<>();
    private final Map<PipeAnchorId, Set<NodeId>> foldedClusterOwnersByAnchor = new HashMap<>();
    private final Map<UUID, NodeId> clusterByStationId = new HashMap<>();
    private final Map<NodeId, NodeId> parentClusterByNodeId = new HashMap<>();
    private final Map<ResourceKey<Level>, List<MapBuildDiagnostic>> diagnosticsByLevel = new LinkedHashMap<>();
    private final Map<ResourceKey<Level>, Map<NodeId, MapNode>> nodesByLevel = new LinkedHashMap<>();
    private final Map<ResourceKey<Level>, Map<NodeId, MapCluster>> clustersByLevel = new LinkedHashMap<>();
    private final Map<ResourceKey<Level>, Map<EdgeKey, EdgeAccumulator>> edgesByLevel = new LinkedHashMap<>();
    private final Map<ResourceKey<Level>, List<MissingCrossDimensionPathHint>> missingCrossDimensionHintsByLevel = new LinkedHashMap<>();
    public FullRouteMapBuilder(FullRouteMapSourceSnapshot source) {
        this.source = source;
    }
    public Map<ResourceKey<Level>, MapDimensionGraph> build() {
        this.indexSource();
        this.computeStationLineRefs();
        this.buildClusters();
        this.buildStationNodes();
        this.buildLayoutEdges();
        this.assignFoldClusterMembership();
        return this.finishGraphs();
    }

    private void indexSource() {
        this.source.stationGroups().forEach(station -> this.stationById.put(station.id(), station));
        this.source.platformStops().forEach(platform -> {
            this.platformById.put(platform.id(), platform);
            this.platformIdsByStation.computeIfAbsent(platform.stationGroupId(), ignored -> new LinkedHashSet<>()).add(platform.id());
        });
        this.source.routeLines().forEach(line -> this.lineById.put(line.id(), line));
        this.source.routeLayouts().forEach(layout -> this.layoutById.put(layout.id(), layout));
        this.source.routeSections().forEach(section -> this.sectionById.put(section.id(), section));
    }

    private void computeStationLineRefs() {
        for (RouteLayout layout : this.source.routeLayouts()) {
            RouteLine line = this.lineById.get(layout.routeLineId());
            if (line == null) {
                this.diagnostic(null, DiagnosticType.MISSING_ROUTE_LINE, "Layout " + layout.id() + " references missing route line " + layout.routeLineId());
                continue;
            }
            for (UUID platformStopId : layout.orderedPlatformStops()) {
                PlatformStop platformStop = this.platformById.get(platformStopId);
                if (platformStop == null) {
                    this.diagnostic(null, DiagnosticType.MISSING_PLATFORM_STOP, "Layout " + layout.id() + " references missing platform stop " + platformStopId);
                    continue;
                }
                this.routeLineIdsByStation.computeIfAbsent(platformStop.stationGroupId(), ignored -> new LinkedHashSet<>()).add(line.id());
            }
        }
    }

    private void buildClusters() {
        Map<ResourceKey<Level>, List<StationGroup>> byLevel = new LinkedHashMap<>();
        for (StationGroup station : this.source.stationGroups()) {
            if (this.routeLineIdsByStation.getOrDefault(station.id(), Set.of()).isEmpty()) {
                continue;
            }
            byLevel.computeIfAbsent(station.levelKey(), ignored -> new ArrayList<>()).add(station);
        }
        for (Map.Entry<ResourceKey<Level>, List<StationGroup>> entry : byLevel.entrySet()) {
            this.buildClustersForLevel(entry.getKey(), entry.getValue());
        }
    }

    private void buildClustersForLevel(ResourceKey<Level> levelKey, List<StationGroup> stations) {
        if (stations.size() < 2) {
            return;
        }
        List<ClusterAggregate> aggregates = new ArrayList<>();
        Set<UUID> deepStationIds = new HashSet<>();
        Map<UUID, List<StationGroup>> deepComponents = stationComponents(stations, FullRouteMapConfig.DEEP_CLUSTER_THRESHOLD);
        for (List<StationGroup> component : deepComponents.values()) {
            if (component.size() < 2) {
                continue;
            }
            component.sort(Comparator.comparing(StationGroup::id));
            NodeId deepId = new NodeId(NodeKind.DEEP_CLUSTER, levelKey, stableUuid("deep_cluster:" + levelKey.identifier() + ":" + component.stream().map(station -> station.id().toString()).reduce("", (a, b) -> a + "|" + b)), 0);
            MapCluster deepCluster = clusterFromStations(levelKey, deepId, component, component.stream().map(station -> new NodeId(NodeKind.STATION, levelKey, station.id(), 0)).toList());
            this.clustersByLevel.computeIfAbsent(levelKey, ignored -> new LinkedHashMap<>()).put(deepId, deepCluster);
            for (StationGroup station : component) {
                this.clusterByStationId.put(station.id(), deepId);
                deepStationIds.add(station.id());
            }
            aggregates.add(ClusterAggregate.deep(deepCluster, component));
        }
        for (StationGroup station : stations) {
            if (!deepStationIds.contains(station.id())) {
                aggregates.add(ClusterAggregate.station(station));
            }
        }
        if (aggregates.size() < 2) {
            return;
        }
        Map<UUID, ClusterAggregate> aggregateById = new LinkedHashMap<>();
        aggregates.forEach(aggregate -> aggregateById.put(aggregate.id(), aggregate));
        UnionFind aggregateUnion = new UnionFind(aggregates.stream().map(ClusterAggregate::id).toList());
        for (int i = 0; i < aggregates.size(); i++) {
            ClusterAggregate first = aggregates.get(i);
            for (int j = i + 1; j < aggregates.size(); j++) {
                ClusterAggregate second = aggregates.get(j);
                if (Math.hypot(first.worldX() - second.worldX(), first.worldZ() - second.worldZ()) < FullRouteMapConfig.CLUSTER_THRESHOLD) {
                    aggregateUnion.union(first.id(), second.id());
                }
            }
        }
        Map<UUID, List<ClusterAggregate>> components = new LinkedHashMap<>();
        for (ClusterAggregate aggregate : aggregates) {
            components.computeIfAbsent(aggregateUnion.find(aggregate.id()), ignored -> new ArrayList<>()).add(aggregate);
        }
        for (List<ClusterAggregate> component : components.values()) {
            if (component.size() < 2) {
                continue;
            }
            component.sort(Comparator.comparing(ClusterAggregate::id));
            List<StationGroup> memberStations = component.stream().flatMap(aggregate -> aggregate.stations().stream()).sorted(Comparator.comparing(StationGroup::id)).toList();
            List<NodeId> memberNodeIds = component.stream().map(ClusterAggregate::nodeId).toList();
            NodeId clusterId = new NodeId(NodeKind.CLUSTER, levelKey, stableUuid("cluster:" + levelKey.identifier() + ":" + component.stream().map(aggregate -> aggregate.id().toString()).reduce("", (a, b) -> a + "|" + b)), 0);
            MapCluster cluster = clusterFromStations(levelKey, clusterId, memberStations, memberNodeIds);
            this.clustersByLevel.computeIfAbsent(levelKey, ignored -> new LinkedHashMap<>()).put(clusterId, cluster);
            for (ClusterAggregate aggregate : component) {
                if (aggregate.deep()) {
                    this.parentClusterByNodeId.put(aggregate.nodeId(), clusterId);
                } else {
                    aggregate.stations().forEach(station -> this.clusterByStationId.put(station.id(), clusterId));
                }
            }
        }
    }

    private void buildStationNodes() {
        for (StationGroup station : this.source.stationGroups()) {
            NodeId id = new NodeId(NodeKind.STATION, station.levelKey(), station.id(), 0);
            this.stationNodeIds.put(station.id(), id);
            List<UUID> platformIds = this.platformIdsByStation.getOrDefault(station.id(), Set.of()).stream().sorted().toList();
            List<UUID> lineIds = this.routeLineIdsByStation.getOrDefault(station.id(), Set.of()).stream().sorted().toList();
            if (lineIds.isEmpty()) {
                continue;
            }
            MapNode node = new MapNode(
                    id,
                    station.levelKey(),
                    station.stationBlockPos().getX(),
                    station.stationBlockPos().getZ(),
                    station.stationBlockPos().getY(),
                    station.primaryName(),
                    NodeKind.STATION,
                    List.of(station.id()),
                    platformIds,
                    lineIds,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.ofNullable(this.clusterByStationId.get(station.id()))
            );
            this.addNode(node);
        }
        for (Map<NodeId, MapCluster> clusters : this.clustersByLevel.values()) {
            for (MapCluster cluster : clusters.values()) {
                MapNode node = new MapNode(
                        cluster.nodeId(),
                        cluster.levelKey(),
                        cluster.worldX(),
                        cluster.worldZ(),
                        cluster.worldY(),
                        cluster.label(),
                        cluster.nodeId().kind(),
                        cluster.stationGroupIds(),
                        List.of(),
                        cluster.routeLineIds(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.ofNullable(this.parentClusterByNodeId.get(cluster.nodeId()))
                );
                this.addNode(node);
            }
        }
    }

    private NodeId ensureFoldNode(PipeAnchorId anchorId) {
        return this.foldNodeIds.computeIfAbsent(anchorId, id -> {
            NodeId nodeId = new NodeId(NodeKind.FOLD_ANCHOR, id.levelKey(), stableUuid("fold:" + id.levelKey().identifier() + ":" + id.blockPos().asLong()), 0);
            Optional<PipeAnchorId> peer = ClientPipeNetworkCache.globalFoldCounterpart(id);
            this.foldAnchorIdsByNode.put(nodeId, id);
            MapNode node = new MapNode(
                    nodeId,
                    id.levelKey(),
                    id.blockPos().getX(),
                    id.blockPos().getZ(),
                    id.blockPos().getY(),
                    this.foldAnchorLabel(id),
                    NodeKind.FOLD_ANCHOR,
                    List.of(),
                    List.of(),
                    List.of(),
                    Optional.of(id),
                    peer,
                    Optional.empty()
            );
            this.addNode(node);
            return nodeId;
        });
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

    private void buildLayoutEdges() {
        for (RouteLayout layout : this.source.routeLayouts()) {
            RouteLine line = this.lineById.get(layout.routeLineId());
            if (line == null) {
                this.diagnostic(null, DiagnosticType.MISSING_ROUTE_LINE, "Layout " + layout.id() + " references missing route line " + layout.routeLineId());
                continue;
            }
            List<UUID> stops = layout.orderedPlatformStops();
            List<UUID> sections = layout.orderedSectionRefs().stream().map(ref -> ref.routeSectionId()).toList();
            if (stops.size() < 2) {
                continue;
            }
            int sectionCount = layout.loop() ? stops.size() : Math.max(0, stops.size() - 1);
            for (int index = 0; index < sectionCount; index++) {
                UUID fromStopId = stops.get(index);
                UUID toStopId = stops.get((index + 1) % stops.size());
                Optional<UUID> resolvedSectionId = this.sectionIdFor(layout, sections, index, fromStopId, toStopId);
                if (resolvedSectionId.isEmpty()) {
                    this.buildMissingSectionFallbackEdge(line, layout, stableUuid("missing-full-map-section:" + layout.id() + ":" + index), index, fromStopId, toStopId);
                    continue;
                }
                UUID sectionId = resolvedSectionId.get();
                this.buildSectionEdges(line, layout, sectionId, index, fromStopId, toStopId);
            }
        }
    }

    private Optional<UUID> sectionIdFor(RouteLayout layout, List<UUID> sections, int index, UUID fromStopId, UUID toStopId) {
        if (index < sections.size()) {
            return Optional.of(sections.get(index));
        }
        return this.sectionById.values().stream()
                .filter(section -> section.routeLayoutId().equals(layout.id()))
                .filter(section -> section.fromPlatformStopId().equals(fromStopId) && section.toPlatformStopId().equals(toStopId))
                .map(RouteSection::id)
                .findFirst();
    }

    private void buildMissingSectionFallbackEdge(RouteLine line, RouteLayout layout, UUID sectionId, int layoutIndex, UUID fromStopId, UUID toStopId) {
        PlatformStop fromStop = this.platformById.get(fromStopId);
        PlatformStop toStop = this.platformById.get(toStopId);
        if (fromStop == null || toStop == null) {
            this.diagnostic(null, DiagnosticType.MISSING_PLATFORM_STOP, "Layout " + layout.id() + " has a missing platform stop for section " + layoutIndex);
            return;
        }
        StationGroup fromStation = this.stationById.get(fromStop.stationGroupId());
        StationGroup toStation = this.stationById.get(toStop.stationGroupId());
        if (fromStation == null || toStation == null) {
            this.diagnostic(null, DiagnosticType.MISSING_STATION_GROUP, "Layout " + layout.id() + " has a missing station for section " + layoutIndex);
            return;
        }
        NodeId fromNode = this.stationNodeIds.get(fromStation.id());
        NodeId toNode = this.stationNodeIds.get(toStation.id());
        if (fromNode == null || toNode == null) {
            return;
        }
        this.diagnostic(fromStation.levelKey(), DiagnosticType.MISSING_ROUTE_SECTION, "Layout " + layout.id() + " has no route section for stop edge " + layoutIndex);
        if (fromStation.levelKey().equals(toStation.levelKey())) {
            this.addEdge(line, layout, sectionId, layoutIndex, fromNode, toNode, 1, List.of());
        } else {
            this.diagnostic(fromStation.levelKey(), DiagnosticType.MISSING_ROUTE_SECTION_PATH, "Cross-dimension stop edge " + layoutIndex + " has no route section path data");
            this.diagnostic(toStation.levelKey(), DiagnosticType.MISSING_ROUTE_SECTION_PATH, "Cross-dimension stop edge " + layoutIndex + " has no route section path data");
            this.addMissingCrossDimensionHint(line, layout, sectionId, layoutIndex, fromStation, fromNode, toStation);
            this.addMissingCrossDimensionHint(line, layout, sectionId, layoutIndex, toStation, toNode, fromStation);
        }
    }

    private void buildSectionEdges(RouteLine line, RouteLayout layout, UUID sectionId, int layoutIndex, UUID fromStopId, UUID toStopId) {
        PlatformStop fromStop = this.platformById.get(fromStopId);
        PlatformStop toStop = this.platformById.get(toStopId);
        if (fromStop == null || toStop == null) {
            this.diagnostic(null, DiagnosticType.MISSING_PLATFORM_STOP, "Section " + sectionId + " references missing platform stop");
            return;
        }
        StationGroup fromStation = this.stationById.get(fromStop.stationGroupId());
        StationGroup toStation = this.stationById.get(toStop.stationGroupId());
        if (fromStation == null || toStation == null) {
            this.diagnostic(null, DiagnosticType.MISSING_STATION_GROUP, "Section " + sectionId + " references missing station group");
            return;
        }
        NodeId fromNode = this.stationNodeIds.get(fromStation.id());
        NodeId toNode = this.stationNodeIds.get(toStation.id());
        RouteSection section = this.sectionById.get(sectionId);
        if (section == null) {
            this.diagnostic(fromStation.levelKey(), DiagnosticType.MISSING_ROUTE_SECTION, "Layout " + layout.id() + " references missing route section " + sectionId);
            return;
        }
        RouteSectionPath path = this.source.sectionPaths().get(sectionId);
        if (path == null || path.forwardConnections().isEmpty()) {
            if (fromStation.levelKey().equals(toStation.levelKey())) {
                this.addEdge(line, layout, sectionId, layoutIndex, fromNode, toNode, 1, List.of());
            } else {
                this.diagnostic(fromStation.levelKey(), DiagnosticType.MISSING_ROUTE_SECTION_PATH, "Cross-dimension section " + sectionId + " has no path data");
                this.diagnostic(toStation.levelKey(), DiagnosticType.MISSING_ROUTE_SECTION_PATH, "Cross-dimension section " + sectionId + " has no path data");
                this.addMissingCrossDimensionHint(line, layout, sectionId, layoutIndex, fromStation, fromNode, toStation);
                this.addMissingCrossDimensionHint(line, layout, sectionId, layoutIndex, toStation, toNode, fromStation);
            }
            return;
        }
        this.buildPathEdges(line, layout, sectionId, layoutIndex, fromNode, toNode, path.forwardConnections());
    }

    private void buildPathEdges(RouteLine line, RouteLayout layout, UUID sectionId, int layoutIndex, NodeId fromNode, NodeId toNode, List<PipeConnectionRef> pathRefs) {
        NodeId currentNode = fromNode;
        List<PipeConnectionRef> slice = new ArrayList<>();
        for (int i = 0; i < pathRefs.size(); i++) {
            PipeConnectionRef ref = pathRefs.get(i);
            slice.add(ref);
            if (i + 1 >= pathRefs.size()) {
                continue;
            }
            PipeConnection currentConnection = ClientPipeNetworkCache.connection(ref).orElse(null);
            PipeConnection nextConnection = ClientPipeNetworkCache.connection(pathRefs.get(i + 1)).orElse(null);
            if (currentConnection == null || nextConnection == null) {
                this.diagnostic(ref.levelKey(), DiagnosticType.MISSING_PIPE_CONNECTION, "Route section " + sectionId + " references missing pipe connection");
                continue;
            }
            Optional<FoldTransition> transition = this.findFoldTransition(currentConnection, nextConnection);
            if (transition.isEmpty()) {
                if (!currentConnection.levelKey().equals(nextConnection.levelKey())) {
                    this.diagnostic(currentConnection.levelKey(), DiagnosticType.CROSS_DIMENSION_WITHOUT_FOLD, "Route section " + sectionId + " crosses dimensions without a resolvable fold anchor");
                    this.diagnostic(nextConnection.levelKey(), DiagnosticType.CROSS_DIMENSION_WITHOUT_FOLD, "Route section " + sectionId + " crosses dimensions without a resolvable fold anchor");
                }
                continue;
            }
            FoldTransition fold = transition.get();
            NodeId localFold = this.ensureFoldNode(fold.localAnchor());
            NodeId peerFold = this.ensureFoldNode(fold.peerAnchor());
            this.addEdge(line, layout, sectionId, layoutIndex, currentNode, localFold, 1, slice);
            currentNode = peerFold;
            slice = new ArrayList<>();
        }
        this.addEdge(line, layout, sectionId, layoutIndex, currentNode, toNode, 1, slice);
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
                if (ClientPipeNetworkCache.globalFoldCounterpart(currentAnchor).filter(nextAnchor::equals).isPresent()) {
                    return Optional.of(new FoldTransition(currentAnchor, nextAnchor));
                }
                if (ClientPipeNetworkCache.globalFoldCounterpart(nextAnchor).filter(currentAnchor::equals).isPresent()) {
                    return Optional.of(new FoldTransition(currentAnchor, nextAnchor));
                }
            }
        }
        return Optional.empty();
    }

    private void addEdge(RouteLine line, RouteLayout layout, UUID sectionId, int layoutIndex, NodeId from, NodeId to, int routeDirection, List<PipeConnectionRef> pathSlice) {
        if (!from.levelKey().equals(to.levelKey())) {
            this.diagnostic(from.levelKey(), DiagnosticType.CROSS_DIMENSION_WITHOUT_FOLD, "Refused to draw cross-dimension edge in layout " + layout.id());
            this.diagnostic(to.levelKey(), DiagnosticType.CROSS_DIMENSION_WITHOUT_FOLD, "Refused to draw cross-dimension edge in layout " + layout.id());
            return;
        }
        ResourceKey<Level> levelKey = from.levelKey();
        EdgeKey key = EdgeKey.of(levelKey, from, to);
        EdgeAccumulator accumulator = this.edgesByLevel.computeIfAbsent(levelKey, ignored -> new LinkedHashMap<>()).computeIfAbsent(key, ignored -> new EdgeAccumulator(levelKey, from, to));
        accumulator.occurrences.add(new MapEdgeOccurrence(line.id(), layout.id(), sectionId, layoutIndex, routeDirection, layout.bidirectional(), pathSlice));
        this.recordFoldClusterOwner(from, to);
        this.recordFoldClusterOwner(to, from);
    }

    private void addMissingCrossDimensionHint(RouteLine line, RouteLayout layout, UUID sectionId, int layoutIndex, StationGroup fromStation, NodeId fromNode, StationGroup targetStation) {
        Vec2 direction = missingCrossDimensionDirection(fromStation, targetStation, sectionId);
        String id = "missing-cross:" + sectionId + ":" + fromStation.levelKey().identifier() + ":" + fromStation.id();
        MissingCrossDimensionPathHint hint = new MissingCrossDimensionPathHint(
                id,
                fromStation.levelKey(),
                fromNode,
                line.id(),
                layout.id(),
                sectionId,
                layoutIndex,
                targetStation.levelKey(),
                direction.x(),
                direction.y()
        );
        this.missingCrossDimensionHintsByLevel.computeIfAbsent(fromStation.levelKey(), ignored -> new ArrayList<>()).add(hint);
    }

    private static Vec2 missingCrossDimensionDirection(StationGroup fromStation, StationGroup targetStation, UUID sectionId) {
        double dx = targetStation.stationBlockPos().getX() - fromStation.stationBlockPos().getX();
        double dz = targetStation.stationBlockPos().getZ() - fromStation.stationBlockPos().getZ();
        double length = Math.hypot(dx, dz);
        if (length >= 0.001D) {
            return new Vec2(dx / length, dz / length);
        }
        int hash = sectionId.hashCode() * 31 + fromStation.id().hashCode();
        double angle = (hash & 0xFFFF) / 65535.0D * Math.PI * 2.0D;
        return new Vec2(Math.cos(angle), Math.sin(angle));
    }

    private void recordFoldClusterOwner(NodeId maybeFold, NodeId other) {
        if (maybeFold.kind() != NodeKind.FOLD_ANCHOR) {
            return;
        }
        PipeAnchorId anchorId = this.foldAnchorIdsByNode.get(maybeFold);
        NodeId owner = this.collapsedClusterOwner(other);
        if (anchorId == null || owner == null) {
            return;
        }
        this.foldedClusterOwnersByAnchor.computeIfAbsent(anchorId, ignored -> new LinkedHashSet<>()).add(owner);
    }

    private NodeId collapsedClusterOwner(NodeId nodeId) {
        return switch (nodeId.kind()) {
            case STATION -> this.topClusterForStation(nodeId.primaryId());
            case DEEP_CLUSTER -> this.parentClusterByNodeId.getOrDefault(nodeId, nodeId);
            case CLUSTER -> nodeId;
            case FOLD_ANCHOR -> null;
        };
    }

    private void assignFoldClusterMembership() {
        for (Map.Entry<PipeAnchorId, NodeId> entry : this.foldNodeIds.entrySet()) {
            PipeAnchorId anchorId = entry.getKey();
            Optional<NodeId> spatialOwner = this.spatialClusterOwnerForFold(anchorId, entry.getValue());
            if (spatialOwner.isPresent()) {
                this.replaceFoldCluster(entry.getValue(), spatialOwner.get());
                continue;
            }
            Optional<PipeAnchorId> peer = ClientPipeNetworkCache.globalFoldCounterpart(anchorId);
            if (peer.isEmpty()) {
                continue;
            }
            Set<NodeId> localOwners = this.foldedClusterOwnersByAnchor.getOrDefault(anchorId, Set.of());
            Set<NodeId> peerOwners = this.foldedClusterOwnersByAnchor.getOrDefault(peer.get(), Set.of());
            Optional<NodeId> sharedOwner = localOwners.stream()
                    .filter(peerOwners::contains)
                    .sorted()
                    .findFirst();
            sharedOwner.ifPresent(owner -> this.replaceFoldCluster(entry.getValue(), owner));
        }
    }

    private Optional<NodeId> spatialClusterOwnerForFold(PipeAnchorId anchorId, NodeId foldNodeId) {
        Map<NodeId, MapNode> nodeMap = this.nodesByLevel.getOrDefault(anchorId.levelKey(), Map.of());
        MapNode foldNode = nodeMap.get(foldNodeId);
        if (foldNode == null) {
            return Optional.empty();
        }
        Optional<NodeId> deepOwner = this.nearestClusterContainingPoint(nodeMap, anchorId.levelKey(), foldNode.worldX(), foldNode.worldZ(), NodeKind.DEEP_CLUSTER, FullRouteMapConfig.DEEP_CLUSTER_THRESHOLD);
        if (deepOwner.isPresent()) {
            return deepOwner;
        }
        return this.nearestClusterContainingPoint(nodeMap, anchorId.levelKey(), foldNode.worldX(), foldNode.worldZ(), NodeKind.CLUSTER, FullRouteMapConfig.CLUSTER_THRESHOLD);
    }

    private Optional<NodeId> nearestClusterContainingPoint(Map<NodeId, MapNode> nodeMap, ResourceKey<Level> levelKey, double worldX, double worldZ, NodeKind kind, double threshold) {
        NodeId best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (MapCluster cluster : this.clustersByLevel.getOrDefault(levelKey, Map.of()).values()) {
            if (cluster.nodeId().kind() != kind) {
                continue;
            }
            double distance = this.distanceToClusterMember(nodeMap, cluster, worldX, worldZ);
            if (distance < threshold && distance < bestDistance) {
                best = cluster.nodeId();
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    private double distanceToClusterMember(Map<NodeId, MapNode> nodeMap, MapCluster cluster, double worldX, double worldZ) {
        double best = Math.hypot(cluster.worldX() - worldX, cluster.worldZ() - worldZ);
        for (UUID stationId : cluster.stationGroupIds()) {
            NodeId stationNodeId = this.stationNodeIds.get(stationId);
            MapNode stationNode = stationNodeId == null ? null : nodeMap.get(stationNodeId);
            if (stationNode != null) {
                best = Math.min(best, Math.hypot(stationNode.worldX() - worldX, stationNode.worldZ() - worldZ));
            }
        }
        for (NodeId memberId : cluster.memberNodeIds()) {
            MapNode memberNode = nodeMap.get(memberId);
            if (memberNode != null) {
                best = Math.min(best, Math.hypot(memberNode.worldX() - worldX, memberNode.worldZ() - worldZ));
            }
        }
        return best;
    }

    private void replaceFoldCluster(NodeId foldNodeId, NodeId clusterId) {
        MapNode node = this.nodesByLevel.getOrDefault(foldNodeId.levelKey(), Map.of()).get(foldNodeId);
        if (node == null) {
            return;
        }
        MapNode updated = new MapNode(
                node.id(),
                node.levelKey(),
                node.worldX(),
                node.worldZ(),
                node.worldY(),
                node.label(),
                node.kind(),
                node.stationGroupIds(),
                node.platformStopIds(),
                node.routeLineIds(),
                node.foldAnchorId(),
                node.foldPeerId(),
                Optional.of(clusterId)
        );
        this.nodesByLevel.computeIfAbsent(foldNodeId.levelKey(), ignored -> new LinkedHashMap<>()).put(foldNodeId, updated);
    }

    private Map<ResourceKey<Level>, MapDimensionGraph> finishGraphs() {
        Set<ResourceKey<Level>> dimensions = new LinkedHashSet<>();
        dimensions.addAll(this.nodesByLevel.keySet());
        dimensions.addAll(this.edgesByLevel.keySet());
        dimensions.addAll(this.clustersByLevel.keySet());
        dimensions.addAll(this.diagnosticsByLevel.keySet());
        dimensions.addAll(this.missingCrossDimensionHintsByLevel.keySet());
        Map<ResourceKey<Level>, MapDimensionGraph> graphs = new LinkedHashMap<>();
        for (ResourceKey<Level> levelKey : dimensions.stream().sorted(Comparator.comparing(level -> level.identifier().toString())).toList()) {
            Map<NodeId, MapNode> nodeMap = this.nodesByLevel.getOrDefault(levelKey, Map.of());
            List<MapEdge> edges = this.edgesByLevel.getOrDefault(levelKey, Map.of()).values().stream()
                    .map(accumulator -> accumulator.toEdge(nodeMap))
                    .toList();
            List<MapCluster> clusters = this.clustersByLevel.getOrDefault(levelKey, Map.of()).values().stream().toList();
            List<MapTransferHint> transferHints = this.buildTransferHints(levelKey, nodeMap, edges);
            Aabb2 bounds = Aabb2.empty();
            for (MapNode node : nodeMap.values()) {
                bounds = bounds.include(node.worldX(), node.worldZ());
            }
            if (bounds.isEmpty()) {
                this.diagnostic(levelKey, DiagnosticType.EMPTY_DIMENSION, "Dimension has no stations or fold anchors");
                bounds = Aabb2.around(0.0D, 0.0D, 32.0D);
            }
            graphs.put(levelKey, new MapDimensionGraph(
                    levelKey,
                    List.copyOf(nodeMap.values()),
                    nodeMap,
                    edges,
                    transferHints,
                    this.missingCrossDimensionHintsByLevel.getOrDefault(levelKey, List.of()),
                    clusters,
                    this.clustersByLevel.getOrDefault(levelKey, Map.of()),
                    this.diagnosticsByLevel.getOrDefault(levelKey, List.of()),
                    bounds.inflate(32.0D),
                    this.source.routeRevision(),
                    this.source.pipeRevision()
            ));
        }
        return graphs;
    }

    private List<MapTransferHint> buildTransferHints(ResourceKey<Level> levelKey, Map<NodeId, MapNode> nodeMap, List<MapEdge> edges) {
        List<MapTransferHint> hints = new ArrayList<>();
        for (StationTransferLink link : this.source.stationTransferLinks()) {
            StationGroup firstStation = this.stationById.get(link.firstStationGroupId());
            StationGroup secondStation = this.stationById.get(link.secondStationGroupId());
            if (firstStation == null || secondStation == null || !firstStation.levelKey().equals(levelKey) || !secondStation.levelKey().equals(levelKey)) {
                continue;
            }
            NodeId from = this.stationNodeIds.get(firstStation.id());
            NodeId to = this.stationNodeIds.get(secondStation.id());
            if (from == null || to == null || from.equals(to)) {
                continue;
            }
            MapNode fromNode = nodeMap.get(from);
            MapNode toNode = nodeMap.get(to);
            if (fromNode == null || toNode == null) {
                continue;
            }
            double distance = Math.hypot(fromNode.worldX() - toNode.worldX(), fromNode.worldZ() - toNode.worldZ());
            hints.add(new MapTransferHint("transfer:" + link.id(), levelKey, from, to, distance));
        }
        return hints;
    }

    private NodeId topClusterForStation(UUID stationId) {
        NodeId cluster = this.clusterByStationId.get(stationId);
        if (cluster == null) {
            return null;
        }
        return this.parentClusterByNodeId.getOrDefault(cluster, cluster);
    }

    private void addNode(MapNode node) {
        this.nodesByLevel.computeIfAbsent(node.levelKey(), ignored -> new LinkedHashMap<>()).put(node.id(), node);
    }

    private void diagnostic(ResourceKey<Level> levelKey, DiagnosticType type, String message) {
        if (levelKey == null) {
            this.source.stationGroups().stream().findFirst().map(StationGroup::levelKey).ifPresentOrElse(
                    key -> this.diagnostic(key, type, message),
                    () -> this.diagnosticsByLevel.computeIfAbsent(Level.OVERWORLD, ignored -> new ArrayList<>()).add(new MapBuildDiagnostic(type, message))
            );
            return;
        }
        this.diagnosticsByLevel.computeIfAbsent(levelKey, ignored -> new ArrayList<>()).add(new MapBuildDiagnostic(type, message));
    }

    private static Map<UUID, List<StationGroup>> stationComponents(List<StationGroup> stations, double threshold) {
        UnionFind unionFind = new UnionFind(stations.stream().map(StationGroup::id).toList());
        Map<GridCell, List<StationGroup>> grid = new HashMap<>();
        for (StationGroup station : stations) {
            GridCell cell = GridCell.of(station.stationBlockPos(), threshold);
            grid.computeIfAbsent(cell, ignored -> new ArrayList<>()).add(station);
        }
        for (StationGroup station : stations) {
            GridCell cell = GridCell.of(station.stationBlockPos(), threshold);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (StationGroup other : grid.getOrDefault(new GridCell(cell.x + dx, cell.z + dz), List.of())) {
                        if (station.id().compareTo(other.id()) >= 0) {
                            continue;
                        }
                        if (distanceXZ(station.stationBlockPos(), other.stationBlockPos()) < threshold) {
                            unionFind.union(station.id(), other.id());
                        }
                    }
                }
            }
        }
        Map<UUID, List<StationGroup>> components = new LinkedHashMap<>();
        for (StationGroup station : stations) {
            components.computeIfAbsent(unionFind.find(station.id()), ignored -> new ArrayList<>()).add(station);
        }
        return components;
    }

    private MapCluster clusterFromStations(ResourceKey<Level> levelKey, NodeId clusterId, List<StationGroup> stations, List<NodeId> memberNodeIds) {
        double x = stations.stream().mapToDouble(station -> station.stationBlockPos().getX()).average().orElse(0.0D);
        double z = stations.stream().mapToDouble(station -> station.stationBlockPos().getZ()).average().orElse(0.0D);
        double y = stations.stream().mapToDouble(station -> station.stationBlockPos().getY()).average().orElse(0.0D);
        List<UUID> stationIds = stations.stream().map(StationGroup::id).toList();
        List<UUID> routeLineIds = stationIds.stream()
                .map(id -> this.routeLineIdsByStation.getOrDefault(id, Set.of()))
                .flatMap(Collection::stream)
                .distinct()
                .sorted()
                .toList();
        return new MapCluster(clusterId, levelKey, stationIds, memberNodeIds, clusterName(stations, routeLineIds), x, z, y, routeLineIds);
    }

    private static double distanceXZ(BlockPos first, BlockPos second) {
        return Math.hypot(first.getX() - second.getX(), first.getZ() - second.getZ());
    }

    private static UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String clusterName(List<StationGroup> stations, List<UUID> routeLineIds) {
        List<String> names = stations.stream().map(StationGroup::primaryName).filter(name -> !name.isBlank()).toList();
        if (names.isEmpty()) {
            return "Cluster";
        }
        String prefix = commonPrefix(names).trim();
        if (prefix.length() >= 2) {
            return prefix;
        }
        return names.getFirst() + " +" + (stations.size() - 1);
    }

    private static String commonPrefix(List<String> values) {
        if (values.isEmpty()) {
            return "";
        }
        String prefix = values.getFirst();
        for (String value : values) {
            while (!value.startsWith(prefix) && !prefix.isEmpty()) {
                prefix = prefix.substring(0, prefix.length() - 1);
            }
        }
        return prefix;
    }

    private static String edgePairId(NodeId first, NodeId second) {
        return first.compareTo(second) <= 0 ? first.primaryId() + ":" + second.primaryId() : second.primaryId() + ":" + first.primaryId();
    }

    private record GridCell(int x, int z) {
        static GridCell of(BlockPos pos, double cellSize) {
            return of(pos.getX(), pos.getZ(), cellSize);
        }

        static GridCell of(double x, double z, double cellSize) {
            return new GridCell((int) Math.floor(x / cellSize), (int) Math.floor(z / cellSize));
        }
    }

    private record FoldTransition(PipeAnchorId localAnchor, PipeAnchorId peerAnchor) {
    }

    private record ClusterAggregate(UUID id, NodeId nodeId, boolean deep, double worldX, double worldZ, List<StationGroup> stations) {
        static ClusterAggregate deep(MapCluster cluster, List<StationGroup> stations) {
            return new ClusterAggregate(cluster.nodeId().primaryId(), cluster.nodeId(), true, cluster.worldX(), cluster.worldZ(), List.copyOf(stations));
        }

        static ClusterAggregate station(StationGroup station) {
            return new ClusterAggregate(station.id(), new NodeId(NodeKind.STATION, station.levelKey(), station.id(), 0), false, station.stationBlockPos().getX(), station.stationBlockPos().getZ(), List.of(station));
        }
    }

    private record EdgeKey(ResourceKey<Level> levelKey, NodeId first, NodeId second) {
        static EdgeKey of(ResourceKey<Level> levelKey, NodeId from, NodeId to) {
            return from.compareTo(to) <= 0 ? new EdgeKey(levelKey, from, to) : new EdgeKey(levelKey, to, from);
        }
    }

    private static final class EdgeAccumulator {
        private final ResourceKey<Level> levelKey;
        private final NodeId from;
        private final NodeId to;
        private final List<MapEdgeOccurrence> occurrences = new ArrayList<>();

        private EdgeAccumulator(ResourceKey<Level> levelKey, NodeId from, NodeId to) {
            this.levelKey = levelKey;
            this.from = from;
            this.to = to;
        }

        private MapEdge toEdge(Map<NodeId, MapNode> nodeMap) {
            MapNode fromNode = nodeMap.get(this.from);
            MapNode toNode = nodeMap.get(this.to);
            Aabb2 bounds = Aabb2.empty();
            if (fromNode != null) {
                bounds = bounds.include(fromNode.worldX(), fromNode.worldZ());
            }
            if (toNode != null) {
                bounds = bounds.include(toNode.worldX(), toNode.worldZ());
            }
            String id = "edge:" + this.levelKey.identifier() + ":" + edgePairId(this.from, this.to);
            return new MapEdge(id, this.levelKey, this.from, this.to, this.occurrences, bounds.inflate(8.0D));
        }
    }

    private static final class UnionFind {
        private final Map<UUID, UUID> parent = new HashMap<>();

        private UnionFind(List<UUID> ids) {
            ids.forEach(id -> this.parent.put(id, id));
        }

        private UUID find(UUID id) {
            UUID current = this.parent.getOrDefault(id, id);
            if (current.equals(id)) {
                return current;
            }
            UUID root = this.find(current);
            this.parent.put(id, root);
            return root;
        }

        private void union(UUID first, UUID second) {
            UUID firstRoot = this.find(first);
            UUID secondRoot = this.find(second);
            if (!firstRoot.equals(secondRoot)) {
                this.parent.put(secondRoot, firstRoot);
            }
        }
    }
}
