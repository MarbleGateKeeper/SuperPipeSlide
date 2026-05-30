package dev.marblegate.superpipeslide.common.core.slide.traversal;

import java.util.UUID;
import net.minecraft.util.Mth;

public record TraversalCursor(UUID connectionId, int direction, double distanceOnConnection) {
    public TraversalCursor {
        direction = direction < 0 ? -1 : 1;
        if (!Double.isFinite(distanceOnConnection)) {
            distanceOnConnection = 0.0D;
        }
    }

    public TraversalCursor clamp(double connectionLength) {
        return new TraversalCursor(this.connectionId, this.direction, Mth.clamp(this.distanceOnConnection, 0.0D, Math.max(0.0D, connectionLength)));
    }
}
