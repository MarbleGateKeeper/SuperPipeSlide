package dev.marblegate.superpipeslide.common.core.networkgraph.branch;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.networkgraph.model.PipeNodeData;
import dev.marblegate.superpipeslide.common.core.networkgraph.model.PipeNodeType;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record BranchNode(
        UUID id,
        ResourceKey<Level> levelKey,
        Vec3 position,
        Optional<PipeAnchorId> optionalAnchorId,
        Optional<UUID> defaultConnectionId,
        List<BranchConnectionSlot> connections) implements PipeNodeData {

    public static final int MAX_CONNECTIONS = 32;

    public static final MapCodec<BranchNode> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(BranchNode::id),
            Level.RESOURCE_KEY_CODEC.fieldOf("level").forGetter(BranchNode::levelKey),
            Vec3.CODEC.fieldOf("position").forGetter(BranchNode::position),
            PipeAnchorId.CODEC.optionalFieldOf("anchor_id").forGetter(BranchNode::optionalAnchorId),
            UUIDUtil.STRING_CODEC.optionalFieldOf("default_connection_id").forGetter(BranchNode::defaultConnectionId),
            BranchConnectionSlot.CODEC.listOf().optionalFieldOf("connections", List.of()).forGetter(BranchNode::connections)).apply(instance, BranchNode::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BranchNode> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            BranchNode::id,
            ResourceKey.streamCodec(Registries.DIMENSION).cast(),
            BranchNode::levelKey,
            Vec3.STREAM_CODEC.cast(),
            BranchNode::position,
            ByteBufCodecs.optional(PipeAnchorId.STREAM_CODEC).cast(),
            BranchNode::optionalAnchorId,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).cast(),
            BranchNode::defaultConnectionId,
            BranchConnectionSlot.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CONNECTIONS)).cast(),
            BranchNode::connections,
            BranchNode::new);
    public BranchNode {
        validateFinite(position, "position");
        optionalAnchorId.ifPresent(anchor -> {
            if (!anchor.levelKey().equals(levelKey)) {
                throw new IllegalArgumentException("Branch node anchor must be in the same dimension");
            }
        });
        connections = List.copyOf(connections);
        if (connections.size() > MAX_CONNECTIONS) {
            throw new IllegalArgumentException("Branch node has too many managed connections");
        }

        Set<UUID> slotIds = new HashSet<>();
        Set<UUID> connectionIds = new HashSet<>();
        for (BranchConnectionSlot connection : connections) {
            if (!slotIds.add(connection.id())) {
                throw new IllegalArgumentException("Branch connection slot ids must be unique");
            }
            if (!connectionIds.add(connection.connectionId())) {
                throw new IllegalArgumentException("Branch managed connections must be unique");
            }
        }
        defaultConnectionId.ifPresent(defaultId -> {
            if (!connectionIds.contains(defaultId)) {
                throw new IllegalArgumentException("Branch default connection must be managed by the branch node");
            }
        });
    }

    public PipeAnchorId anchorId() {
        return this.optionalAnchorId.orElseGet(() -> new PipeAnchorId(this.levelKey, net.minecraft.core.BlockPos.containing(this.position)));
    }

    public List<UUID> managedConnectionIdsInOrder() {
        return this.connections.stream().map(BranchConnectionSlot::connectionId).toList();
    }

    public int managedConnectionCount() {
        return this.connections.size();
    }

    public boolean isCompleteForChoice() {
        return this.managedConnectionCount() >= 3;
    }

    public boolean referencesConnection(UUID connectionId) {
        return this.managedConnectionIdsInOrder().contains(connectionId);
    }

    @Override
    public PipeNodeType type() {
        return PipeNodeType.BRANCH;
    }

    public Optional<UUID> defaultAlternativeTo(UUID currentConnectionId) {
        return this.defaultConnectionId
                .filter(connectionId -> !connectionId.equals(currentConnectionId))
                .or(() -> this.firstAlternativeTo(currentConnectionId));
    }

    public Optional<UUID> firstAlternativeTo(UUID currentConnectionId) {
        return this.managedConnectionIdsInOrder().stream()
                .filter(connectionId -> !connectionId.equals(currentConnectionId))
                .findFirst();
    }

    public BranchNode withConnections(List<BranchConnectionSlot> updatedConnections) {
        Optional<UUID> updatedDefault = this.defaultConnectionId.filter(connectionId -> updatedConnections.stream().anyMatch(slot -> slot.connectionId().equals(connectionId)));
        if (updatedDefault.isEmpty() && !updatedConnections.isEmpty()) {
            updatedDefault = Optional.of(updatedConnections.get(0).connectionId());
        }
        return new BranchNode(this.id, this.levelKey, this.position, this.optionalAnchorId, updatedDefault, updatedConnections);
    }

    private static void validateFinite(Vec3 vector, String name) {
        if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)) {
            throw new IllegalArgumentException("Branch node " + name + " must be finite");
        }
    }
}
