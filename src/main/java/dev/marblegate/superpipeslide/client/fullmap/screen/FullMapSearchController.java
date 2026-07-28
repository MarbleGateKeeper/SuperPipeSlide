package dev.marblegate.superpipeslide.client.fullmap.screen;

import dev.marblegate.superpipeslide.client.core.navigation.ClientNavigationController;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.fullmap.model.search.SearchKind;
import dev.marblegate.superpipeslide.client.fullmap.model.search.SearchResult;
import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapText;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Owns the full route map's search state and result caches for {@code FullRouteMapScreen}.
 * The screen keeps the {@code EditBox} widget, focus management, rendering, and the
 * side effects of choosing a result (opening cards, switching dimensions); this class
 * only tracks the expanded flag, the adopted session text, and the two revision-keyed
 * result caches (destination stations from the navigation controller, route lines and
 * layouts from the route data cache).
 *
 * <p>No rendering, no screen back-reference: every method takes its dependencies as
 * parameters, so the dependency points one way only: screen &rarr; controller.
 */
final class FullMapSearchController {
    private boolean expanded;
    private String restoredText = "";

    private String cachedNavigationQuery = "";
    private List<ClientNavigationController.DestinationSearchResult> cachedNavigationResults = List.of();
    private long cachedNavigationRouteRevision = Long.MIN_VALUE;
    private long cachedNavigationPipeRevision = Long.MIN_VALUE;
    private ResourceKey<Level> cachedNavigationLevelKey;

    private String cachedSearchQuery = "";
    private List<SearchResult> cachedSearchResults = List.of();
    private long cachedSearchRouteRevision = Long.MIN_VALUE;
    private ResourceKey<Level> cachedSearchDimension;

    boolean expanded() {
        return this.expanded;
    }

    void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    /** Drops both result caches (route data snapshot/delta arrived). */
    void invalidate() {
        this.cachedNavigationQuery = "";
        this.cachedNavigationResults = List.of();
        this.cachedNavigationRouteRevision = Long.MIN_VALUE;
        this.cachedNavigationPipeRevision = Long.MIN_VALUE;
        this.cachedNavigationLevelKey = null;
        this.cachedSearchQuery = "";
        this.cachedSearchResults = List.of();
        this.cachedSearchRouteRevision = Long.MIN_VALUE;
        this.cachedSearchDimension = null;
    }

    /** Session text adopted at screen construction (restored after the widget exists). */
    String restoredText() {
        return this.restoredText;
    }

    void adoptText(String text) {
        this.restoredText = text == null ? "" : text;
    }

    /**
     * Destination-station results for the navigation search, cached by
     * (query, route/pipe revision, player dimension). Delegates the actual matching to
     * {@link ClientNavigationController#searchDestinations}; returns an empty list for
     * a blank query and resets the cache.
     */
    List<ClientNavigationController.DestinationSearchResult> destinationResults(LocalPlayer player, String query, int limit, long routeRevision, long pipeRevision) {
        if (query.isEmpty()) {
            this.cachedNavigationQuery = "";
            this.cachedNavigationResults = List.of();
            return List.of();
        }
        ResourceKey<Level> levelKey = player.level().dimension();
        boolean queryChanged = !query.equals(this.cachedNavigationQuery);
        if (queryChanged
                || this.cachedNavigationRouteRevision != routeRevision
                || this.cachedNavigationPipeRevision != pipeRevision
                || this.cachedNavigationLevelKey == null
                || !this.cachedNavigationLevelKey.equals(levelKey)) {
            this.cachedNavigationQuery = query;
            this.cachedNavigationRouteRevision = routeRevision;
            this.cachedNavigationPipeRevision = pipeRevision;
            this.cachedNavigationLevelKey = levelKey;
            this.cachedNavigationResults = ClientNavigationController.searchDestinations(player, query, limit);
        }
        return this.cachedNavigationResults;
    }

    /** True when the destination query changed on the latest {@link #destinationResults} call. */
    boolean navigationQueryChanged(String query) {
        return !query.equals(this.cachedNavigationQuery);
    }

    /**
     * Route-line / route-layout results for the map search, cached by
     * (query, route revision, active dimension). Matching is a case-insensitive
     * substring scan over the display-name stacks.
     */
    List<SearchResult> routeResults(String query, ResourceKey<Level> activeDimension, long routeRevision) {
        if (query.isEmpty()) {
            this.cachedSearchQuery = "";
            this.cachedSearchResults = List.of();
            return List.of();
        }
        if (!query.equals(this.cachedSearchQuery)
                || this.cachedSearchRouteRevision != routeRevision
                || !Objects.equals(this.cachedSearchDimension, activeDimension)) {
            this.cachedSearchQuery = query;
            this.cachedSearchRouteRevision = routeRevision;
            this.cachedSearchDimension = activeDimension;
            this.cachedSearchResults = computeRouteResults(query, activeDimension);
        }
        return this.cachedSearchResults;
    }

    private static List<SearchResult> computeRouteResults(String query, ResourceKey<Level> activeDimension) {
        List<SearchResult> results = new ArrayList<>();
        for (RouteLine line : ClientRouteDataCache.routeLines()) {
            dev.marblegate.superpipeslide.client.fullmap.ui.DisplayNameStack name = FullMapText.displayNameStack(line);
            if (name.searchText().toLowerCase(Locale.ROOT).contains(query)) {
                ResourceKey<Level> dimension = firstStationDimension(line).orElse(activeDimension == null ? Level.OVERWORLD : activeDimension);
                results.add(new SearchResult(SearchKind.ROUTE_LINE, line.id(), dimension, name, net.minecraft.network.chat.Component.translatable("screen.superpipeslide.route").getString()));
            }
        }
        for (RouteLayout layout : ClientRouteDataCache.routeLayouts()) {
            dev.marblegate.superpipeslide.client.fullmap.ui.DisplayNameStack name = FullMapText.displayNameStack(layout);
            if (name.searchText().toLowerCase(Locale.ROOT).contains(query)) {
                ResourceKey<Level> dimension = firstStationDimension(layout).orElse(activeDimension == null ? Level.OVERWORLD : activeDimension);
                results.add(new SearchResult(SearchKind.ROUTE_LAYOUT, layout.id(), dimension, name, net.minecraft.network.chat.Component.translatable("screen.superpipeslide.layout").getString()));
            }
        }
        return results.stream().limit(8).toList();
    }

    private static Optional<ResourceKey<Level>> firstStationDimension(RouteLine line) {
        return ClientRouteDataCache.routeLayoutsForLine(line.id()).stream().findFirst().flatMap(FullMapSearchController::firstStationDimension);
    }

    private static Optional<ResourceKey<Level>> firstStationDimension(RouteLayout layout) {
        return layout.orderedPlatformStops().stream()
                .map(ClientRouteDataCache::platformStop)
                .flatMap(Optional::stream)
                .findFirst()
                .flatMap(platform -> ClientRouteDataCache.stationGroup(platform.stationGroupId()))
                .map(dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup::levelKey);
    }
}
