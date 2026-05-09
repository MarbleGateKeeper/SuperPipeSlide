package dev.marblegate.superpipeslide.client.gui.route;

import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.gui.base.RouteDataAwareScreen;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.network.route.ServerboundRouteEditPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class RouteLineScreen extends RouteEditorScreenBase implements RouteDataAwareScreen {
    private final UUID routeLineId;
    private final boolean focusName;
    private EditBox nameBox;
    private EditBox translatedBox;
    private final List<Integer> draftColors = new ArrayList<>();
    private int colorPickerIndex = -1;
    private int colorPickerOriginal;
    private boolean colorPickerCreatedColor;
    private SPSGui.Rect colorPickerBounds = new SPSGui.Rect(0, 0, 0, 0);
    private double scroll;

    public RouteLineScreen(UUID routeLineId, boolean focusName) {
        super(Component.translatable("screen.superpipeslide.route"));
        this.routeLineId = routeLineId;
        this.focusName = focusName;
    }

    @Override
    protected SPSGui.Rect createPanelRect() {
        return RouteEditorGui.panelRect(this.width, this.height, 390, 238);
    }

    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();
        Optional<RouteLine> line = ClientRouteDataCache.routeLine(this.routeLineId);
        if (line.isEmpty()) {
            return;
        }
        RouteLine routeLine = line.get();
        int left = this.panel.x() + 24;
        int top = this.panel.y() + 48;
        this.nameBox = box(left, top, 150, routeLine.displayName());
        this.translatedBox = box(left, top + 23, 150, String.join(", ", routeLine.translatedNames()));
        this.translatedBox.setTextColor(RouteEditorGui.INK_SECONDARY);
        if (this.draftColors.isEmpty()) {
            this.draftColors.addAll(normalizeColors(routeLine.themeColors()));
        }
        if (this.focusName) {
            this.setInitialFocus(this.nameBox);
        }
    }

    @Override
    public void refreshFromRouteSnapshot() {
        if (this.nameBox == null || (!this.nameBox.isFocused() && !this.translatedBox.isFocused() && this.colorPickerIndex < 0)) {
            this.draftColors.clear();
            this.rebuildWidgets();
        }
    }

    private EditBox box(int x, int y, int width, String value) {
        return this.borderlessBox(x, y, width, value);
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
        RouteLine routeLine = line.get();
        SPSGui.Rect save = RouteEditorGui.titleActionBounds(this.panel, 0);
        SPSGui.Rect delete = RouteEditorGui.titleActionBounds(this.panel, 1);
        boolean saveActive = canSave() && isDirty(routeLine);
        String routeStatus = SPSGui.lineStatus(routeLine);
        Component routeStatusLabel = Component.translatable("screen.superpipeslide.status." + routeStatus.toLowerCase(java.util.Locale.ROOT));
        this.drawTitle(graphics, Component.translatable("screen.superpipeslide.route.title", routeLine.displayName()), true, List.of(new SPSGui.IconButton(SPSGui.Icon.REMOVE, RouteEditorGui.DANGER, false), new SPSGui.IconButton(SPSGui.Icon.SAVE, RouteEditorGui.BLUE, !saveActive)), mouseX, mouseY);
        this.drawDocumentHeader(graphics, SPSGui.Icon.ROUTE_LINE, List.of(Component.translatable("screen.superpipeslide.route"), Component.literal(routeLine.displayName())), saveActive ? Component.translatable("screen.superpipeslide.editor.unsaved") : routeStatusLabel, saveActive ? RouteEditorGui.SAVE : RouteEditorGui.statusColor(routeStatus), routeLine.id().hashCode());
        if (saveActive) {
            this.addClick(save, this::save, Component.translatable("screen.superpipeslide.action.save_route"));
        }
        this.addClick(delete, () -> this.requestDangerConfirmation(
                Component.translatable("screen.superpipeslide.confirm.delete_route.title"),
                Component.translatable("screen.superpipeslide.confirm.delete_route.body"),
                Component.translatable("screen.superpipeslide.action.delete_route"),
                this::deleteRoute
        ), Component.translatable("screen.superpipeslide.action.delete_route"));

        this.scroll = Math.max(0.0D, Math.min(maxScroll(), this.scroll));
        SPSGui.Rect content = this.editorContent();
        int bodyTop = this.documentBodyY();
        int contentTop = bodyTop - (int) this.scroll;
        positionInputs(contentTop);
        graphics.enableScissor(content.x(), bodyTop, content.right(), content.bottom());
        renderInfo(graphics, routeLine, contentTop, mouseX, mouseY);
        renderLayouts(graphics, routeLine, contentTop + 90, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();
        SPSGui.Rect scrollArea = new SPSGui.Rect(content.x(), bodyTop, content.width(), content.bottom() - bodyTop);
        RouteEditorGui.scrollEdges(graphics, scrollArea, this.scroll > 0.5D, this.scroll < maxScroll() - 0.5D, false);
        RouteEditorGui.thinScrollbar(graphics, scrollArea, this.scroll, maxScroll(), scrollArea.contains(mouseX, mouseY));
        renderColorPicker(graphics, mouseX, mouseY);
        this.renderTooltips(graphics, mouseX, mouseY);
    }

    private void positionInputs(int contentTop) {
        if (this.nameBox == null) {
            return;
        }
        int left = this.panel.x() + 24;
        int top = contentTop + 17;
        this.nameBox.setX(left);
        this.nameBox.setY(top);
        this.nameBox.setWidth(this.panel.width() - 156);
        this.translatedBox.setX(left);
        this.translatedBox.setY(top + 23);
        this.translatedBox.setWidth(this.panel.width() - 156);
    }

    private void renderInfo(GuiGraphicsExtractor graphics, RouteLine line, int y, int mouseX, int mouseY) {
        SPSGui.Rect content = this.editorContent();
        SPSGui.Rect info = new SPSGui.Rect(content.x(), y, content.width(), 82);
        RouteEditorGui.paperSection(graphics, info, false, false);
        RouteEditorGui.routeStripe(graphics, info.x(), info.y(), info.height(), this.draftColors);
        RouteEditorGui.fieldLabel(graphics, this.font, Component.translatable("screen.superpipeslide.field.name"), info.x() + 10, info.y() + 6);
        RouteEditorGui.fieldLabel(graphics, this.font, Component.translatable("screen.superpipeslide.field.names"), info.x() + 10, info.y() + 29);
        RouteEditorGui.fieldLabel(graphics, this.font, Component.translatable("screen.superpipeslide.field.colors"), info.right() - 85, info.y() + 6);
        this.drawInputEditableIcon(graphics, this.nameBox);
        this.drawInputEditableIcon(graphics, this.translatedBox);

        SPSGui.LineSummary lineSummary = SPSGui.lineSummary(line);
        String summary = Component.translatable("screen.superpipeslide.route.summary_no_issues", lineSummary.layoutCount(), lineSummary.stationCount()).getString();
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, summary, Math.round((info.width() - 22) / 0.72F)), info.x() + 10, info.y() + 58, RouteEditorGui.INK_SECONDARY, 0.72F);
        int problems = lineSummary.problemCount();
        if (problems > 0) {
            SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.route.issues", problems).getString(), info.x() + 10, info.y() + 69, RouteEditorGui.WARNING, 0.72F);
        }

        int colorY = info.y() + 21;
        int swatchRight = info.right() - 9;
        for (int i = 0; i < this.draftColors.size(); i++) {
            SPSGui.Rect swatch = new SPSGui.Rect(swatchRight - 18 - i * 22, colorY, 18, 18);
            RouteEditorGui.colorSwatch(graphics, swatch, this.draftColors.get(i), this.colorPickerIndex == i);
            int index = i;
            this.addClick(swatch, () -> this.openColorPicker(index, false), Component.translatable("screen.superpipeslide.field.color", index + 1));
        }
        if (this.draftColors.size() < 3) {
            SPSGui.Rect add = new SPSGui.Rect(swatchRight - 18 - this.draftColors.size() * 22, colorY, 18, 18);
            RouteEditorGui.emptyColorSwatch(graphics, add, add.contains(mouseX, mouseY));
            this.addClick(add, () -> {
                this.draftColors.add(defaultNewColor());
                this.openColorPicker(this.draftColors.size() - 1, true);
            }, Component.translatable("screen.superpipeslide.field.colors"));
        }
    }

    private void renderLayouts(GuiGraphicsExtractor graphics, RouteLine line, int y, int mouseX, int mouseY) {
        SPSGui.Rect content = this.editorContent();
        RouteEditorGui.sectionTitle(graphics, this.font, Component.translatable("screen.superpipeslide.layouts"), content.x(), y);
        SPSGui.Rect add = new SPSGui.Rect(this.panel.right() - 30, y - 4, 16, 16);
        RouteEditorGui.toolButton(graphics, add, SPSGui.Icon.PLUS, false, add.contains(mouseX, mouseY), RouteEditorGui.BLUE);
        this.addClick(add, () -> this.minecraft.setScreen(new RouteLayoutCreateScreen(line.id())), Component.translatable("screen.superpipeslide.action.create_layout"));

        int cardY = y + 16;
        List<RouteLayout> layouts = ClientRouteDataCache.routeLayoutsForLine(line.id()).stream()
                .sorted(Comparator.comparing((RouteLayout layout) -> "Draft".equals(SPSGui.layoutStatus(layout))).thenComparing(SPSGui::layoutName))
                .toList();
        if (layouts.isEmpty()) {
            SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.layout.none"), content.x() + 4, cardY + 10, RouteEditorGui.INK_MUTED);
            return;
        }
        int width = content.width();
        for (RouteLayout layout : layouts) {
            int cardHeight = layoutCardHeight(layout);
            SPSGui.Rect card = new SPSGui.Rect(content.x(), cardY, width, cardHeight);
            renderLayoutCard(graphics, line, layout, card, mouseX, mouseY);
            cardY += cardHeight + 5;
        }
    }

    private void renderLayoutCard(GuiGraphicsExtractor graphics, RouteLine line, RouteLayout layout, SPSGui.Rect card, int mouseX, int mouseY) {
        boolean hovered = card.contains(mouseX, mouseY);
        RouteEditorGui.paperSection(graphics, card, hovered, false);
        RouteEditorGui.routeStripe(graphics, card.x(), card.y(), card.height(), line.themeColors());
        String status = SPSGui.layoutStatus(layout);
        RouteEditorGui.nameBlock(graphics, this.font, SPSGui.layoutName(layout), layout.translatedNames(), card.x() + 10, card.y() + 5, card.width() - 106, layout.id().hashCode());
        Component statusLabel = Component.translatable("screen.superpipeslide.status." + status.toLowerCase(java.util.Locale.ROOT));
        RouteEditorGui.stamp(graphics, this.font, statusLabel.getString(), card.right() - this.font.width(statusLabel.getString()) - 35, card.y() + 4, RouteEditorGui.statusColor(status));
        SPSGui.Rect edit = new SPSGui.Rect(card.right() - 20, card.y() + 3, 16, 16);
        RouteEditorGui.toolButton(graphics, edit, SPSGui.Icon.EDIT, false, edit.contains(mouseX, mouseY), RouteEditorGui.INK_SECONDARY);
        this.addClick(edit, () -> this.minecraft.setScreen(new RouteLayoutScreen(layout.id())), Component.translatable("screen.superpipeslide.action.edit_layout"));
        this.addClick(new SPSGui.Rect(card.x(), card.y(), card.width() - 22, card.height()), () -> this.minecraft.setScreen(new RouteLayoutScreen(layout.id())), Component.translatable("screen.superpipeslide.action.open_layout"));
        int mapY = card.y() + 5 + RouteEditorGui.nameBlockHeight(layout.translatedNames()) + 4;
        int mapHeight = Math.max(22, card.bottom() - mapY - 4);
        SPSGui.stationMap(graphics, this.font, layout, line.themeColors(), new SPSGui.Rect(card.x() + 10, mapY, card.width() - 20, mapHeight), false, layout.id().hashCode(), true);
    }

    private boolean canSave() {
        return this.nameBox != null && !this.nameBox.getValue().trim().isEmpty() && !parseColors().isEmpty();
    }

    private boolean isDirty(RouteLine line) {
        return !line.displayName().equals(this.nameBox.getValue())
                || !line.translatedNames().equals(RouteLineCreateScreen.splitCsv(this.translatedBox.getValue()))
                || !line.themeColors().equals(parseColors());
    }

    private void save() {
        ClientPacketDistributor.sendToServer(new ServerboundRouteEditPayload(ServerboundRouteEditPayload.UPDATE_LINE, ClientRouteDataCache.revision(), Optional.of(this.routeLineId), this.nameBox.getValue(), RouteLineCreateScreen.splitCsv(this.translatedBox.getValue()), parseColors(), List.of(), false, false));
    }

    private void deleteRoute() {
        ClientPacketDistributor.sendToServer(new ServerboundRouteEditPayload(ServerboundRouteEditPayload.DELETE_LINE, ClientRouteDataCache.revision(), Optional.of(this.routeLineId), "", List.of(), List.of(), List.of(), false, false));
        this.minecraft.setScreen(new RouteLineListScreen());
    }

    @Override
    protected void onBack() {
        this.minecraft.setScreen(new RouteLineListScreen());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.panel.contains(mouseX, mouseY)) {
            this.scroll = Math.max(0.0D, Math.min(maxScroll(), this.scroll - scrollY * 14.0D));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private double maxScroll() {
        Optional<RouteLine> line = ClientRouteDataCache.routeLine(this.routeLineId);
        int layoutHeight = line.map(value -> ClientRouteDataCache.routeLayoutsForLine(value.id()).stream()
                .mapToInt(layout -> layoutCardHeight(layout) + 5)
                .sum()).orElse(0);
        int contentHeight = 98 + Math.max(18, layoutHeight);
        int visibleHeight = this.editorContent().height() - 20;
        return Math.max(0, contentHeight - visibleHeight);
    }

    private static int layoutCardHeight(RouteLayout layout) {
        return SPSGui.translatedNamesLine(layout.translatedNames()).isEmpty() ? 52 : 62;
    }

    private List<Integer> parseColors() {
        return List.copyOf(this.draftColors);
    }

    private void renderColorPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.colorPickerIndex < 0 || this.colorPickerIndex >= this.draftColors.size()) {
            return;
        }
        this.colorPickerBounds = new SPSGui.Rect(this.panel.right() - 190, this.panel.y() + 44, 178, 134);
        RouteEditorGui.colorPicker(graphics, this.colorPickerBounds, this.draftColors.get(this.colorPickerIndex), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.confirmationOpen()) {
            return super.mouseClicked(event, doubleClick);
        }
        if (event.button() == 0 && this.colorPickerIndex >= 0) {
            if (SPSGui.colorPickerClose(this.colorPickerBounds).contains(event.x(), event.y())) {
                this.cancelColorPicker();
                return true;
            }
            if (SPSGui.colorPickerField(this.colorPickerBounds).contains(event.x(), event.y())) {
                this.draftColors.set(this.colorPickerIndex, SPSGui.colorFromPicker(this.colorPickerBounds, this.draftColors.get(this.colorPickerIndex), event.x(), event.y()));
                return true;
            }
            if (!this.colorPickerBounds.contains(event.x(), event.y())) {
                this.colorPickerIndex = -1;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == 0 && this.colorPickerIndex >= 0 && SPSGui.colorPickerField(this.colorPickerBounds).contains(event.x(), event.y())) {
            this.draftColors.set(this.colorPickerIndex, SPSGui.colorFromPicker(this.colorPickerBounds, this.draftColors.get(this.colorPickerIndex), event.x(), event.y()));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    private void openColorPicker(int index, boolean createdColor) {
        this.colorPickerIndex = index;
        this.colorPickerOriginal = this.draftColors.get(index);
        this.colorPickerCreatedColor = createdColor;
    }

    private void cancelColorPicker() {
        if (this.colorPickerIndex >= 0 && this.colorPickerIndex < this.draftColors.size()) {
            if (this.colorPickerCreatedColor) {
                this.draftColors.remove(this.colorPickerIndex);
            } else {
                this.draftColors.set(this.colorPickerIndex, this.colorPickerOriginal);
            }
        }
        this.colorPickerIndex = -1;
        this.colorPickerCreatedColor = false;
    }

    private static List<Integer> normalizeColors(List<Integer> colors) {
        List<Integer> normalized = colors.stream().limit(3).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (normalized.isEmpty()) {
            normalized.add(defaultNewColor());
        }
        return normalized;
    }

    private static int defaultNewColor() {
        return 0xE03366FF;
    }

    private static int directionGroupCount(RouteLine line) {
        return SPSGui.lineSummary(line).layoutCount();
    }
}
