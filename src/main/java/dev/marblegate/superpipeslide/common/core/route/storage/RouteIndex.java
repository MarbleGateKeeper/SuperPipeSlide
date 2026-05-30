package dev.marblegate.superpipeslide.common.core.route.storage;

import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionPath;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationTransferLink;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

final class RouteIndex {
    private final Map<DimensionPos, UUID> stationGroupIdByPosition = new HashMap<>();
    private final Map<PipeConnectionRef, UUID> platformStopIdByConnection = new HashMap<>();
    private final Map<UUID, List<UUID>> platformStopIdsByStationGroupId = new HashMap<>();
    private final Map<UUID, List<UUID>> routeLayoutIdsByRouteLineId = new HashMap<>();
    private final Map<UUID, List<UUID>> routeLayoutIdsByPlatformStopId = new HashMap<>();
    private final Map<UUID, List<UUID>> routeSectionIdsByRouteLayoutId = new HashMap<>();
    private final Map<PipeConnectionRef, List<UUID>> routeSectionIdsByConnection = new HashMap<>();
    private final Map<UUID, List<UUID>> stationTransferLinkIdsByStationGroupId = new HashMap<>();

    void rebuild(RouteNetworkStore store, Map<UUID, RouteSectionPath> routeSectionPaths) {
        this.stationGroupIdByPosition.clear();
        this.platformStopIdByConnection.clear();
        this.platformStopIdsByStationGroupId.clear();
        this.routeLayoutIdsByRouteLineId.clear();
        this.routeLayoutIdsByPlatformStopId.clear();
        this.routeSectionIdsByRouteLayoutId.clear();
        this.routeSectionIdsByConnection.clear();
        this.stationTransferLinkIdsByStationGroupId.clear();
        store.stationGroupValues().forEach(stationGroup -> this.stationGroupIdByPosition.put(new DimensionPos(stationGroup.levelKey(), stationGroup.stationBlockPos()), stationGroup.id()));
        for (PlatformStop platformStop : store.platformStopValues()) {
            this.platformStopIdByConnection.put(platformStop.connectionRef(), platformStop.id());
            this.platformStopIdsByStationGroupId.computeIfAbsent(platformStop.stationGroupId(), ignored -> new ArrayList<>()).add(platformStop.id());
        }
        for (RouteLayout routeLayout : store.routeLayoutValues()) {
            this.routeLayoutIdsByRouteLineId.computeIfAbsent(routeLayout.routeLineId(), ignored -> new ArrayList<>()).add(routeLayout.id());
            for (UUID platformStopId : routeLayout.orderedPlatformStops()) {
                this.routeLayoutIdsByPlatformStopId.computeIfAbsent(platformStopId, ignored -> new ArrayList<>()).add(routeLayout.id());
            }
        }
        for (RouteSection section : store.routeSectionValues()) {
            this.routeSectionIdsByRouteLayoutId.computeIfAbsent(section.routeLayoutId(), ignored -> new ArrayList<>()).add(section.id());
            Optional<PlatformStop> from = store.platformStop(section.fromPlatformStopId());
            Optional<PlatformStop> to = store.platformStop(section.toPlatformStopId());
            from.ifPresent(platformStop -> this.indexRouteSectionConnection(platformStop.connectionRef(), section.id()));
            to.ifPresent(platformStop -> this.indexRouteSectionConnection(platformStop.connectionRef(), section.id()));
            RouteSectionPath path = routeSectionPaths.get(section.id());
            if (path != null) {
                for (PipeConnectionRef connection : path.forwardConnections()) {
                    this.indexRouteSectionConnection(connection, section.id());
                }
                for (PipeConnectionRef connection : path.reverseConnections()) {
                    this.indexRouteSectionConnection(connection, section.id());
                }
                continue;
            }
        }
        store.stationTransferLinkValues().forEach(this::upsertStationTransferLink);
    }

    void upsertStationGroup(StationGroup stationGroup) {
        this.removeStationGroup(stationGroup.id());
        this.stationGroupIdByPosition.put(new DimensionPos(stationGroup.levelKey(), stationGroup.stationBlockPos()), stationGroup.id());
    }

    void removeStationGroup(UUID stationGroupId) {
        this.stationGroupIdByPosition.values().removeIf(stationGroupId::equals);
        this.platformStopIdsByStationGroupId.remove(stationGroupId);
        this.stationTransferLinkIdsByStationGroupId.remove(stationGroupId);
    }

    void upsertPlatformStop(PlatformStop platformStop) {
        this.removePlatformStop(platformStop.id());
        this.platformStopIdByConnection.put(platformStop.connectionRef(), platformStop.id());
        addUnique(this.platformStopIdsByStationGroupId.computeIfAbsent(platformStop.stationGroupId(), ignored -> new ArrayList<>()), platformStop.id());
    }

