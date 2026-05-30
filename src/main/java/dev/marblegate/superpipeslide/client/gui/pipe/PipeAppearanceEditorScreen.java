package dev.marblegate.superpipeslide.client.gui.pipe;

import dev.marblegate.superpipeslide.client.core.pipe.PipeCoatingRenderResolver;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.render.SmoothGuiPrimitives;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.client.gui.base.SPSScreen;
import dev.marblegate.superpipeslide.common.core.appearance.coating.PipeCoatingDyeMode;
import dev.marblegate.superpipeslide.common.core.appearance.coating.PipeCoatingSelection;
import dev.marblegate.superpipeslide.common.core.appearance.coating.PipeTexturePickType;
import dev.marblegate.superpipeslide.common.core.appearance.material.MaterialSlotDefinition;
import dev.marblegate.superpipeslide.common.core.appearance.model.PipeAppearanceProfile;
import dev.marblegate.superpipeslide.common.core.appearance.storage.PipeAppearanceDefinitions;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeStyleDefinition;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeStyleGeometry;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeStyleParameterDefinition;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeSurfaceModel;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeVariantDefinition;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.network.pipe.appearance.ClientboundOpenPipeAppearanceEditorPayload;
import dev.marblegate.superpipeslide.network.pipe.appearance.ServerboundPipeAppearanceApplyPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class PipeAppearanceEditorScreen extends SPSScreen {
    private static final String BODY_SLOT = "body";
    private static final double PREVIEW_DEFAULT_YAW = -0.62D;
    private static final double PREVIEW_DEFAULT_PITCH = 0.34D;
    private static final double PIPE_TEXTURE_TILE_U_BLOCKS = 1.0D;
    private static final double PIPE_TEXTURE_TILE_V_BLOCKS = 1.0D;
    private static final double SURFACE_UV_EPSILON = 1.0E-5D;
    private static final double PREVIEW_MARKER_SURFACE_OFFSET = 0.026D;
    private static final double PREVIEW_MARKER_LAYER_OFFSET = 0.006D;
    private static final int PREVIEW_ACCELERATION_COLOR = 0xF8FF9F2E;
    private static final int PREVIEW_ACCELERATION_CORE_COLOR = 0xFFFFE37A;
    private static final int PREVIEW_HIGHWAY_COLOR = 0xE835C9FF;
    private static final int PREVIEW_HIGHWAY_HIGHLIGHT_COLOR = 0xF8A8F4FF;
    private static final int PREVIEW_HIGHWAY_EDGE_COLOR = 0xC0258EBA;
    private static final int PREVIEW_DIRECTION_COLOR = 0xF8FF4050;
    private static final int PREVIEW_DIRECTION_CORE_COLOR = 0xF8FFFFFF;
    private static final int PREVIEW_PLATFORM_COLOR = 0xF8FFD34D;
    private static final int PREVIEW_PLATFORM_EDGE_COLOR = 0xEEFFFFFF;
    private static final int PREVIEW_PLATFORM_SHADOW_COLOR = 0xCC3A3524;
    private static final int PREVIEW_PLATFORM_SAFETY_COLOR = 0xF8FFF4C0;
    private static final Identifier PREVIEW_MARKER_TEXTURE = Identifier.withDefaultNamespace("block/white_concrete");
    private static final int PREVIEW_ANIMATION_NONE = 0;
    private static final int PREVIEW_ANIMATION_ACCELERATION = 1;
    private static final int PREVIEW_ANIMATION_HIGHWAY = 2;
    private static final int PREVIEW_ANIMATION_DIRECTION = 3;

    private long appearanceRevision;
    private int targetConnectionKey;
    private double targetLength;
    private PipeAppearanceProfile currentProfile;
    private PipeAppearanceProfile draftProfile;
    private String selectedSlotId = BODY_SLOT;
    private final Map<String, SPSGui.Rect> parameterSliderBounds = new LinkedHashMap<>();
    private String draggingParameterId;
    private SPSGui.Rect previewBounds = new SPSGui.Rect(0, 0, 0, 0);
    private boolean draggingPreview;
    private double previewYaw = PREVIEW_DEFAULT_YAW;
    private double previewPitch = PREVIEW_DEFAULT_PITCH;
    private boolean previewAcceleration;
    private boolean previewHighway;
    private boolean previewDirection;
    private boolean previewPlatform;
    private int previewDirectionLimit = 1;
    private SPSGui.Rect styleRailScrollBounds = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect tuningScrollBounds = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect materialStripScrollBounds = new SPSGui.Rect(0, 0, 0, 0);
    private double styleRailScroll;
    private double styleRailMaxScroll;
    private double tuningScroll;
    private double tuningMaxScroll;
    private double materialStripScroll;
    private double materialStripMaxScroll;
    private boolean revealSelectedStyle = true;

    public PipeAppearanceEditorScreen(ClientboundOpenPipeAppearanceEditorPayload payload) {
        super(Component.translatable("screen.superpipeslide.pipe_appearance"));
        this.applyPayload(payload);
    }

    public void applyPayload(ClientboundOpenPipeAppearanceEditorPayload payload) {
        this.appearanceRevision = payload.appearanceRevision();
        this.targetConnectionKey = payload.targetConnectionKey();
        this.targetLength = payload.targetLength();
        this.currentProfile = payload.currentProfile().normalizedToDefinitions();
        this.draftProfile = payload.draftProfile().normalizedToDefinitions();
        resetPreviewMarkers();
        PipeStyleDefinition style = PipeAppearanceDefinitions.style(this.draftProfile.styleId()).orElse(PipeAppearanceDefinitions.defaultStyle());
        PipeVariantDefinition variant = PipeAppearanceDefinitions.variant(this.draftProfile.variantId()).orElse(PipeAppearanceDefinitions.defaultVariant());
        PipeAppearanceDefinitions.slotsFor(style, variant).stream().findFirst().ifPresent(slot -> this.selectedSlotId = slot.id());
    }

    public void acceptAppearanceRevision(long revision) {
        this.appearanceRevision = revision;
    }

    @Override
    protected SPSGui.Rect createPanelRect() {
        int width = Math.min(820, this.width - 14);
        int height = Math.min(438, this.height - 12);
        return new SPSGui.Rect((this.width - width) / 2, (this.height - height) / 2, width, height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.beginFrame();
        PipeAppearanceTerminalGui.frame(graphics, this.panel);
        PipeAppearanceTerminalGui.titleBar(graphics, this.font, this.panel, Component.translatable("screen.superpipeslide.pipe_appearance"), true, mouseX, mouseY);
        this.addClick(PipeAppearanceTerminalGui.backBounds(this.panel), this::onBack, Component.translatable("screen.superpipeslide.action.back"));

        SPSGui.Rect content = new SPSGui.Rect(this.panel.x() + 7, this.panel.y() + 25, this.panel.width() - 14, this.panel.height() - 31);
        PipeAppearanceLayout layout = layoutFor(content);
        int headerH = layout.headerHeight();
        drawStatusHeader(graphics, new SPSGui.Rect(content.x(), content.y(), content.width(), headerH), mouseX, mouseY);

        int actionH = layout.actionHeight();
        int materialH = layout.materialHeight();
        int gap = layout.gap();
        int mainY = content.y() + headerH + gap;
        SPSGui.Rect actions = new SPSGui.Rect(content.x(), content.bottom() - actionH, content.width(), actionH);
        SPSGui.Rect materialStrip = new SPSGui.Rect(content.x(), actions.y() - materialH - gap, content.width(), materialH);
        SPSGui.Rect main = new SPSGui.Rect(content.x(), mainY, content.width(), materialStrip.y() - mainY - gap);
        int railW = layout.railWidth();
        int tuneW = layout.tuningWidth();
        SPSGui.Rect styleRail = new SPSGui.Rect(main.x(), main.y(), railW, main.height());
        SPSGui.Rect tunePane = new SPSGui.Rect(main.right() - tuneW, main.y(), tuneW, main.height());
        SPSGui.Rect previewPane = new SPSGui.Rect(styleRail.right() + gap, main.y(), tunePane.x() - styleRail.right() - gap * 2, main.height());
        drawStyleRail(graphics, styleRail, mouseX, mouseY);
        drawPreviewPane(graphics, previewPane, mouseX, mouseY);
        drawTuningPane(graphics, tunePane, mouseX, mouseY);
        drawMaterialSlotStrip(graphics, materialStrip, mouseX, mouseY);
        drawBottomActions(graphics, actions, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.renderTooltips(graphics, mouseX, mouseY);
    }

    private void drawStatusHeader(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        PipeStyleDefinition style = PipeAppearanceDefinitions.style(this.draftProfile.styleId()).orElse(PipeAppearanceDefinitions.defaultStyle());
        PipeVariantDefinition variant = PipeAppearanceDefinitions.variant(this.draftProfile.variantId()).orElse(PipeAppearanceDefinitions.defaultVariant());
        boolean dirty = isDirty();
        PipeAppearanceTerminalGui.raisedPanel(graphics, rect);
        boolean compact = rect.height() < 21;
        int glyphSize = compact ? 12 : 16;
        int glyphY = rect.y() + (rect.height() - glyphSize) / 2;
        drawStyleGlyph(graphics, new SPSGui.Rect(rect.x() + 5, glyphY, glyphSize, glyphSize), style, this.draftProfile.glow() ? PipeAppearanceTerminalGui.GLOW : PipeAppearanceTerminalGui.ACCENT_SELECTED);
        int textY = rect.y() + Math.max(3, (rect.height() - 8) / 2);
        SPSGui.text(graphics, this.font, SPSGui.ellipsize(this.font, Component.translatable(style.nameKey()).getString(), compact ? 106 : 92), rect.x() + 26, textY, PipeAppearanceTerminalGui.TEXT);
        String variantText = Component.translatable(variant.nameKey()).getString();
        String modeText = hasTarget()
                ? Component.translatable("screen.superpipeslide.pipe_appearance.mode_target").getString()
                : Component.translatable("screen.superpipeslide.pipe_appearance.mode_tool").getString();
        if (!compact) {
            SPSGui.smallText(graphics, this.font, modeText + " / " + variantText, rect.x() + 130, rect.y() + 7, PipeAppearanceTerminalGui.TEXT_MUTED, 0.62F);
        }
        String stateKey = dirty ? "screen.superpipeslide.pipe_appearance.state_unsaved" : "screen.superpipeslide.pipe_appearance.state_saved";
        int stateColor = dirty ? PipeAppearanceTerminalGui.WARNING : PipeAppearanceTerminalGui.SUCCESS;
        int badgeW = Math.max(50, this.font.width(Component.translatable(stateKey)) + 14);
        SPSGui.Rect badge = new SPSGui.Rect(rect.right() - badgeW - 6, rect.y() + (rect.height() - 14) / 2, badgeW, 14);
        PipeAppearanceTerminalGui.badge(graphics, this.font, badge, Component.translatable(stateKey), stateColor);
    }

    private void drawStyleRail(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        PipeAppearanceTerminalGui.panel(graphics, rect);
        List<PipeStyleDefinition> styles = PipeAppearanceDefinitions.styles();
        boolean labeled = rect.width() >= 54 && rect.height() >= 116;
        int labelHeight = labeled ? 17 : 0;
        if (labeled) {
            SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.pipe_appearance.styles").getString(), rect.x() + 8, rect.y() + 6, PipeAppearanceTerminalGui.TEXT_MUTED, 0.62F);
            graphics.fill(rect.x() + 8, rect.y() + 16, rect.right() - 8, rect.y() + 17, 0x5546FF7A);
        }
        int size = Math.max(24, Math.min(labeled ? 33 : 30, rect.width() - 14));
        int gap = 5;
        int contentHeight = styles.isEmpty() ? 0 : styles.size() * size + (styles.size() - 1) * gap + 10;
        this.styleRailScrollBounds = new SPSGui.Rect(rect.x() + 4, rect.y() + 5 + labelHeight, rect.width() - 8, Math.max(1, rect.height() - 10 - labelHeight));
        this.styleRailMaxScroll = Math.max(0.0D, contentHeight - this.styleRailScrollBounds.height());
        if (this.revealSelectedStyle) {
            int selectedIndex = 0;
            for (int i = 0; i < styles.size(); i++) {
                if (styles.get(i).id().equals(this.draftProfile.styleId())) {
                    selectedIndex = i;
                    break;
                }
            }
            double selectedTop = 5.0D + selectedIndex * (size + gap);
            double selectedBottom = selectedTop + size;
            if (selectedTop < this.styleRailScroll) {
                this.styleRailScroll = selectedTop;
            } else if (selectedBottom > this.styleRailScroll + this.styleRailScrollBounds.height()) {
                this.styleRailScroll = selectedBottom - this.styleRailScrollBounds.height();
            }
            this.revealSelectedStyle = false;
        }
        this.styleRailScroll = clamp(this.styleRailScroll, 0.0D, this.styleRailMaxScroll);
        int y = this.styleRailScrollBounds.y() + 5 - (int) Math.round(this.styleRailScroll);
        graphics.enableScissor(this.styleRailScrollBounds.x(), this.styleRailScrollBounds.y(), this.styleRailScrollBounds.right(), this.styleRailScrollBounds.bottom());
        for (int i = 0; i < styles.size(); i++) {
            PipeStyleDefinition style = styles.get(i);
            SPSGui.Rect tile = new SPSGui.Rect(rect.x() + (rect.width() - size) / 2, y + i * (size + gap), size, size);
            boolean selected = style.id().equals(this.draftProfile.styleId());
            PipeVariantDefinition defaultVariant = PipeAppearanceDefinitions.variant(style.defaultVariantId()).orElse(PipeAppearanceDefinitions.defaultVariant());
            String slotId = PipeAppearanceDefinitions.slotsFor(style, defaultVariant).stream().findFirst().map(MaterialSlotDefinition::id).orElse(BODY_SLOT);
            PipeCoatingRenderResolver.ResolvedPipeCoating resolved = resolvedForSlot(this.draftProfile, slotId);
            drawWorkbenchButton(graphics, tile, tile.contains(mouseX, mouseY), selected, selected ? PipeAppearanceTerminalGui.ACCENT : PipeAppearanceTerminalGui.SURFACE_RAISED);
            if (selected) {
                graphics.fill(tile.x() + 4, tile.bottom() - 4, tile.right() - 4, tile.bottom() - 2, PipeAppearanceTerminalGui.ACCENT_SELECTED);
            }
            int glyphSize = Math.min(20, Math.max(16, tile.width() - 12));
            drawStyleGlyph(graphics, new SPSGui.Rect(tile.x() + (tile.width() - glyphSize) / 2, tile.y() + (tile.height() - glyphSize) / 2, glyphSize, glyphSize), style, selected ? 0xFFFFFFFF : resolved.opaqueTint());
            if (intersects(tile, this.styleRailScrollBounds)) {
                this.addClick(tile, () -> selectStyle(style), Component.translatable(style.nameKey()).append("\n").append(Component.translatable(style.subtitleKey())));
            }
        }
        graphics.disableScissor();
        PipeAppearanceTerminalGui.scrollEdges(graphics, this.styleRailScrollBounds, this.styleRailScroll > 0.5D, this.styleRailScroll < this.styleRailMaxScroll - 0.5D);
    }

    private void drawTuningPane(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        PipeAppearanceTerminalGui.panel(graphics, rect);
        SPSGui.Rect inner = new SPSGui.Rect(rect.x() + 6, rect.y() + 6, rect.width() - 12, rect.height() - 12);
        PipeStyleDefinition style = PipeAppearanceDefinitions.style(this.draftProfile.styleId()).orElse(PipeAppearanceDefinitions.defaultStyle());
        PipeVariantDefinition variant = PipeAppearanceDefinitions.variant(this.draftProfile.variantId()).orElse(PipeAppearanceDefinitions.defaultVariant());
        this.parameterSliderBounds.clear();
        this.tuningScrollBounds = new SPSGui.Rect(inner.x(), inner.y(), inner.width(), inner.height());
        this.tuningScroll = clamp(this.tuningScroll, 0.0D, this.tuningMaxScroll);
        int scrollOffset = (int) Math.round(this.tuningScroll);
        int contentY = this.tuningScrollBounds.y() - scrollOffset;
        graphics.enableScissor(this.tuningScrollBounds.x(), this.tuningScrollBounds.y(), this.tuningScrollBounds.right(), this.tuningScrollBounds.bottom());
        int y = contentY;
        y = drawTuningTopRow(graphics, inner, y, mouseX, mouseY, this.tuningScrollBounds);
        y = drawVariants(graphics, inner, style, y + 6, mouseX, mouseY, this.tuningScrollBounds);
        y = drawStyleParameters(graphics, inner, style, variant, y + 7, mouseX, mouseY, this.tuningScrollBounds);
        graphics.disableScissor();
        int contentHeight = Math.max(0, y - contentY + 2);
        this.tuningMaxScroll = Math.max(0.0D, contentHeight - this.tuningScrollBounds.height());
        this.tuningScroll = clamp(this.tuningScroll, 0.0D, this.tuningMaxScroll);
        PipeAppearanceTerminalGui.scrollEdges(graphics, this.tuningScrollBounds, this.tuningScroll > 0.5D, this.tuningScroll < this.tuningMaxScroll - 0.5D);
    }

    private int drawTuningTopRow(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int y, int mouseX, int mouseY, SPSGui.Rect viewport) {
        PipeAppearanceTerminalGui.sectionLabel(graphics, this.font, Component.translatable("screen.superpipeslide.pipe_appearance.variants"), rect.x(), y + 4);
        SPSGui.Rect glow = new SPSGui.Rect(rect.right() - 21, y, 20, 20);
        boolean active = this.draftProfile.glow();
        PipeAppearanceTerminalGui.iconButton(graphics, glow, SPSGui.Icon.PIPE_GLOW, active, glow.contains(mouseX, mouseY), active ? PipeAppearanceTerminalGui.GLOW : PipeAppearanceTerminalGui.TEXT_MUTED);
        if (intersects(glow, viewport)) {
            this.addClick(glow, () -> setGlow(!this.draftProfile.glow()), Component.translatable("screen.superpipeslide.pipe_appearance.glow_toggle.tooltip"));
        }
        return y + 20;
    }

    private int drawVariants(GuiGraphicsExtractor graphics, SPSGui.Rect rect, PipeStyleDefinition style, int y, int mouseX, int mouseY, SPSGui.Rect viewport) {
        List<PipeVariantDefinition> variants = PipeAppearanceDefinitions.variantsForStyle(style.id());
        int gap = 4;
        int cols = variants.size() <= 1 ? 1 : 2;
        int chipW = (rect.width() - (cols - 1) * gap) / cols;
        int chipH = 17;
        for (int i = 0; i < variants.size(); i++) {
            PipeVariantDefinition variant = variants.get(i);
            int col = i % cols;
            int row = i / cols;
            SPSGui.Rect chip = new SPSGui.Rect(rect.x() + col * (chipW + gap), y + row * (chipH + 4), chipW, chipH);
            boolean selected = variant.id().equals(this.draftProfile.variantId());
            drawWorkbenchButton(graphics, chip, chip.contains(mouseX, mouseY), selected, selected ? PipeAppearanceTerminalGui.ACCENT : PipeAppearanceTerminalGui.SURFACE_RAISED);
            SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, Component.translatable(variant.nameKey()).getString(), chip.width() - 8), chip.x() + 5, chip.y() + 5, selected ? PipeAppearanceTerminalGui.ACCENT_SELECTED : PipeAppearanceTerminalGui.TEXT_SECONDARY, 0.66F);
            if (intersects(chip, viewport)) {
                this.addClick(chip, () -> setVariant(variant.id()), Component.translatable(variant.subtitleKey()));
            }
        }
        return y + ((variants.size() + cols - 1) / cols) * (chipH + 4);
    }

    private int drawStyleParameters(GuiGraphicsExtractor graphics, SPSGui.Rect rect, PipeStyleDefinition style, PipeVariantDefinition variant, int y, int mouseX, int mouseY, SPSGui.Rect viewport) {
        List<PipeStyleParameterDefinition> parameters = PipeAppearanceDefinitions.parametersFor(style, variant);
        if (parameters.isEmpty()) {
            return y;
        }
        PipeAppearanceTerminalGui.sectionLabel(graphics, this.font, Component.translatable("screen.superpipeslide.pipe_appearance.parameters"), rect.x(), y);
        int rowY = y + 11;
        for (PipeStyleParameterDefinition parameter : parameters) {
            double value = style.parameterValue(this.draftProfile.styleParameters(), parameter.id());
            SPSGui.Rect row = new SPSGui.Rect(rect.x(), rowY, rect.width(), 20);
            boolean active = this.draggingParameterId != null && this.draggingParameterId.equals(parameter.id());
            drawWorkbenchButton(graphics, row, row.contains(mouseX, mouseY), active, PipeAppearanceTerminalGui.SURFACE_RAISED);
            SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, Component.translatable(parameter.nameKey()).getString(), 58), row.x() + 5, row.y() + 4, PipeAppearanceTerminalGui.TEXT, 0.62F);
            String valueText = String.format("%.2f", value);
            SPSGui.smallText(graphics, this.font, valueText, row.right() - 42, row.y() + 4, PipeAppearanceTerminalGui.TEXT_MUTED, 0.56F);

            SPSGui.Rect slider = new SPSGui.Rect(row.x() + 56, row.y() + 12, Math.max(28, row.width() - 100), 4);
            if (intersects(row, viewport)) {
                this.parameterSliderBounds.put(parameter.id(), slider);
            }
            graphics.fill(slider.x(), slider.y() + 2, slider.right(), slider.y() + 3, 0x8846FF7A);
            int fillRight = slider.x() + (int) Math.round(slider.width() * parameter.normalizedPercent(value));
            graphics.fill(slider.x(), slider.y() + 1, fillRight, slider.y() + 4, PipeAppearanceTerminalGui.ACCENT_SELECTED);
            int knobX = Math.max(slider.x(), Math.min(slider.right(), fillRight));
            graphics.fill(knobX - 2, slider.y() - 2, knobX + 2, slider.y() + 6, PipeAppearanceTerminalGui.TEXT);
            graphics.outline(knobX - 2, slider.y() - 2, 4, 8, row.contains(mouseX, mouseY) ? PipeAppearanceTerminalGui.ACCENT_SELECTED : PipeAppearanceTerminalGui.BORDER);
            SPSGui.Rect sliderHit = new SPSGui.Rect(slider.x() - 4, row.y(), slider.width() + 8, row.height());
            if (intersects(sliderHit, viewport)) {
                this.addClick(sliderHit, () -> {
                    this.draggingParameterId = parameter.id();
                    adjustParameterFromMouse(parameter.id(), mouseX);
                }, Component.translatable("screen.superpipeslide.pipe_appearance.parameter_adjust", Component.translatable(parameter.nameKey())));
            }

            SPSGui.Rect reset = new SPSGui.Rect(row.right() - 15, row.y() + 4, 12, 12);
            PipeAppearanceTerminalGui.iconButton(graphics, reset, SPSGui.Icon.RESET_VIEW, false, reset.contains(mouseX, mouseY), PipeAppearanceTerminalGui.TEXT_SECONDARY);
            if (intersects(reset, viewport)) {
                this.addClick(reset, () -> setStyleParameter(parameter.id(), parameter.defaultValue()), Component.translatable("screen.superpipeslide.pipe_appearance.parameter_reset", Component.translatable(parameter.nameKey())));
            }
            rowY += 22;
        }
        return rowY;
    }

    private void drawMaterialSlotStrip(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        PipeAppearanceTerminalGui.panel(graphics, rect);
        PipeStyleDefinition style = PipeAppearanceDefinitions.style(this.draftProfile.styleId()).orElse(PipeAppearanceDefinitions.defaultStyle());
        PipeVariantDefinition variant = PipeAppearanceDefinitions.variant(this.draftProfile.variantId()).orElse(PipeAppearanceDefinitions.defaultVariant());
        List<MaterialSlotDefinition> slots = PipeAppearanceDefinitions.slotsFor(style, variant);
        PipeCoatingSelection selectedSelection = PipeAppearanceDefinitions.selectionFor(this.draftProfile, this.selectedSlotId);
        String slotName = slots.stream()
                .filter(slot -> slot.id().equals(this.selectedSlotId))
                .findFirst()
                .map(slot -> Component.translatable(slot.nameKey()).getString())
                .orElse(Component.translatable("pipe_appearance.superpipeslide.slot.body").getString());
        int headerHeight = rect.height() >= 54 ? 15 : 12;
        PipeAppearanceTerminalGui.sectionLabel(graphics, this.font, Component.translatable("screen.superpipeslide.pipe_appearance.slots"), rect.x() + 7, rect.y() + 4);
        int summaryX = rect.x() + 66;
        int summaryW = Math.max(42, rect.right() - summaryX - 8);
        SPSGui.smallText(
                graphics,
                this.font,
                SPSGui.ellipsize(this.font, slotName + " / " + selectionName(selectedSelection), Math.round(summaryW / 0.56F)),
                summaryX,
                rect.y() + 6,
                PipeAppearanceTerminalGui.TEXT_MUTED,
                0.56F);
        int gap = 5;
        int tileH = Math.max(24, rect.height() - headerHeight - 9);
        int tileW = clampInt(tileH + 16, 42, 54);
        int totalWidth = slots.isEmpty() ? 0 : slots.size() * tileW + (slots.size() - 1) * gap;
        this.materialStripScrollBounds = new SPSGui.Rect(rect.x() + 7, rect.y() + headerHeight + 4, rect.width() - 14, tileH + 2);
        this.materialStripMaxScroll = Math.max(0.0D, totalWidth - this.materialStripScrollBounds.width());
        this.materialStripScroll = clamp(this.materialStripScroll, 0.0D, this.materialStripMaxScroll);
        int x = this.materialStripScrollBounds.x() - (int) Math.round(this.materialStripScroll);
        int y = this.materialStripScrollBounds.y() + 1;
        graphics.enableScissor(this.materialStripScrollBounds.x(), this.materialStripScrollBounds.y(), this.materialStripScrollBounds.right(), this.materialStripScrollBounds.bottom());
        for (MaterialSlotDefinition slot : slots) {
            SPSGui.Rect tile = new SPSGui.Rect(x, y, tileW, tileH);
            boolean selected = slot.id().equals(this.selectedSlotId);
            PipeCoatingSelection selection = PipeAppearanceDefinitions.selectionFor(this.draftProfile, slot.id());
            drawWorkbenchButton(graphics, tile, tile.contains(mouseX, mouseY), selected, PipeAppearanceTerminalGui.SURFACE_RAISED);
            if (selected) {
                graphics.fill(tile.x() + 3, tile.bottom() - 4, tile.right() - 3, tile.bottom() - 2, PipeAppearanceTerminalGui.ACCENT_SELECTED);
            }
            int swatchSize = Math.min(tile.width() - 12, tile.height() >= 34 ? 22 : 18);
            SPSGui.Rect swatch = new SPSGui.Rect(tile.x() + (tile.width() - swatchSize) / 2, tile.y() + 4, swatchSize, swatchSize);
            drawMaterialSlotSwatch(graphics, swatch, selection, true);
            if (tile.height() >= 32) {
                String label = SPSGui.ellipsize(this.font, Component.translatable(slot.nameKey()).getString(), Math.round((tile.width() - 6) / 0.48F));
                drawCenteredSmallText(graphics, label, new SPSGui.Rect(tile.x() + 3, tile.bottom() - 10, tile.width() - 6, 8), tile.bottom() - 10, selected ? PipeAppearanceTerminalGui.ACCENT_SELECTED : PipeAppearanceTerminalGui.TEXT_MUTED, 0.48F);
            }
            if (intersects(tile, this.materialStripScrollBounds)) {
                this.addClick(tile, () -> {
                    this.selectedSlotId = slot.id();
                    this.minecraft.setScreen(new PipeCoatingSelectorScreen(this, slot.id(), selection));
                }, materialSlotTooltip(slot, selection));
            }
            x += tileW + gap;
        }
        graphics.disableScissor();
        PipeAppearanceTerminalGui.sideScrollEdges(graphics, this.materialStripScrollBounds, this.materialStripScroll > 0.5D, this.materialStripScroll < this.materialStripMaxScroll - 0.5D);
    }

    private void drawPreviewPane(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        PipeAppearanceTerminalGui.raisedPanel(graphics, rect);
        PipeStyleDefinition style = PipeAppearanceDefinitions.style(this.draftProfile.styleId()).orElse(PipeAppearanceDefinitions.defaultStyle());
        PipeVariantDefinition variant = PipeAppearanceDefinitions.variant(this.draftProfile.variantId()).orElse(PipeAppearanceDefinitions.defaultVariant());
        PipeStyleGeometry geometry = PipeStyleGeometry.resolve(style, variant, this.draftProfile.styleParameters());
        PipeSurfaceModel model = PipeSurfaceModel.build(style.shape(), variant, geometry);
        String primarySlotId = model.slotIds().stream().findFirst().orElse(BODY_SLOT);
        PipeCoatingRenderResolver.ResolvedPipeCoating resolved = resolvedForSlot(this.draftProfile, primarySlotId);
        Map<String, PipeCoatingRenderResolver.ResolvedPipeCoating> resolvedSlots = new LinkedHashMap<>();
        for (String slotId : model.slotIds()) {
            resolvedSlots.put(slotId, resolvedForSlot(this.draftProfile, slotId));
        }
        SPSGui.Rect stage = new SPSGui.Rect(rect.x() + 5, rect.y() + 5, rect.width() - 10, rect.height() - 10);
        this.previewBounds = stage;
        drawPreviewStage(graphics, stage, mouseX, mouseY);

        int chromeHeight = clampInt(stage.height() / 7, 20, 28);
        int footerHeight = stage.height() >= 150 ? 13 : 0;
        int modelTop = stage.y() + chromeHeight + 7;
        int modelBottom = stage.bottom() - footerHeight - 5;
        if (modelBottom - modelTop < 48) {
            footerHeight = 0;
            modelTop = stage.y() + chromeHeight + 5;
            modelBottom = stage.bottom() - 4;
        }
        SPSGui.Rect chrome = new SPSGui.Rect(stage.x() + 6, stage.y() + 5, stage.width() - 12, chromeHeight);
        SPSGui.Rect modelRect = new SPSGui.Rect(stage.x() + 5, modelTop, stage.width() - 10, Math.max(32, modelBottom - modelTop));
        drawPreviewHeader(graphics, chrome, style, variant, resolved, mouseX, mouseY);
        drawUnitPipePreview(graphics, modelRect, this.draftProfile.glow(), geometry, model, resolvedSlots, resolved);
        if (footerHeight > 0) {
            SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.pipe_appearance.preview_drag").getString(), stage.x() + 8, stage.bottom() - 11, PipeAppearanceTerminalGui.TEXT_MUTED, 0.54F);
        }
    }

    private void drawPreviewStage(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        PipeAppearanceTerminalGui.previewStage(graphics, rect, rect.contains(mouseX, mouseY) || this.draggingPreview);
        int railY = rect.y() + (int) Math.round(rect.height() * 0.63D);
        graphics.fill(rect.x() + 10, railY, rect.right() - 10, railY + 1, 0x7746FF7A);
        for (int x = rect.x() + 18; x < rect.right() - 12; x += 18) {
            graphics.fill(x, railY - 2, x + 1, railY + 3, 0x7746FF7A);
        }
    }

    private void drawPreviewHeader(GuiGraphicsExtractor graphics, SPSGui.Rect rect, PipeStyleDefinition style, PipeVariantDefinition variant, PipeCoatingRenderResolver.ResolvedPipeCoating resolved, int mouseX, int mouseY) {
        int gap = 4;
        int sizeLimitByWidth = Math.max(10, (rect.width() - gap * 3) / 4);
        int size = clampInt(Math.min(rect.height() - 4, sizeLimitByWidth), 10, 18);
        int controlsWidth = size * 4 + gap * 3;
        SPSGui.Rect controls = new SPSGui.Rect(rect.right() - controlsWidth, rect.y() + Math.max(1, (rect.height() - size) / 2), controlsWidth, size);
        int tagWidth = controls.x() - rect.x() - 6;
        SPSGui.Rect tag = new SPSGui.Rect(rect.x(), rect.y(), Math.max(0, tagWidth), rect.height());
        if (tag.width() >= 70) {
            graphics.fill(tag.x(), tag.y(), tag.right(), tag.bottom(), 0xB80D1720);
            graphics.outline(tag.x(), tag.y(), tag.width(), tag.height(), 0x6646FF7A);
            int glyphSize = Math.min(16, Math.max(12, tag.height() - 8));
            drawStyleGlyph(graphics, new SPSGui.Rect(tag.x() + 5, tag.y() + (tag.height() - glyphSize) / 2, glyphSize, glyphSize), style, resolved.opaqueTint());
            SPSGui.text(graphics, this.font, SPSGui.ellipsize(this.font, Component.translatable(style.nameKey()).getString(), tag.width() - glyphSize - 18), tag.x() + glyphSize + 12, tag.y() + 4, PipeAppearanceTerminalGui.TEXT);
            if (tag.height() >= 25) {
                Component length = hasTarget()
                        ? Component.translatable("screen.superpipeslide.pipe_appearance.target_length", String.format("%.1f", this.targetLength))
                        : Component.translatable("screen.superpipeslide.pipe_appearance.unit_preview");
                SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, Component.translatable(variant.nameKey()).getString() + " / " + length.getString(), tag.width() - glyphSize - 18), tag.x() + glyphSize + 12, tag.y() + 16, PipeAppearanceTerminalGui.TEXT_MUTED, 0.50F);
            }
        }
        drawPreviewMarkerOverlayControls(graphics, controls, size, mouseX, mouseY);
    }

    private void drawPreviewMarkerOverlayControls(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int size, int mouseX, int mouseY) {
        int gap = 4;
        int x = rect.x();
        int y = rect.y();
        x = drawPreviewMarkerToggle(graphics, x, y, size, SPSGui.Icon.PIPE_PLATFORM, this.previewPlatform, PREVIEW_PLATFORM_COLOR, mouseX, mouseY, () -> this.previewPlatform = !this.previewPlatform, Component.translatable("screen.superpipeslide.pipe_appearance.preview_marker.platform")) + size + gap;
        x = drawPreviewMarkerToggle(graphics, x, y, size, SPSGui.Icon.ONE_WAY, this.previewDirection, PREVIEW_DIRECTION_COLOR, mouseX, mouseY, () -> this.previewDirection = !this.previewDirection, Component.translatable("screen.superpipeslide.pipe_appearance.preview_marker.direction")) + size + gap;
        x = drawPreviewMarkerToggle(graphics, x, y, size, SPSGui.Icon.PIPE_HIGHWAY, this.previewHighway, PREVIEW_HIGHWAY_COLOR, mouseX, mouseY, () -> this.previewHighway = !this.previewHighway, Component.translatable("screen.superpipeslide.pipe_appearance.preview_marker.highway")) + size + gap;
        drawPreviewMarkerToggle(graphics, x, y, size, SPSGui.Icon.PIPE_ACCELERATION, this.previewAcceleration, PREVIEW_ACCELERATION_COLOR, mouseX, mouseY, () -> this.previewAcceleration = !this.previewAcceleration, Component.translatable("screen.superpipeslide.pipe_appearance.preview_marker.acceleration"));
    }

    private void drawBottomActions(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        SPSGui.Rect reset = new SPSGui.Rect(rect.x(), rect.y() + 2, 74, 18);
        drawTextButton(graphics, reset, Component.translatable("screen.superpipeslide.action.reset"), reset.contains(mouseX, mouseY), false, false);
        this.addClick(reset, () -> this.draftProfile = this.currentProfile, Component.translatable("screen.superpipeslide.action.reset"));
        int stateColor = isDirty() ? PipeAppearanceTerminalGui.WARNING : PipeAppearanceTerminalGui.SUCCESS;
        String state = Component.translatable(isDirty() ? "screen.superpipeslide.pipe_appearance.state_unsaved" : "screen.superpipeslide.pipe_appearance.state_saved").getString();
        SPSGui.smallText(graphics, this.font, state, reset.right() + 10, rect.y() + 8, stateColor, 0.62F);
        if (!hasTarget()) {
            SPSGui.Rect save = new SPSGui.Rect(rect.right() - 104, rect.y() + 2, 104, 18);
            drawTextButton(graphics, save, Component.translatable("screen.superpipeslide.pipe_appearance.save_to_tool"), save.contains(mouseX, mouseY), false, true);
            this.addClick(save, () -> apply(ServerboundPipeAppearanceApplyPayload.SCOPE_DRAFT), Component.translatable("screen.superpipeslide.pipe_appearance.save_to_tool.tooltip"));
            return;
        }

        SPSGui.Rect save = new SPSGui.Rect(rect.right() - 286, rect.y() + 2, 92, 18);
        SPSGui.Rect single = new SPSGui.Rect(rect.right() - 188, rect.y() + 2, 86, 18);
        SPSGui.Rect connected = new SPSGui.Rect(rect.right() - 96, rect.y() + 2, 96, 18);
        drawTextButton(graphics, save, Component.translatable("screen.superpipeslide.pipe_appearance.save_to_tool"), save.contains(mouseX, mouseY), false, false);
        drawTextButton(graphics, single, Component.translatable("screen.superpipeslide.pipe_appearance.apply_single"), single.contains(mouseX, mouseY), false, true);
        drawTextButton(graphics, connected, Component.translatable("screen.superpipeslide.pipe_appearance.apply_connected"), connected.contains(mouseX, mouseY), false, false);
        this.addClick(save, () -> apply(ServerboundPipeAppearanceApplyPayload.SCOPE_DRAFT), Component.translatable("screen.superpipeslide.pipe_appearance.save_to_tool.tooltip"));
        this.addClick(single, () -> apply(ServerboundPipeAppearanceApplyPayload.SCOPE_SINGLE), Component.translatable("screen.superpipeslide.pipe_appearance.apply_single.tooltip"));
        this.addClick(connected, () -> apply(ServerboundPipeAppearanceApplyPayload.SCOPE_CONNECTED), Component.translatable("screen.superpipeslide.pipe_appearance.apply_connected.tooltip"));
    }

    private void drawTextButton(GuiGraphicsExtractor graphics, SPSGui.Rect rect, Component text, boolean hovered, boolean disabled, boolean primary) {
        PipeAppearanceTerminalGui.commandButton(graphics, this.font, rect, text, hovered, disabled, primary);
    }

    private void drawCenteredSmallText(GuiGraphicsExtractor graphics, String text, SPSGui.Rect rect, int y, int color, float scale) {
        int x = rect.x() + (rect.width() - SPSGui.scaledWidth(this.font, text, scale)) / 2;
        SPSGui.smallText(graphics, this.font, text, x, y, color, scale);
    }

    private void selectStyle(PipeStyleDefinition style) {
        PipeVariantDefinition variant = PipeAppearanceDefinitions.variant(style.defaultVariantId()).orElse(PipeAppearanceDefinitions.defaultVariant());
        Map<String, PipeCoatingSelection> slots = new LinkedHashMap<>();
        for (MaterialSlotDefinition slot : PipeAppearanceDefinitions.slotsFor(style, variant)) {
            slots.put(slot.id(), this.draftProfile.slotCoatings().getOrDefault(slot.id(), PipeAppearanceDefinitions.defaultSelectionForSlot(slot.id())));
        }
        this.draftProfile = new PipeAppearanceProfile(0, style.id(), style.defaultVariantId(), this.draftProfile.glow(), slots, style.normalizeParameters(this.draftProfile.styleParameters())).normalizedToDefinitions();
        this.selectedSlotId = PipeAppearanceDefinitions.slotsFor(style, variant).isEmpty() ? BODY_SLOT : PipeAppearanceDefinitions.slotsFor(style, variant).getFirst().id();
        this.revealSelectedStyle = true;
    }

    private void setVariant(String variantId) {
        this.draftProfile = new PipeAppearanceProfile(0, this.draftProfile.styleId(), variantId, this.draftProfile.glow(), this.draftProfile.slotCoatings(), this.draftProfile.styleParameters()).normalizedToDefinitions();
        PipeStyleDefinition style = PipeAppearanceDefinitions.style(this.draftProfile.styleId()).orElse(PipeAppearanceDefinitions.defaultStyle());
        PipeVariantDefinition variant = PipeAppearanceDefinitions.variant(this.draftProfile.variantId()).orElse(PipeAppearanceDefinitions.defaultVariant());
        if (PipeAppearanceDefinitions.slotsFor(style, variant).stream().noneMatch(slot -> slot.id().equals(this.selectedSlotId))) {
            this.selectedSlotId = PipeAppearanceDefinitions.slotsFor(style, variant).stream().findFirst().map(MaterialSlotDefinition::id).orElse(BODY_SLOT);
        }
    }

    private void setGlow(boolean glow) {
        this.draftProfile = new PipeAppearanceProfile(0, this.draftProfile.styleId(), this.draftProfile.variantId(), glow, this.draftProfile.slotCoatings(), this.draftProfile.styleParameters()).normalizedToDefinitions();
    }

    void setSlotSelection(String slotId, PipeCoatingSelection selection) {
        Map<String, PipeCoatingSelection> slots = new LinkedHashMap<>(this.draftProfile.slotCoatings());
        slots.put(slotId, selection);
        this.draftProfile = new PipeAppearanceProfile(0, this.draftProfile.styleId(), this.draftProfile.variantId(), this.draftProfile.glow(), slots, this.draftProfile.styleParameters()).normalizedToDefinitions();
    }

    void updateSlotSelectionFromSelector(String slotId, PipeCoatingSelection selection) {
        this.selectedSlotId = slotId;
        setSlotSelection(slotId, selection);
    }

    private void setStyleParameter(String parameterId, double value) {
        PipeStyleDefinition style = PipeAppearanceDefinitions.style(this.draftProfile.styleId()).orElse(PipeAppearanceDefinitions.defaultStyle());
        style.parameter(parameterId).ifPresent(parameter -> {
            Map<String, Double> parameters = new LinkedHashMap<>(this.draftProfile.styleParameters());
            parameters.put(parameter.id(), parameter.clamp(value));
            this.draftProfile = new PipeAppearanceProfile(0, this.draftProfile.styleId(), this.draftProfile.variantId(), this.draftProfile.glow(), this.draftProfile.slotCoatings(), parameters).normalizedToDefinitions();
        });
    }

    private void adjustParameterFromMouse(String parameterId, double mouseX) {
        PipeStyleDefinition style = PipeAppearanceDefinitions.style(this.draftProfile.styleId()).orElse(PipeAppearanceDefinitions.defaultStyle());
        SPSGui.Rect slider = this.parameterSliderBounds.get(parameterId);
        if (slider == null || slider.width() <= 0) {
            return;
        }
        style.parameter(parameterId).ifPresent(parameter -> setStyleParameter(parameterId, parameter.valueAtPercent((mouseX - slider.x()) / slider.width())));
    }

    private void apply(String scope) {
        ClientPacketDistributor.sendToServer(new ServerboundPipeAppearanceApplyPayload(this.appearanceRevision, this.targetConnectionKey, this.draftProfile.withoutServerId(), scope));
        this.currentProfile = this.draftProfile.normalizedToDefinitions();
    }

    private boolean hasTarget() {
        return this.targetConnectionKey > PipeConnection.TRANSIENT_CONNECTION_KEY;
    }

    private boolean isDirty() {
        return this.currentProfile == null || !this.draftProfile.normalizedToDefinitions().contentEquals(this.currentProfile.normalizedToDefinitions());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && this.previewBounds.contains(event.x(), event.y())) {
            if (this.dispatchClickActions(this.clickActions, event.x(), event.y())) {
                return true;
            }
            if (doubleClick) {
                this.previewYaw = PREVIEW_DEFAULT_YAW;
                this.previewPitch = PREVIEW_DEFAULT_PITCH;
                return true;
            }
            this.draggingPreview = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingParameterId != null) {
            adjustParameterFromMouse(this.draggingParameterId, event.x());
            return true;
        }
        if (this.draggingPreview) {
            this.previewYaw += dragX * 0.014D;
            this.previewPitch = Math.max(0.10D, Math.min(1.05D, this.previewPitch - dragY * 0.014D));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.draggingParameterId != null) {
            this.draggingParameterId = null;
            return true;
        }
        if (this.draggingPreview) {
            this.draggingPreview = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.styleRailScrollBounds.contains(mouseX, mouseY) && this.styleRailMaxScroll > 0.5D) {
            this.styleRailScroll = clamp(this.styleRailScroll - scrollY * 22.0D, 0.0D, this.styleRailMaxScroll);
            this.revealSelectedStyle = false;
            return true;
        }
        if (this.tuningScrollBounds.contains(mouseX, mouseY) && this.tuningMaxScroll > 0.5D) {
            this.tuningScroll = clamp(this.tuningScroll - scrollY * 18.0D, 0.0D, this.tuningMaxScroll);
            return true;
        }
        if (this.materialStripScrollBounds.contains(mouseX, mouseY) && this.materialStripMaxScroll > 0.5D) {
            this.materialStripScroll = clamp(this.materialStripScroll - scrollY * 28.0D, 0.0D, this.materialStripMaxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (ClickAction clickAction : this.clickActions) {
            if (clickAction.bounds().contains(mouseX, mouseY)) {
                PipeAppearanceTerminalGui.terminalTooltip(graphics, this.font, clickAction.tooltip(), mouseX, mouseY, new SPSGui.Rect(0, 0, this.width, this.height));
                return;
            }
        }
    }

    private String selectionName(PipeCoatingSelection selection) {
        PipeCoatingSelection normalized = PipeAppearanceDefinitions.normalizeSelection(selection);
        String blockName = PipeCoatingRenderResolver.blockDisplayName(normalized);
        String mode = Component.translatable("screen.superpipeslide.pipe_appearance.dye_mode." + normalized.dyeMode().id()).getString();
        if (normalized.texturePick().type() == PipeTexturePickType.AUTO) {
            return blockName + " / " + mode;
        }
        String pick = normalized.texturePick().type() == PipeTexturePickType.FACE
                ? normalized.texturePick().face()
                        .map(face -> Component.translatable("screen.superpipeslide.pipe_appearance.texture_pick.face." + face.getSerializedName()).getString())
                        .orElse(Component.translatable("screen.superpipeslide.pipe_appearance.texture_pick.auto").getString())
                : Component.translatable("screen.superpipeslide.pipe_appearance.texture_pick.custom").getString();
        return blockName + " / " + pick + " / " + mode;
    }

    private Component materialSlotTooltip(MaterialSlotDefinition slot, PipeCoatingSelection selection) {
        PipeCoatingSelection normalized = PipeAppearanceDefinitions.normalizeSelection(selection);
        String texture = texturePickName(normalized);
        return Component.translatable(
                "screen.superpipeslide.pipe_appearance.material_slot.tooltip",
                Component.translatable(slot.nameKey()),
                selectionName(normalized),
                texture);
    }

    private String texturePickName(PipeCoatingSelection selection) {
        PipeCoatingSelection normalized = PipeAppearanceDefinitions.normalizeSelection(selection);
        if (normalized.texturePick().type() == PipeTexturePickType.AUTO) {
            return Component.translatable("screen.superpipeslide.pipe_appearance.texture_pick.auto").getString();
        }
        if (normalized.texturePick().type() == PipeTexturePickType.FACE) {
            return normalized.texturePick().face()
                    .map(face -> Component.translatable("screen.superpipeslide.pipe_appearance.texture_pick.face." + face.getSerializedName()).getString())
                    .orElse(Component.translatable("screen.superpipeslide.pipe_appearance.texture_pick.auto").getString());
        }
        return normalized.texturePick().fallbackSprite()
                .map(Identifier::toString)
                .orElse(Component.translatable("screen.superpipeslide.pipe_appearance.texture_pick.custom").getString());
    }

    private static boolean intersects(SPSGui.Rect first, SPSGui.Rect second) {
        return first.right() > second.x() && first.x() < second.right() && first.bottom() > second.y() && first.y() < second.bottom();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static PipeAppearanceLayout layoutFor(SPSGui.Rect content) {
        boolean compactWidth = content.width() < 560;
        boolean compactHeight = content.height() < 260;
        int gap = compactWidth || compactHeight ? 4 : 6;
        int headerHeight = compactHeight ? 18 : 22;
        int actionHeight = compactHeight ? 20 : 22;
        int materialHeight = clampInt(content.height() / 7, compactHeight ? 42 : 50, compactHeight ? 50 : 62);
        int railWidth = compactWidth ? 40 : clampInt(content.width() / 14, 52, 62);
        int previewMinWidth = compactWidth ? 172 : 252;
        int desiredTuningWidth = clampInt(content.width() / 4, compactWidth ? 132 : 164, compactWidth ? 158 : 208);
        int availableAfterRail = Math.max(0, content.width() - railWidth - gap * 2);
        int tuningWidth = Math.min(desiredTuningWidth, Math.max(112, availableAfterRail - previewMinWidth));
        if (availableAfterRail - tuningWidth < 132) {
            tuningWidth = Math.max(104, availableAfterRail - 132);
        }
        return new PipeAppearanceLayout(railWidth, tuningWidth, materialHeight, actionHeight, headerHeight, gap);
    }

    private record PipeAppearanceLayout(int railWidth, int tuningWidth, int materialHeight, int actionHeight, int headerHeight, int gap) {}

    private static PipeCoatingRenderResolver.ResolvedPipeCoating resolvedForSlot(PipeAppearanceProfile profile, String slotId) {
        return PipeCoatingRenderResolver.resolve(PipeAppearanceDefinitions.selectionFor(profile, slotId));
    }

    private void resetPreviewMarkers() {
        this.previewAcceleration = false;
        this.previewHighway = false;
        this.previewDirection = false;
        this.previewPlatform = false;
        this.previewDirectionLimit = 1;
    }

    private void drawMaterialSlotSwatch(GuiGraphicsExtractor graphics, SPSGui.Rect rect, PipeCoatingSelection selection, boolean framed) {
        PipeCoatingSelection normalized = PipeAppearanceDefinitions.normalizeSelection(selection);
        if (normalized.dyeMode() == PipeCoatingDyeMode.MULTIPLY) {
            drawSpriteSwatch(graphics, rect, PipeCoatingRenderResolver.spriteFor(PipeCoatingRenderResolver.blockFor(normalized.blockId()), normalized.texturePick()), framed, normalized.dyeColor());
            return;
        }
        drawTextureSwatch(graphics, rect, PipeCoatingRenderResolver.resolve(normalized), framed);
    }

    private void drawSpriteSwatch(GuiGraphicsExtractor graphics, SPSGui.Rect rect, TextureAtlasSprite sprite, boolean framed, int color) {
        if (framed) {
            PipeAppearanceTerminalGui.swatchFrame(graphics, rect, false, false);
        }
        Vec2 a = new Vec2(rect.x() + 1, rect.y() + 1);
        Vec2 b = new Vec2(rect.right() - 1, rect.y() + 1);
        Vec2 c = new Vec2(rect.right() - 1, rect.bottom() - 1);
        Vec2 d = new Vec2(rect.x() + 1, rect.bottom() - 1);
        SmoothGuiPrimitives.texturedQuad(
                graphics,
                sprite,
                a,
                b,
                c,
                d,
                color,
                sprite.getU(0.0015F),
                sprite.getU(0.9985F),
                sprite.getV(0.0015F),
                sprite.getV(0.9985F));
        if (framed) {
            graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), PipeAppearanceTerminalGui.BORDER_MUTED);
        }
    }

    private void drawTextureSwatch(GuiGraphicsExtractor graphics, SPSGui.Rect rect, PipeCoatingRenderResolver.ResolvedPipeCoating resolved, boolean framed) {
        if (framed) {
            PipeAppearanceTerminalGui.swatchFrame(graphics, rect, false, false);
        }
        Vec2 a = new Vec2(rect.x() + 1, rect.y() + 1);
        Vec2 b = new Vec2(rect.right() - 1, rect.y() + 1);
        Vec2 c = new Vec2(rect.right() - 1, rect.bottom() - 1);
        Vec2 d = new Vec2(rect.x() + 1, rect.bottom() - 1);
        SmoothGuiPrimitives.texturedQuad(
                graphics,
                resolved.textureId(),
                a,
                b,
                c,
                d,
                0xFFFFFFFF,
                resolved.u(0.0015F),
                resolved.u(0.9985F),
                resolved.v(0.0015F),
                resolved.v(0.9985F));
        if (framed) {
            graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), PipeAppearanceTerminalGui.BORDER_MUTED);
        }
    }

    private static void drawWorkbenchButton(GuiGraphicsExtractor graphics, SPSGui.Rect rect, boolean hovered, boolean selected, int fillColor) {
        int fill = selected ? 0xF7143224 : hovered ? 0xF711281D : PipeAppearanceTerminalGui.SURFACE_RAISED;
        int border = selected ? PipeAppearanceTerminalGui.ACCENT_SELECTED : hovered ? PipeAppearanceTerminalGui.ACCENT : PipeAppearanceTerminalGui.BORDER_MUTED;
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), border);
        graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.y() + 2, selected ? 0x4446FF7A : 0x22143D2A);
    }

    private int drawPreviewMarkerToggle(GuiGraphicsExtractor graphics, int x, int y, int size, SPSGui.Icon icon, boolean active, int color, int mouseX, int mouseY, Runnable action, Component tooltip) {
        SPSGui.Rect rect = new SPSGui.Rect(x, y, size, size);
        boolean hovered = rect.contains(mouseX, mouseY);
        PipeAppearanceTerminalGui.iconButton(graphics, rect, icon, active, hovered, active ? color : PipeAppearanceTerminalGui.TEXT_MUTED);
        this.addClick(rect, action, tooltip);
        return x;
    }

    private void drawUnitPipePreview(GuiGraphicsExtractor graphics, SPSGui.Rect rect, boolean glow, PipeStyleGeometry geometry, PipeSurfaceModel model, Map<String, PipeCoatingRenderResolver.ResolvedPipeCoating> coatings, PipeCoatingRenderResolver.ResolvedPipeCoating fallbackCoating) {
        int bodyColor = fallbackCoating.opaqueTint();
        int outlineColor = shadeColor(bodyColor, new Vec3Like(0.0D, 1.0D, 0.0D), 0.66D, 0.95D);
        List<PreviewModelSection> sections = new ArrayList<>();
        int sectionCount = 6;
        double previewStartX = -0.62D;
        double previewLength = 1.24D;
        for (int i = 0; i < sectionCount; i++) {
            double x = previewStartX + previewLength * i / (sectionCount - 1);
            sections.add(previewModelSection(model, x, geometry.slideContactY(), x - previewStartX));
        }
        PreviewFit fit = previewFit(rect, sections, model.boxes());
        double scale = fit.scale();
        double centerX = fit.centerX();
        double centerY = fit.centerY();

        List<PreviewFace> bodyFaces = new ArrayList<>();
        List<PreviewFace> markerFaces = new ArrayList<>();
        for (int s = 0; s + 1 < sections.size(); s++) {
            PreviewModelSection sectionA = sections.get(s);
            PreviewModelSection sectionB = sections.get(s + 1);
            int limit = Math.min(sectionA.surfaces().size(), sectionB.surfaces().size());
            for (int i = 0; i < limit; i++) {
                PreviewSurface surfaceA = sectionA.surfaces().get(i);
                PreviewSurface surfaceB = sectionB.surfaces().get(i);
                if (!surfaceA.render() || !surfaceB.render()) {
                    continue;
                }
                PipeCoatingRenderResolver.ResolvedPipeCoating coating = coatings.getOrDefault(surfaceA.slotId(), fallbackCoating);
                Vec3Like normal = rotatedNormal(faceNormal(surfaceA.a(), surfaceB.a(), surfaceA.b()));
                int color = shadeColor(coating.opaqueTint(), normal, glow ? 0.90D : 0.72D, glow ? 1.15D : 1.08D);
                addPreviewSurfaceMappedFaces(bodyFaces, surfaceA.a(), surfaceA.b(), surfaceB.b(), surfaceB.a(), sectionA.distance(), sectionB.distance(), surfaceA.vStart(), surfaceA.vEnd(), color, coating, centerX, centerY, scale);
            }
        }
        for (int s = 0; s + 1 < sections.size(); s++) {
            addPreviewPatternedStructuralBoxes(bodyFaces, sections.get(s), sections.get(s + 1), model.boxes(), coatings, fallbackCoating, glow, centerX, centerY, scale);
        }
        for (int s = 0; s + 1 < sections.size(); s++) {
            addPreviewDecorativeCoatingBands(bodyFaces, sections.get(s), sections.get(s + 1), model, coatings, fallbackCoating, glow, centerX, centerY, scale);
        }
        TextureAtlasSprite markerSprite = previewMarkerSprite();
        for (int s = 0; s + 1 < sections.size(); s++) {
            addPreviewFeatureMarkers(markerFaces, sections.get(s), sections.get(s + 1), model.lanes(), markerSprite, previewLength, centerX, centerY, scale);
        }

        bodyFaces.sort(Comparator.comparingDouble(PreviewFace::depth));
        markerFaces.sort(Comparator.comparingDouble(PreviewFace::depth));
        graphics.enableScissor(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.bottom() - 1);
        for (PreviewFace face : bodyFaces) {
            SmoothGuiPrimitives.texturedQuad(graphics, face.textureId(), face.a().xy(), face.b().xy(), face.c().xy(), face.d().xy(), face.color(), face.u0(), face.u1(), face.v0(), face.v1());
        }
        double animationTime = System.nanoTime() / 1_000_000_000.0D;
        for (PreviewFace face : markerFaces) {
            int color = previewAnimatedMarkerColor(face.color(), face.animationKind(), face.animationPhase(), animationTime);
            SmoothGuiPrimitives.texturedQuad(graphics, face.textureId(), face.a().xy(), face.b().xy(), face.c().xy(), face.d().xy(), color, face.u0(), face.u1(), face.v0(), face.v1());
        }

        for (int i = 0; i < sections.size(); i++) {
            if (i != 0 && i != sections.size() - 1) {
                continue;
            }
            drawPreviewSectionOutline(graphics, sections.get(i), centerX, centerY, scale, glow ? bodyColor : outlineColor, glow ? 1.7D : 1.35D);
        }
        drawSlidePositionMarker(graphics, centerX, centerY, scale);
        graphics.disableScissor();
    }

    private static TextureAtlasSprite previewMarkerSprite() {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(PREVIEW_MARKER_TEXTURE);
    }

    private static PreviewModelSection previewModelSection(PipeSurfaceModel model, double x, double slideContactY, double distance) {
        List<PreviewSurface> surfaces = new ArrayList<>();
        for (PipeSurfaceModel.LocalSurface surface : model.surfaces()) {
            surfaces.add(new PreviewSurface(
                    surface.slotId(),
                    new LocalPoint(x, surface.ay() - slideContactY, surface.ax()),
                    new LocalPoint(x, surface.by() - slideContactY, surface.bx()),
                    surface.vStart(),
                    surface.vEnd(),
                    surface.render()));
        }
        return new PreviewModelSection(List.copyOf(surfaces), model.perimeter(), distance, new LocalPoint(x, -slideContactY, 0.0D));
    }

    private void addPreviewPatternedStructuralBoxes(List<PreviewFace> faces, PreviewModelSection previous, PreviewModelSection current, List<PipeSurfaceModel.PatternedBox> boxes, Map<String, PipeCoatingRenderResolver.ResolvedPipeCoating> coatings, PipeCoatingRenderResolver.ResolvedPipeCoating fallbackCoating, boolean glow, double centerX, double centerY, double scale) {
        if (boxes.isEmpty()) {
            return;
        }
        for (PipeSurfaceModel.PatternedBox box : boxes) {
            PipeCoatingRenderResolver.ResolvedPipeCoating coating = coatings.getOrDefault(box.slotId(), fallbackCoating);
            double period = Math.max(SURFACE_UV_EPSILON, box.period());
            double length = Math.max(SURFACE_UV_EPSILON, box.length());
            double start = previous.distance();
            double end = current.distance();
            int first = (int) Math.floor((start - box.phase()) / period) - 1;
            int last = (int) Math.ceil((end - box.phase()) / period) + 1;
            for (int i = first; i <= last; i++) {
                double boxStart = box.phase() + i * period;
                double boxEnd = boxStart + length;
                addPreviewPatternedBoxRange(faces, previous, current, Math.max(start, boxStart), Math.min(end, boxEnd), boxStart, boxEnd, box, coating, glow, centerX, centerY, scale);
            }
        }
    }

    private void addPreviewPatternedBoxRange(List<PreviewFace> faces, PreviewModelSection previous, PreviewModelSection current, double uStart, double uEnd, double boxStart, double boxEnd, PipeSurfaceModel.PatternedBox box, PipeCoatingRenderResolver.ResolvedPipeCoating coating, boolean glow, double centerX, double centerY, double scale) {
        double segmentLength = current.distance() - previous.distance();
        if (segmentLength <= SURFACE_UV_EPSILON || uEnd <= uStart + SURFACE_UV_EPSILON) {
            return;
        }
        double uT0 = (uStart - previous.distance()) / segmentLength;
        double uT1 = (uEnd - previous.distance()) / segmentLength;
        double left = Math.min(box.left(), box.right());
        double right = Math.max(box.left(), box.right());
        double bottom = Math.min(box.bottom(), box.top());
        double top = Math.max(box.bottom(), box.top());
        LocalPoint slb = previewSectionLocalPoint(previous, current, uT0, left, bottom);
        LocalPoint slt = previewSectionLocalPoint(previous, current, uT0, left, top);
        LocalPoint srb = previewSectionLocalPoint(previous, current, uT0, right, bottom);
        LocalPoint srt = previewSectionLocalPoint(previous, current, uT0, right, top);
        LocalPoint elb = previewSectionLocalPoint(previous, current, uT1, left, bottom);
        LocalPoint elt = previewSectionLocalPoint(previous, current, uT1, left, top);
        LocalPoint erb = previewSectionLocalPoint(previous, current, uT1, right, bottom);
        LocalPoint ert = previewSectionLocalPoint(previous, current, uT1, right, top);
        addPreviewBoxSurface(faces, slt, srt, ert, elt, uStart, uEnd, left, right, coating, glow, centerX, centerY, scale);
        addPreviewBoxSurface(faces, srb, slb, elb, erb, uStart, uEnd, left, right, coating, glow, centerX, centerY, scale);
        addPreviewBoxSurface(faces, slb, slt, elt, elb, uStart, uEnd, bottom, top, coating, glow, centerX, centerY, scale);
        addPreviewBoxSurface(faces, srt, srb, erb, ert, uStart, uEnd, bottom, top, coating, glow, centerX, centerY, scale);
        if (uStart <= boxStart + SURFACE_UV_EPSILON) {
            addPreviewBoxSurface(faces, slb, slt, srt, srb, left, right, bottom, top, coating, glow, centerX, centerY, scale);
        }
        if (uEnd >= boxEnd - SURFACE_UV_EPSILON) {
            addPreviewBoxSurface(faces, erb, ert, elt, elb, left, right, bottom, top, coating, glow, centerX, centerY, scale);
        }
    }

    private void addPreviewBoxSurface(List<PreviewFace> faces, LocalPoint p00, LocalPoint p01, LocalPoint p11, LocalPoint p10, double uStartWorld, double uEndWorld, double vStartWorld, double vEndWorld, PipeCoatingRenderResolver.ResolvedPipeCoating coating, boolean glow, double centerX, double centerY, double scale) {
        Vec3Like normal = rotatedNormal(faceNormal(p00, p01, p10));
        int color = shadeColor(coating.opaqueTint(), normal, glow ? 0.90D : 0.72D, glow ? 1.15D : 1.08D);
        addPreviewSurfaceMappedFaces(faces, p00, p01, p11, p10, uStartWorld, uEndWorld, vStartWorld, vEndWorld, color, coating, centerX, centerY, scale);
    }

    private static LocalPoint previewSectionLocalPoint(PreviewModelSection section, double localX, double localY) {
        return new LocalPoint(section.center().x(), section.center().y() + localY, localX);
    }

    private static LocalPoint previewSectionLocalPoint(PreviewModelSection previous, PreviewModelSection current, double uT, double localX, double localY) {
        return previewLerp(previewSectionLocalPoint(previous, localX, localY), previewSectionLocalPoint(current, localX, localY), uT);
    }

    private void addPreviewDecorativeCoatingBands(List<PreviewFace> faces, PreviewModelSection previous, PreviewModelSection current, PipeSurfaceModel model, Map<String, PipeCoatingRenderResolver.ResolvedPipeCoating> coatings, PipeCoatingRenderResolver.ResolvedPipeCoating fallbackCoating, boolean glow, double centerX, double centerY, double scale) {
        if (model.bands().isEmpty()) {
            return;
        }
        for (PipeSurfaceModel.CoatingBand band : model.bands()) {
            PipeCoatingRenderResolver.ResolvedPipeCoating coating = coatings.getOrDefault(band.slotId(), fallbackCoating);
            double start = previous.distance();
            double end = current.distance();
            int first = (int) Math.floor((start - band.phase()) / band.period()) - 1;
            int last = (int) Math.ceil((end - band.phase()) / band.period()) + 1;
            for (int i = first; i <= last; i++) {
                double u0 = band.phase() + i * band.period();
                addPreviewCoatingBand(faces, previous, current, u0, u0 + band.length(), band.vCenter(), band.vWidth(), coating, glow, centerX, centerY, scale, band.layer());
            }
        }
    }

    private void addPreviewCoatingBand(List<PreviewFace> faces, PreviewModelSection previous, PreviewModelSection current, double uStart, double uEnd, double vCenter, double vWidth, PipeCoatingRenderResolver.ResolvedPipeCoating coating, boolean glow, double centerX, double centerY, double scale, int layer) {
        double clippedUStart = Math.max(uStart, previous.distance());
        double clippedUEnd = Math.min(uEnd, current.distance());
        if (clippedUEnd <= clippedUStart + SURFACE_UV_EPSILON || vWidth <= SURFACE_UV_EPSILON) {
            return;
        }
        addPreviewCoatingRange(faces, previous, current, clippedUStart, clippedUEnd, vCenter - vWidth * 0.5D, vCenter + vWidth * 0.5D, coating, glow, centerX, centerY, scale, layer);
    }

    private void addPreviewCoatingRange(List<PreviewFace> faces, PreviewModelSection previous, PreviewModelSection current, double uStart, double uEnd, double vStart, double vEnd, PipeCoatingRenderResolver.ResolvedPipeCoating coating, boolean glow, double centerX, double centerY, double scale, int layer) {
        double segmentLength = current.distance() - previous.distance();
        if (segmentLength <= SURFACE_UV_EPSILON) {
            return;
        }
        int limit = Math.min(previous.surfaces().size(), current.surfaces().size());
        for (int i = 0; i < limit; i++) {
            PreviewSurface previousSurface = previous.surfaces().get(i);
            PreviewSurface currentSurface = current.surfaces().get(i);
            double overlapStart = Math.max(vStart, previousSurface.vStart());
            double overlapEnd = Math.min(vEnd, previousSurface.vEnd());
            if (overlapEnd <= overlapStart + SURFACE_UV_EPSILON) {
                continue;
            }
            double t0 = (overlapStart - previousSurface.vStart()) / (previousSurface.vEnd() - previousSurface.vStart());
            double t1 = (overlapEnd - previousSurface.vStart()) / (previousSurface.vEnd() - previousSurface.vStart());
            double uT0 = (uStart - previous.distance()) / segmentLength;
            double uT1 = (uEnd - previous.distance()) / segmentLength;
            LocalPoint p00 = previewLerp(previewSurfacePoint(previousSurface.a(), previousSurface.b(), t0), previewSurfacePoint(currentSurface.a(), currentSurface.b(), t0), uT0);
            LocalPoint p01 = previewLerp(previewSurfacePoint(previousSurface.a(), previousSurface.b(), t1), previewSurfacePoint(currentSurface.a(), currentSurface.b(), t1), uT0);
            LocalPoint p11 = previewLerp(previewSurfacePoint(previousSurface.a(), previousSurface.b(), t1), previewSurfacePoint(currentSurface.a(), currentSurface.b(), t1), uT1);
            LocalPoint p10 = previewLerp(previewSurfacePoint(previousSurface.a(), previousSurface.b(), t0), previewSurfacePoint(currentSurface.a(), currentSurface.b(), t0), uT1);
            Vec3Like normal = previewMarkerSurfaceNormal(p00, p01, p10, previous.center(), current.center());
            int color = shadeColor(coating.opaqueTint(), rotatedNormal(normal), glow ? 0.90D : 0.72D, glow ? 1.15D : 1.08D);
            addPreviewSurfaceMappedFaces(
                    faces,
                    previewOffset(p00, normal, layer),
                    previewOffset(p01, normal, layer),
                    previewOffset(p11, normal, layer),
                    previewOffset(p10, normal, layer),
                    uStart,
                    uEnd,
                    overlapStart,
                    overlapEnd,
                    color,
                    coating,
                    centerX,
                    centerY,
                    scale);
        }
    }

    private void addPreviewFeatureMarkers(List<PreviewFace> faces, PreviewModelSection previous, PreviewModelSection current, PipeSurfaceModel.MarkerLanes lanes, TextureAtlasSprite markerSprite, double totalLength, double centerX, double centerY, double scale) {
        if (this.previewPlatform) {
            addPreviewPlatformDockMarkers(faces, previous, current, lanes, totalLength, markerSprite, centerX, centerY, scale);
        }
        if (this.previewHighway) {
            addPreviewHighwaySpineMarkers(faces, previous, current, lanes, markerSprite, centerX, centerY, scale);
        }
        if (this.previewAcceleration) {
            addPreviewAccelerationImpulseMarkers(faces, previous, current, lanes, markerSprite, centerX, centerY, scale);
        }
        if (this.previewDirection) {
            addPreviewDirectionMarkers(faces, previous, current, lanes.directionCenter(), lanes.directionWidth(), this.previewDirectionLimit, markerSprite, centerX, centerY, scale);
        }
    }

    private void addPreviewPlatformDockMarkers(List<PreviewFace> faces, PreviewModelSection previous, PreviewModelSection current, PipeSurfaceModel.MarkerLanes lanes, double totalLength, TextureAtlasSprite sprite, double centerX, double centerY, double scale) {
        double width = lanes.platformWidth() * 1.28D;
        addPreviewContinuousMarkerBand(faces, previous, current, lanes.platformCenter(), width * 1.18D, PREVIEW_PLATFORM_SHADOW_COLOR, sprite, centerX, centerY, scale, 0);
        addPreviewContinuousMarkerBand(faces, previous, current, lanes.platformCenter(), width, PREVIEW_PLATFORM_COLOR, sprite, centerX, centerY, scale, 1);
        addPreviewContinuousMarkerBand(faces, previous, current, lanes.platformCenter() - width * 0.42D, width * 0.105D, PREVIEW_PLATFORM_SAFETY_COLOR, sprite, centerX, centerY, scale, 2);
        addPreviewContinuousMarkerBand(faces, previous, current, lanes.platformCenter() + width * 0.42D, width * 0.105D, PREVIEW_PLATFORM_SAFETY_COLOR, sprite, centerX, centerY, scale, 2);
        addPreviewPatternedMarkerBand(faces, previous, current, lanes.platformCenter(), width * 0.24D, 0.44D, 0.055D, 0.10D, PREVIEW_PLATFORM_SHADOW_COLOR, sprite, centerX, centerY, scale, 3);
        addPreviewMarkerBand(faces, previous, current, 0.0D, Math.min(0.30D, totalLength * 0.42D), lanes.platformCenter(), width * 1.38D, PREVIEW_PLATFORM_EDGE_COLOR, sprite, centerX, centerY, scale, 4);
        addPreviewMarkerBand(faces, previous, current, Math.max(0.0D, totalLength - 0.30D), totalLength, lanes.platformCenter(), width * 1.38D, PREVIEW_PLATFORM_EDGE_COLOR, sprite, centerX, centerY, scale, 4);
    }

    private void addPreviewHighwaySpineMarkers(List<PreviewFace> faces, PreviewModelSection previous, PreviewModelSection current, PipeSurfaceModel.MarkerLanes lanes, TextureAtlasSprite sprite, double centerX, double centerY, double scale) {
        double width = lanes.highwayWidth();
        addPreviewContinuousMarkerBand(faces, previous, current, lanes.highwayCenter(), width * 0.22D, PREVIEW_HIGHWAY_COLOR, sprite, centerX, centerY, scale, 0);
        addPreviewContinuousMarkerBand(faces, previous, current, lanes.highwayCenter() - width * 0.52D, width * 0.14D, PREVIEW_HIGHWAY_EDGE_COLOR, sprite, centerX, centerY, scale, 0);
        addPreviewContinuousMarkerBand(faces, previous, current, lanes.highwayCenter() + width * 0.52D, width * 0.14D, PREVIEW_HIGHWAY_EDGE_COLOR, sprite, centerX, centerY, scale, 0);
        addPreviewPatternedMarkerDiamond(faces, previous, current, lanes.highwayCenter(), width * 0.86D, 0.92D, 0.36D, 0.20D, PREVIEW_HIGHWAY_HIGHLIGHT_COLOR, sprite, centerX, centerY, scale, 2, PREVIEW_ANIMATION_HIGHWAY);
        addPreviewPatternedMarkerDiamond(faces, previous, current, lanes.highwayCenter() - width * 0.52D, width * 0.34D, 0.92D, 0.26D, 0.32D, PREVIEW_HIGHWAY_HIGHLIGHT_COLOR, sprite, centerX, centerY, scale, 3, PREVIEW_ANIMATION_HIGHWAY);
        addPreviewPatternedMarkerDiamond(faces, previous, current, lanes.highwayCenter() + width * 0.52D, width * 0.34D, 0.92D, 0.26D, 0.32D, PREVIEW_HIGHWAY_HIGHLIGHT_COLOR, sprite, centerX, centerY, scale, 3, PREVIEW_ANIMATION_HIGHWAY);
    }

    private void addPreviewAccelerationImpulseMarkers(List<PreviewFace> faces, PreviewModelSection previous, PreviewModelSection current, PipeSurfaceModel.MarkerLanes lanes, TextureAtlasSprite sprite, double centerX, double centerY, double scale) {
        double width = lanes.accelerationWidth();
        double period = 0.74D;
        double length = 0.48D;
        addPreviewPatternedMarkerDiamond(faces, previous, current, lanes.accelerationCenter(), width * 0.92D, period, length, 0.04D, PREVIEW_ACCELERATION_COLOR, sprite, centerX, centerY, scale, 1, PREVIEW_ANIMATION_ACCELERATION);
        addPreviewPatternedMarkerDiamond(faces, previous, current, lanes.accelerationCenter(), width * 0.78D, period, length * 0.62D, 0.26D, PREVIEW_ACCELERATION_COLOR, sprite, centerX, centerY, scale, 2, PREVIEW_ANIMATION_ACCELERATION);
        addPreviewPatternedMarkerDiamond(faces, previous, current, lanes.accelerationCenter(), width * 0.34D, period, length * 0.34D, 0.42D, PREVIEW_ACCELERATION_CORE_COLOR, sprite, centerX, centerY, scale, 3, PREVIEW_ANIMATION_ACCELERATION);
    }

    private void addPreviewContinuousMarkerBand(List<PreviewFace> faces, PreviewModelSection previous, PreviewModelSection current, double vCenter, double vWidth, int color, TextureAtlasSprite sprite, double centerX, double centerY, double scale, int layer) {
        addPreviewMarkerBand(faces, previous, current, previous.distance(), current.distance(), vCenter, vWidth, color, sprite, centerX, centerY, scale, layer);
    }

    private void addPreviewPatternedMarkerBand(List<PreviewFace> faces, PreviewModelSection previous, PreviewModelSection current, double vCenter, double vWidth, double period, double length, double phase, int color, TextureAtlasSprite sprite, double centerX, double centerY, double scale, int layer) {
        double start = previous.distance();
        double end = current.distance();
        int first = (int) Math.floor((start - phase) / period) - 1;
        int last = (int) Math.ceil((end - phase) / period) + 1;
        for (int i = first; i <= last; i++) {
            double u0 = phase + i * period;
            addPreviewMarkerBand(faces, previous, current, u0, u0 + length, vCenter, vWidth, color, sprite, centerX, centerY, scale, layer);
        }
    }

    private void addPreviewPatternedMarkerDiamond(List<PreviewFace> faces, PreviewModelSection previous, PreviewModelSection current, double vCenter, double vWidth, double period, double length, double phase, int color, TextureAtlasSprite sprite, double centerX, double centerY, double scale, int layer, int animationKind) {
        double start = previous.distance();
        double end = current.distance();
        int first = (int) Math.floor((start - phase) / period) - 1;
        int last = (int) Math.ceil((end - phase) / period) + 1;
        for (int i = first; i <= last; i++) {
            double u0 = phase + i * period;
            addPreviewMarkerDiamond(faces, previous, current, u0, u0 + length, vCenter, vWidth, color, sprite, centerX, centerY, scale, layer, animationKind, i * 0.19D);
        }
    }

    private void addPreviewDirectionMarkers(List<PreviewFace> faces, PreviewModelSection previous, PreviewModelSection current, double vCenter, double vWidth, int directionLimit, TextureAtlasSprite sprite, double centerX, double centerY, double scale) {
        double period = 1.12D;
        double start = previous.distance();
        double end = current.distance();
        int first = (int) Math.floor(start / period) - 1;
        int last = (int) Math.ceil(end / period) + 1;
        for (int i = first; i <= last; i++) {
            double base = i * period;
            if (directionLimit >= 0) {
                addPreviewMarkerBand(faces, previous, current, base + 0.05D, base + 0.28D, vCenter, vWidth * 0.42D, PREVIEW_DIRECTION_COLOR, sprite, centerX, centerY, scale, 2);
                addPreviewMarkerTaperedBand(faces, previous, current, base + 0.23D, base + 0.55D, vCenter, vWidth * 1.06D, vWidth * 0.08D, PREVIEW_DIRECTION_COLOR, sprite, centerX, centerY, scale, 3, PREVIEW_ANIMATION_DIRECTION, i * 0.11D);
                addPreviewMarkerTaperedBand(faces, previous, current, base + 0.34D, base + 0.49D, vCenter, vWidth * 0.42D, vWidth * 0.08D, PREVIEW_DIRECTION_CORE_COLOR, sprite, centerX, centerY, scale, 4, PREVIEW_ANIMATION_DIRECTION, i * 0.11D + 0.08D);
            } else {
                addPreviewMarkerBand(faces, previous, current, base + 0.32D, base + 0.55D, vCenter, vWidth * 0.42D, PREVIEW_DIRECTION_COLOR, sprite, centerX, centerY, scale, 2);
                addPreviewMarkerTaperedBand(faces, previous, current, base + 0.05D, base + 0.37D, vCenter, vWidth * 0.08D, vWidth * 1.06D, PREVIEW_DIRECTION_COLOR, sprite, centerX, centerY, scale, 3, PREVIEW_ANIMATION_DIRECTION, i * 0.11D);
                addPreviewMarkerTaperedBand(faces, previous, current, base + 0.11D, base + 0.26D, vCenter, vWidth * 0.08D, vWidth * 0.42D, PREVIEW_DIRECTION_CORE_COLOR, sprite, centerX, centerY, scale, 4, PREVIEW_ANIMATION_DIRECTION, i * 0.11D + 0.08D);
            }
        }
    }

    private void addPreviewMarkerDiamond(List<PreviewFace> faces, PreviewModelSection previous, PreviewModelSection current, double uStart, double uEnd, double vCenter, double vWidth, int color, TextureAtlasSprite sprite, double centerX, double centerY, double scale, int layer, int animationKind, double animationPhase) {
        double mid = (uStart + uEnd) * 0.5D;
        double tipWidth = Math.max(0.004D, vWidth * 0.06D);
        addPreviewMarkerTaperedBand(faces, previous, current, uStart, mid, vCenter, tipWidth, vWidth, color, sprite, centerX, centerY, scale, layer, animationKind, animationPhase);
        addPreviewMarkerTaperedBand(faces, previous, current, mid, uEnd, vCenter, vWidth, tipWidth, color, sprite, centerX, centerY, scale, layer, animationKind, animationPhase);
    }

    private void addPreviewMarkerTaperedBand(List<PreviewFace> faces, PreviewModelSection previous, PreviewModelSection current, double uStart, double uEnd, double vCenter, double startWidth, double endWidth, int color, TextureAtlasSprite sprite, double centerX, double centerY, double scale, int layer) {
        addPreviewMarkerTaperedBand(faces, previous, current, uStart, uEnd, vCenter, startWidth, endWidth, color, sprite, centerX, centerY, scale, layer, PREVIEW_ANIMATION_NONE, 0.0D);
    }

    private void addPreviewMarkerTaperedBand(List<PreviewFace> faces, PreviewModelSection previous, PreviewModelSection current, double uStart, double uEnd, double vCenter, double startWidth, double endWidth, int color, TextureAtlasSprite sprite, double centerX, double centerY, double scale, int layer, int animationKind, double animationPhase) {
        double clippedUStart = Math.max(uStart, previous.distance());
        double clippedUEnd = Math.min(uEnd, current.distance());
        if (clippedUEnd <= clippedUStart + SURFACE_UV_EPSILON || uEnd <= uStart + SURFACE_UV_EPSILON) {
            return;
        }
        double t0 = (clippedUStart - uStart) / (uEnd - uStart);
        double t1 = (clippedUEnd - uStart) / (uEnd - uStart);
        double width0 = startWidth + (endWidth - startWidth) * t0;
        double width1 = startWidth + (endWidth - startWidth) * t1;
        if (width0 <= SURFACE_UV_EPSILON && width1 <= SURFACE_UV_EPSILON) {
            return;
        }
        addPreviewMarkerTaperedRange(
                faces,
                previous,
                current,
                clippedUStart,
                clippedUEnd,
                vCenter - width0 * 0.5D,
                vCenter + width0 * 0.5D,
                vCenter - width1 * 0.5D,
                vCenter + width1 * 0.5D,
                color,
                sprite,
                centerX,
                centerY,
                scale,
                layer,
                animationKind,
                animationPhase);
    }

    private void addPreviewMarkerTaperedRange(List<PreviewFace> faces, PreviewModelSection previous, PreviewModelSection current, double uStart, double uEnd, double vStart0, double vEnd0, double vStart1, double vEnd1, int color, TextureAtlasSprite sprite, double centerX, double centerY, double scale, int layer, int animationKind, double animationPhase) {
        double segmentLength = current.distance() - previous.distance();
        if (segmentLength <= SURFACE_UV_EPSILON) {
            return;
        }
        double minV = Math.min(Math.min(vStart0, vEnd0), Math.min(vStart1, vEnd1));
        double maxV = Math.max(Math.max(vStart0, vEnd0), Math.max(vStart1, vEnd1));
        int limit = Math.min(previous.surfaces().size(), current.surfaces().size());
        for (int i = 0; i < limit; i++) {
            PreviewSurface previousSurface = previous.surfaces().get(i);
            PreviewSurface currentSurface = current.surfaces().get(i);
            double faceStart = previousSurface.vStart();
            double faceEnd = previousSurface.vEnd();
            if (minV < faceStart - SURFACE_UV_EPSILON || maxV > faceEnd + SURFACE_UV_EPSILON) {
                continue;
            }
            double uT0 = (uStart - previous.distance()) / segmentLength;
            double uT1 = (uEnd - previous.distance()) / segmentLength;
            double t00 = (vStart0 - faceStart) / (faceEnd - faceStart);
            double t01 = (vEnd0 - faceStart) / (faceEnd - faceStart);
            double t11 = (vEnd1 - faceStart) / (faceEnd - faceStart);
            double t10 = (vStart1 - faceStart) / (faceEnd - faceStart);
            LocalPoint p00 = previewLerp(previewSurfacePoint(previousSurface.a(), previousSurface.b(), t00), previewSurfacePoint(currentSurface.a(), currentSurface.b(), t00), uT0);
            LocalPoint p01 = previewLerp(previewSurfacePoint(previousSurface.a(), previousSurface.b(), t01), previewSurfacePoint(currentSurface.a(), currentSurface.b(), t01), uT0);
            LocalPoint p11 = previewLerp(previewSurfacePoint(previousSurface.a(), previousSurface.b(), t11), previewSurfacePoint(currentSurface.a(), currentSurface.b(), t11), uT1);
            LocalPoint p10 = previewLerp(previewSurfacePoint(previousSurface.a(), previousSurface.b(), t10), previewSurfacePoint(currentSurface.a(), currentSurface.b(), t10), uT1);
            LocalPoint center0 = previewLerp(previous.center(), current.center(), uT0);
            LocalPoint center1 = previewLerp(previous.center(), current.center(), uT1);
            Vec3Like normal = previewMarkerSurfaceNormal(p00, p01, p10, center0, center1);
            faces.add(new PreviewFace(
                    project(previewOffset(p00, normal, layer), centerX, centerY, scale),
                    project(previewOffset(p01, normal, layer), centerX, centerY, scale),
                    project(previewOffset(p11, normal, layer), centerX, centerY, scale),
                    project(previewOffset(p10, normal, layer), centerX, centerY, scale),
                    color,
                    sprite.getU(0.18F),
                    sprite.getU(0.82F),
                    sprite.getV(0.18F),
                    sprite.getV(0.82F),
                    sprite.atlasLocation(),
                    animationKind,
                    animationPhase));
            return;
        }
        addPreviewMarkerBand(faces, previous, current, uStart, uEnd, (minV + maxV) * 0.5D, maxV - minV, color, sprite, centerX, centerY, scale, layer);
    }

    private void addPreviewMarkerBand(List<PreviewFace> faces, PreviewModelSection previous, PreviewModelSection current, double uStart, double uEnd, double vCenter, double vWidth, int color, TextureAtlasSprite sprite, double centerX, double centerY, double scale, int layer) {
        double clippedUStart = Math.max(uStart, previous.distance());
        double clippedUEnd = Math.min(uEnd, current.distance());
        if (clippedUEnd <= clippedUStart + SURFACE_UV_EPSILON || vWidth <= SURFACE_UV_EPSILON) {
            return;
        }
        double perimeter = previous.perimeter();
        if (perimeter <= SURFACE_UV_EPSILON) {
            return;
        }
        double start = vCenter - vWidth * 0.5D;
        double end = vCenter + vWidth * 0.5D;
        while (start < 0.0D) {
            start += perimeter;
            end += perimeter;
        }
        if (end <= perimeter) {
            addPreviewMarkerRange(faces, previous, current, clippedUStart, clippedUEnd, start, end, color, sprite, centerX, centerY, scale, layer);
        } else {
            addPreviewMarkerRange(faces, previous, current, clippedUStart, clippedUEnd, start, perimeter, color, sprite, centerX, centerY, scale, layer);
            addPreviewMarkerRange(faces, previous, current, clippedUStart, clippedUEnd, 0.0D, end - perimeter, color, sprite, centerX, centerY, scale, layer);
        }
    }

    private void addPreviewMarkerRange(List<PreviewFace> faces, PreviewModelSection previous, PreviewModelSection current, double uStart, double uEnd, double vStart, double vEnd, int color, TextureAtlasSprite sprite, double centerX, double centerY, double scale, int layer) {
        double segmentLength = current.distance() - previous.distance();
        if (segmentLength <= SURFACE_UV_EPSILON) {
            return;
        }
        int limit = Math.min(previous.surfaces().size(), current.surfaces().size());
        for (int i = 0; i < limit; i++) {
            PreviewSurface previousSurface = previous.surfaces().get(i);
            PreviewSurface currentSurface = current.surfaces().get(i);
            double faceStart = previousSurface.vStart();
            double faceEnd = previousSurface.vEnd();
            double overlapStart = Math.max(vStart, faceStart);
            double overlapEnd = Math.min(vEnd, faceEnd);
            if (overlapEnd <= overlapStart + SURFACE_UV_EPSILON) {
                continue;
            }
            double t0 = (overlapStart - faceStart) / (faceEnd - faceStart);
            double t1 = (overlapEnd - faceStart) / (faceEnd - faceStart);
            double uT0 = (uStart - previous.distance()) / segmentLength;
            double uT1 = (uEnd - previous.distance()) / segmentLength;
            LocalPoint p00 = previewLerp(previewSurfacePoint(previousSurface.a(), previousSurface.b(), t0), previewSurfacePoint(currentSurface.a(), currentSurface.b(), t0), uT0);
            LocalPoint p01 = previewLerp(previewSurfacePoint(previousSurface.a(), previousSurface.b(), t1), previewSurfacePoint(currentSurface.a(), currentSurface.b(), t1), uT0);
            LocalPoint p11 = previewLerp(previewSurfacePoint(previousSurface.a(), previousSurface.b(), t1), previewSurfacePoint(currentSurface.a(), currentSurface.b(), t1), uT1);
            LocalPoint p10 = previewLerp(previewSurfacePoint(previousSurface.a(), previousSurface.b(), t0), previewSurfacePoint(currentSurface.a(), currentSurface.b(), t0), uT1);
            LocalPoint center0 = previewLerp(previous.center(), current.center(), uT0);
            LocalPoint center1 = previewLerp(previous.center(), current.center(), uT1);
            Vec3Like normal = previewMarkerSurfaceNormal(p00, p01, p10, center0, center1);
            faces.add(new PreviewFace(
                    project(previewOffset(p00, normal, layer), centerX, centerY, scale),
                    project(previewOffset(p01, normal, layer), centerX, centerY, scale),
                    project(previewOffset(p11, normal, layer), centerX, centerY, scale),
                    project(previewOffset(p10, normal, layer), centerX, centerY, scale),
                    color,
                    sprite.getU(0.18F),
                    sprite.getU(0.82F),
                    sprite.getV(0.18F),
                    sprite.getV(0.82F),
                    sprite.atlasLocation(),
                    PREVIEW_ANIMATION_NONE,
                    0.0D));
        }
    }

    private static Vec3Like previewMarkerSurfaceNormal(LocalPoint a, LocalPoint b, LocalPoint d, LocalPoint center0, LocalPoint center1) {
        Vec3Like normal = b.subtract(a).cross(d.subtract(a)).normalize();
        LocalPoint quadCenter = new LocalPoint(
                (a.x() + b.x() + d.x()) / 3.0D,
                (a.y() + b.y() + d.y()) / 3.0D,
                (a.z() + b.z() + d.z()) / 3.0D);
        LocalPoint center = previewLerp(center0, center1, 0.5D);
        Vec3Like outward = quadCenter.subtract(center);
        if (outward.lengthSqr() > 1.0E-8D && normal.dot(outward) < 0.0D) {
            normal = normal.scale(-1.0D);
        }
        return normal;
    }

    private static LocalPoint previewOffset(LocalPoint point, Vec3Like normal, int layer) {
        double offset = PREVIEW_MARKER_SURFACE_OFFSET + Math.max(0, layer) * PREVIEW_MARKER_LAYER_OFFSET;
        return new LocalPoint(
                point.x() + normal.x() * offset,
                point.y() + normal.y() * offset,
                point.z() + normal.z() * offset);
    }

    private static LocalPoint previewSurfacePoint(LocalPoint a, LocalPoint b, double t) {
        return previewLerp(a, b, t);
    }

    private void addPreviewSurfaceMappedFaces(List<PreviewFace> faces, LocalPoint p00, LocalPoint p01, LocalPoint p11, LocalPoint p10, double uStartWorld, double uEndWorld, double vStartWorld, double vEndWorld, int color, PipeCoatingRenderResolver.ResolvedPipeCoating coating, double centerX, double centerY, double scale) {
        double u0 = uStartWorld / PIPE_TEXTURE_TILE_U_BLOCKS;
        double u1 = uEndWorld / PIPE_TEXTURE_TILE_U_BLOCKS;
        double v0 = vStartWorld / PIPE_TEXTURE_TILE_V_BLOCKS;
        double v1 = vEndWorld / PIPE_TEXTURE_TILE_V_BLOCKS;
        if (u1 <= u0 + SURFACE_UV_EPSILON || v1 <= v0 + SURFACE_UV_EPSILON) {
            return;
        }

        double cursorU = u0;
        while (cursorU < u1 - SURFACE_UV_EPSILON) {
            double nextU = nextPreviewTileBoundary(cursorU, u1);
            double tU0 = (cursorU - u0) / (u1 - u0);
            double tU1 = (nextU - u0) / (u1 - u0);
            double cursorV = v0;
            while (cursorV < v1 - SURFACE_UV_EPSILON) {
                double nextV = nextPreviewTileBoundary(cursorV, v1);
                double tV0 = (cursorV - v0) / (v1 - v0);
                double tV1 = (nextV - v0) / (v1 - v0);
                double uBase = Math.floor(cursorU);
                double vBase = Math.floor(cursorV);
                faces.add(new PreviewFace(
                        project(previewSurfacePoint(p00, p01, p11, p10, tU0, tV0), centerX, centerY, scale),
                        project(previewSurfacePoint(p00, p01, p11, p10, tU1, tV0), centerX, centerY, scale),
                        project(previewSurfacePoint(p00, p01, p11, p10, tU1, tV1), centerX, centerY, scale),
                        project(previewSurfacePoint(p00, p01, p11, p10, tU0, tV1), centerX, centerY, scale),
                        color,
                        coating.u(previewTileFraction(cursorU, uBase)),
                        coating.u(previewTileFraction(nextU, uBase)),
                        coating.v(previewTileFraction(cursorV, vBase)),
                        coating.v(previewTileFraction(nextV, vBase)),
                        coating.textureId(),
                        PREVIEW_ANIMATION_NONE,
                        0.0D));
                cursorV = nextV;
            }
            cursorU = nextU;
        }
    }

    private static double nextPreviewTileBoundary(double cursor, double end) {
        double next = Math.min(end, Math.floor(cursor) + 1.0D);
        if (next <= cursor + SURFACE_UV_EPSILON) {
            next = Math.min(end, cursor + 1.0D);
        }
        return next;
    }

    private static float previewTileFraction(double value, double tileBase) {
        float fraction = (float) Math.max(0.0D, Math.min(1.0D, value - tileBase));
        return 0.0015F + fraction * 0.997F;
    }

    private static LocalPoint previewSurfacePoint(LocalPoint p00, LocalPoint p01, LocalPoint p11, LocalPoint p10, double u, double v) {
        return previewLerp(previewLerp(p00, p10, u), previewLerp(p01, p11, u), v);
    }

    private static LocalPoint previewLerp(LocalPoint a, LocalPoint b, double t) {
        return new LocalPoint(
                a.x() + (b.x() - a.x()) * t,
                a.y() + (b.y() - a.y()) * t,
                a.z() + (b.z() - a.z()) * t);
    }

    private void drawSlidePositionMarker(GuiGraphicsExtractor graphics, double centerX, double centerY, double scale) {
        ProjectedPoint start = project(new LocalPoint(-0.70D, 0.0D, 0.0D), centerX, centerY, scale);
        ProjectedPoint end = project(new LocalPoint(0.70D, 0.0D, 0.0D), centerX, centerY, scale);
        SmoothGuiPrimitives.line(graphics, start.xy(), end.xy(), 2.2D, 0xC837BFEA);
        SmoothGuiPrimitives.line(graphics, start.xy(), end.xy(), 0.9D, 0xEEF7FDFF);
        for (double x : List.of(-0.08D, 0.08D)) {
            ProjectedPoint a = project(new LocalPoint(x, 0.0D, -0.055D), centerX, centerY, scale);
            ProjectedPoint b = project(new LocalPoint(x, 0.0D, 0.055D), centerX, centerY, scale);
            SmoothGuiPrimitives.line(graphics, a.xy(), b.xy(), 1.5D, 0xCC46FF7A);
        }
    }

    private void drawPreviewSectionOutline(GuiGraphicsExtractor graphics, PreviewModelSection section, double centerX, double centerY, double scale, int color, double width) {
        for (PreviewSurface surface : section.surfaces()) {
            if (!surface.render()) {
                continue;
            }
            ProjectedPoint a = project(surface.a(), centerX, centerY, scale);
            ProjectedPoint b = project(surface.b(), centerX, centerY, scale);
            SmoothGuiPrimitives.line(graphics, a.xy(), b.xy(), width, color);
        }
    }

    private ProjectedPoint project(LocalPoint point, double centerX, double centerY, double scale) {
        double cosYaw = Math.cos(this.previewYaw);
        double sinYaw = Math.sin(this.previewYaw);
        double cosPitch = Math.cos(this.previewPitch);
        double sinPitch = Math.sin(this.previewPitch);
        double x1 = point.x() * cosYaw + point.z() * sinYaw;
        double z1 = -point.x() * sinYaw + point.z() * cosYaw;
        double y2 = point.y() * cosPitch - z1 * sinPitch;
        double z2 = point.y() * sinPitch + z1 * cosPitch;
        return new ProjectedPoint(new Vec2(centerX + x1 * scale, centerY - y2 * scale), z2);
    }

    private PreviewFit previewFit(SPSGui.Rect rect, List<PreviewModelSection> sections, List<PipeSurfaceModel.PatternedBox> boxes) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (PreviewModelSection section : sections) {
            for (PreviewSurface surface : section.surfaces()) {
                for (LocalPoint point : List.of(surface.a(), surface.b())) {
                    ProjectedPoint projected = project(point, 0.0D, 0.0D, 1.0D);
                    minX = Math.min(minX, projected.xy().x());
                    minY = Math.min(minY, projected.xy().y());
                    maxX = Math.max(maxX, projected.xy().x());
                    maxY = Math.max(maxY, projected.xy().y());
                }
            }
            for (PipeSurfaceModel.PatternedBox box : boxes) {
                for (LocalPoint point : List.of(
                        previewSectionLocalPoint(section, box.left(), box.bottom()),
                        previewSectionLocalPoint(section, box.left(), box.top()),
                        previewSectionLocalPoint(section, box.right(), box.bottom()),
                        previewSectionLocalPoint(section, box.right(), box.top()))) {
                    ProjectedPoint projected = project(point, 0.0D, 0.0D, 1.0D);
                    minX = Math.min(minX, projected.xy().x());
                    minY = Math.min(minY, projected.xy().y());
                    maxX = Math.max(maxX, projected.xy().x());
                    maxY = Math.max(maxY, projected.xy().y());
                }
            }
        }
        for (LocalPoint point : List.of(
                new LocalPoint(-0.72D, 0.0D, -0.070D),
                new LocalPoint(-0.72D, 0.0D, 0.070D),
                new LocalPoint(0.72D, 0.0D, -0.070D),
                new LocalPoint(0.72D, 0.0D, 0.070D))) {
            ProjectedPoint projected = project(point, 0.0D, 0.0D, 1.0D);
            minX = Math.min(minX, projected.xy().x());
            minY = Math.min(minY, projected.xy().y());
            maxX = Math.max(maxX, projected.xy().x());
            maxY = Math.max(maxY, projected.xy().y());
        }
        double projectedPadding = 0.085D;
        minX -= projectedPadding;
        minY -= projectedPadding;
        maxX += projectedPadding;
        maxY += projectedPadding;
        double availableX = rect.x() + 13.0D;
        double availableY = rect.y() + 29.0D;
        double availableWidth = Math.max(34.0D, rect.width() - 26.0D);
        double availableHeight = Math.max(34.0D, rect.height() - 51.0D);
        double boundsWidth = Math.max(0.1D, maxX - minX);
        double boundsHeight = Math.max(0.1D, maxY - minY);
        double fixedUnitScale = Math.max(48.0D, Math.min(availableWidth / 1.86D, availableHeight / 1.46D) * 0.92D);
        double fitScale = Math.min(availableWidth / boundsWidth, availableHeight / boundsHeight) * 0.94D;
        double scale = Math.min(fixedUnitScale, fitScale);
        double centerX = availableX + availableWidth * 0.5D - (minX + boundsWidth * 0.5D) * scale;
        double centerY = availableY + availableHeight * 0.5D - (minY + boundsHeight * 0.5D) * scale;
        double scaledMinX = centerX + minX * scale;
        double scaledMaxX = centerX + maxX * scale;
        if (scaledMinX < availableX) {
            centerX += availableX - scaledMinX;
        } else if (scaledMaxX > availableX + availableWidth) {
            centerX -= scaledMaxX - availableX - availableWidth;
        }
        double scaledMinY = centerY + minY * scale;
        double scaledMaxY = centerY + maxY * scale;
        if (scaledMinY < availableY) {
            centerY += availableY - scaledMinY;
        } else if (scaledMaxY > availableY + availableHeight) {
            centerY -= scaledMaxY - availableY - availableHeight;
        }
        return new PreviewFit(centerX, centerY, scale);
    }

    private Vec3Like rotatedNormal(Vec3Like normal) {
        double cosYaw = Math.cos(this.previewYaw);
        double sinYaw = Math.sin(this.previewYaw);
        double cosPitch = Math.cos(this.previewPitch);
        double sinPitch = Math.sin(this.previewPitch);
        double x1 = normal.x() * cosYaw + normal.z() * sinYaw;
        double z1 = -normal.x() * sinYaw + normal.z() * cosYaw;
        double y2 = normal.y() * cosPitch - z1 * sinPitch;
        double z2 = normal.y() * sinPitch + z1 * cosPitch;
        return new Vec3Like(x1, y2, z2).normalize();
    }

    private static Vec3Like faceNormal(LocalPoint a, LocalPoint b, LocalPoint c) {
        return b.subtract(a).cross(c.subtract(a)).normalize();
    }

    private static int shadeColor(int color, Vec3Like normal, double min, double max) {
        Vec3Like light = new Vec3Like(-0.38D, 0.84D, 0.44D).normalize();
        double dot = Math.max(0.0D, normal.normalize().dot(light));
        double factor = min + (max - min) * dot;
        int a = color >>> 24 & 0xFF;
        int r = (int) Math.max(0, Math.min(255, ((color >>> 16) & 0xFF) * factor));
        int g = (int) Math.max(0, Math.min(255, ((color >>> 8) & 0xFF) * factor));
        int b = (int) Math.max(0, Math.min(255, (color & 0xFF) * factor));
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int previewAnimatedMarkerColor(int color, int animationKind, double phase, double time) {
        return switch (animationKind) {
            case PREVIEW_ANIMATION_ACCELERATION -> previewMultiplyColor(color, 0.72D + 0.48D * previewImpulseWave(time * 1.35D - phase));
            case PREVIEW_ANIMATION_HIGHWAY -> previewMultiplyColor(color, 0.78D + 0.34D * previewSoftPulse(time * 0.66D - phase));
            case PREVIEW_ANIMATION_DIRECTION -> previewMultiplyColor(color, 0.82D + 0.26D * previewDirectionPulse(time * 0.48D - phase));
            default -> color;
        };
    }

    private static double previewImpulseWave(double value) {
        double phase = value - Math.floor(value);
        return Math.pow(Math.max(0.0D, 1.0D - phase), 2.7D);
    }

    private static double previewSoftPulse(double value) {
        double phase = value - Math.floor(value);
        return 0.5D + 0.5D * Math.cos((phase - 0.5D) * Math.PI * 2.0D);
    }

    private static double previewDirectionPulse(double value) {
        double phase = value - Math.floor(value);
        if (phase < 0.18D) {
            return 1.0D - phase / 0.18D * 0.18D;
        }
        return Math.max(0.0D, 1.0D - (phase - 0.18D) / 0.82D) * 0.24D;
    }

    private static int previewMultiplyColor(int color, double factor) {
        int a = color >>> 24 & 0xFF;
        int r = (int) Math.max(0, Math.min(255, Math.round((color >>> 16 & 0xFF) * factor)));
        int g = (int) Math.max(0, Math.min(255, Math.round((color >>> 8 & 0xFF) * factor)));
        int b = (int) Math.max(0, Math.min(255, Math.round((color & 0xFF) * factor)));
        return a << 24 | r << 16 | g << 8 | b;
    }

    private record LocalPoint(double x, double y, double z) {
        Vec3Like subtract(LocalPoint other) {
            return new Vec3Like(this.x - other.x, this.y - other.y, this.z - other.z);
        }
    }

    private record PreviewModelSection(List<PreviewSurface> surfaces, double perimeter, double distance, LocalPoint center) {}

    private record PreviewSurface(String slotId, LocalPoint a, LocalPoint b, double vStart, double vEnd, boolean render) {}

    private record PreviewFit(double centerX, double centerY, double scale) {}

    private record Vec3Like(double x, double y, double z) {
        Vec3Like cross(Vec3Like other) {
            return new Vec3Like(
                    this.y * other.z - this.z * other.y,
                    this.z * other.x - this.x * other.z,
                    this.x * other.y - this.y * other.x);
        }

        double dot(Vec3Like other) {
            return this.x * other.x + this.y * other.y + this.z * other.z;
        }

        Vec3Like scale(double factor) {
            return new Vec3Like(this.x * factor, this.y * factor, this.z * factor);
        }

        double lengthSqr() {
            return this.x * this.x + this.y * this.y + this.z * this.z;
        }

        Vec3Like normalize() {
            double length = Math.sqrt(lengthSqr());
            if (length <= 1.0E-8D) {
                return new Vec3Like(0.0D, 1.0D, 0.0D);
            }
            return new Vec3Like(this.x / length, this.y / length, this.z / length);
        }
    }

    private record ProjectedPoint(Vec2 xy, double depth) {}

    private record PreviewFace(ProjectedPoint a, ProjectedPoint b, ProjectedPoint c, ProjectedPoint d, int color, float u0, float u1, float v0, float v1, Identifier textureId, int animationKind, double animationPhase) {
        double depth() {
            return (this.a.depth() + this.b.depth() + this.c.depth() + this.d.depth()) * 0.25D;
        }
    }

    private static void drawStyleGlyph(GuiGraphicsExtractor graphics, SPSGui.Rect rect, PipeStyleDefinition style, int color) {
        int cx = rect.x() + rect.width() / 2;
        int cy = rect.y() + rect.height() / 2;
        int accent = SPSGui.withAlpha(color, 0xAA);
        switch (style.shape()) {
            case ROUND -> {
                double radius = Math.min(rect.width(), rect.height()) * 0.36D;
                SmoothGuiPrimitives.circle(graphics, new Vec2(cx, cy), radius + 1.0D, color);
                SmoothGuiPrimitives.circle(graphics, new Vec2(cx, cy), Math.max(1.0D, radius - 1.6D), accent);
            }
            case BOX -> {
                graphics.fill(rect.x() + 3, rect.y() + 3, rect.right() - 3, rect.bottom() - 3, accent);
                graphics.outline(rect.x() + 3, rect.y() + 3, rect.width() - 6, rect.height() - 6, color);
            }
            case TRIANGLE -> {
                SmoothGuiPrimitives.quad(graphics, new Vec2(cx, rect.y() + 4), new Vec2(rect.right() - 4, rect.bottom() - 4), new Vec2(rect.x() + 4, rect.bottom() - 4), new Vec2(cx, rect.y() + 4), accent);
                SmoothGuiPrimitives.line(graphics, new Vec2(cx, rect.y() + 4), new Vec2(rect.right() - 4, rect.bottom() - 4), 1.2D, color);
                SmoothGuiPrimitives.line(graphics, new Vec2(rect.right() - 4, rect.bottom() - 4), new Vec2(rect.x() + 4, rect.bottom() - 4), 1.2D, color);
                SmoothGuiPrimitives.line(graphics, new Vec2(rect.x() + 4, rect.bottom() - 4), new Vec2(cx, rect.y() + 4), 1.2D, color);
            }
            case RAIL -> {
                graphics.fill(rect.x() + 4, rect.y() + 5, rect.x() + 7, rect.bottom() - 5, color);
                graphics.fill(rect.right() - 7, rect.y() + 5, rect.right() - 4, rect.bottom() - 5, color);
                graphics.fill(rect.x() + 5, cy - 1, rect.right() - 5, cy + 2, accent);
            }
            case SLIDE -> {
                SmoothGuiPrimitives.line(graphics, new Vec2(rect.x() + 4, rect.y() + 6), new Vec2(rect.x() + 8, rect.bottom() - 4), 2.0D, color);
                SmoothGuiPrimitives.line(graphics, new Vec2(rect.x() + 8, rect.bottom() - 4), new Vec2(rect.right() - 8, rect.bottom() - 4), 2.0D, accent);
                SmoothGuiPrimitives.line(graphics, new Vec2(rect.right() - 8, rect.bottom() - 4), new Vec2(rect.right() - 4, rect.y() + 6), 2.0D, color);
            }
            case FACETED -> {
                SmoothGuiPrimitives.diamond(graphics, new Vec2(cx, cy), Math.min(rect.width(), rect.height()) * 0.36D, accent);
                SmoothGuiPrimitives.diamond(graphics, new Vec2(cx, cy), Math.min(rect.width(), rect.height()) * 0.34D, color);
            }
            case MONORAIL -> {
                graphics.fill(cx - 3, rect.y() + 4, cx + 3, rect.y() + 8, color);
                SmoothGuiPrimitives.line(graphics, new Vec2(cx, rect.y() + 7), new Vec2(rect.x() + 6, rect.bottom() - 4), 2.0D, accent);
                SmoothGuiPrimitives.line(graphics, new Vec2(cx, rect.y() + 7), new Vec2(rect.right() - 6, rect.bottom() - 4), 2.0D, accent);
            }
            case COVERED -> {
                int baseY = rect.bottom() - 5;
                graphics.fill(rect.x() + 4, baseY - 3, rect.right() - 4, baseY, color);
                List<Vec2> arch = new ArrayList<>();
                int segments = 8;
                double rx = rect.width() * 0.36D;
                double ry = rect.height() * 0.38D;
                for (int i = 0; i <= segments; i++) {
                    double angle = Math.PI - Math.PI * i / segments;
                    arch.add(new Vec2(cx + Math.cos(angle) * rx, baseY - 2 - Math.sin(angle) * ry));
                }
                SmoothGuiPrimitives.polyline(graphics, arch, 2.0D, accent);
                SmoothGuiPrimitives.line(graphics, new Vec2(rect.x() + 5, baseY - 2), new Vec2(rect.right() - 5, baseY - 2), 1.0D, PipeAppearanceTerminalGui.TEXT_MUTED);
            }
        }
    }
}
