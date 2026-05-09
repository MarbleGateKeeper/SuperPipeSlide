package dev.marblegate.superpipeslide.client.gui.route;


import dev.marblegate.superpipeslide.client.gui.base.RouteDataAwareScreen;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.network.route.ServerboundRouteEditPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class RouteLayoutCreateScreen extends RouteEditorScreenBase implements RouteDataAwareScreen {
    private final UUID routeLineId;
    private EditBox nameBox;
    private EditBox translatedBox;
    private boolean bidirectional = true;
    private boolean loop = false;
    private boolean nameAsSectionName = false;

    public RouteLayoutCreateScreen(UUID routeLineId) {
        super(Component.translatable("screen.superpipeslide.route_editor.add_layout"));
        this.routeLineId = routeLineId;
    }

    @Override
    protected SPSGui.Rect createPanelRect() {
        return RouteEditorGui.panelRect(this.width, this.height, 360, 190);
    }

    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();
        SPSGui.Rect content = this.editorContent();
        int left = content.x() + 10;
        int top = content.y() + 20;
        this.nameBox = box(left, top, content.width() - 20, "");
        this.translatedBox = box(left, top + 26, content.width() - 20, "");
    }

    private EditBox box(int x, int y, int width, String value) {
        return this.borderlessBox(x, y, width, value);
    }

    @Override
    public void refreshFromRouteSnapshot() {
        if (this.nameBox == null || (!this.nameBox.isFocused() && !this.translatedBox.isFocused())) {
            this.rebuildWidgets();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.beginFrame();
        this.drawEditorFrame(graphics);
        Optional<RouteLine> line = ClientRouteDataCache.routeLine(this.routeLineId);
        if (line.isEmpty()) {
            this.drawTitle(graphics, Component.translatable("screen.superpipeslide.route.missing"), true, List.of(), mouseX, mouseY);
            SPSGui.centeredText(graphics, this.font, Component.translatable("screen.superpipeslide.route.missing.body"), this.panel.x() + this.panel.width() / 2, this.panel.y() + 92, RouteEditorGui.INK_MUTED);
            this.renderTooltips(graphics, mouseX, mouseY);
            return;
        }
        this.drawTitle(graphics, Component.translatable("screen.superpipeslide.layout.create.title", line.get().displayName()), true, List.of(), mouseX, mouseY);

        SPSGui.Rect content = this.editorContent();
        this.drawDocumentHeader(graphics, SPSGui.Icon.LAYOUT, List.of(Component.literal(line.get().displayName()), Component.translatable("screen.superpipeslide.route_editor.add_layout")), Component.translatable("screen.superpipeslide.status.draft"), RouteEditorGui.INK_MUTED, this.routeLineId.hashCode());
        int bodyTop = this.documentBodyY();
        SPSGui.Rect form = new SPSGui.Rect(content.x(), bodyTop, content.width(), content.bottom() - bodyTop - 24);
        RouteEditorGui.paperSection(graphics, form, false, false);
        int left = form.x() + 10;
        int top = form.y() + 8;
        Component nameLabel = Component.translatable("screen.superpipeslide.field.name");
        Component namesLabel = Component.translatable("screen.superpipeslide.field.names");
        RouteEditorGui.fieldLabel(graphics, this.font, nameLabel, left, top);
        RouteEditorGui.fieldLabel(graphics, this.font, namesLabel, left, top + 28);
        this.nameBox.setX(left);
        this.nameBox.setY(top + 10);
        this.nameBox.setWidth(form.width() - 20);
        this.translatedBox.setX(left);
        this.translatedBox.setY(top + 38);
        this.translatedBox.setWidth(form.width() - 20);
        this.drawInputEditableIcon(graphics, this.nameBox);
        this.drawInputEditableIcon(graphics, this.translatedBox);
        SPSGui.Rect bidirectionalBox = new SPSGui.Rect(left, top + 62, 12, 12);
        RouteEditorGui.checkbox(graphics, bidirectionalBox, this.bidirectional, bidirectionalBox.contains(mouseX, mouseY));
        Component bidirectionalLabel = Component.translatable("screen.superpipeslide.layout.bidirectional");
        SPSGui.text(graphics, this.font, bidirectionalLabel, bidirectionalBox.right() + 6, top + 64, RouteEditorGui.INK_SECONDARY);
        this.addClick(new SPSGui.Rect(left, top + 60, 18 + this.font.width(bidirectionalLabel), 16), () -> this.bidirectional = !this.bidirectional, bidirectionalLabel);

        SPSGui.Rect loopBox = new SPSGui.Rect(left, top + 81, 12, 12);
        RouteEditorGui.checkbox(graphics, loopBox, this.loop, loopBox.contains(mouseX, mouseY));
        Component loopLabel = Component.translatable("screen.superpipeslide.layout.loop");
        SPSGui.text(graphics, this.font, loopLabel, loopBox.right() + 6, top + 83, RouteEditorGui.INK_SECONDARY);
        this.addClick(new SPSGui.Rect(left, top + 79, 18 + this.font.width(loopLabel), 16), () -> this.loop = !this.loop, loopLabel);

        SPSGui.Rect visibleBox = new SPSGui.Rect(left, top + 100, 12, 12);
        RouteEditorGui.checkbox(graphics, visibleBox, this.nameAsSectionName, visibleBox.contains(mouseX, mouseY));
        Component visibleLabel = Component.translatable("screen.superpipeslide.layout.name_as_section_name");
        SPSGui.text(graphics, this.font, visibleLabel, visibleBox.right() + 6, top + 102, RouteEditorGui.INK_SECONDARY);
        this.addClick(new SPSGui.Rect(left, top + 98, 18 + this.font.width(visibleLabel), 16), () -> this.nameAsSectionName = !this.nameAsSectionName, visibleLabel);

        SPSGui.Rect save = new SPSGui.Rect(content.right() - 26, content.bottom() - 18, 16, 16);
        RouteEditorGui.toolButton(graphics, save, SPSGui.Icon.SAVE, !canSave(), save.contains(mouseX, mouseY), RouteEditorGui.BLUE);
        this.addClick(save, this::save, Component.translatable("screen.superpipeslide.action.create_layout"));
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.renderTooltips(graphics, mouseX, mouseY);
    }

    private boolean canSave() {
        return this.nameBox != null;
    }

    private String layoutName() {
        return this.nameBox == null ? "" : this.nameBox.getValue().trim();
    }

    private void save() {
        ClientPacketDistributor.sendToServer(new ServerboundRouteEditPayload(ServerboundRouteEditPayload.CREATE_LAYOUT, ClientRouteDataCache.revision(), Optional.of(this.routeLineId), layoutName(), RouteLineCreateScreen.splitCsv(this.translatedBox.getValue()), List.of(), List.of(), this.bidirectional, this.loop, this.nameAsSectionName));
        this.minecraft.setScreen(new RouteLineScreen(this.routeLineId, false));
    }

    @Override
    protected void onBack() {
        this.minecraft.setScreen(new RouteLineScreen(this.routeLineId, false));
    }
}
