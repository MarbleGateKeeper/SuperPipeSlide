package dev.marblegate.superpipeslide.client.gui.projection;


import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.render.SmoothGuiPrimitives;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.client.gui.route.RouteEditorGui;
import dev.marblegate.superpipeslide.client.gui.route.RouteEditorScreenBase;
import dev.marblegate.superpipeslide.client.core.projection.preview.ProjectionLayoutPreviewPainter;
import dev.marblegate.superpipeslide.client.core.projection.cache.ProjectionBuiltinIconTextureCache;
import dev.marblegate.superpipeslide.client.core.projection.cache.ProjectionNetworkImageCache;
import dev.marblegate.superpipeslide.client.core.projection.preview.ProjectionPreviewScenario;
import dev.marblegate.superpipeslide.client.renderer.projection.PlatformProjectorRenderer;
import dev.marblegate.superpipeslide.client.renderer.projection.StationNameProjectorRenderer;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionCanvas;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionBuiltinIcon;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponent;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponentDescriptors;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponentSettings;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponentType;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionImageLayout;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutDefinition;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutTarget;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionOverflowMode;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionTextAlign;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionVisibleCondition;
import dev.marblegate.superpipeslide.network.projection.ClientboundOpenProjectionLayoutDesignerPayload;
import dev.marblegate.superpipeslide.network.projection.ServerboundProjectionLayoutSavePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class ProjectionLayoutCanvasScreen extends RouteEditorScreenBase {
    private static final int BG = 0xFF141A1E;
    private static final int CHROME = 0xFF20292E;
    private static final int CHROME_HIGH = 0xFF2A353B;
    private static final int STAGE = 0xFF0E1215;
    private static final int GRID = 0x2637C3BB;
    private static final int TEXT = 0xFFDCE7E6;
    private static final int MUTED = 0xFF829492;
    private static final int ACCENT = 0xFF37C3BB;
    private static final int ACTIVE = 0xFF273D3D;
    private static final int DANGER = 0xFFD6685E;
    private static final int HANDLE = 6;
    private static final float CANVAS_SIZE_STEP = 0.05F;

    private final List<LayeredClickAction> layeredClickActions = new ArrayList<>();
    private ClientboundOpenProjectionLayoutDesignerPayload libraryPayload;
    private ProjectionLayoutDefinition draft;
    private UUID selectedId;
    private EditBox nameBox;
    private EditBox componentTextBox;
    private EditBox networkUrlBox;
    private EditBox previewExitBox;
    private EditBox canvasWidthBox;
    private EditBox canvasHeightBox;
    private UUID textBindingId;
    private UUID networkUrlBindingId;
    private ProjectionPreviewScenario scenario = ProjectionPreviewScenario.standard();
    private SPSGui.Rect toolbar = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect stage = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect doc = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect layerPanel = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect layerListArea = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect addMenuBounds = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect canvasSettingsBounds = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect scenarioPopoverBounds = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect propertiesBounds = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect propertiesHeaderBounds = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect iconPickerBounds = new SPSGui.Rect(0, 0, 0, 0);
    private boolean layersOpen = true;
    private boolean previewOpen;
    private boolean canvasSettingsOpen;
    private boolean propertiesOpen;
    private boolean addMenuOpen;
    private boolean iconPickerOpen;
    private boolean propertiesPositionInitialized;
    private boolean viewportInitialized;
    private boolean dirty;
    private boolean savePending;
    private long pendingSaveUpdatedAt;
    private ColorTarget colorTarget;
    private SPSGui.Rect colorPickerBounds = new SPSGui.Rect(0, 0, 0, 0);
    private double layerScroll;
    private double addMenuScroll;
    private double propertiesScroll;
    private double iconPickerScroll;
    private double panX;
    private double panY;
    private float zoom = 180.0F;
    private DragMode dragMode = DragMode.NONE;
    private ProjectionComponent dragStart;
    private double dragMouseX;
    private double dragMouseY;
    private double propertiesX;
    private double propertiesY;
    private UUID draggingLayerId;
    private int draggingLayerTarget = -1;
    private ClickLayer clickLayer = ClickLayer.BASE;
    private EditBox activeFloatingTextBox;
    private EditBox draggingFloatingTextBox;

    public ProjectionLayoutCanvasScreen(ClientboundOpenProjectionLayoutDesignerPayload payload, ProjectionLayoutDefinition draft) {
        super(Component.translatable("screen.superpipeslide.projection_designer.canvas"));
        this.libraryPayload = payload;
        this.draft = draft;
        this.selectedId = draft.components().stream().max(Comparator.comparingInt(ProjectionComponent::layer)).map(ProjectionComponent::id).orElse(null);
        this.dirty = payload.layouts().stream().noneMatch(summary -> summary.id().equals(draft.id()));
    }

    public void acceptLibraryPayload(ClientboundOpenProjectionLayoutDesignerPayload payload) {
        this.libraryPayload = payload;
        if (!this.savePending) {
            return;
        }
        Optional<ProjectionLayoutDefinition> saved = payload.layouts().stream()
                .filter(summary -> summary.id().equals(this.draft.id()) && !summary.invalid())
                .map(summary -> summary.preview())
                .filter(preview -> preview != null && preview.updatedAt() >= this.pendingSaveUpdatedAt)
                .findFirst();
        if (saved.isPresent()) {
            this.draft = saved.get();
            if (this.selectedId != null && this.draft.components().stream().noneMatch(component -> component.id().equals(this.selectedId))) {
                this.selectedId = this.draft.components().stream().max(Comparator.comparingInt(ProjectionComponent::layer)).map(ProjectionComponent::id).orElse(null);
            }
            this.dirty = false;
        }
        this.savePending = false;
        this.pendingSaveUpdatedAt = 0L;
    }

    @Override
    protected SPSGui.Rect createPanelRect() {
        return new SPSGui.Rect(5, 5, Math.max(120, this.width - 10), Math.max(100, this.height - 10));
    }

    @Override
    protected void rebuildWidgets() {
        String name = this.nameBox == null ? this.draft.name() : this.nameBox.getValue();
        this.clearWidgets();
        this.nameBox = new EditBox(this.font, 0, 0, 160, 15, Component.empty());
        this.nameBox.setBordered(false);
        this.nameBox.setTextShadow(false);
        this.nameBox.setMaxLength(ProjectionLayoutDefinition.MAX_NAME_LENGTH);
        this.nameBox.setTextColor(TEXT);
        this.nameBox.setTextColorUneditable(MUTED);
        this.nameBox.setValue(name);
        this.nameBox.setResponder(value -> {
            if (!this.draft.name().equals(value == null ? "" : value.trim())) {
                this.draft = this.draft.renamed(value);
                markDirty();
            }
        });
        this.addRenderableWidget(this.nameBox);
        this.componentTextBox = new EditBox(this.font, 0, 0, 130, 15, Component.empty());
        this.componentTextBox.setBordered(false);
        this.componentTextBox.setTextShadow(false);
        this.componentTextBox.setMaxLength(ProjectionComponent.MAX_TEXT_LENGTH);
        this.componentTextBox.setTextColor(TEXT);
        this.componentTextBox.setTextColorUneditable(MUTED);
        this.componentTextBox.visible = false;
        this.componentTextBox.active = false;
        this.componentTextBox.setResponder(this::updateSelectedText);
        this.addRenderableWidget(this.componentTextBox);
        this.networkUrlBox = new EditBox(this.font, 0, 0, 130, 15, Component.empty());
        this.networkUrlBox.setBordered(false);
        this.networkUrlBox.setTextShadow(false);
        this.networkUrlBox.setMaxLength(ProjectionComponentSettings.MAX_URL_LENGTH);
        this.networkUrlBox.setTextColor(TEXT);
        this.networkUrlBox.setTextColorUneditable(MUTED);
        this.networkUrlBox.visible = false;
        this.networkUrlBox.active = false;
        this.networkUrlBox.setResponder(this::updateSelectedNetworkUrl);
        this.addRenderableWidget(this.networkUrlBox);
        this.previewExitBox = new EditBox(this.font, 0, 0, 52, 15, Component.empty());
        this.previewExitBox.setBordered(false);
        this.previewExitBox.setTextShadow(false);
        this.previewExitBox.setMaxLength(12);
        this.previewExitBox.setTextColor(TEXT);
        this.previewExitBox.setTextColorUneditable(MUTED);
        this.previewExitBox.setValue(this.scenario.exitLabel());
        this.previewExitBox.setResponder(value -> this.scenario = this.scenario.withExitLabel(value));
        this.previewExitBox.visible = false;
        this.previewExitBox.active = false;
        this.addRenderableWidget(this.previewExitBox);
        this.canvasWidthBox = new EditBox(this.font, 0, 0, 54, 15, Component.empty());
        this.canvasWidthBox.setBordered(false);
        this.canvasWidthBox.setTextShadow(false);
        this.canvasWidthBox.setMaxLength(8);
        this.canvasWidthBox.setTextColor(TEXT);
        this.canvasWidthBox.setTextColorUneditable(MUTED);
        this.canvasWidthBox.setValue(formatSize(this.draft.canvas().width()));
        this.canvasWidthBox.setResponder(value -> applyCanvasBoxValue(true, value));
        this.canvasWidthBox.visible = false;
        this.canvasWidthBox.active = false;
        this.addRenderableWidget(this.canvasWidthBox);
        this.canvasHeightBox = new EditBox(this.font, 0, 0, 54, 15, Component.empty());
        this.canvasHeightBox.setBordered(false);
        this.canvasHeightBox.setTextShadow(false);
        this.canvasHeightBox.setMaxLength(8);
        this.canvasHeightBox.setTextColor(TEXT);
        this.canvasHeightBox.setTextColorUneditable(MUTED);
        this.canvasHeightBox.setValue(formatSize(this.draft.canvas().height()));
        this.canvasHeightBox.setResponder(value -> applyCanvasBoxValue(false, value));
        this.canvasHeightBox.visible = false;
        this.canvasHeightBox.active = false;
        this.addRenderableWidget(this.canvasHeightBox);
    }

    @Override
    protected void onBack() {
        if (this.dirty) {
            this.requestDangerConfirmation(Component.translatable("screen.superpipeslide.projection_designer.unsaved.title"),
                    Component.translatable("screen.superpipeslide.projection_designer.unsaved.body"),
                    Component.translatable("screen.superpipeslide.projection_designer.unsaved.discard"),
                    this::returnToLibrary);
            return;
        }
        returnToLibrary();
    }

    private void returnToLibrary() {
        Minecraft.getInstance().setScreen(new ProjectionLayoutLibraryScreen(this.libraryPayload));
    }

    @Override
    public void onClose() {
        this.onBack();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.beginFrame();
        this.layeredClickActions.clear();
        this.clickLayer = ClickLayer.BASE;
        graphics.fill(this.panel.x(), this.panel.y(), this.panel.right(), this.panel.bottom(), BG);
        graphics.outline(this.panel.x(), this.panel.y(), this.panel.width(), this.panel.height(), 0xFF314147);
        drawToolbar(graphics, mouseX, mouseY);
        drawWorkspace(graphics, mouseX, mouseY);
        if (this.layersOpen) {
            drawLayers(graphics, mouseX, mouseY);
        }
        hideFloatingWidgetsForBaseRender();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (this.canvasSettingsOpen) {
            this.clickLayer = ClickLayer.CANVAS_SETTINGS;
            drawCanvasSettings(graphics, mouseX, mouseY);
        } else {
            hideCanvasSettingsWidgets();
        }
        if (this.previewOpen) {
            this.clickLayer = ClickLayer.SCENARIO;
            drawScenarioPopover(graphics, mouseX, mouseY);
        } else {
            hideScenarioWidgets();
        }
        if (this.propertiesOpen && selected() != null) {
            this.clickLayer = ClickLayer.PROPERTIES;
            drawProperties(graphics, mouseX, mouseY);
        } else {
            this.propertiesBounds = new SPSGui.Rect(0, 0, 0, 0);
            this.propertiesHeaderBounds = new SPSGui.Rect(0, 0, 0, 0);
            this.iconPickerOpen = false;
            this.iconPickerBounds = new SPSGui.Rect(0, 0, 0, 0);
            hideComponentTextBox();
            hideNetworkUrlBox();
            this.colorTarget = null;
        }
        renderFloatingTextBoxes(graphics, mouseX, mouseY, partialTick);
        if (this.iconPickerOpen && this.propertiesOpen && selected() != null) {
            this.clickLayer = ClickLayer.ICON_PICKER;
            drawIconPicker(graphics, mouseX, mouseY);
        } else {
            this.iconPickerBounds = new SPSGui.Rect(0, 0, 0, 0);
        }
        if (this.colorTarget != null && selected() != null) {
            this.clickLayer = ClickLayer.COLOR_PICKER;
            drawColorPicker(graphics, mouseX, mouseY);
        }
        this.clickLayer = ClickLayer.BASE;
        this.renderTooltips(graphics, mouseX, mouseY);
    }

    private void drawToolbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.toolbar = new SPSGui.Rect(this.panel.x() + 1, this.panel.y() + 1, this.panel.width() - 2, 43);
        graphics.fill(this.toolbar.x(), this.toolbar.y(), this.toolbar.right(), this.toolbar.bottom(), CHROME);
        graphics.fill(this.toolbar.x(), this.toolbar.bottom() - 1, this.toolbar.right(), this.toolbar.bottom(), 0xFF324248);
        SPSGui.Rect back = new SPSGui.Rect(this.toolbar.x() + 7, this.toolbar.y() + 7, 20, 20);
        darkIcon(graphics, back, SPSGui.Icon.BACK, mouseX, mouseY, MUTED);
        this.addClick(back, this::onBack, Component.translatable("screen.superpipeslide.action.back"));
        SPSGui.icon(graphics, new SPSGui.Rect(back.right() + 10, back.y() + 2, 16, 16), SPSGui.Icon.LAYOUT, ACCENT);

        if (this.nameBox != null) {
            this.nameBox.visible = true;
            this.nameBox.active = true;
            this.nameBox.setX(back.right() + 32);
            this.nameBox.setY(this.toolbar.y() + 9);
            this.nameBox.setWidth(Math.min(180, Math.max(100, this.toolbar.width() / 5)));
            graphics.fill(this.nameBox.getX(), this.nameBox.getY() + 13, this.nameBox.getX() + this.nameBox.getWidth(), this.nameBox.getY() + 14, this.nameBox.isFocused() ? ACCENT : 0xFF425158);
        }
        int controlsX = this.nameBox == null ? this.toolbar.x() + 180 : this.nameBox.getX() + this.nameBox.getWidth() + 16;
        drawSizeControl(graphics, controlsX, mouseX, mouseY);

        int right = this.toolbar.right() - 8;
        SPSGui.Rect save = new SPSGui.Rect(right - 21, this.toolbar.y() + 7, 20, 20);
        SPSGui.Rect layers = new SPSGui.Rect(save.x() - 25, save.y(), 20, 20);
        SPSGui.Rect scenarioButton = new SPSGui.Rect(layers.x() - 25, save.y(), 20, 20);
        SPSGui.Rect fit = new SPSGui.Rect(scenarioButton.x() - 25, save.y(), 20, 20);
        darkIcon(graphics, save, SPSGui.Icon.SAVE, mouseX, mouseY, draftValid() && !this.savePending ? 0xFFFFB04A : MUTED);
        darkIcon(graphics, layers, SPSGui.Icon.LAYERS, mouseX, mouseY, this.layersOpen ? ACCENT : MUTED);
        darkIcon(graphics, scenarioButton, SPSGui.Icon.CAMERA_TILTED, mouseX, mouseY, this.previewOpen ? ACCENT : MUTED);
        darkIcon(graphics, fit, SPSGui.Icon.FIT, mouseX, mouseY, MUTED);
        if (draftValid() && !this.savePending) {
            this.addClick(save, this::save, Component.translatable("screen.superpipeslide.action.save"));
        } else {
            Component hint = this.savePending
                    ? Component.translatable("screen.superpipeslide.projection_designer.saving")
                    : Component.translatable("screen.superpipeslide.projection_designer.invalid_empty");
            if (save.contains(mouseX, mouseY)) {
                this.showDocumentTooltip(hint, mouseX, mouseY);
            }
        }
        this.addClick(layers, () -> {
            this.layersOpen = !this.layersOpen;
            if (!this.layersOpen) {
                this.addMenuOpen = false;
                this.addMenuBounds = new SPSGui.Rect(0, 0, 0, 0);
            }
        }, Component.translatable("screen.superpipeslide.projection_designer.layers"));
        this.addClick(scenarioButton, () -> this.previewOpen = !this.previewOpen, Component.translatable("screen.superpipeslide.projection_designer.preview_scenario"));
        this.addClick(fit, this::fitCanvas, Component.translatable("screen.superpipeslide.projection_designer.fit"));
        String zoomLabel = Math.round(this.zoom) + "px/u";
        int zoomWidth = SPSGui.scaledWidth(this.font, zoomLabel, 0.72F);
        int zoomX = fit.x() - zoomWidth - 9;
        SPSGui.smallText(graphics, this.font, zoomLabel, zoomX, fit.y() + 7, MUTED, 0.72F);
        String stateLabel = this.savePending
                ? Component.translatable("screen.superpipeslide.projection_designer.saving").getString()
                : this.dirty ? Component.translatable("screen.superpipeslide.projection_designer.unsaved").getString() : "";
        if (!stateLabel.isBlank()) {
            float stateScale = 0.66F;
            int stateRight = zoomX - 8;
            int stateLeft = controlsX + 122;
            int stateWidth = stateRight - stateLeft;
            if (stateWidth >= 26) {
                String clipped = SPSGui.ellipsize(this.font, stateLabel, Math.round(stateWidth / stateScale));
                int x = stateRight - SPSGui.scaledWidth(this.font, clipped, stateScale);
                SPSGui.smallText(graphics, this.font, clipped, x, save.y() + 7, this.savePending ? MUTED : DANGER, stateScale);
            }
        }
    }

    private void hideFloatingWidgetsForBaseRender() {
        hideForBaseRender(this.canvasWidthBox);
        hideForBaseRender(this.canvasHeightBox);
        hideForBaseRender(this.previewExitBox);
        hideForBaseRender(this.componentTextBox);
        hideForBaseRender(this.networkUrlBox);
    }

    private static void hideForBaseRender(EditBox box) {
        if (box != null) {
            box.visible = false;
            box.active = false;
        }
    }

    private void renderFloatingTextBoxes(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        renderFloatingTextBox(graphics, this.canvasWidthBox, mouseX, mouseY, partialTick);
        renderFloatingTextBox(graphics, this.canvasHeightBox, mouseX, mouseY, partialTick);
        renderFloatingTextBox(graphics, this.previewExitBox, mouseX, mouseY, partialTick);
        renderFloatingTextBox(graphics, this.componentTextBox, mouseX, mouseY, partialTick);
        renderFloatingTextBox(graphics, this.networkUrlBox, mouseX, mouseY, partialTick);
    }

    private static void renderFloatingTextBox(GuiGraphicsExtractor graphics, EditBox box, int mouseX, int mouseY, float partialTick) {
        if (box != null && box.visible) {
            box.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void drawSizeControl(GuiGraphicsExtractor graphics, int x, int mouseX, int mouseY) {
        String size = String.format(java.util.Locale.ROOT, "%.2f x %.2f", this.draft.canvas().width(), this.draft.canvas().height());
        SPSGui.Rect button = new SPSGui.Rect(x, this.toolbar.y() + 8, 112, 20);
        graphics.fill(button.x(), button.y(), button.right(), button.bottom(), this.canvasSettingsOpen ? ACTIVE : button.contains(mouseX, mouseY) ? CHROME_HIGH : BG);
        graphics.outline(button.x(), button.y(), button.width(), button.height(), this.canvasSettingsOpen ? ACCENT : 0xFF324248);
        SPSGui.icon(graphics, new SPSGui.Rect(button.x() + 5, button.y() + 3, 14, 14), SPSGui.Icon.SETTINGS, this.canvasSettingsOpen ? ACCENT : MUTED);
        int textX = button.x() + 24;
        int textWidth = button.width() - 28;
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, Component.translatable("screen.superpipeslide.projection_designer.canvas_size.short").getString(), textWidth), textX, button.y() + 3, MUTED, 0.58F);
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, size, textWidth), textX, button.y() + 11, TEXT, 0.62F);
        this.addClick(button, () -> {
            this.canvasSettingsOpen = !this.canvasSettingsOpen;
            if (this.canvasSettingsOpen) {
                syncCanvasSizeBoxes();
            }
        }, Component.translatable("screen.superpipeslide.projection_designer.canvas_settings"));
    }

    private void drawCanvasSettings(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int popW = 206;
        int popH = 88;
        int popX = Math.max(this.panel.x() + 8, Math.min(this.panel.right() - popW - 8, this.nameBox == null ? this.toolbar.x() + 170 : this.nameBox.getX() + this.nameBox.getWidth() + 12));
        int popY = this.toolbar.bottom() + 4;
        this.canvasSettingsBounds = new SPSGui.Rect(popX, popY, popW, Math.min(popH, this.panel.bottom() - popY - 8));
        graphics.fill(this.canvasSettingsBounds.x(), this.canvasSettingsBounds.y(), this.canvasSettingsBounds.right(), this.canvasSettingsBounds.bottom(), 0xF0182226);
        graphics.outline(this.canvasSettingsBounds.x(), this.canvasSettingsBounds.y(), this.canvasSettingsBounds.width(), this.canvasSettingsBounds.height(), 0xFF3E555A);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.canvas_settings"), popX + 9, popY + 8, TEXT);

        int y = popY + 31;
        RouteEditorGui.fieldLabel(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.canvas_width"), popX + 9, y);
        RouteEditorGui.fieldLabel(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.canvas_height"), popX + 108, y);
        if (this.canvasWidthBox != null) {
            this.canvasWidthBox.visible = true;
            this.canvasWidthBox.active = true;
            this.canvasWidthBox.setX(popX + 9);
            this.canvasWidthBox.setY(y + 12);
            this.canvasWidthBox.setWidth(72);
            this.drawInputEditableIcon(graphics, this.canvasWidthBox);
        }
        if (this.canvasHeightBox != null) {
            this.canvasHeightBox.visible = true;
            this.canvasHeightBox.active = true;
            this.canvasHeightBox.setX(popX + 108);
            this.canvasHeightBox.setY(y + 12);
            this.canvasHeightBox.setWidth(72);
            this.drawInputEditableIcon(graphics, this.canvasHeightBox);
        }
    }

    private void drawWorkspace(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int leftTools = 8;
        int right = this.layersOpen ? 164 : 8;
        this.stage = new SPSGui.Rect(this.panel.x() + leftTools, this.toolbar.bottom() + 6, this.panel.width() - leftTools - right, this.panel.bottom() - this.toolbar.bottom() - 12);
        if (!this.viewportInitialized) {
            fitCanvas();
            this.viewportInitialized = true;
        }
        graphics.fill(this.stage.x(), this.stage.y(), this.stage.right(), this.stage.bottom(), STAGE);
        graphics.outline(this.stage.x(), this.stage.y(), this.stage.width(), this.stage.height(), 0xFF273339);
        this.doc = canvasRect();
        graphics.enableScissor(this.stage.x(), this.stage.y(), this.stage.right(), this.stage.bottom());
        drawStageGrid(graphics);
        graphics.fill(this.doc.x() - 2, this.doc.y() - 2, this.doc.right() + 2, this.doc.bottom() + 2, 0xAA000000);
        ProjectionLayoutPreviewPainter.drawExact(graphics, this.font, this.draft, this.doc, this.scenario);
        graphics.outline(this.doc.x(), this.doc.y(), this.doc.width(), this.doc.height(), 0xFF42555B);
        drawSelection(graphics, mouseX, mouseY);
        graphics.disableScissor();
        if (this.draft.components().isEmpty()) {
            SPSGui.centeredText(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.canvas_empty"), this.stage.x() + this.stage.width() / 2, this.stage.y() + this.stage.height() / 2 - 10, TEXT);
            SPSGui.centeredText(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.canvas_empty_hint"), this.stage.x() + this.stage.width() / 2, this.stage.y() + this.stage.height() / 2 + 6, MUTED);
        }
    }

    private void drawStageGrid(GuiGraphicsExtractor graphics) {
        int step = Math.max(12, Math.round(this.zoom * 0.25F));
        int originX = this.stage.x() + Math.floorMod((int) Math.round(this.panX), step);
        int originY = this.stage.y() + Math.floorMod((int) Math.round(this.panY), step);
        for (int x = originX; x < this.stage.right(); x += step) {
            graphics.fill(x, this.stage.y(), x + 1, this.stage.bottom(), GRID);
        }
        for (int y = originY; y < this.stage.bottom(); y += step) {
            graphics.fill(this.stage.x(), y, this.stage.right(), y + 1, GRID);
        }
    }

    private void drawSelection(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ProjectionComponent component = selected();
        if (component == null) {
            return;
        }
        int cx = componentCenterX(component);
        int cy = componentCenterY(component);
        int w = componentWidth(component);
        int h = componentHeight(component);
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.pose().rotate((float) Math.toRadians(component.rotationDegrees()));
        graphics.outline(-w / 2, -h / 2, w, h, ACCENT);
        for (Handle handle : Handle.resizeHandles()) {
            Point point = handle.localPoint(w, h);
            handleBox(graphics, point.x(), point.y(), handleAt(component, handle, mouseX, mouseY));
        }
        graphics.fill(-1, -h / 2 - 17, 1, -h / 2, ACCENT);
        handleBox(graphics, 0, -h / 2 - 20, handleAt(component, Handle.ROTATE, mouseX, mouseY));
        graphics.pose().popMatrix();
    }

    private void drawLayers(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.clickLayer = ClickLayer.LAYERS;
        this.layerPanel = new SPSGui.Rect(this.stage.right() + 7, this.stage.y(), this.panel.right() - this.stage.right() - 14, this.stage.height());
        graphics.fill(this.layerPanel.x(), this.layerPanel.y(), this.layerPanel.right(), this.layerPanel.bottom(), CHROME);
        graphics.outline(this.layerPanel.x(), this.layerPanel.y(), this.layerPanel.width(), this.layerPanel.height(), 0xFF304047);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.layers"), this.layerPanel.x() + 8, this.layerPanel.y() + 9, TEXT);
        SPSGui.Rect add = new SPSGui.Rect(this.layerPanel.right() - 26, this.layerPanel.y() + 5, 20, 20);
        darkIcon(graphics, add, SPSGui.Icon.PLUS, mouseX, mouseY, this.addMenuOpen ? 0xFFFFB04A : ACCENT);
        this.addClick(add, () -> this.addMenuOpen = !this.addMenuOpen, Component.translatable("screen.superpipeslide.projection_designer.add_component"));
        List<ProjectionComponent> components = layersDescending();
        this.layerListArea = new SPSGui.Rect(this.layerPanel.x() + 4, this.layerPanel.y() + 31, this.layerPanel.width() - 8, Math.max(16, this.layerPanel.height() - 76));
        graphics.enableScissor(this.layerListArea.x(), this.layerListArea.y(), this.layerListArea.right(), this.layerListArea.bottom());
        int y = this.layerListArea.y() + 3 - (int) this.layerScroll;
        int visibleIndex = 0;
        for (ProjectionComponent component : components) {
            SPSGui.Rect row = new SPSGui.Rect(this.layerPanel.x() + 6, y, this.layerPanel.width() - 12, 29);
            if (row.bottom() < this.layerListArea.y() || row.y() > this.layerListArea.bottom()) {
                y += 33;
                visibleIndex++;
                continue;
            }
            boolean selected = component.id().equals(this.selectedId);
            boolean dragging = component.id().equals(this.draggingLayerId);
            graphics.fill(row.x(), row.y(), row.right(), row.bottom(), dragging ? 0xAA273D3D : selected ? ACTIVE : row.contains(mouseX, mouseY) ? CHROME_HIGH : CHROME);
            graphics.outline(row.x(), row.y(), row.width(), row.height(), selected ? ACCENT : 0xFF334249);
            SPSGui.icon(graphics, new SPSGui.Rect(row.x() + 4, row.y() + 7, 14, 14), SPSGui.Icon.DRAG, dragging ? 0xFFFFB04A : MUTED);
            SPSGui.text(graphics, this.font, SPSGui.ellipsize(this.font, componentLabel(component.type()).getString(), row.width() - 66), row.x() + 21, row.y() + 6, selected ? TEXT : MUTED);
            SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.layer_index", component.layer()).getString(), row.x() + 21, row.y() + 18, MUTED, 0.58F);
            SPSGui.Rect up = new SPSGui.Rect(row.right() - 35, row.y() + 5, 13, 18);
            SPSGui.Rect down = new SPSGui.Rect(row.right() - 18, row.y() + 5, 13, 18);
            compactLetterButton(graphics, up, "^", mouseX, mouseY);
            compactLetterButton(graphics, down, "v", mouseX, mouseY);
            this.addPriorityClick(up, () -> raise(component.id()), Component.translatable("screen.superpipeslide.projection_designer.raise"));
            this.addPriorityClick(down, () -> lower(component.id()), Component.translatable("screen.superpipeslide.projection_designer.lower"));
            this.addClick(row, () -> select(component.id()), componentLabel(component.type()));
            if (this.draggingLayerId != null && this.draggingLayerTarget == visibleIndex) {
                graphics.fill(row.x(), row.y() - 2, row.right(), row.y() + 1, 0xFFFFB04A);
            }
            y += 33;
            visibleIndex++;
        }
        if (this.draggingLayerId != null && this.draggingLayerTarget >= components.size()) {
            graphics.fill(this.layerListArea.x() + 2, Math.min(this.layerListArea.bottom() - 2, y - 2), this.layerListArea.right() - 2, Math.min(this.layerListArea.bottom(), y + 1), 0xFFFFB04A);
        }
        graphics.disableScissor();
        if (this.layerScroll > 0.5D) {
            graphics.fill(this.layerListArea.x(), this.layerListArea.y(), this.layerListArea.right(), this.layerListArea.y() + 5, 0xD920292E);
        }
        if (this.layerScroll < maxLayerScroll() - 0.5D) {
            graphics.fill(this.layerListArea.x(), this.layerListArea.bottom() - 5, this.layerListArea.right(), this.layerListArea.bottom(), 0xD920292E);
        }
        ProjectionComponent component = selected();
        if (component != null) {
            SPSGui.Rect properties = new SPSGui.Rect(this.layerPanel.x() + 7, this.layerPanel.bottom() - 38, this.layerPanel.width() - 35, 22);
            SPSGui.Rect remove = new SPSGui.Rect(this.layerPanel.right() - 27, properties.y(), 20, 22);
            darkTextButton(graphics, properties, Component.translatable("screen.superpipeslide.projection_designer.properties"), mouseX, mouseY, ACCENT);
            darkIcon(graphics, remove, SPSGui.Icon.REMOVE, mouseX, mouseY, DANGER);
            this.addClick(properties, () -> {
                this.propertiesOpen = true;
                this.propertiesScroll = 0.0D;
            }, Component.translatable("screen.superpipeslide.projection_designer.properties"));
            this.addClick(remove, () -> deleteComponent(component.id()), Component.translatable("screen.superpipeslide.projection_designer.delete_component"));
        }
        if (this.addMenuOpen) {
            this.clickLayer = ClickLayer.ADD_MENU;
            drawAddMenu(graphics, add, mouseX, mouseY);
            this.clickLayer = ClickLayer.LAYERS;
        } else {
            this.addMenuBounds = new SPSGui.Rect(0, 0, 0, 0);
        }
    }

    private void drawAddMenu(GuiGraphicsExtractor graphics, SPSGui.Rect anchor, int mouseX, int mouseY) {
        ProjectionComponentType[] types = componentTypes(this.draft.target());
        int width = Math.min(150, Math.max(124, this.layerPanel.width() - 14));
        int rowHeight = 18;
        int contentHeight = types.length * rowHeight;
        int height = Math.min(10 + contentHeight, Math.max(82, this.layerPanel.height() - 44));
        int x = Math.max(this.layerPanel.x() + 6, this.layerPanel.right() - width - 6);
        int y = Math.max(this.layerPanel.y() + 29, Math.min(anchor.bottom() + 5, this.layerPanel.bottom() - height - 8));
        this.addMenuBounds = new SPSGui.Rect(x, y, width, height);
        this.addMenuScroll = Math.max(0.0D, Math.min(maxAddMenuScroll(), this.addMenuScroll));
        graphics.fill(x, y, x + width, y + height, 0xF20E1215);
        graphics.outline(x, y, width, height, ACCENT);
        SPSGui.Rect viewport = new SPSGui.Rect(x + 3, y + 5, width - 6, height - 10);
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        int rowY = viewport.y() - (int) this.addMenuScroll;
        for (ProjectionComponentType type : types) {
            SPSGui.Rect row = new SPSGui.Rect(x + 5, rowY, width - 10, rowHeight - 2);
            if (row.bottom() >= viewport.y() && row.y() <= viewport.bottom()) {
                graphics.fill(row.x(), row.y(), row.right(), row.bottom(), row.contains(mouseX, mouseY) ? ACTIVE : 0x00000000);
                SPSGui.smallText(graphics, this.font, componentLabel(type).getString(), row.x() + 6, row.y() + Math.max(3, (row.height() - 7) / 2), row.contains(mouseX, mouseY) ? TEXT : MUTED, 0.72F);
                this.addPriorityClick(row, () -> {
                    addComponent(type);
                    this.addMenuOpen = false;
                    this.addMenuScroll = 0.0D;
                }, componentLabel(type));
            }
            rowY += rowHeight;
        }
        graphics.disableScissor();
        if (this.addMenuScroll > 0.5D) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + 5, 0xDD0E1215);
        }
        if (this.addMenuScroll < maxAddMenuScroll() - 0.5D) {
            graphics.fill(x + 1, y + height - 5, x + width - 1, y + height - 1, 0xDD0E1215);
        }
    }

    private void drawScenarioPopover(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int popX = this.layersOpen ? this.layerPanel.x() - 241 : this.toolbar.right() - 250;
        boolean platformPreview = this.draft.target() == ProjectionLayoutTarget.PLATFORM;
        SPSGui.Rect pop = new SPSGui.Rect(popX, this.toolbar.bottom() + 7, 234, platformPreview ? 196 : 140);
        this.scenarioPopoverBounds = pop;
        graphics.fill(pop.x(), pop.y(), pop.right(), pop.bottom(), CHROME);
        graphics.outline(pop.x(), pop.y(), pop.width(), pop.height(), 0xFF42555B);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.preview_scenario"), pop.x() + 9, pop.y() + 9, TEXT);
        int y = pop.y() + 28;
        SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.routes").getString(), pop.x() + 9, y + 4, MUTED, 0.68F);
        compactButton(graphics, new SPSGui.Rect(pop.x() + 68, y, 20, 16), "-", mouseX, mouseY, () -> this.scenario = this.scenario.withRouteCount(this.scenario.routeCount() - 1));
        darkTextButton(graphics, new SPSGui.Rect(pop.x() + 91, y, 35, 16), Component.literal(Integer.toString(this.scenario.routeCount())), mouseX, mouseY, TEXT);
        compactButton(graphics, new SPSGui.Rect(pop.x() + 129, y, 20, 16), "+", mouseX, mouseY, () -> this.scenario = this.scenario.withRouteCount(this.scenario.routeCount() + 1));
        darkTextButton(graphics, new SPSGui.Rect(pop.x() + 155, y, 68, 16), localized(this.scenario.routePalette()), mouseX, mouseY, ACCENT);
        this.addClick(new SPSGui.Rect(pop.x() + 155, y, 68, 16), () -> this.scenario = this.scenario.withPalette(this.scenario.routePalette().next()), Component.translatable("screen.superpipeslide.projection_designer.palette"));
        y += 25;
        if (platformPreview) {
            toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.translation"), this.scenario.showTranslation(), mouseX, mouseY, () -> this.scenario = this.scenario.withTranslation(!this.scenario.showTranslation()));
            toggle(graphics, new SPSGui.Rect(pop.x() + 82, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.transfer"), this.scenario.showTransfers(), mouseX, mouseY, () -> this.scenario = this.scenario.withTransfers(!this.scenario.showTransfers()));
            toggle(graphics, new SPSGui.Rect(pop.x() + 155, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.stress"), this.scenario.longNames(), mouseX, mouseY, () -> this.scenario = this.scenario.withLongNames(!this.scenario.longNames()));
            y += 25;
            toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.terminal"), this.scenario.platformTerminal(), mouseX, mouseY, () -> this.scenario = this.scenario.withPlatformTerminal(!this.scenario.platformTerminal()));
            toggle(graphics, new SPSGui.Rect(pop.x() + 82, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.bidirectional"), this.scenario.platformBidirectional(), mouseX, mouseY, () -> this.scenario = this.scenario.withPlatformBidirectional(!this.scenario.platformBidirectional()));
            toggle(graphics, new SPSGui.Rect(pop.x() + 155, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.loop"), this.scenario.platformLoop(), mouseX, mouseY, () -> this.scenario = this.scenario.withPlatformLoop(!this.scenario.platformLoop()));
            y += 25;
            SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.platform_stop_count").getString(), pop.x() + 9, y + 4, MUTED, 0.68F);
            compactButton(graphics, new SPSGui.Rect(pop.x() + 84, y, 20, 16), "-", mouseX, mouseY, () -> this.scenario = this.scenario.withPlatformStopCount(this.scenario.platformStopCount() - 1));
            darkTextButton(graphics, new SPSGui.Rect(pop.x() + 107, y, 40, 16), Component.literal(Integer.toString(this.scenario.platformStopCount())), mouseX, mouseY, TEXT);
            compactButton(graphics, new SPSGui.Rect(pop.x() + 150, y, 20, 16), "+", mouseX, mouseY, () -> this.scenario = this.scenario.withPlatformStopCount(this.scenario.platformStopCount() + 1));
            toggle(graphics, new SPSGui.Rect(pop.x() + 174, y, 49, 18), Component.translatable("screen.superpipeslide.projection_designer.out_station_short"), this.scenario.outStationTransfers(), mouseX, mouseY, () -> this.scenario = this.scenario.withOutStationTransfers(!this.scenario.outStationTransfers()));
            if (this.previewExitBox != null) {
                this.previewExitBox.visible = false;
                this.previewExitBox.active = false;
            }
        } else {
            toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.translation"), this.scenario.showTranslation(), mouseX, mouseY, () -> this.scenario = this.scenario.withTranslation(!this.scenario.showTranslation()));
            toggle(graphics, new SPSGui.Rect(pop.x() + 82, y, 58, 18), Component.translatable("screen.superpipeslide.projection_designer.exit"), this.scenario.showExit(), mouseX, mouseY, () -> this.scenario = this.scenario.withExit(!this.scenario.showExit()));
            toggle(graphics, new SPSGui.Rect(pop.x() + 145, y, 78, 18), Component.translatable("screen.superpipeslide.projection_designer.stress"), this.scenario.longNames(), mouseX, mouseY, () -> this.scenario = this.scenario.withLongNames(!this.scenario.longNames()));
            y += 25;
            SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.exit_label").getString(), pop.x() + 9, y + 5, MUTED, 0.66F);
            if (this.previewExitBox != null) {
                this.previewExitBox.visible = this.scenario.showExit();
                this.previewExitBox.active = this.scenario.showExit();
                this.previewExitBox.setX(pop.x() + 64);
                this.previewExitBox.setY(y + 1);
                this.previewExitBox.setWidth(64);
                graphics.fill(this.previewExitBox.getX(), this.previewExitBox.getY() + 13, this.previewExitBox.getX() + this.previewExitBox.getWidth(), this.previewExitBox.getY() + 14, this.previewExitBox.isFocused() ? ACCENT : 0xFF425158);
            }
        }
        SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.preview_local").getString(), pop.x() + 9, pop.bottom() - 16, MUTED, 0.58F);
    }

    private void hideScenarioWidgets() {
        this.scenarioPopoverBounds = new SPSGui.Rect(0, 0, 0, 0);
        if (this.previewExitBox != null) {
            this.previewExitBox.visible = false;
            this.previewExitBox.active = false;
        }
    }

    private void hideCanvasSettingsWidgets() {
        this.canvasSettingsBounds = new SPSGui.Rect(0, 0, 0, 0);
        if (this.canvasWidthBox != null) {
            this.canvasWidthBox.visible = false;
            this.canvasWidthBox.active = false;
        }
        if (this.canvasHeightBox != null) {
            this.canvasHeightBox.visible = false;
            this.canvasHeightBox.active = false;
        }
    }

    private void drawProperties(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ProjectionComponent component = selected();
        if (component == null) {
            return;
        }
        int width = Math.min(216, Math.max(172, Math.min(this.panel.width() - 18, 196)));
        int desiredHeight = propertiesHeight(component);
        int height = Math.min(desiredHeight, Math.max(108, this.panel.height() - 18));
        if (!this.propertiesPositionInitialized) {
            this.propertiesX = this.stage.right() - width - 10;
            this.propertiesY = this.stage.bottom() - height - 10;
            this.propertiesPositionInitialized = true;
        }
        clampPropertiesPosition(width, height);
        this.propertiesScroll = Math.max(0.0D, Math.min(maxPropertiesScroll(component, height), this.propertiesScroll));
        SPSGui.Rect pop = new SPSGui.Rect((int) Math.round(this.propertiesX), (int) Math.round(this.propertiesY), width, height);
        this.propertiesBounds = pop;
        this.propertiesHeaderBounds = new SPSGui.Rect(pop.x(), pop.y(), pop.width(), 28);
        graphics.fill(pop.x(), pop.y(), pop.right(), pop.bottom(), CHROME);
        graphics.outline(pop.x(), pop.y(), pop.width(), pop.height(), ACCENT);
        graphics.fill(pop.x() + 1, pop.y() + 1, pop.right() - 1, pop.y() + 25, 0xFF253238);
        SPSGui.icon(graphics, new SPSGui.Rect(pop.x() + 7, pop.y() + 7, 12, 12), SPSGui.Icon.DRAG, MUTED);
        SPSGui.text(graphics, this.font, componentLabel(component.type()), pop.x() + 23, pop.y() + 9, TEXT);
        SPSGui.Rect close = new SPSGui.Rect(pop.right() - 25, pop.y() + 5, 19, 19);
        darkIcon(graphics, close, SPSGui.Icon.CLOSE, mouseX, mouseY, MUTED);
        this.addPriorityClick(close, () -> {
            this.propertiesOpen = false;
            this.colorTarget = null;
        }, Component.translatable("screen.superpipeslide.action.cancel"));
        SPSGui.Rect content = new SPSGui.Rect(pop.x(), pop.y() + 28, pop.width(), Math.max(1, pop.height() - 32));
        graphics.enableScissor(content.x(), content.y(), content.right(), content.bottom());
        int y = content.y() + 4 - (int) this.propertiesScroll;
        y = drawTypeSpecificProperties(graphics, pop, y, component, mouseX, mouseY);
        List<ProjectionVisibleCondition> allowedConditions = allowedVisibleConditions(component.type(), this.draft.target());
        if (allowedConditions.size() > 1) {
            ProjectionVisibleCondition visible = normalizeVisibleCondition(component.visibleCondition(), component.type(), this.draft.target());
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.visible"), localized(visible), mouseX, mouseY,
                    () -> replace(component.withVisibleCondition(nextVisibleCondition(visible, allowedConditions))));
        }
        graphics.disableScissor();
        if (this.propertiesScroll > 0.5D) {
            graphics.fill(pop.x() + 1, content.y(), pop.right() - 1, content.y() + 5, 0xDD20292E);
        }
        if (this.propertiesScroll < maxPropertiesScroll(component, height) - 0.5D) {
            graphics.fill(pop.x() + 1, pop.bottom() - 6, pop.right() - 1, pop.bottom() - 1, 0xDD20292E);
        }
    }

    private int propertiesHeight(ProjectionComponent component) {
        int base = switch (component.type()) {
            case BACKGROUND_PANEL -> 118;
            case STATION_TITLE_GROUP -> 286;
            case STATION_NAME_TEXT, TRANSLATION_TEXT -> 152;
            case CUSTOM_TEXT -> 174;
            case EXIT_BADGE -> 176;
            case DIVIDER -> 118;
            case ROUTE_LIST -> 246;
            case ROUTE_TEXT -> 184;
            case ROUTE_ICONS -> 270;
            case ROUTE_OUTLINE_ICONS -> 292;
            case ROUTE_CAPSULES -> 292;
            case ROUTE_BACKPLATE -> 210;
            case BUILTIN_ICON -> 274;
            case NETWORK_IMAGE -> 390;
            case PLATFORM_TITLE_GROUP -> 250;
            case PLATFORM_BADGE -> 218;
            case PLATFORM_DIRECTION_TITLE -> 220;
            case PLATFORM_STATUS_TAGS -> 251;
            case PLATFORM_LINE_CURRENT -> 252;
            case PLATFORM_LINE_BAND, PLATFORM_TERMINAL_STRIP -> 188;
            case PLATFORM_LINE_ICON -> 270;
            case PLATFORM_TRANSFER_LIST -> 318;
            case PLATFORM_TRANSFER_MATRIX -> 318;
            case PLATFORM_LAYOUT_STOP_LIST, PLATFORM_LAYOUT_PHYSICAL_MAP, PLATFORM_LAYOUT_PRACTICAL_MAP, PLATFORM_LAYOUT_SCHEMATIC_MAP, PLATFORM_LAYOUT_EDITOR_MAP -> 268;
        };
        if (component.settings() instanceof ProjectionComponentSettings.BuiltinIcon icon) {
            if (icon.fitMode() == ProjectionComponentSettings.ImageFitMode.CENTER) {
                return base + 21;
            }
            if (icon.fitMode() == ProjectionComponentSettings.ImageFitMode.TILE) {
                return base + 42;
            }
        }
        return base;
    }

    private int drawTypeSpecificProperties(GuiGraphicsExtractor graphics, SPSGui.Rect pop, int y, ProjectionComponent component, int mouseX, int mouseY) {
        ProjectionComponentSettings settings = component.settings();
        if (settings instanceof ProjectionComponentSettings.StationTitleGroup title) {
            hideComponentTextBox();
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.align"), localized(title.align()), mouseX, mouseY,
                    () -> replace(component.withSettings(title.withAlign(next(title.align())))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.orientation"), localized(title.orientation()), mouseX, mouseY,
                    () -> replace(component.withSettings(title.withOrientation(next(title.orientation())))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.missing_translation"), localized(title.missingTranslationMode()), mouseX, mouseY,
                    () -> replace(component.withSettings(title.withMissingTranslationMode(next(title.missingTranslationMode())))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.primary_font_size"), format(title.primaryFontSize()), mouseX, mouseY,
                    () -> replace(component.withSettings(title.withPrimaryFontSize(title.primaryFontSize() - 0.01F))),
                    () -> replace(component.withSettings(title.withPrimaryFontSize(title.primaryFontSize() + 0.01F))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.translation_font_size"), format(title.translationFontSize()), mouseX, mouseY,
                    () -> replace(component.withSettings(title.withTranslationFontSize(title.translationFontSize() - 0.005F))),
                    () -> replace(component.withSettings(title.withTranslationFontSize(title.translationFontSize() + 0.005F))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.title_gap"), format(title.gap()), mouseX, mouseY,
                    () -> replace(component.withSettings(title.withGap(title.gap() - 0.005F))),
                    () -> replace(component.withSettings(title.withGap(title.gap() + 0.005F))));
            y += 21;
            if (title.missingTranslationMode() == ProjectionComponentSettings.MissingTranslationMode.EXPAND_PRIMARY) {
                numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.missing_primary_scale"), format(title.missingPrimaryScale()), mouseX, mouseY,
                        () -> replace(component.withSettings(title.withMissingPrimaryScale(title.missingPrimaryScale() - 0.05F))),
                        () -> replace(component.withSettings(title.withMissingPrimaryScale(title.missingPrimaryScale() + 0.05F))));
                y += 21;
            }
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.primary_overflow"), localized(title.primaryOverflow()), mouseX, mouseY,
                    () -> replace(component.withSettings(title.withPrimaryOverflow(next(title.primaryOverflow())))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.translation_overflow"), localized(title.translationOverflow()), mouseX, mouseY,
                    () -> replace(component.withSettings(title.withTranslationOverflow(next(title.translationOverflow())))));
            y += 21;
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 80, 18), Component.translatable("screen.superpipeslide.projection_designer.color.primary_text.short"), title.primaryColor(), ColorTarget.PRIMARY_TEXT, mouseX, mouseY);
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 94, y, 80, 18), Component.translatable("screen.superpipeslide.projection_designer.color.translation_text.short"), title.translationColor(), ColorTarget.TRANSLATION_TEXT, mouseX, mouseY);
            return y + 25;
        }
        if (settings instanceof ProjectionComponentSettings.Text text) {
            hideNetworkUrlBox();
            if (component.type() == ProjectionComponentType.CUSTOM_TEXT) {
                SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.text").getString(), pop.x() + 9, y + 5, MUTED, 0.66F);
                showComponentTextBox(component, pop.x() + 43, y + 2, pop.width() - 54);
                y += 24;
            } else {
                hideComponentTextBox();
            }
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.align"), localized(text.align()), mouseX, mouseY, () -> replace(component.withSettings(text.withAlign(next(text.align())))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.overflow"), localized(text.overflow()), mouseX, mouseY, () -> replace(component.withSettings(text.withOverflow(next(text.overflow())))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.orientation"), localized(text.orientation()), mouseX, mouseY, () -> replace(component.withSettings(text.withOrientation(next(text.orientation())))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.font_size"), format(text.fontSize()), mouseX, mouseY,
                    () -> replace(component.withSettings(text.withFontSize(text.fontSize() - 0.01F))),
                    () -> replace(component.withSettings(text.withFontSize(text.fontSize() + 0.01F))));
            y += 23;
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.text.short"), text.textColor(), ColorTarget.TEXT, mouseX, mouseY);
            return y + 25;
        }
        hideComponentTextBox();
        hideNetworkUrlBox();
        if (settings instanceof ProjectionComponentSettings.Panel panel) {
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.border_width"), format(panel.borderWidth()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.Panel(panel.fillColor(), panel.borderColor(), panel.borderWidth() - 0.005F, panel.opacity()))),
                    () -> replace(component.withSettings(new ProjectionComponentSettings.Panel(panel.fillColor(), panel.borderColor(), panel.borderWidth() + 0.005F, panel.opacity()))));
            y += 23;
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.background.short"), panel.fillColor(), ColorTarget.FILL, mouseX, mouseY);
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 82, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.border.short"), panel.borderColor(), ColorTarget.BORDER, mouseX, mouseY);
            return y + 25;
        }
        if (settings instanceof ProjectionComponentSettings.ExitBadge exit) {
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.font_size"), format(exit.fontSize()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.ExitBadge(exit.fillEnabled(), exit.borderEnabled(), exit.fillColor(), exit.borderColor(), exit.textColor(), exit.fontSize() - 0.01F))),
                    () -> replace(component.withSettings(new ProjectionComponentSettings.ExitBadge(exit.fillEnabled(), exit.borderEnabled(), exit.fillColor(), exit.borderColor(), exit.textColor(), exit.fontSize() + 0.01F))));
            y += 23;
            toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.fill_enabled"), exit.fillEnabled(), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.ExitBadge(!exit.fillEnabled(), exit.borderEnabled(), exit.fillColor(), exit.borderColor(), exit.textColor(), exit.fontSize()))));
            toggle(graphics, new SPSGui.Rect(pop.x() + 82, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.border_enabled"), exit.borderEnabled(), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.ExitBadge(exit.fillEnabled(), !exit.borderEnabled(), exit.fillColor(), exit.borderColor(), exit.textColor(), exit.fontSize()))));
            y += 23;
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.text.short"), exit.textColor(), ColorTarget.TEXT, mouseX, mouseY);
            if (exit.fillEnabled()) {
                drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 82, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.background.short"), exit.fillColor(), ColorTarget.FILL, mouseX, mouseY);
            }
            y += 23;
            if (exit.borderEnabled()) {
                drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.border.short"), exit.borderColor(), ColorTarget.BORDER, mouseX, mouseY);
                return y + 25;
            }
            return y + 25;
        }
        if (settings instanceof ProjectionComponentSettings.Divider divider) {
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.thickness"), format(divider.thickness()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.Divider(divider.color(), divider.thickness() - 0.005F, divider.dashed()))),
                    () -> replace(component.withSettings(new ProjectionComponentSettings.Divider(divider.color(), divider.thickness() + 0.005F, divider.dashed()))));
            y += 23;
            toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.dashed"), divider.dashed(), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.Divider(divider.color(), divider.thickness(), !divider.dashed()))));
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 82, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.accent.short"), divider.color(), ColorTarget.ACCENT, mouseX, mouseY);
            return y + 25;
        }
        if (settings instanceof ProjectionComponentSettings.BuiltinIcon icon) {
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.icon"), Component.translatable(ProjectionBuiltinIcon.byId(icon.iconId()).translationKey()), mouseX, mouseY,
                    () -> {
                        this.iconPickerOpen = !this.iconPickerOpen;
                        this.iconPickerScroll = 0.0D;
                    });
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.fit_mode"), localized(icon.fitMode()), mouseX, mouseY,
                    () -> replace(component.withSettings(icon.withFitMode(nextIconFitMode(icon.fitMode())))));
            y += 21;
            if (icon.fitMode() == ProjectionComponentSettings.ImageFitMode.CENTER || icon.fitMode() == ProjectionComponentSettings.ImageFitMode.TILE) {
                numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.image_scale"), format(icon.imageScale()), mouseX, mouseY,
                        () -> replace(component.withSettings(icon.withImageScale(icon.imageScale() - 0.05F))),
                        () -> replace(component.withSettings(icon.withImageScale(icon.imageScale() + 0.05F))));
                y += 21;
            }
            if (icon.fitMode() == ProjectionComponentSettings.ImageFitMode.TILE) {
                numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.tile_gap"), format(icon.tileGap()), mouseX, mouseY,
                        () -> replace(component.withSettings(icon.withTileGap(icon.tileGap() - 0.01F))),
                        () -> replace(component.withSettings(icon.withTileGap(icon.tileGap() + 0.01F))));
                y += 21;
            }
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.anchor"), localized(icon.anchor()), mouseX, mouseY,
                    () -> replace(component.withSettings(icon.withAnchor(next(icon.anchor())))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.tint_mode"), localized(icon.tintMode()), mouseX, mouseY,
                    () -> replace(component.withSettings(icon.withTintMode(next(icon.tintMode())))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.opacity"), format(icon.opacity()), mouseX, mouseY,
                    () -> replace(component.withSettings(icon.withOpacity(icon.opacity() - 0.05F))),
                    () -> replace(component.withSettings(icon.withOpacity(icon.opacity() + 0.05F))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.padding"), format(icon.padding()), mouseX, mouseY,
                    () -> replace(component.withSettings(icon.withPadding(icon.padding() - 0.01F))),
                    () -> replace(component.withSettings(icon.withPadding(icon.padding() + 0.01F))));
            y += 21;
            toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 78, 18), Component.translatable("screen.superpipeslide.projection_designer.fill_enabled"), icon.backgroundEnabled(), mouseX, mouseY,
                    () -> replace(component.withSettings(icon.withBackgroundEnabled(!icon.backgroundEnabled()))));
            toggle(graphics, new SPSGui.Rect(pop.x() + 92, y, 78, 18), Component.translatable("screen.superpipeslide.projection_designer.border_enabled"), icon.borderEnabled(), mouseX, mouseY,
                    () -> replace(component.withSettings(icon.withBorderEnabled(!icon.borderEnabled()))));
            y += 21;
            if (icon.borderEnabled()) {
                numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.border_width"), format(icon.borderWidth()), mouseX, mouseY,
                        () -> replace(component.withSettings(icon.withBorderWidth(icon.borderWidth() - 0.005F))),
                        () -> replace(component.withSettings(icon.withBorderWidth(icon.borderWidth() + 0.005F))));
                y += 21;
            }
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 78, 18), Component.translatable("screen.superpipeslide.projection_designer.color.tint.short"), icon.tintColor(), ColorTarget.TINT, mouseX, mouseY);
            if (icon.tintMode() == ProjectionComponentSettings.IconTintMode.DUOTONE) {
                drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 92, y, 78, 18), Component.translatable("screen.superpipeslide.projection_designer.color.secondary.short"), icon.secondaryColor(), ColorTarget.SECONDARY, mouseX, mouseY);
            }
            y += 23;
            if (icon.backgroundEnabled()) {
                drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 78, 18), Component.translatable("screen.superpipeslide.projection_designer.color.background.short"), icon.backgroundColor(), ColorTarget.FILL, mouseX, mouseY);
            }
            if (icon.borderEnabled()) {
                drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 92, y, 78, 18), Component.translatable("screen.superpipeslide.projection_designer.color.border.short"), icon.borderColor(), ColorTarget.BORDER, mouseX, mouseY);
            }
            return y + 25;
        }
        if (settings instanceof ProjectionComponentSettings.NetworkImage image) {
            SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.url").getString(), pop.x() + 9, y + 5, MUTED, 0.66F);
            showNetworkUrlBox(component, pop, y + 2, pop.x() + 43, pop.width() - 54);
            y += 24;
            ProjectionNetworkImageCache.State state = ProjectionNetworkImageCache.state(image.url());
            SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.network_image_status").getString(), pop.x() + 9, y + 5, MUTED, 0.62F);
            String status = Component.translatable(state.messageKey() == null || state.messageKey().isBlank() ? "screen.superpipeslide.projection_image.ready" : state.messageKey()).getString();
            darkTextButton(graphics, new SPSGui.Rect(pop.x() + 84, y, pop.width() - 93, 17), Component.literal(status), mouseX, mouseY, state.ready() ? ACCENT : TEXT);
            y += 21;
            darkTextButton(graphics, new SPSGui.Rect(pop.x() + 9, y, 76, 18), Component.translatable("screen.superpipeslide.projection_designer.reload_image"), mouseX, mouseY, ACCENT);
            this.addClick(new SPSGui.Rect(pop.x() + 9, y, 76, 18), () -> ProjectionNetworkImageCache.reload(image.url()), Component.translatable("screen.superpipeslide.projection_designer.reload_image"));
            darkTextButton(graphics, new SPSGui.Rect(pop.x() + 90, y, 82, 18), Component.translatable("screen.superpipeslide.projection_designer.clear_image_cache"), mouseX, mouseY, MUTED);
            this.addClick(new SPSGui.Rect(pop.x() + 90, y, 82, 18), () -> ProjectionNetworkImageCache.clearUrl(image.url()), Component.translatable("screen.superpipeslide.projection_designer.clear_image_cache"));
            y += 23;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.fit_mode"), localized(image.fitMode()), mouseX, mouseY,
                    () -> replace(component.withSettings(image.withFitMode(next(image.fitMode())))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.anchor"), localized(image.anchor()), mouseX, mouseY,
                    () -> replace(component.withSettings(image.withAnchor(next(image.anchor())))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.loading_mode"), localized(image.loadingMode()), mouseX, mouseY,
                    () -> replace(component.withSettings(image.withLoadingMode(next(image.loadingMode())))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.fallback_mode"), localized(image.fallbackMode()), mouseX, mouseY,
                    () -> replace(component.withSettings(image.withFallbackMode(next(image.fallbackMode())))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.opacity"), format(image.opacity()), mouseX, mouseY,
                    () -> replace(component.withSettings(image.withOpacity(image.opacity() - 0.05F))),
                    () -> replace(component.withSettings(image.withOpacity(image.opacity() + 0.05F))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.crop_x"), format(image.cropX()), mouseX, mouseY,
                    () -> replace(component.withSettings(image.withCrop(image.cropX() - 0.01F, image.cropY(), image.cropW(), image.cropH()))),
                    () -> replace(component.withSettings(image.withCrop(image.cropX() + 0.01F, image.cropY(), image.cropW(), image.cropH()))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.crop_y"), format(image.cropY()), mouseX, mouseY,
                    () -> replace(component.withSettings(image.withCrop(image.cropX(), image.cropY() - 0.01F, image.cropW(), image.cropH()))),
                    () -> replace(component.withSettings(image.withCrop(image.cropX(), image.cropY() + 0.01F, image.cropW(), image.cropH()))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.crop_w"), format(image.cropW()), mouseX, mouseY,
                    () -> replace(component.withSettings(image.withCrop(image.cropX(), image.cropY(), image.cropW() - 0.01F, image.cropH()))),
                    () -> replace(component.withSettings(image.withCrop(image.cropX(), image.cropY(), image.cropW() + 0.01F, image.cropH()))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.crop_h"), format(image.cropH()), mouseX, mouseY,
                    () -> replace(component.withSettings(image.withCrop(image.cropX(), image.cropY(), image.cropW(), image.cropH() - 0.01F))),
                    () -> replace(component.withSettings(image.withCrop(image.cropX(), image.cropY(), image.cropW(), image.cropH() + 0.01F))));
            y += 21;
            toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 78, 18), Component.translatable("screen.superpipeslide.projection_designer.fill_enabled"), image.backgroundEnabled(), mouseX, mouseY,
                    () -> replace(component.withSettings(image.withBackgroundEnabled(!image.backgroundEnabled()))));
            toggle(graphics, new SPSGui.Rect(pop.x() + 92, y, 78, 18), Component.translatable("screen.superpipeslide.projection_designer.border_enabled"), image.borderEnabled(), mouseX, mouseY,
                    () -> replace(component.withSettings(image.withBorderEnabled(!image.borderEnabled()))));
            y += 21;
            if (image.borderEnabled()) {
                numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.border_width"), format(image.borderWidth()), mouseX, mouseY,
                        () -> replace(component.withSettings(image.withBorderWidth(image.borderWidth() - 0.005F))),
                        () -> replace(component.withSettings(image.withBorderWidth(image.borderWidth() + 0.005F))));
                y += 21;
            }
            if (image.backgroundEnabled()) {
                drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 78, 18), Component.translatable("screen.superpipeslide.projection_designer.color.background.short"), image.backgroundColor(), ColorTarget.FILL, mouseX, mouseY);
            }
            if (image.borderEnabled()) {
                drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 92, y, 78, 18), Component.translatable("screen.superpipeslide.projection_designer.color.border.short"), image.borderColor(), ColorTarget.BORDER, mouseX, mouseY);
            }
            return y + 25;
        }
        if (settings instanceof ProjectionComponentSettings.RouteList list) {
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.route_row_height"), format(list.rowHeight()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.RouteList(list.rowHeight() - 0.01F, list.gap(), list.stripeWidth(), list.fontSize(), list.maxVisible(), list.overflow(), list.labelOverflow(), list.textColor(), list.plusTextColor(), list.rotateIntervalTicks()))),
                    () -> replace(component.withSettings(new ProjectionComponentSettings.RouteList(list.rowHeight() + 0.01F, list.gap(), list.stripeWidth(), list.fontSize(), list.maxVisible(), list.overflow(), list.labelOverflow(), list.textColor(), list.plusTextColor(), list.rotateIntervalTicks()))));
            y += 21;
            y = routeCommonRows(graphics, pop, y, component, list.fontSize(), list.maxVisible(), list.overflow(), list.plusTextColor(), mouseX, mouseY, true);
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.label_overflow"), localized(list.labelOverflow()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.RouteList(list.rowHeight(), list.gap(), list.stripeWidth(), list.fontSize(), list.maxVisible(), list.overflow(), next(list.labelOverflow()), list.textColor(), list.plusTextColor(), list.rotateIntervalTicks()))));
            y += 21;
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.text.short"), list.textColor(), ColorTarget.TEXT, mouseX, mouseY);
            if (list.overflow() == ProjectionComponentSettings.RouteOverflowMode.PLUS_COUNT) {
                drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 82, y, 76, 18), Component.translatable("screen.superpipeslide.projection_designer.color.plus.short"), list.plusTextColor(), ColorTarget.PLUS, mouseX, mouseY);
            }
            return y + 25;
        }
        if (settings instanceof ProjectionComponentSettings.RouteText text) {
            y = routeCommonRows(graphics, pop, y, component, text.fontSize(), 1, text.overflow(), text.plusTextColor(), mouseX, mouseY, true);
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.align"), localized(text.align()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.RouteText(text.fontSize(), text.overflow(), text.shortName(), text.textColor(), text.plusTextColor(), next(text.align()), text.rotateIntervalTicks()))));
            y += 21;
            toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 76, 18), Component.translatable("screen.superpipeslide.projection_designer.short_name"), text.shortName(), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.RouteText(text.fontSize(), text.overflow(), !text.shortName(), text.textColor(), text.plusTextColor(), text.align(), text.rotateIntervalTicks()))));
            y += 21;
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.text.short"), text.textColor(), ColorTarget.TEXT, mouseX, mouseY);
            if (text.overflow() == ProjectionComponentSettings.RouteOverflowMode.PLUS_COUNT) {
                drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 82, y, 76, 18), Component.translatable("screen.superpipeslide.projection_designer.color.plus.short"), text.plusTextColor(), ColorTarget.PLUS, mouseX, mouseY);
            }
            return y + 25;
        }
        if (settings instanceof ProjectionComponentSettings.RouteIcon icon) {
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.icon_size"), format(icon.iconSize()), mouseX, mouseY,
                    () -> replace(component.withSettings(icon.withIconSize(icon.iconSize() - 0.01F))),
                    () -> replace(component.withSettings(icon.withIconSize(icon.iconSize() + 0.01F))));
            y += 21;
            y = routeCommonRows(graphics, pop, y, component, icon.fontSize(), icon.maxVisible(), icon.overflow(), icon.plusTextColor(), mouseX, mouseY, true);
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.flow"), localized(icon.flow()), mouseX, mouseY,
                    () -> replace(component.withSettings(icon.withFlow(next(icon.flow())))));
            y += 21;
            toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 76, 18), Component.translatable("screen.superpipeslide.projection_designer.wrap_icons"), icon.wrapEnabled(), mouseX, mouseY,
                    () -> replace(component.withSettings(icon.withWrapEnabled(!icon.wrapEnabled()))));
            y += 21;
            if (icon.wrapEnabled()) {
                numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.wrap_tracks"), Integer.toString(icon.wrapTracks()), mouseX, mouseY,
                        () -> replace(component.withSettings(icon.withWrapTracks(icon.wrapTracks() - 1))),
                        () -> replace(component.withSettings(icon.withWrapTracks(icon.wrapTracks() + 1))));
                y += 21;
            }
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.icon_shape"), localized(icon.shape()), mouseX, mouseY,
                    () -> replace(component.withSettings(icon.withShape(next(icon.shape())))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.border_width"), format(icon.borderWidth()), mouseX, mouseY,
                    () -> replace(component.withSettings(icon.withBorderWidth(icon.borderWidth() - 0.005F))),
                    () -> replace(component.withSettings(icon.withBorderWidth(icon.borderWidth() + 0.005F))));
            y += 21;
            if (component.type() == ProjectionComponentType.ROUTE_OUTLINE_ICONS) {
                numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.ring_thickness"), format(icon.ringThicknessRatio()), mouseX, mouseY,
                        () -> replace(component.withSettings(icon.withRingThicknessRatio(icon.ringThicknessRatio() - 0.02F))),
                        () -> replace(component.withSettings(icon.withRingThicknessRatio(icon.ringThicknessRatio() + 0.02F))));
                y += 21;
            }
            toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 76, 18), Component.translatable("screen.superpipeslide.projection_designer.show_label"), icon.showLabel(), mouseX, mouseY,
                    () -> replace(component.withSettings(icon.withShowLabel(!icon.showLabel()))));
            y += 21;
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.text.short"), icon.textColor(), ColorTarget.TEXT, mouseX, mouseY);
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 82, y, 76, 18), Component.translatable("screen.superpipeslide.projection_designer.color.border.short"), icon.borderColor(), ColorTarget.BORDER, mouseX, mouseY);
            y += 23;
            if (icon.overflow() == ProjectionComponentSettings.RouteOverflowMode.PLUS_COUNT) {
                drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 76, 18), Component.translatable("screen.superpipeslide.projection_designer.color.plus.short"), icon.plusTextColor(), ColorTarget.PLUS, mouseX, mouseY);
            }
            return y + 25;
        }
        if (settings instanceof ProjectionComponentSettings.RouteCapsules capsules) {
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.capsule_height"), format(capsules.capsuleHeight()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.RouteCapsules(capsules.capsuleWidth(), capsules.capsuleHeight() - 0.01F, capsules.gap(), capsules.fontSize(), capsules.maxVisible(), capsules.flow(), capsules.contentOrientation(), capsules.overflow(), capsules.labelOverflow(), capsules.showShortName(), capsules.textColor(), capsules.fillColor(), capsules.plusTextColor(), capsules.rotateIntervalTicks()))),
                    () -> replace(component.withSettings(new ProjectionComponentSettings.RouteCapsules(capsules.capsuleWidth(), capsules.capsuleHeight() + 0.01F, capsules.gap(), capsules.fontSize(), capsules.maxVisible(), capsules.flow(), capsules.contentOrientation(), capsules.overflow(), capsules.labelOverflow(), capsules.showShortName(), capsules.textColor(), capsules.fillColor(), capsules.plusTextColor(), capsules.rotateIntervalTicks()))));
            y += 21;
            y = routeCommonRows(graphics, pop, y, component, capsules.fontSize(), capsules.maxVisible(), capsules.overflow(), capsules.plusTextColor(), mouseX, mouseY, true);
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.flow"), localized(capsules.flow()), mouseX, mouseY, () -> replace(component.withSettings(new ProjectionComponentSettings.RouteCapsules(capsules.capsuleWidth(), capsules.capsuleHeight(), capsules.gap(), capsules.fontSize(), capsules.maxVisible(), next(capsules.flow()), capsules.contentOrientation(), capsules.overflow(), capsules.labelOverflow(), capsules.showShortName(), capsules.textColor(), capsules.fillColor(), capsules.plusTextColor(), capsules.rotateIntervalTicks()))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.content_orientation"), localized(capsules.contentOrientation()), mouseX, mouseY, () -> replace(component.withSettings(new ProjectionComponentSettings.RouteCapsules(capsules.capsuleWidth(), capsules.capsuleHeight(), capsules.gap(), capsules.fontSize(), capsules.maxVisible(), capsules.flow(), next(capsules.contentOrientation()), capsules.overflow(), capsules.labelOverflow(), capsules.showShortName(), capsules.textColor(), capsules.fillColor(), capsules.plusTextColor(), capsules.rotateIntervalTicks()))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.label_overflow"), localized(capsules.labelOverflow()), mouseX, mouseY, () -> replace(component.withSettings(new ProjectionComponentSettings.RouteCapsules(capsules.capsuleWidth(), capsules.capsuleHeight(), capsules.gap(), capsules.fontSize(), capsules.maxVisible(), capsules.flow(), capsules.contentOrientation(), capsules.overflow(), next(capsules.labelOverflow()), capsules.showShortName(), capsules.textColor(), capsules.fillColor(), capsules.plusTextColor(), capsules.rotateIntervalTicks()))));
            y += 21;
            toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 80, 18), Component.translatable("screen.superpipeslide.projection_designer.short_name"), capsules.showShortName(), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.RouteCapsules(capsules.capsuleWidth(), capsules.capsuleHeight(), capsules.gap(), capsules.fontSize(), capsules.maxVisible(), capsules.flow(), capsules.contentOrientation(), capsules.overflow(), capsules.labelOverflow(), !capsules.showShortName(), capsules.textColor(), capsules.fillColor(), capsules.plusTextColor(), capsules.rotateIntervalTicks()))));
            y += 21;
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.text.short"), capsules.textColor(), ColorTarget.TEXT, mouseX, mouseY);
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 82, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.background.short"), capsules.fillColor(), ColorTarget.FILL, mouseX, mouseY);
            y += 23;
            if (capsules.overflow() == ProjectionComponentSettings.RouteOverflowMode.PLUS_COUNT) {
                drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 76, 18), Component.translatable("screen.superpipeslide.projection_designer.color.plus.short"), capsules.plusTextColor(), ColorTarget.PLUS, mouseX, mouseY);
            }
            return y + 25;
        }
        if (settings instanceof ProjectionComponentSettings.RouteBackplate backplate) {
            y = routeCommonRows(graphics, pop, y, component, 0.0F, backplate.maxVisible(), backplate.overflow(), backplate.plusTextColor(), mouseX, mouseY, false);
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.direction"), localized(backplate.direction()), mouseX, mouseY, () -> replace(component.withSettings(new ProjectionComponentSettings.RouteBackplate(next(backplate.direction()), backplate.colorPolicy(), backplate.maxVisible(), backplate.opacity(), backplate.overflow(), backplate.plusTextColor(), backplate.rotateIntervalTicks()))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.opacity"), format(backplate.opacity()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.RouteBackplate(backplate.direction(), backplate.colorPolicy(), backplate.maxVisible(), backplate.opacity() - 0.05F, backplate.overflow(), backplate.plusTextColor(), backplate.rotateIntervalTicks()))),
                    () -> replace(component.withSettings(new ProjectionComponentSettings.RouteBackplate(backplate.direction(), backplate.colorPolicy(), backplate.maxVisible(), backplate.opacity() + 0.05F, backplate.overflow(), backplate.plusTextColor(), backplate.rotateIntervalTicks()))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.color_policy"), localized(backplate.colorPolicy()), mouseX, mouseY, () -> replace(component.withSettings(new ProjectionComponentSettings.RouteBackplate(backplate.direction(), next(backplate.colorPolicy()), backplate.maxVisible(), backplate.opacity(), backplate.overflow(), backplate.plusTextColor(), backplate.rotateIntervalTicks()))));
            y += 21;
            if (backplate.overflow() == ProjectionComponentSettings.RouteOverflowMode.PLUS_COUNT) {
                drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 76, 18), Component.translatable("screen.superpipeslide.projection_designer.color.plus.short"), backplate.plusTextColor(), ColorTarget.PLUS, mouseX, mouseY);
            }
            return y + 23;
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTitleGroup title) {
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.content"), localizedPlatform(title.content()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformTitleGroup(nextEnum(title.content()), title.primaryColor(), title.secondaryColor(), title.primaryFontSize(), title.secondaryFontSize(), title.gap(), title.align(), title.primaryOverflow(), title.secondaryOverflow(), title.orientation(), title.missingSecondaryMode(), title.missingPrimaryScale()))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.align"), localized(title.align()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformTitleGroup(title.content(), title.primaryColor(), title.secondaryColor(), title.primaryFontSize(), title.secondaryFontSize(), title.gap(), next(title.align()), title.primaryOverflow(), title.secondaryOverflow(), title.orientation(), title.missingSecondaryMode(), title.missingPrimaryScale()))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.orientation"), localized(title.orientation()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformTitleGroup(title.content(), title.primaryColor(), title.secondaryColor(), title.primaryFontSize(), title.secondaryFontSize(), title.gap(), title.align(), title.primaryOverflow(), title.secondaryOverflow(), next(title.orientation()), title.missingSecondaryMode(), title.missingPrimaryScale()))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.primary_font_size"), format(title.primaryFontSize()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformTitleGroup(title.content(), title.primaryColor(), title.secondaryColor(), title.primaryFontSize() - 0.01F, title.secondaryFontSize(), title.gap(), title.align(), title.primaryOverflow(), title.secondaryOverflow(), title.orientation(), title.missingSecondaryMode(), title.missingPrimaryScale()))),
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformTitleGroup(title.content(), title.primaryColor(), title.secondaryColor(), title.primaryFontSize() + 0.01F, title.secondaryFontSize(), title.gap(), title.align(), title.primaryOverflow(), title.secondaryOverflow(), title.orientation(), title.missingSecondaryMode(), title.missingPrimaryScale()))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.secondary_font_size"), format(title.secondaryFontSize()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformTitleGroup(title.content(), title.primaryColor(), title.secondaryColor(), title.primaryFontSize(), title.secondaryFontSize() - 0.005F, title.gap(), title.align(), title.primaryOverflow(), title.secondaryOverflow(), title.orientation(), title.missingSecondaryMode(), title.missingPrimaryScale()))),
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformTitleGroup(title.content(), title.primaryColor(), title.secondaryColor(), title.primaryFontSize(), title.secondaryFontSize() + 0.005F, title.gap(), title.align(), title.primaryOverflow(), title.secondaryOverflow(), title.orientation(), title.missingSecondaryMode(), title.missingPrimaryScale()))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.title_gap"), format(title.gap()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformTitleGroup(title.content(), title.primaryColor(), title.secondaryColor(), title.primaryFontSize(), title.secondaryFontSize(), title.gap() - 0.005F, title.align(), title.primaryOverflow(), title.secondaryOverflow(), title.orientation(), title.missingSecondaryMode(), title.missingPrimaryScale()))),
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformTitleGroup(title.content(), title.primaryColor(), title.secondaryColor(), title.primaryFontSize(), title.secondaryFontSize(), title.gap() + 0.005F, title.align(), title.primaryOverflow(), title.secondaryOverflow(), title.orientation(), title.missingSecondaryMode(), title.missingPrimaryScale()))));
            y += 23;
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 74, 18), Component.translatable("screen.superpipeslide.projection_designer.color.primary_text.short"), title.primaryColor(), ColorTarget.PRIMARY_TEXT, mouseX, mouseY);
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 88, y, 74, 18), Component.translatable("screen.superpipeslide.projection_designer.color.secondary_text.short"), title.secondaryColor(), ColorTarget.TRANSLATION_TEXT, mouseX, mouseY);
            return y + 25;
        }
        if (settings instanceof ProjectionComponentSettings.PlatformBadge badge) {
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.style"), localizedPlatform(badge.style()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformBadge(nextEnum(badge.style()), badge.useLineColor(), badge.fillColor(), badge.borderColor(), badge.textColor(), badge.fontSize(), badge.borderWidth(), badge.prefix(), badge.suffix()))));
            y += 21;
            toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 96, 18), Component.translatable("screen.superpipeslide.projection_designer.use_line_color"), badge.useLineColor(), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformBadge(badge.style(), !badge.useLineColor(), badge.fillColor(), badge.borderColor(), badge.textColor(), badge.fontSize(), badge.borderWidth(), badge.prefix(), badge.suffix()))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.font_size"), format(badge.fontSize()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformBadge(badge.style(), badge.useLineColor(), badge.fillColor(), badge.borderColor(), badge.textColor(), badge.fontSize() - 0.01F, badge.borderWidth(), badge.prefix(), badge.suffix()))),
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformBadge(badge.style(), badge.useLineColor(), badge.fillColor(), badge.borderColor(), badge.textColor(), badge.fontSize() + 0.01F, badge.borderWidth(), badge.prefix(), badge.suffix()))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.border_width"), format(badge.borderWidth()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformBadge(badge.style(), badge.useLineColor(), badge.fillColor(), badge.borderColor(), badge.textColor(), badge.fontSize(), badge.borderWidth() - 0.005F, badge.prefix(), badge.suffix()))),
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformBadge(badge.style(), badge.useLineColor(), badge.fillColor(), badge.borderColor(), badge.textColor(), badge.fontSize(), badge.borderWidth() + 0.005F, badge.prefix(), badge.suffix()))));
            y += 23;
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.text.short"), badge.textColor(), ColorTarget.TEXT, mouseX, mouseY);
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 82, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.background.short"), badge.fillColor(), ColorTarget.FILL, mouseX, mouseY);
            y += 23;
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.border.short"), badge.borderColor(), ColorTarget.BORDER, mouseX, mouseY);
            return y + 25;
        }
        if (settings instanceof ProjectionComponentSettings.PlatformDirection direction) {
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.source"), localizedPlatform(direction.source()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformDirection(nextEnum(direction.source()), direction.prefix(), direction.arrow(), direction.arrowPlacement(), direction.textColor(), direction.arrowColor(), direction.fontSize(), direction.align(), direction.overflow()))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.prefix"), localizedPlatform(direction.prefix()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformDirection(direction.source(), nextEnum(direction.prefix()), direction.arrow(), direction.arrowPlacement(), direction.textColor(), direction.arrowColor(), direction.fontSize(), direction.align(), direction.overflow()))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.arrow"), localizedPlatform(direction.arrow()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformDirection(direction.source(), direction.prefix(), nextEnum(direction.arrow()), direction.arrowPlacement(), direction.textColor(), direction.arrowColor(), direction.fontSize(), direction.align(), direction.overflow()))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.arrow_placement"), localizedPlatform(direction.arrowPlacement()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformDirection(direction.source(), direction.prefix(), direction.arrow(), nextEnum(direction.arrowPlacement()), direction.textColor(), direction.arrowColor(), direction.fontSize(), direction.align(), direction.overflow()))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.align"), localized(direction.align()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformDirection(direction.source(), direction.prefix(), direction.arrow(), direction.arrowPlacement(), direction.textColor(), direction.arrowColor(), direction.fontSize(), next(direction.align()), direction.overflow()))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.overflow"), localized(direction.overflow()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformDirection(direction.source(), direction.prefix(), direction.arrow(), direction.arrowPlacement(), direction.textColor(), direction.arrowColor(), direction.fontSize(), direction.align(), next(direction.overflow())))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.font_size"), format(direction.fontSize()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformDirection(direction.source(), direction.prefix(), direction.arrow(), direction.arrowPlacement(), direction.textColor(), direction.arrowColor(), direction.fontSize() - 0.01F, direction.align(), direction.overflow()))),
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformDirection(direction.source(), direction.prefix(), direction.arrow(), direction.arrowPlacement(), direction.textColor(), direction.arrowColor(), direction.fontSize() + 0.01F, direction.align(), direction.overflow()))));
            y += 23;
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.text.short"), direction.textColor(), ColorTarget.TEXT, mouseX, mouseY);
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 82, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.accent.short"), direction.arrowColor(), ColorTarget.ACCENT, mouseX, mouseY);
            return y + 25;
        }
        if (settings instanceof ProjectionComponentSettings.PlatformStatusTags tags) {
            toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 80, 18), Component.translatable("screen.superpipeslide.projection_designer.terminal"), tags.showTerminal(), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformStatusTags(!tags.showTerminal(), tags.showLoop(), tags.showBidirectional(), tags.showTransfer(), tags.showMissingLine(), tags.scope(), tags.align(), tags.fillColor(), tags.textColor(), tags.fontSize(), tags.gap()))));
            toggle(graphics, new SPSGui.Rect(pop.x() + 94, y, 80, 18), Component.translatable("screen.superpipeslide.projection_designer.loop"), tags.showLoop(), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformStatusTags(tags.showTerminal(), !tags.showLoop(), tags.showBidirectional(), tags.showTransfer(), tags.showMissingLine(), tags.scope(), tags.align(), tags.fillColor(), tags.textColor(), tags.fontSize(), tags.gap()))));
            y += 21;
            toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 80, 18), Component.translatable("screen.superpipeslide.projection_designer.bidirectional"), tags.showBidirectional(), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformStatusTags(tags.showTerminal(), tags.showLoop(), !tags.showBidirectional(), tags.showTransfer(), tags.showMissingLine(), tags.scope(), tags.align(), tags.fillColor(), tags.textColor(), tags.fontSize(), tags.gap()))));
            toggle(graphics, new SPSGui.Rect(pop.x() + 94, y, 80, 18), Component.translatable("screen.superpipeslide.projection_designer.transfer"), tags.showTransfer(), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformStatusTags(tags.showTerminal(), tags.showLoop(), tags.showBidirectional(), !tags.showTransfer(), tags.showMissingLine(), tags.scope(), tags.align(), tags.fillColor(), tags.textColor(), tags.fontSize(), tags.gap()))));
            y += 21;
            toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 100, 18), Component.translatable("screen.superpipeslide.projection_designer.missing_line"), tags.showMissingLine(), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformStatusTags(tags.showTerminal(), tags.showLoop(), tags.showBidirectional(), tags.showTransfer(), !tags.showMissingLine(), tags.scope(), tags.align(), tags.fillColor(), tags.textColor(), tags.fontSize(), tags.gap()))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.status_scope"), localizedPlatform(tags.scope()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformStatusTags(tags.showTerminal(), tags.showLoop(), tags.showBidirectional(), tags.showTransfer(), tags.showMissingLine(), nextEnum(tags.scope()), tags.align(), tags.fillColor(), tags.textColor(), tags.fontSize(), tags.gap()))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.align"), localized(tags.align()), mouseX, mouseY,
                    () -> replace(component.withSettings(tags.withAlign(next(tags.align())))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.font_size"), format(tags.fontSize()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformStatusTags(tags.showTerminal(), tags.showLoop(), tags.showBidirectional(), tags.showTransfer(), tags.showMissingLine(), tags.scope(), tags.align(), tags.fillColor(), tags.textColor(), tags.fontSize() - 0.005F, tags.gap()))),
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformStatusTags(tags.showTerminal(), tags.showLoop(), tags.showBidirectional(), tags.showTransfer(), tags.showMissingLine(), tags.scope(), tags.align(), tags.fillColor(), tags.textColor(), tags.fontSize() + 0.005F, tags.gap()))));
            y += 23;
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.text.short"), tags.textColor(), ColorTarget.TEXT, mouseX, mouseY);
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 82, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.background.short"), tags.fillColor(), ColorTarget.FILL, mouseX, mouseY);
            return y + 25;
        }
        if (settings instanceof ProjectionComponentSettings.PlatformLine line) {
            if (line.type() == ProjectionComponentType.PLATFORM_LINE_CURRENT) {
                propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.direction"), localized(line.direction()), mouseX, mouseY,
                        () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLine(line.type(), line.style(), next(line.direction()), line.lineWidth(), line.nodeSize(), line.nodeStyle(), line.showLabel(), line.textColor(), line.fontSize(), line.overflow()))));
                y += 21;
                propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.node_style"), localizedPlatform(line.nodeStyle()), mouseX, mouseY,
                        () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLine(line.type(), line.style(), line.direction(), line.lineWidth(), line.nodeSize(), nextEnum(line.nodeStyle()), line.showLabel(), line.textColor(), line.fontSize(), line.overflow()))));
                y += 21;
            }
            toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 84, 18), Component.translatable("screen.superpipeslide.projection_designer.show_label"), line.showLabel(), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLine(line.type(), line.style(), line.direction(), line.lineWidth(), line.nodeSize(), line.nodeStyle(), !line.showLabel(), line.textColor(), line.fontSize(), line.overflow()))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.line_width"), format(line.lineWidth()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLine(line.type(), line.style(), line.direction(), line.lineWidth() - 0.005F, line.nodeSize(), line.nodeStyle(), line.showLabel(), line.textColor(), line.fontSize(), line.overflow()))),
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLine(line.type(), line.style(), line.direction(), line.lineWidth() + 0.005F, line.nodeSize(), line.nodeStyle(), line.showLabel(), line.textColor(), line.fontSize(), line.overflow()))));
            y += 21;
            if (line.type() == ProjectionComponentType.PLATFORM_LINE_CURRENT && line.nodeStyle() != ProjectionComponentSettings.PlatformNodeStyle.NONE) {
                numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.node_size"), format(line.nodeSize()), mouseX, mouseY,
                        () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLine(line.type(), line.style(), line.direction(), line.lineWidth(), line.nodeSize() - 0.01F, line.nodeStyle(), line.showLabel(), line.textColor(), line.fontSize(), line.overflow()))),
                        () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLine(line.type(), line.style(), line.direction(), line.lineWidth(), line.nodeSize() + 0.01F, line.nodeStyle(), line.showLabel(), line.textColor(), line.fontSize(), line.overflow()))));
                y += 21;
            }
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.font_size"), format(line.fontSize()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLine(line.type(), line.style(), line.direction(), line.lineWidth(), line.nodeSize(), line.nodeStyle(), line.showLabel(), line.textColor(), line.fontSize() - 0.005F, line.overflow()))),
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLine(line.type(), line.style(), line.direction(), line.lineWidth(), line.nodeSize(), line.nodeStyle(), line.showLabel(), line.textColor(), line.fontSize() + 0.005F, line.overflow()))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.overflow"), localized(line.overflow()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLine(line.type(), line.style(), line.direction(), line.lineWidth(), line.nodeSize(), line.nodeStyle(), line.showLabel(), line.textColor(), line.fontSize(), next(line.overflow())))));
            y += 23;
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.text.short"), line.textColor(), ColorTarget.TEXT, mouseX, mouseY);
            return y + 25;
        }
        if (settings instanceof ProjectionComponentSettings.PlatformLineIcon icon) {
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.icon_shape"), localized(icon.shape()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLineIcon(next(icon.shape()), icon.outline(), icon.useLineColor(), icon.fillColor(), icon.borderColor(), icon.textColor(), icon.iconSize(), icon.fontSize(), icon.borderWidth(), icon.ringThicknessRatio(), icon.showLabel()))));
            y += 21;
            toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 70, 18), Component.translatable("screen.superpipeslide.projection_designer.outline"), icon.outline(), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLineIcon(icon.shape(), !icon.outline(), icon.useLineColor(), icon.fillColor(), icon.borderColor(), icon.textColor(), icon.iconSize(), icon.fontSize(), icon.borderWidth(), icon.ringThicknessRatio(), icon.showLabel()))));
            toggle(graphics, new SPSGui.Rect(pop.x() + 84, y, 96, 18), Component.translatable("screen.superpipeslide.projection_designer.use_line_color"), icon.useLineColor(), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLineIcon(icon.shape(), icon.outline(), !icon.useLineColor(), icon.fillColor(), icon.borderColor(), icon.textColor(), icon.iconSize(), icon.fontSize(), icon.borderWidth(), icon.ringThicknessRatio(), icon.showLabel()))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.icon_size"), format(icon.iconSize()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLineIcon(icon.shape(), icon.outline(), icon.useLineColor(), icon.fillColor(), icon.borderColor(), icon.textColor(), icon.iconSize() - 0.01F, icon.fontSize(), icon.borderWidth(), icon.ringThicknessRatio(), icon.showLabel()))),
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLineIcon(icon.shape(), icon.outline(), icon.useLineColor(), icon.fillColor(), icon.borderColor(), icon.textColor(), icon.iconSize() + 0.01F, icon.fontSize(), icon.borderWidth(), icon.ringThicknessRatio(), icon.showLabel()))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.font_size"), format(icon.fontSize()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLineIcon(icon.shape(), icon.outline(), icon.useLineColor(), icon.fillColor(), icon.borderColor(), icon.textColor(), icon.iconSize(), icon.fontSize() - 0.005F, icon.borderWidth(), icon.ringThicknessRatio(), icon.showLabel()))),
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLineIcon(icon.shape(), icon.outline(), icon.useLineColor(), icon.fillColor(), icon.borderColor(), icon.textColor(), icon.iconSize(), icon.fontSize() + 0.005F, icon.borderWidth(), icon.ringThicknessRatio(), icon.showLabel()))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.border_width"), format(icon.borderWidth()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLineIcon(icon.shape(), icon.outline(), icon.useLineColor(), icon.fillColor(), icon.borderColor(), icon.textColor(), icon.iconSize(), icon.fontSize(), icon.borderWidth() - 0.005F, icon.ringThicknessRatio(), icon.showLabel()))),
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLineIcon(icon.shape(), icon.outline(), icon.useLineColor(), icon.fillColor(), icon.borderColor(), icon.textColor(), icon.iconSize(), icon.fontSize(), icon.borderWidth() + 0.005F, icon.ringThicknessRatio(), icon.showLabel()))));
            y += 21;
            if (icon.outline()) {
                numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.ring_thickness"), format(icon.ringThicknessRatio()), mouseX, mouseY,
                        () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLineIcon(icon.shape(), icon.outline(), icon.useLineColor(), icon.fillColor(), icon.borderColor(), icon.textColor(), icon.iconSize(), icon.fontSize(), icon.borderWidth(), icon.ringThicknessRatio() - 0.02F, icon.showLabel()))),
                        () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLineIcon(icon.shape(), icon.outline(), icon.useLineColor(), icon.fillColor(), icon.borderColor(), icon.textColor(), icon.iconSize(), icon.fontSize(), icon.borderWidth(), icon.ringThicknessRatio() + 0.02F, icon.showLabel()))));
                y += 21;
            }
            toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 84, 18), Component.translatable("screen.superpipeslide.projection_designer.show_label"), icon.showLabel(), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformLineIcon(icon.shape(), icon.outline(), icon.useLineColor(), icon.fillColor(), icon.borderColor(), icon.textColor(), icon.iconSize(), icon.fontSize(), icon.borderWidth(), icon.ringThicknessRatio(), !icon.showLabel()))));
            y += 23;
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.text.short"), icon.textColor(), ColorTarget.TEXT, mouseX, mouseY);
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 82, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.background.short"), icon.fillColor(), ColorTarget.FILL, mouseX, mouseY);
            y += 23;
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.border.short"), icon.borderColor(), ColorTarget.BORDER, mouseX, mouseY);
            return y + 25;
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTransferList transfer) {
            y = platformTransferCommonRows(graphics, pop, y, component, transfer.maxVisible(), transfer.overflow(), transfer.includeOutStation(), transfer.showStation(), transfer.showPlatform(), transfer.textColor(), transfer.plusTextColor(), transfer.fillColor(), transfer.fontSize(), transfer.gap(), transfer.rotateIntervalTicks(), mouseX, mouseY);
            return y;
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTransferMatrix matrix) {
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.columns"), Integer.toString(matrix.columns()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformTransferMatrix(matrix.columns() - 1, matrix.maxVisible(), matrix.overflow(), matrix.includeOutStation(), matrix.showStation(), matrix.showPlatform(), matrix.textColor(), matrix.plusTextColor(), matrix.fillColor(), matrix.fontSize(), matrix.gap(), matrix.rotateIntervalTicks()))),
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformTransferMatrix(matrix.columns() + 1, matrix.maxVisible(), matrix.overflow(), matrix.includeOutStation(), matrix.showStation(), matrix.showPlatform(), matrix.textColor(), matrix.plusTextColor(), matrix.fillColor(), matrix.fontSize(), matrix.gap(), matrix.rotateIntervalTicks()))));
            y += 21;
            y = platformTransferCommonRows(graphics, pop, y, component, matrix.maxVisible(), matrix.overflow(), matrix.includeOutStation(), matrix.showStation(), matrix.showPlatform(), matrix.textColor(), matrix.plusTextColor(), matrix.fillColor(), matrix.fontSize(), matrix.gap(), matrix.rotateIntervalTicks(), mouseX, mouseY);
            return y;
        }
        if (settings instanceof ProjectionComponentSettings.PlatformLayoutMap map) {
            toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 82, 18), Component.translatable("screen.superpipeslide.projection_designer.show_stop_names"), map.showStopNames(), mouseX, mouseY,
                    () -> replace(component.withSettings(map.withShowStopNames(!map.showStopNames()))));
            toggle(graphics, new SPSGui.Rect(pop.x() + 96, y, 82, 18), Component.translatable("screen.superpipeslide.projection_designer.show_transfer"), map.showTransferMarks(), mouseX, mouseY,
                    () -> replace(component.withSettings(map.withShowTransferMarks(!map.showTransferMarks()))));
            y += 21;
            toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 128, 18), Component.translatable("screen.superpipeslide.projection_designer.follow_direction"), map.followProjectionDirection(), mouseX, mouseY,
                    () -> replace(component.withSettings(map.withFollowProjectionDirection(!map.followProjectionDirection()))));
            y += 21;
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.node_style"), localizedPlatform(map.nodeStyle()), mouseX, mouseY,
                    () -> replace(component.withSettings(map.withNodeStyle(nextEnum(map.nodeStyle())))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.font_size"), format(map.fontSize()), mouseX, mouseY,
                    () -> replace(component.withSettings(map.withFontSize(map.fontSize() - 0.005F))),
                    () -> replace(component.withSettings(map.withFontSize(map.fontSize() + 0.005F))));
            y += 21;
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.line_width"), format(map.lineWidth()), mouseX, mouseY,
                    () -> replace(component.withSettings(map.withLineWidth(map.lineWidth() - 0.005F))),
                    () -> replace(component.withSettings(map.withLineWidth(map.lineWidth() + 0.005F))));
            y += 21;
            if (map.nodeStyle() != ProjectionComponentSettings.PlatformNodeStyle.NONE) {
                numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.node_size"), format(map.nodeSize()), mouseX, mouseY,
                        () -> replace(component.withSettings(map.withNodeSize(map.nodeSize() - 0.01F))),
                        () -> replace(component.withSettings(map.withNodeSize(map.nodeSize() + 0.01F))));
                y += 21;
            }
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.label_overflow"), localized(map.labelOverflow()), mouseX, mouseY,
                    () -> replace(component.withSettings(map.withLabelOverflow(next(map.labelOverflow())))));
            y += 23;
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.text.short"), map.textColor(), ColorTarget.TEXT, mouseX, mouseY);
            drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 82, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.accent.short"), map.lineColor(), ColorTarget.ACCENT, mouseX, mouseY);
            return y + 25;
        }
        return y;
    }

    private int platformTransferCommonRows(GuiGraphicsExtractor graphics, SPSGui.Rect pop, int y, ProjectionComponent component, int maxVisible, ProjectionComponentSettings.RouteOverflowMode overflow, boolean includeOutStation, boolean showStation, boolean showPlatform, int textColor, int plusTextColor, int fillColor, float fontSize, float gap, int rotateIntervalTicks, int mouseX, int mouseY) {
        ProjectionComponentSettings settings = component.settings();
        numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.max_visible"), Integer.toString(maxVisible), mouseX, mouseY,
                () -> replace(component.withSettings(platformTransferWithMaxVisible(settings, maxVisible - 1))),
                () -> replace(component.withSettings(platformTransferWithMaxVisible(settings, maxVisible + 1))));
        y += 21;
        if (settings instanceof ProjectionComponentSettings.PlatformTransferList list) {
            propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.flow"), localized(list.flow()), mouseX, mouseY,
                    () -> replace(component.withSettings(new ProjectionComponentSettings.PlatformTransferList(list.maxVisible(), next(list.flow()), list.overflow(), list.includeOutStation(), list.showStation(), list.showPlatform(), list.textColor(), list.plusTextColor(), list.fillColor(), list.fontSize(), list.gap(), list.rotateIntervalTicks()))));
            y += 21;
        }
        propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.overflow"), localized(overflow), mouseX, mouseY,
                () -> replace(component.withSettings(platformTransferWithOverflow(settings, next(overflow)))));
        y += 21;
        if (overflow == ProjectionComponentSettings.RouteOverflowMode.ROTATE) {
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.rotate_interval"), Integer.toString(rotateIntervalTicks), mouseX, mouseY,
                    () -> replace(component.withSettings(platformTransferWithRotateInterval(settings, rotateIntervalTicks - 5))),
                    () -> replace(component.withSettings(platformTransferWithRotateInterval(settings, rotateIntervalTicks + 5))));
            y += 21;
        }
        toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 88, 18), Component.translatable("screen.superpipeslide.projection_designer.include_out_station"), includeOutStation, mouseX, mouseY,
                () -> replace(component.withSettings(platformTransferWithIncludeOutStation(settings, !includeOutStation))));
        toggle(graphics, new SPSGui.Rect(pop.x() + 102, y, 70, 18), Component.translatable("screen.superpipeslide.projection_designer.show_station"), showStation, mouseX, mouseY,
                () -> replace(component.withSettings(platformTransferWithShowStation(settings, !showStation))));
        y += 21;
        toggle(graphics, new SPSGui.Rect(pop.x() + 9, y, 92, 18), Component.translatable("screen.superpipeslide.projection_designer.show_platform"), showPlatform, mouseX, mouseY,
                () -> replace(component.withSettings(platformTransferWithShowPlatform(settings, !showPlatform))));
        y += 21;
        numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.font_size"), format(fontSize), mouseX, mouseY,
                () -> replace(component.withSettings(platformTransferWithFontSize(settings, fontSize - 0.005F))),
                () -> replace(component.withSettings(platformTransferWithFontSize(settings, fontSize + 0.005F))));
        y += 21;
        numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.gap"), format(gap), mouseX, mouseY,
                () -> replace(component.withSettings(platformTransferWithGap(settings, gap - 0.005F))),
                () -> replace(component.withSettings(platformTransferWithGap(settings, gap + 0.005F))));
        y += 23;
        drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 68, 18), Component.translatable("screen.superpipeslide.projection_designer.color.text.short"), textColor, ColorTarget.TEXT, mouseX, mouseY);
        drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 82, y, 76, 18), Component.translatable("screen.superpipeslide.projection_designer.color.plus.short"), plusTextColor, ColorTarget.PLUS, mouseX, mouseY);
        y += 23;
        drawColorSwatch(graphics, new SPSGui.Rect(pop.x() + 9, y, 76, 18), Component.translatable("screen.superpipeslide.projection_designer.color.background.short"), fillColor, ColorTarget.FILL, mouseX, mouseY);
        return y + 25;
    }

    private int routeCommonRows(GuiGraphicsExtractor graphics, SPSGui.Rect pop, int y, ProjectionComponent component, float fontSize, int maxVisible, ProjectionComponentSettings.RouteOverflowMode overflow, int plusTextColor, int mouseX, int mouseY, boolean includeFontSize) {
        ProjectionComponentSettings settings = component.settings();
        if (includeFontSize) {
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.font_size"), format(fontSize), mouseX, mouseY,
                    () -> replace(component.withSettings(routeWithFontSize(settings, fontSize - 0.005F))),
                    () -> replace(component.withSettings(routeWithFontSize(settings, fontSize + 0.005F))));
            y += 21;
        }
        if (maxVisible > 1 || settings instanceof ProjectionComponentSettings.RouteBackplate || settings instanceof ProjectionComponentSettings.RouteList || settings instanceof ProjectionComponentSettings.RouteIcon || settings instanceof ProjectionComponentSettings.RouteCapsules) {
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.max_visible"), Integer.toString(maxVisible), mouseX, mouseY,
                    () -> replace(component.withSettings(routeWithMaxVisible(settings, maxVisible - 1))),
                    () -> replace(component.withSettings(routeWithMaxVisible(settings, maxVisible + 1))));
            y += 21;
        }
        propertyRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.overflow"), localized(overflow), mouseX, mouseY, () -> replace(component.withSettings(routeWithOverflow(settings, next(overflow)))));
        y += 21;
        if (overflow == ProjectionComponentSettings.RouteOverflowMode.ROTATE) {
            int interval = routeRotateInterval(settings);
            numberRow(graphics, pop, y, Component.translatable("screen.superpipeslide.projection_designer.rotate_interval"), Integer.toString(interval), mouseX, mouseY,
                    () -> replace(component.withSettings(routeWithRotateInterval(settings, interval - 5))),
                    () -> replace(component.withSettings(routeWithRotateInterval(settings, interval + 5))));
            y += 21;
        }
        return y;
    }

    private void clampPropertiesPosition(int width, int height) {
        int minX = this.panel.x() + 6;
        int minY = this.panel.y() + 6;
        int maxX = this.panel.right() - width - 6;
        int maxY = this.panel.bottom() - height - 6;
        if (maxX < minX) {
            maxX = minX;
        }
        if (maxY < minY) {
            maxY = minY;
        }
        this.propertiesX = Math.max(minX, Math.min(maxX, this.propertiesX));
        this.propertiesY = Math.max(minY, Math.min(maxY, this.propertiesY));
    }

    private void drawColorSwatch(GuiGraphicsExtractor graphics, SPSGui.Rect rect, Component label, int color, ColorTarget target, int mouseX, int mouseY) {
        if (this.propertiesBounds.width() > 0 && (rect.y() > this.propertiesBounds.bottom() - 7 || rect.bottom() < this.propertiesBounds.y() + 28)) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), this.colorTarget == target ? ACTIVE : BG);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), this.colorTarget == target ? ACCENT : 0xFF324248);
        graphics.fill(rect.x() + 3, rect.y() + 3, rect.x() + 15, rect.bottom() - 3, color);
        graphics.outline(rect.x() + 3, rect.y() + 3, 12, rect.height() - 6, 0x88333B40);
        SPSGui.smallText(graphics, this.font, label.getString(), rect.x() + 19, rect.y() + 6, rect.contains(mouseX, mouseY) ? TEXT : MUTED, 0.58F);
        this.addPriorityClick(rect, () -> this.colorTarget = target, label);
    }

    private void drawIconPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ProjectionComponent component = selected();
        if (component == null || !(component.settings() instanceof ProjectionComponentSettings.BuiltinIcon settings)) {
            this.iconPickerOpen = false;
            this.iconPickerBounds = new SPSGui.Rect(0, 0, 0, 0);
            return;
        }
        int width = Math.min(236, Math.max(172, this.stage.width() - 18));
        int height = Math.min(236, Math.max(132, this.stage.height() - 18));
        int x = Math.max(this.panel.x() + 6, Math.min(this.panel.right() - width - 6, this.propertiesBounds.x() - width - 8));
        if (x + width > this.propertiesBounds.x() - 4) {
            x = Math.max(this.panel.x() + 6, this.propertiesBounds.right() - width);
        }
        int y = Math.max(this.panel.y() + 6, Math.min(this.panel.bottom() - height - 6, this.propertiesBounds.y()));
        this.iconPickerBounds = new SPSGui.Rect(x, y, width, height);
        this.iconPickerScroll = Math.max(0.0D, Math.min(maxIconPickerScroll(), this.iconPickerScroll));
        graphics.fill(x, y, x + width, y + height, 0xF60E1215);
        graphics.outline(x, y, width, height, ACCENT);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 24, 0xFF253238);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.icon_picker"), x + 9, y + 8, TEXT);
        SPSGui.Rect close = new SPSGui.Rect(x + width - 25, y + 4, 19, 18);
        darkIcon(graphics, close, SPSGui.Icon.CLOSE, mouseX, mouseY, MUTED);
        this.addPriorityClick(close, () -> this.iconPickerOpen = false, Component.translatable("screen.superpipeslide.action.cancel"));

        SPSGui.Rect viewport = new SPSGui.Rect(x + 7, y + 29, width - 14, height - 36);
        int cell = 34;
        int columns = Math.max(1, viewport.width() / cell);
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        List<ProjectionBuiltinIcon> icons = ProjectionBuiltinIcon.all();
        int rowCount = (int) Math.ceil(icons.size() / (double) columns);
        for (int i = 0; i < icons.size(); i++) {
            ProjectionBuiltinIcon icon = icons.get(i);
            int column = i % columns;
            int row = i / columns;
            int cellX = viewport.x() + column * cell;
            int cellY = viewport.y() + row * cell - (int) this.iconPickerScroll;
            SPSGui.Rect item = new SPSGui.Rect(cellX + 2, cellY + 2, cell - 4, cell - 4);
            if (item.bottom() < viewport.y() || item.y() > viewport.bottom()) {
                continue;
            }
            boolean selected = icon.id().equals(settings.iconId());
            boolean hovered = item.contains(mouseX, mouseY);
            graphics.fill(item.x(), item.y(), item.right(), item.bottom(), selected ? ACTIVE : hovered ? CHROME_HIGH : CHROME);
            graphics.outline(item.x(), item.y(), item.width(), item.height(), selected ? ACCENT : hovered ? 0xFF4D6368 : 0xFF324248);
            ProjectionBuiltinIconTextureCache.IconTexture texture = ProjectionBuiltinIconTextureCache.textureFor(icon, settings.withIconId(icon.id()).withFitMode(ProjectionComponentSettings.ImageFitMode.CONTAIN));
            ProjectionImageLayout.Resolved resolved = ProjectionImageLayout.resolveIcon(item.x() + 3, item.y() + 3, item.width() - 6, item.height() - 6, ProjectionComponentSettings.ImageFitMode.CONTAIN, ProjectionComponentSettings.ImageAnchor.CENTER, 0.0F, 0.75F);
            drawPickerIcon(graphics, texture, resolved);
            this.addPriorityClick(item, () -> {
                replace(component.withSettings(settings.withIconId(icon.id())));
                this.iconPickerOpen = false;
            }, Component.translatable(icon.translationKey()));
            if (hovered) {
                graphics.setTooltipForNextFrame(this.font, Component.translatable(icon.translationKey()), mouseX, mouseY);
            }
        }
        graphics.disableScissor();
        if (this.iconPickerScroll > 0.5D) {
            graphics.fill(viewport.x(), viewport.y(), viewport.right(), viewport.y() + 5, 0xDD0E1215);
        }
        if (this.iconPickerScroll < Math.max(0.0D, rowCount * cell - viewport.height()) - 0.5D) {
            graphics.fill(viewport.x(), viewport.bottom() - 5, viewport.right(), viewport.bottom(), 0xDD0E1215);
        }
    }

    private void drawPickerIcon(GuiGraphicsExtractor graphics, ProjectionBuiltinIconTextureCache.IconTexture texture, ProjectionImageLayout.Resolved resolved) {
        dev.marblegate.superpipeslide.client.fullmap.render.SmoothGuiPrimitives.texturedQuad(graphics, texture.textureId(),
                new dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2(resolved.x(), resolved.y()),
                new dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2(resolved.x() + resolved.width(), resolved.y()),
                new dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2(resolved.x() + resolved.width(), resolved.y() + resolved.height()),
                new dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2(resolved.x(), resolved.y() + resolved.height()),
                texture.color(), texture.u0(), texture.u1(), texture.v0(), texture.v1());
    }

    private double maxIconPickerScroll() {
        if (!this.iconPickerOpen || this.iconPickerBounds.height() <= 0) {
            return 0.0D;
        }
        int viewportWidth = Math.max(1, this.iconPickerBounds.width() - 14);
        int cell = 34;
        int columns = Math.max(1, viewportWidth / cell);
        int rows = (int) Math.ceil(ProjectionBuiltinIcon.all().size() / (double) columns);
        int viewportHeight = Math.max(1, this.iconPickerBounds.height() - 36);
        return Math.max(0.0D, rows * cell - viewportHeight);
    }

    private void drawColorPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ProjectionComponent component = selected();
        if (component == null || this.colorTarget == null) {
            return;
        }
        int pickerWidth = 206;
        int pickerHeight = 132;
        int x = Math.max(this.stage.x() + 8, this.stage.right() - pickerWidth - 244);
        int y = Math.max(this.stage.y() + 8, this.stage.bottom() - pickerHeight - 10);
        this.colorPickerBounds = new SPSGui.Rect(x, y, pickerWidth, pickerHeight);
        graphics.fill(x - 4, y - 4, x + pickerWidth + 4, y + pickerHeight + 4, 0xED20292E);
        graphics.outline(x - 4, y - 4, pickerWidth + 8, pickerHeight + 8, ACCENT);
        SPSGui.colorPicker(graphics, this.colorPickerBounds, selectedColor(component), mouseX, mouseY);
        SPSGui.smallText(graphics, this.font, colorTargetLabel().getString(), x + 28, y + 7, TEXT, 0.62F);
        String hex = "#" + String.format(java.util.Locale.ROOT, "%06X", selectedColor(component) & 0x00FFFFFF);
        SPSGui.smallText(graphics, this.font, hex, x + 100, y + 7, MUTED, 0.58F);
    }

    private Component colorTargetLabel() {
        return switch (this.colorTarget) {
            case TEXT -> Component.translatable("screen.superpipeslide.projection_designer.color.text");
            case PRIMARY_TEXT -> Component.translatable("screen.superpipeslide.projection_designer.color.primary_text");
            case TRANSLATION_TEXT -> Component.translatable("screen.superpipeslide.projection_designer.color.translation_text");
            case FILL -> Component.translatable("screen.superpipeslide.projection_designer.color.background");
            case BORDER -> Component.translatable("screen.superpipeslide.projection_designer.color.border");
            case ACCENT -> Component.translatable("screen.superpipeslide.projection_designer.color.accent");
            case PLUS -> Component.translatable("screen.superpipeslide.projection_designer.color.plus");
            case TINT -> Component.translatable("screen.superpipeslide.projection_designer.color.tint");
            case SECONDARY -> Component.translatable("screen.superpipeslide.projection_designer.color.secondary");
        };
    }

    private int selectedColor(ProjectionComponent component) {
        ProjectionComponentSettings settings = component.settings();
        return switch (this.colorTarget) {
            case TEXT -> textColor(settings);
            case PRIMARY_TEXT -> primaryTextColor(settings);
            case TRANSLATION_TEXT -> translationTextColor(settings);
            case FILL -> fillColor(settings);
            case BORDER -> borderColor(settings);
            case ACCENT -> accentColor(settings);
            case PLUS -> plusColor(settings);
            case TINT -> tintColor(settings);
            case SECONDARY -> secondaryColor(settings);
        };
    }

    private void applySelectedColor(double mouseX, double mouseY) {
        ProjectionComponent component = selected();
        if (component == null || this.colorTarget == null) {
            return;
        }
        int original = selectedColor(component);
        int picked = SPSGui.colorFromPicker(this.colorPickerBounds, original, mouseX, mouseY);
        int color = (original & 0xFF000000) | (picked & 0x00FFFFFF);
        replace(component.withSettings(settingsWithColor(component.settings(), this.colorTarget, color)));
    }

    private ProjectionComponentSettings settingsWithColor(ProjectionComponentSettings settings, ColorTarget target, int color) {
        if (settings instanceof ProjectionComponentSettings.Text text && target == ColorTarget.TEXT) {
            return text.withTextColor(color);
        }
        if (settings instanceof ProjectionComponentSettings.StationTitleGroup title) {
            if (target == ColorTarget.PRIMARY_TEXT) {
                return title.withPrimaryColor(color);
            }
            if (target == ColorTarget.TRANSLATION_TEXT) {
                return title.withTranslationColor(color);
            }
        }
        if (settings instanceof ProjectionComponentSettings.Panel panel) {
            if (target == ColorTarget.FILL) {
                return new ProjectionComponentSettings.Panel(color, panel.borderColor(), panel.borderWidth(), panel.opacity());
            }
            if (target == ColorTarget.BORDER) {
                return new ProjectionComponentSettings.Panel(panel.fillColor(), color, panel.borderWidth(), panel.opacity());
            }
        }
        if (settings instanceof ProjectionComponentSettings.ExitBadge exit) {
            if (target == ColorTarget.TEXT) {
                return new ProjectionComponentSettings.ExitBadge(exit.fillEnabled(), exit.borderEnabled(), exit.fillColor(), exit.borderColor(), color, exit.fontSize());
            }
            if (target == ColorTarget.FILL) {
                return new ProjectionComponentSettings.ExitBadge(exit.fillEnabled(), exit.borderEnabled(), color, exit.borderColor(), exit.textColor(), exit.fontSize());
            }
            if (target == ColorTarget.BORDER) {
                return new ProjectionComponentSettings.ExitBadge(exit.fillEnabled(), exit.borderEnabled(), exit.fillColor(), color, exit.textColor(), exit.fontSize());
            }
        }
        if (settings instanceof ProjectionComponentSettings.Divider divider && target == ColorTarget.ACCENT) {
            return new ProjectionComponentSettings.Divider(color, divider.thickness(), divider.dashed());
        }
        if (settings instanceof ProjectionComponentSettings.RouteList list) {
            if (target == ColorTarget.TEXT) {
                return new ProjectionComponentSettings.RouteList(list.rowHeight(), list.gap(), list.stripeWidth(), list.fontSize(), list.maxVisible(), list.overflow(), list.labelOverflow(), color, list.plusTextColor(), list.rotateIntervalTicks());
            }
            if (target == ColorTarget.PLUS) {
                return new ProjectionComponentSettings.RouteList(list.rowHeight(), list.gap(), list.stripeWidth(), list.fontSize(), list.maxVisible(), list.overflow(), list.labelOverflow(), list.textColor(), color, list.rotateIntervalTicks());
            }
        }
        if (settings instanceof ProjectionComponentSettings.RouteText text) {
            if (target == ColorTarget.TEXT) {
                return new ProjectionComponentSettings.RouteText(text.fontSize(), text.overflow(), text.shortName(), color, text.plusTextColor(), text.align(), text.rotateIntervalTicks());
            }
            if (target == ColorTarget.PLUS) {
                return new ProjectionComponentSettings.RouteText(text.fontSize(), text.overflow(), text.shortName(), text.textColor(), color, text.align(), text.rotateIntervalTicks());
            }
        }
        if (settings instanceof ProjectionComponentSettings.RouteIcon icon) {
            if (target == ColorTarget.TEXT) {
                return icon.withTextColor(color);
            }
            if (target == ColorTarget.BORDER) {
                return icon.withBorderColor(color);
            }
            if (target == ColorTarget.PLUS) {
                return icon.withPlusTextColor(color);
            }
        }
        if (settings instanceof ProjectionComponentSettings.RouteCapsules capsules) {
            if (target == ColorTarget.TEXT) {
                return new ProjectionComponentSettings.RouteCapsules(capsules.capsuleWidth(), capsules.capsuleHeight(), capsules.gap(), capsules.fontSize(), capsules.maxVisible(), capsules.flow(), capsules.contentOrientation(), capsules.overflow(), capsules.labelOverflow(), capsules.showShortName(), color, capsules.fillColor(), capsules.plusTextColor(), capsules.rotateIntervalTicks());
            }
            if (target == ColorTarget.FILL) {
                return new ProjectionComponentSettings.RouteCapsules(capsules.capsuleWidth(), capsules.capsuleHeight(), capsules.gap(), capsules.fontSize(), capsules.maxVisible(), capsules.flow(), capsules.contentOrientation(), capsules.overflow(), capsules.labelOverflow(), capsules.showShortName(), capsules.textColor(), color, capsules.plusTextColor(), capsules.rotateIntervalTicks());
            }
            if (target == ColorTarget.PLUS) {
                return new ProjectionComponentSettings.RouteCapsules(capsules.capsuleWidth(), capsules.capsuleHeight(), capsules.gap(), capsules.fontSize(), capsules.maxVisible(), capsules.flow(), capsules.contentOrientation(), capsules.overflow(), capsules.labelOverflow(), capsules.showShortName(), capsules.textColor(), capsules.fillColor(), color, capsules.rotateIntervalTicks());
            }
        }
        if (settings instanceof ProjectionComponentSettings.RouteBackplate backplate && target == ColorTarget.PLUS) {
            return new ProjectionComponentSettings.RouteBackplate(backplate.direction(), backplate.colorPolicy(), backplate.maxVisible(), backplate.opacity(), backplate.overflow(), color, backplate.rotateIntervalTicks());
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTitleGroup title) {
            if (target == ColorTarget.PRIMARY_TEXT) {
                return new ProjectionComponentSettings.PlatformTitleGroup(title.content(), color, title.secondaryColor(), title.primaryFontSize(), title.secondaryFontSize(), title.gap(), title.align(), title.primaryOverflow(), title.secondaryOverflow(), title.orientation(), title.missingSecondaryMode(), title.missingPrimaryScale());
            }
            if (target == ColorTarget.TRANSLATION_TEXT) {
                return new ProjectionComponentSettings.PlatformTitleGroup(title.content(), title.primaryColor(), color, title.primaryFontSize(), title.secondaryFontSize(), title.gap(), title.align(), title.primaryOverflow(), title.secondaryOverflow(), title.orientation(), title.missingSecondaryMode(), title.missingPrimaryScale());
            }
        }
        if (settings instanceof ProjectionComponentSettings.PlatformBadge badge) {
            if (target == ColorTarget.TEXT) {
                return new ProjectionComponentSettings.PlatformBadge(badge.style(), badge.useLineColor(), badge.fillColor(), badge.borderColor(), color, badge.fontSize(), badge.borderWidth(), badge.prefix(), badge.suffix());
            }
            if (target == ColorTarget.FILL) {
                return new ProjectionComponentSettings.PlatformBadge(badge.style(), badge.useLineColor(), color, badge.borderColor(), badge.textColor(), badge.fontSize(), badge.borderWidth(), badge.prefix(), badge.suffix());
            }
            if (target == ColorTarget.BORDER) {
                return new ProjectionComponentSettings.PlatformBadge(badge.style(), badge.useLineColor(), badge.fillColor(), color, badge.textColor(), badge.fontSize(), badge.borderWidth(), badge.prefix(), badge.suffix());
            }
        }
        if (settings instanceof ProjectionComponentSettings.PlatformDirection direction) {
            if (target == ColorTarget.TEXT) {
                return new ProjectionComponentSettings.PlatformDirection(direction.source(), direction.prefix(), direction.arrow(), direction.arrowPlacement(), color, direction.arrowColor(), direction.fontSize(), direction.align(), direction.overflow());
            }
            if (target == ColorTarget.ACCENT) {
                return new ProjectionComponentSettings.PlatformDirection(direction.source(), direction.prefix(), direction.arrow(), direction.arrowPlacement(), direction.textColor(), color, direction.fontSize(), direction.align(), direction.overflow());
            }
        }
        if (settings instanceof ProjectionComponentSettings.PlatformStatusTags tags) {
            if (target == ColorTarget.TEXT) {
                return new ProjectionComponentSettings.PlatformStatusTags(tags.showTerminal(), tags.showLoop(), tags.showBidirectional(), tags.showTransfer(), tags.showMissingLine(), tags.scope(), tags.align(), tags.fillColor(), color, tags.fontSize(), tags.gap());
            }
            if (target == ColorTarget.FILL) {
                return new ProjectionComponentSettings.PlatformStatusTags(tags.showTerminal(), tags.showLoop(), tags.showBidirectional(), tags.showTransfer(), tags.showMissingLine(), tags.scope(), tags.align(), color, tags.textColor(), tags.fontSize(), tags.gap());
            }
        }
        if (settings instanceof ProjectionComponentSettings.PlatformLine line && target == ColorTarget.TEXT) {
            return new ProjectionComponentSettings.PlatformLine(line.type(), line.style(), line.direction(), line.lineWidth(), line.nodeSize(), line.nodeStyle(), line.showLabel(), color, line.fontSize(), line.overflow());
        }
        if (settings instanceof ProjectionComponentSettings.PlatformLineIcon icon) {
            if (target == ColorTarget.TEXT) {
                return new ProjectionComponentSettings.PlatformLineIcon(icon.shape(), icon.outline(), icon.useLineColor(), icon.fillColor(), icon.borderColor(), color, icon.iconSize(), icon.fontSize(), icon.borderWidth(), icon.ringThicknessRatio(), icon.showLabel());
            }
            if (target == ColorTarget.FILL) {
                return new ProjectionComponentSettings.PlatformLineIcon(icon.shape(), icon.outline(), icon.useLineColor(), color, icon.borderColor(), icon.textColor(), icon.iconSize(), icon.fontSize(), icon.borderWidth(), icon.ringThicknessRatio(), icon.showLabel());
            }
            if (target == ColorTarget.BORDER) {
                return new ProjectionComponentSettings.PlatformLineIcon(icon.shape(), icon.outline(), icon.useLineColor(), icon.fillColor(), color, icon.textColor(), icon.iconSize(), icon.fontSize(), icon.borderWidth(), icon.ringThicknessRatio(), icon.showLabel());
            }
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTransferList transfer) {
            if (target == ColorTarget.TEXT) {
                return new ProjectionComponentSettings.PlatformTransferList(transfer.maxVisible(), transfer.flow(), transfer.overflow(), transfer.includeOutStation(), transfer.showStation(), transfer.showPlatform(), color, transfer.plusTextColor(), transfer.fillColor(), transfer.fontSize(), transfer.gap(), transfer.rotateIntervalTicks());
            }
            if (target == ColorTarget.PLUS) {
                return new ProjectionComponentSettings.PlatformTransferList(transfer.maxVisible(), transfer.flow(), transfer.overflow(), transfer.includeOutStation(), transfer.showStation(), transfer.showPlatform(), transfer.textColor(), color, transfer.fillColor(), transfer.fontSize(), transfer.gap(), transfer.rotateIntervalTicks());
            }
            if (target == ColorTarget.FILL) {
                return new ProjectionComponentSettings.PlatformTransferList(transfer.maxVisible(), transfer.flow(), transfer.overflow(), transfer.includeOutStation(), transfer.showStation(), transfer.showPlatform(), transfer.textColor(), transfer.plusTextColor(), color, transfer.fontSize(), transfer.gap(), transfer.rotateIntervalTicks());
            }
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTransferMatrix transfer) {
            if (target == ColorTarget.TEXT) {
                return new ProjectionComponentSettings.PlatformTransferMatrix(transfer.columns(), transfer.maxVisible(), transfer.overflow(), transfer.includeOutStation(), transfer.showStation(), transfer.showPlatform(), color, transfer.plusTextColor(), transfer.fillColor(), transfer.fontSize(), transfer.gap(), transfer.rotateIntervalTicks());
            }
            if (target == ColorTarget.PLUS) {
                return new ProjectionComponentSettings.PlatformTransferMatrix(transfer.columns(), transfer.maxVisible(), transfer.overflow(), transfer.includeOutStation(), transfer.showStation(), transfer.showPlatform(), transfer.textColor(), color, transfer.fillColor(), transfer.fontSize(), transfer.gap(), transfer.rotateIntervalTicks());
            }
            if (target == ColorTarget.FILL) {
                return new ProjectionComponentSettings.PlatformTransferMatrix(transfer.columns(), transfer.maxVisible(), transfer.overflow(), transfer.includeOutStation(), transfer.showStation(), transfer.showPlatform(), transfer.textColor(), transfer.plusTextColor(), color, transfer.fontSize(), transfer.gap(), transfer.rotateIntervalTicks());
            }
        }
        if (settings instanceof ProjectionComponentSettings.PlatformLayoutMap map) {
            if (target == ColorTarget.TEXT) {
                return map.withTextColor(color);
            }
            if (target == ColorTarget.ACCENT) {
                return map.withLineColor(color);
            }
        }
        if (settings instanceof ProjectionComponentSettings.BuiltinIcon icon) {
            if (target == ColorTarget.TINT) {
                return icon.withTintColor(color);
            }
            if (target == ColorTarget.SECONDARY) {
                return icon.withSecondaryColor(color);
            }
            if (target == ColorTarget.FILL) {
                return icon.withBackgroundColor(color);
            }
            if (target == ColorTarget.BORDER) {
                return icon.withBorderColor(color);
            }
        }
        if (settings instanceof ProjectionComponentSettings.NetworkImage image) {
            if (target == ColorTarget.FILL) {
                return image.withBackgroundColor(color);
            }
            if (target == ColorTarget.BORDER) {
                return image.withBorderColor(color);
            }
        }
        return settings;
    }

    private int textColor(ProjectionComponentSettings settings) {
        if (settings instanceof ProjectionComponentSettings.Text text) {
            return text.textColor();
        }
        if (settings instanceof ProjectionComponentSettings.ExitBadge exit) {
            return exit.textColor();
        }
        if (settings instanceof ProjectionComponentSettings.RouteList list) {
            return list.textColor();
        }
        if (settings instanceof ProjectionComponentSettings.RouteText text) {
            return text.textColor();
        }
        if (settings instanceof ProjectionComponentSettings.RouteIcon icon) {
            return icon.textColor();
        }
        if (settings instanceof ProjectionComponentSettings.RouteCapsules capsules) {
            return capsules.textColor();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformBadge badge) {
            return badge.textColor();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformDirection direction) {
            return direction.textColor();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformStatusTags tags) {
            return tags.textColor();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformLine line) {
            return line.textColor();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformLineIcon icon) {
            return icon.textColor();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTransferList transfer) {
            return transfer.textColor();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTransferMatrix transfer) {
            return transfer.textColor();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformLayoutMap map) {
            return map.textColor();
        }
        return 0xFFFFFFFF;
    }

    private int primaryTextColor(ProjectionComponentSettings settings) {
        if (settings instanceof ProjectionComponentSettings.StationTitleGroup title) {
            return title.primaryColor();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTitleGroup title) {
            return title.primaryColor();
        }
        return textColor(settings);
    }

    private int translationTextColor(ProjectionComponentSettings settings) {
        if (settings instanceof ProjectionComponentSettings.StationTitleGroup title) {
            return title.translationColor();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTitleGroup title) {
            return title.secondaryColor();
        }
        return textColor(settings);
    }

    private int fillColor(ProjectionComponentSettings settings) {
        if (settings instanceof ProjectionComponentSettings.Panel panel) {
            return panel.fillColor();
        }
        if (settings instanceof ProjectionComponentSettings.ExitBadge exit) {
            return exit.fillColor();
        }
        if (settings instanceof ProjectionComponentSettings.RouteCapsules capsules) {
            return capsules.fillColor();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformBadge badge) {
            return badge.fillColor();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformStatusTags tags) {
            return tags.fillColor();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformLineIcon icon) {
            return icon.fillColor();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTransferList transfer) {
            return transfer.fillColor();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTransferMatrix transfer) {
            return transfer.fillColor();
        }
        if (settings instanceof ProjectionComponentSettings.BuiltinIcon icon) {
            return icon.backgroundColor();
        }
        if (settings instanceof ProjectionComponentSettings.NetworkImage image) {
            return image.backgroundColor();
        }
        return 0xFF20292E;
    }

    private int borderColor(ProjectionComponentSettings settings) {
        if (settings instanceof ProjectionComponentSettings.Panel panel) {
            return panel.borderColor();
        }
        if (settings instanceof ProjectionComponentSettings.ExitBadge exit) {
            return exit.borderColor();
        }
        if (settings instanceof ProjectionComponentSettings.RouteIcon icon) {
            return icon.borderColor();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformBadge badge) {
            return badge.borderColor();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformLineIcon icon) {
            return icon.borderColor();
        }
        if (settings instanceof ProjectionComponentSettings.BuiltinIcon icon) {
            return icon.borderColor();
        }
        if (settings instanceof ProjectionComponentSettings.NetworkImage image) {
            return image.borderColor();
        }
        return 0xFF4B5C62;
    }

    private int accentColor(ProjectionComponentSettings settings) {
        if (settings instanceof ProjectionComponentSettings.Divider divider) {
            return divider.color();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformDirection direction) {
            return direction.arrowColor();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformLayoutMap map) {
            return map.lineColor();
        }
        return 0xFF37C3BB;
    }

    private int plusColor(ProjectionComponentSettings settings) {
        if (settings instanceof ProjectionComponentSettings.RouteList list) {
            return list.plusTextColor();
        }
        if (settings instanceof ProjectionComponentSettings.RouteText text) {
            return text.plusTextColor();
        }
        if (settings instanceof ProjectionComponentSettings.RouteIcon icon) {
            return icon.plusTextColor();
        }
        if (settings instanceof ProjectionComponentSettings.RouteCapsules capsules) {
            return capsules.plusTextColor();
        }
        if (settings instanceof ProjectionComponentSettings.RouteBackplate backplate) {
            return backplate.plusTextColor();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTransferList transfer) {
            return transfer.plusTextColor();
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTransferMatrix transfer) {
            return transfer.plusTextColor();
        }
        return 0xFFFFFFFF;
    }

    private int tintColor(ProjectionComponentSettings settings) {
        if (settings instanceof ProjectionComponentSettings.BuiltinIcon icon) {
            return icon.tintColor();
        }
        return 0xFFFFFFFF;
    }

    private int secondaryColor(ProjectionComponentSettings settings) {
        if (settings instanceof ProjectionComponentSettings.BuiltinIcon icon) {
            return icon.secondaryColor();
        }
        return 0xFF37C3BB;
    }

    private ProjectionComponentSettings routeWithFontSize(ProjectionComponentSettings settings, float fontSize) {
        if (settings instanceof ProjectionComponentSettings.RouteList list) {
            return new ProjectionComponentSettings.RouteList(list.rowHeight(), list.gap(), list.stripeWidth(), fontSize, list.maxVisible(), list.overflow(), list.labelOverflow(), list.textColor(), list.plusTextColor(), list.rotateIntervalTicks());
        }
        if (settings instanceof ProjectionComponentSettings.RouteText text) {
            return new ProjectionComponentSettings.RouteText(fontSize, text.overflow(), text.shortName(), text.textColor(), text.plusTextColor(), text.align(), text.rotateIntervalTicks());
        }
        if (settings instanceof ProjectionComponentSettings.RouteIcon icon) {
            return icon.withFontSize(fontSize);
        }
        if (settings instanceof ProjectionComponentSettings.RouteCapsules capsules) {
            return new ProjectionComponentSettings.RouteCapsules(capsules.capsuleWidth(), capsules.capsuleHeight(), capsules.gap(), fontSize, capsules.maxVisible(), capsules.flow(), capsules.contentOrientation(), capsules.overflow(), capsules.labelOverflow(), capsules.showShortName(), capsules.textColor(), capsules.fillColor(), capsules.plusTextColor(), capsules.rotateIntervalTicks());
        }
        return settings;
    }

    private ProjectionComponentSettings routeWithMaxVisible(ProjectionComponentSettings settings, int maxVisible) {
        if (settings instanceof ProjectionComponentSettings.RouteList list) {
            return new ProjectionComponentSettings.RouteList(list.rowHeight(), list.gap(), list.stripeWidth(), list.fontSize(), maxVisible, list.overflow(), list.labelOverflow(), list.textColor(), list.plusTextColor(), list.rotateIntervalTicks());
        }
        if (settings instanceof ProjectionComponentSettings.RouteIcon icon) {
            return icon.withMaxVisible(maxVisible);
        }
        if (settings instanceof ProjectionComponentSettings.RouteCapsules capsules) {
            return new ProjectionComponentSettings.RouteCapsules(capsules.capsuleWidth(), capsules.capsuleHeight(), capsules.gap(), capsules.fontSize(), maxVisible, capsules.flow(), capsules.contentOrientation(), capsules.overflow(), capsules.labelOverflow(), capsules.showShortName(), capsules.textColor(), capsules.fillColor(), capsules.plusTextColor(), capsules.rotateIntervalTicks());
        }
        if (settings instanceof ProjectionComponentSettings.RouteBackplate backplate) {
            return new ProjectionComponentSettings.RouteBackplate(backplate.direction(), backplate.colorPolicy(), maxVisible, backplate.opacity(), backplate.overflow(), backplate.plusTextColor(), backplate.rotateIntervalTicks());
        }
        return settings;
    }

    private ProjectionComponentSettings routeWithOverflow(ProjectionComponentSettings settings, ProjectionComponentSettings.RouteOverflowMode overflow) {
        if (settings instanceof ProjectionComponentSettings.RouteList list) {
            return new ProjectionComponentSettings.RouteList(list.rowHeight(), list.gap(), list.stripeWidth(), list.fontSize(), list.maxVisible(), overflow, list.labelOverflow(), list.textColor(), list.plusTextColor(), list.rotateIntervalTicks());
        }
        if (settings instanceof ProjectionComponentSettings.RouteText text) {
            return new ProjectionComponentSettings.RouteText(text.fontSize(), overflow, text.shortName(), text.textColor(), text.plusTextColor(), text.align(), text.rotateIntervalTicks());
        }
        if (settings instanceof ProjectionComponentSettings.RouteIcon icon) {
            return icon.withOverflow(overflow);
        }
        if (settings instanceof ProjectionComponentSettings.RouteCapsules capsules) {
            return new ProjectionComponentSettings.RouteCapsules(capsules.capsuleWidth(), capsules.capsuleHeight(), capsules.gap(), capsules.fontSize(), capsules.maxVisible(), capsules.flow(), capsules.contentOrientation(), overflow, capsules.labelOverflow(), capsules.showShortName(), capsules.textColor(), capsules.fillColor(), capsules.plusTextColor(), capsules.rotateIntervalTicks());
        }
        if (settings instanceof ProjectionComponentSettings.RouteBackplate backplate) {
            return new ProjectionComponentSettings.RouteBackplate(backplate.direction(), backplate.colorPolicy(), backplate.maxVisible(), backplate.opacity(), overflow, backplate.plusTextColor(), backplate.rotateIntervalTicks());
        }
        return settings;
    }

    private int routeRotateInterval(ProjectionComponentSettings settings) {
        if (settings instanceof ProjectionComponentSettings.RouteList list) {
            return list.rotateIntervalTicks();
        }
        if (settings instanceof ProjectionComponentSettings.RouteText text) {
            return text.rotateIntervalTicks();
        }
        if (settings instanceof ProjectionComponentSettings.RouteIcon icon) {
            return icon.rotateIntervalTicks();
        }
        if (settings instanceof ProjectionComponentSettings.RouteCapsules capsules) {
            return capsules.rotateIntervalTicks();
        }
        if (settings instanceof ProjectionComponentSettings.RouteBackplate backplate) {
            return backplate.rotateIntervalTicks();
        }
        return 35;
    }

    private ProjectionComponentSettings routeWithRotateInterval(ProjectionComponentSettings settings, int interval) {
        if (settings instanceof ProjectionComponentSettings.RouteList list) {
            return new ProjectionComponentSettings.RouteList(list.rowHeight(), list.gap(), list.stripeWidth(), list.fontSize(), list.maxVisible(), list.overflow(), list.labelOverflow(), list.textColor(), list.plusTextColor(), interval);
        }
        if (settings instanceof ProjectionComponentSettings.RouteText text) {
            return new ProjectionComponentSettings.RouteText(text.fontSize(), text.overflow(), text.shortName(), text.textColor(), text.plusTextColor(), text.align(), interval);
        }
        if (settings instanceof ProjectionComponentSettings.RouteIcon icon) {
            return icon.withRotateIntervalTicks(interval);
        }
        if (settings instanceof ProjectionComponentSettings.RouteCapsules capsules) {
            return new ProjectionComponentSettings.RouteCapsules(capsules.capsuleWidth(), capsules.capsuleHeight(), capsules.gap(), capsules.fontSize(), capsules.maxVisible(), capsules.flow(), capsules.contentOrientation(), capsules.overflow(), capsules.labelOverflow(), capsules.showShortName(), capsules.textColor(), capsules.fillColor(), capsules.plusTextColor(), interval);
        }
        if (settings instanceof ProjectionComponentSettings.RouteBackplate backplate) {
            return new ProjectionComponentSettings.RouteBackplate(backplate.direction(), backplate.colorPolicy(), backplate.maxVisible(), backplate.opacity(), backplate.overflow(), backplate.plusTextColor(), interval);
        }
        return settings;
    }

    private ProjectionComponentSettings platformTransferWithMaxVisible(ProjectionComponentSettings settings, int maxVisible) {
        if (settings instanceof ProjectionComponentSettings.PlatformTransferList list) {
            return new ProjectionComponentSettings.PlatformTransferList(maxVisible, list.flow(), list.overflow(), list.includeOutStation(), list.showStation(), list.showPlatform(), list.textColor(), list.plusTextColor(), list.fillColor(), list.fontSize(), list.gap(), list.rotateIntervalTicks());
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTransferMatrix matrix) {
            return new ProjectionComponentSettings.PlatformTransferMatrix(matrix.columns(), maxVisible, matrix.overflow(), matrix.includeOutStation(), matrix.showStation(), matrix.showPlatform(), matrix.textColor(), matrix.plusTextColor(), matrix.fillColor(), matrix.fontSize(), matrix.gap(), matrix.rotateIntervalTicks());
        }
        return settings;
    }

    private ProjectionComponentSettings platformTransferWithOverflow(ProjectionComponentSettings settings, ProjectionComponentSettings.RouteOverflowMode overflow) {
        if (settings instanceof ProjectionComponentSettings.PlatformTransferList list) {
            return new ProjectionComponentSettings.PlatformTransferList(list.maxVisible(), list.flow(), overflow, list.includeOutStation(), list.showStation(), list.showPlatform(), list.textColor(), list.plusTextColor(), list.fillColor(), list.fontSize(), list.gap(), list.rotateIntervalTicks());
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTransferMatrix matrix) {
            return new ProjectionComponentSettings.PlatformTransferMatrix(matrix.columns(), matrix.maxVisible(), overflow, matrix.includeOutStation(), matrix.showStation(), matrix.showPlatform(), matrix.textColor(), matrix.plusTextColor(), matrix.fillColor(), matrix.fontSize(), matrix.gap(), matrix.rotateIntervalTicks());
        }
        return settings;
    }

    private ProjectionComponentSettings platformTransferWithRotateInterval(ProjectionComponentSettings settings, int interval) {
        if (settings instanceof ProjectionComponentSettings.PlatformTransferList list) {
            return new ProjectionComponentSettings.PlatformTransferList(list.maxVisible(), list.flow(), list.overflow(), list.includeOutStation(), list.showStation(), list.showPlatform(), list.textColor(), list.plusTextColor(), list.fillColor(), list.fontSize(), list.gap(), interval);
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTransferMatrix matrix) {
            return new ProjectionComponentSettings.PlatformTransferMatrix(matrix.columns(), matrix.maxVisible(), matrix.overflow(), matrix.includeOutStation(), matrix.showStation(), matrix.showPlatform(), matrix.textColor(), matrix.plusTextColor(), matrix.fillColor(), matrix.fontSize(), matrix.gap(), interval);
        }
        return settings;
    }

    private ProjectionComponentSettings platformTransferWithIncludeOutStation(ProjectionComponentSettings settings, boolean value) {
        if (settings instanceof ProjectionComponentSettings.PlatformTransferList list) {
            return new ProjectionComponentSettings.PlatformTransferList(list.maxVisible(), list.flow(), list.overflow(), value, list.showStation(), list.showPlatform(), list.textColor(), list.plusTextColor(), list.fillColor(), list.fontSize(), list.gap(), list.rotateIntervalTicks());
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTransferMatrix matrix) {
            return new ProjectionComponentSettings.PlatformTransferMatrix(matrix.columns(), matrix.maxVisible(), matrix.overflow(), value, matrix.showStation(), matrix.showPlatform(), matrix.textColor(), matrix.plusTextColor(), matrix.fillColor(), matrix.fontSize(), matrix.gap(), matrix.rotateIntervalTicks());
        }
        return settings;
    }

    private ProjectionComponentSettings platformTransferWithShowStation(ProjectionComponentSettings settings, boolean value) {
        if (settings instanceof ProjectionComponentSettings.PlatformTransferList list) {
            return new ProjectionComponentSettings.PlatformTransferList(list.maxVisible(), list.flow(), list.overflow(), list.includeOutStation(), value, list.showPlatform(), list.textColor(), list.plusTextColor(), list.fillColor(), list.fontSize(), list.gap(), list.rotateIntervalTicks());
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTransferMatrix matrix) {
            return new ProjectionComponentSettings.PlatformTransferMatrix(matrix.columns(), matrix.maxVisible(), matrix.overflow(), matrix.includeOutStation(), value, matrix.showPlatform(), matrix.textColor(), matrix.plusTextColor(), matrix.fillColor(), matrix.fontSize(), matrix.gap(), matrix.rotateIntervalTicks());
        }
        return settings;
    }

    private ProjectionComponentSettings platformTransferWithShowPlatform(ProjectionComponentSettings settings, boolean value) {
        if (settings instanceof ProjectionComponentSettings.PlatformTransferList list) {
            return new ProjectionComponentSettings.PlatformTransferList(list.maxVisible(), list.flow(), list.overflow(), list.includeOutStation(), list.showStation(), value, list.textColor(), list.plusTextColor(), list.fillColor(), list.fontSize(), list.gap(), list.rotateIntervalTicks());
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTransferMatrix matrix) {
            return new ProjectionComponentSettings.PlatformTransferMatrix(matrix.columns(), matrix.maxVisible(), matrix.overflow(), matrix.includeOutStation(), matrix.showStation(), value, matrix.textColor(), matrix.plusTextColor(), matrix.fillColor(), matrix.fontSize(), matrix.gap(), matrix.rotateIntervalTicks());
        }
        return settings;
    }

    private ProjectionComponentSettings platformTransferWithFontSize(ProjectionComponentSettings settings, float fontSize) {
        if (settings instanceof ProjectionComponentSettings.PlatformTransferList list) {
            return new ProjectionComponentSettings.PlatformTransferList(list.maxVisible(), list.flow(), list.overflow(), list.includeOutStation(), list.showStation(), list.showPlatform(), list.textColor(), list.plusTextColor(), list.fillColor(), fontSize, list.gap(), list.rotateIntervalTicks());
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTransferMatrix matrix) {
            return new ProjectionComponentSettings.PlatformTransferMatrix(matrix.columns(), matrix.maxVisible(), matrix.overflow(), matrix.includeOutStation(), matrix.showStation(), matrix.showPlatform(), matrix.textColor(), matrix.plusTextColor(), matrix.fillColor(), fontSize, matrix.gap(), matrix.rotateIntervalTicks());
        }
        return settings;
    }

    private ProjectionComponentSettings platformTransferWithGap(ProjectionComponentSettings settings, float gap) {
        if (settings instanceof ProjectionComponentSettings.PlatformTransferList list) {
            return new ProjectionComponentSettings.PlatformTransferList(list.maxVisible(), list.flow(), list.overflow(), list.includeOutStation(), list.showStation(), list.showPlatform(), list.textColor(), list.plusTextColor(), list.fillColor(), list.fontSize(), gap, list.rotateIntervalTicks());
        }
        if (settings instanceof ProjectionComponentSettings.PlatformTransferMatrix matrix) {
            return new ProjectionComponentSettings.PlatformTransferMatrix(matrix.columns(), matrix.maxVisible(), matrix.overflow(), matrix.includeOutStation(), matrix.showStation(), matrix.showPlatform(), matrix.textColor(), matrix.plusTextColor(), matrix.fillColor(), matrix.fontSize(), gap, matrix.rotateIntervalTicks());
        }
        return settings;
    }

    private void propertyRow(GuiGraphicsExtractor graphics, SPSGui.Rect pop, int y, Component label, Component value, int mouseX, int mouseY, Runnable action) {
        if (y > pop.bottom() - 7 || y + 17 < pop.y() + 28) {
            return;
        }
        SPSGui.smallText(graphics, this.font, label.getString(), pop.x() + 9, y + 5, MUTED, 0.66F);
        SPSGui.Rect button = new SPSGui.Rect(pop.x() + 73, y, pop.width() - 82, 17);
        darkTextButton(graphics, button, value, mouseX, mouseY, TEXT);
        this.addClick(button, action, label);
    }

    private void numberRow(GuiGraphicsExtractor graphics, SPSGui.Rect pop, int y, Component label, String value, int mouseX, int mouseY, Runnable minus, Runnable plus) {
        if (y > pop.bottom() - 7 || y + 17 < pop.y() + 28) {
            return;
        }
        SPSGui.smallText(graphics, this.font, label.getString(), pop.x() + 9, y + 5, MUTED, 0.66F);
        SPSGui.Rect minusRect = new SPSGui.Rect(pop.right() - 77, y, 18, 17);
        SPSGui.Rect valueRect = new SPSGui.Rect(pop.right() - 57, y, 36, 17);
        SPSGui.Rect plusRect = new SPSGui.Rect(pop.right() - 19, y, 18, 17);
        compactButton(graphics, minusRect, "-", mouseX, mouseY, minus);
        darkTextButton(graphics, valueRect, Component.literal(value), mouseX, mouseY, TEXT);
        compactButton(graphics, plusRect, "+", mouseX, mouseY, plus);
    }

    private void showComponentTextBox(ProjectionComponent component, int x, int y, int width) {
        if (this.componentTextBox == null) {
            return;
        }
        hideNetworkUrlBox();
        this.componentTextBox.visible = true;
        this.componentTextBox.active = true;
        this.componentTextBox.setX(x);
        this.componentTextBox.setY(y);
        this.componentTextBox.setWidth(width);
        if (!component.id().equals(this.textBindingId)) {
            this.textBindingId = component.id();
            this.componentTextBox.setValue(component.text());
        }
    }

    private void hideComponentTextBox() {
        if (this.componentTextBox != null) {
            this.componentTextBox.visible = false;
            this.componentTextBox.active = false;
        }
        this.textBindingId = null;
    }

    private void showNetworkUrlBox(ProjectionComponent component, SPSGui.Rect pop, int y, int x, int width) {
        if (this.networkUrlBox == null) {
            return;
        }
        hideComponentTextBox();
        int contentTop = pop.y() + 28;
        int contentBottom = pop.bottom() - 4;
        if (y < contentTop || y + 15 > contentBottom) {
            hideNetworkUrlBox();
            return;
        }
        this.networkUrlBox.visible = true;
        this.networkUrlBox.active = true;
        this.networkUrlBox.setX(x);
        this.networkUrlBox.setY(y);
        this.networkUrlBox.setWidth(width);
        if (!component.id().equals(this.networkUrlBindingId)) {
            this.networkUrlBindingId = component.id();
            String url = component.settings() instanceof ProjectionComponentSettings.NetworkImage image ? image.url() : "";
            this.networkUrlBox.setValue(url);
        }
    }

    private void hideNetworkUrlBox() {
        if (this.networkUrlBox != null) {
            this.networkUrlBox.visible = false;
            this.networkUrlBox.active = false;
        }
        this.networkUrlBindingId = null;
    }

    private void updateSelectedText(String value) {
        ProjectionComponent component = selected();
        if (component != null && component.id().equals(this.textBindingId)) {
            replace(component.withText(value));
        }
    }

    private void updateSelectedNetworkUrl(String value) {
        ProjectionComponent component = selected();
        if (component != null && component.id().equals(this.networkUrlBindingId) && component.settings() instanceof ProjectionComponentSettings.NetworkImage image) {
            replace(component.withSettings(image.withUrl(value)));
        }
    }

    private void darkIcon(GuiGraphicsExtractor graphics, SPSGui.Rect rect, SPSGui.Icon icon, int mouseX, int mouseY, int color) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), rect.contains(mouseX, mouseY) ? CHROME_HIGH : CHROME);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), rect.contains(mouseX, mouseY) ? 0xFF4D6368 : 0xFF324248);
        SPSGui.icon(graphics, rect, icon, color);
    }

    private void darkTextButton(GuiGraphicsExtractor graphics, SPSGui.Rect rect, Component label, int mouseX, int mouseY, int color) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), rect.contains(mouseX, mouseY) ? CHROME_HIGH : BG);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), rect.contains(mouseX, mouseY) ? ACCENT : 0xFF324248);
        SPSGui.centeredText(graphics, this.font, SPSGui.ellipsize(this.font, label.getString(), rect.width() - 6), rect.x() + rect.width() / 2, rect.y() + (rect.height() - 8) / 2, color);
    }

    private void compactButton(GuiGraphicsExtractor graphics, SPSGui.Rect rect, String label, int mouseX, int mouseY, Runnable action) {
        darkTextButton(graphics, rect, Component.literal(label), mouseX, mouseY, MUTED);
        this.addClick(rect, action, Component.literal(label));
    }

    private void compactLetterButton(GuiGraphicsExtractor graphics, SPSGui.Rect rect, String label, int mouseX, int mouseY) {
        darkTextButton(graphics, rect, Component.literal(label), mouseX, mouseY, MUTED);
    }

    private void toggle(GuiGraphicsExtractor graphics, SPSGui.Rect rect, Component label, boolean active, int mouseX, int mouseY, Runnable action) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), active ? ACTIVE : BG);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), active ? ACCENT : 0xFF324248);
        SPSGui.centeredText(graphics, this.font, SPSGui.ellipsize(this.font, label.getString(), rect.width() - 6), rect.x() + rect.width() / 2, rect.y() + 5, active ? TEXT : MUTED);
        this.addClick(rect, action, label);
    }

    @Override
    protected void addClick(SPSGui.Rect bounds, Runnable action, Component tooltip) {
        this.layeredClickActions.add(new LayeredClickAction(this.clickLayer, bounds, action, tooltip));
        super.addClick(bounds, action, tooltip);
    }

    @Override
    protected void addPriorityClick(SPSGui.Rect bounds, Runnable action, Component tooltip) {
        this.layeredClickActions.add(0, new LayeredClickAction(this.clickLayer, bounds, action, tooltip));
        super.addPriorityClick(bounds, action, tooltip);
    }

    @Override
    protected void renderTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.confirmationOpen()) {
            super.renderTooltips(graphics, mouseX, mouseY);
            return;
        }
        ClickLayer layer = tooltipLayerAt(mouseX, mouseY);
        if (layer == ClickLayer.BASE) {
            super.renderTooltips(graphics, mouseX, mouseY);
            return;
        }
        for (LayeredClickAction clickAction : List.copyOf(this.layeredClickActions)) {
            if (clickAction.layer() == layer && clickAction.bounds().contains(mouseX, mouseY)) {
                RouteEditorGui.documentTooltip(graphics, this.font, clickAction.tooltip(), mouseX, mouseY, this.panel);
                return;
            }
        }
    }

    private ClickLayer tooltipLayerAt(double mouseX, double mouseY) {
        if (this.colorTarget != null) {
            return ClickLayer.COLOR_PICKER;
        }
        if (this.iconPickerOpen) {
            return ClickLayer.ICON_PICKER;
        }
        if (this.propertiesOpen && this.propertiesBounds.contains(mouseX, mouseY)) {
            return ClickLayer.PROPERTIES;
        }
        if (this.previewOpen && this.scenarioPopoverBounds.contains(mouseX, mouseY)) {
            return ClickLayer.SCENARIO;
        }
        if (this.canvasSettingsOpen && this.canvasSettingsBounds.contains(mouseX, mouseY)) {
            return ClickLayer.CANVAS_SETTINGS;
        }
        if (this.addMenuOpen && this.addMenuBounds.contains(mouseX, mouseY)) {
            return ClickLayer.ADD_MENU;
        }
        if (this.layersOpen && this.layerPanel.contains(mouseX, mouseY)) {
            return ClickLayer.LAYERS;
        }
        return ClickLayer.BASE;
    }

    private SPSGui.Rect canvasRect() {
        int width = Math.max(1, Math.round(this.draft.canvas().width() * this.zoom));
        int height = Math.max(1, Math.round(this.draft.canvas().height() * this.zoom));
        return new SPSGui.Rect(this.stage.x() + (this.stage.width() - width) / 2 + (int) Math.round(this.panX),
                this.stage.y() + (this.stage.height() - height) / 2 + (int) Math.round(this.panY), width, height);
    }

    private void fitCanvas() {
        float xScale = (this.stage.width() - 62.0F) / Math.max(0.001F, this.draft.canvas().width());
        float yScale = (this.stage.height() - 62.0F) / Math.max(0.001F, this.draft.canvas().height());
        this.zoom = Math.max(12.0F, Math.min(260.0F, Math.min(xScale, yScale)));
        this.panX = 0;
        this.panY = 0;
    }

    private void setCanvasSize(float width, float height, boolean syncBoxes) {
        ProjectionCanvas next = new ProjectionCanvas(width, height);
        if (Math.abs(next.width() - this.draft.canvas().width()) < 0.0005F && Math.abs(next.height() - this.draft.canvas().height()) < 0.0005F) {
            return;
        }
        this.draft = this.draft.withCanvas(next);
        markDirty();
        if (syncBoxes) {
            syncCanvasSizeBoxes();
        }
    }

    private void applyCanvasBoxValue(boolean widthField, String value) {
        Optional<Float> parsed = parseCanvasSize(value);
        if (parsed.isEmpty()) {
            return;
        }
        float width = this.draft.canvas().width();
        float height = this.draft.canvas().height();
        if (widthField) {
            width = parsed.get();
        } else {
            height = parsed.get();
        }
        setCanvasSize(snapCanvasSize(width), snapCanvasSize(height), false);
    }

    private void syncCanvasSizeBoxes() {
        if (this.canvasWidthBox != null && !this.canvasWidthBox.isFocused()) {
            this.canvasWidthBox.setValue(formatSize(this.draft.canvas().width()));
        }
        if (this.canvasHeightBox != null && !this.canvasHeightBox.isFocused()) {
            this.canvasHeightBox.setValue(formatSize(this.draft.canvas().height()));
        }
    }

    private static Optional<Float> parseCanvasSize(String value) {
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

    private static float snapCanvasSize(float value) {
        return Math.round(value / CANVAS_SIZE_STEP) * CANVAS_SIZE_STEP;
    }

    private void save() {
        if (!draftValid()) {
            return;
        }
        this.clearUnusedNetworkImages(List.of(), this.draft.components());
        PlatformProjectorRenderer.clearStaticCache();
        StationNameProjectorRenderer.clearStaticCache();
        this.savePending = true;
        this.pendingSaveUpdatedAt = this.draft.updatedAt();
        ClientPacketDistributor.sendToServer(new ServerboundProjectionLayoutSavePayload(this.draft, true));
    }

    private boolean draftValid() {
        return !this.draft.components().isEmpty();
    }

    private void markDirty() {
        this.dirty = true;
        this.savePending = false;
        this.pendingSaveUpdatedAt = 0L;
    }

    private void addComponent(ProjectionComponentType type) {
        List<ProjectionComponent> components = new ArrayList<>(this.draft.components());
        ProjectionComponent component = createComponent(type, this.draft.target(), components.stream().mapToInt(ProjectionComponent::layer).max().orElse(0) + 1);
        components.add(component);
        this.draft = this.draft.withComponents(components);
        markDirty();
        this.selectedId = component.id();
        this.propertiesOpen = true;
        this.propertiesPositionInitialized = false;
        this.propertiesScroll = 0.0D;
    }

    private static ProjectionComponent createComponent(ProjectionComponentType type, ProjectionLayoutTarget target, int layer) {
        return ProjectionComponentDescriptors.create(type, target, layer);
    }

    private void select(UUID id) {
        this.selectedId = id;
        this.propertiesScroll = 0.0D;
    }

    private ProjectionComponent selected() {
        if (this.selectedId == null) {
            return null;
        }
        return this.draft.components().stream().filter(component -> component.id().equals(this.selectedId)).findFirst().orElse(null);
    }

    private void replace(ProjectionComponent replacement) {
        ProjectionComponent current = this.draft.components().stream().filter(component -> component.id().equals(replacement.id())).findFirst().orElse(null);
        if (replacement.equals(current)) {
            return;
        }
        this.draft = this.draft.withComponents(this.draft.components().stream().map(component -> component.id().equals(replacement.id()) ? replacement : component).toList());
        this.clearUnusedNetworkImages(current == null ? List.of() : List.of(current), this.draft.components());
        PlatformProjectorRenderer.clearStaticCache();
        StationNameProjectorRenderer.clearStaticCache();
        markDirty();
    }

    private void deleteComponent(UUID id) {
        List<ProjectionComponent> previous = this.draft.components().stream().filter(component -> component.id().equals(id)).toList();
        List<ProjectionComponent> next = this.draft.components().stream().filter(component -> !component.id().equals(id)).toList();
        this.draft = this.draft.withComponents(next);
        this.clearUnusedNetworkImages(previous, next);
        PlatformProjectorRenderer.clearStaticCache();
        StationNameProjectorRenderer.clearStaticCache();
        markDirty();
        this.selectedId = next.stream().max(Comparator.comparingInt(ProjectionComponent::layer)).map(ProjectionComponent::id).orElse(null);
        this.propertiesOpen = false;
        this.propertiesPositionInitialized = false;
    }

    private void clearUnusedNetworkImages(List<ProjectionComponent> previous, List<ProjectionComponent> current) {
        List<String> currentUrls = networkImageUrls(current);
        for (String url : networkImageUrls(previous)) {
            if (!url.isBlank() && currentUrls.stream().noneMatch(url::equals)) {
                ProjectionNetworkImageCache.clearUrl(url);
            }
        }
    }

    private static List<String> networkImageUrls(List<ProjectionComponent> components) {
        return components.stream()
                .map(ProjectionComponent::settings)
                .filter(ProjectionComponentSettings.NetworkImage.class::isInstance)
                .map(ProjectionComponentSettings.NetworkImage.class::cast)
                .map(ProjectionComponentSettings.NetworkImage::url)
                .map(url -> url == null ? "" : url.trim())
                .filter(url -> !url.isBlank())
                .distinct()
                .toList();
    }

    private List<ProjectionComponent> layersDescending() {
        return this.draft.components().stream().sorted(Comparator.comparingInt(ProjectionComponent::layer).reversed()).toList();
    }

    private int layerIndexAt(double mouseY) {
        int index = (int) Math.floor((mouseY + this.layerScroll - this.layerListArea.y() - 3.0D) / 33.0D);
        return Math.max(0, Math.min(this.draft.components().size(), index));
    }

    private void raise(UUID id) {
        reorder(id, 1);
    }

    private void lower(UUID id) {
        reorder(id, -1);
    }

    private void reorder(UUID id, int delta) {
        List<ProjectionComponent> ordered = new ArrayList<>(this.draft.components().stream().sorted(Comparator.comparingInt(ProjectionComponent::layer)).toList());
        int index = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).id().equals(id)) {
                index = i;
                break;
            }
        }
        int target = Math.max(0, Math.min(ordered.size() - 1, index + delta));
        if (index < 0 || index == target) {
            return;
        }
        ProjectionComponent component = ordered.remove(index);
        ordered.add(target, component);
        List<ProjectionComponent> normalized = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            normalized.add(ordered.get(i).withLayer(i));
        }
        this.draft = this.draft.withComponents(normalized);
        markDirty();
    }

    private void moveLayerTo(UUID id, int descendingTarget) {
        List<ProjectionComponent> descending = new ArrayList<>(layersDescending());
        ProjectionComponent moved = null;
        for (int i = 0; i < descending.size(); i++) {
            if (descending.get(i).id().equals(id)) {
                moved = descending.remove(i);
                break;
            }
        }
        if (moved == null) {
            return;
        }
        int target = Math.max(0, Math.min(descending.size(), descendingTarget));
        descending.add(target, moved);
        List<ProjectionComponent> ascending = new ArrayList<>();
        for (int i = descending.size() - 1; i >= 0; i--) {
            ascending.add(descending.get(i).withLayer(ascending.size()));
        }
        if (ascending.equals(this.draft.components())) {
            return;
        }
        this.draft = this.draft.withComponents(ascending);
        markDirty();
    }

    private int componentCenterX(ProjectionComponent component) {
        return this.doc.x() + Math.round(component.centerX() * this.zoom);
    }

    private int componentCenterY(ProjectionComponent component) {
        return this.doc.y() + Math.round(component.centerY() * this.zoom);
    }

    private int componentWidth(ProjectionComponent component) {
        return Math.max(1, Math.round(component.width() * this.zoom));
    }

    private int componentHeight(ProjectionComponent component) {
        return Math.max(1, Math.round(component.height() * this.zoom));
    }

    private ProjectionComponent hitComponent(double x, double y) {
        for (ProjectionComponent component : layersDescending()) {
            Point local = localPoint(component, x, y);
            if (Math.abs(local.x()) <= componentWidth(component) / 2.0D && Math.abs(local.y()) <= componentHeight(component) / 2.0D) {
                return component;
            }
        }
        return null;
    }

    private ProjectionComponent layerAt(double y) {
        int index = layerIndexAt(y);
        List<ProjectionComponent> descending = layersDescending();
        return index >= 0 && index < descending.size() ? descending.get(index) : null;
    }

    private boolean layerDragHandleContains(double x, double y) {
        if (!this.layerListArea.contains(x, y)) {
            return false;
        }
        int index = layerIndexAt(y);
        int rowY = this.layerListArea.y() + 3 - (int) this.layerScroll + index * 33;
        SPSGui.Rect handle = new SPSGui.Rect(this.layerPanel.x() + 8, rowY + 5, 18, 19);
        return handle.contains(x, y);
    }

    private Handle hitHandle(ProjectionComponent component, double x, double y) {
        if (component == null) {
            return null;
        }
        for (Handle handle : Handle.values()) {
            Point point = worldHandle(component, handle);
            if (Math.hypot(x - point.x(), y - point.y()) <= HANDLE + 2) {
                return handle;
            }
        }
        return null;
    }

    private boolean handleAt(ProjectionComponent component, Handle handle, double x, double y) {
        Point point = worldHandle(component, handle);
        return Math.hypot(x - point.x(), y - point.y()) <= HANDLE + 2;
    }

    private Point worldHandle(ProjectionComponent component, Handle handle) {
        Point local = handle.localPoint(componentWidth(component), componentHeight(component));
        double radians = Math.toRadians(component.rotationDegrees());
        double rx = local.x() * Math.cos(radians) - local.y() * Math.sin(radians);
        double ry = local.x() * Math.sin(radians) + local.y() * Math.cos(radians);
        return new Point(componentCenterX(component) + rx, componentCenterY(component) + ry);
    }

    private Point localPoint(ProjectionComponent component, double x, double y) {
        double dx = x - componentCenterX(component);
        double dy = y - componentCenterY(component);
        double radians = Math.toRadians(-component.rotationDegrees());
        return new Point(dx * Math.cos(radians) - dy * Math.sin(radians), dx * Math.sin(radians) + dy * Math.cos(radians));
    }

    private void handleBox(GuiGraphicsExtractor graphics, double x, double y, boolean hovered) {
        int ix = (int) Math.round(x) - HANDLE / 2;
        int iy = (int) Math.round(y) - HANDLE / 2;
        graphics.fill(ix, iy, ix + HANDLE, iy + HANDLE, hovered ? 0xFFFFFFFF : ACCENT);
        graphics.outline(ix, iy, HANDLE, HANDLE, STAGE);
    }

    private boolean dispatchFloatingTextBoxMouseClicked(MouseButtonEvent event, boolean doubleClick, EditBox... boxes) {
        for (EditBox box : boxes) {
            if (box != null && box.visible && box.active && box.isMouseOver(event.x(), event.y())) {
                focusFloatingTextBox(box);
                box.mouseClicked(event, doubleClick);
                this.setDragging(true);
                this.draggingFloatingTextBox = box;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.confirmationOpen()) {
            return super.mouseClicked(event, doubleClick);
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (this.colorTarget != null) {
                if (SPSGui.colorPickerClose(this.colorPickerBounds).contains(event.x(), event.y())) {
                    this.colorTarget = null;
                    return true;
                }
                if (SPSGui.colorPickerField(this.colorPickerBounds).contains(event.x(), event.y())) {
                    applySelectedColor(event.x(), event.y());
                    return true;
                }
                if (this.colorPickerBounds.contains(event.x(), event.y())) {
                    return true;
                }
                this.colorTarget = null;
                return true;
            }
            if (this.iconPickerOpen) {
                if (this.iconPickerBounds.contains(event.x(), event.y())) {
                    dispatchClickActionsForLayer(ClickLayer.ICON_PICKER, event.x(), event.y());
                    return true;
                }
                this.iconPickerOpen = false;
                return true;
            }
            if (this.propertiesOpen && this.propertiesHeaderBounds.contains(event.x(), event.y())) {
                SPSGui.Rect close = new SPSGui.Rect(this.propertiesBounds.right() - 25, this.propertiesBounds.y() + 5, 19, 19);
                if (!close.contains(event.x(), event.y())) {
                    this.dragMode = DragMode.PROPERTIES;
                    this.dragMouseX = event.x();
                    this.dragMouseY = event.y();
                    return true;
                }
            }
            if (this.colorTarget == null && this.propertiesOpen && this.propertiesBounds.contains(event.x(), event.y())) {
                if (this.dispatchFloatingTextBoxMouseClicked(event, doubleClick, this.componentTextBox, this.networkUrlBox)) {
                    return true;
                }
                if (dispatchClickActionsForLayer(ClickLayer.PROPERTIES, event.x(), event.y())) {
                    return true;
                }
                return true;
            }
            if (this.addMenuOpen && this.addMenuBounds.contains(event.x(), event.y())) {
                dispatchClickActionsForLayer(ClickLayer.ADD_MENU, event.x(), event.y());
                return true;
            }
            if (this.canvasSettingsOpen && this.canvasSettingsBounds.contains(event.x(), event.y())) {
                if (this.dispatchFloatingTextBoxMouseClicked(event, doubleClick, this.canvasWidthBox, this.canvasHeightBox)) {
                    return true;
                }
                if (dispatchClickActionsForLayer(ClickLayer.CANVAS_SETTINGS, event.x(), event.y())) {
                    return true;
                }
                return true;
            }
            if (this.previewOpen && this.scenarioPopoverBounds.contains(event.x(), event.y())) {
                if (this.dispatchFloatingTextBoxMouseClicked(event, doubleClick, this.previewExitBox)) {
                    return true;
                }
                if (dispatchClickActionsForLayer(ClickLayer.SCENARIO, event.x(), event.y())) {
                    return true;
                }
                return true;
            }
            if (layerDragHandleContains(event.x(), event.y())) {
                ProjectionComponent row = layerAt(event.y());
                if (row != null) {
                    this.selectedId = row.id();
                    this.draggingLayerId = row.id();
                    this.draggingLayerTarget = layerIndexAt(event.y());
                    this.dragMouseX = event.x();
                    this.dragMouseY = event.y();
                    return true;
                }
            }
            if (doubleClick && this.layersOpen && this.layerListArea.contains(event.x(), event.y()) && event.x() < this.layerPanel.right() - 42) {
                ProjectionComponent row = layerAt(event.y());
                if (row != null) {
                    this.selectedId = row.id();
                    this.propertiesOpen = true;
                    this.propertiesPositionInitialized = false;
                    this.propertiesScroll = 0.0D;
                    return true;
                }
            }
            if (topLevelClick(event.x(), event.y())) {
                return true;
            }
            if (this.dispatchWidgetMouseClicked(event, doubleClick)) {
                return true;
            }
            if (this.addMenuOpen) {
                this.addMenuOpen = false;
                return true;
            }
            if (this.canvasSettingsOpen && !this.canvasSettingsBounds.contains(event.x(), event.y())) {
                this.canvasSettingsOpen = false;
            }
            if (this.stage.contains(event.x(), event.y())) {
                ProjectionComponent current = selected();
                Handle selectedHandle = hitHandle(current, event.x(), event.y());
                if (current != null && selectedHandle != null) {
                    this.dragMode = selectedHandle.mode();
                    this.dragStart = current;
                    this.dragMouseX = event.x();
                    this.dragMouseY = event.y();
                    return true;
                }
                ProjectionComponent component = hitComponent(event.x(), event.y());
                if (component != null) {
                    this.selectedId = component.id();
                    if (doubleClick) {
                        this.propertiesOpen = true;
                        this.propertiesScroll = 0.0D;
                        return true;
                    }
                    Handle handle = hitHandle(component, event.x(), event.y());
                    this.dragMode = handle == null ? DragMode.MOVE : handle.mode();
                    this.dragStart = component;
                    this.dragMouseX = event.x();
                    this.dragMouseY = event.y();
            } else {
                this.selectedId = null;
                this.propertiesOpen = false;
                clearFloatingTextBoxFocus();
            }
            return true;
        }
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && this.stage.contains(event.x(), event.y())) {
            this.dragMode = DragMode.PAN;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.draggingFloatingTextBox != null) {
            this.draggingFloatingTextBox.mouseDragged(event, dragX, dragY);
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.colorTarget != null && SPSGui.colorPickerField(this.colorPickerBounds).contains(event.x(), event.y())) {
            applySelectedColor(event.x(), event.y());
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.dragMode == DragMode.PROPERTIES) {
            this.propertiesX += event.x() - this.dragMouseX;
            this.propertiesY += event.y() - this.dragMouseY;
            this.dragMouseX = event.x();
            this.dragMouseY = event.y();
            if (this.propertiesBounds.width() > 0 && this.propertiesBounds.height() > 0) {
                clampPropertiesPosition(this.propertiesBounds.width(), this.propertiesBounds.height());
            }
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.propertiesOpen && this.propertiesBounds.contains(event.x(), event.y()) && this.dragMode == DragMode.NONE && this.dragStart == null) {
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.draggingLayerId != null) {
            this.draggingLayerTarget = layerIndexAt(event.y());
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && this.dragMode == DragMode.PAN) {
            this.panX += dragX;
            this.panY += dragY;
            return true;
        }
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || this.dragStart == null || this.dragMode == DragMode.NONE) {
            return super.mouseDragged(event, dragX, dragY);
        }
        double dx = event.x() - this.dragMouseX;
        double dy = event.y() - this.dragMouseY;
        ProjectionComponent start = this.dragStart;
        if (this.dragMode == DragMode.MOVE) {
            float cx = start.centerX() + (float) (dx / Math.max(1.0F, this.zoom));
            float cy = start.centerY() + (float) (dy / Math.max(1.0F, this.zoom));
            replace(start.withTransform(cx, cy, start.width(), start.height(), start.rotationDegrees()));
            return true;
        }
        if (this.dragMode == DragMode.ROTATE) {
            double angle = Math.toDegrees(Math.atan2(event.y() - componentCenterY(start), event.x() - componentCenterX(start))) + 90.0D;
            replace(start.withTransform(start.centerX(), start.centerY(), start.width(), start.height(), (float) angle));
            return true;
        }
        Point local = localPoint(start, event.x(), event.y());
        double startWidth = componentWidth(start);
        double startHeight = componentHeight(start);
        double nextWidth = startWidth;
        double nextHeight = startHeight;
        double centerOffsetX = 0.0D;
        double centerOffsetY = 0.0D;
        if (this.dragMode.xSign() != 0) {
            double anchor = -this.dragMode.xSign() * startWidth * 0.5D;
            double pointer = this.dragMode.xSign() > 0 ? Math.max(anchor + 4.0D, local.x()) : Math.min(anchor - 4.0D, local.x());
            nextWidth = Math.abs(pointer - anchor);
            centerOffsetX = (pointer + anchor) * 0.5D;
        }
        if (this.dragMode.ySign() != 0) {
            double anchor = -this.dragMode.ySign() * startHeight * 0.5D;
            double pointer = this.dragMode.ySign() > 0 ? Math.max(anchor + 4.0D, local.y()) : Math.min(anchor - 4.0D, local.y());
            nextHeight = Math.abs(pointer - anchor);
            centerOffsetY = (pointer + anchor) * 0.5D;
        }
        double radians = Math.toRadians(start.rotationDegrees());
        double worldOffsetX = centerOffsetX * Math.cos(radians) - centerOffsetY * Math.sin(radians);
        double worldOffsetY = centerOffsetX * Math.sin(radians) + centerOffsetY * Math.cos(radians);
        float centerX = start.centerX() + (float) (worldOffsetX / Math.max(1.0F, this.zoom));
        float centerY = start.centerY() + (float) (worldOffsetY / Math.max(1.0F, this.zoom));
        replace(start.withTransform(centerX, centerY,
                (float) (nextWidth / Math.max(1.0F, this.zoom)),
                (float) (nextHeight / Math.max(1.0F, this.zoom)),
                start.rotationDegrees()));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.draggingFloatingTextBox != null) {
            this.setDragging(false);
            EditBox released = this.draggingFloatingTextBox;
            this.draggingFloatingTextBox = null;
            released.mouseReleased(event);
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.draggingLayerId != null) {
            moveLayerTo(this.draggingLayerId, this.draggingLayerTarget < 0 ? layerIndexAt(event.y()) : this.draggingLayerTarget);
            this.draggingLayerId = null;
            this.draggingLayerTarget = -1;
            this.dragMode = DragMode.NONE;
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.dragMode == DragMode.PROPERTIES) {
            this.dragMode = DragMode.NONE;
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.propertiesOpen && this.propertiesBounds.contains(event.x(), event.y())) {
            return true;
        }
        if ((event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.dragStart != null) || (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && this.dragMode == DragMode.PAN)) {
            this.dragMode = DragMode.NONE;
            this.dragStart = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.iconPickerOpen && this.iconPickerBounds.contains(mouseX, mouseY)) {
            this.iconPickerScroll = Math.max(0.0D, Math.min(maxIconPickerScroll(), this.iconPickerScroll - scrollY * 22.0D));
            return true;
        }
        if (this.addMenuOpen && this.addMenuBounds.contains(mouseX, mouseY)) {
            this.addMenuScroll = Math.max(0.0D, Math.min(maxAddMenuScroll(), this.addMenuScroll - scrollY * 18.0D));
            return true;
        }
        if (this.propertiesOpen && this.propertiesBounds.contains(mouseX, mouseY) && selected() != null) {
            this.propertiesScroll = Math.max(0.0D, Math.min(maxPropertiesScroll(selected(), this.propertiesBounds.height()), this.propertiesScroll - scrollY * 18.0D));
            return true;
        }
        if (this.layersOpen && this.layerListArea.contains(mouseX, mouseY)) {
            this.layerScroll = Math.max(0.0D, Math.min(maxLayerScroll(), this.layerScroll - scrollY * 21.0D));
            return true;
        }
        if (this.stage.contains(mouseX, mouseY)) {
            float old = this.zoom;
            double oldDocX = this.doc.x();
            double oldDocY = this.doc.y();
            double oldDocWidth = Math.max(1.0D, this.doc.width());
            double oldDocHeight = Math.max(1.0D, this.doc.height());
            double canvasX = (mouseX - oldDocX) / oldDocWidth;
            double canvasY = (mouseY - oldDocY) / oldDocHeight;
            float factor = scrollY > 0 ? 1.10F : 1.0F / 1.10F;
            this.zoom = Math.max(8.0F, Math.min(720.0F, this.zoom * factor));
            if (old != this.zoom) {
                SPSGui.Rect nextDoc = canvasRect();
                this.panX += mouseX - (nextDoc.x() + canvasX * nextDoc.width());
                this.panY += mouseY - (nextDoc.y() + canvasY * nextDoc.height());
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean focusedTextBoxInFloatingPanel() {
        return activeFloatingTextBox() != null;
    }

    private EditBox focusedFloatingTextBox() {
        return activeFloatingTextBox();
    }

    private EditBox activeFloatingTextBox() {
        if (this.activeFloatingTextBox != null && this.activeFloatingTextBox.visible && this.activeFloatingTextBox.active) {
            if (!this.activeFloatingTextBox.isFocused()) {
                focusFloatingTextBox(this.activeFloatingTextBox);
            }
            return this.activeFloatingTextBox;
        }
        this.activeFloatingTextBox = null;
        EditBox[] boxes = {
                this.componentTextBox,
                this.networkUrlBox,
                this.canvasWidthBox,
                this.canvasHeightBox,
                this.previewExitBox
        };
        for (EditBox box : boxes) {
            if (box != null && box.visible && box.active && box.isFocused()) {
                this.activeFloatingTextBox = box;
                return box;
            }
        }
        return null;
    }

    private void focusFloatingTextBox(EditBox box) {
        this.clearFocus();
        this.activeFloatingTextBox = box;
        this.setFocused(box);
        if (this.nameBox != null && this.nameBox != box) {
            this.nameBox.setFocused(false);
        }
        for (EditBox candidate : floatingTextBoxes()) {
            if (candidate != null) {
                candidate.setFocused(candidate == box);
            }
        }
    }

    private void clearFloatingTextBoxFocus() {
        this.activeFloatingTextBox = null;
        this.draggingFloatingTextBox = null;
        for (EditBox candidate : floatingTextBoxes()) {
            if (candidate != null) {
                candidate.setFocused(false);
            }
        }
    }

    private EditBox floatingTextBoxAt(double mouseX, double mouseY) {
        for (EditBox box : floatingTextBoxes()) {
            if (box != null && box.visible && box.active && box.isMouseOver(mouseX, mouseY)) {
                return box;
            }
        }
        return null;
    }

    private EditBox[] floatingTextBoxes() {
        return new EditBox[] {
                this.componentTextBox,
                this.networkUrlBox,
                this.canvasWidthBox,
                this.canvasHeightBox,
                this.previewExitBox
        };
    }

    private double maxLayerScroll() {
        return Math.max(0.0D, this.draft.components().size() * 33.0D + 3.0D - this.layerListArea.height());
    }

    private boolean topLevelClick(double mouseX, double mouseY) {
        if (this.addMenuOpen && this.addMenuBounds.contains(mouseX, mouseY)) {
            return dispatchClickActionsForLayer(ClickLayer.ADD_MENU, mouseX, mouseY);
        }
        if (this.layersOpen && this.layerPanel.contains(mouseX, mouseY)) {
            return dispatchClickActionsForLayer(ClickLayer.LAYERS, mouseX, mouseY);
        }
        return dispatchClickActionsForLayer(ClickLayer.BASE, mouseX, mouseY);
    }

    private boolean dispatchClickActionsForLayer(ClickLayer layer, double mouseX, double mouseY) {
        if (floatingTextBoxAt(mouseX, mouseY) != null) {
            return false;
        }
        for (LayeredClickAction clickAction : List.copyOf(this.layeredClickActions)) {
            if (clickAction.layer() == layer && clickAction.bounds().contains(mouseX, mouseY)) {
                clearFloatingTextBoxFocus();
                this.clearFocus();
                clickAction.action().run();
                return true;
            }
        }
        return false;
    }

    private double maxAddMenuScroll() {
        if (!this.addMenuOpen || this.addMenuBounds.height() <= 0) {
            return 0.0D;
        }
        return Math.max(0.0D, componentTypes(this.draft.target()).length * 18.0D - Math.max(1, this.addMenuBounds.height() - 10));
    }

    private double maxPropertiesScroll(ProjectionComponent component, int panelHeight) {
        int contentHeight = Math.max(0, propertiesHeight(component) - 31);
        int viewportHeight = Math.max(1, panelHeight - 32);
        return Math.max(0.0D, contentHeight - viewportHeight);
    }

    private static List<ProjectionVisibleCondition> allowedVisibleConditions(ProjectionComponentType type, ProjectionLayoutTarget target) {
        return ProjectionComponentDescriptors.allowedVisibleConditions(type, target);
    }

    private static ProjectionVisibleCondition normalizeVisibleCondition(ProjectionVisibleCondition value, ProjectionComponentType type, ProjectionLayoutTarget target) {
        return ProjectionComponentDescriptors.normalizeVisibleCondition(value, type, target);
    }

    private static ProjectionVisibleCondition nextVisibleCondition(ProjectionVisibleCondition value, List<ProjectionVisibleCondition> allowed) {
        int index = Math.max(0, allowed.indexOf(value));
        return allowed.get((index + 1) % allowed.size());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        EditBox focusedBox = activeFloatingTextBox();
        if (focusedBox != null) {
            if (handleFloatingTextBoxKey(focusedBox, event)) {
                return true;
            }
            if (focusedBox.keyPressed(event)) {
                return true;
            }
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (this.confirmationOpen()) {
                return true;
            }
            if (this.propertiesOpen) {
                if (this.colorTarget != null) {
                    this.colorTarget = null;
                    return true;
                }
                if (this.iconPickerOpen) {
                    this.iconPickerOpen = false;
                    return true;
                }
                this.propertiesOpen = false;
                clearFloatingTextBoxFocus();
                return true;
            }
            if (this.previewOpen) {
                this.previewOpen = false;
                return true;
            }
            if (this.canvasSettingsOpen) {
                this.canvasSettingsOpen = false;
                return true;
            }
            if (this.addMenuOpen) {
                this.addMenuOpen = false;
                return true;
            }
        }
        if (event.key() == GLFW.GLFW_KEY_DELETE
                && selected() != null
                && (this.nameBox == null || !this.nameBox.isFocused())
                && (this.componentTextBox == null || !this.componentTextBox.isFocused())
                && (this.networkUrlBox == null || !this.networkUrlBox.isFocused())) {
            deleteComponent(this.selectedId);
            return true;
        }
        return super.keyPressed(event);
    }

    private boolean handleFloatingTextBoxKey(EditBox box, KeyEvent event) {
        if (box == null || !box.visible || !box.active || !box.isFocused()) {
            return false;
        }
        if (event.isSelectAll()) {
            box.moveCursorToEnd(false);
            box.setHighlightPos(0);
            return true;
        }
        if (event.isCopy()) {
            String highlighted = box.getHighlighted();
            if (!highlighted.isEmpty()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(highlighted);
            }
            return true;
        }
        if (event.isCut()) {
            String highlighted = box.getHighlighted();
            if (!highlighted.isEmpty()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(highlighted);
                box.insertText("");
            }
            return true;
        }
        if (event.isPaste()) {
            box.insertText(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }
        boolean selecting = event.hasShiftDown();
        return switch (event.key()) {
            case GLFW.GLFW_KEY_LEFT -> {
                if (event.hasControlDownWithQuirk()) {
                    box.moveCursorTo(box.getWordPosition(-1), selecting);
                } else {
                    box.moveCursor(-1, selecting);
                }
                yield true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (event.hasControlDownWithQuirk()) {
                    box.moveCursorTo(box.getWordPosition(1), selecting);
                } else {
                    box.moveCursor(1, selecting);
                }
                yield true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                box.moveCursorToStart(selecting);
                yield true;
            }
            case GLFW.GLFW_KEY_END -> {
                box.moveCursorToEnd(selecting);
                yield true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                box.deleteChars(1);
                yield true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                box.deleteChars(-1);
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        EditBox focusedBox = activeFloatingTextBox();
        if (focusedBox != null && focusedBox.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
    }

    private static Component componentLabel(ProjectionComponentType type) {
        return Component.translatable("screen.superpipeslide.projection_designer.component." + type.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static ProjectionComponentType[] componentTypes(ProjectionLayoutTarget target) {
        return ProjectionComponentDescriptors.typesFor(target);
    }

    private static Component localized(ProjectionTextAlign value) {
        return Component.translatable("screen.superpipeslide.projection_designer.align." + value.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component localized(ProjectionOverflowMode value) {
        return Component.translatable("screen.superpipeslide.projection_designer.overflow." + value.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component localized(ProjectionComponentSettings.FlowDirection value) {
        return Component.translatable("screen.superpipeslide.projection_designer.flow." + value.name().toLowerCase(Locale.ROOT));
    }

    private static Component localized(ProjectionComponentSettings.RouteOverflowMode value) {
        return Component.translatable("screen.superpipeslide.projection_designer.route_overflow." + value.name().toLowerCase(Locale.ROOT));
    }

    private static Component localized(ProjectionComponentSettings.StripeDirection value) {
        return Component.translatable("screen.superpipeslide.projection_designer.stripe_direction." + value.name().toLowerCase(Locale.ROOT));
    }

    private static Component localized(ProjectionComponentSettings.ColorPolicy value) {
        return Component.translatable("screen.superpipeslide.projection_designer.color_policy." + value.name().toLowerCase(Locale.ROOT));
    }

    private static Component localized(ProjectionComponentSettings.IconShape value) {
        return Component.translatable("screen.superpipeslide.projection_designer.icon_shape." + value.name().toLowerCase(Locale.ROOT));
    }

    private static Component localized(ProjectionComponentSettings.CapsuleContentOrientation value) {
        return Component.translatable("screen.superpipeslide.projection_designer.content_orientation." + value.name().toLowerCase(Locale.ROOT));
    }

    private static Component localized(ProjectionComponentSettings.TextOrientation value) {
        return Component.translatable("screen.superpipeslide.projection_designer.orientation." + value.name().toLowerCase(Locale.ROOT));
    }

    private static Component localized(ProjectionComponentSettings.MissingTranslationMode value) {
        return Component.translatable("screen.superpipeslide.projection_designer.missing_translation." + value.name().toLowerCase(Locale.ROOT));
    }

    private static Component localized(ProjectionComponentSettings.ImageFitMode value) {
        return Component.translatable("screen.superpipeslide.projection_designer.fit." + value.name().toLowerCase(Locale.ROOT));
    }

    private static Component localized(ProjectionComponentSettings.ImageAnchor value) {
        return Component.translatable("screen.superpipeslide.projection_designer.anchor." + value.name().toLowerCase(Locale.ROOT));
    }

    private static Component localized(ProjectionComponentSettings.IconTintMode value) {
        return Component.translatable("screen.superpipeslide.projection_designer.tint_mode." + value.name().toLowerCase(Locale.ROOT));
    }

    private static Component localized(ProjectionComponentSettings.ImageFallbackMode value) {
        return Component.translatable("screen.superpipeslide.projection_designer.fallback_mode." + value.name().toLowerCase(Locale.ROOT));
    }

    private static Component localized(ProjectionComponentSettings.ImageLoadingMode value) {
        return Component.translatable("screen.superpipeslide.projection_designer.loading_mode." + value.name().toLowerCase(Locale.ROOT));
    }

    private static Component localized(ProjectionVisibleCondition value) {
        return Component.translatable("screen.superpipeslide.projection_designer.visible." + value.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component localized(ProjectionPreviewScenario.RoutePalette value) {
        return Component.translatable("screen.superpipeslide.projection_designer.palette." + value.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component localizedPlatform(Enum<?> value) {
        return Component.translatable("screen.superpipeslide.projection_designer.platform." + value.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static ProjectionTextAlign next(ProjectionTextAlign value) {
        return switch (value) {
            case LEFT -> ProjectionTextAlign.CENTER;
            case CENTER -> ProjectionTextAlign.RIGHT;
            case RIGHT -> ProjectionTextAlign.LEFT;
        };
    }

    private static ProjectionOverflowMode next(ProjectionOverflowMode value) {
        ProjectionOverflowMode[] values = ProjectionOverflowMode.values();
        return values[(value.ordinal() + 1) % values.length];
    }

    private static ProjectionComponentSettings.FlowDirection next(ProjectionComponentSettings.FlowDirection value) {
        ProjectionComponentSettings.FlowDirection[] values = ProjectionComponentSettings.FlowDirection.values();
        return values[(value.ordinal() + 1) % values.length];
    }

    private static ProjectionComponentSettings.RouteOverflowMode next(ProjectionComponentSettings.RouteOverflowMode value) {
        ProjectionComponentSettings.RouteOverflowMode[] values = ProjectionComponentSettings.RouteOverflowMode.values();
        return values[(value.ordinal() + 1) % values.length];
    }

    private static ProjectionComponentSettings.StripeDirection next(ProjectionComponentSettings.StripeDirection value) {
        ProjectionComponentSettings.StripeDirection[] values = ProjectionComponentSettings.StripeDirection.values();
        return values[(value.ordinal() + 1) % values.length];
    }

    private static ProjectionComponentSettings.ColorPolicy next(ProjectionComponentSettings.ColorPolicy value) {
        ProjectionComponentSettings.ColorPolicy[] values = ProjectionComponentSettings.ColorPolicy.values();
        return values[(value.ordinal() + 1) % values.length];
    }

    private static ProjectionComponentSettings.IconShape next(ProjectionComponentSettings.IconShape value) {
        ProjectionComponentSettings.IconShape[] values = ProjectionComponentSettings.IconShape.values();
        return values[(value.ordinal() + 1) % values.length];
    }

    private static ProjectionComponentSettings.CapsuleContentOrientation next(ProjectionComponentSettings.CapsuleContentOrientation value) {
        ProjectionComponentSettings.CapsuleContentOrientation[] values = ProjectionComponentSettings.CapsuleContentOrientation.values();
        return values[(value.ordinal() + 1) % values.length];
    }

    private static ProjectionComponentSettings.TextOrientation next(ProjectionComponentSettings.TextOrientation value) {
        ProjectionComponentSettings.TextOrientation[] values = ProjectionComponentSettings.TextOrientation.values();
        return values[(value.ordinal() + 1) % values.length];
    }

    private static ProjectionComponentSettings.MissingTranslationMode next(ProjectionComponentSettings.MissingTranslationMode value) {
        ProjectionComponentSettings.MissingTranslationMode[] values = ProjectionComponentSettings.MissingTranslationMode.values();
        return values[(value.ordinal() + 1) % values.length];
    }

    private static ProjectionComponentSettings.ImageFitMode next(ProjectionComponentSettings.ImageFitMode value) {
        ProjectionComponentSettings.ImageFitMode[] values = ProjectionComponentSettings.ImageFitMode.values();
        return values[(value.ordinal() + 1) % values.length];
    }

    private static ProjectionComponentSettings.ImageFitMode nextIconFitMode(ProjectionComponentSettings.ImageFitMode value) {
        return switch (value == null ? ProjectionComponentSettings.ImageFitMode.CONTAIN : value) {
            case CONTAIN -> ProjectionComponentSettings.ImageFitMode.STRETCH;
            case STRETCH, COVER -> ProjectionComponentSettings.ImageFitMode.CENTER;
            case CENTER -> ProjectionComponentSettings.ImageFitMode.TILE;
            case TILE -> ProjectionComponentSettings.ImageFitMode.CONTAIN;
        };
    }

    private static ProjectionComponentSettings.ImageAnchor next(ProjectionComponentSettings.ImageAnchor value) {
        ProjectionComponentSettings.ImageAnchor[] values = ProjectionComponentSettings.ImageAnchor.values();
        return values[(value.ordinal() + 1) % values.length];
    }

    private static ProjectionComponentSettings.IconTintMode next(ProjectionComponentSettings.IconTintMode value) {
        ProjectionComponentSettings.IconTintMode[] values = ProjectionComponentSettings.IconTintMode.values();
        return values[(value.ordinal() + 1) % values.length];
    }

    private static ProjectionComponentSettings.ImageFallbackMode next(ProjectionComponentSettings.ImageFallbackMode value) {
        ProjectionComponentSettings.ImageFallbackMode[] values = ProjectionComponentSettings.ImageFallbackMode.values();
        return values[(value.ordinal() + 1) % values.length];
    }

    private static ProjectionComponentSettings.ImageLoadingMode next(ProjectionComponentSettings.ImageLoadingMode value) {
        ProjectionComponentSettings.ImageLoadingMode[] values = ProjectionComponentSettings.ImageLoadingMode.values();
        return values[(value.ordinal() + 1) % values.length];
    }

    private static ProjectionVisibleCondition next(ProjectionVisibleCondition value) {
        ProjectionVisibleCondition[] values = ProjectionVisibleCondition.values();
        return values[(value.ordinal() + 1) % values.length];
    }

    private static <E extends Enum<E>> E nextEnum(E value) {
        E[] values = value.getDeclaringClass().getEnumConstants();
        return values[(value.ordinal() + 1) % values.length];
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private enum DragMode {
        NONE(0, 0),
        PAN(0, 0),
        PROPERTIES(0, 0),
        MOVE(0, 0),
        RESIZE_NW(-1, -1),
        RESIZE_N(0, -1),
        RESIZE_NE(1, -1),
        RESIZE_E(1, 0),
        RESIZE_SE(1, 1),
        RESIZE_S(0, 1),
        RESIZE_SW(-1, 1),
        RESIZE_W(-1, 0),
        ROTATE(0, 0);

        private final int xSign;
        private final int ySign;

        DragMode(int xSign, int ySign) {
            this.xSign = xSign;
            this.ySign = ySign;
        }

        int xSign() {
            return this.xSign;
        }

        int ySign() {
            return this.ySign;
        }

    }

    private enum Handle {
        NW(-1, -1, DragMode.RESIZE_NW),
        N(0, -1, DragMode.RESIZE_N),
        NE(1, -1, DragMode.RESIZE_NE),
        E(1, 0, DragMode.RESIZE_E),
        SE(1, 1, DragMode.RESIZE_SE),
        S(0, 1, DragMode.RESIZE_S),
        SW(-1, 1, DragMode.RESIZE_SW),
        W(-1, 0, DragMode.RESIZE_W),
        ROTATE(0, -1, DragMode.ROTATE);

        private final int x;
        private final int y;
        private final DragMode mode;

        Handle(int x, int y, DragMode mode) {
            this.x = x;
            this.y = y;
            this.mode = mode;
        }

        DragMode mode() {
            return this.mode;
        }

        Point localPoint(int width, int height) {
            if (this == ROTATE) {
                return new Point(0, -height * 0.5D - 20);
            }
            return new Point(this.x * width * 0.5D, this.y * height * 0.5D);
        }

        static List<Handle> resizeHandles() {
            return List.of(NW, N, NE, E, SE, S, SW, W);
        }
    }

    private record Point(double x, double y) {
    }

    private record LayeredClickAction(ClickLayer layer, SPSGui.Rect bounds, Runnable action, Component tooltip) {
    }

    private enum ClickLayer {
        BASE,
        LAYERS,
        ADD_MENU,
        CANVAS_SETTINGS,
        SCENARIO,
        PROPERTIES,
        ICON_PICKER,
        COLOR_PICKER
    }

    private enum ColorTarget {
        TEXT,
        PRIMARY_TEXT,
        TRANSLATION_TEXT,
        FILL,
        BORDER,
        ACCENT,
        PLUS,
        TINT,
        SECONDARY
    }
}
