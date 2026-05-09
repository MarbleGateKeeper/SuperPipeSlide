package dev.marblegate.superpipeslide.common.core.appearance.storage;

import dev.marblegate.superpipeslide.common.core.appearance.coating.PipeCoatingSelection;
import dev.marblegate.superpipeslide.common.core.appearance.material.MaterialSlotDefinition;
import dev.marblegate.superpipeslide.common.core.appearance.model.PipeAppearanceProfile;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeStyleDefinition;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeStyleParameterDefinition;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeStyleShape;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeVariantDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;

public final class PipeAppearanceDefinitions {
    public static final String PARAM_RADIUS = "radius";
    public static final String PARAM_WIDTH = "width";
    public static final String PARAM_HEIGHT = "height";
    public static final String PARAM_DEPTH = "depth";
    public static final String PARAM_TOP_FLATNESS = "top_flatness";
    public static final String PARAM_GAUGE = "gauge";
    public static final String PARAM_RAIL_WIDTH = "rail_width";
    public static final String PARAM_RAIL_HEIGHT = "rail_height";
    public static final String PARAM_TIE_INTERVAL = "tie_interval";
    public static final String PARAM_TIE_WIDTH = "tie_width";
    public static final String PARAM_RIM_WIDTH = "rim_width";
    public static final String PARAM_WALL_SLOPE = "wall_slope";
    public static final String PARAM_FLOOR_RATIO = "floor_ratio";
    public static final String PARAM_EDGE_WIDTH = "edge_width";

    private static final List<MaterialSlotDefinition> BODY = slots("body");
    private static final List<MaterialSlotDefinition> BODY_RIB = slots("body", "rib");
    private static final List<MaterialSlotDefinition> CAP_SIDE = slots("cap", "side");
    private static final List<MaterialSlotDefinition> TOP_SIDE = slots("top", "side");
    private static final List<MaterialSlotDefinition> TOP_SIDE_KEEL = slots("top", "side", "keel");
    private static final List<MaterialSlotDefinition> RAIL_TIE = slots("rail", "tie");
    private static final List<MaterialSlotDefinition> RAIL_TIE_BED = slots("rail", "tie", "bed");
    private static final List<MaterialSlotDefinition> FLOOR_WALL = slots("floor", "wall");
    private static final List<MaterialSlotDefinition> FLOOR_WALL_RIM = slots("floor", "wall", "rim");
    private static final List<MaterialSlotDefinition> BODY_EDGE = slots("body", "edge");
    private static final List<MaterialSlotDefinition> BEAM_TRACK = slots("beam", "track");
    private static final List<MaterialSlotDefinition> BEAM_TRACK_EDGE = slots("beam", "track", "edge");
    private static final List<MaterialSlotDefinition> BASE_CANOPY = slots("base", "canopy");
    private static final List<MaterialSlotDefinition> BASE_CANOPY_FRAME = slots("base", "canopy", "frame");

    private static final Map<String, PipeStyleDefinition> STYLES = new LinkedHashMap<>();
    private static final Map<String, PipeVariantDefinition> VARIANTS = new LinkedHashMap<>();

    static {
        registerStyles();
        registerVariants();
    }

    private PipeAppearanceDefinitions() {
    }

    public static List<PipeStyleDefinition> styles() {
        return List.copyOf(STYLES.values());
    }

    public static List<PipeVariantDefinition> variantsForStyle(String styleId) {
        return VARIANTS.values().stream()
                .filter(variant -> variant.styleId().equals(styleId))
                .toList();
    }

    public static Optional<PipeStyleDefinition> style(String id) {
        return Optional.ofNullable(STYLES.get(id));
    }

    public static Optional<PipeVariantDefinition> variant(String id) {
        return Optional.ofNullable(VARIANTS.get(id));
    }

    public static PipeStyleDefinition defaultStyle() {
        return STYLES.get(PipeAppearanceProfile.DEFAULT_STYLE_ID);
    }

    public static PipeVariantDefinition defaultVariant() {
        return VARIANTS.get(PipeAppearanceProfile.DEFAULT_VARIANT_ID);
    }

    public static List<MaterialSlotDefinition> slotsFor(PipeStyleDefinition style, PipeVariantDefinition variant) {
        if (variant != null && !variant.slots().isEmpty()) {
            return variant.slots();
        }
        return style == null ? BODY : style.slots();
    }

    public static List<PipeStyleParameterDefinition> parametersFor(PipeStyleDefinition style, PipeVariantDefinition variant) {
        if (style == null) {
            return List.of();
        }
        if (style.shape() == PipeStyleShape.SLIDE && (variant == null || !variant.reinforced())) {
            return style.parameters().stream()
                    .filter(parameter -> !parameter.id().equals(PARAM_RIM_WIDTH))
                    .toList();
        }
        return style.parameters();
    }

