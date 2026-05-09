package dev.marblegate.superpipeslide.network.station;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.route.storage.RouteNetworkSavedData;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.event.ServerEvents;
import dev.marblegate.superpipeslide.common.registry.SPSDataComponents;
import dev.marblegate.superpipeslide.common.registry.SPSItems;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

public record ServerboundStationEditPayload(UUID requestId, long baseRouteRevision, UUID stationGroupId, String primaryName, List<String> translatedNames, boolean generateClaimer) implements CustomPacketPayload {
    private static final int MAX_TRANSLATED_NAMES = 1;

    public static final Type<ServerboundStationEditPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "station_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundStationEditPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ServerboundStationEditPayload::requestId,
            ByteBufCodecs.VAR_LONG.cast(),
            ServerboundStationEditPayload::baseRouteRevision,
            UUIDUtil.STREAM_CODEC,
            ServerboundStationEditPayload::stationGroupId,
            ByteBufCodecs.STRING_UTF8,
            ServerboundStationEditPayload::primaryName,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_TRANSLATED_NAMES)),
            ServerboundStationEditPayload::translatedNames,
            ByteBufCodecs.BOOL,
            ServerboundStationEditPayload::generateClaimer,
            ServerboundStationEditPayload::new
    );

    public ServerboundStationEditPayload(long baseRouteRevision, UUID stationGroupId, String primaryName, List<String> translatedNames, boolean generateClaimer) {
        this(UUID.randomUUID(), baseRouteRevision, stationGroupId, primaryName, translatedNames, generateClaimer);
    }

    public ServerboundStationEditPayload {
        translatedNames = translatedNames.stream().filter(name -> !name.isBlank()).limit(MAX_TRANSLATED_NAMES).toList();
    }

    public static void handleServer(ServerboundStationEditPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        RouteNetworkSavedData routes = RouteNetworkSavedData.get(level.getServer());
        if (payload.baseRouteRevision() != routes.revision()) {
            ServerEvents.sendEditorResult(player, payload.requestId(), false, "Route data changed, please refresh", routes.revision());
            return;
        }
        StationGroup stationGroup = routes.updateStationGroup(payload.stationGroupId(), payload.primaryName(), payload.translatedNames()).orElse(null);
        if (stationGroup == null) {
            ServerEvents.sendEditorResult(player, payload.requestId(), false, "Station no longer exists", routes.revision());
            return;
        }
        if (payload.generateClaimer()) {
            ItemStack stack = new ItemStack(SPSItems.PLATFORM_CLAIMER.get());
            stack.set(SPSDataComponents.PLATFORM_CLAIMER_STATION.get(), stationGroup.id());
            stack.set(SPSDataComponents.PLATFORM_CLAIMER_STATION_NAME.get(), stationGroup.primaryName());
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        ServerEvents.broadcastRouteDataDelta(level);
        ServerEvents.sendEditorResult(player, payload.requestId(), true, "Station saved", routes.revision());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

