package dev.marblegate.superpipeslide.client.fullmap.physical;

import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

public record PhysicalMapEdge(
        String id,
        ResourceKey<Level> levelKey,
        Optional<String> fromNodeId,
        Optional<String> toNodeId,
        List<Vec2> points,
        PhysicalEdgeMetadata metadata,
        Aabb2 worldBounds
) {
    public PhysicalMapEdge {
        fromNodeId = fromNodeId == null ? Optional.empty() : fromNodeId;
        toNodeId = toNodeId == null ? Optional.empty() : toNodeId;
        points = List.copyOf(points);
    }
}
