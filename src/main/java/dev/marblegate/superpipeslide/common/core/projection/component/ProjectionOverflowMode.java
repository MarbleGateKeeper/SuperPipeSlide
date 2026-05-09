package dev.marblegate.superpipeslide.common.core.projection.component;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

public enum ProjectionOverflowMode {
    SCALE,
    MARQUEE,
    WRAP,
    HIDE,
    PLUS_COUNT;

    public static final Codec<ProjectionOverflowMode> CODEC = Codec.STRING.xmap(ProjectionOverflowMode::byName, ProjectionOverflowMode::name);
    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectionOverflowMode> STREAM_CODEC = NeoForgeStreamCodecs.enumCodec(ProjectionOverflowMode.class);

    public static ProjectionOverflowMode byName(String name) {
        if (name != null) {
            for (ProjectionOverflowMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name.trim())) {
                    return mode;
                }
            }
        }
        return SCALE;
    }
}
