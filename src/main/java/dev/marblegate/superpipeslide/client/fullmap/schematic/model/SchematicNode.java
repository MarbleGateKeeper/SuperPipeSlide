package dev.marblegate.superpipeslide.client.fullmap.schematic.model;

import dev.marblegate.superpipeslide.client.fullmap.model.MapNode;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeKind;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record SchematicNode(
        NodeId id,
        NodeKind kind,
        double worldX,
        double worldZ,
        double worldY,
        String label,
        List<UUID> routeLineIds,
        Optional<NodeId> clusterId,
        double anchorWeight,
        double maxDisplacement,
        int importance
) {
    public SchematicNode {
        routeLineIds = List.copyOf(routeLineIds);
        clusterId = clusterId == null ? Optional.empty() : clusterId;
    }

    public static SchematicNode from(MapNode node, double anchorWeight, double maxDisplacement, int importance) {
        return new SchematicNode(
                node.id(),
                node.kind(),
                node.worldX(),
                node.worldZ(),
                node.worldY(),
                node.label(),
                node.routeLineIds(),
                node.clusterId(),
                anchorWeight,
                maxDisplacement,
                importance
        );
    }
}
