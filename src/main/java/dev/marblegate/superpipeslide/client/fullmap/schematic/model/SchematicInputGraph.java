package dev.marblegate.superpipeslide.client.fullmap.schematic.model;

import dev.marblegate.superpipeslide.client.fullmap.model.MapTransferHint;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record SchematicInputGraph(
        ResourceKey<Level> levelKey,
        List<SchematicNode> nodes,
        Map<NodeId, SchematicNode> nodesById,
        List<SchematicEdge> edges,
        List<MapTransferHint> transferHints,
        long routeRevision,
        long pipeRevision) {
    public SchematicInputGraph {
        nodes = nodes.stream().sorted(Comparator.comparing(SchematicNode::id)).toList();
        nodesById = Map.copyOf(nodesById);
        edges = List.copyOf(edges);
        transferHints = List.copyOf(transferHints);
    }

    public Optional<SchematicNode> node(NodeId id) {
        return Optional.ofNullable(this.nodesById.get(id));
    }
}
