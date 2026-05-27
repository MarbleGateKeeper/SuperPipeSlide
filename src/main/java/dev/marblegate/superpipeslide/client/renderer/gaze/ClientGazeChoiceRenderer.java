package dev.marblegate.superpipeslide.client.renderer.gaze;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.marblegate.superpipeslide.client.core.gaze.ClientGazeChoiceController;
import dev.marblegate.superpipeslide.client.renderer.SubmitTextRenderer;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.ArrayList;
import java.util.List;

public final class ClientGazeChoiceRenderer {
    private static final ContextKey<RenderData> RENDER_DATA = new ContextKey<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "gaze_choice_render_data"));

    private ClientGazeChoiceRenderer() {
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        Vec3 camera = event.getCamera().position();
        List<GazeLabel> labels = new ArrayList<>();
        for (ClientGazeChoiceController.RenderChoice choice : ClientGazeChoiceController.renderChoices()) {
            BillboardFrame frame = billboardFrame(choice, camera);
            Vec3 labelPosition = choice.position()
                    .add(frame.up().scale(choice.radius() * 1.08D + 0.08D))
                    .add(frame.normal().scale(0.035D));
            labels.add(new GazeLabel(labelPosition, choice.label(), choice.detail(), choice.visualState(), choice.recommended()));
        }
        event.getRenderState().setRenderData(RENDER_DATA, new RenderData(List.copyOf(labels)));
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        RenderData renderData = event.getLevelRenderState().getRenderData(RENDER_DATA);
        if (renderData == null || renderData.labels().isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        float halfTanFov = (float) Math.tan(Math.toRadians(minecraft.options.fov().get()) * 0.5D);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (GazeLabel label : renderData.labels()) {
            double distance = camera.distanceTo(label.position());
            float scale = (float) Math.max(0.018D, Math.min(0.04D, distance * halfTanFov * 0.008D));
            Component primary = truncate(label.primary(), font, 116);
            Component secondary = truncate(label.secondary(), font, 96);
            boolean hasSecondary = !secondary.getString().isBlank();
            int primaryColor = primaryColor(label);
            int secondaryColor = secondaryColor(label);
            int background = backgroundColor(label);
            poseStack.pushPose();
            poseStack.translate(label.position().x, label.position().y, label.position().z);
            poseStack.mulPose(minecraft.gameRenderer.getMainCamera().rotation());
            poseStack.scale(scale, -scale, scale);
            SubmitTextRenderer.submitText(
                    event.getSubmitNodeCollector(),
                    poseStack,
                    font,
                    -font.width(primary) * 0.5F,
                    hasSecondary ? -5.0F : 0.0F,
                    primary.getVisualOrderText(),
                    false,
                    Font.DisplayMode.NORMAL,
                    LightCoordsUtil.FULL_BRIGHT,
                    primaryColor,
                    background,
                    0
            );
            if (hasSecondary) {
                poseStack.pushPose();
                poseStack.scale(0.84F, 0.84F, 0.84F);
                SubmitTextRenderer.submitText(
                        event.getSubmitNodeCollector(),
                        poseStack,
                        font,
                        -font.width(secondary) * 0.5F,
                        7.0F,
                        secondary.getVisualOrderText(),
                        false,
                        Font.DisplayMode.NORMAL,
                        LightCoordsUtil.FULL_BRIGHT,
                        secondaryColor,
                        background,
                        0
                );
                poseStack.popPose();
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static Component truncate(Component component, Font font, int maxWidth) {
        String text = component.getString();
        if (text.isBlank() || font.width(text) <= maxWidth) {
            return component;
        }
        String suffix = "...";
        int suffixWidth = font.width(suffix);
        return Component.literal(font.plainSubstrByWidth(text, Math.max(8, maxWidth - suffixWidth)) + suffix);
    }

    private static int primaryColor(GazeLabel label) {
        return switch (label.visualState()) {
            case ACCEPTED -> 0xFFE8FFF2;
            case REJECTED -> 0xFFFFE1D6;
            case SUBMITTED, READY -> 0xFFFFFFFF;
            case FOCUSED -> 0xFFF4FBFF;
            case IDLE -> label.recommended() ? 0xFFE6F8FF : 0xDDDCE8F1;
        };
    }

    private static int secondaryColor(GazeLabel label) {
        return switch (label.visualState()) {
            case ACCEPTED -> 0xFFC6FFD7;
            case REJECTED -> 0xFFFFB49E;
            case SUBMITTED, READY -> 0xFFE6F8FF;
            case FOCUSED -> 0xFFD3E9F6;
            case IDLE -> 0xB8B9C7D2;
        };
    }

    private static int backgroundColor(GazeLabel label) {
        return switch (label.visualState()) {
            case ACCEPTED -> 0xA0142C20;
            case REJECTED -> 0xA0321714;
            case SUBMITTED, READY, FOCUSED -> 0xA4121820;
            case IDLE -> 0x7C101820;
        };
    }

    private static BillboardFrame billboardFrame(ClientGazeChoiceController.RenderChoice choice, Vec3 camera) {
        Vec3 normal = safeNormalize(camera.subtract(choice.position()), choice.forward());
        Vec3 upHint = projectOntoPlane(choice.up(), normal, new Vec3(0.0D, 1.0D, 0.0D));
        Vec3 right = safeNormalize(normal.cross(upHint), choice.right());
        Vec3 up = safeNormalize(right.cross(normal), upHint);
        return new BillboardFrame(right, up, normal);
    }

    private static Vec3 projectOntoPlane(Vec3 vector, Vec3 normal, Vec3 fallback) {
        Vec3 projected = vector.subtract(normal.scale(vector.dot(normal)));
        return safeNormalize(projected, fallback);
    }

    private static Vec3 safeNormalize(Vec3 vector, Vec3 fallback) {
        return vector.lengthSqr() < 1.0E-6D ? fallback : vector.normalize();
    }

    private record RenderData(List<GazeLabel> labels) {
    }

    private record GazeLabel(Vec3 position, Component primary, Component secondary, ClientGazeChoiceController.ChoiceVisualState visualState, boolean recommended) {
    }

    private record BillboardFrame(Vec3 right, Vec3 up, Vec3 normal) {
    }
}
