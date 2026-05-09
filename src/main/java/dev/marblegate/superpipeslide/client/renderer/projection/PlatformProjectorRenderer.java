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
import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.client.core.projection.cache.ProjectionBuiltinIconTextureCache;
import dev.marblegate.superpipeslide.client.core.projection.cache.ProjectionNetworkImageCache;
import dev.marblegate.superpipeslide.client.core.projection.cache.ProjectionTextMeasureCache;
import dev.marblegate.superpipeslide.client.core.projection.engine.PlatformLayoutProjectionEngine;
import dev.marblegate.superpipeslide.client.core.projection.engine.PlatformStatusTagProjectionEngine;
import dev.marblegate.superpipeslide.client.core.projection.engine.PlatformTransferProjectionEngine;
import dev.marblegate.superpipeslide.client.core.projection.render.ProjectionQuadClipper;
import dev.marblegate.superpipeslide.client.core.projection.render.ProjectionRenderFrameContext;
import dev.marblegate.superpipeslide.client.core.projection.render.ProjectionTextScroller;
import dev.marblegate.superpipeslide.client.core.projection.render.ProjectionWorldTextRenderer;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.common.block.station.PlatformProjectorBlock;
import dev.marblegate.superpipeslide.common.block.station.PlatformProjectorBlockEntity;
import dev.marblegate.superpipeslide.common.block.station.PlatformProjectorConfig;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionBuiltinIcon;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponent;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponentSettings;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponentType;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionOverflowMode;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionTextAlign;
import dev.marblegate.superpipeslide.common.core.projection.layout.AppliedProjectionLayout;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionImageLayout;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutTarget;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.config.ClientConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

public final class PlatformProjectorRenderer {
    private static final double RENDER_DISTANCE = 128.0D;
    private static final int CHUNK_RADIUS = 9;
    private static final float SURFACE_Z_BASE = 0.00028F;
    private static final float TEXTURE_Z_BASE = 0.00044F;
    private static final float TEXT_Z_BASE = 0.00060F;
    private static final float LAYER_Z_STEP = 0.00042F;
    private static final float PRIMITIVE_Z_STEP = 0.00000220F;
    private static final float SURFACE_BORDER_Z = 0.00023F;
    private static final float SURFACE_OVERLAY_Z = 0.00026F;
    private static final ThreadLocal<CanvasBounds> TEXT_CANVAS_BOUNDS = new ThreadLocal<>();
    private static final int INFO_CACHE_MAX_ENTRIES = 512;
    private static final Map<PlatformInfoCacheKey, PlatformRenderInfo> INFO_CACHE = new LinkedHashMap<>(64, 0.75F, true);
    private static final int STATIC_CACHE_MAX_ENTRIES = 1024;
    private static final long STATIC_CACHE_RETAIN_FRAMES = 600L;
    private static final Map<StaticProjectionKey, StaticProjectionEntry> STATIC_CACHE = new LinkedHashMap<>(64, 0.75F, true);
    private static final int DYNAMIC_CACHE_MAX_ENTRIES = 512;
    private static final long DYNAMIC_CACHE_RETAIN_FRAMES = 80L;
    private static final Map<DynamicProjectionKey, StaticProjectionEntry> DYNAMIC_CACHE = new LinkedHashMap<>(64, 0.75F, true);
    private static final int PLATFORM_LAYOUT_CACHE_MAX_ENTRIES = 48;
    private static final int TRANSFER_LAYOUT_CACHE_MAX_ENTRIES = 64;
    private static final int STATUS_TAG_LAYOUT_CACHE_MAX_ENTRIES = 24;
    private static final float ROTATION_EPSILON = 0.000001F;
    private static final CircleLookup CIRCLE_24 = circleLookup(24);
    private static final CircleLookup CIRCLE_32 = circleLookup(32);
    private static final CircleLookup CIRCLE_48 = circleLookup(48);
    private static final CircleLookup CIRCLE_64 = circleLookup(64);
    private static long lastRouteRevision = Long.MIN_VALUE;
    private static long lastPipeRevision = Long.MIN_VALUE;
    private static long staticRenderFrame;
    private static RenderData lastRenderData = RenderData.EMPTY;
    private static final RenderPipeline PROJECTION_TRANSLUCENT_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipeline/platform_projection_translucent"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
            .withCull(false)
            .build();
    private static final RenderPipeline PROJECTION_TEXTURE_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipeline/platform_projection_texture"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withSampler("Sampler0")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
            .withCull(false)
            .build();
    private static final RenderType PROJECTION_TRANSLUCENT_QUADS = RenderType.create(
            "superpipeslide_platform_projector_translucent_quads",
            RenderSetup.builder(PROJECTION_TRANSLUCENT_PIPELINE).bufferSize(4096).createRenderSetup()
    );
    private static final Map<Identifier, RenderType> PROJECTION_TEXTURE_QUADS = new LinkedHashMap<>();

    private PlatformProjectorRenderer() {
    }

    private static CircleLookup lookupFor(float diameter) {
        if (diameter < 0.05F) {
            return CIRCLE_24;
        }
        if (diameter < 0.12F) {
            return CIRCLE_32;
        }
        if (diameter < 0.24F) {
            return CIRCLE_48;
        }
        return CIRCLE_64;
    }

