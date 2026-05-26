package dev.marblegate.superpipeslide.client.gui.base;


import dev.marblegate.superpipeslide.client.gui.route.ClientRouteViewModelCache;
import dev.marblegate.superpipeslide.client.fullmap.render.SmoothGuiPrimitives;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SPSGui {
    public static final int PANEL_BASE = 0xF4F7F9FC;
    public static final int PANEL_ELEVATED = 0xFFFFFFFF;
    public static final int PANEL_RECESSED = 0xFFE9EEF5;
    public static final int PANEL_LINE = 0xFFB9C4D2;
    public static final int PANEL_HIGHLIGHT = 0xFFE6F0FA;
    public static final int TEXT_PRIMARY = 0xFF1B2633;
    public static final int TEXT_SECONDARY = 0xFF415166;
    public static final int TEXT_MUTED = 0xFF708092;
    public static final int TEXT_DISABLED = 0xFFA8B2BF;
    public static final int WARNING = 0xFFB37A00;
    public static final int DANGER = 0xFFD33C3C;
    public static final int SUCCESS = 0xFF1D8F45;
    public static final int INFO = 0xFF1F73B7;
    public static final int ORANGE = 0xFFD16A1A;
    public static final int SAVE_ATTENTION = 0xFFD7522A;
    private static final Identifier ICON_TEXTURE = Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "textures/gui/icons.png");
    private static final int ICON_CELL_SIZE = 16;
    private static final int ICON_TEXTURE_WIDTH = 256;
    private static final int ICON_TEXTURE_HEIGHT = 64;
    private static final int COLOR_PICKER_MAX_SV_BANDS = 96;
    private SPSGui() {
    }

    public static void text(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
        graphics.text(font, text, x, y, color, false);
    }

    public static void text(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
        graphics.text(font, text, x, y, color, false);
    }

    public static void centeredText(GuiGraphicsExtractor graphics, Font font, String text, int centerX, int y, int color) {
        graphics.text(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    public static void centeredText(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y, int color) {
        String value = text.getString();
        graphics.text(font, text, centerX - font.width(value) / 2, y, color, false);
    }

    public static Rect panelRect(int screenWidth, int screenHeight) {
        int width = Math.min(300, screenWidth - 20);
        int height = Math.min(164, screenHeight - 16);
        return new Rect((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
    }

    public static void panel(GuiGraphicsExtractor graphics, Rect rect) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), PANEL_BASE);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), PANEL_LINE);
        graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.y() + 2, 0xBFFFFFFF);
    }

    public static void titleBar(GuiGraphicsExtractor graphics, Font font, Rect panel, Component title, boolean hasBack, List<IconButton> actions, int mouseX, int mouseY) {
        graphics.fill(panel.x() + 1, panel.y() + 1, panel.right() - 1, panel.y() + 20, 0xFFF2F6FB);
        graphics.fill(panel.x() + 1, panel.y() + 20, panel.right() - 1, panel.y() + 21, PANEL_LINE);
        int titleX = panel.x() + 8;
        if (hasBack) {
            drawIconButton(graphics, new Rect(panel.x() + 5, panel.y() + 3, 16, 16), Icon.BACK, false, contains(panel.x() + 5, panel.y() + 3, 16, 16, mouseX, mouseY), TEXT_SECONDARY);
            titleX = panel.x() + 25;
        }
        SPSGui.text(graphics, font, scrollingText(font, title.getString(), panel.width() - 80, title.getString().hashCode()), titleX, panel.y() + 7, TEXT_PRIMARY);

        int x = panel.right() - 21;
        for (int i = actions.size() - 1; i >= 0; i--) {
            IconButton action = actions.get(i);
            Rect bounds = new Rect(x, panel.y() + 3, 16, 16);
            drawIconButton(graphics, bounds, action.icon(), action.disabled(), bounds.contains(mouseX, mouseY), action.color());
            x -= 18;
        }
    }

    public static void card(GuiGraphicsExtractor graphics, Rect rect, boolean hovered, boolean selected) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), hovered ? 0xFFF2F7FD : PANEL_ELEVATED);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), selected ? INFO : PANEL_LINE);
        graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.y() + 2, 0x99FFFFFF);
    }

    public static void colorStripe(GuiGraphicsExtractor graphics, int x, int y, int height, List<Integer> colors) {
        List<Integer> normalized = colors == null || colors.isEmpty() ? List.of(0xFF3366FF) : colors.stream().limit(3).toList();
        if (normalized.size() == 1) {
            graphics.fill(x, y, x + 4, y + height, opaque(normalized.get(0)));
        } else if (normalized.size() == 2) {
            graphics.fill(x, y, x + 4, y + height / 2, opaque(normalized.get(0)));
            graphics.fill(x, y + height / 2, x + 4, y + height, opaque(normalized.get(1)));
        } else {
            graphics.fill(x, y, x + 4, y + Math.max(1, height / 4), opaque(normalized.get(0)));
            graphics.fill(x, y + Math.max(1, height / 4), x + 4, y + height - Math.max(1, height / 4), opaque(normalized.get(1)));
            graphics.fill(x, y + height - Math.max(1, height / 4), x + 4, y + height, opaque(normalized.get(2)));
        }
    }

    public static void badge(GuiGraphicsExtractor graphics, Font font, String label, int x, int y, int color) {
        int width = font.width(label) + 8;
        graphics.fill(x, y, x + width, y + 12, withAlpha(color, 0x1F));
        graphics.outline(x, y, width, 12, withAlpha(color, 0x88));
        SPSGui.text(graphics, font, label, x + 4, y + 2, color);
    }

    public static void inputUnderline(GuiGraphicsExtractor graphics, Rect rect, boolean focused) {
        graphics.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), focused ? INFO : PANEL_LINE);
    }

    public static void icon(GuiGraphicsExtractor graphics, Rect bounds, Icon icon, int color) {
        int size = Math.min(ICON_CELL_SIZE, Math.min(bounds.width(), bounds.height()));
        int x = bounds.x() + (bounds.width() - size) / 2;
        int y = bounds.y() + (bounds.height() - size) / 2;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                ICON_TEXTURE,
                x,
                y,
                icon.textureX(),
                icon.textureY(),
                size,
                size,
                ICON_CELL_SIZE,
                ICON_CELL_SIZE,
                ICON_TEXTURE_WIDTH,
                ICON_TEXTURE_HEIGHT,
                color
        );
    }

    public static void drawIconButton(GuiGraphicsExtractor graphics, Rect rect, Icon icon, boolean disabled, boolean hovered, int color) {
        int effectiveColor = disabled ? TEXT_DISABLED : icon == Icon.SAVE ? SAVE_ATTENTION : color;
        int bg;
        int border;
        if (disabled) {
            bg = PANEL_RECESSED;
            border = 0xFFD7DEE8;
        } else if (icon == Icon.SAVE) {
            bg = hovered ? 0xFFFFEEE6 : 0xFFFFF6EC;
            border = withAlpha(SAVE_ATTENTION, hovered ? 0xDD : 0x99);
        } else {
            bg = hovered ? PANEL_HIGHLIGHT : PANEL_ELEVATED;
            border = PANEL_LINE;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), bg);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), border);
        icon(graphics, rect, icon, effectiveColor);
    }

    public static void roundCreateButton(GuiGraphicsExtractor graphics, Rect rect, boolean hovered) {
        int border = hovered ? INFO : 0xFF4DA4E8;
        graphics.fill(rect.x() + 8, rect.y(), rect.right() - 8, rect.bottom(), PANEL_ELEVATED);
        graphics.fill(rect.x(), rect.y() + 8, rect.right(), rect.bottom() - 8, PANEL_ELEVATED);
        graphics.outline(rect.x() + 8, rect.y(), rect.width() - 16, rect.height(), border);
        graphics.outline(rect.x(), rect.y() + 8, rect.width(), rect.height() - 16, border);
        icon(graphics, new Rect(rect.x + 2, rect.y + 2, rect.width, rect.height), Icon.PLUS, border);
    }

    public static void stationMap(GuiGraphicsExtractor graphics, Font font, List<StationNode> nodes, List<Integer> colors, Rect rect, boolean large, int offset, boolean showNames) {
        stationMap(graphics, font, nodes, colors, rect, large, autoStationMapScroll(nodes, rect.width(), large, offset), showNames);
    }

    public static void stationMap(GuiGraphicsExtractor graphics, Font font, RouteLayout layout, List<Integer> colors, Rect rect, boolean large, int offset, boolean showNames) {
        List<StationNode> nodes = nodesForLayout(layout);
        stationMap(graphics, font, nodes, colors, rect, large, autoStationMapScroll(nodes, rect.width(), large, offset, layout.loop()), showNames, layout.bidirectional(), layout.loop(), loopStatus(layout), offset);
    }

    public static void stationMap(GuiGraphicsExtractor graphics, Font font, RouteLayout layout, List<Integer> colors, Rect rect, boolean large, double scrollPixels, boolean showNames) {
        List<StationNode> nodes = nodesForLayout(layout);
        stationMap(graphics, font, nodes, colors, rect, large, scrollPixels, showNames, layout.bidirectional(), layout.loop(), loopStatus(layout), layout.id().hashCode());
    }

    public static void stationMap(GuiGraphicsExtractor graphics, Font font, List<StationNode> nodes, List<Integer> colors, Rect rect, boolean large, double scrollPixels, boolean showNames) {
        stationMap(graphics, font, nodes, colors, rect, large, scrollPixels, showNames, false, false, RouteSectionStatus.VALID, 0);
    }

    private static void stationMap(GuiGraphicsExtractor graphics, Font font, List<StationNode> nodes, List<Integer> colors, Rect rect, boolean large, double scrollPixels, boolean showNames, boolean bidirectional, boolean loop, RouteSectionStatus loopStatus, int seed) {
        int count = nodes.size();
        if (count <= 0) {
            SPSGui.text(graphics, font, Component.translatable("screen.superpipeslide.station_map.no_stops"), rect.x(), rect.y() + 3, TEXT_MUTED);
            return;
        }
        int dot = large ? 12 : 7;
        int lineY = stationMapLineY(rect, large);
        double maxScroll = maxStationMapScroll(nodes, rect.width(), large, loop);
        double effectiveScroll = large ? scrollPixels : Math.max(0.0D, Math.min(maxScroll, scrollPixels));
        int spacing = stationMapSpacing(nodes, rect.width(), large, loop);
        double firstXExact = stationMapFirstXExact(rect, nodes, large, loop, effectiveScroll);
        int firstX = (int) Math.floor(firstXExact);
        double subPixelX = firstXExact - firstX;
        List<Integer> railColors = metroColors(colors);
        graphics.enableScissor(rect.x(), rect.y(), rect.right(), rect.bottom());
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) subPixelX, 0.0F);
        if (loop && count > 1) {
            drawLoopConnectors(graphics, firstX + dot / 2, firstX + (count - 1) * spacing + dot / 2, lineY, large ? rect.bottom() : rect.bottom() - 2, loopStatus, railColors, large ? 6 : 3, !bidirectional, large, seed);
        }
        for (int i = 0; i < count; i++) {
            StationNode node = nodes.get(i);
            int x = firstX + i * spacing;
            if (i + 1 < count) {
                StationNode next = nodes.get(i + 1);
                RouteSectionStatus status = node.missing() || next.missing() ? RouteSectionStatus.VALID : next.incomingStatus();
                drawSegment(graphics, x + dot / 2, lineY, x + spacing + dot / 2, status, railColors, large ? 6 : 3);
                if (!bidirectional && status == RouteSectionStatus.VALID) {
                    drawMovingDirectionMarkers(graphics, x + dot / 2, x + spacing + dot / 2, lineY, large ? 6 : 3, large, seed + i);
                }
            }
            if (x + dot >= rect.x() - spacing && x <= rect.right() + spacing) {
                if (node.missing()) {
                    drawMissingStation(graphics, x, lineY - dot / 2, dot);
                } else {
                    drawStationDot(graphics, font, x, lineY - dot / 2, dot, node.transferLines(), node.error(), large);
                }
            }
            if (showNames && !node.missing()) {
                String label = marquee(node.name(), large ? 12 : 6, i);
                float labelScale = large ? 0.82F : 0.68F;
                rotatedText(graphics, font, label, x + dot / 2, lineY + dot / 2 + (large ? 3 : 0), node.error() ? DANGER : TEXT_SECONDARY, labelScale, 0.7853982F);
            }
        }
        graphics.pose().popMatrix();
        graphics.disableScissor();
    }

    public static int visibleStationCount(int count, int width, boolean large) {
        int spacing = large ? 46 : 22;
        int visible = Math.max(1, Math.min(count, Math.max(1, (width - 18) / spacing + 1)));
        if (count > visible && visible > 1) {
            visible--;
        }
        return visible;
    }

    public static double maxStationMapScroll(int count, int width, boolean large) {
        return maxStationMapScroll(count, width, large, false);
    }

    public static double maxStationMapScroll(List<StationNode> nodes, int width, boolean large) {
        return maxStationMapScroll(nodes, width, large, false);
    }

    public static double maxStationMapScroll(List<StationNode> nodes, int width, boolean large, boolean loop) {
        boolean trailingTransfer = !nodes.isEmpty() && !nodes.getLast().missing() && !nodes.getLast().transferLines().isEmpty();
        return maxStationMapScroll(nodes.size(), width, large, trailingTransfer || (loop && nodes.size() > 1));
    }

    private static double maxStationMapScroll(int count, int width, boolean large, boolean trailingTransfer) {
        int contentWidth = stationMapContentWidth(count, large);
        int viewportWidth = stationMapViewportWidth(width, large);
        if (contentWidth <= viewportWidth) {
            return 0.0D;
        }
        return contentWidth + stationMapTrailingScrollSpace(large, trailingTransfer) - viewportWidth;
    }

    public static double autoStationMapScroll(int count, int width, boolean large, int seed) {
        double maxScroll = maxStationMapScroll(count, width, large);
        if (maxScroll <= 0.0D) {
            return 0.0D;
        }
        long now = System.currentTimeMillis();
        double cycleMs = Math.max(5200.0D, 2400.0D + maxScroll * (large ? 30.0D : 38.0D));
        double pause = 0.16D;
        double phase = ((now + Math.floorMod(seed, 997) * 37L) % (long) cycleMs) / cycleMs;
        if (phase < pause) {
            return 0.0D;
        }
        if (phase > 1.0D - pause) {
            return maxScroll;
        }
        double t = (phase - pause) / (1.0D - pause * 2.0D);
        return maxScroll * easeInOut(t);
    }

    private static int stationMapContentWidth(int count, boolean large) {
        if (count <= 0) {
            return 0;
        }
        int spacing = large ? 46 : 22;
        int dot = large ? 12 : 7;
        return (count - 1) * spacing + dot;
    }

    public static int stationMapLineY(Rect rect, boolean large) {
        return large ? rect.y() + Math.max(30, rect.height() / 2) : rect.y() + 7;
    }

    public static int stationMapSpacing(List<StationNode> nodes, int width, boolean large, boolean loop) {
        int count = nodes.size();
        int baseSpacing = large ? 46 : 22;
        if (!large || count <= 1 || maxStationMapScroll(nodes, width, large, loop) > 0.0D) {
            return baseSpacing;
        }
        int visible = Math.max(2, Math.max(1, (width - 18) / baseSpacing + 1) - 1);
        int targetSpan = Math.max(baseSpacing, (visible - 1) * baseSpacing);
        return Math.max(baseSpacing, targetSpan / (count - 1));
    }

    public static int stationMapFirstX(Rect rect, List<StationNode> nodes, boolean large, boolean loop, double scrollPixels) {
        return (int) Math.round(stationMapFirstXExact(rect, nodes, large, loop, scrollPixels));
    }

    private static double stationMapFirstXExact(Rect rect, List<StationNode> nodes, boolean large, boolean loop, double scrollPixels) {
        int count = nodes.size();
        int padding = large ? 14 : 10;
        int dot = large ? 12 : 7;
        double maxScroll = maxStationMapScroll(nodes, rect.width(), large, loop);
        if (maxScroll > 0.0D) {
            return rect.x() + padding - scrollPixels;
        }
        int spacing = stationMapSpacing(nodes, rect.width(), large, loop);
        int mapWidth = count <= 1 ? dot : (count - 1) * spacing + dot;
        int centered = rect.x() + (large ? Math.max(padding, (rect.width() - mapWidth) / 2) : padding);
        return large ? centered - scrollPixels : centered;
    }

    private static int stationMapViewportWidth(int width, boolean large) {
        int padding = large ? 14 : 10;
        return Math.max(1, width - padding * 2);
    }

    public static double autoStationMapScroll(List<StationNode> nodes, int width, boolean large, int seed) {
        return autoStationMapScroll(nodes, width, large, seed, false);
    }

    public static double autoStationMapScroll(List<StationNode> nodes, int width, boolean large, int seed, boolean loop) {
        double maxScroll = maxStationMapScroll(nodes, width, large, loop);
        if (maxScroll <= 0.0D) {
            return 0.0D;
        }
        long now = System.currentTimeMillis();
        double cycleMs = Math.max(5200.0D, 2400.0D + maxScroll * (large ? 30.0D : 38.0D));
        double pause = 0.16D;
        double phase = ((now + Math.floorMod(seed, 997) * 37L) % (long) cycleMs) / cycleMs;
        if (phase < pause) {
            return 0.0D;
        }
        if (phase > 1.0D - pause) {
            return maxScroll;
        }
        double t = (phase - pause) / (1.0D - pause * 2.0D);
        return maxScroll * easeInOut(t);
    }

    private static int stationMapTrailingScrollSpace(boolean large, boolean trailingTransfer) {
        return large && trailingTransfer ? 26 : 0;
    }

    private static double easeInOut(double t) {
        double clamped = Math.max(0.0D, Math.min(1.0D, t));
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    public static String scrollingText(Font font, String text, int maxWidth, int tickSeed) {
        if (font == null || font.width(text) <= maxWidth) {
            return text;
        }
        int maxChars = Math.max(1, text.length());
        while (maxChars > 1 && font.width(text.substring(0, Math.min(maxChars, text.length()))) > maxWidth) {
            maxChars--;
        }
        return marquee(text, maxChars, tickSeed);
    }

    private static String marquee(String text, int maxChars, int tickSeed) {
        if (text.length() <= maxChars) {
            return text;
        }
        int maxStart = Math.max(0, text.length() - maxChars);
        int endPauseSteps = 2;
        int phase = (int) ((System.currentTimeMillis() / 450L + Math.floorMod(tickSeed, 997)) % (maxStart + 1L + endPauseSteps));
        int start = Math.min(phase, maxStart);
        return text.substring(start, start + maxChars);
    }

    private static void drawSegment(GuiGraphicsExtractor graphics, int x1, int y, int x2, RouteSectionStatus status, List<Integer> colors, int width) {
        if (status == RouteSectionStatus.BROKEN) {
            int mid = (x1 + x2) / 2;
            drawRail(graphics, x1, x2, y, colors, width);
            drawBreakSlash(graphics, mid, y, width);
            return;
        }
        if (status == RouteSectionStatus.DISABLED) {
            drawRail(graphics, x1, x2, y, List.of(TEXT_DISABLED), Math.max(1, width - 1));
            return;
        }
        if (status == RouteSectionStatus.STALE || status == RouteSectionStatus.INCOMPLETE || status == RouteSectionStatus.AMBIGUOUS) {
            List<Integer> warningColors = List.of(status == RouteSectionStatus.AMBIGUOUS ? ORANGE : WARNING);
            for (int x = x1; x < x2; x += 6) {
                drawRail(graphics, x, Math.min(x + 3, x2), y, warningColors, width);
            }
            return;
        }
        drawRail(graphics, x1, x2, y, colors, width);
    }

    private static void drawLoopConnectors(GuiGraphicsExtractor graphics, int firstCenterX, int lastCenterX, int lineY, int bottomY, RouteSectionStatus status, List<Integer> colors, int width, boolean oneWay, boolean large, int seed) {
        int horizontal = Math.max(12, width * 3);
        int leftX = firstCenterX - horizontal;
        int rightX = lastCenterX + horizontal;
        int corner = Math.max(width, large ? 7 : 4);
        drawSegment(graphics, leftX + corner, lineY, firstCenterX, RouteSectionStatus.VALID, colors, width);
        drawVerticalRail(graphics, leftX, lineY + corner / 2, bottomY, colors, width);
        drawRoundedElbow(graphics, leftX, lineY, corner, colors, width, true);
        drawSegment(graphics, lastCenterX, lineY, rightX - corner, RouteSectionStatus.VALID, colors, width);
        drawVerticalRail(graphics, rightX, lineY + corner / 2, bottomY, colors, width);
        drawRoundedElbow(graphics, rightX, lineY, corner, colors, width, false);
        if (oneWay) {
            drawMovingHorizontalDirectionMarkers(graphics, leftX + corner, firstCenterX, lineY, width, large, seed + 101, true);
            drawMovingVerticalDirectionMarkers(graphics, leftX, lineY + corner, bottomY, width, large, seed + 102, false);
            drawMovingHorizontalDirectionMarkers(graphics, lastCenterX, rightX, lineY, width, large, seed + 103, true);
            drawMovingVerticalDirectionMarkers(graphics, rightX, lineY + corner, bottomY, width, large, seed + 104, true);
        }
        if (status != RouteSectionStatus.VALID) {
            drawBreakSlash(graphics, leftX, (lineY + bottomY) / 2, width);
        }
    }

    private static void drawRoundedElbow(GuiGraphicsExtractor graphics, int x, int y, int radius, List<Integer> colors, int width, boolean left) {
        List<Integer> normalized = metroColors(colors);
        int color = normalized.get(0);
        int stamp = Math.max(2, width);
        for (int i = 0; i <= radius; i++) {
            double angle = (Math.PI * 0.5D) * i / Math.max(1, radius);
            int ox = (int) Math.round(Math.cos(angle) * radius);
            int oy = (int) Math.round(Math.sin(angle) * radius);
            int px = left ? x + radius - ox : x - radius + ox;
            int py = y + radius - oy;
            graphics.fill(px - stamp / 2, py - stamp / 2, px + stamp / 2 + 1, py + stamp / 2 + 1, color);
        }
    }

    private static void drawVerticalRail(GuiGraphicsExtractor graphics, int x, int y1, int y2, List<Integer> colors, int width) {
        if (y2 <= y1) {
            return;
        }
        List<Integer> normalized = metroColors(colors);
        if (normalized.size() == 1) {
            graphics.fill(x - width / 2, y1, x + width / 2 + 1, y2, normalized.get(0));
            return;
        }
        int left = x - width / 2;
        int stripeWidth = Math.max(1, width / normalized.size());
        for (int i = 0; i < normalized.size(); i++) {
            int x1 = left + i * stripeWidth;
            int x2 = i == normalized.size() - 1 ? x + width / 2 + 1 : x1 + stripeWidth;
            graphics.fill(x1, y1, x2, y2, normalized.get(i));
        }
    }

    private static void drawMovingDirectionMarkers(GuiGraphicsExtractor graphics, int x1, int x2, int y, int railWidth, boolean large, int seed) {
        drawMovingHorizontalDirectionMarkers(graphics, x1, x2, y, railWidth, large, seed, true);
    }

    private static void drawMovingHorizontalDirectionMarkers(GuiGraphicsExtractor graphics, int x1, int x2, int y, int railWidth, boolean large, int seed, boolean leftToRight) {
        int distance = x2 - x1;
        if (distance < Math.max(7, (large ? 7 : 5) + 2)) {
            return;
        }
        int spacing = large ? 22 : 14;
        int size = large ? 7 : 5;
        int phase = (int) ((System.currentTimeMillis() / 90L + Math.floorMod(seed, spacing)) % spacing);
        for (int x = x1 + phase; x < x2 - size; x += spacing) {
            drawChevronCut(graphics, x, y, size, railWidth, leftToRight);
        }
    }

    private static void drawMovingVerticalDirectionMarkers(GuiGraphicsExtractor graphics, int x, int y1, int y2, int railWidth, boolean large, int seed, boolean downward) {
        int distance = y2 - y1;
        if (distance < Math.max(7, (large ? 7 : 5) + 2)) {
            return;
        }
        int spacing = large ? 22 : 14;
        int size = large ? 7 : 5;
        int phase = (int) ((System.currentTimeMillis() / 90L + Math.floorMod(seed, spacing)) % spacing);
        if (downward) {
            for (int y = y1 + phase; y < y2 - size; y += spacing) {
                drawVerticalChevronCut(graphics, x, y, size, railWidth, true);
            }
        } else {
            for (int y = y2 - phase - size; y > y1; y -= spacing) {
                drawVerticalChevronCut(graphics, x, y, size, railWidth, false);
            }
        }
    }

    private static void drawChevronCut(GuiGraphicsExtractor graphics, int x, int y, int size, int railWidth, boolean leftToRight) {
        int color = PANEL_ELEVATED;
        int half = Math.max(2, size / 2);
        int thickness = Math.max(1, railWidth / 3);
        for (int i = 0; i < half; i++) {
            int xx = leftToRight ? x + i : x + half - i;
            graphics.fill(xx, y - half + i, xx + thickness, y - half + i + thickness, color);
            graphics.fill(xx, y + half - i, xx + thickness, y + half - i + thickness, color);
        }
    }

    private static void drawVerticalChevronCut(GuiGraphicsExtractor graphics, int x, int y, int size, int railWidth, boolean downward) {
        int color = PANEL_ELEVATED;
        int half = Math.max(2, size / 2);
        int thickness = Math.max(1, railWidth / 3);
        for (int i = 0; i < half; i++) {
            int yy = downward ? y + i : y + half - i;
            graphics.fill(x - half + i, yy, x - half + i + thickness, yy + thickness, color);
            graphics.fill(x + half - i, yy, x + half - i + thickness, yy + thickness, color);
        }
    }

    private static void drawRail(GuiGraphicsExtractor graphics, int x1, int x2, int y, List<Integer> colors, int width) {
        if (x2 <= x1) {
            return;
        }
        List<Integer> normalized = metroColors(colors);
        if (normalized.size() == 1) {
            graphics.fill(x1, y - width / 2, x2, y + width / 2 + 1, normalized.get(0));
        } else {
            int top = y - width / 2;
            int stripeHeight = Math.max(1, width / normalized.size());
            for (int i = 0; i < normalized.size(); i++) {
                int y1 = top + i * stripeHeight;
                int y2 = i == normalized.size() - 1 ? y + width / 2 + 1 : y1 + stripeHeight;
                graphics.fill(x1, y1, x2, y2, normalized.get(i));
            }
        }
    }

    private static void drawContinuation(GuiGraphicsExtractor graphics, int x1, int x2, int y, List<Integer> colors, int width, boolean left) {
        if (x2 <= x1) {
            return;
        }
        int segments = Math.max(1, Math.min(5, (x2 - x1) / 5));
        for (int i = 0; i < segments; i++) {
            int a = left ? i + 1 : segments - i;
            int alpha = 0x22 + a * (0xAA / segments);
            int sx1 = x1 + (x2 - x1) * i / segments;
            int sx2 = x1 + (x2 - x1) * (i + 1) / segments;
            drawRail(graphics, sx1, sx2, y, colors.stream().map(color -> withAlpha(color, alpha)).toList(), width);
        }
    }

    private static void drawBreakSlash(GuiGraphicsExtractor graphics, int x, int y, int width) {
        int top = y - width / 2 - 1;
        for (int i = 0; i < width + 2; i++) {
            graphics.fill(x - 2 + i, top + i, x + i, top + i + 2, DANGER);
        }
    }

    private static void drawMissingStation(GuiGraphicsExtractor graphics, int x, int y, int size) {
        fillOctagon(graphics, x, y, size, DANGER);
        fillOctagon(graphics, x + 1, y + 1, size - 2, PANEL_ELEVATED);
        drawCross(graphics, x + 2, y + 2, size - 4, DANGER, 1);
    }

    public static void drawCross(GuiGraphicsExtractor graphics, int x, int y, int size, int color, int thickness) {
        int t = Math.max(1, thickness);
        for (int i = 0; i < size; i++) {
            graphics.fill(x + i, y + i, x + i + t, y + i + t, color);
            graphics.fill(x + size - i - t, y + i, x + size - i, y + i + t, color);
        }
    }

    private static void drawStationDot(GuiGraphicsExtractor graphics, Font font, int x, int y, int size, List<TransferLine> transferLines, boolean error, boolean large) {
        boolean showTransfers = large && !transferLines.isEmpty();
        int outline = 0xFF111820;
        if (showTransfers) {
            int shownTransfers = Math.min(3, transferLines.size());
            int extra = Math.min(34, 10 + shownTransfers * 8);
            drawTransferIntersections(graphics, font, x, y, size, extra, transferLines);
            fillCapsule(graphics, x, y - extra, size, size + extra, outline);
            fillCapsule(graphics, x + 1, y - extra + 1, size - 2, size + extra - 2, PANEL_ELEVATED);
        } else {
            fillOctagon(graphics, x, y, size, outline);
            fillOctagon(graphics, x + 1, y + 1, size - 2, PANEL_ELEVATED);
        }
        if (error) {
            graphics.fill(x + size - 2, y, x + size, y + 2, DANGER);
        }
    }

    private static void drawTransferIntersections(GuiGraphicsExtractor graphics, Font font, int x, int y, int size, int extra, List<TransferLine> transferLines) {
        int cy = y - extra + 7;
        int maxShown = Math.min(3, transferLines.size());
        int start = transferLines.size() <= maxShown ? 0 : (int) ((System.currentTimeMillis() / 1400L) % transferLines.size());
        for (int i = 0; i < maxShown; i++) {
            TransferLine line = transferLines.get((start + i) % transferLines.size());
            int yy = cy + i * 8;
            drawRail(graphics, x + 1, x + size + 10, yy, metroColors(line.colors()), 4);
            String label = marquee(line.shortName(), 12, i);
            rotatedText(graphics, font, label, x + size + 14, yy + 2, TEXT_SECONDARY, 0.56F, -0.7853982F);
        }
        if (transferLines.size() > maxShown) {
            smallText(graphics, font, Component.translatable("screen.superpipeslide.more_count", transferLines.size() - maxShown).getString(), x + size + 13, cy - 7, TEXT_MUTED, 0.58F);
        }
    }

    public static void smallText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        SPSGui.text(graphics, font, text, 0, 0, color);
        graphics.pose().popMatrix();
    }

    public static void rotatedText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color, float scale, float radians) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().rotate(radians);
        graphics.pose().scale(scale, scale);
        SPSGui.text(graphics, font, text, 0, 0, color);
        graphics.pose().popMatrix();
    }

    public static int scaledWidth(Font font, String text, float scale) {
        return Math.round(font.width(text) * scale);
    }

    public static void colorSwatch(GuiGraphicsExtractor graphics, Rect rect, int color, boolean hovered) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), PANEL_ELEVATED);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), hovered ? INFO : PANEL_LINE);
        graphics.fill(rect.x() + 3, rect.y() + 3, rect.right() - 3, rect.bottom() - 3, opaque(color));
    }

    public static void emptyColorSwatch(GuiGraphicsExtractor graphics, Rect rect, boolean hovered) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), PANEL_ELEVATED);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), hovered ? INFO : PANEL_LINE);
        icon(graphics, rect, Icon.PLUS, hovered ? INFO : TEXT_MUTED);
    }

    public static void colorPicker(GuiGraphicsExtractor graphics, Rect rect, int selectedColor, int mouseX, int mouseY) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), PANEL_ELEVATED);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), PANEL_LINE);
        Hsv selected = hsvFromColor(selectedColor);
        float hue = selected.s() <= 0.02F ? 0.0F : selected.h();
        Rect field = colorPickerSaturationValueField(rect);
        Rect hueStrip = colorPickerHueField(rect);

        drawColorPickerGradient(graphics, field, hueStrip, hue);
        colorSwatch(graphics, new Rect(rect.x() + 8, rect.y() + 5, 14, 12), selectedColor, false);
        Rect svMarker = new Rect(
                field.x() + Math.round(selected.s() * (field.width() - 1)) - 2,
                field.y() + Math.round((1.0F - selected.v()) * (field.height() - 1)) - 2,
                5,
                5
        );
        graphics.outline(svMarker.x(), svMarker.y(), svMarker.width(), svMarker.height(), 0xEEFFFFFF);
        graphics.outline(svMarker.x() + 1, svMarker.y() + 1, svMarker.width() - 2, svMarker.height() - 2, 0xAA000000);
        int hueY = hueStrip.y() + Math.round(hue * (hueStrip.height() - 1));
        graphics.fill(hueStrip.x() - 2, hueY - 1, hueStrip.right() + 2, hueY + 2, 0xEEFFFFFF);
        graphics.outline(hueStrip.x() - 2, hueY - 1, hueStrip.width() + 4, 3, 0xAA000000);
        Rect close = new Rect(rect.right() - 20, rect.y() + 4, 14, 14);
        drawIconButton(graphics, close, Icon.CLOSE, false, close.contains(mouseX, mouseY), TEXT_SECONDARY);
    }

    public static int colorFromPicker(Rect rect, double mouseX, double mouseY) {
        return colorFromPicker(rect, 0xFFFF4040, mouseX, mouseY);
    }

    public static int colorFromPicker(Rect rect, int selectedColor, double mouseX, double mouseY) {
        Hsv selected = hsvFromColor(selectedColor);
        float hue = selected.s() <= 0.02F ? 0.0F : selected.h();
        Rect hueStrip = colorPickerHueField(rect);
        if (hueStrip.contains(mouseX, mouseY)) {
            float pickedHue = (float) Math.max(0.0D, Math.min(1.0D, (mouseY - hueStrip.y()) / Math.max(1, hueStrip.height() - 1)));
            float saturation = Math.max(0.72F, selected.s());
            float value = Math.max(0.72F, selected.v());
            return hsv(pickedHue, saturation, value);
        }
        Rect field = colorPickerSaturationValueField(rect);
        float saturation = (float) Math.max(0.0D, Math.min(1.0D, (mouseX - field.x()) / Math.max(1, field.width() - 1)));
        float value = 1.0F - (float) Math.max(0.0D, Math.min(1.0D, (mouseY - field.y()) / Math.max(1, field.height() - 1)));
        return hsv(hue, saturation, value);
    }

    public static Rect colorPickerField(Rect rect) {
        return new Rect(rect.x() + 8, rect.y() + 20, rect.width() - 16, rect.height() - 28);
    }

    private static Rect colorPickerSaturationValueField(Rect rect) {
        int fieldX = rect.x() + 8;
        int fieldY = rect.y() + 22;
        int hueW = 12;
        int gap = 6;
        int fieldW = Math.max(24, rect.width() - 16 - hueW - gap);
        int fieldH = Math.max(24, rect.height() - 31);
        return new Rect(fieldX, fieldY, fieldW, fieldH);
    }

    private static Rect colorPickerHueField(Rect rect) {
        Rect field = colorPickerSaturationValueField(rect);
        return new Rect(field.right() + 6, field.y(), 12, field.height());
    }

    public static Rect colorPickerClose(Rect rect) {
        return new Rect(rect.right() - 20, rect.y() + 4, 14, 14);
    }

    private static void drawColorPickerGradient(GuiGraphicsExtractor graphics, Rect field, Rect hueStrip, float hue) {
        int svBands = Math.max(1, Math.min(COLOR_PICKER_MAX_SV_BANDS, field.height()));
        List<SmoothGuiPrimitives.GradientQuad> quads = new ArrayList<>(svBands + 6);
        int hueColor = hsv(hue, 1.0F, 1.0F);
        for (int band = 0; band < svBands; band++) {
            int y0 = field.y() + field.height() * band / svBands;
            int y1 = field.y() + field.height() * (band + 1) / svBands;
            if (y1 <= y0) {
                continue;
            }
            float value0 = 1.0F - (y0 - field.y()) / (float) Math.max(1, field.height() - 1);
            float value1 = 1.0F - (Math.max(y0, y1 - 1) - field.y()) / (float) Math.max(1, field.height() - 1);
            int left0 = gray(value0);
            int right0 = scaleRgb(hueColor, value0);
            int left1 = gray(value1);
            int right1 = scaleRgb(hueColor, value1);
            quads.add(new SmoothGuiPrimitives.GradientQuad(
                    new Vec2(field.x(), y0),
                    new Vec2(field.right(), y0),
                    new Vec2(field.right(), y1),
                    new Vec2(field.x(), y1),
                    left0,
                    right0,
                    right1,
                    left1
            ));
        }

        for (int segment = 0; segment < 6; segment++) {
            int y0 = hueStrip.y() + hueStrip.height() * segment / 6;
            int y1 = hueStrip.y() + hueStrip.height() * (segment + 1) / 6;
            if (y1 <= y0) {
                continue;
            }
            int top = hsv(segment / 6.0F, 0.94F, 1.0F);
            int bottom = hsv((segment + 1) / 6.0F, 0.94F, 1.0F);
            quads.add(new SmoothGuiPrimitives.GradientQuad(
                    new Vec2(hueStrip.x(), y0),
                    new Vec2(hueStrip.right(), y0),
                    new Vec2(hueStrip.right(), y1),
                    new Vec2(hueStrip.x(), y1),
                    top,
                    top,
                    bottom,
                    bottom
            ));
        }

        SmoothGuiPrimitives.quads(graphics, quads);
    }

    private static int hsv(float h, float s, float v) {
        int i = (int) Math.floor(h * 6.0F);
        float f = h * 6.0F - i;
        float p = v * (1.0F - s);
        float q = v * (1.0F - f * s);
        float t = v * (1.0F - (1.0F - f) * s);
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | ((int) (r * 255.0F) << 16) | ((int) (g * 255.0F) << 8) | (int) (b * 255.0F);
    }

    private static int gray(float value) {
        int channel = Math.max(0, Math.min(255, Math.round(value * 255.0F)));
        return 0xFF000000 | channel << 16 | channel << 8 | channel;
    }

    private static int scaleRgb(int color, float value) {
        int red = Math.max(0, Math.min(255, Math.round(((color >>> 16) & 0xFF) * value)));
        int green = Math.max(0, Math.min(255, Math.round(((color >>> 8) & 0xFF) * value)));
        int blue = Math.max(0, Math.min(255, Math.round((color & 0xFF) * value)));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static Hsv hsvFromColor(int color) {
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue;
        if (delta <= 1.0E-6F) {
            hue = 0.0F;
        } else if (max == r) {
            hue = ((g - b) / delta) % 6.0F;
        } else if (max == g) {
            hue = (b - r) / delta + 2.0F;
        } else {
            hue = (r - g) / delta + 4.0F;
        }
        hue /= 6.0F;
        if (hue < 0.0F) {
            hue += 1.0F;
        }
        float saturation = max <= 1.0E-6F ? 0.0F : delta / max;
        return new Hsv(hue, saturation, max);
    }

    private record Hsv(float h, float s, float v) {
    }

    private static void fillOctagon(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
        if (size <= 4) {
            graphics.fill(x, y, x + size, y + size, color);
            return;
        }
        graphics.fill(x + 2, y, x + size - 2, y + size, color);
        graphics.fill(x, y + 2, x + size, y + size - 2, color);
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, color);
    }

    private static void fillCapsule(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        if (width <= 4 || height <= 4) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + 2, y, x + width - 2, y + height, color);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
        graphics.fill(x, y + 2, x + width, y + height - 2, color);
    }

    private static List<Integer> metroColors(List<Integer> colors) {
        if (colors == null || colors.isEmpty()) {
            return List.of(0xFF3366FF);
        }
        return colors.stream().limit(3).map(SPSGui::opaque).toList();
    }

    public static String translatedNamesLine(List<String> translatedNames) {
        if (translatedNames == null || translatedNames.isEmpty()) {
            return "";
        }
        return String.join(" / ", translatedNames);
    }

    public static List<StationNode> nodesForLayout(RouteLayout layout) {
        return ClientRouteViewModelCache.nodesForLayout(layout);
    }

    public static RouteSectionStatus loopStatus(RouteLayout layout) {
        return ClientRouteViewModelCache.loopStatus(layout);
    }

    private static String shortLineName(String name) {
        String trimmed = name.trim();
        return trimmed.length() <= 12 ? trimmed : trimmed.substring(0, 12);
    }

    public static List<StationNode> representativeNodes(RouteLine line) {
        return ClientRouteViewModelCache.representativeNodes(line);
    }

    public static Optional<RouteLayout> representativeLayout(RouteLine line) {
        return ClientRouteViewModelCache.representativeLayout(line);
    }

    public static String lineStatus(RouteLine line) {
        return lineSummary(line).status();
    }

    public static LineSummary lineSummary(RouteLine line) {
        return ClientRouteViewModelCache.lineSummary(line);
    }

    public static String layoutStatus(RouteLayout layout) {
        return ClientRouteViewModelCache.layoutStatus(layout);
    }

    public static int statusColor(String status) {
        return switch (status) {
            case "Broken" -> DANGER;
            case "Ambiguous" -> ORANGE;
            case "Incomplete" -> WARNING;
            case "Stale" -> WARNING;
            case "Disabled" -> TEXT_DISABLED;
            case "Valid" -> SUCCESS;
            default -> TEXT_MUTED;
        };
    }

    public static String layoutName(RouteLayout layout) {
        return layout.displayName().orElse(Component.translatable("screen.superpipeslide.layout.default_name").getString());
    }

    public static int opaque(int color) {
        return 0xFF000000 | color & 0x00FFFFFF;
    }

    public static int withAlpha(int color, int alpha) {
        return (alpha & 0xFF) << 24 | color & 0x00FFFFFF;
    }

    public static String ellipsize(Font font, String text, int maxWidth) {
        if (font == null || font.width(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int suffixWidth = font.width(suffix);
        String value = text;
        while (!value.isEmpty() && font.width(value) + suffixWidth > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + suffix;
    }

    public static boolean contains(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return this.x + this.width;
        }

        public int bottom() {
            return this.y + this.height;
        }

        public boolean contains(double mouseX, double mouseY) {
            return SPSGui.contains(this.x, this.y, this.width, this.height, mouseX, mouseY);
        }
    }

    public record IconButton(Icon icon, int color, boolean disabled) {
        public static IconButton of(Icon icon) {
            return new IconButton(icon, TEXT_SECONDARY, false);
        }
    }

    public enum Icon {
        BACK(0, 0),
        PLUS(0, 1),
        SAVE(0, 2),
        EDIT(0, 3),
        REMOVE(0, 4),
        REFRESH(0, 5),
        ITEM_PLUS(0, 6),
        SEARCH(0, 7),
        DRAG(0, 8),
        CLOSE(0, 9),
        CONFIRM(0, 10),
        WARNING(0, 11),
        CHECKBOX_OFF(0, 12),
        CHECKBOX_ON(0, 13),
        COLOR_ADD(0, 14),
        INFO(0, 15),
        FIT(1, 0),
        LOCATE(1, 1),
        MODE_PHYSICAL(1, 2),
        MODE_GEOGRAPHIC(1, 3),
        MODE_PRACTICAL(1, 4),
        MODE_SCHEMATIC(1, 5),
        RESET_VIEW(1, 6),
        ZOOM_IN(1, 7),
        ZOOM_OUT(1, 8),
        ROUTE_LINE(1, 9),
        LAYOUT(1, 10),
        STATION_ORDER(1, 11),
        MAP(1, 12),
        CAMERA_TOP_DOWN(1, 13),
        CAMERA_TILTED(1, 14),
        RESERVED_UI_2(1, 15),
        BIDIRECTIONAL(2, 0),
        ONE_WAY(2, 1),
        LOOP(2, 2),
        SPLIT(2, 3),
        RECALCULATE(2, 4),
        ERROR(2, 5),
        SUCCESS(2, 6),
        RESERVED_ACTION_0(2, 7),
        RESERVED_ACTION_1(2, 8),
        RESERVED_ACTION_2(2, 9),
        RESERVED_ACTION_3(2, 10),
        RESERVED_ACTION_4(2, 11),
        RESERVED_ACTION_5(2, 12),
        RESERVED_ACTION_6(2, 13),
        RESERVED_ACTION_7(2, 14),
        RESERVED_ACTION_8(2, 15),
        PIPE_ACCELERATION(3, 0),
        PIPE_HIGHWAY(3, 1),
        PIPE_GLOW(3, 2),
        PIPE_BACKPACK(3, 3),
        PIPE_RECOMMENDED(3, 4),
        PIPE_PLATFORM(3, 5),
        LAYERS(3, 6),
        SETTINGS(3, 7),
        RESERVED_8(3, 8),
        RESERVED_9(3, 9),
        RESERVED_10(3, 10),
        RESERVED_11(3, 11),
        RESERVED_12(3, 12),
        RESERVED_13(3, 13),
        RESERVED_14(3, 14),
        RESERVED_15(3, 15);

        private final float textureX;
        private final float textureY;

        Icon(int row, int column) {
            this.textureX = column * ICON_CELL_SIZE;
            this.textureY = row * ICON_CELL_SIZE;
        }

        private float textureX() {
            return this.textureX;
        }

        private float textureY() {
            return this.textureY;
        }
    }

    public record StationNode(String name, List<TransferLine> transferLines, boolean error, RouteSectionStatus incomingStatus, boolean missing) {
        public StationNode(String name, List<TransferLine> transferLines, boolean error, RouteSectionStatus incomingStatus) {
            this(name, transferLines, error, incomingStatus, false);
        }
    }

    public record TransferLine(String shortName, List<Integer> colors) {
    }

    public record LineSummary(int layoutCount, int stationCount, int problemCount, String status) {
    }
}
