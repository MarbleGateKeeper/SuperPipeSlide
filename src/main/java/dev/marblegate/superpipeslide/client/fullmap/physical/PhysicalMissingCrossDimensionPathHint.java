package dev.marblegate.superpipeslide.client.fullmap.physical;

import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

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
        double directionZ) {}
