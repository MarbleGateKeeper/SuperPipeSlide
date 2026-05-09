package dev.marblegate.superpipeslide.network.station;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.block.station.StationNameProjectorConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundStationNameProjectorConfigPayload(BlockPos pos, StationNameProjectorConfig config) implements CustomPacketPayload {
    public static final Type<ClientboundStationNameProjectorConfigPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "station_name_projector_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundStationNameProjectorConfigPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            ClientboundStationNameProjectorConfigPayload::pos,
            StationNameProjectorConfig.STREAM_CODEC,
            ClientboundStationNameProjectorConfigPayload::config,
            ClientboundStationNameProjectorConfigPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
