package dev.marblegate.superpipeslide.common.core.route.service;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import dev.marblegate.superpipeslide.common.core.networkgraph.model.PipeNetworkChangeSet;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkView;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;
import dev.marblegate.superpipeslide.common.core.route.storage.RouteNetworkSavedData;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RouteInvalidationService {
    private static final int REPAIR_COMPONENT_SEARCH_LIMIT = 512;

    private RouteInvalidationService() {}

    public static boolean apply(PipeNetworkChangeSet changes, RouteNetworkSavedData routes, PipeNetworkView pipeNetwork) {
        if (changes.isEmpty() || !changes.mayInvalidateRouteSections()) {
            return false;
        }

        boolean changed = false;
        Set<UUID> directlyAffectedSectionIds = new LinkedHashSet<>();
        changes.addedOrUpdatedConnectionIds().stream()
                .map(pipeNetwork::connection)
                .flatMap(java.util.Optional::stream)
                .map(PipeConnectionRef::of)
                .map(routes::routeSectionIdsReferencingConnection)
                .forEach(directlyAffectedSectionIds::addAll);
        changes.removedConnectionRefs().stream()
                .map(routes::routeSectionIdsReferencingConnection)
                .forEach(directlyAffectedSectionIds::addAll);

        for (UUID sectionId : directlyAffectedSectionIds) {
            changed |= routes.markSectionStale(sectionId);
        }

        boolean foldTopologyMayHaveChanged = !changes.removedNodeIds().isEmpty()
                || changes.addedOrUpdatedNodeIds().stream()
                        .anyMatch(anchorId -> pipeNetwork.foldAnchorAt(anchorId).isPresent());
        if (foldTopologyMayHaveChanged) {
            Set<PipeAnchorId> affectedFoldAnchors = new LinkedHashSet<>();
            affectedFoldAnchors.addAll(changes.removedNodeIds());
            changes.addedOrUpdatedNodeIds().stream()
                    .filter(anchorId -> pipeNetwork.foldAnchorAt(anchorId).isPresent())
                    .forEach(affectedFoldAnchors::add);
            List<PipeAnchorId> anchorsWithCounterparts = new ArrayList<>(affectedFoldAnchors);
            for (PipeAnchorId anchorId : anchorsWithCounterparts) {
                pipeNetwork.localFoldCounterpart(anchorId).ifPresent(affectedFoldAnchors::add);
            }
            for (UUID sectionId : routes.routeSectionIdsNearAnchors(affectedFoldAnchors, pipeNetwork)) {
                changed |= routes.markSectionStale(sectionId);
            }
        }

        if (changes.mayRepairBrokenRouteSections()) {
            Set<PipeAnchorId> changedAnchors = new LinkedHashSet<>(changes.addedOrUpdatedNodeIds());
            changes.addedOrUpdatedConnectionIds().stream()
                    .map(pipeNetwork::connection)
                    .flatMap(java.util.Optional::stream)
                    .forEach(connection -> {
                        changedAnchors.add(connection.fromAnchor());
                        changedAnchors.add(connection.toAnchor());
                    });

            for (UUID sectionId : routes.routeSectionIdsNearAnchors(changedAnchors, pipeNetwork)) {
                routes.markSectionDirty(sectionId);
            }

            for (RouteSection section : routes.routeSections()) {
                Optional<RouteLayout> layout = routes.routeLayout(section.routeLayoutId());
                if (layout.isPresent() && section.statusForLayout(layout.get()) == RouteSectionStatus.VALID) {
                    continue;
                }
                if (sectionMayBeRepairedByChangedAnchors(section, routes, pipeNetwork, changedAnchors)) {
                    routes.enqueueSectionRepair(section.id(), changes.revision());
                }
            }
        }

        return changed;
    }

    private static boolean sectionMayBeRepairedByChangedAnchors(RouteSection section, RouteNetworkSavedData routes, PipeNetworkView pipeNetwork, Set<PipeAnchorId> changedAnchors) {
        Optional<PlatformStop> fromStop = routes.platformStop(section.fromPlatformStopId());
        Optional<PlatformStop> toStop = routes.platformStop(section.toPlatformStopId());
        if (fromStop.isEmpty() || toStop.isEmpty()) {
            return false;
        }
        Optional<PipeConnection> fromConnection = pipeNetwork.connection(fromStop.get().connectionRef());
        Optional<PipeConnection> toConnection = pipeNetwork.connection(toStop.get().connectionRef());
        if (fromConnection.isEmpty() || toConnection.isEmpty()) {
            return true;
        }

        ComponentHit fromHit = reachesChangedAnchor(fromConnection.get(), changedAnchors, pipeNetwork);
        ComponentHit toHit = reachesChangedAnchor(toConnection.get(), changedAnchors, pipeNetwork);
        return fromHit == ComponentHit.REACHES_CHANGED && toHit == ComponentHit.REACHES_CHANGED
                || fromHit == ComponentHit.SEARCH_INCOMPLETE
                || toHit == ComponentHit.SEARCH_INCOMPLETE;
    }

    private static ComponentHit reachesChangedAnchor(PipeConnection endpointConnection, Set<PipeAnchorId> changedAnchors, PipeNetworkView pipeNetwork) {
        ArrayDeque<PipeAnchorId> queue = new ArrayDeque<>();
        Set<PipeAnchorId> visited = new HashSet<>();
        queue.add(endpointConnection.fromAnchor());
        queue.add(endpointConnection.toAnchor());
        while (!queue.isEmpty()) {
            PipeAnchorId anchor = queue.removeFirst();
            if (!visited.add(anchor)) {
                continue;
            }
            if (changedAnchors.contains(anchor)) {
                return ComponentHit.REACHES_CHANGED;
            }
            if (visited.size() > REPAIR_COMPONENT_SEARCH_LIMIT) {
                return ComponentHit.SEARCH_INCOMPLETE;
            }

            pipeNetwork.localFoldCounterpart(anchor).ifPresent(queue::addLast);
            for (PipeConnection connection : pipeNetwork.connectionsTouching(anchor)) {
                if (connection.fromAnchor().equals(anchor)) {
                    queue.addLast(connection.toAnchor());
                } else if (connection.toAnchor().equals(anchor)) {
                    queue.addLast(connection.fromAnchor());
                }
            }
        }
        return ComponentHit.DOES_NOT_REACH_CHANGED;
    }

    private enum ComponentHit {
        REACHES_CHANGED,
        DOES_NOT_REACH_CHANGED,
        SEARCH_INCOMPLETE
    }
}