    void removePlatformStop(UUID platformStopId) {
        this.platformStopIdByConnection.values().removeIf(platformStopId::equals);
        removeFromAll(this.platformStopIdsByStationGroupId, platformStopId);
        removeFromAll(this.routeLayoutIdsByPlatformStopId, platformStopId);
    }

    void upsertRouteLayout(RouteLayout routeLayout) {
        this.removeRouteLayout(routeLayout.id());
        addUnique(this.routeLayoutIdsByRouteLineId.computeIfAbsent(routeLayout.routeLineId(), ignored -> new ArrayList<>()), routeLayout.id());
        for (UUID platformStopId : routeLayout.orderedPlatformStops()) {
            addUnique(this.routeLayoutIdsByPlatformStopId.computeIfAbsent(platformStopId, ignored -> new ArrayList<>()), routeLayout.id());
        }
    }

    void removeRouteLayout(UUID routeLayoutId) {
        removeFromAll(this.routeLayoutIdsByRouteLineId, routeLayoutId);
        removeFromAll(this.routeLayoutIdsByPlatformStopId, routeLayoutId);
        this.routeSectionIdsByRouteLayoutId.remove(routeLayoutId);
    }

    void upsertRouteSection(RouteSection section, RouteNetworkStore store, Map<UUID, RouteSectionPath> routeSectionPaths) {
        this.removeRouteSection(section.id());
        addUnique(this.routeSectionIdsByRouteLayoutId.computeIfAbsent(section.routeLayoutId(), ignored -> new ArrayList<>()), section.id());
        store.platformStop(section.fromPlatformStopId()).ifPresent(platformStop -> this.indexRouteSectionConnection(platformStop.connectionRef(), section.id()));
        store.platformStop(section.toPlatformStopId()).ifPresent(platformStop -> this.indexRouteSectionConnection(platformStop.connectionRef(), section.id()));
        RouteSectionPath path = routeSectionPaths.get(section.id());
        if (path == null) {
            return;
        }
        for (PipeConnectionRef connection : path.forwardConnections()) {
            this.indexRouteSectionConnection(connection, section.id());
        }
        for (PipeConnectionRef connection : path.reverseConnections()) {
            this.indexRouteSectionConnection(connection, section.id());
        }
    }

    void removeRouteSection(UUID routeSectionId) {
        removeFromAll(this.routeSectionIdsByRouteLayoutId, routeSectionId);
        removeFromAll(this.routeSectionIdsByConnection, routeSectionId);
    }

    void upsertStationTransferLink(StationTransferLink link) {
        this.removeStationTransferLink(link.id());
        addUnique(this.stationTransferLinkIdsByStationGroupId.computeIfAbsent(link.firstStationGroupId(), ignored -> new ArrayList<>()), link.id());
        addUnique(this.stationTransferLinkIdsByStationGroupId.computeIfAbsent(link.secondStationGroupId(), ignored -> new ArrayList<>()), link.id());
    }

    void removeStationTransferLink(UUID linkId) {
        removeFromAll(this.stationTransferLinkIdsByStationGroupId, linkId);
    }

    Optional<UUID> stationGroupIdByPosition(ResourceKey<Level> levelKey, BlockPos pos) {
        return Optional.ofNullable(this.stationGroupIdByPosition.get(new DimensionPos(levelKey, pos)));
    }

    Optional<UUID> platformStopIdByConnection(PipeConnectionRef connection) {
        return Optional.ofNullable(this.platformStopIdByConnection.get(connection));
    }

    List<UUID> platformStopIdsByStationGroup(UUID stationGroupId) {
        return List.copyOf(this.platformStopIdsByStationGroupId.getOrDefault(stationGroupId, List.of()));
    }

    List<UUID> routeLayoutIdsByPlatformStop(UUID platformStopId) {
        return List.copyOf(this.routeLayoutIdsByPlatformStopId.getOrDefault(platformStopId, List.of()));
    }

    List<UUID> routeSectionIdsByConnection(PipeConnectionRef connection) {
        return List.copyOf(this.routeSectionIdsByConnection.getOrDefault(connection, List.of()));
    }

    List<UUID> stationTransferLinkIdsByStationGroup(UUID stationGroupId) {
        return List.copyOf(this.stationTransferLinkIdsByStationGroupId.getOrDefault(stationGroupId, List.of()));
    }

    private void indexRouteSectionConnection(PipeConnectionRef connection, UUID sectionId) {
        List<UUID> sectionIds = this.routeSectionIdsByConnection.computeIfAbsent(connection, ignored -> new ArrayList<>());
        addUnique(sectionIds, sectionId);
    }

    private static <K, V> void removeFromAll(Map<K, List<V>> map, V value) {
        map.values().removeIf(values -> {
            values.removeIf(value::equals);
            return values.isEmpty();
        });
    }

    private static <V> void addUnique(List<V> values, V value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private record DimensionPos(ResourceKey<Level> levelKey, BlockPos pos) {
        private DimensionPos {
            pos = pos.immutable();
        }
    }
}
