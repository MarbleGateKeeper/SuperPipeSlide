package dev.marblegate.superpipeslide.common.core.path;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

public final class PipePathfinder {
    private static final double COST_EPSILON = 1.0E-6D;

    private PipePathfinder() {}

    public static PathSearchResult shortestPath(PipeConnection fromConnection, PipeConnection toConnection, PipeGraphSnapshot graph, PathSearchOptions options) {
        List<PipeAnchorId> starts = List.of(fromConnection.fromAnchor(), fromConnection.toAnchor()).stream()
                .filter(anchorId -> allowsExitAt(fromConnection, anchorId))
                .sorted(Comparator.comparing(PipeGraphSnapshot::stableAnchorKey))
                .toList();
        Set<PipeAnchorId> targets = List.of(toConnection.fromAnchor(), toConnection.toAnchor()).stream()
                .filter(anchorId -> allowsEntryFrom(toConnection, anchorId))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (starts.isEmpty() || targets.isEmpty()) {
            return PathSearchResult.broken();
        }

        PriorityQueue<SearchNode> queue = new PriorityQueue<>(SearchNode.ORDER);
        Map<PipeAnchorId, BestState> best = new HashMap<>();
        Map<PipeAnchorId, Step> previous = new HashMap<>();
        for (PipeAnchorId start : starts) {
            BestState state = new BestState(0.0D, PipeGraphSnapshot.stableAnchorKey(start));
            queue.add(new SearchNode(start, state));
            best.put(start, state);
        }

        PipeAnchorId target = null;
        int visited = 0;
        Set<java.util.UUID> excludedConnectionIds = Set.of(fromConnection.id(), toConnection.id());
        while (!queue.isEmpty()) {
            SearchNode current = queue.poll();
            BestState currentBest = best.get(current.anchorId());
            if (currentBest == null || current.state().compareTo(currentBest) > 0) {
                continue;
            }
            if (++visited > options.maxVisitedNodes()) {
                return PathSearchResult.incomplete();
            }
            if (targets.contains(current.anchorId())) {
                target = current.anchorId();
                break;
            }

            for (PipeGraphEdge edge : graph.edgesFrom(current.anchorId(), excludedConnectionIds)) {
                BestState nextState = current.state().append(edge);
                BestState known = best.get(edge.to());
                if (known != null && nextState.compareTo(known) >= 0) {
                    continue;
                }
                best.put(edge.to(), nextState);
                previous.put(edge.to(), new Step(current.anchorId(), edge.connectionRef(), edge.length()));
                queue.add(new SearchNode(edge.to(), nextState));
            }
        }

        if (target == null) {
            return PathSearchResult.broken();
        }

        ArrayDeque<PipeConnectionRef> refs = new ArrayDeque<>();
        double length = 0.0D;
        PipeAnchorId cursor = target;
        while (!starts.contains(cursor)) {
            Step step = previous.get(cursor);
            if (step == null) {
                return PathSearchResult.broken();
            }
            step.connectionRef().ifPresent(refs::addFirst);
            length += step.length();
            cursor = step.previousAnchor();
        }
        refs.addFirst(PipeConnectionRef.of(fromConnection));
        refs.addLast(PipeConnectionRef.of(toConnection));
        return PathSearchResult.valid(new PathSearchResult.Path(List.copyOf(refs), length));
    }

    private static boolean allowsExitAt(PipeConnection connection, PipeAnchorId exitAnchor) {
        if (connection.toAnchor().equals(exitAnchor)) {
            return connection.allowsSlideDirection(1);
        }
        if (connection.fromAnchor().equals(exitAnchor)) {
            return connection.allowsSlideDirection(-1);
        }
        return false;
    }

    private static boolean allowsEntryFrom(PipeConnection connection, PipeAnchorId entryAnchor) {
        if (!connection.fromAnchor().equals(entryAnchor) && !connection.toAnchor().equals(entryAnchor)) {
            return false;
        }
        return connection.allowsSlideDirection(connection.directionAwayFrom(entryAnchor));
    }

    private record BestState(double cost, String tieKey) implements Comparable<BestState> {
        private BestState append(PipeGraphEdge edge) {
            return new BestState(this.cost + edge.cost(), this.tieKey + "|" + edge.stableKey() + ">" + PipeGraphSnapshot.stableAnchorKey(edge.to()));
        }

        @Override
        public int compareTo(BestState other) {
            if (this.cost + COST_EPSILON < other.cost) {
                return -1;
            }
            if (this.cost > other.cost + COST_EPSILON) {
                return 1;
            }
            return this.tieKey.compareTo(other.tieKey);
        }
    }

    private record SearchNode(PipeAnchorId anchorId, BestState state) {
        private static final Comparator<SearchNode> ORDER = Comparator
                .comparing(SearchNode::state)
                .thenComparing(node -> PipeGraphSnapshot.stableAnchorKey(node.anchorId()));
    }

    private record Step(PipeAnchorId previousAnchor, Optional<PipeConnectionRef> connectionRef, double length) {}
}
