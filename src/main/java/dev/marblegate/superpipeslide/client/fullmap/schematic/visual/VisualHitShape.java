package dev.marblegate.superpipeslide.client.fullmap.schematic.visual;

import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;

import java.util.List;

public record VisualHitShape(List<Vec2> points, double radiusBlocks, Aabb2 bounds) {
    public VisualHitShape {
        points = List.copyOf(points);
    }
}
