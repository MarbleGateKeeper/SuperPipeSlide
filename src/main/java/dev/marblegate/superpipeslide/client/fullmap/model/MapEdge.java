package dev.marblegate.superpipeslide.client.fullmap.model;

import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record MapEdge(
        String id,
        ResourceKey<Level> levelKey,
        NodeId from,
        NodeId to,
        List<MapEdgeOccurrence> occurrences,
        Aabb2 worldBounds,
        List<UUID> routeLineIds) {
    public MapEdge(
            String id,
            ResourceKey<Level> levelKey,
            NodeId from,
            NodeId to,
            List<MapEdgeOccurrence> occurrences,
            Aabb2 worldBounds) {
        this(id, levelKey, from, to, occurrences, worldBounds, computeRouteLineIds(occurrences));
    }

    public MapEdge {
        occurrences = List.copyOf(occurrences);
        routeLineIds = List.copyOf(routeLineIds);
    }

    private static List<UUID> computeRouteLineIds(List<MapEdgeOccurrence> occurrences) {
        return occurrences.stream().map(MapEdgeOccurrence::routeLineId).distinct().sorted().toList();
    }
}
