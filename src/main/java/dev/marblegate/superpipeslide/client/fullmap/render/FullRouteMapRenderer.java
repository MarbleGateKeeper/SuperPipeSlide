package dev.marblegate.superpipeslide.client.fullmap.render;

import dev.marblegate.superpipeslide.client.core.navigation.NavigationSemanticColors;
import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.fullmap.cache.FullRouteMapCache;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapLayoutMode;
import dev.marblegate.superpipeslide.client.fullmap.diagnostic.MissingCrossDimensionPathHint;
import dev.marblegate.superpipeslide.client.fullmap.model.MapDimensionGraph;
import dev.marblegate.superpipeslide.client.fullmap.model.MapEdge;
import dev.marblegate.superpipeslide.client.fullmap.model.MapEdgeOccurrence;
import dev.marblegate.superpipeslide.client.fullmap.model.MapNode;
import dev.marblegate.superpipeslide.client.fullmap.model.MapTransferHint;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeKind;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.ViewportState;
import dev.marblegate.superpipeslide.client.fullmap.model.hit.HitTarget;
import dev.marblegate.superpipeslide.client.fullmap.physical.PhysicalMapEdge;
import dev.marblegate.superpipeslide.client.fullmap.physical.PhysicalMapNode;
import dev.marblegate.superpipeslide.client.fullmap.physical.PhysicalMissingCrossDimensionPathHint;
import dev.marblegate.superpipeslide.client.fullmap.physical.PhysicalNodeKind;
import dev.marblegate.superpipeslide.client.fullmap.physical.PhysicalRouteMapGraph;
import dev.marblegate.superpipeslide.client.fullmap.physical.PhysicalStationFrame;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SemanticEdgeKind;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualEdgePath;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualLabel;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualLane;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualNode;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualRouteMapGraph;
import dev.marblegate.superpipeslide.client.fullmap.ui.DisplayNameStack;
import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapText;
import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapTheme;
import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapUi;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class FullRouteMapRenderer {
    public FullRouteMapRenderer() {}

    // === Static-frame geometry cache (B8) ===
    // The map spends most of its time observed through an unmoving viewport, yet every
    // frame re-projects, re-offsets and re-tessellates every visible edge and node. The
    // hover-independent part of a frame (background grid, edges/lanes, node bodies,
    // label text) is therefore captured once per cache key into a FrameProgram of
    // recorded render states plus replay actions, and later frames replay it. Hover
    // rendering is split into per-element slots (conditional decorations, two-way paint
    // variants) that re-evaluate the live hover at replay time, so hover changes never
    // invalidate the program and the submission order -- and thus the composed pixels --
    // stays identical to a fresh render.
    private static final int STATIC_PROGRAM_CACHE_LIMIT = 4;
    private static final List<StaticProgramEntry> STATIC_PROGRAMS = new ArrayList<>(STATIC_PROGRAM_CACHE_LIMIT + 1);
    // Live hover for the frame currently being rendered, consumed by replay slots. This
    // is static on purpose: cached programs outlive the renderer instance that recorded
    // them (a reopened screen creates a new renderer but may reuse a program), and the
    // map screen renders strictly on the render thread.
    private static LiveHover liveHover = LiveHover.empty();

    /**
     * One cached static frame. Identity keys: the exact graph instances served by
     * {@link FullRouteMapCache} (swapped atomically per rebuild), the viewport (value
     * equality on center/zoom/camera), the layout mode, the map area rectangle, the
     * route/pipe data revisions (route line colors and names can change while the cache
     * still serves the previous graphs) and the font instance (resource reloads).
     */
    private record StaticProgramEntry(
            Object dataGraph,
            @Nullable Object visualGraph,
            @Nullable Object physicalGraph,
            ViewportState viewport,
            SPSGui.Rect mapRect,
            FullRouteMapLayoutMode layoutMode,
            long routeRevision,
            long pipeRevision,
            Font font,
            SmoothGuiPrimitives.FrameProgram program) {
        boolean matches(Object data, @Nullable Object visual, @Nullable Object physical, ViewportState view, SPSGui.Rect rect, FullRouteMapLayoutMode mode, long routeRev, long pipeRev, Font renderFont) {
            return this.dataGraph == data
                    && this.visualGraph == visual
                    && this.physicalGraph == physical
                    && this.viewport.equals(view)
                    && this.mapRect.equals(rect)
                    && this.layoutMode == mode
                    && this.routeRevision == routeRev
                    && this.pipeRevision == pipeRev
                    && this.font == renderFont;
        }
    }

    /**
     * Per-frame evaluation of everything hover can visually affect, computed once per
     * render call from the live hover target. Replay slots read these precomputed sets
     * instead of re-running graph lookups for every node or edge.
     */
    private record LiveHover(
            HitTarget target,
            Optional<UUID> highlightedRouteLineId,
            Set<NodeId> transferHighlightNodes,
            Optional<NodeId> foldPeerHighlightId,
            Set<NodeId> schematicHighlightNodes,
            Optional<UUID> physicalStationGroup,
            Optional<PipeAnchorId> physicalFoldPeer) {
        static LiveHover empty() {
            return new LiveHover(HitTarget.none(), Optional.empty(), Set.of(), Optional.empty(), Set.of(), Optional.empty(), Optional.empty());
        }

        static LiveHover forPhysical(PhysicalRouteMapGraph graph, HitTarget hover) {
            Optional<PhysicalMapNode> hoveredNode = hover instanceof HitTarget.PhysicalNodeHit(var nodeId) ? graph.node(nodeId) : Optional.empty();
            Optional<UUID> hoveredStation = hoveredNode.flatMap(PhysicalMapNode::stationGroupId);
            Optional<PipeAnchorId> hoveredFoldPeer = hoveredNode
                    .filter(node -> node.kind() == PhysicalNodeKind.FOLD_ANCHOR)
                    .flatMap(PhysicalMapNode::foldAnchorId)
                    .flatMap(ClientPipeNetworkCache::globalFoldCounterpart);
            return new LiveHover(hover, Optional.empty(), Set.of(), Optional.empty(), Set.of(), hoveredStation, hoveredFoldPeer);
        }

        static LiveHover forVisual(MapDimensionGraph graph, VisualRouteMapGraph visualGraph, HitTarget hover, Optional<UUID> highlightedRouteLineId) {
            Set<NodeId> transferNodes = transferHighlightNodeIds(graph, hover);
            Optional<NodeId> foldPeerHighlight = (hover instanceof HitTarget.NodeHit(var nodeId) ? graph.node(nodeId) : Optional.<MapNode>empty())
                    .filter(node -> node.kind() == NodeKind.FOLD_ANCHOR)
                    .flatMap(node -> foldPeerNode(graph, node))
                    .map(MapNode::id);
            Set<NodeId> schematicNodes = schematicHighlightNodes(visualGraph, hover, highlightedRouteLineId);
            return new LiveHover(hover, highlightedRouteLineId, transferNodes, foldPeerHighlight, schematicNodes, Optional.empty(), Optional.empty());
        }

        static LiveHover forPlain(MapDimensionGraph graph, HitTarget hover) {
            Set<NodeId> transferNodes = transferHighlightNodeIds(graph, hover);
            Optional<NodeId> foldPeerHighlight = (hover instanceof HitTarget.NodeHit(var nodeId) ? graph.node(nodeId) : Optional.<MapNode>empty())
                    .filter(node -> node.kind() == NodeKind.FOLD_ANCHOR)
                    .flatMap(node -> foldPeerNode(graph, node))
                    .map(MapNode::id);
            return new LiveHover(hover, Optional.empty(), transferNodes, foldPeerHighlight, Set.of(), Optional.empty(), Optional.empty());
        }

        /**
         * Nodes highlighted in pure schematic mode because they terminate the hovered
         * edge or touch the legend-highlighted route line (fold anchors excluded).
         */
        private static Set<NodeId> schematicHighlightNodes(VisualRouteMapGraph visualGraph, HitTarget hover, Optional<UUID> highlightedRouteLineId) {
            Set<NodeId> nodes = (hover instanceof HitTarget.EdgeHit(var edgeId) ? visualGraph.edgePath(edgeId) : Optional.<VisualEdgePath>empty())
                    .map(path -> Set.of(path.from(), path.to()).stream()
                            .filter(id -> visualGraph.node(id).map(node -> node.kind() != NodeKind.FOLD_ANCHOR).orElse(true))
                            .collect(Collectors.toSet()))
                    .orElse(Set.of());
            if (highlightedRouteLineId.isPresent()) {
                Set<NodeId> routeNodes = visualGraph.edgePaths().stream()
                        .filter(path -> path.routeLineIds().contains(highlightedRouteLineId.get()))
                        .flatMap(path -> Set.of(path.from(), path.to()).stream())
                        .filter(id -> visualGraph.node(id).map(node -> node.kind() != NodeKind.FOLD_ANCHOR).orElse(true))
                        .collect(Collectors.toSet());
                if (!routeNodes.isEmpty()) {
                    nodes = new HashSet<>(nodes);
                    nodes.addAll(routeNodes);
                }
            }
            return nodes;
        }
    }

    /**
     * Returns the cached static frame program for this exact key, capturing a fresh one
     * via {@code captureBody} on a miss. A freshly captured program is not submitted to
     * the GUI renderer during capture; the caller always finishes with
     * {@link SmoothGuiPrimitives.FrameProgram#replay}, so hit and miss frames share one
     * submission path. The cache is a small access-ordered LRU, so panning or zooming
     * (a new viewport key per frame) stays bounded and switching dimensions back and
     * forth still hits.
     */
    private SmoothGuiPrimitives.FrameProgram staticProgram(Object dataGraph, @Nullable Object visualGraph, @Nullable Object physicalGraph, ViewportState viewport, SPSGui.Rect mapRect, Font font, Runnable captureBody) {
        long routeRevision = ClientRouteDataCache.revision();
        long pipeRevision = ClientPipeNetworkCache.aggregateRevision();
        FullRouteMapLayoutMode layoutMode = FullRouteMapCache.layoutMode();
        for (int i = 0; i < STATIC_PROGRAMS.size(); i++) {
            StaticProgramEntry entry = STATIC_PROGRAMS.get(i);
            if (entry.matches(dataGraph, visualGraph, physicalGraph, viewport, mapRect, layoutMode, routeRevision, pipeRevision, font)) {
                STATIC_PROGRAMS.remove(i);
                STATIC_PROGRAMS.add(entry);
                return entry.program();
            }
        }
        SmoothGuiPrimitives.beginFrameCapture();
        SmoothGuiPrimitives.FrameProgram program;
        try {
            captureBody.run();
        } finally {
            program = SmoothGuiPrimitives.endFrameCapture();
        }
        STATIC_PROGRAMS.add(new StaticProgramEntry(dataGraph, visualGraph, physicalGraph, viewport, mapRect, layoutMode, routeRevision, pipeRevision, font, program));
        while (STATIC_PROGRAMS.size() > STATIC_PROGRAM_CACHE_LIMIT) {
            STATIC_PROGRAMS.removeFirst();
        }
        return program;
    }

    public void renderPhysical(GuiGraphicsExtractor graphics, Font font, PhysicalRouteMapGraph graph, ViewportState viewport, SPSGui.Rect mapRect, HitTarget hover, int mouseX, int mouseY) {
        liveHover = LiveHover.forPhysical(graph, hover);
        SmoothGuiPrimitives.FrameProgram program = this.staticProgram(graph, null, graph, viewport, mapRect, font, () -> {
            drawMapBackground(graphics, mapRect, viewport, FullRouteMapCache.layoutMode());
            Aabb2 worldView = screenWorldBounds(viewport, mapRect).inflate(96.0D / scale(viewport));
            this.drawPhysicalStationFrames(graphics, graph, viewport, mapRect, worldView);
            this.drawPhysicalMissingCrossDimensionHints(graphics, graph, viewport, mapRect);
            this.drawPhysicalEdges(graphics, graph, viewport, mapRect, worldView);
            this.drawPhysicalNodes(graphics, font, graph, viewport, mapRect, worldView);
            this.drawPhysicalLabels(graphics, font, graph, viewport, mapRect, worldView);
        });
        program.replay(graphics);
        if (graph.nodes().isEmpty() && graph.edges().isEmpty()) {
            SPSGui.centeredText(graphics, font, Component.translatable("screen.superpipeslide.full_map.empty_dimension"), mapRect.x() + mapRect.width() / 2, mapRect.y() + mapRect.height() / 2, FullRouteMapConfig.MAP_LABEL_MUTED);
        }
    }

    public HitTarget hitTestPhysical(PhysicalRouteMapGraph graph, ViewportState viewport, SPSGui.Rect mapRect, double mouseX, double mouseY) {
        if (!mapRect.contains(mouseX, mouseY)) {
            return HitTarget.none();
        }
        List<PhysicalMapNode> nodes = graph.nodes().stream()
                .sorted(Comparator.comparing((PhysicalMapNode node) -> node.kind() == PhysicalNodeKind.FOLD_ANCHOR ? 0 : 1))
                .toList();
        for (PhysicalMapNode node : nodes) {
            Vec2 screen = worldToScreen(node.worldX(), node.worldZ(), viewport, mapRect);
            if (screen.distanceTo(new Vec2(mouseX, mouseY)) <= physicalNodeRadius(node.kind(), perspectiveElementZoom(viewport, mapRect, screen)) + 4.0D) {
                return HitTarget.physicalNode(node.id());
            }
        }
        String bestMissingHint = null;
        double bestMissingDistance = Double.POSITIVE_INFINITY;
        List<List<Vec2>> missingHintBlockers = physicalMissingHintBlockers(graph, viewport, mapRect);
        for (PhysicalMissingCrossDimensionPathHint hint : graph.missingCrossDimensionPathHints()) {
            Optional<SegmentScreen> segment = physicalMissingCrossDimensionHintSegment(graph, hint, viewport, mapRect, missingHintBlockers);
            if (segment.isEmpty()) {
                continue;
            }
            double distance = distanceToSegment(new Vec2(mouseX, mouseY), segment.get().a(), segment.get().b());
            if (distance <= missingPathHitRadius(viewport.zoom()) && distance < bestMissingDistance) {
                bestMissingDistance = distance;
                bestMissingHint = hint.id();
            }
        }
        if (bestMissingHint != null) {
            return HitTarget.missingCrossDimensionPath(bestMissingHint);
        }
        String bestEdge = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        Aabb2 worldView = screenWorldBounds(viewport, mapRect).inflate(96.0D / scale(viewport));
        Map<String, List<PhysicalMapEdge>> groups = physicalEdgeGroups(graph.edges(), worldView);
        for (List<PhysicalMapEdge> group : groups.values()) {
            if (group.isEmpty()) {
                continue;
            }
            List<PhysicalRouteLane> lanes = physicalRouteLanes(group);
            if (lanes.isEmpty()) {
                continue;
            }
            List<Vec2> screenPath = group.getFirst().points().stream()
                    .map(point -> worldToScreen(point.x(), point.y(), viewport, mapRect))
                    .toList();
            double laneWidth = FullRouteMapConfig.LINE_WIDTH_PX;
            if (lanes.size() >= FullRouteMapConfig.TRUNK_THRESHOLD) {
                double distance = distanceToPolyline(new Vec2(mouseX, mouseY), screenPath);
                if (distance <= physicalEdgeHitRadius(viewport.zoom()) && distance < bestDistance) {
                    bestDistance = distance;
                    bestEdge = physicalTrunkHitEdge(lanes, screenPath, new Vec2(mouseX, mouseY), viewport.zoom()).id();
                }
                continue;
            }
            double step = laneWidth + 1.0D;
            double center = (lanes.size() - 1) * 0.5D;
            for (int i = 0; i < lanes.size(); i++) {
                PhysicalRouteLane lane = lanes.get(i);
                List<Vec2> lanePath = offsetScreenPathAnchored(screenPath, (i - center) * step);
                double distance = distanceToPolyline(new Vec2(mouseX, mouseY), lanePath);
                if (distance <= physicalEdgeHitRadius(viewport.zoom()) && distance < bestDistance) {
                    bestDistance = distance;
                    bestEdge = lane.representative().id();
                }
            }
        }
        return bestEdge == null ? HitTarget.none() : HitTarget.physicalEdge(bestEdge);
    }

    public void render(GuiGraphicsExtractor graphics, Font font, MapDimensionGraph graph, VisualRouteMapGraph visualGraph, ViewportState viewport, SPSGui.Rect mapRect, HitTarget hover, int mouseX, int mouseY) {
        this.render(graphics, font, graph, visualGraph, viewport, mapRect, hover, mouseX, mouseY, Optional.empty());
    }

    public void render(GuiGraphicsExtractor graphics, Font font, MapDimensionGraph graph, VisualRouteMapGraph visualGraph, ViewportState viewport, SPSGui.Rect mapRect, HitTarget hover, int mouseX, int mouseY, Optional<UUID> highlightedRouteLineId) {
        if (visualGraph == null) {
            this.render(graphics, font, graph, viewport, mapRect, hover, mouseX, mouseY);
            return;
        }
        if (FullRouteMapCache.layoutMode() == FullRouteMapLayoutMode.SCHEMATIC) {
            this.renderPureSchematic(graphics, font, graph, visualGraph, viewport, mapRect, hover, highlightedRouteLineId == null ? Optional.empty() : highlightedRouteLineId);
            return;
        }
        liveHover = LiveHover.forVisual(graph, visualGraph, hover, highlightedRouteLineId == null ? Optional.empty() : highlightedRouteLineId);
        SmoothGuiPrimitives.FrameProgram program = this.staticProgram(graph, visualGraph, null, viewport, mapRect, font, () -> {
            drawMapBackground(graphics, mapRect, viewport, FullRouteMapCache.layoutMode());
            Aabb2 visualView = screenWorldBounds(viewport, mapRect).inflate(96.0D / scale(viewport));
            this.drawVisualTransferHints(graphics, graph, visualGraph, viewport, mapRect, visualView);
            this.drawMissingCrossDimensionHints(graphics, graph, visualGraph, viewport, mapRect);
            this.drawVisualFoldPeerIndicator(graphics, graph, visualGraph, viewport, mapRect, visualView);
            EdgeBlockerIndex edgeBlockers = this.drawVisualEdges(graphics, graph, visualGraph, viewport, mapRect, visualView);
            this.drawVisualNodes(graphics, font, graph, visualGraph, viewport, mapRect, visualView);
            this.drawVisualLabels(graphics, font, graph, visualGraph, viewport, mapRect, visualView, edgeBlockers);
        });
        program.replay(graphics);
        if (graph.nodes().isEmpty()) {
            SPSGui.centeredText(graphics, font, Component.translatable("screen.superpipeslide.full_map.empty_dimension"), mapRect.x() + mapRect.width() / 2, mapRect.y() + mapRect.height() / 2, FullRouteMapConfig.MAP_LABEL_MUTED);
        }
    }

    public HitTarget hitTest(MapDimensionGraph graph, VisualRouteMapGraph visualGraph, ViewportState viewport, SPSGui.Rect mapRect, double mouseX, double mouseY) {
        if (visualGraph == null) {
            return this.hitTest(graph, viewport, mapRect, mouseX, mouseY);
        }
        if (FullRouteMapCache.layoutMode() == FullRouteMapLayoutMode.SCHEMATIC) {
            return this.hitTestPureSchematic(graph, visualGraph, viewport, mapRect, mouseX, mouseY);
        }
        if (!mapRect.contains(mouseX, mouseY)) {
            return HitTarget.none();
        }
        List<MapNode> nodes = graph.nodes().stream()
                .filter(node -> isVisibleNode(node, viewport.zoom()))
                .sorted(Comparator.comparing((MapNode node) -> switch (node.kind()) {
                    case FOLD_ANCHOR -> 0;
                    case CLUSTER -> 1;
                    case DEEP_CLUSTER -> 2;
                    case STATION -> 3;
                }))
                .toList();
        for (MapNode node : nodes) {
            Vec2 screen = screenForNode(graph, visualGraph, node, viewport, mapRect);
            if (nodeHit(node, screen, perspectiveElementZoom(viewport, mapRect, screen), mouseX, mouseY)) {
                return HitTarget.node(node.id());
            }
        }

        for (MapTransferHint hint : graph.transferHints()) {
            Optional<SegmentScreen> segment = visualTransferSegment(graph, visualGraph, hint, viewport, mapRect);
            if (segment.isEmpty()) {
                continue;
            }
            double distance = distanceToSegment(new Vec2(mouseX, mouseY), segment.get().a(), segment.get().b());
            if (distance <= transferHintHitRadius(viewport.zoom())) {
                return HitTarget.transferHint(hint.id());
            }
        }

        String bestMissingHint = null;
        double bestMissingDistance = Double.POSITIVE_INFINITY;
        List<List<Vec2>> missingHintBlockers = missingHintBlockers(graph, visualGraph, viewport, mapRect);
        for (MissingCrossDimensionPathHint hint : graph.missingCrossDimensionPathHints()) {
            Optional<SegmentScreen> segment = missingCrossDimensionHintSegment(graph, visualGraph, hint, viewport, mapRect, missingHintBlockers);
            if (segment.isEmpty()) {
                continue;
            }
            double distance = distanceToSegment(new Vec2(mouseX, mouseY), segment.get().a(), segment.get().b());
            if (distance <= missingPathHitRadius(viewport.zoom()) && distance < bestMissingDistance) {
                bestMissingDistance = distance;
                bestMissingHint = hint.id();
            }
        }
        if (bestMissingHint != null) {
            return HitTarget.missingCrossDimensionPath(bestMissingHint);
        }

        String bestEdge = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (VisualEdgePath path : visualGraph.edgePaths()) {
            MapEdge edge = graph.edges().stream().filter(value -> value.id().equals(path.edgeId())).findFirst().orElse(null);
            if (edge == null) {
                continue;
            }
            Optional<List<Vec2>> screenPath = this.visualScreenPathForEdge(graph, visualGraph, path, edge, viewport, mapRect);
            if (screenPath.isEmpty()) {
                continue;
            }
            double distance = distanceToPolyline(new Vec2(mouseX, mouseY), screenPath.get());
            if (distance < visualEdgeHitRadius(edge, viewport.zoom()) && distance < bestDistance) {
                bestDistance = distance;
                bestEdge = path.edgeId();
            }
        }
        return bestEdge == null ? HitTarget.none() : HitTarget.edge(bestEdge);
    }

    private void renderPureSchematic(GuiGraphicsExtractor graphics, Font font, MapDimensionGraph graph, VisualRouteMapGraph visualGraph, ViewportState viewport, SPSGui.Rect mapRect, HitTarget hover, Optional<UUID> highlightedRouteLineId) {
        liveHover = LiveHover.forVisual(graph, visualGraph, hover, highlightedRouteLineId);
        SmoothGuiPrimitives.FrameProgram program = this.staticProgram(graph, visualGraph, null, viewport, mapRect, font, () -> {
            drawMapBackground(graphics, mapRect, viewport, FullRouteMapCache.layoutMode());
            Aabb2 visualView = screenWorldBounds(viewport, mapRect).inflate(96.0D / scale(viewport));
            this.drawPureSchematicEdges(graphics, visualGraph, viewport, mapRect, visualView);
            this.drawPureSchematicNodes(graphics, font, graph, visualGraph, viewport, mapRect, visualView);
            this.drawPureSchematicLabels(graphics, font, graph, visualGraph, viewport, mapRect, visualView);
        });
        program.replay(graphics);
        if (visualGraph.nodes().isEmpty()) {
            SPSGui.centeredText(graphics, font, Component.translatable("screen.superpipeslide.full_map.empty_dimension"), mapRect.x() + mapRect.width() / 2, mapRect.y() + mapRect.height() / 2, FullRouteMapConfig.MAP_LABEL_MUTED);
        }
    }

    private HitTarget hitTestPureSchematic(MapDimensionGraph graph, VisualRouteMapGraph visualGraph, ViewportState viewport, SPSGui.Rect mapRect, double mouseX, double mouseY) {
        if (!mapRect.contains(mouseX, mouseY)) {
            return HitTarget.none();
        }
        List<VisualNode> stations = visualGraph.nodes().stream()
                .filter(node -> node.kind() == NodeKind.STATION)
                .sorted(Comparator.comparingInt((VisualNode node) -> graph.node(node.id()).map(MapNode::isTransferStation).orElse(false) ? 1 : 0).reversed())
                .toList();
        for (VisualNode visualNode : stations) {
            MapNode raw = graph.node(visualNode.id()).orElse(null);
            if (raw == null) {
                continue;
            }
            Vec2 screen = worldToScreen(visualNode.x(), visualNode.z(), viewport, mapRect);
            if (nodeHit(raw, screen, viewport.zoom(), mouseX, mouseY)) {
                return HitTarget.node(raw.id());
            }
        }

        for (VisualNode visualNode : visualGraph.nodes()) {
            if (visualNode.kind() != NodeKind.FOLD_ANCHOR) {
                continue;
            }
            Vec2 screen = worldToScreen(visualNode.x(), visualNode.z(), viewport, mapRect);
            if (Math.hypot(mouseX - screen.x(), mouseY - screen.y()) <= visualNodeCollisionRadius(visualNode, graph, viewport.zoom()) + 2.0D) {
                return HitTarget.node(visualNode.id());
            }
        }

        String bestEdge = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        Vec2 mouse = new Vec2(mouseX, mouseY);
        for (VisualEdgePath path : visualGraph.edgePaths()) {
            if (path.points().size() < 2) {
                continue;
            }
            List<Vec2> screenPath = path.points().stream()
                    .map(point -> worldToScreen(point.x(), point.y(), viewport, mapRect))
                    .toList();
            double distance = distanceToPolyline(mouse, screenPath);
            if (distance < visualEdgePathHitRadius(path, viewport.zoom()) && distance < bestDistance) {
                bestDistance = distance;
                bestEdge = path.edgeId();
            }
        }
        if (bestEdge != null) {
            return HitTarget.edge(bestEdge);
        }
        return HitTarget.none();
    }

    public void render(GuiGraphicsExtractor graphics, Font font, MapDimensionGraph graph, ViewportState viewport, SPSGui.Rect mapRect, HitTarget hover, int mouseX, int mouseY) {
        liveHover = LiveHover.forPlain(graph, hover);
        SmoothGuiPrimitives.FrameProgram program = this.staticProgram(graph, null, null, viewport, mapRect, font, () -> {
            drawMapBackground(graphics, mapRect, viewport, FullRouteMapCache.layoutMode());
            Aabb2 worldView = screenWorldBounds(viewport, mapRect).inflate(96.0D / scale(viewport));
            this.drawTransferHints(graphics, graph, viewport, mapRect, worldView);
            this.drawMissingCrossDimensionHints(graphics, graph, null, viewport, mapRect);
            this.drawFoldPeerIndicator(graphics, graph, viewport, mapRect, worldView);
            EdgeBlockerIndex edgeBlockers = this.drawEdges(graphics, graph, viewport, mapRect, worldView);
            this.drawNodes(graphics, font, graph, viewport, mapRect, worldView);
            this.drawLabels(graphics, font, graph, viewport, mapRect, worldView, edgeBlockers);
        });
        program.replay(graphics);
        if (graph.nodes().isEmpty()) {
            SPSGui.centeredText(graphics, font, Component.translatable("screen.superpipeslide.full_map.empty_dimension"), mapRect.x() + mapRect.width() / 2, mapRect.y() + mapRect.height() / 2, FullRouteMapConfig.MAP_LABEL_MUTED);
        }
    }

    public HitTarget hitTest(MapDimensionGraph graph, ViewportState viewport, SPSGui.Rect mapRect, double mouseX, double mouseY) {
        if (!mapRect.contains(mouseX, mouseY)) {
            return HitTarget.none();
        }
        List<MapNode> nodes = graph.nodes().stream()
                .filter(node -> isVisibleNode(node, viewport.zoom()))
                .sorted(Comparator.comparing((MapNode node) -> switch (node.kind()) {
                    case FOLD_ANCHOR -> 0;
                    case CLUSTER -> 1;
                    case DEEP_CLUSTER -> 2;
                    case STATION -> 3;
                }))
                .toList();
        for (MapNode node : nodes) {
            Vec2 screen = screenForNode(graph, node, viewport, mapRect);
            if (nodeHit(node, screen, perspectiveElementZoom(viewport, mapRect, screen), mouseX, mouseY)) {
                return HitTarget.node(node.id());
            }
        }

        for (MapTransferHint hint : graph.transferHints()) {
            Optional<SegmentScreen> segment = transferSegment(graph, hint, viewport, mapRect);
            if (segment.isEmpty()) {
                continue;
            }
            double distance = distanceToSegment(new Vec2(mouseX, mouseY), segment.get().a(), segment.get().b());
            if (distance <= transferHintHitRadius(viewport.zoom())) {
                return HitTarget.transferHint(hint.id());
            }
        }

        String bestMissingHint = null;
        double bestMissingDistance = Double.POSITIVE_INFINITY;
        List<List<Vec2>> missingHintBlockers = missingHintBlockers(graph, null, viewport, mapRect);
        for (MissingCrossDimensionPathHint hint : graph.missingCrossDimensionPathHints()) {
            Optional<SegmentScreen> segment = missingCrossDimensionHintSegment(graph, null, hint, viewport, mapRect, missingHintBlockers);
            if (segment.isEmpty()) {
                continue;
            }
            double distance = distanceToSegment(new Vec2(mouseX, mouseY), segment.get().a(), segment.get().b());
            if (distance <= missingPathHitRadius(viewport.zoom()) && distance < bestMissingDistance) {
                bestMissingDistance = distance;
                bestMissingHint = hint.id();
            }
        }
        if (bestMissingHint != null) {
            return HitTarget.missingCrossDimensionPath(bestMissingHint);
        }

        MapEdge bestEdge = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        Aabb2 worldView = screenWorldBounds(viewport, mapRect).inflate(96.0D / scale(viewport));
        Map<String, Double> edgeOffsets = this.visualEdgeOffsets(graph, viewport, mapRect, worldView);
        for (MapEdge edge : graph.edges()) {
            Optional<Segment> segment = displaySegment(graph, edge, viewport.zoom());
            if (segment.isEmpty()) {
                continue;
            }
            Vec2 a = screenForNode(graph, segment.get().from(), viewport, mapRect);
            Vec2 b = screenForNode(graph, segment.get().to(), viewport, mapRect);
            double distance = distanceToVisualEdge(edge, new Vec2(mouseX, mouseY), a, b, edgeOffsets.getOrDefault(edge.id(), 0.0D));
            if (distance < visualEdgeHitRadius(edge, viewport.zoom()) && distance < bestDistance) {
                bestDistance = distance;
                bestEdge = edge;
            }
        }
        return bestEdge == null ? HitTarget.none() : HitTarget.edge(bestEdge.id());
    }

    public static Vec2 worldToScreen(double worldX, double worldZ, ViewportState viewport, SPSGui.Rect mapRect) {
        double s = scale(viewport);
        if (cameraActive(viewport)) {
            double dx = worldX - viewport.centerWorldX();
            double dz = worldZ - viewport.centerWorldZ();
            double bearing = Math.toRadians(viewport.bearingDegrees());
            double cos = Math.cos(bearing);
            double sin = Math.sin(bearing);
            double rotatedX = dx * cos - dz * sin;
            double rotatedZ = dx * sin + dz * cos;
            double screenY = cameraFocusY(mapRect, viewport) + rotatedZ * s * cameraPitchYScale(viewport);
            return new Vec2(mapRect.x() + mapRect.width() * 0.5D + rotatedX * s, screenY);
        }
        return new Vec2(mapRect.x() + mapRect.width() * 0.5D + (worldX - viewport.centerWorldX()) * s,
                mapRect.y() + mapRect.height() * 0.5D + (worldZ - viewport.centerWorldZ()) * s);
    }

    public static Vec2 screenToWorld(double screenX, double screenY, ViewportState viewport, SPSGui.Rect mapRect) {
        double s = scale(viewport);
        if (cameraActive(viewport)) {
            double rotatedX = (screenX - (mapRect.x() + mapRect.width() * 0.5D)) / s;
            double rotatedZ = (screenY - cameraFocusY(mapRect, viewport)) / (s * cameraPitchYScale(viewport));
            double bearing = Math.toRadians(viewport.bearingDegrees());
            double cos = Math.cos(bearing);
            double sin = Math.sin(bearing);
            double dx = rotatedX * cos + rotatedZ * sin;
            double dz = -rotatedX * sin + rotatedZ * cos;
            return new Vec2(viewport.centerWorldX() + dx, viewport.centerWorldZ() + dz);
        }
        return new Vec2(viewport.centerWorldX() + (screenX - (mapRect.x() + mapRect.width() * 0.5D)) / s,
                viewport.centerWorldZ() + (screenY - (mapRect.y() + mapRect.height() * 0.5D)) / s);
    }

    public static void drawMapGrid(GuiGraphicsExtractor graphics, SPSGui.Rect rect, double centerWorldX, double centerWorldZ, double scale) {
        drawGridBackground(graphics, rect, centerWorldX, centerWorldZ, scale, FullRouteMapConfig.MAP_BACKGROUND, FullRouteMapConfig.MAP_GRID, FullRouteMapConfig.MAP_GRID_MAJOR, 64.0D, 4, 16);
    }

    public static void drawMapBackground(GuiGraphicsExtractor graphics, SPSGui.Rect rect, double centerWorldX, double centerWorldZ, double scale, FullRouteMapLayoutMode mode) {
        switch (mode == null ? FullRouteMapLayoutMode.PRACTICAL : mode) {
            case SCHEMATIC -> SmoothGuiPrimitives.quads(graphics, List.of(rectQuad(rect.x(), rect.y(), rect.right(), rect.bottom(), FullMapTheme.SCHEMATIC_BACKGROUND)));
            case GEOGRAPHIC -> drawGridBackground(graphics, rect, centerWorldX, centerWorldZ, scale, FullMapTheme.GEOGRAPHIC_BACKGROUND, FullMapTheme.GEOGRAPHIC_GRID, FullMapTheme.GEOGRAPHIC_GRID_MAJOR, 128.0D, 4, 18);
            case PHYSICAL -> drawGridBackground(graphics, rect, centerWorldX, centerWorldZ, scale, FullMapTheme.PHYSICAL_BACKGROUND, FullMapTheme.PHYSICAL_GRID, FullMapTheme.PHYSICAL_GRID_MAJOR, 32.0D, 4, 12);
            case PRACTICAL -> drawMapGrid(graphics, rect, centerWorldX, centerWorldZ, scale);
        }
    }

    public static void drawMapBackground(GuiGraphicsExtractor graphics, SPSGui.Rect rect, ViewportState viewport, FullRouteMapLayoutMode mode) {
        FullRouteMapLayoutMode normalized = mode == null ? FullRouteMapLayoutMode.PRACTICAL : mode;
        if (!cameraActive(viewport) || normalized == FullRouteMapLayoutMode.SCHEMATIC) {
            drawMapBackground(graphics, rect, viewport.centerWorldX(), viewport.centerWorldZ(), scale(viewport), normalized);
            return;
        }
        drawPerspectiveGridBackground(graphics, rect, viewport, normalized);
    }

    private static void drawGridBackground(GuiGraphicsExtractor graphics, SPSGui.Rect rect, double centerWorldX, double centerWorldZ, double scale, int background, int gridColor, int majorColor, double gridBlocks, int majorEvery, int minGridPx) {
        // The whole background (base fill plus grid lines) is emitted as one quad batch
        // instead of one fill() call per line: identical rectangles in the same draw
        // order and pipeline, but a single render state that the static-frame cache can
        // capture and replay.
        List<SmoothGuiPrimitives.GradientQuad> quads = new ArrayList<>();
        quads.add(rectQuad(rect.x(), rect.y(), rect.right(), rect.bottom(), background));
        // Spacing and offsets stay in double precision and every line is a 1px-wide
        // quad at double coordinates, so each line lands exactly where its world
        // multiple of gridBlocks projects (the zero point matches worldToScreen(0, 0)):
        // panning glides the grid together with the smoothly drawn nodes and edges
        // instead of stepping it in whole pixels. Major lines are picked by line
        // ordinal with ordinal 0 at world coordinate 0, which keeps the same world
        // line major while panning and matches the perspective grid's world-index rule.
        double gridPx = Math.max(minGridPx, gridBlocks * scale);
        long majorOrdinal = Math.max(1, majorEvery);
        double zeroScreenX = rect.x() + rect.width() * 0.5D - centerWorldX * scale;
        double zeroScreenY = rect.y() + rect.height() * 0.5D - centerWorldZ * scale;
        long xOrdinal = (long) Math.ceil((rect.x() - zeroScreenX) / gridPx);
        for (double x = zeroScreenX + xOrdinal * gridPx; x < rect.right(); x += gridPx, xOrdinal++) {
            int color = Math.floorMod(xOrdinal, majorOrdinal) == 0 ? majorColor : gridColor;
            quads.add(rectQuad(x, rect.y(), x + 1.0D, rect.bottom(), color));
        }
        long yOrdinal = (long) Math.ceil((rect.y() - zeroScreenY) / gridPx);
        for (double y = zeroScreenY + yOrdinal * gridPx; y < rect.bottom(); y += gridPx, yOrdinal++) {
            int color = Math.floorMod(yOrdinal, majorOrdinal) == 0 ? majorColor : gridColor;
            quads.add(rectQuad(rect.x(), y, rect.right(), y + 1.0D, color));
        }
        SmoothGuiPrimitives.quads(graphics, quads);
    }

    /** Axis-aligned solid rectangle matching what {@code GuiGraphicsExtractor.fill} rasterizes. */
    private static SmoothGuiPrimitives.GradientQuad rectQuad(double x0, double y0, double x1, double y1, int color) {
        return new SmoothGuiPrimitives.GradientQuad(new Vec2(x0, y0), new Vec2(x1, y0), new Vec2(x1, y1), new Vec2(x0, y1), color, color, color, color);
    }

    private void drawPhysicalStationFrames(GuiGraphicsExtractor graphics, PhysicalRouteMapGraph graph, ViewportState viewport, SPSGui.Rect rect, Aabb2 worldView) {
        for (PhysicalStationFrame frame : graph.stationFrames()) {
            if (!frame.worldBounds().intersects(worldView)) {
                continue;
            }
            // The outline geometry is hover-independent and only its color changes when
            // the station group is hovered, so both paint variants are captured once and
            // the replay picks one per the live hover -- no per-frame dash tessellation.
            double padWorld = Math.max(8.0D, (physicalNodeRadius(PhysicalNodeKind.PLATFORM, viewport.zoom()) + 7.0D) / Math.max(0.001D, scale(viewport)));
            double radiusX = Math.max(padWorld, (frame.worldBounds().maxX() - frame.worldBounds().minX()) * 0.5D + padWorld * 0.35D);
            double radiusZ = Math.max(padWorld, (frame.worldBounds().maxY() - frame.worldBounds().minY()) * 0.5D + padWorld * 0.35D);
            // The outline is tessellated in world space and only then projected, so the
            // ring deforms smoothly and continuously while the map rotates.
            List<Vec2> points = stationFrameWorldOutline(frame.centerX(), frame.centerZ(), radiusX, radiusZ, viewport).stream()
                    .map(point -> worldToScreen(point.x(), point.y(), viewport, rect))
                    .toList();
            List<GuiElementRenderState> normal = SmoothGuiPrimitives.captureBranch(() -> drawDashedPolyline(graphics, points, 1.2D, 0xAA1B2633, 7.0D, 5.0D));
            List<GuiElementRenderState> focused = SmoothGuiPrimitives.captureBranch(() -> drawDashedPolyline(graphics, points, 1.2D, FullRouteMapConfig.MAP_FOCUS_RING, 7.0D, 5.0D));
            UUID stationGroupId = frame.stationGroupId();
            SmoothGuiPrimitives.recordVariantSwitch(graphics, normal, focused, () -> liveHover.physicalStationGroup().filter(stationGroupId::equals).isPresent());
        }
    }

    /**
     * Station-frame outline tessellated in WORLD space: a stadium whose major axis is the
     * world axis with the larger radius — a rotation-invariant choice, unlike the old
     * screen-projected axes whose lengths crossed and flipped the capsule 90° at certain
     * bearings — with semicircle caps of the smaller radius, degenerating to a world
     * circle once the straight segment all but vanishes. The arc segment count depends
     * only on the world radius and the zoom (never on camera bearing or pitch), so the
     * outline suffers no axis flip, no capsule↔circle jump and no LOD pop while rotating.
     * Project every returned point through {@link #worldToScreen} for the screen polyline.
     */
    private static List<Vec2> stationFrameWorldOutline(double centerX, double centerZ, double radiusX, double radiusZ, ViewportState viewport) {
        boolean xMajor = radiusX >= radiusZ;
        double major = Math.max(radiusX, radiusZ);
        double minor = Math.max(1.0D, Math.min(radiusX, radiusZ));
        double halfSegment = Math.max(0.0D, major - minor);
        if (halfSegment <= minor * 0.04D) {
            // Nearly square bounds: a stable world-space circle instead of a capsule
            // whose axis choice would flip with the noise.
            halfSegment = 0.0D;
        }
        // Approximate the minor axis screen length from zoom alone; rotation must not
        // feed back into the tessellation density.
        int arcSegments = Math.max(16, Math.min(64, (int) Math.ceil(minor * scale(viewport) * 0.9D)));
        double ux = xMajor ? 1.0D : 0.0D;
        double uz = xMajor ? 0.0D : 1.0D;
        Vec2 capCenterA = new Vec2(centerX + ux * halfSegment, centerZ + uz * halfSegment);
        Vec2 capCenterB = new Vec2(centerX - ux * halfSegment, centerZ - uz * halfSegment);
        double baseAngle = xMajor ? -Math.PI * 0.5D : 0.0D;
        List<Vec2> points = new ArrayList<>(arcSegments * 2 + 3);
        appendArc(points, capCenterA, minor, baseAngle, baseAngle + Math.PI, arcSegments);
        appendArc(points, capCenterB, minor, baseAngle + Math.PI, baseAngle + Math.PI * 2.0D, arcSegments);
        points.add(points.getFirst());
        return points;
    }

    private static void appendArc(List<Vec2> points, Vec2 center, double radius, double startAngle, double endAngle, int segments) {
        for (int i = 0; i <= segments; i++) {
            if (!points.isEmpty() && i == 0) {
                continue;
            }
            double t = i / (double) segments;
            double angle = startAngle + (endAngle - startAngle) * t;
            points.add(new Vec2(center.x() + Math.cos(angle) * radius, center.y() + Math.sin(angle) * radius));
        }
    }

    private void drawPhysicalEdges(GuiGraphicsExtractor graphics, PhysicalRouteMapGraph graph, ViewportState viewport, SPSGui.Rect rect, Aabb2 worldView) {
        Map<String, List<PhysicalMapEdge>> groups = physicalEdgeGroups(graph.edges(), worldView);
        Set<String> trunkDotGroupKeys = physicalTrunkDotGroupKeys(groups, viewport, rect);
        for (Map.Entry<String, List<PhysicalMapEdge>> entry : groups.entrySet()) {
            List<PhysicalMapEdge> group = entry.getValue();
            if (group.isEmpty() || group.getFirst().points().size() < 2) {
                continue;
            }
            List<PhysicalRouteLane> lanes = physicalRouteLanes(group);
            if (lanes.isEmpty()) {
                continue;
            }
            List<Vec2> screenPath = group.getFirst().points().stream()
                    .map(point -> worldToScreen(point.x(), point.y(), viewport, rect))
                    .toList();
            Set<String> groupEdgeIds = new HashSet<>();
            for (PhysicalMapEdge edge : group) {
                groupEdgeIds.add(edge.id());
            }
            double laneWidth = FullRouteMapConfig.LINE_WIDTH_PX;
            double haloScale = physicalNodeScale(viewport.zoom());
            if (lanes.size() >= FullRouteMapConfig.TRUNK_THRESHOLD) {
                // Hover halo slot: re-evaluated against the live hover at replay time and
                // tessellated only while actually hovered; drawn before the trunk so the
                // halo stays underneath, exactly as the uncached render ordered it.
                SmoothGuiPrimitives.recordOrReplay(graphics, g -> {
                    if (liveHover.target() instanceof HitTarget.PhysicalEdgeHit(var hoveredEdgeId) && groupEdgeIds.contains(hoveredEdgeId)) {
                        drawPolyline(g, screenPath, 5.0D + 4.0D * haloScale, FullRouteMapConfig.MAP_FOCUS_HALO, true, 5.0D);
                    }
                });
                drawPolyline(graphics, screenPath, 5.0D, FullRouteMapConfig.MAP_TRUNK);
                if (trunkDotGroupKeys.contains(entry.getKey())) {
                    this.drawTrunkDotsForColors(graphics, screenPath, lanes.stream().map(FullRouteMapRenderer::physicalLanePrimaryColor).toList(), viewport.zoom());
                }
                continue;
            }
            double haloWidth = routeBundleWidth(lanes.stream().map(lane -> new EdgeLane(List.of(FullRouteMapConfig.MAP_TRUNK))).toList(), laneWidth) + 5.0D * haloScale;
            SmoothGuiPrimitives.recordOrReplay(graphics, g -> {
                if (liveHover.target() instanceof HitTarget.PhysicalEdgeHit(var hoveredEdgeId) && groupEdgeIds.contains(hoveredEdgeId)) {
                    drawPolyline(g, screenPath, haloWidth, FullRouteMapConfig.MAP_FOCUS_HALO, true, laneWidth);
                }
            });
            double step = laneWidth + 1.0D;
            double center = (lanes.size() - 1) * 0.5D;
            for (int i = 0; i < lanes.size(); i++) {
                PhysicalRouteLane lane = lanes.get(i);
                List<Integer> colors = physicalLaneColors(lane);
                List<Vec2> lanePath = offsetScreenPathAnchored(screenPath, (i - center) * step);
                double width = lane.fallback() ? Math.max(2.0D, laneWidth - 0.5D) : laneWidth;
                Set<String> laneEdgeIds = lane.edgeIds();
                SmoothGuiPrimitives.recordOrReplay(graphics, g -> {
                    if (liveHover.target() instanceof HitTarget.PhysicalEdgeHit(var hoveredEdgeId) && laneEdgeIds.contains(hoveredEdgeId)) {
                        drawPolyline(g, lanePath, width + 1.2D * haloScale, FullRouteMapConfig.MAP_FOCUS_RING);
                    }
                });
                if (lane.fallback()) {
                    drawDashedPolyline(graphics, lanePath, width, colors.getFirst(), 10.0D, 6.0D);
                } else {
                    drawColorLanePath(graphics, lanePath, width, colors);
                }
            }
        }
    }

    private static void drawPerspectiveGridBackground(GuiGraphicsExtractor graphics, SPSGui.Rect rect, ViewportState viewport, FullRouteMapLayoutMode mode) {
        int background = switch (mode) {
            case PHYSICAL -> FullMapTheme.PHYSICAL_BACKGROUND;
            case GEOGRAPHIC -> FullMapTheme.GEOGRAPHIC_BACKGROUND;
            case PRACTICAL -> FullRouteMapConfig.MAP_BACKGROUND;
            case SCHEMATIC -> FullMapTheme.SCHEMATIC_BACKGROUND;
        };
        int gridColor = switch (mode) {
            case PHYSICAL -> FullMapTheme.PHYSICAL_GRID;
            case GEOGRAPHIC -> FullMapTheme.GEOGRAPHIC_GRID;
            case PRACTICAL -> FullRouteMapConfig.MAP_GRID;
            case SCHEMATIC -> 0;
        };
        int majorColor = switch (mode) {
            case PHYSICAL -> FullMapTheme.PHYSICAL_GRID_MAJOR;
            case GEOGRAPHIC -> FullMapTheme.GEOGRAPHIC_GRID_MAJOR;
            case PRACTICAL -> FullRouteMapConfig.MAP_GRID_MAJOR;
            case SCHEMATIC -> 0;
        };
        double gridBlocks = switch (mode) {
            case PHYSICAL -> 32.0D;
            case GEOGRAPHIC -> 128.0D;
            case PRACTICAL -> 64.0D;
            case SCHEMATIC -> 64.0D;
        };
        int majorEvery = 4;
        int minGridPx = mode == FullRouteMapLayoutMode.PHYSICAL ? 12 : mode == FullRouteMapLayoutMode.GEOGRAPHIC ? 18 : 16;
        // Base fill and horizon band go into one quad batch (identical rectangles and
        // draw order as the previous fill() calls) so the perspective background is
        // fully capturable by the static-frame cache.
        List<SmoothGuiPrimitives.GradientQuad> baseQuads = new ArrayList<>(2);
        baseQuads.add(rectQuad(rect.x(), rect.y(), rect.right(), rect.bottom(), background));
        baseQuads.add(rectQuad(rect.x(), rect.y(), rect.right(), rect.y() + Math.max(1, rect.height() / 5), SPSGui.withAlpha(FullMapTheme.TEXT_MUTED, 0x08)));
        SmoothGuiPrimitives.quads(graphics, baseQuads);
        Aabb2 bounds = screenWorldBounds(viewport, rect).inflate(gridBlocks * 2.0D);
        double step = gridBlocks;
        while (step * scale(viewport) < minGridPx && step < 4096.0D) {
            step *= 2.0D;
        }
        double startX = Math.floor(bounds.minX() / step) * step;
        double endX = Math.ceil(bounds.maxX() / step) * step;
        double startZ = Math.floor(bounds.minY() / step) * step;
        double endZ = Math.ceil(bounds.maxY() / step) * step;
        graphics.enableScissor(rect.x(), rect.y(), rect.right(), rect.bottom());
        int sampleCount = Math.max(8, Math.min(28, rect.height() / 26));
        // Grid lines are nearly straight and sub-pixel thin, so the full smoothed-polyline
        // pipeline (cleaning, corner rounding, join circles, AA underlay) is wasted on them;
        // accumulate plain per-segment rectangles for every line and submit a single batch.
        List<SmoothGuiPrimitives.ThinStroke> gridStrokes = new ArrayList<>();
        int index = 0;
        for (double x = startX; x <= endX; x += step, index++) {
            int color = Math.floorMod((int) Math.round(x / step), majorEvery) == 0 ? majorColor : gridColor;
            List<Vec2> points = new ArrayList<>();
            for (int i = 0; i <= sampleCount; i++) {
                double z = startZ + (endZ - startZ) * i / sampleCount;
                points.add(worldToScreen(x, z, viewport, rect));
            }
            gridStrokes.add(new SmoothGuiPrimitives.ThinStroke(points, index % majorEvery == 0 ? 1.15D : 0.75D, color));
        }
        index = 0;
        for (double z = startZ; z <= endZ; z += step, index++) {
            int color = Math.floorMod((int) Math.round(z / step), majorEvery) == 0 ? majorColor : gridColor;
            List<Vec2> points = new ArrayList<>();
            for (int i = 0; i <= sampleCount; i++) {
                double x = startX + (endX - startX) * i / sampleCount;
                points.add(worldToScreen(x, z, viewport, rect));
            }
            gridStrokes.add(new SmoothGuiPrimitives.ThinStroke(points, index % majorEvery == 0 ? 1.15D : 0.75D, color));
        }
        SmoothGuiPrimitives.thinSegments(graphics, gridStrokes);
        graphics.disableScissor();
    }

    private void drawPhysicalNodes(GuiGraphicsExtractor graphics, Font font, PhysicalRouteMapGraph graph, ViewportState viewport, SPSGui.Rect rect, Aabb2 worldView) {
        for (PhysicalMapNode node : graph.nodes()) {
            if (!Aabb2.around(node.worldX(), node.worldZ(), 24.0D / Math.max(scale(viewport), 0.001D)).intersects(worldView)) {
                continue;
            }
            Vec2 screen = worldToScreen(node.worldX(), node.worldZ(), viewport, rect);
            double elementZoom = perspectiveElementZoom(viewport, rect, screen);
            double radius = physicalNodeRadius(node.kind(), elementZoom);
            double haloScale = physicalNodeScale(elementZoom);
            String nodeId = node.id();
            Optional<UUID> nodeStationGroup = node.stationGroupId();
            Optional<PipeAnchorId> nodeFoldAnchor = node.foldAnchorId();
            // Focus decoration slot: the condition (direct hover, same station group or
            // fold counterpart) is re-evaluated against the live hover at replay time.
            SmoothGuiPrimitives.recordOrReplay(graphics, g -> {
                boolean hovered = liveHover.target() instanceof HitTarget.PhysicalNodeHit(var hoveredNodeId) && hoveredNodeId.equals(nodeId)
                        || nodeStationGroup.flatMap(stationId -> liveHover.physicalStationGroup().filter(stationId::equals)).isPresent()
                        || nodeFoldAnchor.flatMap(anchor -> liveHover.physicalFoldPeer().filter(anchor::equals)).isPresent();
                if (hovered) {
                    if (node.kind() == PhysicalNodeKind.FOLD_ANCHOR) {
                        SmoothGuiPrimitives.diamond(g, screen, radius + 5.0D * haloScale, FullRouteMapConfig.MAP_FOCUS_HALO);
                        SmoothGuiPrimitives.diamond(g, screen, radius + 2.0D * haloScale, FullRouteMapConfig.MAP_FOCUS_RING);
                    } else {
                        SmoothGuiPrimitives.circle(g, screen, radius + 5.0D * haloScale, FullRouteMapConfig.MAP_FOCUS_HALO);
                        SmoothGuiPrimitives.circle(g, screen, radius + 2.0D * haloScale, FullRouteMapConfig.MAP_FOCUS_RING);
                    }
                }
            });
            if (node.kind() == PhysicalNodeKind.FOLD_ANCHOR) {
                drawDiamond(graphics, screen, (int) Math.round(radius), FullRouteMapConfig.MAP_FOLD_FILL, physicalFoldOutlineColor(graph, node), 1);
            } else {
                drawCircle(graphics, screen, (int) Math.round(radius), FullRouteMapConfig.MAP_NODE_FILL, FullRouteMapConfig.MAP_NODE_OUTLINE);
                if (node.routeLineIds().size() > 1 && viewport.zoom() >= 0.45D) {
                    drawClusterStripes(graphics, screen, (int) Math.round(radius), node.routeLineIds());
                }
            }
        }
    }

    private void drawPhysicalLabels(GuiGraphicsExtractor graphics, Font font, PhysicalRouteMapGraph graph, ViewportState viewport, SPSGui.Rect rect, Aabb2 worldView) {
        // Low-zoom trunk tier: the floor drops from 0.34 to 0.24; between the two only
        // station-frame labels exist here (platform labels already require zoom >= 1.15)
        // and they fade in linearly instead of popping at the old hard cutoff.
        if (viewport.zoom() < 0.24D) {
            return;
        }
        int rendered = 0;
        for (PhysicalStationFrame frame : graph.stationFrames()) {
            if (rendered >= FullRouteMapConfig.MAX_LABELS_PER_FRAME || !frame.worldBounds().intersects(worldView)) {
                continue;
            }
            Vec2 screen = worldToScreen(frame.centerX(), frame.centerZ(), viewport, rect);
            double elementZoom = perspectiveElementZoom(viewport, rect, screen);
            if (elementZoom < 0.24D) {
                continue;
            }
            // Trunk-tier fade band: alpha ramps 0 -> 1 across element zoom 0.24 -> 0.34.
            double labelFade = physicalTrunkLabelFade(elementZoom);
            int primaryColor = fadedLabelColor(FullRouteMapConfig.MAP_LABEL, labelFade);
            int secondaryColor = fadedLabelColor(FullRouteMapConfig.MAP_LABEL_MUTED, labelFade);
            DisplayNameStack frameLabel = ClientRouteDataCache.stationGroup(frame.stationGroupId()).map(FullMapText::displayNameStack).orElse(DisplayNameStack.of(frame.label()));
            if (elementZoom < 1.1D) {
                frameLabel = frameLabel.withoutSecondary();
            }
            float primaryScale = (float) Math.max(0.50D, Math.min(0.9D, 0.52D + elementZoom * 0.08D));
            float secondaryScale = Math.max(0.46F, primaryScale * 0.78F);
            int width = Math.min(144, FullMapUi.nameStackWidth(font, frameLabel, primaryScale, secondaryScale));
            int labelX = (int) Math.round(screen.x() - width * 0.5D);
            int labelY = (int) Math.round(screen.y() - 22.0D * primaryScale);
            // Label placement and text are resolved at capture time; the draw itself is
            // recorded and re-issued verbatim on replay (text is deterministic per frame).
            DisplayNameStack labelText = frameLabel;
            SmoothGuiPrimitives.recordOrReplay(graphics, g -> FullMapUi.drawNameStack(g, font, labelText, labelX, labelY, width, primaryColor, secondaryColor, primaryScale, secondaryScale, 0));
            rendered++;
        }
        if (viewport.zoom() < 1.15D) {
            return;
        }
        for (PhysicalMapNode node : graph.nodes()) {
            if (rendered >= FullRouteMapConfig.MAX_LABELS_PER_FRAME || node.kind() != PhysicalNodeKind.PLATFORM || !Aabb2.around(node.worldX(), node.worldZ(), 24.0D).intersects(worldView)) {
                continue;
            }
            Vec2 screen = worldToScreen(node.worldX(), node.worldZ(), viewport, rect);
            String text = SPSGui.ellipsize(font, node.label(), 120);
            double elementZoom = perspectiveElementZoom(viewport, rect, screen);
            if (elementZoom < 1.15D) {
                continue;
            }
            int labelX = (int) Math.round(screen.x() + physicalNodeRadius(node.kind(), elementZoom) + 5.0D);
            int labelY = (int) Math.round(screen.y() + 3.0D);
            SmoothGuiPrimitives.recordOrReplay(graphics, g -> SPSGui.smallText(g, font, text, labelX, labelY, FullRouteMapConfig.MAP_LABEL_MUTED, 0.58F));
            rendered++;
        }
    }

    private static PhysicalEdgeGroupIndex physicalEdgeGroupIndexCache;

    private static Map<String, List<PhysicalMapEdge>> physicalEdgeGroups(List<PhysicalMapEdge> edges, Aabb2 worldView) {
        PhysicalEdgeGroupIndex index = physicalEdgeGroupIndex(edges);
        Map<String, List<PhysicalMapEdge>> groups = new LinkedHashMap<>();
        for (PhysicalMapEdge edge : edges) {
            if (!edge.worldBounds().intersects(worldView)) {
                continue;
            }
            String key = index.keyByEdgeId().get(edge.id());
            if (key == null || groups.containsKey(key)) {
                continue;
            }
            groups.put(key, index.groupsByKey().get(key).stream()
                    .filter(candidate -> candidate.worldBounds().intersects(worldView))
                    .toList());
        }
        return groups;
    }

    /**
     * Groups and sorts every physical edge once and reuses the result until the edge list
     * or the route/pipe data revision changes. Group keys and the in-group display-name
     * sort are expensive (string keys, translated names), while the per-frame world-view
     * filter is cheap and is therefore applied to the cached, already-sorted groups.
     */
    private static PhysicalEdgeGroupIndex physicalEdgeGroupIndex(List<PhysicalMapEdge> edges) {
        long routeRevision = ClientRouteDataCache.revision();
        long pipeRevision = ClientPipeNetworkCache.aggregateRevision();
        PhysicalEdgeGroupIndex cached = physicalEdgeGroupIndexCache;
        if (cached != null && cached.sourceEdges() == edges && cached.routeRevision() == routeRevision && cached.pipeRevision() == pipeRevision) {
            return cached;
        }
        Map<String, List<PhysicalMapEdge>> groupsByKey = new LinkedHashMap<>();
        Map<String, String> keyByEdgeId = new HashMap<>();
        for (PhysicalMapEdge edge : edges) {
            String key = physicalEdgeGroupKey(edge);
            keyByEdgeId.put(edge.id(), key);
            groupsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(edge);
        }
        groupsByKey.values().forEach(group -> group.sort(Comparator
                .comparing((PhysicalMapEdge edge) -> ClientRouteDataCache.routeLine(edge.metadata().routeLineId()).map(FullMapText::displayName).orElse(""))
                .thenComparing(edge -> edge.metadata().routeLineId())
                .thenComparing(PhysicalMapEdge::id)));
        Map<String, List<PhysicalMapEdge>> groupByEdgeId = new HashMap<>();
        for (List<PhysicalMapEdge> group : groupsByKey.values()) {
            for (PhysicalMapEdge edge : group) {
                groupByEdgeId.put(edge.id(), group);
            }
        }
        PhysicalEdgeGroupIndex built = new PhysicalEdgeGroupIndex(edges, routeRevision, pipeRevision, groupsByKey, keyByEdgeId, groupByEdgeId);
        physicalEdgeGroupIndexCache = built;
        return built;
    }

    private static String physicalEdgeGroupKey(PhysicalMapEdge edge) {
        if (!edge.metadata().backingPathSlice().isEmpty()) {
            List<String> refs = edge.metadata().backingPathSlice().stream()
                    .map(ref -> ref.levelKey().identifier() + ":" + ref.connectionId())
                    .toList();
            return "refs:" + canonicalSequenceKey(refs);
        }
        List<String> points = edge.points().stream()
                .map(point -> Math.round(point.x() * 8.0D) + "," + Math.round(point.y() * 8.0D))
                .toList();
        return "points:" + canonicalSequenceKey(points);
    }

    private static String canonicalSequenceKey(List<String> values) {
        String forward = String.join("|", values);
        List<String> reversed = new ArrayList<>(values);
        java.util.Collections.reverse(reversed);
        String backward = String.join("|", reversed);
        return forward.compareTo(backward) <= 0 ? forward : backward;
    }

    private static List<PhysicalRouteLane> physicalRouteLanes(List<PhysicalMapEdge> group) {
        Map<UUID, List<PhysicalMapEdge>> edgesByLine = new LinkedHashMap<>();
        for (PhysicalMapEdge edge : group) {
            edgesByLine.computeIfAbsent(edge.metadata().routeLineId(), ignored -> new ArrayList<>()).add(edge);
        }
        List<PhysicalRouteLane> lanes = new ArrayList<>();
        for (Map.Entry<UUID, List<PhysicalMapEdge>> entry : edgesByLine.entrySet()) {
            List<PhysicalMapEdge> edges = entry.getValue().stream()
                    .sorted(Comparator.comparing(PhysicalMapEdge::id))
                    .toList();
            RouteLine line = ClientRouteDataCache.routeLine(entry.getKey()).orElse(null);
            boolean fallback = edges.stream().allMatch(edge -> edge.metadata().fallback());
            Set<String> edgeIds = edges.stream().map(PhysicalMapEdge::id).collect(java.util.stream.Collectors.toCollection(HashSet::new));
            lanes.add(new PhysicalRouteLane(entry.getKey(), Optional.ofNullable(line), edges.getFirst(), edgeIds, fallback));
        }
        lanes.sort(Comparator
                .comparing((PhysicalRouteLane lane) -> lane.line().map(FullMapText::displayName).orElse(""))
                .thenComparing(PhysicalRouteLane::routeLineId));
        return lanes;
    }

    private static List<Integer> physicalLaneColors(PhysicalRouteLane lane) {
        return lane.line().map(FullRouteMapRenderer::routeLineColors).orElse(List.of(FullRouteMapConfig.MAP_TRUNK));
    }

    private static int physicalLanePrimaryColor(PhysicalRouteLane lane) {
        return physicalLaneColors(lane).getFirst();
    }

    private static PhysicalMapEdge physicalTrunkHitEdge(List<PhysicalRouteLane> lanes, List<Vec2> screenPath, Vec2 mouse, double zoom) {
        if (lanes.isEmpty()) {
            throw new IllegalArgumentException("Physical trunk hit requires at least one lane");
        }
        List<Vec2> anchors = trunkDotCenters(screenPath, lanes.stream().map(FullRouteMapRenderer::physicalLanePrimaryColor).toList(), zoom);
        double radius = trunkDotRadius(zoom) + 3.0D;
        double best = Double.POSITIVE_INFINITY;
        PhysicalMapEdge bestEdge = lanes.getFirst().representative();
        for (int i = 0; i < anchors.size() && i < lanes.size(); i++) {
            double distance = anchors.get(i).distanceTo(mouse);
            if (distance <= radius && distance < best) {
                best = distance;
                bestEdge = lanes.get(i).representative();
            }
        }
        return bestEdge;
    }

    private static Set<String> physicalTrunkDotGroupKeys(Map<String, List<PhysicalMapEdge>> groups, ViewportState viewport, SPSGui.Rect rect) {
        if (viewport.zoom() < FullRouteMapConfig.TRUNK_DOT_MIN_ZOOM) {
            return Set.of();
        }
        Vec2 screenCenter = new Vec2(rect.x() + rect.width() * 0.5D, rect.y() + rect.height() * 0.5D);
        List<PhysicalTrunkCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, List<PhysicalMapEdge>> entry : groups.entrySet()) {
            List<PhysicalRouteLane> lanes = physicalRouteLanes(entry.getValue());
            if (lanes.size() < FullRouteMapConfig.TRUNK_THRESHOLD || entry.getValue().isEmpty() || entry.getValue().getFirst().points().size() < 2) {
                continue;
            }
            List<Vec2> screenPath = entry.getValue().getFirst().points().stream()
                    .map(point -> worldToScreen(point.x(), point.y(), viewport, rect))
                    .toList();
            if (screenPath.size() < 2) {
                continue;
            }
            String lineKey = lanes.stream()
                    .map(lane -> lane.routeLineId().toString())
                    .sorted()
                    .collect(java.util.stream.Collectors.joining("|"));
            Vec2 midpoint = pointAlongPolyline(screenPath, polylineLength(screenPath) * 0.5D);
            double score = midpoint.distanceTo(screenCenter);
            candidates.add(new PhysicalTrunkCandidate(entry.getKey(), lineKey, screenPath.getFirst(), screenPath.getLast(), score));
        }
        if (candidates.isEmpty()) {
            return Set.of();
        }
        Set<String> selected = new HashSet<>();
        Set<Integer> visited = new HashSet<>();
        for (int i = 0; i < candidates.size(); i++) {
            if (!visited.add(i)) {
                continue;
            }
            List<Integer> component = new ArrayList<>();
            component.add(i);
            for (int cursor = 0; cursor < component.size(); cursor++) {
                PhysicalTrunkCandidate current = candidates.get(component.get(cursor));
                for (int j = 0; j < candidates.size(); j++) {
                    if (visited.contains(j)) {
                        continue;
                    }
                    PhysicalTrunkCandidate other = candidates.get(j);
                    if (current.lineKey().equals(other.lineKey()) && trunkCandidatesTouch(current, other)) {
                        visited.add(j);
                        component.add(j);
                    }
                }
            }
            int best = component.stream()
                    .min(Comparator.comparingDouble(index -> candidates.get(index).score()))
                    .orElse(i);
            selected.add(candidates.get(best).groupKey());
        }
        return selected;
    }

    private static boolean trunkCandidatesTouch(PhysicalTrunkCandidate first, PhysicalTrunkCandidate second) {
        double tolerance = FullRouteMapConfig.LINE_WIDTH_PX + 4.0D;
        return first.start().distanceTo(second.start()) <= tolerance
                || first.start().distanceTo(second.end()) <= tolerance
                || first.end().distanceTo(second.start()) <= tolerance
                || first.end().distanceTo(second.end()) <= tolerance;
    }

    public static List<UUID> physicalRouteLineIdsForEdge(PhysicalRouteMapGraph graph, String edgeId) {
        return physicalEdgeGroupForEdge(graph, edgeId).stream()
                .map(FullRouteMapRenderer::physicalRouteLanes)
                .findFirst()
                .orElse(List.of())
                .stream()
                .map(PhysicalRouteLane::routeLineId)
                .toList();
    }

    public static Optional<PhysicalMapEdge> physicalRepresentativeEdgeForRouteLine(PhysicalRouteMapGraph graph, String edgeId, UUID routeLineId) {
        return physicalEdgeGroupForEdge(graph, edgeId).stream()
                .flatMap(List::stream)
                .filter(edge -> edge.metadata().routeLineId().equals(routeLineId))
                .findFirst();
    }

    private static Optional<List<PhysicalMapEdge>> physicalEdgeGroupForEdge(PhysicalRouteMapGraph graph, String edgeId) {
        if (graph.edge(edgeId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(physicalEdgeGroupIndex(graph.edges()).groupByEdgeId().get(edgeId));
    }

    private void drawVisualTransferHints(GuiGraphicsExtractor graphics, MapDimensionGraph graph, VisualRouteMapGraph visualGraph, ViewportState viewport, SPSGui.Rect rect, Aabb2 visualView) {
        if (viewport.zoom() < 0.5D) {
            return;
        }
        for (MapTransferHint hint : graph.transferHints()) {
            Optional<SegmentScreen> segment = visualTransferSegment(graph, visualGraph, hint, viewport, rect);
            if (segment.isEmpty()) {
                continue;
            }
            if (!visualView.intersects(Aabb2.empty()
                    .include(screenToWorld(segment.get().a().x(), segment.get().a().y(), viewport, rect).x(), screenToWorld(segment.get().a().x(), segment.get().a().y(), viewport, rect).y())
                    .include(screenToWorld(segment.get().b().x(), segment.get().b().y(), viewport, rect).x(), screenToWorld(segment.get().b().x(), segment.get().b().y(), viewport, rect).y())
                    .inflate(16.0D))) {
                continue;
            }
            // Hover swaps the whole hint paint (halo, thicker dash, larger icon): both
            // variants are captured once and switched by the live hover at replay time.
            Vec2 a = segment.get().a();
            Vec2 b = segment.get().b();
            Vec2 mid = midpoint(a, b);
            int normalIconSize = Math.max(4, (int) Math.round(8 * iconScale(viewport.zoom())));
            int focusedIconSize = Math.max(4, (int) Math.round(10 * iconScale(viewport.zoom())));
            List<GuiElementRenderState> normal = SmoothGuiPrimitives.captureBranch(() -> {
                drawDashedLine(graphics, a, b, 1, FullRouteMapConfig.MAP_TRANSFER_HINT, 4.0D, 4.0D);
                drawPedestrianIcon(graphics, mid, normalIconSize, FullRouteMapConfig.MAP_TRANSFER_HINT);
            });
            List<GuiElementRenderState> focused = SmoothGuiPrimitives.captureBranch(() -> {
                drawDashedLine(graphics, a, b, 2.0D + 2.0D * iconScale(viewport.zoom()), FullRouteMapConfig.MAP_FOCUS_HALO, 4.0D, 4.0D);
                drawDashedLine(graphics, a, b, 2, FullRouteMapConfig.MAP_TRANSFER_HINT, 4.0D, 4.0D);
                drawPedestrianIcon(graphics, mid, focusedIconSize, FullRouteMapConfig.MAP_TRANSFER_HINT);
            });
            String hintId = hint.id();
            SmoothGuiPrimitives.recordVariantSwitch(graphics, normal, focused, () -> liveHover.target() instanceof HitTarget.TransferHintHit(var hoveredHintId) && hoveredHintId.equals(hintId));
        }
    }

    private void drawPhysicalMissingCrossDimensionHints(GuiGraphicsExtractor graphics, PhysicalRouteMapGraph graph, ViewportState viewport, SPSGui.Rect rect) {
        if (graph.missingCrossDimensionPathHints().isEmpty()) {
            return;
        }
        if (viewport.zoom() < 0.35D) {
            // Zoomed-out overview: the fading arrows shrink below readability at this
            // scale and used to vanish entirely, so every problem area keeps a
            // fixed-screen-size warning badge pinned to its anchor node instead. The
            // badge is pure paint -- hit testing, and therefore tooltip and click
            // behavior, is untouched.
            for (PhysicalMissingCrossDimensionPathHint hint : graph.missingCrossDimensionPathHints()) {
                Optional<MissingHintAnchor> anchor = physicalMissingCrossDimensionHintAnchor(graph, hint, viewport, rect);
                if (anchor.isPresent()) {
                    drawMissingHintBadge(graphics, anchor.get(), hint.id());
                }
            }
            return;
        }
        List<List<Vec2>> blockers = physicalMissingHintBlockers(graph, viewport, rect);
        for (PhysicalMissingCrossDimensionPathHint hint : graph.missingCrossDimensionPathHints()) {
            Optional<SegmentScreen> segment = physicalMissingCrossDimensionHintSegment(graph, hint, viewport, rect, blockers);
            if (segment.isEmpty()) {
                continue;
            }
            // Hover only adds the halo underneath; both paint variants are captured once.
            List<Integer> colors = ClientRouteDataCache.routeLine(hint.routeLineId())
                    .map(FullRouteMapRenderer::routeLineColors)
                    .orElse(List.of(FullRouteMapConfig.MAP_TRUNK));
            Vec2 a = segment.get().a();
            Vec2 b = segment.get().b();
            List<GuiElementRenderState> normal = SmoothGuiPrimitives.captureBranch(() -> drawFadingDashedRouteLine(graphics, a, b, Math.max(2.0D, FullRouteMapConfig.LINE_WIDTH_PX - 0.2D), colors));
            List<GuiElementRenderState> focused = SmoothGuiPrimitives.captureBranch(() -> {
                drawFadingDashedRouteLine(graphics, a, b, FullRouteMapConfig.LINE_WIDTH_PX + 5.0D * iconScale(viewport.zoom()), List.of(FullRouteMapConfig.MAP_FOCUS_HALO));
                drawFadingDashedRouteLine(graphics, a, b, Math.max(2.0D, FullRouteMapConfig.LINE_WIDTH_PX - 0.2D), colors);
            });
            String hintId = hint.id();
            SmoothGuiPrimitives.recordVariantSwitch(graphics, normal, focused, () -> liveHover.target() instanceof HitTarget.MissingCrossDimensionPathHit(var hoveredHintId) && hoveredHintId.equals(hintId));
        }
    }

    private void drawMissingCrossDimensionHints(GuiGraphicsExtractor graphics, MapDimensionGraph graph, VisualRouteMapGraph visualGraph, ViewportState viewport, SPSGui.Rect rect) {
        if (graph.missingCrossDimensionPathHints().isEmpty()) {
            return;
        }
        if (viewport.zoom() < 0.35D) {
            // Zoomed-out overview: the fading arrows shrink below readability at this
            // scale and used to vanish entirely, so every problem area keeps a
            // fixed-screen-size warning badge pinned to its anchor node instead. The
            // badge is pure paint -- hit testing, and therefore tooltip and click
            // behavior, is untouched.
            for (MissingCrossDimensionPathHint hint : graph.missingCrossDimensionPathHints()) {
                Optional<MissingHintAnchor> anchor = missingCrossDimensionHintAnchor(graph, visualGraph, hint, viewport, rect);
                if (anchor.isPresent()) {
                    drawMissingHintBadge(graphics, anchor.get(), hint.id());
                }
            }
            return;
        }
        List<List<Vec2>> blockers = missingHintBlockers(graph, visualGraph, viewport, rect);
        for (MissingCrossDimensionPathHint hint : graph.missingCrossDimensionPathHints()) {
            Optional<SegmentScreen> segment = missingCrossDimensionHintSegment(graph, visualGraph, hint, viewport, rect, blockers);
            if (segment.isEmpty()) {
                continue;
            }
            // Hover only adds the halo underneath; both paint variants are captured once.
            List<Integer> colors = ClientRouteDataCache.routeLine(hint.routeLineId())
                    .map(FullRouteMapRenderer::routeLineColors)
                    .orElse(List.of(FullRouteMapConfig.MAP_TRUNK));
            Vec2 a = segment.get().a();
            Vec2 b = segment.get().b();
            List<GuiElementRenderState> normal = SmoothGuiPrimitives.captureBranch(() -> drawFadingDashedRouteLine(graphics, a, b, Math.max(2.0D, FullRouteMapConfig.LINE_WIDTH_PX - 0.2D), colors));
            List<GuiElementRenderState> focused = SmoothGuiPrimitives.captureBranch(() -> {
                drawFadingDashedRouteLine(graphics, a, b, FullRouteMapConfig.LINE_WIDTH_PX + 5.0D * iconScale(viewport.zoom()), List.of(FullRouteMapConfig.MAP_FOCUS_HALO));
                drawFadingDashedRouteLine(graphics, a, b, Math.max(2.0D, FullRouteMapConfig.LINE_WIDTH_PX - 0.2D), colors);
            });
            String hintId = hint.id();
            SmoothGuiPrimitives.recordVariantSwitch(graphics, normal, focused, () -> liveHover.target() instanceof HitTarget.MissingCrossDimensionPathHit(var hoveredHintId) && hoveredHintId.equals(hintId));
        }
    }

    private void drawVisualFoldPeerIndicator(GuiGraphicsExtractor graphics, MapDimensionGraph graph, VisualRouteMapGraph visualGraph, ViewportState viewport, SPSGui.Rect rect, Aabb2 visualView) {
        // Pure hover overlay: skip recording entirely and recompute at replay time from
        // the live hover (it is empty unless a fold anchor node is hovered).
        SmoothGuiPrimitives.recordOrReplay(graphics, g -> {
            if (!(liveHover.target() instanceof HitTarget.NodeHit(var hoveredNodeId))) {
                return;
            }
            MapNode node = graph.node(hoveredNodeId).orElse(null);
            if (node == null || node.kind() != NodeKind.FOLD_ANCHOR || node.foldPeerId().isEmpty()) {
                return;
            }
            MapNode peer = foldPeerNode(graph, node)
                    .flatMap(rawPeer -> graph.node(displayNodeId(graph, rawPeer.id(), viewport.zoom())).or(() -> Optional.of(rawPeer)))
                    .orElse(null);
            if (peer == null) {
                return;
            }
            Vec2 peerVisual = visualPosition(graph, visualGraph, peer);
            if (!visualView.contains(peerVisual.x(), peerVisual.y())) {
                return;
            }
            Vec2 a = screenForNode(graph, visualGraph, node, viewport, rect);
            Vec2 b = screenForNode(graph, visualGraph, peer, viewport, rect);
            int color = foldOutlineColor(graph, node);
            drawDashedLine(g, a, b, 1, SPSGui.withAlpha(color, 0xAA), 4.0D, 4.0D);
            drawArrow(g, a, b, SPSGui.withAlpha(color, 0xCC));
            drawArrow(g, b, a, SPSGui.withAlpha(color, 0xCC));
        });
    }

    private EdgeBlockerIndex drawVisualEdges(GuiGraphicsExtractor graphics, MapDimensionGraph graph, VisualRouteMapGraph visualGraph, ViewportState viewport, SPSGui.Rect rect, Aabb2 visualView) {
        // Every painted lane is also registered with the label edge-avoidance index as
        // it is drawn, so drawVisualLabels can solve placements against exactly the
        // strokes on screen. The index is pure bookkeeping -- it emits no draw calls.
        EdgeBlockerIndex edgeBlockers = new EdgeBlockerIndex();
        Map<String, MapEdge> rawEdges = graph.edges().stream().collect(HashMap::new, (map, edge) -> map.put(edge.id(), edge), HashMap::putAll);
        for (VisualEdgePath path : visualGraph.edgePaths()) {
            MapEdge edge = rawEdges.get(path.edgeId());
            if (edge == null) {
                continue;
            }
            Optional<List<Vec2>> visualPath = visualWorldPathForEdge(graph, visualGraph, path, edge, viewport.zoom());
            if (visualPath.isEmpty() || !visualView.intersects(boundsForPath(visualPath.get()).inflate(32.0D / scale(viewport)))) {
                continue;
            }
            List<Vec2> screenPath = visualPath.get().stream()
                    .map(point -> worldToScreen(point.x(), point.y(), viewport, rect))
                    .toList();
            String edgeId = edge.id();
            BooleanSupplier hovered = () -> liveHover.target() instanceof HitTarget.EdgeHit(var hoveredEdgeId) && hoveredEdgeId.equals(edgeId);
            List<RouteLine> lines = routeLinesForIds(path.routeLineIds());
            if (lines.size() >= FullRouteMapConfig.TRUNK_THRESHOLD) {
                double trunkWidth = Math.max(1.6D, 5.0D * lineWidthScale(viewport.zoom()));
                double trunkHaloWidth = trunkWidth + 4.0D * iconScale(viewport.zoom());
                SmoothGuiPrimitives.recordOrReplay(graphics, g -> {
                    if (hovered.getAsBoolean()) {
                        drawPolyline(g, screenPath, trunkHaloWidth, FullRouteMapConfig.MAP_FOCUS_HALO, true, trunkWidth);
                    }
                });
                drawPolyline(graphics, screenPath, trunkWidth, FullRouteMapConfig.MAP_TRUNK);
                addEdgeBlockerPath(edgeBlockers, screenPath, 2.5D);
                this.drawTrunkDots(graphics, screenPath, lines, viewport.zoom());
            } else {
                this.drawVisualEdgeRouteBundle(graphics, screenPath, lines, hovered, path.kind(), viewport.zoom(), edgeBlockers);
            }
        }
        return edgeBlockers;
    }

    private void drawVisualNodes(GuiGraphicsExtractor graphics, Font font, MapDimensionGraph graph, VisualRouteMapGraph visualGraph, ViewportState viewport, SPSGui.Rect rect, Aabb2 visualView) {
        for (MapNode node : graph.nodes()) {
            if (!isVisibleNode(node, viewport.zoom())) {
                continue;
            }
            Vec2 visual = visualPosition(graph, visualGraph, node);
            if (!visualView.contains(visual.x(), visual.y())) {
                continue;
            }
            Vec2 screen = worldToScreen(visual.x(), visual.y(), viewport, rect);
            double elementZoom = perspectiveElementZoom(viewport, rect, screen);
            NodeId nodeId = node.id();
            NodeId displayId = displayNodeId(graph, node.id(), viewport.zoom());
            int radius = nodeRadius(node, elementZoom);
            Optional<Vec2> transferAxis = node.kind() == NodeKind.STATION && node.isTransferStation()
                    ? transferAxis(visualGraph, node.id(), viewport, rect)
                    : Optional.empty();
            // Focus decoration slot: re-evaluated against the live hover at replay time
            // (direct hover, transfer-hint endpoint or fold peer highlight).
            SmoothGuiPrimitives.recordOrReplay(graphics, g -> {
                boolean hovered = liveHover.target() instanceof HitTarget.NodeHit(var hoveredNodeId) && hoveredNodeId.equals(nodeId)
                        || liveHover.transferHighlightNodes().contains(displayId)
                        || liveHover.foldPeerHighlightId().filter(nodeId::equals).isPresent();
                if (hovered) {
                    if (transferAxis.isPresent()) {
                        drawDirectedTransferFocus(g, screen, radius, transferAxis.get(), elementZoom);
                    } else {
                        drawNodeFocus(g, node, screen, radius, elementZoom);
                    }
                }
            });
            switch (node.kind()) {
                case FOLD_ANCHOR -> drawDiamond(graphics, screen, radius, FullRouteMapConfig.MAP_FOLD_FILL, foldOutlineColor(graph, node), 1);
                case CLUSTER -> {
                    drawCircle(graphics, screen, radius, FullRouteMapConfig.MAP_CLUSTER_FILL, FullRouteMapConfig.MAP_CLUSTER_OUTLINE);
                    drawClusterStripes(graphics, screen, radius, node.routeLineIds());
                    String count = Integer.toString(node.stationGroupIds().size());
                    if (radius >= 7) {
                        int textX = (int) screen.x() + radius - 2;
                        int textY = (int) screen.y() + radius - 4;
                        SmoothGuiPrimitives.recordOrReplay(graphics, g -> SPSGui.smallText(g, font, count, textX, textY, FullRouteMapConfig.MAP_CLUSTER_OUTLINE, 0.68F));
                    }
                }
                case DEEP_CLUSTER -> {
                    drawDeepCluster(graphics, screen, radius, FullRouteMapConfig.MAP_CLUSTER_FILL, FullRouteMapConfig.MAP_CLUSTER_OUTLINE);
                    drawClusterStripes(graphics, screen, radius, node.routeLineIds());
                    String count = Integer.toString(node.stationGroupIds().size());
                    if (radius >= 7) {
                        int textX = (int) screen.x() + radius - 2;
                        int textY = (int) screen.y() + radius - 4;
                        SmoothGuiPrimitives.recordOrReplay(graphics, g -> SPSGui.smallText(g, font, count, textX, textY, FullRouteMapConfig.MAP_CLUSTER_OUTLINE, 0.68F));
                    }
                }
                case STATION -> {
                    if (node.isTransferStation()) {
                        drawTransferStationGlyph(graphics, screen, radius, transferAxis);
                    } else {
                        drawCircle(graphics, screen, radius, FullRouteMapConfig.MAP_NODE_FILL, FullRouteMapConfig.MAP_NODE_OUTLINE);
                    }
                }
            }
            if (hasStationInternalEdge(graph, node.id())) {
                drawStationInternalMarker(graphics, screen, radius);
            }
        }
    }

    private void drawVisualLabels(GuiGraphicsExtractor graphics, Font font, MapDimensionGraph graph, VisualRouteMapGraph visualGraph, ViewportState viewport, SPSGui.Rect rect, Aabb2 visualView, EdgeBlockerIndex edgeBlockers) {
        // Low-zoom trunk tier: the floor drops from 0.45 to 0.30; between the two only
        // trunk-tier nodes are labelled (see shouldConsiderLabel) and their alpha fades
        // in linearly instead of popping at the old hard cutoff.
        if (viewport.zoom() < 0.30D) {
            return;
        }
        double labelFade = trunkLabelFade(viewport.zoom());
        int labelPrimaryColor = fadedLabelColor(FullRouteMapConfig.MAP_LABEL, labelFade);
        int labelSecondaryColor = fadedLabelColor(FullRouteMapConfig.MAP_LABEL_MUTED, labelFade);
        List<IconBlocker> iconBlockers = new ArrayList<>();
        for (MapNode node : graph.nodes()) {
            if (!isVisibleNode(node, viewport.zoom())) {
                continue;
            }
            Vec2 visual = visualPosition(graph, visualGraph, node);
            if (!visualView.contains(visual.x(), visual.y())) {
                continue;
            }
            Vec2 screen = worldToScreen(visual.x(), visual.y(), viewport, rect);
            iconBlockers.add(new IconBlocker(node.id(), iconBounds(node, screen, perspectiveElementZoom(viewport, rect, screen))));
        }

        List<SPSGui.Rect> placed = new ArrayList<>();
        int rendered = 0;
        List<VisualLabel> labels = visualGraph.labels().stream()
                .sorted(Comparator.comparingInt(VisualLabel::priority).reversed())
                .toList();
        boolean denseZoom = viewport.zoom() >= 3.0D;
        for (VisualLabel label : labels) {
            if (rendered >= FullRouteMapConfig.MAX_LABELS_PER_FRAME) {
                break;
            }
            MapNode node = graph.node(label.nodeId()).orElse(null);
            if (node == null || !isVisibleNode(node, viewport.zoom())) {
                continue;
            }
            Vec2 nodeVisual = visualPosition(graph, visualGraph, node);
            if (!visualView.contains(label.x(), label.z()) && !(denseZoom && visualView.contains(nodeVisual.x(), nodeVisual.y()))) {
                continue;
            }
            Vec2 nodeScreen = worldToScreen(nodeVisual.x(), nodeVisual.y(), viewport, rect);
            double elementZoom = perspectiveElementZoom(viewport, rect, nodeScreen);
            if (!perspectiveAllowsLabel(viewport, rect, nodeScreen, node) || !shouldConsiderLabel(node, elementZoom)) {
                continue;
            }
            DisplayNameStack text = visualLabelText(label, node, elementZoom);
            float scale = visualLabelScale(label, node, elementZoom);
            float secondaryScale = Math.max(0.48F, scale * 0.78F);
            int width = Math.max(1, FullMapUi.nameStackWidth(font, text, scale, secondaryScale));
            int height = Math.max(7, FullMapUi.nameStackHeight(text, scale, secondaryScale, 0));
            Vec2 screen = worldToScreen(label.x(), label.z(), viewport, rect);
            // The solver's node-to-label offset is a layout-space constant, so the
            // on-screen gap would otherwise grow linearly with zoom while the font size
            // stays capped. Clamp the screen-space distance to the node, keeping the
            // solver's chosen side/direction; the clamped anchor then flows through the
            // regular bounds/collision solving unchanged.
            screen = clampLabelAnchorToNode(screen, nodeScreen, nodeCollisionRadius(node, elementZoom) + 20.0D);
            SPSGui.Rect bounds = new SPSGui.Rect((int) Math.round(screen.x()), (int) Math.round(screen.y()), width, height);
            SPSGui.Rect selected = null;
            if (rectsOverlap(bounds, rect) && !labelBlockedByPlaced(bounds, placed) && !labelBlockedByIcons(label.nodeId(), bounds, iconBlockers) && !labelBlockedByEdges(bounds, edgeBlockers)) {
                selected = bounds;
            } else if (denseZoom) {
                LabelCandidate fallback = new LabelCandidate(node, text, labelBounds(node, nodeScreen, elementZoom, rect, width, height), label.priority(), scale);
                selected = chooseDenseVisualLabelBounds(fallback, placed, iconBlockers, edgeBlockers).orElse(null);
                if (selected == null && rectsOverlap(bounds, rect) && shouldForceDenseLabel(node, label.priority())) {
                    selected = bounds;
                }
            }
            if (selected == null) {
                continue;
            }
            placed.add(selected);
            // Label placement is solved at capture time; only the deterministic text
            // draw is re-issued on replay.
            SPSGui.Rect labelPosition = selected;
            DisplayNameStack labelText = text;
            float labelScaleValue = scale;
            float labelSecondaryScale = secondaryScale;
            SmoothGuiPrimitives.recordOrReplay(graphics, g -> FullMapUi.drawNameStack(g, font, labelText, labelPosition.x(), labelPosition.y(), labelPosition.width(), labelPrimaryColor, labelSecondaryColor, labelScaleValue, labelSecondaryScale, 0));
            rendered++;
        }
    }

    private void drawPureSchematicEdges(GuiGraphicsExtractor graphics, VisualRouteMapGraph visualGraph, ViewportState viewport, SPSGui.Rect rect, Aabb2 visualView) {
        for (VisualEdgePath path : visualGraph.edgePaths()) {
            if (path.points().size() < 2 || !visualView.intersects(path.bounds().inflate(32.0D / scale(viewport)))) {
                continue;
            }
            List<Vec2> screenPath = path.points().stream()
                    .map(point -> worldToScreen(point.x(), point.y(), viewport, rect))
                    .toList();
            String edgeId = path.edgeId();
            List<UUID> routeLineIds = path.routeLineIds();
            // Hover halo slot: also lit while the schematic legend highlights a route
            // line running over this edge; re-evaluated live at replay time.
            BooleanSupplier hovered = () -> liveHover.target() instanceof HitTarget.EdgeHit(var hoveredEdgeId) && hoveredEdgeId.equals(edgeId)
                    || liveHover.highlightedRouteLineId().filter(routeLineIds::contains).isPresent();
            List<RouteLine> lines = routeLinesForIds(path.routeLineIds());
            if (lines.size() >= FullRouteMapConfig.TRUNK_THRESHOLD) {
                double trunkWidth = Math.max(1.6D, 5.0D * lineWidthScale(viewport.zoom()));
                double trunkHaloWidth = trunkWidth + 4.0D * iconScale(viewport.zoom());
                SmoothGuiPrimitives.recordOrReplay(graphics, g -> {
                    if (hovered.getAsBoolean()) {
                        drawPolyline(g, screenPath, trunkHaloWidth, FullRouteMapConfig.MAP_FOCUS_HALO, false, trunkWidth);
                    }
                });
                drawPolyline(graphics, screenPath, trunkWidth, FullRouteMapConfig.MAP_TRUNK, false);
                this.drawTrunkDots(graphics, screenPath, lines, viewport.zoom());
            } else {
                this.drawPureSchematicEdgeRouteBundle(graphics, screenPath, path, hovered, viewport.zoom());
            }
        }
    }

    private void drawPureSchematicNodes(GuiGraphicsExtractor graphics, Font font, MapDimensionGraph graph, VisualRouteMapGraph visualGraph, ViewportState viewport, SPSGui.Rect rect, Aabb2 visualView) {
        List<VisualNode> ordered = visualGraph.nodes().stream()
                .sorted(Comparator.comparing((VisualNode node) -> node.kind() == NodeKind.FOLD_ANCHOR ? 0 : 1))
                .toList();
        for (VisualNode visualNode : ordered) {
            if (!visualView.contains(visualNode.x(), visualNode.z())) {
                continue;
            }
            Vec2 screen = worldToScreen(visualNode.x(), visualNode.z(), viewport, rect);
            NodeId nodeId = visualNode.id();
            // Focus decoration slot: direct hover or highlighted-edge/route endpoint,
            // re-evaluated against the live hover at replay time.
            BooleanSupplier hovered = () -> liveHover.target() instanceof HitTarget.NodeHit(var hoveredNodeId) && hoveredNodeId.equals(nodeId) || liveHover.schematicHighlightNodes().contains(nodeId);
            double elementZoom = perspectiveElementZoom(viewport, rect, screen);
            if (visualNode.kind() == NodeKind.FOLD_ANCHOR) {
                int radius = visualNodeRadius(visualNode, graph, elementZoom);
                SmoothGuiPrimitives.recordOrReplay(graphics, g -> {
                    if (hovered.getAsBoolean()) {
                        drawPortalFocus(g, screen, radius, elementZoom);
                    }
                });
                drawPortalIcon(graphics, screen, radius, visualNode);
                continue;
            }
            MapNode raw = graph.node(visualNode.id()).orElse(null);
            if (raw == null) {
                continue;
            }
            int radius = nodeRadius(raw, elementZoom);
            Optional<Vec2> transferAxis = raw.isTransferStation() ? transferAxis(visualGraph, raw.id(), viewport, rect) : Optional.empty();
            SmoothGuiPrimitives.recordOrReplay(graphics, g -> {
                if (hovered.getAsBoolean()) {
                    if (transferAxis.isPresent()) {
                        drawDirectedTransferFocus(g, screen, radius, transferAxis.get(), elementZoom);
                    } else {
                        drawNodeFocus(g, raw, screen, radius, elementZoom);
                    }
                }
            });
            if (raw.isTransferStation()) {
                drawTransferStationGlyph(graphics, screen, radius, transferAxis);
            } else {
                drawCircle(graphics, screen, radius, FullRouteMapConfig.MAP_NODE_FILL, FullRouteMapConfig.MAP_NODE_OUTLINE);
            }
        }
    }

    private List<SPSGui.Rect> drawPureSchematicLabels(GuiGraphicsExtractor graphics, Font font, MapDimensionGraph graph, VisualRouteMapGraph visualGraph, ViewportState viewport, SPSGui.Rect rect, Aabb2 visualView) {
        // Low-zoom trunk tier: the floor drops from 0.45 to 0.30; between the two only
        // trunk-tier stations are labelled and their alpha fades in linearly instead of
        // popping at the old hard cutoff.
        if (viewport.zoom() < 0.30D) {
            return List.of();
        }
        double labelFade = trunkLabelFade(viewport.zoom());
        int labelPrimaryColor = fadedLabelColor(FullRouteMapConfig.MAP_LABEL, labelFade);
        int labelSecondaryColor = fadedLabelColor(FullRouteMapConfig.MAP_LABEL_MUTED, labelFade);
        List<IconBlocker> iconBlockers = new ArrayList<>();
        for (VisualNode visualNode : visualGraph.nodes()) {
            if (!visualView.contains(visualNode.x(), visualNode.z())) {
                continue;
            }
            Vec2 screen = worldToScreen(visualNode.x(), visualNode.z(), viewport, rect);
            iconBlockers.add(new IconBlocker(visualNode.id(), visualIconBounds(visualNode, graph, screen, viewport.zoom())));
        }

        List<SPSGui.Rect> placed = new ArrayList<>();
        // Pure schematic mode does not index its painted edges yet, so the shared
        // penalty path runs with an empty (edge-neutral) blocker index here.
        EdgeBlockerIndex edgeBlockers = new EdgeBlockerIndex();
        int rendered = 0;
        for (VisualLabel label : visualGraph.labels().stream().sorted(Comparator.comparingInt(VisualLabel::priority).reversed()).toList()) {
            if (rendered >= FullRouteMapConfig.MAX_LABELS_PER_FRAME) {
                break;
            }
            VisualNode visualNode = visualGraph.node(label.nodeId()).orElse(null);
            MapNode raw = graph.node(label.nodeId()).orElse(null);
            if (visualNode == null || raw == null || visualNode.kind() != NodeKind.STATION || label.text().isBlank()) {
                continue;
            }
            // Trunk tier between the 0.30 floor and the full 0.45 cutoff: transfer
            // stations only (cluster kinds never reach this STATION-only path).
            if (viewport.zoom() < 0.45D && !isTrunkTierLabel(raw)) {
                continue;
            }
            if (!visualView.contains(label.x(), label.z()) && !visualView.contains(visualNode.x(), visualNode.z())) {
                continue;
            }
            DisplayNameStack text = visualLabelText(label, raw, viewport.zoom());
            float scale = visualLabelScale(label, raw, viewport.zoom());
            float secondaryScale = Math.max(0.48F, scale * 0.78F);
            int width = Math.max(1, FullMapUi.nameStackWidth(font, text, scale, secondaryScale));
            int height = Math.max(7, FullMapUi.nameStackHeight(text, scale, secondaryScale, 0));
            Vec2 screen = worldToScreen(label.x(), label.z(), viewport, rect);
            Vec2 nodeScreen = worldToScreen(visualNode.x(), visualNode.z(), viewport, rect);
            // Same zoom-drift guard as the visual path: the solver's node-to-label
            // offset is a layout-space constant, so the screen-space distance to the
            // node is clamped (keeping the solver's chosen side) before bounds solving.
            screen = clampLabelAnchorToNode(screen, nodeScreen, visualNodeCollisionRadius(visualNode, graph, viewport.zoom()) + 20.0D);
            SPSGui.Rect bounds = new SPSGui.Rect((int) Math.round(screen.x()), (int) Math.round(screen.y()), width, height);
            SPSGui.Rect selected = null;
            if (rectsOverlap(bounds, rect) && !labelBlockedByPlaced(bounds, placed) && !labelBlockedByIcons(label.nodeId(), bounds, iconBlockers)) {
                selected = bounds;
            } else {
                LabelCandidate fallback = new LabelCandidate(raw, text, labelBounds(raw, nodeScreen, viewport.zoom(), rect, width, height), label.priority(), scale);
                selected = chooseDenseVisualLabelBounds(fallback, placed, iconBlockers, edgeBlockers).orElse(null);
            }
            if (selected == null) {
                continue;
            }
            placed.add(selected);
            // Label placement is solved at capture time; only the deterministic text
            // draw is re-issued on replay.
            SPSGui.Rect labelPosition = selected;
            DisplayNameStack labelText = text;
            float labelScaleValue = scale;
            float labelSecondaryScale = secondaryScale;
            SmoothGuiPrimitives.recordOrReplay(graphics, g -> FullMapUi.drawNameStack(g, font, labelText, labelPosition.x(), labelPosition.y(), labelPosition.width(), labelPrimaryColor, labelSecondaryColor, labelScaleValue, labelSecondaryScale, 0));
            rendered++;
        }
        return placed;
    }

    private void drawTransferHints(GuiGraphicsExtractor graphics, MapDimensionGraph graph, ViewportState viewport, SPSGui.Rect rect, Aabb2 worldView) {
        if (viewport.zoom() < 0.5D) {
            return;
        }
        for (MapTransferHint hint : graph.transferHints()) {
            MapNode from = graph.node(displayNodeId(graph, hint.from(), viewport.zoom())).orElse(null);
            MapNode to = graph.node(displayNodeId(graph, hint.to(), viewport.zoom())).orElse(null);
            if (from == null || to == null || from.id().equals(to.id()) || !worldView.intersects(Aabb2.around((from.worldX() + to.worldX()) * 0.5D, (from.worldZ() + to.worldZ()) * 0.5D, hint.distance() * 0.5D + 4.0D))) {
                continue;
            }
            Optional<SegmentScreen> segment = transferSegment(graph, hint, viewport, rect);
            if (segment.isEmpty()) {
                continue;
            }
            // Hover swaps the whole hint paint (halo, thicker dash, larger icon): both
            // variants are captured once and switched by the live hover at replay time.
            Vec2 a = segment.get().a();
            Vec2 b = segment.get().b();
            Vec2 mid = midpoint(a, b);
            int normalIconSize = Math.max(4, (int) Math.round(8 * iconScale(viewport.zoom())));
            int focusedIconSize = Math.max(4, (int) Math.round(10 * iconScale(viewport.zoom())));
            List<GuiElementRenderState> normal = SmoothGuiPrimitives.captureBranch(() -> {
                drawDashedLine(graphics, a, b, 1, FullRouteMapConfig.MAP_TRANSFER_HINT, 4.0D, 4.0D);
                drawPedestrianIcon(graphics, mid, normalIconSize, FullRouteMapConfig.MAP_TRANSFER_HINT);
            });
            List<GuiElementRenderState> focused = SmoothGuiPrimitives.captureBranch(() -> {
                drawDashedLine(graphics, a, b, 2.0D + 2.0D * iconScale(viewport.zoom()), FullRouteMapConfig.MAP_FOCUS_HALO, 4.0D, 4.0D);
                drawDashedLine(graphics, a, b, 2, FullRouteMapConfig.MAP_TRANSFER_HINT, 4.0D, 4.0D);
                drawPedestrianIcon(graphics, mid, focusedIconSize, FullRouteMapConfig.MAP_TRANSFER_HINT);
            });
            String hintId = hint.id();
            SmoothGuiPrimitives.recordVariantSwitch(graphics, normal, focused, () -> liveHover.target() instanceof HitTarget.TransferHintHit(var hoveredHintId) && hoveredHintId.equals(hintId));
        }
    }

    private void drawFoldPeerIndicator(GuiGraphicsExtractor graphics, MapDimensionGraph graph, ViewportState viewport, SPSGui.Rect rect, Aabb2 worldView) {
        // Pure hover overlay: recompute at replay time from the live hover (empty unless
        // a fold anchor node is hovered).
        SmoothGuiPrimitives.recordOrReplay(graphics, g -> {
            if (!(liveHover.target() instanceof HitTarget.NodeHit(var hoveredNodeId))) {
                return;
            }
            MapNode node = graph.node(hoveredNodeId).orElse(null);
            if (node == null || node.kind() != NodeKind.FOLD_ANCHOR || node.foldPeerId().isEmpty()) {
                return;
            }
            MapNode peer = foldPeerNode(graph, node).orElse(null);
            if (peer == null || !worldView.contains(peer.worldX(), peer.worldZ())) {
                return;
            }
            Vec2 a = screenForNode(graph, node, viewport, rect);
            Vec2 b = screenForNode(graph, peer, viewport, rect);
            int color = foldOutlineColor(graph, node);
            drawDashedLine(g, a, b, 1, SPSGui.withAlpha(color, 0xAA), 4.0D, 4.0D);
            drawArrow(g, a, b, SPSGui.withAlpha(color, 0xCC));
            drawArrow(g, b, a, SPSGui.withAlpha(color, 0xCC));
        });
    }

    private EdgeBlockerIndex drawEdges(GuiGraphicsExtractor graphics, MapDimensionGraph graph, ViewportState viewport, SPSGui.Rect rect, Aabb2 worldView) {
        // Every painted lane is also registered with the label edge-avoidance index as
        // it is drawn, so drawLabels can solve placements against exactly the strokes
        // on screen. The index is pure bookkeeping -- it emits no draw calls.
        EdgeBlockerIndex edgeBlockers = new EdgeBlockerIndex();
        Map<String, Double> edgeOffsets = this.visualEdgeOffsets(graph, viewport, rect, worldView);
        for (MapEdge edge : graph.edges()) {
            Optional<Segment> segment = displaySegment(graph, edge, viewport.zoom());
            if (segment.isEmpty()) {
                continue;
            }
            MapNode from = segment.get().from();
            MapNode to = segment.get().to();
            if (!worldView.intersects(Aabb2.empty().include(from.worldX(), from.worldZ()).include(to.worldX(), to.worldZ()).inflate(16.0D))) {
                continue;
            }
            Vec2 a = screenForNode(graph, from, viewport, rect);
            Vec2 b = screenForNode(graph, to, viewport, rect);
            String edgeId = edge.id();
            BooleanSupplier hovered = () -> liveHover.target() instanceof HitTarget.EdgeHit(var hoveredEdgeId) && hoveredEdgeId.equals(edgeId);
            double baseOffset = edgeOffsets.getOrDefault(edge.id(), 0.0D);
            List<RouteLine> lines = routeLinesForEdge(edge);
            if (lines.size() >= FullRouteMapConfig.TRUNK_THRESHOLD) {
                SegmentScreen trunkSegment = offset(a, b, baseOffset);
                double trunkWidth = Math.max(1.6D, 5.0D * lineWidthScale(viewport.zoom()));
                double trunkHaloWidth = trunkWidth + 4.0D * iconScale(viewport.zoom());
                SmoothGuiPrimitives.recordOrReplay(graphics, g -> {
                    if (hovered.getAsBoolean()) {
                        drawLine(g, trunkSegment.a(), trunkSegment.b(), trunkHaloWidth, FullRouteMapConfig.MAP_FOCUS_HALO);
                    }
                });
                drawLine(graphics, trunkSegment.a(), trunkSegment.b(), trunkWidth, FullRouteMapConfig.MAP_TRUNK);
                addEdgeBlockerPath(edgeBlockers, List.of(trunkSegment.a(), trunkSegment.b()), 2.5D);
                this.drawTrunkDots(graphics, trunkSegment.a(), trunkSegment.b(), lines, viewport.zoom());
            } else {
                this.drawEdgeRouteBundles(graphics, edge, a, b, lines, baseOffset, hovered, Math.max(1.2D, FullRouteMapConfig.LINE_WIDTH_PX * lineWidthScale(viewport.zoom())), viewport.zoom(), edgeBlockers);
            }
        }
        return edgeBlockers;
    }

    private void drawNodes(GuiGraphicsExtractor graphics, Font font, MapDimensionGraph graph, ViewportState viewport, SPSGui.Rect rect, Aabb2 worldView) {
        for (MapNode node : graph.nodes()) {
            if (!isVisibleNode(node, viewport.zoom()) || !worldView.contains(node.worldX(), node.worldZ())) {
                continue;
            }
            Vec2 screen = screenForNode(graph, node, viewport, rect);
            double elementZoom = perspectiveElementZoom(viewport, rect, screen);
            NodeId nodeId = node.id();
            NodeId displayId = displayNodeId(graph, node.id(), viewport.zoom());
            int radius = nodeRadius(node, elementZoom);
            // Focus decoration slot: re-evaluated against the live hover at replay time
            // (direct hover, transfer-hint endpoint or fold peer highlight).
            SmoothGuiPrimitives.recordOrReplay(graphics, g -> {
                boolean hovered = liveHover.target() instanceof HitTarget.NodeHit(var hoveredNodeId) && hoveredNodeId.equals(nodeId)
                        || liveHover.transferHighlightNodes().contains(displayId)
                        || liveHover.foldPeerHighlightId().filter(nodeId::equals).isPresent();
                if (hovered) {
                    drawNodeFocus(g, node, screen, radius, elementZoom);
                }
            });
            switch (node.kind()) {
                case FOLD_ANCHOR -> drawDiamond(graphics, screen, radius, FullRouteMapConfig.MAP_FOLD_FILL, foldOutlineColor(graph, node), 1);
                case CLUSTER -> {
                    drawCircle(graphics, screen, radius, FullRouteMapConfig.MAP_CLUSTER_FILL, FullRouteMapConfig.MAP_CLUSTER_OUTLINE);
                    drawClusterStripes(graphics, screen, radius, node.routeLineIds());
                    String count = Integer.toString(node.stationGroupIds().size());
                    if (radius >= 7) {
                        int textX = (int) screen.x() + radius - 2;
                        int textY = (int) screen.y() + radius - 4;
                        SmoothGuiPrimitives.recordOrReplay(graphics, g -> SPSGui.smallText(g, font, count, textX, textY, FullRouteMapConfig.MAP_CLUSTER_OUTLINE, 0.68F));
                    }
                }
                case DEEP_CLUSTER -> {
                    drawDeepCluster(graphics, screen, radius, FullRouteMapConfig.MAP_CLUSTER_FILL, FullRouteMapConfig.MAP_CLUSTER_OUTLINE);
                    drawClusterStripes(graphics, screen, radius, node.routeLineIds());
                    String count = Integer.toString(node.stationGroupIds().size());
                    if (radius >= 7) {
                        int textX = (int) screen.x() + radius - 2;
                        int textY = (int) screen.y() + radius - 4;
                        SmoothGuiPrimitives.recordOrReplay(graphics, g -> SPSGui.smallText(g, font, count, textX, textY, FullRouteMapConfig.MAP_CLUSTER_OUTLINE, 0.68F));
                    }
                }
                case STATION -> {
                    if (node.isTransferStation()) {
                        drawCapsule(graphics, screen, radius * 3, radius * 2, FullRouteMapConfig.MAP_NODE_FILL, FullRouteMapConfig.MAP_NODE_OUTLINE);
                    } else {
                        drawCircle(graphics, screen, radius, FullRouteMapConfig.MAP_NODE_FILL, FullRouteMapConfig.MAP_NODE_OUTLINE);
                    }
                }
            }
            if (hasStationInternalEdge(graph, node.id())) {
                drawStationInternalMarker(graphics, screen, radius);
            }
        }
    }

    private void drawLabels(GuiGraphicsExtractor graphics, Font font, MapDimensionGraph graph, ViewportState viewport, SPSGui.Rect rect, Aabb2 worldView, EdgeBlockerIndex edgeBlockers) {
        // Low-zoom trunk tier: the floor drops from 0.45 to 0.30; between the two only
        // trunk-tier nodes are labelled (see shouldConsiderLabel) and their alpha fades
        // in linearly instead of popping at the old hard cutoff.
        if (viewport.zoom() < 0.30D) {
            return;
        }
        double labelFade = trunkLabelFade(viewport.zoom());
        int labelPrimaryColor = fadedLabelColor(FullRouteMapConfig.MAP_LABEL, labelFade);
        int labelSecondaryColor = fadedLabelColor(FullRouteMapConfig.MAP_LABEL_MUTED, labelFade);
        List<IconBlocker> iconBlockers = new ArrayList<>();
        List<LabelCandidate> candidates = new ArrayList<>();
        for (MapNode node : graph.nodes()) {
            if (!isVisibleNode(node, viewport.zoom()) || !worldView.contains(node.worldX(), node.worldZ())) {
                continue;
            }
            Vec2 screen = screenForNode(graph, node, viewport, rect);
            double elementZoom = perspectiveElementZoom(viewport, rect, screen);
            iconBlockers.add(new IconBlocker(node.id(), iconBounds(node, screen, elementZoom)));
            if (!perspectiveAllowsLabel(viewport, rect, screen, node) || !shouldConsiderLabel(node, elementZoom)) {
                continue;
            }
            DisplayNameStack label = labelForNode(node, elementZoom);
            float labelScale = labelScale(node, elementZoom);
            float secondaryScale = Math.max(0.48F, labelScale * 0.78F);
            int width = Math.max(1, FullMapUi.nameStackWidth(font, label, labelScale, secondaryScale));
            int height = Math.max(7, FullMapUi.nameStackHeight(label, labelScale, secondaryScale, 0));
            int priority = switch (node.kind()) {
                case CLUSTER -> 1000;
                case DEEP_CLUSTER -> 950;
                case FOLD_ANCHOR -> 800;
                case STATION -> node.isTransferStation() ? 700 : 200;
            } + node.routeLineIds().size() * 10;
            candidates.add(new LabelCandidate(node, label, labelBounds(node, screen, elementZoom, rect, width, height), priority, labelScale));
        }
        candidates.sort(Comparator.comparingInt(LabelCandidate::priority).reversed());
        List<SPSGui.Rect> placed = new ArrayList<>();
        int rendered = 0;
        for (LabelCandidate candidate : candidates) {
            if (rendered >= FullRouteMapConfig.MAX_LABELS_PER_FRAME) {
                break;
            }
            Optional<SPSGui.Rect> placement = chooseLabelBounds(candidate, placed, iconBlockers, edgeBlockers);
            if (placement.isEmpty()) {
                continue;
            }
            SPSGui.Rect bounds = placement.get();
            placed.add(bounds);
            float secondaryScale = Math.max(0.48F, candidate.scale() * 0.78F);
            // Label placement is solved at capture time; only the deterministic text
            // draw is re-issued on replay.
            DisplayNameStack labelText = candidate.label();
            float labelScaleValue = candidate.scale();
            SmoothGuiPrimitives.recordOrReplay(graphics, g -> FullMapUi.drawNameStack(g, font, labelText, bounds.x(), bounds.y(), bounds.width(), labelPrimaryColor, labelSecondaryColor, labelScaleValue, secondaryScale, 0));
            rendered++;
        }
    }

    private static boolean shouldConsiderLabel(MapNode node, double zoom) {
        if (zoom < 0.45D) {
            // Trunk tier between the low-zoom floor (0.30, enforced at the call sites)
            // and the full label cutoff: cluster aggregates and transfer/interchange
            // stations only.
            return isTrunkTierLabel(node);
        }
        if (zoom < 0.65D) {
            return node.kind() == NodeKind.CLUSTER || node.kind() == NodeKind.DEEP_CLUSTER;
        }
        if (zoom < 1.0D) {
            return node.kind() == NodeKind.CLUSTER || node.kind() == NodeKind.DEEP_CLUSTER || node.isTransferStation();
        }
        if (zoom < 1.35D) {
            return node.kind() != NodeKind.STATION || node.isTransferStation();
        }
        return true;
    }

    // Trunk-tier predicate shared by the low-zoom band of every main-map label path:
    // cluster aggregates plus transfer/interchange stations (routeLineIds >= 2).
    private static boolean isTrunkTierLabel(MapNode node) {
        return node.kind() == NodeKind.CLUSTER || node.kind() == NodeKind.DEEP_CLUSTER || node.isTransferStation();
    }

    // Main-map trunk-tier fade band: full opacity at and above the original 0.45
    // cutoff, ramping linearly to 0 at the 0.30 floor so low-zoom labels ease in
    // instead of popping.
    private static double trunkLabelFade(double zoom) {
        if (zoom >= 0.45D) {
            return 1.0D;
        }
        return Math.max(0.0D, (zoom - 0.30D) / 0.15D);
    }

    // Physical-mode trunk-tier fade band: full opacity at and above the original 0.34
    // element-zoom cutoff, ramping linearly to 0 at the 0.24 floor.
    private static double physicalTrunkLabelFade(double elementZoom) {
        if (elementZoom >= 0.34D) {
            return 1.0D;
        }
        return Math.max(0.0D, (elementZoom - 0.24D) / 0.10D);
    }

    // Scales the alpha channel of an ARGB color by fade (0..1), preserving the color's
    // own base alpha; fade >= 1 returns the color unchanged.
    private static int fadedLabelColor(int color, double fade) {
        if (fade >= 1.0D) {
            return color;
        }
        return SPSGui.withAlpha(color, (int) Math.round((color >>> 24) * fade));
    }

    // Pulls a solver-placed label anchor back towards its node when the screen-space
    // distance exceeds cap, preserving the direction (side) the solver picked.
    private static Vec2 clampLabelAnchorToNode(Vec2 labelScreen, Vec2 nodeScreen, double cap) {
        double dx = labelScreen.x() - nodeScreen.x();
        double dy = labelScreen.y() - nodeScreen.y();
        double distance = Math.hypot(dx, dy);
        if (distance <= cap) {
            return labelScreen;
        }
        double factor = cap / distance;
        return new Vec2(nodeScreen.x() + dx * factor, nodeScreen.y() + dy * factor);
    }

    private static float labelScale(MapNode node, double zoom) {
        double base = switch (node.kind()) {
            case CLUSTER -> 0.82D;
            case DEEP_CLUSTER -> 0.78D;
            case FOLD_ANCHOR -> 0.74D;
            case STATION -> node.isTransferStation() ? 0.76D : 0.68D;
        };
        double zoomAdjustment;
        if (zoom < 0.75D) {
            zoomAdjustment = -0.10D;
        } else if (zoom < 1.2D) {
            zoomAdjustment = -0.04D;
        } else if (zoom < 2.0D) {
            zoomAdjustment = 0.04D;
        } else if (zoom < 3.0D) {
            zoomAdjustment = 0.12D;
        } else {
            zoomAdjustment = 0.20D;
        }
        return (float) Math.max(0.58D, Math.min(1.0D, base + zoomAdjustment));
    }

    private static SPSGui.Rect iconBounds(MapNode node, Vec2 screen, double zoom) {
        int radius = (int) Math.ceil(nodeCollisionRadius(node, zoom) + 3.0D);
        return new SPSGui.Rect((int) Math.floor(screen.x() - radius), (int) Math.floor(screen.y() - radius), radius * 2, radius * 2);
    }

    private static List<SPSGui.Rect> labelBounds(MapNode node, Vec2 screen, double zoom, SPSGui.Rect mapRect, int width, int height) {
        double radius = nodeCollisionRadius(node, zoom);
        double gap = 5.0D + Math.max(0.0D, iconScale(zoom) * 2.0D);
        double near = radius + gap;
        double far = near + Math.max(7.0D, height * 0.8D);
        double diagonal = near * 0.72D;
        List<SPSGui.Rect> candidates = new ArrayList<>();
        addLabelBounds(candidates, screen.x() - width * 0.5D, screen.y() + near, width, height, mapRect);
        addLabelBounds(candidates, screen.x() - width * 0.5D, screen.y() - near - height, width, height, mapRect);
        addLabelBounds(candidates, screen.x() + near, screen.y() - height * 0.5D, width, height, mapRect);
        addLabelBounds(candidates, screen.x() - near - width, screen.y() - height * 0.5D, width, height, mapRect);
        addLabelBounds(candidates, screen.x() + diagonal, screen.y() + diagonal, width, height, mapRect);
        addLabelBounds(candidates, screen.x() - diagonal - width, screen.y() + diagonal, width, height, mapRect);
        addLabelBounds(candidates, screen.x() + diagonal, screen.y() - diagonal - height, width, height, mapRect);
        addLabelBounds(candidates, screen.x() - diagonal - width, screen.y() - diagonal - height, width, height, mapRect);
        addLabelBounds(candidates, screen.x() - width * 0.5D, screen.y() + far, width, height, mapRect);
        addLabelBounds(candidates, screen.x() - width * 0.5D, screen.y() - far - height, width, height, mapRect);
        if (node.kind() == NodeKind.CLUSTER || node.kind() == NodeKind.DEEP_CLUSTER || node.kind() == NodeKind.FOLD_ANCHOR || node.isTransferStation()) {
            addLabelBounds(candidates, screen.x() + far, screen.y() - height * 0.5D, width, height, mapRect);
            addLabelBounds(candidates, screen.x() - far - width, screen.y() - height * 0.5D, width, height, mapRect);
        }
        return candidates;
    }

    private static void addLabelBounds(List<SPSGui.Rect> candidates, double x, double y, int width, int height, SPSGui.Rect mapRect) {
        candidates.add(clampToMap(new SPSGui.Rect((int) Math.round(x), (int) Math.round(y), width, height), mapRect));
    }

    private static SPSGui.Rect clampToMap(SPSGui.Rect bounds, SPSGui.Rect mapRect) {
        int padding = 3;
        int maxX = Math.max(mapRect.x() + padding, mapRect.right() - padding - bounds.width());
        int maxY = Math.max(mapRect.y() + padding, mapRect.bottom() - padding - bounds.height());
        int x = Math.max(mapRect.x() + padding, Math.min(bounds.x(), maxX));
        int y = Math.max(mapRect.y() + padding, Math.min(bounds.y(), maxY));
        return new SPSGui.Rect(x, y, bounds.width(), bounds.height());
    }

    private static Optional<SPSGui.Rect> chooseLabelBounds(LabelCandidate candidate, List<SPSGui.Rect> placed, List<IconBlocker> iconBlockers, EdgeBlockerIndex edgeBlockers) {
        SPSGui.Rect best = null;
        int bestPenalty = Integer.MAX_VALUE;
        for (SPSGui.Rect bounds : candidate.bounds()) {
            int penalty = labelPenalty(candidate, bounds, placed, iconBlockers, edgeBlockers);
            if (penalty == 0) {
                return Optional.of(bounds);
            }
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                best = bounds;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        if (candidate.priority() >= 700 || bestPenalty <= 48) {
            return Optional.of(best);
        }
        return Optional.empty();
    }

    private static Optional<SPSGui.Rect> chooseDenseVisualLabelBounds(LabelCandidate candidate, List<SPSGui.Rect> placed, List<IconBlocker> iconBlockers, EdgeBlockerIndex edgeBlockers) {
        SPSGui.Rect best = null;
        int bestPenalty = Integer.MAX_VALUE;
        for (SPSGui.Rect bounds : candidate.bounds()) {
            int penalty = labelPenalty(candidate, bounds, placed, iconBlockers, edgeBlockers);
            if (penalty == 0) {
                return Optional.of(bounds);
            }
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                best = bounds;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        if (shouldForceDenseLabel(candidate.node(), candidate.priority()) || bestPenalty <= 260) {
            return Optional.of(best);
        }
        return Optional.empty();
    }

    private static boolean shouldForceDenseLabel(MapNode node, int priority) {
        return node.kind() == NodeKind.STATION || node.kind() == NodeKind.FOLD_ANCHOR || priority >= 700;
    }

    private static boolean labelBlockedByPlaced(SPSGui.Rect bounds, List<SPSGui.Rect> placed) {
        for (SPSGui.Rect other : placed) {
            if (rectsOverlap(bounds, other)) {
                return true;
            }
        }
        return false;
    }

    private static boolean labelBlockedByIcons(NodeId ownNodeId, SPSGui.Rect bounds, List<IconBlocker> iconBlockers) {
        for (IconBlocker blocker : iconBlockers) {
            if (!blocker.nodeId().equals(ownNodeId) && rectsOverlap(bounds, blocker.bounds())) {
                return true;
            }
        }
        return false;
    }

    private static boolean labelBlockedByEdges(SPSGui.Rect bounds, EdgeBlockerIndex edgeBlockers) {
        return edgeBlockers.overlapArea(bounds) > 0;
    }

    private static int labelPenalty(LabelCandidate candidate, SPSGui.Rect bounds, List<SPSGui.Rect> placed, List<IconBlocker> iconBlockers, EdgeBlockerIndex edgeBlockers) {
        int penalty = 0;
        for (SPSGui.Rect placedBounds : placed) {
            int overlap = intersectionArea(bounds, placedBounds);
            if (overlap > 0) {
                penalty += 900 + overlap * 2;
            }
        }
        for (IconBlocker blocker : iconBlockers) {
            int overlap = intersectionArea(bounds, blocker.bounds());
            if (overlap > 0) {
                penalty += (blocker.nodeId().equals(candidate.node().id()) ? 1200 : 700) + overlap;
            }
        }
        // Third blocker class: painted edge lanes. A station name solved onto a
        // colored line is hard to read, yet a lane is a thin stroke rather than a
        // solid icon, so the flat term sits deliberately below the icon blocker
        // (700): any clean candidate position still wins outright, and among
        // positions that all touch something the overlap area steers the choice
        // toward the least-covered one. The flat 400 also stays above the dense
        // fallback acceptance threshold (260), so dense zooms keep preferring
        // off-line positions for labels that are not force-drawn.
        int edgeOverlap = edgeBlockers.overlapArea(bounds);
        if (edgeOverlap > 0) {
            penalty += 400 + edgeOverlap;
        }
        return penalty;
    }

    /**
     * Coarse screen-space index of the edge lanes painted this frame, consulted as
     * the third label blocker class (besides already placed labels and node icons).
     * Each painted lane path is chopped into chunks of at most {@link #BUCKET_PX}
     * pixels and every chunk's bounding box -- deliberately coarse, this is an
     * avoidance hint, not hit geometry -- is inserted into every 32px bucket it
     * overlaps, so a label query only tests the handful of chunks near the candidate
     * rect instead of every lane on the map. Built and consumed entirely while a
     * static frame is being captured, so replaying a cached program never runs it.
     */
    private static final class EdgeBlockerIndex {
        private static final int BUCKET_PX = 32;
        private final Map<Long, List<EdgeBlocker>> buckets = new HashMap<>();
        private int queryStamp;

        private void add(SPSGui.Rect bounds) {
            EdgeBlocker blocker = new EdgeBlocker(bounds);
            int minBucketX = Math.floorDiv(bounds.x(), BUCKET_PX);
            int minBucketY = Math.floorDiv(bounds.y(), BUCKET_PX);
            int maxBucketX = Math.floorDiv(bounds.right() - 1, BUCKET_PX);
            int maxBucketY = Math.floorDiv(bounds.bottom() - 1, BUCKET_PX);
            for (int bucketX = minBucketX; bucketX <= maxBucketX; bucketX++) {
                for (int bucketY = minBucketY; bucketY <= maxBucketY; bucketY++) {
                    this.buckets.computeIfAbsent(bucketKey(bucketX, bucketY), ignored -> new ArrayList<>()).add(blocker);
                }
            }
        }

        /**
         * Total overlap area between {@code bounds} and the indexed lane chunk boxes,
         * each chunk counted once even when it spans several buckets.
         */
        private int overlapArea(SPSGui.Rect bounds) {
            if (this.buckets.isEmpty()) {
                return 0;
            }
            int stamp = ++this.queryStamp;
            int area = 0;
            int minBucketX = Math.floorDiv(bounds.x(), BUCKET_PX);
            int minBucketY = Math.floorDiv(bounds.y(), BUCKET_PX);
            int maxBucketX = Math.floorDiv(bounds.right() - 1, BUCKET_PX);
            int maxBucketY = Math.floorDiv(bounds.bottom() - 1, BUCKET_PX);
            for (int bucketX = minBucketX; bucketX <= maxBucketX; bucketX++) {
                for (int bucketY = minBucketY; bucketY <= maxBucketY; bucketY++) {
                    List<EdgeBlocker> bucket = this.buckets.get(bucketKey(bucketX, bucketY));
                    if (bucket == null) {
                        continue;
                    }
                    for (EdgeBlocker blocker : bucket) {
                        if (blocker.lastQueryStamp != stamp) {
                            blocker.lastQueryStamp = stamp;
                            area += intersectionArea(bounds, blocker.bounds);
                        }
                    }
                }
            }
            return area;
        }

        private static long bucketKey(int bucketX, int bucketY) {
            return ((long) bucketX << 32) | (bucketY & 0xFFFFFFFFL);
        }

        private static final class EdgeBlocker {
            private final SPSGui.Rect bounds;
            private int lastQueryStamp;

            private EdgeBlocker(SPSGui.Rect bounds) {
                this.bounds = bounds;
            }
        }
    }

    /**
     * Registers one painted lane path with the label edge-avoidance index: every
     * polyline segment is chopped into chunks of at most {@link EdgeBlockerIndex#BUCKET_PX}
     * pixels and each chunk contributes its bounding box inflated by the lane's half
     * width plus one pixel. Chunking keeps the boxes tight around the stroke -- a
     * single box for a whole long or diagonal edge would span large areas of empty
     * background and suppress label positions that never touch the painted line.
     */
    private static void addEdgeBlockerPath(EdgeBlockerIndex edgeBlockers, List<Vec2> path, double halfWidth) {
        double margin = halfWidth + 1.0D;
        for (int i = 0; i + 1 < path.size(); i++) {
            Vec2 a = path.get(i);
            Vec2 b = path.get(i + 1);
            double length = a.distanceTo(b);
            if (length < 1.0D) {
                continue;
            }
            int pieces = Math.max(1, (int) Math.ceil(length / EdgeBlockerIndex.BUCKET_PX));
            for (int piece = 0; piece < pieces; piece++) {
                Vec2 from = pointBetween(a, b, length * piece / pieces);
                Vec2 to = pointBetween(a, b, length * (piece + 1) / pieces);
                int x0 = (int) Math.floor(Math.min(from.x(), to.x()) - margin);
                int y0 = (int) Math.floor(Math.min(from.y(), to.y()) - margin);
                int x1 = (int) Math.ceil(Math.max(from.x(), to.x()) + margin);
                int y1 = (int) Math.ceil(Math.max(from.y(), to.y()) + margin);
                edgeBlockers.add(new SPSGui.Rect(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0)));
            }
        }
    }

    private static DisplayNameStack labelForNode(MapNode node, double zoom) {
        DisplayNameStack label = FullMapText.displayNameStack(node);
        if (zoom < 1.1D && node.kind() == NodeKind.STATION) {
            label = label.withoutSecondary();
        }
        if (zoom >= 3.5D && node.kind() == NodeKind.STATION && !node.platformStopIds().isEmpty()) {
            return new DisplayNameStack(label.primary() + " (" + node.platformStopIds().size() + ")", label.secondary(), label.aliases());
        }
        return label;
    }

    private static DisplayNameStack visualLabelText(VisualLabel label, MapNode node, double zoom) {
        if (node.kind() != NodeKind.STATION) {
            return DisplayNameStack.of(label.text());
        }
        return labelForNode(node, zoom);
    }

    private Optional<Segment> displaySegment(MapDimensionGraph graph, MapEdge edge, double zoom) {
        NodeId fromId = displayNodeId(graph, edge.from(), zoom);
        NodeId toId = displayNodeId(graph, edge.to(), zoom);
        if (fromId.equals(toId)) {
            return Optional.empty();
        }
        Optional<MapNode> from = graph.node(fromId);
        Optional<MapNode> to = graph.node(toId);
        if (from.isEmpty() || to.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Segment(from.get(), to.get()));
    }

    private Map<String, Double> visualEdgeOffsets(MapDimensionGraph graph, ViewportState viewport, SPSGui.Rect rect, Aabb2 worldView) {
        List<VisibleEdge> visible = new ArrayList<>();
        for (MapEdge edge : graph.edges()) {
            Optional<Segment> segment = displaySegment(graph, edge, viewport.zoom());
            if (segment.isEmpty()) {
                continue;
            }
            MapNode from = segment.get().from();
            MapNode to = segment.get().to();
            if (!worldView.intersects(Aabb2.empty().include(from.worldX(), from.worldZ()).include(to.worldX(), to.worldZ()).inflate(16.0D))) {
                continue;
            }
            Vec2 a = screenForNode(graph, from, viewport, rect);
            Vec2 b = screenForNode(graph, to, viewport, rect);
            if (a.distanceTo(b) >= 8.0D) {
                visible.add(new VisibleEdge(edge, a, b));
            }
        }
        List<List<VisibleEdge>> groups = new ArrayList<>();
        for (VisibleEdge edge : visible) {
            List<VisibleEdge> target = null;
            for (List<VisibleEdge> group : groups) {
                if (group.stream().anyMatch(existing -> nearParallelOverlap(existing, edge))) {
                    target = group;
                    break;
                }
            }
            if (target == null) {
                target = new ArrayList<>();
                groups.add(target);
            }
            target.add(edge);
        }
        Map<String, Double> offsets = new HashMap<>();
        double step = FullRouteMapConfig.LINE_WIDTH_PX + 3.0D;
        for (List<VisibleEdge> group : groups) {
            if (group.size() <= 1) {
                continue;
            }
            group.sort(Comparator.comparing(edge -> edge.edge().id()));
            double center = (group.size() - 1) * 0.5D;
            for (int i = 0; i < group.size(); i++) {
                offsets.put(group.get(i).edge().id(), (i - center) * step);
            }
        }
        return offsets;
    }

    private static boolean nearParallelOverlap(VisibleEdge first, VisibleEdge second) {
        if (first.edge().id().equals(second.edge().id())) {
            return false;
        }
        double ax = first.b().x() - first.a().x();
        double ay = first.b().y() - first.a().y();
        double bx = second.b().x() - second.a().x();
        double by = second.b().y() - second.a().y();
        double al = Math.hypot(ax, ay);
        double bl = Math.hypot(bx, by);
        if (al < 8.0D || bl < 8.0D) {
            return false;
        }
        double aux = ax / al;
        double auy = ay / al;
        double bux = bx / bl;
        double buy = by / bl;
        double parallel = Math.abs(aux * bux + auy * buy);
        if (parallel < 0.985D) {
            return false;
        }
        double distance = (distancePointToInfiniteLine(second.a(), first.a(), aux, auy)
                + distancePointToInfiniteLine(second.b(), first.a(), aux, auy)
                + distancePointToInfiniteLine(first.a(), second.a(), bux, buy)
                + distancePointToInfiniteLine(first.b(), second.a(), bux, buy)) * 0.25D;
        if (distance > FullRouteMapConfig.LINE_WIDTH_PX + 4.0D) {
            return false;
        }
        double secondA = projection(second.a(), first.a(), aux, auy);
        double secondB = projection(second.b(), first.a(), aux, auy);
        double min = Math.max(0.0D, Math.min(secondA, secondB));
        double max = Math.min(al, Math.max(secondA, secondB));
        double overlap = max - min;
        return overlap >= Math.min(18.0D, Math.min(al, bl) * 0.35D);
    }

    private static double distancePointToInfiniteLine(Vec2 point, Vec2 origin, double ux, double uy) {
        return Math.abs((point.x() - origin.x()) * uy - (point.y() - origin.y()) * ux);
    }

    private static double projection(Vec2 point, Vec2 origin, double ux, double uy) {
        return (point.x() - origin.x()) * ux + (point.y() - origin.y()) * uy;
    }

    public static NodeId displayNodeId(MapDimensionGraph graph, NodeId nodeId, double zoom) {
        MapNode node = graph.nodesById().get(nodeId);
        if (node == null) {
            return nodeId;
        }
        if (zoom < FullRouteMapConfig.CLUSTER_AUTO_EXPAND_ZOOM) {
            return topDisplayNodeId(graph, node);
        }
        if ((node.kind() == NodeKind.STATION || node.kind() == NodeKind.FOLD_ANCHOR) && node.clusterId().flatMap(graph::node).map(parent -> parent.kind() == NodeKind.DEEP_CLUSTER).orElse(false)) {
            return node.clusterId().orElse(nodeId);
        }
        return nodeId;
    }

    static NodeId topDisplayNodeId(MapDimensionGraph graph, MapNode node) {
        NodeId current = node.id();
        Optional<NodeId> parent = node.clusterId();
        while (parent.isPresent()) {
            current = parent.get();
            MapNode parentNode = graph.nodesById().get(current);
            if (parentNode == null) {
                break;
            }
            parent = parentNode.clusterId();
        }
        return current;
    }

    public static NodeId aggregatedDisplayNodeId(MapDimensionGraph graph, NodeId nodeId) {
        MapNode node = graph.nodesById().get(nodeId);
        return node == null ? nodeId : topDisplayNodeId(graph, node);
    }

    private static boolean isVisibleNode(MapNode node, double zoom) {
        if (node.kind() == NodeKind.CLUSTER) {
            return zoom < FullRouteMapConfig.CLUSTER_AUTO_EXPAND_ZOOM;
        }
        if (node.kind() == NodeKind.DEEP_CLUSTER) {
            return node.clusterId().isEmpty() || zoom >= FullRouteMapConfig.CLUSTER_AUTO_EXPAND_ZOOM;
        }
        if (node.kind() == NodeKind.FOLD_ANCHOR && node.clusterId().isPresent()) {
            return node.clusterId().map(parent -> parent.kind() == NodeKind.CLUSTER && zoom >= FullRouteMapConfig.CLUSTER_AUTO_EXPAND_ZOOM).orElse(false);
        }
        if (node.kind() == NodeKind.STATION && node.clusterId().isPresent()) {
            return zoom >= FullRouteMapConfig.CLUSTER_AUTO_EXPAND_ZOOM && node.clusterId().map(parent -> parent.kind() != NodeKind.DEEP_CLUSTER).orElse(true);
        }
        return true;
    }

    private static int nodeRadius(MapNode node, double zoom) {
        int base = switch (node.kind()) {
            case CLUSTER, DEEP_CLUSTER -> FullRouteMapConfig.CLUSTER_RADIUS_PX;
            case FOLD_ANCHOR, STATION -> FullRouteMapConfig.NODE_RADIUS_PX;
        };
        int min = node.kind() == NodeKind.CLUSTER || node.kind() == NodeKind.DEEP_CLUSTER ? 4 : 2;
        return Math.max(min, (int) Math.round(base * iconScale(zoom)));
    }

    private static int visualNodeRadius(VisualNode node, MapDimensionGraph graph, double zoom) {
        MapNode raw = graph.node(node.id()).orElse(null);
        if (raw != null) {
            return nodeRadius(raw, zoom);
        }
        int base = node.kind() == NodeKind.CLUSTER || node.kind() == NodeKind.DEEP_CLUSTER
                ? FullRouteMapConfig.CLUSTER_RADIUS_PX
                : FullRouteMapConfig.NODE_RADIUS_PX;
        if (FullRouteMapCache.layoutMode() == FullRouteMapLayoutMode.SCHEMATIC && node.kind() == NodeKind.FOLD_ANCHOR) {
            return Math.max(2, (int) Math.round(base * 0.70D * iconScale(zoom)));
        }
        return Math.max(2, (int) Math.round(base * iconScale(zoom)));
    }

    private static double nodeCollisionRadius(MapNode node, double zoom) {
        int radius = nodeRadius(node, zoom);
        return switch (node.kind()) {
            case STATION -> node.isTransferStation() ? radius * 1.65D : radius;
            case CLUSTER, DEEP_CLUSTER -> radius + 1.0D;
            case FOLD_ANCHOR -> radius;
        };
    }

    private static double visualNodeCollisionRadius(VisualNode node, MapDimensionGraph graph, double zoom) {
        MapNode raw = graph.node(node.id()).orElse(null);
        if (raw != null) {
            return nodeCollisionRadius(raw, zoom);
        }
        return visualNodeRadius(node, graph, zoom);
    }

    private static SPSGui.Rect visualIconBounds(VisualNode node, MapDimensionGraph graph, Vec2 screen, double zoom) {
        int radius = (int) Math.ceil(visualNodeCollisionRadius(node, graph, zoom) + 3.0D);
        return new SPSGui.Rect((int) Math.floor(screen.x() - radius), (int) Math.floor(screen.y() - radius), radius * 2, radius * 2);
    }

    private static boolean nodeHit(MapNode node, Vec2 center, double zoom, double mouseX, double mouseY) {
        double padding = nodeHitPadding(node, zoom);
        if (node.kind() == NodeKind.FOLD_ANCHOR) {
            return Math.abs(mouseX - center.x()) + Math.abs(mouseY - center.y()) <= nodeRadius(node, zoom) + padding;
        }
        return Math.hypot(mouseX - center.x(), mouseY - center.y()) <= nodeCollisionRadius(node, zoom) + padding;
    }

    private static double nodeHitPadding(MapNode node, double zoom) {
        double scaled = iconScale(zoom);
        return switch (node.kind()) {
            case FOLD_ANCHOR -> Math.max(1.0D, 1.5D * scaled);
            case STATION -> Math.max(2.0D, 3.0D * scaled);
            case CLUSTER, DEEP_CLUSTER -> Math.max(2.0D, 4.0D * scaled);
        };
    }

    private static double edgeHitRadius(double zoom) {
        return Math.max(3.0D, (FullRouteMapConfig.LINE_WIDTH_PX + 3.0D) * iconScale(zoom));
    }

    private static double transferHintHitRadius(double zoom) {
        return Math.max(3.0D, 5.0D * iconScale(zoom));
    }

    private static double visualEdgeHitRadius(MapEdge edge, double zoom) {
        List<RouteLine> lines = routeLinesForEdge(edge);
        double width = lines.size() >= FullRouteMapConfig.TRUNK_THRESHOLD ? 5.0D : routeBundleWidth(edgeLanes(lines), FullRouteMapConfig.LINE_WIDTH_PX);
        return Math.max(edgeHitRadius(zoom), width * 0.5D + 3.0D);
    }

    private static double visualEdgePathHitRadius(VisualEdgePath path, double zoom) {
        int lineCount = Math.max(1, path.routeLineIds().size());
        double width = lineCount >= FullRouteMapConfig.TRUNK_THRESHOLD
                ? 5.0D
                : lineCount * FullRouteMapConfig.LINE_WIDTH_PX + Math.max(0, lineCount - 1);
        return Math.max(edgeHitRadius(zoom), width * 0.5D + 3.0D);
    }

    private static double distanceToVisualEdge(MapEdge edge, Vec2 point, Vec2 a, Vec2 b, double baseOffset) {
        SegmentScreen segment = offset(a, b, baseOffset);
        return distanceToSegment(point, segment.a(), segment.b());
    }

    private static double iconScale(double zoom) {
        return Math.max(0.35D, Math.min(1.0D, zoom));
    }

    // Lane/trunk widths follow the same sub-linear zoom curve as node icons so lines
    // keep thinning when zooming out instead of ending up thicker than station glyphs.
    private static double lineWidthScale(double zoom) {
        return iconScale(zoom);
    }

    private static double physicalNodeRadius(PhysicalNodeKind kind, double zoom) {
        double scale = physicalNodeScale(zoom);
        double base = kind == PhysicalNodeKind.FOLD_ANCHOR ? FullRouteMapConfig.NODE_RADIUS_PX + 1.0D : FullRouteMapConfig.NODE_RADIUS_PX - 1.0D;
        return Math.max(3.0D, base * scale);
    }

    // The physical mode's own sub-linear zoom curve, shared by focus-halo paddings so
    // they track physical node sizing instead of staying at a fixed screen size.
    private static double physicalNodeScale(double zoom) {
        return Math.max(0.48D, Math.min(1.45D, 0.62D + zoom * 0.12D));
    }

    private static double physicalEdgeHitRadius(double zoom) {
        return Math.max(5.0D, FullRouteMapConfig.LINE_WIDTH_PX + 4.0D);
    }

    private static double missingPathHitRadius(double zoom) {
        return Math.max(6.0D, FullRouteMapConfig.LINE_WIDTH_PX + 5.0D);
    }

    static Aabb2 screenWorldBounds(ViewportState viewport, SPSGui.Rect rect) {
        if (cameraActive(viewport)) {
            Aabb2 bounds = Aabb2.empty();
            bounds = bounds.include(screenToWorld(rect.x(), rect.y(), viewport, rect).x(), screenToWorld(rect.x(), rect.y(), viewport, rect).y());
            bounds = bounds.include(screenToWorld(rect.right(), rect.y(), viewport, rect).x(), screenToWorld(rect.right(), rect.y(), viewport, rect).y());
            bounds = bounds.include(screenToWorld(rect.x(), rect.bottom(), viewport, rect).x(), screenToWorld(rect.x(), rect.bottom(), viewport, rect).y());
            bounds = bounds.include(screenToWorld(rect.right(), rect.bottom(), viewport, rect).x(), screenToWorld(rect.right(), rect.bottom(), viewport, rect).y());
            bounds = bounds.include(screenToWorld(rect.x(), rect.y() + rect.height() * 0.5D, viewport, rect).x(), screenToWorld(rect.x(), rect.y() + rect.height() * 0.5D, viewport, rect).y());
            bounds = bounds.include(screenToWorld(rect.right(), rect.y() + rect.height() * 0.5D, viewport, rect).x(), screenToWorld(rect.right(), rect.y() + rect.height() * 0.5D, viewport, rect).y());
            return bounds;
        }
        double s = scale(viewport);
        double halfW = rect.width() * 0.5D / s;
        double halfH = rect.height() * 0.5D / s;
        return new Aabb2(viewport.centerWorldX() - halfW, viewport.centerWorldZ() - halfH, viewport.centerWorldX() + halfW, viewport.centerWorldZ() + halfH);
    }

    private static double scale(ViewportState viewport) {
        return viewport.zoom() * FullRouteMapConfig.BASE_SCALE;
    }

    private static boolean cameraActive(ViewportState viewport) {
        return viewport.cameraTilted();
    }

    private static double cameraFocusY(SPSGui.Rect rect, ViewportState viewport) {
        double flat = rect.y() + rect.height() * 0.5D;
        double tilted = rect.y() + rect.height() * FullRouteMapConfig.CAMERA_FOCUS_Y_RATIO;
        double t = cameraPitchFactor(viewport);
        return flat + (tilted - flat) * t;
    }

    private static double perspectiveHorizonY(SPSGui.Rect rect, ViewportState viewport) {
        double flat = rect.y() + rect.height() * 0.5D;
        double tilted = rect.y() + rect.height() * FullRouteMapConfig.CAMERA_HORIZON_Y_RATIO;
        double t = cameraPitchFactor(viewport);
        return flat + (tilted - flat) * t;
    }

    private static double perspectiveDepthScale(double screenY, SPSGui.Rect rect, ViewportState viewport) {
        double pitchFactor = cameraPitchFactor(viewport);
        if (pitchFactor <= 0.001D) {
            return 1.0D;
        }
        double horizon = perspectiveHorizonY(rect, viewport);
        double range = Math.max(1.0D, rect.bottom() - horizon);
        double t = Math.max(0.0D, Math.min(1.18D, (screenY - horizon) / range));
        double tiltedScale = 0.66D + t * 0.42D;
        return 1.0D + (tiltedScale - 1.0D) * pitchFactor;
    }

    private static double cameraPitchYScale(ViewportState viewport) {
        return 1.0D - 0.42D * cameraPitchFactor(viewport);
    }

    private static double cameraPitchFactor(ViewportState viewport) {
        return Math.max(0.0D, Math.min(1.0D, viewport.pitchDegrees() / FullRouteMapConfig.CAMERA_PITCH_MAX_DEGREES));
    }

    private static double perspectiveElementZoom(ViewportState viewport, SPSGui.Rect rect, Vec2 screen) {
        if (!cameraActive(viewport)) {
            return viewport.zoom();
        }
        return viewport.zoom() * Math.max(0.58D, Math.min(1.16D, perspectiveDepthScale(screen.y(), rect, viewport)));
    }

    private static boolean perspectiveAllowsLabel(ViewportState viewport, SPSGui.Rect rect, Vec2 screen, MapNode node) {
        if (!cameraActive(viewport) || cameraPitchFactor(viewport) <= 0.001D) {
            return true;
        }
        double horizon = perspectiveHorizonY(rect, viewport);
        double t = Math.max(0.0D, Math.min(1.0D, (screen.y() - horizon) / Math.max(1.0D, rect.bottom() - horizon)));
        if (t < 0.28D) {
            return node.kind() == NodeKind.CLUSTER || node.kind() == NodeKind.DEEP_CLUSTER;
        }
        if (t < 0.42D) {
            return node.kind() != NodeKind.STATION || node.isTransferStation();
        }
        return true;
    }

    private static Optional<SegmentScreen> transferSegment(MapDimensionGraph graph, MapTransferHint hint, ViewportState viewport, SPSGui.Rect rect) {
        MapNode from = graph.node(displayNodeId(graph, hint.from(), viewport.zoom())).orElse(null);
        MapNode to = graph.node(displayNodeId(graph, hint.to(), viewport.zoom())).orElse(null);
        if (from == null || to == null || from.id().equals(to.id()) || !isVisibleNode(from, viewport.zoom()) || !isVisibleNode(to, viewport.zoom())) {
            return Optional.empty();
        }
        Vec2 a = screenForNode(graph, from, viewport, rect);
        Vec2 b = screenForNode(graph, to, viewport, rect);
        double length = a.distanceTo(b);
        if (length <= 1.0D) {
            return Optional.empty();
        }
        double trimA = nodeCollisionRadius(from, viewport.zoom()) + 2.0D;
        double trimB = nodeCollisionRadius(to, viewport.zoom()) + 2.0D;
        if (length <= trimA + trimB + 1.0D) {
            return Optional.empty();
        }
        double ux = (b.x() - a.x()) / length;
        double uy = (b.y() - a.y()) / length;
        return Optional.of(new SegmentScreen(new Vec2(a.x() + ux * trimA, a.y() + uy * trimA), new Vec2(b.x() - ux * trimB, b.y() - uy * trimB)));
    }

    private List<List<Vec2>> missingHintBlockers(MapDimensionGraph graph, VisualRouteMapGraph visualGraph, ViewportState viewport, SPSGui.Rect rect) {
        List<List<Vec2>> blockers = new ArrayList<>();
        if (visualGraph != null) {
            Map<String, MapEdge> rawEdges = graph.edges().stream().collect(HashMap::new, (map, edge) -> map.put(edge.id(), edge), HashMap::putAll);
            for (VisualEdgePath path : visualGraph.edgePaths()) {
                MapEdge edge = rawEdges.get(path.edgeId());
                if (edge == null) {
                    continue;
                }
                this.visualScreenPathForEdge(graph, visualGraph, path, edge, viewport, rect)
                        .filter(points -> points.size() >= 2)
                        .ifPresent(blockers::add);
            }
            return blockers;
        }

        Aabb2 worldView = screenWorldBounds(viewport, rect).inflate(96.0D / scale(viewport));
        Map<String, Double> edgeOffsets = this.visualEdgeOffsets(graph, viewport, rect, worldView);
        for (MapEdge edge : graph.edges()) {
            Optional<Segment> segment = displaySegment(graph, edge, viewport.zoom());
            if (segment.isEmpty()) {
                continue;
            }
            MapNode from = segment.get().from();
            MapNode to = segment.get().to();
            if (!worldView.intersects(Aabb2.empty().include(from.worldX(), from.worldZ()).include(to.worldX(), to.worldZ()).inflate(16.0D))) {
                continue;
            }
            Vec2 a = screenForNode(graph, from, viewport, rect);
            Vec2 b = screenForNode(graph, to, viewport, rect);
            if (a.distanceTo(b) >= 4.0D) {
                blockers.add(offsetScreenPathAnchored(List.of(a, b), edgeOffsets.getOrDefault(edge.id(), 0.0D)));
            }
        }
        return blockers;
    }

    private static List<List<Vec2>> physicalMissingHintBlockers(PhysicalRouteMapGraph graph, ViewportState viewport, SPSGui.Rect rect) {
        List<List<Vec2>> blockers = new ArrayList<>();
        Aabb2 worldView = screenWorldBounds(viewport, rect).inflate(96.0D / scale(viewport));
        for (List<PhysicalMapEdge> group : physicalEdgeGroups(graph.edges(), worldView).values()) {
            if (group.isEmpty() || group.getFirst().points().size() < 2) {
                continue;
            }
            List<Vec2> screenPath = group.getFirst().points().stream()
                    .map(point -> worldToScreen(point.x(), point.y(), viewport, rect))
                    .toList();
            if (screenPath.size() >= 2) {
                blockers.add(screenPath);
            }
        }
        return blockers;
    }

    /**
     * Anchor of a physical missing-cross-dimension hint for the low-zoom warning
     * badge: the anchor node's screen position plus its body radius, resolved with
     * the same gating rules as the full arrow segment (missing node, off-screen
     * anchor) so the badge appears exactly where an arrow would be considered.
     */
    private static Optional<MissingHintAnchor> physicalMissingCrossDimensionHintAnchor(PhysicalRouteMapGraph graph, PhysicalMissingCrossDimensionPathHint hint, ViewportState viewport, SPSGui.Rect rect) {
        PhysicalMapNode node = graph.node(hint.fromNodeId()).orElse(null);
        if (node == null) {
            return Optional.empty();
        }
        Vec2 start = worldToScreen(node.worldX(), node.worldZ(), viewport, rect);
        if (!rect.contains(start.x(), start.y())) {
            return Optional.empty();
        }
        return Optional.of(new MissingHintAnchor(start, physicalNodeRadius(node.kind(), viewport.zoom())));
    }

    /**
     * Same as {@link #physicalMissingCrossDimensionHintAnchor} for data-graph hints,
     * additionally applying the display-node aggregation and node visibility rules of
     * the full arrow segment.
     */
    private static Optional<MissingHintAnchor> missingCrossDimensionHintAnchor(MapDimensionGraph graph, @Nullable VisualRouteMapGraph visualGraph, MissingCrossDimensionPathHint hint, ViewportState viewport, SPSGui.Rect rect) {
        MapNode node = graph.node(displayNodeId(graph, hint.from(), viewport.zoom())).orElse(null);
        if (node == null || !isVisibleNode(node, viewport.zoom())) {
            return Optional.empty();
        }
        Vec2 visual = visualGraph == null ? new Vec2(node.worldX(), node.worldZ()) : visualPosition(graph, visualGraph, node);
        Vec2 start = worldToScreen(visual.x(), visual.y(), viewport, rect);
        if (!rect.contains(start.x(), start.y())) {
            return Optional.empty();
        }
        return Optional.of(new MissingHintAnchor(start, nodeCollisionRadius(node, viewport.zoom())));
    }

    /**
     * Fixed-screen-size warning badge that replaces the missing-cross-dimension arrow
     * below the arrow visibility zoom: a small amber dot offset diagonally just clear
     * of the anchor node's body (badges are painted before edges and nodes, so a
     * centered dot would be overpainted). Both hover variants are captured once and
     * switched from the live hover at replay time, exactly like the full arrow.
     */
    private static void drawMissingHintBadge(GuiGraphicsExtractor graphics, MissingHintAnchor anchor, String hintId) {
        double offset = anchor.nodeRadiusPx() + 4.0D;
        Vec2 center = new Vec2(anchor.screen().x() + offset, anchor.screen().y() - offset);
        List<GuiElementRenderState> normal = SmoothGuiPrimitives.captureBranch(() -> {
            SmoothGuiPrimitives.circle(graphics, center, 3.6D, FullRouteMapConfig.MAP_NODE_OUTLINE);
            SmoothGuiPrimitives.circle(graphics, center, 2.6D, NavigationSemanticColors.WARNING);
        });
        List<GuiElementRenderState> focused = SmoothGuiPrimitives.captureBranch(() -> {
            SmoothGuiPrimitives.circle(graphics, center, 5.4D, FullRouteMapConfig.MAP_FOCUS_HALO);
            SmoothGuiPrimitives.circle(graphics, center, 3.6D, FullRouteMapConfig.MAP_NODE_OUTLINE);
            SmoothGuiPrimitives.circle(graphics, center, 2.6D, NavigationSemanticColors.WARNING);
        });
        SmoothGuiPrimitives.recordVariantSwitch(graphics, normal, focused, () -> liveHover.target() instanceof HitTarget.MissingCrossDimensionPathHit(var hoveredHintId) && hoveredHintId.equals(hintId));
    }

    private static Optional<SegmentScreen> physicalMissingCrossDimensionHintSegment(PhysicalRouteMapGraph graph, PhysicalMissingCrossDimensionPathHint hint, ViewportState viewport, SPSGui.Rect rect, List<List<Vec2>> blockers) {
        PhysicalMapNode node = graph.node(hint.fromNodeId()).orElse(null);
        if (node == null) {
            return Optional.empty();
        }
        Vec2 start = worldToScreen(node.worldX(), node.worldZ(), viewport, rect);
        if (!rect.contains(start.x(), start.y())) {
            return Optional.empty();
        }
        return Optional.of(missingCrossDimensionHintSegment(start, hint.directionX(), hint.directionZ(), viewport.zoom(), blockers));
    }

    private static Optional<SegmentScreen> missingCrossDimensionHintSegment(MapDimensionGraph graph, VisualRouteMapGraph visualGraph, MissingCrossDimensionPathHint hint, ViewportState viewport, SPSGui.Rect rect, List<List<Vec2>> blockers) {
        MapNode node = graph.node(displayNodeId(graph, hint.from(), viewport.zoom())).orElse(null);
        if (node == null || !isVisibleNode(node, viewport.zoom())) {
            return Optional.empty();
        }
        Vec2 visual = visualGraph == null ? new Vec2(node.worldX(), node.worldZ()) : visualPosition(graph, visualGraph, node);
        Vec2 start = worldToScreen(visual.x(), visual.y(), viewport, rect);
        if (!rect.contains(start.x(), start.y())) {
            return Optional.empty();
        }
        return Optional.of(missingCrossDimensionHintSegment(start, hint.directionX(), hint.directionZ(), viewport.zoom(), blockers));
    }

    private static SegmentScreen missingCrossDimensionHintSegment(Vec2 start, double directionX, double directionY, double zoom, List<List<Vec2>> blockers) {
        double dx = directionX;
        double dy = directionY;
        double directionLength = Math.hypot(dx, dy);
        if (directionLength < 0.001D) {
            dx = 1.0D;
            dy = 0.0D;
        } else {
            dx /= directionLength;
            dy /= directionLength;
        }
        double length = Math.max(24.0D, Math.min(56.0D, 22.0D + zoom * 7.0D));
        SegmentScreen best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        Vec2 preferred = new Vec2(dx, dy);
        for (double rotation : missingHintRotations()) {
            Vec2 direction = rotate(preferred, rotation);
            SegmentScreen candidate = new SegmentScreen(start, new Vec2(start.x() + direction.x() * length, start.y() + direction.y() * length));
            double score = Math.abs(rotation) * 18.0D + missingHintRoutePenalty(candidate, blockers, length);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best == null ? new SegmentScreen(start, new Vec2(start.x() + dx * length, start.y() + dy * length)) : best;
    }

    private static double missingHintRoutePenalty(SegmentScreen candidate, List<List<Vec2>> blockers, double length) {
        if (blockers.isEmpty()) {
            return 0.0D;
        }
        double startTrim = Math.min(14.0D, length * 0.36D);
        List<Vec2> candidatePath = List.of(pointBetween(candidate.a(), candidate.b(), startTrim), candidate.b());
        double penalty = 0.0D;
        for (List<Vec2> blocker : blockers) {
            if (blocker.size() < 2) {
                continue;
            }
            double distance = sampledPathDistance(candidatePath, blocker);
            double clearance = FullRouteMapConfig.LINE_WIDTH_PX + 5.0D;
            if (distance < clearance) {
                double miss = clearance - distance;
                penalty += 1_700.0D + miss * miss * 18.0D;
            }
            if (pathsIntersect(candidatePath, blocker)) {
                penalty += 6_000.0D;
            }
        }
        return penalty;
    }

    private static List<Double> missingHintRotations() {
        return List.of(
                0.0D,
                Math.toRadians(22.0D), Math.toRadians(-22.0D),
                Math.toRadians(45.0D), Math.toRadians(-45.0D),
                Math.toRadians(70.0D), Math.toRadians(-70.0D),
                Math.toRadians(100.0D), Math.toRadians(-100.0D),
                Math.toRadians(135.0D), Math.toRadians(-135.0D));
    }

    private static Vec2 rotate(Vec2 direction, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec2(direction.x() * cos - direction.y() * sin, direction.x() * sin + direction.y() * cos);
    }

    private static Vec2 pointBetween(Vec2 a, Vec2 b, double distanceFromA) {
        double length = a.distanceTo(b);
        if (length < 0.001D) {
            return a;
        }
        double t = Math.max(0.0D, Math.min(1.0D, distanceFromA / length));
        return new Vec2(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t);
    }

    private static Vec2 midpoint(Vec2 a, Vec2 b) {
        return new Vec2((a.x() + b.x()) * 0.5D, (a.y() + b.y()) * 0.5D);
    }

    private static Optional<MapTransferHint> transferHintForHover(MapDimensionGraph graph, HitTarget hover) {
        return hover instanceof HitTarget.TransferHintHit(var hintId)
                ? graph.transferHints().stream().filter(hint -> hint.id().equals(hintId)).findFirst()
                : Optional.empty();
    }

    private static Set<NodeId> transferHighlightNodeIds(MapDimensionGraph graph, HitTarget hover) {
        return transferHintForHover(graph, hover)
                .map(hint -> Set.of(hint.from(), hint.to()))
                .orElse(Set.of());
    }

    private static Optional<MapNode> foldPeerNode(MapDimensionGraph graph, MapNode node) {
        return node.foldPeerId().flatMap(peer -> graph.nodes().stream()
                .filter(candidate -> candidate.foldAnchorId().filter(peer::equals).isPresent())
                .findFirst());
    }

    private static FoldLineIndex foldLineIndexCache;

    /**
     * Scans the edge list once and collects the route lines touching each node, replacing
     * the per-fold-node full edge scan that ran every frame. Rebuilt whenever the edge
     * list or the route/pipe data revision changes.
     */
    private static FoldLineIndex foldLineIndex(MapDimensionGraph graph) {
        List<MapEdge> edges = graph.edges();
        long routeRevision = ClientRouteDataCache.revision();
        long pipeRevision = ClientPipeNetworkCache.aggregateRevision();
        FoldLineIndex cached = foldLineIndexCache;
        if (cached != null && cached.sourceEdges() == edges && cached.routeRevision() == routeRevision && cached.pipeRevision() == pipeRevision) {
            return cached;
        }
        Map<NodeId, Set<UUID>> lineIdsByNode = new HashMap<>();
        for (MapEdge edge : edges) {
            if (edge.routeLineIds().isEmpty()) {
                continue;
            }
            lineIdsByNode.computeIfAbsent(edge.from(), ignored -> new HashSet<>()).addAll(edge.routeLineIds());
            lineIdsByNode.computeIfAbsent(edge.to(), ignored -> new HashSet<>()).addAll(edge.routeLineIds());
        }
        FoldLineIndex built = new FoldLineIndex(edges, routeRevision, pipeRevision, lineIdsByNode);
        foldLineIndexCache = built;
        return built;
    }

    private static int foldOutlineColor(MapDimensionGraph graph, MapNode node) {
        Set<UUID> lineIds = foldLineIndex(graph).lineIdsByNode().get(node.id());
        if (lineIds != null && lineIds.size() == 1) {
            return lineIds.stream()
                    .findFirst()
                    .flatMap(ClientRouteDataCache::routeLine)
                    .map(line -> SPSGui.opaque(line.themeColors().getFirst()))
                    .orElse(FullRouteMapConfig.MAP_FOLD_MULTI_LINE);
        }
        return FullRouteMapConfig.MAP_FOLD_MULTI_LINE;
    }

    private static int physicalFoldOutlineColor(PhysicalRouteMapGraph graph, PhysicalMapNode node) {
        Set<UUID> lineIds = new HashSet<>();
        for (PhysicalMapEdge edge : graph.edges()) {
            if (edge.fromNodeId().filter(node.id()::equals).isPresent() || edge.toNodeId().filter(node.id()::equals).isPresent()) {
                lineIds.add(edge.metadata().routeLineId());
            }
        }
        if (lineIds.size() == 1) {
            return lineIds.stream()
                    .findFirst()
                    .flatMap(ClientRouteDataCache::routeLine)
                    .map(line -> SPSGui.opaque(line.themeColors().getFirst()))
                    .orElse(FullRouteMapConfig.MAP_FOLD_MULTI_LINE);
        }
        return FullRouteMapConfig.MAP_FOLD_MULTI_LINE;
    }

    private static Optional<Vec2> transferAxis(VisualRouteMapGraph graph, NodeId nodeId, ViewportState viewport, SPSGui.Rect rect) {
        return transferAxisWorld(graph, nodeId)
                .map(axis -> screenAxisAt(visualNodeCenter(graph, nodeId), axis, viewport, rect))
                .filter(axis -> Math.hypot(axis.x(), axis.y()) > 0.001D);
    }

    private static Vec2 screenAxisAt(Vec2 center, Vec2 axis, ViewportState viewport, SPSGui.Rect rect) {
        if (center == null) {
            return axis;
        }
        double length = Math.hypot(axis.x(), axis.y());
        if (length <= 0.001D) {
            return axis;
        }
        double ux = axis.x() / length;
        double uy = axis.y() / length;
        Vec2 a = worldToScreen(center.x() - ux * 16.0D, center.y() - uy * 16.0D, viewport, rect);
        Vec2 b = worldToScreen(center.x() + ux * 16.0D, center.y() + uy * 16.0D, viewport, rect);
        return new Vec2(b.x() - a.x(), b.y() - a.y());
    }

    private static Vec2 visualNodeCenter(VisualRouteMapGraph graph, NodeId nodeId) {
        return graph.node(nodeId)
                .map(node -> new Vec2(node.x(), node.z()))
                .orElse(new Vec2(0.0D, 0.0D));
    }

    private static Optional<Vec2> transferAxisWorld(VisualRouteMapGraph graph, NodeId nodeId) {
        List<TransferSpoke> spokes = new ArrayList<>();
        double total = 0.0D;
        for (VisualEdgePath path : graph.edgePaths()) {
            Optional<Vec2> direction = adjacentDirection(path, nodeId);
            if (direction.isEmpty()) {
                continue;
            }
            Vec2 value = direction.get();
            double length = Math.hypot(value.x(), value.y());
            if (length <= 0.001D) {
                continue;
            }
            double ux = value.x() / length;
            double uy = value.y() / length;
            double weight = Math.max(1.0D, Math.max(path.routeLineIds().size(), path.occurrences().stream().map(MapEdgeOccurrence::routeLineId).distinct().count()));
            int directionIndex = nearestDirectionIndex(ux, uy);
            spokes.add(new TransferSpoke(directionIndex, directionAxisIndex(directionIndex), ux, uy, weight, path.routeLineIds().size(), Set.copyOf(path.routeLineIds())));
            total += weight;
        }
        if (total <= 0.0D) {
            return Optional.empty();
        }

        double[] directionWeights = new double[8];
        double[] axisWeights = new double[4];
        double vectorX = 0.0D;
        double vectorY = 0.0D;
        for (TransferSpoke spoke : spokes) {
            directionWeights[spoke.directionIndex()] += spoke.weight();
            axisWeights[spoke.axisIndex()] += spoke.weight();
            vectorX += spoke.ux() * spoke.weight();
            vectorY += spoke.uy() * spoke.weight();
        }

        int strongestThroughAxis = -1;
        double strongestThroughWeight = 0.0D;
        for (int axis = 0; axis < 4; axis++) {
            int first = axis;
            int opposite = (axis + 4) % 8;
            if (directionWeights[first] <= 0.0D || directionWeights[opposite] <= 0.0D) {
                continue;
            }
            double throughWeight = directionWeights[first] + directionWeights[opposite] + sharedRouteThroughWeight(spokes, first, opposite) * 0.55D;
            if (throughWeight > strongestThroughWeight) {
                strongestThroughWeight = throughWeight;
                strongestThroughAxis = axis;
            }
        }
        if (strongestThroughAxis >= 0 && strongestThroughWeight / total >= 0.34D) {
            int bestAxis = maxIndex(axisWeights);
            if (axisWeights[strongestThroughAxis] >= axisWeights[bestAxis] * 0.72D || strongestThroughWeight / total >= 0.52D) {
                return Optional.of(axisVector(strongestThroughAxis));
            }
        }

        int strongestDirection = maxIndex(directionWeights);
        double secondDirectionWeight = secondLargest(directionWeights, strongestDirection);
        TransferSpoke strongestSpoke = spokes.stream()
                .filter(spoke -> spoke.directionIndex() == strongestDirection)
                .max(Comparator.comparingDouble(TransferSpoke::routeWeight).thenComparingDouble(TransferSpoke::weight))
                .orElse(null);
        if (strongestSpoke != null
                && directionWeights[strongestDirection] >= Math.max(1.0D, secondDirectionWeight * 1.28D)
                && (strongestSpoke.routeWeight() > 1.0D || directionWeights[strongestDirection] / total >= 0.42D)) {
            return Optional.of(axisVector(strongestSpoke.axisIndex()));
        }

        double vectorLength = Math.hypot(vectorX, vectorY);
        if (vectorLength / total >= 0.26D) {
            return Optional.of(axisVector(directionAxisIndex(nearestDirectionIndex(vectorX / vectorLength, vectorY / vectorLength))));
        }

        int bestAxis = maxIndex(axisWeights);
        double secondAxisWeight = secondLargest(axisWeights, bestAxis);
        if (axisWeights[bestAxis] >= Math.max(1.0D, secondAxisWeight * 1.18D) || axisWeights[bestAxis] / total >= 0.46D) {
            return Optional.of(axisVector(bestAxis));
        }
        return Optional.empty();
    }

    private static double sharedRouteThroughWeight(List<TransferSpoke> spokes, int firstDirection, int oppositeDirection) {
        double weight = 0.0D;
        for (TransferSpoke first : spokes) {
            if (first.directionIndex() != firstDirection || first.routeLineIds().isEmpty()) {
                continue;
            }
            for (TransferSpoke opposite : spokes) {
                if (opposite.directionIndex() != oppositeDirection || opposite.routeLineIds().isEmpty()) {
                    continue;
                }
                if (first.routeLineIds().stream().anyMatch(opposite.routeLineIds()::contains)) {
                    weight += Math.min(first.weight(), opposite.weight());
                }
            }
        }
        return weight;
    }

    private static Optional<Vec2> adjacentDirection(VisualEdgePath path, NodeId nodeId) {
        if (path.points().size() < 2) {
            return Optional.empty();
        }
        if (path.from().equals(nodeId)) {
            Vec2 first = path.points().get(0);
            Vec2 second = path.points().get(1);
            return Optional.of(new Vec2(second.x() - first.x(), second.y() - first.y()));
        }
        if (path.to().equals(nodeId)) {
            Vec2 previous = path.points().get(path.points().size() - 2);
            Vec2 last = path.points().getLast();
            return Optional.of(new Vec2(previous.x() - last.x(), previous.y() - last.y()));
        }
        return Optional.empty();
    }

    private static int nearestDirectionIndex(double ux, double uy) {
        double[][] directions = transferDirections();
        int best = 0;
        double bestDot = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < directions.length; i++) {
            double dot = ux * directions[i][0] + uy * directions[i][1];
            if (dot > bestDot) {
                bestDot = dot;
                best = i;
            }
        }
        return best;
    }

    private static int directionAxisIndex(int directionIndex) {
        return Math.floorMod(directionIndex, 4);
    }

    private static Vec2 axisVector(int axisIndex) {
        double[][] directions = transferDirections();
        double[] direction = directions[Math.floorMod(axisIndex, 4)];
        return new Vec2(direction[0], direction[1]);
    }

    private static int maxIndex(double[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) {
                best = i;
            }
        }
        return best;
    }

    private static double secondLargest(double[] values, int except) {
        double best = 0.0D;
        for (int i = 0; i < values.length; i++) {
            if (i != except) {
                best = Math.max(best, values[i]);
            }
        }
        return best;
    }

    private static double[][] transferDirections() {
        double h = Math.sqrt(0.5D);
        return new double[][] {
                { 1.0D, 0.0D },
                { h, h },
                { 0.0D, 1.0D },
                { -h, h },
                { -1.0D, 0.0D },
                { -h, -h },
                { 0.0D, -1.0D },
                { h, -h }
        };
    }

    private record TransferSpoke(int directionIndex, int axisIndex, double ux, double uy, double weight, double routeWeight, Set<UUID> routeLineIds) {}

    private static void drawNodeFocus(GuiGraphicsExtractor graphics, MapNode node, Vec2 center, int radius, double zoom) {
        double haloScale = iconScale(zoom);
        switch (node.kind()) {
            case FOLD_ANCHOR -> {
                SmoothGuiPrimitives.diamond(graphics, center, radius + 5.0D * haloScale, FullRouteMapConfig.MAP_FOCUS_HALO);
                SmoothGuiPrimitives.diamond(graphics, center, radius + 2.0D * haloScale, FullRouteMapConfig.MAP_FOCUS_RING);
            }
            case STATION -> {
                if (node.isTransferStation()) {
                    SmoothGuiPrimitives.capsule(graphics, center, radius * 3.0D + 10.0D * haloScale, radius * 2.0D + 10.0D * haloScale, FullRouteMapConfig.MAP_FOCUS_HALO);
                    SmoothGuiPrimitives.capsule(graphics, center, radius * 3.0D + 4.0D * haloScale, radius * 2.0D + 4.0D * haloScale, FullRouteMapConfig.MAP_FOCUS_RING);
                } else {
                    SmoothGuiPrimitives.circle(graphics, center, radius + 5.0D * haloScale, FullRouteMapConfig.MAP_FOCUS_HALO);
                    SmoothGuiPrimitives.circle(graphics, center, radius + 2.0D * haloScale, FullRouteMapConfig.MAP_FOCUS_RING);
                }
            }
            case CLUSTER, DEEP_CLUSTER -> {
                SmoothGuiPrimitives.circle(graphics, center, radius + 5.0D * haloScale, FullRouteMapConfig.MAP_FOCUS_HALO);
                SmoothGuiPrimitives.circle(graphics, center, radius + 2.0D * haloScale, FullRouteMapConfig.MAP_FOCUS_RING);
            }
        }
    }

    private static void drawDirectedTransferFocus(GuiGraphicsExtractor graphics, Vec2 center, int radius, Vec2 axis, double zoom) {
        double haloScale = iconScale(zoom);
        SmoothGuiPrimitives.capsule(graphics, center, axis, radius * 3.0D + 10.0D * haloScale, radius * 2.0D + 10.0D * haloScale, FullRouteMapConfig.MAP_FOCUS_HALO);
        SmoothGuiPrimitives.capsule(graphics, center, axis, radius * 3.0D + 4.0D * haloScale, radius * 2.0D + 4.0D * haloScale, FullRouteMapConfig.MAP_FOCUS_RING);
    }

    private static void drawPortalFocus(GuiGraphicsExtractor graphics, Vec2 center, int radius, double zoom) {
        double haloScale = iconScale(zoom);
        SmoothGuiPrimitives.capsule(graphics, center, radius * 1.8D + 10.0D * haloScale, radius * 2.5D + 10.0D * haloScale, FullRouteMapConfig.MAP_FOCUS_HALO);
        SmoothGuiPrimitives.capsule(graphics, center, radius * 1.8D + 4.0D * haloScale, radius * 2.5D + 4.0D * haloScale, FullRouteMapConfig.MAP_FOCUS_RING);
    }

    private static void drawPortalIcon(GuiGraphicsExtractor graphics, Vec2 center, int radius, VisualNode node) {
        List<RouteLine> lines = routeLinesForIds(node.routeLineIds());
        List<Integer> colors = portalColors(lines);
        int outline = portalOutlineColor(lines, colors);
        // Proportional footprint (was clamped to 6x9 minimum) so the portal glyph keeps
        // shrinking with zoom and never outgrows the station glyph at minimum zoom.
        double outerW = radius * 1.5D;
        double outerH = radius * 2.2D;
        double outlinePad = Math.max(1.0D, radius * 0.75D);
        SmoothGuiPrimitives.capsule(graphics, center, outerW + outlinePad, outerH + outlinePad, outline);
        SmoothGuiPrimitives.capsule(graphics, center, outerW, outerH, FullRouteMapConfig.MAP_NODE_FILL);
        if (colors.size() == 1) {
            SmoothGuiPrimitives.capsule(graphics, center, Math.max(3.0D, outerW * 0.42D), Math.max(5.0D, outerH * 0.60D), SPSGui.withAlpha(colors.getFirst(), 0xDD));
            drawLine(graphics, new Vec2(center.x(), center.y() - outerH * 0.32D), new Vec2(center.x(), center.y() + outerH * 0.32D), Math.max(1.0D, radius * 0.16D), FullRouteMapConfig.MAP_NODE_FILL);
            return;
        }
        double stripeWidth = Math.max(1.2D, outerW * 0.58D / colors.size());
        double centerOffset = (colors.size() - 1) * 0.5D;
        for (int i = 0; i < colors.size(); i++) {
            double x = center.x() + (i - centerOffset) * stripeWidth;
            drawLine(graphics, new Vec2(x, center.y() - outerH * 0.28D), new Vec2(x, center.y() + outerH * 0.28D), stripeWidth + 0.2D, colors.get(i));
        }
    }

    private static List<Integer> portalColors(List<RouteLine> lines) {
        if (lines.isEmpty()) {
            return List.of(FullRouteMapConfig.MAP_FOLD_MULTI_LINE);
        }
        if (lines.size() == 1) {
            return routeLineColors(lines.getFirst());
        }
        if (lines.size() >= FullRouteMapConfig.TRUNK_THRESHOLD) {
            return lines.stream()
                    .map(line -> routeLineColors(line).getFirst())
                    .limit(6)
                    .toList();
        }
        return lines.stream()
                .map(line -> routeLineColors(line).getFirst())
                .toList();
    }

    private static int portalOutlineColor(List<RouteLine> lines, List<Integer> colors) {
        if (lines.size() == 1 && !colors.isEmpty()) {
            return colors.getFirst();
        }
        if (lines.size() >= FullRouteMapConfig.TRUNK_THRESHOLD) {
            return FullRouteMapConfig.MAP_TRUNK;
        }
        return FullRouteMapConfig.MAP_NODE_OUTLINE;
    }

    private void drawEdgeRouteBundles(GuiGraphicsExtractor graphics, MapEdge edge, Vec2 a, Vec2 b, List<RouteLine> allLines, double baseOffset, BooleanSupplier hovered, double laneWidth, double zoom, EdgeBlockerIndex edgeBlockers) {
        drawRouteBundle(graphics, a, b, edgeLanes(allLines), laneWidth, baseOffset, hovered, zoom, edgeBlockers);
    }

    private static void drawRouteBundle(GuiGraphicsExtractor graphics, Vec2 a, Vec2 b, List<EdgeLane> lanes, double laneWidth, double baseOffset, BooleanSupplier hovered, double zoom, EdgeBlockerIndex edgeBlockers) {
        if (lanes.isEmpty()) {
            return;
        }
        List<Vec2> basePath = offsetScreenPathAnchored(List.of(a, b), baseOffset);
        double haloWidth = routeBundleWidth(lanes, laneWidth) + 5.0D * iconScale(zoom);
        // Hover halo slot: re-evaluated against the live hover at replay time and kept
        // underneath the lanes, exactly as the uncached render ordered it.
        SmoothGuiPrimitives.recordOrReplay(graphics, g -> {
            if (hovered.getAsBoolean()) {
                drawPolyline(g, basePath, haloWidth, FullRouteMapConfig.MAP_FOCUS_HALO, true, laneWidth);
            }
        });
        double step = laneWidth + 1.0D;
        double center = (lanes.size() - 1) * 0.5D;
        for (int i = 0; i < lanes.size(); i++) {
            List<Vec2> lanePath = offsetScreenPathAnchored(List.of(a, b), baseOffset + (i - center) * step);
            drawColorLanePath(graphics, lanePath, laneWidth, lanes.get(i).colors());
            addEdgeBlockerPath(edgeBlockers, lanePath, laneWidth * 0.5D);
        }
    }

    private static double routeBundleWidth(List<EdgeLane> lanes, double laneWidth) {
        return lanes.isEmpty() ? laneWidth : lanes.size() * laneWidth + Math.max(0, lanes.size() - 1);
    }

    private static List<EdgeLane> edgeLanes(List<RouteLine> lines) {
        if (lines.isEmpty()) {
            return List.of(new EdgeLane(List.of(FullRouteMapConfig.MAP_TRUNK)));
        }
        return lines.stream()
                .map(line -> new EdgeLane(routeLineColors(line)))
                .toList();
    }

    private static List<RouteLine> routeLinesForEdge(MapEdge edge) {
        return routeLinesForIds(edge.routeLineIds());
    }

    private static final int ROUTE_LINE_CACHE_LIMIT = 64;
    private static long routeLineCacheRevision = Long.MIN_VALUE;
    private static final Map<List<UUID>, List<RouteLine>> ROUTE_LINES_BY_IDS = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<List<UUID>, List<RouteLine>> eldest) {
            return size() > ROUTE_LINE_CACHE_LIMIT;
        }
    };
    private static final Map<List<UUID>, List<Integer>> ROUTE_LINE_COLORS_BY_IDS = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<List<UUID>, List<Integer>> eldest) {
            return size() > ROUTE_LINE_CACHE_LIMIT;
        }
    };

    private static void ensureRouteLineCacheRevision() {
        long revision = ClientRouteDataCache.revision();
        if (revision != routeLineCacheRevision) {
            routeLineCacheRevision = revision;
            ROUTE_LINES_BY_IDS.clear();
            ROUTE_LINE_COLORS_BY_IDS.clear();
        }
    }

    /**
     * Resolves and sorts route lines by translated display name once per id set and data
     * revision; the sorted result is shared by every caller until the route data changes.
     */
    private static List<RouteLine> routeLinesForIds(List<UUID> routeLineIds) {
        ensureRouteLineCacheRevision();
        return ROUTE_LINES_BY_IDS.computeIfAbsent(List.copyOf(routeLineIds), FullRouteMapRenderer::resolveRouteLinesForIds);
    }

    private static List<RouteLine> resolveRouteLinesForIds(List<UUID> routeLineIds) {
        return routeLineIds.stream()
                .map(ClientRouteDataCache::routeLine)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing((RouteLine line) -> FullMapText.displayName(line)).thenComparing(RouteLine::id))
                .toList();
    }

    private static List<Integer> routeLineColorsForIds(List<UUID> routeLineIds) {
        ensureRouteLineCacheRevision();
        return ROUTE_LINE_COLORS_BY_IDS.computeIfAbsent(List.copyOf(routeLineIds), FullRouteMapRenderer::resolveRouteLineColorsForIds);
    }

    private static List<Integer> resolveRouteLineColorsForIds(List<UUID> routeLineIds) {
        return routeLinesForIds(routeLineIds).stream()
                .flatMap(line -> routeLineColors(line).stream())
                .toList();
    }

    private static List<Integer> routeLineColors(RouteLine line) {
        List<Integer> colors = line.themeColors().stream()
                .map(SPSGui::opaque)
                .toList();
        return colors.isEmpty() ? List.of(FullRouteMapConfig.MAP_TRUNK) : colors;
    }

    private void drawTrunkDots(GuiGraphicsExtractor graphics, Vec2 a, Vec2 b, List<RouteLine> lines, double zoom) {
        this.drawTrunkDotsForColors(graphics, List.of(a, b), lines.stream().map(line -> routeLineColors(line).getFirst()).toList(), zoom);
    }

    private void drawTrunkDots(GuiGraphicsExtractor graphics, List<Vec2> points, List<RouteLine> lines, double zoom) {
        this.drawTrunkDotsForColors(graphics, points, lines.stream().map(line -> routeLineColors(line).getFirst()).toList(), zoom);
    }

    private void drawTrunkDotsForColors(GuiGraphicsExtractor graphics, List<Vec2> points, List<Integer> colors, double zoom) {
        if (zoom < FullRouteMapConfig.TRUNK_DOT_MIN_ZOOM) {
            return;
        }
        List<Vec2> centers = trunkDotCenters(points, colors, zoom);
        for (int i = 0; i < centers.size(); i++) {
            fillCircle(graphics, centers.get(i), Math.max(1, (int) Math.round(trunkDotRadius(zoom))), colors.get(i));
        }
    }

    private static List<Vec2> trunkDotCenters(List<Vec2> points, List<Integer> colors, double zoom) {
        if (points.size() < 2 || zoom < FullRouteMapConfig.TRUNK_DOT_MIN_ZOOM) {
            return List.of();
        }
        double half = polylineLength(points) * 0.5D;
        double walked = 0.0D;
        Vec2 a = points.get(0);
        Vec2 b = points.get(1);
        for (int i = 0; i + 1 < points.size(); i++) {
            double length = points.get(i).distanceTo(points.get(i + 1));
            if (walked + length >= half) {
                a = points.get(i);
                b = points.get(i + 1);
                break;
            }
            walked += length;
        }
        Vec2 mid = new Vec2((a.x() + b.x()) * 0.5D, (a.y() + b.y()) * 0.5D);
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double length = Math.max(1.0D, Math.hypot(dx, dy));
        double nx = -dy / length;
        double ny = dx / length;
        int count = Math.min(colors.size(), 8);
        double radius = trunkDotRadius(zoom);
        double spacing = Math.max(3.0D, radius * 2.35D);
        double start = -(count - 1) * spacing * 0.5D;
        List<Vec2> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(new Vec2(mid.x() + nx * (start + i * spacing), mid.y() + ny * (start + i * spacing)));
        }
        return result;
    }

    private static double trunkDotRadius(double zoom) {
        return Math.max(1.0D, 2.4D * iconScale(zoom));
    }

    private static void drawLine(GuiGraphicsExtractor graphics, Vec2 a, Vec2 b, double width, int color) {
        SmoothGuiPrimitives.line(graphics, a, b, width, color);
    }

    private static void drawPolyline(GuiGraphicsExtractor graphics, List<Vec2> points, double width, int color) {
        SmoothGuiPrimitives.polyline(graphics, points, width, color);
    }

    private static void drawPolyline(GuiGraphicsExtractor graphics, List<Vec2> points, double width, int color, boolean roundCaps) {
        SmoothGuiPrimitives.polyline(graphics, points, width, color, roundCaps);
    }

    /**
     * Hover halo drawn wider than the line it surrounds, on the same centerline: the
     * halo's corner rounding is derived from the base line's width
     * ({@code roundingReferenceWidth}) instead of the halo width, so halo and line share
     * the same corner cutting and the halo no longer deviates at turns.
     */
    private static void drawPolyline(GuiGraphicsExtractor graphics, List<Vec2> points, double width, int color, boolean roundCaps, double roundingReferenceWidth) {
        SmoothGuiPrimitives.polyline(graphics, points, width, color, roundCaps, roundingReferenceWidth);
    }

    private void drawVisualEdgeRouteBundle(GuiGraphicsExtractor graphics, List<Vec2> screenPath, List<RouteLine> allLines, BooleanSupplier hovered, SemanticEdgeKind kind, double zoom, EdgeBlockerIndex edgeBlockers) {
        List<EdgeLane> lanes = edgeLanes(allLines);
        if (lanes.isEmpty()) {
            return;
        }
        double laneWidth = Math.max(1.2D, FullRouteMapConfig.LINE_WIDTH_PX * lineWidthScale(zoom));
        double haloWidth = routeBundleWidth(lanes, laneWidth) + 5.0D * iconScale(zoom);
        // Hover halo slot: re-evaluated against the live hover at replay time.
        SmoothGuiPrimitives.recordOrReplay(graphics, g -> {
            if (hovered.getAsBoolean()) {
                drawPolyline(g, screenPath, haloWidth, FullRouteMapConfig.MAP_FOCUS_HALO, true, laneWidth);
            }
        });
        if (kind == SemanticEdgeKind.FOLD_ADJACENT && allLines.isEmpty()) {
            drawPolyline(graphics, screenPath, laneWidth, FullRouteMapConfig.MAP_FOLD_MULTI_LINE);
            addEdgeBlockerPath(edgeBlockers, screenPath, laneWidth * 0.5D);
            return;
        }
        double step = laneWidth + 1.0D;
        double center = (lanes.size() - 1) * 0.5D;
        for (int i = 0; i < lanes.size(); i++) {
            List<Vec2> lanePath = offsetScreenPathAnchored(screenPath, (i - center) * step);
            drawColorLanePath(graphics, lanePath, laneWidth, lanes.get(i).colors());
            addEdgeBlockerPath(edgeBlockers, lanePath, laneWidth * 0.5D);
        }
    }

    private void drawPureSchematicEdgeRouteBundle(GuiGraphicsExtractor graphics, List<Vec2> screenPath, VisualEdgePath path, BooleanSupplier hovered, double zoom) {
        double laneWidth = Math.max(1.2D, FullRouteMapConfig.LINE_WIDTH_PX * lineWidthScale(zoom));
        if (path.kind() == SemanticEdgeKind.FOLD_ADJACENT && path.routeLineIds().isEmpty()) {
            drawPolyline(graphics, screenPath, laneWidth, FullRouteMapConfig.MAP_FOLD_MULTI_LINE, false);
            return;
        }
        List<VisualLane> visualLanes = path.lanes();
        if (visualLanes.isEmpty()) {
            List<EdgeLane> lanes = edgeLanes(routeLinesForIds(path.routeLineIds()));
            double haloWidth = routeBundleWidth(lanes, laneWidth) + 5.0D * iconScale(zoom);
            // Hover halo slot: re-evaluated against the live hover at replay time.
            SmoothGuiPrimitives.recordOrReplay(graphics, g -> {
                if (hovered.getAsBoolean()) {
                    drawPolyline(g, screenPath, haloWidth, FullRouteMapConfig.MAP_FOCUS_HALO, false, laneWidth);
                }
            });
            double step = laneWidth + 1.0D;
            double center = (lanes.size() - 1) * 0.5D;
            for (int i = 0; i < lanes.size(); i++) {
                drawColorLanePath(graphics, offsetScreenPath(screenPath, (i - center) * step), laneWidth, lanes.get(i).colors(), false);
            }
            return;
        }
        // Solver lane offsets are fixed layout units, so they must follow the lane-width zoom
        // curve: scaling the offset by lineWidthScale(zoom) keeps the zoom=1 packing ratio and
        // collinear lanes stay packed instead of opening gaps when zoomed out.
        double laneOffsetScale = FullRouteMapConfig.BASE_SCALE * lineWidthScale(zoom);
        double maxAbsOffset = 0.0D;
        for (VisualLane lane : visualLanes) {
            maxAbsOffset = Math.max(maxAbsOffset, Math.abs(lane.offsetBlocks()) * laneOffsetScale);
        }
        // The halo spans exactly the spread lanes: twice the outermost lane offset plus one lane width.
        double haloWidth = 2.0D * maxAbsOffset + laneWidth + 5.0D * iconScale(zoom);
        // Hover halo slot: re-evaluated against the live hover at replay time.
        SmoothGuiPrimitives.recordOrReplay(graphics, g -> {
            if (hovered.getAsBoolean()) {
                drawPolyline(g, screenPath, haloWidth, FullRouteMapConfig.MAP_FOCUS_HALO, false, laneWidth);
            }
        });
        for (VisualLane lane : visualLanes) {
            List<Vec2> lanePath = offsetScreenPath(screenPath, lane.offsetBlocks() * laneOffsetScale);
            drawColorLanePath(graphics, lanePath, laneWidth, colorsForVisualLane(lane), false);
        }
    }

    private static void drawColorLanePath(GuiGraphicsExtractor graphics, List<Vec2> points, double totalWidth, List<Integer> colors) {
        drawColorLanePath(graphics, points, totalWidth, colors, true);
    }

    private static void drawColorLanePath(GuiGraphicsExtractor graphics, List<Vec2> points, double totalWidth, List<Integer> colors, boolean roundCaps) {
        if (colors.isEmpty()) {
            drawPolyline(graphics, points, totalWidth, FullRouteMapConfig.MAP_TRUNK, roundCaps);
            return;
        }
        if (colors.size() == 1) {
            drawPolyline(graphics, points, totalWidth, colors.getFirst(), roundCaps);
            return;
        }
        double stripeWidth = totalWidth / colors.size();
        double center = (colors.size() - 1) * 0.5D;
        for (int i = 0; i < colors.size(); i++) {
            drawPolyline(graphics, offsetScreenPath(points, (i - center) * stripeWidth), stripeWidth + 0.18D, colors.get(i), roundCaps);
        }
    }

    private static List<Integer> colorsForVisualLane(VisualLane lane) {
        return lane.routeLineId()
                .flatMap(ClientRouteDataCache::routeLine)
                .map(FullRouteMapRenderer::routeLineColors)
                .orElse(List.of(FullRouteMapConfig.MAP_TRUNK));
    }

    private static void drawDashedLine(GuiGraphicsExtractor graphics, Vec2 a, Vec2 b, double width, int color, double dash, double gap) {
        SmoothGuiPrimitives.dashedPolyline(graphics, List.of(a, b), width, color, dash, gap);
    }

    private static void drawFadingDashedRouteLine(GuiGraphicsExtractor graphics, Vec2 a, Vec2 b, double totalWidth, List<Integer> colors) {
        List<Integer> routeColors = colors.isEmpty() ? List.of(FullRouteMapConfig.MAP_TRUNK) : colors;
        if (routeColors.size() == 1) {
            drawFadingDashedLine(graphics, a, b, totalWidth, routeColors.getFirst(), 7.0D, 5.0D);
            return;
        }
        double stripeWidth = totalWidth / routeColors.size();
        double center = (routeColors.size() - 1) * 0.5D;
        for (int i = 0; i < routeColors.size(); i++) {
            List<Vec2> stripe = offsetScreenPath(List.of(a, b), (i - center) * stripeWidth);
            drawFadingDashedLine(graphics, stripe.getFirst(), stripe.getLast(), stripeWidth + 0.18D, routeColors.get(i), 7.0D, 5.0D);
        }
    }

    private static void drawFadingDashedLine(GuiGraphicsExtractor graphics, Vec2 a, Vec2 b, double width, int color, double dash, double gap) {
        double length = a.distanceTo(b);
        if (length <= 0.0D) {
            return;
        }
        SmoothGuiPrimitives.dashedPolyline(graphics, List.of(a, b), width, dash, gap, dashIndex -> {
            double progress = Math.max(0.0D, Math.min(1.0D, dashIndex * (dash + gap) / length));
            int alpha = (int) Math.round(0xD8 * Math.pow(1.0D - progress, 1.35D));
            return alpha <= 8 ? 0 : SPSGui.withAlpha(color, alpha);
        });
    }

    private static void drawDashedPolyline(GuiGraphicsExtractor graphics, List<Vec2> points, double width, int color, double dash, double gap) {
        SmoothGuiPrimitives.dashedPolyline(graphics, points, width, color, dash, gap);
    }

    private static void drawPedestrianIcon(GuiGraphicsExtractor graphics, Vec2 center, int size, int color) {
        int headRadius = Math.max(1, size / 5);
        Vec2 head = new Vec2(center.x(), center.y() - size * 0.35D);
        fillCircle(graphics, head, headRadius, color);
        Vec2 neck = new Vec2(center.x(), center.y() - size * 0.12D);
        Vec2 hip = new Vec2(center.x() - size * 0.08D, center.y() + size * 0.16D);
        drawLine(graphics, neck, hip, 1, color);
        drawLine(graphics, new Vec2(center.x() - size * 0.38D, center.y() - size * 0.02D), new Vec2(center.x() + size * 0.28D, center.y() - size * 0.18D), 1, color);
        drawLine(graphics, hip, new Vec2(center.x() - size * 0.42D, center.y() + size * 0.42D), 1, color);
        drawLine(graphics, hip, new Vec2(center.x() + size * 0.36D, center.y() + size * 0.38D), 1, color);
    }

    private static SegmentScreen offset(Vec2 a, Vec2 b, double offset) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double length = Math.max(1.0D, Math.hypot(dx, dy));
        double nx = -dy / length * offset;
        double ny = dx / length * offset;
        return new SegmentScreen(new Vec2(a.x() + nx, a.y() + ny), new Vec2(b.x() + nx, b.y() + ny));
    }

    public static List<Vec2> offsetScreenPath(List<Vec2> points, double offset) {
        if (points.size() < 2 || Math.abs(offset) < 0.001D) {
            return points;
        }
        List<Vec2> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            Vec2 normal;
            if (i == 0) {
                normal = segmentNormal(points.get(0), points.get(1));
            } else if (i == points.size() - 1) {
                normal = segmentNormal(points.get(i - 1), points.get(i));
            } else {
                Vec2 first = segmentNormal(points.get(i - 1), points.get(i));
                Vec2 second = segmentNormal(points.get(i), points.get(i + 1));
                double nx = first.x() + second.x();
                double ny = first.y() + second.y();
                double length = Math.hypot(nx, ny);
                normal = length < 0.001D ? second : new Vec2(nx / length, ny / length);
            }
            result.add(new Vec2(points.get(i).x() + normal.x() * offset, points.get(i).y() + normal.y() * offset));
        }
        return result;
    }

    private static List<Vec2> offsetScreenPathAnchored(List<Vec2> points, double offset) {
        if (points.size() < 2 || Math.abs(offset) < 0.001D) {
            return points;
        }
        double length = polylineLength(points);
        if (length < 18.0D) {
            return points;
        }
        List<Vec2> shifted = offsetScreenPath(points, offset);
        double ramp = Math.max(10.0D, Math.min(24.0D, length * 0.18D));
        List<Vec2> result = new ArrayList<>();
        result.add(points.getFirst());
        if (length <= ramp * 2.0D + 2.0D) {
            result.add(pointAlongPolyline(shifted, length * 0.5D));
        } else {
            result.add(pointAlongPolyline(shifted, ramp));
            for (int i = 1; i + 1 < shifted.size(); i++) {
                double distance = distanceAlongPolyline(shifted, i);
                if (distance > ramp && distance < length - ramp) {
                    result.add(shifted.get(i));
                }
            }
            result.add(pointAlongPolyline(shifted, length - ramp));
        }
        result.add(points.getLast());
        return dedupePath(result);
    }

    private static Vec2 pointAlongPolyline(List<Vec2> points, double distance) {
        if (points.isEmpty()) {
            return new Vec2(0.0D, 0.0D);
        }
        if (points.size() == 1 || distance <= 0.0D) {
            return points.getFirst();
        }
        double walked = 0.0D;
        for (int i = 0; i + 1 < points.size(); i++) {
            Vec2 a = points.get(i);
            Vec2 b = points.get(i + 1);
            double segment = a.distanceTo(b);
            if (walked + segment >= distance) {
                double t = segment < 0.001D ? 0.0D : (distance - walked) / segment;
                return new Vec2(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t);
            }
            walked += segment;
        }
        return points.getLast();
    }

    private static double distanceAlongPolyline(List<Vec2> points, int index) {
        double distance = 0.0D;
        for (int i = 0; i + 1 <= index && i + 1 < points.size(); i++) {
            distance += points.get(i).distanceTo(points.get(i + 1));
        }
        return distance;
    }

    private static List<Vec2> dedupePath(List<Vec2> points) {
        List<Vec2> result = new ArrayList<>();
        for (Vec2 point : points) {
            if (result.isEmpty() || result.getLast().distanceTo(point) > 0.25D) {
                result.add(point);
            }
        }
        return result.size() < 2 ? points : result;
    }

    private static Vec2 segmentNormal(Vec2 a, Vec2 b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double length = Math.hypot(dx, dy);
        if (length < 0.001D) {
            return new Vec2(0.0D, 1.0D);
        }
        return new Vec2(-dy / length, dx / length);
    }

    private static void drawArrow(GuiGraphicsExtractor graphics, Vec2 a, Vec2 b, int color) {
        drawArrow(graphics, a, b, color, 2);
    }

    private static void drawArrow(GuiGraphicsExtractor graphics, Vec2 a, Vec2 b, int color, int width) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double length = Math.hypot(dx, dy);
        if (length < 24.0D) {
            return;
        }
        double ux = dx / length;
        double uy = dy / length;
        Vec2 mid = new Vec2((a.x() + b.x()) * 0.5D, (a.y() + b.y()) * 0.5D);
        Vec2 tip = new Vec2(mid.x() + ux * 6.0D, mid.y() + uy * 6.0D);
        Vec2 left = new Vec2(mid.x() - ux * 5.0D - uy * 4.0D, mid.y() - uy * 5.0D + ux * 4.0D);
        Vec2 right = new Vec2(mid.x() - ux * 5.0D + uy * 4.0D, mid.y() - uy * 5.0D - ux * 4.0D);
        drawLine(graphics, left, tip, width, color);
        drawLine(graphics, right, tip, width, color);
    }

    private static void drawClusterStripes(GuiGraphicsExtractor graphics, Vec2 center, int radius, List<UUID> routeLineIds) {
        if (radius < 7) {
            return;
        }
        List<Integer> allColors = routeLineColorsForIds(routeLineIds);
        int limit = Math.max(1, Math.min(5, radius / 2));
        List<Integer> colors = allColors.subList(0, Math.min(limit, allColors.size()));
        if (colors.isEmpty()) {
            return;
        }
        double gap = Math.max(2.0D, Math.min(3.0D, radius * 0.32D));
        double maxWidth = Math.max(1.0D, radius * 1.35D);
        if ((colors.size() - 1) * gap > maxWidth) {
            gap = maxWidth / Math.max(1, colors.size() - 1);
        }
        double startX = center.x() - (colors.size() - 1) * gap * 0.5D;
        double inner = Math.max(2.0D, radius - 2.0D);
        for (int i = 0; i < colors.size(); i++) {
            double x = startX + i * gap;
            double dx = x - center.x();
            double halfHeight = Math.sqrt(Math.max(1.0D, inner * inner - dx * dx)) * 0.78D;
            drawLine(graphics, new Vec2(x, center.y() - halfHeight), new Vec2(x, center.y() + halfHeight), Math.max(1.4D, Math.min(2.0D, gap * 0.72D)), colors.get(i));
        }
    }

    private static boolean hasStationInternalEdge(MapDimensionGraph graph, NodeId nodeId) {
        return graph.edges().stream().anyMatch(edge -> edge.from().equals(nodeId) && edge.to().equals(nodeId));
    }

    private static void drawStationInternalMarker(GuiGraphicsExtractor graphics, Vec2 center, int radius) {
        Vec2 marker = new Vec2(center.x() + radius * 0.95D, center.y() - radius * 0.95D);
        int outer = Math.max(3, (int) Math.round(radius * 0.42D));
        SmoothGuiPrimitives.circle(graphics, marker, outer + 1.0D, FullRouteMapConfig.MAP_NODE_OUTLINE);
        SmoothGuiPrimitives.circle(graphics, marker, Math.max(1.0D, outer - 1.0D), FullRouteMapConfig.MAP_NODE_FILL);
        drawLine(graphics, new Vec2(marker.x() - outer * 0.45D, marker.y()), new Vec2(marker.x() + outer * 0.25D, marker.y() - outer * 0.32D), 1.0D, FullRouteMapConfig.MAP_NODE_OUTLINE);
        drawLine(graphics, new Vec2(marker.x() + outer * 0.25D, marker.y() - outer * 0.32D), new Vec2(marker.x() + outer * 0.45D, marker.y() + outer * 0.22D), 1.0D, FullRouteMapConfig.MAP_NODE_OUTLINE);
    }

    private static Vec2 screenForNode(MapDimensionGraph graph, VisualRouteMapGraph visualGraph, MapNode node, ViewportState viewport, SPSGui.Rect rect) {
        Vec2 visual = visualPosition(graph, visualGraph, node);
        return worldToScreen(visual.x(), visual.y(), viewport, rect);
    }

    public static Vec2 visualPosition(MapDimensionGraph graph, VisualRouteMapGraph visualGraph, MapNode node) {
        return visualPosition(graph, visualGraph, node.id(), node.worldX(), node.worldZ());
    }

    public static Vec2 visualPosition(MapDimensionGraph graph, VisualRouteMapGraph visualGraph, NodeId nodeId) {
        MapNode raw = graph.node(nodeId).orElse(null);
        return raw == null ? new Vec2(0.0D, 0.0D) : visualPosition(graph, visualGraph, nodeId, raw.worldX(), raw.worldZ());
    }

    private static Vec2 visualPosition(MapDimensionGraph graph, VisualRouteMapGraph visualGraph, NodeId nodeId, double fallbackX, double fallbackZ) {
        if (visualGraph != null) {
            Optional<VisualNode> visualNode = visualGraph.node(nodeId);
            if (visualNode.isPresent()) {
                return new Vec2(visualNode.get().x(), visualNode.get().z());
            }
        }
        return new Vec2(fallbackX, fallbackZ);
    }

    public static Optional<List<Vec2>> visualWorldPathForEdge(MapDimensionGraph graph, VisualRouteMapGraph visualGraph, VisualEdgePath path, MapEdge edge, double zoom) {
        NodeId fromId = displayNodeId(graph, edge.from(), zoom);
        NodeId toId = displayNodeId(graph, edge.to(), zoom);
        if (fromId.equals(toId)) {
            return Optional.empty();
        }
        if (fromId.equals(path.from()) && toId.equals(path.to())) {
            List<Vec2> points = new ArrayList<>(path.points());
            if (points.size() < 2) {
                return Optional.empty();
            }
            points.set(0, visualPosition(graph, visualGraph, fromId));
            points.set(points.size() - 1, visualPosition(graph, visualGraph, toId));
            return Optional.of(points);
        }
        MapNode from = graph.node(fromId).orElse(null);
        MapNode to = graph.node(toId).orElse(null);
        if (from == null || to == null) {
            return Optional.empty();
        }
        Vec2 a = visualPosition(graph, visualGraph, from);
        Vec2 b = visualPosition(graph, visualGraph, to);
        return Optional.of(List.of(a, b));
    }

    private Optional<List<Vec2>> visualScreenPathForEdge(MapDimensionGraph graph, VisualRouteMapGraph visualGraph, VisualEdgePath path, MapEdge edge, ViewportState viewport, SPSGui.Rect rect) {
        return visualWorldPathForEdge(graph, visualGraph, path, edge, viewport.zoom())
                .map(points -> points.stream().map(point -> worldToScreen(point.x(), point.y(), viewport, rect)).toList());
    }

    private static Optional<SegmentScreen> visualTransferSegment(MapDimensionGraph graph, VisualRouteMapGraph visualGraph, MapTransferHint hint, ViewportState viewport, SPSGui.Rect rect) {
        MapNode from = graph.node(displayNodeId(graph, hint.from(), viewport.zoom())).orElse(null);
        MapNode to = graph.node(displayNodeId(graph, hint.to(), viewport.zoom())).orElse(null);
        if (from == null || to == null || from.id().equals(to.id()) || !isVisibleNode(from, viewport.zoom()) || !isVisibleNode(to, viewport.zoom())) {
            return Optional.empty();
        }
        Vec2 fromPos = visualPosition(graph, visualGraph, from);
        Vec2 toPos = visualPosition(graph, visualGraph, to);
        Vec2 a = worldToScreen(fromPos.x(), fromPos.y(), viewport, rect);
        Vec2 b = worldToScreen(toPos.x(), toPos.y(), viewport, rect);
        double length = a.distanceTo(b);
        if (length <= 1.0D) {
            return Optional.empty();
        }
        double shorten = 10.0D * iconScale(viewport.zoom());
        double ux = (b.x() - a.x()) / length;
        double uy = (b.y() - a.y()) / length;
        return Optional.of(new SegmentScreen(new Vec2(a.x() + ux * shorten, a.y() + uy * shorten), new Vec2(b.x() - ux * shorten, b.y() - uy * shorten)));
    }

    private static Aabb2 boundsForPath(List<Vec2> points) {
        Aabb2 bounds = Aabb2.empty();
        for (Vec2 point : points) {
            bounds = bounds.include(point.x(), point.y());
        }
        return bounds;
    }

    private static double distanceToPolyline(Vec2 point, List<Vec2> points) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i + 1 < points.size(); i++) {
            best = Math.min(best, distanceToSegment(point, points.get(i), points.get(i + 1)));
        }
        return best;
    }

    private static double sampledPathDistance(List<Vec2> first, List<Vec2> second) {
        if (first.size() < 2 || second.size() < 2) {
            return Double.POSITIVE_INFINITY;
        }
        double best = Double.POSITIVE_INFINITY;
        for (Vec2 sample : samplePath(first, 10)) {
            best = Math.min(best, distanceToPolyline(sample, second));
        }
        for (Vec2 sample : samplePath(second, 10)) {
            best = Math.min(best, distanceToPolyline(sample, first));
        }
        return best;
    }

    private static List<Vec2> samplePath(List<Vec2> points, int count) {
        double length = polylineLength(points);
        if (length < 0.001D) {
            return List.of();
        }
        List<Vec2> samples = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            samples.add(pointAlongPolyline(points, length * i / (count + 1.0D)));
        }
        return samples;
    }

    private static boolean pathsIntersect(List<Vec2> first, List<Vec2> second) {
        for (int i = 0; i + 1 < first.size(); i++) {
            for (int j = 0; j + 1 < second.size(); j++) {
                if (segmentsIntersect(first.get(i), first.get(i + 1), second.get(j), second.get(j + 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean segmentsIntersect(Vec2 a, Vec2 b, Vec2 c, Vec2 d) {
        double d1 = orientation(a, b, c);
        double d2 = orientation(a, b, d);
        double d3 = orientation(c, d, a);
        double d4 = orientation(c, d, b);
        return d1 * d2 < 0.0D && d3 * d4 < 0.0D;
    }

    private static double orientation(Vec2 a, Vec2 b, Vec2 c) {
        return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
    }

    private static double polylineLength(List<Vec2> points) {
        double length = 0.0D;
        for (int i = 0; i + 1 < points.size(); i++) {
            length += points.get(i).distanceTo(points.get(i + 1));
        }
        return length;
    }

    private static float visualLabelScale(VisualLabel label, MapNode node, double zoom) {
        double base = label.scale() <= 0.0D ? labelScale(node, zoom) : label.scale();
        double zoomAdjustment;
        if (zoom < 0.75D) {
            zoomAdjustment = -0.10D;
        } else if (zoom < 1.2D) {
            zoomAdjustment = -0.04D;
        } else if (zoom < 2.0D) {
            zoomAdjustment = 0.04D;
        } else if (zoom < 3.0D) {
            zoomAdjustment = 0.12D;
        } else {
            zoomAdjustment = 0.20D;
        }
        return (float) Math.max(0.58D, Math.min(1.0D, base + zoomAdjustment));
    }

    private static Vec2 screenForNode(MapDimensionGraph graph, MapNode node, ViewportState viewport, SPSGui.Rect rect) {
        Vec2 base = worldToScreen(node.worldX(), node.worldZ(), viewport, rect);
        if (node.kind() != NodeKind.FOLD_ANCHOR) {
            return base;
        }
        double pushX = 0.0D;
        double pushY = 0.0D;
        for (MapNode other : graph.nodes()) {
            if (other.id().equals(node.id()) || !isVisibleNode(other, viewport.zoom())) {
                continue;
            }
            if (other.kind() != NodeKind.FOLD_ANCHOR && other.kind() != NodeKind.STATION && other.kind() != NodeKind.CLUSTER && other.kind() != NodeKind.DEEP_CLUSTER) {
                continue;
            }
            Vec2 otherBase = worldToScreen(other.worldX(), other.worldZ(), viewport, rect);
            double minDistance = nodeCollisionRadius(node, viewport.zoom()) + nodeCollisionRadius(other, viewport.zoom()) + 8.0D;
            double dx = base.x() - otherBase.x();
            double dy = base.y() - otherBase.y();
            double distance = Math.hypot(dx, dy);
            if (distance >= minDistance) {
                continue;
            }
            double ux;
            double uy;
            if (distance < 0.001D) {
                double angle = hashAngle(node.id(), other.id());
                ux = Math.cos(angle);
                uy = Math.sin(angle);
                distance = 0.001D;
            } else {
                ux = dx / distance;
                uy = dy / distance;
            }
            double strength = (minDistance - distance) / Math.max(1.0D, minDistance);
            pushX += ux * strength * minDistance;
            pushY += uy * strength * minDistance;
        }
        double push = Math.hypot(pushX, pushY);
        if (push < 0.001D) {
            return base;
        }
        double amount = Math.min(FullRouteMapConfig.FOLD_ANCHOR_AVOIDANCE_RADIUS_PX * iconScale(viewport.zoom()), push);
        return new Vec2(base.x() + pushX / push * amount, base.y() + pushY / push * amount);
    }

    private static double hashAngle(NodeId first, NodeId second) {
        int hash = first.hashCode() * 31 + second.hashCode();
        return (hash & 0xFFFF) / 65535.0D * Math.PI * 2.0D;
    }

    private static void fillCircle(GuiGraphicsExtractor graphics, Vec2 center, int radius, int color) {
        SmoothGuiPrimitives.circle(graphics, center, radius, color);
    }

    private static void drawCircle(GuiGraphicsExtractor graphics, Vec2 center, int radius, int fill, int outline) {
        fillCircle(graphics, center, radius + 1, outline);
        fillCircle(graphics, center, radius, fill);
    }

    private static void drawDeepCluster(GuiGraphicsExtractor graphics, Vec2 center, int radius, int fill, int outline) {
        drawCircle(graphics, center, radius, fill, outline);
        int inner = Math.max(3, radius - 4);
        for (int i = 0; i < 16; i += 2) {
            double a1 = i * Math.PI / 8.0D;
            double a2 = (i + 1) * Math.PI / 8.0D;
            drawLine(graphics,
                    new Vec2(center.x() + Math.cos(a1) * inner, center.y() + Math.sin(a1) * inner),
                    new Vec2(center.x() + Math.cos(a2) * inner, center.y() + Math.sin(a2) * inner),
                    1,
                    outline);
        }
        fillCircle(graphics, new Vec2(center.x(), center.y()), 1, outline);
        fillCircle(graphics, new Vec2(center.x() - radius * 0.35D, center.y() + radius * 0.18D), 1, outline);
        fillCircle(graphics, new Vec2(center.x() + radius * 0.35D, center.y() + radius * 0.18D), 1, outline);
    }

    private static void drawCapsule(GuiGraphicsExtractor graphics, Vec2 center, int width, int height, int fill, int outline) {
        fillCapsule(graphics, center, width + 2, height + 2, outline);
        fillCapsule(graphics, center, width, height, fill);
    }

    private static void drawTransferStationGlyph(GuiGraphicsExtractor graphics, Vec2 center, int radius, Optional<Vec2> axis) {
        if (axis.isPresent()) {
            drawDirectedCapsule(graphics, center, axis.get(), radius * 3, radius * 2, FullRouteMapConfig.MAP_NODE_FILL, FullRouteMapConfig.MAP_NODE_OUTLINE);
        } else {
            drawCapsule(graphics, center, radius * 3, radius * 2, FullRouteMapConfig.MAP_NODE_FILL, FullRouteMapConfig.MAP_NODE_OUTLINE);
        }
    }

    private static void drawDirectedCapsule(GuiGraphicsExtractor graphics, Vec2 center, Vec2 axis, int width, int height, int fill, int outline) {
        SmoothGuiPrimitives.capsule(graphics, center, axis, width + 2.0D, height + 2.0D, outline);
        SmoothGuiPrimitives.capsule(graphics, center, axis, width, height, fill);
    }

    private static void fillCapsule(GuiGraphicsExtractor graphics, Vec2 center, int width, int height, int color) {
        SmoothGuiPrimitives.capsule(graphics, center, width, height, color);
    }

    private static void drawDiamond(GuiGraphicsExtractor graphics, Vec2 center, int radius, int fill, int outline, int outlineWidth) {
        SmoothGuiPrimitives.diamond(graphics, center, Math.max(1, radius), fill);
        int width = Math.max(1, outlineWidth);
        drawLine(graphics, new Vec2(center.x(), center.y() - radius), new Vec2(center.x() + radius, center.y()), width, outline);
        drawLine(graphics, new Vec2(center.x() + radius, center.y()), new Vec2(center.x(), center.y() + radius), width, outline);
        drawLine(graphics, new Vec2(center.x(), center.y() + radius), new Vec2(center.x() - radius, center.y()), width, outline);
        drawLine(graphics, new Vec2(center.x() - radius, center.y()), new Vec2(center.x(), center.y() - radius), width, outline);
    }

    private static double distanceToSegment(Vec2 point, Vec2 a, Vec2 b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double lengthSqr = dx * dx + dy * dy;
        if (lengthSqr <= 1.0E-6D) {
            return point.distanceTo(a);
        }
        double t = Math.max(0.0D, Math.min(1.0D, ((point.x() - a.x()) * dx + (point.y() - a.y()) * dy) / lengthSqr));
        return point.distanceTo(new Vec2(a.x() + dx * t, a.y() + dy * t));
    }

    private static int intersectionArea(SPSGui.Rect first, SPSGui.Rect second) {
        int x1 = Math.max(first.x(), second.x());
        int y1 = Math.max(first.y(), second.y());
        int x2 = Math.min(first.right(), second.right());
        int y2 = Math.min(first.bottom(), second.bottom());
        return Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
    }

    private static boolean rectsOverlap(SPSGui.Rect first, SPSGui.Rect second) {
        return first.x() < second.right() && first.right() > second.x() && first.y() < second.bottom() && first.bottom() > second.y();
    }

    private record Segment(MapNode from, MapNode to) {}

    private record SegmentScreen(Vec2 a, Vec2 b) {}

    /** Anchor node screen position and body radius for a low-zoom missing-hint warning badge. */
    private record MissingHintAnchor(Vec2 screen, double nodeRadiusPx) {}

    private record VisibleEdge(MapEdge edge, Vec2 a, Vec2 b) {}

    private record PhysicalRouteLane(UUID routeLineId, Optional<RouteLine> line, PhysicalMapEdge representative, Set<String> edgeIds, boolean fallback) {
        private PhysicalRouteLane {
            line = line == null ? Optional.empty() : line;
            edgeIds = Set.copyOf(edgeIds);
        }
    }

    private record PhysicalTrunkCandidate(String groupKey, String lineKey, Vec2 start, Vec2 end, double score) {}

    private record PhysicalEdgeGroupIndex(List<PhysicalMapEdge> sourceEdges, long routeRevision, long pipeRevision, Map<String, List<PhysicalMapEdge>> groupsByKey, Map<String, String> keyByEdgeId, Map<String, List<PhysicalMapEdge>> groupByEdgeId) {}

    private record FoldLineIndex(List<MapEdge> sourceEdges, long routeRevision, long pipeRevision, Map<NodeId, Set<UUID>> lineIdsByNode) {}

    private record EdgeLane(List<Integer> colors) {
        private EdgeLane {
            colors = List.copyOf(colors);
        }
    }

    private record IconBlocker(NodeId nodeId, SPSGui.Rect bounds) {}

    private record LabelCandidate(MapNode node, DisplayNameStack label, List<SPSGui.Rect> bounds, int priority, float scale) {}
}
