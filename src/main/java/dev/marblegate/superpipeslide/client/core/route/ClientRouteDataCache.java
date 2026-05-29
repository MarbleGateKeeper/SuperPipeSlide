package dev.marblegate.superpipeslide.client.core.route;

import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import dev.marblegate.superpipeslide.common.core.networkgraph.branch.BranchNode;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionPath;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionPathRecord;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationTransferLink;
import dev.marblegate.superpipeslide.common.core.route.service.RouteChoiceResolver;
import dev.marblegate.superpipeslide.common.core.route.service.RouteLayoutNavigator;
import dev.marblegate.superpipeslide.common.core.slide.traversal.TraversalContext;
import dev.marblegate.superpipeslide.common.core.slide.traversal.TraversalContext.RouteChoiceSelection;
import dev.marblegate.superpipeslide.network.sync.route.ClientboundRouteDataDeltaPayload;
import dev.marblegate.superpipeslide.network.sync.route.ClientboundRouteDataSnapshotChunkPayload;
import dev.marblegate.superpipeslide.network.sync.route.ClientboundRouteDataSnapshotEndPayload;
import dev.marblegate.superpipeslide.network.sync.route.ClientboundRouteDataSnapshotStartPayload;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ClientRouteDataCache {
    private static final Map<UUID, StationGroup> STATION_GROUPS = new LinkedHashMap<>();
    private static final Map<UUID, PlatformStop> PLATFORM_STOPS = new LinkedHashMap<>();
    private static final Map<UUID, RouteLine> ROUTE_LINES = new LinkedHashMap<>();
    private static final Map<UUID, RouteLayout> ROUTE_LAYOUTS = new LinkedHashMap<>();
    private static final Map<UUID, RouteSection> ROUTE_SECTIONS = new LinkedHashMap<>();
    private static final Map<UUID, RouteSectionPath> ROUTE_SECTION_PATHS = new LinkedHashMap<>();
    private static final Map<UUID, StationTransferLink> STATION_TRANSFER_LINKS = new LinkedHashMap<>();
    private static final Map<UUID, List<PlatformStop>> PLATFORM_STOPS_BY_STATION = new HashMap<>();
    private static final Map<UUID, List<RouteLayout>> ROUTE_LAYOUTS_BY_LINE = new HashMap<>();
    private static final Map<UUID, List<RouteSection>> ROUTE_SECTIONS_BY_LAYOUT = new HashMap<>();
    private static final Map<UUID, List<UUID>> ROUTE_LAYOUT_IDS_BY_PLATFORM_STOP = new HashMap<>();
    private static final Map<UUID, List<StationTransferLink>> TRANSFER_LINKS_BY_STATION = new HashMap<>();
    private static SnapshotBuffer pendingSnapshot;
    private static long revision = -1L;
    private static long pipeRevisionUsed = -1L;

    private ClientRouteDataCache() {
    }

    public static void handleSnapshotStart(ClientboundRouteDataSnapshotStartPayload payload) {
        if (payload.revision() < revision) {
            pendingSnapshot = null;
            return;
        }
        pendingSnapshot = new SnapshotBuffer(payload.revision(), payload.pipeRevisionUsed(), payload.stationGroupCount(), payload.platformStopCount(), payload.routeLineCount(), payload.routeLayoutCount(), payload.routeSectionCount(), payload.routeSectionPathCount(), payload.stationTransferLinkCount(), payload.chunkCount());
    }

    public static void handleSnapshotChunk(ClientboundRouteDataSnapshotChunkPayload payload) {
        if (pendingSnapshot == null || pendingSnapshot.revision() != payload.revision()) {
            return;
        }
        ChunkAcceptResult acceptResult = pendingSnapshot.acceptChunk(payload);
        if (acceptResult == ChunkAcceptResult.DUPLICATE) {
            return;
        }
        if (acceptResult == ChunkAcceptResult.INVALID) {
            pendingSnapshot = null;
            return;
        }
        pendingSnapshot.stationGroups().addAll(payload.stationGroups());
        pendingSnapshot.platformStops().addAll(payload.platformStops());
        pendingSnapshot.routeLines().addAll(payload.routeLines());
        pendingSnapshot.routeLayouts().addAll(payload.routeLayouts());
        pendingSnapshot.routeSections().addAll(payload.routeSections());
        pendingSnapshot.routeSectionPaths().addAll(payload.routeSectionPaths());
        pendingSnapshot.stationTransferLinks().addAll(payload.stationTransferLinks());
    }

    public static boolean handleSnapshotEnd(ClientboundRouteDataSnapshotEndPayload payload) {
        if (pendingSnapshot == null || pendingSnapshot.revision() != payload.revision() || !pendingSnapshot.complete()) {
            pendingSnapshot = null;
            return false;
        }
        revision = pendingSnapshot.revision();
        pipeRevisionUsed = pendingSnapshot.pipeRevisionUsed();
        STATION_GROUPS.clear();
        PLATFORM_STOPS.clear();
        ROUTE_LINES.clear();
        ROUTE_LAYOUTS.clear();
        ROUTE_SECTIONS.clear();
        ROUTE_SECTION_PATHS.clear();
        STATION_TRANSFER_LINKS.clear();
        clearIndexes();
        pendingSnapshot.stationGroups().forEach(stationGroup -> STATION_GROUPS.put(stationGroup.id(), stationGroup));
        pendingSnapshot.platformStops().forEach(platformStop -> PLATFORM_STOPS.put(platformStop.id(), platformStop));
        pendingSnapshot.routeLines().forEach(routeLine -> ROUTE_LINES.put(routeLine.id(), routeLine));
        pendingSnapshot.routeLayouts().forEach(routeLayout -> ROUTE_LAYOUTS.put(routeLayout.id(), routeLayout));
        pendingSnapshot.routeSections().forEach(routeSection -> ROUTE_SECTIONS.put(routeSection.id(), routeSection));
        pendingSnapshot.routeSectionPaths().forEach(record -> ROUTE_SECTION_PATHS.put(record.routeSectionId(), record.path()));
        pendingSnapshot.stationTransferLinks().forEach(link -> STATION_TRANSFER_LINKS.put(link.id(), link));
        pendingSnapshot = null;
        rebuildIndexes();
        return true;
    }

    public static boolean handleDelta(ClientboundRouteDataDeltaPayload payload) {
        if (pendingSnapshot != null) {
            return false;
        }
        if (payload.revision() <= revision) {
            return true;
        }
        if (payload.baseRevision() != revision) {
            return false;
        }
        pipeRevisionUsed = payload.pipeRevisionUsed();

        payload.removedRouteSectionIds().forEach(sectionId -> {
            RouteSection removed = ROUTE_SECTIONS.remove(sectionId);
            if (removed != null) {
                deindexRouteSection(removed);
            }
            ROUTE_SECTION_PATHS.remove(sectionId);
        });
        payload.removedRouteSectionPathIds().forEach(ROUTE_SECTION_PATHS::remove);
        payload.removedRouteLayoutIds().forEach(layoutId -> {
            RouteLayout removed = ROUTE_LAYOUTS.remove(layoutId);
            if (removed != null) {
                deindexRouteLayout(removed);
            }
        });
        payload.removedRouteLineIds().forEach(ROUTE_LINES::remove);
        payload.removedPlatformStopIds().forEach(platformStopId -> {
            PlatformStop removed = PLATFORM_STOPS.remove(platformStopId);
            if (removed != null) {
                deindexPlatformStop(removed);
            }
        });
        payload.removedStationGroupIds().forEach(STATION_GROUPS::remove);

        payload.updatedStationGroups().forEach(stationGroup -> STATION_GROUPS.put(stationGroup.id(), stationGroup));
        payload.updatedPlatformStops().forEach(platformStop -> {
            PlatformStop previous = PLATFORM_STOPS.put(platformStop.id(), platformStop);
            if (previous != null) {
                deindexPlatformStop(previous);
            }
            indexPlatformStop(platformStop);
        });
        payload.updatedRouteLines().forEach(routeLine -> ROUTE_LINES.put(routeLine.id(), routeLine));
        payload.updatedRouteLayouts().forEach(routeLayout -> {
            RouteLayout previous = ROUTE_LAYOUTS.put(routeLayout.id(), routeLayout);
            if (previous != null) {
                deindexRouteLayout(previous);
            }
            indexRouteLayout(routeLayout);
        });
        payload.updatedRouteSections().forEach(routeSection -> {
            RouteSection previous = ROUTE_SECTIONS.put(routeSection.id(), routeSection);
            if (previous != null) {
                deindexRouteSection(previous);
            }
            indexRouteSection(routeSection);
        });
        payload.updatedRouteSectionPaths().forEach(record -> ROUTE_SECTION_PATHS.put(record.routeSectionId(), record.path()));
        payload.removedStationTransferLinkIds().forEach(linkId -> {
            StationTransferLink removed = STATION_TRANSFER_LINKS.remove(linkId);
            if (removed != null) {
                deindexStationTransferLink(removed);
            }
        });
        payload.updatedStationTransferLinks().forEach(link -> {
            StationTransferLink previous = STATION_TRANSFER_LINKS.put(link.id(), link);
            if (previous != null) {
                deindexStationTransferLink(previous);
            }
            indexStationTransferLink(link);
        });

        revision = payload.revision();
        return true;
    }

    public static void clear() {
        revision = -1L;
        pipeRevisionUsed = -1L;
        STATION_GROUPS.clear();
        PLATFORM_STOPS.clear();
        ROUTE_LINES.clear();
        ROUTE_LAYOUTS.clear();
        ROUTE_SECTIONS.clear();
        ROUTE_SECTION_PATHS.clear();
        STATION_TRANSFER_LINKS.clear();
        pendingSnapshot = null;
        clearIndexes();
    }

    public static long revision() {
        return revision;
    }

    public static long pipeRevisionUsed() {
        return pipeRevisionUsed;
    }

    public static boolean isWaitingForPipeRevision(long currentPipeRevision) {
        return pipeRevisionUsed >= 0L && currentPipeRevision < pipeRevisionUsed;
    }

    public static Optional<StationGroup> stationGroup(UUID id) {
        return Optional.ofNullable(STATION_GROUPS.get(id));
    }

    public static Optional<PlatformStop> platformStop(UUID id) {
        return Optional.ofNullable(PLATFORM_STOPS.get(id));
    }

    public static Optional<RouteLine> routeLine(UUID id) {
        return Optional.ofNullable(ROUTE_LINES.get(id));
    }

    public static Optional<RouteLayout> routeLayout(UUID id) {
        return Optional.ofNullable(ROUTE_LAYOUTS.get(id));
    }

    public static Optional<RouteSection> routeSection(UUID id) {
        return Optional.ofNullable(ROUTE_SECTIONS.get(id));
    }

    public static Optional<RouteSectionPath> routeSectionPath(UUID id) {
        return Optional.ofNullable(ROUTE_SECTION_PATHS.get(id));
    }

    public static Optional<StationTransferLink> stationTransferLink(UUID id) {
        return Optional.ofNullable(STATION_TRANSFER_LINKS.get(id));
    }

    public static Optional<RouteChoiceSelection> routeChoiceForCurrentStep(UUID layoutId, int routeDirection, Optional<UUID> currentPlatformStopId, Optional<UUID> currentRouteSectionId, int routeConnectionIndex, UUID currentConnectionId, UUID branchNodeId) {
        RouteLayout layout = ROUTE_LAYOUTS.get(layoutId);
        if (layout == null) {
            return Optional.empty();
        }
        Optional<Integer> normalizedDirection = RouteLayoutNavigator.normalizeDirection(layout, routeDirection);
        if (normalizedDirection.isEmpty()) {
            return Optional.empty();
        }
        int direction = normalizedDirection.get();
        Optional<RouteSection> currentSection = currentRouteSectionId
                .flatMap(ClientRouteDataCache::routeSection)
                .filter(section -> section.routeLayoutId().equals(layoutId))
                .or(() -> currentPlatformStopId.flatMap(platformStopId -> nextRouteSection(layout, platformStopId, direction)));
        if (currentSection.isPresent()) {
            Optional<RouteChoiceSelection> pathChoice = routeChoiceFromPath(currentSection.get(), direction, routeConnectionIndex, currentConnectionId, branchNodeId);
            if (pathChoice.isPresent()) {
                return pathChoice;
            }
            return RouteChoiceResolver.routeChoiceFor(currentSection.get(), direction, currentConnectionId, branchNodeId)
                    .map(selected -> new RouteChoiceSelection(selected, nextConnectionIndex(currentSection.get().id(), direction, routeConnectionIndex, currentConnectionId, selected).orElse(routeConnectionIndex)));
        }
        return Optional.empty();
    }

    public static Optional<Integer> connectionIndexInSection(UUID routeSectionId, int routeDirection, UUID connectionId, int searchFrom) {
        return routeSectionPath(routeSectionId)
                .map(path -> routeDirection < 0 ? path.reverseConnections() : path.forwardConnections())
                .flatMap(refs -> connectionIndexIn(refs, connectionId, searchFrom));
    }

    private static Optional<RouteChoiceSelection> routeChoiceFromPath(RouteSection section, int routeDirection, int routeConnectionIndex, UUID currentConnectionId, UUID branchNodeId) {
        if (section.statusForDirection(routeDirection) != dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus.VALID) {
            return Optional.empty();
        }
        Optional<RouteSectionPath> path = routeSectionPath(section.id());
        if (path.isEmpty()) {
            return Optional.empty();
        }
        List<dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef> refs = routeDirection < 0 ? path.get().reverseConnections() : path.get().forwardConnections();
        if (refs.size() < 2) {
            return Optional.empty();
        }
        Optional<BranchNode> branch = ClientPipeNetworkCache.currentBranchNode(branchNodeId);
        if (branch.isEmpty()) {
            return Optional.empty();
        }
        Optional<RouteChoiceSelection> forward = routeChoiceFromPath(refs, branch.get(), routeConnectionIndex, currentConnectionId);
        if (forward.isPresent()) {
            return forward;
        }
        return routeChoiceFromPath(refs, branch.get(), 0, currentConnectionId);
    }

    private static Optional<RouteChoiceSelection> routeChoiceFromPath(List<dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef> refs, BranchNode branch, int searchFrom, UUID currentConnectionId) {
        int start = Math.max(0, Math.min(searchFrom, refs.size() - 1));
        for (int i = start; i + 1 < refs.size(); i++) {
            if (!refs.get(i).connectionId().equals(currentConnectionId)) {
                continue;
            }
            UUID selectedConnectionId = refs.get(i + 1).connectionId();
            if (!branch.referencesConnection(currentConnectionId) || !branch.referencesConnection(selectedConnectionId)) {
                continue;
            }
            return Optional.of(new RouteChoiceSelection(selectedConnectionId, i + 1));
        }
        return Optional.empty();
    }

    private static Optional<Integer> nextConnectionIndex(UUID routeSectionId, int routeDirection, int routeConnectionIndex, UUID currentConnectionId, UUID selectedConnectionId) {
        return routeSectionPath(routeSectionId)
                .map(path -> routeDirection < 0 ? path.reverseConnections() : path.forwardConnections())
                .flatMap(refs -> {
                    int start = Math.max(0, Math.min(routeConnectionIndex, refs.size() - 1));
                    for (int i = start; i + 1 < refs.size(); i++) {
                        if (refs.get(i).connectionId().equals(currentConnectionId) && refs.get(i + 1).connectionId().equals(selectedConnectionId)) {
                            return Optional.of(i + 1);
                        }
                    }
                    return connectionIndexIn(refs, selectedConnectionId, start);
                });
    }

    private static Optional<Integer> connectionIndexIn(List<dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef> refs, UUID connectionId, int searchFrom) {
        if (refs.isEmpty()) {
            return Optional.empty();
        }
        int start = Math.max(0, Math.min(searchFrom, refs.size() - 1));
        for (int i = start; i < refs.size(); i++) {
            if (refs.get(i).connectionId().equals(connectionId)) {
                return Optional.of(i);
            }
        }
        for (int i = 0; i < start; i++) {
            if (refs.get(i).connectionId().equals(connectionId)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    public static Collection<StationGroup> stationGroups() {
        return java.util.List.copyOf(STATION_GROUPS.values());
    }

    public static Collection<PlatformStop> platformStops() {
        return java.util.List.copyOf(PLATFORM_STOPS.values());
    }

    public static Collection<RouteLine> routeLines() {
        return java.util.List.copyOf(ROUTE_LINES.values());
    }

    public static Collection<RouteLayout> routeLayouts() {
        return java.util.List.copyOf(ROUTE_LAYOUTS.values());
    }

    public static Collection<RouteSection> routeSections() {
        return java.util.List.copyOf(ROUTE_SECTIONS.values());
    }

    public static Map<UUID, RouteSectionPath> routeSectionPaths() {
        return Map.copyOf(ROUTE_SECTION_PATHS);
    }

    public static Collection<StationTransferLink> stationTransferLinks() {
        return java.util.List.copyOf(STATION_TRANSFER_LINKS.values());
    }

    public static List<RouteLayout> routeLayoutsForLine(UUID routeLineId) {
        return ROUTE_LAYOUTS_BY_LINE.getOrDefault(routeLineId, List.of()).stream()
                .sorted(Comparator.comparing(layout -> layout.displayName().orElse("")))
                .toList();
    }

    public static List<PlatformStop> platformStopsInStation(UUID stationGroupId) {
        return PLATFORM_STOPS_BY_STATION.getOrDefault(stationGroupId, List.of()).stream()
                .sorted(PlatformStop.DISPLAY_ORDER)
                .toList();
    }

    public static List<RouteSection> routeSectionsForLayout(UUID routeLayoutId) {
        return List.copyOf(ROUTE_SECTIONS_BY_LAYOUT.getOrDefault(routeLayoutId, List.of()));
    }

    public static Optional<RouteSection> routeSectionForLayoutEndpoints(UUID routeLayoutId, UUID fromPlatformStopId, UUID toPlatformStopId) {
        return ROUTE_SECTIONS_BY_LAYOUT.getOrDefault(routeLayoutId, List.of()).stream()
                .filter(section -> section.fromPlatformStopId().equals(fromPlatformStopId) && section.toPlatformStopId().equals(toPlatformStopId))
                .findFirst();
    }

    public static List<UUID> routeLayoutIdsForPlatformStop(UUID platformStopId) {
        return List.copyOf(ROUTE_LAYOUT_IDS_BY_PLATFORM_STOP.getOrDefault(platformStopId, List.of()));
    }

    public static List<StationTransferLink> stationTransferLinksForStation(UUID stationGroupId) {
        return TRANSFER_LINKS_BY_STATION.getOrDefault(stationGroupId, List.of()).stream()
                .sorted(Comparator.comparing(link -> link.other(stationGroupId).map(UUID::toString).orElse("")))
                .toList();
    }

    public static boolean hasStationTransferLink(UUID firstStationGroupId, UUID secondStationGroupId) {
        return STATION_TRANSFER_LINKS.values().stream().anyMatch(link -> link.connects(firstStationGroupId, secondStationGroupId));
    }

    private static Optional<RouteSection> nextRouteSection(RouteLayout layout, UUID platformStopId, int routeDirection) {
        return RouteLayoutNavigator.nextStep(layout, platformStopId, routeDirection, ClientRouteDataCache::routeSection)
                .map(RouteLayoutNavigator.RouteStep::section);
    }

    private static void clearIndexes() {
        PLATFORM_STOPS_BY_STATION.clear();
        ROUTE_LAYOUTS_BY_LINE.clear();
        ROUTE_SECTIONS_BY_LAYOUT.clear();
        ROUTE_LAYOUT_IDS_BY_PLATFORM_STOP.clear();
        TRANSFER_LINKS_BY_STATION.clear();
    }

    private static void rebuildIndexes() {
        for (PlatformStop platformStop : PLATFORM_STOPS.values()) {
            indexPlatformStop(platformStop);
        }
        for (RouteLayout routeLayout : ROUTE_LAYOUTS.values()) {
            indexRouteLayout(routeLayout);
        }
        for (RouteSection routeSection : ROUTE_SECTIONS.values()) {
            indexRouteSection(routeSection);
        }
        for (StationTransferLink link : STATION_TRANSFER_LINKS.values()) {
            indexStationTransferLink(link);
        }
    }

    private static void indexPlatformStop(PlatformStop platformStop) {
        addUnique(PLATFORM_STOPS_BY_STATION.computeIfAbsent(platformStop.stationGroupId(), ignored -> new ArrayList<>()), platformStop);
    }

    private static void deindexPlatformStop(PlatformStop platformStop) {
        removeById(PLATFORM_STOPS_BY_STATION.get(platformStop.stationGroupId()), platformStop.id(), PlatformStop::id);
    }

    private static void indexRouteLayout(RouteLayout routeLayout) {
        addUnique(ROUTE_LAYOUTS_BY_LINE.computeIfAbsent(routeLayout.routeLineId(), ignored -> new ArrayList<>()), routeLayout);
        for (UUID platformStopId : routeLayout.orderedPlatformStops()) {
            addUnique(ROUTE_LAYOUT_IDS_BY_PLATFORM_STOP.computeIfAbsent(platformStopId, ignored -> new ArrayList<>()), routeLayout.id());
        }
    }

    private static void deindexRouteLayout(RouteLayout routeLayout) {
        removeById(ROUTE_LAYOUTS_BY_LINE.get(routeLayout.routeLineId()), routeLayout.id(), RouteLayout::id);
        for (UUID platformStopId : routeLayout.orderedPlatformStops()) {
            removeValue(ROUTE_LAYOUT_IDS_BY_PLATFORM_STOP.get(platformStopId), routeLayout.id());
        }
    }

    private static void indexRouteSection(RouteSection routeSection) {
        addUnique(ROUTE_SECTIONS_BY_LAYOUT.computeIfAbsent(routeSection.routeLayoutId(), ignored -> new ArrayList<>()), routeSection);
    }

    private static void deindexRouteSection(RouteSection routeSection) {
        removeById(ROUTE_SECTIONS_BY_LAYOUT.get(routeSection.routeLayoutId()), routeSection.id(), RouteSection::id);
    }

    private static void indexStationTransferLink(StationTransferLink link) {
        addUnique(TRANSFER_LINKS_BY_STATION.computeIfAbsent(link.firstStationGroupId(), ignored -> new ArrayList<>()), link);
        addUnique(TRANSFER_LINKS_BY_STATION.computeIfAbsent(link.secondStationGroupId(), ignored -> new ArrayList<>()), link);
    }

    private static void deindexStationTransferLink(StationTransferLink link) {
        removeById(TRANSFER_LINKS_BY_STATION.get(link.firstStationGroupId()), link.id(), StationTransferLink::id);
        removeById(TRANSFER_LINKS_BY_STATION.get(link.secondStationGroupId()), link.id(), StationTransferLink::id);
    }

    private static <T> void addUnique(List<T> values, T value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private static <T> void removeById(List<T> values, UUID id, java.util.function.Function<T, UUID> idGetter) {
        if (values == null) {
            return;
        }
        values.removeIf(value -> idGetter.apply(value).equals(id));
    }

    private static <T> void removeValue(List<T> values, T value) {
        if (values == null) {
            return;
        }
        values.removeIf(value::equals);
    }

    private record SnapshotBuffer(
            long revision,
            long pipeRevisionUsed,
            int expectedStationGroups,
            int expectedPlatformStops,
            int expectedRouteLines,
            int expectedRouteLayouts,
            int expectedRouteSections,
            int expectedRouteSectionPaths,
            int expectedStationTransferLinks,
            int expectedChunks,
            List<StationGroup> stationGroups,
            List<PlatformStop> platformStops,
            List<RouteLine> routeLines,
            List<RouteLayout> routeLayouts,
            List<RouteSection> routeSections,
            List<RouteSectionPathRecord> routeSectionPaths,
            List<StationTransferLink> stationTransferLinks,
            Set<Integer> receivedChunkIndexes
    ) {
        private SnapshotBuffer(long revision, long pipeRevisionUsed, int expectedStationGroups, int expectedPlatformStops, int expectedRouteLines, int expectedRouteLayouts, int expectedRouteSections, int expectedRouteSectionPaths, int expectedStationTransferLinks, int expectedChunks) {
            this(revision, pipeRevisionUsed, expectedStationGroups, expectedPlatformStops, expectedRouteLines, expectedRouteLayouts, expectedRouteSections, expectedRouteSectionPaths, expectedStationTransferLinks, expectedChunks, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new HashSet<>());
        }

        private ChunkAcceptResult acceptChunk(ClientboundRouteDataSnapshotChunkPayload payload) {
            if (payload.chunkIndex() < 0 || payload.chunkIndex() >= this.expectedChunks) {
                return ChunkAcceptResult.INVALID;
            }
            if (this.receivedChunkIndexes.contains(payload.chunkIndex())) {
                return ChunkAcceptResult.DUPLICATE;
            }
            if (this.stationGroups.size() + payload.stationGroups().size() > this.expectedStationGroups
                    || this.platformStops.size() + payload.platformStops().size() > this.expectedPlatformStops
                    || this.routeLines.size() + payload.routeLines().size() > this.expectedRouteLines
                    || this.routeLayouts.size() + payload.routeLayouts().size() > this.expectedRouteLayouts
                    || this.routeSections.size() + payload.routeSections().size() > this.expectedRouteSections
                    || this.routeSectionPaths.size() + payload.routeSectionPaths().size() > this.expectedRouteSectionPaths
                    || this.stationTransferLinks.size() + payload.stationTransferLinks().size() > this.expectedStationTransferLinks) {
                return ChunkAcceptResult.INVALID;
            }
            this.receivedChunkIndexes.add(payload.chunkIndex());
            return ChunkAcceptResult.ACCEPTED;
        }

        private boolean complete() {
            return this.stationGroups.size() == this.expectedStationGroups
                    && this.platformStops.size() == this.expectedPlatformStops
                    && this.routeLines.size() == this.expectedRouteLines
                    && this.routeLayouts.size() == this.expectedRouteLayouts
                    && this.routeSections.size() == this.expectedRouteSections
                    && this.routeSectionPaths.size() == this.expectedRouteSectionPaths
                    && this.stationTransferLinks.size() == this.expectedStationTransferLinks
                    && this.receivedChunkIndexes.size() == this.expectedChunks;
        }
    }

    private enum ChunkAcceptResult {
        ACCEPTED,
        DUPLICATE,
        INVALID
    }
}

