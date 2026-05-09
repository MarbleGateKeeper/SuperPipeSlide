package dev.marblegate.superpipeslide.client.gui.route;


import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.client.gui.base.SPSScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public abstract class RouteEditorScreenBase extends SPSScreen {
    private Component documentTooltip;
    private int documentTooltipX;
    private int documentTooltipY;
    private Component confirmationTitle;
    private Component confirmationBody;
    private Component confirmationAccept;
    private Runnable confirmationAction;

    protected RouteEditorScreenBase(Component title) {
        super(title);
    }

    @Override
    protected void beginFrame() {
        super.beginFrame();
        this.documentTooltip = null;
    }

    @Override
    protected SPSGui.Rect createPanelRect() {
        return RouteEditorGui.panelRect(this.width, this.height, 360, 224);
    }

    protected void drawEditorFrame(GuiGraphicsExtractor graphics) {
        RouteEditorGui.clipboard(graphics, this.panel);
    }

    protected SPSGui.Rect editorContent() {
        return RouteEditorGui.contentRect(this.panel);
    }

    protected void drawDocumentHeader(GuiGraphicsExtractor graphics, SPSGui.Icon icon, List<Component> path, Component stamp, int stampColor, int seed) {
        RouteEditorGui.documentHeader(graphics, this.font, this.editorContent(), icon, path, java.util.Optional.ofNullable(stamp), stampColor, seed);
    }

    protected int documentBodyY() {
        return RouteEditorGui.documentBodyY(this.editorContent());
    }

    @Override
    protected void drawTitle(GuiGraphicsExtractor graphics, Component title, boolean hasBack, List<SPSGui.IconButton> actions, int mouseX, int mouseY) {
        RouteEditorGui.titleBar(graphics, this.font, this.panel, title, hasBack, actions, mouseX, mouseY);
        if (hasBack) {
            this.addClick(RouteEditorGui.titleBackBounds(this.panel), this::onBack, Component.translatable("screen.superpipeslide.action.back"));
        }
    }

    @Override
    protected EditBox borderlessBox(int x, int y, int width, String value) {
        EditBox box = super.borderlessBox(x, y, width, value);
        box.setTextColor(RouteEditorGui.INK_PRIMARY);
        box.setTextColorUneditable(RouteEditorGui.INK_DISABLED);
        return box;
    }

    @Override
    protected void drawInputEditableIcon(GuiGraphicsExtractor graphics, EditBox box) {
        if (box != null && !box.isFocused()) {
            SPSGui.icon(graphics, new SPSGui.Rect(box.getX() + Math.min(box.getWidth() - 10, this.font.width(box.getValue()) + 3), box.getY(), 10, 10), SPSGui.Icon.EDIT, RouteEditorGui.INK_MUTED);
        }
        if (box != null) {
            int y = box.getY() + 12;
            graphics.fill(box.getX(), y, box.getX() + box.getWidth(), y + 1, box.isFocused() ? RouteEditorGui.BLUE : RouteEditorGui.PAPER_LINE);
        }
    }

    @Override
    protected void drawSearchPlaceholder(GuiGraphicsExtractor graphics, EditBox box) {
        if (box != null && box.getValue().isEmpty() && !box.isFocused()) {
            SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.search"), box.getX() + 2, box.getY() + 3, RouteEditorGui.INK_MUTED);
        }
    }

    @Override
    protected void renderTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.confirmationAction != null) {
            this.renderConfirmation(graphics, mouseX, mouseY);
            return;
        }
        if (this.documentTooltip != null) {
            RouteEditorGui.documentTooltip(graphics, this.font, this.documentTooltip, this.documentTooltipX, this.documentTooltipY, this.panel);
            return;
        }
        for (ClickAction clickAction : this.clickActions) {
            if (clickAction.bounds().contains(mouseX, mouseY)) {
                RouteEditorGui.documentTooltip(graphics, this.font, clickAction.tooltip(), mouseX, mouseY, this.panel);
                return;
            }
        }
    }

    protected void showDocumentTooltip(Component tooltip, int mouseX, int mouseY) {
        this.documentTooltip = tooltip;
        this.documentTooltipX = mouseX;
        this.documentTooltipY = mouseY;
    }

    protected void requestDangerConfirmation(Component title, Component body, Component accept, Runnable action) {
        this.confirmationTitle = title;
        this.confirmationBody = body;
        this.confirmationAccept = accept;
        this.confirmationAction = action;
    }

    protected boolean confirmationOpen() {
        return this.confirmationAction != null;
    }

    private void clearConfirmation() {
        this.confirmationTitle = null;
        this.confirmationBody = null;
        this.confirmationAccept = null;
        this.confirmationAction = null;
    }

    private SPSGui.Rect confirmationBounds() {
        int width = Math.min(268, Math.max(216, this.panel.width() - 56));
        int height = 82;
        return new SPSGui.Rect(this.panel.x() + (this.panel.width() - width) / 2, this.panel.y() + (this.panel.height() - height) / 2 + 7, width, height);
    }

    private SPSGui.Rect confirmationCancelBounds(SPSGui.Rect card) {
        return new SPSGui.Rect(card.right() - 172, card.bottom() - 25, 56, 18);
    }

    private SPSGui.Rect confirmationAcceptBounds(SPSGui.Rect card) {
        return new SPSGui.Rect(card.right() - 110, card.bottom() - 25, 100, 18);
    }

    private void renderConfirmation(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        SPSGui.Rect card = this.confirmationBounds();
        graphics.fill(this.panel.x() + 4, this.panel.y() + 22, this.panel.right() - 4, this.panel.bottom() - 4, 0x66000000);
        RouteEditorGui.paperSection(graphics, card, false, true);
        String title = this.confirmationTitle == null ? "" : this.confirmationTitle.getString();
        String body = this.confirmationBody == null ? "" : this.confirmationBody.getString();
        SPSGui.icon(graphics, new SPSGui.Rect(card.x() + 8, card.y() + 8, 16, 16), SPSGui.Icon.WARNING, RouteEditorGui.DANGER);
        SPSGui.text(graphics, this.font, SPSGui.ellipsize(this.font, title, card.width() - 36), card.x() + 28, card.y() + 11, RouteEditorGui.DANGER);
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, body, Math.round((card.width() - 18) / 0.72F)), card.x() + 9, card.y() + 31, RouteEditorGui.INK_SECONDARY, 0.72F);
        SPSGui.Rect cancel = this.confirmationCancelBounds(card);
        SPSGui.Rect accept = this.confirmationAcceptBounds(card);
        RouteEditorGui.actionButton(graphics, this.font, cancel, SPSGui.Icon.BACK, Component.translatable("screen.superpipeslide.action.cancel"), false, cancel.contains(mouseX, mouseY), RouteEditorGui.INK_SECONDARY);
        RouteEditorGui.actionButton(graphics, this.font, accept, SPSGui.Icon.REMOVE, this.confirmationAccept == null ? Component.empty() : this.confirmationAccept, false, accept.contains(mouseX, mouseY), RouteEditorGui.DANGER);
        if (accept.contains(mouseX, mouseY) && this.confirmationAccept != null) {
            RouteEditorGui.documentTooltip(graphics, this.font, this.confirmationAccept, mouseX, mouseY, this.panel);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && this.confirmationAction != null) {
            SPSGui.Rect card = this.confirmationBounds();
            if (this.confirmationAcceptBounds(card).contains(event.x(), event.y())) {
                Runnable action = this.confirmationAction;
                this.clearConfirmation();
                action.run();
                return true;
            }
            this.clearConfirmation();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
