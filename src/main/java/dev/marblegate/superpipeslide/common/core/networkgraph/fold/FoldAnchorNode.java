package dev.marblegate.superpipeslide.common.core.networkgraph.fold;


import dev.marblegate.superpipeslide.common.core.networkgraph.model.PipeNodeData;
import dev.marblegate.superpipeslide.common.core.networkgraph.model.PipeNodeType;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

public record FoldAnchorNode(
        PipeAnchorId anchorId,
        FoldAnchorKind kind,
        FoldAnchorMode mode,
        String displayName,
        Optional<FoldAnchorRef> boundTarget,
        long configRevision
) implements PipeNodeData {
    public static final int MAX_NAME_LENGTH = 48;

    public static final MapCodec<FoldAnchorNode> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            PipeAnchorId.CODEC.fieldOf("anchor_id").forGetter(FoldAnchorNode::anchorId),
            FoldAnchorKind.CODEC.fieldOf("kind").forGetter(FoldAnchorNode::kind),
            FoldAnchorMode.CODEC.optionalFieldOf("mode", FoldAnchorMode.UNCONFIGURED).forGetter(FoldAnchorNode::mode),
            com.mojang.serialization.Codec.STRING.optionalFieldOf("display_name", "").forGetter(FoldAnchorNode::displayName),
            FoldAnchorRef.CODEC.optionalFieldOf("bound_target").forGetter(FoldAnchorNode::boundTarget),
            com.mojang.serialization.Codec.LONG.optionalFieldOf("config_revision", 0L).forGetter(FoldAnchorNode::configRevision)
    ).apply(instance, FoldAnchorNode::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FoldAnchorNode> STREAM_CODEC = StreamCodec.composite(
            PipeAnchorId.STREAM_CODEC,
            FoldAnchorNode::anchorId,
            FoldAnchorKind.STREAM_CODEC,
            FoldAnchorNode::kind,
            FoldAnchorMode.STREAM_CODEC,
            FoldAnchorNode::mode,
            ByteBufCodecs.STRING_UTF8,
            FoldAnchorNode::displayName,
            ByteBufCodecs.optional(FoldAnchorRef.STREAM_CODEC).cast(),
            FoldAnchorNode::boundTarget,
            ByteBufCodecs.VAR_LONG.cast(),
            FoldAnchorNode::configRevision,
            FoldAnchorNode::new
    );

    public FoldAnchorNode {
        displayName = sanitizeName(displayName);
        boundTarget = boundTarget.filter(target -> mode == FoldAnchorMode.B_END);
        if (mode == FoldAnchorMode.A_END && displayName.isBlank()) {
            displayName = defaultName(anchorId);
        }
    }

    public static FoldAnchorNode unconfigured(PipeAnchorId anchorId, FoldAnchorKind kind) {
        return new FoldAnchorNode(anchorId, kind, FoldAnchorMode.UNCONFIGURED, "", Optional.empty(), 0L);
    }

    public FoldAnchorNode asAEnd(String name) {
        return new FoldAnchorNode(this.anchorId, this.kind, FoldAnchorMode.A_END, name, Optional.empty(), this.configRevision + 1L);
    }

    public FoldAnchorNode asBEnd(FoldAnchorRef target) {
        return new FoldAnchorNode(this.anchorId, this.kind, FoldAnchorMode.B_END, "", Optional.of(target), this.configRevision + 1L);
    }

    public FoldAnchorNode unboundBEnd() {
        return new FoldAnchorNode(this.anchorId, this.kind, FoldAnchorMode.B_END, "", Optional.empty(), this.configRevision + 1L);
    }

    @Override
    public PipeNodeType type() {
        return PipeNodeType.FOLD_ANCHOR;
    }

    public boolean isAEnd() {
        return this.mode == FoldAnchorMode.A_END;
    }

    public boolean isBEnd() {
        return this.mode == FoldAnchorMode.B_END;
    }

    private static String sanitizeName(String name) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.length() <= MAX_NAME_LENGTH ? trimmed : trimmed.substring(0, MAX_NAME_LENGTH);
    }

    private static String defaultName(PipeAnchorId anchorId) {
        return anchorId.blockPos().toShortString();
    }
}
