package dev.marblegate.superpipeslide.network.projection;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutSummary;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutTarget;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundOpenProjectionLayoutDesignerPayload(ProjectionLayoutTarget activeTarget, Map<ProjectionLayoutTarget, UUID> selectedLayoutIds, List<ProjectionLayoutSummary> layouts, boolean editSelected) implements CustomPacketPayload {

    public static final int MAX_LAYOUTS = 96;
    public static final Type<ClientboundOpenProjectionLayoutDesignerPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "open_projection_layout_designer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundOpenProjectionLayoutDesignerPayload> STREAM_CODEC = StreamCodec.of(
            ClientboundOpenProjectionLayoutDesignerPayload::encode,
            ClientboundOpenProjectionLayoutDesignerPayload::decode);
    public ClientboundOpenProjectionLayoutDesignerPayload {
        activeTarget = activeTarget == null ? ProjectionLayoutTarget.STATION_NAME : activeTarget;
        selectedLayoutIds = selectedLayoutIds == null ? Map.of() : Map.copyOf(selectedLayoutIds);
        layouts = layouts == null ? List.of() : layouts.stream().limit(MAX_LAYOUTS).toList();
    }

    public Optional<UUID> selectedLayoutId() {
        return Optional.ofNullable(this.selectedLayoutIds.get(this.activeTarget));
    }

    public Optional<UUID> selectedLayoutId(ProjectionLayoutTarget target) {
        return Optional.ofNullable(this.selectedLayoutIds.get(target == null ? ProjectionLayoutTarget.STATION_NAME : target));
    }

    public ClientboundOpenProjectionLayoutDesignerPayload withActiveTarget(ProjectionLayoutTarget target) {
        return new ClientboundOpenProjectionLayoutDesignerPayload(target, this.selectedLayoutIds, this.layouts, this.editSelected);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ClientboundOpenProjectionLayoutDesignerPayload payload) {
        ProjectionLayoutTarget.STREAM_CODEC.encode(buffer, payload.activeTarget);
        buffer.writeVarInt(Math.min(payload.selectedLayoutIds.size(), ProjectionLayoutTarget.values().length));
        for (Map.Entry<ProjectionLayoutTarget, UUID> entry : payload.selectedLayoutIds.entrySet()) {
            ProjectionLayoutTarget.STREAM_CODEC.encode(buffer, entry.getKey());
            UUIDUtil.STREAM_CODEC.encode(buffer, entry.getValue());
        }
        ProjectionLayoutSummary.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LAYOUTS)).encode(buffer, payload.layouts);
        ByteBufCodecs.BOOL.encode(buffer, payload.editSelected);
    }

    private static ClientboundOpenProjectionLayoutDesignerPayload decode(RegistryFriendlyByteBuf buffer) {
        ProjectionLayoutTarget activeTarget = ProjectionLayoutTarget.STREAM_CODEC.decode(buffer);
        int selectedCount = Math.min(buffer.readVarInt(), ProjectionLayoutTarget.values().length);
        java.util.EnumMap<ProjectionLayoutTarget, UUID> selected = new java.util.EnumMap<>(ProjectionLayoutTarget.class);
        for (int i = 0; i < selectedCount; i++) {
            selected.put(ProjectionLayoutTarget.STREAM_CODEC.decode(buffer), UUIDUtil.STREAM_CODEC.decode(buffer));
        }
        List<ProjectionLayoutSummary> layouts = ProjectionLayoutSummary.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LAYOUTS)).decode(buffer);
        boolean editSelected = ByteBufCodecs.BOOL.decode(buffer);
        return new ClientboundOpenProjectionLayoutDesignerPayload(activeTarget, selected, layouts, editSelected);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
