package dev.marblegate.superpipeslide.common.core.networkgraph.fold;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record FoldAnchorRef(ResourceKey<Level> levelKey, PipeAnchorId anchorId) {
    public static final Codec<FoldAnchorRef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("level").forGetter(FoldAnchorRef::levelKey),
            PipeAnchorId.CODEC.fieldOf("anchor_id").forGetter(FoldAnchorRef::anchorId)
    ).apply(instance, FoldAnchorRef::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FoldAnchorRef> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION).cast(),
            FoldAnchorRef::levelKey,
            PipeAnchorId.STREAM_CODEC,
            FoldAnchorRef::anchorId,
            FoldAnchorRef::new
    );

    public FoldAnchorRef {
        if (!levelKey.equals(anchorId.levelKey())) {
            throw new IllegalArgumentException("Fold anchor ref level must match anchor id level");
        }
    }

    public static FoldAnchorRef of(PipeAnchorId anchorId) {
        return new FoldAnchorRef(anchorId.levelKey(), anchorId);
    }
}
