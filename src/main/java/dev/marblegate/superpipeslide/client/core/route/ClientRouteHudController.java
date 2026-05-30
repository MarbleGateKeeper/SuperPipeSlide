package dev.marblegate.superpipeslide.client.core.route;

import dev.marblegate.superpipeslide.client.core.slide.ClientSlideController;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.render.SmoothGuiPrimitives;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ClientRouteHudController {
    private static final double MIN_WIDTH = 280.0D;
    private static final double WIDTH_RATIO = 0.90D;
    private static final double SCREEN_MARGIN = 18.0D;
    private static final double TOP_MARGIN = 6.0D;
    private static final double RAIL_Y = 30.0D;
    private static final double RAIL_WIDTH = 4.2D;
    private static final double STATION_SPACING = 84.0D;
    private static final double EDGE_FADE = 42.0D;
    private static final double MAX_SEGMENT_STRETCH = 42.0D;
    private static final double MOVING_STRETCH_FULL_PROGRESS = 0.18D;
    private static final double ENTRY_ALPHA_STEP = 0.24D;
    private static final double EXIT_ALPHA_STEP = 0.18D;
    private static final double PLAYER_ANCHOR_RATIO = 0.38D;
    private static final int TEXT_LIGHT = 0xFFEFF6FA;
    private static final int TEXT_MUTED_LIGHT = 0xFFBFD0DC;
    private static final int NODE_OUTLINE = 0xEE101820;
    private static final int NODE_FILL = 0xFFF8FBFD;
    private static final int WARNING = 0xFFFF6A55;
    private static final int DEFAULT_ROUTE_COLOR = 0xFF47A6FF;
    private static final int MAX_TRANSFER_CHIPS = 3;
    private static final int MAX_VISIBLE_LABELS = 18;

    @Nullable
    private static ClientRouteHudSnapshot currentSnapshot;
    @Nullable
    private static HudRouteKey routeKey;
    @Nullable
    private static UUID focusPlatformStopId;
    private static double visibleAlpha;
    private static double revealProgress;
    private static double targetPlayerTravelIndex = Double.NaN;
    private static double displayedPlayerTravelIndex = Double.NaN;
    private static double travelIndexOffset;
    private static double cameraX = Double.NaN;
    private static double focusTravelIndex = Double.NaN;
    private static long focusChangedAtMs;
    private static long lapBoundaryEnteredAtMs;
    private static int activeLapBoundarySegment = Integer.MIN_VALUE;
    private static double displayedDwellSeconds = Double.NaN;
    private static ClientRouteHudSnapshot.StopPhase previousStopPhase = ClientRouteHudSnapshot.StopPhase.MOVING;
    private static boolean departureStretchHandoff;
    private static double departureStretchSegmentStart = Double.NaN;
    private static double departureStretchCarryAmount;

    private ClientRouteHudController() {}

    public static void clear() {
        currentSnapshot = null;
        routeKey = null;
        focusPlatformStopId = null;
        visibleAlpha = 0.0D;
        revealProgress = 0.0D;
        targetPlayerTravelIndex = Double.NaN;
        displayedPlayerTravelIndex = Double.NaN;
        travelIndexOffset = 0.0D;
        cameraX = Double.NaN;
        focusTravelIndex = Double.NaN;
        focusChangedAtMs = 0L;
        lapBoundaryEnteredAtMs = 0L;
        activeLapBoundarySegment = Integer.MIN_VALUE;
        displayedDwellSeconds = Double.NaN;
        previousStopPhase = ClientRouteHudSnapshot.StopPhase.MOVING;
        clearDepartureStretchHandoff();
    }

    public static boolean isVisible() {
        return currentSnapshot != null && visibleAlpha > 0.08D;
    }

    public static void tick() {
        Optional<ClientRouteHudSnapshot> snapshot = ClientSlideController.routeHudSnapshot();
        long now = System.currentTimeMillis();
        if (snapshot.isPresent()) {
            ClientRouteHudSnapshot next = snapshot.get();
            HudRouteKey nextKey = HudRouteKey.of(next);
            boolean routeChanged = !Objects.equals(routeKey, nextKey);
            double normalizedTravel = normalizeTravelIndex(next, next.playerTravelIndex(), routeChanged);
            travelIndexOffset = normalizedTravel - next.playerTravelIndex();
            targetPlayerTravelIndex = normalizedTravel;
            updateDepartureStretchState(next, normalizedTravel, routeChanged);
            if (routeChanged || !Double.isFinite(displayedPlayerTravelIndex)) {
                displayedPlayerTravelIndex = normalizedTravel;
                cameraX = Double.NaN;
                revealProgress = Math.min(revealProgress, 0.18D);
            } else {
                displayedPlayerTravelIndex += (normalizedTravel - displayedPlayerTravelIndex) * 0.26D;
            }

            double nextFocusTravel = resolveFocusTravelIndex(next, normalizedTravel);
            if (!Objects.equals(focusPlatformStopId, next.focusPlatformStopId()) || Math.abs(nextFocusTravel - focusTravelIndex) > 0.45D) {
                focusPlatformStopId = next.focusPlatformStopId();
                focusTravelIndex = nextFocusTravel;
                focusChangedAtMs = now;
            }

            int lapSegment = activeLapBoundarySegment(next, displayedPlayerTravelIndex);
            if (lapSegment != activeLapBoundarySegment) {
                activeLapBoundarySegment = lapSegment;
                if (lapSegment != Integer.MIN_VALUE) {
                    lapBoundaryEnteredAtMs = now;
                }
            }

            if (next.stopPhase() == ClientRouteHudSnapshot.StopPhase.MOVING) {
                displayedDwellSeconds = Double.NaN;
            } else if (!Double.isFinite(displayedDwellSeconds) || routeChanged) {
                displayedDwellSeconds = next.stopDwellRemainingSeconds();
            } else {
                displayedDwellSeconds += (next.stopDwellRemainingSeconds() - displayedDwellSeconds) * 0.42D;
            }

            currentSnapshot = next;
            routeKey = nextKey;
            visibleAlpha += (1.0D - visibleAlpha) * ENTRY_ALPHA_STEP;
            revealProgress += (1.0D - revealProgress) * 0.20D;
            return;
        }

        visibleAlpha += (0.0D - visibleAlpha) * EXIT_ALPHA_STEP;
        revealProgress += (0.0D - revealProgress) * 0.16D;
        if (visibleAlpha < 0.012D) {
            clear();
        }
    }

    private static void updateDepartureStretchState(ClientRouteHudSnapshot snapshot, double normalizedTravel, boolean routeChanged) {
        if (routeChanged) {
            previousStopPhase = ClientRouteHudSnapshot.StopPhase.MOVING;
            clearDepartureStretchHandoff();
        }
        if (snapshot.navigationStopContext().isPresent()) {
            previousStopPhase = ClientRouteHudSnapshot.StopPhase.MOVING;
            clearDepartureStretchHandoff();
            return;
        }

        ClientRouteHudSnapshot.StopPhase phase = snapshot.stopPhase();
        double segmentStart = Math.floor(normalizedTravel + 1.0E-6D);
        if (phase == ClientRouteHudSnapshot.StopPhase.DEPARTING) {
            if (previousStopPhase != ClientRouteHudSnapshot.StopPhase.DEPARTING || !Double.isFinite(departureStretchSegmentStart)) {
                departureStretchSegmentStart = segmentStart;
            }
            departureStretchHandoff = false;
            departureStretchCarryAmount = departingStretchAmount(snapshot, segmentStretchCap(snapshot));
        } else if (phase == ClientRouteHudSnapshot.StopPhase.MOVING) {
            if (previousStopPhase == ClientRouteHudSnapshot.StopPhase.DEPARTING) {
                if (!Double.isFinite(departureStretchSegmentStart)) {
                    departureStretchSegmentStart = segmentStart;
                }
                departureStretchHandoff = true;
                departureStretchCarryAmount = Math.max(departureStretchCarryAmount, segmentStretchCap(snapshot));
            } else if (departureStretchHandoff
                    && (!sameSegment(segmentStart, departureStretchSegmentStart)
                            || snapshot.sectionProgress() >= MOVING_STRETCH_FULL_PROGRESS)) {
                                clearDepartureStretchHandoff();
                            }
        } else {
            clearDepartureStretchHandoff();
        }
        previousStopPhase = phase;
    }

    private static void clearDepartureStretchHandoff() {
        departureStretchHandoff = false;
        departureStretchSegmentStart = Double.NaN;
        departureStretchCarryAmount = 0.0D;
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || currentSnapshot == null || visibleAlpha <= 0.018D) {
            return;
        }
        Font font = minecraft.font;
        double screenOpacity = minecraft.screen == null ? 1.0D : 0.36D;
        double alpha = clamp(visibleAlpha * screenOpacity);
        if (alpha <= 0.018D) {
            return;
        }

        ClientRouteHudSnapshot snapshot = currentSnapshot;
        int screenWidth = graphics.guiWidth();
        double width = Math.max(MIN_WIDTH, Math.min(screenWidth - SCREEN_MARGIN * 2.0D, screenWidth * WIDTH_RATIO));
        double left = (screenWidth - width) * 0.5D;
        double right = left + width;
        double reveal = easeOutCubic(revealProgress);
        double offsetY = (1.0D - reveal) * -7.0D;
        double railY = TOP_MARGIN + RAIL_Y + offsetY;
        double playerAnchorX = left + width * PLAYER_ANCHOR_RATIO;
        double playerRouteX = displayedPlayerTravelIndex * STATION_SPACING;
        double targetCameraX = playerRouteX - playerAnchorX;
        if (!Double.isFinite(cameraX)) {
            cameraX = targetCameraX;
        } else {
            cameraX += (targetCameraX - cameraX) * 0.16D;
        }

        List<StationDraw> stations = visibleStations(snapshot, left, right);
        if (stations.isEmpty()) {
            return;
        }
        double markerX = screenX(snapshot, displayedPlayerTravelIndex);
        double focusX = Double.isFinite(focusTravelIndex) ? screenX(snapshot, focusTravelIndex) : markerX;
        boolean lapActive = activeLapBoundarySegment != Integer.MIN_VALUE && System.currentTimeMillis() - lapBoundaryEnteredAtMs <= 1500L;

        drawRouteHeading(graphics, font, snapshot, left, TOP_MARGIN + offsetY, width, alpha);
        drawRails(graphics, snapshot, stations, markerX, railY, left, right, alpha);
        drawMovementFlow(graphics, snapshot, markerX, railY, left, right, alpha);
        drawStations(graphics, stations, markerX, railY, left, right, snapshot, alpha);
        drawProgressMarker(graphics, markerX, railY, snapshot, alpha);
        drawStationLabels(graphics, font, snapshot, stations, focusX, railY, left, right, lapActive, alpha);
    }

    private static double normalizeTravelIndex(ClientRouteHudSnapshot snapshot, double rawTravelIndex, boolean routeChanged) {
        if (routeChanged || !snapshot.loop() || snapshot.stationCount() <= 0 || !Double.isFinite(targetPlayerTravelIndex)) {
            return rawTravelIndex;
        }
        double count = snapshot.stationCount();
        double rounds = Math.rint((targetPlayerTravelIndex - rawTravelIndex) / count);
        double candidate = rawTravelIndex + rounds * count;
        if (candidate - targetPlayerTravelIndex > count * 0.5D) {
            candidate -= count;
        } else if (targetPlayerTravelIndex - candidate > count * 0.5D) {
            candidate += count;
        }
        return candidate;
    }

    private static List<StationDraw> visibleStations(ClientRouteHudSnapshot snapshot, double left, double right) {
        List<StationDraw> stations = new ArrayList<>();
        if (snapshot.stations().isEmpty()) {
            return stations;
        }
        if (snapshot.loop() && snapshot.stationCount() > 1) {
            Map<Integer, ClientRouteHudSnapshot.Station> stationByTravelIndex = new HashMap<>();
            for (ClientRouteHudSnapshot.Station station : snapshot.stations()) {
                stationByTravelIndex.put(Math.floorMod(station.travelIndex(), snapshot.stationCount()), station);
            }
            double buffer = EDGE_FADE + STATION_SPACING + MAX_SEGMENT_STRETCH;
            int minTravelIndex = (int) Math.floor((cameraX + left - buffer) / STATION_SPACING) - 1;
            int maxTravelIndex = (int) Math.ceil((cameraX + right + buffer) / STATION_SPACING) + 1;
            for (int travelIndex = minTravelIndex; travelIndex <= maxTravelIndex; travelIndex++) {
                ClientRouteHudSnapshot.Station station = stationByTravelIndex.get(Math.floorMod(travelIndex, snapshot.stationCount()));
                if (station != null) {
                    addVisibleStation(stations, snapshot, station, travelIndex, left, right);
                }
            }
        } else {
            for (ClientRouteHudSnapshot.Station station : snapshot.stations()) {
                addVisibleStation(stations, snapshot, station, station.travelIndex() + travelIndexOffset, left, right);
            }
        }
        stations.sort(Comparator.comparingDouble(StationDraw::travelIndex));
        return stations;
    }

    private static void addVisibleStation(List<StationDraw> stations, ClientRouteHudSnapshot snapshot, ClientRouteHudSnapshot.Station station, double travelIndex, double left, double right) {
        double x = screenX(snapshot, travelIndex);
        double fade = edgeFade(x, left, right);
        if (fade <= 0.0D && (x < left - EDGE_FADE || x > right + EDGE_FADE)) {
            return;
        }
        int relativeIndex = relativeIndex(travelIndex);
        boolean focus = Double.isFinite(focusTravelIndex)
                ? Math.abs(travelIndex - focusTravelIndex) < 0.35D
                : station.focus();
        stations.add(new StationDraw(station, travelIndex, relativeIndex, focus, x, fade));
    }

    private static void drawRouteHeading(GuiGraphicsExtractor graphics, Font font, ClientRouteHudSnapshot snapshot, double left, double top, double width, double alpha) {
        String heading = routeHeading(snapshot, font, width);
        drawScaledText(graphics, font, heading, left + 2.0D, top, color(TEXT_LIGHT, alpha * 0.92D), 0.72F);
        if (!snapshot.routeTranslatedNames().isEmpty()) {
            String translated = SPSGui.ellipsize(font, snapshot.routeTranslatedNames().getFirst(), (int) Math.round(width / 0.62D));
            drawScaledText(graphics, font, translated, left + 2.0D, top + 7.0D, color(TEXT_MUTED_LIGHT, alpha * 0.76D), 0.62F);
        }
    }

    private static String routeHeading(ClientRouteHudSnapshot snapshot, Font font, double width) {
        String defaultLayout = Component.translatable("screen.superpipeslide.layout.default_name").getString();
        List<String> parts = new ArrayList<>();
        parts.add(snapshot.routeName());
        if (!snapshot.layoutName().isBlank() && !snapshot.layoutName().equals(defaultLayout)) {
            parts.add(snapshot.layoutName());
        }
        if (!snapshot.directionName().isBlank()) {
            parts.add(snapshot.directionName());
        }
        if (snapshot.loop()) {
            parts.add(Component.translatable("screen.superpipeslide.layout.loop").getString());
        }
        return SPSGui.ellipsize(font, String.join("  /  ", parts), (int) Math.round(width / 0.72D));
    }

    private static void drawRails(GuiGraphicsExtractor graphics, ClientRouteHudSnapshot snapshot, List<StationDraw> stations, double markerX, double railY, double left, double right, double alpha) {
        for (int i = 0; i + 1 < stations.size(); i++) {
            StationDraw a = stations.get(i);
            StationDraw b = stations.get(i + 1);
            double segmentFade = Math.max(a.fade(), b.fade());
            if (segmentFade <= 0.01D) {
                continue;
            }
            if (Math.round(b.travelIndex() - a.travelIndex()) != 1L) {
                continue;
            }
            drawColorRail(graphics, a.x(), b.x(), railY, RAIL_WIDTH, snapshot.routeColors(), alpha * segmentFade * 0.34D);
            if (b.x() <= markerX) {
                drawColorRail(graphics, a.x(), b.x(), railY, RAIL_WIDTH + 0.15D, snapshot.routeColors(), alpha * segmentFade * 0.58D);
            } else if (a.x() < markerX && markerX < b.x()) {
                drawColorRail(graphics, a.x(), markerX, railY, RAIL_WIDTH + 0.2D, snapshot.routeColors(), alpha * segmentFade * 0.72D);
            }
            if (snapshot.status() == ClientRouteHudSnapshot.Status.BLOCKED && isCurrentForwardSegment(a, b)) {
                drawDashedWarning(graphics, Math.max(left - 6.0D, Math.min(a.x(), b.x())), Math.min(right + 6.0D, Math.max(a.x(), b.x())), railY, alpha * segmentFade);
            }
            if (isLoopBoundary(snapshot, a.station(), b.station())) {
                drawLapGate(graphics, (a.x() + b.x()) * 0.5D, railY, snapshot.routeColors(), alpha * segmentFade);
            }
        }
    }

    private static boolean isCurrentForwardSegment(StationDraw a, StationDraw b) {
        return a.relativeIndex() == 0 && b.relativeIndex() == 1;
    }

    private static void drawMovementFlow(GuiGraphicsExtractor graphics, ClientRouteHudSnapshot snapshot, double markerX, double railY, double left, double right, double alpha) {
        if (snapshot.navigationStopContext().isPresent()) {
            return;
        }
        if (snapshot.status() == ClientRouteHudSnapshot.Status.BLOCKED
                || snapshot.status() == ClientRouteHudSnapshot.Status.TERMINAL
                || snapshot.status() == ClientRouteHudSnapshot.Status.ARRIVED
                || snapshot.status() == ClientRouteHudSnapshot.Status.FOLD_TRANSIT) {
            return;
        }
        if (snapshot.stopPhase() == ClientRouteHudSnapshot.StopPhase.DOCKING) {
            return;
        }
        if (snapshot.stopPhase() == ClientRouteHudSnapshot.StopPhase.DEPARTING) {
            drawDepartingSweep(graphics, snapshot, markerX, railY, left, right, alpha);
            return;
        }
        double nextX = screenX(snapshot, Math.floor(displayedPlayerTravelIndex) + 1.0D);
        if (nextX <= markerX + 22.0D) {
            return;
        }
        long now = System.currentTimeMillis();
        double period = snapshot.status() == ClientRouteHudSnapshot.Status.APPROACHING ? 1650.0D : 1450.0D;
        double phase = (now % (long) period) / period;
        for (int i = 0; i < 4; i++) {
            double t = (phase + i / 4.0D) % 1.0D;
            double x = markerX + (nextX - markerX) * t;
            double fade = edgeFade(x, left, right);
            if (fade <= 0.0D) {
                continue;
            }
            double centerGlow = Math.max(0.0D, 1.0D - Math.abs(t - 0.5D) * 1.42D);
            double radius = 1.05D + centerGlow * 0.35D;
            SmoothGuiPrimitives.circle(graphics, new Vec2(x, railY), radius + 1.15D, color(0x88FFFFFF, alpha * fade * centerGlow * 0.10D));
            SmoothGuiPrimitives.circle(graphics, new Vec2(x, railY), radius, color(0xCCFFFFFF, alpha * fade * (0.16D + centerGlow * 0.10D)));
        }
    }

    private static void drawDepartingSweep(GuiGraphicsExtractor graphics, ClientRouteHudSnapshot snapshot, double markerX, double railY, double left, double right, double alpha) {
        double nextX = screenX(snapshot, Math.floor(displayedPlayerTravelIndex) + 1.0D);
        if (nextX <= markerX + 24.0D) {
            return;
        }
        long now = System.currentTimeMillis();
        double phase = (now % 920L) / 920.0D;
        double t = easeInOutCubic(phase);
        double x = markerX + 8.0D + (nextX - markerX - 14.0D) * t;
        double fade = edgeFade(x, left, right);
        if (fade <= 0.0D) {
            return;
        }
        int routeColor = firstRouteColor(snapshot.routeColors());
        SmoothGuiPrimitives.circle(graphics, new Vec2(x, railY), 4.4D, color(routeColor, alpha * fade * 0.20D));
        SmoothGuiPrimitives.circle(graphics, new Vec2(x, railY), 2.45D, color(0xEEFFFFFF, alpha * fade * 0.62D));
        SmoothGuiPrimitives.circle(graphics, new Vec2(x + 0.5D, railY - 0.5D), 1.1D, color(0xFFFFFFFF, alpha * fade * 0.88D));
    }

    private static void drawStations(GuiGraphicsExtractor graphics, List<StationDraw> stations, double markerX, double railY, double left, double right, ClientRouteHudSnapshot snapshot, double alpha) {
        long now = System.currentTimeMillis();
        double focusPulse = pulseSince(now, focusChangedAtMs, 760L);
        for (StationDraw draw : stations) {
            double fade = draw.fade();
            if (fade <= 0.02D) {
                continue;
            }
            ClientRouteHudSnapshot.Station station = draw.station();
            boolean current = draw.relativeIndex() == 0;
            boolean focus = draw.focus();
            boolean passed = draw.x() < markerX - 3.0D;
            boolean navigationFocused = focus && snapshot.navigationStopContext().isPresent();
            boolean stopFocused = focus && (snapshot.stopPhase() != ClientRouteHudSnapshot.StopPhase.MOVING || navigationFocused);
            double radius = stopFocused ? 6.7D + focusPulse * 0.65D : focus ? 5.9D + focusPulse * 1.2D : current ? 5.0D : passed ? 3.7D : 4.2D;
            if (navigationFocused) {
                radius += 0.65D;
            }
            if (station.terminal()) {
                radius += 0.8D;
            }
            if (focus) {
                int focusColor = snapshot.navigationStopContext()
                        .map(context -> navigationStopColor(context, snapshot))
                        .orElseGet(() -> firstRouteColor(snapshot.routeColors()));
                SmoothGuiPrimitives.circle(graphics, new Vec2(draw.x(), railY), radius + 5.4D, color(focusColor, alpha * fade * (0.16D + focusPulse * 0.14D)));
                SmoothGuiPrimitives.circle(graphics, new Vec2(draw.x(), railY), radius + 2.1D, color(focusColor, alpha * fade * 0.44D));
            }
            if (stopFocused) {
                drawDwellRing(graphics, new Vec2(draw.x(), railY), snapshot, alpha * fade);
            }
            SmoothGuiPrimitives.circle(graphics, new Vec2(draw.x(), railY), radius + 1.2D, color(NODE_OUTLINE, alpha * fade * (passed ? 0.70D : 1.0D)));
            SmoothGuiPrimitives.circle(graphics, new Vec2(draw.x(), railY), radius, color(NODE_FILL, alpha * fade * (passed ? 0.78D : 1.0D)));
            if (current && !focus) {
                SmoothGuiPrimitives.circle(graphics, new Vec2(draw.x(), railY), Math.max(1.3D, radius - 2.4D), color(firstRouteColor(snapshot.routeColors()), alpha * fade * 0.62D));
            }
            if (station.terminal()) {
                SmoothGuiPrimitives.circle(graphics, new Vec2(draw.x(), railY), radius + 3.7D, color(0xFFFFD35A, alpha * fade * 0.56D));
            }
        }
    }

    private static void drawProgressMarker(GuiGraphicsExtractor graphics, double x, double y, ClientRouteHudSnapshot snapshot, double alpha) {
        long now = System.currentTimeMillis();
        double breath = 0.5D + 0.5D * Math.sin(now / 190.0D);
        int routeColor = snapshot.status() == ClientRouteHudSnapshot.Status.BLOCKED ? WARNING : firstRouteColor(snapshot.routeColors());
        if (snapshot.navigationStopContext().isPresent()) {
            routeColor = navigationStopColor(snapshot.navigationStopContext().get(), snapshot);
        }
        if (snapshot.status() == ClientRouteHudSnapshot.Status.FOLD_TRANSIT) {
            SmoothGuiPrimitives.diamond(graphics, new Vec2(x, y), 8.2D + breath * 1.2D, color(routeColor, alpha * 0.24D));
            SmoothGuiPrimitives.diamond(graphics, new Vec2(x, y), 5.8D, color(routeColor, alpha));
            SmoothGuiPrimitives.diamond(graphics, new Vec2(x, y), 2.7D, color(0xFFFFFFFF, alpha));
            return;
        }
        if (snapshot.stopPhase() != ClientRouteHudSnapshot.StopPhase.MOVING) {
            double glow = snapshot.stopPhase() == ClientRouteHudSnapshot.StopPhase.DEPARTING ? 1.0D : 0.58D + breath * 0.28D;
            SmoothGuiPrimitives.circle(graphics, new Vec2(x, y), 7.0D + glow * 0.9D, color(routeColor, alpha * 0.16D));
            SmoothGuiPrimitives.circle(graphics, new Vec2(x, y), 4.3D, color(NODE_OUTLINE, alpha * 0.88D));
            SmoothGuiPrimitives.circle(graphics, new Vec2(x, y), 3.25D, color(routeColor, alpha * 0.96D));
            SmoothGuiPrimitives.circle(graphics, new Vec2(x - 0.75D, y - 0.85D), 1.0D, color(0xFFFFFFFF, alpha * 0.88D));
            return;
        }
        SmoothGuiPrimitives.circle(graphics, new Vec2(x, y), 7.8D + breath * 1.2D, color(routeColor, alpha * 0.22D));
        SmoothGuiPrimitives.circle(graphics, new Vec2(x, y), 5.8D, color(NODE_OUTLINE, alpha * 0.92D));
        SmoothGuiPrimitives.circle(graphics, new Vec2(x, y), 4.5D, color(routeColor, alpha));
        SmoothGuiPrimitives.circle(graphics, new Vec2(x - 1.1D, y - 1.1D), 1.45D, color(0xFFFFFFFF, alpha * 0.90D));
    }

    private static void drawStationLabels(GuiGraphicsExtractor graphics, Font font, ClientRouteHudSnapshot snapshot, List<StationDraw> stations, double focusX, double railY, double left, double right, boolean lapActive, double alpha) {
        List<LabelRect> occupied = new ArrayList<>();
        Optional<StationDraw> focus = stations.stream().filter(StationDraw::focus).findFirst();
        focus.ifPresent(draw -> drawFocusLabel(graphics, font, snapshot, draw, focusX, railY, left, right, lapActive, alpha, occupied));

        stations.stream()
                .filter(draw -> draw.fade() > 0.28D)
                .filter(draw -> Math.abs(draw.relativeIndex()) <= 12)
                .filter(draw -> focus.isEmpty() || Math.abs(draw.travelIndex() - focus.get().travelIndex()) > 0.35D)
                .sorted(Comparator.comparingInt(ClientRouteHudController::labelPriority).reversed())
                .limit(MAX_VISIBLE_LABELS)
                .forEach(draw -> drawSecondaryLabel(graphics, font, draw, railY, left, right, alpha, occupied));
    }

    private static int labelPriority(StationDraw draw) {
        int relative = Math.abs(draw.relativeIndex());
        int base = Math.max(0, 80 - relative * 8);
        if (draw.relativeIndex() == 0) {
            base += 50;
        }
        if (draw.station().terminal()) {
            base += 32;
        }
        return base;
    }

    private static void drawFocusLabel(GuiGraphicsExtractor graphics, Font font, ClientRouteHudSnapshot snapshot, StationDraw focus, double focusX, double railY, double left, double right, boolean lapActive, double alpha, List<LabelRect> occupied) {
        String state = snapshot.navigationStopContext().isEmpty()
                && lapActive
                && snapshot.status() != ClientRouteHudSnapshot.Status.BLOCKED
                && snapshot.status() != ClientRouteHudSnapshot.Status.TERMINAL
                        ? Component.translatable("hud.superpipeslide.route.next_lap").getString()
                        : statusLabel(snapshot);
        List<LabelRect> focusRects = new ArrayList<>();
        addRect(focusRects, drawCenteredLabelForced(graphics, font, state, focusX, railY + 8.6D, left, right, color(statusColor(snapshot), alpha * focus.fade() * 0.88D), 0.60F));
        String primary = SPSGui.ellipsize(font, focus.station().primaryName(), (int) Math.round((right - left - 40.0D) / 0.92D));
        addRect(focusRects, drawCenteredLabelForced(graphics, font, primary, focusX, railY + 17.4D, left, right, color(TEXT_LIGHT, alpha * focus.fade()), 0.92F));
        double nextY = railY + 28.0D;
        if (!focus.station().translatedNames().isEmpty()) {
            String translated = SPSGui.ellipsize(font, focus.station().translatedNames().getFirst(), (int) Math.round((right - left - 50.0D) / 0.66D));
            addRect(focusRects, drawCenteredLabelForced(graphics, font, translated, focusX, nextY, left, right, color(TEXT_MUTED_LIGHT, alpha * focus.fade() * 0.86D), 0.66F));
            nextY += 8.0D;
        }
        drawTransfers(graphics, font, snapshot.focusTransfers(), focusX, nextY, left, right, alpha * focus.fade());
        if (!snapshot.focusTransfers().isEmpty()) {
            focusRects.add(new LabelRect(clamp(focusX - 56.0D, left + 2.0D, right - 114.0D), nextY - 1.0D, clamp(focusX + 56.0D, left + 114.0D, right - 2.0D), nextY + 13.0D));
        }
        mergeRects(focusRects, 3.0D).ifPresent(occupied::add);
    }

    private static void drawSecondaryLabel(GuiGraphicsExtractor graphics, Font font, StationDraw draw, double railY, double left, double right, double alpha, List<LabelRect> occupied) {
        int relative = draw.relativeIndex();
        boolean current = relative == 0;
        float scale = current ? 0.68F : Math.abs(relative) <= 3 ? 0.60F : 0.54F;
        double y = current ? railY - 15.0D : (relative < 0 || relative % 2 == 0 ? railY - 13.0D : railY + 8.0D);
        int maxWidth = current ? 96 : 78;
        String label = SPSGui.ellipsize(font, draw.station().primaryName(), (int) Math.round(maxWidth / scale));
        int color = current ? TEXT_LIGHT : TEXT_MUTED_LIGHT;
        drawCenteredLabel(graphics, font, label, draw.x(), y, left, right, color(color, alpha * draw.fade() * (current ? 0.86D : 0.68D)), scale, occupied);
    }

    private static void drawCenteredLabel(GuiGraphicsExtractor graphics, Font font, String text, double centerX, double y, double left, double right, int color, float scale, List<LabelRect> occupied) {
        if (text == null || text.isBlank() || ((color >>> 24) & 0xFF) <= 0) {
            return;
        }
        double width = font.width(text) * scale;
        double height = 9.0D * scale;
        double x = clamp(centerX - width * 0.5D, left + 2.0D, right - width - 2.0D);
        LabelRect rect = new LabelRect(x - 2.0D, y - 1.0D, x + width + 2.0D, y + height + 2.0D);
        if (occupied.stream().anyMatch(rect::overlaps)) {
            return;
        }
        occupied.add(rect);
        drawScaledText(graphics, font, text, x, y, color, scale);
    }

    @Nullable
    private static LabelRect drawCenteredLabelForced(GuiGraphicsExtractor graphics, Font font, String text, double centerX, double y, double left, double right, int color, float scale) {
        if (text == null || text.isBlank() || ((color >>> 24) & 0xFF) <= 0) {
            return null;
        }
        double width = font.width(text) * scale;
        double height = 9.0D * scale;
        double x = clamp(centerX - width * 0.5D, left + 2.0D, right - width - 2.0D);
        drawScaledText(graphics, font, text, x, y, color, scale);
        return new LabelRect(x - 2.0D, y - 1.0D, x + width + 2.0D, y + height + 2.0D);
    }

    private static void addRect(List<LabelRect> rects, @Nullable LabelRect rect) {
        if (rect != null) {
            rects.add(rect);
        }
    }

    private static Optional<LabelRect> mergeRects(List<LabelRect> rects, double padding) {
        if (rects.isEmpty()) {
            return Optional.empty();
        }
        double minLeft = rects.stream().mapToDouble(LabelRect::left).min().orElse(0.0D);
        double minTop = rects.stream().mapToDouble(LabelRect::top).min().orElse(0.0D);
        double maxRight = rects.stream().mapToDouble(LabelRect::right).max().orElse(0.0D);
        double maxBottom = rects.stream().mapToDouble(LabelRect::bottom).max().orElse(0.0D);
        return Optional.of(new LabelRect(minLeft - padding, minTop - padding, maxRight + padding, maxBottom + padding));
    }

    private static String statusLabel(ClientRouteHudSnapshot snapshot) {
        if (snapshot.navigationStopContext().isPresent()) {
            return navigationStopLabel(snapshot.navigationStopContext().get());
        }
        if (snapshot.stopPhase() == ClientRouteHudSnapshot.StopPhase.DEPARTING) {
            return Component.translatable("hud.superpipeslide.route.departing_soon").getString();
        }
        if (snapshot.stopPhase() == ClientRouteHudSnapshot.StopPhase.DOCKING) {
            double seconds = Double.isFinite(displayedDwellSeconds) ? displayedDwellSeconds : snapshot.stopDwellRemainingSeconds();
            return Component.translatable("hud.superpipeslide.route.docking", String.format(Locale.ROOT, "%.1f", Math.max(0.0D, seconds))).getString();
        }
        return switch (snapshot.status()) {
            case TERMINAL -> Component.translatable("hud.superpipeslide.route.terminal").getString();
            case BLOCKED -> Component.translatable("hud.superpipeslide.route.blocked").getString();
            case FOLD_TRANSIT -> Component.translatable("hud.superpipeslide.route.fold_transit").getString();
            case APPROACHING -> Component.translatable("hud.superpipeslide.route.approaching").getString();
            case ARRIVED -> Component.translatable("hud.superpipeslide.route.arrived").getString();
            case CRUISING -> Component.translatable("hud.superpipeslide.route.next_station").getString();
        };
    }

    private static int statusColor(ClientRouteHudSnapshot snapshot) {
        if (snapshot.navigationStopContext().isPresent()) {
            return navigationStopColor(snapshot.navigationStopContext().get(), snapshot);
        }
        if (snapshot.stopPhase() != ClientRouteHudSnapshot.StopPhase.MOVING) {
            return firstRouteColor(snapshot.routeColors());
        }
        return switch (snapshot.status()) {
            case TERMINAL -> 0xFFFFD35A;
            case BLOCKED -> WARNING;
            case FOLD_TRANSIT -> 0xFF9CD9FF;
            default -> TEXT_MUTED_LIGHT;
        };
    }

    private static String navigationStopLabel(ClientRouteHudSnapshot.NavigationStopContext context) {
        return Component.translatable(switch (context.kind()) {
            case DESTINATION -> "hud.superpipeslide.route.navigation.destination";
            case SAME_STATION_TRANSFER -> "hud.superpipeslide.route.navigation.same_station_transfer";
            case OUT_OF_STATION_TRANSFER -> "hud.superpipeslide.route.navigation.out_station_transfer";
            case CROSS_DIMENSION_TRANSFER -> "hud.superpipeslide.route.navigation.cross_dimension_transfer";
            case FINAL_WALK -> "hud.superpipeslide.route.navigation.final_walk";
            case CROSS_DIMENSION_FINAL_WALK -> "hud.superpipeslide.route.navigation.cross_dimension_final_walk";
        }).getString();
    }

    private static int navigationStopColor(ClientRouteHudSnapshot.NavigationStopContext context, ClientRouteHudSnapshot snapshot) {
        return switch (context.kind()) {
            case DESTINATION -> 0xFF65E0A3;
            case SAME_STATION_TRANSFER -> firstRouteColor(snapshot.routeColors());
            case OUT_OF_STATION_TRANSFER, FINAL_WALK -> 0xFFFFC45C;
            case CROSS_DIMENSION_TRANSFER, CROSS_DIMENSION_FINAL_WALK -> 0xFFA7D4FF;
        };
    }

    private static void drawTransfers(GuiGraphicsExtractor graphics, Font font, List<ClientRouteHudSnapshot.TransferLine> transfers, double focusX, double y, double left, double right, double alpha) {
        if (transfers.isEmpty()) {
            return;
        }
        int shown = Math.min(MAX_TRANSFER_CHIPS, transfers.size());
        int extra = transfers.size() - shown;
        double chipWidth = 15.0D;
        double gap = 4.0D;
        String more = extra > 0 ? Component.translatable("screen.superpipeslide.more_count", extra).getString() : "";
        double moreWidth = more.isEmpty() ? 0.0D : SPSGui.scaledWidth(font, more, 0.58F) + 3.0D;
        double totalWidth = shown * chipWidth + Math.max(0, shown - 1) * gap + moreWidth;
        double start = clamp(focusX - totalWidth * 0.5D, left + 4.0D, right - totalWidth - 4.0D);
        for (int i = 0; i < shown; i++) {
            drawMiniColorChip(graphics, start + i * (chipWidth + gap), y + 2.0D, chipWidth, transfers.get(i).colors(), alpha);
        }
        if (!more.isEmpty()) {
            drawScaledText(graphics, font, more, start + shown * (chipWidth + gap), y, color(TEXT_MUTED_LIGHT, alpha * 0.82D), 0.58F);
        }
    }

    private static void drawMiniColorChip(GuiGraphicsExtractor graphics, double x, double y, double width, List<Integer> colors, double alpha) {
        List<Integer> normalized = routeColors(colors);
        double stripeHeight = 2.0D;
        for (int i = 0; i < normalized.size(); i++) {
            SmoothGuiPrimitives.line(graphics, new Vec2(x, y + i * stripeHeight), new Vec2(x + width, y + i * stripeHeight), stripeHeight + 0.25D, color(normalized.get(i), alpha * 0.86D));
        }
    }

    private static void drawDwellRing(GuiGraphicsExtractor graphics, Vec2 center, ClientRouteHudSnapshot snapshot, double alpha) {
        if (snapshot.navigationStopContext().isPresent()) {
            drawNavigationStopRing(graphics, center, snapshot, snapshot.navigationStopContext().get(), alpha);
            return;
        }
        double radius = snapshot.stopPhase() == ClientRouteHudSnapshot.StopPhase.DEPARTING ? 10.2D : 9.2D;
        int routeColor = firstRouteColor(snapshot.routeColors());
        drawCircleStroke(graphics, center, radius, 1.25D, color(NODE_FILL, alpha * 0.20D));
        double progress = clamp(snapshot.stopDwellProgress());
        double remaining = Math.max(0.0D, 1.0D - progress);
        double startAngle = -Math.PI * 0.5D + progress * Math.PI * 2.0D;
        if (remaining > 0.018D) {
            drawArcStroke(graphics, center, radius, startAngle, remaining * Math.PI * 2.0D, 1.65D, color(routeColor, alpha * 0.88D));
        }
        if (snapshot.stopPhase() == ClientRouteHudSnapshot.StopPhase.DEPARTING) {
            long now = System.currentTimeMillis();
            double pulse = 0.5D + 0.5D * Math.sin(now / 135.0D);
            SmoothGuiPrimitives.circle(graphics, center, radius + 2.4D + pulse * 1.1D, color(routeColor, alpha * (0.09D + pulse * 0.08D)));
            if (remaining > 0.018D) {
                double headAngle = startAngle;
                Vec2 head = new Vec2(center.x() + Math.cos(headAngle) * radius, center.y() + Math.sin(headAngle) * radius);
                SmoothGuiPrimitives.circle(graphics, head, 2.2D, color(routeColor, alpha * 0.28D));
                SmoothGuiPrimitives.circle(graphics, head, 1.25D, color(0xFFFFFFFF, alpha * 0.76D));
            }
        }
    }

    private static void drawNavigationStopRing(GuiGraphicsExtractor graphics, Vec2 center, ClientRouteHudSnapshot snapshot, ClientRouteHudSnapshot.NavigationStopContext context, double alpha) {
        long now = System.currentTimeMillis();
        double breath = 0.5D + 0.5D * Math.sin(now / 210.0D);
        int accent = navigationStopColor(context, snapshot);
        double radius = switch (context.kind()) {
            case DESTINATION -> 10.1D;
            case SAME_STATION_TRANSFER -> 9.8D;
            case OUT_OF_STATION_TRANSFER, FINAL_WALK -> 9.5D;
            case CROSS_DIMENSION_TRANSFER, CROSS_DIMENSION_FINAL_WALK -> 10.4D;
        };
        SmoothGuiPrimitives.circle(graphics, center, radius + 5.8D + breath * 1.3D, color(accent, alpha * (0.08D + breath * 0.05D)));
        drawCircleStroke(graphics, center, radius + 1.4D, 1.15D, color(NODE_FILL, alpha * 0.18D));
        switch (context.kind()) {
            case DESTINATION -> drawDestinationStopGlyph(graphics, center, radius, accent, alpha, breath);
            case SAME_STATION_TRANSFER -> drawSameStationTransferGlyph(graphics, center, radius, accent, alpha, now);
            case OUT_OF_STATION_TRANSFER, FINAL_WALK -> drawOutStationTransferGlyph(graphics, center, radius, accent, alpha, now);
            case CROSS_DIMENSION_TRANSFER, CROSS_DIMENSION_FINAL_WALK -> drawCrossDimensionTransferGlyph(graphics, center, radius, accent, alpha, now);
        }
    }

    private static void drawDestinationStopGlyph(GuiGraphicsExtractor graphics, Vec2 center, double radius, int accent, double alpha, double breath) {
        drawCircleStroke(graphics, center, radius, 2.0D, color(accent, alpha * 0.94D));
        SmoothGuiPrimitives.circle(graphics, center, radius * 0.46D + breath * 0.35D, color(accent, alpha * 0.22D));
        SmoothGuiPrimitives.line(graphics, new Vec2(center.x() - 3.8D, center.y() - 0.1D), new Vec2(center.x() - 1.1D, center.y() + 3.1D), 1.75D, color(0xFFFFFFFF, alpha * 0.92D));
        SmoothGuiPrimitives.line(graphics, new Vec2(center.x() - 1.1D, center.y() + 3.1D), new Vec2(center.x() + 4.4D, center.y() - 4.0D), 1.75D, color(0xFFFFFFFF, alpha * 0.92D));
    }

    private static void drawSameStationTransferGlyph(GuiGraphicsExtractor graphics, Vec2 center, double radius, int accent, double alpha, long now) {
        double sweep = (now % 1400L) / 1400.0D * Math.PI * 2.0D;
        drawArcStroke(graphics, center, radius, sweep, Math.PI * 0.92D, 1.9D, color(accent, alpha * 0.92D));
        drawArcStroke(graphics, center, radius - 3.0D, sweep + Math.PI, Math.PI * 0.92D, 1.55D, color(0xFFFFFFFF, alpha * 0.74D));
        SmoothGuiPrimitives.line(graphics, new Vec2(center.x() - 4.6D, center.y()), new Vec2(center.x() + 4.6D, center.y()), 1.45D, color(accent, alpha * 0.78D));
        SmoothGuiPrimitives.circle(graphics, new Vec2(center.x() - 5.4D, center.y()), 1.55D, color(0xFFFFFFFF, alpha * 0.82D));
        SmoothGuiPrimitives.circle(graphics, new Vec2(center.x() + 5.4D, center.y()), 1.55D, color(0xFFFFFFFF, alpha * 0.82D));
    }

    private static void drawOutStationTransferGlyph(GuiGraphicsExtractor graphics, Vec2 center, double radius, int accent, double alpha, long now) {
        double dashPhase = (now % 900L) / 900.0D;
        drawArcStroke(graphics, center, radius, -Math.PI * 0.10D, Math.PI * 1.18D, 1.8D, color(accent, alpha * 0.90D));
        drawArcStroke(graphics, center, radius, Math.PI * 1.25D, Math.PI * 0.42D, 1.8D, color(0xFFFFFFFF, alpha * 0.58D));
        for (int i = 0; i < 3; i++) {
            double y = center.y() + radius + 2.4D + i * 3.0D - dashPhase * 3.0D;
            SmoothGuiPrimitives.capsule(graphics, new Vec2(center.x(), y), 5.2D, 1.35D, color(accent, alpha * (0.72D - i * 0.12D)));
        }
        SmoothGuiPrimitives.line(graphics, new Vec2(center.x() - 3.6D, center.y() + 1.4D), new Vec2(center.x() + 3.6D, center.y() + 1.4D), 1.45D, color(0xFFFFFFFF, alpha * 0.78D));
    }

    private static void drawCrossDimensionTransferGlyph(GuiGraphicsExtractor graphics, Vec2 center, double radius, int accent, double alpha, long now) {
        double phase = (now % 1200L) / 1200.0D * Math.PI * 2.0D;
        int violet = 0xFFC59BFF;
        drawArcStroke(graphics, center, radius + 0.6D, phase, Math.PI * 0.72D, 2.0D, color(accent, alpha * 0.94D));
        drawArcStroke(graphics, center, radius + 0.6D, phase + Math.PI * 0.95D, Math.PI * 0.72D, 2.0D, color(violet, alpha * 0.82D));
        drawArcStroke(graphics, center, radius - 3.0D, -phase * 0.72D, Math.PI * 0.84D, 1.35D, color(0xFFFFFFFF, alpha * 0.58D));
        SmoothGuiPrimitives.diamond(graphics, center, 4.2D, color(violet, alpha * 0.42D));
        SmoothGuiPrimitives.circle(graphics, center, 2.3D, color(0xFFFFFFFF, alpha * 0.80D));
    }

    private static void drawCircleStroke(GuiGraphicsExtractor graphics, Vec2 center, double radius, double width, int color) {
        drawArcStroke(graphics, center, radius, 0.0D, Math.PI * 2.0D, width, color);
    }

    private static void drawArcStroke(GuiGraphicsExtractor graphics, Vec2 center, double radius, double startAngle, double angleLength, double width, int color) {
        if (radius <= 0.0D || width <= 0.0D || angleLength <= 0.01D || ((color >>> 24) & 0xFF) <= 0) {
            return;
        }
        double clampedAngle = Math.min(Math.PI * 2.0D, angleLength);
        int samples = Math.max(8, Math.min(48, (int) Math.ceil(radius * clampedAngle / 2.4D)));
        List<Vec2> points = new ArrayList<>();
        for (int i = 0; i <= samples; i++) {
            double angle = startAngle + clampedAngle * i / samples;
            points.add(new Vec2(center.x() + Math.cos(angle) * radius, center.y() + Math.sin(angle) * radius));
        }
        SmoothGuiPrimitives.polyline(graphics, points, width, color);
    }

    private static void drawLapGate(GuiGraphicsExtractor graphics, double x, double y, List<Integer> colors, double alpha) {
        int routeColor = firstRouteColor(colors);
        SmoothGuiPrimitives.circle(graphics, new Vec2(x, y), 6.0D, color(NODE_OUTLINE, alpha * 0.24D));
        SmoothGuiPrimitives.circle(graphics, new Vec2(x, y), 4.8D, color(NODE_FILL, alpha * 0.82D));
        drawSmoothLoopGlyph(graphics, x, y, routeColor, alpha);
    }

    private static void drawSmoothLoopGlyph(GuiGraphicsExtractor graphics, double x, double y, int routeColor, double alpha) {
        List<Vec2> points = new ArrayList<>();
        int samples = 36;
        for (int i = 0; i <= samples; i++) {
            double t = Math.PI * 2.0D * i / samples;
            points.add(new Vec2(
                    x + Math.sin(t) * 4.2D,
                    y + Math.sin(t * 2.0D) * 2.05D));
        }
        SmoothGuiPrimitives.polyline(graphics, points, 2.7D, color(NODE_OUTLINE, alpha * 0.20D));
        SmoothGuiPrimitives.polyline(graphics, points, 1.45D, color(routeColor, alpha * 0.96D));
    }

    private static void drawDashedWarning(GuiGraphicsExtractor graphics, double x1, double x2, double y, double alpha) {
        double dash = 7.0D;
        double gap = 4.0D;
        for (double x = x1; x < x2; x += dash + gap) {
            SmoothGuiPrimitives.line(graphics, new Vec2(x, y), new Vec2(Math.min(x2, x + dash), y), RAIL_WIDTH + 1.2D, color(WARNING, alpha * 0.88D));
        }
    }

    private static void drawColorRail(GuiGraphicsExtractor graphics, double x1, double x2, double y, double width, List<Integer> colors, double alpha) {
        if (Math.abs(x2 - x1) <= 0.4D || alpha <= 0.0D) {
            return;
        }
        double a = Math.min(x1, x2);
        double b = Math.max(x1, x2);
        List<Integer> normalized = routeColors(colors);
        if (normalized.size() == 1) {
            SmoothGuiPrimitives.line(graphics, new Vec2(a, y), new Vec2(b, y), width, color(normalized.getFirst(), alpha));
            return;
        }
        double stripeWidth = width / normalized.size();
        double center = (normalized.size() - 1) * 0.5D;
        for (int i = 0; i < normalized.size(); i++) {
            double yy = y + (i - center) * stripeWidth;
            SmoothGuiPrimitives.line(graphics, new Vec2(a, yy), new Vec2(b, yy), stripeWidth + 0.35D, color(normalized.get(i), alpha));
        }
    }

    private static int activeLapBoundarySegment(ClientRouteHudSnapshot snapshot, double travelIndex) {
        if (!snapshot.loop() || snapshot.stationCount() <= 1) {
            return Integer.MIN_VALUE;
        }
        int segment = (int) Math.floor(travelIndex);
        int current = Math.floorMod(segment, snapshot.stationCount());
        int next = Math.floorMod(segment + 1, snapshot.stationCount());
        return current == snapshot.stationCount() - 1 && next == 0 ? segment : Integer.MIN_VALUE;
    }

    private static boolean isLoopBoundary(ClientRouteHudSnapshot snapshot, ClientRouteHudSnapshot.Station a, ClientRouteHudSnapshot.Station b) {
        if (!snapshot.loop() || snapshot.stationCount() <= 1) {
            return false;
        }
        if (snapshot.routeDirection() >= 0) {
            return a.layoutIndex() == snapshot.stationCount() - 1 && b.layoutIndex() == 0;
        }
        return a.layoutIndex() == 0 && b.layoutIndex() == snapshot.stationCount() - 1;
    }

    private static List<Integer> routeColors(List<Integer> colors) {
        if (colors == null || colors.isEmpty()) {
            return List.of(DEFAULT_ROUTE_COLOR);
        }
        return colors.stream().limit(3).map(SPSGui::opaque).toList();
    }

    private static int firstRouteColor(List<Integer> colors) {
        return routeColors(colors).getFirst();
    }

    private static double resolveFocusTravelIndex(ClientRouteHudSnapshot snapshot, double normalizedTravel) {
        Optional<ClientRouteHudSnapshot.Station> station = snapshot.stations().stream()
                .filter(candidate -> candidate.platformStopId().equals(snapshot.focusPlatformStopId()))
                .findFirst()
                .or(() -> snapshot.stations().stream().filter(ClientRouteHudSnapshot.Station::focus).findFirst());
        if (station.isEmpty()) {
            return normalizedTravel;
        }
        double travelIndex = station.get().travelIndex() + travelIndexOffset;
        if (!snapshot.loop() || snapshot.stationCount() <= 1) {
            return travelIndex;
        }
        boolean focusIsCurrent = snapshot.focusPlatformStopId().equals(snapshot.currentPlatformStopId());
        double expected = focusIsCurrent ? normalizedTravel : Math.floor(normalizedTravel + 1.0E-6D) + 1.0D;
        return alignLoopTravelIndex(travelIndex, expected, snapshot.stationCount());
    }

    private static double alignLoopTravelIndex(double travelIndex, double expected, int stationCount) {
        if (stationCount <= 0) {
            return travelIndex;
        }
        double rounds = Math.rint((expected - travelIndex) / stationCount);
        double candidate = travelIndex + rounds * stationCount;
        if (candidate - expected > stationCount * 0.5D) {
            candidate -= stationCount;
        } else if (expected - candidate > stationCount * 0.5D) {
            candidate += stationCount;
        }
        return candidate;
    }

    private static int relativeIndex(double travelIndex) {
        double reference = Double.isFinite(targetPlayerTravelIndex) ? targetPlayerTravelIndex : displayedPlayerTravelIndex;
        if (!Double.isFinite(reference)) {
            return 0;
        }
        return (int) Math.round(travelIndex - Math.floor(reference + 1.0E-6D));
    }

    private static double screenX(ClientRouteHudSnapshot snapshot, double travelIndex) {
        return travelIndex * STATION_SPACING - cameraX + elasticOffset(snapshot, travelIndex);
    }

    private static double elasticOffset(ClientRouteHudSnapshot snapshot, double travelIndex) {
        SegmentStretch stretch = currentSegmentStretch(snapshot);
        if (stretch.amount() <= 0.0D || !Double.isFinite(stretch.segmentStart())) {
            return 0.0D;
        }
        double delta = travelIndex - stretch.segmentStart();
        if (delta <= 0.0D) {
            return 0.0D;
        }
        if (delta >= 1.0D) {
            return stretch.amount();
        }
        return stretch.amount() * delta;
    }

    private static SegmentStretch currentSegmentStretch(ClientRouteHudSnapshot snapshot) {
        double segmentStart = currentSegmentStart();
        double cap = segmentStretchCap(snapshot);
        if (snapshot.navigationStopContext().isPresent()) {
            return SegmentStretch.none(segmentStart);
        }
        if (snapshot.stopPhase() == ClientRouteHudSnapshot.StopPhase.DEPARTING) {
            double activeSegmentStart = Double.isFinite(departureStretchSegmentStart) ? departureStretchSegmentStart : segmentStart;
            return new SegmentStretch(departingStretchAmount(snapshot, cap), activeSegmentStart);
        }
        if (snapshot.stopPhase() == ClientRouteHudSnapshot.StopPhase.DOCKING) {
            return SegmentStretch.none(segmentStart);
        }
        if (snapshot.status() == ClientRouteHudSnapshot.Status.BLOCKED
                || snapshot.status() == ClientRouteHudSnapshot.Status.TERMINAL
                || snapshot.status() == ClientRouteHudSnapshot.Status.ARRIVED
                || snapshot.status() == ClientRouteHudSnapshot.Status.FOLD_TRANSIT) {
            return SegmentStretch.none(segmentStart);
        }
        double progress = snapshot.sectionProgress();
        double depart = easeOutCubic(progress / MOVING_STRETCH_FULL_PROGRESS);
        double arrive = 1.0D - easeInOutCubic((progress - 0.66D) / 0.30D);
        double movingStretch = cap * depart * arrive;
        if (departureStretchHandoff
                && Double.isFinite(departureStretchSegmentStart)
                && sameSegment(segmentStart, departureStretchSegmentStart)
                && progress < MOVING_STRETCH_FULL_PROGRESS) {
            return new SegmentStretch(Math.max(movingStretch, departureStretchCarryAmount), departureStretchSegmentStart);
        }
        return new SegmentStretch(movingStretch, segmentStart);
    }

    private static double currentSegmentStart() {
        return Double.isFinite(targetPlayerTravelIndex) ? Math.floor(targetPlayerTravelIndex + 1.0E-6D) : Double.NaN;
    }

    private static double segmentStretchCap(ClientRouteHudSnapshot snapshot) {
        return snapshot.loop() && snapshot.stationCount() <= 5 ? 32.0D : MAX_SEGMENT_STRETCH;
    }

    private static double departingStretchAmount(ClientRouteHudSnapshot snapshot, double cap) {
        double departReadiness = easeOutCubic((1.2D - snapshot.stopDwellRemainingSeconds()) / 1.2D);
        return cap * (0.42D + departReadiness * 0.58D);
    }

    private static boolean sameSegment(double a, double b) {
        return Double.isFinite(a) && Double.isFinite(b) && Math.abs(a - b) < 0.35D;
    }

    private static double edgeFade(double x, double left, double right) {
        if (x < left - EDGE_FADE || x > right + EDGE_FADE) {
            return 0.0D;
        }
        double leftFade = clamp((x - (left - EDGE_FADE)) / EDGE_FADE);
        double rightFade = clamp(((right + EDGE_FADE) - x) / EDGE_FADE);
        return Math.min(leftFade, rightFade);
    }

    private static void drawScaledText(GuiGraphicsExtractor graphics, Font font, String text, double x, double y, int color, float scale) {
        if (text == null || text.isBlank() || ((color >>> 24) & 0xFF) <= 0) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color, true);
        graphics.pose().popMatrix();
    }

    private static int color(int color, double alphaScale) {
        int alpha = (int) Math.round(((color >>> 24) & 0xFF) * clamp(alphaScale));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static double pulseSince(long now, long startedAt, long durationMs) {
        if (startedAt <= 0L) {
            return 0.0D;
        }
        double t = clamp((now - startedAt) / (double) durationMs);
        return 1.0D - easeOutCubic(t);
    }

    private static double easeOutCubic(double t) {
        double clamped = clamp(t);
        double inverse = 1.0D - clamped;
        return 1.0D - inverse * inverse * inverse;
    }

    private static double easeInOutCubic(double t) {
        double clamped = clamp(t);
        if (clamped < 0.5D) {
            return 4.0D * clamped * clamped * clamped;
        }
        return 1.0D - Math.pow(-2.0D * clamped + 2.0D, 3.0D) * 0.5D;
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record HudRouteKey(UUID sessionId, UUID routeLayoutId, int routeDirection) {
        static HudRouteKey of(ClientRouteHudSnapshot snapshot) {
            return new HudRouteKey(snapshot.sessionId(), snapshot.routeLayoutId(), snapshot.routeDirection());
        }
    }

    private record SegmentStretch(double amount, double segmentStart) {
        static SegmentStretch none(double segmentStart) {
            return new SegmentStretch(0.0D, segmentStart);
        }
    }

    private record StationDraw(ClientRouteHudSnapshot.Station station, double travelIndex, int relativeIndex, boolean focus, double x, double fade) {}

    private record LabelRect(double left, double top, double right, double bottom) {
        boolean overlaps(LabelRect other) {
            return this.left < other.right && this.right > other.left && this.top < other.bottom && this.bottom > other.top;
        }
    }
}
