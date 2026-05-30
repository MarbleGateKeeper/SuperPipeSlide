package dev.marblegate.superpipeslide.client.fullmap.physical;

import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import java.util.List;
import java.util.UUID;

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
