package dev.marblegate.superpipeslide.client.core.navigation;

import dev.marblegate.superpipeslide.client.core.route.ClientRouteHudController;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.render.SmoothGuiPrimitives;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
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
    private static final double FAR_SCREEN_MARKER_RANGE = 64.0D;
    private static final double EDGE_MARKER_MARGIN = 22.0D;
    private static final double PROJECTED_MARKER_PADDING = 18.0D;

    @Nullable
    private static ClientNavigationController.NavigationHudSnapshot snapshot;
    private static double visibleAlpha;

    private ClientNavigationHudController() {}

    public static void clear() {
        snapshot = null;
        visibleAlpha = 0.0D;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            clear();
            return;
        }
        Optional<ClientNavigationController.NavigationHudSnapshot> next = ClientNavigationController.hudSnapshot(minecraft.player);
        if (next.isPresent()) {
            snapshot = next.get();
            visibleAlpha += (1.0D - visibleAlpha) * ENTRY_STEP;
            return;
        }
        visibleAlpha += (0.0D - visibleAlpha) * EXIT_STEP;
        if (visibleAlpha < 0.015D) {
            snapshot = null;
            visibleAlpha = 0.0D;
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

        int screenWidth = graphics.guiWidth();
        int availableWidth = Math.max(96, screenWidth - 20);
        int width = Math.min(availableWidth, Math.max(176, Math.min(232, screenWidth / 3 + 52)));
        int x = 10;
        int y = ClientRouteHudController.isVisible() ? 58 : 10;
        renderPanel(graphics, font, snapshot, x, y, width, alpha);
        renderWorldTargetIndicator(graphics, font, minecraft, alpha);
    }

    private static int panelHeight(ClientNavigationController.NavigationHudSnapshot value) {
        return value.target().isPresent() ? 38 : 32;
    }

    private static void renderPanel(GuiGraphicsExtractor graphics, Font font, ClientNavigationController.NavigationHudSnapshot value, int x, int y, int width, double alpha) {
        int height = panelHeight(value);
        int accent = value.target().map(target -> targetColor(target.kind(), value.colors())).orElseGet(() -> firstColor(value.colors()));
        graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, color(0x66000000, alpha));
        graphics.fill(x, y, x + width, y + height, color(PANEL, alpha));
        graphics.outline(x, y, width, height, color(PANEL_LINE, alpha));

        List<Integer> colors = value.colors().isEmpty() ? List.of(0xFF47A6FF) : value.colors();
        drawColorBand(graphics, x, y, height, colors, alpha);

        drawStateGlyph(graphics, new Vec2(x + 14, y + 14), value.phase(), accent, alpha);
        String distance = value.target()
                .map(target -> target.distance() >= 999.0D ? "999m+" : Math.round(target.distance()) + "m")
                .orElse("");
        int distanceWidth = distance.isBlank() ? 0 : font.width(distance);
        int titleRightReserve = distance.isBlank() ? 10 : distanceWidth + 16;
        String destination = SPSGui.ellipsize(font, value.destinationName(), width - 34 - titleRightReserve);
        graphics.text(font, destination, x + 29, y + 5, color(TEXT, alpha), true);
        if (!distance.isBlank()) {
            graphics.text(font, distance, x + width - distanceWidth - 8, y + 5, color(TEXT, alpha * 0.92D), true);
        }

        String secondary = value.actionText();
        if (value.target().isEmpty() && !value.detailText().isBlank()) {
            secondary = secondary + " · " + value.detailText();
        }
        secondary = SPSGui.ellipsize(font, secondary, width - 40);
        graphics.text(font, secondary, x + 29, y + 17, color(MUTED, alpha * 0.90D), true);

        int progressLeft = x + 29;
        int progressRight = x + width - 8;
        int progressY = y + height - 5;
        SmoothGuiPrimitives.line(graphics, new Vec2(progressLeft, progressY), new Vec2(progressRight, progressY), 1.6D, color(0x556A8192, alpha));
        SmoothGuiPrimitives.line(graphics, new Vec2(progressLeft, progressY), new Vec2(progressLeft + (progressRight - progressLeft) * clamp(value.progress()), progressY), 2.0D, color(accent, alpha));
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

    private record TargetProjection(double screenX, double screenY, double directionX, double directionY, boolean insideSafeArea, boolean behind) {}
}
