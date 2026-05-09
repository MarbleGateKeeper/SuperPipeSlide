package dev.marblegate.superpipeslide.client.fullmap.schematic.visual;

import dev.marblegate.superpipeslide.client.fullmap.model.MapEdgeOccurrence;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SemanticEdgeKind;

import java.util.List;
import java.util.UUID;

public record VisualEdgePath(
        String edgeId,
        NodeId from,
        NodeId to,
        SemanticEdgeKind kind,
        List<UUID> routeLineIds,
        List<MapEdgeOccurrence> occurrences,
        List<Vec2> points,
        List<VisualLane> lanes,
        VisualHitShape hitShape,
        Aabb2 bounds,
        boolean fallback
) {
    public VisualEdgePath {
        routeLineIds = routeLineIds.stream().distinct().sorted().toList();
        occurrences = List.copyOf(occurrences);
        points = List.copyOf(points);
        lanes = List.copyOf(lanes);
    }
}
