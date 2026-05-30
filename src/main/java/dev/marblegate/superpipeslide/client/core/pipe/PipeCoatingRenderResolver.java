package dev.marblegate.superpipeslide.client.core.pipe;

import dev.marblegate.superpipeslide.common.core.appearance.coating.PipeCoatingDyeMode;
import dev.marblegate.superpipeslide.common.core.appearance.coating.PipeCoatingSelection;
import dev.marblegate.superpipeslide.common.core.appearance.coating.PipeCoatingTexturePick;
import dev.marblegate.superpipeslide.common.core.appearance.storage.PipeAppearanceDefinitions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class PipeCoatingRenderResolver {
    private static final Map<String, ResolvedPipeCoating> CACHE = new LinkedHashMap<>();
    private static final Map<Identifier, BlockTextureModelProfile> MODEL_PROFILE_CACHE = new LinkedHashMap<>();
    private static final RandomSource MODEL_RANDOM = RandomSource.create(0x5F3759DFL);

    private PipeCoatingRenderResolver() {}

    public static ResolvedPipeCoating resolve(PipeCoatingSelection selection) {
        PipeCoatingSelection normalized = PipeAppearanceDefinitions.normalizeSelection(selection);
        String key = normalized.contentKey();
        ResolvedPipeCoating cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        ResolvedPipeCoating resolved = resolveUncached(normalized);
        CACHE.put(key, resolved);
        return resolved;
    }

    public static void clear() {
        CACHE.clear();
        MODEL_PROFILE_CACHE.clear();
        PipeCoatingDynamicTextureCache.clear();
    }

    private static ResolvedPipeCoating resolveUncached(PipeCoatingSelection selection) {
        Block block = blockFor(selection.blockId());
        TextureAtlasSprite sprite = spriteFor(block, selection.texturePick());
        String debugName = block.getName().getString() + " / " + selection.texturePick().contentKey();
        boolean generated = selection.dyeMode() != PipeCoatingDyeMode.ORIGINAL;
        Identifier textureId = generated ? PipeCoatingDynamicTextureCache.textureFor(sprite, selection) : sprite.atlasLocation();
        boolean translucent = PipeCoatingDynamicTextureCache.hasPartialTransparency(sprite);
        return new ResolvedPipeCoating(sprite, textureId, generated, translucent, selection.dyeColor(), selection.secondaryDyeColor(), selection.tertiaryDyeColor(), selection.dyeMode(), selection.smartStrength(), debugName);
    }

    public static ThemePaletteSuggestion suggestThemePalette(PipeCoatingSelection selection) {
        PipeCoatingSelection normalized = PipeAppearanceDefinitions.normalizeSelection(selection);
        TextureAtlasSprite sprite = spriteFor(blockFor(normalized.blockId()), normalized.texturePick());
        PipeCoatingDynamicTextureCache.ThemePaletteSuggestion suggestion = PipeCoatingDynamicTextureCache.suggestThemePalette(sprite);
        return new ThemePaletteSuggestion(suggestion.colors(), suggestion.preserveAccents());
    }

    public static Block blockFor(Identifier blockId) {
        return BuiltInRegistries.BLOCK.getOptional(blockId)
                .filter(block -> block != Blocks.AIR)
                .orElse(Blocks.OAK_PLANKS);
    }

    public static TextureAtlasSprite spriteFor(Block block, PipeCoatingTexturePick pick) {
        PipeCoatingTexturePick normalizedPick = pick == null ? PipeCoatingTexturePick.AUTO : pick;
        return switch (normalizedPick.type()) {
            case FACE -> normalizedPick.face()
                    .flatMap(direction -> faceSprite(block.defaultBlockState(), direction))
                    .or(() -> normalizedPick.fallbackSprite().map(PipeCoatingRenderResolver::atlasSprite))
                    .orElseGet(() -> particleSprite(block.defaultBlockState()));
            case SPRITE -> normalizedPick.fallbackSprite()
                    .map(PipeCoatingRenderResolver::atlasSprite)
                    .orElseGet(() -> particleSprite(block.defaultBlockState()));
            case AUTO -> particleSprite(block.defaultBlockState());
        };
    }

    public static List<TextureCandidate> textureCandidates(Identifier blockId) {
        Block block = blockFor(blockId);
        BlockState state = block.defaultBlockState();
        Map<String, TextureCandidate> candidates = new LinkedHashMap<>();
        TextureAtlasSprite particle = particleSprite(state);
        Set<Identifier> shownSprites = new LinkedHashSet<>();
        shownSprites.add(spriteId(particle));
        candidates.put("auto", new TextureCandidate(
                Component.translatable("screen.superpipeslide.pipe_appearance.texture_pick.auto"),
                PipeCoatingTexturePick.AUTO,
                particle));

        Map<Identifier, TextureAtlasSprite> faceSprites = new LinkedHashMap<>();
        Map<Identifier, List<Direction>> faceDirections = new LinkedHashMap<>();
        for (Direction direction : Direction.values()) {
            faceSprite(state, direction).ifPresent(sprite -> {
                Identifier spriteId = spriteId(sprite);
                faceSprites.putIfAbsent(spriteId, sprite);
                faceDirections.computeIfAbsent(spriteId, ignored -> new ArrayList<>()).add(direction);
            });
        }
        for (Map.Entry<Identifier, TextureAtlasSprite> entry : faceSprites.entrySet()) {
            Identifier spriteId = entry.getKey();
            if (!shownSprites.add(spriteId)) {
                continue;
            }
            List<Direction> directions = faceDirections.getOrDefault(spriteId, List.of());
            if (directions.isEmpty()) {
                continue;
            }
            candidates.put("face:" + spriteId, new TextureCandidate(
                    faceLabel(directions),
                    PipeCoatingTexturePick.face(directions.getFirst()),
                    entry.getValue()));
        }

        int spriteIndex = 1;
        for (TextureAtlasSprite sprite : uniqueSprites(state)) {
            Identifier spriteId = spriteId(sprite);
            if (!shownSprites.add(spriteId)) {
                continue;
            }
            String key = "sprite:" + spriteId;
            candidates.put(key, new TextureCandidate(
                    Component.translatable("screen.superpipeslide.pipe_appearance.texture_pick.sprite", spriteIndex++),
                    PipeCoatingTexturePick.sprite(spriteId),
                    sprite));
        }

        return List.copyOf(candidates.values());
    }

    public static BlockTextureModelProfile modelProfile(Identifier blockId) {
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(blockId)
                .filter(candidate -> candidate != Blocks.AIR);
        if (block.isEmpty()) {
            return BlockTextureModelProfile.unresolved();
        }
        Identifier normalizedId = BuiltInRegistries.BLOCK.getKey(block.get());
        BlockTextureModelProfile cached = MODEL_PROFILE_CACHE.get(normalizedId);
        if (cached != null) {
            return cached;
        }
        BlockTextureModelProfile profile = scanModelProfile(block.get().defaultBlockState());
        MODEL_PROFILE_CACHE.put(normalizedId, profile);
        return profile;
    }

    public static String blockDisplayName(PipeCoatingSelection selection) {
        return blockFor(PipeAppearanceDefinitions.normalizeSelection(selection).blockId()).getName().getString();
    }

    private static TextureAtlasSprite particleSprite(BlockState state) {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(state).sprite();
    }

    private static Optional<TextureAtlasSprite> faceSprite(BlockState state, Direction direction) {
        for (BlockStateModelPart part : modelParts(state)) {
            List<BakedQuad> quads = part.getQuads(direction);
            for (BakedQuad quad : quads) {
                if (quad.materialInfo().sprite() != null) {
                    return Optional.of(quad.materialInfo().sprite());
                }
            }
        }
        return Optional.empty();
    }

    private static List<TextureAtlasSprite> uniqueSprites(BlockState state) {
        Map<Identifier, TextureAtlasSprite> sprites = new LinkedHashMap<>();
        for (BlockStateModelPart part : modelParts(state)) {
            collectSprites(sprites, part.getQuads(null));
            for (Direction direction : Direction.values()) {
                collectSprites(sprites, part.getQuads(direction));
            }
        }
        return new ArrayList<>(sprites.values());
    }

    private static BlockTextureModelProfile scanModelProfile(BlockState state) {
        List<BlockStateModelPart> parts = modelParts(state);
        Set<Identifier> sprites = new LinkedHashSet<>();
        int unculledQuads = 0;
        int faceQuads = 0;
        int missingFaces = 0;
        for (BlockStateModelPart part : parts) {
            List<BakedQuad> unculled = part.getQuads(null);
            unculledQuads += unculled.size();
            collectSpriteIds(sprites, unculled);
        }
        for (Direction direction : Direction.values()) {
            int directionQuads = 0;
            for (BlockStateModelPart part : parts) {
                List<BakedQuad> quads = part.getQuads(direction);
                directionQuads += quads.size();
                collectSpriteIds(sprites, quads);
            }
            if (directionQuads == 0) {
                missingFaces++;
            }
            faceQuads += directionQuads;
        }
        boolean nonStandard = unculledQuads > 0 || missingFaces > 0 || faceQuads > Direction.values().length || sprites.size() > Direction.values().length || unculledQuads + faceQuads == 0;
        return new BlockTextureModelProfile(true, nonStandard, unculledQuads, faceQuads, missingFaces, sprites.size());
    }

    private static void collectSprites(Map<Identifier, TextureAtlasSprite> sprites, List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            TextureAtlasSprite sprite = quad.materialInfo().sprite();
            if (sprite != null) {
                sprites.putIfAbsent(spriteId(sprite), sprite);
            }
        }
    }

    private static void collectSpriteIds(Set<Identifier> sprites, List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            TextureAtlasSprite sprite = quad.materialInfo().sprite();
            if (sprite != null) {
                sprites.add(spriteId(sprite));
            }
        }
    }

    private static Identifier spriteId(TextureAtlasSprite sprite) {
        return sprite.contents().name();
    }

    private static Component faceLabel(List<Direction> directions) {
        if (directions.size() == 1) {
            Direction direction = directions.getFirst();
            return Component.translatable("screen.superpipeslide.pipe_appearance.texture_pick.face." + direction.getSerializedName());
        }
        String joined = directions.stream()
                .map(direction -> Component.translatable("screen.superpipeslide.pipe_appearance.texture_pick.face." + direction.getSerializedName()).getString())
                .collect(Collectors.joining("/"));
        return Component.literal(joined);
    }

    private static List<BlockStateModelPart> modelParts(BlockState state) {
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        MODEL_RANDOM.setSeed(0x5F3759DFL);
        model.collectParts(MODEL_RANDOM, parts);
        return parts;
    }

    private static TextureAtlasSprite atlasSprite(Identifier spriteId) {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(spriteId);
    }

    public record TextureCandidate(Component label, PipeCoatingTexturePick pick, TextureAtlasSprite sprite) {}

    public record BlockTextureModelProfile(boolean resolved, boolean nonStandardModel, int unculledQuads, int faceQuads, int missingFaces, int uniqueSprites) {
        static BlockTextureModelProfile unresolved() {
            return new BlockTextureModelProfile(false, true, 0, 0, Direction.values().length, 0);
        }
    }

    public record ThemePaletteSuggestion(List<Integer> colors, boolean preserveAccents) {
        public ThemePaletteSuggestion {
            colors = List.copyOf(colors);
        }
    }

    public record ResolvedPipeCoating(TextureAtlasSprite sprite, Identifier textureId, boolean generatedTexture, boolean translucent, int tintColor, int secondaryTintColor, int tertiaryTintColor, PipeCoatingDyeMode dyeMode, float smartStrength, String debugName) {
        public int opaqueTint() {
            return 0xFFFFFFFF;
        }

        public float u(double fraction) {
            float clamped = (float) Math.max(0.0D, Math.min(1.0D, fraction));
            return this.generatedTexture ? clamped : this.sprite.getU(clamped);
        }

        public float v(double fraction) {
            float clamped = (float) Math.max(0.0D, Math.min(1.0D, fraction));
            return this.generatedTexture ? clamped : this.sprite.getV(clamped);
        }
    }
}
