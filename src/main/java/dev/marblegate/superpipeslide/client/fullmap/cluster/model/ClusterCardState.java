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

    /**
     * Clears any custom viewport so the next frame re-fits the card contents; named
     * "reset" rather than "fit" because the fit is computed by the renderer, not here.
     */
    public ClusterCardState resetViewport() {
        return new ClusterCardState(Optional.empty());
    }
}
