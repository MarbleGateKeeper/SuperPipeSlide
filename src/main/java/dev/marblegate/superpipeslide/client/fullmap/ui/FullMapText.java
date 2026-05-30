package dev.marblegate.superpipeslide.client.fullmap.ui;

import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.fullmap.model.MapNode;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeKind;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNode;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;

public final class FullMapText {
    private FullMapText() {}

    public static String displayName(RouteLine line) {
        return displayNameStack(line).flat();
    }

    public static String displayName(RouteLayout layout) {
        return displayNameStack(layout).flat();
    }

    public static String displayName(StationGroup station) {
        return displayNameStack(station).flat();
    }

    public static String displayName(MapNode node) {
        return displayNameStack(node).flat();
    }

    public static String displayName(RouteCardNode node) {
        return displayNameStack(node).flat();
    }

    public static DisplayNameStack displayNameStack(RouteLine line) {
        return DisplayNameStack.of(line.displayName(), line.translatedNames());
    }

    public static DisplayNameStack displayNameStack(RouteLayout layout) {
        return DisplayNameStack.of(SPSGui.layoutName(layout), layout.translatedNames());
    }

    public static DisplayNameStack displayNameStack(StationGroup station) {
        return DisplayNameStack.of(station.primaryName(), station.translatedNames());
    }

    public static DisplayNameStack displayNameStack(MapNode node) {
        if (node.kind() == NodeKind.STATION && !node.stationGroupIds().isEmpty()) {
            Optional<StationGroup> station = ClientRouteDataCache.stationGroup(node.stationGroupIds().getFirst());
            if (station.isPresent()) {
                return displayNameStack(station.get());
            }
        }
        return DisplayNameStack.of(node.label());
    }

    public static DisplayNameStack displayNameStack(RouteCardNode node) {
        if (node.stationGroupId().isPresent()) {
            Optional<StationGroup> station = ClientRouteDataCache.stationGroup(node.stationGroupId().get());
            if (station.isPresent()) {
                DisplayNameStack stationName = displayNameStack(station.get());
                if (node.platformStopId().isPresent()) {
                    String platform = ClientRouteDataCache.platformStop(node.platformStopId().get())
                            .map(FullMapText::platformLabel)
                            .filter(name -> !name.isBlank())
                            .orElse("");
                    if (!platform.isBlank()) {
                        return new DisplayNameStack(stationName.primary() + " " + platform, stationName.secondary(), stationName.aliases());
                    }
                }
                return stationName;
            }
        }
        return DisplayNameStack.of(node.label());
    }

    private static String platformLabel(PlatformStop platformStop) {
        return platformStop.displayName()
                .filter(name -> !name.isBlank())
                .orElse("#" + platformStop.platformNumber());
    }

    public static String titleWithTranslation(String primary, List<String> translatedNames) {
        return DisplayNameStack.of(primary, translatedNames).flat();
    }

    public static String primaryName(RouteLine line) {
        return displayNameStack(line).primary();
    }

    public static String primaryName(RouteLayout layout) {
        return displayNameStack(layout).primary();
    }

    public static String primaryName(StationGroup station) {
        return displayNameStack(station).primary();
    }

    public static String primaryName(MapNode node) {
        return displayNameStack(node).primary();
    }

    public static String primaryName(RouteCardNode node) {
        return displayNameStack(node).primary();
    }

    public static String routeLineNames(List<UUID> routeLineIds) {
        return routeLineIds.stream()
                .distinct()
                .map(ClientRouteDataCache::routeLine)
                .flatMap(Optional::stream)
                .map(FullMapText::primaryName)
                .sorted()
                .reduce((a, b) -> a + " / " + b)
                .orElse(Component.translatable("screen.superpipeslide.route.missing").getString());
    }

    public static String combine(String primary, List<String> translatedNames) {
        String safePrimary = primary == null || primary.isBlank() ? "?" : primary.trim();
        String translated = translatedNames == null
                ? ""
                : translatedNames.stream()
                        .filter(name -> name != null && !name.isBlank())
                        .map(String::trim)
                        .filter(name -> !name.equalsIgnoreCase(safePrimary))
                        .distinct()
                        .reduce((a, b) -> a + " / " + b)
                        .orElse("");
        if (translated.isBlank()) {
            return safePrimary;
        }
        return safePrimary + " / " + translated;
    }
}
