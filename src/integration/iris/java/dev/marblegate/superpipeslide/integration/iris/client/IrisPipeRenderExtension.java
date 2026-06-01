package dev.marblegate.superpipeslide.integration.iris.client;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.marblegate.superpipeslide.client.renderer.pipe.ClientPipeRenderer;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.config.ClientConfig;
import dev.marblegate.superpipeslide.config.ShaderpackPipeRenderMode;
import dev.marblegate.superpipeslide.mixin.iris.IrisRenderingPipelineAccessor;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3d;

public final class IrisPipeRenderExtension implements ClientPipeRenderer.PipeRenderExtension {
    private static final ClientPipeRenderer.PipeRenderExtension.Scope NOOP_SCOPE = () -> {};
    private static final boolean PIPE_EXTERNAL_SHADOWS_ENABLED = true;
    private static final float SHADOW_STRENGTH = 0.72F;
    private static final float SHADOW_BIAS = 0.0025F;
    private static final float SHADOW_NORMAL_BIAS = 0.035F;
    private static boolean warningLogged;
    private static boolean performanceNoticeSent;
    private static boolean nativeModeLogged;
    private static boolean performanceModeLogged;
    @Nullable
    private static ShadowRenderTargets cachedShadowTargets;
    @Nullable
    private static GpuTextureView cachedShadowDepthView;
    @Nullable
    private static GpuTextureView cachedShadowDepthNoTranslucentsView;
    @Nullable
    private static GpuSampler cachedShadowSampler;
    private static float cachedShadowMapBias;
    @Nullable
    private static GpuTexture shadowAttachmentColorTexture;
    @Nullable
    private static GpuTextureView shadowAttachmentColorView;

    @Override
    public ClientPipeRenderer.PipeRenderMode renderMode() {
        if (!irisShaderPackInUse()) {
            return ClientPipeRenderer.PipeRenderMode.VANILLA;
        }
        WorldRenderingPipeline pipeline = currentPipeline();
        if (!(pipeline instanceof IrisRenderingPipeline)) {
            return ClientPipeRenderer.PipeRenderMode.SHADERPACK_PERFORMANCE;
        }
        return ClientConfig.SHADERPACK_PIPE_RENDER_MODE.get() == ShaderpackPipeRenderMode.NATIVE
                ? ClientPipeRenderer.PipeRenderMode.SHADERPACK_ENTITY
                : ClientPipeRenderer.PipeRenderMode.SHADERPACK_PERFORMANCE;
    }

    @Override
    public ClientPipeRenderer.PipeRenderExtension.Scope instancedPipeDrawScope(boolean shadowPass) {
        return instancedPipeDrawScope(renderMode(), shadowPass);
    }

    @Override
    public ClientPipeRenderer.PipeRenderExtension.Scope instancedPipeDrawScope(ClientPipeRenderer.PipeRenderMode renderMode, boolean shadowPass) {
        if (!renderMode.usesShaderpackRenderer()) {
            return NOOP_SCOPE;
        }
        WorldRenderingPipeline pipeline = currentPipeline();
        boolean previousBypass = ImmediateState.bypass;
        try {
            if (renderMode.usesShaderpackEntityRenderer()) {
                logNativeModeActive();
                ImmediateState.bypass = false;
            } else {
                // Performance mode keeps the optimized instanced renderer while
                // still binding Iris framebuffers, shadow maps, and render phases.
                logPerformanceModeActive();
                notifyPerformanceModeActive();
                ImmediateState.bypass = true;
            }
            if (pipeline != null) {
                pipeline.setOverridePhase(WorldRenderingPhase.ENTITIES);
            }
            return new InstancedPipeDrawScope(pipeline, previousBypass);
        } catch (RuntimeException | LinkageError exception) {
            ImmediateState.bypass = previousBypass;
            warn("set Iris instanced pipe rendering scope", exception);
            return NOOP_SCOPE;
        }
    }

    @Override
    public ClientPipeRenderer.PipeRenderExtension.Scope shaderpackEntityBufferBuildScope() {
        if (!irisShaderPackInUse()) {
            return NOOP_SCOPE;
        }
        boolean previousRenderingLevel = ImmediateState.isRenderingLevel;
        boolean previousExtendedVertexFormat = ImmediateState.renderWithExtendedVertexFormat;
        Boolean previousSkipExtension = ImmediateState.skipExtension.get();
        ImmediateState.isRenderingLevel = true;
        ImmediateState.renderWithExtendedVertexFormat = true;
        ImmediateState.skipExtension.set(false);
        return new NativeEntityBufferBuildScope(previousRenderingLevel, previousExtendedVertexFormat, previousSkipExtension);
    }

