package dev.marblegate.superpipeslide.client.fullmap.cluster.model;

import dev.marblegate.superpipeslide.client.fullmap.model.MapNode;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record ClusterCardNode(
        String id,
        ClusterCardNodeKind kind,
        Optional<NodeId> mapNodeId,
        Optional<MapNode> mapNode,
        ResourceKey<Level> levelKey,
        double worldX,
        double worldZ,
        double worldY,
        String label,
        List<UUID> routeLineIds,
        Optional<PipeAnchorId> foldAnchorId,
        Optional<PipeAnchorId> foldPeerId,
        Optional<NodeId> outsideNodeId,
        boolean stationInternalLoop
) {
    public ClusterCardNode {
        mapNodeId = mapNodeId == null ? Optional.empty() : mapNodeId;
        mapNode = mapNode == null ? Optional.empty() : mapNode;
        routeLineIds = routeLineIds.stream().distinct().sorted().toList();
        foldAnchorId = foldAnchorId == null ? Optional.empty() : foldAnchorId;
        foldPeerId = foldPeerId == null ? Optional.empty() : foldPeerId;
        outsideNodeId = outsideNodeId == null ? Optional.empty() : outsideNodeId;
    }

    public boolean member() {
        return this.kind != ClusterCardNodeKind.EXTERNAL_PORT;
    }
}
