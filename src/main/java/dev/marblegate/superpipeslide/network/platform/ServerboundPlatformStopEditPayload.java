package dev.marblegate.superpipeslide.network.platform;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.route.storage.RouteNetworkSavedData;
import dev.marblegate.superpipeslide.common.event.ServerEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;

public record ServerboundPlatformStopEditPayload(UUID requestId, long baseRouteRevision, UUID platformStopId, String platformNumber, Optional<String> displayName) implements CustomPacketPayload {
    public static final Type<ServerboundPlatformStopEditPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "platform_stop_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundPlatformStopEditPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ServerboundPlatformStopEditPayload::requestId,
            ByteBufCodecs.VAR_LONG.cast(),
            ServerboundPlatformStopEditPayload::baseRouteRevision,
            UUIDUtil.STREAM_CODEC,
            ServerboundPlatformStopEditPayload::platformStopId,
            ByteBufCodecs.STRING_UTF8,
            ServerboundPlatformStopEditPayload::platformNumber,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8),
            ServerboundPlatformStopEditPayload::displayName,
            ServerboundPlatformStopEditPayload::new
    );

    public ServerboundPlatformStopEditPayload(long baseRouteRevision, UUID platformStopId, String platformNumber, Optional<String> displayName) {
        this(UUID.randomUUID(), baseRouteRevision, platformStopId, platformNumber, displayName);
    }

    public static void handleServer(ServerboundPlatformStopEditPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        RouteNetworkSavedData routes = RouteNetworkSavedData.get(level.getServer());
        if (payload.baseRouteRevision() != routes.revision()) {
            ServerEvents.sendEditorResult(player, payload.requestId(), false, "Route data changed, please refresh", routes.revision());
            return;
        }
        if (routes.updatePlatformStop(payload.platformStopId(), payload.platformNumber(), payload.displayName()).isEmpty()) {
            ServerEvents.sendEditorResult(player, payload.requestId(), false, "Platform no longer exists", routes.revision());
            return;
        }
        ServerEvents.broadcastRouteDataDelta(level);
        ServerEvents.sendEditorResult(player, payload.requestId(), true, "Platform saved", routes.revision());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
