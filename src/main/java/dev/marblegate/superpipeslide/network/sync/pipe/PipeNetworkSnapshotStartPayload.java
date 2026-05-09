package dev.marblegate.superpipeslide.network.sync.pipe;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record PipeNetworkSnapshotStartPayload(UUID snapshotId, long revision, int totalNodes, int totalConnections, int chunkCount) implements CustomPacketPayload {
    public static final Type<PipeNetworkSnapshotStartPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipe_network_snapshot_start"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PipeNetworkSnapshotStartPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            PipeNetworkSnapshotStartPayload::snapshotId,
            ByteBufCodecs.VAR_LONG.cast(),
            PipeNetworkSnapshotStartPayload::revision,
            ByteBufCodecs.VAR_INT.cast(),
            PipeNetworkSnapshotStartPayload::totalNodes,
            ByteBufCodecs.VAR_INT.cast(),
            PipeNetworkSnapshotStartPayload::totalConnections,
            ByteBufCodecs.VAR_INT.cast(),
            PipeNetworkSnapshotStartPayload::chunkCount,
            PipeNetworkSnapshotStartPayload::new
    );

    public PipeNetworkSnapshotStartPayload {
        if (snapshotId == null) {
            throw new IllegalArgumentException("Pipe network snapshot id cannot be null");
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("Pipe network revision cannot be negative");
        }
        if (totalNodes < 0 || totalConnections < 0) {
            throw new IllegalArgumentException("Pipe network snapshot counts cannot be negative");
        }
        if (chunkCount < 0) {
            throw new IllegalArgumentException("Pipe network snapshot chunk count cannot be negative");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
