package dev.marblegate.superpipeslide.client.fullmap.model.hit;

import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import java.util.Objects;

/**
 * What the mouse cursor currently hovers over on the full route map. Each subtype
 * carries exactly the id of the hovered element, so consumers dispatch with pattern
 * matching instead of switching on a kind tag and then unpacking the matching slot.
 */
public sealed interface HitTarget {
    static HitTarget none() {
        return None.INSTANCE;
    }

    static HitTarget node(NodeId nodeId) {
        return new NodeHit(nodeId);
    }

    static HitTarget edge(String edgeId) {
        return new EdgeHit(edgeId);
    }

    static HitTarget transferHint(String transferHintId) {
        return new TransferHintHit(transferHintId);
    }

    static HitTarget missingCrossDimensionPath(String hintId) {
        return new MissingCrossDimensionPathHit(hintId);
    }

    static HitTarget physicalNode(String nodeId) {
        return new PhysicalNodeHit(nodeId);
    }

    static HitTarget physicalEdge(String edgeId) {
        return new PhysicalEdgeHit(edgeId);
    }

    /** Nothing under the cursor. */
    enum None implements HitTarget {
        INSTANCE
    }

    /** A node of the visual (schematic or geographic) route map graph. */
    record NodeHit(NodeId nodeId) implements HitTarget {
        public NodeHit {
            Objects.requireNonNull(nodeId, "nodeId");
        }
    }

    /** An edge of the visual route map graph. */
    record EdgeHit(String edgeId) implements HitTarget {
        public EdgeHit {
            Objects.requireNonNull(edgeId, "edgeId");
        }
    }

    /** A walking-transfer hint between two nearby stations. */
    record TransferHintHit(String hintId) implements HitTarget {
        public TransferHintHit {
            Objects.requireNonNull(hintId, "hintId");
        }
    }

    /** A hint marking a route that cannot continue because it crosses into another dimension. */
    record MissingCrossDimensionPathHit(String hintId) implements HitTarget {
        public MissingCrossDimensionPathHit {
            Objects.requireNonNull(hintId, "hintId");
        }
    }

    /** A node of the physical (track layout) route map graph. */
    record PhysicalNodeHit(String nodeId) implements HitTarget {
        public PhysicalNodeHit {
            Objects.requireNonNull(nodeId, "nodeId");
        }
    }

    /** An edge of the physical route map graph. */
    record PhysicalEdgeHit(String edgeId) implements HitTarget {
        public PhysicalEdgeHit {
            Objects.requireNonNull(edgeId, "edgeId");
        }
    }
}
