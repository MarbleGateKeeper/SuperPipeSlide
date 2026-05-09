package dev.marblegate.superpipeslide.client.fullmap.cluster.model;

import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.model.MapCluster;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record ClusterCardSemanticGraph(
        ClusterCardProfile profile,
        ResourceKey<Level> levelKey,
        MapCluster cluster,
        List<ClusterCardNode> nodes,
        List<ClusterCardEdge> edges,
        List<UUID> routeLineIds,
        int externalEdgeCount,
        Aabb2 worldBounds
) {
    public ClusterCardSemanticGraph {
        profile = profile == null ? ClusterCardProfile.ORDINARY : profile;
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        routeLineIds = routeLineIds.stream().distinct().sorted().toList();
    }

    public Optional<ClusterCardNode> node(String id) {
        return this.nodes.stream().filter(node -> node.id().equals(id)).findFirst();
    }

    public Optional<ClusterCardEdge> edge(String id) {
        return this.edges.stream().filter(edge -> edge.id().equals(id)).findFirst();
    }
}
