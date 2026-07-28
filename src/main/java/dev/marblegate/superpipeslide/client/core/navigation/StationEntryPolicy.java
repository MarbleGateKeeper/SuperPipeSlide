package dev.marblegate.superpipeslide.client.core.navigation;

import dev.marblegate.superpipeslide.client.core.route.RouteCandidate;
import java.util.List;

public final class StationEntryPolicy {
    private StationEntryPolicy() {}

    public static StationEntryDecision resolve(StationEntryMode mode, List<RouteCandidate> candidates) {
        if (candidates.isEmpty()) {
            return StationEntryDecision.passThrough();
        }
        return switch (mode) {
            case ACTIVE_BOARDING -> activeBoarding(candidates);
            case FREE_SLIDE_ENTRY -> StationEntryDecision.openChoice(candidates, false);
        };
    }

    private static StationEntryDecision activeBoarding(List<RouteCandidate> candidates) {
        return candidates.size() == 1
                ? StationEntryDecision.autoEnter(candidates.getFirst())
                : StationEntryDecision.openChoice(candidates, true);
    }
}
