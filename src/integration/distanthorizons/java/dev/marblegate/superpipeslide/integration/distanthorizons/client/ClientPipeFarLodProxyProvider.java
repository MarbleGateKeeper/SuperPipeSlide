package dev.marblegate.superpipeslide.integration.distanthorizons.client;

import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeAppearanceCache;
import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.common.core.appearance.coating.PipeCoatingDyeMode;
import dev.marblegate.superpipeslide.common.core.appearance.coating.PipeCoatingSelection;
import dev.marblegate.superpipeslide.common.core.appearance.model.PipeAppearanceProfile;
import dev.marblegate.superpipeslide.common.core.appearance.storage.PipeAppearanceDefinitions;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeStyleDefinition;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeStyleGeometry;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeVariantDefinition;
import dev.marblegate.superpipeslide.common.core.geometry.RuntimePipeConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ClientPipeFarLodProxyProvider {
    static final int GROUP_SECTION_SIZE = 32;
    private static final double TARGET_BOX_LENGTH = 2.0D;
    private static final double MIN_PROXY_HALF_THICKNESS = 0.06D;
    private static final double MAX_PROXY_HALF_THICKNESS = 0.24D;
    private static final double MIN_BOX_THICKNESS = 0.05D;

    private static final Map<ResourceKey<Level>, PipeFarLodSnapshot> CACHE = new LinkedHashMap<>();

    private ClientPipeFarLodProxyProvider() {}

    public static Optional<PipeFarLodSnapshot> snapshot(ResourceKey<Level> levelKey) {
        long networkRevision = ClientPipeNetworkCache.aggregateRevision();
        long appearanceRevision = ClientPipeAppearanceCache.revision();
        PipeFarLodSnapshot snapshot = CACHE.get(levelKey);
        if (snapshot != null
                && snapshot.networkRevision() == networkRevision
                && snapshot.appearanceRevision() == appearanceRevision
                && ClientPipeNetworkCache.pendingRuntimeRebuilds(levelKey) == 0) {
            return Optional.of(snapshot);
        }

        Collection<RuntimePipeConnection> runtimes = ClientPipeNetworkCache.runtimeConnectionsForFarLod(levelKey);
        if (ClientPipeNetworkCache.pendingRuntimeRebuilds(levelKey) > 0) {
            return snapshot == null ? Optional.empty() : Optional.of(snapshot);
        }

        PipeFarLodSnapshot snapshotN = buildSnapshot(levelKey, networkRevision, appearanceRevision, runtimes);
        CACHE.put(levelKey, snapshotN);
        return Optional.of(snapshotN);
    }

    public static void clear() {
        CACHE.clear();
    }

    private static PipeFarLodSnapshot buildSnapshot(ResourceKey<Level> levelKey, long networkRevision, long appearanceRevision, Collection<RuntimePipeConnection> runtimes) {
        List<RuntimePipeConnection> sorted = runtimes.stream()
                .filter(runtime -> runtime.connection().levelKey().equals(levelKey))
                .sorted(Comparator.comparing(runtime -> runtime.connection().id()))
                .toList();
        Map<PipeFarLodGroupKey, List<PipeFarLodBox>> boxesByGroup = new LinkedHashMap<>();
        for (RuntimePipeConnection runtime : sorted) {
            PipeAppearanceProfile profile = ClientPipeAppearanceCache.profileFor(runtime.connection().connectionKey()).normalizedToDefinitions();
            PipeFarLodAppearance appearance = appearance(profile);
            addRuntimeBoxes(runtime, appearance, boxesByGroup);
        }

        List<PipeFarLodGroup> groups = new ArrayList<>(boxesByGroup.size());
        for (Map.Entry<PipeFarLodGroupKey, List<PipeFarLodBox>> entry : boxesByGroup.entrySet()) {
            groups.add(new PipeFarLodGroup(entry.getKey(), entry.getValue()));
        }
        return new PipeFarLodSnapshot(levelKey, networkRevision, appearanceRevision, groups);
    }

    private static PipeFarLodAppearance appearance(PipeAppearanceProfile profile) {
        PipeStyleDefinition style = PipeAppearanceDefinitions.style(profile.styleId()).orElse(PipeAppearanceDefinitions.defaultStyle());
        PipeVariantDefinition variant = PipeAppearanceDefinitions.variant(profile.variantId())
                .filter(candidate -> candidate.styleId().equals(style.id()))
                .orElseGet(() -> PipeAppearanceDefinitions.variant(style.defaultVariantId()).orElse(PipeAppearanceDefinitions.defaultVariant()));
        PipeStyleGeometry geometry = PipeStyleGeometry.resolve(style, variant, profile.styleParameters());
        PipeCoatingSelection primary = PipeAppearanceDefinitions.selectionFor(profile, style.primarySlot().id());
        boolean translucent = !profile.glow() && translucentBlock(primary.blockId());
        PipeFarLodMaterial material = materialFor(primary.blockId(), profile.glow(), translucent);
        int color = colorFor(primary, profile.glow(), translucent);
        double halfThickness = proxyHalfThickness(geometry);
        return new PipeFarLodAppearance(halfThickness, color, profile.glow(), translucent, material);
    }

    private static void addRuntimeBoxes(RuntimePipeConnection runtime, PipeFarLodAppearance appearance, Map<PipeFarLodGroupKey, List<PipeFarLodBox>> boxesByGroup) {
        int samples = runtime.sampleCount();
        if (samples < 2 || runtime.length() <= 1.0E-5D) {
            return;
        }

        for (int index = 1; index < samples; index++) {
            addSegmentBoxes(runtime.sample(index - 1), runtime.sample(index), appearance, boxesByGroup);
        }
    }

    private static void addSegmentBoxes(Vec3 from, Vec3 to, PipeFarLodAppearance appearance, Map<PipeFarLodGroupKey, List<PipeFarLodBox>> boxesByGroup) {
        if (from.distanceToSqr(to) < 1.0E-8D) {
            return;
        }

        int subdivisions = segmentSubdivisionCount(from, to, appearance.halfThickness());
        Vec3 previous = from;
        for (int part = 1; part <= subdivisions; part++) {
            Vec3 next = lerp(from, to, (double) part / subdivisions);
            if (previous.distanceToSqr(next) < 1.0E-8D) {
                previous = next;
                continue;
            }
            PipeFarLodBox box = thinBoxFor(previous, next, appearance.halfThickness(), appearance.argb());
            PipeFarLodGroupKey key = groupKey(box, appearance);
            boxesByGroup.computeIfAbsent(key, ignored -> new ArrayList<>()).add(box);
            previous = next;
        }
    }

    private static int segmentSubdivisionCount(Vec3 from, Vec3 to, double halfThickness) {
        double dx = Math.abs(to.x - from.x);
        double dy = Math.abs(to.y - from.y);
        double dz = Math.abs(to.z - from.z);
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double maxSecondaryDrift = maxSecondaryAxisDrift(dx, dy, dz);
        double allowedSecondaryDrift = halfThickness * 2.0D;
        int byLength = Math.max(1, (int) Math.ceil(length / TARGET_BOX_LENGTH));
        int byDrift = Math.max(1, (int) Math.ceil(maxSecondaryDrift / allowedSecondaryDrift));
        return Math.max(byLength, byDrift);
    }

    private static double maxSecondaryAxisDrift(double dx, double dy, double dz) {
        if (dx >= dy && dx >= dz) {
            return Math.max(dy, dz);
        }
        if (dy >= dx && dy >= dz) {
            return Math.max(dx, dz);
        }
        return Math.max(dx, dy);
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double t) {
        return new Vec3(
                from.x + (to.x - from.x) * t,
                from.y + (to.y - from.y) * t,
                from.z + (to.z - from.z) * t);
    }

    private static PipeFarLodBox thinBoxFor(Vec3 from, Vec3 to, double halfThickness, int argb) {
        double dx = Math.abs(to.x - from.x);
        double dy = Math.abs(to.y - from.y);
        double dz = Math.abs(to.z - from.z);
        double centerX = (from.x + to.x) * 0.5D;
        double centerY = (from.y + to.y) * 0.5D;
        double centerZ = (from.z + to.z) * 0.5D;

        double minX;
        double minY;
        double minZ;
        double maxX;
        double maxY;
        double maxZ;
        if (dx >= dy && dx >= dz) {
            minX = Math.min(from.x, to.x);
            maxX = Math.max(from.x, to.x);
            minY = centerY - halfThickness;
            maxY = centerY + halfThickness;
            minZ = centerZ - halfThickness;
            maxZ = centerZ + halfThickness;
        } else if (dy >= dx && dy >= dz) {
            minX = centerX - halfThickness;
            maxX = centerX + halfThickness;
            minY = Math.min(from.y, to.y);
            maxY = Math.max(from.y, to.y);
            minZ = centerZ - halfThickness;
            maxZ = centerZ + halfThickness;
        } else {
            minX = centerX - halfThickness;
            maxX = centerX + halfThickness;
            minY = centerY - halfThickness;
            maxY = centerY + halfThickness;
            minZ = Math.min(from.z, to.z);
            maxZ = Math.max(from.z, to.z);
        }

        return new PipeFarLodBox(
                ensureThicknessMin(minX, maxX),
                ensureThicknessMin(minY, maxY),
                ensureThicknessMin(minZ, maxZ),
                ensureThicknessMax(minX, maxX),
                ensureThicknessMax(minY, maxY),
                ensureThicknessMax(minZ, maxZ),
                argb);
    }

    private static double ensureThicknessMin(double min, double max) {
        if (max - min >= MIN_BOX_THICKNESS) {
            return min;
        }
        double center = (min + max) * 0.5D;
        return center - MIN_BOX_THICKNESS * 0.5D;
    }

    private static double ensureThicknessMax(double min, double max) {
        if (max - min >= MIN_BOX_THICKNESS) {
            return max;
        }
        double center = (min + max) * 0.5D;
        return center + MIN_BOX_THICKNESS * 0.5D;
    }

    private static PipeFarLodGroupKey groupKey(PipeFarLodBox box, PipeFarLodAppearance appearance) {
        int sectionX = sectionCoord((box.minX() + box.maxX()) * 0.5D);
        int sectionY = sectionCoord((box.minY() + box.maxY()) * 0.5D);
        int sectionZ = sectionCoord((box.minZ() + box.maxZ()) * 0.5D);
        return new PipeFarLodGroupKey(sectionX, sectionY, sectionZ, appearance.material(), appearance.emissive(), appearance.translucent());
    }

    private static int sectionCoord(double value) {
        return Math.floorDiv((int) Math.floor(value), GROUP_SECTION_SIZE);
    }

    private static double proxyHalfThickness(PipeStyleGeometry geometry) {
        double thickness = switch (geometry.shape()) {
            case ROUND, FACETED -> geometry.radius() * 0.62D + 0.025D;
            case BOX, MONORAIL -> Math.max(geometry.halfWidth(), geometry.halfHeight()) * 0.52D + 0.020D;
            case TRIANGLE -> Math.max(geometry.halfWidth(), geometry.depth() * 0.5D) * 0.50D + 0.025D;
            case RAIL -> Math.max(Math.max(geometry.railWidth() * 1.70D, geometry.railHeight() * 1.25D), geometry.tieWidth() * 0.80D) + 0.025D;
            case SLIDE -> Math.max(Math.max(geometry.halfWidth() * 0.36D, geometry.depth() * 0.62D), geometry.rimWidth() * 1.45D) + 0.020D;
            case COVERED -> Math.max(geometry.halfWidth() * 0.25D, geometry.rimWidth() * 2.00D + 0.070D);
        };
        return Math.max(MIN_PROXY_HALF_THICKNESS, Math.min(MAX_PROXY_HALF_THICKNESS, thickness));
    }

    private static PipeFarLodMaterial materialFor(Identifier blockId, boolean emissive, boolean translucent) {
        if (emissive) {
            return PipeFarLodMaterial.ILLUMINATED;
        }
        if (translucent) {
            return PipeFarLodMaterial.WATER;
        }

        String path = blockId.getPath().toLowerCase(Locale.ROOT);
        if (path.contains("planks") || path.contains("wood") || path.contains("log") || path.contains("stem")) {
            return PipeFarLodMaterial.WOOD;
        }
        if (path.contains("iron") || path.contains("copper") || path.contains("gold") || path.contains("metal")) {
            return PipeFarLodMaterial.METAL;
        }
        return PipeFarLodMaterial.STONE;
    }

    private static boolean translucentBlock(Identifier blockId) {
        String path = blockId.getPath().toLowerCase(Locale.ROOT);
        return path.contains("glass")
                || path.contains("ice")
                || path.contains("water");
    }

    private static int colorFor(PipeCoatingSelection selection, boolean emissive, boolean translucent) {
        int rgb;
        if (selection.dyeMode() != PipeCoatingDyeMode.ORIGINAL) {
            rgb = selection.dyeColor() & 0x00FFFFFF;
        } else {
            rgb = fallbackBlockRgb(selection.blockId());
        }

        if (emissive) {
            rgb = mix(rgb, 0xFFF2A6, 0.22D);
            return 0xFF000000 | rgb;
        }
        if (translucent) {
            return 0xB8000000 | rgb;
        }
        return 0xFF000000 | rgb;
    }

    private static int fallbackBlockRgb(Identifier blockId) {
        String path = blockId.getPath().toLowerCase(Locale.ROOT);
        if (path.contains("glowstone")) {
            return 0xF5C66A;
        }
        if (path.contains("sea_lantern")) {
            return 0xBEEFE7;
        }
        if (path.contains("shroomlight") || path.contains("lamp")) {
            return 0xF0A052;
        }
        if (path.contains("glass") || path.contains("ice")) {
            return 0xB9E4F5;
        }
        if (path.contains("iron")) {
            return 0xD8D8D2;
        }
        if (path.contains("copper")) {
            return 0xB8734F;
        }
        if (path.contains("gold")) {
            return 0xF6D56A;
        }
        if (path.contains("oak") || path.contains("planks")) {
            return 0xB88755;
        }
        if (path.contains("deepslate")) {
            return 0x4A4A52;
        }
        if (path.contains("stone")) {
            return 0x9A9A93;
        }
        if (path.contains("concrete")) {
            return 0xD7D7D2;
        }
        return 0xC6C1B5;
    }

    private static int mix(int firstRgb, int secondRgb, double secondWeight) {
        double clamped = Math.max(0.0D, Math.min(1.0D, secondWeight));
        double firstWeight = 1.0D - clamped;
        int red = (int) Math.round(((firstRgb >> 16) & 0xFF) * firstWeight + ((secondRgb >> 16) & 0xFF) * clamped);
        int green = (int) Math.round(((firstRgb >> 8) & 0xFF) * firstWeight + ((secondRgb >> 8) & 0xFF) * clamped);
        int blue = (int) Math.round((firstRgb & 0xFF) * firstWeight + (secondRgb & 0xFF) * clamped);
        return red << 16 | green << 8 | blue;
    }

    private record CacheEntry(PipeFarLodSnapshot snapshot) {}
}
