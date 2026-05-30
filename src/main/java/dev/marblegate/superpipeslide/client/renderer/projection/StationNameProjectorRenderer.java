package dev.marblegate.superpipeslide.client.renderer.projection;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.marblegate.superpipeslide.client.core.projection.cache.ProjectionBuiltinIconTextureCache;
import dev.marblegate.superpipeslide.client.core.projection.cache.ProjectionNetworkImageCache;
import dev.marblegate.superpipeslide.client.core.projection.cache.ProjectionTextMeasureCache;
import dev.marblegate.superpipeslide.client.core.projection.render.ProjectionQuadClipper;
import dev.marblegate.superpipeslide.client.core.projection.render.ProjectionRenderFrameContext;
import dev.marblegate.superpipeslide.client.core.projection.render.ProjectionTextScroller;
import dev.marblegate.superpipeslide.client.core.projection.render.ProjectionWorldTextRenderer;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.renderer.ClientRenderCompatibility;
import dev.marblegate.superpipeslide.common.block.station.StationNameProjectorBlock;
import dev.marblegate.superpipeslide.common.block.station.StationNameProjectorBlockEntity;
import dev.marblegate.superpipeslide.common.block.station.StationNameProjectorConfig;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionBuiltinIcon;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponent;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponentSettings;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponentType;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionOverflowMode;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionTextAlign;
import dev.marblegate.superpipeslide.common.core.projection.layout.AppliedProjectionLayout;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionImageLayout;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.config.ClientConfig;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

public final class StationNameProjectorRenderer {
    private static final double RENDER_DISTANCE = 128.0D;
    private static final int CHUNK_RADIUS = 9;
    private static final float SURFACE_Z_BASE = 0.00028F;
    private static final float TEXTURE_Z_BASE = 0.00044F;
    private static final float TEXT_Z_BASE = 0.00060F;
    private static final float LAYER_Z_STEP = 0.00042F;
    private static final float SURFACE_BORDER_Z = 0.00023F;
    private static final float SURFACE_OVERLAY_Z = 0.00026F;
    private static final ThreadLocal<CanvasBounds> TEXT_CANVAS_BOUNDS = new ThreadLocal<>();
    private static final int INFO_CACHE_MAX_ENTRIES = 512;
    private static final Map<StationInfoCacheKey, StationRenderInfo> INFO_CACHE = new LinkedHashMap<>(64, 0.75F, true);
    private static final int STATIC_CACHE_MAX_ENTRIES = 1024;
    private static final long STATIC_CACHE_RETAIN_FRAMES = 600L;
    private static final Map<StaticProjectionKey, StaticProjectionEntry> STATIC_CACHE = new LinkedHashMap<>(64, 0.75F, true);
    private static final int DYNAMIC_CACHE_MAX_ENTRIES = 512;
    private static final long DYNAMIC_CACHE_RETAIN_FRAMES = 80L;
    private static final Map<DynamicProjectionKey, StaticProjectionEntry> DYNAMIC_CACHE = new LinkedHashMap<>(64, 0.75F, true);
    private static long lastRouteRevision = Long.MIN_VALUE;
    private static long staticRenderFrame;
    private static RenderData lastRenderData = RenderData.EMPTY;
    private static final RenderPipeline PROJECTION_TRANSLUCENT_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipeline/station_name_projection_translucent"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
            .withCull(false)
            .build();
    private static final RenderPipeline PROJECTION_TEXTURE_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipeline/station_name_projection_texture"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withSampler("Sampler0")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
            .withCull(false)
            .build();
    private static final RenderType PROJECTION_TRANSLUCENT_QUADS = RenderType.create(
            "superpipeslide_station_name_projector_translucent_quads",
            RenderSetup.builder(PROJECTION_TRANSLUCENT_PIPELINE).bufferSize(4096).createRenderSetup()
    );
    private static final Map<Identifier, RenderType> PROJECTION_TEXTURE_QUADS = new LinkedHashMap<>();

    private StationNameProjectorRenderer() {
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(PROJECTION_TRANSLUCENT_PIPELINE);
        event.registerPipeline(PROJECTION_TEXTURE_PIPELINE);
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        ClientLevel level = event.getLevel();
        Vec3 camera = event.getCamera().position();
        Frustum frustum = event.getFrustum();
        int centerChunkX = (int) Math.floor(camera.x / 16.0D);
        int centerChunkZ = (int) Math.floor(camera.z / 16.0D);
        List<ProjectorData> projectors = new ArrayList<>();

        ClientProjectionProjectorIndex.forStationNameProjectors(level, centerChunkX, centerChunkZ, CHUNK_RADIUS, projector -> {
            Direction facing = projector.getBlockState().getValue(StationNameProjectorBlock.FACING);
            StationNameProjectorConfig config = projector.config();
            AppliedProjectionLayout layout = projector.appliedLayout();
            Vec3 center = projectionCenter(projector.getBlockPos(), facing, config);
            boolean frontSide = camera.subtract(center).dot(facing.getUnitVec3()) >= -0.01D;
            if (!frontSide && !config.backsideProjection()) {
                return;
            }
            double panelRadius = Math.hypot(layout.canvas().width(), layout.canvas().height()) * 0.5D + 1.0D;
            if (camera.distanceToSqr(center) <= Math.pow(RENDER_DISTANCE + panelRadius, 2.0D) && projectionVisible(center, panelRadius, frustum)) {
                projectors.add(new ProjectorData(projector.getBlockPos(), facing, config, layout, cachedStationInfo(config)));
            }
        });

        RenderData renderData = projectors.isEmpty() ? RenderData.EMPTY : new RenderData(List.copyOf(projectors), camera);
        lastRenderData = renderData;
    }

    public static void renderAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        RenderData renderData = lastRenderData;
        if (renderData.projectors().isEmpty()) {
            return;
        }
        staticRenderFrame++;

