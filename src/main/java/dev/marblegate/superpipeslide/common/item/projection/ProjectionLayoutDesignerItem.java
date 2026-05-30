package dev.marblegate.superpipeslide.common.item.projection;

import dev.marblegate.superpipeslide.common.block.station.PlatformProjectorBlockEntity;
import dev.marblegate.superpipeslide.common.block.station.StationNameProjectorBlockEntity;
import dev.marblegate.superpipeslide.common.core.projection.layout.AppliedProjectionLayout;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutDefinition;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutTarget;
import dev.marblegate.superpipeslide.common.core.projection.storage.ProjectionLayoutSavedData;
import dev.marblegate.superpipeslide.common.registry.SPSDataComponents;
import dev.marblegate.superpipeslide.network.projection.ClientboundOpenProjectionLayoutDesignerPayload;
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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public class ProjectionLayoutDesignerItem extends Item {
    public ProjectionLayoutDesignerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.superpipeslide.projection_layout_designer.use").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.superpipeslide.projection_layout_designer.apply").withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable("tooltip.superpipeslide.projection_layout_designer.read").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            openDesigner(serverPlayer, player.getItemInHand(hand), false);
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        TargetProjector projector = targetProjector(serverLevel, context.getClickedPos());
        if (projector == null) {
            return InteractionResult.PASS;
        }

        ProjectionLayoutSavedData library = ProjectionLayoutSavedData.get(serverLevel.getServer());
        ItemStack stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            Optional<ProjectionLayoutDefinition> copy = projector.appliedLayout().editableCopy();
            if (copy.isEmpty()) {
                player.sendOverlayMessage(Component.translatable("message.superpipeslide.projection_layout_read_failed").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            ProjectionLayoutDefinition saved = library.saveCopy(player.getUUID(), copy.get(), projector.appliedLayout().sourceLayoutName() + " Copy", true);
            stack.set(SPSDataComponents.PROJECTION_LAYOUT_SELECTED.get(), saved.id());
            stack.set(SPSDataComponents.PROJECTION_LAYOUT_ACTIVE_TARGET.get(), saved.target());
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.projection_layout_read").withStyle(ChatFormatting.AQUA));
            openDesigner(player, stack, saved.target(), true);
            return InteractionResult.SUCCESS_SERVER;
        }

        ProjectionLayoutTarget target = projector.target();
        UUID selectedId = selectedLayoutId(player, stack, library, target).orElse(null);
        if (selectedId == null) {
            openDesigner(player, stack, target, false);
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.projection_layout_select_first").withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS_SERVER;
        }
        ProjectionLayoutDefinition layout = library.layout(player.getUUID(), selectedId).orElse(null);
        if (layout == null || !ProjectionLayoutSavedData.isValid(layout)) {
            openDesigner(player, stack, target, false);
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.projection_layout_invalid").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (layout.target() != target) {
            openDesigner(player, stack, target, false);
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.projection_layout_target_mismatch").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        AppliedProjectionLayout applied = layout.compile(player.getName().getString());
        projector.applyLayout().accept(applied);
        stack.set(SPSDataComponents.PROJECTION_LAYOUT_SELECTED.get(), layout.id());
        stack.set(SPSDataComponents.PROJECTION_LAYOUT_ACTIVE_TARGET.get(), target);
        player.sendOverlayMessage(Component.translatable("message.superpipeslide.projection_layout_applied", layout.name()).withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void openDesigner(ServerPlayer player, ItemStack stack, boolean editSelected) {
        openDesigner(player, stack, activeTarget(stack), editSelected);
    }

    private static void openDesigner(ServerPlayer player, ItemStack stack, ProjectionLayoutTarget activeTarget, boolean editSelected) {
        ProjectionLayoutSavedData library = ProjectionLayoutSavedData.get(player.level().getServer());
        ProjectionLayoutTarget target = activeTarget == null ? ProjectionLayoutTarget.STATION_NAME : activeTarget;
        UUID selected = selectedLayoutId(player, stack, library, target).orElse(null);
        if (selected != null) {
            library.select(player.getUUID(), target, selected);
            stack.set(SPSDataComponents.PROJECTION_LAYOUT_SELECTED.get(), selected);
        } else {
            stack.remove(SPSDataComponents.PROJECTION_LAYOUT_SELECTED.get());
        }
        stack.set(SPSDataComponents.PROJECTION_LAYOUT_ACTIVE_TARGET.get(), target);
        PacketDistributor.sendToPlayer(player, new ClientboundOpenProjectionLayoutDesignerPayload(target, library.selectedLayoutIds(player.getUUID()), library.summaries(player.getUUID()), editSelected));
    }

    private static Optional<UUID> selectedLayoutId(ServerPlayer player, ItemStack stack, ProjectionLayoutSavedData library, ProjectionLayoutTarget target) {
        ProjectionLayoutTarget normalizedTarget = target == null ? ProjectionLayoutTarget.STATION_NAME : target;
        Optional<UUID> itemSelection = Optional.ofNullable(stack.get(SPSDataComponents.PROJECTION_LAYOUT_SELECTED.get()))
                .filter(id -> library.layout(player.getUUID(), id)
                        .filter(ProjectionLayoutSavedData::isValid)
                        .filter(layout -> layout.target() == normalizedTarget)
                        .isPresent());
        return itemSelection.or(() -> library.selectedLayoutId(player.getUUID(), normalizedTarget));
    }

    private static ProjectionLayoutTarget activeTarget(ItemStack stack) {
        ProjectionLayoutTarget target = stack.get(SPSDataComponents.PROJECTION_LAYOUT_ACTIVE_TARGET.get());
        return target == null ? ProjectionLayoutTarget.STATION_NAME : target;
    }

    private static TargetProjector targetProjector(ServerLevel level, net.minecraft.core.BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof StationNameProjectorBlockEntity projector) {
            return new TargetProjector(ProjectionLayoutTarget.STATION_NAME, projector.appliedLayout(), projector::applyLayout);
        }
        if (level.getBlockEntity(pos) instanceof PlatformProjectorBlockEntity projector) {
            return new TargetProjector(ProjectionLayoutTarget.PLATFORM, projector.appliedLayout(), projector::applyLayout);
        }
        return null;
    }

    private record TargetProjector(ProjectionLayoutTarget target, AppliedProjectionLayout appliedLayout, java.util.function.Consumer<AppliedProjectionLayout> applyLayout) {}
}
