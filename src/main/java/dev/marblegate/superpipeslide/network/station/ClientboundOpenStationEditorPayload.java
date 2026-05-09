package dev.marblegate.superpipeslide.network.station;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record ClientboundOpenStationEditorPayload(UUID stationGroupId) implements CustomPacketPayload {
    public static final Type<ClientboundOpenStationEditorPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "open_station_editor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundOpenStationEditorPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ClientboundOpenStationEditorPayload::stationGroupId,
            ClientboundOpenStationEditorPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
