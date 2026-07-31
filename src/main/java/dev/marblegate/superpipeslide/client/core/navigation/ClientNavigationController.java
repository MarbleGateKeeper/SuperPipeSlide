package dev.marblegate.superpipeslide.client.core.navigation;

import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteHudSnapshot;
import dev.marblegate.superpipeslide.client.core.route.RouteCandidate;
import dev.marblegate.superpipeslide.client.core.slide.ClientSlideController;
import dev.marblegate.superpipeslide.client.core.slide.ClientSlideNoticeController;
import dev.marblegate.superpipeslide.client.core.slide.ClientSlideState;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionPath;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.core.route.service.RouteLayoutNavigator;
import dev.marblegate.superpipeslide.network.slide.ClientboundSlideNoticePayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ClientNavigationController {
    private static final double APPROACHING_ANNOUNCE_RANGE = 32.0D;
    private static final double BOARDING_NEAR_RANGE = 18.0D;
    static final double BOARDING_LOCAL_RANGE = 24.0D;
    private static final double BOARDING_HARD_RANGE = 8.0D;
    private static final double DESTINATION_ARRIVAL_RANGE = BOARDING_HARD_RANGE;
    private static final double EARLY_TRANSFER_PATH_RANGE = 36.0D;
    private static final double EARLY_TRANSFER_WORLD_RANGE = 22.0D;
    private static final long RANGE_EXIT_MESSAGE_COOLDOWN_TICKS = 20L * 9L;
    private static final long WRONG_BOARDING_MESSAGE_COOLDOWN_TICKS = 20L * 3L;
    private static final long GENERIC_ARRIVAL_SUPPRESSION_TICKS = 80L;
    private static final long ARRIVAL_HUD_DISMISS_MILLIS = 4500L;
    private static final long ROUTE_FAILED_HUD_DISMISS_MILLIS = 7000L;
    private static final int RIDING_LIVENESS_GRACE_TICKS = 20;
    @Nullable
    private static NavigationSession session;
    private static long lastWrongBoardingMessageTick = Long.MIN_VALUE;
    private static long lastBoardingRouteUnavailableMessageTick = Long.MIN_VALUE;
    @Nullable
    private static UUID suppressedArrivalPlatformStopId;
    private static long suppressGenericArrivalUntilTick = Long.MIN_VALUE;

    private ClientNavigationController() {}

    public static void clear() {
        session = null;
        ClientSlideController.clearRouteHudNavigationStopRetention();
        NavigationPlanner.clearCache();
        lastWrongBoardingMessageTick = Long.MIN_VALUE;
        lastBoardingRouteUnavailableMessageTick = Long.MIN_VALUE;
        suppressedArrivalPlatformStopId = null;
        suppressGenericArrivalUntilTick = Long.MIN_VALUE;
    }

    public static Optional<NavigationSessionSnapshot> sessionSnapshot() {
        return session == null ? Optional.empty() : Optional.of(session.snapshot());
    }

    public static Optional<NavigationSessionSnapshot> activeSessionSnapshot() {
        return sessionSnapshot().filter(NavigationSessionSnapshot::active);
    }

    public static boolean isNavigating() {
        return session != null && session.phase != NavigationPhase.ARRIVED && session.phase != NavigationPhase.ROUTE_FAILED;
    }

    public static boolean isRidingNavigation() {
        return session != null && session.isRiding();
    }

    public static Optional<NavigationPlan> previewPlan(LocalPlayer player, UUID destinationStationGroupId) {
        return NavigationPlanner.buildPlan(player, destinationStationGroupId, null);
    }

    public static Optional<NavigationPlan> startNavigation(LocalPlayer player, UUID destinationStationGroupId) {
        ClientSlideController.clearRouteHudNavigationStopRetention();
        Optional<NavigationPlan> plan = NavigationPlanner.buildPlan(player, destinationStationGroupId, null);
        if (plan.isEmpty()) {
            sendNotice(ClientboundSlideNoticePayload.Kind.WARNING, List.of(0xFFFFB13B),
                    Component.translatable("navigation.superpipeslide.failed"),
                    List.of(line(Component.translatable("navigation.superpipeslide.failed.body"))));
            return Optional.empty();
        }
        NavigationPlan navigationPlan = plan.get();
        if (navigationPlan.walkOnly()) {
            if (isPhysicallyAtDestination(player, destinationStationGroupId)) {
                session = new NavigationSession(navigationPlan, NavigationPhase.ARRIVED);
                session.completedAtMs = System.currentTimeMillis();
                sendNotice(ClientboundSlideNoticePayload.Kind.ARRIVAL, List.of(0xFFFFD35A),
                        Component.translatable("navigation.superpipeslide.already_arrived", stationName(navigationPlan.destinationStationGroupId())),
                        List.of());
                return plan;
            }
            session = new NavigationSession(navigationPlan, NavigationPhase.FINAL_WALK_APPROACH);
            session.walkStartDistance = navigationPlan.initialWalkDistance();
            sendNotice(ClientboundSlideNoticePayload.Kind.STANDARD, navigationPlan.primaryColors(),
                    Component.translatable("navigation.superpipeslide.walk_started", stationName(navigationPlan.destinationStationGroupId())),
                    List.of(line(Component.translatable("navigation.superpipeslide.walk_started.body", stationName(navigationPlan.destinationStationGroupId())))));
            return plan;
        }
        if (navigationPlan.segments().isEmpty()) {
            if (!isPhysicallyAtDestination(player, navigationPlan.destinationStationGroupId())) {
                sendNotice(ClientboundSlideNoticePayload.Kind.WARNING, List.of(0xFFFFB13B),
                        Component.translatable("navigation.superpipeslide.failed"),
                        List.of(line(Component.translatable("navigation.superpipeslide.failed.body"))));
                return Optional.empty();
            }
            session = new NavigationSession(navigationPlan, NavigationPhase.ARRIVED);
            session.completedAtMs = System.currentTimeMillis();
            sendNotice(ClientboundSlideNoticePayload.Kind.ARRIVAL, List.of(0xFFFFD35A),
                    Component.translatable("navigation.superpipeslide.already_arrived", stationName(navigationPlan.destinationStationGroupId())),
                    List.of());
            return plan;
        }
        session = new NavigationSession(navigationPlan, NavigationPhase.WALK_TO_BOARDING_STATION);
        session.walkStartDistance = navigationPlan.initialWalkDistance();
        sendNotice(ClientboundSlideNoticePayload.Kind.STANDARD, navigationPlan.primaryColors(),
                Component.translatable("navigation.superpipeslide.started", stationName(navigationPlan.destinationStationGroupId())),
                List.of(line(Component.translatable("navigation.superpipeslide.started.body", stationName(navigationPlan.startStationGroupId())))));
        return plan;
    }

    public static void cancelNavigation() {
        if (session == null) {
            return;
        }
        ClientSlideController.clearRouteHudNavigationStopRetention();
        sendNotice(ClientboundSlideNoticePayload.Kind.STANDARD, List.of(0xFF8FA9B8),
                Component.translatable("navigation.superpipeslide.cancelled"),
                List.of());
        session = null;
    }

    public static Optional<ClientRouteHudSnapshot.NavigationStopContext> routeHudStopContext(UUID platformStopId) {
        if (session == null) {
            return Optional.empty();
        }
        if (session.isRiding() && !session.plan.segments().isEmpty()) {
            Optional<ClientRouteHudSnapshot.NavigationStopContext> live = routeHudStopContextForSegment(session.currentSegment(), platformStopId);
            if (live.isPresent()) {
                return live;
            }
        }
        if (session.lastRouteHudStopContext == null) {
            return Optional.empty();
        }
        if (session.lastRouteHudStopContext.platformStopId().equals(platformStopId)) {
            return Optional.of(session.lastRouteHudStopContext);
        }
        Optional<UUID> stationGroupId = stationGroupIdForPlatformStop(platformStopId);
        if (stationGroupId.isPresent() && stationGroupId.get().equals(session.lastRouteHudStopContext.stationGroupId())) {
            ClientRouteHudSnapshot.NavigationStopContext context = session.lastRouteHudStopContext;
            return Optional.of(new ClientRouteHudSnapshot.NavigationStopContext(
                    context.kind(),
                    platformStopId,
                    context.stationGroupId(),
                    context.colors()));
        }
        return Optional.empty();
    }

    public static List<DestinationSearchResult> searchDestinations(LocalPlayer player, String query, int limit) {
        return NavigationPlanner.searchDestinations(player, query, limit);
    }

    public static void tick(Minecraft minecraft, LocalPlayer player) {
        if (session == null) {
            return;
        }
        if (handleDimensionChange(player)) {
            return;
        }
        boolean routeDataStale = ClientRouteDataCache.revision() != session.plan.routeRevision()
                || ClientPipeNetworkCache.aggregateRevision() != session.plan.pipeRevision();
        // Postpone rebuilds while the pipe data the sections were computed against is
        // still on its way: planning on a partially synced pipe cache under-prices
        // missing sections and flips plans in dense areas.
        if (routeDataStale && !session.isRiding() && !ClientRouteDataCache.isWaitingForPipeRevision(ClientPipeNetworkCache.aggregateRevision())) {
            // Mid-ride rebuilds stay deferred: the stale condition persists until the
            // player detaches, and a later tick takes this same branch once they are no
            // longer riding, so no separate after-ride flag is needed.
            rebuildCurrentRoute(player);
        }
        if (session == null || session.phase == NavigationPhase.ROUTE_FAILED || session.phase == NavigationPhase.ARRIVED) {
            if (session != null && shouldDismissTerminalHud()) {
                session = null;
            }
            return;
        }
        if (session.isRiding()) {
            if (!reconcileRidingLiveness(player)) {
                updateRidingApproach(player);
            }
            return;
        }
        updateBoardingProximity(player);
    }

    /**
     * Tracks the player's dimension across ticks. A dimension change is only expected
     * while walking a cross-dimension transfer whose target level is the dimension the
     * player just entered; any other change means the player left the planned route
     * (e.g. walked through a portal mid-navigation), so the session is restarted from
     * the player's position in the new dimension. Returns true when the tick must stop.
     */
    private static boolean handleDimensionChange(LocalPlayer player) {
        ResourceKey<Level> current = player.level().dimension();
        ResourceKey<Level> previous = session.lastSeenDimension;
        session.lastSeenDimension = current;
        if (previous == null || previous.equals(current)) {
            return false;
        }
        if (session.phase == NavigationPhase.ARRIVED || session.phase == NavigationPhase.ROUTE_FAILED) {
            return false;
        }
        if (session.phase == NavigationPhase.TRANSFER_WALK || session.phase == NavigationPhase.TRANSFER_PROXIMITY) {
            Optional<TransferInstruction> transfer = previousSegmentTransferInstruction();
            if (transfer.isPresent() && transfer.get().toLevelKey().equals(current)) {
                return false;
            }
        }
        restartFromCurrentPosition(player);
        return true;
    }

    /**
     * Soft-lock reconciliation: some detach paths (death, flight, spectator mode,
     * mounting, certain fold teleports) drop the slide session without an
     * onSlideDetached callback, which would strand navigation in a riding phase
     * forever - canBoard() stays false and the ride can never complete. When no
     * slide or pipe transfer has been active for a short grace
     * period while the session still believes it is riding, the ride is treated as
     * lost and navigation restarts from the player's current position. Returns true
     * when a restart was triggered.
     */
    private static boolean reconcileRidingLiveness(LocalPlayer player) {
        if (ClientSlideController.isSlidingOrTransferring()) {
            session.ridingLivenessGraceTicks = 0;
            return false;
        }
        if (++session.ridingLivenessGraceTicks < RIDING_LIVENESS_GRACE_TICKS) {
            return false;
        }
        session.ridingLivenessGraceTicks = 0;
        restartFromCurrentPosition(player);
        return true;
    }

    private static boolean shouldDismissTerminalHud() {
        if (session == null || session.completedAtMs <= 0L || ClientSlideController.isSliding()) {
            return false;
        }
        long dismissMillis = session.phase == NavigationPhase.ROUTE_FAILED
                ? ROUTE_FAILED_HUD_DISMISS_MILLIS
                : ARRIVAL_HUD_DISMISS_MILLIS;
        return System.currentTimeMillis() - session.completedAtMs > dismissMillis;
    }

    /**
     * Capture gate consulted by the slide controller. The navigation capture lock
     * was downgraded: every connection stays capturable while navigating, off-plan
     * captures only surface the wrong-boarding overlay hint
     * (see {@link #isPlannedBoardingConnection}), and the session re-plans from the
     * player's position once the slide ends (see {@link #onSlideDetached}).
     */
    public static boolean canCaptureConnection(PipeConnection connection) {
        return true;
    }

    /**
     * Whether the connection belongs to the platform stop the current plan expects
     * the player to board next. Off-plan connections are still capturable; this
     * only drives the wrong-boarding overlay hint. Returns true when there is no
     * active plan to contradict (including the guidance-only final walk phase).
     */
    public static boolean isPlannedBoardingConnection(PipeConnection connection) {
        if (!isNavigating()) {
            return true;
        }
        if (session != null && session.phase == NavigationPhase.FINAL_WALK_APPROACH) {
            return true;
        }
        Optional<UUID> target = currentBoardingPlatformStopId();
        return target.isPresent() && connection.platformStopId().filter(target.get()::equals).isPresent();
    }

    public static void notifyWrongBoardingTarget(LocalPlayer player) {
        long gameTime = player.level().getGameTime();
        if (gameTime - lastWrongBoardingMessageTick < WRONG_BOARDING_MESSAGE_COOLDOWN_TICKS) {
            return;
        }
        lastWrongBoardingMessageTick = gameTime;
        overlayMessage(Component.translatable("navigation.superpipeslide.wrong_boarding"));
    }

    public static void notifyBoardingRouteUnavailable(LocalPlayer player) {
        long gameTime = player.level().getGameTime();
        if (gameTime - lastBoardingRouteUnavailableMessageTick < WRONG_BOARDING_MESSAGE_COOLDOWN_TICKS) {
            return;
        }
        lastBoardingRouteUnavailableMessageTick = gameTime;
        overlayMessage(Component.translatable("navigation.superpipeslide.boarding_route_unavailable"));
    }

    public static Optional<RouteCandidate> boardingCandidate(PlatformStop platformStop) {
        if (session == null || !session.canBoard(platformStop.id())) {
            return Optional.empty();
        }
        NavigationSegment segment = session.currentSegment();
        Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(segment.layoutId());
        if (layout.isEmpty()) {
            return Optional.empty();
        }
        return RouteLayoutNavigator.nextStep(layout.get(), platformStop.id(), segment.routeDirection(), ClientRouteDataCache::routeSection)
                .filter(step -> step.section().statusForDirection(segment.routeDirection()) == RouteSectionStatus.VALID)
                .map(step -> new RouteCandidate(segment.layoutId(), segment.routeDirection(), platformStop.id(), step.nextPlatformStopId(), step.section().id()));
    }

    public static void onRouteBoarded(PlatformStop platformStop, RouteCandidate candidate) {
        if (session == null || !session.canBoard(platformStop.id())) {
            return;
        }
        NavigationSegment segment = session.currentSegment();
        if (!segment.layoutId().equals(candidate.layoutId()) || segment.routeDirection() != candidate.routeDirection()) {
            return;
        }
        session.phase = NavigationPhase.RIDING_SEGMENT;
        session.enteredCurrentBoardingRange = false;
        session.ridingLivenessGraceTicks = 0;
        session.lastRouteHudStopContext = null;
        session.pendingPlannedDismountConnectionId = null;
        sendNotice(ClientboundSlideNoticePayload.Kind.ENTER_ROUTE, segment.colors(),
                Component.translatable("navigation.superpipeslide.boarded", segment.lineName()),
                List.of(line(Component.translatable("navigation.superpipeslide.boarded.body", stationName(segment.alightingPlatformStopId())))));
    }

    public static StationNavigationAction stationAction(UUID platformStopId) {
        if (session == null || !session.isRiding()) {
            return StationNavigationAction.NORMAL;
        }
        NavigationSegment segment = session.currentSegment();
        if (segment.alightingPlatformStopId().equals(platformStopId)) {
            return segment.finalSegment() ? StationNavigationAction.FINAL_DESTINATION : StationNavigationAction.TRANSFER_STOP;
        }
        return segment.stationSequence().contains(platformStopId) ? StationNavigationAction.PASS_THROUGH : StationNavigationAction.NORMAL;
    }

    public static boolean shouldSuppressGenericArrivalNotice(UUID platformStopId) {
        if (session != null && session.isRiding()) {
            NavigationSegment segment = session.currentSegment();
            if (segment.alightingPlatformStopId().equals(platformStopId) && segment.finalSegment()) {
                return true;
            }
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        long gameTime = player == null ? Long.MIN_VALUE : player.level().getGameTime();
        return suppressedArrivalPlatformStopId != null
                && suppressedArrivalPlatformStopId.equals(platformStopId)
                && gameTime <= suppressGenericArrivalUntilTick;
    }

    public static boolean shouldSlowForPlatformStop(UUID platformStopId) {
        StationNavigationAction action = stationAction(platformStopId);
        return action != StationNavigationAction.PASS_THROUGH;
    }

    public static void onSegmentStopReached(UUID platformStopId) {
        if (session == null || !session.isRiding()) {
            return;
        }
        NavigationSegment segment = session.currentSegment();
        if (!segment.alightingPlatformStopId().equals(platformStopId)) {
            return;
        }
        if (segment.finalWalkInstruction().isPresent()) {
            session.pendingPlannedDismountConnectionId = dismountConnectionId(platformStopId);
            completeFinalWalkSegment(segment, false);
            return;
        }
        if (segment.finalSegment()) {
            completeDestinationSegment(segment);
            return;
        }
        rememberRouteHudStopContext(segment, platformStopId);
        session.pendingPlannedDismountConnectionId = dismountConnectionId(platformStopId);
        session.phase = NavigationPhase.TRANSFER_WALK;
        session.segmentIndex++;
        session.enteredCurrentBoardingRange = false;
        session.walkStartDistance = Double.NaN;
        NavigationSegment next = session.currentSegment();
        segment.transferInstruction().ifPresentOrElse(
                instruction -> sendTransferNotice(instruction, next, false),
                () -> sendTransferNotice(TransferInstruction.sameStationFallback(segment, next), next, false));
    }

    /**
     * Connection id of the platform the player is expected to dismount at after a
     * station-entry checkpoint, or null when it cannot be resolved (which disables
     * the planned-dismount fast path for this stop).
     */
    @Nullable
    private static UUID dismountConnectionId(UUID platformStopId) {
        return ClientRouteDataCache.platformStop(platformStopId)
                .map(stop -> stop.connectionRef().connectionId())
                .orElse(null);
    }

    /**
     * Whether the connection a slide ended on belongs to the current segment's
     * section paths. In dense clusters the player can be sliding on a parallel pipe
     * of a neighbouring station whose platform projection is well within the early
     * transfer range; without this check any detach there is mistaken for a planned
     * early transfer. When section paths are missing from the client cache the check
     * cannot prove anything and defers to the proximity heuristic (returns true).
     */
    private static boolean detachOnSegmentPath(NavigationSegment segment, PipeConnection connection) {
        Set<UUID> pathConnectionIds = null;
        for (NavigationSectionRef sectionRef : segment.routeSections()) {
            Optional<RouteSectionPath> path = ClientRouteDataCache.routeSectionPath(sectionRef.routeSectionId());
            if (path.isEmpty()) {
                continue;
            }
            if (pathConnectionIds == null) {
                pathConnectionIds = new HashSet<>();
            }
            List<PipeConnectionRef> refs = segment.routeDirection() < 0 ? path.get().reverseConnections() : path.get().forwardConnections();
            for (PipeConnectionRef ref : refs) {
                pathConnectionIds.add(ref.connectionId());
            }
        }
        return pathConnectionIds == null || pathConnectionIds.contains(connection.id());
    }

    public static void onSlideDetached(LocalPlayer player, ClientSlideState state, PipeConnection connection, DetachReason reason) {
        if (session == null) {
            return;
        }
        if (!session.isRiding()) {
            UUID plannedDismount = session.pendingPlannedDismountConnectionId;
            session.pendingPlannedDismountConnectionId = null;
            if (plannedDismount != null && plannedDismount.equals(connection.id())) {
                // Planned dismount at a transfer or final-walk stop: the plan already
                // advanced past this segment at the station-entry checkpoint, so keep
                // guiding the current leg instead of re-planning from scratch.
                return;
            }
            // Capture lock downgrade: off-plan pipe captures are allowed, so a slide
            // can end far from the planned boarding platform. Re-plan from wherever
            // the player ended up instead of dragging guidance back to the old start.
            if (session.phase != NavigationPhase.ARRIVED && session.phase != NavigationPhase.ROUTE_FAILED) {
                restartFromCurrentPosition(player);
            }
            return;
        }
        NavigationSegment segment = session.currentSegment();
        if (!detachOnSegmentPath(segment, connection)) {
            // The slide ended on a pipe outside the current segment's section paths:
            // off-plan deviation, not an early transfer.
            overlayMessage(Component.translatable("navigation.superpipeslide.detached_continue"));
            restartFromCurrentPosition(player);
            return;
        }
        if (segment.finalWalkInstruction().isPresent() && nearSegmentAlighting(player, state, connection, segment)) {
            completeFinalWalkSegment(segment, true);
            return;
        }
        if (!segment.transferAfter() || !nearSegmentAlighting(player, state, connection, segment)) {
            overlayMessage(Component.translatable("navigation.superpipeslide.detached_continue"));
            restartFromCurrentPosition(player);
            return;
        }
        rememberRouteHudStopContext(segment, segment.alightingPlatformStopId());
        session.phase = NavigationPhase.TRANSFER_WALK;
        session.segmentIndex++;
        session.enteredCurrentBoardingRange = false;
        session.walkStartDistance = Double.NaN;
        NavigationSegment next = session.currentSegment();
        segment.transferInstruction().ifPresentOrElse(
                instruction -> sendTransferNotice(instruction, next, true),
                () -> sendTransferNotice(TransferInstruction.sameStationFallback(segment, next), next, true));
    }

    private static void restartFromCurrentPosition(LocalPlayer player) {
        if (session == null) {
            return;
        }
        UUID destination = session.plan.destinationStationGroupId();
        Optional<NavigationPlan> rebuilt = NavigationPlanner.buildPlan(player, destination, session.plan);
        if (rebuilt.isPresent()) {
            Optional<NavigationPlan> continued = NavigationPlanner.continueIncumbent(player, session.plan, session.segmentIndex, rebuilt.get().estimatedTicks());
            if (continued.isPresent()) {
                // The remaining tail of the current plan is still rideable and not
                // clearly worse than the rebuilt alternative: keep the session's
                // progress and quietly adopt the revision-refreshed incumbent.
                session.plan = continued.get();
                return;
            }
        }
        if (rebuilt.isEmpty()) {
            session.phase = NavigationPhase.ROUTE_FAILED;
            session.completedAtMs = System.currentTimeMillis();
            sendNotice(ClientboundSlideNoticePayload.Kind.WARNING, List.of(0xFFFF5E4D),
                    Component.translatable("navigation.superpipeslide.failed"),
                    List.of(line(Component.translatable("navigation.superpipeslide.failed.body"))));
            return;
        }
        NavigationPlan plan = rebuilt.get();
        if (plan.walkOnly()) {
            session = new NavigationSession(plan, NavigationPhase.FINAL_WALK_APPROACH);
            session.walkStartDistance = plan.initialWalkDistance();
            return;
        }
        if (plan.segments().isEmpty()) {
            if (!isPhysicallyAtDestination(player, plan.destinationStationGroupId())) {
                session.phase = NavigationPhase.ROUTE_FAILED;
                session.completedAtMs = System.currentTimeMillis();
                sendNotice(ClientboundSlideNoticePayload.Kind.WARNING, List.of(0xFFFF5E4D),
                        Component.translatable("navigation.superpipeslide.failed"),
                        List.of(line(Component.translatable("navigation.superpipeslide.failed.body"))));
                return;
            }
            session = new NavigationSession(plan, NavigationPhase.ARRIVED);
            session.completedAtMs = System.currentTimeMillis();
            sendNotice(ClientboundSlideNoticePayload.Kind.ARRIVAL, plan.primaryColors(),
                    Component.translatable("navigation.superpipeslide.arrived", stationName(plan.destinationStationGroupId())),
                    List.of(line(Component.translatable("navigation.superpipeslide.arrived.body"))));
            return;
        }
        session = new NavigationSession(plan, NavigationPhase.WALK_TO_BOARDING_STATION);
        session.walkStartDistance = plan.initialWalkDistance();
    }

    public static Optional<NavigationHudSnapshot> hudSnapshot(LocalPlayer player) {
        if (session == null) {
            return Optional.empty();
        }
        if (session.phase == NavigationPhase.ROUTE_FAILED) {
            return Optional.of(new NavigationHudSnapshot(
                    session.phase,
                    stationName(session.plan.destinationStationGroupId()).getString(),
                    Component.translatable("navigation.superpipeslide.hud.failed").getString(),
                    "",
                    0.0D,
                    0,
                    List.of(0xFFFF5E4D),
                    Optional.empty()));
        }
        if (session.phase == NavigationPhase.ARRIVED) {
            return Optional.of(new NavigationHudSnapshot(
                    session.phase,
                    stationName(session.plan.destinationStationGroupId()).getString(),
                    Component.translatable("navigation.superpipeslide.hud.arrived").getString(),
                    "",
                    1.0D,
                    0,
                    session.plan.primaryColors(),
                    Optional.empty()));
        }
        if (session.plan.walkOnly()) {
            // Walk-only sessions have no segments, so they are rendered here instead
            // of through the segment-based layout below.
            Optional<TargetInfo> walkTarget = currentWorldTarget(player);
            String walkAction = Component.translatable("navigation.superpipeslide.hud.final_walk", stationName(session.plan.destinationStationGroupId()).getString()).getString();
            int walkRemainingTicks = (int) Math.round(walkTarget.map(target -> target.distance() * NavigationPlanner.WALK_TICKS_PER_BLOCK).orElse(0.0D));
            String walkDetail = Component.translatable("navigation.superpipeslide.hud.detail", 0, secondsText(walkRemainingTicks)).getString();
            return Optional.of(new NavigationHudSnapshot(
                    session.phase,
                    stationName(session.plan.destinationStationGroupId()).getString(),
                    walkAction,
                    walkDetail,
                    walkingPhaseProgress(player),
                    1,
                    session.plan.primaryColors(),
                    walkTarget));
        }
        NavigationSegment segment = session.currentSegment();
        Optional<TargetInfo> target = currentWorldTarget(player);
        String action = switch (session.phase) {
            case WALK_TO_BOARDING_STATION -> Component.translatable("navigation.superpipeslide.hud.walk_to_board", stationName(segment.boardingPlatformStopId()).getString()).getString();
            case BOARDING_PROXIMITY -> Component.translatable("navigation.superpipeslide.hud.board_now", stationName(segment.boardingPlatformStopId()).getString()).getString();
            case TRANSFER_WALK -> transferHudText(segment, false);
            case TRANSFER_PROXIMITY -> transferHudText(segment, true);
            case APPROACHING_TRANSFER -> approachingTransferHudText(segment);
            case APPROACHING_DESTINATION -> Component.translatable("navigation.superpipeslide.hud.approaching_destination", stationName(session.plan.destinationStationGroupId()).getString()).getString();
            case RIDING_SEGMENT -> ridingHudText(segment);
            case FINAL_WALK_APPROACH -> Component.translatable("navigation.superpipeslide.hud.final_walk", stationName(session.plan.destinationStationGroupId()).getString()).getString();
            default -> "";
        };
        int completedTransfers = Math.max(0, session.segmentIndex);
        int remainingTransfers = Math.max(0, session.plan.transferCount() - completedTransfers);
        double progress = navigationHudProgress(player, segment);
        int remainingTicks = (int) Math.round(remainingEstimatedTicks(player, segment));
        String detail = Component.translatable("navigation.superpipeslide.hud.detail", remainingTransfers, secondsText(remainingTicks)).getString();
        return Optional.of(new NavigationHudSnapshot(
                session.phase,
                stationName(session.plan.destinationStationGroupId()).getString(),
                action,
                detail,
                progress,
                session.segmentIndex + 1,
                segment.colors(),
                target));
    }

    private static double navigationHudProgress(LocalPlayer player, NavigationSegment segment) {
        if (session == null) {
            return 0.0D;
        }
        int segmentCount = session.plan.segments().size();
        if (segmentCount <= 0) {
            return 1.0D;
        }
        double segmentProgress = session.isRiding() ? ridingSegmentProgress(segment) : walkingPhaseProgress(player);
        return Mth.clamp((session.segmentIndex + segmentProgress) / segmentCount, 0.0D, 1.0D);
    }

    /**
     * Walking-phase completion of the current leg: 1 minus the ratio between the
     * remaining distance to the current world target and the distance recorded when
     * the walk started. The first leg reuses the plan's initialWalkDistance; later
     * legs (transfers, final walk) record the first observed distance instead.
     */
    private static double walkingPhaseProgress(LocalPlayer player) {
        if (session == null) {
            return 0.0D;
        }
        Optional<TargetInfo> target = currentWorldTarget(player);
        if (target.isEmpty()) {
            return 0.0D;
        }
        if (Double.isNaN(session.walkStartDistance) || session.walkStartDistance <= 1.0E-3D) {
            session.walkStartDistance = target.get().distance();
            return 0.0D;
        }
        return Mth.clamp(1.0D - target.get().distance() / session.walkStartDistance, 0.0D, 1.0D);
    }

    /**
     * Remaining route time estimate in ticks: untouched segments at full cost plus
     * the current segment scaled by how much of it is left, so the HUD countdown
     * shrinks as the player progresses instead of always showing the plan total.
     */
    private static double remainingEstimatedTicks(LocalPlayer player, NavigationSegment segment) {
        if (session == null) {
            return 0.0D;
        }
        List<NavigationSegment> segments = session.plan.segments();
        double remaining = 0.0D;
        for (int i = session.segmentIndex + 1; i < segments.size(); i++) {
            remaining += segments.get(i).estimatedTicks();
        }
        if (session.phase == NavigationPhase.FINAL_WALK_APPROACH) {
            // All rides are over; only the on-foot leg to the destination remains.
            return remaining + currentWorldTarget(player).map(target -> target.distance() * NavigationPlanner.WALK_TICKS_PER_BLOCK).orElse(0.0D);
        }
        if (session.isRiding()) {
            return remaining + segment.estimatedTicks() * (1.0D - ridingSegmentProgress(segment));
        }
        // Walking phases: the current ride has not started, so the full segment
        // remains, plus the remaining walk to its boarding platform.
        remaining += segment.estimatedTicks();
        Optional<TargetInfo> target = currentWorldTarget(player);
        if (target.isPresent()) {
            remaining += target.get().distance() * NavigationPlanner.WALK_TICKS_PER_BLOCK;
        }
        return remaining;
    }

    private static double ridingSegmentProgress(NavigationSegment segment) {
        if (session == null) {
            return 0.0D;
        }
        Double computed = computeRidingSegmentProgress(segment);
        if (computed == null) {
            // The slide HUD snapshot is temporarily unavailable or mid-handoff: keep
            // the last valid progress of this segment instead of jumping to a
            // synthetic constant.
            return session.lastRidingProgressSegmentIndex == session.segmentIndex ? session.lastRidingSegmentProgress : 0.0D;
        }
        session.lastRidingSegmentProgress = computed;
        session.lastRidingProgressSegmentIndex = session.segmentIndex;
        return computed;
    }

    @Nullable
    private static Double computeRidingSegmentProgress(NavigationSegment segment) {
        Optional<ClientRouteHudSnapshot> snapshot = ClientSlideController.routeHudSnapshot();
        if (snapshot.isEmpty()) {
            return null;
        }
        ClientRouteHudSnapshot hud = snapshot.get();
        if (!segment.layoutId().equals(hud.routeLayoutId()) || segment.routeDirection() != hud.routeDirection()) {
            return null;
        }
        List<UUID> sequence = segment.stationSequence();
        int targetIndex = lastIndexOf(sequence, segment.alightingPlatformStopId());
        if (targetIndex <= 0) {
            return null;
        }
        int currentIndex = nearestTravelIndex(sequence, hud.currentPlatformStopId(), targetIndex);
        if (currentIndex < 0) {
            return null;
        }
        double progress = (currentIndex + hud.sectionProgress()) / targetIndex;
        if (hud.currentPlatformStopId().equals(segment.alightingPlatformStopId())) {
            progress = 1.0D;
        }
        return Mth.clamp(progress, 0.0D, 1.0D);
    }

    private static int nearestTravelIndex(List<UUID> sequence, UUID platformStopId, int targetIndex) {
        int best = -1;
        for (int i = 0; i < sequence.size() && i <= targetIndex; i++) {
            if (sequence.get(i).equals(platformStopId)) {
                best = i;
            }
        }
        return best;
    }

    private static int lastIndexOf(List<UUID> sequence, UUID platformStopId) {
        for (int i = sequence.size() - 1; i >= 0; i--) {
            if (sequence.get(i).equals(platformStopId)) {
                return i;
            }
        }
        return -1;
    }

    public static Optional<WorldTarget> worldTarget(LocalPlayer player) {
        return currentWorldTarget(player).map(target -> new WorldTarget(
                target.position().add(0.0D, 1.05D, 0.0D),
                target.name(),
                target.colors().isEmpty() ? 0xFF47A6FF : target.colors().getFirst(),
                target.distance(),
                target.kind()));
    }

    private static void rebuildCurrentRoute(LocalPlayer player) {
        if (session == null) {
            return;
        }
        UUID destination = session.plan.destinationStationGroupId();
        Optional<NavigationPlan> rebuilt = NavigationPlanner.buildPlan(player, destination, session.plan);
        if (rebuilt.isPresent()) {
            NavigationPlan plan = rebuilt.get();
            Optional<NavigationPlan> continued = NavigationPlanner.continueIncumbent(player, session.plan, session.segmentIndex, plan.estimatedTicks());
            if (continued.isPresent()) {
                // The data revision bump did not break the plan's remaining tail:
                // keep the session's progress and quietly adopt the refreshed plan
                // instead of resetting and notifying.
                session.plan = continued.get();
                return;
            }
            if (plan.walkOnly()) {
                session = new NavigationSession(plan, NavigationPhase.FINAL_WALK_APPROACH);
                session.walkStartDistance = plan.initialWalkDistance();
                sendNotice(ClientboundSlideNoticePayload.Kind.STANDARD, plan.primaryColors(),
                        Component.translatable("navigation.superpipeslide.route_updated"),
                        List.of(line(Component.translatable("navigation.superpipeslide.route_updated.body"))));
                return;
            }
            if (plan.segments().isEmpty()) {
                if (isPhysicallyAtDestination(player, plan.destinationStationGroupId())) {
                    session = new NavigationSession(plan, NavigationPhase.ARRIVED);
                    session.completedAtMs = System.currentTimeMillis();
                    sendNotice(ClientboundSlideNoticePayload.Kind.ARRIVAL, plan.primaryColors(),
                            Component.translatable("navigation.superpipeslide.arrived", stationName(plan.destinationStationGroupId())),
                            List.of(line(Component.translatable("navigation.superpipeslide.arrived.body"))));
                    return;
                }
                failRouteInvalidated();
                return;
            }
            session = new NavigationSession(plan, NavigationPhase.WALK_TO_BOARDING_STATION);
            session.walkStartDistance = plan.initialWalkDistance();
            sendNotice(ClientboundSlideNoticePayload.Kind.STANDARD, plan.primaryColors(),
                    Component.translatable("navigation.superpipeslide.route_updated"),
                    List.of(line(Component.translatable("navigation.superpipeslide.route_updated.body"))));
            return;
        }
        failRouteInvalidated();
    }

    /**
     * Fails the session after an automatic route rebuild. These rebuild failures used
     * to be silent because the notification was gated on a user-requested flag that
     * was never set, so a warning notice is now sent unconditionally.
     */
    private static void failRouteInvalidated() {
        session.phase = NavigationPhase.ROUTE_FAILED;
        session.completedAtMs = System.currentTimeMillis();
        sendNotice(ClientboundSlideNoticePayload.Kind.WARNING, List.of(0xFFFF5E4D),
                Component.translatable("navigation.superpipeslide.route_invalidated"),
                List.of());
    }

    private static void updateBoardingProximity(LocalPlayer player) {
        // Reaching the destination station on foot completes navigation from any
        // walking phase; stationArrivalDistance already filters other dimensions out.
        if (stationArrivalDistance(player, session.plan.destinationStationGroupId()) <= DESTINATION_ARRIVAL_RANGE) {
            completeArrivalOnFoot();
            return;
        }
        if (session.phase == NavigationPhase.FINAL_WALK_APPROACH) {
            // Guidance-only phase: the world target points at the destination station
            // and no boarding proximity is tracked.
            return;
        }
        Optional<TargetInfo> target = currentWorldTarget(player);
        if (target.isEmpty()) {
            return;
        }
        boolean near = target.get().distance() <= BOARDING_NEAR_RANGE;
        boolean hardNear = target.get().distance() <= BOARDING_HARD_RANGE;
        NavigationPhase targetPhase = session.phase == NavigationPhase.TRANSFER_WALK || session.phase == NavigationPhase.TRANSFER_PROXIMITY
                ? (hardNear ? NavigationPhase.TRANSFER_PROXIMITY : NavigationPhase.TRANSFER_WALK)
                : (hardNear ? NavigationPhase.BOARDING_PROXIMITY : NavigationPhase.WALK_TO_BOARDING_STATION);
        if (near) {
            session.enteredCurrentBoardingRange = true;
        } else if (session.enteredCurrentBoardingRange) {
            maybeNotifyLeftBoardingRange(player);
            session.enteredCurrentBoardingRange = false;
        }
        session.phase = targetPhase;
    }

    /** Marks the session arrived and sends the standard arrival notice for an on-foot arrival. */
    private static void completeArrivalOnFoot() {
        session.phase = NavigationPhase.ARRIVED;
        session.completedAtMs = System.currentTimeMillis();
        sendNotice(ClientboundSlideNoticePayload.Kind.ARRIVAL, session.plan.primaryColors(),
                Component.translatable("navigation.superpipeslide.arrived", stationName(session.plan.destinationStationGroupId())),
                List.of(line(Component.translatable("navigation.superpipeslide.arrived.body"))));
    }

    private static void updateRidingApproach(LocalPlayer player) {
        NavigationSegment segment = session.currentSegment();
        Optional<TargetInfo> alighting = targetInfo(player, segment.alightingPlatformStopId(), segment.colors(), ridingTargetKind(segment));
        if (alighting.isEmpty()) {
            return;
        }
        if (alighting.get().distance() <= APPROACHING_ANNOUNCE_RANGE) {
            session.phase = segment.finalWalkInstruction().isPresent()
                    ? NavigationPhase.APPROACHING_TRANSFER
                    : (segment.finalSegment() ? NavigationPhase.APPROACHING_DESTINATION : NavigationPhase.APPROACHING_TRANSFER);
        } else {
            session.phase = NavigationPhase.RIDING_SEGMENT;
        }
    }

    private static void maybeNotifyLeftBoardingRange(LocalPlayer player) {
        long gameTime = player.level().getGameTime();
        if (gameTime - session.lastRangeExitMessageTick < RANGE_EXIT_MESSAGE_COOLDOWN_TICKS) {
            return;
        }
        session.lastRangeExitMessageTick = gameTime;
        overlayMessage(Component.translatable("navigation.superpipeslide.left_boarding_range"));
    }

    private static Optional<UUID> currentBoardingPlatformStopId() {
        if (session == null || session.phase == NavigationPhase.ARRIVED || session.phase == NavigationPhase.ROUTE_FAILED || session.plan.segments().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(session.currentSegment().boardingPlatformStopId());
    }

    private static Optional<TargetInfo> currentWorldTarget(LocalPlayer player) {
        if (session == null || session.phase == NavigationPhase.ARRIVED || session.phase == NavigationPhase.ROUTE_FAILED) {
            return Optional.empty();
        }
        // Checked before the empty-segment bail-out: walk-only plans stay in this
        // phase for their whole lifetime and have no segments at all.
        if (session.phase == NavigationPhase.FINAL_WALK_APPROACH) {
            return finalWalkDestinationTarget(player);
        }
        if (session.plan.segments().isEmpty()) {
            return Optional.empty();
        }
        NavigationSegment segment = session.currentSegment();
        if (session.isRiding()) {
            return targetInfo(player, segment.alightingPlatformStopId(), segment.colors(), ridingTargetKind(segment));
        }
        TargetKind kind = TargetKind.BOARDING;
        if (session.phase == NavigationPhase.TRANSFER_WALK || session.phase == NavigationPhase.TRANSFER_PROXIMITY) {
            Optional<TransferInstruction> transfer = previousSegmentTransferInstruction();
            if (transfer.isPresent() && !transfer.get().toLevelKey().equals(player.level().dimension())) {
                return Optional.empty();
            }
            kind = transfer.map(instruction -> targetKindForTransfer(instruction.kind())).orElse(TargetKind.SAME_STATION_TRANSFER);
        }
        return targetInfo(player, segment.boardingPlatformStopId(), segment.colors(), kind);
    }

    /**
     * World target for the on-foot final leg: the destination station's center. Empty
     * when the destination is missing or in another dimension, so no marker is drawn.
     */
    private static Optional<TargetInfo> finalWalkDestinationTarget(LocalPlayer player) {
        UUID destinationStationGroupId = session.plan.destinationStationGroupId();
        Optional<StationGroup> station = ClientRouteDataCache.stationGroup(destinationStationGroupId);
        if (station.isEmpty() || !station.get().levelKey().equals(player.level().dimension())) {
            return Optional.empty();
        }
        Vec3 position = Vec3.atCenterOf(station.get().stationBlockPos());
        return Optional.of(new TargetInfo(
                destinationStationGroupId,
                stationName(destinationStationGroupId).getString(),
                position,
                position.distanceTo(player.position()),
                session.plan.primaryColors(),
                TargetKind.FINAL_WALK));
    }

    private static Optional<TargetInfo> targetInfo(LocalPlayer player, UUID platformStopId, List<Integer> colors, TargetKind kind) {
        Optional<PlatformStop> platformStop = ClientRouteDataCache.platformStop(platformStopId);
        if (platformStop.isEmpty()) {
            return Optional.empty();
        }
        // Never target a platform stop in another dimension: its coordinates would
        // render as a ghost marker inside the player's current world.
        Optional<StationGroup> station = ClientRouteDataCache.stationGroup(platformStop.get().stationGroupId());
        if (station.isEmpty() || !station.get().levelKey().equals(player.level().dimension())) {
            return Optional.empty();
        }
        Vec3 position = NavigationPlanner.platformTargetPosition(platformStop.get(), player.position());
        return Optional.of(new TargetInfo(
                platformStopId,
                stationName(platformStopId).getString(),
                position,
                position.distanceTo(player.position()),
                colors,
                kind));
    }

    private static boolean nearSegmentAlighting(LocalPlayer player, ClientSlideState state, PipeConnection connection, NavigationSegment segment) {
        Optional<TargetInfo> target = targetInfo(player, segment.alightingPlatformStopId(), segment.colors(), ridingTargetKind(segment));
        if (target.isPresent() && target.get().distance() <= EARLY_TRANSFER_WORLD_RANGE) {
            return true;
        }
        double remaining = state.direction() >= 0
                ? Math.max(0.0D, connection.length() - state.distanceOnConnection())
                : Math.max(0.0D, state.distanceOnConnection());
        return remaining <= EARLY_TRANSFER_PATH_RANGE && target.map(value -> value.distance() <= EARLY_TRANSFER_WORLD_RANGE * 1.6D).orElse(false);
    }

    static boolean isPhysicallyAtDestination(LocalPlayer player, UUID destinationStationGroupId) {
        return stationArrivalDistance(player, destinationStationGroupId) <= DESTINATION_ARRIVAL_RANGE;
    }

    private static double stationArrivalDistance(LocalPlayer player, UUID stationGroupId) {
        Vec3 playerPosition = player.position();
        ResourceKey<Level> level = player.level().dimension();
        return ClientRouteDataCache.stationGroup(stationGroupId)
                .filter(station -> station.levelKey().equals(level))
                .map(station -> {
                    double best = Vec3.atCenterOf(station.stationBlockPos()).distanceTo(playerPosition);
                    for (PlatformStop stop : ClientRouteDataCache.platformStopsInStation(stationGroupId)) {
                        best = Math.min(best, NavigationPlanner.platformPosition(stop).distanceTo(playerPosition));
                    }
                    return best;
                })
                .orElse(Double.MAX_VALUE / 4.0D);
    }

    private static Component stationName(UUID platformStopOrStationGroupId) {
        Optional<PlatformStop> platformStop = ClientRouteDataCache.platformStop(platformStopOrStationGroupId);
        if (platformStop.isPresent()) {
            return ClientRouteDataCache.stationGroup(platformStop.get().stationGroupId())
                    .map(group -> Component.literal(group.primaryName()))
                    .orElse(Component.translatable("screen.superpipeslide.station.missing"));
        }
        return ClientRouteDataCache.stationGroup(platformStopOrStationGroupId)
                .map(group -> Component.literal(group.primaryName()))
                .orElse(Component.translatable("screen.superpipeslide.station.missing"));
    }

    private static String secondsText(int ticks) {
        int seconds = Math.max(0, Math.round(ticks / 20.0F));
        int minutes = seconds / 60;
        int remain = seconds % 60;
        return minutes > 0 ? minutes + ":" + String.format(Locale.ROOT, "%02d", remain) : remain + "s";
    }

    private static void sendNotice(ClientboundSlideNoticePayload.Kind kind, List<Integer> colors, Component title, List<ClientboundSlideNoticePayload.NoticeLine> lines) {
        ClientSlideNoticeController.handleNotice(new ClientboundSlideNoticePayload(kind, colors, title, lines));
    }

    private static void overlayMessage(Component message) {
        Minecraft.getInstance().gui.setOverlayMessage(message, false);
    }

    private static ClientboundSlideNoticePayload.NoticeLine line(Component text) {
        return new ClientboundSlideNoticePayload.NoticeLine(text, List.of(), false);
    }

    private static void rememberRouteHudStopContext(NavigationSegment segment, UUID platformStopId) {
        if (session == null) {
            return;
        }
        Optional<ClientRouteHudSnapshot.NavigationStopContext> context = routeHudStopContextForSegment(segment, platformStopId);
        if (context.isEmpty()) {
            return;
        }
        session.lastRouteHudStopContext = context.get();
    }

    private static Optional<ClientRouteHudSnapshot.NavigationStopContext> routeHudStopContextForSegment(NavigationSegment segment, UUID platformStopId) {
        if (!segment.alightingPlatformStopId().equals(platformStopId)) {
            return Optional.empty();
        }
        ClientRouteHudSnapshot.NavigationStopKind kind;
        if (segment.finalWalkInstruction().isPresent()) {
            kind = segment.finalWalkInstruction().get().kind() == TransferKind.CROSS_DIMENSION_OUT_OF_STATION
                    ? ClientRouteHudSnapshot.NavigationStopKind.CROSS_DIMENSION_FINAL_WALK
                    : ClientRouteHudSnapshot.NavigationStopKind.FINAL_WALK;
        } else if (segment.finalSegment()) {
            kind = ClientRouteHudSnapshot.NavigationStopKind.DESTINATION;
        } else {
            TransferKind transferKind = segment.transferInstruction().map(TransferInstruction::kind).orElse(TransferKind.SAME_STATION);
            kind = switch (transferKind) {
                case SAME_STATION -> ClientRouteHudSnapshot.NavigationStopKind.SAME_STATION_TRANSFER;
                case OUT_OF_STATION -> ClientRouteHudSnapshot.NavigationStopKind.OUT_OF_STATION_TRANSFER;
                case CROSS_DIMENSION_OUT_OF_STATION -> ClientRouteHudSnapshot.NavigationStopKind.CROSS_DIMENSION_TRANSFER;
            };
        }
        UUID stationGroupId = stationGroupIdForPlatformStop(platformStopId).orElse(platformStopId);
        return Optional.of(new ClientRouteHudSnapshot.NavigationStopContext(kind, platformStopId, stationGroupId, segment.colors()));
    }

    private static Optional<UUID> stationGroupIdForPlatformStop(UUID platformStopId) {
        return ClientRouteDataCache.platformStop(platformStopId).map(PlatformStop::stationGroupId);
    }

    private static void completeDestinationSegment(NavigationSegment segment) {
        if (session == null) {
            return;
        }
        suppressGenericArrivalNotice(segment.alightingPlatformStopId());
        rememberRouteHudStopContext(segment, segment.alightingPlatformStopId());
        session.phase = NavigationPhase.ARRIVED;
        session.completedAtMs = System.currentTimeMillis();
        sendNotice(ClientboundSlideNoticePayload.Kind.ARRIVAL, segment.colors(),
                Component.translatable("navigation.superpipeslide.arrived", stationName(session.plan.destinationStationGroupId())),
                List.of(line(Component.translatable("navigation.superpipeslide.arrived.body"))));
    }

    private static void completeFinalWalkSegment(NavigationSegment segment, boolean early) {
        if (session == null || segment.finalWalkInstruction().isEmpty()) {
            return;
        }
        suppressGenericArrivalNotice(segment.alightingPlatformStopId());
        FinalWalkInstruction instruction = segment.finalWalkInstruction().get();
        rememberRouteHudStopContext(segment, segment.alightingPlatformStopId());
        boolean crossDimension = instruction.kind() == TransferKind.CROSS_DIMENSION_OUT_OF_STATION;
        session.walkStartDistance = Double.NaN;
        if (crossDimension) {
            // The destination is in another dimension and cannot be tracked from the
            // alighting world, so the session completes immediately (as before).
            session.phase = NavigationPhase.ARRIVED;
            session.completedAtMs = System.currentTimeMillis();
        } else {
            // Same-dimension final walk: keep guiding the player to the destination
            // station; updateBoardingProximity promotes the session to ARRIVED once
            // the player comes within arrival range.
            session.phase = NavigationPhase.FINAL_WALK_APPROACH;
        }
        ArrayList<ClientboundSlideNoticePayload.NoticeLine> lines = new ArrayList<>();
        if (early) {
            lines.add(line(Component.translatable("navigation.superpipeslide.early_transfer.body")));
        }
        lines.add(line(Component.translatable(crossDimension
                ? "navigation.superpipeslide.final_cross_dimension_walk.body"
                : "navigation.superpipeslide.final_walk.body",
                stationName(session.plan.destinationStationGroupId()))));
        sendNotice(ClientboundSlideNoticePayload.Kind.ARRIVAL, segment.colors(),
                Component.translatable(crossDimension
                        ? "navigation.superpipeslide.final_cross_dimension_walk"
                        : "navigation.superpipeslide.final_walk",
                        stationName(session.plan.destinationStationGroupId())),
                lines);
    }

    private static void sendTransferNotice(TransferInstruction instruction, NavigationSegment next, boolean early) {
        String titleKey = switch (instruction.kind()) {
            case SAME_STATION -> "navigation.superpipeslide.transfer.same_station";
            case OUT_OF_STATION -> "navigation.superpipeslide.transfer.out_of_station";
            case CROSS_DIMENSION_OUT_OF_STATION -> "navigation.superpipeslide.transfer.cross_dimension";
        };
        String bodyKey = switch (instruction.kind()) {
            case SAME_STATION -> "navigation.superpipeslide.transfer.same_station.body";
            case OUT_OF_STATION -> "navigation.superpipeslide.transfer.out_of_station.body";
            case CROSS_DIMENSION_OUT_OF_STATION -> "navigation.superpipeslide.transfer.cross_dimension.body";
        };
        ArrayList<ClientboundSlideNoticePayload.NoticeLine> lines = new ArrayList<>();
        if (early) {
            lines.add(line(Component.translatable("navigation.superpipeslide.early_transfer.body")));
        }
        lines.add(line(Component.translatable(bodyKey, next.lineName())));
        Component title = early
                ? Component.translatable("navigation.superpipeslide.early_transfer")
                : Component.translatable(titleKey, stationName(next.boardingPlatformStopId()));
        // Routine same-station / out-of-station transfers are demoted to STANDARD;
        // only cross-dimension transfers and early-alighting prompts keep WARNING.
        sendNotice(early || instruction.kind() == TransferKind.CROSS_DIMENSION_OUT_OF_STATION ? ClientboundSlideNoticePayload.Kind.WARNING : ClientboundSlideNoticePayload.Kind.STANDARD, next.colors(),
                title,
                lines);
    }

    private static String transferHudText(NavigationSegment segment, boolean boarding) {
        Optional<TransferInstruction> transfer = previousSegmentTransferInstruction();
        if (transfer.isEmpty()) {
            return Component.translatable(boarding ? "navigation.superpipeslide.hud.transfer_board" : "navigation.superpipeslide.hud.transfer_walk", stationName(segment.boardingPlatformStopId()).getString()).getString();
        }
        String key = switch (transfer.get().kind()) {
            case SAME_STATION -> boarding ? "navigation.superpipeslide.hud.transfer_same_station_board" : "navigation.superpipeslide.hud.transfer_same_station";
            case OUT_OF_STATION -> boarding ? "navigation.superpipeslide.hud.transfer_out_station_board" : "navigation.superpipeslide.hud.transfer_out_station";
            case CROSS_DIMENSION_OUT_OF_STATION -> boarding ? "navigation.superpipeslide.hud.transfer_cross_dimension_board" : "navigation.superpipeslide.hud.transfer_cross_dimension";
        };
        return Component.translatable(key, stationName(segment.boardingPlatformStopId()).getString()).getString();
    }

    private static String approachingTransferHudText(NavigationSegment segment) {
        if (segment.finalWalkInstruction().isPresent()) {
            boolean crossDimension = segment.finalWalkInstruction().get().kind() == TransferKind.CROSS_DIMENSION_OUT_OF_STATION;
            return Component.translatable(crossDimension
                    ? "navigation.superpipeslide.hud.approaching_final_cross_dimension_walk"
                    : "navigation.superpipeslide.hud.approaching_final_walk",
                    stationName(session.plan.destinationStationGroupId()).getString()).getString();
        }
        Optional<TransferInstruction> transfer = segment.transferInstruction();
        String key = transfer.map(value -> switch (value.kind()) {
            case SAME_STATION -> "navigation.superpipeslide.hud.approaching_same_station_transfer";
            case OUT_OF_STATION -> "navigation.superpipeslide.hud.approaching_out_station_transfer";
            case CROSS_DIMENSION_OUT_OF_STATION -> "navigation.superpipeslide.hud.approaching_cross_dimension_transfer";
        }).orElse("navigation.superpipeslide.hud.approaching_transfer");
        return Component.translatable(key, stationName(segment.alightingPlatformStopId()).getString()).getString();
    }

    private static String ridingHudText(NavigationSegment segment) {
        if (segment.finalWalkInstruction().isPresent()) {
            return Component.translatable("navigation.superpipeslide.hud.riding_final_walk", segment.lineName(), stationName(session.plan.destinationStationGroupId()).getString()).getString();
        }
        return Component.translatable("navigation.superpipeslide.hud.riding", segment.lineName(), stationName(segment.alightingPlatformStopId()).getString()).getString();
    }

    private static Optional<TransferInstruction> previousSegmentTransferInstruction() {
        if (session == null || session.segmentIndex <= 0 || session.segmentIndex > session.plan.segments().size()) {
            return Optional.empty();
        }
        return session.plan.segments().get(session.segmentIndex - 1).transferInstruction();
    }

    private static TargetKind ridingTargetKind(NavigationSegment segment) {
        if (segment.finalWalkInstruction().isPresent()) {
            return segment.finalWalkInstruction().get().kind() == TransferKind.CROSS_DIMENSION_OUT_OF_STATION
                    ? TargetKind.CROSS_DIMENSION_FINAL_WALK
                    : TargetKind.FINAL_WALK;
        }
        if (segment.finalSegment()) {
            return TargetKind.DESTINATION;
        }
        return segment.transferInstruction()
                .map(instruction -> targetKindForTransfer(instruction.kind()))
                .orElse(TargetKind.SAME_STATION_TRANSFER);
    }

    private static TargetKind targetKindForTransfer(TransferKind kind) {
        return switch (kind) {
            case SAME_STATION -> TargetKind.SAME_STATION_TRANSFER;
            case OUT_OF_STATION -> TargetKind.OUT_OF_STATION_TRANSFER;
            case CROSS_DIMENSION_OUT_OF_STATION -> TargetKind.CROSS_DIMENSION_TRANSFER;
        };
    }

    private static void suppressGenericArrivalNotice(UUID platformStopId) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        suppressedArrivalPlatformStopId = platformStopId;
        suppressGenericArrivalUntilTick = player == null ? Long.MAX_VALUE : player.level().getGameTime() + GENERIC_ARRIVAL_SUPPRESSION_TICKS;
    }

    public enum NavigationPhase {
        IDLE,
        WALK_TO_BOARDING_STATION,
        BOARDING_PROXIMITY,
        RIDING_SEGMENT,
        APPROACHING_TRANSFER,
        TRANSFER_WALK,
        TRANSFER_PROXIMITY,
        APPROACHING_DESTINATION,
        /** On-foot final leg after alighting: guide to the destination station until within arrival range. */
        FINAL_WALK_APPROACH,
        ARRIVED,
        ROUTE_FAILED
    }

    public enum StationNavigationAction {
        NORMAL,
        PASS_THROUGH,
        TRANSFER_STOP,
        FINAL_DESTINATION
    }

    public enum DetachReason {
        SNEAK,
        JUMP,
        OTHER
    }

    public enum TransferKind {
        SAME_STATION,
        OUT_OF_STATION,
        CROSS_DIMENSION_OUT_OF_STATION
    }

    public enum TargetKind {
        BOARDING,
        SAME_STATION_TRANSFER,
        OUT_OF_STATION_TRANSFER,
        CROSS_DIMENSION_TRANSFER,
        FINAL_WALK,
        CROSS_DIMENSION_FINAL_WALK,
        DESTINATION
    }

    public record NavigationPlan(
            UUID id,
            long routeRevision,
            long pipeRevision,
            UUID startStationGroupId,
            UUID destinationStationGroupId,
            UUID startPlatformStopId,
            List<NavigationSegment> segments,
            int estimatedTicks,
            int transferCount,
            int sameStationTransferCount,
            int outOfStationTransferCount,
            int crossDimensionTransferCount,
            boolean finalWalk,
            boolean crossDimensionFinalWalk,
            double initialWalkDistance,
            List<Integer> primaryColors,
            boolean walkOnly) {
        public NavigationPlan {
            segments = List.copyOf(segments);
            primaryColors = List.copyOf(primaryColors);
        }

        /** Copy of this plan with fresh data revisions and an updated walk-only flag source unchanged. */
        NavigationPlan withRevisions(long newRouteRevision, long newPipeRevision) {
            return new NavigationPlan(
                    this.id,
                    newRouteRevision,
                    newPipeRevision,
                    this.startStationGroupId,
                    this.destinationStationGroupId,
                    this.startPlatformStopId,
                    this.segments,
                    this.estimatedTicks,
                    this.transferCount,
                    this.sameStationTransferCount,
                    this.outOfStationTransferCount,
                    this.crossDimensionTransferCount,
                    this.finalWalk,
                    this.crossDimensionFinalWalk,
                    this.initialWalkDistance,
                    this.primaryColors,
                    this.walkOnly);
        }
    }

    public record NavigationSegment(
            int index,
            UUID routeLineId,
            UUID layoutId,
            int routeDirection,
            UUID boardingPlatformStopId,
            UUID alightingPlatformStopId,
            List<UUID> stationSequence,
            List<UUID> routeSectionIds,
            List<NavigationSectionRef> routeSections,
            Optional<TransferInstruction> transferInstruction,
            Optional<FinalWalkInstruction> finalWalkInstruction,
            boolean finalSegment,
            int estimatedTicks,
            List<Integer> colors,
            String lineName) {
        public NavigationSegment {
            routeDirection = routeDirection < 0 ? -1 : 1;
            stationSequence = List.copyOf(stationSequence);
            routeSectionIds = List.copyOf(routeSectionIds);
            routeSections = List.copyOf(routeSections);
            transferInstruction = transferInstruction == null ? Optional.empty() : transferInstruction;
            finalWalkInstruction = finalWalkInstruction == null ? Optional.empty() : finalWalkInstruction;
            colors = List.copyOf(colors);
        }

        public boolean transferAfter() {
            return this.transferInstruction.isPresent();
        }
    }

    public record NavigationSectionRef(UUID routeSectionId, int layoutIndex) {}

    public record TransferInstruction(
            TransferKind kind,
            UUID fromStationGroupId,
            UUID toStationGroupId,
            Optional<UUID> transferLinkId,
            ResourceKey<Level> fromLevelKey,
            ResourceKey<Level> toLevelKey,
            UUID nextBoardingPlatformStopId,
            String nextLineName,
            List<Integer> nextColors) {
        public TransferInstruction {
            transferLinkId = transferLinkId == null ? Optional.empty() : transferLinkId;
            nextColors = List.copyOf(nextColors);
        }

        static TransferInstruction fromEdge(NavigationPlanner.TransferEdge edge, UUID nextBoardingPlatformStopId, String nextLineName, List<Integer> nextColors) {
            return new TransferInstruction(
                    edge.transferKind(),
                    edge.fromStationGroupId(),
                    edge.toStationGroupId(),
                    edge.transferLinkId(),
                    edge.fromLevelKey(),
                    edge.toLevelKey(),
                    nextBoardingPlatformStopId,
                    nextLineName,
                    nextColors);
        }

        static TransferInstruction sameStation(StationGroup fromStation, StationGroup toStation, UUID nextBoardingPlatformStopId, String nextLineName, List<Integer> nextColors) {
            return new TransferInstruction(
                    TransferKind.SAME_STATION,
                    fromStation.id(),
                    toStation.id(),
                    Optional.empty(),
                    fromStation.levelKey(),
                    toStation.levelKey(),
                    nextBoardingPlatformStopId,
                    nextLineName,
                    nextColors);
        }

        public static TransferInstruction sameStationFallback(NavigationSegment current, NavigationSegment next) {
            Optional<StationGroup> fromStation = ClientRouteDataCache.platformStop(current.alightingPlatformStopId())
                    .flatMap(stop -> ClientRouteDataCache.stationGroup(stop.stationGroupId()));
            Optional<StationGroup> toStation = ClientRouteDataCache.platformStop(next.boardingPlatformStopId())
                    .flatMap(stop -> ClientRouteDataCache.stationGroup(stop.stationGroupId()));
            ResourceKey<Level> fallbackLevel = Minecraft.getInstance().level == null ? Level.OVERWORLD : Minecraft.getInstance().level.dimension();
            UUID fromStationId = fromStation.map(StationGroup::id).orElse(current.alightingPlatformStopId());
            UUID toStationId = toStation.map(StationGroup::id).orElse(fromStationId);
            ResourceKey<Level> fromLevel = fromStation.map(StationGroup::levelKey).orElse(fallbackLevel);
            ResourceKey<Level> toLevel = toStation.map(StationGroup::levelKey).orElse(fromLevel);
            return new TransferInstruction(
                    TransferKind.SAME_STATION,
                    fromStationId,
                    toStationId,
                    Optional.empty(),
                    fromLevel,
                    toLevel,
                    next.boardingPlatformStopId(),
                    next.lineName(),
                    next.colors());
        }
    }

    public record FinalWalkInstruction(
            TransferKind kind,
            UUID fromStationGroupId,
            UUID destinationStationGroupId,
            Optional<UUID> transferLinkId,
            ResourceKey<Level> fromLevelKey,
            ResourceKey<Level> destinationLevelKey) {
        public FinalWalkInstruction {
            transferLinkId = transferLinkId == null ? Optional.empty() : transferLinkId;
        }
    }

    public record DestinationSearchResult(
            UUID stationGroupId,
            String primaryName,
            List<String> translatedNames,
            ResourceKey<Level> levelKey,
            double distanceBlocks,
            boolean reachable,
            int matchScore) {
        public DestinationSearchResult {
            translatedNames = List.copyOf(translatedNames);
        }
    }

    public record NavigationSessionSnapshot(NavigationPhase phase, NavigationPlan plan, int segmentIndex) {
        public boolean active() {
            return this.phase != NavigationPhase.ARRIVED && this.phase != NavigationPhase.ROUTE_FAILED;
        }
    }

    public record NavigationHudSnapshot(
            NavigationPhase phase,
            String destinationName,
            String actionText,
            String detailText,
            double progress,
            int segmentNumber,
            List<Integer> colors,
            Optional<TargetInfo> target) {
        public NavigationHudSnapshot {
            colors = List.copyOf(colors);
        }
    }

    public record TargetInfo(UUID platformStopId, String name, Vec3 position, double distance, List<Integer> colors, TargetKind kind) {
        public TargetInfo {
            colors = List.copyOf(colors);
        }
    }

    public record WorldTarget(Vec3 position, String name, int color, double distance, TargetKind kind) {}

    private static final class NavigationSession {
        private NavigationPlan plan;
        private NavigationPhase phase;
        private int segmentIndex;
        private boolean enteredCurrentBoardingRange;
        private long lastRangeExitMessageTick = Long.MIN_VALUE;
        @Nullable
        private ClientRouteHudSnapshot.NavigationStopContext lastRouteHudStopContext;
        private long completedAtMs;
        /** Dimension the player was seen in on the previous tick; null until the first tick observes it. */
        @Nullable
        private ResourceKey<Level> lastSeenDimension;
        /** Ticks spent in a riding phase without any active slide or pipe transfer (soft-lock reconciliation). */
        private int ridingLivenessGraceTicks;
        /**
         * Distance to the current leg's target when the walk started: the plan's
         * initialWalkDistance for the first leg, NaN ("record the first observed
         * distance lazily") for transfer and final-walk legs.
         */
        private double walkStartDistance = Double.NaN;
        /** Last successfully computed riding progress, retained while the slide HUD snapshot is unavailable. */
        private double lastRidingSegmentProgress;
        /** Segment index lastRidingSegmentProgress belongs to; -1 when nothing was computed yet. */
        private int lastRidingProgressSegmentIndex = -1;
        /**
         * Connection id of the platform where the plan expects the player to dismount
         * next (set at the station-entry checkpoint of a transfer or final-walk stop).
         * The first detach on this connection afterwards is the planned dismount and
         * must not trigger a re-plan; null disables the fast path.
         */
        @Nullable
        private UUID pendingPlannedDismountConnectionId;

        private NavigationSession(NavigationPlan plan, NavigationPhase phase) {
            this.plan = plan;
            this.phase = phase;
        }

        private NavigationSegment currentSegment() {
            return this.plan.segments().get(Math.max(0, Math.min(this.segmentIndex, this.plan.segments().size() - 1)));
        }

        private boolean canBoard(UUID platformStopId) {
            return !this.plan.segments().isEmpty()
                    && this.segmentIndex < this.plan.segments().size()
                    && this.currentSegment().boardingPlatformStopId().equals(platformStopId)
                    && !this.isRiding();
        }

        private boolean isRiding() {
            return this.phase == NavigationPhase.RIDING_SEGMENT
                    || this.phase == NavigationPhase.APPROACHING_TRANSFER
                    || this.phase == NavigationPhase.APPROACHING_DESTINATION;
        }

        private NavigationSessionSnapshot snapshot() {
            return new NavigationSessionSnapshot(this.phase, this.plan, this.segmentIndex);
        }
    }
}
