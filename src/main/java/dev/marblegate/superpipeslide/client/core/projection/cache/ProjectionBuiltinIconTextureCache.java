package dev.marblegate.superpipeslide.client.core.projection.cache;

import com.mojang.blaze3d.platform.NativeImage;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionBuiltinIcon;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponentSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ProjectionBuiltinIconTextureCache {
    private static final int MAX_TEXTURES = 128;
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>(32, 0.75F, true);
    private static int sequence;

    private ProjectionBuiltinIconTextureCache() {
    }

    public static IconTexture textureFor(ProjectionBuiltinIcon icon, ProjectionComponentSettings.BuiltinIcon settings) {
        ProjectionBuiltinIcon safeIcon = icon == null ? ProjectionBuiltinIcon.byId(null) : icon;
        ProjectionComponentSettings.IconTintMode tintMode = settings == null ? ProjectionComponentSettings.IconTintMode.ORIGINAL : settings.tintMode();
        if (tintMode == ProjectionComponentSettings.IconTintMode.ORIGINAL) {
            return new IconTexture(ProjectionBuiltinIcon.TEXTURE, safeIcon.u0(), safeIcon.v0(), safeIcon.u1(), safeIcon.v1(), alphaColor(settings == null ? 1.0F : settings.opacity()));
        }
        String key = safeIcon.id() + "|" + tintMode + "|" + (settings == null ? 0xFFFFFFFF : settings.tintColor()) + "|" + (settings == null ? 0xFF37C3BB : settings.secondaryColor());
        Entry cached = CACHE.get(key);
        if (cached != null) {
            return new IconTexture(cached.textureId(), 0.0F, 0.0F, 1.0F, 1.0F, alphaColor(settings == null ? 1.0F : settings.opacity()));
        }
        try {
            NativeImage generated = generate(safeIcon, settings);
            Identifier textureId = Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "dynamic/projection_builtin_icon/" + Integer.toUnsignedString(key.hashCode(), 16) + "_" + sequence++);
            DynamicTexture texture = new DynamicTexture(() -> "SuperPipeSlide projection icon " + key, generated);
            Minecraft.getInstance().getTextureManager().register(textureId, texture);
            CACHE.put(key, new Entry(textureId, texture));
            trim();
            return new IconTexture(textureId, 0.0F, 0.0F, 1.0F, 1.0F, alphaColor(settings == null ? 1.0F : settings.opacity()));
        } catch (IOException exception) {
            SuperPipeSlide.LOGGER.warn("Failed to generate projection icon texture {}, using atlas icon", key, exception);
            return new IconTexture(ProjectionBuiltinIcon.TEXTURE, safeIcon.u0(), safeIcon.v0(), safeIcon.u1(), safeIcon.v1(), alphaColor(settings == null ? 1.0F : settings.opacity()));
        }
    }

    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (Entry entry : CACHE.values()) {
            minecraft.getTextureManager().release(entry.textureId());
        }
        CACHE.clear();
    }

    private static NativeImage generate(ProjectionBuiltinIcon icon, ProjectionComponentSettings.BuiltinIcon settings) throws IOException {
        NativeImage source = readAtlas();
        try {
            NativeImage result = new NativeImage(ProjectionBuiltinIcon.CELL_SIZE, ProjectionBuiltinIcon.CELL_SIZE, false);
            int x0 = icon.column() * ProjectionBuiltinIcon.CELL_SIZE;
            int y0 = icon.row() * ProjectionBuiltinIcon.CELL_SIZE;
            for (int y = 0; y < ProjectionBuiltinIcon.CELL_SIZE; y++) {
                for (int x = 0; x < ProjectionBuiltinIcon.CELL_SIZE; x++) {
                    int pixel = source.getPixel(x0 + x, y0 + y);
                    result.setPixel(x, y, transform(pixel, settings));
                }
            }
            return result;
        } finally {
            source.close();
        }
    }

    private static NativeImage readAtlas() throws IOException {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(ProjectionBuiltinIcon.TEXTURE);
        if (resource.isEmpty()) {
            throw new IOException("Missing icon atlas " + ProjectionBuiltinIcon.TEXTURE);
        }
        try (InputStream input = resource.get().open()) {
            return NativeImage.read(input);
        }
    }

    private static int transform(int pixel, ProjectionComponentSettings.BuiltinIcon settings) {
        int alpha = (pixel >>> 24) & 0xFF;
        if (alpha <= 0 || settings == null) {
            return pixel;
        }
        int tint = settings.tintColor();
        int secondary = settings.secondaryColor();
        return switch (settings.tintMode()) {
            case ORIGINAL -> pixel;
            case TINT -> argb(alpha, tintRed(tint, luminance(pixel)), tintGreen(tint, luminance(pixel)), tintBlue(tint, luminance(pixel)));
            case MULTIPLY -> argb(alpha,
                    ((pixel >>> 16) & 0xFF) * ((tint >>> 16) & 0xFF) / 255,
                    ((pixel >>> 8) & 0xFF) * ((tint >>> 8) & 0xFF) / 255,
                    (pixel & 0xFF) * (tint & 0xFF) / 255);
            case DUOTONE -> {
                double t = luminance(pixel) / 255.0D;
                yield argb(alpha,
                        mix((secondary >>> 16) & 0xFF, (tint >>> 16) & 0xFF, t),
                        mix((secondary >>> 8) & 0xFF, (tint >>> 8) & 0xFF, t),
                        mix(secondary & 0xFF, tint & 0xFF, t));
            }
        };
    }

    private static int tintRed(int tint, int luma) {
        return Math.max(0, Math.min(255, ((tint >>> 16) & 0xFF) * Math.max(54, luma) / 255));
    }

    private static int tintGreen(int tint, int luma) {
        return Math.max(0, Math.min(255, ((tint >>> 8) & 0xFF) * Math.max(54, luma) / 255));
    }

    private static int tintBlue(int tint, int luma) {
        return Math.max(0, Math.min(255, (tint & 0xFF) * Math.max(54, luma) / 255));
    }

    private static int luminance(int color) {
        return Math.max(0, Math.min(255, (((color >>> 16) & 0xFF) * 299 + ((color >>> 8) & 0xFF) * 587 + (color & 0xFF) * 114) / 1000));
    }

    private static int mix(int a, int b, double t) {
        return Math.max(0, Math.min(255, (int) Math.round(a + (b - a) * Math.max(0.0D, Math.min(1.0D, t)))));
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (Math.max(0, Math.min(255, alpha)) << 24)
                | (Math.max(0, Math.min(255, red)) << 16)
                | (Math.max(0, Math.min(255, green)) << 8)
                | Math.max(0, Math.min(255, blue));
    }

    private static int alphaColor(float opacity) {
        return (Math.max(0, Math.min(255, Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, opacity))))) << 24) | 0x00FFFFFF;
    }

    private static void trim() {
        Minecraft minecraft = Minecraft.getInstance();
        Iterator<Entry> iterator = CACHE.values().iterator();
        while (CACHE.size() > MAX_TEXTURES && iterator.hasNext()) {
            Entry entry = iterator.next();
            minecraft.getTextureManager().release(entry.textureId());
            iterator.remove();
        }
    }

    public record IconTexture(Identifier textureId, float u0, float v0, float u1, float v1, int color) {
    }

    private record Entry(Identifier textureId, DynamicTexture texture) {
    }
}
