package dev.marblegate.superpipeslide.client.fullmap.export;

import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.model.MapCluster;
import dev.marblegate.superpipeslide.client.fullmap.model.MapDimensionGraph;
import dev.marblegate.superpipeslide.client.fullmap.model.MapEdge;
import dev.marblegate.superpipeslide.client.fullmap.model.MapEdgeOccurrence;
import dev.marblegate.superpipeslide.client.fullmap.model.MapNode;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualEdgePath;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualLabel;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualLane;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualNode;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualRouteMapGraph;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Produces a route-line-filtered copy of one dimension's schematic graphs for the PNG map
 * export. Filtering happens at the model level — edges keep only the selected route lines,
 * lanes are re-centered with the solver's lane step, unreferenced nodes and labels are
 * dropped and the bounds are recomputed — so the unchanged renderer can draw the result
 * exactly as it would draw the full graph, and the exported image is automatically cropped
 * to the extent of the selected lines.
 *
 * <p>Geometry is never moved: kept edge paths, node positions and label positions are
 * carried over verbatim, which keeps the export visually identical to the on-screen map.
 */
public final class SchematicGraphFilter {
    /** Matches {@code MetroMapSchematicSolver.lanesFor}: lane spacing in layout units. */
    private static final double LANE_STEP_BLOCKS = FullRouteMapConfig.LINE_WIDTH_PX / FullRouteMapConfig.BASE_SCALE + 3.0D;

    /** Matches the bounds inflation applied by the metro solver. */
    private static final double BOUNDS_INFLATE_BLOCKS = 72.0D;

    private SchematicGraphFilter() {}

    /** The filtered pair of graphs for one dimension. */
    public record Filtered(MapDimensionGraph dataGraph, VisualRouteMapGraph visualGraph) {
        /** True when no edge survived filtering, i.e. the selected lines have no content in this dimension. */
        public boolean isEmpty() {
            return this.visualGraph.edgePaths().isEmpty();
        }
    }

    public static Filtered filter(MapDimensionGraph graph, VisualRouteMapGraph visualGraph, Set<UUID> selectedRouteLineIds) {
        List<VisualEdgePath> keptPaths = new ArrayList<>();
        for (VisualEdgePath path : visualGraph.edgePaths()) {
            VisualEdgePath filtered = filterEdgePath(path, selectedRouteLineIds);
            if (filtered != null) {
                keptPaths.add(filtered);
            }
        }

        Set<NodeId> referencedNodes = new HashSet<>();
        for (VisualEdgePath path : keptPaths) {
            referencedNodes.add(path.from());
            referencedNodes.add(path.to());
        }

        List<VisualNode> keptNodes = visualGraph.nodes().stream().filter(node -> referencedNodes.contains(node.id())).toList();
        Map<NodeId, VisualNode> keptNodesById = new LinkedHashMap<>();
        for (VisualNode node : keptNodes) {
            keptNodesById.put(node.id(), node);
        }
        List<VisualLabel> keptLabels = visualGraph.labels().stream().filter(label -> referencedNodes.contains(label.nodeId())).toList();
        Map<String, VisualEdgePath> keptPathsById = new LinkedHashMap<>();
        for (VisualEdgePath path : keptPaths) {
            keptPathsById.put(path.edgeId(), path);
        }

        Aabb2 bounds = Aabb2.empty();
        for (VisualNode node : keptNodes) {
            bounds = bounds.include(node.x(), node.z());
        }
        for (VisualEdgePath path : keptPaths) {
            bounds = bounds.include(path.bounds());
        }
        for (VisualLabel label : keptLabels) {
            bounds = bounds.include(label.x(), label.z());
        }
        bounds = bounds.isEmpty() ? Aabb2.around(0.0D, 0.0D, 32.0D) : bounds.inflate(BOUNDS_INFLATE_BLOCKS);

        VisualRouteMapGraph filteredVisual = new VisualRouteMapGraph(
                visualGraph.levelKey(),
                keptNodes,
                keptNodesById,
                keptPaths,
                keptPathsById,
                keptLabels,
                // Quality counts describe the unfiltered layout; they are only read by the
                // debug overlay, which the export never draws.
                visualGraph.quality(),
                bounds,
                visualGraph.routeRevision(),
                visualGraph.pipeRevision(),
                visualGraph.solverVersion());

        MapDimensionGraph filteredData = filterDataGraph(graph, keptPathsById.keySet(), referencedNodes, selectedRouteLineIds);
        return new Filtered(filteredData, filteredVisual);
    }

