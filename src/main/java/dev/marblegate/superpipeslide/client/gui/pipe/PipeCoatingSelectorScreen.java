package dev.marblegate.superpipeslide.client.gui.pipe;

import dev.marblegate.superpipeslide.client.core.pipe.PipeCoatingRenderResolver;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.render.SmoothGuiPrimitives;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.client.gui.base.SPSScreen;
import dev.marblegate.superpipeslide.common.core.appearance.coating.PipeCoatingDyeMode;
import dev.marblegate.superpipeslide.common.core.appearance.coating.PipeCoatingSelection;
import dev.marblegate.superpipeslide.common.core.appearance.coating.PipeTexturePickType;
import dev.marblegate.superpipeslide.common.core.appearance.coating.RecommendedPipeCoatings;
import dev.marblegate.superpipeslide.common.core.appearance.storage.PipeAppearanceDefinitions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class PipeCoatingSelectorScreen extends SPSScreen {
    private final PipeAppearanceEditorScreen parent;
    private final String slotId;
    private final PipeCoatingSelection initialSelection;
    private PipeCoatingSelection currentSelection;
    private SelectorTab selectedTab = SelectorTab.RECOMMENDED;
    private String selectedRecommendedCategoryId = RecommendedPipeCoatings.categories().getFirst().id();
    private SPSGui.Rect colorPickerBounds = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect dyeColorSwatchBounds = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect materialGridScrollBounds = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect optionsScrollBounds = new SPSGui.Rect(0, 0, 0, 0);
    private boolean colorPickerOpen;
    private int activeColorSlot;
    private double materialGridScroll;
    private double materialGridMaxScroll;
    private double optionsScroll;
    private double optionsMaxScroll;
    private final Set<String> confirmedRiskKeys = new HashSet<>();
    private PipeCoatingSelection pendingRiskSelection;
    private CoatingTextureRisk pendingRisk;

    public PipeCoatingSelectorScreen(PipeAppearanceEditorScreen parent, String slotId, PipeCoatingSelection currentSelection) {
        super(Component.translatable("screen.superpipeslide.pipe_appearance.coating_selector"));
        this.parent = parent;
        this.slotId = slotId;
        this.initialSelection = PipeAppearanceDefinitions.normalizeSelection(currentSelection);
        this.currentSelection = this.initialSelection;
        this.confirmedRiskKeys.add(riskKey(this.currentSelection));
    }

    @Override
    protected SPSGui.Rect createPanelRect() {
        int width = Math.min(724, this.width - 18);
        int height = Math.min(392, this.height - 14);
        return new SPSGui.Rect((this.width - width) / 2, (this.height - height) / 2, width, height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.beginFrame();
        PipeAppearanceTerminalGui.frame(graphics, this.panel);
        PipeAppearanceTerminalGui.titleBar(graphics, this.font, this.panel, Component.translatable("screen.superpipeslide.pipe_appearance.coating_selector"), true, mouseX, mouseY);
        this.addClick(PipeAppearanceTerminalGui.backBounds(this.panel), this::onBack, Component.translatable("screen.superpipeslide.action.back"));

        SPSGui.Rect content = new SPSGui.Rect(this.panel.x() + 7, this.panel.y() + 25, this.panel.width() - 14, this.panel.height() - 31);
        int y = drawSelectionHeader(graphics, content, mouseX, mouseY);

        SPSGui.Rect actions = new SPSGui.Rect(content.x(), content.bottom() - 22, content.width(), 22);
        SPSGui.Rect body = new SPSGui.Rect(content.x(), y + 6, content.width(), actions.y() - y - 12);
        int sourceW = 48;
        int sideW = 206;
        SPSGui.Rect sourcePane = new SPSGui.Rect(body.x(), body.y(), sourceW, body.height());
        SPSGui.Rect optionPane = new SPSGui.Rect(body.right() - sideW, body.y(), sideW, body.height());
        SPSGui.Rect listPane = new SPSGui.Rect(sourcePane.right() + 6, body.y(), optionPane.x() - sourcePane.right() - 12, body.height());
        drawSourceTabs(graphics, sourcePane, mouseX, mouseY);
        if (this.selectedTab == SelectorTab.RECOMMENDED) {
            drawRecommendedPage(graphics, listPane, mouseX, mouseY);
        } else {
            drawInventoryPage(graphics, listPane, mouseX, mouseY);
        }
        drawOptionsPane(graphics, optionPane, mouseX, mouseY);

        drawBottomActions(graphics, actions, mouseX, mouseY);
        if (this.colorPickerOpen) {
            drawColorPicker(graphics, content, mouseX, mouseY);
        } else {
            this.colorPickerBounds = new SPSGui.Rect(0, 0, 0, 0);
        }
        if (this.pendingRiskSelection != null && this.pendingRisk != null) {
            drawRiskConfirmation(graphics, mouseX, mouseY);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.renderTooltips(graphics, mouseX, mouseY);
    }

    private int drawSelectionHeader(GuiGraphicsExtractor graphics, SPSGui.Rect content, int mouseX, int mouseY) {
        SPSGui.Rect header = new SPSGui.Rect(content.x(), content.y(), content.width(), 32);
        PipeAppearanceTerminalGui.raisedPanel(graphics, header);
        drawChoiceSwatch(graphics, new SPSGui.Rect(header.x() + 7, header.y() + 5, 24, 22), this.currentSelection, true);
        Component slotName = Component.translatable("pipe_appearance.superpipeslide.slot." + this.slotId);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.pipe_appearance.selector_slot", slotName), header.x() + 38, header.y() + 6, PipeAppearanceTerminalGui.TEXT);
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, selectionName(this.currentSelection), Math.max(90, header.width() / 2)), header.x() + 38, header.y() + 19, PipeAppearanceTerminalGui.TEXT_MUTED, 0.58F);
        String modeName = Component.translatable("screen.superpipeslide.pipe_appearance.dye_mode." + this.currentSelection.dyeMode().id()).getString();
        SPSGui.Rect badge = new SPSGui.Rect(header.right() - 90, header.y() + 7, 82, 18);
        PipeAppearanceTerminalGui.badge(graphics, this.font, badge, Component.literal(modeName), PipeAppearanceTerminalGui.ACCENT_SELECTED);
        return header.bottom();
    }

    private void drawSourceTabs(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        PipeAppearanceTerminalGui.panel(graphics, rect);
        int y = rect.y() + 8;
        for (SelectorTab tab : SelectorTab.values()) {
            SPSGui.Rect tabRect = new SPSGui.Rect(rect.x() + 6, y, rect.width() - 12, 38);
            boolean selected = tab == this.selectedTab;
            drawSampleButton(graphics, tabRect, tabRect.contains(mouseX, mouseY), selected, PipeAppearanceTerminalGui.SURFACE_RAISED);
            SPSGui.icon(graphics, new SPSGui.Rect(tabRect.x() + 9, tabRect.y() + 4, 18, 18), tab == SelectorTab.RECOMMENDED ? SPSGui.Icon.PIPE_RECOMMENDED : SPSGui.Icon.PIPE_BACKPACK, selected ? PipeAppearanceTerminalGui.ACCENT_SELECTED : PipeAppearanceTerminalGui.TEXT_MUTED);
            drawCenteredSmallText(graphics, SPSGui.ellipsize(this.font, Component.translatable(tab.nameKey()).getString(), Math.round((tabRect.width() - 6) / 0.50F)), tabRect, tabRect.y() + 26, selected ? PipeAppearanceTerminalGui.TEXT : PipeAppearanceTerminalGui.TEXT_SECONDARY, 0.50F);
            this.addClick(tabRect, () -> {
                this.selectedTab = tab;
                this.colorPickerOpen = false;
                this.materialGridScroll = 0.0D;
            }, Component.translatable(tab.tooltipKey()));
            y += 44;
        }
    }

    private void drawRecommendedPage(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        PipeAppearanceTerminalGui.panel(graphics, rect);
        SPSGui.Rect categories = new SPSGui.Rect(rect.x() + 7, rect.y() + 7, rect.width() - 14, 38);
        SPSGui.Rect grid = new SPSGui.Rect(rect.x() + 7, categories.bottom() + 6, rect.width() - 14, rect.bottom() - categories.bottom() - 13);
        drawRecommendedCategoryRail(graphics, categories, mouseX, mouseY);
        RecommendedPipeCoatings.Category category = RecommendedPipeCoatings.categories().stream()
                .filter(candidate -> candidate.id().equals(this.selectedRecommendedCategoryId))
                .findFirst()
                .orElse(RecommendedPipeCoatings.categories().getFirst());
        drawRecommendedGrid(graphics, grid, category.entries(), mouseX, mouseY);
    }

    private void drawRecommendedCategoryRail(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        int x = rect.x();
        int y = rect.y();
        for (RecommendedPipeCoatings.Category category : RecommendedPipeCoatings.categories()) {
            int width = Math.min(58, Math.max(42, this.font.width(Component.translatable(category.nameKey())) + 12));
            if (x + width > rect.right()) {
                x = rect.x();
                y += 20;
            }
            if (y + 18 > rect.bottom()) {
                break;
            }
            SPSGui.Rect row = new SPSGui.Rect(x, y, width, 18);
            boolean selected = category.id().equals(this.selectedRecommendedCategoryId);
            drawSampleButton(graphics, row, row.contains(mouseX, mouseY), selected, PipeAppearanceTerminalGui.SURFACE_RAISED);
            SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, Component.translatable(category.nameKey()).getString(), Math.round((row.width() - 10) / 0.66F)), row.x() + 6, row.y() + 5, selected ? PipeAppearanceTerminalGui.TEXT : PipeAppearanceTerminalGui.TEXT_SECONDARY, 0.66F);
            this.addClick(row, () -> {
                this.selectedRecommendedCategoryId = category.id();
                this.colorPickerOpen = false;
                this.materialGridScroll = 0.0D;
            }, Component.translatable(category.nameKey()));
            x += width + 5;
        }
    }

    private void drawRecommendedGrid(GuiGraphicsExtractor graphics, SPSGui.Rect rect, List<RecommendedPipeCoatings.Entry> entries, int mouseX, int mouseY) {
        int innerX = rect.x();
        int innerY = rect.y();
        int innerW = rect.width();
        int targetCellW = 68;
        int cellH = 72;
        int gap = 6;
        int columns = Math.max(1, (innerW + gap) / (targetCellW + gap));
        int cellW = Math.max(58, (innerW - (columns - 1) * gap) / columns);
        int rows = (entries.size() + columns - 1) / columns;
        int contentHeight = rows <= 0 ? 0 : rows * cellH + (rows - 1) * gap;
        this.materialGridScrollBounds = rect;
        this.materialGridMaxScroll = Math.max(0.0D, contentHeight - rect.height());
        this.materialGridScroll = clamp(this.materialGridScroll, 0.0D, this.materialGridMaxScroll);
        int scrollOffset = (int) Math.round(this.materialGridScroll);
        graphics.enableScissor(innerX, innerY, rect.right(), rect.bottom());
        for (int i = 0; i < entries.size(); i++) {
            Identifier blockId = entries.get(i).blockId();
            int x = innerX + i % columns * (cellW + gap);
            int y = innerY + i / columns * (cellH + gap) - scrollOffset;
            SPSGui.Rect card = new SPSGui.Rect(x, y, cellW, cellH);
            if (!intersects(card, rect)) {
                continue;
            }
            drawBlockChoice(graphics, card, blockId, mouseX, mouseY);
        }
        graphics.disableScissor();
        PipeAppearanceTerminalGui.scrollEdges(graphics, rect, this.materialGridScroll > 0.5D, this.materialGridScroll < this.materialGridMaxScroll - 0.5D);
    }

    private void drawInventoryPage(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        PipeAppearanceTerminalGui.panel(graphics, rect);
        PipeAppearanceTerminalGui.sectionLabel(graphics, this.font, Component.translatable("screen.superpipeslide.pipe_appearance.inventory_blocks"), rect.x() + 7, rect.y() + 7);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            this.materialGridScrollBounds = new SPSGui.Rect(0, 0, 0, 0);
            this.materialGridMaxScroll = 0.0D;
            SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.pipe_appearance.inventory_unavailable"), rect.x() + 7, rect.y() + 26, PipeAppearanceTerminalGui.TEXT_MUTED);
            return;
        }

        Map<Identifier, ItemStack> blocks = inventoryBlocks(minecraft.player.getInventory());
        if (blocks.isEmpty()) {
            this.materialGridScrollBounds = new SPSGui.Rect(0, 0, 0, 0);
            this.materialGridMaxScroll = 0.0D;
            SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.pipe_appearance.inventory_empty"), rect.x() + 7, rect.y() + 26, PipeAppearanceTerminalGui.TEXT_MUTED);
            return;
        }

        int targetCellW = 62;
        int cellH = 68;
        int gap = 6;
        int columns = Math.max(1, (rect.width() - 14 + gap) / (targetCellW + gap));
        int cellW = Math.max(52, (rect.width() - 14 - (columns - 1) * gap) / columns);
        int startY = rect.y() + 27;
        int rows = (blocks.size() + columns - 1) / columns;
        int contentHeight = rows <= 0 ? 0 : rows * cellH + (rows - 1) * gap;
        this.materialGridScrollBounds = new SPSGui.Rect(rect.x() + 7, startY, rect.width() - 14, rect.bottom() - 7 - startY);
        this.materialGridMaxScroll = Math.max(0.0D, contentHeight - this.materialGridScrollBounds.height());
        this.materialGridScroll = clamp(this.materialGridScroll, 0.0D, this.materialGridMaxScroll);
        int scrollOffset = (int) Math.round(this.materialGridScroll);
        int slot = 0;
        graphics.enableScissor(this.materialGridScrollBounds.x(), this.materialGridScrollBounds.y(), this.materialGridScrollBounds.right(), this.materialGridScrollBounds.bottom());
        for (Map.Entry<Identifier, ItemStack> entry : blocks.entrySet()) {
            int x = rect.x() + 7 + slot % columns * (cellW + gap);
            int y = startY + slot / columns * (cellH + gap) - scrollOffset;
            Identifier blockId = entry.getKey();
            ItemStack stack = entry.getValue();
            SPSGui.Rect itemRect = new SPSGui.Rect(x, y, cellW, cellH);
            if (!intersects(itemRect, this.materialGridScrollBounds)) {
                slot++;
                continue;
            }
            boolean selected = blockId.equals(this.currentSelection.blockId());
            drawSampleButton(graphics, itemRect, itemRect.contains(mouseX, mouseY), selected, PipeAppearanceTerminalGui.SURFACE_RAISED);
            int swatch = Math.min(itemRect.width() - 12, itemRect.height() - 23);
            drawChoiceSwatch(graphics, new SPSGui.Rect(itemRect.x() + (itemRect.width() - swatch) / 2, itemRect.y() + 5, swatch, swatch), this.currentSelection.withBlock(blockId), true);
            graphics.item(stack, itemRect.x() + 2, itemRect.y() + 2);
            drawCenteredScrollingSmallText(graphics, stack.getHoverName().getString(), itemRect, itemRect.bottom() - 13, selected ? PipeAppearanceTerminalGui.TEXT : PipeAppearanceTerminalGui.TEXT_SECONDARY, 0.50F, blockId.hashCode());
            this.addClick(itemRect, () -> chooseSelection(this.currentSelection.withBlock(blockId)), blockChoiceTooltip(this.currentSelection.withBlock(blockId), stack.getHoverName()));
            slot++;
        }
        graphics.disableScissor();
        PipeAppearanceTerminalGui.scrollEdges(graphics, this.materialGridScrollBounds, this.materialGridScroll > 0.5D, this.materialGridScroll < this.materialGridMaxScroll - 0.5D);
    }

    private void drawBlockChoice(GuiGraphicsExtractor graphics, SPSGui.Rect card, Identifier blockId, int mouseX, int mouseY) {
        PipeCoatingSelection selection = this.currentSelection.withBlock(blockId);
        Block block = PipeCoatingRenderResolver.blockFor(blockId);
        boolean selected = blockId.equals(this.currentSelection.blockId());
        drawSampleButton(graphics, card, card.contains(mouseX, mouseY), selected, PipeAppearanceTerminalGui.SURFACE_RAISED);
        if (selected) {
            graphics.fill(card.right() - 10, card.y() + 4, card.right() - 4, card.y() + 10, PipeAppearanceTerminalGui.ACCENT_SELECTED);
        }
        int swatch = Math.min(card.width() - 12, card.height() - 24);
        drawChoiceSwatch(graphics, new SPSGui.Rect(card.x() + (card.width() - swatch) / 2, card.y() + 5, swatch, swatch), selection, true);
        drawCenteredScrollingSmallText(graphics, block.getName().getString(), card, card.bottom() - 14, selected ? PipeAppearanceTerminalGui.TEXT : PipeAppearanceTerminalGui.TEXT_SECONDARY, 0.50F, blockId.hashCode());
        this.addClick(card, () -> chooseSelection(selection), blockChoiceTooltip(selection, block.getName()));
    }

    private void drawOptionsPane(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        PipeAppearanceTerminalGui.panel(graphics, rect);
        PipeAppearanceTerminalGui.sectionLabel(graphics, this.font, Component.translatable("screen.superpipeslide.pipe_appearance.material_inspector"), rect.x() + 7, rect.y() + 6);
        drawChoiceSwatch(graphics, new SPSGui.Rect(rect.x() + 8, rect.y() + 21, 48, 48), this.currentSelection, true);
        SPSGui.text(graphics, this.font, SPSGui.ellipsize(this.font, PipeCoatingRenderResolver.blockDisplayName(this.currentSelection), rect.width() - 70), rect.x() + 64, rect.y() + 23, PipeAppearanceTerminalGui.TEXT);
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, this.currentSelection.blockId().toString(), rect.width() - 70), rect.x() + 64, rect.y() + 36, PipeAppearanceTerminalGui.TEXT_MUTED, 0.52F);
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, selectionName(this.currentSelection), rect.width() - 70), rect.x() + 64, rect.y() + 49, PipeAppearanceTerminalGui.TEXT_MUTED, 0.52F);
        CoatingTextureRisk risk = riskFor(this.currentSelection);
        int optionsTop = rect.y() + 74;
        if (risk.warns()) {
            SPSGui.Rect warning = new SPSGui.Rect(rect.x() + 64, rect.y() + 61, rect.right() - rect.x() - 72, 14);
            PipeAppearanceTerminalGui.badge(graphics, this.font, warning, Component.translatable(risk.labelKey()), PipeAppearanceTerminalGui.WARNING);
            optionsTop = rect.y() + 82;
        }

        this.optionsScrollBounds = new SPSGui.Rect(rect.x() + 6, optionsTop, rect.width() - 12, rect.bottom() - optionsTop - 6);
        this.optionsScroll = clamp(this.optionsScroll, 0.0D, this.optionsMaxScroll);
        int scrollOffset = (int) Math.round(this.optionsScroll);
        int contentY = this.optionsScrollBounds.y() - scrollOffset;
        graphics.enableScissor(this.optionsScrollBounds.x(), this.optionsScrollBounds.y(), this.optionsScrollBounds.right(), this.optionsScrollBounds.bottom());
        int y = contentY;
        y = drawTexturePickOptions(graphics, rect, y, mouseX, mouseY, this.optionsScrollBounds);
        y = drawDyeModeOptions(graphics, rect, y + 7, mouseX, mouseY, this.optionsScrollBounds);
        y = drawColorOptions(graphics, rect, y + 7, mouseX, mouseY, this.optionsScrollBounds);
        graphics.disableScissor();
        int contentHeight = Math.max(0, y - contentY + 4);
        this.optionsMaxScroll = Math.max(0.0D, contentHeight - this.optionsScrollBounds.height());
        this.optionsScroll = clamp(this.optionsScroll, 0.0D, this.optionsMaxScroll);
        PipeAppearanceTerminalGui.scrollEdges(graphics, this.optionsScrollBounds, this.optionsScroll > 0.5D, this.optionsScroll < this.optionsMaxScroll - 0.5D);
    }

    private int drawTexturePickOptions(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int y, int mouseX, int mouseY, SPSGui.Rect viewport) {
        PipeAppearanceTerminalGui.sectionLabel(graphics, this.font, Component.translatable("screen.superpipeslide.pipe_appearance.texture_pick"), rect.x() + 8, y);
        int x = rect.x() + 8;
        int rowY = y + 13;
        int cell = 26;
        int gap = 5;
        List<PipeCoatingRenderResolver.TextureCandidate> candidates = PipeCoatingRenderResolver.textureCandidates(this.currentSelection.blockId());
        int maxRight = rect.right() - 8;
        for (PipeCoatingRenderResolver.TextureCandidate candidate : candidates) {
            if (x + cell > maxRight) {
                x = rect.x() + 8;
                rowY += cell + 5;
            }
            SPSGui.Rect chip = new SPSGui.Rect(x, rowY, cell, cell);
            boolean selected = candidate.pick().equals(this.currentSelection.texturePick());
            drawSampleButton(graphics, chip, chip.contains(mouseX, mouseY), selected, PipeAppearanceTerminalGui.SURFACE_RAISED);
            drawSpriteSwatch(graphics, new SPSGui.Rect(chip.x() + 4, chip.y() + 4, 18, 18), candidate.sprite(), true, 0xFFFFFFFF);
            if (intersects(chip, viewport)) {
                this.addClick(chip, () -> chooseSelection(this.currentSelection.withTexturePick(candidate.pick())), Component.translatable("screen.superpipeslide.pipe_appearance.use_texture_pick", candidate.label()));
            }
            x += cell + gap;
        }
        return rowY + cell;
    }

    private int drawDyeModeOptions(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int y, int mouseX, int mouseY, SPSGui.Rect viewport) {
        PipeAppearanceTerminalGui.sectionLabel(graphics, this.font, Component.translatable("screen.superpipeslide.pipe_appearance.dye_mode"), rect.x() + 8, y);
        PipeCoatingDyeMode[] modes = PipeCoatingDyeMode.values();
        int gap = 4;
        int cols = 3;
        int chipW = (rect.width() - 16 - gap * (cols - 1)) / cols;
        int rowY = y + 13;
        for (int i = 0; i < modes.length; i++) {
            PipeCoatingDyeMode mode = modes[i];
            String label = Component.translatable("screen.superpipeslide.pipe_appearance.dye_mode." + mode.id()).getString();
            int col = i % cols;
            int row = i / cols;
            SPSGui.Rect chip = new SPSGui.Rect(rect.x() + 8 + col * (chipW + gap), rowY + row * 19, chipW, 16);
            boolean selected = mode == this.currentSelection.dyeMode();
            drawSampleButton(graphics, chip, chip.contains(mouseX, mouseY), selected, PipeAppearanceTerminalGui.SURFACE_RAISED);
            SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, label, Math.round((chip.width() - 8) / 0.60F)), chip.x() + 5, chip.y() + 5, selected ? PipeAppearanceTerminalGui.TEXT : PipeAppearanceTerminalGui.TEXT_SECONDARY, 0.60F);
            if (intersects(chip, viewport)) {
                this.addClick(chip, () -> applySelectionDirect(this.currentSelection.withDyeMode(mode)), Component.translatable("screen.superpipeslide.pipe_appearance.dye_mode." + mode.id() + ".tooltip"));
            }
        }
        return rowY + ((modes.length + cols - 1) / cols) * 19 - 3;
    }

    private int drawColorOptions(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int y, int mouseX, int mouseY, SPSGui.Rect viewport) {
        PipeAppearanceTerminalGui.sectionLabel(graphics, this.font, Component.translatable("screen.superpipeslide.pipe_appearance.dye_color"), rect.x() + 8, y);
        int slotCount = currentColorSlotCount();
        if (slotCount <= 0) {
            SPSGui.Rect swatch = new SPSGui.Rect(rect.x() + 8, y + 15, 26, 18);
            this.dyeColorSwatchBounds = swatch;
            drawTerminalEmptySwatch(graphics, swatch, false);
            SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.pipe_appearance.dye_color_disabled").getString(), swatch.right() + 7, swatch.y() + 6, PipeAppearanceTerminalGui.TEXT_MUTED, 0.64F);
            return swatch.bottom() + 2;
        }
        if (this.currentSelection.dyeMode().variableColorSlots()) {
            return drawThemePaletteOptions(graphics, rect, y + 15, mouseX, mouseY, viewport);
        }

        int slotY = y + 15;
        for (int slot = 0; slot < slotCount; slot++) {
            int color = colorForSlot(slot);
            SPSGui.Rect row = new SPSGui.Rect(rect.x() + 8, slotY + slot * 22, rect.width() - 16, 19);
            boolean active = slot == this.activeColorSlot;
            drawSampleButton(graphics, row, row.contains(mouseX, mouseY), active && this.colorPickerOpen, PipeAppearanceTerminalGui.SURFACE_RAISED);
            SPSGui.Rect swatch = new SPSGui.Rect(row.x() + 4, row.y() + 3, 22, 13);
            if (active) {
                this.dyeColorSwatchBounds = swatch;
            }
            drawTerminalColorSwatch(graphics, swatch, color, row.contains(mouseX, mouseY) || active && this.colorPickerOpen);
            Component label = colorSlotLabel(this.currentSelection.dyeMode(), slot);
            SPSGui.smallText(graphics, this.font, label.getString(), swatch.right() + 6, row.y() + 4, active ? PipeAppearanceTerminalGui.ACCENT_SELECTED : PipeAppearanceTerminalGui.TEXT_SECONDARY, 0.60F);
            SPSGui.smallText(graphics, this.font, "#" + String.format("%06X", color & 0x00FFFFFF), row.right() - 56, row.y() + 5, PipeAppearanceTerminalGui.TEXT_MUTED, 0.52F);
            int capturedSlot = slot;
            if (intersects(row, viewport)) {
                this.addClick(row, () -> {
                    this.activeColorSlot = capturedSlot;
                    this.colorPickerOpen = true;
                }, Component.translatable("screen.superpipeslide.pipe_appearance.tint_custom_slot", label));
            }
        }
        return slotY + slotCount * 22;
    }

    private int drawThemePaletteOptions(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int y, int mouseX, int mouseY, SPSGui.Rect viewport) {
        List<Integer> colors = this.currentSelection.dyeColors();
        int chipW = 42;
        int chipH = 28;
        int gap = 5;
        int x = rect.x() + 8;
        int rowY = y;
        int maxRight = rect.right() - 8;
        for (int slot = 0; slot < colors.size(); slot++) {
            if (x + chipW > maxRight) {
                x = rect.x() + 8;
                rowY += chipH + gap;
            }
            int color = colors.get(slot);
            SPSGui.Rect chip = new SPSGui.Rect(x, rowY, chipW, chipH);
            boolean active = slot == this.activeColorSlot;
            drawSampleButton(graphics, chip, chip.contains(mouseX, mouseY), active && this.colorPickerOpen, PipeAppearanceTerminalGui.SURFACE_RAISED);
            SPSGui.Rect swatch = new SPSGui.Rect(chip.x() + 5, chip.y() + 4, chip.width() - 10, 12);
            if (active) {
                this.dyeColorSwatchBounds = swatch;
            }
            drawTerminalColorSwatch(graphics, swatch, color, chip.contains(mouseX, mouseY) || active && this.colorPickerOpen);
            Component label = themeColorSlotLabel(colors.size(), slot);
            drawCenteredSmallText(graphics, SPSGui.ellipsize(this.font, label.getString(), Math.round((chip.width() - 6) / 0.46F)), chip, chip.y() + 19, active ? PipeAppearanceTerminalGui.ACCENT_SELECTED : PipeAppearanceTerminalGui.TEXT_SECONDARY, 0.46F);
            int capturedSlot = slot;
            if (intersects(chip, viewport)) {
                this.addClick(chip, () -> {
                    this.activeColorSlot = capturedSlot;
                    this.colorPickerOpen = true;
                }, Component.translatable("screen.superpipeslide.pipe_appearance.tint_custom_slot", label));
            }
            if (colors.size() > 1) {
                SPSGui.Rect remove = new SPSGui.Rect(chip.right() - 9, chip.y() + 2, 7, 7);
                graphics.fill(remove.x(), remove.y(), remove.right(), remove.bottom(), remove.contains(mouseX, mouseY) ? 0xCCFF5C5C : 0x88332222);
                graphics.fill(remove.x() + 2, remove.y() + 3, remove.right() - 2, remove.y() + 4, 0xFFFFD7D7);
                if (intersects(remove, viewport)) {
                    this.addPriorityClick(remove, () -> removeThemeColor(capturedSlot), Component.translatable("screen.superpipeslide.pipe_appearance.palette_remove", label));
                }
            }
            x += chipW + gap;
        }
        if (colors.size() < PipeCoatingSelection.MAX_DYE_COLORS) {
            if (x + chipH > maxRight) {
                x = rect.x() + 8;
                rowY += chipH + gap;
            }
            SPSGui.Rect add = new SPSGui.Rect(x, rowY, chipH, chipH);
            drawSampleButton(graphics, add, add.contains(mouseX, mouseY), false, PipeAppearanceTerminalGui.SURFACE_RAISED);
            SPSGui.icon(graphics, add, SPSGui.Icon.COLOR_ADD, add.contains(mouseX, mouseY) ? PipeAppearanceTerminalGui.ACCENT_SELECTED : PipeAppearanceTerminalGui.TEXT_MUTED);
            if (intersects(add, viewport)) {
                this.addClick(add, this::addThemeColor, Component.translatable("screen.superpipeslide.pipe_appearance.palette_add"));
            }
        }

        int controlsY = rowY + chipH + 7;
        SPSGui.Rect auto = new SPSGui.Rect(rect.x() + 8, controlsY, 76, 16);
        SPSGui.Rect preserve = new SPSGui.Rect(auto.right() + 5, controlsY, rect.right() - auto.right() - 13, 16);
        drawSampleButton(graphics, auto, auto.contains(mouseX, mouseY), false, PipeAppearanceTerminalGui.SURFACE_RAISED);
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, Component.translatable("screen.superpipeslide.pipe_appearance.palette_auto").getString(), Math.round((auto.width() - 8) / 0.58F)), auto.x() + 5, auto.y() + 5, PipeAppearanceTerminalGui.TEXT_SECONDARY, 0.58F);
        if (intersects(auto, viewport)) {
            this.addClick(auto, this::applyThemeSuggestion, Component.translatable("screen.superpipeslide.pipe_appearance.palette_auto.tooltip"));
        }
        boolean preserveActive = this.currentSelection.preserveAccents();
        drawSampleButton(graphics, preserve, preserve.contains(mouseX, mouseY), preserveActive, PipeAppearanceTerminalGui.SURFACE_RAISED);
        SPSGui.icon(graphics, new SPSGui.Rect(preserve.x() + 3, preserve.y() + 2, 12, 12), preserveActive ? SPSGui.Icon.CHECKBOX_ON : SPSGui.Icon.CHECKBOX_OFF, preserveActive ? PipeAppearanceTerminalGui.ACCENT_SELECTED : PipeAppearanceTerminalGui.TEXT_MUTED);
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, Component.translatable("screen.superpipeslide.pipe_appearance.palette_preserve").getString(), Math.round((preserve.width() - 20) / 0.54F)), preserve.x() + 18, preserve.y() + 5, preserveActive ? PipeAppearanceTerminalGui.TEXT : PipeAppearanceTerminalGui.TEXT_SECONDARY, 0.54F);
        if (intersects(preserve, viewport)) {
            this.addClick(preserve, () -> applySelectionDirect(this.currentSelection.withPreserveAccents(!this.currentSelection.preserveAccents())), Component.translatable("screen.superpipeslide.pipe_appearance.palette_preserve.tooltip"));
        }
        return controlsY + 18;
    }

    private void drawColorPicker(GuiGraphicsExtractor graphics, SPSGui.Rect content, int mouseX, int mouseY) {
        this.colorPickerBounds = colorPickerRect(content);
        graphics.fill(this.colorPickerBounds.x() - 4, this.colorPickerBounds.y() - 4, this.colorPickerBounds.right() + 4, this.colorPickerBounds.bottom() + 4, 0xBB0A1018);
        graphics.outline(this.colorPickerBounds.x() - 4, this.colorPickerBounds.y() - 4, this.colorPickerBounds.width() + 8, this.colorPickerBounds.height() + 8, PipeAppearanceTerminalGui.BORDER);
        int currentColor = colorForSlot(this.activeColorSlot);
        Component slotLabel = currentColorSlotLabel(this.activeColorSlot);
        SPSGui.colorPicker(graphics, this.colorPickerBounds, currentColor, mouseX, mouseY);
        SPSGui.smallText(graphics, this.font, slotLabel.getString(), this.colorPickerBounds.x() + 27, this.colorPickerBounds.y() + 7, PipeAppearanceTerminalGui.TEXT_SECONDARY, 0.62F);
        SPSGui.smallText(graphics, this.font, "#" + String.format("%06X", currentColor & 0x00FFFFFF), this.colorPickerBounds.right() - 76, this.colorPickerBounds.y() + 7, PipeAppearanceTerminalGui.TEXT_MUTED, 0.58F);
    }

    private SPSGui.Rect colorPickerRect(SPSGui.Rect content) {
        int pickerW = 206;
        int pickerH = 132;
        int minX = content.x() + 6;
        int maxX = content.right() - pickerW - 6;
        int minY = content.y() + 6;
        int maxY = content.bottom() - pickerH - 26;
        SPSGui.Rect anchor = this.dyeColorSwatchBounds.width() > 0 ? this.dyeColorSwatchBounds : new SPSGui.Rect(content.right() - 34, content.bottom() - 120, 26, 18);

        int x = anchor.x() - pickerW - 8;
        if (x < minX) {
            x = anchor.right() + 8;
        }
        x = clampInt(x, minX, Math.max(minX, maxX));

        int y = anchor.y() - 34;
        if (y + pickerH > maxY) {
            y = anchor.y() - pickerH - 6;
        }
        y = clampInt(y, minY, Math.max(minY, maxY));
        return new SPSGui.Rect(x, y, pickerW, pickerH);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void drawBottomActions(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        SPSGui.Rect cancel = new SPSGui.Rect(rect.right() - 154, rect.y() + 2, 72, 18);
        SPSGui.Rect done = new SPSGui.Rect(rect.right() - 76, rect.y() + 2, 76, 18);
        drawTextButton(graphics, cancel, Component.translatable("screen.superpipeslide.action.cancel"), cancel.contains(mouseX, mouseY), false);
        drawTextButton(graphics, done, Component.translatable("screen.superpipeslide.action.done"), done.contains(mouseX, mouseY), true);
        this.addClick(cancel, this::cancelAndReturn, Component.translatable("screen.superpipeslide.action.cancel"));
        this.addClick(done, this::returnToParent, Component.translatable("screen.superpipeslide.action.done"));
    }

    private void drawTextButton(GuiGraphicsExtractor graphics, SPSGui.Rect rect, Component text, boolean hovered, boolean primary) {
        PipeAppearanceTerminalGui.commandButton(graphics, this.font, rect, text, hovered, false, primary);
    }

    private void drawCenteredScrollingSmallText(GuiGraphicsExtractor graphics, String text, SPSGui.Rect bounds, int y, int color, float scale, int tickSeed) {
        String visible = SPSGui.scrollingText(this.font, text, Math.round((bounds.width() - 6) / scale), tickSeed);
        drawCenteredSmallText(graphics, visible, bounds, y, color, scale);
    }

    private void drawCenteredSmallText(GuiGraphicsExtractor graphics, String text, SPSGui.Rect bounds, int y, int color, float scale) {
        int textWidth = SPSGui.scaledWidth(this.font, text, scale);
        int x = clampInt(bounds.x() + (bounds.width() - textWidth) / 2, bounds.x() + 3, Math.max(bounds.x() + 3, bounds.right() - textWidth - 3));
        SPSGui.smallText(graphics, this.font, text, x, y, color, scale);
    }

    private static void drawSampleButton(GuiGraphicsExtractor graphics, SPSGui.Rect rect, boolean hovered, boolean selected, int fillColor) {
        int fill = hovered ? 0xF711281D : PipeAppearanceTerminalGui.SURFACE_RAISED;
        int border = selected ? PipeAppearanceTerminalGui.ACCENT_SELECTED : hovered ? PipeAppearanceTerminalGui.ACCENT : PipeAppearanceTerminalGui.BORDER_MUTED;
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), border);
        graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.y() + 2, selected ? 0x4446FF7A : 0x22143D2A);
        if (selected) {
            graphics.fill(rect.x() + 3, rect.bottom() - 3, rect.right() - 3, rect.bottom() - 1, PipeAppearanceTerminalGui.ACCENT_SELECTED);
        }
    }

    private void chooseSelection(PipeCoatingSelection selection) {
        PipeCoatingSelection normalized = PipeAppearanceDefinitions.normalizeSelection(selection);
        CoatingTextureRisk risk = riskFor(normalized);
        String riskKey = riskKey(normalized);
        if (risk.requiresConfirmation() && !this.confirmedRiskKeys.contains(riskKey)) {
            this.pendingRiskSelection = normalized;
            this.pendingRisk = risk;
            this.colorPickerOpen = false;
            return;
        }
        applySelectionDirect(normalized);
    }

    private void applySelectionDirect(PipeCoatingSelection selection) {
        this.currentSelection = PipeAppearanceDefinitions.normalizeSelection(selection);
        this.confirmedRiskKeys.add(riskKey(this.currentSelection));
        if (!this.currentSelection.dyeMode().usesColor()) {
            this.colorPickerOpen = false;
        }
        if (this.activeColorSlot >= currentColorSlotCount()) {
            this.activeColorSlot = 0;
        }
        this.parent.updateSlotSelectionFromSelector(this.slotId, this.currentSelection);
    }

    private void applyPickerColor(double mouseX, double mouseY) {
        int currentColor = colorForSlot(this.activeColorSlot);
        applySelectionDirect(withColorSlot(this.activeColorSlot, SPSGui.colorFromPicker(this.colorPickerBounds, currentColor, mouseX, mouseY)));
        this.colorPickerOpen = true;
    }

    private int colorForSlot(int slot) {
        return this.currentSelection.dyeMode().variableColorSlots()
                ? this.currentSelection.dyeColors().get(Math.max(0, Math.min(this.currentSelection.dyeColors().size() - 1, slot)))
                : switch (slot) {
                    case 1 -> this.currentSelection.secondaryDyeColor();
                    case 2 -> this.currentSelection.tertiaryDyeColor();
                    default -> this.currentSelection.dyeColor();
                };
    }

    private PipeCoatingSelection withColorSlot(int slot, int color) {
        return this.currentSelection.withColorSlot(slot, color);
    }

    private static Component colorSlotLabel(PipeCoatingDyeMode mode, int slot) {
        String suffix = switch (mode) {
            case DUOTONE -> slot == 0 ? "light" : "dark";
            case TRITONE -> slot == 0 ? "middle" : slot == 1 ? "dark" : "light";
            case ACCENT_PRESERVE -> slot == 0 ? "body" : "accent";
            default -> "primary";
        };
        return Component.translatable("screen.superpipeslide.pipe_appearance.color_slot." + suffix);
    }

    private Component currentColorSlotLabel(int slot) {
        if (this.currentSelection.dyeMode().variableColorSlots()) {
            return themeColorSlotLabel(this.currentSelection.dyeColors().size(), slot);
        }
        return colorSlotLabel(this.currentSelection.dyeMode(), slot);
    }

    private static Component themeColorSlotLabel(int count, int slot) {
        String suffix = switch (count) {
            case 1 -> "primary";
            case 2 -> slot == 0 ? "body" : "accent";
            case 3 -> slot == 0 ? "dark" : slot == 1 ? "body" : "light";
            case 4 -> switch (slot) {
                case 0 -> "dark";
                case 1 -> "body";
                case 2 -> "accent";
                default -> "light";
            };
            default -> switch (slot) {
                case 0 -> "dark";
                case 1 -> "body";
                case 2 -> "secondary";
                case 3 -> "accent";
                default -> "light";
            };
        };
        return Component.translatable("screen.superpipeslide.pipe_appearance.color_slot." + suffix);
    }

    private static int shiftedThemeColor(int seed, int index) {
        int[] accents = { 0xFF2E8CFF, 0xFFFFD34D, 0xFFB7FF66, 0xFFFF7A7A };
        int accent = accents[Math.floorMod(index - 1, accents.length)];
        double mix = 0.34D;
        int red = clampInt((int) Math.round(((seed >>> 16) & 0xFF) * (1.0D - mix) + ((accent >>> 16) & 0xFF) * mix), 0, 255);
        int green = clampInt((int) Math.round(((seed >>> 8) & 0xFF) * (1.0D - mix) + ((accent >>> 8) & 0xFF) * mix), 0, 255);
        int blue = clampInt((int) Math.round((seed & 0xFF) * (1.0D - mix) + (accent & 0xFF) * mix), 0, 255);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private int currentColorSlotCount() {
        if (!this.currentSelection.dyeMode().usesColor()) {
            return 0;
        }
        return this.currentSelection.dyeMode().variableColorSlots()
                ? this.currentSelection.dyeColors().size()
                : this.currentSelection.dyeMode().colorSlotCount();
    }

    private void addThemeColor() {
        List<Integer> colors = new ArrayList<>(this.currentSelection.dyeColors());
        if (colors.size() >= PipeCoatingSelection.MAX_DYE_COLORS) {
            return;
        }
        int seed = colors.getLast();
        colors.add(shiftedThemeColor(seed, colors.size()));
        this.activeColorSlot = colors.size() - 1;
        applySelectionDirect(this.currentSelection.withDyeColors(colors));
        this.colorPickerOpen = true;
    }

    private void removeThemeColor(int slot) {
        List<Integer> colors = new ArrayList<>(this.currentSelection.dyeColors());
        if (colors.size() <= 1 || slot < 0 || slot >= colors.size()) {
            return;
        }
        colors.remove(slot);
        this.activeColorSlot = Math.min(this.activeColorSlot, colors.size() - 1);
        applySelectionDirect(this.currentSelection.withDyeColors(colors));
    }

    private void applyThemeSuggestion() {
        PipeCoatingRenderResolver.ThemePaletteSuggestion suggestion = PipeCoatingRenderResolver.suggestThemePalette(this.currentSelection);
        applySelectionDirect(this.currentSelection
                .withDyeMode(PipeCoatingDyeMode.THEME_PALETTE)
                .withDyeColors(suggestion.colors())
                .withPreserveAccents(suggestion.preserveAccents()));
        this.activeColorSlot = 0;
        this.colorPickerOpen = false;
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

    private void drawChoiceSwatch(GuiGraphicsExtractor graphics, SPSGui.Rect rect, PipeCoatingSelection selection, boolean framed) {
        PipeCoatingSelection normalized = PipeAppearanceDefinitions.normalizeSelection(selection);
        if (normalized.dyeMode() == PipeCoatingDyeMode.MULTIPLY) {
            Block block = PipeCoatingRenderResolver.blockFor(normalized.blockId());
            drawSpriteSwatch(graphics, rect, PipeCoatingRenderResolver.spriteFor(block, normalized.texturePick()), framed, normalized.dyeColor());
            return;
        }
        drawResolvedSwatch(graphics, rect, PipeCoatingRenderResolver.resolve(normalized), framed);
    }

    private void drawSpriteSwatch(GuiGraphicsExtractor graphics, SPSGui.Rect rect, net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, boolean framed, int color) {
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

    private void drawResolvedSwatch(GuiGraphicsExtractor graphics, SPSGui.Rect rect, PipeCoatingRenderResolver.ResolvedPipeCoating resolved, boolean framed) {
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

    private static void drawTerminalColorSwatch(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int color, boolean hovered) {
        PipeAppearanceTerminalGui.swatchFrame(graphics, rect, hovered, false);
        graphics.fill(rect.x() + 3, rect.y() + 3, rect.right() - 3, rect.bottom() - 3, SPSGui.opaque(color));
        graphics.outline(rect.x() + 3, rect.y() + 3, rect.width() - 6, rect.height() - 6, 0x88000000);
    }

    private static void drawTerminalEmptySwatch(GuiGraphicsExtractor graphics, SPSGui.Rect rect, boolean hovered) {
        PipeAppearanceTerminalGui.swatchFrame(graphics, rect, hovered, false);
        SPSGui.icon(graphics, rect, SPSGui.Icon.COLOR_ADD, hovered ? PipeAppearanceTerminalGui.ACCENT_SELECTED : PipeAppearanceTerminalGui.TEXT_MUTED);
    }

    private Component blockChoiceTooltip(PipeCoatingSelection selection, Component blockName) {
        CoatingTextureRisk risk = riskFor(selection);
        if (!risk.warns()) {
            return Component.translatable("screen.superpipeslide.pipe_appearance.use_recommended_block", blockName);
        }
        return Component.translatable("screen.superpipeslide.pipe_appearance.use_risky_block", blockName, Component.translatable(risk.bodyKey()));
    }

    private CoatingTextureRisk riskFor(PipeCoatingSelection selection) {
        if (selection == null || BuiltInRegistries.BLOCK.getOptional(selection.blockId()).isEmpty()) {
            return CoatingTextureRisk.UNRESOLVED;
        }
        PipeCoatingSelection normalized = PipeAppearanceDefinitions.normalizeSelection(selection);
        if (BuiltInRegistries.BLOCK.getOptional(normalized.blockId()).isEmpty()) {
            return CoatingTextureRisk.UNRESOLVED;
        }
        PipeCoatingRenderResolver.BlockTextureModelProfile profile = PipeCoatingRenderResolver.modelProfile(normalized.blockId());
        if (!profile.resolved()) {
            return CoatingTextureRisk.UNRESOLVED;
        }
        if (profile.nonStandardModel()) {
            return CoatingTextureRisk.NON_STANDARD_MODEL;
        }
        if (!"minecraft".equals(normalized.blockId().getNamespace())) {
            return CoatingTextureRisk.MODDED_BLOCK;
        }
        PipeCoatingRenderResolver.ResolvedPipeCoating resolved = PipeCoatingRenderResolver.resolve(normalized);
        if (resolved.translucent()) {
            return CoatingTextureRisk.SPECIAL_RENDER;
        }
        return CoatingTextureRisk.STANDARD;
    }

    private static String riskKey(PipeCoatingSelection selection) {
        PipeCoatingSelection normalized = PipeAppearanceDefinitions.normalizeSelection(selection);
        return normalized.blockId().toString();
    }

    private SPSGui.Rect riskConfirmationBounds() {
        int width = Math.min(300, Math.max(244, this.panel.width() - 96));
        int height = 104;
        return new SPSGui.Rect(this.panel.x() + (this.panel.width() - width) / 2, this.panel.y() + (this.panel.height() - height) / 2 + 10, width, height);
    }

    private SPSGui.Rect riskCancelBounds(SPSGui.Rect card) {
        return new SPSGui.Rect(card.right() - 176, card.bottom() - 25, 78, 18);
    }

    private SPSGui.Rect riskAcceptBounds(SPSGui.Rect card) {
        return new SPSGui.Rect(card.right() - 92, card.bottom() - 25, 82, 18);
    }

    private void drawRiskConfirmation(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        SPSGui.Rect card = riskConfirmationBounds();
        graphics.fill(this.panel.x() + 4, this.panel.y() + 22, this.panel.right() - 4, this.panel.bottom() - 4, 0xAA000000);
        PipeAppearanceTerminalGui.raisedPanel(graphics, card);
        SPSGui.icon(graphics, new SPSGui.Rect(card.x() + 9, card.y() + 8, 16, 16), SPSGui.Icon.WARNING, PipeAppearanceTerminalGui.WARNING);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.pipe_appearance.risk_confirm.title"), card.x() + 30, card.y() + 12, PipeAppearanceTerminalGui.WARNING);
        String blockName = this.pendingRiskSelection == null ? "" : PipeCoatingRenderResolver.blockDisplayName(this.pendingRiskSelection);
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, blockName, Math.round((card.width() - 20) / 0.68F)), card.x() + 10, card.y() + 32, PipeAppearanceTerminalGui.TEXT, 0.68F);
        String body = this.pendingRisk == null ? "" : Component.translatable(this.pendingRisk.bodyKey()).getString();
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, body, Math.round((card.width() - 20) / 0.62F)), card.x() + 10, card.y() + 47, PipeAppearanceTerminalGui.TEXT_SECONDARY, 0.62F);
        SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.pipe_appearance.risk_confirm.hint").getString(), card.x() + 10, card.y() + 60, PipeAppearanceTerminalGui.TEXT_MUTED, 0.58F);
        SPSGui.Rect cancel = riskCancelBounds(card);
        SPSGui.Rect accept = riskAcceptBounds(card);
        PipeAppearanceTerminalGui.commandButton(graphics, this.font, cancel, Component.translatable("screen.superpipeslide.action.cancel"), cancel.contains(mouseX, mouseY), false, false);
        PipeAppearanceTerminalGui.commandButton(graphics, this.font, accept, Component.translatable("screen.superpipeslide.pipe_appearance.risk_confirm.accept"), accept.contains(mouseX, mouseY), false, true);
    }

    private Map<Identifier, ItemStack> inventoryBlocks(Inventory inventory) {
        Map<Identifier, ItemStack> blocks = new LinkedHashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
                continue;
            }
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
            blocks.putIfAbsent(blockId, stack.copy());
        }
        return blocks;
    }

    private void returnToParent() {
        this.minecraft.setScreen(this.parent);
    }

    private void cancelAndReturn() {
        this.parent.updateSlotSelectionFromSelector(this.slotId, this.initialSelection);
        this.minecraft.setScreen(this.parent);
    }

    @Override
    protected void onBack() {
        returnToParent();
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && this.pendingRiskSelection != null) {
            SPSGui.Rect card = riskConfirmationBounds();
            if (riskAcceptBounds(card).contains(event.x(), event.y())) {
                PipeCoatingSelection accepted = this.pendingRiskSelection;
                this.confirmedRiskKeys.add(riskKey(accepted));
                this.pendingRiskSelection = null;
                this.pendingRisk = null;
                applySelectionDirect(accepted);
                return true;
            }
            if (riskCancelBounds(card).contains(event.x(), event.y()) || !card.contains(event.x(), event.y())) {
                this.pendingRiskSelection = null;
                this.pendingRisk = null;
                return true;
            }
            return true;
        }
        if (event.button() == 0 && this.colorPickerOpen) {
            if (SPSGui.colorPickerClose(this.colorPickerBounds).contains(event.x(), event.y())) {
                this.colorPickerOpen = false;
                return true;
            }
            if (SPSGui.colorPickerField(this.colorPickerBounds).contains(event.x(), event.y())) {
                applyPickerColor(event.x(), event.y());
                return true;
            }
            if (this.colorPickerBounds.contains(event.x(), event.y())) {
                return true;
            }
            this.colorPickerOpen = false;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == 0 && this.colorPickerOpen && SPSGui.colorPickerField(this.colorPickerBounds).contains(event.x(), event.y())) {
            applyPickerColor(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.materialGridScrollBounds.contains(mouseX, mouseY) && this.materialGridMaxScroll > 0.5D) {
            this.materialGridScroll = clamp(this.materialGridScroll - scrollY * 24.0D, 0.0D, this.materialGridMaxScroll);
            return true;
        }
        if (this.optionsScrollBounds.contains(mouseX, mouseY) && this.optionsMaxScroll > 0.5D) {
            this.optionsScroll = clamp(this.optionsScroll - scrollY * 18.0D, 0.0D, this.optionsMaxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.pendingRiskSelection != null) {
            return;
        }
        if (this.colorPickerOpen && this.colorPickerBounds.contains(mouseX, mouseY)) {
            return;
        }
        for (ClickAction clickAction : this.clickActions) {
            if (clickAction.bounds().contains(mouseX, mouseY)) {
                PipeAppearanceTerminalGui.terminalTooltip(graphics, this.font, clickAction.tooltip(), mouseX, mouseY, new SPSGui.Rect(0, 0, this.width, this.height));
                return;
            }
        }
    }

    private static boolean intersects(SPSGui.Rect first, SPSGui.Rect second) {
        return first.right() > second.x() && first.x() < second.right() && first.bottom() > second.y() && first.y() < second.bottom();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum CoatingTextureRisk {
        STANDARD(false, false, "screen.superpipeslide.pipe_appearance.risk.standard", "screen.superpipeslide.pipe_appearance.risk.standard.body"),
        MODDED_BLOCK(true, true, "screen.superpipeslide.pipe_appearance.risk.modded", "screen.superpipeslide.pipe_appearance.risk.modded.body"),
        NON_STANDARD_MODEL(true, true, "screen.superpipeslide.pipe_appearance.risk.non_standard", "screen.superpipeslide.pipe_appearance.risk.non_standard.body"),
        SPECIAL_RENDER(true, true, "screen.superpipeslide.pipe_appearance.risk.special", "screen.superpipeslide.pipe_appearance.risk.special.body"),
        UNRESOLVED(true, true, "screen.superpipeslide.pipe_appearance.risk.unresolved", "screen.superpipeslide.pipe_appearance.risk.unresolved.body");

        private final boolean warns;
        private final boolean requiresConfirmation;
        private final String labelKey;
        private final String bodyKey;

        CoatingTextureRisk(boolean warns, boolean requiresConfirmation, String labelKey, String bodyKey) {
            this.warns = warns;
            this.requiresConfirmation = requiresConfirmation;
            this.labelKey = labelKey;
            this.bodyKey = bodyKey;
        }

        boolean warns() {
            return this.warns;
        }

        boolean requiresConfirmation() {
            return this.requiresConfirmation;
        }

        String labelKey() {
            return this.labelKey;
        }

        String bodyKey() {
            return this.bodyKey;
        }
    }

    private enum SelectorTab {
        RECOMMENDED("screen.superpipeslide.pipe_appearance.source.recommended", "screen.superpipeslide.pipe_appearance.source.recommended.tooltip"),
        INVENTORY("screen.superpipeslide.pipe_appearance.source.inventory", "screen.superpipeslide.pipe_appearance.source.inventory.tooltip");

        private final String nameKey;
        private final String tooltipKey;

        SelectorTab(String nameKey, String tooltipKey) {
            this.nameKey = nameKey;
            this.tooltipKey = tooltipKey;
        }

        String nameKey() {
            return this.nameKey;
        }

        String tooltipKey() {
            return this.tooltipKey;
        }
    }
}
