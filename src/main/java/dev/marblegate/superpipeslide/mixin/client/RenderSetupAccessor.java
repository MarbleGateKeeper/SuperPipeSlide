package dev.marblegate.superpipeslide.mixin.client;

import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderSetup.class)
public interface RenderSetupAccessor {
    @Accessor("layeringTransform")
    LayeringTransform superpipeslide$layeringTransform();

    @Accessor("textureTransform")
    TextureTransform superpipeslide$textureTransform();
}
