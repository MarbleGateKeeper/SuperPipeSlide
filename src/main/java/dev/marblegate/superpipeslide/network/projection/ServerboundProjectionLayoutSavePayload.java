package dev.marblegate.superpipeslide.network.projection;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutDefinition;
import dev.marblegate.superpipeslide.common.core.projection.storage.ProjectionLayoutSavedData;
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

import java.util.UUID;

public record ServerboundProjectionLayoutSavePayload(UUID requestId, ProjectionLayoutDefinition layout, boolean select) implements CustomPacketPayload {
    public static final Type<ServerboundProjectionLayoutSavePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "projection_layout_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundProjectionLayoutSavePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ServerboundProjectionLayoutSavePayload::requestId,
            ProjectionLayoutDefinition.STREAM_CODEC,
            ServerboundProjectionLayoutSavePayload::layout,
            net.minecraft.network.codec.ByteBufCodecs.BOOL,
            ServerboundProjectionLayoutSavePayload::select,
            ServerboundProjectionLayoutSavePayload::new
    );

    public ServerboundProjectionLayoutSavePayload(ProjectionLayoutDefinition layout, boolean select) {
        this(UUID.randomUUID(), layout, select);
    }

    public static void handleServer(ServerboundProjectionLayoutSavePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ProjectionLayoutSavedData data = ProjectionLayoutSavedData.get(player.level().getServer());
        if (!ProjectionLayoutSavedData.isValid(payload.layout())) {
            ServerEvents.sendEditorResult(player, payload.requestId(), false, "Projection layout is invalid", data.revision());
            PacketDistributor.sendToPlayer(player, new ClientboundOpenProjectionLayoutDesignerPayload(payload.layout().target(), data.selectedLayoutIds(player.getUUID()), data.summaries(player.getUUID()), false));
            return;
        }
        ProjectionLayoutDefinition saved = data.save(player.getUUID(), payload.layout(), payload.select());
        if (payload.select()) {
            ItemStack stack = player.getMainHandItem().is(SPSItems.PROJECTION_LAYOUT_DESIGNER.get()) ? player.getMainHandItem() : player.getOffhandItem();
            if (stack.is(SPSItems.PROJECTION_LAYOUT_DESIGNER.get())) {
                stack.set(SPSDataComponents.PROJECTION_LAYOUT_ACTIVE_TARGET.get(), saved.target());
                stack.set(SPSDataComponents.PROJECTION_LAYOUT_SELECTED.get(), saved.id());
            }
        }
        ServerEvents.sendEditorResult(player, payload.requestId(), true, "Projection layout saved", data.revision());
        PacketDistributor.sendToPlayer(player, new ClientboundOpenProjectionLayoutDesignerPayload(saved.target(), data.selectedLayoutIds(player.getUUID()), data.summaries(player.getUUID()), false));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
