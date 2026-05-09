package dev.marblegate.superpipeslide.common.core.route.service;

import dev.marblegate.superpipeslide.client.fullmap.model.search.SearchResult;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionUtils;
import dev.marblegate.superpipeslide.common.core.networkgraph.branch.BranchNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkView;
import dev.marblegate.superpipeslide.common.core.path.PipeGraphSnapshot;
import dev.marblegate.superpipeslide.common.core.route.model.decision.RouteBranchDecision;
import dev.marblegate.superpipeslide.common.core.route.model.decision.RouteConnectionStepDecision;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionPath;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RouteSectionService {
    private RouteSectionService() {
    }

    public static ComputedSection computeSection(
            UUID sectionId,
            RouteLayout layout,
            UUID fromPlatformStopId,
            UUID toPlatformStopId,
            PipeNetworkView pipeNetwork,
            PipeGraphSnapshot graph,
            Function<UUID, Optional<PlatformStop>> platformStopLookup
    ) {
        Optional<PlatformStop> from = platformStopLookup.apply(fromPlatformStopId);
        Optional<PlatformStop> to = platformStopLookup.apply(toPlatformStopId);
        if (from.isEmpty() || to.isEmpty()) {
            return brokenSection(sectionId, layout.id(), fromPlatformStopId, toPlatformStopId, pipeNetwork.revision(), layout.bidirectional());
        }
        Optional<PipeConnection> fromConnection = pipeNetwork.connection(from.get().connectionRef());
        Optional<PipeConnection> toConnection = pipeNetwork.connection(to.get().connectionRef());
        if (fromConnection.isEmpty() || toConnection.isEmpty()) {
            return brokenSection(sectionId, layout.id(), fromPlatformStopId, toPlatformStopId, pipeNetwork.revision(), layout.bidirectional());
        }

        RoutePathfinder.SearchResult forward = RoutePathfinder.shortestPathResult(fromConnection.get(), toConnection.get(), graph);
        RoutePathfinder.SearchResult reverse = layout.bidirectional()
                ? RoutePathfinder.shortestPathResult(toConnection.get(), fromConnection.get(), graph)
                : new RoutePathfinder.SearchResult(RouteSectionStatus.DISABLED, Optional.empty());
        List<PipeConnectionRef> forwardConnections = forward.optionalPath().map(RoutePathfinder.PathResult::connectionRefs).orElse(List.of());
        List<PipeConnectionRef> reverseConnections = reverse.optionalPath().map(RoutePathfinder.PathResult::connectionRefs).orElse(List.of());

        RouteSection section = new RouteSection(
                sectionId,
                layout.id(),
                fromPlatformStopId,
                toPlatformStopId,
                forward.status(),
                layout.bidirectional() ? reverse.status() : RouteSectionStatus.DISABLED,
                pipeNetwork.revision(),
                forward.optionalPath().map(RoutePathfinder.PathResult::length).orElse(0.0D),
                reverse.optionalPath().map(RoutePathfinder.PathResult::length).orElse(0.0D),
                branchDecisionsForConnections(forwardConnections, pipeNetwork, 1),
                branchDecisionsForConnections(reverseConnections, pipeNetwork, -1),
                stepDecisionsForConnections(forwardConnections, pipeNetwork, 1),
                stepDecisionsForConnections(reverseConnections, pipeNetwork, -1)
        );
        return new ComputedSection(section, new RouteSectionPath(forwardConnections, reverseConnections));
    }

    private static ComputedSection brokenSection(UUID sectionId, UUID layoutId, UUID fromPlatformStopId, UUID toPlatformStopId, long graphRevision, boolean bidirectional) {
        RouteSection section = new RouteSection(sectionId, layoutId, fromPlatformStopId, toPlatformStopId, RouteSectionStatus.BROKEN, bidirectional ? RouteSectionStatus.BROKEN : RouteSectionStatus.DISABLED, graphRevision, 0.0D, 0.0D, List.of(), List.of(), List.of(), List.of());
        return new ComputedSection(section, new RouteSectionPath(List.of(), List.of()));
    }

    private static List<RouteBranchDecision> branchDecisionsForConnections(List<PipeConnectionRef> connections, PipeNetworkView pipeNetwork, int routeDirection) {
        List<RouteBranchDecision> decisions = new ArrayList<>();
        Set<DecisionKey> seenDecisions = new HashSet<>();
        for (int i = 1; i < connections.size(); i++) {
            PipeConnectionRef previousConnection = connections.get(i - 1);
            PipeConnectionRef connection = connections.get(i);
            UUID previousConnectionId = previousConnection.connectionId();
            UUID connectionId = connection.connectionId();
            pipeNetwork.branchNodeManagingConnection(previousConnectionId)
                    .ifPresent(branchNode -> addBranchDecision(decisions, seenDecisions, branchNode, previousConnection, connection, routeDirection));
            pipeNetwork.branchNodeManagingConnection(connectionId)
                    .ifPresent(branchNode -> addBranchDecision(decisions, seenDecisions, branchNode, previousConnection, connection, routeDirection));

            Optional<PipeConnection> previousPipeConnection = pipeNetwork.connection(previousConnection);
            Optional<PipeConnection> pipeConnection = pipeNetwork.connection(connection);
            if (previousPipeConnection.isPresent() && pipeConnection.isPresent()) {
                PipeConnectionUtils.sharedAnchors(previousPipeConnection.get(), pipeConnection.get()).forEach(anchorId -> pipeNetwork.branchNodeAt(anchorId)
                        .ifPresent(branchNode -> addBranchDecision(decisions, seenDecisions, branchNode, previousConnection, connection, routeDirection)));
            }
        }
        return decisions;
    }

    private static void addBranchDecision(List<RouteBranchDecision> decisions, Set<DecisionKey> seenDecisions, BranchNode branchNode, PipeConnectionRef incomingConnection, PipeConnectionRef selectedConnection, int routeDirection) {
        if (!branchNode.referencesConnection(incomingConnection.connectionId()) || !branchNode.referencesConnection(selectedConnection.connectionId())) {
            return;
        }
        DecisionKey key = new DecisionKey(branchNode.id(), incomingConnection.connectionId(), selectedConnection.connectionId(), routeDirection);
        if (seenDecisions.add(key)) {
            decisions.add(new RouteBranchDecision(branchNode.id(), incomingConnection, selectedConnection, routeDirection));
        }
    }

    private static List<RouteConnectionStepDecision> stepDecisionsForConnections(List<PipeConnectionRef> connections, PipeNetworkView pipeNetwork, int routeDirection) {
        List<RouteConnectionStepDecision> decisions = new ArrayList<>();
        Set<DecisionKey> seenDecisions = new HashSet<>();
        for (int i = 0; i + 1 < connections.size(); i++) {
            PipeConnectionRef incomingConnection = connections.get(i);
            PipeConnectionRef selectedConnection = connections.get(i + 1);
            pipeNetwork.branchNodeManagingConnection(incomingConnection.connectionId())
                    .ifPresent(branchNode -> addStepDecision(decisions, seenDecisions, branchNode, incomingConnection, selectedConnection, routeDirection));
            pipeNetwork.branchNodeManagingConnection(selectedConnection.connectionId())
                    .ifPresent(branchNode -> addStepDecision(decisions, seenDecisions, branchNode, incomingConnection, selectedConnection, routeDirection));

            Optional<PipeConnection> incomingPipeConnection = pipeNetwork.connection(incomingConnection);
            Optional<PipeConnection> selectedPipeConnection = pipeNetwork.connection(selectedConnection);
            if (incomingPipeConnection.isPresent() && selectedPipeConnection.isPresent()) {
                PipeConnectionUtils.sharedAnchors(incomingPipeConnection.get(), selectedPipeConnection.get()).forEach(anchorId -> pipeNetwork.branchNodeAt(anchorId)
                        .ifPresent(branchNode -> addStepDecision(decisions, seenDecisions, branchNode, incomingConnection, selectedConnection, routeDirection)));
            }
        }
        return decisions;
    }

    private static void addStepDecision(List<RouteConnectionStepDecision> decisions, Set<DecisionKey> seenDecisions, BranchNode branchNode, PipeConnectionRef incomingConnection, PipeConnectionRef selectedConnection, int routeDirection) {
        if (!branchNode.referencesConnection(incomingConnection.connectionId()) || !branchNode.referencesConnection(selectedConnection.connectionId())) {
            return;
        }
        DecisionKey key = new DecisionKey(branchNode.id(), incomingConnection.connectionId(), selectedConnection.connectionId(), routeDirection);
        if (seenDecisions.add(key)) {
            decisions.add(new RouteConnectionStepDecision(branchNode.id(), incomingConnection, selectedConnection, routeDirection));
        }
    }

    public record ComputedSection(RouteSection section, RouteSectionPath path) {
    }

    private record DecisionKey(UUID branchNodeId, UUID incomingConnectionId, UUID selectedConnectionId, int routeDirection) {
    }
}

