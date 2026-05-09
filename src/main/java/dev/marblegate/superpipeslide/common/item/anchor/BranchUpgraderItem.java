package dev.marblegate.superpipeslide.common.item.anchor;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkSavedData;
import dev.marblegate.superpipeslide.common.registry.SPSBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

public class BranchUpgraderItem extends Item {
    private final boolean consumeOnUpgrade;
    private final boolean allowDowngrade;

    public BranchUpgraderItem(Properties properties) {
        this(properties, false, true);
    }

    public BranchUpgraderItem(Properties properties, boolean consumeOnUpgrade, boolean allowDowngrade) {
        super(properties);
        this.consumeOnUpgrade = consumeOnUpgrade;
        this.allowDowngrade = allowDowngrade;
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        builder.accept(Component.translatable("tooltip.superpipeslide.anchor_upgrade.branch").withStyle(ChatFormatting.DARK_GRAY));
        if (this.allowDowngrade) {
            builder.accept(Component.translatable("tooltip.superpipeslide.anchor_upgrade.branch_to_pipe").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (this.consumeOnUpgrade) {
            builder.accept(Component.translatable("tooltip.superpipeslide.consumed_on_use").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        BlockState state = level.getBlockState(context.getClickedPos());
        boolean pipeAnchor = state.is(SPSBlocks.PIPE_ANCHOR.get());
        boolean branchAnchor = state.is(SPSBlocks.BRANCH_ANCHOR.get());
        if (!pipeAnchor && !branchAnchor) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        PipeAnchorId anchorId = PipeAnchorId.of(level, context.getClickedPos());
        PipeNetworkSavedData data = PipeNetworkSavedData.get(serverLevel);
        if (pipeAnchor) {
            if (data.upgradePipeAnchorToBranch(anchorId).isEmpty()) {
                player.sendOverlayMessage(Component.translatable("message.superpipeslide.branch_upgrade_failed").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            level.setBlock(context.getClickedPos(), SPSBlocks.BRANCH_ANCHOR.get().defaultBlockState(), Block.UPDATE_ALL);
            consume(context.getItemInHand(), player);
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.branch_upgraded").withStyle(ChatFormatting.LIGHT_PURPLE));
            return InteractionResult.SUCCESS_SERVER;
        }

        if (!this.allowDowngrade) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.anchor_upgrade_branch_only").withStyle(ChatFormatting.YELLOW));
            return InteractionResult.FAIL;
        }

        data.downgradeBranchAnchorToPipe(anchorId);
        level.setBlock(context.getClickedPos(), SPSBlocks.PIPE_ANCHOR.get().defaultBlockState(), Block.UPDATE_ALL);
        player.sendOverlayMessage(Component.translatable("message.superpipeslide.branch_downgraded").withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS_SERVER;
    }

    private void consume(ItemStack stack, Player player) {
        if (this.consumeOnUpgrade && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }
}
