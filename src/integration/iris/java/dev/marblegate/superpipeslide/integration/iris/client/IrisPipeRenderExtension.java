package dev.marblegate.superpipeslide.integration.iris.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.marblegate.superpipeslide.client.renderer.pipe.ClientPipeRenderer;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import javax.annotation.Nullable;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fStack;
import org.joml.Vector3d;

public final class IrisPipeRenderExtension implements ClientPipeRenderer.PipeRenderExtension {
    private static final ClientPipeRenderer.PipeRenderExtension.Scope NOOP_SCOPE = () -> {
    };
    private static boolean warningLogged;

    @Override
    public String renderStateKey() {
        if (!irisShaderPackInUse()) {
            return "iris_shaderpack_off";
        }
        try {
            return "iris_shaderpack:" + Iris.getCurrentPackName() + ":pipeline_" + Iris.getPipelineManager().getVersionCounterForSodiumShaderReload() + ":pipe_entity_v12";
        } catch (RuntimeException | LinkageError exception) {
            warn("query Iris render state", exception);
            return "iris_shaderpack:unknown:pipe_entity_v12";
        }
    }

    @Override
    public void refreshPipelineMappings() {
        copyPipelineMapping(RenderPipelines.ENTITY_CUTOUT, ClientPipeRenderer.pipeEntityCutoutPipeline());
        copyPipelineMapping(RenderPipelines.ENTITY_CUTOUT_CULL, ClientPipeRenderer.pipeEntityCutoutCullPipeline());
        copyPipelineMapping(RenderPipelines.ENTITY_TRANSLUCENT, ClientPipeRenderer.pipeEntityTranslucentPipeline());
    }

    @Override
    public ClientPipeRenderer.PipeRenderExtension.Scope entityPhase() {
        if (!irisShaderPackInUse()) {
            return NOOP_SCOPE;
        }
        WorldRenderingPipeline pipeline = currentPipeline();
        if (pipeline == null) {
            return NOOP_SCOPE;
        }
        try {
            pipeline.setOverridePhase(WorldRenderingPhase.ENTITIES);
            return new PhaseScope(pipeline);
        } catch (RuntimeException | LinkageError exception) {
            warn("set Iris entity rendering phase", exception);
            return NOOP_SCOPE;
        }
    }

    @Override
    public ClientPipeRenderer.PipeRenderExtension.Scope entityBufferBuild() {
        if (!irisShaderPackInUse()) {
            return NOOP_SCOPE;
        }
        boolean wasRenderingLevel = ImmediateState.isRenderingLevel;
        boolean wasRenderWithExtendedVertexFormat = ImmediateState.renderWithExtendedVertexFormat;
        Boolean wasSkippingExtension = ImmediateState.skipExtension.get();
        ImmediateState.isRenderingLevel = true;
        ImmediateState.renderWithExtendedVertexFormat = true;
        ImmediateState.skipExtension.set(false);
        return new EntityBufferBuildScope(wasRenderingLevel, wasRenderWithExtendedVertexFormat, wasSkippingExtension);
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
    public boolean isExternalPipelineActive() {
        return irisShaderPackInUse();
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

    private static void copyPipelineMapping(RenderPipeline source, RenderPipeline target) {
        try {
            IrisPipelines.copyPipeline(source, target);
        } catch (RuntimeException | LinkageError exception) {
            warn("copy Iris pipeline mapping", exception);
        }
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

    private static void warn(String action, Throwable throwable) {
        if (warningLogged) {
            return;
        }
        warningLogged = true;
        SuperPipeSlide.LOGGER.warn("Failed to {}; Iris pipe rendering extension will use a reduced path.", action, throwable);
    }

    private static final class EntityBufferBuildScope implements ClientPipeRenderer.PipeRenderExtension.Scope {
        private final boolean wasRenderingLevel;
        private final boolean wasRenderWithExtendedVertexFormat;
        private final Boolean wasSkippingExtension;

        private EntityBufferBuildScope(boolean wasRenderingLevel, boolean wasRenderWithExtendedVertexFormat, Boolean wasSkippingExtension) {
            this.wasRenderingLevel = wasRenderingLevel;
            this.wasRenderWithExtendedVertexFormat = wasRenderWithExtendedVertexFormat;
            this.wasSkippingExtension = wasSkippingExtension;
        }

        @Override
        public void close() {
            ImmediateState.isRenderingLevel = this.wasRenderingLevel;
            ImmediateState.renderWithExtendedVertexFormat = this.wasRenderWithExtendedVertexFormat;
            ImmediateState.skipExtension.set(this.wasSkippingExtension);
        }
    }

    private static final class PhaseScope implements ClientPipeRenderer.PipeRenderExtension.Scope {
        private final WorldRenderingPipeline pipeline;

        private PhaseScope(WorldRenderingPipeline pipeline) {
            this.pipeline = pipeline;
        }

        @Override
        public void close() {
            try {
                this.pipeline.setOverridePhase(null);
            } catch (RuntimeException | LinkageError exception) {
                warn("restore Iris entity rendering phase", exception);
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
