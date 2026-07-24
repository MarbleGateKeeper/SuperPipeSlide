package dev.marblegate.superpipeslide.common.core.geometry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * A node of a PATH curve: an on-curve point with optional manual cubic bezier handles.
 * Handles are stored as absolute control points. An empty handle is automatic: it is
 * derived from the neighbouring points (Catmull-Rom style) whenever the curve is sampled,
 * so segments adjacent to an automatic node follow edits applied around it, while manual
 * handles pin the node's neighbourhood in place.
 */
public record PipePathNode(Vec3 position, Optional<Vec3> inHandle, Optional<Vec3> outHandle) {

    public static final Codec<PipePathNode> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Vec3.CODEC.fieldOf("position").forGetter(PipePathNode::position),
            Vec3.CODEC.optionalFieldOf("in_handle").forGetter(PipePathNode::inHandle),
            Vec3.CODEC.optionalFieldOf("out_handle").forGetter(PipePathNode::outHandle)).apply(instance, PipePathNode::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PipePathNode> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC,
            PipePathNode::position,
            ByteBufCodecs.optional(Vec3.STREAM_CODEC).cast(),
            PipePathNode::inHandle,
            ByteBufCodecs.optional(Vec3.STREAM_CODEC).cast(),
            PipePathNode::outHandle,
            PipePathNode::new);
    public PipePathNode {
        validateFinite(position, "position");
        inHandle.ifPresent(handle -> validateFinite(handle, "inHandle"));
        outHandle.ifPresent(handle -> validateFinite(handle, "outHandle"));
    }

    public static PipePathNode automatic(Vec3 position) {
        return new PipePathNode(position, Optional.empty(), Optional.empty());
    }

    public boolean isAutomatic() {
        return this.inHandle.isEmpty() && this.outHandle.isEmpty();
    }

    public PipePathNode withPosition(Vec3 position) {
        Vec3 delta = position.subtract(this.position);
        return new PipePathNode(position, this.inHandle.map(handle -> handle.add(delta)), this.outHandle.map(handle -> handle.add(delta)));
    }

    public PipePathNode withHandles(Optional<Vec3> inHandle, Optional<Vec3> outHandle) {
        return new PipePathNode(this.position, inHandle, outHandle);
    }

    public PipePathNode asAutomatic() {
        return new PipePathNode(this.position, Optional.empty(), Optional.empty());
    }

    private static void validateFinite(Vec3 vector, String name) {
        if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)) {
            throw new IllegalArgumentException("Pipe path node " + name + " must be finite");
        }
    }
}
