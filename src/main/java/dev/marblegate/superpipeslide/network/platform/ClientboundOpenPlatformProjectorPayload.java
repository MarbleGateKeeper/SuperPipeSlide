package dev.marblegate.superpipeslide.network.platform;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.block.station.PlatformProjectorConfig;
import dev.marblegate.superpipeslide.common.core.projection.layout.AppliedProjectionLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundOpenPlatformProjectorPayload(BlockPos pos, PlatformProjectorConfig config, AppliedProjectionLayout appliedLayout) implements CustomPacketPayload {
    public static final Type<ClientboundOpenPlatformProjectorPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "open_platform_projector"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundOpenPlatformProjectorPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            ClientboundOpenPlatformProjectorPayload::pos,
            PlatformProjectorConfig.STREAM_CODEC,
            ClientboundOpenPlatformProjectorPayload::config,
            AppliedProjectionLayout.STREAM_CODEC,
            ClientboundOpenPlatformProjectorPayload::appliedLayout,
            ClientboundOpenPlatformProjectorPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
