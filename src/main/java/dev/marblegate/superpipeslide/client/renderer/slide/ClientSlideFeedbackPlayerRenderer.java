package dev.marblegate.superpipeslide.client.renderer.slide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.marblegate.superpipeslide.client.core.accessibility.ClientSafetyOptions;
import dev.marblegate.superpipeslide.client.core.slide.ClientSlideFeedbackController;
import dev.marblegate.superpipeslide.client.core.slide.ClientSlidePoseController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ClientSlideFeedbackPlayerRenderer {
    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Map<Integer, ModelPoseBlendState> MODEL_POSE_BLEND_STATES = new LinkedHashMap<>();
    private static final Map<Integer, FloatBlendState> RENDER_YAW_BLEND_STATES = new LinkedHashMap<>();
    private static final Map<Integer, FloatBlendState> RIDE_ROTATION_BLEND_STATES = new LinkedHashMap<>();
    private static final int BODY_X_ROT = 0;
    private static final int BODY_Y_ROT = 1;
    private static final int BODY_Z_ROT = 2;
    private static final int HEAD_X_ROT = 3;
    private static final int HEAD_Y_ROT = 4;
    private static final int HEAD_Z_ROT = 5;
    private static final int HAT_X_ROT = 6;
    private static final int HAT_Y_ROT = 7;
    private static final int HAT_Z_ROT = 8;
    private static final int RIGHT_ARM_X_ROT = 9;
    private static final int RIGHT_ARM_Y_ROT = 10;
    private static final int RIGHT_ARM_Z_ROT = 11;
    private static final int LEFT_ARM_X_ROT = 12;
    private static final int LEFT_ARM_Y_ROT = 13;
    private static final int LEFT_ARM_Z_ROT = 14;
    private static final int RIGHT_LEG_X_ROT = 15;
    private static final int RIGHT_LEG_Y_ROT = 16;
    private static final int RIGHT_LEG_Z_ROT = 17;
    private static final int LEFT_LEG_X_ROT = 18;
    private static final int LEFT_LEG_Y_ROT = 19;
    private static final int LEFT_LEG_Z_ROT = 20;
    private static final int RIGHT_LEG_X = 21;
    private static final int RIGHT_LEG_Y = 22;
    private static final int RIGHT_LEG_Z = 23;
    private static final int LEFT_LEG_X = 24;
    private static final int LEFT_LEG_Y = 25;
    private static final int LEFT_LEG_Z = 26;
    private static final int MODEL_POSE_CHANNELS = 27;
    private static boolean pushedSlidePose;
    private static int pushedSlidePosePlayerId = Integer.MIN_VALUE;
    private static boolean slideLegOffsetsDirty;

    private ClientSlideFeedbackPlayerRenderer() {
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (ClientSafetyOptions.reduceMotionSicknessRisk()) {
            return;
        }
        Optional<ClientSlideFeedbackController.Frame> frame = ClientSlideFeedbackController.currentRenderFrame();
        if (frame.isEmpty()) {
            return;
        }
        event.setFOV(event.getFOV() + (float) frame.get().fovBoost());
    }

    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!ClientSafetyOptions.slideCameraFeedbackEnabled()) {
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
        state.walkAnimationPos = 0.0F;
        state.walkAnimationSpeed = 0.0F;
        // HumanoidModel divides by speedValue while posing limbs; keep it non-zero when freezing walk motion.
        state.speedValue = 1.0F;
        state.swimAmount = 0.0F;
        state.attackTime = 0.0F;
        ClientSlidePoseController.SlidePoseFrame poseFrame = feedback.poseFrame();
        Vec3 renderFacing = renderFacing(feedback, poseFrame);
        float sideStance = fixedStanceSide(feedback);
        float sideSlipYaw = sideSlipYaw(feedback, poseFrame, sideStance);
        float smoothedSideSlipYaw = renderYawState(state.id, feedback.frame().sessionId()).sample(sideSlipYaw);
        renderFacing = rotateHorizontal(renderFacing, smoothedSideSlipYaw);
        alignBodyToFacing(state, renderFacing);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        pushedSlidePose = true;
        pushedSlidePosePlayerId = state.id;

        Vec3 offset = visualOffset(feedback, poseFrame, minecraft);
        poseStack.translate(offset.x, offset.y, offset.z);

        Vec3 baseFacing = renderFacing;
        Vec3 baseRight = safeNormalize(baseFacing.cross(WORLD_UP), new Vec3(1.0D, 0.0D, 0.0D));
        double rideAmount = rideRotationState(state.id, feedback.frame().sessionId()).sample((float) rideFrameAmount(feedback, poseFrame));
        applyFrameRideRotation(poseStack, poseFrame, renderFacing, rideAmount);
        ClientSlideBalancePoseSolver.WorldPose worldPose = ClientSlideBalancePoseSolver.solveWorld(feedback, poseFrame, (float) renderBank(feedback, minecraft), (float) rideAmount);
        rotateAround(poseStack, baseRight, worldPose.pitchDegrees());
        rotateAround(poseStack, baseFacing, worldPose.rollDegrees());
        applyDismountWorldPose(poseStack, feedback, baseFacing);
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
        ClientSlidePoseController.PoseSnapshot snapshot = pose.get();
        ClientSlidePoseController.SlidePoseFrame poseFrame = snapshot.poseFrame();
        ModelPose basePose = ModelPose.capture(model);
        ClientSlideBalancePoseSolver.solveModel(snapshot, poseFrame, fixedStanceSide(snapshot)).apply(model);
        applyDismountModelPose(snapshot, model, (float) Mth.clamp(snapshot.poseAlpha(), 0.0D, 1.0D));
        applyHeadLook(snapshot, poseFrame, model, entity);
        ModelPose targetPose = ModelPose.capture(model);
        basePose.applyTo(model);
        modelPoseBlendState(state.id, snapshot.frame().sessionId()).sample(basePose, targetPose).applyTo(model);
        slideLegOffsetsDirty = true;
    }

    public static void clear() {
        pushedSlidePose = false;
        pushedSlidePosePlayerId = Integer.MIN_VALUE;
        slideLegOffsetsDirty = false;
        MODEL_POSE_BLEND_STATES.clear();
        RENDER_YAW_BLEND_STATES.clear();
        RIDE_ROTATION_BLEND_STATES.clear();
    }

    private static Optional<ClientSlidePoseController.PoseSnapshot> renderPose(Minecraft minecraft, AvatarRenderState state) {
        if (minecraft.level == null) {
            return Optional.empty();
        }
        if (MODEL_POSE_BLEND_STATES.size() > 48) {
            MODEL_POSE_BLEND_STATES.clear();
        }
        if (RENDER_YAW_BLEND_STATES.size() > 48) {
            RENDER_YAW_BLEND_STATES.clear();
        }
        if (RIDE_ROTATION_BLEND_STATES.size() > 48) {
            RIDE_ROTATION_BLEND_STATES.clear();
        }
        Entity entity = minecraft.level.getEntity(state.id);
        if (entity == null || !entity.isAlive() || entity.isPassenger() || entity.isSpectator()) {
            MODEL_POSE_BLEND_STATES.remove(state.id);
            RENDER_YAW_BLEND_STATES.remove(state.id);
            RIDE_ROTATION_BLEND_STATES.remove(state.id);
            return Optional.empty();
        }
        Optional<ClientSlidePoseController.PoseSnapshot> pose = ClientSlidePoseController.poseForPlayer(state.id);
        if (pose.isEmpty()) {
            MODEL_POSE_BLEND_STATES.remove(state.id);
            RENDER_YAW_BLEND_STATES.remove(state.id);
            RIDE_ROTATION_BLEND_STATES.remove(state.id);
        }
        return pose;
    }

    private static Vec3 visualOffset(ClientSlidePoseController.PoseSnapshot frame, ClientSlidePoseController.SlidePoseFrame poseFrame, Minecraft minecraft) {
        double vertical = poseFrame.verticalAmount();
        double railLower = frame.ride().railSpread() * 0.075D * (frame.ride().family() == ClientSlidePoseController.RidePoseFamily.SPLIT_RAIL ? 1.0D : 0.35D);
        double wallSettle = vertical * (0.16D + frame.perceptualSpeed() * 0.08D) * frame.ride().wallRideScale();
        double lift = frame.ride().bodyLift() + 0.018D * (1.0D - frame.mountProgress()) + dismountLift(frame) - railLower - wallSettle;
        Vec3 offset = poseFrame.up().scale(lift);
        double turn = turnSignal(frame.signedTurn(), frame.signedTurnPreview(), 0.62D);
        offset = offset.add(poseFrame.right().scale(-turn * (0.030D + frame.perceptualSpeed() * 0.050D) * frame.ride().balanceScale() * frame.poseAlpha()));
        offset = offset.add(poseFrame.forward().scale((frame.downBlend() - frame.upBlend()) * vertical * 0.060D * frame.poseAlpha()));
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        Vec3 cameraAway = frame.position().subtract(camera);
        Vec3 projected = cameraAway.subtract(frame.tangent().scale(cameraAway.dot(frame.tangent())));
        if (projected.lengthSqr() > 1.0E-6D) {
            offset = offset.add(projected.normalize().scale(0.07D * frame.verticalBlend() * frame.poseAlpha()));
        }
        return offset.scale(Mth.clamp(frame.poseAlpha(), 0.0D, 1.0D));
    }

    private static void applyFrameRideRotation(PoseStack poseStack, ClientSlidePoseController.SlidePoseFrame frame, Vec3 renderFacing, double amount) {
        if (amount <= 1.0E-4D) {
            return;
        }
        Vec3 baseForward = safeNormalize(new Vec3(renderFacing.x, 0.0D, renderFacing.z), new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 targetForward = lerpDirection(baseForward, frame.forward(), amount);
        rotateBetween(poseStack, baseForward, targetForward);

        Vec3 rotatedUp = rotateVector(WORLD_UP, baseForward, targetForward);
        Vec3 targetUp = lerpDirection(rotatedUp, frame.up(), amount);
        double roll = signedAngleOnPlane(rotatedUp, targetUp, targetForward);
        rotateAround(poseStack, baseForward, Math.toDegrees(roll));
    }

    private static double rideFrameAmount(ClientSlidePoseController.PoseSnapshot pose, ClientSlidePoseController.SlidePoseFrame frame) {
        double styleScale = Mth.clamp(pose.ride().verticalRideScale(), 0.0D, 1.2D);
        double amount = frame.trackAmount() * (0.46D + styleScale * 0.54D) * pose.poseAlpha();
        return Mth.clamp(amount, 0.0D, 1.0D);
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

    private static void applyHeadLook(ClientSlidePoseController.PoseSnapshot pose, ClientSlidePoseController.SlidePoseFrame poseFrame, PlayerModel model, Entity entity) {
        Vec3 horizontalFacing = renderFacing(pose, poseFrame);
        float vertical = (float) Mth.clamp(poseFrame.verticalAmount(), 0.0D, 1.0D);
        if (horizontalFacing.lengthSqr() < 1.0E-6D || vertical > 0.92F) {
            model.head.xRot += entity.getXRot() * ((float) Math.PI / 180.0F) * (1.0F - vertical) * 0.35F;
            syncHat(model);
            return;
        }
        float alpha = (float) Mth.clamp(pose.poseAlpha(), 0.0D, 1.0D);
        float bodyYaw = yawFromFacing(horizontalFacing);
        float yawDelta = Mth.wrapDegrees(entity.getYRot() - bodyYaw);
        float maxYaw = lerp(70.0F, 42.0F, vertical);
        float maxPitch = lerp(46.0F, 30.0F, vertical);
        float headYaw = Mth.clamp(yawDelta, -maxYaw, maxYaw) * alpha;
        float headPitch = Mth.clamp(entity.getXRot(), -maxPitch, maxPitch) * alpha * 0.82F;
        model.head.yRot += headYaw * ((float) Math.PI / 180.0F);
        model.head.xRot += headPitch * ((float) Math.PI / 180.0F);
        syncHat(model);
    }

    private static void syncHat(PlayerModel model) {
        model.hat.xRot = model.head.xRot;
        model.hat.yRot = model.head.yRot;
        model.hat.zRot = model.head.zRot;
    }

    private static void resetSlideLegOffsets(PlayerModel model) {
        ClientSlideBalancePoseSolver.resetLegRoots(model);
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

    private static void rotateAround(PoseStack poseStack, Vec3 axis, double degrees) {
        if (Math.abs(degrees) < 1.0E-4D || axis.lengthSqr() < 1.0E-8D) {
            return;
        }
        Vec3 normalized = axis.normalize();
        poseStack.mulPose(Axis.of(new Vector3f((float) normalized.x, (float) normalized.y, (float) normalized.z)).rotationDegrees((float) degrees));
    }

    private static void rotateBetween(PoseStack poseStack, Vec3 from, Vec3 to) {
        Vec3 source = safeNormalize(from, new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 target = safeNormalize(to, source);
        double dot = Mth.clamp(source.dot(target), -1.0D, 1.0D);
        if (dot > 0.9999D) {
            return;
        }
        Vec3 axis = source.cross(target);
        if (axis.lengthSqr() < 1.0E-8D) {
            axis = safeNormalize(source.cross(WORLD_UP), new Vec3(1.0D, 0.0D, 0.0D));
        }
        rotateAround(poseStack, axis, Math.toDegrees(Math.acos(dot)));
    }

    private static Vec3 rotateVector(Vec3 value, Vec3 from, Vec3 to) {
        Vec3 source = safeNormalize(from, new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 target = safeNormalize(to, source);
        double dot = Mth.clamp(source.dot(target), -1.0D, 1.0D);
        if (dot > 0.9999D) {
            return value;
        }
        Vec3 axis = source.cross(target);
        if (axis.lengthSqr() < 1.0E-8D) {
            axis = safeNormalize(source.cross(WORLD_UP), new Vec3(1.0D, 0.0D, 0.0D));
        }
        Vec3 normalizedAxis = axis.normalize();
        Quaternionf rotation = new Quaternionf().rotateAxis(
                (float) Math.acos(dot),
                (float) normalizedAxis.x,
                (float) normalizedAxis.y,
                (float) normalizedAxis.z
        );
        Vector3f rotated = new Vector3f((float) value.x, (float) value.y, (float) value.z).rotate(rotation);
        return new Vec3(rotated.x, rotated.y, rotated.z);
    }

    private static double signedAngleOnPlane(Vec3 from, Vec3 to, Vec3 normal) {
        Vec3 axis = safeNormalize(normal, new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 a = from.subtract(axis.scale(from.dot(axis)));
        Vec3 b = to.subtract(axis.scale(to.dot(axis)));
        if (a.lengthSqr() < 1.0E-8D || b.lengthSqr() < 1.0E-8D) {
            return 0.0D;
        }
        Vec3 normalizedA = a.normalize();
        Vec3 normalizedB = b.normalize();
        double sin = axis.dot(normalizedA.cross(normalizedB));
        double cos = Mth.clamp(normalizedA.dot(normalizedB), -1.0D, 1.0D);
        return Math.atan2(sin, cos);
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

    private static Vec3 renderFacing(ClientSlidePoseController.PoseSnapshot pose, ClientSlidePoseController.SlidePoseFrame frame) {
        Vec3 horizontal = new Vec3(frame.forward().x, 0.0D, frame.forward().z);
        if (horizontal.lengthSqr() > 1.0E-6D) {
            return horizontal.normalize();
        }
        Vec3 fallback = new Vec3(pose.visualFacing().x, 0.0D, pose.visualFacing().z);
        return safeNormalize(fallback, new Vec3(0.0D, 0.0D, 1.0D));
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

    private static Vec3 lerpDirection(Vec3 from, Vec3 to, double t) {
        Vec3 value = new Vec3(
                from.x + (to.x - from.x) * t,
                from.y + (to.y - from.y) * t,
                from.z + (to.z - from.z) * t
        );
        return safeNormalize(value, to);
    }

    private static Vec3 rotateHorizontal(Vec3 direction, float degrees) {
        Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z);
        if (horizontal.lengthSqr() < 1.0E-8D || Math.abs(degrees) < 1.0E-4F) {
            return direction;
        }
        double radians = degrees * Math.PI / 180.0D;
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        Vec3 normalized = horizontal.normalize();
        return new Vec3(normalized.x * cos - normalized.z * sin, 0.0D, normalized.x * sin + normalized.z * cos).normalize();
    }

    private static float fixedStanceSide(ClientSlidePoseController.PoseSnapshot pose) {
        return (pose.frame().sessionId().getLeastSignificantBits() & 1L) == 0L ? 1.0F : -1.0F;
    }

    private static float sideSlipYaw(ClientSlidePoseController.PoseSnapshot pose, ClientSlidePoseController.SlidePoseFrame frame, float sideStance) {
        float inline = inlineSupportAmount(pose.ride());
        if (inline <= 1.0E-4F) {
            return 0.0F;
        }
        float vertical = (float) Mth.clamp(frame.verticalAmount(), 0.0D, 1.0D);
        float speed = (float) Mth.clamp(pose.perceptualSpeed(), 0.0D, 1.0D);
        float degrees = 48.0F + inline * 30.0F + speed * 6.0F;
        return sideStance * degrees * (1.0F - vertical * 0.24F) * (float) Mth.clamp(pose.poseAlpha(), 0.0D, 1.0D);
    }

    private static float inlineSupportAmount(ClientSlidePoseController.RidePoseDescriptor ride) {
        return switch (ride.family()) {
            case MONORAIL -> 1.0F;
            case INLINE -> 0.86F;
            default -> 0.0F;
        };
    }

    private static ModelPoseBlendState modelPoseBlendState(int entityId, UUID sessionId) {
        return MODEL_POSE_BLEND_STATES.compute(entityId, (ignored, previous) -> {
            if (previous == null || !previous.sessionId.equals(sessionId)) {
                return new ModelPoseBlendState(sessionId);
            }
            return previous;
        });
    }

    private static FloatBlendState renderYawState(int entityId, UUID sessionId) {
        return RENDER_YAW_BLEND_STATES.compute(entityId, (ignored, previous) -> {
            if (previous == null || !previous.sessionId.equals(sessionId)) {
                return new FloatBlendState(sessionId);
            }
            return previous;
        });
    }

    private static FloatBlendState rideRotationState(int entityId, UUID sessionId) {
        return RIDE_ROTATION_BLEND_STATES.compute(entityId, (ignored, previous) -> {
            if (previous == null || !previous.sessionId.equals(sessionId)) {
                return new FloatBlendState(sessionId);
            }
            return previous;
        });
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    private static Vec3 safeNormalize(Vec3 value, Vec3 fallback) {
        return value.lengthSqr() < 1.0E-8D ? fallback : value.normalize();
    }

    private record ModelPose(float[] values) {
        private static ModelPose capture(PlayerModel model) {
            float[] values = new float[MODEL_POSE_CHANNELS];
            values[BODY_X_ROT] = model.body.xRot;
            values[BODY_Y_ROT] = model.body.yRot;
            values[BODY_Z_ROT] = model.body.zRot;
            values[HEAD_X_ROT] = model.head.xRot;
            values[HEAD_Y_ROT] = model.head.yRot;
            values[HEAD_Z_ROT] = model.head.zRot;
            values[HAT_X_ROT] = model.hat.xRot;
            values[HAT_Y_ROT] = model.hat.yRot;
            values[HAT_Z_ROT] = model.hat.zRot;
            values[RIGHT_ARM_X_ROT] = model.rightArm.xRot;
            values[RIGHT_ARM_Y_ROT] = model.rightArm.yRot;
            values[RIGHT_ARM_Z_ROT] = model.rightArm.zRot;
            values[LEFT_ARM_X_ROT] = model.leftArm.xRot;
            values[LEFT_ARM_Y_ROT] = model.leftArm.yRot;
            values[LEFT_ARM_Z_ROT] = model.leftArm.zRot;
            values[RIGHT_LEG_X_ROT] = model.rightLeg.xRot;
            values[RIGHT_LEG_Y_ROT] = model.rightLeg.yRot;
            values[RIGHT_LEG_Z_ROT] = model.rightLeg.zRot;
            values[LEFT_LEG_X_ROT] = model.leftLeg.xRot;
            values[LEFT_LEG_Y_ROT] = model.leftLeg.yRot;
            values[LEFT_LEG_Z_ROT] = model.leftLeg.zRot;
            values[RIGHT_LEG_X] = model.rightLeg.x;
            values[RIGHT_LEG_Y] = model.rightLeg.y;
            values[RIGHT_LEG_Z] = model.rightLeg.z;
            values[LEFT_LEG_X] = model.leftLeg.x;
            values[LEFT_LEG_Y] = model.leftLeg.y;
            values[LEFT_LEG_Z] = model.leftLeg.z;
            return new ModelPose(values);
        }

        private void applyTo(PlayerModel model) {
            model.body.xRot = this.values[BODY_X_ROT];
            model.body.yRot = this.values[BODY_Y_ROT];
            model.body.zRot = this.values[BODY_Z_ROT];
            model.head.xRot = this.values[HEAD_X_ROT];
            model.head.yRot = this.values[HEAD_Y_ROT];
            model.head.zRot = this.values[HEAD_Z_ROT];
            model.hat.xRot = this.values[HAT_X_ROT];
            model.hat.yRot = this.values[HAT_Y_ROT];
            model.hat.zRot = this.values[HAT_Z_ROT];
            model.rightArm.xRot = this.values[RIGHT_ARM_X_ROT];
            model.rightArm.yRot = this.values[RIGHT_ARM_Y_ROT];
            model.rightArm.zRot = this.values[RIGHT_ARM_Z_ROT];
            model.leftArm.xRot = this.values[LEFT_ARM_X_ROT];
            model.leftArm.yRot = this.values[LEFT_ARM_Y_ROT];
            model.leftArm.zRot = this.values[LEFT_ARM_Z_ROT];
            model.rightLeg.xRot = this.values[RIGHT_LEG_X_ROT];
            model.rightLeg.yRot = this.values[RIGHT_LEG_Y_ROT];
            model.rightLeg.zRot = this.values[RIGHT_LEG_Z_ROT];
            model.leftLeg.xRot = this.values[LEFT_LEG_X_ROT];
            model.leftLeg.yRot = this.values[LEFT_LEG_Y_ROT];
            model.leftLeg.zRot = this.values[LEFT_LEG_Z_ROT];
            model.rightLeg.x = this.values[RIGHT_LEG_X];
            model.rightLeg.y = this.values[RIGHT_LEG_Y];
            model.rightLeg.z = this.values[RIGHT_LEG_Z];
            model.leftLeg.x = this.values[LEFT_LEG_X];
            model.leftLeg.y = this.values[LEFT_LEG_Y];
            model.leftLeg.z = this.values[LEFT_LEG_Z];
        }
    }

    private static final class ModelPoseBlendState {
        private final UUID sessionId;
        private final float[] values = new float[MODEL_POSE_CHANNELS];
        private boolean initialized;

        private ModelPoseBlendState(UUID sessionId) {
            this.sessionId = sessionId;
        }

        private ModelPose sample(ModelPose base, ModelPose target) {
            if (!this.initialized) {
                System.arraycopy(base.values, 0, this.values, 0, this.values.length);
                this.initialized = true;
            }
            for (int i = 0; i < this.values.length; i++) {
                this.values[i] = lerp(this.values[i], target.values[i], 0.22F);
            }
            return new ModelPose(this.values.clone());
        }
    }

    private static final class FloatBlendState {
        private final UUID sessionId;
        private float value;
        private boolean initialized;

        private FloatBlendState(UUID sessionId) {
            this.sessionId = sessionId;
        }

        private float sample(float target) {
            if (!this.initialized) {
                this.value = 0.0F;
                this.initialized = true;
            }
            this.value = lerp(this.value, target, 0.18F);
            return this.value;
        }
    }
}
