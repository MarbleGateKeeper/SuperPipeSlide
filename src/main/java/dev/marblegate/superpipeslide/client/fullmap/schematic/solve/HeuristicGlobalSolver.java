package dev.marblegate.superpipeslide.client.fullmap.schematic.solve;

import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapLayoutMode;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.schematic.SchematicLayoutConfig;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SchematicEdge;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SchematicInputGraph;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SchematicNode;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SchematicQualityReport;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SemanticEdgeKind;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualEdgePath;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualHitShape;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualLabel;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualLane;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualNode;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualRouteMapGraph;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class HeuristicGlobalSolver implements SchematicSolverBackend {
    private static final double EPSILON = 1.0E-6D;
    private final MetroMapSchematicSolver metroSolver = new MetroMapSchematicSolver();

    @Override
    public SchematicLayoutResult solve(SchematicInputGraph input, SchematicLayoutConfig config, Optional<VisualRouteMapGraphSnapshot> previous) {
        if (config.layoutMode() == FullRouteMapLayoutMode.SCHEMATIC) {
            return this.metroSolver.solve(input, config, previous);
        }
        long startNanos = System.nanoTime();
        List<NodeState> states = initialiseNodes(input, previous);
        Map<NodeId, NodeState> stateById = states.stream().collect(Collectors.toMap(state -> state.node.id(), state -> state, (a, b) -> a, LinkedHashMap::new));
        Map<NodeId, Integer> indexByNode = new HashMap<>();
        for (int i = 0; i < states.size(); i++) {
            indexByNode.put(states.get(i).node.id(), i);
        }

        boolean timeout = false;
        int iterations = 0;
        for (; iterations < config.maxIterations(); iterations++) {
            if ((System.nanoTime() - startNanos) / 1_000_000L >= config.solverTimeoutMillis()) {
                timeout = true;
                break;
            }
            double movement = this.iterate(states, indexByNode, input.edges(), config);
            if (iterations > 40 && movement < 0.015D) {
                iterations++;
                break;
            }
        }

        List<VisualNode> visualNodes = states.stream()
                .map(state -> new VisualNode(
                        state.node.id(),
                        state.node.kind(),
                        state.x,
                        state.z,
                        state.node.worldX(),
                        state.node.worldZ(),
                        state.node.label(),
                        state.node.routeLineIds(),
                        state.node.importance(),
                        false))
                .toList();
        Map<NodeId, VisualNode> visualNodesById = visualNodes.stream()
                .collect(Collectors.toMap(VisualNode::id, node -> node, (a, b) -> a, LinkedHashMap::new));

        Map<String, Double> corridorOffsets = this.computeCorridorOffsets(input, stateById, config);
        RouteOutput routeOutput = this.routeEdges(input, stateById, corridorOffsets, config);
        List<VisualLabel> labels = this.layoutLabels(states);
        SchematicQualityReport quality = this.quality(input, states, routeOutput, labels, iterations, timeout, previous.isPresent(), startNanos, config);
        Aabb2 bounds = this.bounds(visualNodes, routeOutput.edgePaths, labels);
        VisualRouteMapGraph graph = new VisualRouteMapGraph(
                input.levelKey(),
                visualNodes,
                visualNodesById,
                routeOutput.edgePaths,
                routeOutput.edgePaths.stream().collect(Collectors.toMap(VisualEdgePath::edgeId, edge -> edge, (a, b) -> a, LinkedHashMap::new)),
                labels,
                quality,
                bounds,
                input.routeRevision(),
                input.pipeRevision(),
                FullRouteMapConfig.SCHEMATIC_SOLVER_VERSION);
        return new SchematicLayoutResult(graph);
    }

    private static List<NodeState> initialiseNodes(SchematicInputGraph input, Optional<VisualRouteMapGraphSnapshot> previous) {
        List<NodeState> states = new ArrayList<>();
        for (SchematicNode node : input.nodes()) {
            Optional<VisualRouteMapGraphSnapshot.Position> previousPosition = previous.flatMap(snapshot -> snapshot.position(node.id()));
            double x = previousPosition.map(VisualRouteMapGraphSnapshot.Position::x).orElse(node.worldX());
            double z = previousPosition.map(VisualRouteMapGraphSnapshot.Position::z).orElse(node.worldZ());
            double dx = x - node.worldX();
            double dz = z - node.worldZ();
            double distance = Math.hypot(dx, dz);
            if (distance > node.maxDisplacement() && distance > EPSILON) {
                double scale = node.maxDisplacement() / distance;
                x = node.worldX() + dx * scale;
                z = node.worldZ() + dz * scale;
            }
            states.add(new NodeState(node, x, z, previousPosition));
        }
        states.sort(Comparator.comparing(state -> state.node.id()));
        return states;
    }

    private double iterate(List<NodeState> states, Map<NodeId, Integer> indexByNode, List<SchematicEdge> edges, SchematicLayoutConfig config) {
        for (NodeState state : states) {
            state.fx = (state.node.worldX() - state.x) * config.geoWeight() * state.node.anchorWeight();
            state.fz = (state.node.worldZ() - state.z) * config.geoWeight() * state.node.anchorWeight();
            if (state.previousPosition.isPresent()) {
                VisualRouteMapGraphSnapshot.Position previous = state.previousPosition.get();
                state.fx += (previous.x() - state.x) * config.previousWeight();
                state.fz += (previous.z() - state.z) * config.previousWeight();
            }
        }
        this.applyNodeRepulsion(states, config);
        this.applyEdgeForces(states, indexByNode, edges, config);

        double totalMovement = 0.0D;
        double maxStep = config.maxStepBlocks();
        for (NodeState state : states) {
            double fx = clamp(state.fx, -maxStep, maxStep);
            double fz = clamp(state.fz, -maxStep, maxStep);
            state.x += fx;
            state.z += fz;
            this.clampDisplacement(state);
            totalMovement += Math.hypot(fx, fz);
        }
        return totalMovement / Math.max(1, states.size());
    }

    private void applyNodeRepulsion(List<NodeState> states, SchematicLayoutConfig config) {
        double cellSize = config.minNodeGapBlocks() * 2.0D;
        Map<GridCell, List<Integer>> grid = new HashMap<>();
        for (int i = 0; i < states.size(); i++) {
            grid.computeIfAbsent(GridCell.of(states.get(i).x, states.get(i).z, cellSize), ignored -> new ArrayList<>()).add(i);
        }
        for (int i = 0; i < states.size(); i++) {
            NodeState first = states.get(i);
            GridCell cell = GridCell.of(first.x, first.z, cellSize);
            for (int gx = -1; gx <= 1; gx++) {
                for (int gz = -1; gz <= 1; gz++) {
                    for (int j : grid.getOrDefault(new GridCell(cell.x + gx, cell.z + gz), List.of())) {
                        if (j <= i) {
                            continue;
                        }
                        NodeState second = states.get(j);
                        double minDistance = minNodeDistance(first.node, second.node, config);
                        double dx = first.x - second.x;
                        double dz = first.z - second.z;
                        double distance = Math.hypot(dx, dz);
                        if (distance >= minDistance) {
                            continue;
                        }
                        double ux;
                        double uz;
                        if (distance < EPSILON) {
                            double angle = hashAngle(first.node.id(), second.node.id());
                            ux = Math.cos(angle);
                            uz = Math.sin(angle);
                            distance = EPSILON;
                        } else {
                            ux = dx / distance;
                            uz = dz / distance;
                        }
                        double strength = (minDistance - distance) / minDistance * config.nodeRepulsionWeight();
                        double firstShare = second.node.anchorWeight() / Math.max(EPSILON, first.node.anchorWeight() + second.node.anchorWeight());
                        double secondShare = first.node.anchorWeight() / Math.max(EPSILON, first.node.anchorWeight() + second.node.anchorWeight());
                        first.fx += ux * strength * firstShare;
                        first.fz += uz * strength * firstShare;
                        second.fx -= ux * strength * secondShare;
                        second.fz -= uz * strength * secondShare;
                    }
                }
            }
        }
    }

    private void applyEdgeForces(List<NodeState> states, Map<NodeId, Integer> indexByNode, List<SchematicEdge> edges, SchematicLayoutConfig config) {
        for (SchematicEdge edge : edges) {
            Integer fromIndex = indexByNode.get(edge.from());
            Integer toIndex = indexByNode.get(edge.to());
            if (fromIndex == null || toIndex == null || fromIndex.equals(toIndex) || edge.kind() == SemanticEdgeKind.STATION_INTERNAL) {
                continue;
            }
            NodeState from = states.get(fromIndex);
            NodeState to = states.get(toIndex);
            double dx = to.x - from.x;
            double dz = to.z - from.z;
            double distance = Math.max(1.0D, Math.hypot(dx, dz));
            double original = Math.max(24.0D, Math.hypot(to.node.worldX() - from.node.worldX(), to.node.worldZ() - from.node.worldZ()));
            double desired = desiredEdgeLength(original, edge.kind(), config);
            double spring = (distance - desired) / desired * config.edgeLengthWeight();
            double ux = dx / distance;
            double uz = dz / distance;
            from.fx += ux * spring;
            from.fz += uz * spring;
            to.fx -= ux * spring;
            to.fz -= uz * spring;

            Vec2 direction = nearestDirection(dx, dz, config.directionSetMode());
            double targetDx = direction.x() * distance;
            double targetDz = direction.y() * distance;
            double adjustX = (targetDx - dx) * config.directionWeight();
            double adjustZ = (targetDz - dz) * config.directionWeight();
            from.fx -= adjustX * 0.5D;
            from.fz -= adjustZ * 0.5D;
            to.fx += adjustX * 0.5D;
            to.fz += adjustZ * 0.5D;
        }
    }

    private void clampDisplacement(NodeState state) {
        double dx = state.x - state.node.worldX();
        double dz = state.z - state.node.worldZ();
        double distance = Math.hypot(dx, dz);
        if (distance <= state.node.maxDisplacement() || distance < EPSILON) {
            return;
        }
        double scale = state.node.maxDisplacement() / distance;
        state.x = state.node.worldX() + dx * scale;
        state.z = state.node.worldZ() + dz * scale;
    }

    private Map<String, Double> computeCorridorOffsets(SchematicInputGraph input, Map<NodeId, NodeState> stateById, SchematicLayoutConfig config) {
        List<SchematicEdge> candidates = input.edges().stream()
                .filter(edge -> edge.kind() != SemanticEdgeKind.STATION_INTERNAL)
                .filter(edge -> edge.kind() != SemanticEdgeKind.SHARED_TRACK)
                .filter(edge -> edge.kind() != SemanticEdgeKind.FOLD_ADJACENT)
                .filter(edge -> edge.kind() != SemanticEdgeKind.LOOP_BACK)
                .toList();
        List<List<SchematicEdge>> groups = new ArrayList<>();
        for (SchematicEdge edge : candidates) {
            NodeState from = stateById.get(edge.from());
            NodeState to = stateById.get(edge.to());
            if (from == null || to == null || Math.hypot(to.x - from.x, to.z - from.z) * FullRouteMapConfig.BASE_SCALE < config.minReadableEdgePx()) {
                continue;
            }
            List<SchematicEdge> group = null;
            for (List<SchematicEdge> existingGroup : groups) {
                if (existingGroup.stream().anyMatch(existing -> nearParallel(stateById, existing, edge, config))) {
                    group = existingGroup;
                    break;
                }
            }
            if (group == null) {
                group = new ArrayList<>();
                groups.add(group);
            }
            group.add(edge);
        }
        Map<String, Double> offsets = new HashMap<>();
        for (List<SchematicEdge> group : groups) {
            if (group.size() <= 1) {
                continue;
            }
            group.sort(Comparator.comparing(SchematicEdge::id));
            double maxOffset = config.maxCorridorOffsetBlocks();
            double step = config.corridorOffsetBlocks();
            if ((group.size() - 1) * step * 0.5D > maxOffset) {
                step = maxOffset * 2.0D / Math.max(1, group.size() - 1);
            }
            double center = (group.size() - 1) * 0.5D;
            for (int i = 0; i < group.size(); i++) {
                offsets.put(group.get(i).id(), (i - center) * step);
            }
        }
        return offsets;
    }

    private RouteOutput routeEdges(SchematicInputGraph input, Map<NodeId, NodeState> stateById, Map<String, Double> corridorOffsets, SchematicLayoutConfig config) {
        List<VisualEdgePath> paths = new ArrayList<>();
        int fallbackEdges = 0;
        int stationInternalEdges = 0;
        int bendCount = 0;
        int loopGlyphCount = 0;
        for (SchematicEdge edge : input.edges()) {
            if (edge.kind() == SemanticEdgeKind.STATION_INTERNAL) {
                stationInternalEdges++;
                continue;
            }
            NodeState from = stateById.get(edge.from());
            NodeState to = stateById.get(edge.to());
            if (from == null || to == null) {
                fallbackEdges++;
                continue;
            }
            RoutedPath routed = this.routeEdge(edge, from, to, stateById, paths, corridorOffsets.getOrDefault(edge.id(), 0.0D), config);
            fallbackEdges += routed.fallback ? 1 : 0;
            bendCount += Math.max(0, routed.points.size() - 2);
            loopGlyphCount += routed.loopGlyph ? 1 : 0;
            Aabb2 bounds = boundsForPoints(routed.points).inflate(hitRadiusBlocks(edge));
            paths.add(new VisualEdgePath(
                    edge.id(),
                    edge.from(),
                    edge.to(),
                    edge.kind(),
                    edge.routeLineIds(),
                    edge.occurrences(),
                    routed.points,
                    lanesFor(edge, routed.points),
                    new VisualHitShape(routed.points, hitRadiusBlocks(edge), bounds),
                    bounds,
                    routed.fallback));
        }
        return new RouteOutput(paths, fallbackEdges, stationInternalEdges, bendCount, loopGlyphCount);
    }

    private RoutedPath routeEdge(SchematicEdge edge, NodeState from, NodeState to, Map<NodeId, NodeState> stateById, List<VisualEdgePath> existingPaths, double corridorOffset, SchematicLayoutConfig config) {
        Vec2 a = new Vec2(from.x, from.z);
        Vec2 b = new Vec2(to.x, to.z);
        double directLength = Math.max(1.0D, a.distanceTo(b));
        if (edge.kind() == SemanticEdgeKind.LOOP_BACK) {
            List<Vec2> loop = offsetPathAnchored(loopPath(a, b, edge.id()), corridorOffset);
            return new RoutedPath(loop, false, directLength < 14.0D);
        }
        if (directLength * FullRouteMapConfig.BASE_SCALE < config.minReadableEdgePx()) {
            return new RoutedPath(offsetPathAnchored(List.of(a, b), corridorOffset), false, false);
        }

        List<List<Vec2>> candidates = new ArrayList<>();
        candidates.add(List.of(a, b));
        candidates.add(List.of(a, new Vec2(a.x(), b.y()), b));
        candidates.add(List.of(a, new Vec2(b.x(), a.y()), b));
        double midX = (a.x() + b.x()) * 0.5D;
        double midY = (a.y() + b.y()) * 0.5D;
        candidates.add(List.of(a, new Vec2(midX, a.y()), new Vec2(midX, b.y()), b));
        candidates.add(List.of(a, new Vec2(a.x(), midY), new Vec2(b.x(), midY), b));
        if (config.directionSetMode() == SchematicLayoutConfig.DirectionSetMode.OCTILINEAR) {
            candidates.addAll(octilinearCandidates(a, b));
        }

        List<Vec2> best = candidates.getFirst();
        double bestScore = Double.POSITIVE_INFINITY;
        for (List<Vec2> candidate : candidates) {
            List<Vec2> offsetCandidate = offsetPathAnchored(candidate, corridorOffset);
            double length = polylineLength(offsetCandidate);
            if (length / directLength > config.maxVisualDetourRatio()) {
                continue;
            }
            double score = length / directLength
                    + Math.max(0, offsetCandidate.size() - 2) * config.bendWeight()
                    + obstaclePenalty(offsetCandidate, edge, stateById)
                    + directionPenalty(offsetCandidate, config)
                    + edgeCollisionPenalty(offsetCandidate, edge, existingPaths, config);
            if (score < bestScore) {
                bestScore = score;
                best = offsetCandidate;
            }
        }
        boolean fallback = bestScore == Double.POSITIVE_INFINITY;
        if (fallback) {
            best = offsetPathAnchored(candidates.getFirst(), corridorOffset);
        }
        return new RoutedPath(best, fallback, false);
    }

    private static List<List<Vec2>> octilinearCandidates(Vec2 a, Vec2 b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double absX = Math.abs(dx);
        double absY = Math.abs(dy);
        if (absX < EPSILON || absY < EPSILON || Math.abs(absX - absY) < EPSILON) {
            return List.of();
        }
        double sx = Math.signum(dx);
        double sy = Math.signum(dy);
        double diagonal = Math.min(absX, absY);
        List<List<Vec2>> result = new ArrayList<>();

        Vec2 diagonalFirst = new Vec2(a.x() + sx * diagonal, a.y() + sy * diagonal);
        if (diagonalFirst.distanceTo(a) > EPSILON && diagonalFirst.distanceTo(b) > EPSILON) {
            result.add(List.of(a, diagonalFirst, b));
        }

        Vec2 diagonalLast = new Vec2(b.x() - sx * diagonal, b.y() - sy * diagonal);
        if (diagonalLast.distanceTo(a) > EPSILON && diagonalLast.distanceTo(b) > EPSILON) {
            result.add(List.of(a, diagonalLast, b));
        }

        double halfDiagonal = diagonal * 0.5D;
        if (halfDiagonal > EPSILON) {
            Vec2 first = new Vec2(a.x() + sx * halfDiagonal, a.y() + sy * halfDiagonal);
            Vec2 second = new Vec2(b.x() - sx * halfDiagonal, b.y() - sy * halfDiagonal);
            if (first.distanceTo(a) > EPSILON && first.distanceTo(second) > EPSILON && second.distanceTo(b) > EPSILON) {
                result.add(List.of(a, first, second, b));
            }
        }
        return result;
    }

    private List<VisualLane> lanesFor(SchematicEdge edge, List<Vec2> points) {
        List<UUID> lineIds = edge.routeLineIds();
        if (lineIds.isEmpty()) {
            return List.of(new VisualLane(Optional.empty(), 0, 0.0D));
        }
        double step = FullRouteMapConfig.LINE_WIDTH_PX / FullRouteMapConfig.BASE_SCALE + 3.0D;
        double center = (lineIds.size() - 1) * 0.5D;
        List<VisualLane> lanes = new ArrayList<>();
        for (int i = 0; i < lineIds.size(); i++) {
            lanes.add(new VisualLane(Optional.of(lineIds.get(i)), i, (i - center) * step));
        }
        return lanes;
    }

    private List<VisualLabel> layoutLabels(List<NodeState> states) {
        List<NodeState> ordered = states.stream()
                .sorted(Comparator.comparingInt((NodeState state) -> state.node.importance()).reversed().thenComparing(state -> state.node.id()))
                .toList();
        List<LabelBox> placed = new ArrayList<>();
        List<VisualLabel> labels = new ArrayList<>();
        for (NodeState state : ordered) {
            List<LabelCandidate> candidates = labelCandidates(state);
            LabelCandidate best = null;
            int bestPenalty = Integer.MAX_VALUE;
            for (LabelCandidate candidate : candidates) {
                int penalty = 0;
                for (LabelBox box : placed) {
                    if (candidate.box.intersects(box)) {
                        penalty += box.priority > state.node.importance() ? 250 : 90;
                    }
                }
                if (penalty < bestPenalty) {
                    best = candidate;
                    bestPenalty = penalty;
                }
            }
            if (best == null || bestPenalty >= 500) {
                continue;
            }
            placed.add(new LabelBox(best.box.minX, best.box.minZ, best.box.maxX, best.box.maxZ, state.node.importance()));
            labels.add(new VisualLabel(state.node.id(), state.node.label(), best.x, best.z, state.node.importance(), labelBaseScale(state.node), bestPenalty > 0));
        }
        return labels;
    }

    private SchematicQualityReport quality(SchematicInputGraph input, List<NodeState> states, RouteOutput routeOutput, List<VisualLabel> labels, int iterations, boolean timeout, boolean usedPrevious, long startNanos, SchematicLayoutConfig config) {
        double totalDisplacement = 0.0D;
        double maxDisplacement = 0.0D;
        int overlaps = 0;
        for (int i = 0; i < states.size(); i++) {
            NodeState first = states.get(i);
            double displacement = Math.hypot(first.x - first.node.worldX(), first.z - first.node.worldZ());
            totalDisplacement += displacement;
            maxDisplacement = Math.max(maxDisplacement, displacement);
            for (int j = i + 1; j < states.size(); j++) {
                NodeState second = states.get(j);
                if (Math.hypot(first.x - second.x, first.z - second.z) < minNodeDistance(first.node, second.node, config) * 0.75D) {
                    overlaps++;
                }
            }
        }
        int crossings = countEdgeCrossings(routeOutput.edgePaths);
        int labelOverlaps = countLabelOverlaps(labels);
        long solveTimeMillis = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
        return new SchematicQualityReport(
                solveTimeMillis,
                iterations,
                overlaps,
                crossings,
                labelOverlaps,
                totalDisplacement / Math.max(1, states.size()),
                maxDisplacement,
                routeOutput.bendCount,
                routeOutput.fallbackEdges,
                0,
                routeOutput.loopGlyphCount,
                routeOutput.stationInternalEdges,
                timeout,
                usedPrevious);
    }

    private Aabb2 bounds(List<VisualNode> nodes, List<VisualEdgePath> edges, List<VisualLabel> labels) {
        Aabb2 bounds = Aabb2.empty();
        for (VisualNode node : nodes) {
            bounds = bounds.include(node.x(), node.z());
            bounds = bounds.include(node.worldX(), node.worldZ());
        }
        for (VisualEdgePath edge : edges) {
            bounds = bounds.include(edge.bounds());
        }
        for (VisualLabel label : labels) {
            bounds = bounds.include(label.x(), label.z());
        }
        return bounds.isEmpty() ? Aabb2.around(0.0D, 0.0D, 32.0D) : bounds.inflate(48.0D);
    }

    private static double desiredEdgeLength(double original, SemanticEdgeKind kind, SchematicLayoutConfig config) {
        double factor = switch (kind) {
            case FOLD_ADJACENT -> 0.85D;
            case PARALLEL_CORRIDOR -> 1.04D;
            case LOOP_BACK -> 1.12D;
            case SHARED_TRACK, NORMAL, TRANSFER_HINT -> 1.0D;
            case STATION_INTERNAL -> 0.45D;
        };
        return clamp(original * factor, config.minEdgeLengthBlocks(), config.maxEdgeLengthBlocks());
    }

    private static Vec2 nearestDirection(double dx, double dz, SchematicLayoutConfig.DirectionSetMode mode) {
        double length = Math.max(EPSILON, Math.hypot(dx, dz));
        double ux = dx / length;
        double uz = dz / length;
        if (mode == SchematicLayoutConfig.DirectionSetMode.FREEFORM) {
            return new Vec2(ux, uz);
        }
        double[][] directions = mode == SchematicLayoutConfig.DirectionSetMode.ORTHOGONAL
                ? new double[][] { { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 } }
                : new double[][] { { 1, 0 }, { Math.sqrt(0.5D), Math.sqrt(0.5D) }, { 0, 1 }, { -Math.sqrt(0.5D), Math.sqrt(0.5D) }, { -1, 0 }, { -Math.sqrt(0.5D), -Math.sqrt(0.5D) }, { 0, -1 }, { Math.sqrt(0.5D), -Math.sqrt(0.5D) } };
        double bestDot = Double.NEGATIVE_INFINITY;
        double[] best = directions[0];
        for (double[] direction : directions) {
            double dot = ux * direction[0] + uz * direction[1];
            if (dot > bestDot) {
                bestDot = dot;
                best = direction;
            }
        }
        return new Vec2(best[0], best[1]);
    }

    private static boolean nearParallel(Map<NodeId, NodeState> states, SchematicEdge first, SchematicEdge second, SchematicLayoutConfig config) {
        NodeState a1 = states.get(first.from());
        NodeState a2 = states.get(first.to());
        NodeState b1 = states.get(second.from());
        NodeState b2 = states.get(second.to());
        if (a1 == null || a2 == null || b1 == null || b2 == null) {
            return false;
        }
        double ax = a2.x - a1.x;
        double ay = a2.z - a1.z;
        double bx = b2.x - b1.x;
        double by = b2.z - b1.z;
        double al = Math.hypot(ax, ay);
        double bl = Math.hypot(bx, by);
        if (al < 24.0D || bl < 24.0D) {
            return false;
        }
        double aux = ax / al;
        double auy = ay / al;
        double bux = bx / bl;
        double buy = by / bl;
        if (Math.abs(aux * bux + auy * buy) < 0.982D) {
            return false;
        }
        double distance = (distanceToInfiniteLine(b1.x, b1.z, a1.x, a1.z, aux, auy)
                + distanceToInfiniteLine(b2.x, b2.z, a1.x, a1.z, aux, auy)
                + distanceToInfiniteLine(a1.x, a1.z, b1.x, b1.z, bux, buy)
                + distanceToInfiniteLine(a2.x, a2.z, b1.x, b1.z, bux, buy)) * 0.25D;
        if (distance > config.minNodeGapBlocks()) {
            return false;
        }
        double secondA = projection(b1.x, b1.z, a1.x, a1.z, aux, auy);
        double secondB = projection(b2.x, b2.z, a1.x, a1.z, aux, auy);
        double overlap = Math.min(al, Math.max(secondA, secondB)) - Math.max(0.0D, Math.min(secondA, secondB));
        return overlap >= Math.min(al, bl) * 0.25D;
    }

    private static double obstaclePenalty(List<Vec2> path, SchematicEdge edge, Map<NodeId, NodeState> states) {
        double penalty = 0.0D;
        for (NodeState state : states.values()) {
            if (state.node.id().equals(edge.from()) || state.node.id().equals(edge.to())) {
                continue;
            }
            double radius = nodeObstacleRadius(state.node);
            double distance = distanceToPolyline(new Vec2(state.x, state.z), path);
            if (distance < radius) {
                penalty += square((radius - distance) / radius) * 5.0D;
            }
        }
        return penalty;
    }

    private static double edgeCollisionPenalty(List<Vec2> path, SchematicEdge edge, List<VisualEdgePath> existingPaths, SchematicLayoutConfig config) {
        if (existingPaths.isEmpty()) {
            return 0.0D;
        }
        double penalty = 0.0D;
        double overlapThreshold = Math.max(4.0D, config.corridorOffsetBlocks() * 0.65D);
        for (VisualEdgePath existing : existingPaths) {
            if (sharesEndpoint(edge, existing)) {
                continue;
            }
            if (polylinesIntersect(path, existing.points())) {
                penalty += config.edgeCrossingWeight();
            }
            penalty += polylineProximityPenalty(path, existing.points(), overlapThreshold) * config.edgeOverlapWeight();
        }
        return penalty;
    }

    private static double polylineProximityPenalty(List<Vec2> first, List<Vec2> second, double threshold) {
        if (threshold <= EPSILON) {
            return 0.0D;
        }
        double penalty = 0.0D;
        for (int i = 0; i + 1 < first.size(); i++) {
            Vec2 a = first.get(i);
            Vec2 b = first.get(i + 1);
            for (int j = 0; j + 1 < second.size(); j++) {
                double distance = segmentToSegmentDistance(a, b, second.get(j), second.get(j + 1));
                if (distance < threshold) {
                    penalty += (threshold - distance) / threshold;
                }
            }
        }
        return Math.min(3.0D, penalty);
    }

    private static double segmentToSegmentDistance(Vec2 a, Vec2 b, Vec2 c, Vec2 d) {
        if (segmentsIntersect(a, b, c, d)) {
            return 0.0D;
        }
        return Math.min(
                Math.min(distanceToSegment(a, c, d), distanceToSegment(b, c, d)),
                Math.min(distanceToSegment(c, a, b), distanceToSegment(d, a, b)));
    }

    private static boolean sharesEndpoint(SchematicEdge edge, VisualEdgePath path) {
        return edge.from().equals(path.from()) || edge.from().equals(path.to()) || edge.to().equals(path.from()) || edge.to().equals(path.to());
    }

    private static double directionPenalty(List<Vec2> path, SchematicLayoutConfig config) {
        double penalty = 0.0D;
        for (int i = 0; i + 1 < path.size(); i++) {
            Vec2 a = path.get(i);
            Vec2 b = path.get(i + 1);
            double dx = b.x() - a.x();
            double dz = b.y() - a.y();
            double length = Math.hypot(dx, dz);
            if (length < EPSILON) {
                continue;
            }
            Vec2 nearest = nearestDirection(dx, dz, config.directionSetMode());
            double dot = Math.abs(dx / length * nearest.x() + dz / length * nearest.y());
            penalty += (1.0D - dot) * 0.9D;
        }
        return penalty;
    }

    private static List<Vec2> loopPath(Vec2 a, Vec2 b, String id) {
        double dx = b.x() - a.x();
        double dz = b.y() - a.y();
        double length = Math.max(1.0D, Math.hypot(dx, dz));
        double nx = -dz / length;
        double nz = dx / length;
        if ((id.hashCode() & 1) == 0) {
            nx = -nx;
            nz = -nz;
        }
        double amount = Math.max(16.0D, Math.min(40.0D, length * 0.28D));
        Vec2 upper = new Vec2((a.x() + b.x()) * 0.5D + nx * amount, (a.y() + b.y()) * 0.5D + nz * amount);
        Vec2 lower = new Vec2((a.x() + b.x()) * 0.5D - nx * amount, (a.y() + b.y()) * 0.5D - nz * amount);
        if (length < 14.0D) {
            Vec2 far = new Vec2((a.x() + b.x()) * 0.5D + nx * 24.0D, (a.y() + b.y()) * 0.5D + nz * 24.0D);
            Vec2 near = new Vec2((a.x() + b.x()) * 0.5D - nx * 24.0D, (a.y() + b.y()) * 0.5D - nz * 24.0D);
            return List.of(a, far, b, near, a);
        }
        return List.of(a, upper, b, lower, a);
    }

    private static List<Vec2> offsetPath(List<Vec2> points, double offset) {
        if (points.size() < 2 || Math.abs(offset) < EPSILON) {
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
                double nz = first.y() + second.y();
                double length = Math.hypot(nx, nz);
                normal = length < EPSILON ? second : new Vec2(nx / length, nz / length);
            }
            result.add(new Vec2(points.get(i).x() + normal.x() * offset, points.get(i).y() + normal.y() * offset));
        }
        return result;
    }

    private static List<Vec2> offsetPathAnchored(List<Vec2> points, double offset) {
        if (points.size() < 2 || Math.abs(offset) < EPSILON) {
            return points;
        }
        double length = polylineLength(points);
        if (length < EPSILON) {
            return points;
        }
        List<Vec2> shifted = offsetPath(points, offset);
        double ramp = Math.max(4.0D, Math.min(14.0D, length * 0.18D));
        List<Vec2> result = new ArrayList<>();
        result.add(points.getFirst());
        if (length <= ramp * 2.0D + 1.0D) {
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
            double segmentLength = a.distanceTo(b);
            if (walked + segmentLength >= distance) {
                double t = segmentLength < EPSILON ? 0.0D : (distance - walked) / segmentLength;
                return new Vec2(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t);
            }
            walked += segmentLength;
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
            if (result.isEmpty() || result.getLast().distanceTo(point) > 0.1D) {
                result.add(point);
            }
        }
        return result.size() < 2 ? points : result;
    }

    private static Vec2 segmentNormal(Vec2 a, Vec2 b) {
        double dx = b.x() - a.x();
        double dz = b.y() - a.y();
        double length = Math.hypot(dx, dz);
        if (length < EPSILON) {
            return new Vec2(0.0D, 1.0D);
        }
        return new Vec2(-dz / length, dx / length);
    }

    private static List<LabelCandidate> labelCandidates(NodeState state) {
        double radius = nodeObstacleRadius(state.node) * 0.62D;
        double width = Math.max(24.0D, state.node.label().length() * 4.4D);
        double height = 10.0D;
        double gap = Math.max(8.0D, radius * 0.35D);
        double near = radius + gap;
        double diagonal = near * 0.72D;
        List<LabelCandidate> candidates = new ArrayList<>();
        addLabelCandidate(candidates, state.x + near, state.z - height * 0.5D, width, height);
        addLabelCandidate(candidates, state.x - near - width, state.z - height * 0.5D, width, height);
        addLabelCandidate(candidates, state.x - width * 0.5D, state.z + near, width, height);
        addLabelCandidate(candidates, state.x - width * 0.5D, state.z - near - height, width, height);
        addLabelCandidate(candidates, state.x + diagonal, state.z + diagonal, width, height);
        addLabelCandidate(candidates, state.x - diagonal - width, state.z + diagonal, width, height);
        addLabelCandidate(candidates, state.x + diagonal, state.z - diagonal - height, width, height);
        addLabelCandidate(candidates, state.x - diagonal - width, state.z - diagonal - height, width, height);
        return candidates;
    }

    private static void addLabelCandidate(List<LabelCandidate> candidates, double x, double z, double width, double height) {
        candidates.add(new LabelCandidate(x, z, new LabelBox(x, z, x + width, z + height, 0)));
    }

    private static double labelBaseScale(SchematicNode node) {
        return switch (node.kind()) {
            case CLUSTER -> 0.82D;
            case DEEP_CLUSTER -> 0.78D;
            case FOLD_ANCHOR -> 0.74D;
            case STATION -> node.importance() >= 700 ? 0.76D : 0.68D;
        };
    }

    private static int countEdgeCrossings(List<VisualEdgePath> paths) {
        int count = 0;
        for (int i = 0; i < paths.size(); i++) {
            VisualEdgePath first = paths.get(i);
            if (first.kind() == SemanticEdgeKind.FOLD_ADJACENT || first.kind() == SemanticEdgeKind.LOOP_BACK) {
                continue;
            }
            for (int j = i + 1; j < paths.size(); j++) {
                VisualEdgePath second = paths.get(j);
                if (sharesEndpoint(first, second) || second.kind() == SemanticEdgeKind.FOLD_ADJACENT || second.kind() == SemanticEdgeKind.LOOP_BACK) {
                    continue;
                }
                if (polylinesIntersect(first.points(), second.points())) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countLabelOverlaps(List<VisualLabel> labels) {
        int count = 0;
        for (int i = 0; i < labels.size(); i++) {
            VisualLabel first = labels.get(i);
            LabelBox a = new LabelBox(first.x(), first.z(), first.x() + first.text().length() * 4.4D, first.z() + 10.0D, first.priority());
            for (int j = i + 1; j < labels.size(); j++) {
                VisualLabel second = labels.get(j);
                LabelBox b = new LabelBox(second.x(), second.z(), second.x() + second.text().length() * 4.4D, second.z() + 10.0D, second.priority());
                if (a.intersects(b)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean sharesEndpoint(VisualEdgePath first, VisualEdgePath second) {
        return first.from().equals(second.from()) || first.from().equals(second.to()) || first.to().equals(second.from()) || first.to().equals(second.to());
    }

    private static boolean polylinesIntersect(List<Vec2> first, List<Vec2> second) {
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
        double ab1 = cross(a, b, c);
        double ab2 = cross(a, b, d);
        double cd1 = cross(c, d, a);
        double cd2 = cross(c, d, b);
        return ab1 * ab2 < 0.0D && cd1 * cd2 < 0.0D;
    }

    private static double cross(Vec2 a, Vec2 b, Vec2 c) {
        return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
    }

    private static double minNodeDistance(SchematicNode first, SchematicNode second, SchematicLayoutConfig config) {
        return nodeObstacleRadius(first) + nodeObstacleRadius(second) + config.minNodeGapBlocks() * 0.28D;
    }

    private static double nodeObstacleRadius(SchematicNode node) {
        double screenRadius = switch (node.kind()) {
            case CLUSTER, DEEP_CLUSTER -> FullRouteMapConfig.CLUSTER_RADIUS_PX + 5.0D;
            case FOLD_ANCHOR -> FullRouteMapConfig.NODE_RADIUS_PX + 7.0D;
            case STATION -> node.importance() >= 700 ? FullRouteMapConfig.NODE_RADIUS_PX * 2.2D : FullRouteMapConfig.NODE_RADIUS_PX + 4.0D;
        };
        return screenRadius / FullRouteMapConfig.BASE_SCALE;
    }

    private static double hitRadiusBlocks(SchematicEdge edge) {
        int laneCount = Math.max(1, edge.routeLineIds().size());
        return (FullRouteMapConfig.LINE_WIDTH_PX * laneCount + 6.0D) / FullRouteMapConfig.BASE_SCALE;
    }

    private static double distanceToPolyline(Vec2 point, List<Vec2> path) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i + 1 < path.size(); i++) {
            best = Math.min(best, distanceToSegment(point, path.get(i), path.get(i + 1)));
        }
        return best;
    }

    private static double distanceToSegment(Vec2 point, Vec2 a, Vec2 b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double len2 = dx * dx + dy * dy;
        if (len2 < EPSILON) {
            return point.distanceTo(a);
        }
        double t = clamp(((point.x() - a.x()) * dx + (point.y() - a.y()) * dy) / len2, 0.0D, 1.0D);
        return point.distanceTo(new Vec2(a.x() + dx * t, a.y() + dy * t));
    }

    private static double polylineLength(List<Vec2> points) {
        double length = 0.0D;
        for (int i = 0; i + 1 < points.size(); i++) {
            length += points.get(i).distanceTo(points.get(i + 1));
        }
        return length;
    }

    private static Aabb2 boundsForPoints(List<Vec2> points) {
        Aabb2 bounds = Aabb2.empty();
        for (Vec2 point : points) {
            bounds = bounds.include(point.x(), point.y());
        }
        return bounds;
    }

    private static double distanceToInfiniteLine(double x, double y, double originX, double originY, double ux, double uy) {
        return Math.abs((x - originX) * uy - (y - originY) * ux);
    }

    private static double projection(double x, double y, double originX, double originY, double ux, double uy) {
        return (x - originX) * ux + (y - originY) * uy;
    }

    private static double hashAngle(NodeId first, NodeId second) {
        int hash = first.hashCode() * 31 + second.hashCode();
        return (hash & 0xFFFF) / 65535.0D * Math.PI * 2.0D;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double square(double value) {
        return value * value;
    }

    private static final class NodeState {
        private final SchematicNode node;
        private final Optional<VisualRouteMapGraphSnapshot.Position> previousPosition;
        private double x;
        private double z;
        private double fx;
        private double fz;

        private NodeState(SchematicNode node, double x, double z, Optional<VisualRouteMapGraphSnapshot.Position> previousPosition) {
            this.node = node;
            this.x = x;
            this.z = z;
            this.previousPosition = previousPosition;
        }
    }

    private record GridCell(int x, int z) {
        static GridCell of(double x, double z, double cellSize) {
            return new GridCell((int) Math.floor(x / cellSize), (int) Math.floor(z / cellSize));
        }
    }

    private record RoutedPath(List<Vec2> points, boolean fallback, boolean loopGlyph) {}

    private record RouteOutput(List<VisualEdgePath> edgePaths, int fallbackEdges, int stationInternalEdges, int bendCount, int loopGlyphCount) {}

    private record LabelCandidate(double x, double z, LabelBox box) {}

    private record LabelBox(double minX, double minZ, double maxX, double maxZ, int priority) {
        boolean intersects(LabelBox other) {
            return this.minX < other.maxX && this.maxX > other.minX && this.minZ < other.maxZ && this.maxZ > other.minZ;
        }
    }
}
