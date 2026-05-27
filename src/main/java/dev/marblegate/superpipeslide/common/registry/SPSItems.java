package dev.marblegate.superpipeslide.common.registry;


import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorKind;
import dev.marblegate.superpipeslide.common.item.anchor.BranchUpgraderItem;
import dev.marblegate.superpipeslide.common.item.anchor.BrokenAnchorCleanerItem;
import dev.marblegate.superpipeslide.common.item.anchor.FoldAnchorUpgradeItem;
import dev.marblegate.superpipeslide.common.item.pipe.PipeAppearanceToolItem;
import dev.marblegate.superpipeslide.common.item.pipe.PipeAttributeToolItem;
import dev.marblegate.superpipeslide.common.item.pipe.PipeConnectorItem;
import dev.marblegate.superpipeslide.common.item.pipe.PipeConnectorMode;
import dev.marblegate.superpipeslide.common.item.pipe.PipeRemoverItem;
import dev.marblegate.superpipeslide.common.item.projection.ProjectionLayoutDesignerItem;
import dev.marblegate.superpipeslide.common.item.route.PipeTransitGuideItem;
import dev.marblegate.superpipeslide.common.item.route.PlatformClaimerItem;
import dev.marblegate.superpipeslide.common.item.route.RouteEditorItem;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SPSItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SuperPipeSlide.MODID);

    public static final DeferredItem<BlockItem> PIPE_ANCHOR = ITEMS.registerItem(
            "pipe_anchor",
            properties -> new BlockItem(SPSBlocks.PIPE_ANCHOR.get(), properties),
            Item.Properties::fireResistant
    );
    public static final DeferredItem<BlockItem> BRANCH_ANCHOR = ITEMS.registerItem(
            "branch_anchor",
            properties -> new BlockItem(SPSBlocks.BRANCH_ANCHOR.get(), properties),
            Item.Properties::fireResistant
    );
    public static final DeferredItem<BlockItem> SPACE_FOLD_ANCHOR = ITEMS.registerItem(
            "space_fold_anchor",
            properties -> new BlockItem(SPSBlocks.SPACE_FOLD_ANCHOR.get(), properties),
            Item.Properties::fireResistant
    );
    public static final DeferredItem<BlockItem> DIMENSION_FOLD_ANCHOR = ITEMS.registerItem(
            "dimension_fold_anchor",
            properties -> new BlockItem(SPSBlocks.DIMENSION_FOLD_ANCHOR.get(), properties),
            Item.Properties::fireResistant
    );
    public static final DeferredItem<BlockItem> STATION_BLOCK = ITEMS.registerItem(
            "station_block",
            properties -> new BlockItem(SPSBlocks.STATION_BLOCK.get(), properties),
            Item.Properties::fireResistant
    );
    public static final DeferredItem<BlockItem> STATION_NAME_PROJECTOR = ITEMS.registerItem(
            "station_name_projector",
            properties -> new BlockItem(SPSBlocks.STATION_NAME_PROJECTOR.get(), properties),
            Item.Properties::fireResistant
    );
    public static final DeferredItem<BlockItem> PLATFORM_PROJECTOR = ITEMS.registerItem(
            "platform_projector",
            properties -> new BlockItem(SPSBlocks.PLATFORM_PROJECTOR.get(), properties),
            Item.Properties::fireResistant
    );

    public static final DeferredItem<Item> PIPE_CONNECTOR_LINE = ITEMS.registerItem(
            "pipe_connector_line",
            (properties) -> new PipeConnectorItem(properties, PipeConnectorMode.LINE),
            properties -> properties.stacksTo(1)
    );

    public static final DeferredItem<Item> PIPE_CONNECTOR_AUTO_CURVE = ITEMS.registerItem(
            "pipe_connector_auto_curve",
            (properties) -> new PipeConnectorItem(properties, PipeConnectorMode.AUTO_CURVE),
            properties -> properties.stacksTo(1)
    );

    public static final DeferredItem<Item> PIPE_CONNECTOR_GAZE_CURVE = ITEMS.registerItem(
            "pipe_connector_gaze_curve",
            (properties) -> new PipeConnectorItem(properties, PipeConnectorMode.GAZE_CURVE),
            properties -> properties.stacksTo(1)
    );

    public static final DeferredItem<Item> PIPE_CONNECTOR_CONTROLLED = ITEMS.registerItem(
            "pipe_connector_controlled",
            (properties) -> new PipeConnectorItem(properties, PipeConnectorMode.CONTROLLED),
            properties -> properties.stacksTo(1)
    );

    public static final DeferredItem<Item> PIPE_REMOVER = ITEMS.registerItem(
            "pipe_remover",
            PipeRemoverItem::new,
            properties -> properties.stacksTo(1)
    );

    public static final DeferredItem<Item> BROKEN_ANCHOR_CLEANER = ITEMS.registerItem(
            "broken_anchor_cleaner",
            BrokenAnchorCleanerItem::new,
            properties -> properties.stacksTo(1)
    );

    public static final DeferredItem<Item> ANCHOR_UPGRADE_BRANCH = ITEMS.registerItem(
            "anchor_upgrade_branch",
            (properties) -> new BranchUpgraderItem(properties, true, false),
            properties -> properties.stacksTo(16)
    );

    public static final DeferredItem<Item> ANCHOR_UPGRADE_SPACE_FOLD = ITEMS.registerItem(
            "anchor_upgrade_space_fold",
            (properties) -> new FoldAnchorUpgradeItem(properties, FoldAnchorKind.SPACE),
            properties -> properties.stacksTo(16)
    );

    public static final DeferredItem<Item> ANCHOR_UPGRADE_DIMENSION_FOLD = ITEMS.registerItem(
            "anchor_upgrade_dimension_fold",
            (properties) -> new FoldAnchorUpgradeItem(properties, FoldAnchorKind.DIMENSION),
            properties -> properties.stacksTo(16)
    );

    public static final DeferredItem<Item> ACCELERATION_ATTRIBUTE_TOOL = ITEMS.registerItem(
            "acceleration_attribute_tool",
            (properties) -> new PipeAttributeToolItem(properties, PipeAttributeToolItem.AttributeKind.ACCELERATION),
            properties -> properties.stacksTo(1)
    );

    public static final DeferredItem<Item> HIGHWAY_ATTRIBUTE_TOOL = ITEMS.registerItem(
            "highway_attribute_tool",
            (properties) -> new PipeAttributeToolItem(properties, PipeAttributeToolItem.AttributeKind.HIGHWAY),
            properties -> properties.stacksTo(1)
    );

    public static final DeferredItem<Item> ROUTE_DIRECTION_LIMITER = ITEMS.registerItem(
            "route_direction_limiter",
            (properties) -> new PipeAttributeToolItem(properties, PipeAttributeToolItem.AttributeKind.DIRECTION_LIMIT),
            properties -> properties.stacksTo(1)
    );

    public static final DeferredItem<Item> ACCELERATION_PIPE_COATING = ITEMS.registerItem(
            "acceleration_pipe_coating",
            (properties) -> new PipeAttributeToolItem(properties, PipeAttributeToolItem.AttributeKind.ACCELERATION, true, false),
            properties -> properties.stacksTo(16)
    );

    public static final DeferredItem<Item> HIGHWAY_PIPE_COATING = ITEMS.registerItem(
            "highway_pipe_coating",
            (properties) -> new PipeAttributeToolItem(properties, PipeAttributeToolItem.AttributeKind.HIGHWAY, true, false),
            properties -> properties.stacksTo(16)
    );

    public static final DeferredItem<Item> PIPE_APPEARANCE_TOOL = ITEMS.registerItem(
            "pipe_appearance_tool",
            PipeAppearanceToolItem::new,
            properties -> properties.stacksTo(1)
    );

    public static final DeferredItem<Item> PROJECTION_LAYOUT_DESIGNER = ITEMS.registerItem(
            "projection_layout_designer",
            ProjectionLayoutDesignerItem::new,
            properties -> properties.stacksTo(1)
    );

    public static final DeferredItem<Item> PLATFORM_CLAIMER = ITEMS.registerItem(
            "platform_claimer",
            PlatformClaimerItem::new,
            properties -> properties.stacksTo(1)
    );

    public static final DeferredItem<Item> ROUTE_EDITOR = ITEMS.registerItem(
            "route_editor",
            RouteEditorItem::new,
            properties -> properties.stacksTo(1)
    );

    public static final DeferredItem<Item> PIPE_TRANSIT_GUIDE = ITEMS.registerItem(
            "pipe_transit_guide",
            PipeTransitGuideItem::new,
            properties -> properties.stacksTo(1)
    );

    private SPSItems() {
    }
}
