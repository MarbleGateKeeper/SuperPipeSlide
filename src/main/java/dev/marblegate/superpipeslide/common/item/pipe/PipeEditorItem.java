package dev.marblegate.superpipeslide.common.item.pipe;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * In-world pipe shape editor. All interactions run through the client-side editing
 * session (see ClientPipeEditorSession): right-click an anchor to move its attach point,
 * right-click a pipe to edit its curve nodes, and right-click again to confirm. The item
 * itself has no server-side use behaviour; edits are committed through dedicated payloads.
 */
public class PipeEditorItem extends Item {
    public PipeEditorItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.superpipeslide.pipe_editor.header").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.superpipeslide.pipe_editor.open").withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable("tooltip.superpipeslide.pipe_editor.anchor").withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable("tooltip.superpipeslide.pipe_editor.drag").withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable("tooltip.superpipeslide.pipe_editor.node").withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable("tooltip.superpipeslide.pipe_editor.confirm").withStyle(ChatFormatting.DARK_GRAY));
    }
}
