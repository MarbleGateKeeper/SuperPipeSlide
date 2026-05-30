package dev.marblegate.superpipeslide.common.core.route.storage;

import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationTransferLink;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class RouteNetworkStore {
    final Map<UUID, StationGroup> stationGroups = new LinkedHashMap<>();
    final Map<UUID, PlatformStop> platformStops = new LinkedHashMap<>();
    final Map<UUID, RouteLine> routeLines = new LinkedHashMap<>();
    final Map<UUID, RouteLayout> routeLayouts = new LinkedHashMap<>();
    final Map<UUID, RouteSection> routeSections = new LinkedHashMap<>();
    final Map<UUID, StationTransferLink> stationTransferLinks = new LinkedHashMap<>();

    RouteNetworkStore() {}

    RouteNetworkStore(List<StationGroup> stationGroups, List<PlatformStop> platformStops, List<RouteLine> routeLines, List<RouteLayout> routeLayouts, List<RouteSection> routeSections, List<StationTransferLink> stationTransferLinks) {
        stationGroups.forEach(this::put);
        platformStops.forEach(this::put);
        routeLines.forEach(this::put);
        routeLayouts.forEach(this::put);
        routeSections.forEach(this::put);
        stationTransferLinks.forEach(this::put);
    }

    Collection<StationGroup> stationGroups() {
        return List.copyOf(this.stationGroups.values());
    }

    Collection<PlatformStop> platformStops() {
        return List.copyOf(this.platformStops.values());
    }

    Collection<RouteLine> routeLines() {
        return List.copyOf(this.routeLines.values());
    }

    Collection<RouteLayout> routeLayouts() {
        return List.copyOf(this.routeLayouts.values());
    }

    Collection<RouteSection> routeSections() {
        return List.copyOf(this.routeSections.values());
    }

    Collection<StationTransferLink> stationTransferLinks() {
        return List.copyOf(this.stationTransferLinks.values());
    }

    Collection<StationGroup> stationGroupValues() {
        return this.stationGroups.values();
    }

    Collection<PlatformStop> platformStopValues() {
        return this.platformStops.values();
    }

    Collection<RouteLine> routeLineValues() {
        return this.routeLines.values();
    }

    Collection<RouteLayout> routeLayoutValues() {
        return this.routeLayouts.values();
    }

    Collection<RouteSection> routeSectionValues() {
        return this.routeSections.values();
    }

    Collection<StationTransferLink> stationTransferLinkValues() {
        return this.stationTransferLinks.values();
    }

    Optional<StationGroup> stationGroup(UUID id) {
        return Optional.ofNullable(this.stationGroups.get(id));
    }

    Optional<PlatformStop> platformStop(UUID id) {
        return Optional.ofNullable(this.platformStops.get(id));
    }

    Optional<RouteLine> routeLine(UUID id) {
        return Optional.ofNullable(this.routeLines.get(id));
    }

    Optional<RouteLayout> routeLayout(UUID id) {
        return Optional.ofNullable(this.routeLayouts.get(id));
    }

    Optional<RouteSection> routeSection(UUID id) {
        return Optional.ofNullable(this.routeSections.get(id));
    }

    Optional<StationTransferLink> stationTransferLink(UUID id) {
        return Optional.ofNullable(this.stationTransferLinks.get(id));
    }

    boolean hasPlatformStop(UUID id) {
        return this.platformStops.containsKey(id);
    }

    void put(StationGroup stationGroup) {
        this.stationGroups.put(stationGroup.id(), stationGroup);
    }

    void put(PlatformStop platformStop) {
        this.platformStops.put(platformStop.id(), platformStop);
    }

    void put(RouteLine routeLine) {
        this.routeLines.put(routeLine.id(), routeLine);
    }

    void put(RouteLayout routeLayout) {
        this.routeLayouts.put(routeLayout.id(), routeLayout);
    }

    void put(RouteSection routeSection) {
        this.routeSections.put(routeSection.id(), routeSection);
    }

    void put(StationTransferLink stationTransferLink) {
        this.stationTransferLinks.put(stationTransferLink.id(), stationTransferLink);
    }

    StationGroup removeStationGroup(UUID id) {
        return this.stationGroups.remove(id);
    }

    PlatformStop removePlatformStop(UUID id) {
        return this.platformStops.remove(id);
    }

    RouteLine removeRouteLine(UUID id) {
        return this.routeLines.remove(id);
    }

    RouteLayout removeRouteLayout(UUID id) {
        return this.routeLayouts.remove(id);
    }

    RouteSection removeRouteSection(UUID id) {
        return this.routeSections.remove(id);
    }

    StationTransferLink removeStationTransferLink(UUID id) {
        return this.stationTransferLinks.remove(id);
    }

    int routeLayoutCount() {
        return this.routeLayouts.size();
    }

    int routeSectionCount() {
        return this.routeSections.size();
    }

    boolean hasRouteLayouts() {
        return !this.routeLayouts.isEmpty();
    }
}
