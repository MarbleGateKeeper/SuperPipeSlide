package dev.marblegate.superpipeslide.common.item.route;

import dev.marblegate.superpipeslide.common.event.ServerEvents;
import dev.marblegate.superpipeslide.network.route.ClientboundOpenFullRouteMapPayload;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public class PipeTransitGuideItem extends Item {
    public PipeTransitGuideItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.superpipeslide.pipe_transit_guide.use").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return openFullRouteMap(level, player);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return openFullRouteMap(context.getLevel(), context.getPlayer());
    }

    private static InteractionResult openFullRouteMap(Level level, Player player) {
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            ServerEvents.sendRouteDataSnapshotIfNeeded(serverPlayer);
            PacketDistributor.sendToPlayer(serverPlayer, new ClientboundOpenFullRouteMapPayload());
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.PASS;
    }
}
