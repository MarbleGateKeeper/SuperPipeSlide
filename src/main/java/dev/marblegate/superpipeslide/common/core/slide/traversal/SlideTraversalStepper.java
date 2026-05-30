package dev.marblegate.superpipeslide.common.core.slide.traversal;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkView;
import dev.marblegate.superpipeslide.config.Config;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.Mth;

public final class SlideTraversalStepper {
    private static final double MIN_TRAVERSAL_CONNECTION_LENGTH = 0.25D;
    private static final int ABSOLUTE_MAX_TOPOLOGY_STEPS = 96;

    private SlideTraversalStepper() {}

    public static TraversalResult advance(
            PipeNetworkView pipeNetwork,
            TraversalContext context,
            TraversalCursor start,
            double distanceBudget,
            StationCheckpointPolicy stationPolicy) {
        Optional<PipeConnection> startingConnection = pipeNetwork.connection(start.connectionId());
        if (startingConnection.isEmpty()) {
            TraversalEvent barrier = TraversalEvent.atCursor(TraversalEventType.INVALID_TOPOLOGY, start);
            return new TraversalResult(start, Math.max(0.0D, distanceBudget), List.of(barrier), Optional.of(barrier));
        }

        List<TraversalEvent> events = new ArrayList<>();
        PipeConnection current = startingConnection.get();
        int direction = start.direction();
        double distance = Mth.clamp(start.distanceOnConnection(), 0.0D, current.length());
        double remaining = Math.max(0.0D, distanceBudget);
        int maxSteps = maxTopologySteps();

        for (int steps = 0; steps < maxSteps; steps++) {
            if (current.length() < MIN_TRAVERSAL_CONNECTION_LENGTH) {
                TraversalCursor cursor = new TraversalCursor(current.id(), direction, distance);
                TraversalEvent barrier = TraversalEvent.atCursor(TraversalEventType.INVALID_TOPOLOGY, cursor);
                events.add(barrier);
                return new TraversalResult(cursor, remaining, events, Optional.of(barrier));
            }

            double distanceToEnd = direction > 0 ? current.length() - distance : distance;
            double stepDistance = Math.min(remaining, Math.max(0.0D, distanceToEnd));
            Optional<StationCheckpoint> stationCheckpoint = stationPolicy.nextStationCheckpoint(current, distance, direction, stepDistance);
            if (stationCheckpoint.isPresent()) {
                TraversalCursor cursor = new TraversalCursor(current.id(), direction, stationCheckpoint.get().distanceOnConnection());
                TraversalEvent event = TraversalEvent.atCursor(stationCheckpoint.get().eventType(), cursor);
                events.add(event);
                double consumed = Math.abs(stationCheckpoint.get().distanceOnConnection() - distance);
                return new TraversalResult(cursor, Math.max(0.0D, remaining - consumed), events, Optional.of(event));
            }
            if (current.platformStopId().isPresent() && atStationEntry(current, distance, direction)) {
                events.add(TraversalEvent.atCursor(TraversalEventType.STATION_PASS_THROUGH, new TraversalCursor(current.id(), direction, distance)));
            }

            if (remaining < distanceToEnd || distanceToEnd <= 1.0E-6D && remaining <= 1.0E-6D) {
                double nextDistance = direction > 0 ? distance + remaining : distance - remaining;
                return new TraversalResult(new TraversalCursor(current.id(), direction, Mth.clamp(nextDistance, 0.0D, current.length())), 0.0D, events, Optional.empty());
            }

            remaining = Math.max(0.0D, remaining - Math.max(0.0D, distanceToEnd));
            PipeAnchorId exitAnchor = current.anchorForDirectionEnd(direction);
            TraversalCursor exitCursor = new TraversalCursor(current.id(), direction, direction > 0 ? current.length() : 0.0D);
            ContinuationDecision continuation = TraversalContinuationResolver.resolve(pipeNetwork, context, current, direction, exitAnchor);
            if (continuation.type() != ContinuationDecision.Type.NEXT_CONNECTION) {
                TraversalEvent barrier = TraversalEvent.atAnchor(eventTypeFor(continuation.type()), exitCursor, exitAnchor);
                events.add(barrier);
                return new TraversalResult(exitCursor, remaining, events, Optional.of(barrier));
            }

            current = continuation.connection().orElseThrow();
            direction = continuation.direction();
            distance = direction > 0 ? 0.0D : current.length();
            events.add(TraversalEvent.atAnchor(TraversalEventType.CONNECTION_HANDOFF, new TraversalCursor(current.id(), direction, distance), exitAnchor));
        }

        TraversalCursor cursor = new TraversalCursor(current.id(), direction, distance);
        TraversalEvent barrier = TraversalEvent.atCursor(TraversalEventType.STEP_LIMIT_REACHED, cursor);
        events.add(barrier);
        return new TraversalResult(cursor, remaining, events, Optional.of(barrier));
    }

    private static TraversalEventType eventTypeFor(ContinuationDecision.Type type) {
        return switch (type) {
            case BRANCH_CHOICE_REQUIRED -> TraversalEventType.BRANCH_CHOICE_REQUIRED;
            case FOLD_TRANSITION_REQUIRED -> TraversalEventType.FOLD_TRANSITION_REQUIRED;
            case INVALID_TOPOLOGY -> TraversalEventType.INVALID_TOPOLOGY;
            case NEXT_CONNECTION -> TraversalEventType.CONNECTION_HANDOFF;
            case NO_CONTINUATION -> TraversalEventType.NO_CONTINUATION;
        };
    }

    private static int maxTopologySteps() {
        int derived = (int) Math.ceil(Config.MAX_STEP_DISTANCE.getAsDouble() / MIN_TRAVERSAL_CONNECTION_LENGTH) + 8;
        return Math.max(8, Math.min(ABSOLUTE_MAX_TOPOLOGY_STEPS, derived));
    }

    private static boolean atStationEntry(PipeConnection connection, double distance, int direction) {
        double entry = direction > 0 ? 0.0D : connection.length();
        return Math.abs(distance - entry) <= 1.0E-6D;
    }

    @FunctionalInterface
    public interface StationCheckpointPolicy {
        Optional<StationCheckpoint> nextStationCheckpoint(PipeConnection connection, double startDistance, int direction, double travelDistance);
    }
}
