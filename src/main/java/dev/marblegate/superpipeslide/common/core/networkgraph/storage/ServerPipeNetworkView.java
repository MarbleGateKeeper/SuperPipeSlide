package dev.marblegate.superpipeslide.common.core.networkgraph.storage;


import dev.marblegate.superpipeslide.common.core.networkgraph.branch.BranchNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorDirectory;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorNode;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ServerPipeNetworkView implements PipeNetworkView {
    private final MinecraftServer server;
    private final PipeNetworkSavedData data;

    private ServerPipeNetworkView(MinecraftServer server) {
        this.server = server;
        this.data = PipeNetworkSavedData.get(server);
    }

    public static ServerPipeNetworkView of(MinecraftServer server) {
        return new ServerPipeNetworkView(server);
    }

    @Override
    public Optional<PipeConnection> connection(UUID id) {
        return this.data.connection(id);
    }

    @Override
    public Optional<PipeConnection> connection(PipeConnectionRef ref) {
        return this.data.connection(ref.connectionId())
                .filter(connection -> connection.levelKey().equals(ref.levelKey()));
    }

    @Override
    public List<PipeConnection> connectionsTouching(PipeAnchorId anchorId) {
        return this.data(anchorId.levelKey())
                .map(data -> data.connectionsTouching(anchorId))
                .orElse(List.of());
    }

    @Override
    public int connectionCount(PipeAnchorId anchorId) {
        return this.data(anchorId.levelKey())
                .map(data -> data.connectionCount(anchorId))
                .orElse(0);
    }

    @Override
    public Optional<BranchNode> branchNodeAt(PipeAnchorId anchorId) {
        return this.data(anchorId.levelKey()).flatMap(data -> data.branchNodeAt(anchorId));
    }

    @Override
    public Optional<BranchNode> branchNodeManagingConnection(UUID connectionId) {
        return this.data.branchNodeManagingConnection(connectionId);
    }

    @Override
    public Optional<FoldAnchorNode> foldAnchorAt(PipeAnchorId anchorId) {
        return this.data(anchorId.levelKey()).flatMap(data -> data.foldAnchorAt(anchorId));
    }

    @Override
    public Optional<PipeAnchorId> localFoldCounterpart(PipeAnchorId anchorId) {
        Optional<FoldAnchorNode> source = this.foldAnchorAt(anchorId);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        return new FoldAnchorDirectory(this.server).counterpart(source.get()).map(FoldAnchorNode::anchorId);
    }

    @Override
    public long revision() {
        return this.data.revision();
    }

    private Optional<PipeNetworkSavedData> data(ResourceKey<Level> levelKey) {
        return Optional.of(this.data);
    }
}
