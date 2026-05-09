package dev.marblegate.superpipeslide.mixin.client;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.marblegate.superpipeslide.client.renderer.fold.ClientFoldTraversalPostEffectRenderer;
import dev.marblegate.superpipeslide.client.renderer.pipe.ClientPipeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private LevelTargetBundle targets;

    @Shadow
    @Final
    LevelRenderState levelRenderState;

    @Redirect(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;execute(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder$Inspector;)V"
            )
    )
    private void superpipeslide$addFoldTraversalPostEffect(FrameGraphBuilder frame, GraphicsResourceAllocator resourceAllocator, FrameGraphBuilder.Inspector inspector) {
        ClientFoldTraversalPostEffectRenderer.addToFrame(
                frame,
                this.targets,
                this.minecraft.getMainRenderTarget().width,
                this.minecraft.getMainRenderTarget().height,
                this.levelRenderState.cameraRenderState
        );
        frame.execute(resourceAllocator, inspector);
    }

    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
    private void superpipeslide$markPipeSectionLightDirty(int sectionX, int sectionY, int sectionZ, boolean playerChanged, CallbackInfo callbackInfo) {
        ClientPipeRenderer.markSectionLightDirty(sectionX, sectionY, sectionZ);
    }
}
