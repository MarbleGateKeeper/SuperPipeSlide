package dev.marblegate.superpipeslide.client.fullmap.routecard.visual;


import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNodeId;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;

public record RouteCardVisualLabel(RouteCardNodeId nodeId, String text, Vec2 position, int priority, float scale) {
}
