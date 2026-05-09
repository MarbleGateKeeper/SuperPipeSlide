package dev.marblegate.superpipeslide.common.core.networkgraph.model;


import dev.marblegate.superpipeslide.common.core.networkgraph.branch.BranchNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorNode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

/**
 * Authoritative endpoint node data for the pipe graph.
 * Connections still reference PipeAnchorId endpoints; this node record
 * describes what kind of endpoint lives at that id.
 */
public record PipeNode(PipeAnchorId id, PipeNodeData data) {
    public static final Codec<PipeNode> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PipeAnchorId.CODEC.fieldOf("id").forGetter(PipeNode::id),
            PipeNodeData.CODEC.fieldOf("data").forGetter(PipeNode::data)
    ).apply(instance, PipeNode::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PipeNode> STREAM_CODEC = StreamCodec.composite(
            PipeAnchorId.STREAM_CODEC,
            PipeNode::id,
            PipeNodeData.STREAM_CODEC,
            PipeNode::data,
            PipeNode::new
    );

    public PipeNode {
        if (data instanceof BranchNode branchNode && !branchNode.anchorId().equals(id)) {
            throw new IllegalArgumentException("Branch node anchor id does not match pipe node id");
        }
        if (data instanceof FoldAnchorNode foldAnchorNode && !foldAnchorNode.anchorId().equals(id)) {
            throw new IllegalArgumentException("Fold anchor node id does not match pipe node id");
        }
    }

    public static PipeNode ordinary(PipeAnchorId id) {
        return new PipeNode(id, OrdinaryAnchorData.INSTANCE);
    }

    public static PipeNode branch(PipeAnchorId id, BranchNode branchNode) {
        return new PipeNode(id, branchNode);
    }

    public static PipeNode foldAnchor(PipeAnchorId id, FoldAnchorNode foldAnchorNode) {
        return new PipeNode(id, foldAnchorNode);
    }

    public PipeNodeType type() {
        return this.data.type();
    }

    public boolean isOrdinaryAnchor() {
        return this.type() == PipeNodeType.ORDINARY_ANCHOR;
    }

    public boolean isBranch() {
        return this.type() == PipeNodeType.BRANCH;
    }

    public boolean isFoldAnchor() {
        return this.type() == PipeNodeType.FOLD_ANCHOR;
    }

    public Optional<BranchNode> branchNode() {
        return this.data instanceof BranchNode branchNode ? Optional.of(branchNode) : Optional.empty();
    }

    public Optional<FoldAnchorNode> foldAnchorNode() {
        return this.data instanceof FoldAnchorNode foldAnchorNode ? Optional.of(foldAnchorNode) : Optional.empty();
    }
}
