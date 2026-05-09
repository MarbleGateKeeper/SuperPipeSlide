package dev.marblegate.superpipeslide.common.core.projection.component;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

public enum ProjectionTextAlign {
    LEFT,
    CENTER,
    RIGHT;

    public static final Codec<ProjectionTextAlign> CODEC = Codec.STRING.xmap(ProjectionTextAlign::byName, ProjectionTextAlign::name);
    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectionTextAlign> STREAM_CODEC = NeoForgeStreamCodecs.enumCodec(ProjectionTextAlign.class);

    public static ProjectionTextAlign byName(String name) {
        if (name != null) {
            for (ProjectionTextAlign align : values()) {
                if (align.name().equalsIgnoreCase(name.trim())) {
                    return align;
                }
            }
        }
        return LEFT;
    }
}
