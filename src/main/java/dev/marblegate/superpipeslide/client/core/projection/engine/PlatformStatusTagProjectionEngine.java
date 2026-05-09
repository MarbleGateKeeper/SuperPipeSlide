package dev.marblegate.superpipeslide.client.core.projection.engine;

import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponentSettings;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionOverflowMode;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionTextAlign;

import java.util.ArrayList;
import java.util.List;

public final class PlatformStatusTagProjectionEngine {
    private static final int LAYER_SURFACE = 10;
    private static final int LAYER_TEXT = 100;

    private PlatformStatusTagProjectionEngine() {
    }

    public static Layout build(List<String> tags, ProjectionComponentSettings.PlatformStatusTags settings) {
        return build(tags, settings, 1.0F, 1.0F);
    }

    public static Layout build(List<String> tags, ProjectionComponentSettings.PlatformStatusTags settings, float componentWidth, float componentHeight) {
        ProjectionComponentSettings.PlatformStatusTags safe = settings == null ? ProjectionComponentSettings.PlatformStatusTags.defaults() : settings;
        LayoutBuilder b = new LayoutBuilder();
        if (tags == null || tags.isEmpty()) {
            return b.build();
        }
        float canvasW = Math.max(0.001F, componentWidth);
        float canvasH = Math.max(0.001F, componentHeight);
        float targetH = Math.min(canvasH * 0.820F, Math.max(0.040F, safe.fontSize() * 1.720F));
        float tagH = Math.max(0.001F, targetH / canvasH);
        float y = (1.0F - tagH) * 0.5F;
        float gap = Math.max(0.000F, safe.gap()) / canvasW;
        List<Float> widths = new ArrayList<>(tags.size());
        float total = 0.0F;
        for (String tag : tags) {
            float targetW = Math.min(canvasW * 0.340F, Math.max(0.100F, safe.fontSize() * 1.350F + textUnits(tag) * safe.fontSize() * 0.380F));
            float w = targetW / canvasW;
            widths.add(w);
            total += w;
        }
        total += gap * Math.max(0, tags.size() - 1);
        float scale = Math.min(1.0F, 1.0F / Math.max(0.001F, total));
        float used = total * scale;
        float x = switch (safe.align()) {
            case LEFT -> 0.0F;
            case RIGHT -> 1.0F - used;
            case CENTER -> (1.0F - used) * 0.5F;
        };
        x = Math.max(0.0F, x);
        for (int i = 0; i < tags.size(); i++) {
            float w = widths.get(i) * scale;
            b.capsule(x, y, w, tagH, safe.fillColor(), LAYER_SURFACE);
            b.text(x, y, w, tagH, tags.get(i), safe.textColor(), safe.fontSize(), ProjectionTextAlign.CENTER, ProjectionOverflowMode.SCALE);
            x += w + gap * scale;
        }
        return b.build();
    }

    private static int textUnits(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    public record Layout(List<Primitive> primitives) {
    }

    public sealed interface Primitive permits Capsule, Text {
        int layer();
    }

    public record Capsule(float x, float y, float width, float height, int color, int layer) implements Primitive {
    }

    public record Text(float x, float y, float width, float height, String value, int color, float fontSize, ProjectionTextAlign align, ProjectionOverflowMode overflow, int layer) implements Primitive {
    }

    private static final class LayoutBuilder {
        private final List<Primitive> primitives = new ArrayList<>();

        void capsule(float x, float y, float width, float height, int color, int layer) {
            if ((color >>> 24) > 0 && width > 0.002F && height > 0.002F) {
                this.primitives.add(new Capsule(x, y, width, height, color, layer));
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
}
