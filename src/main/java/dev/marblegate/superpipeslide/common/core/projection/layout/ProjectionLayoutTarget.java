package dev.marblegate.superpipeslide.common.core.projection.layout;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

public enum ProjectionLayoutTarget {
    STATION_NAME("screen.superpipeslide.projection.target.station_name"),
    PLATFORM("screen.superpipeslide.projection.target.platform");

    public static final Codec<ProjectionLayoutTarget> CODEC = Codec.STRING.xmap(ProjectionLayoutTarget::byName, ProjectionLayoutTarget::name);
    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectionLayoutTarget> STREAM_CODEC = NeoForgeStreamCodecs.enumCodec(ProjectionLayoutTarget.class);

    private final String translationKey;

    ProjectionLayoutTarget(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return this.translationKey;
    }

    public static ProjectionLayoutTarget byName(String name) {
        if (name != null) {
            for (ProjectionLayoutTarget target : values()) {
                if (target.name().equalsIgnoreCase(name.trim())) {
                    return target;
                }
            }
        }
        return STATION_NAME;
    }
}
