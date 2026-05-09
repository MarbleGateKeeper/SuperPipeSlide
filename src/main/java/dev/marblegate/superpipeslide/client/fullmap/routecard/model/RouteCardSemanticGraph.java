package dev.marblegate.superpipeslide.client.fullmap.routecard.model;

import dev.marblegate.superpipeslide.client.fullmap.routecard.diagnostic.RouteCardDiagnostic;

import java.util.List;
import java.util.UUID;

public record RouteCardSemanticGraph(
        UUID routeLineId,
        UUID routeLayoutId,
        List<RouteCardNode> nodes,
        List<RouteCardEdge> edges,
        List<RouteCardSegment> segments,
        List<RouteCardDiagnostic> diagnostics,
        RouteCardSummary summary
) {
    public RouteCardSemanticGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        segments = List.copyOf(segments);
        diagnostics = List.copyOf(diagnostics);
    }
}
