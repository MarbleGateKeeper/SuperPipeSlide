package dev.marblegate.superpipeslide.client.core.projection.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;

public final class ProjectionTextMeasureCache {
    private static final int MAX_WIDTH_ENTRIES = 8192;
    private static final Map<WidthKey, Integer> WIDTHS = new LinkedHashMap<>(512, 0.75F, true);

    private ProjectionTextMeasureCache() {}

    public static int width(Font font, String text) {
        if (font == null || text == null || text.isEmpty()) {
            return 0;
        }
        WidthKey key = new WidthKey(System.identityHashCode(font), text);
        synchronized (WIDTHS) {
            Integer cached = WIDTHS.get(key);
            if (cached != null) {
                return cached;
            }
        }
        int measured = font.width(text);
        synchronized (WIDTHS) {
            WIDTHS.put(key, measured);
            trimLocked();
        }
        return measured;
    }

    public static int width(Font font, FormattedCharSequence text) {
        if (font == null || text == null) {
            return 0;
        }
        return font.width(text);
    }

    public static void clear() {
        synchronized (WIDTHS) {
            WIDTHS.clear();
        }
    }

    private static void trimLocked() {
        while (WIDTHS.size() > MAX_WIDTH_ENTRIES) {
            var iterator = WIDTHS.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
        }
    }

    private record WidthKey(int fontIdentity, String text) {
        private WidthKey {
            text = Objects.requireNonNullElse(text, "");
        }
    }
}
