package dev.marblegate.superpipeslide.common.core.projection.component;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;

public record ProjectionBuiltinIcon(String id, int row, int column, String translationKey) {

    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "textures/gui/icons.png");
    public static final int CELL_SIZE = 16;
    public static final int TEXTURE_WIDTH = 256;
    public static final int TEXTURE_HEIGHT = 64;

    private static final List<ProjectionBuiltinIcon> ICONS = List.of(
            icon("ui.back", 0, 0),
            icon("ui.plus", 0, 1),
            icon("ui.save", 0, 2),
            icon("ui.edit", 0, 3),
            icon("ui.remove", 0, 4),
            icon("ui.refresh", 0, 5),
            icon("ui.item_plus", 0, 6),
            icon("ui.search", 0, 7),
            icon("ui.drag", 0, 8),
            icon("ui.close", 0, 9),
            icon("ui.confirm", 0, 10),
            icon("ui.warning", 0, 11),
            icon("ui.checkbox_off", 0, 12),
            icon("ui.checkbox_on", 0, 13),
            icon("ui.color_add", 0, 14),
            icon("ui.info", 0, 15),
            icon("ui.layers", 3, 6),
            icon("ui.settings", 3, 7),
            icon("map.fit", 1, 0),
            icon("map.locate", 1, 1),
            icon("map.physical", 1, 2),
            icon("map.geographic", 1, 3),
            icon("map.practical", 1, 4),
            icon("map.schematic", 1, 5),
            icon("map.reset_view", 1, 6),
            icon("map.zoom_in", 1, 7),
            icon("map.zoom_out", 1, 8),
            icon("map.route_line", 1, 9),
            icon("map.layout", 1, 10),
            icon("map.station_order", 1, 11),
            icon("map.map", 1, 12),
            icon("route.bidirectional", 2, 0),
            icon("route.one_way", 2, 1),
            icon("route.loop", 2, 2),
            icon("route.split", 2, 3),
            icon("route.recalculate", 2, 4),
            icon("status.error", 2, 5),
            icon("status.success", 2, 6),
            icon("pipe.acceleration", 3, 0),
            icon("pipe.highway", 3, 1),
            icon("pipe.glow", 3, 2),
            icon("pipe.backpack", 3, 3),
            icon("pipe.recommended", 3, 4),
            icon("pipe.platform", 3, 5));
    private static final Map<String, ProjectionBuiltinIcon> BY_ID = ICONS.stream().collect(Collectors.toUnmodifiableMap(ProjectionBuiltinIcon::id, Function.identity()));
    private static final ProjectionBuiltinIcon FALLBACK = BY_ID.get("ui.info");
    private static ProjectionBuiltinIcon icon(String id, int row, int column) {
        return new ProjectionBuiltinIcon(id, row, column, "screen.superpipeslide.projection_designer.icon." + id.replace('.', '_'));
    }

    public static ProjectionBuiltinIcon byId(String id) {
        if (id != null) {
            ProjectionBuiltinIcon icon = BY_ID.get(id.trim());
            if (icon != null) {
                return icon;
            }
        }
        return FALLBACK;
    }

    public static List<ProjectionBuiltinIcon> all() {
        return ICONS;
    }

    public float u0() {
        return this.column * CELL_SIZE / (float) TEXTURE_WIDTH;
    }

    public float u1() {
        return (this.column + 1) * CELL_SIZE / (float) TEXTURE_WIDTH;
    }

    public float v0() {
        return this.row * CELL_SIZE / (float) TEXTURE_HEIGHT;
    }

    public float v1() {
        return (this.row + 1) * CELL_SIZE / (float) TEXTURE_HEIGHT;
    }
}
