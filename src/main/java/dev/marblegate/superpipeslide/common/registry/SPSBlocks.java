package dev.marblegate.superpipeslide.common.registry;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.block.anchor.BranchAnchorBlock;
import dev.marblegate.superpipeslide.common.block.anchor.FoldAnchorBlock;
import dev.marblegate.superpipeslide.common.block.anchor.PipeAnchorBlock;
import dev.marblegate.superpipeslide.common.block.station.PlatformProjectorBlock;
import dev.marblegate.superpipeslide.common.block.station.StationBlock;
import dev.marblegate.superpipeslide.common.block.station.StationNameProjectorBlock;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorKind;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SPSBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SuperPipeSlide.MODID);

    public static final DeferredBlock<Block> PIPE_ANCHOR = BLOCKS.registerBlock(
            "pipe_anchor",
            PipeAnchorBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_CYAN)
                    .noCollision()
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.COPPER));

    public static final DeferredBlock<Block> BRANCH_ANCHOR = BLOCKS.registerBlock(
            "branch_anchor",
            BranchAnchorBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_PURPLE)
                    .noCollision()
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.AMETHYST));

    public static final DeferredBlock<Block> SPACE_FOLD_ANCHOR = BLOCKS.registerBlock(
            "space_fold_anchor",
            properties -> new FoldAnchorBlock(properties, FoldAnchorKind.SPACE),
            properties -> properties
                    .mapColor(MapColor.COLOR_GREEN)
                    .noCollision()
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.AMETHYST));

    public static final DeferredBlock<Block> DIMENSION_FOLD_ANCHOR = BLOCKS.registerBlock(
            "dimension_fold_anchor",
            properties -> new FoldAnchorBlock(properties, FoldAnchorKind.DIMENSION),
            properties -> properties
                    .mapColor(MapColor.COLOR_MAGENTA)
                    .noCollision()
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.AMETHYST));

    public static final DeferredBlock<Block> STATION_BLOCK = BLOCKS.registerBlock(
            "station_block",
            StationBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .noOcclusion()
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.COPPER));

    public static final DeferredBlock<Block> STATION_NAME_PROJECTOR = BLOCKS.registerBlock(
            "station_name_projector",
            StationNameProjectorBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_GREEN)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .strength(1.8F, 6.0F)
                    .lightLevel(state -> 4)
                    .sound(SoundType.COPPER));

    public static final DeferredBlock<Block> PLATFORM_PROJECTOR = BLOCKS.registerBlock(
            "platform_projector",
            PlatformProjectorBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .strength(1.8F, 6.0F)
                    .lightLevel(state -> 4)
                    .sound(SoundType.COPPER));

    private SPSBlocks() {}
}
