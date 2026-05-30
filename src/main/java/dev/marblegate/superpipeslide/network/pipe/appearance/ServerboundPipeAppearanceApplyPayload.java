package dev.marblegate.superpipeslide.network.pipe.appearance;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.appearance.model.PipeAppearanceProfile;
import dev.marblegate.superpipeslide.common.core.appearance.storage.PipeAppearanceSavedData;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkSavedData;
import dev.marblegate.superpipeslide.common.event.ServerEvents;
import dev.marblegate.superpipeslide.common.item.pipe.PipeAppearanceToolItem;
import dev.marblegate.superpipeslide.common.registry.SPSDataComponents;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ServerboundPipeAppearanceApplyPayload(
        UUID requestId,
        long baseAppearanceRevision,
        int targetConnectionKey,
        PipeAppearanceProfile profile,
        String scope) implements CustomPacketPayload {

    public static final String SCOPE_DRAFT = "draft";
    public static final String SCOPE_SINGLE = "single";
    public static final String SCOPE_CONNECTED = "connected";

    public static final Type<ServerboundPipeAppearanceApplyPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipe_appearance_apply"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundPipeAppearanceApplyPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ServerboundPipeAppearanceApplyPayload::requestId,
            ByteBufCodecs.VAR_LONG.cast(),
            ServerboundPipeAppearanceApplyPayload::baseAppearanceRevision,
            ByteBufCodecs.VAR_INT.cast(),
            ServerboundPipeAppearanceApplyPayload::targetConnectionKey,
            PipeAppearanceProfile.STREAM_CODEC,
            ServerboundPipeAppearanceApplyPayload::profile,
            ByteBufCodecs.STRING_UTF8,
            ServerboundPipeAppearanceApplyPayload::scope,
            ServerboundPipeAppearanceApplyPayload::new);
    public ServerboundPipeAppearanceApplyPayload(long baseAppearanceRevision, int targetConnectionKey, PipeAppearanceProfile profile, String scope) {
        this(UUID.randomUUID(), baseAppearanceRevision, targetConnectionKey, profile, scope);
    }

    public static void handleServer(ServerboundPipeAppearanceApplyPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        PipeAppearanceSavedData appearance = PipeAppearanceSavedData.get(level.getServer());
        if (SCOPE_DRAFT.equals(payload.scope())) {
            updateHeldDraft(player, payload.profile().withoutServerId());
            ServerEvents.sendEditorResult(player, payload.requestId(), true, "Pipe appearance saved to tool", appearance.revision());
            return;
        }

        PipeNetworkSavedData pipes = PipeNetworkSavedData.get(level.getServer());
        PipeAppearanceSavedData.ApplyResult result = SCOPE_CONNECTED.equals(payload.scope())
                ? appearance.applyConnected(pipes, payload.targetConnectionKey(), payload.profile())
                : appearance.applySingle(pipes, payload.targetConnectionKey(), payload.profile());
        if (result.accepted()) {
            updateHeldDraft(player, payload.profile().withoutServerId());
            ServerEvents.broadcastPipeAppearanceDelta(level);
        }
        ServerEvents.sendEditorResult(player, payload.requestId(), result.accepted(), result.message(), appearance.revision());
    }

    private static void updateHeldDraft(ServerPlayer player, PipeAppearanceProfile profile) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof PipeAppearanceToolItem) {
                stack.set(SPSDataComponents.PIPE_APPEARANCE_DRAFT.get(), profile.normalizedToDefinitions().withoutServerId());
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
