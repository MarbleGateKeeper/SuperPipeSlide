package dev.marblegate.superpipeslide.network.editor;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.geometry.CurveSpec;
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
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Confirms an in-world pipe shape edit (curve type, tangents, control points or PATH
 * nodes). The server re-validates length and node limits before replacing the curve.
 */
public record ServerboundUpdatePipeGeometryPayload(UUID requestId, UUID connectionId, CurveSpec curveSpec) implements CustomPacketPayload {

    public static final Type<ServerboundUpdatePipeGeometryPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "update_pipe_geometry"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundUpdatePipeGeometryPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ServerboundUpdatePipeGeometryPayload::requestId,
            UUIDUtil.STREAM_CODEC.cast(),
            ServerboundUpdatePipeGeometryPayload::connectionId,
            CurveSpec.STREAM_CODEC,
            ServerboundUpdatePipeGeometryPayload::curveSpec,
            ServerboundUpdatePipeGeometryPayload::new);
    public ServerboundUpdatePipeGeometryPayload(UUID connectionId, CurveSpec curveSpec) {
        this(UUID.randomUUID(), connectionId, curveSpec);
    }

    public static void handleServer(ServerboundUpdatePipeGeometryPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        PipeNetworkSavedData data = PipeNetworkSavedData.get(level.getServer());
        PipeNetworkSavedData.ConnectionGeometryResult result = data.updateConnectionGeometry(payload.connectionId(), payload.curveSpec(), Config.MAX_CONNECTION_LENGTH.getAsDouble());
        ServerEvents.sendEditorResult(player, payload.requestId(), result.accepted(), result.message(), data.revision());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
