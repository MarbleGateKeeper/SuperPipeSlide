package dev.marblegate.superpipeslide.common.core.slide.traversal;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;

import java.util.Optional;

public record ContinuationDecision(Type type, Optional<PipeConnection> connection, int direction, Optional<PipeAnchorId> anchorId) {
    public enum Type {
        NEXT_CONNECTION,
        BRANCH_CHOICE_REQUIRED,
        FOLD_TRANSITION_REQUIRED,
        NO_CONTINUATION,
        INVALID_TOPOLOGY
    }

    public ContinuationDecision {
        connection = connection == null ? Optional.empty() : connection;
        direction = direction < 0 ? -1 : 1;
        anchorId = anchorId == null ? Optional.empty() : anchorId;
    }

    public static ContinuationDecision next(PipeConnection connection, int direction) {
        return new ContinuationDecision(Type.NEXT_CONNECTION, Optional.of(connection), direction, Optional.empty());
    }

    public static ContinuationDecision barrier(Type type, PipeAnchorId anchorId) {
        return new ContinuationDecision(type, Optional.empty(), 1, Optional.of(anchorId));
    }
}
