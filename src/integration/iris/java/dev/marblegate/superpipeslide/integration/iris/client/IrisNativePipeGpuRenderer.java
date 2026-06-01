package dev.marblegate.superpipeslide.integration.iris.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import dev.marblegate.superpipeslide.client.core.accessibility.ClientSafetyOptions;
import dev.marblegate.superpipeslide.client.renderer.ClientRenderCompatibility;
import dev.marblegate.superpipeslide.client.renderer.pipe.ClientPipeRenderer;
import dev.marblegate.superpipeslide.client.renderer.pipe.ClientPipeRenderer.LitTexturedQuad;
import dev.marblegate.superpipeslide.client.renderer.pipe.ClientPipeRenderer.PipeLitRenderBatches;
import dev.marblegate.superpipeslide.client.renderer.pipe.ClientPipeRenderer.RenderSectionKey;
import dev.marblegate.superpipeslide.client.renderer.pipe.ClientPipeRenderer.ShaderpackEntityRenderContext;
import dev.marblegate.superpipeslide.client.renderer.pipe.ClientPipeRenderer.ShaderpackEntitySection;
import dev.marblegate.superpipeslide.client.renderer.pipe.ClientPipeRenderer.ShaderpackEntityShadowContext;
import dev.marblegate.superpipeslide.client.renderer.pipe.ClientPipeRenderer.TexturedQuad;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.mixin.client.RenderSetupAccessor;
import dev.marblegate.superpipeslide.mixin.client.RenderTypeAccessor;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

final class IrisNativePipeGpuRenderer {
    private static final int GPU_BATCH_INITIAL_BYTES = 4096;
    private static final Map<RenderSectionKey, SectionGpuCache> SECTION_CACHES = new LinkedHashMap<>();
    @Nullable
    private static ResourceKey<Level> cachedLevelKey;
    @Nullable
    private static String cachedRenderStateKey;
    private static boolean cachedPhotic;
    private static boolean loggedMainDraw;
    private static boolean loggedShadowDraw;

    private IrisNativePipeGpuRenderer() {}

