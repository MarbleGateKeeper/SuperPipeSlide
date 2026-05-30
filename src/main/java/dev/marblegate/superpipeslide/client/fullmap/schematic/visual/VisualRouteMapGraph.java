package dev.marblegate.superpipeslide.client.fullmap.schematic.visual;

import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SchematicQualityReport;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record VisualRouteMapGraph(
        ResourceKey<Level> levelKey,
        List<VisualNode> nodes,
        Map<NodeId, VisualNode> nodesById,
        List<VisualEdgePath> edgePaths,
        Map<String, VisualEdgePath> edgePathsById,
        List<VisualLabel> labels,
        SchematicQualityReport quality,
        Aabb2 visualBounds,
        long routeRevision,
        long pipeRevision,
        int solverVersion) {
    public VisualRouteMapGraph {
        nodes = nodes.stream().sorted(Comparator.comparing(VisualNode::id)).toList();
        nodesById = Map.copyOf(nodesById);
        edgePaths = List.copyOf(edgePaths);
        edgePathsById = Map.copyOf(edgePathsById);
        labels = List.copyOf(labels);
    }

    public Optional<VisualNode> node(NodeId id) {
        return Optional.ofNullable(this.nodesById.get(id));
    }

    public Optional<VisualEdgePath> edgePath(String edgeId) {
        return Optional.ofNullable(this.edgePathsById.get(edgeId));
    }
}
