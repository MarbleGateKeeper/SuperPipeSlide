package dev.marblegate.superpipeslide.mixin.client;

import dev.marblegate.superpipeslide.client.renderer.slide.ClientSlideFeedbackPlayerRenderer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void superpipeslide$applySlidingPose(AvatarRenderState state, CallbackInfo ci) {
        ClientSlideFeedbackPlayerRenderer.applySlidingModelPose(state, (PlayerModel) (Object) this);
    }
}
