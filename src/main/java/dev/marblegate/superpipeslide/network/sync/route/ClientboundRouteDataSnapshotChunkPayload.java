package dev.marblegate.superpipeslide.network.sync.route;


import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionPathRecord;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationTransferLink;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

public record ClientboundRouteDataSnapshotChunkPayload(
        long revision,
        int chunkIndex,
        List<StationGroup> stationGroups,
        List<PlatformStop> platformStops,
        List<RouteLine> routeLines,
        List<RouteLayout> routeLayouts,
        List<RouteSection> routeSections,
        List<RouteSectionPathRecord> routeSectionPaths,
        List<StationTransferLink> stationTransferLinks
) implements CustomPacketPayload {
    private static final int MAX_OBJECTS = 1024;

    public static final Type<ClientboundRouteDataSnapshotChunkPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "route_data_snapshot_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundRouteDataSnapshotChunkPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG.cast(),
            ClientboundRouteDataSnapshotChunkPayload::revision,
            ByteBufCodecs.VAR_INT,
            ClientboundRouteDataSnapshotChunkPayload::chunkIndex,
            StationGroup.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OBJECTS)),
            ClientboundRouteDataSnapshotChunkPayload::stationGroups,
            PlatformStop.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OBJECTS)),
            ClientboundRouteDataSnapshotChunkPayload::platformStops,
            RouteLine.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OBJECTS)),
            ClientboundRouteDataSnapshotChunkPayload::routeLines,
            RouteLayout.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OBJECTS)),
            ClientboundRouteDataSnapshotChunkPayload::routeLayouts,
            RouteSection.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OBJECTS)),
            ClientboundRouteDataSnapshotChunkPayload::routeSections,
            RouteSectionPathRecord.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OBJECTS)),
            ClientboundRouteDataSnapshotChunkPayload::routeSectionPaths,
            StationTransferLink.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OBJECTS)),
            ClientboundRouteDataSnapshotChunkPayload::stationTransferLinks,
            ClientboundRouteDataSnapshotChunkPayload::new
    );

    public ClientboundRouteDataSnapshotChunkPayload {
        stationGroups = List.copyOf(stationGroups);
        platformStops = List.copyOf(platformStops);
        routeLines = List.copyOf(routeLines);
        routeLayouts = List.copyOf(routeLayouts);
        routeSections = List.copyOf(routeSections);
        routeSectionPaths = List.copyOf(routeSectionPaths);
        stationTransferLinks = List.copyOf(stationTransferLinks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

