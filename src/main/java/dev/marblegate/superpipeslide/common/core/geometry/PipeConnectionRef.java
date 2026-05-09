package dev.marblegate.superpipeslide.common.core.geometry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

public record PipeConnectionRef(ResourceKey<Level> levelKey, UUID connectionId) {
    public static final Codec<PipeConnectionRef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("level").forGetter(PipeConnectionRef::levelKey),
            UUIDUtil.STRING_CODEC.fieldOf("connection_id").forGetter(PipeConnectionRef::connectionId)
    ).apply(instance, PipeConnectionRef::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PipeConnectionRef> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION).cast(),
            PipeConnectionRef::levelKey,
            UUIDUtil.STREAM_CODEC.cast(),
            PipeConnectionRef::connectionId,
            PipeConnectionRef::new
    );

    public static PipeConnectionRef of(PipeConnection connection) {
        return new PipeConnectionRef(connection.levelKey(), connection.id());
    }
}
