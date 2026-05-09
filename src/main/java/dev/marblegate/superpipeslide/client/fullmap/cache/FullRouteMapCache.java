package dev.marblegate.superpipeslide.client.fullmap.cache;

import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.fullmap.builder.FullRouteMapBuilder;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapLayoutMode;
import dev.marblegate.superpipeslide.client.fullmap.model.FullRouteMapSourceSnapshot;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.model.MapDimensionGraph;
import dev.marblegate.superpipeslide.client.fullmap.model.MapEdge;
import dev.marblegate.superpipeslide.client.fullmap.model.MapNode;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import dev.marblegate.superpipeslide.client.fullmap.physical.PhysicalRouteMapGraph;
import dev.marblegate.superpipeslide.client.fullmap.physical.PhysicalRouteMapGraphBuilder;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SchematicInputGraph;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SchematicQualityReport;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SemanticEdgeKind;
import dev.marblegate.superpipeslide.client.fullmap.schematic.SchematicInputBuilder;
import dev.marblegate.superpipeslide.client.fullmap.schematic.SchematicLayoutConfig;
import dev.marblegate.superpipeslide.client.fullmap.schematic.solve.HeuristicGlobalSolver;
import dev.marblegate.superpipeslide.client.fullmap.schematic.solve.SchematicSolverBackend;
import dev.marblegate.superpipeslide.client.fullmap.schematic.solve.VisualRouteMapGraphSnapshot;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualEdgePath;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualHitShape;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualLabel;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualLane;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualNode;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualRouteMapGraph;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class FullRouteMapCache {
    private static long cachedRouteRevision = Long.MIN_VALUE;
    private static long cachedPipeRevision = Long.MIN_VALUE;
    private static long dirtySinceMillis;
    private static long lastClosedMillis;
    private static Map<ResourceKey<Level>, MapDimensionGraph> cachedGraphs = Map.of();
    private static Map<ResourceKey<Level>, VisualRouteMapGraph> cachedVisualGraphs = Map.of();
    private static Map<ResourceKey<Level>, PhysicalRouteMapGraph> cachedPhysicalGraphs = Map.of();
    private static final SchematicSolverBackend SOLVER = new HeuristicGlobalSolver();
    private static FullRouteMapLayoutMode layoutMode = FullRouteMapLayoutMode.PRACTICAL;

    private FullRouteMapCache() {
    }

    public static void invalidate() {
        cachedRouteRevision = Long.MIN_VALUE;
        cachedPipeRevision = Long.MIN_VALUE;
        cachedGraphs = Map.of();
        cachedVisualGraphs = Map.of();
        cachedPhysicalGraphs = Map.of();
        dirtySinceMillis = 0L;
    }

    public static void markClosed() {
        lastClosedMillis = System.currentTimeMillis();
    }

    public static FullRouteMapLayoutMode layoutMode() {
        return layoutMode;
    }

    public static void setLayoutMode(FullRouteMapLayoutMode mode) {
        FullRouteMapLayoutMode next = mode == null ? FullRouteMapLayoutMode.PRACTICAL : mode;
        if (layoutMode != next) {
            layoutMode = next;
            cachedRouteRevision = Long.MIN_VALUE;
            cachedPipeRevision = Long.MIN_VALUE;
            cachedVisualGraphs = Map.of();
            cachedPhysicalGraphs = Map.of();
            dirtySinceMillis = 0L;
        }
    }

    public static boolean refresh(boolean force) {
        long routeRevision = ClientRouteDataCache.revision();
        long pipeRevision = ClientPipeNetworkCache.aggregateRevision();
        if (!force
                && cachedRouteRevision == routeRevision
                && cachedPipeRevision == pipeRevision
                && !cachedGraphs.isEmpty()
                && System.currentTimeMillis() - lastClosedMillis <= FullRouteMapConfig.CACHE_TTL_MILLIS) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (!force && dirtySinceMillis == 0L && cachedRouteRevision != Long.MIN_VALUE) {
            dirtySinceMillis = now;
            return false;
        }
        if (!force && dirtySinceMillis > 0L && now - dirtySinceMillis < FullRouteMapConfig.UPDATE_DEBOUNCE_MILLIS) {
            return false;
        }

        FullRouteMapSourceSnapshot source = new FullRouteMapSourceSnapshot(
                routeRevision,
                pipeRevision,
                List.copyOf(ClientRouteDataCache.stationGroups()),
                List.copyOf(ClientRouteDataCache.platformStops()),
                List.copyOf(ClientRouteDataCache.routeLines()),
                List.copyOf(ClientRouteDataCache.routeLayouts()),
                List.copyOf(ClientRouteDataCache.routeSections()),
                List.copyOf(ClientRouteDataCache.stationTransferLinks()),
                ClientRouteDataCache.routeSectionPaths(),
                List.copyOf(ClientPipeNetworkCache.foldAnchors())
        );
        Map<ResourceKey<Level>, VisualRouteMapGraph> previousVisualGraphs = cachedVisualGraphs;
        cachedGraphs = new FullRouteMapBuilder(source).build();
        if (layoutMode.physical()) {
            cachedVisualGraphs = Map.of();
            cachedPhysicalGraphs = new PhysicalRouteMapGraphBuilder(source).build(cachedGraphs.keySet());
        } else {
            cachedPhysicalGraphs = Map.of();
            cachedVisualGraphs = buildVisualGraphs(cachedGraphs, previousVisualGraphs);
        }
        cachedRouteRevision = routeRevision;
        cachedPipeRevision = pipeRevision;
        dirtySinceMillis = 0L;
        return true;
    }

    public static Collection<ResourceKey<Level>> dimensions() {
        refresh(false);
        return List.copyOf(cachedGraphs.keySet());
    }

    public static Optional<MapDimensionGraph> graph(ResourceKey<Level> levelKey) {
        refresh(false);
        return Optional.ofNullable(cachedGraphs.get(levelKey));
    }

    public static Map<ResourceKey<Level>, MapDimensionGraph> graphs() {
        refresh(false);
        return new LinkedHashMap<>(cachedGraphs);
    }

    public static Optional<VisualRouteMapGraph> visualGraph(ResourceKey<Level> levelKey) {
        refresh(false);
        return Optional.ofNullable(cachedVisualGraphs.get(levelKey));
    }

    public static Map<ResourceKey<Level>, VisualRouteMapGraph> visualGraphs() {
        refresh(false);
        return new LinkedHashMap<>(cachedVisualGraphs);
    }

    public static Optional<PhysicalRouteMapGraph> physicalGraph(ResourceKey<Level> levelKey) {
        refresh(false);
        return Optional.ofNullable(cachedPhysicalGraphs.get(levelKey));
    }

    public static Map<ResourceKey<Level>, PhysicalRouteMapGraph> physicalGraphs() {
        refresh(false);
        return new LinkedHashMap<>(cachedPhysicalGraphs);
    }

    public static Aabb2 displayBounds(ResourceKey<Level> levelKey) {
        refresh(false);
        if (layoutMode.physical()) {
            PhysicalRouteMapGraph physicalGraph = cachedPhysicalGraphs.get(levelKey);
            if (physicalGraph != null) {
                return physicalGraph.worldBounds();
            }
        }
        VisualRouteMapGraph visualGraph = cachedVisualGraphs.get(levelKey);
        if (visualGraph != null) {
            return visualGraph.visualBounds();
        }
        MapDimensionGraph graph = cachedGraphs.get(levelKey);
        return graph == null ? new Aabb2(-64.0D, -64.0D, 64.0D, 64.0D) : graph.worldBounds();
    }

    public static Optional<ResourceKey<Level>> firstDimension() {
        refresh(false);
        return cachedGraphs.keySet().stream().findFirst();
    }

    public static Optional<ResourceKey<Level>> dimensionForFoldPeer(FoldAnchorNode foldAnchor) {
        return ClientPipeNetworkCache.globalFoldCounterpart(foldAnchor.anchorId()).map(anchorId -> anchorId.levelKey());
    }

    private static Map<ResourceKey<Level>, VisualRouteMapGraph> buildVisualGraphs(Map<ResourceKey<Level>, MapDimensionGraph> graphs, Map<ResourceKey<Level>, VisualRouteMapGraph> previousVisualGraphs) {
        Map<ResourceKey<Level>, VisualRouteMapGraph> visualGraphs = new LinkedHashMap<>();
        SchematicLayoutConfig config = SchematicLayoutConfig.forMode(layoutMode);
        for (MapDimensionGraph graph : graphs.values()) {
            try {
                SchematicInputGraph input = new SchematicInputBuilder(graph, config).build();
                Optional<VisualRouteMapGraphSnapshot> previous = Optional.ofNullable(previousVisualGraphs.get(graph.levelKey())).map(VisualRouteMapGraphSnapshot::of);
                visualGraphs.put(graph.levelKey(), SOLVER.solve(input, config, previous).graph());
            } catch (RuntimeException exception) {
                visualGraphs.put(graph.levelKey(), fallbackVisualGraph(graph));
            }
        }
        return visualGraphs;
    }

    private static VisualRouteMapGraph fallbackVisualGraph(MapDimensionGraph graph) {
        List<VisualNode> nodes = graph.nodes().stream()
                .map(node -> new VisualNode(node.id(), node.kind(), node.worldX(), node.worldZ(), node.worldX(), node.worldZ(), node.label(), node.routeLineIds(), node.routeLineIds().size(), true))
                .toList();
        Map<NodeId, VisualNode> nodesById = nodes.stream()
                .collect(Collectors.toMap(VisualNode::id, node -> node, (a, b) -> a, LinkedHashMap::new));
        List<VisualEdgePath> edgePaths = new ArrayList<>();
        for (MapEdge edge : graph.edges()) {
            MapNode from = graph.nodesById().get(edge.from());
            MapNode to = graph.nodesById().get(edge.to());
            if (from == null || to == null) {
                continue;
            }
            List<Vec2> points = List.of(new Vec2(from.worldX(), from.worldZ()), new Vec2(to.worldX(), to.worldZ()));
            Aabb2 bounds = Aabb2.empty().include(from.worldX(), from.worldZ()).include(to.worldX(), to.worldZ()).inflate(32.0D);
            List<VisualLane> lanes = edge.routeLineIds().isEmpty()
                    ? List.of(new VisualLane(Optional.empty(), 0, 0.0D))
                    : edge.routeLineIds().stream().map(id -> new VisualLane(Optional.of(id), edge.routeLineIds().indexOf(id), 0.0D)).toList();
            edgePaths.add(new VisualEdgePath(edge.id(), edge.from(), edge.to(), SemanticEdgeKind.NORMAL, edge.routeLineIds(), edge.occurrences(), points, lanes, new VisualHitShape(points, 24.0D, bounds), bounds, true));
        }
        Map<String, VisualEdgePath> edgesById = edgePaths.stream()
                .collect(Collectors.toMap(VisualEdgePath::edgeId, edge -> edge, (a, b) -> a, LinkedHashMap::new));
        List<VisualLabel> labels = nodes.stream()
                .map(node -> new VisualLabel(node.id(), node.label(), node.x() + 18.0D, node.z() - 6.0D, node.importance(), 0.68D, true))
                .toList();
        return new VisualRouteMapGraph(
                graph.levelKey(),
                nodes,
                nodesById,
                edgePaths,
                edgesById,
                labels,
                SchematicQualityReport.fallback(0L, edgePaths.size()),
                graph.worldBounds(),
                graph.routeRevision(),
                graph.pipeRevision(),
                FullRouteMapConfig.SCHEMATIC_SOLVER_VERSION
        );
    }
}
