package dev.marblegate.superpipeslide.network.pipe.appearance;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.appearance.model.PipeAppearanceProfile;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundOpenPipeAppearanceEditorPayload(
        long appearanceRevision,
        int targetConnectionKey,
        PipeAppearanceProfile currentProfile,
        PipeAppearanceProfile draftProfile,
        double targetLength) implements CustomPacketPayload {

    public static final Type<ClientboundOpenPipeAppearanceEditorPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "open_pipe_appearance_editor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundOpenPipeAppearanceEditorPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG.cast(),
            ClientboundOpenPipeAppearanceEditorPayload::appearanceRevision,
            ByteBufCodecs.VAR_INT.cast(),
            ClientboundOpenPipeAppearanceEditorPayload::targetConnectionKey,
            PipeAppearanceProfile.STREAM_CODEC,
            ClientboundOpenPipeAppearanceEditorPayload::currentProfile,
            PipeAppearanceProfile.STREAM_CODEC,
            ClientboundOpenPipeAppearanceEditorPayload::draftProfile,
            ByteBufCodecs.DOUBLE.cast(),
            ClientboundOpenPipeAppearanceEditorPayload::targetLength,
            ClientboundOpenPipeAppearanceEditorPayload::new);
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
