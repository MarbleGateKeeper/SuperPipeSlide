package dev.marblegate.superpipeslide.client.renderer.pipe;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.marblegate.superpipeslide.client.core.accessibility.ClientSafetyOptions;
import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeAppearanceCache;
import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.client.core.pipe.PipeCoatingRenderResolver;
import dev.marblegate.superpipeslide.client.renderer.ClientRenderCompatibility;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.appearance.coating.PipeCoatingSelection;
import dev.marblegate.superpipeslide.common.core.appearance.model.PipeAppearanceProfile;
import dev.marblegate.superpipeslide.common.core.appearance.storage.PipeAppearanceDefinitions;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeStyleDefinition;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeStyleGeometry;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeSurfaceModel;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeVariantDefinition;
import dev.marblegate.superpipeslide.common.core.geometry.CurveSpec;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionAttributes;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRaycast;
import dev.marblegate.superpipeslide.common.core.geometry.RuntimePipeConnection;
import dev.marblegate.superpipeslide.common.core.networkgraph.solver.PipeConnectionPlacementPlan;
import dev.marblegate.superpipeslide.common.core.networkgraph.solver.PipeConnectionPlacementPlanner;
import dev.marblegate.superpipeslide.common.item.pipe.PipeAppearanceToolItem;
import dev.marblegate.superpipeslide.common.item.pipe.PipeAttributeToolItem;
import dev.marblegate.superpipeslide.common.item.pipe.PipeConnectorItem;
import dev.marblegate.superpipeslide.common.item.pipe.PipeConnectorMode;
import dev.marblegate.superpipeslide.common.item.pipe.PipeRemoverItem;
import dev.marblegate.superpipeslide.common.item.route.PlatformClaimerItem;
import dev.marblegate.superpipeslide.common.registry.SPSBlocks;
import dev.marblegate.superpipeslide.common.registry.SPSDataComponents;
import dev.marblegate.superpipeslide.config.Config;
import dev.marblegate.superpipeslide.mixin.client.RenderSetupAccessor;
import dev.marblegate.superpipeslide.mixin.client.RenderTypeAccessor;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.IRenderableSection;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class ClientPipeRenderer {
    private static final ContextKey<RenderData> RENDER_DATA = new ContextKey<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipe_render_data"));
    private static final double BLOCKS_PER_CHUNK = 16.0D;
    private static final double PIPE_RADIUS = 0.18D;
    private static final int PIPE_OPERATION_TARGET_COLOR = 0xF0FFFFFF;
    private static final int PREVIEW_VALID_COLOR = 0xE060FF80;
    private static final int PREVIEW_INVALID_COLOR = 0xE0FF5050;
    private static final int PREVIEW_WARNING_COLOR = 0xE0FFD85A;
    private static final double PREVIEW_LENGTH_WARNING_MARGIN = 0.25D;
    private static final int FULL_BRIGHT_LIGHT = 0x00F000F0;
    private static final double PIPE_TEXTURE_TILE_U_BLOCKS = 1.0D;
    private static final double PIPE_TEXTURE_TILE_V_BLOCKS = 1.0D;
    private static final double SURFACE_UV_EPSILON = 1.0E-5D;
    private static final float SURFACE_TILE_UV_INSET = 0.0015F;
    private static final double LIGHT_SAMPLE_NORMAL_OFFSET = 0.08D;
    private static final double TERMINAL_INNER_INSET = 0.018D;
    private static final double TERMINAL_SLEEVE_START = 0.032D;
    private static final double TERMINAL_SLEEVE_LENGTH = 0.60D;
    private static final double MARKER_SURFACE_OFFSET = 0.026D;
    private static final double MARKER_LAYER_OFFSET = 0.006D;
    private static final Identifier MARKER_TEXTURE = Identifier.withDefaultNamespace("block/white_concrete");
    private static final int ACCELERATION_MARKER_COLOR = 0xF8FF9F2E;
    private static final int ACCELERATION_CORE_COLOR = 0xFFFFE37A;
    private static final int HIGHWAY_MARKER_COLOR = 0xE835C9FF;
    private static final int HIGHWAY_HIGHLIGHT_COLOR = 0xF8A8F4FF;
    private static final int HIGHWAY_EDGE_COLOR = 0xC0258EBA;
    private static final int DIRECTION_MARKER_COLOR = 0xF8FF4050;
    private static final int DIRECTION_CORE_COLOR = 0xF8FFFFFF;
    private static final int PLATFORM_MARKER_COLOR = 0xF8FFD34D;
    private static final int PLATFORM_EDGE_COLOR = 0xEEFFFFFF;
    private static final int PLATFORM_SHADOW_COLOR = 0xCC3A3524;
    private static final int PLATFORM_SAFETY_COLOR = 0xF8FFF4C0;
    private static final int MARKER_ANIMATION_NONE = 0;
    private static final int MARKER_ANIMATION_ACCELERATION = 1;
    private static final int MARKER_ANIMATION_HIGHWAY = 2;
    private static final int MARKER_ANIMATION_DIRECTION = 3;
    private static final int MAX_MESH_CACHE_ENTRIES = 8192;
    private static final double ALWAYS_RENDER_RADIUS = 10.0D;
    private static final double VISIBILITY_MARGIN = 8.0D;
    private static final double FRUSTUM_BOUNDS_INFLATE = 0.75D;
    private static final double VISIBLE_SECTION_INFLATE = 1.0D;
    private static final double LOD0_DISTANCE = 24.0D;
    private static final double LOD1_DISTANCE = 64.0D;
    private static final double LOD2_DISTANCE = 128.0D;
    private static final double LOD0_ENTER_DISTANCE = 20.0D;
    private static final double LOD0_EXIT_DISTANCE = 28.0D;
    private static final double LOD1_ENTER_DISTANCE = 56.0D;
    private static final double LOD1_EXIT_DISTANCE = 72.0D;
    private static final double LOD2_ENTER_DISTANCE = 112.0D;
    private static final double LOD2_EXIT_DISTANCE = 144.0D;
    private static final int MAX_LOD_STATE_ENTRIES = 4096;
    private static final int SECTION_LOD_COUNT = 4;
    private static final double SECTION_CACHE_RETAIN_BLOCKS = BLOCKS_PER_CHUNK * 4.0D;
    private static final int GPU_BATCH_INITIAL_BYTES = 262_144;
    private static final int GPU_LIGHT_RETRY_FRAMES = 8;
    private static final Identifier PIPE_ENTITY_SHADER = Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "core/pipe_entity");
    private static final RenderPipeline PIPE_ENTITY_CUTOUT_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipeline/pipe_entity_cutout"))
            .withVertexShader(PIPE_ENTITY_SHADER)
            .withFragmentShader(PIPE_ENTITY_SHADER)
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withSampler("Sampler1")
            .withCull(false)
            .build();
    private static final RenderPipeline PIPE_ENTITY_CUTOUT_CULL_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipeline/pipe_entity_cutout_cull"))
            .withVertexShader(PIPE_ENTITY_SHADER)
            .withFragmentShader(PIPE_ENTITY_SHADER)
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withSampler("Sampler1")
            .build();
    private static final RenderPipeline PIPE_ENTITY_CUTOUT_EMISSIVE_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipeline/pipe_entity_cutout_emissive"))
            .withVertexShader(PIPE_ENTITY_SHADER)
            .withFragmentShader(PIPE_ENTITY_SHADER)
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("EMISSIVE")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withSampler("Sampler1")
            .withCull(false)
            .build();
    private static final RenderPipeline PIPE_ENTITY_CUTOUT_CULL_EMISSIVE_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipeline/pipe_entity_cutout_cull_emissive"))
            .withVertexShader(PIPE_ENTITY_SHADER)
            .withFragmentShader(PIPE_ENTITY_SHADER)
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("EMISSIVE")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withSampler("Sampler1")
            .build();
    private static final RenderPipeline PIPE_ENTITY_TRANSLUCENT_EMISSIVE_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipeline/pipe_entity_translucent_emissive"))
            .withVertexShader(PIPE_ENTITY_SHADER)
            .withFragmentShader(PIPE_ENTITY_SHADER)
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("EMISSIVE")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();
    private static final RenderPipeline PIPE_ENTITY_TRANSLUCENT_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipeline/pipe_entity_translucent"))
            .withVertexShader(PIPE_ENTITY_SHADER)
            .withFragmentShader(PIPE_ENTITY_SHADER)
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .build();
    private static final RenderType PIPE_ATLAS_CUTOUT = pipeCutout(TextureAtlas.LOCATION_BLOCKS);
    private static final RenderType PIPE_ATLAS_CUTOUT_CULL = pipeCutoutCull(TextureAtlas.LOCATION_BLOCKS);
    private static final RenderType PIPE_ATLAS_CUTOUT_EMISSIVE = pipeCutoutEmissive(TextureAtlas.LOCATION_BLOCKS);
    private static final RenderType PIPE_ATLAS_CUTOUT_CULL_EMISSIVE = pipeCutoutCullEmissive(TextureAtlas.LOCATION_BLOCKS);
    private static final RenderType PIPE_ATLAS_TRANSLUCENT = pipeTranslucent(TextureAtlas.LOCATION_BLOCKS);
    private static final RenderType PIPE_ATLAS_TRANSLUCENT_EMISSIVE = pipeTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS);
    private static final Map<Identifier, RenderType> PIPE_GENERATED_CUTOUT = new LinkedHashMap<>();
    private static final Map<Identifier, RenderType> PIPE_GENERATED_CUTOUT_CULL = new LinkedHashMap<>();
    private static final Map<Identifier, RenderType> PIPE_GENERATED_CUTOUT_EMISSIVE = new LinkedHashMap<>();
    private static final Map<Identifier, RenderType> PIPE_GENERATED_CUTOUT_CULL_EMISSIVE = new LinkedHashMap<>();
    private static final Map<Identifier, RenderType> PIPE_GENERATED_TRANSLUCENT = new LinkedHashMap<>();
    private static final Map<Identifier, RenderType> PIPE_GENERATED_TRANSLUCENT_EMISSIVE = new LinkedHashMap<>();
    private static final PipeRenderExtension.Scope NOOP_SCOPE = () -> {};
    private static volatile PipeRenderExtension renderExtension = PipeRenderExtension.NONE;
    private static final Map<MeshCacheKey, List<PipeRenderMesh>> MESH_CACHE = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<MeshCacheKey, List<PipeRenderMesh>> eldest) {
            return this.size() > MAX_MESH_CACHE_ENTRIES;
        }
    };
    private static final Map<RenderSectionKey, Integer> SECTION_LOD_STATE = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<RenderSectionKey, Integer> eldest) {
            return this.size() > MAX_LOD_STATE_ENTRIES;
        }
    };
    private static final Map<RenderSectionKey, PipeSectionState> SECTION_CACHE = new LinkedHashMap<>();
    private static final Map<UUID, PipeSectionConnectionEntry> SECTION_CONNECTION_INDEX = new LinkedHashMap<>();
    private static long cachedNetworkRevision = Long.MIN_VALUE;
    private static long cachedAppearanceRevision = Long.MIN_VALUE;
    private static int cachedRenderDistance = Integer.MIN_VALUE;
    @Nullable
    private static ResourceKey<Level> cachedLevelKey;
    @Nullable
    private static RenderSectionKey cachedCameraSection;
    private static int cachedSectionRenderDistance = Integer.MIN_VALUE;
    @Nullable
    private static ResourceKey<Level> cachedSectionLevelKey;
    private static boolean sectionCacheRefreshNeeded = true;
    @Nullable
    private static ResourceKey<Level> cachedLightLevelKey;
    private static int cachedSkyDarken = Integer.MIN_VALUE;
    private static PipeGpuRenderProfile cachedGpuRenderProfile = PipeGpuRenderProfile.current();
    @Nullable
    private static RenderData latestRenderData;
    private static boolean loggedExternalGpuDraw;
    private static boolean loggedExternalShadowDraw;

    private ClientPipeRenderer() {}

    public static void registerRenderExtension(PipeRenderExtension extension) {
        renderExtension = Objects.requireNonNull(extension, "extension");
        refreshGpuRenderProfile();
    }

    public static PipeRenderExtension activeRenderExtension() {
        return renderExtension;
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(PIPE_ENTITY_CUTOUT_PIPELINE);
        event.registerPipeline(PIPE_ENTITY_CUTOUT_CULL_PIPELINE);
        event.registerPipeline(PIPE_ENTITY_CUTOUT_EMISSIVE_PIPELINE);
        event.registerPipeline(PIPE_ENTITY_CUTOUT_CULL_EMISSIVE_PIPELINE);
        event.registerPipeline(PIPE_ENTITY_TRANSLUCENT_EMISSIVE_PIPELINE);
        event.registerPipeline(PIPE_ENTITY_TRANSLUCENT_PIPELINE);
        renderExtension.registerPipelines(event);
    }

    public static RenderPipeline pipeEntityCutoutPipeline() {
        return PIPE_ENTITY_CUTOUT_PIPELINE;
    }

    public static RenderPipeline pipeEntityCutoutCullPipeline() {
        return PIPE_ENTITY_CUTOUT_CULL_PIPELINE;
    }

    public static RenderPipeline pipeEntityCutoutEmissivePipeline() {
        return PIPE_ENTITY_CUTOUT_EMISSIVE_PIPELINE;
    }

    public static RenderPipeline pipeEntityCutoutCullEmissivePipeline() {
        return PIPE_ENTITY_CUTOUT_CULL_EMISSIVE_PIPELINE;
    }

    public static RenderPipeline pipeEntityTranslucentEmissivePipeline() {
        return PIPE_ENTITY_TRANSLUCENT_EMISSIVE_PIPELINE;
    }

    public static RenderPipeline pipeEntityTranslucentPipeline() {
        return PIPE_ENTITY_TRANSLUCENT_PIPELINE;
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        refreshGpuRenderProfile();
        prepareRenderCache(event.getLevel());
        Vec3 camera = event.getCamera().position();
        double renderRadius = pipeRenderRadius();
        Frustum frustum = event.getFrustum();
        prepareSectionCache(event.getLevel(), camera, renderRadius);
        refreshSkyLightEpoch(event.getLevel());
        PipeRenderFrame frame = new PipeRenderFrame();
        List<VisiblePipeSection> gpuSections = new ArrayList<>();
        List<LineSegment> lines = new ArrayList<>();

        for (PipeSectionState section : SECTION_CACHE.values()) {
            if (!isPotentiallyVisible(section.bounds(), camera, renderRadius, frustum)) {
                continue;
            }
            int lod = lodFor(section.sectionKey(), section.bounds(), camera);
            PipeSectionLodState lodState = section.ensureLod(lod);
            if (!lodState.isEmpty()) {
                frame.add(section.sectionKey(), lodState.dynamicBatches());
                gpuSections.add(new VisiblePipeSection(section, lod));
            }
        }

        PipeConnection pipeOperationTarget = buildPipeOperationTarget(event.getLevel());
        if (pipeOperationTarget != null) {
            addPreviewLines(lines, pipeOperationTarget, PIPE_OPERATION_TARGET_COLOR);
        }

        Preview preview = buildPreview(event.getLevel());
        if (preview != null) {
            int color = switch (preview.validity()) {
                case VALID -> PREVIEW_VALID_COLOR;
                case WARNING -> PREVIEW_WARNING_COLOR;
                case INVALID -> PREVIEW_INVALID_COLOR;
            };
            if (preview.connection() != null) {
                addPreviewLines(lines, preview.connection(), color);
            }
            addControlPathLines(lines, preview.controlPath(), color);
        }

        RenderData renderData = new RenderData(frame, List.copyOf(lines), List.copyOf(gpuSections), camera);
        event.getRenderState().setRenderData(RENDER_DATA, renderData);
        latestRenderData = renderData;
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        RenderData renderData = event.getLevelRenderState().getRenderData(RENDER_DATA);
        if (renderData == null || renderData.isEmpty()) {
            return;
        }

        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        FrameLightSampler lightSampler = new FrameLightSampler(Minecraft.getInstance().level);
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        PipeRenderBatches frame = renderData.frame().visibleBatches(visibleSectionKeys(event));
        double animationTime = markerAnimationTime();
        if (!frame.atlasBatches().isEmpty()) {
            ClientRenderCompatibility.submitCustomGeometry(event.getSubmitNodeCollector(), poseStack, PIPE_ATLAS_CUTOUT, (pose, buffer) -> renderQuadBatches(pose, buffer, frame.atlasBatches(), animationTime, lightSampler));
        }
        if (!frame.emissiveAtlasBatches().isEmpty()) {
            ClientRenderCompatibility.submitCustomGeometry(event.getSubmitNodeCollector(), poseStack, PIPE_ATLAS_CUTOUT_EMISSIVE, (pose, buffer) -> renderQuadBatches(pose, buffer, frame.emissiveAtlasBatches(), animationTime, lightSampler));
        }
        if (!frame.culledAtlasBatches().isEmpty()) {
            ClientRenderCompatibility.submitCustomGeometry(event.getSubmitNodeCollector(), poseStack, PIPE_ATLAS_CUTOUT_CULL, (pose, buffer) -> renderQuadBatches(pose, buffer, frame.culledAtlasBatches(), animationTime, lightSampler));
        }
        if (!frame.emissiveCulledAtlasBatches().isEmpty()) {
            ClientRenderCompatibility.submitCustomGeometry(event.getSubmitNodeCollector(), poseStack, PIPE_ATLAS_CUTOUT_CULL_EMISSIVE, (pose, buffer) -> renderQuadBatches(pose, buffer, frame.emissiveCulledAtlasBatches(), animationTime, lightSampler));
        }
        for (Map.Entry<Identifier, List<List<TexturedQuad>>> entry : frame.generatedBatches().entrySet()) {
            ClientRenderCompatibility.submitCustomGeometry(event.getSubmitNodeCollector(), poseStack, generatedPipeCutout(entry.getKey()), (pose, buffer) -> renderQuadBatches(pose, buffer, entry.getValue(), animationTime, lightSampler));
        }
        for (Map.Entry<Identifier, List<List<TexturedQuad>>> entry : frame.emissiveGeneratedBatches().entrySet()) {
            ClientRenderCompatibility.submitCustomGeometry(event.getSubmitNodeCollector(), poseStack, generatedPipeCutoutEmissive(entry.getKey()), (pose, buffer) -> renderQuadBatches(pose, buffer, entry.getValue(), animationTime, lightSampler));
        }
        for (Map.Entry<Identifier, List<List<TexturedQuad>>> entry : frame.culledGeneratedBatches().entrySet()) {
            ClientRenderCompatibility.submitCustomGeometry(event.getSubmitNodeCollector(), poseStack, generatedPipeCutoutCull(entry.getKey()), (pose, buffer) -> renderQuadBatches(pose, buffer, entry.getValue(), animationTime, lightSampler));
        }
        for (Map.Entry<Identifier, List<List<TexturedQuad>>> entry : frame.emissiveCulledGeneratedBatches().entrySet()) {
            ClientRenderCompatibility.submitCustomGeometry(event.getSubmitNodeCollector(), poseStack, generatedPipeCutoutCullEmissive(entry.getKey()), (pose, buffer) -> renderQuadBatches(pose, buffer, entry.getValue(), animationTime, lightSampler));
        }
        if (!frame.translucentAtlasBatches().isEmpty()) {
            ClientRenderCompatibility.submitCustomGeometry(event.getSubmitNodeCollector(), poseStack, PIPE_ATLAS_TRANSLUCENT, (pose, buffer) -> renderQuadBatches(pose, buffer, frame.translucentAtlasBatches(), animationTime, lightSampler));
        }
        if (!frame.emissiveTranslucentAtlasBatches().isEmpty()) {
            ClientRenderCompatibility.submitCustomGeometry(event.getSubmitNodeCollector(), poseStack, PIPE_ATLAS_TRANSLUCENT_EMISSIVE, (pose, buffer) -> renderQuadBatches(pose, buffer, frame.emissiveTranslucentAtlasBatches(), animationTime, lightSampler));
        }
        for (Map.Entry<Identifier, List<List<TexturedQuad>>> entry : frame.translucentGeneratedBatches().entrySet()) {
            ClientRenderCompatibility.submitCustomGeometry(event.getSubmitNodeCollector(), poseStack, generatedPipeTranslucent(entry.getKey()), (pose, buffer) -> renderQuadBatches(pose, buffer, entry.getValue(), animationTime, lightSampler));
        }
        for (Map.Entry<Identifier, List<List<TexturedQuad>>> entry : frame.emissiveTranslucentGeneratedBatches().entrySet()) {
            ClientRenderCompatibility.submitCustomGeometry(event.getSubmitNodeCollector(), poseStack, generatedPipeTranslucentEmissive(entry.getKey()), (pose, buffer) -> renderQuadBatches(pose, buffer, entry.getValue(), animationTime, lightSampler));
        }
        if (!renderData.lines().isEmpty()) {
            ClientRenderCompatibility.submitCustomGeometry(event.getSubmitNodeCollector(), poseStack, RenderTypes.lines(), (pose, buffer) -> renderLines(pose, buffer, renderData.lines()));
        }
        poseStack.popPose();
    }

    public static void renderAfterOpaqueBlocks(RenderLevelStageEvent.AfterOpaqueBlocks event) {
        renderGpuSections(event, false, true);
    }

    public static void renderAfterTranslucentFeatures(RenderLevelStageEvent.AfterTranslucentFeatures event) {
        renderGpuSections(event, true, false);
    }

    public static void drawExternalShadowPass(PipeRenderExtension extension, Camera camera) {
        if (!extension.isRenderingShadowPass()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        refreshGpuRenderProfile();
        prepareRenderCache(level);
        Vec3 cameraPos = camera.position();
        Vec3 shadowCameraPos = extension.shadowCameraPosition(cameraPos);
        double renderRadius = extension.shadowRenderRadiusBlocks(pipeRenderRadius());
        prepareSectionCache(level, shadowCameraPos, renderRadius);
        refreshSkyLightEpoch(level);

        Frustum shadowFrustum = extension.shadowFrustum();
        FrameLightSampler lightSampler = new FrameLightSampler(level);
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        try (PipeRenderExtension.Scope shadowViewScope = extension.shadowModelView()) {
            for (PipeSectionState section : SECTION_CACHE.values()) {
                if (!isPotentiallyVisible(section.bounds(), shadowCameraPos, renderRadius, shadowFrustum)) {
                    continue;
                }
                int lod = lodFor(section.sectionKey(), section.bounds(), shadowCameraPos);
                PipeSectionLodState lodState = section.ensureLod(lod);
                if (lodState.isEmpty()) {
                    continue;
                }
                lodState.ensureGpuUploaded(lightSampler);
                PipeGpuBatches.DrawStats stats = lodState.gpuBatches().drawShadow(shadowCameraPos);
                if (stats.drew() && !loggedExternalShadowDraw) {
                    loggedExternalShadowDraw = true;
                    SuperPipeSlide.LOGGER.info("Drew SuperPipeSlide external-pipeline shadow pipe batches: batches={}, indices={}", stats.batches(), stats.indices());
                }
            }
        } finally {
            modelViewStack.popMatrix();
        }
    }

    private static void renderGpuSections(RenderLevelStageEvent event, boolean translucent, boolean setupLevelLighting) {
        refreshGpuRenderProfile();
        RenderData renderData = event.getLevelRenderState().getRenderData(RENDER_DATA);
        if (renderData == null || renderData.gpuSections().isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        try {
            if (setupLevelLighting) {
                minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.LEVEL);
            }
            FrameLightSampler lightSampler = new FrameLightSampler(level);
            Set<RenderSectionKey> renderableSections = visibleSectionKeys(event.getRenderableSections());
            for (VisiblePipeSection visible : renderData.gpuSections()) {
                if (!renderableSections.isEmpty() && !renderableSections.contains(visible.section().sectionKey())) {
                    continue;
                }
                PipeSectionLodState lodState = visible.section().lod(visible.lod());
                lodState.ensureGpuUploaded(lightSampler);
                PipeGpuBatches.DrawStats stats = lodState.gpuBatches().draw(translucent, renderData.camera());
                if (renderExtension.isExternalPipelineActive() && stats.drew() && !loggedExternalGpuDraw) {
                    loggedExternalGpuDraw = true;
                    SuperPipeSlide.LOGGER.info("Drew SuperPipeSlide external-pipeline pipe batches: translucent={}, batches={}, indices={}, gpuSections={}", translucent, stats.batches(), stats.indices(), renderData.gpuSections().size());
                }
            }
        } finally {
            modelViewStack.popMatrix();
        }
    }

    private static void refreshSkyLightEpoch(ClientLevel level) {
        int skyDarken = level.getSkyDarken();
        ResourceKey<Level> levelKey = level.dimension();
        if (cachedLightLevelKey != null && cachedLightLevelKey.equals(levelKey) && cachedSkyDarken == skyDarken) {
            return;
        }
        cachedLightLevelKey = levelKey;
        cachedSkyDarken = skyDarken;
        for (PipeSectionState section : SECTION_CACHE.values()) {
            section.markLightDirty();
        }
    }

    private static void refreshGpuRenderProfile() {
        PipeGpuRenderProfile current = PipeGpuRenderProfile.current();
        if (current.equals(cachedGpuRenderProfile)) {
            return;
        }
        cachedGpuRenderProfile = current;
        renderExtension.refreshPipelineMappings();
        for (PipeSectionState section : SECTION_CACHE.values()) {
            section.markGpuDirty();
        }
    }

    public static void clearRenderCache() {
        releaseSectionCache();
        MESH_CACHE.clear();
        SECTION_LOD_STATE.clear();
        cachedNetworkRevision = Long.MIN_VALUE;
        cachedAppearanceRevision = Long.MIN_VALUE;
        cachedRenderDistance = Integer.MIN_VALUE;
        cachedLevelKey = null;
        cachedCameraSection = null;
        cachedSectionRenderDistance = Integer.MIN_VALUE;
        cachedSectionLevelKey = null;
        sectionCacheRefreshNeeded = true;
        cachedLightLevelKey = null;
        cachedSkyDarken = Integer.MIN_VALUE;
        cachedGpuRenderProfile = PipeGpuRenderProfile.current();
        latestRenderData = null;
    }

    public static void markSectionLightDirty(int sectionX, int sectionY, int sectionZ) {
        PipeSectionState section = SECTION_CACHE.get(new RenderSectionKey(sectionX, sectionY, sectionZ));
        if (section != null) {
            section.markLightDirty();
        }
    }

    private static Set<RenderSectionKey> visibleSectionKeys(SubmitCustomGeometryEvent event) {
        return visibleSectionKeys(event.getRenderableSections());
    }

    private static Set<RenderSectionKey> visibleSectionKeys(Iterable<? extends IRenderableSection> renderableSections) {
        Set<RenderSectionKey> keys = new HashSet<>();
        for (IRenderableSection section : renderableSections) {
            addSectionKeys(section.getBoundingBox().inflate(VISIBLE_SECTION_INFLATE), keys);
        }
        return keys;
    }

    private static void renderQuadBatches(PoseStack.Pose pose, VertexConsumer buffer, List<List<TexturedQuad>> batches, double animationTime, FrameLightSampler lightSampler) {
        for (List<TexturedQuad> quads : batches) {
            renderQuads(pose, buffer, quads, animationTime, lightSampler);
        }
    }

    private static void renderQuads(PoseStack.Pose pose, VertexConsumer buffer, List<TexturedQuad> quads, double animationTime, FrameLightSampler lightSampler) {
        boolean photic = ClientSafetyOptions.reducePhotosensitivityRisk();
        for (TexturedQuad quad : quads) {
            int color = animatedMarkerColor(quad.color(), quad.animationKind(), quad.animationPhase(), animationTime, photic);
            boolean fullBright = (quad.fullBright() || quad.emissive()) && !photic;
            addQuadVertex(pose, buffer, quad.a(), quad.u0(), quad.v0(), color, lightSampler.lightAt(quad.lightA(), fullBright), quad.normal());
            addQuadVertex(pose, buffer, quad.b(), quad.u1(), quad.v0(), color, lightSampler.lightAt(quad.lightB(), fullBright), quad.normal());
            addQuadVertex(pose, buffer, quad.c(), quad.u1(), quad.v1(), color, lightSampler.lightAt(quad.lightC(), fullBright), quad.normal());
            addQuadVertex(pose, buffer, quad.d(), quad.u0(), quad.v1(), color, lightSampler.lightAt(quad.lightD(), fullBright), quad.normal());
        }
    }

    private static void addQuadVertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 point, float u, float v, int color, int light, Vec3 normal) {
        buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static double markerAnimationTime() {
        return System.nanoTime() / 1_000_000_000.0D;
    }

    private static int animatedMarkerColor(int color, int animationKind, double phase, double time, boolean photic) {
        if (photic) {
            return color;
        }
        return switch (animationKind) {
            case MARKER_ANIMATION_ACCELERATION -> multiplyColor(color, 0.72D + 0.48D * impulseWave(time * 1.35D - phase));
            case MARKER_ANIMATION_HIGHWAY -> multiplyColor(color, 0.78D + 0.34D * softPulse(time * 0.66D - phase));
            case MARKER_ANIMATION_DIRECTION -> multiplyColor(color, 0.82D + 0.26D * directionPulse(time * 0.48D - phase));
            default -> color;
        };
    }

    private static double impulseWave(double value) {
        double phase = value - Math.floor(value);
        return Math.pow(Math.max(0.0D, 1.0D - phase), 2.7D);
    }

    private static double softPulse(double value) {
        double phase = value - Math.floor(value);
        return 0.5D + 0.5D * Math.cos((phase - 0.5D) * Math.PI * 2.0D);
    }

    private static double directionPulse(double value) {
        double phase = value - Math.floor(value);
        if (phase < 0.18D) {
            return 1.0D - phase / 0.18D * 0.18D;
        }
        return Math.max(0.0D, 1.0D - (phase - 0.18D) / 0.82D) * 0.24D;
    }

    private static int multiplyColor(int color, double factor) {
        int a = color >>> 24 & 0xFF;
        int r = clampColor((color >>> 16 & 0xFF) * factor);
        int g = clampColor((color >>> 8 & 0xFF) * factor);
        int b = clampColor((color & 0xFF) * factor);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int clampColor(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    private static void renderLines(PoseStack.Pose pose, VertexConsumer buffer, List<LineSegment> lines) {
        for (LineSegment line : lines) {
            Vec3 normal = line.to().subtract(line.from()).normalize();
            if (normal.lengthSqr() < 1.0E-6D) {
                normal = new Vec3(0.0D, 1.0D, 0.0D);
            }
            buffer.addVertex(pose, (float) line.from().x, (float) line.from().y, (float) line.from().z)
                    .setColor(line.color())
                    .setNormal((float) normal.x, (float) normal.y, (float) normal.z)
                    .setLineWidth(line.width());
            buffer.addVertex(pose, (float) line.to().x, (float) line.to().y, (float) line.to().z)
                    .setColor(line.color())
                    .setNormal((float) normal.x, (float) normal.y, (float) normal.z)
                    .setLineWidth(line.width());
        }
    }

    private static List<PipeRenderMesh> cachedAppearanceMeshes(RuntimePipeConnection runtime, PipeAppearanceProfile profile, int lod) {
        PipeAppearanceProfile normalizedProfile = profile.normalizedToDefinitions();
        MeshCacheKey key = new MeshCacheKey(runtime.connection().id(), runtime.connection().connectionKey(), runtime.connection().hashCode(), normalizedProfile, lod);
        return MESH_CACHE.computeIfAbsent(key, ignored -> buildAppearanceMeshes(runtime, normalizedProfile, lod));
    }

    private static List<PipeRenderMesh> buildAppearanceMeshes(RuntimePipeConnection runtime, PipeAppearanceProfile normalizedProfile, int lod) {
        PipeStyleDefinition style = PipeAppearanceDefinitions.style(normalizedProfile.styleId()).orElse(PipeAppearanceDefinitions.defaultStyle());
        PipeVariantDefinition variant = PipeAppearanceDefinitions.variant(normalizedProfile.variantId()).orElse(PipeAppearanceDefinitions.defaultVariant());
        PipeStyleGeometry geometry = PipeStyleGeometry.resolve(style, variant, normalizedProfile.styleParameters());
        PipeSurfaceModel surfaceModel = PipeSurfaceModel.build(style.shape(), variant, geometry, lod);
        boolean glow = normalizedProfile.glow() && !ClientSafetyOptions.reducePhotosensitivityRisk();
        Map<String, PipeCoatingRenderResolver.ResolvedPipeCoating> coatings = new LinkedHashMap<>();
        for (String slotId : surfaceModel.slotIds()) {
            PipeCoatingSelection selection = PipeAppearanceDefinitions.selectionFor(normalizedProfile, slotId);
            coatings.put(slotId, PipeCoatingRenderResolver.resolve(selection));
        }
        PipeConnectionAttributes attributes = runtime.connection().resolvedAttributes();
        boolean platform = runtime.connection().platformStopId().isPresent();
        TextureAtlasSprite markerSprite = markerSprite();
        int samples = runtime.sampleCount();
        if (samples < 2) {
            return List.of();
        }

        Map<RenderSectionKey, MeshAccumulator> meshSections = new LinkedHashMap<>();
        Section previousSection = null;
        Section firstSection = null;
        Section lastSection = null;
        int step = switch (lod) {
            case 0 -> 1;
            case 1 -> 2;
            case 2 -> 4;
            default -> 8;
        };
        double accumulatedDistance = 0.0D;
        Vec3 previousCenter = null;
        Vec3 previousRight = null;
        for (int i = 0; i < samples; i += step) {
            if (samples - 1 - i < step) {
                i = samples - 1;
            }
            Vec3 center = runtime.sample(i);
            Vec3 tangent = cachedTangent(runtime, i);
            if (previousCenter != null) {
                accumulatedDistance += center.distanceTo(previousCenter);
            }
            Section section = appearanceSection(surfaceModel, center, tangent, geometry.slideContactY(), accumulatedDistance, previousRight);
            if (previousSection != null) {
                addSegmentGeometry(meshSections, previousSection, section, surfaceModel, coatings, attributes, platform, runtime.connection().length(), markerSprite, glow, lod);
            } else {
                firstSection = section;
            }
            previousSection = section;
            lastSection = section;
            previousCenter = center;
            previousRight = section.right();
        }
        if (firstSection != null && lastSection != null) {
            addTerminalGeometry(meshSections, firstSection, firstSection.tangent(), coatings, glow, lod);
            addTerminalGeometry(meshSections, lastSection, lastSection.tangent().scale(-1.0D), coatings, glow, lod);
        }

        if (meshSections.isEmpty()) {
            return List.of();
        }
        List<PipeRenderMesh> meshes = new ArrayList<>();
        for (MeshAccumulator accumulator : meshSections.values()) {
            PipeRenderMesh mesh = PipeRenderMesh.from(accumulator.sectionKey(), accumulator.bounds(), accumulator.quads());
            if (!mesh.isEmpty()) {
                meshes.add(mesh);
            }
        }
        return List.copyOf(meshes);
    }

    private static void prepareRenderCache(ClientLevel level) {
        long networkRevision = ClientPipeNetworkCache.revision();
        long appearanceRevision = ClientPipeAppearanceCache.revision();
        int renderDistance = Minecraft.getInstance().options.renderDistance().get();
        ResourceKey<Level> levelKey = level.dimension();
        ClientPipeNetworkCache.PipeRenderInvalidation networkInvalidation = ClientPipeNetworkCache.consumePipeRenderInvalidation();
        ClientPipeAppearanceCache.PipeAppearanceRenderInvalidation appearanceInvalidation = ClientPipeAppearanceCache.consumeRenderInvalidation();
        boolean missingNetworkInvalidation = networkRevision != cachedNetworkRevision
                && cachedNetworkRevision != Long.MIN_VALUE
                && networkInvalidation.isEmpty();
        boolean missingAppearanceInvalidation = appearanceRevision != cachedAppearanceRevision
                && cachedAppearanceRevision != Long.MIN_VALUE
                && appearanceInvalidation.isEmpty();
        if (networkInvalidation.full()
                || appearanceInvalidation.full()
                || missingNetworkInvalidation
                || missingAppearanceInvalidation
                || renderDistance != cachedRenderDistance
                || cachedLevelKey == null
                || !cachedLevelKey.equals(levelKey)) {
            MESH_CACHE.clear();
            SECTION_LOD_STATE.clear();
            releaseSectionCache();
        } else {
            invalidateMeshesForConnectionIds(networkInvalidation.removedConnectionIds(), true);
            invalidateMeshesForConnectionIds(networkInvalidation.updatedConnectionIds(), true);
            invalidateMeshesForConnectionKeys(appearanceInvalidation.changedConnectionKeys());
            invalidateSectionConnectionsForKeys(appearanceInvalidation.changedConnectionKeys());
        }
        cachedNetworkRevision = networkRevision;
        cachedAppearanceRevision = appearanceRevision;
        cachedRenderDistance = renderDistance;
        cachedLevelKey = levelKey;
    }

    private static void prepareSectionCache(ClientLevel level, Vec3 camera, double renderRadius) {
        int renderDistance = Minecraft.getInstance().options.renderDistance().get();
        ResourceKey<Level> levelKey = level.dimension();
        RenderSectionKey cameraSection = RenderSectionKey.containing(camera);
        if (cachedSectionLevelKey == null
                || !cachedSectionLevelKey.equals(levelKey)
                || renderDistance != cachedSectionRenderDistance) {
            releaseSectionCache();
        }
        boolean runtimeRebuildPending = ClientPipeNetworkCache.pendingRuntimeRebuilds(levelKey) > 0;
        if (!sectionCacheRefreshNeeded && cameraSection.equals(cachedCameraSection) && !runtimeRebuildPending) {
            return;
        }

        double cacheRadius = renderRadius + VISIBILITY_MARGIN + BLOCKS_PER_CHUNK;
        for (RuntimePipeConnection runtime : ClientPipeNetworkCache.runtimeConnectionsNear(level.dimension(), camera, cacheRadius)) {
            PipeAppearanceProfile appearance = ClientPipeAppearanceCache.profileFor(runtime.connection().connectionKey());
            ensureSectionConnectionIndexed(runtime, appearance);
        }
        pruneDistantSectionConnections(levelKey, camera, cacheRadius + SECTION_CACHE_RETAIN_BLOCKS);
        cachedSectionRenderDistance = renderDistance;
        cachedSectionLevelKey = levelKey;
        cachedCameraSection = cameraSection;
        sectionCacheRefreshNeeded = ClientPipeNetworkCache.pendingRuntimeRebuilds(levelKey) > 0;
    }

    private static void releaseSectionCache() {
        for (PipeSectionState section : SECTION_CACHE.values()) {
            section.release();
        }
        SECTION_CACHE.clear();
        SECTION_CONNECTION_INDEX.clear();
        cachedCameraSection = null;
        cachedSectionRenderDistance = Integer.MIN_VALUE;
        cachedSectionLevelKey = null;
        sectionCacheRefreshNeeded = true;
    }

    private static void invalidateMeshesForConnectionIds(Collection<UUID> connectionIds, boolean removeLodState) {
        if (connectionIds.isEmpty()) {
            return;
        }
        Set<UUID> idSet = Set.copyOf(connectionIds);
        MESH_CACHE.keySet().removeIf(key -> idSet.contains(key.connectionId()));
        if (removeLodState) {
            idSet.forEach(ClientPipeRenderer::invalidateSectionConnection);
            sectionCacheRefreshNeeded = true;
        }
    }

    private static void invalidateMeshesForConnectionKeys(Collection<Integer> connectionKeys) {
        if (connectionKeys.isEmpty()) {
            return;
        }
        Set<Integer> keySet = Set.copyOf(connectionKeys);
        MESH_CACHE.keySet().removeIf(key -> keySet.contains(key.connectionKey()));
    }

    private static void invalidateSectionConnectionsForKeys(Collection<Integer> connectionKeys) {
        if (connectionKeys.isEmpty()) {
            return;
        }
        Set<Integer> keySet = Set.copyOf(connectionKeys);
        Set<UUID> connectionIds = new LinkedHashSet<>();
        for (PipeSectionConnectionEntry entry : SECTION_CONNECTION_INDEX.values()) {
            if (keySet.contains(entry.connectionKey())) {
                connectionIds.add(entry.connectionId());
            }
        }
        for (int connectionKey : keySet) {
            ClientPipeNetworkCache.connectionByKey(connectionKey).ifPresent(connection -> connectionIds.add(connection.id()));
        }
        connectionIds.forEach(ClientPipeRenderer::invalidateSectionConnection);
        if (!connectionIds.isEmpty()) {
            sectionCacheRefreshNeeded = true;
        }
    }

    private static void ensureSectionConnectionIndexed(RuntimePipeConnection runtime, PipeAppearanceProfile profile) {
        PipeAppearanceProfile normalizedProfile = profile.normalizedToDefinitions();
        UUID connectionId = runtime.connection().id();
        int connectionHash = runtime.connection().hashCode();
        PipeSectionConnectionEntry existing = SECTION_CONNECTION_INDEX.get(connectionId);
        if (existing != null
                && existing.connectionHash() == connectionHash
                && existing.connectionKey() == runtime.connection().connectionKey()
                && existing.profile().equals(normalizedProfile)) {
            return;
        }

        invalidateSectionConnection(connectionId);
        Set<RenderSectionKey> sectionKeys = sectionKeysForRuntime(runtime);
        if (sectionKeys.isEmpty()) {
            return;
        }

        PipeSectionConnectionEntry entry = new PipeSectionConnectionEntry(runtime, normalizedProfile, sectionKeys);
        SECTION_CONNECTION_INDEX.put(connectionId, entry);
        for (RenderSectionKey sectionKey : sectionKeys) {
            SECTION_CACHE.computeIfAbsent(sectionKey, PipeSectionState::new).addConnection(connectionId);
            SECTION_LOD_STATE.remove(sectionKey);
        }
    }

    private static void invalidateSectionConnection(UUID connectionId) {
        PipeSectionConnectionEntry entry = SECTION_CONNECTION_INDEX.remove(connectionId);
        if (entry == null) {
            return;
        }
        for (RenderSectionKey sectionKey : entry.sectionKeys()) {
            PipeSectionState section = SECTION_CACHE.get(sectionKey);
            if (section == null) {
                continue;
            }
            section.removeConnection(connectionId);
            SECTION_LOD_STATE.remove(sectionKey);
            if (section.isEmpty()) {
                section.release();
                SECTION_CACHE.remove(sectionKey);
            }
        }
    }

    private static void pruneDistantSectionConnections(ResourceKey<Level> levelKey, Vec3 camera, double retainRadius) {
        List<UUID> staleConnectionIds = new ArrayList<>();
        for (PipeSectionConnectionEntry entry : SECTION_CONNECTION_INDEX.values()) {
            if (!entry.runtime().connection().levelKey().equals(levelKey)
                    || !entry.runtime().bounds().inflate(retainRadius).contains(camera)) {
                staleConnectionIds.add(entry.connectionId());
            }
        }
        staleConnectionIds.forEach(ClientPipeRenderer::invalidateSectionConnection);
    }

    private static Set<RenderSectionKey> sectionKeysForRuntime(RuntimePipeConnection runtime) {
        int samples = runtime.sampleCount();
        if (samples <= 0) {
            return Set.of();
        }
        Set<RenderSectionKey> keys = new LinkedHashSet<>();
        keys.add(RenderSectionKey.containing(runtime.sample(0)));
        for (int i = 1; i < samples; i++) {
            addSegmentSectionKeys(runtime.sample(i - 1), runtime.sample(i), keys);
        }
        keys.add(RenderSectionKey.containing(runtime.sample(samples - 1)));
        return Set.copyOf(keys);
    }

    private static void addSegmentSectionKeys(Vec3 from, Vec3 to, Set<RenderSectionKey> keys) {
        List<Double> cuts = sectionBreakpoints(from, to);
        for (int i = 1; i < cuts.size(); i++) {
            double t0 = cuts.get(i - 1);
            double t1 = cuts.get(i);
            if (t1 <= t0 + SURFACE_UV_EPSILON) {
                continue;
            }
            keys.add(RenderSectionKey.containing(lerp(from, to, (t0 + t1) * 0.5D)));
        }
    }

    private static int lodFor(RenderSectionKey sectionKey, AABB bounds, Vec3 camera) {
        double distance = distanceToAabb(camera, bounds);
        int raw = rawLodForDistance(distance);
        Integer previous = SECTION_LOD_STATE.get(sectionKey);
        int stable = previous == null ? raw : stableLod(previous, raw, distance);
        SECTION_LOD_STATE.put(sectionKey, stable);
        return stable;
    }

    private static int rawLodForDistance(double distance) {
        if (distance < LOD0_DISTANCE) {
            return 0;
        }
        if (distance < LOD1_DISTANCE) {
            return 1;
        }
        return distance < LOD2_DISTANCE ? 2 : 3;
    }

    private static int stableLod(int previous, int raw, double distance) {
        if (raw > previous) {
            return distance > lodExitDistance(previous) ? raw : previous;
        }
        if (raw < previous) {
            return distance < lodEnterDistance(raw) ? raw : previous;
        }
        return previous;
    }

    private static double lodExitDistance(int lod) {
        return switch (lod) {
            case 0 -> LOD0_EXIT_DISTANCE;
            case 1 -> LOD1_EXIT_DISTANCE;
            case 2 -> LOD2_EXIT_DISTANCE;
            default -> Double.POSITIVE_INFINITY;
        };
    }

    private static double lodEnterDistance(int lod) {
        return switch (lod) {
            case 0 -> LOD0_ENTER_DISTANCE;
            case 1 -> LOD1_ENTER_DISTANCE;
            case 2 -> LOD2_ENTER_DISTANCE;
            default -> Double.NEGATIVE_INFINITY;
        };
    }

    private static boolean isPotentiallyVisible(AABB bounds, Vec3 camera, double renderRadius, @Nullable Frustum frustum) {
        double distance = distanceToAabb(camera, bounds);
        if (distance > renderRadius + VISIBILITY_MARGIN) {
            return false;
        }
        if (distance <= ALWAYS_RENDER_RADIUS) {
            return true;
        }
        return frustum == null || frustum.isVisible(bounds.inflate(FRUSTUM_BOUNDS_INFLATE));
    }

    private static double distanceToAabb(Vec3 point, AABB bounds) {
        double dx = axisDistance(point.x, bounds.minX, bounds.maxX);
        double dy = axisDistance(point.y, bounds.minY, bounds.maxY);
        double dz = axisDistance(point.z, bounds.minZ, bounds.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double axisDistance(double value, double min, double max) {
        if (value < min) {
            return min - value;
        }
        return value > max ? value - max : 0.0D;
    }

    private static AABB sectionBounds(Section previous, Section current) {
        AABB bounds = new AABB(previous.center(), current.center());
        for (SectionSurface surface : previous.surfaces()) {
            bounds = include(bounds, surface.a());
            bounds = include(bounds, surface.b());
        }
        for (SectionSurface surface : current.surfaces()) {
            bounds = include(bounds, surface.a());
            bounds = include(bounds, surface.b());
        }
        return bounds.inflate(0.35D);
    }

    private static void addSegmentGeometry(Map<RenderSectionKey, MeshAccumulator> meshSections, Section previous, Section current, PipeSurfaceModel surfaceModel, Map<String, PipeCoatingRenderResolver.ResolvedPipeCoating> coatings, PipeConnectionAttributes attributes, boolean platform, double totalLength, TextureAtlasSprite markerSprite, boolean glow, int lod) {
        List<Double> cuts = sectionBreakpoints(previous.center(), current.center());
        for (int i = 1; i < cuts.size(); i++) {
            double t0 = cuts.get(i - 1);
            double t1 = cuts.get(i);
            if (t1 <= t0 + SURFACE_UV_EPSILON) {
                continue;
            }
            Section start = t0 <= SURFACE_UV_EPSILON ? previous : interpolateSection(previous, current, t0);
            Section end = t1 >= 1.0D - SURFACE_UV_EPSILON ? current : interpolateSection(previous, current, t1);
            List<TexturedQuad> segmentQuads = new ArrayList<>();
            addTexturedSectionFaces(segmentQuads, start, end, coatings, glow, lod);
            addPatternedStructuralBoxes(segmentQuads, start, end, surfaceModel.boxes(), coatings, glow, lod);
            addDecorativeCoatingBands(segmentQuads, start, end, surfaceModel, coatings, glow, lod);
            addFeatureMarkers(segmentQuads, start, end, surfaceModel.lanes(), attributes, platform, totalLength, markerSprite, lod);
            RenderSectionKey sectionKey = RenderSectionKey.containing(lerp(previous.center(), current.center(), (t0 + t1) * 0.5D));
            addMeshSection(meshSections, sectionKey, sectionBounds(start, end), segmentQuads);
        }
    }

    private static Section interpolateSection(Section previous, Section current, double t) {
        Vec3 center = lerp(previous.center(), current.center(), t);
        Vec3 right = safeNormalize(lerp(previous.right(), current.right(), t), previous.right());
        Vec3 up = safeNormalize(lerp(previous.up(), current.up(), t), previous.up());
        Vec3 tangent = safeNormalize(lerp(previous.tangent(), current.tangent(), t), current.tangent());
        double distance = previous.distance() + (current.distance() - previous.distance()) * t;
        double slideContactY = previous.slideContactY() + (current.slideContactY() - previous.slideContactY()) * t;
        int limit = Math.min(previous.surfaces().size(), current.surfaces().size());
        List<SectionSurface> surfaces = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            SectionSurface previousSurface = previous.surfaces().get(i);
            SectionSurface currentSurface = current.surfaces().get(i);
            surfaces.add(new SectionSurface(
                    previousSurface.slotId(),
                    lerp(previousSurface.a(), currentSurface.a(), t),
                    lerp(previousSurface.b(), currentSurface.b(), t),
                    previousSurface.vStart() + (currentSurface.vStart() - previousSurface.vStart()) * t,
                    previousSurface.vEnd() + (currentSurface.vEnd() - previousSurface.vEnd()) * t,
                    previousSurface.render() && currentSurface.render(),
                    previousSurface.visibility()));
        }
        return new Section(center, List.copyOf(surfaces), previous.perimeter() + (current.perimeter() - previous.perimeter()) * t, right, up, tangent, distance, slideContactY);
    }

    private static List<Double> sectionBreakpoints(Vec3 from, Vec3 to) {
        List<Double> cuts = new ArrayList<>();
        cuts.add(0.0D);
        addAxisSectionBreakpoints(cuts, from.x, to.x);
        addAxisSectionBreakpoints(cuts, from.y, to.y);
        addAxisSectionBreakpoints(cuts, from.z, to.z);
        cuts.add(1.0D);
        cuts.sort(Double::compare);
        List<Double> unique = new ArrayList<>(cuts.size());
        double previous = Double.NaN;
        for (double cut : cuts) {
            double clamped = clamp(cut, 0.0D, 1.0D);
            if (unique.isEmpty() || Math.abs(clamped - previous) > SURFACE_UV_EPSILON) {
                unique.add(clamped);
                previous = clamped;
            }
        }
        return unique;
    }

    private static void addAxisSectionBreakpoints(List<Double> cuts, double from, double to) {
        double delta = to - from;
        if (Math.abs(delta) <= SURFACE_UV_EPSILON) {
            return;
        }
        int fromSection = RenderSectionKey.sectionCoord(from);
        int toSection = RenderSectionKey.sectionCoord(to);
        if (fromSection == toSection) {
            return;
        }
        if (delta > 0.0D) {
            for (int section = fromSection + 1; section <= toSection; section++) {
                addSectionBreakpoint(cuts, from, delta, section * BLOCKS_PER_CHUNK);
            }
        } else {
            for (int section = fromSection; section > toSection; section--) {
                addSectionBreakpoint(cuts, from, delta, section * BLOCKS_PER_CHUNK);
            }
        }
    }

    private static void addSectionBreakpoint(List<Double> cuts, double from, double delta, double boundary) {
        double t = (boundary - from) / delta;
        if (t > SURFACE_UV_EPSILON && t < 1.0D - SURFACE_UV_EPSILON) {
            cuts.add(t);
        }
    }

    private static void addMeshSection(Map<RenderSectionKey, MeshAccumulator> meshSections, AABB bounds, Collection<TexturedQuad> quads) {
        if (quads.isEmpty()) {
            return;
        }
        RenderSectionKey sectionKey = RenderSectionKey.containing(bounds.getCenter());
        addMeshSection(meshSections, sectionKey, bounds, quads);
    }

    private static void addMeshSection(Map<RenderSectionKey, MeshAccumulator> meshSections, RenderSectionKey sectionKey, AABB bounds, Collection<TexturedQuad> quads) {
        if (quads.isEmpty()) {
            return;
        }
        meshSections.computeIfAbsent(sectionKey, ignored -> new MeshAccumulator(sectionKey)).add(bounds, quads);
    }

    private static void addTerminalGeometry(Map<RenderSectionKey, MeshAccumulator> meshSections, Section terminal, Vec3 inwardDirection, Map<String, PipeCoatingRenderResolver.ResolvedPipeCoating> coatings, boolean glow, int lod) {
        Vec3 inward = safeNormalize(inwardDirection, terminal.tangent());
        PipeCoatingRenderResolver.ResolvedPipeCoating fallback = coatings.values().stream().findFirst().orElse(PipeCoatingRenderResolver.resolve(PipeAppearanceDefinitions.defaultSelectionForSlot("body")));
        List<TexturedQuad> terminalQuads = new ArrayList<>();
        for (SectionSurface surface : terminal.surfaces()) {
            if (!surface.render()) {
                continue;
            }
            PipeCoatingRenderResolver.ResolvedPipeCoating coating = coatings.getOrDefault(surface.slotId(), fallback);
            addTerminalSurface(terminalQuads, terminal, surface, inward, coating, glow, lod);
        }
        if (!terminalQuads.isEmpty()) {
            addMeshSection(meshSections, quadBounds(terminalQuads).inflate(0.05D), terminalQuads);
        }
    }

    private static void addTerminalSurface(List<TexturedQuad> quads, Section terminal, SectionSurface surface, Vec3 inward, PipeCoatingRenderResolver.ResolvedPipeCoating coating, boolean glow, int lod) {
        if (surface.visibility() != PipeSurfaceModel.FaceVisibility.SINGLE_SIDED_OUTWARD) {
            return;
        }
        Vec3 profileCenter = sectionProfileCenter(terminal);
        Vec3 startA = insetToward(surface.a(), profileCenter, TERMINAL_INNER_INSET).add(inward.scale(TERMINAL_SLEEVE_START));
        Vec3 startB = insetToward(surface.b(), profileCenter, TERMINAL_INNER_INSET).add(inward.scale(TERMINAL_SLEEVE_START));
        Vec3 sleeveA = startA.add(inward.scale(TERMINAL_SLEEVE_LENGTH));
        Vec3 sleeveB = startB.add(inward.scale(TERMINAL_SLEEVE_LENGTH));
        Vec3 surfaceMid = surface.a().add(surface.b()).scale(0.5D);
        Vec3 innerNormal = safeNormalize(profileCenter.subtract(surfaceMid), terminal.up().scale(-1.0D));
        addTerminalSleeveMappedQuad(quads, startB, startA, sleeveA, sleeveB, surface.vStart(), surface.vEnd(), innerNormal, coating, glow, lod);
    }

    private static Vec3 sectionProfileCenter(Section section) {
        return section.center().subtract(section.up().scale(section.slideContactY()));
    }

    private static Vec3 insetToward(Vec3 point, Vec3 target, double amount) {
        Vec3 toTarget = target.subtract(point);
        if (toTarget.lengthSqr() < 1.0E-8D) {
            return point;
        }
        return point.add(toTarget.normalize().scale(amount));
    }

    private static void addTerminalSleeveMappedQuad(List<TexturedQuad> quads, Vec3 a, Vec3 b, Vec3 c, Vec3 d, double vStartWorld, double vEndWorld, Vec3 preferredNormal, PipeCoatingRenderResolver.ResolvedPipeCoating coating, boolean glow, int lod) {
        Vec3 normal = safeNormalize(preferredNormal, quadNormal(a, b, d));
        int color = shadeTint(coating.opaqueTint(), normal, glow);
        double uSpan = TERMINAL_SLEEVE_LENGTH / lodTextureTileUBlocks(lod);
        double v0World = vStartWorld / PIPE_TEXTURE_TILE_V_BLOCKS;
        double v1World = vEndWorld / PIPE_TEXTURE_TILE_V_BLOCKS;
        double vBase = Math.floor(v0World);
        quads.add(glow
                ? emissiveTexturedQuad(
                        a,
                        b,
                        c,
                        d,
                        coating.u(SURFACE_TILE_UV_INSET),
                        coating.u(tileFraction(uSpan, 0.0D)),
                        coating.v(tileFraction(v0World, vBase)),
                        coating.v(tileFraction(v1World, vBase)),
                        color,
                        normal,
                        coating.generatedTexture(),
                        coating.textureId(),
                        coating.translucent(),
                        false,
                        MARKER_ANIMATION_NONE,
                        0.0D)
                : texturedQuad(
                        a,
                        b,
                        c,
                        d,
                        coating.u(SURFACE_TILE_UV_INSET),
                        coating.u(tileFraction(uSpan, 0.0D)),
                        coating.v(tileFraction(v0World, vBase)),
                        coating.v(tileFraction(v1World, vBase)),
                        color,
                        normal,
                        coating.generatedTexture(),
                        coating.textureId(),
                        coating.translucent(),
                        false,
                        false,
                        MARKER_ANIMATION_NONE,
                        0.0D));
    }

    private static AABB quadBounds(Collection<TexturedQuad> quads) {
        AABB bounds = null;
        for (TexturedQuad quad : quads) {
            AABB quadBounds = new AABB(quad.a(), quad.a());
            quadBounds = include(quadBounds, quad.b());
            quadBounds = include(quadBounds, quad.c());
            quadBounds = include(quadBounds, quad.d());
            bounds = bounds == null ? quadBounds : union(bounds, quadBounds);
        }
        return bounds == null ? new AABB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D) : bounds;
    }

    private static AABB include(AABB bounds, Vec3 point) {
        return new AABB(
                Math.min(bounds.minX, point.x),
                Math.min(bounds.minY, point.y),
                Math.min(bounds.minZ, point.z),
                Math.max(bounds.maxX, point.x),
                Math.max(bounds.maxY, point.y),
                Math.max(bounds.maxZ, point.z));
    }

    private static AABB union(AABB first, AABB second) {
        return new AABB(
                Math.min(first.minX, second.minX),
                Math.min(first.minY, second.minY),
                Math.min(first.minZ, second.minZ),
                Math.max(first.maxX, second.maxX),
                Math.max(first.maxY, second.maxY),
                Math.max(first.maxZ, second.maxZ));
    }

    private static TextureAtlasSprite markerSprite() {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(MARKER_TEXTURE);
    }

    private static void addFeatureMarkers(List<TexturedQuad> quads, Section previous, Section current, PipeSurfaceModel.MarkerLanes lanes, PipeConnectionAttributes attributes, boolean platform, double totalLength, TextureAtlasSprite sprite, int lod) {
        if (lod >= 3) {
            if (platform) {
                addPlatformDockMarkers(quads, previous, current, lanes, totalLength, sprite, true);
            }
            if (attributes.highway()) {
                addHighwaySpineMarkers(quads, previous, current, lanes, sprite, true);
            }
            if (attributes.acceleration()) {
                addAccelerationImpulseMarkers(quads, previous, current, lanes, sprite, true);
            }
            if (attributes.directionLimit() != 0) {
                addDirectionMarkers(quads, previous, current, lanes.directionCenter(), lanes.directionWidth(), attributes.directionLimit(), sprite, lod);
            }
            return;
        }
        if (platform) {
            addPlatformDockMarkers(quads, previous, current, lanes, totalLength, sprite, false);
        }
        if (attributes.highway()) {
            addHighwaySpineMarkers(quads, previous, current, lanes, sprite, false);
        }
        if (attributes.acceleration()) {
            addAccelerationImpulseMarkers(quads, previous, current, lanes, sprite, lod >= 2);
        }
        if (attributes.directionLimit() != 0) {
            addDirectionMarkers(quads, previous, current, lanes.directionCenter(), lanes.directionWidth(), attributes.directionLimit(), sprite, lod);
        }
    }

    private static void addPlatformDockMarkers(List<TexturedQuad> quads, Section previous, Section current, PipeSurfaceModel.MarkerLanes lanes, double totalLength, TextureAtlasSprite sprite, boolean simple) {
        double width = simple ? lanes.platformWidth() * 0.95D : lanes.platformWidth() * 1.28D;
        addContinuousMarkerBand(quads, previous, current, lanes.platformCenter(), width * 1.18D, PLATFORM_SHADOW_COLOR, sprite, 0);
        addContinuousMarkerBand(quads, previous, current, lanes.platformCenter(), width, PLATFORM_MARKER_COLOR, sprite, 1);
        if (simple) {
            return;
        }
        addContinuousMarkerBand(quads, previous, current, lanes.platformCenter() - width * 0.42D, width * 0.105D, PLATFORM_SAFETY_COLOR, sprite, 2);
        addContinuousMarkerBand(quads, previous, current, lanes.platformCenter() + width * 0.42D, width * 0.105D, PLATFORM_SAFETY_COLOR, sprite, 2);
        addPatternedMarkerBand(quads, previous, current, lanes.platformCenter(), width * 0.24D, 0.44D, 0.055D, 0.10D, PLATFORM_SHADOW_COLOR, sprite, 3);
        addMarkerBand(quads, previous, current, 0.0D, Math.min(0.30D, totalLength * 0.42D), lanes.platformCenter(), width * 1.38D, PLATFORM_EDGE_COLOR, sprite, 4);
        addMarkerBand(quads, previous, current, Math.max(0.0D, totalLength - 0.30D), totalLength, lanes.platformCenter(), width * 1.38D, PLATFORM_EDGE_COLOR, sprite, 4);
    }

    private static void addHighwaySpineMarkers(List<TexturedQuad> quads, Section previous, Section current, PipeSurfaceModel.MarkerLanes lanes, TextureAtlasSprite sprite, boolean simple) {
        double width = lanes.highwayWidth();
        addContinuousMarkerBand(quads, previous, current, lanes.highwayCenter(), width * 0.22D, HIGHWAY_MARKER_COLOR, sprite, 0);
        addContinuousMarkerBand(quads, previous, current, lanes.highwayCenter() - width * 0.52D, width * 0.14D, HIGHWAY_EDGE_COLOR, sprite, 0);
        addContinuousMarkerBand(quads, previous, current, lanes.highwayCenter() + width * 0.52D, width * 0.14D, HIGHWAY_EDGE_COLOR, sprite, 0);
        double period = simple ? 1.46D : 0.92D;
        double length = simple ? 0.30D : 0.36D;
        addPatternedMarkerDiamond(quads, previous, current, lanes.highwayCenter(), width * 0.86D, period, length, 0.20D, HIGHWAY_HIGHLIGHT_COLOR, sprite, 2, MARKER_ANIMATION_HIGHWAY);
        if (!simple) {
            addPatternedMarkerDiamond(quads, previous, current, lanes.highwayCenter() - width * 0.52D, width * 0.34D, period, length * 0.72D, 0.32D, HIGHWAY_HIGHLIGHT_COLOR, sprite, 3, MARKER_ANIMATION_HIGHWAY);
            addPatternedMarkerDiamond(quads, previous, current, lanes.highwayCenter() + width * 0.52D, width * 0.34D, period, length * 0.72D, 0.32D, HIGHWAY_HIGHLIGHT_COLOR, sprite, 3, MARKER_ANIMATION_HIGHWAY);
        }
    }

    private static void addAccelerationImpulseMarkers(List<TexturedQuad> quads, Section previous, Section current, PipeSurfaceModel.MarkerLanes lanes, TextureAtlasSprite sprite, boolean simple) {
        double width = simple ? lanes.accelerationWidth() * 0.78D : lanes.accelerationWidth();
        double period = simple ? 1.14D : 0.74D;
        double length = simple ? 0.42D : 0.48D;
        addPatternedMarkerDiamond(quads, previous, current, lanes.accelerationCenter(), width * 0.92D, period, length, 0.04D, ACCELERATION_MARKER_COLOR, sprite, 1, MARKER_ANIMATION_ACCELERATION);
        addPatternedMarkerDiamond(quads, previous, current, lanes.accelerationCenter(), width * 0.78D, period, length * 0.62D, 0.26D, ACCELERATION_MARKER_COLOR, sprite, 2, MARKER_ANIMATION_ACCELERATION);
        if (!simple) {
            addPatternedMarkerDiamond(quads, previous, current, lanes.accelerationCenter(), width * 0.34D, period, length * 0.34D, 0.42D, ACCELERATION_CORE_COLOR, sprite, 3, MARKER_ANIMATION_ACCELERATION);
        }
    }

    private static void addContinuousMarkerBand(List<TexturedQuad> quads, Section previous, Section current, double vCenter, double vWidth, int color, TextureAtlasSprite sprite, int layer) {
        addMarkerBand(quads, previous, current, previous.distance(), current.distance(), vCenter, vWidth, color, sprite, layer);
    }

    private static void addPatternedMarkerBand(List<TexturedQuad> quads, Section previous, Section current, double vCenter, double vWidth, double period, double length, double phase, int color, TextureAtlasSprite sprite, int layer) {
        double start = previous.distance();
        double end = current.distance();
        int first = (int) Math.floor((start - phase) / period) - 1;
        int last = (int) Math.ceil((end - phase) / period) + 1;
        for (int i = first; i <= last; i++) {
            double u0 = phase + i * period;
            addMarkerBand(quads, previous, current, u0, u0 + length, vCenter, vWidth, color, sprite, layer);
        }
    }

    private static void addPatternedMarkerDiamond(List<TexturedQuad> quads, Section previous, Section current, double vCenter, double vWidth, double period, double length, double phase, int color, TextureAtlasSprite sprite, int layer, int animationKind) {
        double start = previous.distance();
        double end = current.distance();
        int first = (int) Math.floor((start - phase) / period) - 1;
        int last = (int) Math.ceil((end - phase) / period) + 1;
        for (int i = first; i <= last; i++) {
            double u0 = phase + i * period;
            addMarkerDiamond(quads, previous, current, u0, u0 + length, vCenter, vWidth, color, sprite, layer, animationKind, i * 0.19D);
        }
    }

    private static void addDirectionMarkers(List<TexturedQuad> quads, Section previous, Section current, double vCenter, double vWidth, int directionLimit, TextureAtlasSprite sprite, int lod) {
        double period = lod >= 2 ? 1.85D : 1.12D;
        double start = previous.distance();
        double end = current.distance();
        int first = (int) Math.floor(start / period) - 1;
        int last = (int) Math.ceil(end / period) + 1;
        for (int i = first; i <= last; i++) {
            double base = i * period;
            if (directionLimit >= 0) {
                addMarkerBand(quads, previous, current, base + 0.05D, base + 0.28D, vCenter, vWidth * 0.42D, DIRECTION_MARKER_COLOR, sprite, 2);
                addMarkerTaperedBand(quads, previous, current, base + 0.23D, base + 0.55D, vCenter, vWidth * 1.06D, vWidth * 0.08D, DIRECTION_MARKER_COLOR, sprite, 3, MARKER_ANIMATION_DIRECTION, i * 0.11D);
                addMarkerTaperedBand(quads, previous, current, base + 0.34D, base + 0.49D, vCenter, vWidth * 0.42D, vWidth * 0.08D, DIRECTION_CORE_COLOR, sprite, 4, MARKER_ANIMATION_DIRECTION, i * 0.11D + 0.08D);
            } else {
                addMarkerBand(quads, previous, current, base + 0.32D, base + 0.55D, vCenter, vWidth * 0.42D, DIRECTION_MARKER_COLOR, sprite, 2);
                addMarkerTaperedBand(quads, previous, current, base + 0.05D, base + 0.37D, vCenter, vWidth * 0.08D, vWidth * 1.06D, DIRECTION_MARKER_COLOR, sprite, 3, MARKER_ANIMATION_DIRECTION, i * 0.11D);
                addMarkerTaperedBand(quads, previous, current, base + 0.11D, base + 0.26D, vCenter, vWidth * 0.08D, vWidth * 0.42D, DIRECTION_CORE_COLOR, sprite, 4, MARKER_ANIMATION_DIRECTION, i * 0.11D + 0.08D);
            }
        }
    }

    private static void addMarkerDiamond(List<TexturedQuad> quads, Section previous, Section current, double uStart, double uEnd, double vCenter, double vWidth, int color, TextureAtlasSprite sprite, int layer, int animationKind, double animationPhase) {
        double mid = (uStart + uEnd) * 0.5D;
        double tipWidth = Math.max(0.004D, vWidth * 0.06D);
        addMarkerTaperedBand(quads, previous, current, uStart, mid, vCenter, tipWidth, vWidth, color, sprite, layer, animationKind, animationPhase);
        addMarkerTaperedBand(quads, previous, current, mid, uEnd, vCenter, vWidth, tipWidth, color, sprite, layer, animationKind, animationPhase);
    }

    private static void addMarkerTaperedBand(List<TexturedQuad> quads, Section previous, Section current, double uStart, double uEnd, double vCenter, double startWidth, double endWidth, int color, TextureAtlasSprite sprite, int layer) {
        addMarkerTaperedBand(quads, previous, current, uStart, uEnd, vCenter, startWidth, endWidth, color, sprite, layer, MARKER_ANIMATION_NONE, 0.0D);
    }

    private static void addMarkerTaperedBand(List<TexturedQuad> quads, Section previous, Section current, double uStart, double uEnd, double vCenter, double startWidth, double endWidth, int color, TextureAtlasSprite sprite, int layer, int animationKind, double animationPhase) {
        double clippedUStart = Math.max(uStart, previous.distance());
        double clippedUEnd = Math.min(uEnd, current.distance());
        if (clippedUEnd <= clippedUStart + SURFACE_UV_EPSILON || uEnd <= uStart + SURFACE_UV_EPSILON) {
            return;
        }
        double t0 = (clippedUStart - uStart) / (uEnd - uStart);
        double t1 = (clippedUEnd - uStart) / (uEnd - uStart);
        double width0 = startWidth + (endWidth - startWidth) * t0;
        double width1 = startWidth + (endWidth - startWidth) * t1;
        if (width0 <= SURFACE_UV_EPSILON && width1 <= SURFACE_UV_EPSILON) {
            return;
        }
        addMarkerTaperedRange(
                quads,
                previous,
                current,
                clippedUStart,
                clippedUEnd,
                vCenter - width0 * 0.5D,
                vCenter + width0 * 0.5D,
                vCenter - width1 * 0.5D,
                vCenter + width1 * 0.5D,
                color,
                sprite,
                layer,
                animationKind,
                animationPhase);
    }

    private static void addMarkerTaperedRange(List<TexturedQuad> quads, Section previous, Section current, double uStart, double uEnd, double vStart0, double vEnd0, double vStart1, double vEnd1, int color, TextureAtlasSprite sprite, int layer, int animationKind, double animationPhase) {
        double segmentLength = current.distance() - previous.distance();
        if (segmentLength <= SURFACE_UV_EPSILON) {
            return;
        }
        double minV = Math.min(Math.min(vStart0, vEnd0), Math.min(vStart1, vEnd1));
        double maxV = Math.max(Math.max(vStart0, vEnd0), Math.max(vStart1, vEnd1));
        int limit = Math.min(previous.surfaces().size(), current.surfaces().size());
        for (int i = 0; i < limit; i++) {
            SectionSurface previousSurface = previous.surfaces().get(i);
            SectionSurface currentSurface = current.surfaces().get(i);
            double faceStart = previousSurface.vStart();
            double faceEnd = previousSurface.vEnd();
            if (minV < faceStart - SURFACE_UV_EPSILON || maxV > faceEnd + SURFACE_UV_EPSILON) {
                continue;
            }
            double uT0 = (uStart - previous.distance()) / segmentLength;
            double uT1 = (uEnd - previous.distance()) / segmentLength;
            double t00 = (vStart0 - faceStart) / (faceEnd - faceStart);
            double t01 = (vEnd0 - faceStart) / (faceEnd - faceStart);
            double t11 = (vEnd1 - faceStart) / (faceEnd - faceStart);
            double t10 = (vStart1 - faceStart) / (faceEnd - faceStart);
            Vec3 p00 = lerp(surfacePoint(previousSurface.a(), previousSurface.b(), t00), surfacePoint(currentSurface.a(), currentSurface.b(), t00), uT0);
            Vec3 p01 = lerp(surfacePoint(previousSurface.a(), previousSurface.b(), t01), surfacePoint(currentSurface.a(), currentSurface.b(), t01), uT0);
            Vec3 p11 = lerp(surfacePoint(previousSurface.a(), previousSurface.b(), t11), surfacePoint(currentSurface.a(), currentSurface.b(), t11), uT1);
            Vec3 p10 = lerp(surfacePoint(previousSurface.a(), previousSurface.b(), t10), surfacePoint(currentSurface.a(), currentSurface.b(), t10), uT1);
            Vec3 center0 = lerp(previous.center(), current.center(), uT0);
            Vec3 center1 = lerp(previous.center(), current.center(), uT1);
            addSolidMarkerQuad(quads, p00, p01, p11, p10, markerSurfaceNormal(p00, p01, p10, center0, center1), color, sprite, layer, animationKind, animationPhase);
            return;
        }
        addMarkerBand(quads, previous, current, uStart, uEnd, (minV + maxV) * 0.5D, maxV - minV, color, sprite, layer);
    }

    private static void addMarkerBand(List<TexturedQuad> quads, Section previous, Section current, double uStart, double uEnd, double vCenter, double vWidth, int color, TextureAtlasSprite sprite, int layer) {
        double clippedUStart = Math.max(uStart, previous.distance());
        double clippedUEnd = Math.min(uEnd, current.distance());
        if (clippedUEnd <= clippedUStart + SURFACE_UV_EPSILON || vWidth <= SURFACE_UV_EPSILON) {
            return;
        }
        double perimeter = previous.perimeter();
        if (perimeter <= SURFACE_UV_EPSILON) {
            return;
        }
        double start = vCenter - vWidth * 0.5D;
        double end = vCenter + vWidth * 0.5D;
        while (start < 0.0D) {
            start += perimeter;
            end += perimeter;
        }
        if (end <= perimeter) {
            addMarkerRange(quads, previous, current, clippedUStart, clippedUEnd, start, end, color, sprite, layer);
        } else {
            addMarkerRange(quads, previous, current, clippedUStart, clippedUEnd, start, perimeter, color, sprite, layer);
            addMarkerRange(quads, previous, current, clippedUStart, clippedUEnd, 0.0D, end - perimeter, color, sprite, layer);
        }
    }

    private static void addMarkerRange(List<TexturedQuad> quads, Section previous, Section current, double uStart, double uEnd, double vStart, double vEnd, int color, TextureAtlasSprite sprite, int layer) {
        double segmentLength = current.distance() - previous.distance();
        if (segmentLength <= SURFACE_UV_EPSILON) {
            return;
        }
        int limit = Math.min(previous.surfaces().size(), current.surfaces().size());
        for (int i = 0; i < limit; i++) {
            SectionSurface previousSurface = previous.surfaces().get(i);
            SectionSurface currentSurface = current.surfaces().get(i);
            double faceStart = previousSurface.vStart();
            double faceEnd = previousSurface.vEnd();
            double overlapStart = Math.max(vStart, faceStart);
            double overlapEnd = Math.min(vEnd, faceEnd);
            if (overlapEnd <= overlapStart + SURFACE_UV_EPSILON) {
                continue;
            }
            double t0 = (overlapStart - faceStart) / (faceEnd - faceStart);
            double t1 = (overlapEnd - faceStart) / (faceEnd - faceStart);
            double uT0 = (uStart - previous.distance()) / segmentLength;
            double uT1 = (uEnd - previous.distance()) / segmentLength;
            Vec3 p00 = lerp(surfacePoint(previousSurface.a(), previousSurface.b(), t0), surfacePoint(currentSurface.a(), currentSurface.b(), t0), uT0);
            Vec3 p01 = lerp(surfacePoint(previousSurface.a(), previousSurface.b(), t1), surfacePoint(currentSurface.a(), currentSurface.b(), t1), uT0);
            Vec3 p11 = lerp(surfacePoint(previousSurface.a(), previousSurface.b(), t1), surfacePoint(currentSurface.a(), currentSurface.b(), t1), uT1);
            Vec3 p10 = lerp(surfacePoint(previousSurface.a(), previousSurface.b(), t0), surfacePoint(currentSurface.a(), currentSurface.b(), t0), uT1);
            Vec3 center0 = lerp(previous.center(), current.center(), uT0);
            Vec3 center1 = lerp(previous.center(), current.center(), uT1);
            addSolidMarkerQuad(quads, p00, p01, p11, p10, markerSurfaceNormal(p00, p01, p10, center0, center1), color, sprite, layer);
        }
    }

    private static Vec3 markerSurfaceNormal(Vec3 a, Vec3 b, Vec3 d, Vec3 center0, Vec3 center1) {
        Vec3 normal = b.subtract(a).cross(d.subtract(a));
        if (normal.lengthSqr() < 1.0E-8D) {
            normal = quadNormal(a, b, d);
        } else {
            normal = normal.normalize();
        }
        Vec3 quadCenter = new Vec3(
                (a.x + b.x + d.x) / 3.0D,
                (a.y + b.y + d.y) / 3.0D,
                (a.z + b.z + d.z) / 3.0D);
        Vec3 center = center0.add(center1).scale(0.5D);
        Vec3 outward = quadCenter.subtract(center);
        if (outward.lengthSqr() > 1.0E-8D && normal.dot(outward) < 0.0D) {
            normal = normal.scale(-1.0D);
        }
        return normal;
    }

    private static void addSolidMarkerQuad(List<TexturedQuad> quads, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal, int color, TextureAtlasSprite sprite, int layer) {
        addSolidMarkerQuad(quads, a, b, c, d, normal, color, sprite, layer, MARKER_ANIMATION_NONE, 0.0D);
    }

    private static void addSolidMarkerQuad(List<TexturedQuad> quads, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal, int color, TextureAtlasSprite sprite, int layer, int animationKind, double animationPhase) {
        Vec3 offset = normal.scale(MARKER_SURFACE_OFFSET + Math.max(0, layer) * MARKER_LAYER_OFFSET);
        Vec3 aa = a.add(offset);
        Vec3 bb = d.add(offset);
        Vec3 cc = c.add(offset);
        Vec3 dd = b.add(offset);
        if (quadNormal(aa, bb, dd).dot(normal) < 0.0D) {
            Vec3 swap = bb;
            bb = dd;
            dd = swap;
        }
        quads.add(texturedQuad(
                aa,
                bb,
                cc,
                dd,
                sprite.getU(0.18F),
                sprite.getU(0.82F),
                sprite.getV(0.18F),
                sprite.getV(0.82F),
                color,
                normal,
                false,
                sprite.atlasLocation(),
                false,
                true,
                false,
                false,
                animationKind,
                animationPhase));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void addTexturedSectionFaces(List<TexturedQuad> quads, Section previous, Section current, Map<String, PipeCoatingRenderResolver.ResolvedPipeCoating> coatings, boolean glow, int lod) {
        int limit = Math.min(previous.surfaces().size(), current.surfaces().size());
        for (int i = 0; i < limit; i++) {
            SectionSurface previousSurface = previous.surfaces().get(i);
            SectionSurface currentSurface = current.surfaces().get(i);
            if (!previousSurface.render() || !currentSurface.render()) {
                continue;
            }
            PipeCoatingRenderResolver.ResolvedPipeCoating coating = coatings.getOrDefault(previousSurface.slotId(), coatings.values().stream().findFirst().orElse(PipeCoatingRenderResolver.resolve(PipeAppearanceDefinitions.defaultSelectionForSlot("body"))));
            Vec3 normal = quadNormal(previousSurface.a(), previousSurface.b(), currentSurface.a());
            int color = shadeTint(coating.opaqueTint(), normal, glow);
            addSurfaceMappedQuad(
                    quads,
                    previousSurface.a(),
                    previousSurface.b(),
                    currentSurface.b(),
                    currentSurface.a(),
                    previous.distance(),
                    current.distance(),
                    previousSurface.vStart(),
                    previousSurface.vEnd(),
                    color,
                    glow,
                    coating,
                    shouldCullSurface(coating, previousSurface),
                    lod);
        }
    }

    private static void addPatternedStructuralBoxes(List<TexturedQuad> quads, Section previous, Section current, List<PipeSurfaceModel.PatternedBox> boxes, Map<String, PipeCoatingRenderResolver.ResolvedPipeCoating> coatings, boolean glow, int lod) {
        if (boxes.isEmpty() || lod >= 3) {
            return;
        }
        PipeCoatingRenderResolver.ResolvedPipeCoating fallback = coatings.values().stream().findFirst().orElse(PipeCoatingRenderResolver.resolve(PipeAppearanceDefinitions.defaultSelectionForSlot("body")));
        for (PipeSurfaceModel.PatternedBox box : boxes) {
            PipeCoatingRenderResolver.ResolvedPipeCoating coating = coatings.getOrDefault(box.slotId(), fallback);
            double period = Math.max(SURFACE_UV_EPSILON, box.period() * (lod >= 2 ? 2.0D : 1.0D));
            double length = Math.max(SURFACE_UV_EPSILON, box.length());
            double start = previous.distance();
            double end = current.distance();
            int first = (int) Math.floor((start - box.phase()) / period) - 1;
            int last = (int) Math.ceil((end - box.phase()) / period) + 1;
            for (int i = first; i <= last; i++) {
                double boxStart = box.phase() + i * period;
                double boxEnd = boxStart + length;
                addPatternedBoxRange(quads, previous, current, Math.max(start, boxStart), Math.min(end, boxEnd), boxStart, boxEnd, box, coating, glow, lod);
            }
        }
    }

    private static void addPatternedBoxRange(List<TexturedQuad> quads, Section previous, Section current, double uStart, double uEnd, double boxStart, double boxEnd, PipeSurfaceModel.PatternedBox box, PipeCoatingRenderResolver.ResolvedPipeCoating coating, boolean glow, int lod) {
        double segmentLength = current.distance() - previous.distance();
        if (segmentLength <= SURFACE_UV_EPSILON || uEnd <= uStart + SURFACE_UV_EPSILON) {
            return;
        }
        double uT0 = (uStart - previous.distance()) / segmentLength;
        double uT1 = (uEnd - previous.distance()) / segmentLength;
        double left = Math.min(box.left(), box.right());
        double right = Math.max(box.left(), box.right());
        double bottom = Math.min(box.bottom(), box.top());
        double top = Math.max(box.bottom(), box.top());
        Vec3 slb = sectionLocalPoint(previous, current, uT0, left, bottom);
        Vec3 slt = sectionLocalPoint(previous, current, uT0, left, top);
        Vec3 srb = sectionLocalPoint(previous, current, uT0, right, bottom);
        Vec3 srt = sectionLocalPoint(previous, current, uT0, right, top);
        Vec3 elb = sectionLocalPoint(previous, current, uT1, left, bottom);
        Vec3 elt = sectionLocalPoint(previous, current, uT1, left, top);
        Vec3 erb = sectionLocalPoint(previous, current, uT1, right, bottom);
        Vec3 ert = sectionLocalPoint(previous, current, uT1, right, top);
        addBoxSurface(quads, slt, srt, ert, elt, uStart, uEnd, left, right, coating, glow, lod);
        if (lod <= 1) {
            addBoxSurface(quads, slb, slt, elt, elb, uStart, uEnd, bottom, top, coating, glow, lod);
            addBoxSurface(quads, srt, srb, erb, ert, uStart, uEnd, bottom, top, coating, glow, lod);
        }
        if (lod == 0) {
            addBoxSurface(quads, srb, slb, elb, erb, uStart, uEnd, left, right, coating, glow, lod);
            if (uStart <= boxStart + SURFACE_UV_EPSILON) {
                addBoxSurface(quads, slb, slt, srt, srb, left, right, bottom, top, coating, glow, lod);
            }
            if (uEnd >= boxEnd - SURFACE_UV_EPSILON) {
                addBoxSurface(quads, erb, ert, elt, elb, left, right, bottom, top, coating, glow, lod);
            }
        }
    }

    private static Vec3 sectionLocalPoint(Section section, double localX, double localY) {
        return localPoint(section.center(), section.right(), section.up(), localX, localY, section.slideContactY());
    }

    private static Vec3 sectionLocalPoint(Section previous, Section current, double uT, double localX, double localY) {
        return lerp(sectionLocalPoint(previous, localX, localY), sectionLocalPoint(current, localX, localY), uT);
    }

    private static void addBoxSurface(List<TexturedQuad> quads, Vec3 p00, Vec3 p01, Vec3 p11, Vec3 p10, double uStartWorld, double uEndWorld, double vStartWorld, double vEndWorld, PipeCoatingRenderResolver.ResolvedPipeCoating coating, boolean glow, int lod) {
        Vec3 normal = quadNormal(p00, p01, p10);
        int color = shadeTint(coating.opaqueTint(), normal, glow);
        addSurfaceMappedQuad(quads, p00, p01, p11, p10, uStartWorld, uEndWorld, vStartWorld, vEndWorld, color, glow, coating, false, lod);
    }

    private static void addSurfaceMappedQuad(List<TexturedQuad> quads, Vec3 p00, Vec3 p01, Vec3 p11, Vec3 p10, double uStartWorld, double uEndWorld, double vStartWorld, double vEndWorld, int color, boolean fullBright, PipeCoatingRenderResolver.ResolvedPipeCoating coating, boolean cullBackFace, int lod) {
        double uTileBlocks = lodTextureTileUBlocks(lod);
        double u0 = uStartWorld / uTileBlocks;
        double u1 = uEndWorld / uTileBlocks;
        double v0 = vStartWorld / PIPE_TEXTURE_TILE_V_BLOCKS;
        double v1 = vEndWorld / PIPE_TEXTURE_TILE_V_BLOCKS;
        if (u1 <= u0 + SURFACE_UV_EPSILON || v1 <= v0 + SURFACE_UV_EPSILON) {
            return;
        }
        double cursorU = u0;
        while (cursorU < u1 - SURFACE_UV_EPSILON) {
            double nextU = nextTileBoundary(cursorU, u1);
            double tU0 = (cursorU - u0) / (u1 - u0);
            double tU1 = (nextU - u0) / (u1 - u0);
            double cursorV = v0;
            while (cursorV < v1 - SURFACE_UV_EPSILON) {
                double nextV = nextTileBoundary(cursorV, v1);
                double tV0 = (cursorV - v0) / (v1 - v0);
                double tV1 = (nextV - v0) / (v1 - v0);
                Vec3 aa = surfacePoint(p00, p01, p11, p10, tU0, tV0);
                Vec3 bb = surfacePoint(p00, p01, p11, p10, tU1, tV0);
                Vec3 cc = surfacePoint(p00, p01, p11, p10, tU1, tV1);
                Vec3 dd = surfacePoint(p00, p01, p11, p10, tU0, tV1);
                double uBase = Math.floor(cursorU);
                double vBase = Math.floor(cursorV);
                float spriteU0 = coating.u(tileFraction(cursorU, uBase));
                float spriteU1 = coating.u(tileFraction(nextU, uBase));
                float spriteV0 = coating.v(tileFraction(cursorV, vBase));
                float spriteV1 = coating.v(tileFraction(nextV, vBase));
                Vec3 normal = quadNormal(aa, bb, dd);
                quads.add(fullBright
                        ? emissiveTexturedQuad(aa, bb, cc, dd, spriteU0, spriteU1, spriteV0, spriteV1, color, normal, coating.generatedTexture(), coating.textureId(), coating.translucent(), cullBackFace, MARKER_ANIMATION_NONE, 0.0D)
                        : texturedQuad(aa, bb, cc, dd, spriteU0, spriteU1, spriteV0, spriteV1, color, normal, coating.generatedTexture(), coating.textureId(), coating.translucent(), false, cullBackFace, MARKER_ANIMATION_NONE, 0.0D));
                cursorV = nextV;
            }
            cursorU = nextU;
        }
    }

    private static boolean shouldCullSurface(PipeCoatingRenderResolver.ResolvedPipeCoating coating, SectionSurface surface) {
        return !coating.translucent() && surface.visibility() == PipeSurfaceModel.FaceVisibility.SINGLE_SIDED_OUTWARD;
    }

    private static double lodTextureTileUBlocks(int lod) {
        return switch (lod) {
            case 0 -> PIPE_TEXTURE_TILE_U_BLOCKS;
            case 1 -> PIPE_TEXTURE_TILE_U_BLOCKS * 2.0D;
            case 2 -> PIPE_TEXTURE_TILE_U_BLOCKS * 4.0D;
            default -> PIPE_TEXTURE_TILE_U_BLOCKS * 8.0D;
        };
    }

    private static double nextTileBoundary(double cursor, double end) {
        double next = Math.min(end, Math.floor(cursor) + 1.0D);
        if (next <= cursor + SURFACE_UV_EPSILON) {
            next = Math.min(end, cursor + 1.0D);
        }
        return next;
    }

    private static float tileFraction(double value, double tileBase) {
        float fraction = (float) Math.max(0.0D, Math.min(1.0D, value - tileBase));
        return SURFACE_TILE_UV_INSET + fraction * (1.0F - SURFACE_TILE_UV_INSET * 2.0F);
    }

    private static Vec3 surfacePoint(Vec3 p00, Vec3 p01, Vec3 p11, Vec3 p10, double u, double v) {
        return lerp(lerp(p00, p10, u), lerp(p01, p11, u), v);
    }

    private static Vec3 surfacePoint(Vec3 a, Vec3 b, double t) {
        return lerp(a, b, t);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t);
    }

    private static Vec3 quadNormal(Vec3 a, Vec3 b, Vec3 d) {
        Vec3 normal = b.subtract(a).cross(d.subtract(a));
        if (normal.lengthSqr() < 1.0E-8D) {
            return new Vec3(0.0D, 1.0D, 0.0D);
        }
        return normal.normalize();
    }

    private static int shadeTint(int color, Vec3 normal, boolean glow) {
        Vec3 light = new Vec3(-0.42D, 0.86D, 0.36D).normalize();
        double dot = Math.max(0.0D, safeNormalize(normal, new Vec3(0.0D, 1.0D, 0.0D)).dot(light));
        double factor = glow ? 0.98D + dot * 0.08D : 0.82D + dot * 0.18D;
        int alpha = color >>> 24 & 0xFF;
        int red = (int) Math.max(0, Math.min(255, ((color >>> 16) & 0xFF) * factor));
        int green = (int) Math.max(0, Math.min(255, ((color >>> 8) & 0xFF) * factor));
        int blue = (int) Math.max(0, Math.min(255, (color & 0xFF) * factor));
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static void addDecorativeCoatingBands(List<TexturedQuad> quads, Section previous, Section current, PipeSurfaceModel model, Map<String, PipeCoatingRenderResolver.ResolvedPipeCoating> coatings, boolean glow, int lod) {
        if (model.bands().isEmpty() || lod >= 3) {
            return;
        }
        for (PipeSurfaceModel.CoatingBand band : model.bands()) {
            PipeCoatingRenderResolver.ResolvedPipeCoating coating = coatings.get(band.slotId());
            if (coating == null) {
                continue;
            }
            double start = previous.distance();
            double end = current.distance();
            int first = (int) Math.floor((start - band.phase()) / band.period()) - 1;
            int last = (int) Math.ceil((end - band.phase()) / band.period()) + 1;
            for (int i = first; i <= last; i++) {
                double u0 = band.phase() + i * band.period();
                addCoatingBand(quads, previous, current, u0, u0 + band.length(), band.vCenter(), band.vWidth(), coating, glow, band.layer(), lod);
            }
        }
    }

    private static void addCoatingBand(List<TexturedQuad> quads, Section previous, Section current, double uStart, double uEnd, double vCenter, double vWidth, PipeCoatingRenderResolver.ResolvedPipeCoating coating, boolean glow, int layer, int lod) {
        double clippedUStart = Math.max(uStart, previous.distance());
        double clippedUEnd = Math.min(uEnd, current.distance());
        if (clippedUEnd <= clippedUStart + SURFACE_UV_EPSILON || vWidth <= SURFACE_UV_EPSILON) {
            return;
        }
        double start = vCenter - vWidth * 0.5D;
        double end = vCenter + vWidth * 0.5D;
        addCoatingRange(quads, previous, current, clippedUStart, clippedUEnd, start, end, coating, glow, layer, lod);
    }

    private static void addCoatingRange(List<TexturedQuad> quads, Section previous, Section current, double uStart, double uEnd, double vStart, double vEnd, PipeCoatingRenderResolver.ResolvedPipeCoating coating, boolean glow, int layer, int lod) {
        double segmentLength = current.distance() - previous.distance();
        if (segmentLength <= SURFACE_UV_EPSILON) {
            return;
        }
        int limit = Math.min(previous.surfaces().size(), current.surfaces().size());
        for (int i = 0; i < limit; i++) {
            SectionSurface previousSurface = previous.surfaces().get(i);
            SectionSurface currentSurface = current.surfaces().get(i);
            double overlapStart = Math.max(vStart, previousSurface.vStart());
            double overlapEnd = Math.min(vEnd, previousSurface.vEnd());
            if (overlapEnd <= overlapStart + SURFACE_UV_EPSILON) {
                continue;
            }
            double t0 = (overlapStart - previousSurface.vStart()) / (previousSurface.vEnd() - previousSurface.vStart());
            double t1 = (overlapEnd - previousSurface.vStart()) / (previousSurface.vEnd() - previousSurface.vStart());
            double uT0 = (uStart - previous.distance()) / segmentLength;
            double uT1 = (uEnd - previous.distance()) / segmentLength;
            Vec3 p00 = lerp(surfacePoint(previousSurface.a(), previousSurface.b(), t0), surfacePoint(currentSurface.a(), currentSurface.b(), t0), uT0);
            Vec3 p01 = lerp(surfacePoint(previousSurface.a(), previousSurface.b(), t1), surfacePoint(currentSurface.a(), currentSurface.b(), t1), uT0);
            Vec3 p11 = lerp(surfacePoint(previousSurface.a(), previousSurface.b(), t1), surfacePoint(currentSurface.a(), currentSurface.b(), t1), uT1);
            Vec3 p10 = lerp(surfacePoint(previousSurface.a(), previousSurface.b(), t0), surfacePoint(currentSurface.a(), currentSurface.b(), t0), uT1);
            Vec3 normal = markerSurfaceNormal(p00, p01, p10, previous.center(), current.center());
            Vec3 offset = normal.scale(MARKER_SURFACE_OFFSET + Math.max(0, layer) * MARKER_LAYER_OFFSET);
            int color = shadeTint(coating.opaqueTint(), normal, glow);
            addSurfaceMappedQuad(quads, p00.add(offset), p01.add(offset), p11.add(offset), p10.add(offset), uStart, uEnd, overlapStart, overlapEnd, color, glow, coating, shouldCullSurface(coating, previousSurface), lod);
        }
    }

    private static Section appearanceSection(PipeSurfaceModel model, Vec3 slideCenter, Vec3 tangent, double slideContactY, double distance, @Nullable Vec3 previousRight) {
        Vec3 forward = tangent.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : tangent.normalize();
        Vec3 right = transportedRight(forward, previousRight);
        Vec3 up = right.cross(forward).normalize();
        List<SectionSurface> surfaces = new ArrayList<>();
        for (PipeSurfaceModel.LocalSurface surface : model.surfaces()) {
            Vec3 a = localPoint(slideCenter, right, up, surface.ax(), surface.ay(), slideContactY);
            Vec3 b = localPoint(slideCenter, right, up, surface.bx(), surface.by(), slideContactY);
            surfaces.add(new SectionSurface(surface.slotId(), a, b, surface.vStart(), surface.vEnd(), surface.render(), surface.visibility()));
        }
        return new Section(slideCenter, List.copyOf(surfaces), model.perimeter(), right, up, forward, distance, slideContactY);
    }

    private static Vec3 localPoint(Vec3 slideCenter, Vec3 right, Vec3 up, double localX, double localY, double slideContactY) {
        return slideCenter.add(right.scale(localX)).add(up.scale(localY - slideContactY));
    }

    private static Vec3 transportedRight(Vec3 tangent, @Nullable Vec3 previousRight) {
        Vec3 forward = tangent.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : tangent.normalize();
        if (previousRight != null) {
            Vec3 projected = previousRight.subtract(forward.scale(previousRight.dot(forward)));
            if (projected.lengthSqr() > 1.0E-6D) {
                return projected.normalize();
            }
        }
        Vec3 side = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-6D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        return side.normalize();
    }

    private static Vec3 cachedTangent(RuntimePipeConnection runtime, int index) {
        Vec3 before = runtime.sample(Math.max(0, index - 1));
        Vec3 after = runtime.sample(Math.min(runtime.sampleCount() - 1, index + 1));
        Vec3 tangent = after.subtract(before);
        return tangent.lengthSqr() < 1.0E-6D ? runtime.connection().tangentForward() : tangent.normalize();
    }

    private static void addPreviewLines(List<LineSegment> lines, PipeConnection connection, int color) {
        double length = connection.length();
        int samples = sampleCount(length);
        Vec3 previous = null;
        for (int i = 0; i <= samples; i++) {
            Vec3 point = connection.positionAt(length * i / samples);
            if (previous != null) {
                lines.add(new LineSegment(previous, point, color, 3.0F));
            }
            previous = point;
        }
        addRing(lines, ring(connection.fromSurface(), connection.tangentAt(0.0D), PIPE_RADIUS * 1.5D), color, 2.0F);
        addRing(lines, ring(connection.toSurface(), connection.tangentAt(length), PIPE_RADIUS * 1.5D), color, 2.0F);
    }

    private static void addControlPathLines(List<LineSegment> lines, List<Vec3> controlPath, int color) {
        if (controlPath.isEmpty()) {
            return;
        }

        Vec3 previous = null;
        for (Vec3 point : controlPath) {
            addRing(lines, ring(point, new Vec3(0.0D, 1.0D, 0.0D), PIPE_RADIUS * 0.9D), color, 1.5F);
            if (previous != null) {
                lines.add(new LineSegment(previous, point, color, 1.0F));
            }
            previous = point;
        }
    }

    private static void addRing(List<LineSegment> lines, Vec3[] ring, int color, float width) {
        for (int i = 0; i < ring.length; i++) {
            lines.add(new LineSegment(ring[i], ring[(i + 1) % ring.length], color, width));
        }
    }

    private static Vec3[] ring(Vec3 center, Vec3 tangent, double radius) {
        Vec3 forward = tangent.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : tangent.normalize();
        Vec3 up = Math.abs(forward.dot(new Vec3(0.0D, 1.0D, 0.0D))) > 0.92D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = forward.cross(up).normalize();
        Vec3 normal = right.cross(forward).normalize();
        return new Vec3[] {
                center.add(normal.scale(radius)),
                center.add(right.scale(radius)),
                center.subtract(normal.scale(radius)),
                center.subtract(right.scale(radius))
        };
    }

    private static Vec3 safeNormalize(Vec3 vector, Vec3 fallback) {
        return vector.lengthSqr() < 1.0E-6D ? fallback : vector.normalize();
    }

    private static int sampleCount(double length) {
        return Math.max(6, Math.min(48, (int) Math.ceil(length * 1.5D)));
    }

    private static double pipeRenderRadius() {
        int renderDistanceChunks = Minecraft.getInstance().options.renderDistance().get();
        return Math.max(BLOCKS_PER_CHUNK, renderDistanceChunks * BLOCKS_PER_CHUNK);
    }

    @Nullable
    private static Preview buildPreview(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return null;
        }

        ItemStack stack = heldConnector(player);
        if (stack.isEmpty()) {
            return null;
        }
        PipeAnchorId start = stack.get(SPSDataComponents.SELECTED_ANCHOR.get());
        if (start == null || !start.levelKey().equals(level.dimension())) {
            return null;
        }
        if (!isConnectorAnchor(level.getBlockState(start.blockPos()))) {
            return null;
        }

        Target target = previewTarget(minecraft, level, start);
        if (target == null) {
            return null;
        }

        PipeAnchorId end = PipeAnchorId.of(level, target.pos());
        if (start.equals(end)) {
            return null;
        }
        PipeConnectorMode mode = PipeConnectorItem.mode(stack);
        if (mode == PipeConnectorMode.CONTROLLED && !target.existingAnchor()) {
            List<Vec3> controlPath = new ArrayList<>();
            controlPath.add(Vec3.atCenterOf(start.blockPos()));
            controlPath.addAll(stack.getOrDefault(SPSDataComponents.PENDING_CONTROL_POINTS.get(), List.of()));
            controlPath.add(target.controlPoint());
            return new Preview(null, Validity.WARNING, controlPath);
        }

        CurveSpec curveSpec = PipeConnectorItem.curveSpec(stack, player, start, end);
        PipeConnection rawConnection = PipeConnection.withCurve(start, end, curveSpec);
        PipeConnectionPlacementPlan placementPlan = PipeConnectionPlacementPlanner.plan(ClientPipeNetworkCache.currentView(), rawConnection, Config.MAX_CONNECTION_LENGTH.getAsDouble());
        PipeConnection connection = placementPlan.candidate();

        List<Vec3> controlPath = List.of();
        if (mode == PipeConnectorMode.CONTROLLED) {
            controlPath = new ArrayList<>();
            controlPath.add(connection.fromSurface());
            controlPath.addAll(stack.getOrDefault(SPSDataComponents.PENDING_CONTROL_POINTS.get(), List.of()));
            controlPath.add(connection.toSurface());
        }
        return new Preview(connection, validate(level, start, end, target, placementPlan), controlPath);
    }

    private static ItemStack heldConnector(LocalPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (PipeConnectorItem.isConnector(mainHand)) {
            return mainHand;
        }

        ItemStack offHand = player.getOffhandItem();
        return PipeConnectorItem.isConnector(offHand) ? offHand : ItemStack.EMPTY;
    }

    @Nullable
    private static PipeConnection buildPipeOperationTarget(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || heldPipeOperationTool(player).isEmpty()) {
            return null;
        }

        return PipeConnectionRaycast.find(
                ClientPipeNetworkCache.connectionsNear(level.dimension(), player.getEyePosition(), 10.0D),
                player.getEyePosition(),
                player.getLookAngle(),
                8.0D,
                0.55D).map(PipeConnectionRaycast.Hit::connection).orElse(null);
    }

    private static ItemStack heldPipeOperationTool(LocalPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (isPipeOperationTool(mainHand)) {
            return mainHand;
        }

        ItemStack offHand = player.getOffhandItem();
        return isPipeOperationTool(offHand) ? offHand : ItemStack.EMPTY;
    }

    private static boolean isPipeOperationTool(ItemStack stack) {
        return stack.getItem() instanceof PipeAttributeToolItem
                || stack.getItem() instanceof PipeAppearanceToolItem
                || stack.getItem() instanceof PipeRemoverItem
                || stack.getItem() instanceof PlatformClaimerItem;
    }

    @Nullable
    private static Target previewTarget(Minecraft minecraft, ClientLevel level, PipeAnchorId start) {
        HitResult hitResult = minecraft.hitResult;
        if (!(hitResult instanceof BlockHitResult blockHit) || hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos hitPos = blockHit.getBlockPos();
        BlockState hitState = level.getBlockState(hitPos);
        if (isConnectorAnchor(hitState)) {
            return hitPos.equals(start.blockPos()) ? null : new Target(hitPos, true, Vec3.atCenterOf(hitPos));
        }

        BlockPos ghostAnchor = hitPos.relative(blockHit.getDirection());
        return level.isEmptyBlock(ghostAnchor) ? new Target(ghostAnchor, false, blockHit.getLocation()) : null;
    }

    private static Validity validate(ClientLevel level, PipeAnchorId start, PipeAnchorId end, Target target, PipeConnectionPlacementPlan placementPlan) {
        if (!start.levelKey().equals(end.levelKey()) || start.equals(end)) {
            return Validity.INVALID;
        }
        BlockState startState = level.getBlockState(start.blockPos());
        boolean startBranch = startState.is(SPSBlocks.BRANCH_ANCHOR.get());
        boolean startFold = isFoldAnchor(startState);
        boolean startStandard = startState.is(SPSBlocks.PIPE_ANCHOR.get()) || startFold;
        BlockState endState = level.getBlockState(end.blockPos());
        boolean endBranch = target.existingAnchor() && level.getBlockState(end.blockPos()).is(SPSBlocks.BRANCH_ANCHOR.get());
        boolean endFold = target.existingAnchor() && isFoldAnchor(endState);
        boolean endStandard = target.existingAnchor() ? endState.is(SPSBlocks.PIPE_ANCHOR.get()) || endFold : true;
        if (!startStandard && !startBranch) {
            return Validity.INVALID;
        }
        if ((startBranch && ClientPipeNetworkCache.branchNodeAt(start).isEmpty())
                || (endBranch && ClientPipeNetworkCache.branchNodeAt(end).isEmpty())) {
            return Validity.INVALID;
        }
        if (startBranch && endBranch) {
            return Validity.INVALID;
        }
        if (ClientPipeNetworkCache.hasConnectionBetween(start, end)) {
            return Validity.INVALID;
        }
        if (startBranch && endFold || startFold && endBranch) {
            return Validity.INVALID;
        }
        if (!startBranch && ClientPipeNetworkCache.connectionCount(start) >= connectionLimit(startFold)) {
            return Validity.INVALID;
        }
        if (target.existingAnchor() && !endBranch && (!endStandard || ClientPipeNetworkCache.connectionCount(end) >= connectionLimit(endFold))) {
            return Validity.INVALID;
        }
        if (!target.existingAnchor() && !level.isEmptyBlock(end.blockPos())) {
            return Validity.INVALID;
        }
        double maxLength = Config.MAX_CONNECTION_LENGTH.getAsDouble();
        if (placementPlan.hasLengthViolations()) {
            return Validity.INVALID;
        }
        if (placementPlan.isNearLimit(maxLength, PREVIEW_LENGTH_WARNING_MARGIN)) {
            return Validity.WARNING;
        }
        return ClientPipeNetworkCache.connections(start.levelKey()).isEmpty() ? Validity.WARNING : Validity.VALID;
    }

    private static boolean isConnectorAnchor(BlockState state) {
        return state.is(SPSBlocks.PIPE_ANCHOR.get())
                || state.is(SPSBlocks.BRANCH_ANCHOR.get())
                || isFoldAnchor(state);
    }

    private static boolean isFoldAnchor(BlockState state) {
        return state.is(SPSBlocks.SPACE_FOLD_ANCHOR.get()) || state.is(SPSBlocks.DIMENSION_FOLD_ANCHOR.get());
    }

    private static int connectionLimit(boolean foldAnchor) {
        return foldAnchor ? 1 : 2;
    }

    private static TexturedQuad texturedQuad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float u1, float v0, float v1, int color, Vec3 normal, boolean generatedTexture, Identifier textureId, boolean translucent, boolean fullBright, boolean cullBackFace, int animationKind, double animationPhase) {
        return texturedQuad(a, b, c, d, u0, u1, v0, v1, color, normal, generatedTexture, textureId, translucent, fullBright, cullBackFace, true, animationKind, animationPhase);
    }

    private static TexturedQuad texturedQuad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float u1, float v0, float v1, int color, Vec3 normal, boolean generatedTexture, Identifier textureId, boolean translucent, boolean fullBright, boolean cullBackFace, boolean castsShadow, int animationKind, double animationPhase) {
        return texturedQuad(a, b, c, d, u0, u1, v0, v1, color, normal, generatedTexture, textureId, translucent, fullBright, false, cullBackFace, castsShadow, animationKind, animationPhase);
    }

    private static TexturedQuad emissiveTexturedQuad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float u1, float v0, float v1, int color, Vec3 normal, boolean generatedTexture, Identifier textureId, boolean translucent, boolean cullBackFace, int animationKind, double animationPhase) {
        return texturedQuad(a, b, c, d, u0, u1, v0, v1, color, normal, generatedTexture, textureId, translucent, true, true, cullBackFace, true, animationKind, animationPhase);
    }

    private static TexturedQuad texturedQuad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float u1, float v0, float v1, int color, Vec3 normal, boolean generatedTexture, Identifier textureId, boolean translucent, boolean fullBright, boolean emissive, boolean cullBackFace, boolean castsShadow, int animationKind, double animationPhase) {
        boolean safetyReduced = ClientSafetyOptions.reducePhotosensitivityRisk();
        return new TexturedQuad(
                a,
                b,
                c,
                d,
                u0,
                u1,
                v0,
                v1,
                color,
                normal,
                generatedTexture,
                textureId,
                translucent,
                fullBright && !safetyReduced,
                emissive && !safetyReduced,
                cullBackFace,
                castsShadow,
                animationKind,
                animationPhase,
                lightSampleKey(a, normal),
                lightSampleKey(b, normal),
                lightSampleKey(c, normal),
                lightSampleKey(d, normal));
    }

    private static long lightSampleKey(Vec3 point, Vec3 normal) {
        Vec3 sampleNormal = safeNormalize(normal, new Vec3(0.0D, 1.0D, 0.0D));
        Vec3 samplePoint = point.add(sampleNormal.scale(LIGHT_SAMPLE_NORMAL_OFFSET));
        return BlockPos.containing(samplePoint).asLong();
    }

    private static void addSectionKeys(AABB bounds, Set<RenderSectionKey> keys) {
        int minX = RenderSectionKey.sectionCoord(bounds.minX);
        int minY = RenderSectionKey.sectionCoord(bounds.minY);
        int minZ = RenderSectionKey.sectionCoord(bounds.minZ);
        int maxX = RenderSectionKey.sectionCoord(bounds.maxX);
        int maxY = RenderSectionKey.sectionCoord(bounds.maxY);
        int maxZ = RenderSectionKey.sectionCoord(bounds.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    keys.add(new RenderSectionKey(x, y, z));
                }
            }
        }
    }

    public interface PipeRenderExtension {
        PipeRenderExtension NONE = new PipeRenderExtension() {};

        default String renderStateKey() {
            return "base";
        }

        default void registerPipelines(RegisterRenderPipelinesEvent event) {}

        default void refreshPipelineMappings() {}

        default Scope entityPhase() {
            return NOOP_SCOPE;
        }

        default Scope entityBufferBuild() {
            return NOOP_SCOPE;
        }

        default boolean isRenderingShadowPass() {
            return false;
        }

        default void renderExternalShadowPass(Camera camera) {}

        default boolean isExternalPipelineActive() {
            return false;
        }

        @Nullable
        default Frustum shadowFrustum() {
            return null;
        }

        default double shadowRenderRadiusBlocks(double fallback) {
            return fallback;
        }

        default Vec3 shadowCameraPosition(Vec3 fallback) {
            return fallback;
        }

        default Scope shadowModelView() {
            return NOOP_SCOPE;
        }

        interface Scope extends AutoCloseable {
            @Override
            void close();
        }
    }

    private enum Validity {
        VALID,
        WARNING,
        INVALID
    }

    private record Target(BlockPos pos, boolean existingAnchor, Vec3 controlPoint) {}

    private record Preview(@Nullable PipeConnection connection, Validity validity, List<Vec3> controlPath) {}

    private record RenderData(PipeRenderFrame frame, List<LineSegment> lines, List<VisiblePipeSection> gpuSections, Vec3 camera) {
        boolean isEmpty() {
            return lines.isEmpty() && frame.isEmpty() && gpuSections.isEmpty();
        }
    }

    private record LineSegment(Vec3 from, Vec3 to, int color, float width) {}

    private record TexturedQuad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float u1, float v0, float v1, int color, Vec3 normal, boolean generatedTexture, Identifier textureId, boolean translucent, boolean fullBright, boolean emissive, boolean cullBackFace, boolean castsShadow, int animationKind, double animationPhase, long lightA, long lightB, long lightC, long lightD) {
        boolean persistentGpuEligible() {
            return this.animationKind == MARKER_ANIMATION_NONE;
        }
    }

    private record Section(Vec3 center, List<SectionSurface> surfaces, double perimeter, Vec3 right, Vec3 up, Vec3 tangent, double distance, double slideContactY) {}

    private record SectionSurface(String slotId, Vec3 a, Vec3 b, double vStart, double vEnd, boolean render, PipeSurfaceModel.FaceVisibility visibility) {}

    private record MeshCacheKey(UUID connectionId, int connectionKey, int connectionHash, PipeAppearanceProfile profile, int lod) {}

    private static final class FrameLightSampler {
        @Nullable
        private final ClientLevel level;
        private final Map<Long, LightSample> cache = new HashMap<>();

        private FrameLightSampler(@Nullable ClientLevel level) {
            this.level = level;
        }

        int lightAt(long blockPosKey, boolean fullBright) {
            return this.lightAt(blockPosKey, fullBright, null);
        }

        int lightAt(long blockPosKey, boolean fullBright, @Nullable LightBakeStats stats) {
            if (fullBright || this.level == null) {
                return FULL_BRIGHT_LIGHT;
            }
            LightSample sample = this.cache.computeIfAbsent(blockPosKey, key -> this.sampleLight(BlockPos.of(key)));
            if (stats != null) {
                stats.record(sample);
            }
            return sample.packedLight();
        }

        private LightSample sampleLight(BlockPos pos) {
            if (!this.level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))) {
                return new LightSample(this.missingChunkLight(), true);
            }
            return new LightSample(LevelRenderer.getLightCoords(this.level, pos), false);
        }

        private int missingChunkLight() {
            return LightCoordsUtil.pack(0, this.level.dimensionType().hasSkyLight() ? 15 : 0);
        }
    }

    private record LightSample(int packedLight, boolean provisional) {}

    private static final class LightBakeStats {
        private int sampled;
        private int dark;
        private int provisional;

        void record(LightSample sample) {
            this.sampled++;
            int packedLight = sample.packedLight();
            if (sample.provisional()) {
                this.provisional++;
            }
            if (packedLight != LightCoordsUtil.FULL_BRIGHT
                    && LightCoordsUtil.block(packedLight) == 0
                    && LightCoordsUtil.sky(packedLight) == 0) {
                this.dark++;
            }
        }

        boolean needsRetry() {
            return this.sampled >= 16 && (this.provisional > 0 || this.dark * 4 > this.sampled);
        }
    }

    private record RenderSectionKey(int x, int y, int z) {
        static RenderSectionKey containing(Vec3 point) {
            return new RenderSectionKey(sectionCoord(point.x), sectionCoord(point.y), sectionCoord(point.z));
        }

        static RenderSectionKey containing(BlockPos pos) {
            return new RenderSectionKey(sectionCoord(pos.getX()), sectionCoord(pos.getY()), sectionCoord(pos.getZ()));
        }

        private static int sectionCoord(double coordinate) {
            return (int) Math.floor(coordinate / BLOCKS_PER_CHUNK);
        }

        private static int sectionCoord(int coordinate) {
            return Math.floorDiv(coordinate, (int) BLOCKS_PER_CHUNK);
        }

        AABB bounds() {
            double minX = this.x * BLOCKS_PER_CHUNK;
            double minY = this.y * BLOCKS_PER_CHUNK;
            double minZ = this.z * BLOCKS_PER_CHUNK;
            return new AABB(minX, minY, minZ, minX + BLOCKS_PER_CHUNK, minY + BLOCKS_PER_CHUNK, minZ + BLOCKS_PER_CHUNK);
        }

        Vec3 origin() {
            return new Vec3(this.x * BLOCKS_PER_CHUNK, this.y * BLOCKS_PER_CHUNK, this.z * BLOCKS_PER_CHUNK);
        }
    }

    private static final class MeshAccumulator {
        private final RenderSectionKey sectionKey;
        private final List<TexturedQuad> quads = new ArrayList<>();
        @Nullable
        private AABB bounds;

        private MeshAccumulator(RenderSectionKey sectionKey) {
            this.sectionKey = sectionKey;
        }

        void add(AABB segmentBounds, Collection<TexturedQuad> segmentQuads) {
            if (segmentQuads.isEmpty()) {
                return;
            }
            this.bounds = this.bounds == null ? segmentBounds : union(this.bounds, segmentBounds);
            this.quads.addAll(segmentQuads);
        }

        RenderSectionKey sectionKey() {
            return this.sectionKey;
        }

        AABB bounds() {
            return this.bounds == null ? new AABB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D) : this.bounds;
        }

        List<TexturedQuad> quads() {
            return this.quads;
        }
    }

    private static final class PipeRenderFrame {
        private final PipeRenderBatches allBatches = new PipeRenderBatches();
        private final Map<RenderSectionKey, PipeRenderBatches> sectionBatches = new LinkedHashMap<>();

        void add(RenderSectionKey sectionKey, PipeRenderBatches batches) {
            if (batches.isEmpty()) {
                return;
            }
            this.allBatches.add(batches);
            this.sectionBatches.computeIfAbsent(sectionKey, ignored -> new PipeRenderBatches()).add(batches);
        }

        PipeRenderBatches visibleBatches(Set<RenderSectionKey> visibleSections) {
            if (visibleSections.isEmpty()) {
                return this.allBatches;
            }
            PipeRenderBatches visible = new PipeRenderBatches();
            for (RenderSectionKey key : visibleSections) {
                PipeRenderBatches section = this.sectionBatches.get(key);
                if (section != null) {
                    visible.add(section);
                }
            }
            return visible;
        }

        boolean isEmpty() {
            return this.allBatches.isEmpty();
        }
    }

    private static final class PipeRenderBatches {
        private final List<List<TexturedQuad>> atlasBatches = new ArrayList<>();
        private final List<List<TexturedQuad>> culledAtlasBatches = new ArrayList<>();
        private final List<List<TexturedQuad>> translucentAtlasBatches = new ArrayList<>();
        private final List<List<TexturedQuad>> emissiveAtlasBatches = new ArrayList<>();
        private final List<List<TexturedQuad>> emissiveCulledAtlasBatches = new ArrayList<>();
        private final List<List<TexturedQuad>> emissiveTranslucentAtlasBatches = new ArrayList<>();
        private final Map<Identifier, List<List<TexturedQuad>>> generatedBatches = new LinkedHashMap<>();
        private final Map<Identifier, List<List<TexturedQuad>>> culledGeneratedBatches = new LinkedHashMap<>();
        private final Map<Identifier, List<List<TexturedQuad>>> translucentGeneratedBatches = new LinkedHashMap<>();
        private final Map<Identifier, List<List<TexturedQuad>>> emissiveGeneratedBatches = new LinkedHashMap<>();
        private final Map<Identifier, List<List<TexturedQuad>>> emissiveCulledGeneratedBatches = new LinkedHashMap<>();
        private final Map<Identifier, List<List<TexturedQuad>>> emissiveTranslucentGeneratedBatches = new LinkedHashMap<>();

        void add(PipeRenderMesh mesh) {
            if (!mesh.dynamicAtlasQuads().isEmpty()) {
                this.atlasBatches.add(mesh.dynamicAtlasQuads());
            }
            if (!mesh.dynamicCulledAtlasQuads().isEmpty()) {
                this.culledAtlasBatches.add(mesh.dynamicCulledAtlasQuads());
            }
            if (!mesh.dynamicTranslucentAtlasQuads().isEmpty()) {
                this.translucentAtlasBatches.add(mesh.dynamicTranslucentAtlasQuads());
            }
            if (!mesh.dynamicEmissiveAtlasQuads().isEmpty()) {
                this.emissiveAtlasBatches.add(mesh.dynamicEmissiveAtlasQuads());
            }
            if (!mesh.dynamicEmissiveCulledAtlasQuads().isEmpty()) {
                this.emissiveCulledAtlasBatches.add(mesh.dynamicEmissiveCulledAtlasQuads());
            }
            if (!mesh.dynamicEmissiveTranslucentAtlasQuads().isEmpty()) {
                this.emissiveTranslucentAtlasBatches.add(mesh.dynamicEmissiveTranslucentAtlasQuads());
            }
            addGenerated(this.generatedBatches, mesh.dynamicGeneratedQuads());
            addGenerated(this.culledGeneratedBatches, mesh.dynamicCulledGeneratedQuads());
            addGenerated(this.translucentGeneratedBatches, mesh.dynamicTranslucentGeneratedQuads());
            addGenerated(this.emissiveGeneratedBatches, mesh.dynamicEmissiveGeneratedQuads());
            addGenerated(this.emissiveCulledGeneratedBatches, mesh.dynamicEmissiveCulledGeneratedQuads());
            addGenerated(this.emissiveTranslucentGeneratedBatches, mesh.dynamicEmissiveTranslucentGeneratedQuads());
        }

        void add(PipeRenderBatches other) {
            this.atlasBatches.addAll(other.atlasBatches);
            this.culledAtlasBatches.addAll(other.culledAtlasBatches);
            this.translucentAtlasBatches.addAll(other.translucentAtlasBatches);
            this.emissiveAtlasBatches.addAll(other.emissiveAtlasBatches);
            this.emissiveCulledAtlasBatches.addAll(other.emissiveCulledAtlasBatches);
            this.emissiveTranslucentAtlasBatches.addAll(other.emissiveTranslucentAtlasBatches);
            addGeneratedBatches(this.generatedBatches, other.generatedBatches);
            addGeneratedBatches(this.culledGeneratedBatches, other.culledGeneratedBatches);
            addGeneratedBatches(this.translucentGeneratedBatches, other.translucentGeneratedBatches);
            addGeneratedBatches(this.emissiveGeneratedBatches, other.emissiveGeneratedBatches);
            addGeneratedBatches(this.emissiveCulledGeneratedBatches, other.emissiveCulledGeneratedBatches);
            addGeneratedBatches(this.emissiveTranslucentGeneratedBatches, other.emissiveTranslucentGeneratedBatches);
        }

        private static void addGenerated(Map<Identifier, List<List<TexturedQuad>>> target, Map<Identifier, List<TexturedQuad>> source) {
            for (Map.Entry<Identifier, List<TexturedQuad>> entry : source.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    target.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(entry.getValue());
                }
            }
        }

        private static void addGeneratedBatches(Map<Identifier, List<List<TexturedQuad>>> target, Map<Identifier, List<List<TexturedQuad>>> source) {
            for (Map.Entry<Identifier, List<List<TexturedQuad>>> entry : source.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    target.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).addAll(entry.getValue());
                }
            }
        }

        boolean isEmpty() {
            return this.atlasBatches.isEmpty()
                    && this.culledAtlasBatches.isEmpty()
                    && this.translucentAtlasBatches.isEmpty()
                    && this.emissiveAtlasBatches.isEmpty()
                    && this.emissiveCulledAtlasBatches.isEmpty()
                    && this.emissiveTranslucentAtlasBatches.isEmpty()
                    && this.generatedBatches.isEmpty()
                    && this.culledGeneratedBatches.isEmpty()
                    && this.translucentGeneratedBatches.isEmpty()
                    && this.emissiveGeneratedBatches.isEmpty()
                    && this.emissiveCulledGeneratedBatches.isEmpty()
                    && this.emissiveTranslucentGeneratedBatches.isEmpty();
        }

        List<List<TexturedQuad>> atlasBatches() {
            return this.atlasBatches;
        }

        List<List<TexturedQuad>> culledAtlasBatches() {
            return this.culledAtlasBatches;
        }

        List<List<TexturedQuad>> translucentAtlasBatches() {
            return this.translucentAtlasBatches;
        }

        List<List<TexturedQuad>> emissiveAtlasBatches() {
            return this.emissiveAtlasBatches;
        }

        List<List<TexturedQuad>> emissiveCulledAtlasBatches() {
            return this.emissiveCulledAtlasBatches;
        }

        List<List<TexturedQuad>> emissiveTranslucentAtlasBatches() {
            return this.emissiveTranslucentAtlasBatches;
        }

        Map<Identifier, List<List<TexturedQuad>>> generatedBatches() {
            return this.generatedBatches;
        }

        Map<Identifier, List<List<TexturedQuad>>> culledGeneratedBatches() {
            return this.culledGeneratedBatches;
        }

        Map<Identifier, List<List<TexturedQuad>>> translucentGeneratedBatches() {
            return this.translucentGeneratedBatches;
        }

        Map<Identifier, List<List<TexturedQuad>>> emissiveGeneratedBatches() {
            return this.emissiveGeneratedBatches;
        }

        Map<Identifier, List<List<TexturedQuad>>> emissiveCulledGeneratedBatches() {
            return this.emissiveCulledGeneratedBatches;
        }

        Map<Identifier, List<List<TexturedQuad>>> emissiveTranslucentGeneratedBatches() {
            return this.emissiveTranslucentGeneratedBatches;
        }
    }

    private record PipeRenderMesh(
            RenderSectionKey sectionKey,
            AABB bounds,
            List<TexturedQuad> dynamicAtlasQuads,
            List<TexturedQuad> dynamicCulledAtlasQuads,
            List<TexturedQuad> dynamicTranslucentAtlasQuads,
            List<TexturedQuad> dynamicEmissiveAtlasQuads,
            List<TexturedQuad> dynamicEmissiveCulledAtlasQuads,
            List<TexturedQuad> dynamicEmissiveTranslucentAtlasQuads,
            Map<Identifier, List<TexturedQuad>> dynamicGeneratedQuads,
            Map<Identifier, List<TexturedQuad>> dynamicCulledGeneratedQuads,
            Map<Identifier, List<TexturedQuad>> dynamicTranslucentGeneratedQuads,
            Map<Identifier, List<TexturedQuad>> dynamicEmissiveGeneratedQuads,
            Map<Identifier, List<TexturedQuad>> dynamicEmissiveCulledGeneratedQuads,
            Map<Identifier, List<TexturedQuad>> dynamicEmissiveTranslucentGeneratedQuads,
            PipeStaticQuadBatches staticBatches) {
        static PipeRenderMesh from(RenderSectionKey sectionKey, AABB bounds, List<TexturedQuad> quads) {
            List<TexturedQuad> atlasQuads = new ArrayList<>();
            List<TexturedQuad> culledAtlasQuads = new ArrayList<>();
            List<TexturedQuad> translucentAtlasQuads = new ArrayList<>();
            List<TexturedQuad> emissiveAtlasQuads = new ArrayList<>();
            List<TexturedQuad> emissiveCulledAtlasQuads = new ArrayList<>();
            List<TexturedQuad> emissiveTranslucentAtlasQuads = new ArrayList<>();
            Map<Identifier, List<TexturedQuad>> generatedQuads = new LinkedHashMap<>();
            Map<Identifier, List<TexturedQuad>> culledGeneratedQuads = new LinkedHashMap<>();
            Map<Identifier, List<TexturedQuad>> translucentGeneratedQuads = new LinkedHashMap<>();
            Map<Identifier, List<TexturedQuad>> emissiveGeneratedQuads = new LinkedHashMap<>();
            Map<Identifier, List<TexturedQuad>> emissiveCulledGeneratedQuads = new LinkedHashMap<>();
            Map<Identifier, List<TexturedQuad>> emissiveTranslucentGeneratedQuads = new LinkedHashMap<>();
            PipeStaticQuadBatches staticBatches = new PipeStaticQuadBatches();
            for (TexturedQuad quad : quads) {
                if (quad.persistentGpuEligible()) {
                    staticBatches.add(quad);
                    continue;
                }
                if (quad.emissive() && quad.translucent()) {
                    if (quad.generatedTexture()) {
                        emissiveTranslucentGeneratedQuads.computeIfAbsent(quad.textureId(), ignored -> new ArrayList<>()).add(quad);
                    } else {
                        emissiveTranslucentAtlasQuads.add(quad);
                    }
                } else if (quad.emissive()) {
                    if (quad.generatedTexture()) {
                        if (quad.cullBackFace()) {
                            emissiveCulledGeneratedQuads.computeIfAbsent(quad.textureId(), ignored -> new ArrayList<>()).add(quad);
                        } else {
                            emissiveGeneratedQuads.computeIfAbsent(quad.textureId(), ignored -> new ArrayList<>()).add(quad);
                        }
                    } else if (quad.cullBackFace()) {
                        emissiveCulledAtlasQuads.add(quad);
                    } else {
                        emissiveAtlasQuads.add(quad);
                    }
                } else if (quad.translucent()) {
                    if (quad.generatedTexture()) {
                        translucentGeneratedQuads.computeIfAbsent(quad.textureId(), ignored -> new ArrayList<>()).add(quad);
                    } else {
                        translucentAtlasQuads.add(quad);
                    }
                } else if (quad.generatedTexture()) {
                    if (quad.cullBackFace()) {
                        culledGeneratedQuads.computeIfAbsent(quad.textureId(), ignored -> new ArrayList<>()).add(quad);
                    } else {
                        generatedQuads.computeIfAbsent(quad.textureId(), ignored -> new ArrayList<>()).add(quad);
                    }
                } else if (quad.cullBackFace()) {
                    culledAtlasQuads.add(quad);
                } else {
                    atlasQuads.add(quad);
                }
            }
            return new PipeRenderMesh(
                    sectionKey,
                    bounds,
                    List.copyOf(atlasQuads),
                    List.copyOf(culledAtlasQuads),
                    List.copyOf(translucentAtlasQuads),
                    List.copyOf(emissiveAtlasQuads),
                    List.copyOf(emissiveCulledAtlasQuads),
                    List.copyOf(emissiveTranslucentAtlasQuads),
                    freezeQuadMap(generatedQuads),
                    freezeQuadMap(culledGeneratedQuads),
                    freezeQuadMap(translucentGeneratedQuads),
                    freezeQuadMap(emissiveGeneratedQuads),
                    freezeQuadMap(emissiveCulledGeneratedQuads),
                    freezeQuadMap(emissiveTranslucentGeneratedQuads),
                    staticBatches.freeze());
        }

        private static Map<Identifier, List<TexturedQuad>> freezeQuadMap(Map<Identifier, List<TexturedQuad>> source) {
            Map<Identifier, List<TexturedQuad>> frozen = new LinkedHashMap<>();
            for (Map.Entry<Identifier, List<TexturedQuad>> entry : source.entrySet()) {
                frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            return Map.copyOf(frozen);
        }

        boolean isEmpty() {
            return this.dynamicAtlasQuads.isEmpty()
                    && this.dynamicCulledAtlasQuads.isEmpty()
                    && this.dynamicTranslucentAtlasQuads.isEmpty()
                    && this.dynamicEmissiveAtlasQuads.isEmpty()
                    && this.dynamicEmissiveCulledAtlasQuads.isEmpty()
                    && this.dynamicEmissiveTranslucentAtlasQuads.isEmpty()
                    && this.dynamicGeneratedQuads.isEmpty()
                    && this.dynamicCulledGeneratedQuads.isEmpty()
                    && this.dynamicTranslucentGeneratedQuads.isEmpty()
                    && this.dynamicEmissiveGeneratedQuads.isEmpty()
                    && this.dynamicEmissiveCulledGeneratedQuads.isEmpty()
                    && this.dynamicEmissiveTranslucentGeneratedQuads.isEmpty()
                    && this.staticBatches.isEmpty();
        }
    }

    private record VisiblePipeSection(PipeSectionState section, int lod) {}

    private record PipeSectionConnectionEntry(RuntimePipeConnection runtime, PipeAppearanceProfile profile, Set<RenderSectionKey> sectionKeys) {
        private PipeSectionConnectionEntry {
            profile = profile.normalizedToDefinitions();
            sectionKeys = Set.copyOf(sectionKeys);
        }

        UUID connectionId() {
            return this.runtime.connection().id();
        }

        int connectionKey() {
            return this.runtime.connection().connectionKey();
        }

        int connectionHash() {
            return this.runtime.connection().hashCode();
        }
    }

    private static final class PipeSectionState {
        private final RenderSectionKey sectionKey;
        private final Set<UUID> connectionIds = new LinkedHashSet<>();
        private final PipeSectionLodState[] lodStates = new PipeSectionLodState[SECTION_LOD_COUNT];

        private PipeSectionState(RenderSectionKey sectionKey) {
            this.sectionKey = sectionKey;
            for (int i = 0; i < this.lodStates.length; i++) {
                this.lodStates[i] = new PipeSectionLodState(sectionKey);
            }
        }

        void addConnection(UUID connectionId) {
            if (this.connectionIds.add(connectionId)) {
                this.invalidate();
            }
        }

        void removeConnection(UUID connectionId) {
            if (this.connectionIds.remove(connectionId)) {
                this.invalidate();
            }
        }

        RenderSectionKey sectionKey() {
            return this.sectionKey;
        }

        AABB bounds() {
            return this.sectionKey.bounds().inflate(FRUSTUM_BOUNDS_INFLATE);
        }

        PipeSectionLodState ensureLod(int lod) {
            int index = Math.max(0, Math.min(SECTION_LOD_COUNT - 1, lod));
            PipeSectionLodState lodState = this.lodStates[index];
            lodState.ensureBuilt(this.connectionIds, index);
            return lodState;
        }

        PipeSectionLodState lod(int lod) {
            return this.lodStates[Math.max(0, Math.min(SECTION_LOD_COUNT - 1, lod))];
        }

        boolean isEmpty() {
            return this.connectionIds.isEmpty();
        }

        private void invalidate() {
            for (PipeSectionLodState lodState : this.lodStates) {
                lodState.invalidate();
            }
        }

        void markLightDirty() {
            for (PipeSectionLodState lodState : this.lodStates) {
                lodState.markLightDirty();
            }
        }

        void markGpuDirty() {
            for (PipeSectionLodState lodState : this.lodStates) {
                lodState.markGpuDirty();
            }
        }

        void release() {
            for (PipeSectionLodState lodState : this.lodStates) {
                lodState.release();
            }
        }
    }

    private static final class PipeSectionLodState {
        private final RenderSectionKey sectionKey;
        private PipeRenderBatches dynamicBatches = new PipeRenderBatches();
        private PipeStaticQuadBatches staticBatches = new PipeStaticQuadBatches();
        private PipeGpuBatches gpuBatches = PipeGpuBatches.EMPTY;
        private boolean built;
        private boolean hasStaticGeometry;
        private boolean gpuDirty = true;
        private int lightRetryFrames;
        private PipeGpuRenderProfile gpuProfile = PipeGpuRenderProfile.uninitialized();

        private PipeSectionLodState(RenderSectionKey sectionKey) {
            this.sectionKey = sectionKey;
        }

        void ensureBuilt(Collection<UUID> connectionIds, int lod) {
            if (this.built) {
                return;
            }
            this.dynamicBatches = new PipeRenderBatches();
            this.staticBatches = new PipeStaticQuadBatches();
            for (UUID connectionId : connectionIds) {
                PipeSectionConnectionEntry entry = SECTION_CONNECTION_INDEX.get(connectionId);
                if (entry == null) {
                    continue;
                }
                for (PipeRenderMesh mesh : cachedAppearanceMeshes(entry.runtime(), entry.profile(), lod)) {
                    if (mesh.sectionKey().equals(this.sectionKey)) {
                        this.dynamicBatches.add(mesh);
                        this.staticBatches.add(mesh.staticBatches());
                    }
                }
            }
            this.hasStaticGeometry = !this.staticBatches.isEmpty();
            this.built = true;
            this.gpuDirty = true;
        }

        PipeRenderBatches dynamicBatches() {
            return this.dynamicBatches;
        }

        PipeGpuBatches gpuBatches() {
            return this.gpuBatches;
        }

        void ensureGpuUploaded(FrameLightSampler lightSampler) {
            if (!this.built) {
                return;
            }
            PipeGpuRenderProfile currentProfile = PipeGpuRenderProfile.current();
            if (!currentProfile.equals(this.gpuProfile)) {
                this.release();
                this.gpuProfile = currentProfile;
                this.gpuDirty = true;
            }
            if (!this.gpuDirty) {
                return;
            }
            PipeGpuBatches replacement = PipeGpuBatches.upload(this.sectionKey, this.staticBatches, lightSampler, currentProfile);
            this.gpuBatches.release();
            this.gpuBatches = replacement;
            if (replacement.needsLightRetry() && this.lightRetryFrames < GPU_LIGHT_RETRY_FRAMES) {
                this.lightRetryFrames++;
                this.gpuDirty = true;
            } else {
                this.gpuDirty = false;
            }
        }

        boolean isEmpty() {
            return this.dynamicBatches.isEmpty() && !this.hasStaticGeometry;
        }

        void invalidate() {
            this.dynamicBatches = new PipeRenderBatches();
            this.staticBatches = new PipeStaticQuadBatches();
            this.built = false;
            this.hasStaticGeometry = false;
            this.lightRetryFrames = 0;
            this.release();
        }

        void markLightDirty() {
            if (!this.built && !this.hasStaticGeometry && this.gpuBatches == PipeGpuBatches.EMPTY) {
                return;
            }
            this.invalidate();
        }

        void markGpuDirty() {
            if (!this.built && this.gpuBatches == PipeGpuBatches.EMPTY) {
                return;
            }
            this.release();
        }

        void release() {
            this.gpuBatches.release();
            this.gpuBatches = PipeGpuBatches.EMPTY;
            this.gpuDirty = true;
            this.gpuProfile = PipeGpuRenderProfile.uninitialized();
        }
    }

    private static RenderType pipeCutout(Identifier texture) {
        return RenderType.create(
                "superpipeslide_pipe_cutout",
                RenderSetup.builder(PIPE_ENTITY_CUTOUT_PIPELINE)
                        .withTexture("Sampler0", texture)
                        .useLightmap()
                        .useOverlay()
                        .bufferSize(RenderType.SMALL_BUFFER_SIZE)
                        .createRenderSetup());
    }

    private static RenderType pipeCutoutEmissive(Identifier texture) {
        return RenderType.create(
                "superpipeslide_pipe_cutout_emissive",
                RenderSetup.builder(PIPE_ENTITY_CUTOUT_EMISSIVE_PIPELINE)
                        .withTexture("Sampler0", texture)
                        .useOverlay()
                        .bufferSize(RenderType.SMALL_BUFFER_SIZE)
                        .createRenderSetup());
    }

    private static RenderType pipeCutoutCull(Identifier texture) {
        return RenderType.create(
                "superpipeslide_pipe_cutout_cull",
                RenderSetup.builder(PIPE_ENTITY_CUTOUT_CULL_PIPELINE)
                        .withTexture("Sampler0", texture)
                        .useLightmap()
                        .useOverlay()
                        .bufferSize(RenderType.SMALL_BUFFER_SIZE)
                        .createRenderSetup());
    }

    private static RenderType pipeCutoutCullEmissive(Identifier texture) {
        return RenderType.create(
                "superpipeslide_pipe_cutout_cull_emissive",
                RenderSetup.builder(PIPE_ENTITY_CUTOUT_CULL_EMISSIVE_PIPELINE)
                        .withTexture("Sampler0", texture)
                        .useOverlay()
                        .bufferSize(RenderType.SMALL_BUFFER_SIZE)
                        .createRenderSetup());
    }

    private static RenderType pipeTranslucent(Identifier texture) {
        return RenderType.create(
                "superpipeslide_pipe_translucent",
                RenderSetup.builder(PIPE_ENTITY_TRANSLUCENT_PIPELINE)
                        .withTexture("Sampler0", texture)
                        .useLightmap()
                        .useOverlay()
                        .sortOnUpload()
                        .bufferSize(RenderType.SMALL_BUFFER_SIZE)
                        .createRenderSetup());
    }

    private static RenderType pipeTranslucentEmissive(Identifier texture) {
        return RenderType.create(
                "superpipeslide_pipe_translucent_emissive",
                RenderSetup.builder(PIPE_ENTITY_TRANSLUCENT_EMISSIVE_PIPELINE)
                        .withTexture("Sampler0", texture)
                        .useOverlay()
                        .sortOnUpload()
                        .bufferSize(RenderType.SMALL_BUFFER_SIZE)
                        .createRenderSetup());
    }

    private static RenderType generatedPipeCutout(Identifier texture) {
        return PIPE_GENERATED_CUTOUT.computeIfAbsent(texture, ClientPipeRenderer::pipeCutout);
    }

    private static RenderType generatedPipeCutoutEmissive(Identifier texture) {
        return PIPE_GENERATED_CUTOUT_EMISSIVE.computeIfAbsent(texture, ClientPipeRenderer::pipeCutoutEmissive);
    }

    private static RenderType generatedPipeCutoutCull(Identifier texture) {
        return PIPE_GENERATED_CUTOUT_CULL.computeIfAbsent(texture, ClientPipeRenderer::pipeCutoutCull);
    }

    private static RenderType generatedPipeCutoutCullEmissive(Identifier texture) {
        return PIPE_GENERATED_CUTOUT_CULL_EMISSIVE.computeIfAbsent(texture, ClientPipeRenderer::pipeCutoutCullEmissive);
    }

    private static RenderType generatedPipeTranslucent(Identifier texture) {
        return PIPE_GENERATED_TRANSLUCENT.computeIfAbsent(texture, ClientPipeRenderer::pipeTranslucent);
    }

    private static RenderType generatedPipeTranslucentEmissive(Identifier texture) {
        return PIPE_GENERATED_TRANSLUCENT_EMISSIVE.computeIfAbsent(texture, ClientPipeRenderer::pipeTranslucentEmissive);
    }

    private static final class PipeStaticQuadBatches {
        private final List<TexturedQuad> atlasQuads = new ArrayList<>();
        private final List<TexturedQuad> culledAtlasQuads = new ArrayList<>();
        private final List<TexturedQuad> translucentAtlasQuads = new ArrayList<>();
        private final List<TexturedQuad> emissiveAtlasQuads = new ArrayList<>();
        private final List<TexturedQuad> emissiveCulledAtlasQuads = new ArrayList<>();
        private final List<TexturedQuad> emissiveTranslucentAtlasQuads = new ArrayList<>();
        private final Map<Identifier, List<TexturedQuad>> generatedQuads = new LinkedHashMap<>();
        private final Map<Identifier, List<TexturedQuad>> culledGeneratedQuads = new LinkedHashMap<>();
        private final Map<Identifier, List<TexturedQuad>> translucentGeneratedQuads = new LinkedHashMap<>();
        private final Map<Identifier, List<TexturedQuad>> emissiveGeneratedQuads = new LinkedHashMap<>();
        private final Map<Identifier, List<TexturedQuad>> emissiveCulledGeneratedQuads = new LinkedHashMap<>();
        private final Map<Identifier, List<TexturedQuad>> emissiveTranslucentGeneratedQuads = new LinkedHashMap<>();

        void add(TexturedQuad quad) {
            if (quad.emissive() && quad.translucent()) {
                if (quad.generatedTexture()) {
                    this.emissiveTranslucentGeneratedQuads.computeIfAbsent(quad.textureId(), ignored -> new ArrayList<>()).add(quad);
                } else {
                    this.emissiveTranslucentAtlasQuads.add(quad);
                }
            } else if (quad.emissive()) {
                if (quad.generatedTexture()) {
                    if (quad.cullBackFace()) {
                        this.emissiveCulledGeneratedQuads.computeIfAbsent(quad.textureId(), ignored -> new ArrayList<>()).add(quad);
                    } else {
                        this.emissiveGeneratedQuads.computeIfAbsent(quad.textureId(), ignored -> new ArrayList<>()).add(quad);
                    }
                } else if (quad.cullBackFace()) {
                    this.emissiveCulledAtlasQuads.add(quad);
                } else {
                    this.emissiveAtlasQuads.add(quad);
                }
            } else if (quad.translucent()) {
                if (quad.generatedTexture()) {
                    this.translucentGeneratedQuads.computeIfAbsent(quad.textureId(), ignored -> new ArrayList<>()).add(quad);
                } else {
                    this.translucentAtlasQuads.add(quad);
                }
            } else if (quad.generatedTexture()) {
                if (quad.cullBackFace()) {
                    this.culledGeneratedQuads.computeIfAbsent(quad.textureId(), ignored -> new ArrayList<>()).add(quad);
                } else {
                    this.generatedQuads.computeIfAbsent(quad.textureId(), ignored -> new ArrayList<>()).add(quad);
                }
            } else if (quad.cullBackFace()) {
                this.culledAtlasQuads.add(quad);
            } else {
                this.atlasQuads.add(quad);
            }
        }

        void add(PipeStaticQuadBatches other) {
            this.atlasQuads.addAll(other.atlasQuads);
            this.culledAtlasQuads.addAll(other.culledAtlasQuads);
            this.translucentAtlasQuads.addAll(other.translucentAtlasQuads);
            this.emissiveAtlasQuads.addAll(other.emissiveAtlasQuads);
            this.emissiveCulledAtlasQuads.addAll(other.emissiveCulledAtlasQuads);
            this.emissiveTranslucentAtlasQuads.addAll(other.emissiveTranslucentAtlasQuads);
            addQuadMap(this.generatedQuads, other.generatedQuads);
            addQuadMap(this.culledGeneratedQuads, other.culledGeneratedQuads);
            addQuadMap(this.translucentGeneratedQuads, other.translucentGeneratedQuads);
            addQuadMap(this.emissiveGeneratedQuads, other.emissiveGeneratedQuads);
            addQuadMap(this.emissiveCulledGeneratedQuads, other.emissiveCulledGeneratedQuads);
            addQuadMap(this.emissiveTranslucentGeneratedQuads, other.emissiveTranslucentGeneratedQuads);
        }

        private static void addQuadMap(Map<Identifier, List<TexturedQuad>> target, Map<Identifier, List<TexturedQuad>> source) {
            for (Map.Entry<Identifier, List<TexturedQuad>> entry : source.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    target.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).addAll(entry.getValue());
                }
            }
        }

        PipeStaticQuadBatches freeze() {
            PipeStaticQuadBatches frozen = new PipeStaticQuadBatches();
            frozen.atlasQuads.addAll(List.copyOf(this.atlasQuads));
            frozen.culledAtlasQuads.addAll(List.copyOf(this.culledAtlasQuads));
            frozen.translucentAtlasQuads.addAll(List.copyOf(this.translucentAtlasQuads));
            frozen.emissiveAtlasQuads.addAll(List.copyOf(this.emissiveAtlasQuads));
            frozen.emissiveCulledAtlasQuads.addAll(List.copyOf(this.emissiveCulledAtlasQuads));
            frozen.emissiveTranslucentAtlasQuads.addAll(List.copyOf(this.emissiveTranslucentAtlasQuads));
            copyFrozen(this.generatedQuads, frozen.generatedQuads);
            copyFrozen(this.culledGeneratedQuads, frozen.culledGeneratedQuads);
            copyFrozen(this.translucentGeneratedQuads, frozen.translucentGeneratedQuads);
            copyFrozen(this.emissiveGeneratedQuads, frozen.emissiveGeneratedQuads);
            copyFrozen(this.emissiveCulledGeneratedQuads, frozen.emissiveCulledGeneratedQuads);
            copyFrozen(this.emissiveTranslucentGeneratedQuads, frozen.emissiveTranslucentGeneratedQuads);
            return frozen;
        }

        private static void copyFrozen(Map<Identifier, List<TexturedQuad>> source, Map<Identifier, List<TexturedQuad>> target) {
            for (Map.Entry<Identifier, List<TexturedQuad>> entry : source.entrySet()) {
                target.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }

        boolean isEmpty() {
            return this.atlasQuads.isEmpty()
                    && this.culledAtlasQuads.isEmpty()
                    && this.translucentAtlasQuads.isEmpty()
                    && this.emissiveAtlasQuads.isEmpty()
                    && this.emissiveCulledAtlasQuads.isEmpty()
                    && this.emissiveTranslucentAtlasQuads.isEmpty()
                    && this.generatedQuads.isEmpty()
                    && this.culledGeneratedQuads.isEmpty()
                    && this.translucentGeneratedQuads.isEmpty()
                    && this.emissiveGeneratedQuads.isEmpty()
                    && this.emissiveCulledGeneratedQuads.isEmpty()
                    && this.emissiveTranslucentGeneratedQuads.isEmpty();
        }
    }

    private record PipeGpuRenderProfile(String stateKey) {
        static PipeGpuRenderProfile current() {
            return new PipeGpuRenderProfile(renderExtension.renderStateKey());
        }

        static PipeGpuRenderProfile uninitialized() {
            return new PipeGpuRenderProfile("uninitialized");
        }
    }

    private static final class PipeGpuBatches {
        private static final PipeGpuBatches EMPTY = new PipeGpuBatches(List.of(), List.of());
        private final List<PipeGpuBatch> opaque;
        private final List<PipeGpuBatch> translucent;

        private PipeGpuBatches(List<PipeGpuBatch> opaque, List<PipeGpuBatch> translucent) {
            this.opaque = opaque;
            this.translucent = translucent;
        }

        static PipeGpuBatches upload(RenderSectionKey sectionKey, PipeStaticQuadBatches source, FrameLightSampler lightSampler, PipeGpuRenderProfile profile) {
            if (source.isEmpty()) {
                return EMPTY;
            }
            List<PipeGpuBatch> opaque = new ArrayList<>();
            List<PipeGpuBatch> translucent = new ArrayList<>();
            addUploaded(opaque, PIPE_ATLAS_CUTOUT, source.atlasQuads, sectionKey, lightSampler);
            addUploaded(opaque, PIPE_ATLAS_CUTOUT_CULL, source.culledAtlasQuads, sectionKey, lightSampler);
            addUploaded(opaque, PIPE_ATLAS_CUTOUT_EMISSIVE, source.emissiveAtlasQuads, sectionKey, lightSampler);
            addUploaded(opaque, PIPE_ATLAS_CUTOUT_CULL_EMISSIVE, source.emissiveCulledAtlasQuads, sectionKey, lightSampler);
            for (Map.Entry<Identifier, List<TexturedQuad>> entry : source.generatedQuads.entrySet()) {
                addUploaded(opaque, generatedPipeCutout(entry.getKey()), entry.getValue(), sectionKey, lightSampler);
            }
            for (Map.Entry<Identifier, List<TexturedQuad>> entry : source.culledGeneratedQuads.entrySet()) {
                addUploaded(opaque, generatedPipeCutoutCull(entry.getKey()), entry.getValue(), sectionKey, lightSampler);
            }
            for (Map.Entry<Identifier, List<TexturedQuad>> entry : source.emissiveGeneratedQuads.entrySet()) {
                addUploaded(opaque, generatedPipeCutoutEmissive(entry.getKey()), entry.getValue(), sectionKey, lightSampler);
            }
            for (Map.Entry<Identifier, List<TexturedQuad>> entry : source.emissiveCulledGeneratedQuads.entrySet()) {
                addUploaded(opaque, generatedPipeCutoutCullEmissive(entry.getKey()), entry.getValue(), sectionKey, lightSampler);
            }
            addUploaded(translucent, PIPE_ATLAS_TRANSLUCENT, source.translucentAtlasQuads, sectionKey, lightSampler);
            for (Map.Entry<Identifier, List<TexturedQuad>> entry : source.translucentGeneratedQuads.entrySet()) {
                addUploaded(translucent, generatedPipeTranslucent(entry.getKey()), entry.getValue(), sectionKey, lightSampler);
            }
            addUploaded(translucent, PIPE_ATLAS_TRANSLUCENT_EMISSIVE, source.emissiveTranslucentAtlasQuads, sectionKey, lightSampler);
            for (Map.Entry<Identifier, List<TexturedQuad>> entry : source.emissiveTranslucentGeneratedQuads.entrySet()) {
                addUploaded(translucent, generatedPipeTranslucentEmissive(entry.getKey()), entry.getValue(), sectionKey, lightSampler);
            }
            return new PipeGpuBatches(List.copyOf(opaque), List.copyOf(translucent));
        }

        private static void addUploaded(List<PipeGpuBatch> target, RenderType renderType, List<TexturedQuad> quads, RenderSectionKey sectionKey, FrameLightSampler lightSampler) {
            PipeGpuBatch batch = PipeGpuBatch.upload(renderType, quads, sectionKey, lightSampler);
            if (batch != null) {
                target.add(batch);
            }
        }

        DrawStats draw(boolean translucentPass, Vec3 camera) {
            int batchesDrawn = 0;
            int indicesDrawn = 0;
            List<PipeGpuBatch> batches = translucentPass ? this.translucent : this.opaque;
            for (PipeGpuBatch batch : batches) {
                int indices = batch.draw(camera);
                if (indices > 0) {
                    batchesDrawn++;
                    indicesDrawn += indices;
                }
            }
            return new DrawStats(batchesDrawn, indicesDrawn);
        }

        DrawStats drawShadow(Vec3 camera) {
            int batchesDrawn = 0;
            int indicesDrawn = 0;
            for (PipeGpuBatch batch : this.opaque) {
                int indices = batch.drawShadow(camera);
                if (indices > 0) {
                    batchesDrawn++;
                    indicesDrawn += indices;
                }
            }
            return new DrawStats(batchesDrawn, indicesDrawn);
        }

        void release() {
            for (PipeGpuBatch batch : this.opaque) {
                batch.release();
            }
            for (PipeGpuBatch batch : this.translucent) {
                batch.release();
            }
        }

        boolean needsLightRetry() {
            for (PipeGpuBatch batch : this.opaque) {
                if (batch.needsLightRetry()) {
                    return true;
                }
            }
            for (PipeGpuBatch batch : this.translucent) {
                if (batch.needsLightRetry()) {
                    return true;
                }
            }
            return false;
        }

        private record DrawStats(int batches, int indices) {
            boolean drew() {
                return this.batches > 0;
            }
        }
    }

    private static final class PipeGpuBatch {
        private final RenderType renderType;
        private final Vec3 sectionOrigin;
        private final GpuBuffer vertexBuffer;
        private final int indexCount;
        private final int shadowIndexCount;
        private final boolean needsLightRetry;

        private PipeGpuBatch(RenderType renderType, Vec3 sectionOrigin, GpuBuffer vertexBuffer, int indexCount, int shadowIndexCount, boolean needsLightRetry) {
            this.renderType = renderType;
            this.sectionOrigin = sectionOrigin;
            this.vertexBuffer = vertexBuffer;
            this.indexCount = indexCount;
            this.shadowIndexCount = shadowIndexCount;
            this.needsLightRetry = needsLightRetry;
        }

        @Nullable
        static PipeGpuBatch upload(RenderType renderType, List<TexturedQuad> quads, RenderSectionKey sectionKey, FrameLightSampler lightSampler) {
            if (quads.isEmpty()) {
                return null;
            }
            Vec3 sectionOrigin = sectionKey.origin();
            int estimatedBytes = Math.max(GPU_BATCH_INITIAL_BYTES, quads.size() * 4 * renderType.format().getVertexSize());
            LightBakeStats lightStats = new LightBakeStats();
            int shadowQuadCount = shadowQuadCount(quads);
            List<TexturedQuad> uploadQuads = orderShadowCastersFirst(quads, shadowQuadCount);
            try (PipeRenderExtension.Scope bufferBuildScope = renderExtension.entityBufferBuild();
                    ByteBufferBuilder byteBuffer = new ByteBufferBuilder(estimatedBytes)) {
                BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.mode(), renderType.format());
                for (TexturedQuad quad : uploadQuads) {
                    addGpuVertex(builder, quad.a(), sectionOrigin, quad.u0(), quad.v0(), quad.color(), lightSampler.lightAt(quad.lightA(), quad.fullBright(), lightStats), quad.normal());
                    addGpuVertex(builder, quad.b(), sectionOrigin, quad.u1(), quad.v0(), quad.color(), lightSampler.lightAt(quad.lightB(), quad.fullBright(), lightStats), quad.normal());
                    addGpuVertex(builder, quad.c(), sectionOrigin, quad.u1(), quad.v1(), quad.color(), lightSampler.lightAt(quad.lightC(), quad.fullBright(), lightStats), quad.normal());
                    addGpuVertex(builder, quad.d(), sectionOrigin, quad.u0(), quad.v1(), quad.color(), lightSampler.lightAt(quad.lightD(), quad.fullBright(), lightStats), quad.normal());
                }
                MeshData mesh = builder.build();
                if (mesh == null) {
                    return null;
                }
                try {
                    ByteBuffer vertices = mesh.vertexBuffer();
                    GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(
                            () -> "SuperPipeSlide pipe section " + renderType,
                            GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_VERTEX,
                            vertices);
                    int indexCount = mesh.drawState().indexCount();
                    int shadowIndexCount = shadowQuadCount == quads.size() ? indexCount : shadowQuadCount * 6;
                    return new PipeGpuBatch(renderType, sectionOrigin, vertexBuffer, indexCount, shadowIndexCount, lightStats.needsRetry());
                } finally {
                    mesh.close();
                }
            }
        }

        private static int shadowQuadCount(List<TexturedQuad> quads) {
            int count = 0;
            for (TexturedQuad quad : quads) {
                if (quad.castsShadow()) {
                    count++;
                }
            }
            return count;
        }

        private static List<TexturedQuad> orderShadowCastersFirst(List<TexturedQuad> quads, int shadowQuadCount) {
            if (shadowQuadCount == 0 || shadowQuadCount == quads.size()) {
                return quads;
            }
            List<TexturedQuad> ordered = new ArrayList<>(quads.size());
            for (TexturedQuad quad : quads) {
                if (quad.castsShadow()) {
                    ordered.add(quad);
                }
            }
            for (TexturedQuad quad : quads) {
                if (!quad.castsShadow()) {
                    ordered.add(quad);
                }
            }
            return ordered;
        }

        private static void addGpuVertex(BufferBuilder buffer, Vec3 point, Vec3 sectionOrigin, float u, float v, int color, int light, Vec3 normal) {
            buffer.addVertex((float) (point.x - sectionOrigin.x), (float) (point.y - sectionOrigin.y), (float) (point.z - sectionOrigin.z))
                    .setColor(color)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
        }

        int draw(Vec3 camera) {
            return this.draw(camera, this.indexCount);
        }

        int drawShadow(Vec3 camera) {
            return this.draw(camera, this.shadowIndexCount);
        }

        private int draw(Vec3 camera, int indexCount) {
            if (this.vertexBuffer.isClosed() || indexCount <= 0) {
                return 0;
            }
            RenderSetup renderSetup = ((RenderTypeAccessor) this.renderType).superpipeslide$state();
            RenderSetupAccessor renderSetupAccessor = (RenderSetupAccessor) (Object) renderSetup;
            Map<String, RenderSetup.TextureAndSampler> textures = renderSetup.getTextures();
            Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
            Consumer<Matrix4fStack> layeringModifier = renderSetupAccessor.superpipeslide$layeringTransform().getModifier();
            boolean pushedLayer = layeringModifier != null;
            if (pushedLayer) {
                modelViewStack.pushMatrix();
                layeringModifier.accept(modelViewStack);
            }
            try {
                TextureTransform textureTransform = renderSetupAccessor.superpipeslide$textureTransform();
                GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                        RenderSystem.getModelViewMatrix(),
                        new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
                        new Vector3f((float) (this.sectionOrigin.x - camera.x), (float) (this.sectionOrigin.y - camera.y), (float) (this.sectionOrigin.z - camera.z)),
                        textureTransform.getMatrix());
                RenderTarget target = this.renderType.outputTarget().getRenderTarget();
                var colorTexture = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
                var depthTexture = target.useDepth
                        ? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView())
                        : null;
                CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
                try (PipeRenderExtension.Scope phaseScope = renderExtension.entityPhase();
                        RenderPass renderPass = encoder.createRenderPass(
                                () -> "SuperPipeSlide pipe section " + this.renderType,
                                colorTexture,
                                OptionalInt.empty(),
                                depthTexture,
                                OptionalDouble.empty())) {
                    ScissorState scissorState = RenderSystem.getScissorStateForRenderTypeDraws();
                    if (scissorState.enabled()) {
                        renderPass.enableScissor(scissorState.x(), scissorState.y(), scissorState.width(), scissorState.height());
                    }
                    RenderSystem.bindDefaultUniforms(renderPass);
                    for (Map.Entry<String, RenderSetup.TextureAndSampler> entry : textures.entrySet()) {
                        renderPass.bindTexture(entry.getKey(), entry.getValue().textureView(), entry.getValue().sampler());
                    }
                    renderPass.setPipeline(this.renderType.pipeline());
                    RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(this.renderType.mode());
                    renderPass.setUniform("DynamicTransforms", dynamicTransforms);
                    renderPass.setVertexBuffer(0, this.vertexBuffer);
                    renderPass.setIndexBuffer(indexBuffer.getBuffer(indexCount), indexBuffer.type());
                    renderPass.drawIndexed(0, 0, indexCount, 1);
                }
            } finally {
                if (pushedLayer) {
                    modelViewStack.popMatrix();
                }
            }
            return indexCount;
        }

        void release() {
            if (!this.vertexBuffer.isClosed()) {
                this.vertexBuffer.close();
            }
        }

        boolean needsLightRetry() {
            return this.needsLightRetry;
        }
    }
}
