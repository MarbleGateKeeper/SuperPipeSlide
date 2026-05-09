package dev.marblegate.superpipeslide.common.core.geometry;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

public enum CurveType implements StringRepresentable {
    LINE("line"),
    AUTO_CURVE("auto_curve"),
    GAZE_CURVE("gaze_curve"),
    CONTROLLED("controlled");

    public static final Codec<CurveType> CODEC = StringRepresentable.fromEnum(CurveType::values);
    public static final StreamCodec<RegistryFriendlyByteBuf, CurveType> STREAM_CODEC = NeoForgeStreamCodecs.enumCodec(CurveType.class).cast();

    private final String serializedName;

    CurveType(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}
