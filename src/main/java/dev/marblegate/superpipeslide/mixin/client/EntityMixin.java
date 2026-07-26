package dev.marblegate.superpipeslide.mixin.client;

import dev.marblegate.superpipeslide.client.core.slide.ClientSlideController;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Silences the local player's own step sounds while sliding, including the brief
 * vanilla-physics ticks of collision-driven detach/recapture flapping. Only the sound
 * is suppressed; step vibrations (sculk game events) are left untouched.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "walkingStepSound", at = @At("HEAD"), cancellable = true)
    private void superpipeslide$suppressSlideStepSound(BlockPos onPos, BlockState onState, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self == Minecraft.getInstance().player && ClientSlideController.wasSlidingRecently(self.level())) {
            ci.cancel();
        }
    }
}
