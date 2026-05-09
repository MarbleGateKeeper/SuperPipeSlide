package dev.marblegate.superpipeslide.common.core.slide;

import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionAttributes;
import dev.marblegate.superpipeslide.config.Config;

public record ResolvedPipeSpeedRules(double maxSpeed, double acceleration, double deceleration, boolean highway, boolean accelerationAttribute) {
    public static ResolvedPipeSpeedRules from(PipeConnectionAttributes attributes) {
        boolean highway = attributes.highway();
        boolean accelerationAttribute = attributes.acceleration();
        double maxSpeed = highway ? Config.HIGHWAY_MAX_SPEED.getAsDouble() : Config.NORMAL_MAX_SPEED.getAsDouble();
        double acceleration = accelerationAttribute
                ? Config.ACCELERATION_ATTRIBUTE_ACCELERATION.getAsDouble()
                : highway ? Config.HIGHWAY_ACCELERATION.getAsDouble() : Config.NORMAL_ACCELERATION.getAsDouble();
        double deceleration = highway ? Config.HIGHWAY_OVERSPEED_DECELERATION.getAsDouble() : Config.NORMAL_OVERSPEED_DECELERATION.getAsDouble();
        return new ResolvedPipeSpeedRules(maxSpeed, acceleration, deceleration, highway, accelerationAttribute);
    }

    public double approach(double currentSpeed) {
        if (currentSpeed < this.maxSpeed) {
            return Math.min(this.maxSpeed, currentSpeed + this.acceleration);
        }
        if (currentSpeed > this.maxSpeed) {
            return Math.max(this.maxSpeed, currentSpeed - this.deceleration);
        }
        return currentSpeed;
    }
}
