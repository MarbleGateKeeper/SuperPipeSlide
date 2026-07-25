package dev.marblegate.superpipeslide.mixin.client;

import dev.marblegate.superpipeslide.client.core.slide.ClientCinematicCameraController;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void superpipeslide$applyCinematicCamera(float partialTicks, CallbackInfo ci) {
        ClientCinematicCameraController.apply((Camera) (Object) this, partialTicks);
    }
}
