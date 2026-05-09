package dev.marblegate.superpipeslide.client.fullmap.ui;

import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class FullMapTooltipCard {
    private static final int MIN_WIDTH = 132;
    private static final int MAX_WIDTH = 264;
    private static final int PADDING = 5;
    private static final int GAP = 2;

    private FullMapTooltipCard() {
    }

    public static void renderComponent(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect boundary, int anchorX, int anchorY, Component tooltip) {
        renderComponent(graphics, font, boundary, List.of(), anchorX, anchorY, tooltip);
    }

    public static void renderComponent(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect boundary, List<SPSGui.Rect> avoidRects, int anchorX, int anchorY, Component tooltip) {
        String value = tooltip.getString();
        if (value.isBlank()) {
            return;
        }
        String[] lines = value.split("\\R", -1);
        String title = lines.length == 0 ? value : lines[0];
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                rows.add(new Row("", lines[i], FullMapTheme.TEXT_SECONDARY));
            }
        }
        render(graphics, font, boundary, avoidRects, anchorX, anchorY, DisplayNameStack.of(title), "", rows, List.of(), FullMapTheme.BORDER_SELECTED);
    }

    public static void render(
            GuiGraphicsExtractor graphics,
            Font font,
            SPSGui.Rect boundary,
            int anchorX,
            int anchorY,
            String title,
            String subtitle,
            List<Row> rows,
            List<RouteChip> chips,
            int accentColor
    ) {
        render(graphics, font, boundary, List.of(), anchorX, anchorY, DisplayNameStack.of(title), subtitle, rows, chips, accentColor);
    }

    public static void render(
            GuiGraphicsExtractor graphics,
            Font font,
            SPSGui.Rect boundary,
            int anchorX,
            int anchorY,
            DisplayNameStack title,
            String subtitle,
            List<Row> rows,
            List<RouteChip> chips,
            int accentColor
    ) {
        render(graphics, font, boundary, List.of(), anchorX, anchorY, title, subtitle, rows, chips, accentColor);
    }

    public static void render(
            GuiGraphicsExtractor graphics,
            Font font,
            SPSGui.Rect boundary,
            List<SPSGui.Rect> avoidRects,
            int anchorX,
            int anchorY,
            String title,
            String subtitle,
            List<Row> rows,
            List<RouteChip> chips,
            int accentColor
    ) {
        render(graphics, font, boundary, avoidRects, anchorX, anchorY, DisplayNameStack.of(title), subtitle, rows, chips, accentColor);
    }

    public static void render(
            GuiGraphicsExtractor graphics,
            Font font,
            SPSGui.Rect boundary,
            List<SPSGui.Rect> avoidRects,
            int anchorX,
            int anchorY,
            DisplayNameStack title,
            String subtitle,
            List<Row> rows,
            List<RouteChip> chips,
            int accentColor
    ) {
        DisplayNameStack safeTitle = title == null ? DisplayNameStack.of("?") : title;
        String safeSubtitle = subtitle == null ? "" : subtitle;
        List<Row> safeRows = rows == null ? List.of() : rows;
        List<RouteChip> safeChips = chips == null ? List.of() : chips;

        int width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, FullMapUi.nameStackWidth(font, safeTitle, 1.0F, FullMapTheme.TYPE_META) + PADDING * 2 + 8));
        if (!safeSubtitle.isBlank()) {
            width = Math.max(width, Math.min(MAX_WIDTH, Math.round(font.width(safeSubtitle) * FullMapTheme.TYPE_META) + PADDING * 2 + 10));
        }
        for (Row row : safeRows) {
            int rowWidth = Math.round((font.width(row.label()) + font.width(row.value()) + 16) * FullMapTheme.TYPE_META) + PADDING * 2;
            width = Math.max(width, Math.min(MAX_WIDTH, rowWidth));
        }
        for (RouteChip chip : safeChips) {
            int chipWidth = Math.round(font.width(chip.label()) * FullMapTheme.TYPE_TINY) + 22;
            width = Math.max(width, Math.min(MAX_WIDTH, chipWidth + PADDING * 2));
        }
        width = Math.min(width, Math.max(MIN_WIDTH, boundary.width() - 12));

        int height = PADDING + FullMapUi.nameStackHeight(safeTitle, 1.0F, FullMapTheme.TYPE_META, 0);
        if (!safeSubtitle.isBlank()) {
            height += 9;
        }
        if (!safeRows.isEmpty()) {
            height += GAP + safeRows.size() * 10;
        }
        if (!safeChips.isEmpty()) {
            height += GAP + safeChips.size() * (FullMapTheme.ROUTE_CHIP_TINY_HEIGHT + 2);
        }
        height += PADDING - 1;

        SPSGui.Rect bounds = place(new SPSGui.Rect(anchorX + 9, anchorY + 9, width, Math.min(height, Math.max(34, boundary.height() - 10))), boundary, avoidRects);
        graphics.fill(bounds.x() + 2, bounds.y() + 3, bounds.right() + 2, bounds.bottom() + 3, FullMapTheme.SHADOW);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), FullMapTheme.SURFACE_CARD_ACTIVE);
        graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), FullMapTheme.BORDER);
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + 2, bounds.bottom(), accentColor);
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.y() + 2, 0xBFFFFFFF);

        graphics.enableScissor(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1);
        int x = bounds.x() + PADDING;
        int y = bounds.y() + PADDING - 1;
        FullMapUi.drawNameStack(graphics, font, safeTitle, x, y, bounds.width() - PADDING * 2 - 4, FullMapTheme.TEXT_PRIMARY, FullMapTheme.TEXT_MUTED, 1.0F, FullMapTheme.TYPE_META, 0);
        y += FullMapUi.nameStackHeight(safeTitle, 1.0F, FullMapTheme.TYPE_META, 0) + 1;
        if (!safeSubtitle.isBlank()) {
            SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, safeSubtitle, Math.round((bounds.width() - PADDING * 2) / FullMapTheme.TYPE_META)), x, y, FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_META);
            y += 9;
        }
        if (!safeRows.isEmpty()) {
            y += GAP;
            for (Row row : safeRows) {
                if (!row.label().isBlank()) {
                    SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, row.label(), 64), x, y, FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_META);
                    SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, row.value(), Math.round((bounds.width() - PADDING * 2 - 62) / FullMapTheme.TYPE_META)), x + 58, y, row.color(), FullMapTheme.TYPE_META);
                } else {
                    SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, row.value(), Math.round((bounds.width() - PADDING * 2) / FullMapTheme.TYPE_META)), x, y, row.color(), FullMapTheme.TYPE_META);
                }
                y += 10;
            }
        }
        if (!safeChips.isEmpty()) {
            y += GAP;
            for (RouteChip chip : safeChips) {
                SPSGui.Rect chipRect = new SPSGui.Rect(x, y, bounds.width() - PADDING * 2, FullMapTheme.ROUTE_CHIP_TINY_HEIGHT);
                FullMapUi.routeChip(graphics, font, chipRect, chip.label(), chip.colors(), false, false, chip.seed());
                y += FullMapTheme.ROUTE_CHIP_TINY_HEIGHT + 2;
            }
        }
        graphics.disableScissor();
    }

    public static SPSGui.Rect place(SPSGui.Rect preferred, SPSGui.Rect boundary) {
        return place(preferred, boundary, List.of());
    }

    public static SPSGui.Rect place(SPSGui.Rect preferred, SPSGui.Rect boundary, List<SPSGui.Rect> avoidRects) {
        int padding = 5;
        List<SPSGui.Rect> candidates = List.of(
                preferred,
                new SPSGui.Rect(preferred.x() - preferred.width() - 18, preferred.y(), preferred.width(), preferred.height()),
                new SPSGui.Rect(preferred.x(), preferred.y() - preferred.height() - 18, preferred.width(), preferred.height()),
                new SPSGui.Rect(preferred.x() - preferred.width() - 18, preferred.y() - preferred.height() - 18, preferred.width(), preferred.height()),
                new SPSGui.Rect(boundary.right() - preferred.width() - padding, preferred.y(), preferred.width(), preferred.height()),
                new SPSGui.Rect(boundary.x() + padding, preferred.y(), preferred.width(), preferred.height())
        );
        SPSGui.Rect best = null;
        long bestScore = Long.MAX_VALUE;
        for (SPSGui.Rect candidate : candidates) {
            SPSGui.Rect clamped = clamp(candidate, boundary, padding);
            long score = Math.abs(clamped.x() - preferred.x()) + Math.abs(clamped.y() - preferred.y());
            for (SPSGui.Rect avoid : avoidRects == null ? List.<SPSGui.Rect>of() : avoidRects) {
                score += (long) overlapArea(clamped, avoid) * 200L;
            }
            if (score < bestScore) {
                bestScore = score;
                best = clamped;
            }
        }
        return best == null ? clamp(preferred, boundary, padding) : best;
    }

    private static SPSGui.Rect clamp(SPSGui.Rect preferred, SPSGui.Rect boundary, int padding) {
        int x = preferred.x();
        int y = preferred.y();
        if (x + preferred.width() > boundary.right() - padding) {
            x = Math.max(boundary.x() + padding, preferred.x() - preferred.width() - 18);
        }
        if (y + preferred.height() > boundary.bottom() - padding) {
            y = Math.max(boundary.y() + padding, preferred.y() - preferred.height() - 18);
        }
        x = Math.max(boundary.x() + padding, Math.min(x, boundary.right() - preferred.width() - padding));
        y = Math.max(boundary.y() + padding, Math.min(y, boundary.bottom() - preferred.height() - padding));
        return new SPSGui.Rect(x, y, preferred.width(), preferred.height());
    }

    private static int overlapArea(SPSGui.Rect first, SPSGui.Rect second) {
        int x1 = Math.max(first.x(), second.x());
        int y1 = Math.max(first.y(), second.y());
        int x2 = Math.min(first.right(), second.right());
        int y2 = Math.min(first.bottom(), second.bottom());
        return Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
    }

    public record Row(String label, String value, int color) {
    }

    public record RouteChip(String label, List<Integer> colors, int seed) {
    }
}
