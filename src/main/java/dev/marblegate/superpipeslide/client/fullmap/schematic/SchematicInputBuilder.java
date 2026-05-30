package dev.marblegate.superpipeslide.client.fullmap.schematic;

import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapLayoutMode;
import dev.marblegate.superpipeslide.client.fullmap.diagnostic.MissingCrossDimensionPathHint;
import dev.marblegate.superpipeslide.client.fullmap.model.MapDimensionGraph;
import dev.marblegate.superpipeslide.client.fullmap.model.MapEdge;
import dev.marblegate.superpipeslide.client.fullmap.model.MapEdgeOccurrence;
import dev.marblegate.superpipeslide.client.fullmap.model.MapNode;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeKind;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SchematicEdge;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SchematicInputGraph;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SchematicNode;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SemanticEdgeKind;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SchematicInputBuilder {
    private final MapDimensionGraph graph;
    private final SchematicLayoutConfig config;

    public SchematicInputBuilder(MapDimensionGraph graph, SchematicLayoutConfig config) {
        this.graph = graph;
        this.config = config;
    }

    public SchematicInputGraph build() {
        if (this.config.layoutMode() == FullRouteMapLayoutMode.SCHEMATIC) {
            return this.buildPureLineDiagram();
        }
        Map<NodeId, SchematicNode> nodes = new LinkedHashMap<>();
        for (MapNode node : this.graph.nodes()) {
            nodes.put(node.id(), SchematicNode.from(node, this.anchorWeight(node), this.maxDisplacement(node), this.importance(node)));
        }

        List<SchematicEdge> edges = new ArrayList<>();
        for (MapEdge edge : this.graph.edges()) {
            MapNode from = this.graph.nodesById().get(edge.from());
            MapNode to = this.graph.nodesById().get(edge.to());
            if (from == null || to == null) {
                continue;
            }
            edges.add(new SchematicEdge(edge.id(), edge.from(), edge.to(), this.initialKind(edge, from, to), edge.routeLineIds(), edge.occurrences(), edge));
        }
        edges = this.markParallelCorridors(edges);

        return new SchematicInputGraph(
                this.graph.levelKey(),
                List.copyOf(nodes.values()),
                nodes,
                edges,
                this.graph.transferHints(),
                this.graph.routeRevision(),
                this.graph.pipeRevision());
    }

    private SchematicInputGraph buildPureLineDiagram() {
        Map<NodeId, SchematicNode> nodes = new LinkedHashMap<>();
        Map<SectionKey, List<EdgePart>> partsBySection = new LinkedHashMap<>();
        for (MapEdge edge : this.graph.edges()) {
            MapNode from = this.graph.nodesById().get(edge.from());
            MapNode to = this.graph.nodesById().get(edge.to());
            if (from == null || to == null) {
                continue;
            }
            for (MapEdgeOccurrence occurrence : edge.occurrences()) {
                SectionKey key = new SectionKey(occurrence.routeLineId(), occurrence.routeLayoutId(), occurrence.routeSectionId(), occurrence.layoutIndex());
                partsBySection.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new EdgePart(edge, occurrence, from, to));
            }
        }

        Map<SchematicEdgeKey, SchematicEdgeAccumulator> accumulators = new LinkedHashMap<>();
        for (Map.Entry<SectionKey, List<EdgePart>> entry : partsBySection.entrySet()) {
            this.addPureSection(entry.getKey(), entry.getValue(), nodes, accumulators);
        }
        for (MissingCrossDimensionPathHint hint : this.graph.missingCrossDimensionPathHints()) {
            this.addPortalHint(hint, nodes, accumulators);
        }

        List<SchematicEdge> edges = accumulators.values().stream()
                .map(SchematicEdgeAccumulator::edge)
                .sorted(Comparator.comparing(SchematicEdge::id))
                .toList();
        edges = this.markParallelCorridors(edges);

        return new SchematicInputGraph(
                this.graph.levelKey(),
                List.copyOf(nodes.values()),
                nodes,
                edges,
                List.of(),
                this.graph.routeRevision(),
                this.graph.pipeRevision());
    }

    private void addPureSection(SectionKey key, List<EdgePart> parts, Map<NodeId, SchematicNode> nodes, Map<SchematicEdgeKey, SchematicEdgeAccumulator> accumulators) {
        List<MapNode> stationEndpoints = new ArrayList<>();
        List<MapNode> boundaryEndpoints = new ArrayList<>();
        for (EdgePart part : parts) {
            collectEndpoint(part.from(), stationEndpoints, boundaryEndpoints);
            collectEndpoint(part.to(), stationEndpoints, boundaryEndpoints);
        }
        List<MapNode> uniqueStations = distinctNodes(stationEndpoints);
        List<MapEdgeOccurrence> occurrences = parts.stream().map(EdgePart::occurrence).distinct().toList();
        if (uniqueStations.size() >= 2) {
            MapNode first = uniqueStations.getFirst();
            MapNode second = farthestStation(first, uniqueStations);
            if (!first.id().equals(second.id())) {
                this.ensurePureStationNode(first, nodes);
                this.ensurePureStationNode(second, nodes);
                this.addPureEdge(first.id(), second.id(), SemanticEdgeKind.NORMAL, occurrences, accumulators, null);
            }
            return;
        }
        if (uniqueStations.size() == 1 && !boundaryEndpoints.isEmpty()) {
            MapNode station = uniqueStations.getFirst();
            MapNode boundary = boundaryEndpoints.getFirst();
            if (boundary.foldPeerId().map(peer -> peer.levelKey().equals(this.graph.levelKey())).orElse(true)) {
                return;
            }
            this.ensurePureStationNode(station, nodes);
            String targetKey = boundary.foldPeerId()
                    .map(peer -> peer.levelKey().identifier().toString())
                    .orElseThrow();
            NodeId portalId = this.portalNodeId(station, targetKey);
            this.upsertPortalNode(nodes, portalId, this.portalNode(portalId, station, boundary, targetKey, key.routeLineId()), key.routeLineId());
            this.addPureEdge(station.id(), portalId, SemanticEdgeKind.FOLD_ADJACENT, occurrences, accumulators, null);
        }
    }

    private void addPortalHint(MissingCrossDimensionPathHint hint, Map<NodeId, SchematicNode> nodes, Map<SchematicEdgeKey, SchematicEdgeAccumulator> accumulators) {
        MapNode from = this.graph.nodesById().get(hint.from());
        if (from == null || from.kind() != NodeKind.STATION) {
            return;
        }
        this.ensurePureStationNode(from, nodes);
        NodeId portalId = this.portalNodeId(from, hint.targetLevelKey().identifier().toString());
        this.upsertPortalNode(nodes, portalId, this.portalNode(portalId, from, hint.directionX(), hint.directionZ(), hint.targetLevelKey().identifier().toString(), hint.routeLineId()), hint.routeLineId());
        MapEdgeOccurrence occurrence = new MapEdgeOccurrence(hint.routeLineId(), hint.routeLayoutId(), hint.routeSectionId(), hint.layoutIndex(), 1, true, List.<PipeConnectionRef>of());
        this.addPureEdge(from.id(), portalId, SemanticEdgeKind.FOLD_ADJACENT, List.of(occurrence), accumulators, null);
    }

    private void ensurePureStationNode(MapNode station, Map<NodeId, SchematicNode> nodes) {
        nodes.computeIfAbsent(station.id(), ignored -> SchematicNode.from(station, this.anchorWeight(station), this.maxDisplacement(station), this.importance(station)));
    }

    private void addPureEdge(NodeId from, NodeId to, SemanticEdgeKind kind, List<MapEdgeOccurrence> occurrences, Map<SchematicEdgeKey, SchematicEdgeAccumulator> accumulators, MapEdge source) {
        if (from.equals(to) || occurrences.isEmpty()) {
            return;
        }
        SchematicEdgeKey key = SchematicEdgeKey.of(from, to, kind);
        accumulators.computeIfAbsent(key, ignored -> new SchematicEdgeAccumulator(key, source)).occurrences.addAll(occurrences);
    }

    private void upsertPortalNode(Map<NodeId, SchematicNode> nodes, NodeId portalId, SchematicNode candidate, UUID routeLineId) {
        SchematicNode existing = nodes.get(portalId);
        if (existing == null) {
            nodes.put(portalId, candidate);
            return;
        }
        List<UUID> lineIds = new ArrayList<>(existing.routeLineIds());
        if (!lineIds.contains(routeLineId)) {
            lineIds.add(routeLineId);
            lineIds.sort(Comparator.naturalOrder());
        }
        nodes.put(portalId, new SchematicNode(
                existing.id(),
                existing.kind(),
                existing.worldX(),
                existing.worldZ(),
                existing.worldY(),
                existing.label(),
                lineIds,
                existing.clusterId(),
                existing.anchorWeight(),
                existing.maxDisplacement(),
                existing.importance()));
    }

    private NodeId portalNodeId(MapNode station, String targetKey) {
        return new NodeId(NodeKind.FOLD_ANCHOR, this.graph.levelKey(), stableUuid("schematic-portal:" + this.graph.levelKey().identifier() + ":" + station.id() + ":" + targetKey), 0);
    }

    private SchematicNode portalNode(NodeId portalId, MapNode station, MapNode boundary, String targetLabel, UUID routeLineId) {
        return this.portalNode(portalId, station, boundary.worldX() - station.worldX(), boundary.worldZ() - station.worldZ(), targetLabel, routeLineId);
    }

    private SchematicNode portalNode(NodeId portalId, MapNode station, double dx, double dz, String targetLabel, UUID routeLineId) {
        double length = Math.hypot(dx, dz);
        if (length < 0.001D) {
            int hash = portalId.hashCode();
            double angle = (hash & 0xFFFF) / 65535.0D * Math.PI * 2.0D;
            dx = Math.cos(angle);
            dz = Math.sin(angle);
            length = 1.0D;
        }
        double distance = 96.0D;
        return new SchematicNode(
                portalId,
                NodeKind.FOLD_ANCHOR,
                station.worldX() + dx / length * distance,
                station.worldZ() + dz / length * distance,
                station.worldY(),
                targetLabel,
                List.of(routeLineId),
                Optional.empty(),
                1.2D,
                80.0D,
                420);
    }

    private static void collectEndpoint(MapNode node, List<MapNode> stations, List<MapNode> boundaries) {
        if (node.kind() == NodeKind.STATION) {
            stations.add(node);
        } else if (node.kind() == NodeKind.FOLD_ANCHOR) {
            boundaries.add(node);
        }
    }

    private static List<MapNode> distinctNodes(List<MapNode> nodes) {
        Map<NodeId, MapNode> result = new LinkedHashMap<>();
        for (MapNode node : nodes) {
            result.putIfAbsent(node.id(), node);
        }
        return List.copyOf(result.values());
    }

    private static MapNode farthestStation(MapNode first, List<MapNode> candidates) {
        MapNode best = first;
        double bestDistance = Double.NEGATIVE_INFINITY;
        for (MapNode candidate : candidates) {
            double distance = Math.hypot(candidate.worldX() - first.worldX(), candidate.worldZ() - first.worldZ());
            if (!candidate.id().equals(first.id()) && distance > bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static UUID stableUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private SemanticEdgeKind initialKind(MapEdge edge, MapNode from, MapNode to) {
        if (edge.from().equals(edge.to())) {
            return SemanticEdgeKind.STATION_INTERNAL;
        }
        if (from.kind() == NodeKind.STATION && to.kind() == NodeKind.STATION && intersects(from.stationGroupIds(), to.stationGroupIds())) {
            return SemanticEdgeKind.STATION_INTERNAL;
        }
        if (from.kind() == NodeKind.FOLD_ANCHOR || to.kind() == NodeKind.FOLD_ANCHOR) {
            return SemanticEdgeKind.FOLD_ADJACENT;
        }
        if (edge.routeLineIds().size() > 1) {
            return SemanticEdgeKind.SHARED_TRACK;
        }
        return SemanticEdgeKind.NORMAL;
    }

    private List<SchematicEdge> markParallelCorridors(List<SchematicEdge> sourceEdges) {
        Set<String> parallelIds = new HashSet<>();
        for (int i = 0; i < sourceEdges.size(); i++) {
            SchematicEdge first = sourceEdges.get(i);
            if (first.kind() != SemanticEdgeKind.NORMAL) {
                continue;
            }
            for (int j = i + 1; j < sourceEdges.size(); j++) {
                SchematicEdge second = sourceEdges.get(j);
                if (second.kind() != SemanticEdgeKind.NORMAL || sharesRouteLine(first, second)) {
                    continue;
                }
                if (nearParallel(first, second)) {
                    parallelIds.add(first.id());
                    parallelIds.add(second.id());
                }
            }
        }
        return sourceEdges.stream()
                .map(edge -> parallelIds.contains(edge.id()) ? edge.withKind(SemanticEdgeKind.PARALLEL_CORRIDOR) : edge)
                .sorted(Comparator.comparing(SchematicEdge::id))
                .toList();
    }

    private boolean nearParallel(SchematicEdge first, SchematicEdge second) {
        MapNode a1 = this.graph.nodesById().get(first.from());
        MapNode a2 = this.graph.nodesById().get(first.to());
        MapNode b1 = this.graph.nodesById().get(second.from());
        MapNode b2 = this.graph.nodesById().get(second.to());
        if (a1 == null || a2 == null || b1 == null || b2 == null) {
            return false;
        }
        double ax = a2.worldX() - a1.worldX();
        double ay = a2.worldZ() - a1.worldZ();
        double bx = b2.worldX() - b1.worldX();
        double by = b2.worldZ() - b1.worldZ();
        double al = Math.hypot(ax, ay);
        double bl = Math.hypot(bx, by);
        if (al < 8.0D || bl < 8.0D) {
            return false;
        }
        double aux = ax / al;
        double auy = ay / al;
        double bux = bx / bl;
        double buy = by / bl;
        double parallelDot = switch (this.config.layoutMode()) {
            case PHYSICAL -> 0.985D;
            case GEOGRAPHIC -> 0.985D;
            case PRACTICAL -> 0.97D;
            case SCHEMATIC -> 0.94D;
        };
        if (Math.abs(aux * bux + auy * buy) < parallelDot) {
            return false;
        }
        double distance = (distanceToInfiniteLine(b1.worldX(), b1.worldZ(), a1.worldX(), a1.worldZ(), aux, auy)
                + distanceToInfiniteLine(b2.worldX(), b2.worldZ(), a1.worldX(), a1.worldZ(), aux, auy)
                + distanceToInfiniteLine(a1.worldX(), a1.worldZ(), b1.worldX(), b1.worldZ(), bux, buy)
                + distanceToInfiniteLine(a2.worldX(), a2.worldZ(), b1.worldX(), b1.worldZ(), bux, buy)) * 0.25D;
        double distanceThreshold = switch (this.config.layoutMode()) {
            case PHYSICAL -> 44.0D;
            case GEOGRAPHIC -> 44.0D;
            case PRACTICAL -> Math.max(FullRouteMapConfig.CLUSTER_THRESHOLD, 56.0D);
            case SCHEMATIC -> 84.0D;
        };
        if (distance > distanceThreshold) {
            return false;
        }
        double firstMin = 0.0D;
        double firstMax = al;
        double secondA = projection(b1.worldX(), b1.worldZ(), a1.worldX(), a1.worldZ(), aux, auy);
        double secondB = projection(b2.worldX(), b2.worldZ(), a1.worldX(), a1.worldZ(), aux, auy);
        double overlap = Math.min(firstMax, Math.max(secondA, secondB)) - Math.max(firstMin, Math.min(secondA, secondB));
        return overlap >= Math.min(al, bl) * 0.28D;
    }

    private double anchorWeight(MapNode node) {
        double base = switch (node.kind()) {
            case FOLD_ANCHOR -> 3.2D;
            case CLUSTER -> 1.9D;
            case DEEP_CLUSTER -> 1.35D;
            case STATION -> node.isTransferStation() ? 2.7D : 1.65D;
        };
        return switch (this.config.layoutMode()) {
            case PHYSICAL -> base * 2.05D;
            case GEOGRAPHIC -> base * 2.05D;
            case PRACTICAL -> base * 0.82D;
            case SCHEMATIC -> base * 0.58D;
        };
    }

    private double maxDisplacement(MapNode node) {
        boolean important = node.kind() == NodeKind.FOLD_ANCHOR || node.kind() == NodeKind.CLUSTER || node.isTransferStation();
        double base = important ? this.config.importantNodeDisplacementBlocks() : this.config.maxDisplacementBlocks();
        return switch (node.kind()) {
            case FOLD_ANCHOR -> base * 0.72D;
            case CLUSTER -> base * 0.85D;
            case DEEP_CLUSTER -> base;
            case STATION -> node.isTransferStation() ? base * 0.85D : base;
        };
    }

    private int importance(MapNode node) {
        return switch (node.kind()) {
            case FOLD_ANCHOR -> 900;
            case CLUSTER -> 850;
            case DEEP_CLUSTER -> 760;
            case STATION -> node.isTransferStation() ? 700 : 250;
        } + node.routeLineIds().size() * 20;
    }

    private static boolean sharesRouteLine(SchematicEdge first, SchematicEdge second) {
        for (UUID lineId : first.routeLineIds()) {
            if (second.routeLineIds().contains(lineId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean intersects(List<UUID> first, List<UUID> second) {
        for (UUID id : first) {
            if (second.contains(id)) {
                return true;
            }
        }
        return false;
    }

    private static double distanceToInfiniteLine(double x, double y, double originX, double originY, double ux, double uy) {
        return Math.abs((x - originX) * uy - (y - originY) * ux);
    }

    private static double projection(double x, double y, double originX, double originY, double ux, double uy) {
        return (x - originX) * ux + (y - originY) * uy;
    }

    private record SectionKey(UUID routeLineId, UUID routeLayoutId, UUID routeSectionId, int layoutIndex) {}

    private record EdgePart(MapEdge edge, MapEdgeOccurrence occurrence, MapNode from, MapNode to) {}

    private record SchematicEdgeKey(NodeId from, NodeId to, SemanticEdgeKind kind) {
        static SchematicEdgeKey of(NodeId first, NodeId second, SemanticEdgeKind kind) {
            return first.compareTo(second) <= 0 ? new SchematicEdgeKey(first, second, kind) : new SchematicEdgeKey(second, first, kind);
        }

        String id() {
            return "schematic:" + this.kind + ":" + this.from + ":" + this.to;
        }
    }

    private static final class SchematicEdgeAccumulator {
        private final SchematicEdgeKey key;
        private final MapEdge source;
        private final List<MapEdgeOccurrence> occurrences = new ArrayList<>();

        private SchematicEdgeAccumulator(SchematicEdgeKey key, MapEdge source) {
            this.key = key;
            this.source = source;
        }

        private SchematicEdge edge() {
            List<UUID> lineIds = this.occurrences.stream()
                    .map(MapEdgeOccurrence::routeLineId)
                    .distinct()
                    .sorted()
                    .toList();
            SemanticEdgeKind kind = this.key.kind == SemanticEdgeKind.NORMAL && lineIds.size() > 1 ? SemanticEdgeKind.SHARED_TRACK : this.key.kind;
            return new SchematicEdge(this.key.id(), this.key.from, this.key.to, kind, lineIds, this.occurrences, this.source);
        }
    }
}
