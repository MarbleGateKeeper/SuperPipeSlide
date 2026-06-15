package dev.marblegate.superpipeslide.client.core.slide;

import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRaycast;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class SlideJumpTargetResolver {
    private static final double MAX_LOOK_DISTANCE = 14.0D;
    private static final double MAX_TARGET_DISTANCE_FROM_CURRENT = 6.5D;
    private static final double DIRECT_HIT_PIPE_DISTANCE = 0.55D;
    private static final double BASE_RAY_TOLERANCE = 0.9D;
    private static final double RAY_TOLERANCE_PER_BLOCK = 0.18D;
    private static final double PIPE_SCORE_THRESHOLD = 2.85D;
    private static final double SAME_PIPE_REVERSE_MIN_ALIGNMENT = 0.52D;
    private static final double SAME_PIPE_REVERSE_MARGIN = 0.35D;
    private static final int MIN_SAMPLES = 12;
    private static final int MAX_SAMPLES = 72;
    private static final int[] SLIDE_DIRECTIONS = { 1, -1 };

    private SlideJumpTargetResolver() {}

    static Optional<Target> resolve(LocalPlayer player, ClientSlideState state, PipeConnection current, Collection<PipeConnection> candidates) {
        Vec3 currentPosition = current.positionAt(state.distanceOnConnection());
        Vec3 currentForward = safeNormalize(current.tangentAt(state.distanceOnConnection()).scale(state.direction()), player.getLookAngle());
        Vec3 eye = player.getEyePosition();
        Vec3 look = safeNormalize(player.getLookAngle(), currentForward);
        Optional<UUID> directHit = PipeConnectionRaycast.find(
                candidates.stream().filter(connection -> !connection.id().equals(current.id())).toList(),
                eye,
                look,
                MAX_LOOK_DISTANCE,
                DIRECT_HIT_PIPE_DISTANCE)
                .map(hit -> hit.connection().id());

        Target bestPipe = null;
        for (PipeConnection candidate : candidates) {
            if (candidate.id().equals(current.id()) || !candidate.levelKey().equals(current.levelKey())) {
                continue;
            }
            Optional<Target> scored = scoreCandidate(candidate, eye, look, currentPosition, currentForward, directHit);
            if (scored.isEmpty()) {
                continue;
            }
            if (bestPipe == null || scored.get().score() > bestPipe.score()) {
                bestPipe = scored.get();
            }
        }

        Optional<Target> reverse = samePipeReverseTarget(current, state, currentForward, look);
        if (bestPipe != null && bestPipe.score() >= PIPE_SCORE_THRESHOLD) {
            if (reverse.isPresent() && reverse.get().score() > bestPipe.score() + SAME_PIPE_REVERSE_MARGIN) {
                return reverse;
            }
            return Optional.of(bestPipe);
        }
        return reverse;
    }

    private static Optional<Target> scoreCandidate(PipeConnection candidate, Vec3 eye, Vec3 look, Vec3 currentPosition, Vec3 currentForward, Optional<UUID> directHit) {
        double length = candidate.length();
        if (length < 1.0E-6D) {
            return Optional.empty();
        }
        int samples = Mth.clamp((int) Math.ceil(length * 3.0D), MIN_SAMPLES, MAX_SAMPLES);
        Target best = null;
        for (int i = 0; i < samples; i++) {
            double distance = samples <= 1 ? 0.0D : length * i / (samples - 1);
            Vec3 point = candidate.positionAt(distance);
            PointScore pointScore = scorePoint(candidate, point, eye, look, currentPosition, directHit);
            if (!pointScore.accepted()) {
                continue;
            }
            Vec3 forward = safeNormalize(candidate.tangentAt(distance), currentForward);
            for (int direction : SLIDE_DIRECTIONS) {
                if (!candidate.allowsSlideDirection(direction)) {
                    continue;
                }
                Vec3 travel = forward.scale(direction);
                double lookAlong = travel.dot(look);
                double continuation = travel.dot(currentForward);
                double directionScore = Math.max(0.0D, lookAlong) * 0.62D
                        + Mth.clamp(continuation, -0.35D, 1.0D) * 0.48D;
                double score = pointScore.score() + directionScore;
                Target target = new Target(TargetKind.NEARBY_PIPE, candidate, direction, distance, score);
                if (best == null || target.score() > best.score()) {
                    best = target;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static PointScore scorePoint(PipeConnection candidate, Vec3 point, Vec3 eye, Vec3 look, Vec3 currentPosition, Optional<UUID> directHit) {
        Vec3 toPoint = point.subtract(eye);
        double distanceToEyeSqr = toPoint.lengthSqr();
        if (distanceToEyeSqr < 1.0E-6D) {
            return PointScore.rejected();
        }
        double rayDistance = toPoint.dot(look);
        if (rayDistance < -0.35D || rayDistance > MAX_LOOK_DISTANCE) {
            return PointScore.rejected();
        }
        double lateralSqr = Math.max(0.0D, distanceToEyeSqr - rayDistance * rayDistance);
        double lateralDistance = Math.sqrt(lateralSqr);
        double tolerance = BASE_RAY_TOLERANCE + Math.max(0.0D, rayDistance) * RAY_TOLERANCE_PER_BLOCK;
        boolean direct = directHit.filter(candidate.id()::equals).isPresent();
        if (!direct && lateralDistance > tolerance) {
            return PointScore.rejected();
        }
        double distanceFromCurrent = point.distanceTo(currentPosition);
        double maxDistance = direct ? MAX_TARGET_DISTANCE_FROM_CURRENT + 1.5D : MAX_TARGET_DISTANCE_FROM_CURRENT;
        if (distanceFromCurrent > maxDistance) {
            return PointScore.rejected();
        }

        double lookAlignment = toPoint.normalize().dot(look);
        double alignmentScore = Mth.clamp((lookAlignment - 0.42D) / 0.50D, 0.0D, 1.0D) * 3.15D;
        double lateralScore = (1.0D - Mth.clamp(lateralDistance / Math.max(0.001D, tolerance), 0.0D, 1.0D)) * 2.35D;
        double proximityScore = (1.0D - Mth.clamp(distanceFromCurrent / maxDistance, 0.0D, 1.0D)) * 1.15D;
        double forwardScore = Mth.clamp(rayDistance / MAX_LOOK_DISTANCE, 0.0D, 1.0D) * 0.38D;
        double directScore = direct ? 2.1D : 0.0D;
        return new PointScore(true, alignmentScore + lateralScore + proximityScore + forwardScore + directScore);
    }

    private static Optional<Target> samePipeReverseTarget(PipeConnection current, ClientSlideState state, Vec3 currentForward, Vec3 look) {
        int reverseDirection = -state.direction();
        if (!current.allowsSlideDirection(reverseDirection)) {
            return Optional.empty();
        }
        double backwardLook = -currentForward.dot(look);
        if (backwardLook < SAME_PIPE_REVERSE_MIN_ALIGNMENT) {
            return Optional.empty();
        }
        double score = 3.0D + (backwardLook - SAME_PIPE_REVERSE_MIN_ALIGNMENT) * 6.2D;
        return Optional.of(new Target(
                TargetKind.SAME_PIPE_REVERSE,
                current,
                reverseDirection,
                state.distanceOnConnection(),
                score));
    }

    private static Vec3 safeNormalize(Vec3 vector, Vec3 fallback) {
        if (vector.lengthSqr() >= 1.0E-6D) {
            return vector.normalize();
        }
        return fallback.lengthSqr() >= 1.0E-6D ? fallback.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
    }

    record Target(TargetKind kind, PipeConnection connection, int direction, double distanceOnConnection, double score) {
        Target {
            direction = direction < 0 ? -1 : 1;
            distanceOnConnection = Mth.clamp(distanceOnConnection, 0.0D, connection.length());
        }
    }

    enum TargetKind {
        NEARBY_PIPE,
        SAME_PIPE_REVERSE
    }

    private record PointScore(boolean accepted, double score) {
        static PointScore rejected() {
            return new PointScore(false, 0.0D);
        }
    }
}
