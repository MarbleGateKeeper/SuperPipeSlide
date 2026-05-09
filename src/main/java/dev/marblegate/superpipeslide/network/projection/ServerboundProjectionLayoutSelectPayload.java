package dev.marblegate.superpipeslide.network.projection;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.projection.storage.ProjectionLayoutSavedData;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutTarget;
import dev.marblegate.superpipeslide.common.registry.SPSDataComponents;
import dev.marblegate.superpipeslide.common.registry.SPSItems;
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

public record ServerboundProjectionLayoutSelectPayload(ProjectionLayoutTarget target, UUID layoutId) implements CustomPacketPayload {
    public static final Type<ServerboundProjectionLayoutSelectPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "projection_layout_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundProjectionLayoutSelectPayload> STREAM_CODEC = StreamCodec.of(
            ServerboundProjectionLayoutSelectPayload::encode,
            ServerboundProjectionLayoutSelectPayload::decode
    );

    public static void handleServer(ServerboundProjectionLayoutSelectPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ProjectionLayoutSavedData data = ProjectionLayoutSavedData.get(player.level().getServer());
        data.select(player.getUUID(), payload.target(), payload.layoutId());
        syncHeldSelection(player, payload.target(), data.selectedLayoutId(player.getUUID(), payload.target()));
        PacketDistributor.sendToPlayer(player, new ClientboundOpenProjectionLayoutDesignerPayload(payload.target(), data.selectedLayoutIds(player.getUUID()), data.summaries(player.getUUID()), false));
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

    private static void encode(RegistryFriendlyByteBuf buffer, ServerboundProjectionLayoutSelectPayload payload) {
        ProjectionLayoutTarget.STREAM_CODEC.encode(buffer, payload.target);
        net.minecraft.core.UUIDUtil.STREAM_CODEC.encode(buffer, payload.layoutId);
    }

    private static ServerboundProjectionLayoutSelectPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ServerboundProjectionLayoutSelectPayload(ProjectionLayoutTarget.STREAM_CODEC.decode(buffer), net.minecraft.core.UUIDUtil.STREAM_CODEC.decode(buffer));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
