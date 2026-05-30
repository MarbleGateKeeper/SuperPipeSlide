package dev.marblegate.superpipeslide.client.fullmap.routecard.semantic;

import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.routecard.diagnostic.RouteCardDiagnostic;
import dev.marblegate.superpipeslide.client.fullmap.routecard.diagnostic.RouteCardDiagnosticKind;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardEdge;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNode;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNodeId;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNodeKind;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardSegment;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardSemanticGraph;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardSummary;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SemanticEdgeKind;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorKind;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorNode;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionPath;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class RouteCardSemanticBuilder {
    private final Map<UUID, Integer> stationTotalOccurrences = new LinkedHashMap<>();
    private final Map<UUID, Integer> stationSeenOccurrences = new LinkedHashMap<>();
    private final Map<RouteCardNodeId, RouteCardNode> nodes = new LinkedHashMap<>();
    private final List<RouteCardEdge> edges = new ArrayList<>();
    private final Map<Integer, SegmentAccumulator> segments = new LinkedHashMap<>();
    private final List<RouteCardDiagnostic> diagnostics = new ArrayList<>();
    private List<Integer> themeColors = List.of(FullRouteMapConfig.MAP_TRUNK);
    private int crossDimensionCount;
    private int stationInternalCount;
    private int currentSegmentIndex;

    public RouteCardSemanticGraph build(RouteLine line, RouteLayout layout) {
        return this.build(line, layout, false);
    }

    public RouteCardSemanticGraph buildPlatform(RouteLine line, RouteLayout layout) {
        return this.build(line, layout, true);
    }

    public RouteCardSemanticGraph buildSchematic(RouteLine line, RouteLayout layout) {
        this.stationTotalOccurrences.clear();
        this.stationSeenOccurrences.clear();
        this.nodes.clear();
        this.edges.clear();
        this.segments.clear();
        this.diagnostics.clear();
        this.crossDimensionCount = 0;
        this.stationInternalCount = 0;
        this.currentSegmentIndex = 0;
        this.themeColors = line.themeColors().stream().map(SPSGui::opaque).limit(3).toList();
        if (this.themeColors.isEmpty()) {
            this.themeColors = List.of(FullRouteMapConfig.MAP_TRUNK);
        }

        List<RouteCardNode> stopNodes = this.buildStopNodes(layout, false);
        if (!stopNodes.isEmpty()) {
            this.startSegment(0, stopNodes.getFirst().levelKey());
            this.addNodeToCurrentSegment(stopNodes.getFirst());
        }
        this.buildSchematicLayoutEdges(layout, stopNodes);
        this.mergeLoopTailSegmentIntoHead(layout, stopNodes);

        int stationCount = (int) this.nodes.values().stream()
                .flatMap(node -> node.stationGroupId().stream())
                .distinct()
                .count();
        RouteCardSummary summary = new RouteCardSummary(
                stationCount,
                stopNodes.size(),
                layout.loop() ? stopNodes.size() : Math.max(0, stopNodes.size() - 1),
                this.crossDimensionCount,
                this.stationInternalCount,
                layout.bidirectional(),
                layout.loop());
        return new RouteCardSemanticGraph(line.id(), layout.id(), this.nodes.values().stream().toList(), this.edges, this.buildSegments(), this.diagnostics, summary);
    }

    private RouteCardSemanticGraph build(RouteLine line, RouteLayout layout, boolean platformLevel) {
        this.stationTotalOccurrences.clear();
        this.stationSeenOccurrences.clear();
        this.nodes.clear();
        this.edges.clear();
        this.segments.clear();
        this.diagnostics.clear();
        this.crossDimensionCount = 0;
        this.stationInternalCount = 0;
        this.currentSegmentIndex = 0;
        this.themeColors = line.themeColors().stream().map(SPSGui::opaque).limit(3).toList();
        if (this.themeColors.isEmpty()) {
            this.themeColors = List.of(FullRouteMapConfig.MAP_TRUNK);
        }

        List<RouteCardNode> stopNodes = this.buildStopNodes(layout, platformLevel);
        if (!stopNodes.isEmpty()) {
            this.startSegment(0, stopNodes.getFirst().levelKey());
            this.addNodeToCurrentSegment(stopNodes.getFirst());
        }
        this.buildLayoutEdges(layout, stopNodes);
        this.mergeLoopTailSegmentIntoHead(layout, stopNodes);

        int stationCount = (int) this.nodes.values().stream()
                .flatMap(node -> node.stationGroupId().stream())
                .distinct()
                .count();
        RouteCardSummary summary = new RouteCardSummary(
                stationCount,
                stopNodes.size(),
                this.edges.stream().map(RouteCardEdge::routeSectionId).distinct().toList().size(),
                this.crossDimensionCount,
                this.stationInternalCount,
                layout.bidirectional(),
                layout.loop());
        return new RouteCardSemanticGraph(line.id(), layout.id(), this.nodes.values().stream().toList(), this.edges, this.buildSegments(), this.diagnostics, summary);
    }

    private void buildSchematicLayoutEdges(RouteLayout layout, List<RouteCardNode> stopNodes) {
        if (stopNodes.size() < 2) {
            return;
        }
        int sectionCount = layout.loop() ? stopNodes.size() : Math.max(0, stopNodes.size() - 1);
        for (int index = 0; index < sectionCount; index++) {
            RouteCardNode from = stopNodes.get(index);
            RouteCardNode to = stopNodes.get((index + 1) % stopNodes.size());
            boolean loopBack = this.isLoopBack(layout, stopNodes, index);
            SemanticEdgeKind kind = this.edgeKind(from, to, loopBack);
            if (kind == SemanticEdgeKind.STATION_INTERNAL && from.stationGroupId().equals(to.stationGroupId()) && stopNodes.size() <= 1) {
                continue;
            }
            if (kind == SemanticEdgeKind.STATION_INTERNAL) {
                this.stationInternalCount++;
            }
            int sectionIndex = index;
            UUID sectionId = this.sectionIdFor(layout, sectionIndex).orElseGet(() -> stableUuid("route-card-schematic-section:" + layout.id() + ":" + sectionIndex));
            if (!from.levelKey().equals(to.levelKey())) {
                this.addSchematicPortalBoundaryEdges(layout, sectionId, index, from, to, statusForSchematicSection(sectionId), kind, loopBack);
                continue;
            }
            this.addNodeToCurrentSegment(from);
            this.addNodeToCurrentSegment(to);
            this.addEdge(layout, sectionId, index, this.currentSegmentIndex, from.id(), to.id(), kind, RouteSectionStatus.VALID, loopBack || kind == SemanticEdgeKind.LOOP_BACK, List.of());
        }
    }

    private void addSchematicPortalBoundaryEdges(RouteLayout layout, UUID sectionId, int layoutIndex, RouteCardNode from, RouteCardNode to, RouteSectionStatus status, SemanticEdgeKind baseKind, boolean loopBack) {
        this.crossDimensionCount++;
        RouteCardNode fromBoundary = this.ensureSchematicPortalBoundary(from, to, sectionId, layoutIndex, 0);
        RouteCardNode toBoundary = this.ensureSchematicPortalBoundary(to, from, sectionId, layoutIndex, 1);
        this.addNodeToCurrentSegment(from);
        this.addNodeToCurrentSegment(fromBoundary);
        this.addEdge(layout, sectionId, layoutIndex, this.currentSegmentIndex, from.id(), fromBoundary.id(), SemanticEdgeKind.FOLD_ADJACENT, status, loopBack || baseKind == SemanticEdgeKind.LOOP_BACK, List.of());
        this.addEdge(layout, sectionId, layoutIndex, -1, fromBoundary.id(), toBoundary.id(), SemanticEdgeKind.FOLD_ADJACENT, status, false, List.of());
        this.startNextSegment(to.levelKey());
        this.addNodeToCurrentSegment(toBoundary);
        this.addNodeToCurrentSegment(to);
        this.addEdge(layout, sectionId, layoutIndex, this.currentSegmentIndex, toBoundary.id(), to.id(), SemanticEdgeKind.FOLD_ADJACENT, status, loopBack || baseKind == SemanticEdgeKind.LOOP_BACK, List.of());
    }

    private static RouteSectionStatus statusForSchematicSection(UUID sectionId) {
        return ClientRouteDataCache.routeSection(sectionId)
                .map(RouteSection::forwardStatus)
                .orElse(RouteSectionStatus.VALID);
    }

    private List<RouteCardNode> buildStopNodes(RouteLayout layout, boolean platformLevel) {
        for (UUID platformStopId : layout.orderedPlatformStops()) {
            ClientRouteDataCache.platformStop(platformStopId).ifPresent(platformStop -> {
                UUID occurrenceKey = platformLevel ? platformStop.id() : platformStop.stationGroupId();
                this.stationTotalOccurrences.merge(occurrenceKey, 1, Integer::sum);
            });
        }

        List<RouteCardNode> stopNodes = new ArrayList<>();
        for (int index = 0; index < layout.orderedPlatformStops().size(); index++) {
            UUID platformStopId = layout.orderedPlatformStops().get(index);
            PlatformStop platformStop = ClientRouteDataCache.platformStop(platformStopId).orElse(null);
            if (platformStop == null) {
                this.diagnostics.add(new RouteCardDiagnostic(RouteCardDiagnosticKind.MISSING_PLATFORM_STOP, "Layout " + layout.id() + " references missing platform stop " + platformStopId));
                continue;
            }
            StationGroup station = ClientRouteDataCache.stationGroup(platformStop.stationGroupId()).orElse(null);
            if (station == null) {
                this.diagnostics.add(new RouteCardDiagnostic(RouteCardDiagnosticKind.MISSING_STATION_GROUP, "Platform stop " + platformStopId + " references missing station " + platformStop.stationGroupId()));
                continue;
            }
            UUID occurrenceKey = platformLevel ? platformStop.id() : station.id();
            int occurrence = this.stationSeenOccurrences.merge(occurrenceKey, 1, Integer::sum) - 1;
            int nodeOccurrence = this.stationTotalOccurrences.getOrDefault(occurrenceKey, 0) > 1 ? occurrence : 0;
            UUID nodePrimaryId = platformLevel ? platformStop.id() : station.id();
            RouteCardNodeId nodeId = new RouteCardNodeId(RouteCardNodeKind.STATION, station.levelKey(), nodePrimaryId, nodeOccurrence);
            NodePosition position = this.platformPosition(station, platformStop, index, nodeOccurrence);
            RouteCardNode node = new RouteCardNode(
                    nodeId,
                    Optional.of(station.id()),
                    Optional.of(platformStop.id()),
                    index,
                    RouteCardNodeKind.STATION,
                    station.levelKey(),
                    position.x(),
                    position.z(),
                    position.y(),
                    platformLevel ? platformLabel(station, platformStop, nodeOccurrence) : stationLabel(station, nodeOccurrence),
                    routeLineIdsForStation(station.id()));
            this.nodes.putIfAbsent(nodeId, node);
            stopNodes.add(node);
        }
        return stopNodes;
    }

    private void buildLayoutEdges(RouteLayout layout, List<RouteCardNode> stopNodes) {
        if (stopNodes.size() < 2) {
            return;
        }
        int sectionCount = layout.loop() ? stopNodes.size() : Math.max(0, stopNodes.size() - 1);
        for (int index = 0; index < sectionCount; index++) {
            RouteCardNode from = stopNodes.get(index);
            RouteCardNode to = stopNodes.get((index + 1) % stopNodes.size());
            Optional<UUID> resolvedSectionId = this.sectionIdFor(layout, index);
            if (resolvedSectionId.isEmpty()) {
                UUID missingId = stableUuid("missing-route-card-section:" + layout.id() + ":" + index);
                this.diagnostics.add(new RouteCardDiagnostic(RouteCardDiagnosticKind.MISSING_ROUTE_SECTION, "Layout " + layout.id() + " has no section for stop edge " + index));
                this.addMissingPathBoundaryEdges(layout, missingId, index, from, to, RouteSectionStatus.BROKEN);
                continue;
            }
            UUID sectionId = resolvedSectionId.get();
            RouteSection section = ClientRouteDataCache.routeSection(sectionId).orElse(null);
            boolean loopBack = this.isLoopBack(layout, stopNodes, index);
            if (section == null) {
                this.diagnostics.add(new RouteCardDiagnostic(RouteCardDiagnosticKind.MISSING_ROUTE_SECTION, "Layout " + layout.id() + " references missing section " + sectionId));
                this.addMissingPathBoundaryEdges(layout, sectionId, index, from, to, RouteSectionStatus.BROKEN);
                continue;
            }
            RouteSectionPath path = ClientRouteDataCache.routeSectionPath(sectionId).orElse(null);
            RouteSectionStatus status = section.statusForLayout(layout);
            SemanticEdgeKind kind = this.edgeKind(from, to, loopBack);
            if (kind == SemanticEdgeKind.STATION_INTERNAL) {
                this.stationInternalCount++;
                this.addFallbackEdge(layout, sectionId, index, from, to, status, kind, List.of(), this.currentSegmentIndex, loopBack);
                continue;
            }
            if (path == null || path.forwardConnections().isEmpty()) {
                this.diagnostics.add(new RouteCardDiagnostic(RouteCardDiagnosticKind.MISSING_SECTION_PATH, "Section " + sectionId + " has no preview path"));
                this.addMissingPathBoundaryEdges(layout, sectionId, index, from, to, status);
                continue;
            }
            this.buildPathEdges(layout, sectionId, index, from, to, status, path.forwardConnections(), kind, loopBack);
        }
    }

    private Optional<UUID> sectionIdFor(RouteLayout layout, int index) {
        if (index < layout.orderedSectionRefs().size()) {
            return Optional.of(layout.orderedSectionRefs().get(index).routeSectionId());
        }
        List<UUID> stops = layout.orderedPlatformStops();
        if (!layout.loop() || index >= stops.size() || stops.size() < 2) {
            return Optional.empty();
        }
        UUID fromPlatformStopId = stops.get(index);
        UUID toPlatformStopId = stops.get((index + 1) % stops.size());
        return ClientRouteDataCache.routeSectionForLayoutEndpoints(layout.id(), fromPlatformStopId, toPlatformStopId)
                .map(RouteSection::id);
    }

    private SemanticEdgeKind edgeKind(RouteCardNode from, RouteCardNode to, boolean loopBack) {
        if (from.stationGroupId().isPresent() && from.stationGroupId().equals(to.stationGroupId())) {
            return SemanticEdgeKind.STATION_INTERNAL;
        }
        if (loopBack) {
            return SemanticEdgeKind.LOOP_BACK;
        }
        return SemanticEdgeKind.NORMAL;
    }

    private boolean isLoopBack(RouteLayout layout, List<RouteCardNode> stopNodes, int sectionIndex) {
        if (stopNodes.size() < 3) {
            return false;
        }
        RouteCardNode from = stopNodes.get(sectionIndex);
        RouteCardNode to = stopNodes.get((sectionIndex + 1) % stopNodes.size());
        RouteCardNode next = stopNodes.get((sectionIndex + 2) % stopNodes.size());
        RouteCardNode previous = stopNodes.get(Math.floorMod(sectionIndex - 1, stopNodes.size()));
        return sameStation(from, next) || sameStation(to, previous);
    }

    private static boolean sameStation(RouteCardNode first, RouteCardNode second) {
        return first.stationGroupId().isPresent() && first.stationGroupId().equals(second.stationGroupId());
    }

    private void buildPathEdges(RouteLayout layout, UUID sectionId, int layoutIndex, RouteCardNode from, RouteCardNode to, RouteSectionStatus status, List<PipeConnectionRef> pathRefs, SemanticEdgeKind baseKind, boolean loopBack) {
        List<PathFoldEvent> foldEvents = new ArrayList<>();
        List<PipeConnectionRef> slice = new ArrayList<>();
        for (int i = 0; i < pathRefs.size(); i++) {
            PipeConnectionRef ref = pathRefs.get(i);
            slice.add(ref);
            if (i + 1 >= pathRefs.size()) {
                continue;
            }
            PipeConnection currentConnection = ClientPipeNetworkCache.connection(ref).orElse(null);
            PipeConnection nextConnection = ClientPipeNetworkCache.connection(pathRefs.get(i + 1)).orElse(null);
            if (currentConnection == null || nextConnection == null) {
                this.diagnostics.add(new RouteCardDiagnostic(RouteCardDiagnosticKind.MISSING_PIPE_CONNECTION, "Section " + sectionId + " references missing pipe connection"));
                continue;
            }
            Optional<FoldTransition> transition = this.findFoldTransition(currentConnection, nextConnection);
            if (transition.isEmpty()) {
                if (!currentConnection.levelKey().equals(nextConnection.levelKey())) {
                    this.diagnostics.add(new RouteCardDiagnostic(RouteCardDiagnosticKind.CROSS_DIMENSION_WITHOUT_FOLD, "Section " + sectionId + " crosses dimensions without a fold anchor"));
                }
                continue;
            }
            FoldTransition fold = transition.get();
            if (!fold.localAnchor().levelKey().equals(fold.peerAnchor().levelKey())) {
                this.crossDimensionCount++;
            }
            foldEvents.add(new PathFoldEvent(fold.localAnchor(), fold.peerAnchor(), List.copyOf(slice)));
            slice = new ArrayList<>();
        }
        List<PipeConnectionRef> finalSlice = List.copyOf(slice);
        if (foldEvents.size() <= 2 && !this.hasMultipleSegmentBreakpoints(foldEvents)) {
            RouteCardNode currentNode = from;
            this.addNodeToCurrentSegment(from);
            for (int i = 0; i < foldEvents.size(); i++) {
                PathFoldEvent event = foldEvents.get(i);
                RouteCardNode localFold = this.ensureFoldBoundary(event.localAnchor(), layoutIndex * 8 + i * 2);
                RouteCardNode peerFold = this.ensureFoldBoundary(event.peerAnchor(), layoutIndex * 8 + i * 2 + 1);
                this.addNodeToCurrentSegment(localFold);
                this.addEdge(layout, sectionId, layoutIndex, this.currentSegmentIndex, currentNode.id(), localFold.id(), SemanticEdgeKind.FOLD_ADJACENT, status, loopBack || baseKind == SemanticEdgeKind.LOOP_BACK, event.slice());
                boolean breakpoint = this.isSegmentBreakpoint(event);
                this.addEdge(layout, sectionId, layoutIndex, breakpoint ? -1 : this.currentSegmentIndex, localFold.id(), peerFold.id(), SemanticEdgeKind.FOLD_ADJACENT, status, false, List.of());
                if (breakpoint) {
                    this.startNextSegment(peerFold.levelKey());
                }
                this.addNodeToCurrentSegment(peerFold);
                currentNode = peerFold;
            }
            this.addNodeToCurrentSegment(to);
            this.addEdge(layout, sectionId, layoutIndex, this.currentSegmentIndex, currentNode.id(), to.id(), baseKind, status, loopBack || baseKind == SemanticEdgeKind.LOOP_BACK, finalSlice);
            return;
        }

        PathFoldEvent first = foldEvents.getFirst();
        PathFoldEvent last = foldEvents.getLast();
        RouteCardNode firstLocal = this.ensureFoldBoundary(first.localAnchor(), layoutIndex * 8);
        RouteCardNode lastPeer = this.ensureFoldBoundary(last.peerAnchor(), layoutIndex * 8 + 1);
        this.addNodeToCurrentSegment(from);
        this.addNodeToCurrentSegment(firstLocal);
        this.addEdge(layout, sectionId, layoutIndex, this.currentSegmentIndex, from.id(), firstLocal.id(), SemanticEdgeKind.FOLD_ADJACENT, status, loopBack || baseKind == SemanticEdgeKind.LOOP_BACK, first.slice());

        this.addEdge(layout, sectionId, layoutIndex, -1, firstLocal.id(), lastPeer.id(), SemanticEdgeKind.FOLD_ADJACENT, status, false, List.of());
        this.startNextSegment(lastPeer.levelKey());
        this.addNodeToCurrentSegment(lastPeer);
        this.addNodeToCurrentSegment(to);
        this.addEdge(layout, sectionId, layoutIndex, this.currentSegmentIndex, lastPeer.id(), to.id(), baseKind, status, loopBack || baseKind == SemanticEdgeKind.LOOP_BACK, finalSlice);
    }

    private Optional<FoldTransition> findFoldTransition(PipeConnection currentConnection, PipeConnection nextConnection) {
        List<PipeAnchorId> currentAnchors = List.of(currentConnection.fromAnchor(), currentConnection.toAnchor());
        List<PipeAnchorId> nextAnchors = List.of(nextConnection.fromAnchor(), nextConnection.toAnchor());
        for (PipeAnchorId currentAnchor : currentAnchors) {
            if (nextAnchors.contains(currentAnchor)) {
                return Optional.empty();
            }
        }
        for (PipeAnchorId currentAnchor : currentAnchors) {
            for (PipeAnchorId nextAnchor : nextAnchors) {
                if (ClientPipeNetworkCache.globalFoldCounterpart(currentAnchor).filter(nextAnchor::equals).isPresent()) {
                    return Optional.of(new FoldTransition(currentAnchor, nextAnchor));
                }
                if (ClientPipeNetworkCache.globalFoldCounterpart(nextAnchor).filter(currentAnchor::equals).isPresent()) {
                    return Optional.of(new FoldTransition(currentAnchor, nextAnchor));
                }
            }
        }
        return Optional.empty();
    }

    private RouteCardNode ensureFoldBoundary(PipeAnchorId anchorId, int occurrence) {
        UUID primaryId = stableUuid("route-card-fold-boundary:" + anchorId.levelKey().identifier() + ":" + anchorId.blockPos().asLong() + ":" + occurrence);
        RouteCardNodeId nodeId = new RouteCardNodeId(RouteCardNodeKind.FOLD_BOUNDARY, anchorId.levelKey(), primaryId, occurrence);
        return this.nodes.computeIfAbsent(nodeId, ignored -> new RouteCardNode(
                nodeId,
                Optional.empty(),
                Optional.empty(),
                occurrence,
                RouteCardNodeKind.FOLD_BOUNDARY,
                anchorId.levelKey(),
                anchorId.blockPos().getX() + 0.5D,
                anchorId.blockPos().getZ() + 0.5D,
                anchorId.blockPos().getY() + 0.5D,
                "Fold boundary",
                List.of()));
    }

    private RouteCardNode ensureSchematicPortalBoundary(RouteCardNode station, RouteCardNode target, UUID sectionId, int layoutIndex, int side) {
        int occurrence = layoutIndex * 8 + 2 + side;
        UUID primaryId = stableUuid("route-card-schematic-portal-boundary:" + sectionId + ":" + station.id() + ":" + target.levelKey().identifier() + ":" + side);
        RouteCardNodeId nodeId = new RouteCardNodeId(RouteCardNodeKind.PORTAL_BOUNDARY, station.levelKey(), primaryId, occurrence);
        Vec2 direction = directionBetween(station, target, sectionId, side);
        double distance = 34.0D;
        return this.nodes.computeIfAbsent(nodeId, ignored -> new RouteCardNode(
                nodeId,
                Optional.empty(),
                Optional.empty(),
                occurrence,
                RouteCardNodeKind.PORTAL_BOUNDARY,
                station.levelKey(),
                station.worldX() + direction.x() * distance,
                station.worldZ() + direction.y() * distance,
                station.worldY(),
                target.levelKey().identifier().toString(),
                List.of()));
    }

    private RouteCardNode ensureMissingPathBoundary(RouteCardNode station, RouteCardNode other, UUID sectionId, int layoutIndex, int side) {
        int occurrence = layoutIndex * 8 + 4 + side;
        UUID primaryId = stableUuid("route-card-missing-path-boundary:" + sectionId + ":" + station.id() + ":" + side);
        RouteCardNodeId nodeId = new RouteCardNodeId(RouteCardNodeKind.MISSING_PATH_BOUNDARY, station.levelKey(), primaryId, occurrence);
        Vec2 direction = directionBetween(station, other, sectionId, side);
        double distance = 32.0D;
        return this.nodes.computeIfAbsent(nodeId, ignored -> new RouteCardNode(
                nodeId,
                Optional.empty(),
                Optional.empty(),
                occurrence,
                RouteCardNodeKind.MISSING_PATH_BOUNDARY,
                station.levelKey(),
                station.worldX() + direction.x() * distance,
                station.worldZ() + direction.y() * distance,
                station.worldY(),
                "Missing path",
                List.of()));
    }

    private void addMissingPathBoundaryEdges(RouteLayout layout, UUID sectionId, int layoutIndex, RouteCardNode from, RouteCardNode to, RouteSectionStatus status) {
        if (!from.levelKey().equals(to.levelKey())) {
            this.crossDimensionCount++;
        }
        RouteCardNode fromBoundary = this.ensureMissingPathBoundary(from, to, sectionId, layoutIndex, 0);
        this.addNodeToCurrentSegment(from);
        this.addNodeToCurrentSegment(fromBoundary);
        this.addEdge(layout, sectionId, layoutIndex, this.currentSegmentIndex, from.id(), fromBoundary.id(), SemanticEdgeKind.NORMAL, status, false, List.of());

        this.startNextSegment(to.levelKey());
        RouteCardNode toBoundary = this.ensureMissingPathBoundary(to, from, sectionId, layoutIndex, 1);
        this.addNodeToCurrentSegment(toBoundary);
        this.addNodeToCurrentSegment(to);
        this.addEdge(layout, sectionId, layoutIndex, -1, fromBoundary.id(), toBoundary.id(), SemanticEdgeKind.FOLD_ADJACENT, status, false, List.of());
        this.addEdge(layout, sectionId, layoutIndex, this.currentSegmentIndex, toBoundary.id(), to.id(), SemanticEdgeKind.NORMAL, status, false, List.of());
    }

    private static Vec2 directionBetween(RouteCardNode from, RouteCardNode to, UUID sectionId, int side) {
        double dx = to.worldX() - from.worldX();
        double dz = to.worldZ() - from.worldZ();
        double length = Math.hypot(dx, dz);
        if (length >= 0.001D) {
            return new Vec2(dx / length, dz / length);
        }
        int hash = sectionId.hashCode() * 31 + from.id().hashCode() + side * 17;
        double angle = (hash & 0xFFFF) / 65535.0D * Math.PI * 2.0D;
        return new Vec2(Math.cos(angle), Math.sin(angle));
    }

    private void addFallbackEdge(RouteLayout layout, UUID sectionId, int layoutIndex, RouteCardNode from, RouteCardNode to, RouteSectionStatus status, SemanticEdgeKind kind, List<PipeConnectionRef> slice, int segmentIndex, boolean loopBack) {
        this.addNodeToCurrentSegment(from);
        if (!from.levelKey().equals(to.levelKey()) && kind != SemanticEdgeKind.STATION_INTERNAL) {
            this.addEdge(layout, sectionId, layoutIndex, -1, from.id(), to.id(), SemanticEdgeKind.FOLD_ADJACENT, status, false, slice);
            this.startNextSegment(to.levelKey());
            this.addNodeToCurrentSegment(to);
            return;
        }
        this.addNodeToCurrentSegment(to);
        this.addEdge(layout, sectionId, layoutIndex, segmentIndex, from.id(), to.id(), kind, status, loopBack || kind == SemanticEdgeKind.LOOP_BACK, slice);
    }

    private void addEdge(RouteLayout layout, UUID sectionId, int layoutIndex, int segmentIndex, RouteCardNodeId from, RouteCardNodeId to, SemanticEdgeKind kind, RouteSectionStatus status, boolean loopBack, List<PipeConnectionRef> slice) {
        String id = "route-card-edge:" + layout.id() + ":" + sectionId + ":" + layoutIndex + ":" + this.edges.size();
        this.edges.add(new RouteCardEdge(id, from, to, kind, sectionId, layoutIndex, segmentIndex, layout.bidirectional(), loopBack, status, slice, this.themeColors));
        if (segmentIndex >= 0) {
            RouteCardNode referenceNode = this.nodes.get(from);
            if (referenceNode == null) {
                referenceNode = this.nodes.get(to);
            }
            ResourceKey<Level> levelKey = referenceNode == null ? Level.OVERWORLD : referenceNode.levelKey();
            SegmentAccumulator segment = this.segments.computeIfAbsent(segmentIndex, index -> new SegmentAccumulator(index, levelKey));
            segment.nodeIds.add(from);
            segment.nodeIds.add(to);
            segment.edgeIds.add(id);
        }
    }

    private void startSegment(int index, ResourceKey<Level> levelKey) {
        this.currentSegmentIndex = index;
        this.segments.computeIfAbsent(index, ignored -> new SegmentAccumulator(index, levelKey));
    }

    private void startNextSegment(ResourceKey<Level> levelKey) {
        int next = this.segments.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        this.startSegment(next, levelKey);
    }

    private void addNodeToCurrentSegment(RouteCardNode node) {
        this.segments.computeIfAbsent(this.currentSegmentIndex, ignored -> new SegmentAccumulator(this.currentSegmentIndex, node.levelKey())).nodeIds.add(node.id());
    }

    private List<RouteCardSegment> buildSegments() {
        return this.segments.values().stream()
                .filter(segment -> !segment.nodeIds.isEmpty())
                .map(segment -> new RouteCardSegment(
                        "route-card-segment:" + segment.index,
                        segment.index,
                        segment.levelKey,
                        segment.nodeIds.stream().toList(),
                        segment.edgeIds.stream().toList()))
                .toList();
    }

    private void mergeLoopTailSegmentIntoHead(RouteLayout layout, List<RouteCardNode> stopNodes) {
        if (!layout.loop() || stopNodes.size() < 2) {
            return;
        }
        SegmentAccumulator head = this.segments.get(0);
        if (head == null) {
            return;
        }
        RouteCardNodeId firstStopId = stopNodes.getFirst().id();
        List<SegmentAccumulator> tails = this.segments.values().stream()
                .filter(segment -> segment.index != 0)
                .filter(segment -> segment.levelKey.equals(head.levelKey))
                .filter(segment -> segment.nodeIds.contains(firstStopId))
                .sorted(Comparator.comparingInt(segment -> segment.index))
                .toList();
        for (SegmentAccumulator tail : tails) {
            this.mergeSegmentIntoHead(head, tail);
            this.remapEdgeSegmentIndex(tail.index, head.index);
            this.segments.remove(tail.index);
            if (this.currentSegmentIndex == tail.index) {
                this.currentSegmentIndex = head.index;
            }
        }
    }

    private void mergeSegmentIntoHead(SegmentAccumulator head, SegmentAccumulator tail) {
        LinkedHashSet<RouteCardNodeId> mergedNodes = new LinkedHashSet<>(head.nodeIds);
        mergedNodes.addAll(tail.nodeIds);
        head.nodeIds.clear();
        head.nodeIds.addAll(mergedNodes);

        LinkedHashSet<String> mergedEdges = new LinkedHashSet<>(head.edgeIds);
        mergedEdges.addAll(tail.edgeIds);
        head.edgeIds.clear();
        head.edgeIds.addAll(mergedEdges);
    }

    private void remapEdgeSegmentIndex(int fromSegmentIndex, int toSegmentIndex) {
        for (int i = 0; i < this.edges.size(); i++) {
            RouteCardEdge edge = this.edges.get(i);
            if (edge.segmentIndex() != fromSegmentIndex) {
                continue;
            }
            this.edges.set(i, new RouteCardEdge(
                    edge.id(),
                    edge.from(),
                    edge.to(),
                    edge.kind(),
                    edge.routeSectionId(),
                    edge.layoutIndex(),
                    toSegmentIndex,
                    edge.bidirectional(),
                    edge.loopBack(),
                    edge.status(),
                    edge.backingPathSlice(),
                    edge.themeColors()));
        }
    }

    private boolean isSegmentBreakpoint(PathFoldEvent event) {
        if (!event.localAnchor().levelKey().equals(event.peerAnchor().levelKey())) {
            return true;
        }
        return ClientPipeNetworkCache.foldAnchorAt(event.localAnchor()).map(FoldAnchorNode::kind).filter(FoldAnchorKind.SPACE::equals).isPresent()
                || ClientPipeNetworkCache.foldAnchorAt(event.peerAnchor()).map(FoldAnchorNode::kind).filter(FoldAnchorKind.SPACE::equals).isPresent();
    }

    private boolean hasMultipleSegmentBreakpoints(List<PathFoldEvent> foldEvents) {
        int breakpoints = 0;
        for (PathFoldEvent event : foldEvents) {
            if (this.isSegmentBreakpoint(event)) {
                breakpoints++;
                if (breakpoints >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    private NodePosition platformPosition(StationGroup station, PlatformStop platformStop, int layoutIndex, int occurrence) {
        Optional<PipeConnection> connection = ClientPipeNetworkCache.connection(platformStop.connectionRef());
        if (connection.isPresent()) {
            PipeConnection pipe = connection.get();
            Vec3 midpoint = pipe.positionAt(pipe.length() * 0.5D);
            return new NodePosition(midpoint.x, midpoint.y, midpoint.z);
        }
        double angle = hashAngle(platformStop.id()) + occurrence * 0.47D;
        double radius = 4.0D + Math.floorMod(layoutIndex, 3) * 1.5D;
        return new NodePosition(
                station.stationBlockPos().getX() + Math.cos(angle) * radius,
                station.stationBlockPos().getY(),
                station.stationBlockPos().getZ() + Math.sin(angle) * radius);
    }

    private static String stationLabel(StationGroup station, int occurrence) {
        return occurrence <= 0 ? station.primaryName() : station.primaryName() + " #" + (occurrence + 1);
    }

    private static String platformLabel(StationGroup station, PlatformStop platformStop, int occurrence) {
        String platform = platformStop.displayName()
                .filter(name -> !name.isBlank())
                .orElse(platformStop.platformNumber());
        String label = station.primaryName() + " " + platform;
        return occurrence <= 0 ? label : label + " #" + (occurrence + 1);
    }

    private static List<UUID> routeLineIdsForStation(UUID stationGroupId) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (PlatformStop platformStop : ClientRouteDataCache.platformStopsInStation(stationGroupId)) {
            for (UUID layoutId : ClientRouteDataCache.routeLayoutIdsForPlatformStop(platformStop.id())) {
                ClientRouteDataCache.routeLayout(layoutId).ifPresent(layout -> ids.add(layout.routeLineId()));
            }
        }
        return ids.stream().sorted().toList();
    }

    private static UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static double hashAngle(UUID id) {
        long mixed = id.getMostSignificantBits() * 31L + id.getLeastSignificantBits();
        return (Math.floorMod(mixed, 65_536L) / 65_536.0D) * Math.PI * 2.0D;
    }

    private record FoldTransition(PipeAnchorId localAnchor, PipeAnchorId peerAnchor) {}

    private record PathFoldEvent(PipeAnchorId localAnchor, PipeAnchorId peerAnchor, List<PipeConnectionRef> slice) {}

    private record NodePosition(double x, double y, double z) {}

    private static final class SegmentAccumulator {
        private final int index;
        private final ResourceKey<Level> levelKey;
        private final LinkedHashSet<RouteCardNodeId> nodeIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> edgeIds = new LinkedHashSet<>();

        private SegmentAccumulator(int index, ResourceKey<Level> levelKey) {
            this.index = index;
            this.levelKey = levelKey;
        }
    }
}
