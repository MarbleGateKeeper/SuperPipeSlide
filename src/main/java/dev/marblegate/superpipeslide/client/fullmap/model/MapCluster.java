package dev.marblegate.superpipeslide.client.fullmap.model;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

public record MapCluster(
        NodeId nodeId,
        ResourceKey<Level> levelKey,
        List<UUID> stationGroupIds,
        List<NodeId> memberNodeIds,
        String label,
        double worldX,
        double worldZ,
        double worldY,
        List<UUID> routeLineIds
) {
    public MapCluster {
        stationGroupIds = List.copyOf(stationGroupIds);
        memberNodeIds = List.copyOf(memberNodeIds);
        routeLineIds = routeLineIds.stream().distinct().sorted().toList();
    }
}
