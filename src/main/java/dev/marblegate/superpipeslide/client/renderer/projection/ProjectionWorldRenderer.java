package dev.marblegate.superpipeslide.client.renderer.projection;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.marblegate.superpipeslide.client.core.projection.render.ProjectionRenderFrameContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class ProjectionWorldRenderer {
    private ProjectionWorldRenderer() {
    }

    public static void renderAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        boolean hasStationProjectors = StationNameProjectorRenderer.hasQueuedProjectors();
        boolean hasPlatformProjectors = PlatformProjectorRenderer.hasQueuedProjectors();
        if (!hasStationProjectors && !hasPlatformProjectors) {
            return;
        }

        Vec3 camera = hasStationProjectors ? StationNameProjectorRenderer.queuedCamera() : PlatformProjectorRenderer.queuedCamera();
        Font font = Minecraft.getInstance().font;
        PoseStack poseStack = new PoseStack();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(event.getModelViewMatrix());
        modelViewStack.translate((float) -camera.x, (float) -camera.y, (float) -camera.z);
        try (ByteBufferBuilder buffer = new ByteBufferBuilder(RenderType.BIG_BUFFER_SIZE)) {
            ProjectionRenderFrameContext.begin(System.currentTimeMillis());
            MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(buffer);
            ImmediateSubmitNodeCollector collector = new ImmediateSubmitNodeCollector(bufferSource);
            StationNameProjectorRenderer.submitQueued(collector, poseStack, font, camera);
            PlatformProjectorRenderer.submitQueued(collector, poseStack, font, camera);
            bufferSource.endBatch();
        } finally {
            ProjectionRenderFrameContext.end();
            modelViewStack.popMatrix();
        }
    }
}
