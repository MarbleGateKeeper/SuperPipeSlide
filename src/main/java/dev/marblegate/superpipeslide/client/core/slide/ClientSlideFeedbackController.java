package dev.marblegate.superpipeslide.client.core.slide;

import dev.marblegate.superpipeslide.client.core.accessibility.ClientSafetyOptions;
import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.SlideGeometry;
import dev.marblegate.superpipeslide.common.core.slide.ResolvedPipeSpeedRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class ClientSlideFeedbackController {
    private static final int MAX_TRAILS = 220;
    private static final int MAX_REMOTE_TRAILS_PER_PLAYER = 64;
    private static final double REMOTE_TRACK_DISTANCE = 96.0D;
    private static final double REMOTE_TRAIL_FULL_DISTANCE = 28.0D;
    private static final double REMOTE_TRAIL_FADE_DISTANCE = 64.0D;
    private static final double REMOTE_PIPE_QUERY_RADIUS = 7.0D;
    private static final double REMOTE_SIGNAL_DISTANCE = 0.72D;
    private static final double REMOTE_CONTINUITY_DISTANCE = 0.88D;
    private static final double REMOTE_START_MIN_STEP = 0.012D;
    private static final double REMOTE_CONTINUE_MIN_STEP = 0.003D;
    private static final int TURN_HISTORY_MAX_SAMPLES = 36;
    private static final double TURN_HISTORY_LOOKBACK_DISTANCE = 2.85D;
    private static final double TURN_HISTORY_MIN_DISTANCE = 0.55D;
    private static final double TURN_HISTORY_MAX_DISTANCE = 6.25D;
    private static final double TURN_HISTORY_TELEPORT_DISTANCE = 14.0D;
    private static final double TURN_MIN_ANGLE = 0.035D;
    private static final double TURN_FULL_ANGLE = 0.42D;
    private static final double TURN_HORIZONTAL_DEADBAND = 0.10D;
    private static final int TURN_PREVIEW_MAX_CONNECTIONS = 8;
    private static final double TURN_PREVIEW_DISTANCE_MIN = 4.25D;
    private static final double TURN_PREVIEW_DISTANCE_MAX = 23.0D;
    private static final double TURN_PREVIEW_SAMPLE_STEP = 1.15D;
    private static final double TURN_PREVIEW_MIN_ANGLE = Math.toRadians(8.0D);
    private static final double TURN_PREVIEW_FULL_ANGLE = Math.toRadians(38.0D);
    private static final int TRAIL_STREAK = 0;
    private static final int TRAIL_SPARK = 1;
    private static final int TRAIL_SIDE_STREAM = 2;
    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Vec3 DEFAULT_VISUAL_FACING = new Vec3(0.0D, 0.0D, 1.0D);
    private static final RandomSource RANDOM = RandomSource.create();
    private static final TurnAccumulator TURN_ACCUMULATOR = new TurnAccumulator();
    private static final TurnPreviewTracker TURN_PREVIEW = new TurnPreviewTracker();
    private static final VisualFacingTracker VISUAL_FACING = new VisualFacingTracker();

    @Nullable
    private static Frame frame;
    @Nullable
    private static Frame previousFrame;
    @Nullable
    private static UUID sessionId;
    private static double alpha;
    private static double speed01;
    private static double accelerationBlend;
    private static double highwayBlend;
    private static double platformBlend;
    private static double verticalBlend;
    private static double upBlend;
    private static double downBlend;
    private static double perceptualSpeed;
    private static double turnBlend;
    private static double signedTurn;
    private static double turnPreviewBlend;
    private static double signedTurnPreview;
    private static double accelerationPulse;
    private static double motionPhase;
    private static double lastSpeed;
    private static boolean previousHighway;
    private static boolean previousAcceleration;
    private static final List<TrailParticle> TRAILS = new ArrayList<>();
    private static final Map<Integer, RemoteSlideState> REMOTE_STATES = new LinkedHashMap<>();

    private ClientSlideFeedbackController() {
    }

    public static void tick(Minecraft minecraft, LocalPlayer player) {
        Optional<ClientSlideFeedbackSnapshot> snapshot = ClientSlideController.slideFeedbackSnapshot(player);
        tickTrailParticles(TRAILS);
        tickRemotePlayers(minecraft, player);
        if (snapshot.isEmpty()) {
            tickInactive();
            return;
        }

        ClientSlideFeedbackSnapshot next = snapshot.get();
        boolean newSession = sessionId == null || !sessionId.equals(next.sessionId());
        if (newSession) {
            resetSession(next.sessionId(), next.speed());
        }
        previousFrame = newSession ? null : frame;

        Vec3 tangent = safeNormalize(next.tangent(), new Vec3(0.0D, 0.0D, 1.0D));
        double targetSpeed01 = next.speed01();
        double targetPerceptualSpeed = smoothstep(0.06D, 0.92D, targetSpeed01);
        double speedDelta = newSession ? 0.0D : next.speed() - lastSpeed;
        double targetAcceleration = next.accelerationAttribute()
                ? Math.max(0.36D, targetSpeed01 * 0.95D)
                : Mth.clamp(speedDelta * 48.0D, 0.0D, 1.0D);
        double targetHighway = next.highway() ? Math.max(0.42D, targetSpeed01) : 0.0D;
        double targetPlatform = next.stationSlow() ? 1.0D : next.platformConnection() && targetSpeed01 < 0.20D ? 0.42D : 0.0D;
        double targetVertical = smoothstep(0.62D, 0.86D, Math.abs(tangent.y));
        double targetUp = targetVertical * Math.max(0.0D, tangent.y);
        double targetDown = targetVertical * Math.max(0.0D, -tangent.y);
        TurnSample turn = TURN_ACCUMULATOR.sample(next.position(), tangent, newSession, turnSpeedScale(targetSpeed01, targetPerceptualSpeed));
        TurnSample turnPreview = TURN_PREVIEW.sample(tangent, newSession, turnSpeedScale(targetSpeed01, targetPerceptualSpeed));
        Vec3 visualFacing = VISUAL_FACING.update(tangent, player.getYRot(), newSession);
        double pulseTarget = Mth.clamp(speedDelta * 42.0D, 0.0D, 1.0D);
        if (next.accelerationAttribute() && !previousAcceleration) {
            pulseTarget = Math.max(pulseTarget, 0.78D);
        }
        if (next.highway() && !previousHighway) {
            pulseTarget = Math.max(pulseTarget, 0.92D);
        }

        alpha = approach(alpha, 1.0D, 0.24D);
        speed01 = approach(speed01, targetSpeed01, 0.24D);
        accelerationBlend = approach(accelerationBlend, targetAcceleration, 0.22D);
        highwayBlend = approach(highwayBlend, targetHighway, 0.18D);
        platformBlend = approach(platformBlend, targetPlatform, 0.18D);
        verticalBlend = approach(verticalBlend, targetVertical, 0.20D);
        upBlend = approach(upBlend, targetUp, 0.20D);
        downBlend = approach(downBlend, targetDown, 0.20D);
        perceptualSpeed = approach(perceptualSpeed, targetPerceptualSpeed, 0.28D);
        turnBlend = approach(turnBlend, turn.intensity(), turn.intensity() > turnBlend ? 0.42D : 0.16D);
        signedTurn = approach(signedTurn, turn.signedIntensity(), 0.32D);
        turnPreviewBlend = approach(turnPreviewBlend, turnPreview.intensity(), turnPreview.intensity() > turnPreviewBlend ? 0.30D : 0.14D);
        signedTurnPreview = approach(signedTurnPreview, turnPreview.signedIntensity(), 0.26D);
        accelerationPulse = Math.max(accelerationPulse * 0.76D, pulseTarget);
        motionPhase = (motionPhase + 0.030D + perceptualSpeed * 0.115D + accelerationPulse * 0.045D + highwayBlend * 0.030D) % 1.0D;

        frame = new Frame(
                true,
                alpha,
                next.sessionId(),
                next.connectionId(),
                next.distanceOnConnection(),
                next.connectionLength(),
                next.position(),
                tangent,
                visualFacing,
                next.speed(),
                speed01,
                perceptualSpeed,
                accelerationBlend,
                highwayBlend,
                platformBlend,
                verticalBlend,
                upBlend,
                downBlend,
                turnBlend,
                signedTurn,
                turnPreviewBlend,
                signedTurnPreview,
                accelerationPulse,
                motionPhase,
                fovBoost(),
                edgeIntensity(),
                next.ticksSliding()
        );

        spawnTrails(next);
        lastSpeed = next.speed();
        previousAcceleration = next.accelerationAttribute();
        previousHighway = next.highway();
    }

    public static void clear() {
        frame = null;
        previousFrame = null;
        sessionId = null;
        alpha = 0.0D;
        speed01 = 0.0D;
        accelerationBlend = 0.0D;
        highwayBlend = 0.0D;
        platformBlend = 0.0D;
        verticalBlend = 0.0D;
        upBlend = 0.0D;
        downBlend = 0.0D;
        perceptualSpeed = 0.0D;
        turnBlend = 0.0D;
        signedTurn = 0.0D;
        turnPreviewBlend = 0.0D;
        signedTurnPreview = 0.0D;
        accelerationPulse = 0.0D;
        motionPhase = 0.0D;
        lastSpeed = 0.0D;
        previousHighway = false;
        previousAcceleration = false;
        TURN_ACCUMULATOR.reset();
        TURN_PREVIEW.reset();
        VISUAL_FACING.reset();
        TRAILS.clear();
        REMOTE_STATES.clear();
    }

    public static Optional<Frame> currentFrame() {
        return frame == null || frame.alpha() <= 0.015D ? Optional.empty() : Optional.of(frame);
    }

    public static Optional<Frame> currentRenderFrame() {
        if (frame == null || frame.alpha() <= 0.015D) {
            return Optional.empty();
        }
        if (previousFrame == null
                || !previousFrame.sessionId().equals(frame.sessionId())
                || !previousFrame.connectionId().equals(frame.connectionId())
                || previousFrame.position().distanceToSqr(frame.position()) > 16.0D) {
            return Optional.of(frame);
        }
        Minecraft minecraft = Minecraft.getInstance();
        double partialTick = minecraft.getDeltaTracker() == null ? 1.0D : Mth.clamp(minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false), 0.0D, 1.0D);
        return Optional.of(Frame.interpolate(previousFrame, frame, partialTick));
    }

    public static Optional<Frame> frameForPlayer(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getId() == entityId) {
            return currentFrame();
        }
        RemoteSlideState state = REMOTE_STATES.get(entityId);
        return state == null ? Optional.empty() : state.currentFrame();
    }

    public static List<TrailParticleSnapshot> trailParticles() {
        if (ClientSafetyOptions.reduceMotionSicknessRisk() || ClientSafetyOptions.reducePhotosensitivityRisk()) {
            return List.of();
        }
        boolean hasRemoteTrails = REMOTE_STATES.values().stream().anyMatch(RemoteSlideState::hasTrails);
        if (TRAILS.isEmpty() && !hasRemoteTrails) {
            return List.of();
        }
        List<TrailParticleSnapshot> snapshots = new ArrayList<>(TRAILS.size() + REMOTE_STATES.size() * 8);
        appendTrailSnapshots(TRAILS, snapshots);
        for (RemoteSlideState state : REMOTE_STATES.values()) {
            state.appendTrailSnapshots(snapshots);
        }
        return List.copyOf(snapshots);
    }

    private static void appendTrailSnapshots(List<TrailParticle> particles, List<TrailParticleSnapshot> snapshots) {
        for (TrailParticle particle : particles) {
            double life = 1.0D - particle.age / (double) particle.lifetime;
            if (life <= 0.0D) {
                continue;
            }
            int baseAlpha = switch (particle.kind) {
                case TRAIL_SPARK -> 225;
                case TRAIL_SIDE_STREAM -> 150;
                default -> 180;
            };
            int alpha = (int) Math.round(baseAlpha * life * life);
            snapshots.add(new TrailParticleSnapshot(
                    particle.position,
                    particle.direction,
                    particle.width * (0.70D + life * 0.50D),
                    particle.length * (0.72D + life * 0.36D),
                    withAlpha(particle.color, alpha),
                    particle.age / (double) particle.lifetime,
                    particle.kind
            ));
        }
    }

    private static void tickInactive() {
        previousFrame = frame;
        alpha = approach(alpha, 0.0D, 0.20D);
        speed01 = approach(speed01, 0.0D, 0.18D);
        accelerationBlend = approach(accelerationBlend, 0.0D, 0.24D);
        highwayBlend = approach(highwayBlend, 0.0D, 0.20D);
        platformBlend = approach(platformBlend, 0.0D, 0.20D);
        verticalBlend = approach(verticalBlend, 0.0D, 0.18D);
        upBlend = approach(upBlend, 0.0D, 0.18D);
        downBlend = approach(downBlend, 0.0D, 0.18D);
        perceptualSpeed = approach(perceptualSpeed, 0.0D, 0.18D);
        turnBlend = approach(turnBlend, 0.0D, 0.22D);
        signedTurn = approach(signedTurn, 0.0D, 0.24D);
        turnPreviewBlend = approach(turnPreviewBlend, 0.0D, 0.18D);
        signedTurnPreview = approach(signedTurnPreview, 0.0D, 0.20D);
        accelerationPulse *= 0.72D;
        motionPhase = (motionPhase + 0.018D) % 1.0D;
        if (frame != null) {
            frame = frame.withFeedback(alpha, speed01, perceptualSpeed, accelerationBlend, highwayBlend, platformBlend, verticalBlend, upBlend, downBlend, turnBlend, signedTurn, turnPreviewBlend, signedTurnPreview, accelerationPulse, motionPhase, fovBoost(), edgeIntensity());
        }
        previousHighway = false;
        previousAcceleration = false;
        if (alpha <= 0.015D && TRAILS.isEmpty()) {
            frame = null;
            previousFrame = null;
            sessionId = null;
            TURN_ACCUMULATOR.reset();
            TURN_PREVIEW.reset();
            VISUAL_FACING.reset();
        }
    }

    private static void tickRemotePlayers(Minecraft minecraft, LocalPlayer localPlayer) {
        if (minecraft.level == null) {
            REMOTE_STATES.clear();
            return;
        }

        Set<Integer> seen = new HashSet<>();
        Vec3 observerPosition = localPlayer.position();
        for (Player remotePlayer : minecraft.level.players()) {
            int entityId = remotePlayer.getId();
            if (entityId == localPlayer.getId()) {
                continue;
            }
            seen.add(entityId);
            RemoteSlideState state = REMOTE_STATES.computeIfAbsent(entityId, ignored -> new RemoteSlideState());
            if (!remotePlayer.isAlive()
                    || remotePlayer.isSpectator()
                    || remotePlayer.isPassenger()
                    || remotePlayer.distanceToSqr(localPlayer) > REMOTE_TRACK_DISTANCE * REMOTE_TRACK_DISTANCE) {
                state.tickUnavailable();
                continue;
            }
            state.tick(minecraft, remotePlayer, observerPosition);
        }

        Iterator<Map.Entry<Integer, RemoteSlideState>> iterator = REMOTE_STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, RemoteSlideState> entry = iterator.next();
            if (!seen.contains(entry.getKey())) {
                entry.getValue().tickUnavailable();
            }
            if (entry.getValue().isExpired()) {
                iterator.remove();
            }
        }
    }

    private static void resetSession(UUID nextSessionId, double initialSpeed) {
        sessionId = nextSessionId;
        previousFrame = null;
        alpha = 0.0D;
        speed01 = 0.0D;
        accelerationBlend = 0.0D;
        highwayBlend = 0.0D;
        platformBlend = 0.0D;
        verticalBlend = 0.0D;
        upBlend = 0.0D;
        downBlend = 0.0D;
        perceptualSpeed = 0.0D;
        turnBlend = 0.0D;
        signedTurn = 0.0D;
        turnPreviewBlend = 0.0D;
        signedTurnPreview = 0.0D;
        accelerationPulse = 0.0D;
        motionPhase = 0.0D;
        lastSpeed = initialSpeed;
        previousHighway = false;
        previousAcceleration = false;
        TURN_ACCUMULATOR.reset();
        TURN_PREVIEW.reset();
        VISUAL_FACING.reset();
    }

    private static void tickTrailParticles(List<TrailParticle> particles) {
        particles.removeIf(particle -> {
            particle.age++;
            particle.position = particle.position.add(particle.velocity);
            particle.velocity = particle.velocity.scale(0.90D);
            return particle.age >= particle.lifetime;
        });
    }

    private static void spawnTrails(ClientSlideFeedbackSnapshot snapshot) {
        if (ClientSafetyOptions.reduceMotionSicknessRisk() || ClientSafetyOptions.reducePhotosensitivityRisk()) {
            return;
        }
        if (snapshot.speed01() <= 0.035D || alpha <= 0.08D) {
            return;
        }
        spawnFrameTrails(
                TRAILS,
                snapshot.position(),
                snapshot.tangent(),
                snapshot.speed01(),
                snapshot.highway(),
                snapshot.stationSlow(),
                alpha,
                perceptualSpeed,
                accelerationBlend,
                highwayBlend,
                platformBlend,
                verticalBlend,
                upBlend,
                downBlend,
                accelerationPulse,
                1.0D,
                MAX_TRAILS
        );
    }

    private static void spawnFrameTrails(List<TrailParticle> particles, Vec3 position, Vec3 frameTangent, double frameSpeed01, boolean frameHighway, boolean frameStationSlow, double frameAlpha, double framePerceptualSpeed, double frameAccelerationBlend, double frameHighwayBlend, double framePlatformBlend, double frameVerticalBlend, double frameUpBlend, double frameDownBlend, double frameAccelerationPulse, double densityScale, int maxTrails) {
        if (ClientSafetyOptions.reduceMotionSicknessRisk() || ClientSafetyOptions.reducePhotosensitivityRisk()) {
            return;
        }
        if (frameSpeed01 <= 0.035D || frameAlpha <= 0.08D || densityScale <= 0.02D) {
            return;
        }
        Vec3 tangent = safeNormalize(frameTangent, new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 right = safeNormalize(tangent.cross(WORLD_UP), new Vec3(1.0D, 0.0D, 0.0D));
        if (Math.abs(tangent.y) > 0.82D) {
            right = safeNormalize(tangent.cross(new Vec3(0.0D, 0.0D, 1.0D)), new Vec3(1.0D, 0.0D, 0.0D));
        }
        Vec3 up = safeNormalize(right.cross(tangent), WORLD_UP);
        double energy = Mth.clamp(framePerceptualSpeed + frameHighwayBlend * 0.26D + frameAccelerationPulse * 0.34D, 0.0D, 1.45D);
        int streaks = scaledCount(1.0D + energy * 3.2D + (frameHighway ? 1.0D : 0.0D), densityScale);
        for (int i = 0; i < streaks; i++) {
            double side = (RANDOM.nextDouble() - 0.5D) * (0.32D + energy * 0.32D);
            double lift = (RANDOM.nextDouble() - 0.20D) * (0.20D + energy * 0.08D);
            Vec3 origin = position
                    .subtract(tangent.scale(0.24D + RANDOM.nextDouble() * (0.46D + energy * 0.42D)))
                    .add(right.scale(side))
                    .add(up.scale(lift + 0.16D * (1.0D - frameVerticalBlend)));
            Vec3 velocity = tangent.scale(-(0.026D + energy * 0.070D))
                    .add(right.scale((RANDOM.nextDouble() - 0.5D) * 0.018D))
                    .add(up.scale((RANDOM.nextDouble() - 0.5D) * 0.014D));
            double width = 0.026D + energy * 0.025D;
            double length = 0.26D + energy * 0.88D + frameHighwayBlend * 0.28D + frameAccelerationPulse * 0.30D;
            particles.add(new TrailParticle(origin, velocity, tangent, width, length, trailColor(framePlatformBlend, frameAccelerationBlend, frameHighwayBlend, frameVerticalBlend, frameUpBlend, frameDownBlend), 11 + RANDOM.nextInt(10), TRAIL_STREAK));
        }

        int sparks = frameStationSlow ? 0 : scaledCount(1.0D + energy * 2.2D + frameAccelerationPulse * 1.8D, densityScale);
        for (int i = 0; i < sparks; i++) {
            double side = (RANDOM.nextDouble() - 0.5D) * (0.40D + energy * 0.18D);
            double contact = -0.10D + RANDOM.nextDouble() * 0.10D;
            Vec3 origin = position
                    .subtract(tangent.scale(0.10D + RANDOM.nextDouble() * 0.26D))
                    .add(right.scale(side))
                    .add(up.scale(contact));
            Vec3 direction = safeNormalize(tangent.scale(0.70D + RANDOM.nextDouble() * 0.45D)
                    .add(right.scale((RANDOM.nextDouble() - 0.5D) * 0.45D))
                    .add(up.scale((RANDOM.nextDouble() - 0.1D) * 0.26D)), tangent);
            Vec3 velocity = direction.scale(-(0.012D + RANDOM.nextDouble() * 0.026D + energy * 0.018D));
            double width = 0.020D + RANDOM.nextDouble() * 0.018D;
            double length = 0.055D + RANDOM.nextDouble() * 0.090D + energy * 0.050D;
            particles.add(new TrailParticle(origin, velocity, direction, width, length, sparkColor(frameAccelerationPulse, frameAccelerationBlend, frameHighwayBlend), 7 + RANDOM.nextInt(8), TRAIL_SPARK));
        }

        if (frameHighwayBlend > 0.35D || frameAccelerationPulse > 0.30D) {
            int sideStreams = scaledCount(1.0D + (frameHighwayBlend + frameAccelerationPulse) * 2.0D, densityScale);
            for (int i = 0; i < sideStreams; i++) {
                double sign = RANDOM.nextBoolean() ? 1.0D : -1.0D;
                Vec3 origin = position
                        .subtract(tangent.scale(0.35D + RANDOM.nextDouble() * 0.40D))
                        .add(right.scale(sign * (0.46D + RANDOM.nextDouble() * 0.34D)))
                        .add(up.scale((RANDOM.nextDouble() - 0.35D) * 0.20D));
                Vec3 velocity = tangent.scale(-(0.040D + energy * 0.070D)).add(right.scale(sign * 0.006D));
                double width = 0.030D + energy * 0.020D;
                double length = 0.60D + energy * 1.05D + frameAccelerationPulse * 0.35D;
                particles.add(new TrailParticle(origin, velocity, tangent, width, length, sideStreamColor(frameAccelerationPulse, frameAccelerationBlend), 9 + RANDOM.nextInt(8), TRAIL_SIDE_STREAM));
            }
        }
        while (particles.size() > maxTrails) {
            particles.removeFirst();
        }
    }

    private static int scaledCount(double rawCount, double densityScale) {
        if (densityScale >= 0.999D) {
            return (int) Math.floor(Math.max(0.0D, rawCount));
        }
        double scaled = Math.max(0.0D, rawCount) * Mth.clamp(densityScale, 0.0D, 1.0D);
        int count = (int) Math.floor(scaled);
        return RANDOM.nextDouble() < scaled - count ? count + 1 : count;
    }

    private static int trailColor(double framePlatformBlend, double frameAccelerationBlend, double frameHighwayBlend, double frameVerticalBlend, double frameUpBlend, double frameDownBlend) {
        if (framePlatformBlend > 0.35D) {
            return 0xFFFFD57A;
        }
        if (frameAccelerationBlend > 0.48D) {
            return 0xFFFFB14A;
        }
        if (frameHighwayBlend > 0.42D) {
            return 0xFF6FE8FF;
        }
        if (frameVerticalBlend > 0.55D) {
            return frameUpBlend >= frameDownBlend ? 0xFF9FF5FF : 0xFFB6D4FF;
        }
        return 0xFF93D7FF;
    }

    private static int sparkColor(double frameAccelerationPulse, double frameAccelerationBlend, double frameHighwayBlend) {
        if (frameAccelerationPulse > 0.34D || frameAccelerationBlend > 0.48D) {
            return 0xFFFFC45D;
        }
        if (frameHighwayBlend > 0.42D) {
            return 0xFF9CF8FF;
        }
        return 0xFFE6F4FF;
    }

    private static int sideStreamColor(double frameAccelerationPulse, double frameAccelerationBlend) {
        if (frameAccelerationPulse > 0.42D || frameAccelerationBlend > 0.55D) {
            return 0xFFFFA84A;
        }
        return 0xFF70E8FF;
    }

    private static double fovBoost() {
        if (ClientSafetyOptions.reduceMotionSicknessRisk()) {
            return 0.0D;
        }
        double base = 3.20D * perceptualSpeed
                + 1.55D * accelerationBlend
                + 2.85D * highwayBlend
                + 2.25D * accelerationPulse
                + 0.75D * turnBlend;
        return Mth.clamp(base * (1.0D - platformBlend * 0.45D) * (1.0D - verticalBlend * 0.18D) * alpha, 0.0D, 7.5D);
    }

    private static double edgeIntensity() {
        if (ClientSafetyOptions.reduceMotionSicknessRisk() || ClientSafetyOptions.reducePhotosensitivityRisk()) {
            return 0.0D;
        }
        return Mth.clamp(0.10D + perceptualSpeed * 0.78D + accelerationBlend * 0.30D + highwayBlend * 0.34D + accelerationPulse * 0.52D, 0.0D, 1.0D) * alpha;
    }

    private static double approach(double value, double target, double step) {
        return value + (target - value) * step;
    }

    private static double turnSpeedScale(double speed01, double targetPerceptualSpeed) {
        return smoothstep(0.035D, 0.22D, speed01) * Mth.clamp(0.28D + targetPerceptualSpeed * 0.88D, 0.0D, 1.0D);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    private static Vec3 safeNormalize(Vec3 value, Vec3 fallback) {
        return value.lengthSqr() < 1.0E-8D ? fallback : value.normalize();
    }

    private static int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private static final class TrailParticle {
        private Vec3 position;
        private Vec3 velocity;
        private final Vec3 direction;
        private final double width;
        private final double length;
        private final int color;
        private int age;
        private final int lifetime;
        private final int kind;

        private TrailParticle(Vec3 position, Vec3 velocity, Vec3 direction, double width, double length, int color, int lifetime, int kind) {
            this.position = position;
            this.velocity = velocity;
            this.direction = safeNormalize(direction, new Vec3(0.0D, 0.0D, 1.0D));
            this.width = width;
            this.length = length;
            this.color = color;
            this.lifetime = lifetime;
            this.kind = kind;
        }
    }

    private static final class RemoteSlideState {
        private final UUID sessionId = UUID.randomUUID();
        private final List<TrailParticle> trails = new ArrayList<>();
        @Nullable
        private Frame frame;
        @Nullable
        private Vec3 previousPosition;
        @Nullable
        private UUID previousConnectionId;
        private final TurnAccumulator turnAccumulator = new TurnAccumulator();
        private final VisualFacingTracker visualFacing = new VisualFacingTracker();
        private double previousDistanceOnConnection;
        private double alpha;
        private double speed01;
        private double accelerationBlend;
        private double highwayBlend;
        private double platformBlend;
        private double verticalBlend;
        private double upBlend;
        private double downBlend;
        private double perceptualSpeed;
        private double turnBlend;
        private double signedTurn;
        private double accelerationPulse;
        private double motionPhase;
        private double lastSpeed;
        private int ticksSliding;
        private boolean previousAcceleration;
        private boolean previousHighway;

        private RemoteSlideState() {
        }

        private void tick(Minecraft minecraft, Player remotePlayer, Vec3 observerPosition) {
            tickTrailParticles(this.trails);
            Optional<RemoteSample> sample = this.sample(minecraft, remotePlayer);
            this.previousPosition = remotePlayer.position();
            if (sample.isEmpty()) {
                this.tickInactive();
                return;
            }

            RemoteSample next = sample.get();
            boolean continuedConnection = this.previousConnectionId != null && this.previousConnectionId.equals(next.connection().id());
            double speedDelta = continuedConnection ? next.speed() - this.lastSpeed : 0.0D;
            double targetSpeed01 = next.speed01();
            double targetPerceptualSpeed = smoothstep(0.06D, 0.92D, targetSpeed01);
            double targetAcceleration = next.rules().accelerationAttribute()
                    ? Math.max(0.32D, targetSpeed01 * 0.82D)
                    : Mth.clamp(speedDelta * 34.0D, 0.0D, 1.0D);
            double targetHighway = next.rules().highway() ? Math.max(0.36D, targetSpeed01) : 0.0D;
            double targetPlatform = next.connection().platformStopId().isPresent() && targetSpeed01 < 0.18D ? 0.45D : 0.0D;
            double targetVertical = smoothstep(0.62D, 0.86D, Math.abs(next.tangent().y));
            double targetUp = targetVertical * Math.max(0.0D, next.tangent().y);
            double targetDown = targetVertical * Math.max(0.0D, -next.tangent().y);
            TurnSample turn = this.turnAccumulator.sample(next.position(), next.tangent(), false, turnSpeedScale(targetSpeed01, targetPerceptualSpeed));
            Vec3 visualFacing = this.visualFacing.update(next.tangent(), remotePlayer.getYRot(), this.frame == null);
            double pulseTarget = Mth.clamp(speedDelta * 28.0D, 0.0D, 1.0D);
            if (next.rules().accelerationAttribute() && !this.previousAcceleration) {
                pulseTarget = Math.max(pulseTarget, 0.46D);
            }
            if (next.rules().highway() && !this.previousHighway) {
                pulseTarget = Math.max(pulseTarget, 0.54D);
            }

            this.alpha = approach(this.alpha, 0.86D, 0.22D);
            this.speed01 = approach(this.speed01, targetSpeed01, 0.24D);
            this.accelerationBlend = approach(this.accelerationBlend, targetAcceleration, 0.20D);
            this.highwayBlend = approach(this.highwayBlend, targetHighway, 0.17D);
            this.platformBlend = approach(this.platformBlend, targetPlatform, 0.16D);
            this.verticalBlend = approach(this.verticalBlend, targetVertical, 0.20D);
            this.upBlend = approach(this.upBlend, targetUp, 0.20D);
            this.downBlend = approach(this.downBlend, targetDown, 0.20D);
            this.perceptualSpeed = approach(this.perceptualSpeed, targetPerceptualSpeed, 0.28D);
            this.turnBlend = approach(this.turnBlend, turn.intensity(), turn.intensity() > this.turnBlend ? 0.36D : 0.14D);
            this.signedTurn = approach(this.signedTurn, turn.signedIntensity(), 0.28D);
            this.accelerationPulse = Math.max(this.accelerationPulse * 0.74D, pulseTarget);
            this.motionPhase = (this.motionPhase + 0.026D + this.perceptualSpeed * 0.105D + this.accelerationPulse * 0.035D + this.highwayBlend * 0.024D) % 1.0D;
            this.ticksSliding = continuedConnection ? this.ticksSliding + 1 : 1;

            this.frame = new Frame(
                    true,
                    this.alpha,
                    this.sessionId,
                    next.connection().id(),
                    next.distanceOnConnection(),
                    next.connection().length(),
                    next.position(),
                    next.tangent(),
                    visualFacing,
                    next.speed(),
                    this.speed01,
                    this.perceptualSpeed,
                    this.accelerationBlend,
                    this.highwayBlend,
                    this.platformBlend,
                    this.verticalBlend,
                    this.upBlend,
                    this.downBlend,
                    this.turnBlend,
                    this.signedTurn,
                    0.0D,
                    0.0D,
                    this.accelerationPulse,
                    this.motionPhase,
                    0.0D,
                    0.0D,
                    this.ticksSliding
            );

            double distance = remotePlayer.position().distanceTo(observerPosition);
            double density = remoteTrailDensity(distance);
            spawnFrameTrails(
                    this.trails,
                    next.position(),
                    next.tangent(),
                    this.speed01,
                    next.rules().highway(),
                    targetPlatform > 0.35D,
                    this.alpha,
                    this.perceptualSpeed,
                    this.accelerationBlend,
                    this.highwayBlend,
                    this.platformBlend,
                    this.verticalBlend,
                    this.upBlend,
                    this.downBlend,
                    this.accelerationPulse,
                    density,
                    MAX_REMOTE_TRAILS_PER_PLAYER
            );

            this.previousConnectionId = next.connection().id();
            this.previousDistanceOnConnection = next.distanceOnConnection();
            this.lastSpeed = next.speed();
            this.previousAcceleration = next.rules().accelerationAttribute();
            this.previousHighway = next.rules().highway();
        }

        private Optional<RemoteSample> sample(Minecraft minecraft, Player remotePlayer) {
            if (minecraft.level == null) {
                return Optional.empty();
            }
            Vec3 current = remotePlayer.position();
            if (this.previousPosition == null) {
                return Optional.empty();
            }
            Vec3 movement = current.subtract(this.previousPosition);
            double step = movement.length();
            double minStep = this.frame != null && this.alpha > 0.18D ? REMOTE_CONTINUE_MIN_STEP : REMOTE_START_MIN_STEP;
            if (step < minStep) {
                return Optional.empty();
            }
            Vec3 motionDirection = movement.scale(1.0D / step);

            PipeConnection bestConnection = null;
            SlideGeometry.Projection bestProjection = null;
            ResolvedPipeSpeedRules bestRules = null;
            Vec3 bestTangent = Vec3.ZERO;
            double bestScore = Double.MAX_VALUE;
            for (PipeConnection connection : ClientPipeNetworkCache.connectionsNear(minecraft.level.dimension(), current, REMOTE_PIPE_QUERY_RADIUS)) {
                SlideGeometry.Projection projection = SlideGeometry.project(connection, current);
                boolean sameConnection = this.previousConnectionId != null && this.previousConnectionId.equals(connection.id());
                double maxDistance = sameConnection ? REMOTE_CONTINUITY_DISTANCE : REMOTE_SIGNAL_DISTANCE;
                if (!projection.withinSegment(0.14D) || projection.distance() > maxDistance) {
                    continue;
                }

                Vec3 tangent = safeNormalize(connection.tangentAt(projection.distanceOnConnection()), new Vec3(0.0D, 0.0D, 1.0D));
                double forward = tangent.dot(motionDirection);
                int direction = forward >= 0.0D ? 1 : -1;
                double alignment = Math.abs(forward);
                double minAlignment = sameConnection ? 0.28D : 0.42D;
                if (alignment < minAlignment) {
                    continue;
                }

                ResolvedPipeSpeedRules rules = ResolvedPipeSpeedRules.from(connection.resolvedAttributes());
                Vec3 directedTangent = tangent.scale(direction);
                double distanceChange = sameConnection ? Math.abs(projection.distanceOnConnection() - this.previousDistanceOnConnection) : step;
                double continuityBonus = sameConnection ? 0.18D : 0.0D;
                double progressBonus = Mth.clamp(distanceChange * 3.0D, 0.0D, 0.16D);
                double score = projection.distance() * 1.35D - alignment * 0.24D - continuityBonus - progressBonus;
                if (score < bestScore) {
                    bestScore = score;
                    bestConnection = connection;
                    bestProjection = projection;
                    bestRules = rules;
                    bestTangent = directedTangent;
                }
            }

            if (bestConnection == null || bestProjection == null || bestRules == null) {
                return Optional.empty();
            }
            double maxSpeed = bestRules.maxSpeed();
            double speed01 = maxSpeed <= 1.0E-6D ? Mth.clamp(step / 0.44D, 0.0D, 1.0D) : Mth.clamp(step / maxSpeed, 0.0D, 1.0D);
            return Optional.of(new RemoteSample(
                    bestConnection,
                    bestProjection.distanceOnConnection(),
                    bestConnection.positionAt(bestProjection.distanceOnConnection()),
                    safeNormalize(bestTangent, new Vec3(0.0D, 0.0D, 1.0D)),
                    step,
                    speed01,
                    bestRules
            ));
        }

        private void tickUnavailable() {
            tickTrailParticles(this.trails);
            this.previousPosition = null;
            this.turnAccumulator.reset();
            this.visualFacing.reset();
            this.tickInactive();
        }

        private void tickInactive() {
            this.alpha = approach(this.alpha, 0.0D, 0.18D);
            this.speed01 = approach(this.speed01, 0.0D, 0.18D);
            this.accelerationBlend = approach(this.accelerationBlend, 0.0D, 0.22D);
            this.highwayBlend = approach(this.highwayBlend, 0.0D, 0.20D);
            this.platformBlend = approach(this.platformBlend, 0.0D, 0.18D);
            this.verticalBlend = approach(this.verticalBlend, 0.0D, 0.18D);
            this.upBlend = approach(this.upBlend, 0.0D, 0.18D);
            this.downBlend = approach(this.downBlend, 0.0D, 0.18D);
            this.perceptualSpeed = approach(this.perceptualSpeed, 0.0D, 0.18D);
            this.turnBlend = approach(this.turnBlend, 0.0D, 0.20D);
            this.signedTurn = approach(this.signedTurn, 0.0D, 0.22D);
            this.accelerationPulse *= 0.72D;
            this.motionPhase = (this.motionPhase + 0.014D) % 1.0D;
            if (this.frame != null) {
                this.frame = this.frame.withFeedback(this.alpha, this.speed01, this.perceptualSpeed, this.accelerationBlend, this.highwayBlend, this.platformBlend, this.verticalBlend, this.upBlend, this.downBlend, this.turnBlend, this.signedTurn, 0.0D, 0.0D, this.accelerationPulse, this.motionPhase, 0.0D, 0.0D);
            }
            if (this.alpha <= 0.015D && this.trails.isEmpty()) {
                this.frame = null;
                this.previousConnectionId = null;
                this.turnAccumulator.reset();
                this.visualFacing.reset();
                this.ticksSliding = 0;
                this.previousAcceleration = false;
                this.previousHighway = false;
            }
        }

        private Optional<Frame> currentFrame() {
            return this.frame == null || this.frame.alpha() <= 0.015D ? Optional.empty() : Optional.of(this.frame);
        }

        private boolean hasTrails() {
            return !this.trails.isEmpty();
        }

        private void appendTrailSnapshots(List<TrailParticleSnapshot> snapshots) {
            ClientSlideFeedbackController.appendTrailSnapshots(this.trails, snapshots);
        }

        private boolean isExpired() {
            return this.frame == null && this.trails.isEmpty() && this.previousPosition == null;
        }

    }

    public record Frame(
            boolean active,
            double alpha,
            UUID sessionId,
            UUID connectionId,
            double distanceOnConnection,
            double connectionLength,
            Vec3 position,
            Vec3 tangent,
            Vec3 visualFacing,
            double speed,
            double speed01,
            double perceptualSpeed,
            double accelerationBlend,
            double highwayBlend,
            double platformBlend,
            double verticalBlend,
            double upBlend,
            double downBlend,
            double turnBlend,
            double signedTurn,
            double turnPreviewBlend,
            double signedTurnPreview,
            double accelerationPulse,
            double motionPhase,
            double fovBoost,
            double edgeIntensity,
            int ticksSliding
    ) {
        private static Frame interpolate(Frame previous, Frame current, double partialTick) {
            double t = Mth.clamp(partialTick, 0.0D, 1.0D);
            if (t <= 0.0D) {
                return previous;
            }
            if (t >= 1.0D) {
                return current;
            }
            return new Frame(
                    current.active,
                    lerp(previous.alpha, current.alpha, t),
                    current.sessionId,
                    current.connectionId,
                    lerp(previous.distanceOnConnection, current.distanceOnConnection, t),
                    lerp(previous.connectionLength, current.connectionLength, t),
                    lerp(previous.position, current.position, t),
                    lerpDirection(previous.tangent, current.tangent, t),
                    lerpDirection(previous.visualFacing, current.visualFacing, t),
                    lerp(previous.speed, current.speed, t),
                    lerp(previous.speed01, current.speed01, t),
                    lerp(previous.perceptualSpeed, current.perceptualSpeed, t),
                    lerp(previous.accelerationBlend, current.accelerationBlend, t),
                    lerp(previous.highwayBlend, current.highwayBlend, t),
                    lerp(previous.platformBlend, current.platformBlend, t),
                    lerp(previous.verticalBlend, current.verticalBlend, t),
                    lerp(previous.upBlend, current.upBlend, t),
                    lerp(previous.downBlend, current.downBlend, t),
                    lerp(previous.turnBlend, current.turnBlend, t),
                    lerp(previous.signedTurn, current.signedTurn, t),
                    lerp(previous.turnPreviewBlend, current.turnPreviewBlend, t),
                    lerp(previous.signedTurnPreview, current.signedTurnPreview, t),
                    lerp(previous.accelerationPulse, current.accelerationPulse, t),
                    lerpUnit(previous.motionPhase, current.motionPhase, t),
                    lerp(previous.fovBoost, current.fovBoost, t),
                    lerp(previous.edgeIntensity, current.edgeIntensity, t),
                    current.ticksSliding
            );
        }

        private Frame withFeedback(double nextAlpha, double nextSpeed01, double nextPerceptualSpeed, double nextAccelerationBlend, double nextHighwayBlend, double nextPlatformBlend, double nextVerticalBlend, double nextUpBlend, double nextDownBlend, double nextTurnBlend, double nextSignedTurn, double nextTurnPreviewBlend, double nextSignedTurnPreview, double nextAccelerationPulse, double nextMotionPhase, double nextFovBoost, double nextEdgeIntensity) {
            return new Frame(
                    this.active,
                    nextAlpha,
                    this.sessionId,
                    this.connectionId,
                    this.distanceOnConnection,
                    this.connectionLength,
                    this.position,
                    this.tangent,
                    this.visualFacing,
                    this.speed,
                    nextSpeed01,
                    nextPerceptualSpeed,
                    nextAccelerationBlend,
                    nextHighwayBlend,
                    nextPlatformBlend,
                    nextVerticalBlend,
                    nextUpBlend,
                    nextDownBlend,
                    nextTurnBlend,
                    nextSignedTurn,
                    nextTurnPreviewBlend,
                    nextSignedTurnPreview,
                    nextAccelerationPulse,
                    nextMotionPhase,
                    nextFovBoost,
                    nextEdgeIntensity,
                    this.ticksSliding
            );
        }

        private static double lerp(double from, double to, double t) {
            return from + (to - from) * t;
        }

        private static Vec3 lerp(Vec3 from, Vec3 to, double t) {
            return new Vec3(lerp(from.x, to.x, t), lerp(from.y, to.y, t), lerp(from.z, to.z, t));
        }

        private static Vec3 lerpDirection(Vec3 from, Vec3 to, double t) {
            Vec3 value = lerp(from, to, t);
            return value.lengthSqr() < 1.0E-8D ? to : value.normalize();
        }

        private static double lerpUnit(double from, double to, double t) {
            double delta = to - from;
            if (delta > 0.5D) {
                delta -= 1.0D;
            } else if (delta < -0.5D) {
                delta += 1.0D;
            }
            double value = from + delta * t;
            value %= 1.0D;
            return value < 0.0D ? value + 1.0D : value;
        }
    }

    public record TrailParticleSnapshot(Vec3 position, Vec3 direction, double width, double length, int color, double age01, int kind) {
    }

    private record TurnSample(double intensity, double signedIntensity) {
    }

    private static final class VisualFacingTracker {
        private static final double HORIZONTAL_UPDATE_THRESHOLD = 0.08D;
        private static final double FACING_SMOOTH_STEP = 0.28D;

        private Vec3 facing = DEFAULT_VISUAL_FACING;
        private boolean initialized;

        private Vec3 update(Vec3 tangent, float entityYaw, boolean reset) {
            if (reset) {
                this.reset();
            }
            Vec3 horizontal = new Vec3(tangent.x, 0.0D, tangent.z);
            if (horizontal.lengthSqr() > HORIZONTAL_UPDATE_THRESHOLD * HORIZONTAL_UPDATE_THRESHOLD) {
                Vec3 target = horizontal.normalize();
                this.facing = this.initialized
                        ? safeNormalize(this.facing.scale(1.0D - FACING_SMOOTH_STEP).add(target.scale(FACING_SMOOTH_STEP)), target)
                        : target;
                this.initialized = true;
                return this.facing;
            }
            if (!this.initialized) {
                this.facing = safeNormalize(Vec3.directionFromRotation(0.0F, entityYaw), DEFAULT_VISUAL_FACING);
                this.initialized = true;
            }
            return this.facing;
        }

        private void reset() {
            this.facing = DEFAULT_VISUAL_FACING;
            this.initialized = false;
        }
    }

    private static final class TurnPreviewTracker {
        private double heldIntensity;
        private double heldSignedIntensity;

        private TurnSample sample(Vec3 currentTangent, boolean reset, double speedScale) {
            if (reset) {
                this.reset();
            }
            TurnSample raw = this.computeRawTurnPreview(currentTangent, speedScale);
            double rawSigned = raw.signedIntensity();
            double targetIntensity = Math.abs(rawSigned) > 0.0D ? Math.max(raw.intensity(), Math.abs(rawSigned)) : raw.intensity();
            double signedStep = Math.abs(rawSigned) > Math.abs(this.heldSignedIntensity) ? 0.30D : 0.11D;
            if (Math.signum(rawSigned) != Math.signum(this.heldSignedIntensity) && Math.abs(this.heldSignedIntensity) > 0.08D) {
                signedStep = 0.18D;
            }
            double intensityStep = targetIntensity > this.heldIntensity ? 0.32D : 0.10D;
            this.heldSignedIntensity = approach(this.heldSignedIntensity, rawSigned, signedStep);
            this.heldIntensity = approach(this.heldIntensity, targetIntensity, intensityStep);
            if (Math.abs(this.heldSignedIntensity) < 0.005D) {
                this.heldSignedIntensity = 0.0D;
            }
            if (this.heldIntensity < 0.005D) {
                this.heldIntensity = 0.0D;
            }
            return new TurnSample(this.heldIntensity, this.heldSignedIntensity);
        }

        private TurnSample computeRawTurnPreview(Vec3 currentTangent, double speedScale) {
            if (speedScale <= 0.0D) {
                return new TurnSample(0.0D, 0.0D);
            }
            Vec3 currentHorizontal = new Vec3(currentTangent.x, 0.0D, currentTangent.z);
            double currentLength = currentHorizontal.length();
            if (currentLength < TURN_HORIZONTAL_DEADBAND) {
                return new TurnSample(0.0D, 0.0D);
            }

            List<ClientSlideController.SlidePreviewConnection> previewConnections = ClientSlideController.slidePreviewConnections(TURN_PREVIEW_MAX_CONNECTIONS);
            if (previewConnections.isEmpty()) {
                return new TurnSample(0.0D, 0.0D);
            }

            TurnSample best = new TurnSample(0.0D, 0.0D);
            double walked = 0.0D;
            for (ClientSlideController.SlidePreviewConnection preview : previewConnections) {
                PipeConnection connection = preview.connection();
                int direction = preview.direction();
                double length = connection.length();
                double startDistance = Mth.clamp(preview.startDistance(), 0.0D, length);
                double remaining = direction >= 0 ? length - startDistance : startDistance;
                if (remaining <= 1.0E-5D) {
                    continue;
                }
                double scanDistance = Math.min(remaining, TURN_PREVIEW_DISTANCE_MAX - walked);
                if (scanDistance <= 1.0E-5D) {
                    break;
                }

                double sampleDistance = Math.min(TURN_PREVIEW_SAMPLE_STEP, scanDistance);
                while (sampleDistance <= scanDistance + 1.0E-5D) {
                    double distanceOnConnection = startDistance + direction * sampleDistance;
                    Vec3 futureTangent = connection.tangentAt(distanceOnConnection).scale(direction);
                    TurnSample candidate = this.turnTo(currentHorizontal, currentLength, futureTangent, walked + sampleDistance, speedScale);
                    if (candidate.intensity() > best.intensity()) {
                        best = candidate;
                    }
                    sampleDistance += TURN_PREVIEW_SAMPLE_STEP;
                }

                if (Math.abs((sampleDistance - TURN_PREVIEW_SAMPLE_STEP) - scanDistance) > 0.18D) {
                    double distanceOnConnection = startDistance + direction * scanDistance;
                    Vec3 futureTangent = connection.tangentAt(distanceOnConnection).scale(direction);
                    TurnSample candidate = this.turnTo(currentHorizontal, currentLength, futureTangent, walked + scanDistance, speedScale);
                    if (candidate.intensity() > best.intensity()) {
                        best = candidate;
                    }
                }
                walked += remaining;
                if (walked >= TURN_PREVIEW_DISTANCE_MAX) {
                    break;
                }
            }
            return best;
        }

        private TurnSample turnTo(Vec3 currentHorizontal, double currentLength, Vec3 futureTangent, double futureDistance, double speedScale) {
            Vec3 futureHorizontal = new Vec3(futureTangent.x, 0.0D, futureTangent.z);
            double futureLength = futureHorizontal.length();
            double minHorizontal = Math.min(currentLength, futureLength);
            if (minHorizontal < TURN_HORIZONTAL_DEADBAND) {
                return new TurnSample(0.0D, 0.0D);
            }
            Vec3 from = currentHorizontal.scale(1.0D / currentLength);
            Vec3 to = futureHorizontal.scale(1.0D / futureLength);
            double signedAngle = Math.atan2(from.cross(to).dot(WORLD_UP), Mth.clamp(from.dot(to), -1.0D, 1.0D));
            double intensity = smoothstep(TURN_PREVIEW_MIN_ANGLE, TURN_PREVIEW_FULL_ANGLE, Math.abs(signedAngle))
                    * (1.0D - smoothstep(TURN_PREVIEW_DISTANCE_MIN, TURN_PREVIEW_DISTANCE_MAX, futureDistance))
                    * Mth.clamp(speedScale, 0.0D, 1.0D)
                    * smoothstep(TURN_HORIZONTAL_DEADBAND, 0.42D, minHorizontal);
            return new TurnSample(intensity, Math.signum(signedAngle) * intensity);
        }

        private void reset() {
            this.heldIntensity = 0.0D;
            this.heldSignedIntensity = 0.0D;
        }
    }

    private static final class TurnAccumulator {
        private final List<TurnHistorySample> history = new ArrayList<>();
        private double pathDistance;
        private double heldIntensity;
        private double heldSignedIntensity;

        private TurnSample sample(Vec3 position, Vec3 tangent, boolean reset, double speedScale) {
            if (reset) {
                this.reset();
            }
            Vec3 normalizedTangent = safeNormalize(tangent, new Vec3(0.0D, 0.0D, 1.0D));
            if (!this.history.isEmpty()) {
                TurnHistorySample previous = this.history.getLast();
                double step = position.distanceTo(previous.position());
                if (step > TURN_HISTORY_TELEPORT_DISTANCE) {
                    this.reset();
                } else {
                    this.pathDistance += step;
                }
            }

            TurnSample raw = this.history.isEmpty()
                    ? new TurnSample(0.0D, 0.0D)
                    : this.computeRawTurn(normalizedTangent, speedScale);
            this.append(position, normalizedTangent);
            return this.filter(raw);
        }

        private TurnSample computeRawTurn(Vec3 tangent, double speedScale) {
            TurnHistorySample reference = this.referenceSample();
            if (reference == null || speedScale <= 0.0D) {
                return new TurnSample(0.0D, 0.0D);
            }
            Vec3 fromHorizontal = new Vec3(reference.tangent().x, 0.0D, reference.tangent().z);
            Vec3 toHorizontal = new Vec3(tangent.x, 0.0D, tangent.z);
            double fromLength = fromHorizontal.length();
            double toLength = toHorizontal.length();
            if (fromLength < TURN_HORIZONTAL_DEADBAND || toLength < TURN_HORIZONTAL_DEADBAND) {
                return new TurnSample(0.0D, 0.0D);
            }
            Vec3 from = fromHorizontal.scale(1.0D / fromLength);
            Vec3 to = toHorizontal.scale(1.0D / toLength);
            double signedAngle = Math.atan2(from.cross(to).dot(WORLD_UP), Mth.clamp(from.dot(to), -1.0D, 1.0D));
            double absoluteAngle = Math.abs(signedAngle);
            double intensity = smoothstep(TURN_MIN_ANGLE, TURN_FULL_ANGLE, absoluteAngle)
                    * Mth.clamp(speedScale, 0.0D, 1.0D)
                    * smoothstep(TURN_HORIZONTAL_DEADBAND, 0.42D, Math.min(fromLength, toLength));
            return new TurnSample(intensity, Math.signum(signedAngle) * intensity);
        }

        @Nullable
        private TurnHistorySample referenceSample() {
            TurnHistorySample best = null;
            double bestScore = Double.MAX_VALUE;
            for (TurnHistorySample sample : this.history) {
                double behind = this.pathDistance - sample.pathDistance();
                if (behind < TURN_HISTORY_MIN_DISTANCE || behind > TURN_HISTORY_MAX_DISTANCE) {
                    continue;
                }
                double score = Math.abs(behind - TURN_HISTORY_LOOKBACK_DISTANCE);
                if (score < bestScore) {
                    best = sample;
                    bestScore = score;
                }
            }
            return best;
        }

        private TurnSample filter(TurnSample raw) {
            double rawSigned = raw.signedIntensity();
            double targetIntensity = Math.abs(rawSigned) > 0.0D ? Math.max(raw.intensity(), Math.abs(rawSigned)) : raw.intensity();
            double signedStep = Math.abs(rawSigned) > Math.abs(this.heldSignedIntensity) ? 0.34D : 0.12D;
            if (Math.signum(rawSigned) != Math.signum(this.heldSignedIntensity) && Math.abs(this.heldSignedIntensity) > 0.08D) {
                signedStep = 0.18D;
            }
            double intensityStep = targetIntensity > this.heldIntensity ? 0.38D : 0.09D;
            this.heldSignedIntensity = approach(this.heldSignedIntensity, rawSigned, signedStep);
            this.heldIntensity = approach(this.heldIntensity, targetIntensity, intensityStep);
            if (Math.abs(this.heldSignedIntensity) < 0.006D) {
                this.heldSignedIntensity = 0.0D;
            }
            if (this.heldIntensity < 0.006D) {
                this.heldIntensity = 0.0D;
            }
            return new TurnSample(this.heldIntensity, this.heldSignedIntensity);
        }

        private void append(Vec3 position, Vec3 tangent) {
            this.history.add(new TurnHistorySample(position, tangent, this.pathDistance));
            while (this.history.size() > TURN_HISTORY_MAX_SAMPLES) {
                this.history.removeFirst();
            }
            while (!this.history.isEmpty() && this.pathDistance - this.history.getFirst().pathDistance() > TURN_HISTORY_MAX_DISTANCE) {
                this.history.removeFirst();
            }
        }

        private void reset() {
            this.history.clear();
            this.pathDistance = 0.0D;
            this.heldIntensity = 0.0D;
            this.heldSignedIntensity = 0.0D;
        }
    }

    private record TurnHistorySample(Vec3 position, Vec3 tangent, double pathDistance) {
    }

    private record RemoteSample(PipeConnection connection, double distanceOnConnection, Vec3 position, Vec3 tangent, double speed, double speed01, ResolvedPipeSpeedRules rules) {
    }

    private static double remoteTrailDensity(double distance) {
        if (distance <= REMOTE_TRAIL_FULL_DISTANCE) {
            return 0.58D;
        }
        if (distance >= REMOTE_TRAIL_FADE_DISTANCE) {
            return 0.0D;
        }
        double t = 1.0D - (distance - REMOTE_TRAIL_FULL_DISTANCE) / (REMOTE_TRAIL_FADE_DISTANCE - REMOTE_TRAIL_FULL_DISTANCE);
        return 0.18D + t * 0.34D;
    }
}
