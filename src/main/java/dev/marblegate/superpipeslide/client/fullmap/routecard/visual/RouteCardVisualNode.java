package dev.marblegate.superpipeslide.client.fullmap.routecard.visual;


import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNode;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;

public record RouteCardVisualNode(RouteCardNode node, Vec2 position, int priority) {
}