    private static CircleLookup circleLookup(int segments) {
        int safe = Math.max(12, segments);
        float[] cos = new float[safe + 1];
        float[] sin = new float[safe + 1];
        for (int i = 0; i <= safe; i++) {
            double angle = Math.PI * 2.0D * i / safe;
            cos[i] = (float) Math.cos(angle);
            sin[i] = (float) Math.sin(angle);
        }
        return new CircleLookup(cos, sin);
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

        ClientProjectionProjectorIndex.forPlatformProjectors(level, centerChunkX, centerChunkZ, CHUNK_RADIUS, projector -> {
            Direction facing = projector.getBlockState().getValue(PlatformProjectorBlock.FACING);
            PlatformProjectorConfig config = projector.config();
            AppliedProjectionLayout layout = projector.appliedLayout();
            if (layout.target() != ProjectionLayoutTarget.PLATFORM) {
                return;
            }
            Vec3 center = projectionCenter(projector.getBlockPos(), facing, config);
            boolean frontSide = camera.subtract(center).dot(facing.getUnitVec3()) >= -0.01D;
            if (!frontSide && !config.backsideProjection()) {
                return;
            }
            double panelRadius = Math.hypot(layout.canvas().width(), layout.canvas().height()) * 0.5D + 1.0D;
            if (camera.distanceToSqr(center) <= Math.pow(RENDER_DISTANCE + panelRadius, 2.0D) && projectionVisible(center, panelRadius, frustum)) {
                projectors.add(new ProjectorData(projector.getBlockPos(), facing, config, layout, cachedPlatformInfo(config)));
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
        PlatformProjectorConfig config = projector.config();
        AppliedProjectionLayout layout = projector.layout();
        if (layout.target() != ProjectionLayoutTarget.PLATFORM) {
            return;
        }
        float width = layout.canvas().width();
        float height = layout.canvas().height();
        Vec3 center = projectionCenter(projector.pos(), projector.facing(), config);
        boolean frontSide = camera.subtract(center).dot(projector.facing().getUnitVec3()) >= -0.01D;
        if (!frontSide && !config.backsideProjection()) {
            return;
        }

        StaticProjectionKey staticKey = staticKey(projector, frontSide, width, height);
        if (staticKey != null) {
            StaticProjectionEntry entry = STATIC_CACHE.get(staticKey);
            if (entry == null) {
                entry = new StaticProjectionEntry(compileStaticProjector(projector, frontSide, width, height));
                STATIC_CACHE.put(staticKey, entry);
            }
            entry.lastUsedFrame = staticRenderFrame;
            entry.batches.draw();
        }

        DynamicProjectionKey dynamicKey = dynamicKey(projector, frontSide, width, height);
        if (dynamicKey != null) {
            StaticProjectionEntry entry = DYNAMIC_CACHE.get(dynamicKey);
            if (entry == null) {
                entry = new StaticProjectionEntry(compileDynamicProjector(projector, frontSide, width, height));
                DYNAMIC_CACHE.put(dynamicKey, entry);
            }
            entry.lastUsedFrame = staticRenderFrame;
            entry.batches.draw();
        }

        PoseStack textPoseStack = new PoseStack();
        applyProjectorTransform(textPoseStack, projector.pos(), projector.facing(), projector.config(), frontSide);
        renderProjectionText(textPoseStack, collector, font, projector.layout(), projector.info(), width, height);
    }

    private static ProjectionGpuBatchCache.GpuBatches compileDynamicProjector(ProjectorData projector, boolean frontSide, float width, float height) {
        return ProjectionGpuBatchCache.compile(collector -> {
            PoseStack poseStack = new PoseStack();
            applyProjectorTransform(poseStack, projector.pos(), projector.facing(), projector.config(), frontSide);
            collector.submitCustomGeometry(poseStack, PROJECTION_TRANSLUCENT_QUADS, (pose, buffer) -> renderProjectionSurfaces(pose, buffer, projector.layout(), projector.info(), width, height, SurfacePass.COLOR, ProjectionBatchMode.DYNAMIC_ONLY));
            renderProjectionTextures(poseStack, collector, projector.layout(), projector.info(), width, height, ProjectionBatchMode.DYNAMIC_ONLY);
        });
    }

    private static void applyProjectorTransform(PoseStack poseStack, BlockPos pos, Direction facing, PlatformProjectorConfig config, boolean frontSide) {
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        translateToProjectionPlane(poseStack, facing, config);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        if (!frontSide) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        }
    }

    private static ProjectionGpuBatchCache.GpuBatches compileStaticProjector(ProjectorData projector, boolean frontSide, float width, float height) {
        return ProjectionGpuBatchCache.compile(collector -> {
            PoseStack poseStack = new PoseStack();
            applyProjectorTransform(poseStack, projector.pos(), projector.facing(), projector.config(), frontSide);
            collector.submitCustomGeometry(poseStack, PROJECTION_TRANSLUCENT_QUADS, (pose, buffer) -> renderProjectionSurfaces(pose, buffer, projector.layout(), projector.info(), width, height, SurfacePass.COLOR, ProjectionBatchMode.STATIC_ONLY));
            renderProjectionTextures(poseStack, collector, projector.layout(), projector.info(), width, height, ProjectionBatchMode.STATIC_ONLY);
        });
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
        lastPipeRevision = Long.MIN_VALUE;
    }

    private static StaticProjectionKey staticKey(ProjectorData projector, boolean frontSide, float width, float height) {
        if (!hasStaticRenderableContent(projector.layout(), projector.info())) {
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
                projector.info().signature(),
                ClientRouteDataCache.revision(),
                ClientPipeNetworkCache.revision(),
                networkImageStates(projector.layout(), true)
        );
    }

    private static DynamicProjectionKey dynamicKey(ProjectorData projector, boolean frontSide, float width, float height) {
        List<DynamicComponentPhase> phases = dynamicComponentPhases(projector.layout(), projector.info());
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
                projector.info().signature(),
                ClientRouteDataCache.revision(),
                ClientPipeNetworkCache.revision(),
                phases
        );
    }

    private static boolean hasStaticRenderableContent(AppliedProjectionLayout layout, PlatformRenderInfo info) {
        if (layout.target() != ProjectionLayoutTarget.PLATFORM) {
            return false;
        }
        if (layout.invalid() || info.missing()) {
            return true;
        }
        for (ProjectionComponent component : layout.components()) {
            if (!shouldRenderComponent(component, info)) {
                continue;
            }
            if (!componentSurfaceDynamic(component, info)) {
                return true;
            }
        }
        return false;
    }

    private static boolean componentSurfaceDynamic(ProjectionComponent component, PlatformRenderInfo info) {
        ProjectionComponentSettings settings = component.settings();
        return switch (component.type()) {
            case PLATFORM_TRANSFER_LIST -> settings instanceof ProjectionComponentSettings.PlatformTransferList transferList
                    && transferList.overflow() == ProjectionComponentSettings.RouteOverflowMode.ROTATE
                    && transferCandidates(info.transferData, transferList.includeOutStation()) > transferList.maxVisible();
            case PLATFORM_TRANSFER_MATRIX -> settings instanceof ProjectionComponentSettings.PlatformTransferMatrix transferMatrix
                    && transferMatrix.overflow() == ProjectionComponentSettings.RouteOverflowMode.ROTATE
                    && transferCandidates(info.transferData, transferMatrix.includeOutStation()) > transferMatrix.maxVisible();
            case PLATFORM_LAYOUT_EDITOR_MAP -> true;
            case NETWORK_IMAGE -> settings instanceof ProjectionComponentSettings.NetworkImage image && networkImageSurfaceDynamic(image);
            default -> false;
        };
    }

    private static boolean networkImageSurfaceDynamic(ProjectionComponentSettings.NetworkImage image) {
        ProjectionNetworkImageCache.State state = ProjectionRenderFrameContext.networkImageState(image.url());
        return !state.ready() && shouldShowNetworkPlaceholder(image, state);
    }

    private static boolean isUnrotated(ComponentTransform transform) {
        return Math.abs(transform.radians()) <= ROTATION_EPSILON;
    }

    private static int transferCandidates(List<PlatformTransferProjectionEngine.TransferData> transfers, boolean includeOutStation) {
        if (transfers == null || transfers.isEmpty()) {
            return 0;
        }
        if (includeOutStation) {
            return transfers.size();
        }
        int count = 0;
        for (PlatformTransferProjectionEngine.TransferData transfer : transfers) {
            if (!transfer.outStation()) {
                count++;
            }
        }
        return count;
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

    private static List<DynamicComponentPhase> dynamicComponentPhases(AppliedProjectionLayout layout, PlatformRenderInfo info) {
        if (layout.invalid() || info.missing()) {
            return List.of();
        }
        long frameTimeMillis = ProjectionRenderFrameContext.timeMillis();
        List<DynamicComponentPhase> phases = new ArrayList<>();
        for (ProjectionComponent component : layout.components()) {
            if (!shouldRenderComponent(component, info) || !componentSurfaceDynamic(component, info)) {
                continue;
            }
            ProjectionComponentSettings settings = component.settings();
            if (component.type() == ProjectionComponentType.PLATFORM_LAYOUT_EDITOR_MAP && settings instanceof ProjectionComponentSettings.PlatformLayoutMap map) {
                phases.add(DynamicComponentPhase.phase(component.id(), PlatformPrimitiveKey.of(map, frameTimeMillis).phase()));
            } else if (component.type() == ProjectionComponentType.PLATFORM_TRANSFER_LIST && settings instanceof ProjectionComponentSettings.PlatformTransferList transferList) {
                phases.add(DynamicComponentPhase.phase(component.id(), TransferPrimitiveKey.list(transferList, frameTimeMillis, componentRect(component, layout.canvas().width(), layout.canvas().height()).hashCode(), transferCandidates(info.transferData, transferList.includeOutStation())).phase()));
            } else if (component.type() == ProjectionComponentType.PLATFORM_TRANSFER_MATRIX && settings instanceof ProjectionComponentSettings.PlatformTransferMatrix transferMatrix) {
                phases.add(DynamicComponentPhase.phase(component.id(), TransferPrimitiveKey.matrix(transferMatrix, frameTimeMillis, componentRect(component, layout.canvas().width(), layout.canvas().height()).hashCode(), transferCandidates(info.transferData, transferMatrix.includeOutStation())).phase()));
            } else if (settings instanceof ProjectionComponentSettings.NetworkImage image) {
                ProjectionNetworkImageCache.State state = ProjectionRenderFrameContext.networkImageState(image.url());
                phases.add(DynamicComponentPhase.network(component.id(), image.url(), state));
            } else {
                phases.add(DynamicComponentPhase.phase(component.id(), frameTimeMillis / 50L));
            }
        }
        return List.copyOf(phases);
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

    private static Vec3 projectionCenter(BlockPos pos, Direction facing, PlatformProjectorConfig config) {
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

    private static void translateToProjectionPlane(PoseStack poseStack, Direction facing, PlatformProjectorConfig config) {
        ClientProjectionGeometry.translateToProjectionPlane(poseStack, facing, config.offsetX(), config.offsetY());
    }

    private static void renderProjectionSurfaces(PoseStack.Pose pose, VertexConsumer buffer, AppliedProjectionLayout layout, PlatformRenderInfo info, float width, float height, SurfacePass pass, ProjectionBatchMode mode) {
        if (layout.invalid()) {
            addRect(pose, buffer, -width * 0.5F, -height * 0.5F, width, height, SURFACE_Z_BASE, 0xFF350909, pass);
            addRect(pose, buffer, -width * 0.5F, -height * 0.5F, width, 0.018F, SURFACE_Z_BASE + SURFACE_OVERLAY_Z, 0xFFFF6B6B, pass);
            addRect(pose, buffer, -width * 0.5F, height * 0.5F - 0.018F, width, 0.018F, SURFACE_Z_BASE + SURFACE_OVERLAY_Z, 0xFFFF6B6B, pass);
            return;
        }
        long frameTimeMillis = ProjectionRenderFrameContext.timeMillis();
        for (ProjectionComponent component : layout.components()) {
            if (!shouldRenderComponent(component, info)) {
                continue;
            }
            boolean dynamic = componentSurfaceDynamic(component, info);
            if (!mode.accepts(dynamic)) {
                continue;
            }
            renderComponentSurface(pose, buffer, component, width, height, info, frameTimeMillis, pass);
        }
    }

    private static void renderProjectionText(PoseStack poseStack, SubmitNodeCollector collector, Font font, AppliedProjectionLayout layout, PlatformRenderInfo info, float width, float height) {
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, TEXT_Z_BASE);
        Matrix4f worldToCanvas = new Matrix4f(poseStack.last().pose()).invert();
        TEXT_CANVAS_BOUNDS.set(CanvasBounds.of(width, height, worldToCanvas));
        try {
            if (layout.invalid()) {
                renderCenteredLine(poseStack, collector, font, Component.translatable("screen.superpipeslide.station_projector.layout_invalid").getString(), 0.03F, width * 0.72F, Math.min(0.045F, height * 0.095F), 0xFFFFE2E2);
                renderCenteredLine(poseStack, collector, font, layout.errorMessage(), -0.13F, width * 0.72F, Math.min(0.023F, height * 0.05F), 0xFFFFA0A0);
                return;
            }
            if (info.missing()) {
                renderCenteredLine(poseStack, collector, font, Component.translatable("screen.superpipeslide.platform_projector.unbound").getString(), 0.0F, width * 0.72F, Math.min(0.05F, height * 0.105F), 0xFFFFD36A);
                renderCenteredLine(poseStack, collector, font, Component.translatable("screen.superpipeslide.platform_projector.unbound.hint").getString(), -0.16F, width * 0.72F, Math.min(0.026F, height * 0.055F), 0xFFBFD4DC);
                return;
            }
            long frameTimeMillis = ProjectionRenderFrameContext.timeMillis();
            for (ProjectionComponent component : layout.components()) {
                if (!shouldRenderComponent(component, info)) {
                    continue;
                }
                renderComponentText(poseStack, collector, font, component, width, height, info, frameTimeMillis);
            }
        } finally {
            TEXT_CANVAS_BOUNDS.remove();
            poseStack.popPose();
        }
    }

    private static boolean shouldRenderComponent(ProjectionComponent component, PlatformRenderInfo info) {
        return switch (component.visibleCondition()) {
            case ALWAYS -> true;
            case HAS_PLATFORM -> !info.missing();
            case HAS_PLATFORM_DISPLAY_NAME -> !info.platformName().isBlank();
            case HAS_CURRENT_LINE -> info.line().isPresent();
            case HAS_ROUTE_LAYOUT -> !info.layoutName().isBlank() || !info.stops().isEmpty();
            case HAS_NEXT_STOP -> !info.nextStop().isBlank();
            case HAS_PREVIOUS_STOP -> !info.previousStop().isBlank();
            case HAS_TERMINAL -> !info.terminalStop().isBlank();
            case HAS_TRANSFERS -> !info.transfers().isEmpty();
            case HAS_OUT_OF_STATION_TRANSFERS -> info.hasOutStationTransfers();
            case MULTI_TRANSFER -> info.transfers().size() > 1;
            case BIDIRECTIONAL_LAYOUT -> info.anyBidirectional();
            case LOOP_LAYOUT -> info.anyLoop();
            default -> false;
        };
    }

    private static void renderComponentSurface(PoseStack.Pose pose, VertexConsumer buffer, ProjectionComponent component, float width, float height, PlatformRenderInfo info, long frameTimeMillis, SurfacePass pass) {
        ComponentRect rect = componentRect(component, width, height);
        ComponentTransform transform = componentTransform(component, rect, width, height);
        ProjectionComponentSettings settings = component.settings();
        float z = componentZ(component, SURFACE_Z_BASE);
        switch (component.type()) {
            case BACKGROUND_PANEL -> {
                ProjectionComponentSettings.Panel panel = (ProjectionComponentSettings.Panel) settings;
                int fill = withAlpha(panel.fillColor(), Math.round(((panel.fillColor() >>> 24) & 0xFF) * panel.opacity()));
                addTransformedRect(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), z, fill, pass);
                if (panel.borderWidth() > 0.001F && ((panel.borderColor() >>> 24) & 0xFF) > 0) {
                    addBorder(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), Math.min(panel.borderWidth(), Math.min(rect.width(), rect.height()) * 0.22F), z + SURFACE_BORDER_Z, panel.borderColor(), pass);
                }
            }
            case DIVIDER -> {
                ProjectionComponentSettings.Divider divider = (ProjectionComponentSettings.Divider) settings;
                float thickness = Math.min(Math.max(0.004F, divider.thickness()), Math.max(0.004F, rect.height()));
                if (!divider.dashed()) {
                    addTransformedRect(pose, buffer, transform, rect.x(), rect.y() + (rect.height() - thickness) * 0.5F, rect.width(), thickness, z, divider.color(), pass);
                } else {
                    float dash = Math.max(thickness * 3.0F, rect.width() * 0.045F);
                    for (float x = rect.x(); x < rect.x() + rect.width(); x += dash * 2.0F) {
                        addTransformedRect(pose, buffer, transform, x, rect.y() + (rect.height() - thickness) * 0.5F, Math.min(dash, rect.x() + rect.width() - x), thickness, z, divider.color(), pass);
                    }
                }
            }
            case PLATFORM_BADGE -> renderBadgeSurface(pose, buffer, transform, rect, (ProjectionComponentSettings.PlatformBadge) settings, info, z, pass);
            case PLATFORM_LINE_CURRENT, PLATFORM_LINE_BAND, PLATFORM_TERMINAL_STRIP -> renderLineSurface(pose, buffer, transform, rect, (ProjectionComponentSettings.PlatformLine) settings, info, z, pass);
            case PLATFORM_LINE_ICON -> renderLineIconSurface(pose, buffer, transform, rect, (ProjectionComponentSettings.PlatformLineIcon) settings, info, z, pass);
            case PLATFORM_TRANSFER_LIST -> renderTransferListSurface(pose, buffer, transform, rect, (ProjectionComponentSettings.PlatformTransferList) settings, info, frameTimeMillis, z, pass);
            case PLATFORM_TRANSFER_MATRIX -> renderTransferMatrixSurface(pose, buffer, transform, rect, (ProjectionComponentSettings.PlatformTransferMatrix) settings, info, frameTimeMillis, z, pass);
            case PLATFORM_LAYOUT_STOP_LIST, PLATFORM_LAYOUT_PHYSICAL_MAP, PLATFORM_LAYOUT_PRACTICAL_MAP, PLATFORM_LAYOUT_SCHEMATIC_MAP, PLATFORM_LAYOUT_EDITOR_MAP -> renderPlatformLayoutSurface(pose, buffer, transform, rect, (ProjectionComponentSettings.PlatformLayoutMap) settings, info, frameTimeMillis, z, pass);
            case PLATFORM_STATUS_TAGS -> renderStatusTagSurface(pose, buffer, transform, rect, (ProjectionComponentSettings.PlatformStatusTags) settings, info, z, pass);
            case BUILTIN_ICON -> renderBuiltinIconChrome(pose, buffer, transform, rect, (ProjectionComponentSettings.BuiltinIcon) settings, z, pass);
            case NETWORK_IMAGE -> renderNetworkImageChrome(pose, buffer, transform, rect, (ProjectionComponentSettings.NetworkImage) settings, z, pass);
            default -> {
            }
        }
    }

    private static void renderBuiltinIconChrome(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, ProjectionComponentSettings.BuiltinIcon settings, float z, SurfacePass pass) {
        if (settings.backgroundEnabled() && (settings.backgroundColor() >>> 24) > 0) {
            addTransformedRect(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), z, settings.backgroundColor(), pass);
        }
        if (settings.borderEnabled() && settings.borderWidth() > 0.001F && (settings.borderColor() >>> 24) > 0) {
            addBorder(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), Math.min(settings.borderWidth(), Math.min(rect.width(), rect.height()) * 0.35F), z + SURFACE_BORDER_Z, settings.borderColor(), pass);
        }
    }

    private static void renderNetworkImageChrome(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, ProjectionComponentSettings.NetworkImage settings, float z, SurfacePass pass) {
        if (settings.backgroundEnabled() && (settings.backgroundColor() >>> 24) > 0) {
            addTransformedRect(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), z, settings.backgroundColor(), pass);
        }
        if (settings.borderEnabled() && settings.borderWidth() > 0.001F && (settings.borderColor() >>> 24) > 0) {
            addBorder(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), Math.min(settings.borderWidth(), Math.min(rect.width(), rect.height()) * 0.35F), z + SURFACE_BORDER_Z, settings.borderColor(), pass);
        }
        ProjectionNetworkImageCache.State state = ProjectionRenderFrameContext.networkImageState(settings.url());
        if (state.ready() || !shouldShowNetworkPlaceholder(settings, state)) {
            return;
        }
        int color = isLoading(state) ? 0x9937C3BB : 0x99FF8A6A;
        if (state.status() != ProjectionNetworkImageCache.Status.EMPTY) {
            addTransformedRect(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), z + SURFACE_OVERLAY_Z, isLoading(state) ? 0x2219E4D8 : 0x22FF705C, pass);
        }
        float mark = Math.max(0.012F, Math.min(rect.width(), rect.height()) * 0.18F);
        float thickness = Math.max(0.003F, mark * 0.14F);
        float cx = rect.x() + rect.width() * 0.5F;
        float cy = rect.y() + rect.height() * 0.5F;
        addTransformedRect(pose, buffer, transform, cx - mark, cy - thickness * 0.5F, mark * 2.0F, thickness, z + SURFACE_OVERLAY_Z, color, pass);
        addTransformedRect(pose, buffer, transform, cx - thickness * 0.5F, cy - mark, thickness, mark * 2.0F, z + SURFACE_OVERLAY_Z, color, pass);
    }

    private static void renderComponentText(PoseStack poseStack, SubmitNodeCollector collector, Font font, ProjectionComponent component, float width, float height, PlatformRenderInfo info, long frameTimeMillis) {
        ComponentRect rect = componentRect(component, width, height);
        ProjectionComponentSettings settings = component.settings();
        poseStack.pushPose();
        poseStack.translate(rect.x() + rect.width() * 0.5F, rect.y() + rect.height() * 0.5F, componentLayerZ(component));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-component.rotationDegrees()));
        poseStack.translate(-(rect.x() + rect.width() * 0.5F), -(rect.y() + rect.height() * 0.5F), 0.0F);
        switch (component.type()) {
            case CUSTOM_TEXT -> drawComponentLine(poseStack, collector, font, component.text(), rect, (ProjectionComponentSettings.Text) settings);
            case PLATFORM_TITLE_GROUP -> drawPlatformTitle(poseStack, collector, font, rect, info, (ProjectionComponentSettings.PlatformTitleGroup) settings);
            case PLATFORM_BADGE -> drawComponentLine(poseStack, collector, font, badgeLabel(info, (ProjectionComponentSettings.PlatformBadge) settings), rect, textSettings(((ProjectionComponentSettings.PlatformBadge) settings).textColor(), ((ProjectionComponentSettings.PlatformBadge) settings).fontSize(), ProjectionTextAlign.CENTER, ProjectionOverflowMode.SCALE));
            case PLATFORM_DIRECTION_TITLE -> drawComponentLine(poseStack, collector, font, directionLabel(info, (ProjectionComponentSettings.PlatformDirection) settings), rect, textSettings(((ProjectionComponentSettings.PlatformDirection) settings).textColor(), ((ProjectionComponentSettings.PlatformDirection) settings).fontSize(), ((ProjectionComponentSettings.PlatformDirection) settings).align(), ((ProjectionComponentSettings.PlatformDirection) settings).overflow()));
            case PLATFORM_STATUS_TAGS -> drawStatusTagText(poseStack, collector, font, rect, info, (ProjectionComponentSettings.PlatformStatusTags) settings);
            case PLATFORM_LINE_CURRENT, PLATFORM_LINE_BAND, PLATFORM_TERMINAL_STRIP -> drawLineText(poseStack, collector, font, rect, info, (ProjectionComponentSettings.PlatformLine) settings);
            case PLATFORM_LINE_ICON -> drawLineIconText(poseStack, collector, font, rect, info, (ProjectionComponentSettings.PlatformLineIcon) settings);
            case PLATFORM_TRANSFER_LIST -> drawTransferListText(poseStack, collector, font, rect, info, (ProjectionComponentSettings.PlatformTransferList) settings, frameTimeMillis);
            case PLATFORM_TRANSFER_MATRIX -> drawTransferMatrixText(poseStack, collector, font, rect, info, (ProjectionComponentSettings.PlatformTransferMatrix) settings, frameTimeMillis);
            case PLATFORM_LAYOUT_STOP_LIST, PLATFORM_LAYOUT_PHYSICAL_MAP, PLATFORM_LAYOUT_PRACTICAL_MAP, PLATFORM_LAYOUT_SCHEMATIC_MAP, PLATFORM_LAYOUT_EDITOR_MAP -> drawPlatformLayoutText(poseStack, collector, font, rect, info, (ProjectionComponentSettings.PlatformLayoutMap) settings, frameTimeMillis);
            case NETWORK_IMAGE -> drawNetworkImageStatusText(poseStack, collector, font, rect, (ProjectionComponentSettings.NetworkImage) settings);
            default -> {
            }
        }
        poseStack.popPose();
    }

    private static void renderProjectionTextures(PoseStack poseStack, SubmitNodeCollector collector, AppliedProjectionLayout layout, PlatformRenderInfo info, float width, float height, ProjectionBatchMode mode) {
        if (layout.invalid()) {
            return;
        }
        Map<Identifier, List<TexturedComponent>> batches = new LinkedHashMap<>();
        for (ProjectionComponent component : layout.components()) {
            if (!shouldRenderComponent(component, info)) {
                continue;
            }
            boolean dynamic = componentSurfaceDynamic(component, info);
            if (!mode.accepts(dynamic)) {
                continue;
            }
            for (TexturedComponent item : texturedComponents(component, width, height)) {
                batches.computeIfAbsent(item.textureId(), ignored -> new ArrayList<>()).add(item);
            }
        }
        for (Map.Entry<Identifier, List<TexturedComponent>> entry : batches.entrySet()) {
            RenderType renderType = texturedRenderType(entry.getKey());
            collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
                for (TexturedComponent item : entry.getValue()) {
                    addTransformedTexturedRect(pose, buffer, item.transform(), item.rect(), item.z(), item.color(), item.u0(), item.v0(), item.u1(), item.v1());
                }
            });
        }
    }

    private static List<TexturedComponent> texturedComponents(ProjectionComponent component, float width, float height) {
        ProjectionComponentSettings settings = component.settings();
        ComponentRect rect = componentRect(component, width, height);
        ComponentTransform transform = componentTransform(component, rect, width, height);
        float z = componentZ(component, TEXTURE_Z_BASE);
        if (settings instanceof ProjectionComponentSettings.BuiltinIcon iconSettings) {
            ProjectionBuiltinIcon icon = ProjectionBuiltinIcon.byId(iconSettings.iconId());
            ProjectionBuiltinIconTextureCache.IconTexture texture = ProjectionBuiltinIconTextureCache.textureFor(icon, iconSettings);
            float padding = Math.min(rect.width(), rect.height()) * iconSettings.padding();
            if (iconSettings.fitMode() == ProjectionComponentSettings.ImageFitMode.TILE) {
                return tiledIconComponents(texture.textureId(), transform, ProjectionImageLayout.resolveIconTiles(rect.x(), rect.y(), rect.width(), rect.height(), iconSettings.anchor(), padding, iconSettings.imageScale(), iconSettings.tileGap()), z, texture.color(), texture.u0(), texture.v0(), texture.u1(), texture.v1());
            }
            ProjectionImageLayout.Resolved resolved = ProjectionImageLayout.resolveIcon(rect.x(), rect.y(), rect.width(), rect.height(), iconSettings.fitMode(), iconSettings.anchor(), padding, iconSettings.imageScale());
            return List.of(new TexturedComponent(texture.textureId(), transform, new ComponentRect(resolved.x(), resolved.y(), resolved.width(), resolved.height()), z + 0.00003F, texture.color(), texture.u0(), texture.v0(), texture.u1(), texture.v1()));
        }
        if (settings instanceof ProjectionComponentSettings.NetworkImage imageSettings) {
            ProjectionNetworkImageCache.State state = ProjectionRenderFrameContext.networkImageState(imageSettings.url());
            if (!state.ready()) {
                return List.of();
            }
            ProjectionImageLayout.Resolved resolved = ProjectionImageLayout.resolve(rect.x(), rect.y(), rect.width(), rect.height(), state.width(), state.height(), imageSettings.fitMode(), imageSettings.anchor(), 0.0F, imageSettings.cropX(), imageSettings.cropY(), imageSettings.cropW(), imageSettings.cropH());
            int color = withAlpha(0xFFFFFFFF, Math.round(255.0F * imageSettings.opacity()));
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
                    "superpipeslide_platform_projector_texture_" + sanitizeRenderTypeName(id),
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

    private static void drawNetworkImageStatusText(PoseStack poseStack, SubmitNodeCollector collector, Font font, ComponentRect rect, ProjectionComponentSettings.NetworkImage settings) {
        ProjectionNetworkImageCache.State state = ProjectionRenderFrameContext.networkImageState(settings.url());
        if (state.ready() || !shouldShowNetworkPlaceholder(settings, state)) {
            return;
        }
        boolean loading = isLoading(state);
        if (!loading && settings.fallbackMode() == ProjectionComponentSettings.ImageFallbackMode.COMPACT) {
            return;
        }
        String key = state.messageKey() == null || state.messageKey().isBlank() ? "screen.superpipeslide.projection_image.failed" : state.messageKey();
        drawComponentLine(poseStack, collector, font, Component.translatable(key).getString(), new ComponentRect(rect.x() + rect.width() * 0.06F, rect.y() + rect.height() * 0.10F, rect.width() * 0.88F, rect.height() * 0.32F), textSettings(loading ? 0xFF37C3BB : 0xFFFF8A6A, Math.min(0.035F, Math.max(0.012F, rect.height() * 0.16F)), ProjectionTextAlign.CENTER, ProjectionOverflowMode.SCALE));
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

    private static void renderBadgeSurface(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, ProjectionComponentSettings.PlatformBadge settings, PlatformRenderInfo info, float z, SurfacePass pass) {
        int fill = settings.useLineColor() ? info.line().map(RouteChip::firstColor).orElse(settings.fillColor()) : settings.fillColor();
        int border = settings.borderColor();
        if (settings.style() == ProjectionComponentSettings.PlatformBadgeStyle.TEXT_ONLY) {
            return;
        }
        if (settings.style() == ProjectionComponentSettings.PlatformBadgeStyle.OUTLINE) {
            addBorder(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), Math.max(0.004F, settings.borderWidth()), z, border, pass);
            return;
        }
        if (settings.style() == ProjectionComponentSettings.PlatformBadgeStyle.CAPSULE) {
            if (settings.borderWidth() > 0.0005F && (border >>> 24) > 0) {
                addFilledCapsule(pose, buffer, transform, rect, z, border, pass);
                addFilledCapsule(pose, buffer, transform, inset(rect, settings.borderWidth()), z + SURFACE_OVERLAY_Z, fill, pass);
            } else {
                addFilledCapsule(pose, buffer, transform, rect, z, fill, pass);
            }
        } else {
            addTransformedRect(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), z, fill, pass);
        }
        if (settings.style() != ProjectionComponentSettings.PlatformBadgeStyle.CAPSULE && settings.borderWidth() > 0.0005F && (border >>> 24) > 0) {
            addBorder(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), settings.borderWidth(), z + SURFACE_BORDER_Z, border, pass);
        }
    }

    private static void renderLineSurface(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, ProjectionComponentSettings.PlatformLine settings, PlatformRenderInfo info, float z, SurfacePass pass) {
        int color = info.line().map(RouteChip::firstColor).orElse(0xFF3399FF);
        boolean horizontal = settings.direction() == ProjectionComponentSettings.StripeDirection.HORIZONTAL;
        if (settings.style() == ProjectionComponentSettings.PlatformLineStyle.BAND || settings.style() == ProjectionComponentSettings.PlatformLineStyle.TERMINAL_STRIP) {
            addRouteColorBands(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), z, info.line().map(RouteChip::colors).orElse(List.of(color)), rect.width() >= rect.height(), pass);
            return;
        }
        float lineWidth = Math.min(settings.lineWidth(), horizontal ? rect.height() : rect.width());
        float x = horizontal ? rect.x() : rect.x() + (rect.width() - lineWidth) * 0.5F;
        float y = horizontal ? rect.y() + (rect.height() - lineWidth) * 0.5F : rect.y();
        float w = horizontal ? rect.width() : lineWidth;
        float h = horizontal ? lineWidth : rect.height();
        addTransformedRect(pose, buffer, transform, x, y, w, h, z, color, pass);
        if (settings.style() == ProjectionComponentSettings.PlatformLineStyle.CURRENT_NODE && settings.nodeStyle() != ProjectionComponentSettings.PlatformNodeStyle.NONE) {
            float node = Math.min(Math.max(0.01F, settings.nodeSize()), Math.min(rect.width(), rect.height()));
            ComponentRect nodeRect = new ComponentRect(rect.x() + (rect.width() - node) * 0.5F, rect.y() + (rect.height() - node) * 0.5F, node, node);
            addNodeShape(pose, buffer, transform, nodeRect, settings.nodeStyle(), Math.max(0.002F, lineWidth), z + SURFACE_OVERLAY_Z, color, pass);
        }
    }

    private static void renderLineIconSurface(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, ProjectionComponentSettings.PlatformLineIcon settings, PlatformRenderInfo info, float z, SurfacePass pass) {
        float size = Math.min(Math.max(0.01F, settings.iconSize()), Math.min(rect.width(), rect.height()));
        ComponentRect icon = new ComponentRect(rect.x() + (rect.width() - size) * 0.5F, rect.y() + (rect.height() - size) * 0.5F, size, size);
        int fill = settings.useLineColor() ? info.line().map(RouteChip::firstColor).orElse(settings.fillColor()) : settings.fillColor();
        addIconShape(pose, buffer, transform, icon, settings.shape(), settings.outline(), z, fill, settings.borderColor(), settings.borderWidth(), settings.ringThicknessRatio(), pass);
    }

    private static void renderTransferListSurface(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, ProjectionComponentSettings.PlatformTransferList settings, PlatformRenderInfo info, long frameTimeMillis, float z, SurfacePass pass) {
        PlatformTransferProjectionEngine.Layout layout = info.transferListLayout(settings, frameTimeMillis, rect.hashCode());
        renderTransferPrimitives(pose, buffer, transform, rect, layout, z, pass);
    }

    private static void renderTransferMatrixSurface(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, ProjectionComponentSettings.PlatformTransferMatrix settings, PlatformRenderInfo info, long frameTimeMillis, float z, SurfacePass pass) {
        PlatformTransferProjectionEngine.Layout layout = info.transferMatrixLayout(settings, frameTimeMillis, rect.hashCode());
        renderTransferPrimitives(pose, buffer, transform, rect, layout, z, pass);
    }

    private static void renderPlatformLayoutSurface(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, ProjectionComponentSettings.PlatformLayoutMap settings, PlatformRenderInfo info, long frameTimeMillis, float z, SurfacePass pass) {
        PlatformLayoutProjectionEngine.Layout layout = info.platformLayout(settings, frameTimeMillis);
        for (PlatformLayoutProjectionEngine.Primitive primitive : layout.primitives()) {
            if (primitive instanceof PlatformLayoutProjectionEngine.Text) {
                continue;
            }
            addPlatformLayoutPrimitive(pose, buffer, transform, rect, primitive, z, pass);
        }
    }

    private static void renderTransferPrimitives(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, PlatformTransferProjectionEngine.Layout layout, float z, SurfacePass pass) {
        for (PlatformTransferProjectionEngine.Primitive primitive : layout.primitives()) {
            if (primitive instanceof PlatformTransferProjectionEngine.Text) {
                continue;
            }
            addTransferPrimitive(pose, buffer, transform, rect, primitive, z, pass);
        }
    }

    private static void renderStatusTagSurface(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, ProjectionComponentSettings.PlatformStatusTags settings, PlatformRenderInfo info, float z, SurfacePass pass) {
        PlatformStatusTagProjectionEngine.Layout layout = info.statusTagLayout(settings, rect.width(), rect.height());
        for (PlatformStatusTagProjectionEngine.Primitive primitive : layout.primitives()) {
            if (primitive instanceof PlatformStatusTagProjectionEngine.Capsule capsule) {
                ComponentRect rr = normalizedRect(rect, capsule.x(), capsule.y(), capsule.width(), capsule.height());
                addFilledCapsule(pose, buffer, transform, rr, z + primitiveLayerZ(capsule.layer()), capsule.color(), pass);
            }
        }
    }

    private static void drawPlatformTitle(PoseStack poseStack, SubmitNodeCollector collector, Font font, ComponentRect rect, PlatformRenderInfo info, ProjectionComponentSettings.PlatformTitleGroup settings) {
        String primary;
        String secondary;
        switch (settings.content()) {
            case PLATFORM_AND_STATION -> {
                primary = info.platformNameOrNumber();
                secondary = info.stationName();
            }
            case STATION_ONLY -> {
                primary = info.stationName();
                secondary = "";
            }
            case PLATFORM_ONLY -> {
                primary = info.platformNameOrNumber();
                secondary = "";
            }
            default -> {
                primary = info.stationName();
                secondary = info.platformNameOrNumber();
            }
        }
        if (settings.orientation() == ProjectionComponentSettings.TextOrientation.ROTATE_CW || settings.orientation() == ProjectionComponentSettings.TextOrientation.ROTATE_CCW) {
            poseStack.pushPose();
            poseStack.translate(rect.x() + rect.width() * 0.5F, rect.y() + rect.height() * 0.5F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(settings.orientation() == ProjectionComponentSettings.TextOrientation.ROTATE_CW ? -90.0F : 90.0F));
            ComponentRect rotated = new ComponentRect(-rect.height() * 0.5F, -rect.width() * 0.5F, rect.height(), rect.width());
            drawPlatformTitleHorizontal(poseStack, collector, font, rotated, primary, secondary, settings);
            poseStack.popPose();
            return;
        }
        drawPlatformTitleHorizontal(poseStack, collector, font, rect, primary, secondary, settings);
    }

    private static void drawPlatformTitleHorizontal(PoseStack poseStack, SubmitNodeCollector collector, Font font, ComponentRect rect, String primary, String secondary, ProjectionComponentSettings.PlatformTitleGroup settings) {
        ProjectionComponentSettings.Text primaryText = textSettings(settings.primaryColor(), settings.primaryFontSize(), settings.align(), settings.primaryOverflow());
        ProjectionComponentSettings.Text secondaryText = textSettings(settings.secondaryColor(), settings.secondaryFontSize(), settings.align(), settings.secondaryOverflow());
        if (secondary == null || secondary.isBlank()) {
            ProjectionComponentSettings.Text text = settings.missingSecondaryMode() == ProjectionComponentSettings.MissingTranslationMode.EXPAND_PRIMARY
                    ? textSettings(settings.primaryColor(), settings.primaryFontSize() * settings.missingPrimaryScale(), settings.align(), settings.primaryOverflow())
                    : primaryText;
            drawComponentLine(poseStack, collector, font, primary, rect, text);
            return;
        }
        float primaryH = settings.primaryFontSize();
        float secondaryH = settings.secondaryFontSize();
        float gap = settings.gap();
        float total = Math.min(rect.height(), primaryH + secondaryH + gap);
        float top = rect.top() - Math.max(0.0F, rect.height() - total) * 0.5F;
        drawComponentLine(poseStack, collector, font, primary, new ComponentRect(rect.x(), top - primaryH, rect.width(), primaryH), primaryText);
        drawComponentLine(poseStack, collector, font, secondary, new ComponentRect(rect.x(), top - primaryH - gap - secondaryH, rect.width(), secondaryH), secondaryText);
    }

    private static void drawLineText(PoseStack poseStack, SubmitNodeCollector collector, Font font, ComponentRect rect, PlatformRenderInfo info, ProjectionComponentSettings.PlatformLine settings) {
        if (!settings.showLabel()) {
            return;
        }
        info.line().ifPresent(line -> drawComponentLine(poseStack, collector, font, line.name(), rect, textSettings(settings.textColor(), settings.fontSize(), ProjectionTextAlign.CENTER, settings.overflow())));
    }

    private static void drawLineIconText(PoseStack poseStack, SubmitNodeCollector collector, Font font, ComponentRect rect, PlatformRenderInfo info, ProjectionComponentSettings.PlatformLineIcon settings) {
        if (!settings.showLabel()) {
            return;
        }
        info.line().ifPresent(line -> {
            String label = shortLabel(line.name());
            drawComponentLine(poseStack, collector, font, label, rect, textSettings(settings.textColor(), settings.fontSize(), ProjectionTextAlign.CENTER, ProjectionOverflowMode.SCALE));
        });
    }

    private static void drawTransferListText(PoseStack poseStack, SubmitNodeCollector collector, Font font, ComponentRect rect, PlatformRenderInfo info, ProjectionComponentSettings.PlatformTransferList settings, long frameTimeMillis) {
        PlatformTransferProjectionEngine.Layout layout = info.transferListLayout(settings, frameTimeMillis, rect.hashCode());
        drawTransferPrimitiveTexts(poseStack, collector, font, rect, layout);
    }

    private static void drawTransferMatrixText(PoseStack poseStack, SubmitNodeCollector collector, Font font, ComponentRect rect, PlatformRenderInfo info, ProjectionComponentSettings.PlatformTransferMatrix settings, long frameTimeMillis) {
        PlatformTransferProjectionEngine.Layout layout = info.transferMatrixLayout(settings, frameTimeMillis, rect.hashCode());
        drawTransferPrimitiveTexts(poseStack, collector, font, rect, layout);
    }

    private static void drawPlatformLayoutText(PoseStack poseStack, SubmitNodeCollector collector, Font font, ComponentRect rect, PlatformRenderInfo info, ProjectionComponentSettings.PlatformLayoutMap settings, long frameTimeMillis) {
        PlatformLayoutProjectionEngine.Layout layout = info.platformLayout(settings, frameTimeMillis);
        for (PlatformLayoutProjectionEngine.Primitive primitive : layout.primitives()) {
            if (primitive instanceof PlatformLayoutProjectionEngine.Text text) {
                ComponentRect textRect = normalizedRect(rect, text.x(), text.y(), text.width(), text.height());
                if (Math.abs(text.rotationDegrees()) > 0.01F) {
                    poseStack.pushPose();
                    poseStack.translate(textRect.x() + textRect.width() * 0.5F, textRect.y() + textRect.height() * 0.5F, 0.0F);
                    poseStack.mulPose(Axis.ZP.rotationDegrees(text.rotationDegrees()));
                    drawComponentLine(poseStack, collector, font, text.value(), new ComponentRect(-textRect.width() * 0.5F, -textRect.height() * 0.5F, textRect.width(), textRect.height()), textSettings(text.color(), text.fontSize(), text.align(), text.overflow()));
                    poseStack.popPose();
                    continue;
                }
                drawComponentLine(poseStack, collector, font, text.value(), textRect, textSettings(text.color(), text.fontSize(), text.align(), text.overflow()));
            }
        }
    }

    private static void drawStatusTagText(PoseStack poseStack, SubmitNodeCollector collector, Font font, ComponentRect rect, PlatformRenderInfo info, ProjectionComponentSettings.PlatformStatusTags settings) {
        PlatformStatusTagProjectionEngine.Layout layout = info.statusTagLayout(settings, rect.width(), rect.height());
        for (PlatformStatusTagProjectionEngine.Primitive primitive : layout.primitives()) {
            if (primitive instanceof PlatformStatusTagProjectionEngine.Text text) {
                drawComponentLine(poseStack, collector, font, text.value(), normalizedRect(rect, text.x(), text.y(), text.width(), text.height()), textSettings(text.color(), text.fontSize(), text.align(), text.overflow()));
            }
        }
    }

    private static void drawComponentLine(PoseStack poseStack, SubmitNodeCollector collector, Font font, String text, ComponentRect rect, ProjectionComponentSettings.Text settings) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return;
        }
        if (settings.orientation() == ProjectionComponentSettings.TextOrientation.ROTATE_CW || settings.orientation() == ProjectionComponentSettings.TextOrientation.ROTATE_CCW) {
            poseStack.pushPose();
            poseStack.translate(rect.x() + rect.width() * 0.5F, rect.y() + rect.height() * 0.5F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(settings.orientation() == ProjectionComponentSettings.TextOrientation.ROTATE_CW ? -90.0F : 90.0F));
            drawHorizontalComponentLine(poseStack, collector, font, value, new ComponentRect(-rect.height() * 0.5F, -rect.width() * 0.5F, rect.height(), rect.width()), settings);
            poseStack.popPose();
            return;
        }
        drawHorizontalComponentLine(poseStack, collector, font, value, rect, settings);
    }

    private static void drawHorizontalComponentLine(PoseStack poseStack, SubmitNodeCollector collector, Font font, String value, ComponentRect rect, ProjectionComponentSettings.Text settings) {
        float preferredScale = Math.max(0.004F, settings.fontSize() / Math.max(1.0F, font.lineHeight));
        float maxWidth = rect.width() * 0.92F;
        boolean overflow = ProjectionTextMeasureCache.width(font, value) * preferredScale > maxWidth;
        float scale = settings.overflow() == ProjectionOverflowMode.SCALE ? Math.min(preferredScale, maxWidth / Math.max(1.0F, ProjectionTextMeasureCache.width(font, value))) : preferredScale;
        scale = Math.max(0.004F, scale);
        float textHeight = font.lineHeight * scale;
        float topY = rect.y() + rect.height() * 0.5F + textHeight * 0.5F;
        int color = settings.textColor();
        if (settings.overflow() == ProjectionOverflowMode.MARQUEE && overflow) {
            drawMarqueeText(poseStack, collector, font, value, rect.x() + rect.width() * 0.04F, topY, scale, color, maxWidth, value.hashCode());
            return;
        }
        if (settings.overflow() == ProjectionOverflowMode.HIDE && overflow) {
            return;
        }
        String rendered = settings.overflow() == ProjectionOverflowMode.PLUS_COUNT && overflow ? ellipsizeForWorld(font, value, maxWidth / scale) : value;
        float textWidth = ProjectionTextMeasureCache.width(font, rendered) * scale;
        float x = switch (settings.align()) {
            case CENTER -> rect.x() + (rect.width() - textWidth) * 0.5F;
            case RIGHT -> rect.x() + rect.width() - textWidth - rect.width() * 0.04F;
            case LEFT -> rect.x() + rect.width() * 0.04F;
        };
        drawText(poseStack, collector, rendered, x, topY, scale, color, false);
    }

    private static ProjectionComponentSettings.Text textSettings(int color, float fontSize, ProjectionTextAlign align, ProjectionOverflowMode overflow) {
        return new ProjectionComponentSettings.Text(ProjectionComponentType.CUSTOM_TEXT, "", "", color, fontSize, align, overflow, ProjectionComponentSettings.TextOrientation.HORIZONTAL, 0.02F, 1);
    }

    private static String badgeLabel(PlatformRenderInfo info, ProjectionComponentSettings.PlatformBadge settings) {
        return settings.prefix() + info.platformNumber() + settings.suffix();
    }

    private static String directionLabel(PlatformRenderInfo info, ProjectionComponentSettings.PlatformDirection settings) {
        String target = switch (settings.source()) {
            case NEXT_STOP -> info.nextStop();
            case PREVIOUS_STOP -> info.previousStop();
            case ORIGIN -> info.originStop();
            case LAYOUT_NAME -> info.layoutName();
            case TERMINAL -> info.terminalStop();
        };
        String prefix = platformPrefix(settings.prefix());
        String label = (prefix.isBlank() ? "" : prefix + " ") + fallback(target, info.nextStop());
        String arrow = switch (settings.arrow()) {
            case NONE -> "";
            case LEFT -> arrowText(false);
            case RIGHT -> arrowText(true);
            case BOTH -> arrowText(false) + arrowText(true);
            case AUTO -> arrowText(settings.source() != ProjectionComponentSettings.PlatformDirectionSource.PREVIOUS_STOP && settings.source() != ProjectionComponentSettings.PlatformDirectionSource.ORIGIN);
        };
        if (arrow.isBlank()) {
            return label;
        }
        return settings.arrowPlacement() == ProjectionComponentSettings.ArrowPlacement.AFTER ? label + " " + arrow : arrow + " " + label;
    }

    private static String platformPrefix(ProjectionComponentSettings.PlatformDirectionPrefix prefix) {
        return switch (prefix == null ? ProjectionComponentSettings.PlatformDirectionPrefix.TOWARDS : prefix) {
            case NONE -> "";
            case TOWARDS -> Component.translatable("screen.superpipeslide.platform_projector.towards").getString();
            case NEXT_STOP -> Component.translatable("screen.superpipeslide.projection_designer.platform.next_stop_prefix").getString();
            case PREVIOUS_STOP -> Component.translatable("screen.superpipeslide.projection_designer.platform.previous_stop_prefix").getString();
            case TERMINAL -> Component.translatable("screen.superpipeslide.projection_designer.platform.terminal_prefix").getString();
            case ORIGIN -> Component.translatable("screen.superpipeslide.projection_designer.platform.origin_prefix").getString();
        };
    }

    private static String arrowText(boolean forward) {
        return forward ? ">" : "<";
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static List<String> statusTags(ProjectionComponentSettings.PlatformStatusTags settings, PlatformRenderInfo info) {
        List<String> tags = new ArrayList<>();
        if (settings.showTerminal() && info.terminal()) {
            tags.add(Component.translatable("screen.superpipeslide.platform_projector.tag.terminal").getString());
        }
        boolean serviceScope = settings.scope() == ProjectionComponentSettings.PlatformStatusScope.PLATFORM_SERVICE;
        if (settings.showLoop() && (serviceScope ? info.anyLoop() : info.loop())) {
            tags.add(Component.translatable("screen.superpipeslide.platform_projector.tag.loop").getString());
        }
        if (settings.showBidirectional() && (serviceScope ? info.anyBidirectional() : info.bidirectional())) {
            tags.add(Component.translatable("screen.superpipeslide.platform_projector.tag.bidirectional").getString());
        }
        if (settings.showTransfer() && !info.transfers().isEmpty()) {
            tags.add(Component.translatable("screen.superpipeslide.platform_projector.tag.transfer").getString());
        }
        if (settings.showMissingLine() && info.line().isEmpty()) {
            tags.add(Component.translatable("screen.superpipeslide.platform_projector.tag.no_line").getString());
        }
        return tags;
    }

    private static PlatformRenderInfo cachedPlatformInfo(PlatformProjectorConfig config) {
        clearStaleInfoCache();
        PlatformInfoCacheKey key = PlatformInfoCacheKey.of(config);
        PlatformRenderInfo cached = INFO_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        PlatformRenderInfo info = platformInfo(config);
        INFO_CACHE.put(key, info);
        trimInfoCache();
        return info;
    }

    private static void clearStaleInfoCache() {
        long routeRevision = ClientRouteDataCache.revision();
        long pipeRevision = ClientPipeNetworkCache.revision();
        if (routeRevision != lastRouteRevision || pipeRevision != lastPipeRevision) {
            INFO_CACHE.clear();
            clearStaticCache();
            lastRouteRevision = routeRevision;
            lastPipeRevision = pipeRevision;
        }
    }

    private static void trimInfoCache() {
        while (INFO_CACHE.size() > INFO_CACHE_MAX_ENTRIES) {
            Iterator<PlatformInfoCacheKey> iterator = INFO_CACHE.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
        }
    }

    private static PlatformRenderInfo platformInfo(PlatformProjectorConfig config) {
        Optional<PlatformStop> platform = config.platformStopId().flatMap(ClientRouteDataCache::platformStop);
        if (platform.isEmpty()) {
            return PlatformRenderInfo.unbound();
        }
        PlatformStop stop = platform.get();
        StationGroup station = ClientRouteDataCache.stationGroup(stop.stationGroupId()).orElse(null);
        List<RouteLayout> platformLayouts = platformLayouts(stop);
        RouteLayout layout = resolveLayout(config, stop).orElse(null);
        RouteLine line = resolveLine(stop, layout).orElse(null);
        List<StopChip> stops = layout == null ? List.of() : stopChips(layout, stop.id());
        PlatformLayoutProjectionEngine.Data layoutData = layoutDataFor(stop, config, layout);
        int currentIndex = currentIndex(stops);
        String previous = neighborName(stops, currentIndex, -1, layout != null && layout.loop());
        String next = neighborName(stops, currentIndex, 1, layout != null && layout.loop());
        String origin = stops.isEmpty() ? "" : stops.getFirst().name();
        PlatformProjectorConfig.PlatformProjectionDirection direction = config.direction();
        if (direction == PlatformProjectorConfig.PlatformProjectionDirection.REVERSE) {
            String swapped = previous;
            previous = next;
            next = swapped;
            origin = stops.isEmpty() ? "" : stops.getLast().name();
        }
        String terminal = terminalName(layout, direction, stops);
        return new PlatformRenderInfo(
                false,
                station == null ? "?" : station.primaryName(),
                station == null || station.translatedNames().isEmpty() ? "" : station.translatedNames().getFirst(),
                stop.displayName().orElse(""),
                stop.platformNumber(),
                line == null ? Optional.empty() : Optional.of(new RouteChip(line.id(), line.displayName(), "", "", "", false, List.copyOf(line.themeColors()))),
                layoutName(layout, line),
                origin,
                previous,
                next,
                terminal,
                transfersFor(stop, line == null ? null : line.id()),
                stops,
                layoutData,
                Math.max(0, currentIndex),
                layout != null && layout.bidirectional(),
                layout != null && layout.loop(),
                platformLayouts.stream().anyMatch(RouteLayout::bidirectional),
                platformLayouts.stream().anyMatch(RouteLayout::loop),
                terminalPlatform(layout, stop.id()),
                hasOutStationTransfers(stop)
        );
    }

    private static List<RouteLayout> platformLayouts(PlatformStop stop) {
        return ClientRouteDataCache.routeLayoutIdsForPlatformStop(stop.id()).stream()
                .map(ClientRouteDataCache::routeLayout)
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<RouteLayout> resolveLayout(PlatformProjectorConfig config, PlatformStop stop) {
        List<UUID> layoutIds = ClientRouteDataCache.routeLayoutIdsForPlatformStop(stop.id());
        return config.routeLayoutId()
                .filter(layoutIds::contains)
                .flatMap(ClientRouteDataCache::routeLayout)
                .or(() -> layoutIds.stream().map(ClientRouteDataCache::routeLayout).flatMap(Optional::stream).findFirst());
    }

    private static Optional<RouteLine> resolveLine(PlatformStop stop, RouteLayout layout) {
        if (layout != null) {
            return ClientRouteDataCache.routeLine(layout.routeLineId());
        }
        return stop.routeLineId().flatMap(ClientRouteDataCache::routeLine);
    }

    private static PlatformLayoutProjectionEngine.Data layoutDataFor(PlatformStop platform, PlatformProjectorConfig config, RouteLayout activeLayout) {
        if (activeLayout == null) {
            return PlatformLayoutProjectionEngine.Data.sample();
        }
        PlatformLayoutProjectionEngine.Data data = layoutProjectionData(activeLayout, platform.id(), config.direction());
        return data.stops().isEmpty() ? PlatformLayoutProjectionEngine.Data.sample() : data;
    }

    private static PlatformLayoutProjectionEngine.Data layoutProjectionData(RouteLayout layout, UUID currentStopId, PlatformProjectorConfig.PlatformProjectionDirection direction) {
        RouteLine line = ClientRouteDataCache.routeLine(layout.routeLineId()).orElse(null);
        List<UUID> stopIds = layout.orderedPlatformStops();
        List<PlatformLayoutProjectionEngine.StopData> stops = new ArrayList<>();
        for (int i = 0; i < stopIds.size(); i++) {
            UUID stopId = stopIds.get(i);
            PlatformStop stop = ClientRouteDataCache.platformStop(stopId).orElse(null);
            if (stop == null) {
                continue;
            }
            StationGroup station = ClientRouteDataCache.stationGroup(stop.stationGroupId()).orElse(null);
            String name = station == null ? "?" : station.primaryName();
            String translated = station == null || station.translatedNames().isEmpty() ? "" : station.translatedNames().getFirst();
            List<PlatformLayoutProjectionEngine.TransferData> transfers = layoutTransfersFor(stop, line == null ? null : line.id());
            boolean terminal = i == 0 || i == stopIds.size() - 1 || layout.terminalStationGroupId().map(id -> id.equals(stop.stationGroupId())).orElse(false);
            RouteSectionStatus incoming = incomingStatus(layout, i);
            boolean missing = isCurrentDimensionStopMissing(stop);
            double worldX = station == null ? i * 32.0D : station.stationBlockPos().getX();
            double worldZ = station == null ? 0.0D : station.stationBlockPos().getZ();
            stops.add(new PlatformLayoutProjectionEngine.StopData(name, translated, stop.platformNumber(), stop.id().equals(currentStopId), terminal, !transfers.isEmpty(), transfers, incoming, missing, worldX, worldZ));
        }
        int current = 0;
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).current()) {
                current = i;
                break;
            }
        }
        boolean reverse = direction == PlatformProjectorConfig.PlatformProjectionDirection.REVERSE;
        PlatformLayoutProjectionEngine.RouteData route = line == null
                ? new PlatformLayoutProjectionEngine.RouteData(layout.displayName().orElse("Line"), List.of(0xFF3366FF))
                : new PlatformLayoutProjectionEngine.RouteData(line.displayName(), List.copyOf(line.themeColors()));
        return new PlatformLayoutProjectionEngine.Data(layoutName(layout, line), route, stops, current, layout.bidirectional(), layout.loop(), loopStatus(layout), reverse);
    }

    private static RouteSectionStatus incomingStatus(RouteLayout layout, int stopIndex) {
        if (stopIndex <= 0) {
            return RouteSectionStatus.VALID;
        }
        int sectionIndex = stopIndex - 1;
        if (sectionIndex >= layout.orderedSectionRefs().size()) {
            return RouteSectionStatus.STALE;
        }
        return ClientRouteDataCache.routeSection(layout.orderedSectionRefs().get(sectionIndex).routeSectionId())
                .map(section -> section.statusForLayout(layout))
                .orElse(RouteSectionStatus.STALE);
    }

    private static RouteSectionStatus loopStatus(RouteLayout layout) {
        if (!layout.loop() || layout.orderedPlatformStops().size() <= 1) {
            return RouteSectionStatus.VALID;
        }
        int loopSectionIndex = layout.orderedPlatformStops().size() - 1;
        if (loopSectionIndex >= layout.orderedSectionRefs().size()) {
            return RouteSectionStatus.STALE;
        }
        return ClientRouteDataCache.routeSection(layout.orderedSectionRefs().get(loopSectionIndex).routeSectionId())
                .map(section -> section.statusForLayout(layout))
                .orElse(RouteSectionStatus.STALE);
    }

    private static boolean isCurrentDimensionStopMissing(PlatformStop stop) {
        return ClientPipeNetworkCache.levelKey()
                .filter(levelKey -> levelKey.equals(stop.connectionRef().levelKey()))
                .map(ignored -> ClientPipeNetworkCache.currentConnection(stop.connectionId()).isEmpty()
                        && !ClientRouteDataCache.isWaitingForPipeRevision(ClientPipeNetworkCache.revision()))
                .orElse(false);
    }

    private static List<PlatformLayoutProjectionEngine.TransferData> layoutTransfersFor(PlatformStop platform, UUID currentLineId) {
        List<PlatformLayoutProjectionEngine.TransferData> transfers = new ArrayList<>();
        for (TransferOption chip : transfersFor(platform, currentLineId)) {
            transfers.add(new PlatformLayoutProjectionEngine.TransferData(chip.name(), chip.colors(), chip.platform(), chip.station(), chip.outStation()));
        }
        return List.copyOf(transfers);
    }

    private static List<StopChip> stopChips(RouteLayout layout, UUID currentStopId) {
        List<StopChip> stops = new ArrayList<>();
        for (UUID stopId : layout.orderedPlatformStops()) {
            PlatformStop stop = ClientRouteDataCache.platformStop(stopId).orElse(null);
            if (stop == null) {
                continue;
            }
            String stationName = ClientRouteDataCache.stationGroup(stop.stationGroupId()).map(StationGroup::primaryName).orElse("?");
            boolean transfer = ClientRouteDataCache.routeLayoutIdsForPlatformStop(stop.id()).size() > 1 || hasOutStationTransfers(stop);
            stops.add(new StopChip(stationName, stop.id().equals(currentStopId), transfer));
        }
        return List.copyOf(stops);
    }

    private static int currentIndex(List<StopChip> stops) {
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).current()) {
                return i;
            }
        }
        return -1;
    }

    private static String neighborName(List<StopChip> stops, int index, int delta, boolean loop) {
        if (stops.isEmpty() || index < 0) {
            return "";
        }
        int next = index + delta;
        if (loop) {
            next = Math.floorMod(next, stops.size());
        }
        if (next < 0 || next >= stops.size()) {
            return "";
        }
        return stops.get(next).name();
    }

    private static String terminalName(RouteLayout layout, PlatformProjectorConfig.PlatformProjectionDirection direction, List<StopChip> stops) {
        if (layout == null || stops.isEmpty()) {
            return "";
        }
        if (direction == PlatformProjectorConfig.PlatformProjectionDirection.REVERSE) {
            return stops.getFirst().name();
        }
        if (direction == PlatformProjectorConfig.PlatformProjectionDirection.BIDIRECTIONAL) {
            return stops.getFirst().name() + " / " + stops.getLast().name();
        }
        return layout.terminalStationGroupId()
                .flatMap(ClientRouteDataCache::stationGroup)
                .map(StationGroup::primaryName)
                .orElse(stops.getLast().name());
    }

    private static boolean terminalPlatform(RouteLayout layout, UUID stopId) {
        if (layout == null || layout.orderedPlatformStops().isEmpty()) {
            return false;
        }
        return layout.orderedPlatformStops().getFirst().equals(stopId) || layout.orderedPlatformStops().getLast().equals(stopId);
    }

    private static String layoutName(RouteLayout layout, RouteLine line) {
        if (layout == null) {
            return "";
        }
        return layout.displayName().filter(name -> !name.isBlank()).orElse(line == null ? "?" : line.displayName());
    }

    private static List<TransferOption> transfersFor(PlatformStop platform, UUID currentLineId) {
        Map<TransferKey, TransferAccumulator> routes = new LinkedHashMap<>();
        addTransferRoutesFromStation(routes, platform.stationGroupId(), currentLineId, "");
        for (var link : ClientRouteDataCache.stationTransferLinksForStation(platform.stationGroupId())) {
            link.other(platform.stationGroupId()).ifPresent(other -> addTransferRoutesFromStation(routes, other, currentLineId, ClientRouteDataCache.stationGroup(other).map(StationGroup::primaryName).orElse("")));
        }
        return routes.values().stream()
                .map(TransferAccumulator::toOption)
                .sorted(Comparator
                        .comparing(TransferOption::outStation)
                        .thenComparing(TransferOption::station)
                        .thenComparing(TransferOption::name)
                        .thenComparing(TransferOption::platform))
                .toList();
    }

    private static void addTransferRoutesFromStation(Map<TransferKey, TransferAccumulator> routes, UUID stationGroupId, UUID currentLineId, String detail) {
        for (PlatformStop stop : ClientRouteDataCache.platformStopsInStation(stationGroupId)) {
            for (UUID layoutId : ClientRouteDataCache.routeLayoutIdsForPlatformStop(stop.id())) {
                ClientRouteDataCache.routeLayout(layoutId)
                        .flatMap(layout -> ClientRouteDataCache.routeLine(layout.routeLineId()))
                        .filter(RouteLine::visibleOnHud)
                        .filter(line -> currentLineId == null || !line.id().equals(currentLineId))
                        .ifPresent(line -> {
                            TransferKey key = new TransferKey(line.id(), stationGroupId, !detail.isBlank());
                            routes.computeIfAbsent(key, ignored -> new TransferAccumulator(line.id(), line.displayName(), firstTranslatedName(line.translatedNames()), detail, !detail.isBlank(), List.copyOf(line.themeColors())))
                                    .addPlatform(stop.platformNumber());
                        });
            }
        }
    }

    private static String firstTranslatedName(List<String> translatedNames) {
        return translatedNames == null || translatedNames.isEmpty() ? "" : translatedNames.getFirst();
    }

    private static boolean hasOutStationTransfers(PlatformStop platform) {
        return !ClientRouteDataCache.stationTransferLinksForStation(platform.stationGroupId()).isEmpty();
    }

    private static ComponentRect componentRect(ProjectionComponent component, float width, float height) {
        float x = -width * 0.5F + component.x();
        float top = height * 0.5F - component.y();
        return new ComponentRect(x, top - component.height(), component.width(), component.height());
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

    private static float primitiveLayerZ(int layer) {
        return layer * PRIMITIVE_Z_STEP;
    }

    private static void addPlatformLayoutPrimitive(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, PlatformLayoutProjectionEngine.Primitive primitive, float z, SurfacePass pass) {
        float primitiveZ = z + primitiveLayerZ(primitive.layer());
        if (primitive instanceof PlatformLayoutProjectionEngine.Rect item) {
            ComponentRect rr = normalizedRect(rect, item.x(), item.y(), item.width(), item.height());
            addTransformedRect(pose, buffer, transform, rr.x(), rr.y(), rr.width(), rr.height(), primitiveZ, item.color(), pass);
            return;
        }
        if (primitive instanceof PlatformLayoutProjectionEngine.Band item) {
            ComponentRect rr = normalizedRect(rect, item.x(), item.y(), item.width(), item.height());
            addRouteColorBands(pose, buffer, transform, rr.x(), rr.y(), rr.width(), rr.height(), primitiveZ, item.colors(), item.horizontal(), pass);
            return;
        }
        if (primitive instanceof PlatformLayoutProjectionEngine.Capsule item) {
            addFilledCapsule(pose, buffer, transform, normalizedRect(rect, item.x(), item.y(), item.width(), item.height()), primitiveZ, item.color(), pass);
            return;
        }
        if (primitive instanceof PlatformLayoutProjectionEngine.Circle item) {
            float diameter = Math.max(0.001F, item.radius() * 2.0F * Math.min(rect.width(), rect.height()));
            float cx = rect.x() + item.x() * rect.width();
            float cy = rect.top() - item.y() * rect.height();
            addFilledCircle(pose, buffer, transform, new ComponentRect(cx - diameter * 0.5F, cy - diameter * 0.5F, diameter, diameter), primitiveZ, item.color(), pass);
            return;
        }
        if (primitive instanceof PlatformLayoutProjectionEngine.Ring item) {
            float diameter = Math.max(0.001F, item.radius() * 2.0F * Math.min(rect.width(), rect.height()));
            float thickness = Math.max(0.001F, item.thickness() * Math.min(rect.width(), rect.height()));
            float cx = rect.x() + item.x() * rect.width();
            float cy = rect.top() - item.y() * rect.height();
            addCircleRing(pose, buffer, transform, new ComponentRect(cx - diameter * 0.5F, cy - diameter * 0.5F, diameter, diameter), thickness, primitiveZ, item.color(), pass);
            return;
        }
        if (primitive instanceof PlatformLayoutProjectionEngine.Line item) {
            float x1 = rect.x() + item.x1() * rect.width();
            float y1 = rect.top() - item.y1() * rect.height();
            float x2 = rect.x() + item.x2() * rect.width();
            float y2 = rect.top() - item.y2() * rect.height();
            addLineSegment(pose, buffer, transform, x1, y1, x2, y2, Math.max(0.001F, item.width() * Math.min(rect.width(), rect.height())), primitiveZ, item.color(), pass);
        }
    }

    private static void addTransferPrimitive(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, PlatformTransferProjectionEngine.Primitive primitive, float z, SurfacePass pass) {
        float primitiveZ = z + primitiveLayerZ(primitive.layer());
        if (primitive instanceof PlatformTransferProjectionEngine.Rect item) {
            ComponentRect rr = normalizedRect(rect, item.x(), item.y(), item.width(), item.height());
            addTransformedRect(pose, buffer, transform, rr.x(), rr.y(), rr.width(), rr.height(), primitiveZ, item.color(), pass);
            return;
        }
        if (primitive instanceof PlatformTransferProjectionEngine.Capsule item) {
            addFilledCapsule(pose, buffer, transform, normalizedRect(rect, item.x(), item.y(), item.width(), item.height()), primitiveZ, item.color(), pass);
            return;
        }
        if (primitive instanceof PlatformTransferProjectionEngine.Icon item) {
            ComponentRect icon = normalizedSquare(rect, item.centerX(), item.centerY(), item.size());
            addIconShape(pose, buffer, transform, icon, item.shape(), item.outline(), primitiveZ, item.fillColor(), item.borderColor(), item.borderWidth(), item.ringThicknessRatio(), pass);
        }
    }

    private static void drawTransferPrimitiveTexts(PoseStack poseStack, SubmitNodeCollector collector, Font font, ComponentRect rect, PlatformTransferProjectionEngine.Layout layout) {
        for (PlatformTransferProjectionEngine.Primitive primitive : layout.primitives()) {
            if (primitive instanceof PlatformTransferProjectionEngine.Text text) {
                drawComponentLine(poseStack, collector, font, text.value(), normalizedRect(rect, text.x(), text.y(), text.width(), text.height()), textSettings(text.color(), text.fontSize(), text.align(), text.overflow()));
            }
        }
    }

    private static List<PlatformTransferProjectionEngine.TransferData> buildTransferData(List<TransferOption> transfers) {
        if (transfers == null || transfers.isEmpty()) {
            return List.of();
        }
        List<PlatformTransferProjectionEngine.TransferData> result = new ArrayList<>(transfers.size());
        for (TransferOption transfer : transfers) {
            result.add(new PlatformTransferProjectionEngine.TransferData(transfer.id(), transfer.name(), transfer.translatedName(), transfer.station(), transfer.platform(), transfer.outStation(), transfer.colors()));
        }
        return List.copyOf(result);
    }

    private static ComponentRect normalizedRect(ComponentRect rect, float x, float y, float width, float height) {
        float w = Math.max(0.001F, width * rect.width());
        float h = Math.max(0.001F, height * rect.height());
        return new ComponentRect(rect.x() + x * rect.width(), rect.top() - (y + height) * rect.height(), w, h);
    }

    private static ComponentRect normalizedSquare(ComponentRect rect, float centerX, float centerY, float size) {
        float diameter = Math.max(0.001F, size * Math.min(rect.width(), rect.height()));
        float cx = rect.x() + centerX * rect.width();
        float cy = rect.top() - centerY * rect.height();
        return new ComponentRect(cx - diameter * 0.5F, cy - diameter * 0.5F, diameter, diameter);
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
                addBorder(pose, buffer, transform, rect.x(), rect.y(), rect.width(), rect.height(), Math.max(border, ring), z + SURFACE_BORDER_Z, fillColor, pass);
            }
            return;
        }
        if (!outline) {
            addFilledCircle(pose, buffer, transform, rect, z, fillColor, pass);
            if (border > 0.0F && (borderColor >>> 24) > 0) {
                addCircleRing(pose, buffer, transform, rect, border, z + SURFACE_BORDER_Z, borderColor, pass);
            }
        } else {
            addCircleRing(pose, buffer, transform, rect, ring, z + SURFACE_OVERLAY_Z, fillColor, pass);
        }
    }

    private static void addNodeShape(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, ProjectionComponentSettings.PlatformNodeStyle style, float thickness, float z, int color, SurfacePass pass) {
        if (style == ProjectionComponentSettings.PlatformNodeStyle.NONE) {
            return;
        }
        if (style == ProjectionComponentSettings.PlatformNodeStyle.OUTLINE) {
            addCircleRing(pose, buffer, transform, rect, Math.max(0.001F, thickness), z, color, pass);
            return;
        }
        addFilledCircle(pose, buffer, transform, rect, z, color, pass);
    }

    private static void addFilledCapsule(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, float z, int color, SurfacePass pass) {
        float radius = Math.min(rect.width(), rect.height()) * 0.5F;
        if (Math.abs(rect.width() - rect.height()) < 0.0001F) {
            addFilledCircle(pose, buffer, transform, rect, z, color, pass);
            return;
        }
        if (rect.width() > rect.height()) {
            addTransformedRect(pose, buffer, transform, rect.x() + radius, rect.y(), Math.max(0.0F, rect.width() - radius * 2.0F), rect.height(), z, color, pass);
            float cy = rect.y() + rect.height() * 0.5F;
            addFilledEllipseSector(pose, buffer, transform, rect.x() + radius, cy, radius, radius, (float) (Math.PI * 0.5D), (float) (Math.PI * 1.5D), z, color, pass);
            addFilledEllipseSector(pose, buffer, transform, rect.x() + rect.width() - radius, cy, radius, radius, (float) (-Math.PI * 0.5D), (float) (Math.PI * 0.5D), z, color, pass);
            return;
        }
        addTransformedRect(pose, buffer, transform, rect.x(), rect.y() + radius, rect.width(), Math.max(0.0F, rect.height() - radius * 2.0F), z, color, pass);
        float cx = rect.x() + rect.width() * 0.5F;
        addFilledEllipseSector(pose, buffer, transform, cx, rect.y() + radius, radius, radius, (float) Math.PI, (float) (Math.PI * 2.0D), z, color, pass);
        addFilledEllipseSector(pose, buffer, transform, cx, rect.y() + rect.height() - radius, radius, radius, 0.0F, (float) Math.PI, z, color, pass);
    }

    private static ComponentRect inset(ComponentRect rect, float amount) {
        float safe = Math.max(0.0F, Math.min(amount, Math.min(rect.width(), rect.height()) * 0.48F));
        return new ComponentRect(rect.x() + safe, rect.y() + safe, Math.max(0.001F, rect.width() - safe * 2.0F), Math.max(0.001F, rect.height() - safe * 2.0F));
    }

    private static void addFilledCircle(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, float z, int color, SurfacePass pass) {
        if (!pass.accepts(color)) {
            return;
        }
        float cx = rect.x() + rect.width() * 0.5F;
        float cy = rect.y() + rect.height() * 0.5F;
        float rx = Math.max(0.0005F, rect.width() * 0.5F);
        float ry = Math.max(0.0005F, rect.height() * 0.5F);
        CircleLookup lookup = lookupFor(Math.max(rect.width(), rect.height()));
        for (int i = 0; i < lookup.count(); i++) {
            float a0x = lookup.cos(i);
            float a0y = lookup.sin(i);
            float a1x = lookup.cos(i + 1);
            float a1y = lookup.sin(i + 1);
            addTransformedQuad(pose, buffer, transform, cx, cy, cx + a0x * rx, cy + a0y * ry, cx + a1x * rx, cy + a1y * ry, cx, cy, z, color, pass);
        }
    }

    private static void addFilledEllipseSector(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, float cx, float cy, float rx, float ry, float startAngle, float endAngle, float z, int color, SurfacePass pass) {
        if (!pass.accepts(color)) {
            return;
        }
        float radius = Math.max(rx, ry);
        int segments = Math.max(8, lookupFor(radius * 2.0F).count() / 2);
        float span = endAngle - startAngle;
        for (int i = 0; i < segments; i++) {
            float a0 = startAngle + span * i / segments;
            float a1 = startAngle + span * (i + 1) / segments;
            addTransformedQuad(pose, buffer, transform,
                    cx, cy,
                    cx + (float) Math.cos(a0) * rx, cy + (float) Math.sin(a0) * ry,
                    cx + (float) Math.cos(a1) * rx, cy + (float) Math.sin(a1) * ry,
                    cx, cy,
                    z, color, pass);
        }
    }

    private static void addCircleRing(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, ComponentRect rect, float thickness, float z, int color, SurfacePass pass) {
        if (!pass.accepts(color)) {
            return;
        }
        float t = Math.max(0.001F, Math.min(thickness, Math.min(rect.width(), rect.height()) * 0.5F));
        float cx = rect.x() + rect.width() * 0.5F;
        float cy = rect.y() + rect.height() * 0.5F;
        float rx = Math.max(0.0005F, rect.width() * 0.5F);
        float ry = Math.max(0.0005F, rect.height() * 0.5F);
        float innerRx = Math.max(0.0001F, rx - t);
        float innerRy = Math.max(0.0001F, ry - t);
        CircleLookup lookup = lookupFor(Math.max(rect.width(), rect.height()));
        for (int i = 0; i < lookup.count(); i++) {
            float a0x = lookup.cos(i);
            float a0y = lookup.sin(i);
            float a1x = lookup.cos(i + 1);
            float a1y = lookup.sin(i + 1);
            addTransformedQuad(pose, buffer, transform,
                    cx + a0x * rx, cy + a0y * ry,
                    cx + a1x * rx, cy + a1y * ry,
                    cx + a1x * innerRx, cy + a1y * innerRy,
                    cx + a0x * innerRx, cy + a0y * innerRy,
                    z, color, pass);
        }
    }

    private static void addArrowSurface(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, float x, float y, int direction, float size, float z, int color, SurfacePass pass) {
        float s = Math.max(0.01F, size);
        if (direction >= 0) {
            addTransformedQuad(pose, buffer, transform, x + s, y, x - s, y + s * 0.7F, x - s * 0.45F, y, x - s, y - s * 0.7F, z, color, pass);
        } else {
            addTransformedQuad(pose, buffer, transform, x - s, y, x + s, y - s * 0.7F, x + s * 0.45F, y, x + s, y + s * 0.7F, z, color, pass);
        }
    }

    private static void addLineSegment(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, float x1, float y1, float x2, float y2, float width, float z, int color, SurfacePass pass) {
        if (!pass.accepts(color)) {
            return;
        }
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length <= 0.0001F) {
            float diameter = Math.max(0.001F, width);
            addFilledCircle(pose, buffer, transform, new ComponentRect(x1 - diameter * 0.5F, y1 - diameter * 0.5F, diameter, diameter), z, color, pass);
            return;
        }
        float radius = Math.max(0.0005F, width * 0.5F);
        float nx = -dy / length * radius;
        float ny = dx / length * radius;
        addTransformedQuad(pose, buffer, transform, x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny, x1 - nx, y1 - ny, z, color, pass);
        float angle = (float) Math.atan2(dy, dx);
        addFilledEllipseSector(pose, buffer, transform, x1, y1, radius, radius, angle + (float) (Math.PI * 0.5D), angle + (float) (Math.PI * 1.5D), z, color, pass);
        addFilledEllipseSector(pose, buffer, transform, x2, y2, radius, radius, angle - (float) (Math.PI * 0.5D), angle + (float) (Math.PI * 0.5D), z, color, pass);
    }

    private static void addRouteColorBands(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, float x, float y, float width, float height, float z, List<Integer> colors, boolean horizontal, SurfacePass pass) {
        List<Integer> normalized = colors == null || colors.isEmpty() ? List.of(0xFF3366FF) : colors.stream().limit(8).toList();
        if (horizontal) {
            float segment = width / normalized.size();
            for (int i = 0; i < normalized.size(); i++) {
                float xx = x + i * segment;
                addTransformedRect(pose, buffer, transform, xx, y, i == normalized.size() - 1 ? x + width - xx : segment, height, z, normalized.get(i), pass);
            }
        } else {
            float segment = height / normalized.size();
            for (int i = 0; i < normalized.size(); i++) {
                float yy = y + i * segment;
                addTransformedRect(pose, buffer, transform, x, yy, width, i == normalized.size() - 1 ? y + height - yy : segment, z, normalized.get(i), pass);
            }
        }
    }

    private static void addBorder(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, float x, float y, float width, float height, float thickness, float z, int color, SurfacePass pass) {
        addTransformedRect(pose, buffer, transform, x, y, width, thickness, z, color, pass);
        addTransformedRect(pose, buffer, transform, x, y + height - thickness, width, thickness, z, color, pass);
        addTransformedRect(pose, buffer, transform, x, y, thickness, height, z, color, pass);
        addTransformedRect(pose, buffer, transform, x + width - thickness, y, thickness, height, z, color, pass);
    }

    private static void addTransformedRect(PoseStack.Pose pose, VertexConsumer buffer, ComponentTransform transform, float x, float y, float width, float height, float z, int color, SurfacePass pass) {
        if (!pass.accepts(color)) {
            return;
        }
        if (isUnrotated(transform) && isInsideCanvas(transform.canvasBounds(), x, y, width, height)) {
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
        if (isUnrotated(transform) && isInsideCanvas(transform.canvasBounds(), rect.x(), rect.y(), rect.width(), rect.height())) {
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
        if (isUnrotated(transform) && isQuadInsideCanvas(transform.canvasBounds(), x1, y1, x2, y2, x3, y3, x4, y4)) {
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

    private static boolean isInsideCanvas(CanvasBounds bounds, float x, float y, float width, float height) {
        return x >= bounds.minX()
                && y >= bounds.minY()
                && x + width <= bounds.maxX()
                && y + height <= bounds.maxY();
    }

    private static boolean isQuadInsideCanvas(CanvasBounds bounds, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4) {
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

    private static String shortLabel(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return "?";
        }
        String first = trimmed.split("\\s+")[0];
        return first.length() <= 3 ? first : first.substring(0, 3);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static int contrast(int color) {
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        return r * 299 + g * 587 + b * 114 > 150000 ? 0xFF111820 : 0xFFFFFFFF;
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

    private record ProjectorData(BlockPos pos, Direction facing, PlatformProjectorConfig config, AppliedProjectionLayout layout, PlatformRenderInfo info) {
    }

    private record PlatformInfoCacheKey(
            long routeRevision,
            long pipeRevision,
            Optional<UUID> platformStopId,
            Optional<UUID> routeLayoutId,
            PlatformProjectorConfig.PlatformProjectionDirection direction
    ) {
        static PlatformInfoCacheKey of(PlatformProjectorConfig config) {
            return new PlatformInfoCacheKey(
                    ClientRouteDataCache.revision(),
                    ClientPipeNetworkCache.revision(),
                    config.platformStopId(),
                    config.routeLayoutId(),
                    config.direction()
            );
        }
    }

    private record PlatformRenderInfo(
            boolean missing,
            String stationName,
            String translation,
            String platformName,
            String platformNumber,
            Optional<RouteChip> line,
            String layoutName,
            String originStop,
            String previousStop,
            String nextStop,
            String terminalStop,
            List<TransferOption> transfers,
            List<StopChip> stops,
            PlatformLayoutProjectionEngine.Data layoutData,
            int currentIndex,
            boolean bidirectional,
            boolean loop,
            boolean anyBidirectional,
            boolean anyLoop,
            boolean terminal,
            boolean hasOutStationTransfers,
            List<PlatformTransferProjectionEngine.TransferData> transferData,
            Map<PlatformPrimitiveKey, PlatformLayoutProjectionEngine.Layout> platformLayouts,
            Map<TransferPrimitiveKey, PlatformTransferProjectionEngine.Layout> transferLayouts,
            Map<StatusTagPrimitiveKey, PlatformStatusTagProjectionEngine.Layout> statusTagLayouts
    ) {
        PlatformRenderInfo(
                boolean missing,
                String stationName,
                String translation,
                String platformName,
                String platformNumber,
                Optional<RouteChip> line,
                String layoutName,
                String originStop,
                String previousStop,
                String nextStop,
                String terminalStop,
                List<TransferOption> transfers,
                List<StopChip> stops,
                PlatformLayoutProjectionEngine.Data layoutData,
                int currentIndex,
                boolean bidirectional,
                boolean loop,
                boolean anyBidirectional,
                boolean anyLoop,
                boolean terminal,
                boolean hasOutStationTransfers
        ) {
            this(
                    missing,
                    stationName,
                    translation,
                    platformName,
                    platformNumber,
                    line,
                    layoutName,
                    originStop,
                    previousStop,
                    nextStop,
                    terminalStop,
                    transfers,
                    stops,
                    layoutData,
                    currentIndex,
                    bidirectional,
                    loop,
                    anyBidirectional,
                    anyLoop,
                    terminal,
                    hasOutStationTransfers,
                    PlatformProjectorRenderer.buildTransferData(transfers),
                    new LinkedHashMap<>(16, 0.75F, true),
                    new LinkedHashMap<>(16, 0.75F, true),
                    new LinkedHashMap<>(8, 0.75F, true)
            );
        }

        static PlatformRenderInfo unbound() {
            return new PlatformRenderInfo(true, "", "", "", "", Optional.empty(), "", "", "", "", "", List.of(), List.of(), PlatformLayoutProjectionEngine.Data.sample(), -1, false, false, false, false, false, false);
        }

        String platformNameOrNumber() {
            return this.platformName.isBlank() ? Component.translatable("screen.superpipeslide.platform_projector.platform_number", this.platformNumber).getString() : this.platformName;
        }

        PlatformLayoutProjectionEngine.Layout platformLayout(ProjectionComponentSettings.PlatformLayoutMap settings, long frameTimeMillis) {
            PlatformPrimitiveKey key = PlatformPrimitiveKey.of(settings, frameTimeMillis);
            PlatformLayoutProjectionEngine.Layout layout = this.platformLayouts.computeIfAbsent(key, ignored -> PlatformLayoutProjectionEngine.build(this.layoutData, settings, key.buildTimeMillis()));
            LayoutCache.trim(this.platformLayouts, PLATFORM_LAYOUT_CACHE_MAX_ENTRIES);
            return layout;
        }

        PlatformTransferProjectionEngine.Layout transferListLayout(ProjectionComponentSettings.PlatformTransferList settings, long frameTimeMillis, int seed) {
            TransferPrimitiveKey key = TransferPrimitiveKey.list(settings, frameTimeMillis, seed, transferCandidates(this.transferData, settings.includeOutStation()));
            PlatformTransferProjectionEngine.Layout layout = this.transferLayouts.computeIfAbsent(key, ignored -> PlatformTransferProjectionEngine.buildList(this.transferData, settings, key.buildTimeMillis(settings.rotateIntervalTicks()), seed));
            LayoutCache.trim(this.transferLayouts, TRANSFER_LAYOUT_CACHE_MAX_ENTRIES);
            return layout;
        }

        PlatformTransferProjectionEngine.Layout transferMatrixLayout(ProjectionComponentSettings.PlatformTransferMatrix settings, long frameTimeMillis, int seed) {
            TransferPrimitiveKey key = TransferPrimitiveKey.matrix(settings, frameTimeMillis, seed, transferCandidates(this.transferData, settings.includeOutStation()));
            PlatformTransferProjectionEngine.Layout layout = this.transferLayouts.computeIfAbsent(key, ignored -> PlatformTransferProjectionEngine.buildMatrix(this.transferData, settings, key.buildTimeMillis(settings.rotateIntervalTicks()), seed));
            LayoutCache.trim(this.transferLayouts, TRANSFER_LAYOUT_CACHE_MAX_ENTRIES);
            return layout;
        }

        PlatformStatusTagProjectionEngine.Layout statusTagLayout(ProjectionComponentSettings.PlatformStatusTags settings, float width, float height) {
            List<String> tags = statusTags(settings, this);
            StatusTagPrimitiveKey key = new StatusTagPrimitiveKey(settings, width, height, tags);
            PlatformStatusTagProjectionEngine.Layout layout = this.statusTagLayouts.computeIfAbsent(key, ignored -> PlatformStatusTagProjectionEngine.build(List.copyOf(tags), settings, width, height));
            LayoutCache.trim(this.statusTagLayouts, STATUS_TAG_LAYOUT_CACHE_MAX_ENTRIES);
            return layout;
        }

        PlatformRenderInfoSignature signature() {
            return new PlatformRenderInfoSignature(
                    this.missing,
                    this.stationName,
                    this.translation,
                    this.platformName,
                    this.platformNumber,
                    this.line,
                    this.layoutName,
                    this.originStop,
                    this.previousStop,
                    this.nextStop,
                    this.terminalStop,
                    this.transfers,
                    this.stops,
                    this.layoutData,
                    this.currentIndex,
                    this.bidirectional,
                    this.loop,
                    this.anyBidirectional,
                    this.anyLoop,
                    this.terminal,
                    this.hasOutStationTransfers,
                    this.transferData
            );
        }

    }

    private record PlatformRenderInfoSignature(
            boolean missing,
            String stationName,
            String translation,
            String platformName,
            String platformNumber,
            Optional<RouteChip> line,
            String layoutName,
            String originStop,
            String previousStop,
            String nextStop,
            String terminalStop,
            List<TransferOption> transfers,
            List<StopChip> stops,
            PlatformLayoutProjectionEngine.Data layoutData,
            int currentIndex,
            boolean bidirectional,
            boolean loop,
            boolean anyBidirectional,
            boolean anyLoop,
            boolean terminal,
            boolean hasOutStationTransfers,
            List<PlatformTransferProjectionEngine.TransferData> transferData
    ) {
    }

    private record PlatformPrimitiveKey(ProjectionComponentSettings.PlatformLayoutMap settings, long phase) {
        static PlatformPrimitiveKey of(ProjectionComponentSettings.PlatformLayoutMap settings, long frameTimeMillis) {
            if (settings != null && settings.style() == ProjectionComponentSettings.PlatformLayoutMapStyle.EDITOR) {
                return new PlatformPrimitiveKey(settings, frameTimeMillis / 50L);
            }
            return new PlatformPrimitiveKey(settings, 0L);
        }

        long buildTimeMillis() {
            return this.phase * 50L;
        }
    }

    private record TransferPrimitiveKey(Object settings, boolean matrix, long phase, int seed) {
        static TransferPrimitiveKey list(ProjectionComponentSettings.PlatformTransferList settings, long frameTimeMillis, int seed, int candidateCount) {
            return new TransferPrimitiveKey(settings, false, rotatePhase(settings.overflow(), settings.maxVisible(), candidateCount, settings.rotateIntervalTicks(), frameTimeMillis), seed);
        }

        static TransferPrimitiveKey matrix(ProjectionComponentSettings.PlatformTransferMatrix settings, long frameTimeMillis, int seed, int candidateCount) {
            return new TransferPrimitiveKey(settings, true, rotatePhase(settings.overflow(), settings.maxVisible(), candidateCount, settings.rotateIntervalTicks(), frameTimeMillis), seed);
        }

        long buildTimeMillis(int intervalTicks) {
            return this.phase * rotateIntervalMillis(intervalTicks);
        }

        private static long rotatePhase(ProjectionComponentSettings.RouteOverflowMode overflow, int maxVisible, int candidateCount, int intervalTicks, long frameTimeMillis) {
            if (overflow != ProjectionComponentSettings.RouteOverflowMode.ROTATE || candidateCount <= Math.max(1, maxVisible)) {
                return 0L;
            }
            return frameTimeMillis / rotateIntervalMillis(intervalTicks);
        }

        private static long rotateIntervalMillis(int intervalTicks) {
            return Math.max(10L, intervalTicks) * 50L;
        }
    }

    private record StatusTagPrimitiveKey(ProjectionComponentSettings.PlatformStatusTags settings, float width, float height, List<String> tags) {
    }

    private record RouteChip(UUID id, String name, String translatedName, String station, String platform, boolean outStation, List<Integer> colors) {
        int firstColor() {
            return this.colors == null || this.colors.isEmpty() ? 0xFF3366FF : this.colors.getFirst();
        }
    }

    private record TexturedComponent(Identifier textureId, ComponentTransform transform, ComponentRect rect, float z, int color, float u0, float v0, float u1, float v1) {
    }

    private record TransferOption(UUID id, String name, String translatedName, String station, String platform, boolean outStation, List<Integer> colors) {
        int firstColor() {
            return this.colors == null || this.colors.isEmpty() ? 0xFF3366FF : this.colors.getFirst();
        }
    }

    private record TransferKey(UUID lineId, UUID stationGroupId, boolean outStation) {
    }

    private record StopChip(String name, boolean current, boolean transfer) {
    }

    private record StaticProjectionKey(
            BlockPos pos,
            Direction facing,
            boolean frontSide,
            PlatformProjectorConfig config,
            float width,
            float height,
            AppliedProjectionLayout layout,
            PlatformRenderInfoSignature info,
            long routeRevision,
            long pipeRevision,
            List<NetworkImageStateKey> networkImages
    ) {
    }

    private record DynamicProjectionKey(
            BlockPos pos,
            Direction facing,
            boolean frontSide,
            PlatformProjectorConfig config,
            float width,
            float height,
            AppliedProjectionLayout layout,
            PlatformRenderInfoSignature info,
            long routeRevision,
            long pipeRevision,
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

    private record CircleLookup(float[] cos, float[] sin) {
        int count() {
            return Math.max(0, this.cos.length - 1);
        }

        float cos(int index) {
            return this.cos[index];
        }

        float sin(int index) {
            return this.sin[index];
        }
    }

    private static final class LayoutCache {
        private LayoutCache() {
        }

        static <K, V> void trim(Map<K, V> cache, int maxEntries) {
            if (cache.size() < maxEntries) {
                return;
            }
            Iterator<K> iterator = cache.keySet().iterator();
            while (cache.size() > maxEntries && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
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

    private static final class TransferAccumulator {
        private final UUID id;
        private final String name;
        private final String translatedName;
        private final String station;
        private final boolean outStation;
        private final List<Integer> colors;
        private final Set<String> platformLabels = new LinkedHashSet<>();

        TransferAccumulator(UUID id, String name, String translatedName, String station, boolean outStation, List<Integer> colors) {
            this.id = id;
            this.name = name == null ? "?" : name;
            this.translatedName = translatedName == null ? "" : translatedName;
            this.station = station == null ? "" : station;
            this.outStation = outStation;
            this.colors = colors == null ? List.of() : List.copyOf(colors);
        }

        TransferAccumulator addPlatform(String platform) {
            if (platform != null && !platform.isBlank()) {
                this.platformLabels.add(platform.trim());
            }
            return this;
        }

        TransferOption toOption() {
            return new TransferOption(this.id, this.name, this.translatedName, this.station, platformLabel(), this.outStation, this.colors);
        }

        private String platformLabel() {
            if (this.platformLabels.isEmpty()) {
                return "";
            }
            List<String> labels = this.platformLabels.stream()
                    .sorted(PlatformProjectorRenderer::comparePlatformLabels)
                    .toList();
            return String.join(" / ", labels);
        }
    }

    private static int comparePlatformLabels(String first, String second) {
        Integer firstNumber = parsePositiveInt(first);
        Integer secondNumber = parsePositiveInt(second);
        if (firstNumber != null && secondNumber != null) {
            return Integer.compare(firstNumber, secondNumber);
        }
        if (firstNumber != null) {
            return -1;
        }
        if (secondNumber != null) {
            return 1;
        }
        return first.compareToIgnoreCase(second);
    }

    private static Integer parsePositiveInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
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
