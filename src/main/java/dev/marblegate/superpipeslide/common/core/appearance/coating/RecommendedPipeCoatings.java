package dev.marblegate.superpipeslide.common.core.appearance.coating;

import java.util.List;
import net.minecraft.resources.Identifier;

public final class RecommendedPipeCoatings {
    private static final List<Category> CATEGORIES = List.of(
            category("minimal_flavor",
                    "white_concrete",
                    "white_wool",
                    "white_terracotta"),
            category("basic_building",
                    "smooth_stone",
                    "stone_bricks",
                    "bricks",
                    "polished_andesite",
                    "deepslate_tiles",
                    "quartz_block"),
            category("warm_wood",
                    "oak_planks",
                    "spruce_planks",
                    "mangrove_planks",
                    "bamboo_planks",
                    "stripped_oak_log",
                    "stripped_cherry_log",
                    "stripped_dark_oak_log",
                    "barrel"),
            category("industrial_metal",
                    "iron_block",
                    "copper_block",
                    "cut_copper",
                    "oxidized_copper",
                    "netherite_block",
                    "anvil"),
            category("mineral_gems",
                    "amethyst_block",
                    "gold_block",
                    "diamond_block",
                    "emerald_block",
                    "lapis_block"),
            category("glass_tour",
                    "glass",
                    "tinted_glass",
                    "white_stained_glass",
                    "light_blue_stained_glass",
                    "red_stained_glass"),
            category("energy_signal",
                    "sea_lantern",
                    "glowstone",
                    "redstone_lamp",
                    "crying_obsidian",
                    "respawn_anchor",
                    "sculk"),
            category("natural_environment",
                    "moss_block",
                    "mud_bricks",
                    "packed_mud",
                    "calcite",
                    "tuff_bricks",
                    "prismarine"),
            category("nether_danger",
                    "blackstone",
                    "polished_blackstone_bricks",
                    "basalt",
                    "nether_bricks",
                    "crimson_planks",
                    "magma_block",
                    "shroomlight"),
            category("end_exotic",
                    "end_stone_bricks",
                    "purpur_block",
                    "purpur_pillar",
                    "obsidian",
                    "end_stone"));

    private RecommendedPipeCoatings() {}

    public static List<Category> categories() {
        return CATEGORIES;
    }

    private static Category category(String id, String... blocks) {
        return new Category(
                id,
                "pipe_appearance.superpipeslide.recommended_category." + id,
                List.of(blocks).stream()
                        .map(block -> new Entry(Identifier.withDefaultNamespace(block)))
                        .toList());
    }

    public record Category(String id, String nameKey, List<Entry> entries) {
        public Category {
            entries = List.copyOf(entries);
        }
    }

    public record Entry(Identifier blockId) {}
}
