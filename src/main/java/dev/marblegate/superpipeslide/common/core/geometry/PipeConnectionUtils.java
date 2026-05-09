package dev.marblegate.superpipeslide.common.core.geometry;

import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class PipeConnectionUtils {
    private PipeConnectionUtils() {
    }

    public static Optional<PipeAnchorId> targetFor(PipeConnection connection, PipeAnchorId anchorId) {
        if (connection.fromAnchor().equals(anchorId)) {
            return Optional.of(connection.toAnchor());
        }
        if (connection.toAnchor().equals(anchorId)) {
            return Optional.of(connection.fromAnchor());
        }
        return Optional.empty();
    }

    public static boolean connectsSameAnchors(PipeConnection connection, PipeAnchorId first, PipeAnchorId second) {
        return connection.fromAnchor().equals(first) && connection.toAnchor().equals(second)
                || connection.fromAnchor().equals(second) && connection.toAnchor().equals(first);
    }

    public static Set<PipeAnchorId> sharedAnchors(PipeConnection first, PipeConnection second) {
        Set<PipeAnchorId> anchors = new HashSet<>();
        if (first.fromAnchor().equals(second.fromAnchor()) || first.fromAnchor().equals(second.toAnchor())) {
            anchors.add(first.fromAnchor());
        }
        if (first.toAnchor().equals(second.fromAnchor()) || first.toAnchor().equals(second.toAnchor())) {
            anchors.add(first.toAnchor());
        }
        return anchors;
    }

    public static Vec3 safeNormalize(Vec3 vector, Vec3 fallback) {
        return vector.lengthSqr() < 1.0E-8D ? fallback : vector.normalize();
    }

    public static Vec3 worldToLocal(Vec3 worldDirection, Vec3 forward, Vec3 up) {
        Vec3 normalizedForward = safeNormalize(forward, new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 right = safeNormalize(normalizedForward.cross(up), new Vec3(1.0D, 0.0D, 0.0D));
        Vec3 normalizedUp = safeNormalize(right.cross(normalizedForward), new Vec3(0.0D, 1.0D, 0.0D));
        return new Vec3(worldDirection.dot(right), worldDirection.dot(normalizedUp), worldDirection.dot(normalizedForward));
    }
}
