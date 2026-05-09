package dev.marblegate.superpipeslide.common.core.route.sync;

import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionPathRecord;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationTransferLink;
import dev.marblegate.superpipeslide.network.sync.route.ClientboundRouteDataDeltaPayload;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RouteSyncTracker {
    private final Map<UUID, StationGroup> pendingUpdatedStationGroups = new LinkedHashMap<>();
    private final Set<UUID> pendingRemovedStationGroupIds = new LinkedHashSet<>();
    private final Map<UUID, PlatformStop> pendingUpdatedPlatformStops = new LinkedHashMap<>();
    private final Set<UUID> pendingRemovedPlatformStopIds = new LinkedHashSet<>();
    private final Map<UUID, RouteLine> pendingUpdatedRouteLines = new LinkedHashMap<>();
    private final Set<UUID> pendingRemovedRouteLineIds = new LinkedHashSet<>();
    private final Map<UUID, RouteLayout> pendingUpdatedRouteLayouts = new LinkedHashMap<>();
    private final Set<UUID> pendingRemovedRouteLayoutIds = new LinkedHashSet<>();
    private final Map<UUID, RouteSection> pendingUpdatedRouteSections = new LinkedHashMap<>();
    private final Set<UUID> pendingRemovedRouteSectionIds = new LinkedHashSet<>();
    private final Map<UUID, RouteSectionPathRecord> pendingUpdatedRouteSectionPaths = new LinkedHashMap<>();
    private final Set<UUID> pendingRemovedRouteSectionPathIds = new LinkedHashSet<>();
    private final Map<UUID, StationTransferLink> pendingUpdatedStationTransferLinks = new LinkedHashMap<>();
    private final Set<UUID> pendingRemovedStationTransferLinkIds = new LinkedHashSet<>();
    private long pendingBaseRevision = -1L;    public void captureBaseRevision(long revision) {
        if (this.pendingBaseRevision < 0L) {
            this.pendingBaseRevision = revision;
        }
    }    public ClientboundRouteDataDeltaPayload consume(long currentRevision, long pipeRevisionUsed) {
        long baseRevision = this.pendingBaseRevision >= 0L ? this.pendingBaseRevision : currentRevision;
        ClientboundRouteDataDeltaPayload payload = new ClientboundRouteDataDeltaPayload(
                baseRevision,
                currentRevision,
                pipeRevisionUsed,
                List.copyOf(this.pendingUpdatedStationGroups.values()),
                List.copyOf(this.pendingRemovedStationGroupIds),
                List.copyOf(this.pendingUpdatedPlatformStops.values()),
                List.copyOf(this.pendingRemovedPlatformStopIds),
                List.copyOf(this.pendingUpdatedRouteLines.values()),
                List.copyOf(this.pendingRemovedRouteLineIds),
                List.copyOf(this.pendingUpdatedRouteLayouts.values()),
                List.copyOf(this.pendingRemovedRouteLayoutIds),
                List.copyOf(this.pendingUpdatedRouteSections.values()),
                List.copyOf(this.pendingRemovedRouteSectionIds),
                List.copyOf(this.pendingUpdatedRouteSectionPaths.values()),
                List.copyOf(this.pendingRemovedRouteSectionPathIds),
                List.copyOf(this.pendingUpdatedStationTransferLinks.values()),
                List.copyOf(this.pendingRemovedStationTransferLinkIds)
        );
        this.clear();
        return payload;
    }    public void trackUpdated(StationGroup stationGroup) {
        this.pendingRemovedStationGroupIds.remove(stationGroup.id());
        this.pendingUpdatedStationGroups.put(stationGroup.id(), stationGroup);
    }    public void trackRemovedStationGroup(UUID id) {
        this.pendingUpdatedStationGroups.remove(id);
        this.pendingRemovedStationGroupIds.add(id);
    }    public void trackUpdated(PlatformStop platformStop) {
        this.pendingRemovedPlatformStopIds.remove(platformStop.id());
        this.pendingUpdatedPlatformStops.put(platformStop.id(), platformStop);
    }    public void trackRemovedPlatformStop(UUID id) {
        this.pendingUpdatedPlatformStops.remove(id);
        this.pendingRemovedPlatformStopIds.add(id);
    }    public void trackUpdated(RouteLine routeLine) {
        this.pendingRemovedRouteLineIds.remove(routeLine.id());
        this.pendingUpdatedRouteLines.put(routeLine.id(), routeLine);
    }    public void trackRemovedRouteLine(UUID id) {
        this.pendingUpdatedRouteLines.remove(id);
        this.pendingRemovedRouteLineIds.add(id);
    }    public void trackUpdated(RouteLayout routeLayout) {
        this.pendingRemovedRouteLayoutIds.remove(routeLayout.id());
        this.pendingUpdatedRouteLayouts.put(routeLayout.id(), routeLayout);
    }    public void trackRemovedRouteLayout(UUID id) {
        this.pendingUpdatedRouteLayouts.remove(id);
        this.pendingRemovedRouteLayoutIds.add(id);
    }    public void trackUpdated(RouteSection routeSection) {
        this.pendingRemovedRouteSectionIds.remove(routeSection.id());
        this.pendingUpdatedRouteSections.put(routeSection.id(), routeSection);
    }    public void trackRemovedRouteSection(UUID id) {
        this.pendingUpdatedRouteSections.remove(id);
        this.pendingRemovedRouteSectionIds.add(id);
    }    public void trackUpdated(RouteSectionPathRecord record) {
        this.pendingRemovedRouteSectionPathIds.remove(record.routeSectionId());
        this.pendingUpdatedRouteSectionPaths.put(record.routeSectionId(), record);
    }    public void trackRemovedRouteSectionPath(UUID id) {
        this.pendingUpdatedRouteSectionPaths.remove(id);
        this.pendingRemovedRouteSectionPathIds.add(id);
    }    public void trackUpdated(StationTransferLink link) {
        this.pendingRemovedStationTransferLinkIds.remove(link.id());
        this.pendingUpdatedStationTransferLinks.put(link.id(), link);
    }    public void trackRemovedStationTransferLink(UUID id) {
        this.pendingUpdatedStationTransferLinks.remove(id);
        this.pendingRemovedStationTransferLinkIds.add(id);
    }

    private void clear() {
        this.pendingUpdatedStationGroups.clear();
        this.pendingRemovedStationGroupIds.clear();
        this.pendingUpdatedPlatformStops.clear();
        this.pendingRemovedPlatformStopIds.clear();
        this.pendingUpdatedRouteLines.clear();
        this.pendingRemovedRouteLineIds.clear();
        this.pendingUpdatedRouteLayouts.clear();
        this.pendingRemovedRouteLayoutIds.clear();
        this.pendingUpdatedRouteSections.clear();
        this.pendingRemovedRouteSectionIds.clear();
        this.pendingUpdatedRouteSectionPaths.clear();
        this.pendingRemovedRouteSectionPathIds.clear();
        this.pendingUpdatedStationTransferLinks.clear();
        this.pendingRemovedStationTransferLinkIds.clear();
        this.pendingBaseRevision = -1L;
    }
}

