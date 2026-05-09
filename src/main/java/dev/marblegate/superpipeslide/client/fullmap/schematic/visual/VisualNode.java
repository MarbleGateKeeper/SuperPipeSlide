package dev.marblegate.superpipeslide.client.fullmap.schematic.visual;

import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeKind;

import java.util.List;
import java.util.UUID;

public record VisualNode(
        NodeId id,
        NodeKind kind,
        double x,
        double z,
        double worldX,
        double worldZ,
        String label,
        List<UUID> routeLineIds,
        int importance,
        boolean fallbackPosition
) {
    public VisualNode {
        routeLineIds = List.copyOf(routeLineIds);
    }
}
