package dev.marblegate.superpipeslide.client.fullmap.cluster.semantic;

import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardEdge;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardEdgeKind;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardNode;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardNodeKind;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardProfile;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardSemanticGraph;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.model.MapCluster;
import dev.marblegate.superpipeslide.client.fullmap.model.MapDimensionGraph;
import dev.marblegate.superpipeslide.client.fullmap.model.MapEdge;
import dev.marblegate.superpipeslide.client.fullmap.model.MapNode;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeKind;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
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

public final class ClusterCardSemanticBuilder {
    public ClusterCardSemanticGraph build(MapDimensionGraph graph, MapCluster cluster) {
        return this.build(graph, cluster, ClusterCardProfile.ORDINARY);
    }

    public ClusterCardSemanticGraph build(MapDimensionGraph graph, MapCluster cluster, ClusterCardProfile profile) {
        List<MapNode> members = this.clusterMembers(graph, cluster, profile);
        Map<NodeId, MapNode> memberById = new LinkedHashMap<>();
        members.forEach(member -> memberById.put(member.id(), member));
        Set<NodeId> memberIds = new LinkedHashSet<>(memberById.keySet());

        Map<String, ClusterCardNode> nodes = new LinkedHashMap<>();
        for (MapNode member : members) {
            ClusterCardNode node = this.memberNode(member, this.hasStationInternalLoop(graph, member.id()));
            nodes.put(node.id(), node);
        }

        List<ClusterCardEdge> edges = new ArrayList<>();
        int externalCount = 0;
        for (MapEdge edge : graph.edges()) {
            NodeId from = this.cardMemberId(graph, edge.from(), memberIds);
            NodeId to = this.cardMemberId(graph, edge.to(), memberIds);
            if (from != null && to != null && !from.equals(to)) {
                edges.add(new ClusterCardEdge(
                        "cluster-card-edge:internal:" + edge.id(),
                        ClusterCardEdgeKind.INTERNAL_ROUTE,
                        memberNodeId(from),
                        memberNodeId(to),
                        Optional.of(edge),
                        Optional.empty(),
                        Optional.empty(),
                        edge.routeLineIds()));
            } else if ((from != null) != (to != null)) {
                NodeId inside = from == null ? to : from;
                NodeId outside = from == null ? edge.from() : edge.to();
                MapNode insideNode = graph.node(inside).orElse(null);
                MapNode outsideNode = graph.node(outside).orElse(null);
                if (insideNode == null || outsideNode == null) {
                    continue;
                }
                String portId = externalPortNodeId(edge, inside, outside);
                nodes.putIfAbsent(portId, this.externalPortNode(portId, outsideNode, edge.routeLineIds()));
                edges.add(new ClusterCardEdge(
                        "cluster-card-edge:external:" + edge.id() + ":" + inside + ":" + outside,
                        ClusterCardEdgeKind.EXTERNAL_ROUTE,
                        memberNodeId(inside),
                        portId,
                        Optional.of(edge),
                        Optional.of(inside),
                        Optional.of(outside),
                        edge.routeLineIds()));
                externalCount++;
            }
        }

        edges.addAll(this.foldPeerLinks(members));
        Aabb2 bounds = Aabb2.empty();
        for (ClusterCardNode node : nodes.values()) {
            if (node.kind() == ClusterCardNodeKind.EXTERNAL_PORT) {
                continue;
            }
            bounds = bounds.include(node.worldX(), node.worldZ());
        }
        if (bounds.isEmpty()) {
            bounds = Aabb2.around(cluster.worldX(), cluster.worldZ(), 1.0D);
        }
        return new ClusterCardSemanticGraph(
                profile,
                graph.levelKey(),
                cluster,
                nodes.values().stream().toList(),
                edges,
                cluster.routeLineIds(),
                externalCount,
                bounds);
    }

    private List<MapNode> clusterMembers(MapDimensionGraph graph, MapCluster cluster, ClusterCardProfile profile) {
        List<MapNode> baseMembers = cluster.memberNodeIds().stream()
                .map(graph::node)
                .flatMap(Optional::stream)
                .filter(node -> profile != ClusterCardProfile.DEEP || node.kind() == NodeKind.STATION)
                .toList();
        Map<NodeId, MapNode> members = new LinkedHashMap<>();
        baseMembers.forEach(node -> members.put(node.id(), node));
        Set<NodeId> baseIds = new LinkedHashSet<>(members.keySet());
        for (MapEdge edge : graph.edges()) {
            MapNode from = graph.node(edge.from()).orElse(null);
            MapNode to = graph.node(edge.to()).orElse(null);
            if (from == null || to == null) {
                continue;
            }
            if (from.kind() == NodeKind.FOLD_ANCHOR && this.cardMemberId(graph, to.id(), baseIds) != null && nearClusterMember(from, baseMembers)) {
                members.put(from.id(), from);
            }
            if (to.kind() == NodeKind.FOLD_ANCHOR && this.cardMemberId(graph, from.id(), baseIds) != null && nearClusterMember(to, baseMembers)) {
                members.put(to.id(), to);
            }
        }
        return members.values().stream().sorted(Comparator.comparing(MapNode::id)).toList();
    }

