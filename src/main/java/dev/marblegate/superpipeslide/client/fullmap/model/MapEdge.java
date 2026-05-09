package dev.marblegate.superpipeslide.client.fullmap.model;


import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

public record MapEdge(
        String id,
        ResourceKey<Level> levelKey,
        NodeId from,
        NodeId to,
        List<MapEdgeOccurrence> occurrences,
        Aabb2 worldBounds
) {
    public MapEdge {
        occurrences = List.copyOf(occurrences);
    }

    public List<UUID> routeLineIds() {
        return this.occurrences.stream().map(MapEdgeOccurrence::routeLineId).distinct().sorted().toList();
    }
}
