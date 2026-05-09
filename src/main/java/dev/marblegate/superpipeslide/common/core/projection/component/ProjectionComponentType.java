package dev.marblegate.superpipeslide.common.core.projection.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

public enum ProjectionComponentType {
    BACKGROUND_PANEL,
    STATION_TITLE_GROUP,
    STATION_NAME_TEXT,
    TRANSLATION_TEXT,
    CUSTOM_TEXT,
    EXIT_BADGE,
    DIVIDER,
    ROUTE_LIST,
    ROUTE_TEXT,
    ROUTE_ICONS,
    ROUTE_OUTLINE_ICONS,
    ROUTE_CAPSULES,
    ROUTE_BACKPLATE,
    PLATFORM_TITLE_GROUP,
    PLATFORM_BADGE,
    PLATFORM_DIRECTION_TITLE,
    PLATFORM_STATUS_TAGS,
    PLATFORM_LINE_CURRENT,
    PLATFORM_LINE_BAND,
    PLATFORM_LINE_ICON,
    PLATFORM_TERMINAL_STRIP,
    PLATFORM_TRANSFER_LIST,
    PLATFORM_TRANSFER_MATRIX,
    PLATFORM_LAYOUT_STOP_LIST,
    PLATFORM_LAYOUT_PHYSICAL_MAP,
    PLATFORM_LAYOUT_PRACTICAL_MAP,
    PLATFORM_LAYOUT_SCHEMATIC_MAP,
    PLATFORM_LAYOUT_EDITOR_MAP,
    BUILTIN_ICON,
    NETWORK_IMAGE;

    public static final Codec<ProjectionComponentType> CODEC = Codec.STRING.xmap(ProjectionComponentType::byName, ProjectionComponentType::name);
    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectionComponentType> STREAM_CODEC = NeoForgeStreamCodecs.enumCodec(ProjectionComponentType.class);

    public static ProjectionComponentType byName(String name) {
        if (name != null) {
            for (ProjectionComponentType type : values()) {
                if (type.name().equalsIgnoreCase(name.trim())) {
                    return type;
                }
            }
        }
        return CUSTOM_TEXT;
    }

    public MapCodec<? extends ProjectionComponentSettings> settingsCodec() {
        return switch (this) {
            case BACKGROUND_PANEL -> ProjectionComponentSettings.Panel.CODEC.fieldOf("data");
            case STATION_TITLE_GROUP -> ProjectionComponentSettings.StationTitleGroup.CODEC.fieldOf("data");
            case STATION_NAME_TEXT, TRANSLATION_TEXT, CUSTOM_TEXT -> ProjectionComponentSettings.Text.codec(this).fieldOf("data");
            case EXIT_BADGE -> ProjectionComponentSettings.ExitBadge.CODEC.fieldOf("data");
            case DIVIDER -> ProjectionComponentSettings.Divider.CODEC.fieldOf("data");
            case ROUTE_LIST -> ProjectionComponentSettings.RouteList.CODEC.fieldOf("data");
            case ROUTE_TEXT -> ProjectionComponentSettings.RouteText.CODEC.fieldOf("data");
            case ROUTE_ICONS, ROUTE_OUTLINE_ICONS -> ProjectionComponentSettings.RouteIcon.codec(this).fieldOf("data");
            case ROUTE_CAPSULES -> ProjectionComponentSettings.RouteCapsules.CODEC.fieldOf("data");
            case ROUTE_BACKPLATE -> ProjectionComponentSettings.RouteBackplate.CODEC.fieldOf("data");
            case PLATFORM_TITLE_GROUP -> ProjectionComponentSettings.PlatformTitleGroup.CODEC.fieldOf("data");
            case PLATFORM_BADGE -> ProjectionComponentSettings.PlatformBadge.CODEC.fieldOf("data");
            case PLATFORM_DIRECTION_TITLE -> ProjectionComponentSettings.PlatformDirection.CODEC.fieldOf("data");
            case PLATFORM_STATUS_TAGS -> ProjectionComponentSettings.PlatformStatusTags.CODEC.fieldOf("data");
            case PLATFORM_LINE_CURRENT, PLATFORM_LINE_BAND, PLATFORM_TERMINAL_STRIP -> ProjectionComponentSettings.PlatformLine.codec(this).fieldOf("data");
            case PLATFORM_LINE_ICON -> ProjectionComponentSettings.PlatformLineIcon.CODEC.fieldOf("data");
            case PLATFORM_TRANSFER_LIST -> ProjectionComponentSettings.PlatformTransferList.CODEC.fieldOf("data");
            case PLATFORM_TRANSFER_MATRIX -> ProjectionComponentSettings.PlatformTransferMatrix.CODEC.fieldOf("data");
            case PLATFORM_LAYOUT_STOP_LIST, PLATFORM_LAYOUT_PHYSICAL_MAP, PLATFORM_LAYOUT_PRACTICAL_MAP, PLATFORM_LAYOUT_SCHEMATIC_MAP, PLATFORM_LAYOUT_EDITOR_MAP -> ProjectionComponentSettings.PlatformLayoutMap.codec(this).fieldOf("data");
            case BUILTIN_ICON -> ProjectionComponentSettings.BuiltinIcon.CODEC.fieldOf("data");
            case NETWORK_IMAGE -> ProjectionComponentSettings.NetworkImage.CODEC.fieldOf("data");
        };
    }

