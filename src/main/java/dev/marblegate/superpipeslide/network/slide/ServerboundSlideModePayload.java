package dev.marblegate.superpipeslide.network.slide;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.slide.ServerSlideController;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record ServerboundSlideModePayload(UUID sessionId, boolean sliding) implements CustomPacketPayload {
    public static final Type<ServerboundSlideModePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "slide_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSlideModePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ServerboundSlideModePayload::sessionId,
            ByteBufCodecs.BOOL,
            ServerboundSlideModePayload::sliding,
            ServerboundSlideModePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(ServerboundSlideModePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ServerSlideController.handleSlideMode(player, payload);
        }
    }
}
