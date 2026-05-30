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
        List<Integer> themeColors) {
    public RouteCardEdge {
        backingPathSlice = List.copyOf(backingPathSlice);
        themeColors = themeColors.stream().map(SPSGui::opaque).limit(3).toList();
    }
}