        Font font = Minecraft.getInstance().font;
        Vec3 camera = renderData.camera();
        PoseStack poseStack = new PoseStack();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(event.getModelViewMatrix());
        modelViewStack.translate((float) -camera.x, (float) -camera.y, (float) -camera.z);
        try (ByteBufferBuilder buffer = new ByteBufferBuilder(RenderType.BIG_BUFFER_SIZE)) {
            MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(buffer);
            ImmediateSubmitNodeCollector collector = new ImmediateSubmitNodeCollector(bufferSource);
            for (ProjectorData projector : renderData.projectors()) {
                submitProjector(collector, poseStack, font, projector, camera);
            }
            bufferSource.endBatch();
        } finally {
            modelViewStack.popMatrix();
        }
        trimStaticCache();
    }

    static boolean hasQueuedProjectors() {
        return !lastRenderData.projectors().isEmpty();
    }

    static Vec3 queuedCamera() {
        return lastRenderData.camera();
    }

    static void submitQueued(SubmitNodeCollector collector, PoseStack poseStack, Font font, Vec3 camera) {
        RenderData renderData = lastRenderData;
        if (renderData.projectors().isEmpty()) {
            return;
        }
        staticRenderFrame++;
        for (ProjectorData projector : renderData.projectors()) {
            submitProjector(collector, poseStack, font, projector, camera);
        }
        trimStaticCache();
    }

    private static void submitProjector(SubmitNodeCollector collector, PoseStack poseStack, Font font, ProjectorData projector, Vec3 camera) {
        StationNameProjectorConfig config = projector.config();
        AppliedProjectionLayout layout = projector.layout();
        float width = layout.canvas().width();
        float height = layout.canvas().height();
        float brightness = 1.0F;
        Vec3 center = projectionCenter(projector.pos(), projector.facing(), config);
        boolean frontSide = camera.subtract(center).dot(projector.facing().getUnitVec3()) >= -0.01D;
        if (!frontSide && !config.backsideProjection()) {
            return;
        }

        StaticProjectionKey staticKey = staticKey(projector, frontSide, width, height);
        if (staticKey != null) {
            StaticProjectionEntry entry = STATIC_CACHE.get(staticKey);
            if (entry != null && !entry.batches.validForCurrentRenderState()) {
                entry.release();
                STATIC_CACHE.remove(staticKey);
                entry = null;
            }
            if (entry == null) {
                entry = new StaticProjectionEntry(compileStaticProjector(projector, frontSide, width, height, brightness));
                STATIC_CACHE.put(staticKey, entry);
            }
            entry.lastUsedFrame = staticRenderFrame;
            entry.batches.draw();
        }

        DynamicProjectionKey dynamicKey = dynamicKey(projector, frontSide, width, height);
        if (dynamicKey != null) {
            StaticProjectionEntry entry = DYNAMIC_CACHE.get(dynamicKey);
            if (entry != null && !entry.batches.validForCurrentRenderState()) {
                entry.release();
                DYNAMIC_CACHE.remove(dynamicKey);
                entry = null;
            }
            if (entry == null) {
                entry = new StaticProjectionEntry(compileDynamicProjector(projector, frontSide, width, height, brightness));
                DYNAMIC_CACHE.put(dynamicKey, entry);
            }
            entry.lastUsedFrame = staticRenderFrame;
            entry.batches.draw();
        }

        PoseStack textPoseStack = new PoseStack();
        applyProjectorTransform(textPoseStack, projector.pos(), projector.facing(), projector.config(), frontSide);
        renderProjectionText(textPoseStack, collector, font, projector.config(), projector.layout(), projector.stationInfo(), width, height, brightness);
    }

    public static void clearStaticCache() {
        for (StaticProjectionEntry entry : STATIC_CACHE.values()) {
            entry.release();
        }
        STATIC_CACHE.clear();
        for (StaticProjectionEntry entry : DYNAMIC_CACHE.values()) {
            entry.release();
        }
        DYNAMIC_CACHE.clear();
        PROJECTION_TEXTURE_QUADS.clear();
    }

    public static void clearCaches() {
        clearStaticCache();
        INFO_CACHE.clear();
        lastRouteRevision = Long.MIN_VALUE;
    }

    private static StaticProjectionKey staticKey(ProjectorData projector, boolean frontSide, float width, float height) {
        if (!hasStaticRenderableContent(projector.layout(), projector.stationInfo(), projector.config())) {
            return null;
        }
        return new StaticProjectionKey(
                projector.pos(),
                projector.facing(),
                frontSide,
                projector.config(),
                width,
                height,
                projector.layout(),
                projector.stationInfo(),
                ClientRouteDataCache.revision(),
                networkImageStates(projector.layout(), true)
        );
    }

    private static DynamicProjectionKey dynamicKey(ProjectorData projector, boolean frontSide, float width, float height) {
        List<DynamicComponentPhase> phases = dynamicComponentPhases(projector.layout(), projector.stationInfo(), projector.config());
        if (phases.isEmpty()) {
            return null;
        }
        return new DynamicProjectionKey(
                projector.pos(),
                projector.facing(),
                frontSide,
                projector.config(),
                width,
                height,
                projector.layout(),
                projector.stationInfo(),
                ClientRouteDataCache.revision(),
                phases
        );
    }

    private static boolean hasStaticRenderableContent(AppliedProjectionLayout layout, StationRenderInfo info, StationNameProjectorConfig config) {
        if (layout.invalid() || info.missing()) {
            return true;
        }
        for (ProjectionComponent component : layout.components()) {
            if (!shouldRenderComponent(component, info, config)) {
                continue;
            }
            if (!componentSurfaceDynamic(component, info, config)) {
                return true;
            }
        }
        return false;
    }

    private static boolean componentSurfaceDynamic(ProjectionComponent component, StationRenderInfo info, StationNameProjectorConfig config) {
        ProjectionComponentSettings settings = component.settings();
        return switch (component.type()) {
            case ROUTE_LIST -> settings instanceof ProjectionComponentSettings.RouteList routeList
                    && routeList.overflow() == ProjectionComponentSettings.RouteOverflowMode.ROTATE && info.routes().size() > routeList.maxVisible();
            case ROUTE_ICONS, ROUTE_OUTLINE_ICONS -> settings instanceof ProjectionComponentSettings.RouteIcon routeIcon
                    && routeIcon.overflow() == ProjectionComponentSettings.RouteOverflowMode.ROTATE && info.routes().size() > routeIcon.maxVisible();
            case ROUTE_CAPSULES -> settings instanceof ProjectionComponentSettings.RouteCapsules routeCapsules
                    && routeCapsules.overflow() == ProjectionComponentSettings.RouteOverflowMode.ROTATE && info.routes().size() > routeCapsules.maxVisible();
            case ROUTE_BACKPLATE -> settings instanceof ProjectionComponentSettings.RouteBackplate routeBackplate
                    && routeBackplate.overflow() == ProjectionComponentSettings.RouteOverflowMode.ROTATE && info.routes().size() > routeBackplate.maxVisible();
            case NETWORK_IMAGE -> settings instanceof ProjectionComponentSettings.NetworkImage image && networkImageSurfaceDynamic(image);
            default -> false;
        };
    }

    private static List<NetworkImageStateKey> networkImageStates(AppliedProjectionLayout layout, boolean staticOnly) {
        if (layout.invalid()) {
            return List.of();
        }
        List<NetworkImageStateKey> result = new ArrayList<>();
        for (ProjectionComponent component : layout.components()) {
            if (component.settings() instanceof ProjectionComponentSettings.NetworkImage image) {
                if (staticOnly && networkImageSurfaceDynamic(image)) {
                    continue;
                }
                ProjectionNetworkImageCache.State state = ProjectionRenderFrameContext.networkImageState(image.url());
                result.add(new NetworkImageStateKey(image.url(), state.status(), state.textureId(), state.width(), state.height()));
            }
        }
        return List.copyOf(result);
    }

    private static boolean networkImageSurfaceDynamic(ProjectionComponentSettings.NetworkImage image) {
        ProjectionNetworkImageCache.State state = ProjectionRenderFrameContext.networkImageState(image.url());
        return !state.ready() && shouldShowNetworkPlaceholder(image, state);
    }

    private static List<DynamicComponentPhase> dynamicComponentPhases(AppliedProjectionLayout layout, StationRenderInfo info, StationNameProjectorConfig config) {
        if (layout.invalid() || info.missing()) {
            return List.of();
        }
        List<DynamicComponentPhase> phases = new ArrayList<>();
        long frameTimeMillis = ProjectionRenderFrameContext.timeMillis();
        float width = layout.canvas().width();
        float height = layout.canvas().height();
        for (ProjectionComponent component : layout.components()) {
            if (!shouldRenderComponent(component, info, config) || !componentSurfaceDynamic(component, info, config)) {
                continue;
            }
            ProjectionComponentSettings settings = component.settings();
            if (settings instanceof ProjectionComponentSettings.RouteList routeList) {
                phases.add(DynamicComponentPhase.phase(component.id(), routePhase(routeList.overflow(), routeList.maxVisible(), info.routes().size(), routeList.rotateIntervalTicks(), frameTimeMillis, componentRect(component, width, height).hashCode())));
            } else if (settings instanceof ProjectionComponentSettings.RouteIcon routeIcon) {
                phases.add(DynamicComponentPhase.phase(component.id(), routePhase(routeIcon.overflow(), routeIcon.maxVisible(), info.routes().size(), routeIcon.rotateIntervalTicks(), frameTimeMillis, componentRect(component, width, height).hashCode())));
            } else if (settings instanceof ProjectionComponentSettings.RouteCapsules routeCapsules) {
                phases.add(DynamicComponentPhase.phase(component.id(), routePhase(routeCapsules.overflow(), routeCapsules.maxVisible(), info.routes().size(), routeCapsules.rotateIntervalTicks(), frameTimeMillis, componentRect(component, width, height).hashCode())));
            } else if (settings instanceof ProjectionComponentSettings.RouteBackplate routeBackplate) {
                phases.add(DynamicComponentPhase.phase(component.id(), routePhase(routeBackplate.overflow(), routeBackplate.maxVisible(), info.routes().size(), routeBackplate.rotateIntervalTicks(), frameTimeMillis, component.id().hashCode())));
            } else if (settings instanceof ProjectionComponentSettings.NetworkImage image) {
                ProjectionNetworkImageCache.State state = ProjectionRenderFrameContext.networkImageState(image.url());
                phases.add(DynamicComponentPhase.network(component.id(), image.url(), state));
            } else {
                phases.add(DynamicComponentPhase.phase(component.id(), frameTimeMillis / 50L));
            }
        }
        return List.copyOf(phases);
    }

    private static long routePhase(ProjectionComponentSettings.RouteOverflowMode overflow, int maxVisible, int routeCount, int intervalTicks, long frameTimeMillis, int seed) {
        if (overflow != ProjectionComponentSettings.RouteOverflowMode.ROTATE || routeCount <= Math.max(1, maxVisible)) {
            return 0L;
        }
        return frameTimeMillis / routeIntervalMillis(intervalTicks) + seed;
    }

    private static long routeIntervalMillis(int intervalTicks) {
        return Math.max(10L, intervalTicks) * 50L;
    }

    private static void trimStaticCache() {
        Iterator<Map.Entry<StaticProjectionKey, StaticProjectionEntry>> iterator = STATIC_CACHE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<StaticProjectionKey, StaticProjectionEntry> entry = iterator.next();
            if (STATIC_CACHE.size() > STATIC_CACHE_MAX_ENTRIES || staticRenderFrame - entry.getValue().lastUsedFrame > STATIC_CACHE_RETAIN_FRAMES) {
                entry.getValue().release();
                iterator.remove();
            }
        }
        Iterator<Map.Entry<DynamicProjectionKey, StaticProjectionEntry>> dynamicIterator = DYNAMIC_CACHE.entrySet().iterator();
        while (dynamicIterator.hasNext()) {
            Map.Entry<DynamicProjectionKey, StaticProjectionEntry> entry = dynamicIterator.next();
            if (DYNAMIC_CACHE.size() > DYNAMIC_CACHE_MAX_ENTRIES || staticRenderFrame - entry.getValue().lastUsedFrame > DYNAMIC_CACHE_RETAIN_FRAMES) {
                entry.getValue().release();
                dynamicIterator.remove();
            }
        }
    }

    private static void applyProjectorTransform(PoseStack poseStack, BlockPos pos, Direction facing, StationNameProjectorConfig config, boolean frontSide) {
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        translateToProjectionPlane(poseStack, facing, config);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        if (!frontSide) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        }
    }

    private static ProjectionGpuBatchCache.GpuBatches compileStaticProjector(ProjectorData projector, boolean frontSide, float width, float height, float brightness) {
        return ProjectionGpuBatchCache.compile(collector -> {
            PoseStack poseStack = new PoseStack();
            applyProjectorTransform(poseStack, projector.pos(), projector.facing(), projector.config(), frontSide);
            ClientRenderCompatibility.submitCustomGeometry(collector, poseStack, PROJECTION_TRANSLUCENT_QUADS, (pose, buffer) -> renderProjectionSurfaces(pose, buffer, Minecraft.getInstance().font, projector.config(), projector.layout(), projector.stationInfo(), width, height, brightness, SurfacePass.COLOR, ProjectionBatchMode.STATIC_ONLY));
            renderProjectionTextures(poseStack, collector, projector.config(), projector.layout(), projector.stationInfo(), width, height, brightness, ProjectionBatchMode.STATIC_ONLY);
        });
    }

    private static ProjectionGpuBatchCache.GpuBatches compileDynamicProjector(ProjectorData projector, boolean frontSide, float width, float height, float brightness) {
        return ProjectionGpuBatchCache.compile(collector -> {
            PoseStack poseStack = new PoseStack();
            applyProjectorTransform(poseStack, projector.pos(), projector.facing(), projector.config(), frontSide);
            ClientRenderCompatibility.submitCustomGeometry(collector, poseStack, PROJECTION_TRANSLUCENT_QUADS, (pose, buffer) -> renderProjectionSurfaces(pose, buffer, Minecraft.getInstance().font, projector.config(), projector.layout(), projector.stationInfo(), width, height, brightness, SurfacePass.COLOR, ProjectionBatchMode.DYNAMIC_ONLY));
            renderProjectionTextures(poseStack, collector, projector.config(), projector.layout(), projector.stationInfo(), width, height, brightness, ProjectionBatchMode.DYNAMIC_ONLY);
        });
    }

    private static Vec3 projectionCenter(BlockPos pos, Direction facing, StationNameProjectorConfig config) {
        return ClientProjectionGeometry.projectionCenter(pos, facing, config.offsetX(), config.offsetY());
    }

    private static boolean projectionVisible(Vec3 center, double radius, Frustum frustum) {
        if (frustum == null) {
            return true;
        }
        double safeRadius = Math.max(0.75D, radius);
        AABB bounds = new AABB(
                center.x - safeRadius,
                center.y - safeRadius,
                center.z - safeRadius,
                center.x + safeRadius,
                center.y + safeRadius,
                center.z + safeRadius
        );
        return frustum.isVisible(bounds);
    }

    private static void translateToProjectionPlane(PoseStack poseStack, Direction facing, StationNameProjectorConfig config) {
        ClientProjectionGeometry.translateToProjectionPlane(poseStack, facing, config.offsetX(), config.offsetY());
    }

    private static void renderProjectionSurfaces(PoseStack.Pose pose, VertexConsumer buffer, Font font, StationNameProjectorConfig config, AppliedProjectionLayout layout, StationRenderInfo info, float width, float height, float brightness, SurfacePass pass, ProjectionBatchMode mode) {
        if (layout.invalid()) {
            addRect(pose, buffer, -width * 0.5F, -height * 0.5F, width, height, SURFACE_Z_BASE, multiplyAlpha(0xFF350909, brightness), pass);
            addRect(pose, buffer, -width * 0.5F, -height * 0.5F, width, 0.018F, SURFACE_Z_BASE + SURFACE_OVERLAY_Z, multiplyAlpha(0xFFFF6B6B, brightness), pass);
            addRect(pose, buffer, -width * 0.5F, height * 0.5F - 0.018F, width, 0.018F, SURFACE_Z_BASE + SURFACE_OVERLAY_Z, multiplyAlpha(0xFFFF6B6B, brightness), pass);
            return;
        }
        for (ProjectionComponent component : layout.components()) {
            if (!shouldRenderComponent(component, info, config)) {
                continue;
            }
            boolean dynamic = componentSurfaceDynamic(component, info, config);
            if (!mode.accepts(dynamic)) {
                continue;
            }
            renderComponentSurface(pose, buffer, component, width, height, info, config, brightness, pass);
        }
    }

    private static void renderProjectionText(PoseStack poseStack, SubmitNodeCollector collector, Font font, StationNameProjectorConfig config, AppliedProjectionLayout layout, StationRenderInfo info, float width, float height, float brightness) {
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, TEXT_Z_BASE);
        Matrix4f worldToCanvas = new Matrix4f(poseStack.last().pose()).invert();
        TEXT_CANVAS_BOUNDS.set(CanvasBounds.of(width, height, worldToCanvas));
        try {
            if (layout.invalid()) {
                renderCenteredLine(poseStack, collector, font, Component.translatable("screen.superpipeslide.station_projector.layout_invalid").getString(), 0.03F, width * 0.72F, Math.min(0.045F, height * 0.095F), multiplyAlpha(0xFFFFE2E2, brightness));
                renderCenteredLine(poseStack, collector, font, layout.errorMessage(), -0.13F, width * 0.72F, Math.min(0.023F, height * 0.05F), multiplyAlpha(0xFFFFA0A0, brightness));
                return;
            }
            if (info.missing()) {
                renderCenteredLine(poseStack, collector, font, Component.translatable("screen.superpipeslide.station_projector.unbound").getString(), 0.0F, width * 0.72F, Math.min(0.05F, height * 0.105F), multiplyAlpha(0xFFFFD36A, brightness));
                renderCenteredLine(poseStack, collector, font, Component.translatable("screen.superpipeslide.station_projector.unbound.hint").getString(), -0.16F, width * 0.72F, Math.min(0.026F, height * 0.055F), multiplyAlpha(0xFFBFD4DC, brightness));
                return;
            }
            for (ProjectionComponent component : layout.components()) {
                if (!shouldRenderComponent(component, info, config)) {
                    continue;
                }
                renderComponentText(poseStack, collector, font, component, width, height, info, config, brightness);
            }
        } finally {
            TEXT_CANVAS_BOUNDS.remove();
            poseStack.popPose();
        }
    }

    private static boolean shouldRenderComponent(ProjectionComponent component, StationRenderInfo info, StationNameProjectorConfig config) {
        return switch (component.visibleCondition()) {
            case ALWAYS -> true;
            case HAS_TRANSLATION -> !info.translation().isBlank();
            case HAS_ROUTES -> !info.routes().isEmpty();
            case HAS_EXIT -> config.showExit() && !config.exitLabel().isBlank();
            case MULTI_ROUTE -> info.routes().size() > 1;
            default -> false;
        };
    }

    private static void renderComponentSurface(PoseStack.Pose pose, VertexConsumer buffer, ProjectionComponent component, float width, float height, StationRenderInfo info, StationNameProjectorConfig config, float brightness, SurfacePass pass) {
        ComponentRect rect = componentRect(component, width, height);
        ComponentTransform transform = componentTransform(component, rect, width, height);
        ProjectionComponentSettings settings = component.settings();
        float z = componentZ(component, SURFACE_Z_BASE);
        switch (component.type()) {
            case BACKGROUND_PANEL -> {
                ProjectionComponentSettings.Panel panel = (ProjectionComponentSettings.Panel) settings;
                int fill = withAlpha(panel.fillColor(), Math.round(((panel.fillColor() >>> 24) & 0xFF) * panel.opacity() * brightness));
                if ((fill >>> 24) > 0) {
                    addTransformedRect(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), z, fill, pass);
                }
                if (panel.borderWidth() > 0.001F && ((panel.borderColor() >>> 24) & 0xFF) > 0) {
                    addBorder(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), Math.min(panel.borderWidth(), Math.min(rect.width(), rect.height()) * 0.22F), z + SURFACE_BORDER_Z, withAlphaMultiplier(panel.borderColor(), brightness), pass);
                }
            }
            case EXIT_BADGE -> {
                ProjectionComponentSettings.ExitBadge badge = (ProjectionComponentSettings.ExitBadge) settings;
                if (badge.fillEnabled() && (badge.fillColor() >>> 24) > 0) {
                    addTransformedRect(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), z, withAlphaMultiplier(badge.fillColor(), brightness), pass);
                }
                if (badge.borderEnabled() && (badge.borderColor() >>> 24) > 0) {
                    addBorder(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), Math.max(0.006F, Math.min(rect.width(), rect.height()) * 0.035F), z + SURFACE_BORDER_Z, withAlphaMultiplier(badge.borderColor(), brightness), pass);
                }
            }
            case DIVIDER -> {
                ProjectionComponentSettings.Divider divider = (ProjectionComponentSettings.Divider) settings;
                float thickness = Math.min(Math.max(0.004F, divider.thickness()), Math.max(0.004F, rect.height()));
                if (!divider.dashed()) {
                    addTransformedRect(pose, buffer, transform, rect.x(), rect.y() + (rect.height() - thickness) * 0.5F, rect.width(), thickness, z, withAlphaMultiplier(divider.color(), brightness), pass);
                } else {
                    float dash = Math.max(thickness * 3.0F, rect.width() * 0.045F);
                    for (float x = rect.x(); x < rect.x() + rect.width(); x += dash * 2.0F) {
                        addTransformedRect(pose, buffer, transform, x, rect.y() + (rect.height() - thickness) * 0.5F, Math.min(dash, rect.x() + rect.width() - x), thickness, z, withAlphaMultiplier(divider.color(), brightness), pass);
                    }
                }
            }
            case ROUTE_LIST -> renderRouteListSurfaces(pose, buffer, transform, rect, (ProjectionComponentSettings.RouteList) settings, info.routes(), brightness, z, pass);
            case ROUTE_ICONS, ROUTE_OUTLINE_ICONS -> renderRouteIconSurfaces(pose, buffer, transform, rect, (ProjectionComponentSettings.RouteIcon) settings, info.routes(), component.type() == ProjectionComponentType.ROUTE_OUTLINE_ICONS, brightness, z, pass);
            case ROUTE_CAPSULES -> renderRouteCapsuleSurfaces(pose, buffer, transform, rect, (ProjectionComponentSettings.RouteCapsules) settings, info.routes(), brightness, z, pass);
            case ROUTE_BACKPLATE -> {
                ProjectionComponentSettings.RouteBackplate backplate = (ProjectionComponentSettings.RouteBackplate) settings;
                RouteWindow<RouteChip> window = routeWindow(info.routes(), backplate.maxVisible(), backplate.overflow(), backplate.rotateIntervalTicks(), component.id().hashCode());
                boolean horizontal = backplate.direction() == ProjectionComponentSettings.StripeDirection.HORIZONTAL;
                addRouteBands(pose, buffer, transform, rect, z, window.items(), backplate.colorPolicy(), brightness, horizontal, backplate.opacity(), pass);
                if (window.hidden() > 0 && backplate.overflow() == ProjectionComponentSettings.RouteOverflowMode.PLUS_COUNT) {
                    addPlusBadgeSurface(pose, buffer, transform, rect, z + SURFACE_OVERLAY_Z, brightness, pass);
                }
            }
            case BUILTIN_ICON -> renderBuiltinIconChrome(pose, buffer, transform, rect, (ProjectionComponentSettings.BuiltinIcon) settings, brightness, z, pass);
            case NETWORK_IMAGE -> renderNetworkImageChrome(pose, buffer, transform, rect, (ProjectionComponentSettings.NetworkImage) settings, brightness, z, pass);
            case STATION_TITLE_GROUP, STATION_NAME_TEXT, TRANSLATION_TEXT, CUSTOM_TEXT -> {
            }
            case ROUTE_TEXT -> {
            }
        }
    }

    private static void renderBuiltinIconChrome(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, ProjectionComponentSettings.BuiltinIcon settings, float brightness, float z, SurfacePass pass) {
        if (settings.backgroundEnabled() && (settings.backgroundColor() >>> 24) > 0) {
            addTransformedRect(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), z, withAlphaMultiplier(settings.backgroundColor(), brightness), pass);
        }
        if (settings.borderEnabled() && settings.borderWidth() > 0.001F && (settings.borderColor() >>> 24) > 0) {
            addBorder(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), Math.min(settings.borderWidth(), Math.min(rect.width(), rect.height()) * 0.35F), z + SURFACE_BORDER_Z, withAlphaMultiplier(settings.borderColor(), brightness), pass);
        }
    }

    private static void renderNetworkImageChrome(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, ProjectionComponentSettings.NetworkImage settings, float brightness, float z, SurfacePass pass) {
        if (settings.backgroundEnabled() && (settings.backgroundColor() >>> 24) > 0) {
            addTransformedRect(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), z, withAlphaMultiplier(settings.backgroundColor(), brightness), pass);
        }
        if (settings.borderEnabled() && settings.borderWidth() > 0.001F && (settings.borderColor() >>> 24) > 0) {
            addBorder(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), Math.min(settings.borderWidth(), Math.min(rect.width(), rect.height()) * 0.35F), z + SURFACE_BORDER_Z, withAlphaMultiplier(settings.borderColor(), brightness), pass);
        }
        ProjectionNetworkImageCache.State state = ProjectionRenderFrameContext.networkImageState(settings.url());
        if (state.ready() || !shouldShowNetworkPlaceholder(settings, state)) {
            return;
        }
        int tint = isLoading(state) ? 0x9937C3BB : 0x99FF8A6A;
        int color = withAlphaMultiplier(tint, brightness);
        if (state.status() != ProjectionNetworkImageCache.Status.EMPTY) {
            addTransformedRect(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), z + SURFACE_OVERLAY_Z, withAlphaMultiplier(isLoading(state) ? 0x2219E4D8 : 0x22FF705C, brightness), pass);
        }
        float mark = Math.max(0.012F, Math.min(rect.width(), rect.height()) * 0.18F);
        float thickness = Math.max(0.003F, mark * 0.14F);
        float cx = rect.x() + rect.width() * 0.5F;
        float cy = rect.y() + rect.height() * 0.5F;
        addTransformedRect(pose, buffer, transform, cx - mark, cy - thickness * 0.5F, mark * 2.0F, thickness, z + SURFACE_OVERLAY_Z, color, pass);
        addTransformedRect(pose, buffer, transform, cx - thickness * 0.5F, cy - mark, thickness, mark * 2.0F, z + SURFACE_OVERLAY_Z, color, pass);
    }

    private static void renderProjectionTextures(PoseStack poseStack, SubmitNodeCollector collector, StationNameProjectorConfig config, AppliedProjectionLayout layout, StationRenderInfo info, float width, float height, float brightness, ProjectionBatchMode mode) {
        if (layout.invalid()) {
            return;
        }
        Map<Identifier, List<TexturedComponent>> batches = new LinkedHashMap<>();
        for (ProjectionComponent component : layout.components()) {
            if (!shouldRenderComponent(component, info, config)) {
                continue;
            }
            boolean dynamic = componentSurfaceDynamic(component, info, config);
            if (!mode.accepts(dynamic)) {
                continue;
            }
            for (TexturedComponent item : texturedComponents(component, width, height, brightness)) {
                batches.computeIfAbsent(item.textureId(), ignored -> new ArrayList<>()).add(item);
            }
        }
        for (Map.Entry<Identifier, List<TexturedComponent>> entry : batches.entrySet()) {
            RenderType renderType = texturedRenderType(entry.getKey());
            ClientRenderCompatibility.submitCustomGeometry(collector, poseStack, renderType, (pose, buffer) -> {
                for (TexturedComponent item : entry.getValue()) {
                    addTransformedTexturedRect(pose, buffer, item.transform(), item.rect(), item.z(), item.color(), item.u0(), item.v0(), item.u1(), item.v1());
                }
            });
        }
    }

    private static List<TexturedComponent> texturedComponents(ProjectionComponent component, float width, float height, float brightness) {
        ProjectionComponentSettings settings = component.settings();
        ComponentRect rect = componentRect(component, width, height);
        ComponentTransform transform = componentTransform(component, rect, width, height);
        float z = componentZ(component, TEXTURE_Z_BASE);
        if (settings instanceof ProjectionComponentSettings.BuiltinIcon iconSettings) {
            ProjectionBuiltinIcon icon = ProjectionBuiltinIcon.byId(iconSettings.iconId());
            ProjectionBuiltinIconTextureCache.IconTexture texture = ProjectionBuiltinIconTextureCache.textureFor(icon, iconSettings);
            int color = withAlphaMultiplier(texture.color(), brightness);
            float padding = Math.min(rect.width(), rect.height()) * iconSettings.padding();
            if (iconSettings.fitMode() == ProjectionComponentSettings.ImageFitMode.TILE) {
                return tiledIconComponents(texture.textureId(), transform, ProjectionImageLayout.resolveIconTiles(rect.x(), rect.y(), rect.width(), rect.height(), iconSettings.anchor(), padding, iconSettings.imageScale(), iconSettings.tileGap()), z, color, texture.u0(), texture.v0(), texture.u1(), texture.v1());
            }
            ProjectionImageLayout.Resolved resolved = ProjectionImageLayout.resolveIcon(rect.x(), rect.y(), rect.width(), rect.height(), iconSettings.fitMode(), iconSettings.anchor(), padding, iconSettings.imageScale());
            return List.of(new TexturedComponent(texture.textureId(), transform, new ComponentRect(resolved.x(), resolved.y(), resolved.width(), resolved.height()), z + 0.00003F, color, texture.u0(), texture.v0(), texture.u1(), texture.v1()));
        }
        if (settings instanceof ProjectionComponentSettings.NetworkImage imageSettings) {
            ProjectionNetworkImageCache.State state = ProjectionRenderFrameContext.networkImageState(imageSettings.url());
            if (!state.ready()) {
                return List.of();
            }
            ProjectionImageLayout.Resolved resolved = ProjectionImageLayout.resolve(rect.x(), rect.y(), rect.width(), rect.height(), state.width(), state.height(), imageSettings.fitMode(), imageSettings.anchor(), 0.0F, imageSettings.cropX(), imageSettings.cropY(), imageSettings.cropW(), imageSettings.cropH());
            int color = withAlphaMultiplier(0xFFFFFFFF, imageSettings.opacity() * brightness);
            if (imageSettings.fitMode() == ProjectionComponentSettings.ImageFitMode.TILE) {
                return tiledComponents(state.textureId(), transform, resolved, state.width(), state.height(), imageSettings, z, color);
            }
            return List.of(new TexturedComponent(state.textureId(), transform, new ComponentRect(resolved.x(), resolved.y(), resolved.width(), resolved.height()), z, color, resolved.u0(), resolved.v0(), resolved.u1(), resolved.v1()));
        }
        return List.of();
    }

    private static List<TexturedComponent> tiledComponents(Identifier textureId, ComponentTransform transform, ProjectionImageLayout.Resolved area, int imageWidth, int imageHeight, ProjectionComponentSettings.NetworkImage settings, float z, int color) {
        float cropRatio = Math.max(0.01F, (imageWidth / (float) Math.max(1, imageHeight)) * settings.cropW() / Math.max(0.01F, settings.cropH()));
        float base = Math.max(0.08F, Math.min(area.width(), area.height()) * 0.42F);
        float tileW = Math.max(0.02F, Math.min(area.width(), cropRatio >= 1.0F ? base * cropRatio : base));
        float tileH = Math.max(0.02F, Math.min(area.height(), cropRatio >= 1.0F ? base : base / cropRatio));
        int columns = Math.min(16, Math.max(1, (int) Math.ceil(area.width() / tileW)));
        int rows = Math.min(16, Math.max(1, (int) Math.ceil(area.height() / tileH)));
        List<TexturedComponent> result = new ArrayList<>(columns * rows);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                float x = area.x() + column * tileW;
                float y = area.y() + row * tileH;
                float w = Math.min(tileW, area.x() + area.width() - x);
                float h = Math.min(tileH, area.y() + area.height() - y);
                if (w <= 0.001F || h <= 0.001F) {
                    continue;
                }
                float u1 = area.u0() + (area.u1() - area.u0()) * (w / tileW);
                float v1 = area.v0() + (area.v1() - area.v0()) * (h / tileH);
                result.add(new TexturedComponent(textureId, transform, new ComponentRect(x, y, w, h), z, color, area.u0(), area.v0(), u1, v1));
            }
        }
        return List.copyOf(result);
    }

    private static List<TexturedComponent> tiledIconComponents(Identifier textureId, ComponentTransform transform, ProjectionImageLayout.TileGrid grid, float z, int color, float u0, float v0, float u1, float v1) {
        List<TexturedComponent> result = new ArrayList<>(grid.columns() * grid.rows());
        for (int row = 0; row < grid.rows(); row++) {
            for (int column = 0; column < grid.columns(); column++) {
                float x = grid.x() + column * (grid.tileSize() + grid.gap());
                float y = grid.y() + row * (grid.tileSize() + grid.gap());
                float w = Math.min(grid.tileSize(), grid.x() + grid.width() - x);
                float h = Math.min(grid.tileSize(), grid.y() + grid.height() - y);
                if (w <= 0.001F || h <= 0.001F) {
                    continue;
                }
                float tileU1 = u0 + (u1 - u0) * (w / grid.tileSize());
                float tileV1 = v0 + (v1 - v0) * (h / grid.tileSize());
                result.add(new TexturedComponent(textureId, transform, new ComponentRect(x, y, w, h), z, color, u0, v0, tileU1, tileV1));
            }
        }
        return List.copyOf(result);
    }

    private static RenderType texturedRenderType(Identifier textureId) {
        synchronized (PROJECTION_TEXTURE_QUADS) {
            trimTextureRenderTypes();
            return PROJECTION_TEXTURE_QUADS.computeIfAbsent(textureId, id -> RenderType.create(
                    "superpipeslide_station_name_projector_texture_" + sanitizeRenderTypeName(id),
                    RenderSetup.builder(PROJECTION_TEXTURE_PIPELINE)
                            .withTexture("Sampler0", id)
                            .bufferSize(4096)
                            .createRenderSetup()
            ));
        }
    }

    private static void trimTextureRenderTypes() {
        int max = Math.max(32, ClientConfig.PROJECTION_NETWORK_IMAGE_CACHE_SIZE.get() * 2 + 8);
        Iterator<Identifier> iterator = PROJECTION_TEXTURE_QUADS.keySet().iterator();
        while (PROJECTION_TEXTURE_QUADS.size() > max && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static String sanitizeRenderTypeName(Identifier id) {
        return id.toString().replace(':', '_').replace('/', '_').replace('.', '_');
    }

    private static void renderRouteListSurfaces(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, ProjectionComponentSettings.RouteList settings, List<RouteChip> routes, float brightness, float z, SurfacePass pass) {
        if (routes.isEmpty()) {
            return;
        }
        RouteWindow<RouteChip> window = routeWindow(routes, settings.maxVisible(), settings.overflow(), settings.rotateIntervalTicks(), rect.hashCode());
        float rowHeight = Math.max(0.006F, settings.rowHeight());
        float gap = Math.max(0.0F, settings.gap());
        float stripeWidth = Math.max(0.004F, Math.min(settings.stripeWidth(), rect.width()));
        int drawn = 0;
        for (int i = 0; i < window.items().size(); i++) {
            RouteChip route = window.items().get(i);
            float y = rect.top() - rowHeight - i * (rowHeight + gap);
            if (y < rect.y() - 0.0005F) {
                break;
            }
            addColorStripe(pose, buffer, transform, rect.x(), y, stripeWidth, rowHeight, z, route.colors(), brightness, pass);
            drawn++;
        }
        if (settings.overflow() == ProjectionComponentSettings.RouteOverflowMode.PLUS_COUNT && window.hidden() + Math.max(0, window.items().size() - drawn) > 0) {
            addPlusBadgeSurface(pose, buffer, transform, rect, z + SURFACE_OVERLAY_Z, brightness, pass);
        }
    }

    private static void renderRouteIconSurfaces(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, ProjectionComponentSettings.RouteIcon settings, List<RouteChip> routes, boolean outline, float brightness, float z, SurfacePass pass) {
        float size = Math.max(0.006F, settings.iconSize());
        float gap = Math.max(0.0F, settings.gap());
        List<ComponentRect> cells = iconCells(rect, settings, size, gap);
        if (cells.isEmpty()) {
            return;
        }
        int cellCapacity = Math.min(settings.maxVisible(), cells.size());
        boolean reservePlusCell = settings.overflow() == ProjectionComponentSettings.RouteOverflowMode.PLUS_COUNT && routes != null && routes.size() > cellCapacity;
        int routeSlots = Math.max(0, reservePlusCell ? cellCapacity - 1 : cellCapacity);
        RouteWindow<RouteChip> window = routeWindow(routes, routeSlots, settings.overflow(), settings.rotateIntervalTicks(), rect.hashCode());
        float border = settings.borderWidth() > 0.0005F ? Math.max(0.002F, Math.min(settings.borderWidth(), size * 0.35F)) : 0.0F;
        int drawn = 0;
        for (int i = 0; i < window.items().size() && i < cells.size(); i++) {
            ComponentRect cell = cells.get(i);
            if (cell.x() + cell.width() > rect.x() + rect.width() + 0.0005F || cell.y() < rect.y() - 0.0005F) {
                break;
            }
            RouteChip route = window.items().get(i);
            int color = firstRouteColor(route.colors());
            addIconShape(pose, buffer, transform, cell, settings.shape(), outline, z, withAlphaMultiplier(color, brightness), withAlphaMultiplier(settings.borderColor(), brightness), border, settings.ringThicknessRatio(), pass);
            drawn++;
        }
        int hidden = window.hidden() + Math.max(0, window.items().size() - drawn);
        if (reservePlusCell && drawn < cells.size()) {
            int plusFill = outline ? settings.plusTextColor() : 0xAA10151A;
            addIconShape(pose, buffer, transform, cells.get(drawn), settings.shape(), outline, z + SURFACE_OVERLAY_Z, withAlphaMultiplier(plusFill, brightness), withAlphaMultiplier(settings.plusTextColor(), brightness), 0.0F, Math.max(0.14F, settings.ringThicknessRatio()), pass);
        } else if (settings.overflow() == ProjectionComponentSettings.RouteOverflowMode.PLUS_COUNT && hidden > 0) {
            addPlusBadgeSurface(pose, buffer, transform, rect, z + SURFACE_OVERLAY_Z, brightness, pass);
        }
    }

    private static List<ComponentRect> iconCells(ComponentRect rect, ProjectionComponentSettings.RouteIcon settings, float size, float gap) {
        if (size <= 0.0F || rect.width() <= 0.0F || rect.height() <= 0.0F) {
            return List.of();
        }
        int columnsPossible = Math.max(0, (int) Math.floor((rect.width() + gap) / Math.max(0.0001F, size + gap)));
        int rowsPossible = Math.max(0, (int) Math.floor((rect.height() + gap) / Math.max(0.0001F, size + gap)));
        if (columnsPossible <= 0 || rowsPossible <= 0) {
            return List.of();
        }
        int columns;
        int rows;
        if (settings.flow() == ProjectionComponentSettings.FlowDirection.HORIZONTAL) {
            rows = settings.wrapEnabled() ? Math.min(settings.wrapTracks(), rowsPossible) : 1;
            columns = columnsPossible;
        } else {
            columns = settings.wrapEnabled() ? Math.min(settings.wrapTracks(), columnsPossible) : 1;
            rows = rowsPossible;
        }
        int maxCells = Math.min(settings.maxVisible(), Math.max(0, columns * rows));
        List<ComponentRect> cells = new ArrayList<>(maxCells);
        if (settings.flow() == ProjectionComponentSettings.FlowDirection.HORIZONTAL) {
            for (int row = 0; row < rows && cells.size() < maxCells; row++) {
                for (int column = 0; column < columns && cells.size() < maxCells; column++) {
                    cells.add(new ComponentRect(rect.x() + column * (size + gap), rect.top() - size - row * (size + gap), size, size));
                }
            }
        } else {
            for (int column = 0; column < columns && cells.size() < maxCells; column++) {
                for (int row = 0; row < rows && cells.size() < maxCells; row++) {
                    cells.add(new ComponentRect(rect.x() + column * (size + gap), rect.top() - size - row * (size + gap), size, size));
                }
            }
        }
        return List.copyOf(cells);
    }

    private static void renderRouteCapsuleSurfaces(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, ProjectionComponentSettings.RouteCapsules settings, List<RouteChip> routes, float brightness, float z, SurfacePass pass) {
        RouteWindow<RouteChip> window = routeWindow(routes, settings.maxVisible(), settings.overflow(), settings.rotateIntervalTicks(), rect.hashCode());
        float width = Math.max(0.006F, settings.capsuleWidth());
        float height = Math.max(0.006F, settings.capsuleHeight());
        float gap = Math.max(0.0F, settings.gap());
        int drawn = 0;
        for (int i = 0; i < window.items().size(); i++) {
            float x = settings.flow() == ProjectionComponentSettings.FlowDirection.HORIZONTAL ? rect.x() + i * (width + gap) : rect.x();
            float y = settings.flow() == ProjectionComponentSettings.FlowDirection.HORIZONTAL ? rect.top() - height : rect.top() - height - i * (height + gap);
            if (x + width > rect.x() + rect.width() + 0.0005F || y < rect.y() - 0.0005F) {
                break;
            }
            RouteChip route = window.items().get(i);
            addTransformedRect(pose, buffer, transform, x, y, width, height, z, withAlphaMultiplier(settings.fillColor(), brightness), pass);
            addColorStripe(pose, buffer, transform, x, y, Math.min(height * 0.20F, width * 0.18F), height, z + SURFACE_OVERLAY_Z, route.colors(), brightness, pass);
            drawn++;
        }
        if (settings.overflow() == ProjectionComponentSettings.RouteOverflowMode.PLUS_COUNT && window.hidden() + Math.max(0, window.items().size() - drawn) > 0) {
            addPlusBadgeSurface(pose, buffer, transform, rect, z + SURFACE_OVERLAY_Z, brightness, pass);
        }
    }

    private static void addPlusBadgeSurface(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, float z, float brightness, SurfacePass pass) {
        float size = Math.max(0.045F, Math.min(rect.width() * 0.22F, rect.height() * 0.24F));
        float x = rect.x() + rect.width() - size - 0.010F;
        float y = rect.y() + 0.010F;
        addTransformedRect(pose, buffer, transform, x, y, size, size, z, withAlphaMultiplier(0xAA10151A, brightness), pass);
    }

    private static void renderComponentText(PoseStack poseStack, SubmitNodeCollector collector, Font font, ProjectionComponent component, float width, float height, StationRenderInfo info, StationNameProjectorConfig config, float brightness) {
        ComponentRect rect = componentRect(component, width, height);
        ProjectionComponentSettings settings = component.settings();
        poseStack.pushPose();
        poseStack.translate(rect.x() + rect.width() * 0.5F, rect.y() + rect.height() * 0.5F, componentLayerZ(component));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-component.rotationDegrees()));
        poseStack.translate(-(rect.x() + rect.width() * 0.5F), -(rect.y() + rect.height() * 0.5F), 0.0F);
        switch (component.type()) {
            case STATION_TITLE_GROUP -> drawStationTitleGroup(poseStack, collector, font, info.primaryName(), info.translation(), rect, (ProjectionComponentSettings.StationTitleGroup) settings, brightness);
            case STATION_NAME_TEXT -> drawComponentLine(poseStack, collector, font, info.primaryName(), rect, (ProjectionComponentSettings.Text) settings, brightness);
            case TRANSLATION_TEXT -> {
                if (!info.translation().isBlank()) {
                    drawComponentLine(poseStack, collector, font, info.translation(), rect, (ProjectionComponentSettings.Text) settings, brightness);
                }
            }
            case CUSTOM_TEXT -> drawComponentLine(poseStack, collector, font, component.text(), rect, (ProjectionComponentSettings.Text) settings, brightness);
            case EXIT_BADGE -> {
                if (config.showExit() && !config.exitLabel().isBlank()) {
                    ProjectionComponentSettings.ExitBadge badge = (ProjectionComponentSettings.ExitBadge) settings;
                    ProjectionComponentSettings.Text text = new ProjectionComponentSettings.Text(ProjectionComponentType.CUSTOM_TEXT, "", "", badge.textColor(), badge.fontSize(), ProjectionTextAlign.CENTER, ProjectionOverflowMode.SCALE, 0.0F, 1);
                    drawComponentLine(poseStack, collector, font, Component.translatable("screen.superpipeslide.station_projector.exit", config.exitLabel()).getString(), rect, text, brightness);
                }
            }
            case ROUTE_LIST -> renderRouteListText(poseStack, collector, font, info.routes(), rect, (ProjectionComponentSettings.RouteList) settings, brightness);
            case ROUTE_TEXT -> renderRouteText(poseStack, collector, font, info.routes(), rect, (ProjectionComponentSettings.RouteText) settings, brightness);
            case ROUTE_ICONS, ROUTE_OUTLINE_ICONS -> renderRouteIconText(poseStack, collector, font, info.routes(), rect, (ProjectionComponentSettings.RouteIcon) settings, component.type() == ProjectionComponentType.ROUTE_OUTLINE_ICONS, brightness);
            case ROUTE_CAPSULES -> renderRouteCapsuleText(poseStack, collector, font, info.routes(), rect, (ProjectionComponentSettings.RouteCapsules) settings, brightness);
            case NETWORK_IMAGE -> drawNetworkImageStatusText(poseStack, collector, font, rect, (ProjectionComponentSettings.NetworkImage) settings, brightness);
            default -> {
            }
        }
        poseStack.popPose();
    }

    private static void drawNetworkImageStatusText(PoseStack poseStack, SubmitNodeCollector collector, Font font, ComponentRect rect, ProjectionComponentSettings.NetworkImage settings, float brightness) {
        ProjectionNetworkImageCache.State state = ProjectionRenderFrameContext.networkImageState(settings.url());
        if (state.ready() || !shouldShowNetworkPlaceholder(settings, state)) {
            return;
        }
        boolean loading = isLoading(state);
        if (!loading && settings.fallbackMode() == ProjectionComponentSettings.ImageFallbackMode.COMPACT) {
            return;
        }
        String key = state.messageKey() == null || state.messageKey().isBlank() ? "screen.superpipeslide.projection_image.failed" : state.messageKey();
        ProjectionComponentSettings.Text text = new ProjectionComponentSettings.Text(ProjectionComponentType.CUSTOM_TEXT, "", "", withAlphaMultiplier(loading ? 0xFF37C3BB : 0xFFFF8A6A, brightness), Math.min(0.035F, Math.max(0.012F, rect.height() * 0.16F)), ProjectionTextAlign.CENTER, ProjectionOverflowMode.SCALE, 0.0F, 1);
        drawComponentLine(poseStack, collector, font, Component.translatable(key).getString(), new ComponentRect(rect.x() + rect.width() * 0.06F, rect.y() + rect.height() * 0.10F, rect.width() * 0.88F, rect.height() * 0.32F), text, brightness);
    }

    private static boolean shouldShowNetworkPlaceholder(ProjectionComponentSettings.NetworkImage settings, ProjectionNetworkImageCache.State state) {
        if (isLoading(state)) {
            return settings.loadingMode() != ProjectionComponentSettings.ImageLoadingMode.HIDDEN;
        }
        return settings.fallbackMode() != ProjectionComponentSettings.ImageFallbackMode.HIDDEN;
    }

    private static boolean isLoading(ProjectionNetworkImageCache.State state) {
        return state.status() == ProjectionNetworkImageCache.Status.QUEUED || state.status() == ProjectionNetworkImageCache.Status.DOWNLOADING || state.status() == ProjectionNetworkImageCache.Status.DECODING;
    }

    private static void drawStationTitleGroup(PoseStack poseStack, SubmitNodeCollector collector, Font font, String primaryName, String translationName, ComponentRect rect, ProjectionComponentSettings.StationTitleGroup settings, float brightness) {
        if (settings.orientation() == ProjectionComponentSettings.TextOrientation.ROTATE_CW || settings.orientation() == ProjectionComponentSettings.TextOrientation.ROTATE_CCW) {
            poseStack.pushPose();
            poseStack.translate(rect.x() + rect.width() * 0.5F, rect.y() + rect.height() * 0.5F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(settings.orientation() == ProjectionComponentSettings.TextOrientation.ROTATE_CW ? -90.0F : 90.0F));
            ComponentRect rotated = new ComponentRect(-rect.height() * 0.5F, -rect.width() * 0.5F, rect.height(), rect.width());
            drawStationTitleGroupHorizontal(poseStack, collector, font, primaryName, translationName, rotated, settings, brightness);
            poseStack.popPose();
            return;
        }
        if (settings.orientation() == ProjectionComponentSettings.TextOrientation.VERTICAL_STACK) {
            drawStationTitleGroupVertical(poseStack, collector, font, primaryName, translationName, rect, settings, brightness);
            return;
        }
        drawStationTitleGroupHorizontal(poseStack, collector, font, primaryName, translationName, rect, settings, brightness);
    }

    private static void drawStationTitleGroupHorizontal(PoseStack poseStack, SubmitNodeCollector collector, Font font, String primaryName, String translationName, ComponentRect rect, ProjectionComponentSettings.StationTitleGroup settings, float brightness) {
        String primary = primaryName == null ? "" : primaryName.trim();
        String translation = translationName == null ? "" : translationName.trim();
        if (primary.isEmpty()) {
            return;
        }
        float primaryHeight = Math.max(0.006F, settings.primaryFontSize());
        float translationHeight = Math.max(0.006F, settings.translationFontSize());
        float gap = Math.max(0.0F, settings.gap());
        ProjectionComponentSettings.Text primaryText = titleText(settings.primaryColor(), settings.primaryFontSize(), settings.align(), settings.primaryOverflow(), ProjectionComponentSettings.TextOrientation.HORIZONTAL);
        ProjectionComponentSettings.Text translationText = titleText(settings.translationColor(), settings.translationFontSize(), settings.align(), settings.translationOverflow(), ProjectionComponentSettings.TextOrientation.HORIZONTAL);
        if (!translation.isEmpty()) {
            float totalHeight = Math.min(rect.height(), primaryHeight + gap + translationHeight);
            float top = rect.top() - Math.max(0.0F, rect.height() - totalHeight) * 0.5F;
            ComponentRect primaryRect = new ComponentRect(rect.x(), top - primaryHeight, rect.width(), Math.max(0.001F, Math.min(primaryHeight, totalHeight)));
            ComponentRect translationRect = new ComponentRect(rect.x(), primaryRect.y() - gap - translationHeight, rect.width(), Math.max(0.001F, Math.min(translationHeight, primaryRect.y() - gap - rect.y())));
            drawComponentLine(poseStack, collector, font, primary, primaryRect, primaryText, brightness);
            drawComponentLine(poseStack, collector, font, translation, translationRect, translationText, brightness);
            return;
        }
        ProjectionComponentSettings.Text missingText = settings.missingTranslationMode() == ProjectionComponentSettings.MissingTranslationMode.EXPAND_PRIMARY
                ? titleText(settings.primaryColor(), settings.primaryFontSize() * settings.missingPrimaryScale(), settings.align(), settings.primaryOverflow(), ProjectionComponentSettings.TextOrientation.HORIZONTAL)
                : primaryText;
        ComponentRect primaryRect = switch (settings.missingTranslationMode()) {
            case KEEP_PRIMARY_SLOT -> {
                float totalHeight = Math.min(rect.height(), primaryHeight + gap + translationHeight);
                float top = rect.top() - Math.max(0.0F, rect.height() - totalHeight) * 0.5F;
                yield new ComponentRect(rect.x(), top - primaryHeight, rect.width(), Math.max(0.001F, Math.min(primaryHeight, totalHeight)));
            }
            case CENTER_PRIMARY, EXPAND_PRIMARY -> rect;
        };
        drawComponentLine(poseStack, collector, font, primary, primaryRect, missingText, brightness);
    }

    private static void drawStationTitleGroupVertical(PoseStack poseStack, SubmitNodeCollector collector, Font font, String primaryName, String translationName, ComponentRect rect, ProjectionComponentSettings.StationTitleGroup settings, float brightness) {
        String primary = primaryName == null ? "" : primaryName.trim();
        String translation = translationName == null ? "" : translationName.trim();
        if (primary.isEmpty()) {
            return;
        }
        ProjectionComponentSettings.Text primaryText = titleText(settings.primaryColor(), settings.primaryFontSize(), settings.align(), settings.primaryOverflow(), ProjectionComponentSettings.TextOrientation.VERTICAL_STACK);
        ProjectionComponentSettings.Text translationText = titleText(settings.translationColor(), settings.translationFontSize(), settings.align(), settings.translationOverflow(), ProjectionComponentSettings.TextOrientation.VERTICAL_STACK);
        if (!translation.isEmpty()) {
            float gap = Math.max(0.0F, settings.gap());
            float primaryWidth = Math.max(0.001F, rect.width() * 0.62F);
            ComponentRect primaryRect = new ComponentRect(rect.x(), rect.y(), Math.max(0.001F, primaryWidth - gap * 0.5F), rect.height());
            ComponentRect translationRect = new ComponentRect(rect.x() + primaryWidth + gap * 0.5F, rect.y(), Math.max(0.001F, rect.width() - primaryWidth - gap * 0.5F), rect.height());
            drawComponentLine(poseStack, collector, font, primary, primaryRect, primaryText, brightness);
            drawComponentLine(poseStack, collector, font, translation, translationRect, translationText, brightness);
            return;
        }
        ProjectionComponentSettings.Text missingText = settings.missingTranslationMode() == ProjectionComponentSettings.MissingTranslationMode.EXPAND_PRIMARY
                ? titleText(settings.primaryColor(), settings.primaryFontSize() * settings.missingPrimaryScale(), settings.align(), settings.primaryOverflow(), ProjectionComponentSettings.TextOrientation.VERTICAL_STACK)
                : primaryText;
        ComponentRect primaryRect = settings.missingTranslationMode() == ProjectionComponentSettings.MissingTranslationMode.KEEP_PRIMARY_SLOT
                ? new ComponentRect(rect.x(), rect.y(), Math.max(0.001F, rect.width() * 0.62F), rect.height())
                : rect;
        drawComponentLine(poseStack, collector, font, primary, primaryRect, missingText, brightness);
    }

    private static ProjectionComponentSettings.Text titleText(int color, float fontSize, ProjectionTextAlign align, ProjectionOverflowMode overflow, ProjectionComponentSettings.TextOrientation orientation) {
        return new ProjectionComponentSettings.Text(ProjectionComponentType.CUSTOM_TEXT, "", "", color, fontSize, align, overflow, orientation, 0.02F, 1);
    }

    private static void drawComponentLine(PoseStack poseStack, SubmitNodeCollector collector, Font font, String text, ComponentRect rect, ProjectionComponentSettings.Text settings, float brightness) {
        if (settings.orientation() == ProjectionComponentSettings.TextOrientation.ROTATE_CW || settings.orientation() == ProjectionComponentSettings.TextOrientation.ROTATE_CCW) {
            poseStack.pushPose();
            poseStack.translate(rect.x() + rect.width() * 0.5F, rect.y() + rect.height() * 0.5F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(settings.orientation() == ProjectionComponentSettings.TextOrientation.ROTATE_CW ? -90.0F : 90.0F));
            ComponentRect rotated = new ComponentRect(-rect.height() * 0.5F, -rect.width() * 0.5F, rect.height(), rect.width());
            drawHorizontalComponentLine(poseStack, collector, font, text, rotated, settings, brightness);
            poseStack.popPose();
            return;
        }
        if (settings.orientation() == ProjectionComponentSettings.TextOrientation.VERTICAL_STACK) {
            drawVerticalStackComponentLine(poseStack, collector, font, text, rect, settings, brightness);
            return;
        }
        drawHorizontalComponentLine(poseStack, collector, font, text, rect, settings, brightness);
    }

    private static void drawHorizontalComponentLine(PoseStack poseStack, SubmitNodeCollector collector, Font font, String text, ComponentRect rect, ProjectionComponentSettings.Text settings, float brightness) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return;
        }
        float preferredScale = Math.max(0.004F, settings.fontSize() / Math.max(1.0F, font.lineHeight));
        float maxWidth = rect.width() * 0.92F;
        if (settings.overflow() == ProjectionOverflowMode.WRAP) {
            drawWrappedComponentLine(poseStack, collector, font, value, rect, settings, brightness, preferredScale, maxWidth);
            return;
        }
        boolean overflowingAtPreferred = ProjectionTextMeasureCache.width(font, value) * preferredScale > maxWidth;
        if (settings.overflow() == ProjectionOverflowMode.HIDE && overflowingAtPreferred) {
            return;
        }
        float scale = settings.overflow() == ProjectionOverflowMode.SCALE || settings.overflow() == ProjectionOverflowMode.PLUS_COUNT
                ? Math.min(preferredScale, maxWidth / Math.max(1.0F, ProjectionTextMeasureCache.width(font, value)))
                : preferredScale;
        scale = Math.max(0.004F, scale);
        if (settings.overflow() == ProjectionOverflowMode.MARQUEE && overflowingAtPreferred) {
            float textHeight = font.lineHeight * scale;
            float topY = rect.y() + rect.height() * 0.5F + textHeight * 0.5F;
            drawMarqueeText(poseStack, collector, font, value, rect.x() + rect.width() * 0.04F, topY, scale, withAlphaMultiplier(settings.textColor(), brightness), maxWidth, value.hashCode());
            return;
        }
        String rendered = settings.overflow() == ProjectionOverflowMode.PLUS_COUNT && overflowingAtPreferred
                ? ellipsizeForWorld(font, value, maxWidth / scale)
                : value;
        float textWidth = ProjectionTextMeasureCache.width(font, rendered) * scale;
        float x = switch (settings.align()) {
            case CENTER -> rect.x() + (rect.width() - textWidth) * 0.5F;
            case RIGHT -> rect.x() + rect.width() - textWidth - rect.width() * 0.04F;
            case LEFT -> rect.x() + rect.width() * 0.04F;
        };
        float textHeight = font.lineHeight * scale;
        float topY = rect.y() + rect.height() * 0.5F + textHeight * 0.5F;
        drawText(poseStack, collector, rendered, x, topY, scale, withAlphaMultiplier(settings.textColor(), brightness), false);
    }

    private static void drawVerticalStackComponentLine(PoseStack poseStack, SubmitNodeCollector collector, Font font, String text, ComponentRect rect, ProjectionComponentSettings.Text settings, float brightness) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return;
        }
        List<String> glyphs = new ArrayList<>();
        int maxGlyphWidth = 1;
        int index = 0;
        while (index < value.length()) {
            int codePoint = value.codePointAt(index);
            String glyph = new String(Character.toChars(codePoint));
            glyphs.add(glyph);
            maxGlyphWidth = Math.max(maxGlyphWidth, ProjectionTextMeasureCache.width(font, glyph));
            index += Character.charCount(codePoint);
        }
        if (glyphs.isEmpty()) {
            return;
        }
        float preferredScale = Math.max(0.004F, settings.fontSize() / Math.max(1.0F, font.lineHeight));
        float fitWidth = rect.width() * 0.86F / Math.max(1.0F, maxGlyphWidth);
        float unitGap = settings.lineSpacing() / Math.max(0.001F, preferredScale);
        float totalUnits = glyphs.size() * font.lineHeight + Math.max(0, glyphs.size() - 1) * unitGap;
        float fitHeight = rect.height() * 0.94F / Math.max(1.0F, totalUnits);
        float scale = Math.max(0.004F, Math.min(preferredScale, Math.min(fitWidth, fitHeight)));
        float scaledGap = Math.max(0.0F, settings.lineSpacing());
        float totalHeight = glyphs.size() * font.lineHeight * scale + Math.max(0, glyphs.size() - 1) * scaledGap;
        float y = rect.y() + rect.height() * 0.5F + totalHeight * 0.5F;
        int color = withAlphaMultiplier(settings.textColor(), brightness);
        for (String glyph : glyphs) {
            float glyphWidth = ProjectionTextMeasureCache.width(font, glyph) * scale;
            float x = switch (settings.align()) {
                case CENTER -> rect.x() + (rect.width() - glyphWidth) * 0.5F;
                case RIGHT -> rect.x() + rect.width() - glyphWidth - rect.width() * 0.06F;
                case LEFT -> rect.x() + rect.width() * 0.06F;
            };
            drawText(poseStack, collector, glyph, x, y, scale, color, false);
            y -= font.lineHeight * scale + scaledGap;
        }
    }

    private static void drawWrappedComponentLine(PoseStack poseStack, SubmitNodeCollector collector, Font font, String value, ComponentRect rect, ProjectionComponentSettings.Text settings, float brightness, float preferredScale, float maxWidth) {
        float scale = preferredScale;
        List<String> lines = wrapLines(font, value, Math.max(1.0F, maxWidth / scale), settings.maxLines());
        float totalHeight = lines.size() * font.lineHeight * scale + Math.max(0, lines.size() - 1) * settings.lineSpacing();
        if (totalHeight > rect.height() * 0.90F) {
            scale = Math.max(0.004F, Math.min(scale, rect.height() * 0.90F / Math.max(1.0F, lines.size() * font.lineHeight)));
            lines = wrapLines(font, value, Math.max(1.0F, maxWidth / scale), settings.maxLines());
            totalHeight = lines.size() * font.lineHeight * scale + Math.max(0, lines.size() - 1) * settings.lineSpacing();
        }
        float y = rect.y() + rect.height() * 0.5F + totalHeight * 0.5F;
        int color = withAlphaMultiplier(settings.textColor(), brightness);
        for (String line : lines) {
            float textWidth = ProjectionTextMeasureCache.width(font, line) * scale;
            float x = switch (settings.align()) {
                case CENTER -> rect.x() + (rect.width() - textWidth) * 0.5F;
                case RIGHT -> rect.x() + rect.width() - textWidth - rect.width() * 0.04F;
                case LEFT -> rect.x() + rect.width() * 0.04F;
            };
            drawText(poseStack, collector, line, x, y, scale, color, false);
            y -= font.lineHeight * scale + settings.lineSpacing();
        }
    }

    private static String ellipsizeForWorld(Font font, String text, float maxTextWidth) {
        if (ProjectionTextMeasureCache.width(font, text) <= maxTextWidth) {
            return text;
        }
        String suffix = "...";
        int suffixWidth = ProjectionTextMeasureCache.width(font, suffix);
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            int next = index + Character.charCount(codePoint);
            String candidate = builder + text.substring(index, next);
            if (ProjectionTextMeasureCache.width(font, candidate) + suffixWidth > maxTextWidth) {
                break;
            }
            builder.appendCodePoint(codePoint);
            index = next;
        }
        return builder + suffix;
    }

    private static List<String> wrapLines(Font font, String value, float maxTextWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        int index = 0;
        while (index < value.length() && lines.size() < maxLines) {
            int lineEnd = fitEnd(font, value, index, maxTextWidth);
            if (lineEnd <= index) {
                lineEnd = index + Character.charCount(value.codePointAt(index));
            }
            int trimmedEnd = lineEnd;
            while (trimmedEnd > index && Character.isWhitespace(value.charAt(trimmedEnd - 1))) {
                trimmedEnd--;
            }
            String line = value.substring(index, Math.max(index, trimmedEnd));
            if (lines.size() == maxLines - 1 && lineEnd < value.length()) {
                line = ellipsizeForWorld(font, value.substring(index).trim(), maxTextWidth);
                lines.add(line);
                break;
            }
            lines.add(line);
            index = lineEnd;
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
                index++;
            }
        }
        return lines.isEmpty() ? List.of(value) : lines;
    }

    private static int fitEnd(Font font, String value, int start, float maxTextWidth) {
        int best = start;
        int lastSpace = -1;
        int index = start;
        while (index < value.length()) {
            int next = index + Character.charCount(value.codePointAt(index));
            if (Character.isWhitespace(value.charAt(index))) {
                lastSpace = next;
            }
            if (ProjectionTextMeasureCache.width(font, value.substring(start, next)) > maxTextWidth) {
                return lastSpace > start ? lastSpace : best;
            }
            best = next;
            index = next;
        }
        return best;
    }

    private static void renderRouteListText(PoseStack poseStack, SubmitNodeCollector collector, Font font, List<RouteChip> routes, ComponentRect rect, ProjectionComponentSettings.RouteList settings, float brightness) {
        if (routes.isEmpty()) {
            return;
        }
        RouteWindow<RouteChip> window = routeWindow(routes, settings.maxVisible(), settings.overflow(), settings.rotateIntervalTicks(), rect.hashCode());
        float rowHeight = Math.max(0.006F, settings.rowHeight());
        float gap = Math.max(0.0F, settings.gap());
        float stripeWidth = Math.max(0.004F, Math.min(settings.stripeWidth(), rect.width()));
        float scale = Math.max(0.004F, settings.fontSize() / Math.max(1.0F, font.lineHeight));
        int drawn = 0;
        for (int i = 0; i < window.items().size(); i++) {
            RouteChip route = window.items().get(i);
            float y = rect.top() - rowHeight - i * (rowHeight + gap);
            if (y < rect.y() - 0.0005F) {
                break;
            }
            float maxTextWidth = Math.max(0.001F, rect.width() - stripeWidth - 0.035F);
            float textY = y + rowHeight * 0.5F + font.lineHeight * scale * 0.5F;
            String label = route.name();
            int color = withAlphaMultiplier(settings.textColor(), brightness);
            if (settings.labelOverflow() == ProjectionOverflowMode.MARQUEE && ProjectionTextMeasureCache.width(font, label) * scale > maxTextWidth) {
                drawMarqueeText(poseStack, collector, font, label, rect.x() + stripeWidth + 0.025F, textY, scale, color, maxTextWidth, label.hashCode());
            } else {
                label = fitRouteLabel(font, label, maxTextWidth / scale, settings.labelOverflow());
                drawText(poseStack, collector, label, rect.x() + stripeWidth + 0.025F, textY, scale, color, false);
            }
            drawn++;
        }
        drawPlusCountText(poseStack, collector, font, rect, window.hidden() + Math.max(0, window.items().size() - drawn), settings.plusTextColor(), settings.overflow(), brightness);
    }

    private static void renderRouteText(PoseStack poseStack, SubmitNodeCollector collector, Font font, List<RouteChip> routes, ComponentRect rect, ProjectionComponentSettings.RouteText settings, float brightness) {
        RouteWindow<RouteChip> window = routeWindow(routes, 1, settings.overflow(), settings.rotateIntervalTicks(), rect.hashCode());
        if (window.items().isEmpty()) {
            return;
        }
        RouteChip route = window.items().getFirst();
        String label = settings.shortName() ? shortRouteLabel(route.name()) : route.name();
        ProjectionComponentSettings.Text text = new ProjectionComponentSettings.Text(ProjectionComponentType.CUSTOM_TEXT, "", "", settings.textColor(), settings.fontSize(), settings.align(), ProjectionOverflowMode.MARQUEE, 0.0F, 1);
        drawComponentLine(poseStack, collector, font, label, rect, text, brightness);
        drawPlusCountText(poseStack, collector, font, rect, window.hidden(), settings.plusTextColor(), settings.overflow(), brightness);
    }

    private static void renderRouteIconText(PoseStack poseStack, SubmitNodeCollector collector, Font font, List<RouteChip> routes, ComponentRect rect, ProjectionComponentSettings.RouteIcon settings, boolean outline, float brightness) {
        float size = Math.max(0.006F, settings.iconSize());
        float gap = Math.max(0.0F, settings.gap());
        List<ComponentRect> cells = iconCells(rect, settings, size, gap);
        if (cells.isEmpty()) {
            return;
        }
        int cellCapacity = Math.min(settings.maxVisible(), cells.size());
        boolean reservePlusCell = settings.overflow() == ProjectionComponentSettings.RouteOverflowMode.PLUS_COUNT && routes != null && routes.size() > cellCapacity;
        int routeSlots = Math.max(0, reservePlusCell ? cellCapacity - 1 : cellCapacity);
        RouteWindow<RouteChip> window = routeWindow(routes, routeSlots, settings.overflow(), settings.rotateIntervalTicks(), rect.hashCode());
        float scale = Math.max(0.004F, settings.fontSize() / Math.max(1.0F, font.lineHeight));
        int drawn = 0;
        for (int i = 0; i < window.items().size() && i < cells.size(); i++) {
            ComponentRect cell = cells.get(i);
            if (cell.x() + cell.width() > rect.x() + rect.width() + 0.0005F || cell.y() < rect.y() - 0.0005F) {
                break;
            }
            RouteChip route = window.items().get(i);
            if (settings.showLabel()) {
                String label = shortRouteLabel(route.name());
                int textColor = outline ? settings.textColor() : contrastText(route.colors());
                drawText(poseStack, collector, label, cell.x() + (cell.width() - ProjectionTextMeasureCache.width(font, label) * scale) * 0.5F, cell.y() + cell.height() * 0.5F + font.lineHeight * scale * 0.5F, scale, withAlphaMultiplier(textColor, brightness), false);
            }
            drawn++;
        }
        int hidden = window.hidden() + Math.max(0, window.items().size() - drawn);
        if (reservePlusCell && drawn < cells.size()) {
            ComponentRect cell = cells.get(drawn);
            String label = "+" + Math.max(1, hidden);
            float fittedScale = Math.min(scale, Math.min(cell.width() * 0.76F / Math.max(1.0F, ProjectionTextMeasureCache.width(font, label)), cell.height() * 0.76F / Math.max(1.0F, font.lineHeight)));
            fittedScale = Math.max(0.004F, fittedScale);
            drawText(poseStack, collector, label, cell.x() + (cell.width() - ProjectionTextMeasureCache.width(font, label) * fittedScale) * 0.5F, cell.y() + cell.height() * 0.5F + font.lineHeight * fittedScale * 0.5F, fittedScale, withAlphaMultiplier(settings.plusTextColor(), brightness), false);
        } else {
            drawPlusCountText(poseStack, collector, font, rect, hidden, settings.plusTextColor(), settings.overflow(), brightness);
        }
    }

    private static void renderRouteCapsuleText(PoseStack poseStack, SubmitNodeCollector collector, Font font, List<RouteChip> routes, ComponentRect rect, ProjectionComponentSettings.RouteCapsules settings, float brightness) {
        RouteWindow<RouteChip> window = routeWindow(routes, settings.maxVisible(), settings.overflow(), settings.rotateIntervalTicks(), rect.hashCode());
        float width = Math.max(0.006F, settings.capsuleWidth());
        float height = Math.max(0.006F, settings.capsuleHeight());
        float gap = Math.max(0.0F, settings.gap());
        float scale = Math.max(0.004F, settings.fontSize() / Math.max(1.0F, font.lineHeight));
        int drawn = 0;
        for (int i = 0; i < window.items().size(); i++) {
            float x = settings.flow() == ProjectionComponentSettings.FlowDirection.HORIZONTAL ? rect.x() + i * (width + gap) : rect.x();
            float y = settings.flow() == ProjectionComponentSettings.FlowDirection.HORIZONTAL ? rect.top() - height : rect.top() - height - i * (height + gap);
            if (x + width > rect.x() + rect.width() + 0.0005F || y < rect.y() - 0.0005F) {
                break;
            }
            RouteChip route = window.items().get(i);
            float maxTextWidth = Math.max(0.001F, width - height * 0.30F - 0.025F);
            String label = settings.showShortName() ? shortRouteLabel(route.name()) : route.name();
            drawCapsuleText(poseStack, collector, font, label, x + height * 0.26F, y, width - height * 0.26F, height, scale, withAlphaMultiplier(settings.textColor(), brightness), settings.contentOrientation(), settings.labelOverflow(), maxTextWidth);
            drawn++;
        }
        drawPlusCountText(poseStack, collector, font, rect, window.hidden() + Math.max(0, window.items().size() - drawn), settings.plusTextColor(), settings.overflow(), brightness);
    }

    private static void drawPlusCountText(PoseStack poseStack, SubmitNodeCollector collector, Font font, ComponentRect rect, int extra, int color, ProjectionComponentSettings.RouteOverflowMode overflow, float brightness) {
        if (extra <= 0 || overflow != ProjectionComponentSettings.RouteOverflowMode.PLUS_COUNT) {
            return;
        }
        String label = "+" + extra;
        float scale = Math.max(0.004F, Math.min(0.065F, rect.height() * 0.16F) / Math.max(1.0F, font.lineHeight));
        drawText(poseStack, collector, label, rect.x() + rect.width() - ProjectionTextMeasureCache.width(font, label) * scale - 0.012F, rect.y() + 0.012F + font.lineHeight * scale, scale, withAlphaMultiplier(color, brightness), false);
    }

    private static void drawCapsuleText(PoseStack poseStack, SubmitNodeCollector collector, Font font, String label, float x, float y, float width, float height, float scale, int color, ProjectionComponentSettings.CapsuleContentOrientation orientation, ProjectionOverflowMode overflow, float maxTextWidth) {
        if (orientation == ProjectionComponentSettings.CapsuleContentOrientation.HORIZONTAL) {
            float textY = y + height * 0.5F + font.lineHeight * scale * 0.5F;
            if (overflow == ProjectionOverflowMode.MARQUEE && ProjectionTextMeasureCache.width(font, label) * scale > maxTextWidth) {
                drawMarqueeText(poseStack, collector, font, label, x, textY, scale, color, maxTextWidth, label.hashCode());
            } else {
                drawText(poseStack, collector, fitRouteLabel(font, label, maxTextWidth / scale, overflow), x, textY, scale, color, false);
            }
            return;
        }
        poseStack.pushPose();
        poseStack.translate(x + width * 0.5F, y + height * 0.5F, 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(orientation == ProjectionComponentSettings.CapsuleContentOrientation.ROTATE_CW ? -90.0F : 90.0F));
        if (overflow == ProjectionOverflowMode.MARQUEE && ProjectionTextMeasureCache.width(font, label) * scale > maxTextWidth) {
            drawMarqueeText(poseStack, collector, font, label, -maxTextWidth * 0.5F, font.lineHeight * scale * 0.5F, scale, color, maxTextWidth, label.hashCode());
        } else {
            String rendered = fitRouteLabel(font, label, maxTextWidth / scale, overflow);
            drawText(poseStack, collector, rendered, -ProjectionTextMeasureCache.width(font, rendered) * scale * 0.5F, font.lineHeight * scale * 0.5F, scale, color, false);
        }
        poseStack.popPose();
    }

    private static String fitRouteLabel(Font font, String label, float maxTextWidth, ProjectionOverflowMode overflow) {
        boolean overflows = ProjectionTextMeasureCache.width(font, label) > maxTextWidth;
        return switch (overflow) {
            case HIDE -> overflows ? "" : label;
            case MARQUEE -> overflows ? ellipsizeForWorld(font, label, maxTextWidth) : label;
            case SCALE, PLUS_COUNT, WRAP -> overflows ? ellipsizeForWorld(font, label, maxTextWidth) : label;
        };
    }

    private static ComponentRect componentRect(ProjectionComponent component, float width, float height) {
        float x = -width * 0.5F + component.x();
        float top = height * 0.5F - component.y();
        float w = component.width();
        float h = component.height();
        return new ComponentRect(x, top - h, w, h);
    }

    private static ComponentTransform componentTransform(ProjectionComponent component, ComponentRect rect, float canvasWidth, float canvasHeight) {
        return new ComponentTransform(rect.x() + rect.width() * 0.5F, rect.y() + rect.height() * 0.5F, (float) Math.toRadians(-component.rotationDegrees()), CanvasBounds.of(canvasWidth, canvasHeight));
    }

    private static float componentZ(ProjectionComponent component, float base) {
        return base + componentLayerZ(component);
    }

    private static float componentLayerZ(ProjectionComponent component) {
        return component.layer() * LAYER_Z_STEP;
    }

    private static void addIconShape(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, ProjectionComponentSettings.IconShape shape, boolean outline, float z, int fillColor, int borderColor, float borderWidth, float ringThicknessRatio, SurfacePass pass) {
        float border = borderWidth > 0.0005F ? Math.max(0.002F, Math.min(borderWidth, Math.min(rect.width(), rect.height()) * 0.35F)) : 0.0F;
        float ring = Math.max(0.002F, Math.min(Math.min(rect.width(), rect.height()) * ringThicknessRatio, Math.min(rect.width(), rect.height()) * 0.45F));
        if (shape == ProjectionComponentSettings.IconShape.SQUARE) {
            if (!outline) {
                addTransformedRect(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), z, fillColor, pass);
                if (border > 0.0F && (borderColor >>> 24) > 0) {
                    addBorder(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), border, z + SURFACE_BORDER_Z, borderColor, pass);
                }
            } else {
                if (border > 0.0F && (borderColor >>> 24) > 0) {
                    addBorder(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), border, z + SURFACE_BORDER_Z, borderColor, pass);
                }
                ComponentRect inner = inset(rect, border);
                addBorder(pose, buffer, transform, inner.x(), inner.y(), inner.width(), inner.height(), Math.min(ring, Math.min(inner.width(), inner.height()) * 0.45F), z + SURFACE_OVERLAY_Z, fillColor, pass);
            }
            return;
        }
        if (!outline) {
            addFilledCircle(pose, buffer, transform, rect, z, fillColor, pass);
            if (border > 0.0F && (borderColor >>> 24) > 0) {
                addCircleRing(pose, buffer, transform, rect, border, z + SURFACE_BORDER_Z, borderColor, pass);
            }
        } else {
            if (border > 0.0F && (borderColor >>> 24) > 0) {
                addCircleRing(pose, buffer, transform, rect, border, z + SURFACE_BORDER_Z, borderColor, pass);
            }
            addCircleRing(pose, buffer, transform, inset(rect, border), ring, z + SURFACE_OVERLAY_Z, fillColor, pass);
        }
    }

    private static ComponentRect inset(ComponentRect rect, float amount) {
        float safe = Math.max(0.0F, Math.min(amount, Math.max(0.0F, Math.min(rect.width(), rect.height()) * 0.5F - 0.001F)));
        return new ComponentRect(rect.x() + safe, rect.y() + safe, Math.max(0.001F, rect.width() - safe * 2.0F), Math.max(0.001F, rect.height() - safe * 2.0F));
    }

    private static void addFilledCircle(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, float z, int color, SurfacePass pass) {
        if (!pass.accepts(color)) {
            return;
        }
        int segments = circleSegments(rect);
        float cx = rect.x() + rect.width() * 0.5F;
        float cy = rect.y() + rect.height() * 0.5F;
        float rx = Math.max(0.0005F, rect.width() * 0.5F);
        float ry = Math.max(0.0005F, rect.height() * 0.5F);
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (Math.PI * 2.0D * i / segments);
            float a1 = (float) (Math.PI * 2.0D * (i + 1) / segments);
            float x0 = cx + (float) Math.cos(a0) * rx;
            float y0 = cy + (float) Math.sin(a0) * ry;
            float x1 = cx + (float) Math.cos(a1) * rx;
            float y1 = cy + (float) Math.sin(a1) * ry;
            addTransformedQuad(pose, buffer, transform, cx, cy, x0, y0, x1, y1, cx, cy, z, color, pass);
        }
    }

    private static void addCircleRing(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, float thickness, float z, int color, SurfacePass pass) {
        if (!pass.accepts(color)) {
            return;
        }
        float t = Math.max(0.001F, Math.min(thickness, Math.min(rect.width(), rect.height()) * 0.5F));
        ComponentRect inner = inset(rect, t);
        int segments = circleSegments(rect);
        float cx = rect.x() + rect.width() * 0.5F;
        float cy = rect.y() + rect.height() * 0.5F;
        float rx = Math.max(0.0005F, rect.width() * 0.5F);
        float ry = Math.max(0.0005F, rect.height() * 0.5F);
        float innerRx = Math.max(0.0F, inner.width() * 0.5F);
        float innerRy = Math.max(0.0F, inner.height() * 0.5F);
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (Math.PI * 2.0D * i / segments);
            float a1 = (float) (Math.PI * 2.0D * (i + 1) / segments);
            float ox0 = cx + (float) Math.cos(a0) * rx;
            float oy0 = cy + (float) Math.sin(a0) * ry;
            float ox1 = cx + (float) Math.cos(a1) * rx;
            float oy1 = cy + (float) Math.sin(a1) * ry;
            float ix1 = cx + (float) Math.cos(a1) * innerRx;
            float iy1 = cy + (float) Math.sin(a1) * innerRy;
            float ix0 = cx + (float) Math.cos(a0) * innerRx;
            float iy0 = cy + (float) Math.sin(a0) * innerRy;
            addTransformedQuad(pose, buffer, transform, ox0, oy0, ox1, oy1, ix1, iy1, ix0, iy0, z, color, pass);
        }
    }

    private static int circleSegments(ComponentRect rect) {
        float diameter = Math.max(rect.width(), rect.height());
        return Math.max(96, Math.min(192, (int) Math.ceil(diameter * 480.0F)));
    }

    private static void addRouteBands(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, float z, List<RouteChip> routes, ProjectionComponentSettings.ColorPolicy colorPolicy, float brightness, boolean horizontal, float opacity, SurfacePass pass) {
        List<RouteChip> normalized = routes == null || routes.isEmpty() ? List.of(new RouteChip("Line", List.of(0xFF3366FF))) : routes;
        if (colorPolicy == ProjectionComponentSettings.ColorPolicy.FIRST_ROUTE) {
            addRouteColorBands(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), z, withOpacity(normalized.getFirst().colors(), opacity), brightness, horizontal, pass);
            return;
        }
        int count = normalized.size();
        for (int i = 0; i < count; i++) {
            ComponentRect segment;
            if (horizontal) {
                float x0 = rect.x() + rect.width() * i / count;
                float x1 = rect.x() + rect.width() * (i + 1) / count;
                segment = new ComponentRect(x0, rect.y(), Math.max(0.001F, x1 - x0), rect.height());
            } else {
                float y0 = rect.y() + rect.height() * i / count;
                float y1 = rect.y() + rect.height() * (i + 1) / count;
                segment = new ComponentRect(rect.x(), y0, rect.width(), Math.max(0.001F, y1 - y0));
            }
            addRouteColorBands(pose, buffer, transform, segment.x(), segment.y(), segment.width(), segment.height(), z, withOpacity(normalized.get(i).colors(), opacity), brightness, horizontal, pass);
        }
    }

    private static void addRouteColorBands(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, float x, float y, float width, float height, float z, List<Integer> colors, float brightness, boolean horizontal, SurfacePass pass) {
        List<Integer> normalized = colors == null || colors.isEmpty() ? List.of(0xFF3366FF) : colors.stream().limit(8).toList();
        if (horizontal) {
            float segment = width / normalized.size();
            for (int i = 0; i < normalized.size(); i++) {
                float xx = x + i * segment;
                addTransformedRect(pose, buffer, transform, xx, y, i == normalized.size() - 1 ? x + width - xx : segment, height, z, withAlphaMultiplier(normalized.get(i), brightness), pass);
            }
        } else {
            float segment = height / normalized.size();
            for (int i = 0; i < normalized.size(); i++) {
                float yy = y + i * segment;
                addTransformedRect(pose, buffer, transform, x, yy, width, i == normalized.size() - 1 ? y + height - yy : segment, z, withAlphaMultiplier(normalized.get(i), brightness), pass);
            }
        }
    }

    private static RouteWindow<RouteChip> routeWindow(List<RouteChip> routes, int maxVisible, ProjectionComponentSettings.RouteOverflowMode overflow, int intervalTicks, int seed) {
        if (routes == null || routes.isEmpty()) {
            return new RouteWindow<>(List.of(), 0);
        }
        if (maxVisible <= 0) {
            return new RouteWindow<>(List.of(), routes.size());
        }
        int visible = Math.max(1, Math.min(maxVisible, routes.size()));
        if (overflow == ProjectionComponentSettings.RouteOverflowMode.ROTATE && routes.size() > visible) {
            long intervalMs = Math.max(10L, intervalTicks) * 50L;
            int start = Math.floorMod((int) (ProjectionRenderFrameContext.timeMillis() / intervalMs) + seed, routes.size());
            List<RouteChip> items = new ArrayList<>(visible);
            for (int i = 0; i < visible; i++) {
                items.add(routes.get((start + i) % routes.size()));
            }
            return new RouteWindow<>(List.copyOf(items), 0);
        }
        return new RouteWindow<>(routes.subList(0, visible), routes.size() - visible);
    }

    private static int firstRouteColor(List<Integer> colors) {
        return colors == null || colors.isEmpty() ? 0xFF3366FF : colors.getFirst();
    }

    private static List<Integer> withOpacity(List<Integer> colors, float opacity) {
        List<Integer> normalized = colors == null || colors.isEmpty() ? List.of(0xFF3366FF) : colors;
        if (opacity >= 0.999F) {
            return normalized;
        }
        return normalized.stream()
                .map(color -> withAlpha(color, Math.round(((color >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, opacity)))))
                .toList();
    }

    private static void addBorder(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, float x, float y, float width, float height, float thickness, float z, int color, SurfacePass pass) {
        addTransformedRect(pose, buffer, transform, x, y, width, thickness, z, color, pass);
        addTransformedRect(pose, buffer, transform, x, y + height - thickness, width, thickness, z, color, pass);
        addTransformedRect(pose, buffer, transform, x, y, thickness, height, z, color, pass);
        addTransformedRect(pose, buffer, transform, x + width - thickness, y, thickness, height, z, color, pass);
    }

    private static String shortRouteLabel(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return "?";
        }
        String[] parts = trimmed.split("\\s+");
        String first = parts.length == 0 ? trimmed : parts[0];
        return first.length() <= 3 ? first : first.substring(0, Math.min(3, first.length()));
    }

    private static int contrastText(List<Integer> colors) {
        int color = colors == null || colors.isEmpty() ? 0xFF3366FF : colors.getFirst();
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 150 ? 0xFF1B1B1B : 0xFFFFFFFF;
    }

    private static void renderCenteredLine(PoseStack poseStack, SubmitNodeCollector collector, Font font, String text, float topY, float maxWidth, float preferredScale, int color) {
        float scale = Math.min(preferredScale, maxWidth / Math.max(1.0F, ProjectionTextMeasureCache.width(font, text)));
        float x = -ProjectionTextMeasureCache.width(font, text) * scale * 0.5F;
        drawText(poseStack, collector, text, x, topY, scale, color, false);
    }

    private static void drawText(PoseStack poseStack, SubmitNodeCollector collector, String text, float x, float topY, float scale, int color, boolean shadow) {
        CanvasBounds bounds = TEXT_CANVAS_BOUNDS.get();
        if (bounds != null && bounds.valid()) {
            ProjectionWorldTextRenderer.drawCanvasClipped(poseStack, collector, font(), text, x, topY, scale, color, shadow, bounds.worldToCanvas(), bounds.minX(), bounds.minY(), bounds.maxX(), bounds.maxY());
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0F, topY, 0.0F);
        poseStack.scale(scale, -scale, scale);
        FormattedCharSequence sequence = Component.literal(text).getVisualOrderText();
        collector.submitText(poseStack, x / scale, 0.0F, sequence, shadow, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT, color, 0, 0);
        poseStack.popPose();
    }

    private static void drawMarqueeText(PoseStack poseStack, SubmitNodeCollector collector, Font font, String text, float x, float topY, float scale, int color, float maxWidth, int seed) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return;
        }
        float safeScale = Math.max(0.001F, scale);
        float safeWidth = Math.max(0.001F, maxWidth);
        float offset = ProjectionTextScroller.offset(font, value, safeWidth / safeScale, seed, ProjectionRenderFrameContext.timeMillis());
        CanvasBounds bounds = TEXT_CANVAS_BOUNDS.get();
        if (bounds != null && bounds.valid()) {
            ProjectionWorldTextRenderer.drawClippedToCanvas(poseStack, collector, font, value, x - offset * safeScale, topY, safeScale, color, false, x, x + safeWidth, bounds.worldToCanvas(), bounds.minX(), bounds.minY(), bounds.maxX(), bounds.maxY());
            return;
        }
        ProjectionWorldTextRenderer.drawClipped(poseStack, collector, font, value, x - offset * safeScale, topY, safeScale, color, false, x, x + safeWidth);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private static StationRenderInfo cachedStationInfo(StationNameProjectorConfig config) {
        clearStaleInfoCache();
        StationInfoCacheKey key = StationInfoCacheKey.of(config);
        StationRenderInfo cached = INFO_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        StationRenderInfo info = stationInfo(config);
        INFO_CACHE.put(key, info);
        trimInfoCache();
        return info;
    }

    private static void clearStaleInfoCache() {
        long routeRevision = ClientRouteDataCache.revision();
        if (routeRevision != lastRouteRevision) {
            INFO_CACHE.clear();
            clearStaticCache();
            lastRouteRevision = routeRevision;
        }
    }

    private static void trimInfoCache() {
        while (INFO_CACHE.size() > INFO_CACHE_MAX_ENTRIES) {
            Iterator<StationInfoCacheKey> iterator = INFO_CACHE.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
        }
    }

    private static StationRenderInfo stationInfo(StationNameProjectorConfig config) {
        Optional<StationGroup> station = config.stationGroupId().flatMap(ClientRouteDataCache::stationGroup);
        if (station.isEmpty()) {
            return StationRenderInfo.unbound();
        }
        StationGroup group = station.get();
        return new StationRenderInfo(
                false,
                group.primaryName(),
                group.translatedNames().isEmpty() ? "" : group.translatedNames().getFirst(),
                routesForStation(group.id())
        );
    }

    private static List<RouteChip> routesForStation(UUID stationGroupId) {
        Map<UUID, RouteChip> routes = new LinkedHashMap<>();
        for (PlatformStop stop : ClientRouteDataCache.platformStopsInStation(stationGroupId)) {
            for (UUID layoutId : ClientRouteDataCache.routeLayoutIdsForPlatformStop(stop.id())) {
                Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(layoutId);
                if (layout.isEmpty()) {
                    continue;
                }
                ClientRouteDataCache.routeLine(layout.get().routeLineId())
                        .filter(RouteLine::visibleOnHud)
                        .ifPresent(line -> routes.putIfAbsent(line.id(), new RouteChip(line.displayName(), List.copyOf(line.themeColors()))));
            }
        }
        return List.copyOf(routes.values());
    }

    private static void addColorStripe(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, float x, float y, float width, float height, float z, List<Integer> colors, float brightness, SurfacePass pass) {
        List<Integer> normalized = colors == null || colors.isEmpty() ? List.of(0xFF34F0B8) : colors.stream().limit(3).toList();
        if (normalized.size() == 1) {
            addTransformedRect(pose, buffer, transform, x, y, width, height, z, multiplyAlpha(normalized.getFirst(), brightness), pass);
            return;
        }
        if (normalized.size() == 2) {
            float half = height * 0.5F;
            addTransformedRect(pose, buffer, transform, x, y, width, half, z, multiplyAlpha(normalized.get(0), brightness), pass);
            addTransformedRect(pose, buffer, transform, x, y + half, width, height - half, z, multiplyAlpha(normalized.get(1), brightness), pass);
            return;
        }
        float edge = Math.max(0.010F, height * 0.25F);
        addTransformedRect(pose, buffer, transform, x, y, width, edge, z, multiplyAlpha(normalized.get(0), brightness), pass);
        addTransformedRect(pose, buffer, transform, x, y + edge, width, height - edge * 2.0F, z, multiplyAlpha(normalized.get(1), brightness), pass);
        addTransformedRect(pose, buffer, transform, x, y + height - edge, width, edge, z, multiplyAlpha(normalized.get(2), brightness), pass);
    }

    private static void addTransformedRect(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, float x, float y, float width, float height, float z, int color, SurfacePass pass) {
        if (!pass.accepts(color)) {
            return;
        }
        if (unrotatedInsideCanvas(transform, x, y, width, height)) {
            addRect(pose, buffer, x, y, width, height, z, color, pass);
            return;
        }
        List<ProjectionQuadClipper.Vertex> clipped = clipTransformedQuad(transform,
                new ProjectionQuadClipper.Vertex(x, y),
                new ProjectionQuadClipper.Vertex(x + width, y),
                new ProjectionQuadClipper.Vertex(x + width, y + height),
                new ProjectionQuadClipper.Vertex(x, y + height));
        emitClippedColorQuad(pose.pose(), buffer, z, pass.color(color), clipped);
    }

    private static void addTransformedTexturedRect(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, float z, int color, float u0, float v0, float u1, float v1) {
        if (((color >>> 24) & 0xFF) <= 0) {
            return;
        }
        if (unrotatedInsideCanvas(transform, rect.x(), rect.y(), rect.width(), rect.height())) {
            Matrix4f matrix = pose.pose();
            buffer.addVertex(matrix, rect.x(), rect.y(), z).setUv(u0, v1).setColor(color);
            buffer.addVertex(matrix, rect.x() + rect.width(), rect.y(), z).setUv(u1, v1).setColor(color);
            buffer.addVertex(matrix, rect.x() + rect.width(), rect.y() + rect.height(), z).setUv(u1, v0).setColor(color);
            buffer.addVertex(matrix, rect.x(), rect.y() + rect.height(), z).setUv(u0, v0).setColor(color);
            return;
        }
        List<ProjectionQuadClipper.Vertex> clipped = clipTransformedQuad(transform,
                new ProjectionQuadClipper.Vertex(rect.x(), rect.y(), u0, v1),
                new ProjectionQuadClipper.Vertex(rect.x() + rect.width(), rect.y(), u1, v1),
                new ProjectionQuadClipper.Vertex(rect.x() + rect.width(), rect.y() + rect.height(), u1, v0),
                new ProjectionQuadClipper.Vertex(rect.x(), rect.y() + rect.height(), u0, v0));
        emitClippedTexturedQuad(pose.pose(), buffer, z, color, clipped);
    }

    private static void addTransformedQuad(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, float z, int color, SurfacePass pass) {
        if (!pass.accepts(color)) {
            return;
        }
        if (unrotatedQuadInsideCanvas(transform, x1, y1, x2, y2, x3, y3, x4, y4)) {
            Matrix4f matrix = pose.pose();
            int emittedColor = pass.color(color);
            buffer.addVertex(matrix, x1, y1, z).setColor(emittedColor);
            buffer.addVertex(matrix, x2, y2, z).setColor(emittedColor);
            buffer.addVertex(matrix, x3, y3, z).setColor(emittedColor);
            buffer.addVertex(matrix, x4, y4, z).setColor(emittedColor);
            return;
        }
        List<ProjectionQuadClipper.Vertex> clipped = clipTransformedQuad(transform,
                new ProjectionQuadClipper.Vertex(x1, y1),
                new ProjectionQuadClipper.Vertex(x2, y2),
                new ProjectionQuadClipper.Vertex(x3, y3),
                new ProjectionQuadClipper.Vertex(x4, y4));
        emitClippedColorQuad(pose.pose(), buffer, z, pass.color(color), clipped);
    }

    private static boolean unrotatedInsideCanvas(ComponentTransform transform, float x, float y, float width, float height) {
        if (Math.abs(transform.radians()) > 0.000001F) {
            return false;
        }
        CanvasBounds bounds = transform.canvasBounds();
        return x >= bounds.minX()
                && y >= bounds.minY()
                && x + width <= bounds.maxX()
                && y + height <= bounds.maxY();
    }

    private static boolean unrotatedQuadInsideCanvas(ComponentTransform transform, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4) {
        if (Math.abs(transform.radians()) > 0.000001F) {
            return false;
        }
        CanvasBounds bounds = transform.canvasBounds();
        return isPointInsideCanvas(bounds, x1, y1)
                && isPointInsideCanvas(bounds, x2, y2)
                && isPointInsideCanvas(bounds, x3, y3)
                && isPointInsideCanvas(bounds, x4, y4);
    }

    private static boolean isPointInsideCanvas(CanvasBounds bounds, float x, float y) {
        return x >= bounds.minX()
                && y >= bounds.minY()
                && x <= bounds.maxX()
                && y <= bounds.maxY();
    }

    private static List<ProjectionQuadClipper.Vertex> clipTransformedQuad(ComponentTransform transform, ProjectionQuadClipper.Vertex first, ProjectionQuadClipper.Vertex second, ProjectionQuadClipper.Vertex third, ProjectionQuadClipper.Vertex fourth) {
        CanvasBounds bounds = transform.canvasBounds();
        return ProjectionQuadClipper.clip(bounds.minX(), bounds.minY(), bounds.maxX(), bounds.maxY(),
                transformVertex(transform, first),
                transformVertex(transform, second),
                transformVertex(transform, third),
                transformVertex(transform, fourth));
    }

    private static ProjectionQuadClipper.Vertex transformVertex(ComponentTransform transform, ProjectionQuadClipper.Vertex vertex) {
        float dx = vertex.x() - transform.centerX();
        float dy = vertex.y() - transform.centerY();
        float sin = (float) Math.sin(transform.radians());
        float cos = (float) Math.cos(transform.radians());
        return new ProjectionQuadClipper.Vertex(
                transform.centerX() + dx * cos - dy * sin,
                transform.centerY() + dx * sin + dy * cos,
                vertex.u(),
                vertex.v()
        );
    }

    private static void emitClippedColorQuad(Matrix4f matrix, VertexConsumer buffer, float z, int color, List<ProjectionQuadClipper.Vertex> vertices) {
        if (vertices.size() < 3) {
            return;
        }
        ProjectionQuadClipper.Vertex first = vertices.getFirst();
        for (int i = 1; i < vertices.size() - 1; i++) {
            addColorVertex(buffer, matrix, first, z, color);
            addColorVertex(buffer, matrix, vertices.get(i), z, color);
            addColorVertex(buffer, matrix, vertices.get(i + 1), z, color);
            addColorVertex(buffer, matrix, first, z, color);
        }
    }

    private static void emitClippedTexturedQuad(Matrix4f matrix, VertexConsumer buffer, float z, int color, List<ProjectionQuadClipper.Vertex> vertices) {
        if (vertices.size() < 3) {
            return;
        }
        ProjectionQuadClipper.Vertex first = vertices.getFirst();
        for (int i = 1; i < vertices.size() - 1; i++) {
            addTexturedVertex(buffer, matrix, first, z, color);
            addTexturedVertex(buffer, matrix, vertices.get(i), z, color);
            addTexturedVertex(buffer, matrix, vertices.get(i + 1), z, color);
            addTexturedVertex(buffer, matrix, first, z, color);
        }
    }

    private static void addColorVertex(VertexConsumer buffer, Matrix4f matrix, ProjectionQuadClipper.Vertex vertex, float z, int color) {
        buffer.addVertex(matrix, vertex.x(), vertex.y(), z).setColor(color);
    }

    private static void addTexturedVertex(VertexConsumer buffer, Matrix4f matrix, ProjectionQuadClipper.Vertex vertex, float z, int color) {
        buffer.addVertex(matrix, vertex.x(), vertex.y(), z).setUv(vertex.u(), vertex.v()).setColor(color);
    }

    private static void addRect(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float width, float height, float z, int color, SurfacePass pass) {
        if (!pass.accepts(color)) {
            return;
        }
        Matrix4f matrix = pose.pose();
        int emittedColor = pass.color(color);
        buffer.addVertex(matrix, x, y, z).setColor(emittedColor);
        buffer.addVertex(matrix, x + width, y, z).setColor(emittedColor);
        buffer.addVertex(matrix, x + width, y + height, z).setColor(emittedColor);
        buffer.addVertex(matrix, x, y + height, z).setColor(emittedColor);
    }

    private static int multiplyAlpha(int color, float multiplier) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * Math.max(0.0F, multiplier));
        return (Math.min(255, alpha) << 24) | (color & 0x00FFFFFF);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static int withAlphaMultiplier(int color, float multiplier) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.5F, multiplier)));
        return (Math.min(255, alpha) << 24) | (color & 0x00FFFFFF);
    }

    private enum SurfacePass {
        COLOR {
            @Override
            boolean accepts(int color) {
                return ((color >>> 24) & 0xFF) > 0;
            }
        };

        abstract boolean accepts(int color);

        int color(int color) {
            return color;
        }
    }

    private enum ProjectionBatchMode {
        ALL {
            @Override
            boolean accepts(boolean dynamic) {
                return true;
            }
        },
        STATIC_ONLY {
            @Override
            boolean accepts(boolean dynamic) {
                return !dynamic;
            }
        },
        DYNAMIC_ONLY {
            @Override
            boolean accepts(boolean dynamic) {
                return dynamic;
            }
        };

        abstract boolean accepts(boolean dynamic);
    }

    private record RenderData(List<ProjectorData> projectors, Vec3 camera) {
        private static final RenderData EMPTY = new RenderData(List.of(), Vec3.ZERO);
    }

    private record ProjectorData(BlockPos pos, Direction facing, StationNameProjectorConfig config, AppliedProjectionLayout layout, StationRenderInfo stationInfo) {
    }

    private record StationRenderInfo(boolean missing, String primaryName, String translation, List<RouteChip> routes) {
        static StationRenderInfo unbound() {
            return new StationRenderInfo(true, "", "", List.of());
        }
    }

    private record StationInfoCacheKey(Optional<UUID> stationGroupId, long routeRevision) {
        private static StationInfoCacheKey of(StationNameProjectorConfig config) {
            return new StationInfoCacheKey(config.stationGroupId(), ClientRouteDataCache.revision());
        }
    }

    private record RouteChip(String name, List<Integer> colors) {
    }

    private record RouteWindow<T>(List<T> items, int hidden) {
    }

    private record TexturedComponent(Identifier textureId, ComponentTransform transform, ComponentRect rect, float z, int color, float u0, float v0, float u1, float v1) {
    }

    private record StaticProjectionKey(
            BlockPos pos,
            Direction facing,
            boolean frontSide,
            StationNameProjectorConfig config,
            float width,
            float height,
            AppliedProjectionLayout layout,
            StationRenderInfo info,
            long routeRevision,
            List<NetworkImageStateKey> networkImages
    ) {
    }

    private record DynamicProjectionKey(
            BlockPos pos,
            Direction facing,
            boolean frontSide,
            StationNameProjectorConfig config,
            float width,
            float height,
            AppliedProjectionLayout layout,
            StationRenderInfo info,
            long routeRevision,
            List<DynamicComponentPhase> phases
    ) {
    }

    private record NetworkImageStateKey(String url, ProjectionNetworkImageCache.Status status, Identifier textureId, int width, int height) {
    }

    private record DynamicComponentPhase(UUID componentId, long phase, String url, ProjectionNetworkImageCache.Status status, Identifier textureId, int width, int height) {
        private static DynamicComponentPhase phase(UUID componentId, long phase) {
            return new DynamicComponentPhase(componentId, phase, "", null, null, 0, 0);
        }

        private static DynamicComponentPhase network(UUID componentId, String url, ProjectionNetworkImageCache.State state) {
            ProjectionNetworkImageCache.State safe = state == null ? new ProjectionNetworkImageCache.State(ProjectionNetworkImageCache.Status.EMPTY, null, 0, 0, "") : state;
            return new DynamicComponentPhase(componentId, 0L, url == null ? "" : url, safe.status(), safe.textureId(), safe.width(), safe.height());
        }
    }

    private static final class StaticProjectionEntry {
        private final ProjectionGpuBatchCache.GpuBatches batches;
        private long lastUsedFrame = staticRenderFrame;

        private StaticProjectionEntry(ProjectionGpuBatchCache.GpuBatches batches) {
            this.batches = batches;
        }

        private void release() {
            this.batches.release();
        }
    }

    private record ComponentRect(float x, float y, float width, float height) {
        float top() {
            return this.y + this.height;
        }
    }

    private record ComponentTransform(float centerX, float centerY, float radians, CanvasBounds canvasBounds) {
    }

    private record CanvasBounds(float minX, float minY, float maxX, float maxY, Matrix4f worldToCanvas) {
        private static CanvasBounds of(float width, float height) {
            return of(width, height, new Matrix4f());
        }

        private static CanvasBounds of(float width, float height, Matrix4f worldToCanvas) {
            return new CanvasBounds(-width * 0.5F, -height * 0.5F, width * 0.5F, height * 0.5F, worldToCanvas);
        }

        private boolean valid() {
            return this.maxX > this.minX && this.maxY > this.minY && this.worldToCanvas != null;
        }
    }
}
