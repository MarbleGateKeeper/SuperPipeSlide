package dev.marblegate.superpipeslide.common.core.networkgraph.model;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public interface PipeNodeData {
    Codec<PipeNodeData> CODEC = PipeNodeType.CODEC.dispatch("type", PipeNodeData::type, PipeNodeType::codec);
    StreamCodec<RegistryFriendlyByteBuf, PipeNodeData> STREAM_CODEC = PipeNodeType.STREAM_CODEC.dispatch(PipeNodeData::type, PipeNodeType::streamCodec);

    PipeNodeType type();
}
