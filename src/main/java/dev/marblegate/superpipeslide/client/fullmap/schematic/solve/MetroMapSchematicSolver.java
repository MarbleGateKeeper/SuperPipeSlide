package dev.marblegate.superpipeslide.client.fullmap.schematic.solve;

import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.model.MapEdgeOccurrence;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeKind;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.CoordinateSnapper;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.schematic.SchematicLayoutConfig;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.LabelWidthMeasurer;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SchematicEdge;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SchematicInputGraph;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SchematicNode;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SchematicQualityReport;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SemanticEdgeKind;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.LabelSlot;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualEdgePath;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualHitShape;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualLabel;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualLane;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualNode;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualRouteMapGraph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orientation-locked transit diagram solver for the pure SCHEMATIC map mode.
 *
 * <p>Station coordinates are embedded with an order-preserving transform: if a
 * station is west/east or north/south of another station in world space, the
 * schematic position keeps that ordering. The solver may stretch distances and
 * route edges through octilinear bends, but it must not reorder stations.</p>
 *
 * <p>Every solve tries the candidate layout profiles carried by
 * {@link SchematicLayoutConfig#metroProfiles()} in order and keeps the best-scoring
 * attempt; the loop stops early once an attempt is defect-free or the wall-clock
 * budget is spent.</p>
 *
 * <p>After the initial routing pass, three deterministic improvement stages refine the
 * layout: a rip-up-and-reroute pass that re-routes crossing edges with a tripled crossing
 * penalty for every profile, and -- for the winning profile only -- a time-boxed
 * simulated-annealing pass that nudges nodes while preserving the axis ordering above,
 * followed by an axis-compaction pass that shrinks embedding voids and rebuilds routing on
 * the compacted positions (rolling back on any defect regression). All stages share the
 * wall-clock deadline of {@code solve}.</p>
 */
public final class MetroMapSchematicSolver implements SchematicSolverBackend {
    private static final double EPSILON = 1.0E-6D;
    private static final double SQRT_HALF = Math.sqrt(0.5D);
    // Rip-up-and-reroute: full passes over the crossing-edge set. Strict per-edge crossing
    // improvement keeps every pass productive, so a small cap is enough in practice.
    private static final int MAX_REROUTE_ROUNDS = 4;
    // Bend-reduction sweeps after crossing elimination: non-crossing edges with 2+ bends are
    // re-routed and kept only when the replacement has strictly fewer bends, no crossings of
    // its own, and no more node conflicts than before. Two sweeps catch most straightenings
    // without threatening the routing budget.
    private static final int BEND_REROUTE_ROUNDS = 2;
    // Crossing penalty multiplier while re-routing a ripped-up edge, relative to the base
    // crossing term in routeScore: ripped edges pay triple for every remaining crossing.
    private static final double REROUTE_CROSSING_SCALE = 3.0D;
    // Annealing: temperature schedule, probe cap, and the minimum remaining wall-clock
    // budget (in nanoseconds) below which the stage is skipped entirely.
    private static final double ANNEAL_START_TEMPERATURE = 240.0D;
    private static final double ANNEAL_END_TEMPERATURE = 4.0D;
    private static final int ANNEAL_CALIBRATION_ROUNDS = 16;
    private static final int ANNEAL_MAX_PLANNED_ROUNDS = 2048;
    // Early convergence exit: this many consecutive probes without a new best snapshot ends the
    // stage before the wall-clock budget is spent, so already-good layouts do not burn it all.
    private static final int ANNEAL_CONVERGENCE_ROUNDS = 96;
    private static final long ANNEAL_MIN_BUDGET_NANOS = 25_000_000L;
    // Per-line label side consistency: score discounts for slots matching the station's own
    // line preference (see linePreferredSlots). Kept well below the label-label conflict
    // penalties (120/520) so they only break ties towards the preferred side.
    private static final double LABEL_LINE_SIDE_PRIMARY_BONUS = 8.0D;
    private static final double LABEL_LINE_SIDE_SECONDARY_BONUS = 4.0D;
    // Label placement improvement: deterministic local-search sweeps after the greedy pass,
    // time-boxed so per-profile label layout stays a small fraction of the solve budget.
    private static final long LABEL_IMPROVE_BUDGET_NANOS = 12_000_000L;
    private static final int LABEL_IMPROVE_MAX_SWEEPS = 6;
    // Line straightness: per-joint weight for route runs bending at intermediate stations,
    // applied to the joint turn amount in [0,1] (0 = straight through, 1 = full reversal).
    // Deliberately below the defect weights (4000+) but far above the per-bend weight (18),
    // so straightening lines never trades away defect fixes.
    private static final double LINE_TURN_WEIGHT = 60.0D;
    // Axis compaction: skipped when less than this wall-clock budget (nanoseconds) remains,
    // since the pass pays for a full re-route of the compacted positions.
    private static final long COMPACTION_MIN_BUDGET_NANOS = 80_000_000L;
    private static final List<Vec2> DIRECTIONS = List.of(
            new Vec2(1.0D, 0.0D),
            new Vec2(SQRT_HALF, SQRT_HALF),
            new Vec2(0.0D, 1.0D),
            new Vec2(-SQRT_HALF, SQRT_HALF),
            new Vec2(-1.0D, 0.0D),
            new Vec2(-SQRT_HALF, -SQRT_HALF),
            new Vec2(0.0D, -1.0D),
            new Vec2(SQRT_HALF, -SQRT_HALF));

    // Active label width measurer, set by layoutLabels at the start of every solve pass and read
    // by the label helpers (labelCandidates, countLabelOverlaps, labelBox) so their callers in the
    // quality pipeline stay untouched. Solver instances are confined to the single-threaded map
    // build executor in FullRouteMapCache, so this field never races.
    private LabelWidthMeasurer labelWidthMeasurer = LabelWidthMeasurer.latinEstimate();

    @Override
    public SchematicLayoutResult solve(SchematicInputGraph input, SchematicLayoutConfig config, Optional<VisualRouteMapGraphSnapshot> previous) {
        if (input.nodes().isEmpty()) {
            return new SchematicLayoutResult(emptyGraph(input));
        }

        long startNanos = System.nanoTime();
        long deadlineNanos = startNanos + Math.max(0L, config.solverTimeoutMillis()) * 1_000_000L;
        MetroTopology topology = MetroTopology.build(input);
        LayoutAttempt best = null;
        boolean timedOut = false;
        for (LayoutProfile profile : profilesFor(config)) {
            // Always let the first profile finish so a usable layout exists even when the budget
            // is already spent; afterwards the wall-clock budget gates every further profile.
            if (best != null && System.nanoTime() >= deadlineNanos) {
                timedOut = true;
                break;
            }
            LayoutAttempt attempt = this.solveWithProfile(input, topology, profile, previous, startNanos, deadlineNanos);
            if (best == null || attempt.score() < best.score()) {
                best = attempt;
            }
            // A defect-free attempt cannot be beaten in any of the weighted conflict counts, so
            // the remaining profiles would only chase bend/aspect nuances at full price.
            if (isDefectFree(attempt.quality())) {
                break;
            }
        }
        // Simulated annealing runs only on the winning attempt: it is a pure budget sink that
        // consumes whatever wall-clock time the profile loop left, so spending it per profile
        // would starve the exploration of the remaining profiles under the shared deadline.
        AnnealOutcome annealed = this.annealLayout(input, topology, best, deadlineNanos);
        int iterations = best.iterations() + annealed.rounds();
        // Axis compaction runs after annealing on the winning profile: shrinks the voids the
        // embedding's cursor spacing left, rebuilds routing on the compacted positions, and
        // rolls back when defect counts got worse (see compactAxes).
        CompactionOutcome compacted = this.compactAxes(topology, best.profile(), annealed.positions(), annealed.routeOutput(), deadlineNanos);
        iterations += compacted.rounds();
        Map<NodeId, Vec2> winnerPositions = compacted.positions();
        RouteOutput winnerRoutes = compacted.routeOutput();
        List<VisualLabel> winnerLabels = annealed.changed() || compacted.changed()
                ? this.layoutLabels(topology, winnerPositions, winnerRoutes.edgePaths(), best.profile(), input.labelWidthMeasurer())
                : best.labels();
        // Only the winning profile pays for the full (quadratic) quality measurement; every other
        // attempt was ranked on the cheap proxy counts gathered while routing.
        ConstraintStats winnerConstraints = this.measureGlobalConstraints(topology, winnerPositions, best.profile());
        boolean winnerTimedOut = timedOut || winnerRoutes.timedOut();
        SchematicQualityReport fullQuality = this.quality(input, topology, winnerPositions, winnerRoutes, winnerLabels, winnerConstraints, best.profile(), startNanos, winnerTimedOut, iterations, best.usedPrevious());
        best = new LayoutAttempt(best.profile(), winnerPositions, winnerRoutes, winnerLabels, fullQuality, best.score(), iterations, best.usedPrevious());
        return new SchematicLayoutResult(this.visualGraph(input, best));
    }

    private static boolean isDefectFree(SchematicQualityReport quality) {
        return quality.nodeOverlapCount() == 0
                && quality.edgeCrossingCount() == 0
                && quality.edgeNodeConflictCount() == 0
                && quality.fallbackEdgeCount() == 0
                && quality.labelOverlapCount() == 0;
    }

    /**
     * Maps the profiles configured in {@link SchematicLayoutConfig#metroProfiles()} onto the
     * solver-internal record. The config substitutes its defaults for a null or empty list, so
     * at least one profile always runs.
     */
    private static List<LayoutProfile> profilesFor(SchematicLayoutConfig config) {
        return config.metroProfiles().stream()
                .map(profile -> new LayoutProfile(profile.name(), profile.stationSpacing(), profile.targetAspect()))
                .toList();
    }

    private LayoutAttempt solveWithProfile(SchematicInputGraph input, MetroTopology topology, LayoutProfile profile, Optional<VisualRouteMapGraphSnapshot> previous, long startNanos, long deadlineNanos) {
        Map<NodeId, Vec2> positions = this.layoutOrientationLocked(topology, profile);
        // Stabilise the fresh embedding against the previously cached layout: a rigid
        // translation-plus-uniform-scale fit keeps every axis ordering intact while stations
        // that existed before stay visually put across incremental edits.
        boolean usedPrevious = previous.map(snapshot -> alignToPrevious(positions, snapshot)).orElse(false);
        // Snap near-equal axis coordinates after the last positional mutation of the
        // embedding (duplicate separation, alignment fit): kills sub-spacing drift so
        // stations meant to line up share an exact x/y before routing glues edge
        // endpoints to these coordinates, keeping straight segments exactly straight.
        // The 0.2-spacing tolerance is deliberately generous: axis ties are free under the
        // order-preservation promise, and every tie is one more exactly horizontal or
        // vertical line instead of a slightly tilted, jagged one.
        // replaceAll keeps the map identity: positions is captured by the lambda above.
        Map<NodeId, Vec2> snappedPositions = CoordinateSnapper.mergeNearEqualAxes(positions, profile.stationSpacing() * 0.20D);
        positions.replaceAll((id, position) -> snappedPositions.get(id));
        // Pull coordinates onto the half-spacing grid: the beat-quantized embedding already
        // lands near grid multiples, and exact grid coordinates make zero-bend octilinear
        // routes far more likely during routing.
        Map<NodeId, Vec2> griddedPositions = CoordinateSnapper.snapToGrid(positions, profile.stationSpacing() * 0.5D, profile.stationSpacing() * 0.18D);
        positions.replaceAll((id, position) -> griddedPositions.get(id));
        Map<String, CorridorHint> corridorHints = this.corridorHints(topology, positions);
        CorridorPlan corridors = this.buildCorridorPlan(topology, positions, profile);
        ConstraintStats constraints = this.measureGlobalConstraints(topology, positions, profile);
        RouteOutput routeOutput = this.routeEdges(topology, positions, profile, corridorHints, corridors, deadlineNanos);
        RerouteResult rerouted = this.ripUpAndReroute(topology, positions, profile, routeOutput, corridorHints, corridors, deadlineNanos);
        List<VisualLabel> labels = this.layoutLabels(topology, positions, rerouted.output().edgePaths(), profile, input.labelWidthMeasurer());
        SchematicQualityReport quality = this.proxyQuality(topology, positions, rerouted.output(), labels, constraints, profile, startNanos, rerouted.rounds(), usedPrevious);
        double score = this.qualityScore(quality, boundsForPositions(positions), profile);
        return new LayoutAttempt(profile, positions, rerouted.output(), labels, quality, score, rerouted.rounds(), usedPrevious);
    }

    /**
     * Cheap quality proxy used to rank profiles. It reuses the grid-accelerated constraint counts
     * and the crossing count accumulated while routing, skipping the quadratic node-overlap and
     * crossing recounts of {@link #quality}; only the winning profile is re-measured exactly.
     */
    private SchematicQualityReport proxyQuality(MetroTopology topology, Map<NodeId, Vec2> positions, RouteOutput routes, List<VisualLabel> labels, ConstraintStats constraints, LayoutProfile profile, long startNanos, int iterations, boolean usedPrevious) {
        int labelOverlaps = countLabelOverlaps(labels);
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
                // Rip-up-and-reroute rounds of this attempt; the winner additionally adds its
                // annealing rounds when the full quality report is built.
                iterations,
                profile.name(),
                constraints.nodeOverlaps(),
                routes.crossingCount(),
                labelOverlaps,
                averageDisplacement,
                maxDisplacement,
                routes.bendCount(),
                // Line turns are only measured in the winner's full quality report.
                0,
                routes.fallbackEdges(),
                routes.corridorViolations(),
                constraints.edgeNodeConflicts(),
                routes.loopGlyphs(),
                routes.stationInternalEdges(),
                routes.timedOut(),
                usedPrevious);
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

        // Remaining nodes (clusters, deep clusters, portals without a placed station anchor) must
        // never fall back to raw world coordinates: those sit thousands of blocks outside the
        // schematic frame. Anchor them to their already placed neighbours instead, reusing the
        // portal slot search so they land on a collision-free octilinear ring slot; fully
        // isolated nodes anchor to the centroid of everything placed so far.
        for (NodeId nodeId : topology.nodesById().keySet().stream().sorted(NodeId::compareTo).toList()) {
            if (positions.containsKey(nodeId)) {
                continue;
            }
            SchematicNode node = topology.node(nodeId);
            List<NodeId> placedNeighbors = topology.neighbors(nodeId).stream()
                    .filter(positions::containsKey)
                    .toList();
            Vec2 origin;
            Vec2 preferred;
            if (placedNeighbors.isEmpty()) {
                origin = positions.isEmpty() ? new Vec2(0.0D, 0.0D) : average(List.copyOf(positions.values()));
                preferred = hashDirection(nodeId);
            } else {
                origin = average(placedNeighbors.stream().map(positions::get).toList());
                double meanWorldX = placedNeighbors.stream().mapToDouble(id -> topology.node(id).worldX()).average().orElse(node.worldX());
                double meanWorldZ = placedNeighbors.stream().mapToDouble(id -> topology.node(id).worldZ()).average().orElse(node.worldZ());
                preferred = nearestDirection(node.worldX() - meanWorldX, node.worldZ() - meanWorldZ);
            }
            positions.put(nodeId, this.bestPortalSlot(topology, nodeId, origin, preferred, positions, profile));
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
        // Quantize to whole spacing beats (1x or 2x) so consecutive stations read as evenly
        // spaced stops on a rhythmic grid. Exact axis coincidences also become far more
        // likely, which raises the zero-bend octilinear hit rate during routing.
        long beats = Math.round(geographicHint / spacing);
        return spacing * Math.max(1L, Math.min(2L, beats));
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

    private ConstraintStats measureGlobalConstraints(MetroTopology topology, Map<NodeId, Vec2> positions, LayoutProfile profile) {
        int nodeOverlaps = 0;
        int edgeNodeConflicts = 0;
        List<NodeId> ids = positions.keySet().stream().sorted(NodeId::compareTo).toList();
        double cellSize = profile.stationSpacing();
        Map<GridCell, List<Integer>> nodeGrid = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            Vec2 position = positions.get(ids.get(i));
            nodeGrid.computeIfAbsent(GridCell.of(position.x(), position.y(), cellSize), ignored -> new ArrayList<>()).add(i);
        }
        // Furthest distance that can still trigger an overlap: the largest minimum node distance
        // in use (connected stations at 0.70 * stationSpacing, or two maximum-radius obstacles
        // plus the node gap). Cells are stationSpacing-sized, so the neighbourhood stays at one
        // cell for every profile; the range is computed to keep the scan exact regardless.
        double maxNodeReach = Math.max(profile.stationSpacing() * 0.70D, 2.0D * 23.0D + profile.nodeGap());
        int nodeRange = (int) Math.ceil(maxNodeReach / cellSize);
        for (int i = 0; i < ids.size(); i++) {
            NodeId first = ids.get(i);
            GridCell cell = GridCell.of(positions.get(first).x(), positions.get(first).y(), cellSize);
            for (int gx = -nodeRange; gx <= nodeRange; gx++) {
                for (int gz = -nodeRange; gz <= nodeRange; gz++) {
                    for (int j : nodeGrid.getOrDefault(new GridCell(cell.x() + gx, cell.z() + gz), List.of())) {
                        if (j <= i) {
                            continue;
                        }
                        NodeId second = ids.get(j);
                        double min = minNodeDistance(topology.node(first), topology.node(second), profile, topology.connected(first, second));
                        if (positions.get(first).distanceTo(positions.get(second)) < min) {
                            nodeOverlaps++;
                        }
                    }
                }
            }
        }
        // A node can only conflict with a segment when it lies within its clearance of the
        // segment AABB, so bucket lookups over the inflated AABB reproduce the full scan exactly.
        double maxEdgeClearance = 23.0D + 4.0D;
        for (SchematicEdge edge : topology.edges()) {
            Vec2 a = positions.get(edge.from());
            Vec2 b = positions.get(edge.to());
            if (a == null || b == null || edge.kind() == SemanticEdgeKind.STATION_INTERNAL) {
                continue;
            }
            int minCellX = (int) Math.floor((Math.min(a.x(), b.x()) - maxEdgeClearance) / cellSize);
            int maxCellX = (int) Math.floor((Math.max(a.x(), b.x()) + maxEdgeClearance) / cellSize);
            int minCellZ = (int) Math.floor((Math.min(a.y(), b.y()) - maxEdgeClearance) / cellSize);
            int maxCellZ = (int) Math.floor((Math.max(a.y(), b.y()) + maxEdgeClearance) / cellSize);
            for (int cx = minCellX; cx <= maxCellX; cx++) {
                for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                    for (int index : nodeGrid.getOrDefault(new GridCell(cx, cz), List.of())) {
                        NodeId nodeId = ids.get(index);
                        if (nodeId.equals(edge.from()) || nodeId.equals(edge.to())) {
                            continue;
                        }
                        double clearance = nodeObstacleRadius(topology.node(nodeId)) + 4.0D;
                        if (distanceToSegment(positions.get(nodeId), a, b) < clearance) {
                            edgeNodeConflicts++;
                        }
                    }
                }
            }
        }
        return new ConstraintStats(nodeOverlaps, edgeNodeConflicts);
    }

    private RouteOutput routeEdges(MetroTopology topology, Map<NodeId, Vec2> positions, LayoutProfile profile, Map<String, CorridorHint> corridorHints, CorridorPlan corridors, long deadlineNanos) {
        RoutingContext routing = new RoutingContext(topology, positions, profile);
        List<EdgeRouteState> states = new ArrayList<>();
        int stationInternalEdges = 0;
        int unroutedEdges = 0;
        int crossingCount = 0;
        boolean budgetExhausted = false;
        int processed = 0;
        for (SchematicEdge edge : topology.edges().stream().sorted(edgeOrder()).toList()) {
            if (edge.kind() == SemanticEdgeKind.STATION_INTERNAL || edge.from().equals(edge.to())) {
                stationInternalEdges++;
                continue;
            }
            Vec2 from = positions.get(edge.from());
            Vec2 to = positions.get(edge.to());
            if (from == null || to == null) {
                // Unpositioned endpoints cannot be routed; counted as fallback without a path.
                unroutedEdges++;
                continue;
            }
            // Consult the clock only every few edges to keep the check itself cheap. Once the
            // budget is spent, every remaining edge takes the direct degraded path below.
            if (!budgetExhausted && (processed & 7) == 7 && System.nanoTime() >= deadlineNanos) {
                budgetExhausted = true;
            }
            processed++;
            RoutedPath routed = budgetExhausted
                    ? degradedPath(edge, from, to, profile)
                    : this.routeEdge(routing, edge, from, to, corridorHints.getOrDefault(edge.id(), CorridorHint.fromEndpoints(from, to)), corridors, 1.0D);
            if (!budgetExhausted) {
                crossingCount += routing.countCrossings(routed.points(), edge);
                routing.insert(routed.points(), edge);
            }
            states.add(new EdgeRouteState(edge, routed.points(), routed.fallback(), routed.loopGlyph()));
        }
        return this.buildRouteOutput(states, corridors, crossingCount, stationInternalEdges, unroutedEdges, budgetExhausted);
    }

    /**
     * Assembles the immutable routing result from the per-edge states: visual paths, bend and
     * fallback accounting, and the unresolved-corridor count for grouped edges that ended up
     * closer than half their assigned lane step.
     */
    private RouteOutput buildRouteOutput(List<EdgeRouteState> states, CorridorPlan corridors, int crossingCount, int stationInternalEdges, int unroutedEdges, boolean timedOut) {
        List<VisualEdgePath> paths = new ArrayList<>();
        int fallbackEdges = unroutedEdges;
        int loopGlyphs = 0;
        for (EdgeRouteState state : states) {
            SchematicEdge edge = state.edge();
            fallbackEdges += state.fallback() ? 1 : 0;
            loopGlyphs += state.loopGlyph() ? 1 : 0;
            Aabb2 bounds = boundsForPoints(state.points()).inflate(hitRadiusBlocks(edge));
            paths.add(new VisualEdgePath(
                    edge.id(),
                    edge.from(),
                    edge.to(),
                    edge.kind(),
                    edge.routeLineIds(),
                    edge.occurrences(),
                    state.points(),
                    lanesFor(edge),
                    new VisualHitShape(state.points(), hitRadiusBlocks(edge), bounds),
                    bounds,
                    state.fallback()));
        }
        int bendCount = paths.stream().mapToInt(path -> Math.max(0, path.points().size() - 2)).sum();
        return new RouteOutput(paths, fallbackEdges, bendCount, loopGlyphs, stationInternalEdges, crossingCount, timedOut, countCorridorViolations(states, corridors), unroutedEdges);
    }

    /**
     * Rip-up-and-reroute crossing elimination, then bend reduction. Every crossing-elimination
     * pass rips each crossing edge out of the routing index (tombstone), re-routes it with the
     * crossing penalty tripled, and keeps the new path only when it crosses strictly fewer
     * paths than the ripped one, so the total crossing count falls monotonically and at most
     * {@link #MAX_REROUTE_ROUNDS} passes run. The bend-reduction sweeps afterwards re-route
     * non-crossing edges that still detour through two or more bends, keeping replacements
     * only when they straighten the edge without new crossings or node conflicts (corridor
     * members are skipped so lane separation stays intact). Passes share the solve-wide
     * wall-clock deadline; a pass cut short by the clock keeps the improvements it already
     * banked and flags the output as timed out.
     */
    private RerouteResult ripUpAndReroute(MetroTopology topology, Map<NodeId, Vec2> positions, LayoutProfile profile, RouteOutput initial, Map<String, CorridorHint> corridorHints, CorridorPlan corridors, long deadlineNanos) {
        Map<String, VisualEdgePath> pathByEdgeId = new HashMap<>();
        for (VisualEdgePath path : initial.edgePaths()) {
            pathByEdgeId.put(path.edgeId(), path);
        }
        // Rebuild the routing index over the current paths in the same deterministic edge order
        // the initial routing used; state i starts out owning routing entry i.
        List<EdgeRouteState> states = new ArrayList<>();
        for (SchematicEdge edge : topology.edges().stream().sorted(edgeOrder()).toList()) {
            VisualEdgePath path = pathByEdgeId.get(edge.id());
            if (path == null) {
                continue;
            }
            states.add(new EdgeRouteState(edge, path.points(), path.fallback(), edge.kind() == SemanticEdgeKind.LOOP_BACK));
        }
        int[] routingIndexOf = new int[states.size()];
        RoutingContext routing = rebuildRoutingContext(topology, positions, profile, states, routingIndexOf);
        int totalCrossings = initial.crossingCount();
        int rounds = 0;
        boolean interrupted = false;
        for (int round = 0; round < MAX_REROUTE_ROUNDS && totalCrossings > 0 && !interrupted; round++) {
            if (System.nanoTime() >= deadlineNanos) {
                break;
            }
            int[] crossingCounts = new int[states.size()];
            List<Integer> crossingStates = new ArrayList<>();
            for (int i = 0; i < states.size(); i++) {
                crossingCounts[i] = routing.countCrossings(states.get(i).points(), states.get(i).edge());
                if (crossingCounts[i] > 0) {
                    crossingStates.add(i);
                }
            }
            if (crossingStates.isEmpty()) {
                break;
            }
            // Most-crossed edges first; ties keep the deterministic state order.
            crossingStates.sort(Comparator.comparingInt((Integer index) -> crossingCounts[index]).reversed().thenComparingInt(index -> index));
            boolean improved = false;
            int processed = 0;
            for (int stateIndex : crossingStates) {
                if (((processed++ & 3) == 3) && System.nanoTime() >= deadlineNanos) {
                    interrupted = true;
                    break;
                }
                EdgeRouteState state = states.get(stateIndex);
                routing.setLive(routingIndexOf[stateIndex], false);
                int oldCrossings = routing.countCrossings(state.points(), state.edge());
                Vec2 from = positions.get(state.edge().from());
                Vec2 to = positions.get(state.edge().to());
                CorridorHint hint = corridorHints.getOrDefault(state.edge().id(), CorridorHint.fromEndpoints(from, to));
                RoutedPath rerouted = this.routeEdge(routing, state.edge(), from, to, hint, corridors, REROUTE_CROSSING_SCALE);
                int newCrossings = routing.countCrossings(rerouted.points(), state.edge());
                if (newCrossings < oldCrossings) {
                    routingIndexOf[stateIndex] = routing.insert(rerouted.points(), state.edge());
                    states.set(stateIndex, new EdgeRouteState(state.edge(), rerouted.points(), rerouted.fallback(), rerouted.loopGlyph()));
                    totalCrossings += newCrossings - oldCrossings;
                    improved = true;
                } else {
                    routing.setLive(routingIndexOf[stateIndex], true);
                }
            }
            rounds++;
            if (!improved) {
                break;
            }
        }
        // Bend-reduction sweeps: edges that no longer cross anything but still detour through
        // two or more bends are re-routed once more; a replacement is kept only when it has
        // strictly fewer bends, crosses nothing, and creates no new node conflicts. Corridor
        // members are skipped so their lane separation stays intact.
        for (int round = 0; round < BEND_REROUTE_ROUNDS && !interrupted; round++) {
            if (System.nanoTime() >= deadlineNanos) {
                break;
            }
            boolean improved = false;
            int processed = 0;
            for (int i = 0; i < states.size(); i++) {
                if (((processed++ & 3) == 3) && System.nanoTime() >= deadlineNanos) {
                    interrupted = true;
                    break;
                }
                EdgeRouteState state = states.get(i);
                int bends = Math.max(0, state.points().size() - 2);
                if (bends < 2 || corridors.groupByEdgeId().containsKey(state.edge().id()) || routing.countCrossings(state.points(), state.edge()) > 0) {
                    continue;
                }
                int oldConflicts = pathNodeConflicts(routing, state.edge(), state.points());
                routing.setLive(routingIndexOf[i], false);
                Vec2 from = positions.get(state.edge().from());
                Vec2 to = positions.get(state.edge().to());
                CorridorHint hint = corridorHints.getOrDefault(state.edge().id(), CorridorHint.fromEndpoints(from, to));
                RoutedPath rerouted = this.routeEdge(routing, state.edge(), from, to, hint, corridors, 1.0D);
                int newBends = Math.max(0, rerouted.points().size() - 2);
                if (newBends < bends && routing.countCrossings(rerouted.points(), state.edge()) == 0 && pathNodeConflicts(routing, state.edge(), rerouted.points()) <= oldConflicts) {
                    routingIndexOf[i] = routing.insert(rerouted.points(), state.edge());
                    states.set(i, new EdgeRouteState(state.edge(), rerouted.points(), rerouted.fallback(), rerouted.loopGlyph()));
                    improved = true;
                } else {
                    routing.setLive(routingIndexOf[i], true);
                }
            }
            rounds++;
            if (!improved) {
                break;
            }
        }
        RouteOutput output = this.buildRouteOutput(states, corridors, totalCrossings, initial.stationInternalEdges(), initial.unroutedEdges(), interrupted || initial.timedOut());
        return new RerouteResult(output, rounds);
    }

    /**
     * Time-boxed simulated annealing over node positions, executed once for the winning profile
     * with whatever wall-clock budget the profile loop left over.
     *
     * <p>Each probe moves one uniformly picked node by {@code stationSpacing / 4} along one of
     * the eight octilinear directions and re-routes only its incident edges (reusing the same
     * {@link #routeEdge} mechanism as rip-up-and-reroute). The energy mirrors {@link #qualityScore}'s
     * weighted conflict counts -- node overlaps, crossings, edge-node conflicts, fallback edges,
     * bends -- plus a small total-path-length term: the discrete counts alone are almost flat
     * between conflict changes, and Metropolis needs the smooth length gradient to drift towards
     * shorter routes. A line-turn term ({@link #LINE_TURN_WEIGHT}) rewards route runs that pass
     * straight through their intermediate stations, the defining look of a transit diagram.
     * Label overlaps are excluded because labels are re-laid once after the stage;
     * aspect is excluded because spacing/4 nudges cannot change it meaningfully. Computing the
     * exact energy per probe would need the quadratic global recounts, so the probe evaluates the
     * exact delta of the affected terms only, which is equivalent by construction.</p>
     *
     * <p>Hard constraint: a station move is rejected outright when it would flip the relative
     * axis order of any two stations with distinct world coordinates, upholding the class-level
     * order-preservation promise. Acceptance follows the Metropolis rule under a geometric
     * temperature schedule calibrated after {@link #ANNEAL_CALIBRATION_ROUNDS} probes so the
     * planned round count fits the remaining budget; the deadline is checked every round. The
     * random stream is seeded from the input graph content, so identical inputs replay identical
     * probe sequences. The best-scoring snapshot is kept, and when the stage ends without
     * improving the initial energy the pre-annealing state is restored. The stage runs even on
     * defect-free attempts: the aesthetic terms (bends, length, line turns) are otherwise never
     * optimized at all.</p>
     */
    private AnnealOutcome annealLayout(SchematicInputGraph input, MetroTopology topology, LayoutAttempt attempt, long deadlineNanos) {
        if (System.nanoTime() >= deadlineNanos - ANNEAL_MIN_BUDGET_NANOS
                || attempt.positions().size() < 2
                || attempt.routeOutput().timedOut()) {
            return new AnnealOutcome(attempt.positions(), attempt.routeOutput(), 0, false);
        }
        LayoutProfile profile = attempt.profile();
        Map<NodeId, Vec2> positions = new LinkedHashMap<>(attempt.positions());
        Map<String, CorridorHint> corridorHints = this.corridorHints(topology, positions);
        CorridorPlan corridors = this.buildCorridorPlan(topology, positions, profile);

        List<EdgeRouteState> states = new ArrayList<>();
        Map<String, VisualEdgePath> pathByEdgeId = new HashMap<>();
        for (VisualEdgePath path : attempt.routeOutput().edgePaths()) {
            pathByEdgeId.put(path.edgeId(), path);
        }
        for (SchematicEdge edge : topology.edges().stream().sorted(edgeOrder()).toList()) {
            VisualEdgePath path = pathByEdgeId.get(edge.id());
            if (path == null) {
                continue;
            }
            states.add(new EdgeRouteState(edge, path.points(), path.fallback(), edge.kind() == SemanticEdgeKind.LOOP_BACK));
        }
        int[] routingIndexOf = new int[states.size()];
        RoutingContext routing = rebuildRoutingContext(topology, positions, profile, states, routingIndexOf);
        Map<NodeId, List<Integer>> incidentByNode = new HashMap<>();
        for (int i = 0; i < states.size(); i++) {
            incidentByNode.computeIfAbsent(states.get(i).edge().from(), ignored -> new ArrayList<>()).add(i);
            incidentByNode.computeIfAbsent(states.get(i).edge().to(), ignored -> new ArrayList<>()).add(i);
        }
        // Line-straightness joints: route-run interior stations with their two incident edges.
        // Only joints whose edges both have a live route state participate in the energy.
        Map<String, Integer> stateIndexByEdgeId = new HashMap<>();
        for (int i = 0; i < states.size(); i++) {
            stateIndexByEdgeId.put(states.get(i).edge().id(), i);
        }
        Map<NodeId, List<LineJoint>> jointsByNode = new HashMap<>();
        for (LineJoint joint : lineJoints(topology)) {
            if (stateIndexByEdgeId.containsKey(joint.incoming().id()) && stateIndexByEdgeId.containsKey(joint.outgoing().id())) {
                jointsByNode.computeIfAbsent(joint.node(), ignored -> new ArrayList<>()).add(joint);
            }
        }

        ConstraintStats initialConstraints = this.measureGlobalConstraints(topology, positions, profile);
        int initialBends = 0;
        int initialFallbacks = 0;
        double initialLength = 0.0D;
        for (EdgeRouteState state : states) {
            initialBends += Math.max(0, state.points().size() - 2);
            initialFallbacks += state.fallback() ? 1 : 0;
            initialLength += polylineLength(state.points());
        }
        double initialLineTurns = 0.0D;
        for (List<LineJoint> joints : jointsByNode.values()) {
            for (LineJoint joint : joints) {
                initialLineTurns += jointTurnAmount(joint, edgeId -> {
                    Integer index = stateIndexByEdgeId.get(edgeId);
                    return index == null ? null : states.get(index).points();
                });
            }
        }
        double energy = annealEnergy(initialConstraints.nodeOverlaps(), attempt.routeOutput().crossingCount(), initialConstraints.edgeNodeConflicts(), initialFallbacks, initialBends, initialLength, initialLineTurns);
        double initialEnergy = energy;

        List<NodeId> movable = positions.keySet().stream().sorted(NodeId::compareTo).toList();
        Random random = new Random(annealSeed(input, profile));
        double step = profile.stationSpacing() * 0.25D;
        double bestEnergy = energy;
        Map<NodeId, Vec2> bestPositions = new LinkedHashMap<>(positions);
        List<EdgeRouteState> bestStates = new ArrayList<>(states);
        int plannedRounds = ANNEAL_CALIBRATION_ROUNDS + 64;
        long annealStart = System.nanoTime();
        int rounds = 0;
        int roundsSinceBest = 0;
        for (int k = 0; k < plannedRounds; k++) {
            long now = System.nanoTime();
            if (now >= deadlineNanos || roundsSinceBest >= ANNEAL_CONVERGENCE_ROUNDS) {
                break;
            }
            if (k == ANNEAL_CALIBRATION_ROUNDS) {
                // Calibrate the schedule to the measured probe cost so the temperature reaches its
                // floor right as the budget runs out, regardless of map size or machine speed.
                double averageNanos = (now - annealStart) / (double) ANNEAL_CALIBRATION_ROUNDS;
                long remaining = deadlineNanos - now;
                plannedRounds = ANNEAL_CALIBRATION_ROUNDS
                        + (int) Math.min(ANNEAL_MAX_PLANNED_ROUNDS - ANNEAL_CALIBRATION_ROUNDS, Math.max(8.0D, remaining / Math.max(1.0D, averageNanos)));
            }
            double temperature = ANNEAL_START_TEMPERATURE * Math.pow(ANNEAL_END_TEMPERATURE / ANNEAL_START_TEMPERATURE, k / (double) plannedRounds);
            rounds++;

            // Rejected probes leave their provisionally appended entries tombstoned in the index;
            // rebuild once the bloat would start to dominate every query replay.
            if (routing.pathEntryCount() > states.size() * 2 + 64) {
                routing = rebuildRoutingContext(topology, positions, profile, states, routingIndexOf);
            }

            NodeId moved = movable.get(random.nextInt(movable.size()));
            Vec2 direction = DIRECTIONS.get(random.nextInt(DIRECTIONS.size()));
            Vec2 oldPos = positions.get(moved);
            // Half-spacing grid snap keeps annealed layouts on the same tidy grid the
            // embedding starts from; the probe evaluates exactly the position it would place.
            // The 0.18-spacing tolerance also catches diagonal probes (0.177x off-grid), so
            // annealed nodes stay grid-aligned instead of accumulating slight tilts.
            Vec2 newPos = CoordinateSnapper.snapPoint(new Vec2(oldPos.x() + direction.x() * step, oldPos.y() + direction.y() * step), profile.stationSpacing() * 0.5D, profile.stationSpacing() * 0.18D);
            if (newPos.distanceTo(oldPos) < EPSILON || !preservesAxisOrder(topology, positions, moved, newPos)) {
                roundsSinceBest++;
                continue;
            }
            List<Integer> incident = incidentByNode.getOrDefault(moved, List.of());
            for (int index : incident) {
                routing.setLive(routingIndexOf[index], false);
            }
            int oldOverlaps = nodeOverlapsAround(topology, positions, profile, moved, oldPos);
            double clearance = nodeObstacleRadius(topology.node(moved)) + 4.0D;
            int oldMovedConflicts = movedNodeConflicts(routing, moved, oldPos, clearance);
            int oldCrossings = 0;
            int oldPathConflicts = 0;
            int oldBends = 0;
            int oldFallbacks = 0;
            double oldLength = 0.0D;
            for (int index : incident) {
                EdgeRouteState state = states.get(index);
                oldCrossings += routing.countCrossings(state.points(), state.edge());
                oldPathConflicts += pathNodeConflicts(routing, state.edge(), state.points());
                oldBends += Math.max(0, state.points().size() - 2);
                oldFallbacks += state.fallback() ? 1 : 0;
                oldLength += polylineLength(state.points());
            }
            List<LineJoint> movedJoints = jointsByNode.getOrDefault(moved, List.of());
            double oldJointTurns = 0.0D;
            for (LineJoint joint : movedJoints) {
                oldJointTurns += jointTurnAmount(joint, edgeId -> {
                    Integer index = stateIndexByEdgeId.get(edgeId);
                    return index == null ? null : states.get(index).points();
                });
            }

            positions.put(moved, newPos);
            int newOverlaps = nodeOverlapsAround(topology, positions, profile, moved, newPos);
            List<RoutedPath> rerouted = new ArrayList<>();
            List<Integer> appended = new ArrayList<>();
            int newCrossings = 0;
            int newPathConflicts = 0;
            int newBends = 0;
            int newFallbacks = 0;
            double newLength = 0.0D;
            for (int index : incident) {
                EdgeRouteState state = states.get(index);
                SchematicEdge edge = state.edge();
                Vec2 from = positions.get(edge.from());
                Vec2 to = positions.get(edge.to());
                CorridorHint hint = corridorHints.getOrDefault(edge.id(), CorridorHint.fromEndpoints(from, to));
                RoutedPath path = this.routeEdge(routing, edge, from, to, hint, corridors, 1.0D);
                // Insert provisionally so the next incident edge is routed against this one; the
                // entry is tombstoned again when the probe is rejected. Mutual crossings between
                // incident edges are never counted either way because they share the moved node.
                appended.add(routing.insert(path.points(), edge));
                rerouted.add(path);
                newCrossings += routing.countCrossings(path.points(), edge);
                newPathConflicts += pathNodeConflicts(routing, edge, path.points());
                newBends += Math.max(0, path.points().size() - 2);
                newFallbacks += path.fallback() ? 1 : 0;
                newLength += polylineLength(path.points());
            }
            int newMovedConflicts = movedNodeConflicts(routing, moved, newPos, clearance);
            Map<String, List<Vec2>> reroutedPaths = new HashMap<>();
            for (int i = 0; i < incident.size(); i++) {
                reroutedPaths.put(states.get(incident.get(i)).edge().id(), rerouted.get(i).points());
            }
            double newJointTurns = 0.0D;
            for (LineJoint joint : movedJoints) {
                newJointTurns += jointTurnAmount(joint, edgeId -> {
                    List<Vec2> replaced = reroutedPaths.get(edgeId);
                    if (replaced != null) {
                        return replaced;
                    }
                    Integer index = stateIndexByEdgeId.get(edgeId);
                    return index == null ? null : states.get(index).points();
                });
            }
            double delta = annealEnergy(newOverlaps, newCrossings, newPathConflicts + newMovedConflicts, newFallbacks, newBends, newLength, newJointTurns)
                    - annealEnergy(oldOverlaps, oldCrossings, oldPathConflicts + oldMovedConflicts, oldFallbacks, oldBends, oldLength, oldJointTurns);
            if (delta <= 0.0D || random.nextDouble() < Math.exp(-delta / temperature)) {
                for (int i = 0; i < incident.size(); i++) {
                    int index = incident.get(i);
                    RoutedPath path = rerouted.get(i);
                    states.set(index, new EdgeRouteState(states.get(index).edge(), path.points(), path.fallback(), path.loopGlyph()));
                }
                // Re-index on every accepted move: the node buckets must see the new position
                // for the next probe's conflict deltas to stay exact, and rebuilding also
                // compacts the tombstoned and provisional entries appended by past probes.
                routing = rebuildRoutingContext(topology, positions, profile, states, routingIndexOf);
                energy += delta;
                if (energy < bestEnergy - 1.0E-9D) {
                    bestEnergy = energy;
                    bestPositions = new LinkedHashMap<>(positions);
                    bestStates = new ArrayList<>(states);
                    roundsSinceBest = 0;
                } else {
                    roundsSinceBest++;
                }
            } else {
                roundsSinceBest++;
                positions.put(moved, oldPos);
                for (int index : appended) {
                    routing.setLive(index, false);
                }
                for (int index : incident) {
                    routing.setLive(routingIndexOf[index], true);
                }
            }
        }

        if (bestEnergy >= initialEnergy - 1.0E-9D) {
            // No improvement over the pre-annealing state: roll back to it.
            return new AnnealOutcome(attempt.positions(), attempt.routeOutput(), rounds, false);
        }
        // Restore the best snapshot and recount its crossings exactly by re-indexing it.
        RoutingContext recount = new RoutingContext(topology, bestPositions, profile);
        int crossings = 0;
        for (EdgeRouteState state : bestStates) {
            crossings += recount.countCrossings(state.points(), state.edge());
            recount.insert(state.points(), state.edge());
        }
        RouteOutput output = this.buildRouteOutput(bestStates, corridors, crossings, attempt.routeOutput().stationInternalEdges(), attempt.routeOutput().unroutedEdges(), attempt.routeOutput().timedOut());
        return new AnnealOutcome(bestPositions, output, rounds, true);
    }

    /**
     * Rebuilds the routing index from scratch over the given positions and per-edge states, and
     * resets the state-to-entry mapping. Replay order matches the state order, so every query
     * against the rebuilt context is deterministic.
     */
    private static RoutingContext rebuildRoutingContext(MetroTopology topology, Map<NodeId, Vec2> positions, LayoutProfile profile, List<EdgeRouteState> states, int[] routingIndexOf) {
        RoutingContext fresh = new RoutingContext(topology, positions, profile);
        for (int i = 0; i < states.size(); i++) {
            fresh.insert(states.get(i).points(), states.get(i).edge());
            routingIndexOf[i] = i;
        }
        return fresh;
    }

    /** Weighted annealing energy; the conflict weights mirror {@link #qualityScore} exactly. */
    private static double annealEnergy(int nodeOverlaps, int crossings, int edgeNodeConflicts, int fallbackEdges, int bends, double totalLength, double lineTurns) {
        return nodeOverlaps * 25_000.0D
                + crossings * 11_000.0D
                + edgeNodeConflicts * 4_000.0D
                + fallbackEdges * 7_500.0D
                + bends * 18.0D
                + totalLength * 0.6D
                + lineTurns * LINE_TURN_WEIGHT;
    }

    /**
     * Route-run line joints: interior stations of every {@link RouteRun} paired with the two
     * edges the run arrives and leaves on. Runs are stitched per (routeLineId, routeLayoutId),
     * so a joint is exactly the place where a drawn line can kink at a station. Joints whose
     * two sequence neighbours are not connected by a single edge (gaps in the stitched
     * sequence) are skipped.
     */
    private static List<LineJoint> lineJoints(MetroTopology topology) {
        Map<String, SchematicEdge> edgeByPair = new HashMap<>();
        for (SchematicEdge edge : topology.edges()) {
            edgeByPair.put(MetroTopology.pairKey(edge.from(), edge.to()), edge);
        }
        List<LineJoint> joints = new ArrayList<>();
        for (RouteRun run : topology.runs()) {
            List<NodeId> sequence = run.sequence();
            for (int i = 1; i < sequence.size() - 1; i++) {
                SchematicEdge incoming = edgeByPair.get(MetroTopology.pairKey(sequence.get(i - 1), sequence.get(i)));
                SchematicEdge outgoing = edgeByPair.get(MetroTopology.pairKey(sequence.get(i), sequence.get(i + 1)));
                if (incoming != null && outgoing != null && !incoming.id().equals(outgoing.id())) {
                    joints.add(new LineJoint(sequence.get(i), incoming, outgoing));
                }
            }
        }
        return joints;
    }

    /**
     * Turn amount of one line joint in [0,1]: 0 when the run passes straight through the
     * station (the into-node and out-of-node directions align), 1 on a full reversal. Only
     * the end segments of the two routed paths at the joint station are considered. Edges
     * without a routed path contribute 0.
     */
    private static double jointTurnAmount(LineJoint joint, Function<String, List<Vec2>> pathByEdgeId) {
        List<Vec2> in = pathByEdgeId.apply(joint.incoming().id());
        List<Vec2> out = pathByEdgeId.apply(joint.outgoing().id());
        if (in == null || out == null || in.size() < 2 || out.size() < 2) {
            return 0.0D;
        }
        Vec2 u = directionInto(joint.incoming(), joint.node(), in);
        Vec2 v = directionOutOf(joint.outgoing(), joint.node(), out);
        if (u == null || v == null) {
            return 0.0D;
        }
        double dot = u.x() * v.x() + u.y() * v.y();
        return (1.0D - Math.max(-1.0D, Math.min(1.0D, dot))) * 0.5D;
    }

    /** Unit travel direction pointing INTO {@code node} along the path's segment at that end. */
    private static Vec2 directionInto(SchematicEdge edge, NodeId node, List<Vec2> points) {
        Vec2 head = points.getFirst();
        Vec2 neck = points.get(1);
        Vec2 tail = points.get(points.size() - 2);
        Vec2 tip = points.getLast();
        if (edge.to().equals(node)) {
            return normalized(tip.x() - tail.x(), tip.y() - tail.y());
        }
        return normalized(head.x() - neck.x(), head.y() - neck.y());
    }

    /** Unit travel direction pointing AWAY from {@code node} along the path's segment at that end. */
    private static Vec2 directionOutOf(SchematicEdge edge, NodeId node, List<Vec2> points) {
        Vec2 head = points.getFirst();
        Vec2 neck = points.get(1);
        Vec2 tail = points.get(points.size() - 2);
        Vec2 tip = points.getLast();
        if (edge.from().equals(node)) {
            return normalized(neck.x() - head.x(), neck.y() - head.y());
        }
        return normalized(tail.x() - tip.x(), tail.y() - tip.y());
    }

    private static Vec2 normalized(double x, double y) {
        double length = Math.hypot(x, y);
        if (length < EPSILON) {
            return null;
        }
        return new Vec2(x / length, y / length);
    }

    /**
     * Axis compaction, executed once for the winning profile after annealing: shrinks the
     * voids the embedding's accumulate-only cursor spacing left behind, without violating the
     * class-level axis-order promise (stations keep their relative order, ties allowed, and
     * every gap stays strictly positive).
     *
     * <p>Each axis is swept independently with a longest-path pass over the axis-sorted
     * stations. Gap targets distinguish two cases: connected pairs keep their embedding beat
     * (a 2x spacing gap marks a genuinely long leg and propagates compression along the
     * chain), while non-connected pairs collapse to at most one spacing beat -- unrelated
     * lines never need more than that between each other, and the even spacing is exactly the
     * transit-map look. Any gap below the 2D minimum distance projected onto the axis
     * (plus half a node gap of margin) expands just enough to stay legal.</p>
     *
     * <p>Non-station nodes are then re-anchored with the same rules as the initial embedding,
     * axis coordinates are re-snapped, and the whole routing pipeline (corridor plan, routing,
     * rip-up-and-reroute) rebuilds on the compacted positions. The compacted result is kept
     * only when the defect counts (node overlaps + edge-node conflicts + edge crossings) did
     * not get worse; otherwise the pass rolls back to the annealed state.</p>
     */
    private CompactionOutcome compactAxes(MetroTopology topology, LayoutProfile profile, Map<NodeId, Vec2> positions, RouteOutput routes, long deadlineNanos) {
        if (!FullRouteMapConfig.SCHEMATIC_COMPACTION_ENABLED
                || positions.size() < 2
                || routes.timedOut()
                || System.nanoTime() >= deadlineNanos - COMPACTION_MIN_BUDGET_NANOS) {
            return new CompactionOutcome(positions, routes, 0, false);
        }
        ConstraintStats before = this.measureGlobalConstraints(topology, positions, profile);
        int defectsBefore = before.nodeOverlaps() + before.edgeNodeConflicts() + routes.crossingCount();
        Map<NodeId, Vec2> compacted = new LinkedHashMap<>(positions);
        boolean moved = this.compactAxis(topology, compacted, profile, true);
        moved |= this.compactAxis(topology, compacted, profile, false);
        if (!moved) {
            return new CompactionOutcome(positions, routes, 0, false);
        }
        this.replaceDerivedNodes(topology, compacted, profile);
        // Re-snap after the last positional mutation, mirroring the embedding's pre-routing
        // snap so stations meant to line up share exact axis coordinates again.
        Map<NodeId, Vec2> snapped = CoordinateSnapper.mergeNearEqualAxes(compacted, profile.stationSpacing() * 0.20D);
        compacted.replaceAll((id, position) -> snapped.get(id));
        Map<NodeId, Vec2> gridded = CoordinateSnapper.snapToGrid(compacted, profile.stationSpacing() * 0.5D, profile.stationSpacing() * 0.18D);
        compacted.replaceAll((id, position) -> gridded.get(id));
        Map<String, CorridorHint> hints = this.corridorHints(topology, compacted);
        CorridorPlan corridors = this.buildCorridorPlan(topology, compacted, profile);
        RouteOutput compactedRoutes = this.routeEdges(topology, compacted, profile, hints, corridors, deadlineNanos);
        RerouteResult rerouted = this.ripUpAndReroute(topology, compacted, profile, compactedRoutes, hints, corridors, deadlineNanos);
        ConstraintStats after = this.measureGlobalConstraints(topology, compacted, profile);
        int defectsAfter = after.nodeOverlaps() + after.edgeNodeConflicts() + rerouted.output().crossingCount();
        if (defectsAfter > defectsBefore) {
            return new CompactionOutcome(positions, routes, rerouted.rounds(), false);
        }
        return new CompactionOutcome(compacted, rerouted.output(), rerouted.rounds(), true);
    }

    /**
     * Single-axis compaction sweep. Stations are processed in axis order; each station's new
     * axis value is the previous station's (already compacted) value plus the pair's target
     * gap, computed from the ORIGINAL gap so connected beats propagate along the chain.
     * Returns true when at least one station moved.
     */
    private boolean compactAxis(MetroTopology topology, Map<NodeId, Vec2> positions, LayoutProfile profile, boolean xAxis) {
        List<NodeId> stations = positions.keySet().stream()
                .filter(id -> topology.node(id).kind() == NodeKind.STATION)
                .sorted(Comparator
                        .comparingDouble((NodeId id) -> xAxis ? positions.get(id).x() : positions.get(id).y())
                        .thenComparingDouble(id -> xAxis ? positions.get(id).y() : positions.get(id).x())
                        .thenComparing(NodeId::compareTo))
                .toList();
        Map<NodeId, Double> original = new HashMap<>();
        for (NodeId station : stations) {
            Vec2 position = positions.get(station);
            original.put(station, xAxis ? position.x() : position.y());
        }
        boolean moved = false;
        for (int i = 1; i < stations.size(); i++) {
            NodeId previousId = stations.get(i - 1);
            NodeId currentId = stations.get(i);
            boolean connected = topology.connected(previousId, currentId);
            Vec2 previous = positions.get(previousId);
            Vec2 current = positions.get(currentId);
            double gap = original.get(currentId) - original.get(previousId);
            double otherSeparation = Math.abs(xAxis ? current.y() - previous.y() : current.x() - previous.x());
            double minimum = minNodeDistance(topology.node(previousId), topology.node(currentId), profile, connected);
            // The 2D minimum distance projected onto this axis, given the other axis'
            // separation, plus half a node gap of margin.
            double required = Math.sqrt(Math.max(0.0D, minimum * minimum - otherSeparation * otherSeparation)) + profile.nodeGap() * 0.5D;
            double target = connected
                    ? Math.max(gap, required)
                    : Math.max(Math.min(gap, profile.stationSpacing()), required);
            double updated = (xAxis ? previous.x() : previous.y()) + target;
            Vec2 replacement = xAxis ? new Vec2(updated, current.y()) : new Vec2(current.x(), updated);
            if (replacement.distanceTo(current) > EPSILON) {
                positions.put(currentId, replacement);
                moved = true;
            }
        }
        return moved;
    }

    /**
     * Re-anchors every non-station node after stations moved, with the same rules the initial
     * embedding uses: portals hang off their station anchor in the world-space direction;
     * clusters and anchor-less nodes sit on a collision-free ring slot near their placed
     * neighbours (or the centroid of everything placed when fully isolated).
     */
    private void replaceDerivedNodes(MetroTopology topology, Map<NodeId, Vec2> positions, LayoutProfile profile) {
        for (NodeId nodeId : topology.nodesById().keySet().stream().sorted(NodeId::compareTo).toList()) {
            SchematicNode node = topology.node(nodeId);
            if (node.kind() == NodeKind.STATION) {
                continue;
            }
            if (node.kind() == NodeKind.FOLD_ANCHOR) {
                Optional<NodeId> anchor = topology.neighbors(nodeId).stream()
                        .filter(id -> topology.node(id).kind() == NodeKind.STATION)
                        .findFirst();
                if (anchor.isPresent()) {
                    SchematicNode anchorNode = topology.node(anchor.get());
                    Vec2 preferred = nearestDirection(node.worldX() - anchorNode.worldX(), node.worldZ() - anchorNode.worldZ());
                    positions.put(nodeId, this.bestPortalSlot(topology, nodeId, positions.get(anchor.get()), preferred, positions, profile));
                    continue;
                }
            }
            Vec2 origin;
            Vec2 preferred;
            List<NodeId> placedNeighbors = topology.neighbors(nodeId).stream()
                    .filter(positions::containsKey)
                    .toList();
            if (placedNeighbors.isEmpty()) {
                origin = positions.isEmpty() ? new Vec2(0.0D, 0.0D) : average(List.copyOf(positions.values()));
                preferred = hashDirection(nodeId);
            } else {
                origin = average(placedNeighbors.stream().map(positions::get).toList());
                double meanWorldX = placedNeighbors.stream().mapToDouble(id -> topology.node(id).worldX()).average().orElse(node.worldX());
                double meanWorldZ = placedNeighbors.stream().mapToDouble(id -> topology.node(id).worldZ()).average().orElse(node.worldZ());
                preferred = nearestDirection(node.worldX() - meanWorldX, node.worldZ() - meanWorldZ);
            }
            positions.put(nodeId, this.bestPortalSlot(topology, nodeId, origin, preferred, positions, profile));
        }
    }

    /**
     * Hard order constraint for annealing: stations with distinct world X (or Z) must keep their
     * relative schematic order on that axis, so a probe move that would flip or collapse the
     * ordering of any station pair is rejected. Non-station nodes carry no axis promise and move
     * freely; pairs with equal world coordinates were never ordered by the embedding either.
     */
    private static boolean preservesAxisOrder(MetroTopology topology, Map<NodeId, Vec2> positions, NodeId moved, Vec2 newPos) {
        SchematicNode node = topology.node(moved);
        if (node.kind() != NodeKind.STATION) {
            return true;
        }
        for (Map.Entry<NodeId, Vec2> entry : positions.entrySet()) {
            if (entry.getKey().equals(moved)) {
                continue;
            }
            SchematicNode other = topology.node(entry.getKey());
            if (other.kind() != NodeKind.STATION) {
                continue;
            }
            double worldDeltaX = node.worldX() - other.worldX();
            if (Math.abs(worldDeltaX) > EPSILON && Math.signum(newPos.x() - entry.getValue().x()) != Math.signum(worldDeltaX)) {
                return false;
            }
            double worldDeltaZ = node.worldZ() - other.worldZ();
            if (Math.abs(worldDeltaZ) > EPSILON && Math.signum(newPos.y() - entry.getValue().y()) != Math.signum(worldDeltaZ)) {
                return false;
            }
        }
        return true;
    }

    /** Overlapping node pairs involving {@code moved} if it sat at {@code at}; O(node count). */
    private static int nodeOverlapsAround(MetroTopology topology, Map<NodeId, Vec2> positions, LayoutProfile profile, NodeId moved, Vec2 at) {
        int count = 0;
        SchematicNode node = topology.node(moved);
        for (Map.Entry<NodeId, Vec2> entry : positions.entrySet()) {
            if (entry.getKey().equals(moved)) {
                continue;
            }
            double min = minNodeDistance(node, topology.node(entry.getKey()), profile, topology.connected(moved, entry.getKey()));
            if (at.distanceTo(entry.getValue()) < min) {
                count++;
            }
        }
        return count;
    }

    /**
     * Nodes whose clearance disc is clipped by the given path, endpoint nodes excluded; the same
     * definition as {@link #measureGlobalConstraints} so the annealing energy tracks the reported
     * edge-node conflict count.
     */
    private static int pathNodeConflicts(RoutingContext routing, SchematicEdge edge, List<Vec2> path) {
        int count = 0;
        int nearby = routing.collectNodesNear(path);
        for (int k = 0; k < nearby; k++) {
            NodeId nodeId = routing.nodeId(routing.nodeScratch[k]);
            if (nodeId.equals(edge.from()) || nodeId.equals(edge.to())) {
                continue;
            }
            double clearance = nodeObstacleRadius(routing.topology().node(nodeId)) + 4.0D;
            if (distanceToPolyline(routing.position(routing.nodeScratch[k]), path) < clearance) {
                count++;
            }
        }
        return count;
    }

    /** Live routed paths clipping the moved node's clearance disc at the given position. */
    private static int movedNodeConflicts(RoutingContext routing, NodeId moved, Vec2 at, double clearance) {
        int count = 0;
        // A stub segment so the grid query has an extent; the pad covers the maximum clearance.
        int nearby = routing.collectPathsNear(List.of(at, new Vec2(at.x() + 0.1D, at.y())), RoutingContext.MAX_NODE_CLEARANCE);
        for (int k = 0; k < nearby; k++) {
            SchematicEdge other = routing.routedEdge(routing.pathScratch[k]);
            if (other.from().equals(moved) || other.to().equals(moved)) {
                continue;
            }
            if (distanceToPolyline(at, routing.routedPath(routing.pathScratch[k])) < clearance) {
                count++;
            }
        }
        return count;
    }

    /**
     * Deterministic annealing seed derived from the input graph content (sorted node ids with
     * their world coordinates, sorted edge ids with their kinds, and the profile name), so the
     * same map always replays the same probe sequence.
     */
    private static long annealSeed(SchematicInputGraph input, LayoutProfile profile) {
        long hash = 0x9E3779B97F4A7C15L;
        for (SchematicNode node : input.nodes()) {
            hash = 31L * hash + node.id().hashCode();
            hash = 31L * hash + Double.hashCode(node.worldX());
            hash = 31L * hash + Double.hashCode(node.worldZ());
        }
        for (SchematicEdge edge : input.edges().stream().sorted(Comparator.comparing(SchematicEdge::id)).toList()) {
            hash = 31L * hash + edge.id().hashCode();
            hash = 31L * hash + edge.kind().ordinal();
        }
        return 31L * hash + profile.name().hashCode();
    }

    /**
     * Procrustes-style stabilisation of a fresh embedding against the previously cached layout:
     * a least-squares translation plus uniform scale over the shared nodes, applied to every
     * node. No rotation or reflection is fitted, and the scale is clamped to a sane positive
     * band, so the axis ordering and orientation promised by this class are preserved exactly
     * while stations that existed before stay visually put when stations are added or removed.
     * Returns false when the two layouts share no node.
     */
    private static boolean alignToPrevious(Map<NodeId, Vec2> positions, VisualRouteMapGraphSnapshot previous) {
        List<NodeId> shared = positions.keySet().stream()
                .filter(id -> previous.position(id).isPresent())
                .sorted(NodeId::compareTo)
                .toList();
        if (shared.isEmpty()) {
            return false;
        }
        double meanNewX = 0.0D;
        double meanNewY = 0.0D;
        double meanOldX = 0.0D;
        double meanOldY = 0.0D;
        for (NodeId id : shared) {
            Vec2 position = positions.get(id);
            VisualRouteMapGraphSnapshot.Position old = previous.position(id).get();
            meanNewX += position.x();
            meanNewY += position.y();
            meanOldX += old.x();
            meanOldY += old.z();
        }
        meanNewX /= shared.size();
        meanNewY /= shared.size();
        meanOldX /= shared.size();
        meanOldY /= shared.size();
        double scale = 1.0D;
        if (shared.size() > 1) {
            double numerator = 0.0D;
            double denominator = 0.0D;
            for (NodeId id : shared) {
                Vec2 position = positions.get(id);
                VisualRouteMapGraphSnapshot.Position old = previous.position(id).get();
                double dxNew = position.x() - meanNewX;
                double dyNew = position.y() - meanNewY;
                numerator += dxNew * (old.x() - meanOldX) + dyNew * (old.z() - meanOldY);
                denominator += dxNew * dxNew + dyNew * dyNew;
            }
            if (denominator > EPSILON) {
                double fitted = numerator / denominator;
                // Guard against degenerate or anti-correlated fits: only a positive scale near 1
                // keeps the embedding's per-axis ordering intact.
                if (Double.isFinite(fitted) && fitted > 0.5D && fitted < 2.0D) {
                    scale = fitted;
                }
            }
        }
        double offsetX = meanOldX - meanNewX * scale;
        double offsetY = meanOldY - meanNewY * scale;
        for (Map.Entry<NodeId, Vec2> entry : positions.entrySet()) {
            Vec2 position = entry.getValue();
            entry.setValue(new Vec2(position.x() * scale + offsetX, position.y() * scale + offsetY));
        }
        return true;
    }

    /**
     * Lane assignment for {@code PARALLEL_CORRIDOR} edges. Members are grouped greedily in
     * deterministic edge order by schematic-space near-parallelism (direction, normal distance,
     * projection overlap -- the same shape of test the input builder paid its O(E^2) for in world
     * space), then each group with at least two members gets lane offsets symmetric about the
     * corridor centre line, ordered by the members' current normal projection.
     */
    private CorridorPlan buildCorridorPlan(MetroTopology topology, Map<NodeId, Vec2> positions, LayoutProfile profile) {
        List<SchematicEdge> candidates = topology.edges().stream()
                .filter(edge -> edge.kind() == SemanticEdgeKind.PARALLEL_CORRIDOR)
                .filter(edge -> positions.get(edge.from()) != null && positions.get(edge.to()) != null)
                .sorted(edgeOrder())
                .toList();
        if (candidates.isEmpty()) {
            return CorridorPlan.empty();
        }
        List<List<SchematicEdge>> groups = new ArrayList<>();
        for (SchematicEdge edge : candidates) {
            List<SchematicEdge> match = null;
            for (List<SchematicEdge> group : groups) {
                boolean fits = false;
                for (SchematicEdge member : group) {
                    if (nearParallelInSchematic(positions, member, edge, profile)) {
                        fits = true;
                        break;
                    }
                }
                if (fits) {
                    match = group;
                    break;
                }
            }
            if (match == null) {
                match = new ArrayList<>();
                groups.add(match);
            }
            match.add(edge);
        }
        Map<String, Integer> groupByEdgeId = new HashMap<>();
        Map<String, CorridorLane> laneByEdgeId = new HashMap<>();
        Map<Integer, Double> laneStepByGroup = new HashMap<>();
        double desiredStep = Math.max(profile.routeSeparation() * 1.25D, FullRouteMapConfig.LINE_WIDTH_PX / FullRouteMapConfig.BASE_SCALE + 4.0D);
        double maxSpread = profile.stationSpacing() * 0.6D;
        int nextGroupId = 0;
        for (List<SchematicEdge> group : groups) {
            int groupId = nextGroupId++;
            if (group.size() < 2) {
                continue;
            }
            // Corridor axis: sign-aligned mean of member directions snapped to the octilinear grid.
            Vec2 reference = unitDirectionOf(positions, group.getFirst());
            double sumX = 0.0D;
            double sumY = 0.0D;
            for (SchematicEdge edge : group) {
                Vec2 direction = unitDirectionOf(positions, edge);
                if (dot(direction, reference) < 0.0D) {
                    direction = reverse(direction);
                }
                sumX += direction.x();
                sumY += direction.y();
            }
            Vec2 axis = nearestDirection(sumX, sumY);
            Vec2 normal = new Vec2(-axis.y(), axis.x());
            List<SchematicEdge> ordered = new ArrayList<>(group);
            ordered.sort(Comparator
                    .comparingDouble((SchematicEdge edge) -> {
                        Vec2 midpoint = midpointOf(positions, edge);
                        return midpoint.x() * normal.x() + midpoint.y() * normal.y();
                    })
                    .thenComparing(SchematicEdge::id));
            double step = desiredStep;
            if ((ordered.size() - 1) * step > maxSpread) {
                step = maxSpread / (ordered.size() - 1);
            }
            double center = (ordered.size() - 1) * 0.5D;
            laneStepByGroup.put(groupId, step);
            for (int i = 0; i < ordered.size(); i++) {
                SchematicEdge edge = ordered.get(i);
                groupByEdgeId.put(edge.id(), groupId);
                laneByEdgeId.put(edge.id(), new CorridorLane(groupId, (i - center) * step, axis, step));
            }
        }
        return new CorridorPlan(groupByEdgeId, laneByEdgeId, laneStepByGroup);
    }

    /**
     * Schematic-space near-parallelism used for corridor grouping: direction alignment, mean
     * normal distance, and projection overlap, ported from the heuristic solver's corridor test
     * with profile-derived thresholds.
     */
    private static boolean nearParallelInSchematic(Map<NodeId, Vec2> positions, SchematicEdge first, SchematicEdge second, LayoutProfile profile) {
        Vec2 a1 = positions.get(first.from());
        Vec2 a2 = positions.get(first.to());
        Vec2 b1 = positions.get(second.from());
        Vec2 b2 = positions.get(second.to());
        double ax = a2.x() - a1.x();
        double ay = a2.y() - a1.y();
        double bx = b2.x() - b1.x();
        double by = b2.y() - b1.y();
        double al = Math.hypot(ax, ay);
        double bl = Math.hypot(bx, by);
        if (al < 24.0D || bl < 24.0D) {
            return false;
        }
        double aux = ax / al;
        double auy = ay / al;
        double bux = bx / bl;
        double buy = by / bl;
        if (Math.abs(aux * bux + auy * buy) < 0.975D) {
            return false;
        }
        double distance = (distanceToInfiniteLine(b1.x(), b1.y(), a1.x(), a1.y(), aux, auy)
                + distanceToInfiniteLine(b2.x(), b2.y(), a1.x(), a1.y(), aux, auy)
                + distanceToInfiniteLine(a1.x(), a1.y(), b1.x(), b1.y(), bux, buy)
                + distanceToInfiniteLine(a2.x(), a2.y(), b1.x(), b1.y(), bux, buy)) * 0.25D;
        // The order-preserving embedding inflates small world gaps to at least one station
        // spacing, so the schematic-space corridor distance threshold must be proportionally
        // wider than the world-space mark the input builder used, or no pair ever groups.
        if (distance > profile.stationSpacing() * 1.5D) {
            return false;
        }
        double secondA = projection(b1.x(), b1.y(), a1.x(), a1.y(), aux, auy);
        double secondB = projection(b2.x(), b2.y(), a1.x(), a1.y(), aux, auy);
        double overlap = Math.min(al, Math.max(secondA, secondB)) - Math.max(0.0D, Math.min(secondA, secondB));
        return overlap >= Math.min(al, bl) * 0.25D;
    }

    /**
     * Shifts a corridor member's candidate path into its assigned lane. Every point is translated
     * by the same normal offset -- which preserves each segment's direction -- and the endpoints
     * are re-anchored with 45-degree ramps whose along-track run equals the offset, so an
     * octilinear input stays octilinear as long as the path's end segments run parallel to the
     * corridor axis and are long enough to absorb a ramp. Any other shape keeps its unshifted
     * candidate rather than paying the non-octilinear fallback penalty for a lane offset.
     */
    private static List<Vec2> offsetCorridorPath(List<Vec2> points, CorridorLane lane) {
        double offset = lane.offset();
        if (Math.abs(offset) < 0.75D || points.size() < 2) {
            return points;
        }
        Vec2 firstDirection = segmentDirection(points.get(0), points.get(1)).orElse(null);
        Vec2 lastDirection = segmentDirection(points.get(points.size() - 2), points.get(points.size() - 1)).orElse(null);
        if (firstDirection == null || lastDirection == null) {
            return points;
        }
        if (Math.abs(dot(firstDirection, lane.axis())) < 0.999D || Math.abs(dot(lastDirection, lane.axis())) < 0.999D) {
            return points;
        }
        double ramp = Math.abs(offset);
        if (points.get(0).distanceTo(points.get(1)) < ramp + 1.0D
                || points.get(points.size() - 2).distanceTo(points.get(points.size() - 1)) < ramp + 1.0D) {
            return points;
        }
        double length = polylineLength(points);
        if (length < ramp * 2.0D + 6.0D) {
            return points;
        }
        Vec2 normal = new Vec2(-lane.axis().y(), lane.axis().x());
        List<Vec2> shifted = new ArrayList<>();
        for (Vec2 point : points) {
            shifted.add(new Vec2(point.x() + normal.x() * offset, point.y() + normal.y() * offset));
        }
        List<Vec2> result = new ArrayList<>();
        result.add(points.getFirst());
        result.add(pointAlongPolyline(shifted, ramp));
        for (int i = 1; i + 1 < shifted.size(); i++) {
            double distance = distanceAlongPolyline(shifted, i);
            if (distance > ramp && distance < length - ramp) {
                result.add(shifted.get(i));
            }
        }
        result.add(pointAlongPolyline(shifted, length - ramp));
        result.add(points.getLast());
        return dedupePath(result);
    }

    /** Grouped corridor members ending up closer than half their assigned lane step. */
    private static int countCorridorViolations(List<EdgeRouteState> states, CorridorPlan corridors) {
        List<EdgeRouteState> grouped = states.stream()
                .filter(state -> corridors.groupOf(state.edge().id()) >= 0)
                .toList();
        int violations = 0;
        for (int i = 0; i < grouped.size(); i++) {
            int group = corridors.groupOf(grouped.get(i).edge().id());
            for (int j = i + 1; j < grouped.size(); j++) {
                if (corridors.groupOf(grouped.get(j).edge().id()) != group) {
                    continue;
                }
                if (sharesEndpoint(grouped.get(i).edge(), grouped.get(j).edge())) {
                    continue;
                }
                if (minPolylineDistance(grouped.get(i).points(), grouped.get(j).points()) < corridors.laneStep(group) * 0.5D) {
                    violations++;
                }
            }
        }
        return violations;
    }

    private static double minPolylineDistance(List<Vec2> first, List<Vec2> second) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i + 1 < first.size(); i++) {
            for (int j = 0; j + 1 < second.size(); j++) {
                best = Math.min(best, segmentToSegmentDistance(first.get(i), first.get(i + 1), second.get(j), second.get(j + 1)));
            }
        }
        return best;
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

    private static double distanceToInfiniteLine(double x, double y, double originX, double originY, double ux, double uy) {
        return Math.abs((x - originX) * uy - (y - originY) * ux);
    }

    private static double projection(double x, double y, double originX, double originY, double ux, double uy) {
        return (x - originX) * ux + (y - originY) * uy;
    }

    private static Vec2 unitDirectionOf(Map<NodeId, Vec2> positions, SchematicEdge edge) {
        Vec2 from = positions.get(edge.from());
        Vec2 to = positions.get(edge.to());
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double length = Math.hypot(dx, dy);
        return length < EPSILON ? new Vec2(1.0D, 0.0D) : new Vec2(dx / length, dy / length);
    }

    private static Vec2 midpointOf(Map<NodeId, Vec2> positions, SchematicEdge edge) {
        Vec2 from = positions.get(edge.from());
        Vec2 to = positions.get(edge.to());
        return new Vec2((from.x() + to.x()) * 0.5D, (from.y() + to.y()) * 0.5D);
    }

    /**
     * Budget-exhausted routing: loop-back edges keep their (cheap) loop glyph, every other edge is
     * connected directly and flagged with the usual fallback semantics when it is not octilinear.
     * Without a routing context the loop shoulder cannot be scored against its surroundings, so
     * the hash-preferred side is kept here.
     */
    private static RoutedPath degradedPath(SchematicEdge edge, Vec2 from, Vec2 to, LayoutProfile profile) {
        if (edge.kind() == SemanticEdgeKind.LOOP_BACK) {
            return new RoutedPath(loopPath(from, to, edge.id(), profile, false), false, true);
        }
        List<Vec2> direct = dedupePath(List.of(from, to));
        return new RoutedPath(direct, !isOctilinearPath(direct), false);
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

    private RoutedPath routeEdge(RoutingContext routing, SchematicEdge edge, Vec2 from, Vec2 to, CorridorHint corridorHint, CorridorPlan corridors, double crossingScale) {
        if (edge.kind() == SemanticEdgeKind.LOOP_BACK) {
            // Environment-aware shoulder pick: both sides of the loop glyph are scored against
            // the already routed surroundings and the cheaper one wins; a tie (or an empty
            // neighbourhood) keeps the hash-preferred shoulder.
            List<Vec2> preferred = loopPath(from, to, edge.id(), routing.profile(), false);
            List<Vec2> alternate = loopPath(from, to, edge.id(), routing.profile(), true);
            double preferredScore = routeScore(routing, edge, preferred, corridorHint, corridors, crossingScale).score();
            double alternateScore = routeScore(routing, edge, alternate, corridorHint, corridors, crossingScale).score();
            return new RoutedPath(alternateScore < preferredScore - EPSILON ? alternate : preferred, false, true);
        }
        List<List<Vec2>> candidates = this.routeCandidates(from, to, edge, corridorHint, corridors, routing.profile());
        double direct = Math.max(1.0D, from.distanceTo(to));
        List<Vec2> best = List.of(from, to);
        double bestScore = Double.POSITIVE_INFINITY;
        boolean cleanSeen = false;
        for (List<Vec2> candidate : candidates) {
            RouteScoreEvaluation evaluation = routeScore(routing, edge, candidate, corridorHint, corridors, crossingScale);
            if (evaluation.score() < bestScore) {
                bestScore = evaluation.score();
                best = candidate;
            }
            cleanSeen |= evaluation.clean();
            // Candidates arrive in ascending seed-score order and the seed score yields a monotone
            // lower bound for the routing score, so once a conflict-free (zero obstacle, zero
            // crossing, direction-satisfying) candidate exists, nothing past the break point can
            // still win. Ties never replace the incumbent (strict <), so this is exact.
            if (cleanSeen && seedScoreLowerBound(candidateSeedScore(candidate), direct) >= bestScore) {
                break;
            }
        }
        return new RoutedPath(dedupePath(best), !isOctilinearPath(best), false);
    }

    private List<List<Vec2>> routeCandidates(Vec2 from, Vec2 to, SchematicEdge edge, CorridorHint corridorHint, CorridorPlan corridors, LayoutProfile profile) {
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
        // Short edges almost never need exotic detours, so their candidate list is capped much
        // lower; the seed-score sort keeps the most promising candidates in front regardless.
        int candidateLimit = directDistance <= profile.stationSpacing() * 1.6D ? 40 : 180;
        CorridorLane lane = corridors.lane(edge.id());
        return candidates.stream()
                .map(MetroMapSchematicSolver::dedupePath)
                .filter(path -> path.size() >= 2)
                .distinct()
                // Corridor members are shifted into their assigned lane before scoring, so the
                // seed sort, the exact pruning bound, and the conflict checks all see the final
                // drawn geometry.
                .map(path -> lane == null ? path : offsetCorridorPath(path, lane))
                .filter(path -> path.size() >= 2)
                .distinct()
                .sorted(Comparator.comparingDouble(MetroMapSchematicSolver::candidateSeedScore))
                .limit(candidateLimit)
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

    private static RouteScoreEvaluation routeScore(RoutingContext routing, SchematicEdge edge, List<Vec2> path, CorridorHint corridorHint, CorridorPlan corridors, double crossingScale) {
        LayoutProfile profile = routing.profile();
        double direct = Math.max(1.0D, path.getFirst().distanceTo(path.getLast()));
        double endpointPenalty = endpointDirectionPenalty(path, corridorHint.direction());
        double score = polylineLength(path) / direct
                + Math.max(0, path.size() - 2) * 0.72D
                + directionPenalty(path) * 42.0D
                + endpointPenalty * 3.8D
                + turnPenalty(path) * 0.34D;
        boolean octilinear = isOctilinearPath(path);
        if (!octilinear) {
            score += 1_000.0D;
        }
        double conflictPenalty = 0.0D;
        int nearbyNodes = routing.collectNodesNear(path);
        for (int k = 0; k < nearbyNodes; k++) {
            NodeId nodeId = routing.nodeId(routing.nodeScratch[k]);
            if (nodeId.equals(edge.from()) || nodeId.equals(edge.to())) {
                continue;
            }
            double clearance = nodeObstacleRadius(routing.topology().node(nodeId)) + 5.0D;
            double distance = distanceToPolyline(routing.position(routing.nodeScratch[k]), path);
            if (distance < clearance) {
                conflictPenalty += 10.0D + square((clearance - distance) / clearance) * 34.0D;
            }
        }
        int nearbyPaths = routing.collectPathsNear(path, corridors.queryPad(edge.id(), profile.routeSeparation()));
        for (int k = 0; k < nearbyPaths; k++) {
            List<Vec2> routed = routing.routedPath(routing.pathScratch[k]);
            if (polylinesIntersect(path, routed)) {
                conflictPenalty += 11.0D * crossingScale;
            }
            int sharedGroup = corridors.sharedGroup(edge, routing.routedEdge(routing.pathScratch[k]));
            if (sharedGroup >= 0) {
                // Members of one corridor do not pay the generic proximity charge; instead their
                // separation is enforced against the assigned lane step, so fully separated lanes
                // score zero here while squeezed lanes are charged harder than generic closeness.
                // There is deliberately no negative separation bonus: every score term must stay
                // non-negative or the seed-score pruning bound in routeEdge stops being exact.
                conflictPenalty += polylineProximityPenalty(path, routed, corridors.laneStep(sharedGroup)) * 8.0D;
            } else {
                double proximity = polylineProximityPenalty(path, routed, profile.routeSeparation());
                conflictPenalty += proximity * 5.4D;
            }
        }
        score += conflictPenalty;
        if (edge.kind() == SemanticEdgeKind.FOLD_ADJACENT) {
            score += Math.max(0, path.size() - 2) * 0.46D;
        }
        return new RouteScoreEvaluation(score, octilinear && conflictPenalty == 0.0D && endpointPenalty == 0.0D);
    }

    /**
     * Monotone lower bound of {@link #routeScore} derived from {@link #candidateSeedScore}. The
     * routing score is at least {@code length/direct + bends*0.72 + directionPenalty*42} because
     * every dropped term is non-negative; minimising that expression under the seed-score identity
     * {@code seed = length + bends*32 + directionPenalty*240} with {@code length >= direct} yields
     * a bound that never decreases as the seed score grows, which makes the early break in
     * {@link #routeEdge} exact rather than heuristic.
     */
    private static double seedScoreLowerBound(double seedScore, double direct) {
        if (direct * 0.0225D >= 1.0D) {
            return seedScore / direct;
        }
        return 1.0D + 0.0225D * Math.max(0.0D, seedScore - direct);
    }

    private List<VisualLabel> layoutLabels(MetroTopology topology, Map<NodeId, Vec2> positions, List<VisualEdgePath> edges, LayoutProfile profile, LabelWidthMeasurer widthMeasurer) {
        this.labelWidthMeasurer = widthMeasurer;
        List<NodeId> ordered = positions.keySet().stream()
                .sorted(Comparator.comparingInt((NodeId id) -> topology.node(id).importance()).reversed().thenComparing(NodeId::compareTo))
                .toList();
        Map<NodeId, List<LabelSlot>> linePreferences = linePreferredSlots(topology);
        List<LabelBox> placed = new ArrayList<>();
        List<LabelWork> works = new ArrayList<>();
        for (NodeId nodeId : ordered) {
            SchematicNode node = topology.node(nodeId);
            Vec2 position = positions.get(nodeId);
            if (node.label().isBlank()) {
                continue;
            }
            List<LabelCandidate> candidates = labelCandidates(node, position);
            LabelCandidate best = null;
            double bestScore = Double.POSITIVE_INFINITY;
            for (LabelCandidate candidate : candidates) {
                double score = labelPlacementScore(candidate, nodeId, node.importance(), placed, -1, edges, linePreferences);
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            if (best == null) {
                continue;
            }
            placed.add(new LabelBox(best.box().minX(), best.box().minY(), best.box().maxX(), best.box().maxY(), node.importance()));
            works.add(new LabelWork(nodeId, node, candidates, best, labelPenalized(best, bestScore, linePreferences.get(nodeId))));
        }
        this.improveLabelPlacements(works, placed, edges, linePreferences);
        List<VisualLabel> labels = new ArrayList<>();
        for (LabelWork work : works) {
            // Never skip: the old 520 skip threshold hid low-importance station names entirely.
            // Every label is placed at its best candidate; fallback records that the placement
            // carries conflict penalties, letting the renderer declutter it by zoom and priority
            // instead of the solver deleting it here.
            labels.add(new VisualLabel(work.nodeId(), work.node().label(), work.chosen().x(), work.chosen().y(), work.node().importance(), labelScale(work.node()), work.penalized(), work.chosen().slot()));
        }
        return labels;
    }

    /**
     * Deterministic local-search sweeps after the greedy pass. The greedy order lets early
     * high-importance labels push later ones into bad slots; these sweeps revisit every
     * conflicted label and move it to a better slot whenever the total placement score
     * improves, so placements no longer depend as heavily on processing order. Sweeps stop
     * on the first round without an improvement, at {@link #LABEL_IMPROVE_MAX_SWEEPS}, or at
     * the {@link #LABEL_IMPROVE_BUDGET_NANOS} wall-clock budget, whichever comes first; the
     * fixed label/candidate order keeps the outcome deterministic.
     */
    private void improveLabelPlacements(List<LabelWork> works, List<LabelBox> placed, List<VisualEdgePath> edges, Map<NodeId, List<LabelSlot>> linePreferences) {
        List<Integer> conflicted = new ArrayList<>();
        for (int i = 0; i < works.size(); i++) {
            if (works.get(i).penalized()) {
                conflicted.add(i);
            }
        }
        if (conflicted.isEmpty()) {
            return;
        }
        long deadline = System.nanoTime() + LABEL_IMPROVE_BUDGET_NANOS;
        for (int sweep = 0; sweep < LABEL_IMPROVE_MAX_SWEEPS; sweep++) {
            if (System.nanoTime() >= deadline) {
                break;
            }
            boolean improved = false;
            for (int index : conflicted) {
                LabelWork work = works.get(index);
                double bestScore = labelPlacementScore(work.chosen(), work.nodeId(), work.node().importance(), placed, index, edges, linePreferences);
                LabelCandidate best = work.chosen();
                for (LabelCandidate candidate : work.candidates()) {
                    double score = labelPlacementScore(candidate, work.nodeId(), work.node().importance(), placed, index, edges, linePreferences);
                    if (score < bestScore - 1.0E-9D) {
                        bestScore = score;
                        best = candidate;
                    }
                }
                if (best != work.chosen()) {
                    work.chosen(best);
                    placed.set(index, new LabelBox(best.box().minX(), best.box().minY(), best.box().maxX(), best.box().maxY(), work.node().importance()));
                    work.penalized(labelPenalized(best, bestScore, linePreferences.get(work.nodeId())));
                    improved = true;
                }
            }
            if (!improved) {
                break;
            }
        }
    }

    /**
     * Total placement score of one label candidate: distance from the node, minus the
     * line-side consistency discount, plus label-label and label-edge conflict penalties
     * (edges incident to the label's own node are exempt). {@code skipIndex} excludes the
     * label's own current box when re-scoring an already placed label.
     */
    private static double labelPlacementScore(LabelCandidate candidate, NodeId nodeId, int importance, List<LabelBox> placed, int skipIndex, List<VisualEdgePath> edges, Map<NodeId, List<LabelSlot>> linePreferences) {
        double score = candidate.distanceFromNode() * 0.035D - lineSideDiscount(candidate.slot(), linePreferences.get(nodeId));
        for (int i = 0; i < placed.size(); i++) {
            if (i == skipIndex) {
                continue;
            }
            LabelBox box = placed.get(i);
            if (candidate.box().intersects(box)) {
                score += box.priority() > importance ? 520.0D : 120.0D;
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
        return score;
    }

    /** Line-side consistency discount for a candidate slot; 0 when the slot is not preferred. */
    private static double lineSideDiscount(LabelSlot slot, List<LabelSlot> preferred) {
        if (preferred == null) {
            return 0.0D;
        }
        if (slot == preferred.getFirst()) {
            return LABEL_LINE_SIDE_PRIMARY_BONUS;
        }
        return preferred.size() > 1 && slot == preferred.get(1) ? LABEL_LINE_SIDE_SECONDARY_BONUS : 0.0D;
    }

    /**
     * Whether a placement carries real conflict penalties. The line-side discount can push a
     * conflict-free score negative, so the flag keys on the penalty terms only: undo the
     * discount before comparing against the pure distance score.
     */
    private static boolean labelPenalized(LabelCandidate candidate, double score, List<LabelSlot> preferred) {
        return score + lineSideDiscount(candidate.slot(), preferred) > candidate.distanceFromNode() * 0.035D;
    }

    /**
     * Metro-map label side consistency: assigns each station its dominant line's preferred
     * label side. Runs are already sorted by coverage score, so the first run claiming a
     * station wins. Horizontal lines alternate above/below by station index, vertical lines
     * alternate right/left, diagonal lines keep the global preference order, and transfer
     * stations (two or more runs) always prefer right-then-above.
     */
    private static Map<NodeId, List<LabelSlot>> linePreferredSlots(MetroTopology topology) {
        Map<NodeId, Integer> memberships = new HashMap<>();
        for (RouteRun run : topology.runs()) {
            for (NodeId station : run.sequence()) {
                memberships.merge(station, 1, Integer::sum);
            }
        }
        Map<NodeId, List<LabelSlot>> preferences = new LinkedHashMap<>();
        Set<NodeId> claimed = new HashSet<>();
        for (RouteRun run : topology.runs()) {
            List<LabelSlot> pair = dominantSidePair(run, topology);
            for (int i = 0; i < run.sequence().size(); i++) {
                NodeId station = run.sequence().get(i);
                if (memberships.getOrDefault(station, 0) >= 2 || !claimed.add(station)) {
                    continue;
                }
                if (pair != null) {
                    preferences.put(station, i % 2 == 0 ? pair : List.of(pair.get(1), pair.get(0)));
                }
            }
        }
        for (Map.Entry<NodeId, Integer> entry : memberships.entrySet()) {
            if (entry.getValue() >= 2) {
                preferences.put(entry.getKey(), List.of(LabelSlot.RIGHT_NEAR, LabelSlot.ABOVE_NEAR));
            }
        }
        return preferences;
    }

    /**
     * Preferred label side pair for a run, derived from its endpoint world coordinates (the
     * order-preserving embedding keeps the dominant axis identical in schematic space).
     * Returns null for diagonal or degenerate runs, which keep the global preference order.
     */
    private static List<LabelSlot> dominantSidePair(RouteRun run, MetroTopology topology) {
        if (run.sequence().size() < 2) {
            return null;
        }
        SchematicNode first = topology.node(run.sequence().getFirst());
        SchematicNode last = topology.node(run.sequence().getLast());
        if (first == null || last == null) {
            return null;
        }
        double dx = Math.abs(last.worldX() - first.worldX());
        double dz = Math.abs(last.worldZ() - first.worldZ());
        if (dx >= dz * 1.5D) {
            return List.of(LabelSlot.ABOVE_NEAR, LabelSlot.BELOW_NEAR);
        }
        if (dz >= dx * 1.5D) {
            return List.of(LabelSlot.RIGHT_NEAR, LabelSlot.LEFT_NEAR);
        }
        return null;
    }

    /**
     * Full quality measurement, including the quadratic crossing recount. Only executed for the
     * winning profile; ranking inside the profile loop uses {@link #proxyQuality}. Node overlaps
     * are taken straight from the {@link #measureGlobalConstraints} count: it is exact at the
     * full minimum-distance threshold, and a tighter (0.72x) recount would only re-count pairs
     * already contained in that superset, so the two measurements are never summed.
     */
    private SchematicQualityReport quality(SchematicInputGraph input, MetroTopology topology, Map<NodeId, Vec2> positions, RouteOutput routes, List<VisualLabel> labels, ConstraintStats constraints, LayoutProfile profile, long startNanos, boolean timedOut, int iterations, boolean usedPrevious) {
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
        int lineTurns = 0;
        {
            Map<String, List<Vec2>> pathsByEdgeId = new HashMap<>();
            for (VisualEdgePath path : routes.edgePaths()) {
                pathsByEdgeId.put(path.edgeId(), path.points());
            }
            for (LineJoint joint : lineJoints(topology)) {
                if (jointTurnAmount(joint, pathsByEdgeId::get) > 0.5D) {
                    lineTurns++;
                }
            }
        }
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
                // Improvement rounds actually executed: this attempt's rip-up-and-reroute passes
                // plus the winner's annealing probes.
                iterations,
                profile.name(),
                constraints.nodeOverlaps(),
                crossings,
                labelOverlaps,
                averageDisplacement,
                maxDisplacement,
                routes.bendCount(),
                lineTurns,
                routes.fallbackEdges(),
                routes.corridorViolations(),
                constraints.edgeNodeConflicts(),
                routes.loopGlyphs(),
                routes.stationInternalEdges(),
                timedOut,
                usedPrevious);
    }

    private double qualityScore(SchematicQualityReport quality, Aabb2 bounds, LayoutProfile profile) {
        double width = Math.max(1.0D, width(bounds));
        double height = Math.max(1.0D, height(bounds));
        double aspect = width / height;
        double aspectPenalty = Math.abs(Math.log(aspect / profile.targetAspect())) * 900.0D;
        double oversizePenalty = Math.max(0.0D, height - width * 0.92D) * 0.7D;
        return quality.nodeOverlapCount() * 25_000.0D
                + quality.edgeCrossingCount() * 11_000.0D
                + quality.edgeNodeConflictCount() * 4_000.0D
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

    private static List<Vec2> orderedDirections(Vec2 preferred) {
        return DIRECTIONS.stream()
                .sorted(Comparator.comparingDouble((Vec2 direction) -> dot(direction, preferred)).reversed())
                .toList();
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

    /**
     * Three-point loop glyph bowing to one side of the direct connection. The default shoulder is
     * chosen by the edge id hash; {@code flip} selects the opposite shoulder so callers can score
     * both sides against the routed environment.
     */
    private static List<Vec2> loopPath(Vec2 from, Vec2 to, String id, LayoutProfile profile, boolean flip) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double length = Math.max(1.0D, Math.hypot(dx, dy));
        double nx = -dy / length;
        double ny = dx / length;
        if (((id.hashCode() & 1) == 0) != flip) {
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

    private List<LabelCandidate> labelCandidates(SchematicNode node, Vec2 position) {
        double radius = nodeObstacleRadius(node);
        // Real font measurement at the same scale the label will carry, so CJK names get boxes as
        // wide as the text the renderer draws. Placement and overlap measurement share the clamp.
        double width = LabelWidthMeasurer.clampWidth(this.labelWidthMeasurer.width(node.label(), labelScale(node)) + 8.0D);
        double height = 10.0D;
        // Gap tiers derive from the shared label-node gap (FullRouteMapConfig.LABEL_NODE_GAP_PX,
        // converted to layout blocks) so solver boxes and renderer slot projections agree on
        // what "next to the node" means.
        double gapBlocks = FullRouteMapConfig.LABEL_NODE_GAP_PX / FullRouteMapConfig.BASE_SCALE;
        double near = radius + gapBlocks;
        // Second, tighter tier for the near side above/below, so dense clusters can still squeeze
        // a label between the node and the first ring of obstacles.
        double close = radius + gapBlocks * 0.4D;
        // Last-resort tier further out on the sides, behind the distance penalty.
        double far = near * 1.5D;
        double diagonal = near * 0.76D;
        List<LabelCandidate> candidates = new ArrayList<>();
        // Deterministic preference order: sides, below/above near, below/above close, diagonals,
        // sides far. Ties keep the earliest candidate (strict < in the scoring loop). The slot
        // tags mirror LabelSlot's declaration order and are the renderer's placement contract.
        addLabelCandidate(candidates, position, position.x() + near, position.y() - height * 0.5D, width, height, LabelSlot.RIGHT_NEAR);
        addLabelCandidate(candidates, position, position.x() - near - width, position.y() - height * 0.5D, width, height, LabelSlot.LEFT_NEAR);
        addLabelCandidate(candidates, position, position.x() - width * 0.5D, position.y() + near, width, height, LabelSlot.BELOW_NEAR);
        addLabelCandidate(candidates, position, position.x() - width * 0.5D, position.y() - near - height, width, height, LabelSlot.ABOVE_NEAR);
        addLabelCandidate(candidates, position, position.x() - width * 0.5D, position.y() + close, width, height, LabelSlot.BELOW_CLOSE);
        addLabelCandidate(candidates, position, position.x() - width * 0.5D, position.y() - close - height, width, height, LabelSlot.ABOVE_CLOSE);
        addLabelCandidate(candidates, position, position.x() + diagonal, position.y() + diagonal, width, height, LabelSlot.DIAGONAL_DOWN_RIGHT);
        addLabelCandidate(candidates, position, position.x() - diagonal - width, position.y() + diagonal, width, height, LabelSlot.DIAGONAL_DOWN_LEFT);
        addLabelCandidate(candidates, position, position.x() + diagonal, position.y() - diagonal - height, width, height, LabelSlot.DIAGONAL_UP_RIGHT);
        addLabelCandidate(candidates, position, position.x() - diagonal - width, position.y() - diagonal - height, width, height, LabelSlot.DIAGONAL_UP_LEFT);
        addLabelCandidate(candidates, position, position.x() + far, position.y() - height * 0.5D, width, height, LabelSlot.RIGHT_FAR);
        addLabelCandidate(candidates, position, position.x() - far - width, position.y() - height * 0.5D, width, height, LabelSlot.LEFT_FAR);
        return candidates;
    }

    private static void addLabelCandidate(List<LabelCandidate> candidates, Vec2 node, double x, double y, double width, double height, LabelSlot slot) {
        Vec2 center = new Vec2(x + width * 0.5D, y + height * 0.5D);
        candidates.add(new LabelCandidate(x, y, new LabelBox(x, y, x + width, y + height, 0), center.distanceTo(node), slot));
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
        // After coordinate snapping, genuinely straight station pairs compare exact, so a
        // tight threshold (~0.81 degrees) keeps tilted segments from passing as straights.
        return dot > 0.9999D;
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

    private static boolean sharesEndpoint(SchematicEdge first, SchematicEdge second) {
        return first.from().equals(second.from()) || first.from().equals(second.to()) || first.to().equals(second.from()) || first.to().equals(second.to());
    }

    private int countLabelOverlaps(List<VisualLabel> labels) {
        int count = 0;
        for (int i = 0; i < labels.size(); i++) {
            LabelBox first = this.labelBox(labels.get(i));
            for (int j = i + 1; j < labels.size(); j++) {
                if (first.intersects(this.labelBox(labels.get(j)))) {
                    count++;
                }
            }
        }
        return count;
    }

    private LabelBox labelBox(VisualLabel label) {
        // Same measurer, padding, and clamp as labelCandidates, so the overlap metric counts
        // exactly the boxes the placement stage negotiated.
        double width = LabelWidthMeasurer.clampWidth(this.labelWidthMeasurer.width(label.text(), label.scale()) + 8.0D);
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

    private record LayoutProfile(String name, double stationSpacing, double targetAspect) {
        double boundarySpacing() {
            return this.stationSpacing * 0.42D;
        }

        double nodeGap() {
            return this.stationSpacing * 0.16D;
        }

        double routeSeparation() {
            return this.stationSpacing * 0.12D;
        }
    }

    private record LayoutAttempt(LayoutProfile profile, Map<NodeId, Vec2> positions, RouteOutput routeOutput, List<VisualLabel> labels, SchematicQualityReport quality, double score, int iterations, boolean usedPrevious) {}

    private record RouteOutput(List<VisualEdgePath> edgePaths, int fallbackEdges, int bendCount, int loopGlyphs, int stationInternalEdges, int crossingCount, boolean timedOut, int corridorViolations, int unroutedEdges) {}

    /** Mutable per-edge routing state shared by the rip-up-and-reroute and annealing stages. */
    private record EdgeRouteState(SchematicEdge edge, List<Vec2> points, boolean fallback, boolean loopGlyph) {}

    private record RerouteResult(RouteOutput output, int rounds) {}

    private record AnnealOutcome(Map<NodeId, Vec2> positions, RouteOutput routeOutput, int rounds, boolean changed) {}

    private record CompactionOutcome(Map<NodeId, Vec2> positions, RouteOutput routeOutput, int rounds, boolean changed) {}

    /** Assigned lane of one corridor member: normal offset from the corridor centre line. */
    private record CorridorLane(int groupId, double offset, Vec2 axis, double step) {}

    /**
     * Lane assignment for one layout pass: groups of near-parallel {@code PARALLEL_CORRIDOR}
     * edges, each member's lane offset symmetric about the corridor centre line. Lookup-only
     * maps; every query is deterministic.
     */
    private record CorridorPlan(Map<String, Integer> groupByEdgeId, Map<String, CorridorLane> laneByEdgeId, Map<Integer, Double> laneStepByGroup) {
        static CorridorPlan empty() {
            return new CorridorPlan(Map.of(), Map.of(), Map.of());
        }

        CorridorLane lane(String edgeId) {
            return this.laneByEdgeId.get(edgeId);
        }

        int groupOf(String edgeId) {
            return this.groupByEdgeId.getOrDefault(edgeId, -1);
        }

        /** Group id when both edges belong to the same corridor group, otherwise -1. */
        int sharedGroup(SchematicEdge first, SchematicEdge second) {
            int group = this.groupOf(first.id());
            return group >= 0 && group == this.groupOf(second.id()) ? group : -1;
        }

        double laneStep(int groupId) {
            return this.laneStepByGroup.getOrDefault(groupId, 0.0D);
        }

        /** Query padding wide enough to see same-corridor partners a full lane step away. */
        double queryPad(String edgeId, double defaultPad) {
            CorridorLane lane = this.lane(edgeId);
            return lane == null ? defaultPad : Math.max(defaultPad, lane.step());
        }
    }

    private record RouteScoreEvaluation(double score, boolean clean) {}

    private record GridCell(int x, int z) {
        static GridCell of(double x, double z, double cellSize) {
            return new GridCell((int) Math.floor(x / cellSize), (int) Math.floor(z / cellSize));
        }
    }

    /**
     * Per-profile routing state: uniform-grid spatial indices over node positions and already
     * routed segments, plus generation-stamped scratch arrays so per-candidate queries allocate
     * nothing. Buckets are filled in a deterministic order and only ever read through point
     * lookups (never map iteration); query results are replayed in ascending insertion index, so
     * every candidate is scored against exactly the same elements in exactly the same order as
     * the previous full scans.
     *
     * <p>Entries can be tombstoned ({@link #setLive}) and re-appended ({@link #insert}) by the
     * rip-up-and-reroute and annealing stages; tombstoned entries keep their bucket slots but are
     * skipped during replay, which keeps every query result identical to a full rebuild over the
     * live paths only.</p>
     */
    private static final class RoutingContext {
        private static final double MAX_NODE_CLEARANCE = 23.0D + 5.0D;
        private final MetroTopology topology;
        private final LayoutProfile profile;
        private final double cellSize;
        private final List<NodeId> nodeIds;
        private final List<Vec2> nodePositions;
        private final Map<GridCell, List<Integer>> nodeBuckets = new HashMap<>();
        private final List<List<Vec2>> routedPaths = new ArrayList<>();
        private final List<SchematicEdge> routedEdges = new ArrayList<>();
        private final Map<GridCell, List<Integer>> segmentBuckets = new HashMap<>();
        private final int[] nodeMarks;
        private final int[] nodeScratch;
        private int[] pathMarks;
        private int[] pathScratch;
        private boolean[] live;
        private int markGeneration;

        private RoutingContext(MetroTopology topology, Map<NodeId, Vec2> positions, LayoutProfile profile) {
            this.topology = topology;
            this.profile = profile;
            this.cellSize = profile.stationSpacing();
            this.nodeIds = List.copyOf(positions.keySet());
            this.nodePositions = List.copyOf(positions.values());
            for (int i = 0; i < this.nodeIds.size(); i++) {
                Vec2 position = this.nodePositions.get(i);
                this.nodeBuckets.computeIfAbsent(GridCell.of(position.x(), position.y(), this.cellSize), ignored -> new ArrayList<>()).add(i);
            }
            this.nodeMarks = new int[this.nodeIds.size()];
            this.nodeScratch = new int[this.nodeIds.size()];
            int initialPathCapacity = Math.max(16, topology.edges().size());
            this.pathMarks = new int[initialPathCapacity];
            this.pathScratch = new int[initialPathCapacity];
            this.live = new boolean[initialPathCapacity];
        }

        private LayoutProfile profile() {
            return this.profile;
        }

        private MetroTopology topology() {
            return this.topology;
        }

        private NodeId nodeId(int index) {
            return this.nodeIds.get(index);
        }

        private Vec2 position(int index) {
            return this.nodePositions.get(index);
        }

        private List<Vec2> routedPath(int index) {
            return this.routedPaths.get(index);
        }

        private SchematicEdge routedEdge(int index) {
            return this.routedEdges.get(index);
        }

        /** Total entries ever appended, live or tombstoned; drives annealing's compaction guard. */
        private int pathEntryCount() {
            return this.routedPaths.size();
        }

        /**
         * Marks a routed entry in or out of the live set. Tombstoned entries stay in the segment
         * buckets but are skipped by every query replay, so scoring and crossing counts behave
         * exactly as if the entry had never been inserted.
         */
        private void setLive(int index, boolean isLive) {
            this.live[index] = isLive;
        }

        /**
         * Stamps every node that can lie within the maximum obstacle clearance of the given
         * polyline; the stamped indices are replayed through {@link #nodeScratch} in ascending
         * order. Nodes outside the inflated AABB provably contribute zero penalty.
         */
        private int collectNodesNear(List<Vec2> path) {
            this.markGeneration++;
            Aabb2 bounds = boundsForPoints(path).inflate(MAX_NODE_CLEARANCE);
            int minCellX = (int) Math.floor(bounds.minX() / this.cellSize);
            int maxCellX = (int) Math.floor(bounds.maxX() / this.cellSize);
            int minCellZ = (int) Math.floor(bounds.minY() / this.cellSize);
            int maxCellZ = (int) Math.floor(bounds.maxY() / this.cellSize);
            for (int cx = minCellX; cx <= maxCellX; cx++) {
                for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                    for (int index : this.nodeBuckets.getOrDefault(new GridCell(cx, cz), List.of())) {
                        this.nodeMarks[index] = this.markGeneration;
                    }
                }
            }
            int count = 0;
            for (int i = 0; i < this.nodeIds.size(); i++) {
                if (this.nodeMarks[i] == this.markGeneration) {
                    this.nodeScratch[count++] = i;
                }
            }
            return count;
        }

        /**
         * Stamps every previously routed path that owns at least one segment within route
         * separation distance of the given polyline. Segments are bucketed by their uninflated
         * AABB and queried with the separation-inflated AABB, so no contributing pair is missed;
         * the stamped path indices are replayed through {@link #pathScratch} in ascending order.
         */
        private int collectPathsNear(List<Vec2> path) {
            return this.collectPathsNear(path, this.profile.routeSeparation());
        }

        /**
         * Same as {@link #collectPathsNear(List)} but with an explicit query padding, used when a
         * corridor lane step wider than the generic route separation must still see its partners.
         * Tombstoned entries are stamped but never replayed.
         */
        private int collectPathsNear(List<Vec2> path, double pad) {
            this.markGeneration++;
            for (int i = 0; i + 1 < path.size(); i++) {
                Vec2 a = path.get(i);
                Vec2 b = path.get(i + 1);
                int minCellX = (int) Math.floor((Math.min(a.x(), b.x()) - pad) / this.cellSize);
                int maxCellX = (int) Math.floor((Math.max(a.x(), b.x()) + pad) / this.cellSize);
                int minCellZ = (int) Math.floor((Math.min(a.y(), b.y()) - pad) / this.cellSize);
                int maxCellZ = (int) Math.floor((Math.max(a.y(), b.y()) + pad) / this.cellSize);
                for (int cx = minCellX; cx <= maxCellX; cx++) {
                    for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                        for (int pathIndex : this.segmentBuckets.getOrDefault(new GridCell(cx, cz), List.of())) {
                            this.pathMarks[pathIndex] = this.markGeneration;
                        }
                    }
                }
            }
            int count = 0;
            for (int i = 0; i < this.routedPaths.size(); i++) {
                if (this.pathMarks[i] == this.markGeneration && this.live[i]) {
                    this.pathScratch[count++] = i;
                }
            }
            return count;
        }

        /**
         * Exact crossing count of a freshly routed path against previously routed paths, using
         * the same endpoint-sharing exclusion as the full quality measurement, so the proxy
         * crossing count matches the exact recount pair-for-pair.
         */
        private int countCrossings(List<Vec2> path, SchematicEdge edge) {
            int nearby = this.collectPathsNear(path);
            int crossings = 0;
            for (int k = 0; k < nearby; k++) {
                int pathIndex = this.pathScratch[k];
                if (sharesEndpoint(edge, this.routedEdges.get(pathIndex))) {
                    continue;
                }
                if (polylinesIntersect(path, this.routedPaths.get(pathIndex))) {
                    crossings++;
                }
            }
            return crossings;
        }

        /**
         * Appends a live routed path and returns its routing index. The scratch arrays grow on
         * demand: rip-up-and-reroute and annealing append beyond the initial edge count.
         */
        private int insert(List<Vec2> points, SchematicEdge edge) {
            int pathIndex = this.routedPaths.size();
            if (pathIndex >= this.pathMarks.length) {
                int grown = this.pathMarks.length * 2;
                this.pathMarks = Arrays.copyOf(this.pathMarks, grown);
                this.pathScratch = Arrays.copyOf(this.pathScratch, grown);
                this.live = Arrays.copyOf(this.live, grown);
            }
            this.routedPaths.add(points);
            this.routedEdges.add(edge);
            this.live[pathIndex] = true;
            for (int i = 0; i + 1 < points.size(); i++) {
                Vec2 a = points.get(i);
                Vec2 b = points.get(i + 1);
                int minCellX = (int) Math.floor(Math.min(a.x(), b.x()) / this.cellSize);
                int maxCellX = (int) Math.floor(Math.max(a.x(), b.x()) / this.cellSize);
                int minCellZ = (int) Math.floor(Math.min(a.y(), b.y()) / this.cellSize);
                int maxCellZ = (int) Math.floor(Math.max(a.y(), b.y()) / this.cellSize);
                for (int cx = minCellX; cx <= maxCellX; cx++) {
                    for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                        this.segmentBuckets.computeIfAbsent(new GridCell(cx, cz), ignored -> new ArrayList<>()).add(pathIndex);
                    }
                }
            }
            return pathIndex;
        }
    }

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

    private record LabelCandidate(double x, double y, LabelBox box, double distanceFromNode, LabelSlot slot) {}

    private record LineJoint(NodeId node, SchematicEdge incoming, SchematicEdge outgoing) {}

    /** Mutable per-label placement state shared by the greedy pass and the improvement sweeps. */
    private static final class LabelWork {
        private final NodeId nodeId;
        private final SchematicNode node;
        private final List<LabelCandidate> candidates;
        private LabelCandidate chosen;
        private boolean penalized;

        private LabelWork(NodeId nodeId, SchematicNode node, List<LabelCandidate> candidates, LabelCandidate chosen, boolean penalized) {
            this.nodeId = nodeId;
            this.node = node;
            this.candidates = candidates;
            this.chosen = chosen;
            this.penalized = penalized;
        }

        private NodeId nodeId() {
            return this.nodeId;
        }

        private SchematicNode node() {
            return this.node;
        }

        private List<LabelCandidate> candidates() {
            return this.candidates;
        }

        private LabelCandidate chosen() {
            return this.chosen;
        }

        private void chosen(LabelCandidate chosen) {
            this.chosen = chosen;
        }

        private boolean penalized() {
            return this.penalized;
        }

        private void penalized(boolean penalized) {
            this.penalized = penalized;
        }
    }

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
    }

    private record MetroTopology(Map<NodeId, SchematicNode> nodesById, List<SchematicEdge> edges, List<RouteRun> runs, Map<NodeId, List<NodeId>> adjacency, Set<String> connectedPairs, Map<NodeId, Integer> degree) {
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
            return new MetroTopology(nodes, usableEdges, runs, adjacency, connectedPairs, degree);
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
