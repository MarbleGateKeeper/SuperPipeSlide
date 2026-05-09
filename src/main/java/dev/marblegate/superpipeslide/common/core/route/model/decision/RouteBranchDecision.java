package dev.marblegate.superpipeslide.common.core.route.model.decision;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record RouteBranchDecision(UUID branchNodeId, PipeConnectionRef incomingConnection, PipeConnectionRef selectedConnection, int routeDirection) {
    public static final Codec<RouteBranchDecision> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("branch_node_id").forGetter(RouteBranchDecision::branchNodeId),
            PipeConnectionRef.CODEC.fieldOf("incoming_connection").forGetter(RouteBranchDecision::incomingConnection),
            PipeConnectionRef.CODEC.fieldOf("selected_connection").forGetter(RouteBranchDecision::selectedConnection),
            Codec.INT.optionalFieldOf("route_direction", 1).forGetter(RouteBranchDecision::routeDirection)
    ).apply(instance, RouteBranchDecision::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RouteBranchDecision> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            RouteBranchDecision::branchNodeId,
            PipeConnectionRef.STREAM_CODEC,
            RouteBranchDecision::incomingConnection,
            PipeConnectionRef.STREAM_CODEC,
            RouteBranchDecision::selectedConnection,
            ByteBufCodecs.VAR_INT,
            RouteBranchDecision::routeDirection,
            RouteBranchDecision::new
    );

    public RouteBranchDecision {
        routeDirection = routeDirection < 0 ? -1 : 1;
    }

    public UUID incomingConnectionId() {
        return this.incomingConnection.connectionId();
    }

    public UUID selectedConnectionId() {
        return this.selectedConnection.connectionId();
    }
}

