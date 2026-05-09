package dev.marblegate.superpipeslide.common.core.slide.traversal;

import java.util.List;
import java.util.Optional;

public record TraversalResult(TraversalCursor cursor, double remainingDistance, List<TraversalEvent> events, Optional<TraversalEvent> barrier) {
    public TraversalResult {
        remainingDistance = Math.max(0.0D, remainingDistance);
        events = List.copyOf(events);
        barrier = barrier == null ? Optional.empty() : barrier;
    }

    public boolean blocked() {
        return this.barrier.isPresent();
    }
}
