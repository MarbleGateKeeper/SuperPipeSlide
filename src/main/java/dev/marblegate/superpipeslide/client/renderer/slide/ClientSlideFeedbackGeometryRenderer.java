package dev.marblegate.superpipeslide.client.renderer.slide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.marblegate.superpipeslide.client.core.accessibility.ClientSafetyOptions;
import dev.marblegate.superpipeslide.client.core.slide.ClientSlideFeedbackController;
import dev.marblegate.superpipeslide.client.renderer.ClientRenderCompatibility;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import java.util.List;
import java.util.Random;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

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

    private ClientSlideFeedbackGeometryRenderer() {}

    public static void extract(ExtractLevelRenderStateEvent event) {
        if (ClientSafetyOptions.reduceMotionSicknessRisk() || ClientSafetyOptions.reducePhotosensitivityRisk()) {
            event.getRenderState().setRenderData(RENDER_DATA, new RenderData(List.of()));
            return;
        }
        List<ClientSlideFeedbackController.TrailParticleSnapshot> particles = ClientSlideFeedbackController.trailParticles();
        event.getRenderState().setRenderData(RENDER_DATA, new RenderData(particles));
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
        if (!renderData.particles().isEmpty()) {
            ClientRenderCompatibility.submitCustomGeometry(event.getSubmitNodeCollector(), poseStack, RenderTypes.lightning(), (pose, buffer) -> {
                for (ClientSlideFeedbackController.TrailParticleSnapshot particle : renderData.particles()) {
                    renderParticle(pose, buffer, particle, camera);
                }
            });
        }
        poseStack.popPose();
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

    private record RenderData(List<ClientSlideFeedbackController.TrailParticleSnapshot> particles) {
        private boolean isEmpty() {
            return this.particles.isEmpty();
        }
    }
}
