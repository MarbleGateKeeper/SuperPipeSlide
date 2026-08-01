package dev.marblegate.superpipeslide.client.fullmap.cache;

import com.mojang.logging.LogUtils;
import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.fullmap.builder.FullRouteMapBuilder;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapLayoutMode;
import dev.marblegate.superpipeslide.client.fullmap.model.FullRouteMapSourceSnapshot;
import dev.marblegate.superpipeslide.client.fullmap.model.MapDimensionGraph;
import dev.marblegate.superpipeslide.client.fullmap.model.MapEdge;
import dev.marblegate.superpipeslide.client.fullmap.model.MapNode;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.physical.PhysicalRouteMapGraph;
import dev.marblegate.superpipeslide.client.fullmap.physical.PhysicalRouteMapGraphBuilder;
import dev.marblegate.superpipeslide.client.fullmap.schematic.SchematicInputBuilder;
import dev.marblegate.superpipeslide.client.fullmap.schematic.SchematicLayoutConfig;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.LabelWidthMeasurer;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SchematicInputGraph;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SchematicQualityReport;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SemanticEdgeKind;
import dev.marblegate.superpipeslide.client.fullmap.schematic.solve.HeuristicGlobalSolver;
import dev.marblegate.superpipeslide.client.fullmap.schematic.solve.SchematicSolverBackend;
import dev.marblegate.superpipeslide.client.fullmap.schematic.solve.VisualRouteMapGraphSnapshot;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.LabelSlot;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualEdgePath;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualHitShape;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualLabel;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualLane;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualNode;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualRouteMapGraph;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorNode;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.config.ClientConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public final class FullRouteMapCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Every printable ASCII character, pre-baked so lazy ASCII glyph stitches never reach the builder thread. */
    private static final String ASCII_PRINTABLE = " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";
    // Dedicated single-threaded executor for full route map builds. The daemon thread
    // never blocks JVM shutdown, and serial execution guarantees a superseded build has
    // wound down (via its cancellation flag) before the next build starts.
    private static final ExecutorService MAP_BUILD_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sps-map-builder");
        thread.setDaemon(true);
        return thread;
    });

    private static long cachedRouteRevision = Long.MIN_VALUE;
    private static long cachedPipeRevision = Long.MIN_VALUE;
    private static long dirtySinceMillis;
    private static long lastClosedMillis;
    // Timestamp of the last successful rebuild (diagnostics only).
    private static long builtAtMillis;
    // True once any build (even an empty one) has completed. The TTL no longer gates
    // the refresh fast path -- it is applied once per screen open in markOpened() --
    // so this flag is what proves the cache is warm and avoids periodic rebuilds
    // while the map stays open.
    private static boolean builtOnce;
    // Swapped atomically on the render thread when an async build result is applied;
    // volatile so the building() state and the graphs stay consistent for readers.
    private static volatile Map<ResourceKey<Level>, MapDimensionGraph> cachedGraphs = Map.of();
    private static volatile Map<ResourceKey<Level>, VisualRouteMapGraph> cachedVisualGraphs = Map.of();
    private static volatile Map<ResourceKey<Level>, PhysicalRouteMapGraph> cachedPhysicalGraphs = Map.of();
    // The running (or finished-but-not-yet-applied) async build; null when idle.
    // Written on the render thread only; the builder thread never touches it.
    private static volatile BuildTask inFlightBuild;
    private static final SchematicSolverBackend DEFAULT_SOLVER = new HeuristicGlobalSolver();
    // Per-layout-mode overrides of the schematic solver backend, registered by experimental
    // tooling through registerSolverBackend; modes without an override use DEFAULT_SOLVER.
    // Backend lookups happen on the map builder thread while registration may come from any
    // thread, hence the concurrent map.
    private static final Map<FullRouteMapLayoutMode, SchematicSolverBackend> SOLVER_OVERRIDES = new ConcurrentHashMap<>();
    private static FullRouteMapLayoutMode layoutMode = configuredDefaultLayoutMode();

    private FullRouteMapCache() {}

    /**
     * Registers an experimental schematic solver backend for a layout mode; {@code null}
     * removes the override and restores {@link #DEFAULT_SOLVER}. Registration may happen on
     * any thread while lookups run on the map builder thread.
     */
    public static void registerSolverBackend(FullRouteMapLayoutMode mode, SchematicSolverBackend backend) {
        if (backend == null) {
            SOLVER_OVERRIDES.remove(mode);
        } else {
            SOLVER_OVERRIDES.put(mode, backend);
        }
    }

    private static SchematicSolverBackend solverFor(FullRouteMapLayoutMode mode) {
        return SOLVER_OVERRIDES.getOrDefault(mode, DEFAULT_SOLVER);
    }

    public static void invalidate() {
        cancelInFlightBuild();
        cachedRouteRevision = Long.MIN_VALUE;
        cachedPipeRevision = Long.MIN_VALUE;
        cachedGraphs = Map.of();
        cachedVisualGraphs = Map.of();
        cachedPhysicalGraphs = Map.of();
        dirtySinceMillis = 0L;
        lastClosedMillis = System.currentTimeMillis();
        builtOnce = false;
    }

    public static void markClosed() {
        lastClosedMillis = System.currentTimeMillis();
    }

    /**
     * Applies the cache TTL once, when the full route map screen opens: if the map was
     * closed for longer than {@link FullRouteMapConfig#CACHE_TTL_MILLIS}, the next
     * refresh is forced to rebuild via the normal debounce path. Applying the TTL here
     * instead of inside {@link #refresh(boolean)} avoids periodic full rebuilds while
     * the map stays open.
     */
    public static void markOpened() {
        if (System.currentTimeMillis() - lastClosedMillis > FullRouteMapConfig.CACHE_TTL_MILLIS) {
            cachedRouteRevision = Long.MIN_VALUE;
            cachedPipeRevision = Long.MIN_VALUE;
        }
    }

    public static FullRouteMapLayoutMode layoutMode() {
        return layoutMode;
    }

    private static FullRouteMapLayoutMode configuredDefaultLayoutMode() {
        try {
            return FullRouteMapLayoutMode.valueOf(ClientConfig.FULL_ROUTE_MAP_DEFAULT_LAYOUT_MODE.get());
        } catch (IllegalArgumentException exception) {
            return FullRouteMapLayoutMode.PRACTICAL;
        }
    }

    public static void setLayoutMode(FullRouteMapLayoutMode mode) {
        FullRouteMapLayoutMode next = mode == null ? configuredDefaultLayoutMode() : mode;
        if (layoutMode != next) {
            cancelInFlightBuild();
            layoutMode = next;
            cachedRouteRevision = Long.MIN_VALUE;
            cachedPipeRevision = Long.MIN_VALUE;
            cachedVisualGraphs = Map.of();
            cachedPhysicalGraphs = Map.of();
            dirtySinceMillis = 0L;
            builtOnce = false;
        }
        // Persist the selection so the next game start restores it (spec save is cheap
        // and follows the same set+save pattern as the client safety options).
        if (!next.name().equals(ClientConfig.FULL_ROUTE_MAP_DEFAULT_LAYOUT_MODE.get())) {
            ClientConfig.FULL_ROUTE_MAP_DEFAULT_LAYOUT_MODE.set(next.name());
            ClientConfig.save();
        }
    }

    /**
     * True while an asynchronous map build has been submitted and its result has not been
     * applied to the cache yet. The map screen uses this to show a building indicator;
     * while it is true the cache keeps serving the previous graphs.
     */
    public static boolean building() {
        return inFlightBuild != null;
    }

    public static boolean refresh(boolean force) {
        boolean applied = applyFinishedBuild();
        long routeRevision = ClientRouteDataCache.revision();
        long pipeRevision = ClientPipeNetworkCache.aggregateRevision();
        if (!force
                && cachedRouteRevision == routeRevision
                && cachedPipeRevision == pipeRevision
                && builtOnce) {
            return applied;
        }
        BuildTask inFlight = inFlightBuild;
        if (!force && inFlight != null && inFlight.source.routeRevision() == routeRevision && inFlight.source.pipeRevision() == pipeRevision) {
            // A build for exactly these revisions is already running; keep showing the
            // previous graphs until it lands.
            return applied;
        }

        long now = System.currentTimeMillis();
        if (!force && dirtySinceMillis == 0L && (cachedRouteRevision != Long.MIN_VALUE || inFlight != null)) {
            dirtySinceMillis = now;
            return applied;
        }
        if (!force && dirtySinceMillis > 0L && now - dirtySinceMillis < FullRouteMapConfig.UPDATE_DEBOUNCE_MILLIS) {
            return applied;
        }

        submitBuild(routeRevision, pipeRevision);
        dirtySinceMillis = 0L;
        return true;
    }

    /**
     * Assembles the source snapshot and hands the build to the background executor. Must
     * run on the render thread: the snapshot reads the mutable client caches, which are
     * only ever written from this same thread, while the returned snapshot is immutable
     * and exclusively read by the builder thread afterwards. The render thread is also the
     * only thread where the label glyph pre-bake below is legal, because a first-time glyph
     * measurement may upload the glyph bitmap to a GL texture.
     */
    private static void submitBuild(long routeRevision, long pipeRevision) {
        cancelInFlightBuild();
        List<PipeConnection> connections = new ArrayList<>();
        for (ResourceKey<Level> levelKey : ClientPipeNetworkCache.knownDimensions()) {
            connections.addAll(ClientPipeNetworkCache.connections(levelKey));
        }
        FullRouteMapSourceSnapshot source = FullRouteMapSourceSnapshot.of(
                routeRevision,
                pipeRevision,
                List.copyOf(ClientRouteDataCache.stationGroups()),
                List.copyOf(ClientRouteDataCache.platformStops()),
                List.copyOf(ClientRouteDataCache.routeLines()),
                List.copyOf(ClientRouteDataCache.routeLayouts()),
                List.copyOf(ClientRouteDataCache.routeSections()),
                List.copyOf(ClientRouteDataCache.stationTransferLinks()),
                ClientRouteDataCache.routeSectionPaths(),
                List.copyOf(ClientPipeNetworkCache.foldAnchors()),
                connections);
        // Capture the font here, on the render thread: the builder thread must not dereference
        // the Minecraft singleton itself. The first Font.width call on a glyph that has not been
        // baked yet lazily uploads its bitmap to a GL texture (UnihexProvider glyphs for CJK
        // station names are the known case), and that upload is a render-thread-only operation --
        // it throws IllegalStateException when the solver measures such a label on the builder
        // thread. Pre-baking every label string the solver may measure makes the lambda below a
        // genuine pure read of the baked glyph cache. CJK station names are roughly twice as wide
        // as the old Latin estimate assumed, which is why the solvers need the real measurement.
        Font font = Minecraft.getInstance().font;
        preBakeLabelGlyphs(font, source);
        LabelWidthMeasurer labelWidthMeasurer = (text, scale) -> font.width(text) * scale;
        inFlightBuild = new BuildTask(source, layoutMode, cachedVisualGraphs, labelWidthMeasurer);
    }

    /**
     * Measures, on the render thread, every string the schematic solver may later measure on the
     * map builder thread, forcing the lazy glyph upload to happen here where GL access is legal.
     * The set mirrors every source of {@code MapNode.label()}: station primary names
     * ({@link StationGroup#primaryName}), fold anchor display names
     * ({@link FoldAnchorNode#displayName} -- the counterpart fallback reads a name from the same
     * snapshot list, so it is covered too), and the static cluster-name translation wrappers. The
     * derived cluster names themselves are assembled from primary-name glyphs (a common prefix, or
     * the first name embedded in the "and N more" wrapper), so baking the names and wrappers
     * covers them glyph by glyph.
     *
     * <p>Portal labels (dimension identifiers) and the fold-label block-position fallback
     * ("x, y, z") are pure ASCII -- but ASCII bitmap glyphs stitch lazily on first use like any
     * other, and a first-open schematic build can be the first code anywhere to measure them
     * (doing so on the builder thread throws and drops the whole dimension to the geographic
     * fallback layout). Baking the full printable ASCII range here closes that hole for every
     * present and future ASCII composite.
     */
    private static void preBakeLabelGlyphs(Font font, FullRouteMapSourceSnapshot source) {
        Set<String> labelTexts = new HashSet<>();
        for (StationGroup station : source.stationGroups()) {
            labelTexts.add(station.primaryName());
        }
        for (FoldAnchorNode foldAnchor : source.foldAnchors()) {
            labelTexts.add(foldAnchor.displayName());
        }
        labelTexts.add(Component.translatable("screen.superpipeslide.full_map.cluster_fallback_name").getString());
        labelTexts.add(Component.translatable("screen.superpipeslide.full_map.cluster_more", "", 0).getString());
        labelTexts.add(ASCII_PRINTABLE);
        for (String text : labelTexts) {
            if (!text.isBlank()) {
                font.width(text);
            }
        }
    }

    /**
     * Picks up the in-flight build's result, if it has finished, and atomically swaps the
     * cached graphs. Runs on the render thread (via refresh) so the builder thread never
     * writes the cached fields itself. On failure the previous graphs are kept and the
     * revision markers are reset so a later refresh retries through the debounce path.
     */
    private static boolean applyFinishedBuild() {
        BuildTask task = inFlightBuild;
        if (task == null || !task.future.isDone()) {
            return false;
        }
        inFlightBuild = null;
        BuildResult result;
        try {
            result = task.future.join();
        } catch (CancellationException exception) {
            // Superseded or explicitly cancelled build; nothing to apply and nothing to
            // retry -- revision state belongs to whatever build comes next.
            return false;
        } catch (CompletionException exception) {
            if (task.cancelled.get() || exception.getCause() instanceof CancellationException) {
                return false;
            }
            LOGGER.warn(
                    "Full route map build failed for dimensions {}; keeping the previous map",
                    task.source.stationGroups().stream().map(StationGroup::levelKey).distinct().toList(),
                    exception);
            cachedRouteRevision = Long.MIN_VALUE;
            cachedPipeRevision = Long.MIN_VALUE;
            dirtySinceMillis = System.currentTimeMillis();
            return false;
        }
        cachedGraphs = result.graphs();
        cachedVisualGraphs = applyQualityGate(result.visualGraphs(), task.source);
        cachedPhysicalGraphs = result.physicalGraphs();
        cachedRouteRevision = task.source.routeRevision();
        cachedPipeRevision = task.source.pipeRevision();
        builtAtMillis = System.currentTimeMillis();
        builtOnce = true;
        return true;
    }

    /**
     * Conservative quality gate executed where a finished build is swapped into the cache. When
     * the build ran against the same input revisions as the graphs currently cached, a
     * dimension's new visual graph is adopted only if it does not increase the hard conflict
     * count (edge crossings + node overlaps) over the cached graph; on a regression the previous
     * graph is kept. Solvers warm-start from the previous layout, so identical input can still
     * solve to a worse arrangement, and keeping the strictly better old layout beats churning
     * the map. The comparison is a pure function of the two quality reports, so the gate is
     * deterministic. Builds for changed revisions bypass the gate entirely because the cached
     * graphs no longer describe the same input.
     */
    private static Map<ResourceKey<Level>, VisualRouteMapGraph> applyQualityGate(Map<ResourceKey<Level>, VisualRouteMapGraph> newGraphs, FullRouteMapSourceSnapshot source) {
        if (newGraphs.isEmpty() || cachedVisualGraphs.isEmpty()
                || source.routeRevision() != cachedRouteRevision
                || source.pipeRevision() != cachedPipeRevision) {
            return newGraphs;
        }
        Map<ResourceKey<Level>, VisualRouteMapGraph> gated = new LinkedHashMap<>(newGraphs);
        for (Map.Entry<ResourceKey<Level>, VisualRouteMapGraph> entry : newGraphs.entrySet()) {
            VisualRouteMapGraph previous = cachedVisualGraphs.get(entry.getKey());
            if (previous == null) {
                continue;
            }
            int previousConflicts = previous.quality().edgeCrossingCount() + previous.quality().nodeOverlapCount();
            int newConflicts = entry.getValue().quality().edgeCrossingCount() + entry.getValue().quality().nodeOverlapCount();
            if (newConflicts > previousConflicts) {
                gated.put(entry.getKey(), previous);
                LOGGER.info(
                        "Keeping the previous full route map layout for dimension {}: the rebuild regressed (crossings + overlaps {} -> {})",
                        entry.getKey(),
                        previousConflicts,
                        newConflicts);
            }
        }
        return gated;
    }

    private static void cancelInFlightBuild() {
        BuildTask task = inFlightBuild;
        if (task != null) {
            inFlightBuild = null;
            task.cancel();
        }
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

    private static Map<ResourceKey<Level>, VisualRouteMapGraph> buildVisualGraphs(
            Map<ResourceKey<Level>, MapDimensionGraph> graphs,
            Map<ResourceKey<Level>, VisualRouteMapGraph> previousVisualGraphs,
            FullRouteMapLayoutMode mode,
            BooleanSupplier cancellation,
            LabelWidthMeasurer labelWidthMeasurer) {
        Map<ResourceKey<Level>, VisualRouteMapGraph> visualGraphs = new LinkedHashMap<>();
        SchematicLayoutConfig config = SchematicLayoutConfig.forMode(mode);
        for (MapDimensionGraph graph : graphs.values()) {
            if (cancellation.getAsBoolean()) {
                throw new CancellationException("Full route map build cancelled");
            }
            try {
                SchematicInputGraph input = new SchematicInputBuilder(graph, config).build().withLabelWidthMeasurer(labelWidthMeasurer);
                Optional<VisualRouteMapGraphSnapshot> previous = Optional.ofNullable(previousVisualGraphs.get(graph.levelKey())).map(VisualRouteMapGraphSnapshot::of);
                visualGraphs.put(graph.levelKey(), solverFor(mode).solve(input, config, previous).graph());
            } catch (CancellationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                LOGGER.warn("Full route map schematic solver failed for dimension {}; using fallback layout", graph.levelKey(), exception);
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
                .map(node -> new VisualLabel(node.id(), node.label(), node.x() + 18.0D, node.z() - 6.0D, node.importance(), 0.68D, true, LabelSlot.RIGHT_NEAR))
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
                FullRouteMapConfig.SCHEMATIC_SOLVER_VERSION);
    }

    private record BuildResult(
            Map<ResourceKey<Level>, MapDimensionGraph> graphs,
            Map<ResourceKey<Level>, VisualRouteMapGraph> visualGraphs,
            Map<ResourceKey<Level>, PhysicalRouteMapGraph> physicalGraphs) {}

    /**
     * One asynchronous build submitted to {@link #MAP_BUILD_EXECUTOR}. Cancellation is
     * cooperative: {@link #cancel()} raises a flag the builders poll (and completes the
     * future without interrupting the thread, since the schematic solver does not respond
     * to interrupts), and the task's result is discarded once it has been detached from
     * {@link #inFlightBuild}.
     */
    private static final class BuildTask {
        private final FullRouteMapSourceSnapshot source;
        private final FullRouteMapLayoutMode mode;
        private final Map<ResourceKey<Level>, VisualRouteMapGraph> previousVisualGraphs;
        private final LabelWidthMeasurer labelWidthMeasurer;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final CompletableFuture<BuildResult> future;

        private BuildTask(FullRouteMapSourceSnapshot source, FullRouteMapLayoutMode mode, Map<ResourceKey<Level>, VisualRouteMapGraph> previousVisualGraphs, LabelWidthMeasurer labelWidthMeasurer) {
            this.source = source;
            this.mode = mode;
            this.previousVisualGraphs = previousVisualGraphs;
            this.labelWidthMeasurer = labelWidthMeasurer;
            this.future = CompletableFuture.supplyAsync(this::run, MAP_BUILD_EXECUTOR);
        }

        private BuildResult run() {
            BooleanSupplier cancellation = this.cancelled::get;
            Map<ResourceKey<Level>, MapDimensionGraph> graphs = new FullRouteMapBuilder(this.source, cancellation).build();
            if (this.mode.physical()) {
                Map<ResourceKey<Level>, PhysicalRouteMapGraph> physicalGraphs = new PhysicalRouteMapGraphBuilder(this.source, cancellation).build(graphs.keySet());
                return new BuildResult(graphs, Map.of(), physicalGraphs);
            }
            return new BuildResult(graphs, buildVisualGraphs(graphs, this.previousVisualGraphs, this.mode, cancellation, this.labelWidthMeasurer), Map.of());
        }

        private void cancel() {
            this.cancelled.set(true);
            this.future.cancel(false);
        }
    }
}
