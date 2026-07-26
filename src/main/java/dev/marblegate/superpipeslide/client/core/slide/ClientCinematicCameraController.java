package dev.marblegate.superpipeslide.client.core.slide;

import dev.marblegate.superpipeslide.client.core.accessibility.ClientSafetyOptions;
import dev.marblegate.superpipeslide.client.core.gaze.ClientGazeChoiceController;
import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.config.ClientConfig;
import dev.marblegate.superpipeslide.mixin.client.CameraAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Cinematic perspective director (v3): documentary-style scenic shots while sliding.
 * The camera parks at validated vantage points framing a "stage" the rider crosses, and
 * hard-cuts between shots on motivated reasons only. Two orthogonal axes drive the
 * grammar: ShotContext (CRUISE / VERTICAL / STATION, arbitrated from slide signals)
 * decides the stage and cut policy, while ShotClass (VISTA / MEDIUM / INTERIOR, probed
 * from local openness) decides the spatial scale. All timing is in real seconds, the
 * camera position is clearance-validated every frame, and failing searches freeze the
 * current shot instead of flapping back to first person.
 */
public final class ClientCinematicCameraController {
    // Timing (real seconds, frame-rate independent)
    private static final double EVAL_INTERVAL_SECONDS = 0.5D;
    private static final double LOS_INTERVAL_SECONDS = 0.25D;
    private static final double ENTER_HYSTERESIS_SECONDS = 1.0D;
    private static final double CLASS_HYSTERESIS_SECONDS = 2.0D;
    private static final int LOS_FAIL_CUT_STREAK = 3;
    private static final double CUT_COOLDOWN_SECONDS = 3.0D;
    private static final double MIN_SHOT_DWELL_SECONDS = 2.0D;
    private static final double BLEND_RATE_PER_SECOND = 3.0D;
    // Duration of the eased return to first person after a real dismount.
    private static final double RETURN_SECONDS = 0.7D;
    // How far ahead (in ticks) the LOS probe looks to tell a passing pillar (LOS recovers
    // on its own) apart from persistent cover (LOS stays broken, cut immediately).
    private static final double LOS_FUTURE_PROBE_TICKS = 10.0D;
    // Framing
    private static final double OFF_AXIS_CUT_DEGREES = 55.0D;
    private static final double CROSSED_STAGE_DISTANCE = 8.0D;
    private static final double AIM_RAISE_FRACTION = 0.12D;
    private static final double RIDER_SCREEN_HEIGHT = 1.286D; // player height factor at FOV 70
    private static final double MAX_RIDER_PROPORTION = 0.35D;
    private static final double RIDER_MAX_FRAME_ANGLE = 35.0D;
    private static final int TOP_PICK_RANGE = 4;
    private static final java.util.Random SHOT_RANDOM = new java.util.Random();
    private static final double MIN_SCORE = 1.0D;
    // Context thresholds
    private static final double VERTICAL_ENTER_BLEND = 0.85D;
    private static final double VERTICAL_EXIT_BLEND = 0.35D;
    private static final double CONTEXT_MIN_DWELL_SECONDS = 2.0D;
    private static final double VERTICAL_REANCHOR_DISTANCE = 6.0D;
    private static final double STATION_ENTER_BLEND = 0.3D;
    private static final double STATION_HOLD_BLEND = 0.9D;
    private static final double STATION_HOLD_MAX_SPEED = 0.02D;
    private static final double BEND_ANCHOR_MAX_REMAINING = 25.0D;
    private static final double VERTICAL_TRACK_LEAD = 2.5D;
    // How long the drift clearance must stay clean before the camera wander eases back in
    // after being blocked by terrain (stops the drift from saw-toothing near slopes).
    private static final double DRIFT_RECOVER_SECONDS = 0.5D;
    // Corridor grammar (cluttered terrain): along-path vantages that keep line of sight
    // to the rider's upcoming path, giving static shots a multi-second lifespan where
    // any off-path vantage would lose the rider within a second or two.
    private static final double[] CORRIDOR_ANGLES = { 0.0D, 12.0D, -12.0D, 25.0D, -25.0D, 168.0D, -168.0D };
    private static final double[] CORRIDOR_DISTANCES = { 10.0D, 16.0D, 24.0D };
    private static final double[] CORRIDOR_HEIGHTS = { 1.5D, 3.0D, 5.0D };
    private static final int PATH_SAMPLE_COUNT = 6;
    private static final double CLUTTER_ENTER_RATIO = 0.45D;
    private static final double CLUTTER_EXIT_RATIO = 0.30D;

    private static double blend;
    private static double returnStartBlend;
    private static double returnProgress;
    private static double evalSeconds;
    private static double losSeconds;
    private static double enterStreakSeconds = Double.MAX_VALUE;
    private static double classStreakSeconds;
    private static ShotClass pendingClass;
    private static double cooldownSeconds;
    private static double contextDwellSeconds;
    private static int losFailStreak;
    private static ShotContext context = ShotContext.CRUISE;
    private static ClientSceneProbe.SceneShape sceneShape = ClientSceneProbe.SceneShape.FIELD;
    private static ClientSceneProbe.SceneFeatures sceneFeatures;
    private static Vec3 lastRiderPos;
    private static ResourceKey<Level> lastLevelKey;
    private static Vec3 lastSafePosition;
    private static double driftAmount;
    private static double driftBlockedSeconds;
    private static double clutterEma;
    private static boolean corridorMode;
    private static Shot shot;

    private ClientCinematicCameraController() {}

    private enum ShotContext {
        CRUISE, VERTICAL, STATION
    }

    private enum CutReason {
        LOS_BROKEN, TOO_FAR, OFF_AXIS, CROSSED_STAGE, TOO_CLOSE, SHOT_AGE, CLASS_CHANGE
    }

    private record Shot(Vec3 position, Vec3 aim, Vec3 stage, Vec3 travel, ShotContext context, ShotClass shotClass, boolean side, double minCut, double maxCut, double ageSeconds, double driftPhase) {
        Shot withAge(double ageSeconds) {
            return new Shot(this.position, this.aim, this.stage, this.travel, this.context, this.shotClass, this.side, this.minCut, this.maxCut, ageSeconds, this.driftPhase);
        }

        Shot withPosition(Vec3 position) {
            return new Shot(position, this.aim, this.stage, this.travel, this.context, this.shotClass, this.side, this.minCut, this.maxCut, this.ageSeconds, this.driftPhase);
        }

        Shot withStage(Vec3 stage, Vec3 travel, Vec3 aim) {
            return new Shot(this.position, aim, stage, travel, this.context, this.shotClass, this.side, this.minCut, this.maxCut, this.ageSeconds, this.driftPhase);
        }
    }

    public static void clear() {
        blend = 0.0D;
        returnStartBlend = 0.0D;
        returnProgress = 0.0D;
        shot = null;
        context = ShotContext.CRUISE;
        evalSeconds = 0.0D;
        losSeconds = 0.0D;
        enterStreakSeconds = Double.MAX_VALUE;
        classStreakSeconds = 0.0D;
        pendingClass = null;
        cooldownSeconds = 0.0D;
        contextDwellSeconds = 0.0D;
        losFailStreak = 0;
        lastRiderPos = null;
        lastLevelKey = null;
        lastSafePosition = null;
        driftAmount = 0.0D;
        driftBlockedSeconds = 0.0D;
        clutterEma = 0.0D;
        corridorMode = false;
    }

    public static boolean isActive() {
        return blend > 0.02D;
    }

