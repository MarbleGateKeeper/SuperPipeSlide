package dev.marblegate.superpipeslide.common.core.appearance.style;

import dev.marblegate.superpipeslide.common.core.appearance.storage.PipeAppearanceDefinitions;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public record PipeStyleGeometry(
        PipeStyleShape shape,
        double radius,
        double halfWidth,
        double halfHeight,
        double depth,
        double slideContactY,
        double topFlatness,
        double gauge,
        double railWidth,
        double railHeight,
        double tieInterval,
        double tieWidth,
        double rimWidth,
        double wallSlope,
        double floorRatio,
        double edgeWidth) {
    public static PipeStyleGeometry resolve(PipeStyleDefinition style, PipeVariantDefinition variant, Map<String, Double> parameters) {
        double size = variant.sizeMultiplier();
        return switch (style.shape()) {
            case BOX -> {
                double halfWidth = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_WIDTH) * 0.5D * size;
                double halfHeight = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_HEIGHT) * 0.5D * size;
                yield geometry(style.shape(), Math.max(halfWidth, halfHeight), halfWidth, halfHeight, halfHeight * 2.0D, halfHeight, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            case ROUND -> {
                double radius = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_RADIUS) * size;
                yield geometry(style.shape(), radius, radius, radius, radius * 2.0D, radius, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            case FACETED -> {
                double radius = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_RADIUS) * size;
                yield geometry(style.shape(), radius, radius, radius, radius * 2.0D, radius, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, radius * 0.16D);
            }
            case TRIANGLE -> {
                double halfWidth = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_WIDTH) * 0.5D * size;
                double depth = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_DEPTH) * size;
                double flatness = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_TOP_FLATNESS);
                yield geometry(style.shape(), Math.max(halfWidth, depth), halfWidth, depth * 0.5D, depth, 0.0D, flatness, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, halfWidth * 0.12D);
            }
            case RAIL -> {
                double gauge = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_GAUGE) * size;
                double railWidth = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_RAIL_WIDTH) * size;
                double railHeight = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_RAIL_HEIGHT) * size;
                double tieInterval = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_TIE_INTERVAL);
                double tieWidth = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_TIE_WIDTH) * size;
                double halfWidth = gauge * 0.5D + railWidth;
                yield geometry(style.shape(), halfWidth, halfWidth, railHeight, railHeight + tieWidth, 0.0D, 0.0D, gauge, railWidth, railHeight, tieInterval, tieWidth, 0.0D, 0.0D, 0.0D, railWidth * 0.5D);
            }
            case SLIDE -> {
                double halfWidth = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_WIDTH) * 0.5D * size;
                double depth = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_DEPTH) * size;
                double rimWidth = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_RIM_WIDTH) * size;
                double wallSlope = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_WALL_SLOPE);
                double floorRatio = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_FLOOR_RATIO);
                yield geometry(style.shape(), Math.max(halfWidth, depth), halfWidth, depth, depth, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, rimWidth, wallSlope, floorRatio, rimWidth);
            }
            case MONORAIL -> {
                double halfWidth = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_WIDTH) * 0.5D * size;
                double halfHeight = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_HEIGHT) * 0.5D * size;
                yield geometry(style.shape(), Math.max(halfWidth, halfHeight), halfWidth, halfHeight, halfHeight * 2.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            case COVERED -> {
                double halfWidth = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_WIDTH) * 0.5D * size;
                double halfHeight = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_HEIGHT) * size;
                double rimWidth = style.parameterValue(parameters, PipeAppearanceDefinitions.PARAM_RIM_WIDTH) * size;
                yield geometry(style.shape(), Math.max(halfWidth, halfHeight), halfWidth, halfHeight, halfHeight, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, rimWidth, 0.0D, 0.62D, rimWidth);
            }
        };
    }

    private static PipeStyleGeometry geometry(PipeStyleShape shape, double radius, double halfWidth, double halfHeight, double depth, double slideContactY, double topFlatness, double gauge, double railWidth, double railHeight, double tieInterval, double tieWidth, double rimWidth, double wallSlope, double floorRatio, double edgeWidth) {
        return new PipeStyleGeometry(shape, radius, halfWidth, halfHeight, depth, slideContactY, topFlatness, gauge, railWidth, railHeight, tieInterval, tieWidth, rimWidth, wallSlope, floorRatio, edgeWidth);
    }

    /**
     * How "vertical" a run is (0 = horizontal, 1 = vertical), shared by the renderer
     * (cross-section centering) and the slide pose (vertical pose blending) so both
     * always agree on where the transition happens.
     */
    public static double verticalness(Vec3 tangent) {
        if (tangent.lengthSqr() < 1.0E-6D) {
            return 0.0D;
        }
        double t = Mth.clamp((Math.abs(tangent.y) - 0.65D) / 0.30D, 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    /**
     * Local-Y midpoint of the rendered cross-section profile: where the slide line sits
     * relative to the profile when a vertical run centers the section on it.
     */
    public static double profileCenterShift(PipeSurfaceModel model) {
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (PipeSurfaceModel.LocalSurface surface : model.surfaces()) {
            if (!surface.render()) {
                continue;
            }
            minY = Math.min(minY, Math.min(surface.ay(), surface.by()));
            maxY = Math.max(maxY, Math.max(surface.ay(), surface.by()));
        }
        return minY <= maxY ? (minY + maxY) * 0.5D : 0.0D;
    }

    /**
     * Lateral half-extent of the rendered cross-section profile (the distance from the
     * slide line to the outer wall), used by the slide pose to place the rider's body and
     * feet against the real wall on vertical runs.
     */
    public static double profileHalfExtent(PipeSurfaceModel model) {
        double extent = 0.0D;
        for (PipeSurfaceModel.LocalSurface surface : model.surfaces()) {
            if (!surface.render()) {
                continue;
            }
            extent = Math.max(extent, Math.max(Math.abs(surface.ax()), Math.abs(surface.bx())));
        }
        return extent;
    }
}
