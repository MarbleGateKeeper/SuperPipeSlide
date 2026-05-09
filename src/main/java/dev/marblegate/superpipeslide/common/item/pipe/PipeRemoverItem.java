package dev.marblegate.superpipeslide.common.item.pipe;

import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRaycast;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.Consumer;

public class PipeRemoverItem extends Item {
    private static final double REMOVE_REACH = 8.0D;
    private static final double PIPE_PICK_RADIUS = 0.55D;

    public PipeRemoverItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.superpipeslide.pipe_remover.use").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        PipeNetworkSavedData data = PipeNetworkSavedData.get(serverLevel);
        Vec3 eye = player.getEyePosition();
        Optional<PipeConnectionRaycast.Hit> target = PipeConnectionRaycast.find(
                data.connectionsIn(serverLevel),
                eye,
                player.getLookAngle(),
                REMOVE_REACH,
                PIPE_PICK_RADIUS
        );
        if (target.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.no_pipe_connection_targeted").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        PipeConnection removed = data.removeConnection(target.get().connection().id()).orElse(null);
        if (removed == null) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.pipe_connection_missing").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        player.sendOverlayMessage(Component.translatable("message.superpipeslide.pipe_removed").withStyle(ChatFormatting.YELLOW));
        return InteractionResult.SUCCESS_SERVER;
    }
}
