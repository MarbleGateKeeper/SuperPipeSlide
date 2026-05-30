package dev.marblegate.superpipeslide.network.route;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.ServerPipeNetworkView;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.storage.RouteNetworkSavedData;
import dev.marblegate.superpipeslide.common.event.ServerEvents;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ServerboundRouteEditPayload(
        UUID requestId,
        String action,
        long baseRouteRevision,
        Optional<UUID> targetId,
        String name,
        List<String> translatedNames,
        List<Integer> themeColors,
        List<UUID> platformStopIds,
        boolean bidirectional,
        boolean loop,
        boolean nameAsSectionName) implements CustomPacketPayload {

    private static final int MAX_TRANSLATED_NAMES = 1;
    private static final int MAX_THEME_COLORS = 3;
    private static final int MAX_STOPS = 512;

    public static final String CREATE_LINE = "create_line";
    public static final String UPDATE_LINE = "update_line";
    public static final String DELETE_LINE = "delete_line";
    public static final String CREATE_LAYOUT = "create_layout";
    public static final String UPDATE_LAYOUT = "update_layout";
    public static final String DELETE_LAYOUT = "delete_layout";
    public static final String SET_LAYOUT_STOPS = "set_layout_stops";
    public static final String SPLIT_LAYOUT = "split_layout";
    public static final String DELETE_PLATFORM_STOP = "delete_platform_stop";

    public static final Type<ServerboundRouteEditPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "route_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundRouteEditPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ServerboundRouteEditPayload::requestId,
            ByteBufCodecs.STRING_UTF8,
            ServerboundRouteEditPayload::action,
            ByteBufCodecs.VAR_LONG.cast(),
            ServerboundRouteEditPayload::baseRouteRevision,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).cast(),
            ServerboundRouteEditPayload::targetId,
            ByteBufCodecs.STRING_UTF8,
            ServerboundRouteEditPayload::name,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_TRANSLATED_NAMES)),
            ServerboundRouteEditPayload::translatedNames,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_THEME_COLORS)),
            ServerboundRouteEditPayload::themeColors,
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STOPS)).cast(),
            ServerboundRouteEditPayload::platformStopIds,
            ByteBufCodecs.BOOL,
            ServerboundRouteEditPayload::bidirectional,
            ByteBufCodecs.BOOL,
            ServerboundRouteEditPayload::loop,
            ByteBufCodecs.BOOL,
            ServerboundRouteEditPayload::nameAsSectionName,
            ServerboundRouteEditPayload::new);
    public ServerboundRouteEditPayload(String action, long baseRouteRevision, Optional<UUID> targetId, String name, List<String> translatedNames, List<Integer> themeColors, List<UUID> platformStopIds, boolean bidirectional, boolean loop) {
        this(UUID.randomUUID(), action, baseRouteRevision, targetId, name, translatedNames, themeColors, platformStopIds, bidirectional, loop, true);
    }

    public ServerboundRouteEditPayload(String action, long baseRouteRevision, Optional<UUID> targetId, String name, List<String> translatedNames, List<Integer> themeColors, List<UUID> platformStopIds, boolean bidirectional, boolean loop, boolean nameAsSectionName) {
        this(UUID.randomUUID(), action, baseRouteRevision, targetId, name, translatedNames, themeColors, platformStopIds, bidirectional, loop, nameAsSectionName);
    }

    public ServerboundRouteEditPayload {
        translatedNames = translatedNames.stream().filter(entry -> !entry.isBlank()).limit(MAX_TRANSLATED_NAMES).toList();
        themeColors = List.copyOf(themeColors);
        platformStopIds = List.copyOf(platformStopIds);
    }

    public static void handleServer(ServerboundRouteEditPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        RouteNetworkSavedData routes = RouteNetworkSavedData.get(level.getServer());
        ServerPipeNetworkView pipeView = ServerPipeNetworkView.of(level.getServer());
        if (payload.baseRouteRevision() != routes.revision()) {
            ServerEvents.sendEditorResult(player, payload.requestId(), false, "Route data changed, please refresh", routes.revision());
            return;
        }
        DeletionAudit deletionAudit = DeletionAudit.capture(payload, routes, player);
        boolean accepted = switch (payload.action()) {
            case CREATE_LINE -> routes.createRouteLine(payload.name(), payload.translatedNames(), payload.themeColors()).isPresent();
            case UPDATE_LINE -> payload.targetId().flatMap(id -> routes.updateRouteLine(id, payload.name(), payload.translatedNames(), payload.themeColors())).isPresent();
            case DELETE_LINE -> payload.targetId().filter(routes::deleteRouteLine).isPresent();
            case CREATE_LAYOUT -> payload.targetId().flatMap(id -> routes.createRouteLayout(id, optionalString(payload.name()), payload.translatedNames(), payload.bidirectional(), payload.loop(), payload.nameAsSectionName())).isPresent();
            case UPDATE_LAYOUT -> payload.targetId().flatMap(id -> routes.updateRouteLayout(id, optionalString(payload.name()), payload.translatedNames(), payload.bidirectional(), payload.loop(), payload.nameAsSectionName(), pipeView)).isPresent();
            case DELETE_LAYOUT -> payload.targetId().filter(routes::deleteRouteLayout).isPresent();
            case SET_LAYOUT_STOPS -> payload.targetId().flatMap(id -> routes.setLayoutStops(id, payload.platformStopIds(), pipeView)).isPresent();
            case SPLIT_LAYOUT -> payload.targetId().flatMap(id -> routes.splitBidirectionalLayout(id, payload.name(), pipeView)).isPresent();
            case DELETE_PLATFORM_STOP -> payload.targetId().filter(id -> routes.deletePlatformStop(id, level.getServer())).isPresent();
            default -> false;
        };
        deletionAudit.log(accepted, routes.revision());
        if (!accepted) {
            ServerEvents.sendEditorResult(player, payload.requestId(), false, "Route edit failed", routes.revision());
            return;
        }
        boolean stillHasDirtySections = routes.hasDirtyRouteSections();
        ServerEvents.broadcastRouteDataDelta(level);
        if (stillHasDirtySections) {
            ServerEvents.queueRouteDataDelta(level);
        }
        ServerEvents.sendEditorResult(player, payload.requestId(), true, "Route edit saved", routes.revision());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static Optional<String> optionalString(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
    }

    private record DeletionAudit(
            String action,
            UUID requestId,
            long baseRevision,
            String playerName,
            UUID playerId,
            String dimension,
            BlockPos position,
            Optional<UUID> targetId,
            String targetSummary) {
        private static DeletionAudit capture(ServerboundRouteEditPayload payload, RouteNetworkSavedData routes, ServerPlayer player) {
            if (!DELETE_LINE.equals(payload.action()) && !DELETE_LAYOUT.equals(payload.action())) {
                return NONE;
            }
            String summary = payload.targetId()
                    .map(id -> DELETE_LINE.equals(payload.action()) ? lineSummary(routes, id) : layoutSummary(routes, id))
                    .orElse("target=<missing>");
            return new DeletionAudit(
                    payload.action(),
                    payload.requestId(),
                    payload.baseRouteRevision(),
                    player.getName().getString(),
                    player.getUUID(),
                    player.level().dimension().identifier().toString(),
                    player.blockPosition(),
                    payload.targetId(),
                    summary);
        }

        private void log(boolean accepted, long currentRevision) {
            if (this == NONE) {
                return;
            }
            SuperPipeSlide.LOGGER.debug(
                    "[RouteAudit] action={} accepted={} at={} player={} uuid={} dim={} pos={},{},{} request={} baseRevision={} currentRevision={} targetId={} {}",
                    this.action,
                    accepted,
                    Instant.now(),
                    this.playerName,
                    this.playerId,
                    this.dimension,
                    this.position.getX(),
                    this.position.getY(),
                    this.position.getZ(),
                    this.requestId,
                    this.baseRevision,
                    currentRevision,
                    this.targetId.map(UUID::toString).orElse("<none>"),
                    this.targetSummary);
        }

        private static String lineSummary(RouteNetworkSavedData routes, UUID routeLineId) {
            Optional<RouteLine> line = routes.routeLine(routeLineId);
            if (line.isEmpty()) {
                return "line=<missing>";
            }
            RouteLine value = line.get();
            return "lineName=\"" + value.displayName() + "\" layoutCount=" + value.layoutIds().size();
        }

        private static String layoutSummary(RouteNetworkSavedData routes, UUID routeLayoutId) {
            Optional<RouteLayout> layout = routes.routeLayout(routeLayoutId);
            if (layout.isEmpty()) {
                return "layout=<missing>";
            }
            RouteLayout value = layout.get();
            String lineName = routes.routeLine(value.routeLineId()).map(RouteLine::displayName).orElse("<missing>");
            return "layoutName=\"" + value.displayName().orElse("") + "\" lineId=" + value.routeLineId()
                    + " lineName=\"" + lineName + "\" stops=" + value.orderedPlatformStops().size()
                    + " bidirectional=" + value.bidirectional()
                    + " loop=" + value.loop();
        }

        private static final DeletionAudit NONE = new DeletionAudit("", new UUID(0L, 0L), 0L, "", new UUID(0L, 0L), "", BlockPos.ZERO, Optional.empty(), "");
    }
}
