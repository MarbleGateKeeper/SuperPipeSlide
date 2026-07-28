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
        long pipeRevision,
        LabelWidthMeasurer labelWidthMeasurer) {
    public SchematicInputGraph {
        nodes = nodes.stream().sorted(Comparator.comparing(SchematicNode::id)).toList();
        nodesById = Map.copyOf(nodesById);
        edges = List.copyOf(edges);
        transferHints = List.copyOf(transferHints);
        // Never let a solver run with a null measurer; the Latin estimate is the safe fallback.
        labelWidthMeasurer = labelWidthMeasurer == null ? LabelWidthMeasurer.latinEstimate() : labelWidthMeasurer;
    }

    /** Backwards-compatible constructor: inputs built without a measurer fall back to the Latin estimate. */
    public SchematicInputGraph(
            ResourceKey<Level> levelKey,
            List<SchematicNode> nodes,
            Map<NodeId, SchematicNode> nodesById,
            List<SchematicEdge> edges,
            List<MapTransferHint> transferHints,
            long routeRevision,
            long pipeRevision) {
        this(levelKey, nodes, nodesById, edges, transferHints, routeRevision, pipeRevision, LabelWidthMeasurer.latinEstimate());
    }

    /** Returns a copy of this input carrying the given label width measurer. */
    public SchematicInputGraph withLabelWidthMeasurer(LabelWidthMeasurer measurer) {
        return new SchematicInputGraph(this.levelKey, this.nodes, this.nodesById, this.edges, this.transferHints, this.routeRevision, this.pipeRevision, measurer);
    }

    public Optional<SchematicNode> node(NodeId id) {
        return Optional.ofNullable(this.nodesById.get(id));
    }
}
