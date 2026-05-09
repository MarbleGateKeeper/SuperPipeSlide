package dev.marblegate.superpipeslide.common.core.slide.traversal;

public record StationCheckpoint(double distanceOnConnection, TraversalEventType eventType) {
    public StationCheckpoint {
        if (!Double.isFinite(distanceOnConnection)) {
            distanceOnConnection = 0.0D;
        }
        if (eventType != TraversalEventType.STATION_ENTRY_CHECKPOINT
                && eventType != TraversalEventType.STATION_CENTER_CHECKPOINT) {
            throw new IllegalArgumentException("Station checkpoint event type expected");
        }
    }
}
