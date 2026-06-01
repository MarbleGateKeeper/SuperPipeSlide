package dev.marblegate.superpipeslide.mixin.iris;

import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Restriction(require = @Condition(dev.marblegate.superpipeslide.integration.ModIntegration.Constants.IRIS))
@Mixin(IrisRenderingPipeline.class)
public interface IrisRenderingPipelineAccessor {
    @Accessor("shadowRenderTargets")
    ShadowRenderTargets superpipeslide$shadowRenderTargets();
}
