package dev.marblegate.superpipeslide.client.fullmap.cluster.hit;

import java.util.Optional;

public record ClusterCardHit(ClusterCardHitKind kind, Optional<String> nodeId, Optional<String> edgeId) {
    public ClusterCardHit {
        nodeId = nodeId == null ? Optional.empty() : nodeId;
        edgeId = edgeId == null ? Optional.empty() : edgeId;
    }

    public static ClusterCardHit none() {
        return new ClusterCardHit(ClusterCardHitKind.NONE, Optional.empty(), Optional.empty());
    }

    public static ClusterCardHit node(String nodeId) {
        return new ClusterCardHit(ClusterCardHitKind.NODE, Optional.of(nodeId), Optional.empty());
    }

    public static ClusterCardHit edge(String edgeId) {
        return new ClusterCardHit(ClusterCardHitKind.EDGE, Optional.empty(), Optional.of(edgeId));
    }
}
