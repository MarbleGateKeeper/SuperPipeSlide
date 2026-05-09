package dev.marblegate.superpipeslide.client.fullmap.cluster.visual;


import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardEdge;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;

import java.util.List;

public record ClusterCardVisualEdge(ClusterCardEdge edge, List<Vec2> points, Aabb2 bounds) {
    public ClusterCardVisualEdge {
        points = List.copyOf(points);
    }
}
