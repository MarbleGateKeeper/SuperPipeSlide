package dev.marblegate.superpipeslide.common.core.geometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Converts any curve spec into an equivalent PATH spec for node-based editing, aiming to
 * preserve the source shape exactly or near-exactly:
 *
 * <ul>
 * <li>LINE becomes a node-less PATH (automatic handles reproduce the straight line).
 * <li>Tangent-only specs (GAZE, plain AUTO) become a single-segment PATH whose end
 * handle vectors reproduce the exact same cubic the source sampler evaluates.
 * <li>Specs with control points (solved AUTO curves, legacy CONTROLLED curves) are
 * degree-n bezier curves. They are subdivided into k cubic Hermite segments at
 * t = j/k with exact on-curve positions and exact derivatives, so a degree-3 source is
 * reproduced point-for-point and higher degrees are approximated with visually
 * negligible error.
 * </ul>
 */
public final class PathConversion {
    private static final int MAX_SUBDIVISIONS = 6;

    private PathConversion() {}

    public static CurveSpec toPath(Vec3 from, Vec3 to, CurveSpec spec) {
        if (spec.type() == CurveType.PATH) {
            return spec;
        }
        if (spec.controlPoints().isEmpty()) {
            return tangentSpecToPath(from, to, spec);
        }
        return bezierSpecToPath(from, to, spec.controlPoints());
    }

    private static CurveSpec tangentSpecToPath(Vec3 from, Vec3 to, CurveSpec spec) {
        Vec3 chord = to.subtract(from);
        double handleLength = PathCurves.endHandleLength(from, to);
        Optional<Vec3> start = handleVector(spec.startTangent(), chord, handleLength);
        Optional<Vec3> end = handleVector(spec.endTangent(), chord, handleLength);
        return CurveSpec.path(List.of(), start, end);
    }

    private static Optional<Vec3> handleVector(Optional<Vec3> tangent, Vec3 chord, double handleLength) {
        Vec3 direction = tangent.orElse(chord);
        if (direction.lengthSqr() < 1.0E-6D) {
            return Optional.empty();
        }
        return Optional.of(direction.normalize().scale(handleLength));
    }

    private static CurveSpec bezierSpecToPath(Vec3 from, Vec3 to, List<Vec3> controlPoints) {
        List<Vec3> points = new ArrayList<>(controlPoints.size() + 2);
        points.add(from);
        points.addAll(controlPoints);
        points.add(to);
        int segments = Mth.clamp(controlPoints.size() - 1, 1, MAX_SUBDIVISIONS);
        double step = 1.0D / segments;

        List<PipePathNode> nodes = new ArrayList<>(segments - 1);
        for (int j = 1; j < segments; j++) {
            double t = j * step;
            Vec3 position = bezierPoint(points, t);
            Vec3 derivative = bezierDerivative(points, t).scale(step / 3.0D);
            nodes.add(new PipePathNode(position, Optional.of(position.subtract(derivative)), Optional.of(position.add(derivative))));
        }
        Vec3 startHandle = bezierDerivative(points, 0.0D).scale(step / 3.0D);
        Vec3 endHandle = bezierDerivative(points, 1.0D).scale(step / 3.0D);
        Optional<Vec3> start = startHandle.lengthSqr() >= 1.0E-6D ? Optional.of(startHandle) : Optional.empty();
        Optional<Vec3> end = endHandle.lengthSqr() >= 1.0E-6D ? Optional.of(endHandle) : Optional.empty();
        return CurveSpec.path(nodes, start, end);
    }

    private static Vec3 bezierPoint(List<Vec3> points, double t) {
        int degree = points.size() - 1;
        Vec3 result = Vec3.ZERO;
        for (int i = 0; i <= degree; i++) {
            result = result.add(points.get(i).scale(bernstein(degree, i, t)));
        }
        return result;
    }

    private static Vec3 bezierDerivative(List<Vec3> points, double t) {
        int degree = points.size() - 1;
        Vec3 result = Vec3.ZERO;
        for (int i = 0; i < degree; i++) {
            result = result.add(points.get(i + 1).subtract(points.get(i)).scale(bernstein(degree - 1, i, t)));
        }
        return result.scale(degree);
    }

    private static double bernstein(int n, int i, double t) {
        return binomial(n, i) * Math.pow(t, i) * Math.pow(1.0D - t, n - i);
    }

    private static long binomial(int n, int k) {
        long result = 1L;
        for (int i = 0; i < k; i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
    }
}
