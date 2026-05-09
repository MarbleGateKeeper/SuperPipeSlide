package dev.marblegate.superpipeslide.client.gui.route;

import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.fullmap.model.search.SearchResult;
import dev.marblegate.superpipeslide.client.gui.base.RouteDataAwareScreen;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkView;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.core.route.service.RoutePathfinder;
import dev.marblegate.superpipeslide.network.route.ServerboundRouteEditPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class RouteStationOrderScreen extends RouteEditorScreenBase implements RouteDataAwareScreen {
    private static final int CURRENT_ROW_HEIGHT = 22;
    private static final int CURRENT_ROW_GAP = 24;
    private static final int TRANSFER_ROW_HEIGHT = 12;
    private static final int MAX_TRANSFER_ROWS = 3;

    private final UUID routeLayoutId;
    private final List<UUID> draftStops = new ArrayList<>();
    private EditBox searchBox;
    private double leftScroll;
    private double rightScroll;
    private boolean dirty;
    private final List<RowHit> leftRows = new ArrayList<>();
    private final List<RowHit> rightRows = new ArrayList<>();
    private final Map<PreviewKey, Optional<SectionPreview>> sectionPreviewCache = new HashMap<>();
    private final List<PreviewKey> pendingSectionPreviews = new ArrayList<>();
    private static final long PREVIEW_COMPUTE_BUDGET_NANOS = 1_000_000L;
    private int draftVersion;
    private List<AvailableStationCard> availableStationCardsCache = List.of();
    private long availableCardsRouteRevision = Long.MIN_VALUE;
    private long availableCardsPipeRevision = Long.MIN_VALUE;
    private int availableCardsDraftHash;
    private String availableCardsQuery = "";
    private DragState dragState;
    private SPSGui.Rect leftPane = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect rightPane = new SPSGui.Rect(0, 0, 0, 0);

    public RouteStationOrderScreen(UUID routeLayoutId) {
        super(Component.translatable("screen.superpipeslide.station_order"));
        this.routeLayoutId = routeLayoutId;
        ClientRouteDataCache.routeLayout(routeLayoutId).ifPresent(layout -> this.draftStops.addAll(layout.orderedPlatformStops()));
    }

    @Override
    protected SPSGui.Rect createPanelRect() {
        return RouteEditorGui.panelRect(this.width, this.height, 500, 282);
    }

    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();
        this.searchBox = this.borderlessBox(this.panel.right() - 150, this.panel.y() + 52, 118, "");
    }

    @Override
    public void refreshFromRouteSnapshot() {
        if (!this.dirty && this.dragState == null) {
            this.draftStops.clear();
            ClientRouteDataCache.routeLayout(this.routeLayoutId).ifPresent(layout -> this.draftStops.addAll(layout.orderedPlatformStops()));
            this.invalidateDraftPreviews();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.beginFrame();
        this.leftRows.clear();
        this.rightRows.clear();
        this.drawEditorFrame(graphics);
        Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(this.routeLayoutId);
        if (layout.isEmpty()) {
            this.drawTitle(graphics, Component.translatable("screen.superpipeslide.layout.missing"), true, List.of(), mouseX, mouseY);
            SPSGui.centeredText(graphics, this.font, Component.translatable("screen.superpipeslide.layout.missing.body"), this.panel.x() + this.panel.width() / 2, this.panel.y() + 92, RouteEditorGui.INK_MUTED);
            this.renderTooltips(graphics, mouseX, mouseY);
            return;
        }
        Optional<RouteLine> line = ClientRouteDataCache.routeLine(layout.get().routeLineId());
        processPendingSectionPreviews(layout.get(), PREVIEW_COMPUTE_BUDGET_NANOS);
        SPSGui.Rect save = RouteEditorGui.titleActionBounds(this.panel, 0);
        boolean hasInvalidStops = hasInvalidDraftStops();
        boolean saveActive = this.dirty && !hasInvalidStops;
        this.drawTitle(graphics, Component.translatable("screen.superpipeslide.station_order.title", line.map(RouteLine::displayName).orElse(Component.translatable("screen.superpipeslide.route").getString()), SPSGui.layoutName(layout.get())), true, List.of(new SPSGui.IconButton(SPSGui.Icon.SAVE, hasInvalidStops ? RouteEditorGui.DANGER : RouteEditorGui.BLUE, !saveActive)), mouseX, mouseY);
        if (saveActive) {
            this.addClick(save, this::save, Component.translatable("screen.superpipeslide.action.save_station_order"));
        } else if (hasInvalidStops && save.contains(mouseX, mouseY)) {
            this.showDocumentTooltip(Component.translatable("screen.superpipeslide.station_order.save_blocked_missing"), mouseX, mouseY);
        }

        SPSGui.Rect content = this.editorContent();
        Component stamp = hasInvalidStops
                ? Component.translatable("screen.superpipeslide.station_order.missing_platform")
                : this.dirty
                ? Component.translatable("screen.superpipeslide.editor.unsaved")
                : Component.translatable("screen.superpipeslide.station_order.stop_count", this.draftStops.size());
        int stampColor = hasInvalidStops ? RouteEditorGui.DANGER : this.dirty ? RouteEditorGui.SAVE : RouteEditorGui.INK_MUTED;
        this.drawDocumentHeader(graphics, SPSGui.Icon.STATION_ORDER, List.of(
                Component.literal(line.map(RouteLine::displayName).orElse(Component.translatable("screen.superpipeslide.route").getString())),
                Component.literal(SPSGui.layoutName(layout.get())),
                Component.translatable("screen.superpipeslide.station_order")
        ), stamp, stampColor, layout.get().id().hashCode());
        int contentTop = this.documentBodyY();
        this.leftPane = new SPSGui.Rect(content.x(), contentTop, Math.min(172, Math.max(142, content.width() / 3)), content.bottom() - contentTop);
        this.rightPane = new SPSGui.Rect(this.leftPane.right() + 8, contentTop, content.right() - this.leftPane.right() - 8, content.bottom() - contentTop);
        this.leftScroll = Math.max(0.0D, Math.min(maxLeftScroll(), this.leftScroll));
        this.rightScroll = Math.max(0.0D, Math.min(maxRightScroll(), this.rightScroll));
        RouteEditorGui.worksheetPane(graphics, this.leftPane, this.leftPane.contains(mouseX, mouseY), false);
        RouteEditorGui.paperSection(graphics, this.rightPane, this.rightPane.contains(mouseX, mouseY), false);
        RouteEditorGui.sectionTitle(graphics, this.font, Component.translatable("screen.superpipeslide.station_order.current"), this.leftPane.x() + 6, this.leftPane.y() + 5);
        if (hasInvalidStops) {
            SPSGui.Rect removeMissing = new SPSGui.Rect(this.leftPane.right() - 20, this.leftPane.y() + 3, 14, 14);
            RouteEditorGui.toolButton(graphics, removeMissing, SPSGui.Icon.REMOVE, false, removeMissing.contains(mouseX, mouseY), RouteEditorGui.DANGER);
            this.addClick(removeMissing, this::removeMissingStops, Component.translatable("screen.superpipeslide.station_order.remove_missing"));
        }
        RouteEditorGui.sectionTitle(graphics, this.font, Component.translatable("screen.superpipeslide.station_order.available"), this.rightPane.x() + 6, this.rightPane.y() + 5);
        this.searchBox.setX(this.rightPane.x() + 6);
        this.searchBox.setY(this.rightPane.y() + 20);
        this.searchBox.setWidth(this.rightPane.width() - 12);
        SPSGui.icon(graphics, new SPSGui.Rect(this.searchBox.getX(), this.searchBox.getY(), 12, 12), SPSGui.Icon.SEARCH, RouteEditorGui.INK_MUTED);
        this.searchBox.setX(this.rightPane.x() + 20);
        this.searchBox.setWidth(this.rightPane.width() - 26);
        this.drawSearchPlaceholder(graphics, this.searchBox);

        renderLeft(graphics, line.map(RouteLine::themeColors).orElse(List.of()), layout.get(), mouseX, mouseY);
        renderRight(graphics, mouseX, mouseY);
        RouteEditorGui.scrollEdges(graphics, this.leftPane, this.leftScroll > 0.5D, this.leftScroll < maxLeftScroll() - 0.5D, true);
        RouteEditorGui.scrollEdges(graphics, this.rightPane, this.rightScroll > 0.5D, this.rightScroll < maxRightScroll() - 0.5D, false);
        RouteEditorGui.thinScrollbar(graphics, this.leftPane, this.leftScroll, maxLeftScroll(), this.leftPane.contains(mouseX, mouseY));
        RouteEditorGui.thinScrollbar(graphics, this.rightPane, this.rightScroll, maxRightScroll(), this.rightPane.contains(mouseX, mouseY));
        renderDrag(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.renderTooltips(graphics, mouseX, mouseY);
    }

    private void renderLeft(GuiGraphicsExtractor graphics, List<Integer> colors, RouteLayout layout, int mouseX, int mouseY) {
        int top = this.leftPane.y() + 20;
        int bottom = this.leftPane.bottom() - 5;
        int y = top - (int) this.leftScroll;
        graphics.enableScissor(this.leftPane.x() + 1, top, this.leftPane.right() - 1, bottom);
        if (this.draftStops.isEmpty()) {
            SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.station_order.drag_here"), this.leftPane.x() + 8, y + 12, RouteEditorGui.INK_MUTED);
        }
        for (int i = 0; i < this.draftStops.size(); i++) {
            UUID stopId = this.draftStops.get(i);
            int transferHeight = leftTransferHeight(stopId);
            SPSGui.Rect row = new SPSGui.Rect(this.leftPane.x() + 5, y, this.leftPane.width() - 10, CURRENT_ROW_HEIGHT + transferHeight);
            if (i > 0) {
                SPSGui.Rect arrow = new SPSGui.Rect(row.x() + 8, y - CURRENT_ROW_GAP + 4, layout.bidirectional() ? 28 : 18, CURRENT_ROW_GAP - 8);
                if (arrow.bottom() >= top && arrow.y() <= bottom) {
                    renderSectionArrow(graphics, arrow, layout, this.draftStops.get(i - 1), stopId, mouseX, mouseY);
                }
            }
            if (row.bottom() >= top && row.y() <= bottom) {
                renderOrderRow(graphics, row, i, stopId, colors, mouseX, mouseY);
            }
            this.leftRows.add(new RowHit(row, stopId, i));
            y += row.height() + CURRENT_ROW_GAP;
        }
        if (layout.loop() && this.draftStops.size() > 1) {
            int loopY = y - CURRENT_ROW_GAP + 3;
            SPSGui.Rect arrow = new SPSGui.Rect(this.leftPane.x() + 12, loopY, 22, CURRENT_ROW_GAP - 6);
            if (arrow.bottom() >= top && arrow.y() <= bottom) {
                renderSectionArrow(graphics, arrow, layout, this.draftStops.getLast(), this.draftStops.getFirst(), mouseX, mouseY);
            }
            SPSGui.Rect loopCard = new SPSGui.Rect(this.leftPane.x() + 5, loopY + CURRENT_ROW_GAP - 2, this.leftPane.width() - 10, 16);
            if (loopCard.bottom() >= top && loopCard.y() <= bottom) {
                RouteEditorGui.paperSection(graphics, loopCard, loopCard.contains(mouseX, mouseY), false);
                SPSGui.centeredText(graphics, this.font, Component.translatable("screen.superpipeslide.station_order.origin_station"), loopCard.x() + loopCard.width() / 2, loopCard.y() + 4, RouteEditorGui.INK_SECONDARY);
            }
        }
        int insert = insertionIndex(mouseY);
        if (this.dragState != null && insert >= 0 && this.leftPane.contains(mouseX, mouseY)) {
            int lineY = top + draftOffsetForIndex(insert) - (int) this.leftScroll;
            graphics.fill(this.leftPane.x() + 5, lineY, this.leftPane.right() - 5, lineY + 2, RouteEditorGui.BLUE);
        }
        graphics.disableScissor();
    }

    private void renderOrderRow(GuiGraphicsExtractor graphics, SPSGui.Rect row, int index, UUID stopId, List<Integer> colors, int mouseX, int mouseY) {
        boolean dragging = this.dragState != null && this.dragState.stopId().equals(stopId) && this.dragState.fromLeft();
        boolean missing = isInvalidDraftStop(stopId);
        RouteEditorGui.paperSection(graphics, row, row.contains(mouseX, mouseY), dragging);
        if (missing) {
            graphics.fill(row.x() + 1, row.y() + 1, row.right() - 1, row.bottom() - 1, 0x22D33C3C);
            graphics.outline(row.x(), row.y(), row.width(), row.height(), RouteEditorGui.DANGER);
        } else {
            RouteEditorGui.routeStripe(graphics, row.x(), row.y(), row.height(), colors);
        }
        SPSGui.text(graphics, this.font, String.format("%02d", index + 1), row.x() + 6, row.y() + 6, missing ? RouteEditorGui.DANGER : RouteEditorGui.INK_MUTED);
        SPSGui.text(graphics, this.font, SPSGui.scrollingText(this.font, stopLabel(stopId), row.width() - 44, index), row.x() + 28, row.y() + 5, missing ? RouteEditorGui.DANGER : RouteEditorGui.INK_PRIMARY);
        if (!missing) {
            renderTransferSummary(graphics, transferLinesForStop(stopId), row.x() + 28, row.y() + CURRENT_ROW_HEIGHT, row.width() - 32, stopId.hashCode(), false);
        }
        if (missing && row.contains(mouseX, mouseY)) {
            graphics.fill(row.x(), row.y(), row.right(), row.bottom(), 0x55D33C3C);
            SPSGui.drawCross(graphics, row.x() + row.width() / 2 - 8, row.y() + row.height() / 2 - 8, 16, 0xFFE02020, 2);
            this.showDocumentTooltip(Component.translatable("screen.superpipeslide.station_order.remove_missing_one"), mouseX, mouseY);
        }
    }

    private void renderSectionArrow(GuiGraphicsExtractor graphics, SPSGui.Rect arrow, RouteLayout layout, UUID fromStopId, UUID toStopId, int mouseX, int mouseY) {
        Optional<SectionPreview> section = sectionBetween(layout, fromStopId, toStopId);
        boolean invalidStop = isInvalidDraftStop(fromStopId) || isInvalidDraftStop(toStopId);
        int cx = layout.bidirectional() ? arrow.x() + 9 : arrow.x() + 8;
        if (layout.bidirectional()) {
            int forwardColor = invalidStop ? RouteEditorGui.WARNING : section.map(value -> arrowColor(value.forwardStatus())).orElse(RouteEditorGui.INK_MUTED);
            int reverseColor = invalidStop ? RouteEditorGui.WARNING : section.map(value -> arrowColor(value.reverseStatus())).orElse(RouteEditorGui.INK_MUTED);
            drawVerticalArrow(graphics, cx - 4, arrow.y(), arrow.bottom(), true, forwardColor);
            drawVerticalArrow(graphics, cx + 5, arrow.y(), arrow.bottom(), false, reverseColor);
        } else {
            int color = invalidStop ? RouteEditorGui.WARNING : section.map(value -> arrowColor(value.forwardStatus())).orElse(RouteEditorGui.INK_MUTED);
            drawVerticalArrow(graphics, cx, arrow.y(), arrow.bottom(), true, color);
        }
        if (arrow.contains(mouseX, mouseY)) {
            if (invalidStop) {
                this.showDocumentTooltip(Component.translatable("screen.superpipeslide.station_order.missing_platform"), mouseX, mouseY);
            } else if (section.isPresent()) {
                if (layout.bidirectional()) {
                    int direction = mouseX < arrow.x() + arrow.width() / 2 ? 1 : -1;
                    this.showDocumentTooltip(sectionTooltip(section.get(), direction), mouseX, mouseY);
                } else if (section.get().forwardStatus() == RouteSectionStatus.VALID) {
                    this.showDocumentTooltip(Component.translatable("screen.superpipeslide.station_order.section_distance", String.format("%.1f", section.get().forwardLength())), mouseX, mouseY);
                } else if (section.get().forwardStatus() == RouteSectionStatus.STALE || section.get().forwardStatus() == RouteSectionStatus.INCOMPLETE) {
                    this.showDocumentTooltip(Component.translatable("screen.superpipeslide.station_order.section_preview_unavailable"), mouseX, mouseY);
                } else {
                    this.showDocumentTooltip(Component.translatable("screen.superpipeslide.station_order.section_broken"), mouseX, mouseY);
                }
            } else {
                this.showDocumentTooltip(Component.translatable("screen.superpipeslide.station_order.section_broken"), mouseX, mouseY);
            }
        }
    }

    private static int arrowColor(RouteSectionStatus status) {
        return switch (status) {
            case VALID -> RouteEditorGui.INK_MUTED;
            case DISABLED -> RouteEditorGui.INK_DISABLED;
            case STALE, INCOMPLETE -> RouteEditorGui.BLUE;
            case BROKEN, AMBIGUOUS -> RouteEditorGui.WARNING;
        };
    }

    private static Component sectionTooltip(SectionPreview section, int direction) {
        Component name = Component.translatable(direction > 0 ? "screen.superpipeslide.direction.forward" : "screen.superpipeslide.direction.reverse");
        RouteSectionStatus status = direction > 0 ? section.forwardStatus() : section.reverseStatus();
        if (status == RouteSectionStatus.VALID) {
            double length = direction > 0 ? section.forwardLength() : section.reverseLength();
            return Component.translatable("screen.superpipeslide.layout.direction_distance", name, String.format("%.1f", length));
        }
        if (status == RouteSectionStatus.DISABLED) {
            return Component.translatable("screen.superpipeslide.layout.direction_disabled", name);
        }
        if (status == RouteSectionStatus.STALE || status == RouteSectionStatus.INCOMPLETE) {
            return Component.translatable("screen.superpipeslide.layout.direction_preview_unavailable", name);
        }
        return Component.translatable("screen.superpipeslide.layout.direction_broken", name);
    }

    private static void drawVerticalArrow(GuiGraphicsExtractor graphics, int cx, int top, int bottom, boolean downward, int color) {
        if (bottom - top < 14) {
            drawRotatedArrowIcon(graphics, cx, (top + bottom) / 2, downward, color);
            return;
        }
        if (downward) {
            int iconTop = bottom - 16;
            graphics.fill(cx - 1, top, cx + 2, iconTop + 2, color);
            drawRotatedArrowIcon(graphics, cx, iconTop + 8, true, color);
        } else {
            graphics.fill(cx - 2, top + 14, cx + 1, bottom, color);
            drawRotatedArrowIcon(graphics, cx, top + 8, false, color);
        }
    }

    private static void drawRotatedArrowIcon(GuiGraphicsExtractor graphics, int cx, int cy, boolean downward, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.pose().rotate(downward ? (float) (-Math.PI * 0.5D) : (float) (Math.PI * 0.5D));
        SPSGui.icon(graphics, new SPSGui.Rect(-8, -8, 16, 16), SPSGui.Icon.BACK, color);
        graphics.pose().popMatrix();
    }

    private Optional<SectionPreview> sectionBetween(RouteLayout layout, UUID fromStopId, UUID toStopId) {
        Optional<SectionPreview> persisted = layout.orderedSectionRefs().stream()
                .map(ref -> ClientRouteDataCache.routeSection(ref.routeSectionId()))
                .flatMap(Optional::stream)
                .filter(section -> section.fromPlatformStopId().equals(fromStopId) && section.toPlatformStopId().equals(toStopId))
                .map(section -> new SectionPreview(section.forwardStatus(), section.reverseStatus(), section.forwardLength(), section.reverseLength()))
                .findFirst();
        if (persisted.isEmpty()) {
            return previewSectionBetween(layout, fromStopId, toStopId);
        }
        RouteSectionStatus status = persisted.get().statusForLayout(layout);
        if (status == RouteSectionStatus.STALE || status == RouteSectionStatus.INCOMPLETE) {
            return previewSectionBetween(layout, fromStopId, toStopId).or(() -> persisted);
        }
        return persisted;
    }

    private Optional<SectionPreview> previewSectionBetween(RouteLayout layout, UUID fromStopId, UUID toStopId) {
        PreviewKey key = new PreviewKey(fromStopId, toStopId, layout.bidirectional(), ClientPipeNetworkCache.aggregateRevision(), ClientRouteDataCache.revision(), this.draftVersion);
        Optional<SectionPreview> cached = this.sectionPreviewCache.get(key);
        if (cached != null) {
            return cached;
        }
        if (this.sectionPreviewCache.size() > 512) {
            this.sectionPreviewCache.clear();
            this.pendingSectionPreviews.clear();
        }
        if (!this.pendingSectionPreviews.contains(key)) {
            this.pendingSectionPreviews.add(key);
        }
        return Optional.of(SectionPreview.stale(layout.bidirectional()));
    }

    private void processPendingSectionPreviews(RouteLayout layout, long maxNanos) {
        if (this.pendingSectionPreviews.isEmpty() || maxNanos <= 0L) {
            return;
        }
        long deadline = System.nanoTime() + maxNanos;
        do {
            PreviewKey key = this.pendingSectionPreviews.removeFirst();
            if (this.sectionPreviewCache.containsKey(key)) {
                continue;
            }
            if (key.pipeRevision() != ClientPipeNetworkCache.aggregateRevision()
                    || key.routeRevision() != ClientRouteDataCache.revision()
                    || key.bidirectional() != layout.bidirectional()
                    || key.draftVersion() != this.draftVersion) {
                continue;
            }
            this.sectionPreviewCache.put(key, computePreviewSectionBetween(layout, key.fromStopId(), key.toStopId()));
        } while (!this.pendingSectionPreviews.isEmpty() && System.nanoTime() < deadline);
    }

    private Optional<SectionPreview> computePreviewSectionBetween(RouteLayout layout, UUID fromStopId, UUID toStopId) {
        Optional<PlatformStop> from = ClientRouteDataCache.platformStop(fromStopId);
        Optional<PlatformStop> to = ClientRouteDataCache.platformStop(toStopId);
        if (from.isEmpty() || to.isEmpty()) {
            return Optional.empty();
        }
        PipeNetworkView pipeView = ClientPipeNetworkCache.globalView();
        Optional<PipeConnection> fromConnection = pipeView.connection(from.get().connectionRef());
        Optional<PipeConnection> toConnection = pipeView.connection(to.get().connectionRef());
        if (fromConnection.isEmpty() || toConnection.isEmpty()) {
            return Optional.of(SectionPreview.stale(layout.bidirectional()));
        }
        RoutePathfinder.SearchResult forward = RoutePathfinder.shortestPathResult(fromConnection.get(), toConnection.get(), pipeView);
            RoutePathfinder.SearchResult reverse = layout.bidirectional()
                    ? RoutePathfinder.shortestPathResult(toConnection.get(), fromConnection.get(), pipeView)
                    : new RoutePathfinder.SearchResult(RouteSectionStatus.DISABLED, Optional.empty());
        return Optional.of(new SectionPreview(
                forward.status(),
                    layout.bidirectional() ? reverse.status() : RouteSectionStatus.DISABLED,
                forward.optionalPath().map(RoutePathfinder.PathResult::length).orElse(0.0D),
                reverse.optionalPath().map(RoutePathfinder.PathResult::length).orElse(0.0D)
        ));
    }

    private void renderRight(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int top = this.rightPane.y() + 39;
        int bottom = this.rightPane.bottom() - 5;
        int y = top - (int) this.rightScroll;
        graphics.enableScissor(this.rightPane.x() + 1, top, this.rightPane.right() - 1, bottom);
        for (AvailableStationCard availableCard : availableStationCards()) {
            StationGroup station = availableCard.station();
            List<PlatformStop> stops = availableCard.stops();
            List<RouteLine> transferLines = availableCard.transferLines();
            int transferHeight = transferSummaryHeight(transferLines);
            int cardHeight = availableCard.height();
            SPSGui.Rect card = new SPSGui.Rect(this.rightPane.x() + 6, y, this.rightPane.width() - 12, cardHeight);
            if (card.bottom() >= top && card.y() <= bottom) {
                RouteEditorGui.paperSection(graphics, card, card.contains(mouseX, mouseY), false);
                RouteEditorGui.nameBlock(graphics, this.font, station.primaryName(), station.translatedNames(), card.x() + 7, card.y() + 5, card.width() - 14, station.id().hashCode());
                int transferY = card.y() + 5 + RouteEditorGui.nameBlockHeight(station.translatedNames()) + 3;
                renderTransferSummary(graphics, transferLines, card.x() + 7, transferY, card.width() - 14, station.id().hashCode(), true);
                int rowY = transferY + transferHeight + 2;
                for (PlatformStop stop : stops) {
                    SPSGui.Rect row = new SPSGui.Rect(card.x() + 6, rowY, card.width() - 12, 16);
                    renderAvailableRow(graphics, row, stop, mouseX, mouseY);
                    this.rightRows.add(new RowHit(row, stop.id(), -1));
                    rowY += 18;
                }
            }
            y += cardHeight + 4;
        }
        graphics.disableScissor();
    }

    private void renderAvailableRow(GuiGraphicsExtractor graphics, SPSGui.Rect row, PlatformStop stop, int mouseX, int mouseY) {
        boolean hovered = row.contains(mouseX, mouseY);
        if (hovered) {
            graphics.fill(row.x(), row.y(), row.right(), row.bottom(), 0x33226C9E);
        }
        SPSGui.icon(graphics, new SPSGui.Rect(row.x(), row.y(), 12, 12), SPSGui.Icon.DRAG, hovered ? RouteEditorGui.BLUE : RouteEditorGui.INK_MUTED);
        String fallback = Component.translatable("screen.superpipeslide.platform.length_short", String.format("%.1f", stop.length())).getString();
        SPSGui.text(graphics, this.font, SPSGui.scrollingText(this.font, stop.platformNumber() + " / " + stop.displayName().orElse(fallback), row.width() - 76, stop.id().hashCode()), row.x() + 14, row.y() + 4, RouteEditorGui.INK_SECONDARY);
        List<UUID> layoutIds = ClientRouteDataCache.routeLayoutIdsForPlatformStop(stop.id());
        Component status = layoutIds.isEmpty() ? Component.translatable("screen.superpipeslide.station_order.unassigned") : Component.translatable("screen.superpipeslide.station_order.layout_count", layoutIds.size());
        String statusText = status.getString();
        SPSGui.text(graphics, this.font, status, row.right() - this.font.width(statusText) - 4, row.y() + 4, RouteEditorGui.INK_MUTED);
    }

    private void renderDrag(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.dragState == null) {
            return;
        }
        SPSGui.Rect ghost = new SPSGui.Rect(mouseX + 5, mouseY + 5, 96, 18);
        graphics.fill(ghost.x(), ghost.y(), ghost.right(), ghost.bottom(), 0xEEF7F1E3);
        graphics.outline(ghost.x(), ghost.y(), ghost.width(), ghost.height(), RouteEditorGui.BLUE);
        SPSGui.text(graphics, this.font, SPSGui.scrollingText(this.font, stopLabel(this.dragState.stopId()), ghost.width() - 8, this.dragState.stopId().hashCode()), ghost.x() + 4, ghost.y() + 5, RouteEditorGui.INK_PRIMARY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            Optional<Integer> fallbackIndex = leftDraftIndexAt(event.x(), event.y());
            if (fallbackIndex.isPresent() && isInvalidDraftStop(this.draftStops.get(fallbackIndex.get()))) {
                removeDraftStopAt(fallbackIndex.get());
                return true;
            }
            for (RowHit row : this.leftRows) {
                if (row.bounds().contains(event.x(), event.y()) && isInvalidDraftStop(row.stopId())) {
                    removeDraftStopAt(row.index());
                    return true;
                }
            }
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != 0) {
            return false;
        }
        for (RowHit row : this.leftRows) {
            if (row.bounds().contains(event.x(), event.y())) {
                if (isInvalidDraftStop(row.stopId())) {
                    removeDraftStopAt(row.index());
                    return true;
                }
                this.dragState = new DragState(row.stopId(), row.index(), true);
                return true;
            }
        }
        for (RowHit row : this.rightRows) {
            if (row.bounds().contains(event.x(), event.y())) {
                this.dragState = new DragState(row.stopId(), -1, false);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return this.dragState != null || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragState != null) {
            DragState drag = this.dragState;
            this.dragState = null;
            if (drag.fromLeft() && isInvalidDraftStop(drag.stopId())) {
                removeDraftStopAt(drag.sourceIndex());
                return true;
            }
            if (this.leftPane.contains(event.x(), event.y())) {
                int target = insertionIndex(event.y());
                if (target < 0) {
                    return true;
                }
                if (drag.fromLeft()) {
                    UUID removed = this.draftStops.remove(drag.sourceIndex());
                    if (target > drag.sourceIndex()) {
                        target--;
                    }
                    this.draftStops.add(Math.max(0, Math.min(target, this.draftStops.size())), removed);
                } else if (!this.draftStops.contains(drag.stopId())) {
                    this.draftStops.add(Math.max(0, Math.min(target, this.draftStops.size())), drag.stopId());
                }
                this.dirty = true;
                this.invalidateDraftPreviews();
            } else if (drag.fromLeft() && this.rightPane.contains(event.x(), event.y())) {
                if (drag.sourceIndex() >= 0 && drag.sourceIndex() < this.draftStops.size()) {
                    this.draftStops.remove(drag.sourceIndex());
                    this.dirty = true;
                    this.invalidateDraftPreviews();
                }
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.leftPane.contains(mouseX, mouseY)) {
            this.leftScroll = Math.max(0.0D, Math.min(maxLeftScroll(), this.leftScroll - scrollY * 14.0D));
            return true;
        }
        if (this.rightPane.contains(mouseX, mouseY)) {
            this.rightScroll = Math.max(0.0D, Math.min(maxRightScroll(), this.rightScroll - scrollY * 14.0D));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int insertionIndex(double mouseY) {
        int top = this.leftPane.y() + 20;
        double localY = mouseY - top + this.leftScroll;
        if (ClientRouteDataCache.routeLayout(this.routeLayoutId).map(RouteLayout::loop).orElse(false) && this.draftStops.size() > 1) {
            int normalEnd = 0;
            for (UUID stopId : this.draftStops) {
                normalEnd += CURRENT_ROW_HEIGHT + leftTransferHeight(stopId) + CURRENT_ROW_GAP;
            }
            normalEnd = Math.max(0, normalEnd - CURRENT_ROW_GAP);
            if (localY > normalEnd + CURRENT_ROW_GAP / 2.0D) {
                return -1;
            }
        }
        for (int i = 0; i < this.draftStops.size(); i++) {
            int rowStart = draftOffsetForIndex(i);
            int nextStart = rowStart + CURRENT_ROW_HEIGHT + leftTransferHeight(this.draftStops.get(i)) + CURRENT_ROW_GAP;
            if (localY < (rowStart + nextStart) / 2.0D) {
                return i;
            }
        }
        return this.draftStops.size();
    }

    private void save() {
        if (hasInvalidDraftStops()) {
            return;
        }
        Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(this.routeLayoutId);
        boolean bidirectional = layout.map(RouteLayout::bidirectional).orElse(false);
        boolean loop = layout.map(RouteLayout::loop).orElse(false);
        ClientPacketDistributor.sendToServer(new ServerboundRouteEditPayload(ServerboundRouteEditPayload.SET_LAYOUT_STOPS, ClientRouteDataCache.revision(), Optional.of(this.routeLayoutId), "", List.of(), List.of(), List.copyOf(this.draftStops), bidirectional, loop));
        this.dirty = false;
    }

    private void removeMissingStops() {
        if (this.draftStops.removeIf(this::isInvalidDraftStop)) {
            this.dirty = true;
            this.dragState = null;
            this.leftScroll = Math.max(0.0D, Math.min(maxLeftScroll(), this.leftScroll));
            this.invalidateDraftPreviews();
        }
    }

    private void removeDraftStopAt(int index) {
        if (index >= 0 && index < this.draftStops.size()) {
            this.draftStops.remove(index);
            this.dirty = true;
            this.dragState = null;
            this.leftScroll = Math.max(0.0D, Math.min(maxLeftScroll(), this.leftScroll));
            this.invalidateDraftPreviews();
        }
    }

    private void invalidateDraftPreviews() {
        this.draftVersion++;
        this.sectionPreviewCache.clear();
        this.pendingSectionPreviews.clear();
    }

    private double maxLeftScroll() {
        int contentHeight = Math.max(CURRENT_ROW_HEIGHT, draftContentHeight());
        int visibleHeight = this.leftPane.height() - 25;
        return Math.max(0, contentHeight - visibleHeight);
    }

    private double maxRightScroll() {
        int contentHeight = availableStationCardHeights();
        int visibleHeight = this.rightPane.height() - 39;
        return Math.max(0, contentHeight - visibleHeight);
    }

    private int availableStationCardHeights() {
        return availableStationCards().stream().mapToInt(card -> card.height() + 4).sum();
    }

    private List<AvailableStationCard> availableStationCards() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        long routeRevision = ClientRouteDataCache.revision();
        long pipeRevision = ClientPipeNetworkCache.aggregateRevision();
        int draftHash = this.draftStops.hashCode();
        if (routeRevision == this.availableCardsRouteRevision
                && pipeRevision == this.availableCardsPipeRevision
                && draftHash == this.availableCardsDraftHash
                && query.equals(this.availableCardsQuery)) {
            return this.availableStationCardsCache;
        }

        Set<UUID> used = new HashSet<>(this.draftStops);
        List<AvailableStationCard> cards = new ArrayList<>();
        for (StationGroup station : ClientRouteDataCache.stationGroups().stream().sorted(Comparator.comparing(StationGroup::primaryName)).toList()) {
            List<PlatformStop> stops = ClientRouteDataCache.platformStopsInStation(station.id()).stream()
                    .filter(stop -> !used.contains(stop.id()))
                    .filter(this::availableForCurrentRoute)
                    .filter(stop -> query.isEmpty()
                            || station.primaryName().toLowerCase(java.util.Locale.ROOT).contains(query)
                            || SPSGui.translatedNamesLine(station.translatedNames()).toLowerCase(java.util.Locale.ROOT).contains(query)
                            || stop.platformNumber().toLowerCase(java.util.Locale.ROOT).contains(query)
                            || stop.displayName().orElse("").toLowerCase(java.util.Locale.ROOT).contains(query))
                    .toList();
            if (!stops.isEmpty()) {
                List<RouteLine> transferLines = transferLinesForStation(station.id());
                int headerHeight = 5 + RouteEditorGui.nameBlockHeight(station.translatedNames()) + 3 + transferSummaryHeight(transferLines) + 2;
                cards.add(new AvailableStationCard(station, stops, transferLines, headerHeight + stops.size() * 18 + 5));
            }
        }
        this.availableCardsRouteRevision = routeRevision;
        this.availableCardsPipeRevision = pipeRevision;
        this.availableCardsDraftHash = draftHash;
        this.availableCardsQuery = query;
        this.availableStationCardsCache = List.copyOf(cards);
        return this.availableStationCardsCache;
    }

    private int draftOffsetForIndex(int index) {
        int offset = 0;
        for (int i = 0; i < Math.min(index, this.draftStops.size()); i++) {
            offset += CURRENT_ROW_HEIGHT + leftTransferHeight(this.draftStops.get(i)) + CURRENT_ROW_GAP;
        }
        return offset;
    }

    private Optional<Integer> leftDraftIndexAt(double mouseX, double mouseY) {
        if (!this.leftPane.contains(mouseX, mouseY)) {
            return Optional.empty();
        }
        int top = this.leftPane.y() + 20;
        int bottom = this.leftPane.bottom() - 5;
        if (mouseY < top || mouseY >= bottom) {
            return Optional.empty();
        }
        double localY = mouseY - top + this.leftScroll;
        for (int i = 0; i < this.draftStops.size(); i++) {
            int rowStart = draftOffsetForIndex(i);
            int rowHeight = CURRENT_ROW_HEIGHT + leftTransferHeight(this.draftStops.get(i));
            if (localY >= rowStart && localY < rowStart + rowHeight) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private int draftContentHeight() {
        if (this.draftStops.isEmpty()) {
            return CURRENT_ROW_HEIGHT;
        }
        int height = 0;
        for (UUID stopId : this.draftStops) {
            height += CURRENT_ROW_HEIGHT + leftTransferHeight(stopId) + CURRENT_ROW_GAP;
        }
        int total = Math.max(CURRENT_ROW_HEIGHT, height - CURRENT_ROW_GAP);
        if (ClientRouteDataCache.routeLayout(this.routeLayoutId).map(RouteLayout::loop).orElse(false) && this.draftStops.size() > 1) {
            total += CURRENT_ROW_GAP + 18;
        }
        return total;
    }

    private int leftTransferHeight(UUID stopId) {
        if (isInvalidDraftStop(stopId)) {
            return 0;
        }
        List<RouteLine> lines = transferLinesForStop(stopId);
        return lines.size() > 1 ? transferSummaryHeight(lines) : 0;
    }

    private int transferSummaryHeight(List<RouteLine> lines) {
        if (lines.isEmpty()) {
            return 0;
        }
        return Math.min(MAX_TRANSFER_ROWS, lines.size()) * TRANSFER_ROW_HEIGHT + (lines.size() > MAX_TRANSFER_ROWS ? 8 : 0);
    }

    private void renderTransferSummary(GuiGraphicsExtractor graphics, List<RouteLine> lines, int x, int y, int width, int seed, boolean showSingleLine) {
        if (lines.isEmpty() || (!showSingleLine && lines.size() <= 1)) {
            return;
        }
        int maxShown = Math.min(MAX_TRANSFER_ROWS, lines.size());
        int start = lines.size() <= maxShown ? 0 : (int) ((System.currentTimeMillis() / 1400L + Math.floorMod(seed, 997)) % lines.size());
        for (int i = 0; i < maxShown; i++) {
            RouteLine line = lines.get((start + i) % lines.size());
            int yy = y + i * TRANSFER_ROW_HEIGHT + 2;
            SPSGui.colorStripe(graphics, x, yy + 2, 5, line.themeColors());
            graphics.fill(x + 5, yy + 4, x + 17, yy + 5, SPSGui.opaque(line.themeColors().isEmpty() ? RouteEditorGui.BLUE : line.themeColors().getFirst()));
            SPSGui.smallText(graphics, this.font, SPSGui.scrollingText(this.font, line.displayName(), Math.round((width - 22) / 0.7F), line.id().hashCode()), x + 21, yy, RouteEditorGui.INK_SECONDARY, 0.7F);
        }
        if (lines.size() > maxShown) {
            SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.more_count", lines.size() - maxShown).getString(), x + 21, y + maxShown * TRANSFER_ROW_HEIGHT + 1, RouteEditorGui.INK_MUTED, 0.66F);
        }
    }

    private List<RouteLine> transferLinesForStop(UUID stopId) {
        return ClientRouteDataCache.platformStop(stopId)
                .map(stop -> transferLinesForStation(stop.stationGroupId()))
                .orElse(List.of());
    }

    private static List<RouteLine> transferLinesForStation(UUID stationGroupId) {
        return ClientRouteDataCache.platformStopsInStation(stationGroupId).stream()
                .flatMap(stop -> routeLineIdsForStop(stop).stream())
                .distinct()
                .map(ClientRouteDataCache::routeLine)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(RouteLine::displayName))
                .toList();
    }

    private static List<UUID> routeLineIdsForStop(PlatformStop stop) {
        List<UUID> ids = new ArrayList<>();
        stop.routeLineId().ifPresent(ids::add);
        ClientRouteDataCache.routeLayoutIdsForPlatformStop(stop.id()).stream()
                .map(ClientRouteDataCache::routeLayout)
                .flatMap(Optional::stream)
                .map(RouteLayout::routeLineId)
                .forEach(ids::add);
        return ids;
    }

    @Override
    protected void onBack() {
        this.minecraft.setScreen(new RouteLayoutScreen(this.routeLayoutId));
    }

    private static String stopLabel(UUID stopId) {
        return ClientRouteDataCache.platformStop(stopId).map(stop -> {
            String station = ClientRouteDataCache.stationGroup(stop.stationGroupId()).map(StationGroup::primaryName).orElse(Component.translatable("screen.superpipeslide.station_order.missing_platform").getString());
            return station + " / " + stop.displayName().orElse(stop.platformNumber());
        }).orElse(Component.translatable("screen.superpipeslide.station_order.missing_platform").getString());
    }

    private boolean hasInvalidDraftStops() {
        return this.draftStops.stream().anyMatch(this::isInvalidDraftStop);
    }

    private boolean isInvalidDraftStop(UUID stopId) {
        Optional<PlatformStop> stop = ClientRouteDataCache.platformStop(stopId);
        return stop.isEmpty()
                || ClientRouteDataCache.stationGroup(stop.get().stationGroupId()).isEmpty()
                || isCurrentDimensionConnectionMissing(stop.get());
    }

    private static boolean isCurrentDimensionConnectionMissing(PlatformStop stop) {
        return ClientPipeNetworkCache.levelKey()
                .filter(levelKey -> levelKey.equals(stop.connectionRef().levelKey()))
                .map(ignored -> ClientPipeNetworkCache.currentConnection(stop.connectionId()).isEmpty()
                        && !ClientRouteDataCache.isWaitingForPipeRevision(ClientPipeNetworkCache.revision()))
                .orElse(false);
    }

    private Optional<UUID> layoutRouteLineId() {
        return ClientRouteDataCache.routeLayout(this.routeLayoutId).map(RouteLayout::routeLineId);
    }

    private boolean availableForCurrentRoute(PlatformStop stop) {
        Optional<UUID> currentRouteLineId = layoutRouteLineId();
        if (currentRouteLineId.isEmpty()) {
            return false;
        }
        UUID currentLineId = currentRouteLineId.get();
        if (stop.routeLineId().isPresent()) {
            return stop.routeLineId().filter(currentLineId::equals).isPresent();
        }

        List<RouteLayout> referencingLayouts = ClientRouteDataCache.routeLayoutIdsForPlatformStop(stop.id()).stream()
                .map(ClientRouteDataCache::routeLayout)
                .flatMap(Optional::stream)
                .toList();
        if (referencingLayouts.stream().anyMatch(layout -> !layout.routeLineId().equals(currentLineId))) {
            return false;
        }
        return true;
    }

    private record RowHit(SPSGui.Rect bounds, UUID stopId, int index) {
    }

    private record DragState(UUID stopId, int sourceIndex, boolean fromLeft) {
    }

    private record AvailableStationCard(StationGroup station, List<PlatformStop> stops, List<RouteLine> transferLines, int height) {
    }

    private record SectionPreview(RouteSectionStatus forwardStatus, RouteSectionStatus reverseStatus, double forwardLength, double reverseLength) {
        static SectionPreview stale(boolean bidirectional) {
            return new SectionPreview(RouteSectionStatus.STALE, bidirectional ? RouteSectionStatus.STALE : RouteSectionStatus.DISABLED, 0.0D, 0.0D);
        }

        RouteSectionStatus statusForLayout(RouteLayout layout) {
            return layout.bidirectional() ? worse(this.forwardStatus, this.reverseStatus) : this.forwardStatus;
        }

        private static RouteSectionStatus worse(RouteSectionStatus first, RouteSectionStatus second) {
            return severity(first) >= severity(second) ? first : second;
        }

        private static int severity(RouteSectionStatus status) {
            return switch (status) {
            case VALID -> 0;
            case DISABLED -> 0;
            case STALE -> 1;
                case INCOMPLETE -> 2;
                case AMBIGUOUS -> 3;
                case BROKEN -> 4;
            };
        }
    }

    private record PreviewKey(UUID fromStopId, UUID toStopId, boolean bidirectional, long pipeRevision, long routeRevision, int draftVersion) {
    }
}
