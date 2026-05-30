package dev.marblegate.superpipeslide.client.fullmap.schematic.solve;

import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.model.MapEdgeOccurrence;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeKind;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orientation-locked transit diagram solver for the pure SCHEMATIC map mode.
 *
 * <p>Station coordinates are embedded with an order-preserving transform: if a
 * station is west/east or north/south of another station in world space, the
 * schematic position keeps that ordering. The solver may stretch distances and
 * route edges through octilinear bends, but it must not reorder stations.</p>
 */
public final class MetroMapSchematicSolver implements SchematicSolverBackend {
    private static final double EPSILON = 1.0E-6D;
    private static final double SQRT_HALF = Math.sqrt(0.5D);
    private static final List<Vec2> DIRECTIONS = List.of(
            new Vec2(1.0D, 0.0D),
            new Vec2(SQRT_HALF, SQRT_HALF),
            new Vec2(0.0D, 1.0D),
            new Vec2(-SQRT_HALF, SQRT_HALF),
            new Vec2(-1.0D, 0.0D),
            new Vec2(-SQRT_HALF, -SQRT_HALF),
            new Vec2(0.0D, -1.0D),
            new Vec2(SQRT_HALF, -SQRT_HALF));
    private static final List<LayoutProfile> PROFILES = List.of(
            new LayoutProfile("balanced", 118.0D, 1.42D, 20, 0.82D, false),
            new LayoutProfile("wide", 112.0D, 1.72D, 22, 0.78D, false),
            new LayoutProfile("compact", 104.0D, 1.36D, 18, 0.74D, true),
            new LayoutProfile("fallback", 96.0D, 1.58D, 16, 0.72D, true));

    @Override
    public SchematicLayoutResult solve(SchematicInputGraph input, SchematicLayoutConfig config, Optional<VisualRouteMapGraphSnapshot> previous) {
        if (input.nodes().isEmpty()) {
            return new SchematicLayoutResult(emptyGraph(input));
        }

        long startNanos = System.nanoTime();
        MetroTopology topology = MetroTopology.build(input);
        LayoutAttempt best = null;
        for (LayoutProfile profile : PROFILES) {
            LayoutAttempt attempt = this.solveWithProfile(input, topology, profile, startNanos);
            if (best == null || attempt.score() < best.score()) {
                best = attempt;
            }
        }
        return new SchematicLayoutResult(this.visualGraph(input, best == null ? this.solveWithProfile(input, topology, PROFILES.getLast(), startNanos) : best));
    }

    private LayoutAttempt solveWithProfile(SchematicInputGraph input, MetroTopology topology, LayoutProfile profile, long startNanos) {
        Map<NodeId, Vec2> positions = this.layoutOrientationLocked(topology, profile);
        ConstraintStats constraints = this.measureGlobalConstraints(topology, positions, profile);
        RouteOutput routeOutput = this.routeEdges(input, topology, positions, profile);
        List<VisualLabel> labels = this.layoutLabels(topology, positions, routeOutput.edgePaths(), profile);
        SchematicQualityReport quality = this.quality(input, topology, positions, routeOutput, labels, constraints, profile, startNanos);
        double score = this.qualityScore(quality, boundsForPositions(positions), profile);
        return new LayoutAttempt(profile, positions, routeOutput, labels, quality, score);
    }

    private Map<NodeId, Vec2> layoutOrientationLocked(MetroTopology topology, LayoutProfile profile) {
        List<NodeId> stations = topology.nodesById().keySet().stream()
                .filter(id -> topology.node(id).kind() == NodeKind.STATION)
                .sorted(NodeId::compareTo)
                .toList();
        Map<NodeId, Vec2> positions = new LinkedHashMap<>();
        if (!stations.isEmpty()) {
            Map<NodeId, Double> xPositions = this.axisPositions(stations, topology, true, profile.stationSpacing());
            Map<NodeId, Double> yPositions = this.axisPositions(stations, topology, false, profile.stationSpacing());
            for (NodeId station : stations) {
                positions.put(station, new Vec2(xPositions.getOrDefault(station, 0.0D), yPositions.getOrDefault(station, 0.0D)));
            }
            this.separateExactDuplicateStations(stations, topology, positions, profile);
        }

        List<NodeId> portals = topology.nodesById().keySet().stream()
                .filter(id -> topology.node(id).kind() == NodeKind.FOLD_ANCHOR)
                .sorted(NodeId::compareTo)
                .toList();
        for (NodeId portal : portals) {
            Optional<NodeId> anchor = topology.neighbors(portal).stream()
                    .filter(positions::containsKey)
                    .filter(id -> topology.node(id).kind() == NodeKind.STATION)
                    .findFirst();
            if (anchor.isEmpty()) {
                continue;
            }
            SchematicNode portalNode = topology.node(portal);
            SchematicNode anchorNode = topology.node(anchor.get());
            Vec2 preferred = nearestDirection(portalNode.worldX() - anchorNode.worldX(), portalNode.worldZ() - anchorNode.worldZ());
            Vec2 origin = positions.get(anchor.get());
            positions.put(portal, this.bestPortalSlot(topology, portal, origin, preferred, positions, profile));
        }

        for (NodeId nodeId : topology.nodesById().keySet().stream().sorted(NodeId::compareTo).toList()) {
            if (positions.containsKey(nodeId)) {
                continue;
            }
            SchematicNode node = topology.node(nodeId);
            positions.put(nodeId, new Vec2(node.worldX(), node.worldZ()));
        }
        return centerPositions(positions);
    }

