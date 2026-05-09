package dev.marblegate.superpipeslide.client.gui.route;

import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ClientRouteViewModelCache {
    private static final Map<UUID, List<SPSGui.StationNode>> STATION_NODE_CACHE = new HashMap<>();
    private static final Map<UUID, RouteSectionStatus> LOOP_STATUS_CACHE = new HashMap<>();
    private static final Map<UUID, String> LAYOUT_STATUS_CACHE = new HashMap<>();
    private static final Map<UUID, SPSGui.LineSummary> LINE_SUMMARY_CACHE = new HashMap<>();
    private static long cachedRouteRevision = Long.MIN_VALUE;
    private static long cachedPipeRevision = Long.MIN_VALUE;

    private ClientRouteViewModelCache() {
    }    public static List<SPSGui.StationNode> nodesForLayout(RouteLayout layout) {
        ensureCurrent();
        List<SPSGui.StationNode> cached = STATION_NODE_CACHE.get(layout.id());
        if (cached != null) {
            return cached;
        }
        List<SPSGui.StationNode> nodes = computeNodesForLayout(layout);
        STATION_NODE_CACHE.put(layout.id(), nodes);
        return nodes;
    }    public static RouteSectionStatus loopStatus(RouteLayout layout) {
        ensureCurrent();
        RouteSectionStatus cached = LOOP_STATUS_CACHE.get(layout.id());
        if (cached != null) {
            return cached;
        }
        RouteSectionStatus status = computeLoopStatus(layout);
        LOOP_STATUS_CACHE.put(layout.id(), status);
        return status;
    }    public static List<SPSGui.StationNode> representativeNodes(RouteLine line) {
        return representativeLayout(line)
                .map(ClientRouteViewModelCache::nodesForLayout)
                .orElse(List.of());
    }    public static Optional<RouteLayout> representativeLayout(RouteLine line) {
        return ClientRouteDataCache.routeLayoutsForLine(line.id()).stream()
                .max(Comparator.comparingInt(layout -> layout.orderedPlatformStops().size()))
                .or(() -> Optional.empty());
    }    public static SPSGui.LineSummary lineSummary(RouteLine line) {
        ensureCurrent();
        SPSGui.LineSummary cached = LINE_SUMMARY_CACHE.get(line.id());
        if (cached != null) {
            return cached;
        }
        List<RouteLayout> layouts = ClientRouteDataCache.routeLayoutsForLine(line.id());
        int layoutCount = Math.max(1, layouts.size());
        int stationCount = (int) layouts.stream()
                .flatMap(layout -> layout.orderedPlatformStops().stream())
                .map(uuid -> ClientRouteDataCache.platformStop(uuid).map(stop -> stop.stationGroupId()).orElse(uuid))
                .distinct()
                .count();
        List<RouteSectionStatus> statuses = routeStatuses(layouts);
        int problemCount = (int) statuses.stream()
                .filter(status -> status != RouteSectionStatus.VALID)
                .count();
        SPSGui.LineSummary summary = new SPSGui.LineSummary(layoutCount, stationCount, problemCount, statusLabel(statuses, "Draft"));
        LINE_SUMMARY_CACHE.put(line.id(), summary);
        return summary;
    }    public static String layoutStatus(RouteLayout layout) {
        ensureCurrent();
        String cached = LAYOUT_STATUS_CACHE.get(layout.id());
        if (cached != null) {
            return cached;
        }
        String status = statusLabel(routeStatuses(List.of(layout)), "Draft");
        LAYOUT_STATUS_CACHE.put(layout.id(), status);
        return status;
    }

    private static List<SPSGui.StationNode> computeNodesForLayout(RouteLayout layout) {
        List<RouteSection> sections = layout.orderedSectionRefs().stream()
                .map(ref -> ClientRouteDataCache.routeSection(ref.routeSectionId()))
                .flatMap(Optional::stream)
                .toList();
        List<SPSGui.StationNode> nodes = new ArrayList<>();
        for (int i = 0; i < layout.orderedPlatformStops().size(); i++) {
            UUID stopId = layout.orderedPlatformStops().get(i);
            Optional<PlatformStop> stop = ClientRouteDataCache.platformStop(stopId);
            if (stop.isEmpty() || isCurrentDimensionStopMissing(stop.get())) {
                nodes.add(new SPSGui.StationNode("", List.of(), false, RouteSectionStatus.VALID, true));
                continue;
            }
            String name = stop.get().displayName()
                    .orElseGet(() -> ClientRouteDataCache.stationGroup(stop.get().stationGroupId())
                            .map(StationGroup::primaryName)
                            .orElse("?"));
            List<SPSGui.TransferLine> transferLines = transferLinesForStation(layout.routeLineId(), stop.get().stationGroupId());
            RouteSectionStatus incoming = i == 0 || i - 1 >= sections.size() ? RouteSectionStatus.VALID : sections.get(i - 1).statusForLayout(layout);
            boolean error = incoming == RouteSectionStatus.BROKEN || incoming == RouteSectionStatus.AMBIGUOUS;
            nodes.add(new SPSGui.StationNode(name, transferLines, error, incoming, false));
        }
        return nodes;
    }

    private static RouteSectionStatus computeLoopStatus(RouteLayout layout) {
        if (!layout.loop() || layout.orderedPlatformStops().size() <= 1 || layout.orderedSectionRefs().isEmpty()) {
            return RouteSectionStatus.VALID;
        }
        return ClientRouteDataCache.routeSection(layout.orderedSectionRefs().getLast().routeSectionId())
                .map(section -> section.statusForLayout(layout))
                .orElse(RouteSectionStatus.STALE);
    }

    private static boolean isCurrentDimensionStopMissing(PlatformStop stop) {
        return ClientPipeNetworkCache.levelKey()
                .filter(levelKey -> levelKey.equals(stop.connectionRef().levelKey()))
                .map(ignored -> ClientPipeNetworkCache.currentConnection(stop.connectionId()).isEmpty()
                        && !ClientRouteDataCache.isWaitingForPipeRevision(ClientPipeNetworkCache.revision()))
                .orElse(false);
    }

    private static List<SPSGui.TransferLine> transferLinesForStation(UUID currentRouteLineId, UUID stationGroupId) {
        return ClientRouteDataCache.platformStopsInStation(stationGroupId).stream()
                .map(PlatformStop::routeLineId)
                .flatMap(Optional::stream)
                .distinct()
                .filter(routeLineId -> !routeLineId.equals(currentRouteLineId))
                .map(ClientRouteDataCache::routeLine)
                .flatMap(Optional::stream)
                .map(line -> new SPSGui.TransferLine(line.displayName(), line.themeColors()))
                .toList();
    }

    private static List<RouteSectionStatus> routeStatuses(List<RouteLayout> layouts) {
        return layouts.stream()
                .flatMap(layout -> layout.orderedSectionRefs().stream()
                        .map(ref -> ClientRouteDataCache.routeSection(ref.routeSectionId()).map(section -> section.statusForLayout(layout)).orElse(RouteSectionStatus.STALE)))
                .toList();
    }

    private static String statusLabel(List<RouteSectionStatus> statuses, String emptyStatus) {
        if (statuses.stream().anyMatch(value -> value == RouteSectionStatus.BROKEN)) {
            return "Broken";
        }
        if (statuses.stream().anyMatch(value -> value == RouteSectionStatus.AMBIGUOUS)) {
            return "Ambiguous";
        }
        if (statuses.stream().anyMatch(value -> value == RouteSectionStatus.INCOMPLETE)) {
            return "Incomplete";
        }
        if (statuses.stream().anyMatch(value -> value == RouteSectionStatus.STALE)) {
            return "Stale";
        }
        if (statuses.stream().anyMatch(value -> value == RouteSectionStatus.DISABLED)) {
            return "Disabled";
        }
        return statuses.isEmpty() ? emptyStatus : "Valid";
    }

    private static void ensureCurrent() {
        long routeRevision = ClientRouteDataCache.revision();
        long pipeRevision = ClientPipeNetworkCache.aggregateRevision();
        if (routeRevision == cachedRouteRevision && pipeRevision == cachedPipeRevision) {
            return;
        }
        cachedRouteRevision = routeRevision;
        cachedPipeRevision = pipeRevision;
        STATION_NODE_CACHE.clear();
        LOOP_STATUS_CACHE.clear();
        LAYOUT_STATUS_CACHE.clear();
        LINE_SUMMARY_CACHE.clear();
    }
}

