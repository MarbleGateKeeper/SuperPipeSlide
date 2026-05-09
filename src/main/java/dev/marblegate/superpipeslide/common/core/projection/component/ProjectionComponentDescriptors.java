package dev.marblegate.superpipeslide.common.core.projection.component;

import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutTarget;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionRect;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.network.chat.Component;

public final class ProjectionComponentDescriptors {
    private static final EnumMap<ProjectionLayoutTarget, EnumMap<ProjectionComponentType, ProjectionComponentDescriptor>> DESCRIPTORS = new EnumMap<>(ProjectionLayoutTarget.class);
    private static final List<ProjectionVisibleCondition> STATION_BASE = List.of(ProjectionVisibleCondition.ALWAYS);
    private static final List<ProjectionVisibleCondition> STATION_DECOR = List.of(ProjectionVisibleCondition.ALWAYS, ProjectionVisibleCondition.HAS_ROUTES, ProjectionVisibleCondition.HAS_EXIT, ProjectionVisibleCondition.HAS_TRANSLATION);
    private static final List<ProjectionVisibleCondition> TRANSLATION = List.of(ProjectionVisibleCondition.HAS_TRANSLATION, ProjectionVisibleCondition.ALWAYS);
    private static final List<ProjectionVisibleCondition> EXIT = List.of(ProjectionVisibleCondition.HAS_EXIT, ProjectionVisibleCondition.ALWAYS);
    private static final List<ProjectionVisibleCondition> ROUTE = List.of(ProjectionVisibleCondition.HAS_ROUTES, ProjectionVisibleCondition.MULTI_ROUTE, ProjectionVisibleCondition.ALWAYS);
    private static final List<ProjectionVisibleCondition> PLATFORM = List.of(
            ProjectionVisibleCondition.ALWAYS,
            ProjectionVisibleCondition.HAS_PLATFORM,
            ProjectionVisibleCondition.HAS_PLATFORM_DISPLAY_NAME,
            ProjectionVisibleCondition.HAS_CURRENT_LINE,
            ProjectionVisibleCondition.HAS_ROUTE_LAYOUT,
            ProjectionVisibleCondition.HAS_NEXT_STOP,
            ProjectionVisibleCondition.HAS_PREVIOUS_STOP,
            ProjectionVisibleCondition.HAS_TERMINAL,
            ProjectionVisibleCondition.HAS_TRANSFERS,
            ProjectionVisibleCondition.HAS_OUT_OF_STATION_TRANSFERS,
            ProjectionVisibleCondition.MULTI_TRANSFER,
            ProjectionVisibleCondition.BIDIRECTIONAL_LAYOUT,
            ProjectionVisibleCondition.LOOP_LAYOUT
    );

