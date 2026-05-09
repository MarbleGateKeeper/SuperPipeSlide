package dev.marblegate.superpipeslide.common.core.networkgraph.fold;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

public enum FoldAnchorKind implements StringRepresentable {
    SPACE("space"),
    DIMENSION("dimension");

    public static final com.mojang.serialization.Codec<FoldAnchorKind> CODEC = StringRepresentable.fromEnum(FoldAnchorKind::values);
    public static final StreamCodec<RegistryFriendlyByteBuf, FoldAnchorKind> STREAM_CODEC = NeoForgeStreamCodecs.enumCodec(FoldAnchorKind.class).cast();

    private final String serializedName;

    FoldAnchorKind(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}
