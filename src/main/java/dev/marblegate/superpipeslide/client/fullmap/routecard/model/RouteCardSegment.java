package dev.marblegate.superpipeslide.client.fullmap.routecard.model;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

public record RouteCardSegment(
        String id,
        int index,
        ResourceKey<Level> levelKey,
        List<RouteCardNodeId> nodeIds,
        List<String> edgeIds
) {
    public RouteCardSegment {
        nodeIds = List.copyOf(nodeIds);
        edgeIds = List.copyOf(edgeIds);
    }
}