    public ProjectionComponentSettings decodeSettings(RegistryFriendlyByteBuf buffer) {
        return switch (this) {
            case BACKGROUND_PANEL -> ProjectionComponentSettings.Panel.decode(buffer);
            case STATION_TITLE_GROUP -> ProjectionComponentSettings.StationTitleGroup.decode(buffer);
            case STATION_NAME_TEXT, TRANSLATION_TEXT, CUSTOM_TEXT -> ProjectionComponentSettings.Text.decode(buffer, this);
            case EXIT_BADGE -> ProjectionComponentSettings.ExitBadge.decode(buffer);
            case DIVIDER -> ProjectionComponentSettings.Divider.decode(buffer);
            case ROUTE_LIST -> ProjectionComponentSettings.RouteList.decode(buffer);
            case ROUTE_TEXT -> ProjectionComponentSettings.RouteText.decode(buffer);
            case ROUTE_ICONS, ROUTE_OUTLINE_ICONS -> ProjectionComponentSettings.RouteIcon.decode(buffer, this);
            case ROUTE_CAPSULES -> ProjectionComponentSettings.RouteCapsules.decode(buffer);
            case ROUTE_BACKPLATE -> ProjectionComponentSettings.RouteBackplate.decode(buffer);
            case PLATFORM_TITLE_GROUP -> ProjectionComponentSettings.PlatformTitleGroup.decode(buffer);
            case PLATFORM_BADGE -> ProjectionComponentSettings.PlatformBadge.decode(buffer);
            case PLATFORM_DIRECTION_TITLE -> ProjectionComponentSettings.PlatformDirection.decode(buffer);
            case PLATFORM_STATUS_TAGS -> ProjectionComponentSettings.PlatformStatusTags.decode(buffer);
            case PLATFORM_LINE_CURRENT, PLATFORM_LINE_BAND, PLATFORM_TERMINAL_STRIP -> ProjectionComponentSettings.PlatformLine.decode(buffer, this);
            case PLATFORM_LINE_ICON -> ProjectionComponentSettings.PlatformLineIcon.decode(buffer);
            case PLATFORM_TRANSFER_LIST -> ProjectionComponentSettings.PlatformTransferList.decode(buffer);
            case PLATFORM_TRANSFER_MATRIX -> ProjectionComponentSettings.PlatformTransferMatrix.decode(buffer);
            case PLATFORM_LAYOUT_STOP_LIST, PLATFORM_LAYOUT_PHYSICAL_MAP, PLATFORM_LAYOUT_PRACTICAL_MAP, PLATFORM_LAYOUT_SCHEMATIC_MAP, PLATFORM_LAYOUT_EDITOR_MAP -> ProjectionComponentSettings.PlatformLayoutMap.decode(buffer, this);
            case BUILTIN_ICON -> ProjectionComponentSettings.BuiltinIcon.decode(buffer);
            case NETWORK_IMAGE -> ProjectionComponentSettings.NetworkImage.decode(buffer);
        };
    }

    public boolean textLike() {
        return this == STATION_TITLE_GROUP || this == STATION_NAME_TEXT || this == TRANSLATION_TEXT || this == CUSTOM_TEXT || this == PLATFORM_TITLE_GROUP || this == PLATFORM_DIRECTION_TITLE;
    }

    public boolean routeLike() {
        return this == ROUTE_LIST
                || this == ROUTE_TEXT
                || this == ROUTE_ICONS
                || this == ROUTE_OUTLINE_ICONS
                || this == ROUTE_CAPSULES
                || this == ROUTE_BACKPLATE;
    }

    public boolean platformLike() {
        return name().startsWith("PLATFORM_");
    }

    public boolean stationOnly() {
        return !platformLike() && this != BUILTIN_ICON && this != NETWORK_IMAGE;
    }
}
