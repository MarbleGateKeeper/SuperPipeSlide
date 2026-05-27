package dev.marblegate.superpipeslide.client.renderer.navigation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.marblegate.superpipeslide.client.core.accessibility.ClientSafetyOptions;
import dev.marblegate.superpipeslide.client.core.navigation.ClientNavigationController;
import dev.marblegate.superpipeslide.client.renderer.ClientRenderCompatibility;
import dev.marblegate.superpipeslide.client.renderer.SubmitTextRenderer;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Matrix4f;

import java.util.Optional;

public final class ClientNavigationWorldHighlighter {
    private static final ContextKey<RenderData> RENDER_DATA = new ContextKey<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "navigation_world_highlight"));
    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final double WORLD_MARKER_RANGE = 72.0D;

    private ClientNavigationWorldHighlighter() {
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !event.getLevel().dimension().equals(player.level().dimension())) {
            event.getRenderState().setRenderData(RENDER_DATA, RenderData.EMPTY);
            return;
        }
        Optional<ClientNavigationController.WorldTarget> target = ClientNavigationController.worldTarget(player)
                .filter(value -> value.distance() <= WORLD_MARKER_RANGE);
        event.getRenderState().setRenderData(RENDER_DATA, target.isPresent() ? new RenderData(target) : RenderData.EMPTY);
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        boolean photic = ClientSafetyOptions.reducePhotosensitivityRisk();
        RenderData renderData = event.getLevelRenderState().getRenderData(RENDER_DATA);
        if (renderData == null || renderData.empty()) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        renderData.target().ifPresent(target -> {
            ClientRenderCompatibility.submitCustomGeometry(
                    event.getSubmitNodeCollector(),
                    poseStack,
                    ClientRenderCompatibility.effectQuads(),
                    (pose, buffer) -> renderTarget(pose, buffer, target, camera, photic)
            );
            renderTargetLabel(event, poseStack, target, camera);
        });
        poseStack.popPose();
    }

    private static void renderTarget(PoseStack.Pose pose, VertexConsumer buffer, ClientNavigationController.WorldTarget target, Vec3 camera, boolean photic) {
        Vec3 normal = safeNormalize(camera.subtract(target.position()), new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 right = safeNormalize(WORLD_UP.cross(normal), new Vec3(1.0D, 0.0D, 0.0D));
        Vec3 up = safeNormalize(normal.cross(right), WORLD_UP);
        double distanceToCamera = Math.max(4.0D, camera.distanceTo(target.position()));
        double markerScale = markerScale(distanceToCamera);
        double distanceFade = Math.max(0.58D, Math.min(1.0D, 96.0D / Math.max(16.0D, distanceToCamera)));
        long now = System.currentTimeMillis();
        double pulse = photic ? 0.0D : 0.5D + 0.5D * Math.sin(now / 230.0D);
        int color = withAlpha(target.color(), (int) Math.round((photic ? 0x6A : 0x86 + pulse * 0x48) * distanceFade));
        int core = withAlpha(0xFFFFFFFF, (int) Math.round((photic ? 0x96 : 0xD0) * distanceFade));
        double baseRadius = switch (target.kind()) {
            case SAME_STATION_TRANSFER -> 0.72D;
            case OUT_OF_STATION_TRANSFER -> 0.80D;
            case CROSS_DIMENSION_TRANSFER -> 0.88D;
            case FINAL_WALK -> 0.84D;
            case CROSS_DIMENSION_FINAL_WALK -> 0.92D;
            case DESTINATION -> 0.86D;
            case BOARDING -> 0.68D;
        };
        double radius = baseRadius * markerScale;
        Vec3 markerCenter = target.position();
        Vec3 coreCenter = markerCenter.add(normal.scale(0.024D * markerScale));
        renderDiamond(pose, buffer, markerCenter, right, up, radius + pulse * 0.08D * markerScale, color);
        renderDiamond(pose, buffer, coreCenter, right, up, radius * 0.36D, core);
        renderArrowStem(pose, buffer, markerCenter.add(normal.scale(0.012D * markerScale)), right, up, color, pulse, markerScale);
    }

    private static void renderTargetLabel(SubmitCustomGeometryEvent event, PoseStack poseStack, ClientNavigationController.WorldTarget target, Vec3 camera) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        String distance = target.distance() >= 999.0D ? "999m+" : Math.round(target.distance()) + "m";
        Component label = Component.literal(target.name() + " / " + distance);
        double distanceToCamera = Math.max(4.0D, camera.distanceTo(target.position()));
        double markerScale = markerScale(distanceToCamera);
        float halfTanFov = (float) Math.tan(Math.toRadians(minecraft.options.fov().get()) * 0.5D);
        float scale = (float) Math.max(0.019D, Math.min(0.048D, distanceToCamera * halfTanFov * 0.009D));
        poseStack.pushPose();
        poseStack.translate(target.position().x, target.position().y + 0.72D + markerScale * 0.48D, target.position().z);
        poseStack.mulPose(minecraft.gameRenderer.getMainCamera().rotation());
        poseStack.scale(scale, -scale, scale);
        SubmitTextRenderer.submitText(
                event.getSubmitNodeCollector(),
                poseStack,
                font,
                -font.width(label) * 0.5F,
                0.0F,
                label.getVisualOrderText(),
                false,
                throughWallTarget(target) ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
                LightCoordsUtil.FULL_BRIGHT,
                0xFFFFFFFF,
                0xAA101820,
                0
        );
        poseStack.popPose();
    }

    private static boolean throughWallTarget(ClientNavigationController.WorldTarget target) {
        return switch (target.kind()) {
            case BOARDING, SAME_STATION_TRANSFER, OUT_OF_STATION_TRANSFER, CROSS_DIMENSION_TRANSFER -> true;
            case FINAL_WALK, CROSS_DIMENSION_FINAL_WALK, DESTINATION -> false;
        };
    }

    private static double markerScale(double distanceToCamera) {
        Minecraft minecraft = Minecraft.getInstance();
        double halfTanFov = Math.tan(Math.toRadians(minecraft.options.fov().get()) * 0.5D);
        return Math.max(0.85D, Math.min(6.2D, distanceToCamera * halfTanFov * 0.022D));
    }

    private static void renderDiamond(PoseStack.Pose pose, VertexConsumer buffer, Vec3 center, Vec3 right, Vec3 up, double radius, int color) {
        Vec3 top = center.add(up.scale(radius));
        Vec3 r = center.add(right.scale(radius));
        Vec3 bottom = center.subtract(up.scale(radius));
        Vec3 l = center.subtract(right.scale(radius));
        addBillboardQuad(pose, buffer, top, r, bottom, l, color);
    }

    private static void renderArrowStem(PoseStack.Pose pose, VertexConsumer buffer, Vec3 center, Vec3 right, Vec3 up, int color, double pulse, double markerScale) {
        double halfWidth = (0.055D + pulse * 0.018D) * markerScale;
        double top = -0.92D * markerScale;
        double bottom = -1.68D * markerScale;
        Vec3 a = center.add(up.scale(top)).subtract(right.scale(halfWidth));
        Vec3 b = center.add(up.scale(top)).add(right.scale(halfWidth));
        Vec3 c = center.add(up.scale(bottom)).add(right.scale(halfWidth));
        Vec3 d = center.add(up.scale(bottom)).subtract(right.scale(halfWidth));
        addBillboardQuad(pose, buffer, a, b, c, d, withAlpha(color, Math.min(210, (color >>> 24) & 0xFF)));
    }

    private static void addBillboardQuad(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        addQuad(pose, buffer, d, c, b, a, color);
    }

    private static void addQuad(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        Matrix4f matrix = pose.pose();
        buffer.addVertex(matrix, (float) a.x, (float) a.y, (float) a.z).setColor(color);
        buffer.addVertex(matrix, (float) b.x, (float) b.y, (float) b.z).setColor(color);
        buffer.addVertex(matrix, (float) c.x, (float) c.y, (float) c.z).setColor(color);
        buffer.addVertex(matrix, (float) d.x, (float) d.y, (float) d.z).setColor(color);
    }

    private static Vec3 safeNormalize(Vec3 value, Vec3 fallback) {
        return value.lengthSqr() < 1.0E-8D ? fallback : value.normalize();
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private record RenderData(Optional<ClientNavigationController.WorldTarget> target) {
        private static final RenderData EMPTY = new RenderData(Optional.empty());

        private boolean empty() {
            return this.target.isEmpty();
        }
    }
}
