package dev.marblegate.superpipeslide.client.fullmap.schematic.visual;

import java.util.Optional;
import java.util.UUID;

public record VisualLane(Optional<UUID> routeLineId, int laneIndex, double offsetBlocks) {
    public VisualLane {
        routeLineId = routeLineId == null ? Optional.empty() : routeLineId;
    }
}
