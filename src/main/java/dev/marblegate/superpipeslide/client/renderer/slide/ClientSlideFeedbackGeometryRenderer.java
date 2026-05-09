package dev.marblegate.superpipeslide.client.renderer.slide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.marblegate.superpipeslide.client.core.fold.ClientFoldTraversalEffectController;
import dev.marblegate.superpipeslide.client.core.slide.ClientSlideFeedbackController;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.config.ClientConfig;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class ClientSlideFeedbackGeometryRenderer {
    private static final ContextKey<RenderData> RENDER_DATA = new ContextKey<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "slide_feedback_geometry"));
    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final int TRAIL_SPARK = 1;
    private static final int TRAIL_SIDE_STREAM = 2;
    private static final int SPEED_LINE_COUNT = 80;
    private static final float MIN_LINE_LENGTH = 1.10F;
    private static final float MAX_LINE_LENGTH = 6.65F;
    private static final float[] LINE_ANGLES = new float[SPEED_LINE_COUNT];
    private static final float[] LINE_LENGTHS = new float[SPEED_LINE_COUNT];
    private static final float[] LINE_OFFSETS = new float[SPEED_LINE_COUNT];
    private static final float[] LINE_RADII = new float[SPEED_LINE_COUNT];

    static {
        Random random = new Random(0x51A6E1D5L);
        for (int i = 0; i < SPEED_LINE_COUNT; i++) {
            LINE_ANGLES[i] = random.nextFloat() * Mth.TWO_PI;
            LINE_LENGTHS[i] = MIN_LINE_LENGTH + random.nextFloat() * (MAX_LINE_LENGTH - MIN_LINE_LENGTH);
            LINE_OFFSETS[i] = random.nextFloat();
            LINE_RADII[i] = 1.35F + random.nextFloat() * 4.85F;
        }
    }

    private ClientSlideFeedbackGeometryRenderer() {
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        List<ClientSlideFeedbackController.TrailParticleSnapshot> particles = ClientSlideFeedbackController.trailParticles();
        Optional<SpeedLineField> speedLines = ClientConfig.ENABLE_SLIDE_SPEED_LINES.get()
                ? ClientSlideFeedbackController.currentFrame()
                .filter(frame -> frame.alpha() > 0.04D && frame.speed01() > 0.06D)
                .map(frame -> speedLineField(event, frame))
                : Optional.empty();
        event.getRenderState().setRenderData(RENDER_DATA, new RenderData(particles, speedLines));
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        RenderData renderData = event.getLevelRenderState().getRenderData(RENDER_DATA);
        if (renderData == null || renderData.isEmpty()) {
            return;
        }

        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        renderData.speedLines().ifPresent(field ->
                event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, buffer) -> renderSpeedLines(pose, buffer, field)));
        if (!renderData.particles().isEmpty()) {
            event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
                for (ClientSlideFeedbackController.TrailParticleSnapshot particle : renderData.particles()) {
                    renderParticle(pose, buffer, particle, camera);
                }
            });
        }
        poseStack.popPose();
    }

    private static SpeedLineField speedLineField(ExtractLevelRenderStateEvent event, ClientSlideFeedbackController.Frame frame) {
        Vec3 center = event.getCamera().isDetached()
                ? frame.position().add(0.0D, 0.82D * (1.0D - frame.verticalBlend()), 0.0D)
                : event.getCamera().position();
        double foldMultiplier = ClientFoldTraversalEffectController.speedLineMultiplier();
        return new SpeedLineField(
                center,
                safeNormalize(frame.tangent(), new Vec3(0.0D, 0.0D, 1.0D)),
                Mth.clamp(frame.edgeIntensity() * foldMultiplier, 0.0D, 1.0D),
                Mth.clamp(frame.perceptualSpeed() * (0.58D + foldMultiplier * 0.42D), 0.0D, 1.0D),
                Mth.clamp(frame.accelerationPulse(), 0.0D, 1.0D),
                Mth.clamp(frame.highwayBlend(), 0.0D, 1.0D),
                Mth.clamp(frame.turnBlend(), 0.0D, 1.0D),
                frame.motionPhase(),
                speedLineColor(frame)
        );
    }

    private static void renderSpeedLines(PoseStack.Pose pose, VertexConsumer buffer, SpeedLineField field) {
        Vec3 tangent = safeNormalize(field.tangent(), new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 right = safeNormalize(tangent.cross(Math.abs(tangent.y) > 0.88D ? new Vec3(0.0D, 0.0D, 1.0D) : WORLD_UP), new Vec3(1.0D, 0.0D, 0.0D));
        Vec3 up = safeNormalize(right.cross(tangent), WORLD_UP);
        double time = System.nanoTime() / 1_000_000_000.0D;
        double speed = field.perceptualSpeed();
        double flowSpeed = 0.90D + speed * 4.70D + field.accelerationPulse() * 2.25D + field.highwayBlend() * 0.95D;
        int lineCount = Mth.clamp((int) Math.round(10.0D + speed * 54.0D + field.accelerationPulse() * 10.0D + field.highwayBlend() * 8.0D), 8, SPEED_LINE_COUNT);
        int color = field.color();
        int r = color >>> 16 & 0xFF;
        int g = color >>> 8 & 0xFF;
        int b = color & 0xFF;
        Matrix4f matrix = pose.pose();
        for (int i = 0; i < lineCount; i++) {
            float t = (float) ((field.motionPhase() + time * flowSpeed * 0.18D + LINE_OFFSETS[i]) % 1.0D);
            float alpha = Mth.clamp((float) Math.pow(1.0F - t, 1.18D) * (0.28F + (float) speed * 0.72F + (float) field.accelerationPulse() * 0.26F), 0.0F, 0.92F);
            if (alpha < 0.012F) {
                continue;
            }

            double radiusScale = 1.06D - speed * 0.18D + field.turnBlend() * 0.08D;
            double radius = LINE_RADII[i] * radiusScale;
            double angle = LINE_ANGLES[i];
            Vec3 radial = right.scale(Math.cos(angle) * radius).add(up.scale(Math.sin(angle) * radius));
            double along = Mth.lerp(t, 7.4F + speed * 4.2F + field.accelerationPulse() * 1.6F, -3.8F - speed * 2.0F);
            double halfLength = LINE_LENGTHS[i] * (0.38D + speed * 0.54D + field.accelerationPulse() * 0.20D);
            Vec3 center = field.center().add(radial).add(tangent.scale(along));
            Vec3 a = center.subtract(tangent.scale(halfLength));
            Vec3 c = center.add(tangent.scale(halfLength));
            int a0 = (int) (alpha * field.intensity() * 220.0F);
            if (a0 <= 2) {
                continue;
            }
            float lineWidth = (float) (1.15D + speed * 1.85D + field.accelerationPulse() * 0.70D + field.highwayBlend() * 0.45D);
            buffer.addVertex(matrix, (float) a.x, (float) a.y, (float) a.z)
                    .setColor(r, g, b, a0)
                    .setNormal(pose, (float) tangent.x, (float) tangent.y, (float) tangent.z)
                    .setLineWidth(lineWidth);
            buffer.addVertex(matrix, (float) c.x, (float) c.y, (float) c.z)
                    .setColor(r, g, b, 0)
                    .setNormal(pose, (float) tangent.x, (float) tangent.y, (float) tangent.z)
                    .setLineWidth(lineWidth);
        }
    }

    private static void renderParticle(PoseStack.Pose pose, VertexConsumer buffer, ClientSlideFeedbackController.TrailParticleSnapshot particle, Vec3 camera) {
        Vec3 normal = safeNormalize(camera.subtract(particle.position()), new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 direction = safeNormalize(particle.direction(), new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 widthAxis = safeNormalize(direction.cross(normal), new Vec3(1.0D, 0.0D, 0.0D));
        if (widthAxis.lengthSqr() < 1.0E-8D) {
            Vec3 upHint = Math.abs(normal.dot(WORLD_UP)) > 0.92D ? new Vec3(0.0D, 0.0D, 1.0D) : WORLD_UP;
            widthAxis = safeNormalize(normal.cross(upHint), new Vec3(1.0D, 0.0D, 0.0D));
        }
        double life = 1.0D - particle.age01();
        double width = particle.width();
        double length = particle.length();
        if (particle.kind() == TRAIL_SPARK) {
            length *= 0.70D + life * 0.30D;
            width *= 0.95D;
        } else if (particle.kind() == TRAIL_SIDE_STREAM) {
            length *= 1.08D;
            width *= 0.72D + life * 0.22D;
        }
        Vec3 along = direction.scale(length * 0.5D);
        Vec3 side = widthAxis.scale(width);
        Vec3 a = particle.position().subtract(along).subtract(side);
        Vec3 b = particle.position().subtract(along).add(side);
        Vec3 c = particle.position().add(along).add(side.scale(0.68D));
        Vec3 d = particle.position().add(along).subtract(side.scale(0.68D));
        addVertex(pose, buffer, a, particle.color());
        addVertex(pose, buffer, b, particle.color());
        addVertex(pose, buffer, c, particle.color());
        addVertex(pose, buffer, d, particle.color());
    }

    private static void addVertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 point, int color) {
        buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(color);
    }

    private static Vec3 safeNormalize(Vec3 value, Vec3 fallback) {
        return value.lengthSqr() < 1.0E-8D ? fallback : value.normalize();
    }

    private static int speedLineColor(ClientSlideFeedbackController.Frame frame) {
        if (frame.platformBlend() > 0.36D) {
            return 0xFFE7B85F;
        }
        if (frame.accelerationBlend() > 0.46D) {
            return 0xFFFFB14A;
        }
        if (frame.highwayBlend() > 0.42D) {
            return 0xFF82EFFF;
        }
        if (frame.verticalBlend() > 0.56D) {
            return frame.upBlend() >= frame.downBlend() ? 0xFFA5F8FF : 0xFFB8D6FF;
        }
        return 0xFFDCEFFF;
    }

    private record RenderData(List<ClientSlideFeedbackController.TrailParticleSnapshot> particles, Optional<SpeedLineField> speedLines) {
        private boolean isEmpty() {
            return this.particles.isEmpty() && this.speedLines.isEmpty();
        }
    }

    private record SpeedLineField(Vec3 center, Vec3 tangent, double intensity, double perceptualSpeed, double accelerationPulse, double highwayBlend, double turnBlend, double motionPhase, int color) {
    }
}
