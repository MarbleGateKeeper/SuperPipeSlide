package dev.marblegate.superpipeslide.common.item.anchor;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorKind;
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

public class FoldAnchorUpgradeItem extends Item {
    private final FoldAnchorKind kind;

    public FoldAnchorUpgradeItem(Properties properties, FoldAnchorKind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        String kindKey = this.kind == FoldAnchorKind.SPACE ? "space_fold" : "dimension_fold";
        builder.accept(Component.translatable("tooltip.superpipeslide.anchor_upgrade." + kindKey).withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable("tooltip.superpipeslide.anchor_upgrade.fold_single_connection").withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable("tooltip.superpipeslide.consumed_on_use").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        BlockState state = level.getBlockState(context.getClickedPos());
        if (!state.is(SPSBlocks.PIPE_ANCHOR.get())) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        PipeAnchorId anchorId = PipeAnchorId.of(level, context.getClickedPos());
        PipeNetworkSavedData data = PipeNetworkSavedData.get(serverLevel);
        if (data.upgradePipeAnchorToFold(anchorId, this.kind).isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.fold_anchor_upgrade_failed").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        Block targetBlock = this.kind == FoldAnchorKind.SPACE ? SPSBlocks.SPACE_FOLD_ANCHOR.get() : SPSBlocks.DIMENSION_FOLD_ANCHOR.get();
        level.setBlock(context.getClickedPos(), targetBlock.defaultBlockState(), Block.UPDATE_ALL);
        consume(context.getItemInHand(), player);
        String messageKey = this.kind == FoldAnchorKind.SPACE
                ? "message.superpipeslide.space_fold_anchor_upgraded"
                : "message.superpipeslide.dimension_fold_anchor_upgraded";
        player.sendOverlayMessage(Component.translatable(messageKey).withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS_SERVER;
    }

    private void consume(ItemStack stack, Player player) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }
}