    /**
     * True once the cinematic shot has mostly taken over. HUD elements that only make
     * sense for the first-person view (crosshair, block outline, slide action hints)
     * hide past this point.
     */
    public static boolean hidesFirstPersonHud() {
        return blendFactor() > 0.5D;
    }

    /**
     * 0..1 progress of the cinematic blend, used to fade ordinary slide camera feedback
     * out exactly as the shot eases in, keeping a clean boundary between camera modes.
     */
    public static double blendFactor() {
        return Mth.clamp(blend, 0.0D, 1.0D);
    }

    public static void apply(Camera camera, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            clear();
            return;
        }
        CameraAccessor access = (CameraAccessor) (Object) camera;
        double deltaSeconds = deltaSeconds(minecraft);
        Optional<ClientSlideFeedbackController.Frame> frame = ClientSlideFeedbackController.currentRenderFrame();
        double intensity = ClientConfig.CINEMATIC_CAMERA_INTENSITY.get();
        boolean enabled = ClientSafetyOptions.cinematicCameraEnabled() && !ClientGazeChoiceController.hasActiveChoice() && intensity > 1.0E-6D;
        double targetBlend;
        if (!enabled) {
            targetBlend = 0.0D;
        } else if (ClientSlideController.isSlidingOrTransferring()) {
            // Fully engaged while the slide session is alive. Mirroring the feedback
            // frame's alpha used to park the blend in the mid range on collision-heavy
            // sections, where the eye-position share of the camera leaked every jolt.
            targetBlend = 1.0D;
        } else if (shot != null && ClientSlideController.hasOpenRecaptureWindow(player.level())) {
            // Collision-style detach with a live recapture window: hold the blend
            // untouched so a quick recapture never dips the camera (the downhill-curve
            // detach/recapture cycle used to pump the blend about once a second).
            targetBlend = blend;
        } else {
            // Intentional exits (sneak/jump) open no recapture window: the return
            // starts immediately, with no dead time before the camera heads back.
            targetBlend = 0.0D;
        }
        if (targetBlend <= 0.0D && blend > 0.0D) {
            // Time-boxed smoothstep return: starts gently, moves deliberately, and
            // arrives exactly at first person — no exponential head-start rush and no
            // cutoff snap at the end.
            if (returnStartBlend <= 0.0D) {
                returnStartBlend = blend;
                returnProgress = 0.0D;
            }
            returnProgress = Math.min(1.0D, returnProgress + deltaSeconds / RETURN_SECONDS);
            double eased = returnProgress * returnProgress * (3.0D - 2.0D * returnProgress);
            blend = returnProgress >= 1.0D ? 0.0D : returnStartBlend * (1.0D - eased);
        } else {
            returnStartBlend = 0.0D;
            returnProgress = 0.0D;
            blend = approachExp(blend, targetBlend, BLEND_RATE_PER_SECOND, deltaSeconds);
        }
        if (blend <= 0.02D) {
            blend = targetBlend <= 0.0D ? 0.0D : blend;
            shot = null;
            lastSafePosition = null;
            return;
        }
        if (frame.isEmpty()) {
            // The slide session just ended: keep the frozen shot for the whole return
            // journey instead of clearing it here. Clearing at this point would snap the
            // camera through the remaining ~10% of the blend in a single frame and make
            // the player lose their bearings.
            if (shot != null) {
                access.superpipeslide$setDetached(true);
                writeCamera(minecraft, player, camera, access, partialTick, intensity);
            }
            return;
        }

        ClientSlideFeedbackController.Frame snapshot = frame.get();
        Vec3 rider = snapshot.position();
        // Only large teleports void the shot: reconcile hops after block collisions are
        // small and must not churn the framing.
        if (lastRiderPos != null && (rider.distanceToSqr(lastRiderPos) > 576.0D || !Objects.equals(lastLevelKey, player.level().dimension()))) {
            shot = null;
            lastSafePosition = null;
        }
        lastRiderPos = rider;
        lastLevelKey = player.level().dimension();

        boolean paused = minecraft.isPaused();
        if (!paused && shot != null) {
            shot = shot.withAge(shot.ageSeconds() + deltaSeconds);
            cooldownSeconds = Math.max(0.0D, cooldownSeconds - deltaSeconds);
            tickPushIn(minecraft.level, deltaSeconds);
            // Cheap per-frame cut evaluation (pure math, see shouldCut for gating)
            CutReason cheapReason = cheapCutReason(minecraft.level, player, snapshot);
            if (cheapReason != null) {
                cutTo(minecraft.level, player, snapshot, cheapReason);
            }
        }
        if (!paused) {
            losSeconds += deltaSeconds;
            evalSeconds += deltaSeconds;
            if (losSeconds >= LOS_INTERVAL_SECONDS) {
                losSeconds = 0.0D;
                if (shot != null && !hasLineOfSight(minecraft.level, shot.position(), rider.add(0.0D, 0.9D, 0.0D), player)) {
                    // Tell a passing pillar (the rider pops back into view on their own,
                    // so wait out the streak) apart from riding behind persistent cover:
                    // probe where the rider is heading, and if LOS stays broken there,
                    // cut immediately instead of leaving the screen blocked.
                    Vec3 ahead = rider.add(safeNormalize(snapshot.tangent()).scale(Math.max(0.0D, snapshot.speed()) * LOS_FUTURE_PROBE_TICKS)).add(0.0D, 0.9D, 0.0D);
                    boolean futureBroken = !hasLineOfSight(minecraft.level, shot.position(), ahead, player);
                    losFailStreak++;
                    if (futureBroken || losFailStreak >= LOS_FAIL_CUT_STREAK) {
                        losFailStreak = 0;
                        cutTo(minecraft.level, player, snapshot, CutReason.LOS_BROKEN);
                    }
                } else {
                    losFailStreak = 0;
                }
            }
            if (evalSeconds >= EVAL_INTERVAL_SECONDS) {
                evalSeconds = 0.0D;
                evaluate(minecraft.level, player, snapshot, deltaSeconds);
            }
        }

