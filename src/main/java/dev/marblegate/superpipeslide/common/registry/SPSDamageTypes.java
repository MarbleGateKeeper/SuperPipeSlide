package dev.marblegate.superpipeslide.common.registry;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

public final class SPSDamageTypes {
    public static final ResourceKey<DamageType> PIPE_SUFFOCATION = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipe_suffocation"));

    private SPSDamageTypes() {}

    public static DamageSource pipeSuffocation(ServerLevel level) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(PIPE_SUFFOCATION));
    }
}
