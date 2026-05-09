package dev.marblegate.superpipeslide.client.fullmap.routecard.hit;


import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNode;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNodeId;
import java.util.Optional;
import java.util.UUID;

public record RouteLineCardHit(RouteLineCardHitKind kind, Optional<RouteCardNodeId> node, Optional<String> edge, Optional<UUID> stationGroupId) {
    public RouteLineCardHit {
        node = node == null ? Optional.empty() : node;
        edge = edge == null ? Optional.empty() : edge;
        stationGroupId = stationGroupId == null ? Optional.empty() : stationGroupId;
    }

    public static RouteLineCardHit none() {
        return new RouteLineCardHit(RouteLineCardHitKind.NONE, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static RouteLineCardHit node(RouteCardNodeId nodeId) {
        return new RouteLineCardHit(RouteLineCardHitKind.NODE, Optional.of(nodeId), Optional.empty(), Optional.empty());
    }

    public static RouteLineCardHit node(RouteCardNode node) {
        return new RouteLineCardHit(RouteLineCardHitKind.NODE, Optional.of(node.id()), Optional.empty(), node.stationGroupId());
    }

    public static RouteLineCardHit edge(String edgeId) {
        return new RouteLineCardHit(RouteLineCardHitKind.EDGE, Optional.empty(), Optional.of(edgeId), Optional.empty());
    }
}
