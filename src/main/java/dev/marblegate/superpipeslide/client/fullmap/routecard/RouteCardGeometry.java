package dev.marblegate.superpipeslide.client.fullmap.routecard;

import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNode;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNodeKind;
import java.util.List;

/**
 * Shared 2D geometry and node metrics for the route-card pipeline (semantic layout,
 * physical layout, and renderer). Previously each of the three stages carried its own
 * copy of these helpers and they had already started to drift (three different segment
 * epsilons and two different station priorities); keep a single source of truth here.
 */
public final class RouteCardGeometry {
    private RouteCardGeometry() {}

    /** Degenerate-segment threshold shared by all distance queries. */
    private static final double SEGMENT_EPSILON = 1.0E-6D;

    public static double distanceToSegment(Vec2 point, Vec2 a, Vec2 b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double len2 = dx * dx + dy * dy;
        if (len2 <= SEGMENT_EPSILON) {
            return point.distanceTo(a);
        }
        double t = ((point.x() - a.x()) * dx + (point.y() - a.y()) * dy) / len2;
        double clamped = Math.max(0.0D, Math.min(1.0D, t));
        return point.distanceTo(new Vec2(a.x() + dx * clamped, a.y() + dy * clamped));
    }

    public static double distanceToPolyline(Vec2 point, List<Vec2> points) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i + 1 < points.size(); i++) {
            best = Math.min(best, distanceToSegment(point, points.get(i), points.get(i + 1)));
        }
        return best;
    }

    public static Aabb2 boundsFor(List<Vec2> points) {
        Aabb2 bounds = Aabb2.empty();
        for (Vec2 point : points) {
            bounds = bounds.include(point.x(), point.y());
        }
        return bounds;
    }

    /** Base (zoom-independent) icon radius per node kind, shared by layout and renderer. */
    public static double baseRadius(RouteCardNodeKind kind) {
        return switch (kind) {
            case STATION -> 6.0D;
            case PORTAL_BOUNDARY -> 5.2D;
            case FOLD_BOUNDARY -> 6.0D;
            case MISSING_PATH_BOUNDARY -> 3.5D;
        };
    }

    /**
     * Label-placement priority per node. The two historical copies drifted apart
     * (station 650 in the semantic solver vs 680 in the physical builder); unified on
     * 650 so warning-style boundary labels keep their edge over most station labels.
     */
    public static int nodePriority(RouteCardNode node) {
        return switch (node.kind()) {
            case STATION -> 650;
            case PORTAL_BOUNDARY -> 755;
            case FOLD_BOUNDARY -> 760;
            case MISSING_PATH_BOUNDARY -> 720;
        } + Math.max(0, 100 - node.layoutOccurrence());
    }
}
