package dev.marblegate.superpipeslide.network.sync.pipe;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.networkgraph.model.PipeNode;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.UUID;

public record PipeNetworkDeltaPayload(
        long baseRevision,
        long revision,
        List<PipeNode> addedOrUpdatedNodes,
        List<PipeAnchorId> removedNodeIds,
        List<PipeConnection> addedOrUpdatedConnections,
        List<UUID> removedConnectionIds
) implements CustomPacketPayload {
    private static final int MAX_NODES = 32767;
    private static final int MAX_CONNECTIONS = 32767;

    public static final Type<PipeNetworkDeltaPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipe_network_delta"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PipeNetworkDeltaPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG.cast(),
            PipeNetworkDeltaPayload::baseRevision,
            ByteBufCodecs.VAR_LONG.cast(),
            PipeNetworkDeltaPayload::revision,
            PipeNode.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_NODES)),
            PipeNetworkDeltaPayload::addedOrUpdatedNodes,
            PipeAnchorId.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_NODES)),
            PipeNetworkDeltaPayload::removedNodeIds,
            PipeConnection.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CONNECTIONS)),
            PipeNetworkDeltaPayload::addedOrUpdatedConnections,
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CONNECTIONS)).cast(),
            PipeNetworkDeltaPayload::removedConnectionIds,
            PipeNetworkDeltaPayload::new
    );

    public PipeNetworkDeltaPayload {
        if (baseRevision < 0L || revision < 0L) {
            throw new IllegalArgumentException("Pipe network revisions cannot be negative");
        }
        if (revision < baseRevision) {
            throw new IllegalArgumentException("Pipe network revision cannot be older than its base revision");
        }
        addedOrUpdatedNodes = List.copyOf(addedOrUpdatedNodes);
        removedNodeIds = List.copyOf(removedNodeIds);
        addedOrUpdatedConnections = List.copyOf(addedOrUpdatedConnections);
        removedConnectionIds = List.copyOf(removedConnectionIds);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public boolean isEmpty() {
        return this.addedOrUpdatedNodes.isEmpty()
                && this.removedNodeIds.isEmpty()
                && this.addedOrUpdatedConnections.isEmpty()
                && this.removedConnectionIds.isEmpty();
    }
}
