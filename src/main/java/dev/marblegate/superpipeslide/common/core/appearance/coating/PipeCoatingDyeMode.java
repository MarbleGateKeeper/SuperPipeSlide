package dev.marblegate.superpipeslide.common.core.appearance.coating;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public enum PipeCoatingDyeMode {
    ORIGINAL("original"),
    MULTIPLY("multiply"),
    SMART_RECOLOR("smart_recolor"),
    HUE_SHIFT("hue_shift"),
    DUOTONE("duotone"),
    TRITONE("tritone"),
    ACCENT_PRESERVE("accent_preserve"),
    THEME_PALETTE("theme_palette");

    public static final Codec<PipeCoatingDyeMode> CODEC = Codec.STRING.xmap(PipeCoatingDyeMode::byId, PipeCoatingDyeMode::id);
    public static final StreamCodec<io.netty.buffer.ByteBuf, PipeCoatingDyeMode> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(PipeCoatingDyeMode::byId, PipeCoatingDyeMode::id);

    private final String id;

    PipeCoatingDyeMode(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public boolean usesColor() {
        return this != ORIGINAL;
    }

    public int colorSlotCount() {
        return switch (this) {
            case ORIGINAL -> 0;
            case DUOTONE, ACCENT_PRESERVE -> 2;
            case TRITONE -> 3;
            case THEME_PALETTE, MULTIPLY, SMART_RECOLOR, HUE_SHIFT -> 1;
        };
    }

    public boolean variableColorSlots() {
        return this == THEME_PALETTE;
    }

    public static PipeCoatingDyeMode byId(String id) {
        for (PipeCoatingDyeMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return ORIGINAL;
    }
}