    static void render(ShaderpackEntityRenderContext context, IrisPipeRenderExtension extension) {
        refreshProfile(context.level().dimension());
        Minecraft minecraft = Minecraft.getInstance();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        try {
            if (context.setupLevelLighting()) {
                minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.LEVEL);
            }
            NativeDrawFrame frame = new NativeDrawFrame();
            for (ShaderpackEntitySection section : context.sections()) {
                if (!shouldDrawSection(context.visibleSections(), section)) {
                    continue;
                }
                sectionCache(section, extension).addDraws(frame, context.translucent());
            }
            DrawStats stats = frame.draw(context.camera(), false, extension);
            if (stats.drew() && !loggedMainDraw) {
                loggedMainDraw = true;
                SuperPipeSlide.LOGGER.info(
                        "Drew SuperPipeSlide Iris native pipe GPU batches: translucent={}, batches={}, indices={}",
                        context.translucent(),
                        stats.batches(),
                        stats.indices());
            }
        } finally {
            modelViewStack.popMatrix();
        }
    }

    static void renderShadow(ShaderpackEntityShadowContext context, IrisPipeRenderExtension extension) {
        refreshProfile(context.level().dimension());
        try (ClientPipeRenderer.PipeRenderExtension.Scope shadowViewScope = extension.shadowModelView()) {
            NativeDrawFrame frame = new NativeDrawFrame();
            for (ShaderpackEntitySection section : context.sections()) {
                sectionCache(section, extension).addShadowDraws(frame);
            }
            DrawStats stats = frame.draw(context.camera(), true, extension);
            if (stats.drew() && !loggedShadowDraw) {
                loggedShadowDraw = true;
                SuperPipeSlide.LOGGER.info(
                        "Drew SuperPipeSlide Iris native shadow pipe GPU batches: batches={}, indices={}",
                        stats.batches(),
                        stats.indices());
            }
        }
    }

    static void invalidateSection(RenderSectionKey sectionKey) {
        SectionGpuCache removed = SECTION_CACHES.remove(sectionKey);
        if (removed != null) {
            removed.release();
        }
    }

    static void clear(String reason) {
        if (SECTION_CACHES.isEmpty()) {
            cachedLevelKey = null;
            cachedRenderStateKey = null;
            return;
        }
        for (SectionGpuCache cache : SECTION_CACHES.values()) {
            cache.release();
        }
        SECTION_CACHES.clear();
        cachedLevelKey = null;
        cachedRenderStateKey = null;
    }

    private static void refreshProfile(ResourceKey<Level> levelKey) {
        String renderStateKey = ClientRenderCompatibility.renderStateKey();
        boolean photic = ClientSafetyOptions.reducePhotosensitivityRisk();
        if (cachedLevelKey != null
                && cachedLevelKey.equals(levelKey)
                && cachedRenderStateKey != null
                && cachedRenderStateKey.equals(renderStateKey)
                && cachedPhotic == photic) {
            return;
        }
        clear("Iris native profile changed");
        cachedLevelKey = levelKey;
        cachedRenderStateKey = renderStateKey;
        cachedPhotic = photic;
    }

    private static boolean shouldDrawSection(Set<RenderSectionKey> visibleSections, ShaderpackEntitySection section) {
        return visibleSections.isEmpty() || visibleSections.contains(section.sectionKey());
    }

    private static SectionGpuCache sectionCache(ShaderpackEntitySection section, IrisPipeRenderExtension extension) {
        SectionGpuCache cache = SECTION_CACHES.get(section.sectionKey());
        if (cache == null || cache.version() != section.version()) {
            if (cache != null) {
                cache.release();
            }
            cache = SectionGpuCache.upload(section, extension);
            SECTION_CACHES.put(section.sectionKey(), cache);
        }
        return cache;
    }

    private record SectionGpuCache(long version, NativeGpuBatches batches) {
        static SectionGpuCache upload(ShaderpackEntitySection section, IrisPipeRenderExtension extension) {
            return new SectionGpuCache(section.version(), NativeGpuBatches.upload(section, extension));
        }

        void addDraws(NativeDrawFrame frame, boolean translucent) {
            this.batches.addDraws(frame, translucent);
        }

        void addShadowDraws(NativeDrawFrame frame) {
            this.batches.addShadowDraws(frame);
        }

        void release() {
            this.batches.release();
        }
    }

    private record NativeGpuBatches(List<NativeGpuBatch> opaque, List<NativeGpuBatch> translucent) {
        private static final NativeGpuBatches EMPTY = new NativeGpuBatches(List.of(), List.of());

        static NativeGpuBatches upload(ShaderpackEntitySection section, IrisPipeRenderExtension extension) {
            PipeLitRenderBatches source = section.litBatches();
            if (source.isEmpty()) {
                return EMPTY;
            }
            boolean photic = ClientSafetyOptions.reducePhotosensitivityRisk();
            List<NativeGpuBatch> opaque = new ArrayList<>();
            List<NativeGpuBatch> translucent = new ArrayList<>();
            addUploaded(opaque, entityCutout(TextureAtlas.LOCATION_BLOCKS), source.atlasBatches(), section.sectionKey(), photic, extension);
            addUploaded(opaque, entityCutoutCull(TextureAtlas.LOCATION_BLOCKS), source.culledAtlasBatches(), section.sectionKey(), photic, extension);
            addUploaded(opaque, entityCutout(TextureAtlas.LOCATION_BLOCKS), source.emissiveAtlasBatches(), section.sectionKey(), photic, extension);
            addUploaded(opaque, entityCutoutCull(TextureAtlas.LOCATION_BLOCKS), source.emissiveCulledAtlasBatches(), section.sectionKey(), photic, extension);
            addUploaded(translucent, entityTranslucent(TextureAtlas.LOCATION_BLOCKS), source.translucentAtlasBatches(), section.sectionKey(), photic, extension);
            addUploaded(translucent, entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS), source.emissiveTranslucentAtlasBatches(), section.sectionKey(), photic, extension);
            addUploadedMaps(opaque, IrisNativePipeGpuRenderer::entityCutout, source.generatedBatches(), section.sectionKey(), photic, extension);
            addUploadedMaps(opaque, IrisNativePipeGpuRenderer::entityCutoutCull, source.culledGeneratedBatches(), section.sectionKey(), photic, extension);
            addUploadedMaps(opaque, IrisNativePipeGpuRenderer::entityCutout, source.emissiveGeneratedBatches(), section.sectionKey(), photic, extension);
            addUploadedMaps(opaque, IrisNativePipeGpuRenderer::entityCutoutCull, source.emissiveCulledGeneratedBatches(), section.sectionKey(), photic, extension);
            addUploadedMaps(translucent, IrisNativePipeGpuRenderer::entityTranslucent, source.translucentGeneratedBatches(), section.sectionKey(), photic, extension);
            addUploadedMaps(translucent, IrisNativePipeGpuRenderer::entityTranslucentEmissive, source.emissiveTranslucentGeneratedBatches(), section.sectionKey(), photic, extension);
            return new NativeGpuBatches(List.copyOf(opaque), List.copyOf(translucent));
        }

        private static void addUploaded(
                List<NativeGpuBatch> target,
                RenderType renderType,
                List<List<LitTexturedQuad>> batches,
                RenderSectionKey sectionKey,
                boolean photic,
                IrisPipeRenderExtension extension) {
            NativeGpuBatch batch = NativeGpuBatch.upload(renderType, batches, sectionKey, photic, extension);
            if (batch != null) {
                target.add(batch);
            }
        }

        private static void addUploadedMaps(
                List<NativeGpuBatch> target,
                RenderTypeFactory renderTypeFactory,
                Map<Identifier, List<List<LitTexturedQuad>>> batchesByTexture,
                RenderSectionKey sectionKey,
                boolean photic,
                IrisPipeRenderExtension extension) {
            for (Map.Entry<Identifier, List<List<LitTexturedQuad>>> entry : batchesByTexture.entrySet()) {
                addUploaded(target, renderTypeFactory.create(entry.getKey()), entry.getValue(), sectionKey, photic, extension);
            }
        }

        void addDraws(NativeDrawFrame frame, boolean translucentPass) {
            List<NativeGpuBatch> batches = translucentPass ? this.translucent : this.opaque;
            for (NativeGpuBatch batch : batches) {
                frame.add(batch, batch.indexCount);
            }
        }

        void addShadowDraws(NativeDrawFrame frame) {
            for (NativeGpuBatch batch : this.opaque) {
                frame.add(batch, batch.shadowIndexCount);
            }
        }

        void release() {
            for (NativeGpuBatch batch : this.opaque) {
                batch.release();
            }
            for (NativeGpuBatch batch : this.translucent) {
                batch.release();
            }
        }
    }

    private static final class NativeDrawFrame {
        private final Map<RenderType, List<NativeGpuBatchDraw>> draws = new LinkedHashMap<>();

        void add(NativeGpuBatch batch, int indices) {
            if (batch.vertexBuffer.isClosed() || indices <= 0) {
                return;
            }
            this.draws.computeIfAbsent(batch.renderType, ignored -> new ArrayList<>()).add(new NativeGpuBatchDraw(batch, indices));
        }

        DrawStats draw(Vec3 camera, boolean shadowPass, IrisPipeRenderExtension extension) {
            DrawStats stats = DrawStats.EMPTY;
            for (Map.Entry<RenderType, List<NativeGpuBatchDraw>> entry : this.draws.entrySet()) {
                stats = stats.add(drawGroup(entry.getKey(), entry.getValue(), camera, shadowPass, extension));
            }
            return stats;
        }

        private static DrawStats drawGroup(RenderType renderType, List<NativeGpuBatchDraw> draws, Vec3 camera, boolean shadowPass, IrisPipeRenderExtension extension) {
            if (draws.isEmpty()) {
                return DrawStats.EMPTY;
            }
            RenderSetup renderSetup = ((RenderTypeAccessor) renderType).superpipeslide$state();
            RenderSetupAccessor renderSetupAccessor = (RenderSetupAccessor) (Object) renderSetup;
            Map<String, RenderSetup.TextureAndSampler> textures = renderSetup.getTextures();
            Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
            java.util.function.Consumer<Matrix4fStack> layeringModifier = renderSetupAccessor.superpipeslide$layeringTransform().getModifier();
            boolean pushedLayer = layeringModifier != null;
            if (pushedLayer) {
                modelViewStack.pushMatrix();
                layeringModifier.accept(modelViewStack);
            }
            try {
                TextureTransform textureTransform = renderSetupAccessor.superpipeslide$textureTransform();
                ClientPipeRenderer.PipeRenderTargetOverride targetOverride = shadowPass
                        ? extension.instancedRenderTargetOverride(true)
                        : ClientPipeRenderer.PipeRenderTargetOverride.none();
                RenderTarget target = renderType.outputTarget().getRenderTarget();
                GpuTextureView colorTexture = targetOverride.colorTexture() != null
                        ? targetOverride.colorTexture()
                        : RenderSystem.outputColorTextureOverride != null
                                ? RenderSystem.outputColorTextureOverride
                                : target.getColorTextureView();
                GpuTextureView depthTexture = targetOverride.depthTexture() != null
                        ? targetOverride.depthTexture()
                        : target.useDepth
                                ? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView())
                                : null;
                try {
                    try (ClientPipeRenderer.PipeRenderExtension.Scope ignored = extension.shaderpackEntityPhaseScope(shadowPass)) {
                        List<PreparedNativeGpuBatchDraw> preparedDraws = prepareDraws(draws, camera, textureTransform);
                        RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(renderType.mode());
                        GpuBuffer sequentialIndexBuffer = indexBuffer.getBuffer(maxIndices(draws));
                        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
                        try (RenderPass renderPass = encoder.createRenderPass(
                                    () -> "SuperPipeSlide Iris native pipe " + renderType,
                                    colorTexture,
                                    targetOverride.colorClear(),
                                    depthTexture,
                                    targetOverride.depthClear())) {
                            extension.prepareInstancedRenderPass(renderPass, shadowPass);
                            ScissorState scissorState = RenderSystem.getScissorStateForRenderTypeDraws();
                            if (scissorState.enabled()) {
                                renderPass.enableScissor(scissorState.x(), scissorState.y(), scissorState.width(), scissorState.height());
                            }
                            RenderSystem.bindDefaultUniforms(renderPass);
                            for (Map.Entry<String, RenderSetup.TextureAndSampler> entry : textures.entrySet()) {
                                renderPass.bindTexture(entry.getKey(), entry.getValue().textureView(), entry.getValue().sampler());
                            }
                            renderPass.setPipeline(renderType.pipeline());
                            renderPass.setIndexBuffer(sequentialIndexBuffer, indexBuffer.type());
                            int batchesDrawn = 0;
                            int indicesDrawn = 0;
                            for (PreparedNativeGpuBatchDraw draw : preparedDraws) {
                                renderPass.setUniform("DynamicTransforms", draw.dynamicTransforms());
                                renderPass.setVertexBuffer(0, draw.batch().vertexBuffer);
                                renderPass.drawIndexed(0, 0, draw.indices(), 1);
                                batchesDrawn++;
                                indicesDrawn += draw.indices();
                            }
                            return new DrawStats(batchesDrawn, indicesDrawn);
                        }
                    }
                } finally {
                    extension.restoreInstancedRenderPassTarget(shadowPass);
                }
            } finally {
                if (pushedLayer) {
                    modelViewStack.popMatrix();
                }
            }
        }

        private static int maxIndices(List<NativeGpuBatchDraw> draws) {
            int max = 0;
            for (NativeGpuBatchDraw draw : draws) {
                max = Math.max(max, draw.indices());
            }
            return max;
        }

        private static List<PreparedNativeGpuBatchDraw> prepareDraws(List<NativeGpuBatchDraw> draws, Vec3 camera, TextureTransform textureTransform) {
            List<PreparedNativeGpuBatchDraw> preparedDraws = new ArrayList<>(draws.size());
            Vector4f colorModulator = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
            Vector3f modelOffset = new Vector3f();
            for (NativeGpuBatchDraw draw : draws) {
                modelOffset.set(
                        (float) (draw.batch().sectionOrigin.x - camera.x),
                        (float) (draw.batch().sectionOrigin.y - camera.y),
                        (float) (draw.batch().sectionOrigin.z - camera.z));
                GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                        RenderSystem.getModelViewMatrix(),
                        colorModulator,
                        modelOffset,
                        textureTransform.getMatrix());
                preparedDraws.add(new PreparedNativeGpuBatchDraw(draw.batch(), draw.indices(), dynamicTransforms));
            }
            return preparedDraws;
        }
    }

    private record NativeGpuBatchDraw(NativeGpuBatch batch, int indices) {}

    private record PreparedNativeGpuBatchDraw(NativeGpuBatch batch, int indices, GpuBufferSlice dynamicTransforms) {}

    private static final class NativeGpuBatch {
        private final RenderType renderType;
        private final Vec3 sectionOrigin;
        private final GpuBuffer vertexBuffer;
        private final int indexCount;
        private final int shadowIndexCount;

        private NativeGpuBatch(RenderType renderType, Vec3 sectionOrigin, GpuBuffer vertexBuffer, int indexCount, int shadowIndexCount) {
            this.renderType = renderType;
            this.sectionOrigin = sectionOrigin;
            this.vertexBuffer = vertexBuffer;
            this.indexCount = indexCount;
            this.shadowIndexCount = shadowIndexCount;
        }

        @Nullable
        static NativeGpuBatch upload(
                RenderType baseRenderType,
                List<List<LitTexturedQuad>> batches,
                RenderSectionKey sectionKey,
                boolean photic,
                IrisPipeRenderExtension extension) {
            int quadCount = countQuads(batches);
            if (quadCount <= 0) {
                return null;
            }
            RenderType renderType = ClientRenderCompatibility.world(baseRenderType);
            Vec3 sectionOrigin = sectionKey.origin();
            int estimatedBytes = Math.max(GPU_BATCH_INITIAL_BYTES, quadCount * 4 * renderType.format().getVertexSize());
            int shadowQuadCount = countShadowQuads(batches);
            try (ClientPipeRenderer.PipeRenderExtension.Scope ignored = extension.shaderpackEntityBufferBuildScope();
                    ByteBufferBuilder byteBuffer = new ByteBufferBuilder(estimatedBytes)) {
                BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.mode(), renderType.format());
                writeQuads(builder, batches, sectionOrigin, photic, true);
                if (shadowQuadCount < quadCount) {
                    writeQuads(builder, batches, sectionOrigin, photic, false);
                }
                MeshData mesh = builder.build();
                if (mesh == null) {
                    return null;
                }
                try {
                    ByteBuffer vertices = mesh.vertexBuffer();
                    GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(
                            () -> "SuperPipeSlide Iris native pipe section " + renderType,
                            GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_VERTEX,
                            vertices);
                    int indexCount = mesh.drawState().indexCount();
                    int shadowIndexCount = shadowQuadCount == quadCount ? indexCount : shadowQuadCount * 6;
                    return new NativeGpuBatch(renderType, sectionOrigin, vertexBuffer, indexCount, shadowIndexCount);
                } finally {
                    mesh.close();
                }
            }
        }

        void release() {
            if (!this.vertexBuffer.isClosed()) {
                this.vertexBuffer.close();
            }
        }
    }

    private static int countQuads(List<List<LitTexturedQuad>> batches) {
        int count = 0;
        for (List<LitTexturedQuad> batch : batches) {
            count += batch.size();
        }
        return count;
    }

    private static int countShadowQuads(List<List<LitTexturedQuad>> batches) {
        int count = 0;
        for (List<LitTexturedQuad> batch : batches) {
            for (LitTexturedQuad quad : batch) {
                if (quad.quad().castsShadow()) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void writeQuads(BufferBuilder builder, List<List<LitTexturedQuad>> batches, Vec3 origin, boolean photic, boolean shadowCasterPass) {
        for (List<LitTexturedQuad> batch : batches) {
            for (LitTexturedQuad litQuad : batch) {
                if (litQuad.quad().castsShadow() != shadowCasterPass) {
                    continue;
                }
                writeQuad(builder, litQuad, origin, photic);
            }
        }
    }

    private static void writeQuad(BufferBuilder builder, LitTexturedQuad litQuad, Vec3 origin, boolean photic) {
        TexturedQuad quad = litQuad.quad();
        int color = quad.color();
        if (!photic && quad.animationKind() != 0) {
            color = ClientPipeRenderer.pipeEntityColor(
                    color,
                    quad.animationKind(),
                    (float) quad.animationPhase(),
                    false,
                    (float) (System.nanoTime() / 1_000_000_000.0D % 4096.0D));
        }
        addVertex(builder, quad.a(), origin, quad.u0(), quad.v0(), color, ClientPipeRenderer.pipeEntityLight(litQuad, 0, photic), quad.normal());
        addVertex(builder, quad.b(), origin, quad.u1(), quad.v0(), color, ClientPipeRenderer.pipeEntityLight(litQuad, 1, photic), quad.normal());
        addVertex(builder, quad.c(), origin, quad.u1(), quad.v1(), color, ClientPipeRenderer.pipeEntityLight(litQuad, 2, photic), quad.normal());
        addVertex(builder, quad.d(), origin, quad.u0(), quad.v1(), color, ClientPipeRenderer.pipeEntityLight(litQuad, 3, photic), quad.normal());
    }

    private static void addVertex(BufferBuilder builder, Vec3 point, Vec3 origin, float u, float v, int color, int light, Vec3 normal) {
        builder.addVertex((float) (point.x - origin.x), (float) (point.y - origin.y), (float) (point.z - origin.z))
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static RenderType entityCutout(Identifier texture) {
        return RenderTypes.entityCutout(texture, false);
    }

    private static RenderType entityCutoutCull(Identifier texture) {
        return RenderTypes.entityCutoutCull(texture);
    }

    private static RenderType entityTranslucent(Identifier texture) {
        return RenderTypes.entityTranslucent(texture, false);
    }

    private static RenderType entityTranslucentEmissive(Identifier texture) {
        return RenderTypes.entityTranslucentEmissive(texture, false);
    }

    private interface RenderTypeFactory {
        RenderType create(Identifier texture);
    }

    private record DrawStats(int batches, int indices) {
        private static final DrawStats EMPTY = new DrawStats(0, 0);

        DrawStats add(DrawStats other) {
            return new DrawStats(this.batches + other.batches, this.indices + other.indices);
        }

        boolean drew() {
            return this.batches > 0 && this.indices > 0;
        }
    }
}
