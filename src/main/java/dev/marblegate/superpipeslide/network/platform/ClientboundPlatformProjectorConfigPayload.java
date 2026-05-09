package dev.marblegate.superpipeslide.network.platform;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.block.station.PlatformProjectorConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundPlatformProjectorConfigPayload(BlockPos pos, PlatformProjectorConfig config) implements CustomPacketPayload {
    public static final Type<ClientboundPlatformProjectorConfigPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "platform_projector_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundPlatformProjectorConfigPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            ClientboundPlatformProjectorConfigPayload::pos,
            PlatformProjectorConfig.STREAM_CODEC,
            ClientboundPlatformProjectorConfigPayload::config,
            ClientboundPlatformProjectorConfigPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
