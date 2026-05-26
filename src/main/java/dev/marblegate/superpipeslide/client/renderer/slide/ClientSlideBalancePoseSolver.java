package dev.marblegate.superpipeslide.client.renderer.slide;

import dev.marblegate.superpipeslide.client.core.slide.ClientSlidePoseController;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class ClientSlideBalancePoseSolver {
    private static final float RIGHT_LEG_ROOT_X = -1.9F;
    private static final float LEFT_LEG_ROOT_X = 1.9F;
    private static final float LEG_ROOT_Y = 12.0F;
    private static final float LEG_ROOT_Z = 0.0F;

    private ClientSlideBalancePoseSolver() {
    }

    static ModelPose solveModel(ClientSlidePoseController.PoseSnapshot pose, ClientSlidePoseController.SlidePoseFrame frame, float sideStance) {
        BalanceState balance = BalanceState.of(pose, frame, sideStance);
        BodyPose body = solveBody(balance);
        LegPairPose legs = solveLegs(balance, body);
        ArmPairPose arms = solveArms(balance);
        HeadPose head = solveHead(balance);
        return new ModelPose(body, head, legs.right(), legs.left(), arms.right(), arms.left());
    }

    static WorldPose solveWorld(ClientSlidePoseController.PoseSnapshot pose, ClientSlidePoseController.SlidePoseFrame frame, float viewBank, float rideAmount) {
        float alpha = (float) Mth.clamp(pose.poseAlpha(), 0.0D, 1.0D);
        float speed = (float) Mth.clamp(pose.perceptualSpeed(), 0.0D, 1.0D);
        float pulse = (float) Mth.clamp(pose.accelerationPulse(), 0.0D, 1.0D);
        float station = (float) Mth.clamp(pose.platformBlend(), 0.0D, 1.0D);
        float vertical = (float) Mth.clamp(frame.verticalAmount(), 0.0D, 1.0D);
        float slope = (float) Mth.clamp(pose.tangent().y, -0.92D, 0.92D);
        float uphill = Math.max(0.0F, slope);
        float downhill = Math.max(0.0F, -slope);
        float slopeResponse = (float) smoothstep(0.035D, 0.48D, Math.abs(slope));
        float ride = (float) Mth.clamp(rideAmount, 0.0D, 1.0D);
        float slopePitch = -Math.signum(slope) * slopeResponse * (6.0F + speed * 5.2F + pulse * 1.7F)
                * (1.0F - vertical * 0.46F)
                * (1.0F - station * 0.34F)
                * (1.0F - ride * 0.74F);
        float supportPitch = -(1.5F + speed * 2.3F + pulse * 1.0F)
                * (1.0F - station * 0.45F)
                * (1.0F - ride * 0.86F);
        float gravityPitch = ((float) smoothstep(0.04D, 0.58D, downhill) * (4.2F + speed * 3.8F + pulse * 1.4F)
                - (float) smoothstep(0.04D, 0.58D, uphill) * (3.4F + speed * 3.2F + pulse * 1.2F))
                * (1.0F - vertical * 0.42F)
                * (0.30F + ride * 0.70F)
                * (1.0F - station * 0.36F);

        float turn = (float) turnSignal(pose.signedTurn(), pose.signedTurnPreview(), 0.62D);
        float previewTurn = (float) turnSignal(pose.signedTurn(), pose.signedTurnPreview(), 0.84D);
        float turnAmount = Math.abs(turn);
        float steeringRoll = turn * (9.0F + speed * 8.0F + pulse * 1.6F + turnAmount * 3.0F) * (float) pose.ride().turnLeanScale();
        float anticipationRoll = (previewTurn - turn) * (2.6F + speed * 2.2F) * (float) pose.ride().turnLeanScale();
        float viewRoll = viewBank * (0.72F + speed * 1.10F) * (1.0F - vertical * 0.40F);
        float wallRoll = ((float) frame.descendAmount() - (float) frame.ascendAmount()) * 8.0F * (float) pose.ride().wallRideScale();
        float roll = ((steeringRoll + anticipationRoll + viewRoll)
                * (0.28F + speed * 0.60F)
                * (1.0F - ride * 0.38F)
                * (1.0F - vertical * 0.46F)
                * (1.0F - station * 0.32F)
                + wallRoll) * alpha;
        return new WorldPose((slopePitch + supportPitch + gravityPitch) * alpha, roll);
    }

    static void resetLegRoots(PlayerModel model) {
        model.rightLeg.x = RIGHT_LEG_ROOT_X;
        model.leftLeg.x = LEFT_LEG_ROOT_X;
        model.rightLeg.y = LEG_ROOT_Y;
        model.leftLeg.y = LEG_ROOT_Y;
        model.rightLeg.z = LEG_ROOT_Z;
        model.leftLeg.z = LEG_ROOT_Z;
    }

    static float inlineSupportAmount(ClientSlidePoseController.RidePoseDescriptor ride) {
        return switch (ride.family()) {
            case MONORAIL -> 1.0F;
            case INLINE -> 0.86F;
            default -> 0.0F;
        };
    }

    private static float supportCenterDepth(BalanceState balance) {
        float slopeDepth = balance.slope() * (0.010F + balance.speed() * 0.006F) * (1.0F - balance.vertical() * 0.42F);
        float verticalDepth = balance.vertical() * (0.008F + balance.speed() * 0.004F);
        float turnDepth = balance.turn() * balance.sideStance() * 0.004F * (1.0F - balance.station() * 0.40F);
        return slopeDepth + verticalDepth + turnDepth;
    }

    private static float supportSeparationScale(BalanceState balance, float baseScale) {
        float stability = 1.0F - balance.forwardStability() * 0.22F - balance.vertical() * 0.10F;
        return Mth.clamp(baseScale * stability, 0.58F, 1.08F);
    }

    private static boolean rightLeadSide(float sideStance) {
        return sideStance < 0.0F;
    }

    private static BodyPose solveBody(BalanceState balance) {
        float railCrouch = balance.ride().family() == ClientSlidePoseController.RidePoseFamily.SPLIT_RAIL ? balance.railSpread() * 0.030F : 0.0F;
        float verticalCrouch = balance.vertical() * (0.045F + balance.speed() * 0.032F);
        float wallTension = (balance.ascend() * 0.032F + balance.descend() * 0.046F) * (float) balance.ride().wallRideScale();
        float speedLean = (0.018F + balance.speed() * 0.034F + balance.pulse() * 0.014F)
                * (1.0F - balance.station() * 0.55F)
                * (1.0F - balance.vertical() * 0.72F);
        float slopeLean = balance.slope() * (0.016F + balance.speed() * 0.010F)
                * (1.0F - balance.vertical() * 0.62F)
                * (1.0F - balance.station() * 0.34F);
        float sideYaw = balance.sideStance()
                * (0.018F + balance.speed() * 0.020F + balance.inline() * 0.040F)
                * (1.0F - balance.vertical() * 0.74F);
        float steeringRoll = balance.turn()
                * (0.20F + balance.speed() * 0.10F + balance.turnAmount() * 0.08F)
                * (float) balance.ride().turnLeanScale()
                * (1.0F - balance.vertical() * 0.55F);
        float anticipation = (balance.previewTurn() - balance.turn()) * (0.030F + balance.speed() * 0.032F) * (1.0F - balance.vertical() * 0.50F);
        float bodyPitch = speedLean + slopeLean + railCrouch + verticalCrouch + wallTension + 0.030F * balance.mount();
        float bodyYaw = sideYaw;
        float bodyRoll = steeringRoll + anticipation + balance.descend() * balance.turn() * 0.055F - balance.ascend() * balance.turn() * 0.036F;
        return new BodyPose(bodyPitch * balance.alpha(), bodyYaw * balance.alpha(), bodyRoll * balance.alpha());
    }

    private static HeadPose solveHead(BalanceState balance) {
        float steeringRoll = balance.turn()
                * (0.20F + balance.speed() * 0.10F + balance.turnAmount() * 0.08F)
                * (float) balance.ride().turnLeanScale()
                * (1.0F - balance.vertical() * 0.55F);
        float slopeLean = balance.slope() * (0.016F + balance.speed() * 0.010F)
                * (1.0F - balance.vertical() * 0.62F)
                * (1.0F - balance.station() * 0.34F);
        float headPitch = -(0.046F * balance.speed() * (1.0F - balance.station()) + slopeLean * 0.34F - balance.vertical() * 0.038F);
        float headRoll = steeringRoll * 0.48F;
        return new HeadPose(headPitch * balance.alpha(), headRoll * balance.alpha());
    }

    private static LegPairPose solveLegs(BalanceState balance, BodyPose body) {
        return switch (balance.ride().family()) {
            case SPLIT_RAIL -> solveSplitRailLegs(balance, body);
            case CRADLE -> solveCradleLegs(balance, body);
            case MONORAIL -> solveInlineLegs(balance, body, true);
            default -> balance.inline() > 0.5F ? solveInlineLegs(balance, body, false) : solveWideInlineLegs(balance, body);
        };
    }

    private static LegPairPose solveSplitRailLegs(BalanceState balance, BodyPose body) {
        float railOut = 0.240F + balance.railSpread() * 0.460F;
        float railFront = 0.070F + balance.length01() * 0.050F;
        float railBack = 0.055F + balance.length01() * 0.038F;
        float midlinePull = 0.42F * (0.024F + balance.inline() * 0.020F + balance.vertical() * 0.014F + Math.abs(balance.slope()) * 0.009F);
        float centerDepth = supportCenterDepth(balance) * 0.80F;
        float separation = supportSeparationScale(balance, 0.90F);
        float frontDepth = centerDepth + separation * (0.012F + balance.speed() * 0.003F + balance.downhillBrace() * 0.002F);
        float rearDepth = centerDepth - separation * (0.010F + balance.speed() * 0.002F + balance.uphillPress() * 0.001F);
        float rightX = balance.rightLead() ? -railFront : railBack;
        float leftX = balance.rightLead() ? railBack : -railFront;
        float rightZ = balance.rightLead() ? frontDepth : rearDepth;
        float leftZ = balance.rightLead() ? rearDepth : frontDepth;
        float baseCrouch = balance.crouch() * 0.36F;
        LegPose right = new LegPose(
                (rightX + baseCrouch + balance.descend() * balance.verticalBrace() * 0.55F - balance.ascend() * balance.verticalBrace() * 0.20F) * balance.alpha(),
                (-railOut - balance.footYaw() * 0.24F + balance.turn() * 0.030F + balance.sideStance() * 0.020F) * balance.alpha(),
                (0.145F + balance.railSpread() * 0.220F + balance.sideLoad() * 0.10F + balance.descend() * 0.025F) * balance.alpha(),
                RIGHT_LEG_ROOT_X + midlinePull,
                LEG_ROOT_Y,
                rightZ
        );
        LegPose left = new LegPose(
                (leftX + baseCrouch + balance.ascend() * balance.verticalBrace() * 0.40F - balance.descend() * balance.verticalBrace() * 0.16F) * balance.alpha(),
                (railOut + balance.footYaw() * 0.24F + balance.turn() * 0.030F + balance.sideStance() * 0.020F) * balance.alpha(),
                (-0.145F - balance.railSpread() * 0.220F + balance.sideLoad() * 0.10F - balance.ascend() * 0.020F) * balance.alpha(),
                LEFT_LEG_ROOT_X - midlinePull,
                LEG_ROOT_Y,
                leftZ
        );
        return followBody(balance, body, right, left);
    }

    private static LegPairPose solveCradleLegs(BalanceState balance, BodyPose body) {
        float front = balance.frontReach() * 0.64F;
        float back = balance.backSweep() * 0.62F;
        float midlinePull = 0.82F * (0.024F + balance.inline() * 0.020F + balance.vertical() * 0.014F + Math.abs(balance.slope()) * 0.009F);
        float centerDepth = supportCenterDepth(balance) * 0.88F;
        float separation = supportSeparationScale(balance, 0.92F);
        float frontDepth = centerDepth + separation * (0.011F + balance.speed() * 0.003F + balance.uphillPress() * 0.002F);
        float rearDepth = centerDepth - separation * (0.010F + balance.speed() * 0.002F + balance.downhillBrace() * 0.002F);
        LegPose right = new LegPose(
                ((balance.rightLead() ? -front : back) + balance.crouch() * 0.34F) * balance.alpha(),
                (-0.050F - balance.footYaw() * 0.48F + balance.turn() * 0.030F + balance.sideStance() * 0.025F) * balance.alpha(),
                (0.034F + balance.sideLoad() * 0.12F + (balance.rightLead() ? -0.012F : 0.018F)) * balance.alpha(),
                RIGHT_LEG_ROOT_X + midlinePull,
                LEG_ROOT_Y,
                balance.rightLead() ? frontDepth : rearDepth
        );
        LegPose left = new LegPose(
                ((balance.rightLead() ? back : -front) + balance.crouch() * 0.34F) * balance.alpha(),
                (0.050F + balance.footYaw() * 0.48F + balance.turn() * 0.030F + balance.sideStance() * 0.025F) * balance.alpha(),
                (-0.034F + balance.sideLoad() * 0.12F + (balance.rightLead() ? -0.018F : 0.012F)) * balance.alpha(),
                LEFT_LEG_ROOT_X - midlinePull,
                LEG_ROOT_Y,
                balance.rightLead() ? rearDepth : frontDepth
        );
        return followBody(balance, body, right, left);
    }

    private static LegPairPose solveInlineLegs(BalanceState balance, BodyPose body, boolean monorail) {
        float lineAngle = monorail
                ? 0.430F + balance.inline() * 0.420F + balance.speed() * 0.040F
                : 0.380F + balance.inline() * 0.340F + balance.speed() * 0.036F;
        float lineTurn = -balance.sideStance() * lineAngle;
        float lineRoll = balance.sideStance() * balance.footRoll();
        float midlinePull = (monorail ? 1.08F : 0.98F) * (0.024F + balance.inline() * 0.020F + balance.vertical() * 0.014F + Math.abs(balance.slope()) * 0.009F);
        float centerDepth = supportCenterDepth(balance) * (monorail ? 1.0F : 0.94F);
        float separation = supportSeparationScale(balance, monorail ? 0.98F : 0.92F);
        float frontDepth = centerDepth + separation * ((monorail ? 0.017F : 0.015F) + balance.speed() * 0.004F + balance.uphillPress() * 0.003F - balance.downhillBrace() * 0.002F);
        float rearDepth = centerDepth - separation * ((monorail ? 0.013F : 0.012F) + balance.speed() * 0.003F + balance.downhillBrace() * 0.003F - balance.uphillPress() * 0.001F);
        float frontToe = -balance.sideStance() * (monorail ? 0.054F : 0.048F) * (1.0F + balance.speed() * 0.32F);
        float rearToe = balance.sideStance() * (monorail ? 0.045F : 0.040F) * (1.0F + balance.speed() * 0.28F);
        float front = (monorail ? 0.245F : 0.220F)
                + balance.frontReach() * (monorail ? 0.46F : 0.48F)
                + balance.uphillPress() * (monorail ? 0.210F : 0.190F)
                - balance.downhillBrace() * (monorail ? 0.070F : 0.062F);
        float back = (monorail ? 0.185F : 0.165F)
                + balance.backSweep() * (monorail ? 0.42F : 0.44F)
                + balance.downhillBrace() * (monorail ? 0.180F : 0.160F)
                - balance.uphillPress() * (monorail ? 0.060F : 0.054F);
        float frontLeg = -front + balance.crouch() * (monorail ? 0.18F : 0.20F)
                - balance.downhillBrace() * (monorail ? 0.045F : 0.040F)
                + balance.descend() * balance.verticalBrace() * (monorail ? 0.18F : 0.17F)
                - balance.ascend() * balance.verticalBrace() * 0.10F;
        float rearLeg = back + balance.crouch() * (monorail ? 0.18F : 0.20F)
                + balance.uphillPress() * (monorail ? 0.050F : 0.046F)
                + balance.ascend() * balance.verticalBrace() * 0.12F
                - balance.descend() * balance.verticalBrace() * 0.08F;
        float verticalRollScale = 1.0F - balance.vertical() * 0.45F;
        float frontRollScale = monorail ? 0.52F : 0.50F;
        float rearRollScale = monorail ? 0.34F : 0.32F;
        LegPose right = new LegPose(
                (balance.rightLead() ? frontLeg : rearLeg) * balance.alpha() * (1.0F - balance.forwardStability()),
                (lineTurn + (balance.rightLead() ? frontToe : rearToe) + balance.turn() * 0.020F + balance.sideStance() * (monorail ? 0.160F : 0.135F)) * balance.alpha(),
                (lineRoll * verticalRollScale * (balance.rightLead() ? frontRollScale : rearRollScale)
                        + balance.sideLoad() * (monorail ? 0.16F : 0.15F)
                        + balance.sideStance() * (balance.rightLead() ? -0.014F : 0.018F)
                        + balance.descend() * 0.012F) * balance.alpha(),
                RIGHT_LEG_ROOT_X + midlinePull,
                LEG_ROOT_Y,
                balance.rightLead() ? frontDepth : rearDepth
        );
        LegPose left = new LegPose(
                (balance.rightLead() ? rearLeg : frontLeg) * balance.alpha() * (1.0F - balance.forwardStability()),
                (lineTurn + (balance.rightLead() ? rearToe : frontToe) + balance.turn() * 0.020F + balance.sideStance() * (monorail ? 0.160F : 0.135F)) * balance.alpha(),
                (lineRoll * verticalRollScale * (balance.rightLead() ? rearRollScale : frontRollScale)
                        + balance.sideLoad() * (monorail ? 0.16F : 0.15F)
                        + balance.sideStance() * (balance.rightLead() ? 0.018F : -0.014F)
                        - balance.ascend() * 0.010F) * balance.alpha(),
                LEFT_LEG_ROOT_X - midlinePull,
                LEG_ROOT_Y,
                balance.rightLead() ? rearDepth : frontDepth
        );
        return followBody(balance, body, right, left);
    }

    private static LegPairPose solveWideInlineLegs(BalanceState balance, BodyPose body) {
        float front = balance.frontReach();
        float back = balance.backSweep();
        float midlinePull = 1.02F * (0.024F + balance.inline() * 0.020F + balance.vertical() * 0.014F + Math.abs(balance.slope()) * 0.009F);
        float centerDepth = supportCenterDepth(balance) * 0.96F;
        float separation = supportSeparationScale(balance, 0.96F);
        float frontDepth = centerDepth + separation * (0.014F + balance.speed() * 0.004F + balance.uphillPress() * 0.003F - balance.downhillBrace() * 0.002F);
        float rearDepth = centerDepth - separation * (0.012F + balance.speed() * 0.003F + balance.downhillBrace() * 0.003F - balance.uphillPress() * 0.001F);
        LegPose right = new LegPose(
                ((balance.rightLead() ? -front : back) + balance.crouch() * 0.30F + balance.descend() * balance.verticalBrace() * 0.36F - balance.ascend() * balance.verticalBrace() * 0.18F) * balance.alpha() * (1.0F - balance.forwardStability()),
                (-0.040F - balance.footYaw() + balance.turn() * 0.034F + balance.sideStance() * 0.048F) * balance.alpha(),
                (balance.footRoll() * (1.0F - balance.vertical() * 0.45F) + balance.sideLoad() * 0.13F + (balance.rightLead() ? -0.018F : 0.024F) + balance.descend() * 0.018F) * balance.alpha(),
                RIGHT_LEG_ROOT_X + midlinePull,
                LEG_ROOT_Y,
                balance.rightLead() ? frontDepth : rearDepth
        );
        LegPose left = new LegPose(
                ((balance.rightLead() ? back : -front) + balance.crouch() * 0.30F + balance.ascend() * balance.verticalBrace() * 0.24F - balance.descend() * balance.verticalBrace() * 0.14F) * balance.alpha() * (1.0F - balance.forwardStability()),
                (0.040F + balance.footYaw() + balance.turn() * 0.034F + balance.sideStance() * 0.048F) * balance.alpha(),
                (-balance.footRoll() * (1.0F - balance.vertical() * 0.45F) + balance.sideLoad() * 0.13F + (balance.rightLead() ? -0.024F : 0.018F) - balance.ascend() * 0.014F) * balance.alpha(),
                LEFT_LEG_ROOT_X - midlinePull,
                LEG_ROOT_Y,
                balance.rightLead() ? rearDepth : frontDepth
        );
        return followBody(balance, body, right, left);
    }

    private static LegPairPose followBody(BalanceState balance, BodyPose body, LegPose right, LegPose left) {
        if (Math.abs(body.xRot()) + Math.abs(body.yRot()) + Math.abs(body.zRot()) < 1.0E-5F) {
            return new LegPairPose(right, left);
        }
        Quaternionf rootRotation = new Quaternionf().rotationZYX(body.zRot(), body.yRot(), body.xRot());
        Vector3f rightRoot = new Vector3f(right.x, right.y, right.z).rotate(rootRotation);
        Vector3f leftRoot = new Vector3f(left.x, left.y, left.z).rotate(rootRotation);
        float rotationFollow = (0.84F + balance.inline() * 0.08F + balance.railSpread() * 0.04F + balance.vertical() * 0.04F) * (1.0F - balance.station() * 0.12F);
        LegPose followedRight = new LegPose(
                right.xRot() + body.xRot() * rotationFollow,
                right.yRot() + body.yRot() * rotationFollow,
                right.zRot() + body.zRot() * rotationFollow,
                rightRoot.x,
                rightRoot.y,
                rightRoot.z
        );
        LegPose followedLeft = new LegPose(
                left.xRot() + body.xRot() * rotationFollow,
                left.yRot() + body.yRot() * rotationFollow,
                left.zRot() + body.zRot() * rotationFollow,
                leftRoot.x,
                leftRoot.y,
                leftRoot.z
        );
        return new LegPairPose(followedRight, followedLeft);
    }

    private static ArmPairPose solveArms(BalanceState balance) {
        float armFloat = (float) Math.cos(balance.phase()) * (0.003F + balance.speed() * 0.004F) * (1.0F - balance.station() * 0.50F);
        float turnAmount = Math.abs(balance.previewTurn());
        float armSpread = (float) balance.ride().armBaseSpread()
                + balance.speed() * 0.48F
                + balance.pulse() * 0.22F
                + balance.turnAmount() * 0.18F
                - balance.station() * 0.20F;
        float armOpen = (0.165F + armSpread * 0.48F + balance.vertical() * 0.070F + balance.descend() * 0.060F + turnAmount * 0.080F)
                * (1.0F - balance.station() * 0.24F);
        float forwardReach = 0.560F + balance.speed() * 0.260F + balance.pulse() * 0.110F + turnAmount * 0.100F + balance.ascend() * 0.055F - balance.station() * 0.050F;
        float backSweep = 0.240F + balance.speed() * 0.220F + balance.pulse() * 0.080F + turnAmount * 0.090F + balance.descend() * 0.050F;
        float outside = Math.signum(balance.previewTurn() == 0.0F ? balance.sideStance() : balance.previewTurn());
        float rightOutside = outside > 0.0F ? 1.0F : 0.0F;
        float leftOutside = outside < 0.0F ? 1.0F : 0.0F;
        float rightForward = balance.rightLead() ? 1.0F : 0.0F;
        float leftForward = balance.rightLead() ? 0.0F : 1.0F;
        float slopeArm = balance.slope() * (0.070F + balance.speed() * 0.034F) * (1.0F - balance.vertical() * 0.48F);
        ArmPose right = new ArmPose(
                ((balance.rightLead() ? -forwardReach : backSweep)
                        - slopeArm * 0.52F
                        + armFloat
                        - rightOutside * turnAmount * 0.135F
                        + rightForward * turnAmount * 0.040F) * balance.alpha(),
                (-0.060F - balance.speed() * 0.040F + balance.previewTurn() * 0.125F - balance.sideStance() * 0.048F + balance.descend() * 0.026F) * balance.alpha(),
                (armOpen + rightOutside * turnAmount * 0.210F - rightForward * 0.045F + balance.sideLoad() * 0.135F) * balance.alpha()
        );
        ArmPose left = new ArmPose(
                ((balance.rightLead() ? backSweep : -forwardReach)
                        - slopeArm * 0.52F
                        - armFloat
                        - leftOutside * turnAmount * 0.135F
                        + leftForward * turnAmount * 0.040F) * balance.alpha(),
                (0.060F + balance.speed() * 0.040F + balance.previewTurn() * 0.125F - balance.sideStance() * 0.048F - balance.ascend() * 0.020F) * balance.alpha(),
                (-armOpen - leftOutside * turnAmount * 0.210F + leftForward * 0.045F + balance.sideLoad() * 0.135F) * balance.alpha()
        );
        return new ArmPairPose(right, left);
    }

    private static double turnSignal(double signedTurn, double signedTurnPreview, double previewWeight) {
        return Mth.clamp(signedTurn + signedTurnPreview * previewWeight, -1.0D, 1.0D);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    record WorldPose(float pitchDegrees, float rollDegrees) {
    }

    record ModelPose(BodyPose body, HeadPose head, LegPose rightLeg, LegPose leftLeg, ArmPose rightArm, ArmPose leftArm) {
        void apply(PlayerModel model) {
            model.body.xRot += this.body.xRot();
            model.body.yRot += this.body.yRot();
            model.body.zRot += this.body.zRot();
            model.head.xRot += this.head.xRot();
            model.head.zRot += this.head.zRot();
            this.rightLeg.applyToRight(model);
            this.leftLeg.applyToLeft(model);
            this.rightArm.applyToRight(model);
            this.leftArm.applyToLeft(model);
        }
    }

    private record BodyPose(float xRot, float yRot, float zRot) {
    }

    private record HeadPose(float xRot, float zRot) {
    }

    private record LegPairPose(LegPose right, LegPose left) {
    }

    private record ArmPairPose(ArmPose right, ArmPose left) {
    }

    private record LegPose(float xRot, float yRot, float zRot, float x, float y, float z) {
        private void applyToRight(PlayerModel model) {
            model.rightLeg.xRot = this.xRot;
            model.rightLeg.yRot = this.yRot;
            model.rightLeg.zRot = this.zRot;
            model.rightLeg.x = this.x;
            model.rightLeg.y = this.y;
            model.rightLeg.z = this.z;
        }

        private void applyToLeft(PlayerModel model) {
            model.leftLeg.xRot = this.xRot;
            model.leftLeg.yRot = this.yRot;
            model.leftLeg.zRot = this.zRot;
            model.leftLeg.x = this.x;
            model.leftLeg.y = this.y;
            model.leftLeg.z = this.z;
        }
    }

    private record ArmPose(float xRot, float yRot, float zRot) {
        private void applyToRight(PlayerModel model) {
            model.rightArm.xRot = this.xRot;
            model.rightArm.yRot = this.yRot;
            model.rightArm.zRot = this.zRot;
        }

        private void applyToLeft(PlayerModel model) {
            model.leftArm.xRot = this.xRot;
            model.leftArm.yRot = this.yRot;
            model.leftArm.zRot = this.zRot;
        }
    }

    private record BalanceState(
            ClientSlidePoseController.RidePoseDescriptor ride,
            float alpha,
            float speed,
            float pulse,
            float station,
            float vertical,
            float ascend,
            float descend,
            float slope,
            float mount,
            float phase,
            float inline,
            float turn,
            float previewTurn,
            float turnAmount,
            float sideStance,
            boolean rightLead,
            float railSpread,
            float length01,
            float sideLoad,
            float crouch,
            float frontReach,
            float backSweep,
            float footYaw,
            float footRoll,
            float uphillPress,
            float downhillBrace,
            float verticalBrace,
            float forwardStability
    ) {
        private static BalanceState of(ClientSlidePoseController.PoseSnapshot pose, ClientSlidePoseController.SlidePoseFrame frame, float sideStance) {
            ClientSlidePoseController.RidePoseDescriptor ride = pose.ride();
            float alpha = (float) Mth.clamp(pose.poseAlpha(), 0.0D, 1.0D);
            float speed = (float) Mth.clamp(pose.perceptualSpeed(), 0.0D, 1.0D);
            float pulse = (float) Mth.clamp(pose.accelerationPulse(), 0.0D, 1.0D);
            float station = (float) Mth.clamp(pose.platformBlend(), 0.0D, 1.0D);
            float vertical = (float) Mth.clamp(frame.verticalAmount(), 0.0D, 1.0D);
            float ascend = (float) Mth.clamp(frame.ascendAmount(), 0.0D, 1.0D);
            float descend = (float) Mth.clamp(frame.descendAmount(), 0.0D, 1.0D);
            float slope = (float) Mth.clamp(pose.tangent().y, -0.92D, 0.92D);
            float mount = (float) Mth.clamp(pose.mountProgress(), 0.0D, 1.0D);
            float inline = inlineSupportAmount(ride);
            float turn = (float) Mth.clamp(turnSignal(pose.signedTurn(), pose.signedTurnPreview(), 0.58D), -1.0D, 1.0D);
            float previewTurn = (float) Mth.clamp(turnSignal(pose.signedTurn(), pose.signedTurnPreview(), 0.84D), -1.0D, 1.0D);
            float turnAmount = Math.abs(turn);
            float dynamic = (0.45F + speed * 0.55F + pulse * 0.22F) * (1.0F - station * 0.35F);
            float knee = (float) ride.kneeBend() * (0.72F + dynamic * 0.18F) + speed * 0.110F + pulse * 0.070F + turnAmount * 0.045F - station * 0.060F;
            float stanceLengthPx = (float) Mth.clamp(ride.stanceLength() * 16.0D, 1.0D, 6.4D);
            float length01 = (float) Mth.clamp((stanceLengthPx - 1.0F) / 5.4F, 0.0F, 1.0F);
            float slopeLoad = (float) Mth.clamp(slope, -0.85F, 0.85F) * (1.0F - vertical * 0.45F);
            float uphillPress = Math.max(0.0F, slopeLoad);
            float downhillBrace = Math.max(0.0F, -slopeLoad);
            float uphillStability = Math.max((float) smoothstep(0.04D, 0.50D, Math.max(0.0F, slope)), (float) pose.upBlend() * 0.86F);
            return new BalanceState(
                    ride,
                    alpha,
                    speed,
                    pulse,
                    station,
                    vertical,
                    ascend,
                    descend,
                    slope,
                    mount,
                    (float) (pose.motionPhase() * Mth.TWO_PI),
                    inline,
                    turn,
                    previewTurn,
                    turnAmount,
                    sideStance,
                    rightLeadSide(sideStance),
                    (float) Mth.clamp(ride.railSpread(), 0.0D, 1.0D),
                    length01,
                    (previewTurn - turn * 0.35F) * (0.026F + speed * 0.030F) * (float) ride.balanceScale() * (1.0F - station * 0.45F),
                    Math.max(0.0F, knee) * 0.18F + speed * 0.020F + pulse * 0.012F,
                    (0.200F + length01 * 0.120F + speed * 0.055F - station * 0.030F) * (1.0F - vertical * 0.42F),
                    (0.140F + length01 * 0.090F + speed * 0.040F + pulse * 0.022F) * (1.0F - vertical * 0.46F),
                    0.055F + speed * 0.035F,
                    0.030F + speed * 0.026F,
                    uphillPress,
                    downhillBrace,
                    vertical * (0.040F + speed * 0.026F),
                    0.08F + uphillStability * 0.16F + vertical * 0.08F
            );
        }
    }
}
