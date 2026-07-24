package dev.marblegate.superpipeslide.common.core.geometry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * Baked world-space attachment points of a pipe connection. When present they override the
 * default "center of the anchor block" endpoints, which is how adjustable anchor attach
 * points reach every geometry consumer (rendering, sliding, raycast, length checks).
 */
public record PipeEndpoints(Vec3 from, Vec3 to) {
    public static final Codec<PipeEndpoints> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Vec3.CODEC.fieldOf("from").forGetter(PipeEndpoints::from),
            Vec3.CODEC.fieldOf("to").forGetter(PipeEndpoints::to)).apply(instance, PipeEndpoints::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PipeEndpoints> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC,
            PipeEndpoints::from,
            Vec3.STREAM_CODEC,
            PipeEndpoints::to,
            PipeEndpoints::new);

    public PipeEndpoints {
        validateFinite(from, "from");
        validateFinite(to, "to");
    }

    private static void validateFinite(Vec3 vector, String name) {
        if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)) {
            throw new IllegalArgumentException("Pipe endpoint " + name + " must be finite");
        }
    }
}
