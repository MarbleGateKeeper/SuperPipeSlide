package dev.marblegate.superpipeslide.client.core.slide;

import dev.marblegate.superpipeslide.client.core.accessibility.ClientSafetyOptions;
import dev.marblegate.superpipeslide.client.core.fold.ClientFoldTraversalEffectController;
import dev.marblegate.superpipeslide.client.core.gaze.ClientGazeChoiceController;
import dev.marblegate.superpipeslide.client.core.navigation.ClientNavigationController;
import dev.marblegate.superpipeslide.client.core.navigation.StationEntryDecision;
import dev.marblegate.superpipeslide.client.core.navigation.StationEntryMode;
import dev.marblegate.superpipeslide.client.core.navigation.StationEntryPolicy;
import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteHudSnapshot;
import dev.marblegate.superpipeslide.client.core.route.RouteCandidate;
import dev.marblegate.superpipeslide.client.core.sync.ClientDataResyncRequests;
import dev.marblegate.superpipeslide.client.gui.accessibility.SlideSafetyWarningScreen;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.gaze.GazeChoice;
import dev.marblegate.superpipeslide.common.core.gaze.GazeChoiceExpireCondition;
import dev.marblegate.superpipeslide.common.core.gaze.GazeChoiceExpireType;
import dev.marblegate.superpipeslide.common.core.gaze.GazeChoicePlacement;
import dev.marblegate.superpipeslide.common.core.gaze.GazeChoiceSession;
import dev.marblegate.superpipeslide.common.core.gaze.GazeChoiceShape;
import dev.marblegate.superpipeslide.common.core.gaze.GazeChoiceSource;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import dev.marblegate.superpipeslide.common.core.geometry.SlideGeometry;
import dev.marblegate.superpipeslide.common.core.networkgraph.branch.BranchNode;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionPath;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.core.route.service.RouteLayoutNavigator;
import dev.marblegate.superpipeslide.common.core.route.service.RouteLayoutNavigator.RouteStep;
import dev.marblegate.superpipeslide.common.core.slide.ResolvedPipeSpeedRules;
import dev.marblegate.superpipeslide.common.core.slide.traversal.SlideTraversalStepper;
import dev.marblegate.superpipeslide.common.core.slide.traversal.StationCheckpoint;
import dev.marblegate.superpipeslide.common.core.slide.traversal.TraversalContext;
import dev.marblegate.superpipeslide.common.core.slide.traversal.TraversalCursor;
import dev.marblegate.superpipeslide.common.core.slide.traversal.TraversalEvent;
import dev.marblegate.superpipeslide.common.core.slide.traversal.TraversalEventType;
import dev.marblegate.superpipeslide.common.core.slide.traversal.TraversalResult;
import dev.marblegate.superpipeslide.config.Config;
import dev.marblegate.superpipeslide.network.slide.ClientboundSlideNoticePayload;
import dev.marblegate.superpipeslide.network.slide.ClientboundSlideTeleportCommitPayload;
import dev.marblegate.superpipeslide.network.slide.ClientboundSlideTeleportFailedPayload;
import dev.marblegate.superpipeslide.network.slide.ServerboundSlideModePayload;
import dev.marblegate.superpipeslide.network.slide.ServerboundSlideTeleportRequestPayload;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class ClientSlideController {
    private static final double SEARCH_RADIUS = 72.0D;
    private static final double MIN_SIDE_CAPTURE_ALIGNMENT = 0.35D;
    private static final double MAX_SLIDE_POSITION_ERROR = 4.0D;
    private static final double COOLDOWN_EXIT_SEGMENT_MARGIN = 0.45D;
    private static final double COOLDOWN_EXIT_HORIZONTAL_RADIUS = 0.62D;
    private static final double FOLD_ANCHOR_EXIT_OFFSET = 0.22D;
    private static final double BRANCH_CHOICE_LOOKAHEAD_MIN_DISTANCE = 4.0D;
    private static final double BRANCH_CHOICE_LOOKAHEAD_FRACTION = 0.35D;
    private static final double BRANCH_CHOICE_HEIGHT_ABOVE_PIPE = 1.35D;
    private static final int BRANCH_REQUIRED_FOCUS_TICKS = 8;
    private static final int STATION_REQUIRED_FOCUS_TICKS = 5;
    private static final int STATION_MIN_DWELL_TICKS = 20 * 8;
    private static final double STATION_CHECKPOINT_EPSILON = 1.0E-4D;
    private static final double MIN_STATION_SLOW_SPEED = 0.001D;
    private static final int BRANCH_SESSION_TTL_TICKS = 20 * 20;
    private static final int STATION_SESSION_TTL_TICKS = 20 * 30;
    private static final int DEFAULT_BRANCH_EXIT_COLOR = 0xFFD8F4FF;
    private static final int BRANCH_EXIT_COLOR = 0xFF80D8FF;
    private static final int ROUTE_COLOR = 0xFF7CCBFF;
    private static final int SAFETY_PROMPT_COOLDOWN_TICKS = 20;

    @Nullable
    private static ClientSlideState active;
    @Nullable
    private static CaptureCooldown cooldown;
    @Nullable
    private static UUID pendingBranchChoiceId;
    @Nullable
    private static UUID pendingStationChoiceId;
    @Nullable
    private static UUID activeRouteLayoutId;
    @Nullable
    private static UUID activeRoutePlatformStopId;
    @Nullable
    private static UUID activeRouteSectionId;
    private static int activeRouteConnectionIndex;
    private static int activeRouteDirection = 1;
    @Nullable
    private static RetainedRouteHudNavigationStop retainedRouteHudNavigationStop;
    @Nullable
    private static ClientboundSlideTeleportCommitPayload pendingTeleportCommit;
    @Nullable
    private static UUID waitingTeleportSessionId;
    @Nullable
    private static UUID openBranchNodeId;
    @Nullable
    private static UUID openBranchSessionId;
    @Nullable
    private static UUID openStationPlatformStopId;
    @Nullable
    private static UUID openStationSessionId;
    @Nullable
    private static UUID heldStationChoicePlatformStopId;
    @Nullable
    private static StationCenterAction pendingStationCenterAction;
    private static final Map<UUID, RouteCandidate> openStationCandidates = new HashMap<>();
    private static final Set<NoticeKey> localNotices = new HashSet<>();

    private ClientSlideController() {}

    public static void tick(Minecraft minecraft, LocalPlayer player) {
        if (minecraft.level == null || player.isSpectator() || player.getAbilities().flying || !player.isAlive() || player.isPassenger()) {
            stop(player);
            return;
        }
        if (minecraft.isPaused()) {
            freezeDuringPausedGame(player);
            return;
        }

        tickCooldown(player);
        applyPendingTeleportCommit(player);
        if (waitingTeleportSessionId != null) {
            ClientSlideMotion.freeze(player);
            return;
        }
        if (active != null) {
            continueSliding(player, active);
            return;
        }

        if (minecraft.screen instanceof SlideSafetyWarningScreen) {
            return;
        }
        if (!wantsSneakExit(player) && !wantsJumpExit(player)) {
            tryCapture(player);
        }
    }

    private static void freezeDuringPausedGame(LocalPlayer player) {
        if (active == null) {
            return;
        }
        ClientSlideMotion.freeze(player);
    }

    public static boolean isSliding() {
        return active != null;
    }

    public static void clearRouteHudNavigationStopRetention() {
        retainedRouteHudNavigationStop = null;
    }

    public static Optional<Vec3> currentSlideDirection(LocalPlayer player) {
        if (active == null) {
            return Optional.empty();
        }
        return ClientPipeNetworkCache.globalConnection(active.connectionId())
                .filter(connection -> connection.levelKey().equals(player.level().dimension()))
                .map(connection -> connection.tangentAt(active.distanceOnConnection()).scale(active.direction()).normalize());
    }

    public static Optional<ClientSlideFeedbackSnapshot> slideFeedbackSnapshot(LocalPlayer player) {
        if (active == null) {
            return Optional.empty();
        }
        return ClientPipeNetworkCache.globalConnection(active.connectionId())
                .filter(connection -> connection.levelKey().equals(player.level().dimension()))
                .map(connection -> {
                    double distance = Mth.clamp(active.distanceOnConnection(), 0.0D, connection.length());
                    Vec3 tangent = connection.tangentAt(distance).scale(active.direction()).normalize();
                    ResolvedPipeSpeedRules speedRules = ResolvedPipeSpeedRules.from(connection.resolvedAttributes());
                    boolean platformConnection = connection.platformStopId().isPresent();
                    boolean stationSlow = platformConnection
                            && activeRouteLayoutId != null
                            && (pendingStationCenterAction != null
                                    || openStationPlatformStopId != null
                                    || active.speed() <= stationSlowMaxSpeed(connection) + 0.004D);
                    return new ClientSlideFeedbackSnapshot(
                            active.sessionId(),
                            connection.id(),
                            connection.positionAt(distance),
                            tangent,
                            active.speed(),
                            speedRules.maxSpeed(),
                            distance,
                            connection.length(),
                            speedRules.highway(),
                            speedRules.accelerationAttribute(),
                            platformConnection,
                            stationSlow,
                            activeRouteLayoutId != null,
                            active.ticksSliding());
                });
    }

    public static List<SlidePreviewConnection> slidePreviewConnections(int maxConnections) {
        if (active == null || maxConnections <= 0) {
            return List.of();
        }
        Optional<PipeConnection> current = ClientPipeNetworkCache.globalConnection(active.connectionId());
        if (current.isEmpty()) {
            return List.of();
        }

        List<SlidePreviewConnection> preview = new ArrayList<>(Math.min(maxConnections, 8));
        PipeConnection currentConnection = current.get();
        preview.add(new SlidePreviewConnection(
                currentConnection,
                active.direction(),
                Mth.clamp(active.distanceOnConnection(), 0.0D, currentConnection.length())));

        if (activeRouteSectionId == null || preview.size() >= maxConnections) {
            return List.copyOf(preview);
        }

        Optional<RouteSectionPath> path = ClientRouteDataCache.routeSectionPath(activeRouteSectionId);
        if (path.isEmpty()) {
            return List.copyOf(preview);
        }
        List<PipeConnectionRef> refs = activeRouteDirection < 0 ? path.get().reverseConnections() : path.get().forwardConnections();
        if (refs.isEmpty()) {
            return List.copyOf(preview);
        }

        int currentIndex = ClientRouteDataCache.connectionIndexInSection(activeRouteSectionId, activeRouteDirection, currentConnection.id(), activeRouteConnectionIndex)
                .orElse(Math.max(0, Math.min(activeRouteConnectionIndex, refs.size() - 1)));
        PipeAnchorId exitAnchor = currentConnection.anchorForDirectionEnd(active.direction());
        for (int i = currentIndex + 1; i < refs.size() && preview.size() < maxConnections; i++) {
            Optional<PipeConnection> next = ClientPipeNetworkCache.connection(refs.get(i));
            if (next.isEmpty()) {
                break;
            }
            PipeConnection nextConnection = next.get();
            if (!nextConnection.touches(exitAnchor)) {
                break;
            }
            int direction = nextConnection.directionAwayFrom(exitAnchor);
            double startDistance = direction >= 0 ? 0.0D : nextConnection.length();
            preview.add(new SlidePreviewConnection(nextConnection, direction, startDistance));
            exitAnchor = nextConnection.anchorForDirectionEnd(direction);
        }
        return List.copyOf(preview);
    }

    public static Optional<ClientRouteHudSnapshot> routeHudSnapshot() {
        if (active == null || activeRouteLayoutId == null || activeRoutePlatformStopId == null) {
            return Optional.empty();
        }
        Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(activeRouteLayoutId);
        if (layout.isEmpty() || !layout.get().orderedPlatformStops().contains(activeRoutePlatformStopId)) {
            return Optional.empty();
        }
        RouteLayout activeLayout = layout.get();
        Optional<RouteLine> routeLine = ClientRouteDataCache.routeLine(activeLayout.routeLineId());
        Optional<PlatformStop> currentStop = ClientRouteDataCache.platformStop(activeRoutePlatformStopId);
        if (currentStop.isEmpty()) {
            return Optional.empty();
        }

        Optional<RouteStep> nextStep = nextStep(activeLayout, activeRoutePlatformStopId, activeRouteDirection);
        boolean terminal = nextStep.isEmpty() && !activeLayout.loop();
        boolean blocked = nextStep
                .map(step -> step.section().statusForDirection(activeRouteDirection) != RouteSectionStatus.VALID)
                .orElse(false);
        HudStopTiming stopTiming = routeHudStopTiming(activeLayout, activeRoutePlatformStopId, terminal, blocked);
        boolean stationFocused = stopTiming.phase() != ClientRouteHudSnapshot.StopPhase.MOVING;
        RouteHudStationSet hudStations = routeHudStations(activeLayout, activeRoutePlatformStopId, activeRouteDirection, terminal, blocked, stationFocused);
        if (hudStations.stations().isEmpty()) {
            return Optional.empty();
        }
        double progress = terminal || blocked
                ? 0.0D
                : routeHudSectionProgress(nextStep.map(step -> step.section().id()).orElse(activeRouteSectionId)).orElse(0.0D);
        if (stationFocused) {
            progress = 0.0D;
        }
        ClientRouteHudSnapshot.Status status = routeHudStatus(progress, terminal, blocked);
        List<ClientRouteHudSnapshot.TransferLine> transfers = routeHudTransfers(hudStations.focusPlatformStopId(), activeLayout.routeLineId());
        boolean stopFocused = terminal || stationFocused;
        Optional<ClientRouteHudSnapshot.NavigationStopContext> navigationStopContext = routeHudNavigationStopContext(
                activeLayout.id(),
                hudStations.focusPlatformStopId(),
                stopFocused);

        return Optional.of(new ClientRouteHudSnapshot(
                active.sessionId(),
                activeLayout.routeLineId(),
                activeLayout.id(),
                activeRouteDirection,
                status,
                routeLine.map(RouteLine::displayName).orElse(Component.translatable("screen.superpipeslide.route.default_name").getString()),
                routeLine.map(RouteLine::translatedNames).orElse(List.of()),
                routeHudLayoutName(activeLayout),
                activeLayout.bidirectional() ? Component.translatable(activeRouteDirection < 0 ? "notice.superpipeslide.slide.direction.down" : "notice.superpipeslide.slide.direction.up").getString() : "",
                routeLine.map(RouteLine::themeColors).filter(colors -> !colors.isEmpty()).orElseGet(() -> themeColors(activeLayout)),
                hudStations.stations(),
                activeRoutePlatformStopId,
                hudStations.focusPlatformStopId(),
                hudStations.stationCount(),
                hudStations.currentTravelIndex() + progress,
                progress,
                stopTiming.phase(),
                stopTiming.remainingSeconds(),
                stopTiming.progress(),
                navigationStopContext,
                transfers,
                activeLayout.loop()));
    }

    public static void clear(LocalPlayer player) {
        stop(player);
        cooldown = null;
        clearRuntimeState();
    }

    public static void resetClientSession(@Nullable LocalPlayer player) {
        if (active != null && player != null) {
            player.noPhysics = active.previousNoPhysics();
        }
        active = null;
        cooldown = null;
        ClientSlidePoseController.cancelLocalPose();
        clearRuntimeState();
        ClientGazeChoiceController.clear();
        ClientFoldTraversalEffectController.clear();
    }

    public static void acceptTeleportCommit(@Nullable LocalPlayer player, ClientboundSlideTeleportCommitPayload payload) {
        if (active != null && !active.sessionId().equals(payload.sessionId())) {
            return;
        }
        if (waitingTeleportSessionId != null && !waitingTeleportSessionId.equals(payload.sessionId())) {
            return;
        }
        pendingTeleportCommit = payload;
        if (player != null) {
            applyPendingTeleportCommit(player);
        }
    }

    public static void acceptTeleportFailed(@Nullable LocalPlayer player, ClientboundSlideTeleportFailedPayload payload) {
        if (active != null && !active.sessionId().equals(payload.sessionId())) {
            return;
        }
        ClientFoldTraversalEffectController.failTraversal();
        if (player != null && active != null) {
            detach(player, active, 20);
        }
        pendingTeleportCommit = null;
        waitingTeleportSessionId = null;
    }

    private static void acceptBranchChoice(UUID choiceId) {
        pendingBranchChoiceId = choiceId;
        openBranchNodeId = null;
        openBranchSessionId = null;
    }

    public static boolean handleLocalGazeChoice(GazeChoiceSession session, GazeChoice choice) {
        if (active == null || !session.slideSessionId().equals(active.sessionId())) {
            return false;
        }
        if (session.source() == GazeChoiceSource.BRANCH) {
            acceptBranchChoice(choice.id());
            return true;
        }
        if (session.source() == GazeChoiceSource.STATION) {
            RouteCandidate selected = openStationCandidates.get(choice.id());
            if (selected == null) {
                return false;
            }
            Optional<PlatformStop> platformStop = ClientRouteDataCache.platformStop(selected.platformStopId());
            Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(selected.layoutId());
            if (platformStop.isEmpty() || layout.isEmpty()) {
                requestDataResync();
                return false;
            }
            enterRoute(platformStop.get(), selected, active.connectionId());
            pendingStationChoiceId = choice.id();
            heldStationChoicePlatformStopId = null;
            correctActiveDepartureDirection(selected);
            openStationPlatformStopId = null;
            openStationSessionId = null;
            openStationCandidates.clear();
            return true;
        }
        return false;
    }

    private static void tryCapture(LocalPlayer player) {
        if (isCaptureSuppressedByCooldown()) {
            return;
        }

        Vec3 feet = player.position();
        double captureRadius = Config.CAPTURE_RADIUS.getAsDouble();
        double verticalTolerance = Config.CAPTURE_VERTICAL_TOLERANCE.getAsDouble();

        PipeConnection bestConnection = null;
        SlideGeometry.Projection bestProjection = null;
        double bestDistance = Double.MAX_VALUE;
        boolean navigationDeniedCapture = false;

        for (PipeConnection connection : ClientPipeNetworkCache.connectionsNear(player.level().dimension(), feet, SEARCH_RADIUS)) {
            if (isCoolingDown(connection.id())) {
                continue;
            }
            SlideGeometry.Projection projection = SlideGeometry.project(connection, feet);
            double verticalDistance = Math.abs(feet.y - projection.closestPoint().y);
            if (!projection.withinSegment(0.02D) || verticalDistance > verticalTolerance || projection.distance() > captureRadius) {
                continue;
            }
            Vec3 tangent = connection.tangentAt(projection.distanceOnConnection());
            if (!canCaptureFromCurrentMotion(player, tangent)) {
                continue;
            }
            if (!ClientNavigationController.canCaptureConnection(connection)) {
                navigationDeniedCapture = true;
                continue;
            }
            if (projection.distance() < bestDistance) {
                bestConnection = connection;
                bestProjection = projection;
                bestDistance = projection.distance();
            }
        }

        if (bestConnection == null || bestProjection == null) {
            if (navigationDeniedCapture) {
                ClientNavigationController.notifyWrongBoardingTarget(player);
            }
            return;
        }

        PipeConnection capturedConnection = bestConnection;
        SlideGeometry.Projection capturedProjection = bestProjection;
        Vec3 forward = capturedConnection.tangentAt(capturedProjection.distanceOnConnection());
        int direction = chooseDirection(player, forward);
        Optional<PlatformStop> platformStop = capturedConnection.platformStopId().flatMap(ClientRouteDataCache::platformStop);
        Optional<RouteCandidate> navigationCandidate = platformStop.flatMap(ClientNavigationController::boardingCandidate);
        Optional<ResolvedRouteCandidate> navigationBoarding = navigationCandidate.flatMap(candidate -> resolveNavigationBoardingCandidate(capturedConnection, candidate));
        if (ClientNavigationController.isNavigating() && platformStop.isPresent()) {
            if (navigationCandidate.isEmpty()) {
                logRejectedNavigationBoarding(capturedConnection, null, "no-navigation-candidate");
                ClientNavigationController.notifyBoardingRouteUnavailable(player);
                return;
            }
            if (navigationBoarding.isEmpty()) {
                ClientNavigationController.notifyBoardingRouteUnavailable(player);
                return;
            }
            direction = navigationBoarding.get().departureDirection();
        }
        List<RouteCandidate> candidates = platformStop.isPresent()
                ? stationRouteChoiceCandidates(feasibleStationCandidates(platformStop.get()), capturedConnection)
                : List.of();
        if (navigationBoarding.isPresent()) {
            candidates = List.of(navigationBoarding.get().candidate());
        }
        if (!capturedConnection.allowsSlideDirection(direction)) {
            if (navigationBoarding.isPresent()) {
                logRejectedNavigationBoarding(capturedConnection, navigationBoarding.get().candidate(), "resolved-direction-disallowed");
                ClientNavigationController.notifyBoardingRouteUnavailable(player);
                return;
            }
            if (!candidates.isEmpty() && capturedConnection.allowsSlideDirection(-direction)) {
                direction = -direction;
                candidates = platformStop.isPresent()
                        ? stationRouteChoiceCandidates(feasibleStationCandidates(platformStop.get()), capturedConnection)
                        : List.of();
            } else {
                return;
            }
        }
        if (blockForInitialSafetyWarning(player, capturedConnection)) {
            return;
        }
        StationEntryDecision stationDecision = platformStop.isPresent()
                ? StationEntryPolicy.resolve(StationEntryMode.ACTIVE_BOARDING, candidates)
                : StationEntryDecision.passThrough();
        if (platformStop.isPresent() && stationDecision.action() == StationEntryDecision.Action.AUTO_ENTER_ROUTE) {
            RouteCandidate selected = stationDecision.selectedCandidate();
            Optional<Integer> departureDirection = navigationBoarding
                    .filter(resolved -> resolved.candidate().sameRoute(selected) && resolved.candidate().sectionId().equals(selected.sectionId()))
                    .map(ResolvedRouteCandidate::departureDirection)
                    .or(() -> desiredExitDirection(capturedConnection, selected).filter(capturedConnection::allowsSlideDirection));
            if (departureDirection.isEmpty()) {
                logRejectedNavigationBoarding(capturedConnection, selected, "auto-enter-without-departure-direction");
                if (navigationBoarding.isPresent()) {
                    ClientNavigationController.notifyBoardingRouteUnavailable(player);
                }
                return;
            }
            if (navigationBoarding.isPresent()
                    && navigationBoarding.get().candidate().sameRoute(selected)
                    && navigationBoarding.get().candidate().sectionId().equals(selected.sectionId())) {
                enterRoute(platformStop.get(), selected, capturedConnection.id(), navigationBoarding.get().routeConnectionIndex());
            } else {
                enterRoute(platformStop.get(), selected, capturedConnection.id());
            }
            direction = departureDirection.get();
        }

        UUID sessionId = UUID.randomUUID();
        double speed = initialSlideSpeed(player, forward.scale(direction), capturedConnection);
        active = ClientSlideState.start(sessionId, capturedConnection.id(), direction, capturedProjection.distanceOnConnection(), speed, player.noPhysics);
        sendSlideMode(sessionId, true);
        if (platformStop.isPresent() && navigationCandidate.isPresent()) {
            ClientNavigationController.onRouteBoarded(platformStop.get(), navigationCandidate.get(), sessionId);
        }
        if (platformStop.isPresent() && stationDecision.action() == StationEntryDecision.Action.OPEN_LAYOUT_CHOICE) {
            active = active.withSpeed(0.0D);
            openStationChoice(player, active, capturedConnection, direction, platformStop.get(), stationDecision.candidates(), stationDecision.holdUntilSelected());
            ClientSlideMotion.snap(player, capturedConnection, capturedProjection.distanceOnConnection(), direction, 0.0D);
            return;
        }

        ClientSlideMotion.apply(player, capturedConnection, capturedProjection.distanceOnConnection(), direction, active.speed());
    }

    private static void continueSliding(LocalPlayer player, ClientSlideState state) {
        Optional<PipeConnection> connection = ClientPipeNetworkCache.globalConnection(state.connectionId());
        if (connection.isEmpty() || !connection.get().levelKey().equals(player.level().dimension())) {
            holdForLocalData(player, state);
            return;
        }

        PipeConnection current = connection.get();
        if (!current.allowsSlideDirection(state.direction())) {
            if (activeRouteLayoutId != null) {
                holdForLocalData(player, state);
                return;
            }
            detach(player, state, 12);
            return;
        }
        if (wantsSneakExit(player)) {
            detach(player, state, 8, true);
            return;
        }
        if (wantsJumpExit(player)) {
            detachByJump(player, state, current);
            return;
        }

        if (isHoldingAtStationCenter(state, current)) {
            holdAtStationCenter(player, state, current);
            return;
        }

        ClientSlideMotion.snap(player, current, state.distanceOnConnection(), state.direction(), state.speed());
        SlideGeometry.Projection projection = SlideGeometry.project(current, player.position());
        if (!projection.withinSegment(0.35D) || projection.distance() > MAX_SLIDE_POSITION_ERROR) {
            if (reconcileAheadFromPosition(player, state)) {
                return;
            }
            detach(player, state, 12);
            return;
        }

        boolean slowingForBranchChoice = ensureBranchChoice(player, state, current);
        boolean slowingForStationChoice = ensureStationChoice(player, state, current);
        if (isHeldForStationChoice(state, current)) {
            ClientSlideState held = state.advance(current.id(), state.direction(), Mth.clamp(state.distanceOnConnection(), 0.0D, current.length()), 0.0D);
            active = held;
            ClientSlideMotion.snap(player, current, held.distanceOnConnection(), held.direction(), 0.0D);
            return;
        }

        double stationSlowCap = stationSlowMaxSpeed(current);
        double temporaryCap = slowingForStationChoice ? stationSlowCap : Config.BRANCH_CHOICE_MAX_SPEED.getAsDouble();
        ClientSlideState speedState = rememberTemporarySpeedCapRestoreSpeed(state, slowingForBranchChoice || slowingForStationChoice, temporaryCap);
        ClientSlideState updatedState;
        if ((pendingBranchChoiceId != null || pendingStationChoiceId != null) && speedState.hasTemporarySpeedCapRestoreSpeed()) {
            updatedState = restoreTemporarySpeedCapSpeed(speedState);
        } else {
            double updatedSpeed;
            if (slowingForStationChoice) {
                updatedSpeed = approachTemporaryChoiceSpeed(speedState.speed(), stationSlowCap, Config.STATION_SLOW_DECELERATION.getAsDouble());
            } else if (slowingForBranchChoice) {
                updatedSpeed = approachTemporaryChoiceSpeed(speedState.speed(), Config.BRANCH_CHOICE_MAX_SPEED.getAsDouble(), Config.BRANCH_CHOICE_DECELERATION.getAsDouble());
            } else {
                updatedSpeed = ResolvedPipeSpeedRules.from(current.resolvedAttributes()).approach(speedState.speed());
            }
            updatedState = speedState.withSpeed(updatedSpeed);
        }
        advanceAlongPipe(player, updatedState, current);
    }

    private static boolean reconcileAheadFromPosition(LocalPlayer player, ClientSlideState state) {
        RouteProgressTracker routeProgress = RouteProgressTracker.fromActive();
        TraversalResult result = SlideTraversalStepper.advance(
                ClientPipeNetworkCache.currentView(),
                traversalContext(routeProgress),
                new TraversalCursor(state.connectionId(), state.direction(), state.distanceOnConnection()),
                Config.MAX_STEP_DISTANCE.getAsDouble(),
                ClientSlideController::nextStationCheckpoint);
        if (result.barrier().isPresent() && isStationCheckpoint(result.barrier().get().type())) {
            TraversalCursor cursor = result.barrier().get().cursor();
            Optional<PipeConnection> connection = ClientPipeNetworkCache.globalConnection(cursor.connectionId());
            if (connection.isPresent() && connection.get().levelKey().equals(player.level().dimension())) {
                active = state.advance(connection.get().id(), cursor.direction(), cursor.distanceOnConnection(), stationCheckpointSpeed(connection.get(), state.speed()));
                commitRouteProgress(routeProgress, connection.get().id());
                processStationCheckpoint(player, connection.get(), active, result.barrier().get().type());
                return true;
            }
        }
        List<TraversalEvent> stationPassEvents = new ArrayList<>();
        List<TraversalCursor> handoffCursors = new ArrayList<>();
        for (TraversalEvent event : result.events()) {
            if (event.type() == TraversalEventType.STATION_PASS_THROUGH) {
                stationPassEvents.add(event);
                continue;
            }
            if (event.type() == TraversalEventType.CONNECTION_HANDOFF) {
                handoffCursors.add(event.cursor());
            }
        }
        for (TraversalCursor cursor : handoffCursors) {
            if (reconcileToCursor(player, state, cursor)) {
                handleStationPassThroughEvents(state, stationPassEvents);
                return true;
            }
        }
        boolean reconciled = !result.cursor().connectionId().equals(state.connectionId()) && reconcileToCursor(player, state, result.cursor());
        handleStationPassThroughEvents(state, stationPassEvents);
        return reconciled;
    }

    private static boolean reconcileToCursor(LocalPlayer player, ClientSlideState state, TraversalCursor cursor) {
        Optional<PipeConnection> connection = ClientPipeNetworkCache.globalConnection(cursor.connectionId());
        if (connection.isEmpty() || !connection.get().levelKey().equals(player.level().dimension())) {
            return false;
        }
        SlideGeometry.Projection projection = SlideGeometry.project(connection.get(), player.position());
        if (!projection.withinSegment(0.85D) || projection.distance() > MAX_SLIDE_POSITION_ERROR) {
            return false;
        }
        active = state.advance(connection.get().id(), cursor.direction(), projection.distanceOnConnection(), state.speed());
        updateActiveRouteProgress(connection.get().id(), !connection.get().id().equals(state.connectionId()));
        ClientSlideMotion.apply(player, connection.get(), projection.distanceOnConnection(), cursor.direction(), active.speed());
        clearPassedStateForConnection(state.connectionId());
        clearOpenChoicesAfterHandoff();
        return true;
    }

    private static void advanceAlongPipe(LocalPlayer player, ClientSlideState state, PipeConnection startingConnection) {
        double remaining = Math.min(Math.abs(state.speed()), Config.MAX_STEP_DISTANCE.getAsDouble());
        RouteProgressTracker routeProgress = RouteProgressTracker.fromActive();
        TraversalResult result = SlideTraversalStepper.advance(
                ClientPipeNetworkCache.currentView(),
                traversalContext(routeProgress),
                new TraversalCursor(state.connectionId(), state.direction(), state.distanceOnConnection()),
                remaining,
                ClientSlideController::nextStationCheckpoint);
        TraversalCursor cursor = result.cursor();
        Optional<PipeConnection> finalConnection = ClientPipeNetworkCache.globalConnection(cursor.connectionId());
        if (finalConnection.isEmpty() || !finalConnection.get().levelKey().equals(player.level().dimension())) {
            holdForLocalData(player, state);
            return;
        }

        PipeConnection current = finalConnection.get();
        int direction = cursor.direction();
        double distance = Mth.clamp(cursor.distanceOnConnection(), 0.0D, current.length());
        double storedSpeed = speedAfterHandoff(state.speed(), current);
        ClientFoldTraversalEffectController.updateApproach(current, direction, distance, storedSpeed);
        if (result.barrier().isPresent()) {
            TraversalEvent barrier = result.barrier().get();
            if (isStationCheckpoint(barrier.type())) {
                active = state.advance(current.id(), direction, distance, stationCheckpointSpeed(current, storedSpeed));
                commitRouteProgress(routeProgress, current.id());
                processStationCheckpoint(player, current, active, barrier.type());
                return;
            }
            if (barrier.type() == TraversalEventType.FOLD_TRANSITION_REQUIRED) {
                commitRouteProgress(routeProgress, current.id());
                executeFoldTransition(player, state.advance(current.id(), direction, distance, storedSpeed), current, direction);
                return;
            }
            if (barrier.type() == TraversalEventType.BRANCH_CHOICE_REQUIRED) {
                active = state.advance(current.id(), direction, distance, 0.0D);
                ClientSlideMotion.snap(player, current, distance, direction, 0.0D);
                ensureBranchChoice(player, active, current);
                return;
            }
            if (activeRouteLayoutId != null) {
                active = state.advance(current.id(), direction, distance, 0.0D);
                ClientSlideMotion.snap(player, current, distance, direction, 0.0D);
                requestDataResync();
                return;
            }
            detachAtEnd(player, state.advance(current.id(), direction, distance, storedSpeed), current, direction);
            return;
        }

        ClientSlideMotion.apply(player, current, distance, direction, storedSpeed);
        active = state.advance(current.id(), direction, distance, storedSpeed);
        commitRouteProgress(routeProgress, current.id());
        if (!current.id().equals(state.connectionId())) {
            clearPassedStateForConnection(state.connectionId());
            clearOpenChoicesAfterHandoff();
        }
        handleStationPassThroughEvents(active, result.events());
    }

    private static void processStationCheckpoint(LocalPlayer player, PipeConnection current, ClientSlideState state, TraversalEventType checkpointType) {
        if (checkpointType == TraversalEventType.STATION_ENTRY_CHECKPOINT) {
            processStationEntryCheckpoint(player, current, state);
            return;
        }
        if (checkpointType == TraversalEventType.STATION_CENTER_CHECKPOINT) {
            processStationCenterCheckpoint(player, current, state);
            return;
        }
        ClientSlideMotion.apply(player, current, state.distanceOnConnection(), state.direction(), state.speed());
        active = state;
    }

    private static void handleStationPassThroughEvents(ClientSlideState state, List<TraversalEvent> events) {
        for (TraversalEvent event : events) {
            if (event.type() == TraversalEventType.STATION_PASS_THROUGH) {
                handleStationPassThroughEvent(state, event);
            }
        }
    }

    private static void handleStationPassThroughEvent(ClientSlideState state, TraversalEvent event) {
        if (activeRouteLayoutId == null) {
            return;
        }
        Optional<PipeConnection> connection = ClientPipeNetworkCache.globalConnection(event.cursor().connectionId());
        Optional<PlatformStop> platformStop = connection.flatMap(PipeConnection::platformStopId).flatMap(ClientRouteDataCache::platformStop);
        if (platformStop.isEmpty()) {
            return;
        }
        Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(activeRouteLayoutId);
        if (layout.isEmpty()) {
            return;
        }
        if (platformStop.get().id().equals(activeRoutePlatformStopId)) {
            return;
        }
        if (!layout.get().orderedPlatformStops().contains(platformStop.get().id())) {
            sendPassStationNotice(state, platformStop.get(), layout.get(), true);
            return;
        }
        if (!isArrivalTargetStation(layout.get(), platformStop.get().id())) {
            sendPassStationNotice(state, platformStop.get(), layout.get(), false);
        }
    }

    private static void processStationEntryCheckpoint(LocalPlayer player, PipeConnection current, ClientSlideState state) {
        Optional<PlatformStop> platformStop = current.platformStopId().flatMap(ClientRouteDataCache::platformStop);
        if (platformStop.isEmpty() || activeRouteLayoutId == null) {
            ClientSlideMotion.apply(player, current, state.distanceOnConnection(), state.direction(), state.speed());
            active = state;
            return;
        }
        Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(activeRouteLayoutId);
        if (layout.isEmpty()) {
            clearActiveRoute();
            ClientSlideMotion.apply(player, current, state.distanceOnConnection(), state.direction(), state.speed());
            active = state;
            return;
        }
        if (!layout.get().orderedPlatformStops().contains(platformStop.get().id())) {
            ClientSlideMotion.apply(player, current, state.distanceOnConnection(), state.direction(), state.speed());
            active = state;
            return;
        }

        if (!isArrivalTargetStation(layout.get(), platformStop.get().id())) {
            ClientSlideMotion.apply(player, current, state.distanceOnConnection(), state.direction(), state.speed());
            active = state;
            return;
        }

        double checkpointDistance = stationEntryDistance(current, state.direction());
        state = state.advance(current.id(), state.direction(), checkpointDistance, stationCheckpointSpeed(current, state.speed()));
        pendingStationCenterAction = null;
        activeRoutePlatformStopId = platformStop.get().id();
        activeRouteSectionId = null;
        activeRouteConnectionIndex = 0;
        ClientNavigationController.StationNavigationAction navigationAction = ClientNavigationController.stationAction(platformStop.get().id());
        if (navigationAction == ClientNavigationController.StationNavigationAction.PASS_THROUGH) {
            ClientNavigationController.onPassThroughStation(platformStop.get().id());
            sendPassStationNotice(state, platformStop.get(), layout.get(), false);
            Optional<RouteStep> next = nextStep(layout.get(), platformStop.get().id(), activeRouteDirection)
                    .filter(step -> step.section().statusForDirection(activeRouteDirection) == RouteSectionStatus.VALID);
            if (next.isPresent()) {
                setActiveRouteSection(next.get().section().id(), current.id());
                Optional<Integer> desiredDirection = desiredExitDirection(current, next.get().section(), activeRouteDirection);
                if (desiredDirection.isPresent() && desiredDirection.get() != state.direction() && current.allowsSlideDirection(desiredDirection.get())) {
                    scheduleStationCenterTurn(current, platformStop.get().id(), desiredDirection.get());
                }
            }
            active = state;
            ClientSlideMotion.apply(player, current, active.distanceOnConnection(), active.direction(), active.speed());
            return;
        }
        if (navigationAction == ClientNavigationController.StationNavigationAction.TRANSFER_STOP
                || navigationAction == ClientNavigationController.StationNavigationAction.FINAL_DESTINATION) {
            if (!ClientNavigationController.shouldSuppressGenericArrivalNotice(platformStop.get().id())) {
                sendArrivalNotice(state, platformStop.get(), layout.get());
            }
            ClientNavigationController.onSegmentStopReached(platformStop.get().id());
            scheduleStationCenterStop(current, platformStop.get().id());
            active = state;
            ClientSlideMotion.snap(player, current, state.distanceOnConnection(), state.direction(), state.speed());
            return;
        }
        sendArrivalNotice(state, platformStop.get(), layout.get());
        Optional<RouteStep> next = nextStep(layout.get(), platformStop.get().id(), activeRouteDirection);
        if (next.isEmpty() && !layout.get().loop()) {
            sendTerminalNotice(state, platformStop.get(), layout.get());
            scheduleStationCenterStop(current, platformStop.get().id());
            active = state;
            ClientSlideMotion.snap(player, current, state.distanceOnConnection(), state.direction(), state.speed());
            return;
        }
        if (next.isPresent() && next.get().section().statusForDirection(activeRouteDirection) != RouteSectionStatus.VALID) {
            sendBlockedNotice(state, platformStop.get(), next.get().nextPlatformStopId());
            scheduleStationCenterStop(current, platformStop.get().id());
            active = state;
            ClientSlideMotion.snap(player, current, state.distanceOnConnection(), state.direction(), state.speed());
            return;
        }
        if (next.isPresent()) {
            setActiveRouteSection(next.get().section().id(), current.id());
            Optional<Integer> desiredDirection = desiredExitDirection(current, next.get().section(), activeRouteDirection);
            if (desiredDirection.isPresent() && desiredDirection.get() != state.direction() && current.allowsSlideDirection(desiredDirection.get())) {
                scheduleStationCenterTurn(current, platformStop.get().id(), desiredDirection.get());
            }
        }
        active = state;
        ClientSlideMotion.snap(player, current, active.distanceOnConnection(), active.direction(), active.speed());
    }

    private static void processStationCenterCheckpoint(LocalPlayer player, PipeConnection current, ClientSlideState state) {
        StationCenterAction action = pendingStationCenterAction;
        if (action == null || !action.matches(current)) {
            ClientSlideMotion.apply(player, current, state.distanceOnConnection(), state.direction(), state.speed());
            active = state;
            return;
        }
        double center = stationCenterDistance(current);
        if (action.kind() == StationCenterAction.Kind.STOP) {
            holdAtStationCenter(player, state, current);
            return;
        }
        pendingStationCenterAction = null;
        double speed = state.speed() > 1.0E-6D ? Math.min(state.speed(), stationSlowMaxSpeed(current)) : stationSlowMaxSpeed(current);
        ClientSlideState turned = state.advance(current.id(), action.turnDirection(), center, speed);
        active = turned;
        ClientSlideMotion.snap(player, current, center, turned.direction(), turned.speed());
    }

    private static boolean isArrivalTargetStation(RouteLayout layout, UUID platformStopId) {
        if (activeRoutePlatformStopId == null || !layout.orderedPlatformStops().contains(activeRoutePlatformStopId)) {
            return layout.orderedPlatformStops().contains(platformStopId);
        }
        if (activeRoutePlatformStopId.equals(platformStopId)) {
            return false;
        }
        return RouteLayoutNavigator.nextPlatformStopId(layout, activeRoutePlatformStopId, activeRouteDirection)
                .filter(platformStopId::equals)
                .isPresent();
    }

    private static Optional<StationCheckpoint> nextStationCheckpoint(PipeConnection connection, double startDistance, int direction, double travelDistance) {
        if (activeRouteLayoutId == null) {
            return Optional.empty();
        }
        Optional<PlatformStop> platformStop = connection.platformStopId().flatMap(ClientRouteDataCache::platformStop);
        if (platformStop.isEmpty()) {
            return Optional.empty();
        }
        Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(activeRouteLayoutId);
        if (layout.isEmpty()) {
            return Optional.empty();
        }
        if (isArrivalTargetStation(layout.get(), platformStop.get().id())) {
            double entry = stationEntryDistance(connection, direction);
            if (reachesCheckpoint(startDistance, direction, travelDistance, entry, true)) {
                return Optional.of(new StationCheckpoint(entry, TraversalEventType.STATION_ENTRY_CHECKPOINT));
            }
        }
        StationCenterAction action = pendingStationCenterAction;
        if (action != null && action.matches(connection)) {
            double center = stationCenterDistance(connection);
            if (reachesCheckpoint(startDistance, direction, travelDistance, center, false)) {
                return Optional.of(new StationCheckpoint(center, TraversalEventType.STATION_CENTER_CHECKPOINT));
            }
        }
        return Optional.empty();
    }

    private static boolean isStationCheckpoint(TraversalEventType type) {
        return type == TraversalEventType.STATION_ENTRY_CHECKPOINT || type == TraversalEventType.STATION_CENTER_CHECKPOINT;
    }

    private static double stationCheckpointSpeed(PipeConnection connection, double speed) {
        Optional<UUID> platformStopId = connection.platformStopId();
        if (platformStopId.isPresent() && !ClientNavigationController.shouldSlowForPlatformStop(platformStopId.get())) {
            return speed;
        }
        return Math.min(speed, stationSlowMaxSpeed(connection));
    }

    private static void scheduleStationCenterStop(PipeConnection connection, UUID platformStopId) {
        pendingStationCenterAction = new StationCenterAction(platformStopId, connection.id(), StationCenterAction.Kind.STOP, 0);
    }

    private static void scheduleStationCenterTurn(PipeConnection connection, UUID platformStopId, int turnDirection) {
        pendingStationCenterAction = new StationCenterAction(platformStopId, connection.id(), StationCenterAction.Kind.TURN, turnDirection);
    }

    private static double stationEntryDistance(PipeConnection connection, int direction) {
        return direction > 0 ? 0.0D : connection.length();
    }

    private static double stationCenterDistance(PipeConnection connection) {
        return connection.length() * 0.5D;
    }

    private static boolean reachesCheckpoint(double startDistance, int direction, double travelDistance, double checkpointDistance, boolean includeStart) {
        if (travelDistance <= 1.0E-6D) {
            return false;
        }
        if (includeStart && Math.abs(startDistance - checkpointDistance) <= STATION_CHECKPOINT_EPSILON) {
            return true;
        }
        double endDistance = direction > 0 ? startDistance + travelDistance : startDistance - travelDistance;
        if (direction > 0) {
            return startDistance < checkpointDistance - STATION_CHECKPOINT_EPSILON && endDistance >= checkpointDistance - STATION_CHECKPOINT_EPSILON;
        }
        return startDistance > checkpointDistance + STATION_CHECKPOINT_EPSILON && endDistance <= checkpointDistance + STATION_CHECKPOINT_EPSILON;
    }

    private static double stationSlowMaxSpeed(PipeConnection connection) {
        double configured = Config.STATION_SLOW_MAX_SPEED.getAsDouble();
        double dwellLimited = Math.max(MIN_STATION_SLOW_SPEED, connection.length() / STATION_MIN_DWELL_TICKS);
        return Math.min(configured, dwellLimited);
    }

    private static TraversalContext traversalContext(RouteProgressTracker routeProgress) {
        return new TraversalContext(
                Optional.ofNullable(activeRouteLayoutId),
                activeRouteDirection,
                Optional.ofNullable(activeRoutePlatformStopId),
                routeProgress.sectionId(),
                routeProgress.connectionIndex(),
                Optional.ofNullable(pendingBranchChoiceId),
                true,
                routeProgress::routeChoiceForCurrentStep);
    }

    private static Optional<TraversalContext.RouteChoiceSelection> routeChoiceForCurrentStep(UUID layoutId, int routeDirection, Optional<UUID> currentPlatformStopId, Optional<UUID> currentRouteSectionId, int routeConnectionIndex, UUID currentConnectionId, UUID branchNodeId) {
        if (ClientRouteDataCache.isWaitingForPipeRevision(ClientPipeNetworkCache.revision())) {
            requestDataResync();
            return Optional.empty();
        }
        return ClientRouteDataCache.routeChoiceForCurrentStep(layoutId, routeDirection, currentPlatformStopId, currentRouteSectionId, routeConnectionIndex, currentConnectionId, branchNodeId);
    }

    private static void setActiveRouteSection(UUID routeSectionId, UUID currentConnectionId) {
        setActiveRouteSection(routeSectionId, currentConnectionId, -1);
    }

    private static void setActiveRouteSection(UUID routeSectionId, UUID currentConnectionId, int preferredConnectionIndex) {
        activeRouteSectionId = routeSectionId;
        activeRouteConnectionIndex = matchingConnectionIndex(routeSectionId, activeRouteDirection, currentConnectionId, preferredConnectionIndex)
                .orElseGet(() -> ClientRouteDataCache.connectionIndexInSection(routeSectionId, activeRouteDirection, currentConnectionId, 0).orElse(0));
    }

    private static Optional<Integer> matchingConnectionIndex(UUID routeSectionId, int routeDirection, UUID currentConnectionId, int preferredConnectionIndex) {
        if (preferredConnectionIndex < 0) {
            return Optional.empty();
        }
        return ClientRouteDataCache.routeSectionPath(routeSectionId)
                .map(path -> routeDirection < 0 ? path.reverseConnections() : path.forwardConnections())
                .filter(refs -> preferredConnectionIndex < refs.size())
                .filter(refs -> refs.get(preferredConnectionIndex).connectionId().equals(currentConnectionId))
                .map(ignored -> preferredConnectionIndex);
    }

    private static void commitRouteProgress(RouteProgressTracker routeProgress, UUID finalConnectionId) {
        if (activeRouteLayoutId == null || routeProgress.sectionId().isEmpty()) {
            return;
        }
        activeRouteSectionId = routeProgress.sectionId().get();
        activeRouteConnectionIndex = routeProgress.connectionIndex();
        updateActiveRouteProgress(finalConnectionId, true);
    }

    private static void updateActiveRouteProgress(UUID connectionId, boolean preferForwardProgress) {
        if (activeRouteSectionId == null) {
            return;
        }
        int searchFrom = preferForwardProgress ? activeRouteConnectionIndex + 1 : activeRouteConnectionIndex;
        ClientRouteDataCache.connectionIndexInSection(activeRouteSectionId, activeRouteDirection, connectionId, searchFrom)
                .ifPresent(index -> activeRouteConnectionIndex = index);
    }

    private static final class RouteProgressTracker {
        private final Optional<UUID> sectionId;
        private int connectionIndex;

        private RouteProgressTracker(Optional<UUID> sectionId, int connectionIndex) {
            this.sectionId = sectionId;
            this.connectionIndex = Math.max(0, connectionIndex);
        }

        static RouteProgressTracker fromActive() {
            return new RouteProgressTracker(Optional.ofNullable(activeRouteSectionId), activeRouteConnectionIndex);
        }

        Optional<UUID> sectionId() {
            return this.sectionId;
        }

        int connectionIndex() {
            return this.connectionIndex;
        }

        Optional<TraversalContext.RouteChoiceSelection> routeChoiceForCurrentStep(UUID layoutId, int routeDirection, Optional<UUID> currentPlatformStopId, Optional<UUID> currentRouteSectionId, int ignoredRouteConnectionIndex, UUID currentConnectionId, UUID branchNodeId) {
            Optional<TraversalContext.RouteChoiceSelection> selected = ClientSlideController.routeChoiceForCurrentStep(layoutId, routeDirection, currentPlatformStopId, this.sectionId.or(() -> currentRouteSectionId), this.connectionIndex, currentConnectionId, branchNodeId);
            selected.ifPresent(selection -> this.connectionIndex = selection.selectedConnectionIndex());
            return selected;
        }
    }

    private static boolean ensureBranchChoice(LocalPlayer player, ClientSlideState state, PipeConnection current) {
        Optional<BranchNode> branchNode = ClientPipeNetworkCache.currentBranchNodeManagingConnection(current.id());
        if (branchNode.isEmpty() || !branchNode.get().isCompleteForChoice()) {
            return false;
        }
        BranchNode branch = branchNode.get();
        if (!current.anchorForDirectionEnd(state.direction()).equals(branch.anchorId())) {
            return false;
        }
        if (pendingBranchChoiceId != null && branch.referencesConnection(pendingBranchChoiceId)) {
            return false;
        }
        if (activeRouteLayoutId != null) {
            return false;
        }

        double distanceToBranch = state.direction() > 0 ? current.length() - state.distanceOnConnection() : state.distanceOnConnection();
        if (distanceToBranch > Config.BRANCH_CHOICE_PREVIEW_DISTANCE.getAsDouble()) {
            return false;
        }

        List<PipeConnection> options = ClientPipeNetworkCache.connectionsTouching(branch.anchorId()).stream()
                .filter(connection -> !connection.id().equals(current.id()) && branch.referencesConnection(connection.id()))
                .filter(connection -> canLeaveBranch(connection, branch.anchorId()))
                .toList();
        if (options.size() < 2) {
            return false;
        }
        if (!branch.id().equals(openBranchNodeId) || openBranchSessionId == null) {
            openBranchChoice(player, state, current, branch, options);
        }
        return true;
    }

    private static boolean ensureStationChoice(LocalPlayer player, ClientSlideState state, PipeConnection current) {
        if (current.platformStopId().isEmpty() || pendingStationChoiceId != null) {
            return false;
        }
        Optional<PlatformStop> stop = current.platformStopId().flatMap(ClientRouteDataCache::platformStop);
        if (stop.isEmpty()) {
            return false;
        }
        if (activeRouteLayoutId != null) {
            Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(activeRouteLayoutId);
            if (layout.isEmpty()) {
                return false;
            }
            if (!ClientNavigationController.shouldSlowForPlatformStop(stop.get().id())) {
                return false;
            }
            if (activeRoutePlatformStopId == null) {
                return ClientRouteDataCache.routeLayoutIdsForPlatformStop(stop.get().id()).contains(activeRouteLayoutId);
            }
            Optional<UUID> nextPlatformStopId = RouteLayoutNavigator.nextPlatformStopId(layout.get(), activeRoutePlatformStopId, activeRouteDirection);
            return stop.get().id().equals(activeRoutePlatformStopId) || nextPlatformStopId.map(stop.get().id()::equals).orElse(false);
        }
        List<RouteCandidate> candidates = feasibleStationCandidates(stop.get());
        boolean stationDeparture = state.ticksSliding() == 0 && state.startedConnectionId().equals(current.id());
        candidates = stationRouteChoiceCandidates(candidates, current);
        StationEntryDecision stationDecision = StationEntryPolicy.resolve(StationEntryMode.FREE_SLIDE_ENTRY, candidates);
        if (stationDecision.action() != StationEntryDecision.Action.OPEN_LAYOUT_CHOICE) {
            return false;
        }
        if (!stop.get().id().equals(openStationPlatformStopId) || openStationSessionId == null) {
            openStationChoice(player, state, current, state.direction(), stop.get(), stationDecision.candidates(), stationDecision.holdUntilSelected() && stationDeparture);
        }
        return true;
    }

    private static boolean isHeldForStationChoice(ClientSlideState state, PipeConnection current) {
        return heldStationChoicePlatformStopId != null
                && current.platformStopId().filter(heldStationChoicePlatformStopId::equals).isPresent()
                && active != null
                && active.sessionId().equals(state.sessionId());
    }

    private static boolean isHoldingAtStationCenter(ClientSlideState state, PipeConnection current) {
        return pendingStationCenterAction != null
                && pendingStationCenterAction.kind() == StationCenterAction.Kind.STOP
                && pendingStationCenterAction.matches(current)
                && Math.abs(state.distanceOnConnection() - stationCenterDistance(current)) <= STATION_CHECKPOINT_EPSILON
                && active != null
                && active.sessionId().equals(state.sessionId());
    }

    private static void holdAtStationCenter(LocalPlayer player, ClientSlideState state, PipeConnection current) {
        double stationCenter = stationCenterDistance(current);
        ClientSlideState held = state.advance(current.id(), state.direction(), stationCenter, 0.0D);
        active = held;
        ClientSlideMotion.snap(player, current, stationCenter, held.direction(), 0.0D);
    }

    private static void openBranchChoice(LocalPlayer player, ClientSlideState state, PipeConnection incomingConnection, BranchNode branch, List<PipeConnection> options) {
        Vec3 forward = safeNormalize(incomingConnection.tangentAt(state.direction() > 0 ? incomingConnection.length() : 0.0D).scale(state.direction()), new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        UUID defaultChoiceId = foremostConnection(branch, incomingConnection, state.direction())
                .map(PipeConnection::id)
                .or(() -> branch.defaultAlternativeTo(incomingConnection.id()).filter(connectionId -> options.stream().anyMatch(option -> option.id().equals(connectionId))))
                .orElse(options.getFirst().id());
        List<GazeChoice> choices = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            choices.add(branchChoiceFor(branch, options.get(i), i, options.get(i).id().equals(defaultChoiceId), forward, up));
        }
        GazeChoiceSession session = new GazeChoiceSession(
                UUID.randomUUID(),
                state.sessionId(),
                player.getUUID(),
                GazeChoiceSource.BRANCH,
                choices,
                defaultChoiceId,
                BRANCH_REQUIRED_FOCUS_TICKS,
                new GazeChoiceExpireCondition(GazeChoiceExpireType.PASS_BRANCH_NODE, BRANCH_SESSION_TTL_TICKS));
        openBranchNodeId = branch.id();
        openBranchSessionId = session.sessionId();
        ClientGazeChoiceController.openLocal(session);
    }

    private static GazeChoice branchChoiceFor(BranchNode branch, PipeConnection connection, int index, boolean defaultChoice, Vec3 forward, Vec3 up) {
        Vec3 choicePosition = choicePositionAboveConnection(connection, branch.anchorId(), forward);
        Vec3 worldDirection = choicePosition.subtract(branch.position());
        Vec3 localDirection = worldToLocal(safeNormalize(worldDirection, directionAwayFrom(connection, branch.anchorId())), forward, up);
        Component label = defaultChoice
                ? Component.translatable("gaze.superpipeslide.branch.default")
                : Component.translatable("gaze.superpipeslide.branch.exit", index + 1);
        return new GazeChoice(
                connection.id(),
                GazeChoicePlacement.worldFrame(branch.position(), forward, up, worldToLocal(choicePosition.subtract(branch.position()), forward, up)),
                GazeChoiceShape.arrow(localDirection),
                label,
                directionLabel(localDirection),
                defaultChoice ? List.of(DEFAULT_BRANCH_EXIT_COLOR, 0xFF7CCBFF) : List.of(BRANCH_EXIT_COLOR),
                defaultChoice,
                Math.cos(Math.toRadians(12.0D)));
    }

    private static void openStationChoice(LocalPlayer player, ClientSlideState state, PipeConnection current, int direction, PlatformStop platformStop, List<RouteCandidate> candidates, boolean holdUntilSelected) {
        if (candidates.isEmpty()) {
            return;
        }
        if (candidates.size() > 1) {
            sendLocalNoticeOnce(state, platformStop.id(), "multi", ClientboundSlideNoticePayload.Kind.CHOICE, List.of(0xFF47A6FF),
                    Component.translatable("notice.superpipeslide.slide.multiple_directions.title"),
                    List.of(line(Component.translatable("notice.superpipeslide.slide.multiple_directions.body"))));
        }
        Map<UUID, RouteCandidate> candidateByChoiceId = new HashMap<>();
        List<GazeChoice> choices = new ArrayList<>();
        double x = -0.72D * Math.max(0, candidates.size() - 1) / 2.0D;
        RouteCandidate preferred = preferredCandidate(candidates, current, direction);
        for (RouteCandidate candidate : candidates) {
            UUID choiceId = candidateChoiceId(platformStop.id(), candidate);
            candidateByChoiceId.put(choiceId, candidate);
            List<Integer> colors = ClientRouteDataCache.routeLayout(candidate.layoutId()).map(ClientSlideController::themeColors).orElse(List.of(ROUTE_COLOR));
            choices.add(new GazeChoice(
                    choiceId,
                    GazeChoicePlacement.slideFrame(new Vec3(x, 1.05D, 3.2D)),
                    GazeChoiceShape.arrow(new Vec3(0.0D, 0.0D, 1.0D)),
                    stationChoiceLabel(candidate, candidates),
                    Component.translatable("gaze.superpipeslide.station.layout_direction_detail"),
                    colors,
                    candidate.sameRoute(preferred),
                    Math.cos(Math.toRadians(18.0D))));
            x += 0.72D;
            if (choices.size() >= GazeChoiceSession.MAX_CHOICES) {
                break;
            }
        }
        if (choices.isEmpty()) {
            return;
        }
        UUID preferredChoiceId = candidateChoiceId(platformStop.id(), preferred);
        UUID defaultChoiceId = choices.stream().anyMatch(choice -> choice.id().equals(preferredChoiceId)) ? preferredChoiceId : choices.getFirst().id();
        GazeChoiceSession session = new GazeChoiceSession(
                UUID.randomUUID(),
                state.sessionId(),
                player.getUUID(),
                GazeChoiceSource.STATION,
                choices,
                defaultChoiceId,
                STATION_REQUIRED_FOCUS_TICKS,
                new GazeChoiceExpireCondition(GazeChoiceExpireType.PASS_STATION, STATION_SESSION_TTL_TICKS));
        openStationPlatformStopId = platformStop.id();
        openStationSessionId = session.sessionId();
        openStationCandidates.clear();
        openStationCandidates.putAll(candidateByChoiceId);
        heldStationChoicePlatformStopId = holdUntilSelected ? platformStop.id() : null;
        ClientGazeChoiceController.openLocal(session);
    }

    private static void enterRoute(PlatformStop platformStop, RouteCandidate candidate, UUID currentConnectionId) {
        enterRoute(platformStop, candidate, currentConnectionId, -1);
    }

    private static void enterRoute(PlatformStop platformStop, RouteCandidate candidate, UUID currentConnectionId, int preferredRouteConnectionIndex) {
        retainedRouteHudNavigationStop = null;
        activeRouteLayoutId = candidate.layoutId();
        activeRouteDirection = candidate.routeDirection();
        activeRoutePlatformStopId = platformStop.id();
        setActiveRouteSection(candidate.sectionId(), currentConnectionId, preferredRouteConnectionIndex);
        pendingStationCenterAction = null;
        Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(candidate.layoutId());
        if (layout.isEmpty()) {
            return;
        }
        sendLocalNotice(ClientboundSlideNoticePayload.Kind.ENTER_ROUTE, themeColors(layout.get()),
                Component.translatable("notice.superpipeslide.slide.entered", routeLabel(candidate.layoutId(), candidate.routeDirection())),
                List.of());
        furthestReachableBeforeBreak(layout.get(), platformStop.id(), candidate.routeDirection()).ifPresent(furthest -> sendLocalNotice(
                ClientboundSlideNoticePayload.Kind.WARNING,
                List.of(0xFFFFB13B),
                Component.translatable("notice.superpipeslide.slide.layout_broken", stationName(furthest)),
                List.of()));
    }

    private static void correctActiveDepartureDirection(RouteCandidate selected) {
        if (active == null) {
            return;
        }
        Optional<PipeConnection> currentConnection = ClientPipeNetworkCache.globalConnection(active.connectionId());
        if (currentConnection.isEmpty()) {
            return;
        }
        Optional<Integer> desiredDirection = desiredExitDirection(currentConnection.get(), selected);
        if (desiredDirection.isPresent() && currentConnection.get().allowsSlideDirection(desiredDirection.get())) {
            active = active.advance(currentConnection.get().id(), desiredDirection.get(), active.distanceOnConnection(), Math.max(0.08D, active.speed()));
        } else {
            active = active.withSpeed(Math.max(0.08D, active.speed()));
        }
    }

    private static void executeFoldTransition(LocalPlayer player, ClientSlideState state, PipeConnection connection, int direction) {
        PipeAnchorId exitAnchor = connection.anchorForDirectionEnd(direction);
        Optional<PipeAnchorId> targetAnchor = ClientPipeNetworkCache.globalFoldCounterpart(exitAnchor);
        boolean crossDimension = targetAnchor.map(anchor -> !anchor.levelKey().equals(player.level().dimension())).orElse(false);
        ClientFoldTraversalEffectController.beginTraversal(connection, direction, state.speed(), crossDimension);
        if (targetAnchor.isEmpty()) {
            ClientFoldTraversalEffectController.failTraversal();
            detach(player, state, 30);
            return;
        }
        List<PipeConnection> targetConnections = ClientPipeNetworkCache.connectionsTouching(targetAnchor.get());
        if (targetConnections.isEmpty()) {
            Vec3 targetPosition = Vec3.atCenterOf(targetAnchor.get().blockPos());
            if (targetAnchor.get().levelKey().equals(player.level().dimension())) {
                player.setPos(targetPosition.x, targetPosition.y, targetPosition.z);
                ClientFoldTraversalEffectController.completeTraversal(targetAnchor.get().levelKey(), targetPosition, connection.tangentAt(direction > 0 ? connection.length() : 0.0D).scale(direction), state.speed());
                sendFoldPositionSync(state.withSpeed(0.0D), targetAnchor.get().levelKey(), targetPosition, Optional.empty(), 1, 0.0D, 0.0D);
                detach(player, state, 30);
            } else {
                requestFoldTeleport(player, state.withSpeed(0.0D), targetAnchor.get().levelKey(), targetPosition, Optional.empty(), 1, 0.0D, 0.0D);
            }
            return;
        }
        if (targetConnections.size() != 1) {
            ClientFoldTraversalEffectController.failTraversal();
            detach(player, state, 30);
            return;
        }
        PipeConnection targetConnection = targetConnections.getFirst();
        int targetDirection = targetConnection.directionAwayFrom(targetAnchor.get());
        if (!targetConnection.allowsSlideDirection(targetDirection)) {
            ClientFoldTraversalEffectController.failTraversal();
            detach(player, state, 30);
            return;
        }
        double targetDistance = targetDirection > 0
                ? Math.min(FOLD_ANCHOR_EXIT_OFFSET, targetConnection.length())
                : Math.max(0.0D, targetConnection.length() - FOLD_ANCHOR_EXIT_OFFSET);
        double targetSpeed = speedAfterHandoff(state.speed(), targetConnection);
        Vec3 targetPosition = targetConnection.positionAt(targetDistance);
        if (targetConnection.levelKey().equals(player.level().dimension())) {
            active = state.advance(targetConnection.id(), targetDirection, targetDistance, targetSpeed);
            updateActiveRouteProgress(targetConnection.id(), true);
            ClientSlideMotion.apply(player, targetConnection, targetDistance, targetDirection, targetSpeed);
            ClientFoldTraversalEffectController.completeTraversal(targetConnection.levelKey(), targetPosition, targetConnection.tangentAt(targetDistance).scale(targetDirection), targetSpeed);
            clearOpenChoicesAfterHandoff();
            sendFoldPositionSync(active, targetConnection.levelKey(), targetPosition, Optional.of(targetConnection.id()), targetDirection, targetDistance, targetSpeed);
            return;
        }
        requestFoldTeleport(player, state.withSpeed(0.0D), targetConnection.levelKey(), targetPosition, Optional.of(targetConnection.id()), targetDirection, targetDistance, targetSpeed);
    }

    private static void requestFoldTeleport(LocalPlayer player, ClientSlideState state, ResourceKey<Level> targetLevel, Vec3 targetPosition, Optional<UUID> targetConnectionId, int direction, double distanceOnConnection, double speed) {
        active = state;
        waitingTeleportSessionId = state.sessionId();
        ClientSlideMotion.freeze(player);
        ClientGazeChoiceController.clear();
        sendFoldTeleportRequest(state.sessionId(), targetLevel, targetPosition, targetConnectionId, direction, distanceOnConnection, speed);
    }

    private static void sendFoldPositionSync(ClientSlideState state, ResourceKey<Level> targetLevel, Vec3 targetPosition, Optional<UUID> targetConnectionId, int direction, double distanceOnConnection, double speed) {
        sendFoldTeleportRequest(state.sessionId(), targetLevel, targetPosition, targetConnectionId, direction, distanceOnConnection, speed);
    }

    private static void sendFoldTeleportRequest(UUID sessionId, ResourceKey<Level> targetLevel, Vec3 targetPosition, Optional<UUID> targetConnectionId, int direction, double distanceOnConnection, double speed) {
        ClientPacketDistributor.sendToServer(new ServerboundSlideTeleportRequestPayload(
                sessionId,
                targetLevel,
                targetPosition.x,
                targetPosition.y,
                targetPosition.z,
                targetConnectionId,
                direction,
                distanceOnConnection,
                speed));
    }

    private static void applyPendingTeleportCommit(LocalPlayer player) {
        if (pendingTeleportCommit == null) {
            return;
        }
        ClientboundSlideTeleportCommitPayload payload = pendingTeleportCommit;
        if (waitingTeleportSessionId == null) {
            pendingTeleportCommit = null;
            return;
        }
        if (!player.level().dimension().equals(payload.targetLevel())) {
            ClientSlideMotion.freeze(player);
            return;
        }
        Vec3 targetPosition = new Vec3(payload.x(), payload.y(), payload.z());
        if (payload.targetConnectionId().isEmpty()) {
            player.setPos(targetPosition.x, targetPosition.y, targetPosition.z);
            ClientFoldTraversalEffectController.completeTraversal(payload.targetLevel(), targetPosition, new Vec3(0.0D, 0.0D, 1.0D), payload.speed());
            if (active != null) {
                sendSlideMode(active.sessionId(), false);
                player.noPhysics = active.previousNoPhysics();
            }
            active = null;
            ClientSlidePoseController.cancelLocalPose();
            pendingTeleportCommit = null;
            waitingTeleportSessionId = null;
            clearRuntimeState();
            return;
        }
        Optional<PipeConnection> connection = ClientPipeNetworkCache.globalConnection(payload.targetConnectionId().get());
        if (connection.isEmpty() || !connection.get().levelKey().equals(payload.targetLevel())) {
            requestDataResync();
            ClientSlideMotion.freeze(player);
            return;
        }
        boolean previousNoPhysics = active == null ? player.noPhysics : active.previousNoPhysics();
        active = ClientSlideState.start(payload.sessionId(), connection.get().id(), payload.direction(), payload.distanceOnConnection(), payload.speed(), previousNoPhysics);
        updateActiveRouteProgress(connection.get().id(), true);
        pendingStationCenterAction = null;
        player.setPos(targetPosition.x, targetPosition.y, targetPosition.z);
        ClientSlideMotion.apply(player, connection.get(), payload.distanceOnConnection(), payload.direction(), payload.speed());
        ClientFoldTraversalEffectController.completeTraversal(payload.targetLevel(), targetPosition, connection.get().tangentAt(payload.distanceOnConnection()).scale(payload.direction()), payload.speed());
        pendingTeleportCommit = null;
        waitingTeleportSessionId = null;
        clearOpenChoicesAfterHandoff();
    }

    private static void holdForLocalData(LocalPlayer player, ClientSlideState state) {
        active = state.withSpeed(0.0D);
        ClientSlideMotion.freeze(player);
        requestDataResync();
    }

    private static void detach(LocalPlayer player, ClientSlideState state, int cooldownTicks) {
        detach(player, state, cooldownTicks, false);
    }

    private static void detach(LocalPlayer player, ClientSlideState state, int cooldownTicks, boolean requireExit) {
        ClientPipeNetworkCache.globalConnection(state.connectionId())
                .ifPresent(connection -> ClientNavigationController.onSlideDetached(player, state, connection, requireExit ? ClientNavigationController.DetachReason.SNEAK : ClientNavigationController.DetachReason.OTHER));
        sendSlideMode(state.sessionId(), false);
        ClientSlidePoseController.beginDismount(ClientSlidePoseController.DismountKind.STEP);
        active = null;
        clearRuntimeState();
        ClientGazeChoiceController.clear();
        ClientFoldTraversalEffectController.clear();
        player.noPhysics = state.previousNoPhysics();
        cooldown = new CaptureCooldown(state.connectionId(), cooldownTicks, requireExit);
    }

    private static void detachByJump(LocalPlayer player, ClientSlideState state, PipeConnection connection) {
        Vec3 tangent = connection.tangentAt(state.distanceOnConnection()).scale(state.direction());
        ClientNavigationController.onSlideDetached(player, state, connection, ClientNavigationController.DetachReason.JUMP);
        sendSlideMode(state.sessionId(), false);
        ClientSlidePoseController.beginDismount(ClientSlidePoseController.DismountKind.FLIP);
        active = null;
        clearRuntimeState();
        ClientGazeChoiceController.clear();
        ClientFoldTraversalEffectController.clear();
        player.noPhysics = state.previousNoPhysics();
        player.setDeltaMovement(tangent.scale(Math.max(state.speed(), Config.NORMAL_MAX_SPEED.getAsDouble())).add(0.0D, 0.12D, 0.0D));
        player.jumpFromGround();
        cooldown = new CaptureCooldown(connection.id(), 12, true);
    }

    private static void detachAtEnd(LocalPlayer player, ClientSlideState state, PipeConnection connection, int direction) {
        PipeAnchorId exitAnchor = connection.anchorForDirectionEnd(direction);
        if (ClientPipeNetworkCache.foldAnchorAt(exitAnchor).isPresent()) {
            executeFoldTransition(player, state.advance(connection.id(), direction, direction > 0 ? connection.length() : 0.0D, state.speed()), connection, direction);
            return;
        }
        Vec3 tangent = connection.tangentAt(direction > 0 ? connection.length() : 0.0D).scale(direction);
        player.setDeltaMovement(tangent.scale(Math.max(Config.NORMAL_MAX_SPEED.getAsDouble() * 0.75D, 0.18D)).add(0.0D, 0.04D, 0.0D));
        detach(player, state.advance(connection.id(), direction, direction > 0 ? connection.length() : 0.0D, state.speed()), 30);
    }

    private static double initialSlideSpeed(LocalPlayer player, Vec3 direction, PipeConnection connection) {
        double projectedSpeed = Math.max(0.0D, player.getDeltaMovement().dot(direction));
        double maxSpeed = ResolvedPipeSpeedRules.from(connection.resolvedAttributes()).maxSpeed();
        return Mth.clamp(Math.max(Config.INITIAL_SLIDE_SPEED.getAsDouble(), projectedSpeed), Config.INITIAL_SLIDE_SPEED.getAsDouble(), maxSpeed);
    }

    private static double speedAfterHandoff(double speed, PipeConnection current) {
        ResolvedPipeSpeedRules rules = ResolvedPipeSpeedRules.from(current.resolvedAttributes());
        return speed > rules.maxSpeed() ? rules.approach(speed) : speed;
    }

    private static double approachTemporaryChoiceSpeed(double speed, double cap, double deceleration) {
        if (speed < cap) {
            return Math.min(cap, speed + Config.NORMAL_ACCELERATION.getAsDouble());
        }
        return Math.max(cap, speed - deceleration);
    }

    private static ClientSlideState rememberTemporarySpeedCapRestoreSpeed(ClientSlideState state, boolean active, double speedCap) {
        if (!active || state.speed() <= speedCap) {
            return state;
        }
        return state.rememberTemporarySpeedCapRestoreSpeed(state.speed());
    }

    private static ClientSlideState restoreTemporarySpeedCapSpeed(ClientSlideState state) {
        if (!state.hasTemporarySpeedCapRestoreSpeed()) {
            return state;
        }
        return state.withSpeed(Math.max(state.speed(), state.temporarySpeedCapRestoreSpeed())).clearTemporarySpeedCapRestoreSpeed();
    }

    private static void stop(LocalPlayer player) {
        if (active != null) {
            sendSlideMode(active.sessionId(), false);
            player.noPhysics = active.previousNoPhysics();
            active = null;
        }
        ClientSlidePoseController.cancelLocalPose();
        clearRuntimeState();
        ClientGazeChoiceController.clear();
        ClientFoldTraversalEffectController.clear();
    }

    private static void sendSlideMode(UUID sessionId, boolean sliding) {
        ClientPacketDistributor.sendToServer(new ServerboundSlideModePayload(sessionId, sliding));
    }

    private static void requestDataResync() {
        ClientDataResyncRequests.requestPipeAndRouteFromServer();
    }

    private static void clearRuntimeState() {
        pendingBranchChoiceId = null;
        pendingStationChoiceId = null;
        activeRouteLayoutId = null;
        activeRoutePlatformStopId = null;
        activeRouteSectionId = null;
        activeRouteConnectionIndex = 0;
        activeRouteDirection = 1;
        retainedRouteHudNavigationStop = null;
        pendingTeleportCommit = null;
        waitingTeleportSessionId = null;
        clearOpenChoicesAfterHandoff();
        heldStationChoicePlatformStopId = null;
        pendingStationCenterAction = null;
        localNotices.clear();
    }

    private static void clearActiveRoute() {
        activeRouteLayoutId = null;
        activeRoutePlatformStopId = null;
        activeRouteSectionId = null;
        activeRouteConnectionIndex = 0;
        activeRouteDirection = 1;
        retainedRouteHudNavigationStop = null;
        pendingStationCenterAction = null;
    }

    private static void clearOpenChoicesAfterHandoff() {
        openBranchNodeId = null;
        openBranchSessionId = null;
        openStationPlatformStopId = null;
        openStationSessionId = null;
        openStationCandidates.clear();
        heldStationChoicePlatformStopId = null;
        pendingBranchChoiceId = null;
        pendingStationChoiceId = null;
        ClientGazeChoiceController.clear();
    }

    private static void clearPassedStateForConnection(UUID connectionId) {
        if (pendingStationCenterAction != null && pendingStationCenterAction.connectionId().equals(connectionId)) {
            pendingStationCenterAction = null;
        }
        ClientPipeNetworkCache.globalConnection(connectionId)
                .flatMap(PipeConnection::platformStopId)
                .ifPresent(platformStopId -> localNotices.removeIf(key -> key.platformStopId().equals(platformStopId)));
    }

    private static ClientRouteHudSnapshot.Status routeHudStatus(double progress, boolean terminal, boolean blocked) {
        if (waitingTeleportSessionId != null) {
            return ClientRouteHudSnapshot.Status.FOLD_TRANSIT;
        }
        if (blocked) {
            return ClientRouteHudSnapshot.Status.BLOCKED;
        }
        if (terminal) {
            return ClientRouteHudSnapshot.Status.TERMINAL;
        }
        if (active != null && active.speed() <= 0.012D && progress >= 0.92D) {
            return ClientRouteHudSnapshot.Status.ARRIVED;
        }
        return progress >= 0.78D ? ClientRouteHudSnapshot.Status.APPROACHING : ClientRouteHudSnapshot.Status.CRUISING;
    }

    private static Optional<ClientRouteHudSnapshot.NavigationStopContext> routeHudNavigationStopContext(UUID routeLayoutId, UUID focusPlatformStopId, boolean stopFocused) {
        Optional<ClientRouteHudSnapshot.NavigationStopContext> live = stopFocused
                ? ClientNavigationController.routeHudStopContext(focusPlatformStopId)
                : Optional.empty();
        if (live.isPresent()) {
            retainedRouteHudNavigationStop = new RetainedRouteHudNavigationStop(routeLayoutId, live.get());
            return live;
        }
        if (!stopFocused || retainedRouteHudNavigationStop == null || !retainedRouteHudNavigationStop.routeLayoutId().equals(routeLayoutId)) {
            retainedRouteHudNavigationStop = null;
            return Optional.empty();
        }
        ClientRouteHudSnapshot.NavigationStopContext retained = retainedRouteHudNavigationStop.context();
        if (retained.platformStopId().equals(focusPlatformStopId)) {
            return Optional.of(retained);
        }
        Optional<UUID> focusStationGroupId = ClientRouteDataCache.platformStop(focusPlatformStopId).map(PlatformStop::stationGroupId);
        if (focusStationGroupId.isPresent() && focusStationGroupId.get().equals(retained.stationGroupId())) {
            return Optional.of(new ClientRouteHudSnapshot.NavigationStopContext(
                    retained.kind(),
                    focusPlatformStopId,
                    retained.stationGroupId(),
                    retained.colors()));
        }
        retainedRouteHudNavigationStop = null;
        return Optional.empty();
    }

    private static HudStopTiming routeHudStopTiming(RouteLayout layout, UUID currentVisibleStopId, boolean terminal, boolean blocked) {
        if (active == null || terminal || blocked || waitingTeleportSessionId != null) {
            return HudStopTiming.moving();
        }
        Optional<PipeConnection> currentConnection = ClientPipeNetworkCache.globalConnection(active.connectionId());
        if (currentConnection.isEmpty()) {
            return HudStopTiming.moving();
        }
        Optional<UUID> platformStopId = currentConnection.get().platformStopId();
        if (platformStopId.isEmpty() || !platformStopId.get().equals(currentVisibleStopId)) {
            return HudStopTiming.moving();
        }
        if (!layout.orderedPlatformStops().contains(platformStopId.get())) {
            return HudStopTiming.moving();
        }
        if (nextStep(layout, platformStopId.get(), activeRouteDirection).isEmpty() && !layout.loop()) {
            return HudStopTiming.moving();
        }

        PipeConnection connection = currentConnection.get();
        double speed = stationSlowMaxSpeed(connection);
        if (speed <= 1.0E-6D) {
            return HudStopTiming.moving();
        }
        double remainingDistance = active.direction() >= 0
                ? Math.max(0.0D, connection.length() - active.distanceOnConnection())
                : Math.max(0.0D, active.distanceOnConnection());
        double totalSeconds = connection.length() / speed / 20.0D;
        double remainingSeconds = remainingDistance / speed / 20.0D;
        double progress = totalSeconds <= 1.0E-6D ? 1.0D : 1.0D - Mth.clamp(remainingSeconds / totalSeconds, 0.0D, 1.0D);
        ClientRouteHudSnapshot.StopPhase phase = remainingSeconds <= 1.2D
                ? ClientRouteHudSnapshot.StopPhase.DEPARTING
                : ClientRouteHudSnapshot.StopPhase.DOCKING;
        return new HudStopTiming(phase, remainingSeconds, progress);
    }

    private static String routeHudLayoutName(RouteLayout layout) {
        if (layout.displayName().isPresent()) {
            return layout.displayName().get();
        }
        return Component.translatable("screen.superpipeslide.layout.default_name").getString();
    }

    private static Optional<Double> routeHudSectionProgress(@Nullable UUID preferredSectionId) {
        if (active == null) {
            return Optional.empty();
        }
        UUID sectionId = preferredSectionId == null ? activeRouteSectionId : preferredSectionId;
        if (sectionId == null) {
            return Optional.empty();
        }
        Optional<RouteSectionPath> path = ClientRouteDataCache.routeSectionPath(sectionId);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        List<PipeConnectionRef> refs = activeRouteDirection < 0 ? path.get().reverseConnections() : path.get().forwardConnections();
        if (refs.isEmpty()) {
            return Optional.empty();
        }
        Optional<RouteSection> section = ClientRouteDataCache.routeSection(sectionId);
        Optional<Double> platformAwareProgress = section.flatMap(value -> routeHudPlatformAwareSectionProgress(value, refs));
        if (platformAwareProgress.isPresent()) {
            return platformAwareProgress;
        }
        int index = ClientRouteDataCache.connectionIndexInSection(sectionId, activeRouteDirection, active.connectionId(), activeRouteConnectionIndex)
                .orElse(Math.max(0, Math.min(activeRouteConnectionIndex, refs.size() - 1)));
        double total = 0.0D;
        double traversed = 0.0D;
        for (int i = 0; i < refs.size(); i++) {
            Optional<PipeConnection> connection = ClientPipeNetworkCache.connection(refs.get(i));
            if (connection.isEmpty()) {
                continue;
            }
            double length = connection.get().length();
            if (i < index) {
                traversed += length;
            } else if (i == index && connection.get().id().equals(active.connectionId())) {
                double local = active.direction() >= 0 ? active.distanceOnConnection() : length - active.distanceOnConnection();
                traversed += Mth.clamp(local, 0.0D, length);
            }
            total += length;
        }
        if (total <= 1.0E-6D) {
            return Optional.empty();
        }
        return Optional.of(Mth.clamp(traversed / total, 0.0D, 1.0D));
    }

    private static Optional<Double> routeHudPlatformAwareSectionProgress(RouteSection section, List<PipeConnectionRef> refs) {
        if (active == null || activeRoutePlatformStopId == null) {
            return Optional.empty();
        }
        UUID departureStopId = activeRouteDirection < 0 ? section.toPlatformStopId() : section.fromPlatformStopId();
        UUID arrivalStopId = activeRouteDirection < 0 ? section.fromPlatformStopId() : section.toPlatformStopId();
        Optional<PlatformStop> departureStop = ClientRouteDataCache.platformStop(departureStopId);
        Optional<PlatformStop> arrivalStop = ClientRouteDataCache.platformStop(arrivalStopId);
        if (departureStop.isEmpty() || arrivalStop.isEmpty() || !departureStopId.equals(activeRoutePlatformStopId)) {
            return Optional.empty();
        }
        UUID departureConnectionId = departureStop.get().connectionId();
        UUID arrivalConnectionId = arrivalStop.get().connectionId();
        int departureIndex = firstConnectionIndex(refs, departureConnectionId);
        int arrivalIndex = lastConnectionIndex(refs, arrivalConnectionId);
        if (departureIndex < 0 || arrivalIndex < 0 || arrivalIndex <= departureIndex) {
            return Optional.empty();
        }
        int activeIndex = ClientRouteDataCache.connectionIndexInSection(section.id(), activeRouteDirection, active.connectionId(), activeRouteConnectionIndex)
                .orElse(-1);
        if (activeIndex < 0) {
            return Optional.empty();
        }
        if (activeIndex <= departureIndex) {
            return Optional.of(0.0D);
        }
        if (activeIndex >= arrivalIndex) {
            return Optional.of(1.0D);
        }

        double total = 0.0D;
        double traversed = 0.0D;
        for (int i = departureIndex + 1; i < arrivalIndex; i++) {
            Optional<PipeConnection> connection = ClientPipeNetworkCache.connection(refs.get(i));
            if (connection.isEmpty()) {
                continue;
            }
            double length = connection.get().length();
            if (i < activeIndex) {
                traversed += length;
            } else if (i == activeIndex && connection.get().id().equals(active.connectionId())) {
                double local = active.direction() >= 0 ? active.distanceOnConnection() : length - active.distanceOnConnection();
                traversed += Mth.clamp(local, 0.0D, length);
            }
            total += length;
        }
        if (total <= 1.0E-6D) {
            return Optional.of(activeIndex > departureIndex ? 1.0D : 0.0D);
        }
        return Optional.of(Mth.clamp(traversed / total, 0.0D, 1.0D));
    }

    private static int firstConnectionIndex(List<PipeConnectionRef> refs, UUID connectionId) {
        for (int i = 0; i < refs.size(); i++) {
            if (refs.get(i).connectionId().equals(connectionId)) {
                return i;
            }
        }
        return -1;
    }

    private static int lastConnectionIndex(List<PipeConnectionRef> refs, UUID connectionId) {
        for (int i = refs.size() - 1; i >= 0; i--) {
            if (refs.get(i).connectionId().equals(connectionId)) {
                return i;
            }
        }
        return -1;
    }

    private static RouteHudStationSet routeHudStations(RouteLayout layout, UUID currentPlatformStopId, int routeDirection, boolean terminal, boolean blocked, boolean stationFocused) {
        List<RouteHudStationEntry> entries = routeHudStationEntries(layout);
        int count = entries.size();
        if (count <= 0) {
            return RouteHudStationSet.empty(currentPlatformStopId);
        }
        int currentForwardIndex = -1;
        for (int i = 0; i < count; i++) {
            if (entries.get(i).platformStopId().equals(currentPlatformStopId)) {
                currentForwardIndex = i;
                break;
            }
        }
        if (currentForwardIndex < 0) {
            return RouteHudStationSet.empty(currentPlatformStopId);
        }
        int currentTravelIndex = routeHudTravelIndex(count, currentForwardIndex, routeDirection);
        int focusTravelIndex = terminal || blocked || stationFocused ? currentTravelIndex : currentTravelIndex + 1;
        UUID focusPlatformStopId = terminal || blocked || stationFocused
                ? currentPlatformStopId
                : routeHudEntryAtTravelIndex(entries, focusTravelIndex, routeDirection, layout.loop())
                        .map(RouteHudStationEntry::platformStopId)
                        .orElse(currentPlatformStopId);

        List<ClientRouteHudSnapshot.Station> stations = new ArrayList<>();
        for (int forwardIndex = 0; forwardIndex < count; forwardIndex++) {
            RouteHudStationEntry entry = entries.get(forwardIndex);
            int travelIndex = routeHudTravelIndex(count, forwardIndex, routeDirection);
            int relativeIndex = travelIndex - currentTravelIndex;
            stations.add(new ClientRouteHudSnapshot.Station(
                    entry.platformStopId(),
                    entry.primaryName(),
                    entry.translatedNames(),
                    relativeIndex,
                    travelIndex,
                    forwardIndex,
                    travelIndex == focusTravelIndex,
                    terminal && travelIndex == currentTravelIndex));
        }
        return new RouteHudStationSet(stations, count, currentTravelIndex, focusPlatformStopId);
    }

    private static List<RouteHudStationEntry> routeHudStationEntries(RouteLayout layout) {
        List<RouteHudStationEntry> entries = new ArrayList<>();
        for (UUID platformStopId : layout.orderedPlatformStops()) {
            ClientRouteDataCache.platformStop(platformStopId)
                    .flatMap(stop -> ClientRouteDataCache.stationGroup(stop.stationGroupId()))
                    .ifPresent(station -> entries.add(new RouteHudStationEntry(platformStopId, station.primaryName(), station.translatedNames())));
        }
        return entries;
    }

    private static int routeHudTravelIndex(int validStationCount, int forwardIndex, int routeDirection) {
        if (validStationCount <= 0 || forwardIndex < 0) {
            return 0;
        }
        int clamped = Math.max(0, Math.min(validStationCount - 1, forwardIndex));
        return routeDirection < 0 ? validStationCount - 1 - clamped : clamped;
    }

    private static Optional<RouteHudStationEntry> routeHudEntryAtTravelIndex(List<RouteHudStationEntry> entries, int travelIndex, int routeDirection, boolean loop) {
        int count = entries.size();
        if (count <= 0) {
            return Optional.empty();
        }
        if (!loop && (travelIndex < 0 || travelIndex >= count)) {
            return Optional.empty();
        }
        int normalizedTravelIndex = Math.floorMod(travelIndex, count);
        int forwardIndex = routeDirection < 0 ? count - 1 - normalizedTravelIndex : normalizedTravelIndex;
        return Optional.of(entries.get(Math.max(0, Math.min(count - 1, forwardIndex))));
    }

    private static List<ClientRouteHudSnapshot.TransferLine> routeHudTransfers(UUID platformStopId, UUID currentRouteLineId) {
        Optional<PlatformStop> focusStop = ClientRouteDataCache.platformStop(platformStopId);
        if (focusStop.isEmpty()) {
            return List.of();
        }
        Map<UUID, ClientRouteHudSnapshot.TransferLine> transfers = new LinkedHashMap<>();
        for (PlatformStop platformStop : ClientRouteDataCache.platformStopsInStation(focusStop.get().stationGroupId())) {
            for (UUID layoutId : ClientRouteDataCache.routeLayoutIdsForPlatformStop(platformStop.id())) {
                Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(layoutId);
                if (layout.isEmpty() || layout.get().routeLineId().equals(currentRouteLineId) || transfers.containsKey(layout.get().routeLineId())) {
                    continue;
                }
                Optional<RouteLine> routeLine = ClientRouteDataCache.routeLine(layout.get().routeLineId());
                if (routeLine.isPresent() && !routeLine.get().visibleOnHud()) {
                    continue;
                }
                transfers.put(layout.get().routeLineId(), new ClientRouteHudSnapshot.TransferLine(
                        layout.get().routeLineId(),
                        routeLine.map(RouteLine::displayName).orElseGet(() -> routeHudLayoutName(layout.get())),
                        routeLine.map(RouteLine::translatedNames).orElse(List.of()),
                        routeLine.map(RouteLine::themeColors).filter(colors -> !colors.isEmpty()).orElseGet(() -> themeColors(layout.get()))));
            }
        }
        return List.copyOf(transfers.values());
    }

    private static List<RouteCandidate> feasibleStationCandidates(PlatformStop platformStop) {
        if (ClientRouteDataCache.isWaitingForPipeRevision(ClientPipeNetworkCache.revision())) {
            requestDataResync();
            return List.of();
        }
        ArrayList<RouteCandidate> candidates = new ArrayList<>();
        Optional<UUID> platformRouteLineId = platformStop.routeLineId();
        for (RouteLayout layout : routeLayoutsServingPlatform(platformStop)) {
            if (platformRouteLineId.isPresent() && !layout.routeLineId().equals(platformRouteLineId.get())) {
                continue;
            }
            nextStep(layout, platformStop.id(), 1)
                    .filter(step -> step.section().statusForDirection(1) == RouteSectionStatus.VALID)
                    .ifPresent(step -> candidates.add(new RouteCandidate(layout.id(), 1, platformStop.id(), step.nextPlatformStopId(), step.section().id())));
            if (layout.bidirectional()) {
                nextStep(layout, platformStop.id(), -1)
                        .filter(step -> step.section().statusForDirection(-1) == RouteSectionStatus.VALID)
                        .ifPresent(step -> candidates.add(new RouteCandidate(layout.id(), -1, platformStop.id(), step.nextPlatformStopId(), step.section().id())));
            }
        }
        return candidates;
    }

    private static List<RouteLayout> routeLayoutsServingPlatform(PlatformStop platformStop) {
        Map<UUID, RouteLayout> layoutsById = new LinkedHashMap<>();
        for (UUID layoutId : ClientRouteDataCache.routeLayoutIdsForPlatformStop(platformStop.id())) {
            ClientRouteDataCache.routeLayout(layoutId)
                    .filter(layout -> layout.orderedPlatformStops().contains(platformStop.id()))
                    .ifPresent(layout -> layoutsById.put(layout.id(), layout));
        }
        for (RouteLayout layout : ClientRouteDataCache.routeLayouts()) {
            if (layout.orderedPlatformStops().contains(platformStop.id())) {
                layoutsById.putIfAbsent(layout.id(), layout);
            }
        }
        return List.copyOf(layoutsById.values());
    }

    private static List<RouteCandidate> stationRouteChoiceCandidates(List<RouteCandidate> candidates, PipeConnection current) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<RouteCandidateKey, RouteCandidate> compatibleCandidates = new LinkedHashMap<>();
        for (RouteCandidate candidate : candidates) {
            if (canDepartCurrentPlatform(current, candidate)) {
                compatibleCandidates.putIfAbsent(new RouteCandidateKey(candidate.layoutId(), candidate.routeDirection()), candidate);
            }
        }
        return List.copyOf(compatibleCandidates.values());
    }

    private static boolean canDepartCurrentPlatform(PipeConnection current, RouteCandidate candidate) {
        return desiredExitDirection(current, candidate)
                .map(current::allowsSlideDirection)
                .orElse(false);
    }

    private static Optional<ResolvedRouteCandidate> resolveNavigationBoardingCandidate(PipeConnection current, RouteCandidate candidate) {
        Optional<DepartureResolution> departure = resolveDeparture(current, candidate);
        if (departure.isEmpty()) {
            logRejectedNavigationBoarding(current, candidate, "missing-departure-direction");
            return Optional.empty();
        }
        if (!current.allowsSlideDirection(departure.get().direction())) {
            logRejectedNavigationBoarding(current, candidate, "departure-direction-not-allowed");
            return Optional.empty();
        }
        return Optional.of(new ResolvedRouteCandidate(candidate, departure.get().direction(), departure.get().routeConnectionIndex()));
    }

    private static void logRejectedNavigationBoarding(PipeConnection current, @Nullable RouteCandidate candidate, String reason) {
        if (candidate == null) {
            SuperPipeSlide.LOGGER.debug("Rejected navigation boarding on connection {}: {}", current.id(), reason);
            return;
        }
        SuperPipeSlide.LOGGER.debug(
                "Rejected navigation boarding on connection {} platform {} layout {} routeDirection {} section {}: {}",
                current.id(),
                candidate.platformStopId(),
                candidate.layoutId(),
                candidate.routeDirection(),
                candidate.sectionId(),
                reason);
    }

    private static Optional<RouteStep> nextStep(RouteLayout layout, UUID platformStopId, int routeDirection) {
        return RouteLayoutNavigator.nextStep(layout, platformStopId, routeDirection, ClientRouteDataCache::routeSection);
    }

    private static Optional<Integer> desiredExitDirection(PipeConnection currentConnection, RouteCandidate candidate) {
        return resolveDeparture(currentConnection, candidate).map(DepartureResolution::direction);
    }

    private static Optional<Integer> desiredExitDirection(PipeConnection currentConnection, RouteSection section, int routeDirection) {
        return resolveDeparture(currentConnection, section.id(), routeDirection).map(DepartureResolution::direction);
    }

    private static Optional<DepartureResolution> resolveDeparture(PipeConnection currentConnection, RouteCandidate candidate) {
        return ClientRouteDataCache.routeSection(candidate.sectionId())
                .flatMap(section -> resolveDeparture(currentConnection, section.id(), candidate.routeDirection()));
    }

    private static Optional<DepartureResolution> resolveDeparture(PipeConnection currentConnection, UUID sectionId, int routeDirection) {
        Optional<RouteSectionPath> path = ClientRouteDataCache.routeSectionPath(sectionId);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        List<PipeConnectionRef> refs = routeDirection < 0 ? path.get().reverseConnections() : path.get().forwardConnections();
        if (refs.isEmpty()) {
            return Optional.empty();
        }
        for (int i = 0; i < refs.size(); i++) {
            if (!refs.get(i).connectionId().equals(currentConnection.id())) {
                continue;
            }
            for (int j = i + 1; j < refs.size(); j++) {
                if (refs.get(j).connectionId().equals(currentConnection.id())) {
                    continue;
                }
                int routeConnectionIndex = i;
                return ClientPipeNetworkCache.connection(refs.get(j))
                        .flatMap(nextConnection -> sharedAnchorExitDirection(currentConnection, nextConnection)
                                .map(direction -> new DepartureResolution(direction, routeConnectionIndex)));
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static Optional<Integer> sharedAnchorExitDirection(PipeConnection currentConnection, PipeConnection nextConnection) {
        if (currentConnection.fromAnchor().equals(nextConnection.fromAnchor()) || currentConnection.fromAnchor().equals(nextConnection.toAnchor())) {
            return Optional.of(-1);
        }
        if (currentConnection.toAnchor().equals(nextConnection.fromAnchor()) || currentConnection.toAnchor().equals(nextConnection.toAnchor())) {
            return Optional.of(1);
        }
        return Optional.empty();
    }

    private static Optional<UUID> furthestReachableBeforeBreak(RouteLayout layout, UUID startPlatformStopId, int routeDirection) {
        UUID current = startPlatformStopId;
        int maxSteps = Math.max(0, layout.loop() ? layout.orderedPlatformStops().size() : layout.orderedPlatformStops().size() - 1);
        for (int i = 0; i < maxSteps; i++) {
            Optional<RouteStep> step = nextStep(layout, current, routeDirection);
            if (step.isEmpty()) {
                return Optional.empty();
            }
            if (step.get().section().statusForDirection(routeDirection) != RouteSectionStatus.VALID) {
                return Optional.of(current);
            }
            current = step.get().nextPlatformStopId();
        }
        return Optional.empty();
    }

    private static RouteCandidate preferredCandidate(List<RouteCandidate> candidates, int slideDirection) {
        if (slideDirection < 0) {
            return candidates.stream().filter(candidate -> candidate.routeDirection() < 0).findFirst().orElse(candidates.getFirst());
        }
        return candidates.stream().filter(candidate -> candidate.routeDirection() > 0).findFirst().orElse(candidates.getFirst());
    }

    private static RouteCandidate preferredCandidate(List<RouteCandidate> candidates, PipeConnection current, int slideDirection) {
        return candidates.stream()
                .filter(candidate -> desiredExitDirection(current, candidate).filter(direction -> direction == slideDirection).isPresent())
                .findFirst()
                .orElseGet(() -> preferredCandidate(candidates, slideDirection));
    }

    private static Component stationChoiceLabel(RouteCandidate candidate, List<RouteCandidate> allCandidates) {
        Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(candidate.layoutId());
        if (layout.isEmpty()) {
            return Component.translatable("screen.superpipeslide.layout.default_name");
        }
        String lineName = ClientRouteDataCache.routeLine(layout.get().routeLineId()).map(RouteLine::displayName).orElse("Route");
        boolean multipleLayouts = allCandidates.stream().map(RouteCandidate::layoutId).distinct().limit(2).count() > 1;
        MutableComponent label = Component.literal(lineName);
        if (multipleLayouts || layout.get().nameAsSectionName()) {
            label.append(" ");
            label.append(routeLayoutChoiceName(layout.get()));
        }
        if (layout.get().bidirectional()) {
            label.append(" ");
            label.append(Component.translatable(candidate.routeDirection() < 0 ? "notice.superpipeslide.slide.direction.down" : "notice.superpipeslide.slide.direction.up"));
        }
        return label;
    }

    private static String routeLayoutChoiceName(RouteLayout layout) {
        if (layout.displayName().isPresent()) {
            return layout.displayName().get();
        }
        List<RouteLayout> layouts = ClientRouteDataCache.routeLayoutsForLine(layout.routeLineId());
        for (int i = 0; i < layouts.size(); i++) {
            if (layouts.get(i).id().equals(layout.id())) {
                return Component.translatable("screen.superpipeslide.layout.default_name").getString() + " " + (i + 1);
            }
        }
        return Component.translatable("screen.superpipeslide.layout.default_name").getString();
    }

    private static Component routeLabel(UUID layoutId, int direction) {
        Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(layoutId);
        if (layout.isEmpty()) {
            return Component.translatable("screen.superpipeslide.layout.default_name");
        }
        String lineName = ClientRouteDataCache.routeLine(layout.get().routeLineId()).map(RouteLine::displayName).orElse("Route");
        MutableComponent label = Component.literal(lineName);
        if (layout.get().nameAsSectionName()) {
            label.append(" ").append(layout.get().displayName().orElse(Component.translatable("screen.superpipeslide.layout.default_name").getString()));
        }
        if (layout.get().bidirectional()) {
            label.append(" ").append(Component.translatable(direction < 0 ? "notice.superpipeslide.slide.direction.down" : "notice.superpipeslide.slide.direction.up"));
        }
        return label;
    }

    private static Component stationName(PlatformStop platformStop) {
        return stationName(platformStop.id());
    }

    private static Component stationName(UUID platformStopId) {
        return ClientRouteDataCache.platformStop(platformStopId)
                .flatMap(stop -> ClientRouteDataCache.stationGroup(stop.stationGroupId()))
                .map(StationGroup::primaryName)
                .map(Component::literal)
                .orElse(Component.translatable("screen.superpipeslide.station.missing"));
    }

    private static List<Integer> themeColors(RouteLayout layout) {
        return ClientRouteDataCache.routeLine(layout.routeLineId())
                .map(RouteLine::themeColors)
                .filter(colors -> !colors.isEmpty())
                .orElse(List.of(0xFF3366FF));
    }

    private static UUID candidateChoiceId(UUID platformStopId, RouteCandidate candidate) {
        return UUID.nameUUIDFromBytes(("superpipeslide:station/" + platformStopId + "/" + candidate.layoutId() + "/" + candidate.routeDirection()).getBytes(StandardCharsets.UTF_8));
    }

    private static void sendArrivalNotice(ClientSlideState state, PlatformStop platformStop, RouteLayout layout) {
        sendLocalNoticeOnce(state, platformStop.id(), "arrival", ClientboundSlideNoticePayload.Kind.ARRIVAL, themeColors(layout),
                Component.translatable("notice.superpipeslide.slide.arrived", stationName(platformStop)),
                transferChips(platformStop, layout));
    }

    private static void sendPassStationNotice(ClientSlideState state, PlatformStop platformStop, RouteLayout layout, boolean offLayout) {
        sendLocalNoticeOnce(state, platformStop.id(), offLayout ? "pass" : "pass-non-target", ClientboundSlideNoticePayload.Kind.PASS_STATION, themeColors(layout),
                Component.translatable("notice.superpipeslide.slide.pass_station", stationName(platformStop)),
                List.of(line(Component.translatable(offLayout ? "notice.superpipeslide.slide.pass_station.body" : "notice.superpipeslide.slide.pass_station.non_target_body"))));
    }

    private static void sendTerminalNotice(ClientSlideState state, PlatformStop platformStop, RouteLayout layout) {
        sendLocalNoticeOnce(state, platformStop.id(), "terminal", ClientboundSlideNoticePayload.Kind.TERMINAL, themeColors(layout),
                Component.translatable("notice.superpipeslide.slide.terminal", stationName(platformStop)),
                List.of());
    }

    private static void sendBlockedNotice(ClientSlideState state, PlatformStop platformStop, UUID nextPlatformStopId) {
        sendLocalNoticeOnce(state, platformStop.id(), "blocked", ClientboundSlideNoticePayload.Kind.BLOCKED, List.of(0xFFFF5E4D),
                Component.translatable("notice.superpipeslide.slide.blocked_ahead", stationName(platformStop), stationName(nextPlatformStopId)),
                List.of());
    }

    private static List<ClientboundSlideNoticePayload.NoticeLine> transferChips(PlatformStop currentPlatformStop, RouteLayout currentLayout) {
        Map<UUID, ClientboundSlideNoticePayload.NoticeLine> chipsByRouteLine = new LinkedHashMap<>();
        for (PlatformStop platformStop : ClientRouteDataCache.platformStopsInStation(currentPlatformStop.stationGroupId())) {
            if (platformStop.id().equals(currentPlatformStop.id())) {
                continue;
            }
            for (RouteCandidate candidate : feasibleStationCandidates(platformStop)) {
                Optional<RouteLayout> candidateLayout = ClientRouteDataCache.routeLayout(candidate.layoutId());
                if (candidateLayout.isEmpty() || candidateLayout.get().routeLineId().equals(currentLayout.routeLineId())) {
                    continue;
                }
                UUID routeLineId = candidateLayout.get().routeLineId();
                if (!chipsByRouteLine.containsKey(routeLineId)) {
                    chipsByRouteLine.put(routeLineId, transferChip(routeLineId, candidateLayout.get()));
                }
            }
        }
        return List.copyOf(chipsByRouteLine.values());
    }

    private static ClientboundSlideNoticePayload.NoticeLine transferChip(UUID routeLineId, RouteLayout fallbackLayout) {
        Optional<RouteLine> routeLine = ClientRouteDataCache.routeLine(routeLineId);
        Component label = routeLine
                .map(line -> Component.literal(line.displayName()))
                .orElseGet(() -> fallbackLayout.displayName()
                        .map(Component::literal)
                        .orElse(Component.translatable("screen.superpipeslide.layout.default_name")));
        List<Integer> colors = routeLine
                .map(RouteLine::themeColors)
                .filter(routeColors -> !routeColors.isEmpty())
                .orElseGet(() -> themeColors(fallbackLayout));
        return chip(label, colors);
    }

    private static ClientboundSlideNoticePayload.NoticeLine line(Component text) {
        return new ClientboundSlideNoticePayload.NoticeLine(text, List.of(), false);
    }

    private static ClientboundSlideNoticePayload.NoticeLine chip(Component text, List<Integer> colors) {
        return new ClientboundSlideNoticePayload.NoticeLine(text, colors, true);
    }

    private static void sendLocalNoticeOnce(ClientSlideState state, UUID platformStopId, String kind, ClientboundSlideNoticePayload.Kind noticeKind, List<Integer> colors, Component title, List<ClientboundSlideNoticePayload.NoticeLine> lines) {
        NoticeKey key = new NoticeKey(platformStopId, state.sessionId(), kind);
        if (localNotices.add(key)) {
            sendLocalNotice(noticeKind, colors, title, lines);
        }
    }

    private static void sendLocalNotice(ClientboundSlideNoticePayload.Kind kind, List<Integer> colors, Component title, List<ClientboundSlideNoticePayload.NoticeLine> lines) {
        ClientSlideNoticeController.handleNotice(new ClientboundSlideNoticePayload(kind, colors, title, lines));
    }

    private static Optional<PipeConnection> foremostConnection(BranchNode branch, PipeConnection current, int direction) {
        Vec3 incomingDirection = safeNormalize(current.tangentAt(direction > 0 ? current.length() : 0.0D).scale(direction), new Vec3(0.0D, 0.0D, 1.0D));
        PipeConnection best = null;
        double bestScore = -Double.MAX_VALUE;
        for (UUID connectionId : branch.managedConnectionIdsInOrder()) {
            if (connectionId.equals(current.id())) {
                continue;
            }
            Optional<PipeConnection> candidate = ClientPipeNetworkCache.globalConnection(connectionId);
            if (candidate.isEmpty() || !canLeaveBranch(candidate.get(), branch.anchorId())) {
                continue;
            }
            Vec3 candidateDirection = safeNormalize(directionAwayFrom(candidate.get(), branch.anchorId()), incomingDirection);
            double score = incomingDirection.dot(candidateDirection);
            if (best == null || score > bestScore) {
                best = candidate.get();
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean canLeaveBranch(PipeConnection connection, PipeAnchorId branchAnchor) {
        int direction = connection.directionAwayFrom(branchAnchor);
        return connection.allowsSlideDirection(direction);
    }

    private static Vec3 choicePositionAboveConnection(PipeConnection connection, PipeAnchorId anchor, Vec3 incomingForward) {
        double length = connection.length();
        if (length < 1.0E-6D) {
            return Vec3.atCenterOf(anchor.blockPos()).add(0.0D, BRANCH_CHOICE_HEIGHT_ABOVE_PIPE, 0.0D);
        }
        double lookahead = Math.min(length, Math.max(BRANCH_CHOICE_LOOKAHEAD_MIN_DISTANCE, length * BRANCH_CHOICE_LOOKAHEAD_FRACTION));
        double distance = connection.fromAnchor().equals(anchor) ? lookahead : Math.max(0.0D, length - lookahead);
        Vec3 pipePoint = connection.positionAt(distance);
        Vec3 tangent = directionAwayFrom(connection, anchor);
        Vec3 pipeUp = pipeUp(tangent, incomingForward);
        return pipePoint.add(pipeUp.scale(BRANCH_CHOICE_HEIGHT_ABOVE_PIPE));
    }

    private static Vec3 pipeUp(Vec3 tangent, Vec3 fallbackForward) {
        Vec3 direction = safeNormalize(tangent, fallbackForward);
        Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 projectedUp = worldUp.subtract(direction.scale(worldUp.dot(direction)));
        if (projectedUp.lengthSqr() >= 1.0E-6D) {
            return projectedUp.normalize();
        }
        Vec3 side = safeNormalize(direction.cross(fallbackForward), new Vec3(1.0D, 0.0D, 0.0D));
        return safeNormalize(side.cross(direction), worldUp);
    }

    private static Vec3 directionAwayFrom(PipeConnection connection, PipeAnchorId anchor) {
        Vec3 anchorPosition = Vec3.atCenterOf(anchor.blockPos());
        double length = connection.length();
        if (length < 1.0E-6D) {
            return Vec3.ZERO;
        }
        double lookahead = Math.min(length, Math.max(BRANCH_CHOICE_LOOKAHEAD_MIN_DISTANCE, length * BRANCH_CHOICE_LOOKAHEAD_FRACTION));
        if (connection.fromAnchor().equals(anchor)) {
            return connection.positionAt(lookahead).subtract(anchorPosition);
        }
        if (connection.toAnchor().equals(anchor)) {
            return connection.positionAt(Math.max(0.0D, length - lookahead)).subtract(anchorPosition);
        }
        return Vec3.ZERO;
    }

    private static Vec3 worldToLocal(Vec3 world, Vec3 forward, Vec3 up) {
        Vec3 f = safeNormalize(forward, new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 u = safeNormalize(up.subtract(f.scale(up.dot(f))), new Vec3(0.0D, 1.0D, 0.0D));
        Vec3 r = safeNormalize(f.cross(u), new Vec3(1.0D, 0.0D, 0.0D));
        return new Vec3(world.dot(r), world.dot(u), world.dot(f));
    }

    private static Component directionLabel(Vec3 localDirection) {
        Vec3 direction = safeNormalize(localDirection, new Vec3(0.0D, 0.0D, 1.0D));
        double absX = Math.abs(direction.x);
        double absY = Math.abs(direction.y);
        double absZ = Math.abs(direction.z);
        if (absY > absX && absY > absZ && absY > 0.45D) {
            return Component.translatable(direction.y > 0.0D ? "gaze.superpipeslide.direction.up" : "gaze.superpipeslide.direction.down");
        }
        if (absX > absZ && absX > 0.35D) {
            return Component.translatable(direction.x > 0.0D ? "gaze.superpipeslide.direction.right" : "gaze.superpipeslide.direction.left");
        }
        return Component.translatable(direction.z >= 0.0D ? "gaze.superpipeslide.direction.forward" : "gaze.superpipeslide.direction.back");
    }

    private static Vec3 safeNormalize(Vec3 vector, Vec3 fallback) {
        return vector.lengthSqr() < 1.0E-6D ? fallback : vector.normalize();
    }

    private static void tickCooldown(LocalPlayer player) {
        if (cooldown == null) {
            return;
        }
        if (cooldown.requireExit() && isStillOnCooldownConnection(player, cooldown.connectionId())) {
            cooldown = new CaptureCooldown(cooldown.connectionId(), Math.max(cooldown.ticks(), 10), true);
            return;
        }
        if (cooldown.ticks() <= 1) {
            cooldown = null;
        } else {
            cooldown = new CaptureCooldown(cooldown.connectionId(), cooldown.ticks() - 1, cooldown.requireExit());
        }
    }

    private static boolean isCoolingDown(UUID connectionId) {
        return cooldown != null && cooldown.connectionId().equals(connectionId) && cooldown.ticks() > 0;
    }

    private static boolean isCaptureSuppressedByCooldown() {
        return cooldown != null && cooldown.requireExit() && cooldown.ticks() > 0;
    }

    private static boolean blockForInitialSafetyWarning(LocalPlayer player, PipeConnection connection) {
        if (ClientSafetyOptions.slideSafetyWarningAcknowledged()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SlideSafetyWarningScreen) {
            return true;
        }
        if (minecraft.screen != null) {
            return true;
        }
        minecraft.setScreen(new SlideSafetyWarningScreen(() -> cooldown = new CaptureCooldown(connection.id(), SAFETY_PROMPT_COOLDOWN_TICKS, true)));
        return true;
    }

    private static boolean isStillOnCooldownConnection(LocalPlayer player, UUID connectionId) {
        Optional<PipeConnection> connection = ClientPipeNetworkCache.globalConnection(connectionId);
        if (connection.isEmpty() || !connection.get().levelKey().equals(player.level().dimension())) {
            return false;
        }
        SlideGeometry.Projection projection = SlideGeometry.project(connection.get(), player.position());
        if (!projection.withinSegment(COOLDOWN_EXIT_SEGMENT_MARGIN)) {
            return false;
        }
        Vec3 closest = projection.closestPoint();
        Vec3 position = player.position();
        double horizontalDistance = Math.hypot(position.x - closest.x, position.z - closest.z);
        double exitRadius = Math.max(COOLDOWN_EXIT_HORIZONTAL_RADIUS, Config.CAPTURE_RADIUS.getAsDouble() * 1.75D);
        return horizontalDistance <= exitRadius;
    }

    private static boolean canCaptureFromCurrentMotion(LocalPlayer player, Vec3 tangent) {
        Vec3 movement = player.getDeltaMovement();
        double movementLength = movement.length();
        if (movementLength < 0.25D || movement.y < -0.25D) {
            return true;
        }
        return Math.abs(movement.normalize().dot(tangent)) >= MIN_SIDE_CAPTURE_ALIGNMENT;
    }

    private static int chooseDirection(LocalPlayer player, Vec3 forward) {
        Vec3 movement = player.getDeltaMovement();
        if (movement.lengthSqr() > 0.01D) {
            double movementDot = movement.dot(forward);
            if (Math.abs(movementDot) > 0.02D) {
                return movementDot < 0.0D ? -1 : 1;
            }
        }
        return player.getLookAngle().dot(forward) < 0.0D ? -1 : 1;
    }

    private static boolean wantsSneakExit(LocalPlayer player) {
        return player.input.keyPresses.shift();
    }

    private static boolean wantsJumpExit(LocalPlayer player) {
        return player.input.keyPresses.jump();
    }

    public record SlidePreviewConnection(PipeConnection connection, int direction, double startDistance) {
        public SlidePreviewConnection {
            direction = direction < 0 ? -1 : 1;
            startDistance = Mth.clamp(startDistance, 0.0D, connection.length());
        }
    }

    private record CaptureCooldown(UUID connectionId, int ticks, boolean requireExit) {}

    private record NoticeKey(UUID platformStopId, UUID slideSessionId, String kind) {}

    private record HudStopTiming(ClientRouteHudSnapshot.StopPhase phase, double remainingSeconds, double progress) {
        static HudStopTiming moving() {
            return new HudStopTiming(ClientRouteHudSnapshot.StopPhase.MOVING, 0.0D, 0.0D);
        }
    }

    private record RetainedRouteHudNavigationStop(UUID routeLayoutId, ClientRouteHudSnapshot.NavigationStopContext context) {}

    private record RouteHudStationSet(List<ClientRouteHudSnapshot.Station> stations, int stationCount, int currentTravelIndex, UUID focusPlatformStopId) {
        private RouteHudStationSet {
            stations = List.copyOf(stations);
        }

        static RouteHudStationSet empty(UUID focusPlatformStopId) {
            return new RouteHudStationSet(List.of(), 0, 0, focusPlatformStopId);
        }
    }

    private record RouteHudStationEntry(UUID platformStopId, String primaryName, List<String> translatedNames) {
        private RouteHudStationEntry {
            translatedNames = List.copyOf(translatedNames);
        }
    }

    private record RouteCandidateKey(UUID layoutId, int routeDirection) {
        private RouteCandidateKey {
            routeDirection = routeDirection < 0 ? -1 : 1;
        }
    }

    private record DepartureResolution(int direction, int routeConnectionIndex) {
        private DepartureResolution {
            direction = direction < 0 ? -1 : 1;
            routeConnectionIndex = Math.max(0, routeConnectionIndex);
        }
    }

    private record ResolvedRouteCandidate(RouteCandidate candidate, int departureDirection, int routeConnectionIndex) {
        private ResolvedRouteCandidate {
            departureDirection = departureDirection < 0 ? -1 : 1;
            routeConnectionIndex = Math.max(0, routeConnectionIndex);
        }
    }

    private record StationCenterAction(UUID platformStopId, UUID connectionId, Kind kind, int turnDirection) {
        private StationCenterAction {
            turnDirection = turnDirection < 0 ? -1 : turnDirection > 0 ? 1 : 0;
        }

        boolean matches(PipeConnection connection) {
            return this.connectionId.equals(connection.id())
                    && connection.platformStopId().filter(this.platformStopId::equals).isPresent();
        }

        enum Kind {
            STOP,
            TURN
        }
    }
}
