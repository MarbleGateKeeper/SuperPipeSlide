package dev.marblegate.superpipeslide.common.core.networkgraph.storage;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionUtils;
import dev.marblegate.superpipeslide.common.core.geometry.RuntimePipeConnection;
import dev.marblegate.superpipeslide.common.core.networkgraph.branch.BranchNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.model.PipeNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PipeNetworkIndex {
    private static final double CELL_SIZE = 16.0D;

    private final Map<PipeAnchorId, PipeNode> nodesById = new HashMap<>();
    private final Set<PipeAnchorId> ordinaryAnchors = new HashSet<>();
    private final Map<UUID, PipeConnection> connectionsById = new HashMap<>();
    private final Map<Integer, UUID> connectionIdsByKey = new HashMap<>();
    private final Map<PipeAnchorId, Set<UUID>> connectionsByAnchor = new HashMap<>();
    private final Map<UUID, RuntimePipeConnection> runtimeConnectionsById = new HashMap<>();
    private final LinkedHashSet<UUID> dirtyRuntimeConnectionIds = new LinkedHashSet<>();
    private final Map<CellKey, Set<UUID>> spatialCells = new HashMap<>();
    private final Map<UUID, Set<CellKey>> cellsByConnection = new HashMap<>();
    private final Map<CellKey, Set<PipeAnchorId>> nodeSpatialCells = new HashMap<>();
    private final Map<PipeAnchorId, CellKey> cellByNode = new HashMap<>();
    private final Map<UUID, BranchNode> branchNodesById = new HashMap<>();
    private final Map<UUID, UUID> branchNodeByManagedConnectionId = new HashMap<>();
    private final Map<PipeAnchorId, FoldAnchorNode> foldAnchorsById = new HashMap<>();

    public void reset(Collection<PipeNode> nodes, Collection<PipeConnection> connections) {
        this.clear();
        nodes.forEach(this::upsertNode);
        for (PipeConnection connection : connections) {
            this.upsertConnection(connection);
        }
    }

    public void clear() {
        this.nodesById.clear();
        this.ordinaryAnchors.clear();
        this.connectionsById.clear();
        this.connectionIdsByKey.clear();
        this.connectionsByAnchor.clear();
        this.runtimeConnectionsById.clear();
        this.dirtyRuntimeConnectionIds.clear();
        this.spatialCells.clear();
        this.cellsByConnection.clear();
        this.nodeSpatialCells.clear();
        this.cellByNode.clear();
        this.branchNodesById.clear();
        this.branchNodeByManagedConnectionId.clear();
        this.foldAnchorsById.clear();
    }

    public void upsertNode(PipeNode node) {
        PipeNode previous = this.nodesById.put(node.id(), node);
        if (previous != null) {
            this.removeNodeProjection(previous);
        }
        this.addNodeProjection(node);
    }

    public void removeNode(PipeAnchorId nodeId) {
        PipeNode removed = this.nodesById.remove(nodeId);
        if (removed != null) {
            this.removeNodeProjection(removed);
        }
    }

    public Optional<PipeNode> node(PipeAnchorId nodeId) {
        return Optional.ofNullable(this.nodesById.get(nodeId));
    }

    public boolean isOrdinaryNode(PipeAnchorId anchorId) {
        return this.ordinaryAnchors.contains(anchorId);
    }

    public Optional<BranchNode> branchNodeAt(PipeAnchorId anchorId) {
        return this.node(anchorId).flatMap(PipeNode::branchNode);
    }

    public Optional<FoldAnchorNode> foldAnchorAt(PipeAnchorId anchorId) {
        return Optional.ofNullable(this.foldAnchorsById.get(anchorId));
    }

    public Collection<FoldAnchorNode> foldAnchors() {
        return List.copyOf(this.foldAnchorsById.values());
    }

    public Collection<PipeNode> nodesIn(ResourceKey<Level> levelKey) {
        return this.nodesById.values().stream()
                .filter(node -> node.id().levelKey().equals(levelKey))
                .toList();
    }

    public Collection<PipeNode> nodesNear(ResourceKey<Level> levelKey, Vec3 position, double radius) {
        int minX = cell(position.x - radius);
        int minY = cell(position.y - radius);
        int minZ = cell(position.z - radius);
        int maxX = cell(position.x + radius);
        int maxY = cell(position.y + radius);
        int maxZ = cell(position.z + radius);
        Set<PipeAnchorId> candidateIds = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Set<PipeAnchorId> cellNodes = this.nodeSpatialCells.get(new CellKey(levelKey, x, y, z));
                    if (cellNodes != null) {
                        candidateIds.addAll(cellNodes);
                    }
                }
            }
        }
        if (candidateIds.isEmpty()) {
            return List.of();
        }

        double radiusSqr = radius * radius;
        List<PipeNode> nearby = new ArrayList<>();
        for (PipeAnchorId candidateId : candidateIds) {
            PipeNode node = this.nodesById.get(candidateId);
            if (node != null && node.id().levelKey().equals(levelKey) && centerDistanceSqr(node.id(), position) <= radiusSqr) {
                nearby.add(node);
            }
        }
        return nearby;
    }

    public void upsertConnection(PipeConnection connection) {
        PipeConnection previous = this.connectionsById.put(connection.id(), connection);
        if (previous != null) {
            this.removeAnchorReference(previous.fromAnchor(), previous.id());
            this.removeAnchorReference(previous.toAnchor(), previous.id());
            if (previous.connectionKey() > PipeConnection.TRANSIENT_CONNECTION_KEY) {
                this.connectionIdsByKey.remove(previous.connectionKey());
            }
        }

        if (connection.connectionKey() > PipeConnection.TRANSIENT_CONNECTION_KEY) {
            this.connectionIdsByKey.put(connection.connectionKey(), connection.id());
        }
        this.connectionsByAnchor.computeIfAbsent(connection.fromAnchor(), ignored -> new HashSet<>()).add(connection.id());
        this.connectionsByAnchor.computeIfAbsent(connection.toAnchor(), ignored -> new HashSet<>()).add(connection.id());
        this.dirtyRuntimeConnectionIds.add(connection.id());
    }

    public void removeConnection(UUID connectionId) {
        PipeConnection removed = this.connectionsById.remove(connectionId);
        if (removed != null) {
            this.removeAnchorReference(removed.fromAnchor(), connectionId);
            this.removeAnchorReference(removed.toAnchor(), connectionId);
            if (removed.connectionKey() > PipeConnection.TRANSIENT_CONNECTION_KEY) {
                this.connectionIdsByKey.remove(removed.connectionKey());
            }
        }
        this.removeRuntimeConnection(connectionId);
        this.dirtyRuntimeConnectionIds.remove(connectionId);
    }

    public Optional<PipeConnection> connection(UUID id) {
        return Optional.ofNullable(this.connectionsById.get(id));
    }

    public Optional<PipeConnection> connectionByKey(int connectionKey) {
        UUID connectionId = this.connectionIdsByKey.get(connectionKey);
        return connectionId == null ? Optional.empty() : this.connection(connectionId);
    }

    public Collection<PipeConnection> connectionsIn(ResourceKey<Level> levelKey) {
        return this.connectionsById.values().stream()
                .filter(connection -> connection.levelKey().equals(levelKey))
                .toList();
    }

    public List<PipeConnection> connectionsTouching(PipeAnchorId anchorId) {
        Set<UUID> connectionIds = this.connectionsByAnchor.get(anchorId);
        if (connectionIds == null || connectionIds.isEmpty()) {
            return List.of();
        }

        List<PipeConnection> connections = new ArrayList<>(connectionIds.size());
        for (UUID connectionId : connectionIds) {
            PipeConnection connection = this.connectionsById.get(connectionId);
            if (connection != null) {
                connections.add(connection);
            }
        }
        return connections;
    }

    public int connectionCount(PipeAnchorId anchorId) {
        Set<UUID> connectionIds = this.connectionsByAnchor.get(anchorId);
        return connectionIds == null ? 0 : connectionIds.size();
    }

    public boolean hasConnectionBetween(PipeAnchorId first, PipeAnchorId second) {
        Set<UUID> connectionIds = this.connectionsByAnchor.get(first);
        if (connectionIds == null || connectionIds.isEmpty()) {
            return false;
        }
        for (UUID connectionId : connectionIds) {
            PipeConnection connection = this.connectionsById.get(connectionId);
            if (connection != null && PipeConnectionUtils.connectsSameAnchors(connection, first, second)) {
                return true;
            }
        }
        return false;
    }

    public Optional<BranchNode> branchNode(UUID branchNodeId) {
        return Optional.ofNullable(this.branchNodesById.get(branchNodeId));
    }

    public Optional<BranchNode> branchNodeManagingConnection(UUID connectionId) {
        UUID branchNodeId = this.branchNodeByManagedConnectionId.get(connectionId);
        return branchNodeId == null ? Optional.empty() : this.branchNode(branchNodeId);
    }

    public List<PipeConnection> connectionsNear(ResourceKey<Level> levelKey, Vec3 position, double radius, int runtimeBuildBudget) {
        this.processDirtyRuntimeConnectionsNear(levelKey, position, radius, runtimeBuildBudget);
        Set<UUID> candidateIds = this.connectionIdsNear(levelKey, position, radius);
        for (UUID connectionId : this.dirtyRuntimeConnectionIds) {
            PipeConnection connection = this.connectionsById.get(connectionId);
            if (connection != null && connection.levelKey().equals(levelKey) && roughBounds(connection).inflate(radius).contains(position)) {
                candidateIds.add(connectionId);
            }
        }
        if (candidateIds.isEmpty()) {
            return List.of();
        }

        List<PipeConnection> nearby = new ArrayList<>();
        for (UUID connectionId : candidateIds) {
            RuntimePipeConnection runtime = this.runtimeConnectionsById.get(connectionId);
            if (runtime != null && runtime.connection().levelKey().equals(levelKey) && runtime.isNear(position, radius)) {
                nearby.add(runtime.connection());
                continue;
            }
            PipeConnection connection = this.connectionsById.get(connectionId);
            if (connection != null
                    && this.dirtyRuntimeConnectionIds.contains(connectionId)
                    && connection.levelKey().equals(levelKey)
                    && roughBounds(connection).inflate(radius).contains(position)) {
                nearby.add(connection);
            }
        }
        return nearby;
    }

    public Collection<RuntimePipeConnection> runtimeConnectionsNear(ResourceKey<Level> levelKey, Vec3 position, double radius, int runtimeBuildBudget) {
        this.processDirtyRuntimeConnectionsNear(levelKey, position, radius, runtimeBuildBudget);
        Set<UUID> candidateIds = this.connectionIdsNear(levelKey, position, radius);
        for (UUID connectionId : this.dirtyRuntimeConnectionIds) {
            PipeConnection connection = this.connectionsById.get(connectionId);
            if (connection != null && connection.levelKey().equals(levelKey) && roughBounds(connection).inflate(radius).contains(position)) {
                candidateIds.add(connectionId);
            }
        }
        if (candidateIds.isEmpty()) {
            return List.of();
        }

        List<RuntimePipeConnection> nearby = new ArrayList<>();
        for (UUID connectionId : candidateIds) {
            RuntimePipeConnection runtime = this.runtimeConnectionsById.get(connectionId);
            if (runtime != null && runtime.connection().levelKey().equals(levelKey) && runtime.bounds().inflate(radius).contains(position)) {
                nearby.add(runtime);
                continue;
            }
            PipeConnection connection = this.connectionsById.get(connectionId);
            if (connection != null
                    && this.dirtyRuntimeConnectionIds.contains(connectionId)
                    && connection.levelKey().equals(levelKey)
                    && roughBounds(connection).inflate(radius).contains(position)) {
                nearby.add(RuntimePipeConnection.create(connection));
            }
        }
        return nearby;
    }

    public Collection<RuntimePipeConnection> runtimeConnections(ResourceKey<Level> levelKey, int runtimeBuildBudget) {
        this.processDirtyRuntimeConnections(runtimeBuildBudget);
        return this.runtimeConnectionsById.values().stream()
                .filter(runtime -> runtime.connection().levelKey().equals(levelKey))
                .toList();
    }

    public void processDirtyRuntimeConnections(int maxConnections) {
        int processed = 0;
        while (processed < maxConnections && !this.dirtyRuntimeConnectionIds.isEmpty()) {
            UUID connectionId = this.dirtyRuntimeConnectionIds.iterator().next();
            this.dirtyRuntimeConnectionIds.remove(connectionId);
            PipeConnection connection = this.connectionsById.get(connectionId);
            if (connection == null) {
                this.removeRuntimeConnection(connectionId);
                continue;
            }

            RuntimePipeConnection runtime = RuntimePipeConnection.create(connection);
            this.replaceRuntimeConnection(runtime);
            processed++;
        }
    }

    public void processDirtyRuntimeConnectionsNear(ResourceKey<Level> levelKey, Vec3 position, double radius, int maxConnections) {
        int processed = 0;
        List<UUID> nearbyDirty = new ArrayList<>();
        for (UUID connectionId : this.dirtyRuntimeConnectionIds) {
            PipeConnection connection = this.connectionsById.get(connectionId);
            if (connection != null && connection.levelKey().equals(levelKey) && roughBounds(connection).inflate(radius).contains(position)) {
                nearbyDirty.add(connectionId);
                if (nearbyDirty.size() >= maxConnections) {
                    break;
                }
            }
        }

        for (UUID connectionId : nearbyDirty) {
            if (!this.dirtyRuntimeConnectionIds.remove(connectionId)) {
                continue;
            }
            PipeConnection connection = this.connectionsById.get(connectionId);
            if (connection != null) {
                this.replaceRuntimeConnection(RuntimePipeConnection.create(connection));
                processed++;
            }
        }

        if (processed < maxConnections) {
            this.processDirtyRuntimeConnections(maxConnections - processed);
        }
    }

    public int pendingRuntimeRebuilds() {
        return this.dirtyRuntimeConnectionIds.size();
    }

    private Set<UUID> connectionIdsNear(ResourceKey<Level> levelKey, Vec3 position, double radius) {
        int minX = cell(position.x - radius);
        int minY = cell(position.y - radius);
        int minZ = cell(position.z - radius);
        int maxX = cell(position.x + radius);
        int maxY = cell(position.y + radius);
        int maxZ = cell(position.z + radius);
        Set<UUID> connectionIds = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Set<UUID> cellConnections = this.spatialCells.get(new CellKey(levelKey, x, y, z));
                    if (cellConnections != null) {
                        connectionIds.addAll(cellConnections);
                    }
                }
            }
        }
        return connectionIds;
    }

    private void replaceRuntimeConnection(RuntimePipeConnection runtime) {
        this.removeRuntimeConnection(runtime.connection().id());
        this.runtimeConnectionsById.put(runtime.connection().id(), runtime);
        Set<CellKey> cells = cellsFor(runtime.connection().levelKey(), runtime.bounds());
        this.cellsByConnection.put(runtime.connection().id(), cells);
        for (CellKey cell : cells) {
            this.spatialCells.computeIfAbsent(cell, ignored -> new HashSet<>()).add(runtime.connection().id());
        }
    }

    private void removeRuntimeConnection(UUID connectionId) {
        this.runtimeConnectionsById.remove(connectionId);
        Set<CellKey> cells = this.cellsByConnection.remove(connectionId);
        if (cells == null) {
            return;
        }
        for (CellKey cell : cells) {
            Set<UUID> connectionIds = this.spatialCells.get(cell);
            if (connectionIds == null) {
                continue;
            }
            connectionIds.remove(connectionId);
            if (connectionIds.isEmpty()) {
                this.spatialCells.remove(cell);
            }
        }
    }

    private void addNodeProjection(PipeNode node) {
        this.addNodeSpatial(node);
        if (node.isOrdinaryAnchor()) {
            this.ordinaryAnchors.add(node.id());
            return;
        }

        node.branchNode().ifPresent(branchNode -> {
            this.ordinaryAnchors.remove(node.id());
            this.branchNodesById.put(branchNode.id(), branchNode);
            branchNode.managedConnectionIdsInOrder().forEach(connectionId -> this.branchNodeByManagedConnectionId.put(connectionId, branchNode.id()));
        });
        node.foldAnchorNode().ifPresent(foldAnchor -> {
            this.ordinaryAnchors.remove(node.id());
            this.foldAnchorsById.put(node.id(), foldAnchor);
        });
    }

    private void removeNodeProjection(PipeNode node) {
        this.removeNodeSpatial(node.id());
        if (node.isOrdinaryAnchor()) {
            this.ordinaryAnchors.remove(node.id());
        }

        node.branchNode().ifPresent(branchNode -> {
            this.branchNodesById.remove(branchNode.id());
            branchNode.managedConnectionIdsInOrder().forEach(this.branchNodeByManagedConnectionId::remove);
        });
        node.foldAnchorNode().ifPresent(ignored -> this.foldAnchorsById.remove(node.id()));
    }

    private void addNodeSpatial(PipeNode node) {
        PipeAnchorId anchorId = node.id();
        CellKey cell = nodeCell(anchorId);
        this.cellByNode.put(anchorId, cell);
        this.nodeSpatialCells.computeIfAbsent(cell, ignored -> new HashSet<>()).add(anchorId);
    }

    private void removeNodeSpatial(PipeAnchorId anchorId) {
        CellKey cell = this.cellByNode.remove(anchorId);
        if (cell == null) {
            return;
        }
        Set<PipeAnchorId> nodes = this.nodeSpatialCells.get(cell);
        if (nodes == null) {
            return;
        }
        nodes.remove(anchorId);
        if (nodes.isEmpty()) {
            this.nodeSpatialCells.remove(cell);
        }
    }

    private static CellKey nodeCell(PipeAnchorId anchorId) {
        return new CellKey(anchorId.levelKey(), cell(anchorId.blockPos().getX()), cell(anchorId.blockPos().getY()), cell(anchorId.blockPos().getZ()));
    }

    private static double centerDistanceSqr(PipeAnchorId anchorId, Vec3 position) {
        double dx = anchorId.blockPos().getX() + 0.5D - position.x;
        double dy = anchorId.blockPos().getY() + 0.5D - position.y;
        double dz = anchorId.blockPos().getZ() + 0.5D - position.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static Set<CellKey> cellsFor(ResourceKey<Level> levelKey, AABB bounds) {
        int minX = cell(bounds.minX);
        int minY = cell(bounds.minY);
        int minZ = cell(bounds.minZ);
        int maxX = cell(bounds.maxX);
        int maxY = cell(bounds.maxY);
        int maxZ = cell(bounds.maxZ);
        Set<CellKey> cells = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cells.add(new CellKey(levelKey, x, y, z));
                }
            }
        }
        return cells;
    }

    private static AABB roughBounds(PipeConnection connection) {
        Vec3 from = connection.fromSurface();
        Vec3 to = connection.toSurface();
        double minX = Math.min(from.x, to.x);
        double minY = Math.min(from.y, to.y);
        double minZ = Math.min(from.z, to.z);
        double maxX = Math.max(from.x, to.x);
        double maxY = Math.max(from.y, to.y);
        double maxZ = Math.max(from.z, to.z);
        for (Vec3 point : connection.curveSpec().controlPoints()) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private void removeAnchorReference(PipeAnchorId anchorId, UUID connectionId) {
        Set<UUID> connectionIds = this.connectionsByAnchor.get(anchorId);
        if (connectionIds == null) {
            return;
        }
        connectionIds.remove(connectionId);
        if (connectionIds.isEmpty()) {
            this.connectionsByAnchor.remove(anchorId);
        }
    }

    private static int cell(double coordinate) {
        return (int) Math.floor(coordinate / CELL_SIZE);
    }

    private record CellKey(ResourceKey<Level> levelKey, int x, int y, int z) {}
}
