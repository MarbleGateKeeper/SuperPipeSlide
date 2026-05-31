package dev.marblegate.superpipeslide.client.core.navigation;

import dev.marblegate.superpipeslide.client.core.route.ClientRouteHudController;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.render.SmoothGuiPrimitives;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

public final class ClientNavigationHudController {
    private static final int TEXT = 0xFFEAF1F6;
    private static final int MUTED = 0xFFB8C6D0;
    private static final int PANEL = 0xDE121920;
    private static final int PANEL_LINE = 0x7A6EA7D6;
    private static final int GREEN = 0xFF7CC7A2;
    private static final int AMBER = 0xFFE0B65A;
    private static final int BLUE = 0xFF69AEE8;
    private static final int RED = 0xFFE36D63;
    private static final double ENTRY_STEP = 0.22D;
    private static final double EXIT_STEP = 0.16D;
    private static final double CARD_ENTRY_STEP = 0.30D;
    private static final double CARD_EXIT_STEP = 0.22D;
    private static final double PROGRESS_STEP = 0.28D;
    private static final int CARD_PEEK_MILLIS = 3200;
    private static final int RAIL_WIDTH = 16;
    private static final int RAIL_TRACK_TOP_PADDING = 14;
    private static final int RAIL_TRACK_BOTTOM_PADDING = 14;
    private static final int CARD_GAP = 8;
    private static final int MAX_CARD_WIDTH = 176;
    private static final int MIN_CARD_WIDTH = 96;
    private static final double FAR_SCREEN_MARKER_RANGE = 64.0D;
    private static final double EDGE_MARKER_MARGIN = 22.0D;
    private static final double PROJECTED_MARKER_PADDING = 18.0D;

    @Nullable
    private static ClientNavigationController.NavigationHudSnapshot snapshot;
    @Nullable
    private static ClientNavigationController.NavigationPhase lastPhase;
    private static int lastSegmentNumber = -1;
    private static double visibleAlpha;
    private static double cardReveal;
    private static double displayedProgress = Double.NaN;
    private static long cardPeekUntilMs;

    private ClientNavigationHudController() {}

