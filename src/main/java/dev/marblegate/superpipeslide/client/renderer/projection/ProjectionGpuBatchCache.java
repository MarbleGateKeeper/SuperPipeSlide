package dev.marblegate.superpipeslide.client.renderer.projection;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.marblegate.superpipeslide.client.renderer.ClientRenderCompatibility;
import dev.marblegate.superpipeslide.mixin.client.RenderSetupAccessor;
import dev.marblegate.superpipeslide.mixin.client.RenderTypeAccessor;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

final class ProjectionGpuBatchCache {
    private static final int INITIAL_BYTES = 4096;

    private ProjectionGpuBatchCache() {}

    static GpuBatches compile(Consumer<SubmitNodeCollector> recorder) {
        RecordingCollector collector = new RecordingCollector();
        recorder.accept(collector);
        return collector.upload();
    }

    static final class GpuBatches {
        private final List<GpuBatch> batches;
        private final String renderStateKey;

        private GpuBatches(List<GpuBatch> batches, String renderStateKey) {
            this.batches = batches;
            this.renderStateKey = renderStateKey;
        }

        boolean empty() {
            return this.batches.isEmpty();
        }

        boolean validForCurrentRenderState() {
            return this.renderStateKey.equals(ClientRenderCompatibility.renderStateKey());
        }

        void draw() {
            for (GpuBatch batch : this.batches) {
                batch.draw();
            }
        }

        void release() {
            for (GpuBatch batch : this.batches) {
                batch.release();
            }
        }
    }

    private static final class RecordingCollector implements SubmitNodeCollector {
        private final Map<RenderType, GeometryBuffer> buffers = new LinkedHashMap<>();

        @Override
        public SubmitNodeCollector order(int order) {
            return this;
        }

        @Override
        public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer customGeometryRenderer) {
            GeometryBuffer geometry = this.buffers.computeIfAbsent(ClientRenderCompatibility.world(renderType), GeometryBuffer::new);
            customGeometryRenderer.render(poseStack.last(), geometry.builder());
        }

        GpuBatches upload() {
            if (this.buffers.isEmpty()) {
                return new GpuBatches(List.of(), ClientRenderCompatibility.renderStateKey());
            }
            List<GpuBatch> uploaded = new ArrayList<>();
            for (GeometryBuffer buffer : this.buffers.values()) {
                GpuBatch batch = buffer.upload();
                if (batch != null) {
                    uploaded.add(batch);
                }
            }
            this.closeBuilders();
            return new GpuBatches(List.copyOf(uploaded), ClientRenderCompatibility.renderStateKey());
        }

        private void closeBuilders() {
            for (GeometryBuffer buffer : this.buffers.values()) {
                buffer.close();
            }
        }

