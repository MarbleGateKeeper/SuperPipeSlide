package dev.marblegate.superpipeslide.client.fullmap.routecard.visual;

import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import java.util.List;

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
}
