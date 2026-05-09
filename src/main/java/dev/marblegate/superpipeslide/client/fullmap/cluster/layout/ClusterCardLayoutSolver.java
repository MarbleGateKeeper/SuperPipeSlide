package dev.marblegate.superpipeslide.client.fullmap.cluster.layout;

import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardEdge;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardEdgeKind;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardNode;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardNodeKind;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardProfile;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardSemanticGraph;
import dev.marblegate.superpipeslide.client.fullmap.cluster.visual.ClusterCardVisualEdge;
import dev.marblegate.superpipeslide.client.fullmap.cluster.visual.ClusterCardVisualGraph;
import dev.marblegate.superpipeslide.client.fullmap.cluster.visual.ClusterCardVisualNode;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.model.MapNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClusterCardLayoutSolver {
    private static final double MEMBER_MIN_GAP = 42.0D;
    private static final double MEMBER_PADDING = 14.0D;
    private static final double DEEP_MEMBER_MIN_GAP = 34.0D;
    private static final double DEEP_MEMBER_PADDING = 10.0D;
    private static final double EXTERNAL_PORT_GAP = 28.0D;
    private static final double EXTERNAL_PORT_SPACING = 14.0D;
    private static final double EXTERNAL_PORT_BOUNDS_INFLATE = 22.0D;
    private static final double DEEP_EXTERNAL_PORT_GAP = 24.0D;
    private static final double DEEP_EXTERNAL_PORT_SPACING = 11.0D;
    private static final double DEEP_EXTERNAL_PORT_BOUNDS_INFLATE = 18.0D;
    private static final double MAX_LOCAL_SPREAD_SCALE = 5.0D;
    private static final double DEEP_MAX_LOCAL_SPREAD_SCALE = 7.0D;

    public ClusterCardVisualGraph solve(ClusterCardSemanticGraph graph) {
        ClusterCardProfile profile = graph.profile();
        List<ClusterCardNode> members = graph.nodes().stream()
                .filter(ClusterCardNode::member)
                .toList();
        Map<String, Vec2> positions = new LinkedHashMap<>();
        Map<String, Vec2> anchors = new HashMap<>();
        if (members.isEmpty()) {
            return new ClusterCardVisualGraph(List.of(), List.of(), Aabb2.around(0.0D, 0.0D, 1.0D), true);
        }

        double memberMinGap = memberMinGap(profile);
        if (usesVerticalFallback(members, profile)) {
            List<ClusterCardNode> sorted = members.stream()
                    .sorted(Comparator.comparingDouble(ClusterCardNode::worldY).reversed().thenComparing(ClusterCardNode::label).thenComparing(ClusterCardNode::id))
                    .toList();
            double center = (sorted.size() - 1) * 0.5D;
            for (int i = 0; i < sorted.size(); i++) {
                Vec2 point = new Vec2(0.0D, (i - center) * memberMinGap);
                positions.put(sorted.get(i).id(), point);
                anchors.put(sorted.get(i).id(), point);
            }
        } else {
            double meanX = members.stream().mapToDouble(ClusterCardNode::worldX).average().orElse(0.0D);
            double meanZ = members.stream().mapToDouble(ClusterCardNode::worldZ).average().orElse(0.0D);
            double minDistance = minMemberDistance(members);
            double scale = minDistance == Double.POSITIVE_INFINITY || minDistance <= 0.001D
                    ? 1.0D
                    : Math.min(maxLocalSpreadScale(profile), Math.max(1.0D, memberMinGap / minDistance));
            for (ClusterCardNode node : members) {
                Vec2 point = new Vec2((node.worldX() - meanX) * scale, (node.worldZ() - meanZ) * scale);
                positions.put(node.id(), point);
                anchors.put(node.id(), point);
            }
        }

        relaxMemberCollisions(members, positions, anchors, profile);
        placeExternalPorts(graph, positions);

        List<ClusterCardVisualNode> visualNodes = graph.nodes().stream()
                .map(node -> new ClusterCardVisualNode(node, positions.getOrDefault(node.id(), new Vec2(0.0D, 0.0D)), priority(node)))
                .toList();
        List<ClusterCardVisualEdge> visualEdges = new ArrayList<>();
        for (ClusterCardEdge edge : graph.edges()) {
            Vec2 from = positions.get(edge.from());
            Vec2 to = positions.get(edge.to());
            if (from == null || to == null || from.distanceTo(to) < 0.01D) {
                continue;
            }
            List<Vec2> points = routeEdge(edge, from, to);
            visualEdges.add(new ClusterCardVisualEdge(edge, points, boundsFor(points).inflate(12.0D)));
        }

        Aabb2 bounds = Aabb2.empty();
        for (ClusterCardVisualNode node : visualNodes) {
            if (!node.node().member()) {
                continue;
            }
            double radius = collisionRadius(node.node(), profile);
            bounds = bounds.include(Aabb2.around(node.position().x(), node.position().y(), radius + 24.0D));
        }
        for (ClusterCardVisualEdge edge : visualEdges) {
            if (edge.edge().kind() != ClusterCardEdgeKind.EXTERNAL_ROUTE) {
                bounds = bounds.include(edge.bounds());
            }
        }
        if (bounds.isEmpty()) {
            bounds = Aabb2.around(0.0D, 0.0D, 1.0D);
        }
        return new ClusterCardVisualGraph(visualNodes, visualEdges, bounds, false);
    }

    private static boolean usesVerticalFallback(List<ClusterCardNode> members, ClusterCardProfile profile) {
        if (profile == ClusterCardProfile.DEEP) {
            return false;
        }
        if (members.size() <= 1) {
            return false;
        }
        double meanX = members.stream().mapToDouble(ClusterCardNode::worldX).average().orElse(0.0D);
        double meanZ = members.stream().mapToDouble(ClusterCardNode::worldZ).average().orElse(0.0D);
        double variance = members.stream()
                .mapToDouble(node -> square(node.worldX() - meanX) + square(node.worldZ() - meanZ))
                .average()
                .orElse(0.0D);
        return variance < FullRouteMapConfig.CLUSTER_CARD_SPREAD_FLAT_THRESHOLD;
    }

    private static double minMemberDistance(List<ClusterCardNode> members) {
        double min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                min = Math.min(min, Math.hypot(members.get(i).worldX() - members.get(j).worldX(), members.get(i).worldZ() - members.get(j).worldZ()));
            }
        }
        return min;
    }

    private static void relaxMemberCollisions(List<ClusterCardNode> members, Map<String, Vec2> positions, Map<String, Vec2> anchors, ClusterCardProfile profile) {
        for (int pass = 0; pass < 80; pass++) {
            boolean moved = false;
            Map<String, Vec2> delta = new HashMap<>();
            for (int i = 0; i < members.size(); i++) {
                for (int j = i + 1; j < members.size(); j++) {
                    ClusterCardNode a = members.get(i);
                    ClusterCardNode b = members.get(j);
                    Vec2 pa = positions.get(a.id());
                    Vec2 pb = positions.get(b.id());
                    if (pa == null || pb == null) {
                        continue;
                    }
                    double min = collisionRadius(a, profile) + collisionRadius(b, profile) + memberPadding(profile);
                    double dx = pb.x() - pa.x();
                    double dy = pb.y() - pa.y();
                    double distance = Math.hypot(dx, dy);
                    if (distance >= min) {
                        continue;
                    }
                    if (distance < 0.001D) {
                        double angle = hashAngle(a.id(), b.id());
                        dx = Math.cos(angle);
                        dy = Math.sin(angle);
                        distance = 1.0D;
                    }
                    double push = (min - distance) * 0.52D;
                    double ux = dx / distance;
                    double uy = dy / distance;
                    addDelta(delta, a.id(), -ux * push, -uy * push);
                    addDelta(delta, b.id(), ux * push, uy * push);
                    moved = true;
                }
            }
            for (ClusterCardNode node : members) {
                Vec2 current = positions.get(node.id());
                Vec2 anchor = anchors.get(node.id());
                Vec2 push = delta.getOrDefault(node.id(), new Vec2(0.0D, 0.0D));
                if (current == null || anchor == null) {
                    continue;
                }
                double pull = node.kind() == ClusterCardNodeKind.MEMBER_FOLD_ANCHOR ? 0.055D : (profile == ClusterCardProfile.DEEP ? 0.045D : 0.035D);
                Vec2 next = new Vec2(
                        current.x() + push.x() + (anchor.x() - current.x()) * pull,
                        current.y() + push.y() + (anchor.y() - current.y()) * pull
                );
                positions.put(node.id(), next);
            }
            if (!moved) {
                break;
            }
        }
    }

    private static void addDelta(Map<String, Vec2> delta, String key, double dx, double dy) {
        Vec2 current = delta.getOrDefault(key, new Vec2(0.0D, 0.0D));
        delta.put(key, new Vec2(current.x() + dx, current.y() + dy));
    }

    private static void placeExternalPorts(ClusterCardSemanticGraph graph, Map<String, Vec2> positions) {
        ClusterCardProfile profile = graph.profile();
        List<ClusterCardNode> ports = graph.nodes().stream()
                .filter(node -> node.kind() == ClusterCardNodeKind.EXTERNAL_PORT)
                .toList();
        if (ports.isEmpty()) {
            return;
        }
        Aabb2 memberBounds = Aabb2.empty();
        for (ClusterCardNode node : graph.nodes()) {
            if (!node.member()) {
                continue;
            }
            Vec2 position = positions.get(node.id());
            if (position != null) {
                memberBounds = memberBounds.include(position.x(), position.y());
            }
        }
        if (memberBounds.isEmpty()) {
            memberBounds = Aabb2.around(0.0D, 0.0D, 1.0D);
        }
        Aabb2 placementBounds = memberBounds.inflate(externalPortBoundsInflate(profile));
        Map<Integer, List<PortPlacement>> byDirection = new LinkedHashMap<>();
        for (ClusterCardNode port : ports) {
            ClusterCardEdge edge = graph.edges().stream()
                    .filter(value -> value.kind() == ClusterCardEdgeKind.EXTERNAL_ROUTE && (value.from().equals(port.id()) || value.to().equals(port.id())))
                    .findFirst()
                    .orElse(null);
            if (edge == null) {
                positions.put(port.id(), new Vec2(memberBounds.centerX(), memberBounds.minY() - externalPortGap(profile)));
                continue;
            }
            String insideId = edge.from().equals(port.id()) ? edge.to() : edge.from();
            ClusterCardNode inside = graph.node(insideId).orElse(null);
            Vec2 insidePosition = positions.get(insideId);
            Vec2 direction = externalDirection(inside, port);
            int bucket = directionBucket(direction);
            byDirection.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(new PortPlacement(port, insidePosition, direction));
        }
        for (List<PortPlacement> group : byDirection.values()) {
            group.sort(Comparator.comparingDouble(value -> {
                Vec2 normal = new Vec2(-value.direction().y(), value.direction().x());
                Vec2 inside = value.insidePosition() == null ? new Vec2(placementBounds.centerX(), placementBounds.centerY()) : value.insidePosition();
                return inside.x() * normal.x() + inside.y() * normal.y();
            }));
            double center = (group.size() - 1) * 0.5D;
            for (int i = 0; i < group.size(); i++) {
                PortPlacement placement = group.get(i);
                Vec2 inside = placement.insidePosition() == null ? new Vec2(placementBounds.centerX(), placementBounds.centerY()) : placement.insidePosition();
                Vec2 direction = placement.direction();
                Vec2 normal = new Vec2(-direction.y(), direction.x());
                double distance = distanceToBoundsEdge(inside, direction, placementBounds, externalPortBoundsInflate(profile)) + externalPortGap(profile);
                Vec2 portPosition = new Vec2(
                        inside.x() + direction.x() * distance + normal.x() * (i - center) * externalPortSpacing(profile),
                        inside.y() + direction.y() * distance + normal.y() * (i - center) * externalPortSpacing(profile)
                );
                positions.put(placement.node().id(), portPosition);
            }
        }
    }

    private static Vec2 externalDirection(ClusterCardNode inside, ClusterCardNode port) {
        if (inside == null) {
            return normalized(new Vec2(port.worldX(), port.worldZ()));
        }
        return normalized(new Vec2(port.worldX() - inside.worldX(), port.worldZ() - inside.worldZ()));
    }

    private static double distanceToBoundsEdge(Vec2 origin, Vec2 direction, Aabb2 bounds, double fallback) {
        double best = Double.POSITIVE_INFINITY;
        if (Math.abs(direction.x()) > 0.0001D) {
            double tx = ((direction.x() > 0.0D ? bounds.maxX() : bounds.minX()) - origin.x()) / direction.x();
            if (tx >= 0.0D) {
                double y = origin.y() + direction.y() * tx;
                if (y >= bounds.minY() - 0.5D && y <= bounds.maxY() + 0.5D) {
                    best = Math.min(best, tx);
                }
            }
        }
        if (Math.abs(direction.y()) > 0.0001D) {
            double ty = ((direction.y() > 0.0D ? bounds.maxY() : bounds.minY()) - origin.y()) / direction.y();
            if (ty >= 0.0D) {
                double x = origin.x() + direction.x() * ty;
                if (x >= bounds.minX() - 0.5D && x <= bounds.maxX() + 0.5D) {
                    best = Math.min(best, ty);
                }
            }
        }
        return Double.isFinite(best) ? best : fallback;
    }

    private static List<Vec2> routeEdge(ClusterCardEdge edge, Vec2 from, Vec2 to) {
        if (edge.kind() != ClusterCardEdgeKind.EXTERNAL_ROUTE) {
            return List.of(from, to);
        }
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double length = Math.hypot(dx, dy);
        if (length < 1.0D) {
            return List.of(from, to);
        }
        Vec2 mid = new Vec2(from.x() + dx * 0.62D, from.y() + dy * 0.62D);
        return List.of(from, mid, to);
    }

    private static int priority(ClusterCardNode node) {
        return switch (node.kind()) {
            case MEMBER_STATION -> 40;
            case MEMBER_FOLD_ANCHOR -> 35;
            case MEMBER_DEEP_CLUSTER -> 30;
            case EXTERNAL_PORT -> 20;
        };
    }

    private static double collisionRadius(ClusterCardNode node, ClusterCardProfile profile) {
        double radius = switch (node.kind()) {
            case MEMBER_DEEP_CLUSTER -> FullRouteMapConfig.CLUSTER_RADIUS_PX * 1.7D;
            case MEMBER_STATION -> node.mapNode().filter(MapNode::isTransferStation).isPresent()
                    ? FullRouteMapConfig.NODE_RADIUS_PX * 1.85D
                    : FullRouteMapConfig.NODE_RADIUS_PX * 1.35D;
            case MEMBER_FOLD_ANCHOR -> FullRouteMapConfig.NODE_RADIUS_PX * 1.45D;
            case EXTERNAL_PORT -> FullRouteMapConfig.NODE_RADIUS_PX * 1.1D;
        };
        return profile == ClusterCardProfile.DEEP ? radius * 0.86D : radius;
    }

    private static Vec2 normalized(Vec2 value) {
        double length = Math.hypot(value.x(), value.y());
        if (length < 0.001D) {
            return new Vec2(0.0D, -1.0D);
        }
        return new Vec2(value.x() / length, value.y() / length);
    }

    private static int directionBucket(Vec2 direction) {
        double angle = Math.atan2(direction.y(), direction.x());
        int bucket = (int) Math.round(angle / (Math.PI / 8.0D));
        return Math.floorMod(bucket, 16);
    }

    private static double memberMinGap(ClusterCardProfile profile) {
        return profile == ClusterCardProfile.DEEP ? DEEP_MEMBER_MIN_GAP : MEMBER_MIN_GAP;
    }

    private static double memberPadding(ClusterCardProfile profile) {
        return profile == ClusterCardProfile.DEEP ? DEEP_MEMBER_PADDING : MEMBER_PADDING;
    }

    private static double maxLocalSpreadScale(ClusterCardProfile profile) {
        return profile == ClusterCardProfile.DEEP ? DEEP_MAX_LOCAL_SPREAD_SCALE : MAX_LOCAL_SPREAD_SCALE;
    }

    private static double externalPortGap(ClusterCardProfile profile) {
        return profile == ClusterCardProfile.DEEP ? DEEP_EXTERNAL_PORT_GAP : EXTERNAL_PORT_GAP;
    }

    private static double externalPortSpacing(ClusterCardProfile profile) {
        return profile == ClusterCardProfile.DEEP ? DEEP_EXTERNAL_PORT_SPACING : EXTERNAL_PORT_SPACING;
    }

    private static double externalPortBoundsInflate(ClusterCardProfile profile) {
        return profile == ClusterCardProfile.DEEP ? DEEP_EXTERNAL_PORT_BOUNDS_INFLATE : EXTERNAL_PORT_BOUNDS_INFLATE;
    }

    private static double hashAngle(String first, String second) {
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

    private static double square(double value) {
        return value * value;
    }

    private record PortPlacement(ClusterCardNode node, Vec2 insidePosition, Vec2 direction) {
    }
}
