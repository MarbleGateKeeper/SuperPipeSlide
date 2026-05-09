package dev.marblegate.superpipeslide.client.fullmap.routecard.model;

public record RouteCardSummary(
        int stationCount,
        int occurrenceCount,
        int sectionCount,
        int crossDimensionCount,
        int stationInternalCount,
        boolean bidirectional,
        boolean loop
) {
}
