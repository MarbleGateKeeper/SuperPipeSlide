package dev.marblegate.superpipeslide.client.fullmap.routecard.model;

import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SemanticEdgeKind;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;
import java.util.List;
import java.util.UUID;

public record RouteCardEdge(
        String id,
        RouteCardNodeId from,
        RouteCardNodeId to,
        SemanticEdgeKind kind,
        UUID routeSectionId,
        int layoutIndex,
        int segmentIndex,
        boolean bidirectional,
        boolean loopBack,
        RouteSectionStatus status,
        List<PipeConnectionRef> backingPathSlice,
        List<Integer> themeColors,
        boolean abstractFoldLink,
        boolean abstractMissingLink,
        boolean missingPathEdge) {
    public RouteCardEdge {
        backingPathSlice = List.copyOf(backingPathSlice);
        themeColors = themeColors.stream().map(SPSGui::opaque).limit(3).toList();
        // Boundary classification is fully determined by the endpoint id kinds, so it is
        // computed once here instead of being re-derived per frame with node lookups.
        boolean synthetic = kind == SemanticEdgeKind.FOLD_ADJACENT && backingPathSlice.isEmpty();
        abstractFoldLink = synthetic && isFoldLinkBoundary(from.kind()) && isFoldLinkBoundary(to.kind());
        abstractMissingLink = synthetic && from.kind() == RouteCardNodeKind.MISSING_PATH_BOUNDARY && to.kind() == RouteCardNodeKind.MISSING_PATH_BOUNDARY;
        missingPathEdge = from.kind() != to.kind() && (from.kind() == RouteCardNodeKind.MISSING_PATH_BOUNDARY || to.kind() == RouteCardNodeKind.MISSING_PATH_BOUNDARY);
    }

    private static boolean isFoldLinkBoundary(RouteCardNodeKind kind) {
        return kind == RouteCardNodeKind.FOLD_BOUNDARY || kind == RouteCardNodeKind.PORTAL_BOUNDARY;
    }

    /** Full constructor without the boundary flags; they are derived in the compact constructor. */
    public RouteCardEdge(String id, RouteCardNodeId from, RouteCardNodeId to, SemanticEdgeKind kind, UUID routeSectionId, int layoutIndex, int segmentIndex, boolean bidirectional, boolean loopBack, RouteSectionStatus status, List<PipeConnectionRef> backingPathSlice, List<Integer> themeColors) {
        this(id, from, to, kind, routeSectionId, layoutIndex, segmentIndex, bidirectional, loopBack, status, backingPathSlice, themeColors, false, false, false);
    }

    /** Synthetic links that only exist inside the route card and have no main-map counterpart. */
    public boolean abstractBoundaryLink() {
        return this.abstractFoldLink || this.abstractMissingLink;
    }
}
