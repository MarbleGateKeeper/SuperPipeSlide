package dev.marblegate.superpipeslide.common.core.geometry;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class SlideGeometry {
    private static final int PROJECTION_SAMPLES = 48;

    private SlideGeometry() {
    }

    public static Projection project(PipeConnection connection, Vec3 point) {
        double totalLength = connection.length();
        if (totalLength < 1.0E-6D) {
            Vec3 from = connection.fromSurface();
            return new Projection(from, 0.0D, 0.0D, point.distanceTo(from));
        }

        Projection best = null;
        double walked = 0.0D;
        Vec3 previous = connection.positionAtT(0.0D);
        for (int i = 1; i <= PROJECTION_SAMPLES; i++) {
            double sampleT = (double) i / PROJECTION_SAMPLES;
            Vec3 next = connection.positionAtT(sampleT);
            Projection candidate = projectOnSampleSegment(point, previous, next, (double) (i - 1) / PROJECTION_SAMPLES, sampleT, walked);
            if (best == null || candidate.distance() < best.distance()) {
                best = candidate;
            }
            walked += previous.distanceTo(next);
            previous = next;
        }

        return best == null ? new Projection(connection.fromSurface(), 0.0D, 0.0D, point.distanceTo(connection.fromSurface())) : best;
    }

    private static Projection projectOnSampleSegment(Vec3 point, Vec3 from, Vec3 to, double fromT, double toT, double walkedBeforeSegment) {
        Vec3 axis = to.subtract(from);
        double lengthSqr = axis.lengthSqr();
        if (lengthSqr < 1.0E-6D) {
            return new Projection(from, fromT, walkedBeforeSegment, point.distanceTo(from));
        }

        double segmentRawT = point.subtract(from).dot(axis) / lengthSqr;
        double segmentT = Mth.clamp(segmentRawT, 0.0D, 1.0D);
        Vec3 closest = from.add(axis.scale(segmentT));
        double rawT = Mth.lerp(segmentRawT, fromT, toT);
        double distanceOnConnection = walkedBeforeSegment + Math.sqrt(lengthSqr) * segmentT;
        return new Projection(closest, rawT, distanceOnConnection, point.distanceTo(closest));
    }

    public record Projection(Vec3 closestPoint, double rawT, double distanceOnConnection, double distance) {
        public boolean withinSegment(double extra) {
            return this.rawT >= -extra && this.rawT <= 1.0D + extra;
        }
    }
}
