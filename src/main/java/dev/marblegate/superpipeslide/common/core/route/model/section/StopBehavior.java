package dev.marblegate.superpipeslide.common.core.route.model.section;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

public enum StopBehavior implements StringRepresentable {
    STOP("stop"),
    PASS("pass");

    public static final Codec<StopBehavior> CODEC = StringRepresentable.fromEnum(StopBehavior::values);
    public static final StreamCodec<RegistryFriendlyByteBuf, StopBehavior> STREAM_CODEC = NeoForgeStreamCodecs.enumCodec(StopBehavior.class);

    private final String serializedName;

    StopBehavior(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}

