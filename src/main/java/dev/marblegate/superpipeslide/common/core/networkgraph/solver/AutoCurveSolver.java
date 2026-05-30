package dev.marblegate.superpipeslide.common.core.networkgraph.solver;

import dev.marblegate.superpipeslide.common.core.geometry.CurveSpec;
import dev.marblegate.superpipeslide.common.core.geometry.CurveType;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionUtils;
import dev.marblegate.superpipeslide.common.core.networkgraph.branch.BranchNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class AutoCurveSolver {
    private static final double SAME_SIDE_TURN_DOT = 0.0D;
    private static final double HAIRPIN_DOT = -0.10D;
    private static final int CURVE_QUALITY_SAMPLES = 32;
    private static final int CURVE_SOLVE_ATTEMPTS = 4;
    private static final double MAX_BACKTRACK_FRACTION = 0.42D;
    private static final double MAX_LENGTH_RATIO = 3.4D;
    private static final double MAX_SEGMENT_REVERSAL_DOT = -0.72D;

    private AutoCurveSolver() {
    }

    public static Map<UUID, CurveSpec> recomputeAutoCurvesAround(PipeNetworkView view, Set<PipeAnchorId> anchors) {
        return recomputeAutoCurveSpecs(view, affectedAutoCurveIdsAround(view, anchors));
    }

    static Map<UUID, CurveSpec> recomputeAutoCurveSpecs(PipeNetworkView view, Set<UUID> affectedConnectionIds) {
        Map<UUID, CurveSpec> updatedSpecs = new LinkedHashMap<>();
        for (UUID connectionId : affectedConnectionIds) {
            Optional<PipeConnection> connection = view.connection(connectionId);
            if (connection.isEmpty()) {
                continue;
            }

            PipeConnection current = connection.get();
            CurveSpec updatedSpec = autoCurveSpecFor(view, current);
            if (!updatedSpec.equals(current.curveSpec())) {
                updatedSpecs.put(current.id(), updatedSpec);
            }
        }
        return updatedSpecs;
    }

    public static Set<UUID> affectedAutoCurveIdsAround(PipeNetworkView view, Set<PipeAnchorId> anchors) {
        if (anchors.isEmpty()) {
            return Set.of();
        }

        Set<UUID> affectedConnectionIds = new LinkedHashSet<>();
        for (PipeAnchorId anchor : anchors) {
            collectAutoCurvesFrom(view, anchor, affectedConnectionIds);
        }
        return Collections.unmodifiableSet(affectedConnectionIds);
    }

    public static CurveSpec autoCurveSpecFor(PipeNetworkView view, PipeConnection current) {
        Vec3 from = current.fromSurface();
        Vec3 to = current.toSurface();
        Vec3 axisVector = to.subtract(from);
        double chordLength = axisVector.length();
        if (chordLength < 1.0E-6D) {
            return CurveSpec.autoCurve(Vec3.ZERO, Vec3.ZERO);
        }

        Vec3 axis = axisVector.scale(1.0D / chordLength);
        Vec3 startTangent = safeNormalize(autoTangentAt(view, current, current.fromAnchor(), true), axis);
        Vec3 endTangent = safeNormalize(autoTangentAt(view, current, current.toAnchor(), false), axis);
        Vec3 startSide = endpointReliefSide(view, current, current.fromAnchor(), axis);
        Vec3 endSide = endpointReliefSide(view, current, current.toAnchor(), axis);
        if (startSide.dot(endSide) < 0.0D) {
            endSide = endSide.scale(-1.0D);
        }

        CurveSpec fallback = conservativeAutoCurve(from, to, axis, chordLength);
        for (int attempt = 0; attempt < CURVE_SOLVE_ATTEMPTS; attempt++) {
            EndpointPlan start = endpointPlan(startTangent, axis, startSide, chordLength, attempt);
            EndpointPlan end = endpointPlan(endTangent, axis, endSide, chordLength, attempt);
            List<Vec3> controlPoints = controlPointsFor(from, to, axis, chordLength, start, end);
            if (curveQualityAcceptable(from, to, axis, chordLength, controlPoints)) {
                return CurveSpec.autoCurve(start.tangent(), end.tangent(), controlPoints);
            }
        }
        return fallback;
    }

    private static void collectAutoCurvesFrom(PipeNetworkView view, PipeAnchorId startAnchor, Set<UUID> affectedConnectionIds) {
        Set<PipeAnchorId> visitedAnchors = new HashSet<>();
        List<PipeAnchorId> queue = new ArrayList<>();
        queue.add(startAnchor);

        while (!queue.isEmpty()) {
            PipeAnchorId anchor = queue.remove(queue.size() - 1);
            if (!visitedAnchors.add(anchor)) {
                continue;
            }

            for (PipeConnection connection : view.connectionsTouching(anchor)) {
                if (connection.curveSpec().type() != CurveType.AUTO_CURVE || !affectedConnectionIds.add(connection.id())) {
                    continue;
                }

                PipeConnectionUtils.targetFor(connection, anchor).ifPresent(nextAnchor -> {
                    if (view.connectionCount(nextAnchor) == 2) {
                        queue.add(nextAnchor);
                    }
                });
            }
        }
    }

    private static Vec3 autoTangentAt(PipeNetworkView view, PipeConnection current, PipeAnchorId anchor, boolean awayFromAnchor) {
        Vec3 currentChordTangent = chordTangent(current, anchor, awayFromAnchor);
        Optional<BranchNode> branchNode = view.branchNodeAt(anchor);
        if (branchNode.isPresent()) {
            Optional<PipeConnection> firstBranchConnection = branchNode.get().managedConnectionIdsInOrder().stream()
                    .filter(connectionId -> !connectionId.equals(current.id()))
                    .map(view::connection)
                    .flatMap(Optional::stream)
                    .findFirst();
            if (firstBranchConnection.isPresent()) {
                Vec3 boundaryTangent = boundaryTangentFromOther(firstBranchConnection.get(), anchor, awayFromAnchor);
                return boundaryTangent.lengthSqr() < 1.0E-6D ? currentChordTangent : boundaryTangent;
            }
        }

        List<PipeConnection> touching = view.connectionsTouching(anchor);
        if (touching.size() != 2) {
            return currentChordTangent;
        }

        PipeConnection other = touching.get(0).id().equals(current.id()) ? touching.get(1) : touching.get(0);
        if (other.curveSpec().type() == CurveType.AUTO_CURVE) {
            Vec3 chainTangent = chainTangentFor(current, other, anchor, awayFromAnchor);
            return chainTangent.lengthSqr() < 1.0E-6D ? currentChordTangent : chainTangent;
        }
        if (isSameSideTurn(current, other, anchor)) {
            Vec3 extensionTangent = extensionTangentFromOther(other, anchor, awayFromAnchor);
            return extensionTangent.lengthSqr() < 1.0E-6D ? currentChordTangent : extensionTangent;
        }
        Vec3 boundaryTangent = boundaryTangentFromOther(other, anchor, awayFromAnchor);
        return boundaryTangent.lengthSqr() < 1.0E-6D ? currentChordTangent : boundaryTangent;
    }

    private static Vec3 chordTangent(PipeConnection current, PipeAnchorId anchor, boolean awayFromAnchor) {
        Optional<PipeAnchorId> target = PipeConnectionUtils.targetFor(current, anchor);
        if (target.isEmpty()) {
            return Vec3.ZERO;
        }

        Vec3 chord = surfacePosition(target.get()).subtract(surfacePosition(anchor));
        if (chord.lengthSqr() < 1.0E-6D) {
            return Vec3.ZERO;
        }

        Vec3 normalizedChord = chord.normalize();
        return awayFromAnchor ? normalizedChord : normalizedChord.scale(-1.0D);
    }

    private static Vec3 chainTangentFor(PipeConnection current, PipeConnection other, PipeAnchorId anchor, boolean awayFromAnchor) {
        Optional<PipeAnchorId> currentTarget = PipeConnectionUtils.targetFor(current, anchor);
        Optional<PipeAnchorId> otherTarget = PipeConnectionUtils.targetFor(other, anchor);
        if (currentTarget.isEmpty() || otherTarget.isEmpty()) {
            return Vec3.ZERO;
        }

        Vec3 currentOutward = surfacePosition(currentTarget.get()).subtract(surfacePosition(anchor));
        Vec3 otherOutward = surfacePosition(otherTarget.get()).subtract(surfacePosition(anchor));
        Vec3 tangentAwayFromAnchor = currentOutward.subtract(otherOutward);
        if (tangentAwayFromAnchor.lengthSqr() < 1.0E-6D) {
            return Vec3.ZERO;
        }
        Vec3 normalized = tangentAwayFromAnchor.normalize();
        return awayFromAnchor ? normalized : normalized.scale(-1.0D);
    }

    private static boolean isSameSideTurn(PipeConnection current, PipeConnection other, PipeAnchorId anchor) {
        Optional<PipeAnchorId> currentTarget = PipeConnectionUtils.targetFor(current, anchor);
        Optional<PipeAnchorId> otherTarget = PipeConnectionUtils.targetFor(other, anchor);
        if (currentTarget.isEmpty() || otherTarget.isEmpty()) {
            return false;
        }

        Vec3 currentOutward = surfacePosition(currentTarget.get()).subtract(surfacePosition(anchor));
        Vec3 otherOutward = surfacePosition(otherTarget.get()).subtract(surfacePosition(anchor));
        if (currentOutward.lengthSqr() < 1.0E-6D || otherOutward.lengthSqr() < 1.0E-6D) {
            return false;
        }
        return currentOutward.normalize().dot(otherOutward.normalize()) > SAME_SIDE_TURN_DOT;
    }

    private static EndpointPlan endpointPlan(Vec3 rawTangent, Vec3 axis, Vec3 side, double chordLength, int attempt) {
        Vec3 tangent = safeNormalize(rawTangent, axis);
        boolean hairpin = tangent.dot(axis) < HAIRPIN_DOT && attempt < 2;

        double dot = clamp(tangent.dot(axis), -1.0D, 1.0D);
        double handleLength;
        double reliefRadius = 0.0D;
        double forwardBias = 0.0D;
        if (hairpin) {
            double severity = clamp((-dot - HAIRPIN_DOT) / (1.0D + HAIRPIN_DOT), 0.0D, 1.0D);
            double attemptScale = attempt == 0 ? 1.0D : 0.74D;
            handleLength = clampByChord(chordLength * (0.30D + 0.18D * severity) * attemptScale, 0.55D, chordLength, 0.48D);
            reliefRadius = clampByChord(chordLength * (0.30D + 0.34D * severity) * attemptScale, 0.65D, chordLength, 0.72D);
            forwardBias = chordLength * (0.08D + 0.10D * severity) * attemptScale;
        } else {
            double sharpness = 1.0D - Math.max(0.0D, dot);
            handleLength = clampByChord(chordLength * (0.25D + 0.13D * sharpness), 0.45D, chordLength, 0.42D);
        }
        return new EndpointPlan(tangent, side, handleLength, reliefRadius, forwardBias, hairpin);
    }

    private static List<Vec3> controlPointsFor(Vec3 from, Vec3 to, Vec3 axis, double chordLength, EndpointPlan start, EndpointPlan end) {
        List<Vec3> controls = new ArrayList<>();
        Vec3 firstControl = from.add(start.tangent().scale(start.handleLength()));
        controls.add(firstControl);
        if (start.hairpin()) {
            controls.add(firstControl.add(start.side().scale(start.reliefRadius())).add(axis.scale(start.forwardBias())));
        }

        Vec3 lastControl = to.subtract(end.tangent().scale(end.handleLength()));
        if (end.hairpin()) {
            controls.add(lastControl.add(end.side().scale(end.reliefRadius())).subtract(axis.scale(end.forwardBias())));
        }
        controls.add(lastControl);

        if (controls.size() == 2 && controls.get(0).distanceTo(controls.get(1)) < chordLength * 0.05D) {
            return List.of(from.add(axis.scale(chordLength * 0.30D)), to.subtract(axis.scale(chordLength * 0.30D)));
        }
        return List.copyOf(controls);
    }

    private static boolean curveQualityAcceptable(Vec3 from, Vec3 to, Vec3 axis, double chordLength, List<Vec3> controls) {
        double minProgress = -chordLength * MAX_BACKTRACK_FRACTION;
        double maxProgress = chordLength * (1.0D + MAX_BACKTRACK_FRACTION);
        double sampledLength = 0.0D;
        Vec3 previousPoint = from;
        Vec3 previousSegment = Vec3.ZERO;
        List<Vec3> samples = new ArrayList<>();
        samples.add(from);

        for (int i = 1; i <= CURVE_QUALITY_SAMPLES; i++) {
            Vec3 point = bezier(from, controls, to, (double) i / CURVE_QUALITY_SAMPLES);
            if (!isFinite(point)) {
                return false;
            }

            double progress = point.subtract(from).dot(axis);
            if (progress < minProgress || progress > maxProgress) {
                return false;
            }

            Vec3 segment = point.subtract(previousPoint);
            double segmentLength = segment.length();
            if (!Double.isFinite(segmentLength)) {
                return false;
            }
            sampledLength += segmentLength;
            if (segmentLength > 1.0E-6D && previousSegment.lengthSqr() > 1.0E-6D) {
                double reversal = segment.normalize().dot(previousSegment.normalize());
                if (reversal < MAX_SEGMENT_REVERSAL_DOT) {
                    return false;
                }
            }

            if (segmentLength > 1.0E-6D) {
                previousSegment = segment;
            }
            previousPoint = point;
            samples.add(point);
        }

        if (sampledLength > chordLength * MAX_LENGTH_RATIO) {
            return false;
        }
        return !hasNearSelfOverlap(samples, chordLength);
    }

    private static boolean hasNearSelfOverlap(List<Vec3> samples, double chordLength) {
        double minimumDistance = Math.max(0.08D, Math.min(0.28D, chordLength * 0.035D));
        double minimumDistanceSqr = minimumDistance * minimumDistance;
        for (int i = 0; i < samples.size(); i++) {
            for (int j = i + 6; j < samples.size(); j++) {
                if (i == 0 && j == samples.size() - 1) {
                    continue;
                }
                if (samples.get(i).distanceToSqr(samples.get(j)) < minimumDistanceSqr) {
                    return true;
                }
            }
        }
        return false;
    }

    private static CurveSpec conservativeAutoCurve(Vec3 from, Vec3 to, Vec3 axis, double chordLength) {
        double handle = clampByChord(chordLength * 0.28D, 0.40D, chordLength, 0.36D);
        return CurveSpec.autoCurve(axis, axis, List.of(from.add(axis.scale(handle)), to.subtract(axis.scale(handle))));
    }

    private static Vec3 endpointReliefSide(PipeNetworkView view, PipeConnection current, PipeAnchorId anchor, Vec3 axis) {
        Optional<PipeAnchorId> currentTarget = PipeConnectionUtils.targetFor(current, anchor);
        if (currentTarget.isPresent()) {
            Vec3 currentOutward = surfacePosition(currentTarget.get()).subtract(surfacePosition(anchor));
            for (PipeConnection other : view.connectionsTouching(anchor)) {
                if (other.id().equals(current.id())) {
                    continue;
                }

                Optional<PipeAnchorId> otherTarget = PipeConnectionUtils.targetFor(other, anchor);
                if (otherTarget.isEmpty()) {
                    continue;
                }

                Vec3 otherOutward = surfacePosition(otherTarget.get()).subtract(surfacePosition(anchor));
                Vec3 side = rejectFromAxis(currentOutward.subtract(otherOutward), axis);
                if (side.lengthSqr() >= 1.0E-6D) {
                    return side.normalize();
                }
            }
        }
        return stablePerpendicular(axis);
    }

    private static Vec3 rejectFromAxis(Vec3 vector, Vec3 axis) {
        return vector.subtract(axis.scale(vector.dot(axis)));
    }

    private static Vec3 stablePerpendicular(Vec3 axis) {
        Vec3 side = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-6D) {
            side = axis.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        return safeNormalize(side, new Vec3(1.0D, 0.0D, 0.0D));
    }

    private static Vec3 safeNormalize(Vec3 vector, Vec3 fallback) {
        return vector.lengthSqr() < 1.0E-6D ? fallback : vector.normalize();
    }

    private static double clampByChord(double value, double absoluteMinimum, double chordLength, double maximumFraction) {
        double maximum = Math.max(0.05D, chordLength * maximumFraction);
        double minimum = Math.min(absoluteMinimum, maximum);
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isFinite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    private static Vec3 bezier(Vec3 from, List<Vec3> controlPoints, Vec3 to, double t) {
        Vec3[] points = new Vec3[controlPoints.size() + 2];
        points[0] = from;
        for (int i = 0; i < controlPoints.size(); i++) {
            points[i + 1] = controlPoints.get(i);
        }
        points[points.length - 1] = to;

        for (int level = points.length - 1; level > 0; level--) {
            for (int i = 0; i < level; i++) {
                points[i] = points[i].lerp(points[i + 1], t);
            }
        }
        return points[0];
    }

    private static Vec3 extensionTangentFromOther(PipeConnection other, PipeAnchorId anchor, boolean awayFromAnchor) {
        Optional<PipeAnchorId> otherTarget = PipeConnectionUtils.targetFor(other, anchor);
        if (otherTarget.isEmpty()) {
            return Vec3.ZERO;
        }

        Vec3 otherOutward = surfacePosition(otherTarget.get()).subtract(surfacePosition(anchor));
        if (otherOutward.lengthSqr() < 1.0E-6D) {
            return Vec3.ZERO;
        }

        Vec3 tangent = otherOutward.normalize();
        return awayFromAnchor ? tangent.scale(-1.0D) : tangent;
    }

    private static Vec3 boundaryTangentFromOther(PipeConnection other, PipeAnchorId anchor, boolean awayFromAnchor) {
        Vec3 tangent = endpointTangentAt(other, anchor);
        if (tangent.lengthSqr() < 1.0E-6D) {
            return extensionTangentFromOther(other, anchor, awayFromAnchor);
        }

        Optional<PipeAnchorId> otherTarget = PipeConnectionUtils.targetFor(other, anchor);
        if (otherTarget.isEmpty()) {
            return tangent.normalize();
        }

        Vec3 outwardToOther = surfacePosition(otherTarget.get()).subtract(surfacePosition(anchor));
        if (outwardToOther.lengthSqr() < 1.0E-6D) {
            return tangent.normalize();
        }

        Vec3 desired = awayFromAnchor ? outwardToOther.normalize().scale(-1.0D) : outwardToOther.normalize();
        Vec3 normalizedTangent = tangent.normalize();
        return normalizedTangent.dot(desired) < 0.0D ? normalizedTangent.scale(-1.0D) : normalizedTangent;
    }

    private static Vec3 endpointTangentAt(PipeConnection connection, PipeAnchorId anchorId) {
        if (connection.fromAnchor().equals(anchorId)) {
            return connection.tangentAt(0.0D);
        }
        if (connection.toAnchor().equals(anchorId)) {
            return connection.tangentAt(connection.length());
        }
        return Vec3.ZERO;
    }

    private static Vec3 surfacePosition(PipeAnchorId anchorId) {
        return Vec3.atCenterOf(anchorId.blockPos());
    }

    private record EndpointPlan(Vec3 tangent, Vec3 side, double handleLength, double reliefRadius, double forwardBias, boolean hairpin) {
    }
}
