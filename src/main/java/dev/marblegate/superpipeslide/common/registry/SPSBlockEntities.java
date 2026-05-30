package dev.marblegate.superpipeslide.common.registry;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.block.station.PlatformProjectorBlockEntity;
import dev.marblegate.superpipeslide.common.block.station.StationNameProjectorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SPSBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SuperPipeSlide.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StationNameProjectorBlockEntity>> STATION_NAME_PROJECTOR = BLOCK_ENTITIES.register(
            "station_name_projector",
            () -> new BlockEntityType<>(StationNameProjectorBlockEntity::new, false, SPSBlocks.STATION_NAME_PROJECTOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlatformProjectorBlockEntity>> PLATFORM_PROJECTOR = BLOCK_ENTITIES.register(
            "platform_projector",
            () -> new BlockEntityType<>(PlatformProjectorBlockEntity::new, false, SPSBlocks.PLATFORM_PROJECTOR.get()));

    private SPSBlockEntities() {}
}
