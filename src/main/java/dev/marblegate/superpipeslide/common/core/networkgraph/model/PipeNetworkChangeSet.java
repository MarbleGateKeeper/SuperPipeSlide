package dev.marblegate.superpipeslide.common.core.networkgraph.model;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import java.util.List;
import java.util.UUID;

public record PipeNetworkChangeSet(
        long baseRevision,
        long revision,
        List<PipeAnchorId> addedOrUpdatedNodeIds,
        List<PipeAnchorId> removedNodeIds,
        List<UUID> addedOrUpdatedConnectionIds,
        List<PipeConnectionRef> removedConnectionRefs) {
    public PipeNetworkChangeSet {
        addedOrUpdatedNodeIds = List.copyOf(addedOrUpdatedNodeIds);
        removedNodeIds = List.copyOf(removedNodeIds);
        addedOrUpdatedConnectionIds = List.copyOf(addedOrUpdatedConnectionIds);
        removedConnectionRefs = List.copyOf(removedConnectionRefs);
    }

    public static PipeNetworkChangeSet empty(long revision) {
        return new PipeNetworkChangeSet(revision, revision, List.of(), List.of(), List.of(), List.of());
    }

    public boolean isEmpty() {
        return this.addedOrUpdatedNodeIds.isEmpty()
                && this.removedNodeIds.isEmpty()
                && this.addedOrUpdatedConnectionIds.isEmpty()
                && this.removedConnectionRefs.isEmpty();
    }

    public boolean mayRepairBrokenRouteSections() {
        return !this.addedOrUpdatedConnectionIds.isEmpty() || !this.addedOrUpdatedNodeIds.isEmpty();
    }

    public boolean mayInvalidateRouteSections() {
        return !this.addedOrUpdatedConnectionIds.isEmpty()
                || !this.removedConnectionRefs.isEmpty()
                || !this.addedOrUpdatedNodeIds.isEmpty()
                || !this.removedNodeIds.isEmpty();
    }
}
