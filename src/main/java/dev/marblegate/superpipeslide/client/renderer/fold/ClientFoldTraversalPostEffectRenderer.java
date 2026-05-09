package dev.marblegate.superpipeslide.client.renderer.fold;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.marblegate.superpipeslide.client.core.fold.ClientFoldTraversalEffectController;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.joml.Vector4f;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class ClientFoldTraversalPostEffectRenderer {
    private static final int SPACE_PRIMARY = 0xFF8FF6FF;
    private static final int SPACE_SECONDARY = 0xFFD9FFFF;
    private static final int DIMENSION_PRIMARY = 0xFFB56CFF;
    private static final int DIMENSION_SECONDARY = 0xFFE45BFF;
    private static final int CANCEL_PRIMARY = 0xFFFF8166;
    private static final int CANCEL_SECONDARY = 0xFFFFD16A;
    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private static final Identifier DISTORTION_SHADER = Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "core/fold_traversal_distortion");
    private static final RenderPipeline DISTORTION_PIPELINE = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipeline/fold_traversal_distortion"))
            .withVertexShader(DISTORTION_SHADER)
            .withFragmentShader(DISTORTION_SHADER)
            .withSampler("InSampler")
            .withUniform("FoldTraversal", UniformType.UNIFORM_BUFFER)
            .build();

    @Nullable
    private static DynamicUniformStorage<FoldTraversalUniform> uniforms;

    private ClientFoldTraversalPostEffectRenderer() {
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(DISTORTION_PIPELINE);
    }

    public static void addToFrame(FrameGraphBuilder frame, LevelTargetBundle targets, int width, int height, CameraRenderState cameraState) {
        if (width <= 0 || height <= 0) {
            return;
        }
        Optional<ClientFoldTraversalEffectController.Snapshot> snapshot = ClientFoldTraversalEffectController.snapshot();
        if (snapshot.isEmpty()) {
            return;
        }
        FoldTraversalUniform uniform = uniform(snapshot.get(), width, height, cameraState);
        if (uniform.intensity() <= 0.01F) {
            return;
        }

        RenderTargetDescriptor sourceDescriptor = new RenderTargetDescriptor(width, height, false, 0);
        ResourceHandle<RenderTarget> mainBeforeCopy = targets.main;
        FramePass copyPass = frame.addPass("superpipeslide_fold_traversal_copy");
        copyPass.reads(mainBeforeCopy);
        ResourceHandle<RenderTarget> source = copyPass.createsInternal("superpipeslide_fold_traversal_source", sourceDescriptor);
        copyPass.executes(() -> copyColor(mainBeforeCopy.get(), source.get()));

        FramePass distortionPass = frame.addPass("superpipeslide_fold_traversal_distortion");
        distortionPass.reads(source);
        ResourceHandle<RenderTarget> mainOutput = distortionPass.readsAndWrites(targets.main);
        targets.main = mainOutput;
        distortionPass.executes(() -> renderDistortion(source.get(), mainOutput.get(), uniform));
    }

    public static void endFrame() {
        if (uniforms != null) {
            uniforms.endFrame();
        }
    }

    private static void copyColor(RenderTarget input, RenderTarget output) {
        GpuTexture inputColor = input.getColorTexture();
        GpuTexture outputColor = output.getColorTexture();
        if (inputColor == null || outputColor == null) {
            return;
        }
        int width = Math.min(input.width, output.width);
        int height = Math.min(input.height, output.height);
        if (width <= 0 || height <= 0) {
            return;
        }
        RenderSystem.getDevice()
                .createCommandEncoder()
                .copyTextureToTexture(inputColor, outputColor, 0, 0, 0, 0, 0, width, height);
    }

    private static void renderDistortion(RenderTarget source, RenderTarget output, FoldTraversalUniform uniform) {
        if (source.getColorTextureView() == null || output.getColorTextureView() == null) {
            return;
        }
        GpuBufferSlice uniformSlice = uniformStorage().writeUniform(uniform);
        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass renderPass = commandEncoder.createRenderPass(
                () -> "SuperPipeSlide fold traversal distortion",
                output.getColorTextureView(),
                OptionalInt.empty(),
                output.useDepth ? output.getDepthTextureView() : null,
                OptionalDouble.empty()
        )) {
            renderPass.setPipeline(DISTORTION_PIPELINE);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.bindTexture("InSampler", source.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            renderPass.setUniform("FoldTraversal", uniformSlice);
            renderPass.draw(0, 3);
        }
    }

    private static DynamicUniformStorage<FoldTraversalUniform> uniformStorage() {
        if (uniforms == null) {
            uniforms = new DynamicUniformStorage<>("SuperPipeSlide fold traversal distortion", UNIFORM_SIZE, 2);
        }
        return uniforms;
    }

    private static FoldTraversalUniform uniform(ClientFoldTraversalEffectController.Snapshot effect, int width, int height, CameraRenderState cameraState) {
        float phase = phaseCode(effect.visualPhase());
        float phaseProgress = (float) Mth.clamp(effect.phaseProgress(), 0.0D, 1.0D);
        float approachProgress = (float) Mth.clamp(effect.approachProgress(), 0.0D, 1.0D);
        float life = (float) Mth.clamp(effect.life(), 0.0D, 1.0D);
        float speed = (float) Mth.clamp(effect.speed01(), 0.0D, 1.0D);
        float dimension = effect.dimensionFold() ? 1.0F : 0.0F;
        float waiting = effect.waitingForCommit() ? 1.0F : 0.0F;
        float cancel = effect.visualPhase() == ClientFoldTraversalEffectController.VisualPhase.CANCEL ? 1.0F : 0.0F;
        float aspect = width / (float) height;
        float time = System.nanoTime() / 1.0E9F;
        float seed = Math.floorMod(effect.seed(), 1_000_000L) / 1_000_000.0F;
        float kindScale = effect.dimensionFold() ? 1.66F : 1.58F;
        float intensity = postIntensity(effect) * (1.04F + speed * 0.42F) * kindScale;
        intensity = (float) Mth.clamp(intensity, 0.0F, 2.16F);
        ProjectedFold projected = projectFold(effect, cameraState);
        float foldAmount = switch (effect.visualPhase()) {
            case APPROACH -> approachProgress;
            case EXIT, DECAY -> life;
            case CANCEL -> life * 0.72F;
            default -> 1.0F;
        };
        float exitBlend = effect.visualPhase() == ClientFoldTraversalEffectController.VisualPhase.EXIT
                || effect.visualPhase() == ClientFoldTraversalEffectController.VisualPhase.DECAY ? 1.0F : 0.0F;

        int primaryColor = cancel > 0.5F ? CANCEL_PRIMARY : (effect.dimensionFold() ? DIMENSION_PRIMARY : SPACE_PRIMARY);
        int secondaryColor = cancel > 0.5F ? CANCEL_SECONDARY : (effect.dimensionFold() ? DIMENSION_SECONDARY : SPACE_SECONDARY);
        float[] primary = color(primaryColor);
        float[] secondary = color(secondaryColor);
        float chroma = 0.65F + dimension * 0.30F + speed * 0.25F;
        float vignette = effect.dimensionFold() ? 0.56F : 0.42F;

        return new FoldTraversalUniform(
                intensity,
                phaseProgress,
                approachProgress,
                life,
                speed,
                phase,
                dimension,
                waiting,
                time,
                seed,
                aspect,
                cancel,
                projected.focusX(),
                projected.focusY(),
                projected.axisX(),
                projected.axisY(),
                foldAmount,
                exitBlend,
                projected.visible(),
                projected.front(),
                primary[0],
                primary[1],
                primary[2],
                chroma,
                secondary[0],
                secondary[1],
                secondary[2],
                vignette
        );
    }

    private static float postIntensity(ClientFoldTraversalEffectController.Snapshot effect) {
        float progress = (float) Mth.clamp(effect.phaseProgress(), 0.0D, 1.0D);
        float life = (float) Mth.clamp(effect.life(), 0.0D, 1.0D);
        return switch (effect.visualPhase()) {
            case APPROACH -> (0.16F + (float) Mth.clamp(effect.approachProgress(), 0.0D, 1.0D) * 0.52F) * life;
            case CONTACT -> (0.84F + easeOut(progress) * 0.52F) * life;
            case TUNNEL -> 1.30F * life;
            case WAITING -> (1.12F + (float) Math.sin(System.nanoTime() / 210_000_000.0D) * 0.07F) * life;
            case EXIT -> (1.02F * (1.0F - easeOut(progress)) + 0.42F) * life;
            case DECAY -> 0.42F * (1.0F - easeOut(progress)) * life;
            case CANCEL -> 0.72F * (1.0F - easeOut(progress)) * life;
        };
    }

    private static float phaseCode(ClientFoldTraversalEffectController.VisualPhase phase) {
        return switch (phase) {
            case APPROACH -> 0.0F;
            case CONTACT -> 1.0F;
            case TUNNEL -> 2.0F;
            case WAITING -> 3.0F;
            case EXIT -> 4.0F;
            case DECAY -> 5.0F;
            case CANCEL -> 6.0F;
        };
    }

    private static float easeOut(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return 1.0F - (float) Math.pow(1.0F - t, 3.0D);
    }

    private static float[] color(int argb) {
        return new float[]{
                ((argb >> 16) & 0xFF) / 255.0F,
                ((argb >> 8) & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F
        };
    }

    private static ProjectedFold projectFold(ClientFoldTraversalEffectController.Snapshot effect, CameraRenderState cameraState) {
        boolean exit = effect.visualPhase() == ClientFoldTraversalEffectController.VisualPhase.EXIT
                || effect.visualPhase() == ClientFoldTraversalEffectController.VisualPhase.DECAY;
        Vec3 focus = exit ? effect.exitPosition() : effect.entryPosition();
        Vec3 tangent = exit ? effect.exitTangent() : effect.entryTangent();
        ProjectedPoint center = project(focus, cameraState);
        ProjectedPoint ahead = project(focus.add(tangent.scale(2.0D + effect.speed01() * 1.5D)), cameraState);
        float focusX = Mth.clamp(center.x(), 0.06F, 0.94F);
        float focusY = Mth.clamp(center.y(), 0.06F, 0.94F);
        float axisX = ahead.x() - center.x();
        float axisY = ahead.y() - center.y();
        float axisLength = (float) Math.hypot(axisX, axisY);
        if (axisLength < 0.025F || !center.front() || !ahead.front()) {
            axisX = 0.5F - focusX;
            axisY = 0.5F - focusY;
            axisLength = (float) Math.hypot(axisX, axisY);
            if (axisLength < 0.025F) {
                axisX = 0.0F;
                axisY = exit ? 1.0F : -1.0F;
                axisLength = 1.0F;
            }
        }
        axisX /= axisLength;
        axisY /= axisLength;
        float visible = center.visible() ? 1.0F : 0.0F;
        float front = center.front() ? 1.0F : 0.0F;
        return new ProjectedFold(focusX, focusY, axisX, axisY, visible, front);
    }

    private static ProjectedPoint project(Vec3 worldPosition, CameraRenderState cameraState) {
        Vec3 relative = worldPosition.subtract(cameraState.pos);
        Vector4f clip = new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1.0F);
        clip.mul(cameraState.viewRotationMatrix);
        clip.mul(cameraState.projectionMatrix);
        if (Math.abs(clip.w) < 1.0E-5F) {
            return new ProjectedPoint(0.5F, 0.5F, false, false);
        }
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        boolean front = clip.w > 0.0F;
        boolean visible = front && ndcX >= -1.15F && ndcX <= 1.15F && ndcY >= -1.15F && ndcY <= 1.15F;
        return new ProjectedPoint(ndcX * 0.5F + 0.5F, ndcY * 0.5F + 0.5F, visible, front);
    }

    private record FoldTraversalUniform(
            float intensity,
            float phaseProgress,
            float approachProgress,
            float life,
            float speed,
            float phase,
            float dimension,
            float waiting,
            float time,
            float seed,
            float aspect,
            float cancel,
            float focusX,
            float focusY,
            float axisX,
            float axisY,
            float foldAmount,
            float exitBlend,
            float focusVisible,
            float focusFront,
            float primaryR,
            float primaryG,
            float primaryB,
            float chroma,
            float secondaryR,
            float secondaryG,
            float secondaryB,
            float vignette
    ) implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(ByteBuffer byteBuffer) {
            Std140Builder.intoBuffer(byteBuffer)
                    .putVec4(this.intensity, this.phaseProgress, this.approachProgress, this.life)
                    .putVec4(this.speed, this.phase, this.dimension, this.waiting)
                    .putVec4(this.time, this.seed, this.aspect, this.cancel)
                    .putVec4(this.focusX, this.focusY, this.axisX, this.axisY)
                    .putVec4(this.foldAmount, this.exitBlend, this.focusVisible, this.focusFront)
                    .putVec4(this.primaryR, this.primaryG, this.primaryB, this.chroma)
                    .putVec4(this.secondaryR, this.secondaryG, this.secondaryB, this.vignette);
        }
    }

    private record ProjectedFold(float focusX, float focusY, float axisX, float axisY, float visible, float front) {
    }

    private record ProjectedPoint(float x, float y, boolean visible, boolean front) {
    }
}
