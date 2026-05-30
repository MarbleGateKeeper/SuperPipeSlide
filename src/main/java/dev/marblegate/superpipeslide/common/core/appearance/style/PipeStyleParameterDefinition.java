package dev.marblegate.superpipeslide.common.core.appearance.style;

public record PipeStyleParameterDefinition(
        String id,
        String nameKey,
        double defaultValue,
        double minValue,
        double maxValue,
        double step) {
    public PipeStyleParameterDefinition {
        id = id == null ? "" : id.trim();
        nameKey = nameKey == null ? "" : nameKey.trim();
        minValue = Math.max(0.0D, minValue);
        maxValue = Math.max(minValue, maxValue);
        defaultValue = Math.max(minValue, Math.min(maxValue, defaultValue));
        step = step <= 0.0D ? 0.01D : step;
    }

    public double clamp(double value) {
        double clamped = Math.max(this.minValue, Math.min(this.maxValue, value));
        double stepped = Math.round((clamped - this.minValue) / this.step) * this.step + this.minValue;
        return Math.max(this.minValue, Math.min(this.maxValue, stepped));
    }

    public double normalizedPercent(double value) {
        if (this.maxValue <= this.minValue) {
            return 0.0D;
        }
        return (this.clamp(value) - this.minValue) / (this.maxValue - this.minValue);
    }

    public double valueAtPercent(double percent) {
        double clampedPercent = Math.max(0.0D, Math.min(1.0D, percent));
        return this.clamp(this.minValue + (this.maxValue - this.minValue) * clampedPercent);
    }
}
