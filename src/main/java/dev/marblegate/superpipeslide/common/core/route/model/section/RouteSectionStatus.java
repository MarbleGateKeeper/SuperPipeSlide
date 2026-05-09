package dev.marblegate.superpipeslide.common.core.route.model.section;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

public enum RouteSectionStatus implements StringRepresentable {
    VALID("valid"),
    DISABLED("disabled"),
    STALE("stale"),
    INCOMPLETE("incomplete"),
    BROKEN("broken"),
    AMBIGUOUS("ambiguous");

    public static final Codec<RouteSectionStatus> CODEC = StringRepresentable.fromEnum(RouteSectionStatus::values);
    public static final StreamCodec<RegistryFriendlyByteBuf, RouteSectionStatus> STREAM_CODEC = NeoForgeStreamCodecs.enumCodec(RouteSectionStatus.class);

    private final String serializedName;

    RouteSectionStatus(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}

