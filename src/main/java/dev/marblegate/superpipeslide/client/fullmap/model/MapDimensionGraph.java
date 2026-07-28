package dev.marblegate.superpipeslide.client.fullmap.model;

import dev.marblegate.superpipeslide.client.fullmap.diagnostic.MapBuildDiagnostic;
import dev.marblegate.superpipeslide.client.fullmap.diagnostic.MissingCrossDimensionPathHint;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record MapDimensionGraph(
        ResourceKey<Level> levelKey,
        List<MapNode> nodes,
        Map<NodeId, MapNode> nodesById,
        List<MapEdge> edges,
        Map<String, MapEdge> edgesById,
        List<MapTransferHint> transferHints,
        List<MissingCrossDimensionPathHint> missingCrossDimensionPathHints,
        List<MapCluster> clusters,
        Map<NodeId, MapCluster> clustersById,
        Map<NodeKind, Map<UUID, MapCluster>> clustersByKindAndPrimaryId,
        List<MapBuildDiagnostic> diagnostics,
        Aabb2 worldBounds,
        long routeRevision,
        long pipeRevision) {
    public MapDimensionGraph(
            ResourceKey<Level> levelKey,
            List<MapNode> nodes,
            Map<NodeId, MapNode> nodesById,
            List<MapEdge> edges,
            List<MapTransferHint> transferHints,
            List<MissingCrossDimensionPathHint> missingCrossDimensionPathHints,
            List<MapCluster> clusters,
            Map<NodeId, MapCluster> clustersById,
            List<MapBuildDiagnostic> diagnostics,
            Aabb2 worldBounds,
            long routeRevision,
            long pipeRevision) {
        this(
                levelKey,
                nodes,
                nodesById,
                edges,
                indexEdgesById(edges),
                transferHints,
                missingCrossDimensionPathHints,
                clusters,
                clustersById,
                indexClustersByKindAndPrimaryId(clustersById),
                diagnostics,
                worldBounds,
                routeRevision,
                pipeRevision);
    }

    public MapDimensionGraph {
        nodes = nodes.stream().sorted(Comparator.comparing(MapNode::id)).toList();
        nodesById = Map.copyOf(nodesById);
        edges = List.copyOf(edges);
        edgesById = Map.copyOf(edgesById);
        transferHints = List.copyOf(transferHints);
        missingCrossDimensionPathHints = List.copyOf(missingCrossDimensionPathHints);
        clusters = List.copyOf(clusters);
        clustersById = Map.copyOf(clustersById);
        clustersByKindAndPrimaryId = copyClusterIndex(clustersByKindAndPrimaryId);
        diagnostics = List.copyOf(diagnostics);
    }

    public Optional<MapNode> node(NodeId id) {
        return Optional.ofNullable(this.nodesById.get(id));
    }

    public Optional<MapEdge> edge(String id) {
        return Optional.ofNullable(this.edgesById.get(id));
    }

    public Optional<MapCluster> cluster(NodeKind kind, UUID primaryId) {
        Map<UUID, MapCluster> byPrimaryId = this.clustersByKindAndPrimaryId.get(kind);
        return byPrimaryId == null ? Optional.empty() : Optional.ofNullable(byPrimaryId.get(primaryId));
    }

    private static Map<String, MapEdge> indexEdgesById(List<MapEdge> edges) {
        Map<String, MapEdge> index = new LinkedHashMap<>();
        for (MapEdge edge : edges) {
            index.put(edge.id(), edge);
        }
        return index;
    }

    private static Map<NodeKind, Map<UUID, MapCluster>> indexClustersByKindAndPrimaryId(Map<NodeId, MapCluster> clustersById) {
        Map<NodeKind, Map<UUID, MapCluster>> index = new EnumMap<>(NodeKind.class);
        for (MapCluster cluster : clustersById.values()) {
            // Keep the first cluster per (kind, primaryId) to preserve the historical
            // stream-filter findFirst() lookup semantics.
            index.computeIfAbsent(cluster.nodeId().kind(), ignored -> new LinkedHashMap<>()).putIfAbsent(cluster.nodeId().primaryId(), cluster);
        }
        return index;
    }

    private static Map<NodeKind, Map<UUID, MapCluster>> copyClusterIndex(Map<NodeKind, Map<UUID, MapCluster>> index) {
        Map<NodeKind, Map<UUID, MapCluster>> copy = new EnumMap<>(NodeKind.class);
        index.forEach((kind, byPrimaryId) -> copy.put(kind, Map.copyOf(byPrimaryId)));
        return Map.copyOf(copy);
    }
}
