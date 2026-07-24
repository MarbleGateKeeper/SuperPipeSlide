package dev.marblegate.superpipeslide.common.core.networkgraph.model;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

public record OrdinaryAnchorData(Vec3 attachOffset) implements PipeNodeData {
    public static final MapCodec<OrdinaryAnchorData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Vec3.CODEC.optionalFieldOf("attach_offset", Vec3.ZERO).forGetter(OrdinaryAnchorData::attachOffset)).apply(instance, OrdinaryAnchorData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrdinaryAnchorData> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC,
            OrdinaryAnchorData::attachOffset,
            OrdinaryAnchorData::new);

    public OrdinaryAnchorData {
        if (!Double.isFinite(attachOffset.x) || !Double.isFinite(attachOffset.y) || !Double.isFinite(attachOffset.z)) {
            throw new IllegalArgumentException("Ordinary anchor attach offset must be finite");
        }
    }

    public static OrdinaryAnchorData centered() {
        return new OrdinaryAnchorData(Vec3.ZERO);
    }

    @Override
    public PipeNodeType type() {
        return PipeNodeType.ORDINARY_ANCHOR;
    }
}