        @Override
        public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow, net.minecraft.client.gui.Font.DisplayMode displayMode, int lightCoords, int color, int backgroundColor, int outlineColor) {
            Font font = Minecraft.getInstance().font;
            MultiBufferSource bufferSource = new RecordingBufferSource(this.buffers);
            if (outlineColor == 0) {
                font.drawInBatch(string, x, y, color, dropShadow, poseStack.last().pose(), bufferSource, displayMode, backgroundColor, lightCoords);
                return;
            }
            font.drawInBatch8xOutline(string, x, y, color, outlineColor, poseStack.last().pose(), bufferSource, lightCoords);
        }

        @Override
        public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {}

        @Override
        public void submitNameTag(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough, int lightCoords, double distanceToCameraSq, CameraRenderState camera) {}

        @Override
        public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {}

        @Override
        public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {}

        @Override
        public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords, int tintedColor, @Nullable TextureAtlasSprite sprite, int outlineColor, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {}

        @Override
        public void submitModelPart(ModelPart modelPart, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords, @Nullable TextureAtlasSprite sprite, boolean sheeted, boolean hasFoil, int tintedColor, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay, int outlineColor) {}

        @Override
        public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState) {}

        @Override
        public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {}

        @Override
        public void submitBreakingBlockModel(PoseStack poseStack, BlockStateModel model, long seed, int progress) {}

        @Override
        public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor, int[] tintLayers, List<BakedQuad> quads, ItemStackRenderState.FoilType foilType) {}

        @Override
        public void submitParticleGroup(ParticleGroupRenderer particleGroupRenderer) {}
    }

    private static final class RecordingBufferSource implements MultiBufferSource {
        private final Map<RenderType, GeometryBuffer> buffers;

        private RecordingBufferSource(Map<RenderType, GeometryBuffer> buffers) {
            this.buffers = buffers;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return this.buffers.computeIfAbsent(ClientRenderCompatibility.world(renderType), GeometryBuffer::new).builder();
        }
    }

    private static final class GeometryBuffer implements AutoCloseable {
        private final RenderType renderType;
        private final ByteBufferBuilder bytes;
        private final BufferBuilder builder;

        private GeometryBuffer(RenderType renderType) {
            this.renderType = renderType;
            this.bytes = new ByteBufferBuilder(Math.max(INITIAL_BYTES, renderType.bufferSize()));
            this.builder = new BufferBuilder(this.bytes, renderType.mode(), renderType.format());
        }

        VertexConsumer builder() {
            return this.builder;
        }

        @Nullable
        GpuBatch upload() {
            MeshData mesh = this.builder.build();
            if (mesh == null) {
                return null;
            }
            try {
                ByteBuffer vertices = mesh.vertexBuffer();
                GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "SuperPipeSlide projection " + this.renderType,
                        GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_VERTEX,
                        vertices);
                return new GpuBatch(this.renderType, vertexBuffer, mesh.drawState().indexCount());
            } finally {
                mesh.close();
            }
        }

        @Override
        public void close() {
            this.bytes.close();
        }
    }

    private static final class GpuBatch {
        private final RenderType renderType;
        private final GpuBuffer vertexBuffer;
        private final int indexCount;

        private GpuBatch(RenderType renderType, GpuBuffer vertexBuffer, int indexCount) {
            this.renderType = renderType;
            this.vertexBuffer = vertexBuffer;
            this.indexCount = indexCount;
        }

        void draw() {
            if (this.vertexBuffer.isClosed() || this.indexCount <= 0) {
                return;
            }
            RenderSetup renderSetup = ((RenderTypeAccessor) this.renderType).superpipeslide$state();
            RenderSetupAccessor renderSetupAccessor = (RenderSetupAccessor) (Object) renderSetup;
            Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
            Consumer<Matrix4fStack> layeringModifier = renderSetupAccessor.superpipeslide$layeringTransform().getModifier();
            boolean pushedLayer = layeringModifier != null;
            if (pushedLayer) {
                modelViewStack.pushMatrix();
                layeringModifier.accept(modelViewStack);
            }
            try {
                TextureTransform textureTransform = renderSetupAccessor.superpipeslide$textureTransform();
                GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                        RenderSystem.getModelViewMatrix(),
                        new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
                        new Vector3f(),
                        textureTransform.getMatrix());
                RenderTarget target = this.renderType.outputTarget().getRenderTarget();
                var colorTexture = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
                var depthTexture = target.useDepth
                        ? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView())
                        : null;
                CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
                try (RenderPass renderPass = encoder.createRenderPass(
                        () -> "SuperPipeSlide projection " + this.renderType,
                        colorTexture,
                        OptionalInt.empty(),
                        depthTexture,
                        OptionalDouble.empty())) {
                    renderPass.setPipeline(this.renderType.pipeline());
                    ScissorState scissorState = RenderSystem.getScissorStateForRenderTypeDraws();
                    if (scissorState.enabled()) {
                        renderPass.enableScissor(scissorState.x(), scissorState.y(), scissorState.width(), scissorState.height());
                    }
                    RenderSystem.bindDefaultUniforms(renderPass);
                    renderPass.setUniform("DynamicTransforms", dynamicTransforms);
                    renderPass.setVertexBuffer(0, this.vertexBuffer);
                    for (Map.Entry<String, RenderSetup.TextureAndSampler> entry : renderSetup.getTextures().entrySet()) {
                        renderPass.bindTexture(entry.getKey(), entry.getValue().textureView(), entry.getValue().sampler());
                    }
                    RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(this.renderType.mode());
                    renderPass.setIndexBuffer(indexBuffer.getBuffer(this.indexCount), indexBuffer.type());
                    renderPass.drawIndexed(0, 0, this.indexCount, 1);
                }
            } finally {
                if (pushedLayer) {
                    modelViewStack.popMatrix();
                }
            }
        }

        void release() {
            if (!this.vertexBuffer.isClosed()) {
                this.vertexBuffer.close();
            }
        }
    }
}
