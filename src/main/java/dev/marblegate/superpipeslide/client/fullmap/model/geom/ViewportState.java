package dev.marblegate.superpipeslide.client.fullmap.model.geom;

import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record ViewportState(ResourceKey<Level> levelKey, double centerWorldX, double centerWorldZ, double zoom, double pitchDegrees, double bearingDegrees) {
    public ViewportState(ResourceKey<Level> levelKey, double centerWorldX, double centerWorldZ, double zoom) {
        this(levelKey, centerWorldX, centerWorldZ, zoom, 0.0D, 0.0D);
    }

    public ViewportState {
        zoom = clampZoom(zoom);
        pitchDegrees = clampPitch(pitchDegrees);
        bearingDegrees = normalizeBearing(bearingDegrees);
    }

    public ViewportState withCenter(double x, double z) {
        return new ViewportState(this.levelKey, x, z, this.zoom, this.pitchDegrees, this.bearingDegrees);
    }

    public ViewportState withZoom(double zoom) {
        return new ViewportState(this.levelKey, this.centerWorldX, this.centerWorldZ, zoom, this.pitchDegrees, this.bearingDegrees);
    }

    public ViewportState withCamera(double pitchDegrees, double bearingDegrees) {
        return new ViewportState(this.levelKey, this.centerWorldX, this.centerWorldZ, this.zoom, pitchDegrees, bearingDegrees);
    }

    public ViewportState withFlatCamera() {
        return this.withCamera(0.0D, 0.0D);
    }

    public boolean cameraTilted() {
        return Math.abs(this.pitchDegrees) > FullRouteMapConfig.CAMERA_TILTED_THRESHOLD_DEGREES
                || Math.abs(this.bearingDegrees) > FullRouteMapConfig.CAMERA_ROTATED_THRESHOLD_DEGREES;
    }

    private static double clampZoom(double zoom) {
        if (!Double.isFinite(zoom)) {
            return FullRouteMapConfig.DEFAULT_ZOOM;
        }
        return Math.max(FullRouteMapConfig.ZOOM_MIN, Math.min(FullRouteMapConfig.ZOOM_MAX, zoom));
    }

    private static double clampPitch(double pitchDegrees) {
        if (!Double.isFinite(pitchDegrees)) {
            return 0.0D;
        }
        return Math.max(FullRouteMapConfig.CAMERA_PITCH_MIN_DEGREES, Math.min(FullRouteMapConfig.CAMERA_PITCH_MAX_DEGREES, pitchDegrees));
    }

    private static double normalizeBearing(double bearingDegrees) {
        if (!Double.isFinite(bearingDegrees)) {
            return 0.0D;
        }
        double normalized = bearingDegrees % 360.0D;
        if (normalized > 180.0D) {
            normalized -= 360.0D;
        } else if (normalized <= -180.0D) {
            normalized += 360.0D;
        }
        return normalized;
    }
}
