package dev.marblegate.superpipeslide.common.core.route.service;

import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public final class RouteLayoutNavigator {
    private RouteLayoutNavigator() {}

    public static boolean containsStop(RouteLayout layout, UUID platformStopId) {
        return layout.orderedPlatformStops().contains(platformStopId);
    }

    public static Optional<Integer> normalizeDirection(RouteLayout layout, int routeDirection) {
        if (routeDirection < 0) {
            return layout.bidirectional() ? Optional.of(-1) : Optional.empty();
        }
        return Optional.of(1);
    }

    public static Optional<UUID> nextPlatformStopId(RouteLayout layout, UUID platformStopId, int routeDirection) {
        Optional<StepPosition> position = nextStepPosition(layout, platformStopId, routeDirection);
        return position.map(StepPosition::nextPlatformStopId);
    }

    public static boolean isTerminal(RouteLayout layout, UUID platformStopId, int routeDirection) {
        return containsStop(layout, platformStopId) && nextStepPosition(layout, platformStopId, routeDirection).isEmpty();
    }

    public static Optional<RouteStep> nextStep(RouteLayout layout, UUID platformStopId, int routeDirection, Function<UUID, Optional<RouteSection>> sectionLookup) {
        Optional<StepPosition> position = nextStepPosition(layout, platformStopId, routeDirection);
        if (position.isEmpty()) {
            return Optional.empty();
        }
        UUID sectionId = layout.orderedSectionRefs().get(position.get().sectionIndex()).routeSectionId();
        return sectionLookup.apply(sectionId)
                .map(section -> new RouteStep(section, position.get().nextPlatformStopId(), position.get().sectionIndex()));
    }

    private static Optional<StepPosition> nextStepPosition(RouteLayout layout, UUID platformStopId, int routeDirection) {
        if (normalizeDirection(layout, routeDirection).isEmpty()) {
            return Optional.empty();
        }
        List<UUID> stops = layout.orderedPlatformStops();
        int index = stops.indexOf(platformStopId);
        if (index < 0 || stops.size() < 2 || layout.orderedSectionRefs().isEmpty()) {
            return Optional.empty();
        }
        if (routeDirection >= 0) {
            if (index + 1 < stops.size()) {
                return Optional.of(new StepPosition(index, stops.get(index + 1)));
            }
            if (layout.loop() && layout.orderedSectionRefs().size() >= stops.size()) {
                return Optional.of(new StepPosition(stops.size() - 1, stops.getFirst()));
            }
            return Optional.empty();
        }

        if (index > 0) {
            return Optional.of(new StepPosition(index - 1, stops.get(index - 1)));
        }
        if (layout.loop() && layout.orderedSectionRefs().size() >= stops.size()) {
            return Optional.of(new StepPosition(stops.size() - 1, stops.getLast()));
        }
        return Optional.empty();
    }

    public record RouteStep(RouteSection section, UUID nextPlatformStopId, int sectionIndex) {}

    private record StepPosition(int sectionIndex, UUID nextPlatformStopId) {}
}
