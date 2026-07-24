package dev.marblegate.superpipeslide.network.editor;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkSavedData;
import dev.marblegate.superpipeslide.common.event.ServerEvents;
import dev.marblegate.superpipeslide.config.Config;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Confirms an in-world anchor attach point edit. The server re-validates, quantizes and
 * clamps the offset, rewrites every connection touching the anchor and replies with an
 * editor result for player feedback.
 */
public record ServerboundUpdateAnchorOffsetPayload(UUID requestId, PipeAnchorId anchor, Vec3 offset) implements CustomPacketPayload {

    public static final Type<ServerboundUpdateAnchorOffsetPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "update_anchor_offset"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundUpdateAnchorOffsetPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ServerboundUpdateAnchorOffsetPayload::requestId,
            PipeAnchorId.STREAM_CODEC,
            ServerboundUpdateAnchorOffsetPayload::anchor,
            Vec3.STREAM_CODEC,
            ServerboundUpdateAnchorOffsetPayload::offset,
            ServerboundUpdateAnchorOffsetPayload::new);
    public ServerboundUpdateAnchorOffsetPayload(PipeAnchorId anchor, Vec3 offset) {
        this(UUID.randomUUID(), anchor, offset);
    }

    public static void handleServer(ServerboundUpdateAnchorOffsetPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        PipeNetworkSavedData data = PipeNetworkSavedData.get(level.getServer());
        PipeNetworkSavedData.AnchorAttachOffsetResult result = data.updateAnchorAttachOffset(payload.anchor(), payload.offset(), Config.MAX_CONNECTION_LENGTH.getAsDouble());
        ServerEvents.sendEditorResult(player, payload.requestId(), result.accepted(), result.message(), data.revision());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
