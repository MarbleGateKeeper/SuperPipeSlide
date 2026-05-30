package dev.marblegate.superpipeslide.client.core.projection.engine;

import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponentSettings;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionOverflowMode;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionTextAlign;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PlatformLayoutProjectionEngine {
    private static final int FALLBACK_LINE_COLOR = 0xFF3366FF;
    private static final int LIGHT_TEXT = 0xFFFFFFFF;
    private static final int DARK_TEXT = 0xFF17242A;
    private static final int MAP_NODE_FILL = 0xFFF7F9F9;
    private static final int MAP_NODE_OUTLINE = 0xFF17242A;
    private static final int STATUS_WARNING = 0xFFFFC247;
    private static final int STATUS_DANGER = 0xFFFF5F57;
    private static final int STATUS_STALE = 0xFF8D99A6;
    private static final int LAYER_BACKGROUND = 0;
    private static final int LAYER_LINE_SHADOW = 8;
    private static final int LAYER_LINE = 10;
    private static final int LAYER_WARNING = 20;
    private static final int LAYER_WARNING_MARK = 24;
    private static final int LAYER_NODE_FILL = 30;
    private static final int LAYER_NODE_CORE = 34;
    private static final int LAYER_NODE_RING = 40;
    private static final int LAYER_NODE_TRANSFER_RING = 46;
    private static final int LAYER_CURRENT_HALO = 58;
    private static final int LAYER_CURRENT_LINE = 62;
    private static final int LAYER_CURRENT_CORE = 66;
    private static final int LAYER_TRANSFER = 72;
    private static final int LAYER_TEXT = 100;

    private PlatformLayoutProjectionEngine() {
    }

    public static Layout build(Data data, ProjectionComponentSettings.PlatformLayoutMap settings, long timeMillis) {
        Data safe = data == null ? Data.sample() : data;
        ProjectionComponentSettings.PlatformLayoutMap map = settings == null ? ProjectionComponentSettings.PlatformLayoutMap.schematicMapDefaults() : settings;
        if (map.followProjectionDirection() && safe.reverseOrder()) {
            safe = safe.reversed();
        }
        return switch (map.style()) {
            case STOP_LIST -> stopList(safe, map);
            case PHYSICAL -> physicalMap(safe, map);
            case PRACTICAL -> practicalMap(safe, map);
            case SCHEMATIC -> schematicMap(safe, map);
            case EDITOR -> editorMap(safe, map, timeMillis);
        };
    }

    private static Layout stopList(Data data, ProjectionComponentSettings.PlatformLayoutMap settings) {
        LayoutBuilder b = new LayoutBuilder();
        List<StopData> stops = data.stops();
        if (stops.isEmpty()) {
            return b.build();
        }
        int lineColor = routeColor(data.route(), settings.lineColor());
        float top = 0.060F;
        float bottom = 0.940F;
        float railX = 0.120F;
        float lineW = Math.max(0.006F, settings.lineWidth());
        addRouteSegments(b, pointsForStopList(stops, railX, top, bottom), stops, false, false, data.loopStatus(), lineW, data.route().colors(), lineColor);
        int count = stops.size();
        float rowBand = count <= 1 ? 0.24F : Math.max(0.040F, (bottom - top) / count);
        for (int i = 0; i < count; i++) {
            StopData stop = stops.get(i);
            float y = position(i, count, top, bottom);
            float node = nodeRadius(settings, stop, count);
            b.text(0.018F, y - rowBand * 0.34F, 0.066F, rowBand * 0.68F, Integer.toString(i + 1), withAlpha(settings.textColor(), 150), Math.min(settings.fontSize() * 0.68F, rowBand * 0.48F), ProjectionTextAlign.CENTER, ProjectionOverflowMode.SCALE);
            if (stop.missing()) {
                drawMissingNode(b, railX, y, node);
            } else if (stop.current()) {
                drawCurrentStopListMarker(b, stop, railX, y, node, rowBand, lineW, lineColor, settings);
            } else {
                drawNode(b, railX, y, node, stop.terminal(), stop.transfer(), settings.nodeStyle(), lineColor, settings.textColor(), settings.showTransferMarks());
            }
            if (settings.showTransferMarks()) {
                drawTransferPips(b, stop, 0.885F, y, rowBand * 0.35F, settings);
            }
            if (settings.showStopNames()) {
                float labelHeight = Math.min(rowBand * 0.92F, 0.105F);
                int labelColor = stop.missing() ? withAlpha(settings.textColor(), 120) : stop.current() ? lineColor : settings.textColor();
                b.text(0.185F, y - labelHeight * 0.5F, 0.620F, labelHeight, stop.name(), labelColor, Math.min(settings.fontSize(), labelHeight * 0.70F), ProjectionTextAlign.LEFT, settings.labelOverflow());
                if (!stop.missing() && !stop.translationName().isBlank() && rowBand > 0.072F) {
                    b.text(0.185F, y + labelHeight * 0.08F, 0.620F, labelHeight * 0.50F, stop.translationName(), stop.current() ? withAlpha(lineColor, 180) : withAlpha(settings.textColor(), 165), Math.min(settings.fontSize() * 0.54F, labelHeight * 0.38F), ProjectionTextAlign.LEFT, settings.labelOverflow());
                }
            }
        }
        return b.build();
    }

    private static void drawCurrentStopListMarker(LayoutBuilder b, StopData stop, float railX, float y, float node, float rowBand, float lineWidth, int lineColor, ProjectionComponentSettings.PlatformLayoutMap settings) {
        float markerLine = Math.max(0.004F, lineWidth * 0.760F);
        float labelW = 0.072F;
        float labelH = Math.min(rowBand * 0.560F, 0.044F);
        drawNode(b, railX, y, node, stop.terminal(), stop.transfer(), settings.nodeStyle(), lineColor, settings.textColor(), settings.showTransferMarks());
        b.line(railX + node * 1.640F, y, 0.176F, y, markerLine, withAlpha(lineColor, 235), LAYER_CURRENT_LINE);
        b.ring(railX, y, node * 1.560F, Math.max(0.004F, node * 0.240F), 0xFFFFFFFF, LAYER_CURRENT_HALO);
        b.ring(railX, y, node * 1.220F, Math.max(0.003F, node * 0.180F), lineColor, LAYER_CURRENT_HALO + 1);
        if (labelH > 0.018F) {
            float labelX = 0.805F;
            b.text(labelX, y - labelH * 0.5F, labelW, labelH, Component.translatable("screen.superpipeslide.platform_projector.current_stop").getString(), lineColor, Math.min(settings.fontSize() * 0.680F, labelH * 0.720F), ProjectionTextAlign.CENTER, ProjectionOverflowMode.SCALE);
        }
    }

    private static Layout physicalMap(Data data, ProjectionComponentSettings.PlatformLayoutMap settings) {
        List<Point> points = physicalPoints(data.stops(), 0.080F, 0.920F, 0.160F, 0.820F);
        return mapFromPoints(data, settings, points, MapTreatment.PHYSICAL);
    }

    private static Layout practicalMap(Data data, ProjectionComponentSettings.PlatformLayoutMap settings) {
        List<Point> physical = physicalPoints(data.stops(), 0.080F, 0.920F, 0.200F, 0.800F);
        List<Point> points = new ArrayList<>();
        int count = Math.max(1, data.stops().size());
        for (int i = 0; i < count; i++) {
            float x = position(i, count, 0.080F, 0.920F);
            float y = physical.isEmpty() ? 0.500F : physical.get(i).y();
            y = 0.500F + Math.round((y - 0.500F) / 0.105F) * 0.105F;
            points.add(new Point(x, clamp(y, 0.250F, 0.750F)));
        }
        return mapFromPoints(data, settings, simplify(points), MapTreatment.PRACTICAL);
    }

    private static Layout schematicMap(Data data, ProjectionComponentSettings.PlatformLayoutMap settings) {
        if (data.loop() && data.stops().size() >= 4) {
            return loopSchematic(data, settings);
        }
        List<Point> points = new ArrayList<>();
        int count = Math.max(1, data.stops().size());
        for (int i = 0; i < count; i++) {
            points.add(new Point(position(i, count, 0.075F, 0.925F), 0.500F));
        }
        return mapFromPoints(data, settings, points, MapTreatment.SCHEMATIC);
    }

    private static Layout editorMap(Data data, ProjectionComponentSettings.PlatformLayoutMap settings, long timeMillis) {
        LayoutBuilder b = new LayoutBuilder();
        List<StopData> stops = data.stops();
        if (stops.isEmpty()) {
            return b.build();
        }
        int lineColor = routeColor(data.route(), settings.lineColor());
        EditorWindow window = editorWindow(stops.size(), timeMillis);
        List<Point> points = new ArrayList<>();
        int visibleCount = Math.max(1, window.endIndex() - window.startIndex() + 1);
        float span = Math.max(1.0F, visibleCount - 1.0F);
        for (int i = window.startIndex(); i <= window.endIndex(); i++) {
            points.add(new Point(0.070F + (i - window.offset()) / span * 0.860F, 0.500F));
        }
        float width = Math.max(0.005F, settings.lineWidth()) * 1.08F;
        addVisibleRouteSegments(b, points, stops, window.startIndex(), width, data.route().colors(), lineColor);
        if (data.loop()) {
            addEditorLoopTerminals(b, stops, points, window.startIndex(), window.endIndex() + 1, width, data.route().colors(), lineColor, data.loopStatus());
        }
        for (int i = window.startIndex(); i <= window.endIndex(); i++) {
            drawStopNodeAndLabel(b, stops.get(i), points.get(i - window.startIndex()), i, stops.size(), settings, lineColor, settings.showStopNames(), MapTreatment.EDITOR);
        }
        return b.build();
    }

    private static Layout loopSchematic(Data data, ProjectionComponentSettings.PlatformLayoutMap settings) {
        LayoutBuilder b = new LayoutBuilder();
        List<StopData> stops = data.stops();
        int lineColor = routeColor(data.route(), settings.lineColor());
        float width = Math.max(0.005F, settings.lineWidth());
        List<Point> points = new ArrayList<>();
        int count = stops.size();
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0D * i / count - Math.PI * 0.5D;
            points.add(new Point(0.500F + (float) Math.cos(angle) * 0.350F, 0.500F + (float) Math.sin(angle) * 0.320F));
        }
        if (!points.isEmpty()) {
            List<Point> closed = new ArrayList<>(points);
            closed.add(points.getFirst());
            addRouteSegments(b, closed, stops, true, true, data.loopStatus(), width, data.route().colors(), lineColor);
        }
        for (int i = 0; i < stops.size(); i++) {
            drawStopNodeAndLabel(b, stops.get(i), points.get(i), i, stops.size(), settings, lineColor, true, MapTreatment.SCHEMATIC);
        }
        return b.build();
    }

    private static Layout mapFromPoints(Data data, ProjectionComponentSettings.PlatformLayoutMap settings, List<Point> points, MapTreatment treatment) {
        LayoutBuilder b = new LayoutBuilder();
        List<StopData> stops = data.stops();
        if (stops.isEmpty() || points.isEmpty()) {
            return b.build();
        }
        int lineColor = routeColor(data.route(), settings.lineColor());
        boolean editor = treatment == MapTreatment.EDITOR;
        float width = Math.max(0.005F, settings.lineWidth()) * (editor ? 1.08F : 1.0F);
        boolean builtInLoop = data.loop() && treatment != MapTreatment.EDITOR;
        addRouteSegments(b, points, stops, false, builtInLoop, data.loopStatus(), width, data.route().colors(), lineColor);
        for (int i = 0; i < Math.min(stops.size(), points.size()); i++) {
            drawStopNodeAndLabel(b, stops.get(i), points.get(i), i, stops.size(), settings, lineColor, settings.showStopNames(), treatment);
        }
        return b.build();
    }

    private static void drawStopNodeAndLabel(LayoutBuilder b, StopData stop, Point point, int index, int total, ProjectionComponentSettings.PlatformLayoutMap settings, int lineColor, boolean showLabel, MapTreatment treatment) {
        float radius = nodeRadius(settings, stop, total);
        boolean editor = treatment == MapTreatment.EDITOR;
        if (stop.missing()) {
            drawMissingNode(b, point.x(), point.y(), radius);
        } else {
            drawNode(b, point.x(), point.y(), radius, stop.terminal(), stop.transfer(), settings.nodeStyle(), lineColor, editor ? MAP_NODE_OUTLINE : settings.textColor(), settings.showTransferMarks());
        }
        if (stop.current()) {
            b.ring(point.x(), point.y(), radius * 1.80F, Math.max(0.004F, radius * 0.24F), withAlpha(lineColor, 210), LAYER_CURRENT_HALO);
            b.ring(point.x(), point.y(), radius * 2.18F, Math.max(0.003F, radius * 0.16F), editor ? 0xFFFFFFFF : withAlpha(settings.textColor(), 215), LAYER_CURRENT_HALO + 1);
            b.circle(point.x(), point.y(), radius * 0.44F, editor ? 0xFFFFFFFF : lineColor, LAYER_CURRENT_CORE);
        }
        if (settings.showTransferMarks()) {
            drawTransferPips(b, stop, point.x(), point.y() - radius * (editor ? 2.35F : 2.15F), radius * 0.75F, settings);
        }
        if (!showLabel) {
            return;
        }
        LabelPolicy labelPolicy = labelPolicy(total);
        boolean important = stop.current() || stop.missing() || stop.terminal() || stop.transfer() || total <= labelPolicy.fullLabelLimit() || index % Math.max(1, (int) Math.ceil(total / (double) labelPolicy.maxLabels())) == 0;
        if (!important) {
            return;
        }
        boolean above = editor && labelPolicy.stagger() ? index % 2 == 0 : point.y() >= 0.50F;
        float h = Math.max(0.026F, Math.min(editor ? 0.080F : 0.090F, settings.fontSize() * 1.65F));
        float w = stop.current() ? (editor ? 0.310F : 0.270F) : (editor ? 0.245F : 0.215F);
        float y = point.y() + (above ? -radius * (editor ? 3.35F : 2.85F) - h : radius * (editor ? 2.25F : 1.75F));
        if (y < 0.020F) {
            y = point.y() + radius * (editor ? 2.25F : 1.75F);
        }
        if (y + h > 0.980F) {
            y = point.y() - radius * (editor ? 3.35F : 2.85F) - h;
        }
        float x = clamp(point.x() - w * 0.5F, 0.010F, 0.990F - w);
        int color = stop.missing() ? withAlpha(settings.textColor(), 120) : stop.current() ? lineColor : settings.textColor();
        float rotation = labelPolicy.rotationDegrees();
        if (rotation != 0.0F) {
            float labelHeight = Math.max(h, settings.fontSize() * 2.10F);
            float labelWidth = Math.min(0.260F, Math.max(0.150F, w * 0.88F));
            float labelY = point.y() + (above ? -radius * 3.70F - labelHeight : radius * 2.60F);
            if (labelY < 0.018F) {
                labelY = point.y() + radius * 2.40F;
            }
            if (labelY + labelHeight > 0.982F) {
                labelY = point.y() - radius * 3.50F - labelHeight;
            }
            b.text(clamp(point.x() - labelWidth * 0.5F, 0.010F, 0.990F - labelWidth), labelY, labelWidth, labelHeight, stop.name(), color, Math.min(settings.fontSize(), h * 0.80F), ProjectionTextAlign.CENTER, settings.labelOverflow(), rotation);
            return;
        }
        b.text(x, y, w, h, stop.name(), color, Math.min(settings.fontSize(), h * 0.72F), ProjectionTextAlign.CENTER, settings.labelOverflow());
    }

    private static void drawNode(LayoutBuilder b, float x, float y, float radius, boolean terminal, boolean transfer, ProjectionComponentSettings.PlatformNodeStyle style, int lineColor, int textColor, boolean transferMarks) {
        if (style == ProjectionComponentSettings.PlatformNodeStyle.NONE) {
            return;
        }
        if (style == ProjectionComponentSettings.PlatformNodeStyle.OUTLINE) {
            float ring = Math.max(0.004F, radius * 0.32F);
            b.circle(x, y, Math.max(0.003F, radius - ring - radius * 0.035F), MAP_NODE_FILL, LAYER_NODE_CORE);
            b.ring(x, y, radius, ring, lineColor, LAYER_NODE_RING);
        } else if (terminal) {
            float core = radius * 0.60F;
            b.ring(x, y, radius * 1.08F, Math.max(0.004F, radius * 0.42F), lineColor, LAYER_NODE_FILL);
            b.circle(x, y, core, MAP_NODE_FILL, LAYER_NODE_CORE);
            b.ring(x, y, radius * 1.26F, Math.max(0.003F, radius * 0.13F), lineColor, LAYER_NODE_RING);
        } else {
            b.circle(x, y, radius * 0.96F, lineColor, LAYER_NODE_FILL);
            b.ring(x, y, radius * 1.10F, Math.max(0.0025F, radius * 0.10F), MAP_NODE_FILL, LAYER_NODE_RING);
        }
        if (transfer && transferMarks) {
            b.ring(x, y, radius * 1.38F, Math.max(0.003F, radius * 0.18F), textColor, LAYER_NODE_TRANSFER_RING);
        }
    }

    private static void drawTransferPips(LayoutBuilder b, StopData stop, float x, float y, float scale, ProjectionComponentSettings.PlatformLayoutMap settings) {
        if (stop.missing() || stop.transfers().isEmpty()) {
            return;
        }
        int count = Math.min(3, stop.transfers().size());
        float radius = Math.max(0.004F, Math.min(0.018F, scale * 0.32F));
        float step = radius * 1.85F;
        float start = -(count - 1) * step * 0.5F;
        for (int i = 0; i < count; i++) {
            TransferData transfer = stop.transfers().get(i);
            b.circle(x + start + i * step, y, radius, firstColor(transfer.colors(), settings.lineColor()), LAYER_TRANSFER);
        }
    }

    private static List<Point> pointsForStopList(List<StopData> stops, float x, float top, float bottom) {
        int count = Math.max(1, stops.size());
        List<Point> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new Point(x, position(i, count, top, bottom)));
        }
        return points;
    }

    private static void addRouteSegments(LayoutBuilder b, List<Point> points, List<StopData> stops, boolean closedPath, boolean loop, RouteSectionStatus loopStatus, float width, List<Integer> colors, int fallback) {
        List<Point> safe = points == null ? List.of() : points.stream().filter(point -> point != null).toList();
        if (safe.size() < 2) {
            return;
        }
        int openSegments = Math.min(Math.max(0, stops.size() - 1), safe.size() - 1);
        for (int i = 0; i < openSegments; i++) {
            RouteSectionStatus status = stops.get(i + 1).incomingStatus();
            boolean missing = stops.get(i).missing() || stops.get(i + 1).missing();
            addRouteLineByStatus(b, safe.get(i), safe.get(i + 1), width, colors, fallback, missing ? RouteSectionStatus.STALE : status);
        }
        if ((closedPath || loop) && safe.size() > stops.size() && stops.size() > 1) {
            boolean missing = stops.getLast().missing() || stops.getFirst().missing();
            addRouteLineByStatus(b, safe.get(stops.size() - 1), safe.get(stops.size()), width, colors, fallback, missing ? RouteSectionStatus.STALE : loopStatus);
        } else if (loop && safe.size() == stops.size() && stops.size() > 2) {
            boolean missing = stops.getLast().missing() || stops.getFirst().missing();
            addRouteLineByStatus(b, safe.getLast(), safe.getFirst(), width, colors, fallback, missing ? RouteSectionStatus.STALE : loopStatus);
        }
    }

    private static void addVisibleRouteSegments(LayoutBuilder b, List<Point> points, List<StopData> stops, int startIndex, float width, List<Integer> colors, int fallback) {
        if (points.size() < 2) {
            return;
        }
        for (int i = 0; i + 1 < points.size(); i++) {
            int from = startIndex + i;
            int to = from + 1;
            if (to >= stops.size()) {
                break;
            }
            if (points.get(i).x() < -0.030F || points.get(i + 1).x() > 1.030F) {
                continue;
            }
            boolean missing = stops.get(from).missing() || stops.get(to).missing();
            addRouteLineByStatus(b, points.get(i), points.get(i + 1), width, colors, fallback, missing ? RouteSectionStatus.STALE : stops.get(to).incomingStatus());
        }
    }

    private static void addEditorLoopTerminals(LayoutBuilder b, List<StopData> stops, List<Point> points, int startIndex, int endExclusive, float width, List<Integer> colors, int fallback, RouteSectionStatus status) {
        if (points.isEmpty() || stops.size() < 2) {
            return;
        }
        if (startIndex == 0) {
            Point first = points.getFirst();
            Point left = new Point(clamp(first.x() - width * 2.25F, 0.010F, first.x()), first.y());
            Point down = new Point(left.x(), clamp(first.y() + width * 3.20F, 0.060F, 0.940F));
            addRouteLineByStatus(b, first, left, width, colors, fallback, status, false);
            addRouteLineByStatus(b, left, down, width, colors, fallback, status, false);
            if (needsRouteBreakMarker(status)) {
                Point mark = midpointOnPolyline(first, left, down);
                addRouteBreakMarker(b, mark.x(), mark.y(), width);
            }
        }
        if (endExclusive == stops.size()) {
            Point last = points.getLast();
            Point right = new Point(clamp(last.x() + width * 2.25F, last.x(), 0.990F), last.y());
            Point down = new Point(right.x(), clamp(last.y() + width * 3.20F, 0.060F, 0.940F));
            addRouteLineByStatus(b, last, right, width, colors, fallback, status, false);
            addRouteLineByStatus(b, right, down, width, colors, fallback, status, false);
            if (needsRouteBreakMarker(status)) {
                Point mark = midpointOnPolyline(last, right, down);
                addRouteBreakMarker(b, mark.x(), mark.y(), width);
            }
        }
    }

    private static void addRouteLineByStatus(LayoutBuilder b, Point a, Point c, float width, List<Integer> colors, int fallback, RouteSectionStatus status) {
        addRouteLineByStatus(b, a, c, width, colors, fallback, status, true);
    }

    private static void addRouteLineByStatus(LayoutBuilder b, Point a, Point c, float width, List<Integer> colors, int fallback, RouteSectionStatus status, boolean breakMarker) {
        RouteSectionStatus safe = status == null ? RouteSectionStatus.VALID : status;
        if (safe == RouteSectionStatus.VALID) {
            addRouteLine(b, a.x(), a.y(), c.x(), c.y(), width, colors, fallback);
            return;
        }
        int color = statusColor(safe);
        if (safe == RouteSectionStatus.STALE) {
            addDashedRouteLine(b, a.x(), a.y(), c.x(), c.y(), width, color, 0.065F);
            return;
        }
        addRouteLine(b, a.x(), a.y(), c.x(), c.y(), Math.max(0.003F, width * 0.92F), List.of(color), color);
        if (breakMarker) {
            addRouteBreakMarker(b, (a.x() + c.x()) * 0.5F, (a.y() + c.y()) * 0.5F, width);
        }
    }

    private static boolean needsRouteBreakMarker(RouteSectionStatus status) {
        RouteSectionStatus safe = status == null ? RouteSectionStatus.VALID : status;
        return safe != RouteSectionStatus.VALID && safe != RouteSectionStatus.STALE;
    }

    private static void addRouteBreakMarker(LayoutBuilder b, float x, float y, float width) {
        float extent = width * 1.45F;
        float markerWidth = Math.max(0.003F, width * 0.46F);
        b.line(x - extent, y - extent, x + extent, y + extent, markerWidth, 0xFFFFFFFF, LAYER_WARNING_MARK);
        b.line(x + extent, y - extent, x - extent, y + extent, markerWidth, 0xFFFFFFFF, LAYER_WARNING_MARK);
    }

    private static Point midpointOnPolyline(Point start, Point corner, Point end) {
        float firstLength = distance(start, corner);
        float secondLength = distance(corner, end);
        float target = (firstLength + secondLength) * 0.5F;
        if (target <= firstLength || secondLength <= 0.0001F) {
            return lerp(start, corner, firstLength <= 0.0001F ? 0.0F : target / firstLength);
        }
        return lerp(corner, end, (target - firstLength) / secondLength);
    }

    private static Point lerp(Point a, Point b, float t) {
        float safe = clamp(t, 0.0F, 1.0F);
        return new Point(a.x() + (b.x() - a.x()) * safe, a.y() + (b.y() - a.y()) * safe);
    }

    private static float distance(Point a, Point b) {
        return (float) Math.hypot(b.x() - a.x(), b.y() - a.y());
    }

    private static void addDashedRouteLine(LayoutBuilder b, float x1, float y1, float x2, float y2, float width, int color, float dashLength) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length <= 0.0001F) {
            b.circle(x1, y1, width * 0.5F, color);
            return;
        }
        float ux = dx / length;
        float uy = dy / length;
        float dash = Math.max(0.018F, dashLength);
        float gap = dash * 0.55F;
        b.line(x1, y1, x2, y2, Math.max(0.002F, width * 0.38F), withAlpha(color, 88), LAYER_LINE_SHADOW);
        float cap = Math.min(length * 0.22F, Math.max(width * 1.9F, dash * 0.42F));
        b.line(x1, y1, x1 + ux * cap, y1 + uy * cap, Math.max(0.003F, width * 0.82F), color);
        b.line(x2 - ux * cap, y2 - uy * cap, x2, y2, Math.max(0.003F, width * 0.82F), color);
        float start = Math.min(cap + gap * 0.35F, length * 0.48F);
        float endLimit = Math.max(start, length - cap - gap * 0.35F);
        for (float t = start; t < endLimit; t += dash + gap) {
            float end = Math.min(length, t + dash);
            if (end > endLimit) {
                end = endLimit;
            }
            if (end <= t) {
                break;
            }
            b.line(x1 + ux * t, y1 + uy * t, x1 + ux * end, y1 + uy * end, Math.max(0.003F, width * 0.82F), color);
        }
    }

    private static void addRouteLine(LayoutBuilder b, float x1, float y1, float x2, float y2, float width, List<Integer> colors, int fallback) {
        List<Integer> normalized = colors == null || colors.isEmpty() ? List.of(fallback) : colors.stream().limit(4).toList();
        if (normalized.size() <= 1) {
            b.line(x1, y1, x2, y2, width, firstColor(normalized, fallback));
            return;
        }
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length <= 0.0001F) {
            b.circle(x1, y1, width * 0.5F, firstColor(normalized, fallback));
            return;
        }
        float nx = -dy / length;
        float ny = dx / length;
        float stripeWidth = Math.max(0.003F, width / normalized.size());
        for (int i = 0; i < normalized.size(); i++) {
            float offset = (i - (normalized.size() - 1) * 0.5F) * stripeWidth;
            b.line(x1 + nx * offset, y1 + ny * offset, x2 + nx * offset, y2 + ny * offset, stripeWidth * 1.04F, normalized.get(i), LAYER_LINE + i);
        }
    }

    private static void drawMissingNode(LayoutBuilder b, float x, float y, float radius) {
        float r = Math.max(0.008F, radius);
        b.ring(x, y, r * 1.06F, Math.max(0.003F, r * 0.22F), STATUS_STALE, LAYER_NODE_RING);
        b.line(x - r * 0.70F, y - r * 0.70F, x + r * 0.70F, y + r * 0.70F, Math.max(0.003F, r * 0.22F), STATUS_WARNING, LAYER_WARNING);
        b.line(x + r * 0.70F, y - r * 0.70F, x - r * 0.70F, y + r * 0.70F, Math.max(0.003F, r * 0.22F), STATUS_WARNING, LAYER_WARNING);
    }

    private static List<Point> physicalPoints(List<StopData> stops, float minX, float maxX, float minY, float maxY) {
        if (stops == null || stops.isEmpty()) {
            return List.of();
        }
        double x0 = stops.stream().mapToDouble(StopData::worldX).min().orElse(0.0D);
        double x1 = stops.stream().mapToDouble(StopData::worldX).max().orElse(1.0D);
        double z0 = stops.stream().mapToDouble(StopData::worldZ).min().orElse(0.0D);
        double z1 = stops.stream().mapToDouble(StopData::worldZ).max().orElse(1.0D);
        double dx = Math.max(1.0D, x1 - x0);
        double dz = Math.max(1.0D, z1 - z0);
        boolean collapsed = dx <= 1.01D && dz <= 1.01D;
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            if (collapsed) {
                points.add(new Point(position(i, stops.size(), minX, maxX), (minY + maxY) * 0.5F));
            } else {
                float x = minX + (float) ((stops.get(i).worldX() - x0) / dx) * (maxX - minX);
                float y = minY + (float) ((stops.get(i).worldZ() - z0) / dz) * (maxY - minY);
                points.add(new Point(x, y));
            }
        }
        return points;
    }

    private static List<Point> simplify(List<Point> points) {
        if (points.size() < 3) {
            return points;
        }
        List<Point> result = new ArrayList<>();
        result.add(points.getFirst());
        for (int i = 1; i + 1 < points.size(); i++) {
            Point previous = result.getLast();
            Point current = points.get(i);
            Point next = points.get(i + 1);
            float ax = current.x() - previous.x();
            float ay = current.y() - previous.y();
            float bx = next.x() - current.x();
            float by = next.y() - current.y();
            float cross = Math.abs(ax * by - ay * bx);
            if (cross > 0.0015F) {
                result.add(current);
            }
        }
        result.add(points.getLast());
        return List.copyOf(result);
    }

    private static float nodeRadius(ProjectionComponentSettings.PlatformLayoutMap settings, StopData stop, int total) {
        float base = settings.nodeSize() * (total > 28 ? 0.42F : total > 18 ? 0.55F : 0.70F);
        if (stop.current()) {
            base *= 1.28F;
        } else if (stop.terminal() || stop.transfer()) {
            base *= 1.08F;
        }
        return Math.max(0.008F, Math.min(0.060F, base));
    }

    private static LabelPolicy labelPolicy(int total) {
        if (total <= 10) {
            return new LabelPolicy(18, 0.0F, false, 18);
        }
        if (total <= 18) {
            return new LabelPolicy(18, -35.0F, true, 18);
        }
        if (total <= 32) {
            return new LabelPolicy(22, -58.0F, true, 22);
        }
        return new LabelPolicy(24, -74.0F, true, 18);
    }

    private static EditorWindow editorWindow(int total, long timeMillis) {
        int visible = Math.max(1, Math.min(total, 18));
        if (total <= visible) {
            return new EditorWindow(0, total - 1, 0.0F);
        }
        float travel = total - visible;
        float seconds = timeMillis / 1000.0F;
        float position = (seconds * 0.62F) % Math.max(0.001F, travel + 1.0F);
        int start = Math.max(0, Math.min(total - visible, (int) Math.floor(position)));
        float offset = position - start;
        int end = Math.min(total - 1, start + visible);
        if (end - start + 1 < visible && start > 0) {
            start = Math.max(0, end - visible + 1);
        }
        return new EditorWindow(start, end, offset);
    }

    private static float position(int index, int total, float start, float end) {
        return total <= 1 ? (start + end) * 0.5F : start + (end - start) * index / (float) (total - 1);
    }

    private static int routeColor(RouteData route, int fallback) {
        return route == null ? firstColor(List.of(), fallback) : firstColor(route.colors(), fallback);
    }

    private static int firstColor(List<Integer> colors, int fallback) {
        return colors == null || colors.isEmpty() ? (fallback == 0 ? FALLBACK_LINE_COLOR : fallback) : colors.getFirst();
    }

    private static int statusColor(RouteSectionStatus status) {
        return switch (status == null ? RouteSectionStatus.VALID : status) {
            case VALID -> FALLBACK_LINE_COLOR;
            case DISABLED -> STATUS_STALE;
            case STALE -> STATUS_STALE;
            case INCOMPLETE -> STATUS_WARNING;
            case BROKEN, AMBIGUOUS -> STATUS_DANGER;
        };
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String clean(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value.trim();
        return result.length() <= 96 ? result : result.substring(0, 96);
    }

    private enum MapTreatment {
        PHYSICAL,
        PRACTICAL,
        SCHEMATIC,
        EDITOR
    }

    private record LabelPolicy(int fullLabelLimit, float rotationDegrees, boolean stagger, int maxLabels) {
    }

    private record EditorWindow(int startIndex, int endIndex, float offset) {
    }

    public record Data(String layoutName, RouteData route, List<StopData> stops, int currentIndex, boolean bidirectional, boolean loop, RouteSectionStatus loopStatus, boolean reverseOrder) {
        public Data {
            layoutName = clean(layoutName, "Layout");
            route = route == null ? new RouteData("Line", List.of(FALLBACK_LINE_COLOR)) : route;
            stops = stops == null || stops.isEmpty() ? List.of(StopData.sample()) : List.copyOf(stops);
            currentIndex = Math.max(0, Math.min(currentIndex, stops.size() - 1));
            loopStatus = loopStatus == null ? RouteSectionStatus.VALID : loopStatus;
        }

        public Data reversed() {
            int count = this.stops.size();
            List<StopData> reversed = new ArrayList<>(count);
            for (int i = count - 1; i >= 0; i--) {
                RouteSectionStatus incoming = i + 1 < count ? this.stops.get(i + 1).incomingStatus() : this.loop ? this.loopStatus : RouteSectionStatus.VALID;
                reversed.add(this.stops.get(i).withIncomingStatus(incoming));
            }
            int index = count - 1 - this.currentIndex;
            return new Data(this.layoutName, this.route, reversed, index, this.bidirectional, this.loop, this.loopStatus, false);
        }

        public static Data sample() {
            List<StopData> stops = List.of(
                    new StopData("Quartz", "", "1", false, true, false, List.of(), RouteSectionStatus.VALID, false, 0.0D, 0.0D),
                    new StopData("Copper", "", "2", false, false, true, List.of(new TransferData("Gold", List.of(0xFFFFB021), "2", "", false)), RouteSectionStatus.VALID, false, 34.0D, 18.0D),
                    new StopData("Block Plaza", "", "1", true, false, false, List.of(), RouteSectionStatus.VALID, false, 78.0D, 20.0D),
                    new StopData("Market", "", "1", false, false, true, List.of(new TransferData("End Loop", List.of(0xFF7D4AE8), "3", "Market", true)), RouteSectionStatus.INCOMPLETE, false, 120.0D, -4.0D),
                    new StopData("End", "", "1", false, true, false, List.of(), RouteSectionStatus.VALID, false, 160.0D, 26.0D)
            );
            return new Data("Local", new RouteData("Redstone", List.of(0xFFE24B3B)), stops, 2, true, false, RouteSectionStatus.VALID, false);
        }
    }

    public record RouteData(String name, List<Integer> colors) {
        public RouteData {
            name = clean(name, "Line");
            colors = colors == null || colors.isEmpty() ? List.of(FALLBACK_LINE_COLOR) : List.copyOf(colors);
        }
    }

    public record StopData(String name, String translationName, String platformNumber, boolean current, boolean terminal, boolean transfer, List<TransferData> transfers, RouteSectionStatus incomingStatus, boolean missing, double worldX, double worldZ) {
        public StopData {
            name = clean(name, "?");
            translationName = clean(translationName, "");
            platformNumber = clean(platformNumber, "");
            transfers = transfers == null ? List.of() : List.copyOf(transfers);
            transfer = transfer || !transfers.isEmpty();
            incomingStatus = incomingStatus == null ? RouteSectionStatus.VALID : incomingStatus;
            worldX = Double.isFinite(worldX) ? worldX : 0.0D;
            worldZ = Double.isFinite(worldZ) ? worldZ : 0.0D;
        }

        public StopData(String name, String translationName, String platformNumber, boolean current, boolean terminal, boolean transfer, List<TransferData> transfers, double worldX, double worldZ) {
            this(name, translationName, platformNumber, current, terminal, transfer, transfers, RouteSectionStatus.VALID, false, worldX, worldZ);
        }

        StopData withIncomingStatus(RouteSectionStatus incomingStatus) {
            return new StopData(this.name, this.translationName, this.platformNumber, this.current, this.terminal, this.transfer, this.transfers, incomingStatus, this.missing, this.worldX, this.worldZ);
        }

        static StopData sample() {
            return new StopData("Block Plaza", "", "1", true, false, false, List.of(), RouteSectionStatus.VALID, false, 0.0D, 0.0D);
        }
    }

    public record TransferData(String routeName, List<Integer> colors, String platform, String outStationName, boolean outStation) {
        public TransferData {
            routeName = clean(routeName, "?");
            colors = colors == null || colors.isEmpty() ? List.of(FALLBACK_LINE_COLOR) : List.copyOf(colors);
            platform = clean(platform, "");
            outStationName = clean(outStationName, "");
        }
    }

    public record Layout(List<Primitive> primitives) {
    }

    public sealed interface Primitive permits Rect, Band, Capsule, Circle, Ring, Line, Text {
        int layer();
    }

    public record Rect(float x, float y, float width, float height, int color, int layer) implements Primitive {
    }

    public record Band(float x, float y, float width, float height, List<Integer> colors, boolean horizontal, int layer) implements Primitive {
        public Band {
            colors = colors == null || colors.isEmpty() ? List.of(FALLBACK_LINE_COLOR) : List.copyOf(colors);
        }
    }

    public record Capsule(float x, float y, float width, float height, int color, int layer) implements Primitive {
    }

    public record Circle(float x, float y, float radius, int color, int layer) implements Primitive {
    }

    public record Ring(float x, float y, float radius, float thickness, int color, int layer) implements Primitive {
    }

    public record Line(float x1, float y1, float x2, float y2, float width, int color, int layer) implements Primitive {
    }

    public record Text(float x, float y, float width, float height, String value, int color, float fontSize, ProjectionTextAlign align, ProjectionOverflowMode overflow, float rotationDegrees, int layer) implements Primitive {
    }

    public record Point(float x, float y) {
    }

    private static final class LayoutBuilder {
        private final List<Primitive> primitives = new ArrayList<>();

        void rect(float x, float y, float width, float height, int color) {
            this.rect(x, y, width, height, color, LAYER_BACKGROUND);
        }

        void rect(float x, float y, float width, float height, int color, int layer) {
            this.primitives.add(new Rect(x, y, width, height, color, layer));
        }

        void band(float x, float y, float width, float height, List<Integer> colors, boolean horizontal) {
            this.band(x, y, width, height, colors, horizontal, LAYER_LINE);
        }

        void band(float x, float y, float width, float height, List<Integer> colors, boolean horizontal, int layer) {
            this.primitives.add(new Band(x, y, width, height, colors, horizontal, layer));
        }

        void capsule(float x, float y, float width, float height, int color) {
            this.capsule(x, y, width, height, color, LAYER_WARNING);
        }

        void capsule(float x, float y, float width, float height, int color, int layer) {
            this.primitives.add(new Capsule(x, y, width, height, color, layer));
        }

        void circle(float x, float y, float radius, int color) {
            this.circle(x, y, radius, color, LAYER_NODE_FILL);
        }

        void circle(float x, float y, float radius, int color, int layer) {
            this.primitives.add(new Circle(x, y, radius, color, layer));
        }

        void ring(float x, float y, float radius, float thickness, int color) {
            this.ring(x, y, radius, thickness, color, LAYER_NODE_RING);
        }

        void ring(float x, float y, float radius, float thickness, int color, int layer) {
            this.primitives.add(new Ring(x, y, radius, thickness, color, layer));
        }

        void line(float x1, float y1, float x2, float y2, float width, int color) {
            this.line(x1, y1, x2, y2, width, color, LAYER_LINE);
        }

        void line(float x1, float y1, float x2, float y2, float width, int color, int layer) {
            this.primitives.add(new Line(x1, y1, x2, y2, width, color, layer));
        }

        void text(float x, float y, float width, float height, String value, int color, float fontSize, ProjectionTextAlign align, ProjectionOverflowMode overflow) {
            this.text(x, y, width, height, value, color, fontSize, align, overflow, 0.0F);
        }

        void text(float x, float y, float width, float height, String value, int color, float fontSize, ProjectionTextAlign align, ProjectionOverflowMode overflow, float rotationDegrees) {
            this.primitives.add(new Text(x, y, width, height, value, color, fontSize, align, overflow, rotationDegrees, LAYER_TEXT));
        }

        Layout build() {
            if (this.primitives.isEmpty()) {
                return new Layout(List.of());
            }
            ArrayList<Primitive> sorted = new ArrayList<>(this.primitives);
            sorted.sort(Comparator.comparingInt(Primitive::layer));
            return new Layout(List.copyOf(sorted));
        }
    }
}
