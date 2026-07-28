package dev.marblegate.superpipeslide.client.fullmap.physical;

import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import java.util.List;
import java.util.UUID;

/**
 * Route metadata attached to a physical-map edge.
 *
 * <p>{@code routeDirection} is a legacy component: every construction site passes {@code 1}.
 * The real one-way semantics live in {@code RouteLayout.bidirectional} and
 * {@code PipeConnectionAttributes.directionLimit}; removing this component (and its reads)
 * is behavior-neutral.
 */
public record PhysicalEdgeMetadata(
        UUID routeLineId,
        UUID routeLayoutId,
        UUID routeSectionId,
        int layoutIndex,
        int routeDirection,
        boolean bidirectional,
        UUID fromPlatformStopId,
        UUID toPlatformStopId,
        List<PipeConnectionRef> backingPathSlice,
        boolean fallback,
        double lengthBlocks) {
    public PhysicalEdgeMetadata {
        backingPathSlice = List.copyOf(backingPathSlice);
        lengthBlocks = Math.max(0.0D, lengthBlocks);
    }
}