    @Override
    public ClientPipeRenderer.PipeRenderExtension.Scope shaderpackEntityPhaseScope(boolean shadowPass) {
        if (!irisShaderPackInUse()) {
            return NOOP_SCOPE;
        }
        WorldRenderingPipeline pipeline = currentPipeline();
        boolean previousBypass = ImmediateState.bypass;
        boolean previousRenderingBlockEntities = ImmediateState.isRenderingBEs;
        ImmediateState.bypass = false;
        ImmediateState.isRenderingBEs = false;
        CapturedRenderingState capturedState = CapturedRenderingState.INSTANCE;
        int previousEntity = capturedState.getCurrentRenderedEntity();
        int previousBlockEntity = capturedState.getCurrentRenderedBlockEntity();
        int previousItem = capturedState.getCurrentRenderedItem();
        capturedState.setCurrentEntity(0);
        capturedState.setCurrentBlockEntity(0);
        capturedState.setCurrentRenderedItem(0);
        try {
            if (pipeline != null) {
                pipeline.setOverridePhase(WorldRenderingPhase.ENTITIES);
            }
        } catch (RuntimeException | LinkageError exception) {
            warn("set Iris native pipe rendering phase", exception);
        }
        logNativeModeActive();
        return new NativeEntityPhaseScope(
                pipeline,
                previousBypass,
                previousRenderingBlockEntities,
                previousEntity,
                previousBlockEntity,
                previousItem);
    }

    @Override
    public void renderShaderpackEntityPipes(ClientPipeRenderer.ShaderpackEntityRenderContext context) {
        IrisNativePipeGpuRenderer.render(context, this);
    }

    @Override
    public void renderShaderpackEntityShadows(ClientPipeRenderer.ShaderpackEntityShadowContext context) {
        IrisNativePipeGpuRenderer.renderShadow(context, this);
    }

    @Override
    public void invalidateShaderpackEntitySection(ClientPipeRenderer.RenderSectionKey sectionKey) {
        IrisNativePipeGpuRenderer.invalidateSection(sectionKey);
    }

    @Override
    public void clearShaderpackEntityResources(String reason) {
        IrisNativePipeGpuRenderer.clear(reason);
    }

    @Override
    public boolean isRenderingShadowPass() {
        try {
            return IrisApi.getInstance().isRenderingShadowPass();
        } catch (RuntimeException | LinkageError exception) {
            warn("query Iris shadow pass state", exception);
            return false;
        }
    }

    @Override
    public ClientPipeRenderer.PipeRenderTargetOverride instancedRenderTargetOverride(boolean shadowPass) {
        if (!shadowPass || !externalShadowTargetsAvailable()) {
            return ClientPipeRenderer.PipeRenderTargetOverride.none();
        }
        ShadowRenderTargets shadowTargets = shadowTargets(currentPipeline());
        if (shadowTargets == null) {
            return ClientPipeRenderer.PipeRenderTargetOverride.none();
        }
        try {
            return new ClientPipeRenderer.PipeRenderTargetOverride(
                    shadowAttachmentColorView(shadowTargets.getResolution()),
                    shadowDepthTextureView(shadowTargets.getDepthTexture()),
                    OptionalInt.empty(),
                    OptionalDouble.empty());
        } catch (RuntimeException | LinkageError exception) {
            warn("prepare Iris pipe shadow render target override", exception);
            return ClientPipeRenderer.PipeRenderTargetOverride.none();
        }
    }

    @Override
    public void prepareInstancedRenderPass(RenderPass renderPass, boolean shadowPass) {
        if (!irisShaderPackInUse()) {
            return;
        }
        WorldRenderingPipeline pipeline = currentPipeline();
        if (!(pipeline instanceof IrisRenderingPipeline irisPipeline)) {
            return;
        }
        try {
            if (shadowPass) {
                irisPipeline.bindDefaultShadow();
                GlStateManager._viewport(0, 0, ShadowRenderer.RESOLUTION, ShadowRenderer.RESOLUTION);
            } else {
                irisPipeline.bindDefault();
            }
        } catch (RuntimeException | LinkageError exception) {
            warn("bind Iris instanced pipe render target", exception);
        }
    }

