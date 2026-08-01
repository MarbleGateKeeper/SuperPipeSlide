package dev.marblegate.superpipeslide.client.fullmap.schematic.model;

/**
 * Quality metrics of one schematic layout solve. Counts are exact measurements over the layout
 * they describe: the metro backend re-measures the winning profile in full and ranks the other
 * attempts on grid-accelerated counts with identical semantics, while the heuristic backend
 * applies the same definitions to its own layout (at its own overlap threshold), so reports are
 * comparable across profiles, backends, and cache rebuilds.
 *
 * @param solveTimeMillis          wall-clock time of the whole solve call
 * @param iterationCount           layout improvement rounds executed by the solver. The heuristic backend
 *                                 reports its force-directed iteration count; the metro backend reports the
 *                                 sum of its rip-up-and-reroute passes (every profile) and, for the winning
 *                                 profile, the simulated-annealing probes executed. The metro attempt that
 *                                 produced the layout is identified by {@code profileName}
 * @param profileName              name of the layout profile that produced this layout; "heuristic" for the
 *                                 heuristic backend and "fallback" for fallback layouts
 * @param nodeOverlapCount         node pairs placed closer than the backend's overlap threshold,
 *                                 measured once. The metro backend uses the full minimum-distance threshold; any tighter
 *                                 threshold yields a strict subset of that count and is never summed in. The heuristic backend
 *                                 counts at 0.75x its minimum node distance
 * @param edgeCrossingCount        routed edge polyline crossings, excluding paths that share an endpoint
 * @param labelOverlapCount        placed labels whose boxes intersect
 * @param averageDisplacement      mean distance between schematic and world positions, in blocks
 * @param maxDisplacement          largest distance between schematic and world positions, in blocks
 * @param bendCount                corners over all routed edge polylines
 * @param lineTurnCount            route-run joints whose two incident edge directions leave the
 *                                 intermediate station at an angle sharper than about 60 degrees
 *                                 (turn amount above 0.5); the metro backend measures it exactly,
 *                                 the heuristic backend does not track it and reports 0
 * @param fallbackEdgeCount        edges drawn as straight fallback segments because routing failed
 * @param unresolvedCorridorCount  grouped parallel-corridor edges whose routed paths ended up
 *                                 closer than half their assigned lane step, as counted by the
 *                                 metro backend's corridor lane assignment; the heuristic backend
 *                                 does not track it and reports 0
 * @param edgeNodeConflictCount    routed edges passing within a node's clearance radius (this is
 *                                 what {@code unresolvedCorridorCount} used to be filled with)
 * @param loopGlyphCount           loop edges rendered as glyphs instead of routed paths
 * @param stationInternalEdgeCount intra-station edges collapsed into the station glyph
 * @param timeout                  true when the solver hit its wall-clock budget and returned a partial result
 * @param usedPreviousLayout       true when the solve warm-started from the previously cached layout
 *                                 (the heuristic backend seeds its node positions from it; the metro backend
 *                                 aligns its fresh embedding onto it when the two share at least one node)
 */
public record SchematicQualityReport(
        long solveTimeMillis,
        int iterationCount,
        String profileName,
        int nodeOverlapCount,
        int edgeCrossingCount,
        int labelOverlapCount,
        double averageDisplacement,
        double maxDisplacement,
        int bendCount,
        int lineTurnCount,
        int fallbackEdgeCount,
        int unresolvedCorridorCount,
        int edgeNodeConflictCount,
        int loopGlyphCount,
        int stationInternalEdgeCount,
        boolean timeout,
        boolean usedPreviousLayout) {
    public static SchematicQualityReport fallback(long solveTimeMillis, int edgeCount) {
        return new SchematicQualityReport(solveTimeMillis, 0, "fallback", 0, 0, 0, 0.0D, 0.0D, 0, 0, edgeCount, 0, 0, 0, 0, false, false);
    }
}
