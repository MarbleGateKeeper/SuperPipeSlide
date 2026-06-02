package dev.marblegate.superpipeslide.client.core.projection.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;

public final class ProjectionTextMeasureCache {
    private static final int MAX_WIDTH_ENTRIES = 8192;
    private static final Map<WidthKey, Integer> WIDTHS = new LinkedHashMap<>(512, 0.75F, true);
    private static final ThreadLocal<WidthLookupKey> WIDTH_LOOKUP = ThreadLocal.withInitial(WidthLookupKey::new);

    private ProjectionTextMeasureCache() {}

    public static int width(Font font, String text) {
        if (font == null || text == null || text.isEmpty()) {
            return 0;
        }
        int fontIdentity = System.identityHashCode(font);
        WidthLookupKey lookupKey = WIDTH_LOOKUP.get().set(fontIdentity, text);
        try {
            synchronized (WIDTHS) {
                Integer cached = WIDTHS.get(lookupKey);
                if (cached != null) {
                    return cached;
                }
            }
            int measured = font.width(text);
            synchronized (WIDTHS) {
                Integer cached = WIDTHS.get(lookupKey);
                if (cached != null) {
                    return cached;
                }
                WIDTHS.put(new WidthKey(fontIdentity, text), measured);
                trimLocked();
            }
            return measured;
        } finally {
            lookupKey.clear();
        }
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
        WIDTH_LOOKUP.remove();
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

    private static int widthHash(int fontIdentity, String text) {
        int result = Integer.hashCode(fontIdentity);
        result = 31 * result + text.hashCode();
        return result;
    }

    private static boolean widthEquals(int fontIdentity, String text, Object other) {
        if (other instanceof WidthKey key) {
            return fontIdentity == key.fontIdentity && text.equals(key.text);
        }
        if (other instanceof WidthLookupKey key) {
            return fontIdentity == key.fontIdentity && text.equals(key.text);
        }
        return false;
    }

    private static final class WidthLookupKey {
        private int fontIdentity;
        private String text = "";
        private int hash;

        private WidthLookupKey set(int fontIdentity, String text) {
            this.fontIdentity = fontIdentity;
            this.text = Objects.requireNonNullElse(text, "");
            this.hash = widthHash(this.fontIdentity, this.text);
            return this;
        }

        private void clear() {
            this.fontIdentity = 0;
            this.text = "";
            this.hash = 0;
        }

        @Override
        public boolean equals(Object other) {
            return widthEquals(this.fontIdentity, this.text, other);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }

    private static final class WidthKey {
        private final int fontIdentity;
        private final String text;
        private final int hash;

        private WidthKey(int fontIdentity, String text) {
            this.fontIdentity = fontIdentity;
            this.text = Objects.requireNonNullElse(text, "");
            this.hash = widthHash(this.fontIdentity, this.text);
        }

        @Override
        public boolean equals(Object other) {
            return widthEquals(this.fontIdentity, this.text, other);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }
}
