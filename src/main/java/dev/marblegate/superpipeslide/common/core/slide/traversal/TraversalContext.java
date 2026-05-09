package dev.marblegate.superpipeslide.common.core.slide.traversal;

import java.util.Optional;
import java.util.UUID;

public record TraversalContext(
        Optional<UUID> routeLayoutId,
        int routeDirection,
        Optional<UUID> currentPlatformStopId,
        Optional<UUID> currentRouteSectionId,
        int routeConnectionIndex,
        Optional<UUID> pendingBranchChoiceConnectionId,
        boolean allowAutomaticBranchChoice,
        RouteChoiceLookup routeChoiceLookup
) {
    public static final RouteChoiceLookup EMPTY_ROUTE_CHOICE_LOOKUP = (layoutId, routeDirection, currentPlatformStopId, currentRouteSectionId, routeConnectionIndex, currentConnectionId, branchNodeId) -> Optional.empty();

    public TraversalContext {
        routeLayoutId = routeLayoutId == null ? Optional.empty() : routeLayoutId;
        currentPlatformStopId = currentPlatformStopId == null ? Optional.empty() : currentPlatformStopId;
        currentRouteSectionId = currentRouteSectionId == null ? Optional.empty() : currentRouteSectionId;
        routeConnectionIndex = Math.max(0, routeConnectionIndex);
        pendingBranchChoiceConnectionId = pendingBranchChoiceConnectionId == null ? Optional.empty() : pendingBranchChoiceConnectionId;
        routeDirection = routeDirection < 0 ? -1 : 1;
        if (routeLayoutId.isEmpty()) {
            currentPlatformStopId = Optional.empty();
            currentRouteSectionId = Optional.empty();
            routeConnectionIndex = 0;
            routeDirection = 1;
        }
        routeChoiceLookup = routeChoiceLookup == null ? EMPTY_ROUTE_CHOICE_LOOKUP : routeChoiceLookup;
    }

    public boolean activeRoute() {
        return this.routeLayoutId.isPresent();
    }

    @FunctionalInterface
    public interface RouteChoiceLookup {
        Optional<RouteChoiceSelection> routeChoiceForCurrentStep(UUID layoutId, int routeDirection, Optional<UUID> currentPlatformStopId, Optional<UUID> currentRouteSectionId, int routeConnectionIndex, UUID currentConnectionId, UUID branchNodeId);
    }

    public record RouteChoiceSelection(UUID selectedConnectionId, int selectedConnectionIndex) {
        public RouteChoiceSelection {
            selectedConnectionIndex = Math.max(0, selectedConnectionIndex);
        }
    }
}
