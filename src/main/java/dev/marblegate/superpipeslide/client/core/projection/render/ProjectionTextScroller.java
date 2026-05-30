package dev.marblegate.superpipeslide.client.core.projection.render;

import dev.marblegate.superpipeslide.client.core.projection.cache.ProjectionTextMeasureCache;
import net.minecraft.client.gui.Font;

public final class ProjectionTextScroller {
    private static final long START_PAUSE_MS = 650L;
    private static final long END_PAUSE_MS = 950L;
    private static final double PIXELS_PER_SECOND = 24.0D;

    private ProjectionTextScroller() {}

    public static float offset(Font font, String text, float viewportWidth, int seed) {
        return offset(font, text, viewportWidth, seed, System.currentTimeMillis());
    }

    public static float offset(Font font, String text, float viewportWidth, int seed, long frameTimeMillis) {
        String value = text == null ? "" : text;
        if (font == null || value.isEmpty()) {
            return 0.0F;
        }
        float maxOffset = ProjectionTextMeasureCache.width(font, value) - Math.max(1.0F, viewportWidth);
        if (maxOffset <= 0.0F) {
            return 0.0F;
        }

        long scrollMs = Math.max(900L, Math.round(maxOffset / PIXELS_PER_SECOND * 1000.0D));
        long cycleMs = START_PAUSE_MS + scrollMs + END_PAUSE_MS;
        long phase = Math.floorMod(frameTimeMillis + Math.floorMod(seed, 997) * 31L, cycleMs);
        if (phase < START_PAUSE_MS) {
            return 0.0F;
        }
        if (phase >= START_PAUSE_MS + scrollMs) {
            return maxOffset;
        }
        return maxOffset * ((phase - START_PAUSE_MS) / (float) scrollMs);
    }

    public static TextWindow window(Font font, String text, float viewportWidth, int seed) {
        String value = text == null ? "" : text;
        if (font == null || value.isEmpty()) {
            return new TextWindow("", 0.0F);
        }
        float safeViewport = Math.max(1.0F, viewportWidth);
        if (ProjectionTextMeasureCache.width(font, value) <= safeViewport) {
            return new TextWindow(value, 0.0F);
        }

        float offset = offset(font, value, safeViewport, seed);
        int start = 0;
        float consumed = 0.0F;
        while (start < value.length()) {
            int next = start + Character.charCount(value.codePointAt(start));
            float glyphWidth = ProjectionTextMeasureCache.width(font, value.substring(start, next));
            if (consumed + glyphWidth > offset + 0.001F) {
                break;
            }
            consumed += glyphWidth;
            start = next;
        }

        float leadingOffset = Math.max(0.0F, offset - consumed);
        int end = start;
        float width = 0.0F;
        while (end < value.length()) {
            int next = end + Character.charCount(value.codePointAt(end));
            float glyphWidth = ProjectionTextMeasureCache.width(font, value.substring(end, next));
            if (width > 0.0F && width + glyphWidth - leadingOffset > safeViewport + 0.001F) {
                break;
            }
            width += glyphWidth;
            end = next;
        }

        return new TextWindow(value.substring(start, end), leadingOffset);
    }

    public record TextWindow(String text, float leadingOffset) {}
}
