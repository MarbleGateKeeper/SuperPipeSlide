package dev.marblegate.superpipeslide.client.core.slide;


import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeAppearanceCache;
import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.common.core.appearance.model.PipeAppearanceProfile;
import dev.marblegate.superpipeslide.common.core.appearance.storage.PipeAppearanceDefinitions;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeStyleDefinition;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeStyleGeometry;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeStyleShape;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeVariantDefinition;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class ClientSlidePoseController {
    private static final int STEP_DISMOUNT_TICKS = 14;
    private static final int FLIP_DISMOUNT_TICKS = 20;
    private static final double MOUNT_TICKS = 9.0D;
    private static final Map<String, RidePoseDescriptor> DESCRIPTOR_CACHE = new LinkedHashMap<>();
    private static final Map<Integer, PoseSmoothingState> SMOOTHED_POSES = new LinkedHashMap<>();

    @Nullable
    private static LocalDismount localDismount;

    private ClientSlidePoseController() {
    }

    public static void tick(Minecraft minecraft, LocalPlayer player) {
        if (localDismount != null) {
            if (ClientSlideController.isSliding()) {
                localDismount = null;
            } else {
                localDismount = localDismount.tick();
                if (localDismount.finished()) {
                    localDismount = null;
                }
            }
        }
        if (DESCRIPTOR_CACHE.size() > 96) {
            DESCRIPTOR_CACHE.clear();
        }
        if (minecraft.level != null && SMOOTHED_POSES.size() > 24) {
            long gameTime = minecraft.level.getGameTime();
            SMOOTHED_POSES.entrySet().removeIf(entry -> gameTime - entry.getValue().lastSeenTick > 40L);
        }
    }

    public static void beginDismount(DismountKind kind) {
        Optional<ClientSlideFeedbackController.Frame> frame = ClientSlideFeedbackController.currentFrame();
        if (frame.isEmpty()) {
            localDismount = null;
            return;
        }
        int duration = kind == DismountKind.FLIP ? FLIP_DISMOUNT_TICKS : STEP_DISMOUNT_TICKS;
        localDismount = new LocalDismount(kind, frame.get(), descriptorFor(frame.get()), 0, duration);
    }

    public static void cancelLocalPose() {
        localDismount = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            SMOOTHED_POSES.remove(minecraft.player.getId());
        }
    }

    public static void clear() {
        localDismount = null;
        DESCRIPTOR_CACHE.clear();
        SMOOTHED_POSES.clear();
    }

    public static Optional<PoseSnapshot> poseForPlayer(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return Optional.empty();
        }
        Optional<PoseSnapshot> raw = rawPoseForPlayer(minecraft, entityId);
        if (raw.isEmpty()) {
            SMOOTHED_POSES.remove(entityId);
            return Optional.empty();
        }
        long gameTime = minecraft.level.getGameTime();
        float partialTick = Mth.clamp(minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false), 0.0F, 1.0F);
        return Optional.of(SMOOTHED_POSES.computeIfAbsent(entityId, ignored -> new PoseSmoothingState()).sample(raw.get(), gameTime, partialTick));
    }

    private static Optional<PoseSnapshot> rawPoseForPlayer(Minecraft minecraft, int entityId) {
        if (minecraft.player.getId() == entityId) {
            if (ClientSlideController.isSliding()) {
                return ClientSlideFeedbackController.currentFrame().map(frame -> activePose(frame, true));
            }
            if (localDismount != null) {
                return Optional.of(localDismount.pose());
            }
            return Optional.empty();
        }

        return ClientSlideFeedbackController.frameForPlayer(entityId).map(frame -> activePose(frame, false));
    }

    private static PoseSnapshot activePose(ClientSlideFeedbackController.Frame frame, boolean local) {
        double mount = smoothstep(0.0D, MOUNT_TICKS, frame.ticksSliding());
        return new PoseSnapshot(
                frame,
                descriptorFor(frame),
                local,
                true,
                DismountKind.NONE,
                mount,
                0.0D,
                Mth.clamp(frame.alpha(), 0.0D, 1.0D)
        );
    }

    private static RidePoseDescriptor descriptorFor(ClientSlideFeedbackController.Frame frame) {
        Optional<PipeConnection> connection = ClientPipeNetworkCache.globalConnection(frame.connectionId());
        PipeAppearanceProfile profile = connection
                .map(PipeConnection::connectionKey)
                .map(ClientPipeAppearanceCache::profileFor)
                .orElseGet(PipeAppearanceProfile::defaultProfile)
                .normalizedToDefinitions();
        String key = profile.contentKey();
        return DESCRIPTOR_CACHE.computeIfAbsent(key, ignored -> buildDescriptor(profile));
    }

    private static RidePoseDescriptor buildDescriptor(PipeAppearanceProfile profile) {
        PipeStyleDefinition style = PipeAppearanceDefinitions.style(profile.styleId()).orElse(PipeAppearanceDefinitions.defaultStyle());
        PipeVariantDefinition variant = PipeAppearanceDefinitions.variant(profile.variantId())
                .filter(candidate -> candidate.styleId().equals(style.id()))
                .orElseGet(() -> PipeAppearanceDefinitions.variant(style.defaultVariantId()).orElse(PipeAppearanceDefinitions.defaultVariant()));
        PipeStyleGeometry geometry = PipeStyleGeometry.resolve(style, variant, profile.styleParameters());
        return switch (geometry.shape()) {
            case ROUND -> new RidePoseDescriptor(
                    RidePoseFamily.INLINE,
                    geometry.shape(),
                    clamp(geometry.radius() * 0.62D, 0.10D, 0.24D),
                    clamp(0.28D + geometry.radius() * 0.18D, 0.28D, 0.38D),
                    -0.010D,
                    0.30D,
                    0.48D,
                    1.12D,
                    1.05D
            );
            case FACETED -> new RidePoseDescriptor(
                    RidePoseFamily.INLINE,
                    geometry.shape(),
                    clamp(geometry.radius() * 0.68D, 0.11D, 0.26D),
                    clamp(0.29D + geometry.radius() * 0.16D, 0.28D, 0.38D),
                    -0.006D,
                    0.28D,
                    0.44D,
                    1.08D,
                    1.00D
            );
            case BOX, TRIANGLE -> new RidePoseDescriptor(
                    RidePoseFamily.INLINE,
                    geometry.shape(),
                    clamp(geometry.halfWidth() * 0.52D, 0.12D, 0.34D),
                    clamp(0.27D + geometry.halfWidth() * 0.10D, 0.26D, 0.39D),
                    0.000D,
                    0.22D,
                    0.38D,
                    0.96D,
                    0.88D
            );
            case RAIL -> new RidePoseDescriptor(
                    RidePoseFamily.SPLIT_RAIL,
                    geometry.shape(),
                    clamp(geometry.gauge(), 0.26D, 1.04D),
                    clamp(0.08D + geometry.railWidth() * 0.80D, 0.08D, 0.22D),
                    0.000D,
                    0.24D,
                    0.58D,
                    0.92D,
                    0.86D
            );
            case SLIDE -> new RidePoseDescriptor(
                    RidePoseFamily.CRADLE,
                    geometry.shape(),
                    clamp(geometry.halfWidth() * geometry.floorRatio() * 0.62D, 0.18D, 0.48D),
                    clamp(0.18D + geometry.depth() * 0.18D, 0.18D, 0.34D),
                    -0.004D,
                    0.20D,
                    0.34D,
                    0.78D,
                    0.70D
            );
            case MONORAIL -> new RidePoseDescriptor(
                    RidePoseFamily.MONORAIL,
                    geometry.shape(),
                    clamp(geometry.halfWidth() * 0.42D, 0.08D, 0.22D),
                    clamp(0.30D + geometry.halfWidth() * 0.08D, 0.28D, 0.40D),
                    -0.006D,
                    0.34D,
                    0.56D,
                    1.20D,
                    1.12D
            );
            case COVERED -> new RidePoseDescriptor(
                    RidePoseFamily.CRADLE,
                    geometry.shape(),
                    clamp(geometry.halfWidth() * 0.44D, 0.24D, 0.54D),
                    clamp(0.19D + geometry.halfWidth() * 0.06D, 0.18D, 0.32D),
                    0.004D,
                    0.18D,
                    0.30D,
                    0.70D,
                    0.64D
            );
        };
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static PoseSnapshot interpolate(PoseSnapshot previous, PoseSnapshot current, double partialTick) {
        double t = Mth.clamp(partialTick, 0.0D, 1.0D);
        if (t <= 0.0D || previous == current) {
            return previous;
        }
        if (t >= 1.0D) {
            return current;
        }
        ClientSlideFeedbackController.Frame previousFrame = previous.frame();
        ClientSlideFeedbackController.Frame currentFrame = current.frame();
        ClientSlideFeedbackController.Frame frame = new ClientSlideFeedbackController.Frame(
                currentFrame.active(),
                lerp(previousFrame.alpha(), currentFrame.alpha(), t),
                currentFrame.sessionId(),
                currentFrame.connectionId(),
                lerp(previousFrame.position(), currentFrame.position(), t),
                lerpDirection(previousFrame.tangent(), currentFrame.tangent(), t),
                lerpDirection(previousFrame.visualFacing(), currentFrame.visualFacing(), t),
                lerp(previousFrame.speed(), currentFrame.speed(), t),
                lerp(previousFrame.speed01(), currentFrame.speed01(), t),
                lerp(previousFrame.perceptualSpeed(), currentFrame.perceptualSpeed(), t),
                lerp(previousFrame.accelerationBlend(), currentFrame.accelerationBlend(), t),
                lerp(previousFrame.highwayBlend(), currentFrame.highwayBlend(), t),
                lerp(previousFrame.platformBlend(), currentFrame.platformBlend(), t),
                lerp(previousFrame.verticalBlend(), currentFrame.verticalBlend(), t),
                lerp(previousFrame.upBlend(), currentFrame.upBlend(), t),
                lerp(previousFrame.downBlend(), currentFrame.downBlend(), t),
                lerp(previousFrame.turnBlend(), currentFrame.turnBlend(), t),
                lerp(previousFrame.signedTurn(), currentFrame.signedTurn(), t),
                lerp(previousFrame.turnPreviewBlend(), currentFrame.turnPreviewBlend(), t),
                lerp(previousFrame.signedTurnPreview(), currentFrame.signedTurnPreview(), t),
                lerp(previousFrame.accelerationPulse(), currentFrame.accelerationPulse(), t),
                lerpUnit(previousFrame.motionPhase(), currentFrame.motionPhase(), t),
                lerp(previousFrame.fovBoost(), currentFrame.fovBoost(), t),
                lerp(previousFrame.edgeIntensity(), currentFrame.edgeIntensity(), t),
                currentFrame.ticksSliding()
        );
        return new PoseSnapshot(
                frame,
                current.ride(),
                current.local(),
                current.sliding(),
                current.dismountKind(),
                lerp(previous.mountProgress(), current.mountProgress(), t),
                lerp(previous.dismountProgress(), current.dismountProgress(), t),
                lerp(previous.poseAlpha(), current.poseAlpha(), t)
        );
    }

    private static boolean shouldSnap(PoseSnapshot previous, PoseSnapshot current) {
        return !previous.frame().sessionId().equals(current.frame().sessionId())
                || previous.frame().position().distanceToSqr(current.frame().position()) > 16.0D
                || previous.ride().family() != current.ride().family()
                || previous.ride().shape() != current.ride().shape();
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

    private static final class PoseSmoothingState {
        @Nullable
        private PoseSnapshot previous;
        @Nullable
        private PoseSnapshot current;
        private long lastUpdateTick = Long.MIN_VALUE;
        private long lastSeenTick = Long.MIN_VALUE;

        private PoseSnapshot sample(PoseSnapshot raw, long gameTime, double partialTick) {
            this.lastSeenTick = gameTime;
            if (this.current == null || this.previous == null || shouldSnap(this.current, raw)) {
                this.previous = raw;
                this.current = raw;
                this.lastUpdateTick = gameTime;
                return raw;
            }
            if (gameTime != this.lastUpdateTick) {
                this.previous = this.current;
                this.current = raw;
                this.lastUpdateTick = gameTime;
            } else {
                this.current = raw;
            }
            return interpolate(this.previous, this.current, partialTick);
        }
    }

    private record LocalDismount(DismountKind kind, ClientSlideFeedbackController.Frame frame, RidePoseDescriptor ride, int age, int duration) {
        private LocalDismount tick() {
            return new LocalDismount(this.kind, this.frame, this.ride, this.age + 1, this.duration);
        }

        private PoseSnapshot pose() {
            double progress = Mth.clamp(this.age / (double) this.duration, 0.0D, 1.0D);
            double alpha = this.kind == DismountKind.FLIP
                    ? 1.0D - smoothstep(0.72D, 1.0D, progress)
                    : 1.0D - smoothstep(0.58D, 1.0D, progress);
            return new PoseSnapshot(this.frame, this.ride, true, false, this.kind, 1.0D, progress, alpha);
        }

        private boolean finished() {
            return this.age >= this.duration;
        }
    }

    public enum DismountKind {
        NONE,
        STEP,
        FLIP
    }

    public enum RidePoseFamily {
        INLINE,
        SPLIT_RAIL,
        CRADLE,
        MONORAIL
    }

    public record RidePoseDescriptor(
            RidePoseFamily family,
            PipeStyleShape shape,
            double stanceWidth,
            double stanceLength,
            double bodyLift,
            double kneeBend,
            double armBaseSpread,
            double turnLeanScale,
            double balanceScale
    ) {
    }

    public record PoseSnapshot(
            ClientSlideFeedbackController.Frame frame,
            RidePoseDescriptor ride,
            boolean local,
            boolean sliding,
            DismountKind dismountKind,
            double mountProgress,
            double dismountProgress,
            double poseAlpha
    ) {
        public Vec3 position() {
            return this.frame.position();
        }

        public Vec3 tangent() {
            return this.frame.tangent();
        }

        public Vec3 visualFacing() {
            return this.frame.visualFacing();
        }

        public double speed01() {
            return this.frame.speed01();
        }

        public double perceptualSpeed() {
            return this.frame.perceptualSpeed();
        }

        public double accelerationBlend() {
            return this.frame.accelerationBlend();
        }

        public double highwayBlend() {
            return this.frame.highwayBlend();
        }

        public double platformBlend() {
            return this.frame.platformBlend();
        }

        public double verticalBlend() {
            return this.frame.verticalBlend();
        }

        public double upBlend() {
            return this.frame.upBlend();
        }

        public double downBlend() {
            return this.frame.downBlend();
        }

        public double turnBlend() {
            return this.frame.turnBlend();
        }

        public double signedTurn() {
            return this.frame.signedTurn();
        }

        public double turnPreviewBlend() {
            return this.frame.turnPreviewBlend();
        }

        public double signedTurnPreview() {
            return this.frame.signedTurnPreview();
        }

        public double accelerationPulse() {
            return this.frame.accelerationPulse();
        }

        public double motionPhase() {
            return this.frame.motionPhase();
        }
    }
}
