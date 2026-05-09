package dev.marblegate.superpipeslide.network.sync.route;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.event.ServerEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RouteDataResyncRequestPayload() implements CustomPacketPayload {
    public static final Type<RouteDataResyncRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "route_data_resync_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RouteDataResyncRequestPayload> STREAM_CODEC = StreamCodec.unit(new RouteDataResyncRequestPayload());

    public static void handleServer(RouteDataResyncRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ServerEvents.sendRouteDataSnapshotForResync(player);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
