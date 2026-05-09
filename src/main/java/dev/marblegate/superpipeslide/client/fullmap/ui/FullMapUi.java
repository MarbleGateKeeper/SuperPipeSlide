package dev.marblegate.superpipeslide.client.fullmap.ui;


import dev.marblegate.superpipeslide.client.fullmap.render.SmoothGuiPrimitives;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class FullMapUi {
    private FullMapUi() {
    }

    public static void cardFrame(GuiGraphicsExtractor graphics, SPSGui.Rect bounds, boolean active) {
        graphics.fill(bounds.x() + 2, bounds.y() + 3, bounds.right() + 2, bounds.bottom() + 3, FullMapTheme.SHADOW);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), active ? FullMapTheme.SURFACE_CARD_ACTIVE : FullMapTheme.SURFACE_CARD_INACTIVE);
        graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), active ? FullMapTheme.BORDER_ACTIVE : FullMapTheme.BORDER);
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.y() + 2, 0xBFFFFFFF);
    }

    public static void cardHeader(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect bounds, Component title, Component meta, boolean active) {
        cardHeader(graphics, font, bounds, DisplayNameStack.of(title.getString()), meta, active);
    }

    public static void cardHeader(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect bounds, DisplayNameStack title, Component meta, boolean active) {
        boolean hasMeta = !meta.getString().isBlank();
        int headerHeight = cardHeaderHeight(title, meta);
        SPSGui.Rect header = new SPSGui.Rect(bounds.x() + 1, bounds.y() + 1, bounds.width() - 2, headerHeight - 1);
        graphics.fill(header.x(), header.y(), header.right(), header.bottom(), active ? FullMapTheme.SURFACE_HEADER_ACTIVE : FullMapTheme.SURFACE_HEADER);
        graphics.fill(bounds.x() + 1, bounds.y() + headerHeight, bounds.right() - 1, bounds.y() + headerHeight + 1, FullMapTheme.BORDER);
        int titleWidth = Math.max(20, bounds.width() - 58);
        int y = bounds.y() + 4;
        drawNamePrimary(graphics, font, title, bounds.x() + 8, y, titleWidth, FullMapTheme.TEXT_PRIMARY, 1.0F);
        y += 11;
        if (title.hasSecondary()) {
            drawNameSecondary(graphics, font, title, bounds.x() + 8, y, titleWidth, FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_META);
            y += 9;
        }
        if (hasMeta) {
            SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, meta.getString(), Math.round(titleWidth / FullMapTheme.TYPE_TINY)), bounds.x() + 8, y, FullMapTheme.TEXT_SECONDARY, FullMapTheme.TYPE_TINY);
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
        String text = SPSGui.ellipsize(font, name.primary(), Math.max(1, Math.round(maxWidth / scale)));
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
        SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, name.secondary(), Math.max(1, Math.round(maxWidth / scale))), x, y, color, scale);
    }

    public static void iconButton(GuiGraphicsExtractor graphics, SPSGui.Rect rect, boolean hovered, boolean selected, boolean disabled, SPSGui.Icon icon) {
        int bg = disabled ? FullMapTheme.SURFACE_CONTROL_DISABLED : selected ? FullMapTheme.SURFACE_CONTROL_SELECTED : hovered ? FullMapTheme.SURFACE_CONTROL_HOVER : FullMapTheme.SURFACE_CONTROL;
        int border = disabled ? FullMapTheme.BORDER_MUTED : selected ? FullMapTheme.BORDER_SELECTED : FullMapTheme.BORDER;
        int color = disabled ? FullMapTheme.TEXT_DISABLED : selected || hovered ? SPSGui.INFO : FullMapTheme.TEXT_SECONDARY;
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), bg);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), border);
        SPSGui.icon(graphics, rect, icon, color);
    }

    public static void toolbarPanel(GuiGraphicsExtractor graphics, SPSGui.Rect panel) {
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), FullMapTheme.SURFACE_TOOLBAR);
        graphics.outline(panel.x(), panel.y(), panel.width(), panel.height(), FullMapTheme.BORDER);
        graphics.fill(panel.x() + 1, panel.y() + 1, panel.right() - 1, panel.y() + 2, 0x77FFFFFF);
    }

    public static void dimensionChip(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect rect, String label, int color, boolean muted) {
        int fill = SPSGui.withAlpha(color, muted ? 0x18 : 0x26);
        Vec2 center = new Vec2(rect.x() + rect.width() * 0.5D, rect.y() + rect.height() * 0.5D);
        SmoothGuiPrimitives.capsule(graphics, center, rect.width(), rect.height(), SPSGui.withAlpha(color, muted ? 0x55 : 0x88));
        SmoothGuiPrimitives.capsule(graphics, center, Math.max(1, rect.width() - 2), Math.max(1, rect.height() - 2), fill);
        SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, label, Math.round((rect.width() - 7) / FullMapTheme.TYPE_TINY)), rect.x() + 4, rect.y() + Math.max(1, (rect.height() - 7) / 2), FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_TINY);
    }

    public static void routeChip(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect chip, String label, List<Integer> colors, boolean hovered, boolean selected, int seed) {
        graphics.fill(chip.x(), chip.y(), chip.right(), chip.bottom(), selected ? FullMapTheme.SURFACE_CONTROL_SELECTED : hovered ? FullMapTheme.SURFACE_CONTROL_HOVER : FullMapTheme.SURFACE_CONTROL);
        graphics.outline(chip.x(), chip.y(), chip.width(), chip.height(), selected ? FullMapTheme.BORDER_SELECTED : FullMapTheme.BORDER);
        drawThemeBands(graphics, chip, colors);
        String text = SPSGui.scrollingText(font, label, Math.max(8, Math.round((chip.width() - 13) / FullMapTheme.TYPE_META)), seed);
        SPSGui.smallText(graphics, font, text, chip.x() + 8, chip.y() + Math.max(2, (chip.height() - 8) / 2), selected ? SPSGui.INFO : FullMapTheme.TEXT_SECONDARY, FullMapTheme.TYPE_META);
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
