package dev.marblegate.superpipeslide.network.fold;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorDirectory;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorRef;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkSavedData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record ClientboundOpenFoldAnchorEditorPayload(FoldAnchorNode anchor, int sourceConnectionCount, List<Candidate> candidates) implements CustomPacketPayload {
    private static final int MAX_CANDIDATES = 4096;

    public static final Type<ClientboundOpenFoldAnchorEditorPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "open_fold_anchor_editor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundOpenFoldAnchorEditorPayload> STREAM_CODEC = StreamCodec.composite(
            FoldAnchorNode.STREAM_CODEC,
            ClientboundOpenFoldAnchorEditorPayload::anchor,
            ByteBufCodecs.VAR_INT.cast(),
            ClientboundOpenFoldAnchorEditorPayload::sourceConnectionCount,
            Candidate.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CANDIDATES)).cast(),
            ClientboundOpenFoldAnchorEditorPayload::candidates,
            ClientboundOpenFoldAnchorEditorPayload::new
    );

    public ClientboundOpenFoldAnchorEditorPayload {
        candidates = List.copyOf(candidates);
    }

    public static ClientboundOpenFoldAnchorEditorPayload create(MinecraftServer server, PipeAnchorId anchorId) {
        FoldAnchorDirectory directory = new FoldAnchorDirectory(server);
        FoldAnchorNode anchor = directory.foldAnchor(anchorId).orElseThrow();
        int sourceConnectionCount = directory.data(anchorId.levelKey())
                .map(data -> data.connectionCount(anchorId))
                .orElse(0);
        List<Candidate> candidates = directory.candidatesFor(anchor).stream()
                .map(candidate -> toCandidate(directory, anchor, candidate))
                .filter(candidate -> candidate.available() || candidate.currentlySelected())
                .sorted(Comparator.comparing((Candidate candidate) -> !candidate.currentlySelected())
                        .thenComparing(candidate -> !candidate.available())
                        .thenComparing(Candidate::displayName)
                        .thenComparing(candidate -> candidate.ref().levelKey().toString())
                        .thenComparing(candidate -> candidate.ref().anchorId().blockPos().asLong()))
                .toList();
        return new ClientboundOpenFoldAnchorEditorPayload(anchor, sourceConnectionCount, candidates);
    }

    private static Candidate toCandidate(FoldAnchorDirectory directory, FoldAnchorNode source, FoldAnchorNode candidate) {
        FoldAnchorRef ref = FoldAnchorRef.of(candidate.anchorId());
        boolean selected = source.boundTarget().filter(ref::equals).isPresent();
        boolean boundByOther = directory.bAnchorsBoundTo(ref).stream().anyMatch(node -> !node.id().equals(source.anchorId()));
        Optional<PipeNetworkSavedData> data = directory.data(candidate.anchorId().levelKey());
        boolean hasSingleConnection = data.map(value -> value.connectionCount(candidate.anchorId()) == 1).orElse(false);
        boolean available = !boundByOther;
        String disabledReason = "";
        if (boundByOther) {
            disabledReason = "Already bound";
        }
        return new Candidate(ref, candidate.displayName(), data.map(value -> value.connectionCount(candidate.anchorId())).orElse(0), available, selected, disabledReason);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Candidate(FoldAnchorRef ref, String displayName, int connectionCount, boolean available, boolean currentlySelected, String disabledReason) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Candidate> STREAM_CODEC = StreamCodec.composite(
                FoldAnchorRef.STREAM_CODEC,
                Candidate::ref,
                ByteBufCodecs.STRING_UTF8,
                Candidate::displayName,
                ByteBufCodecs.VAR_INT.cast(),
                Candidate::connectionCount,
                ByteBufCodecs.BOOL,
                Candidate::available,
                ByteBufCodecs.BOOL,
                Candidate::currentlySelected,
                ByteBufCodecs.STRING_UTF8,
                Candidate::disabledReason,
                Candidate::new
        );
    }
}
