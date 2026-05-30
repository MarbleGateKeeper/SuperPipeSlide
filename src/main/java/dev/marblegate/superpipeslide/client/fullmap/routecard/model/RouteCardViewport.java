package dev.marblegate.superpipeslide.client.fullmap.routecard.model;

public record RouteCardViewport(double centerX, double centerY, double zoom) {

    private static final double MIN_ZOOM = 0.001D;
    private static final double MAX_ZOOM = 12.0D;
    public RouteCardViewport {
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    }

    public RouteCardViewport withCenter(double x, double y) {
        return new RouteCardViewport(x, y, this.zoom);
    }

    public RouteCardViewport withZoom(double zoom) {
        return new RouteCardViewport(this.centerX, this.centerY, zoom);
    }
}