    @Override
    public void bindInstancedRenderPassTextures(RenderPass renderPass, boolean shadowPass) {
        if (shadowPass || !externalShadowTargetsAvailable()) {
            return;
        }
        ShadowRenderTargets shadowTargets = shadowTargets(currentPipeline());
        if (shadowTargets == null) {
            return;
        }
        try {
            renderPass.bindTexture("PipeShadowSampler", shadowDepthNoTranslucentsTextureView(shadowTargets.getDepthTextureNoTranslucents()), shadowDepthSampler());
            renderPass.bindTexture("PipeShadowWithPipesSampler", shadowDepthTextureView(shadowTargets.getDepthTexture()), shadowDepthSampler());
        } catch (RuntimeException | LinkageError exception) {
            warn("bind Iris shadow sampler for pipe rendering", exception);
        }
    }

    @Override
    public void restoreInstancedRenderPassTarget(boolean shadowPass) {
        if (!irisShaderPackInUse()) {
            return;
        }
        WorldRenderingPipeline pipeline = currentPipeline();
        if (!(pipeline instanceof IrisRenderingPipeline irisPipeline)) {
            return;
        }
        try {
            if (shadowPass) {
                irisPipeline.bindDefaultShadow();
                GlStateManager._viewport(0, 0, ShadowRenderer.RESOLUTION, ShadowRenderer.RESOLUTION);
            } else {
                irisPipeline.bindDefault();
            }
        } catch (RuntimeException | LinkageError exception) {
            warn("restore Iris instanced pipe render target", exception);
        }
    }

    @Override
    public ClientPipeRenderer.PipeExternalLighting externalLightingState(Vec3 camera, boolean shadowPass) {
        if (!externalShadowTargetsAvailable()) {
            return ClientPipeRenderer.PipeExternalLighting.disabled();
        }
        WorldRenderingPipeline pipeline = currentPipeline();
        if (!(pipeline instanceof IrisRenderingPipeline irisPipeline)) {
            return ClientPipeRenderer.PipeExternalLighting.disabled();
        }
        ShadowRenderTargets shadowTargets = shadowTargets(irisPipeline);
        if (shadowTargets == null || !irisPipeline.hasShadowRenderTargets() || ShadowRenderer.PROJECTION == null || ShadowRenderer.MODELVIEW == null) {
            return ClientPipeRenderer.PipeExternalLighting.disabled();
        }
        try {
            Vector3d shadowCamera = CameraUniforms.getUnshiftedCameraPosition();
            Matrix4f shadowMatrix = new Matrix4f(ShadowRenderer.PROJECTION)
                    .mul(ShadowRenderer.MODELVIEW)
                    .translate(
                            (float) (camera.x - shadowCamera.x),
                            (float) (camera.y - shadowCamera.y),
                            (float) (camera.z - shadowCamera.z));
            return new ClientPipeRenderer.PipeExternalLighting(
                    shadowMatrix,
                    shadowPass ? 0.0F : SHADOW_STRENGTH,
                    SHADOW_BIAS,
                    SHADOW_NORMAL_BIAS,
                    shadowMapBias(),
                    RenderSystem.getDevice().isZZeroToOne());
        } catch (RuntimeException | LinkageError exception) {
            warn("build Iris pipe external lighting state", exception);
            return ClientPipeRenderer.PipeExternalLighting.disabled();
        }
    }

    private static float shadowMapBias() {
        int chunks = ShadowRenderingState.getRenderDistance();
        if (chunks <= 0) {
            return cachedShadowMapBias;
        }
        double shadowDistance = chunks * 16.0D;
        cachedShadowMapBias = (float) Math.max(0.0D, Math.min(0.99D, 1.0D - 25.6D / shadowDistance));
        return cachedShadowMapBias;
    }

    @Override
    public void renderExternalShadowPass(Camera camera) {
        if (!externalShadowTargetsAvailable() || ShadowRenderer.PROJECTION == null || ShadowRenderer.MODELVIEW == null) {
            return;
        }
        ClientPipeRenderer.drawExternalShadowPass(this, camera);
    }

    @Override
    public boolean isExternalPipelineActive() {
        return externalShadowTargetsAvailable();
    }

    private static void notifyPerformanceModeActive() {
        if (performanceNoticeSent) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        performanceNoticeSent = true;
        minecraft.player.sendSystemMessage(Component.translatable("message.superpipeslide.shaderpack_pipe_performance_mode").withStyle(ChatFormatting.YELLOW));
    }

    @Nullable
    @Override
    public Frustum shadowFrustum() {
        try {
            return ShadowRenderer.FRUSTUM;
        } catch (RuntimeException | LinkageError exception) {
            warn("read Iris shadow frustum", exception);
            return null;
        }
    }

    @Override
    public double shadowRenderRadiusBlocks(double fallback) {
        try {
            int chunks = ShadowRenderingState.getRenderDistance();
            return chunks > 0 ? chunks * 16.0D : fallback;
        } catch (RuntimeException | LinkageError exception) {
            warn("read Iris shadow render distance", exception);
            return fallback;
        }
    }

