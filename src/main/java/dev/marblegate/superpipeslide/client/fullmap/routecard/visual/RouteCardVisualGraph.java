package dev.marblegate.superpipeslide.client.fullmap.routecard.visual;

import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNodeId;
import java.util.List;
import java.util.Optional;

public record RouteCardVisualGraph(
        List<RouteCardVisualNode> nodes,
        List<RouteCardVisualEdge> edges,
        List<RouteCardVisualSegment> segments,
        List<RouteCardVisualLabel> labels,
        Aabb2 bounds,
        boolean fallback) {
    public RouteCardVisualGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        segments = List.copyOf(segments);
        labels = List.copyOf(labels);
    }

    public Optional<RouteCardVisualNode> node(RouteCardNodeId id) {
        return this.nodes.stream().filter(node -> node.node().id().equals(id)).findFirst();
    }
}
