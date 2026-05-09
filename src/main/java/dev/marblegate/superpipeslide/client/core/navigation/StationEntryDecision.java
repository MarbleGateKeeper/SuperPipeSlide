package dev.marblegate.superpipeslide.client.core.navigation;


import dev.marblegate.superpipeslide.client.core.route.RouteCandidate;
import java.util.List;

public record StationEntryDecision(Action action, List<RouteCandidate> candidates, boolean holdUntilSelected) {    public StationEntryDecision {
        candidates = List.copyOf(candidates);
    }    public static StationEntryDecision passThrough() {
        return new StationEntryDecision(Action.PASS_THROUGH, List.of(), false);
    }    public static StationEntryDecision autoEnter(RouteCandidate candidate) {
        return new StationEntryDecision(Action.AUTO_ENTER_ROUTE, List.of(candidate), false);
    }    public static StationEntryDecision openChoice(List<RouteCandidate> candidates, boolean holdUntilSelected) {
        return new StationEntryDecision(Action.OPEN_LAYOUT_CHOICE, candidates, holdUntilSelected);
    }    public RouteCandidate selectedCandidate() {
        return this.candidates.getFirst();
    }    public enum Action {
        PASS_THROUGH,
        AUTO_ENTER_ROUTE,
        OPEN_LAYOUT_CHOICE
    }
}
