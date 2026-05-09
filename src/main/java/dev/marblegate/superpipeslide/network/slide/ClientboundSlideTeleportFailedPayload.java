package dev.marblegate.superpipeslide.network.slide;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record ClientboundSlideTeleportFailedPayload(UUID sessionId, String reason) implements CustomPacketPayload {
    private static final int MAX_REASON_LENGTH = 96;

    public static final Type<ClientboundSlideTeleportFailedPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "slide_teleport_failed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSlideTeleportFailedPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ClientboundSlideTeleportFailedPayload::sessionId,
            ByteBufCodecs.stringUtf8(MAX_REASON_LENGTH).cast(),
            ClientboundSlideTeleportFailedPayload::reason,
            ClientboundSlideTeleportFailedPayload::new
    );

    public ClientboundSlideTeleportFailedPayload {
        reason = reason == null ? "" : reason;
        if (reason.length() > MAX_REASON_LENGTH) {
            reason = reason.substring(0, MAX_REASON_LENGTH);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
