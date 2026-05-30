package dev.marblegate.superpipeslide.network.fold;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorDirectory;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorMode;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorRef;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkSavedData;
import dev.marblegate.superpipeslide.network.editor.ClientboundEditorResultPayload;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ServerboundFoldAnchorSavePayload(UUID requestId, PipeAnchorId anchorId, long baseConfigRevision, FoldAnchorMode mode, String displayName, Optional<FoldAnchorRef> selectedTarget) implements CustomPacketPayload {

    public static final Type<ServerboundFoldAnchorSavePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "fold_anchor_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundFoldAnchorSavePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ServerboundFoldAnchorSavePayload::requestId,
            PipeAnchorId.STREAM_CODEC,
            ServerboundFoldAnchorSavePayload::anchorId,
            ByteBufCodecs.VAR_LONG.cast(),
            ServerboundFoldAnchorSavePayload::baseConfigRevision,
            FoldAnchorMode.STREAM_CODEC,
            ServerboundFoldAnchorSavePayload::mode,
            ByteBufCodecs.STRING_UTF8,
            ServerboundFoldAnchorSavePayload::displayName,
            ByteBufCodecs.optional(FoldAnchorRef.STREAM_CODEC).cast(),
            ServerboundFoldAnchorSavePayload::selectedTarget,
            ServerboundFoldAnchorSavePayload::new);
    public ServerboundFoldAnchorSavePayload(PipeAnchorId anchorId, long baseConfigRevision, FoldAnchorMode mode, String displayName, Optional<FoldAnchorRef> selectedTarget) {
        this(UUID.randomUUID(), anchorId, baseConfigRevision, mode, displayName, selectedTarget);
    }

    public static void handleServer(ServerboundFoldAnchorSavePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!payload.anchorId().levelKey().equals(level.dimension())) {
            PacketDistributor.sendToPlayer(player, new ClientboundEditorResultPayload(payload.requestId(), false, "Open the fold anchor in its own dimension", 0L));
            return;
        }
        PipeNetworkSavedData data = PipeNetworkSavedData.get(level);
        FoldAnchorDirectory directory = new FoldAnchorDirectory(level.getServer());
        PipeNetworkSavedData.FoldAnchorSaveResult result = data.saveFoldAnchor(payload.anchorId(), payload.baseConfigRevision(), payload.mode(), payload.displayName(), payload.selectedTarget(), directory);
        PacketDistributor.sendToPlayer(player, new ClientboundEditorResultPayload(payload.requestId(), result.accepted(), result.message(), 0L));
        directory.data(payload.anchorId().levelKey())
                .flatMap(value -> value.foldAnchorAt(payload.anchorId()))
                .ifPresent(ignored -> PacketDistributor.sendToPlayer(player, ClientboundOpenFoldAnchorEditorPayload.create(level.getServer(), payload.anchorId())));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
