package dev.marblegate.superpipeslide.network.sync.route;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundRouteDataSnapshotStartPayload(
        long revision,
        long pipeRevisionUsed,
        int stationGroupCount,
        int platformStopCount,
        int routeLineCount,
        int routeLayoutCount,
        int routeSectionCount,
        int routeSectionPathCount,
        int stationTransferLinkCount,
        int chunkCount
) implements CustomPacketPayload {
    public static final Type<ClientboundRouteDataSnapshotStartPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "route_data_snapshot_start"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundRouteDataSnapshotStartPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG.cast(),
            ClientboundRouteDataSnapshotStartPayload::revision,
            ByteBufCodecs.VAR_LONG.cast(),
            ClientboundRouteDataSnapshotStartPayload::pipeRevisionUsed,
            ByteBufCodecs.VAR_INT,
            ClientboundRouteDataSnapshotStartPayload::stationGroupCount,
            ByteBufCodecs.VAR_INT,
            ClientboundRouteDataSnapshotStartPayload::platformStopCount,
            ByteBufCodecs.VAR_INT,
            ClientboundRouteDataSnapshotStartPayload::routeLineCount,
            ByteBufCodecs.VAR_INT,
            ClientboundRouteDataSnapshotStartPayload::routeLayoutCount,
            ByteBufCodecs.VAR_INT,
            ClientboundRouteDataSnapshotStartPayload::routeSectionCount,
            ByteBufCodecs.VAR_INT,
            ClientboundRouteDataSnapshotStartPayload::routeSectionPathCount,
            ByteBufCodecs.VAR_INT,
            ClientboundRouteDataSnapshotStartPayload::stationTransferLinkCount,
            ByteBufCodecs.VAR_INT,
            ClientboundRouteDataSnapshotStartPayload::chunkCount,
            ClientboundRouteDataSnapshotStartPayload::new
    );

    public ClientboundRouteDataSnapshotStartPayload {
        if (revision < 0L || pipeRevisionUsed < 0L) {
            throw new IllegalArgumentException("Route data snapshot revisions cannot be negative");
        }
        if (stationGroupCount < 0
                || platformStopCount < 0
                || routeLineCount < 0
                || routeLayoutCount < 0
                || routeSectionCount < 0
                || routeSectionPathCount < 0
                || stationTransferLinkCount < 0
                || chunkCount < 0) {
            throw new IllegalArgumentException("Route data snapshot counts cannot be negative");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