    static {
        register(ProjectionComponentType.BACKGROUND_PANEL, ProjectionLayoutTarget.STATION_NAME, 0.12F, 0.12F, 1.40F, 0.48F, ProjectionComponentSettings.Panel.defaults(), ProjectionVisibleCondition.ALWAYS, STATION_DECOR);
        register(ProjectionComponentType.STATION_TITLE_GROUP, ProjectionLayoutTarget.STATION_NAME, 0.28F, 0.18F, 1.45F, 0.54F, ProjectionComponentSettings.StationTitleGroup.defaults(), ProjectionVisibleCondition.ALWAYS, STATION_BASE);
        register(ProjectionComponentType.STATION_NAME_TEXT, ProjectionLayoutTarget.STATION_NAME, 0.28F, 0.22F, 1.45F, 0.32F, ProjectionComponentSettings.Text.stationName(), ProjectionVisibleCondition.ALWAYS, STATION_BASE);
        register(ProjectionComponentType.TRANSLATION_TEXT, ProjectionLayoutTarget.STATION_NAME, 0.28F, 0.56F, 1.45F, 0.16F, ProjectionComponentSettings.Text.translationName(), ProjectionVisibleCondition.HAS_TRANSLATION, TRANSLATION);
        register(ProjectionComponentType.ROUTE_LIST, ProjectionLayoutTarget.STATION_NAME, 0.10F, 0.16F, 0.58F, 0.58F, ProjectionComponentSettings.RouteList.defaults(), ProjectionVisibleCondition.HAS_ROUTES, ROUTE);
        register(ProjectionComponentType.ROUTE_TEXT, ProjectionLayoutTarget.STATION_NAME, 0.22F, 0.66F, 0.90F, 0.16F, ProjectionComponentSettings.RouteText.defaults(), ProjectionVisibleCondition.HAS_ROUTES, ROUTE);
        register(ProjectionComponentType.ROUTE_ICONS, ProjectionLayoutTarget.STATION_NAME, 0.10F, 0.12F, 1.05F, 0.18F, ProjectionComponentSettings.RouteIcon.solidDefaults(), ProjectionVisibleCondition.HAS_ROUTES, ROUTE);
        register(ProjectionComponentType.ROUTE_OUTLINE_ICONS, ProjectionLayoutTarget.STATION_NAME, 0.10F, 0.12F, 1.08F, 0.20F, ProjectionComponentSettings.RouteIcon.outlineDefaults(), ProjectionVisibleCondition.HAS_ROUTES, ROUTE);
        register(ProjectionComponentType.ROUTE_CAPSULES, ProjectionLayoutTarget.STATION_NAME, 0.10F, 0.16F, 0.78F, 0.62F, ProjectionComponentSettings.RouteCapsules.defaults(), ProjectionVisibleCondition.HAS_ROUTES, ROUTE);
        register(ProjectionComponentType.ROUTE_BACKPLATE, ProjectionLayoutTarget.STATION_NAME, 0.0F, 0.0F, 0.82F, 0.95F, ProjectionComponentSettings.RouteBackplate.defaults(), ProjectionVisibleCondition.HAS_ROUTES, ROUTE);
        register(ProjectionComponentType.EXIT_BADGE, ProjectionLayoutTarget.STATION_NAME, 1.85F, 0.20F, 0.42F, 0.38F, ProjectionComponentSettings.ExitBadge.defaults(), ProjectionVisibleCondition.HAS_EXIT, EXIT);
        register(ProjectionComponentType.CUSTOM_TEXT, ProjectionLayoutTarget.STATION_NAME, 0.30F, 0.72F, 1.30F, 0.18F, ProjectionComponentSettings.Text.customText().withText(Component.translatable("screen.superpipeslide.projection_designer.custom_text.default").getString()), ProjectionVisibleCondition.ALWAYS, STATION_DECOR);
        register(ProjectionComponentType.DIVIDER, ProjectionLayoutTarget.STATION_NAME, 0.20F, 0.48F, 1.60F, 0.06F, ProjectionComponentSettings.Divider.defaults(), ProjectionVisibleCondition.ALWAYS, STATION_DECOR);
        register(ProjectionComponentType.BUILTIN_ICON, ProjectionLayoutTarget.STATION_NAME, 0.12F, 0.12F, 0.28F, 0.28F, ProjectionComponentSettings.BuiltinIcon.defaults(), ProjectionVisibleCondition.ALWAYS, STATION_DECOR);
        register(ProjectionComponentType.NETWORK_IMAGE, ProjectionLayoutTarget.STATION_NAME, 0.20F, 0.20F, 0.80F, 0.50F, ProjectionComponentSettings.NetworkImage.defaults(), ProjectionVisibleCondition.ALWAYS, STATION_DECOR);

        register(ProjectionComponentType.PLATFORM_TITLE_GROUP, ProjectionLayoutTarget.PLATFORM, 0.34F, 0.18F, 1.70F, 0.52F, ProjectionComponentSettings.PlatformTitleGroup.defaults(), ProjectionVisibleCondition.HAS_PLATFORM, PLATFORM);
        register(ProjectionComponentType.PLATFORM_BADGE, ProjectionLayoutTarget.PLATFORM, 0.16F, 0.18F, 0.56F, 0.34F, ProjectionComponentSettings.PlatformBadge.defaults(), ProjectionVisibleCondition.HAS_PLATFORM, PLATFORM);
        register(ProjectionComponentType.PLATFORM_DIRECTION_TITLE, ProjectionLayoutTarget.PLATFORM, 0.68F, 0.18F, 1.52F, 0.34F, ProjectionComponentSettings.PlatformDirection.defaults(), ProjectionVisibleCondition.HAS_ROUTE_LAYOUT, PLATFORM);
        register(ProjectionComponentType.PLATFORM_STATUS_TAGS, ProjectionLayoutTarget.PLATFORM, 0.28F, 0.56F, 1.42F, 0.20F, ProjectionComponentSettings.PlatformStatusTags.defaults(), ProjectionVisibleCondition.HAS_PLATFORM, PLATFORM);
        register(ProjectionComponentType.PLATFORM_LINE_CURRENT, ProjectionLayoutTarget.PLATFORM, 0.18F, 0.12F, 2.10F, 0.26F, ProjectionComponentSettings.PlatformLine.currentDefaults(), ProjectionVisibleCondition.HAS_CURRENT_LINE, PLATFORM);
        register(ProjectionComponentType.PLATFORM_LINE_BAND, ProjectionLayoutTarget.PLATFORM, 0.0F, 0.0F, 2.40F, 0.18F, ProjectionComponentSettings.PlatformLine.bandDefaults(), ProjectionVisibleCondition.HAS_CURRENT_LINE, PLATFORM);
        register(ProjectionComponentType.PLATFORM_LINE_ICON, ProjectionLayoutTarget.PLATFORM, 0.16F, 0.16F, 0.42F, 0.42F, ProjectionComponentSettings.PlatformLineIcon.defaults(), ProjectionVisibleCondition.HAS_CURRENT_LINE, PLATFORM);
        register(ProjectionComponentType.PLATFORM_TERMINAL_STRIP, ProjectionLayoutTarget.PLATFORM, 0.0F, 0.0F, 2.20F, 0.16F, ProjectionComponentSettings.PlatformLine.terminalDefaults(), ProjectionVisibleCondition.HAS_ROUTE_LAYOUT, PLATFORM);
        register(ProjectionComponentType.PLATFORM_TRANSFER_LIST, ProjectionLayoutTarget.PLATFORM, 1.74F, 0.18F, 1.20F, 0.68F, ProjectionComponentSettings.PlatformTransferList.defaults(), ProjectionVisibleCondition.HAS_TRANSFERS, PLATFORM);
        register(ProjectionComponentType.PLATFORM_TRANSFER_MATRIX, ProjectionLayoutTarget.PLATFORM, 1.72F, 0.18F, 1.10F, 0.58F, ProjectionComponentSettings.PlatformTransferMatrix.defaults(), ProjectionVisibleCondition.HAS_TRANSFERS, PLATFORM);
        register(ProjectionComponentType.PLATFORM_LAYOUT_STOP_LIST, ProjectionLayoutTarget.PLATFORM, 0.20F, 0.20F, 1.28F, 2.20F, ProjectionComponentSettings.PlatformLayoutMap.stopListDefaults(), ProjectionVisibleCondition.HAS_ROUTE_LAYOUT, PLATFORM);
        register(ProjectionComponentType.PLATFORM_LAYOUT_PHYSICAL_MAP, ProjectionLayoutTarget.PLATFORM, 0.20F, 0.26F, 3.20F, 0.86F, ProjectionComponentSettings.PlatformLayoutMap.physicalMapDefaults(), ProjectionVisibleCondition.HAS_ROUTE_LAYOUT, PLATFORM);
        register(ProjectionComponentType.PLATFORM_LAYOUT_PRACTICAL_MAP, ProjectionLayoutTarget.PLATFORM, 0.20F, 0.26F, 3.20F, 0.86F, ProjectionComponentSettings.PlatformLayoutMap.practicalMapDefaults(), ProjectionVisibleCondition.HAS_ROUTE_LAYOUT, PLATFORM);
        register(ProjectionComponentType.PLATFORM_LAYOUT_SCHEMATIC_MAP, ProjectionLayoutTarget.PLATFORM, 0.20F, 0.26F, 3.20F, 0.86F, ProjectionComponentSettings.PlatformLayoutMap.schematicMapDefaults(), ProjectionVisibleCondition.HAS_ROUTE_LAYOUT, PLATFORM);
        register(ProjectionComponentType.PLATFORM_LAYOUT_EDITOR_MAP, ProjectionLayoutTarget.PLATFORM, 0.18F, 0.24F, 3.60F, 0.72F, ProjectionComponentSettings.PlatformLayoutMap.editorMapDefaults(), ProjectionVisibleCondition.HAS_ROUTE_LAYOUT, PLATFORM);
        register(ProjectionComponentType.BUILTIN_ICON, ProjectionLayoutTarget.PLATFORM, 0.16F, 0.16F, 0.28F, 0.28F, ProjectionComponentSettings.BuiltinIcon.defaults(), ProjectionVisibleCondition.ALWAYS, PLATFORM);
        register(ProjectionComponentType.NETWORK_IMAGE, ProjectionLayoutTarget.PLATFORM, 0.24F, 0.24F, 0.96F, 0.58F, ProjectionComponentSettings.NetworkImage.defaults(), ProjectionVisibleCondition.ALWAYS, PLATFORM);
    }

