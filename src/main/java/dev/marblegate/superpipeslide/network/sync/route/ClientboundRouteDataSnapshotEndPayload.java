package dev.marblegate.superpipeslide.network.sync.route;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundRouteDataSnapshotEndPayload(long revision) implements CustomPacketPayload {
    public static final Type<ClientboundRouteDataSnapshotEndPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "route_data_snapshot_end"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundRouteDataSnapshotEndPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG.cast(),
            ClientboundRouteDataSnapshotEndPayload::revision,
            ClientboundRouteDataSnapshotEndPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
