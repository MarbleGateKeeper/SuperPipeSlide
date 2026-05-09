package dev.marblegate.superpipeslide.client.core.pipe;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import dev.marblegate.superpipeslide.common.core.geometry.RuntimePipeConnection;
import dev.marblegate.superpipeslide.common.core.networkgraph.branch.BranchNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.model.PipeNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkIndex;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkView;
import dev.marblegate.superpipeslide.network.sync.pipe.PipeNetworkDeltaPayload;
import dev.marblegate.superpipeslide.network.sync.pipe.PipeNetworkSnapshotChunkPayload;
import dev.marblegate.superpipeslide.network.sync.pipe.PipeNetworkSnapshotEndPayload;
import dev.marblegate.superpipeslide.network.sync.pipe.PipeNetworkSnapshotStartPayload;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ClientPipeNetworkCache {
    private static final long RESYNC_COOLDOWN_MILLIS = 1000L;
    private static final int CLIENT_QUERY_RUNTIME_REBUILD_BUDGET = 12;
    private static final int CLIENT_RENDER_RUNTIME_REBUILD_BUDGET = 24;

    private static final Map<ResourceKey<Level>, DimensionCache> BY_LEVEL = new LinkedHashMap<>();
    private static final Map<UUID, PipeConnection> CONNECTIONS_BY_ID = new LinkedHashMap<>();
    private static final Map<Integer, PipeConnection> CONNECTIONS_BY_KEY = new LinkedHashMap<>();
    private static final Map<UUID, BranchNode> BRANCH_NODES_BY_CONNECTION_ID = new LinkedHashMap<>();
    private static final Map<PipeAnchorId, PipeAnchorId> FOLD_COUNTERPARTS_BY_ANCHOR = new LinkedHashMap<>();
    private static final Set<UUID> RENDER_UPDATED_CONNECTION_IDS = new LinkedHashSet<>();
    private static final Set<UUID> RENDER_REMOVED_CONNECTION_IDS = new LinkedHashSet<>();
    @Nullable
    private static SnapshotBuilder pendingSnapshot;
    @Nullable
    private static ResourceKey<Level> currentLevelKey;
    private static long globalRevision = -1L;
    private static long lastResyncRequestMillis;
    private static boolean renderFullInvalidation;

    private static final PipeNetworkView CURRENT_VIEW = new PipeNetworkView() {
        @Override
        public Optional<PipeConnection> connection(UUID id) {
            return ClientPipeNetworkCache.currentConnection(id);
        }

        @Override
        public Optional<PipeConnection> connection(PipeConnectionRef ref) {
            return ClientPipeNetworkCache.connection(ref);
        }

        @Override
        public List<PipeConnection> connectionsTouching(PipeAnchorId anchorId) {
            return ClientPipeNetworkCache.currentLevelKey()
                    .filter(anchorId.levelKey()::equals)
                    .map(ignored -> ClientPipeNetworkCache.connectionsTouching(anchorId))
                    .orElse(List.of());
        }

        @Override
        public int connectionCount(PipeAnchorId anchorId) {
            return this.connectionsTouching(anchorId).size();
        }

        @Override
        public Optional<BranchNode> branchNodeAt(PipeAnchorId anchorId) {
            return ClientPipeNetworkCache.currentLevelKey()
                    .filter(anchorId.levelKey()::equals)
                    .flatMap(ignored -> ClientPipeNetworkCache.branchNodeAt(anchorId));
        }

        @Override
        public Optional<BranchNode> branchNodeManagingConnection(UUID connectionId) {
            return ClientPipeNetworkCache.currentBranchNodeManagingConnection(connectionId);
        }

        @Override
        public Optional<FoldAnchorNode> foldAnchorAt(PipeAnchorId anchorId) {
            return ClientPipeNetworkCache.currentLevelKey()
                    .filter(anchorId.levelKey()::equals)
                    .flatMap(ignored -> ClientPipeNetworkCache.foldAnchorAt(anchorId));
        }

        @Override
        public Optional<PipeAnchorId> localFoldCounterpart(PipeAnchorId anchorId) {
            return ClientPipeNetworkCache.localFoldCounterpart(anchorId);
        }

        @Override
        public long revision() {
            return ClientPipeNetworkCache.revision();
        }
    };

    private static final PipeNetworkView GLOBAL_VIEW = new PipeNetworkView() {
        @Override
        public Optional<PipeConnection> connection(UUID id) {
            return ClientPipeNetworkCache.globalConnection(id);
        }

        @Override
        public Optional<PipeConnection> connection(PipeConnectionRef ref) {
            return ClientPipeNetworkCache.connection(ref);
        }

        @Override
        public List<PipeConnection> connectionsTouching(PipeAnchorId anchorId) {
            return ClientPipeNetworkCache.connectionsTouching(anchorId);
        }

        @Override
        public int connectionCount(PipeAnchorId anchorId) {
            return ClientPipeNetworkCache.connectionCount(anchorId);
        }

        @Override
        public Optional<BranchNode> branchNodeAt(PipeAnchorId anchorId) {
            return ClientPipeNetworkCache.dimension(anchorId.levelKey())
                    .flatMap(cache -> cache.index().branchNodeAt(anchorId));
        }

        @Override
        public Optional<BranchNode> branchNodeManagingConnection(UUID connectionId) {
            return Optional.ofNullable(BRANCH_NODES_BY_CONNECTION_ID.get(connectionId));
        }

        @Override
        public Optional<FoldAnchorNode> foldAnchorAt(PipeAnchorId anchorId) {
            return ClientPipeNetworkCache.foldAnchorAt(anchorId);
        }

        @Override
        public Optional<PipeAnchorId> localFoldCounterpart(PipeAnchorId anchorId) {
            return ClientPipeNetworkCache.globalFoldCounterpart(anchorId);
        }

        @Override
        public long revision() {
            return ClientPipeNetworkCache.aggregateRevision();
        }
    };

    private ClientPipeNetworkCache() {
    }

    public static void handleSnapshotStart(PipeNetworkSnapshotStartPayload payload) {
        pendingSnapshot = new SnapshotBuilder(payload);
    }

    public static void handleSnapshotChunk(PipeNetworkSnapshotChunkPayload payload, Runnable resyncRequester) {
        if (pendingSnapshot == null || !pendingSnapshot.accepts(payload.snapshotId(), payload.revision())) {
            requestResync(resyncRequester);
            return;
        }

        if (!pendingSnapshot.add(payload.chunkIndex(), payload.nodes(), payload.connections())) {
            pendingSnapshot = null;
            requestResync(resyncRequester);
        }
    }

    public static void handleSnapshotEnd(PipeNetworkSnapshotEndPayload payload, Runnable resyncRequester) {
        if (pendingSnapshot == null || !pendingSnapshot.accepts(payload.snapshotId(), payload.revision()) || !pendingSnapshot.isComplete()) {
            pendingSnapshot = null;
            requestResync(resyncRequester);
            return;
        }

        applySnapshot(pendingSnapshot);
        pendingSnapshot = null;
    }

    private static void applySnapshot(SnapshotBuilder snapshot) {
        PipeNetworkSnapshotStartPayload payload = snapshot.start();
        rebuildDimensionCaches(snapshot.nodes(), snapshot.connections(), payload.revision());
        rebuildGlobalLookupIndexes();
        globalRevision = payload.revision();
        if (currentLevelKey == null) {
            currentLevelKey = Minecraft.getInstance().level == null ? BY_LEVEL.keySet().stream().findFirst().orElse(null) : Minecraft.getInstance().level.dimension();
        }
        lastResyncRequestMillis = 0L;
        markRenderFullInvalidation();
    }

    public static void handleDelta(PipeNetworkDeltaPayload payload, Runnable resyncRequester) {
        if (payload.revision() <= globalRevision) {
            return;
        }
        if (payload.baseRevision() != globalRevision || pendingSnapshot != null) {
            requestResync(resyncRequester);
            return;
        }

        for (UUID connectionId : payload.removedConnectionIds()) {
            BY_LEVEL.values().forEach(cache -> cache.index().removeConnection(connectionId));
            PipeConnection removed = CONNECTIONS_BY_ID.remove(connectionId);
            if (removed != null) {
                CONNECTIONS_BY_KEY.remove(removed.connectionKey());
            }
            BRANCH_NODES_BY_CONNECTION_ID.remove(connectionId);
            markConnectionRemovedForRender(connectionId);
        }
        for (PipeAnchorId nodeId : payload.removedNodeIds()) {
            dimension(nodeId.levelKey()).ifPresent(cache -> {
                cache.index().node(nodeId).ifPresent(ClientPipeNetworkCache::removeGlobalNodeProjection);
                cache.index().removeNode(nodeId);
            });
        }
        payload.addedOrUpdatedNodes().forEach(node -> {
            PipeNetworkIndex index = dimensionForUpdate(node.id().levelKey(), payload.revision()).index();
            index.node(node.id()).ifPresent(ClientPipeNetworkCache::removeGlobalNodeProjection);
            index.upsertNode(node);
            addGlobalNodeProjection(node);
        });
        payload.addedOrUpdatedConnections().forEach(connection -> {
            dimensionForUpdate(connection.levelKey(), payload.revision()).index().upsertConnection(connection);
            PipeConnection previous = CONNECTIONS_BY_ID.put(connection.id(), connection);
            if (previous != null && previous.connectionKey() != connection.connectionKey()) {
                CONNECTIONS_BY_KEY.remove(previous.connectionKey());
            }
            if (connection.connectionKey() > PipeConnection.TRANSIENT_CONNECTION_KEY) {
                CONNECTIONS_BY_KEY.put(connection.connectionKey(), connection);
            }
            markConnectionUpdatedForRender(connection.id());
        });
        BY_LEVEL.replaceAll((levelKey, cache) -> new DimensionCache(levelKey, payload.revision(), cache.index(), DimensionCacheState.READY));
        rebuildFoldCounterpartIndex();
        globalRevision = payload.revision();
    }

    public static void clear() {
        currentLevelKey = null;
        globalRevision = -1L;
        pendingSnapshot = null;
        BY_LEVEL.clear();
        CONNECTIONS_BY_ID.clear();
        CONNECTIONS_BY_KEY.clear();
        BRANCH_NODES_BY_CONNECTION_ID.clear();
        FOLD_COUNTERPARTS_BY_ANCHOR.clear();
        lastResyncRequestMillis = 0L;
        markRenderFullInvalidation();
    }

    public static PipeRenderInvalidation consumePipeRenderInvalidation() {
        PipeRenderInvalidation invalidation = new PipeRenderInvalidation(
                renderFullInvalidation,
                List.copyOf(RENDER_UPDATED_CONNECTION_IDS),
                List.copyOf(RENDER_REMOVED_CONNECTION_IDS)
        );
        renderFullInvalidation = false;
        RENDER_UPDATED_CONNECTION_IDS.clear();
        RENDER_REMOVED_CONNECTION_IDS.clear();
        return invalidation;
    }

    public static void setCurrentLevel(ResourceKey<Level> levelKey) {
        currentLevelKey = levelKey;
    }

    public static long revision() {
        return globalRevision;
    }

    public static long aggregateRevision() {
        return globalRevision;
    }

    public static Optional<ResourceKey<Level>> levelKey() {
        return currentLevelKey();
    }

    public static Optional<ResourceKey<Level>> currentLevelKey() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            currentLevelKey = minecraft.level.dimension();
        }
        return Optional.ofNullable(currentLevelKey);
    }

    public static Collection<ResourceKey<Level>> knownDimensions() {
        return List.copyOf(BY_LEVEL.keySet());
    }

    public static Collection<FoldAnchorNode> foldAnchors() {
        return BY_LEVEL.values().stream()
                .flatMap(cache -> cache.index().foldAnchors().stream())
                .toList();
    }

    public static DimensionCacheState state(ResourceKey<Level> levelKey) {
        return dimension(levelKey).map(DimensionCache::state).orElse(DimensionCacheState.UNAVAILABLE);
    }

    public static PipeNetworkView currentView() {
        return CURRENT_VIEW;
    }

    public static PipeNetworkView globalView() {
        return GLOBAL_VIEW;
    }

    public static Optional<PipeNode> node(PipeAnchorId nodeId) {
        return dimension(nodeId.levelKey()).flatMap(cache -> cache.index().node(nodeId));
    }

    public static boolean hasNode(PipeAnchorId nodeId) {
        return node(nodeId).isPresent();
    }

    public static boolean isOrdinaryNode(PipeAnchorId nodeId) {
        return dimension(nodeId.levelKey()).map(cache -> cache.index().isOrdinaryNode(nodeId)).orElse(false);
    }

    public static Optional<BranchNode> branchNodeAt(PipeAnchorId nodeId) {
        return dimension(nodeId.levelKey()).flatMap(cache -> cache.index().branchNodeAt(nodeId));
    }

    public static Optional<FoldAnchorNode> foldAnchorAt(PipeAnchorId nodeId) {
        return dimension(nodeId.levelKey()).flatMap(cache -> cache.index().foldAnchorAt(nodeId));
    }

    public static Optional<PipeAnchorId> localFoldCounterpart(PipeAnchorId nodeId) {
        return globalFoldCounterpart(nodeId)
                .filter(target -> target.levelKey().equals(nodeId.levelKey()));
    }

    public static Optional<PipeAnchorId> globalFoldCounterpart(PipeAnchorId nodeId) {
        return Optional.ofNullable(FOLD_COUNTERPARTS_BY_ANCHOR.get(nodeId));
    }

    public static Collection<PipeNode> nodes(ResourceKey<Level> requestedLevel) {
        return dimension(requestedLevel)
                .map(cache -> List.copyOf(cache.index().nodesIn(requestedLevel)))
                .orElse(List.of());
    }

    public static Collection<PipeNode> nodesNear(ResourceKey<Level> requestedLevel, Vec3 position, double radius) {
        return dimension(requestedLevel)
                .map(cache -> cache.index().nodesNear(requestedLevel, position, radius))
                .orElse(List.of());
    }

    public static Collection<PipeConnection> connections(ResourceKey<Level> requestedLevel) {
        return dimension(requestedLevel)
                .map(cache -> List.copyOf(cache.index().connectionsIn(requestedLevel)))
                .orElse(List.of());
    }

    public static Collection<RuntimePipeConnection> runtimeConnections(ResourceKey<Level> requestedLevel) {
        return dimension(requestedLevel)
                .map(cache -> List.copyOf(cache.index().runtimeConnections(requestedLevel, CLIENT_RENDER_RUNTIME_REBUILD_BUDGET)))
                .orElse(List.of());
    }

    public static Collection<RuntimePipeConnection> runtimeConnectionsNear(ResourceKey<Level> requestedLevel, Vec3 position, double radius) {
        return dimension(requestedLevel)
                .map(cache -> List.copyOf(cache.index().runtimeConnectionsNear(requestedLevel, position, radius, CLIENT_RENDER_RUNTIME_REBUILD_BUDGET)))
                .orElse(List.of());
    }

    public static int pendingRuntimeRebuilds(ResourceKey<Level> requestedLevel) {
        return dimension(requestedLevel)
                .map(cache -> cache.index().pendingRuntimeRebuilds())
                .orElse(0);
    }

    public static Optional<PipeConnection> currentConnection(UUID id) {
        return currentLevelKey()
                .flatMap(ClientPipeNetworkCache::dimension)
                .flatMap(cache -> cache.index().connection(id));
    }

    public static Optional<PipeConnection> globalConnection(UUID id) {
        return Optional.ofNullable(CONNECTIONS_BY_ID.get(id));
    }

    public static Optional<PipeConnection> connectionByKey(int connectionKey) {
        return Optional.ofNullable(CONNECTIONS_BY_KEY.get(connectionKey));
    }

    public static Optional<PipeConnection> connection(PipeConnectionRef ref) {
        return dimension(ref.levelKey()).flatMap(cache -> cache.index().connection(ref.connectionId()));
    }

    public static Optional<BranchNode> currentBranchNode(UUID id) {
        return currentLevelKey()
                .flatMap(ClientPipeNetworkCache::dimension)
                .flatMap(cache -> cache.index().branchNode(id));
    }

    public static Optional<BranchNode> currentBranchNodeManagingConnection(UUID connectionId) {
        return currentLevelKey()
                .flatMap(ClientPipeNetworkCache::dimension)
                .flatMap(cache -> cache.index().branchNodeManagingConnection(connectionId));
    }

    public static List<PipeConnection> connectionsNear(ResourceKey<Level> requestedLevel, Vec3 position, double radius) {
        return dimension(requestedLevel)
                .map(cache -> cache.index().connectionsNear(requestedLevel, position, radius, CLIENT_QUERY_RUNTIME_REBUILD_BUDGET))
                .orElse(List.of());
    }

    public static List<PipeConnection> connectionsTouching(PipeAnchorId anchorId) {
        return dimension(anchorId.levelKey())
                .map(cache -> cache.index().connectionsTouching(anchorId))
                .orElse(List.of());
    }

    public static int connectionCount(PipeAnchorId anchorId) {
        return connectionsTouching(anchorId).size();
    }

    public static boolean hasConnectionBetween(PipeAnchorId first, PipeAnchorId second) {
        if (!first.levelKey().equals(second.levelKey())) {
            return false;
        }
        return dimension(first.levelKey())
                .map(cache -> cache.index().hasConnectionBetween(first, second))
                .orElse(false);
    }

    private static Optional<DimensionCache> dimension(ResourceKey<Level> levelKey) {
        return Optional.ofNullable(BY_LEVEL.get(levelKey));
    }

    private static DimensionCache dimensionForUpdate(ResourceKey<Level> levelKey, long revision) {
        return BY_LEVEL.computeIfAbsent(levelKey, key -> new DimensionCache(key, revision, new PipeNetworkIndex(), DimensionCacheState.READY));
    }

    private static void rebuildDimensionCaches(Collection<PipeNode> nodes, Collection<PipeConnection> connections, long revision) {
        Set<ResourceKey<Level>> levels = new LinkedHashSet<>();
        Map<ResourceKey<Level>, List<PipeNode>> nodesByLevel = new LinkedHashMap<>();
        Map<ResourceKey<Level>, List<PipeConnection>> connectionsByLevel = new LinkedHashMap<>();
        for (PipeNode node : nodes) {
            ResourceKey<Level> levelKey = node.id().levelKey();
            levels.add(levelKey);
            nodesByLevel.computeIfAbsent(levelKey, ignored -> new ArrayList<>()).add(node);
        }
        for (PipeConnection connection : connections) {
            ResourceKey<Level> levelKey = connection.levelKey();
            levels.add(levelKey);
            connectionsByLevel.computeIfAbsent(levelKey, ignored -> new ArrayList<>()).add(connection);
        }
        BY_LEVEL.clear();
        for (ResourceKey<Level> levelKey : levels) {
            PipeNetworkIndex index = new PipeNetworkIndex();
            index.reset(
                    nodesByLevel.getOrDefault(levelKey, List.of()),
                    connectionsByLevel.getOrDefault(levelKey, List.of())
            );
            BY_LEVEL.put(levelKey, new DimensionCache(levelKey, revision, index, DimensionCacheState.READY));
        }
    }

    private static void rebuildGlobalLookupIndexes() {
        CONNECTIONS_BY_ID.clear();
        CONNECTIONS_BY_KEY.clear();
        BRANCH_NODES_BY_CONNECTION_ID.clear();
        FOLD_COUNTERPARTS_BY_ANCHOR.clear();
        for (DimensionCache cache : BY_LEVEL.values()) {
            for (PipeConnection connection : cache.index().connectionsIn(cache.levelKey())) {
                CONNECTIONS_BY_ID.put(connection.id(), connection);
                if (connection.connectionKey() > PipeConnection.TRANSIENT_CONNECTION_KEY) {
                    CONNECTIONS_BY_KEY.put(connection.connectionKey(), connection);
                }
            }
            for (PipeNode node : cache.index().nodesIn(cache.levelKey())) {
                node.branchNode().ifPresent(branch -> branch.managedConnectionIdsInOrder()
                        .forEach(connectionId -> BRANCH_NODES_BY_CONNECTION_ID.put(connectionId, branch)));
            }
        }
        for (DimensionCache cache : BY_LEVEL.values()) {
            for (FoldAnchorNode fold : cache.index().foldAnchors()) {
                if (!fold.isBEnd()) {
                    continue;
                }
                fold.boundTarget().ifPresent(target -> {
                    FOLD_COUNTERPARTS_BY_ANCHOR.put(fold.anchorId(), target.anchorId());
                    FOLD_COUNTERPARTS_BY_ANCHOR.put(target.anchorId(), fold.anchorId());
                });
            }
        }
    }

    private static void addGlobalNodeProjection(PipeNode node) {
        node.branchNode().ifPresent(branch -> branch.managedConnectionIdsInOrder()
                .forEach(connectionId -> BRANCH_NODES_BY_CONNECTION_ID.put(connectionId, branch)));
    }

    private static void removeGlobalNodeProjection(PipeNode node) {
        node.branchNode().ifPresent(branch -> branch.managedConnectionIdsInOrder()
                .forEach(BRANCH_NODES_BY_CONNECTION_ID::remove));
    }

    private static void rebuildFoldCounterpartIndex() {
        FOLD_COUNTERPARTS_BY_ANCHOR.clear();
        for (DimensionCache cache : BY_LEVEL.values()) {
            for (FoldAnchorNode fold : cache.index().foldAnchors()) {
                if (!fold.isBEnd()) {
                    continue;
                }
                fold.boundTarget().ifPresent(target -> {
                    FOLD_COUNTERPARTS_BY_ANCHOR.put(fold.anchorId(), target.anchorId());
                    FOLD_COUNTERPARTS_BY_ANCHOR.put(target.anchorId(), fold.anchorId());
                });
            }
        }
    }

    private static void requestResync(Runnable resyncRequester) {
        pendingSnapshot = null;
        long now = System.currentTimeMillis();
        if (now - lastResyncRequestMillis < RESYNC_COOLDOWN_MILLIS) {
            return;
        }
        lastResyncRequestMillis = now;
        resyncRequester.run();
    }

    private static void markRenderFullInvalidation() {
        renderFullInvalidation = true;
        RENDER_UPDATED_CONNECTION_IDS.clear();
        RENDER_REMOVED_CONNECTION_IDS.clear();
    }

    private static void markConnectionUpdatedForRender(UUID connectionId) {
        if (renderFullInvalidation) {
            return;
        }
        RENDER_REMOVED_CONNECTION_IDS.remove(connectionId);
        RENDER_UPDATED_CONNECTION_IDS.add(connectionId);
    }

    private static void markConnectionRemovedForRender(UUID connectionId) {
        if (renderFullInvalidation) {
            return;
        }
        RENDER_UPDATED_CONNECTION_IDS.remove(connectionId);
        RENDER_REMOVED_CONNECTION_IDS.add(connectionId);
    }

    public record PipeRenderInvalidation(boolean full, List<UUID> updatedConnectionIds, List<UUID> removedConnectionIds) {
        public PipeRenderInvalidation {
            updatedConnectionIds = List.copyOf(updatedConnectionIds);
            removedConnectionIds = List.copyOf(removedConnectionIds);
        }

        public boolean isEmpty() {
            return !this.full && this.updatedConnectionIds.isEmpty() && this.removedConnectionIds.isEmpty();
        }
    }

    public enum DimensionCacheState {
        READY,
        UNAVAILABLE
    }

    private record DimensionCache(ResourceKey<Level> levelKey, long revision, PipeNetworkIndex index, DimensionCacheState state) {
    }

    private record SnapshotBuilder(PipeNetworkSnapshotStartPayload start, List<PipeNode> nodes, List<PipeConnection> connections, BitSet receivedChunks) {
        SnapshotBuilder(PipeNetworkSnapshotStartPayload start) {
            this(start, new ArrayList<>(start.totalNodes()), new ArrayList<>(start.totalConnections()), new BitSet(start.chunkCount()));
        }

        boolean accepts(UUID snapshotId, long revision) {
            return this.start.snapshotId().equals(snapshotId) && this.start.revision() == revision;
        }

        boolean add(int chunkIndex, List<PipeNode> addedNodes, List<PipeConnection> addedConnections) {
            if (chunkIndex < 0
                    || chunkIndex >= this.start.chunkCount()
                    || this.receivedChunks.get(chunkIndex)
                    || this.nodes.size() + addedNodes.size() > this.start.totalNodes()
                    || this.connections.size() + addedConnections.size() > this.start.totalConnections()) {
                return false;
            }
            this.receivedChunks.set(chunkIndex);
            this.nodes.addAll(addedNodes);
            this.connections.addAll(addedConnections);
            return true;
        }

        boolean isComplete() {
            return this.nodes.size() == this.start.totalNodes()
                    && this.connections.size() == this.start.totalConnections()
                    && this.receivedChunks.cardinality() == this.start.chunkCount();
        }
    }
}
