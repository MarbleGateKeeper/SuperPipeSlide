package dev.marblegate.superpipeslide.client.fullmap.cluster.render;

import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.fullmap.cluster.hit.ClusterCardHit;
import dev.marblegate.superpipeslide.client.fullmap.cluster.hit.ClusterCardHitKind;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardEdge;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardEdgeKind;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardNode;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardNodeKind;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardProfile;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardSemanticGraph;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardViewport;
import dev.marblegate.superpipeslide.client.fullmap.cluster.visual.ClusterCardVisualEdge;
import dev.marblegate.superpipeslide.client.fullmap.cluster.visual.ClusterCardVisualGraph;
import dev.marblegate.superpipeslide.client.fullmap.cluster.visual.ClusterCardVisualNode;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapLayoutMode;
import dev.marblegate.superpipeslide.client.fullmap.model.MapNode;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.render.FullRouteMapRenderer;
import dev.marblegate.superpipeslide.client.fullmap.render.SmoothGuiPrimitives;
import dev.marblegate.superpipeslide.client.fullmap.ui.DisplayNameStack;
import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapText;
import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapTheme;
import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapTooltipCard;
import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapUi;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class ClusterCardRenderer {
    public ClusterCardRenderResult render(
            GuiGraphicsExtractor graphics,
            Font font,
            ClusterCardSemanticGraph semanticGraph,
            ClusterCardVisualGraph visualGraph,
            ClusterCardViewport viewport,
            SPSGui.Rect map,
            boolean active,
            int mouseX,
            int mouseY) {
        SPSGui.Rect fitViewport = new SPSGui.Rect(map.right() - 20, map.bottom() - 20, FullMapTheme.ICON_BUTTON_SMALL, FullMapTheme.ICON_BUTTON_SMALL);
        boolean overControl = active && fitViewport.contains(mouseX, mouseY);
        ClusterCardProfile profile = semanticGraph.profile();
        ClusterCardVisualGraph screenGraph = screenGraph(visualGraph, viewport, map);
        ClusterCardHit hover = active && !overControl && map.contains(mouseX, mouseY)
                ? this.hitTest(screenGraph, map, mouseX, mouseY, viewport.zoom(), profile).orElse(ClusterCardHit.none())
                : ClusterCardHit.none();

        FullRouteMapRenderer.drawMapBackground(graphics, map, viewport.centerX(), viewport.centerY(), scale(viewport), FullRouteMapLayoutMode.PRACTICAL);
        graphics.outline(map.x(), map.y(), map.width(), map.height(), FullMapTheme.BORDER);
        graphics.enableScissor(map.x(), map.y(), map.right(), map.bottom());
        this.drawEdges(graphics, screenGraph, hover, viewport.zoom());
        this.drawNodes(graphics, screenGraph, hover, viewport.zoom(), profile);
        this.drawLabels(graphics, font, screenGraph, hover, viewport.zoom(), map, profile);
        graphics.disableScissor();
        this.drawViewportHints(graphics, map, visualGraph.bounds(), viewport);
        if (active) {
            this.drawFitViewportButton(graphics, fitViewport, fitViewport.contains(mouseX, mouseY));
        }
        if (semanticGraph.nodes().isEmpty()) {
            SPSGui.centeredText(graphics, font, Component.translatable("screen.superpipeslide.full_map.route_card.map_empty"), map.x() + map.width() / 2, map.y() + map.height() / 2 - 4, FullMapTheme.TEXT_MUTED);
        }
        return new ClusterCardRenderResult(map, fitViewport, hover);
    }

    public static ClusterCardViewport fitViewport(Aabb2 bounds, SPSGui.Rect map) {
        if (bounds.isEmpty()) {
            return new ClusterCardViewport(0.0D, 0.0D, FullRouteMapConfig.DEFAULT_ZOOM);
        }
        double paddedWidth = Math.max(1.0D, bounds.maxX() - bounds.minX() + 72.0D);
        double paddedHeight = Math.max(1.0D, bounds.maxY() - bounds.minY() + 72.0D);
        double fitScale = Math.min(Math.max(1.0D, map.width()) / paddedWidth, Math.max(1.0D, map.height()) / paddedHeight);
        double zoom = fitScale / FullRouteMapConfig.BASE_SCALE;
        return new ClusterCardViewport(bounds.centerX(), bounds.centerY(), zoom * 0.96D);
    }

    public static Vec2 screenToWorld(double screenX, double screenY, ClusterCardViewport viewport, SPSGui.Rect map) {
        double s = scale(viewport);
        return new Vec2(
                viewport.centerX() + (screenX - (map.x() + map.width() * 0.5D)) / s,
                viewport.centerY() + (screenY - (map.y() + map.height() * 0.5D)) / s);
    }

    public static double scale(ClusterCardViewport viewport) {
        return viewport.zoom() * FullRouteMapConfig.BASE_SCALE;
    }

    private static ClusterCardVisualGraph screenGraph(ClusterCardVisualGraph graph, ClusterCardViewport viewport, SPSGui.Rect map) {
        List<ClusterCardVisualNode> nodes = graph.nodes().stream()
                .map(node -> new ClusterCardVisualNode(node.node(), worldToScreen(node.position(), viewport, map), node.priority()))
                .toList();
        List<ClusterCardVisualEdge> edges = graph.edges().stream()
                .map(edge -> {
                    List<Vec2> points = edge.points().stream().map(point -> worldToScreen(point, viewport, map)).toList();
                    return new ClusterCardVisualEdge(edge.edge(), points, boundsFor(points).inflate(8.0D));
                })
                .toList();
        return new ClusterCardVisualGraph(nodes, edges, screenBounds(graph.bounds(), viewport, map), graph.fallback());
    }

    private static Vec2 worldToScreen(Vec2 point, ClusterCardViewport viewport, SPSGui.Rect map) {
        double s = scale(viewport);
        return new Vec2(
                map.x() + map.width() * 0.5D + (point.x() - viewport.centerX()) * s,
                map.y() + map.height() * 0.5D + (point.y() - viewport.centerY()) * s);
    }

    private static Aabb2 screenBounds(Aabb2 bounds, ClusterCardViewport viewport, SPSGui.Rect map) {
        if (bounds.isEmpty()) {
            return bounds;
        }
        Vec2 a = worldToScreen(new Vec2(bounds.minX(), bounds.minY()), viewport, map);
        Vec2 b = worldToScreen(new Vec2(bounds.maxX(), bounds.minY()), viewport, map);
        Vec2 c = worldToScreen(new Vec2(bounds.maxX(), bounds.maxY()), viewport, map);
        Vec2 d = worldToScreen(new Vec2(bounds.minX(), bounds.maxY()), viewport, map);
        return Aabb2.empty()
                .include(a.x(), a.y())
                .include(b.x(), b.y())
                .include(c.x(), c.y())
                .include(d.x(), d.y());
    }

    private void drawEdges(GuiGraphicsExtractor graphics, ClusterCardVisualGraph graph, ClusterCardHit hover, double zoom) {
        Set<String> highlightedFoldNodes = highlightedFoldNodes(graph, hover);
        for (ClusterCardVisualEdge visualEdge : graph.edges()) {
            ClusterCardEdge edge = visualEdge.edge();
            if (visualEdge.points().size() < 2) {
                continue;
            }
            if (edge.kind() == ClusterCardEdgeKind.FOLD_PEER_LINK) {
                if (highlightedFoldNodes.contains(edge.from()) || highlightedFoldNodes.contains(edge.to())) {
                    drawPolyline(graphics, visualEdge.points(), 7.0D, FullRouteMapConfig.MAP_FOCUS_HALO);
                    drawPolyline(graphics, visualEdge.points(), 1.7D, FullRouteMapConfig.MAP_FOLD_MULTI_LINE);
                }
                continue;
            }
            List<RouteLine> lines = routeLinesForIds(edge.routeLineIds());
            boolean hovered = hover.edgeId().filter(edge.id()::equals).isPresent()
                    || hover.nodeId().filter(nodeId -> edge.from().equals(nodeId) || edge.to().equals(nodeId)).isPresent();
            double width = edge.kind() == ClusterCardEdgeKind.EXTERNAL_ROUTE
                    ? Math.max(2.0D, FullRouteMapConfig.LINE_WIDTH_PX - 0.4D)
                    : FullRouteMapConfig.LINE_WIDTH_PX;
            if (lines.size() >= FullRouteMapConfig.TRUNK_THRESHOLD) {
                if (hovered) {
                    drawPolyline(graphics, visualEdge.points(), 9.0D, FullRouteMapConfig.MAP_FOCUS_HALO);
                }
                drawPolyline(graphics, visualEdge.points(), 5.0D, FullRouteMapConfig.MAP_TRUNK);
                if (zoom >= FullRouteMapConfig.TRUNK_DOT_MIN_ZOOM) {
                    this.drawTrunkDots(graphics, visualEdge.points(), lines);
                }
            } else {
                this.drawRouteBundle(graphics, visualEdge.points(), edgeLanes(lines), width, hovered);
            }
        }
    }

    private void drawNodes(GuiGraphicsExtractor graphics, ClusterCardVisualGraph graph, ClusterCardHit hover, double zoom, ClusterCardProfile profile) {
        Set<String> highlightedFoldNodes = highlightedFoldNodes(graph, hover);
        for (ClusterCardVisualNode visualNode : graph.nodes().stream().sorted(Comparator.comparingInt(ClusterCardVisualNode::priority)).toList()) {
            ClusterCardNode node = visualNode.node();
            Vec2 center = visualNode.position();
            double radius = nodeRadius(node, zoom, profile);
            boolean hovered = hover.nodeId().filter(node.id()::equals).isPresent() || highlightedFoldNodes.contains(node.id());
            if (hovered) {
                drawNodeFocus(graphics, node, center, radius);
            }
            switch (node.kind()) {
                case MEMBER_DEEP_CLUSTER -> {
                    drawCircle(graphics, center, radius, FullRouteMapConfig.MAP_CLUSTER_FILL, FullRouteMapConfig.MAP_CLUSTER_OUTLINE);
                    drawClusterStripes(graphics, center, radius, node.routeLineIds());
                    double inner = Math.max(3.0D, radius - 4.0D);
                    for (int i = 0; i < 16; i += 2) {
                        double a1 = i * Math.PI / 8.0D;
                        double a2 = (i + 1) * Math.PI / 8.0D;
                        drawLine(graphics,
                                new Vec2(center.x() + Math.cos(a1) * inner, center.y() + Math.sin(a1) * inner),
                                new Vec2(center.x() + Math.cos(a2) * inner, center.y() + Math.sin(a2) * inner),
                                1.0D,
                                FullRouteMapConfig.MAP_CLUSTER_OUTLINE);
                    }
                }
                case MEMBER_FOLD_ANCHOR -> drawDiamond(graphics, center, radius, FullRouteMapConfig.MAP_FOLD_FILL, FullRouteMapConfig.MAP_FOLD_MULTI_LINE);
                case MEMBER_STATION -> {
                    if (node.mapNode().filter(MapNode::isTransferStation).isPresent()) {
                        SmoothGuiPrimitives.capsule(graphics, center, radius * 3.15D + 2.0D, radius * 2.0D + 2.0D, FullRouteMapConfig.MAP_CARD_NODE_OUTLINE);
                        SmoothGuiPrimitives.capsule(graphics, center, radius * 3.15D, radius * 2.0D, FullRouteMapConfig.MAP_NODE_FILL);
                    } else {
                        drawCircle(graphics, center, radius, FullRouteMapConfig.MAP_NODE_FILL, FullRouteMapConfig.MAP_CARD_NODE_OUTLINE);
                    }
                    if (node.stationInternalLoop()) {
                        drawStationInternalMarker(graphics, center, radius);
                    }
                }
                case EXTERNAL_PORT -> {
                    drawCircle(graphics, center, radius, 0xFFFFFFFF, FullRouteMapConfig.MAP_LABEL_MUTED);
                    drawClusterStripes(graphics, center, Math.max(5.0D, radius - 1.0D), node.routeLineIds());
                }
            }
        }
    }

    private void drawLabels(GuiGraphicsExtractor graphics, Font font, ClusterCardVisualGraph graph, ClusterCardHit hover, double zoom, SPSGui.Rect map, ClusterCardProfile profile) {
        if (zoom < (profile == ClusterCardProfile.DEEP ? 0.5D : 0.42D)) {
            return;
        }
        List<LabelBlocker> blockers = new ArrayList<>();
        for (ClusterCardVisualNode visualNode : graph.nodes()) {
            blockers.add(new LabelBlocker(visualNode.node().id(), iconBounds(visualNode.node(), visualNode.position(), zoom, profile)));
        }
        List<SPSGui.Rect> placed = new ArrayList<>();
        int rendered = 0;
        int maxLabels = profile == ClusterCardProfile.DEEP
                ? Math.min(24, FullRouteMapConfig.MAX_LABELS_PER_FRAME)
                : FullRouteMapConfig.MAX_LABELS_PER_FRAME;
        for (ClusterCardVisualNode visualNode : graph.nodes().stream().sorted(Comparator.comparingInt(ClusterCardVisualNode::priority).reversed()).toList()) {
            if (rendered >= maxLabels) {
                break;
            }
            ClusterCardNode node = visualNode.node();
            boolean hovered = hover.nodeId().filter(node.id()::equals).isPresent();
            if (node.kind() == ClusterCardNodeKind.MEMBER_FOLD_ANCHOR && !hovered && zoom < (profile == ClusterCardProfile.DEEP ? 1.08D : 1.0D)) {
                continue;
            }
            if (profile == ClusterCardProfile.DEEP && !hovered) {
                if (node.kind() == ClusterCardNodeKind.EXTERNAL_PORT && zoom < 0.85D) {
                    continue;
                }
                if (node.kind() == ClusterCardNodeKind.MEMBER_STATION && zoom < 0.78D) {
                    continue;
                }
                if (node.kind() == ClusterCardNodeKind.MEMBER_STATION && !node.mapNode().filter(MapNode::isTransferStation).isPresent() && zoom < 1.02D) {
                    continue;
                }
            }
            if (node.kind() == ClusterCardNodeKind.EXTERNAL_PORT && !hovered && zoom < 0.62D) {
                continue;
            }
            DisplayNameStack name = displayNameStack(node, zoom);
            if (node.kind() == ClusterCardNodeKind.EXTERNAL_PORT) {
                name = new DisplayNameStack("\u2192 " + name.primary(), name.secondary(), name.aliases());
            }
            float scale = node.kind() == ClusterCardNodeKind.EXTERNAL_PORT ? 0.58F : (float) Math.max(0.58D, Math.min(0.78D, 0.58D + zoom * 0.08D));
            float secondaryScale = Math.max(0.46F, scale * 0.78F);
            int width = Math.max(1, FullMapUi.nameStackWidth(font, name, scale, secondaryScale));
            int height = Math.max(7, FullMapUi.nameStackHeight(name, scale, secondaryScale, 0));
            Optional<SPSGui.Rect> selected = chooseLabelBounds(node, visualNode.position(), zoom, map, width, height, placed, blockers, profile);
            if (selected.isEmpty()) {
                continue;
            }
            SPSGui.Rect rect = selected.get();
            placed.add(rect);
            FullMapUi.drawNameStack(graphics, font, name, rect.x(), rect.y(), rect.width(), FullRouteMapConfig.MAP_CARD_LABEL, FullMapTheme.TEXT_MUTED, scale, secondaryScale, 0);
            rendered++;
        }
    }

    private Optional<ClusterCardHit> hitTest(ClusterCardVisualGraph graph, SPSGui.Rect map, double mouseX, double mouseY, double zoom, ClusterCardProfile profile) {
        if (!map.contains(mouseX, mouseY)) {
            return Optional.empty();
        }
        for (ClusterCardVisualNode visualNode : graph.nodes().stream().sorted(Comparator.comparingInt(ClusterCardVisualNode::priority).reversed()).toList()) {
            ClusterCardNode node = visualNode.node();
            if (nodeHitScore(node, visualNode.position(), mouseX, mouseY, zoom, profile) <= 1.0D) {
                return Optional.of(ClusterCardHit.node(node.id()));
            }
        }
        ClusterCardVisualEdge best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (ClusterCardVisualEdge visualEdge : graph.edges()) {
            if (visualEdge.edge().kind() == ClusterCardEdgeKind.FOLD_PEER_LINK || visualEdge.points().size() < 2) {
                continue;
            }
            double distance = distanceToPolyline(new Vec2(mouseX, mouseY), visualEdge.points());
            double threshold = Math.max(6.0D, routeBundleWidth(edgeLanes(routeLinesForIds(visualEdge.edge().routeLineIds())), FullRouteMapConfig.LINE_WIDTH_PX) * 0.5D + 5.0D);
            if (distance < bestDistance && distance <= threshold) {
                bestDistance = distance;
                best = visualEdge;
            }
        }
        return best == null ? Optional.empty() : Optional.of(ClusterCardHit.edge(best.edge().id()));
    }

    public void renderHoverTooltip(
            GuiGraphicsExtractor graphics,
            Font font,
            ClusterCardSemanticGraph graph,
            ClusterCardHit hover,
            SPSGui.Rect boundary,
            List<SPSGui.Rect> avoidRects,
            int mouseX,
            int mouseY) {
        if (hover.kind() == ClusterCardHitKind.NODE && hover.nodeId().isPresent()) {
            ClusterCardNode node = graph.node(hover.nodeId().get()).orElse(null);
            if (node == null) {
                return;
            }
            FullMapTooltipCard.render(
                    graphics,
                    font,
                    boundary,
                    avoidRects,
                    mouseX,
                    mouseY,
                    clusterNodeTitle(node),
                    clusterNodeSubtitle(node),
                    clusterNodeRows(node),
                    routeChips(node.routeLineIds()),
                    clusterNodeAccent(node));
            return;
        }
        if (hover.kind() == ClusterCardHitKind.EDGE && hover.edgeId().isPresent()) {
            graph.edge(hover.edgeId().get())
                    .ifPresent(edge -> FullMapTooltipCard.render(
                            graphics,
                            font,
                            boundary,
                            avoidRects,
                            mouseX,
                            mouseY,
                            edgeTitleStack(edge.routeLineIds()),
                            Component.translatable("screen.superpipeslide.full_map.tooltip_card.edge_subtitle", edge.routeLineIds().size()).getString(),
                            List.of(tooltipRow("screen.superpipeslide.full_map.tooltip_field.routes", Integer.toString(edge.routeLineIds().size()))),
                            routeChips(edge.routeLineIds()),
                            primaryRouteColor(edge.routeLineIds())));
        }
    }

    private void drawViewportHints(GuiGraphicsExtractor graphics, SPSGui.Rect map, Aabb2 worldBounds, ClusterCardViewport viewport) {
        if (worldBounds.isEmpty()) {
            return;
        }
        Aabb2 screenBounds = screenBounds(worldBounds.inflate(24.0D), viewport, map);
        boolean left = screenBounds.minX() < map.x();
        boolean right = screenBounds.maxX() > map.right();
        boolean up = screenBounds.minY() < map.y();
        boolean down = screenBounds.maxY() > map.bottom();
        int fade = 0x44EEF2F7;
        if (left) {
            graphics.fill(map.x(), map.y(), map.x() + 8, map.bottom(), fade);
            drawChevron(graphics, map.x() + 4, map.y() + map.height() * 0.5D, -1, 0);
        }
        if (right) {
            graphics.fill(map.right() - 8, map.y(), map.right(), map.bottom(), fade);
            drawChevron(graphics, map.right() - 4, map.y() + map.height() * 0.5D, 1, 0);
        }
        if (up) {
            graphics.fill(map.x(), map.y(), map.right(), map.y() + 8, fade);
            drawChevron(graphics, map.x() + map.width() * 0.5D, map.y() + 4, 0, -1);
        }
        if (down) {
            graphics.fill(map.x(), map.bottom() - 8, map.right(), map.bottom(), fade);
            drawChevron(graphics, map.x() + map.width() * 0.5D, map.bottom() - 4, 0, 1);
        }
    }

    private void drawFitViewportButton(GuiGraphicsExtractor graphics, SPSGui.Rect rect, boolean hovered) {
        FullMapUi.iconButton(graphics, rect, hovered, false, false, SPSGui.Icon.FIT);
    }

    private static void drawChevron(GuiGraphicsExtractor graphics, double x, double y, int dx, int dy) {
        int color = 0xAA4B5563;
        if (dx < 0) {
            SmoothGuiPrimitives.line(graphics, new Vec2(x + 3.0D, y - 5.0D), new Vec2(x - 2.0D, y), 1.25D, color);
            SmoothGuiPrimitives.line(graphics, new Vec2(x - 2.0D, y), new Vec2(x + 3.0D, y + 5.0D), 1.25D, color);
        } else if (dx > 0) {
            SmoothGuiPrimitives.line(graphics, new Vec2(x - 3.0D, y - 5.0D), new Vec2(x + 2.0D, y), 1.25D, color);
            SmoothGuiPrimitives.line(graphics, new Vec2(x + 2.0D, y), new Vec2(x - 3.0D, y + 5.0D), 1.25D, color);
        } else if (dy < 0) {
            SmoothGuiPrimitives.line(graphics, new Vec2(x - 5.0D, y + 3.0D), new Vec2(x, y - 2.0D), 1.25D, color);
            SmoothGuiPrimitives.line(graphics, new Vec2(x, y - 2.0D), new Vec2(x + 5.0D, y + 3.0D), 1.25D, color);
        } else if (dy > 0) {
            SmoothGuiPrimitives.line(graphics, new Vec2(x - 5.0D, y - 3.0D), new Vec2(x, y + 2.0D), 1.25D, color);
            SmoothGuiPrimitives.line(graphics, new Vec2(x, y + 2.0D), new Vec2(x + 5.0D, y - 3.0D), 1.25D, color);
        }
    }

    private static Set<String> highlightedFoldNodes(ClusterCardVisualGraph graph, ClusterCardHit hover) {
        Set<String> result = new LinkedHashSet<>();
        if (hover.nodeId().isEmpty()) {
            return result;
        }
        String hoveredNodeId = hover.nodeId().get();
        ClusterCardNode hovered = graph.node(hoveredNodeId).map(ClusterCardVisualNode::node).orElse(null);
        if (hovered == null || hovered.kind() != ClusterCardNodeKind.MEMBER_FOLD_ANCHOR) {
            return result;
        }
        result.add(hoveredNodeId);
        for (ClusterCardVisualEdge edge : graph.edges()) {
            if (edge.edge().kind() != ClusterCardEdgeKind.FOLD_PEER_LINK) {
                continue;
            }
            if (edge.edge().from().equals(hoveredNodeId)) {
                result.add(edge.edge().to());
            }
            if (edge.edge().to().equals(hoveredNodeId)) {
                result.add(edge.edge().from());
            }
        }
        return result;
    }

    private Optional<SPSGui.Rect> chooseLabelBounds(ClusterCardNode node, Vec2 center, double zoom, SPSGui.Rect map, int width, int height, List<SPSGui.Rect> placed, List<LabelBlocker> blockers, ClusterCardProfile profile) {
        double radius = nodeRadius(node, zoom, profile);
        List<SPSGui.Rect> candidates = new ArrayList<>();
        addLabelCandidate(candidates, center.x() + radius + 6.0D, center.y() - height * 0.5D, width, height, map);
        addLabelCandidate(candidates, center.x() - radius - 6.0D - width, center.y() - height * 0.5D, width, height, map);
        addLabelCandidate(candidates, center.x() - width * 0.5D, center.y() - radius - height - 5.0D, width, height, map);
        addLabelCandidate(candidates, center.x() - width * 0.5D, center.y() + radius + 5.0D, width, height, map);
        SPSGui.Rect best = null;
        int bestPenalty = Integer.MAX_VALUE;
        for (SPSGui.Rect candidate : candidates) {
            int penalty = 0;
            for (LabelBlocker blocker : blockers) {
                if (!blocker.nodeId().equals(node.id()) && rectsOverlap(candidate, blocker.bounds())) {
                    penalty += 110;
                }
            }
            for (SPSGui.Rect placedLabel : placed) {
                if (rectsOverlap(candidate, placedLabel)) {
                    penalty += 45;
                }
            }
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private static void addLabelCandidate(List<SPSGui.Rect> candidates, double x, double y, int width, int height, SPSGui.Rect map) {
        int padding = 3;
        int clampedX = Math.max(map.x() + padding, Math.min((int) Math.round(x), map.right() - padding - width));
        int clampedY = Math.max(map.y() + padding, Math.min((int) Math.round(y), map.bottom() - padding - height));
        candidates.add(new SPSGui.Rect(clampedX, clampedY, width, height));
    }

    private static SPSGui.Rect iconBounds(ClusterCardNode node, Vec2 position, double zoom, ClusterCardProfile profile) {
        double radius = nodeRadius(node, zoom, profile) + 4.0D;
        if (node.kind() == ClusterCardNodeKind.MEMBER_STATION && node.mapNode().filter(MapNode::isTransferStation).isPresent()) {
            return new SPSGui.Rect((int) Math.floor(position.x() - radius * 1.75D), (int) Math.floor(position.y() - radius), (int) Math.ceil(radius * 3.5D), (int) Math.ceil(radius * 2.0D));
        }
        return new SPSGui.Rect((int) Math.floor(position.x() - radius), (int) Math.floor(position.y() - radius), (int) Math.ceil(radius * 2.0D), (int) Math.ceil(radius * 2.0D));
    }

    private static double nodeHitScore(ClusterCardNode node, Vec2 position, double mouseX, double mouseY, double zoom, ClusterCardProfile profile) {
        double radius = nodeRadius(node, zoom, profile) + 4.0D;
        double dx = Math.abs(mouseX - position.x());
        double dy = Math.abs(mouseY - position.y());
        if (node.kind() == ClusterCardNodeKind.MEMBER_FOLD_ANCHOR) {
            return (dx + dy) / Math.max(1.0D, radius);
        }
        if (node.kind() == ClusterCardNodeKind.MEMBER_STATION && node.mapNode().filter(MapNode::isTransferStation).isPresent()) {
            return Math.max(dx / Math.max(1.0D, radius * 1.75D), dy / Math.max(1.0D, radius));
        }
        return Math.hypot(dx, dy) / Math.max(1.0D, radius);
    }

    private static double nodeRadius(ClusterCardNode node, double zoom, ClusterCardProfile profile) {
        double base = switch (node.kind()) {
            case MEMBER_DEEP_CLUSTER -> FullRouteMapConfig.CLUSTER_RADIUS_PX;
            case MEMBER_STATION, MEMBER_FOLD_ANCHOR -> FullRouteMapConfig.NODE_RADIUS_PX;
            case EXTERNAL_PORT -> Math.max(4.2D, FullRouteMapConfig.NODE_RADIUS_PX - 1.5D);
        };
        double scale = Math.max(0.72D, Math.min(1.25D, 0.78D + zoom * 0.12D));
        if (profile == ClusterCardProfile.DEEP) {
            scale *= 0.88D;
        }
        return base * scale;
    }

    private void drawRouteBundle(GuiGraphicsExtractor graphics, List<Vec2> points, List<EdgeLane> lanes, double width, boolean hovered) {
        if (lanes.isEmpty() || points.size() < 2) {
            return;
        }
        if (hovered) {
            drawPolyline(graphics, points, routeBundleWidth(lanes, width) + 5.0D, FullRouteMapConfig.MAP_FOCUS_HALO);
        }
        double step = width + 1.0D;
        double center = (lanes.size() - 1) * 0.5D;
        for (int i = 0; i < lanes.size(); i++) {
            List<Vec2> lanePath = offsetPolyline(points, (i - center) * step);
            drawColorLanePath(graphics, lanePath, width, lanes.get(i).colors());
        }
    }

    private static void drawColorLanePath(GuiGraphicsExtractor graphics, List<Vec2> points, double totalWidth, List<Integer> colors) {
        if (points.size() < 2) {
            return;
        }
        if (colors.isEmpty()) {
            drawPolyline(graphics, points, totalWidth, FullRouteMapConfig.MAP_TRUNK);
            return;
        }
        if (colors.size() == 1) {
            drawPolyline(graphics, points, totalWidth, colors.getFirst());
            return;
        }
        double stripeWidth = totalWidth / colors.size();
        double center = (colors.size() - 1) * 0.5D;
        for (int i = 0; i < colors.size(); i++) {
            drawPolyline(graphics, offsetPolyline(points, (i - center) * stripeWidth), stripeWidth + 0.18D, colors.get(i));
        }
    }

    private void drawTrunkDots(GuiGraphicsExtractor graphics, List<Vec2> path, List<RouteLine> lines) {
        if (path.size() < 2 || lines.isEmpty()) {
            return;
        }
        double half = polylineLength(path) * 0.5D;
        double walked = 0.0D;
        Vec2 a = path.getFirst();
        Vec2 b = path.get(1);
        for (int i = 0; i + 1 < path.size(); i++) {
            double length = path.get(i).distanceTo(path.get(i + 1));
            if (walked + length >= half) {
                a = path.get(i);
                b = path.get(i + 1);
                break;
            }
            walked += length;
        }
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double length = Math.max(1.0D, Math.hypot(dx, dy));
        double nx = -dy / length;
        double ny = dx / length;
        Vec2 mid = new Vec2((a.x() + b.x()) * 0.5D, (a.y() + b.y()) * 0.5D);
        int count = Math.min(lines.size(), 6);
        double start = -(count - 1) * 2.0D;
        for (int i = 0; i < count; i++) {
            SmoothGuiPrimitives.circle(graphics, new Vec2(mid.x() + nx * (start + i * 4.0D), mid.y() + ny * (start + i * 4.0D)), 2.0D, SPSGui.opaque(lines.get(i).themeColors().getFirst()));
        }
    }

    private static void drawNodeFocus(GuiGraphicsExtractor graphics, ClusterCardNode node, Vec2 center, double radius) {
        if (node.kind() == ClusterCardNodeKind.MEMBER_FOLD_ANCHOR) {
            SmoothGuiPrimitives.diamond(graphics, center, radius + 5.0D, FullRouteMapConfig.MAP_FOCUS_HALO);
            SmoothGuiPrimitives.diamond(graphics, center, radius + 2.0D, FullRouteMapConfig.MAP_FOCUS_RING);
            return;
        }
        if (node.kind() == ClusterCardNodeKind.MEMBER_STATION && node.mapNode().filter(MapNode::isTransferStation).isPresent()) {
            SmoothGuiPrimitives.capsule(graphics, center, radius * 3.15D + 10.0D, radius * 2.0D + 10.0D, FullRouteMapConfig.MAP_FOCUS_HALO);
            SmoothGuiPrimitives.capsule(graphics, center, radius * 3.15D + 4.0D, radius * 2.0D + 4.0D, FullRouteMapConfig.MAP_FOCUS_RING);
            return;
        }
        SmoothGuiPrimitives.circle(graphics, center, radius + 5.0D, FullRouteMapConfig.MAP_FOCUS_HALO);
        SmoothGuiPrimitives.circle(graphics, center, radius + 2.0D, FullRouteMapConfig.MAP_FOCUS_RING);
    }

    private static void drawStationInternalMarker(GuiGraphicsExtractor graphics, Vec2 center, double radius) {
        Vec2 marker = new Vec2(center.x() + radius * 0.95D, center.y() - radius * 0.95D);
        double outer = Math.max(3.0D, radius * 0.42D);
        SmoothGuiPrimitives.circle(graphics, marker, outer + 1.0D, FullRouteMapConfig.MAP_CARD_NODE_OUTLINE);
        SmoothGuiPrimitives.circle(graphics, marker, Math.max(1.0D, outer - 1.0D), FullRouteMapConfig.MAP_NODE_FILL);
        drawLine(graphics, new Vec2(marker.x() - outer * 0.45D, marker.y()), new Vec2(marker.x() + outer * 0.25D, marker.y() - outer * 0.32D), 1.0D, FullRouteMapConfig.MAP_CARD_NODE_OUTLINE);
        drawLine(graphics, new Vec2(marker.x() + outer * 0.25D, marker.y() - outer * 0.32D), new Vec2(marker.x() + outer * 0.45D, marker.y() + outer * 0.22D), 1.0D, FullRouteMapConfig.MAP_CARD_NODE_OUTLINE);
    }

    private static void drawClusterStripes(GuiGraphicsExtractor graphics, Vec2 center, double radius, List<UUID> routeLineIds) {
        if (radius < 5.0D) {
            return;
        }
        List<Integer> colors = routeLineIds.stream()
                .map(ClientRouteDataCache::routeLine)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing((RouteLine line) -> FullMapText.displayName(line)).thenComparing(RouteLine::id))
                .flatMap(line -> routeLineColors(line).stream())
                .limit(Math.max(1, Math.min(5, (int) Math.round(radius / 2.0D))))
                .toList();
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

    private static void drawCircle(GuiGraphicsExtractor graphics, Vec2 center, double radius, int fill, int outline) {
        SmoothGuiPrimitives.circle(graphics, center, radius + 1.0D, outline);
        SmoothGuiPrimitives.circle(graphics, center, radius, fill);
    }

    private static void drawDiamond(GuiGraphicsExtractor graphics, Vec2 center, double radius, int fill, int outline) {
        SmoothGuiPrimitives.diamond(graphics, center, radius + 1.0D, outline);
        SmoothGuiPrimitives.diamond(graphics, center, Math.max(1.0D, radius - 1.0D), fill);
    }

    private static void drawPolyline(GuiGraphicsExtractor graphics, List<Vec2> points, double width, int color) {
        SmoothGuiPrimitives.polyline(graphics, points, width, color);
    }

    private static void drawLine(GuiGraphicsExtractor graphics, Vec2 a, Vec2 b, double width, int color) {
        SmoothGuiPrimitives.line(graphics, a, b, width, color);
    }

    private static List<Vec2> offsetPolyline(List<Vec2> points, double offset) {
        if (Math.abs(offset) < 0.001D || points.size() < 2) {
            return points;
        }
        List<Vec2> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            Vec2 normal;
            if (i == 0) {
                normal = segmentNormal(points.get(0), points.get(1));
            } else if (i + 1 == points.size()) {
                normal = segmentNormal(points.get(i - 1), points.get(i));
            } else {
                Vec2 a = segmentNormal(points.get(i - 1), points.get(i));
                Vec2 b = segmentNormal(points.get(i), points.get(i + 1));
                normal = normalized(new Vec2(a.x() + b.x(), a.y() + b.y()));
            }
            result.add(new Vec2(points.get(i).x() + normal.x() * offset, points.get(i).y() + normal.y() * offset));
        }
        return result;
    }

    private static Vec2 segmentNormal(Vec2 a, Vec2 b) {
        return normalized(new Vec2(-(b.y() - a.y()), b.x() - a.x()));
    }

    private static Vec2 normalized(Vec2 value) {
        double length = Math.hypot(value.x(), value.y());
        if (length < 0.001D) {
            return new Vec2(0.0D, -1.0D);
        }
        return new Vec2(value.x() / length, value.y() / length);
    }

    private static double routeBundleWidth(List<EdgeLane> lanes, double width) {
        return lanes.isEmpty() ? width : lanes.size() * width + Math.max(0, lanes.size() - 1);
    }

    private static List<EdgeLane> edgeLanes(List<RouteLine> lines) {
        if (lines.isEmpty()) {
            return List.of(new EdgeLane(List.of(FullRouteMapConfig.MAP_TRUNK)));
        }
        return lines.stream()
                .map(line -> new EdgeLane(routeLineColors(line)))
                .toList();
    }

    private static List<RouteLine> routeLinesForIds(Collection<UUID> routeLineIds) {
        return routeLineIds.stream()
                .distinct()
                .map(ClientRouteDataCache::routeLine)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing((RouteLine line) -> FullMapText.displayName(line)).thenComparing(RouteLine::id))
                .toList();
    }

    private static List<Integer> routeLineColors(RouteLine line) {
        List<Integer> colors = line.themeColors().stream()
                .map(SPSGui::opaque)
                .toList();
        return colors.isEmpty() ? List.of(FullRouteMapConfig.MAP_TRUNK) : colors;
    }

    private static String displayName(ClusterCardNode node) {
        return node.mapNode()
                .map(FullMapText::displayName)
                .orElse(node.label());
    }

    private static DisplayNameStack displayNameStack(ClusterCardNode node, double zoom) {
        DisplayNameStack name = node.mapNode()
                .map(FullMapText::displayNameStack)
                .orElse(DisplayNameStack.of(node.label()));
        if (zoom < 1.0D && node.kind() == ClusterCardNodeKind.MEMBER_STATION) {
            return name.withoutSecondary();
        }
        return name;
    }

    private static DisplayNameStack clusterNodeTitle(ClusterCardNode node) {
        return switch (node.kind()) {
            case MEMBER_DEEP_CLUSTER -> node.mapNode().map(FullMapText::displayNameStack).orElse(DisplayNameStack.of(Component.translatable("screen.superpipeslide.full_map.tooltip_card.deep_cluster").getString()));
            default -> node.mapNode().map(FullMapText::displayNameStack).orElse(DisplayNameStack.of(node.label()));
        };
    }

    private static String clusterNodeSubtitle(ClusterCardNode node) {
        return switch (node.kind()) {
            case MEMBER_DEEP_CLUSTER -> dimensionLabel(node.levelKey());
            case MEMBER_FOLD_ANCHOR -> Component.translatable("screen.superpipeslide.full_map.tooltip_card.fold_anchor").getString();
            case MEMBER_STATION -> Component.translatable("screen.superpipeslide.station.info").getString();
            case EXTERNAL_PORT -> Component.translatable("screen.superpipeslide.full_map.tooltip_card.external_port").getString();
        };
    }

    private static int clusterNodeAccent(ClusterCardNode node) {
        return switch (node.kind()) {
            case MEMBER_DEEP_CLUSTER -> FullRouteMapConfig.MAP_CLUSTER_OUTLINE;
            case MEMBER_FOLD_ANCHOR -> FullRouteMapConfig.MAP_FOLD_MULTI_LINE;
            default -> primaryRouteColor(node.routeLineIds());
        };
    }

    private static List<FullMapTooltipCard.Row> clusterNodeRows(ClusterCardNode node) {
        List<FullMapTooltipCard.Row> rows = new ArrayList<>();
        switch (node.kind()) {
            case MEMBER_DEEP_CLUSTER -> node.mapNode()
                    .ifPresent(mapNode -> rows.add(tooltipRow("screen.superpipeslide.full_map.tooltip_field.stations", Integer.toString(mapNode.stationGroupIds().size()))));
            case MEMBER_FOLD_ANCHOR -> {
                rows.add(tooltipRow("screen.superpipeslide.full_map.tooltip_field.dimension", dimensionLabel(node.levelKey())));
                rows.add(tooltipRow("screen.superpipeslide.full_map.tooltip_field.peer_dimension", node.foldPeerId().map(peer -> dimensionLabel(peer.levelKey())).orElse("?")));
            }
            case MEMBER_STATION -> {
                rows.add(tooltipRow("screen.superpipeslide.full_map.tooltip_field.dimension", dimensionLabel(node.levelKey())));
                rows.add(tooltipRow("screen.superpipeslide.full_map.tooltip_field.position", (int) node.worldX() + ", " + (int) node.worldY() + ", " + (int) node.worldZ()));
                rows.add(tooltipRow("screen.superpipeslide.full_map.tooltip_field.routes", Integer.toString(node.routeLineIds().size())));
            }
            case EXTERNAL_PORT -> {
                rows.add(tooltipRow("screen.superpipeslide.full_map.tooltip_field.dimension", dimensionLabel(node.levelKey())));
                rows.add(tooltipRow("screen.superpipeslide.full_map.tooltip_field.routes", Integer.toString(node.routeLineIds().size())));
            }
        }
        return rows;
    }

    private static FullMapTooltipCard.Row tooltipRow(String labelKey, String value) {
        return new FullMapTooltipCard.Row(Component.translatable(labelKey).getString(), value, FullMapTheme.TEXT_SECONDARY);
    }

    private static List<FullMapTooltipCard.RouteChip> routeChips(Collection<UUID> routeLineIds) {
        return routeLineIds.stream()
                .distinct()
                .map(ClientRouteDataCache::routeLine)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing((RouteLine line) -> FullMapText.displayName(line)).thenComparing(RouteLine::id))
                .limit(6)
                .map(line -> new FullMapTooltipCard.RouteChip(FullMapText.primaryName(line), routeLineColors(line), line.id().hashCode()))
                .toList();
    }

    private static int primaryRouteColor(Collection<UUID> routeLineIds) {
        return routeLineIds.stream()
                .map(ClientRouteDataCache::routeLine)
                .flatMap(Optional::stream)
                .findFirst()
                .map(ClusterCardRenderer::routeLineColors)
                .flatMap(colors -> colors.stream().findFirst())
                .orElse(FullMapTheme.BORDER_SELECTED);
    }

    private static DisplayNameStack edgeTitleStack(List<UUID> routeLineIds) {
        if (routeLineIds.size() == 1) {
            return routeLineIds.stream()
                    .findFirst()
                    .flatMap(ClientRouteDataCache::routeLine)
                    .map(FullMapText::displayNameStack)
                    .orElse(DisplayNameStack.of(Component.translatable("screen.superpipeslide.route").getString()));
        }
        return DisplayNameStack.of(Component.translatable("screen.superpipeslide.full_map.tooltip_card.shared_routes").getString());
    }

    private static String dimensionLabel(ResourceKey<Level> dimension) {
        String path = dimension.identifier().getPath();
        return switch (dimension.identifier().toString()) {
            case "minecraft:overworld" -> Component.translatable("screen.superpipeslide.full_map.dimension.overworld").getString();
            case "minecraft:the_nether" -> Component.translatable("screen.superpipeslide.full_map.dimension.nether").getString();
            case "minecraft:the_end" -> Component.translatable("screen.superpipeslide.full_map.dimension.end").getString();
            default -> path;
        };
    }

    private static double distanceToPolyline(Vec2 point, List<Vec2> points) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i + 1 < points.size(); i++) {
            best = Math.min(best, distanceToSegment(point, points.get(i), points.get(i + 1)));
        }
        return best;
    }

    private static double distanceToSegment(Vec2 point, Vec2 a, Vec2 b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double len2 = dx * dx + dy * dy;
        if (len2 <= 0.0001D) {
            return point.distanceTo(a);
        }
        double t = ((point.x() - a.x()) * dx + (point.y() - a.y()) * dy) / len2;
        double clamped = Math.max(0.0D, Math.min(1.0D, t));
        return point.distanceTo(new Vec2(a.x() + dx * clamped, a.y() + dy * clamped));
    }

    private static double polylineLength(List<Vec2> path) {
        double length = 0.0D;
        for (int i = 0; i + 1 < path.size(); i++) {
            length += path.get(i).distanceTo(path.get(i + 1));
        }
        return length;
    }

    private static Aabb2 boundsFor(List<Vec2> points) {
        Aabb2 bounds = Aabb2.empty();
        for (Vec2 point : points) {
            bounds = bounds.include(point.x(), point.y());
        }
        return bounds;
    }

    private static boolean rectsOverlap(SPSGui.Rect first, SPSGui.Rect second) {
        return first.x() < second.right() && first.right() > second.x() && first.y() < second.bottom() && first.bottom() > second.y();
    }

    private record LabelBlocker(String nodeId, SPSGui.Rect bounds) {}

    private record EdgeLane(List<Integer> colors) {
        private EdgeLane {
            colors = List.copyOf(colors);
        }
    }
}
