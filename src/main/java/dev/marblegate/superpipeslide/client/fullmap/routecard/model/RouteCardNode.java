package dev.marblegate.superpipeslide.client.fullmap.routecard.model;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record RouteCardNode(
        RouteCardNodeId id,
        Optional<UUID> stationGroupId,
        Optional<UUID> platformStopId,
        int layoutOccurrence,
        RouteCardNodeKind kind,
        ResourceKey<Level> levelKey,
        double worldX,
        double worldZ,
        double worldY,
        String label,
        List<UUID> routeLineIds
) {
    public RouteCardNode {
        stationGroupId = stationGroupId == null ? Optional.empty() : stationGroupId;
        platformStopId = platformStopId == null ? Optional.empty() : platformStopId;
        routeLineIds = routeLineIds.stream().distinct().sorted().toList();
    }
}
