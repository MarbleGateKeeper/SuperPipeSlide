package dev.marblegate.superpipeslide.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.marblegate.superpipeslide.client.core.slide.ClientCinematicCameraController;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses vanilla hurt-tilt and view-bobbing while the cinematic camera is engaged.
 * Both effects are applied to the render state/projection AFTER the camera is set up,
 * so the cinematic controller cannot override them; during collision detach/recapture
 * chains the rider briefly walks and lands under vanilla physics, and these two bobs
 * shook the whole frame on top of the stabilized shot (the downhill collision tremble).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void superpipeslide$suppressBobHurt(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        if (ClientCinematicCameraController.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void superpipeslide$suppressBobView(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        if (ClientCinematicCameraController.isActive()) {
            ci.cancel();
        }
    }
}
