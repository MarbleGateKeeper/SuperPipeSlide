package dev.marblegate.superpipeslide.client.fullmap.schematic;

import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapLayoutMode;

public record SchematicLayoutConfig(
        FullRouteMapLayoutMode layoutMode,
        DirectionSetMode directionSetMode,
        long solverTimeoutMillis,
        int maxIterations,
        double geoWeight,
        double previousWeight,
        double nodeRepulsionWeight,
        double edgeLengthWeight,
        double edgeCrossingWeight,
        double edgeOverlapWeight,
        double bendWeight,
        double directionWeight,
        double maxDisplacementBlocks,
        double importantNodeDisplacementBlocks,
        double minReadableEdgePx,
        double maxVisualDetourRatio,
        double minNodeGapBlocks,
        double corridorOffsetBlocks,
        double maxCorridorOffsetBlocks,
        double maxStepBlocks,
        double minEdgeLengthBlocks,
        double maxEdgeLengthBlocks
) {
    public static SchematicLayoutConfig defaultConfig() {
        return forMode(FullRouteMapLayoutMode.PRACTICAL);
    }

    public static SchematicLayoutConfig forMode(FullRouteMapLayoutMode mode) {
        FullRouteMapLayoutMode normalized = mode == null || mode.physical() ? FullRouteMapLayoutMode.PRACTICAL : mode;
        return switch (normalized) {
            case PHYSICAL -> throw new IllegalArgumentException("Physical map mode does not use the schematic layout solver");
            case GEOGRAPHIC -> new SchematicLayoutConfig(
                    normalized,
                    DirectionSetMode.FREEFORM,
                    320L,
                    180,
                    0.034D,
                    0.028D,
                    0.72D,
                    0.022D,
                    0.68D,
                    0.72D,
                    0.48D,
                    0.008D,
                    48.0D,
                    24.0D,
                    20.0D,
                    1.12D,
                    28.0D,
                    6.0D,
                    18.0D,
                    2.2D,
                    26.0D,
                    260.0D
            );
            case PRACTICAL -> new SchematicLayoutConfig(
                    normalized,
                    DirectionSetMode.OCTILINEAR,
                    560L,
                    330,
                    0.010D,
                    0.016D,
                    1.42D,
                    0.036D,
                    2.85D,
                    3.25D,
                    0.58D,
                    0.034D,
                    144.0D,
                    68.0D,
                    20.0D,
                    1.46D,
                    48.0D,
                    13.0D,
                    42.0D,
                    4.1D,
                    52.0D,
                    220.0D
            );
            case SCHEMATIC -> new SchematicLayoutConfig(
                    normalized,
                    DirectionSetMode.OCTILINEAR,
                    550L,
                    340,
                    0.012D,
                    0.016D,
                    1.35D,
                    0.038D,
                    2.6D,
                    3.0D,
                    0.72D,
                    0.024D,
                    132.0D,
                    56.0D,
                    18.0D,
                    1.50D,
                    44.0D,
                    12.0D,
                    36.0D,
                    4.4D,
                    48.0D,
                    150.0D
            );
        };
    }

    public enum DirectionSetMode {
        FREEFORM,
        ORTHOGONAL,
        OCTILINEAR
    }
}