        if (shot == null) {
            lastSafePosition = null;
            return;
        }
        access.superpipeslide$setDetached(true);
        writeCamera(minecraft, player, camera, access, partialTick, intensity);
    }

    private static void tickPushIn(Level level, double deltaSeconds) {
        if (context == ShotContext.STATION && isStationHold()) {
            return;
        }
        Vec3 aimDirection = shot.aim().subtract(shot.position());
        if (aimDirection.lengthSqr() < 1.0E-6D) {
            return;
        }
        aimDirection = aimDirection.normalize();
        double speed = 0.2D * deltaSeconds;
        int moveMode = (int) (shot.driftPhase() * 3.0D) % 3;
        Vec3 move = switch (moveMode) {
            case 1 -> aimDirection.scale(-speed);
            case 2 -> new Vec3(-aimDirection.z, 0.0D, aimDirection.x).normalize().scale(shot.side() ? speed : -speed);
            default -> aimDirection.scale(speed);
        };
        if (move.lengthSqr() < 1.0E-9D) {
            return;
        }
        Vec3 pushed = shot.position().add(move);
        if (hasClearance(level, pushed)) {
            shot = shot.withPosition(pushed);
        }
    }

    private static void evaluate(Level level, LocalPlayer player, ClientSlideFeedbackController.Frame snapshot, double deltaSeconds) {
        context = arbitrateContext(snapshot);
        sceneFeatures = ClientSceneProbe.sample(level, player, snapshot);
        ClientSceneProbe.SceneAssessment scene = ClientSceneProbe.assess(sceneFeatures);
        sceneShape = scene.shape();
        ShotClass probed = shotClassFor(scene, sceneFeatures, shot == null ? null : shot.shotClass());
        if (pendingClass != probed) {
            pendingClass = probed;
            classStreakSeconds = 0.0D;
        } else {
            classStreakSeconds += EVAL_INTERVAL_SECONDS;
        }

        if (shot != null) {
            // Station scenes sit right on the openness thresholds, so the probed class
            // flaps between VISTA/MEDIUM/INTERIOR; reframing on every flip reads as
            // tremble. Keep the shot's class until the rider is back in motion grammar.
            boolean classChanged = context != ShotContext.STATION && probed != ShotClass.NONE && probed != shot.shotClass() && classStreakSeconds >= CLASS_HYSTERESIS_SECONDS;
            if (classChanged) {
                cutTo(level, player, snapshot, CutReason.CLASS_CHANGE);
                return;
            }
            // Refresh vertical long-run stage silently, but only re-anchor when the
            // stage has actually drifted away from the rider; constant re-anchoring on
            // sloped curves used to make the camera chase the rider's heading.
            if (context == ShotContext.VERTICAL && shot.context() == ShotContext.VERTICAL && shot.stage() != null) {
                // Glide the aim toward its ideal instead of snapping: at vertical speeds
                // the 6-block stage re-anchor fires several times a second, and every
                // aim snap read as a jolt despite no shot cut happening.
                Vec3 idealAim = shotAimFor(shot.position(), shot.stage());
                if (shot.aim().distanceToSqr(idealAim) > 1.0E-4D) {
                    shot = shot.withStage(shot.stage(), shot.travel(), shot.aim().lerp(idealAim, 0.4D));
                }
                if (shot.stage().distanceTo(snapshot.position()) > VERTICAL_REANCHOR_DISTANCE) {
                    shot = shot.withStage(verticalStage(snapshot), safeNormalize(snapshot.tangent()), shot.aim());
                }
            }
            return;
        }

        ShotCandidate next = searchShot(level, player, snapshot, probed, context, null);
        if (next == null) {
            applyShot(fallbackShot(snapshot.position(), safeNormalize(snapshot.tangent()), context, probed, player), snapshot);
            enterStreakSeconds = 0.0D;
            return;
        }
        enterStreakSeconds += EVAL_INTERVAL_SECONDS;
        if (enterStreakSeconds >= ENTER_HYSTERESIS_SECONDS) {
            applyShot(next, snapshot);
            enterStreakSeconds = Double.MAX_VALUE;
        }
    }

    private static ShotContext arbitrateContext(ClientSlideFeedbackController.Frame snapshot) {
        if (snapshot.platformBlend() > STATION_ENTER_BLEND) {
            contextDwellSeconds = 0.0D;
            return ShotContext.STATION;
        }
        ShotContext desired = snapshot.verticalBlend() > (context == ShotContext.VERTICAL ? VERTICAL_EXIT_BLEND : VERTICAL_ENTER_BLEND)
                ? ShotContext.VERTICAL
                : ShotContext.CRUISE;
        // Dwell accounting must use the eval cadence, not the render-frame delta that
        // evaluate() happens to receive: accumulating frame deltas once per eval made
        // the 2-second dwell effectively last minutes at high frame rates, so the
        // context never left STATION after a platform stop (and with it every
        // non-station cut reason stayed exempt for the whole ride).
        if (desired == context) {
            contextDwellSeconds += EVAL_INTERVAL_SECONDS;
            return context;
        }
        // Hold the current context through short flirtations across the threshold so
        // sloped curves do not thrash between cruise and vertical grammars.
        if (contextDwellSeconds < CONTEXT_MIN_DWELL_SECONDS) {
            contextDwellSeconds += EVAL_INTERVAL_SECONDS;
            return context;
        }
        contextDwellSeconds = 0.0D;
        return desired;
    }

    private static ShotClass shotClassFor(ClientSceneProbe.SceneAssessment scene, ClientSceneProbe.SceneFeatures features, ShotClass current) {
        boolean grand = scene.skyOpenness() >= 0.7D && (features.axisLength() >= 20.0D || features.forwardDepth() >= 0.6D);
        return switch (scene.shape()) {
            case FIELD -> grand ? ShotClass.VISTA : features.openness() >= 8.0D ? ShotClass.MEDIUM : ShotClass.INTERIOR;
            case CORRIDOR -> features.axisLength() >= 20.0D ? ShotClass.VISTA : features.axisLength() >= 12.0D ? ShotClass.MEDIUM : ShotClass.INTERIOR;
            case BACKDROP, EDGE, WELL -> grand ? ShotClass.VISTA : ShotClass.MEDIUM;
            case INTERIOR -> ShotClass.INTERIOR;
        };
    }

    private static boolean isStationHold() {
        Optional<ClientSlideFeedbackController.Frame> frame = ClientSlideFeedbackController.currentRenderFrame();
        if (frame.isEmpty()) {
            return false;
        }
        return (frame.get().platformBlend() > STATION_HOLD_BLEND && frame.get().speed01() < STATION_HOLD_MAX_SPEED) || ClientSlideController.isHoldingAtStationCenter();
    }

    private static CutReason cheapCutReason(Level level, LocalPlayer player, ClientSlideFeedbackController.Frame snapshot) {
        Vec3 rider = snapshot.position();
        double distance = rider.distanceTo(shot.position());
        if (distance > shot.maxCut()) {
            return CutReason.TOO_FAR;
        }
        if (cooldownSeconds > 0.0D || shot.ageSeconds() < MIN_SHOT_DWELL_SECONDS) {
            return null;
        }
        boolean stationHold = context == ShotContext.STATION && isStationHold();
        if (stationHold) {
            return null;
        }
        // The station exemption follows the live arbitrated context, not the context
        // baked into the shot at creation time; otherwise a shot taken near a platform
        // keeps its master-shot immunity long after the rider has left the area.
        if (context != ShotContext.STATION) {
            double stageDistance = shot.stage() != null ? shot.stage().distanceTo(snapshot.position()) : 20.0D;
            double maxAge = Math.max(10.0D, 2.0D * stageDistance / Math.max(1.0D, snapshot.speed() * 20.0D));
            if (shot.ageSeconds() > maxAge) {
                return CutReason.SHOT_AGE;
            }
            if (distance < shot.minCut()) {
                return CutReason.TOO_CLOSE;
            }
            Vec3 viewDirection = shot.aim().subtract(shot.position());
            Vec3 riderDirection = rider.subtract(shot.position());
            if (viewDirection.lengthSqr() > 1.0E-6D && riderDirection.lengthSqr() > 1.0E-6D) {
                double angle = Math.toDegrees(Math.acos(Mth.clamp(viewDirection.normalize().dot(riderDirection.normalize()), -1.0D, 1.0D)));
                if (angle > OFF_AXIS_CUT_DEGREES) {
                    return CutReason.OFF_AXIS;
                }
            }
            if (shot.stage() != null && shot.travel() != null && rider.subtract(shot.stage()).dot(shot.travel()) > CROSSED_STAGE_DISTANCE) {
                return CutReason.CROSSED_STAGE;
            }
        }
        return null;
    }

    private static void cutTo(Level level, LocalPlayer player, ClientSlideFeedbackController.Frame snapshot, CutReason reason) {
        cooldownSeconds = CUT_COOLDOWN_SECONDS;
        ShotCandidate next = searchShot(level, player, snapshot, pendingClass == null ? ShotClass.NONE : pendingClass, context, shot);
        if (next != null && (next.shotClass() != shot.shotClass() || reason != CutReason.SHOT_AGE) && next.position().distanceTo(shot.position()) > 0.5D) {
            applyShot(next, snapshot);
            return;
        }
        if (reason == CutReason.LOS_BROKEN || reason == CutReason.TOO_FAR) {
            // The rider must stay visible: a persistently occluded or out-of-range shot
            // has to move, so take the guaranteed close over-shoulder fallback. But a
            // shot that is already close gains nothing from a fresh fallback — it would
            // just strobe the dip-to-black in fully occluded spots — so freeze it.
            if (shot.position().distanceTo(snapshot.position()) > ShotClass.INTERIOR.maxCut()) {
                applyShot(fallbackShot(snapshot.position(), safeNormalize(snapshot.tangent()), context, pendingClass == null ? ShotClass.INTERIOR : pendingClass, player), snapshot);
            }
        }
        // Otherwise freeze the current shot: a failed re-search is not a reason to
        // abandon a framing that still works (the documented contract), and snapping to
        // the close fallback on every flaky search caused violent vista/close oscillation.
    }

    private static void applyShot(ShotCandidate candidate, ClientSlideFeedbackController.Frame snapshot) {
        shot = new Shot(
                candidate.position(),
                candidate.aim(),
                candidate.stage(),
                candidate.travel(),
                candidate.context(),
                candidate.shotClass(),
                candidate.side(),
                candidate.shotClass().minCut(),
                candidate.shotClass().maxCut(),
                0.0D,
                SHOT_RANDOM.nextDouble() * 100.0D);
        cooldownSeconds = 0.0D;
        losFailStreak = 0;
    }

    private enum ShotClass {
        VISTA(20.0D, new double[] { 150.0D, -150.0D, 120.0D, -120.0D }, new double[] { 12.0D, 16.0D, 24.0D, 32.0D }, new double[] { 3.0D, 6.0D, 10.0D }, 18.0D, 5.0D, 55.0D, 36.0D, 0.035D),
        MEDIUM(12.0D, new double[] { 150.0D, -150.0D, 120.0D, -120.0D }, new double[] { 8.0D, 11.0D, 14.0D }, new double[] { 2.0D, 4.0D }, 10.0D, 4.0D, 32.0D, 18.0D, 0.06D),
        INTERIOR(5.0D, new double[] { 170.0D, -170.0D, 10.0D, -10.0D, 45.0D, -45.0D }, new double[] { 4.0D, 7.0D, 10.0D }, new double[] { 1.5D, 2.5D }, 7.0D, 2.0D, 16.0D, 12.0D, 0.08D),
        NONE(0.0D, new double[0], new double[0], new double[0], 0.0D, 0.0D, 0.0D, 0.0D, 1.0D);

        private final double stageLead;
        private final double[] angleDegrees;
        private final double[] distances;
        private final double[] heights;
        private final double preferredDistance;
        private final double minCut;
        private final double maxCut;
        private final double subjectMaxDistance;
        private final double proportionMin;

        ShotClass(double stageLead, double[] angleDegrees, double[] distances, double[] heights, double preferredDistance, double minCut, double maxCut, double subjectMaxDistance, double proportionMin) {
            this.stageLead = stageLead;
            this.angleDegrees = angleDegrees;
            this.distances = distances;
            this.heights = heights;
            this.preferredDistance = preferredDistance;
            this.minCut = minCut;
            this.maxCut = maxCut;
            this.subjectMaxDistance = subjectMaxDistance;
            this.proportionMin = proportionMin;
        }

        double stageLead() {
            return this.stageLead;
        }

        double[] angleDegrees() {
            return this.angleDegrees;
        }

        double[] distances() {
            return this.distances;
        }

        double[] heights() {
            return this.heights;
        }

        double preferredDistance() {
            return this.preferredDistance;
        }

        double minCut() {
            return this.minCut;
        }

        double maxCut() {
            return this.maxCut;
        }

        double subjectMaxDistance() {
            return this.subjectMaxDistance;
        }

        double proportionMin() {
            return this.proportionMin;
        }
    }

    private record ShotCandidate(Vec3 position, Vec3 aim, Vec3 stage, Vec3 travel, ShotContext context, ShotClass shotClass, boolean side, double distance, double score) {
        ShotCandidate withScore(double score) {
            return new ShotCandidate(this.position, this.aim, this.stage, this.travel, this.context, this.shotClass, this.side, this.distance, score);
        }
    }

    private static ShotCandidate searchShot(Level level, LocalPlayer player, ClientSlideFeedbackController.Frame snapshot, ShotClass shotClass, ShotContext context, Shot previous) {
        if (shotClass == ShotClass.NONE) {
            return null;
        }
        Vec3 rider = snapshot.position();
        Vec3 travel = safeNormalize(snapshot.tangent());
        Vec3 stage = stageFor(context, shotClass, snapshot);
        Vec3 aim = aimFor(stage, travel, shotClass.preferredDistance());
        List<ShotCandidate> candidates = new ArrayList<>();
        // Corridor grammar: in cluttered terrain the only reliably clear sight-lines run
        // along the rider's own upcoming path, so candidates switch to along-path
        // vantages and must hold LOS to every path sample in the budget.
        boolean corridor = corridorMode;
        List<Vec3> pathSamples = corridor ? futurePathSamples(snapshot, PATH_SAMPLE_COUNT) : List.of();
        int corridorSamples = corridor ? corridorSampleBudget(level, rider, travel, pathSamples, player) : 0;
        double[] angles = corridor ? CORRIDOR_ANGLES : candidateAngles(shotClass);
        double[] distances = corridor ? CORRIDOR_DISTANCES : candidateDistances(shotClass);
        double[] heights = corridor ? CORRIDOR_HEIGHTS : candidateHeights(shotClass);
        int losRejects = 0;
        int passed = 0;
        for (double angleDegrees : angles) {
            Vec3 direction = directionFor(context, travel, angleDegrees);
            for (double distance : distances) {
                for (double height : heights) {
                    // Candidates are placed around the RIDER and frame the stage ahead:
                    // the rider enters the shot at good size and crosses toward the
                    // stage. Placing them around the stage instead left the rider
                    // permanently outside every subject-distance check.
                    Vec3 position = rider.add(direction.scale(distance)).add(0.0D, height, 0.0D);
                    double riderDistance = position.distanceTo(rider);
                    if (riderDistance > shotClass.subjectMaxDistance()) {
                        continue;
                    }
                    double proportion = RIDER_SCREEN_HEIGHT / Math.max(1.0D, riderDistance);
                    if (proportion < shotClass.proportionMin() || proportion > MAX_RIDER_PROPORTION) {
                        continue;
                    }
                    if (!hasClearance(level, position)) {
                        continue;
                    }
                    if (!hasLineOfSight(level, position, aim, player) || !hasLineOfSight(level, position, rider.add(0.0D, 0.9D, 0.0D), player)) {
                        losRejects++;
                        continue;
                    }
                    if (corridorSamples > 0 && !hasCorridorClearance(level, position, pathSamples, corridorSamples, player)) {
                        continue;
                    }
                    if (!hasViewDepth(level, position, aim, shotClass, player)) {
                        continue;
                    }
                    double frameAngle = frameAngleDegrees(position, aim, rider);
                    if (frameAngle > RIDER_MAX_FRAME_ANGLE) {
                        continue;
                    }
                    boolean side = angleDegrees < 0.0D;
                    candidates.add(new ShotCandidate(position, aim, stage, travel, context, shotClass, side, riderDistance, frameAngle));
                    passed++;
                }
            }
        }
        if (!corridor) {
            int beforeEdge = candidates.size();
            injectEdgeCandidates(level, player, rider, travel, stage, aim, shotClass, candidates);
            passed += candidates.size() - beforeEdge;
        }
        // Durability: vantages that will lose the rider half a second from now churn a
        // cut every couple of seconds in occluded terrain (the downhill cut machine-gun),
        // so they are heavily penalized and only win when nothing durable exists. The
        // corridor grammar already enforces this by construction, so skip it there.
        Vec3 futureRider = corridor ? null : rider.add(travel.scale(Math.max(0.0D, snapshot.speed()) * LOS_FUTURE_PROBE_TICKS)).add(0.0D, 0.9D, 0.0D);
        for (int i = 0; i < candidates.size(); i++) {
            ShotCandidate candidate = candidates.get(i);
            // Scenery-first scoring: how good the shot LOOKS, not how open the spot is.
            double layerScore = depthLayeringScore(level, candidate.position(), aim, player);
            double lineScore = leadingLineScore(level, candidate.position(), aim, rider, player);
            double thirdsScore = thirdsPlacementScore(candidate.position(), aim, rider);
            double skyScore = skyRatioScore(level, candidate.position(), aim, player);
            double distanceScore = 1.0D - Math.abs(candidate.distance() - shotClass.preferredDistance()) / Math.max(1.0D, shotClass.preferredDistance());
            double proportionScore = 1.0D - Math.abs(RIDER_SCREEN_HEIGHT / candidate.distance() - RIDER_SCREEN_HEIGHT / shotClass.preferredDistance()) * shotClass.preferredDistance() / RIDER_SCREEN_HEIGHT;
            double sideScore = previous == null || candidate.side() == previous.side() ? 0.2D : -0.2D;
            double classBonus = previous != null && candidate.shotClass() != previous.shotClass() ? 0.3D : 0.0D;
            double durabilityScore = futureRider == null ? 0.0D : hasLineOfSight(level, candidate.position(), futureRider, player) ? 0.6D : -1.2D;
            candidates.set(i, candidate.withScore(layerScore * 1.2D + lineScore * 0.8D + thirdsScore * 0.6D + skyScore * 0.5D + distanceScore + proportionScore + sideScore + classBonus + durabilityScore));
        }
        candidates.sort((a, b) -> Double.compare(b.score(), a.score()));
        ShotCandidate best = null;
        if (!candidates.isEmpty()) {
            // Pick from the top few instead of always the single best, so the same
            // stretch of pipe does not produce the identical angle on every pass.
            int pickRange = Math.min(TOP_PICK_RANGE, candidates.size());
            if (previous != null) {
                // Continuity: a forced re-search should land on essentially the same
                // framing, so prefer the top pick nearest the current camera instead of
                // jumping to a fresh random angle on every cut.
                double nearest = Double.MAX_VALUE;
                for (int i = 0; i < pickRange; i++) {
                    ShotCandidate candidate = candidates.get(i);
                    double distance = candidate.position().distanceToSqr(previous.position());
                    if (distance < nearest) {
                        nearest = distance;
                        best = candidate;
                    }
                }
            } else {
                best = candidates.get(SHOT_RANDOM.nextInt(pickRange));
            }
        }
        // Clutter arbitration: the share of candidates dying on the LOS filter feeds an
        // EMA that switches the next search between the open-terrain ring and the
        // corridor grammar (hysteresis keeps borderline woods from thrashing).
        int losTotal = losRejects + passed;
        if (losTotal > 4) {
            clutterEma += ((double) losRejects / losTotal - clutterEma) * 0.3D;
        }
        corridorMode = clutterEma >= CLUTTER_ENTER_RATIO || (corridorMode && clutterEma >= CLUTTER_EXIT_RATIO);
        return best != null && best.score() > MIN_SCORE ? best : null;
    }

    /**
     * Positions the rider will occupy over the next few seconds, walked along the
     * previewed connections (tangent extrapolation past the known path). Corridor
     * vantages must hold line of sight to all of them, which is what keeps a static
     * shot motivated for the whole approach-and-pass in cluttered terrain.
     */
    private static List<Vec3> futurePathSamples(ClientSlideFeedbackController.Frame snapshot, int count) {
        List<Vec3> samples = new ArrayList<>(count);
        double speedBps = Math.max(2.0D, Math.abs(snapshot.speed()) * 20.0D);
        List<ClientSlideController.SlidePreviewConnection> previews = ClientSlideController.slidePreviewConnections(4);
        if (previews.isEmpty()) {
            Vec3 travel = safeNormalize(snapshot.tangent());
            for (int i = 1; i <= count; i++) {
                samples.add(snapshot.position().add(travel.scale(speedBps * 0.5D * i)));
            }
            return samples;
        }
        int index = 0;
        ClientSlideController.SlidePreviewConnection current = previews.get(0);
        int direction = current.direction();
        double distance = current.startDistance();
        for (int i = 1; i <= count; i++) {
            double step = speedBps * 0.5D;
            while (step > 1.0E-6D) {
                double length = current.connection().length();
                double available = direction >= 0 ? length - distance : distance;
                if (step < available) {
                    distance += direction >= 0 ? step : -step;
                    step = 0.0D;
                } else {
                    step -= available;
                    if (index + 1 < previews.size()) {
                        current = previews.get(++index);
                        direction = current.direction();
                        distance = current.startDistance();
                    } else {
                        // Ran out of known path: extrapolate the remaining samples
                        // along the exit tangent of the last previewed connection.
                        Vec3 exitTangent = current.connection().tangentAt(direction >= 0 ? length : 0.0D).scale(direction).normalize();
                        Vec3 exitPoint = current.connection().positionAt(direction >= 0 ? length : 0.0D);
                        for (int j = i; j <= count; j++) {
                            samples.add(exitPoint.add(exitTangent.scale(step + speedBps * 0.5D * (j - i))));
                        }
                        return samples;
                    }
                }
            }
            samples.add(current.connection().positionAt(Mth.clamp(distance, 0.0D, current.connection().length())));
        }
        return samples;
    }

    /**
     * How many leading path samples a corridor candidate must see, degraded gracefully:
     * if no rough along-path probe can satisfy the full corridor, the budget shrinks
     * until something passes (zero falls back to ordinary LOS checks only).
     */
    private static int corridorSampleBudget(Level level, Vec3 rider, Vec3 travel, List<Vec3> pathSamples, LocalPlayer player) {
        for (int budget = pathSamples.size(); budget > 0; budget -= 2) {
            if (axisProbePasses(level, rider, travel, pathSamples, budget, player)) {
                return budget;
            }
        }
        return 0;
    }

    private static boolean axisProbePasses(Level level, Vec3 rider, Vec3 travel, List<Vec3> pathSamples, int budget, LocalPlayer player) {
        for (double angle : new double[] { 0.0D, 15.0D, -15.0D }) {
            Vec3 direction = directionFor(context, travel, angle);
            for (double distance : CORRIDOR_DISTANCES) {
                Vec3 position = rider.add(direction.scale(distance)).add(0.0D, 2.0D, 0.0D);
                if (hasClearance(level, position) && hasCorridorClearance(level, position, pathSamples, budget, player)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasCorridorClearance(Level level, Vec3 position, List<Vec3> pathSamples, int budget, LocalPlayer player) {
        for (int i = 0; i < Math.min(budget, pathSamples.size()); i++) {
            if (!hasLineOfSight(level, position, pathSamples.get(i).add(0.0D, 0.9D, 0.0D), player)) {
                return false;
            }
        }
        return true;
    }

    private static double[] candidateAngles(ShotClass shotClass) {
        return switch (sceneShape) {
            case CORRIDOR -> new double[] { 0.0D, 25.0D, -25.0D, 155.0D, -155.0D };
            case WELL -> new double[] { 30.0D, -30.0D, 120.0D, -120.0D };
            case BACKDROP -> new double[] { 80.0D, -80.0D, 120.0D, -120.0D, 60.0D, -60.0D };
            default -> shotClass.angleDegrees();
        };
    }

    private static double[] candidateDistances(ShotClass shotClass) {
        if (sceneShape == ClientSceneProbe.SceneShape.CORRIDOR && sceneFeatures != null && sceneFeatures.axisLength() > 0.0D) {
            double near = clamp(sceneFeatures.axisLength() * 0.45D, 4.0D, 24.0D);
            double far = clamp(sceneFeatures.axisLength() * 0.7D, near + 2.0D, 26.0D);
            return new double[] { near, far };
        }
        return shotClass.distances();
    }

    private static double[] candidateHeights(ShotClass shotClass) {
        if (sceneShape == ClientSceneProbe.SceneShape.WELL) {
            return new double[] { -1.0D, -0.2D, 0.5D };
        }
        if (sceneShape == ClientSceneProbe.SceneShape.BACKDROP) {
            return new double[] { 1.5D, 4.0D, 8.0D };
        }
        if (sceneShape == ClientSceneProbe.SceneShape.CORRIDOR && sceneFeatures != null && sceneFeatures.skyOpenness() >= 0.7D) {
            return new double[] { 2.0D, 5.0D, 10.0D };
        }
        return shotClass.heights();
    }

    private static void injectEdgeCandidates(Level level, LocalPlayer player, Vec3 rider, Vec3 travel, Vec3 stage, Vec3 aim, ShotClass shotClass, List<ShotCandidate> candidates) {
        if (sceneFeatures == null || (sceneShape != ClientSceneProbe.SceneShape.EDGE && sceneShape != ClientSceneProbe.SceneShape.FIELD)) {
            return;
        }
        for (ClientSceneProbe.EdgePoint edge : sceneFeatures.edgeTops()) {
            Vec3 position = edge.topPos().add(edge.outwardDir().scale(1.5D)).add(0.0D, 1.2D, 0.0D);
            double riderDistance = position.distanceTo(rider);
            if (riderDistance > shotClass.subjectMaxDistance()) {
                continue;
            }
            double proportion = RIDER_SCREEN_HEIGHT / Math.max(1.0D, riderDistance);
            if (proportion < shotClass.proportionMin() || proportion > MAX_RIDER_PROPORTION) {
                continue;
            }
            if (!hasClearance(level, position)) {
                continue;
            }
            if (!hasLineOfSight(level, position, aim, player) || !hasLineOfSight(level, position, rider.add(0.0D, 0.9D, 0.0D), player)) {
                continue;
            }
            double frameAngle = frameAngleDegrees(position, aim, rider);
            if (frameAngle > RIDER_MAX_FRAME_ANGLE) {
                continue;
            }
            candidates.add(new ShotCandidate(position, aim, stage, travel, context, shotClass, false, riderDistance, frameAngle));
        }
    }

    private static Vec3 stageFor(ShotContext context, ShotClass shotClass, ClientSlideFeedbackController.Frame snapshot) {
        return switch (context) {
            case STATION -> stationStage(snapshot).orElseGet(() -> cruiseStage(shotClass, snapshot));
            case VERTICAL -> verticalStage(snapshot);
            case CRUISE -> cruiseStage(shotClass, snapshot);
        };
    }

    private static Vec3 cruiseStage(ShotClass shotClass, ClientSlideFeedbackController.Frame snapshot) {
        double lead = clamp(snapshot.speed() * 20.0D * 4.5D, 8.0D, 45.0D);
        Vec3 travel = safeNormalize(snapshot.tangent());
        // Bend anchoring: the stage is the end of the current connection (or the end of
        // a chain of short ones) when it lies within the lead window, so the cut lands
        // exactly on the bend instead of the rider whipping out of frame at it.
        double walked = Math.max(0.0D, snapshot.connectionLength() - snapshot.distanceOnConnection());
        if (walked > 1.0D && walked <= lead * 1.3D) {
            List<ClientSlideController.SlidePreviewConnection> previews = ClientSlideController.slidePreviewConnections(4);
            Vec3 stage = null;
            for (int i = 0; i < previews.size(); i++) {
                ClientSlideController.SlidePreviewConnection preview = previews.get(i);
                PipeConnection connection = preview.connection();
                stage = connection.positionAt(preview.direction() > 0 ? connection.length() : 0.0D);
                if (connection.length() >= 10.0D || i + 1 >= previews.size()) {
                    break;
                }
                double nextLength = previews.get(i + 1).connection().length();
                if (walked + nextLength > lead * 1.3D) {
                    break;
                }
                walked += nextLength;
            }
            if (stage != null) {
                return stage;
            }
        }
        return snapshot.position().add(travel.scale(lead));
    }

    /**
     * Guaranteed last-resort framing for tight spots where every candidate dies: a
     * ladder of close over-shoulder points behind-beside or above the rider. The ladder
     * almost always finds a valid cell; as a final resort the point above the rider is
     * used unchecked, so the camera never falls back to first person while sliding.
     */
    private static ShotCandidate fallbackShot(Vec3 rider, Vec3 travel, ShotContext context, ShotClass shotClass, LocalPlayer player) {
        ShotClass cls = shotClass == ShotClass.NONE ? ShotClass.INTERIOR : shotClass;
        Vec3 stage = rider.add(travel.scale(cls.stageLead() * 0.5D));
        Vec3 aim = aimFor(stage, travel, cls.preferredDistance());
        Vec3 right = travel.cross(new Vec3(0.0D, 1.0D, 0.0D));
        right = right.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();
        double[][] ladder = {
                { -2.5D, 1.0D, 1.2D }, { -1.8D, 0.9D, 1.0D }, { -1.2D, 0.8D, 0.8D }, { 0.0D, 1.0D, 1.3D }, { 0.0D, 0.9D, 0.9D } };
        for (double[] option : ladder) {
            Vec3 position = rider.add(travel.scale(option[0])).add(right.scale(option[1])).add(0.0D, option[2], 0.0D);
            if (hasClearance(player.level(), position) && hasLineOfSight(player.level(), position, rider.add(0.0D, 0.9D, 0.0D), player)) {
                return new ShotCandidate(position, aim, stage, travel, context, cls, false, position.distanceTo(rider), MIN_SCORE + 0.01D);
            }
        }
        Vec3 lastResort = rider.add(0.0D, 1.0D, 0.0D);
        return new ShotCandidate(lastResort, aim, stage, travel, context, cls, false, 1.0D, MIN_SCORE + 0.01D);
    }

    private static Vec3 verticalStage(ClientSlideFeedbackController.Frame snapshot) {
        double remaining = Math.max(0.0D, snapshot.connectionLength() - snapshot.distanceOnConnection());
        if (remaining > 1.0D && remaining <= BEND_ANCHOR_MAX_REMAINING) {
            Optional<PipeConnection> connection = ClientPipeNetworkCache.globalConnection(snapshot.connectionId());
            if (connection.isPresent()) {
                return connection.get().positionAt(snapshot.connectionLength());
            }
        }
        return snapshot.position().add(safeNormalize(snapshot.tangent()).scale(VERTICAL_TRACK_LEAD));
    }

    private static Optional<Vec3> stationStage(ClientSlideFeedbackController.Frame snapshot) {
        Optional<PipeConnection> connection = ClientPipeNetworkCache.globalConnection(snapshot.connectionId());
        if (connection.isPresent() && connection.get().platformStopId().isPresent()) {
            return Optional.of(connection.get().positionAt(connection.get().length() * 0.5D));
        }
        return Optional.empty();
    }

    private static Vec3 aimFor(Vec3 stage, Vec3 travel, double cameraDistance) {
        // Lead room: aim a little ahead of the stage so the rider enters from the
        // trailing edge with exit space, plus a raise that keeps the rider in the lower
        // third of the frame. Too much forward push used to squeeze the rider into a
        // sliver at the frame edge, so the travel offset stays small.
        double raise = Math.max(1.0D, cameraDistance * AIM_RAISE_FRACTION);
        return stage.add(travel.scale(raise * 0.6D)).add(0.0D, raise, 0.0D);
    }

    private static Vec3 shotAimFor(Vec3 position, Vec3 stage) {
        return stage.add(0.0D, Math.max(1.0D, position.distanceTo(stage) * AIM_RAISE_FRACTION), 0.0D);
    }

    private static Vec3 directionFor(ShotContext context, Vec3 travel, double angleDegrees) {
        if (sceneShape == ClientSceneProbe.SceneShape.CORRIDOR && sceneFeatures != null && sceneFeatures.axisDirection() != null) {
            return rotateHorizontal(sceneFeatures.axisDirection(), Math.toRadians(angleDegrees));
        }
        Vec3 horizontal = new Vec3(travel.x, 0.0D, travel.z);
        Vec3 base = horizontal.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : horizontal.normalize();
        return rotateHorizontal(base, Math.toRadians(angleDegrees));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void writeCamera(Minecraft minecraft, LocalPlayer player, Camera camera, CameraAccessor access, float partialTick, double intensity) {
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 target = eye.lerp(shot.position(), blend);
        Vec3 position = clearanceFallback(minecraft.level, eye, target);
        if (position == null) {
            position = lastSafePosition != null ? lastSafePosition : eye;
        } else {
            lastSafePosition = position;
        }
        // Slow sinusoidal wander (per-shot random phase): the "handheld" breathing that
        // keeps a parked shot alive. Amplitude scales with shot distance so it stays
        // visible on wide shots. The amount eases toward the clearance state so the
        // drift never snaps on or off.
        double driftScale = Math.max(2.0D, shot.position().distanceTo(player.position())) * 0.025D;
        double time = (System.currentTimeMillis() % 200000L) / 1000.0D;
        Vec3 drift = new Vec3(
                Math.sin(time * 0.55D + shot.driftPhase()) * driftScale,
                Math.sin(time * 0.42D + shot.driftPhase() * 1.7D) * driftScale * 0.45D,
                Math.sin(time * 0.61D + shot.driftPhase() * 2.3D) * driftScale);
        // Hysteresis on the drift clearance: near terrain the drift tip flaps in and out
        // of blocks, and chasing that boolean made the parked camera saw in and out of
        // its wander (visible tremble on downhill sections). Blocked stops the drift at
        // once; it only eases back after the tip has stayed clear for a moment.
        double dt = deltaSeconds(minecraft);
        boolean driftClear = hasClearance(minecraft.level, position.add(drift));
        if (!driftClear) {
            driftBlockedSeconds = DRIFT_RECOVER_SECONDS;
        } else if (driftBlockedSeconds > 0.0D) {
            driftBlockedSeconds -= dt;
        }
        double driftTarget = driftClear && driftBlockedSeconds <= 0.0D ? 1.0D : 0.0D;
        driftAmount = approachExp(driftAmount, driftTarget, driftTarget < driftAmount ? 6.0D : 1.5D, dt);
        position = position.add(drift.scale(driftAmount));
        Vec3 look = shot.aim().subtract(position);
        double targetYaw = Math.toDegrees(Math.atan2(-look.x, look.z));
        double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        double targetPitch = Math.toDegrees(Math.atan2(-look.y, horizontal));
        // Steady state hands rotation fully to the shot (mouse input is decoupled, which
        // is what made curves tremble); only the first/last stretch of the blend mixes
        // with the player's own view for a smooth handoff in both directions.
        double rotationBlend = Mth.clamp((blend - 0.15D) / 0.35D, 0.0D, 1.0D);
        float yaw = player.getViewYRot(partialTick);
        float pitch = player.getViewXRot(partialTick);
        double blendedYaw = yaw + Mth.wrapDegrees(targetYaw - yaw) * rotationBlend;
        double blendedPitch = pitch + (targetPitch - pitch) * rotationBlend;
        access.superpipeslide$invokeSetPosition(position);
        access.superpipeslide$invokeSetRotation((float) blendedYaw, (float) blendedPitch, camera.getRoll());
    }

    /**
     * Walks the eye -> target segment with hysteresis: a clear target becomes the new
     * safe point; a blocked target keeps the previous safe point; the bisection search
     * only runs when the safe point itself is invalid. This stops the per-frame
     * pass/fail oscillation at block boundaries (the in-wall tremor).
     */
    private static Vec3 clearanceFallback(Level level, Vec3 eye, Vec3 target) {
        if (hasClearance(level, target)) {
            lastSafePosition = target;
            return target;
        }
        if (lastSafePosition != null && hasClearance(level, lastSafePosition)) {
            return lastSafePosition;
        }
        Vec3 low = eye;
        Vec3 high = target;
        for (int i = 0; i < 5; i++) {
            Vec3 mid = low.lerp(high, 0.5D);
            if (hasClearance(level, mid)) {
                low = mid;
            } else {
                high = mid;
            }
        }
        if (hasClearance(level, low)) {
            lastSafePosition = low;
            return low;
        }
        return null;
    }

    private static double freeDistance(Level level, Vec3 from, Vec3 direction, double maxDistance, LocalPlayer player) {
        Vec3 to = from.add(direction.normalize().scale(maxDistance));
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? maxDistance : from.distanceTo(hit.getLocation());
    }

    private static boolean hasLineOfSight(Level level, Vec3 from, Vec3 to, LocalPlayer player) {
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    /**
     * Rejects points that would fill the frame with a single surface (the solid-color
     * failure): the view direction must stay clear for 1.5 blocks, and enough of the
     * six cardinal directions must stay clear for 1.2 blocks. Interior shots tolerate
     * more blocked sides (stations have roofs and walls by nature).
     */
    private static boolean hasViewDepth(Level level, Vec3 position, Vec3 aim, ShotClass shotClass, LocalPlayer player) {
        Vec3 viewDirection = aim.subtract(position);
        if (viewDirection.lengthSqr() < 1.0E-6D) {
            return false;
        }
        if (freeDistance(level, position, viewDirection.normalize(), 1.5D, player) < 1.5D) {
            return false;
        }
        Vec3[] cardinals = {
                new Vec3(1.0D, 0.0D, 0.0D), new Vec3(-1.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 1.0D, 0.0D), new Vec3(0.0D, -1.0D, 0.0D),
                new Vec3(0.0D, 0.0D, 1.0D), new Vec3(0.0D, 0.0D, -1.0D) };
        int clear = 0;
        for (Vec3 cardinal : cardinals) {
            if (freeDistance(level, position, cardinal, 1.2D, player) >= 1.2D) {
                clear++;
            }
        }
        return clear >= (shotClass == ShotClass.INTERIOR ? 3 : 4);
    }

    /**
     * Upgraded clearance: the 0.6m box must not intersect solid collision, fluids, or
     * any non-air non-collidable block (vegetation, snow layers), and must not sit
     * inside a pipe body. The pipe test projects onto the actual connection line:
     * connection bounding boxes cover whole station areas and would otherwise reject
     * every valid vantage point near pipes.
     */
    private static boolean hasClearance(Level level, Vec3 position) {
        AABB box = new AABB(position.x - 0.3D, position.y - 0.3D, position.z - 0.3D, position.x + 0.3D, position.y + 0.3D, position.z + 0.3D);
        if (!level.noCollision(box)) {
            return false;
        }
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.getCollisionShape(level, pos).isEmpty()) {
                return false;
            }
            if (!level.getFluidState(pos).isEmpty()) {
                return false;
            }
        }
        for (PipeConnection connection : ClientPipeNetworkCache.connectionsNear(level.dimension(), position, 3.0D)) {
            if (dev.marblegate.superpipeslide.common.core.geometry.SlideGeometry.project(connection, position).distance() < 0.7D) {
                return false;
            }
        }
        return true;
    }

    private static Vec3 rotateHorizontal(Vec3 forward, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(forward.x * cos - forward.z * sin, 0.0D, forward.x * sin + forward.z * cos);
    }

    /**
     * Projects a world point into the candidate camera frame (horizontal ~51 deg,
     * vertical ~35 deg half angles). Returns [u, v, forward] or null when behind.
     */
    private static double[] projectToFrame(Vec3 position, Vec3 aim, Vec3 point) {
        Vec3 view = aim.subtract(position);
        if (view.lengthSqr() < 1.0E-6D) {
            return null;
        }
        view = view.normalize();
        Vec3 right = view.cross(new Vec3(0.0D, 1.0D, 0.0D));
        right = right.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();
        Vec3 up = right.cross(view).normalize();
        Vec3 relative = point.subtract(position);
        double forward = relative.dot(view);
        if (forward < 0.1D) {
            return null;
        }
        double tanH = Math.tan(Math.toRadians(51.0D));
        double tanV = Math.tan(Math.toRadians(35.0D));
        return new double[] { relative.dot(right) / (forward * tanH), relative.dot(up) / (forward * tanV), forward };
    }

    /**
     * Depth layering: 15 rays in a small grid around the view direction, bucketed into
     * near / mid / far. Rich near+far mixes score high; a single empty layer scores low.
     */
    private static double depthLayeringScore(Level level, Vec3 position, Vec3 aim, LocalPlayer player) {
        Vec3 view = aim.subtract(position);
        if (view.lengthSqr() < 1.0E-6D) {
            return 0.0D;
        }
        view = view.normalize();
        Vec3 right = view.cross(new Vec3(0.0D, 1.0D, 0.0D));
        right = right.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();
        Vec3 up = right.cross(view).normalize();
        int near = 0;
        int mid = 0;
        int far = 0;
        for (int i = -2; i <= 2; i++) {
            for (int j = -1; j <= 1; j++) {
                Vec3 direction = view.add(right.scale(i * 0.22D)).add(up.scale(j * 0.22D)).normalize();
                double distance = freeDistance(level, position, direction, 30.0D, player);
                if (distance < 8.0D) {
                    near++;
                } else if (distance < 24.0D) {
                    mid++;
                } else {
                    far++;
                }
            }
        }
        double[] bands = { near / 15.0D, mid / 15.0D, far / 15.0D };
        double entropy = 0.0D;
        for (double band : bands) {
            if (band > 1.0E-6D) {
                entropy -= band * (Math.log(band) / Math.log(2.0D));
            }
        }
        return entropy / (Math.log(3.0D) / Math.log(2.0D)) * 0.7D + (far > 0 ? 0.3D : 0.0D);
    }

    /**
     * Leading line: samples the pipe itself (a free, structured leading line) into the
     * candidate frame and rewards long visible runs pointing across the view.
     */
    private static double leadingLineScore(Level level, Vec3 position, Vec3 aim, Vec3 rider, LocalPlayer player) {
        List<PipeConnection> connections = ClientPipeNetworkCache.connectionsNear(level.dimension(), rider, 20.0D);
        int inFrame = 0;
        int total = 0;
        for (PipeConnection connection : connections) {
            double length = Math.min(connection.length(), 24.0D);
            int samples = 8;
            for (int i = 0; i <= samples; i++) {
                Vec3 point = connection.positionAt(connection.length() * i / samples);
                if (projectToFrame(position, aim, point) != null) {
                    inFrame++;
                }
                total++;
            }
        }
        return total == 0 ? 0.0D : clamp(inFrame / 8.0D, 0.0D, 1.0D);
    }

    /**
     * Thirds placement: how close the rider's projection sits to a rule-of-thirds point
     * instead of dead center or the sliver edge.
     */
    private static double thirdsPlacementScore(Vec3 position, Vec3 aim, Vec3 rider) {
        double[] projection = projectToFrame(position, aim, rider);
        if (projection == null) {
            return 0.0D;
        }
        double best = Double.MAX_VALUE;
        for (double u : new double[] { -1.0D / 3.0D, 1.0D / 3.0D }) {
            for (double v : new double[] { -1.0D / 3.0D, 1.0D / 3.0D }) {
                best = Math.min(best, Math.hypot(projection[0] - u, projection[1] - v));
            }
        }
        return clamp(1.0D - best * 1.2D, 0.0D, 1.0D);
    }

    /**
     * Sky ratio: how much of the upper frame is open sky (great for backlit/profile
     * shots), from an 8-ray fan above the view axis.
     */
    private static double skyRatioScore(Level level, Vec3 position, Vec3 aim, LocalPlayer player) {
        Vec3 view = aim.subtract(position);
        if (view.lengthSqr() < 1.0E-6D) {
            return 0.0D;
        }
        view = view.normalize();
        Vec3 right = view.cross(new Vec3(0.0D, 1.0D, 0.0D));
        right = right.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();
        Vec3 up = right.cross(view).normalize();
        int clear = 0;
        for (int i = 0; i < 8; i++) {
            double spread = (i - 3.5D) * 0.18D;
            Vec3 direction = view.add(right.scale(spread)).add(up.scale(0.45D)).normalize();
            if (freeDistance(level, position, direction, 24.0D, player) >= 23.5D) {
                clear++;
            }
        }
        return clear / 8.0D;
    }

    private static double frameAngleDegrees(Vec3 position, Vec3 aim, Vec3 subject) {
        Vec3 viewDirection = aim.subtract(position);
        Vec3 subjectDirection = subject.subtract(position);
        if (viewDirection.lengthSqr() < 1.0E-6D || subjectDirection.lengthSqr() < 1.0E-6D) {
            return 180.0D;
        }
        return Math.toDegrees(Math.acos(Mth.clamp(viewDirection.normalize().dot(subjectDirection.normalize()), -1.0D, 1.0D)));
    }

    private static Vec3 safeNormalize(Vec3 vector) {
        return vector.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : vector.normalize();
    }

    private static double deltaSeconds(Minecraft minecraft) {
        return minecraft.getDeltaTracker() == null ? 0.05D : Math.min(0.25D, minecraft.getDeltaTracker().getRealtimeDeltaTicks() * 0.05D);
    }

    private static double approachExp(double current, double target, double ratePerSecond, double deltaSeconds) {
        return current + (target - current) * (1.0D - Math.exp(-ratePerSecond * deltaSeconds));
    }
}
