package dev.marblegate.superpipeslide.common.core.networkgraph.solver;

import dev.marblegate.superpipeslide.common.core.geometry.CurveSpec;
import dev.marblegate.superpipeslide.common.core.geometry.CurveType;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionUtils;
import dev.marblegate.superpipeslide.common.core.networkgraph.branch.BranchNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class AutoCurveSolver {
    private static final double SAME_SIDE_TURN_DOT = 0.0D;
    private static final double DEFAULT_HANDLE_SCALE = 0.32D;
    private static final double OVERSHOOT_HANDLE_SCALE = 1.15D;

    private AutoCurveSolver() {
    }

    public static Map<UUID, CurveSpec> recomputeAutoCurvesAround(PipeNetworkView view, Set<PipeAnchorId> anchors) {
        if (anchors.isEmpty()) {
            return Map.of();
        }

        Set<UUID> affectedConnectionIds = new HashSet<>();
        for (PipeAnchorId anchor : anchors) {
            collectAutoCurvesFrom(view, anchor, affectedConnectionIds);
        }

        Map<UUID, CurveSpec> updatedSpecs = new HashMap<>();
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

    public static CurveSpec autoCurveSpecFor(PipeNetworkView view, PipeConnection current) {
        Vec3 startTangent = autoTangentAt(view, current, current.fromAnchor(), true);
        Vec3 endTangent = autoTangentAt(view, current, current.toAnchor(), false);
        boolean startOvershoots = hasNonAutoSameSideTurn(view, current, current.fromAnchor());
        boolean endOvershoots = hasNonAutoSameSideTurn(view, current, current.toAnchor());
        if (!startOvershoots && !endOvershoots) {
            return CurveSpec.autoCurve(startTangent, endTangent);
        }

        double chordLength = surfacePosition(current.toAnchor()).distanceTo(surfacePosition(current.fromAnchor()));
        double defaultHandle = Math.max(0.75D, chordLength * DEFAULT_HANDLE_SCALE);
        double overshootHandle = Math.max(1.25D, chordLength * OVERSHOOT_HANDLE_SCALE);
        double startHandle = startOvershoots ? overshootHandle : defaultHandle;
        double endHandle = endOvershoots ? overshootHandle : defaultHandle;
        Vec3 firstControl = current.fromSurface().add(startTangent.normalize().scale(startHandle));
        Vec3 secondControl = current.toSurface().subtract(endTangent.normalize().scale(endHandle));
        return CurveSpec.autoCurve(startTangent, endTangent, List.of(firstControl, secondControl));
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
        Optional<PipeAnchorId> previousAnchor = PipeConnectionUtils.targetFor(other, anchor);
        Optional<PipeAnchorId> nextAnchor = PipeConnectionUtils.targetFor(current, anchor);
        if (previousAnchor.isEmpty() || nextAnchor.isEmpty()) {
            return Vec3.ZERO;
        }

        Vec3 chainTangent = surfacePosition(nextAnchor.get()).subtract(surfacePosition(previousAnchor.get()));
        if (chainTangent.lengthSqr() < 1.0E-6D) {
            return Vec3.ZERO;
        }
        return orientTangent(chainTangent.normalize(), current, anchor, awayFromAnchor);
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

    private static boolean hasNonAutoSameSideTurn(PipeNetworkView view, PipeConnection current, PipeAnchorId anchor) {
        List<PipeConnection> touching = view.connectionsTouching(anchor);
        if (touching.size() != 2) {
            return false;
        }

        PipeConnection other = touching.get(0).id().equals(current.id()) ? touching.get(1) : touching.get(0);
        return other.curveSpec().type() != CurveType.AUTO_CURVE && isSameSideTurn(current, other, anchor);
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

    private static Vec3 orientTangent(Vec3 tangent, PipeConnection current, PipeAnchorId anchor, boolean awayFromAnchor) {
        Optional<PipeAnchorId> target = PipeConnectionUtils.targetFor(current, anchor);
        if (target.isEmpty()) {
            return tangent;
        }

        Vec3 outward = surfacePosition(target.get()).subtract(surfacePosition(anchor));
        if (outward.lengthSqr() < 1.0E-6D) {
            return tangent;
        }

        boolean pointsAway = tangent.dot(outward) >= 0.0D;
        return pointsAway == awayFromAnchor ? tangent : tangent.scale(-1.0D);
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
}
