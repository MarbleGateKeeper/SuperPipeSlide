package dev.marblegate.superpipeslide.common.core.route.service;

import dev.marblegate.superpipeslide.common.core.route.model.decision.RouteBranchDecision;
import dev.marblegate.superpipeslide.common.core.route.model.decision.RouteConnectionStepDecision;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;

import java.util.Optional;
import java.util.UUID;

public final class RouteChoiceResolver {
    private RouteChoiceResolver() {
    }

    public static Optional<UUID> routeChoiceFor(
            RouteSection routeSection,
            int routeDirection,
            UUID currentConnectionId,
            UUID branchNodeId
    ) {
        int direction = routeDirection < 0 ? -1 : 1;
        if (routeSection.statusForDirection(direction) != RouteSectionStatus.VALID) {
            return Optional.empty();
        }
        return routeSection.branchDecisionsForDirection(direction).stream()
                .filter(decision -> decision.branchNodeId().equals(branchNodeId))
                .filter(decision -> decision.incomingConnectionId().equals(currentConnectionId))
                .map(RouteBranchDecision::selectedConnectionId)
                .findFirst()
                .or(() -> routeConnectionStepFor(routeSection, direction, currentConnectionId, branchNodeId));
    }

    public static Optional<UUID> routeConnectionStepFor(
            RouteSection routeSection,
            int routeDirection,
            UUID currentConnectionId,
            UUID branchNodeId
    ) {
        int direction = routeDirection < 0 ? -1 : 1;
        if (routeSection.statusForDirection(direction) != RouteSectionStatus.VALID) {
            return Optional.empty();
        }
        return routeSection.stepDecisionsForDirection(direction).stream()
                .filter(decision -> decision.branchNodeId().equals(branchNodeId))
                .filter(decision -> decision.incomingConnectionId().equals(currentConnectionId))
                .map(RouteConnectionStepDecision::selectedConnectionId)
                .findFirst();
    }
}

