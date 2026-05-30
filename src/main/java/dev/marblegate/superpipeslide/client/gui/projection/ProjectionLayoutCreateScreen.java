package dev.marblegate.superpipeslide.client.gui.projection;

import dev.marblegate.superpipeslide.client.core.projection.preview.ProjectionLayoutPreviewPainter;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.client.gui.route.RouteEditorGui;
import dev.marblegate.superpipeslide.client.gui.route.RouteEditorScreenBase;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionCanvas;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutDefinition;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutTarget;
import dev.marblegate.superpipeslide.common.core.projection.storage.ProjectionLayoutSavedData;
import dev.marblegate.superpipeslide.common.core.projection.template.ProjectionTemplates;
import dev.marblegate.superpipeslide.network.projection.ClientboundOpenProjectionLayoutDesignerPayload;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class ProjectionLayoutCreateScreen extends RouteEditorScreenBase {
    private static final float CANVAS_SIZE_STEP = 0.05F;

    private final ClientboundOpenProjectionLayoutDesignerPayload payload;
    private EditBox nameBox;
    private EditBox widthBox;
    private EditBox heightBox;
    private float blankWidth = 2.75F;
    private float blankHeight = 0.95F;
    private double templateScroll;
    private SPSGui.Rect templateArea = new SPSGui.Rect(0, 0, 0, 0);

    public ProjectionLayoutCreateScreen(ClientboundOpenProjectionLayoutDesignerPayload payload) {
        super(Component.translatable("screen.superpipeslide.projection_designer.create"));
        this.payload = payload;
        if (payload.activeTarget() == ProjectionLayoutTarget.PLATFORM) {
            this.blankWidth = 3.8F;
            this.blankHeight = 0.9F;
        }
    }

    @Override
    protected SPSGui.Rect createPanelRect() {
        return RouteEditorGui.panelRect(this.width, this.height, 630, 360);
    }

    @Override
    protected void rebuildWidgets() {
        String name = this.nameBox == null ? Component.translatable("screen.superpipeslide.projection_designer.untitled").getString() : this.nameBox.getValue();
        this.clearWidgets();
        this.nameBox = this.borderlessBox(0, 0, 162, name);
        this.nameBox.setMaxLength(ProjectionLayoutDefinition.MAX_NAME_LENGTH);
        this.widthBox = this.borderlessBox(0, 0, 58, formatSize(this.blankWidth));
        this.widthBox.setMaxLength(8);
        this.widthBox.setResponder(value -> parseSize(value).ifPresent(parsed -> {
            this.blankWidth = snapCanvasSize(clampSize(parsed, true));
        }));
        this.heightBox = this.borderlessBox(0, 0, 58, formatSize(this.blankHeight));
        this.heightBox.setMaxLength(8);
        this.heightBox.setResponder(value -> parseSize(value).ifPresent(parsed -> {
            this.blankHeight = snapCanvasSize(clampSize(parsed, false));
        }));
    }

    @Override
    protected void onBack() {
        Minecraft.getInstance().setScreen(new ProjectionLayoutLibraryScreen(this.payload));
    }

    @Override
    public void onClose() {
        this.onBack();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.beginFrame();
        this.drawEditorFrame(graphics);
        this.drawTitle(graphics, Component.translatable("screen.superpipeslide.projection_designer.create"), true, List.of(), mouseX, mouseY);
        this.drawDocumentHeader(graphics, SPSGui.Icon.PLUS,
                List.of(Component.translatable("screen.superpipeslide.projection_designer.library"), Component.translatable("screen.superpipeslide.projection_designer.create")),
                Component.translatable(this.payload.activeTarget().translationKey()), RouteEditorGui.BLUE, 37);
        SPSGui.Rect content = this.editorContent();
        int top = this.documentBodyY();
        int availableHeight = Math.max(0, content.bottom() - top);
        boolean stacked = content.width() < 420 || availableHeight < 190;
        SPSGui.Rect blank;
        SPSGui.Rect templates;
        if (stacked) {
            int blankHeight = availableHeight >= 180 ? Math.min(172, availableHeight - 72) : Math.max(42, Math.min(92, availableHeight / 2));
            blank = new SPSGui.Rect(content.x(), top, content.width(), blankHeight);
            templates = new SPSGui.Rect(content.x(), blank.bottom() + 7, content.width(), Math.max(0, content.bottom() - blank.bottom() - 7));
        } else {
            int blankWidth = Math.min(190, Math.max(154, content.width() / 3));
            blank = new SPSGui.Rect(content.x(), top, blankWidth, availableHeight);
            templates = new SPSGui.Rect(blank.right() + 8, top, Math.max(0, content.right() - blank.right() - 8), availableHeight);
        }
        drawBlank(graphics, blank, mouseX, mouseY);
        drawTemplates(graphics, templates, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.renderTooltips(graphics, mouseX, mouseY);
    }

    private void drawBlank(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        RouteEditorGui.paperSection(graphics, rect, rect.contains(mouseX, mouseY), false);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.blank"), rect.x() + 9, rect.y() + 9, RouteEditorGui.INK_PRIMARY);
        if (rect.height() < 92) {
            hideBox(this.nameBox);
            hideBox(this.widthBox);
            hideBox(this.heightBox);
            SPSGui.Rect create = new SPSGui.Rect(rect.x() + 9, Math.min(rect.bottom() - 22, rect.y() + 25), rect.width() - 18, 18);
            RouteEditorGui.actionButton(graphics, this.font, create, SPSGui.Icon.PLUS, Component.translatable("screen.superpipeslide.projection_designer.start_blank"), false, create.contains(mouseX, mouseY), RouteEditorGui.BLUE);
            this.addClick(create, this::createBlank, Component.translatable("screen.superpipeslide.projection_designer.start_blank"));
            return;
        }
        if (rect.height() >= 150) {
            SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, Component.translatable("screen.superpipeslide.projection_designer.blank_hint").getString(), Math.round((rect.width() - 18) / 0.66F)), rect.x() + 9, rect.y() + 24, RouteEditorGui.INK_MUTED, 0.66F);
        }
        int nameY = rect.y() + (rect.height() >= 150 ? 52 : 28);
        RouteEditorGui.fieldLabel(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.name"), rect.x() + 9, nameY);
        if (this.nameBox != null) {
            this.nameBox.setX(rect.x() + 9);
            this.nameBox.setY(nameY + 12);
            this.nameBox.setWidth(Math.max(44, rect.width() - 18));
            this.drawInputEditableIcon(graphics, this.nameBox);
        }
        int controlsTop = nameY + 42;
        int buttonTop = rect.bottom() - 29;
        if (buttonTop - controlsTop >= 32 && rect.width() >= 178) {
            drawSizeField(graphics, Component.translatable("screen.superpipeslide.projection_designer.canvas_width"), this.widthBox, rect.x() + 9, controlsTop, (rect.width() - 24) / 2, this.blankWidth);
            drawSizeField(graphics, Component.translatable("screen.superpipeslide.projection_designer.canvas_height"), this.heightBox, rect.x() + 15 + (rect.width() - 24) / 2, controlsTop, rect.width() - 24 - (rect.width() - 24) / 2, this.blankHeight);
        } else if (buttonTop - controlsTop >= 64) {
            drawSizeField(graphics, Component.translatable("screen.superpipeslide.projection_designer.canvas_width"), this.widthBox, rect.x() + 9, controlsTop, rect.width() - 18, this.blankWidth);
            drawSizeField(graphics, Component.translatable("screen.superpipeslide.projection_designer.canvas_height"), this.heightBox, rect.x() + 9, controlsTop + 32, rect.width() - 18, this.blankHeight);
        } else {
            hideBox(this.widthBox);
            hideBox(this.heightBox);
        }
        SPSGui.Rect create = new SPSGui.Rect(rect.x() + 9, buttonTop, Math.max(44, rect.width() - 18), 20);
        RouteEditorGui.actionButton(graphics, this.font, create, SPSGui.Icon.PLUS, Component.translatable("screen.superpipeslide.projection_designer.start_blank"), false, create.contains(mouseX, mouseY), RouteEditorGui.BLUE);
        this.addClick(create, this::createBlank, Component.translatable("screen.superpipeslide.projection_designer.start_blank"));
    }

    private void drawTemplates(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            this.templateArea = new SPSGui.Rect(0, 0, 0, 0);
            return;
        }
        RouteEditorGui.worksheetPane(graphics, rect, rect.contains(mouseX, mouseY), false);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.templates"), rect.x() + 9, rect.y() + 9, RouteEditorGui.INK_PRIMARY);
        List<ProjectionLayoutDefinition> entries = ProjectionTemplates.defaultLibrary(this.payload.activeTarget());
        int columns = templateColumns(rect);
        int gap = 5;
        int cardW = Math.max(64, (rect.width() - 18 - gap * (columns - 1)) / columns);
        int cardH = rect.height() < 110 ? 70 : 82;
        this.templateArea = new SPSGui.Rect(rect.x() + 5, rect.y() + 24, rect.width() - 10, rect.height() - 30);
        graphics.enableScissor(this.templateArea.x(), this.templateArea.y(), this.templateArea.right(), this.templateArea.bottom());
        for (int i = 0; i < entries.size(); i++) {
            ProjectionLayoutDefinition template = entries.get(i);
            int x = rect.x() + 9 + (i % columns) * (cardW + gap);
            int y = rect.y() + 27 + (i / columns) * (cardH + gap) - (int) this.templateScroll;
            SPSGui.Rect card = new SPSGui.Rect(x, y, cardW, cardH);
            if (card.bottom() < this.templateArea.y() || card.y() > this.templateArea.bottom()) {
                continue;
            }
            RouteEditorGui.paperSection(graphics, card, card.contains(mouseX, mouseY), false);
            ProjectionLayoutPreviewPainter.draw(graphics, this.font, template, new SPSGui.Rect(card.x() + 4, card.y() + 4, card.width() - 8, Math.max(32, cardH - 25)), false);
            SPSGui.centeredText(graphics, this.font, SPSGui.ellipsize(this.font, template.name(), card.width() - 8), card.x() + card.width() / 2, card.bottom() - 14, RouteEditorGui.INK_SECONDARY);
            this.addClick(card, () -> useTemplate(template), Component.translatable("screen.superpipeslide.projection_designer.create_from_template", template.name()));
        }
        graphics.disableScissor();
        RouteEditorGui.scrollEdges(graphics, this.templateArea, this.templateScroll > 0.5D, this.templateScroll < maxTemplateScroll() - 0.5D, true);
    }

    private void drawSizeField(GuiGraphicsExtractor graphics, Component label, EditBox box, int x, int y, int width, float value) {
        RouteEditorGui.fieldLabel(graphics, this.font, label, x, y);
        if (box == null) {
            return;
        }
        box.setX(x);
        box.setY(y + 13);
        box.setWidth(Math.max(44, width));
        if (!box.isFocused()) {
            box.setValue(formatSize(value));
        }
        this.drawInputEditableIcon(graphics, box);
    }

    private static void hideBox(EditBox box) {
        if (box != null) {
            box.setX(-10000);
            box.setY(-10000);
        }
    }

    private void createBlank() {
        ProjectionLayoutDefinition layout = new ProjectionLayoutDefinition(UUID.randomUUID(), nameValue(), ProjectionLayoutDefinition.CURRENT_SCHEMA_VERSION,
                this.payload.activeTarget(), new ProjectionCanvas(this.blankWidth, this.blankHeight), List.of(), System.currentTimeMillis());
        Minecraft.getInstance().setScreen(new ProjectionLayoutCanvasScreen(this.payload, layout));
    }

    private void useTemplate(ProjectionLayoutDefinition template) {
        ProjectionLayoutDefinition layout = new ProjectionLayoutDefinition(UUID.randomUUID(), template.name(), ProjectionLayoutDefinition.CURRENT_SCHEMA_VERSION,
                template.target(), template.canvas(), ProjectionLayoutSavedData.freshComponents(template.components()), System.currentTimeMillis());
        Minecraft.getInstance().setScreen(new ProjectionLayoutCanvasScreen(this.payload, layout));
    }

    private String nameValue() {
        return this.nameBox == null || this.nameBox.getValue().isBlank() ? Component.translatable("screen.superpipeslide.projection_designer.untitled").getString() : this.nameBox.getValue().trim();
    }

    private static float snapCanvasSize(float value) {
        return Math.round(value / CANVAS_SIZE_STEP) * CANVAS_SIZE_STEP;
    }

    private static float clampSize(float value, boolean width) {
        float min = width ? ProjectionCanvas.MIN_WIDTH : ProjectionCanvas.MIN_HEIGHT;
        float max = width ? ProjectionCanvas.MAX_WIDTH : ProjectionCanvas.MAX_HEIGHT;
        return Math.max(min, Math.min(max, value));
    }

    private static Optional<Float> parseSize(String value) {
        try {
            float parsed = Float.parseFloat(value == null ? "" : value.trim());
            return Float.isFinite(parsed) ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static String formatSize(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.templateArea.contains(mouseX, mouseY)) {
            this.templateScroll = Math.max(0.0D, Math.min(maxTemplateScroll(), this.templateScroll - scrollY * 22.0D));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private double maxTemplateScroll() {
        int columns = templateColumns(new SPSGui.Rect(this.templateArea.x() - 5, this.templateArea.y() - 24, this.templateArea.width() + 10, this.templateArea.height() + 30));
        int rows = (ProjectionTemplates.defaultLibrary(this.payload.activeTarget()).size() + columns - 1) / columns;
        int cardH = this.templateArea.height() < 80 ? 70 : 82;
        return Math.max(0.0D, rows * (cardH + 5) - 5 - this.templateArea.height());
    }

    private static int templateColumns(SPSGui.Rect rect) {
        return rect.width() >= 300 ? 2 : 1;
    }
}
