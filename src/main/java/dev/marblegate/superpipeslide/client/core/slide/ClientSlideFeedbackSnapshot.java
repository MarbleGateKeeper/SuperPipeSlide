package dev.marblegate.superpipeslide.client.core.slide;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public record ClientSlideFeedbackSnapshot(
        UUID sessionId,
        UUID connectionId,
        Vec3 position,
        Vec3 tangent,
        double speed,
        double maxSpeed,
        double distanceOnConnection,
        double connectionLength,
        boolean highway,
        boolean accelerationAttribute,
        boolean platformConnection,
        boolean stationSlow,
        boolean routeActive,
        int ticksSliding) {
    public double speed01() {
        return this.maxSpeed <= 1.0E-6D ? 0.0D : Math.min(1.0D, Math.abs(this.speed) / this.maxSpeed);
    }
}
