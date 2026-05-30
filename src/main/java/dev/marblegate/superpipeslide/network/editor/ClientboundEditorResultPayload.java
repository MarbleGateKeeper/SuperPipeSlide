package dev.marblegate.superpipeslide.network.editor;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundEditorResultPayload(UUID requestId, boolean accepted, String message, long routeRevision) implements CustomPacketPayload {

    public static final Type<ClientboundEditorResultPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "editor_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundEditorResultPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ClientboundEditorResultPayload::requestId,
            ByteBufCodecs.BOOL,
            ClientboundEditorResultPayload::accepted,
            ByteBufCodecs.STRING_UTF8,
            ClientboundEditorResultPayload::message,
            ByteBufCodecs.VAR_LONG.cast(),
            ClientboundEditorResultPayload::routeRevision,
            ClientboundEditorResultPayload::new);
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
