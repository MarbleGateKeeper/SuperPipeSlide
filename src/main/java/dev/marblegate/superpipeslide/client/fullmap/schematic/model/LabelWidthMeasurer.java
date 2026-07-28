package dev.marblegate.superpipeslide.client.fullmap.schematic.model;

/**
 * Measures the width a station label occupies in schematic layout space, in the same units the
 * solvers use for node positions. The production implementation is backed by
 * {@code Minecraft.getInstance().font} and injected per build by the full route map cache, so
 * CJK station names (roughly twice as wide as Latin glyphs under the Minecraft font) get boxes
 * that match what the renderer will actually draw. The solvers must never touch the font
 * themselves; they only see this function.
 */
@FunctionalInterface
public interface LabelWidthMeasurer {
    /** Shared bounds for label box widths, applied identically at placement and at overlap measurement. */
    double MIN_WIDTH = 24.0D;
    double MAX_WIDTH = 160.0D;

    /**
     * Returns the width of {@code text} rendered at the label's {@code scale}
     * (see {@code VisualLabel.scale}), in layout units.
     */
    double width(String text, double scale);

    /** Clamps a raw label width into the shared [{@link #MIN_WIDTH}, {@link #MAX_WIDTH}] range. */
    static double clampWidth(double width) {
        return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width));
    }

    /**
     * Fallback used when no real font measurement is available (e.g. solver runs without an
     * injected measurer). Preserves the previous estimate of 4.8 layout units per character at
     * the typical 0.70 label scale, expressed as 6.8 unscaled font pixels per Latin glyph so it
     * stays proportional to the requested scale. It still underestimates CJK text, which is why
     * the cache always injects the font-backed measurer.
     */
    static LabelWidthMeasurer latinEstimate() {
        return (text, scale) -> text.length() * 6.8D * scale;
    }
}
