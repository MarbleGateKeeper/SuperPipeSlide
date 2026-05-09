package dev.marblegate.superpipeslide.network.station;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.route.storage.RouteNetworkSavedData;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.event.ServerEvents;
import dev.marblegate.superpipeslide.config.Config;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record ServerboundStationTransferEditPayload(
        UUID requestId,
        String action,
        long baseRouteRevision,
        UUID stationGroupId,
        UUID otherStationGroupId,
        boolean confirmedRisk
) implements CustomPacketPayload {
    public static final String ADD = "add";
    public static final String REMOVE = "remove";

    public static final Type<ServerboundStationTransferEditPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "station_transfer_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundStationTransferEditPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ServerboundStationTransferEditPayload::requestId,
            ByteBufCodecs.STRING_UTF8,
            ServerboundStationTransferEditPayload::action,
            ByteBufCodecs.VAR_LONG.cast(),
            ServerboundStationTransferEditPayload::baseRouteRevision,
            UUIDUtil.STREAM_CODEC,
            ServerboundStationTransferEditPayload::stationGroupId,
            UUIDUtil.STREAM_CODEC,
            ServerboundStationTransferEditPayload::otherStationGroupId,
            ByteBufCodecs.BOOL,
            ServerboundStationTransferEditPayload::confirmedRisk,
            ServerboundStationTransferEditPayload::new
    );

    public ServerboundStationTransferEditPayload(String action, long baseRouteRevision, UUID stationGroupId, UUID otherStationGroupId, boolean confirmedRisk) {
        this(UUID.randomUUID(), action, baseRouteRevision, stationGroupId, otherStationGroupId, confirmedRisk);
    }

    public static void handleServer(ServerboundStationTransferEditPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        RouteNetworkSavedData routes = RouteNetworkSavedData.get(level.getServer());
        if (payload.baseRouteRevision() != routes.revision()) {
            ServerEvents.sendEditorResult(player, payload.requestId(), false, "Route data changed, please refresh", routes.revision());
            return;
        }
        StationGroup station = routes.stationGroup(payload.stationGroupId()).orElse(null);
        StationGroup other = routes.stationGroup(payload.otherStationGroupId()).orElse(null);
        if (station == null || other == null || station.id().equals(other.id())) {
            ServerEvents.sendEditorResult(player, payload.requestId(), false, "Station transfer target no longer exists", routes.revision());
            return;
        }
        if (REMOVE.equals(payload.action())) {
            routes.deleteStationTransferLink(station.id(), other.id());
            ServerEvents.broadcastRouteDataDelta(level);
            ServerEvents.sendEditorResult(player, payload.requestId(), true, "Station transfer removed", routes.revision());
            return;
        }
        if (!ADD.equals(payload.action())) {
            ServerEvents.sendEditorResult(player, payload.requestId(), false, "Unknown station transfer edit action", routes.revision());
            return;
        }
        boolean risky = transferRisky(station, other);
        if (risky && !payload.confirmedRisk()) {
            ServerEvents.sendEditorResult(player, payload.requestId(), false, "Station transfer requires confirmation", routes.revision());
            return;
        }
        routes.createStationTransferLink(station.id(), other.id(), true, risky);
        ServerEvents.broadcastRouteDataDelta(level);
        ServerEvents.sendEditorResult(player, payload.requestId(), true, "Station transfer saved", routes.revision());
    }

    private static boolean transferRisky(StationGroup first, StationGroup second) {
        if (!first.levelKey().equals(second.levelKey())) {
            return true;
        }
        double warningDistance = Config.FAR_OUT_OF_STATION_TRANSFER_WARNING_DISTANCE.getAsDouble();
        return RouteNetworkSavedData.stationDistanceSqr(first, second) > warningDistance * warningDistance;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
