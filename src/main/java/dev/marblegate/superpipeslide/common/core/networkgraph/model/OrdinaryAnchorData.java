package dev.marblegate.superpipeslide.common.core.networkgraph.model;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record OrdinaryAnchorData() implements PipeNodeData {
    public static final OrdinaryAnchorData INSTANCE = new OrdinaryAnchorData();
    public static final MapCodec<OrdinaryAnchorData> CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, OrdinaryAnchorData> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public PipeNodeType type() {
        return PipeNodeType.ORDINARY_ANCHOR;
    }
}