    private Map<NodeId, Double> axisPositions(List<NodeId> stations, MetroTopology topology, boolean xAxis, double spacing) {
        List<NodeId> sorted = stations.stream()
                .sorted(Comparator
                        .comparingDouble((NodeId id) -> axisValue(topology.node(id), xAxis))
                        .thenComparingDouble(id -> axisValue(topology.node(id), !xAxis))
                        .thenComparing(NodeId::compareTo))
                .toList();
        Map<NodeId, Double> result = new LinkedHashMap<>();
        if (sorted.isEmpty()) {
            return result;
        }
        double cursor = 0.0D;
        double previous = axisValue(topology.node(sorted.getFirst()), xAxis);
        result.put(sorted.getFirst(), cursor);
        for (int i = 1; i < sorted.size(); i++) {
            NodeId nodeId = sorted.get(i);
            double current = axisValue(topology.node(nodeId), xAxis);
            double delta = current - previous;
            if (Math.abs(delta) > EPSILON) {
                cursor += axisGap(Math.abs(delta), spacing);
            }
            result.put(nodeId, cursor);
            previous = current;
        }
        double center = (result.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0D)
                + result.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0D)) * 0.5D;
        result.replaceAll((id, value) -> value - center);
        return result;
    }

    private static double axisValue(SchematicNode node, boolean xAxis) {
        return xAxis ? node.worldX() : node.worldZ();
    }

    private static double axisGap(double worldDelta, double spacing) {
        double geographicHint = Math.sqrt(Math.max(1.0D, worldDelta)) * 6.5D;
        return Math.max(spacing, Math.min(spacing * 2.35D, geographicHint));
    }

    private void separateExactDuplicateStations(List<NodeId> stations, MetroTopology topology, Map<NodeId, Vec2> positions, LayoutProfile profile) {
        Map<String, List<NodeId>> groups = new LinkedHashMap<>();
        for (NodeId station : stations) {
            SchematicNode node = topology.node(station);
            String key = Math.round(node.worldX() * 1000.0D) + ":" + Math.round(node.worldZ() * 1000.0D);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(station);
        }
        for (List<NodeId> group : groups.values()) {
            if (group.size() <= 1) {
                continue;
            }
            group.sort(NodeId::compareTo);
            Vec2 center = positions.get(group.getFirst());
            double radius = profile.stationSpacing() * 0.22D;
            for (int i = 0; i < group.size(); i++) {
                double angle = Math.PI * 2.0D * i / group.size();
                positions.put(group.get(i), new Vec2(center.x() + Math.cos(angle) * radius, center.y() + Math.sin(angle) * radius));
            }
        }
    }

    private Vec2 bestPortalSlot(MetroTopology topology, NodeId portalId, Vec2 origin, Vec2 preferredDirection, Map<NodeId, Vec2> positions, LayoutProfile profile) {
        SchematicNode portal = topology.node(portalId);
        Vec2 best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int ring = 1; ring <= 4; ring++) {
            double radius = profile.boundarySpacing() * ring;
            for (Vec2 direction : orderedDirections(preferredDirection)) {
                Vec2 candidate = new Vec2(origin.x() + direction.x() * radius, origin.y() + direction.y() * radius);
                double score = (1.0D - dot(direction, preferredDirection)) * 90.0D + ring * 7.0D;
                for (Map.Entry<NodeId, Vec2> entry : positions.entrySet()) {
                    SchematicNode other = topology.node(entry.getKey());
                    double min = nodeObstacleRadius(portal) + nodeObstacleRadius(other) + profile.nodeGap();
                    double distance = candidate.distanceTo(entry.getValue());
                    if (distance < min) {
                        score += 6_000.0D + (min - distance) * 80.0D;
                    }
                }
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            if (bestScore < 5_000.0D) {
                break;
            }
        }
        return best == null ? new Vec2(origin.x() + preferredDirection.x() * profile.boundarySpacing(), origin.y() + preferredDirection.y() * profile.boundarySpacing()) : best;
    }

    private ComponentLayout layoutComponent(MetroTopology topology, MetroComponent component, LayoutProfile profile) {
        Map<NodeId, Vec2> positions;
        Optional<RouteRun> primary = this.primaryRun(component);
        if (primary.isPresent() && this.isClosedWithinComponent(primary.get(), component) && componentSequence(primary.get(), component).size() >= 4) {
            positions = this.layoutLoopComponent(primary.get(), component, profile);
        } else if (this.starCenter(topology, component).isPresent()) {
            positions = this.layoutStarComponent(topology, component, this.starCenter(topology, component).get(), profile);
        } else if (primary.isPresent()) {
            positions = this.layoutRunBackbone(topology, component, primary.get(), profile);
        } else {
            positions = this.layoutRadialFallback(topology, component, profile);
        }

        this.placeRemainingRuns(topology, component, positions, profile);
        this.placeUnpositionedNodes(topology, component, positions, profile);
        this.repairComponent(topology, component, positions, profile);
        this.preserveWorldOrientation(component, positions);
        positions = centerPositions(positions);
        Aabb2 bounds = boundsForComponent(component.nodeIds(), positions).inflate(profile.stationSpacing() * 0.40D);
        return new ComponentLayout(component, positions, bounds);
    }

    private Map<NodeId, Vec2> layoutRunBackbone(MetroTopology topology, MetroComponent component, RouteRun run, LayoutProfile profile) {
        List<NodeId> sequence = componentSequence(run, component);
        Map<NodeId, Vec2> positions = new LinkedHashMap<>();
        if (sequence.isEmpty()) {
            return positions;
        }
        int n = sequence.size();
        boolean fold = n >= Math.max(profile.longRunThreshold(), profile.aggressiveFold() ? 18 : 22);
        if (!fold) {
            Vec2 axis = this.primaryAxisFor(component, run);
            for (int i = 0; i < n; i++) {
                positions.put(sequence.get(i), new Vec2(axis.x() * i * profile.stationSpacing(), axis.y() * i * profile.stationSpacing()));
            }
            return centerPositions(positions);
        }

        Vec2 axis = this.primaryAxisFor(component, run);
        Vec2 rowAxis = new Vec2(-axis.y(), axis.x());
        int columns = Math.max(7, (int) Math.ceil(Math.sqrt(n * profile.targetAspect())));
        columns = Math.min(n, columns);
        int rows = (int) Math.ceil(n / (double) columns);
        while (rows > 1 && columns > 7 && (rows - 1) * profile.rowSpacing() > columns * profile.stationSpacing() * 0.85D) {
            columns++;
            rows = (int) Math.ceil(n / (double) columns);
        }
        for (int i = 0; i < n; i++) {
            int row = i / columns;
            int columnInRow = i % columns;
            double x = axis.x() * columnInRow * profile.stationSpacing() + rowAxis.x() * row * profile.rowSpacing();
            double y = axis.y() * columnInRow * profile.stationSpacing() + rowAxis.y() * row * profile.rowSpacing();
            positions.put(sequence.get(i), new Vec2(x, y));
        }
        return centerPositions(positions);
    }

    private Optional<RouteRun> primaryRun(MetroComponent component) {
        return component.runs().stream()
                .filter(run -> componentSequence(run, component).size() >= 2)
                .max(Comparator
                        .comparingInt((RouteRun run) -> componentSequence(run, component).size() * 100)
                        .thenComparingInt(RouteRun::score));
    }

    private Map<NodeId, Vec2> layoutLoopComponent(RouteRun run, MetroComponent component, LayoutProfile profile) {
        List<NodeId> sequence = componentSequence(run, component);
        Map<NodeId, Vec2> positions = new LinkedHashMap<>();
        int n = sequence.size();
        if (n == 0) {
            return positions;
        }
        double width = Math.max(profile.stationSpacing() * 2.2D, Math.ceil(Math.sqrt(n * profile.targetAspect())) * profile.stationSpacing());
        double height = Math.max(profile.stationSpacing() * 1.6D, width / profile.targetAspect());
        double perimeter = 2.0D * (width + height);
        for (int i = 0; i < n; i++) {
            double distance = perimeter * i / n;
            positions.put(sequence.get(i), pointOnRectanglePerimeter(width, height, distance));
        }
        return centerPositions(positions);
    }

    private Map<NodeId, Vec2> layoutStarComponent(MetroTopology topology, MetroComponent component, NodeId centerId, LayoutProfile profile) {
        Map<NodeId, Vec2> positions = new LinkedHashMap<>();
        positions.put(centerId, new Vec2(0.0D, 0.0D));
        List<List<NodeId>> arms = this.starArms(topology, component, centerId);
        arms.sort(Comparator.comparingDouble(arm -> {
            SchematicNode node = topology.node(arm.get(Math.min(1, arm.size() - 1)));
            SchematicNode center = topology.node(centerId);
            return Math.atan2(node.worldZ() - center.worldZ(), node.worldX() - center.worldX());
        }));
        for (int i = 0; i < arms.size(); i++) {
            Vec2 direction = DIRECTIONS.get((int) Math.round(i * DIRECTIONS.size() / (double) Math.max(1, arms.size())) % DIRECTIONS.size());
            List<NodeId> arm = arms.get(i);
            for (int j = 1; j < arm.size(); j++) {
                positions.putIfAbsent(arm.get(j), new Vec2(direction.x() * j * profile.stationSpacing(), direction.y() * j * profile.stationSpacing()));
            }
        }
        return centerPositions(positions);
    }

    private Map<NodeId, Vec2> layoutRadialFallback(MetroTopology topology, MetroComponent component, LayoutProfile profile) {
        Map<NodeId, Vec2> positions = new LinkedHashMap<>();
        List<NodeId> nodes = component.nodeIds().stream()
                .sorted(Comparator.comparingInt((NodeId id) -> topology.degree(id)).reversed().thenComparing(NodeId::compareTo))
                .toList();
        if (nodes.isEmpty()) {
            return positions;
        }
        positions.put(nodes.getFirst(), new Vec2(0.0D, 0.0D));
        for (int i = 1; i < nodes.size(); i++) {
            double angle = i * 2.399963229728653D;
            double radius = profile.stationSpacing() * (0.9D + Math.sqrt(i) * 0.42D);
            positions.put(nodes.get(i), new Vec2(Math.cos(angle) * radius, Math.sin(angle) * radius));
        }
        return centerPositions(positions);
    }

    private void placeRemainingRuns(MetroTopology topology, MetroComponent component, Map<NodeId, Vec2> positions, LayoutProfile profile) {
        List<RouteRun> runs = component.runs().stream()
                .sorted(Comparator.comparingInt(RouteRun::score).reversed())
                .toList();
        for (RouteRun run : runs) {
            List<NodeId> sequence = componentSequence(run, component);
            for (int i = 0; i < sequence.size(); i++) {
                NodeId nodeId = sequence.get(i);
                if (positions.containsKey(nodeId)) {
                    continue;
                }
                Optional<PlacementAnchor> anchor = this.nearestSequenceAnchor(sequence, i, positions);
                if (anchor.isPresent()) {
                    Vec2 direction = this.directionForPlacement(topology, anchor.get().nodeId(), nodeId, anchor.get().forward());
                    positions.put(nodeId, this.bestOpenSlot(topology, component, nodeId, positions.get(anchor.get().nodeId()), direction, positions, profile, profile.stationSpacing()));
                }
            }
        }
    }

    private void placeUnpositionedNodes(MetroTopology topology, MetroComponent component, Map<NodeId, Vec2> positions, LayoutProfile profile) {
        for (NodeId nodeId : component.nodeIds().stream().sorted(NodeId::compareTo).toList()) {
            if (positions.containsKey(nodeId)) {
                continue;
            }
            List<NodeId> placedNeighbors = topology.neighbors(nodeId).stream()
                    .filter(positions::containsKey)
                    .toList();
            Vec2 origin = placedNeighbors.isEmpty()
                    ? new Vec2(0.0D, 0.0D)
                    : average(placedNeighbors.stream().map(positions::get).toList());
            Vec2 direction = placedNeighbors.isEmpty()
                    ? hashDirection(nodeId)
                    : this.directionForPlacement(topology, placedNeighbors.getFirst(), nodeId, true);
            double distance = topology.node(nodeId).kind() == NodeKind.FOLD_ANCHOR ? profile.boundarySpacing() : profile.stationSpacing();
            positions.put(nodeId, this.bestOpenSlot(topology, component, nodeId, origin, direction, positions, profile, distance));
        }
    }

    private Vec2 bestOpenSlot(MetroTopology topology, MetroComponent component, NodeId nodeId, Vec2 origin, Vec2 preferredDirection, Map<NodeId, Vec2> positions, LayoutProfile profile, double distance) {
        SchematicNode node = topology.node(nodeId);
        Vec2 best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int ring = 1; ring <= 5; ring++) {
            double radius = distance * ring;
            for (Vec2 direction : orderedDirections(preferredDirection)) {
                Vec2 candidate = new Vec2(origin.x() + direction.x() * radius, origin.y() + direction.y() * radius);
                double score = candidate.distanceTo(origin) * 0.04D + (1.0D - dot(direction, preferredDirection)) * 24.0D;
                Aabb2 bounds = nodeBounds(node, candidate);
                for (Map.Entry<NodeId, Vec2> entry : positions.entrySet()) {
                    SchematicNode other = topology.node(entry.getKey());
                    double min = minNodeDistance(node, other, profile, topology.connected(nodeId, entry.getKey()));
                    double actual = candidate.distanceTo(entry.getValue());
                    if (actual < min) {
                        score += 10_000.0D + (min - actual) * 160.0D;
                    }
                    if (bounds.intersects(nodeBounds(other, entry.getValue()))) {
                        score += 4_000.0D;
                    }
                }
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            if (bestScore < 8_000.0D) {
                break;
            }
        }
        return best == null ? new Vec2(origin.x() + preferredDirection.x() * distance, origin.y() + preferredDirection.y() * distance) : best;
    }

    private void repairComponent(MetroTopology topology, MetroComponent component, Map<NodeId, Vec2> positions, LayoutProfile profile) {
        List<NodeId> nodes = component.nodeIds().stream().filter(positions::containsKey).sorted(NodeId::compareTo).toList();
        for (int iteration = 0; iteration < 84; iteration++) {
            boolean moved = false;
            for (int i = 0; i < nodes.size(); i++) {
                NodeId first = nodes.get(i);
                for (int j = i + 1; j < nodes.size(); j++) {
                    NodeId second = nodes.get(j);
                    double min = minNodeDistance(topology.node(first), topology.node(second), profile, topology.connected(first, second));
                    Vec2 a = positions.get(first);
                    Vec2 b = positions.get(second);
                    double distance = a.distanceTo(b);
                    if (distance >= min) {
                        continue;
                    }
                    Vec2 dir = distance < EPSILON ? hashDirection(first, second) : new Vec2((b.x() - a.x()) / distance, (b.y() - a.y()) / distance);
                    double push = (min - distance) * 0.52D;
                    positions.put(first, new Vec2(a.x() - dir.x() * push, a.y() - dir.y() * push));
                    positions.put(second, new Vec2(b.x() + dir.x() * push, b.y() + dir.y() * push));
                    moved = true;
                }
            }
            for (SchematicEdge edge : component.edges()) {
                if (edge.kind() == SemanticEdgeKind.STATION_INTERNAL || edge.from().equals(edge.to())) {
                    continue;
                }
                Vec2 a = positions.get(edge.from());
                Vec2 b = positions.get(edge.to());
                if (a == null || b == null) {
                    continue;
                }
                double min = minConnectedDistance(topology.node(edge.from()), topology.node(edge.to()), profile);
                double distance = a.distanceTo(b);
                if (distance >= min) {
                    continue;
                }
                Vec2 dir = distance < EPSILON ? this.directionForPlacement(topology, edge.from(), edge.to(), true) : new Vec2((b.x() - a.x()) / distance, (b.y() - a.y()) / distance);
                double push = (min - distance) * 0.46D;
                positions.put(edge.from(), new Vec2(a.x() - dir.x() * push, a.y() - dir.y() * push));
                positions.put(edge.to(), new Vec2(b.x() + dir.x() * push, b.y() + dir.y() * push));
                moved = true;
            }
            if (!moved) {
                break;
            }
        }
    }

    private ConstraintStats measureGlobalConstraints(MetroTopology topology, Map<NodeId, Vec2> positions, LayoutProfile profile) {
        int nodeOverlaps = 0;
        int edgeNodeConflicts = 0;
        List<NodeId> ids = positions.keySet().stream().sorted(NodeId::compareTo).toList();
        for (int i = 0; i < ids.size(); i++) {
            NodeId first = ids.get(i);
            for (int j = i + 1; j < ids.size(); j++) {
                NodeId second = ids.get(j);
                double min = minNodeDistance(topology.node(first), topology.node(second), profile, topology.connected(first, second));
                if (positions.get(first).distanceTo(positions.get(second)) < min) {
                    nodeOverlaps++;
                }
            }
        }
        for (SchematicEdge edge : topology.edges()) {
            Vec2 a = positions.get(edge.from());
            Vec2 b = positions.get(edge.to());
            if (a == null || b == null || edge.kind() == SemanticEdgeKind.STATION_INTERNAL) {
                continue;
            }
            for (Map.Entry<NodeId, Vec2> entry : positions.entrySet()) {
                if (entry.getKey().equals(edge.from()) || entry.getKey().equals(edge.to())) {
                    continue;
                }
                double clearance = nodeObstacleRadius(topology.node(entry.getKey())) + 4.0D;
                if (distanceToSegment(entry.getValue(), a, b) < clearance) {
                    edgeNodeConflicts++;
                }
            }
        }
        return new ConstraintStats(nodeOverlaps, edgeNodeConflicts);
    }

    private Map<NodeId, Vec2> packComponents(List<ComponentLayout> components, LayoutProfile profile) {
        if (components.isEmpty()) {
            return Map.of();
        }
        List<ComponentLayout> ordered = components.stream()
                .sorted(Comparator.comparingDouble((ComponentLayout layout) -> area(layout.bounds())).reversed())
                .toList();
        double totalArea = ordered.stream()
                .mapToDouble(layout -> Math.max(1.0D, width(layout.bounds()) + profile.componentGap()) * Math.max(1.0D, height(layout.bounds()) + profile.componentGap()))
                .sum();
        double targetWidth = Math.max(
                ordered.getFirst().bounds().maxX() - ordered.getFirst().bounds().minX(),
                Math.sqrt(totalArea * profile.targetAspect()));
        Map<NodeId, Vec2> result = new LinkedHashMap<>();
        double cursorX = 0.0D;
        double cursorY = 0.0D;
        double shelfHeight = 0.0D;
        for (ComponentLayout layout : ordered) {
            double componentWidth = width(layout.bounds());
            double componentHeight = height(layout.bounds());
            if (!result.isEmpty() && cursorX + componentWidth > targetWidth) {
                cursorX = 0.0D;
                cursorY += shelfHeight + profile.componentGap();
                shelfHeight = 0.0D;
            }
            double offsetX = cursorX - layout.bounds().minX();
            double offsetY = cursorY - layout.bounds().minY();
            for (Map.Entry<NodeId, Vec2> entry : layout.positions().entrySet()) {
                result.put(entry.getKey(), new Vec2(entry.getValue().x() + offsetX, entry.getValue().y() + offsetY));
            }
            cursorX += componentWidth + profile.componentGap();
            shelfHeight = Math.max(shelfHeight, componentHeight);
        }
        return centerPositions(result);
    }

    private RouteOutput routeEdges(SchematicInputGraph input, MetroTopology topology, Map<NodeId, Vec2> positions, LayoutProfile profile) {
        List<VisualEdgePath> paths = new ArrayList<>();
        List<List<Vec2>> routedPaths = new ArrayList<>();
        Map<String, CorridorHint> corridorHints = this.corridorHints(topology, positions);
        int fallbackEdges = 0;
        int loopGlyphs = 0;
        int stationInternalEdges = 0;
        for (SchematicEdge edge : topology.edges().stream().sorted(edgeOrder()).toList()) {
            if (edge.kind() == SemanticEdgeKind.STATION_INTERNAL || edge.from().equals(edge.to())) {
                stationInternalEdges++;
                continue;
            }
            Vec2 from = positions.get(edge.from());
            Vec2 to = positions.get(edge.to());
            if (from == null || to == null) {
                fallbackEdges++;
                continue;
            }
            RoutedPath routed = this.routeEdge(topology, edge, from, to, positions, routedPaths, corridorHints.getOrDefault(edge.id(), CorridorHint.fromEndpoints(from, to)), profile);
            fallbackEdges += routed.fallback() ? 1 : 0;
            loopGlyphs += routed.loopGlyph() ? 1 : 0;
            Aabb2 bounds = boundsForPoints(routed.points()).inflate(hitRadiusBlocks(edge));
            paths.add(new VisualEdgePath(
                    edge.id(),
                    edge.from(),
                    edge.to(),
                    edge.kind(),
                    edge.routeLineIds(),
                    edge.occurrences(),
                    routed.points(),
                    lanesFor(edge),
                    new VisualHitShape(routed.points(), hitRadiusBlocks(edge), bounds),
                    bounds,
                    routed.fallback()));
            routedPaths.add(routed.points());
        }
        int bendCount = paths.stream().mapToInt(path -> Math.max(0, path.points().size() - 2)).sum();
        return new RouteOutput(paths, fallbackEdges, bendCount, loopGlyphs, stationInternalEdges);
    }

    private Map<String, CorridorHint> corridorHints(MetroTopology topology, Map<NodeId, Vec2> positions) {
        Map<String, DirectionVotes> votesByEdge = new LinkedHashMap<>();
        for (SchematicEdge edge : topology.edges()) {
            Vec2 from = positions.get(edge.from());
            Vec2 to = positions.get(edge.to());
            if (from == null || to == null) {
                continue;
            }
            votesByEdge.computeIfAbsent(edge.id(), ignored -> new DirectionVotes(nearestDirection(to.x() - from.x(), to.y() - from.y())));
        }

        for (RouteRun run : topology.runs()) {
            List<NodeId> sequence = run.sequence();
            if (sequence.size() < 2) {
                continue;
            }
            int segments = run.closed() ? sequence.size() : sequence.size() - 1;
            for (int i = 0; i < segments; i++) {
                NodeId a = sequence.get(i);
                NodeId b = sequence.get((i + 1) % sequence.size());
                Optional<SchematicEdge> edge = edgeForPair(run.edges(), a, b);
                if (edge.isEmpty()) {
                    continue;
                }
                Vec2 direction = this.corridorDirectionForRunSegment(run, sequence, i, positions);
                if (edge.get().from().equals(b) && edge.get().to().equals(a)) {
                    direction = reverse(direction);
                }
                Vec2 votedDirection = direction;
                votesByEdge.computeIfAbsent(edge.get().id(), ignored -> new DirectionVotes(votedDirection)).add(votedDirection, 3);
            }
        }

        Map<String, CorridorHint> hints = new LinkedHashMap<>();
        for (Map.Entry<String, DirectionVotes> entry : votesByEdge.entrySet()) {
            hints.put(entry.getKey(), entry.getValue().hint());
        }
        return hints;
    }

    private Vec2 corridorDirectionForRunSegment(RouteRun run, List<NodeId> sequence, int index, Map<NodeId, Vec2> positions) {
        int size = sequence.size();
        NodeId a = sequence.get(index);
        NodeId b = sequence.get((index + 1) % size);
        Vec2 aPos = positions.get(a);
        Vec2 bPos = positions.get(b);
        Vec2 fallback = aPos == null || bPos == null ? new Vec2(1.0D, 0.0D) : nearestDirection(bPos.x() - aPos.x(), bPos.y() - aPos.y());

        NodeId before = index > 0 ? sequence.get(index - 1) : run.closed() ? sequence.get(size - 1) : a;
        NodeId after = index + 2 < size ? sequence.get(index + 2) : run.closed() ? sequence.get((index + 2) % size) : b;
        Vec2 beforePos = positions.get(before);
        Vec2 afterPos = positions.get(after);
        if (beforePos == null || afterPos == null || before.equals(after)) {
            return fallback;
        }
        Vec2 direction = nearestDirection(afterPos.x() - beforePos.x(), afterPos.y() - beforePos.y());
        return dot(direction, fallback) < 0.0D ? reverse(direction) : direction;
    }

    private static Optional<SchematicEdge> edgeForPair(List<SchematicEdge> edges, NodeId a, NodeId b) {
        return edges.stream()
                .filter(edge -> (edge.from().equals(a) && edge.to().equals(b)) || (edge.from().equals(b) && edge.to().equals(a)))
                .findFirst();
    }

    private RoutedPath routeEdge(MetroTopology topology, SchematicEdge edge, Vec2 from, Vec2 to, Map<NodeId, Vec2> positions, List<List<Vec2>> routedPaths, CorridorHint corridorHint, LayoutProfile profile) {
        if (edge.kind() == SemanticEdgeKind.LOOP_BACK) {
            return new RoutedPath(loopPath(from, to, edge.id(), profile), false, true);
        }
        List<List<Vec2>> candidates = this.routeCandidates(from, to, edge, corridorHint, profile);
        List<Vec2> best = List.of(from, to);
        double bestScore = Double.POSITIVE_INFINITY;
        for (List<Vec2> candidate : candidates) {
            double score = routeScore(topology, edge, candidate, positions, routedPaths, corridorHint, profile);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return new RoutedPath(dedupePath(best), !isOctilinearPath(best), false);
    }

    private List<List<Vec2>> routeCandidates(Vec2 from, Vec2 to, SchematicEdge edge, CorridorHint corridorHint, LayoutProfile profile) {
        List<List<Vec2>> candidates = new ArrayList<>();
        double directDistance = Math.max(1.0D, from.distanceTo(to));
        double minLeg = Math.max(8.0D, profile.stationSpacing() * 0.10D);
        double maxLength = directDistance * 2.75D + profile.stationSpacing() * 1.20D;
        if (isOctilinearSegment(from, to)) {
            candidates.add(List.of(from, to));
        }

        List<Vec2> startDirections = orderedDirections(corridorHint.direction());
        List<Vec2> endDirections = orderedDirections(corridorHint.direction());
        for (Vec2 first : startDirections) {
            for (Vec2 entry : endDirections) {
                Optional<Vec2> corner = rayIntersection(from, first, to, reverse(entry));
                if (corner.isEmpty() || corner.get().distanceTo(from) < minLeg || corner.get().distanceTo(to) < minLeg) {
                    continue;
                }
                List<Vec2> path = dedupePath(List.of(from, corner.get(), to));
                if (polylineLength(path) <= maxLength) {
                    candidates.add(path);
                }
            }
        }

        double stub = Math.max(profile.stationSpacing() * 0.24D, Math.min(profile.stationSpacing() * 0.48D, directDistance * 0.24D));
        for (Vec2 start : startDirections.stream().limit(5).toList()) {
            for (Vec2 entry : endDirections.stream().limit(5).toList()) {
                Vec2 startStub = new Vec2(from.x() + start.x() * stub, from.y() + start.y() * stub);
                Vec2 endStub = new Vec2(to.x() - entry.x() * stub, to.y() - entry.y() * stub);
                for (List<Vec2> bridge : octilinearBridges(startStub, endStub, minLeg)) {
                    List<Vec2> path = new ArrayList<>();
                    path.add(from);
                    path.add(startStub);
                    path.addAll(bridge.subList(1, bridge.size()));
                    path.add(to);
                    path = dedupePath(path);
                    if (isOctilinearPath(path) && polylineLength(path) <= maxLength) {
                        candidates.add(path);
                    }
                }
            }
        }

        List<Vec2> fallback = dedupePath(List.of(from, to));
        candidates.add(fallback);
        return candidates.stream()
                .map(MetroMapSchematicSolver::dedupePath)
                .filter(path -> path.size() >= 2)
                .distinct()
                .sorted(Comparator.comparingDouble(MetroMapSchematicSolver::candidateSeedScore))
                .limit(180)
                .toList();
    }

    private static List<List<Vec2>> octilinearBridges(Vec2 from, Vec2 to, double minLeg) {
        List<List<Vec2>> bridges = new ArrayList<>();
        if (from.distanceTo(to) < 1.0D) {
            bridges.add(List.of(from, to));
            return bridges;
        }
        if (isOctilinearSegment(from, to)) {
            bridges.add(List.of(from, to));
        }
        for (Vec2 first : DIRECTIONS) {
            for (Vec2 second : DIRECTIONS) {
                Optional<Vec2> corner = rayIntersection(from, first, to, reverse(second));
                if (corner.isPresent() && corner.get().distanceTo(from) >= minLeg && corner.get().distanceTo(to) >= minLeg) {
                    bridges.add(dedupePath(List.of(from, corner.get(), to)));
                }
            }
        }
        Vec2 horizontal = new Vec2(to.x(), from.y());
        if (horizontal.distanceTo(from) >= minLeg && horizontal.distanceTo(to) >= minLeg) {
            bridges.add(dedupePath(List.of(from, horizontal, to)));
        }
        Vec2 vertical = new Vec2(from.x(), to.y());
        if (vertical.distanceTo(from) >= minLeg && vertical.distanceTo(to) >= minLeg) {
            bridges.add(dedupePath(List.of(from, vertical, to)));
        }
        return bridges.stream()
                .filter(MetroMapSchematicSolver::isOctilinearPath)
                .distinct()
                .sorted(Comparator.comparingDouble(MetroMapSchematicSolver::candidateSeedScore))
                .limit(10)
                .toList();
    }

    private static double candidateSeedScore(List<Vec2> path) {
        return polylineLength(path)
                + Math.max(0, path.size() - 2) * 32.0D
                + directionPenalty(path) * 240.0D
                + (isOctilinearPath(path) ? 0.0D : 10_000.0D);
    }

    private static double routeScore(MetroTopology topology, SchematicEdge edge, List<Vec2> path, Map<NodeId, Vec2> positions, List<List<Vec2>> routedPaths, CorridorHint corridorHint, LayoutProfile profile) {
        double direct = Math.max(1.0D, path.getFirst().distanceTo(path.getLast()));
        double score = polylineLength(path) / direct
                + Math.max(0, path.size() - 2) * 0.72D
                + directionPenalty(path) * 42.0D
                + endpointDirectionPenalty(path, corridorHint.direction()) * 3.8D
                + turnPenalty(path) * 0.34D;
        if (!isOctilinearPath(path)) {
            score += 1_000.0D;
        }
        for (Map.Entry<NodeId, Vec2> entry : positions.entrySet()) {
            if (entry.getKey().equals(edge.from()) || entry.getKey().equals(edge.to())) {
                continue;
            }
            double clearance = nodeObstacleRadius(topology.node(entry.getKey())) + 5.0D;
            double distance = distanceToPolyline(entry.getValue(), path);
            if (distance < clearance) {
                score += 10.0D + square((clearance - distance) / clearance) * 34.0D;
            }
        }
        for (List<Vec2> routed : routedPaths) {
            if (polylinesIntersect(path, routed)) {
                score += 11.0D;
            }
            double proximity = polylineProximityPenalty(path, routed, profile.routeSeparation());
            score += proximity * 5.4D;
        }
        if (edge.kind() == SemanticEdgeKind.FOLD_ADJACENT) {
            score += Math.max(0, path.size() - 2) * 0.46D;
        }
        return score;
    }

    private List<VisualLabel> layoutLabels(MetroTopology topology, Map<NodeId, Vec2> positions, List<VisualEdgePath> edges, LayoutProfile profile) {
        List<NodeId> ordered = positions.keySet().stream()
                .sorted(Comparator.comparingInt((NodeId id) -> topology.node(id).importance()).reversed().thenComparing(NodeId::compareTo))
                .toList();
        List<LabelBox> placed = new ArrayList<>();
        List<VisualLabel> labels = new ArrayList<>();
        for (NodeId nodeId : ordered) {
            SchematicNode node = topology.node(nodeId);
            Vec2 position = positions.get(nodeId);
            if (node.label().isBlank()) {
                continue;
            }
            LabelCandidate best = null;
            double bestScore = Double.POSITIVE_INFINITY;
            for (LabelCandidate candidate : labelCandidates(node, position)) {
                double score = candidate.distanceFromNode() * 0.035D;
                for (LabelBox box : placed) {
                    if (candidate.box().intersects(box)) {
                        score += box.priority() > node.importance() ? 520.0D : 120.0D;
                    }
                }
                for (VisualEdgePath edge : edges) {
                    if (edge.from().equals(nodeId) || edge.to().equals(nodeId)) {
                        continue;
                    }
                    if (candidate.box().intersects(edge.bounds())) {
                        score += 7.0D;
                    }
                }
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            if (best == null || bestScore >= 520.0D) {
                continue;
            }
            placed.add(new LabelBox(best.box().minX(), best.box().minY(), best.box().maxX(), best.box().maxY(), node.importance()));
            labels.add(new VisualLabel(nodeId, node.label(), best.x(), best.y(), node.importance(), labelScale(node), bestScore > 0.0D));
        }
        return labels;
    }

    private SchematicQualityReport quality(SchematicInputGraph input, MetroTopology topology, Map<NodeId, Vec2> positions, RouteOutput routes, List<VisualLabel> labels, ConstraintStats constraints, LayoutProfile profile, long startNanos) {
        int nodeOverlaps = 0;
        List<NodeId> ids = positions.keySet().stream().toList();
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                if (positions.get(ids.get(i)).distanceTo(positions.get(ids.get(j))) < minNodeDistance(topology.node(ids.get(i)), topology.node(ids.get(j)), profile, topology.connected(ids.get(i), ids.get(j))) * 0.72D) {
                    nodeOverlaps++;
                }
            }
        }
        int crossings = 0;
        for (int i = 0; i < routes.edgePaths().size(); i++) {
            VisualEdgePath first = routes.edgePaths().get(i);
            for (int j = i + 1; j < routes.edgePaths().size(); j++) {
                VisualEdgePath second = routes.edgePaths().get(j);
                if (sharesEndpoint(first, second)) {
                    continue;
                }
                if (polylinesIntersect(first.points(), second.points())) {
                    crossings++;
                }
            }
        }
        int labelOverlaps = countLabelOverlaps(labels);
        Aabb2 bounds = boundsForPositions(positions);
        double averageDisplacement = 0.0D;
        double maxDisplacement = 0.0D;
        for (Map.Entry<NodeId, Vec2> entry : positions.entrySet()) {
            SchematicNode node = topology.node(entry.getKey());
            double displacement = Math.hypot(entry.getValue().x() - node.worldX(), entry.getValue().y() - node.worldZ());
            averageDisplacement += displacement;
            maxDisplacement = Math.max(maxDisplacement, displacement);
        }
        averageDisplacement /= Math.max(1, positions.size());
        long millis = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
        return new SchematicQualityReport(
                millis,
                PROFILES.indexOf(profile) + 1,
                nodeOverlaps + constraints.nodeOverlaps(),
                crossings,
                labelOverlaps,
                averageDisplacement,
                maxDisplacement,
                routes.bendCount(),
                routes.fallbackEdges(),
                constraints.edgeNodeConflicts(),
                routes.loopGlyphs(),
                routes.stationInternalEdges(),
                false,
                false);
    }

    private double qualityScore(SchematicQualityReport quality, Aabb2 bounds, LayoutProfile profile) {
        double width = Math.max(1.0D, width(bounds));
        double height = Math.max(1.0D, height(bounds));
        double aspect = width / height;
        double aspectPenalty = Math.abs(Math.log(aspect / profile.targetAspect())) * 900.0D;
        double oversizePenalty = Math.max(0.0D, height - width * 0.92D) * 0.7D;
        return quality.nodeOverlapCount() * 25_000.0D
                + quality.edgeCrossingCount() * 11_000.0D
                + quality.unresolvedCorridorCount() * 4_000.0D
                + quality.labelOverlapCount() * 1_200.0D
                + quality.bendCount() * 18.0D
                + quality.fallbackEdgeCount() * 7_500.0D
                + aspectPenalty
                + oversizePenalty;
    }

    private VisualRouteMapGraph visualGraph(SchematicInputGraph input, LayoutAttempt attempt) {
        List<VisualNode> visualNodes = attempt.positions().entrySet().stream()
                .map(entry -> {
                    SchematicNode node = input.nodesById().get(entry.getKey());
                    return new VisualNode(
                            node.id(),
                            node.kind(),
                            entry.getValue().x(),
                            entry.getValue().y(),
                            node.worldX(),
                            node.worldZ(),
                            node.label(),
                            node.routeLineIds(),
                            node.importance(),
                            false);
                })
                .sorted(Comparator.comparing(VisualNode::id))
                .toList();
        Map<NodeId, VisualNode> nodesById = visualNodes.stream()
                .collect(Collectors.toMap(VisualNode::id, node -> node, (a, b) -> a, LinkedHashMap::new));
        Aabb2 bounds = bounds(visualNodes, attempt.routeOutput().edgePaths(), attempt.labels());
        return new VisualRouteMapGraph(
                input.levelKey(),
                visualNodes,
                nodesById,
                attempt.routeOutput().edgePaths(),
                attempt.routeOutput().edgePaths().stream().collect(Collectors.toMap(VisualEdgePath::edgeId, edge -> edge, (a, b) -> a, LinkedHashMap::new)),
                attempt.labels(),
                attempt.quality(),
                bounds,
                input.routeRevision(),
                input.pipeRevision(),
                FullRouteMapConfig.SCHEMATIC_SOLVER_VERSION);
    }

    private static VisualRouteMapGraph emptyGraph(SchematicInputGraph input) {
        return new VisualRouteMapGraph(
                input.levelKey(),
                List.of(),
                Map.of(),
                List.of(),
                Map.of(),
                List.of(),
                SchematicQualityReport.fallback(0L, 0),
                Aabb2.around(0.0D, 0.0D, 32.0D),
                input.routeRevision(),
                input.pipeRevision(),
                FullRouteMapConfig.SCHEMATIC_SOLVER_VERSION);
    }

    private Optional<NodeId> starCenter(MetroTopology topology, MetroComponent component) {
        return component.nodeIds().stream()
                .filter(id -> topology.degree(id) >= 4)
                .max(Comparator.comparingInt(topology::degree));
    }

    private List<List<NodeId>> starArms(MetroTopology topology, MetroComponent component, NodeId centerId) {
        Set<NodeId> componentNodes = new HashSet<>(component.nodeIds());
        List<List<NodeId>> arms = new ArrayList<>();
        for (NodeId neighbor : topology.neighbors(centerId).stream().sorted(NodeId::compareTo).toList()) {
            if (!componentNodes.contains(neighbor)) {
                continue;
            }
            List<NodeId> arm = new ArrayList<>();
            arm.add(centerId);
            NodeId previous = centerId;
            NodeId current = neighbor;
            while (current != null && componentNodes.contains(current) && !arm.contains(current)) {
                arm.add(current);
                NodeId previousForFilter = previous;
                List<NodeId> next = topology.neighbors(current).stream()
                        .filter(id -> !id.equals(previousForFilter))
                        .filter(componentNodes::contains)
                        .sorted(Comparator.comparingInt((NodeId id) -> topology.degree(id)).thenComparing(NodeId::compareTo))
                        .toList();
                if (topology.degree(current) != 2 || next.isEmpty()) {
                    break;
                }
                NodeId currentNode = next.getFirst();
                previous = current;
                current = currentNode;
            }
            arms.add(arm);
        }
        return arms;
    }

    private Vec2 primaryAxisFor(MetroComponent component, RouteRun run) {
        List<NodeId> sequence = componentSequence(run, component);
        if (sequence.size() < 2) {
            return new Vec2(1.0D, 0.0D);
        }
        SchematicNode first = component.nodesById().get(sequence.getFirst());
        SchematicNode last = component.nodesById().get(sequence.getLast());
        double dx = last.worldX() - first.worldX();
        double dy = last.worldZ() - first.worldZ();
        return nearestDirection(dx, dy);
    }

    private void preserveWorldOrientation(MetroComponent component, Map<NodeId, Vec2> positions) {
        if (positions.size() < 2) {
            return;
        }
        double visualMeanX = positions.values().stream().mapToDouble(Vec2::x).average().orElse(0.0D);
        double visualMeanY = positions.values().stream().mapToDouble(Vec2::y).average().orElse(0.0D);
        double worldMeanX = positions.keySet().stream().map(component.nodesById()::get).filter(node -> node != null).mapToDouble(SchematicNode::worldX).average().orElse(0.0D);
        double worldMeanZ = positions.keySet().stream().map(component.nodesById()::get).filter(node -> node != null).mapToDouble(SchematicNode::worldZ).average().orElse(0.0D);
        double covarianceX = 0.0D;
        double covarianceY = 0.0D;
        for (Map.Entry<NodeId, Vec2> entry : positions.entrySet()) {
            SchematicNode node = component.nodesById().get(entry.getKey());
            if (node == null) {
                continue;
            }
            covarianceX += (entry.getValue().x() - visualMeanX) * (node.worldX() - worldMeanX);
            covarianceY += (entry.getValue().y() - visualMeanY) * (node.worldZ() - worldMeanZ);
        }
        boolean flipX = covarianceX < -EPSILON;
        boolean flipY = covarianceY < -EPSILON;
        if (!flipX && !flipY) {
            return;
        }
        for (Map.Entry<NodeId, Vec2> entry : new ArrayList<>(positions.entrySet())) {
            Vec2 position = entry.getValue();
            double x = flipX ? visualMeanX - (position.x() - visualMeanX) : position.x();
            double y = flipY ? visualMeanY - (position.y() - visualMeanY) : position.y();
            positions.put(entry.getKey(), new Vec2(x, y));
        }
    }

    private static List<NodeId> componentSequence(RouteRun run, MetroComponent component) {
        Set<NodeId> componentNodes = new HashSet<>(component.nodeIds());
        List<NodeId> result = new ArrayList<>();
        for (NodeId nodeId : run.uniqueSequence()) {
            if (componentNodes.contains(nodeId)) {
                result.add(nodeId);
            }
        }
        return result;
    }

    private boolean isClosedWithinComponent(RouteRun run, MetroComponent component) {
        if (!run.closed()) {
            return false;
        }
        Set<NodeId> componentNodes = new HashSet<>(component.nodeIds());
        return run.uniqueSequence().stream().allMatch(componentNodes::contains);
    }

    private Optional<PlacementAnchor> nearestSequenceAnchor(List<NodeId> sequence, int index, Map<NodeId, Vec2> positions) {
        for (int offset = 1; offset < sequence.size(); offset++) {
            int before = index - offset;
            if (before >= 0 && positions.containsKey(sequence.get(before))) {
                return Optional.of(new PlacementAnchor(sequence.get(before), true));
            }
            int after = index + offset;
            if (after < sequence.size() && positions.containsKey(sequence.get(after))) {
                return Optional.of(new PlacementAnchor(sequence.get(after), false));
            }
        }
        return Optional.empty();
    }

    private Vec2 directionForPlacement(MetroTopology topology, NodeId fromId, NodeId toId, boolean forward) {
        SchematicNode from = topology.node(fromId);
        SchematicNode to = topology.node(toId);
        double dx = to.worldX() - from.worldX();
        double dy = to.worldZ() - from.worldZ();
        Vec2 direction = nearestDirection(dx, dy);
        if (!forward) {
            direction = new Vec2(-direction.x(), -direction.y());
        }
        return direction;
    }

    private static List<Vec2> orderedDirections(Vec2 preferred) {
        return DIRECTIONS.stream()
                .sorted(Comparator.comparingDouble((Vec2 direction) -> dot(direction, preferred)).reversed())
                .toList();
    }

    private static Vec2 pointOnRectanglePerimeter(double width, double height, double distance) {
        double halfW = width * 0.5D;
        double halfH = height * 0.5D;
        double top = width;
        double right = height;
        double bottom = width;
        double d = distance % (2.0D * (width + height));
        if (d <= top) {
            return new Vec2(-halfW + d, -halfH);
        }
        d -= top;
        if (d <= right) {
            return new Vec2(halfW, -halfH + d);
        }
        d -= right;
        if (d <= bottom) {
            return new Vec2(halfW - d, halfH);
        }
        d -= bottom;
        return new Vec2(-halfW, halfH - d);
    }

    private static Optional<Vec2> rayIntersection(Vec2 a, Vec2 first, Vec2 b, Vec2 second) {
        double det = first.x() * -second.y() - first.y() * -second.x();
        if (Math.abs(det) < EPSILON) {
            return Optional.empty();
        }
        double bx = b.x() - a.x();
        double by = b.y() - a.y();
        double t = (bx * -second.y() - by * -second.x()) / det;
        double u = (first.x() * by - first.y() * bx) / det;
        if (t < 0.0D || u < 0.0D) {
            return Optional.empty();
        }
        return Optional.of(new Vec2(a.x() + first.x() * t, a.y() + first.y() * t));
    }

    private static List<Vec2> loopPath(Vec2 from, Vec2 to, String id, LayoutProfile profile) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double length = Math.max(1.0D, Math.hypot(dx, dy));
        double nx = -dy / length;
        double ny = dx / length;
        if ((id.hashCode() & 1) == 0) {
            nx = -nx;
            ny = -ny;
        }
        double bend = Math.max(profile.stationSpacing() * 0.28D, Math.min(profile.stationSpacing() * 0.62D, length * 0.36D));
        return dedupePath(List.of(from, new Vec2((from.x() + to.x()) * 0.5D + nx * bend, (from.y() + to.y()) * 0.5D + ny * bend), to));
    }

    private static List<VisualLane> lanesFor(SchematicEdge edge) {
        if (edge.routeLineIds().isEmpty()) {
            return List.of(new VisualLane(Optional.empty(), 0, 0.0D));
        }
        double step = FullRouteMapConfig.LINE_WIDTH_PX / FullRouteMapConfig.BASE_SCALE + 3.0D;
        double center = (edge.routeLineIds().size() - 1) * 0.5D;
        List<VisualLane> lanes = new ArrayList<>();
        for (int i = 0; i < edge.routeLineIds().size(); i++) {
            lanes.add(new VisualLane(Optional.of(edge.routeLineIds().get(i)), i, (i - center) * step));
        }
        return lanes;
    }

    private static List<LabelCandidate> labelCandidates(SchematicNode node, Vec2 position) {
        double radius = nodeObstacleRadius(node);
        double width = Math.max(24.0D, Math.min(160.0D, node.label().length() * 4.8D + 8.0D));
        double height = 10.0D;
        double near = radius + 10.0D;
        double diagonal = near * 0.76D;
        List<LabelCandidate> candidates = new ArrayList<>();
        addLabelCandidate(candidates, position, position.x() + near, position.y() - height * 0.5D, width, height);
        addLabelCandidate(candidates, position, position.x() - near - width, position.y() - height * 0.5D, width, height);
        addLabelCandidate(candidates, position, position.x() - width * 0.5D, position.y() + near, width, height);
        addLabelCandidate(candidates, position, position.x() - width * 0.5D, position.y() - near - height, width, height);
        addLabelCandidate(candidates, position, position.x() + diagonal, position.y() + diagonal, width, height);
        addLabelCandidate(candidates, position, position.x() - diagonal - width, position.y() + diagonal, width, height);
        addLabelCandidate(candidates, position, position.x() + diagonal, position.y() - diagonal - height, width, height);
        addLabelCandidate(candidates, position, position.x() - diagonal - width, position.y() - diagonal - height, width, height);
        return candidates;
    }

    private static void addLabelCandidate(List<LabelCandidate> candidates, Vec2 node, double x, double y, double width, double height) {
        Vec2 center = new Vec2(x + width * 0.5D, y + height * 0.5D);
        candidates.add(new LabelCandidate(x, y, new LabelBox(x, y, x + width, y + height, 0), center.distanceTo(node)));
    }

    private static float labelScale(SchematicNode node) {
        return switch (node.kind()) {
            case CLUSTER -> 0.82F;
            case DEEP_CLUSTER -> 0.78F;
            case FOLD_ANCHOR -> 0.72F;
            case STATION -> node.importance() >= 700 ? 0.78F : 0.70F;
        };
    }

    private static double minNodeDistance(SchematicNode first, SchematicNode second, LayoutProfile profile, boolean connected) {
        if (connected) {
            return minConnectedDistance(first, second, profile);
        }
        return nodeObstacleRadius(first) + nodeObstacleRadius(second) + profile.nodeGap();
    }

    private static double minConnectedDistance(SchematicNode first, SchematicNode second, LayoutProfile profile) {
        if (first.kind() == NodeKind.FOLD_ANCHOR || second.kind() == NodeKind.FOLD_ANCHOR) {
            return profile.boundarySpacing() * 0.82D;
        }
        if (first.kind() == NodeKind.CLUSTER || second.kind() == NodeKind.CLUSTER || first.kind() == NodeKind.DEEP_CLUSTER || second.kind() == NodeKind.DEEP_CLUSTER) {
            return profile.stationSpacing() * 0.64D;
        }
        return profile.stationSpacing() * 0.70D;
    }

    private static double nodeObstacleRadius(SchematicNode node) {
        return switch (node.kind()) {
            case CLUSTER, DEEP_CLUSTER -> 23.0D;
            case FOLD_ANCHOR -> 18.0D;
            case STATION -> node.importance() >= 700 ? 23.0D : 16.0D;
        };
    }

    private static Aabb2 nodeBounds(SchematicNode node, Vec2 position) {
        return Aabb2.around(position.x(), position.y(), nodeObstacleRadius(node));
    }

    private static double hitRadiusBlocks(SchematicEdge edge) {
        int laneCount = Math.max(1, edge.routeLineIds().size());
        return (FullRouteMapConfig.LINE_WIDTH_PX * laneCount + 8.0D) / FullRouteMapConfig.BASE_SCALE;
    }

    private static Vec2 nearestDirection(double dx, double dy) {
        double length = Math.hypot(dx, dy);
        if (length < EPSILON) {
            return new Vec2(1.0D, 0.0D);
        }
        double ux = dx / length;
        double uy = dy / length;
        Vec2 best = DIRECTIONS.getFirst();
        double bestDot = Double.NEGATIVE_INFINITY;
        for (Vec2 direction : DIRECTIONS) {
            double dot = ux * direction.x() + uy * direction.y();
            if (dot > bestDot) {
                bestDot = dot;
                best = direction;
            }
        }
        return best;
    }

    private static Vec2 reverse(Vec2 direction) {
        return new Vec2(-direction.x(), -direction.y());
    }

    private static Vec2 hashDirection(NodeId id) {
        double angle = (id.hashCode() & 0xFFFF) / 65535.0D * Math.PI * 2.0D;
        return nearestDirection(Math.cos(angle), Math.sin(angle));
    }

    private static Vec2 hashDirection(NodeId first, NodeId second) {
        double angle = ((first.hashCode() * 31 + second.hashCode()) & 0xFFFF) / 65535.0D * Math.PI * 2.0D;
        return new Vec2(Math.cos(angle), Math.sin(angle));
    }

    private static Vec2 average(List<Vec2> points) {
        if (points.isEmpty()) {
            return new Vec2(0.0D, 0.0D);
        }
        return new Vec2(points.stream().mapToDouble(Vec2::x).average().orElse(0.0D), points.stream().mapToDouble(Vec2::y).average().orElse(0.0D));
    }

    private static Map<NodeId, Vec2> centerPositions(Map<NodeId, Vec2> positions) {
        Aabb2 bounds = boundsForPositions(positions);
        if (bounds.isEmpty()) {
            return positions;
        }
        Map<NodeId, Vec2> centered = new LinkedHashMap<>();
        for (Map.Entry<NodeId, Vec2> entry : positions.entrySet()) {
            centered.put(entry.getKey(), new Vec2(entry.getValue().x() - bounds.centerX(), entry.getValue().y() - bounds.centerY()));
        }
        return centered;
    }

    private static Aabb2 bounds(List<VisualNode> nodes, List<VisualEdgePath> edges, List<VisualLabel> labels) {
        Aabb2 bounds = Aabb2.empty();
        for (VisualNode node : nodes) {
            bounds = bounds.include(node.x(), node.z());
        }
        for (VisualEdgePath edge : edges) {
            bounds = bounds.include(edge.bounds());
        }
        for (VisualLabel label : labels) {
            bounds = bounds.include(label.x(), label.z());
        }
        return bounds.isEmpty() ? Aabb2.around(0.0D, 0.0D, 32.0D) : bounds.inflate(72.0D);
    }

    private static Aabb2 boundsForPositions(Map<NodeId, Vec2> positions) {
        Aabb2 bounds = Aabb2.empty();
        for (Vec2 position : positions.values()) {
            bounds = bounds.include(position.x(), position.y());
        }
        return bounds.isEmpty() ? Aabb2.around(0.0D, 0.0D, 1.0D) : bounds;
    }

    private static Aabb2 boundsForComponent(List<NodeId> nodeIds, Map<NodeId, Vec2> positions) {
        Aabb2 bounds = Aabb2.empty();
        for (NodeId nodeId : nodeIds) {
            Vec2 position = positions.get(nodeId);
            if (position != null) {
                bounds = bounds.include(position.x(), position.y());
            }
        }
        return bounds.isEmpty() ? Aabb2.around(0.0D, 0.0D, 1.0D) : bounds;
    }

    private static Aabb2 boundsForPoints(List<Vec2> points) {
        Aabb2 bounds = Aabb2.empty();
        for (Vec2 point : points) {
            bounds = bounds.include(point.x(), point.y());
        }
        return bounds;
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

    private static double polylineLength(List<Vec2> points) {
        double length = 0.0D;
        for (int i = 0; i + 1 < points.size(); i++) {
            length += points.get(i).distanceTo(points.get(i + 1));
        }
        return length;
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
        if (len2 < EPSILON) {
            return point.distanceTo(a);
        }
        double t = ((point.x() - a.x()) * dx + (point.y() - a.y()) * dy) / len2;
        double clamped = Math.max(0.0D, Math.min(1.0D, t));
        return point.distanceTo(new Vec2(a.x() + dx * clamped, a.y() + dy * clamped));
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

    private static double polylineProximityPenalty(List<Vec2> first, List<Vec2> second, double threshold) {
        double penalty = 0.0D;
        for (int i = 0; i + 1 < first.size(); i++) {
            for (int j = 0; j + 1 < second.size(); j++) {
                double distance = segmentToSegmentDistance(first.get(i), first.get(i + 1), second.get(j), second.get(j + 1));
                if (distance < threshold) {
                    penalty += (threshold - distance) / threshold;
                }
            }
        }
        return Math.min(5.0D, penalty);
    }

    private static double segmentToSegmentDistance(Vec2 a, Vec2 b, Vec2 c, Vec2 d) {
        if (segmentsIntersect(a, b, c, d)) {
            return 0.0D;
        }
        return Math.min(
                Math.min(distanceToSegment(a, c, d), distanceToSegment(b, c, d)),
                Math.min(distanceToSegment(c, a, b), distanceToSegment(d, a, b)));
    }

    private static double directionPenalty(List<Vec2> path) {
        double penalty = 0.0D;
        for (int i = 0; i + 1 < path.size(); i++) {
            Vec2 a = path.get(i);
            Vec2 b = path.get(i + 1);
            double dx = b.x() - a.x();
            double dy = b.y() - a.y();
            double length = Math.hypot(dx, dy);
            if (length < EPSILON) {
                continue;
            }
            Vec2 nearest = nearestDirection(dx, dy);
            double dot = Math.abs(dx / length * nearest.x() + dy / length * nearest.y());
            penalty += 1.0D - dot;
        }
        return penalty;
    }

    private static double endpointDirectionPenalty(List<Vec2> path, Vec2 preferred) {
        if (path.size() < 2) {
            return 0.0D;
        }
        Vec2 first = segmentDirection(path.get(0), path.get(1)).orElse(preferred);
        Vec2 last = segmentDirection(path.get(path.size() - 2), path.getLast()).orElse(preferred);
        return (1.0D - Math.max(0.0D, dot(first, preferred))) + (1.0D - Math.max(0.0D, dot(last, preferred)));
    }

    private static double turnPenalty(List<Vec2> path) {
        double penalty = 0.0D;
        for (int i = 1; i + 1 < path.size(); i++) {
            Optional<Vec2> before = segmentDirection(path.get(i - 1), path.get(i));
            Optional<Vec2> after = segmentDirection(path.get(i), path.get(i + 1));
            if (before.isEmpty() || after.isEmpty()) {
                continue;
            }
            double alignment = dot(before.get(), after.get());
            if (alignment > 0.999D) {
                continue;
            }
            penalty += alignment < -0.2D ? 2.0D : 1.0D - alignment * 0.35D;
        }
        return penalty;
    }

    private static Optional<Vec2> segmentDirection(Vec2 from, Vec2 to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double length = Math.hypot(dx, dy);
        if (length < EPSILON) {
            return Optional.empty();
        }
        return Optional.of(new Vec2(dx / length, dy / length));
    }

    private static boolean isOctilinearPath(List<Vec2> path) {
        for (int i = 0; i + 1 < path.size(); i++) {
            if (!isOctilinearSegment(path.get(i), path.get(i + 1))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOctilinearSegment(Vec2 from, Vec2 to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double length = Math.hypot(dx, dy);
        if (length < EPSILON) {
            return true;
        }
        Vec2 direction = nearestDirection(dx, dy);
        double dot = Math.abs(dx / length * direction.x() + dy / length * direction.y());
        return dot > 0.999D;
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

    private static boolean sharesEndpoint(VisualEdgePath first, VisualEdgePath second) {
        return first.from().equals(second.from()) || first.from().equals(second.to()) || first.to().equals(second.from()) || first.to().equals(second.to());
    }

    private static int countLabelOverlaps(List<VisualLabel> labels) {
        int count = 0;
        for (int i = 0; i < labels.size(); i++) {
            LabelBox first = labelBox(labels.get(i));
            for (int j = i + 1; j < labels.size(); j++) {
                if (first.intersects(labelBox(labels.get(j)))) {
                    count++;
                }
            }
        }
        return count;
    }

    private static LabelBox labelBox(VisualLabel label) {
        double width = Math.max(24.0D, label.text().length() * 4.8D + 8.0D);
        return new LabelBox(label.x(), label.z(), label.x() + width, label.z() + 10.0D, label.priority());
    }

    private static double dot(Vec2 first, Vec2 second) {
        return first.x() * second.x() + first.y() * second.y();
    }

    private static double square(double value) {
        return value * value;
    }

    private static Comparator<SchematicEdge> edgeOrder() {
        return Comparator
                .comparingInt((SchematicEdge edge) -> edge.occurrences().stream().mapToInt(MapEdgeOccurrence::layoutIndex).min().orElse(0))
                .thenComparing(edge -> edge.routeLineIds().stream().map(UUID::toString).findFirst().orElse(""))
                .thenComparing(SchematicEdge::id);
    }

    private record LayoutProfile(String name, double stationSpacing, double targetAspect, int longRunThreshold, double rowScale, boolean aggressiveFold) {
        double rowSpacing() {
            return this.stationSpacing * this.rowScale;
        }

        double boundarySpacing() {
            return this.stationSpacing * 0.42D;
        }

        double nodeGap() {
            return this.stationSpacing * 0.16D;
        }

        double routeSeparation() {
            return this.stationSpacing * 0.12D;
        }

        double componentGap() {
            return this.stationSpacing * 0.72D;
        }
    }

    private record LayoutAttempt(LayoutProfile profile, Map<NodeId, Vec2> positions, RouteOutput routeOutput, List<VisualLabel> labels, SchematicQualityReport quality, double score) {}

    private record ComponentLayout(MetroComponent component, Map<NodeId, Vec2> positions, Aabb2 bounds) {}

    private record RouteOutput(List<VisualEdgePath> edgePaths, int fallbackEdges, int bendCount, int loopGlyphs, int stationInternalEdges) {}

    private record RoutedPath(List<Vec2> points, boolean fallback, boolean loopGlyph) {}

    private record ConstraintStats(int nodeOverlaps, int edgeNodeConflicts) {}

    private record CorridorHint(Vec2 direction) {
        static CorridorHint fromEndpoints(Vec2 from, Vec2 to) {
            return new CorridorHint(nearestDirection(to.x() - from.x(), to.y() - from.y()));
        }
    }

    private static final class DirectionVotes {
        private final Vec2 fallback;
        private double x;
        private double y;

        private DirectionVotes(Vec2 fallback) {
            this.fallback = fallback;
            this.add(fallback, 1);
        }

        private void add(Vec2 direction, int weight) {
            Vec2 aligned = dot(direction, this.fallback) < 0.0D ? reverse(direction) : direction;
            this.x += aligned.x() * weight;
            this.y += aligned.y() * weight;
        }

        private CorridorHint hint() {
            if (Math.hypot(this.x, this.y) < EPSILON) {
                return new CorridorHint(this.fallback);
            }
            return new CorridorHint(nearestDirection(this.x, this.y));
        }
    }

    private record PlacementAnchor(NodeId nodeId, boolean forward) {}

    private record LabelCandidate(double x, double y, LabelBox box, double distanceFromNode) {}

    private record LabelBox(double minX, double minY, double maxX, double maxY, int priority) {
        boolean intersects(LabelBox other) {
            return this.minX < other.maxX && this.maxX > other.minX && this.minY < other.maxY && this.maxY > other.minY;
        }

        boolean intersects(Aabb2 bounds) {
            return !bounds.isEmpty() && this.minX < bounds.maxX() && this.maxX > bounds.minX() && this.minY < bounds.maxY() && this.maxY > bounds.minY();
        }
    }

    private record RouteKey(UUID routeLineId, UUID routeLayoutId) {}

    private record EdgeUse(SchematicEdge edge, int layoutIndex) {}

    private record RouteRun(RouteKey key, List<NodeId> sequence, List<SchematicEdge> edges, boolean closed, int score) {
        RouteRun {
            sequence = List.copyOf(sequence);
            edges = List.copyOf(edges);
        }

        List<NodeId> uniqueSequence() {
            LinkedHashSet<NodeId> unique = new LinkedHashSet<>(this.sequence);
            return List.copyOf(unique);
        }
    }

    private record MetroComponent(int index, List<NodeId> nodeIds, List<SchematicEdge> edges, List<RouteRun> runs, Map<NodeId, SchematicNode> nodesById, Aabb2 worldBounds) {
        MetroComponent {
            nodeIds = List.copyOf(nodeIds);
            edges = List.copyOf(edges);
            runs = List.copyOf(runs);
            nodesById = Map.copyOf(nodesById);
        }

        double worldWidth() {
            return width(this.worldBounds);
        }

        double worldHeight() {
            return height(this.worldBounds);
        }
    }

    private record MetroTopology(Map<NodeId, SchematicNode> nodesById, List<SchematicEdge> edges, List<RouteRun> runs, List<MetroComponent> components, Map<NodeId, List<NodeId>> adjacency, Set<String> connectedPairs, Map<NodeId, Integer> degree) {
        static MetroTopology build(SchematicInputGraph input) {
            Map<NodeId, SchematicNode> nodes = new LinkedHashMap<>(input.nodesById());
            List<SchematicEdge> usableEdges = input.edges().stream()
                    .filter(edge -> !edge.from().equals(edge.to()))
                    .toList();
            Map<NodeId, List<NodeId>> adjacency = new LinkedHashMap<>();
            for (NodeId id : nodes.keySet()) {
                adjacency.put(id, new ArrayList<>());
            }
            Set<String> connectedPairs = new HashSet<>();
            for (SchematicEdge edge : usableEdges) {
                if (edge.kind() == SemanticEdgeKind.STATION_INTERNAL) {
                    continue;
                }
                adjacency.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge.to());
                adjacency.computeIfAbsent(edge.to(), ignored -> new ArrayList<>()).add(edge.from());
                connectedPairs.add(pairKey(edge.from(), edge.to()));
            }
            adjacency.replaceAll((id, list) -> list.stream().distinct().sorted(NodeId::compareTo).toList());
            Map<NodeId, Integer> degree = adjacency.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().size(), (a, b) -> a, LinkedHashMap::new));
            List<RouteRun> runs = buildRouteRuns(usableEdges);
            List<MetroComponent> components = buildComponents(nodes, usableEdges, runs, adjacency);
            return new MetroTopology(nodes, usableEdges, runs, components, adjacency, connectedPairs, degree);
        }

        private static List<RouteRun> buildRouteRuns(List<SchematicEdge> edges) {
            Map<RouteKey, List<EdgeUse>> byRoute = new LinkedHashMap<>();
            for (SchematicEdge edge : edges) {
                for (MapEdgeOccurrence occurrence : edge.occurrences()) {
                    byRoute.computeIfAbsent(new RouteKey(occurrence.routeLineId(), occurrence.routeLayoutId()), ignored -> new ArrayList<>())
                            .add(new EdgeUse(edge, occurrence.layoutIndex()));
                }
            }
            List<RouteRun> runs = new ArrayList<>();
            for (Map.Entry<RouteKey, List<EdgeUse>> entry : byRoute.entrySet()) {
                List<EdgeUse> uses = entry.getValue().stream()
                        .sorted(Comparator.comparingInt(EdgeUse::layoutIndex).thenComparing(use -> use.edge().id()))
                        .toList();
                List<SchematicEdge> runEdges = uses.stream().map(EdgeUse::edge).distinct().toList();
                List<NodeId> sequence = stitchSequence(uses);
                boolean closed = sequence.size() > 2 && sequence.getFirst().equals(sequence.getLast());
                if (closed) {
                    sequence = sequence.subList(0, sequence.size() - 1);
                }
                int score = sequence.stream().distinct().toList().size() * 10 + runEdges.stream().mapToInt(edge -> edge.routeLineIds().size()).sum();
                runs.add(new RouteRun(entry.getKey(), sequence, runEdges, closed, score));
            }
            return runs.stream().sorted(Comparator.comparingInt(RouteRun::score).reversed()).toList();
        }

        private static List<NodeId> stitchSequence(List<EdgeUse> uses) {
            if (uses.isEmpty()) {
                return List.of();
            }
            SchematicEdge first = uses.getFirst().edge();
            List<NodeId> sequence = new ArrayList<>();
            if (uses.size() > 1) {
                SchematicEdge second = uses.get(1).edge();
                if (first.from().equals(second.from()) || first.from().equals(second.to())) {
                    sequence.add(first.to());
                    sequence.add(first.from());
                } else {
                    sequence.add(first.from());
                    sequence.add(first.to());
                }
            } else {
                sequence.add(first.from());
                sequence.add(first.to());
            }
            for (int i = 1; i < uses.size(); i++) {
                SchematicEdge edge = uses.get(i).edge();
                NodeId last = sequence.getLast();
                if (edge.from().equals(last)) {
                    sequence.add(edge.to());
                } else if (edge.to().equals(last)) {
                    sequence.add(edge.from());
                } else if (edge.from().equals(sequence.getFirst())) {
                    sequence.add(0, edge.to());
                } else if (edge.to().equals(sequence.getFirst())) {
                    sequence.add(0, edge.from());
                } else {
                    sequence.add(edge.from());
                    sequence.add(edge.to());
                }
            }
            return sequence;
        }

        private static List<MetroComponent> buildComponents(Map<NodeId, SchematicNode> nodes, List<SchematicEdge> edges, List<RouteRun> runs, Map<NodeId, List<NodeId>> adjacency) {
            List<MetroComponent> components = new ArrayList<>();
            Set<NodeId> visited = new HashSet<>();
            int index = 0;
            for (NodeId start : nodes.keySet().stream().sorted(NodeId::compareTo).toList()) {
                if (visited.contains(start)) {
                    continue;
                }
                List<NodeId> componentNodes = new ArrayList<>();
                ArrayDeque<NodeId> queue = new ArrayDeque<>();
                queue.add(start);
                visited.add(start);
                while (!queue.isEmpty()) {
                    NodeId current = queue.removeFirst();
                    componentNodes.add(current);
                    for (NodeId next : adjacency.getOrDefault(current, List.of())) {
                        if (visited.add(next)) {
                            queue.add(next);
                        }
                    }
                }
                Set<NodeId> nodeSet = new HashSet<>(componentNodes);
                List<SchematicEdge> componentEdges = edges.stream()
                        .filter(edge -> nodeSet.contains(edge.from()) && nodeSet.contains(edge.to()))
                        .toList();
                List<RouteRun> componentRuns = runs.stream()
                        .filter(run -> run.sequence().stream().anyMatch(nodeSet::contains))
                        .toList();
                Map<NodeId, SchematicNode> componentNodeMap = componentNodes.stream()
                        .collect(Collectors.toMap(id -> id, nodes::get, (a, b) -> a, LinkedHashMap::new));
                Aabb2 worldBounds = Aabb2.empty();
                for (NodeId id : componentNodes) {
                    SchematicNode node = nodes.get(id);
                    worldBounds = worldBounds.include(node.worldX(), node.worldZ());
                }
                components.add(new MetroComponent(index++, componentNodes.stream().sorted(NodeId::compareTo).toList(), componentEdges, componentRuns, componentNodeMap, worldBounds));
            }
            return components;
        }

        SchematicNode node(NodeId id) {
            return this.nodesById.get(id);
        }

        List<NodeId> neighbors(NodeId id) {
            return this.adjacency.getOrDefault(id, List.of());
        }

        int degree(NodeId id) {
            return this.degree.getOrDefault(id, 0);
        }

        boolean connected(NodeId first, NodeId second) {
            return this.connectedPairs.contains(pairKey(first, second));
        }

        private static String pairKey(NodeId first, NodeId second) {
            return first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first;
        }
    }
}
