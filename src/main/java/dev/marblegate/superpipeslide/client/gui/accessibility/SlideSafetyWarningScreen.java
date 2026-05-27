package dev.marblegate.superpipeslide.client.gui.accessibility;

import dev.marblegate.superpipeslide.client.core.accessibility.ClientSafetyOptions;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.client.gui.base.SPSScreen;
import dev.marblegate.superpipeslide.client.gui.route.RouteEditorGui;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class SlideSafetyWarningScreen extends SPSScreen {
    private static final int WARNING_PANEL_WIDTH = 386;
    private static final int WARNING_PANEL_HEIGHT = 218;
    private final Runnable onClosedWithoutCapture;
    private boolean handled;

    public SlideSafetyWarningScreen(Runnable onClosedWithoutCapture) {
        super(Component.translatable("screen.superpipeslide.slide_safety.title"));
        this.onClosedWithoutCapture = onClosedWithoutCapture;
    }

    @Override
    protected SPSGui.Rect createPanelRect() {
        return RouteEditorGui.panelRect(this.width, this.height, WARNING_PANEL_WIDTH, WARNING_PANEL_HEIGHT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.beginFrame();
        RouteEditorGui.clipboard(graphics, this.panel);
        this.drawTitle(graphics, Component.translatable("screen.superpipeslide.slide_safety.title"), false, List.of(), mouseX, mouseY);

        SPSGui.Rect content = RouteEditorGui.contentRect(this.panel);
        int y = content.y() + 5;
        renderWarningBlock(
                graphics,
                new SPSGui.Rect(content.x(), y, content.width(), 48),
                SPSGui.Icon.WARNING,
                Component.translatable("screen.superpipeslide.slide_safety.motion.title"),
                Component.translatable("screen.superpipeslide.slide_safety.motion.body"),
                RouteEditorGui.WARNING
        );
        y += 55;
        renderWarningBlock(
                graphics,
                new SPSGui.Rect(content.x(), y, content.width(), 48),
                SPSGui.Icon.WARNING,
                Component.translatable("screen.superpipeslide.slide_safety.photosensitivity.title"),
                Component.translatable("screen.superpipeslide.slide_safety.photosensitivity.body"),
                RouteEditorGui.DANGER
        );
        y += 56;
        SPSGui.smallText(
                graphics,
                this.font,
                Component.translatable("screen.superpipeslide.slide_safety.settings_hint").getString(),
                content.x() + 4,
                y,
                RouteEditorGui.INK_MUTED,
                0.72F
        );

        int buttonY = content.bottom() - 26;
        int gap = 7;
        int safeWidth = Math.min(154, Math.max(120, content.width() / 3 + 24));
        int continueWidth = Math.min(120, Math.max(92, (content.width() - safeWidth - gap * 2) / 2));
        int closeWidth = content.width() - safeWidth - continueWidth - gap * 2;
        SPSGui.Rect safe = new SPSGui.Rect(content.x(), buttonY, safeWidth, 20);
        SPSGui.Rect current = new SPSGui.Rect(safe.right() + gap, buttonY, continueWidth, 20);
        SPSGui.Rect close = new SPSGui.Rect(current.right() + gap, buttonY, closeWidth, 20);
        RouteEditorGui.actionButton(graphics, this.font, safe, SPSGui.Icon.CONFIRM, Component.translatable("screen.superpipeslide.slide_safety.enable_safe_and_continue"), false, safe.contains(mouseX, mouseY), RouteEditorGui.SUCCESS);
        RouteEditorGui.actionButton(graphics, this.font, current, SPSGui.Icon.INFO, Component.translatable("screen.superpipeslide.slide_safety.continue_current"), false, current.contains(mouseX, mouseY), RouteEditorGui.BLUE);
        RouteEditorGui.actionButton(graphics, this.font, close, SPSGui.Icon.CLOSE, Component.translatable("screen.superpipeslide.slide_safety.close"), false, close.contains(mouseX, mouseY), RouteEditorGui.INK_SECONDARY);
        this.addClick(safe, this::enableSafetyAndContinue, Component.translatable("screen.superpipeslide.slide_safety.enable_safe_and_continue.tooltip"));
        this.addClick(current, this::continueCurrentEffects, Component.translatable("screen.superpipeslide.slide_safety.continue_current.tooltip"));
        this.addClick(close, this::closeWithoutCapture, Component.translatable("screen.superpipeslide.slide_safety.close.tooltip"));
        this.renderTooltips(graphics, mouseX, mouseY);
    }

    private void renderWarningBlock(GuiGraphicsExtractor graphics, SPSGui.Rect rect, SPSGui.Icon icon, Component title, Component body, int color) {
        RouteEditorGui.paperSection(graphics, rect, false, false);
        SPSGui.icon(graphics, new SPSGui.Rect(rect.x() + 7, rect.y() + 7, 16, 16), icon, color);
        SPSGui.text(graphics, this.font, SPSGui.ellipsize(this.font, title.getString(), rect.width() - 36), rect.x() + 28, rect.y() + 9, color);
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, body.getString(), Math.round((rect.width() - 20) / 0.72F)), rect.x() + 9, rect.y() + 28, RouteEditorGui.INK_SECONDARY, 0.72F);
    }

    private void enableSafetyAndContinue() {
        this.handled = true;
        ClientSafetyOptions.setReduceMotionSicknessRisk(true);
        ClientSafetyOptions.setReducePhotosensitivityRisk(true);
        ClientSafetyOptions.acknowledgeSlideSafetyWarning();
        Minecraft.getInstance().setScreen(null);
    }

    private void continueCurrentEffects() {
        this.handled = true;
        ClientSafetyOptions.acknowledgeSlideSafetyWarning();
        Minecraft.getInstance().setScreen(null);
    }

    private void closeWithoutCapture() {
        if (this.handled) {
            return;
        }
        this.handled = true;
        ClientSafetyOptions.acknowledgeSlideSafetyWarning();
        this.onClosedWithoutCapture.run();
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void onClose() {
        this.closeWithoutCapture();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.closeWithoutCapture();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick);
    }
}
