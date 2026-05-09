package dev.marblegate.superpipeslide.network.platform;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.block.station.PlatformProjectorBlockEntity;
import dev.marblegate.superpipeslide.common.block.station.PlatformProjectorConfig;
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

public record ServerboundPlatformProjectorSavePayload(UUID requestId, BlockPos pos, PlatformProjectorConfig config) implements CustomPacketPayload {
    public static final Type<ServerboundPlatformProjectorSavePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "platform_projector_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundPlatformProjectorSavePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ServerboundPlatformProjectorSavePayload::requestId,
            BlockPos.STREAM_CODEC,
            ServerboundPlatformProjectorSavePayload::pos,
            PlatformProjectorConfig.STREAM_CODEC,
            ServerboundPlatformProjectorSavePayload::config,
            ServerboundPlatformProjectorSavePayload::new
    );

    public ServerboundPlatformProjectorSavePayload(BlockPos pos, PlatformProjectorConfig config) {
        this(UUID.randomUUID(), pos, config);
    }

    public static void handleServer(ServerboundPlatformProjectorSavePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(level.getBlockEntity(payload.pos()) instanceof PlatformProjectorBlockEntity projector)) {
            ServerEvents.sendEditorResult(player, payload.requestId(), false, "Platform projector no longer exists", 0L);
            return;
        }
        projector.applyConfig(payload.config());
        ServerEvents.sendEditorResult(player, payload.requestId(), true, "Platform projector saved", 0L);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
