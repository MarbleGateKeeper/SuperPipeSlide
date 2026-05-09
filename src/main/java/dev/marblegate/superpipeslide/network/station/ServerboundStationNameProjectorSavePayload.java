package dev.marblegate.superpipeslide.network.station;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.block.station.StationNameProjectorBlockEntity;
import dev.marblegate.superpipeslide.common.block.station.StationNameProjectorConfig;
import dev.marblegate.superpipeslide.common.event.ServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record ServerboundStationNameProjectorSavePayload(UUID requestId, BlockPos pos, StationNameProjectorConfig config) implements CustomPacketPayload {
    public static final Type<ServerboundStationNameProjectorSavePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "station_name_projector_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundStationNameProjectorSavePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ServerboundStationNameProjectorSavePayload::requestId,
            BlockPos.STREAM_CODEC,
            ServerboundStationNameProjectorSavePayload::pos,
            StationNameProjectorConfig.STREAM_CODEC,
            ServerboundStationNameProjectorSavePayload::config,
            ServerboundStationNameProjectorSavePayload::new
    );

    public ServerboundStationNameProjectorSavePayload(BlockPos pos, StationNameProjectorConfig config) {
        this(UUID.randomUUID(), pos, config);
    }

    public static void handleServer(ServerboundStationNameProjectorSavePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(level.getBlockEntity(payload.pos()) instanceof StationNameProjectorBlockEntity projector)) {
            ServerEvents.sendEditorResult(player, payload.requestId(), false, "Station projector no longer exists", 0L);
            return;
        }
        projector.applyConfig(payload.config());
        ServerEvents.sendEditorResult(player, payload.requestId(), true, "Station projector saved", 0L);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