    private ProjectionComponentDescriptors() {
    }

    public static ProjectionComponentDescriptor get(ProjectionComponentType type, ProjectionLayoutTarget target) {
        ProjectionComponentDescriptor descriptor = descriptorsFor(target).get(type);
        if (descriptor != null) {
            return descriptor;
        }
        if (shared(type)) {
            ProjectionComponentDescriptor base = descriptorsFor(ProjectionLayoutTarget.STATION_NAME).get(type);
            if (base != null) {
                return new ProjectionComponentDescriptor(type, target, base.defaultBounds(), base.defaultSettings(), ProjectionVisibleCondition.ALWAYS, target == ProjectionLayoutTarget.PLATFORM ? PLATFORM : STATION_DECOR);
            }
        }
        ProjectionComponentDescriptor fallback = descriptorsFor(target).get(ProjectionComponentType.CUSTOM_TEXT);
        if (fallback != null) {
            return fallback;
        }
        return descriptorsFor(ProjectionLayoutTarget.STATION_NAME).get(ProjectionComponentType.CUSTOM_TEXT);
    }

    public static ProjectionComponent create(ProjectionComponentType type, ProjectionLayoutTarget target, int layer) {
        return get(type, target).create(layer);
    }

    public static List<ProjectionVisibleCondition> allowedVisibleConditions(ProjectionComponentType type, ProjectionLayoutTarget target) {
        return get(type, target).visibleConditions();
    }

