package dev.marblegate.superpipeslide.client.core.pipe;

import com.mojang.blaze3d.platform.NativeImage;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.appearance.coating.PipeCoatingDyeMode;
import dev.marblegate.superpipeslide.common.core.appearance.coating.PipeCoatingSelection;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

final class PipeCoatingDynamicTextureCache {
    private static final int MAX_SOURCE_IMAGES = 128;
    // Generated texture ids are copied into GUI/world render meshes, so releasing individual entries can leave stale ids behind.
    private static final Map<String, TextureEntry> CACHE = new LinkedHashMap<>(32, 0.75F, true);
    private static final Map<Identifier, SourcePixels> SOURCE_CACHE = new LinkedHashMap<>(32, 0.75F, true);

    private PipeCoatingDynamicTextureCache() {}

    static Identifier textureFor(TextureAtlasSprite sourceSprite, PipeCoatingSelection selection) {
        String key = sourceSprite.contents().name() + "|" + selection.contentKey();
        TextureEntry cached = CACHE.get(key);
        if (cached != null) {
            return cached.textureId();
        }

        Identifier textureId = Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "dynamic/pipe_coating/" + sha1(key));
        try {
            NativeImage generated = recolor(sourcePixels(sourceSprite), selection);
            DynamicTexture texture = new DynamicTexture(() -> "SuperPipeSlide pipe coating " + key, generated);
            Minecraft.getInstance().getTextureManager().register(textureId, texture);
            CACHE.put(key, new TextureEntry(textureId, texture));
            return textureId;
        } catch (Exception exception) {
            SuperPipeSlide.LOGGER.warn("Failed to generate pipe coating texture {}, using safe fallback", key, exception);
            NativeImage fallback = new NativeImage(16, 16, false);
            fallback.fillRect(0, 0, 16, 16, selection.dyeColor());
            DynamicTexture texture = new DynamicTexture(() -> "SuperPipeSlide pipe coating fallback " + key, fallback);
            Minecraft.getInstance().getTextureManager().register(textureId, texture);
            CACHE.put(key, new TextureEntry(textureId, texture));
            return textureId;
        }
    }

    static boolean hasPartialTransparency(TextureAtlasSprite sourceSprite) {
        try {
            return sourcePixels(sourceSprite).hasPartialTransparency();
        } catch (IOException ignored) {
            return false;
        }
    }

    static ThemePaletteSuggestion suggestThemePalette(TextureAtlasSprite sourceSprite) {
        try {
            ThemePaletteStats stats = ThemePaletteStats.from(sourcePixels(sourceSprite));
            return new ThemePaletteSuggestion(stats.suggestedColors(), stats.hasStrongAccents());
        } catch (IOException ignored) {
            return new ThemePaletteSuggestion(List.of(PipeCoatingSelection.DEFAULT_DYE_COLOR), PipeCoatingSelection.DEFAULT_PRESERVE_ACCENTS);
        }
    }

    static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (TextureEntry entry : CACHE.values()) {
            minecraft.getTextureManager().release(entry.textureId());
        }
        CACHE.clear();
        SOURCE_CACHE.clear();
    }

    private static SourcePixels sourcePixels(TextureAtlasSprite sprite) throws IOException {
        Identifier spriteId = sprite.contents().name();
        SourcePixels cached = SOURCE_CACHE.get(spriteId);
        if (cached != null) {
            return cached;
        }
        SourcePixels loaded = readSourcePixels(sprite);
        SOURCE_CACHE.put(spriteId, loaded);
        trimSourceCache();
        return loaded;
    }

    private static SourcePixels readSourcePixels(TextureAtlasSprite sprite) throws IOException {
        Identifier spriteId = sprite.contents().name();
        Identifier textureId = Identifier.fromNamespaceAndPath(spriteId.getNamespace(), "textures/" + spriteId.getPath() + ".png");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(textureId);
        if (resource.isPresent()) {
            try (InputStream stream = resource.get().open()) {
                try (NativeImage full = NativeImage.read(stream)) {
                    int width = Math.min(full.getWidth(), Math.max(1, sprite.contents().width()));
                    int height = Math.min(full.getHeight(), Math.max(1, sprite.contents().height()));
                    int[] pixels = new int[width * height];
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            pixels[y * width + x] = full.getPixel(x, y);
                        }
                    }
                    return new SourcePixels(width, height, pixels);
                }
            }
        }
        int[] fallback = new int[16 * 16];
        Arrays.fill(fallback, 0xFFFFFFFF);
        return new SourcePixels(16, 16, fallback);
    }

    private static NativeImage recolor(SourcePixels source, PipeCoatingSelection selection) {
        int width = source.width();
        int height = source.height();
        NativeImage result = new NativeImage(width, height, false);
        PaletteStats stats = PaletteStats.from(source);
        ThemePaletteStats themeStats = selection.dyeMode() == PipeCoatingDyeMode.THEME_PALETTE ? ThemePaletteStats.from(source) : null;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = source.pixel(x, y);
                int alpha = pixel >>> 24 & 0xFF;
                if (alpha == 0) {
                    result.setPixel(x, y, pixel);
                    continue;
                }
                result.setPixel(x, y, applyMode(pixel, selection, stats, themeStats));
            }
        }
        return result;
    }

    private static int applyMode(int pixel, PipeCoatingSelection selection, PaletteStats stats, ThemePaletteStats themeStats) {
        int alpha = pixel >>> 24 & 0xFF;
        double r = ((pixel >>> 16) & 0xFF) / 255.0D;
        double g = ((pixel >>> 8) & 0xFF) / 255.0D;
        double b = (pixel & 0xFF) / 255.0D;
        double luma = luma(r, g, b);
        Hsv base = rgbToHsv(r, g, b);
        Hsv primary = rgbToHsv(selection.dyeColor());

        return switch (selection.dyeMode()) {
            case ORIGINAL -> pixel;
            case MULTIPLY -> argb(alpha,
                    r * channel(selection.dyeColor(), 16),
                    g * channel(selection.dyeColor(), 8),
                    b * channel(selection.dyeColor(), 0));
            case SMART_RECOLOR -> {
                double v = clamp(0.18D + luma * 1.08D);
                double s = clamp(primary.s() * (0.72D + base.s() * 0.38D));
                yield argb(alpha, hsvToRgb(primary.h(), s, v));
            }
            case HUE_SHIFT -> {
                double hueDelta = primary.h() - stats.averageHue();
                yield argb(alpha, hsvToRgb(wrapHue(base.h() + hueDelta), base.s(), base.v()));
            }
            case DUOTONE -> {
                double t = smooth(stats.normalizedLuma(luma));
                yield argb(alpha, mixColor(selection.secondaryDyeColor(), selection.dyeColor(), t));
            }
            case TRITONE -> {
                double t = smooth(stats.normalizedLuma(luma));
                if (t < 0.5D) {
                    yield argb(alpha, mixColor(selection.secondaryDyeColor(), selection.dyeColor(), t * 2.0D));
                }
                yield argb(alpha, mixColor(selection.dyeColor(), selection.tertiaryDyeColor(), (t - 0.5D) * 2.0D));
            }
            case ACCENT_PRESERVE -> {
                double tintAmount = clamp((0.72D - base.s()) / 0.72D);
                int recolored = argb(alpha, mixColor(selection.secondaryDyeColor(), selection.dyeColor(), smooth(stats.normalizedLuma(luma))));
                yield mixArgb(pixel, recolored, tintAmount);
            }
            case THEME_PALETTE -> {
                ThemePaletteStats safeThemeStats = themeStats == null ? ThemePaletteStats.fromPixel(pixel) : themeStats;
                ThemeCluster cluster = safeThemeStats.clusterFor(pixel);
                int target = safeThemeStats.targetColor(cluster, selection.dyeColors());
                int recolored = themedPixel(pixel, alpha, luma, base, target, cluster, stats, selection.textureStrength());
                if (selection.preserveAccents()) {
                    double threshold = 0.72D - selection.accentSensitivity() * 0.42D;
                    double preserve = smooth((cluster.accentScore() - threshold) / Math.max(0.08D, 1.0D - threshold));
                    if (selection.dyeColors().size() > 1) {
                        preserve *= 0.66D;
                    }
                    recolored = mixArgb(recolored, pixel, preserve * 0.58D);
                }
                yield recolored;
            }
        };
    }

    private static void trimSourceCache() {
        while (SOURCE_CACHE.size() > MAX_SOURCE_IMAGES) {
            Identifier eldest = SOURCE_CACHE.keySet().iterator().next();
            SOURCE_CACHE.remove(eldest);
        }
    }

    private static String sha1(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8 && i < bytes.length; i++) {
                builder.append(String.format("%02x", bytes[i] & 0xFF));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ignored) {
            return Integer.toUnsignedString(input.hashCode(), 16);
        }
    }

    private static double channel(int color, int shift) {
        return ((color >>> shift) & 0xFF) / 255.0D;
    }

    private static double luma(double r, double g, double b) {
        return r * 0.299D + g * 0.587D + b * 0.114D;
    }

    private static int argb(int alpha, double[] rgb) {
        return argb(alpha, rgb[0], rgb[1], rgb[2]);
    }

    private static int argb(int alpha, double r, double g, double b) {
        int red = (int) Math.round(clamp(r) * 255.0D);
        int green = (int) Math.round(clamp(g) * 255.0D);
        int blue = (int) Math.round(clamp(b) * 255.0D);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int mixArgb(int a, int b, double t) {
        return argb(
                (int) Math.round(mix((a >>> 24) & 0xFF, (b >>> 24) & 0xFF, t)),
                mix(channel(a, 16), channel(b, 16), t),
                mix(channel(a, 8), channel(b, 8), t),
                mix(channel(a, 0), channel(b, 0), t));
    }

    private static double[] mixColor(int a, int b, double t) {
        return new double[] {
                mix(channel(a, 16), channel(b, 16), t),
                mix(channel(a, 8), channel(b, 8), t),
                mix(channel(a, 0), channel(b, 0), t)
        };
    }

    private static double mix(double a, double b, double t) {
        double clamped = clamp(t);
        return a + (b - a) * clamped;
    }

    private static double smooth(double value) {
        double t = clamp(value);
        return t * t * (3.0D - 2.0D * t);
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static double wrapHue(double hue) {
        double wrapped = hue % 1.0D;
        return wrapped < 0.0D ? wrapped + 1.0D : wrapped;
    }

    private static Hsv rgbToHsv(int color) {
        return rgbToHsv(channel(color, 16), channel(color, 8), channel(color, 0));
    }

    private static Hsv rgbToHsv(double r, double g, double b) {
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double delta = max - min;
        double hue;
        if (delta <= 1.0E-6D) {
            hue = 0.0D;
        } else if (max == r) {
            hue = ((g - b) / delta) % 6.0D;
        } else if (max == g) {
            hue = (b - r) / delta + 2.0D;
        } else {
            hue = (r - g) / delta + 4.0D;
        }
        hue = wrapHue(hue / 6.0D);
        double saturation = max <= 1.0E-6D ? 0.0D : delta / max;
        return new Hsv(hue, saturation, max);
    }

    private static double[] hsvToRgb(double h, double s, double v) {
        double hue = wrapHue(h) * 6.0D;
        int i = (int) Math.floor(hue);
        double f = hue - i;
        double p = v * (1.0D - s);
        double q = v * (1.0D - f * s);
        double t = v * (1.0D - (1.0D - f) * s);
        return switch (Math.floorMod(i, 6)) {
            case 0 -> new double[] { v, t, p };
            case 1 -> new double[] { q, v, p };
            case 2 -> new double[] { p, v, t };
            case 3 -> new double[] { p, q, v };
            case 4 -> new double[] { t, p, v };
            default -> new double[] { v, p, q };
        };
    }

    private static int themedPixel(int pixel, int alpha, double luma, Hsv base, int target, ThemeCluster cluster, PaletteStats stats, double textureStrength) {
        Hsv targetHsv = rgbToHsv(target);
        double normalizedLuma = stats.normalizedLuma(luma);
        double localContrast = clampSigned((luma - cluster.luma()) / Math.max(0.055D, cluster.lumaSpread()));
        double strength = clamp(textureStrength);
        double value = clamp(targetHsv.v() + localContrast * 0.32D * strength + (normalizedLuma - 0.5D) * 0.16D * strength);
        double saturation = clamp(targetHsv.s() * (0.72D + base.s() * 0.30D) + cluster.chroma() * 0.08D * strength);
        return argb(alpha, hsvToRgb(targetHsv.h(), saturation, value));
    }

    private static double clampSigned(double value) {
        return Math.max(-1.0D, Math.min(1.0D, value));
    }

    private static Oklab oklab(int color) {
        return oklab(channel(color, 16), channel(color, 8), channel(color, 0));
    }

    private static Oklab oklab(double r, double g, double b) {
        double lr = srgbToLinear(r);
        double lg = srgbToLinear(g);
        double lb = srgbToLinear(b);
        double l = Math.cbrt(0.4122214708D * lr + 0.5363325363D * lg + 0.0514459929D * lb);
        double m = Math.cbrt(0.2119034982D * lr + 0.6806995451D * lg + 0.1073969566D * lb);
        double s = Math.cbrt(0.0883024619D * lr + 0.2817188376D * lg + 0.6299787005D * lb);
        return new Oklab(
                0.2104542553D * l + 0.7936177850D * m - 0.0040720468D * s,
                1.9779984951D * l - 2.4285922050D * m + 0.4505937099D * s,
                0.0259040371D * l + 0.7827717662D * m - 0.8086757660D * s);
    }

    private static double srgbToLinear(double channel) {
        return channel <= 0.04045D ? channel / 12.92D : Math.pow((channel + 0.055D) / 1.055D, 2.4D);
    }

    private static double oklabDistance(Oklab first, Oklab second) {
        double dl = (first.l() - second.l()) * 1.25D;
        double da = first.a() - second.a();
        double db = first.b() - second.b();
        return dl * dl + da * da + db * db;
    }

    record ThemePaletteSuggestion(List<Integer> colors, boolean preserveAccents) {
        ThemePaletteSuggestion {
            colors = List.copyOf(colors);
        }
    }

    private record TextureEntry(Identifier textureId, DynamicTexture texture) {}

    private record Hsv(double h, double s, double v) {}

    private record Oklab(double l, double a, double b) {}

    private record ThemeSample(int color, Oklab lab, double luma, double chroma, double weight) {}

    private record ThemeCluster(Oklab lab, double luma, double lumaSpread, double chroma, double coverage, double accentScore, int averageColor) {}

    private record ThemePaletteStats(List<ThemeCluster> clusters, double averageLuma, double totalWeight) {
        static ThemePaletteStats from(SourcePixels image) {
            List<ThemeSample> samples = new ArrayList<>();
            double totalWeight = 0.0D;
            double lumaWeight = 0.0D;
            for (int y = 0; y < image.height(); y++) {
                for (int x = 0; x < image.width(); x++) {
                    int pixel = image.pixel(x, y);
                    int alpha = pixel >>> 24 & 0xFF;
                    if (alpha <= 8) {
                        continue;
                    }
                    double r = channel(pixel, 16);
                    double g = channel(pixel, 8);
                    double b = channel(pixel, 0);
                    Oklab lab = oklab(r, g, b);
                    double luma = luma(r, g, b);
                    double chroma = Math.hypot(lab.a(), lab.b());
                    double weight = alpha / 255.0D * (0.55D + chroma * 1.85D);
                    samples.add(new ThemeSample(pixel, lab, luma, chroma, weight));
                    totalWeight += weight;
                    lumaWeight += luma * weight;
                }
            }
            if (samples.isEmpty()) {
                return fromPixel(PipeCoatingSelection.DEFAULT_DYE_COLOR);
            }
            List<ThemeCluster> clusters = cluster(samples, totalWeight);
            double averageLuma = lumaWeight / Math.max(1.0E-6D, totalWeight);
            return new ThemePaletteStats(clusters, averageLuma, totalWeight);
        }

        static ThemePaletteStats fromPixel(int pixel) {
            Oklab lab = oklab(pixel);
            Hsv hsv = rgbToHsv(pixel);
            double luma = luma(channel(pixel, 16), channel(pixel, 8), channel(pixel, 0));
            ThemeCluster cluster = new ThemeCluster(lab, luma, 0.12D, Math.hypot(lab.a(), lab.b()), 1.0D, hsv.s() * 0.55D, 0xFF000000 | pixel & 0x00FFFFFF);
            return new ThemePaletteStats(List.of(cluster), luma, 1.0D);
        }

        ThemeCluster clusterFor(int pixel) {
            Oklab lab = oklab(pixel);
            ThemeCluster best = this.clusters.getFirst();
            double bestDistance = Double.MAX_VALUE;
            for (ThemeCluster cluster : this.clusters) {
                double distance = oklabDistance(lab, cluster.lab());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = cluster;
                }
            }
            return best;
        }

        int targetColor(ThemeCluster cluster, List<Integer> targetColors) {
            List<Integer> colors = targetColors == null || targetColors.isEmpty() ? List.of(PipeCoatingSelection.DEFAULT_DYE_COLOR) : targetColors;
            int count = colors.size();
            if (count == 1) {
                return colors.getFirst();
            }
            if (count == 2) {
                return cluster.accentScore() > 0.54D || cluster.coverage() < 0.16D && cluster.chroma() > 0.065D ? colors.get(1) : colors.getFirst();
            }
            if (count == 3) {
                if (cluster.luma() < Math.min(0.38D, this.averageLuma - 0.12D)) {
                    return colors.getFirst();
                }
                if (cluster.luma() > Math.max(0.70D, this.averageLuma + 0.16D)) {
                    return colors.get(2);
                }
                return colors.get(1);
            }
            if (count == 4) {
                if (cluster.luma() < Math.min(0.34D, this.averageLuma - 0.14D)) {
                    return colors.getFirst();
                }
                if (cluster.luma() > Math.max(0.76D, this.averageLuma + 0.18D)) {
                    return colors.get(3);
                }
                return cluster.accentScore() > 0.58D ? colors.get(2) : colors.get(1);
            }
            if (cluster.luma() < Math.min(0.30D, this.averageLuma - 0.16D)) {
                return colors.getFirst();
            }
            if (cluster.luma() > Math.max(0.80D, this.averageLuma + 0.20D)) {
                return colors.get(4);
            }
            if (cluster.accentScore() > 0.60D) {
                return colors.get(3);
            }
            return cluster.luma() > this.averageLuma + 0.08D ? colors.get(2) : colors.get(1);
        }

        boolean hasStrongAccents() {
            return this.clusters.stream().anyMatch(cluster -> cluster.accentScore() > 0.58D);
        }

        List<Integer> suggestedColors() {
            List<ThemeCluster> significant = this.clusters.stream()
                    .filter(cluster -> cluster.coverage() > 0.055D || cluster.accentScore() > 0.52D)
                    .sorted(Comparator.comparingDouble(ThemeCluster::luma))
                    .toList();
            if (significant.isEmpty()) {
                return List.of(this.clusters.getFirst().averageColor());
            }
            int count = Math.max(1, Math.min(PipeCoatingSelection.MAX_DYE_COLORS, significant.size()));
            if (count > 3 && significant.stream().noneMatch(cluster -> cluster.accentScore() > 0.55D)) {
                count = 3;
            }
            List<Integer> colors = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int sourceIndex = count == 1 ? significant.size() / 2 : (int) Math.round(i * (significant.size() - 1) / (double) (count - 1));
                int color = significant.get(sourceIndex).averageColor();
                if (colors.stream().noneMatch(existing -> colorDistance(existing, color) < 0.018D)) {
                    colors.add(color);
                }
            }
            if (colors.isEmpty()) {
                colors.add(significant.get(significant.size() / 2).averageColor());
            }
            return List.copyOf(colors);
        }

        private static List<ThemeCluster> cluster(List<ThemeSample> samples, double totalWeight) {
            int clusterCount = Math.min(PipeCoatingSelection.MAX_DYE_COLORS, Math.max(1, samples.size() / 28 + 1));
            clusterCount = Math.min(clusterCount, samples.size());
            List<Oklab> centers = initialCenters(samples, clusterCount);
            int[] assignments = new int[samples.size()];
            Arrays.fill(assignments, -1);
            for (int iteration = 0; iteration < 9; iteration++) {
                double[] l = new double[centers.size()];
                double[] a = new double[centers.size()];
                double[] b = new double[centers.size()];
                double[] weights = new double[centers.size()];
                for (int i = 0; i < samples.size(); i++) {
                    ThemeSample sample = samples.get(i);
                    int assigned = nearestCenter(sample.lab(), centers);
                    assignments[i] = assigned;
                    double weight = sample.weight();
                    l[assigned] += sample.lab().l() * weight;
                    a[assigned] += sample.lab().a() * weight;
                    b[assigned] += sample.lab().b() * weight;
                    weights[assigned] += weight;
                }
                for (int i = 0; i < centers.size(); i++) {
                    if (weights[i] > 1.0E-6D) {
                        centers.set(i, new Oklab(l[i] / weights[i], a[i] / weights[i], b[i] / weights[i]));
                    }
                }
            }
            List<ThemeCluster> clusters = new ArrayList<>();
            for (int cluster = 0; cluster < centers.size(); cluster++) {
                double weight = 0.0D;
                double luma = 0.0D;
                double lumaSq = 0.0D;
                double chroma = 0.0D;
                double red = 0.0D;
                double green = 0.0D;
                double blue = 0.0D;
                for (int i = 0; i < samples.size(); i++) {
                    if (assignments[i] != cluster) {
                        continue;
                    }
                    ThemeSample sample = samples.get(i);
                    double sampleWeight = sample.weight();
                    weight += sampleWeight;
                    luma += sample.luma() * sampleWeight;
                    lumaSq += sample.luma() * sample.luma() * sampleWeight;
                    chroma += sample.chroma() * sampleWeight;
                    red += channel(sample.color(), 16) * sampleWeight;
                    green += channel(sample.color(), 8) * sampleWeight;
                    blue += channel(sample.color(), 0) * sampleWeight;
                }
                if (weight <= 1.0E-6D) {
                    continue;
                }
                double averageLuma = luma / weight;
                double spread = Math.sqrt(Math.max(0.004D, lumaSq / weight - averageLuma * averageLuma));
                double averageChroma = chroma / weight;
                double coverage = weight / Math.max(1.0E-6D, totalWeight);
                int averageColor = argb(0xFF, red / weight, green / weight, blue / weight);
                double accent = clamp(averageChroma * 3.1D + (coverage < 0.18D ? 0.24D : 0.0D) + (averageLuma > 0.78D ? 0.10D : 0.0D));
                clusters.add(new ThemeCluster(centers.get(cluster), averageLuma, spread, averageChroma, coverage, accent, averageColor));
            }
            List<ThemeCluster> merged = mergeCloseClusters(clusters);
            merged.sort(Comparator.comparingDouble(ThemeCluster::coverage).reversed());
            return List.copyOf(merged.isEmpty() ? List.of(new ThemeCluster(oklab(PipeCoatingSelection.DEFAULT_DYE_COLOR), 1.0D, 0.12D, 0.0D, 1.0D, 0.0D, PipeCoatingSelection.DEFAULT_DYE_COLOR)) : merged);
        }

        private static List<Oklab> initialCenters(List<ThemeSample> samples, int clusterCount) {
            List<Oklab> centers = new ArrayList<>();
            ThemeSample first = samples.stream()
                    .max(Comparator.comparingDouble(sample -> sample.weight() * (0.65D + sample.chroma() * 2.0D)))
                    .orElse(samples.getFirst());
            centers.add(first.lab());
            while (centers.size() < clusterCount) {
                ThemeSample next = samples.stream()
                        .max(Comparator.comparingDouble(sample -> nearestDistance(sample.lab(), centers) * sample.weight()))
                        .orElse(samples.getFirst());
                centers.add(next.lab());
            }
            return centers;
        }

        private static int nearestCenter(Oklab lab, List<Oklab> centers) {
            int best = 0;
            double bestDistance = Double.MAX_VALUE;
            for (int i = 0; i < centers.size(); i++) {
                double distance = oklabDistance(lab, centers.get(i));
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = i;
                }
            }
            return best;
        }

        private static double nearestDistance(Oklab lab, List<Oklab> centers) {
            double best = Double.MAX_VALUE;
            for (Oklab center : centers) {
                best = Math.min(best, oklabDistance(lab, center));
            }
            return best;
        }

        private static List<ThemeCluster> mergeCloseClusters(List<ThemeCluster> clusters) {
            List<ThemeCluster> result = new ArrayList<>(clusters);
            boolean mergedAny = true;
            while (mergedAny) {
                mergedAny = false;
                outer:
                for (int i = 0; i < result.size(); i++) {
                    for (int j = i + 1; j < result.size(); j++) {
                        if (oklabDistance(result.get(i).lab(), result.get(j).lab()) < 0.0032D) {
                            result.set(i, merge(result.get(i), result.get(j)));
                            result.remove(j);
                            mergedAny = true;
                            break outer;
                        }
                    }
                }
            }
            return result;
        }

        private static ThemeCluster merge(ThemeCluster first, ThemeCluster second) {
            double weight = first.coverage() + second.coverage();
            double firstWeight = first.coverage() / Math.max(1.0E-6D, weight);
            double secondWeight = 1.0D - firstWeight;
            Oklab lab = new Oklab(
                    first.lab().l() * firstWeight + second.lab().l() * secondWeight,
                    first.lab().a() * firstWeight + second.lab().a() * secondWeight,
                    first.lab().b() * firstWeight + second.lab().b() * secondWeight);
            return new ThemeCluster(
                    lab,
                    first.luma() * firstWeight + second.luma() * secondWeight,
                    Math.max(first.lumaSpread(), second.lumaSpread()),
                    first.chroma() * firstWeight + second.chroma() * secondWeight,
                    weight,
                    Math.max(first.accentScore(), second.accentScore()),
                    mixArgb(first.averageColor(), second.averageColor(), secondWeight));
        }

        private static double colorDistance(int first, int second) {
            return oklabDistance(oklab(first), oklab(second));
        }
    }

    private record SourcePixels(int width, int height, int[] pixels) {
        int pixel(int x, int y) {
            return this.pixels[y * this.width + x];
        }

        boolean hasPartialTransparency() {
            for (int pixel : this.pixels) {
                int alpha = pixel >>> 24 & 0xFF;
                if (alpha > 0 && alpha < 255) {
                    return true;
                }
            }
            return false;
        }
    }

    private record PaletteStats(double minLuma, double maxLuma, double averageHue, double lowHue, double midHue, double highHue) {
        static PaletteStats from(SourcePixels image) {
            double min = 1.0D;
            double max = 0.0D;
            double sumSin = 0.0D;
            double sumCos = 0.0D;
            double lowSin = 0.0D;
            double lowCos = 0.0D;
            double midSin = 0.0D;
            double midCos = 0.0D;
            double highSin = 0.0D;
            double highCos = 0.0D;
            double lowWeight = 0.0D;
            double midWeight = 0.0D;
            double highWeight = 0.0D;
            double totalWeight = 0.0D;
            for (int y = 0; y < image.height(); y++) {
                for (int x = 0; x < image.width(); x++) {
                    int pixel = image.pixel(x, y);
                    int alpha = pixel >>> 24 & 0xFF;
                    if (alpha == 0) {
                        continue;
                    }
                    Hsv hsv = rgbToHsv(pixel);
                    double luma = luma(channel(pixel, 16), channel(pixel, 8), channel(pixel, 0));
                    min = Math.min(min, luma);
                    max = Math.max(max, luma);
                    double weight = alpha / 255.0D * Math.max(0.08D, hsv.s());
                    double sin = Math.sin(hsv.h() * Math.PI * 2.0D) * weight;
                    double cos = Math.cos(hsv.h() * Math.PI * 2.0D) * weight;
                    sumSin += sin;
                    sumCos += cos;
                    totalWeight += weight;
                    if (luma < 0.36D) {
                        lowSin += sin;
                        lowCos += cos;
                        lowWeight += weight;
                    } else if (luma < 0.68D) {
                        midSin += sin;
                        midCos += cos;
                        midWeight += weight;
                    } else {
                        highSin += sin;
                        highCos += cos;
                        highWeight += weight;
                    }
                }
            }
            double avg = hueFromVector(sumSin, sumCos, totalWeight, 0.0D);
            return new PaletteStats(
                    min,
                    Math.max(min + 0.02D, max),
                    avg,
                    hueFromVector(lowSin, lowCos, lowWeight, avg),
                    hueFromVector(midSin, midCos, midWeight, avg),
                    hueFromVector(highSin, highCos, highWeight, avg));
        }

        double normalizedLuma(double luma) {
            return clamp((luma - this.minLuma) / Math.max(0.02D, this.maxLuma - this.minLuma));
        }

        int paletteBand(int pixel) {
            Hsv hsv = rgbToHsv(pixel);
            double low = hueDistance(hsv.h(), this.lowHue);
            double mid = hueDistance(hsv.h(), this.midHue);
            double high = hueDistance(hsv.h(), this.highHue);
            if (low <= mid && low <= high) {
                return 0;
            }
            return mid <= high ? 1 : 2;
        }

        private static double hueFromVector(double sin, double cos, double weight, double fallback) {
            if (weight <= 1.0E-6D) {
                return fallback;
            }
            return wrapHue(Math.atan2(sin, cos) / (Math.PI * 2.0D));
        }

        private static double hueDistance(double a, double b) {
            double delta = Math.abs(a - b);
            return Math.min(delta, 1.0D - delta);
        }
    }
}
