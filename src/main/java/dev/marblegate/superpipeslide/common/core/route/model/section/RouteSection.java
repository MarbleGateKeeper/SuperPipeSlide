package dev.marblegate.superpipeslide.common.core.route.model.section;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.marblegate.superpipeslide.common.core.route.model.decision.RouteBranchDecision;
import dev.marblegate.superpipeslide.common.core.route.model.decision.RouteConnectionStepDecision;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record RouteSection(
        UUID id,
        UUID routeLayoutId,
        UUID fromPlatformStopId,
        UUID toPlatformStopId,
        RouteSectionStatus forwardStatus,
        RouteSectionStatus reverseStatus,
        long computedGraphRevision,
        double forwardLength,
        double reverseLength,
        List<RouteBranchDecision> forwardBranchDecisions,
        List<RouteBranchDecision> reverseBranchDecisions,
        List<RouteConnectionStepDecision> forwardStepDecisions,
        List<RouteConnectionStepDecision> reverseStepDecisions) {

    private static final int MAX_BRANCH_DECISIONS = 512;
    private static final int MAX_STEP_DECISIONS = 2048;
    private static final StreamCodec<RegistryFriendlyByteBuf, List<RouteBranchDecision>> BRANCH_DECISION_LIST_STREAM_CODEC = RouteBranchDecision.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_BRANCH_DECISIONS));
    private static final StreamCodec<RegistryFriendlyByteBuf, List<RouteConnectionStepDecision>> STEP_DECISION_LIST_STREAM_CODEC = RouteConnectionStepDecision.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STEP_DECISIONS));

    public static final Codec<RouteSection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(RouteSection::id),
            UUIDUtil.STRING_CODEC.fieldOf("route_layout_id").forGetter(RouteSection::routeLayoutId),
            UUIDUtil.STRING_CODEC.fieldOf("from_platform_stop_id").forGetter(RouteSection::fromPlatformStopId),
            UUIDUtil.STRING_CODEC.fieldOf("to_platform_stop_id").forGetter(RouteSection::toPlatformStopId),
            RouteSectionStatus.CODEC.optionalFieldOf("forward_status", RouteSectionStatus.STALE).forGetter(RouteSection::forwardStatus),
            RouteSectionStatus.CODEC.optionalFieldOf("reverse_status", RouteSectionStatus.STALE).forGetter(RouteSection::reverseStatus),
            Codec.LONG.optionalFieldOf("computed_graph_revision", -1L).forGetter(RouteSection::computedGraphRevision),
            Codec.DOUBLE.optionalFieldOf("forward_length", 0.0D).forGetter(RouteSection::forwardLength),
            Codec.DOUBLE.optionalFieldOf("reverse_length", 0.0D).forGetter(RouteSection::reverseLength),
            RouteBranchDecision.CODEC.listOf().optionalFieldOf("forward_branch_decisions", List.of()).forGetter(RouteSection::forwardBranchDecisions),
            RouteBranchDecision.CODEC.listOf().optionalFieldOf("reverse_branch_decisions", List.of()).forGetter(RouteSection::reverseBranchDecisions),
            RouteConnectionStepDecision.CODEC.listOf().optionalFieldOf("forward_step_decisions", List.of()).forGetter(RouteSection::forwardStepDecisions),
            RouteConnectionStepDecision.CODEC.listOf().optionalFieldOf("reverse_step_decisions", List.of()).forGetter(RouteSection::reverseStepDecisions)).apply(instance, RouteSection::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RouteSection> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RouteSection decode(RegistryFriendlyByteBuf buffer) {
            return new RouteSection(
                    UUIDUtil.STREAM_CODEC.decode(buffer),
                    UUIDUtil.STREAM_CODEC.decode(buffer),
                    UUIDUtil.STREAM_CODEC.decode(buffer),
                    UUIDUtil.STREAM_CODEC.decode(buffer),
                    RouteSectionStatus.STREAM_CODEC.decode(buffer),
                    RouteSectionStatus.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.VAR_LONG.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    BRANCH_DECISION_LIST_STREAM_CODEC.decode(buffer),
                    BRANCH_DECISION_LIST_STREAM_CODEC.decode(buffer),
                    STEP_DECISION_LIST_STREAM_CODEC.decode(buffer),
                    STEP_DECISION_LIST_STREAM_CODEC.decode(buffer));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, RouteSection section) {
            UUIDUtil.STREAM_CODEC.encode(buffer, section.id());
            UUIDUtil.STREAM_CODEC.encode(buffer, section.routeLayoutId());
            UUIDUtil.STREAM_CODEC.encode(buffer, section.fromPlatformStopId());
            UUIDUtil.STREAM_CODEC.encode(buffer, section.toPlatformStopId());
            RouteSectionStatus.STREAM_CODEC.encode(buffer, section.forwardStatus());
            RouteSectionStatus.STREAM_CODEC.encode(buffer, section.reverseStatus());
            ByteBufCodecs.VAR_LONG.encode(buffer, section.computedGraphRevision());
            ByteBufCodecs.DOUBLE.encode(buffer, section.forwardLength());
            ByteBufCodecs.DOUBLE.encode(buffer, section.reverseLength());
            BRANCH_DECISION_LIST_STREAM_CODEC.encode(buffer, section.forwardBranchDecisions());
            BRANCH_DECISION_LIST_STREAM_CODEC.encode(buffer, section.reverseBranchDecisions());
            STEP_DECISION_LIST_STREAM_CODEC.encode(buffer, section.forwardStepDecisions());
            STEP_DECISION_LIST_STREAM_CODEC.encode(buffer, section.reverseStepDecisions());
        }
    };
    public RouteSection {
        forwardLength = Math.max(0.0D, forwardLength);
        reverseLength = Math.max(0.0D, reverseLength);
        forwardBranchDecisions = List.copyOf(forwardBranchDecisions);
        reverseBranchDecisions = List.copyOf(reverseBranchDecisions);
        forwardStepDecisions = List.copyOf(forwardStepDecisions);
        reverseStepDecisions = List.copyOf(reverseStepDecisions);
    }

    public RouteSectionStatus status() {
        return worse(this.forwardStatus, this.reverseStatus);
    }

    public RouteSectionStatus statusForDirection(int direction) {
        return direction < 0 ? this.reverseStatus : this.forwardStatus;
    }

    public RouteSectionStatus statusForLayout(RouteLayout layout) {
        return layout.bidirectional() ? this.status() : this.forwardStatus;
    }

    public double length() {
        return this.forwardLength;
    }

    public double lengthForDirection(int direction) {
        return direction < 0 ? this.reverseLength : this.forwardLength;
    }

    public List<RouteBranchDecision> branchDecisionsForDirection(int direction) {
        return direction < 0 ? this.reverseBranchDecisions : this.forwardBranchDecisions;
    }

    public List<RouteConnectionStepDecision> stepDecisionsForDirection(int direction) {
        return direction < 0 ? this.reverseStepDecisions : this.forwardStepDecisions;
    }

    public int direction() {
        return 1;
    }

    private static RouteSectionStatus worse(RouteSectionStatus first, RouteSectionStatus second) {
        return severity(first) >= severity(second) ? first : second;
    }

    private static int severity(RouteSectionStatus status) {
        return switch (status) {
            case VALID -> 0;
            case DISABLED -> 1;
            case STALE -> 1;
            case INCOMPLETE -> 2;
            case AMBIGUOUS -> 3;
            case BROKEN -> 4;
        };
    }
}
