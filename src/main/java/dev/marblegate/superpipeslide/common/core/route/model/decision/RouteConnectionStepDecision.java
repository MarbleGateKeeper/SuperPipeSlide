package dev.marblegate.superpipeslide.common.core.route.model.decision;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record RouteConnectionStepDecision(UUID branchNodeId, PipeConnectionRef incomingConnection, PipeConnectionRef selectedConnection, int routeDirection) {

    public static final Codec<RouteConnectionStepDecision> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("branch_node_id").forGetter(RouteConnectionStepDecision::branchNodeId),
            PipeConnectionRef.CODEC.fieldOf("incoming_connection").forGetter(RouteConnectionStepDecision::incomingConnection),
            PipeConnectionRef.CODEC.fieldOf("selected_connection").forGetter(RouteConnectionStepDecision::selectedConnection),
            Codec.INT.optionalFieldOf("route_direction", 1).forGetter(RouteConnectionStepDecision::routeDirection)).apply(instance, RouteConnectionStepDecision::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RouteConnectionStepDecision> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            RouteConnectionStepDecision::branchNodeId,
            PipeConnectionRef.STREAM_CODEC,
            RouteConnectionStepDecision::incomingConnection,
            PipeConnectionRef.STREAM_CODEC,
            RouteConnectionStepDecision::selectedConnection,
            ByteBufCodecs.VAR_INT,
            RouteConnectionStepDecision::routeDirection,
            RouteConnectionStepDecision::new);
    public RouteConnectionStepDecision {
        routeDirection = routeDirection < 0 ? -1 : 1;
    }

    public UUID incomingConnectionId() {
        return this.incomingConnection.connectionId();
    }

    public UUID selectedConnectionId() {
        return this.selectedConnection.connectionId();
    }
}
