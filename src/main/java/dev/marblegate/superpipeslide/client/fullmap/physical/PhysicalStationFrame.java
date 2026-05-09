package dev.marblegate.superpipeslide.client.fullmap.physical;

import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

public record PhysicalStationFrame(
        UUID stationGroupId,
        ResourceKey<Level> levelKey,
        String label,
        List<String> platformNodeIds,
        List<UUID> routeLineIds,
        double centerX,
        double centerZ,
        double centerY,
        Aabb2 worldBounds
) {
    public PhysicalStationFrame {
        platformNodeIds = List.copyOf(platformNodeIds);
        routeLineIds = routeLineIds.stream().distinct().sorted().toList();
    }
}
