package dev.marblegate.superpipeslide.client.fullmap.routecard.render;

import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapLayoutMode;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.render.FullRouteMapRenderer;
import dev.marblegate.superpipeslide.client.fullmap.render.SmoothGuiPrimitives;
import dev.marblegate.superpipeslide.client.fullmap.routecard.hit.LayoutChipHit;
import dev.marblegate.superpipeslide.client.fullmap.routecard.hit.RouteLineCardHit;
import dev.marblegate.superpipeslide.client.fullmap.routecard.hit.RouteLineCardHitKind;
import dev.marblegate.superpipeslide.client.fullmap.routecard.hit.ViewModeChipHit;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardEdge;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNode;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNodeId;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNodeKind;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardSemanticGraph;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardViewMode;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardViewport;
import dev.marblegate.superpipeslide.client.fullmap.routecard.visual.RouteCardVisualEdge;
import dev.marblegate.superpipeslide.client.fullmap.routecard.visual.RouteCardVisualGraph;
import dev.marblegate.superpipeslide.client.fullmap.routecard.visual.RouteCardVisualLabel;
import dev.marblegate.superpipeslide.client.fullmap.routecard.visual.RouteCardVisualNode;
import dev.marblegate.superpipeslide.client.fullmap.routecard.visual.RouteCardVisualSegment;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SemanticEdgeKind;
import dev.marblegate.superpipeslide.client.fullmap.ui.DisplayNameStack;
import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapText;
import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapTheme;
import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapTooltipCard;
import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapUi;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class RouteLineCardRenderer {
    public RouteLineCardRenderResult render(
            GuiGraphicsExtractor graphics,
            Font font,
            RouteLine line,
            RouteLayout selectedLayout,
            List<RouteLayout> layouts,
            RouteCardViewMode viewMode,
            RouteCardSemanticGraph stopListGraph,
            RouteCardSemanticGraph semanticGraph,
            RouteCardVisualGraph visualGraph,
            RouteCardViewport viewport,
            SPSGui.Rect bounds,
            double layoutScroll,
            double stopListScroll,
            boolean active,
            int mouseX,
            int mouseY
    ) {
        SPSGui.colorStripe(graphics, bounds.x(), bounds.y(), bounds.height(), line.themeColors());
        DisplayNameStack lineName = FullMapText.displayNameStack(line);
        FullMapUi.drawNameStack(graphics, font, lineName, bounds.x() + 8, bounds.y() + 4, bounds.width() - 52, FullMapTheme.TEXT_PRIMARY, FullMapTheme.TEXT_MUTED, 1.0F, FullMapTheme.TYPE_META, 0);

        SPSGui.Rect strip = new SPSGui.Rect(bounds.x() + 8, bounds.y() + 27, bounds.width() - 32, 15);
        LayoutStripRenderResult stripResult = this.renderLayoutStrip(graphics, font, layouts, selectedLayout.id(), strip, layoutScroll, active, mouseX, mouseY);
        String layoutName = FullMapText.primaryName(selectedLayout);
        String direction = selectedLayout.bidirectional()
                ? Component.translatable("screen.superpipeslide.layout.bidirectional").getString()
                : Component.translatable("screen.superpipeslide.direction.forward").getString();
        String loop = selectedLayout.loop() ? " · " + Component.translatable("screen.superpipeslide.layout.loop").getString() : "";
        String info = Component.translatable("screen.superpipeslide.full_map.route_card.layout_info", layoutName, semanticGraph.summary().stationCount(), direction, loop).getString();
        SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, info, Math.round((bounds.width() - 16) / FullMapTheme.TYPE_META)), bounds.x() + 8, bounds.y() + 45, FullMapTheme.TEXT_SECONDARY, FullMapTheme.TYPE_META);

        SPSGui.Rect content = contentBounds(bounds);
        int stopListWidth = stopListWidth(bounds);
        SPSGui.Rect stopList = new SPSGui.Rect(content.x(), content.y(), stopListWidth, content.height());
        SPSGui.Rect map = new SPSGui.Rect(stopList.right() + 6, content.y(), Math.max(56, content.right() - stopList.right() - 6), content.height());
        SPSGui.Rect fitViewport = new SPSGui.Rect(map.right() - 20, map.bottom() - 20, FullMapTheme.ICON_BUTTON_SMALL, FullMapTheme.ICON_BUTTON_SMALL);
        SPSGui.Rect viewModeStrip = this.viewModeStripBounds(map, viewMode);
        boolean overMapControl = active && (viewModeStrip.contains(mouseX, mouseY) || fitViewport.contains(mouseX, mouseY));

        RouteCardVisualGraph screenGraph = screenGraph(visualGraph, viewport, map);
        RouteLineCardHit mapHover = active && !overMapControl && map.contains(mouseX, mouseY)
                ? this.hitTest(screenGraph, map, mouseX, mouseY, viewport.zoom()).orElse(RouteLineCardHit.none())
                : RouteLineCardHit.none();
        Set<UUID> highlightedStopGroups = highlightedStationGroups(semanticGraph, mapHover);
        StopListRenderResult stopResult = this.renderStopListView(graphics, font, line, selectedLayout, stopListGraph, stopList, stopListScroll, active, mouseX, mouseY, highlightedStopGroups);
        FullRouteMapRenderer.drawMapBackground(graphics, map, viewport.centerX(), viewport.centerY(), scale(viewport), routeCardLayoutMode(viewMode));
        graphics.outline(map.x(), map.y(), map.width(), map.height(), FullMapTheme.BORDER);
        RouteLineCardHit hover = mapHover.kind() == RouteLineCardHitKind.NONE ? stopResult.hover() : mapHover;
        graphics.enableScissor(map.x(), map.y(), map.right(), map.bottom());
        if (viewMode == RouteCardViewMode.PHYSICAL) {
            this.drawStationPlatformFrames(graphics, font, semanticGraph, screenGraph, viewport.zoom());
        }
        this.drawEdges(graphics, screenGraph, hover);
        this.drawNodes(graphics, font, semanticGraph, screenGraph, hover, viewport.zoom());
        List<SPSGui.Rect> labelRects = this.drawLabels(graphics, font, screenGraph, viewport.zoom(), map);
        Optional<Component> dimensionTooltip = this.drawSegmentDimensionMarks(graphics, font, screenGraph, map, viewport.zoom(), mouseX, mouseY, labelRects);
        graphics.disableScissor();
        this.drawViewportHints(graphics, map, visualGraph.bounds(), viewport);
        this.drawFitViewportButton(graphics, fitViewport, active && fitViewport.contains(mouseX, mouseY));
        if (semanticGraph.nodes().isEmpty()) {
            SPSGui.centeredText(graphics, font, Component.translatable("screen.superpipeslide.full_map.route_card.map_empty"), map.x() + map.width() / 2, map.y() + map.height() / 2 - 4, FullMapTheme.TEXT_MUTED);
        }
        List<ViewModeChipHit> viewModeChips = this.renderViewModeStrip(graphics, map, viewMode, active, mouseX, mouseY);

        int summaryY = bounds.bottom() - 18;
        String summary = Component.translatable(
                "screen.superpipeslide.full_map.route_card.summary_compact",
                semanticGraph.summary().stationCount(),
                semanticGraph.summary().sectionCount(),
                semanticGraph.summary().crossDimensionCount(),
                semanticGraph.summary().stationInternalCount()
        ).getString();
        SPSGui.Rect locateFirst = new SPSGui.Rect(bounds.x() + 8, bounds.bottom() - 22, FullMapTheme.ICON_BUTTON_SMALL, FullMapTheme.ICON_BUTTON_SMALL);
        SPSGui.Rect locateLayout = new SPSGui.Rect(locateFirst.right() + 4, bounds.bottom() - 22, FullMapTheme.ICON_BUTTON_SMALL, FullMapTheme.ICON_BUTTON_SMALL);
        int summaryX = locateLayout.right() + 7;
        int summaryWidth = Math.max(20, bounds.right() - summaryX - 8);
        SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, summary, Math.round(summaryWidth / FullMapTheme.TYPE_TINY)), summaryX, summaryY, FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_TINY);
        if ((!semanticGraph.diagnostics().isEmpty() || visualGraph.fallback()) && summaryWidth > 90) {
            String diagnostic = Component.translatable("screen.superpipeslide.full_map.route_card.diagnostics", semanticGraph.diagnostics().size() + (visualGraph.fallback() ? 1 : 0)).getString();
            SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, diagnostic, Math.round(summaryWidth / FullMapTheme.TYPE_TINY)), summaryX, summaryY + 8, SPSGui.WARNING, FullMapTheme.TYPE_TINY);
        }

        FullMapUi.iconButton(graphics, locateFirst, active && locateFirst.contains(mouseX, mouseY), false, false, SPSGui.Icon.LOCATE);
        FullMapUi.iconButton(graphics, locateLayout, active && locateLayout.contains(mouseX, mouseY), false, false, SPSGui.Icon.FIT);
        return new RouteLineCardRenderResult(strip, stripResult.maxScroll(), stripResult.chips(), viewModeChips, viewModeStrip, stopList, map, fitViewport, locateFirst, locateLayout, stopResult.maxScroll(), hover, active ? dimensionTooltip : Optional.empty());
    }

    public static RouteCardViewport fitViewport(Aabb2 bounds, SPSGui.Rect map) {
        if (bounds.isEmpty()) {
            return new RouteCardViewport(0.0D, 0.0D, FullRouteMapConfig.DEFAULT_ZOOM);
        }
        double paddedWidth = Math.max(1.0D, bounds.maxX() - bounds.minX() + 72.0D);
        double paddedHeight = Math.max(1.0D, bounds.maxY() - bounds.minY() + 72.0D);
        double fitScale = Math.min(Math.max(1.0D, map.width()) / paddedWidth, Math.max(1.0D, map.height()) / paddedHeight);
        double zoom = fitScale / FullRouteMapConfig.BASE_SCALE;
        return new RouteCardViewport(bounds.centerX(), bounds.centerY(), zoom * 0.98D);
    }

    public static Vec2 screenToWorld(double screenX, double screenY, RouteCardViewport viewport, SPSGui.Rect map) {
        double s = scale(viewport);
        return new Vec2(
                viewport.centerX() + (screenX - (map.x() + map.width() * 0.5D)) / s,
                viewport.centerY() + (screenY - (map.y() + map.height() * 0.5D)) / s
        );
    }

    public static double scale(RouteCardViewport viewport) {
        return viewport.zoom() * FullRouteMapConfig.BASE_SCALE;
    }

    public static SPSGui.Rect mapBoundsForCard(SPSGui.Rect bounds) {
        SPSGui.Rect content = contentBounds(bounds);
        int stopListWidth = stopListWidth(bounds);
        return new SPSGui.Rect(content.x() + stopListWidth + 6, content.y(), Math.max(56, content.width() - stopListWidth - 6), content.height());
    }

    private static SPSGui.Rect contentBounds(SPSGui.Rect bounds) {
        return new SPSGui.Rect(bounds.x() + 8, bounds.y() + 60, bounds.width() - 16, Math.max(56, bounds.height() - 86));
    }

    private static int stopListWidth(SPSGui.Rect bounds) {
        int preferred = Math.max(100, Math.min(122, (int) Math.round(bounds.width() * 0.24D)));
        return Math.min(preferred, Math.max(92, bounds.width() - 180));
    }

    private static RouteCardVisualGraph screenGraph(RouteCardVisualGraph graph, RouteCardViewport viewport, SPSGui.Rect map) {
        List<RouteCardVisualNode> nodes = graph.nodes().stream()
                .map(node -> new RouteCardVisualNode(node.node(), worldToScreen(node.position(), viewport, map), node.priority()))
                .toList();
        List<RouteCardVisualEdge> edges = graph.edges().stream()
                .map(edge -> {
                    List<Vec2> points = edge.points().stream().map(point -> worldToScreen(point, viewport, map)).toList();
                    return new RouteCardVisualEdge(edge.edge(), points, boundsFor(points).inflate(8.0D));
                })
                .toList();
        List<RouteCardVisualSegment> segments = graph.segments().stream()
                .map(segment -> new RouteCardVisualSegment(segment.segment(), screenBounds(segment.bounds(), viewport, map)))
                .toList();
        List<RouteCardVisualLabel> labels = graph.labels().stream()
                .map(label -> new RouteCardVisualLabel(label.nodeId(), label.text(), worldToScreen(label.position(), viewport, map), label.priority(), label.scale()))
                .toList();
        return new RouteCardVisualGraph(nodes, edges, segments, labels, screenBounds(graph.bounds(), viewport, map), graph.fallback());
    }

    private static Vec2 worldToScreen(Vec2 point, RouteCardViewport viewport, SPSGui.Rect map) {
        double s = scale(viewport);
        return new Vec2(
                map.x() + map.width() * 0.5D + (point.x() - viewport.centerX()) * s,
                map.y() + map.height() * 0.5D + (point.y() - viewport.centerY()) * s
        );
    }

    private static Aabb2 screenBounds(Aabb2 bounds, RouteCardViewport viewport, SPSGui.Rect map) {
        if (bounds.isEmpty()) {
            return bounds;
        }
        Vec2 a = worldToScreen(new Vec2(bounds.minX(), bounds.minY()), viewport, map);
        Vec2 b = worldToScreen(new Vec2(bounds.maxX(), bounds.minY()), viewport, map);
        Vec2 c = worldToScreen(new Vec2(bounds.maxX(), bounds.maxY()), viewport, map);
        Vec2 d = worldToScreen(new Vec2(bounds.minX(), bounds.maxY()), viewport, map);
        return Aabb2.empty()
                .include(a.x(), a.y())
                .include(b.x(), b.y())
                .include(c.x(), c.y())
                .include(d.x(), d.y());
    }

    private void drawViewportHints(GuiGraphicsExtractor graphics, SPSGui.Rect map, Aabb2 worldBounds, RouteCardViewport viewport) {
        if (worldBounds.isEmpty()) {
            return;
        }
        Aabb2 screenBounds = screenBounds(worldBounds.inflate(24.0D), viewport, map);
        boolean left = screenBounds.minX() < map.x();
        boolean right = screenBounds.maxX() > map.right();
        boolean up = screenBounds.minY() < map.y();
        boolean down = screenBounds.maxY() > map.bottom();
        int fade = 0x44EEF2F7;
        if (left) {
            graphics.fill(map.x(), map.y(), map.x() + 8, map.bottom(), fade);
            drawChevron(graphics, map.x() + 4, map.y() + map.height() * 0.5D, -1, 0);
        }
        if (right) {
            graphics.fill(map.right() - 8, map.y(), map.right(), map.bottom(), fade);
            drawChevron(graphics, map.right() - 4, map.y() + map.height() * 0.5D, 1, 0);
        }
        if (up) {
            graphics.fill(map.x(), map.y(), map.right(), map.y() + 8, fade);
            drawChevron(graphics, map.x() + map.width() * 0.5D, map.y() + 4, 0, -1);
        }
        if (down) {
            graphics.fill(map.x(), map.bottom() - 8, map.right(), map.bottom(), fade);
            drawChevron(graphics, map.x() + map.width() * 0.5D, map.bottom() - 4, 0, 1);
        }
    }

    private static void drawChevron(GuiGraphicsExtractor graphics, double x, double y, int dx, int dy) {
        int color = 0xAA4B5563;
        if (dx < 0) {
            SmoothGuiPrimitives.line(graphics, new Vec2(x + 3.0D, y - 5.0D), new Vec2(x - 2.0D, y), 1.25D, color);
            SmoothGuiPrimitives.line(graphics, new Vec2(x - 2.0D, y), new Vec2(x + 3.0D, y + 5.0D), 1.25D, color);
        } else if (dx > 0) {
            SmoothGuiPrimitives.line(graphics, new Vec2(x - 3.0D, y - 5.0D), new Vec2(x + 2.0D, y), 1.25D, color);
            SmoothGuiPrimitives.line(graphics, new Vec2(x + 2.0D, y), new Vec2(x - 3.0D, y + 5.0D), 1.25D, color);
        } else if (dy < 0) {
            SmoothGuiPrimitives.line(graphics, new Vec2(x - 5.0D, y + 3.0D), new Vec2(x, y - 2.0D), 1.25D, color);
            SmoothGuiPrimitives.line(graphics, new Vec2(x, y - 2.0D), new Vec2(x + 5.0D, y + 3.0D), 1.25D, color);
        } else if (dy > 0) {
            SmoothGuiPrimitives.line(graphics, new Vec2(x - 5.0D, y - 3.0D), new Vec2(x, y + 2.0D), 1.25D, color);
            SmoothGuiPrimitives.line(graphics, new Vec2(x, y + 2.0D), new Vec2(x + 5.0D, y - 3.0D), 1.25D, color);
        }
    }

    private LayoutStripRenderResult renderLayoutStrip(GuiGraphicsExtractor graphics, Font font, List<RouteLayout> layouts, UUID selectedLayoutId, SPSGui.Rect strip, double scroll, boolean active, int mouseX, int mouseY) {
        List<LayoutChipHit> chips = new ArrayList<>();
        graphics.fill(strip.x(), strip.y(), strip.right(), strip.bottom(), FullMapTheme.SURFACE_CONTROL_DISABLED);
        graphics.enableScissor(strip.x(), strip.y(), strip.right(), strip.bottom());
        int contentX = strip.x() - (int) Math.round(scroll);
        int x = contentX + 3;
        for (RouteLayout layout : layouts) {
            String name = FullMapText.primaryName(layout);
            int chipWidth = Math.max(FullRouteMapConfig.CARD_LINE_STRIP_CHIP_MIN_PX, Math.min(FullRouteMapConfig.CARD_LINE_STRIP_CHIP_MAX_PX + 18, font.width(name) + 12));
            SPSGui.Rect chip = new SPSGui.Rect(x, strip.y() + 1, chipWidth, strip.height() - 3);
            boolean selected = layout.id().equals(selectedLayoutId);
            boolean hovered = active && chip.contains(mouseX, mouseY) && strip.contains(mouseX, mouseY);
            graphics.fill(chip.x(), chip.y(), chip.right(), chip.bottom(), selected ? FullMapTheme.SURFACE_CONTROL_SELECTED : hovered ? FullMapTheme.SURFACE_CONTROL_HOVER : FullMapTheme.SURFACE_CONTROL);
            graphics.outline(chip.x(), chip.y(), chip.width(), chip.height(), selected ? FullMapTheme.BORDER_SELECTED : FullMapTheme.BORDER);
            String display = SPSGui.scrollingText(font, name, chip.width() - 8, layout.id().hashCode());
            SPSGui.smallText(graphics, font, display, chip.x() + 5, chip.y() + 3, selected ? SPSGui.INFO : FullMapTheme.TEXT_SECONDARY, FullMapTheme.TYPE_META);
            chips.add(new LayoutChipHit(layout.id(), clipRect(chip, strip), FullMapText.primaryName(layout)));
            x += chipWidth + 3;
        }
        graphics.disableScissor();
        int contentWidth = Math.max(0, x - contentX);
        double maxScroll = Math.max(0.0D, contentWidth - strip.width() + 6.0D);
        if (maxScroll > 0.0D) {
            int barWidth = Math.max(16, (int) Math.round(strip.width() * strip.width() / (double) contentWidth));
            int barX = strip.x() + (int) Math.round((strip.width() - barWidth) * Math.min(1.0D, scroll / maxScroll));
            graphics.fill(barX, strip.bottom() - 2, barX + barWidth, strip.bottom(), SPSGui.withAlpha(SPSGui.INFO, 0x88));
        }
        graphics.outline(strip.x(), strip.y(), strip.width(), strip.height(), FullMapTheme.BORDER);
        return new LayoutStripRenderResult(chips, maxScroll);
    }

    private List<ViewModeChipHit> renderViewModeStrip(GuiGraphicsExtractor graphics, SPSGui.Rect map, RouteCardViewMode selectedMode, boolean active, int mouseX, int mouseY) {
        List<ViewModeChipHit> chips = new ArrayList<>();
        RouteCardViewMode[] modes = RouteCardViewMode.values();
        int size = FullMapTheme.ICON_BUTTON;
        int gap = 2;
        SPSGui.Rect panel = this.viewModeStripBounds(map, selectedMode);
        int x = panel.x() + 3;
        int y = panel.y() + 3;
        FullMapUi.toolbarPanel(graphics, panel);
        for (RouteCardViewMode mode : modes) {
            SPSGui.Rect chip = new SPSGui.Rect(x, y, size, size);
            boolean selected = mode == selectedMode;
            boolean hovered = active && chip.contains(mouseX, mouseY);
            FullMapUi.iconButton(graphics, chip, hovered, selected, false, viewModeGlyph(mode));
            chips.add(new ViewModeChipHit(mode, chip));
            x += size + gap;
        }
        return chips;
    }

    private SPSGui.Rect viewModeStripBounds(SPSGui.Rect map, RouteCardViewMode selectedMode) {
        RouteCardViewMode[] modes = RouteCardViewMode.values();
        int size = FullMapTheme.ICON_BUTTON;
        int gap = 2;
        int panelWidth = modes.length * size + (modes.length - 1) * gap + 6;
        int margin = 6;
        int x = map.x() + margin;
        int y = map.bottom() - margin - size - 6;
        return new SPSGui.Rect(x, y, panelWidth, size + 6);
    }

    private static SPSGui.Icon viewModeGlyph(RouteCardViewMode mode) {
        return switch (mode) {
            case PHYSICAL -> SPSGui.Icon.MODE_PHYSICAL;
            case PRACTICAL -> SPSGui.Icon.MODE_PRACTICAL;
            case SCHEMATIC -> SPSGui.Icon.MODE_SCHEMATIC;
        };
    }

    private static FullRouteMapLayoutMode routeCardLayoutMode(RouteCardViewMode mode) {
        return switch (mode) {
            case PHYSICAL -> FullRouteMapLayoutMode.PHYSICAL;
            case PRACTICAL -> FullRouteMapLayoutMode.PRACTICAL;
            case SCHEMATIC -> FullRouteMapLayoutMode.SCHEMATIC;
        };
    }

    private StopListRenderResult renderStopListView(
            GuiGraphicsExtractor graphics,
            Font font,
            RouteLine line,
            RouteLayout selectedLayout,
            RouteCardSemanticGraph semanticGraph,
            SPSGui.Rect area,
            double scroll,
            boolean active,
            int mouseX,
            int mouseY,
            Set<UUID> externallyHighlightedStationGroups
    ) {
        graphics.fill(area.x(), area.y(), area.right(), area.bottom(), FullMapTheme.SURFACE_CONTROL_DISABLED);
        graphics.outline(area.x(), area.y(), area.width(), area.height(), FullMapTheme.BORDER);
        List<RouteCardNode> stops = semanticGraph.nodes().stream()
                .filter(node -> node.kind() == RouteCardNodeKind.STATION)
                .sorted(Comparator.comparingInt(RouteCardNode::layoutOccurrence).thenComparing(RouteCardNode::id))
                .toList();
        if (stops.isEmpty()) {
            SPSGui.centeredText(graphics, font, Component.translatable("screen.superpipeslide.full_map.route_card.map_empty"), area.x() + area.width() / 2, area.y() + area.height() / 2 - 4, FullMapTheme.TEXT_MUTED);
            return new StopListRenderResult(RouteLineCardHit.none(), 0.0D);
        }

        RouteLineCardHit hover = RouteLineCardHit.none();
        int rowHeight = 24;
        int contentTop = area.y() + 5;
        int contentHeight = 12 + stops.size() * rowHeight;
        double maxScroll = Math.max(0.0D, contentHeight - area.height());
        double clampedScroll = Math.max(0.0D, Math.min(maxScroll, scroll));
        int railX = area.x() + 13;
        int indexX = area.x() + 23;
        int nameX = area.x() + 35;
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        for (int i = 0; i < stops.size(); i++) {
            RouteCardNode stop = stops.get(i);
            int y = (int) Math.round(contentTop + i * rowHeight - clampedScroll);
            if (y > area.bottom() || y + rowHeight < area.y()) {
                continue;
            }
            SPSGui.Rect row = new SPSGui.Rect(area.x() + 4, y - 2, area.width() - 8, rowHeight - 2);
            boolean hovered = active && row.contains(mouseX, mouseY);
            boolean linkedHover = stop.stationGroupId().filter(externallyHighlightedStationGroups::contains).isPresent();
            if (hovered) {
                graphics.fill(row.x(), row.y(), row.right(), row.bottom(), FullMapTheme.HIGHLIGHT_SOFT);
                hover = RouteLineCardHit.node(stop);
            } else if (linkedHover) {
                graphics.fill(row.x(), row.y(), row.right(), row.bottom(), SPSGui.withAlpha(SPSGui.INFO, 0x16));
            }
        }
        for (int i = 0; i < stops.size(); i++) {
            int y = (int) Math.round(contentTop + i * rowHeight - clampedScroll);
            if (y > area.bottom() || y + rowHeight < area.y()) {
                continue;
            }
            if (i > 0) {
                drawColorLanePath(graphics, List.of(new Vec2(railX, y - 9.0D), new Vec2(railX, y + 4.0D)), Math.max(2.0D, FullRouteMapConfig.LINE_WIDTH_PX - 0.5D), line.themeColors());
            }
            if (i + 1 < stops.size()) {
                drawColorLanePath(graphics, List.of(new Vec2(railX, y + 14.0D), new Vec2(railX, y + rowHeight + 4.0D)), Math.max(2.0D, FullRouteMapConfig.LINE_WIDTH_PX - 0.5D), line.themeColors());
            } else if (selectedLayout.loop() && stops.size() > 1) {
                double endY = area.bottom() - 1.0D;
                if (endY > y + 14.0D) {
                    drawColorLanePath(graphics, List.of(new Vec2(railX, y + 14.0D), new Vec2(railX, endY)), Math.max(2.0D, FullRouteMapConfig.LINE_WIDTH_PX - 0.5D), line.themeColors());
                }
            }
        }
        for (int i = 0; i < stops.size(); i++) {
            RouteCardNode stop = stops.get(i);
            int y = (int) Math.round(contentTop + i * rowHeight - clampedScroll);
            if (y > area.bottom() || y + rowHeight < area.y()) {
                continue;
            }
            SPSGui.Rect row = new SPSGui.Rect(area.x() + 4, y - 2, area.width() - 8, rowHeight - 2);
            if (active && row.contains(mouseX, mouseY)) {
                hover = RouteLineCardHit.node(stop);
            }
            SmoothGuiPrimitives.circle(graphics, new Vec2(railX, y + 9.0D), 5.2D, FullRouteMapConfig.MAP_CARD_NODE_OUTLINE);
            SmoothGuiPrimitives.circle(graphics, new Vec2(railX, y + 9.0D), 4.0D, FullRouteMapConfig.MAP_NODE_FILL);
            String index = Integer.toString(stop.layoutOccurrence() + 1);
            SPSGui.smallText(graphics, font, index, indexX, y + 5, FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_TINY);

            String dimension = dimensionLabel(stop.levelKey());
            float dimensionScale = 0.54F;
            int dimensionWidth = Math.max(24, Math.min(48, (int) Math.ceil(font.width(dimension) * dimensionScale) + 8));
            int chipX = area.right() - dimensionWidth - 5;
            int nameWidth = Math.max(14, chipX - nameX - 4);
            DisplayNameStack name = FullMapText.displayNameStack(stop);
            String displayName = SPSGui.scrollingText(font, name.primary(), nameWidth, stop.id().hashCode());
            SPSGui.text(graphics, font, displayName, nameX, y + 3, FullMapTheme.TEXT_PRIMARY);
            if (name.hasSecondary() && nameWidth > 28) {
                SPSGui.smallText(graphics, font, SPSGui.scrollingText(font, name.secondary(), Math.round(nameWidth / FullMapTheme.TYPE_TINY), stop.id().hashCode() ^ 0x51), nameX, y + 14, FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_TINY);
            }

            this.drawStopListDimensionBadge(graphics, font, new SPSGui.Rect(chipX, y + 7, dimensionWidth, 10), dimension, dimensionColor(stop.levelKey()));
        }
        if (maxScroll > 0.0D) {
            int barHeight = Math.max(18, (int) Math.round(area.height() * area.height() / (double) contentHeight));
            int barY = area.y() + (int) Math.round((area.height() - barHeight) * (clampedScroll / maxScroll));
            graphics.fill(area.right() - 3, barY, area.right() - 1, barY + barHeight, SPSGui.withAlpha(SPSGui.INFO, 0xAA));
        }
        graphics.disableScissor();
        return new StopListRenderResult(hover, maxScroll);
    }

    private void drawStopListDimensionBadge(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect rect, String label, int color) {
        int boxY = rect.y() - 1;
        graphics.fill(rect.x(), boxY, rect.right(), boxY + rect.height(), SPSGui.withAlpha(color, 0x22));
        graphics.outline(rect.x(), boxY, rect.width(), rect.height(), SPSGui.withAlpha(color, 0xB0));
        String text = SPSGui.ellipsize(font, label, Math.round((rect.width() - 6) / FullMapTheme.TYPE_TINY));
        SPSGui.smallText(graphics, font, text, rect.x() + 3, rect.y() + Math.max(1, (rect.height() - 7) / 2), FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_TINY);
    }

    private void drawEdges(GuiGraphicsExtractor graphics, RouteCardVisualGraph visualGraph, RouteLineCardHit hover) {
        Set<RouteCardNodeId> highlightedMissingBoundaries = highlightedMissingPathBoundaries(visualGraph, hover);
        for (RouteCardVisualEdge visualEdge : visualGraph.edges()) {
            if (visualEdge.points().isEmpty()) {
                continue;
            }
            RouteCardEdge edge = visualEdge.edge();
            boolean foldBoundaryLink = isAbstractFoldBoundaryLink(visualGraph, visualEdge);
            boolean missingBoundaryLink = isAbstractMissingPathBoundaryLink(visualGraph, visualEdge);
            boolean missingPathBoundary = isMissingPathBoundaryEdge(visualGraph, visualEdge.edge());
            boolean hovered = edgeHoveredByRouteCardHit(visualGraph, edge, hover);
            double width = FullRouteMapConfig.LINE_WIDTH_PX + 1.0D;
            if (foldBoundaryLink) {
                if (hovered) {
                    drawPolyline(graphics, visualEdge.points(), width + 4.0D, FullRouteMapConfig.MAP_FOCUS_HALO);
                    drawPolyline(graphics, visualEdge.points(), width, FullRouteMapConfig.MAP_FOCUS_RING);
                }
                continue;
            }
            if (missingBoundaryLink) {
                boolean peerHovered = hovered || highlightedMissingBoundaries.contains(edge.from()) || highlightedMissingBoundaries.contains(edge.to());
                if (peerHovered) {
                    drawPolyline(graphics, visualEdge.points(), width + 4.0D, FullRouteMapConfig.MAP_FOCUS_HALO);
                    drawDashedPolyline(graphics, visualEdge.points(), Math.max(1.4D, width - 0.4D), FullRouteMapConfig.MAP_FOCUS_RING, 7.0D, 5.0D);
                }
                continue;
            }
            if (missingPathBoundary) {
                boolean pairHovered = hovered || missingBoundaryEndpoint(visualGraph, edge).filter(highlightedMissingBoundaries::contains).isPresent();
                if (pairHovered) {
                    drawFadingDashedRouteLine(graphics, visualEdge.points(), width + 5.0D, List.of(FullRouteMapConfig.MAP_FOCUS_HALO));
                    drawFadingDashedRouteLine(graphics, visualEdge.points(), width + 1.0D, List.of(FullRouteMapConfig.MAP_FOCUS_RING));
                }
                drawFadingDashedRouteLine(graphics, visualEdge.points(), Math.max(2.0D, width - 0.2D), edge.themeColors());
                continue;
            }
            if (edge.kind() == SemanticEdgeKind.STATION_INTERNAL) {
                if (hovered) {
                    drawPolyline(graphics, visualEdge.points(), width + 5.0D, FullRouteMapConfig.MAP_FOCUS_HALO);
                }
                drawDashedColorLanePath(graphics, visualEdge.points(), Math.max(2.2D, width - 0.2D), edge.themeColors(), 6.0D, 4.0D);
                continue;
            }
            if (hovered) {
                drawPolyline(graphics, visualEdge.points(), width + 5.0D, FullRouteMapConfig.MAP_FOCUS_HALO);
            }
            if (edge.status() != RouteSectionStatus.VALID) {
                drawPolyline(graphics, visualEdge.points(), width + 2.0D, SPSGui.withAlpha(SPSGui.WARNING, 0x55));
            }
            drawColorLanePath(graphics, visualEdge.points(), width, edge.themeColors());
        }
    }

    private Optional<Component> drawSegmentDimensionMarks(
            GuiGraphicsExtractor graphics,
            Font font,
            RouteCardVisualGraph visualGraph,
            SPSGui.Rect map,
            double zoom,
            int mouseX,
            int mouseY,
            List<SPSGui.Rect> labelRects
    ) {
        Optional<Component> tooltip = Optional.empty();
        Map<RouteCardNodeId, RouteCardVisualNode> nodeById = new LinkedHashMap<>();
        for (RouteCardVisualNode node : visualGraph.nodes()) {
            nodeById.put(node.node().id(), node);
        }
        List<SPSGui.Rect> blockers = new ArrayList<>();
        visualGraph.nodes().stream()
                .map(node -> iconBounds(node.node(), node.position(), zoom))
                .forEach(blockers::add);
        blockers.addAll(labelRects == null ? List.of() : labelRects);
        List<SPSGui.Rect> placed = new ArrayList<>();
        for (RouteCardVisualSegment visualSegment : visualGraph.segments()) {
            if (visualSegment.bounds().isEmpty()) {
                continue;
            }
            ResourceKey<Level> levelKey = visualSegment.segment().levelKey();
            String label = dimensionLabel(levelKey);
            float scale = 0.54F;
            int width = Math.max(24, Math.min(92, (int) Math.ceil(font.width(label) * scale) + 10));
            int height = 10;
            RouteCardVisualNode anchor = dimensionChipAnchor(visualSegment, nodeById).orElse(null);
            SPSGui.Rect chip = chooseDimensionChipBounds(visualSegment, anchor, visualGraph.edges(), map, width, height, zoom, blockers, placed);
            placed.add(chip);
            int color = dimensionColor(levelKey);
            SmoothGuiPrimitives.capsule(graphics, new Vec2(chip.x() + width * 0.5D, chip.y() + height * 0.5D), width, height, color);
            SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, label, Math.round((width - 7) / scale)), chip.x() + 5, chip.y() + 2, readableTextColor(color), scale);
            if (tooltip.isEmpty() && chip.contains(mouseX, mouseY)) {
                tooltip = Optional.of(Component.literal(label + "\n" + levelKey.identifier()));
            }
        }
        return tooltip;
    }

    private static Optional<RouteCardVisualNode> dimensionChipAnchor(RouteCardVisualSegment visualSegment, Map<RouteCardNodeId, RouteCardVisualNode> nodeById) {
        return visualSegment.segment().nodeIds().stream()
                .map(nodeById::get)
                .filter(node -> node != null && node.node().kind() == RouteCardNodeKind.STATION)
                .min(Comparator.comparingInt((RouteCardVisualNode node) -> Math.max(0, node.node().layoutOccurrence()))
                        .thenComparing(node -> node.node().id().primaryId()))
                .or(() -> visualSegment.segment().nodeIds().stream()
                        .map(nodeById::get)
                        .filter(node -> node != null)
                        .min(Comparator.comparingInt(RouteCardVisualNode::priority).reversed()));
    }

    private void drawStationPlatformFrames(GuiGraphicsExtractor graphics, Font font, RouteCardSemanticGraph semanticGraph, RouteCardVisualGraph visualGraph, double zoom) {
        Map<UUID, List<RouteCardVisualNode>> byStation = new LinkedHashMap<>();
        for (RouteCardVisualNode visualNode : visualGraph.nodes()) {
            RouteCardNode node = visualNode.node();
            if (node.kind() == RouteCardNodeKind.STATION && node.stationGroupId().isPresent()) {
                byStation.computeIfAbsent(node.stationGroupId().get(), ignored -> new ArrayList<>()).add(visualNode);
            }
        }
        for (Map.Entry<UUID, List<RouteCardVisualNode>> entry : byStation.entrySet()) {
            List<RouteCardVisualNode> nodes = entry.getValue();
            if (nodes.size() < 2) {
                continue;
            }
            Aabb2 bounds = Aabb2.empty();
            for (RouteCardVisualNode node : nodes) {
                SPSGui.Rect icon = iconBounds(node.node(), node.position(), zoom);
                bounds = bounds.include(icon.x(), icon.y()).include(icon.right(), icon.bottom());
            }
            if (bounds.isEmpty()) {
                continue;
            }
            bounds = bounds.inflate(8.0D);
            double radiusX = Math.max(12.0D, (bounds.maxX() - bounds.minX()) * 0.5D);
            double radiusY = Math.max(12.0D, (bounds.maxY() - bounds.minY()) * 0.5D);
            drawDashedEllipse(graphics, new Vec2(bounds.centerX(), bounds.centerY()), radiusX, radiusY, 1.15D, 0xAA1B2633, 7.0D, 5.0D);
            String label = nodes.stream()
                    .map(RouteCardVisualNode::node)
                    .filter(node -> node.layoutOccurrence() >= 0)
                    .min(Comparator.comparingInt(RouteCardNode::layoutOccurrence))
                    .map(RouteCardNode::label)
                    .orElse("");
            int cut = label.indexOf(' ');
            if (cut > 0) {
                label = label.substring(0, cut);
            }
            if (!label.isBlank() && zoom >= 0.65D) {
                SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, label, 76), (int) Math.round(bounds.minX() + 4), (int) Math.round(bounds.minY() + 3), FullRouteMapConfig.MAP_CARD_LABEL, 0.58F);
            }
        }
    }

    private static SPSGui.Rect chooseDimensionChipBounds(
            RouteCardVisualSegment segment,
            RouteCardVisualNode anchor,
            List<RouteCardVisualEdge> edges,
            SPSGui.Rect map,
            int width,
            int height,
            double zoom,
            List<SPSGui.Rect> blockers,
            List<SPSGui.Rect> placed
    ) {
        Aabb2 bounds = segment.bounds();
        List<SPSGui.Rect> candidates = dimensionChipCandidates(anchor, bounds, map, width, height, zoom);
        SPSGui.Rect best = candidates.getFirst();
        double bestPenalty = Double.POSITIVE_INFINITY;
        for (SPSGui.Rect candidate : candidates) {
            double penalty = dimensionChipPenalty(candidate, anchor, edges, blockers, placed);
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                best = candidate;
                if (penalty <= 0.001D) {
                    break;
                }
            }
        }
        return best;
    }

    private static List<SPSGui.Rect> dimensionChipCandidates(RouteCardVisualNode anchor, Aabb2 bounds, SPSGui.Rect map, int width, int height, double zoom) {
        List<SPSGui.Rect> candidates = new ArrayList<>();
        if (anchor != null) {
            Vec2 p = anchor.position();
            double radius = nodeCollisionRadius(anchor.node(), zoom);
            double near = radius + 6.0D;
            double diagonal = Math.max(near * 0.72D, 8.0D);
            addChipCandidate(candidates, p.x() + diagonal, p.y() - diagonal - height, width, height, map);
            addChipCandidate(candidates, p.x() + near, p.y() - height * 0.5D, width, height, map);
            addChipCandidate(candidates, p.x() + diagonal, p.y() + diagonal, width, height, map);
            addChipCandidate(candidates, p.x() - diagonal - width, p.y() - diagonal - height, width, height, map);
            addChipCandidate(candidates, p.x() - near - width, p.y() - height * 0.5D, width, height, map);
            addChipCandidate(candidates, p.x() - diagonal - width, p.y() + diagonal, width, height, map);
            addChipCandidate(candidates, p.x() - width * 0.5D, p.y() - near - height, width, height, map);
            addChipCandidate(candidates, p.x() - width * 0.5D, p.y() + near, width, height, map);
            return candidates;
        }
        addChipCandidate(candidates, bounds.centerX() - width * 0.5D, bounds.minY() - height - 5.0D, width, height, map);
        addChipCandidate(candidates, bounds.centerX() - width * 0.5D, bounds.maxY() + 5.0D, width, height, map);
        addChipCandidate(candidates, bounds.minX(), bounds.minY() - height - 5.0D, width, height, map);
        addChipCandidate(candidates, bounds.maxX() - width, bounds.minY() - height - 5.0D, width, height, map);
        return candidates;
    }

    private static double dimensionChipPenalty(SPSGui.Rect candidate, RouteCardVisualNode anchor, List<RouteCardVisualEdge> edges, List<SPSGui.Rect> blockers, List<SPSGui.Rect> placed) {
        Vec2 center = new Vec2(candidate.x() + candidate.width() * 0.5D, candidate.y() + candidate.height() * 0.5D);
        double penalty = anchor == null ? 0.0D : center.distanceTo(anchor.position()) * 0.05D;
        for (SPSGui.Rect blocker : blockers) {
            if (rectsOverlap(candidate, blocker)) {
                penalty += 900.0D + overlapArea(candidate, blocker) * 0.2D;
            }
        }
        for (SPSGui.Rect previous : placed) {
            if (rectsOverlap(candidate, previous)) {
                penalty += 650.0D + overlapArea(candidate, previous) * 0.2D;
            }
        }
        for (RouteCardVisualEdge edge : edges) {
            if (isAbstractFoldBoundaryLink(edge)) {
                continue;
            }
            double distance = distanceRectToPolyline(candidate, edge.points());
            if (distance < 4.5D) {
                penalty += 160.0D + (4.5D - distance) * 40.0D;
            }
        }
        return penalty;
    }

    private static int overlapArea(SPSGui.Rect first, SPSGui.Rect second) {
        int x1 = Math.max(first.x(), second.x());
        int y1 = Math.max(first.y(), second.y());
        int x2 = Math.min(first.right(), second.right());
        int y2 = Math.min(first.bottom(), second.bottom());
        return Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
    }

    private static double distanceRectToPolyline(SPSGui.Rect rect, List<Vec2> points) {
        if (points.size() < 2) {
            return Double.POSITIVE_INFINITY;
        }
        List<Vec2> samples = List.of(
                new Vec2(rect.x() + rect.width() * 0.5D, rect.y() + rect.height() * 0.5D),
                new Vec2(rect.x(), rect.y()),
                new Vec2(rect.right(), rect.y()),
                new Vec2(rect.x(), rect.bottom()),
                new Vec2(rect.right(), rect.bottom())
        );
        double best = Double.POSITIVE_INFINITY;
        for (Vec2 sample : samples) {
            best = Math.min(best, distanceToPolyline(sample, points));
        }
        return best;
    }

    private static boolean isAbstractFoldBoundaryLink(RouteCardVisualEdge edge) {
        return edge.edge().kind() == SemanticEdgeKind.FOLD_ADJACENT && edge.edge().backingPathSlice().isEmpty();
    }

    private static void addChipCandidate(List<SPSGui.Rect> candidates, double x, double y, int width, int height, SPSGui.Rect map) {
        int padding = 3;
        int clampedX = Math.max(map.x() + padding, Math.min((int) Math.round(x), map.right() - padding - width));
        int clampedY = Math.max(map.y() + padding, Math.min((int) Math.round(y), map.bottom() - padding - height));
        SPSGui.Rect candidate = new SPSGui.Rect(clampedX, clampedY, width, height);
        if (candidates.stream().noneMatch(existing -> existing.x() == candidate.x() && existing.y() == candidate.y())) {
            candidates.add(candidate);
        }
    }

    private void drawNodes(GuiGraphicsExtractor graphics, Font font, RouteCardSemanticGraph semanticGraph, RouteCardVisualGraph visualGraph, RouteLineCardHit hover, double zoom) {
        for (RouteCardVisualNode visualNode : visualGraph.nodes()) {
            RouteCardNode node = visualNode.node();
            Vec2 position = visualNode.position();
            boolean hovered = nodeHoveredByRouteCardHit(node, hover) || foldPeerHighlighted(semanticGraph, hover, node);
            int radius = (int) Math.round(nodeRadius(node, zoom));
            if (hovered) {
                this.drawNodeFocus(graphics, node, position, radius);
            }
            switch (node.kind()) {
                case STATION -> {
                    drawCircle(graphics, position, radius, FullRouteMapConfig.MAP_NODE_FILL, FullRouteMapConfig.MAP_CARD_NODE_OUTLINE);
                }
                case PORTAL_BOUNDARY -> drawPortalBoundary(graphics, position, radius, foldColor(semanticGraph, node));
                case FOLD_BOUNDARY -> drawDiamond(graphics, position, radius, FullRouteMapConfig.MAP_FOLD_FILL, foldColor(semanticGraph, node));
                case MISSING_PATH_BOUNDARY -> {
                }
            }
        }
    }

    private List<SPSGui.Rect> drawLabels(GuiGraphicsExtractor graphics, Font font, RouteCardVisualGraph visualGraph, double zoom, SPSGui.Rect map) {
        if (zoom < 0.45D) {
            return List.of();
        }
        List<LabelBlocker> blockers = new ArrayList<>();
        for (RouteCardVisualNode visualNode : visualGraph.nodes()) {
            blockers.add(new LabelBlocker(visualNode.node().id(), iconBounds(visualNode.node(), visualNode.position(), zoom)));
        }
        List<SPSGui.Rect> placed = new ArrayList<>();
        int rendered = 0;
        List<RouteCardVisualNode> candidates = visualGraph.nodes().stream()
                .filter(node -> shouldConsiderLabel(node.node(), zoom))
                .sorted(Comparator.comparingInt(RouteCardVisualNode::priority).reversed())
                .toList();
        for (RouteCardVisualNode visualNode : candidates) {
            if (rendered >= FullRouteMapConfig.MAX_LABELS_PER_FRAME) {
                break;
            }
            RouteCardNode node = visualNode.node();
            DisplayNameStack name = labelName(node, zoom);
            float scale = labelScale(node, zoom);
            float secondaryScale = Math.max(0.48F, scale * 0.78F);
            int width = Math.max(1, FullMapUi.nameStackWidth(font, name, scale, secondaryScale));
            int height = Math.max(7, FullMapUi.nameStackHeight(name, scale, secondaryScale, 0));
            Optional<SPSGui.Rect> selected = chooseLabelBounds(node, visualNode.position(), zoom, map, width, height, placed, blockers);
            if (selected.isEmpty()) {
                continue;
            }
            SPSGui.Rect rect = selected.get();
            placed.add(rect);
            FullMapUi.drawNameStack(graphics, font, name, rect.x(), rect.y(), rect.width(), FullRouteMapConfig.MAP_CARD_LABEL, FullMapTheme.TEXT_MUTED, scale, secondaryScale, 0);
            rendered++;
        }
        return placed;
    }

    private static DisplayNameStack labelName(RouteCardNode node, double zoom) {
        DisplayNameStack name = FullMapText.displayNameStack(node);
        if (zoom < 1.15D && node.kind() == RouteCardNodeKind.STATION) {
            return name.withoutSecondary();
        }
        return name;
    }

    private Optional<RouteLineCardHit> hitTest(RouteCardVisualGraph visualGraph, SPSGui.Rect map, double mouseX, double mouseY, double zoom) {
        if (!map.contains(mouseX, mouseY)) {
            return Optional.empty();
        }
        for (RouteCardVisualNode visualNode : visualGraph.nodes().stream().sorted(Comparator.comparingInt(RouteCardVisualNode::priority).reversed()).toList()) {
            RouteCardNode node = visualNode.node();
            if (node.kind() == RouteCardNodeKind.MISSING_PATH_BOUNDARY) {
                continue;
            }
            double radius = nodeRadius(node, zoom);
            if (node.kind() == RouteCardNodeKind.FOLD_BOUNDARY && Math.abs(mouseX - visualNode.position().x()) + Math.abs(mouseY - visualNode.position().y()) <= radius + 2.0D) {
                return Optional.of(RouteLineCardHit.node(node));
            }
            if (node.kind() != RouteCardNodeKind.FOLD_BOUNDARY && Math.hypot(mouseX - visualNode.position().x(), mouseY - visualNode.position().y()) <= radius + 5.0D) {
                return Optional.of(RouteLineCardHit.node(node));
            }
        }
        RouteCardVisualEdge best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (RouteCardVisualEdge edge : visualGraph.edges()) {
            if (edge.points().size() < 2) {
                continue;
            }
            if (isAbstractFoldBoundaryLink(visualGraph, edge) || isAbstractMissingPathBoundaryLink(visualGraph, edge)) {
                continue;
            }
            double distance = distanceToPolyline(new Vec2(mouseX, mouseY), edge.points());
            if (distance < bestDistance && distance <= FullRouteMapConfig.LINE_WIDTH_PX + 5.0D) {
                bestDistance = distance;
                best = edge;
            }
        }
        return best == null ? Optional.empty() : Optional.of(RouteLineCardHit.edge(best.edge().id()));
    }

    public void renderHoverTooltip(
            GuiGraphicsExtractor graphics,
            Font font,
            RouteCardSemanticGraph semanticGraph,
            RouteCardVisualGraph visualGraph,
            RouteLineCardHit hover,
            SPSGui.Rect boundary,
            List<SPSGui.Rect> avoidRects,
            int mouseX,
            int mouseY
    ) {
        if (hover.kind() == RouteLineCardHitKind.NONE) {
            return;
        }
        if (hover.node().isPresent()) {
            RouteCardNode node = semanticGraph.nodes().stream().filter(value -> value.id().equals(hover.node().get())).findFirst().orElse(null);
            if (node == null) {
                return;
            }
            switch (node.kind()) {
                case STATION -> FullMapTooltipCard.render(
                        graphics,
                        font,
                        boundary,
                        avoidRects,
                        mouseX,
                        mouseY,
                        FullMapText.displayNameStack(node),
                        Component.translatable("screen.superpipeslide.full_map.route_card.tooltip.station", FullMapText.primaryName(node), node.layoutOccurrence() + 1, dimensionLabel(node.levelKey())).getString(),
                        List.of(),
                        List.of(),
                        FullRouteMapConfig.MAP_CARD_NODE_OUTLINE
                );
                case PORTAL_BOUNDARY -> FullMapTooltipCard.renderComponent(graphics, font, boundary, avoidRects, mouseX, mouseY, Component.translatable("screen.superpipeslide.full_map.route_card.tooltip.portal_boundary", dimensionLabel(node.label())));
                case FOLD_BOUNDARY -> FullMapTooltipCard.renderComponent(graphics, font, boundary, avoidRects, mouseX, mouseY, Component.translatable("screen.superpipeslide.full_map.route_card.tooltip.fold_boundary"));
                case MISSING_PATH_BOUNDARY -> FullMapTooltipCard.renderComponent(graphics, font, boundary, avoidRects, mouseX, mouseY, Component.translatable("screen.superpipeslide.full_map.route_card.tooltip.missing_path_boundary"));
            }
            return;
        }
        if (hover.edge().isPresent()) {
            RouteCardEdge edge = semanticGraph.edges().stream().filter(value -> value.id().equals(hover.edge().get())).findFirst().orElse(null);
            if (edge == null) {
                return;
            }
            RouteCardNode from = semanticGraph.nodes().stream().filter(value -> value.id().equals(edge.from())).findFirst().orElse(null);
            RouteCardNode to = semanticGraph.nodes().stream().filter(value -> value.id().equals(edge.to())).findFirst().orElse(null);
            String fromLabel = from == null ? "?" : routeCardNodeTooltipName(from);
            String toLabel = to == null ? "?" : routeCardNodeTooltipName(to);
            if (isMissingPathBoundaryEdge(semanticGraph, edge)) {
                FullMapTooltipCard.renderComponent(graphics, font, boundary, avoidRects, mouseX, mouseY, Component.translatable("screen.superpipeslide.full_map.route_card.tooltip.missing_path_edge", fromLabel, toLabel));
            } else {
                FullMapTooltipCard.renderComponent(graphics, font, boundary, avoidRects, mouseX, mouseY, Component.translatable("screen.superpipeslide.full_map.route_card.tooltip.edge", fromLabel, toLabel, statusLabel(edge.status())));
            }
            return;
        }
    }

    private static String routeCardNodeTooltipName(RouteCardNode node) {
        return switch (node.kind()) {
            case STATION -> FullMapText.primaryName(node);
            case PORTAL_BOUNDARY -> Component.translatable("screen.superpipeslide.full_map.route_card.node.portal_boundary", dimensionLabel(node.label())).getString();
            case FOLD_BOUNDARY -> Component.translatable("screen.superpipeslide.full_map.route_card.node.fold_boundary").getString();
            case MISSING_PATH_BOUNDARY -> Component.translatable("screen.superpipeslide.full_map.route_card.node.missing_path_boundary").getString();
        };
    }

    private static String statusLabel(RouteSectionStatus status) {
        return Component.translatable("screen.superpipeslide.route_section_status." + status.getSerializedName()).getString();
    }

    private void drawFitViewportButton(GuiGraphicsExtractor graphics, SPSGui.Rect rect, boolean hovered) {
        FullMapUi.iconButton(graphics, rect, hovered, false, false, SPSGui.Icon.FIT);
    }

    private static boolean foldPeerHighlighted(RouteCardSemanticGraph graph, RouteLineCardHit hover, RouteCardNode node) {
        if (hover.node().isEmpty() || !isFoldLinkBoundary(node)) {
            return false;
        }
        RouteCardNodeId hoveredNodeId = hover.node().get();
        if (hoveredNodeId.equals(node.id())) {
            return false;
        }
        return graph.edges().stream().anyMatch(edge -> isAbstractFoldBoundaryLink(graph, edge)
                && ((edge.from().equals(hoveredNodeId) && edge.to().equals(node.id()))
                || (edge.to().equals(hoveredNodeId) && edge.from().equals(node.id()))));
    }

    private static Set<UUID> highlightedStationGroups(RouteCardSemanticGraph graph, RouteLineCardHit hover) {
        Set<UUID> result = new HashSet<>();
        hover.stationGroupId().ifPresent(result::add);
        hover.node()
                .flatMap(nodeId -> graph.nodes().stream()
                        .filter(node -> node.id().equals(nodeId))
                        .findFirst())
                .flatMap(RouteCardNode::stationGroupId)
                .ifPresent(result::add);
        return result;
    }

    private static boolean nodeHoveredByRouteCardHit(RouteCardNode node, RouteLineCardHit hover) {
        if (hover.node().filter(node.id()::equals).isPresent()) {
            return true;
        }
        return node.kind() == RouteCardNodeKind.STATION
                && node.stationGroupId().isPresent()
                && node.stationGroupId().equals(hover.stationGroupId());
    }

    private static boolean edgeHoveredByRouteCardHit(RouteCardVisualGraph graph, RouteCardEdge edge, RouteLineCardHit hover) {
        if (hover.edge().filter(edge.id()::equals).isPresent()) {
            return true;
        }
        if (hover.node().filter(nodeId -> edge.from().equals(nodeId) || edge.to().equals(nodeId)).isPresent()) {
            return true;
        }
        if (hover.stationGroupId().isEmpty()) {
            return false;
        }
        UUID stationGroupId = hover.stationGroupId().get();
        return graph.nodes().stream()
                .map(RouteCardVisualNode::node)
                .filter(node -> edge.from().equals(node.id()) || edge.to().equals(node.id()))
                .anyMatch(node -> node.stationGroupId().filter(stationGroupId::equals).isPresent());
    }

    private static Set<RouteCardNodeId> highlightedMissingPathBoundaries(RouteCardVisualGraph graph, RouteLineCardHit hover) {
        Set<RouteCardNodeId> result = new HashSet<>();
        hover.node().flatMap(nodeId -> graph.nodes().stream()
                        .map(RouteCardVisualNode::node)
                        .filter(node -> node.id().equals(nodeId) && node.kind() == RouteCardNodeKind.MISSING_PATH_BOUNDARY)
                        .findFirst())
                .ifPresent(node -> result.add(node.id()));
        hover.edge()
                .flatMap(edgeId -> graph.edges().stream().map(RouteCardVisualEdge::edge).filter(edge -> edge.id().equals(edgeId)).findFirst())
                .ifPresent(edge -> {
                    if (isAbstractMissingPathBoundaryLink(graph, edge)) {
                        result.add(edge.from());
                        result.add(edge.to());
                    } else if (isMissingPathBoundaryEdge(graph, edge)) {
                        missingBoundaryEndpoint(graph, edge).ifPresent(result::add);
                    }
                });
        if (result.isEmpty()) {
            return result;
        }
        boolean changed;
        do {
            changed = false;
            for (RouteCardEdge edge : graph.edges().stream().map(RouteCardVisualEdge::edge).toList()) {
                if (!isAbstractMissingPathBoundaryLink(graph, edge)) {
                    continue;
                }
                if (result.contains(edge.from()) && result.add(edge.to())) {
                    changed = true;
                }
                if (result.contains(edge.to()) && result.add(edge.from())) {
                    changed = true;
                }
            }
        } while (changed);
        return result;
    }

    private static int foldColor(RouteCardSemanticGraph graph, RouteCardNode node) {
        for (RouteCardEdge edge : graph.edges()) {
            if (edge.from().equals(node.id()) || edge.to().equals(node.id())) {
                return edge.themeColors().isEmpty() ? FullRouteMapConfig.MAP_FOLD_MULTI_LINE : edge.themeColors().getFirst();
            }
        }
        return FullRouteMapConfig.MAP_FOLD_MULTI_LINE;
    }

    private static boolean shouldConsiderLabel(RouteCardNode node, double zoom) {
        if (isFoldLinkBoundary(node)) {
            return false;
        }
        if (node.kind() == RouteCardNodeKind.MISSING_PATH_BOUNDARY) {
            return false;
        }
        if (zoom < 0.65D) {
            return false;
        }
        if (zoom < 1.0D) {
            return node.kind() != RouteCardNodeKind.STATION || node.routeLineIds().size() >= 2;
        }
        if (zoom < 1.35D) {
            return node.kind() != RouteCardNodeKind.STATION || node.routeLineIds().size() >= 2 || node.layoutOccurrence() % 2 == 0;
        }
        return true;
    }

    private static float labelScale(RouteCardNode node, double zoom) {
        double base = switch (node.kind()) {
            case STATION -> 0.68D;
            case PORTAL_BOUNDARY -> 0.64D;
            case FOLD_BOUNDARY -> 0.70D;
            case MISSING_PATH_BOUNDARY -> 0.62D;
        };
        double adjustment;
        if (zoom < 0.75D) {
            adjustment = -0.10D;
        } else if (zoom < 1.2D) {
            adjustment = -0.04D;
        } else if (zoom < 2.0D) {
            adjustment = 0.04D;
        } else if (zoom < 3.0D) {
            adjustment = 0.12D;
        } else {
            adjustment = 0.20D;
        }
        return (float) Math.max(0.56D, Math.min(1.0D, base + adjustment));
    }

    private static SPSGui.Rect iconBounds(RouteCardNode node, Vec2 screen, double zoom) {
        int radius = (int) Math.ceil(nodeCollisionRadius(node, zoom) + 3.0D);
        return new SPSGui.Rect((int) Math.floor(screen.x() - radius), (int) Math.floor(screen.y() - radius), radius * 2, radius * 2);
    }

    private static Optional<SPSGui.Rect> chooseLabelBounds(RouteCardNode node, Vec2 screen, double zoom, SPSGui.Rect map, int width, int height, List<SPSGui.Rect> placed, List<LabelBlocker> blockers) {
        SPSGui.Rect fallback = null;
        for (SPSGui.Rect candidate : labelCandidates(node, screen, zoom, map, width, height)) {
            if (fallback == null) {
                fallback = candidate;
            }
            if (placed.stream().anyMatch(existing -> rectsOverlap(existing, candidate))) {
                continue;
            }
            if (blockers.stream().anyMatch(blocker -> !blocker.nodeId().equals(node.id()) && rectsOverlap(blocker.bounds(), candidate))) {
                continue;
            }
            return Optional.of(candidate);
        }
        if (node.routeLineIds().size() >= 2 || node.kind() != RouteCardNodeKind.STATION) {
            return Optional.ofNullable(fallback);
        }
        return Optional.empty();
    }

    private static List<SPSGui.Rect> labelCandidates(RouteCardNode node, Vec2 screen, double zoom, SPSGui.Rect map, int width, int height) {
        double radius = nodeCollisionRadius(node, zoom);
        double gap = 5.0D + Math.max(0.0D, iconScale(zoom) * 2.0D);
        double near = radius + gap;
        double far = near + Math.max(7.0D, height * 0.8D);
        double diagonal = near * 0.72D;
        List<SPSGui.Rect> candidates = new ArrayList<>();
        addLabelBounds(candidates, screen.x() - width * 0.5D, screen.y() + near, width, height, map);
        addLabelBounds(candidates, screen.x() - width * 0.5D, screen.y() - near - height, width, height, map);
        addLabelBounds(candidates, screen.x() + near, screen.y() - height * 0.5D, width, height, map);
        addLabelBounds(candidates, screen.x() - near - width, screen.y() - height * 0.5D, width, height, map);
        addLabelBounds(candidates, screen.x() + diagonal, screen.y() + diagonal, width, height, map);
        addLabelBounds(candidates, screen.x() - diagonal - width, screen.y() + diagonal, width, height, map);
        addLabelBounds(candidates, screen.x() + diagonal, screen.y() - diagonal - height, width, height, map);
        addLabelBounds(candidates, screen.x() - diagonal - width, screen.y() - diagonal - height, width, height, map);
        addLabelBounds(candidates, screen.x() - width * 0.5D, screen.y() + far, width, height, map);
        addLabelBounds(candidates, screen.x() - width * 0.5D, screen.y() - far - height, width, height, map);
        return candidates;
    }

    private static void addLabelBounds(List<SPSGui.Rect> candidates, double x, double y, int width, int height, SPSGui.Rect map) {
        int padding = 2;
        int clampedX = Math.max(map.x() + padding, Math.min((int) Math.round(x), map.right() - padding - width));
        int clampedY = Math.max(map.y() + padding, Math.min((int) Math.round(y), map.bottom() - padding - height));
        candidates.add(new SPSGui.Rect(clampedX, clampedY, width, height));
    }

    private static String dimensionLabel(ResourceKey<Level> dimension) {
        return switch (dimension.identifier().toString()) {
            case "minecraft:overworld" -> Component.translatable("screen.superpipeslide.full_map.dimension.overworld").getString();
            case "minecraft:the_nether" -> Component.translatable("screen.superpipeslide.full_map.dimension.nether").getString();
            case "minecraft:the_end" -> Component.translatable("screen.superpipeslide.full_map.dimension.end").getString();
            default -> titleCase(dimension.identifier().getPath());
        };
    }

    private static String dimensionLabel(String dimensionId) {
        return switch (dimensionId) {
            case "minecraft:overworld" -> Component.translatable("screen.superpipeslide.full_map.dimension.overworld").getString();
            case "minecraft:the_nether" -> Component.translatable("screen.superpipeslide.full_map.dimension.nether").getString();
            case "minecraft:the_end" -> Component.translatable("screen.superpipeslide.full_map.dimension.end").getString();
            default -> {
                int separator = dimensionId.indexOf(':');
                yield titleCase(separator >= 0 && separator + 1 < dimensionId.length() ? dimensionId.substring(separator + 1) : dimensionId);
            }
        };
    }

    private static int dimensionColor(ResourceKey<Level> dimension) {
        return switch (dimension.identifier().toString()) {
            case "minecraft:overworld" -> 0xFF4CAF50;
            case "minecraft:the_nether" -> 0xFFB71C1C;
            case "minecraft:the_end" -> 0xFF7E57C2;
            default -> stableDimensionColor(dimension.identifier().toString());
        };
    }

    private static int stableDimensionColor(String value) {
        int hash = 0x811C9DC5;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x01000193;
        }
        int[] palette = {
                0xFF1565C0, 0xFF00838F, 0xFF2E7D32, 0xFF6A1B9A,
                0xFFAD1457, 0xFF5D4037, 0xFF455A64, 0xFFEF6C00,
                0xFF00796B, 0xFF283593, 0xFF9E9D24, 0xFFC62828
        };
        return palette[Math.floorMod(hash, palette.length)];
    }

    private static int readableTextColor(int background) {
        int r = (background >> 16) & 0xFF;
        int g = (background >> 8) & 0xFF;
        int b = background & 0xFF;
        double luminance = (0.299D * r + 0.587D * g + 0.114D * b) / 255.0D;
        return luminance > 0.58D ? 0xFF111111 : 0xFFFFFFFF;
    }

    private static String titleCase(String path) {
        String[] parts = path.replace('-', '_').split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.isEmpty() ? path : builder.toString();
    }

    private static double nodeRadius(RouteCardNode node, double zoom) {
        double base = switch (node.kind()) {
            case STATION -> 6.0D;
            case PORTAL_BOUNDARY -> 5.2D;
            case FOLD_BOUNDARY -> 6.0D;
            case MISSING_PATH_BOUNDARY -> 3.5D;
        };
        double min = node.kind() == RouteCardNodeKind.STATION ? 2.0D : 2.5D;
        return Math.max(min, base * iconScale(zoom));
    }

    private static double nodeCollisionRadius(RouteCardNode node, double zoom) {
        double radius = nodeRadius(node, zoom);
        return switch (node.kind()) {
            case STATION -> radius;
            case PORTAL_BOUNDARY -> radius;
            case FOLD_BOUNDARY -> radius;
            case MISSING_PATH_BOUNDARY -> radius;
        };
    }

    private static double iconScale(double zoom) {
        return Math.max(0.35D, Math.min(1.0D, zoom));
    }

    private static void drawNodeFocus(GuiGraphicsExtractor graphics, RouteCardNode node, Vec2 center, int radius) {
        switch (node.kind()) {
            case PORTAL_BOUNDARY -> {
                SmoothGuiPrimitives.circle(graphics, center, radius + 5.0D, FullRouteMapConfig.MAP_FOCUS_HALO);
                SmoothGuiPrimitives.circle(graphics, center, radius + 2.0D, FullRouteMapConfig.MAP_FOCUS_RING);
            }
            case FOLD_BOUNDARY -> {
                SmoothGuiPrimitives.diamond(graphics, center, radius + 5.0D, FullRouteMapConfig.MAP_FOCUS_HALO);
                SmoothGuiPrimitives.diamond(graphics, center, radius + 2.0D, FullRouteMapConfig.MAP_FOCUS_RING);
            }
            case STATION -> {
                SmoothGuiPrimitives.circle(graphics, center, radius + 5.0D, FullRouteMapConfig.MAP_FOCUS_HALO);
                SmoothGuiPrimitives.circle(graphics, center, radius + 2.0D, FullRouteMapConfig.MAP_FOCUS_RING);
            }
            case MISSING_PATH_BOUNDARY -> {
            }
        }
    }

    private static void drawCircle(GuiGraphicsExtractor graphics, Vec2 center, int radius, int fill, int outline) {
        SmoothGuiPrimitives.circle(graphics, center, radius + 1.0D, outline);
        SmoothGuiPrimitives.circle(graphics, center, radius, fill);
    }

    private static void drawDiamond(GuiGraphicsExtractor graphics, Vec2 center, int radius, int fill, int outline) {
        SmoothGuiPrimitives.diamond(graphics, center, radius + 1.0D, outline);
        SmoothGuiPrimitives.diamond(graphics, center, Math.max(1.0D, radius - 1.0D), fill);
    }

    private static void drawPortalBoundary(GuiGraphicsExtractor graphics, Vec2 center, int radius, int color) {
        SmoothGuiPrimitives.circle(graphics, center, radius + 1.0D, FullRouteMapConfig.MAP_CARD_NODE_OUTLINE);
        SmoothGuiPrimitives.circle(graphics, center, Math.max(2.0D, radius - 0.6D), FullRouteMapConfig.MAP_NODE_FILL);
        double inner = Math.max(2.0D, radius - 2.0D);
        SmoothGuiPrimitives.circle(graphics, center, inner, SPSGui.withAlpha(color, 0x44));
        SmoothGuiPrimitives.line(graphics, new Vec2(center.x() - inner, center.y()), new Vec2(center.x() + inner, center.y()), 1.1D, color);
        SmoothGuiPrimitives.line(graphics, new Vec2(center.x(), center.y() - inner), new Vec2(center.x(), center.y() + inner), 1.1D, color);
    }

    private static void drawColorLanePath(GuiGraphicsExtractor graphics, List<Vec2> points, double totalWidth, List<Integer> colors) {
        if (points.size() < 2) {
            return;
        }
        if (colors.isEmpty()) {
            drawPolyline(graphics, points, totalWidth, FullRouteMapConfig.MAP_TRUNK);
            return;
        }
        if (colors.size() == 1) {
            drawPolyline(graphics, points, totalWidth, colors.getFirst());
            return;
        }
        double stripeWidth = totalWidth / colors.size();
        double center = (colors.size() - 1) * 0.5D;
        for (int i = 0; i < colors.size(); i++) {
            drawPolyline(graphics, offsetPath(points, (i - center) * stripeWidth), stripeWidth + 0.2D, colors.get(i));
        }
    }

    private static void drawDashedColorLanePath(GuiGraphicsExtractor graphics, List<Vec2> points, double totalWidth, List<Integer> colors, double dash, double gap) {
        if (points.size() < 2) {
            return;
        }
        if (colors.isEmpty()) {
            drawDashedPolyline(graphics, points, totalWidth, FullRouteMapConfig.MAP_TRUNK, dash, gap);
            return;
        }
        if (colors.size() == 1) {
            drawDashedPolyline(graphics, points, totalWidth, colors.getFirst(), dash, gap);
            return;
        }
        double stripeWidth = totalWidth / colors.size();
        double center = (colors.size() - 1) * 0.5D;
        for (int i = 0; i < colors.size(); i++) {
            drawDashedPolyline(graphics, offsetPath(points, (i - center) * stripeWidth), stripeWidth + 0.2D, colors.get(i), dash, gap);
        }
    }

    private static void drawFadingDashedRouteLine(GuiGraphicsExtractor graphics, List<Vec2> points, double totalWidth, List<Integer> colors) {
        if (points.size() < 2) {
            return;
        }
        List<Integer> routeColors = colors.isEmpty() ? List.of(FullRouteMapConfig.MAP_TRUNK) : colors;
        if (routeColors.size() == 1) {
            drawFadingDashedPolyline(graphics, points, totalWidth, routeColors.getFirst(), 7.0D, 5.0D);
            return;
        }
        double stripeWidth = totalWidth / routeColors.size();
        double center = (routeColors.size() - 1) * 0.5D;
        for (int i = 0; i < routeColors.size(); i++) {
            drawFadingDashedPolyline(graphics, offsetPath(points, (i - center) * stripeWidth), stripeWidth + 0.2D, routeColors.get(i), 7.0D, 5.0D);
        }
    }

    private static void drawFadingDashedPolyline(GuiGraphicsExtractor graphics, List<Vec2> points, double width, int color, double dash, double gap) {
        double total = 0.0D;
        for (int i = 0; i + 1 < points.size(); i++) {
            total += points.get(i).distanceTo(points.get(i + 1));
        }
        if (total <= 0.001D) {
            return;
        }
        double walked = 0.0D;
        for (int i = 0; i + 1 < points.size(); i++) {
            Vec2 a = points.get(i);
            Vec2 b = points.get(i + 1);
            double dx = b.x() - a.x();
            double dy = b.y() - a.y();
            double length = Math.hypot(dx, dy);
            if (length <= 0.001D) {
                continue;
            }
            double ux = dx / length;
            double uy = dy / length;
            for (double start = 0.0D; start < length; start += dash + gap) {
                double end = Math.min(length, start + dash);
                double progress = Math.max(0.0D, Math.min(1.0D, (walked + start) / total));
                int alpha = (int) Math.round(0xD8 * Math.pow(1.0D - progress, 1.35D));
                if (alpha > 8) {
                    drawPolyline(graphics, List.of(
                            new Vec2(a.x() + ux * start, a.y() + uy * start),
                            new Vec2(a.x() + ux * end, a.y() + uy * end)
                    ), width, SPSGui.withAlpha(color, alpha));
                }
            }
            walked += length;
        }
    }

    private static void drawDashedPolyline(GuiGraphicsExtractor graphics, List<Vec2> points, double width, int color, double dash, double gap) {
        for (int i = 0; i + 1 < points.size(); i++) {
            drawDashedLine(graphics, points.get(i), points.get(i + 1), width, color, dash, gap);
        }
    }

    private static void drawDashedLine(GuiGraphicsExtractor graphics, Vec2 a, Vec2 b, double width, int color, double dash, double gap) {
        double length = a.distanceTo(b);
        if (length <= 0.001D) {
            return;
        }
        double ux = (b.x() - a.x()) / length;
        double uy = (b.y() - a.y()) / length;
        for (double start = 0.0D; start < length; start += dash + gap) {
            double end = Math.min(length, start + dash);
            drawPolyline(graphics, List.of(
                    new Vec2(a.x() + ux * start, a.y() + uy * start),
                    new Vec2(a.x() + ux * end, a.y() + uy * end)
            ), width, color);
        }
    }

    private static void drawDashedEllipse(GuiGraphicsExtractor graphics, Vec2 center, double radiusX, double radiusY, double width, int color, double dash, double gap) {
        double circumference = Math.PI * (3.0D * (radiusX + radiusY) - Math.sqrt((3.0D * radiusX + radiusY) * (radiusX + 3.0D * radiusY)));
        int steps = Math.max(36, (int) Math.ceil(circumference / 4.0D));
        double phase = 0.0D;
        Vec2 previous = new Vec2(center.x() + radiusX, center.y());
        for (int i = 1; i <= steps; i++) {
            double angle = Math.PI * 2.0D * i / steps;
            Vec2 current = new Vec2(center.x() + Math.cos(angle) * radiusX, center.y() + Math.sin(angle) * radiusY);
            double length = previous.distanceTo(current);
            double midpoint = phase + length * 0.5D;
            if (Math.floorMod((int) Math.floor(midpoint), (int) Math.max(1.0D, dash + gap)) < dash) {
                drawPolyline(graphics, List.of(previous, current), width, color);
            }
            phase += length;
            previous = current;
        }
    }

    private static List<Vec2> offsetPath(List<Vec2> points, double offset) {
        if (points.size() < 2 || Math.abs(offset) < 0.001D) {
            return points;
        }
        List<Vec2> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            Vec2 normal;
            if (i == 0) {
                normal = segmentNormal(points.get(0), points.get(1));
            } else if (i == points.size() - 1) {
                normal = segmentNormal(points.get(i - 1), points.get(i));
            } else {
                Vec2 first = segmentNormal(points.get(i - 1), points.get(i));
                Vec2 second = segmentNormal(points.get(i), points.get(i + 1));
                double nx = first.x() + second.x();
                double ny = first.y() + second.y();
                double length = Math.hypot(nx, ny);
                normal = length < 0.001D ? second : new Vec2(nx / length, ny / length);
            }
            result.add(new Vec2(points.get(i).x() + normal.x() * offset, points.get(i).y() + normal.y() * offset));
        }
        return result;
    }

    private static Vec2 segmentNormal(Vec2 a, Vec2 b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double length = Math.hypot(dx, dy);
        if (length < 0.001D) {
            return new Vec2(0.0D, 1.0D);
        }
        return new Vec2(-dy / length, dx / length);
    }

    private static void drawPolyline(GuiGraphicsExtractor graphics, List<Vec2> points, double width, int color) {
        SmoothGuiPrimitives.polyline(graphics, points, width, color);
    }

    private static boolean isAbstractFoldBoundaryLink(RouteCardVisualGraph graph, RouteCardVisualEdge visualEdge) {
        return isAbstractFoldBoundaryLink(graph, visualEdge.edge());
    }

    private static boolean isAbstractFoldBoundaryLink(RouteCardVisualGraph graph, RouteCardEdge edge) {
        if (edge.kind() != SemanticEdgeKind.FOLD_ADJACENT || !edge.backingPathSlice().isEmpty()) {
            return false;
        }
        RouteCardNode from = graph.nodes().stream()
                .map(RouteCardVisualNode::node)
                .filter(node -> node.id().equals(edge.from()))
                .findFirst()
                .orElse(null);
        RouteCardNode to = graph.nodes().stream()
                .map(RouteCardVisualNode::node)
                .filter(node -> node.id().equals(edge.to()))
                .findFirst()
                .orElse(null);
        return from != null && to != null && isFoldLinkBoundary(from) && isFoldLinkBoundary(to);
    }

    private static boolean isAbstractFoldBoundaryLink(RouteCardSemanticGraph graph, RouteCardEdge edge) {
        if (edge.kind() != SemanticEdgeKind.FOLD_ADJACENT || !edge.backingPathSlice().isEmpty()) {
            return false;
        }
        RouteCardNode from = graph.nodes().stream()
                .filter(node -> node.id().equals(edge.from()))
                .findFirst()
                .orElse(null);
        RouteCardNode to = graph.nodes().stream()
                .filter(node -> node.id().equals(edge.to()))
                .findFirst()
                .orElse(null);
        return from != null && to != null && isFoldLinkBoundary(from) && isFoldLinkBoundary(to);
    }

    private static boolean isFoldLinkBoundary(RouteCardNode node) {
        return node.kind() == RouteCardNodeKind.FOLD_BOUNDARY || node.kind() == RouteCardNodeKind.PORTAL_BOUNDARY;
    }

    private static boolean isAbstractMissingPathBoundaryLink(RouteCardVisualGraph graph, RouteCardVisualEdge visualEdge) {
        return isAbstractMissingPathBoundaryLink(graph, visualEdge.edge());
    }

    private static boolean isAbstractMissingPathBoundaryLink(RouteCardVisualGraph graph, RouteCardEdge edge) {
        if (edge.kind() != SemanticEdgeKind.FOLD_ADJACENT || !edge.backingPathSlice().isEmpty()) {
            return false;
        }
        RouteCardNode from = graph.nodes().stream()
                .map(RouteCardVisualNode::node)
                .filter(node -> node.id().equals(edge.from()))
                .findFirst()
                .orElse(null);
        RouteCardNode to = graph.nodes().stream()
                .map(RouteCardVisualNode::node)
                .filter(node -> node.id().equals(edge.to()))
                .findFirst()
                .orElse(null);
        return from != null && to != null && from.kind() == RouteCardNodeKind.MISSING_PATH_BOUNDARY && to.kind() == RouteCardNodeKind.MISSING_PATH_BOUNDARY;
    }

    private static boolean isMissingPathBoundaryEdge(RouteCardVisualGraph graph, RouteCardEdge edge) {
        RouteCardNode from = graph.nodes().stream()
                .map(RouteCardVisualNode::node)
                .filter(node -> node.id().equals(edge.from()))
                .findFirst()
                .orElse(null);
        RouteCardNode to = graph.nodes().stream()
                .map(RouteCardVisualNode::node)
                .filter(node -> node.id().equals(edge.to()))
                .findFirst()
                .orElse(null);
        return isMissingPathBoundaryEdge(from, to);
    }

    private static boolean isMissingPathBoundaryEdge(RouteCardSemanticGraph graph, RouteCardEdge edge) {
        RouteCardNode from = graph.nodes().stream()
                .filter(node -> node.id().equals(edge.from()))
                .findFirst()
                .orElse(null);
        RouteCardNode to = graph.nodes().stream()
                .filter(node -> node.id().equals(edge.to()))
                .findFirst()
                .orElse(null);
        return isMissingPathBoundaryEdge(from, to);
    }

    private static boolean isMissingPathBoundaryEdge(RouteCardNode from, RouteCardNode to) {
        return from != null && to != null && from.kind() != to.kind()
                && (from.kind() == RouteCardNodeKind.MISSING_PATH_BOUNDARY || to.kind() == RouteCardNodeKind.MISSING_PATH_BOUNDARY);
    }

    private static Optional<RouteCardNodeId> missingBoundaryEndpoint(RouteCardVisualGraph graph, RouteCardEdge edge) {
        RouteCardNode from = graph.nodes().stream()
                .map(RouteCardVisualNode::node)
                .filter(node -> node.id().equals(edge.from()))
                .findFirst()
                .orElse(null);
        if (from != null && from.kind() == RouteCardNodeKind.MISSING_PATH_BOUNDARY) {
            return Optional.of(from.id());
        }
        RouteCardNode to = graph.nodes().stream()
                .map(RouteCardVisualNode::node)
                .filter(node -> node.id().equals(edge.to()))
                .findFirst()
                .orElse(null);
        if (to != null && to.kind() == RouteCardNodeKind.MISSING_PATH_BOUNDARY) {
            return Optional.of(to.id());
        }
        return Optional.empty();
    }

    private static double distanceToPolyline(Vec2 point, List<Vec2> points) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i + 1 < points.size(); i++) {
            best = Math.min(best, distanceToSegment(point, points.get(i), points.get(i + 1)));
        }
        return best;
    }

    private static Aabb2 boundsFor(List<Vec2> points) {
        Aabb2 bounds = Aabb2.empty();
        for (Vec2 point : points) {
            bounds = bounds.include(point.x(), point.y());
        }
        return bounds;
    }

    private static double distanceToSegment(Vec2 point, Vec2 a, Vec2 b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double len2 = dx * dx + dy * dy;
        if (len2 <= 0.0001D) {
            return point.distanceTo(a);
        }
        double t = ((point.x() - a.x()) * dx + (point.y() - a.y()) * dy) / len2;
        double clamped = Math.max(0.0D, Math.min(1.0D, t));
        return point.distanceTo(new Vec2(a.x() + dx * clamped, a.y() + dy * clamped));
    }

    private static SPSGui.Rect clipRect(SPSGui.Rect rect, SPSGui.Rect clip) {
        int x1 = Math.max(rect.x(), clip.x());
        int y1 = Math.max(rect.y(), clip.y());
        int x2 = Math.min(rect.right(), clip.right());
        int y2 = Math.min(rect.bottom(), clip.bottom());
        return new SPSGui.Rect(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
    }

    private static boolean rectsOverlap(SPSGui.Rect first, SPSGui.Rect second) {
        return first.x() < second.right() && first.right() > second.x() && first.y() < second.bottom() && first.bottom() > second.y();
    }

    private record LayoutStripRenderResult(List<LayoutChipHit> chips, double maxScroll) {
    }

    private record StopListRenderResult(RouteLineCardHit hover, double maxScroll) {
    }

    private record LabelBlocker(RouteCardNodeId nodeId, SPSGui.Rect bounds) {
    }
}
