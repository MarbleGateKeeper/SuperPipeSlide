package dev.marblegate.superpipeslide.common.core.geometry;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Limits and quantization for adjustable anchor attach points. The attach point of an
 * anchor always stays inside its own block cell: each offset axis is clamped to
 * [-MAX_OFFSET, MAX_OFFSET] and snapped to a 1/16 grid so client preview and server
 * validation agree on the exact value.
 */
public final class AnchorAttachOffsets {
    public static final double MAX_OFFSET = 0.5D;
    public static final double STEP = 1.0D / 16.0D;

    private AnchorAttachOffsets() {}

    public static Vec3 sanitize(Vec3 offset) {
        return new Vec3(snap(offset.x()), snap(offset.y()), snap(offset.z()));
    }

    public static boolean isSane(Vec3 offset) {
        return Double.isFinite(offset.x())
                && Double.isFinite(offset.y())
                && Double.isFinite(offset.z())
                && Math.abs(offset.x()) <= MAX_OFFSET + 1.0E-6D
                && Math.abs(offset.y()) <= MAX_OFFSET + 1.0E-6D
                && Math.abs(offset.z()) <= MAX_OFFSET + 1.0E-6D;
    }

    private static double snap(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Mth.clamp(Math.round(value / STEP) * STEP, -MAX_OFFSET, MAX_OFFSET);
    }
}
