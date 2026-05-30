package dev.marblegate.superpipeslide.client.fullmap.card;

import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardState;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardViewMode;
import dev.marblegate.superpipeslide.client.fullmap.routecard.render.RouteLineCardState;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record MapCard(
        CardKind kind,
        UUID id,
        Optional<ResourceKey<Level>> levelKey,
        Optional<RouteLineCardState> routeLineState,
        Optional<ClusterCardState> clusterState) {
    public MapCard {
        levelKey = levelKey == null ? Optional.empty() : levelKey;
        routeLineState = routeLineState == null ? Optional.empty() : routeLineState;
        clusterState = clusterState == null ? Optional.empty() : clusterState;
    }

    public static MapCard routeLine(UUID routeLineId, Optional<UUID> layoutId) {
        return routeLine(routeLineId, layoutId, Optional.empty());
    }

    public static MapCard routeLine(UUID routeLineId, Optional<UUID> layoutId, Optional<ResourceKey<Level>> levelKey) {
        return new MapCard(CardKind.ROUTE_LINE, routeLineId, levelKey, Optional.of(RouteLineCardState.create(routeLineId, layoutId, levelKey)), Optional.empty());
    }

    public static MapCard routeLine(UUID routeLineId, Optional<UUID> layoutId, ResourceKey<Level> levelKey) {
        return routeLine(routeLineId, layoutId, Optional.of(levelKey));
    }

    public static MapCard routeLine(UUID routeLineId, Optional<UUID> layoutId, ResourceKey<Level> levelKey, RouteCardViewMode viewMode) {
        RouteLineCardState state = RouteLineCardState.create(routeLineId, layoutId, Optional.of(levelKey)).withViewMode(viewMode);
        return new MapCard(CardKind.ROUTE_LINE, routeLineId, Optional.of(levelKey), Optional.of(state), Optional.empty());
    }

    public static MapCard cluster(UUID clusterId, ResourceKey<Level> levelKey) {
        return new MapCard(CardKind.CLUSTER, clusterId, Optional.of(levelKey), Optional.empty(), Optional.of(ClusterCardState.create()));
    }

    public static MapCard deepCluster(UUID clusterId, ResourceKey<Level> levelKey) {
        return new MapCard(CardKind.DEEP_CLUSTER, clusterId, Optional.of(levelKey), Optional.empty(), Optional.of(ClusterCardState.create()));
    }

    public static MapCard station(UUID stationId, ResourceKey<Level> levelKey) {
        return new MapCard(CardKind.STATION, stationId, Optional.of(levelKey), Optional.empty(), Optional.empty());
    }

    public static MapCard foldPeek(UUID foldNodeId, ResourceKey<Level> levelKey) {
        return new MapCard(CardKind.FOLD_PEEK, foldNodeId, Optional.of(levelKey), Optional.empty(), Optional.empty());
    }

    public MapCard withRouteLineState(RouteLineCardState state) {
        return new MapCard(this.kind, state.routeLineId(), this.levelKey.or(state::sourceLevelKey), Optional.of(state), Optional.empty());
    }

    public MapCard withClusterState(ClusterCardState state) {
        return new MapCard(this.kind, this.id, this.levelKey, Optional.empty(), Optional.of(state));
    }

    public String windowKey() {
        String level = this.levelKey.map(value -> value.identifier().toString()).orElse("");
        return switch (this.kind) {
            case ROUTE_LINE -> "route:" + this.id;
            default -> this.kind + ":" + level + ":" + this.id;
        };
    }
}
