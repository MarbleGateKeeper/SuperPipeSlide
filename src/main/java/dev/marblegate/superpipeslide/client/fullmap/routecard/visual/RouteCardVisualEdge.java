package dev.marblegate.superpipeslide.client.fullmap.routecard.visual;

import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardEdge;
import java.util.List;

public record RouteCardVisualEdge(RouteCardEdge edge, List<Vec2> points, Aabb2 bounds) {
    public RouteCardVisualEdge {
        points = List.copyOf(points);
    }
}
