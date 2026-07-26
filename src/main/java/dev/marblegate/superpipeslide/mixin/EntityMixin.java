package dev.marblegate.superpipeslide.mixin;

import dev.marblegate.superpipeslide.common.core.slide.ServerSlideController;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops the server from broadcasting a sliding player's step sounds to nearby players.
 * The server runs {@code Entity.move()} for every accepted move packet, which would
 * otherwise emit footsteps for a rider that is actually gliding along a pipe. Only the
 * sound is suppressed; step vibrations (sculk game events) are left untouched.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "walkingStepSound", at = @At("HEAD"), cancellable = true)
    private void superpipeslide$suppressServerSlideStepSound(BlockPos onPos, BlockState onState, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer serverPlayer && ServerSlideController.isSliding(serverPlayer)) {
            ci.cancel();
        }
    }
}
