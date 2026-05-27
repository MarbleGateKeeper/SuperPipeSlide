package dev.marblegate.superpipeslide.mixin.iris;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.marblegate.superpipeslide.integration.ModIntegration;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Restriction(require = @Condition(ModIntegration.Constants.IRIS))
@Mixin(RenderSetup.class)
public interface IrisRenderSetupAccessor {
    @Accessor("pipeline")
    RenderPipeline superpipeslide_iris$pipeline();

    @Accessor("layeringTransform")
    LayeringTransform superpipeslide_iris$layeringTransform();

    @Accessor("textureTransform")
    TextureTransform superpipeslide_iris$textureTransform();

    @Accessor("outputTarget")
    OutputTarget superpipeslide_iris$outputTarget();

    @Accessor("outlineProperty")
    RenderSetup.OutlineProperty superpipeslide_iris$outlineProperty();

    @Accessor("textures")
    java.util.Map<String, ?> superpipeslide_iris$textures();

    @Accessor("useLightmap")
    boolean superpipeslide_iris$useLightmap();

    @Accessor("useOverlay")
    boolean superpipeslide_iris$useOverlay();

    @Accessor("affectsCrumbling")
    boolean superpipeslide_iris$affectsCrumbling();

    @Accessor("sortOnUpload")
    boolean superpipeslide_iris$sortOnUpload();

    @Accessor("bufferSize")
    int superpipeslide_iris$bufferSize();
}
