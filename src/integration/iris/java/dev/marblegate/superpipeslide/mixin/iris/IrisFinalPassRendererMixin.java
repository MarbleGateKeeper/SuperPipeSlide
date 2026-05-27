package dev.marblegate.superpipeslide.mixin.iris;

import dev.marblegate.superpipeslide.client.renderer.fold.ClientFoldTraversalPostEffectRenderer;
import dev.marblegate.superpipeslide.integration.ModIntegration;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Restriction(require = @Condition(ModIntegration.Constants.IRIS))
@Mixin(targets = "net.irisshaders.iris.pipeline.FinalPassRenderer")
public abstract class IrisFinalPassRendererMixin {
    @Inject(method = "renderFinalPass", at = @At("RETURN"))
    private void superpipeslide$renderFoldTraversalPostEffectAfterIrisFinalPass(CallbackInfo callbackInfo) {
        ClientFoldTraversalPostEffectRenderer.renderAfterExternalFinalPass(Minecraft.getInstance().getMainRenderTarget());
    }
}
