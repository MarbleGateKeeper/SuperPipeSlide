package dev.marblegate.superpipeslide.client.fullmap.physical;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

public record PhysicalMissingCrossDimensionPathHint(
        String id,
        ResourceKey<Level> levelKey,
        String fromNodeId,
        UUID routeLineId,
        UUID routeLayoutId,
        UUID routeSectionId,
        int layoutIndex,
        UUID fromPlatformStopId,
        UUID toPlatformStopId,
        ResourceKey<Level> targetLevelKey,
        double directionX,
        double directionZ
) {
}
