package dev.marblegate.superpipeslide.client.fullmap.schematic.model;

public record SchematicQualityReport(
        long solveTimeMillis,
        int iterationCount,
        int nodeOverlapCount,
        int edgeCrossingCount,
        int labelOverlapCount,
        double averageDisplacement,
        double maxDisplacement,
        int bendCount,
        int fallbackEdgeCount,
        int unresolvedCorridorCount,
        int loopGlyphCount,
        int stationInternalEdgeCount,
        boolean timeout,
        boolean usedPreviousLayout) {
    public static SchematicQualityReport fallback(long solveTimeMillis, int edgeCount) {
        return new SchematicQualityReport(solveTimeMillis, 0, 0, 0, 0, 0.0D, 0.0D, 0, edgeCount, 0, 0, 0, false, false);
    }
}
