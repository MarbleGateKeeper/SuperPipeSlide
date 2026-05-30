package dev.marblegate.superpipeslide.common.core.networkgraph.sync;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import dev.marblegate.superpipeslide.common.core.networkgraph.model.PipeNetworkChangeSet;
import dev.marblegate.superpipeslide.common.core.networkgraph.model.PipeNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkSavedData;
import dev.marblegate.superpipeslide.network.sync.pipe.PipeNetworkDeltaPayload;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class PipeSyncTracker {
    private final Map<PipeAnchorId, PipeNode> pendingAddedOrUpdatedNodes = new HashMap<>();
    private final Map<PipeAnchorId, ResourceKey<Level>> pendingRemovedNodeIds = new HashMap<>();
    private final Map<UUID, PipeConnection> pendingAddedOrUpdatedConnections = new HashMap<>();
    private final Map<UUID, ResourceKey<Level>> pendingRemovedConnectionIds = new HashMap<>();
    private long pendingBaseRevision = -1L;

    public void captureBaseRevision(long revision) {
        if (this.pendingBaseRevision < 0L) {
            this.pendingBaseRevision = revision;
        }
    }

    public PipeNetworkSavedData.PendingPipeNetworkChanges consume(long currentRevision) {
        long baseRevision = this.pendingBaseRevision >= 0L ? this.pendingBaseRevision : currentRevision;
        List<PipeNode> addedOrUpdatedNodes = List.copyOf(this.pendingAddedOrUpdatedNodes.values());
        List<PipeAnchorId> removedNodeIds = List.copyOf(this.pendingRemovedNodeIds.keySet());
        List<PipeConnection> addedOrUpdatedConnections = List.copyOf(this.pendingAddedOrUpdatedConnections.values());
        List<UUID> removedConnectionIds = List.copyOf(this.pendingRemovedConnectionIds.keySet());
        List<PipeConnectionRef> removedConnectionRefs = this.pendingRemovedConnectionIds.entrySet().stream()
                .map(entry -> new PipeConnectionRef(entry.getValue(), entry.getKey()))
                .toList();

        PipeNetworkDeltaPayload payload = new PipeNetworkDeltaPayload(
                baseRevision,
                currentRevision,
                addedOrUpdatedNodes,
                removedNodeIds,
                addedOrUpdatedConnections,
                removedConnectionIds);
        PipeNetworkChangeSet changeSet = new PipeNetworkChangeSet(
                baseRevision,
                currentRevision,
                addedOrUpdatedNodes.stream().map(PipeNode::id).toList(),
                removedNodeIds,
                addedOrUpdatedConnections.stream().map(PipeConnection::id).toList(),
                removedConnectionRefs);

        this.pendingAddedOrUpdatedNodes.clear();
        this.pendingRemovedNodeIds.clear();
        this.pendingAddedOrUpdatedConnections.clear();
        this.pendingRemovedConnectionIds.clear();
        this.pendingBaseRevision = -1L;
        return new PipeNetworkSavedData.PendingPipeNetworkChanges(payload, changeSet);
    }

    public void trackNodeUpsert(PipeNode node) {
        this.pendingRemovedNodeIds.remove(node.id());
        this.pendingAddedOrUpdatedNodes.put(node.id(), node);
    }

    public void trackNodeRemoval(PipeAnchorId nodeId, ResourceKey<Level> levelKey) {
        this.pendingAddedOrUpdatedNodes.remove(nodeId);
        this.pendingRemovedNodeIds.put(nodeId, levelKey);
    }

    public void trackConnectionUpsert(PipeConnection connection) {
        this.pendingRemovedConnectionIds.remove(connection.id());
        this.pendingAddedOrUpdatedConnections.put(connection.id(), connection);
    }

    public void trackConnectionRemovals(Collection<UUID> connectionIds, ResourceKey<Level> levelKey) {
        for (UUID connectionId : connectionIds) {
            this.pendingAddedOrUpdatedConnections.remove(connectionId);
            this.pendingRemovedConnectionIds.put(connectionId, levelKey);
        }
    }
}
