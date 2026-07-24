package dev.marblegate.superpipeslide.common.core.networkgraph.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.networkgraph.branch.BranchNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorNode;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * Authoritative endpoint node data for the pipe graph.
 * Connections still reference PipeAnchorId endpoints; this node record
 * describes what kind of endpoint lives at that id.
 */
public record PipeNode(PipeAnchorId id, PipeNodeData data) {
    public static final Codec<PipeNode> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PipeAnchorId.CODEC.fieldOf("id").forGetter(PipeNode::id),
            PipeNodeData.CODEC.fieldOf("data").forGetter(PipeNode::data)).apply(instance, PipeNode::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PipeNode> STREAM_CODEC = StreamCodec.composite(
            PipeAnchorId.STREAM_CODEC,
            PipeNode::id,
            PipeNodeData.STREAM_CODEC,
            PipeNode::data,
            PipeNode::new);

    public PipeNode {
        if (data instanceof BranchNode branchNode && !branchNode.anchorId().equals(id)) {
            throw new IllegalArgumentException("Branch node anchor id does not match pipe node id");
        }
        if (data instanceof FoldAnchorNode foldAnchorNode && !foldAnchorNode.anchorId().equals(id)) {
            throw new IllegalArgumentException("Fold anchor node id does not match pipe node id");
        }
    }

    public static PipeNode ordinary(PipeAnchorId id) {
        return new PipeNode(id, OrdinaryAnchorData.centered());
    }

    public static PipeNode ordinary(PipeAnchorId id, Vec3 attachOffset) {
        return new PipeNode(id, new OrdinaryAnchorData(attachOffset));
    }

    public static PipeNode branch(PipeAnchorId id, BranchNode branchNode) {
        return new PipeNode(id, branchNode);
    }

    public static PipeNode foldAnchor(PipeAnchorId id, FoldAnchorNode foldAnchorNode) {
        return new PipeNode(id, foldAnchorNode);
    }

    /**
     * The world-space point where pipes attach to this node. For ordinary and fold anchors
     * this is the block center plus the node's attach offset; branch nodes carry their
     * junction position directly.
     */
    public Vec3 attachPoint() {
        Vec3 center = Vec3.atCenterOf(this.id.blockPos());
        if (this.data instanceof OrdinaryAnchorData ordinary) {
            return center.add(ordinary.attachOffset());
        }
        if (this.data instanceof FoldAnchorNode foldAnchor) {
            return center.add(foldAnchor.attachOffset());
        }
        if (this.data instanceof BranchNode branchNode) {
            return branchNode.position();
        }
        return center;
    }

    public PipeNode withAttachOffset(Vec3 offset) {
        if (this.data instanceof OrdinaryAnchorData) {
            return new PipeNode(this.id, new OrdinaryAnchorData(offset));
        }
        if (this.data instanceof FoldAnchorNode foldAnchor) {
            return new PipeNode(this.id, foldAnchor.withAttachOffset(offset));
        }
        if (this.data instanceof BranchNode branchNode) {
            return new PipeNode(this.id, branchNode.withPosition(Vec3.atCenterOf(this.id.blockPos()).add(offset)));
        }
        return this;
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
