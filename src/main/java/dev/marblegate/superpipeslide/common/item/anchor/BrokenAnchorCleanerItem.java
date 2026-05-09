package dev.marblegate.superpipeslide.common.item.anchor;

import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

public class BrokenAnchorCleanerItem extends Item {
    private static final double CLEAN_RADIUS = 32.0D;
    private static final double WIDE_CLEAN_RADIUS = 96.0D;

    public BrokenAnchorCleanerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.superpipeslide.broken_anchor_cleaner.creative_only").withStyle(ChatFormatting.RED));
        builder.accept(Component.translatable("tooltip.superpipeslide.broken_anchor_cleaner.use").withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable("tooltip.superpipeslide.broken_anchor_cleaner.sneak").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        return this.clean(serverLevel, player, player.position());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        return this.clean(serverLevel, player, Vec3.atCenterOf(context.getClickedPos()));
    }

    private InteractionResult clean(ServerLevel level, Player player, Vec3 center) {
        if (!player.getAbilities().instabuild) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.broken_anchor_cleaner_creative_only").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        double radius = player.isShiftKeyDown() ? WIDE_CLEAN_RADIUS : CLEAN_RADIUS;
        PipeNetworkSavedData.BrokenAnchorCleanupResult result = PipeNetworkSavedData.get(level).pruneMissingAnchorBlocksNear(level, center, radius);
        if (!result.changed()) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.broken_anchor_cleaner_none", formatRadius(radius)).withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS_SERVER;
        }

        player.sendOverlayMessage(Component.translatable(
                "message.superpipeslide.broken_anchor_cleaner_cleaned",
                result.removedNodes(),
                result.removedConnections(),
                formatRadius(radius)
        ).withStyle(ChatFormatting.YELLOW));
        return InteractionResult.SUCCESS_SERVER;
    }

    private static String formatRadius(double radius) {
        return String.valueOf((int) Math.round(radius));
    }
}
