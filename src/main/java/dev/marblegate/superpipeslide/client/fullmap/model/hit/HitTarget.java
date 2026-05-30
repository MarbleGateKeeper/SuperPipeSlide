package dev.marblegate.superpipeslide.client.fullmap.model.hit;

import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import java.util.Optional;

public record HitTarget(
        HitKind kind,
        Optional<NodeId> nodeId,
        Optional<String> edgeId,
        Optional<String> transferHintId,
        Optional<String> missingCrossDimensionHintId,
        Optional<String> physicalNodeId,
        Optional<String> physicalEdgeId) {
    public HitTarget {
        nodeId = nodeId == null ? Optional.empty() : nodeId;
        edgeId = edgeId == null ? Optional.empty() : edgeId;
        transferHintId = transferHintId == null ? Optional.empty() : transferHintId;
        missingCrossDimensionHintId = missingCrossDimensionHintId == null ? Optional.empty() : missingCrossDimensionHintId;
        physicalNodeId = physicalNodeId == null ? Optional.empty() : physicalNodeId;
        physicalEdgeId = physicalEdgeId == null ? Optional.empty() : physicalEdgeId;
    }

    public static HitTarget none() {
        return new HitTarget(HitKind.NONE, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static HitTarget node(NodeId nodeId) {
        return new HitTarget(HitKind.NODE, Optional.of(nodeId), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static HitTarget edge(String edgeId) {
        return new HitTarget(HitKind.EDGE, Optional.empty(), Optional.of(edgeId), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static HitTarget transferHint(String transferHintId) {
        return new HitTarget(HitKind.TRANSFER_HINT, Optional.empty(), Optional.empty(), Optional.of(transferHintId), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static HitTarget missingCrossDimensionPath(String hintId) {
        return new HitTarget(HitKind.MISSING_CROSS_DIMENSION_PATH, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(hintId), Optional.empty(), Optional.empty());
    }

    public static HitTarget physicalNode(String nodeId) {
        return new HitTarget(HitKind.PHYSICAL_NODE, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(nodeId), Optional.empty());
    }

    public static HitTarget physicalEdge(String edgeId) {
        return new HitTarget(HitKind.PHYSICAL_EDGE, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(edgeId));
    }
}
