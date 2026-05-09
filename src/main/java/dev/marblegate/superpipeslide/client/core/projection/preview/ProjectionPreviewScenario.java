package dev.marblegate.superpipeslide.client.core.projection.preview;


import dev.marblegate.superpipeslide.client.core.projection.engine.PlatformLayoutProjectionEngine;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;

import java.util.ArrayList;
import java.util.List;

public record ProjectionPreviewScenario(
        String primaryName,
        String translationName,
        boolean showTranslation,
        int routeCount,
        RoutePalette routePalette,
        boolean showExit,
        String exitLabel,
        boolean longNames,
        boolean showTransfers,
        boolean platformTerminal,
        boolean platformBidirectional,
        boolean platformLoop,
        boolean outStationTransfers,
        int platformStopCount
) {
    public ProjectionPreviewScenario {
        primaryName = normalized(primaryName, "Block Plaza");
        translationName = normalized(translationName, "Block Plaza");
        routeCount = Math.max(0, Math.min(8, routeCount));
        routePalette = routePalette == null ? RoutePalette.METRO : routePalette;
        exitLabel = normalized(exitLabel, "A3");
        platformStopCount = Math.max(3, Math.min(64, platformStopCount));
    }

    public static ProjectionPreviewScenario standard() {
        return new ProjectionPreviewScenario("Block Plaza", "Block Plaza", true, 4, RoutePalette.METRO, true, "A3", false, true, true, true, false, true, 12);
    }

    public String renderedPrimaryName() {
        return this.longNames ? "Central Block Plaza Transportation Hub North" : this.primaryName;
    }

    public String renderedTranslationName() {
        return this.showTranslation ? (this.longNames ? "Central Block Plaza Transportation Hub North" : this.translationName) : "";
    }

    public String renderedExitLabel() {
        return this.showExit ? this.exitLabel : "";
    }

    public List<RoutePreview> routes() {
        List<RoutePreview> routes = new ArrayList<>(this.routeCount);
        for (int i = 0; i < this.routeCount; i++) {
            String name = this.longNames ? "Sample Loop Line " + (i + 1) + " Extended Service" : switch (i) {
                case 0 -> "Redstone";
                case 1 -> "Gold";
                case 2 -> "End Loop";
                case 3 -> "Prismarine";
                default -> "Line " + (i + 1);
            };
            routes.add(new RoutePreview(name, this.routePalette.colorsFor(i)));
        }
        return List.copyOf(routes);
    }

    public String renderedPlatformName() {
        return this.longNames ? "Northbound Express Platform 12" : "Platform 1";
    }

    public String renderedPlatformNumber() {
        return this.longNames ? "12" : "1";
    }

    public String renderedLineName() {
        return this.routes().isEmpty() ? "Redstone" : this.routes().getFirst().name();
    }

    public RoutePreview lineRoute() {
        return this.routes().isEmpty() ? new RoutePreview("Redstone", this.routePalette.colorsFor(0)) : this.routes().getFirst();
    }

    public String renderedPreviousStop() {
        return this.longNames ? "Old Copper Factory Interchange" : "Copper Yard";
    }

    public String renderedOriginStop() {
        return this.longNames ? "Quartz Harbor Transit Center" : "Quartz";
    }

    public String renderedNextStop() {
        return this.longNames ? "Central Block Plaza Transportation Hub North" : "Block Plaza";
    }

    public String renderedTerminalStop() {
        return this.longNames ? "End Gateway International Terminal" : "End Gateway";
    }

    public PlatformLayoutProjectionEngine.Data platformLayoutData() {
        int stopCount = this.platformStopCount;
        int current = Math.max(1, Math.min(stopCount - 2, stopCount / 2));
        PlatformLayoutProjectionEngine.RouteData route = new PlatformLayoutProjectionEngine.RouteData(routeName(0), this.routePalette.colorsFor(0));
        List<PlatformLayoutProjectionEngine.StopData> stops = new ArrayList<>();
        for (int i = 0; i < stopCount; i++) {
            boolean currentStop = i == current;
            boolean terminal = i == 0 || i == stopCount - 1;
            List<PlatformLayoutProjectionEngine.TransferData> transfers = layoutTransfers(i);
            double curve = Math.sin(i * 0.72D) * 18.0D + Math.cos(i * 0.28D) * 7.0D;
            RouteSectionStatus incoming = previewIncomingStatus(i, stopCount);
            boolean missing = false;
            stops.add(new PlatformLayoutProjectionEngine.StopData(stopName(i, 0), "", Integer.toString(i % 4 + 1), currentStop, terminal, !transfers.isEmpty(), transfers, incoming, missing, i * 36.0D, curve));
        }
        RouteSectionStatus loopStatus = RouteSectionStatus.VALID;
        return new PlatformLayoutProjectionEngine.Data(layoutName(0), route, stops, current, this.platformBidirectional, this.platformLoop, loopStatus, false);
    }

    private static RouteSectionStatus previewIncomingStatus(int index, int stopCount) {
        if (index <= 0) {
            return RouteSectionStatus.VALID;
        }
        return RouteSectionStatus.VALID;
    }

    public List<RoutePreview> transferRoutes() {
        if (!this.showTransfers) {
            return List.of();
        }
        List<RoutePreview> routes = this.routes();
        if (routes.size() <= 1) {
            return List.of();
        }
        List<RoutePreview> transfers = new ArrayList<>();
        for (int i = 1; i < routes.size(); i++) {
            RoutePreview route = routes.get(i);
            boolean outStation = this.outStationTransfers && i % 2 == 0;
            String station = outStation ? "Market" : "";
            String platform = switch (i % 4) {
                case 0 -> "East Transfer Deck";
                case 1 -> Integer.toString(i + 1);
                case 2 -> "Lower Hall";
                default -> "B" + (i + 1);
            };
            transfers.add(new RoutePreview(route.name(), route.colors(), station, platform, outStation));
        }
        return List.copyOf(transfers);
    }

    private List<PlatformLayoutProjectionEngine.TransferData> layoutTransfers(int stopIndex) {
        if (!this.showTransfers || stopIndex == 0) {
            return List.of();
        }
        List<PlatformLayoutProjectionEngine.TransferData> transfers = new ArrayList<>();
        if (stopIndex % 4 == 1) {
            int routeIndex = Math.max(1, stopIndex % Math.max(2, this.routeCount));
            transfers.add(new PlatformLayoutProjectionEngine.TransferData(routeName(routeIndex), this.routePalette.colorsFor(routeIndex), Integer.toString(routeIndex + 1), "", false));
        }
        if (this.outStationTransfers && stopIndex % 6 == 3) {
            int routeIndex = Math.max(1, (stopIndex + 1) % Math.max(2, this.routeCount));
            transfers.add(new PlatformLayoutProjectionEngine.TransferData(routeName(routeIndex), this.routePalette.colorsFor(routeIndex), Integer.toString(routeIndex + 2), stopName(Math.max(0, stopIndex - 1), 0), true));
        }
        return List.copyOf(transfers);
    }

    private String routeName(int index) {
        return this.longNames ? "Sample Loop Line " + (index + 1) + " Extended Service" : switch (index) {
            case 0 -> "Redstone";
            case 1 -> "Gold";
            case 2 -> "End Loop";
            case 3 -> "Prismarine";
            default -> "Line " + (index + 1);
        };
    }

    private String stopName(int index, int layoutIndex) {
        String[] shortNames = {"Quartz", "Copper", "Block Plaza", "Market", "Prismarine", "Basalt", "Library", "Harbor", "End", "Gateway", "Garden", "Archive", "Canyon", "Depot", "Tower", "Museum"};
        String[] longNames = {"Quartz Harbor Transit Center", "Old Copper Factory Interchange", "Block Plaza", "Central Block Plaza Transportation Hub North", "Prismarine Gate", "Basalt Delta Museum", "Great Library Underpass", "Harbor Freight Terminal", "End Gateway International Terminal", "Gateway Garden Station", "Archive Hall", "Canyon South", "Depot East", "Tower Junction", "Museum Park", "Beacon Hill"};
        String[] names = this.longNames ? longNames : shortNames;
        return names[Math.floorMod(index + layoutIndex * 2, names.length)];
    }

    public ProjectionPreviewScenario withRouteCount(int count) {
        return copy(count, this.routePalette, this.showTranslation, this.showExit, this.exitLabel, this.longNames, this.showTransfers, this.platformTerminal, this.platformBidirectional, this.platformLoop, this.outStationTransfers, this.platformStopCount);
    }

    public ProjectionPreviewScenario withTranslation(boolean visible) {
        return copy(this.routeCount, this.routePalette, visible, this.showExit, this.exitLabel, this.longNames, this.showTransfers, this.platformTerminal, this.platformBidirectional, this.platformLoop, this.outStationTransfers, this.platformStopCount);
    }

    public ProjectionPreviewScenario withExit(boolean visible) {
        return copy(this.routeCount, this.routePalette, this.showTranslation, visible, this.exitLabel, this.longNames, this.showTransfers, this.platformTerminal, this.platformBidirectional, this.platformLoop, this.outStationTransfers, this.platformStopCount);
    }

    public ProjectionPreviewScenario withExitLabel(String label) {
        return copy(this.routeCount, this.routePalette, this.showTranslation, this.showExit, label, this.longNames, this.showTransfers, this.platformTerminal, this.platformBidirectional, this.platformLoop, this.outStationTransfers, this.platformStopCount);
    }

    public ProjectionPreviewScenario withLongNames(boolean enabled) {
        return copy(this.routeCount, this.routePalette, this.showTranslation, this.showExit, this.exitLabel, enabled, this.showTransfers, this.platformTerminal, this.platformBidirectional, this.platformLoop, this.outStationTransfers, this.platformStopCount);
    }

    public ProjectionPreviewScenario withPalette(RoutePalette palette) {
        return copy(this.routeCount, palette, this.showTranslation, this.showExit, this.exitLabel, this.longNames, this.showTransfers, this.platformTerminal, this.platformBidirectional, this.platformLoop, this.outStationTransfers, this.platformStopCount);
    }

    public ProjectionPreviewScenario withTransfers(boolean visible) {
        return copy(this.routeCount, this.routePalette, this.showTranslation, this.showExit, this.exitLabel, this.longNames, visible, this.platformTerminal, this.platformBidirectional, this.platformLoop, this.outStationTransfers, this.platformStopCount);
    }

    public ProjectionPreviewScenario withPlatformTerminal(boolean visible) {
        return copy(this.routeCount, this.routePalette, this.showTranslation, this.showExit, this.exitLabel, this.longNames, this.showTransfers, visible, this.platformBidirectional, this.platformLoop, this.outStationTransfers, this.platformStopCount);
    }

    public ProjectionPreviewScenario withPlatformBidirectional(boolean visible) {
        return copy(this.routeCount, this.routePalette, this.showTranslation, this.showExit, this.exitLabel, this.longNames, this.showTransfers, this.platformTerminal, visible, this.platformLoop, this.outStationTransfers, this.platformStopCount);
    }

    public ProjectionPreviewScenario withPlatformLoop(boolean visible) {
        return copy(this.routeCount, this.routePalette, this.showTranslation, this.showExit, this.exitLabel, this.longNames, this.showTransfers, this.platformTerminal, this.platformBidirectional, visible, this.outStationTransfers, this.platformStopCount);
    }

    public ProjectionPreviewScenario withOutStationTransfers(boolean visible) {
        return copy(this.routeCount, this.routePalette, this.showTranslation, this.showExit, this.exitLabel, this.longNames, this.showTransfers, this.platformTerminal, this.platformBidirectional, this.platformLoop, visible, this.platformStopCount);
    }

    public ProjectionPreviewScenario withPlatformStopCount(int count) {
        return copy(this.routeCount, this.routePalette, this.showTranslation, this.showExit, this.exitLabel, this.longNames, this.showTransfers, this.platformTerminal, this.platformBidirectional, this.platformLoop, this.outStationTransfers, count);
    }

    private ProjectionPreviewScenario copy(int routeCount, RoutePalette routePalette, boolean showTranslation, boolean showExit, String exitLabel, boolean longNames, boolean showTransfers, boolean platformTerminal, boolean platformBidirectional, boolean platformLoop, boolean outStationTransfers, int platformStopCount) {
        return new ProjectionPreviewScenario(this.primaryName, this.translationName, showTranslation, routeCount, routePalette, showExit, exitLabel, longNames, showTransfers, platformTerminal, platformBidirectional, platformLoop, outStationTransfers, platformStopCount);
    }

    private String layoutName(int index) {
        return this.longNames ? "Platform Service Layout " + (index + 1) : switch (index) {
            case 0 -> "Local";
            case 1 -> "Express";
            case 2 -> "Branch";
            case 3 -> "Night";
            default -> "L" + (index + 1);
        };
    }

    private static String normalized(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value.trim();
        return result.length() > 64 ? result.substring(0, 64) : result;
    }

    public enum RoutePalette {
        METRO,
        PASTEL,
        INDUSTRIAL;

        public RoutePalette next() {
            RoutePalette[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }

        public List<Integer> colorsFor(int index) {
            return switch (this) {
                case METRO -> switch (index % 6) {
                    case 0 -> List.of(0xFFE24B3B);
                    case 1 -> List.of(0xFF2675D8, 0xFF45D6C6);
                    case 2 -> List.of(0xFF7D4AE8);
                    case 3 -> List.of(0xFF14A36C);
                    case 4 -> List.of(0xFFFFB021);
                    default -> List.of(0xFFF263A6);
                };
                case PASTEL -> switch (index % 5) {
                    case 0 -> List.of(0xFFF08080);
                    case 1 -> List.of(0xFF76B5C5);
                    case 2 -> List.of(0xFFA889D9);
                    case 3 -> List.of(0xFF83C995);
                    default -> List.of(0xFFF4BA71);
                };
                case INDUSTRIAL -> switch (index % 5) {
                    case 0 -> List.of(0xFFFFC43D);
                    case 1 -> List.of(0xFF42D9E8);
                    case 2 -> List.of(0xFFF05A4F);
                    case 3 -> List.of(0xFFCED3D8);
                    default -> List.of(0xFF74E06A);
                };
            };
        }
    }

    public record RoutePreview(String name, List<Integer> colors, String station, String platform, boolean outStation) {
        public RoutePreview(String name, List<Integer> colors) {
            this(name, colors, "", "", false);
        }

        public RoutePreview {
            name = normalized(name, "?");
            colors = colors == null || colors.isEmpty() ? List.of(0xFF3366FF) : List.copyOf(colors);
            station = normalized(station, "");
            platform = normalized(platform, "");
        }
    }

}