    @Override
    public Vec3 shadowCameraPosition(Vec3 fallback) {
        try {
            Vector3d camera = CameraUniforms.getUnshiftedCameraPosition();
            return new Vec3(camera.x, camera.y, camera.z);
        } catch (RuntimeException | LinkageError exception) {
            warn("read Iris shadow camera position", exception);
            return fallback;
        }
    }

    @Override
    public ClientPipeRenderer.PipeRenderExtension.Scope shadowModelView() {
        try {
            Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.pushMatrix();
            modelViewStack.set(ShadowRenderer.MODELVIEW);
            return new ShadowModelViewScope();
        } catch (RuntimeException | LinkageError exception) {
            warn("set Iris shadow model view", exception);
            return NOOP_SCOPE;
        }
    }

    private static boolean irisShaderPackInUse() {
        try {
            return IrisApi.getInstance().isShaderPackInUse();
        } catch (RuntimeException | LinkageError exception) {
            warn("query Iris shaderpack state", exception);
            return false;
        }
    }

    private static boolean externalShadowTargetsAvailable() {
        if (!PIPE_EXTERNAL_SHADOWS_ENABLED || !irisShaderPackInUse()) {
            return false;
        }
        WorldRenderingPipeline pipeline = currentPipeline();
        if (!(pipeline instanceof IrisRenderingPipeline irisPipeline) || !irisPipeline.hasShadowRenderTargets()) {
            return false;
        }
        return shadowTargets(irisPipeline) != null;
    }

    @Nullable
    private static WorldRenderingPipeline currentPipeline() {
        try {
            return Iris.getPipelineManager().getPipelineNullable();
        } catch (RuntimeException | LinkageError exception) {
            warn("obtain Iris rendering pipeline", exception);
            return null;
        }
    }

    @Nullable
    private static ShadowRenderTargets shadowTargets(@Nullable WorldRenderingPipeline pipeline) {
        if (!(pipeline instanceof IrisRenderingPipeline irisPipeline)) {
            return null;
        }
        return shadowTargets(irisPipeline);
    }

    @Nullable
    private static ShadowRenderTargets shadowTargets(IrisRenderingPipeline pipeline) {
        try {
            ShadowRenderTargets shadowTargets = ((IrisRenderingPipelineAccessor) pipeline).superpipeslide$shadowRenderTargets();
            if (shadowTargets != null) {
                cachedShadowTargets = shadowTargets;
            }
            return shadowTargets != null ? shadowTargets : cachedShadowTargets;
        } catch (RuntimeException | LinkageError exception) {
            warn("read Iris shadow render targets", exception);
            return cachedShadowTargets;
        }
    }

    private static GpuTextureView shadowDepthTextureView(com.mojang.blaze3d.textures.GpuTexture shadowDepthTexture) {
        if (cachedShadowDepthView == null || cachedShadowDepthView.isClosed() || cachedShadowDepthView.texture() != shadowDepthTexture) {
            if (cachedShadowDepthView != null) {
                cachedShadowDepthView.close();
            }
            cachedShadowDepthView = RenderSystem.getDevice().createTextureView(shadowDepthTexture);
        }
        return cachedShadowDepthView;
    }

    private static GpuTextureView shadowDepthNoTranslucentsTextureView(com.mojang.blaze3d.textures.GpuTexture shadowDepthTexture) {
        if (cachedShadowDepthNoTranslucentsView == null || cachedShadowDepthNoTranslucentsView.isClosed() || cachedShadowDepthNoTranslucentsView.texture() != shadowDepthTexture) {
            if (cachedShadowDepthNoTranslucentsView != null) {
                cachedShadowDepthNoTranslucentsView.close();
            }
            cachedShadowDepthNoTranslucentsView = RenderSystem.getDevice().createTextureView(shadowDepthTexture);
        }
        return cachedShadowDepthNoTranslucentsView;
    }

    private static GpuTextureView shadowAttachmentColorView(int resolution) {
        if (shadowAttachmentColorTexture == null
                || shadowAttachmentColorTexture.isClosed()
                || shadowAttachmentColorTexture.getWidth(0) != resolution
                || shadowAttachmentColorTexture.getHeight(0) != resolution
                || shadowAttachmentColorView == null
                || shadowAttachmentColorView.isClosed()) {
            closeShadowAttachmentColor();
            shadowAttachmentColorTexture = RenderSystem.getDevice().createTexture(
                    () -> "SuperPipeSlide Iris shadow attachment color",
                    GpuTexture.USAGE_RENDER_ATTACHMENT,
                    TextureFormat.RGBA8,
                    resolution,
                    resolution,
                    1,
                    1);
            shadowAttachmentColorView = RenderSystem.getDevice().createTextureView(shadowAttachmentColorTexture);
        }
        return shadowAttachmentColorView;
    }

