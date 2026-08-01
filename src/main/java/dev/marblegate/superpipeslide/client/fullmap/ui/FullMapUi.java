package dev.marblegate.superpipeslide.client.fullmap.ui;

import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.render.SmoothGuiPrimitives;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class FullMapUi {
    private FullMapUi() {}

    public static void cardFrame(GuiGraphicsExtractor graphics, SPSGui.Rect bounds, boolean active) {
        FullMapPalette palette = FullMapTheme.palette();
        graphics.fill(bounds.x() + 2, bounds.y() + 3, bounds.right() + 2, bounds.bottom() + 3, FullMapTheme.SHADOW);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), active ? palette.surfaceCardActive() : palette.surfaceCardInactive());
        graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), active ? palette.borderActive() : palette.border());
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.y() + 2, FullMapTheme.INNER_HIGHLIGHT_STRONG);
    }

    public static void cardHeader(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect bounds, Component title, Component meta, boolean active) {
        cardHeader(graphics, font, bounds, DisplayNameStack.of(title.getString()), meta, active);
    }

    public static void cardHeader(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect bounds, DisplayNameStack title, Component meta, boolean active) {
        FullMapPalette palette = FullMapTheme.palette();
        boolean hasMeta = !meta.getString().isBlank();
        int headerHeight = cardHeaderHeight(title, meta);
        SPSGui.Rect header = new SPSGui.Rect(bounds.x() + 1, bounds.y() + 1, bounds.width() - 2, headerHeight - 1);
        graphics.fill(header.x(), header.y(), header.right(), header.bottom(), active ? palette.surfaceHeaderActive() : palette.surfaceHeader());
        graphics.fill(bounds.x() + 1, bounds.y() + headerHeight, bounds.right() - 1, bounds.y() + headerHeight + 1, palette.border());
        int titleWidth = Math.max(20, bounds.width() - FullMapTheme.CARD_HEADER_TITLE_RESERVE);
        int y = bounds.y() + FullMapTheme.SPACE_SM;
        drawNamePrimary(graphics, font, title, bounds.x() + FullMapTheme.CARD_PADDING, y, titleWidth, palette.textPrimary(), FullMapTheme.TYPE_TITLE);
        y += FullMapTheme.CARD_HEADER_PRIMARY_ADVANCE;
        if (title.hasSecondary()) {
            drawNameSecondary(graphics, font, title, bounds.x() + FullMapTheme.CARD_PADDING, y, titleWidth, palette.textMuted(), FullMapTheme.TYPE_META);
            y += FullMapTheme.CARD_HEADER_SECONDARY_ADVANCE;
        }
        if (hasMeta) {
            SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, meta.getString(), Math.round(titleWidth / FullMapTheme.TYPE_TINY)), bounds.x() + FullMapTheme.CARD_PADDING, y, palette.textSecondary(), FullMapTheme.TYPE_TINY);
        }
    }

    public static int cardHeaderHeight(DisplayNameStack title, Component meta) {
        boolean hasSecondary = title != null && title.hasSecondary();
        boolean hasMeta = meta != null && !meta.getString().isBlank();
        if (hasSecondary && hasMeta) {
            return FullMapTheme.CARD_HEADER_WITH_STACK;
        }
        if (hasSecondary || hasMeta) {
            return FullMapTheme.CARD_HEADER_WITH_META;
        }
        return FullMapTheme.CARD_HEADER_HEIGHT;
    }

    public static int nameStackWidth(Font font, DisplayNameStack name, float primaryScale, float secondaryScale) {
        if (name == null) {
            return 0;
        }
        int width = Math.round(font.width(name.primary()) * primaryScale);
        if (name.hasSecondary()) {
            width = Math.max(width, Math.round(font.width(name.secondary()) * secondaryScale));
        }
        return width;
    }

    public static int nameStackHeight(DisplayNameStack name, float primaryScale, float secondaryScale, int gap) {
        if (name == null) {
            return 0;
        }
        int primaryHeight = Math.max(7, Math.round(9.0F * primaryScale));
        if (!name.hasSecondary()) {
            return primaryHeight;
        }
        return primaryHeight + gap + Math.max(6, Math.round(8.0F * secondaryScale));
    }

    public static void drawNameStack(GuiGraphicsExtractor graphics, Font font, DisplayNameStack name, int x, int y, int maxWidth, int primaryColor, int secondaryColor, float primaryScale, float secondaryScale, int gap) {
        if (name == null) {
            return;
        }
        drawNamePrimary(graphics, font, name, x, y, maxWidth, primaryColor, primaryScale);
        if (name.hasSecondary()) {
            int secondaryY = y + Math.max(7, Math.round(9.0F * primaryScale)) + gap;
            drawNameSecondary(graphics, font, name, x, secondaryY, maxWidth, secondaryColor, secondaryScale);
        }
    }

    public static void drawNamePrimary(GuiGraphicsExtractor graphics, Font font, DisplayNameStack name, int x, int y, int maxWidth, int color, float scale) {
        // Ceil, not round: a label box is usually the measured text width itself, and
        // round(maxWidth / scale) can land a pixel below the true width and ellipsize text
        // that actually fits (visible at specific zoom-dependent scales).
        String text = SPSGui.ellipsize(font, name.primary(), Math.max(1, (int) Math.ceil(maxWidth / scale)));
        if (scale >= 0.995F) {
            SPSGui.text(graphics, font, text, x, y, color);
        } else {
            SPSGui.smallText(graphics, font, text, x, y, color, scale);
        }
    }

    public static void drawNameSecondary(GuiGraphicsExtractor graphics, Font font, DisplayNameStack name, int x, int y, int maxWidth, int color, float scale) {
        if (!name.hasSecondary()) {
            return;
        }
        SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, name.secondary(), Math.max(1, (int) Math.ceil(maxWidth / scale))), x, y, color, scale);
    }

    /**
     * Full-map styled icon button; delegates to {@link SPSGui#drawIconButton} with the
     * palette's hover/selection color rules.
     */
    public static void iconButton(GuiGraphicsExtractor graphics, SPSGui.Rect rect, boolean hovered, boolean selected, boolean disabled, SPSGui.Icon icon) {
        FullMapPalette palette = FullMapTheme.palette();
        SPSGui.drawIconButton(graphics, rect, icon, disabled, hovered, selected, palette.textSecondary(), SPSGui.INFO, palette.surfaceControlHover(), palette.surfaceControlSelected(), palette.borderSelected());
    }

    public static void toolbarPanel(GuiGraphicsExtractor graphics, SPSGui.Rect panel) {
        FullMapPalette palette = FullMapTheme.palette();
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), palette.surfaceToolbar());
        graphics.outline(panel.x(), panel.y(), panel.width(), panel.height(), palette.border());
        graphics.fill(panel.x() + 1, panel.y() + 1, panel.right() - 1, panel.y() + 2, FullMapTheme.INNER_HIGHLIGHT_EXTRA_SOFT);
    }

    public static void dimensionChip(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect rect, String label, int color, boolean muted) {
        int fill = SPSGui.withAlpha(color, muted ? 0x18 : 0x26);
        Vec2 center = new Vec2(rect.x() + rect.width() * 0.5D, rect.y() + rect.height() * 0.5D);
        SmoothGuiPrimitives.capsule(graphics, center, rect.width(), rect.height(), SPSGui.withAlpha(color, muted ? 0x55 : 0x88));
        SmoothGuiPrimitives.capsule(graphics, center, Math.max(1, rect.width() - 2), Math.max(1, rect.height() - 2), fill);
        SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, label, Math.round((rect.width() - 7) / FullMapTheme.TYPE_TINY)), rect.x() + 4, rect.y() + Math.max(1, (rect.height() - 7) / 2), FullMapTheme.palette().textMuted(), FullMapTheme.TYPE_TINY);
    }

    public static void routeChip(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect chip, String label, List<Integer> colors, boolean hovered, boolean selected, int seed) {
        FullMapPalette palette = FullMapTheme.palette();
        graphics.fill(chip.x(), chip.y(), chip.right(), chip.bottom(), selected ? palette.surfaceControlSelected() : hovered ? palette.surfaceControlHover() : palette.surfaceControl());
        graphics.outline(chip.x(), chip.y(), chip.width(), chip.height(), selected ? palette.borderSelected() : palette.border());
        drawThemeBands(graphics, chip, colors);
        SPSGui.smallScrollingText(graphics, font, label, chip.x() + 8, chip.y() + Math.max(2, (chip.height() - 8) / 2), selected ? SPSGui.INFO : palette.textSecondary(), FullMapTheme.TYPE_META, Math.max(8, chip.width() - 13), seed);
    }

    public static void drawThemeBands(GuiGraphicsExtractor graphics, SPSGui.Rect chip, List<Integer> colors) {
        List<Integer> normalized = colors == null || colors.isEmpty() ? List.of(0xFFB8C0CA) : colors.stream().limit(3).map(SPSGui::opaque).toList();
        int stripeHeight = Math.max(1, chip.height() / normalized.size());
        for (int i = 0; i < normalized.size(); i++) {
            int y1 = chip.y() + i * stripeHeight;
            int y2 = i == normalized.size() - 1 ? chip.bottom() : Math.min(chip.bottom(), y1 + stripeHeight);
            graphics.fill(chip.x(), y1, chip.x() + 5, y2, normalized.get(i));
        }
    }
}
