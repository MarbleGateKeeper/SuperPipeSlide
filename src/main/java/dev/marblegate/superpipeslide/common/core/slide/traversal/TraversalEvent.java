package dev.marblegate.superpipeslide.common.core.slide.traversal;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import java.util.Optional;

public record TraversalEvent(TraversalEventType type, TraversalCursor cursor, Optional<PipeAnchorId> anchorId) {
    public TraversalEvent {
        anchorId = anchorId == null ? Optional.empty() : anchorId;
    }

    public static TraversalEvent atCursor(TraversalEventType type, TraversalCursor cursor) {
        return new TraversalEvent(type, cursor, Optional.empty());
    }

    public static TraversalEvent atAnchor(TraversalEventType type, TraversalCursor cursor, PipeAnchorId anchorId) {
        return new TraversalEvent(type, cursor, Optional.of(anchorId));
    }
}
