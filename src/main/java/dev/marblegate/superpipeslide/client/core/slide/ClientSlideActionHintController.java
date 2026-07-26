package dev.marblegate.superpipeslide.client.core.slide;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Persistent in-slide action hint shown above the hotbar: it mirrors
 * ClientSlideController.slideJumpHint() so the player always sees what SPACE (and SNEAK)
 * will do before pressing it, including the press-again detach confirmation state while
 * riding a route mid-station.
 */
public final class ClientSlideActionHintController {
    private static final int MAIN_COLOR = 0xFFFFFFFF;
    private static final int ARMED_COLOR = 0xFFFFD85A;
    private static final int SUB_COLOR = 0xFF9A9A9A;
    private static final int HOTBAR_OFFSET = 59;

    private ClientSlideActionHintController() {}

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null || minecraft.options.hideGui
                || ClientCinematicCameraController.hidesFirstPersonHud()) {
            return;
        }
        ClientSlideController.slideJumpHint().ifPresent(hint -> {
            Font font = minecraft.font;
            Component main = mainText(hint);
            Component sub = Component.translatable("hud.superpipeslide.slide_action.sneak_hint");
            int centerX = graphics.guiWidth() / 2;
            int y = graphics.guiHeight() - HOTBAR_OFFSET - font.lineHeight * 2 - 4;
            int mainColor = hint.armed() ? ARMED_COLOR : MAIN_COLOR;
            graphics.text(font, main, centerX - font.width(main) / 2, y, mainColor, true);
            graphics.text(font, sub, centerX - font.width(sub) / 2, y + font.lineHeight + 2, SUB_COLOR, true);
        });
    }

    private static Component mainText(ClientSlideController.SlideJumpHint hint) {
        if (hint.armed()) {
            return Component.translatable("hud.superpipeslide.slide_action.detach_armed");
        }
        return switch (hint.action()) {
            case DETACH -> hint.onRoute()
                    ? Component.translatable("hud.superpipeslide.slide_action.get_off")
                    : Component.translatable("hud.superpipeslide.slide_action.detach");
            case REVERSE -> Component.translatable("hud.superpipeslide.slide_action.u_turn");
            case PIPE_JUMP -> Component.translatable("hud.superpipeslide.slide_action.pipe_jump");
            case ROUTE_DETACH_CONFIRM -> Component.translatable("hud.superpipeslide.slide_action.leave_route");
        };
    }
}
