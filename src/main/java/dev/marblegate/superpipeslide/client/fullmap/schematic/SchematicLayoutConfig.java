package dev.marblegate.superpipeslide.client.fullmap.schematic;

import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapLayoutMode;
import java.util.List;

/**
 * Shared knobs for the schematic layout pipeline. Not every field reaches every backend:
 * the force-directed heuristic solver consumes the weights, thresholds, and iteration
 * budget, while the metro solver (pure SCHEMATIC mode) honours only
 * {@code solverTimeoutMillis} and {@code metroProfiles} -- its remaining geometry derives
 * from the active profile. {@code solverTimeoutMillis} is a wall-clock budget honoured by
 * both backends: the heuristic solver stops iterating once it is spent, and the metro
 * solver stops trying further layout profiles and routes any remaining edges as direct
 * degraded connections.
 *
 * @param layoutMode                      map mode this config was built for. Selects the input shaping in
 *                                        {@link SchematicInputBuilder} and, inside the heuristic solver, the dispatch to the
 *                                        metro backend for SCHEMATIC.
 * @param directionSetMode                allowed edge directions for the heuristic backend's direction
 *                                        forces and octilinear candidate routing. The metro backend is inherently octilinear and
 *                                        ignores this.
 * @param solverTimeoutMillis             wall-clock budget in milliseconds, honoured by both backends
 *                                        as described above.
 * @param maxIterations                   iteration cap of the heuristic backend's force relaxation. The metro
 *                                        backend ignores it; its own effort is bounded by the profile count and the wall-clock
 *                                        budget.
 * @param geoWeight                       heuristic-only force weight pulling a node toward its world position.
 * @param previousWeight                  heuristic-only force weight pulling a node toward its position in
 *                                        the previously cached layout.
 * @param nodeRepulsionWeight             heuristic-only force weight separating nodes that violate
 *                                        their minimum distance.
 * @param edgeLengthWeight                heuristic-only spring weight pulling edges toward their desired
 *                                        length.
 * @param edgeCrossingWeight              heuristic-only routing penalty per crossing with an already
 *                                        routed edge.
 * @param edgeOverlapWeight               heuristic-only routing penalty for running near-parallel on top
 *                                        of an already routed edge.
 * @param bendWeight                      heuristic-only routing penalty per bend in a candidate path.
 * @param directionWeight                 heuristic-only force weight aligning edges with the configured
 *                                        direction set.
 * @param maxDisplacementBlocks           baked into per-node displacement clamps by
 *                                        {@link SchematicInputBuilder}; only the heuristic backend enforces the clamp.
 * @param importantNodeDisplacementBlocks same as {@code maxDisplacementBlocks}, applied to
 *                                        transfer stations, clusters, and fold anchors.
 * @param minReadableEdgePx               heuristic-only on-screen length below which an edge skips
 *                                        corridor grouping and is always routed as a direct connection.
 * @param maxVisualDetourRatio            heuristic-only cap on routed path length relative to the
 *                                        direct connection; longer candidates are discarded.
 * @param minNodeGapBlocks                heuristic-only minimum free gap kept between node obstacles.
 * @param corridorOffsetBlocks            heuristic-only lane spacing between parallel corridor edges.
 * @param maxCorridorOffsetBlocks         heuristic-only cap on the outermost corridor lane offset.
 * @param maxStepBlocks                   heuristic-only per-iteration movement cap of the force relaxation.
 * @param minEdgeLengthBlocks             heuristic-only lower clamp of the desired edge length.
 * @param maxEdgeLengthBlocks             heuristic-only upper clamp of the desired edge length.
 * @param metroProfiles                   candidate layout profiles the metro backend tries in order; the
 *                                        first defect-free profile wins, otherwise the best-scoring one. A null or empty list
 *                                        restores {@link #defaultMetroProfiles()}. The heuristic backend ignores this.
 */
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
        double maxEdgeLengthBlocks,
        List<MetroProfile> metroProfiles) {
    public SchematicLayoutConfig {
        metroProfiles = metroProfiles == null || metroProfiles.isEmpty() ? defaultMetroProfiles() : List.copyOf(metroProfiles);
    }

    /**
     * One metro layout profile: the station grid spacing the profile embeds nodes on and
     * the target aspect ratio the resulting layout is scored against. List order is the
     * try order.
     */
    public record MetroProfile(String name, double stationSpacing, double targetAspect) {}

    /**
     * The profile sequence a metro solve falls back to when the config carries no explicit
     * list. These are the historical built-in profiles of the metro backend.
     */
    public static List<MetroProfile> defaultMetroProfiles() {
        return List.of(
                new MetroProfile("balanced", 118.0D, 1.42D),
                new MetroProfile("wide", 112.0D, 1.72D),
                new MetroProfile("compact", 104.0D, 1.36D),
                new MetroProfile("fallback", 96.0D, 1.58D));
    }

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
                    260.0D,
                    defaultMetroProfiles());
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
                    220.0D,
                    defaultMetroProfiles());
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
                    150.0D,
                    defaultMetroProfiles());
        };
    }

    public enum DirectionSetMode {
        FREEFORM,
        OCTILINEAR
    }
}
