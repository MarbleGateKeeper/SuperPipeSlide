package dev.marblegate.superpipeslide.client.fullmap.routecard.model;

import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record RouteCardSegment(
        String id,
        int index,
        ResourceKey<Level> levelKey,
        List<RouteCardNodeId> nodeIds,
        List<String> edgeIds) {
    public RouteCardSegment {
        nodeIds = List.copyOf(nodeIds);
        edgeIds = List.copyOf(edgeIds);
    }
}
