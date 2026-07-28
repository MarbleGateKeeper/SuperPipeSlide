package dev.marblegate.superpipeslide.client.fullmap.model;

import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import java.util.List;
import java.util.UUID;

/**
 * A single route-layout visit carried by a map edge.
 *
 * <p>{@code routeDirection} is a legacy component: every construction site passes {@code 1}.
 * The real one-way semantics live in {@code RouteLayout.bidirectional} and
 * {@code PipeConnectionAttributes.directionLimit}; removing this component (and its reads)
 * is behavior-neutral.
 */
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
