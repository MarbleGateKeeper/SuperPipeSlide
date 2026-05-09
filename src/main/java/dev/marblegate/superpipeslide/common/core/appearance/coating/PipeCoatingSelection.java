package dev.marblegate.superpipeslide.common.core.appearance.coating;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record PipeCoatingSelection(
        Identifier blockId,
        PipeCoatingTexturePick texturePick,
        PipeCoatingDyeMode dyeMode,
        List<Integer> dyeColors,
        boolean preserveAccents,
        float textureStrength,
        float accentSensitivity,
        float smartStrength
) {
    public static final Identifier DEFAULT_BLOCK_ID = Identifier.withDefaultNamespace("white_concrete");
    public static final int DEFAULT_DYE_COLOR = 0xFFFFFFFF;
    public static final int DEFAULT_SECONDARY_DYE_COLOR = 0xFF2E8CFF;
    public static final int DEFAULT_TERTIARY_DYE_COLOR = 0xFFFFD34D;
    public static final boolean DEFAULT_PRESERVE_ACCENTS = true;
    public static final float DEFAULT_TEXTURE_STRENGTH = 0.76F;
    public static final float DEFAULT_ACCENT_SENSITIVITY = 0.56F;
    public static final float DEFAULT_SMART_STRENGTH = 0.78F;
    public static final int MAX_DYE_COLORS = 5;

    public static final Codec<PipeCoatingSelection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("block", DEFAULT_BLOCK_ID).forGetter(PipeCoatingSelection::blockId),
            PipeCoatingTexturePick.CODEC.optionalFieldOf("texture_pick", PipeCoatingTexturePick.AUTO).forGetter(PipeCoatingSelection::texturePick),
            PipeCoatingDyeMode.CODEC.optionalFieldOf("dye_mode", PipeCoatingDyeMode.ORIGINAL).forGetter(PipeCoatingSelection::dyeMode),
            Codec.INT.listOf().optionalFieldOf("dye_colors", List.of(DEFAULT_DYE_COLOR)).forGetter(PipeCoatingSelection::dyeColors),
            Codec.BOOL.optionalFieldOf("preserve_accents", DEFAULT_PRESERVE_ACCENTS).forGetter(PipeCoatingSelection::preserveAccents),
            Codec.FLOAT.optionalFieldOf("texture_strength", DEFAULT_TEXTURE_STRENGTH).forGetter(PipeCoatingSelection::textureStrength),
            Codec.FLOAT.optionalFieldOf("accent_sensitivity", DEFAULT_ACCENT_SENSITIVITY).forGetter(PipeCoatingSelection::accentSensitivity),
            Codec.FLOAT.optionalFieldOf("smart_strength", DEFAULT_SMART_STRENGTH).forGetter(PipeCoatingSelection::smartStrength)
    ).apply(instance, PipeCoatingSelection::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PipeCoatingSelection> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PipeCoatingSelection decode(RegistryFriendlyByteBuf buffer) {
            Identifier blockId = Identifier.STREAM_CODEC.decode(buffer);
            PipeCoatingTexturePick texturePick = PipeCoatingTexturePick.STREAM_CODEC.decode(buffer);
            PipeCoatingDyeMode dyeMode = PipeCoatingDyeMode.STREAM_CODEC.decode(buffer);
            int colorCount = Math.max(0, Math.min(MAX_DYE_COLORS, buffer.readVarInt()));
            List<Integer> dyeColors = new ArrayList<>(colorCount);
            for (int i = 0; i < colorCount; i++) {
                dyeColors.add(buffer.readInt());
            }
            boolean preserveAccents = buffer.readBoolean();
            float textureStrength = buffer.readFloat();
            float accentSensitivity = buffer.readFloat();
            float smartStrength = buffer.readFloat();
            return new PipeCoatingSelection(blockId, texturePick, dyeMode, dyeColors, preserveAccents, textureStrength, accentSensitivity, smartStrength);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, PipeCoatingSelection selection) {
            PipeCoatingSelection normalized = selection == null ? defaultSelection() : selection;
            Identifier.STREAM_CODEC.encode(buffer, normalized.blockId());
            PipeCoatingTexturePick.STREAM_CODEC.encode(buffer, normalized.texturePick());
            PipeCoatingDyeMode.STREAM_CODEC.encode(buffer, normalized.dyeMode());
            buffer.writeVarInt(normalized.dyeColors().size());
            for (int color : normalized.dyeColors()) {
                buffer.writeInt(color);
            }
            buffer.writeBoolean(normalized.preserveAccents());
            buffer.writeFloat(normalized.textureStrength());
            buffer.writeFloat(normalized.accentSensitivity());
            buffer.writeFloat(normalized.smartStrength());
        }
    };

    public PipeCoatingSelection {
        blockId = blockId == null ? DEFAULT_BLOCK_ID : blockId;
        texturePick = texturePick == null ? PipeCoatingTexturePick.AUTO : texturePick;
        dyeMode = dyeMode == null ? PipeCoatingDyeMode.ORIGINAL : dyeMode;
        dyeColors = normalizeDyeColors(dyeColors);
        textureStrength = clampFloat(textureStrength, DEFAULT_TEXTURE_STRENGTH);
        accentSensitivity = clampFloat(accentSensitivity, DEFAULT_ACCENT_SENSITIVITY);
        smartStrength = clampFloat(smartStrength, DEFAULT_SMART_STRENGTH);
    }

    public static PipeCoatingSelection defaultSelection() {
        return original(DEFAULT_BLOCK_ID);
    }

    public static PipeCoatingSelection original(Identifier blockId) {
        return new PipeCoatingSelection(blockId, PipeCoatingTexturePick.AUTO, PipeCoatingDyeMode.ORIGINAL, List.of(DEFAULT_DYE_COLOR), DEFAULT_PRESERVE_ACCENTS, DEFAULT_TEXTURE_STRENGTH, DEFAULT_ACCENT_SENSITIVITY, DEFAULT_SMART_STRENGTH);
    }

    public static PipeCoatingSelection multiply(Identifier blockId, int dyeColor) {
        return new PipeCoatingSelection(blockId, PipeCoatingTexturePick.AUTO, PipeCoatingDyeMode.MULTIPLY, List.of(opaque(dyeColor)), DEFAULT_PRESERVE_ACCENTS, DEFAULT_TEXTURE_STRENGTH, DEFAULT_ACCENT_SENSITIVITY, DEFAULT_SMART_STRENGTH);
    }

    public static PipeCoatingSelection smart(Identifier blockId, int dyeColor) {
        return new PipeCoatingSelection(blockId, PipeCoatingTexturePick.AUTO, PipeCoatingDyeMode.SMART_RECOLOR, List.of(opaque(dyeColor)), DEFAULT_PRESERVE_ACCENTS, DEFAULT_TEXTURE_STRENGTH, DEFAULT_ACCENT_SENSITIVITY, DEFAULT_SMART_STRENGTH);
    }

    public int dyeColor() {
        return colorAt(0, DEFAULT_DYE_COLOR);
    }

    public int secondaryDyeColor() {
        return colorAt(1, DEFAULT_SECONDARY_DYE_COLOR);
    }

    public int tertiaryDyeColor() {
        return colorAt(2, DEFAULT_TERTIARY_DYE_COLOR);
    }

    public PipeCoatingSelection withBlock(Identifier blockId) {
        return new PipeCoatingSelection(blockId, PipeCoatingTexturePick.AUTO, this.dyeMode, this.dyeColors, this.preserveAccents, this.textureStrength, this.accentSensitivity, this.smartStrength);
    }

    public PipeCoatingSelection withTexturePick(PipeCoatingTexturePick texturePick) {
        return new PipeCoatingSelection(this.blockId, texturePick, this.dyeMode, this.dyeColors, this.preserveAccents, this.textureStrength, this.accentSensitivity, this.smartStrength);
    }

    public PipeCoatingSelection withDyeMode(PipeCoatingDyeMode dyeMode) {
        return new PipeCoatingSelection(this.blockId, this.texturePick, dyeMode, this.dyeColors, this.preserveAccents, this.textureStrength, this.accentSensitivity, this.smartStrength);
    }

    public PipeCoatingSelection withDyeColor(int dyeColor) {
        return withColorSlot(0, dyeColor);
    }

    public PipeCoatingSelection withSecondaryDyeColor(int secondaryDyeColor) {
        return withColorSlot(1, secondaryDyeColor);
    }

    public PipeCoatingSelection withTertiaryDyeColor(int tertiaryDyeColor) {
        return withColorSlot(2, tertiaryDyeColor);
    }

    public PipeCoatingSelection withColorSlot(int slot, int color) {
        int index = Math.max(0, Math.min(MAX_DYE_COLORS - 1, slot));
        List<Integer> colors = new ArrayList<>(this.dyeColors);
        while (colors.size() <= index) {
            colors.add(defaultColorForIndex(colors.size()));
        }
        colors.set(index, opaque(color));
        return withDyeColors(colors);
    }

    public PipeCoatingSelection withDyeColors(List<Integer> dyeColors) {
        return new PipeCoatingSelection(this.blockId, this.texturePick, this.dyeMode, dyeColors, this.preserveAccents, this.textureStrength, this.accentSensitivity, this.smartStrength);
    }

    public PipeCoatingSelection withPreserveAccents(boolean preserveAccents) {
        return new PipeCoatingSelection(this.blockId, this.texturePick, this.dyeMode, this.dyeColors, preserveAccents, this.textureStrength, this.accentSensitivity, this.smartStrength);
    }

    public PipeCoatingSelection withTextureStrength(float textureStrength) {
        return new PipeCoatingSelection(this.blockId, this.texturePick, this.dyeMode, this.dyeColors, this.preserveAccents, textureStrength, this.accentSensitivity, this.smartStrength);
    }

    public PipeCoatingSelection withAccentSensitivity(float accentSensitivity) {
        return new PipeCoatingSelection(this.blockId, this.texturePick, this.dyeMode, this.dyeColors, this.preserveAccents, this.textureStrength, accentSensitivity, this.smartStrength);
    }

    public PipeCoatingSelection withSmartStrength(float smartStrength) {
        return new PipeCoatingSelection(this.blockId, this.texturePick, this.dyeMode, this.dyeColors, this.preserveAccents, this.textureStrength, this.accentSensitivity, smartStrength);
    }

    public String contentKey() {
        StringBuilder builder = new StringBuilder();
        builder.append(this.blockId)
                .append('|').append(this.texturePick.contentKey())
                .append('|').append(this.dyeMode.id())
                .append('|').append(this.preserveAccents)
                .append('|').append(this.textureStrength)
                .append('|').append(this.accentSensitivity)
                .append('|').append(this.smartStrength);
        for (int color : this.dyeColors) {
            builder.append('|').append(Integer.toHexString(color));
        }
        return builder.toString();
    }

    private int colorAt(int index, int fallback) {
        if (index >= 0 && index < this.dyeColors.size()) {
            return this.dyeColors.get(index);
        }
        return fallback;
    }

    private static List<Integer> normalizeDyeColors(List<Integer> colors) {
        List<Integer> normalized = new ArrayList<>();
        if (colors != null) {
            for (int color : colors) {
                if (normalized.size() >= MAX_DYE_COLORS) {
                    break;
                }
                normalized.add(opaque(color));
            }
        }
        if (normalized.isEmpty()) {
            normalized.add(DEFAULT_DYE_COLOR);
        }
        return List.copyOf(normalized);
    }

    private static int defaultColorForIndex(int index) {
        return switch (index) {
            case 1 -> DEFAULT_SECONDARY_DYE_COLOR;
            case 2 -> DEFAULT_TERTIARY_DYE_COLOR;
            case 3 -> 0xFFB7FF66;
            case 4 -> 0xFFFF7A7A;
            default -> DEFAULT_DYE_COLOR;
        };
    }

    private static int opaque(int color) {
        return 0xFF000000 | color & 0x00FFFFFF;
    }

    private static float clampFloat(float value, float fallback) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : fallback;
    }
}
