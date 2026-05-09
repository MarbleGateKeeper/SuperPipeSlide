package dev.marblegate.superpipeslide.client.fullmap.model;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record MapNode(
        NodeId id,
        ResourceKey<Level> levelKey,
        double worldX,
        double worldZ,
        double worldY,
        String label,
        NodeKind kind,
        List<UUID> stationGroupIds,
        List<UUID> platformStopIds,
        List<UUID> routeLineIds,
        Optional<PipeAnchorId> foldAnchorId,
        Optional<PipeAnchorId> foldPeerId,
        Optional<NodeId> clusterId
) {
    public MapNode {
        stationGroupIds = List.copyOf(stationGroupIds);
        platformStopIds = List.copyOf(platformStopIds);
        routeLineIds = routeLineIds.stream().distinct().sorted().toList();
        foldAnchorId = foldAnchorId == null ? Optional.empty() : foldAnchorId;
        foldPeerId = foldPeerId == null ? Optional.empty() : foldPeerId;
        clusterId = clusterId == null ? Optional.empty() : clusterId;
    }

    public boolean isTransferStation() {
        return this.routeLineIds.size() >= 2;
    }
}
