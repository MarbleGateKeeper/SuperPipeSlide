package dev.marblegate.superpipeslide.mixin.iris;

import dev.marblegate.superpipeslide.client.renderer.pipe.ClientPipeRenderer;
import dev.marblegate.superpipeslide.integration.ModIntegration;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Restriction(require = @Condition(ModIntegration.Constants.IRIS))
@Mixin(targets = "net.irisshaders.iris.shadows.ShadowRenderer")
public abstract class IrisShadowRendererMixin {
    @Inject(
            method = "renderShadows(Lnet/irisshaders/iris/mixin/LevelRendererAccessor;Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures()V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void superpipeslide$renderPipeExternalShadows(@Coerce Object levelRenderer, Camera playerCamera, CameraRenderState renderState, CallbackInfo callbackInfo) {
        ClientPipeRenderer.renderExternalShadowPass(playerCamera);
    }
}
