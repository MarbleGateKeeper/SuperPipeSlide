package dev.marblegate.superpipeslide.client.fullmap.cluster.model;

import dev.marblegate.superpipeslide.client.fullmap.model.MapEdge;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record ClusterCardEdge(
        String id,
        ClusterCardEdgeKind kind,
        String from,
        String to,
        Optional<MapEdge> mapEdge,
        Optional<NodeId> insideNodeId,
        Optional<NodeId> outsideNodeId,
        List<UUID> routeLineIds
) {
    public ClusterCardEdge {
        mapEdge = mapEdge == null ? Optional.empty() : mapEdge;
        insideNodeId = insideNodeId == null ? Optional.empty() : insideNodeId;
        outsideNodeId = outsideNodeId == null ? Optional.empty() : outsideNodeId;
        routeLineIds = routeLineIds.stream().distinct().sorted().toList();
    }
}
