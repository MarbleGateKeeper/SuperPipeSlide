package dev.marblegate.superpipeslide.client.renderer.slide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.marblegate.superpipeslide.client.core.slide.ClientSlideFeedbackController;
import dev.marblegate.superpipeslide.client.core.slide.ClientSlidePoseController;
import dev.marblegate.superpipeslide.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;

import java.util.Optional;

public final class ClientSlideFeedbackPlayerRenderer {
    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static boolean pushedSlidePose;
    private static int pushedSlidePosePlayerId = Integer.MIN_VALUE;
    private static boolean slideLegOffsetsDirty;

    private ClientSlideFeedbackPlayerRenderer() {
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Optional<ClientSlideFeedbackController.Frame> frame = ClientSlideFeedbackController.currentRenderFrame();
        if (frame.isEmpty()) {
            return;
        }
        event.setFOV(event.getFOV() + (float) frame.get().fovBoost());
    }

    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!ClientConfig.ENABLE_SLIDE_CAMERA_FEEDBACK.get()) {
            return;
        }
        Optional<ClientSlideFeedbackController.Frame> frame = ClientSlideFeedbackController.currentRenderFrame();
        if (frame.isEmpty()) {
            return;
        }
        ClientSlideFeedbackController.Frame feedback = frame.get();
        Vec3 viewRight = Vec3.directionFromRotation(0.0F, event.getYaw() + 90.0F);
        Vec3 tangentHorizontal = new Vec3(feedback.tangent().x, 0.0D, feedback.tangent().z);
        double horizontal = tangentHorizontal.lengthSqr() < 1.0E-8D ? 0.0D : tangentHorizontal.normalize().dot(viewRight);
        double turnSignal = turnSignal(feedback.signedTurn(), feedback.signedTurnPreview(), 0.58D);
        double steeringRoll = -turnSignal * (2.40D + feedback.perceptualSpeed() * 5.80D + feedback.highwayBlend() * 1.60D + feedback.accelerationPulse() * 1.25D);
        double directionalRoll = -horizontal * (0.40D + feedback.perceptualSpeed() * 0.85D) * (1.0D - feedback.verticalBlend() * 0.60D);
        double roll = (steeringRoll + directionalRoll) * feedback.alpha();
        double pitch = (feedback.downBlend() - feedback.upBlend()) * (1.45D + feedback.perceptualSpeed() * 2.35D + feedback.highwayBlend() * 0.45D) * feedback.alpha();
        event.setRoll(event.getRoll() + (float) roll);
        event.setPitch(event.getPitch() + (float) pitch);
    }

    public static void onRenderPlayerPre(RenderPlayerEvent.Pre<?> event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        Optional<ClientSlidePoseController.PoseSnapshot> pose = renderPose(minecraft, event.getRenderState());
        if (pose.isEmpty()) {
            return;
        }
        ClientSlidePoseController.PoseSnapshot feedback = pose.get();
        AvatarRenderState state = event.getRenderState();
        state.isSpectator = false;
        state.isPassenger = false;
        double balanceSway = Math.sin(feedback.motionPhase() * Mth.TWO_PI) * (0.10D + feedback.perceptualSpeed() * 0.10D);
        state.walkAnimationPos = (float) ((balanceSway + Mth.TWO_PI * 4.0D) / 0.6662D);
        state.walkAnimationSpeed = (float) Mth.clamp(0.18D + feedback.perceptualSpeed() * 0.20D + feedback.accelerationPulse() * 0.08D, 0.18D, 0.50D);
        // HumanoidModel divides by speedValue while posing limbs; keep it non-zero when freezing walk motion.
        state.speedValue = 1.0F;
        state.swimAmount = 0.0F;
        state.attackTime = 0.0F;
        alignBodyToFacing(state, feedback.visualFacing());

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        pushedSlidePose = true;
        pushedSlidePosePlayerId = state.id;

        Vec3 offset = visualOffset(feedback, minecraft);
        poseStack.translate(offset.x, offset.y, offset.z);

        Vec3 facing = feedback.visualFacing();
        double poseAlpha = feedback.poseAlpha();
        double verticalPitch = (feedback.downBlend() - feedback.upBlend()) * (58.0D + feedback.perceptualSpeed() * 12.0D);
        double slopeBalancePitch = slopeBalancePitch(feedback);
        double forwardLean = -(8.0D + feedback.perceptualSpeed() * 7.0D + feedback.accelerationPulse() * 4.0D)
                * (1.0D - feedback.platformBlend() * 0.45D)
                * (1.0D - feedback.verticalBlend() * 0.72D)
                * poseAlpha;
        double bank = (turnSignal(feedback.signedTurn(), feedback.signedTurnPreview(), 0.52D) * 16.0D * feedback.ride().turnLeanScale() + renderBank(feedback, minecraft) * 3.8D)
                * (0.35D + feedback.perceptualSpeed() * 0.65D)
                * (1.0D - feedback.verticalBlend() * 0.65D)
                * poseAlpha;
        rotateAround(poseStack, WORLD_UP.cross(facing), verticalPitch + slopeBalancePitch + forwardLean);
        rotateAround(poseStack, facing, bank);
        applyDismountWorldPose(poseStack, feedback, facing);
    }

    public static void onRenderPlayerPost(RenderPlayerEvent.Post<?> event) {
        if (!pushedSlidePose || event.getRenderState().id != pushedSlidePosePlayerId) {
            return;
        }
        event.getPoseStack().popPose();
        pushedSlidePose = false;
        pushedSlidePosePlayerId = Integer.MIN_VALUE;
    }

    public static void applySlidingModelPose(AvatarRenderState state, PlayerModel model) {
        if (slideLegOffsetsDirty) {
            resetSlideLegOffsets(model);
            slideLegOffsetsDirty = false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        Optional<ClientSlidePoseController.PoseSnapshot> pose = renderPose(minecraft, state);
        if (pose.isEmpty()) {
            return;
        }
        Entity entity = minecraft.level.getEntity(state.id);
        if (entity == null) {
            return;
        }
        resetSlideLegOffsets(model);
        applyNaturalSlidingModelPose(pose.get(), model);
        applyHeadLook(pose.get(), model, entity);
        slideLegOffsetsDirty = true;
    }

    public static void clear() {
        pushedSlidePose = false;
        pushedSlidePosePlayerId = Integer.MIN_VALUE;
        slideLegOffsetsDirty = false;
    }

    private static Optional<ClientSlidePoseController.PoseSnapshot> renderPose(Minecraft minecraft, AvatarRenderState state) {
        if (minecraft.level == null) {
            return Optional.empty();
        }
        Entity entity = minecraft.level.getEntity(state.id);
        if (entity == null || !entity.isAlive() || entity.isPassenger() || entity.isSpectator()) {
            return Optional.empty();
        }
        return ClientSlidePoseController.poseForPlayer(state.id);
    }

    private static Vec3 visualOffset(ClientSlidePoseController.PoseSnapshot frame, Minecraft minecraft) {
        double lift = frame.ride().bodyLift() + 0.018D * (1.0D - frame.mountProgress()) + dismountLift(frame);
        Vec3 offset = new Vec3(0.0D, lift, 0.0D);
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        Vec3 cameraAway = frame.position().subtract(camera);
        Vec3 projected = cameraAway.subtract(frame.tangent().scale(cameraAway.dot(frame.tangent())));
        if (projected.lengthSqr() > 1.0E-6D) {
            offset = offset.add(projected.normalize().scale(0.07D * frame.verticalBlend() * frame.poseAlpha()));
        }
        return offset.scale(Mth.clamp(frame.poseAlpha(), 0.0D, 1.0D));
    }

    private static double renderBank(ClientSlidePoseController.PoseSnapshot frame, Minecraft minecraft) {
        Vec3 viewRight = Vec3.directionFromRotation(0.0F, minecraft.player == null ? 90.0F : minecraft.player.getYRot() + 90.0F);
        Vec3 tangentHorizontal = new Vec3(frame.tangent().x, 0.0D, frame.tangent().z);
        return tangentHorizontal.lengthSqr() < 1.0E-8D ? 0.0D : tangentHorizontal.normalize().dot(viewRight);
    }

    private static void applyDismountWorldPose(PoseStack poseStack, ClientSlidePoseController.PoseSnapshot pose, Vec3 facing) {
        if (pose.dismountKind() == ClientSlidePoseController.DismountKind.NONE) {
            return;
        }
        double progress = pose.dismountProgress();
        Vec3 right = safeNormalize(facing.cross(WORLD_UP), new Vec3(1.0D, 0.0D, 0.0D));
        if (pose.dismountKind() == ClientSlidePoseController.DismountKind.FLIP) {
            double load = smoothstep(0.0D, 0.16D, progress);
            double release = smoothstep(0.12D, 0.24D, progress);
            double spin = smoothstep(0.20D, 0.76D, progress);
            double open = smoothstep(0.66D, 0.94D, progress);
            double hop = Math.sin(progress * Math.PI);
            double preload = load * (1.0D - smoothstep(0.12D, 0.26D, progress));
            Vec3 horizontalFacing = new Vec3(facing.x, 0.0D, facing.z);
            if (horizontalFacing.lengthSqr() > 1.0E-6D) {
                double backward = 0.22D * smoothstep(0.16D, 0.82D, progress) * (1.0D - open * 0.18D);
                Vec3 drift = horizontalFacing.normalize().scale(-backward);
                poseStack.translate(drift.x, 0.0D, drift.z);
            }
            double centerArc = hop * 0.34D - preload * 0.060D - open * 0.035D;
            double centerPivot = 0.86D + release * 0.08D - open * 0.05D;
            poseStack.translate(0.0D, centerArc, 0.0D);
            poseStack.translate(0.0D, centerPivot, 0.0D);
            rotateAround(poseStack, right, -3.0D * preload - 360.0D * spin + 8.0D * open);
            poseStack.translate(0.0D, -centerPivot, 0.0D);
            rotateAround(poseStack, facing, Math.sin(progress * Math.PI) * 3.5D);
        } else {
            rotateAround(poseStack, right, -10.0D * Math.sin(progress * Math.PI));
            rotateAround(poseStack, facing, 7.0D * Math.sin(progress * Math.PI * 1.4D));
        }
    }

    private static double dismountLift(ClientSlidePoseController.PoseSnapshot pose) {
        if (pose.dismountKind() == ClientSlidePoseController.DismountKind.FLIP) {
            return 0.0D;
        }
        if (pose.dismountKind() == ClientSlidePoseController.DismountKind.STEP) {
            return Math.sin(pose.dismountProgress() * Math.PI) * 0.10D;
        }
        return 0.0D;
    }

    private static void applyNaturalSlidingModelPose(ClientSlidePoseController.PoseSnapshot pose, PlayerModel model) {
        ClientSlidePoseController.RidePoseDescriptor ride = pose.ride();
        float alpha = (float) Mth.clamp(pose.poseAlpha(), 0.0D, 1.0D);
        float speed = (float) Mth.clamp(pose.perceptualSpeed(), 0.0D, 1.0D);
        float pulse = (float) Mth.clamp(pose.accelerationPulse(), 0.0D, 1.0D);
        float turn = (float) Mth.clamp(turnSignal(pose.signedTurn(), pose.signedTurnPreview(), 0.58D), -1.0D, 1.0D);
        float station = (float) Mth.clamp(pose.platformBlend(), 0.0D, 1.0D);
        float vertical = (float) Mth.clamp(pose.verticalBlend(), 0.0D, 1.0D);
        float slope = (float) Mth.clamp(pose.tangent().y, -0.92D, 0.92D);
        float mount = (float) Mth.clamp(pose.mountProgress(), 0.0D, 1.0D);
        float phase = (float) (pose.motionPhase() * Mth.TWO_PI);
        float sway = (float) Math.sin(phase);
        float counterSway = (float) Math.cos(phase);
        float speedLean = (0.020F + speed * 0.024F + pulse * 0.014F) * (1.0F - station * 0.55F) * (1.0F - vertical * 0.78F);
        float turnLean = turn * 0.16F * (float) ride.turnLeanScale() * (1.0F - vertical * 0.68F);
        float balance = (0.014F + speed * 0.014F) * sway * (float) ride.balanceScale() * (1.0F - station * 0.45F);
        float slopeLean = slope * (0.014F + speed * 0.006F) * (1.0F - vertical * 0.72F) * (1.0F - station * 0.34F);
        float slopeArm = slope * (0.052F + speed * 0.018F) * (1.0F - vertical * 0.56F);
        float knee = ((float) ride.kneeBend() * 0.70F + speed * 0.075F + pulse * 0.040F - station * 0.060F) * alpha;
        float armSpread = ((float) ride.armBaseSpread() + speed * 0.26F + pulse * 0.12F - station * 0.20F) * alpha;

        model.body.xRot += (speedLean + slopeLean + 0.030F * mount) * alpha;
        model.body.zRot += (turnLean + balance * 0.45F) * alpha;
        model.head.zRot += (turnLean * 0.32F - balance * 0.20F) * alpha;
        model.head.xRot -= (0.035F * speed * (1.0F - station) + slopeLean * 0.28F) * alpha;

        applyStance(pose, model, alpha, speed, pulse, station, vertical, slope, knee, turn, balance);
        applyArms(pose, model, alpha, speed, pulse, station, vertical, armSpread, turn, balance, counterSway, slopeArm);
        applyDismountModelPose(pose, model, alpha);
    }

    private static void applyHeadLook(ClientSlidePoseController.PoseSnapshot pose, PlayerModel model, Entity entity) {
        Vec3 horizontalFacing = new Vec3(pose.visualFacing().x, 0.0D, pose.visualFacing().z);
        if (horizontalFacing.lengthSqr() < 1.0E-6D) {
            return;
        }
        float alpha = (float) Mth.clamp(pose.poseAlpha(), 0.0D, 1.0D);
        float bodyYaw = yawFromFacing(horizontalFacing);
        float yawDelta = Mth.wrapDegrees(entity.getYRot() - bodyYaw);
        float vertical = (float) Mth.clamp(pose.verticalBlend(), 0.0D, 1.0D);
        float maxYaw = lerp(70.0F, 42.0F, vertical);
        float maxPitch = lerp(46.0F, 30.0F, vertical);
        float headYaw = Mth.clamp(yawDelta, -maxYaw, maxYaw) * alpha;
        float headPitch = Mth.clamp(entity.getXRot(), -maxPitch, maxPitch) * alpha * 0.82F;
        model.head.yRot += headYaw * ((float) Math.PI / 180.0F);
        model.head.xRot += headPitch * ((float) Math.PI / 180.0F);
        model.hat.xRot = model.head.xRot;
        model.hat.yRot = model.head.yRot;
        model.hat.zRot = model.head.zRot;
    }

    private static void resetSlideLegOffsets(PlayerModel model) {
        model.rightLeg.x = -1.9F;
        model.leftLeg.x = 1.9F;
        model.rightLeg.z = 0.0F;
        model.leftLeg.z = 0.0F;
    }

    private static void applyStance(ClientSlidePoseController.PoseSnapshot pose, PlayerModel model, float alpha, float speed, float pulse, float station, float vertical, float slope, float knee, float turn, float balance) {
        ClientSlidePoseController.RidePoseDescriptor ride = pose.ride();
        float stanceWidthPx = (float) Mth.clamp(ride.stanceWidth() * 16.0D, 1.2D, 8.6D);
        float stanceLengthPx = (float) Mth.clamp(ride.stanceLength() * 16.0D, 1.0D, 6.4D);
        float width01 = (float) Mth.clamp((stanceWidthPx - 2.8F) / 5.8F, 0.0F, 1.0F);
        float length01 = (float) Mth.clamp((stanceLengthPx - 1.0F) / 5.4F, 0.0F, 1.0F);
        float hipOpen = width01 * 0.105F;
        float footYaw = 0.030F + speed * 0.025F;
        float footRoll = 0.035F + speed * 0.035F;
        switch (ride.family()) {
            case SPLIT_RAIL -> {
                model.rightLeg.x = -1.9F - hipOpen;
                model.leftLeg.x = 1.9F + hipOpen;
                model.rightLeg.z = 0.0F;
                model.leftLeg.z = 0.0F;
                float railOut = 0.14F + width01 * 0.18F;
                float railStagger = 0.04F + length01 * 0.08F;
                model.rightLeg.xRot = (0.034F + knee * 0.15F + pulse * 0.018F + railStagger * 0.42F) * alpha;
                model.leftLeg.xRot = (0.024F + knee * 0.11F - pulse * 0.008F - railStagger * 0.18F) * alpha;
                model.rightLeg.yRot = (-railOut - footYaw * 0.50F + turn * 0.050F) * alpha;
                model.leftLeg.yRot = (railOut + footYaw * 0.50F + turn * 0.050F) * alpha;
                model.rightLeg.zRot = (0.075F + width01 * 0.055F + balance * 0.16F) * alpha;
                model.leftLeg.zRot = (-0.075F - width01 * 0.055F + balance * 0.16F) * alpha;
            }
            case CRADLE -> {
                model.rightLeg.x = -1.9F - hipOpen * 0.45F;
                model.leftLeg.x = 1.9F + hipOpen * 0.45F;
                model.rightLeg.z = 0.0F;
                model.leftLeg.z = 0.0F;
                model.rightLeg.xRot = (0.052F + knee * 0.18F + pulse * 0.018F) * alpha;
                model.leftLeg.xRot = (-0.020F + knee * 0.070F - station * 0.020F) * alpha;
                model.rightLeg.yRot = (-0.035F - footYaw * 0.45F + turn * 0.040F) * alpha;
                model.leftLeg.yRot = (0.035F + footYaw * 0.45F + turn * 0.040F) * alpha;
                model.rightLeg.zRot = (0.030F + balance * 0.16F) * alpha;
                model.leftLeg.zRot = (-0.030F + balance * 0.16F) * alpha;
            }
            case MONORAIL -> {
                model.rightLeg.x = -1.9F - hipOpen * 0.25F;
                model.leftLeg.x = 1.9F + hipOpen * 0.25F;
                model.rightLeg.z = 0.0F;
                model.leftLeg.z = 0.0F;
                model.rightLeg.xRot = (0.062F + knee * 0.19F + pulse * 0.020F + length01 * 0.018F) * alpha;
                model.leftLeg.xRot = (-0.040F + knee * 0.070F - length01 * 0.014F) * alpha;
                model.rightLeg.yRot = (-0.020F - footYaw * 0.55F + turn * 0.050F) * alpha;
                model.leftLeg.yRot = (0.020F + footYaw * 0.55F + turn * 0.050F) * alpha;
                model.rightLeg.zRot = (footRoll * 0.60F + balance * 0.20F) * alpha;
                model.leftLeg.zRot = (-footRoll * 0.60F + balance * 0.20F) * alpha;
            }
            default -> {
                model.rightLeg.x = -1.9F - hipOpen * 0.35F;
                model.leftLeg.x = 1.9F + hipOpen * 0.35F;
                model.rightLeg.z = 0.0F;
                model.leftLeg.z = 0.0F;
                model.rightLeg.xRot = (0.056F + knee * 0.19F + pulse * 0.022F + length01 * 0.020F) * alpha;
                model.leftLeg.xRot = (-0.038F + knee * 0.055F - station * 0.014F - length01 * 0.018F) * alpha;
                model.rightLeg.yRot = (-0.030F - footYaw + turn * 0.050F) * alpha;
                model.leftLeg.yRot = (0.030F + footYaw + turn * 0.050F) * alpha;
                model.rightLeg.zRot = (footRoll + balance * 0.18F) * alpha;
                model.leftLeg.zRot = (-footRoll + balance * 0.18F) * alpha;
            }
        }
        float verticalTuck = vertical * (0.11F + speed * 0.045F) * alpha;
        if (pose.upBlend() >= pose.downBlend()) {
            model.rightLeg.xRot += verticalTuck * 0.16F;
            model.leftLeg.xRot -= verticalTuck * 0.035F;
        } else {
            model.rightLeg.xRot -= verticalTuck * 0.12F;
            model.leftLeg.xRot += verticalTuck * 0.28F;
        }
        float uphillStability = Math.max((float) smoothstep(0.04D, 0.50D, Math.max(0.0F, slope)), (float) pose.upBlend() * 0.86F);
        float forwardStability = 0.12F + uphillStability * 0.46F;
        model.rightLeg.xRot *= 1.0F - forwardStability;
        model.leftLeg.xRot *= 1.0F - forwardStability * 0.72F;
    }

    private static void applyArms(ClientSlidePoseController.PoseSnapshot pose, PlayerModel model, float alpha, float speed, float pulse, float station, float vertical, float armSpread, float turn, float balance, float counterSway, float slopeArm) {
        float armFloat = counterSway * (0.006F + speed * 0.007F) * (1.0F - station * 0.50F);
        float verticalOpen = vertical * 0.24F;
        float accelerationPull = pulse * 0.11F;
        model.rightArm.xRot = (-0.20F - speed * 0.12F - accelerationPull - slopeArm + armFloat + verticalOpen * 0.35F) * alpha;
        model.leftArm.xRot = (-0.20F - speed * 0.12F - accelerationPull - slopeArm - armFloat + verticalOpen * 0.35F) * alpha;
        model.rightArm.yRot = (-0.075F - speed * 0.040F + turn * 0.060F) * alpha;
        model.leftArm.yRot = (0.075F + speed * 0.040F + turn * 0.060F) * alpha;
        model.rightArm.zRot = (armSpread + verticalOpen - turn * 0.15F + balance * 0.12F) * alpha;
        model.leftArm.zRot = (-armSpread - verticalOpen - turn * 0.15F + balance * 0.12F) * alpha;
    }

    private static void applyDismountModelPose(ClientSlidePoseController.PoseSnapshot pose, PlayerModel model, float alpha) {
        if (pose.dismountKind() == ClientSlidePoseController.DismountKind.NONE) {
            return;
        }
        float p = (float) Mth.clamp(pose.dismountProgress(), 0.0D, 1.0D);
        if (pose.dismountKind() == ClientSlidePoseController.DismountKind.FLIP) {
            float load = (float) smoothstep(0.0D, 0.16D, p);
            float release = (float) smoothstep(0.12D, 0.24D, p);
            float tuck = (float) (smoothstep(0.22D, 0.40D, p) * (1.0D - smoothstep(0.58D, 0.84D, p)));
            float open = (float) smoothstep(0.66D, 0.94D, p);
            float preload = load * (1.0F - release);
            model.body.xRot += (preload * 0.24F + tuck * 0.40F - open * 0.12F) * alpha;
            model.rightLeg.xRot = (preload * 0.42F + tuck * 1.02F - open * 0.07F) * alpha;
            model.leftLeg.xRot = (preload * 0.24F + tuck * 0.92F - open * 0.08F) * alpha;
            model.rightLeg.zRot += (preload * 0.04F + tuck * 0.11F + open * 0.025F) * alpha;
            model.leftLeg.zRot -= (preload * 0.04F + tuck * 0.11F + open * 0.025F) * alpha;
            model.rightArm.xRot = (-0.62F * preload - 1.08F * release - tuck * 0.30F + open * 0.26F) * alpha;
            model.leftArm.xRot = (-0.62F * preload - 1.08F * release - tuck * 0.30F + open * 0.26F) * alpha;
            model.rightArm.yRot = (-0.18F - tuck * 0.08F + open * 0.08F) * alpha;
            model.leftArm.yRot = (0.18F + tuck * 0.08F - open * 0.08F) * alpha;
            model.rightArm.zRot = (0.40F * preload + 0.62F * release + tuck * 0.22F + open * 0.14F) * alpha;
            model.leftArm.zRot = (-0.40F * preload - 0.62F * release - tuck * 0.22F - open * 0.14F) * alpha;
        } else {
            float tuck = (float) Math.sin(p * Math.PI);
            model.body.xRot += tuck * 0.12F * alpha;
            model.rightLeg.xRot += tuck * 0.24F * alpha;
            model.leftLeg.xRot -= tuck * 0.10F * alpha;
            model.rightArm.zRot += tuck * 0.24F * alpha;
            model.leftArm.zRot -= tuck * 0.24F * alpha;
        }
    }

    private static double slopeBalancePitch(ClientSlidePoseController.PoseSnapshot pose) {
        double slope = Mth.clamp(pose.tangent().y, -0.92D, 0.92D);
        double slopeResponse = smoothstep(0.035D, 0.48D, Math.abs(slope));
        double degrees = -Math.signum(slope) * slopeResponse * (5.5D + pose.perceptualSpeed() * 5.0D + pose.accelerationPulse() * 1.6D);
        return degrees
                * (1.0D - pose.verticalBlend() * 0.46D)
                * (1.0D - pose.platformBlend() * 0.34D)
                * pose.poseAlpha();
    }

    private static void rotateAround(PoseStack poseStack, Vec3 axis, double degrees) {
        if (Math.abs(degrees) < 1.0E-4D || axis.lengthSqr() < 1.0E-8D) {
            return;
        }
        Vec3 normalized = axis.normalize();
        poseStack.mulPose(Axis.of(new Vector3f((float) normalized.x, (float) normalized.y, (float) normalized.z)).rotationDegrees((float) degrees));
    }

    private static void alignBodyToFacing(AvatarRenderState state, Vec3 facing) {
        Vec3 horizontal = new Vec3(facing.x, 0.0D, facing.z);
        if (horizontal.lengthSqr() < 1.0E-6D) {
            return;
        }
        float yaw = yawFromFacing(horizontal);
        state.bodyRot = yaw;
        state.yRot = 0.0F;
    }

    private static double turnSignal(double signedTurn, double signedTurnPreview, double previewWeight) {
        return Mth.clamp(signedTurn + signedTurnPreview * previewWeight, -1.0D, 1.0D);
    }

    private static float yawFromFacing(Vec3 horizontalFacing) {
        return (float) (Math.atan2(-horizontalFacing.x, horizontalFacing.z) * 180.0D / Math.PI);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    private static Vec3 safeNormalize(Vec3 value, Vec3 fallback) {
        return value.lengthSqr() < 1.0E-8D ? fallback : value.normalize();
    }
}
