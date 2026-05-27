package dev.marblegate.superpipeslide.common.registry;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SPSCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SuperPipeSlide.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.superpipeslide"))
            .withTabsBefore(CreativeModeTabs.BUILDING_BLOCKS)
            .icon(() -> SPSItems.PIPE_CONNECTOR_GAZE_CURVE.get().getDefaultInstance())
            .displayItems((_, output) -> {
                output.accept(SPSItems.PIPE_ANCHOR.get());
                output.accept(SPSItems.BRANCH_ANCHOR.get());
                output.accept(SPSItems.SPACE_FOLD_ANCHOR.get());
                output.accept(SPSItems.DIMENSION_FOLD_ANCHOR.get());
                output.accept(SPSItems.ANCHOR_UPGRADE_BRANCH.get());
                output.accept(SPSItems.ANCHOR_UPGRADE_SPACE_FOLD.get());
                output.accept(SPSItems.ANCHOR_UPGRADE_DIMENSION_FOLD.get());
                output.accept(SPSItems.PIPE_CONNECTOR_LINE.get());
                output.accept(SPSItems.PIPE_CONNECTOR_AUTO_CURVE.get());
                output.accept(SPSItems.PIPE_CONNECTOR_GAZE_CURVE.get());
                output.accept(SPSItems.PIPE_CONNECTOR_CONTROLLED.get());
                output.accept(SPSItems.PIPE_REMOVER.get());
                output.accept(SPSItems.ACCELERATION_ATTRIBUTE_TOOL.get());
                output.accept(SPSItems.HIGHWAY_ATTRIBUTE_TOOL.get());
                output.accept(SPSItems.ROUTE_DIRECTION_LIMITER.get());
                output.accept(SPSItems.ACCELERATION_PIPE_COATING.get());
                output.accept(SPSItems.HIGHWAY_PIPE_COATING.get());
                output.accept(SPSItems.PIPE_APPEARANCE_TOOL.get());
                output.accept(SPSItems.BROKEN_ANCHOR_CLEANER.get());
                output.accept(SPSItems.STATION_BLOCK.get());
                output.accept(SPSItems.PLATFORM_CLAIMER.get());
                output.accept(SPSItems.ROUTE_EDITOR.get());
                output.accept(SPSItems.PIPE_TRANSIT_GUIDE.get());
                output.accept(SPSItems.PROJECTION_LAYOUT_DESIGNER.get());
                output.accept(SPSItems.STATION_NAME_PROJECTOR.get());
                output.accept(SPSItems.PLATFORM_PROJECTOR.get());
            })
            .build());

    private SPSCreativeTabs() {
    }
}
