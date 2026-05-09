package dev.marblegate.superpipeslide.client.fullmap.model.geom;

public record Aabb2(double minX, double minY, double maxX, double maxY) {
    public static Aabb2 empty() {
        return new Aabb2(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
    }

    public static Aabb2 around(double x, double y, double radius) {
        return new Aabb2(x - radius, y - radius, x + radius, y + radius);
    }

    public Aabb2 include(double x, double y) {
        return new Aabb2(Math.min(this.minX, x), Math.min(this.minY, y), Math.max(this.maxX, x), Math.max(this.maxY, y));
    }

    public Aabb2 include(Aabb2 other) {
        if (other.isEmpty()) {
            return this;
        }
        return new Aabb2(Math.min(this.minX, other.minX), Math.min(this.minY, other.minY), Math.max(this.maxX, other.maxX), Math.max(this.maxY, other.maxY));
    }

    public Aabb2 inflate(double amount) {
        if (this.isEmpty()) {
            return this;
        }
        return new Aabb2(this.minX - amount, this.minY - amount, this.maxX + amount, this.maxY + amount);
    }

    public boolean intersects(Aabb2 other) {
        return !this.isEmpty() && !other.isEmpty() && this.maxX >= other.minX && this.minX <= other.maxX && this.maxY >= other.minY && this.minY <= other.maxY;
    }

    public boolean contains(double x, double y) {
        return !this.isEmpty() && x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY;
    }

    public double centerX() {
        return this.isEmpty() ? 0.0D : (this.minX + this.maxX) * 0.5D;
    }

    public double centerY() {
        return this.isEmpty() ? 0.0D : (this.minY + this.maxY) * 0.5D;
    }

    public boolean isEmpty() {
        return this.minX > this.maxX || this.minY > this.maxY;
    }
}
