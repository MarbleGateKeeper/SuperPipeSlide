package dev.marblegate.superpipeslide.common.core.networkgraph.fold;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

public enum FoldAnchorMode implements StringRepresentable {
    UNCONFIGURED("unconfigured"),
    A_END("a_end"),
    B_END("b_end");

    public static final com.mojang.serialization.Codec<FoldAnchorMode> CODEC = StringRepresentable.fromEnum(FoldAnchorMode::values);
    public static final StreamCodec<RegistryFriendlyByteBuf, FoldAnchorMode> STREAM_CODEC = NeoForgeStreamCodecs.enumCodec(FoldAnchorMode.class).cast();

    private final String serializedName;

    FoldAnchorMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}
