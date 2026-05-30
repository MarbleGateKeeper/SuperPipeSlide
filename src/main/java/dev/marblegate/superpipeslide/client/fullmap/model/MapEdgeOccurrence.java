package dev.marblegate.superpipeslide.client.fullmap.model;

import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import java.util.List;
import java.util.UUID;

public record MapEdgeOccurrence(
        UUID routeLineId,
        UUID routeLayoutId,
        UUID routeSectionId,
        int layoutIndex,
        int routeDirection,
        boolean bidirectional,
        List<PipeConnectionRef> backingPathSlice) {
    public MapEdgeOccurrence {
        backingPathSlice = List.copyOf(backingPathSlice);
    }
}
