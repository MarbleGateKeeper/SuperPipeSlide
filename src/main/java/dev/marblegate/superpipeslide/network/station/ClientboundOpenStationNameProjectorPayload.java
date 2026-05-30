package dev.marblegate.superpipeslide.network.station;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.block.station.StationNameProjectorConfig;
import dev.marblegate.superpipeslide.common.core.projection.layout.AppliedProjectionLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundOpenStationNameProjectorPayload(BlockPos pos, StationNameProjectorConfig config, AppliedProjectionLayout appliedLayout) implements CustomPacketPayload {

    public static final Type<ClientboundOpenStationNameProjectorPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "open_station_name_projector"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundOpenStationNameProjectorPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            ClientboundOpenStationNameProjectorPayload::pos,
            StationNameProjectorConfig.STREAM_CODEC,
            ClientboundOpenStationNameProjectorPayload::config,
            AppliedProjectionLayout.STREAM_CODEC,
            ClientboundOpenStationNameProjectorPayload::appliedLayout,
            ClientboundOpenStationNameProjectorPayload::new);
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
