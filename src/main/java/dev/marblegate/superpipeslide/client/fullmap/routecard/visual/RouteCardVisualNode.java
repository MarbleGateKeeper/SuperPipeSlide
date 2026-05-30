package dev.marblegate.superpipeslide.client.fullmap.routecard.visual;

import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNode;

public record RouteCardVisualNode(RouteCardNode node, Vec2 position, int priority) {}
