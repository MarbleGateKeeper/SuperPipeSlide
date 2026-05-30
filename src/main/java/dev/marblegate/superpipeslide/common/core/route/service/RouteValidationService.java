package dev.marblegate.superpipeslide.common.core.route.service;

import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.storage.RouteNetworkSavedData;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public final class RouteValidationService {
    private RouteValidationService() {}

    public static ValidationResult validateLayoutStops(RouteNetworkSavedData routes, RouteLayout layout, List<UUID> platformStopIds) {
        if (layout == null) {
            return ValidationResult.MISSING_LAYOUT_OR_PLATFORM;
        }
        if (platformStopIds.stream().anyMatch(id -> routes.platformStop(id).isEmpty())) {
            return ValidationResult.MISSING_LAYOUT_OR_PLATFORM;
        }
        if (platformStopIds.stream()
                .map(id -> routes.platformStop(id).orElseThrow())
                .anyMatch(stop -> routes.stationGroup(stop.stationGroupId()).isEmpty())) {
            return ValidationResult.MISSING_STATION_GROUP;
        }
        if (platformStopIds.stream()
                .map(id -> routes.platformStop(id).orElseThrow())
                .anyMatch(stop -> routes.isPlatformStopUnavailableForLayout(stop, layout.routeLineId(), layout.id()))) {
            return ValidationResult.PLATFORM_ASSIGNED_TO_OTHER_ROUTE;
        }
        if (new HashSet<>(platformStopIds).size() != platformStopIds.size()) {
            return ValidationResult.DUPLICATE_PLATFORM_STOP;
        }
        return ValidationResult.OK;
    }

    public enum ValidationResult {
        OK,
        MISSING_LAYOUT_OR_PLATFORM,
        MISSING_STATION_GROUP,
        PLATFORM_ASSIGNED_TO_OTHER_ROUTE,
        DUPLICATE_PLATFORM_STOP
    }
}
