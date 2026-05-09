package dev.marblegate.superpipeslide.client.fullmap.model.geom;

public record Vec2(double x, double y) {
    public double distanceTo(Vec2 other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
