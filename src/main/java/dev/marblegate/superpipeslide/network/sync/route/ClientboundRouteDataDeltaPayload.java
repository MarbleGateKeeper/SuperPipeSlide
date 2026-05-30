package dev.marblegate.superpipeslide.network.sync.route;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionPathRecord;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationTransferLink;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundRouteDataDeltaPayload(
        long baseRevision,
        long revision,
        long pipeRevisionUsed,
        List<StationGroup> updatedStationGroups,
        List<UUID> removedStationGroupIds,
        List<PlatformStop> updatedPlatformStops,
        List<UUID> removedPlatformStopIds,
        List<RouteLine> updatedRouteLines,
        List<UUID> removedRouteLineIds,
        List<RouteLayout> updatedRouteLayouts,
        List<UUID> removedRouteLayoutIds,
        List<RouteSection> updatedRouteSections,
        List<UUID> removedRouteSectionIds,
        List<RouteSectionPathRecord> updatedRouteSectionPaths,
        List<UUID> removedRouteSectionPathIds,
        List<StationTransferLink> updatedStationTransferLinks,
        List<UUID> removedStationTransferLinkIds) implements CustomPacketPayload {

    private static final int MAX_OBJECTS = 32767;

    public static final Type<ClientboundRouteDataDeltaPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "route_data_delta"));
    private static final StreamCodec<RegistryFriendlyByteBuf, List<StationGroup>> STATION_GROUP_LIST_CODEC = StationGroup.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OBJECTS));
    private static final StreamCodec<RegistryFriendlyByteBuf, List<PlatformStop>> PLATFORM_STOP_LIST_CODEC = PlatformStop.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OBJECTS));
    private static final StreamCodec<RegistryFriendlyByteBuf, List<RouteLine>> ROUTE_LINE_LIST_CODEC = RouteLine.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OBJECTS));
    private static final StreamCodec<RegistryFriendlyByteBuf, List<RouteLayout>> ROUTE_LAYOUT_LIST_CODEC = RouteLayout.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OBJECTS));
    private static final StreamCodec<RegistryFriendlyByteBuf, List<RouteSection>> ROUTE_SECTION_LIST_CODEC = RouteSection.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OBJECTS));
    private static final StreamCodec<RegistryFriendlyByteBuf, List<RouteSectionPathRecord>> ROUTE_SECTION_PATH_LIST_CODEC = RouteSectionPathRecord.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OBJECTS));
    private static final StreamCodec<RegistryFriendlyByteBuf, List<StationTransferLink>> STATION_TRANSFER_LINK_LIST_CODEC = StationTransferLink.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OBJECTS));
    private static final StreamCodec<RegistryFriendlyByteBuf, List<UUID>> UUID_LIST_CODEC = UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OBJECTS)).cast();

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundRouteDataDeltaPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ClientboundRouteDataDeltaPayload decode(RegistryFriendlyByteBuf buffer) {
            return new ClientboundRouteDataDeltaPayload(
                    ByteBufCodecs.VAR_LONG.decode(buffer),
                    ByteBufCodecs.VAR_LONG.decode(buffer),
                    ByteBufCodecs.VAR_LONG.decode(buffer),
                    STATION_GROUP_LIST_CODEC.decode(buffer),
                    UUID_LIST_CODEC.decode(buffer),
                    PLATFORM_STOP_LIST_CODEC.decode(buffer),
                    UUID_LIST_CODEC.decode(buffer),
                    ROUTE_LINE_LIST_CODEC.decode(buffer),
                    UUID_LIST_CODEC.decode(buffer),
                    ROUTE_LAYOUT_LIST_CODEC.decode(buffer),
                    UUID_LIST_CODEC.decode(buffer),
                    ROUTE_SECTION_LIST_CODEC.decode(buffer),
                    UUID_LIST_CODEC.decode(buffer),
                    ROUTE_SECTION_PATH_LIST_CODEC.decode(buffer),
                    UUID_LIST_CODEC.decode(buffer),
                    STATION_TRANSFER_LINK_LIST_CODEC.decode(buffer),
                    UUID_LIST_CODEC.decode(buffer));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ClientboundRouteDataDeltaPayload payload) {
            ByteBufCodecs.VAR_LONG.encode(buffer, payload.baseRevision());
            ByteBufCodecs.VAR_LONG.encode(buffer, payload.revision());
            ByteBufCodecs.VAR_LONG.encode(buffer, payload.pipeRevisionUsed());
            STATION_GROUP_LIST_CODEC.encode(buffer, payload.updatedStationGroups());
            UUID_LIST_CODEC.encode(buffer, payload.removedStationGroupIds());
            PLATFORM_STOP_LIST_CODEC.encode(buffer, payload.updatedPlatformStops());
            UUID_LIST_CODEC.encode(buffer, payload.removedPlatformStopIds());
            ROUTE_LINE_LIST_CODEC.encode(buffer, payload.updatedRouteLines());
            UUID_LIST_CODEC.encode(buffer, payload.removedRouteLineIds());
            ROUTE_LAYOUT_LIST_CODEC.encode(buffer, payload.updatedRouteLayouts());
            UUID_LIST_CODEC.encode(buffer, payload.removedRouteLayoutIds());
            ROUTE_SECTION_LIST_CODEC.encode(buffer, payload.updatedRouteSections());
            UUID_LIST_CODEC.encode(buffer, payload.removedRouteSectionIds());
            ROUTE_SECTION_PATH_LIST_CODEC.encode(buffer, payload.updatedRouteSectionPaths());
            UUID_LIST_CODEC.encode(buffer, payload.removedRouteSectionPathIds());
            STATION_TRANSFER_LINK_LIST_CODEC.encode(buffer, payload.updatedStationTransferLinks());
            UUID_LIST_CODEC.encode(buffer, payload.removedStationTransferLinkIds());
        }
    };
    public ClientboundRouteDataDeltaPayload {
        updatedStationGroups = List.copyOf(updatedStationGroups);
        removedStationGroupIds = List.copyOf(removedStationGroupIds);
        updatedPlatformStops = List.copyOf(updatedPlatformStops);
        removedPlatformStopIds = List.copyOf(removedPlatformStopIds);
        updatedRouteLines = List.copyOf(updatedRouteLines);
        removedRouteLineIds = List.copyOf(removedRouteLineIds);
        updatedRouteLayouts = List.copyOf(updatedRouteLayouts);
        removedRouteLayoutIds = List.copyOf(removedRouteLayoutIds);
        updatedRouteSections = List.copyOf(updatedRouteSections);
        removedRouteSectionIds = List.copyOf(removedRouteSectionIds);
        updatedRouteSectionPaths = List.copyOf(updatedRouteSectionPaths);
        removedRouteSectionPathIds = List.copyOf(removedRouteSectionPathIds);
        updatedStationTransferLinks = List.copyOf(updatedStationTransferLinks);
        removedStationTransferLinkIds = List.copyOf(removedStationTransferLinkIds);
    }

    public boolean isEmpty() {
        return updatedStationGroups.isEmpty()
                && removedStationGroupIds.isEmpty()
                && updatedPlatformStops.isEmpty()
                && removedPlatformStopIds.isEmpty()
                && updatedRouteLines.isEmpty()
                && removedRouteLineIds.isEmpty()
                && updatedRouteLayouts.isEmpty()
                && removedRouteLayoutIds.isEmpty()
                && updatedRouteSections.isEmpty()
                && removedRouteSectionIds.isEmpty()
                && updatedRouteSectionPaths.isEmpty()
                && removedRouteSectionPathIds.isEmpty()
                && updatedStationTransferLinks.isEmpty()
                && removedStationTransferLinkIds.isEmpty();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
