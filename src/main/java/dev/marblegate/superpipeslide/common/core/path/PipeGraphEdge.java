package dev.marblegate.superpipeslide.common.core.path;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;

import java.util.Comparator;
import java.util.Optional;

public record PipeGraphEdge(
        PipeAnchorId from,
        PipeAnchorId to,
        Optional<PipeConnectionRef> connectionRef,
        double length,
        double cost,
        String stableKey
) {
    static final Comparator<PipeGraphEdge> STABLE_ORDER = Comparator
            .comparingDouble(PipeGraphEdge::cost)
            .thenComparing(PipeGraphEdge::stableKey)
            .thenComparing(edge -> PipeGraphSnapshot.stableAnchorKey(edge.to()));

    public PipeGraphEdge {
        connectionRef = connectionRef == null ? Optional.empty() : connectionRef;
        length = Math.max(0.0D, length);
        cost = Math.max(0.0D, cost);
    }

    static PipeGraphEdge pipe(PipeAnchorId from, PipeAnchorId to, PipeConnection connection, double branchPenalty) {
        PipeConnectionRef ref = PipeConnectionRef.of(connection);
        return new PipeGraphEdge(
                from,
                to,
                Optional.of(ref),
                connection.length(),
                connection.length() + Math.max(0.0D, branchPenalty),
                "pipe/" + connection.id()
        );
    }

    static PipeGraphEdge fold(PipeAnchorId from, PipeAnchorId to) {
        return new PipeGraphEdge(
                from,
                to,
                Optional.empty(),
                0.0D,
                0.25D,
                "fold/" + PipeGraphSnapshot.stableAnchorKey(to)
        );
    }
}
