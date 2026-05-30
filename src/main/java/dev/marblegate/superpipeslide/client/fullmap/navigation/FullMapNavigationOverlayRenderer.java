package dev.marblegate.superpipeslide.client.fullmap.navigation;

import dev.marblegate.superpipeslide.client.core.navigation.ClientNavigationController;
import dev.marblegate.superpipeslide.client.fullmap.cache.FullRouteMapCache;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapLayoutMode;
import dev.marblegate.superpipeslide.client.fullmap.model.MapDimensionGraph;
import dev.marblegate.superpipeslide.client.fullmap.model.MapEdge;
import dev.marblegate.superpipeslide.client.fullmap.model.MapEdgeOccurrence;
import dev.marblegate.superpipeslide.client.fullmap.model.MapNode;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.ViewportState;
import dev.marblegate.superpipeslide.client.fullmap.physical.PhysicalMapEdge;
import dev.marblegate.superpipeslide.client.fullmap.physical.PhysicalRouteMapGraph;
import dev.marblegate.superpipeslide.client.fullmap.render.FullRouteMapRenderer;
import dev.marblegate.superpipeslide.client.fullmap.render.SmoothGuiPrimitives;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualEdgePath;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualLane;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualRouteMapGraph;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
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
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class FullMapNavigationOverlayRenderer {
    private static final int SHADOW = 0xD416202B;
    private static final int ACTIVE_HALO = 0xC8FFFFFF;
    private static final int TRANSFER_OUTLINE = 0xEE17202B;
    private static final int MARKER_OUTER = 0xF517202B;
    private static final int START = 0xFF38C86E;
    private static final int TRANSFER_SAME = 0xFF30B76B;
    private static final int TRANSFER_OUT = 0xFFFFB13B;
    private static final int TRANSFER_CROSS = 0xFFC59BFF;

    public void render(
            GuiGraphicsExtractor graphics,
            MapDimensionGraph graph,
            @Nullable VisualRouteMapGraph visualGraph,
            ViewportState viewport,
            SPSGui.Rect mapRect,
            ClientNavigationController.NavigationPlan plan,
            int activeSegmentIndex) {
        if (graph == null || plan == null || plan.segments().isEmpty()) {
            return;
        }
        List<ProjectedPiece> pieces = visualGraph == null
                ? projectSemantic(graph, viewport, mapRect, plan, activeSegmentIndex)
                : projectVisual(graph, visualGraph, viewport, mapRect, plan, activeSegmentIndex);
        renderStrokes(graphics, pieces);
        renderTransferMarkers(graphics, graph, visualGraph, viewport, mapRect, plan);
        renderStationMarker(graphics, graph, visualGraph, viewport, mapRect, plan.destinationStationGroupId(), firstPlanColor(plan), MarkerKind.DESTINATION);
        renderStationMarker(graphics, graph, visualGraph, viewport, mapRect, plan.startStationGroupId(), START, MarkerKind.START);
    }

    public void renderPhysical(
            GuiGraphicsExtractor graphics,
            PhysicalRouteMapGraph graph,
            ViewportState viewport,
            SPSGui.Rect mapRect,
            ClientNavigationController.NavigationPlan plan,
            int activeSegmentIndex) {
        if (graph == null || plan == null || plan.segments().isEmpty()) {
            return;
        }
        renderStrokes(graphics, projectPhysical(graph, viewport, mapRect, plan, activeSegmentIndex));
        renderPhysicalTransferMarkers(graphics, graph, viewport, mapRect, plan);
        renderPhysicalStationMarker(graphics, graph, viewport, mapRect, plan.destinationStationGroupId(), firstPlanColor(plan), MarkerKind.DESTINATION);
        renderPhysicalStationMarker(graphics, graph, viewport, mapRect, plan.startStationGroupId(), START, MarkerKind.START);
    }

    private static List<ProjectedPiece> projectVisual(
            MapDimensionGraph graph,
            VisualRouteMapGraph visualGraph,
            ViewportState viewport,
            SPSGui.Rect mapRect,
            ClientNavigationController.NavigationPlan plan,
            int activeSegmentIndex) {
        Map<String, MapEdge> rawEdges = graph.edges().stream().collect(HashMap::new, (map, edge) -> map.put(edge.id(), edge), HashMap::putAll);
        boolean pureSchematic = FullRouteMapCache.layoutMode() == FullRouteMapLayoutMode.SCHEMATIC;
        List<ProjectedPiece> pieces = new ArrayList<>();
        for (ClientNavigationController.NavigationSegment segment : plan.segments()) {
            SegmentMatcher matcher = new SegmentMatcher(segment);
            for (VisualEdgePath path : visualGraph.edgePaths()) {
                if (!matcher.matches(path.occurrences())) {
                    continue;
                }
                List<Vec2> worldPath;
                if (pureSchematic) {
                    worldPath = path.points();
                } else {
                    MapEdge edge = rawEdges.get(path.edgeId());
                    if (edge == null) {
                        continue;
                    }
                    worldPath = FullRouteMapRenderer.visualWorldPathForEdge(graph, visualGraph, path, edge, viewport.zoom()).orElse(List.of());
                }
                List<Vec2> screenPath = toScreen(worldPath, viewport, mapRect);
                screenPath = applyVisualLaneOffset(screenPath, path, segment);
                addPiece(pieces, segment, matcher.sortKey(path.occurrences()), orientPath(screenPath, segment), activeSegmentIndex);
            }
        }
        return pieces;
    }

    private static List<ProjectedPiece> projectSemantic(
            MapDimensionGraph graph,
            ViewportState viewport,
            SPSGui.Rect mapRect,
            ClientNavigationController.NavigationPlan plan,
            int activeSegmentIndex) {
        List<ProjectedPiece> pieces = new ArrayList<>();
        for (ClientNavigationController.NavigationSegment segment : plan.segments()) {
            SegmentMatcher matcher = new SegmentMatcher(segment);
            for (MapEdge edge : graph.edges()) {
                if (!matcher.matches(edge.occurrences())) {
                    continue;
                }
                Optional<List<Vec2>> screenPath = projectedSemanticScreenPath(graph, edge, viewport, mapRect);
                screenPath.ifPresent(path -> addPiece(pieces, segment, matcher.sortKey(edge.occurrences()), orientPath(path, segment), activeSegmentIndex));
            }
        }
        return pieces;
    }

    private static List<ProjectedPiece> projectPhysical(
            PhysicalRouteMapGraph graph,
            ViewportState viewport,
            SPSGui.Rect mapRect,
            ClientNavigationController.NavigationPlan plan,
            int activeSegmentIndex) {
        List<ProjectedPiece> pieces = new ArrayList<>();
        for (ClientNavigationController.NavigationSegment segment : plan.segments()) {
            SegmentMatcher matcher = new SegmentMatcher(segment);
            for (PhysicalMapEdge edge : graph.edges()) {
                if (!matcher.matches(edge)) {
                    continue;
                }
                List<Vec2> path = toScreen(edge.points(), viewport, mapRect);
                addPiece(pieces, segment, matcher.sortKey(edge.metadata().routeSectionId(), edge.metadata().layoutIndex()), orientPath(path, segment), activeSegmentIndex);
            }
        }
        return pieces;
    }

    private static Optional<List<Vec2>> projectedSemanticScreenPath(MapDimensionGraph graph, MapEdge edge, ViewportState viewport, SPSGui.Rect mapRect) {
        NodeId fromId = FullRouteMapRenderer.displayNodeId(graph, edge.from(), viewport.zoom());
        NodeId toId = FullRouteMapRenderer.displayNodeId(graph, edge.to(), viewport.zoom());
        if (fromId.equals(toId)) {
            return Optional.empty();
        }
        MapNode from = graph.nodesById().get(fromId);
        MapNode to = graph.nodesById().get(toId);
        if (from == null || to == null) {
            return Optional.empty();
        }
        return Optional.of(List.of(
                FullRouteMapRenderer.worldToScreen(from.worldX(), from.worldZ(), viewport, mapRect),
                FullRouteMapRenderer.worldToScreen(to.worldX(), to.worldZ(), viewport, mapRect)));
    }

    private static void addPiece(List<ProjectedPiece> pieces, ClientNavigationController.NavigationSegment segment, int order, List<Vec2> path, int activeSegmentIndex) {
        List<Vec2> cleaned = cleanPath(path);
        if (cleaned.size() < 2) {
            return;
        }
        pieces.add(new ProjectedPiece(
                segment.index(),
                segment.routeLineId(),
                segment.layoutId(),
                order,
                cleaned,
                normalizeColors(segment.colors()),
                segment.index() == activeSegmentIndex ? NavigationPhase.ACTIVE : segment.index() < activeSegmentIndex ? NavigationPhase.COMPLETE : NavigationPhase.UPCOMING));
    }

    private static void renderStrokes(GuiGraphicsExtractor graphics, List<ProjectedPiece> pieces) {
        List<NavigationStroke> strokes = stitchPieces(pieces);
        strokes.stream()
                .sorted(Comparator.comparingInt((NavigationStroke stroke) -> stroke.phase().drawOrder()).thenComparingInt(NavigationStroke::segmentIndex))
                .forEach(stroke -> drawNavigationStroke(graphics, stroke));
    }

    private static List<NavigationStroke> stitchPieces(List<ProjectedPiece> pieces) {
        Map<StrokeKey, List<ProjectedPiece>> groups = new LinkedHashMap<>();
        pieces.stream()
                .sorted(Comparator.comparingInt(ProjectedPiece::segmentIndex).thenComparingInt(ProjectedPiece::order).thenComparing(piece -> piece.path().getFirst().x()))
                .forEach(piece -> groups.computeIfAbsent(new StrokeKey(piece.segmentIndex(), piece.routeLineId(), piece.layoutId(), piece.phase(), piece.colors()), ignored -> new ArrayList<>()).add(piece));
        List<NavigationStroke> strokes = new ArrayList<>();
        for (Map.Entry<StrokeKey, List<ProjectedPiece>> entry : groups.entrySet()) {
            List<ProjectedPiece> pending = new ArrayList<>(entry.getValue());
            pending.sort(Comparator.comparingInt(ProjectedPiece::order));
            while (!pending.isEmpty()) {
                List<Vec2> path = new ArrayList<>(pending.removeFirst().path());
                boolean extended;
                do {
                    extended = false;
                    int index = nearestConnectable(path, pending);
                    if (index >= 0) {
                        ProjectedPiece next = pending.remove(index);
                        appendPath(path, orientedForAppend(path, next.path()));
                        extended = true;
                    }
                } while (extended);
                StrokeKey key = entry.getKey();
                strokes.add(new NavigationStroke(key.segmentIndex(), key.routeLineId(), key.layoutId(), path, key.colors(), key.phase()));
            }
        }
        return strokes;
    }

    private static int nearestConnectable(List<Vec2> path, List<ProjectedPiece> pending) {
        Vec2 tail = path.getLast();
        int best = -1;
        double bestDistance = 9.0D;
        for (int i = 0; i < pending.size(); i++) {
            List<Vec2> candidate = pending.get(i).path();
            double distance = Math.min(tail.distanceTo(candidate.getFirst()), tail.distanceTo(candidate.getLast()));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static List<Vec2> orientedForAppend(List<Vec2> current, List<Vec2> next) {
        Vec2 tail = current.getLast();
        if (tail.distanceTo(next.getFirst()) <= tail.distanceTo(next.getLast())) {
            return next;
        }
        ArrayList<Vec2> reversed = new ArrayList<>(next);
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    private static void appendPath(List<Vec2> path, List<Vec2> next) {
        if (path.isEmpty()) {
            path.addAll(next);
            return;
        }
        int start = path.getLast().distanceTo(next.getFirst()) <= 1.2D ? 1 : 0;
        for (int i = start; i < next.size(); i++) {
            path.add(next.get(i));
        }
    }

    private static void drawNavigationStroke(GuiGraphicsExtractor graphics, NavigationStroke stroke) {
        if (stroke.path().size() < 2) {
            return;
        }
        double core = stroke.phase() == NavigationPhase.ACTIVE ? 7.2D : 6.0D;
        double halo = stroke.phase() == NavigationPhase.ACTIVE ? 12.0D : 10.0D;
        int alpha = stroke.phase() == NavigationPhase.ACTIVE ? 0xFF : stroke.phase() == NavigationPhase.COMPLETE ? 0x90 : 0xC8;
        SmoothGuiPrimitives.polyline(graphics, stroke.path(), halo + 5.0D, SPSGui.withAlpha(SHADOW, Math.min(0xE0, alpha)), true);
        SmoothGuiPrimitives.polyline(graphics, stroke.path(), halo + 1.5D, SPSGui.withAlpha(primaryColor(stroke.colors()), Math.max(0x70, alpha - 0x40)), true);
        SmoothGuiPrimitives.polyline(graphics, stroke.path(), halo - 1.4D, SPSGui.withAlpha(ACTIVE_HALO, stroke.phase() == NavigationPhase.ACTIVE ? 0xB4 : 0x62), true);
        drawColorPolyline(graphics, stroke.path(), core, stroke.colors(), alpha);
        if (stroke.phase() == NavigationPhase.ACTIVE) {
            renderPathDirection(graphics, stroke.path(), primaryColor(stroke.colors()));
        }
    }

    private static void drawColorPolyline(GuiGraphicsExtractor graphics, List<Vec2> path, double totalWidth, List<Integer> colors, int alpha) {
        List<Integer> normalized = colors.isEmpty() ? List.of(0xFF47A6FF) : colors.stream().limit(3).map(SPSGui::opaque).toList();
        if (normalized.size() == 1) {
            SmoothGuiPrimitives.polyline(graphics, path, totalWidth, SPSGui.withAlpha(normalized.getFirst(), alpha), true);
            return;
        }
        double stripeWidth = totalWidth / normalized.size();
        double center = (normalized.size() - 1) * 0.5D;
        for (int i = 0; i < normalized.size(); i++) {
            List<Vec2> stripe = FullRouteMapRenderer.offsetScreenPath(path, (i - center) * stripeWidth);
            SmoothGuiPrimitives.polyline(graphics, stripe, stripeWidth + 0.35D, SPSGui.withAlpha(normalized.get(i), alpha), true);
        }
    }

    private static void renderPathDirection(GuiGraphicsExtractor graphics, List<Vec2> screenPath, int color) {
        double target = 34.0D;
        for (int i = 1; i < screenPath.size(); i++) {
            Vec2 previous = screenPath.get(i - 1);
            Vec2 current = screenPath.get(i);
            double length = previous.distanceTo(current);
            if (length <= 10.0D) {
                continue;
            }
            target -= length;
            if (target > 0.0D) {
                continue;
            }
            double ux = (current.x() - previous.x()) / length;
            double uy = (current.y() - previous.y()) / length;
            Vec2 center = new Vec2(current.x() - ux * Math.min(18.0D, length * 0.35D), current.y() - uy * Math.min(18.0D, length * 0.35D));
            Vec2 axis = new Vec2(ux, uy);
            SmoothGuiPrimitives.capsule(graphics, center, axis, 15.0D, 6.0D, 0xEFFFFFFF);
            SmoothGuiPrimitives.capsule(graphics, center, axis, 10.5D, 3.5D, SPSGui.opaque(color));
            target = 92.0D;
        }
    }

    private static List<Vec2> applyVisualLaneOffset(List<Vec2> screenPath, VisualEdgePath path, ClientNavigationController.NavigationSegment segment) {
        if (screenPath.size() < 2 || path.lanes().isEmpty()) {
            return screenPath;
        }
        Optional<VisualLane> lane = path.lanes().stream()
                .filter(candidate -> candidate.routeLineId().filter(segment.routeLineId()::equals).isPresent())
                .findFirst();
        if (lane.isEmpty() && path.lanes().size() == 1) {
            lane = Optional.of(path.lanes().getFirst());
        }
        return lane
                .map(value -> FullRouteMapRenderer.offsetScreenPath(screenPath, value.offsetBlocks() * FullRouteMapConfig.BASE_SCALE))
                .orElse(screenPath);
    }

    private static List<Vec2> orientPath(List<Vec2> path, ClientNavigationController.NavigationSegment segment) {
        if (segment.routeDirection() >= 0 || path == null || path.size() < 2) {
            return path;
        }
        ArrayList<Vec2> reversed = new ArrayList<>(path);
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    private static List<Vec2> toScreen(List<Vec2> worldPath, ViewportState viewport, SPSGui.Rect mapRect) {
        if (worldPath == null || worldPath.size() < 2) {
            return List.of();
        }
        return worldPath.stream()
                .map(point -> FullRouteMapRenderer.worldToScreen(point.x(), point.y(), viewport, mapRect))
                .filter(point -> Double.isFinite(point.x()) && Double.isFinite(point.y()))
                .collect(Collectors.toList());
    }

    private static List<Vec2> cleanPath(List<Vec2> path) {
        if (path == null || path.isEmpty()) {
            return List.of();
        }
        ArrayList<Vec2> result = new ArrayList<>();
        for (Vec2 point : path) {
            if (Double.isFinite(point.x()) && Double.isFinite(point.y()) && (result.isEmpty() || result.getLast().distanceTo(point) > 0.35D)) {
                result.add(point);
            }
        }
        return result;
    }

    private static void renderStationMarker(
            GuiGraphicsExtractor graphics,
            MapDimensionGraph graph,
            @Nullable VisualRouteMapGraph visualGraph,
            ViewportState viewport,
            SPSGui.Rect mapRect,
            UUID stationGroupId,
            int color,
            MarkerKind kind) {
        stationScreen(graph, visualGraph, viewport, mapRect, stationGroupId).ifPresent(screen -> renderMarker(graphics, screen, color, kind));
    }

    private static void renderPhysicalStationMarker(
            GuiGraphicsExtractor graphics,
            PhysicalRouteMapGraph graph,
            ViewportState viewport,
            SPSGui.Rect mapRect,
            UUID stationGroupId,
            int color,
            MarkerKind kind) {
        physicalStationScreen(graph, viewport, mapRect, stationGroupId).ifPresent(screen -> renderMarker(graphics, screen, color, kind));
    }

    private static Optional<Vec2> stationScreen(MapDimensionGraph graph, @Nullable VisualRouteMapGraph visualGraph, ViewportState viewport, SPSGui.Rect mapRect, UUID stationGroupId) {
        return graph.nodes().stream()
                .filter(node -> node.stationGroupIds().contains(stationGroupId))
                .findFirst()
                .flatMap(node -> {
                    if (visualGraph != null && FullRouteMapCache.layoutMode() == FullRouteMapLayoutMode.SCHEMATIC) {
                        Vec2 world = FullRouteMapRenderer.visualPosition(graph, visualGraph, node);
                        return Optional.of(FullRouteMapRenderer.worldToScreen(world.x(), world.y(), viewport, mapRect));
                    }
                    return projectedNodeScreen(graph, visualGraph, viewport, mapRect, node.id());
                });
    }

    private static Optional<Vec2> projectedNodeScreen(MapDimensionGraph graph, @Nullable VisualRouteMapGraph visualGraph, ViewportState viewport, SPSGui.Rect mapRect, NodeId nodeId) {
        NodeId displayId = FullRouteMapRenderer.displayNodeId(graph, nodeId, viewport.zoom());
        MapNode display = graph.nodesById().get(displayId);
        if (display == null) {
            return Optional.empty();
        }
        Vec2 world = visualGraph == null ? new Vec2(display.worldX(), display.worldZ()) : FullRouteMapRenderer.visualPosition(graph, visualGraph, display);
        return Optional.of(FullRouteMapRenderer.worldToScreen(world.x(), world.y(), viewport, mapRect));
    }

    private static Optional<Vec2> physicalStationScreen(PhysicalRouteMapGraph graph, ViewportState viewport, SPSGui.Rect mapRect, UUID stationGroupId) {
        return graph.stationFrame(stationGroupId)
                .map(frame -> FullRouteMapRenderer.worldToScreen(frame.centerX(), frame.centerZ(), viewport, mapRect));
    }

    private static void renderTransferMarkers(GuiGraphicsExtractor graphics, MapDimensionGraph graph, @Nullable VisualRouteMapGraph visualGraph, ViewportState viewport, SPSGui.Rect mapRect, ClientNavigationController.NavigationPlan plan) {
        for (int i = 0; i + 1 < plan.segments().size(); i++) {
            ClientNavigationController.NavigationSegment current = plan.segments().get(i);
            ClientNavigationController.NavigationSegment next = plan.segments().get(i + 1);
            ClientNavigationController.TransferInstruction instruction = current.transferInstruction()
                    .orElseGet(() -> ClientNavigationController.TransferInstruction.sameStationFallback(current, next));
            renderTransferInstruction(graphics, graph.levelKey(), stationScreen(graph, visualGraph, viewport, mapRect, instruction.fromStationGroupId()), stationScreen(graph, visualGraph, viewport, mapRect, instruction.toStationGroupId()), instruction.kind(), instruction.fromLevelKey(), instruction.toLevelKey());
        }
        for (ClientNavigationController.NavigationSegment segment : plan.segments()) {
            segment.finalWalkInstruction().ifPresent(instruction -> renderTransferInstruction(
                    graphics,
                    graph.levelKey(),
                    stationScreen(graph, visualGraph, viewport, mapRect, instruction.fromStationGroupId()),
                    stationScreen(graph, visualGraph, viewport, mapRect, instruction.destinationStationGroupId()),
                    instruction.kind(),
                    instruction.fromLevelKey(),
                    instruction.destinationLevelKey()));
        }
    }

    private static void renderPhysicalTransferMarkers(GuiGraphicsExtractor graphics, PhysicalRouteMapGraph graph, ViewportState viewport, SPSGui.Rect mapRect, ClientNavigationController.NavigationPlan plan) {
        for (int i = 0; i + 1 < plan.segments().size(); i++) {
            ClientNavigationController.NavigationSegment current = plan.segments().get(i);
            ClientNavigationController.NavigationSegment next = plan.segments().get(i + 1);
            ClientNavigationController.TransferInstruction instruction = current.transferInstruction()
                    .orElseGet(() -> ClientNavigationController.TransferInstruction.sameStationFallback(current, next));
            renderTransferInstruction(graphics, graph.levelKey(), physicalStationScreen(graph, viewport, mapRect, instruction.fromStationGroupId()), physicalStationScreen(graph, viewport, mapRect, instruction.toStationGroupId()), instruction.kind(), instruction.fromLevelKey(), instruction.toLevelKey());
        }
        for (ClientNavigationController.NavigationSegment segment : plan.segments()) {
            segment.finalWalkInstruction().ifPresent(instruction -> renderTransferInstruction(
                    graphics,
                    graph.levelKey(),
                    physicalStationScreen(graph, viewport, mapRect, instruction.fromStationGroupId()),
                    physicalStationScreen(graph, viewport, mapRect, instruction.destinationStationGroupId()),
                    instruction.kind(),
                    instruction.fromLevelKey(),
                    instruction.destinationLevelKey()));
        }
    }

    private static void renderTransferInstruction(
            GuiGraphicsExtractor graphics,
            ResourceKey<Level> levelKey,
            Optional<Vec2> from,
            Optional<Vec2> to,
            ClientNavigationController.TransferKind kind,
            ResourceKey<Level> fromLevel,
            ResourceKey<Level> toLevel) {
        Optional<Vec2> visibleFrom = fromLevel.equals(levelKey) ? from : Optional.empty();
        Optional<Vec2> visibleTo = toLevel.equals(levelKey) ? to : Optional.empty();
        if (kind == ClientNavigationController.TransferKind.SAME_STATION) {
            visibleFrom.or(() -> visibleTo).ifPresent(point -> drawSameStationGlyph(graphics, point));
            return;
        }
        int color = kind == ClientNavigationController.TransferKind.OUT_OF_STATION ? TRANSFER_OUT : TRANSFER_CROSS;
        if (visibleFrom.isPresent() && visibleTo.isPresent() && visibleFrom.get().distanceTo(visibleTo.get()) > 4.0D) {
            drawDashedConnector(graphics, visibleFrom.get(), visibleTo.get(), color);
            Vec2 mid = midpoint(visibleFrom.get(), visibleTo.get());
            if (kind == ClientNavigationController.TransferKind.OUT_OF_STATION) {
                drawOutStationGlyph(graphics, mid);
            } else {
                drawCrossDimensionGlyph(graphics, mid);
            }
            return;
        }
        visibleFrom.or(() -> visibleTo).ifPresent(point -> {
            if (kind == ClientNavigationController.TransferKind.OUT_OF_STATION) {
                drawOutStationGlyph(graphics, point);
            } else {
                drawCrossDimensionGlyph(graphics, point);
            }
        });
    }

    private static void renderMarker(GuiGraphicsExtractor graphics, Vec2 screen, int color, MarkerKind kind) {
        if (kind == MarkerKind.DESTINATION) {
            SmoothGuiPrimitives.diamond(graphics, screen, 10.8D, MARKER_OUTER);
            SmoothGuiPrimitives.diamond(graphics, screen, 7.0D, SPSGui.opaque(color));
            SmoothGuiPrimitives.circle(graphics, screen, 2.0D, 0xF0FFFFFF);
            return;
        }
        SmoothGuiPrimitives.circle(graphics, screen, 8.5D, MARKER_OUTER);
        SmoothGuiPrimitives.circle(graphics, screen, 5.0D, SPSGui.opaque(color));
        SmoothGuiPrimitives.circle(graphics, screen, 1.8D, 0xF0FFFFFF);
    }

    private static void drawSameStationGlyph(GuiGraphicsExtractor graphics, Vec2 center) {
        SmoothGuiPrimitives.circle(graphics, center, 8.0D, TRANSFER_OUTLINE);
        SmoothGuiPrimitives.circle(graphics, center, 6.0D, 0xF2FFFFFF);
        SmoothGuiPrimitives.line(graphics, new Vec2(center.x() - 3.0D, center.y()), new Vec2(center.x() + 3.0D, center.y()), 2.0D, TRANSFER_SAME);
        SmoothGuiPrimitives.circle(graphics, new Vec2(center.x() - 3.3D, center.y()), 2.3D, TRANSFER_SAME);
        SmoothGuiPrimitives.circle(graphics, new Vec2(center.x() + 3.3D, center.y()), 2.3D, TRANSFER_SAME);
    }

    private static void drawOutStationGlyph(GuiGraphicsExtractor graphics, Vec2 center) {
        SmoothGuiPrimitives.circle(graphics, center, 8.0D, TRANSFER_OUTLINE);
        SmoothGuiPrimitives.circle(graphics, center, 6.0D, 0xF8FFFFFF);
        graphics.fill((int) Math.round(center.x() - 3.0D), (int) Math.round(center.y() - 4.0D), (int) Math.round(center.x() + 2.0D), (int) Math.round(center.y() + 4.0D), SPSGui.withAlpha(TRANSFER_OUT, 0xE8));
        SmoothGuiPrimitives.line(graphics, new Vec2(center.x() - 1.0D, center.y()), new Vec2(center.x() + 4.5D, center.y()), 1.6D, TRANSFER_OUT);
        SmoothGuiPrimitives.line(graphics, new Vec2(center.x() + 2.1D, center.y() - 2.0D), new Vec2(center.x() + 4.6D, center.y()), 1.4D, TRANSFER_OUT);
        SmoothGuiPrimitives.line(graphics, new Vec2(center.x() + 2.1D, center.y() + 2.0D), new Vec2(center.x() + 4.6D, center.y()), 1.4D, TRANSFER_OUT);
    }

    private static void drawCrossDimensionGlyph(GuiGraphicsExtractor graphics, Vec2 center) {
        SmoothGuiPrimitives.diamond(graphics, center, 8.8D, TRANSFER_OUTLINE);
        SmoothGuiPrimitives.diamond(graphics, center, 6.5D, 0xF8FFFFFF);
        SmoothGuiPrimitives.ring(graphics, center, 4.3D, 1.7D, TRANSFER_CROSS);
        SmoothGuiPrimitives.circle(graphics, center, 1.6D, TRANSFER_CROSS);
    }

    private static void drawDashedConnector(GuiGraphicsExtractor graphics, Vec2 a, Vec2 b, int color) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double length = Math.hypot(dx, dy);
        if (length <= 2.0D) {
            return;
        }
        double ux = dx / length;
        double uy = dy / length;
        for (double start = 8.0D; start < length - 8.0D; start += 8.5D) {
            double end = Math.min(length - 8.0D, start + 4.5D);
            SmoothGuiPrimitives.line(graphics, new Vec2(a.x() + ux * start, a.y() + uy * start), new Vec2(a.x() + ux * end, a.y() + uy * end), 3.0D, SPSGui.withAlpha(SHADOW, 0xB0));
            SmoothGuiPrimitives.line(graphics, new Vec2(a.x() + ux * start, a.y() + uy * start), new Vec2(a.x() + ux * end, a.y() + uy * end), 1.5D, SPSGui.withAlpha(color, 0xE8));
        }
    }

    private static Vec2 midpoint(Vec2 a, Vec2 b) {
        return new Vec2((a.x() + b.x()) * 0.5D, (a.y() + b.y()) * 0.5D);
    }

    private static int firstPlanColor(ClientNavigationController.NavigationPlan plan) {
        return plan.primaryColors().isEmpty() ? 0xFF47A6FF : SPSGui.opaque(plan.primaryColors().getFirst());
    }

    private static int primaryColor(List<Integer> colors) {
        return colors == null || colors.isEmpty() ? 0xFF47A6FF : SPSGui.opaque(colors.getFirst());
    }

    private static List<Integer> normalizeColors(List<Integer> colors) {
        if (colors == null || colors.isEmpty()) {
            return List.of(0xFF47A6FF);
        }
        return colors.stream().limit(3).map(SPSGui::opaque).toList();
    }

    private record ProjectedPiece(int segmentIndex, UUID routeLineId, UUID layoutId, int order, List<Vec2> path, List<Integer> colors, NavigationPhase phase) {
        private ProjectedPiece {
            path = List.copyOf(path);
            colors = List.copyOf(colors);
        }
    }

    private record StrokeKey(int segmentIndex, UUID routeLineId, UUID layoutId, NavigationPhase phase, List<Integer> colors) {
        private StrokeKey {
            colors = List.copyOf(colors);
        }
    }

    private record NavigationStroke(int segmentIndex, UUID routeLineId, UUID layoutId, List<Vec2> path, List<Integer> colors, NavigationPhase phase) {
        private NavigationStroke {
            path = List.copyOf(path);
            colors = List.copyOf(colors);
        }
    }

    private enum NavigationPhase {
        COMPLETE(0),
        UPCOMING(1),
        ACTIVE(2);

        private final int drawOrder;

        NavigationPhase(int drawOrder) {
            this.drawOrder = drawOrder;
        }

        private int drawOrder() {
            return this.drawOrder;
        }
    }

    private enum MarkerKind {
        START,
        DESTINATION
    }

    private static final class SegmentMatcher {
        private final ClientNavigationController.NavigationSegment segment;
        private final Set<ClientNavigationController.NavigationSectionRef> sectionRefs;
        private final Set<UUID> sectionIds;
        private final Map<ClientNavigationController.NavigationSectionRef, Integer> orderByRef = new HashMap<>();
        private final Map<UUID, Integer> orderBySection = new HashMap<>();

        private SegmentMatcher(ClientNavigationController.NavigationSegment segment) {
            this.segment = segment;
            this.sectionRefs = new HashSet<>(segment.routeSections());
            this.sectionIds = new HashSet<>(segment.routeSectionIds());
            for (int i = 0; i < segment.routeSections().size(); i++) {
                this.orderByRef.putIfAbsent(segment.routeSections().get(i), i);
            }
            for (int i = 0; i < segment.routeSectionIds().size(); i++) {
                this.orderBySection.putIfAbsent(segment.routeSectionIds().get(i), i);
            }
        }

        private boolean matches(PhysicalMapEdge edge) {
            return this.segment.routeLineId().equals(edge.metadata().routeLineId())
                    && this.segment.layoutId().equals(edge.metadata().routeLayoutId())
                    && matchesDirection(edge.metadata().routeDirection(), edge.metadata().bidirectional())
                    && this.matchesSection(edge.metadata().routeSectionId(), edge.metadata().layoutIndex());
        }

        private boolean matches(List<MapEdgeOccurrence> occurrences) {
            return occurrences.stream().anyMatch(occurrence -> this.segment.routeLineId().equals(occurrence.routeLineId())
                    && this.segment.layoutId().equals(occurrence.routeLayoutId())
                    && this.matchesDirection(occurrence.routeDirection(), occurrence.bidirectional())
                    && this.matchesSection(occurrence.routeSectionId(), occurrence.layoutIndex()));
        }

        private boolean matchesDirection(int edgeDirection, boolean bidirectional) {
            int segmentDirection = this.segment.routeDirection() < 0 ? -1 : 1;
            int normalizedEdge = edgeDirection < 0 ? -1 : 1;
            return segmentDirection == normalizedEdge || bidirectional;
        }

        private boolean matchesSection(UUID routeSectionId, int layoutIndex) {
            if (!this.sectionRefs.isEmpty()) {
                return this.sectionRefs.contains(new ClientNavigationController.NavigationSectionRef(routeSectionId, layoutIndex));
            }
            return this.sectionIds.contains(routeSectionId);
        }

        private int sortKey(List<MapEdgeOccurrence> occurrences) {
            return occurrences.stream()
                    .filter(occurrence -> this.segment.routeLineId().equals(occurrence.routeLineId()) && this.segment.layoutId().equals(occurrence.routeLayoutId()))
                    .mapToInt(occurrence -> this.sortKey(occurrence.routeSectionId(), occurrence.layoutIndex()))
                    .min()
                    .orElse(Integer.MAX_VALUE / 2);
        }

        private int sortKey(UUID routeSectionId, int layoutIndex) {
            Integer exact = this.orderByRef.get(new ClientNavigationController.NavigationSectionRef(routeSectionId, layoutIndex));
            if (exact != null) {
                return exact;
            }
            return this.orderBySection.getOrDefault(routeSectionId, Integer.MAX_VALUE / 2);
        }
    }
}