    private ClusterCardNode memberNode(MapNode node, boolean stationInternalLoop) {
        ClusterCardNodeKind kind = switch (node.kind()) {
            case DEEP_CLUSTER -> ClusterCardNodeKind.MEMBER_DEEP_CLUSTER;
            case FOLD_ANCHOR -> ClusterCardNodeKind.MEMBER_FOLD_ANCHOR;
            case STATION, CLUSTER -> ClusterCardNodeKind.MEMBER_STATION;
        };
        return new ClusterCardNode(
                memberNodeId(node.id()),
                kind,
                Optional.of(node.id()),
                Optional.of(node),
                node.levelKey(),
                node.worldX(),
                node.worldZ(),
                node.worldY(),
                node.label(),
                node.routeLineIds(),
                node.foldAnchorId(),
                node.foldPeerId(),
                Optional.empty(),
                stationInternalLoop);
    }

    private ClusterCardNode externalPortNode(String id, MapNode outsideNode, List<UUID> routeLineIds) {
        return new ClusterCardNode(
                id,
                ClusterCardNodeKind.EXTERNAL_PORT,
                Optional.of(outsideNode.id()),
                Optional.of(outsideNode),
                outsideNode.levelKey(),
                outsideNode.worldX(),
                outsideNode.worldZ(),
                outsideNode.worldY(),
                outsideNode.label(),
                routeLineIds,
                Optional.empty(),
                Optional.empty(),
                Optional.of(outsideNode.id()),
                false);
    }

    private boolean hasStationInternalLoop(MapDimensionGraph graph, NodeId nodeId) {
        return graph.node(nodeId)
                .filter(node -> node.kind() == NodeKind.STATION)
                .isPresent()
                && graph.edges().stream().anyMatch(edge -> edge.from().equals(nodeId) && edge.to().equals(nodeId));
    }

    private List<ClusterCardEdge> foldPeerLinks(List<MapNode> members) {
        Map<PipeAnchorId, MapNode> foldsByAnchor = new LinkedHashMap<>();
        for (MapNode member : members) {
            member.foldAnchorId().ifPresent(anchor -> foldsByAnchor.put(anchor, member));
        }
        Set<String> rendered = new LinkedHashSet<>();
        List<ClusterCardEdge> result = new ArrayList<>();
        for (MapNode member : members) {
            if (member.kind() != NodeKind.FOLD_ANCHOR || member.foldAnchorId().isEmpty() || member.foldPeerId().isEmpty()) {
                continue;
            }
            MapNode peer = foldsByAnchor.get(member.foldPeerId().get());
            if (peer == null) {
                continue;
            }
            String first = memberNodeId(member.id());
            String second = memberNodeId(peer.id());
            String key = first.compareTo(second) <= 0 ? first + ":" + second : second + ":" + first;
            if (!rendered.add(key)) {
                continue;
            }
            result.add(new ClusterCardEdge(
                    "cluster-card-edge:fold-peer:" + key,
                    ClusterCardEdgeKind.FOLD_PEER_LINK,
                    first,
                    second,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    List.of()));
        }
        return result;
    }

    private NodeId cardMemberId(MapDimensionGraph graph, NodeId nodeId, Set<NodeId> memberIds) {
        NodeId current = nodeId;
        while (current != null) {
            if (memberIds.contains(current)) {
                return current;
            }
            MapNode node = graph.node(current).orElse(null);
            if (node == null || node.clusterId().isEmpty()) {
                return null;
            }
            current = node.clusterId().get();
        }
        return null;
    }

    private static boolean nearClusterMember(MapNode node, List<MapNode> members) {
        return members.stream().anyMatch(member -> Math.hypot(node.worldX() - member.worldX(), node.worldZ() - member.worldZ()) < FullRouteMapConfig.CLUSTER_THRESHOLD);
    }

    private static String memberNodeId(NodeId nodeId) {
        return "member:" + nodeId.kind() + ":" + nodeId.levelKey().identifier() + ":" + nodeId.primaryId() + ":" + nodeId.occurrence();
    }

    private static String externalPortNodeId(MapEdge edge, NodeId inside, NodeId outside) {
        return "external:" + stableUuid("cluster-card-external:" + edge.id() + ":" + inside + ":" + outside);
    }

    private static UUID stableUuid(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