    public static PipeCoatingSelection defaultSelectionForSlot(String slotId) {
        return switch (slotId) {
            case "rail", "edge", "rim", "track", "keel", "rib", "frame" -> PipeCoatingSelection.original(net.minecraft.resources.Identifier.withDefaultNamespace("iron_block"));
            case "tie", "beam" -> PipeCoatingSelection.original(net.minecraft.resources.Identifier.withDefaultNamespace("oak_planks"));
            case "canopy" -> PipeCoatingSelection.original(net.minecraft.resources.Identifier.withDefaultNamespace("glass"));
            case "bed", "side", "wall", "base" -> PipeCoatingSelection.original(net.minecraft.resources.Identifier.withDefaultNamespace("smooth_stone"));
            default -> PipeCoatingSelection.defaultSelection();
        };
    }

    public static PipeCoatingSelection selectionFor(PipeAppearanceProfile profile, String slotId) {
        return profile.normalizedToDefinitions().slotCoatings().getOrDefault(slotId, defaultSelectionForSlot(slotId));
    }

    public static PipeCoatingSelection normalizeSelection(PipeCoatingSelection selection) {
        return normalizeSelection(selection, "body");
    }

    public static PipeCoatingSelection normalizeSelection(PipeCoatingSelection selection, String fallbackSlotId) {
        if (selection == null) {
            return defaultSelectionForSlot(fallbackSlotId);
        }
        return BuiltInRegistries.BLOCK.getOptional(selection.blockId())
                .filter(block -> block != Blocks.AIR)
                .map(block -> new PipeCoatingSelection(
                        BuiltInRegistries.BLOCK.getKey(block),
                        selection.texturePick(),
                        selection.dyeMode(),
                        selection.dyeColors(),
                        selection.preserveAccents(),
                        selection.textureStrength(),
                        selection.accentSensitivity(),
                        selection.smartStrength()
                ))
                .orElseGet(() -> defaultSelectionForSlot(fallbackSlotId));
    }

    private static void registerStyles() {
        style("round_pipe", "basic", PipeStyleShape.ROUND, "round_basic", BODY, List.of(
                parameter(PARAM_RADIUS, 0.20D, 0.10D, 0.60D, 0.01D)
        ), 1.7F, 5, false);
        style("box_pipe", "basic", PipeStyleShape.BOX, "box_basic", BODY, List.of(
                parameter(PARAM_WIDTH, 0.56D, 0.20D, 1.40D, 0.02D),
                parameter(PARAM_HEIGHT, 0.42D, 0.14D, 1.20D, 0.02D)
        ), 2.0F, 4, false);
        style("triangle_pipe", "profiled", PipeStyleShape.TRIANGLE, "triangle_basic", TOP_SIDE, List.of(
                parameter(PARAM_WIDTH, 0.58D, 0.26D, 1.40D, 0.02D),
                parameter(PARAM_DEPTH, 0.40D, 0.16D, 1.10D, 0.02D),
                parameter(PARAM_TOP_FLATNESS, 0.20D, 0.06D, 0.60D, 0.02D)
        ), 1.9F, 4, false);
        style("rail_pipe", "track", PipeStyleShape.RAIL, "rail_basic", RAIL_TIE, List.of(
                parameter(PARAM_GAUGE, 0.44D, 0.28D, 1.00D, 0.02D),
                parameter(PARAM_RAIL_WIDTH, 0.08D, 0.04D, 0.20D, 0.01D),
                parameter(PARAM_RAIL_HEIGHT, 0.10D, 0.05D, 0.28D, 0.01D),
                parameter(PARAM_TIE_INTERVAL, 0.62D, 0.32D, 1.40D, 0.05D),
                parameter(PARAM_TIE_WIDTH, 0.12D, 0.06D, 0.30D, 0.01D)
        ), 1.8F, 5, true);
        style("slide_pipe", "track", PipeStyleShape.SLIDE, "slide_basic", FLOOR_WALL, List.of(
                parameter(PARAM_WIDTH, 0.72D, 0.36D, 1.50D, 0.02D),
                parameter(PARAM_DEPTH, 0.24D, 0.10D, 0.72D, 0.02D),
                parameter(PARAM_RIM_WIDTH, 0.065D, 0.035D, 0.20D, 0.005D),
                parameter(PARAM_WALL_SLOPE, 0.20D, 0.00D, 0.80D, 0.02D),
                parameter(PARAM_FLOOR_RATIO, 0.56D, 0.30D, 0.90D, 0.02D)
        ), 2.2F, 4, true);
        style("faceted_pipe", "profiled", PipeStyleShape.FACETED, "faceted_basic", BODY, List.of(
                parameter(PARAM_RADIUS, 0.22D, 0.12D, 0.62D, 0.01D)
        ), 1.8F, 5, false);
        style("monorail_pipe", "track", PipeStyleShape.MONORAIL, "monorail_basic", BEAM_TRACK, List.of(
                parameter(PARAM_WIDTH, 0.36D, 0.18D, 1.10D, 0.02D),
                parameter(PARAM_HEIGHT, 0.32D, 0.14D, 1.00D, 0.02D),
                parameter(PARAM_EDGE_WIDTH, 0.060D, 0.025D, 0.18D, 0.005D)
        ), 2.0F, 5, false);
        style("covered_tube", "tech", PipeStyleShape.COVERED, "covered_half", BASE_CANOPY, List.of(
                parameter(PARAM_WIDTH, 1.25D, 1.05D, 1.95D, 0.02D),
                parameter(PARAM_HEIGHT, 2.00D, 1.88D, 3.00D, 0.02D),
                parameter(PARAM_RIM_WIDTH, 0.070D, 0.035D, 0.22D, 0.005D)
        ), 2.1F, 4, true);
    }

