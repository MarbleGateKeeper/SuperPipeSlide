package dev.marblegate.superpipeslide.client.fullmap.routecard.layout;

import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardEdge;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNode;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNodeId;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNodeKind;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardSegment;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardSemanticGraph;
import dev.marblegate.superpipeslide.client.fullmap.routecard.visual.RouteCardVisualEdge;
import dev.marblegate.superpipeslide.client.fullmap.routecard.visual.RouteCardVisualGraph;
import dev.marblegate.superpipeslide.client.fullmap.routecard.visual.RouteCardVisualNode;
import dev.marblegate.superpipeslide.client.fullmap.routecard.visual.RouteCardVisualSegment;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SemanticEdgeKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RouteCardLayoutSolver {
    private static final int LOCAL_RELAX_ITERATIONS = 44;
    private static final int GLOBAL_COLLISION_ITERATIONS = 96;
    private static final double EDGE_FOOTPRINT_PADDING = 18.0D;
    private static final double SEGMENT_BODY_PADDING = 24.0D;
    private static final double SEGMENT_LABEL_PADDING = 18.0D;
    private static final double DIMENSION_CHIP_WIDTH = 86.0D;
    private static final double DIMENSION_CHIP_HEIGHT = 14.0D;
    private static final double DEFAULT_STATION_SPACING = 128.0D;
    private static final double MIN_STATION_SPACING = 104.0D;
    private static final double MAX_STATION_SPACING = 160.0D;
    private static final double SQRT_HALF = Math.sqrt(0.5D);
    private static final List<Vec2> CARD_DIRECTIONS = List.of(
            new Vec2(1.0D, 0.0D),
            new Vec2(SQRT_HALF, SQRT_HALF),
            new Vec2(0.0D, 1.0D),
            new Vec2(-SQRT_HALF, SQRT_HALF),
            new Vec2(-1.0D, 0.0D),
            new Vec2(-SQRT_HALF, -SQRT_HALF),
            new Vec2(0.0D, -1.0D),
            new Vec2(SQRT_HALF, -SQRT_HALF));

    public RouteCardVisualGraph solve(RouteCardSemanticGraph graph) {
        return this.solvePractical(graph);
    }

    public RouteCardVisualGraph solvePractical(RouteCardSemanticGraph graph) {
        return this.solve(graph, RouteCardSolveMode.PRACTICAL);
    }

    public RouteCardVisualGraph solveSchematic(RouteCardSemanticGraph graph) {
        return this.solve(graph, RouteCardSolveMode.SCHEMATIC);
    }

    private RouteCardVisualGraph solve(RouteCardSemanticGraph graph, RouteCardSolveMode mode) {
        if (graph.nodes().isEmpty()) {
            return new RouteCardVisualGraph(List.of(), List.of(), List.of(), List.of(), Aabb2.empty(), false);
        }

        Map<RouteCardNodeId, RouteCardNode> nodeById = nodeIndex(graph.nodes());
        Map<String, RouteCardEdge> edgeById = edgeIndex(graph.edges());
        RouteCardSpacing spacing = spacingFor(graph, mode);
        Map<RouteCardNodeId, Integer> canonicalSegmentIndexByNode = canonicalSegmentIndexByNode(graph.segments(), nodeById);
        List<SegmentBody> bodies = this.buildSegmentBodies(graph, nodeById, edgeById, canonicalSegmentIndexByNode, spacing);
        if (bodies.isEmpty()) {
            return this.solveUnsegmentedGraph(graph, nodeById, spacing);
        }

        SegmentPlacement placement = this.placeSegmentBodies(bodies, spacing);
        Map<RouteCardNodeId, Vec2> positions = new LinkedHashMap<>();
        Map<RouteCardNodeId, SegmentBody> owningBodyByNode = new LinkedHashMap<>();
        Set<String> routedEdgeIds = new HashSet<>();
        List<RouteCardVisualEdge> visualEdges = new ArrayList<>();
        List<RouteCardVisualSegment> visualSegments = new ArrayList<>();

        for (PlacedSegmentBody placed : placement.placed()) {
            for (Map.Entry<RouteCardNodeId, Vec2> entry : placed.body().localPositions().entrySet()) {
                if (!isCanonicalInSegment(entry.getKey(), placed.body().segment().index(), nodeById, canonicalSegmentIndexByNode)) {
                    positions.putIfAbsent(entry.getKey(), placed.translate(entry.getValue()));
                    owningBodyByNode.putIfAbsent(entry.getKey(), placed.body());
                    continue;
                }
                positions.put(entry.getKey(), placed.translate(entry.getValue()));
                owningBodyByNode.put(entry.getKey(), placed.body());
            }
            for (RouteCardVisualEdge localEdge : placed.body().localVisualEdges()) {
                if (!isCanonicalLocalEdge(localEdge.edge(), placed.body().segment().index(), nodeById, canonicalSegmentIndexByNode)) {
                    continue;
                }
                RouteCardVisualEdge translated = this.translateEdge(localEdge, placed.offsetX(), placed.offsetY());
                visualEdges.add(translated);
                routedEdgeIds.add(localEdge.edge().id());
            }
            visualSegments.add(new RouteCardVisualSegment(placed.body().segment(), placed.footprint()));
        }

        this.placeUnassignedNodes(graph, positions, placement.placed());
        List<RoutedEdge> routed = new ArrayList<>();
        for (RouteCardVisualEdge edge : visualEdges) {
            if (!edge.points().isEmpty()) {
                routed.add(new RoutedEdge(edge.edge(), edge.points()));
            }
        }
        List<Aabb2> obstacles = placement.placed().stream()
                .map(placed -> placed.footprint().inflate(8.0D))
                .toList();
        for (RouteCardEdge edge : graph.edges().stream().sorted(edgeOrder()).toList()) {
            if (routedEdgeIds.contains(edge.id())) {
                continue;
            }
            List<Aabb2> connectorObstacles = obstaclesForConnector(edge, placement.placed(), owningBodyByNode);
            RouteCardVisualEdge visualEdge = this.visualEdge(edge, positions, nodeById, routed, connectorObstacles, spacing);
            visualEdges.add(visualEdge);
            if (!visualEdge.points().isEmpty()) {
                routed.add(new RoutedEdge(edge, visualEdge.points()));
            }
        }

        List<RouteCardVisualNode> visualNodes = graph.nodes().stream()
                .map(node -> new RouteCardVisualNode(node, positions.getOrDefault(node.id(), new Vec2(node.worldX(), node.worldZ())), nodePriority(node)))
                .sorted(Comparator.comparingInt(RouteCardVisualNode::priority).thenComparing(node -> node.node().id()))
                .toList();

        Aabb2 bounds = graphBounds(visualNodes, visualEdges, visualSegments);
        return new RouteCardVisualGraph(visualNodes, visualEdges, visualSegments, List.of(), bounds, placement.fallback());
    }

    private RouteCardVisualGraph solveUnsegmentedGraph(RouteCardSemanticGraph graph, Map<RouteCardNodeId, RouteCardNode> nodeById, RouteCardSpacing spacing) {
        Map<RouteCardNodeId, Vec2> positions = this.localNodePositions(graph.nodes(), graph.edges(), spacing);
        List<RouteCardVisualEdge> visualEdges = this.visualEdgesFor(graph.edges().stream().sorted(edgeOrder()).toList(), positions, nodeById, List.of(), spacing);
        List<RouteCardVisualNode> visualNodes = graph.nodes().stream()
                .map(node -> new RouteCardVisualNode(node, positions.get(node.id()), nodePriority(node)))
                .sorted(Comparator.comparingInt(RouteCardVisualNode::priority).thenComparing(node -> node.node().id()))
                .toList();
        Aabb2 bounds = graphBounds(visualNodes, visualEdges, List.of());
        return new RouteCardVisualGraph(visualNodes, visualEdges, List.of(), List.of(), bounds, false);
    }

    private List<SegmentBody> buildSegmentBodies(
            RouteCardSemanticGraph graph,
            Map<RouteCardNodeId, RouteCardNode> nodeById,
            Map<String, RouteCardEdge> edgeById,
            Map<RouteCardNodeId, Integer> canonicalSegmentIndexByNode,
            RouteCardSpacing spacing) {
        List<SegmentBody> bodies = new ArrayList<>();
        for (RouteCardSegment segment : graph.segments().stream().sorted(Comparator.comparingInt(RouteCardSegment::index)).toList()) {
            List<RouteCardNode> nodes = segment.nodeIds().stream()
                    .map(nodeById::get)
                    .filter(java.util.Objects::nonNull)
                    .filter(node -> isCanonicalInSegment(node.id(), segment.index(), nodeById, canonicalSegmentIndexByNode))
                    .toList();
            if (nodes.isEmpty()) {
                continue;
            }
            List<RouteCardNode> stationNodes = nodes.stream()
                    .filter(node -> node.kind() == RouteCardNodeKind.STATION)
                    .toList();
            if (stationNodes.isEmpty()) {
                continue;
            }
            List<RouteCardEdge> edges = segment.edgeIds().stream()
                    .map(edgeById::get)
                    .filter(java.util.Objects::nonNull)
                    .filter(edge -> edge.segmentIndex() == segment.index())
                    .filter(edge -> isCanonicalLocalEdge(edge, segment.index(), nodeById, canonicalSegmentIndexByNode))
                    .sorted(edgeOrder())
                    .toList();
            Map<RouteCardNodeId, Vec2> localPositions = this.localNodePositions(nodes, edges, spacing);
            List<RouteCardVisualEdge> localEdges = this.visualEdgesFor(edges, localPositions, nodeById, List.of(), spacing);
            Aabb2 nodeBounds = Aabb2.empty();
            for (RouteCardNode node : nodes) {
                Vec2 point = localPositions.get(node.id());
                if (point != null) {
                    nodeBounds = nodeBounds.include(nodeBounds(node, point));
                }
            }
            Aabb2 edgeBounds = Aabb2.empty();
            for (RouteCardVisualEdge edge : localEdges) {
                edgeBounds = edgeBounds.include(edge.bounds());
            }
            Aabb2 bodyBounds = nodeBounds.include(edgeBounds);
            if (bodyBounds.isEmpty()) {
                bodyBounds = Aabb2.around(0.0D, 0.0D, 1.0D);
            }
            Aabb2 footprint = this.segmentFootprint(nodes, localPositions, bodyBounds);
            RouteCardNode entry = stationNodes.getFirst();
            RouteCardNode exit = stationNodes.getLast();
            bodies.add(new SegmentBody(segment, localPositions, localEdges, footprint, entry, exit));
        }
        return bodies;
    }

    private Map<RouteCardNodeId, Vec2> localNodePositions(List<RouteCardNode> nodes, List<RouteCardEdge> edges, RouteCardSpacing spacing) {
        List<RouteCardNode> anchoredNodes = nodes.stream()
                .filter(node -> !isBoundaryPort(node))
                .toList();
        if (anchoredNodes.isEmpty()) {
            anchoredNodes = nodes;
        }
        Map<RouteCardNodeId, Vec2> positions = this.metroLocalNodePositions(anchoredNodes, edges, spacing);
        this.relaxLocalNodes(anchoredNodes, positions, spacing);
        this.placeFoldBoundaryPorts(nodes, edges, positions, spacing);
        return positions;
    }

    private Map<RouteCardNodeId, Vec2> metroLocalNodePositions(List<RouteCardNode> nodes, List<RouteCardEdge> edges, RouteCardSpacing spacing) {
        Map<RouteCardNodeId, Vec2> positions = new LinkedHashMap<>();
        if (nodes.isEmpty()) {
            return positions;
        }
        List<RouteCardNode> ordered = routeOrder(nodes, edges);
        RouteCardNode first = ordered.getFirst();
        positions.put(first.id(), new Vec2(0.0D, 0.0D));
        Vec2 previousDirection = new Vec2(1.0D, 0.0D);
        List<List<Vec2>> placedSegments = new ArrayList<>();
        for (int i = 1; i < ordered.size(); i++) {
            RouteCardNode previous = ordered.get(i - 1);
            RouteCardNode node = ordered.get(i);
            Vec2 previousPoint = positions.get(previous.id());
            if (previousPoint == null) {
                previousPoint = positions.values().stream().findFirst().orElse(new Vec2(0.0D, 0.0D));
            }
            double step = localStep(previous, node, spacing);
            Vec2 preferred = nearestCardDirection(node.worldX() - previous.worldX(), node.worldZ() - previous.worldZ(), previousDirection);
            Vec2 best = null;
            Vec2 bestDirection = preferred;
            double bestScore = Double.POSITIVE_INFINITY;
            for (Vec2 direction : cardDirectionCandidates(preferred, previousDirection)) {
                Vec2 candidate = new Vec2(previousPoint.x() + direction.x() * step, previousPoint.y() + direction.y() * step);
                double score = candidateScore(node, candidate, previousPoint, direction, preferred, previousDirection, positions, placedSegments, spacing);
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                    bestDirection = direction;
                }
            }
            positions.put(node.id(), best == null ? new Vec2(previousPoint.x() + preferred.x() * step, previousPoint.y() + preferred.y() * step) : best);
            placedSegments.add(List.of(previousPoint, positions.get(node.id())));
            previousDirection = bestDirection;
        }

        Aabb2 worldBounds = worldBounds(nodes);
        int unplaced = 0;
        for (RouteCardNode node : nodes.stream().sorted(Comparator.comparingInt(RouteCardNode::layoutOccurrence).thenComparing(RouteCardNode::id)).toList()) {
            if (positions.containsKey(node.id())) {
                continue;
            }
            double x = node.worldX() - worldBounds.centerX();
            double y = node.worldZ() - worldBounds.centerY();
            double angle = hashAngle(node.id(), node.id()) + unplaced * 0.9D;
            positions.put(node.id(), new Vec2(x + Math.cos(angle) * spacing.stationSpacing() * 0.35D, y + Math.sin(angle) * spacing.stationSpacing() * 0.35D));
            unplaced++;
        }
        return centerPositions(positions);
    }

    private static List<RouteCardNode> routeOrder(List<RouteCardNode> nodes, List<RouteCardEdge> edges) {
        Map<RouteCardNodeId, RouteCardNode> nodeById = nodeIndex(nodes);
        List<RouteCardNode> edgeOrder = new ArrayList<>();
        for (RouteCardEdge edge : edges.stream().filter(edge -> nodeById.containsKey(edge.from()) && nodeById.containsKey(edge.to())).sorted(edgeOrder()).toList()) {
            RouteCardNode from = nodeById.get(edge.from());
            RouteCardNode to = nodeById.get(edge.to());
            if (from == null || to == null) {
                continue;
            }
            if (edgeOrder.isEmpty()) {
                edgeOrder.add(from);
                edgeOrder.add(to);
                continue;
            }
            RouteCardNode last = edgeOrder.getLast();
            if (last.id().equals(from.id()) && edgeOrder.stream().noneMatch(node -> node.id().equals(to.id()))) {
                edgeOrder.add(to);
            } else if (last.id().equals(to.id()) && edgeOrder.stream().noneMatch(node -> node.id().equals(from.id()))) {
                edgeOrder.add(from);
            }
        }
        for (RouteCardNode node : nodes.stream().sorted(Comparator.comparingInt(RouteCardNode::layoutOccurrence).thenComparing(RouteCardNode::id)).toList()) {
            if (edgeOrder.stream().noneMatch(existing -> existing.id().equals(node.id()))) {
                edgeOrder.add(node);
            }
        }
        return edgeOrder.isEmpty() ? nodes.stream().sorted(Comparator.comparingInt(RouteCardNode::layoutOccurrence).thenComparing(RouteCardNode::id)).toList() : edgeOrder;
    }

    private static double localStep(RouteCardNode previous, RouteCardNode node, RouteCardSpacing spacing) {
        if (previous.stationGroupId().isPresent() && previous.stationGroupId().equals(node.stationGroupId())) {
            return spacing.stationSpacing() * 0.56D;
        }
        if (previous.kind() != RouteCardNodeKind.STATION || node.kind() != RouteCardNodeKind.STATION) {
            return spacing.stationSpacing() * 0.72D;
        }
        return spacing.stationSpacing();
    }

    private static double candidateScore(RouteCardNode node, Vec2 candidate, Vec2 previousPoint, Vec2 direction, Vec2 preferred, Vec2 previousDirection, Map<RouteCardNodeId, Vec2> positions, List<List<Vec2>> placedSegments, RouteCardSpacing spacing) {
        double score = (1.0D - dot(direction, preferred)) * 80.0D + (1.0D - Math.max(-0.4D, dot(direction, previousDirection))) * 18.0D;
        Aabb2 candidateBounds = nodeBounds(node, candidate).inflate(5.0D);
        for (Map.Entry<RouteCardNodeId, Vec2> entry : positions.entrySet()) {
            double distance = candidate.distanceTo(entry.getValue());
            double minDistance = nodeRadius(node) * 2.0D + spacing.localNodeGap() + 4.0D;
            if (distance < minDistance) {
                score += 8_000.0D + (minDistance - distance) * 120.0D;
            }
        }
        List<Vec2> candidateSegment = List.of(previousPoint, candidate);
        for (List<Vec2> segment : placedSegments) {
            if (segment.getLast().distanceTo(previousPoint) < 0.5D || segment.getFirst().distanceTo(previousPoint) < 0.5D) {
                continue;
            }
            if (pathsIntersect(candidateSegment, segment)) {
                score += 5_000.0D;
            }
            double distance = sampledPathDistance(candidateSegment, segment);
            if (distance < spacing.routeEdgeSeparation()) {
                score += 750.0D + (spacing.routeEdgeSeparation() - distance) * 70.0D;
            }
        }
        if (candidateBounds.contains(previousPoint.x(), previousPoint.y())) {
            score += 10_000.0D;
        }
        return score;
    }

    private static List<Vec2> cardDirectionCandidates(Vec2 preferred, Vec2 previous) {
        List<Vec2> result = new ArrayList<>();
        addUniqueDirection(result, preferred);
        addUniqueDirection(result, previous);
        for (double angle : List.of(Math.toRadians(45.0D), Math.toRadians(-45.0D), Math.toRadians(90.0D), Math.toRadians(-90.0D), Math.toRadians(135.0D), Math.toRadians(-135.0D), Math.PI)) {
            addUniqueDirection(result, nearestCardDirection(rotate(preferred, angle).x(), rotate(preferred, angle).y(), preferred));
        }
        return result;
    }

    private static void addUniqueDirection(List<Vec2> result, Vec2 direction) {
        for (Vec2 existing : result) {
            if (dot(existing, direction) > 0.999D) {
                return;
            }
        }
        result.add(direction);
    }

    private static Vec2 nearestCardDirection(double dx, double dy, Vec2 fallback) {
        double length = Math.hypot(dx, dy);
        if (length < 0.001D) {
            dx = fallback.x();
            dy = fallback.y();
            length = Math.hypot(dx, dy);
        }
        if (length < 0.001D) {
            return new Vec2(1.0D, 0.0D);
        }
        double ux = dx / length;
        double uy = dy / length;
        Vec2 best = CARD_DIRECTIONS.getFirst();
        double bestDot = Double.NEGATIVE_INFINITY;
        for (Vec2 direction : CARD_DIRECTIONS) {
            double dot = ux * direction.x() + uy * direction.y();
            if (dot > bestDot) {
                bestDot = dot;
                best = direction;
            }
        }
        return best;
    }

    private static Map<RouteCardNodeId, Vec2> centerPositions(Map<RouteCardNodeId, Vec2> positions) {
        Aabb2 bounds = boundsForPositions(positions);
        if (bounds.isEmpty()) {
            return positions;
        }
        Map<RouteCardNodeId, Vec2> centered = new LinkedHashMap<>();
        for (Map.Entry<RouteCardNodeId, Vec2> entry : positions.entrySet()) {
            centered.put(entry.getKey(), new Vec2(entry.getValue().x() - bounds.centerX(), entry.getValue().y() - bounds.centerY()));
        }
        return centered;
    }

    private void placeFoldBoundaryPorts(List<RouteCardNode> nodes, List<RouteCardEdge> edges, Map<RouteCardNodeId, Vec2> positions, RouteCardSpacing spacing) {
        List<RouteCardNode> boundaries = nodes.stream()
                .filter(RouteCardLayoutSolver::isBoundaryPort)
                .sorted(Comparator.comparingInt(RouteCardNode::layoutOccurrence).thenComparing(RouteCardNode::id))
                .toList();
        if (boundaries.isEmpty()) {
            return;
        }
        Aabb2 anchorBounds = boundsForPositions(positions);
        Vec2 center = new Vec2(anchorBounds.centerX(), anchorBounds.centerY());
        List<Aabb2> blockers = nodes.stream()
                .filter(node -> !isBoundaryPort(node))
                .map(node -> {
                    Vec2 point = positions.get(node.id());
                    return point == null ? Aabb2.empty() : nodeBounds(node, point).inflate(5.0D);
                })
                .filter(bounds -> !bounds.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<Aabb2> placedPorts = new ArrayList<>();
        for (RouteCardNode boundary : boundaries) {
            BoundaryPortContext context = this.boundaryPortContext(boundary, edges, positions, center);
            Vec2 position = this.bestBoundaryPortPosition(boundary, context, edges, positions, anchorBounds, blockers, placedPorts, spacing);
            positions.put(boundary.id(), position);
            Aabb2 portBounds = nodeBounds(boundary, position).inflate(4.0D);
            blockers.add(portBounds);
            placedPorts.add(portBounds);
        }
    }

    private BoundaryPortContext boundaryPortContext(RouteCardNode boundary, List<RouteCardEdge> edges, Map<RouteCardNodeId, Vec2> positions, Vec2 center) {
        List<RouteCardEdge> incident = edges.stream()
                .filter(edge -> edge.from().equals(boundary.id()) || edge.to().equals(boundary.id()))
                .sorted(edgeOrder())
                .toList();
        List<Vec2> adjacent = new ArrayList<>();
        for (RouteCardEdge edge : incident) {
            RouteCardNodeId other = edge.from().equals(boundary.id()) ? edge.to() : edge.from();
            Vec2 point = positions.get(other);
            if (point != null) {
                adjacent.add(point);
            }
        }
        Vec2 base = adjacent.isEmpty()
                ? center
                : new Vec2(adjacent.stream().mapToDouble(Vec2::x).average().orElse(center.x()), adjacent.stream().mapToDouble(Vec2::y).average().orElse(center.y()));
        Vec2 direction = normalize(base.x() - center.x(), base.y() - center.y());
        if (isZero(direction)) {
            direction = incident.stream()
                    .map(edge -> routeDirectionForBoundary(boundary.id(), edge, positions))
                    .filter(vector -> !isZero(vector))
                    .findFirst()
                    .orElseGet(() -> hashDirection(boundary.id()));
        }
        return new BoundaryPortContext(base, direction);
    }

    private Vec2 bestBoundaryPortPosition(RouteCardNode boundary, BoundaryPortContext context, List<RouteCardEdge> edges, Map<RouteCardNodeId, Vec2> positions, Aabb2 anchorBounds, List<Aabb2> blockers, List<Aabb2> placedPorts, RouteCardSpacing spacing) {
        double distance = spacing.boundaryPortDistance();
        Vec2 best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        List<List<Vec2>> routeBlockers = this.boundaryRouteBlockers(boundary, edges, positions, spacing);
        for (double rotation : boundaryPortRotations()) {
            Vec2 direction = rotate(context.direction(), rotation);
            Vec2 tangent = new Vec2(-direction.y(), direction.x());
            for (int lane : boundaryPortLanes()) {
                Vec2 candidate = new Vec2(
                        context.base().x() + direction.x() * distance + tangent.x() * lane * spacing.boundaryPortSpacing(),
                        context.base().y() + direction.y() * distance + tangent.y() * lane * spacing.boundaryPortSpacing());
                double score = Math.abs(rotation) * 32.0D + Math.abs(lane) * 14.0D + candidate.distanceTo(context.base()) * 0.06D;
                if (anchorBounds.inflate(7.0D).contains(candidate.x(), candidate.y())) {
                    score += 250.0D;
                }
                Aabb2 candidateBounds = nodeBounds(boundary, candidate).inflate(5.0D);
                for (Aabb2 blocker : blockers) {
                    if (candidateBounds.intersects(blocker)) {
                        score += 9_000.0D + overlapArea(candidateBounds, blocker) * 8.0D;
                    }
                }
                for (Aabb2 port : placedPorts) {
                    if (candidateBounds.intersects(port)) {
                        score += 6_000.0D + overlapArea(candidateBounds, port) * 6.0D;
                    }
                }
                score += this.boundaryRoutePenalty(boundary, edges, positions, candidate, routeBlockers, spacing);
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best == null ? new Vec2(context.base().x() + context.direction().x() * distance, context.base().y() + context.direction().y() * distance) : best;
    }

    private List<List<Vec2>> boundaryRouteBlockers(RouteCardNode boundary, List<RouteCardEdge> edges, Map<RouteCardNodeId, Vec2> positions, RouteCardSpacing spacing) {
        List<List<Vec2>> blockers = new ArrayList<>();
        for (RouteCardEdge edge : edges) {
            if (edge.segmentIndex() < 0 || edge.from().equals(boundary.id()) || edge.to().equals(boundary.id())) {
                continue;
            }
            Vec2 a = positions.get(edge.from());
            Vec2 b = positions.get(edge.to());
            if (a == null || b == null || a.distanceTo(b) < 1.0D) {
                continue;
            }
            List<List<Vec2>> candidates = this.edgeCandidates(edge, a, b, spacing);
            if (candidates.isEmpty()) {
                blockers.add(List.of(a, b));
            } else {
                blockers.add(candidates.getFirst());
            }
        }
        return blockers;
    }

    private double boundaryRoutePenalty(RouteCardNode boundary, List<RouteCardEdge> edges, Map<RouteCardNodeId, Vec2> positions, Vec2 candidate, List<List<Vec2>> routeBlockers, RouteCardSpacing spacing) {
        if (routeBlockers.isEmpty()) {
            return 0.0D;
        }
        double penalty = 0.0D;
        for (RouteCardEdge edge : edges) {
            if (edge.segmentIndex() < 0) {
                continue;
            }
            RouteCardNodeId otherId;
            if (edge.from().equals(boundary.id())) {
                otherId = edge.to();
            } else if (edge.to().equals(boundary.id())) {
                otherId = edge.from();
            } else {
                continue;
            }
            Vec2 other = positions.get(otherId);
            if (other == null || other.distanceTo(candidate) < 1.0D) {
                continue;
            }
            List<Vec2> candidatePath = trimmedSegment(other, candidate, 10.0D, 4.0D);
            for (List<Vec2> blocker : routeBlockers) {
                if (blocker.size() < 2) {
                    continue;
                }
                double distance = sampledPathDistance(candidatePath, blocker);
                double clearance = spacing.routeEdgeSeparation() + FullRouteMapConfig.LINE_WIDTH_PX + 2.0D;
                if (distance < clearance) {
                    double miss = clearance - distance;
                    penalty += 1_800.0D + miss * miss * 12.0D;
                }
                if (pathsIntersect(candidatePath, blocker)) {
                    penalty += 7_000.0D;
                }
            }
        }
        return penalty;
    }

    private void relaxLocalNodes(List<RouteCardNode> nodes, Map<RouteCardNodeId, Vec2> positions, RouteCardSpacing spacing) {
        if (nodes.size() < 2) {
            return;
        }
        Map<RouteCardNodeId, Vec2> anchors = new LinkedHashMap<>(positions);
        for (int iteration = 0; iteration < LOCAL_RELAX_ITERATIONS; iteration++) {
            Map<RouteCardNodeId, Vec2> deltas = new LinkedHashMap<>();
            for (RouteCardNode node : nodes) {
                deltas.put(node.id(), new Vec2(0.0D, 0.0D));
            }
            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    RouteCardNode first = nodes.get(i);
                    RouteCardNode second = nodes.get(j);
                    Vec2 a = positions.get(first.id());
                    Vec2 b = positions.get(second.id());
                    double minGap = nodeRadius(first) + nodeRadius(second) + spacing.localNodeGap();
                    double dx = b.x() - a.x();
                    double dy = b.y() - a.y();
                    double distance = Math.hypot(dx, dy);
                    if (distance >= minGap) {
                        continue;
                    }
                    if (distance < 0.001D) {
                        double angle = hashAngle(first.id(), second.id());
                        dx = Math.cos(angle);
                        dy = Math.sin(angle);
                        distance = 1.0D;
                    }
                    double push = (minGap - distance) * 0.52D;
                    double ux = dx / distance;
                    double uy = dy / distance;
                    deltas.put(first.id(), add(deltas.get(first.id()), -ux * push, -uy * push));
                    deltas.put(second.id(), add(deltas.get(second.id()), ux * push, uy * push));
                }
            }
            for (RouteCardNode node : nodes) {
                Vec2 position = positions.get(node.id());
                Vec2 delta = deltas.get(node.id());
                Vec2 anchor = anchors.get(node.id());
                positions.put(node.id(), new Vec2(
                        position.x() + delta.x() + (anchor.x() - position.x()) * 0.10D,
                        position.y() + delta.y() + (anchor.y() - position.y()) * 0.10D));
            }
        }
    }

    private Aabb2 segmentFootprint(List<RouteCardNode> nodes, Map<RouteCardNodeId, Vec2> positions, Aabb2 bodyBounds) {
        Aabb2 labels = Aabb2.empty();
        for (RouteCardNode node : nodes) {
            Vec2 point = positions.get(node.id());
            if (point != null) {
                labels = labels.include(labelReservation(node, point));
            }
        }
        Aabb2 chip = new Aabb2(
                bodyBounds.minX(),
                bodyBounds.minY() - DIMENSION_CHIP_HEIGHT - 8.0D,
                bodyBounds.minX() + DIMENSION_CHIP_WIDTH,
                bodyBounds.minY() - 8.0D);
        return bodyBounds.include(labels).include(chip).inflate(SEGMENT_BODY_PADDING);
    }

    private SegmentPlacement placeSegmentBodies(List<SegmentBody> bodies, RouteCardSpacing spacing) {
        List<PlacedSegmentBody> placed = new ArrayList<>();
        SegmentBody first = bodies.getFirst();
        placed.add(new PlacedSegmentBody(first, -first.footprint().centerX(), -first.footprint().centerY()));
        for (int i = 1; i < bodies.size(); i++) {
            placed.add(this.bestPlacementFor(bodies.get(i), placed, spacing));
        }

        placed = this.resolveSegmentCollisions(placed, spacing);
        boolean fallback = false;
        if (this.hasSegmentCollisions(placed, spacing)) {
            placed = this.shelfPack(bodies, spacing);
            fallback = true;
        }
        return new SegmentPlacement(this.centerPlacedSegments(placed), fallback);
    }

    private PlacedSegmentBody bestPlacementFor(SegmentBody body, List<PlacedSegmentBody> placed, RouteCardSpacing spacing) {
        PlacedSegmentBody previous = placed.getLast();
        Vec2 previousExit = previous.positionOf(previous.body().exitNode().id());
        Vec2 entry = body.localPositions().getOrDefault(body.entryNode().id(), new Vec2(body.footprint().centerX(), body.footprint().centerY()));
        Vec2 direction = segmentDirection(previous.body(), body);
        double baseGap = Math.max(spacing.segmentPlacementGap(), Math.max(maxDimension(previous.body().footprint()), maxDimension(body.footprint())) * 0.30D + spacing.segmentClearance());
        PlacementCandidate bestValid = null;
        PlacementCandidate bestAny = null;
        for (double gapMultiplier : gapMultipliers()) {
            double gap = baseGap * gapMultiplier;
            for (double rotation : segmentRotations()) {
                Vec2 rotated = rotate(direction, rotation);
                Vec2 target = new Vec2(previousExit.x() + rotated.x() * gap, previousExit.y() + rotated.y() * gap);
                PlacementCandidate candidate = this.candidate(body, entry, target, previousExit, placed, rotation, gap, spacing);
                bestAny = better(bestAny, candidate);
                if (candidate.overlapScore() <= 0.001D) {
                    bestValid = better(bestValid, candidate);
                }
            }
        }
        Vec2 ideal = new Vec2(previousExit.x() + direction.x() * baseGap, previousExit.y() + direction.y() * baseGap);
        for (double radius : spiralRadii(baseGap)) {
            int samples = radius < baseGap * 1.5D ? 16 : 28;
            for (int i = 0; i < samples; i++) {
                double angle = Math.PI * 2.0D * i / samples + (body.segment().index() % 5) * 0.17D;
                Vec2 target = new Vec2(ideal.x() + Math.cos(angle) * radius, ideal.y() + Math.sin(angle) * radius);
                PlacementCandidate candidate = this.candidate(body, entry, target, previousExit, placed, angle, radius / baseGap, spacing);
                bestAny = better(bestAny, candidate);
                if (candidate.overlapScore() <= 0.001D) {
                    bestValid = better(bestValid, candidate);
                }
            }
        }
        return (bestValid != null ? bestValid : bestAny).placed();
    }

    private PlacementCandidate candidate(SegmentBody body, Vec2 entryLocal, Vec2 target, Vec2 previousExit, List<PlacedSegmentBody> placed, double angleDeviation, double gapCost, RouteCardSpacing spacing) {
        PlacedSegmentBody candidate = new PlacedSegmentBody(body, target.x() - entryLocal.x(), target.y() - entryLocal.y());
        Aabb2 inflated = candidate.footprint().inflate(spacing.segmentClearance() * 0.5D);
        double overlap = 0.0D;
        Aabb2 existingBounds = Aabb2.empty();
        for (PlacedSegmentBody placedSegment : placed) {
            Aabb2 placedInflated = placedSegment.footprint().inflate(spacing.segmentClearance() * 0.5D);
            overlap += overlapArea(inflated, placedInflated);
            existingBounds = existingBounds.include(placedSegment.footprint());
        }
        Vec2 entry = candidate.positionOf(body.entryNode().id());
        Aabb2 union = existingBounds.include(candidate.footprint());
        double growth = area(union) - area(existingBounds);
        double connectorLength = entry.distanceTo(previousExit);
        double score = overlap * 25_000.0D
                + connectorLength * 0.36D
                + growth * 0.0016D
                + Math.abs(angleDeviation) * 38.0D
                + Math.abs(gapCost - 1.0D) * 16.0D
                + Math.hypot(candidate.footprint().centerX(), candidate.footprint().centerY()) * 0.025D;
        return new PlacementCandidate(candidate, score, overlap);
    }

    private List<PlacedSegmentBody> resolveSegmentCollisions(List<PlacedSegmentBody> initial, RouteCardSpacing spacing) {
        List<MutablePlacement> mutable = initial.stream()
                .map(placed -> new MutablePlacement(placed.body(), placed.offsetX(), placed.offsetY()))
                .toList();
        for (int iteration = 0; iteration < GLOBAL_COLLISION_ITERATIONS; iteration++) {
            boolean moved = false;
            for (int i = 0; i < mutable.size(); i++) {
                for (int j = i + 1; j < mutable.size(); j++) {
                    MutablePlacement first = mutable.get(i);
                    MutablePlacement second = mutable.get(j);
                    Aabb2 a = first.footprint().inflate(spacing.segmentClearance() * 0.5D);
                    Aabb2 b = second.footprint().inflate(spacing.segmentClearance() * 0.5D);
                    if (!a.intersects(b)) {
                        continue;
                    }
                    Vec2 separation = separationVectorForSecond(a, b);
                    double firstWeight = i == 0 ? 0.0D : 0.48D;
                    double secondWeight = i == 0 ? 1.0D : 0.52D;
                    first.move(-separation.x() * firstWeight, -separation.y() * firstWeight);
                    second.move(separation.x() * secondWeight, separation.y() * secondWeight);
                    moved = true;
                }
            }
            if (!moved) {
                break;
            }
        }
        return mutable.stream().map(MutablePlacement::placed).toList();
    }

    private boolean hasSegmentCollisions(List<PlacedSegmentBody> placed, RouteCardSpacing spacing) {
        for (int i = 0; i < placed.size(); i++) {
            Aabb2 a = placed.get(i).footprint().inflate(spacing.segmentClearance() * 0.5D);
            for (int j = i + 1; j < placed.size(); j++) {
                if (a.intersects(placed.get(j).footprint().inflate(spacing.segmentClearance() * 0.5D))) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<PlacedSegmentBody> shelfPack(List<SegmentBody> bodies, RouteCardSpacing spacing) {
        double totalArea = bodies.stream()
                .mapToDouble(body -> Math.max(1.0D, width(body.footprint()) + spacing.segmentClearance()) * Math.max(1.0D, height(body.footprint()) + spacing.segmentClearance()))
                .sum();
        double widest = bodies.stream().mapToDouble(body -> width(body.footprint()) + spacing.segmentClearance()).max().orElse(spacing.segmentPlacementGap());
        double targetWidth = Math.max(widest * 1.75D, Math.sqrt(totalArea) * 1.35D);
        List<PlacedSegmentBody> placed = new ArrayList<>();
        double cursorX = 0.0D;
        double cursorY = 0.0D;
        double shelfHeight = 0.0D;
        for (SegmentBody body : bodies.stream().sorted(Comparator.comparingInt(value -> value.segment().index())).toList()) {
            double bodyWidth = width(body.footprint());
            double bodyHeight = height(body.footprint());
            if (!placed.isEmpty() && cursorX + bodyWidth > targetWidth) {
                cursorX = 0.0D;
                cursorY += shelfHeight + spacing.segmentClearance();
                shelfHeight = 0.0D;
            }
            double offsetX = cursorX - body.footprint().minX();
            double offsetY = cursorY - body.footprint().minY();
            placed.add(new PlacedSegmentBody(body, offsetX, offsetY));
            cursorX += bodyWidth + spacing.segmentClearance();
            shelfHeight = Math.max(shelfHeight, bodyHeight);
        }
        return placed;
    }

    private List<PlacedSegmentBody> centerPlacedSegments(List<PlacedSegmentBody> placed) {
        Aabb2 bounds = Aabb2.empty();
        for (PlacedSegmentBody body : placed) {
            bounds = bounds.include(body.footprint());
        }
        if (bounds.isEmpty()) {
            return placed;
        }
        double dx = -bounds.centerX();
        double dy = -bounds.centerY();
        return placed.stream()
                .map(body -> new PlacedSegmentBody(body.body(), body.offsetX() + dx, body.offsetY() + dy))
                .toList();
    }

    private void placeUnassignedNodes(RouteCardSemanticGraph graph, Map<RouteCardNodeId, Vec2> positions, List<PlacedSegmentBody> placed) {
        Aabb2 currentBounds = Aabb2.empty();
        for (Vec2 point : positions.values()) {
            currentBounds = currentBounds.include(point.x(), point.y());
        }
        Aabb2 worldBounds = worldBounds(graph.nodes());
        int unassignedIndex = 0;
        for (RouteCardNode node : graph.nodes().stream().sorted(Comparator.comparingInt(RouteCardNode::layoutOccurrence)).toList()) {
            if (positions.containsKey(node.id())) {
                continue;
            }
            List<Vec2> connected = new ArrayList<>();
            for (RouteCardEdge edge : graph.edges()) {
                if (edge.from().equals(node.id())) {
                    Vec2 point = positions.get(edge.to());
                    if (point != null) {
                        connected.add(point);
                    }
                } else if (edge.to().equals(node.id())) {
                    Vec2 point = positions.get(edge.from());
                    if (point != null) {
                        connected.add(point);
                    }
                }
            }
            Vec2 point;
            if (!connected.isEmpty()) {
                double x = connected.stream().mapToDouble(Vec2::x).average().orElse(0.0D);
                double y = connected.stream().mapToDouble(Vec2::y).average().orElse(0.0D);
                double angle = hashAngle(node.id(), node.id()) + unassignedIndex * 0.83D;
                point = new Vec2(x + Math.cos(angle) * 34.0D, y + Math.sin(angle) * 34.0D);
            } else if (!currentBounds.isEmpty()) {
                double x = node.worldX() - worldBounds.centerX() + currentBounds.centerX();
                double y = node.worldZ() - worldBounds.centerY() + currentBounds.maxY() + 48.0D;
                point = new Vec2(x, y);
            } else if (!placed.isEmpty()) {
                point = new Vec2(placed.getFirst().footprint().centerX(), placed.getFirst().footprint().centerY());
            } else {
                point = new Vec2(0.0D, 0.0D);
            }
            positions.put(node.id(), point);
            currentBounds = currentBounds.include(nodeBounds(node, point));
            unassignedIndex++;
        }
    }

    private List<RouteCardVisualEdge> visualEdgesFor(List<RouteCardEdge> edges, Map<RouteCardNodeId, Vec2> positions, Map<RouteCardNodeId, RouteCardNode> nodeById, List<Aabb2> obstacles, RouteCardSpacing spacing) {
        List<RoutedEdge> routed = new ArrayList<>();
        List<RouteCardVisualEdge> result = new ArrayList<>();
        for (RouteCardEdge edge : edges) {
            RouteCardVisualEdge visualEdge = this.visualEdge(edge, positions, nodeById, routed, obstacles, spacing);
            result.add(visualEdge);
            if (!visualEdge.points().isEmpty()) {
                routed.add(new RoutedEdge(edge, visualEdge.points()));
            }
        }
        return result;
    }

    private RouteCardVisualEdge visualEdge(RouteCardEdge edge, Map<RouteCardNodeId, Vec2> positions, Map<RouteCardNodeId, RouteCardNode> nodesById, List<RoutedEdge> routedEdges, List<Aabb2> obstacles, RouteCardSpacing spacing) {
        Vec2 a = positions.get(edge.from());
        Vec2 b = positions.get(edge.to());
        if (a == null || b == null) {
            return new RouteCardVisualEdge(edge, List.of(), Aabb2.empty());
        }
        List<Vec2> direct = List.of(a, b);
        if (this.preferDirectRoute(edge, direct, positions, nodesById, routedEdges, obstacles, spacing)) {
            return new RouteCardVisualEdge(edge, direct, boundsFor(direct).inflate(edgeFootprintPadding(edge)));
        }
        List<List<Vec2>> candidates = new ArrayList<>(this.edgeCandidates(edge, a, b, spacing));
        candidates.addAll(this.connectorCandidates(edge, a, b, obstacles, spacing));
        List<Vec2> best = List.of();
        double bestScore = Double.POSITIVE_INFINITY;
        for (List<Vec2> candidate : candidates) {
            if (candidate.size() < 2) {
                continue;
            }
            double score = this.routeScore(edge, candidate, nodesById, positions, routedEdges, obstacles, spacing);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return new RouteCardVisualEdge(edge, best, boundsFor(best).inflate(edgeFootprintPadding(edge)));
    }

    private boolean preferDirectRoute(RouteCardEdge edge, List<Vec2> direct, Map<RouteCardNodeId, Vec2> positions, Map<RouteCardNodeId, RouteCardNode> nodesById, List<RoutedEdge> routedEdges, List<Aabb2> obstacles, RouteCardSpacing spacing) {
        if (edge.kind() == SemanticEdgeKind.STATION_INTERNAL || edge.kind() == SemanticEdgeKind.LOOP_BACK || edge.loopBack()) {
            return false;
        }
        if (!isOctilinearPath(direct)) {
            return false;
        }
        for (Map.Entry<RouteCardNodeId, Vec2> entry : positions.entrySet()) {
            RouteCardNodeId nodeId = entry.getKey();
            if (nodeId.equals(edge.from()) || nodeId.equals(edge.to())) {
                continue;
            }
            RouteCardNode node = nodesById.get(nodeId);
            if (node == null) {
                continue;
            }
            if (distanceToPolyline(entry.getValue(), direct) < nodeRadius(node) + spacing.routeEdgeClearance()) {
                return false;
            }
        }
        for (Aabb2 obstacle : obstacles) {
            if (pathIntersectsAabb(direct, obstacle)) {
                return false;
            }
        }
        for (RoutedEdge routed : routedEdges) {
            if (sharesEndpoint(edge, routed.edge())) {
                continue;
            }
            if (pathsIntersect(direct, routed.points())) {
                return false;
            }
        }
        return true;
    }

    private RouteCardVisualEdge translateEdge(RouteCardVisualEdge edge, double dx, double dy) {
        List<Vec2> points = edge.points().stream()
                .map(point -> new Vec2(point.x() + dx, point.y() + dy))
                .toList();
        return new RouteCardVisualEdge(edge.edge(), points, translate(edge.bounds(), dx, dy));
    }

    private List<List<Vec2>> edgeCandidates(RouteCardEdge edge, Vec2 a, Vec2 b, RouteCardSpacing spacing) {
        if (edge.from().equals(edge.to()) || a.distanceTo(b) < 1.0D) {
            return List.of();
        }
        List<List<Vec2>> candidates = new ArrayList<>();
        if (edge.kind() == SemanticEdgeKind.STATION_INTERNAL) {
            double preferredSide = edge.loopBack() ? 1.0D : hashSide(edge.id());
            candidates.add(stationInternalPath(a, b, preferredSide, edge.loopBack() ? 0.28D : 0.16D));
            candidates.add(stationInternalPath(a, b, -preferredSide, edge.loopBack() ? 0.28D : 0.16D));
            candidates.add(stationInternalPath(a, b, preferredSide, 0.40D));
            candidates.add(stationInternalPath(a, b, -preferredSide, 0.40D));
            candidates.add(stationInternalPath(a, b, preferredSide, 0.62D));
            candidates.add(stationInternalPath(a, b, -preferredSide, 0.62D));
            return candidates;
        }
        if (edge.kind() == SemanticEdgeKind.LOOP_BACK || edge.loopBack()) {
            candidates.add(curvedPath(a, b, 1.0D, 0.36D));
            candidates.add(curvedPath(a, b, -1.0D, 0.36D));
            candidates.add(curvedPath(a, b, 1.0D, 0.52D));
            candidates.add(curvedPath(a, b, -1.0D, 0.52D));
            candidates.add(curvedPath(a, b, 1.0D, 0.76D));
            candidates.add(curvedPath(a, b, -1.0D, 0.76D));
            return candidates;
        }
        if (edge.kind() == SemanticEdgeKind.FOLD_ADJACENT) {
            candidates.addAll(octilinearEdgeCandidates(a, b, spacing, 0.18D));
            return candidates.stream().distinct().toList();
        }
        candidates.addAll(octilinearEdgeCandidates(a, b, spacing, 0.30D));
        return candidates.stream().distinct().toList();
    }

    private List<List<Vec2>> connectorCandidates(RouteCardEdge edge, Vec2 a, Vec2 b, List<Aabb2> obstacles, RouteCardSpacing spacing) {
        if (obstacles.isEmpty()) {
            return List.of();
        }
        Aabb2 union = Aabb2.empty();
        for (Aabb2 obstacle : obstacles) {
            union = union.include(obstacle);
        }
        List<List<Vec2>> candidates = new ArrayList<>();
        double midX = (a.x() + b.x()) * 0.5D;
        double midY = (a.y() + b.y()) * 0.5D;
        candidates.add(List.of(a, new Vec2(midX, a.y()), new Vec2(midX, b.y()), b));
        candidates.add(List.of(a, new Vec2(a.x(), midY), new Vec2(b.x(), midY), b));
        double side = hashSide(edge.id());
        double top = union.minY() - spacing.segmentClearance();
        double bottom = union.maxY() + spacing.segmentClearance();
        double left = union.minX() - spacing.segmentClearance();
        double right = union.maxX() + spacing.segmentClearance();
        candidates.add(List.of(a, new Vec2(a.x(), side > 0.0D ? top : bottom), new Vec2(b.x(), side > 0.0D ? top : bottom), b));
        candidates.add(List.of(a, new Vec2(side > 0.0D ? left : right, a.y()), new Vec2(side > 0.0D ? left : right, b.y()), b));
        candidates.add(List.of(a, new Vec2(left, a.y()), new Vec2(left, b.y()), b));
        candidates.add(List.of(a, new Vec2(right, a.y()), new Vec2(right, b.y()), b));
        return candidates;
    }

    private static List<List<Vec2>> octilinearEdgeCandidates(Vec2 a, Vec2 b, RouteCardSpacing spacing, double stubRatio) {
        List<List<Vec2>> candidates = new ArrayList<>();
        double direct = Math.max(1.0D, a.distanceTo(b));
        double minLeg = Math.max(7.0D, spacing.stationSpacing() * 0.08D);
        double maxLength = direct * 2.70D + spacing.stationSpacing() * 0.90D;
        Vec2 preferred = nearestCardDirection(b.x() - a.x(), b.y() - a.y());

        if (isOctilinearSegment(a, b)) {
            candidates.add(List.of(a, b));
        }
        for (Vec2 first : orderedCardDirections(preferred)) {
            for (Vec2 entry : orderedCardDirections(preferred)) {
                java.util.Optional<Vec2> corner = rayIntersection(a, first, b, reverse(entry));
                if (corner.isEmpty() || corner.get().distanceTo(a) < minLeg || corner.get().distanceTo(b) < minLeg) {
                    continue;
                }
                List<Vec2> path = dedupePath(List.of(a, corner.get(), b));
                if (polylineLength(path) <= maxLength) {
                    candidates.add(path);
                }
            }
        }

        double stub = Math.max(spacing.stationSpacing() * 0.18D, Math.min(spacing.stationSpacing() * 0.46D, direct * stubRatio));
        for (Vec2 start : orderedCardDirections(preferred).stream().limit(5).toList()) {
            for (Vec2 entry : orderedCardDirections(preferred).stream().limit(5).toList()) {
                Vec2 startStub = new Vec2(a.x() + start.x() * stub, a.y() + start.y() * stub);
                Vec2 endStub = new Vec2(b.x() - entry.x() * stub, b.y() - entry.y() * stub);
                for (List<Vec2> bridge : octilinearBridges(startStub, endStub, minLeg)) {
                    List<Vec2> path = new ArrayList<>();
                    path.add(a);
                    path.add(startStub);
                    path.addAll(bridge.subList(1, bridge.size()));
                    path.add(b);
                    path = dedupePath(path);
                    if (isOctilinearPath(path) && polylineLength(path) <= maxLength) {
                        candidates.add(path);
                    }
                }
            }
        }

        candidates.add(List.of(a, b));
        return candidates.stream()
                .map(RouteCardLayoutSolver::dedupePath)
                .filter(path -> path.size() >= 2)
                .distinct()
                .sorted(Comparator.comparingDouble(RouteCardLayoutSolver::candidateSeedScore))
                .limit(140)
                .toList();
    }

    private static List<List<Vec2>> octilinearBridges(Vec2 a, Vec2 b, double minLeg) {
        List<List<Vec2>> bridges = new ArrayList<>();
        if (a.distanceTo(b) < 1.0D) {
            return List.of(List.of(a, b));
        }
        if (isOctilinearSegment(a, b)) {
            bridges.add(List.of(a, b));
        }
        for (Vec2 first : CARD_DIRECTIONS) {
            for (Vec2 second : CARD_DIRECTIONS) {
                java.util.Optional<Vec2> corner = rayIntersection(a, first, b, reverse(second));
                if (corner.isPresent() && corner.get().distanceTo(a) >= minLeg && corner.get().distanceTo(b) >= minLeg) {
                    bridges.add(dedupePath(List.of(a, corner.get(), b)));
                }
            }
        }
        Vec2 horizontal = new Vec2(b.x(), a.y());
        if (horizontal.distanceTo(a) >= minLeg && horizontal.distanceTo(b) >= minLeg) {
            bridges.add(dedupePath(List.of(a, horizontal, b)));
        }
        Vec2 vertical = new Vec2(a.x(), b.y());
        if (vertical.distanceTo(a) >= minLeg && vertical.distanceTo(b) >= minLeg) {
            bridges.add(dedupePath(List.of(a, vertical, b)));
        }
        return bridges.stream()
                .filter(RouteCardLayoutSolver::isOctilinearPath)
                .distinct()
                .sorted(Comparator.comparingDouble(RouteCardLayoutSolver::candidateSeedScore))
                .limit(8)
                .toList();
    }

    private static double candidateSeedScore(List<Vec2> path) {
        return polylineLength(path)
                + Math.max(0, path.size() - 2) * 26.0D
                + directionPenalty(path) * 220.0D
                + (isOctilinearPath(path) ? 0.0D : 10_000.0D);
    }

    private double routeScore(RouteCardEdge edge, List<Vec2> points, Map<RouteCardNodeId, RouteCardNode> nodesById, Map<RouteCardNodeId, Vec2> positions, List<RoutedEdge> routedEdges, List<Aabb2> obstacles, RouteCardSpacing spacing) {
        double bendPenalty = spacing.mode() == RouteCardSolveMode.SCHEMATIC ? 15.0D : 7.0D;
        double directionPenalty = spacing.mode() == RouteCardSolveMode.SCHEMATIC ? 150.0D : 120.0D;
        double score = polylineLength(points) * 0.02D + Math.max(0, points.size() - 2) * bendPenalty + directionPenalty(points) * directionPenalty;
        if (!isOctilinearPath(points) && edge.kind() != SemanticEdgeKind.STATION_INTERNAL && edge.kind() != SemanticEdgeKind.LOOP_BACK && !edge.loopBack()) {
            score += 20_000.0D;
        }
        for (Map.Entry<RouteCardNodeId, Vec2> entry : positions.entrySet()) {
            RouteCardNodeId nodeId = entry.getKey();
            if (nodeId.equals(edge.from()) || nodeId.equals(edge.to())) {
                continue;
            }
            RouteCardNode node = nodesById.get(nodeId);
            if (node == null) {
                continue;
            }
            double clearance = nodeRadius(node) + spacing.routeEdgeClearance();
            double distance = distanceToPolyline(entry.getValue(), points);
            if (distance < clearance) {
                double miss = clearance - distance;
                score += 1_800.0D + miss * miss * 10.0D;
            }
        }
        for (RoutedEdge routed : routedEdges) {
            if (sharesEndpoint(edge, routed.edge())) {
                continue;
            }
            double distance = sampledPathDistance(points, routed.points());
            double separation = spacing.routeEdgeSeparation();
            if (distance < separation) {
                double miss = separation - distance;
                score += 900.0D + miss * miss * 7.0D;
            }
            if (pathsIntersect(points, routed.points())) {
                score += 4_000.0D;
            }
        }
        for (Aabb2 obstacle : obstacles) {
            if (pathIntersectsAabb(points, obstacle)) {
                score += 80_000.0D;
            }
            for (Vec2 point : points) {
                if (obstacle.contains(point.x(), point.y())) {
                    score += 40_000.0D;
                }
            }
        }
        return score;
    }

    private static List<Aabb2> obstaclesForConnector(RouteCardEdge edge, List<PlacedSegmentBody> placed, Map<RouteCardNodeId, SegmentBody> owningBodyByNode) {
        SegmentBody fromBody = owningBodyByNode.get(edge.from());
        SegmentBody toBody = owningBodyByNode.get(edge.to());
        List<Aabb2> obstacles = new ArrayList<>();
        for (PlacedSegmentBody body : placed) {
            if (body.body() == fromBody || body.body() == toBody) {
                continue;
            }
            obstacles.add(body.footprint().inflate(8.0D));
        }
        return obstacles;
    }

    private static RouteCardSpacing spacingFor(RouteCardSemanticGraph graph, RouteCardSolveMode mode) {
        List<RouteCardNode> stations = graph.nodes().stream()
                .filter(node -> node.kind() == RouteCardNodeKind.STATION)
                .sorted(Comparator.comparingInt(RouteCardNode::layoutOccurrence).thenComparing(RouteCardNode::id))
                .toList();
        List<Double> distances = new ArrayList<>();
        for (int i = 0; i + 1 < stations.size(); i++) {
            addStationDistance(distances, stations.get(i), stations.get(i + 1));
        }
        if (graph.summary().loop() && stations.size() > 2) {
            addStationDistance(distances, stations.getLast(), stations.getFirst());
        }
        double stationSpacing = mode == RouteCardSolveMode.SCHEMATIC
                ? DEFAULT_STATION_SPACING
                : distances.isEmpty()
                        ? DEFAULT_STATION_SPACING
                        : clamp(median(distances), MIN_STATION_SPACING, MAX_STATION_SPACING);
        return new RouteCardSpacing(
                mode,
                stationSpacing,
                clamp(stationSpacing * 0.34D, 32.0D, 50.0D),
                clamp(stationSpacing * 0.28D, 24.0D, 42.0D),
                clamp(stationSpacing * 0.46D, 44.0D, 72.0D),
                clamp(stationSpacing * 1.05D, 96.0D, 168.0D),
                clamp(stationSpacing * 0.18D, 18.0D, 30.0D),
                clamp(stationSpacing * 0.25D, 26.0D, 42.0D),
                clamp(stationSpacing * 0.18D, 18.0D, 30.0D));
    }

    private static void addStationDistance(List<Double> distances, RouteCardNode first, RouteCardNode second) {
        if (first.stationGroupId().equals(second.stationGroupId())) {
            return;
        }
        double distance = Math.hypot(second.worldX() - first.worldX(), second.worldZ() - first.worldZ());
        if (distance > 1.0D && Double.isFinite(distance)) {
            distances.add(distance);
        }
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int size = sorted.size();
        if (size == 0) {
            return DEFAULT_STATION_SPACING;
        }
        int middle = size / 2;
        return (size & 1) == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) * 0.5D;
    }

    private static Map<RouteCardNodeId, Integer> canonicalSegmentIndexByNode(List<RouteCardSegment> segments, Map<RouteCardNodeId, RouteCardNode> nodeById) {
        Map<RouteCardNodeId, Integer> result = new LinkedHashMap<>();
        for (RouteCardSegment segment : segments.stream().sorted(Comparator.comparingInt(RouteCardSegment::index)).toList()) {
            for (RouteCardNodeId nodeId : segment.nodeIds()) {
                RouteCardNode node = nodeById.get(nodeId);
                if (node == null || node.kind() != RouteCardNodeKind.STATION) {
                    continue;
                }
                result.putIfAbsent(nodeId, segment.index());
            }
        }
        return result;
    }

    private static boolean isCanonicalLocalEdge(
            RouteCardEdge edge,
            int segmentIndex,
            Map<RouteCardNodeId, RouteCardNode> nodeById,
            Map<RouteCardNodeId, Integer> canonicalSegmentIndexByNode) {
        return isCanonicalInSegment(edge.from(), segmentIndex, nodeById, canonicalSegmentIndexByNode)
                && isCanonicalInSegment(edge.to(), segmentIndex, nodeById, canonicalSegmentIndexByNode);
    }

    private static boolean isCanonicalInSegment(
            RouteCardNodeId nodeId,
            int segmentIndex,
            Map<RouteCardNodeId, RouteCardNode> nodeById,
            Map<RouteCardNodeId, Integer> canonicalSegmentIndexByNode) {
        RouteCardNode node = nodeById.get(nodeId);
        if (node == null || node.kind() != RouteCardNodeKind.STATION) {
            return true;
        }
        return canonicalSegmentIndexByNode.getOrDefault(nodeId, segmentIndex) == segmentIndex;
    }

    private static Map<RouteCardNodeId, RouteCardNode> nodeIndex(List<RouteCardNode> nodes) {
        Map<RouteCardNodeId, RouteCardNode> result = new LinkedHashMap<>();
        for (RouteCardNode node : nodes) {
            result.put(node.id(), node);
        }
        return result;
    }

    private static Map<String, RouteCardEdge> edgeIndex(List<RouteCardEdge> edges) {
        Map<String, RouteCardEdge> result = new LinkedHashMap<>();
        for (RouteCardEdge edge : edges) {
            result.put(edge.id(), edge);
        }
        return result;
    }

    private static Comparator<RouteCardEdge> edgeOrder() {
        return Comparator.comparingInt(RouteCardEdge::layoutIndex).thenComparing(RouteCardEdge::id);
    }

    private static Aabb2 graphBounds(List<RouteCardVisualNode> nodes, List<RouteCardVisualEdge> edges, List<RouteCardVisualSegment> segments) {
        Aabb2 bounds = Aabb2.empty();
        for (RouteCardVisualNode node : nodes) {
            bounds = bounds.include(nodeBounds(node.node(), node.position()));
        }
        for (RouteCardVisualEdge edge : edges) {
            bounds = bounds.include(edge.bounds());
        }
        for (RouteCardVisualSegment segment : segments) {
            bounds = bounds.include(segment.bounds());
        }
        return bounds;
    }

    private static Aabb2 nodeBounds(RouteCardNode node, Vec2 point) {
        double radius = nodeRadius(node);
        return switch (node.kind()) {
            case STATION -> Aabb2.around(point.x(), point.y(), radius + 5.0D);
            case PORTAL_BOUNDARY -> Aabb2.around(point.x(), point.y(), radius * 1.25D + 5.0D);
            case FOLD_BOUNDARY -> Aabb2.around(point.x(), point.y(), radius * 1.42D + 5.0D);
            case MISSING_PATH_BOUNDARY -> Aabb2.around(point.x(), point.y(), radius * 1.2D + 4.0D);
        };
    }

    private static Aabb2 labelReservation(RouteCardNode node, Vec2 point) {
        if (node.label().isBlank() || isBoundaryPort(node)) {
            return Aabb2.empty();
        }
        double radius = nodeRadius(node);
        double width = Math.max(26.0D, Math.min(118.0D, node.label().length() * 4.8D + 10.0D));
        double height = 10.0D;
        Aabb2 right = new Aabb2(
                point.x() + radius + 7.0D,
                point.y() - height * 0.5D,
                point.x() + radius + 7.0D + width,
                point.y() + height * 0.5D);
        Aabb2 top = new Aabb2(
                point.x() - width * 0.5D,
                point.y() - radius - height - 7.0D,
                point.x() + width * 0.5D,
                point.y() - radius - 7.0D);
        return right.include(top).inflate(SEGMENT_LABEL_PADDING);
    }

    private static double edgeFootprintPadding(RouteCardEdge edge) {
        int lanes = Math.max(1, edge.themeColors().size());
        return EDGE_FOOTPRINT_PADDING + FullRouteMapConfig.LINE_WIDTH_PX + lanes * 1.6D;
    }

    private static int nodePriority(RouteCardNode node) {
        return switch (node.kind()) {
            case STATION -> 650;
            case PORTAL_BOUNDARY -> 755;
            case FOLD_BOUNDARY -> 760;
            case MISSING_PATH_BOUNDARY -> 720;
        } + Math.max(0, 100 - node.layoutOccurrence());
    }

    private static double nodeRadius(RouteCardNode node) {
        return switch (node.kind()) {
            case STATION -> 6.0D;
            case PORTAL_BOUNDARY -> 5.2D;
            case FOLD_BOUNDARY -> 6.0D;
            case MISSING_PATH_BOUNDARY -> 3.5D;
        };
    }

    private static Aabb2 worldBounds(List<RouteCardNode> nodes) {
        Aabb2 bounds = Aabb2.empty();
        List<RouteCardNode> anchored = nodes.stream()
                .filter(node -> !isBoundaryPort(node))
                .toList();
        if (anchored.isEmpty()) {
            anchored = nodes;
        }
        for (RouteCardNode node : anchored) {
            bounds = bounds.include(node.worldX(), node.worldZ());
        }
        return bounds.isEmpty() ? Aabb2.around(0.0D, 0.0D, 1.0D) : bounds;
    }

    private static boolean isBoundaryPort(RouteCardNode node) {
        return node.kind() == RouteCardNodeKind.FOLD_BOUNDARY
                || node.kind() == RouteCardNodeKind.PORTAL_BOUNDARY
                || node.kind() == RouteCardNodeKind.MISSING_PATH_BOUNDARY;
    }

    private static Vec2 segmentDirection(SegmentBody previous, SegmentBody next) {
        double dx = next.entryNode().worldX() - previous.exitNode().worldX();
        double dy = next.entryNode().worldZ() - previous.exitNode().worldZ();
        double length = Math.hypot(dx, dy);
        if (length < 0.001D) {
            Vec2 first = previous.localPositions().getOrDefault(previous.entryNode().id(), new Vec2(previous.footprint().centerX(), previous.footprint().centerY()));
            Vec2 last = previous.localPositions().getOrDefault(previous.exitNode().id(), new Vec2(previous.footprint().centerX() + 1.0D, previous.footprint().centerY()));
            dx = last.x() - first.x();
            dy = last.y() - first.y();
            length = Math.hypot(dx, dy);
        }
        if (length < 0.001D) {
            return new Vec2(1.0D, 0.0D);
        }
        return new Vec2(dx / length, dy / length);
    }

    private static List<Double> segmentRotations() {
        return List.of(
                0.0D,
                Math.toRadians(15.0D), Math.toRadians(-15.0D),
                Math.toRadians(30.0D), Math.toRadians(-30.0D),
                Math.toRadians(45.0D), Math.toRadians(-45.0D),
                Math.toRadians(60.0D), Math.toRadians(-60.0D),
                Math.toRadians(75.0D), Math.toRadians(-75.0D),
                Math.toRadians(90.0D), Math.toRadians(-90.0D),
                Math.toRadians(120.0D), Math.toRadians(-120.0D),
                Math.toRadians(150.0D), Math.toRadians(-150.0D),
                Math.PI);
    }

    private static List<Double> gapMultipliers() {
        return List.of(1.0D, 1.25D, 1.55D, 2.0D, 2.75D, 3.75D, 5.0D);
    }

    private static List<Double> spiralRadii(double baseGap) {
        return List.of(baseGap * 0.35D, baseGap * 0.70D, baseGap * 1.15D, baseGap * 1.70D, baseGap * 2.45D, baseGap * 3.40D, baseGap * 4.75D);
    }

    private static List<Double> boundaryPortRotations() {
        return List.of(
                0.0D,
                Math.toRadians(18.0D), Math.toRadians(-18.0D),
                Math.toRadians(34.0D), Math.toRadians(-34.0D),
                Math.toRadians(55.0D), Math.toRadians(-55.0D),
                Math.toRadians(82.0D), Math.toRadians(-82.0D));
    }

    private static List<Integer> boundaryPortLanes() {
        return List.of(0, 1, -1, 2, -2, 3, -3);
    }

    private static PlacementCandidate better(PlacementCandidate current, PlacementCandidate candidate) {
        return current == null || candidate.score() < current.score() ? candidate : current;
    }

    private static Vec2 separationVectorForSecond(Aabb2 first, Aabb2 second) {
        double right = first.maxX() - second.minX();
        double left = second.maxX() - first.minX();
        double down = first.maxY() - second.minY();
        double up = second.maxY() - first.minY();
        double overlapX = Math.min(right, left);
        double overlapY = Math.min(down, up);
        if (overlapX <= overlapY) {
            double sign = second.centerX() >= first.centerX() ? 1.0D : -1.0D;
            return new Vec2(sign * (overlapX + 0.5D), 0.0D);
        }
        double sign = second.centerY() >= first.centerY() ? 1.0D : -1.0D;
        return new Vec2(0.0D, sign * (overlapY + 0.5D));
    }

    private static List<Vec2> curvedPath(Vec2 a, Vec2 b, double side, double bendRatio) {
        if (bendRatio <= 0.001D) {
            return List.of(a, b);
        }
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double length = Math.max(1.0D, Math.hypot(dx, dy));
        double nx = -dy / length * Math.signum(side == 0.0D ? 1.0D : side);
        double ny = dx / length * Math.signum(side == 0.0D ? 1.0D : side);
        double bend = Math.min(Math.max(18.0D, length * 0.42D), Math.max(7.0D, length * bendRatio));
        return List.of(a, new Vec2((a.x() + b.x()) * 0.5D + nx * bend, (a.y() + b.y()) * 0.5D + ny * bend), b);
    }

    private static List<Vec2> stationInternalPath(Vec2 a, Vec2 b, double side, double bendRatio) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double length = Math.max(1.0D, Math.hypot(dx, dy));
        double nx = -dy / length * Math.signum(side == 0.0D ? 1.0D : side);
        double ny = dx / length * Math.signum(side == 0.0D ? 1.0D : side);
        double bend = Math.max(18.0D, Math.min(62.0D, length * (0.88D + bendRatio)));
        return List.of(a, new Vec2((a.x() + b.x()) * 0.5D + nx * bend, (a.y() + b.y()) * 0.5D + ny * bend), b);
    }

    private static Vec2 rotate(Vec2 direction, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec2(direction.x() * cos - direction.y() * sin, direction.x() * sin + direction.y() * cos);
    }

    private static Vec2 routeDirectionForBoundary(RouteCardNodeId boundaryId, RouteCardEdge edge, Map<RouteCardNodeId, Vec2> positions) {
        RouteCardNodeId other = edge.from().equals(boundaryId) ? edge.to() : edge.from();
        Vec2 point = positions.get(other);
        if (point == null) {
            return new Vec2(0.0D, 0.0D);
        }
        return edge.from().equals(boundaryId) ? normalize(-point.x(), -point.y()) : normalize(point.x(), point.y());
    }

    private static Vec2 hashDirection(RouteCardNodeId nodeId) {
        double angle = ((nodeId.hashCode() & 0xFFFF) / 65535.0D) * Math.PI * 2.0D;
        return new Vec2(Math.cos(angle), Math.sin(angle));
    }

    private static Vec2 normalize(double x, double y) {
        double length = Math.hypot(x, y);
        return length < 0.001D ? new Vec2(0.0D, 0.0D) : new Vec2(x / length, y / length);
    }

    private static Vec2 nearestCardDirection(double dx, double dy) {
        double length = Math.hypot(dx, dy);
        if (length < 0.001D) {
            return new Vec2(1.0D, 0.0D);
        }
        double ux = dx / length;
        double uy = dy / length;
        Vec2 best = CARD_DIRECTIONS.getFirst();
        double bestDot = Double.NEGATIVE_INFINITY;
        for (Vec2 direction : CARD_DIRECTIONS) {
            double score = ux * direction.x() + uy * direction.y();
            if (score > bestDot) {
                bestDot = score;
                best = direction;
            }
        }
        return best;
    }

    private static List<Vec2> orderedCardDirections(Vec2 preferred) {
        return CARD_DIRECTIONS.stream()
                .sorted(Comparator.comparingDouble((Vec2 direction) -> dot(direction, preferred)).reversed())
                .toList();
    }

    private static Vec2 reverse(Vec2 direction) {
        return new Vec2(-direction.x(), -direction.y());
    }

    private static java.util.Optional<Vec2> rayIntersection(Vec2 a, Vec2 first, Vec2 b, Vec2 second) {
        double det = first.x() * -second.y() - first.y() * -second.x();
        if (Math.abs(det) < 0.001D) {
            return java.util.Optional.empty();
        }
        double bx = b.x() - a.x();
        double by = b.y() - a.y();
        double t = (bx * -second.y() - by * -second.x()) / det;
        double u = (first.x() * by - first.y() * bx) / det;
        if (t < 0.0D || u < 0.0D) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Vec2(a.x() + first.x() * t, a.y() + first.y() * t));
    }

    private static List<Vec2> dedupePath(List<Vec2> points) {
        List<Vec2> result = new ArrayList<>();
        for (Vec2 point : points) {
            if (result.isEmpty() || result.getLast().distanceTo(point) > 0.5D) {
                result.add(point);
            }
        }
        return result.size() >= 2 ? result : points;
    }

    private static double directionPenalty(List<Vec2> points) {
        double penalty = 0.0D;
        for (int i = 0; i + 1 < points.size(); i++) {
            Vec2 a = points.get(i);
            Vec2 b = points.get(i + 1);
            double dx = b.x() - a.x();
            double dy = b.y() - a.y();
            double length = Math.hypot(dx, dy);
            if (length < 0.001D) {
                continue;
            }
            Vec2 nearest = nearestCardDirection(dx, dy);
            penalty += 1.0D - Math.abs(dx / length * nearest.x() + dy / length * nearest.y());
        }
        return penalty;
    }

    private static boolean isOctilinearPath(List<Vec2> points) {
        for (int i = 0; i + 1 < points.size(); i++) {
            if (!isOctilinearSegment(points.get(i), points.get(i + 1))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOctilinearSegment(Vec2 a, Vec2 b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double length = Math.hypot(dx, dy);
        if (length < 0.001D) {
            return true;
        }
        Vec2 nearest = nearestCardDirection(dx, dy);
        return Math.abs(dx / length * nearest.x() + dy / length * nearest.y()) > 0.999D;
    }

    private static boolean isZero(Vec2 vector) {
        return Math.hypot(vector.x(), vector.y()) < 0.001D;
    }

    private static double dot(Vec2 first, Vec2 second) {
        return first.x() * second.x() + first.y() * second.y();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Vec2 add(Vec2 point, double dx, double dy) {
        return new Vec2(point.x() + dx, point.y() + dy);
    }

    private static Aabb2 translate(Aabb2 bounds, double dx, double dy) {
        if (bounds.isEmpty()) {
            return bounds;
        }
        return new Aabb2(bounds.minX() + dx, bounds.minY() + dy, bounds.maxX() + dx, bounds.maxY() + dy);
    }

    private static double maxDimension(Aabb2 bounds) {
        return Math.max(width(bounds), height(bounds));
    }

    private static double width(Aabb2 bounds) {
        return bounds.isEmpty() ? 0.0D : Math.max(0.0D, bounds.maxX() - bounds.minX());
    }

    private static double height(Aabb2 bounds) {
        return bounds.isEmpty() ? 0.0D : Math.max(0.0D, bounds.maxY() - bounds.minY());
    }

    private static double area(Aabb2 bounds) {
        return width(bounds) * height(bounds);
    }

    private static double overlapArea(Aabb2 first, Aabb2 second) {
        if (!first.intersects(second)) {
            return 0.0D;
        }
        double x = Math.max(0.0D, Math.min(first.maxX(), second.maxX()) - Math.max(first.minX(), second.minX()));
        double y = Math.max(0.0D, Math.min(first.maxY(), second.maxY()) - Math.max(first.minY(), second.minY()));
        return x * y;
    }

    private static double polylineLength(List<Vec2> points) {
        double length = 0.0D;
        for (int i = 0; i + 1 < points.size(); i++) {
            length += points.get(i).distanceTo(points.get(i + 1));
        }
        return length;
    }

    private static double sampledPathDistance(List<Vec2> first, List<Vec2> second) {
        if (first.size() < 2 || second.size() < 2) {
            return Double.POSITIVE_INFINITY;
        }
        double best = Double.POSITIVE_INFINITY;
        for (Vec2 sample : samplePath(first, 14)) {
            best = Math.min(best, distanceToPolyline(sample, second));
        }
        for (Vec2 sample : samplePath(second, 14)) {
            best = Math.min(best, distanceToPolyline(sample, first));
        }
        return best;
    }

    private static List<Vec2> samplePath(List<Vec2> points, int count) {
        double length = polylineLength(points);
        if (length < 1.0E-6D) {
            return List.of();
        }
        List<Vec2> samples = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            double target = length * i / (count + 1.0D);
            samples.add(pointAtDistance(points, target));
        }
        return samples;
    }

    private static List<Vec2> trimmedSegment(Vec2 a, Vec2 b, double startTrim, double endTrim) {
        double length = a.distanceTo(b);
        if (length < 1.0E-6D) {
            return List.of(a, b);
        }
        double trimA = Math.min(startTrim, length * 0.42D);
        double trimB = Math.min(endTrim, length * 0.32D);
        if (trimA + trimB >= length - 0.5D) {
            double midpoint = length * 0.5D;
            trimA = Math.max(0.0D, midpoint - 0.25D);
            trimB = Math.max(0.0D, midpoint - 0.25D);
        }
        double ux = (b.x() - a.x()) / length;
        double uy = (b.y() - a.y()) / length;
        return List.of(
                new Vec2(a.x() + ux * trimA, a.y() + uy * trimA),
                new Vec2(b.x() - ux * trimB, b.y() - uy * trimB));
    }

    private static Vec2 pointAtDistance(List<Vec2> points, double target) {
        double walked = 0.0D;
        for (int i = 0; i + 1 < points.size(); i++) {
            Vec2 a = points.get(i);
            Vec2 b = points.get(i + 1);
            double length = a.distanceTo(b);
            if (walked + length >= target) {
                double t = length < 1.0E-6D ? 0.0D : (target - walked) / length;
                return new Vec2(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t);
            }
            walked += length;
        }
        return points.getLast();
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
        if (len2 <= 1.0E-6D) {
            return point.distanceTo(a);
        }
        double t = ((point.x() - a.x()) * dx + (point.y() - a.y()) * dy) / len2;
        double clamped = Math.max(0.0D, Math.min(1.0D, t));
        return point.distanceTo(new Vec2(a.x() + dx * clamped, a.y() + dy * clamped));
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

    private static boolean pathIntersectsAabb(List<Vec2> points, Aabb2 bounds) {
        if (points.size() < 2 || bounds.isEmpty()) {
            return false;
        }
        for (Vec2 point : points) {
            if (bounds.contains(point.x(), point.y())) {
                return true;
            }
        }
        Vec2 topLeft = new Vec2(bounds.minX(), bounds.minY());
        Vec2 topRight = new Vec2(bounds.maxX(), bounds.minY());
        Vec2 bottomRight = new Vec2(bounds.maxX(), bounds.maxY());
        Vec2 bottomLeft = new Vec2(bounds.minX(), bounds.maxY());
        for (int i = 0; i + 1 < points.size(); i++) {
            Vec2 a = points.get(i);
            Vec2 b = points.get(i + 1);
            if (segmentsIntersect(a, b, topLeft, topRight)
                    || segmentsIntersect(a, b, topRight, bottomRight)
                    || segmentsIntersect(a, b, bottomRight, bottomLeft)
                    || segmentsIntersect(a, b, bottomLeft, topLeft)) {
                return true;
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

    private static boolean sharesEndpoint(RouteCardEdge first, RouteCardEdge second) {
        return first.from().equals(second.from())
                || first.from().equals(second.to())
                || first.to().equals(second.from())
                || first.to().equals(second.to());
    }

    private static double hashSide(String id) {
        return (id.hashCode() & 1) == 0 ? 1.0D : -1.0D;
    }

    private static double hashAngle(RouteCardNodeId first, RouteCardNodeId second) {
        int hash = first.hashCode() * 31 + second.hashCode();
        return (hash & 0xFFFF) / 65535.0D * Math.PI * 2.0D;
    }

    private static Aabb2 boundsFor(List<Vec2> points) {
        Aabb2 bounds = Aabb2.empty();
        for (Vec2 point : points) {
            bounds = bounds.include(point.x(), point.y());
        }
        return bounds;
    }

    private static Aabb2 boundsForPositions(Map<RouteCardNodeId, Vec2> positions) {
        Aabb2 bounds = Aabb2.empty();
        for (Vec2 position : positions.values()) {
            bounds = bounds.include(position.x(), position.y());
        }
        return bounds.isEmpty() ? Aabb2.around(0.0D, 0.0D, 1.0D) : bounds;
    }

    private record SegmentBody(
            RouteCardSegment segment,
            Map<RouteCardNodeId, Vec2> localPositions,
            List<RouteCardVisualEdge> localVisualEdges,
            Aabb2 footprint,
            RouteCardNode entryNode,
            RouteCardNode exitNode) {}

    private record PlacedSegmentBody(SegmentBody body, double offsetX, double offsetY) {
        private Vec2 positionOf(RouteCardNodeId nodeId) {
            Vec2 local = this.body.localPositions().getOrDefault(nodeId, new Vec2(this.body.footprint().centerX(), this.body.footprint().centerY()));
            return this.translate(local);
        }

        private Vec2 translate(Vec2 local) {
            return new Vec2(local.x() + this.offsetX, local.y() + this.offsetY);
        }

        private Aabb2 footprint() {
            return RouteCardLayoutSolver.translate(this.body.footprint(), this.offsetX, this.offsetY);
        }
    }

    private record PlacementCandidate(PlacedSegmentBody placed, double score, double overlapScore) {}

    private record SegmentPlacement(List<PlacedSegmentBody> placed, boolean fallback) {}

    private record RoutedEdge(RouteCardEdge edge, List<Vec2> points) {}

    private enum RouteCardSolveMode {
        PRACTICAL,
        SCHEMATIC
    }

    private record RouteCardSpacing(
            RouteCardSolveMode mode,
            double stationSpacing,
            double boundaryPortDistance,
            double boundaryPortSpacing,
            double segmentClearance,
            double segmentPlacementGap,
            double localNodeGap,
            double routeEdgeClearance,
            double routeEdgeSeparation) {}

    private record BoundaryPortContext(Vec2 base, Vec2 direction) {}

    private static final class MutablePlacement {
        private final SegmentBody body;
        private double offsetX;
        private double offsetY;

        private MutablePlacement(SegmentBody body, double offsetX, double offsetY) {
            this.body = body;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }

        private void move(double dx, double dy) {
            this.offsetX += dx;
            this.offsetY += dy;
        }

        private Aabb2 footprint() {
            return translate(this.body.footprint(), this.offsetX, this.offsetY);
        }

        private PlacedSegmentBody placed() {
            return new PlacedSegmentBody(this.body, this.offsetX, this.offsetY);
        }
    }
}
