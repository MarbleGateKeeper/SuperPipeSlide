package dev.marblegate.superpipeslide.common.core.path;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.networkgraph.branch.BranchNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PipeGraphSnapshot {
    private static final double BRANCH_PENALTY = 8.0D;

    private final PipeNetworkView view;
    private final long revision;
    private final Map<PipeAnchorId, List<PipeGraphEdge>> edgesByAnchor = new HashMap<>();

    private PipeGraphSnapshot(PipeNetworkView view) {
        this.view = view;
        this.revision = view.revision();
    }

    public static PipeGraphSnapshot of(PipeNetworkView view) {
        return new PipeGraphSnapshot(view);
    }

    public long revision() {
        return this.revision;
    }

    public List<PipeGraphEdge> edgesFrom(PipeAnchorId anchorId, Set<UUID> excludedConnectionIds) {
        List<PipeGraphEdge> edges = this.edgesByAnchor.computeIfAbsent(anchorId, this::buildEdgesFrom);
        if (excludedConnectionIds.isEmpty()) {
            return edges;
        }
        return edges.stream()
                .filter(edge -> edge.connectionRef().map(ref -> !excludedConnectionIds.contains(ref.connectionId())).orElse(true))
                .toList();
    }

    private List<PipeGraphEdge> buildEdgesFrom(PipeAnchorId anchorId) {
        List<PipeGraphEdge> edges = new ArrayList<>();
        this.view.localFoldCounterpart(anchorId)
                .ifPresent(target -> edges.add(PipeGraphEdge.fold(anchorId, target)));

        List<PipeConnection> touching = new ArrayList<>(this.view.connectionsTouching(anchorId));
        touching.sort(Comparator
                .comparing(PipeConnection::id)
                .thenComparing(connection -> stableAnchorKey(connection.fromAnchor()))
                .thenComparing(connection -> stableAnchorKey(connection.toAnchor())));
        for (PipeConnection connection : touching) {
            int travelDirection = connection.directionAwayFrom(anchorId);
            if (!connection.allowsSlideDirection(travelDirection)) {
                continue;
            }
            PipeAnchorId nextAnchor = connection.fromAnchor().equals(anchorId) ? connection.toAnchor() : connection.fromAnchor();
            edges.add(PipeGraphEdge.pipe(anchorId, nextAnchor, connection, branchPenalty(anchorId, connection)));
        }

        edges.sort(PipeGraphEdge.STABLE_ORDER);
        return List.copyOf(edges);
    }

    private double branchPenalty(PipeAnchorId anchorId, PipeConnection connection) {
        Optional<BranchNode> branch = this.view.branchNodeAt(anchorId);
        return branch.isPresent() && branch.get().referencesConnection(connection.id()) ? BRANCH_PENALTY : 0.0D;
    }

    static String stableAnchorKey(PipeAnchorId anchorId) {
        return anchorId.levelKey() + "/"
                + anchorId.blockPos().getX() + "/"
                + anchorId.blockPos().getY() + "/"
                + anchorId.blockPos().getZ();
    }
}
