package dev.marblegate.superpipeslide.common.core.geometry;

import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Optional;

public final class PipeConnectionRaycast {
    private static final int MIN_SAMPLES = 12;
    private static final int MAX_SAMPLES = 96;
    private static final double SAMPLE_DENSITY = 2.0D;

    private PipeConnectionRaycast() {
    }

    public static Optional<Hit> find(Collection<PipeConnection> connections, Vec3 rayOrigin, Vec3 rayDirection, double maxRayDistance, double maxPipeDistance) {
        Vec3 direction = rayDirection.lengthSqr() < 1.0E-6D ? Vec3.ZERO : rayDirection.normalize();
        if (direction == Vec3.ZERO) {
            return Optional.empty();
        }

        Hit best = null;
        for (PipeConnection connection : connections) {
            Optional<Hit> hit = hit(connection, rayOrigin, direction, maxRayDistance, maxPipeDistance);
            if (hit.isEmpty()) {
                continue;
            }
            if (best == null || hit.get().pipeDistance() < best.pipeDistance() || hit.get().pipeDistance() == best.pipeDistance() && hit.get().rayDistance() < best.rayDistance()) {
                best = hit.get();
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<Hit> hit(PipeConnection connection, Vec3 rayOrigin, Vec3 rayDirection, double maxRayDistance, double maxPipeDistance) {
        double length = connection.length();
        if (length < 1.0E-6D) {
            return Optional.empty();
        }

        int samples = Math.max(MIN_SAMPLES, Math.min(MAX_SAMPLES, (int) Math.ceil(length * SAMPLE_DENSITY)));
        Vec3 previous = connection.positionAt(0.0D);
        double previousDistance = 0.0D;
        Hit best = null;
        for (int i = 1; i <= samples; i++) {
            double distance = length * i / samples;
            Vec3 current = connection.positionAt(distance);
            SegmentRayClosest closest = closestSegmentRay(previous, current, rayOrigin, rayDirection, maxRayDistance);
            if (closest.pipeDistance() <= maxPipeDistance) {
                double connectionDistance = previousDistance + (distance - previousDistance) * closest.segmentT();
                Hit hit = new Hit(connection, closest.pointOnSegment(), connectionDistance, closest.rayDistance(), closest.pipeDistance());
                if (best == null || hit.pipeDistance() < best.pipeDistance() || hit.pipeDistance() == best.pipeDistance() && hit.rayDistance() < best.rayDistance()) {
                    best = hit;
                }
            }
            previous = current;
            previousDistance = distance;
        }

        return Optional.ofNullable(best);
    }

    private static SegmentRayClosest closestSegmentRay(Vec3 segmentStart, Vec3 segmentEnd, Vec3 rayOrigin, Vec3 rayDirection, double maxRayDistance) {
        Vec3 segment = segmentEnd.subtract(segmentStart);
        Vec3 originDelta = segmentStart.subtract(rayOrigin);
        double a = segment.dot(segment);
        double b = segment.dot(rayDirection);
        double c = rayDirection.dot(rayDirection);
        double d = segment.dot(originDelta);
        double e = rayDirection.dot(originDelta);
        double denominator = a * c - b * b;

        double segmentT = 0.0D;
        double rayT;
        if (denominator > 1.0E-8D) {
            segmentT = clamp((b * e - c * d) / denominator, 0.0D, 1.0D);
        }
        rayT = (b * segmentT + e) / c;
        rayT = clamp(rayT, 0.0D, maxRayDistance);

        if (a > 1.0E-8D) {
            segmentT = clamp((b * rayT - d) / a, 0.0D, 1.0D);
        }

        Vec3 pointOnSegment = segmentStart.add(segment.scale(segmentT));
        Vec3 pointOnRay = rayOrigin.add(rayDirection.scale(rayT));
        return new SegmentRayClosest(segmentT, rayT, pointOnSegment, pointOnSegment.distanceTo(pointOnRay));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Hit(PipeConnection connection, Vec3 position, double distanceOnConnection, double rayDistance, double pipeDistance) {
    }

    private record SegmentRayClosest(double segmentT, double rayDistance, Vec3 pointOnSegment, double pipeDistance) {
    }
}