    public static void clear() {
        snapshot = null;
        lastPhase = null;
        lastSegmentNumber = -1;
        visibleAlpha = 0.0D;
        cardReveal = 0.0D;
        displayedProgress = Double.NaN;
        cardPeekUntilMs = 0L;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            clear();
            return;
        }
        Optional<ClientNavigationController.NavigationHudSnapshot> next = ClientNavigationController.hudSnapshot(minecraft.player);
        if (next.isPresent()) {
            ClientNavigationController.NavigationHudSnapshot value = next.get();
            long now = System.currentTimeMillis();
            if (isAttentionPhase(value.phase()) && (lastPhase != value.phase() || lastSegmentNumber != value.segmentNumber())) {
                cardPeekUntilMs = now + CARD_PEEK_MILLIS;
            }
            snapshot = value;
            lastPhase = value.phase();
            lastSegmentNumber = value.segmentNumber();
            if (!Double.isFinite(displayedProgress)) {
                displayedProgress = value.progress();
            } else {
                displayedProgress += (value.progress() - displayedProgress) * PROGRESS_STEP;
            }
            visibleAlpha += (1.0D - visibleAlpha) * ENTRY_STEP;
            double targetReveal = targetCardReveal(value, now);
            cardReveal += (targetReveal - cardReveal) * (targetReveal > cardReveal ? CARD_ENTRY_STEP : CARD_EXIT_STEP);
            return;
        }
        visibleAlpha += (0.0D - visibleAlpha) * EXIT_STEP;
        cardReveal += (0.0D - cardReveal) * CARD_EXIT_STEP;
        if (visibleAlpha < 0.015D) {
            clear();
        }
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || snapshot == null || visibleAlpha <= 0.02D) {
            return;
        }
        Font font = minecraft.font;
        double screenOpacity = minecraft.screen == null ? 1.0D : 0.42D;
        double alpha = clamp(visibleAlpha * screenOpacity);
        if (alpha <= 0.02D) {
            return;
        }

        HudGeometry geometry = geometry(graphics.guiWidth(), graphics.guiHeight(), snapshot);
        renderProgressRail(graphics, snapshot, geometry, alpha);
        renderInfoCard(graphics, font, snapshot, geometry, alpha);
        renderWorldTargetIndicator(graphics, font, minecraft, alpha);
    }

    private static double targetCardReveal(ClientNavigationController.NavigationHudSnapshot value, long now) {
        if (value.phase() == ClientNavigationController.NavigationPhase.ARRIVED
                || value.phase() == ClientNavigationController.NavigationPhase.ROUTE_FAILED
                || !isRidingPhase(value.phase())) {
            return 1.0D;
        }
        return now <= cardPeekUntilMs ? 1.0D : 0.0D;
    }

    private static boolean isRidingPhase(ClientNavigationController.NavigationPhase phase) {
        return phase == ClientNavigationController.NavigationPhase.RIDING_SEGMENT
                || phase == ClientNavigationController.NavigationPhase.APPROACHING_TRANSFER
                || phase == ClientNavigationController.NavigationPhase.APPROACHING_DESTINATION;
    }

    private static boolean isAttentionPhase(ClientNavigationController.NavigationPhase phase) {
        return phase == ClientNavigationController.NavigationPhase.APPROACHING_TRANSFER
                || phase == ClientNavigationController.NavigationPhase.APPROACHING_DESTINATION;
    }

    private static HudGeometry geometry(int screenWidth, int screenHeight, ClientNavigationController.NavigationHudSnapshot value) {
        int railX = screenWidth <= 240 ? 5 : 9;
        int topLimit = screenHeight < 160 ? 10 : ClientRouteHudController.isVisible() ? 58 : 16;
        int bottomMargin = screenHeight < 180 ? 18 : 42;
        int availableHeight = Math.max(64, screenHeight - topLimit - bottomMargin);
        int railHeight = Math.min(164, Math.max(88, availableHeight));
        railHeight = Math.min(railHeight, availableHeight);
        int railY = topLimit + Math.max(0, (availableHeight - railHeight) / 2);
        int cardX = railX + RAIL_WIDTH + CARD_GAP;
        int cardWidth = Math.min(MAX_CARD_WIDTH, Math.max(0, screenWidth - cardX - 8));
        int cardHeight = value.target().isPresent() || !value.detailText().isBlank() ? 54 : 44;
        int preferredCardY = railY + 8;
        int cardY = Math.max(6, Math.min(screenHeight - cardHeight - 6, preferredCardY));
        if (cardWidth > 0 && cardWidth < MIN_CARD_WIDTH) {
            cardWidth = Math.max(0, cardWidth);
        }
        return new HudGeometry(railX, railY, RAIL_WIDTH, railHeight, cardX, cardY, cardWidth, cardHeight);
    }

    private static void renderProgressRail(GuiGraphicsExtractor graphics, ClientNavigationController.NavigationHudSnapshot value, HudGeometry geometry, double alpha) {
        int accent = value.target().map(target -> targetColor(target.kind(), value.colors())).orElseGet(() -> firstColor(value.colors()));
        int centerX = geometry.railX() + geometry.railWidth() / 2;
        double centerY = geometry.railY() + geometry.railHeight() * 0.5D;
        double progress = clamp(Double.isFinite(displayedProgress) ? displayedProgress : value.progress());
        double trackTop = geometry.railY() + RAIL_TRACK_TOP_PADDING;
        double trackBottom = geometry.railY() + geometry.railHeight() - RAIL_TRACK_BOTTOM_PADDING;
        double progressY = trackTop + (trackBottom - trackTop) * progress;

        drawVerticalPill(graphics, centerX, centerY, geometry.railWidth(), geometry.railHeight(), color(PANEL, alpha * 0.90D));
        drawVerticalPill(graphics, centerX, centerY, geometry.railWidth() - 3.0D, geometry.railHeight() - 3.0D, color(0xAA0E151C, alpha * 0.72D));

        drawVerticalColorTrack(graphics, centerX, trackTop, trackBottom, value.colors(), alpha * 0.46D);
        if (progressY > trackTop + 0.4D) {
            SmoothGuiPrimitives.line(graphics, new Vec2(centerX, trackTop), new Vec2(centerX, progressY), 5.4D, color(accent, alpha * 0.90D));
            SmoothGuiPrimitives.line(graphics, new Vec2(centerX, trackTop), new Vec2(centerX, progressY), 2.2D, color(0x88FFFFFF, alpha * 0.52D));
        }
        if (progressY < trackBottom - 0.4D) {
            SmoothGuiPrimitives.line(graphics, new Vec2(centerX, progressY), new Vec2(centerX, trackBottom), 3.0D, color(0x556A8192, alpha * 0.70D));
        }

        SmoothGuiPrimitives.circle(graphics, new Vec2(centerX, trackTop), 3.4D, color(accent, alpha * 0.64D));
        SmoothGuiPrimitives.circle(graphics, new Vec2(centerX, trackBottom), 3.4D, color(0xFF6A8192, alpha * 0.62D));
        drawStateGlyph(graphics, new Vec2(centerX, progressY), value.phase(), accent, alpha);
    }

    private static void drawVerticalPill(GuiGraphicsExtractor graphics, double centerX, double centerY, double width, double height, int color) {
        if (((color >>> 24) & 0xFF) <= 0 || width <= 0.0D || height <= 0.0D) {
            return;
        }
        double halfWidth = width * 0.5D;
        double top = centerY - height * 0.5D;
        double bottom = centerY + height * 0.5D;
        double radius = Math.min(halfWidth, height * 0.5D);
        double capTopCenter = top + radius;
        double capBottomCenter = bottom - radius;
        List<SmoothGuiPrimitives.GradientQuad> quads = new ArrayList<>();
        addRectQuad(quads, centerX - halfWidth, capTopCenter, centerX + halfWidth, capBottomCenter, color);
        int slices = Math.max(8, Math.min(24, (int) Math.ceil(radius * 2.0D)));
        for (int i = 0; i < slices; i++) {
            double y0 = top + radius * i / slices;
            double y1 = top + radius * (i + 1) / slices;
            double sampleY = (y0 + y1) * 0.5D;
            double topHalf = Math.sqrt(Math.max(0.0D, radius * radius - (sampleY - capTopCenter) * (sampleY - capTopCenter)));
            addRectQuad(quads, centerX - topHalf, y0, centerX + topHalf, y1, color);

            double by0 = capBottomCenter + radius * i / slices;
            double by1 = capBottomCenter + radius * (i + 1) / slices;
            double sampleBottomY = (by0 + by1) * 0.5D;
            double bottomHalf = Math.sqrt(Math.max(0.0D, radius * radius - (sampleBottomY - capBottomCenter) * (sampleBottomY - capBottomCenter)));
            addRectQuad(quads, centerX - bottomHalf, by0, centerX + bottomHalf, by1, color);
        }
        SmoothGuiPrimitives.quads(graphics, quads);
    }

    private static void addRectQuad(List<SmoothGuiPrimitives.GradientQuad> quads, double left, double top, double right, double bottom, int color) {
        if (right <= left || bottom <= top) {
            return;
        }
        quads.add(new SmoothGuiPrimitives.GradientQuad(
                new Vec2(left, top),
                new Vec2(right, top),
                new Vec2(right, bottom),
                new Vec2(left, bottom),
                color,
                color,
                color,
                color));
    }

    private static void drawVerticalColorTrack(GuiGraphicsExtractor graphics, int centerX, double top, double bottom, List<Integer> colors, double alpha) {
        List<Integer> normalized = colors == null || colors.isEmpty() ? List.of(0xFF47A6FF) : colors.stream().limit(3).map(SPSGui::opaque).toList();
        double height = Math.max(1.0D, bottom - top);
        double segmentHeight = height / normalized.size();
        for (int i = 0; i < normalized.size(); i++) {
            double y1 = top + i * segmentHeight;
            double y2 = i == normalized.size() - 1 ? bottom : y1 + segmentHeight;
            SmoothGuiPrimitives.line(graphics, new Vec2(centerX, y1), new Vec2(centerX, y2), 4.0D, color(normalized.get(i), alpha));
        }
    }

    private static void renderInfoCard(GuiGraphicsExtractor graphics, Font font, ClientNavigationController.NavigationHudSnapshot value, HudGeometry geometry, double alpha) {
        double reveal = easeOutCubic(cardReveal);
        if (reveal <= 0.025D || geometry.cardWidth() < 64) {
            return;
        }
        double cardAlpha = alpha * reveal;
        int slideOffset = (int) Math.round((1.0D - reveal) * -12.0D);
        int x = geometry.cardX() + slideOffset;
        int y = geometry.cardY();
        int width = geometry.cardWidth();
        int height = geometry.cardHeight();

        graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, color(0x66000000, cardAlpha * 0.72D));
        graphics.fill(x, y, x + width, y + height, color(PANEL, cardAlpha));
        graphics.outline(x, y, width, height, color(PANEL_LINE, cardAlpha));
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, color(0x66FFFFFF, cardAlpha * 0.42D));
        drawColorBand(graphics, x, y, height, value.colors(), cardAlpha);

        int textX = x + 9;
        int right = x + width - 8;
        String distance = value.target().map(ClientNavigationHudController::distanceText).orElse("");
        int distanceWidth = distance.isBlank() ? 0 : font.width(distance);
        boolean showDistanceChip = !distance.isBlank() && width >= 132;
        int headlineRightReserve = showDistanceChip ? distanceWidth + 12 : 0;
        String headline = value.actionText().isBlank() ? value.destinationName() : value.actionText();
        graphics.text(font, SPSGui.ellipsize(font, headline, right - textX - headlineRightReserve), textX, y + 6, color(TEXT, cardAlpha), true);
        if (showDistanceChip) {
            graphics.text(font, distance, right - distanceWidth, y + 6, color(TEXT, cardAlpha * 0.90D), true);
        }

        String targetLine = targetLine(value, distance, showDistanceChip);
        graphics.text(font, SPSGui.ellipsize(font, targetLine, right - textX), textX, y + 19, color(MUTED, cardAlpha * 0.92D), true);
        if (!value.detailText().isBlank() && height >= 50) {
            graphics.text(font, SPSGui.ellipsize(font, value.detailText(), right - textX), textX, y + 33, color(MUTED, cardAlpha * 0.72D), true);
        }
    }

    private static String targetLine(ClientNavigationController.NavigationHudSnapshot value, String distance, boolean distanceIsSeparate) {
        String targetName = value.target().map(ClientNavigationController.TargetInfo::name).orElse(value.destinationName());
        if (distance.isBlank() || distanceIsSeparate) {
            return targetName;
        }
        return targetName + " / " + distance;
    }

    private static String distanceText(ClientNavigationController.TargetInfo target) {
        return target.distance() >= 999.0D ? "999m+" : Math.round(target.distance()) + "m";
    }

    private static void drawColorBand(GuiGraphicsExtractor graphics, int x, int y, int height, List<Integer> colors, double alpha) {
        List<Integer> normalized = colors == null || colors.isEmpty() ? List.of(0xFF47A6FF) : colors.stream().limit(3).map(SPSGui::opaque).toList();
        int segmentHeight = Math.max(1, height / normalized.size());
        for (int i = 0; i < normalized.size(); i++) {
            int y1 = y + i * segmentHeight;
            int y2 = i == normalized.size() - 1 ? y + height : y1 + segmentHeight;
            graphics.fill(x, y1, x + 3, y2, color(normalized.get(i), alpha));
        }
    }

    private static void drawStateGlyph(GuiGraphicsExtractor graphics, Vec2 center, ClientNavigationController.NavigationPhase phase, int accent, double alpha) {
        long now = System.currentTimeMillis();
        double pulse = 0.5D + 0.5D * Math.sin(now / 210.0D);
        switch (phase) {
            case ROUTE_FAILED -> {
                SmoothGuiPrimitives.diamond(graphics, center, 7.2D, color(RED, alpha * 0.78D));
                SmoothGuiPrimitives.line(graphics, new Vec2(center.x() - 3.2D, center.y() - 3.2D), new Vec2(center.x() + 3.2D, center.y() + 3.2D), 1.4D, color(0xFFFFFFFF, alpha * 0.86D));
                SmoothGuiPrimitives.line(graphics, new Vec2(center.x() + 3.2D, center.y() - 3.2D), new Vec2(center.x() - 3.2D, center.y() + 3.2D), 1.4D, color(0xFFFFFFFF, alpha * 0.86D));
            }
            case ARRIVED -> {
                SmoothGuiPrimitives.circle(graphics, center, 7.2D + pulse * 0.7D, color(GREEN, alpha * 0.22D));
                SmoothGuiPrimitives.circle(graphics, center, 5.3D, color(GREEN, alpha * 0.86D));
                SmoothGuiPrimitives.line(graphics, new Vec2(center.x() - 3.2D, center.y()), new Vec2(center.x() - 0.8D, center.y() + 2.7D), 1.6D, color(0xFFFFFFFF, alpha));
                SmoothGuiPrimitives.line(graphics, new Vec2(center.x() - 0.8D, center.y() + 2.7D), new Vec2(center.x() + 4.0D, center.y() - 3.6D), 1.6D, color(0xFFFFFFFF, alpha));
            }
            case TRANSFER_WALK, TRANSFER_PROXIMITY -> {
                SmoothGuiPrimitives.circle(graphics, center, 7.0D + pulse, color(AMBER, alpha * 0.22D));
                SmoothGuiPrimitives.diamond(graphics, center, 5.2D, color(AMBER, alpha * 0.88D));
                SmoothGuiPrimitives.line(graphics, new Vec2(center.x() - 4.2D, center.y()), new Vec2(center.x() + 4.2D, center.y()), 1.4D, color(0xFFFFFFFF, alpha * 0.72D));
            }
            default -> {
                SmoothGuiPrimitives.circle(graphics, center, 7.0D + pulse, color(accent, alpha * 0.20D));
                SmoothGuiPrimitives.diamond(graphics, center, 5.2D, color(accent, alpha * 0.92D));
                SmoothGuiPrimitives.circle(graphics, center, 1.8D, color(0xFFFFFFFF, alpha * 0.84D));
            }
        }
    }

    private static void renderWorldTargetIndicator(GuiGraphicsExtractor graphics, Font font, Minecraft minecraft, double alpha) {
        Optional<ClientNavigationController.WorldTarget> target = ClientNavigationController.worldTarget(minecraft.player);
        if (target.isEmpty()) {
            return;
        }
        TargetProjection projection = projectTarget(minecraft, target.get(), graphics.guiWidth(), graphics.guiHeight());
        boolean farTarget = target.get().distance() > FAR_SCREEN_MARKER_RANGE;
        if (!farTarget) {
            return;
        }
        Vec2 point;
        boolean edge = projection.behind() || !projection.insideSafeArea();
        if (edge) {
            point = clampDirectionToEdge(graphics.guiWidth(), graphics.guiHeight(), projection.directionX(), projection.directionY());
        } else {
            point = new Vec2(
                    Math.max(PROJECTED_MARKER_PADDING, Math.min(graphics.guiWidth() - PROJECTED_MARKER_PADDING, projection.screenX())),
                    Math.max(PROJECTED_MARKER_PADDING, Math.min(graphics.guiHeight() - PROJECTED_MARKER_PADDING, projection.screenY())));
        }
        drawTargetIndicator(graphics, font, target.get(), point, edge, alpha);
    }

    private static TargetProjection projectTarget(Minecraft minecraft, ClientNavigationController.WorldTarget target, int screenWidth, int screenHeight) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPosition = camera.position();
        Vec3 relative = target.position().subtract(cameraPosition);
        Vector3fc forward = camera.forwardVector();
        Vector3fc left = camera.leftVector();
        Vector3fc up = camera.upVector();
        double forwardDot = dot(relative, forward);
        double rightDot = -dot(relative, left);
        double upDot = dot(relative, up);
        double centerX = screenWidth * 0.5D;
        double centerY = screenHeight * 0.5D;
        double directionX;
        double directionY;
        double screenX = centerX;
        double screenY = centerY;
        boolean behind = forwardDot <= 0.05D;
        boolean inside = false;
        if (!behind) {
            double halfTan = Math.tan(Math.toRadians(minecraft.options.fov().get()) * 0.5D);
            double aspect = Math.max(0.1D, screenWidth / (double) Math.max(1, screenHeight));
            screenX = centerX + (rightDot / (forwardDot * halfTan * aspect)) * centerX;
            screenY = centerY - (upDot / (forwardDot * halfTan)) * centerY;
            directionX = screenX - centerX;
            directionY = screenY - centerY;
            inside = screenX >= PROJECTED_MARKER_PADDING
                    && screenX <= screenWidth - PROJECTED_MARKER_PADDING
                    && screenY >= PROJECTED_MARKER_PADDING
                    && screenY <= screenHeight - PROJECTED_MARKER_PADDING;
        } else {
            directionX = rightDot;
            directionY = -upDot;
            if (Math.hypot(directionX, directionY) < 0.001D) {
                directionY = screenHeight * 0.5D;
            }
        }
        return new TargetProjection(screenX, screenY, directionX, directionY, inside, behind);
    }

    private static double dot(Vec3 relative, Vector3fc vector) {
        return relative.x * vector.x() + relative.y * vector.y() + relative.z * vector.z();
    }

    private static Vec2 clampDirectionToEdge(int screenWidth, int screenHeight, double directionX, double directionY) {
        double centerX = screenWidth * 0.5D;
        double centerY = screenHeight * 0.5D;
        if (Math.hypot(directionX, directionY) < 0.001D) {
            directionY = 1.0D;
        }
        double left = EDGE_MARKER_MARGIN;
        double right = Math.max(left, screenWidth - EDGE_MARKER_MARGIN);
        double top = EDGE_MARKER_MARGIN;
        double bottom = Math.max(top, screenHeight - EDGE_MARKER_MARGIN);
        double tx = directionX > 0.0D ? (right - centerX) / directionX : directionX < 0.0D ? (left - centerX) / directionX : Double.POSITIVE_INFINITY;
        double ty = directionY > 0.0D ? (bottom - centerY) / directionY : directionY < 0.0D ? (top - centerY) / directionY : Double.POSITIVE_INFINITY;
        double t = Math.min(tx > 0.0D ? tx : Double.POSITIVE_INFINITY, ty > 0.0D ? ty : Double.POSITIVE_INFINITY);
        if (!Double.isFinite(t)) {
            t = 0.0D;
        }
        return new Vec2(
                Math.max(left, Math.min(right, centerX + directionX * t)),
                Math.max(top, Math.min(bottom, centerY + directionY * t)));
    }

    private static void drawTargetIndicator(GuiGraphicsExtractor graphics, Font font, ClientNavigationController.WorldTarget target, Vec2 point, boolean edge, double alpha) {
        int accent = targetColor(target.kind(), List.of(target.color()));
        long now = System.currentTimeMillis();
        double pulse = 0.5D + 0.5D * Math.sin(now / 190.0D);
        double radius = edge ? 7.6D : 6.2D;
        SmoothGuiPrimitives.circle(graphics, point, radius + 4.0D + pulse * 1.6D, color(accent, alpha * 0.15D));
        SmoothGuiPrimitives.diamond(graphics, point, radius + 1.8D, color(0xEE071018, alpha * 0.70D));
        SmoothGuiPrimitives.diamond(graphics, point, radius, color(accent, alpha * 0.92D));
        SmoothGuiPrimitives.circle(graphics, point, 2.1D, color(0xFFFFFFFF, alpha * 0.86D));
        if (edge) {
            drawEdgeArrow(graphics, point, graphics.guiWidth(), graphics.guiHeight(), accent, alpha);
        }

        String distance = target.distance() >= 999.0D ? "999m+" : Math.round(target.distance()) + "m";
        String label = SPSGui.ellipsize(font, target.name() + " / " + distance, Math.min(148, Math.max(72, graphics.guiWidth() / 3)));
        int labelWidth = font.width(label) + 10;
        int labelX = (int) Math.round(point.x() + 11.0D);
        if (labelX + labelWidth > graphics.guiWidth() - 4) {
            labelX = (int) Math.round(point.x() - 11.0D - labelWidth);
        }
        labelX = Math.max(4, Math.min(graphics.guiWidth() - labelWidth - 4, labelX));
        int labelY = (int) Math.round(point.y() - 7.0D);
        labelY = Math.max(4, Math.min(graphics.guiHeight() - 15, labelY));
        graphics.fill(labelX + 1, labelY + 1, labelX + labelWidth + 1, labelY + 13, color(0x66000000, alpha));
        graphics.fill(labelX, labelY, labelX + labelWidth, labelY + 12, color(PANEL, alpha * 0.82D));
        graphics.outline(labelX, labelY, labelWidth, 12, color(accent, alpha * 0.56D));
        graphics.text(font, label, labelX + 5, labelY + 2, color(TEXT, alpha * 0.94D), true);
    }

    private static void drawEdgeArrow(GuiGraphicsExtractor graphics, Vec2 point, int screenWidth, int screenHeight, int accent, double alpha) {
        double centerX = screenWidth * 0.5D;
        double centerY = screenHeight * 0.5D;
        double dx = point.x() - centerX;
        double dy = point.y() - centerY;
        double length = Math.hypot(dx, dy);
        if (length < 0.001D) {
            return;
        }
        double ux = dx / length;
        double uy = dy / length;
        double px = -uy;
        double py = ux;
        Vec2 tip = new Vec2(point.x() + ux * 7.2D, point.y() + uy * 7.2D);
        Vec2 left = new Vec2(point.x() - ux * 4.8D + px * 4.5D, point.y() - uy * 4.8D + py * 4.5D);
        Vec2 right = new Vec2(point.x() - ux * 4.8D - px * 4.5D, point.y() - uy * 4.8D - py * 4.5D);
        SmoothGuiPrimitives.line(graphics, left, tip, 2.0D, color(0xEE071018, alpha * 0.78D));
        SmoothGuiPrimitives.line(graphics, right, tip, 2.0D, color(0xEE071018, alpha * 0.78D));
        SmoothGuiPrimitives.line(graphics, left, tip, 1.1D, color(accent, alpha * 0.92D));
        SmoothGuiPrimitives.line(graphics, right, tip, 1.1D, color(accent, alpha * 0.92D));
    }

    private static int targetColor(ClientNavigationController.TargetKind kind, List<Integer> colors) {
        return switch (kind) {
            case BOARDING, SAME_STATION_TRANSFER, DESTINATION -> firstColor(colors);
            case OUT_OF_STATION_TRANSFER, FINAL_WALK -> AMBER;
            case CROSS_DIMENSION_TRANSFER, CROSS_DIMENSION_FINAL_WALK -> BLUE;
        };
    }

    private static int firstColor(List<Integer> colors) {
        return SPSGui.opaque(colors.isEmpty() ? 0xFF47A6FF : colors.getFirst());
    }

    private static int color(int color, double alphaScale) {
        int alpha = (int) Math.round(((color >>> 24) & 0xFF) * clamp(alphaScale));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static double easeOutCubic(double value) {
        double t = clamp(value);
        double inv = 1.0D - t;
        return 1.0D - inv * inv * inv;
    }

    private record HudGeometry(int railX, int railY, int railWidth, int railHeight, int cardX, int cardY, int cardWidth, int cardHeight) {}

    private record TargetProjection(double screenX, double screenY, double directionX, double directionY, boolean insideSafeArea, boolean behind) {}
}
