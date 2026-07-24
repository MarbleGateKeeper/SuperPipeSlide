package dev.marblegate.superpipeslide.common.core.networkgraph.storage;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import dev.marblegate.superpipeslide.common.core.networkgraph.branch.BranchNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.model.PipeNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public interface PipeNetworkView {
    Optional<PipeConnection> connection(UUID id);

    default Optional<PipeConnection> connection(PipeConnectionRef ref) {
        return this.connection(ref.connectionId())
                .filter(connection -> connection.levelKey().equals(ref.levelKey()));
    }

    List<PipeConnection> connectionsTouching(PipeAnchorId anchorId);

    int connectionCount(PipeAnchorId anchorId);

    Optional<BranchNode> branchNodeAt(PipeAnchorId anchorId);

    Optional<BranchNode> branchNodeManagingConnection(UUID connectionId);

    Optional<FoldAnchorNode> foldAnchorAt(PipeAnchorId anchorId);

    Optional<PipeAnchorId> localFoldCounterpart(PipeAnchorId anchorId);

    default Optional<PipeNode> node(PipeAnchorId anchorId) {
        return Optional.empty();
    }

    /**
     * Resolves the world-space point where pipes attach to the given anchor. Falls back to
     * the anchor block center when the node is unknown to this view.
     */
    default Vec3 attachPoint(PipeAnchorId anchorId) {
        return this.node(anchorId).map(PipeNode::attachPoint).orElseGet(() -> Vec3.atCenterOf(anchorId.blockPos()));
    }

    long revision();
}
