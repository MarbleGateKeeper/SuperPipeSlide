package dev.marblegate.superpipeslide.common.core.appearance.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record PipeAppearanceAssignment(int connectionKey, int profileId) {
    public static final Codec<PipeAppearanceAssignment> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("connection_key").forGetter(PipeAppearanceAssignment::connectionKey),
            Codec.INT.fieldOf("profile_id").forGetter(PipeAppearanceAssignment::profileId)
    ).apply(instance, PipeAppearanceAssignment::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PipeAppearanceAssignment> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.cast(),
            PipeAppearanceAssignment::connectionKey,
            ByteBufCodecs.VAR_INT.cast(),
            PipeAppearanceAssignment::profileId,
            PipeAppearanceAssignment::new
    );
}
