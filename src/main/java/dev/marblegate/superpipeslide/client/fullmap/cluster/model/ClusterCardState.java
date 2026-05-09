package dev.marblegate.superpipeslide.client.fullmap.cluster.model;

import java.util.Optional;

public record ClusterCardState(Optional<ClusterCardViewport> viewport) {
    public ClusterCardState {
        viewport = viewport == null ? Optional.empty() : viewport;
    }

    public static ClusterCardState create() {
        return new ClusterCardState(Optional.empty());
    }

    public ClusterCardState withViewport(ClusterCardViewport viewport) {
        return new ClusterCardState(Optional.of(viewport));
    }

    public ClusterCardState fitViewport() {
        return new ClusterCardState(Optional.empty());
    }
}