    private static void closeShadowAttachmentColor() {
        if (shadowAttachmentColorView != null && !shadowAttachmentColorView.isClosed()) {
            shadowAttachmentColorView.close();
        }
        shadowAttachmentColorView = null;
        if (shadowAttachmentColorTexture != null && !shadowAttachmentColorTexture.isClosed()) {
            shadowAttachmentColorTexture.close();
        }
        shadowAttachmentColorTexture = null;
    }

    private static GpuSampler shadowDepthSampler() {
        if (cachedShadowSampler == null) {
            cachedShadowSampler = RenderSystem.getDevice().createSampler(
                    AddressMode.CLAMP_TO_EDGE,
                    AddressMode.CLAMP_TO_EDGE,
                    FilterMode.NEAREST,
                    FilterMode.NEAREST,
                    1,
                    OptionalDouble.empty());
        }
        return cachedShadowSampler;
    }

    private static void warn(String action, Throwable throwable) {
        if (warningLogged) {
            return;
        }
        warningLogged = true;
        SuperPipeSlide.LOGGER.warn("Failed to {}; Iris pipe rendering extension will use a reduced path.", action, throwable);
    }

    private static void logNativeModeActive() {
        if (nativeModeLogged) {
            return;
        }
        nativeModeLogged = true;
        SuperPipeSlide.LOGGER.info("Using Iris native GPU renderer for SuperPipeSlide pipes.");
    }

    private static void logPerformanceModeActive() {
        if (performanceModeLogged) {
            return;
        }
        performanceModeLogged = true;
        SuperPipeSlide.LOGGER.info("Using Iris performance renderer for SuperPipeSlide pipes.");
    }

    private static final class InstancedPipeDrawScope implements ClientPipeRenderer.PipeRenderExtension.Scope {
        @Nullable
        private final WorldRenderingPipeline pipeline;
        private final boolean previousBypass;

        private InstancedPipeDrawScope(@Nullable WorldRenderingPipeline pipeline, boolean previousBypass) {
            this.pipeline = pipeline;
            this.previousBypass = previousBypass;
        }

        @Override
        public void close() {
            try {
                if (this.pipeline != null) {
                    this.pipeline.setOverridePhase(null);
                }
            } catch (RuntimeException | LinkageError exception) {
                warn("restore Iris instanced pipe rendering phase", exception);
            } finally {
                ImmediateState.bypass = this.previousBypass;
            }
        }
    }

    private record NativeEntityBufferBuildScope(
            boolean previousRenderingLevel,
            boolean previousExtendedVertexFormat,
            Boolean previousSkipExtension)
            implements ClientPipeRenderer.PipeRenderExtension.Scope {
        @Override
        public void close() {
            ImmediateState.isRenderingLevel = this.previousRenderingLevel;
            ImmediateState.renderWithExtendedVertexFormat = this.previousExtendedVertexFormat;
            ImmediateState.skipExtension.set(this.previousSkipExtension);
        }
    }

    private record NativeEntityPhaseScope(
            @Nullable WorldRenderingPipeline pipeline,
            boolean previousBypass,
            boolean previousRenderingBlockEntities,
            int previousEntity,
            int previousBlockEntity,
            int previousItem)
            implements ClientPipeRenderer.PipeRenderExtension.Scope {
        @Override
        public void close() {
            try {
                if (this.pipeline != null) {
                    this.pipeline.setOverridePhase(null);
                }
            } catch (RuntimeException | LinkageError exception) {
                warn("restore Iris native pipe rendering phase", exception);
            } finally {
                CapturedRenderingState capturedState = CapturedRenderingState.INSTANCE;
                capturedState.setCurrentEntity(this.previousEntity);
                capturedState.setCurrentBlockEntity(this.previousBlockEntity);
                capturedState.setCurrentRenderedItem(this.previousItem);
                ImmediateState.isRenderingBEs = this.previousRenderingBlockEntities;
                ImmediateState.bypass = this.previousBypass;
            }
        }
    }

    private static final class ShadowModelViewScope implements ClientPipeRenderer.PipeRenderExtension.Scope {
        @Override
        public void close() {
            try {
                RenderSystem.getModelViewStack().popMatrix();
            } catch (RuntimeException | LinkageError exception) {
                warn("restore Iris shadow model view", exception);
            }
        }
    }
}
