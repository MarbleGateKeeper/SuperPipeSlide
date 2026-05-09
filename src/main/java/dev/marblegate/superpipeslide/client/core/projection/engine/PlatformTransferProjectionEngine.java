package dev.marblegate.superpipeslide.client.core.projection.engine;

import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponentSettings;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionOverflowMode;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionTextAlign;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlatformTransferProjectionEngine {
    private static final int FALLBACK_ROUTE_COLOR = 0xFF3366FF;
    private static final int LAYER_SURFACE = 10;
    private static final int LAYER_ACCENT = 20;
    private static final int LAYER_ICON = 30;
    private static final int LAYER_BADGE = 40;
    private static final int LAYER_TEXT = 100;

    private PlatformTransferProjectionEngine() {
    }

    public static Layout buildList(List<TransferData> transfers, ProjectionComponentSettings.PlatformTransferList settings, long timeMillis, int seed) {
        ProjectionComponentSettings.PlatformTransferList safe = settings == null ? ProjectionComponentSettings.PlatformTransferList.defaults() : settings;
        Window window = window(filter(transfers, safe.includeOutStation()), safe.maxVisible(), safe.overflow(), safe.rotateIntervalTicks(), seed, timeMillis);
        LayoutBuilder b = new LayoutBuilder();
        int count = window.items().size() + (window.hidden() > 0 ? 1 : 0);
        if (count <= 0) {
            return b.build();
        }
        boolean horizontal = safe.flow() == ProjectionComponentSettings.FlowDirection.HORIZONTAL;
        float gap = Math.max(0.0F, safe.gap());
        if (horizontal) {
            float[] widths = new float[count];
            float sum = 0.0F;
            for (int i = 0; i < window.items().size(); i++) {
                widths[i] = estimateListWidth(window.items().get(i), safe);
                sum += widths[i];
            }
            if (window.hidden() > 0) {
                widths[count - 1] = 0.150F;
                sum += widths[count - 1];
            }
            float available = Math.max(0.010F, 1.0F - gap * Math.max(0, count - 1));
            float scale = Math.min(1.0F, available / Math.max(0.010F, sum));
            float used = sum * scale + gap * Math.max(0, count - 1);
            float x = Math.max(0.0F, (1.0F - used) * 0.5F);
            for (int i = 0; i < window.items().size(); i++) {
                float w = widths[i] * scale;
                drawListCell(b, window.items().get(i), safe, x, 0.0F, w, 1.0F);
                x += w + gap;
            }
            if (window.hidden() > 0) {
                drawOverflowCell(b, x, 0.0F, widths[count - 1] * scale, 1.0F, window.hidden(), safe.fillColor(), safe.plusTextColor(), safe.fontSize());
            }
            return b.build();
        }
        float cellH = Math.max(0.010F, (1.0F - gap * Math.max(0, count - 1)) / count);
        float y = 0.0F;
        for (TransferData item : window.items()) {
            drawListCell(b, item, safe, 0.0F, y, 1.0F, cellH);
            y += cellH + gap;
        }
        if (window.hidden() > 0) {
            drawOverflowCell(b, 0.0F, y, 1.0F, cellH, window.hidden(), safe.fillColor(), safe.plusTextColor(), safe.fontSize());
        }
        return b.build();
    }

    public static Layout buildMatrix(List<TransferData> transfers, ProjectionComponentSettings.PlatformTransferMatrix settings, long timeMillis, int seed) {
        ProjectionComponentSettings.PlatformTransferMatrix safe = settings == null ? ProjectionComponentSettings.PlatformTransferMatrix.defaults() : settings;
        Window window = window(filter(transfers, safe.includeOutStation()), safe.maxVisible(), safe.overflow(), safe.rotateIntervalTicks(), seed, timeMillis);
        LayoutBuilder b = new LayoutBuilder();
        int count = window.items().size() + (window.hidden() > 0 ? 1 : 0);
        if (count <= 0) {
            return b.build();
        }
        int columns = Math.max(1, Math.min(safe.columns(), count));
        int rows = Math.max(1, (int) Math.ceil(count / (double) columns));
        float gap = Math.max(0.0F, safe.gap());
        float cellW = Math.max(0.010F, (1.0F - gap * Math.max(0, columns - 1)) / columns);
        float cellH = Math.max(0.010F, (1.0F - gap * Math.max(0, rows - 1)) / rows);
        int index = 0;
        for (TransferData item : window.items()) {
            int col = index % columns;
            int row = index / columns;
            drawMatrixCell(b, item, safe, col * (cellW + gap), row * (cellH + gap), cellW, cellH);
            index++;
        }
        if (window.hidden() > 0) {
            int col = index % columns;
            int row = index / columns;
            drawOverflowCell(b, col * (cellW + gap), row * (cellH + gap), cellW, cellH, window.hidden(), safe.fillColor(), safe.plusTextColor(), safe.fontSize());
        }
        return b.build();
    }

    private static void drawListCell(LayoutBuilder b, TransferData item, ProjectionComponentSettings.PlatformTransferList settings, float x, float y, float w, float h) {
        float pad = Math.min(w, h) * 0.080F;
        b.rect(x, y, w, h, settings.fillColor(), LAYER_SURFACE);
        float accent = Math.min(w * 0.120F, Math.max(0.010F, h * 0.180F));
        addColorStripe(b, x, y, accent, h, item.colors(), false);
        boolean showPlatform = settings.showPlatform() && !item.platform().isBlank();
        String secondary = secondaryLabel(item, settings.showStation());
        boolean showSecondary = !secondary.isBlank();
        float badgeW = showPlatform ? Math.min(w * 0.360F, Math.max(h * 0.720F, estimateTextWidth(item.platform()) * 0.012F + h * 0.260F)) : 0.0F;
        badgeW = Math.min(badgeW, w * 0.460F);
        float textX = x + accent + pad;
        float textW = Math.max(0.010F, w - (textX - x) - (showPlatform ? badgeW + pad : pad));
        if (showSecondary && h > 0.105F) {
            b.text(textX, y + h * 0.120F, textW, h * 0.480F, item.name(), settings.textColor(), settings.fontSize(), ProjectionTextAlign.LEFT, ProjectionOverflowMode.MARQUEE);
            b.text(textX, y + h * 0.570F, textW, h * 0.300F, secondary, withAlpha(settings.textColor(), 185), settings.fontSize() * 0.700F, ProjectionTextAlign.LEFT, ProjectionOverflowMode.MARQUEE);
        } else {
            b.text(textX, y, textW, h, routeLabel(item, settings.showStation()), settings.textColor(), settings.fontSize(), ProjectionTextAlign.LEFT, ProjectionOverflowMode.MARQUEE);
        }
        if (showPlatform) {
            float badgeH = Math.min(h * 0.620F, Math.max(0.030F, h - pad * 2.0F));
            addBadge(b, x + w - badgeW - pad, y + (h - badgeH) * 0.5F, badgeW, badgeH, item.platform(), settings.plusTextColor(), settings.fontSize() * 0.780F);
        }
    }

    private static void drawMatrixCell(LayoutBuilder b, TransferData item, ProjectionComponentSettings.PlatformTransferMatrix settings, float x, float y, float w, float h) {
        float pad = Math.min(w, h) * 0.080F;
        b.rect(x, y, w, h, settings.fillColor(), LAYER_SURFACE);
        float accent = Math.min(w * 0.100F, Math.max(0.008F, h * 0.160F));
        addColorStripe(b, x, y, accent, h, item.colors(), false);
        float iconSize = Math.min(Math.min(w, h) * 0.420F, 0.135F);
        float iconX = x + accent + pad + iconSize * 0.5F;
        float iconY = y + h * 0.500F;
        b.icon(iconX, iconY, iconSize, ProjectionComponentSettings.IconShape.CIRCLE, false, item.firstColor(), 0x00000000, 0.0F, 0.22F, LAYER_ICON);

        boolean showPlatform = settings.showPlatform() && !item.platform().isBlank();
        String secondary = secondaryLabel(item, settings.showStation());
        boolean showSecondary = !secondary.isBlank() && h > 0.120F;
        float contentTop = y + pad;
        float contentBottom = y + h - pad;
        float badgeH = showPlatform ? Math.min(h * 0.280F, 0.095F) : 0.0F;
        float badgeW = showPlatform ? Math.min(w * 0.400F, Math.max(0.120F, estimateTextWidth(item.platform()) * 0.012F + badgeH * 0.820F)) : 0.0F;
        float badgeX = x + w - badgeW - pad;
        float badgeY = contentBottom - badgeH;
        float textX = iconX + iconSize * 0.680F + pad;
        float textRight = x + w - pad;
        float textW = Math.max(0.010F, textRight - textX);
        if (!showSecondary) {
            b.text(textX, y + h * 0.155F, textW, h * 0.690F, item.name(), settings.textColor(), settings.fontSize(), ProjectionTextAlign.LEFT, ProjectionOverflowMode.MARQUEE);
        } else {
            float primaryH = Math.min(h * 0.430F, Math.max(0.032F, contentBottom - contentTop));
            float secondaryY = contentTop + primaryH;
            float secondaryH = Math.max(0.0F, contentBottom - secondaryY);
            float secondaryW = showPlatform ? Math.max(0.010F, badgeX - textX - pad * 0.500F) : textW;
            if (secondaryH < h * 0.185F) {
                secondaryW = showPlatform ? Math.max(0.010F, badgeX - textX - pad * 0.500F) : textW;
                secondaryH = Math.max(0.020F, h * 0.240F);
                secondaryY = y + h - pad - secondaryH;
                primaryH = Math.max(0.025F, secondaryY - contentTop);
            }
            b.text(textX, contentTop, textW, primaryH, item.name(), settings.textColor(), settings.fontSize(), ProjectionTextAlign.LEFT, ProjectionOverflowMode.MARQUEE);
            b.text(textX, secondaryY, secondaryW, secondaryH, secondary, withAlpha(settings.textColor(), 180), settings.fontSize() * 0.680F, ProjectionTextAlign.LEFT, ProjectionOverflowMode.MARQUEE);
        }
        if (showPlatform) {
            addBadge(b, badgeX, badgeY, badgeW, badgeH, item.platform(), settings.plusTextColor(), settings.fontSize() * 0.680F);
        }
    }

    private static void drawOverflowCell(LayoutBuilder b, float x, float y, float w, float h, int hidden, int fillColor, int textColor, float fontSize) {
        if ((fillColor >>> 24) > 0) {
            b.capsule(x, y, w, h, withAlpha(fillColor, Math.max(80, (fillColor >>> 24) & 0xFF)), LAYER_SURFACE);
        }
        b.text(x, y, w, h, "+" + hidden, textColor, fontSize, ProjectionTextAlign.CENTER, ProjectionOverflowMode.SCALE);
    }

    private static void addBadge(LayoutBuilder b, float x, float y, float w, float h, String label, int color, float fontSize) {
        if (w <= 0.002F || h <= 0.002F || label.isBlank()) {
            return;
        }
        b.capsule(x, y, w, h, color, LAYER_BADGE);
        b.text(x, y, w, h, label, contrast(color), fontSize, ProjectionTextAlign.CENTER, ProjectionOverflowMode.MARQUEE);
    }

    private static void addColorStripe(LayoutBuilder b, float x, float y, float w, float h, List<Integer> colors, boolean horizontal) {
        List<Integer> safe = colors == null || colors.isEmpty() ? List.of(FALLBACK_ROUTE_COLOR) : colors;
        if (safe.size() == 1) {
            b.rect(x, y, w, h, safe.getFirst(), LAYER_ACCENT);
            return;
        }
        for (int i = 0; i < safe.size(); i++) {
            if (horizontal) {
                float ww = w / safe.size();
                b.rect(x + ww * i, y, i == safe.size() - 1 ? x + w - (x + ww * i) : ww, h, safe.get(i), LAYER_ACCENT);
            } else {
                float hh = h / safe.size();
                b.rect(x, y + hh * i, w, i == safe.size() - 1 ? y + h - (y + hh * i) : hh, safe.get(i), LAYER_ACCENT);
            }
        }
    }

    private static Window window(List<TransferData> transfers, int maxVisible, ProjectionComponentSettings.RouteOverflowMode overflow, int intervalTicks, int seed, long timeMillis) {
        if (transfers == null || transfers.isEmpty()) {
            return new Window(List.of(), 0);
        }
        int visible = Math.max(1, Math.min(maxVisible, transfers.size()));
        if (overflow == ProjectionComponentSettings.RouteOverflowMode.ROTATE && transfers.size() > visible) {
            long intervalMs = Math.max(10L, intervalTicks) * 50L;
            int start = Math.floorMod((int) (timeMillis / intervalMs) + seed, transfers.size());
            List<TransferData> items = new ArrayList<>(visible);
            for (int i = 0; i < visible; i++) {
                items.add(transfers.get((start + i) % transfers.size()));
            }
            return new Window(List.copyOf(items), 0);
        }
        if (overflow == ProjectionComponentSettings.RouteOverflowMode.PLUS_COUNT && transfers.size() > visible) {
            int itemCount = Math.max(0, visible - 1);
            return new Window(transfers.subList(0, itemCount), transfers.size() - itemCount);
        }
        return new Window(transfers.subList(0, visible), 0);
    }

    private static List<TransferData> filter(List<TransferData> transfers, boolean includeOutStation) {
        if (transfers == null || transfers.isEmpty()) {
            return List.of();
        }
        if (includeOutStation) {
            return transfers;
        }
        return transfers.stream().filter(route -> !route.outStation()).toList();
    }

    private static float estimateListWidth(TransferData item, ProjectionComponentSettings.PlatformTransferList settings) {
        float width = 0.180F + Math.min(0.320F, estimateTextWidth(routeLabel(item, settings.showStation())) * 0.010F);
        if (settings.showPlatform() && !item.platform().isBlank()) {
            width += Math.min(0.250F, 0.080F + estimateTextWidth(item.platform()) * 0.008F);
        }
        return Math.max(0.180F, Math.min(0.720F, width));
    }

    private static int estimateTextWidth(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    private static String routeLabel(TransferData route, boolean showStation) {
        String secondary = secondaryLabel(route, showStation);
        if (!secondary.isBlank()) {
            return route.name() + " " + secondary;
        }
        return route.name();
    }

    private static String secondaryLabel(TransferData route, boolean showStation) {
        String translated = route.translatedName();
        String station = showStation && !route.station().isBlank() ? route.station() : "";
        if (!translated.isBlank() && !station.isBlank()) {
            return translated + " / " + station;
        }
        if (!translated.isBlank()) {
            return translated;
        }
        return station;
    }

    private static int contrast(int color) {
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        return r * 299 + g * 587 + b * 114 > 150000 ? 0xFF111820 : 0xFFFFFFFF;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    public record TransferData(UUID id, String name, String translatedName, String station, String platform, boolean outStation, List<Integer> colors) {
        public TransferData(UUID id, String name, String station, String platform, boolean outStation, List<Integer> colors) {
            this(id, name, "", station, platform, outStation, colors);
        }

        public TransferData {
            name = clean(name, "?");
            translatedName = clean(translatedName, "");
            station = clean(station, "");
            platform = clean(platform, "");
            colors = colors == null || colors.isEmpty() ? List.of(FALLBACK_ROUTE_COLOR) : List.copyOf(colors);
        }

        public int firstColor() {
            return this.colors.getFirst();
        }
    }

    public record Layout(List<Primitive> primitives) {
    }

    public sealed interface Primitive permits Rect, Capsule, Icon, Text {
        int layer();
    }

    public record Rect(float x, float y, float width, float height, int color, int layer) implements Primitive {
    }

    public record Capsule(float x, float y, float width, float height, int color, int layer) implements Primitive {
    }

    public record Icon(float centerX, float centerY, float size, ProjectionComponentSettings.IconShape shape, boolean outline, int fillColor, int borderColor, float borderWidth, float ringThicknessRatio, int layer) implements Primitive {
    }

    public record Text(float x, float y, float width, float height, String value, int color, float fontSize, ProjectionTextAlign align, ProjectionOverflowMode overflow, int layer) implements Primitive {
    }

    private record Window(List<TransferData> items, int hidden) {
    }

    private static final class LayoutBuilder {
        private final List<Primitive> primitives = new ArrayList<>();

        void rect(float x, float y, float width, float height, int color, int layer) {
            if ((color >>> 24) > 0 && width > 0.002F && height > 0.002F) {
                this.primitives.add(new Rect(x, y, width, height, color, layer));
            }
        }

        void capsule(float x, float y, float width, float height, int color, int layer) {
            if ((color >>> 24) > 0 && width > 0.002F && height > 0.002F) {
                this.primitives.add(new Capsule(x, y, width, height, color, layer));
            }
        }

        void icon(float centerX, float centerY, float size, ProjectionComponentSettings.IconShape shape, boolean outline, int fillColor, int borderColor, float borderWidth, float ringThicknessRatio, int layer) {
            if (size > 0.002F) {
                this.primitives.add(new Icon(centerX, centerY, size, shape, outline, fillColor, borderColor, borderWidth, ringThicknessRatio, layer));
            }
        }

        void text(float x, float y, float width, float height, String value, int color, float fontSize, ProjectionTextAlign align, ProjectionOverflowMode overflow) {
            if (value != null && !value.isBlank() && width > 0.002F && height > 0.002F) {
                this.primitives.add(new Text(x, y, width, height, value, color, fontSize, align, overflow, LAYER_TEXT));
            }
        }

        Layout build() {
            if (this.primitives.isEmpty()) {
                return new Layout(List.of());
            }
            ArrayList<Primitive> sorted = new ArrayList<>(this.primitives);
            sorted.sort(java.util.Comparator.comparingInt(Primitive::layer));
            return new Layout(List.copyOf(sorted));
        }
    }

    private static String clean(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value.trim();
        return result.length() <= 96 ? result : result.substring(0, 96);
    }
}
