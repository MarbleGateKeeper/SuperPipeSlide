package dev.marblegate.superpipeslide.network.sync.pipe;


import dev.marblegate.superpipeslide.common.event.ServerEvents;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PipeNetworkResyncRequestPayload() implements CustomPacketPayload {
    public static final Type<PipeNetworkResyncRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipe_network_resync_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PipeNetworkResyncRequestPayload> STREAM_CODEC = StreamCodec.unit(new PipeNetworkResyncRequestPayload());

    public PipeNetworkResyncRequestPayload {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(PipeNetworkResyncRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            dev.marblegate.superpipeslide.common.event.ServerEvents.sendPipeNetworkSnapshotForResync(player);
        }
    }
}
