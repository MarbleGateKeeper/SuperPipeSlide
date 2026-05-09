package dev.marblegate.superpipeslide.common.item.pipe;

import dev.marblegate.superpipeslide.common.core.appearance.model.PipeAppearanceProfile;
import dev.marblegate.superpipeslide.common.core.appearance.storage.PipeAppearanceSavedData;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRaycast;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkSavedData;
import dev.marblegate.superpipeslide.common.event.ServerEvents;
import dev.marblegate.superpipeslide.common.registry.SPSDataComponents;
import dev.marblegate.superpipeslide.network.pipe.appearance.ClientboundOpenPipeAppearanceEditorPayload;
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
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;
import java.util.function.Consumer;

public class PipeAppearanceToolItem extends Item {
    private static final double TOOL_REACH = 8.0D;
    private static final double PIPE_PICK_RADIUS = 0.58D;

    public PipeAppearanceToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        PipeAppearanceProfile draft = stack.get(SPSDataComponents.PIPE_APPEARANCE_DRAFT.get());
        if (draft == null) {
            builder.accept(Component.translatable("tooltip.superpipeslide.pipe_appearance_tool.no_draft").withStyle(ChatFormatting.GRAY));
        } else {
            builder.accept(Component.translatable("tooltip.superpipeslide.pipe_appearance_tool.draft", Component.translatable("pipe_appearance.superpipeslide.style." + draft.normalizedToDefinitions().styleId())).withStyle(ChatFormatting.AQUA));
        }
        builder.accept(Component.translatable("tooltip.superpipeslide.pipe_appearance_tool.use").withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable("tooltip.superpipeslide.pipe_appearance_tool.brush").withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable("tooltip.superpipeslide.pipe_appearance_tool.pick").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        PipeNetworkSavedData pipes = PipeNetworkSavedData.get(serverLevel);
        Optional<PipeConnectionRaycast.Hit> target = PipeConnectionRaycast.find(
                pipes.connectionsNear(serverLevel, player.getEyePosition(), TOOL_REACH + 2.0D),
                player.getEyePosition(),
                player.getLookAngle(),
                TOOL_REACH,
                PIPE_PICK_RADIUS
        );
        PipeAppearanceSavedData appearances = PipeAppearanceSavedData.get(serverLevel);
        ItemStack stack = player.getItemInHand(hand);
        PipeAppearanceProfile draft = Optional.ofNullable(stack.get(SPSDataComponents.PIPE_APPEARANCE_DRAFT.get()))
                .map(PipeAppearanceProfile::normalizedToDefinitions)
                .orElse(null);

        if (target.isPresent() && player.isShiftKeyDown()) {
            PipeConnection connection = target.get().connection();
            PipeAppearanceProfile current = appearances.profileForConnectionKey(connection.connectionKey()).normalizedToDefinitions();
            stack.set(SPSDataComponents.PIPE_APPEARANCE_DRAFT.get(), current.withoutServerId());
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.pipe_appearance_picked").withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS_SERVER;
        }

        if (target.isPresent() && draft != null) {
            PipeConnection connection = target.get().connection();
            PipeAppearanceSavedData.ApplyResult result = appearances.applySingle(pipes, connection.connectionKey(), draft.withoutServerId());
            if (result.accepted()) {
                ServerEvents.broadcastPipeAppearanceDelta(serverLevel);
                player.sendOverlayMessage(Component.translatable("message.superpipeslide.pipe_appearance_brushed").withStyle(ChatFormatting.AQUA));
                return InteractionResult.SUCCESS_SERVER;
            }
            player.sendOverlayMessage(Component.literal(result.message()).withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        openDraftEditor(serverPlayer, appearances, draft == null ? PipeAppearanceProfile.defaultProfile().normalizedToDefinitions() : draft);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void openDraftEditor(ServerPlayer player, PipeAppearanceSavedData appearances, PipeAppearanceProfile draft) {
        PacketDistributor.sendToPlayer(player, appearances.fullSyncPayload());
        PacketDistributor.sendToPlayer(player, new ClientboundOpenPipeAppearanceEditorPayload(
                appearances.revision(),
                PipeConnection.TRANSIENT_CONNECTION_KEY,
                draft,
                draft,
                1.0D
        ));
    }
}
