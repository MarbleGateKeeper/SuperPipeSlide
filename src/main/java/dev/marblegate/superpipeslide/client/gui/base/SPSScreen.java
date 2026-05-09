package dev.marblegate.superpipeslide.client.gui.base;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public abstract class SPSScreen extends Screen {
    protected final List<ClickAction> clickActions = new ArrayList<>();
    protected SPSGui.Rect panel = new SPSGui.Rect(0, 0, 0, 0);

    protected SPSScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        this.panel = this.createPanelRect();
        this.rebuildWidgets();
    }

    protected void rebuildWidgets() {
    }

    protected void beginFrame() {
        this.clickActions.clear();
        this.panel = this.createPanelRect();
    }

    protected SPSGui.Rect createPanelRect() {
        return SPSGui.panelRect(this.width, this.height);
    }

    protected void addClick(SPSGui.Rect bounds, Runnable action, Component tooltip) {
        this.clickActions.add(new ClickAction(bounds, action, tooltip));
    }

    protected void addPriorityClick(SPSGui.Rect bounds, Runnable action, Component tooltip) {
        this.clickActions.add(0, new ClickAction(bounds, action, tooltip));
    }

    protected void addClick(SPSGui.Rect bounds, Runnable action, String tooltipKey) {
        this.addClick(bounds, action, Component.translatable(tooltipKey));
    }

    protected void drawTitle(GuiGraphicsExtractor graphics, Component title, boolean hasBack, List<SPSGui.IconButton> actions, int mouseX, int mouseY) {
        SPSGui.titleBar(graphics, this.font, this.panel, title, hasBack, actions, mouseX, mouseY);
        if (hasBack) {
            this.addClick(new SPSGui.Rect(this.panel.x() + 5, this.panel.y() + 3, 16, 16), this::onBack, Component.translatable("screen.superpipeslide.action.back"));
        }
    }

    protected void drawTitle(GuiGraphicsExtractor graphics, String titleKey, boolean hasBack, List<SPSGui.IconButton> actions, int mouseX, int mouseY) {
        this.drawTitle(graphics, Component.translatable(titleKey), hasBack, actions, mouseX, mouseY);
    }

    protected EditBox borderlessBox(int x, int y, int width, String value) {
        EditBox box = new EditBox(this.font, x, y, width, 14, Component.empty());
        box.setBordered(false);
        box.setTextShadow(false);
        box.setTextColor(SPSGui.TEXT_PRIMARY);
        box.setTextColorUneditable(SPSGui.TEXT_DISABLED);
        box.setValue(value);
        this.addRenderableWidget(box);
        return box;
    }

    protected void drawInputEditableIcon(GuiGraphicsExtractor graphics, EditBox box) {
        if (box != null && !box.isFocused()) {
            SPSGui.icon(graphics, new SPSGui.Rect(box.getX() + Math.min(box.getWidth() - 10, this.font.width(box.getValue()) + 3), box.getY(), 10, 10), SPSGui.Icon.EDIT, SPSGui.TEXT_MUTED);
        }
    }

    protected void layoutBoxAfterLabel(EditBox box, Component label, int labelX, int y, int right, int gap) {
        if (box == null) {
            return;
        }
        int inputX = labelX + this.font.width(label) + gap;
        box.setX(inputX);
        box.setY(y + 5);
        box.setWidth(Math.max(24, right - inputX));
    }

    protected void drawSearchPlaceholder(GuiGraphicsExtractor graphics, EditBox box) {
        if (box != null && box.getValue().isEmpty() && !box.isFocused()) {
            SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.search"), box.getX() + 2, box.getY() + 3, SPSGui.TEXT_MUTED);
        }
    }

    protected void onBack() {
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            if (this.dispatchClickActions(this.clickActions, event.x(), event.y())) {
                return true;
            }
        }
        return this.dispatchWidgetMouseClicked(event, doubleClick);
    }

    protected boolean dispatchClickActions(List<ClickAction> actions, double mouseX, double mouseY) {
        for (ClickAction clickAction : List.copyOf(actions)) {
            if (clickAction.bounds().contains(mouseX, mouseY)) {
                this.clearFocus();
                clickAction.action().run();
                return true;
            }
        }
        return false;
    }

    protected boolean dispatchWidgetMouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean handled = super.mouseClicked(event, doubleClick);
        if (!handled && event.button() == 0) {
            this.clearFocus();
        }
        return handled;
    }

    protected void renderTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (ClickAction clickAction : this.clickActions) {
            if (clickAction.bounds().contains(mouseX, mouseY)) {
                graphics.setTooltipForNextFrame(this.font, clickAction.tooltip(), mouseX, mouseY);
                return;
            }
        }
    }

    protected ClickAction clickAction(SPSGui.Rect bounds, Runnable action, Component tooltip) {
        return new ClickAction(bounds, action, tooltip);
    }

    protected record ClickAction(SPSGui.Rect bounds, Runnable action, Component tooltip) {
    }
}
