package dev.marblegate.superpipeslide.client.fullmap.model;

import dev.marblegate.superpipeslide.client.fullmap.diagnostic.MapBuildDiagnostic;
import dev.marblegate.superpipeslide.client.fullmap.diagnostic.MissingCrossDimensionPathHint;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record MapDimensionGraph(
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
    public MapDimensionGraph {
        nodes = nodes.stream().sorted(Comparator.comparing(MapNode::id)).toList();
        nodesById = Map.copyOf(nodesById);
        edges = List.copyOf(edges);
        transferHints = List.copyOf(transferHints);
        missingCrossDimensionPathHints = List.copyOf(missingCrossDimensionPathHints);
        clusters = List.copyOf(clusters);
        clustersById = Map.copyOf(clustersById);
        diagnostics = List.copyOf(diagnostics);
    }

    public Optional<MapNode> node(NodeId id) {
        return Optional.ofNullable(this.nodesById.get(id));
    }
}
