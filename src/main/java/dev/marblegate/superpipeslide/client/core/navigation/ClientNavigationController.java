package dev.marblegate.superpipeslide.client.core.navigation;

import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteHudSnapshot;
import dev.marblegate.superpipeslide.client.core.route.RouteCandidate;
import dev.marblegate.superpipeslide.client.core.slide.ClientSlideController;
import dev.marblegate.superpipeslide.client.core.slide.ClientSlideNoticeController;
import dev.marblegate.superpipeslide.client.core.slide.ClientSlideState;
import dev.marblegate.superpipeslide.client.fullmap.model.search.SearchResult;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.SlideGeometry;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationTransferLink;
import dev.marblegate.superpipeslide.common.core.route.service.RouteLayoutNavigator;
import dev.marblegate.superpipeslide.network.slide.ClientboundSlideNoticePayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
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
    private static final double WALK_TICKS_PER_BLOCK = 8.0D;
    private static final double SAME_STATION_TRANSFER_PENALTY_TICKS = 6.0D * 20.0D;
    private static final double BOARDING_PENALTY_TICKS = 4.0D * 20.0D;
    private static final double ESTIMATED_RIDE_SPEED = 0.30D;
    private static final double BOARDING_NEAR_RANGE = 18.0D;
    private static final double BOARDING_LOCAL_RANGE = 24.0D;
    private static final double BOARDING_HARD_RANGE = 8.0D;
    private static final double EARLY_TRANSFER_PATH_RANGE = 36.0D;
    private static final double EARLY_TRANSFER_WORLD_RANGE = 22.0D;
    private static final long RANGE_EXIT_MESSAGE_COOLDOWN_TICKS = 20L * 9L;
    private static final long WRONG_BOARDING_MESSAGE_COOLDOWN_TICKS = 20L * 3L;
    private static final long GENERIC_ARRIVAL_SUPPRESSION_TICKS = 80L;
    @Nullable
    private static NavigationSession session;
    @Nullable
    private static NavigationGraph cachedGraph;
    @Nullable
    private static ReachabilityCache cachedReachability;
    private static long cachedRouteRevision = Long.MIN_VALUE;
    private static long cachedPipeRevision = Long.MIN_VALUE;
    private static long lastWrongBoardingMessageTick = Long.MIN_VALUE;
    private static long lastBoardingRouteUnavailableMessageTick = Long.MIN_VALUE;
    @Nullable
    private static UUID suppressedArrivalPlatformStopId;
    private static long suppressGenericArrivalUntilTick = Long.MIN_VALUE;

    private ClientNavigationController() {
    }

    public static void clear() {
        session = null;
        ClientSlideController.clearRouteHudNavigationStopRetention();
        cachedGraph = null;
        cachedReachability = null;
        cachedRouteRevision = Long.MIN_VALUE;
        cachedPipeRevision = Long.MIN_VALUE;
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
        return buildPlan(player, destinationStationGroupId);
    }

    public static Optional<NavigationPlan> startNavigation(LocalPlayer player, UUID destinationStationGroupId) {
        ClientSlideController.clearRouteHudNavigationStopRetention();
        Optional<NavigationPlan> plan = buildPlan(player, destinationStationGroupId);
        if (plan.isEmpty()) {
            sendNotice(ClientboundSlideNoticePayload.Kind.WARNING, List.of(0xFFFFB13B),
                    Component.translatable("navigation.superpipeslide.failed"),
                    List.of(line(Component.translatable("navigation.superpipeslide.failed.body"))));
            return Optional.empty();
        }
        NavigationPlan navigationPlan = plan.get();
        if (navigationPlan.segments().isEmpty()) {
            session = new NavigationSession(navigationPlan, NavigationPhase.ARRIVED);
            session.completedAtMs = System.currentTimeMillis();
            sendNotice(ClientboundSlideNoticePayload.Kind.ARRIVAL, List.of(0xFFFFD35A),
                    Component.translatable("navigation.superpipeslide.already_arrived", stationName(navigationPlan.destinationStationGroupId())),
                    List.of());
            return plan;
        }
        session = new NavigationSession(navigationPlan, NavigationPhase.WALK_TO_BOARDING_STATION);
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
                    context.colors()
            ));
        }
        return Optional.empty();
    }

    public static List<DestinationSearchResult> searchDestinations(LocalPlayer player, String query, int limit) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Vec3 playerPosition = player.position();
        ResourceKey<Level> playerLevel = player.level().dimension();
        Set<UUID> reachableStations = reachableStationGroups(player);
        return ClientRouteDataCache.stationGroups().stream()
                .map(station -> destinationResult(playerLevel, playerPosition, station, normalized, reachableStations.contains(station.id())))
                .filter(result -> normalized.isBlank() || result.matchScore() > 0)
                .sorted(Comparator
                        .comparingInt((DestinationSearchResult result) -> result.reachable() ? 0 : 1)
                        .thenComparing(Comparator.comparingInt(DestinationSearchResult::matchScore).reversed())
                        .thenComparingDouble(DestinationSearchResult::distanceBlocks)
                        .thenComparing(result -> result.primaryName().toLowerCase(Locale.ROOT)))
                .limit(limit)
                .toList();
    }

    public static void tick(Minecraft minecraft, LocalPlayer player) {
        if (session == null) {
            return;
        }
        boolean routeDataStale = ClientRouteDataCache.revision() != session.plan.routeRevision()
                || ClientPipeNetworkCache.aggregateRevision() != session.plan.pipeRevision();
        if (routeDataStale) {
            if (session.isRiding()) {
                session.rebuildAfterRide = true;
            } else {
                rebuildCurrentRoute(player, false);
            }
        }
        if (session == null || session.phase == NavigationPhase.ROUTE_FAILED || session.phase == NavigationPhase.ARRIVED) {
            if (session != null && session.phase == NavigationPhase.ARRIVED && session.completedAtMs > 0L && System.currentTimeMillis() - session.completedAtMs > 4500L) {
                session = null;
            }
            return;
        }
        if (session.isRiding()) {
            updateRidingApproach(player);
            return;
        }
        if (session.rebuildAfterRide) {
            rebuildCurrentRoute(player, false);
            if (session == null || session.phase == NavigationPhase.ROUTE_FAILED || session.phase == NavigationPhase.ARRIVED) {
                return;
            }
        }
        updateBoardingProximity(player);
    }

    public static boolean canCaptureConnection(PipeConnection connection) {
        if (!isNavigating()) {
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

    public static void onRouteBoarded(PlatformStop platformStop, RouteCandidate candidate, UUID slideSessionId) {
        if (session == null || !session.canBoard(platformStop.id())) {
            return;
        }
        NavigationSegment segment = session.currentSegment();
        if (!segment.layoutId().equals(candidate.layoutId()) || segment.routeDirection() != candidate.routeDirection()) {
            return;
        }
        session.phase = NavigationPhase.RIDING_SEGMENT;
        session.slideSessionId = slideSessionId;
        session.enteredCurrentBoardingRange = false;
        session.lastRouteHudStopContext = null;
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

    public static void onPassThroughStation(UUID platformStopId) {
        if (session == null || !session.isRiding()) {
            return;
        }
        session.lastPassedPlatformStopId = platformStopId;
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
            completeFinalWalkSegment(segment, false);
            return;
        }
        if (segment.finalSegment()) {
            completeDestinationSegment(segment);
            return;
        }
        rememberRouteHudStopContext(segment, platformStopId);
        session.phase = NavigationPhase.TRANSFER_WALK;
        session.segmentIndex++;
        session.enteredCurrentBoardingRange = false;
        NavigationSegment next = session.currentSegment();
        segment.transferInstruction().ifPresentOrElse(
                instruction -> sendTransferNotice(instruction, next, false),
                () -> sendTransferNotice(TransferInstruction.sameStationFallback(segment, next), next, false)
        );
    }

    public static void onSlideDetached(LocalPlayer player, ClientSlideState state, PipeConnection connection, DetachReason reason) {
        if (session == null || !session.isRiding()) {
            return;
        }
        NavigationSegment segment = session.currentSegment();
        session.slideSessionId = null;
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
        session.earlyTransferWarningShown = true;
        NavigationSegment next = session.currentSegment();
        segment.transferInstruction().ifPresentOrElse(
                instruction -> sendTransferNotice(instruction, next, true),
                () -> sendTransferNotice(TransferInstruction.sameStationFallback(segment, next), next, true)
        );
    }

    private static void restartFromCurrentPosition(LocalPlayer player) {
        if (session == null) {
            return;
        }
        UUID destination = session.plan.destinationStationGroupId();
        Optional<NavigationPlan> rebuilt = buildPlan(player, destination);
        if (rebuilt.isEmpty()) {
            session.phase = NavigationPhase.ROUTE_FAILED;
            sendNotice(ClientboundSlideNoticePayload.Kind.WARNING, List.of(0xFFFF5E4D),
                    Component.translatable("navigation.superpipeslide.failed"),
                    List.of(line(Component.translatable("navigation.superpipeslide.failed.body"))));
            return;
        }
        NavigationPlan plan = rebuilt.get();
        if (plan.segments().isEmpty()) {
            session = new NavigationSession(plan, NavigationPhase.ARRIVED);
            session.completedAtMs = System.currentTimeMillis();
            sendNotice(ClientboundSlideNoticePayload.Kind.ARRIVAL, plan.primaryColors(),
                    Component.translatable("navigation.superpipeslide.arrived", stationName(plan.destinationStationGroupId())),
                    List.of(line(Component.translatable("navigation.superpipeslide.arrived.body"))));
            return;
        }
        session = new NavigationSession(plan, NavigationPhase.WALK_TO_BOARDING_STATION);
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
                    Optional.empty()
            ));
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
                    Optional.empty()
            ));
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
            default -> "";
        };
        int completedTransfers = Math.max(0, session.segmentIndex);
        int remainingTransfers = Math.max(0, session.plan.transferCount() - completedTransfers);
        double progress = navigationHudProgress(segment);
        String detail = Component.translatable("navigation.superpipeslide.hud.detail", remainingTransfers, secondsText(session.plan.estimatedTicks())).getString();
        return Optional.of(new NavigationHudSnapshot(
                session.phase,
                stationName(session.plan.destinationStationGroupId()).getString(),
                action,
                detail,
                progress,
                session.segmentIndex + 1,
                segment.colors(),
                target
        ));
    }

    private static double navigationHudProgress(NavigationSegment segment) {
        if (session == null) {
            return 0.0D;
        }
        int segmentCount = session.plan.segments().size();
        if (segmentCount <= 0) {
            return 1.0D;
        }
        double segmentProgress = session.isRiding() ? ridingSegmentProgress(segment) : 0.0D;
        return Mth.clamp((session.segmentIndex + segmentProgress) / segmentCount, 0.0D, 1.0D);
    }

    private static double ridingSegmentProgress(NavigationSegment segment) {
        Optional<ClientRouteHudSnapshot> snapshot = ClientSlideController.routeHudSnapshot();
        if (snapshot.isEmpty()) {
            return 0.38D;
        }
        ClientRouteHudSnapshot hud = snapshot.get();
        if (!segment.layoutId().equals(hud.routeLayoutId()) || segment.routeDirection() != hud.routeDirection()) {
            return 0.38D;
        }
        List<UUID> sequence = segment.stationSequence();
        int targetIndex = lastIndexOf(sequence, segment.alightingPlatformStopId());
        if (targetIndex <= 0) {
            return 0.38D;
        }
        int currentIndex = nearestTravelIndex(sequence, hud.currentPlatformStopId(), targetIndex);
        if (currentIndex < 0) {
            return 0.38D;
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
                target.kind()
        ));
    }

    private static void rebuildCurrentRoute(LocalPlayer player, boolean userRequested) {
        if (session == null) {
            return;
        }
        UUID destination = session.plan.destinationStationGroupId();
        Optional<NavigationPlan> rebuilt = buildPlan(player, destination);
        if (rebuilt.isPresent()) {
            session = new NavigationSession(rebuilt.get(), NavigationPhase.WALK_TO_BOARDING_STATION);
            sendNotice(ClientboundSlideNoticePayload.Kind.STANDARD, rebuilt.get().primaryColors(),
                    Component.translatable("navigation.superpipeslide.route_updated"),
                    List.of(line(Component.translatable("navigation.superpipeslide.route_updated.body"))));
            return;
        }
        session.phase = NavigationPhase.ROUTE_FAILED;
        if (userRequested) {
            sendNotice(ClientboundSlideNoticePayload.Kind.WARNING, List.of(0xFFFF5E4D),
                    Component.translatable("navigation.superpipeslide.failed"),
                    List.of(line(Component.translatable("navigation.superpipeslide.failed.body"))));
        }
    }

    private static void updateBoardingProximity(LocalPlayer player) {
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

    private static void updateRidingApproach(LocalPlayer player) {
        NavigationSegment segment = session.currentSegment();
        Optional<TargetInfo> alighting = targetInfo(player, segment.alightingPlatformStopId(), segment.colors(), ridingTargetKind(segment));
        if (alighting.isEmpty()) {
            return;
        }
        if (alighting.get().distance() <= 32.0D) {
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
        if (session == null || session.plan.segments().isEmpty() || session.phase == NavigationPhase.ARRIVED || session.phase == NavigationPhase.ROUTE_FAILED) {
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

    private static Optional<TargetInfo> targetInfo(LocalPlayer player, UUID platformStopId, List<Integer> colors, TargetKind kind) {
        Optional<PlatformStop> platformStop = ClientRouteDataCache.platformStop(platformStopId);
        if (platformStop.isEmpty()) {
            return Optional.empty();
        }
        Vec3 position = platformTargetPosition(platformStop.get(), player.position());
        return Optional.of(new TargetInfo(
                platformStopId,
                stationName(platformStopId).getString(),
                position,
                position.distanceTo(player.position()),
                colors,
                kind
        ));
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

    private static Optional<NavigationPlan> buildPlan(LocalPlayer player, UUID destinationStationGroupId) {
        NavigationGraph graph = graph();
        List<PlatformStop> destinationStops = ClientRouteDataCache.platformStopsInStation(destinationStationGroupId);
        Set<UUID> destinationStopIds = new HashSet<>();
        destinationStops.forEach(stop -> destinationStopIds.add(stop.id()));
        AccessDistances accessDistances = new AccessDistances(player.position());

        List<PlatformStop> preferredStarts = preferredStartCandidates(player, destinationStationGroupId, accessDistances);
        CandidatePlan best = bestCandidatePlan(graph, preferredStarts, destinationStopIds, destinationStationGroupId, accessDistances);
        if (best == null) {
            best = bestCandidatePlan(graph, fallbackStartCandidates(player, accessDistances), destinationStopIds, destinationStationGroupId, accessDistances);
        }
        if (best == null) {
            return Optional.empty();
        }

        List<NavigationSegment> segments = compressSegments(best.search().edges());
        UUID boardingPlatformStopId = segments.isEmpty() ? best.start().id() : segments.getFirst().boardingPlatformStopId();
        StationGroup startStation = ClientRouteDataCache.platformStop(boardingPlatformStopId)
                .flatMap(stop -> ClientRouteDataCache.stationGroup(stop.stationGroupId()))
                .orElse(null);
        if (startStation == null) {
            return Optional.empty();
        }
        int estimatedTicks = (int) Math.round(best.cost());
        int sameStationTransferCount = 0;
        int outOfStationTransferCount = 0;
        int crossDimensionTransferCount = 0;
        boolean finalWalk = false;
        boolean crossDimensionFinalWalk = false;
        for (NavigationSegment segment : segments) {
            if (segment.transferInstruction().isPresent()) {
                switch (segment.transferInstruction().get().kind()) {
                    case SAME_STATION -> sameStationTransferCount++;
                    case OUT_OF_STATION -> outOfStationTransferCount++;
                    case CROSS_DIMENSION_OUT_OF_STATION -> crossDimensionTransferCount++;
                }
            }
            if (segment.finalWalkInstruction().isPresent()) {
                finalWalk = true;
                crossDimensionFinalWalk = segment.finalWalkInstruction().get().kind() == TransferKind.CROSS_DIMENSION_OUT_OF_STATION;
            }
        }
        int transferCount = sameStationTransferCount + outOfStationTransferCount + crossDimensionTransferCount;
        List<Integer> primaryColors = segments.isEmpty() ? List.of(0xFF47A6FF) : segments.getFirst().colors();
        return Optional.of(new NavigationPlan(
                UUID.randomUUID(),
                ClientRouteDataCache.revision(),
                ClientPipeNetworkCache.aggregateRevision(),
                startStation.id(),
                destinationStationGroupId,
                best.start().id(),
                segments,
                estimatedTicks,
                transferCount,
                sameStationTransferCount,
                outOfStationTransferCount,
                crossDimensionTransferCount,
                finalWalk,
                crossDimensionFinalWalk,
                best.walkDistance(),
                primaryColors
        ));
    }

    @Nullable
    private static CandidatePlan bestCandidatePlan(NavigationGraph graph, List<PlatformStop> candidates, Set<UUID> destinationStopIds, UUID destinationStationGroupId, AccessDistances accessDistances) {
        if (candidates.isEmpty()) {
            return null;
        }
        SearchResult search = solve(graph, candidates, destinationStopIds, destinationStationGroupId, accessDistances);
        if (search.start().isEmpty()) {
            return null;
        }
        double walk = accessDistances.platformDistance(search.start().get());
        return new CandidatePlan(search.start().get(), search, search.cost(), walk);
    }

    private static List<PlatformStop> preferredStartCandidates(LocalPlayer player, UUID destinationStationGroupId, AccessDistances accessDistances) {
        ResourceKey<Level> level = player.level().dimension();
        LinkedHashMap<UUID, PlatformStop> nearbyDestinationStops = new LinkedHashMap<>();
        if (ClientRouteDataCache.stationGroup(destinationStationGroupId)
                .filter(station -> station.levelKey().equals(level))
                .filter(station -> accessDistances.stationGroupDistance(station.id()) <= BOARDING_LOCAL_RANGE)
                .isPresent()) {
            ClientRouteDataCache.platformStopsInStation(destinationStationGroupId).stream()
                    .sorted(Comparator.comparingDouble(accessDistances::platformDistance))
                    .forEach(stop -> nearbyDestinationStops.put(stop.id(), stop));
            if (!nearbyDestinationStops.isEmpty()) {
                return List.copyOf(nearbyDestinationStops.values());
            }
        }
        LinkedHashMap<UUID, PlatformStop> localStationCandidates = new LinkedHashMap<>();
        ClientRouteDataCache.stationGroups().stream()
                .filter(station -> station.levelKey().equals(level))
                .filter(station -> accessDistances.stationGroupDistance(station.id()) <= BOARDING_LOCAL_RANGE)
                .sorted(Comparator.comparingDouble(station -> accessDistances.stationGroupDistance(station.id())))
                .forEach(station -> ClientRouteDataCache.platformStopsInStation(station.id()).stream()
                        .filter(stop -> !routeEdgesFrom(stop.id()).isEmpty())
                        .sorted(Comparator.comparingDouble(accessDistances::platformDistance))
                        .forEach(stop -> localStationCandidates.put(stop.id(), stop)));
        if (!localStationCandidates.isEmpty()) {
            return List.copyOf(localStationCandidates.values());
        }
        return List.of();
    }

    private static List<PlatformStop> fallbackStartCandidates(LocalPlayer player, AccessDistances accessDistances) {
        ResourceKey<Level> level = player.level().dimension();
        LinkedHashMap<UUID, PlatformStop> candidates = new LinkedHashMap<>();
        ClientRouteDataCache.platformStops().stream()
                .filter(stop -> ClientRouteDataCache.stationGroup(stop.stationGroupId()).map(group -> group.levelKey().equals(level)).orElse(false))
                .filter(stop -> !routeEdgesFrom(stop.id()).isEmpty())
                .sorted(Comparator
                        .comparingDouble((PlatformStop stop) -> accessDistances.platformDistance(stop))
                        .thenComparingDouble(stop -> accessDistances.stationGroupDistance(stop.stationGroupId())))
                .forEach(stop -> candidates.put(stop.id(), stop));
        return List.copyOf(candidates.values());
    }

    private static NavigationGraph graph() {
        long routeRevision = ClientRouteDataCache.revision();
        long pipeRevision = ClientPipeNetworkCache.aggregateRevision();
        if (cachedGraph != null && cachedRouteRevision == routeRevision && cachedPipeRevision == pipeRevision) {
            return cachedGraph;
        }
        Map<NodeKey, List<GraphEdge>> edges = new LinkedHashMap<>();
        for (RouteLayout layout : ClientRouteDataCache.routeLayouts()) {
            addLayoutEdges(edges, layout, 1);
            if (layout.bidirectional()) {
                addLayoutEdges(edges, layout, -1);
            }
        }
        Set<UUID> rideConnectedStops = rideConnectedStops(edges);
        addSameStationTransferEdges(edges, rideConnectedStops);
        addConfiguredOutOfStationTransferEdges(edges);
        cachedGraph = new NavigationGraph(edges, rideConnectedStops);
        cachedRouteRevision = routeRevision;
        cachedPipeRevision = pipeRevision;
        return cachedGraph;
    }

    private static Set<UUID> rideConnectedStops(Map<NodeKey, List<GraphEdge>> edges) {
        Set<UUID> result = new HashSet<>();
        for (List<GraphEdge> outgoing : edges.values()) {
            for (GraphEdge edge : outgoing) {
                if (edge.kind() == EdgeKind.RIDE) {
                    result.add(edge.from().id());
                    result.add(edge.to().id());
                }
            }
        }
        return result;
    }

    private static void addSameStationTransferEdges(Map<NodeKey, List<GraphEdge>> edges, Set<UUID> rideConnectedStops) {
        for (StationGroup station : ClientRouteDataCache.stationGroups()) {
            List<PlatformStop> stops = ClientRouteDataCache.platformStopsInStation(station.id());
            NodeKey stationNode = NodeKey.stationTransfer(station.id());
            for (PlatformStop stop : stops) {
                if (!rideConnectedStops.contains(stop.id())) {
                    continue;
                }
                edges.computeIfAbsent(NodeKey.platform(stop.id()), ignored -> new ArrayList<>()).add(GraphEdge.stationAccess(stop.id(), station));
                edges.computeIfAbsent(stationNode, ignored -> new ArrayList<>()).add(GraphEdge.stationBoard(station, stop.id(), SAME_STATION_TRANSFER_PENALTY_TICKS));
            }
        }
    }

    private static void addConfiguredOutOfStationTransferEdges(Map<NodeKey, List<GraphEdge>> edges) {
        for (StationTransferLink link : ClientRouteDataCache.stationTransferLinks()) {
            Optional<StationGroup> firstStation = ClientRouteDataCache.stationGroup(link.firstStationGroupId());
            Optional<StationGroup> secondStation = ClientRouteDataCache.stationGroup(link.secondStationGroupId());
            if (firstStation.isEmpty() || secondStation.isEmpty()) {
                continue;
            }
            TransferKind forwardKind = firstStation.get().levelKey().equals(secondStation.get().levelKey())
                    ? TransferKind.OUT_OF_STATION
                    : TransferKind.CROSS_DIMENSION_OUT_OF_STATION;
            edges.computeIfAbsent(NodeKey.stationTransfer(firstStation.get().id()), ignored -> new ArrayList<>()).add(GraphEdge.stationTransfer(
                    link.estimatedWalkTicks(),
                    forwardKind,
                    link.id(),
                    firstStation.get(),
                    secondStation.get()
            ));
            edges.computeIfAbsent(NodeKey.stationTransfer(secondStation.get().id()), ignored -> new ArrayList<>()).add(GraphEdge.stationTransfer(
                    link.estimatedWalkTicks(),
                    forwardKind,
                    link.id(),
                    secondStation.get(),
                    firstStation.get()
            ));
        }
    }

    private static void addLayoutEdges(Map<NodeKey, List<GraphEdge>> edges, RouteLayout layout, int direction) {
        for (UUID platformStopId : layout.orderedPlatformStops()) {
            RouteLayoutNavigator.nextStep(layout, platformStopId, direction, ClientRouteDataCache::routeSection)
                    .filter(step -> step.section().statusForDirection(direction) == RouteSectionStatus.VALID)
                    .ifPresent(step -> {
                        RouteSection section = step.section();
                        double length = Math.max(1.0D, section.lengthForDirection(direction));
                        double cost = length / ESTIMATED_RIDE_SPEED;
                        List<Integer> colors = ClientRouteDataCache.routeLine(layout.routeLineId())
                                .map(RouteLine::themeColors)
                                .filter(values -> !values.isEmpty())
                                .orElse(List.of(0xFF47A6FF));
                        String lineName = ClientRouteDataCache.routeLine(layout.routeLineId()).map(RouteLine::displayName).orElse("Route");
                        edges.computeIfAbsent(NodeKey.platform(platformStopId), ignored -> new ArrayList<>()).add(GraphEdge.ride(
                                platformStopId,
                                step.nextPlatformStopId(),
                                layout.routeLineId(),
                                layout.id(),
                                direction,
                                section.id(),
                                step.sectionIndex(),
                                cost,
                                colors,
                                lineName
                        ));
                    });
        }
    }

    private static List<GraphEdge> routeEdgesFrom(UUID platformStopId) {
        return graph().edgesFrom(NodeKey.platform(platformStopId)).stream().filter(edge -> edge.kind() == EdgeKind.RIDE).toList();
    }

    private static SearchResult solve(NavigationGraph graph, List<PlatformStop> starts, Set<UUID> destinations, UUID destinationStationGroupId, AccessDistances accessDistances) {
        PriorityQueue<SearchNode> open = new PriorityQueue<>(Comparator.comparingDouble(SearchNode::cost));
        Map<SearchState, Double> bestCost = new HashMap<>();
        Map<SearchState, PathBackref> backrefs = new HashMap<>();
        Map<SearchState, PlatformStop> sourceByState = new HashMap<>();
        for (PlatformStop start : starts) {
            SearchState state = new SearchState(NodeKey.platform(start.id()), false);
            double walk = accessDistances.platformDistance(start);
            double cost = walk * WALK_TICKS_PER_BLOCK + BOARDING_PENALTY_TICKS;
            if (cost >= bestCost.getOrDefault(state, Double.MAX_VALUE)) {
                continue;
            }
            bestCost.put(state, cost);
            sourceByState.put(state, start);
            open.add(new SearchNode(state, cost));
        }
        SearchState reached = null;
        while (!open.isEmpty()) {
            SearchNode current = open.poll();
            if (current.cost() > bestCost.getOrDefault(current.state(), Double.MAX_VALUE) + 1.0E-6D) {
                continue;
            }
            if (isTargetState(current.state(), destinations, destinationStationGroupId)) {
                reached = current.state();
                break;
            }
            for (GraphEdge edge : graph.edgesFrom(current.state().node())) {
                if (!current.state().hasRide() && edge.kind() != EdgeKind.RIDE) {
                    continue;
                }
                boolean nextHasRide = current.state().hasRide() || edge.kind() == EdgeKind.RIDE;
                SearchState nextState = new SearchState(edge.to(), nextHasRide);
                double nextCost = current.cost() + edge.cost();
                if (nextCost >= bestCost.getOrDefault(nextState, Double.MAX_VALUE)) {
                    continue;
                }
                bestCost.put(nextState, nextCost);
                backrefs.put(nextState, new PathBackref(current.state(), edge));
                PlatformStop source = sourceByState.get(current.state());
                if (source != null) {
                    sourceByState.put(nextState, source);
                }
                open.add(new SearchNode(nextState, nextCost));
            }
        }
        if (reached == null) {
            return new SearchResult(List.of(), Double.MAX_VALUE, Optional.empty());
        }
        ArrayList<GraphEdge> path = new ArrayList<>();
        SearchState cursor = reached;
        while (backrefs.containsKey(cursor)) {
            PathBackref backref = backrefs.get(cursor);
            if (backref == null) {
                return new SearchResult(List.of(), Double.MAX_VALUE, Optional.empty());
            }
            path.add(0, backref.edge());
            cursor = backref.previous();
        }
        return new SearchResult(path, bestCost.getOrDefault(reached, Double.MAX_VALUE), Optional.ofNullable(sourceByState.get(reached)));
    }

    private static boolean isTargetState(SearchState state, Set<UUID> destinations, UUID destinationStationGroupId) {
        if (state.node().type() == NodeType.PLATFORM && destinations.contains(state.node().id())) {
            return true;
        }
        return state.hasRide()
                && state.node().type() == NodeType.STATION_TRANSFER
                && destinationStationGroupId.equals(state.node().id());
    }

    private static List<NavigationSegment> compressSegments(List<GraphEdge> edges) {
        ArrayList<SegmentBuilder> builders = new ArrayList<>();
        SegmentBuilder current = null;
        ArrayList<GraphEdge> transferAfterCurrent = new ArrayList<>();
        for (GraphEdge edge : edges) {
            if (!(edge instanceof RideEdge rideEdge)) {
                transferAfterCurrent.add(edge);
                continue;
            }
            if (current != null && current.matches(rideEdge) && transferAfterCurrent.isEmpty()) {
                current.add(rideEdge);
                continue;
            }
            if (current != null) {
                current.transferAfterEdges.addAll(transferAfterCurrent);
                transferAfterCurrent.clear();
                builders.add(current);
            } else {
                transferAfterCurrent.clear();
            }
            current = new SegmentBuilder(rideEdge);
        }
        if (current != null) {
            current.transferAfterEdges.addAll(transferAfterCurrent);
            builders.add(current);
        } else if (!builders.isEmpty() && !transferAfterCurrent.isEmpty()) {
            builders.getLast().transferAfterEdges.addAll(transferAfterCurrent);
        }
        ArrayList<NavigationSegment> segments = new ArrayList<>();
        for (int i = 0; i < builders.size(); i++) {
            SegmentBuilder builder = builders.get(i);
            boolean finalSegment = i == builders.size() - 1;
            Optional<TransferInstruction> transferInstruction = Optional.empty();
            Optional<FinalWalkInstruction> finalWalkInstruction = Optional.empty();
            if (!finalSegment) {
                SegmentBuilder next = builders.get(i + 1);
                transferInstruction = transferInstruction(builder, next);
            } else if (!builder.transferAfterEdges.isEmpty()) {
                finalWalkInstruction = finalWalkInstruction(builder.transferAfterEdges);
            }
            segments.add(builder.build(i, finalSegment, transferInstruction, finalWalkInstruction));
        }
        return List.copyOf(segments);
    }

    private static Optional<TransferInstruction> transferInstruction(SegmentBuilder builder, SegmentBuilder next) {
        Optional<TransferEdge> semanticEdge = transferSemanticEdge(builder.transferAfterEdges);
        if (semanticEdge.isPresent()) {
            return Optional.of(TransferInstruction.fromEdge(semanticEdge.get(), next.boardingPlatformStopId, next.lineName, next.colors));
        }
        Optional<PlatformStop> fromStop = ClientRouteDataCache.platformStop(builder.alightingPlatformStopId());
        Optional<PlatformStop> toStop = ClientRouteDataCache.platformStop(next.boardingPlatformStopId);
        Optional<StationGroup> fromStation = fromStop.flatMap(stop -> ClientRouteDataCache.stationGroup(stop.stationGroupId()));
        Optional<StationGroup> toStation = toStop.flatMap(stop -> ClientRouteDataCache.stationGroup(stop.stationGroupId()));
        if (fromStation.isEmpty() || toStation.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(TransferInstruction.sameStation(
                fromStation.get(),
                toStation.get(),
                next.boardingPlatformStopId,
                next.lineName,
                next.colors
        ));
    }

    private static Optional<FinalWalkInstruction> finalWalkInstruction(List<GraphEdge> transferEdges) {
        return transferEdges.stream()
                .filter(TransferEdge.class::isInstance)
                .map(TransferEdge.class::cast)
                .filter(edge -> edge.transferKind() == TransferKind.OUT_OF_STATION || edge.transferKind() == TransferKind.CROSS_DIMENSION_OUT_OF_STATION)
                .reduce((ignored, edge) -> edge)
                .flatMap(edge -> {
                    if (edge.transferLinkId().isEmpty()) {
                        return Optional.empty();
                    }
                    return Optional.of(new FinalWalkInstruction(
                            edge.transferKind(),
                            edge.fromStationGroupId(),
                            edge.toStationGroupId(),
                            edge.transferLinkId(),
                            edge.fromLevelKey(),
                            edge.toLevelKey()
                    ));
                });
    }

    private static Optional<TransferEdge> transferSemanticEdge(List<GraphEdge> transferEdges) {
        Optional<TransferEdge> outOfStation = transferEdges.stream()
                .filter(TransferEdge.class::isInstance)
                .map(TransferEdge.class::cast)
                .filter(edge -> edge.transferKind() == TransferKind.OUT_OF_STATION || edge.transferKind() == TransferKind.CROSS_DIMENSION_OUT_OF_STATION)
                .reduce((ignored, edge) -> edge);
        if (outOfStation.isPresent()) {
            return outOfStation;
        }
        return transferEdges.stream()
                .filter(TransferEdge.class::isInstance)
                .map(TransferEdge.class::cast)
                .filter(edge -> edge.transferKind() == TransferKind.SAME_STATION)
                .reduce((ignored, edge) -> edge);
    }

    private static DestinationSearchResult destinationResult(ResourceKey<Level> playerLevel, Vec3 playerPosition, StationGroup station, String query, boolean reachable) {
        int score = query.isBlank() ? 1 : matchScore(station, query);
        double distance = station.levelKey().equals(playerLevel) ? Vec3.atCenterOf(station.stationBlockPos()).distanceTo(playerPosition) : Double.MAX_VALUE / 4.0D;
        return new DestinationSearchResult(station.id(), station.primaryName(), station.translatedNames(), station.levelKey(), distance, reachable, score);
    }

    private static Set<UUID> reachableStationGroups(LocalPlayer player) {
        NavigationGraph graph = graph();
        long routeRevision = ClientRouteDataCache.revision();
        long pipeRevision = ClientPipeNetworkCache.aggregateRevision();
        ResourceKey<Level> level = player.level().dimension();
        if (cachedReachability != null
                && cachedReachability.routeRevision() == routeRevision
                && cachedReachability.pipeRevision() == pipeRevision
                && cachedReachability.levelKey().equals(level)) {
            return cachedReachability.stationGroupIds();
        }
        LinkedHashSet<UUID> reachable = new LinkedHashSet<>();
        ArrayDeque<SearchState> queue = new ArrayDeque<>();
        HashSet<SearchState> visited = new HashSet<>();
        for (PlatformStop start : ClientRouteDataCache.platformStops()) {
            if (!graph.hasRideConnection(start.id())) {
                continue;
            }
            if (ClientRouteDataCache.stationGroup(start.stationGroupId()).map(station -> station.levelKey().equals(level)).orElse(false)) {
                SearchState state = new SearchState(NodeKey.platform(start.id()), false);
                if (visited.add(state)) {
                    queue.add(state);
                }
            }
        }
        while (!queue.isEmpty()) {
            SearchState current = queue.removeFirst();
            if (current.hasRide()) {
                addReachableStation(current.node(), reachable);
            }
            for (GraphEdge edge : graph.edgesFrom(current.node())) {
                if (!current.hasRide() && edge.kind() != EdgeKind.RIDE) {
                    continue;
                }
                SearchState next = new SearchState(edge.to(), current.hasRide() || edge.kind() == EdgeKind.RIDE);
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        cachedReachability = new ReachabilityCache(routeRevision, pipeRevision, level, Set.copyOf(reachable));
        return cachedReachability.stationGroupIds();
    }

    private static void addReachableStation(NodeKey node, Set<UUID> reachable) {
        if (node.type() == NodeType.STATION_TRANSFER) {
            reachable.add(node.id());
            return;
        }
        if (node.type() == NodeType.PLATFORM) {
            ClientRouteDataCache.platformStop(node.id()).ifPresent(stop -> reachable.add(stop.stationGroupId()));
        }
    }

    private static int matchScore(StationGroup station, String query) {
        String primary = station.primaryName().toLowerCase(Locale.ROOT);
        if (primary.equals(query)) {
            return 100;
        }
        if (primary.startsWith(query)) {
            return 80;
        }
        if (primary.contains(query)) {
            return 60;
        }
        for (String translated : station.translatedNames()) {
            String value = translated.toLowerCase(Locale.ROOT);
            if (value.equals(query)) {
                return 95;
            }
            if (value.startsWith(query)) {
                return 75;
            }
            if (value.contains(query)) {
                return 55;
            }
        }
        return 0;
    }

    private static Vec3 platformPosition(PlatformStop platformStop) {
        return ClientPipeNetworkCache.connection(platformStop.connectionRef())
                .map(connection -> connection.positionAt(connection.length() * 0.5D))
                .orElseGet(() -> ClientRouteDataCache.stationGroup(platformStop.stationGroupId())
                        .map(group -> Vec3.atCenterOf(group.stationBlockPos()))
                        .orElse(Vec3.ZERO));
    }

    private static Vec3 platformTargetPosition(PlatformStop platformStop, Vec3 playerPosition) {
        return ClientPipeNetworkCache.connection(platformStop.connectionRef())
                .map(connection -> SlideGeometry.project(connection, playerPosition).closestPoint())
                .orElseGet(() -> ClientRouteDataCache.stationGroup(platformStop.stationGroupId())
                        .map(group -> Vec3.atCenterOf(group.stationBlockPos()))
                        .orElse(Vec3.ZERO));
    }

    private static double platformAccessDistance(PlatformStop platformStop, Vec3 playerPosition) {
        return ClientPipeNetworkCache.connection(platformStop.connectionRef())
                .map(connection -> SlideGeometry.project(connection, playerPosition).distance())
                .orElseGet(() -> platformPosition(platformStop).distanceTo(playerPosition));
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
        session.phase = NavigationPhase.ARRIVED;
        session.completedAtMs = System.currentTimeMillis();
        boolean crossDimension = instruction.kind() == TransferKind.CROSS_DIMENSION_OUT_OF_STATION;
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
        sendNotice(ClientboundSlideNoticePayload.Kind.WARNING, next.colors(),
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
            List<Integer> primaryColors
    ) {
        public NavigationPlan {
            segments = List.copyOf(segments);
            primaryColors = List.copyOf(primaryColors);
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
            String lineName
    ) {
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

    public record NavigationSectionRef(UUID routeSectionId, int layoutIndex) {
    }

    public record TransferInstruction(
            TransferKind kind,
            UUID fromStationGroupId,
            UUID toStationGroupId,
            Optional<UUID> transferLinkId,
            ResourceKey<Level> fromLevelKey,
            ResourceKey<Level> toLevelKey,
            UUID nextBoardingPlatformStopId,
            String nextLineName,
            List<Integer> nextColors
    ) {
        public TransferInstruction {
            transferLinkId = transferLinkId == null ? Optional.empty() : transferLinkId;
            nextColors = List.copyOf(nextColors);
        }

        private static TransferInstruction fromEdge(TransferEdge edge, UUID nextBoardingPlatformStopId, String nextLineName, List<Integer> nextColors) {
            return new TransferInstruction(
                    edge.transferKind(),
                    edge.fromStationGroupId(),
                    edge.toStationGroupId(),
                    edge.transferLinkId(),
                    edge.fromLevelKey(),
                    edge.toLevelKey(),
                    nextBoardingPlatformStopId,
                    nextLineName,
                    nextColors
            );
        }

        private static TransferInstruction sameStation(StationGroup fromStation, StationGroup toStation, UUID nextBoardingPlatformStopId, String nextLineName, List<Integer> nextColors) {
            return new TransferInstruction(
                    TransferKind.SAME_STATION,
                    fromStation.id(),
                    toStation.id(),
                    Optional.empty(),
                    fromStation.levelKey(),
                    toStation.levelKey(),
                    nextBoardingPlatformStopId,
                    nextLineName,
                    nextColors
            );
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
                    next.colors()
            );
        }
    }

    public record FinalWalkInstruction(
            TransferKind kind,
            UUID fromStationGroupId,
            UUID destinationStationGroupId,
            Optional<UUID> transferLinkId,
            ResourceKey<Level> fromLevelKey,
            ResourceKey<Level> destinationLevelKey
    ) {
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
            int matchScore
    ) {
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
            Optional<TargetInfo> target
    ) {
        public NavigationHudSnapshot {
            colors = List.copyOf(colors);
        }
    }

    public record TargetInfo(UUID platformStopId, String name, Vec3 position, double distance, List<Integer> colors, TargetKind kind) {
        public TargetInfo {
            colors = List.copyOf(colors);
        }
    }

    public record WorldTarget(Vec3 position, String name, int color, double distance, TargetKind kind) {
    }

    private static final class NavigationSession {
        private NavigationPlan plan;
        private NavigationPhase phase;
        private int segmentIndex;
        private boolean enteredCurrentBoardingRange;
        private long lastRangeExitMessageTick = Long.MIN_VALUE;
        private boolean earlyTransferWarningShown;
        private boolean rebuildAfterRide;
        @Nullable
        private ClientRouteHudSnapshot.NavigationStopContext lastRouteHudStopContext;
        private long completedAtMs;
        @Nullable
        private UUID slideSessionId;
        @Nullable
        private UUID lastPassedPlatformStopId;

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

    private record NavigationGraph(Map<NodeKey, List<GraphEdge>> edges, Set<UUID> rideConnectedStops) {
        private NavigationGraph {
            rideConnectedStops = Set.copyOf(rideConnectedStops);
        }

        private List<GraphEdge> edgesFrom(NodeKey node) {
            return this.edges.getOrDefault(node, List.of());
        }

        private boolean hasRideConnection(UUID platformStopId) {
            return this.rideConnectedStops.contains(platformStopId);
        }
    }

    private enum NodeType {
        PLATFORM,
        STATION_TRANSFER
    }

    private record NodeKey(NodeType type, UUID id) {
        private static NodeKey platform(UUID platformStopId) {
            return new NodeKey(NodeType.PLATFORM, platformStopId);
        }

        private static NodeKey stationTransfer(UUID stationGroupId) {
            return new NodeKey(NodeType.STATION_TRANSFER, stationGroupId);
        }
    }

    private enum EdgeKind {
        RIDE,
        STATION_ACCESS,
        TRANSFER
    }

    private sealed interface GraphEdge permits RideEdge, StationAccessEdge, TransferEdge {
        EdgeKind kind();

        NodeKey from();

        NodeKey to();

        double cost();

        static RideEdge ride(UUID from, UUID to, UUID routeLineId, UUID layoutId, int routeDirection, UUID routeSectionId, int layoutIndex, double cost, List<Integer> colors, String lineName) {
            return new RideEdge(NodeKey.platform(from), NodeKey.platform(to), routeLineId, layoutId, routeDirection, routeSectionId, layoutIndex, cost, colors, lineName);
        }

        static StationAccessEdge stationAccess(UUID platformStopId, StationGroup station) {
            return new StationAccessEdge(NodeKey.platform(platformStopId), NodeKey.stationTransfer(station.id()), station.id(), station.levelKey());
        }

        static TransferEdge stationBoard(StationGroup station, UUID platformStopId, double cost) {
            return new TransferEdge(
                    NodeKey.stationTransfer(station.id()),
                    NodeKey.platform(platformStopId),
                    cost,
                    TransferKind.SAME_STATION,
                    Optional.empty(),
                    station.id(),
                    station.id(),
                    station.levelKey(),
                    station.levelKey()
            );
        }

        static TransferEdge stationTransfer(double cost, TransferKind transferKind, UUID transferLinkId, StationGroup fromStation, StationGroup toStation) {
            return new TransferEdge(
                    NodeKey.stationTransfer(fromStation.id()),
                    NodeKey.stationTransfer(toStation.id()),
                    cost,
                    transferKind,
                    Optional.of(transferLinkId),
                    fromStation.id(),
                    toStation.id(),
                    fromStation.levelKey(),
                    toStation.levelKey()
            );
        }
    }

    private record RideEdge(
            NodeKey from,
            NodeKey to,
            UUID routeLineId,
            UUID layoutId,
            int routeDirection,
            UUID routeSectionId,
            int layoutIndex,
            double cost,
            List<Integer> colors,
            String lineName
    ) implements GraphEdge {
        private RideEdge {
            routeDirection = routeDirection < 0 ? -1 : 1;
            colors = List.copyOf(colors);
        }

        @Override
        public EdgeKind kind() {
            return EdgeKind.RIDE;
        }
    }

    private record StationAccessEdge(
            NodeKey from,
            NodeKey to,
            UUID stationGroupId,
            ResourceKey<Level> levelKey
    ) implements GraphEdge {
        @Override
        public EdgeKind kind() {
            return EdgeKind.STATION_ACCESS;
        }

        @Override
        public double cost() {
            return 0.0D;
        }
    }

    private record TransferEdge(
            NodeKey from,
            NodeKey to,
            double cost,
            TransferKind transferKind,
            Optional<UUID> transferLinkId,
            UUID fromStationGroupId,
            UUID toStationGroupId,
            ResourceKey<Level> fromLevelKey,
            ResourceKey<Level> toLevelKey
    ) implements GraphEdge {
        private TransferEdge {
            transferLinkId = transferLinkId == null ? Optional.empty() : transferLinkId;
        }

        @Override
        public EdgeKind kind() {
            return EdgeKind.TRANSFER;
        }
    }

    private record SearchState(NodeKey node, boolean hasRide) {
    }

    private record SearchNode(SearchState state, double cost) {
    }

    private record PathBackref(SearchState previous, GraphEdge edge) {
    }

    private record SearchResult(List<GraphEdge> edges, double cost, Optional<PlatformStop> start) {
        private SearchResult {
            edges = List.copyOf(edges);
            start = start == null ? Optional.empty() : start;
        }
    }

    private record ReachabilityCache(long routeRevision, long pipeRevision, ResourceKey<Level> levelKey, Set<UUID> stationGroupIds) {
        private ReachabilityCache {
            stationGroupIds = Set.copyOf(stationGroupIds);
        }
    }

    private record CandidatePlan(PlatformStop start, SearchResult search, double cost, double walkDistance) {
    }

    private static final class AccessDistances {
        private final Vec3 playerPosition;
        private final Map<UUID, Double> platformDistances = new HashMap<>();
        private final Map<UUID, Double> stationGroupDistances = new HashMap<>();

        private AccessDistances(Vec3 playerPosition) {
            this.playerPosition = playerPosition;
        }

        private double platformDistance(PlatformStop platformStop) {
            return this.platformDistances.computeIfAbsent(platformStop.id(), ignored -> platformAccessDistance(platformStop, this.playerPosition));
        }

        private double stationGroupDistance(UUID stationGroupId) {
            return this.stationGroupDistances.computeIfAbsent(stationGroupId, ignored -> {
                List<PlatformStop> stops = ClientRouteDataCache.platformStopsInStation(stationGroupId);
                if (stops.isEmpty()) {
                    return ClientRouteDataCache.stationGroup(stationGroupId)
                            .map(station -> Vec3.atCenterOf(station.stationBlockPos()).distanceTo(this.playerPosition))
                            .orElse(Double.MAX_VALUE / 4.0D);
                }
                return stops.stream()
                        .mapToDouble(this::platformDistance)
                        .min()
                        .orElse(Double.MAX_VALUE / 4.0D);
            });
        }
    }

    private static final class SegmentBuilder {
        private final UUID routeLineId;
        private final UUID layoutId;
        private final int routeDirection;
        private final UUID boardingPlatformStopId;
        private final ArrayList<UUID> stationSequence = new ArrayList<>();
        private final ArrayList<UUID> sectionIds = new ArrayList<>();
        private final ArrayList<NavigationSectionRef> sectionRefs = new ArrayList<>();
        private final ArrayList<Integer> colors;
        private final String lineName;
        private double cost;
        private final ArrayList<GraphEdge> transferAfterEdges = new ArrayList<>();

        private SegmentBuilder(RideEdge first) {
            this.routeLineId = first.routeLineId();
            this.layoutId = first.layoutId();
            this.routeDirection = first.routeDirection();
            this.boardingPlatformStopId = first.from().id();
            this.colors = new ArrayList<>(first.colors());
            this.lineName = first.lineName();
            this.stationSequence.add(first.from().id());
            this.add(first);
        }

        private boolean matches(RideEdge edge) {
            return this.routeLineId.equals(edge.routeLineId())
                    && this.layoutId.equals(edge.layoutId())
                    && this.routeDirection == edge.routeDirection()
                    && this.stationSequence.getLast().equals(edge.from().id());
        }

        private void add(RideEdge edge) {
            this.stationSequence.add(edge.to().id());
            this.sectionIds.add(edge.routeSectionId());
            this.sectionRefs.add(new NavigationSectionRef(edge.routeSectionId(), edge.layoutIndex()));
            this.cost += edge.cost();
        }

        private UUID alightingPlatformStopId() {
            return this.stationSequence.getLast();
        }

        private NavigationSegment build(int index, boolean finalSegment, Optional<TransferInstruction> transferInstruction, Optional<FinalWalkInstruction> finalWalkInstruction) {
            return new NavigationSegment(
                    index,
                    this.routeLineId,
                    this.layoutId,
                    this.routeDirection,
                    this.boardingPlatformStopId,
                    this.alightingPlatformStopId(),
                    this.stationSequence,
                    this.sectionIds,
                    this.sectionRefs,
                    transferInstruction,
                    finalWalkInstruction,
                    finalSegment,
                    (int) Math.round(this.cost),
                    this.colors,
                    this.lineName
            );
        }
    }
}
