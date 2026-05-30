package dev.marblegate.superpipeslide.common.core.networkgraph.solver;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.networkgraph.branch.BranchNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class OverlayPipeNetworkView implements PipeNetworkView {
    private final PipeNetworkView base;
    private final Map<UUID, PipeConnection> overlayById = new LinkedHashMap<>();
    private final Map<PipeAnchorId, List<PipeConnection>> overlayByAnchor = new LinkedHashMap<>();
    private final Map<PipeAnchorId, BranchNode> branchOverlayByAnchor = new LinkedHashMap<>();
    private final Map<UUID, BranchNode> branchOverlayByManagedConnection = new LinkedHashMap<>();

    private OverlayPipeNetworkView(PipeNetworkView base, Collection<PipeConnection> overlays, Map<PipeAnchorId, BranchNode> branchOverlays) {
        this.base = base;
        for (PipeConnection connection : overlays) {
            this.overlayById.put(connection.id(), connection);
            this.overlayByAnchor.computeIfAbsent(connection.fromAnchor(), ignored -> new ArrayList<>()).add(connection);
            this.overlayByAnchor.computeIfAbsent(connection.toAnchor(), ignored -> new ArrayList<>()).add(connection);
        }
        for (Map.Entry<PipeAnchorId, BranchNode> entry : branchOverlays.entrySet()) {
            this.branchOverlayByAnchor.put(entry.getKey(), entry.getValue());
            for (UUID connectionId : entry.getValue().managedConnectionIdsInOrder()) {
                this.branchOverlayByManagedConnection.put(connectionId, entry.getValue());
            }
        }
    }

    static OverlayPipeNetworkView withUpsert(PipeNetworkView base, PipeConnection overlay, Map<PipeAnchorId, BranchNode> branchOverlays) {
        return new OverlayPipeNetworkView(base, List.of(overlay), branchOverlays);
    }

    @Override
    public Optional<PipeConnection> connection(UUID id) {
        PipeConnection overlay = this.overlayById.get(id);
        return overlay != null ? Optional.of(overlay) : this.base.connection(id);
    }

    @Override
    public List<PipeConnection> connectionsTouching(PipeAnchorId anchorId) {
        List<PipeConnection> merged = new ArrayList<>();
        Set<UUID> seen = new LinkedHashSet<>();
        for (PipeConnection connection : this.base.connectionsTouching(anchorId)) {
            if (!this.overlayById.containsKey(connection.id()) && seen.add(connection.id())) {
                merged.add(connection);
            }
        }
        for (PipeConnection connection : this.overlayByAnchor.getOrDefault(anchorId, List.of())) {
            if (seen.add(connection.id())) {
                merged.add(connection);
            }
        }
        return List.copyOf(merged);
    }

    @Override
    public int connectionCount(PipeAnchorId anchorId) {
        return this.connectionsTouching(anchorId).size();
    }

    @Override
    public Optional<BranchNode> branchNodeAt(PipeAnchorId anchorId) {
        BranchNode overlay = this.branchOverlayByAnchor.get(anchorId);
        return overlay != null ? Optional.of(overlay) : this.base.branchNodeAt(anchorId);
    }

    @Override
    public Optional<BranchNode> branchNodeManagingConnection(UUID connectionId) {
        BranchNode overlay = this.branchOverlayByManagedConnection.get(connectionId);
        return overlay != null ? Optional.of(overlay) : this.base.branchNodeManagingConnection(connectionId);
    }

    @Override
    public Optional<FoldAnchorNode> foldAnchorAt(PipeAnchorId anchorId) {
        return this.base.foldAnchorAt(anchorId);
    }

    @Override
    public Optional<PipeAnchorId> localFoldCounterpart(PipeAnchorId anchorId) {
        return this.base.localFoldCounterpart(anchorId);
    }

    @Override
    public long revision() {
        return this.base.revision();
    }
}
