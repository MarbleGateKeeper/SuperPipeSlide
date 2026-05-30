package dev.marblegate.superpipeslide.network.sync.pipe;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.networkgraph.model.PipeNode;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PipeNetworkSnapshotChunkPayload(UUID snapshotId, long revision, int chunkIndex, List<PipeNode> nodes, List<PipeConnection> connections) implements CustomPacketPayload {

    private static final int MAX_NODES_PER_CHUNK = 512;
    private static final int MAX_CONNECTIONS_PER_CHUNK = 128;

    public static final Type<PipeNetworkSnapshotChunkPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipe_network_snapshot_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PipeNetworkSnapshotChunkPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            PipeNetworkSnapshotChunkPayload::snapshotId,
            ByteBufCodecs.VAR_LONG.cast(),
            PipeNetworkSnapshotChunkPayload::revision,
            ByteBufCodecs.VAR_INT.cast(),
            PipeNetworkSnapshotChunkPayload::chunkIndex,
            PipeNode.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_NODES_PER_CHUNK)),
            PipeNetworkSnapshotChunkPayload::nodes,
            PipeConnection.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CONNECTIONS_PER_CHUNK)),
            PipeNetworkSnapshotChunkPayload::connections,
            PipeNetworkSnapshotChunkPayload::new);
    public PipeNetworkSnapshotChunkPayload {
        if (snapshotId == null) {
            throw new IllegalArgumentException("Pipe network snapshot id cannot be null");
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("Pipe network revision cannot be negative");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("Pipe network snapshot chunk index cannot be negative");
        }
        nodes = List.copyOf(nodes);
        connections = List.copyOf(connections);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
