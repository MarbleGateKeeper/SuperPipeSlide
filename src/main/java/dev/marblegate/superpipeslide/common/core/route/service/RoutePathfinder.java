package dev.marblegate.superpipeslide.common.core.route.service;


import dev.marblegate.superpipeslide.client.fullmap.model.search.SearchResult;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkView;
import dev.marblegate.superpipeslide.common.core.path.PathSearchOptions;
import dev.marblegate.superpipeslide.common.core.path.PathSearchResult;
import dev.marblegate.superpipeslide.common.core.path.PipeGraphSnapshot;
import dev.marblegate.superpipeslide.common.core.path.PipePathfinder;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;
import dev.marblegate.superpipeslide.config.Config;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class RoutePathfinder {
    private RoutePathfinder() {
    }

    public static Optional<PathResult> shortestPath(PipeConnection fromConnection, PipeConnection toConnection, PipeNetworkView pipeNetwork) {
        SearchResult result = shortestPathResult(fromConnection, toConnection, pipeNetwork, configuredMaxVisitedNodes());
        return result.status() == RouteSectionStatus.VALID ? Optional.of(result.path()) : Optional.empty();
    }

    public static SearchResult shortestPathResult(PipeConnection fromConnection, PipeConnection toConnection, PipeNetworkView pipeNetwork) {
        return shortestPathResult(fromConnection, toConnection, pipeNetwork, configuredMaxVisitedNodes());
    }

    public static SearchResult shortestPathResult(PipeConnection fromConnection, PipeConnection toConnection, PipeNetworkView pipeNetwork, int maxVisitedNodes) {
        return shortestPathResult(fromConnection, toConnection, PipeGraphSnapshot.of(pipeNetwork), maxVisitedNodes);
    }

    public static SearchResult shortestPathResult(PipeConnection fromConnection, PipeConnection toConnection, PipeGraphSnapshot graph) {
        return shortestPathResult(fromConnection, toConnection, graph, configuredMaxVisitedNodes());
    }

    public static SearchResult shortestPathResult(PipeConnection fromConnection, PipeConnection toConnection, PipeGraphSnapshot graph, int maxVisitedNodes) {
        return fromPathSearchResult(PipePathfinder.shortestPath(
                fromConnection,
                toConnection,
                graph,
                new PathSearchOptions(maxVisitedNodes)
        ));
    }

    private static SearchResult fromPathSearchResult(PathSearchResult result) {
        return switch (result.status()) {
            case VALID -> SearchResult.valid(new PathResult(result.path().connectionRefs(), result.path().length()));
            case BROKEN -> SearchResult.broken();
            case INCOMPLETE -> SearchResult.incomplete();
        };
    }

    private static int configuredMaxVisitedNodes() {
        return Config.ROUTE_PATHFINDER_MAX_VISITED_NODES.getAsInt();
    }

    public record PathResult(List<PipeConnectionRef> connectionRefs, double length) {
        public PathResult {
            connectionRefs = List.copyOf(connectionRefs);
            length = Math.max(0.0D, length);
        }

        public List<UUID> connectionIds() {
            return this.connectionRefs.stream().map(PipeConnectionRef::connectionId).toList();
        }
    }

    public record SearchResult(RouteSectionStatus status, Optional<PathResult> optionalPath) {
        public static SearchResult valid(PathResult path) {
            return new SearchResult(RouteSectionStatus.VALID, Optional.of(path));
        }

        public static SearchResult broken() {
            return new SearchResult(RouteSectionStatus.BROKEN, Optional.empty());
        }

        public static SearchResult incomplete() {
            return new SearchResult(RouteSectionStatus.INCOMPLETE, Optional.empty());
        }

        public PathResult path() {
            return this.optionalPath.orElseThrow();
        }
    }
}
