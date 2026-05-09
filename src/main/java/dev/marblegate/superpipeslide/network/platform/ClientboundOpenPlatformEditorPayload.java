package dev.marblegate.superpipeslide.network.platform;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record ClientboundOpenPlatformEditorPayload(UUID platformStopId) implements CustomPacketPayload {
    public static final Type<ClientboundOpenPlatformEditorPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "open_platform_editor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundOpenPlatformEditorPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ClientboundOpenPlatformEditorPayload::platformStopId,
            ClientboundOpenPlatformEditorPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
