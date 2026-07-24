package dev.marblegate.superpipeslide.common.core.geometry;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;

/**
 * Shared PATH curve math: resolution of the effective cubic bezier handles of a
 * [from, ...nodes, to] chain. Manual node handles win; missing handles fall back to
 * Catmull-Rom automatic handles (with reflected phantom points at the chain ends), so a
 * fully automatic chain stays C1-smooth. Both the connection sampler and the client-side
 * shape editor resolve handles through this class so they never disagree.
 *
 * <p>End tangents are raw handle offset vectors (not directions): the effective end
 * handles are exactly {@code from + startTangent} and {@code to - endTangent}, so any
 * source cubic can be reproduced losslessly by a single-segment PATH.
 */
public final class PathCurves {
    private PathCurves() {}

    public static Vec3 pointAt(Vec3 from, Vec3 to, List<PipePathNode> nodes, int pointIndex) {
        if (pointIndex <= 0) {
            return from;
        }
        if (pointIndex > nodes.size()) {
            return to;
        }
        return nodes.get(pointIndex - 1).position();
    }

    public static Vec3 outHandle(Vec3 from, Vec3 to, List<PipePathNode> nodes, Optional<Vec3> startTangent, int pointIndex) {
        if (pointIndex == 0) {
            Optional<Vec3> manual = startTangent.filter(tangent -> tangent.lengthSqr() >= 1.0E-6D);
            if (manual.isPresent()) {
                return from.add(manual.get());
            }
        } else if (pointIndex <= nodes.size() && nodes.get(pointIndex - 1).outHandle().isPresent()) {
            return nodes.get(pointIndex - 1).outHandle().get();
        }
        Vec3 point = pointAt(from, to, nodes, pointIndex);
        Vec3 previous = pointIndex - 1 >= 0 ? pointAt(from, to, nodes, pointIndex - 1) : point.add(point).subtract(pointAt(from, to, nodes, 1));
        Vec3 next = pointIndex <= nodes.size() ? pointAt(from, to, nodes, pointIndex + 1) : point.add(point).subtract(pointAt(from, to, nodes, nodes.size()));
        return point.add(next.subtract(previous).scale(1.0D / 6.0D));
    }

    public static Vec3 inHandle(Vec3 from, Vec3 to, List<PipePathNode> nodes, Optional<Vec3> endTangent, int pointIndex) {
        if (pointIndex == nodes.size() + 1) {
            Optional<Vec3> manual = endTangent.filter(tangent -> tangent.lengthSqr() >= 1.0E-6D);
            if (manual.isPresent()) {
                return to.subtract(manual.get());
            }
        } else if (pointIndex >= 1 && nodes.get(pointIndex - 1).inHandle().isPresent()) {
            return nodes.get(pointIndex - 1).inHandle().get();
        }
        Vec3 point = pointAt(from, to, nodes, pointIndex);
        Vec3 previous = pointIndex - 1 >= 0 ? pointAt(from, to, nodes, pointIndex - 1) : point.add(point).subtract(pointAt(from, to, nodes, 1));
        Vec3 next = pointIndex <= nodes.size() ? pointAt(from, to, nodes, pointIndex + 1) : point.add(point).subtract(pointAt(from, to, nodes, nodes.size()));
        return point.subtract(next.subtract(previous).scale(1.0D / 6.0D));
    }

    /**
     * Handle magnitude rule shared with GAZE curves, used when converting a GAZE curve
     * into a single-segment PATH so the shape survives unchanged.
     */
    public static double endHandleLength(Vec3 from, Vec3 to) {
        return Math.max(0.75D, from.distanceTo(to) * 0.32D);
    }
}
