package dev.marblegate.superpipeslide.common.item.route;

import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRaycast;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkSavedData;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.core.route.storage.RouteNetworkSavedData;
import dev.marblegate.superpipeslide.common.event.ServerEvents;
import dev.marblegate.superpipeslide.common.registry.SPSDataComponents;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class PlatformClaimerItem extends Item {
    private static final double CLAIM_REACH = 8.0D;
    private static final double PIPE_PICK_RADIUS = 0.55D;

    public PlatformClaimerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        UUID stationId = stack.get(SPSDataComponents.PLATFORM_CLAIMER_STATION.get());
        if (stationId == null) {
            builder.accept(Component.translatable("tooltip.superpipeslide.platform_claimer.unbound").withStyle(ChatFormatting.RED));
        } else {
            String stationName = stack.get(SPSDataComponents.PLATFORM_CLAIMER_STATION_NAME.get());
            builder.accept(Component.translatable("tooltip.superpipeslide.platform_claimer.bound", stationName == null || stationName.isBlank() ? stationId.toString().substring(0, 8) : stationName).withStyle(ChatFormatting.AQUA));
        }
        builder.accept(Component.translatable("tooltip.superpipeslide.platform_claimer.use").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        ItemStack stack = player.getItemInHand(hand);
        UUID stationId = stack.get(SPSDataComponents.PLATFORM_CLAIMER_STATION.get());
        if (stationId == null) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.platform_claimer_unbound").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        RouteNetworkSavedData routes = RouteNetworkSavedData.get(serverLevel.getServer());
        StationGroup stationGroup = routes.stationGroup(stationId).orElse(null);
        if (stationGroup == null || !stationGroup.levelKey().equals(serverLevel.dimension())) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.station_missing").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        PipeNetworkSavedData pipeData = PipeNetworkSavedData.get(serverLevel);
        Vec3 eye = player.getEyePosition();
        Optional<PipeConnectionRaycast.Hit> target = PipeConnectionRaycast.find(
                pipeData.connectionsNear(serverLevel, eye, CLAIM_REACH + 2.0D),
                eye,
                player.getLookAngle(),
                CLAIM_REACH,
                PIPE_PICK_RADIUS);
        if (target.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.no_pipe_connection_targeted").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        PipeConnection connection = target.get().connection();
        Vec3 midpoint = connection.positionAt(connection.length() * 0.5D);
        if (midpoint.distanceTo(Vec3.atCenterOf(stationGroup.stationBlockPos())) > stationGroup.platformClaimRadius()) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.platform_claim_too_far").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        Optional<PlatformStop> existing = connection.platformStopId().flatMap(routes::platformStop).or(() -> routes.platformStopByConnection(PipeConnectionRef.of(connection)));
        if (existing.isPresent()) {
            PlatformStop platformStop = existing.get();
            if (!routes.deletePlatformStop(platformStop.id(), serverLevel.getServer())) {
                player.sendOverlayMessage(Component.translatable("message.superpipeslide.pipe_connection_missing").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            String claimedStationName = routes.stationGroup(platformStop.stationGroupId())
                    .map(StationGroup::primaryName)
                    .orElse(stationGroup.primaryName());
            ServerEvents.sendPipeNetworkSnapshot(serverPlayer);
            ServerEvents.broadcastRouteDataDelta(serverLevel);
            player.sendOverlayMessage(Component.translatable(
                    "message.superpipeslide.platform_unclaimed",
                    claimedStationName,
                    platformStop.platformNumber()).withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS_SERVER;
        }

        if (pipeData.branchNodeManagingConnection(connection.id()).isPresent() || pipeData.isBranchNode(connection.fromAnchor()) || pipeData.isBranchNode(connection.toAnchor())) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.platform_claim_branch_connection").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        PlatformStop platformStop = routes.createPlatformStop(stationGroup, connection, pipeData);
        ServerEvents.sendPipeNetworkSnapshot(serverPlayer);
        ServerEvents.broadcastRouteDataDelta(serverLevel);
        player.sendOverlayMessage(Component.translatable(
                "message.superpipeslide.platform_claimed",
                stationGroup.primaryName(),
                platformStop.platformNumber(),
                String.format("%.1f", platformStop.length())).withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS_SERVER;
    }
}
