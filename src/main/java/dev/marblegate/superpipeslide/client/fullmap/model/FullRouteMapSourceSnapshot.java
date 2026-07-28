package dev.marblegate.superpipeslide.client.fullmap.model;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorNode;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionPath;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationTransferLink;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable copy of everything a full route map build reads from the mutable client
 * caches. The snapshot is assembled on the render thread -- the same thread that applies
 * network updates to the source caches, so the copy cannot race with those writes -- and
 * is the only input the builders may read, which is what allows the build itself to run
 * off the render thread.
 */
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
        List<FoldAnchorNode> foldAnchors,
        Map<PipeAnchorId, FoldAnchorNode> foldAnchorsById,
        Map<PipeAnchorId, PipeAnchorId> foldCounterparts,
        Map<UUID, PipeConnection> connectionsById) {
    public FullRouteMapSourceSnapshot {
        stationGroups = List.copyOf(stationGroups);
        platformStops = List.copyOf(platformStops);
        routeLines = List.copyOf(routeLines);
        routeLayouts = List.copyOf(routeLayouts);
        routeSections = List.copyOf(routeSections);
        stationTransferLinks = List.copyOf(stationTransferLinks);
        sectionPaths = Map.copyOf(sectionPaths);
        foldAnchors = List.copyOf(foldAnchors);
        foldAnchorsById = Map.copyOf(foldAnchorsById);
        foldCounterparts = Map.copyOf(foldCounterparts);
        connectionsById = Map.copyOf(connectionsById);
    }

    /**
     * Derives the lookup indexes from the raw fold anchor and connection lists. The fold
     * counterpart projection mirrors ClientPipeNetworkCache.rebuildFoldCounterpartIndex:
     * only bound B-end anchors link the two ends, in both directions.
     */
    public static FullRouteMapSourceSnapshot of(
            long routeRevision,
            long pipeRevision,
            List<StationGroup> stationGroups,
            List<PlatformStop> platformStops,
            List<RouteLine> routeLines,
            List<RouteLayout> routeLayouts,
            List<RouteSection> routeSections,
            List<StationTransferLink> stationTransferLinks,
            Map<UUID, RouteSectionPath> sectionPaths,
            List<FoldAnchorNode> foldAnchors,
            Collection<PipeConnection> connections) {
        Map<PipeAnchorId, FoldAnchorNode> foldAnchorsById = new LinkedHashMap<>();
        Map<PipeAnchorId, PipeAnchorId> foldCounterparts = new LinkedHashMap<>();
        for (FoldAnchorNode foldAnchor : foldAnchors) {
            foldAnchorsById.put(foldAnchor.anchorId(), foldAnchor);
            if (!foldAnchor.isBEnd()) {
                continue;
            }
            foldAnchor.boundTarget().ifPresent(target -> {
                foldCounterparts.put(foldAnchor.anchorId(), target.anchorId());
                foldCounterparts.put(target.anchorId(), foldAnchor.anchorId());
            });
        }
        Map<UUID, PipeConnection> connectionsById = new LinkedHashMap<>();
        for (PipeConnection connection : connections) {
            connectionsById.put(connection.id(), connection);
        }
        return new FullRouteMapSourceSnapshot(
                routeRevision,
                pipeRevision,
                stationGroups,
                platformStops,
                routeLines,
                routeLayouts,
                routeSections,
                stationTransferLinks,
                sectionPaths,
                foldAnchors,
                foldAnchorsById,
                foldCounterparts,
                connectionsById);
    }

    public Optional<FoldAnchorNode> foldAnchorAt(PipeAnchorId anchorId) {
        return Optional.ofNullable(this.foldAnchorsById.get(anchorId));
    }

    public Optional<PipeAnchorId> foldCounterpart(PipeAnchorId anchorId) {
        return Optional.ofNullable(this.foldCounterparts.get(anchorId));
    }

    public Optional<PipeConnection> connection(PipeConnectionRef ref) {
        return Optional.ofNullable(this.connectionsById.get(ref.connectionId()));
    }
}
