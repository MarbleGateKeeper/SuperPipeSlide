package dev.marblegate.superpipeslide.network.slide;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.slide.ServerSlideController;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;

public record ServerboundSlideTeleportRequestPayload(
        UUID sessionId,
        ResourceKey<Level> targetLevel,
        double x,
        double y,
        double z,
        Optional<UUID> targetConnectionId,
        int direction,
        double distanceOnConnection,
        double speed
) implements CustomPacketPayload {
    public static final Type<ServerboundSlideTeleportRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "slide_teleport_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSlideTeleportRequestPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ServerboundSlideTeleportRequestPayload::sessionId,
            ResourceKey.streamCodec(Registries.DIMENSION).cast(),
            ServerboundSlideTeleportRequestPayload::targetLevel,
            ByteBufCodecs.DOUBLE.cast(),
            ServerboundSlideTeleportRequestPayload::x,
            ByteBufCodecs.DOUBLE.cast(),
            ServerboundSlideTeleportRequestPayload::y,
            ByteBufCodecs.DOUBLE.cast(),
            ServerboundSlideTeleportRequestPayload::z,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).cast(),
            ServerboundSlideTeleportRequestPayload::targetConnectionId,
            ByteBufCodecs.VAR_INT.cast(),
            ServerboundSlideTeleportRequestPayload::direction,
            ByteBufCodecs.DOUBLE.cast(),
            ServerboundSlideTeleportRequestPayload::distanceOnConnection,
            ByteBufCodecs.DOUBLE.cast(),
            ServerboundSlideTeleportRequestPayload::speed,
            ServerboundSlideTeleportRequestPayload::new
    );

    public ServerboundSlideTeleportRequestPayload {
        targetConnectionId = targetConnectionId == null ? Optional.empty() : targetConnectionId;
        direction = direction < 0 ? -1 : 1;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(distanceOnConnection) || !Double.isFinite(speed)) {
            throw new IllegalArgumentException("Slide teleport values must be finite");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(ServerboundSlideTeleportRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ServerSlideController.handleTeleportRequest(player, payload);
        }
    }
}
