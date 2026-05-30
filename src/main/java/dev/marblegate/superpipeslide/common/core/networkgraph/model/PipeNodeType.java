package dev.marblegate.superpipeslide.common.core.networkgraph.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.marblegate.superpipeslide.common.core.networkgraph.branch.BranchNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorNode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

public enum PipeNodeType implements StringRepresentable {
    ORDINARY_ANCHOR("ordinary_anchor"),
    BRANCH("branch"),
    FOLD_ANCHOR("fold_anchor");

    public static final Codec<PipeNodeType> CODEC = StringRepresentable.fromEnum(PipeNodeType::values);
    public static final StreamCodec<RegistryFriendlyByteBuf, PipeNodeType> STREAM_CODEC = NeoForgeStreamCodecs.enumCodec(PipeNodeType.class).cast();

    private final String serializedName;

    PipeNodeType(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }

    public MapCodec<? extends PipeNodeData> codec() {
        return switch (this) {
            case ORDINARY_ANCHOR -> OrdinaryAnchorData.CODEC;
            case BRANCH -> BranchNode.CODEC;
            case FOLD_ANCHOR -> FoldAnchorNode.CODEC;
        };
    }

    public StreamCodec<? super RegistryFriendlyByteBuf, ? extends PipeNodeData> streamCodec() {
        return switch (this) {
            case ORDINARY_ANCHOR -> OrdinaryAnchorData.STREAM_CODEC;
            case BRANCH -> BranchNode.STREAM_CODEC;
            case FOLD_ANCHOR -> FoldAnchorNode.STREAM_CODEC;
        };
    }
}
