package dev.marblegate.superpipeslide.network.slide;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record ClientboundSlideTeleportCommitPayload(
        UUID sessionId,
        ResourceKey<Level> targetLevel,
        double x,
        double y,
        double z,
        Optional<UUID> targetConnectionId,
        int direction,
        double distanceOnConnection,
        double speed) implements CustomPacketPayload {

    public static final Type<ClientboundSlideTeleportCommitPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "slide_teleport_commit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSlideTeleportCommitPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ClientboundSlideTeleportCommitPayload::sessionId,
            ResourceKey.streamCodec(Registries.DIMENSION).cast(),
            ClientboundSlideTeleportCommitPayload::targetLevel,
            ByteBufCodecs.DOUBLE.cast(),
            ClientboundSlideTeleportCommitPayload::x,
            ByteBufCodecs.DOUBLE.cast(),
            ClientboundSlideTeleportCommitPayload::y,
            ByteBufCodecs.DOUBLE.cast(),
            ClientboundSlideTeleportCommitPayload::z,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).cast(),
            ClientboundSlideTeleportCommitPayload::targetConnectionId,
            ByteBufCodecs.VAR_INT.cast(),
            ClientboundSlideTeleportCommitPayload::direction,
            ByteBufCodecs.DOUBLE.cast(),
            ClientboundSlideTeleportCommitPayload::distanceOnConnection,
            ByteBufCodecs.DOUBLE.cast(),
            ClientboundSlideTeleportCommitPayload::speed,
            ClientboundSlideTeleportCommitPayload::new);
    public ClientboundSlideTeleportCommitPayload {
        targetConnectionId = targetConnectionId == null ? Optional.empty() : targetConnectionId;
        direction = direction < 0 ? -1 : 1;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
