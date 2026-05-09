package dev.marblegate.superpipeslide.client.fullmap.cluster.model;

import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;

public record ClusterCardViewport(double centerX, double centerY, double zoom) {
    public ClusterCardViewport withCenter(double x, double y) {
        return new ClusterCardViewport(x, y, this.zoom);
    }

    public ClusterCardViewport withZoom(double zoom) {
        return new ClusterCardViewport(this.centerX, this.centerY, Math.max(FullRouteMapConfig.ZOOM_MIN, Math.min(FullRouteMapConfig.ZOOM_MAX, zoom)));
    }
}
