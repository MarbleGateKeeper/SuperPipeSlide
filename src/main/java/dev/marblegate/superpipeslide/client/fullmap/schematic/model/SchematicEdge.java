package dev.marblegate.superpipeslide.client.fullmap.schematic.model;

import dev.marblegate.superpipeslide.client.fullmap.model.MapEdge;
import dev.marblegate.superpipeslide.client.fullmap.model.MapEdgeOccurrence;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;

import java.util.List;
import java.util.UUID;

public record SchematicEdge(
        String id,
        NodeId from,
        NodeId to,
        SemanticEdgeKind kind,
        List<UUID> routeLineIds,
        List<MapEdgeOccurrence> occurrences,
        MapEdge sourceEdge
) {
    public SchematicEdge {
        routeLineIds = routeLineIds.stream().distinct().sorted().toList();
        occurrences = List.copyOf(occurrences);
    }

    public SchematicEdge withKind(SemanticEdgeKind nextKind) {
        return new SchematicEdge(this.id, this.from, this.to, nextKind, this.routeLineIds, this.occurrences, this.sourceEdge);
    }
}
