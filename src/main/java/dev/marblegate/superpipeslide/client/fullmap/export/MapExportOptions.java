package dev.marblegate.superpipeslide.client.fullmap.export;

import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * User-chosen parameters of one map export run: which route lines to draw, which zoom levels to
 * render at, the resolution multiplier relative to the on-screen map, and whether the images are
 * written with an opaque background, a transparent one, or both.
 *
 * <p>
 * Zoom levels reuse the viewport zoom semantics of the on-screen map (logical pixels = layout units
 * × {@link FullRouteMapConfig#BASE_SCALE} × zoom), so an exported image is laid out identically to
 * what the map shows at that zoom, including its zoom-driven label level-of-detail. The resolution
 * multiplier then scales the physical pixel density relative to the window's GUI scale: at
 * {@code 1.0} one logical map pixel occupies exactly as many physical pixels as on screen.
 */
public record MapExportOptions(Set<UUID> routeLineIds, List<Double> zoomLevels, double resolutionMultiplier, ExportBackground backgroundMode) {

    /** Preset zoom levels offered as toggle chips in the export sheet. */
    public static final List<Double> PRESET_ZOOM_LEVELS = List.of(0.25D, 0.5D, 1.0D, 2.0D, 4.0D);
    /** Preset resolution multipliers offered as toggle chips in the export sheet. */
    public static final List<Double> DENSITY_PRESETS = List.of(1.0D, 2.0D, 4.0D);
    /** Which background variant(s) the export writes. */
    public enum ExportBackground {
        OPAQUE,
        TRANSPARENT,
        BOTH;

        public boolean includesOpaque() {
            return this != TRANSPARENT;
        }

        public boolean includesTransparent() {
            return this != OPAQUE;
        }
    }

    public MapExportOptions {
        routeLineIds = Set.copyOf(routeLineIds);
        zoomLevels = zoomLevels.stream()
                .map(zoom -> Math.max(FullRouteMapConfig.ZOOM_MIN, Math.min(FullRouteMapConfig.ZOOM_MAX, zoom)))
                .distinct()
                .sorted()
                .toList();
        resolutionMultiplier = Math.max(DENSITY_PRESETS.getFirst(), Math.min(DENSITY_PRESETS.getLast(), resolutionMultiplier));
        backgroundMode = backgroundMode == null ? ExportBackground.OPAQUE : backgroundMode;
    }

    public boolean isValid() {
        return !this.routeLineIds.isEmpty() && !this.zoomLevels.isEmpty();
    }

    public MapExportOptions withRouteLineIds(Set<UUID> ids) {
        return new MapExportOptions(new LinkedHashSet<>(ids), this.zoomLevels, this.resolutionMultiplier, this.backgroundMode);
    }

    public MapExportOptions withZoomLevels(List<Double> zooms) {
        return new MapExportOptions(this.routeLineIds, zooms, this.resolutionMultiplier, this.backgroundMode);
    }

    public MapExportOptions withResolutionMultiplier(double multiplier) {
        return new MapExportOptions(this.routeLineIds, this.zoomLevels, multiplier, this.backgroundMode);
    }

    public MapExportOptions withBackgroundMode(ExportBackground mode) {
        return new MapExportOptions(this.routeLineIds, this.zoomLevels, this.resolutionMultiplier, mode);
    }
}
