package dev.marblegate.superpipeslide.client.fullmap.ui;

import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class FullMapTooltipCard {
    private static final int MIN_WIDTH = 132;
    private static final int MAX_WIDTH = 264;
    private static final int PADDING = 5;
    private static final int GAP = 2;
    // Unified label-column rule for label/value rows: the label column is as wide as the
    // widest label, capped at 64px (scaled pixels), with a fixed gap before the value.
    // Width measurement and rendering both derive from this, so they can never disagree.
    private static final int LABEL_COLUMN_MAX_WIDTH = 64;
    private static final int LABEL_VALUE_GAP = 6;
    // Placement hysteresis: the previous frame's placement stays put unless another
    // candidate scores better by more than this many pixels (stops boundary flip-flopping).
    private static final long PLACEMENT_HYSTERESIS_PX = 40L;
    // Height of the fade-out gradient drawn where overlong content is scissor-clipped.
    private static final int CLIP_FADE_HEIGHT = 12;
    // Opacity applied by the no-opacity overloads below. Card renderers call those
    // (they have no opacity parameter), so the map screen wraps their tooltip calls in
    // #withScopedOpacity to let renderer-drawn tooltips join the appearance fade-in.
    private static float scopedOpacity = 1.0F;

    private FullMapTooltipCard() {}

    /**
     * Runs {@code action} with the no-opacity overloads rendering at {@code opacity}
     * instead of full strength, restoring the previous value afterwards. Rendered
     * tooltips stay on the client thread, so the plain field is safe.
     */
    public static void withScopedOpacity(float opacity, Runnable action) {
        float previous = scopedOpacity;
        scopedOpacity = opacity;
        try {
            action.run();
        } finally {
            scopedOpacity = previous;
        }
    }

    public static SPSGui.Rect renderComponent(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect boundary, int anchorX, int anchorY, Component tooltip) {
        return renderComponent(graphics, font, boundary, List.of(), anchorX, anchorY, tooltip);
    }

    public static SPSGui.Rect renderComponent(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect boundary, List<SPSGui.Rect> avoidRects, int anchorX, int anchorY, Component tooltip) {
        return renderComponent(graphics, font, boundary, avoidRects, anchorX, anchorY, null, tooltip);
    }

    public static SPSGui.Rect renderComponent(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect boundary, int anchorX, int anchorY, @Nullable SPSGui.Rect previousPlacement, Component tooltip) {
        return renderComponent(graphics, font, boundary, List.of(), anchorX, anchorY, previousPlacement, tooltip);
    }

    public static SPSGui.Rect renderComponent(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect boundary, List<SPSGui.Rect> avoidRects, int anchorX, int anchorY, @Nullable SPSGui.Rect previousPlacement, Component tooltip) {
        return renderComponent(graphics, font, boundary, avoidRects, anchorX, anchorY, previousPlacement, tooltip, scopedOpacity);
    }

    public static SPSGui.Rect renderComponent(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect boundary, int anchorX, int anchorY, @Nullable SPSGui.Rect previousPlacement, Component tooltip, float opacity) {
        return renderComponent(graphics, font, boundary, List.of(), anchorX, anchorY, previousPlacement, tooltip, opacity);
    }

    public static SPSGui.Rect renderComponent(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect boundary, List<SPSGui.Rect> avoidRects, int anchorX, int anchorY, @Nullable SPSGui.Rect previousPlacement, Component tooltip, float opacity) {
        String value = tooltip.getString();
        if (value.isBlank()) {
            // Nothing rendered: keep the caller's sticky placement untouched.
            return previousPlacement;
        }
        String[] lines = value.split("\\R", -1);
        String title = lines.length == 0 ? value : lines[0];
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                rows.add(new Row("", lines[i], FullMapTheme.TEXT_SECONDARY));
            }
        }
        return render(graphics, font, boundary, avoidRects, anchorX, anchorY, previousPlacement, DisplayNameStack.of(title), "", rows, List.of(), FullMapTheme.BORDER_SELECTED, opacity);
    }

    public static SPSGui.Rect render(
            GuiGraphicsExtractor graphics,
            Font font,
            SPSGui.Rect boundary,
            int anchorX,
            int anchorY,
            String title,
            String subtitle,
            List<Row> rows,
            List<RouteChip> chips,
            int accentColor) {
        return render(graphics, font, boundary, List.of(), anchorX, anchorY, DisplayNameStack.of(title), subtitle, rows, chips, accentColor);
    }

    public static SPSGui.Rect render(
            GuiGraphicsExtractor graphics,
            Font font,
            SPSGui.Rect boundary,
            int anchorX,
            int anchorY,
            DisplayNameStack title,
            String subtitle,
            List<Row> rows,
            List<RouteChip> chips,
            int accentColor) {
        return render(graphics, font, boundary, List.of(), anchorX, anchorY, title, subtitle, rows, chips, accentColor);
    }

    public static SPSGui.Rect render(
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
            int accentColor) {
        return render(graphics, font, boundary, avoidRects, anchorX, anchorY, DisplayNameStack.of(title), subtitle, rows, chips, accentColor);
    }

    public static SPSGui.Rect render(
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
            int accentColor) {
        return render(graphics, font, boundary, avoidRects, anchorX, anchorY, null, title, subtitle, rows, chips, accentColor);
    }

    /**
     * Renders the card and returns the bounds it was placed at, so the caller can feed
     * them back as {@code previousPlacement} next frame (placement hysteresis, see
     * {@link #place(SPSGui.Rect, SPSGui.Rect, List, SPSGui.Rect)}).
     */
    public static SPSGui.Rect render(
            GuiGraphicsExtractor graphics,
            Font font,
            SPSGui.Rect boundary,
            List<SPSGui.Rect> avoidRects,
            int anchorX,
            int anchorY,
            @Nullable SPSGui.Rect previousPlacement,
            DisplayNameStack title,
            String subtitle,
            List<Row> rows,
            List<RouteChip> chips,
            int accentColor) {
        return render(graphics, font, boundary, avoidRects, anchorX, anchorY, previousPlacement, title, subtitle, rows, chips, accentColor, scopedOpacity);
    }

    /**
     * Opacity-taking variant of {@link #render(GuiGraphicsExtractor, Font, SPSGui.Rect, List, int, int, SPSGui.Rect, DisplayNameStack, String, List, List, int)}:
     * every color drawn here gets its alpha channel scaled by {@code opacity} (0..1),
     * which the map screen uses for a short fade-in when the tooltip appears. Route
     * chips are exempt: {@link FullMapUi#routeChip} derives opaque theme colors
     * internally, so they cannot be faded from here.
     */
    public static SPSGui.Rect render(
            GuiGraphicsExtractor graphics,
            Font font,
            SPSGui.Rect boundary,
            List<SPSGui.Rect> avoidRects,
            int anchorX,
            int anchorY,
            @Nullable SPSGui.Rect previousPlacement,
            DisplayNameStack title,
            String subtitle,
            List<Row> rows,
            List<RouteChip> chips,
            int accentColor,
            float opacity) {
        DisplayNameStack safeTitle = title == null ? DisplayNameStack.of("?") : title;
        String safeSubtitle = subtitle == null ? "" : subtitle;
        List<Row> safeRows = rows == null ? List.of() : rows;
        List<RouteChip> safeChips = chips == null ? List.of() : chips;

        int labelColumnWidth = 0;
        for (Row row : safeRows) {
            if (!row.label().isBlank()) {
                labelColumnWidth = Math.max(labelColumnWidth, Math.round(font.width(row.label()) * FullMapTheme.TYPE_META));
            }
        }
        labelColumnWidth = Math.min(LABEL_COLUMN_MAX_WIDTH, labelColumnWidth);

        int width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, FullMapUi.nameStackWidth(font, safeTitle, 1.0F, FullMapTheme.TYPE_META) + PADDING * 2 + 8));
        if (!safeSubtitle.isBlank()) {
            width = Math.max(width, Math.min(MAX_WIDTH, Math.round(font.width(safeSubtitle) * FullMapTheme.TYPE_META) + PADDING * 2 + 10));
        }
        for (Row row : safeRows) {
            int contentWidth = row.label().isBlank()
                    ? Math.round(font.width(row.value()) * FullMapTheme.TYPE_META)
                    : Math.round(labelColumnWidth + LABEL_VALUE_GAP + font.width(row.value()) * FullMapTheme.TYPE_META);
            width = Math.max(width, Math.min(MAX_WIDTH, contentWidth + PADDING * 2));
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

        SPSGui.Rect bounds = place(new SPSGui.Rect(anchorX + 9, anchorY + 9, width, Math.min(height, Math.max(34, boundary.height() - 10))), boundary, avoidRects, previousPlacement);
        boolean clipped = height > bounds.height();
        graphics.fill(bounds.x() + 2, bounds.y() + 3, bounds.right() + 2, bounds.bottom() + 3, fade(FullMapTheme.SHADOW, opacity));
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), fade(FullMapTheme.SURFACE_CARD_ACTIVE, opacity));
        graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), fade(FullMapTheme.BORDER, opacity));
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + 2, bounds.bottom(), fade(accentColor, opacity));
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.y() + 2, fade(FullMapTheme.INNER_HIGHLIGHT_STRONG, opacity));

        graphics.enableScissor(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1);
        int x = bounds.x() + PADDING;
        int y = bounds.y() + PADDING - 1;
        FullMapUi.drawNameStack(graphics, font, safeTitle, x, y, bounds.width() - PADDING * 2 - 4, fade(FullMapTheme.TEXT_PRIMARY, opacity), fade(FullMapTheme.TEXT_MUTED, opacity), 1.0F, FullMapTheme.TYPE_META, 0);
        y += FullMapUi.nameStackHeight(safeTitle, 1.0F, FullMapTheme.TYPE_META, 0) + 1;
        if (!safeSubtitle.isBlank()) {
            SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, safeSubtitle, Math.round((bounds.width() - PADDING * 2) / FullMapTheme.TYPE_META)), x, y, fade(FullMapTheme.TEXT_MUTED, opacity), FullMapTheme.TYPE_META);
            y += 9;
        }
        int hiddenEntries = 0;
        if (!safeRows.isEmpty()) {
            y += GAP;
            for (Row row : safeRows) {
                if (y + 10 > bounds.bottom() - 1) {
                    hiddenEntries++;
                }
                if (!row.label().isBlank()) {
                    SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, row.label(), Math.round(labelColumnWidth / FullMapTheme.TYPE_META)), x, y, fade(FullMapTheme.TEXT_MUTED, opacity), FullMapTheme.TYPE_META);
                    SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, row.value(), Math.round((bounds.width() - PADDING * 2 - labelColumnWidth - LABEL_VALUE_GAP) / FullMapTheme.TYPE_META)), x + labelColumnWidth + LABEL_VALUE_GAP, y, fade(row.color(), opacity), FullMapTheme.TYPE_META);
                } else {
                    SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, row.value(), Math.round((bounds.width() - PADDING * 2) / FullMapTheme.TYPE_META)), x, y, fade(row.color(), opacity), FullMapTheme.TYPE_META);
                }
                y += 10;
            }
        }
        if (!safeChips.isEmpty()) {
            y += GAP;
            for (RouteChip chip : safeChips) {
                if (y + FullMapTheme.ROUTE_CHIP_TINY_HEIGHT > bounds.bottom() - 1) {
                    hiddenEntries++;
                }
                SPSGui.Rect chipRect = new SPSGui.Rect(x, y, bounds.width() - PADDING * 2, FullMapTheme.ROUTE_CHIP_TINY_HEIGHT);
                FullMapUi.routeChip(graphics, font, chipRect, chip.label(), chip.colors(), false, false, chip.seed());
                y += FullMapTheme.ROUTE_CHIP_TINY_HEIGHT + 2;
            }
        }
        graphics.disableScissor();
        if (clipped && hiddenEntries > 0) {
            // The scissor above silently cut off content: fade the cut edge out and say
            // how many rows/chips are hidden.
            int fadeHeight = Math.min(CLIP_FADE_HEIGHT, Math.max(0, bounds.height() - 4));
            for (int i = 0; i < fadeHeight; i++) {
                int alpha = Math.round(0xFA * (i + 1) / (float) fadeHeight * opacity);
                graphics.fill(bounds.x() + 1, bounds.bottom() - 1 - fadeHeight + i, bounds.right() - 1, bounds.bottom() - fadeHeight + i, SPSGui.withAlpha(FullMapTheme.SURFACE_CARD_ACTIVE, alpha));
            }
            String more = Component.literal("+" + hiddenEntries + " more").getString();
            SPSGui.smallText(graphics, font, more, bounds.x() + PADDING, bounds.bottom() - 9, fade(FullMapTheme.TEXT_MUTED, opacity), FullMapTheme.TYPE_TINY);
        }
        return bounds;
    }

    /**
     * Scales a color's own alpha channel by {@code opacity} (0..1). Route chips drawn
     * through {@link FullMapUi#routeChip} cannot go through this (it resolves opaque
     * theme colors internally), which is why chips stay at full strength during the
     * fade-in.
     */
    private static int fade(int color, float opacity) {
        if (opacity >= 1.0F) {
            return color;
        }
        return SPSGui.withAlpha(color, Math.round((color >>> 24) * opacity));
    }

    public static SPSGui.Rect place(SPSGui.Rect preferred, SPSGui.Rect boundary) {
        return place(preferred, boundary, List.of());
    }

    public static SPSGui.Rect place(SPSGui.Rect preferred, SPSGui.Rect boundary, List<SPSGui.Rect> avoidRects) {
        return place(preferred, boundary, avoidRects, null);
    }

    /**
     * Picks the best-scoring clamped candidate. When {@code previousPlacement} is given,
     * its position (re-sized to the current content and re-clamped) acts as a sticky
     * candidate: it wins unless the best candidate scores better by more than
     * {@link #PLACEMENT_HYSTERESIS_PX}, so the card stops flip-flopping between two sides
     * of the cursor near a boundary.
     */
    public static SPSGui.Rect place(SPSGui.Rect preferred, SPSGui.Rect boundary, List<SPSGui.Rect> avoidRects, @Nullable SPSGui.Rect previousPlacement) {
        int padding = 5;
        List<SPSGui.Rect> candidates = List.of(
                preferred,
                new SPSGui.Rect(preferred.x() - preferred.width() - 18, preferred.y(), preferred.width(), preferred.height()),
                new SPSGui.Rect(preferred.x(), preferred.y() - preferred.height() - 18, preferred.width(), preferred.height()),
                new SPSGui.Rect(preferred.x() - preferred.width() - 18, preferred.y() - preferred.height() - 18, preferred.width(), preferred.height()),
                new SPSGui.Rect(boundary.right() - preferred.width() - padding, preferred.y(), preferred.width(), preferred.height()),
                new SPSGui.Rect(boundary.x() + padding, preferred.y(), preferred.width(), preferred.height()));
        SPSGui.Rect best = null;
        long bestScore = Long.MAX_VALUE;
        for (SPSGui.Rect candidate : candidates) {
            SPSGui.Rect clamped = clamp(candidate, boundary, padding);
            long score = placementScore(clamped, preferred, avoidRects);
            if (score < bestScore) {
                bestScore = score;
                best = clamped;
            }
        }
        if (best == null) {
            return clamp(preferred, boundary, padding);
        }
        if (previousPlacement != null) {
            SPSGui.Rect sticky = clamp(new SPSGui.Rect(previousPlacement.x(), previousPlacement.y(), preferred.width(), preferred.height()), boundary, padding);
            long stickyScore = placementScore(sticky, preferred, avoidRects);
            if (stickyScore <= bestScore + PLACEMENT_HYSTERESIS_PX) {
                return sticky;
            }
        }
        return best;
    }

    private static long placementScore(SPSGui.Rect clamped, SPSGui.Rect preferred, List<SPSGui.Rect> avoidRects) {
        long score = Math.abs(clamped.x() - preferred.x()) + Math.abs(clamped.y() - preferred.y());
        for (SPSGui.Rect avoid : avoidRects == null ? List.<SPSGui.Rect>of() : avoidRects) {
            score += (long) overlapArea(clamped, avoid) * 200L;
        }
        return score;
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

    public record Row(String label, String value, int color) {}

    public record RouteChip(String label, List<Integer> colors, int seed) {}
}
