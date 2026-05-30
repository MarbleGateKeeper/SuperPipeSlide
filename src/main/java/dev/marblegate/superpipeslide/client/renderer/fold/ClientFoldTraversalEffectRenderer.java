package dev.marblegate.superpipeslide.client.renderer.fold;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.marblegate.superpipeslide.client.core.accessibility.ClientSafetyOptions;
import dev.marblegate.superpipeslide.client.core.fold.ClientFoldTraversalEffectController;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.render.SmoothGuiPrimitives;
import dev.marblegate.superpipeslide.client.renderer.ClientRenderCompatibility;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class ClientFoldTraversalEffectRenderer {
    private static final ContextKey<RenderData> RENDER_DATA = new ContextKey<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "fold_traversal_effect"));
    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final int SPACE_PRIMARY = 0xFF8FF6FF;
    private static final int SPACE_SECONDARY = 0xFFD9FFFF;
    private static final int SPACE_SHADOW = 0xFF1D5C6D;
    private static final int DIMENSION_PRIMARY = 0xFFB56CFF;
    private static final int DIMENSION_SECONDARY = 0xFFE45BFF;
    private static final int DIMENSION_ACCENT = 0xFFFFD16A;
    private static final int DIMENSION_SHADOW = 0xFF241447;

    private ClientFoldTraversalEffectRenderer() {}

    public static void extract(ExtractLevelRenderStateEvent event) {
        Optional<ClientFoldTraversalEffectController.Snapshot> snapshot = ClientFoldTraversalEffectController.snapshot();
        event.getRenderState().setRenderData(RENDER_DATA, new RenderData(snapshot.orElse(null)));
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        if (ClientSafetyOptions.reduceMotionSicknessRisk() || ClientSafetyOptions.reducePhotosensitivityRisk()) {
            return;
        }
        RenderData renderData = event.getLevelRenderState().getRenderData(RENDER_DATA);
        if (renderData == null || renderData.snapshot() == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        ClientFoldTraversalEffectController.Snapshot snapshot = renderData.snapshot();
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        ClientRenderCompatibility.submitCustomGeometry(event.getSubmitNodeCollector(), poseStack, ClientRenderCompatibility.effectQuads(), (pose, buffer) -> renderWorldSurfaces(pose, buffer, snapshot, minecraft.level.dimension(), camera));
        poseStack.popPose();
    }

    public static void renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (ClientSafetyOptions.reduceMotionSicknessRisk() || ClientSafetyOptions.reducePhotosensitivityRisk()) {
            return;
        }
        Optional<ClientFoldTraversalEffectController.Snapshot> snapshot = ClientFoldTraversalEffectController.snapshot();
        if (snapshot.isEmpty()) {
            return;
        }
        ClientFoldTraversalEffectController.Snapshot effect = snapshot.get();
        if (effect.visualPhase() == ClientFoldTraversalEffectController.VisualPhase.EXIT
                || effect.visualPhase() == ClientFoldTraversalEffectController.VisualPhase.DECAY) {
            return;
        }
        double intensity = overlayIntensity(effect);
        if (intensity <= 0.015D) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        renderEdgeCompression(graphics, width, height, effect, intensity);
        renderTunnelSheets(graphics, width, height, effect, intensity);
        renderExitSeam(graphics, width, height, effect, intensity);
        renderPixelShear(graphics, width, height, effect, intensity);
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (ClientSafetyOptions.reduceMotionSicknessRisk()) {
            return;
        }
        event.setFOV(event.getFOV() + (float) ClientFoldTraversalEffectController.fovOffset());
    }

    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (ClientSafetyOptions.reduceMotionSicknessRisk()) {
            return;
        }
        event.setRoll(event.getRoll() + (float) ClientFoldTraversalEffectController.cameraRollOffset());
    }

    private static void renderWorldSurfaces(PoseStack.Pose pose, VertexConsumer buffer, ClientFoldTraversalEffectController.Snapshot effect, ResourceKey<Level> currentLevel, Vec3 camera) {
        if (effect.entryLevel().equals(currentLevel)) {
            renderMembrane(pose, buffer, effect, effect.entryPosition(), effect.entryTangent(), true, camera);
            renderMembraneCreases(pose, buffer, effect, effect.entryPosition(), effect.entryTangent(), true);
            renderAnchorPulse(pose, buffer, effect, effect.entryPosition(), effect.entryTangent(), true);
            renderFoldSheets(pose, buffer, effect, effect.entryPosition(), effect.entryTangent(), true);
        }
        if (effect.exitLevel().equals(currentLevel) && shouldRenderExit(effect)) {
            renderMembrane(pose, buffer, effect, effect.exitPosition(), effect.exitTangent(), false, camera);
            renderMembraneCreases(pose, buffer, effect, effect.exitPosition(), effect.exitTangent(), false);
            renderAnchorPulse(pose, buffer, effect, effect.exitPosition(), effect.exitTangent(), false);
            renderFoldSheets(pose, buffer, effect, effect.exitPosition(), effect.exitTangent(), false);
            renderSpatialSilhouette(pose, buffer, effect, effect.exitPosition(), effect.exitTangent());
        }
    }

    private static boolean shouldRenderExit(ClientFoldTraversalEffectController.Snapshot effect) {
        return switch (effect.visualPhase()) {
            case EXIT, DECAY -> true;
            case TUNNEL, WAITING -> !effect.crossDimension();
            case CONTACT -> !effect.crossDimension() && effect.phaseProgress() > 0.45D;
            case APPROACH, CANCEL -> false;
        };
    }

    private static void renderMembrane(PoseStack.Pose pose, VertexConsumer buffer, ClientFoldTraversalEffectController.Snapshot effect, Vec3 center, Vec3 tangent, boolean entry, Vec3 camera) {
        Frame frame = frame(tangent);
        double time = timeSeconds(effect);
        double phase = effect.phaseProgress();
        double life = effect.life();
        double entrySign = entry ? -1.0D : 1.0D;
        double radius = (effect.dimensionFold() ? 1.12D : 0.92D) + effect.speed01() * 0.18D;
        double membraneScale = switch (effect.visualPhase()) {
            case APPROACH -> 0.58D + effect.approachProgress() * 0.46D;
            case CONTACT -> 1.02D + easeOut(phase) * 0.12D;
            case TUNNEL, WAITING -> 1.12D + pulse(time, effect.seed(), 0.08D);
            case EXIT -> 0.72D + easeOut(phase) * 0.50D;
            case DECAY -> 1.18D - easeOut(phase) * 0.36D;
            case CANCEL -> 0.95D - easeOut(phase) * 0.30D;
        };
        double alphaScale = life * switch (effect.visualPhase()) {
            case APPROACH -> 0.45D + effect.approachProgress() * 0.38D;
            case CONTACT -> 0.86D;
            case TUNNEL, WAITING -> 0.72D;
            case EXIT -> 0.90D - phase * 0.18D;
            case DECAY -> 0.56D;
            case CANCEL -> 0.64D;
        };
        if (alphaScale <= 0.01D) {
            return;
        }

        int rings = effect.dimensionFold() ? 6 : 5;
        int segments = effect.dimensionFold() ? 48 : 40;
        for (int ring = 0; ring < rings; ring++) {
            double r0 = ring / (double) rings;
            double r1 = (ring + 1) / (double) rings;
            for (int segment = 0; segment < segments; segment++) {
                double a0 = Mth.TWO_PI * segment / segments;
                double a1 = Mth.TWO_PI * (segment + 1) / segments;
                Vec3 p00 = membranePoint(effect, center, frame, radius, membraneScale, r0, a0, entrySign, time);
                Vec3 p10 = membranePoint(effect, center, frame, radius, membraneScale, r1, a0, entrySign, time);
                Vec3 p11 = membranePoint(effect, center, frame, radius, membraneScale, r1, a1, entrySign, time);
                Vec3 p01 = membranePoint(effect, center, frame, radius, membraneScale, r0, a1, entrySign, time);
                double edge = Math.max(r0, r1);
                double crease = 0.5D + 0.5D * Math.sin(a0 * (effect.dimensionFold() ? 6.0D : 4.0D) + time * 2.2D + effect.seed() * 0.001D);
                int color = colorForMembrane(effect, edge, crease, alphaScale);
                addQuad(pose, buffer, p00, p10, p11, p01, color);
                addQuad(pose, buffer, p01, p11, p10, p00, colorWithScaledAlpha(color, 0.55D));
            }
        }
    }

    private static Vec3 membranePoint(ClientFoldTraversalEffectController.Snapshot effect, Vec3 center, Frame frame, double radius, double scale, double r, double angle, double entrySign, double time) {
        double fold = 1.0D + 0.055D * Math.sin(angle * 4.0D + effect.seed() * 0.0007D)
                + (effect.dimensionFold() ? 0.035D * Math.sin(angle * 9.0D - time * 1.5D) : 0.025D * Math.sin(angle * 7.0D + time * 2.0D));
        double x = Math.cos(angle) * radius * scale * r * fold;
        double y = Math.sin(angle) * radius * scale * r * (1.0D + (effect.dimensionFold() ? 0.04D : 0.025D) * Math.sin(angle * 3.0D + time));
        double ripple = Math.sin((r * 4.0D - effect.phaseProgress() * 2.7D + time * 1.4D) * Math.PI);
        double depression = (0.10D + effect.speed01() * 0.14D + (effect.dimensionFold() ? 0.08D : 0.0D)) * Math.sin(r * Math.PI) * ripple;
        if (effect.visualPhase() == ClientFoldTraversalEffectController.VisualPhase.EXIT) {
            depression *= 0.55D * (1.0D - effect.phaseProgress());
        } else if (effect.visualPhase() == ClientFoldTraversalEffectController.VisualPhase.APPROACH) {
            depression *= effect.approachProgress() * 0.72D;
        }
        return center.add(frame.right().scale(x)).add(frame.up().scale(y)).add(frame.normal().scale(depression * entrySign));
    }

    private static int colorForMembrane(ClientFoldTraversalEffectController.Snapshot effect, double edge, double crease, double alphaScale) {
        int primary = primary(effect);
        int secondary = secondary(effect);
        int color = mix(primary, secondary, Math.min(1.0D, edge * 0.72D + crease * 0.20D));
        int baseAlpha = effect.dimensionFold() ? 82 : 66;
        int edgeBoost = (int) Math.round(edge * (effect.dimensionFold() ? 74 : 58));
        int creaseBoost = (int) Math.round(crease * 28.0D);
        return withAlpha(color, (int) Math.round((baseAlpha + edgeBoost + creaseBoost) * alphaScale));
    }

    private static void renderMembraneCreases(PoseStack.Pose pose, VertexConsumer buffer, ClientFoldTraversalEffectController.Snapshot effect, Vec3 center, Vec3 tangent, boolean entry) {
        Frame frame = frame(tangent);
        double time = timeSeconds(effect);
        double radius = (effect.dimensionFold() ? 1.22D : 1.02D) + effect.speed01() * 0.18D;
        double alpha = effect.life() * switch (effect.visualPhase()) {
            case APPROACH -> 0.34D + effect.approachProgress() * 0.54D;
            case CONTACT -> 0.95D;
            case TUNNEL, WAITING -> 0.72D;
            case EXIT -> 0.88D * (1.0D - effect.phaseProgress() * 0.28D);
            case DECAY -> 0.45D * (1.0D - effect.phaseProgress());
            case CANCEL -> 0.68D * (1.0D - effect.phaseProgress());
        };
        int creases = effect.dimensionFold() ? 7 : 5;
        for (int i = 0; i < creases; i++) {
            double angle = Mth.TWO_PI * (i / (double) creases) + effect.seed() * 0.00012D + time * (effect.dimensionFold() ? -0.22D : 0.34D);
            double start = 0.20D + 0.12D * Math.sin(time * 1.8D + i);
            double end = 0.92D + 0.06D * Math.sin(time * 1.2D + i * 1.7D);
            Vec3 a = membranePoint(effect, center, frame, radius, 1.0D, start, angle, entry ? -1.0D : 1.0D, time).add(frame.normal().scale(entry ? -0.015D : 0.015D));
            Vec3 b = membranePoint(effect, center, frame, radius, 1.0D, end, angle + 0.12D * Math.sin(time + i), entry ? -1.0D : 1.0D, time).add(frame.normal().scale(entry ? -0.015D : 0.015D));
            int color = withAlpha(i % 3 == 0 && effect.dimensionFold() ? DIMENSION_ACCENT : secondary(effect), (int) Math.round(alpha * (effect.dimensionFold() ? 170 : 145)));
            renderRibbon(pose, buffer, a, b, frame.normal(), 0.018D + effect.speed01() * 0.014D + (effect.dimensionFold() ? 0.012D : 0.0D), color);
        }
    }

    private static void renderAnchorPulse(PoseStack.Pose pose, VertexConsumer buffer, ClientFoldTraversalEffectController.Snapshot effect, Vec3 center, Vec3 tangent, boolean entry) {
        Frame frame = frame(tangent);
        double time = timeSeconds(effect);
        double progress = effect.phaseProgress();
        double radius = switch (effect.visualPhase()) {
            case APPROACH -> 0.38D + effect.approachProgress() * 0.42D;
            case CONTACT -> 0.92D - easeOut(progress) * 0.36D;
            case TUNNEL, WAITING -> 0.64D + pulse(time, effect.seed(), 0.10D);
            case EXIT -> 0.34D + easeOut(progress) * 0.72D;
            case DECAY -> 1.02D - progress * 0.42D;
            case CANCEL -> 0.86D + progress * 0.28D;
        };
        double width = 0.036D + effect.speed01() * 0.020D + (effect.dimensionFold() ? 0.024D : 0.0D);
        double alpha = effect.life() * (entry ? 1.0D : 0.86D) * (effect.visualPhase() == ClientFoldTraversalEffectController.VisualPhase.CANCEL ? 0.65D : 1.0D);
        int color = withAlpha(effect.visualPhase() == ClientFoldTraversalEffectController.VisualPhase.CANCEL ? 0xFFFF8166 : primary(effect), (int) Math.round(alpha * 170.0D));
        renderRing(pose, buffer, center.add(frame.normal().scale(entry ? -0.055D : 0.055D)), frame, radius, width, color, 36, time, effect);
    }

    private static void renderFoldSheets(PoseStack.Pose pose, VertexConsumer buffer, ClientFoldTraversalEffectController.Snapshot effect, Vec3 center, Vec3 tangent, boolean entry) {
        if (effect.visualPhase() == ClientFoldTraversalEffectController.VisualPhase.APPROACH && effect.approachProgress() < 0.18D) {
            return;
        }
        Frame frame = frame(tangent);
        double time = timeSeconds(effect);
        int sheets = effect.dimensionFold() ? 7 : 5;
        double phaseWeight = switch (effect.visualPhase()) {
            case APPROACH -> effect.approachProgress() * 0.55D;
            case CONTACT -> 0.72D;
            case TUNNEL, WAITING -> 0.95D;
            case EXIT -> 0.82D * (1.0D - effect.phaseProgress() * 0.45D);
            case DECAY -> 0.46D * (1.0D - effect.phaseProgress());
            case CANCEL -> 0.42D * (1.0D - effect.phaseProgress());
        };
        if (phaseWeight <= 0.01D) {
            return;
        }
        for (int i = 0; i < sheets; i++) {
            double offsetPhase = ((time * (effect.dimensionFold() ? 0.55D : 0.90D) + i / (double) sheets + effect.seed() * 0.00003D) % 1.0D);
            double along = (entry ? -1.0D : 1.0D) * Mth.lerp(offsetPhase, 0.12D, 1.35D + effect.speed01() * 0.45D);
            double halfWidth = (effect.dimensionFold() ? 0.82D : 0.66D) + Math.sin(time + i) * 0.08D;
            double halfHeight = (effect.dimensionFold() ? 0.36D : 0.24D) + Math.cos(time * 1.3D + i) * 0.06D;
            double twist = Math.sin(time * 1.8D + i * 2.1D) * (effect.dimensionFold() ? 0.34D : 0.22D);
            Vec3 sheetCenter = center.add(frame.normal().scale(along)).add(frame.right().scale(twist * 0.10D)).add(frame.up().scale(Math.sin(time + i) * 0.05D));
            Vec3 right = safeNormalize(frame.right().add(frame.up().scale(twist * 0.22D)), frame.right());
            Vec3 up = safeNormalize(frame.up().subtract(frame.right().scale(twist * 0.18D)), frame.up());
            Vec3 a = sheetCenter.subtract(right.scale(halfWidth)).subtract(up.scale(halfHeight));
            Vec3 b = sheetCenter.add(right.scale(halfWidth * 0.92D)).subtract(up.scale(halfHeight * 1.10D));
            Vec3 c = sheetCenter.add(right.scale(halfWidth)).add(up.scale(halfHeight));
            Vec3 d = sheetCenter.subtract(right.scale(halfWidth * 0.88D)).add(up.scale(halfHeight * 1.08D));
            int color = withAlpha(mix(shadow(effect), primary(effect), 0.45D + offsetPhase * 0.35D), (int) Math.round(phaseWeight * (effect.dimensionFold() ? 54 : 42) * (1.0D - offsetPhase * 0.35D)));
            addQuad(pose, buffer, a, b, c, d, color);
        }
    }

    private static void renderSpatialSilhouette(PoseStack.Pose pose, VertexConsumer buffer, ClientFoldTraversalEffectController.Snapshot effect, Vec3 center, Vec3 tangent) {
        if (effect.visualPhase() != ClientFoldTraversalEffectController.VisualPhase.EXIT && effect.visualPhase() != ClientFoldTraversalEffectController.VisualPhase.DECAY) {
            return;
        }
        Frame frame = frame(tangent);
        double progress = effect.visualPhase() == ClientFoldTraversalEffectController.VisualPhase.EXIT ? effect.phaseProgress() : 1.0D;
        double alpha = effect.life() * (1.0D - Math.min(0.82D, progress * 0.58D));
        int shards = effect.dimensionFold() ? 5 : 4;
        for (int i = 0; i < shards; i++) {
            double t = (i + 1.0D) / shards;
            Vec3 shardCenter = center.subtract(frame.normal().scale(t * (effect.dimensionFold() ? 1.55D : 1.05D) * (0.55D + effect.speed01() * 0.65D)));
            double halfWidth = 0.26D + t * 0.18D;
            double height = 0.92D + t * 0.28D;
            double lean = (0.12D + effect.speed01() * 0.08D) * Math.sin(timeSeconds(effect) * 3.0D + i);
            Vec3 right = frame.right().scale(halfWidth);
            Vec3 up = frame.up().scale(height * 0.5D);
            Vec3 shear = frame.right().scale(lean);
            Vec3 a = shardCenter.subtract(right).subtract(up).subtract(shear);
            Vec3 b = shardCenter.add(right).subtract(up).add(shear);
            Vec3 c = shardCenter.add(right.scale(0.82D)).add(up).subtract(shear.scale(0.45D));
            Vec3 d = shardCenter.subtract(right.scale(0.82D)).add(up).add(shear.scale(0.45D));
            int color = withAlpha(primary(effect), (int) Math.round(alpha * (82 - i * 10)));
            addQuad(pose, buffer, a, b, c, d, color);
        }
    }

    private static void renderRing(PoseStack.Pose pose, VertexConsumer buffer, Vec3 center, Frame frame, double radius, double width, int color, int segments, double time, ClientFoldTraversalEffectController.Snapshot effect) {
        for (int i = 0; i < segments; i++) {
            double a0 = Mth.TWO_PI * i / segments;
            double a1 = Mth.TWO_PI * (i + 1) / segments;
            double pulse0 = 1.0D + 0.045D * Math.sin(a0 * (effect.dimensionFold() ? 7.0D : 5.0D) + time * 2.0D);
            double pulse1 = 1.0D + 0.045D * Math.sin(a1 * (effect.dimensionFold() ? 7.0D : 5.0D) + time * 2.0D);
            Vec3 p00 = ringPoint(center, frame, radius - width, a0, pulse0);
            Vec3 p10 = ringPoint(center, frame, radius + width, a0, pulse0);
            Vec3 p11 = ringPoint(center, frame, radius + width, a1, pulse1);
            Vec3 p01 = ringPoint(center, frame, radius - width, a1, pulse1);
            addQuad(pose, buffer, p00, p10, p11, p01, color);
        }
    }

    private static Vec3 ringPoint(Vec3 center, Frame frame, double radius, double angle, double pulse) {
        return center.add(frame.right().scale(Math.cos(angle) * radius * pulse)).add(frame.up().scale(Math.sin(angle) * radius * pulse));
    }

    private static void renderRibbon(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 normal, double width, int color) {
        Vec3 axis = b.subtract(a);
        if (axis.lengthSqr() < 1.0E-8D) {
            return;
        }
        Vec3 side = safeNormalize(axis.cross(normal), new Vec3(1.0D, 0.0D, 0.0D)).scale(width);
        addQuad(pose, buffer, a.subtract(side), a.add(side), b.add(side), b.subtract(side), color);
    }

    private static void addQuad(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        addVertex(pose, buffer, a, color);
        addVertex(pose, buffer, b, color);
        addVertex(pose, buffer, c, color);
        addVertex(pose, buffer, d, color);
    }

    private static void addVertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 point, int color) {
        buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z).setColor(color);
    }

    private static void renderEdgeCompression(GuiGraphicsExtractor graphics, int width, int height, ClientFoldTraversalEffectController.Snapshot effect, double intensity) {
        int primary = primary(effect);
        int shadow = shadow(effect);
        double phase = effect.phaseProgress();
        double compression = switch (effect.visualPhase()) {
            case APPROACH -> effect.approachProgress() * 0.42D;
            case CONTACT -> 0.48D + easeOut(phase) * 0.42D;
            case TUNNEL, WAITING -> 0.95D;
            case EXIT -> 0.78D * (1.0D - phase * 0.62D);
            case DECAY -> 0.28D * (1.0D - phase);
            case CANCEL -> 0.46D * (1.0D - phase);
        } * intensity;
        int edge = Math.max(8, (int) Math.round(Math.min(width, height) * (0.035D + compression * 0.070D)));
        int alpha = (int) Math.round((effect.dimensionFold() ? 72 : 52) * compression);
        graphics.fill(0, 0, width, edge, withAlpha(mix(primary, shadow, 0.42D), alpha));
        graphics.fill(0, height - edge, width, height, withAlpha(mix(primary, shadow, 0.42D), alpha));
        graphics.fill(0, 0, edge, height, withAlpha(shadow, Math.max(0, alpha - 8)));
        graphics.fill(width - edge, 0, width, height, withAlpha(shadow, Math.max(0, alpha - 8)));

        int innerAlpha = (int) Math.round(alpha * 0.42D);
        graphics.fill(edge, edge, width - edge, edge + 2, withAlpha(primary, innerAlpha));
        graphics.fill(edge, height - edge - 2, width - edge, height - edge, withAlpha(primary, innerAlpha));
    }

    private static void renderTunnelSheets(GuiGraphicsExtractor graphics, int width, int height, ClientFoldTraversalEffectController.Snapshot effect, double intensity) {
        double time = timeSeconds(effect);
        Vec2 center = new Vec2(width * 0.5D, height * 0.48D);
        int sheetCount = effect.dimensionFold() ? 14 : 10;
        List<SmoothGuiPrimitives.GradientQuad> quads = new ArrayList<>();
        double baseSpan = Math.hypot(width, height);
        double tunnel = switch (effect.visualPhase()) {
            case APPROACH -> effect.approachProgress() * 0.38D;
            case CONTACT -> 0.48D + easeOut(effect.phaseProgress()) * 0.44D;
            case TUNNEL, WAITING -> 1.0D;
            case EXIT -> 0.86D * (1.0D - effect.phaseProgress() * 0.36D);
            case DECAY -> 0.32D * (1.0D - effect.phaseProgress());
            case CANCEL -> 0.48D * (1.0D - effect.phaseProgress());
        } * intensity;
        if (tunnel <= 0.02D) {
            return;
        }
        for (int i = 0; i < sheetCount; i++) {
            double seedPhase = fractional(effect.seed() * 0.00013D + i * 0.173D);
            double flow = fractional(seedPhase + time * (effect.dimensionFold() ? 0.34D : 0.58D) + effect.phaseProgress() * 0.28D);
            double side = i % 2 == 0 ? -1.0D : 1.0D;
            double near = Mth.lerp(flow, baseSpan * 0.08D, baseSpan * 0.46D);
            double far = near + baseSpan * (0.18D + 0.04D * Math.sin(time + i));
            double band = 11.0D + flow * 28.0D + (effect.dimensionFold() ? 10.0D : 0.0D);
            double tilt = side * (0.30D + 0.18D * Math.sin(time * 1.4D + i));
            Vec2 axis = new Vec2(side * (0.46D + 0.12D * Math.sin(i)), -0.88D + tilt * 0.12D);
            Vec2 lateral = normalize(new Vec2(axis.y(), -axis.x()));
            Vec2 a = add(add(center, scale(axis, near)), scale(lateral, side * band));
            Vec2 b = add(add(center, scale(axis, far)), scale(lateral, side * (band * 2.8D + flow * 42.0D)));
            Vec2 c = add(add(center, scale(axis, far + band * 1.2D)), scale(lateral, side * (band * 1.7D + flow * 34.0D)));
            Vec2 d = add(add(center, scale(axis, near + band)), scale(lateral, side * band * 0.45D));
            int colorA = withAlpha(mix(shadow(effect), primary(effect), 0.34D + flow * 0.26D), (int) Math.round(tunnel * (effect.dimensionFold() ? 46 : 34) * (1.0D - flow * 0.42D)));
            int colorB = withAlpha(mix(primary(effect), secondary(effect), 0.42D), (int) Math.round(tunnel * (effect.dimensionFold() ? 68 : 52) * (1.0D - flow * 0.22D)));
            quads.add(new SmoothGuiPrimitives.GradientQuad(a, b, c, d, colorA, colorB, withAlpha(colorB, (colorB >>> 24) / 2), withAlpha(colorA, (colorA >>> 24) / 3)));
        }
        SmoothGuiPrimitives.quads(graphics, quads);
    }

    private static void renderExitSeam(GuiGraphicsExtractor graphics, int width, int height, ClientFoldTraversalEffectController.Snapshot effect, double intensity) {
        Vec2 center = new Vec2(width * 0.5D, height * 0.48D);
        double phase = effect.phaseProgress();
        double seamPower = switch (effect.visualPhase()) {
            case APPROACH -> effect.approachProgress() * 0.32D;
            case CONTACT -> 0.56D + easeOut(phase) * 0.32D;
            case TUNNEL, WAITING -> 1.0D;
            case EXIT -> 1.0D - phase * 0.42D;
            case DECAY -> 0.24D * (1.0D - phase);
            case CANCEL -> 0.40D * (1.0D - phase);
        } * intensity;
        if (seamPower <= 0.015D) {
            return;
        }
        double length = Math.min(width, height) * (effect.visualPhase() == ClientFoldTraversalEffectController.VisualPhase.EXIT ? 0.22D + easeOut(phase) * 0.50D : 0.24D);
        double thickness = 2.0D + seamPower * (effect.dimensionFold() ? 8.0D : 5.0D);
        Vec2 axis = new Vec2(1.0D, 0.06D * Math.sin(timeSeconds(effect) * 1.6D));
        int glow = withAlpha(secondary(effect), (int) Math.round(seamPower * (effect.dimensionFold() ? 150 : 126)));
        int core = withAlpha(effect.dimensionFold() ? DIMENSION_ACCENT : 0xFFE8FFFF, (int) Math.round(seamPower * 210.0D));
        SmoothGuiPrimitives.capsule(graphics, center, axis, length + thickness * 4.0D, thickness * 3.2D, withAlpha(primary(effect), (int) Math.round(seamPower * 54.0D)));
        SmoothGuiPrimitives.capsule(graphics, center, axis, length, thickness, glow);
        SmoothGuiPrimitives.capsule(graphics, center, axis, length * 0.52D, Math.max(1.4D, thickness * 0.34D), core);
    }

    private static void renderPixelShear(GuiGraphicsExtractor graphics, int width, int height, ClientFoldTraversalEffectController.Snapshot effect, double intensity) {
        double time = timeSeconds(effect);
        double amount = switch (effect.visualPhase()) {
            case CONTACT -> easeOut(effect.phaseProgress()) * 0.86D;
            case TUNNEL, WAITING -> 1.0D;
            case EXIT -> 1.0D - effect.phaseProgress();
            case CANCEL -> 0.72D * (1.0D - effect.phaseProgress());
            default -> effect.dimensionFold() ? effect.approachProgress() * 0.34D : 0.0D;
        } * intensity * (effect.dimensionFold() ? 1.0D : 0.45D);
        if (amount <= 0.02D) {
            return;
        }
        int count = effect.dimensionFold() ? 34 : 18;
        for (int i = 0; i < count; i++) {
            double n1 = noise(effect.seed(), i, 11);
            double n2 = noise(effect.seed(), i, 29);
            double edgeBias = n1 < 0.5D ? n1 * 0.30D : 0.70D + (n1 - 0.5D) * 0.60D;
            int x = (int) Math.round(edgeBias * width + Math.sin(time * 2.1D + i) * amount * 8.0D);
            int y = (int) Math.round(n2 * height);
            int w = (int) Math.round(2.0D + noise(effect.seed(), i, 43) * 9.0D * amount);
            int h = (int) Math.round(1.0D + noise(effect.seed(), i, 67) * 4.0D * amount);
            int color = withAlpha(i % 5 == 0 && effect.dimensionFold() ? DIMENSION_ACCENT : secondary(effect), (int) Math.round(amount * (effect.dimensionFold() ? 76 : 44) * (0.35D + noise(effect.seed(), i, 83) * 0.65D)));
            graphics.fill(x, y, x + w, y + h, color);
        }
    }

    private static double overlayIntensity(ClientFoldTraversalEffectController.Snapshot effect) {
        double base = switch (effect.visualPhase()) {
            case APPROACH -> effect.approachProgress() * 0.42D;
            case CONTACT -> 0.70D + easeOut(effect.phaseProgress()) * 0.30D;
            case TUNNEL, WAITING -> 1.0D;
            case EXIT -> 0.92D * (1.0D - effect.phaseProgress() * 0.42D);
            case DECAY -> 0.34D * (1.0D - effect.phaseProgress());
            case CANCEL -> 0.52D * (1.0D - effect.phaseProgress());
        };
        return base * effect.life();
    }

    private static Frame frame(Vec3 tangent) {
        Vec3 normal = safeNormalize(tangent, new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 upHint = Math.abs(normal.dot(WORLD_UP)) > 0.88D ? new Vec3(0.0D, 0.0D, 1.0D) : WORLD_UP;
        Vec3 right = safeNormalize(normal.cross(upHint), new Vec3(1.0D, 0.0D, 0.0D));
        Vec3 up = safeNormalize(right.cross(normal), upHint);
        return new Frame(right, up, normal);
    }

    private static int primary(ClientFoldTraversalEffectController.Snapshot effect) {
        return effect.dimensionFold() ? DIMENSION_PRIMARY : SPACE_PRIMARY;
    }

    private static int secondary(ClientFoldTraversalEffectController.Snapshot effect) {
        return effect.dimensionFold() ? DIMENSION_SECONDARY : SPACE_SECONDARY;
    }

    private static int shadow(ClientFoldTraversalEffectController.Snapshot effect) {
        return effect.dimensionFold() ? DIMENSION_SHADOW : SPACE_SHADOW;
    }

    private static int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private static int colorWithScaledAlpha(int color, double scale) {
        return withAlpha(color, (int) Math.round(((color >>> 24) & 0xFF) * Mth.clamp(scale, 0.0D, 1.0D)));
    }

    private static int mix(int a, int b, double t) {
        double f = Mth.clamp(t, 0.0D, 1.0D);
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = (int) Math.round(Mth.lerp(f, ar, br));
        int g = (int) Math.round(Mth.lerp(f, ag, bg));
        int bl = (int) Math.round(Mth.lerp(f, ab, bb));
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    private static double easeOut(double value) {
        double t = Mth.clamp(value, 0.0D, 1.0D);
        return 1.0D - Math.pow(1.0D - t, 3.0D);
    }

    private static double pulse(double time, long seed, double amount) {
        return Math.sin(time * 2.4D + seed * 0.0003D) * amount;
    }

    private static double timeSeconds(ClientFoldTraversalEffectController.Snapshot effect) {
        return System.nanoTime() / 1_000_000_000.0D + effect.seed() * 0.000001D;
    }

    private static double noise(long seed, int index, int salt) {
        long value = seed ^ (long) index * 0x9E3779B97F4A7C15L ^ (long) salt * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return (value & 0xFFFFFFL) / (double) 0x1000000L;
    }

    private static double fractional(double value) {
        return value - Math.floor(value);
    }

    private static Vec2 add(Vec2 a, Vec2 b) {
        return new Vec2(a.x() + b.x(), a.y() + b.y());
    }

    private static Vec2 scale(Vec2 value, double scale) {
        return new Vec2(value.x() * scale, value.y() * scale);
    }

    private static Vec2 normalize(Vec2 value) {
        double length = Math.hypot(value.x(), value.y());
        return length <= 1.0E-6D ? new Vec2(1.0D, 0.0D) : new Vec2(value.x() / length, value.y() / length);
    }

    private static Vec3 safeNormalize(Vec3 value, Vec3 fallback) {
        return value.lengthSqr() < 1.0E-8D ? fallback : value.normalize();
    }

    private record RenderData(ClientFoldTraversalEffectController.Snapshot snapshot) {}

    private record Frame(Vec3 right, Vec3 up, Vec3 normal) {}
}
