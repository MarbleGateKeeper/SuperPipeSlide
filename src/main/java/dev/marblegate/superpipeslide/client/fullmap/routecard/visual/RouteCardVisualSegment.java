package dev.marblegate.superpipeslide.client.fullmap.routecard.visual;

import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardSegment;

public record RouteCardVisualSegment(RouteCardSegment segment, Aabb2 bounds) {}
