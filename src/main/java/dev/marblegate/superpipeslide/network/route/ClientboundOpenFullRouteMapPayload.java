package dev.marblegate.superpipeslide.network.route;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundOpenFullRouteMapPayload() implements CustomPacketPayload {
    public static final Type<ClientboundOpenFullRouteMapPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "open_full_route_map"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundOpenFullRouteMapPayload> STREAM_CODEC = StreamCodec.unit(new ClientboundOpenFullRouteMapPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
