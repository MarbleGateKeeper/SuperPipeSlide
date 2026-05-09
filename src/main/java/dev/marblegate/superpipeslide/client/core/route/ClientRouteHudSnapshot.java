package dev.marblegate.superpipeslide.client.core.route;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record ClientRouteHudSnapshot(
        UUID sessionId,
        UUID routeLineId,
        UUID routeLayoutId,
        int routeDirection,
        Status status,
        String routeName,
        List<String> routeTranslatedNames,
        String layoutName,
        String directionName,
        List<Integer> routeColors,
        List<Station> stations,
        UUID currentPlatformStopId,
        UUID focusPlatformStopId,
        int stationCount,
        double playerTravelIndex,
        double sectionProgress,
        StopPhase stopPhase,
        double stopDwellRemainingSeconds,
        double stopDwellProgress,
        Optional<NavigationStopContext> navigationStopContext,
        List<TransferLine> focusTransfers,
        boolean loop
) {
    public ClientRouteHudSnapshot {
        routeTranslatedNames = List.copyOf(routeTranslatedNames);
        routeColors = List.copyOf(routeColors);
        stations = List.copyOf(stations);
        focusTransfers = List.copyOf(focusTransfers);
        stationCount = Math.max(0, stationCount);
        playerTravelIndex = Math.max(-4096.0D, Math.min(4096.0D, playerTravelIndex));
        sectionProgress = Math.max(0.0D, Math.min(1.0D, sectionProgress));
        stopPhase = stopPhase == null ? StopPhase.MOVING : stopPhase;
        stopDwellRemainingSeconds = Math.max(0.0D, Math.min(999.0D, stopDwellRemainingSeconds));
        stopDwellProgress = Math.max(0.0D, Math.min(1.0D, stopDwellProgress));
        navigationStopContext = navigationStopContext == null ? Optional.empty() : navigationStopContext;
    }

    public enum Status {
        CRUISING,
        APPROACHING,
        ARRIVED,
        TERMINAL,
        BLOCKED,
        FOLD_TRANSIT
    }

    public enum StopPhase {
        MOVING,
        DOCKING,
        DEPARTING
    }

    public enum NavigationStopKind {
        DESTINATION,
        SAME_STATION_TRANSFER,
        OUT_OF_STATION_TRANSFER,
        CROSS_DIMENSION_TRANSFER,
        FINAL_WALK,
        CROSS_DIMENSION_FINAL_WALK
    }

    public record NavigationStopContext(
            NavigationStopKind kind,
            UUID platformStopId,
            UUID stationGroupId,
            List<Integer> colors
    ) {
        public NavigationStopContext(NavigationStopKind kind, UUID platformStopId, List<Integer> colors) {
            this(kind, platformStopId, platformStopId, colors);
        }

        public NavigationStopContext {
            stationGroupId = stationGroupId == null ? platformStopId : stationGroupId;
            colors = List.copyOf(colors);
        }
    }

    public record Station(
            UUID platformStopId,
            String primaryName,
            List<String> translatedNames,
            int relativeIndex,
            int travelIndex,
            int layoutIndex,
            boolean focus,
            boolean terminal
    ) {
        public Station {
            translatedNames = List.copyOf(translatedNames);
        }
    }

    public record TransferLine(UUID routeLineId, String name, List<String> translatedNames, List<Integer> colors) {
        public TransferLine {
            translatedNames = List.copyOf(translatedNames);
            colors = List.copyOf(colors);
        }
    }
}
