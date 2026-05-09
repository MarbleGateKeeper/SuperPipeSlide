package dev.marblegate.superpipeslide.common.core.geometry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record PipeAnchorId(ResourceKey<Level> levelKey, BlockPos blockPos) {
    public static final Codec<PipeAnchorId> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("level").forGetter(PipeAnchorId::levelKey),
            BlockPos.CODEC.fieldOf("pos").forGetter(PipeAnchorId::blockPos)
    ).apply(instance, PipeAnchorId::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PipeAnchorId> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION).cast(),
            PipeAnchorId::levelKey,
            BlockPos.STREAM_CODEC.cast(),
            PipeAnchorId::blockPos,
            PipeAnchorId::new
    );

    public static PipeAnchorId of(Level level, BlockPos pos) {
        return new PipeAnchorId(level.dimension(), pos.immutable());
    }
}
