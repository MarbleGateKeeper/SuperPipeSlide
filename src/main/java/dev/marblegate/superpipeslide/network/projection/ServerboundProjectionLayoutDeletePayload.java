package dev.marblegate.superpipeslide.network.projection;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.projection.storage.ProjectionLayoutSavedData;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutTarget;
import dev.marblegate.superpipeslide.common.event.ServerEvents;
import dev.marblegate.superpipeslide.common.registry.SPSDataComponents;
import dev.marblegate.superpipeslide.common.registry.SPSItems;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;

public record ServerboundProjectionLayoutDeletePayload(UUID requestId, UUID layoutId) implements CustomPacketPayload {
    public static final Type<ServerboundProjectionLayoutDeletePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "projection_layout_delete"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundProjectionLayoutDeletePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ServerboundProjectionLayoutDeletePayload::requestId,
            UUIDUtil.STREAM_CODEC,
            ServerboundProjectionLayoutDeletePayload::layoutId,
            ServerboundProjectionLayoutDeletePayload::new
    );

    public ServerboundProjectionLayoutDeletePayload(UUID layoutId) {
        this(UUID.randomUUID(), layoutId);
    }

    public static void handleServer(ServerboundProjectionLayoutDeletePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ProjectionLayoutSavedData data = ProjectionLayoutSavedData.get(player.level().getServer());
        ProjectionLayoutTarget target = data.layout(player.getUUID(), payload.layoutId())
                .map(layout -> layout.target())
                .orElse(ProjectionLayoutTarget.STATION_NAME);
        boolean deleted = data.delete(player.getUUID(), payload.layoutId());
        if (deleted) {
            syncHeldSelection(player, target, data.selectedLayoutId(player.getUUID(), target));
        }
        ServerEvents.sendEditorResult(player, payload.requestId(), deleted, deleted ? "Projection layout deleted" : "Projection layout not found", data.revision());
        PacketDistributor.sendToPlayer(player, new ClientboundOpenProjectionLayoutDesignerPayload(target, data.selectedLayoutIds(player.getUUID()), data.summaries(player.getUUID()), false));
    }

    private static void syncHeldSelection(ServerPlayer player, ProjectionLayoutTarget target, Optional<UUID> selected) {
        ItemStack stack = player.getMainHandItem().is(SPSItems.PROJECTION_LAYOUT_DESIGNER.get()) ? player.getMainHandItem() : player.getOffhandItem();
        if (!stack.is(SPSItems.PROJECTION_LAYOUT_DESIGNER.get())) {
            return;
        }
        stack.set(SPSDataComponents.PROJECTION_LAYOUT_ACTIVE_TARGET.get(), target);
        if (selected.isPresent()) {
            stack.set(SPSDataComponents.PROJECTION_LAYOUT_SELECTED.get(), selected.get());
        } else {
            stack.remove(SPSDataComponents.PROJECTION_LAYOUT_SELECTED.get());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
