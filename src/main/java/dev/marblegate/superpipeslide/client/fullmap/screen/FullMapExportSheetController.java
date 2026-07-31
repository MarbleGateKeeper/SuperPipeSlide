package dev.marblegate.superpipeslide.client.fullmap.screen;

import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.export.MapExportOptions;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Owns the full route map's export sheet state for {@code FullRouteMapScreen}: the open
 * flag, the selected route lines and zoom levels (presets plus at most one custom value
 * from the input box), the resolution multiplier and background mode, and the route list
 * scroll position.
 *
 * <p>Mirrors {@code FullMapNavigationSheetController}: no rendering and no screen
 * back-reference; the screen drives this controller through the smallest possible state
 * API and renders it each frame.
 */
final class FullMapExportSheetController {
    private boolean open;
    private final Set<UUID> selectedRouteLineIds = new LinkedHashSet<>();
    private final Set<Double> selectedZoomLevels = new LinkedHashSet<>();
    private @Nullable Double customZoomLevel;
    private double selectedResolutionMultiplier = 1.0D;
    private MapExportOptions.ExportBackground backgroundMode = MapExportOptions.ExportBackground.BOTH;
    private double routeListScroll;
    private double routeListMaxScroll;

    boolean open() {
        return this.open;
    }

    void setOpen(boolean open) {
        this.open = open;
        if (open) {
            // First open defaults to "everything": all known lines at the default zoom.
            if (this.selectedRouteLineIds.isEmpty()) {
                ClientRouteDataCache.routeLines().forEach(line -> this.selectedRouteLineIds.add(line.id()));
            }
            if (this.selectedZoomLevels.isEmpty() && this.customZoomLevel == null) {
                this.selectedZoomLevels.add(FullRouteMapConfig.DEFAULT_ZOOM);
            }
        }
    }

    Set<UUID> selectedRouteLineIds() {
        return this.selectedRouteLineIds;
    }

    void toggleRouteLine(UUID routeLineId) {
        if (!this.selectedRouteLineIds.remove(routeLineId)) {
            this.selectedRouteLineIds.add(routeLineId);
        }
    }

    void selectAllRouteLines() {
        this.selectedRouteLineIds.clear();
        ClientRouteDataCache.routeLines().forEach(line -> this.selectedRouteLineIds.add(line.id()));
    }

    void clearRouteLines() {
        this.selectedRouteLineIds.clear();
    }

    Set<Double> selectedZoomLevels() {
        return this.selectedZoomLevels;
    }

    void togglePresetZoomLevel(double zoom) {
        if (!this.selectedZoomLevels.remove(zoom)) {
            this.selectedZoomLevels.add(zoom);
        }
    }

    @Nullable
    Double customZoomLevel() {
        return this.customZoomLevel;
    }

    /** Sets (or clears, on {@code null}) the custom zoom level typed into the input box. */
    void setCustomZoomLevel(@Nullable Double zoom) {
        this.customZoomLevel = zoom;
    }

    /** Preset selections plus the custom value, as accepted by {@link MapExportOptions}. */
    List<Double> effectiveZoomLevels() {
        List<Double> zooms = new ArrayList<>(this.selectedZoomLevels);
        if (this.customZoomLevel != null) {
            zooms.add(this.customZoomLevel);
        }
        return zooms;
    }

    double selectedResolutionMultiplier() {
        return this.selectedResolutionMultiplier;
    }

    void setResolutionMultiplier(double multiplier) {
        this.selectedResolutionMultiplier = multiplier;
    }

    MapExportOptions.ExportBackground backgroundMode() {
        return this.backgroundMode;
    }

    void setBackgroundMode(MapExportOptions.ExportBackground mode) {
        this.backgroundMode = mode;
    }

    MapExportOptions options() {
        return new MapExportOptions(this.selectedRouteLineIds, this.effectiveZoomLevels(), this.selectedResolutionMultiplier, this.backgroundMode);
    }

    double routeListScroll() {
        return this.routeListScroll;
    }

    void setRouteListScroll(double scroll) {
        this.routeListScroll = Math.max(0.0D, Math.min(this.routeListMaxScroll, scroll));
    }

    double routeListMaxScroll() {
        return this.routeListMaxScroll;
    }

    void setRouteListMaxScroll(double maxScroll) {
        this.routeListMaxScroll = Math.max(0.0D, maxScroll);
        this.routeListScroll = Math.max(0.0D, Math.min(this.routeListScroll, this.routeListMaxScroll));
    }
}
