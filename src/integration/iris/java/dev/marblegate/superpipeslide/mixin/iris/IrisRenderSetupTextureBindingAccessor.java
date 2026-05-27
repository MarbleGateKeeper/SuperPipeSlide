package dev.marblegate.superpipeslide.mixin.iris;

import com.mojang.blaze3d.textures.GpuSampler;
import dev.marblegate.superpipeslide.integration.ModIntegration;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Restriction(require = @Condition(ModIntegration.Constants.IRIS))
@Mixin(targets = "net.minecraft.client.renderer.rendertype.RenderSetup$TextureBinding")
public interface IrisRenderSetupTextureBindingAccessor {
    @Accessor("location")
    Identifier superpipeslide_iris$location();

    @Accessor("sampler")
    Supplier<GpuSampler> superpipeslide_iris$sampler();
}
