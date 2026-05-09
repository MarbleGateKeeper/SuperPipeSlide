package dev.marblegate.superpipeslide.common.event;

import dev.marblegate.superpipeslide.common.core.appearance.storage.PipeAppearanceSavedData;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.networkgraph.model.PipeNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkSavedData;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.ServerPipeNetworkView;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionPathRecord;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationTransferLink;
import dev.marblegate.superpipeslide.common.core.route.storage.RouteNetworkSavedData;
import dev.marblegate.superpipeslide.common.core.slide.ServerSlideController;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.network.editor.ClientboundEditorResultPayload;
import dev.marblegate.superpipeslide.network.sync.pipe.PipeNetworkSnapshotChunkPayload;
import dev.marblegate.superpipeslide.network.sync.pipe.PipeNetworkSnapshotEndPayload;
import dev.marblegate.superpipeslide.network.sync.pipe.PipeNetworkSnapshotStartPayload;
import dev.marblegate.superpipeslide.network.sync.route.ClientboundRouteDataSnapshotChunkPayload;
import dev.marblegate.superpipeslide.network.sync.route.ClientboundRouteDataSnapshotEndPayload;
import dev.marblegate.superpipeslide.network.sync.route.ClientboundRouteDataSnapshotStartPayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = SuperPipeSlide.MODID)
public final class ServerEvents {
    private static final int SNAPSHOT_NODES_PER_CHUNK = 512;
    private static final int SNAPSHOT_CONNECTIONS_PER_CHUNK = 128;
    private static final int ROUTE_SNAPSHOT_OBJECTS_PER_CHUNK = 256;
    private static final long ROUTE_SECTION_RECOMPUTE_BUDGET_NANOS = 2_000_000L;
    private static final long RESYNC_REQUEST_COOLDOWN_MILLIS = 1000L;
    private static final Map<UUID, SyncedPlayer> LAST_SYNCED_PLAYERS = new HashMap<>();
    private static final Map<UUID, Long> LAST_PIPE_RESYNC_REQUEST_MILLIS = new HashMap<>();
    private static final Map<UUID, Long> LAST_ROUTE_RESYNC_REQUEST_MILLIS = new HashMap<>();
    private static final NetworkSyncScheduler SYNC = new NetworkSyncScheduler();

    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerSlideController.tick(player);
            if (player.level() instanceof ServerLevel level) {
                syncSnapshotIfNeeded(level, player);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PipeNetworkSavedData pipeData = PipeNetworkSavedData.get(level);
            ServerPipeNetworkView pipeView = ServerPipeNetworkView.of(level.getServer());
            RouteNetworkSavedData routeData = RouteNetworkSavedData.get(level.getServer());

            if (level.dimension().equals(Level.OVERWORLD)) {
                PipeNetworkSavedData.PendingPipeNetworkChanges pipeChanges = SYNC.broadcastPendingPipeNetworkChanges(level);
                if (!pipeChanges.changeSet().isEmpty()) {
                    RouteNetworkSavedData.Batch batch = routeData.beginMutationBatch();
                    try {
                        for (ServerLevel serverLevel : level.getServer().getAllLevels()) {
                            batch.include(routeData.prunePlatformStopsWithMissingConnections(serverLevel.dimension(), pipeData));
                        }
                        batch.include(routeData.markSectionsAffectedByPipeChanges(pipeChanges.changeSet(), pipeView));
                    } finally {
                        batch.close();
                    }
                    if (batch.changed()) {
                        queueRouteDataDelta(level);
                    }
                }
                PipeAppearanceSavedData.get(level.getServer()).pruneMissingConnections(pipeData);

                RouteNetworkSavedData.Batch batch = routeData.beginMutationBatch();
                try {
                    batch.include(routeData.recomputeDirtySectionsForNanos(pipeView, ROUTE_SECTION_RECOMPUTE_BUDGET_NANOS));
                } finally {
                    batch.close();
                }
                if (batch.changed()) {
                    queueRouteDataDelta(level);
                }
                SYNC.flush(level);
            }
        }
    }

    private static void syncSnapshotIfNeeded(ServerLevel level, ServerPlayer player) {
        SyncedPlayer syncedPlayer = LAST_SYNCED_PLAYERS.get(player.getUUID());
        if (syncedPlayer != null && syncedPlayer.entityId() == player.getId()) {
            return;
        }

        sendPipeNetworkSnapshot(player);
        sendPipeAppearanceSnapshot(player);
        sendRouteDataSnapshot(player);
        LAST_SYNCED_PLAYERS.put(player.getUUID(), new SyncedPlayer(player.getId()));
    }

    public static void sendPipeNetworkSnapshot(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        PipeNetworkSavedData data = PipeNetworkSavedData.get(level.getServer());
        List<dev.marblegate.superpipeslide.common.core.networkgraph.model.PipeNode> nodes = List.copyOf(data.nodes());
        List<dev.marblegate.superpipeslide.common.core.geometry.PipeConnection> connections = List.copyOf(data.connections());
        long revision = data.revision();
        UUID snapshotId = UUID.randomUUID();
        int chunkCount = pipeNetworkSnapshotChunkCount(nodes.size(), connections.size());

        PacketDistributor.sendToPlayer(player, new PipeNetworkSnapshotStartPayload(snapshotId, revision, nodes.size(), connections.size(), chunkCount));

        int chunkIndex = 0;
        int nodeIndex = 0;
        int connectionIndex = 0;
        while (nodeIndex < nodes.size() || connectionIndex < connections.size()) {
            int nextNodeIndex = Math.min(nodeIndex + SNAPSHOT_NODES_PER_CHUNK, nodes.size());
            int nextConnectionIndex = Math.min(connectionIndex + SNAPSHOT_CONNECTIONS_PER_CHUNK, connections.size());
            PacketDistributor.sendToPlayer(player, new PipeNetworkSnapshotChunkPayload(
                    snapshotId,
                    revision,
                    chunkIndex++,
                    nodes.subList(nodeIndex, nextNodeIndex),
                    connections.subList(connectionIndex, nextConnectionIndex)
            ));
            nodeIndex = nextNodeIndex;
            connectionIndex = nextConnectionIndex;
        }

        PacketDistributor.sendToPlayer(player, new PipeNetworkSnapshotEndPayload(snapshotId, revision));
    }

    public static void sendPipeNetworkSnapshotForResync(ServerPlayer player) {
        if (shouldAcceptResyncRequest(player, LAST_PIPE_RESYNC_REQUEST_MILLIS)) {
            sendPipeNetworkSnapshot(player);
            sendPipeAppearanceSnapshot(player);
        }
    }

    public static void sendPipeAppearanceSnapshot(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, PipeAppearanceSavedData.get(level.getServer()).fullSyncPayload());
    }

    public static void sendRouteDataSnapshot(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        sendRouteDataSnapshot(player, RouteNetworkSavedData.get(level.getServer()));
    }

    public static void sendRouteDataSnapshotIfNeeded(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        SyncedPlayer syncedPlayer = LAST_SYNCED_PLAYERS.get(player.getUUID());
        if (syncedPlayer != null && syncedPlayer.entityId() == player.getId()) {
            return;
        }
        sendRouteDataSnapshot(player, RouteNetworkSavedData.get(level.getServer()));
    }

    public static void sendRouteDataSnapshotForResync(ServerPlayer player) {
        if (shouldAcceptResyncRequest(player, LAST_ROUTE_RESYNC_REQUEST_MILLIS)) {
            sendRouteDataSnapshot(player);
        }
    }

    public static void broadcastRouteDataSnapshot(ServerLevel level) {
        RouteNetworkSavedData data = RouteNetworkSavedData.get(level.getServer());
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            sendRouteDataSnapshot(player, data);
        }
    }

    public static void broadcastRouteDataDelta(ServerLevel level) {
        SYNC.broadcastRouteDataDelta(level);
    }

    public static void broadcastPipeAppearanceDelta(ServerLevel level) {
        SYNC.broadcastPipeAppearanceDelta(level);
    }

    public static void queueRouteDataSnapshot(ServerLevel level) {
        SYNC.queueRouteDataSnapshot();
    }

    public static void queueRouteDataDelta(ServerLevel level) {
        SYNC.queueRouteDataDelta();
    }

    private static void sendRouteDataSnapshot(ServerPlayer player, RouteNetworkSavedData data) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        List<dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup> stationGroups = List.copyOf(data.stationGroups());
        List<dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop> platformStops = List.copyOf(data.platformStops());
        List<dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine> routeLines = List.copyOf(data.routeLines());
        List<dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout> routeLayouts = List.copyOf(data.routeLayouts());
        List<dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection> routeSections = List.copyOf(data.routeSections());
        List<dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionPathRecord> routeSectionPaths = List.copyOf(data.routeSectionPathRecords());
        List<dev.marblegate.superpipeslide.common.core.route.model.station.StationTransferLink> stationTransferLinks = List.copyOf(data.stationTransferLinks());
        long revision = data.revision();
        int chunkCount = routeDataSnapshotChunkCount(
                stationGroups.size(),
                platformStops.size(),
                routeLines.size(),
                routeLayouts.size(),
                routeSections.size(),
                routeSectionPaths.size(),
                stationTransferLinks.size()
        );
        PacketDistributor.sendToPlayer(player, new ClientboundRouteDataSnapshotStartPayload(
                revision,
                ServerPipeNetworkView.of(level.getServer()).revision(),
                stationGroups.size(),
                platformStops.size(),
                routeLines.size(),
                routeLayouts.size(),
                routeSections.size(),
                routeSectionPaths.size(),
                stationTransferLinks.size(),
                chunkCount
        ));

        int chunkIndex = 0;
        int stationIndex = 0;
        int platformIndex = 0;
        int lineIndex = 0;
        int layoutIndex = 0;
        int sectionIndex = 0;
        int sectionPathIndex = 0;
        int transferLinkIndex = 0;
        while (stationIndex < stationGroups.size()
                || platformIndex < platformStops.size()
                || lineIndex < routeLines.size()
                || layoutIndex < routeLayouts.size()
                || sectionIndex < routeSections.size()
                || sectionPathIndex < routeSectionPaths.size()
                || transferLinkIndex < stationTransferLinks.size()) {
            int nextStationIndex = Math.min(stationIndex + ROUTE_SNAPSHOT_OBJECTS_PER_CHUNK, stationGroups.size());
            int nextPlatformIndex = Math.min(platformIndex + ROUTE_SNAPSHOT_OBJECTS_PER_CHUNK, platformStops.size());
            int nextLineIndex = Math.min(lineIndex + ROUTE_SNAPSHOT_OBJECTS_PER_CHUNK, routeLines.size());
            int nextLayoutIndex = Math.min(layoutIndex + ROUTE_SNAPSHOT_OBJECTS_PER_CHUNK, routeLayouts.size());
            int nextSectionIndex = Math.min(sectionIndex + ROUTE_SNAPSHOT_OBJECTS_PER_CHUNK, routeSections.size());
            int nextSectionPathIndex = Math.min(sectionPathIndex + ROUTE_SNAPSHOT_OBJECTS_PER_CHUNK, routeSectionPaths.size());
            int nextTransferLinkIndex = Math.min(transferLinkIndex + ROUTE_SNAPSHOT_OBJECTS_PER_CHUNK, stationTransferLinks.size());
            PacketDistributor.sendToPlayer(player, new ClientboundRouteDataSnapshotChunkPayload(
                    revision,
                    chunkIndex++,
                    stationGroups.subList(stationIndex, nextStationIndex),
                    platformStops.subList(platformIndex, nextPlatformIndex),
                    routeLines.subList(lineIndex, nextLineIndex),
                    routeLayouts.subList(layoutIndex, nextLayoutIndex),
                    routeSections.subList(sectionIndex, nextSectionIndex),
                    routeSectionPaths.subList(sectionPathIndex, nextSectionPathIndex),
                    stationTransferLinks.subList(transferLinkIndex, nextTransferLinkIndex)
            ));
            stationIndex = nextStationIndex;
            platformIndex = nextPlatformIndex;
            lineIndex = nextLineIndex;
            layoutIndex = nextLayoutIndex;
            sectionIndex = nextSectionIndex;
            sectionPathIndex = nextSectionPathIndex;
            transferLinkIndex = nextTransferLinkIndex;
        }
        PacketDistributor.sendToPlayer(player, new ClientboundRouteDataSnapshotEndPayload(revision));
    }

    private static int routeDataSnapshotChunkCount(int... counts) {
        int chunks = 0;
        for (int count : counts) {
            chunks = Math.max(chunks, (count + ROUTE_SNAPSHOT_OBJECTS_PER_CHUNK - 1) / ROUTE_SNAPSHOT_OBJECTS_PER_CHUNK);
        }
        return chunks;
    }

    private static int pipeNetworkSnapshotChunkCount(int nodeCount, int connectionCount) {
        return Math.max(
                (nodeCount + SNAPSHOT_NODES_PER_CHUNK - 1) / SNAPSHOT_NODES_PER_CHUNK,
                (connectionCount + SNAPSHOT_CONNECTIONS_PER_CHUNK - 1) / SNAPSHOT_CONNECTIONS_PER_CHUNK
        );
    }

    public static void sendEditorResult(ServerPlayer player, boolean accepted, String message, long routeRevision) {
        sendEditorResult(player, UUID.randomUUID(), accepted, message, routeRevision);
    }

    public static void sendEditorResult(ServerPlayer player, UUID requestId, boolean accepted, String message, long routeRevision) {
        PacketDistributor.sendToPlayer(player, new ClientboundEditorResultPayload(requestId, accepted, message, routeRevision));
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        PipeNetworkSavedData pipeData = PipeNetworkSavedData.get(event.getServer());
        ServerPipeNetworkView pipeView = ServerPipeNetworkView.of(event.getServer());
        RouteNetworkSavedData routeData = RouteNetworkSavedData.get(event.getServer());
        Set<ResourceKey<Level>> availableDimensions = new HashSet<>();
        for (ServerLevel serverLevel : event.getServer().getAllLevels()) {
            availableDimensions.add(serverLevel.dimension());
        }
        boolean prunedPipeData = pipeData.pruneUnavailableDimensions(availableDimensions);
        boolean prunedRouteData = routeData.pruneUnavailableDimensions(availableDimensions, pipeView);
        boolean prunedAppearanceData = PipeAppearanceSavedData.get(event.getServer()).pruneMissingConnections(pipeData);
        if (prunedPipeData || prunedRouteData || prunedAppearanceData) {
            SuperPipeSlide.LOGGER.debug("Pruned unavailable dimension data on server start: pipe={}, route={}, appearance={}", prunedPipeData, prunedRouteData, prunedAppearanceData);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_SYNCED_PLAYERS.remove(player.getUUID());
            LAST_PIPE_RESYNC_REQUEST_MILLIS.remove(player.getUUID());
            LAST_ROUTE_RESYNC_REQUEST_MILLIS.remove(player.getUUID());
            ServerSlideController.clear(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_SYNCED_PLAYERS.clear();
        LAST_PIPE_RESYNC_REQUEST_MILLIS.clear();
        LAST_ROUTE_RESYNC_REQUEST_MILLIS.clear();
        SYNC.clear();
        ServerSlideController.clearAllSessions();
    }

    private static boolean shouldAcceptResyncRequest(ServerPlayer player, Map<UUID, Long> lastRequestMillis) {
        long now = System.currentTimeMillis();
        long previous = lastRequestMillis.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (now - previous < RESYNC_REQUEST_COOLDOWN_MILLIS) {
            return false;
        }
        lastRequestMillis.put(player.getUUID(), now);
        return true;
    }

    private record SyncedPlayer(int entityId) {
    }
}

