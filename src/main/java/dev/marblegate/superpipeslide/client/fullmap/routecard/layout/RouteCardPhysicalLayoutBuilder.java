package dev.marblegate.superpipeslide.client.fullmap.routecard.layout;

import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
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
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class RouteCardPhysicalLayoutBuilder {
    private static final double SAMPLE_STEP_BLOCKS = 2.0D;
    private static final int MAX_CONNECTION_SAMPLES = 48;
    private static final double SIMPLIFY_EPSILON_BLOCKS = 0.12D;
    private static final double PHYSICAL_SEGMENT_MERGE_GAP_BLOCKS = 6.0D;

    public RouteCardVisualGraph build(RouteCardSemanticGraph graph) {
        if (graph.nodes().isEmpty()) {
            return new RouteCardVisualGraph(List.of(), List.of(), List.of(), List.of(), Aabb2.empty(), false);
        }
        Map<RouteCardNodeId, Vec2> positions = new LinkedHashMap<>();
        for (RouteCardNode node : graph.nodes()) {
            positions.put(node.id(), new Vec2(node.worldX(), node.worldZ()));
        }

        List<RouteCardVisualEdge> visualEdges = new ArrayList<>();
        Map<String, RouteCardVisualEdge> edgeById = new HashMap<>();
        boolean fallback = false;
        for (RouteCardEdge edge : graph.edges()) {
            List<Vec2> points = this.pointsFor(edge, positions);
            if (points.size() < 2) {
                fallback = true;
                points = fallbackPoints(edge, positions);
            }
            RouteCardVisualEdge visualEdge = new RouteCardVisualEdge(edge, points, boundsFor(points).inflate(8.0D));
            visualEdges.add(visualEdge);
            edgeById.put(edge.id(), visualEdge);
        }

        List<RouteCardVisualNode> visualNodes = graph.nodes().stream()
                .map(node -> new RouteCardVisualNode(node, positions.getOrDefault(node.id(), new Vec2(node.worldX(), node.worldZ())), nodePriority(node)))
                .toList();

        List<RouteCardVisualSegment> rawSegments = new ArrayList<>();
        for (RouteCardSegment segment : graph.segments()) {
            Aabb2 bounds = Aabb2.empty();
            for (RouteCardNodeId nodeId : segment.nodeIds()) {
                Vec2 point = positions.get(nodeId);
                if (point != null) {
                    RouteCardNode node = graph.nodes().stream().filter(value -> value.id().equals(nodeId)).findFirst().orElse(null);
                    double radius = node == null || node.kind() == RouteCardNodeKind.STATION ? 12.0D : 10.0D;
                    bounds = bounds.include(Aabb2.around(point.x(), point.y(), radius));
                }
            }
            for (String edgeId : segment.edgeIds()) {
                RouteCardVisualEdge edge = edgeById.get(edgeId);
                if (edge != null) {
                    bounds = bounds.include(edge.bounds());
                }
            }
            if (!bounds.isEmpty()) {
                rawSegments.add(new RouteCardVisualSegment(segment, bounds.inflate(24.0D)));
            }
        }
        List<RouteCardVisualSegment> visualSegments = mergePhysicalSegments(rawSegments);

        Aabb2 graphBounds = Aabb2.empty();
        for (RouteCardVisualNode node : visualNodes) {
            graphBounds = graphBounds.include(Aabb2.around(node.position().x(), node.position().y(), 16.0D));
        }
        for (RouteCardVisualEdge edge : visualEdges) {
            graphBounds = graphBounds.include(edge.bounds());
        }
        for (RouteCardVisualSegment segment : visualSegments) {
            graphBounds = graphBounds.include(segment.bounds());
        }
        return new RouteCardVisualGraph(visualNodes, visualEdges, visualSegments, List.of(), graphBounds, fallback);
    }

    private static List<RouteCardVisualSegment> mergePhysicalSegments(List<RouteCardVisualSegment> segments) {
        if (segments.size() <= 1) {
            return segments;
        }
        List<PhysicalSegmentGroup> groups = segments.stream()
                .map(PhysicalSegmentGroup::from)
                .sorted(Comparator.comparingInt(PhysicalSegmentGroup::index))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        boolean changed;
        do {
            changed = false;
            outer:
            for (int i = 0; i < groups.size(); i++) {
                for (int j = i + 1; j < groups.size(); j++) {
                    PhysicalSegmentGroup first = groups.get(i);
                    PhysicalSegmentGroup second = groups.get(j);
                    if (shouldMergePhysicalSegments(first, second)) {
                        groups.set(i, first.merge(second));
                        groups.remove(j);
                        changed = true;
                        break outer;
                    }
                }
            }
        } while (changed);
        return groups.stream()
                .sorted(Comparator.comparingInt(PhysicalSegmentGroup::index))
                .map(PhysicalSegmentGroup::toVisualSegment)
                .toList();
    }

    private static boolean shouldMergePhysicalSegments(PhysicalSegmentGroup first, PhysicalSegmentGroup second) {
        if (!first.levelKey().equals(second.levelKey())) {
            return false;
        }
        if (first.sharesTopologyWith(second)) {
            return true;
        }
        return aabbGap(first.bounds(), second.bounds()) <= PHYSICAL_SEGMENT_MERGE_GAP_BLOCKS;
    }

    private static double aabbGap(Aabb2 first, Aabb2 second) {
        if (first.intersects(second)) {
            return 0.0D;
        }
        double dx = Math.max(0.0D, Math.max(second.minX() - first.maxX(), first.minX() - second.maxX()));
        double dy = Math.max(0.0D, Math.max(second.minY() - first.maxY(), first.minY() - second.maxY()));
        return Math.hypot(dx, dy);
    }

    private List<Vec2> pointsFor(RouteCardEdge edge, Map<RouteCardNodeId, Vec2> positions) {
        if (edge.backingPathSlice().isEmpty()) {
            return fallbackPoints(edge, positions);
        }
        Vec2 from = positions.get(edge.from());
        Vec2 to = positions.get(edge.to());
        List<Vec2> result = new ArrayList<>();
        Vec2 previousEnd = null;
        for (PipeConnectionRef ref : edge.backingPathSlice()) {
            PipeConnection connection = ClientPipeNetworkCache.connection(ref).orElse(null);
            if (connection == null) {
                continue;
            }
            List<Vec2> samples = sampleConnection(connection);
            if (samples.size() < 2) {
                continue;
            }
            boolean reverse;
            if (previousEnd == null) {
                reverse = from != null && samples.getLast().distanceTo(from) < samples.getFirst().distanceTo(from);
            } else {
                reverse = samples.getLast().distanceTo(previousEnd) < samples.getFirst().distanceTo(previousEnd);
            }
            if (reverse) {
                samples = reversed(samples);
            }
            appendPolyline(result, samples);
            previousEnd = result.isEmpty() ? null : result.getLast();
        }
        if (result.size() < 2) {
            return fallbackPoints(edge, positions);
        }
        if (from != null && result.getFirst().distanceTo(from) > 6.0D) {
            result.addFirst(from);
        }
        if (to != null && result.getLast().distanceTo(to) > 6.0D) {
            result.add(to);
        }
        return simplify(result, SIMPLIFY_EPSILON_BLOCKS);
    }

    private static List<Vec2> fallbackPoints(RouteCardEdge edge, Map<RouteCardNodeId, Vec2> positions) {
        Vec2 from = positions.get(edge.from());
        Vec2 to = positions.get(edge.to());
        if (from == null || to == null) {
            return List.of();
        }
        return List.of(from, to);
    }

    private static List<Vec2> sampleConnection(PipeConnection connection) {
        double length = connection.length();
        int samples = Math.max(2, Math.min(MAX_CONNECTION_SAMPLES, (int) Math.ceil(length / SAMPLE_STEP_BLOCKS) + 1));
        List<Vec2> result = new ArrayList<>(samples + 1);
        for (int i = 0; i <= samples; i++) {
            Vec3 point = connection.positionAt(length * i / samples);
            result.add(new Vec2(point.x, point.z));
        }
        return result;
    }

    private static void appendPolyline(List<Vec2> target, List<Vec2> source) {
        for (Vec2 point : source) {
            if (!target.isEmpty() && target.getLast().distanceTo(point) < 0.05D) {
                continue;
            }
            target.add(point);
        }
    }

    private static List<Vec2> reversed(List<Vec2> values) {
        List<Vec2> result = new ArrayList<>(values);
        java.util.Collections.reverse(result);
        return result;
    }

    private static List<Vec2> simplify(List<Vec2> points, double epsilon) {
        if (points.size() <= 2 || epsilon <= 0.0D) {
            return points;
        }
        boolean[] keep = new boolean[points.size()];
        keep[0] = true;
        keep[points.size() - 1] = true;
        simplifyRange(points, keep, 0, points.size() - 1, epsilon);
        List<Vec2> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (keep[i]) {
                result.add(points.get(i));
            }
        }
        return result.size() < 2 ? points : result;
    }

    private static void simplifyRange(List<Vec2> points, boolean[] keep, int start, int end, double epsilon) {
        if (end <= start + 1) {
            return;
        }
        Vec2 a = points.get(start);
        Vec2 b = points.get(end);
        double bestDistance = -1.0D;
        int bestIndex = -1;
        for (int i = start + 1; i < end; i++) {
            double distance = distanceToSegment(points.get(i), a, b);
            if (distance > bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        if (bestDistance > epsilon && bestIndex >= 0) {
            keep[bestIndex] = true;
            simplifyRange(points, keep, start, bestIndex, epsilon);
            simplifyRange(points, keep, bestIndex, end, epsilon);
        }
    }

    private static double distanceToSegment(Vec2 p, Vec2 a, Vec2 b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double lengthSqr = dx * dx + dy * dy;
        if (lengthSqr <= 1.0E-9D) {
            return p.distanceTo(a);
        }
        double t = Math.max(0.0D, Math.min(1.0D, ((p.x() - a.x()) * dx + (p.y() - a.y()) * dy) / lengthSqr));
        return p.distanceTo(new Vec2(a.x() + dx * t, a.y() + dy * t));
    }

    private static Aabb2 boundsFor(List<Vec2> points) {
        Aabb2 bounds = Aabb2.empty();
        for (Vec2 point : points) {
            bounds = bounds.include(point.x(), point.y());
        }
        return bounds;
    }

    private static int nodePriority(RouteCardNode node) {
        return switch (node.kind()) {
            case STATION -> 680;
            case PORTAL_BOUNDARY -> 755;
            case FOLD_BOUNDARY -> 760;
            case MISSING_PATH_BOUNDARY -> 720;
        } + Math.max(0, 100 - node.layoutOccurrence());
    }

    private record PhysicalSegmentGroup(
            int index,
            ResourceKey<Level> levelKey,
            Set<RouteCardNodeId> nodeIds,
            Set<String> edgeIds,
            Aabb2 bounds) {
        private static PhysicalSegmentGroup from(RouteCardVisualSegment visualSegment) {
            RouteCardSegment segment = visualSegment.segment();
            return new PhysicalSegmentGroup(
                    segment.index(),
                    segment.levelKey(),
                    new LinkedHashSet<>(segment.nodeIds()),
                    new LinkedHashSet<>(segment.edgeIds()),
                    visualSegment.bounds());
        }

        private PhysicalSegmentGroup merge(PhysicalSegmentGroup other) {
            LinkedHashSet<RouteCardNodeId> mergedNodes = new LinkedHashSet<>(this.nodeIds);
            mergedNodes.addAll(other.nodeIds);
            LinkedHashSet<String> mergedEdges = new LinkedHashSet<>(this.edgeIds);
            mergedEdges.addAll(other.edgeIds);
            return new PhysicalSegmentGroup(
                    Math.min(this.index, other.index),
                    this.levelKey,
                    mergedNodes,
                    mergedEdges,
                    this.bounds.include(other.bounds));
        }

        private RouteCardVisualSegment toVisualSegment() {
            RouteCardSegment segment = new RouteCardSegment(
                    "route-card-physical-segment:" + this.levelKey.identifier() + ":" + this.index,
                    this.index,
                    this.levelKey,
                    this.nodeIds.stream().toList(),
                    this.edgeIds.stream().toList());
            return new RouteCardVisualSegment(segment, this.bounds);
        }

        private boolean sharesTopologyWith(PhysicalSegmentGroup other) {
            return this.nodeIds.stream().anyMatch(other.nodeIds::contains)
                    || this.edgeIds.stream().anyMatch(other.edgeIds::contains);
        }
    }
}
