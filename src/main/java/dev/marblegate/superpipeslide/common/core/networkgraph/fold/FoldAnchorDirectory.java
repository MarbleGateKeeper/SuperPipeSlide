package dev.marblegate.superpipeslide.common.core.networkgraph.fold;


import dev.marblegate.superpipeslide.common.core.networkgraph.model.PipeNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkSavedData;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class FoldAnchorDirectory {
    private final MinecraftServer server;

    public FoldAnchorDirectory(MinecraftServer server) {
        this.server = server;
    }

    public Optional<PipeNetworkSavedData> data(ResourceKey<Level> levelKey) {
        return Optional.of(PipeNetworkSavedData.get(this.server));
    }

    public Optional<FoldAnchorNode> foldAnchor(PipeAnchorId anchorId) {
        return this.data(anchorId.levelKey()).flatMap(data -> data.foldAnchorAt(anchorId));
    }

    public List<PipeNode> bAnchorsBoundTo(FoldAnchorRef target) {
        List<PipeNode> result = new ArrayList<>();
        PipeNetworkSavedData data = PipeNetworkSavedData.get(this.server);
        for (PipeNode node : data.nodes()) {
            node.foldAnchorNode()
                    .filter(FoldAnchorNode::isBEnd)
                    .filter(fold -> fold.boundTarget().filter(target::equals).isPresent())
                    .ifPresent(ignored -> result.add(node));
        }
        return result;
    }

    public List<FoldAnchorNode> candidatesFor(FoldAnchorNode source) {
        List<FoldAnchorNode> result = new ArrayList<>();
        PipeNetworkSavedData data = PipeNetworkSavedData.get(this.server);
        for (FoldAnchorNode candidate : data.foldAnchors()) {
            if (!candidate.isAEnd() || candidate.anchorId().equals(source.anchorId()) || candidate.kind() != source.kind()) {
                continue;
            }
            boolean dimensionOk = source.kind() == FoldAnchorKind.SPACE
                    ? candidate.anchorId().levelKey().equals(source.anchorId().levelKey())
                    : !candidate.anchorId().levelKey().equals(source.anchorId().levelKey());
            if (dimensionOk) {
                result.add(candidate);
            }
        }
        return result;
    }

    public Optional<FoldAnchorNode> counterpart(FoldAnchorNode source) {
        if (source.isBEnd()) {
            return source.boundTarget().flatMap(target -> this.foldAnchor(target.anchorId()));
        }
        if (source.isAEnd()) {
            return this.bAnchorsBoundTo(FoldAnchorRef.of(source.anchorId())).stream()
                    .map(PipeNode::foldAnchorNode)
                    .flatMap(Optional::stream)
                    .findFirst();
        }
        return Optional.empty();
    }
}
