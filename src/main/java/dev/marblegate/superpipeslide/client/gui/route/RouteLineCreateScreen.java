package dev.marblegate.superpipeslide.client.gui.route;


import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.network.route.ServerboundRouteEditPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class RouteLineCreateScreen extends RouteEditorScreenBase {
    private EditBox nameBox;
    private EditBox translatedBox;
    private final List<Integer> draftColors = new ArrayList<>(List.of(0xE03366FF));
    private int colorPickerIndex = -1;
    private int colorPickerOriginal;
    private boolean colorPickerCreatedColor;
    private SPSGui.Rect colorPickerBounds = new SPSGui.Rect(0, 0, 0, 0);

    public RouteLineCreateScreen() {
        super(Component.translatable("screen.superpipeslide.route_editor.create_line"));
    }

    @Override
    protected SPSGui.Rect createPanelRect() {
        return RouteEditorGui.panelRect(this.width, this.height, 390, 180);
    }

    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();
        SPSGui.Rect content = this.editorContent();
        int left = content.x() + 8;
        int top = content.y() + 22;
        this.nameBox = box(left, top, 150, Component.translatable("screen.superpipeslide.route.default_name").getString());
        this.translatedBox = box(left, top + 26, 150, "");
    }

    private EditBox box(int x, int y, int width, String value) {
        return this.borderlessBox(x, y, width, value);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.beginFrame();
        this.drawEditorFrame(graphics);
        this.drawTitle(graphics, Component.translatable("screen.superpipeslide.route_editor.create_line"), true, List.of(), mouseX, mouseY);

        SPSGui.Rect content = this.editorContent();
        this.drawDocumentHeader(graphics, SPSGui.Icon.ROUTE_LINE, List.of(Component.translatable("screen.superpipeslide.route_editor"), Component.translatable("screen.superpipeslide.route_editor.create_line")), Component.translatable("screen.superpipeslide.status.draft"), RouteEditorGui.INK_MUTED, 31);
        int bodyTop = this.documentBodyY();
        SPSGui.Rect form = new SPSGui.Rect(content.x(), bodyTop, Math.max(160, content.width() - 136), content.bottom() - bodyTop - 26);
        SPSGui.Rect preview = new SPSGui.Rect(form.right() + 8, bodyTop, content.right() - form.right() - 8, content.bottom() - bodyTop - 26);
        RouteEditorGui.paperSection(graphics, form, false, false);
        RouteEditorGui.worksheetPane(graphics, preview, false, false);
        int left = form.x() + 8;
        int top = form.y() + 8;
        Component nameLabel = Component.translatable("screen.superpipeslide.field.name");
        Component namesLabel = Component.translatable("screen.superpipeslide.field.names");
        Component colorsLabel = Component.translatable("screen.superpipeslide.field.colors");
        RouteEditorGui.fieldLabel(graphics, this.font, nameLabel, left, top);
        RouteEditorGui.fieldLabel(graphics, this.font, namesLabel, left, top + 26);
        RouteEditorGui.fieldLabel(graphics, this.font, colorsLabel, left, top + 55);
        this.nameBox.setX(left);
        this.nameBox.setY(top + 10);
        this.nameBox.setWidth(form.width() - 18);
        this.translatedBox.setX(left);
        this.translatedBox.setY(top + 36);
        this.translatedBox.setWidth(form.width() - 18);
        this.drawInputEditableIcon(graphics, this.nameBox);
        this.drawInputEditableIcon(graphics, this.translatedBox);

        int swatchX = left;
        int swatchY = top + 66;
        for (int i = 0; i < this.draftColors.size(); i++) {
            SPSGui.Rect swatch = new SPSGui.Rect(swatchX + i * 24, swatchY, 18, 18);
            RouteEditorGui.colorSwatch(graphics, swatch, this.draftColors.get(i), this.colorPickerIndex == i);
            int index = i;
            this.addClick(swatch, () -> this.openColorPicker(index, false), Component.translatable("screen.superpipeslide.field.color", index + 1));
        }
        if (this.draftColors.size() < 3) {
            SPSGui.Rect add = new SPSGui.Rect(swatchX + this.draftColors.size() * 24, swatchY, 18, 18);
            RouteEditorGui.emptyColorSwatch(graphics, add, add.contains(mouseX, mouseY));
            this.addClick(add, () -> {
                this.draftColors.add(0xE03366FF);
                this.openColorPicker(this.draftColors.size() - 1, true);
            }, Component.translatable("screen.superpipeslide.field.colors"));
        }

        List<Integer> colors = parseColors();
        RouteLine draft = new RouteLine(java.util.UUID.randomUUID(), this.nameBox.getValue(), splitCsv(this.translatedBox.getValue()), colors, List.of(), true);
        RouteEditorGui.routeStripe(graphics, preview.x(), preview.y(), preview.height(), draft.themeColors());
        RouteEditorGui.nameBlock(graphics, this.font, draft.displayName(), draft.translatedNames(), preview.x() + 10, preview.y() + 8, preview.width() - 20, draft.displayName().hashCode());
        if (draft.translatedNames().isEmpty()) {
            SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.route.create_preview_summary").getString(), preview.x() + 10, preview.y() + 30, RouteEditorGui.INK_SECONDARY, 0.72F);
        }
        SPSGui.stationMap(graphics, this.font, List.of(
                new SPSGui.StationNode("A", List.of(), false, dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus.VALID),
                new SPSGui.StationNode("B", List.of(), false, dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus.VALID),
                new SPSGui.StationNode("C", List.of(), false, dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus.VALID)
        ), colors, new SPSGui.Rect(preview.x() + 10, preview.y() + 49, preview.width() - 20, 30), false, 0, true);

        SPSGui.Rect cancel = new SPSGui.Rect(content.right() - 82, content.bottom() - 18, 34, 18);
        SPSGui.Rect save = new SPSGui.Rect(content.right() - 42, content.bottom() - 18, 34, 18);
        RouteEditorGui.toolButton(graphics, new SPSGui.Rect(cancel.x(), cancel.y() + 1, 16, 16), SPSGui.Icon.BACK, false, cancel.contains(mouseX, mouseY), RouteEditorGui.INK_SECONDARY);
        RouteEditorGui.toolButton(graphics, new SPSGui.Rect(save.x(), save.y() + 1, 16, 16), SPSGui.Icon.SAVE, !canSave(), save.contains(mouseX, mouseY), RouteEditorGui.BLUE);
        this.addClick(cancel, () -> this.minecraft.setScreen(new RouteLineListScreen()), Component.translatable("screen.superpipeslide.action.back"));
        if (canSave()) {
            this.addClick(save, this::save, Component.translatable("screen.superpipeslide.action.create_route"));
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        renderColorPicker(graphics, mouseX, mouseY);
        this.renderTooltips(graphics, mouseX, mouseY);
    }

    private boolean canSave() {
        return !this.nameBox.getValue().trim().isEmpty() && !parseColors().isEmpty();
    }

    private void save() {
        ClientPacketDistributor.sendToServer(new ServerboundRouteEditPayload(ServerboundRouteEditPayload.CREATE_LINE, ClientRouteDataCache.revision(), Optional.empty(), this.nameBox.getValue(), splitCsv(this.translatedBox.getValue()), parseColors(), List.of(), false, false));
        this.minecraft.setScreen(new RouteLineListScreen());
    }

    @Override
    protected void onBack() {
        this.minecraft.setScreen(new RouteLineListScreen());
    }

    private List<Integer> parseColors() {
        return List.copyOf(this.draftColors);
    }

    private void renderColorPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.colorPickerIndex < 0 || this.colorPickerIndex >= this.draftColors.size()) {
            return;
        }
        this.colorPickerBounds = new SPSGui.Rect(this.panel.right() - 190, this.panel.y() + 32, 178, 134);
        RouteEditorGui.colorPicker(graphics, this.colorPickerBounds, this.draftColors.get(this.colorPickerIndex), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
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

    static Optional<Integer> parseColor(String value) {
        String trimmed = value.trim().replace("#", "");
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (trimmed.length() == 8) {
                trimmed = trimmed.substring(2);
            }
            if (trimmed.length() != 6) {
                return Optional.empty();
            }
            return Optional.of(0xFF000000 | Integer.parseUnsignedInt(trimmed, 16));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }    public static List<String> splitCsv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .limit(1)
                .toList();
    }
}