    public static ProjectionVisibleCondition normalizeVisibleCondition(ProjectionVisibleCondition value, ProjectionComponentType type, ProjectionLayoutTarget target) {
        List<ProjectionVisibleCondition> allowed = allowedVisibleConditions(type, target);
        return allowed.contains(value) ? value : allowed.getFirst();
    }

    public static ProjectionComponentType[] typesFor(ProjectionLayoutTarget target) {
        if (target == ProjectionLayoutTarget.PLATFORM) {
            return new ProjectionComponentType[] {
                    ProjectionComponentType.BACKGROUND_PANEL,
                    ProjectionComponentType.PLATFORM_TITLE_GROUP,
                    ProjectionComponentType.PLATFORM_BADGE,
                    ProjectionComponentType.PLATFORM_DIRECTION_TITLE,
                    ProjectionComponentType.PLATFORM_STATUS_TAGS,
                    ProjectionComponentType.PLATFORM_LINE_CURRENT,
                    ProjectionComponentType.PLATFORM_LINE_BAND,
                    ProjectionComponentType.PLATFORM_LINE_ICON,
                    ProjectionComponentType.PLATFORM_TERMINAL_STRIP,
                    ProjectionComponentType.PLATFORM_TRANSFER_LIST,
                    ProjectionComponentType.PLATFORM_TRANSFER_MATRIX,
                    ProjectionComponentType.PLATFORM_LAYOUT_STOP_LIST,
                    ProjectionComponentType.PLATFORM_LAYOUT_PHYSICAL_MAP,
                    ProjectionComponentType.PLATFORM_LAYOUT_PRACTICAL_MAP,
                    ProjectionComponentType.PLATFORM_LAYOUT_SCHEMATIC_MAP,
                    ProjectionComponentType.PLATFORM_LAYOUT_EDITOR_MAP,
                    ProjectionComponentType.BUILTIN_ICON,
                    ProjectionComponentType.NETWORK_IMAGE,
                    ProjectionComponentType.CUSTOM_TEXT,
                    ProjectionComponentType.DIVIDER
            };
        }
        return new ProjectionComponentType[] {
                ProjectionComponentType.BACKGROUND_PANEL,
                ProjectionComponentType.STATION_TITLE_GROUP,
                ProjectionComponentType.STATION_NAME_TEXT,
                ProjectionComponentType.TRANSLATION_TEXT,
                ProjectionComponentType.ROUTE_LIST,
                ProjectionComponentType.ROUTE_TEXT,
                ProjectionComponentType.ROUTE_ICONS,
                ProjectionComponentType.ROUTE_OUTLINE_ICONS,
                ProjectionComponentType.ROUTE_CAPSULES,
                ProjectionComponentType.ROUTE_BACKPLATE,
                ProjectionComponentType.EXIT_BADGE,
                ProjectionComponentType.BUILTIN_ICON,
                ProjectionComponentType.NETWORK_IMAGE,
                ProjectionComponentType.CUSTOM_TEXT,
                ProjectionComponentType.DIVIDER
        };
    }

    private static void register(ProjectionComponentType type, ProjectionLayoutTarget target, float x, float y, float width, float height, ProjectionComponentSettings settings, ProjectionVisibleCondition visible, List<ProjectionVisibleCondition> visibleConditions) {
        descriptorsFor(target).put(type, new ProjectionComponentDescriptor(type, target, new ProjectionRect(x, y, width, height), settings, visible, List.copyOf(visibleConditions)));
    }

    private static EnumMap<ProjectionComponentType, ProjectionComponentDescriptor> descriptorsFor(ProjectionLayoutTarget target) {
        ProjectionLayoutTarget safeTarget = target == null ? ProjectionLayoutTarget.STATION_NAME : target;
        return DESCRIPTORS.computeIfAbsent(safeTarget, ignored -> new EnumMap<>(ProjectionComponentType.class));
    }

    private static boolean shared(ProjectionComponentType type) {
        return type == ProjectionComponentType.BACKGROUND_PANEL || type == ProjectionComponentType.CUSTOM_TEXT || type == ProjectionComponentType.DIVIDER || type == ProjectionComponentType.BUILTIN_ICON || type == ProjectionComponentType.NETWORK_IMAGE;
    }
}
