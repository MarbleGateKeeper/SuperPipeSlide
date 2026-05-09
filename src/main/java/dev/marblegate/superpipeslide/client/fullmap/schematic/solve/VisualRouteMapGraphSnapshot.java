package dev.marblegate.superpipeslide.client.fullmap.schematic.solve;

import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualNode;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualRouteMapGraph;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public record VisualRouteMapGraphSnapshot(Map<NodeId, Position> positions) {
    public static VisualRouteMapGraphSnapshot of(VisualRouteMapGraph graph) {
        return new VisualRouteMapGraphSnapshot(graph.nodes().stream()
                .collect(Collectors.toMap(VisualNode::id, node -> new Position(node.x(), node.z()))));
    }

    public Optional<Position> position(NodeId nodeId) {
        return Optional.ofNullable(this.positions.get(nodeId));
    }

    public record Position(double x, double z) {
    }
}
