package dev.marblegate.superpipeslide.client.fullmap.cluster.visual;

import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;

import java.util.List;
import java.util.Optional;

public record ClusterCardVisualGraph(
        List<ClusterCardVisualNode> nodes,
        List<ClusterCardVisualEdge> edges,
        Aabb2 bounds,
        boolean fallback
) {
    public ClusterCardVisualGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public Optional<ClusterCardVisualNode> node(String id) {
        return this.nodes.stream().filter(node -> node.node().id().equals(id)).findFirst();
    }
}
