package dev.marblegate.superpipeslide.client.fullmap.model;

import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorNode;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionPath;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationTransferLink;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record FullRouteMapSourceSnapshot(
        long routeRevision,
        long pipeRevision,
        List<StationGroup> stationGroups,
        List<PlatformStop> platformStops,
        List<RouteLine> routeLines,
        List<RouteLayout> routeLayouts,
        List<RouteSection> routeSections,
        List<StationTransferLink> stationTransferLinks,
        Map<UUID, RouteSectionPath> sectionPaths,
        List<FoldAnchorNode> foldAnchors) {
    public FullRouteMapSourceSnapshot {
        stationGroups = List.copyOf(stationGroups);
        platformStops = List.copyOf(platformStops);
        routeLines = List.copyOf(routeLines);
        routeLayouts = List.copyOf(routeLayouts);
        routeSections = List.copyOf(routeSections);
        stationTransferLinks = List.copyOf(stationTransferLinks);
        sectionPaths = Map.copyOf(sectionPaths);
        foldAnchors = List.copyOf(foldAnchors);
    }
}
