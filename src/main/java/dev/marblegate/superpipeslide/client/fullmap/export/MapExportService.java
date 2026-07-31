package dev.marblegate.superpipeslide.client.fullmap.export;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.fullmap.cache.FullRouteMapCache;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapLayoutMode;
import dev.marblegate.superpipeslide.client.fullmap.render.FullRouteMapRenderer;
import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapText;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

/**
 * Orchestrates a schematic map export: validates the request against the cache and the
 * GPU, then renders the export jobs one at a time on the render thread — each image's GPU
 * readback and PNG write complete asynchronously before the next job starts, so at most
 * one large offscreen target is alive at any moment.
 *
 * <p>All public methods must be called on the render thread.
 */
public final class MapExportService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean exportInProgress;
    private static int maxTextureSize = -1;

    private MapExportService() {}

    /** Summary of a finished export run. */
    public record Result(int fileCount, int successCount, Path outputDirectory) {
        public boolean allSucceeded() {
            return this.fileCount == this.successCount;
        }
    }

    public static boolean exportInProgress() {
        return exportInProgress;
    }

    /** The GPU texture size limit, queried lazily on the render thread. */
    public static int maxTextureSize() {
        if (maxTextureSize <= 0) {
            maxTextureSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        }
        return maxTextureSize;
    }

    /**
     * Physical pixels per logical map pixel for the given resolution multiplier: the window's
     * current GUI scale (so {@code 1.0} matches the on-screen map exactly) times the multiplier.
     */
    public static double pixelScale(double resolutionMultiplier) {
        return Minecraft.getInstance().getWindow().getGuiScale() * resolutionMultiplier;
    }

    /**
     * Returns a {@code screen.superpipeslide.full_map.export.*} error key suffix describing
     * why an export cannot start, or {@code null} when it can.
     */
    public static @Nullable String validateForExport(MapExportOptions options, MapExportPlan plan) {
        if (FullRouteMapCache.layoutMode() != FullRouteMapLayoutMode.SCHEMATIC) {
            return "error.wrong_mode";
        }
        if (FullRouteMapCache.building()) {
            return "error.building";
        }
        if (!options.isValid()) {
            return "error.incomplete";
        }
        if (plan.isEmpty()) {
            return "error.no_content";
        }
        double pixelScale = pixelScale(options.resolutionMultiplier());
        for (double zoom : options.zoomLevels()) {
            if (plan.estimateMaxSpan(zoom, pixelScale) > maxTextureSize()) {
                return "error.too_large";
            }
        }
        return null;
    }

    /**
     * Runs the export. Assumes {@link #validateForExport} passed. {@code onDone} is invoked
     * on the render thread once every file's readback and write has settled.
     */
    public static void export(MapExportOptions options, Consumer<Result> onDone) {
        RenderSystem.assertOnRenderThread();
        if (exportInProgress) {
            return;
        }
        MapExportPlan plan = MapExportPlan.build(options);
        Path outputDirectory = Minecraft.getInstance().gameDirectory.toPath().resolve("superpipeslide").resolve("map-exports");
        List<MapExportPlan.Item> items = plan.items(routeSegment(options), Util.getFilenameFormattedDateTime());
        // One job per output file: each plan item renders once per requested background variant.
        List<ExportJob> jobs = new ArrayList<>();
        for (MapExportPlan.Item item : items) {
            if (options.backgroundMode().includesOpaque()) {
                jobs.add(new ExportJob(item, true));
            }
            if (options.backgroundMode().includesTransparent()) {
                jobs.add(new ExportJob(item, false));
            }
        }
        if (jobs.isEmpty()) {
            onDone.accept(new Result(0, 0, outputDirectory));
            return;
        }
        exportInProgress = true;
        AtomicInteger succeeded = new AtomicInteger();
        double pixelScale = pixelScale(options.resolutionMultiplier());
        renderJob(jobs, 0, new FullRouteMapRenderer(), pixelScale, outputDirectory, succeeded, () -> {
            Result result = new Result(jobs.size(), succeeded.get(), outputDirectory);
            Minecraft.getInstance().execute(() -> {
                exportInProgress = false;
                onDone.accept(result);
            });
        });
    }

    /** One output file to render: a plan item with a chosen background variant. */
    private record ExportJob(MapExportPlan.Item item, boolean opaque) {}

    private static void renderJob(List<ExportJob> jobs, int index, FullRouteMapRenderer mapRenderer, double pixelScale, Path outputDirectory, AtomicInteger succeeded,
            Runnable allDone) {
        if (index >= jobs.size()) {
            allDone.run();
            return;
        }
        ExportJob job = jobs.get(index);
        MapExportPlan.Item item = job.item();
        OffscreenGuiRenderer renderer = null;
        try {
            renderer = new OffscreenGuiRenderer(item.logicalWidth(), item.logicalHeight(), pixelScale);
            GuiGraphicsExtractor graphics = renderer.beginFrame();
            mapRenderer.renderPureSchematicDirect(graphics, Minecraft.getInstance().font, item.graphs().dataGraph(), item.graphs().visualGraph(), item.viewport(),
                    new SPSGui.Rect(0, 0, item.logicalWidth(), item.logicalHeight()), job.opaque());
            OffscreenGuiRenderer flushing = renderer;
            Path outFile = outputDirectory.resolve(item.baseFileName() + (job.opaque() ? ".png" : "_transparent.png"));
            renderer.flushToPng(outFile, success -> {
                if (success) {
                    succeeded.incrementAndGet();
                }
                // The write finished on the IO pool; hop back to the render thread to
                // release the GPU resources and continue the chain.
                Minecraft.getInstance().execute(() -> {
                    flushing.close();
                    renderJob(jobs, index + 1, mapRenderer, pixelScale, outputDirectory, succeeded, allDone);
                });
            });
        } catch (RuntimeException exception) {
            if (renderer != null) {
                renderer.close();
            }
            LOGGER.warn("Map export render failed for {}", item.baseFileName(), exception);
            renderJob(jobs, index + 1, mapRenderer, pixelScale, outputDirectory, succeeded, allDone);
        }
    }

    /** Short file-name segment describing the route selection: the line name, "all", or a count. */
    private static String routeSegment(MapExportOptions options) {
        Collection<RouteLine> allLines = ClientRouteDataCache.routeLines();
        if (!allLines.isEmpty() && options.routeLineIds().size() >= allLines.size() && allLines.stream().allMatch(line -> options.routeLineIds().contains(line.id()))) {
            return "all";
        }
        if (options.routeLineIds().size() == 1) {
            UUID id = options.routeLineIds().iterator().next();
            return ClientRouteDataCache.routeLine(id)
                    .map(line -> MapExportPlan.sanitize(FullMapText.primaryName(line)))
                    .filter(segment -> segment.chars().anyMatch(ch -> ch != '-'))
                    .orElse("route");
        }
        return "selected-" + options.routeLineIds().size();
    }
}
