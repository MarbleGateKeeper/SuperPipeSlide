package dev.marblegate.superpipeslide.client.core.fold;


import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorKind;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorNode;
import dev.marblegate.superpipeslide.common.core.slide.ResolvedPipeSpeedRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ClientFoldTraversalEffectController {
    private static final double APPROACH_RANGE = 12.0D;
    private static final double MIN_APPROACH_RANGE = 5.6D;
    private static final double EXIT_OFFSET = 0.22D;
    private static final double EXIT_RANGE = 14.0D;
    private static final double MIN_EXIT_RANGE = 7.0D;
    private static final long APPROACH_STALE_MS = 180L;
    private static final long EXIT_STALE_MS = 2200L;
    private static final long EXIT_TIMED_FALLBACK_GRACE_MS = 140L;
    private static final long EXIT_TIMED_UNFOLD_MS_SPACE = 1100L;
    private static final long EXIT_TIMED_UNFOLD_MS_DIMENSION = 1700L;
    private static final long CONTACT_MS_SPACE = 220L;
    private static final long TUNNEL_MS_SPACE = 300L;
    private static final long EXIT_MS_SPACE = 520L;
    private static final long DECAY_MS_SPACE = 620L;
    private static final long CONTACT_MS_DIMENSION = 220L;
    private static final long TUNNEL_MS_DIMENSION = 330L;
    private static final long EXIT_MS_DIMENSION = 430L;
    private static final long DECAY_MS_DIMENSION = 840L;
    private static final long CANCEL_MS = 420L;
    @Nullable
    private static ActiveEffect active;
    private static long lastApproachRefreshMs;

    private ClientFoldTraversalEffectController() {
    }

    public static void tick(Minecraft minecraft, LocalPlayer player) {
        if (minecraft.level == null || active == null) {
            return;
        }
        long now = now();
        if (active.phase == Phase.APPROACH && now - lastApproachRefreshMs > APPROACH_STALE_MS) {
            active.phase = Phase.DECAY;
            active.phaseStartMs = now;
        }
        if (active.phase == Phase.EXIT
                && now - active.lastTrackRefreshMs > EXIT_TIMED_FALLBACK_GRACE_MS
                && active.exitProgress < 0.995D) {
            long timedElapsed = Math.max(0L, now - active.phaseStartMs - EXIT_TIMED_FALLBACK_GRACE_MS);
            double timedProgress = easeInOut(Mth.clamp(timedElapsed / (double) exitTimedUnfoldMs(active.kind), 0.0D, 1.0D));
            active.exitProgress = Math.max(active.exitProgress, timedProgress);
        }
        if (active.phase == Phase.EXIT && now - active.lastTrackRefreshMs > EXIT_STALE_MS) {
            active = null;
            return;
        }
        if (active.expired(now)) {
            active = null;
        }
    }

    public static void clear() {
        active = null;
        lastApproachRefreshMs = 0L;
    }

    public static void updateApproach(PipeConnection connection, int direction, double distanceOnConnection, double speed) {
        updateTrackPosition(connection, direction, distanceOnConnection, speed);
    }

    public static void updateTrackPosition(PipeConnection connection, int direction, double distanceOnConnection, double speed) {
        long now = now();
        if (updateExitUnfold(connection, direction, distanceOnConnection, speed, now)) {
            return;
        }

        Optional<ResolvedFoldEndpoint> endpoint = resolveFoldEndpoint(connection, direction, speed);
        if (endpoint.isEmpty()) {
            return;
        }

        ResolvedFoldEndpoint fold = endpoint.get();
        double exitDistance = direction > 0 ? connection.length() : 0.0D;
        double distanceToFold = Math.abs(exitDistance - Mth.clamp(distanceOnConnection, 0.0D, connection.length()));
        double range = Math.max(MIN_APPROACH_RANGE, Math.min(APPROACH_RANGE, 3.2D + Math.abs(speed) * 13.0D));
        if (distanceToFold > range) {
            return;
        }

        double approach = 1.0D - Mth.clamp(distanceToFold / range, 0.0D, 1.0D);
        double speed01 = speed01(Math.abs(speed), connection);
        if (active == null || active.phase != Phase.APPROACH || !active.matches(fold.entryAnchor, fold.exitAnchor)) {
            active = ActiveEffect.approach(
                    fold.kind,
                    fold.entryAnchor,
                    fold.exitAnchor,
                    connection.levelKey(),
                    fold.exitAnchor.levelKey(),
                    fold.entryPosition,
                    fold.entryTangent,
                    fold.exitPosition,
                    fold.exitTangent,
                    speed01,
                    now
            );
        }
        active.approachProgress = Math.max(active.approachProgress, approach);
        active.speed01 = Math.max(active.speed01 * 0.82D, speed01);
        active.lastTrackRefreshMs = now;
        lastApproachRefreshMs = now;
    }

    public static void beginTraversal(PipeConnection connection, int direction, double speed, boolean crossDimension) {
        Optional<ResolvedFoldEndpoint> endpoint = resolveFoldEndpoint(connection, direction, speed);
        if (endpoint.isEmpty()) {
            beginInvalid(connection, direction);
            return;
        }

        ResolvedFoldEndpoint fold = endpoint.get();
        long now = now();
        active = ActiveEffect.contact(
                fold.kind,
                fold.entryAnchor,
                fold.exitAnchor,
                connection.levelKey(),
                fold.exitAnchor.levelKey(),
                fold.entryPosition,
                fold.entryTangent,
                fold.exitPosition,
                fold.exitTangent,
                speed01(Math.abs(speed), connection),
                crossDimension,
                now
        );
        lastApproachRefreshMs = now;
    }

    public static void completeTraversal(ResourceKey<Level> targetLevel, Vec3 targetPosition, Vec3 targetTangent, double speed) {
        long now = now();
        if (active == null) {
            active = ActiveEffect.exitFallback(targetLevel, targetPosition, safeNormalize(targetTangent, new Vec3(0.0D, 0.0D, 1.0D)), Mth.clamp(Math.abs(speed) / 0.72D, 0.0D, 1.0D), now);
            return;
        }
        active.exitLevel = targetLevel;
        active.exitPosition = targetPosition;
        active.exitTangent = safeNormalize(targetTangent, active.exitTangent);
        active.waitingForCommit = false;
        active.speed01 = Math.max(active.speed01, Mth.clamp(Math.abs(speed) / 0.72D, 0.0D, 1.0D));
        active.exitCommitted = true;
        active.exitProgress = 0.0D;
        active.phase = Phase.EXIT;
        active.phaseStartMs = now;
        active.lastTrackRefreshMs = now;
    }

    public static void failTraversal() {
        if (active == null) {
            return;
        }
        active.phase = Phase.CANCEL;
        active.phaseStartMs = now();
        active.waitingForCommit = false;
    }

    public static Optional<Snapshot> snapshot() {
        if (active == null) {
            return Optional.empty();
        }
        long now = now();
        Phase phase = active.visualPhase(now);
        double progress = active.phaseProgress(now, phase);
        double life = active.life(now, phase, progress);
        if (life <= 0.01D) {
            return Optional.empty();
        }
        return Optional.of(new Snapshot(
                active.kind,
                visualPhase(phase),
                active.entryLevel,
                active.exitLevel,
                active.entryPosition,
                active.entryTangent,
                active.exitPosition,
                active.exitTangent,
                active.speed01,
                active.crossDimension,
                active.waitingForCommit || phase == Phase.WAITING,
                active.approachProgress,
                progress,
                life,
                active.seed
        ));
    }

    public static double fovOffset() {
        Optional<Snapshot> snapshot = snapshot();
        if (snapshot.isEmpty()) {
            return 0.0D;
        }
        Snapshot effect = snapshot.get();
        double kindScale = effect.kind == FoldAnchorKind.DIMENSION ? 1.25D : 0.85D;
        return switch (effect.phase) {
            case APPROACH -> -1.0D * effect.life * effect.approachProgress;
            case CONTACT -> -3.6D * kindScale * easeOut(effect.phaseProgress()) * effect.life;
            case TUNNEL, WAITING -> -2.8D * kindScale * effect.life;
            case EXIT -> 1.8D * kindScale * (1.0D - effect.phaseProgress()) * effect.life;
            case DECAY, CANCEL -> 0.0D;
        };
    }

    public static double cameraRollOffset() {
        Optional<Snapshot> snapshot = snapshot();
        if (snapshot.isEmpty() || snapshot.get().kind != FoldAnchorKind.DIMENSION) {
            return 0.0D;
        }
        Snapshot effect = snapshot.get();
        double wave = Math.sin((now() + effect.seed % 997L) / 86.0D);
        return switch (effect.phase) {
            case CONTACT -> wave * 0.85D * effect.life * easeOut(effect.phaseProgress());
            case TUNNEL, WAITING -> wave * 1.15D * effect.life;
            case EXIT -> wave * 0.70D * effect.life * (1.0D - effect.phaseProgress());
            default -> 0.0D;
        };
    }

    private static boolean updateExitUnfold(PipeConnection connection, int direction, double distanceOnConnection, double speed, long now) {
        if (active == null || !active.exitCommitted || !connection.levelKey().equals(active.exitLevel) || !connection.touches(active.exitAnchor)) {
            return false;
        }
        int awayDirection = connection.directionAwayFrom(active.exitAnchor);
        if (awayDirection != direction) {
            return false;
        }

        double distanceFromExit = distanceFromAnchor(connection, active.exitAnchor, distanceOnConnection);
        double range = exitRange(Math.abs(speed));
        double progress = easeInOut(Mth.clamp(distanceFromExit / range, 0.0D, 1.0D));
        active.exitProgress = progress;
        active.phase = Phase.EXIT;
        active.lastTrackRefreshMs = now;
        active.speed01 = Math.max(active.speed01 * 0.86D, speed01(Math.abs(speed), connection));
        active.exitPosition = Vec3.atCenterOf(active.exitAnchor.blockPos());
        active.exitTangent = safeNormalize(connection.tangentAt(Mth.clamp(distanceOnConnection, 0.0D, connection.length())).scale(direction), active.exitTangent);
        if (progress >= 0.995D) {
            active = null;
        }
        return true;
    }

    private static double distanceFromAnchor(PipeConnection connection, PipeAnchorId anchor, double distanceOnConnection) {
        double distance = Mth.clamp(distanceOnConnection, 0.0D, connection.length());
        if (connection.fromAnchor().equals(anchor)) {
            return distance;
        }
        if (connection.toAnchor().equals(anchor)) {
            return connection.length() - distance;
        }
        return Double.POSITIVE_INFINITY;
    }

    private static double exitRange(double speed) {
        return Math.max(MIN_EXIT_RANGE, Math.min(EXIT_RANGE, 6.2D + speed * 18.0D));
    }

    private static Optional<ResolvedFoldEndpoint> resolveFoldEndpoint(PipeConnection connection, int direction, double speed) {
        PipeAnchorId entryAnchor = connection.anchorForDirectionEnd(direction);
        Optional<FoldAnchorNode> foldNode = ClientPipeNetworkCache.foldAnchorAt(entryAnchor);
        Optional<PipeAnchorId> exitAnchor = ClientPipeNetworkCache.globalFoldCounterpart(entryAnchor);
        if (foldNode.isEmpty() || exitAnchor.isEmpty()) {
            return Optional.empty();
        }

        Vec3 entryPosition = Vec3.atCenterOf(entryAnchor.blockPos());
        Vec3 entryTangent = safeNormalize(connection.tangentAt(direction > 0 ? connection.length() : 0.0D).scale(direction), new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 exitPosition = Vec3.atCenterOf(exitAnchor.get().blockPos());
        Vec3 exitTangent = entryTangent;

        List<PipeConnection> targetConnections = ClientPipeNetworkCache.connectionsTouching(exitAnchor.get());
        if (targetConnections.size() == 1) {
            PipeConnection targetConnection = targetConnections.getFirst();
            int targetDirection = targetConnection.directionAwayFrom(exitAnchor.get());
            double targetDistance = targetDirection > 0
                    ? Math.min(EXIT_OFFSET, targetConnection.length())
                    : Math.max(0.0D, targetConnection.length() - EXIT_OFFSET);
            exitPosition = targetConnection.positionAt(targetDistance);
            exitTangent = safeNormalize(targetConnection.tangentAt(targetDistance).scale(targetDirection), entryTangent);
        }

        return Optional.of(new ResolvedFoldEndpoint(
                foldNode.get().kind(),
                entryAnchor,
                exitAnchor.get(),
                entryPosition,
                entryTangent,
                exitPosition,
                exitTangent
        ));
    }

    private static void beginInvalid(PipeConnection connection, int direction) {
        PipeAnchorId entryAnchor = connection.anchorForDirectionEnd(direction);
        long now = now();
        Vec3 entryPosition = Vec3.atCenterOf(entryAnchor.blockPos());
        Vec3 entryTangent = safeNormalize(connection.tangentAt(direction > 0 ? connection.length() : 0.0D).scale(direction), new Vec3(0.0D, 0.0D, 1.0D));
        active = ActiveEffect.contact(
                FoldAnchorKind.SPACE,
                entryAnchor,
                entryAnchor,
                connection.levelKey(),
                connection.levelKey(),
                entryPosition,
                entryTangent,
                entryPosition,
                entryTangent.scale(-1.0D),
                0.45D,
                false,
                now
        );
        active.phase = Phase.CANCEL;
        active.phaseStartMs = now;
    }

    private static double speed01(double speed, PipeConnection connection) {
        double maxSpeed = ResolvedPipeSpeedRules.from(connection.resolvedAttributes()).maxSpeed();
        return maxSpeed <= 1.0E-6D ? Mth.clamp(speed / 0.72D, 0.0D, 1.0D) : Mth.clamp(speed / maxSpeed, 0.0D, 1.0D);
    }

    private static Vec3 safeNormalize(Vec3 value, Vec3 fallback) {
        return value.lengthSqr() < 1.0E-8D ? fallback : value.normalize();
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static double easeOut(double value) {
        double t = Mth.clamp(value, 0.0D, 1.0D);
        return 1.0D - Math.pow(1.0D - t, 3.0D);
    }

    private static double easeInOut(double value) {
        double t = Mth.clamp(value, 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    private static long exitTimedUnfoldMs(FoldAnchorKind kind) {
        return kind == FoldAnchorKind.DIMENSION ? EXIT_TIMED_UNFOLD_MS_DIMENSION : EXIT_TIMED_UNFOLD_MS_SPACE;
    }

    private enum Phase {
        APPROACH,
        CONTACT,
        TUNNEL,
        WAITING,
        EXIT,
        DECAY,
        CANCEL
    }

    public enum VisualPhase {
        APPROACH,
        CONTACT,
        TUNNEL,
        WAITING,
        EXIT,
        DECAY,
        CANCEL
    }

    private static final class ActiveEffect {
        private final FoldAnchorKind kind;
        private final PipeAnchorId entryAnchor;
        private final PipeAnchorId exitAnchor;
        private ResourceKey<Level> entryLevel;
        private ResourceKey<Level> exitLevel;
        private Vec3 entryPosition;
        private Vec3 entryTangent;
        private Vec3 exitPosition;
        private Vec3 exitTangent;
        private double speed01;
        private final boolean crossDimension;
        private boolean waitingForCommit;
        private double approachProgress;
        private double exitProgress;
        private boolean exitCommitted;
        private Phase phase;
        private long phaseStartMs;
        private long lastTrackRefreshMs;
        private final long contactStartMs;
        private final long seed;

        private ActiveEffect(FoldAnchorKind kind, PipeAnchorId entryAnchor, PipeAnchorId exitAnchor, ResourceKey<Level> entryLevel, ResourceKey<Level> exitLevel, Vec3 entryPosition, Vec3 entryTangent, Vec3 exitPosition, Vec3 exitTangent, double speed01, boolean crossDimension, boolean waitingForCommit, double approachProgress, Phase phase, long phaseStartMs, long contactStartMs, long seed) {
            this.kind = kind;
            this.entryAnchor = entryAnchor;
            this.exitAnchor = exitAnchor;
            this.entryLevel = entryLevel;
            this.exitLevel = exitLevel;
            this.entryPosition = entryPosition;
            this.entryTangent = safeNormalize(entryTangent, new Vec3(0.0D, 0.0D, 1.0D));
            this.exitPosition = exitPosition;
            this.exitTangent = safeNormalize(exitTangent, this.entryTangent);
            this.speed01 = speed01;
            this.crossDimension = crossDimension;
            this.waitingForCommit = waitingForCommit;
            this.approachProgress = approachProgress;
            this.exitProgress = 0.0D;
            this.exitCommitted = phase == Phase.EXIT;
            this.phase = phase;
            this.phaseStartMs = phaseStartMs;
            this.lastTrackRefreshMs = phaseStartMs;
            this.contactStartMs = contactStartMs;
            this.seed = seed;
        }

        private static ActiveEffect approach(FoldAnchorKind kind, PipeAnchorId entryAnchor, PipeAnchorId exitAnchor, ResourceKey<Level> entryLevel, ResourceKey<Level> exitLevel, Vec3 entryPosition, Vec3 entryTangent, Vec3 exitPosition, Vec3 exitTangent, double speed01, long now) {
            return new ActiveEffect(kind, entryAnchor, exitAnchor, entryLevel, exitLevel, entryPosition, entryTangent, exitPosition, exitTangent, speed01, !entryLevel.equals(exitLevel), false, 0.0D, Phase.APPROACH, now, now, UUID.randomUUID().getMostSignificantBits());
        }

        private static ActiveEffect contact(FoldAnchorKind kind, PipeAnchorId entryAnchor, PipeAnchorId exitAnchor, ResourceKey<Level> entryLevel, ResourceKey<Level> exitLevel, Vec3 entryPosition, Vec3 entryTangent, Vec3 exitPosition, Vec3 exitTangent, double speed01, boolean crossDimension, long now) {
            return new ActiveEffect(kind, entryAnchor, exitAnchor, entryLevel, exitLevel, entryPosition, entryTangent, exitPosition, exitTangent, speed01, crossDimension, crossDimension, 1.0D, Phase.CONTACT, now, now, UUID.randomUUID().getMostSignificantBits());
        }

        private static ActiveEffect exitFallback(ResourceKey<Level> targetLevel, Vec3 targetPosition, Vec3 targetTangent, double speed01, long now) {
            PipeAnchorId fallback = new PipeAnchorId(targetLevel, BlockPos.containing(targetPosition));
            return new ActiveEffect(FoldAnchorKind.DIMENSION, fallback, fallback, targetLevel, targetLevel, targetPosition.subtract(targetTangent.scale(1.2D)), targetTangent, targetPosition, targetTangent, speed01, true, false, 1.0D, Phase.EXIT, now, now, UUID.randomUUID().getMostSignificantBits());
        }

        private boolean matches(PipeAnchorId entryAnchor, PipeAnchorId exitAnchor) {
            return this.entryAnchor.equals(entryAnchor) && this.exitAnchor.equals(exitAnchor);
        }

        private Phase visualPhase(long now) {
            if (this.phase == Phase.APPROACH || this.phase == Phase.EXIT || this.phase == Phase.DECAY || this.phase == Phase.CANCEL) {
                return this.phase;
            }
            long elapsed = now - this.contactStartMs;
            long contact = contactMs(this.kind);
            long tunnel = tunnelMs(this.kind);
            if (elapsed < contact) {
                return Phase.CONTACT;
            }
            if (elapsed < contact + tunnel) {
                return Phase.TUNNEL;
            }
            if (this.waitingForCommit) {
                return Phase.WAITING;
            }
            this.phase = Phase.EXIT;
            this.phaseStartMs = now;
            return Phase.EXIT;
        }

        private double phaseProgress(long now, Phase phase) {
            return switch (phase) {
                case APPROACH -> easeInOut(this.approachProgress);
                case CONTACT -> Mth.clamp((now - this.contactStartMs) / (double) contactMs(this.kind), 0.0D, 1.0D);
                case TUNNEL -> Mth.clamp((now - this.contactStartMs - contactMs(this.kind)) / (double) tunnelMs(this.kind), 0.0D, 1.0D);
                case WAITING -> 0.5D + 0.5D * Math.sin(now / 460.0D);
                case EXIT -> Mth.clamp(this.exitProgress, 0.0D, 1.0D);
                case DECAY -> Mth.clamp((now - this.phaseStartMs) / (double) decayMs(this.kind), 0.0D, 1.0D);
                case CANCEL -> Mth.clamp((now - this.phaseStartMs) / (double) CANCEL_MS, 0.0D, 1.0D);
            };
        }

        private double life(long now, Phase phase, double progress) {
            return switch (phase) {
                case APPROACH -> Mth.clamp(0.18D + this.approachProgress * 0.82D, 0.0D, 1.0D);
                case CONTACT -> Mth.clamp(0.72D + easeOut(progress) * 0.28D, 0.0D, 1.0D);
                case TUNNEL, WAITING -> 1.0D;
                case EXIT -> 1.0D - Math.pow(Mth.clamp(progress, 0.0D, 1.0D), 1.65D);
                case DECAY -> 1.0D - easeOut(progress);
                case CANCEL -> 1.0D - easeOut(progress);
            };
        }

        private boolean expired(long now) {
            Phase visual = this.visualPhase(now);
            return switch (visual) {
                case DECAY -> now - this.phaseStartMs > decayMs(this.kind);
                case CANCEL -> now - this.phaseStartMs > CANCEL_MS;
                case EXIT -> this.exitProgress >= 0.995D;
                default -> false;
            };
        }

        private static long contactMs(FoldAnchorKind kind) {
            return kind == FoldAnchorKind.DIMENSION ? CONTACT_MS_DIMENSION : CONTACT_MS_SPACE;
        }

        private static long tunnelMs(FoldAnchorKind kind) {
            return kind == FoldAnchorKind.DIMENSION ? TUNNEL_MS_DIMENSION : TUNNEL_MS_SPACE;
        }

        private static long exitMs(FoldAnchorKind kind) {
            return kind == FoldAnchorKind.DIMENSION ? EXIT_MS_DIMENSION : EXIT_MS_SPACE;
        }

        private static long decayMs(FoldAnchorKind kind) {
            return kind == FoldAnchorKind.DIMENSION ? DECAY_MS_DIMENSION : DECAY_MS_SPACE;
        }
    }

    public record Snapshot(
            FoldAnchorKind kind,
            VisualPhase phase,
            ResourceKey<Level> entryLevel,
            ResourceKey<Level> exitLevel,
            Vec3 entryPosition,
            Vec3 entryTangent,
            Vec3 exitPosition,
            Vec3 exitTangent,
            double speed01,
            boolean crossDimension,
            boolean waitingForCommit,
            double approachProgress,
            double phaseProgress,
            double life,
            long seed
    ) {
        public boolean dimensionFold() {
            return this.kind == FoldAnchorKind.DIMENSION;
        }

        public VisualPhase visualPhase() {
            return this.phase;
        }
    }

    private static VisualPhase visualPhase(Phase phase) {
        return switch (phase) {
            case APPROACH -> VisualPhase.APPROACH;
            case CONTACT -> VisualPhase.CONTACT;
            case TUNNEL -> VisualPhase.TUNNEL;
            case WAITING -> VisualPhase.WAITING;
            case EXIT -> VisualPhase.EXIT;
            case DECAY -> VisualPhase.DECAY;
            case CANCEL -> VisualPhase.CANCEL;
        };
    }

    private record ResolvedFoldEndpoint(FoldAnchorKind kind, PipeAnchorId entryAnchor, PipeAnchorId exitAnchor, Vec3 entryPosition, Vec3 entryTangent, Vec3 exitPosition, Vec3 exitTangent) {
    }
}
