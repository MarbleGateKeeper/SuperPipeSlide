package dev.marblegate.superpipeslide.client.fullmap.physical;

import dev.marblegate.superpipeslide.client.fullmap.diagnostic.MapBuildDiagnostic;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record PhysicalRouteMapGraph(
        ResourceKey<Level> levelKey,
        List<PhysicalMapNode> nodes,
        Map<String, PhysicalMapNode> nodesById,
        List<PhysicalMapEdge> edges,
        Map<String, PhysicalMapEdge> edgesById,
        List<PhysicalMissingCrossDimensionPathHint> missingCrossDimensionPathHints,
        List<PhysicalStationFrame> stationFrames,
        Map<UUID, PhysicalStationFrame> stationFramesByStationId,
        List<MapBuildDiagnostic> diagnostics,
        Aabb2 worldBounds,
        long routeRevision,
        long pipeRevision) {
    public PhysicalRouteMapGraph {
        nodes = nodes.stream().sorted(Comparator.comparing(PhysicalMapNode::id)).toList();
        nodesById = Map.copyOf(nodesById);
        edges = edges.stream().sorted(Comparator.comparing(PhysicalMapEdge::id)).toList();
        edgesById = Map.copyOf(edgesById);
        missingCrossDimensionPathHints = missingCrossDimensionPathHints.stream().sorted(Comparator.comparing(PhysicalMissingCrossDimensionPathHint::id)).toList();
        stationFrames = stationFrames.stream().sorted(Comparator.comparing(frame -> frame.stationGroupId().toString())).toList();
        stationFramesByStationId = Map.copyOf(stationFramesByStationId);
        diagnostics = List.copyOf(diagnostics);
    }

    public Optional<PhysicalMapNode> node(String id) {
        return Optional.ofNullable(this.nodesById.get(id));
    }

    public Optional<PhysicalMapEdge> edge(String id) {
        return Optional.ofNullable(this.edgesById.get(id));
    }

    public Optional<PhysicalStationFrame> stationFrame(UUID stationGroupId) {
        return Optional.ofNullable(this.stationFramesByStationId.get(stationGroupId));
    }
}
