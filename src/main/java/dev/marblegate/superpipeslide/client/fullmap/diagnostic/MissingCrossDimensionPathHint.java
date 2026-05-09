package dev.marblegate.superpipeslide.client.fullmap.diagnostic;

import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

public record MissingCrossDimensionPathHint(
        String id,
        ResourceKey<Level> levelKey,
        NodeId from,
        UUID routeLineId,
        UUID routeLayoutId,
        UUID routeSectionId,
        int layoutIndex,
        ResourceKey<Level> targetLevelKey,
        double directionX,
        double directionZ
) {
}