    /** Returns the filtered edge path, or {@code null} when no selected line runs over it. */
    private static VisualEdgePath filterEdgePath(VisualEdgePath path, Set<UUID> selected) {
        List<MapEdgeOccurrence> occurrences = path.occurrences().stream().filter(occurrence -> selected.contains(occurrence.routeLineId())).toList();
        if (occurrences.isEmpty()) {
            return null;
        }
        List<UUID> routeLineIds = occurrences.stream().map(MapEdgeOccurrence::routeLineId).distinct().toList();
        List<VisualLane> lanes = path.lanes().stream().filter(lane -> lane.routeLineId().filter(routeLineIds::contains).isPresent()).toList();
        if (lanes.isEmpty()) {
            // Defensive: a kept edge always carries at least one selected lane. Fall back
            // to the line-less grey lane the renderer already knows how to draw.
            lanes = List.of(new VisualLane(Optional.empty(), 0, 0.0D));
        } else {
            lanes = relayoutLanes(lanes);
        }
        return new VisualEdgePath(path.edgeId(), path.from(), path.to(), path.kind(), routeLineIds, occurrences, path.points(), lanes, path.hitShape(), path.bounds(), path.fallback());
    }

    /** Re-centers the kept lanes symmetrically around the path centerline, mirroring the solver's {@code lanesFor}. */
    private static List<VisualLane> relayoutLanes(List<VisualLane> keptLanes) {
        double center = (keptLanes.size() - 1) * 0.5D;
        List<VisualLane> lanes = new ArrayList<>(keptLanes.size());
        for (int i = 0; i < keptLanes.size(); i++) {
            lanes.add(new VisualLane(keptLanes.get(i).routeLineId(), i, (i - center) * LANE_STEP_BLOCKS));
        }
        return lanes;
    }

    private static MapDimensionGraph filterDataGraph(MapDimensionGraph graph, Set<String> keptEdgeIds, Set<NodeId> referencedNodes, Set<UUID> selected) {
        List<MapNode> nodes = new ArrayList<>();
        Map<NodeId, MapNode> nodesById = new LinkedHashMap<>();
        for (NodeId id : referencedNodes) {
            // Synthetic portal nodes have no raw counterpart; the pure schematic renderer
            // draws fold anchors without one.
            graph.node(id).ifPresent(raw -> {
                MapNode filtered = new MapNode(
                        raw.id(),
                        raw.levelKey(),
                        raw.worldX(),
                        raw.worldZ(),
                        raw.worldY(),
                        raw.label(),
                        raw.kind(),
                        raw.stationGroupIds(),
                        raw.platformStopIds(),
                        raw.routeLineIds().stream().filter(selected::contains).toList(),
                        raw.foldAnchorId(),
                        raw.foldPeerId(),
                        raw.clusterId());
                nodes.add(filtered);
                nodesById.put(filtered.id(), filtered);
            });
        }

        List<MapEdge> edges = new ArrayList<>();
        for (MapEdge edge : graph.edges()) {
            if (!keptEdgeIds.contains(edge.id())) {
                continue;
            }
            List<MapEdgeOccurrence> occurrences = edge.occurrences().stream().filter(occurrence -> selected.contains(occurrence.routeLineId())).toList();
            edges.add(new MapEdge(edge.id(), edge.levelKey(), edge.from(), edge.to(), occurrences, edge.worldBounds()));
        }

        List<MapCluster> clusters = new ArrayList<>();
        Map<NodeId, MapCluster> clustersById = new LinkedHashMap<>();
        for (MapCluster cluster : graph.clusters()) {
            if (!referencedNodes.contains(cluster.nodeId())) {
                continue;
            }
            MapCluster filtered = new MapCluster(
                    cluster.nodeId(),
                    cluster.levelKey(),
                    cluster.stationGroupIds(),
                    cluster.memberNodeIds(),
                    cluster.label(),
                    cluster.worldX(),
                    cluster.worldZ(),
                    cluster.worldY(),
                    cluster.routeLineIds().stream().filter(selected::contains).toList());
            clusters.add(filtered);
            clustersById.put(filtered.nodeId(), filtered);
        }

        // Transfer hints, missing-path hints and diagnostics are not drawn by the pure
        // schematic render path, so the filtered graph carries none.
        return new MapDimensionGraph(
                graph.levelKey(),
                nodes,
                nodesById,
                edges,
                List.of(),
                List.of(),
                clusters,
                clustersById,
                List.of(),
                graph.worldBounds(),
                graph.routeRevision(),
                graph.pipeRevision());
    }
}
