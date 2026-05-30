package dev.marblegate.superpipeslide.client.core.navigation;

import dev.marblegate.superpipeslide.client.core.route.RouteCandidate;
import java.util.List;
import java.util.Optional;

public final class StationEntryPolicy {
    private StationEntryPolicy() {}

    public static StationEntryDecision resolve(StationEntryMode mode, List<RouteCandidate> candidates) {
        if (candidates.isEmpty()) {
            return StationEntryDecision.passThrough();
        }
        return switch (mode) {
            case ACTIVE_BOARDING -> activeBoarding(candidates);
            case FREE_SLIDE_ENTRY -> StationEntryDecision.openChoice(candidates, false);
            case ROUTE_CHECKPOINT -> StationEntryDecision.passThrough();
            case FUTURE_CROSS_LINE_NAVIGATION -> crossLineNavigation(candidates)
                    .map(StationEntryDecision::autoEnter)
                    .orElseGet(() -> activeBoarding(candidates));
        };
    }

    private static StationEntryDecision activeBoarding(List<RouteCandidate> candidates) {
        return candidates.size() == 1
                ? StationEntryDecision.autoEnter(candidates.getFirst())
                : StationEntryDecision.openChoice(candidates, true);
    }

    private static Optional<RouteCandidate> crossLineNavigation(List<RouteCandidate> candidates) {
        // Reserved for future cross-line navigation: when a target route is active,
        // this hook can select the best outgoing layout without changing capture flow.
        return Optional.empty();
    }
}
