package dev.marblegate.superpipeslide.client.renderer.anchor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.client.renderer.ClientRenderCompatibility;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.networkgraph.model.PipeNode;
import dev.marblegate.superpipeslide.common.item.anchor.BranchUpgraderItem;
import dev.marblegate.superpipeslide.common.item.anchor.BrokenAnchorCleanerItem;
import dev.marblegate.superpipeslide.common.item.anchor.FoldAnchorUpgradeItem;
import dev.marblegate.superpipeslide.common.item.pipe.PipeAppearanceToolItem;
import dev.marblegate.superpipeslide.common.item.pipe.PipeAttributeToolItem;
import dev.marblegate.superpipeslide.common.item.pipe.PipeConnectorItem;
import dev.marblegate.superpipeslide.common.item.pipe.PipeRemoverItem;
import dev.marblegate.superpipeslide.common.item.route.PlatformClaimerItem;
import dev.marblegate.superpipeslide.common.registry.SPSBlocks;
import dev.marblegate.superpipeslide.common.registry.SPSDataComponents;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

public final class ClientAnchorVisibilityRenderer {
    private static final ContextKey<RenderData> RENDER_DATA = new ContextKey<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "anchor_visibility_render_data"));
    private static final double NORMAL_RENDER_RADIUS = 72.0D;
    private static final double ENGINEERING_RENDER_RADIUS = 104.0D;
    private static final double SELECTED_RENDER_RADIUS = 144.0D;
    private static final double FADE_IN_SECONDS = 0.14D;
    private static final double FADE_OUT_SECONDS = 0.20D;
    private static final double MIN_RENDER_ALPHA = 0.025D;
    private static final double OPAQUE_ALPHA_EPSILON = 0.004D;
    private static final int NORMAL_TINT = 0xFFFFFFFF;
    private static final int DISABLED_TINT = 0xFF93A1AC;
    private static final int CANDIDATE_TINT = 0xFF9EFFDC;
    private static final int SELECTED_TINT = 0xFFEAFBFF;
    private static final int HOVER_TINT = 0xFFFFFFFF;
    private static final int BRANCH_TINT = 0xFFE7D1FF;
    private static final RenderType ANCHOR_SOLID_RENDER_TYPE = RenderTypes.entitySolid(TextureAtlas.LOCATION_BLOCKS);
    private static final RenderType ANCHOR_CUTOUT_RENDER_TYPE = RenderTypes.entityCutoutCull(TextureAtlas.LOCATION_BLOCKS);
    private static final RenderType ANCHOR_TRANSLUCENT_RENDER_TYPE = RenderType.create(
            "superpipeslide_anchor_translucent_cull",
            RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_CULL)
                    .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                    .useLightmap()
                    .useOverlay()
                    .affectsCrumbling()
                    .sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .createRenderSetup());

    private static final RandomSource MODEL_RANDOM = RandomSource.create();
    private static final Map<BlockState, List<BakedQuad>> MODEL_QUADS = new HashMap<>();
    private static final Map<BlockState, Boolean> SOLID_MODEL_CACHE = new HashMap<>();
    private static final Map<PipeAnchorId, FadeState> FADE_STATES = new HashMap<>();
    private static long lastExtractNanos;
    private static int frameId;

    private ClientAnchorVisibilityRenderer() {}

    public static void clear() {
        MODEL_QUADS.clear();
        SOLID_MODEL_CACHE.clear();
        FADE_STATES.clear();
        lastExtractNanos = 0L;
        frameId = 0;
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !event.getLevel().dimension().equals(player.level().dimension())) {
            event.getRenderState().setRenderData(RENDER_DATA, RenderData.EMPTY);
            return;
        }

        long now = System.nanoTime();
        double deltaSeconds = lastExtractNanos == 0L ? 1.0D / 60.0D : Math.min(0.08D, Math.max(0.0D, (now - lastExtractNanos) / 1_000_000_000.0D));
        lastExtractNanos = now;
        frameId++;

        AnchorRenderContext context = AnchorRenderContext.from(minecraft, player);
        Vec3 camera = event.getCamera().position();
        double baseRadius = context.engineeringMode() ? ENGINEERING_RENDER_RADIUS : NORMAL_RENDER_RADIUS;
        double queryRadius = context.expandedInterest() ? SELECTED_RENDER_RADIUS : baseRadius;
        double baseRadiusSqr = baseRadius * baseRadius;
        double selectedRadiusSqr = SELECTED_RENDER_RADIUS * SELECTED_RENDER_RADIUS;
        Map<BlockState, List<AnchorVisual>> solidBatches = new LinkedHashMap<>();
        Map<BlockState, List<AnchorVisual>> cutoutBatches = new LinkedHashMap<>();
        Map<BlockState, List<AnchorVisual>> translucentBatches = new LinkedHashMap<>();

        Collection<PipeNode> nodes = ClientPipeNetworkCache.nodesNear(event.getLevel().dimension(), camera, queryRadius);
        for (PipeNode node : nodes) {
            if (node.isFoldAnchor()) {
                FADE_STATES.remove(node.id());
                continue;
            }
            PipeAnchorId anchorId = node.id();
            BlockPos pos = anchorId.blockPos();
            Vec3 center = Vec3.atCenterOf(pos);
            double distanceSqr = center.distanceToSqr(camera);
            boolean selected = context.isSelected(anchorId);
            boolean hovered = context.isHovered(pos);
            boolean candidate = context.isConnectionCandidate(anchorId, node);
            if (distanceSqr > (selected || hovered || candidate ? selectedRadiusSqr : baseRadiusSqr)) {
                continue;
            }

            BlockState state = anchorBlockState(node).orElse(null);
            if (state == null) {
                continue;
            }

            AnchorStyle style = styleFor(node, context, selected, hovered, candidate);
            FadeState fade = FADE_STATES.get(anchorId);
            if (style.targetAlpha() <= 0.0D && fade == null) {
                continue;
            }
            if (fade == null) {
                fade = new FadeState();
                FADE_STATES.put(anchorId, fade);
            }
            fade.lastFrame = frameId;
            fade.alpha = approach(fade.alpha, style.targetAlpha(), deltaSeconds);
            if (fade.alpha > MIN_RENDER_ALPHA) {
                boolean opaque = isOpaqueAndSettled(style, fade);
                double showProgress = opaque ? 1.0D : Math.max(0.0D, Math.min(1.0D, fade.alpha / Math.max(0.18D, style.targetAlpha())));
                int color = opaque ? forceOpaque(style.tint()) : colorWithAlpha(style.tint(), fade.alpha);
                AnchorVisual visual = new AnchorVisual(anchorId, state, pos, color, showProgress, lightAt(event.getLevel(), pos));
                if (!opaque) {
                    translucentBatches.computeIfAbsent(state, ignored -> new ArrayList<>()).add(visual);
                } else if (canUseSolidRenderType(state)) {
                    solidBatches.computeIfAbsent(state, ignored -> new ArrayList<>()).add(visual);
                } else {
                    cutoutBatches.computeIfAbsent(state, ignored -> new ArrayList<>()).add(visual);
                }
            }
        }

        pruneFadeStates();
        RenderData renderData = RenderData.from(solidBatches, cutoutBatches, translucentBatches);
        event.getRenderState().setRenderData(RENDER_DATA, renderData.isEmpty() ? RenderData.EMPTY : renderData);
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        RenderData renderData = event.getLevelRenderState().getRenderData(RENDER_DATA);
        if (renderData == null || renderData.isEmpty()) {
            return;
        }

        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        submitBatches(event, camera, renderData.solidBatches(), ANCHOR_SOLID_RENDER_TYPE);
        submitBatches(event, camera, renderData.cutoutBatches(), ANCHOR_CUTOUT_RENDER_TYPE);
        submitBatches(event, camera, renderData.translucentBatches(), ANCHOR_TRANSLUCENT_RENDER_TYPE);
    }

    private static void submitBatches(SubmitCustomGeometryEvent event, Vec3 camera, List<AnchorBatch> batches, RenderType renderType) {
        if (batches.isEmpty()) {
            return;
        }
        ClientRenderCompatibility.submitCustomGeometry(
                event.getSubmitNodeCollector(),
                event.getPoseStack(),
                renderType,
                (pose, buffer) -> renderAnchorBatches(buffer, batches, camera));
    }

    private static void renderAnchorBatches(VertexConsumer buffer, List<AnchorBatch> batches, Vec3 camera) {
        PoseStack poseStack = new PoseStack();
        QuadInstance instance = new QuadInstance();
        for (AnchorBatch batch : batches) {
            List<BakedQuad> quads = quadsFor(batch.state());
            if (quads.isEmpty()) {
                continue;
            }
            for (AnchorVisual visual : batch.visuals()) {
                renderAnchorModel(poseStack, buffer, visual, quads, instance, camera);
            }
        }
    }

    private static void renderAnchorModel(PoseStack poseStack, VertexConsumer buffer, AnchorVisual visual, List<BakedQuad> quads, QuadInstance instance, Vec3 camera) {
        double scale = 0.92D + 0.08D * easeOutCubic(visual.showProgress());
        poseStack.pushPose();
        poseStack.translate(visual.pos().getX() + 0.5D - camera.x, visual.pos().getY() + 0.5D - camera.y, visual.pos().getZ() + 0.5D - camera.z);
        poseStack.scale((float) scale, (float) scale, (float) scale);
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        PoseStack.Pose pose = poseStack.last();
        instance.setColor(visual.color());
        for (BakedQuad quad : quads) {
            instance.setLightCoords(lightForQuad(visual.light(), quad));
            buffer.putBakedQuad(pose, quad, instance);
        }
        poseStack.popPose();
    }

    private static int lightForQuad(int baseLight, BakedQuad quad) {
        int lightEmission = Math.max(0, Math.min(15, quad.materialInfo().lightEmission()));
        if (lightEmission <= 0) {
            return baseLight;
        }
        int blockLight = Math.max(LightCoordsUtil.block(baseLight), lightEmission);
        return LightCoordsUtil.withBlock(baseLight, blockLight);
    }

    private static Optional<BlockState> anchorBlockState(PipeNode node) {
        return switch (node.type()) {
            case ORDINARY_ANCHOR -> Optional.of(SPSBlocks.PIPE_ANCHOR.get().defaultBlockState());
            case BRANCH -> Optional.of(SPSBlocks.BRANCH_ANCHOR.get().defaultBlockState());
            case FOLD_ANCHOR -> Optional.empty();
        };
    }

    private static AnchorStyle styleFor(PipeNode node, AnchorRenderContext context, boolean selected, boolean hovered, boolean candidate) {
        if (selected) {
            return new AnchorStyle(1.0D, SELECTED_TINT);
        }
        if (candidate) {
            return new AnchorStyle(0.95D, CANDIDATE_TINT);
        }
        if (hovered) {
            return new AnchorStyle(context.engineeringMode() ? 1.0D : 0.72D, HOVER_TINT);
        }

        int connectionCount = ClientPipeNetworkCache.connectionCount(node.id());
        return switch (node.type()) {
            case ORDINARY_ANCHOR -> ordinaryStyle(context, connectionCount);
            case BRANCH -> branchStyle(context, connectionCount);
            case FOLD_ANCHOR -> AnchorStyle.HIDDEN;
        };
    }

    private static AnchorStyle ordinaryStyle(AnchorRenderContext context, int connectionCount) {
        boolean full = connectionCount >= 2;
        if (context.engineeringMode()) {
            return full ? new AnchorStyle(0.36D, DISABLED_TINT) : new AnchorStyle(0.88D, NORMAL_TINT);
        }
        return full ? AnchorStyle.HIDDEN : new AnchorStyle(0.54D, NORMAL_TINT);
    }

    private static AnchorStyle branchStyle(AnchorRenderContext context, int connectionCount) {
        boolean complete = connectionCount >= 3;
        if (context.engineeringMode()) {
            return complete ? new AnchorStyle(0.74D, BRANCH_TINT) : new AnchorStyle(0.94D, BRANCH_TINT);
        }
        return complete ? new AnchorStyle(0.18D, BRANCH_TINT) : new AnchorStyle(0.62D, BRANCH_TINT);
    }

    private static double approach(double current, double target, double deltaSeconds) {
        double duration = target > current ? FADE_IN_SECONDS : FADE_OUT_SECONDS;
        double step = duration <= 1.0E-6D ? 1.0D : Math.min(1.0D, deltaSeconds / duration);
        return current + (target - current) * step;
    }

    private static boolean isOpaqueAndSettled(AnchorStyle style, FadeState fade) {
        return style.targetAlpha() >= 1.0D - OPAQUE_ALPHA_EPSILON
                && fade.alpha >= 1.0D - OPAQUE_ALPHA_EPSILON;
    }

    private static void pruneFadeStates() {
        Iterator<Map.Entry<PipeAnchorId, FadeState>> iterator = FADE_STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<PipeAnchorId, FadeState> entry = iterator.next();
            int age = frameId - entry.getValue().lastFrame;
            if (age > 120 || (age > 0 && entry.getValue().alpha <= MIN_RENDER_ALPHA)) {
                iterator.remove();
            }
        }
    }

    private static List<BakedQuad> quadsFor(BlockState state) {
        return MODEL_QUADS.computeIfAbsent(state, ClientAnchorVisibilityRenderer::collectQuads);
    }

    private static boolean canUseSolidRenderType(BlockState state) {
        return SOLID_MODEL_CACHE.computeIfAbsent(state, ignored -> quadsFor(state).stream().allMatch(ClientAnchorVisibilityRenderer::isSolidQuad));
    }

    private static boolean isSolidQuad(BakedQuad quad) {
        return (quad.materialInfo().flags() & BakedQuad.FLAG_TRANSLUCENT) == 0
                && !quad.materialInfo().sprite().transparency().hasTranslucent();
    }

    private static List<BakedQuad> collectQuads(BlockState state) {
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        MODEL_RANDOM.setSeed(0x51A7E5EEDL);
        model.collectParts(MODEL_RANDOM, parts);
        List<BakedQuad> quads = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            quads.addAll(part.getQuads(null));
            for (Direction direction : Direction.values()) {
                quads.addAll(part.getQuads(direction));
            }
        }
        return List.copyOf(quads);
    }

    private static int lightAt(ClientLevel level, BlockPos pos) {
        return LevelRenderer.getLightCoords(level, pos);
    }

    private static boolean isAnchorBlock(Block block) {
        return block == SPSBlocks.PIPE_ANCHOR.get()
                || block == SPSBlocks.BRANCH_ANCHOR.get()
                || block == SPSBlocks.SPACE_FOLD_ANCHOR.get()
                || block == SPSBlocks.DIMENSION_FOLD_ANCHOR.get();
    }

    private static int colorWithAlpha(int color, double alpha) {
        int a = (int) Math.round(((color >>> 24) & 0xFF) * Math.max(0.0D, Math.min(1.0D, alpha)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static int forceOpaque(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    private static double easeOutCubic(double value) {
        double clamped = Math.max(0.0D, Math.min(1.0D, value));
        double inverse = 1.0D - clamped;
        return 1.0D - inverse * inverse * inverse;
    }

    private record AnchorRenderContext(boolean engineeringMode, boolean connectorMode, @Nullable PipeAnchorId selectedAnchor, @Nullable BlockPos hoveredAnchorPos) {
        static AnchorRenderContext from(Minecraft minecraft, LocalPlayer player) {
            ItemStack mainHand = player.getMainHandItem();
            ItemStack offHand = player.getOffhandItem();
            boolean engineering = isEngineeringTool(mainHand) || isEngineeringTool(offHand);
            boolean connector = PipeConnectorItem.isConnector(mainHand) || PipeConnectorItem.isConnector(offHand);
            PipeAnchorId selected = selectedAnchor(mainHand).or(() -> selectedAnchor(offHand)).orElse(null);
            BlockPos hovered = hoveredAnchorPos(minecraft).orElse(null);
            return new AnchorRenderContext(engineering, connector, selected, hovered);
        }

        boolean isSelected(PipeAnchorId anchorId) {
            return this.selectedAnchor != null && this.selectedAnchor.equals(anchorId);
        }

        boolean isHovered(BlockPos pos) {
            return this.hoveredAnchorPos != null && this.hoveredAnchorPos.equals(pos);
        }

        boolean isConnectionCandidate(PipeAnchorId anchorId, PipeNode node) {
            if (!this.connectorMode || this.selectedAnchor == null || this.selectedAnchor.equals(anchorId)) {
                return false;
            }
            if (!this.selectedAnchor.levelKey().equals(anchorId.levelKey()) || ClientPipeNetworkCache.hasConnectionBetween(this.selectedAnchor, anchorId)) {
                return false;
            }
            return hasEndpointCapacity(node);
        }

        boolean expandedInterest() {
            return this.selectedAnchor != null || this.hoveredAnchorPos != null || this.connectorMode;
        }

        private static Optional<PipeAnchorId> selectedAnchor(ItemStack stack) {
            return Optional.ofNullable(stack.get(SPSDataComponents.SELECTED_ANCHOR.get()));
        }

        private static Optional<BlockPos> hoveredAnchorPos(Minecraft minecraft) {
            if (!(minecraft.hitResult instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK || minecraft.level == null) {
                return Optional.empty();
            }
            BlockPos pos = blockHit.getBlockPos();
            return isAnchorBlock(minecraft.level.getBlockState(pos).getBlock()) ? Optional.of(pos) : Optional.empty();
        }

        private static boolean hasEndpointCapacity(PipeNode node) {
            int count = ClientPipeNetworkCache.connectionCount(node.id());
            return switch (node.type()) {
                case ORDINARY_ANCHOR -> count < 2;
                case FOLD_ANCHOR -> count < 1;
                case BRANCH -> true;
            };
        }

        private static boolean isEngineeringTool(ItemStack stack) {
            Item item = stack.getItem();
            if (item instanceof PipeConnectorItem
                    || item instanceof PipeRemoverItem
                    || item instanceof BranchUpgraderItem
                    || item instanceof BrokenAnchorCleanerItem
                    || item instanceof FoldAnchorUpgradeItem
                    || item instanceof PipeAttributeToolItem
                    || item instanceof PipeAppearanceToolItem
                    || item instanceof PlatformClaimerItem) {
                return true;
            }
            return item instanceof BlockItem blockItem && isAnchorBlock(blockItem.getBlock());
        }
    }

    private record AnchorStyle(double targetAlpha, int tint) {
        static final AnchorStyle HIDDEN = new AnchorStyle(0.0D, NORMAL_TINT);
    }

    private record AnchorVisual(PipeAnchorId anchorId, BlockState state, BlockPos pos, int color, double showProgress, int light) {}

    private record AnchorBatch(BlockState state, List<AnchorVisual> visuals) {}

    private record RenderData(List<AnchorBatch> solidBatches, List<AnchorBatch> cutoutBatches, List<AnchorBatch> translucentBatches) {

        static final RenderData EMPTY = new RenderData(List.of(), List.of(), List.of());
        static RenderData from(Map<BlockState, List<AnchorVisual>> solidBatches, Map<BlockState, List<AnchorVisual>> cutoutBatches, Map<BlockState, List<AnchorVisual>> translucentBatches) {
            return new RenderData(freezeBatches(solidBatches), freezeBatches(cutoutBatches), freezeBatches(translucentBatches));
        }

        private static List<AnchorBatch> freezeBatches(Map<BlockState, List<AnchorVisual>> batches) {
            if (batches.isEmpty()) {
                return List.of();
            }
            List<AnchorBatch> frozen = new ArrayList<>(batches.size());
            for (Map.Entry<BlockState, List<AnchorVisual>> entry : batches.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    frozen.add(new AnchorBatch(entry.getKey(), List.copyOf(entry.getValue())));
                }
            }
            return List.copyOf(frozen);
        }

        boolean isEmpty() {
            return this.solidBatches.isEmpty() && this.cutoutBatches.isEmpty() && this.translucentBatches.isEmpty();
        }
    }

    private static final class FadeState {
        private double alpha;
        private int lastFrame;
    }
}
