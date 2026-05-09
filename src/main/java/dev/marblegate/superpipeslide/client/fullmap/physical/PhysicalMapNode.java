package dev.marblegate.superpipeslide.client.fullmap.physical;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record PhysicalMapNode(
        String id,
        PhysicalNodeKind kind,
        ResourceKey<Level> levelKey,
        double worldX,
        double worldZ,
        double worldY,
        String label,
        Optional<UUID> platformStopId,
        Optional<UUID> stationGroupId,
        Optional<PipeAnchorId> foldAnchorId,
        List<UUID> routeLineIds
) {
    public PhysicalMapNode {
        platformStopId = platformStopId == null ? Optional.empty() : platformStopId;
        stationGroupId = stationGroupId == null ? Optional.empty() : stationGroupId;
        foldAnchorId = foldAnchorId == null ? Optional.empty() : foldAnchorId;
        routeLineIds = routeLineIds.stream().distinct().sorted().toList();
    }
}
