package dev.marblegate.superpipeslide.integration.iris.client;

import com.mojang.blaze3d.textures.GpuSampler;
import dev.marblegate.superpipeslide.client.renderer.ClientRenderCompatibility;
import dev.marblegate.superpipeslide.mixin.client.RenderTypeAccessor;
import dev.marblegate.superpipeslide.mixin.iris.IrisRenderSetupAccessor;
import dev.marblegate.superpipeslide.mixin.iris.IrisRenderSetupTextureBindingAccessor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class IrisRenderTypeAdapter implements ClientRenderCompatibility.RenderTypeAdapter {
    private static final int MAX_RENDER_TYPES = 512;
    private final Map<RenderType, RenderType> overlayRenderTypes = new LinkedHashMap<>(64, 0.75F, true);

    @Override
    public String renderStateKey() {
        if (!shaderPackInUse()) {
            return "iris_shaderpack_off";
        }
        try {
            return "iris_shaderpack:" + Iris.getCurrentPackName() + ":pipeline_" + Iris.getPipelineManager().getVersionCounterForSodiumShaderReload() + ":render_types_v1";
        } catch (RuntimeException | LinkageError exception) {
            return "iris_shaderpack:unknown:render_types_v1";
        }
    }

    @Override
    public RenderType world(RenderType original) {
        if (!shaderPackInUse()) {
            return original;
        }
        IrisRenderSetupAccessor setup = setup(original);
        if (setup.superpipeslide_iris$useOverlay() || setup.superpipeslide_iris$textures().containsKey("Sampler1")) {
            return original;
        }
        synchronized (this.overlayRenderTypes) {
            RenderType adapted = this.overlayRenderTypes.get(original);
            if (adapted != null) {
                return adapted;
            }
            trimForNewEntryLocked();
            adapted = createOverlayRenderType(original);
            this.overlayRenderTypes.put(original, adapted);
            return adapted;
        }
    }

    @Override
    public void clearCaches() {
        synchronized (this.overlayRenderTypes) {
            this.overlayRenderTypes.clear();
        }
    }

    private RenderType createOverlayRenderType(RenderType original) {
        IrisRenderSetupAccessor setup = setup(original);
        RenderSetup.RenderSetupBuilder builder = RenderSetup.builder(setup.superpipeslide_iris$pipeline())
                .setLayeringTransform(setup.superpipeslide_iris$layeringTransform())
                .setOutputTarget(setup.superpipeslide_iris$outputTarget())
                .setTextureTransform(setup.superpipeslide_iris$textureTransform())
                .setOutline(setup.superpipeslide_iris$outlineProperty())
                .bufferSize(setup.superpipeslide_iris$bufferSize());

        for (Map.Entry<String, ?> entry : setup.superpipeslide_iris$textures().entrySet()) {
            IrisRenderSetupTextureBindingAccessor texture = (IrisRenderSetupTextureBindingAccessor) entry.getValue();
            Identifier location = texture.superpipeslide_iris$location();
            Supplier<GpuSampler> sampler = texture.superpipeslide_iris$sampler();
            builder.withTexture(entry.getKey(), location, sampler);
        }
        if (setup.superpipeslide_iris$useLightmap()) {
            builder.useLightmap();
        }
        builder.useOverlay();
        if (setup.superpipeslide_iris$affectsCrumbling()) {
            builder.affectsCrumbling();
        }
        if (setup.superpipeslide_iris$sortOnUpload()) {
            builder.sortOnUpload();
        }
        return RenderType.create("superpipeslide_iris_overlay_" + sanitizeRenderTypeName(original.toString()), builder.createRenderSetup());
    }

    private static IrisRenderSetupAccessor setup(RenderType renderType) {
        return (IrisRenderSetupAccessor) (Object) ((RenderTypeAccessor) renderType).superpipeslide$state();
    }

    private void trimForNewEntryLocked() {
        while (this.overlayRenderTypes.size() >= MAX_RENDER_TYPES) {
            var iterator = this.overlayRenderTypes.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
        }
    }

    private static boolean shaderPackInUse() {
        try {
            return IrisApi.getInstance().isShaderPackInUse();
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    private static String sanitizeRenderTypeName(String name) {
        return name.replace(':', '_').replace('/', '_').replace('.', '_').replace(' ', '_').replace('[', '_').replace(']', '_');
    }
}
