package dev.marblegate.superpipeslide.client.renderer;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.Objects;

public final class ClientRenderCompatibility {
    private static final RenderType EFFECT_QUADS = RenderType.create(
            "superpipeslide_effect_quads",
            RenderSetup.builder(RenderPipelines.LIGHTNING)
                    .sortOnUpload()
                    .createRenderSetup()
    );
    private static volatile RenderTypeAdapter renderTypeAdapter = RenderTypeAdapter.IDENTITY;

    private ClientRenderCompatibility() {
    }

    public static void registerRenderTypeAdapter(RenderTypeAdapter adapter) {
        renderTypeAdapter = Objects.requireNonNull(adapter, "adapter");
    }

    public static void clearCaches() {
        renderTypeAdapter.clearCaches();
    }

    public static String renderStateKey() {
        return renderTypeAdapter.renderStateKey();
    }

    public static RenderType world(RenderType original) {
        return renderTypeAdapter.world(original);
    }

    public static RenderType text(RenderType original) {
        if (!isWorldTextPipeline(original.pipeline())) {
            return original;
        }
        return world(original);
    }

    public static RenderType effectQuads() {
        return EFFECT_QUADS;
    }

    public static MultiBufferSource textBufferSource(MultiBufferSource delegate) {
        return renderType -> delegate.getBuffer(text(renderType));
    }

    public static void submitCustomGeometry(SubmitNodeCollector collector, PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        collector.submitCustomGeometry(poseStack, world(renderType), renderer);
    }

    private static boolean isWorldTextPipeline(RenderPipeline pipeline) {
        return pipeline == RenderPipelines.TEXT
                || pipeline == RenderPipelines.TEXT_INTENSITY
                || pipeline == RenderPipelines.TEXT_POLYGON_OFFSET
                || pipeline == RenderPipelines.TEXT_SEE_THROUGH
                || pipeline == RenderPipelines.TEXT_INTENSITY_SEE_THROUGH
                || pipeline == RenderPipelines.TEXT_BACKGROUND
                || pipeline == RenderPipelines.TEXT_BACKGROUND_SEE_THROUGH;
    }

    public interface RenderTypeAdapter {
        RenderTypeAdapter IDENTITY = new RenderTypeAdapter() {
        };

        default String renderStateKey() {
            return "vanilla";
        }

        default RenderType world(RenderType original) {
            return original;
        }

        default void clearCaches() {
        }
    }
}
