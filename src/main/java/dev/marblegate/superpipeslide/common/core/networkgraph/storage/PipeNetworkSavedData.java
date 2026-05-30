package dev.marblegate.superpipeslide.common.core.networkgraph.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.geometry.*;
import dev.marblegate.superpipeslide.common.core.networkgraph.branch.BranchConnectionSlot;
import dev.marblegate.superpipeslide.common.core.networkgraph.branch.BranchNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.*;
import dev.marblegate.superpipeslide.common.core.networkgraph.model.PipeNetworkChangeSet;
import dev.marblegate.superpipeslide.common.core.networkgraph.model.PipeNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.solver.AutoCurveSolver;
import dev.marblegate.superpipeslide.common.core.networkgraph.sync.PipeSyncTracker;
import dev.marblegate.superpipeslide.common.registry.SPSBlocks;
import dev.marblegate.superpipeslide.network.sync.pipe.PipeNetworkDeltaPayload;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

public final class PipeNetworkSavedData extends SavedData implements PipeNetworkView {
    private static final int SERVER_RUNTIME_REBUILD_BUDGET_PER_QUERY = 16;

    public static final Codec<PipeNetworkSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PipeNode.CODEC.listOf().optionalFieldOf("nodes", List.of()).forGetter(PipeNetworkSavedData::nodesForCodec),
            PipeConnection.CODEC.listOf().optionalFieldOf("connections", List.of()).forGetter(PipeNetworkSavedData::connectionsForCodec),
            Codec.LONG.optionalFieldOf("revision", 0L).forGetter(PipeNetworkSavedData::revision),
            Codec.INT.optionalFieldOf("next_connection_key", 1).forGetter(PipeNetworkSavedData::nextConnectionKeyForCodec)).apply(instance, PipeNetworkSavedData::new));

    public static final SavedDataType<PipeNetworkSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipe_network"),
            PipeNetworkSavedData::new,
            CODEC);

    private final Map<PipeAnchorId, PipeNode> nodes;
    private final List<PipeConnection> connections;
    private final PipeNetworkIndex index = new PipeNetworkIndex();
    private final PipeSyncTracker syncTracker = new PipeSyncTracker();
    private long revision;
    private int nextConnectionKey;
    private boolean trackingChanges;
    private int batchDepth;
    private boolean batchRevisionDirty;

    public PipeNetworkSavedData() {
        this(List.of(), List.of(), 0L, 1);
    }

    public PipeNetworkSavedData(List<PipeNode> nodes, List<PipeConnection> connections, long revision) {
        this(nodes, connections, revision, 1);
    }

    public PipeNetworkSavedData(List<PipeNode> nodes, List<PipeConnection> connections, long revision, int nextConnectionKey) {
        this.nodes = new LinkedHashMap<>();
        for (PipeNode node : nodes) {
            PipeNode previous = this.nodes.put(node.id(), node);
            if (previous != null) {
                SuperPipeSlide.LOGGER.debug("Duplicate pipe node {} in saved network data was replaced during load", node.id());
            }
        }
        this.connections = new ArrayList<>(connections);
        this.revision = revision;
        this.nextConnectionKey = Math.max(1, nextConnectionKey);
        if (this.normalizeConnectionKeys()) {
            this.setDirty();
        }
        this.prunePhaseOneConnectionConflicts();
        this.pruneInvalidBranchNodes();
        this.rebuildIndex();
        this.recomputeAutoCurvesAround(new HashSet<>(this.nodes.keySet()));
        this.trackingChanges = true;
    }

    public static PipeNetworkSavedData get(ServerLevel level) {
        return get(level.getServer());
    }

    public static PipeNetworkSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Collection<PipeNode> nodes() {
        return List.copyOf(this.nodes.values());
    }

    public List<PipeNode> nodesIn(ResourceKey<Level> levelKey) {
        return this.nodes.values().stream()
                .filter(node -> node.id().levelKey().equals(levelKey))
                .toList();
    }

    public Collection<PipeConnection> connections() {
        return List.copyOf(this.connections);
    }

    public boolean pruneUnavailableDimensions(Set<ResourceKey<Level>> availableDimensions) {
        Set<ResourceKey<Level>> available = Set.copyOf(availableDimensions);
        List<PipeAnchorId> removedNodeIds = this.nodes.keySet().stream()
                .filter(nodeId -> !available.contains(nodeId.levelKey()))
                .toList();
        Map<ResourceKey<Level>, List<UUID>> removedByLevel = this.connections.stream()
                .filter(connection -> !available.contains(connection.levelKey()))
                .collect(java.util.stream.Collectors.groupingBy(PipeConnection::levelKey, java.util.stream.Collectors.mapping(PipeConnection::id, java.util.stream.Collectors.toList())));
        List<UUID> removedConnectionIds = removedByLevel.values().stream()
                .flatMap(Collection::stream)
                .toList();
        if (removedNodeIds.isEmpty() && removedConnectionIds.isEmpty()) {
            return false;
        }

        try (Batch ignored = this.beginMutationBatch()) {
            for (PipeAnchorId nodeId : removedNodeIds) {
                this.nodes.remove(nodeId);
                this.trackNodeRemoval(nodeId, nodeId.levelKey());
            }
            Set<UUID> removedConnections = new HashSet<>(removedConnectionIds);
            this.connections.removeIf(connection -> removedConnections.contains(connection.id()));
            removedByLevel.forEach((levelKey, connectionIds) -> this.trackConnectionRemovals(connectionIds, levelKey));
            this.pruneInvalidBranchNodes();
            this.rebuildIndex();
            return true;
        }
    }

    public BrokenAnchorCleanupResult pruneMissingAnchorBlocksNear(ServerLevel level, Vec3 center, double radius) {
        if (radius <= 0.0D || this.nodes.isEmpty()) {
            return BrokenAnchorCleanupResult.EMPTY;
        }

        double radiusSqr = radius * radius;
        List<PipeNode> missingBlockNodes = this.nodes.values().stream()
                .filter(node -> node.id().levelKey().equals(level.dimension()))
                .filter(node -> Vec3.atCenterOf(node.id().blockPos()).distanceToSqr(center) <= radiusSqr)
                .filter(node -> {
                    BlockPos pos = node.id().blockPos();
                    if (!level.hasChunkAt(pos)) {
                        return false;
                    }
                    BlockState state = level.getBlockState(pos);
                    return !isAnchorBlock(state.getBlock());
                })
                .toList();
        if (missingBlockNodes.isEmpty()) {
            return BrokenAnchorCleanupResult.EMPTY;
        }

        FoldAnchorDirectory foldDirectory = new FoldAnchorDirectory(level.getServer());
        int removedNodes = 0;
        int removedConnections = 0;
        try (Batch batch = this.beginMutationBatch()) {
            for (PipeNode node : missingBlockNodes) {
                int nodesBefore = this.nodes.size();
                int connectionsBefore = this.connections.size();
                if (this.pruneMissingAnchorBlockNode(node, foldDirectory)) {
                    removedNodes += Math.max(0, nodesBefore - this.nodes.size());
                    removedConnections += Math.max(0, connectionsBefore - this.connections.size());
                }
            }
            batch.include(removedNodes > 0 || removedConnections > 0);
        }
        if (removedNodes > 0 || removedConnections > 0) {
            SuperPipeSlide.LOGGER.debug("Manually pruned {} broken pipe anchor nodes and {} stale pipe connections near {} in {}", removedNodes, removedConnections, center, level.dimension().identifier());
        }
        return new BrokenAnchorCleanupResult(removedNodes, removedConnections);
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
        this.revision++;
        this.setDirty();
        return true;
    }

    public static final class Batch implements AutoCloseable {
        private final PipeNetworkSavedData owner;
        private boolean closed;
        private boolean changed;

        private Batch(PipeNetworkSavedData owner) {
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

    public List<PipeConnection> connectionsIn(Level level) {
        return this.connections.stream()
                .filter(connection -> connection.levelKey().equals(level.dimension()))
                .toList();
    }

    public List<PipeConnection> connectionsNear(Level level, Vec3 position, double radius) {
        return this.index.connectionsNear(level.dimension(), position, radius, SERVER_RUNTIME_REBUILD_BUDGET_PER_QUERY);
    }

    public Optional<PipeNode> node(PipeAnchorId id) {
        return Optional.ofNullable(this.nodes.get(id));
    }

    public boolean hasNode(PipeAnchorId id) {
        return this.nodes.containsKey(id);
    }

    public boolean isOrdinaryNode(PipeAnchorId id) {
        return this.node(id).filter(PipeNode::isOrdinaryAnchor).isPresent();
    }

    public boolean isBranchNode(PipeAnchorId id) {
        return this.node(id).filter(PipeNode::isBranch).isPresent();
    }

    public boolean isFoldAnchorNode(PipeAnchorId id) {
        return this.node(id).filter(PipeNode::isFoldAnchor).isPresent();
    }

    public boolean isSpecialAnchorNode(PipeAnchorId id) {
        return this.node(id).filter(node -> node.isBranch() || node.isFoldAnchor()).isPresent();
    }

    public Optional<PipeConnection> connection(UUID id) {
        return this.index.connection(id);
    }

    public Optional<PipeConnection> connectionByKey(int connectionKey) {
        return this.index.connectionByKey(connectionKey);
    }

    public Optional<PipeConnection> updateConnectionAttributes(UUID connectionId, Optional<PipeConnectionAttributes> attributes) {
        Optional<PipeConnection> existing = this.connection(connectionId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        PipeConnection updated = existing.get().withAttributes(attributes);
        if (updated.equals(existing.get())) {
            return Optional.of(existing.get());
        }

        this.replaceConnection(updated);
        this.setDirty();
        return Optional.of(updated);
    }

    public Optional<PipeConnection> updateConnectionPlatformStop(UUID connectionId, Optional<UUID> platformStopId) {
        Optional<PipeConnection> existing = this.connection(connectionId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        PipeConnection updated = existing.get().withPlatformStopId(platformStopId);
        if (updated.equals(existing.get())) {
            return Optional.of(existing.get());
        }

        this.replaceConnection(updated);
        this.setDirty();
        return Optional.of(updated);
    }

    public Optional<BranchNode> branchNode(UUID id) {
        return this.index.branchNode(id);
    }

    public Optional<BranchNode> branchNodeManagingConnection(UUID connectionId) {
        return this.index.branchNodeManagingConnection(connectionId);
    }

    public Optional<BranchNode> branchNodeAt(PipeAnchorId branchAnchor) {
        return this.node(branchAnchor).flatMap(PipeNode::branchNode);
    }

    public Optional<FoldAnchorNode> foldAnchorAt(PipeAnchorId anchorId) {
        return this.node(anchorId).flatMap(PipeNode::foldAnchorNode);
    }

    public List<FoldAnchorNode> foldAnchors() {
        return this.nodes.values().stream()
                .map(PipeNode::foldAnchorNode)
                .flatMap(Optional::stream)
                .toList();
    }

    private Collection<BranchNode> branchNodeValues() {
        return this.nodes.values().stream()
                .map(PipeNode::branchNode)
                .flatMap(Optional::stream)
                .toList();
    }

    public BranchNode initializeBranchNode(PipeAnchorId anchorId) {
        Optional<BranchNode> existing = this.branchNodeAt(anchorId);
        if (existing.isPresent()) {
            return existing.get();
        }

        BranchNode branchNode = new BranchNode(
                UUID.randomUUID(),
                anchorId.levelKey(),
                Vec3.atCenterOf(anchorId.blockPos()),
                Optional.of(anchorId),
                Optional.empty(),
                List.of());
        this.upsertBranchPipeNode(branchNode);
        return branchNode;
    }

    public Optional<BranchNode> upgradePipeAnchorToBranch(PipeAnchorId anchorId) {
        PipeNode existingNode = this.nodes.get(anchorId);
        if (existingNode == null || !existingNode.isOrdinaryAnchor() || this.branchNodeAt(anchorId).isPresent()) {
            return Optional.empty();
        }

        List<PipeConnection> touching = this.orderedConnectionsTouching(anchorId);
        if (touching.stream().anyMatch(connection -> this.isManagedByBranchNode(connection) || this.touchesSpecialNode(connection))) {
            return Optional.empty();
        }

        List<BranchConnectionSlot> managedConnections = new ArrayList<>();
        for (int i = 0; i < touching.size(); i++) {
            PipeConnection connection = touching.get(i);
            managedConnections.add(this.branchConnectionSlotFor(anchorId, connection, i == 0 ? "Default" : "Path " + (i + 1)));
        }

        try (Batch ignored = this.beginMutationBatch()) {
            BranchNode branchNode = new BranchNode(
                    UUID.randomUUID(),
                    anchorId.levelKey(),
                    Vec3.atCenterOf(anchorId.blockPos()),
                    Optional.of(anchorId),
                    touching.isEmpty() ? Optional.empty() : Optional.of(touching.get(0).id()),
                    managedConnections);

            PipeNode branchPipeNode = PipeNode.branch(anchorId, branchNode);
            this.nodes.put(anchorId, branchPipeNode);
            this.index.upsertNode(branchPipeNode);
            this.trackNodeUpsert(branchPipeNode);
            this.recomputeAutoCurvesAround(Set.of(anchorId));
            this.setDirty();
            return Optional.of(branchNode);
        }
    }

    public Optional<FoldAnchorNode> upgradePipeAnchorToFold(PipeAnchorId anchorId, FoldAnchorKind kind) {
        PipeNode existingNode = this.nodes.get(anchorId);
        if (existingNode == null || !existingNode.isOrdinaryAnchor() || this.foldAnchorAt(anchorId).isPresent()) {
            return Optional.empty();
        }

        List<PipeConnection> touching = this.orderedConnectionsTouching(anchorId);
        if (touching.stream().anyMatch(connection -> this.isManagedByBranchNode(connection) || this.touchesSpecialNode(connection))) {
            return Optional.empty();
        }
        try (Batch ignored = this.beginMutationBatch()) {
            this.trimConnectionsTouching(anchorId, 1);
            FoldAnchorNode foldAnchor = FoldAnchorNode.unconfigured(anchorId, kind);
            this.upsertFoldAnchorPipeNode(foldAnchor);
            this.recomputeAutoCurvesAround(Set.of(anchorId));
            this.setDirty();
            return Optional.of(foldAnchor);
        }
    }

    public Optional<BranchNode> downgradeBranchAnchorToPipe(PipeAnchorId branchAnchor) {
        Optional<BranchNode> existing = this.branchNodeAt(branchAnchor);
        if (existing.isEmpty()) {
            this.addOrdinaryNode(branchAnchor);
            return Optional.empty();
        }

        BranchNode branchNode = existing.get();
        List<UUID> orderedConnectionIds = branchNode.managedConnectionIdsInOrder();
        Set<UUID> keptConnectionIds = orderedConnectionIds.stream()
                .limit(2)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        Set<UUID> removedConnectionIds = orderedConnectionIds.stream()
                .skip(2)
                .collect(java.util.stream.Collectors.toSet());

        try (Batch ignored = this.beginMutationBatch()) {
            Set<PipeAnchorId> affectedAnchors = new HashSet<>();
            affectedAnchors.add(branchAnchor);
            this.connections.stream()
                    .filter(connection -> removedConnectionIds.contains(connection.id()))
                    .forEach(connection -> {
                        affectedAnchors.add(connection.fromAnchor());
                        affectedAnchors.add(connection.toAnchor());
                    });

            if (!removedConnectionIds.isEmpty()) {
                this.connections.removeIf(connection -> removedConnectionIds.contains(connection.id()));
                removedConnectionIds.forEach(this.index::removeConnection);
                this.trackConnectionRemovals(removedConnectionIds, branchAnchor.levelKey());
            }

            this.nodes.remove(branchAnchor);
            if (!this.nodes.containsKey(branchAnchor)) {
                PipeNode ordinaryNode = PipeNode.ordinary(branchAnchor);
                this.nodes.put(branchAnchor, ordinaryNode);
                this.trackNodeUpsert(ordinaryNode);
            }
            this.index.upsertNode(this.nodes.get(branchAnchor));
            keptConnectionIds.forEach(connectionId -> this.index.connection(connectionId).ifPresent(this.index::upsertConnection));
            this.recomputeAutoCurvesAround(affectedAnchors);
            this.setDirty();
            return Optional.of(branchNode);
        }
    }

    public Optional<PipeConnection> addConnectionWithBranchSupport(PipeAnchorId fromAnchor, PipeAnchorId toAnchor, CurveSpec curveSpec) {
        Optional<BranchNode> fromBranch = this.branchNodeAt(fromAnchor);
        Optional<BranchNode> toBranch = this.branchNodeAt(toAnchor);
        if (fromBranch.isPresent() && toBranch.isPresent()) {
            return Optional.empty();
        }
        if (fromBranch.isEmpty() && toBranch.isEmpty()) {
            return this.addConnection(fromAnchor, toAnchor, curveSpec);
        }

        PipeAnchorId branchAnchor = fromBranch.isPresent() ? fromAnchor : toAnchor;
        PipeAnchorId ordinaryAnchor = fromBranch.isPresent() ? toAnchor : fromAnchor;
        BranchNode branchNode = this.pruneMissingManagedConnections(fromBranch.orElseGet(toBranch::get));
        if (fromAnchor.equals(toAnchor)
                || this.hasConnectionBetween(fromAnchor, toAnchor)
                || !this.isOrdinaryNode(ordinaryAnchor)
                || this.connectionCount(ordinaryAnchor) >= 2
                || branchNode.managedConnectionCount() >= BranchNode.MAX_CONNECTIONS) {
            return Optional.empty();
        }

        try (Batch ignored = this.beginMutationBatch()) {
            PipeConnection connection = this.prepareConnectionForStorage(PipeConnection.withCurve(fromAnchor, toAnchor, curveSpec));
            this.connections.add(connection);
            this.index.upsertConnection(connection);
            this.trackConnectionUpsert(connection);
            this.upsertBranchPipeNode(this.withAddedBranchConnection(branchNode, branchAnchor, connection));
            this.recomputeAutoCurvesAround(Set.of(fromAnchor, toAnchor));
            this.setDirty();
            return this.connection(connection.id()).or(() -> Optional.of(connection));
        }
    }

    private void upsertBranchPipeNode(BranchNode branchNode) {
        branchNode = this.pruneMissingManagedConnections(branchNode);
        this.pruneSharedManagedConnections(branchNode);
        this.validateBranchNode(branchNode);
        PipeAnchorId branchAnchor = branchNode.anchorId();
        PipeNode pipeNode = PipeNode.branch(branchAnchor, branchNode);
        this.nodes.put(branchAnchor, pipeNode);
        this.index.upsertNode(pipeNode);
        this.trackNodeUpsert(pipeNode);
        this.setDirty();
    }

    public Optional<BranchNode> removeBranchNodeById(UUID branchNodeId) {
        for (PipeNode node : List.copyOf(this.nodes.values())) {
            Optional<BranchNode> branchNode = node.branchNode();
            if (branchNode.isPresent() && branchNode.get().id().equals(branchNodeId)) {
                try (Batch ignored = this.beginMutationBatch()) {
                    this.nodes.remove(node.id());
                    this.index.removeNode(node.id());
                    this.trackNodeRemoval(node.id(), branchNode.get().levelKey());
                    this.removeManagedBranchConnections(branchNode.get());
                    this.setDirty();
                    return branchNode;
                }
            }
        }
        return Optional.empty();
    }

    private void validateBranchNode(BranchNode branchNode) {
        Set<UUID> referencedConnectionIds = new HashSet<>(branchNode.managedConnectionIdsInOrder());
        for (UUID connectionId : referencedConnectionIds) {
            PipeConnection connection = this.index.connection(connectionId)
                    .orElseThrow(() -> new IllegalArgumentException("Branch node references a missing pipe connection"));
            if (!connection.levelKey().equals(branchNode.levelKey())) {
                throw new IllegalArgumentException("Branch node references a connection in another dimension");
            }
        }

        for (BranchNode existing : this.branchNodeValues()) {
            if (existing.id().equals(branchNode.id())) {
                continue;
            }
            Set<UUID> existingConnectionIds = new HashSet<>(existing.managedConnectionIdsInOrder());
            if (referencedConnectionIds.stream().anyMatch(existingConnectionIds::contains)) {
                throw new IllegalArgumentException("Branch node managed connections cannot be shared");
            }
        }
    }

    public void addOrdinaryNode(PipeAnchorId anchorId) {
        if (!this.nodes.containsKey(anchorId)) {
            PipeNode node = PipeNode.ordinary(anchorId);
            this.nodes.put(anchorId, node);
            this.index.upsertNode(node);
            this.trackNodeUpsert(node);
            this.setDirty();
        }
    }

    public FoldAnchorNode initializeFoldAnchor(PipeAnchorId anchorId, FoldAnchorKind kind) {
        Optional<FoldAnchorNode> existing = this.foldAnchorAt(anchorId);
        if (existing.isPresent() && existing.get().kind() == kind) {
            return existing.get();
        }

        FoldAnchorNode foldAnchor = FoldAnchorNode.unconfigured(anchorId, kind);
        this.upsertFoldAnchorPipeNode(foldAnchor);
        return foldAnchor;
    }

    public FoldAnchorSaveResult saveFoldAnchor(PipeAnchorId anchorId, long baseConfigRevision, FoldAnchorMode mode, String displayName, Optional<FoldAnchorRef> target, FoldAnchorDirectory directory) {
        Optional<FoldAnchorNode> current = this.foldAnchorAt(anchorId);
        if (current.isEmpty()) {
            return FoldAnchorSaveResult.failure("Fold anchor no longer exists");
        }
        if (current.get().configRevision() != baseConfigRevision) {
            return FoldAnchorSaveResult.failure("Fold anchor changed, please refresh");
        }
        if (mode == FoldAnchorMode.UNCONFIGURED) {
            return FoldAnchorSaveResult.failure("Choose A or B end before saving");
        }

        FoldAnchorNode updated;
        if (mode == FoldAnchorMode.A_END) {
            updated = current.get().asAEnd(displayName);
        } else {
            if (target.isEmpty()) {
                updated = current.get().unboundBEnd();
            } else {
                Optional<FoldAnchorNode> targetNode = directory.foldAnchor(target.get().anchorId());
                Optional<String> validationError = validateFoldBinding(current.get(), target.get(), targetNode, directory);
                if (validationError.isPresent()) {
                    return FoldAnchorSaveResult.failure(validationError.get());
                }
                directory.bAnchorsBoundTo(FoldAnchorRef.of(anchorId)).forEach(boundB -> {
                    if (!boundB.id().equals(anchorId)) {
                        boundB.foldAnchorNode()
                                .filter(FoldAnchorNode::isBEnd)
                                .ifPresent(b -> directory.data(boundB.id().levelKey()).ifPresent(data -> data.upsertFoldAnchorPipeNode(b.unboundBEnd())));
                    }
                });
                updated = current.get().asBEnd(target.get());
            }
        }

        this.upsertFoldAnchorPipeNode(updated);
        return FoldAnchorSaveResult.success(updated);
    }

    public void upsertFoldAnchorPipeNode(FoldAnchorNode foldAnchor) {
        PipeNode pipeNode = PipeNode.foldAnchor(foldAnchor.anchorId(), foldAnchor);
        this.nodes.put(foldAnchor.anchorId(), pipeNode);
        this.index.upsertNode(pipeNode);
        this.trackNodeUpsert(pipeNode);
        this.setDirty();
    }

    private boolean removeOrdinaryNode(PipeAnchorId anchorId) {
        PipeNode node = this.nodes.get(anchorId);
        if (node == null || !node.isOrdinaryAnchor()) {
            return false;
        }
        this.nodes.remove(anchorId);
        return true;
    }

    public Optional<PipeConnection> addConnection(PipeAnchorId fromAnchor, PipeAnchorId toAnchor) {
        return this.addConnection(fromAnchor, toAnchor, CurveSpec.line());
    }

    public Optional<PipeConnection> addConnection(PipeAnchorId fromAnchor, PipeAnchorId toAnchor, CurveSpec curveSpec) {
        if (fromAnchor.equals(toAnchor)
                || !this.canUseStandardConnectionEndpoint(fromAnchor)
                || !this.canUseStandardConnectionEndpoint(toAnchor)
                || this.areBothSpecialAnchors(fromAnchor, toAnchor)
                || this.hasConnectionBetween(fromAnchor, toAnchor)) {
            return Optional.empty();
        }

        try (Batch ignored = this.beginMutationBatch()) {
            PipeConnection connection = this.prepareConnectionForStorage(PipeConnection.withCurve(fromAnchor, toAnchor, curveSpec));
            this.connections.add(connection);
            this.index.upsertConnection(connection);
            this.trackConnectionUpsert(connection);
            this.recomputeAutoCurvesAround(Set.of(fromAnchor, toAnchor));
            this.setDirty();
            return this.connection(connection.id()).or(() -> Optional.of(connection));
        }
    }

    public Optional<PipeConnection> removeConnection(UUID connectionId) {
        Optional<PipeConnection> existing = this.connection(connectionId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        try (Batch ignored = this.beginMutationBatch()) {
            PipeConnection removed = existing.get();
            Set<PipeAnchorId> affectedAnchors = new HashSet<>();
            affectedAnchors.add(removed.fromAnchor());
            affectedAnchors.add(removed.toAnchor());
            boolean removedAny = this.connections.removeIf(connection -> connection.id().equals(connectionId));
            if (!removedAny) {
                return Optional.empty();
            }

            this.index.removeConnection(connectionId);
            this.removeBranchNodesReferencing(List.of(connectionId));
            this.trackConnectionRemovals(List.of(connectionId), removed.levelKey());
            this.recomputeAutoCurvesAround(affectedAnchors);
            this.setDirty();
            return Optional.of(removed);
        }
    }

    public boolean hasConnectionBetween(PipeAnchorId first, PipeAnchorId second) {
        return this.index.hasConnectionBetween(first, second);
    }

    public boolean hasConnectionTouching(PipeAnchorId anchorId) {
        return this.index.connectionCount(anchorId) > 0;
    }

    public boolean canAddConnection(PipeAnchorId fromAnchor, PipeAnchorId toAnchor) {
        return !fromAnchor.equals(toAnchor)
                && !this.areBothSpecialAnchors(fromAnchor, toAnchor)
                && this.canUseStandardConnectionEndpoint(fromAnchor)
                && this.canUseStandardConnectionEndpoint(toAnchor)
                && !this.hasConnectionBetween(fromAnchor, toAnchor);
    }

    public boolean canAddConnectionWithBranchSupport(PipeAnchorId fromAnchor, PipeAnchorId toAnchor) {
        Optional<BranchNode> fromBranch = this.branchNodeAt(fromAnchor);
        Optional<BranchNode> toBranch = this.branchNodeAt(toAnchor);
        if (fromBranch.isPresent() && toBranch.isPresent()) {
            return false;
        }
        if (fromBranch.isEmpty() && toBranch.isEmpty()) {
            return this.canAddConnection(fromAnchor, toAnchor);
        }

        PipeAnchorId ordinaryAnchor = fromBranch.isPresent() ? toAnchor : fromAnchor;
        BranchNode branchNode = fromBranch.orElseGet(toBranch::get);
        return !fromAnchor.equals(toAnchor)
                && this.isOrdinaryNode(ordinaryAnchor)
                && this.connectionCount(ordinaryAnchor) < 2
                && this.validManagedConnectionCount(branchNode) < BranchNode.MAX_CONNECTIONS
                && !this.hasConnectionBetween(fromAnchor, toAnchor);
    }

    public boolean canUseStandardConnectionEndpoint(PipeAnchorId anchorId) {
        Optional<PipeNode> node = this.node(anchorId);
        if (node.isEmpty() || node.get().isBranch()) {
            return false;
        }
        int count = this.connectionCount(anchorId);
        if (node.get().isFoldAnchor()) {
            return count < 1;
        }
        return node.get().isOrdinaryAnchor() && count < 2;
    }

    public int connectionCount(PipeAnchorId anchorId) {
        return this.index.connectionCount(anchorId);
    }

    public List<PipeConnection> connectionsTouching(PipeAnchorId anchorId) {
        return this.index.connectionsTouching(anchorId);
    }

    public Optional<PipeAnchorId> localFoldCounterpart(PipeAnchorId anchorId) {
        Optional<FoldAnchorNode> source = this.foldAnchorAt(anchorId);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        FoldAnchorNode node = source.get();
        if (node.isBEnd()) {
            return node.boundTarget()
                    .map(FoldAnchorRef::anchorId);
        }
        if (node.isAEnd()) {
            return this.foldAnchors().stream()
                    .filter(FoldAnchorNode::isBEnd)
                    .filter(candidate -> candidate.boundTarget().map(FoldAnchorRef::anchorId).filter(anchorId::equals).isPresent())
                    .map(FoldAnchorNode::anchorId)
                    .findFirst();
        }
        return Optional.empty();
    }

    public int removeOrdinaryNodeAndConnections(PipeAnchorId anchorId) {
        if (this.branchNodeAt(anchorId).isPresent() || this.foldAnchorAt(anchorId).isPresent()) {
            return 0;
        }

        try (Batch ignored = this.beginMutationBatch()) {
            boolean removedAnchor = this.removeOrdinaryNode(anchorId);
            Set<PipeAnchorId> affectedAnchors = new HashSet<>();
            List<PipeConnection> touchingConnections = this.connectionsTouching(anchorId);
            touchingConnections.forEach(connection -> PipeConnectionUtils.targetFor(connection, anchorId).ifPresent(affectedAnchors::add));
            int before = this.connections.size();
            List<UUID> removedConnectionIds = touchingConnections.stream()
                    .map(PipeConnection::id)
                    .toList();
            this.connections.removeIf(connection -> connection.touches(anchorId));
            removedConnectionIds.forEach(this.index::removeConnection);
            int removedConnections = before - this.connections.size();
            if (removedAnchor) {
                this.index.removeNode(anchorId);
            }
            this.removeBranchNodesReferencing(removedConnectionIds);
            if (removedAnchor) {
                this.trackNodeRemoval(anchorId, anchorId.levelKey());
            }
            this.trackConnectionRemovals(removedConnectionIds, anchorId.levelKey());
            if (removedConnections > 0) {
                this.recomputeAutoCurvesAround(affectedAnchors);
            }
            if (removedAnchor || removedConnections > 0) {
                this.setDirty();
            }
            return removedConnections;
        }
    }

    public int removeBranchNodeAndConnections(PipeAnchorId branchAnchor) {
        Optional<BranchNode> branchNode = this.branchNodeAt(branchAnchor);
        if (branchNode.isEmpty()) {
            PipeNode node = this.nodes.get(branchAnchor);
            if (node != null && node.isOrdinaryAnchor()) {
                return 0;
            }
            PipeNode removed = this.nodes.remove(branchAnchor);
            if (removed != null) {
                SuperPipeSlide.LOGGER.debug("Pruned invalid non-ordinary pipe node {} while removing BranchNode connections", branchAnchor);
                this.index.removeNode(branchAnchor);
                this.trackNodeRemoval(branchAnchor, branchAnchor.levelKey());
                this.setDirty();
            }
            return 0;
        }

        try (Batch ignored = this.beginMutationBatch()) {
            int removedConnections = branchNode.get().managedConnectionCount();
            this.removeBranchNodeById(branchNode.get().id());
            return removedConnections;
        }
    }

    public int removeFoldAnchorAndConnections(PipeAnchorId anchorId, FoldAnchorDirectory directory) {
        Optional<FoldAnchorNode> foldAnchor = this.foldAnchorAt(anchorId);
        if (foldAnchor.isEmpty()) {
            return 0;
        }

        try (Batch ignored = this.beginMutationBatch()) {
            int removedConnections = this.removeConnectionsTouching(anchorId);
            this.nodes.remove(anchorId);
            this.index.removeNode(anchorId);
            this.trackNodeRemoval(anchorId, anchorId.levelKey());
            FoldAnchorRef removedRef = FoldAnchorRef.of(anchorId);
            directory.bAnchorsBoundTo(removedRef).forEach(boundB -> boundB.foldAnchorNode()
                    .filter(FoldAnchorNode::isBEnd)
                    .ifPresent(b -> directory.data(boundB.id().levelKey()).ifPresent(data -> data.upsertFoldAnchorPipeNode(b.unboundBEnd()))));
            this.setDirty();
            return removedConnections;
        }
    }

    private boolean pruneMissingAnchorBlockNode(PipeNode node, FoldAnchorDirectory directory) {
        PipeAnchorId anchorId = node.id();
        if (!this.nodes.containsKey(anchorId)) {
            return false;
        }

        int nodesBefore = this.nodes.size();
        int connectionsBefore = this.connections.size();
        switch (node.type()) {
            case ORDINARY_ANCHOR -> this.removeOrdinaryNodeAndConnections(anchorId);
            case BRANCH -> this.removeBranchNodeAndConnections(anchorId);
            case FOLD_ANCHOR -> this.removeFoldAnchorAndConnections(anchorId, directory);
        }
        return this.nodes.size() != nodesBefore || this.connections.size() != connectionsBefore;
    }

    public int removeConnectionsTouching(PipeAnchorId anchorId) {
        try (Batch ignored = this.beginMutationBatch()) {
            Set<PipeAnchorId> affectedAnchors = new HashSet<>();
            List<PipeConnection> touchingConnections = this.connectionsTouching(anchorId);
            touchingConnections.forEach(connection -> PipeConnectionUtils.targetFor(connection, anchorId).ifPresent(affectedAnchors::add));
            int before = this.connections.size();
            List<UUID> removedConnectionIds = touchingConnections.stream()
                    .map(PipeConnection::id)
                    .toList();
            this.connections.removeIf(connection -> connection.touches(anchorId));
            removedConnectionIds.forEach(this.index::removeConnection);
            int removed = before - this.connections.size();
            this.removeBranchNodesReferencing(removedConnectionIds);
            this.trackConnectionRemovals(removedConnectionIds, anchorId.levelKey());
            if (removed > 0) {
                this.recomputeAutoCurvesAround(affectedAnchors);
            }
            if (removed > 0) {
                this.setDirty();
            }
            return removed;
        }
    }

    public int trimConnectionsTouching(PipeAnchorId anchorId, int maxConnections) {
        List<UUID> removedConnectionIds = this.orderedConnectionsTouching(anchorId).stream()
                .skip(Math.max(0, maxConnections))
                .map(PipeConnection::id)
                .toList();
        int removed = 0;
        for (UUID connectionId : removedConnectionIds) {
            if (this.removeConnection(connectionId).isPresent()) {
                removed++;
            }
        }
        return removed;
    }

    private void removeBranchNodesReferencing(Collection<UUID> connectionIds) {
        if (connectionIds.isEmpty()) {
            return;
        }

        Set<UUID> removedConnectionIds = new HashSet<>(connectionIds);
        List<BranchNode> changedBranchNodes = this.branchNodeValues().stream()
                .filter(branchNode -> removedConnectionIds.stream().anyMatch(branchNode::referencesConnection))
                .toList();
        if (changedBranchNodes.isEmpty()) {
            return;
        }

        for (BranchNode branchNode : changedBranchNodes) {
            List<BranchConnectionSlot> keptConnections = branchNode.connections().stream()
                    .filter(slot -> !removedConnectionIds.contains(slot.connectionId()))
                    .toList();
            SuperPipeSlide.LOGGER.debug("Removed stale managed connections {} from BranchNode {}", removedConnectionIds, branchNode.id());
            this.upsertBranchPipeNode(branchNode.withConnections(keptConnections));
        }
    }

    private void removeManagedBranchConnections(BranchNode branchNode) {
        Set<UUID> managedConnectionIds = new HashSet<>(branchNode.managedConnectionIdsInOrder());

        Set<PipeAnchorId> affectedAnchors = new HashSet<>();
        this.connections.stream()
                .filter(connection -> managedConnectionIds.contains(connection.id()))
                .forEach(connection -> {
                    affectedAnchors.add(connection.fromAnchor());
                    affectedAnchors.add(connection.toAnchor());
                });

        boolean removedAny = this.connections.removeIf(connection -> managedConnectionIds.contains(connection.id()));
        managedConnectionIds.forEach(this.index::removeConnection);
        this.trackConnectionRemovals(managedConnectionIds, branchNode.levelKey());
        this.removeBranchNodesReferencing(managedConnectionIds);
        if (removedAny) {
            this.recomputeAutoCurvesAround(affectedAnchors);
        }
    }

    private boolean isManagedByBranchNode(PipeConnection connection) {
        return this.branchNodeManagingConnection(connection.id()).isPresent();
    }

    private boolean touchesSpecialNode(PipeConnection connection) {
        return this.isSpecialAnchorNode(connection.fromAnchor()) || this.isSpecialAnchorNode(connection.toAnchor());
    }

    private boolean areBothSpecialAnchors(PipeAnchorId first, PipeAnchorId second) {
        return this.isSpecialAnchorNode(first) && this.isSpecialAnchorNode(second);
    }

    private int validManagedConnectionCount(BranchNode branchNode) {
        int count = 0;
        for (UUID connectionId : branchNode.managedConnectionIdsInOrder()) {
            if (this.index.connection(connectionId)
                    .filter(connection -> connection.levelKey().equals(branchNode.levelKey()))
                    .isPresent()) {
                count++;
            }
        }
        return count;
    }

    private BranchNode pruneMissingManagedConnections(BranchNode branchNode) {
        List<BranchConnectionSlot> keptConnections = branchNode.connections().stream()
                .filter(slot -> this.index.connection(slot.connectionId())
                        .filter(connection -> connection.levelKey().equals(branchNode.levelKey()))
                        .isPresent())
                .toList();
        if (keptConnections.size() == branchNode.connections().size()) {
            return branchNode;
        }

        Set<UUID> removedConnectionIds = branchNode.connections().stream()
                .map(BranchConnectionSlot::connectionId)
                .filter(connectionId -> keptConnections.stream().noneMatch(slot -> slot.connectionId().equals(connectionId)))
                .collect(java.util.stream.Collectors.toSet());
        BranchNode pruned = branchNode.withConnections(keptConnections);
        PipeNode pipeNode = PipeNode.branch(pruned.anchorId(), pruned);
        this.nodes.put(pipeNode.id(), pipeNode);
        this.index.upsertNode(pipeNode);
        this.trackNodeUpsert(pipeNode);
        this.setDirty();
        SuperPipeSlide.LOGGER.debug("Pruned stale managed connections {} from BranchNode {}", removedConnectionIds, branchNode.id());
        return pruned;
    }

    private void pruneSharedManagedConnections(BranchNode branchNode) {
        Set<UUID> referencedConnectionIds = new HashSet<>(branchNode.managedConnectionIdsInOrder());
        if (referencedConnectionIds.isEmpty()) {
            return;
        }

        for (BranchNode existing : this.branchNodeValues()) {
            if (existing.id().equals(branchNode.id())) {
                continue;
            }

            List<BranchConnectionSlot> keptConnections = existing.connections().stream()
                    .filter(slot -> !referencedConnectionIds.contains(slot.connectionId()))
                    .toList();
            if (keptConnections.size() == existing.connections().size()) {
                continue;
            }

            Set<UUID> removedConnectionIds = existing.connections().stream()
                    .map(BranchConnectionSlot::connectionId)
                    .filter(referencedConnectionIds::contains)
                    .collect(java.util.stream.Collectors.toSet());
            BranchNode pruned = existing.withConnections(keptConnections);
            PipeNode pipeNode = PipeNode.branch(pruned.anchorId(), pruned);
            this.nodes.put(pipeNode.id(), pipeNode);
            this.index.upsertNode(pipeNode);
            this.trackNodeUpsert(pipeNode);
            this.setDirty();
            SuperPipeSlide.LOGGER.debug("Pruned shared managed connections {} from BranchNode {}", removedConnectionIds, existing.id());
        }
    }

    private List<PipeConnection> orderedConnectionsTouching(PipeAnchorId anchorId) {
        List<PipeConnection> touching = new ArrayList<>();
        for (PipeConnection connection : this.connections) {
            if (connection.touches(anchorId)) {
                touching.add(connection);
            }
        }
        return touching;
    }

    private BranchNode withAddedBranchConnection(BranchNode branchNode, PipeAnchorId branchAnchor, PipeConnection connection) {
        List<BranchConnectionSlot> managedConnections = new ArrayList<>(branchNode.connections());
        BranchConnectionSlot slot = this.branchConnectionSlotFor(branchAnchor, connection, managedConnections.isEmpty() ? "Default" : "Path " + (managedConnections.size() + 1));
        managedConnections.add(slot);
        Optional<UUID> defaultConnectionId = branchNode.defaultConnectionId().or(() -> Optional.of(connection.id()));
        return new BranchNode(branchNode.id(), branchNode.levelKey(), branchNode.position(), branchNode.optionalAnchorId(), defaultConnectionId, managedConnections);
    }

    private BranchConnectionSlot branchConnectionSlotFor(PipeAnchorId branchAnchor, PipeConnection connection, String displayName) {
        Vec3 worldDirection = PipeConnectionUtils.targetFor(connection, branchAnchor)
                .map(target -> surfacePosition(target).subtract(surfacePosition(branchAnchor)))
                .orElse(new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 normalizedWorldDirection = worldDirection.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : worldDirection.normalize();
        Vec3 forward = this.orderedConnectionsTouching(branchAnchor).stream()
                .filter(candidate -> !candidate.id().equals(connection.id()))
                .findFirst()
                .map(candidate -> endpointTangentAt(candidate, branchAnchor))
                .filter(tangent -> tangent.lengthSqr() >= 1.0E-6D)
                .orElse(normalizedWorldDirection);
        BranchFrame frame = branchFrame(forward);
        Vec3 localDirection = worldToLocal(normalizedWorldDirection, frame);
        return new BranchConnectionSlot(UUID.randomUUID(), connection.id(), displayName, localDirection, localDirection.scale(3.0D), Optional.empty());
    }

    private static BranchFrame branchFrame(Vec3 forward) {
        Vec3 normalizedForward = forward.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : forward.normalize();
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = normalizedForward.cross(up);
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        up = right.cross(normalizedForward).normalize();
        return new BranchFrame(normalizedForward, up, right);
    }

    private static Vec3 worldToLocal(Vec3 worldDirection, BranchFrame frame) {
        return new Vec3(worldDirection.dot(frame.right()), worldDirection.dot(frame.up()), worldDirection.dot(frame.forward()));
    }

    private void recomputeAutoCurvesAround(Set<PipeAnchorId> anchors) {
        boolean changed = false;
        for (Map.Entry<UUID, CurveSpec> entry : AutoCurveSolver.recomputeAutoCurvesAround(this, anchors).entrySet()) {
            UUID connectionId = entry.getKey();
            Optional<PipeConnection> connection = this.connection(connectionId);
            if (connection.isEmpty()) {
                continue;
            }

            PipeConnection current = connection.get();
            CurveSpec updatedSpec = entry.getValue();
            PipeConnection updated = current.withCurveSpec(updatedSpec);
            if (!updated.curveSpec().equals(current.curveSpec())) {
                this.replaceConnection(updated);
                changed = true;
            }
        }

        if (changed) {
            this.setDirty();
        }
    }

    private boolean replaceConnection(PipeConnection updated) {
        updated = this.prepareConnectionForStorage(updated);
        for (int i = 0; i < this.connections.size(); i++) {
            if (this.connections.get(i).id().equals(updated.id())) {
                this.connections.set(i, updated);
                this.index.upsertConnection(updated);
                this.trackConnectionUpsert(updated);
                return true;
            }
        }
        SuperPipeSlide.LOGGER.debug("Ignored pipe connection replacement for missing connection {}", updated.id());
        return false;
    }

    public PendingPipeNetworkChanges consumePendingNetworkChanges() {
        return this.syncTracker.consume(this.revision);
    }

    public record PendingPipeNetworkChanges(PipeNetworkDeltaPayload payload, PipeNetworkChangeSet changeSet) {
        public boolean isEmpty() {
            return this.payload.isEmpty() && this.changeSet.isEmpty();
        }
    }

    public record BrokenAnchorCleanupResult(int removedNodes, int removedConnections) {
        public static final BrokenAnchorCleanupResult EMPTY = new BrokenAnchorCleanupResult(0, 0);

        public boolean changed() {
            return this.removedNodes > 0 || this.removedConnections > 0;
        }
    }

    private void trackNodeUpsert(PipeNode node) {
        if (!this.trackingChanges) {
            return;
        }

        this.syncTracker.trackNodeUpsert(node);
        this.bumpRevision();
    }

    private void trackNodeRemoval(PipeAnchorId nodeId, ResourceKey<Level> levelKey) {
        if (!this.trackingChanges) {
            return;
        }

        this.syncTracker.trackNodeRemoval(nodeId, levelKey);
        this.bumpRevision();
    }

    private void trackConnectionUpsert(PipeConnection connection) {
        if (!this.trackingChanges) {
            return;
        }

        this.syncTracker.trackConnectionUpsert(connection);
        this.bumpRevision();
    }

    private void trackConnectionRemovals(Collection<UUID> connectionIds, ResourceKey<Level> levelKey) {
        if (!this.trackingChanges || connectionIds.isEmpty()) {
            return;
        }

        this.syncTracker.trackConnectionRemovals(connectionIds, levelKey);
        this.bumpRevision();
    }

    private void bumpRevision() {
        if (this.batchDepth > 0) {
            if (!this.batchRevisionDirty) {
                this.syncTracker.captureBaseRevision(this.revision);
            }
            this.batchRevisionDirty = true;
            this.setDirty();
            return;
        }
        this.syncTracker.captureBaseRevision(this.revision);
        this.revision++;
        this.setDirty();
    }

    private List<PipeNode> nodesForCodec() {
        return List.copyOf(this.nodes.values());
    }

    private List<PipeConnection> connectionsForCodec() {
        return this.connections;
    }

    private int nextConnectionKeyForCodec() {
        return this.nextConnectionKey;
    }

    private void rebuildIndex() {
        this.index.reset(this.nodes.values(), this.connections);
    }

    private PipeConnection prepareConnectionForStorage(PipeConnection connection) {
        if (connection.connectionKey() > PipeConnection.TRANSIENT_CONNECTION_KEY) {
            this.nextConnectionKey = Math.max(this.nextConnectionKey, connection.connectionKey() + 1);
            return connection;
        }
        return connection.withConnectionKey(this.allocateConnectionKey());
    }

    private int allocateConnectionKey() {
        Set<Integer> usedKeys = this.connections.stream()
                .map(PipeConnection::connectionKey)
                .filter(key -> key > PipeConnection.TRANSIENT_CONNECTION_KEY)
                .collect(java.util.stream.Collectors.toSet());
        while (usedKeys.contains(this.nextConnectionKey)) {
            this.nextConnectionKey++;
        }
        return this.nextConnectionKey++;
    }

    private boolean normalizeConnectionKeys() {
        Set<Integer> usedKeys = new HashSet<>();
        boolean changed = false;
        for (int i = 0; i < this.connections.size(); i++) {
            PipeConnection connection = this.connections.get(i);
            int key = connection.connectionKey();
            if (key <= PipeConnection.TRANSIENT_CONNECTION_KEY || !usedKeys.add(key)) {
                int allocated = this.allocateConnectionKey(usedKeys);
                PipeConnection updated = connection.withConnectionKey(allocated);
                this.connections.set(i, updated);
                usedKeys.add(allocated);
                changed = true;
            } else {
                this.nextConnectionKey = Math.max(this.nextConnectionKey, key + 1);
            }
        }
        return changed;
    }

    private int allocateConnectionKey(Set<Integer> usedKeys) {
        while (usedKeys.contains(this.nextConnectionKey)) {
            this.nextConnectionKey++;
        }
        return this.nextConnectionKey++;
    }

    private void prunePhaseOneConnectionConflicts() {
        Set<UUID> branchManagedConnectionIds = this.branchManagedConnectionIds();
        Map<PipeAnchorId, Set<PipeAnchorId>> targetsByAnchor = new HashMap<>();
        Set<ConnectionKey> seenPairs = new HashSet<>();
        List<UUID> removedConnectionIds = new ArrayList<>();
        int before = this.connections.size();

        this.connections.removeIf(connection -> {
            if (branchManagedConnectionIds.contains(connection.id()) && !this.areBothSpecialAnchors(connection.fromAnchor(), connection.toAnchor())) {
                return false;
            }

            if (connection.fromAnchor().equals(connection.toAnchor())) {
                removedConnectionIds.add(connection.id());
                return true;
            }

            if (this.areBothSpecialAnchors(connection.fromAnchor(), connection.toAnchor())) {
                removedConnectionIds.add(connection.id());
                return true;
            }

            ConnectionKey key = ConnectionKey.of(connection.fromAnchor(), connection.toAnchor());
            if (!seenPairs.add(key)) {
                removedConnectionIds.add(connection.id());
                return true;
            }

            Set<PipeAnchorId> fromTargets = targetsByAnchor.computeIfAbsent(connection.fromAnchor(), ignored -> new HashSet<>());
            Set<PipeAnchorId> toTargets = targetsByAnchor.computeIfAbsent(connection.toAnchor(), ignored -> new HashSet<>());
            if (fromTargets.size() >= this.connectionLimitForPrune(connection.fromAnchor())
                    || toTargets.size() >= this.connectionLimitForPrune(connection.toAnchor())
                    || fromTargets.contains(connection.toAnchor())
                    || toTargets.contains(connection.fromAnchor())) {
                removedConnectionIds.add(connection.id());
                return true;
            }

            fromTargets.add(connection.toAnchor());
            toTargets.add(connection.fromAnchor());
            return false;
        });

        if (this.connections.size() != before) {
            SuperPipeSlide.LOGGER.debug("Pruned invalid ordinary pipe connections from saved network data: {}", removedConnectionIds);
            this.setDirty();
        }
    }

    private int connectionLimitForPrune(PipeAnchorId anchorId) {
        return this.node(anchorId).filter(PipeNode::isFoldAnchor).isPresent() ? 1 : 2;
    }

    private Set<UUID> branchManagedConnectionIds() {
        Set<UUID> connectionIds = new HashSet<>();
        for (BranchNode branchNode : this.branchNodeValues()) {
            connectionIds.addAll(branchNode.managedConnectionIdsInOrder());
        }
        return connectionIds;
    }

    private void pruneInvalidBranchNodes() {
        Set<UUID> connectionIds = this.connections.stream()
                .map(PipeConnection::id)
                .collect(java.util.stream.Collectors.toSet());
        List<BranchNode> invalidBranchNodes = this.branchNodeValues().stream()
                .filter(branchNode -> branchNode.managedConnectionIdsInOrder().stream().anyMatch(connectionId -> !connectionIds.contains(connectionId)))
                .toList();
        for (BranchNode branchNode : invalidBranchNodes) {
            List<BranchConnectionSlot> keptConnections = branchNode.connections().stream()
                    .filter(slot -> connectionIds.contains(slot.connectionId()))
                    .toList();
            Set<UUID> removedConnectionIds = branchNode.connections().stream()
                    .map(BranchConnectionSlot::connectionId)
                    .filter(connectionId -> !connectionIds.contains(connectionId))
                    .collect(java.util.stream.Collectors.toSet());
            SuperPipeSlide.LOGGER.debug("Pruned missing managed connections {} from BranchNode {} in pipe network data", removedConnectionIds, branchNode.id());
            PipeNode pipeNode = PipeNode.branch(branchNode.anchorId(), branchNode.withConnections(keptConnections));
            this.nodes.put(pipeNode.id(), pipeNode);
            this.setDirty();
        }
    }

    private static Vec3 endpointTangentAt(PipeConnection connection, PipeAnchorId anchorId) {
        if (connection.fromAnchor().equals(anchorId)) {
            return connection.tangentAt(0.0D);
        }
        if (connection.toAnchor().equals(anchorId)) {
            return connection.tangentAt(connection.length());
        }
        return Vec3.ZERO;
    }

    private static Vec3 surfacePosition(PipeAnchorId anchorId) {
        return Vec3.atCenterOf(anchorId.blockPos());
    }

    private static boolean isAnchorBlock(Block block) {
        return block == SPSBlocks.PIPE_ANCHOR.get()
                || block == SPSBlocks.BRANCH_ANCHOR.get()
                || block == SPSBlocks.SPACE_FOLD_ANCHOR.get()
                || block == SPSBlocks.DIMENSION_FOLD_ANCHOR.get();
    }

    private Optional<String> validateFoldBinding(FoldAnchorNode source, FoldAnchorRef targetRef, Optional<FoldAnchorNode> targetNode, FoldAnchorDirectory directory) {
        if (targetNode.isEmpty()) {
            return Optional.of("Target A end no longer exists");
        }
        FoldAnchorNode target = targetNode.get();
        if (!target.kind().equals(source.kind())) {
            return Optional.of("Fold anchors must have the same type");
        }
        if (!target.isAEnd()) {
            return Optional.of("Target anchor is not an A end");
        }
        if (source.kind() == FoldAnchorKind.SPACE && !source.anchorId().levelKey().equals(target.anchorId().levelKey())) {
            return Optional.of("Space folding anchors must bind within the same dimension");
        }
        if (source.kind() == FoldAnchorKind.DIMENSION && source.anchorId().levelKey().equals(target.anchorId().levelKey())) {
            return Optional.of("Dimensional folding anchors must bind another dimension");
        }
        Optional<PipeNode> existingBoundB = directory.bAnchorsBoundTo(targetRef).stream()
                .filter(node -> !node.id().equals(source.anchorId()))
                .findFirst();
        if (existingBoundB.isPresent()) {
            return Optional.of("Target A end is already bound");
        }
        return Optional.empty();
    }

    public record FoldAnchorSaveResult(boolean accepted, String message, Optional<FoldAnchorNode> updated) {
        public static FoldAnchorSaveResult success(FoldAnchorNode updated) {
            return new FoldAnchorSaveResult(true, "Fold anchor saved", Optional.of(updated));
        }

        public static FoldAnchorSaveResult failure(String message) {
            return new FoldAnchorSaveResult(false, message, Optional.empty());
        }
    }

    private record ConnectionKey(PipeAnchorId first, PipeAnchorId second) {
        static ConnectionKey of(PipeAnchorId first, PipeAnchorId second) {
            return first.toString().compareTo(second.toString()) <= 0 ? new ConnectionKey(first, second) : new ConnectionKey(second, first);
        }
    }

    private record BranchFrame(Vec3 forward, Vec3 up, Vec3 right) {}
}
