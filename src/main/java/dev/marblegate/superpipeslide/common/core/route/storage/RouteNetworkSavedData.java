package dev.marblegate.superpipeslide.common.core.route.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import dev.marblegate.superpipeslide.common.core.networkgraph.model.PipeNetworkChangeSet;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkSavedData;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkView;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.ServerPipeNetworkView;
import dev.marblegate.superpipeslide.common.core.path.PipeGraphSnapshot;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayoutSectionRef;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionPath;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionPathRecord;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;
import dev.marblegate.superpipeslide.common.core.route.model.section.StopBehavior;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationTransferLink;
import dev.marblegate.superpipeslide.common.core.route.service.RouteInvalidationService;
import dev.marblegate.superpipeslide.common.core.route.service.RouteSectionService;
import dev.marblegate.superpipeslide.common.core.route.service.RouteValidationService;
import dev.marblegate.superpipeslide.common.core.route.sync.RouteSyncTracker;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.config.Config;
import dev.marblegate.superpipeslide.network.sync.route.ClientboundRouteDataDeltaPayload;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class RouteNetworkSavedData extends SavedData {
    private static final int ROUTE_PATH_RULES_VERSION = 3;

    public static final Codec<RouteNetworkSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StationGroup.CODEC.listOf().optionalFieldOf("station_groups", List.of()).forGetter(RouteNetworkSavedData::stationGroupsForCodec),
            PlatformStop.CODEC.listOf().optionalFieldOf("platform_stops", List.of()).forGetter(RouteNetworkSavedData::platformStopsForCodec),
            RouteLine.CODEC.listOf().optionalFieldOf("route_lines", List.of()).forGetter(RouteNetworkSavedData::routeLinesForCodec),
            RouteLayout.CODEC.listOf().optionalFieldOf("route_layouts", List.of()).forGetter(RouteNetworkSavedData::routeLayoutsForCodec),
            RouteSection.CODEC.listOf().optionalFieldOf("route_sections", List.of()).forGetter(RouteNetworkSavedData::routeSectionsForCodec),
            RouteSectionPathRecord.CODEC.listOf().optionalFieldOf("route_section_paths", List.of()).forGetter(RouteNetworkSavedData::routeSectionPathsForCodec),
            StationTransferLink.CODEC.listOf().optionalFieldOf("station_transfer_links", List.of()).forGetter(RouteNetworkSavedData::stationTransferLinksForCodec),
            Codec.INT.optionalFieldOf("route_path_rules_version", 0).forGetter(RouteNetworkSavedData::routePathRulesVersionForCodec),
            Codec.LONG.optionalFieldOf("revision", 0L).forGetter(RouteNetworkSavedData::revision)
    ).apply(instance, RouteNetworkSavedData::new));

    public static final SavedDataType<RouteNetworkSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "route_network"),
            RouteNetworkSavedData::new,
            CODEC
    );

    private final RouteNetworkStore store;
    private final RouteIndex index = new RouteIndex();
    private final RouteSyncTracker syncTracker = new RouteSyncTracker();
    private final RouteSectionWorkQueue sectionWorkQueue = new RouteSectionWorkQueue();
    private final Map<UUID, RouteSectionPath> routeSectionPaths = new HashMap<>();
    private int routePathRulesVersion = ROUTE_PATH_RULES_VERSION;
    private long revision;
    private int batchDepth;
    private boolean batchRevisionDirty;

    public RouteNetworkSavedData() {
        this.store = new RouteNetworkStore();
        this.rebuildIndexes();
    }

    public RouteNetworkSavedData(List<StationGroup> stationGroups, List<PlatformStop> platformStops, List<RouteLine> routeLines, List<RouteLayout> routeLayouts, List<RouteSection> routeSections, List<RouteSectionPathRecord> routeSectionPathRecords, List<StationTransferLink> stationTransferLinks, int routePathRulesVersion, long revision) {
        this.store = new RouteNetworkStore(stationGroups, platformStops, routeLines, routeLayouts, routeSections, stationTransferLinks);
        boolean recomputeAllPaths = routePathRulesVersion != ROUTE_PATH_RULES_VERSION;
        if (!recomputeAllPaths) {
            routeSectionPathRecords.forEach(record -> this.routeSectionPaths.put(record.routeSectionId(), record.path()));
        }
        routeSections.stream()
                .filter(routeSection -> recomputeAllPaths || !this.routeSectionPaths.containsKey(routeSection.id()))
                .forEach(routeSection -> {
                    boolean bidirectional = this.store.routeLayout(routeSection.routeLayoutId()).map(RouteLayout::bidirectional).orElse(true);
                    this.store.put(new RouteSection(
                            routeSection.id(),
                            routeSection.routeLayoutId(),
                            routeSection.fromPlatformStopId(),
                            routeSection.toPlatformStopId(),
                            RouteSectionStatus.STALE,
                            bidirectional ? RouteSectionStatus.STALE : RouteSectionStatus.DISABLED,
                            routeSection.computedGraphRevision(),
                            routeSection.forwardLength(),
                            routeSection.reverseLength(),
                            routeSection.forwardBranchDecisions(),
                            routeSection.reverseBranchDecisions(),
                            routeSection.forwardStepDecisions(),
                            routeSection.reverseStepDecisions()
                    ));
                    this.sectionWorkQueue.markDirty(routeSection.id());
                });
        this.routePathRulesVersion = ROUTE_PATH_RULES_VERSION;
        this.revision = revision;
        this.rebuildIndexes();
    }

    public static RouteNetworkSavedData get(ServerLevel level) {
        return get(level.getServer());
    }

    public static RouteNetworkSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public long revision() {
        return this.revision;
    }

    private void beginBatch() {
        this.batchDepth++;
    }

    public Batch beginMutationBatch() {
        this.beginBatch();
        return new Batch(this);
    }

    private boolean endBatch() {
        if (this.batchDepth <= 0) {
            return false;
        }
        this.batchDepth--;
        if (this.batchDepth > 0 || !this.batchRevisionDirty) {
            return false;
        }
        this.batchRevisionDirty = false;
        this.bumpRevisionNow();
        return true;
    }

    public static final class Batch implements AutoCloseable {
        private final RouteNetworkSavedData owner;
        private boolean closed;
        private boolean changed;

        private Batch(RouteNetworkSavedData owner) {
            this.owner = owner;
        }

        public void include(boolean changed) {
            this.changed |= changed;
        }

        public boolean changed() {
            return this.changed;
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.changed |= this.owner.endBatch();
            this.closed = true;
        }
    }

    public Collection<StationGroup> stationGroups() {
        return this.store.stationGroups();
    }

    public Collection<PlatformStop> platformStops() {
        return this.store.platformStops();
    }

    public Collection<RouteLine> routeLines() {
        return this.store.routeLines();
    }

    public Collection<RouteLayout> routeLayouts() {
        return this.store.routeLayouts();
    }

    public Collection<RouteSection> routeSections() {
        return this.store.routeSections();
    }

    public Collection<RouteSectionPathRecord> routeSectionPathRecords() {
        return this.routeSectionPathsForCodec();
    }

    public Collection<StationTransferLink> stationTransferLinks() {
        return this.store.stationTransferLinks();
    }

    public ClientboundRouteDataDeltaPayload consumePendingRouteDelta(long pipeRevisionUsed) {
        return this.syncTracker.consume(this.revision, pipeRevisionUsed);
    }

    public Optional<StationGroup> stationGroup(UUID id) {
        return this.store.stationGroup(id);
    }

    public Optional<StationGroup> stationGroupAt(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> levelKey, net.minecraft.core.BlockPos pos) {
        return this.index.stationGroupIdByPosition(levelKey, pos).flatMap(this::stationGroup);
    }

    public Optional<PlatformStop> platformStop(UUID id) {
        return this.store.platformStop(id);
    }

    public Optional<RouteLine> routeLine(UUID id) {
        return this.store.routeLine(id);
    }

    public Optional<RouteLayout> routeLayout(UUID id) {
        return this.store.routeLayout(id);
    }

    public Optional<RouteSection> routeSection(UUID id) {
        return this.store.routeSection(id);
    }

    public Optional<RouteSectionPath> routeSectionPath(UUID id) {
        return Optional.ofNullable(this.routeSectionPaths.get(id));
    }

    public Optional<StationTransferLink> stationTransferLink(UUID id) {
        return this.store.stationTransferLink(id);
    }

    public boolean hasDirtyRouteSections() {
        return this.sectionWorkQueue.hasWork();
    }

    public Optional<PlatformStop> platformStopByConnection(PipeConnectionRef connection) {
        return this.index.platformStopIdByConnection(connection).flatMap(this::platformStop);
    }

    public List<PlatformStop> platformStopsInStation(UUID stationGroupId) {
        return this.index.platformStopIdsByStationGroup(stationGroupId).stream()
                .map(id -> this.store.platformStop(id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public List<StationTransferLink> stationTransferLinksForStation(UUID stationGroupId) {
        return this.index.stationTransferLinkIdsByStationGroup(stationGroupId).stream()
                .map(id -> this.store.stationTransferLink(id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public StationGroup createStationGroup(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> levelKey, net.minecraft.core.BlockPos stationBlockPos) {
        return this.createStationGroup(levelKey, stationBlockPos, null);
    }

    public StationGroup createStationGroup(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> levelKey, net.minecraft.core.BlockPos stationBlockPos, String languageCode) {
        Optional<StationGroup> existing = this.stationGroupAt(levelKey, stationBlockPos);
        if (existing.isPresent()) {
            return existing.get();
        }
        StationGroup stationGroup = new StationGroup(UUID.randomUUID(), levelKey, stationBlockPos, this.nextDefaultStationName(languageCode), List.of(), Config.PLATFORM_CLAIM_RADIUS.getAsDouble());
        try (Batch ignored = this.beginMutationBatch()) {
            this.store.put(stationGroup);
            this.trackUpdated(stationGroup);
            this.createAutomaticTransferLinks(stationGroup);
            this.bumpRevision();
        }
        return stationGroup;
    }

    public Optional<StationGroup> updateStationGroup(UUID id, String primaryName, List<String> translatedNames) {
        StationGroup existing = this.store.stationGroup(id).orElse(null);
        if (existing == null) {
            return Optional.empty();
        }
        StationGroup updated = existing.withNames(primaryName, translatedNames);
        this.store.put(updated);
        this.trackUpdated(updated);
        this.bumpRevision();
        return Optional.of(updated);
    }

    public Optional<StationTransferLink> stationTransferLinkBetween(UUID firstStationGroupId, UUID secondStationGroupId) {
        StationTransferLink.StationPair pair = StationTransferLink.StationPair.of(firstStationGroupId, secondStationGroupId);
        return this.store.stationTransferLinkValues().stream()
                .filter(link -> link.firstStationGroupId().equals(pair.first()) && link.secondStationGroupId().equals(pair.second()))
                .findFirst();
    }

    public Optional<StationTransferLink> createStationTransferLink(UUID firstStationGroupId, UUID secondStationGroupId, boolean manual, boolean risky) {
        StationGroup first = this.store.stationGroup(firstStationGroupId).orElse(null);
        StationGroup second = this.store.stationGroup(secondStationGroupId).orElse(null);
        if (first == null || second == null || first.id().equals(second.id())) {
            return Optional.empty();
        }
        Optional<StationTransferLink> existing = this.stationTransferLinkBetween(first.id(), second.id());
        if (existing.isPresent()) {
            return existing;
        }
        int estimatedWalkTicks = estimatedTransferWalkTicks(first, second);
        StationTransferLink link = new StationTransferLink(UUID.randomUUID(), first.id(), second.id(), estimatedWalkTicks, manual, risky);
        this.store.put(link);
        this.trackUpdated(link);
        this.bumpRevision();
        return Optional.of(link);
    }

    public boolean deleteStationTransferLink(UUID stationGroupId, UUID otherStationGroupId) {
        Optional<StationTransferLink> existing = this.stationTransferLinkBetween(stationGroupId, otherStationGroupId);
        if (existing.isEmpty()) {
            return false;
        }
        this.store.removeStationTransferLink(existing.get().id());
        this.trackRemovedStationTransferLink(existing.get().id());
        this.bumpRevision();
        return true;
    }

    public boolean deleteStationGroupAt(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> levelKey, net.minecraft.core.BlockPos pos, PipeNetworkSavedData pipeData) {
        return this.deleteStationGroupAt(levelKey, pos, pipeData, pipeData);
    }

    public boolean deleteStationGroupAt(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> levelKey, net.minecraft.core.BlockPos pos, PipeNetworkSavedData pipeData, PipeNetworkView pipeNetwork) {
        Optional<StationGroup> stationGroup = this.stationGroupAt(levelKey, pos);
        if (stationGroup.isEmpty()) {
            return false;
        }
        try (Batch ignored = this.beginMutationBatch()) {
            UUID stationGroupId = stationGroup.get().id();
            List<UUID> platformStopIds = this.platformStopsInStation(stationGroupId).stream()
                    .map(PlatformStop::id)
                    .toList();
            for (UUID platformStopId : platformStopIds) {
                this.deletePlatformStop(platformStopId, pipeData, pipeNetwork);
            }
            for (StationTransferLink link : this.stationTransferLinksForStation(stationGroupId)) {
                this.store.removeStationTransferLink(link.id());
                this.trackRemovedStationTransferLink(link.id());
            }
            this.store.removeStationGroup(stationGroupId);
            this.trackRemovedStationGroup(stationGroupId);
            SuperPipeSlide.LOGGER.debug("Removed station group {} at {}", stationGroupId, pos);
            this.bumpRevision();
            return true;
        }
    }

    public PlatformStop createPlatformStop(StationGroup stationGroup, PipeConnection connection, PipeNetworkSavedData pipeData) {
        PipeConnectionRef connectionRef = PipeConnectionRef.of(connection);
        Optional<PlatformStop> existing = this.platformStopByConnection(connectionRef);
        if (existing.isPresent()) {
            return existing.get();
        }
        String nextNumber = Integer.toString(this.platformStopsInStation(stationGroup.id()).size() + 1);
        PlatformStop platformStop = new PlatformStop(UUID.randomUUID(), stationGroup.id(), Optional.empty(), nextNumber, Optional.empty(), connectionRef, connection.length());
        this.store.put(platformStop);
        this.trackUpdated(platformStop);
        pipeData.updateConnectionPlatformStop(connection.id(), Optional.of(platformStop.id()));
        this.markSectionsReferencing(connectionRef, RouteSectionStatus.STALE);
        this.bumpRevision();
        return platformStop;
    }

    public Optional<PlatformStop> updatePlatformStop(UUID id, String platformNumber, Optional<String> displayName) {
        PlatformStop existing = this.store.platformStop(id).orElse(null);
        if (existing == null) {
            return Optional.empty();
        }
        PlatformStop updated = existing.withDisplay(platformNumber, displayName);
        this.store.put(updated);
        this.trackUpdated(updated);
        this.bumpRevision();
        return Optional.of(updated);
    }

    public Optional<RouteLine> createRouteLine(String displayName, List<String> translatedNames, List<Integer> themeColors) {
        RouteLine routeLine = new RouteLine(UUID.randomUUID(), displayName, translatedNames, themeColors, List.of(), true);
        this.store.put(routeLine);
        this.trackUpdated(routeLine);
        this.bumpRevision();
        return Optional.of(routeLine);
    }

    public Optional<RouteLine> updateRouteLine(UUID routeLineId, String displayName, List<String> translatedNames, List<Integer> themeColors) {
        RouteLine line = this.store.routeLine(routeLineId).orElse(null);
        if (line == null) {
            return Optional.empty();
        }
        RouteLine updated = line.withMetadata(displayName, translatedNames, themeColors);
        this.store.put(updated);
        this.trackUpdated(updated);
        this.bumpRevision();
        return Optional.of(updated);
    }

    public boolean deleteRouteLine(UUID routeLineId) {
        RouteLine line = this.store.removeRouteLine(routeLineId);
        if (line == null) {
            return false;
        }
        this.trackRemovedRouteLine(routeLineId);
        try (Batch ignored = this.beginMutationBatch()) {
            for (UUID layoutId : List.copyOf(line.layoutIds())) {
                this.deleteRouteLayout(layoutId);
            }
            this.clearPlatformRouteOwnership(routeLineId);
            this.bumpRevision();
            return true;
        }
    }

    public Optional<RouteLayout> createRouteLayout(UUID routeLineId, Optional<String> displayName, List<String> translatedNames, boolean bidirectional, boolean loop, boolean nameAsSectionName) {
        RouteLine line = this.store.routeLine(routeLineId).orElse(null);
        if (line == null) {
            return Optional.empty();
        }
        RouteLayout layout = new RouteLayout(UUID.randomUUID(), routeLineId, displayName, translatedNames, Optional.empty(), List.of(), List.of(), bidirectional, loop, nameAsSectionName);
        this.store.put(layout);
        this.trackUpdated(layout);
        List<UUID> layoutIds = new ArrayList<>(line.layoutIds());
        layoutIds.add(layout.id());
        RouteLine updatedLine = line.withLayoutIds(layoutIds);
        this.store.put(updatedLine);
        this.trackUpdated(updatedLine);
        this.bumpRevision();
        return Optional.of(layout);
    }

    public Optional<RouteLayout> updateRouteLayout(UUID routeLayoutId, Optional<String> displayName, List<String> translatedNames, boolean bidirectional, boolean loop, boolean nameAsSectionName, PipeNetworkView pipeNetwork) {
        RouteLayout layout = this.store.routeLayout(routeLayoutId).orElse(null);
        if (layout == null) {
            return Optional.empty();
        }
        RouteLayout updated = layout.withMetadata(displayName, translatedNames, bidirectional, loop, nameAsSectionName);
        this.store.put(updated);
        this.trackUpdated(updated);
        if (layout.loop() != loop || layout.bidirectional() != bidirectional) {
            updated = this.replaceLayoutStopsAndRebuildSections(updated, updated.orderedPlatformStops(), pipeNetwork);
        }
        this.bumpRevision();
        return Optional.of(updated);
    }

    public Optional<RouteLayout> splitBidirectionalLayout(UUID routeLayoutId, String reverseSuffix, PipeNetworkView pipeNetwork) {
        RouteLayout layout = this.store.routeLayout(routeLayoutId).orElse(null);
        if (layout == null || !layout.bidirectional()) {
            return Optional.empty();
        }
        RouteLine line = this.store.routeLine(layout.routeLineId()).orElse(null);
        if (line == null) {
            return Optional.empty();
        }

        try (Batch ignored = this.beginMutationBatch()) {
            RouteLayout forward = layout.withMetadata(layout.displayName(), layout.translatedNames(), false, layout.loop(), layout.nameAsSectionName());
            forward = this.replaceLayoutStopsAndRebuildSections(forward, forward.orderedPlatformStops(), pipeNetwork);

            String suffix = reverseSuffix == null || reverseSuffix.isBlank() ? "Reverse" : reverseSuffix.trim();
            Optional<String> reverseName = Optional.of(forward.displayName().filter(name -> !name.isBlank()).orElse("Layout") + " " + suffix);
            List<String> reverseTranslatedNames = forward.translatedNames().stream()
                    .filter(name -> !name.isBlank())
                    .map(name -> name + " " + suffix)
                    .toList();
            List<UUID> reverseStops = new ArrayList<>(forward.orderedPlatformStops());
            java.util.Collections.reverse(reverseStops);
            RouteLayout reverse = new RouteLayout(UUID.randomUUID(), forward.routeLineId(), reverseName, reverseTranslatedNames, forward.terminalStationGroupId(), List.of(), List.of(), false, forward.loop(), forward.nameAsSectionName());
            this.store.put(reverse);
            this.trackUpdated(reverse);
            reverse = this.replaceLayoutStopsAndRebuildSections(reverse, reverseStops, pipeNetwork);

            List<UUID> layoutIds = new ArrayList<>(line.layoutIds());
            int insertAt = layoutIds.indexOf(routeLayoutId);
            if (insertAt < 0) {
                layoutIds.add(reverse.id());
            } else {
                layoutIds.add(insertAt + 1, reverse.id());
            }
            RouteLine updatedLine = line.withLayoutIds(layoutIds);
            this.store.put(updatedLine);
            this.trackUpdated(updatedLine);
            this.updatePlatformRouteOwnership(line.id());
            this.bumpRevision();
            return Optional.of(reverse);
        }
    }

    public boolean deleteRouteLayout(UUID routeLayoutId) {
        RouteLayout layout = this.store.removeRouteLayout(routeLayoutId);
        if (layout == null) {
            return false;
        }
        this.trackRemovedRouteLayout(routeLayoutId);
        layout.orderedSectionRefs().forEach(sectionRef -> {
            this.store.removeRouteSection(sectionRef.routeSectionId());
            this.routeSectionPaths.remove(sectionRef.routeSectionId());
            this.trackRemovedRouteSection(sectionRef.routeSectionId());
        });
        RouteLine line = this.store.routeLine(layout.routeLineId()).orElse(null);
        if (line != null) {
            List<UUID> layoutIds = new ArrayList<>(line.layoutIds());
            layoutIds.remove(routeLayoutId);
            RouteLine updatedLine = line.withLayoutIds(layoutIds);
            this.store.put(updatedLine);
            this.trackUpdated(updatedLine);
        }
        this.updatePlatformRouteOwnership(layout.routeLineId());
        this.bumpRevision();
        return true;
    }

    public boolean deletePlatformStop(UUID platformStopId, PipeNetworkSavedData pipeData) {
        return this.deletePlatformStop(platformStopId, pipeData, pipeData);
    }

    public boolean deletePlatformStop(UUID platformStopId, MinecraftServer server) {
        PlatformStop existing = this.store.platformStop(platformStopId).orElse(null);
        if (existing == null) {
            return false;
        }
        ServerLevel level = server.getLevel(existing.connectionRef().levelKey());
        PipeNetworkSavedData pipeData = level == null ? null : PipeNetworkSavedData.get(level);
        return this.deletePlatformStop(platformStopId, pipeData, dev.marblegate.superpipeslide.common.core.networkgraph.storage.ServerPipeNetworkView.of(server));
    }

    private boolean deletePlatformStop(UUID platformStopId, @javax.annotation.Nullable PipeNetworkSavedData pipeData, PipeNetworkView pipeNetwork) {
        PlatformStop removed = this.store.removePlatformStop(platformStopId);
        if (removed == null) {
            return false;
        }
        this.trackRemovedPlatformStop(platformStopId);
        try (Batch ignored = this.beginMutationBatch()) {
            SuperPipeSlide.LOGGER.debug("Removed platform stop {} for connection {}", removed.id(), removed.connectionRef());
            if (pipeData != null) {
                pipeData.updateConnectionPlatformStop(removed.connectionId(), Optional.empty());
            }
            Set<UUID> affectedRouteLineIds = new HashSet<>();
            for (RouteLayout layout : List.copyOf(this.store.routeLayoutValues())) {
                if (layout.orderedPlatformStops().contains(platformStopId)) {
                    SuperPipeSlide.LOGGER.debug("Preserved missing platform stop marker {} in route layout {}", platformStopId, layout.id());
                    this.rebuildLayoutSections(layout, pipeNetwork);
                    affectedRouteLineIds.add(layout.routeLineId());
                }
            }
            affectedRouteLineIds.forEach(this::updatePlatformRouteOwnership);
            this.bumpRevision();
            return true;
        }
    }

    public boolean prunePlatformStopsWithMissingConnections(ResourceKey<Level> levelKey, PipeNetworkSavedData pipeData) {
        List<UUID> stalePlatformStopIds = this.store.platformStopValues().stream()
                .filter(platformStop -> platformStop.connectionRef().levelKey().equals(levelKey))
                .filter(platformStop -> pipeData.connection(platformStop.connectionRef()).isEmpty())
                .map(PlatformStop::id)
                .toList();
        if (stalePlatformStopIds.isEmpty()) {
            return false;
        }
        SuperPipeSlide.LOGGER.debug("Pruned platform stops with missing pipe connections: {}", stalePlatformStopIds);
        try (Batch ignored = this.beginMutationBatch()) {
            stalePlatformStopIds.forEach(platformStopId -> this.deletePlatformStop(platformStopId, pipeData));
            return true;
        }
    }

    public boolean pruneUnavailableDimensions(Set<ResourceKey<Level>> availableDimensions, PipeNetworkView pipeNetwork) {
        Set<ResourceKey<Level>> available = Set.copyOf(availableDimensions);
        List<UUID> removedStationGroupIds = this.store.stationGroupValues().stream()
                .filter(stationGroup -> !available.contains(stationGroup.levelKey()))
                .map(StationGroup::id)
                .toList();
        List<UUID> removedPlatformStopIds = this.store.platformStopValues().stream()
                .filter(platformStop -> !available.contains(platformStop.connectionRef().levelKey())
                        || removedStationGroupIds.contains(platformStop.stationGroupId()))
                .map(PlatformStop::id)
                .toList();
        Set<UUID> removedStations = new HashSet<>(removedStationGroupIds);
        List<UUID> removedTransferLinkIds = this.store.stationTransferLinkValues().stream()
                .filter(link -> removedStations.contains(link.firstStationGroupId()) || removedStations.contains(link.secondStationGroupId()))
                .map(StationTransferLink::id)
                .toList();
        if (removedStationGroupIds.isEmpty() && removedPlatformStopIds.isEmpty() && removedTransferLinkIds.isEmpty()) {
            return false;
        }

        Set<UUID> removedStops = new HashSet<>(removedPlatformStopIds);
        Set<UUID> affectedLineIds = new HashSet<>();
        try (Batch ignored = this.beginMutationBatch()) {
            for (UUID platformStopId : removedPlatformStopIds) {
                PlatformStop removed = this.store.removePlatformStop(platformStopId);
                if (removed != null) {
                    this.trackRemovedPlatformStop(platformStopId);
                }
            }
            for (UUID stationGroupId : removedStationGroupIds) {
                if (this.store.removeStationGroup(stationGroupId) != null) {
                    this.trackRemovedStationGroup(stationGroupId);
                }
            }
            for (UUID linkId : removedTransferLinkIds) {
                if (this.store.removeStationTransferLink(linkId) != null) {
                    this.trackRemovedStationTransferLink(linkId);
                }
            }

            for (RouteLayout layout : List.copyOf(this.store.routeLayoutValues())) {
                List<UUID> retainedStops = layout.orderedPlatformStops().stream()
                        .filter(platformStopId -> !removedStops.contains(platformStopId))
                        .filter(this.store::hasPlatformStop)
                        .toList();
                if (!retainedStops.equals(layout.orderedPlatformStops())) {
                    this.replaceLayoutStopsAndRebuildSections(layout, retainedStops, pipeNetwork);
                    affectedLineIds.add(layout.routeLineId());
                }
            }
            affectedLineIds.forEach(this::updatePlatformRouteOwnership);
            this.bumpRevision();
        }
        SuperPipeSlide.LOGGER.debug("Pruned route data in unavailable dimensions: stations={}, platformStops={}", removedStationGroupIds.size(), removedPlatformStopIds.size());
        return true;
    }

    public Optional<RouteLayout> setLayoutStops(UUID layoutId, List<UUID> platformStopIds, PipeNetworkView pipeNetwork) {
        RouteLayout layout = this.store.routeLayout(layoutId).orElse(null);
        RouteValidationService.ValidationResult validation = RouteValidationService.validateLayoutStops(this, layout, platformStopIds);
        if (validation != RouteValidationService.ValidationResult.OK) {
            SuperPipeSlide.LOGGER.debug("Rejected station order update for layout {}: {}", layoutId, validation);
            return Optional.empty();
        }

        RouteLayout updated = this.replaceLayoutStopsAndRebuildSections(layout, platformStopIds, pipeNetwork);
        this.updatePlatformRouteOwnership(layout.routeLineId());
        this.bumpRevision();
        return Optional.of(updated);
    }

    private RouteLayout replaceLayoutStopsAndRebuildSections(RouteLayout layout, List<UUID> platformStopIds, PipeNetworkView pipeNetwork) {
        layout.orderedSectionRefs().forEach(sectionRef -> {
            this.store.removeRouteSection(sectionRef.routeSectionId());
            this.routeSectionPaths.remove(sectionRef.routeSectionId());
            this.sectionWorkQueue.remove(sectionRef.routeSectionId());
            this.trackRemovedRouteSection(sectionRef.routeSectionId());
        });
        List<RouteLayoutSectionRef> sectionRefs = new ArrayList<>();
        for (int i = 0; i + 1 < platformStopIds.size(); i++) {
            RouteSection section = this.createPendingRouteSection(UUID.randomUUID(), layout, platformStopIds.get(i), platformStopIds.get(i + 1));
            this.store.put(section);
            this.trackUpdated(section);
            this.sectionWorkQueue.markDirty(section.id());
            sectionRefs.add(new RouteLayoutSectionRef(section.id(), StopBehavior.STOP));
        }
        if (layout.loop() && platformStopIds.size() > 1) {
            RouteSection section = this.createPendingRouteSection(UUID.randomUUID(), layout, platformStopIds.get(platformStopIds.size() - 1), platformStopIds.get(0));
            this.store.put(section);
            this.trackUpdated(section);
            this.sectionWorkQueue.markDirty(section.id());
            sectionRefs.add(new RouteLayoutSectionRef(section.id(), StopBehavior.STOP));
        }

        RouteLayout updated = layout.withStopsAndSections(platformStopIds, sectionRefs);
        this.store.put(updated);
        this.trackUpdated(updated);
        return updated;
    }

    private void rebuildLayoutSections(RouteLayout layout, PipeNetworkView pipeNetwork) {
        this.replaceLayoutStopsAndRebuildSections(layout, layout.orderedPlatformStops(), pipeNetwork);
    }

    public boolean rebuildAllSections(PipeNetworkView pipeNetwork) {
        if (!this.store.hasRouteLayouts()) {
            return false;
        }
        long started = System.nanoTime();
        int layoutCount = this.store.routeLayoutCount();
        int sectionCountBefore = this.store.routeSectionCount();
        for (RouteLayout layout : List.copyOf(this.store.routeLayoutValues())) {
            this.rebuildLayoutSections(layout, pipeNetwork);
        }
        this.bumpRevision();
        SuperPipeSlide.LOGGER.debug("Rebuilt all route sections for {} layouts ({} -> {} sections) in {} ms",
                layoutCount,
                sectionCountBefore,
                this.store.routeSectionCount(),
                String.format("%.3f", (System.nanoTime() - started) / 1_000_000.0D));
        return true;
    }

    public boolean markSectionsAffectedByPipeChanges(PipeNetworkChangeSet changes, PipeNetworkView pipeNetwork) {
        boolean changed = RouteInvalidationService.apply(changes, this, pipeNetwork);
        if (changed) {
            this.bumpRevision();
        }
        return changed;
    }

    public boolean recomputeDirtySections(PipeNetworkView pipeNetwork, int budget) {
        if (!this.hasDirtyRouteSections() || budget <= 0) {
            return false;
        }
        boolean changed = false;
        int remaining = budget;
        PipeGraphSnapshot graph = PipeGraphSnapshot.of(pipeNetwork);
        while (remaining-- > 0 && this.hasDirtyRouteSections()) {
            changed |= this.recomputeNextDirtySection(pipeNetwork, graph);
        }
        if (changed) {
            this.bumpRevision();
        }
        return changed;
    }

    public boolean recomputeDirtySectionsForNanos(PipeNetworkView pipeNetwork, long maxNanos) {
        if (!this.hasDirtyRouteSections() || maxNanos <= 0L) {
            return false;
        }
        long deadline = System.nanoTime() + maxNanos;
        boolean changed = false;
        PipeGraphSnapshot graph = PipeGraphSnapshot.of(pipeNetwork);
        do {
            changed |= this.recomputeNextDirtySection(pipeNetwork, graph);
        } while (this.hasDirtyRouteSections() && System.nanoTime() < deadline);
        if (changed) {
            this.bumpRevision();
        }
        return changed;
    }

    private boolean recomputeNextDirtySection(PipeNetworkView pipeNetwork, PipeGraphSnapshot graph) {
        Optional<RouteSectionWorkQueue.WorkItem> workItem = this.sectionWorkQueue.poll(pipeNetwork.revision());
        if (workItem.isEmpty()) {
            return false;
        }

        UUID sectionId = workItem.get().sectionId();
        RouteSection current = this.store.routeSection(sectionId).orElse(null);
        if (current == null) {
            this.sectionWorkQueue.remove(sectionId);
            return false;
        }
        RouteLayout layout = this.store.routeLayout(current.routeLayoutId()).orElse(null);
        if (layout == null) {
            this.store.removeRouteSection(sectionId);
            this.routeSectionPaths.remove(sectionId);
            this.sectionWorkQueue.remove(sectionId);
            this.trackRemovedRouteSection(sectionId);
            return true;
        }
        RouteSectionPath previousPath = this.routeSectionPaths.get(sectionId);
        RouteSection updated = this.computeRouteSection(sectionId, layout, current.fromPlatformStopId(), current.toPlatformStopId(), pipeNetwork, graph);
        boolean pathChanged = !Objects.equals(previousPath, this.routeSectionPaths.get(sectionId));
        if (!updated.equals(current)) {
            this.store.put(updated);
            this.trackUpdated(updated);
            if (updated.statusForLayout(layout) == RouteSectionStatus.VALID) {
                this.sectionWorkQueue.clearRepairMarker(sectionId);
            }
            return true;
        }
        if (pathChanged) {
            this.index.upsertRouteSection(current, this.store, this.routeSectionPaths);
            this.trackUpdatedRouteSectionPath(sectionId);
            this.setDirty();
            return true;
        }
        if (workItem.get().repairAttempt() && updated.statusForLayout(layout) == RouteSectionStatus.VALID) {
            this.sectionWorkQueue.clearRepairMarker(sectionId);
        }
        return false;
    }

    public boolean isPlatformStopUnavailableForLayout(PlatformStop stop, UUID routeLineId, UUID routeLayoutId) {
        if (stop.routeLineId().filter(id -> !id.equals(routeLineId)).isPresent()) {
            return true;
        }
        return this.index.routeLayoutIdsByPlatformStop(stop.id()).stream()
                .map(this.store::routeLayout)
                .flatMap(Optional::stream)
                .anyMatch(layout -> !layout.routeLineId().equals(routeLineId));
    }

    public List<UUID> routeLayoutIdsForPlatformStop(UUID platformStopId) {
        return this.index.routeLayoutIdsByPlatformStop(platformStopId);
    }

    private RouteSection computeRouteSection(UUID sectionId, RouteLayout layout, UUID fromPlatformStopId, UUID toPlatformStopId, PipeNetworkView pipeNetwork, PipeGraphSnapshot graph) {
        RouteSectionService.ComputedSection computed = RouteSectionService.computeSection(sectionId, layout, fromPlatformStopId, toPlatformStopId, pipeNetwork, graph, this::platformStop);
        this.routeSectionPaths.put(sectionId, computed.path());
        return computed.section();
    }

    private RouteSection createPendingRouteSection(UUID sectionId, RouteLayout layout, UUID fromPlatformStopId, UUID toPlatformStopId) {
        this.routeSectionPaths.remove(sectionId);
        return new RouteSection(
                sectionId,
                layout.id(),
                fromPlatformStopId,
                toPlatformStopId,
                RouteSectionStatus.STALE,
                layout.bidirectional() ? RouteSectionStatus.STALE : RouteSectionStatus.DISABLED,
                -1L,
                0.0D,
                0.0D,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private void updatePlatformRouteOwnership(UUID routeLineId) {
        Set<UUID> platformsInLine = new HashSet<>();
        for (PlatformStop platformStop : List.copyOf(this.store.platformStopValues())) {
            if (platformStop.routeLineId().filter(routeLineId::equals).isPresent()) {
                PlatformStop updated = platformStop.withRouteLine(Optional.empty());
                this.store.put(updated);
                this.trackUpdated(updated);
            }
        }
        for (RouteLayout layout : this.store.routeLayoutValues()) {
            if (!layout.routeLineId().equals(routeLineId)) {
                continue;
            }
            platformsInLine.addAll(layout.orderedPlatformStops());
        }
        for (UUID platformStopId : platformsInLine) {
            PlatformStop platformStop = this.store.platformStop(platformStopId).orElse(null);
            if (platformStop != null) {
                PlatformStop updated = platformStop.withRouteLine(Optional.of(routeLineId));
                this.store.put(updated);
                this.trackUpdated(updated);
            }
        }
    }

    private void clearPlatformRouteOwnership(UUID routeLineId) {
        for (PlatformStop platformStop : List.copyOf(this.store.platformStopValues())) {
            if (platformStop.routeLineId().filter(routeLineId::equals).isPresent()) {
                PlatformStop updated = platformStop.withRouteLine(Optional.empty());
                this.store.put(updated);
                this.trackUpdated(updated);
            }
        }
    }

    public void markSectionsReferencing(PipeConnectionRef connection, RouteSectionStatus status) {
        boolean changed = false;
        for (UUID sectionId : this.index.routeSectionIdsByConnection(connection)) {
            RouteSection section = this.store.routeSection(sectionId).orElse(null);
            if (section == null) {
                continue;
            }
            RouteSectionPath path = this.routeSectionPaths.get(sectionId);
            boolean forward = path == null || path.forwardConnections().contains(connection);
            boolean reverse = path == null || path.reverseConnections().contains(connection);
            boolean forwardChanged = forward && section.forwardStatus() != status;
            boolean reverseChanged = reverse && section.reverseStatus() != RouteSectionStatus.DISABLED && section.reverseStatus() != status;
            if (forwardChanged || reverseChanged) {
                this.sectionWorkQueue.markDirty(section.id());
                RouteSection updated = new RouteSection(
                        section.id(),
                        section.routeLayoutId(),
                        section.fromPlatformStopId(),
                        section.toPlatformStopId(),
                        forward ? status : section.forwardStatus(),
                        reverse && section.reverseStatus() != RouteSectionStatus.DISABLED ? status : section.reverseStatus(),
                        section.computedGraphRevision(),
                        section.forwardLength(),
                        section.reverseLength(),
                        section.forwardBranchDecisions(),
                        section.reverseBranchDecisions(),
                        section.forwardStepDecisions(),
                        section.reverseStepDecisions()
                );
                this.store.put(updated);
                this.trackUpdated(updated);
                changed = true;
            }
        }
        if (changed) {
            this.bumpRevision();
        }
    }

    public List<UUID> routeSectionIdsReferencingConnection(PipeConnectionRef connection) {
        return this.index.routeSectionIdsByConnection(connection);
    }

    public void markSectionDirty(UUID sectionId) {
        if (this.store.routeSection(sectionId).isPresent()) {
            this.sectionWorkQueue.markDirty(sectionId);
        }
    }

    public void enqueueSectionRepair(UUID sectionId, long pipeRevision) {
        RouteSection section = this.store.routeSection(sectionId).orElse(null);
        if (section == null) {
            return;
        }
        RouteLayout layout = this.store.routeLayout(section.routeLayoutId()).orElse(null);
        if (layout != null && section.statusForLayout(layout) == RouteSectionStatus.VALID) {
            return;
        }
        this.sectionWorkQueue.enqueueRepair(sectionId, pipeRevision);
    }

    public List<UUID> routeSectionIdsNearAnchors(Set<PipeAnchorId> anchors, PipeNetworkView pipeNetwork) {
        if (anchors.isEmpty()) {
            return List.of();
        }
        Set<UUID> sectionIds = new LinkedHashSet<>();
        for (PipeAnchorId anchor : anchors) {
            for (PipeConnection connection : pipeNetwork.connectionsTouching(anchor)) {
                sectionIds.addAll(this.routeSectionIdsReferencingConnection(PipeConnectionRef.of(connection)));
            }
        }
        return List.copyOf(sectionIds);
    }

    public List<UUID> nonValidRouteSectionIds() {
        return this.store.routeSectionValues().stream()
                .filter(section -> this.store.routeLayout(section.routeLayoutId())
                        .map(layout -> section.statusForLayout(layout) != RouteSectionStatus.VALID)
                        .orElse(true))
                .map(RouteSection::id)
                .toList();
    }

    public boolean markSectionStale(UUID sectionId) {
        RouteSection section = this.store.routeSection(sectionId).orElse(null);
        if (section == null) {
            return false;
        }
        this.sectionWorkQueue.markDirty(section.id());
        RouteLayout layout = this.store.routeLayout(section.routeLayoutId()).orElse(null);
        RouteSectionStatus reverseStatus = layout != null && !layout.bidirectional() ? RouteSectionStatus.DISABLED : RouteSectionStatus.STALE;
        if (section.forwardStatus() == RouteSectionStatus.STALE && section.reverseStatus() == reverseStatus) {
            return false;
        }
        RouteSection updated = new RouteSection(
                section.id(),
                section.routeLayoutId(),
                section.fromPlatformStopId(),
                section.toPlatformStopId(),
                RouteSectionStatus.STALE,
                reverseStatus,
                section.computedGraphRevision(),
                section.forwardLength(),
                section.reverseLength(),
                section.forwardBranchDecisions(),
                section.reverseBranchDecisions(),
                section.forwardStepDecisions(),
                section.reverseStepDecisions()
        );
        this.store.put(updated);
        this.trackUpdated(updated);
        return true;
    }

    private void createAutomaticTransferLinks(StationGroup stationGroup) {
        double radius = Config.AUTO_OUT_OF_STATION_TRANSFER_RADIUS.getAsDouble();
        double radiusSqr = radius * radius;
        for (StationGroup other : this.store.stationGroupValues()) {
            if (other.id().equals(stationGroup.id()) || !other.levelKey().equals(stationGroup.levelKey())) {
                continue;
            }
            if (stationDistanceSqr(stationGroup, other) > radiusSqr) {
                continue;
            }
            this.createStationTransferLink(stationGroup.id(), other.id(), false, false);
        }
    }

    private String nextDefaultStationName(String languageCode) {
        String prefix = defaultStationNamePrefix(languageCode);
        int index = this.store.stationGroups.size() + 1;
        while (true) {
            String name = prefix + " " + String.format(java.util.Locale.ROOT, "%03d", index);
            boolean exists = this.store.stationGroupValues().stream().anyMatch(station -> station.primaryName().equals(name));
            if (!exists) {
                return name;
            }
            index++;
        }
    }

    private static String defaultStationNamePrefix(String languageCode) {
        String normalized = languageCode == null ? "" : languageCode.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("zh")) {
            return "站点";
        }
        return "Station";
    }

    public static int estimatedTransferWalkTicks(StationGroup first, StationGroup second) {
        double distance = first.levelKey().equals(second.levelKey()) ? Math.sqrt(stationDistanceSqr(first, second)) : 192.0D;
        return Math.max(20 * 6, (int) Math.round(20 * 8 + distance * 8.0D));
    }

    public static double stationDistanceSqr(StationGroup first, StationGroup second) {
        long dx = (long) first.stationBlockPos().getX() - second.stationBlockPos().getX();
        long dy = (long) first.stationBlockPos().getY() - second.stationBlockPos().getY();
        long dz = (long) first.stationBlockPos().getZ() - second.stationBlockPos().getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private void bumpRevision() {
        if (this.batchDepth > 0) {
            this.batchRevisionDirty = true;
            return;
        }
        this.bumpRevisionNow();
    }

    private void bumpRevisionNow() {
        this.syncTracker.captureBaseRevision(this.revision);
        this.revision++;
        this.setDirty();
    }

    private void trackUpdated(StationGroup stationGroup) {
        this.index.upsertStationGroup(stationGroup);
        this.syncTracker.trackUpdated(stationGroup);
    }

    private void trackRemovedStationGroup(UUID id) {
        this.index.removeStationGroup(id);
        this.syncTracker.trackRemovedStationGroup(id);
    }

    private void trackUpdated(PlatformStop platformStop) {
        this.index.upsertPlatformStop(platformStop);
        this.syncTracker.trackUpdated(platformStop);
    }

    private void trackRemovedPlatformStop(UUID id) {
        this.index.removePlatformStop(id);
        this.syncTracker.trackRemovedPlatformStop(id);
    }

    private void trackUpdated(RouteLine routeLine) {
        this.syncTracker.trackUpdated(routeLine);
    }

    private void trackRemovedRouteLine(UUID id) {
        this.syncTracker.trackRemovedRouteLine(id);
    }

    private void trackUpdated(RouteLayout routeLayout) {
        this.index.upsertRouteLayout(routeLayout);
        this.syncTracker.trackUpdated(routeLayout);
    }

    private void trackRemovedRouteLayout(UUID id) {
        this.index.removeRouteLayout(id);
        this.syncTracker.trackRemovedRouteLayout(id);
    }

    private void trackUpdated(RouteSection routeSection) {
        this.index.upsertRouteSection(routeSection, this.store, this.routeSectionPaths);
        this.syncTracker.trackUpdated(routeSection);
        this.trackUpdatedRouteSectionPath(routeSection.id());
    }

    private void trackRemovedRouteSection(UUID id) {
        this.index.removeRouteSection(id);
        this.syncTracker.trackRemovedRouteSection(id);
        this.syncTracker.trackRemovedRouteSectionPath(id);
        this.sectionWorkQueue.remove(id);
    }

    private void trackUpdatedRouteSectionPath(UUID sectionId) {
        RouteSectionPath path = this.routeSectionPaths.get(sectionId);
        if (path == null) {
            this.syncTracker.trackRemovedRouteSectionPath(sectionId);
            return;
        }
        this.syncTracker.trackUpdated(new RouteSectionPathRecord(sectionId, path));
    }

    private void trackUpdated(StationTransferLink link) {
        this.index.upsertStationTransferLink(link);
        this.syncTracker.trackUpdated(link);
    }

    private void trackRemovedStationTransferLink(UUID id) {
        this.index.removeStationTransferLink(id);
        this.syncTracker.trackRemovedStationTransferLink(id);
    }

    private void rebuildIndexes() {
        this.index.rebuild(this.store, this.routeSectionPaths);
    }

    private List<StationGroup> stationGroupsForCodec() {
        return List.copyOf(this.store.stationGroupValues());
    }

    private List<PlatformStop> platformStopsForCodec() {
        return List.copyOf(this.store.platformStopValues());
    }

    private List<RouteLine> routeLinesForCodec() {
        return List.copyOf(this.store.routeLineValues());
    }

    private List<RouteLayout> routeLayoutsForCodec() {
        return List.copyOf(this.store.routeLayoutValues());
    }

    private List<RouteSection> routeSectionsForCodec() {
        return List.copyOf(this.store.routeSectionValues());
    }

    private List<RouteSectionPathRecord> routeSectionPathsForCodec() {
        return this.routeSectionPaths.entrySet().stream()
                .filter(entry -> this.store.routeSection(entry.getKey()).isPresent())
                .map(entry -> new RouteSectionPathRecord(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<StationTransferLink> stationTransferLinksForCodec() {
        return List.copyOf(this.store.stationTransferLinkValues());
    }

    private int routePathRulesVersionForCodec() {
        return this.routePathRulesVersion;
    }

}
