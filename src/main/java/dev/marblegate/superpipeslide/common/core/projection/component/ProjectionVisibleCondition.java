package dev.marblegate.superpipeslide.common.core.projection.component;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

public enum ProjectionVisibleCondition {
    ALWAYS,
    HAS_TRANSLATION,
    HAS_ROUTES,
    HAS_EXIT,
    MULTI_ROUTE,
    HAS_PLATFORM,
    HAS_PLATFORM_DISPLAY_NAME,
    HAS_CURRENT_LINE,
    HAS_ROUTE_LAYOUT,
    HAS_NEXT_STOP,
    HAS_PREVIOUS_STOP,
    HAS_TERMINAL,
    HAS_TRANSFERS,
    HAS_OUT_OF_STATION_TRANSFERS,
    MULTI_TRANSFER,
    BIDIRECTIONAL_LAYOUT,
    LOOP_LAYOUT;

    public static final Codec<ProjectionVisibleCondition> CODEC = Codec.STRING.xmap(ProjectionVisibleCondition::byName, ProjectionVisibleCondition::name);
    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectionVisibleCondition> STREAM_CODEC = NeoForgeStreamCodecs.enumCodec(ProjectionVisibleCondition.class);

    public static ProjectionVisibleCondition byName(String name) {
        if (name != null) {
            for (ProjectionVisibleCondition condition : values()) {
                if (condition.name().equalsIgnoreCase(name.trim())) {
                    return condition;
                }
            }
        }
        return ALWAYS;
    }
}
