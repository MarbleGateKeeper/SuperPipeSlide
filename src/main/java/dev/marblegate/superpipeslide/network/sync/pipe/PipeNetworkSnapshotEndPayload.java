package dev.marblegate.superpipeslide.network.sync.pipe;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record PipeNetworkSnapshotEndPayload(UUID snapshotId, long revision) implements CustomPacketPayload {
    public static final Type<PipeNetworkSnapshotEndPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipe_network_snapshot_end"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PipeNetworkSnapshotEndPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            PipeNetworkSnapshotEndPayload::snapshotId,
            ByteBufCodecs.VAR_LONG.cast(),
            PipeNetworkSnapshotEndPayload::revision,
            PipeNetworkSnapshotEndPayload::new
    );

    public PipeNetworkSnapshotEndPayload {
        if (snapshotId == null) {
            throw new IllegalArgumentException("Pipe network snapshot id cannot be null");
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("Pipe network revision cannot be negative");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