    private static void registerVariants() {
        variant("round_basic", "round_pipe", BODY, 1.00D, 1.00F, false, false, false, false);
        variant("round_ribbed", "round_pipe", BODY_RIB, 1.00D, 1.02F, false, false, true, false);

        variant("box_basic", "box_pipe", BODY, 1.00D, 1.00F, false, false, false, false);
        variant("box_split", "box_pipe", CAP_SIDE, 1.00D, 1.00F, true, false, false, false);
        variant("box_ribbed", "box_pipe", BODY_RIB, 1.00D, 1.02F, false, false, true, false);

        variant("triangle_basic", "triangle_pipe", TOP_SIDE, 1.00D, 1.00F, false, false, false, false);
        variant("triangle_keel", "triangle_pipe", TOP_SIDE_KEEL, 1.00D, 1.06F, false, true, false, false);

        variant("rail_basic", "rail_pipe", RAIL_TIE, 1.00D, 1.00F, false, false, false, false);
        variant("rail_heavy", "rail_pipe", RAIL_TIE_BED, 1.00D, 1.08F, false, true, false, false);

        variant("slide_basic", "slide_pipe", FLOOR_WALL, 1.00D, 1.00F, false, false, false, false);
        variant("slide_rim", "slide_pipe", FLOOR_WALL_RIM, 1.00D, 1.08F, false, true, false, false);
        variant("slide_curved", "slide_pipe", FLOOR_WALL, 1.00D, 1.00F, false, false, false, true);
        variant("slide_curved_rim", "slide_pipe", FLOOR_WALL_RIM, 1.00D, 1.08F, false, true, false, true);

        variant("faceted_basic", "faceted_pipe", BODY, 1.00D, 1.00F, false, false, false, false);
        variant("faceted_edge", "faceted_pipe", BODY_EDGE, 1.00D, 1.05F, false, true, false, false);
        variant("faceted_ribbed", "faceted_pipe", BODY_RIB, 1.00D, 1.04F, false, false, true, false);

        variant("monorail_basic", "monorail_pipe", BEAM_TRACK, 1.00D, 1.00F, false, false, false, false);
        variant("monorail_heavy", "monorail_pipe", BEAM_TRACK_EDGE, 1.00D, 1.08F, false, true, false, false);

        variant("covered_half", "covered_tube", BASE_CANOPY, 1.00D, 1.00F, false, false, false, false);
        variant("covered_framed", "covered_tube", BASE_CANOPY_FRAME, 1.00D, 1.06F, false, true, false, false);
        variant("covered_ringed", "covered_tube", BASE_CANOPY_FRAME, 1.00D, 1.06F, false, true, true, false);
    }

    private static void style(String id, String category, PipeStyleShape shape, String defaultVariantId, List<MaterialSlotDefinition> slots, List<PipeStyleParameterDefinition> parameters, float lineWidth, int ringInterval, boolean openTop) {
        STYLES.put(id, new PipeStyleDefinition(id, "pipe_appearance.superpipeslide.style." + id, "pipe_appearance.superpipeslide.style." + id + ".subtitle", category, shape, defaultVariantId, slots, parameters, lineWidth, ringInterval, openTop));
    }

    private static void variant(String id, String styleId, List<MaterialSlotDefinition> slots, double sizeMultiplier, float lineWidthMultiplier, boolean splitCoating, boolean reinforced, boolean extraRibs, boolean curved) {
        VARIANTS.put(id, new PipeVariantDefinition(id, styleId, "pipe_appearance.superpipeslide.variant." + id, "pipe_appearance.superpipeslide.variant." + id + ".subtitle", slots, sizeMultiplier, lineWidthMultiplier, splitCoating, reinforced, extraRibs, curved));
    }

    private static List<MaterialSlotDefinition> slots(String... ids) {
        List<MaterialSlotDefinition> slots = new ArrayList<>();
        for (String id : ids) {
            slots.add(new MaterialSlotDefinition(id, "pipe_appearance.superpipeslide.slot." + id));
        }
        return List.copyOf(slots);
    }

    private static PipeStyleParameterDefinition parameter(String id, double defaultValue, double minValue, double maxValue, double step) {
        return new PipeStyleParameterDefinition(id, "pipe_appearance.superpipeslide.parameter." + id, defaultValue, minValue, maxValue, step);
    }
}
