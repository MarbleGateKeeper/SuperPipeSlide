package dev.marblegate.superpipeslide.mixin.iris;

import dev.marblegate.superpipeslide.client.renderer.ClientRenderCompatibility;
import dev.marblegate.superpipeslide.integration.ModIntegration;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Restriction(require = @Condition(ModIntegration.Constants.IRIS))
@Mixin(ItemFeatureRenderer.class)
public abstract class IrisItemFeatureRendererMixin {
    @Redirect(
            method = "renderItem(Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/OutlineBufferSource;Lnet/minecraft/client/renderer/SubmitNodeStorage$ItemSubmit;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/model/geometry/BakedQuad$MaterialInfo;itemRenderType()Lnet/minecraft/client/renderer/rendertype/RenderType;"
            )
    )
    private RenderType superpipeslide$adaptItemRenderType(BakedQuad.MaterialInfo materialInfo) {
        return ClientRenderCompatibility.world(materialInfo.itemRenderType());
    }
}
