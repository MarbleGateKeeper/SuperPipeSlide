package dev.marblegate.superpipeslide.client.fullmap.export;

import dev.marblegate.superpipeslide.client.fullmap.cache.FullRouteMapCache;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.model.MapDimensionGraph;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.ViewportState;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualRouteMapGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The concrete work plan of one export run: the route-line-filtered graphs of every
 * dimension that has content for the selected lines, flattened into one {@link Item} per
 * (dimension × zoom level) with its fitted viewport, logical size and output file name base.
 *
 * <p>Dimensions are independent schematic graphs, so a cross-dimension route exports as
 * one image per dimension it runs through; the portal nodes inside each image carry the
 * continuation into the other dimensions.
 *
 * <p>Item sizes are <b>logical</b> (GUI-space) pixels: layout units ×
 * {@link FullRouteMapConfig#BASE_SCALE} × zoom. The physical PNG size additionally scales with the
 * export pixel scale (window GUI scale × resolution multiplier), applied by the off-screen
 * renderer.
 */
public record MapExportPlan(List<MapExportPlan.Entry> entries, List<Double> zoomLevels) {
    public MapExportPlan {
        entries = List.copyOf(entries);
        zoomLevels = List.copyOf(zoomLevels);
    }

    /** The filtered graphs of one exportable dimension. */
    public record Entry(ResourceKey<Level> levelKey, SchematicGraphFilter.Filtered graphs) {}

    /** One image to render: a dimension at one zoom level. {@code baseFileName} carries no extension. */
    public record Item(ResourceKey<Level> levelKey, SchematicGraphFilter.Filtered graphs, double zoom, ViewportState viewport, int logicalWidth,
            int logicalHeight, String baseFileName) {}

    /**
     * Builds the plan from the currently cached graphs. Dimensions without cached graphs
     * or without content for the selected lines are skipped. Returns an empty plan when
     * nothing would be exported.
     */
    public static MapExportPlan build(MapExportOptions options) {
        List<Entry> entries = new ArrayList<>();
        if (!options.isValid()) {
            return new MapExportPlan(entries, List.of());
        }
        for (ResourceKey<Level> levelKey : FullRouteMapCache.dimensions()) {
            Optional<MapDimensionGraph> graph = FullRouteMapCache.graph(levelKey);
            Optional<VisualRouteMapGraph> visualGraph = FullRouteMapCache.visualGraph(levelKey);
            if (graph.isEmpty() || visualGraph.isEmpty()) {
                continue;
            }
            SchematicGraphFilter.Filtered filtered = SchematicGraphFilter.filter(graph.get(), visualGraph.get(), options.routeLineIds());
            if (!filtered.isEmpty()) {
                entries.add(new Entry(levelKey, filtered));
            }
        }
        return new MapExportPlan(entries, options.zoomLevels());
    }

    public boolean isEmpty() {
        return this.entries.isEmpty() || this.zoomLevels.isEmpty();
    }

    /**
     * The largest physical image side in pixels that the given zoom level would produce in this
     * plan when rasterized at the given pixel scale.
     */
    public int estimateMaxSpan(double zoom, double pixelScale) {
        int span = 0;
        for (Entry entry : this.entries) {
            Aabb2 bounds = entry.graphs().visualGraph().visualBounds();
            span = Math.max(span, (int) Math.ceil(pixelSpan(bounds.maxX() - bounds.minX(), zoom) * pixelScale));
            span = Math.max(span, (int) Math.ceil(pixelSpan(bounds.maxY() - bounds.minY(), zoom) * pixelScale));
        }
        return span;
    }

    /** Flattens the plan into render items. {@code routeSegment} and {@code timestamp} decorate the file name bases. */
    public List<Item> items(String routeSegment, String timestamp) {
        List<Item> items = new ArrayList<>();
        for (Entry entry : this.entries) {
            Aabb2 bounds = entry.graphs().visualGraph().visualBounds();
            for (double zoom : this.zoomLevels) {
                int logicalWidth = Math.max(1, pixelSpan(bounds.maxX() - bounds.minX(), zoom));
                int logicalHeight = Math.max(1, pixelSpan(bounds.maxY() - bounds.minY(), zoom));
                ViewportState viewport = new ViewportState(entry.levelKey(), bounds.centerX(), bounds.centerY(), zoom);
                String baseFileName = "routemap_" + sanitize(entry.levelKey().identifier().getPath().replace('/', '-')) + "_" + routeSegment + "_z"
                        + zoomString(zoom) + "_" + timestamp;
                items.add(new Item(entry.levelKey(), entry.graphs(), zoom, viewport, logicalWidth, logicalHeight, baseFileName));
            }
        }
        return items;
    }

    /** The logical (GUI-space) span of a layout span at the given zoom. */
    private static int pixelSpan(double layoutSpan, double zoom) {
        return (int) Math.ceil(layoutSpan * FullRouteMapConfig.BASE_SCALE * zoom);
    }

    static String zoomString(double zoom) {
        return zoom == Math.floor(zoom) && !Double.isInfinite(zoom) ? Integer.toString((int) zoom) : Double.toString(zoom);
    }

    static String sanitize(String raw) {
        StringBuilder sanitized = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = Character.toLowerCase(raw.charAt(i));
            sanitized.append(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-' || c == '_' ? c : '-');
        }
        return sanitized.toString();
    }
}
