package dev.marblegate.superpipeslide.client.gui.station;

import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.gui.base.RouteDataAwareScreen;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.client.gui.route.RouteEditorGui;
import dev.marblegate.superpipeslide.client.gui.route.RouteEditorScreenBase;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationTransferLink;
import dev.marblegate.superpipeslide.config.Config;
import dev.marblegate.superpipeslide.network.station.ServerboundStationTransferEditPayload;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class StationTransferEditorScreen extends RouteEditorScreenBase implements RouteDataAwareScreen {
    private static final int CURRENT_ROW_HEIGHT = 34;
    private static final int CANDIDATE_ROW_HEIGHT = 36;

    private final UUID stationGroupId;
    private EditBox searchBox;
    private double currentScroll;
    private double candidateScroll;
    private SPSGui.Rect currentArea = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect candidateArea = new SPSGui.Rect(0, 0, 0, 0);

    public StationTransferEditorScreen(UUID stationGroupId) {
        super(Component.translatable("screen.superpipeslide.station_transfer.title"));
        this.stationGroupId = stationGroupId;
    }

    @Override
    protected SPSGui.Rect createPanelRect() {
        return RouteEditorGui.panelRect(this.width, this.height, 520, 276);
    }

    @Override
    protected void rebuildWidgets() {
        String search = this.searchBox == null ? "" : this.searchBox.getValue();
        this.clearWidgets();
        if (ClientRouteDataCache.stationGroup(this.stationGroupId).isEmpty()) {
            return;
        }
        this.searchBox = this.borderlessBox(0, 0, 120, search);
    }

    @Override
    public void refreshFromRouteSnapshot() {
        if (this.searchBox == null || !this.searchBox.isFocused()) {
            this.rebuildWidgets();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.beginFrame();
        this.drawEditorFrame(graphics);
        Optional<StationGroup> station = ClientRouteDataCache.stationGroup(this.stationGroupId);
        if (station.isEmpty()) {
            this.drawTitle(graphics, Component.translatable("screen.superpipeslide.station.missing"), true, List.of(), mouseX, mouseY);
            SPSGui.centeredText(graphics, this.font, Component.translatable("screen.superpipeslide.station.missing.body"), this.panel.x() + this.panel.width() / 2, this.panel.y() + 92, RouteEditorGui.INK_MUTED);
            this.renderTooltips(graphics, mouseX, mouseY);
            return;
        }

        List<StationTransferLink> transferLinks = transferLinks(station.get());
        this.drawTitle(graphics, Component.translatable("screen.superpipeslide.station_transfer.header", station.get().primaryName()), true, List.of(), mouseX, mouseY);
        this.drawDocumentHeader(
                graphics,
                SPSGui.Icon.SPLIT,
                List.of(Component.translatable("screen.superpipeslide.station_editor"), Component.literal(station.get().primaryName()), Component.translatable("screen.superpipeslide.station_transfer.short")),
                Component.translatable("screen.superpipeslide.station_transfer.count", transferLinks.size()),
                transferLinks.isEmpty() ? RouteEditorGui.INK_MUTED : RouteEditorGui.BLUE,
                station.get().id().hashCode()
        );

        SPSGui.Rect content = this.editorContent();
        int top = this.documentBodyY();
        int leftWidth = Math.min(202, Math.max(168, (int) Math.round(content.width() * 0.40D)));
        this.currentArea = new SPSGui.Rect(content.x(), top, leftWidth, content.bottom() - top);
        this.candidateArea = new SPSGui.Rect(this.currentArea.right() + 8, top, content.right() - this.currentArea.right() - 8, content.bottom() - top);

        RouteEditorGui.paperSection(graphics, this.currentArea, this.currentArea.contains(mouseX, mouseY), false);
        RouteEditorGui.worksheetPane(graphics, this.candidateArea, this.candidateArea.contains(mouseX, mouseY), false);
        renderCurrentTransfers(graphics, station.get(), transferLinks, mouseX, mouseY);
        renderCandidateStations(graphics, station.get(), mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        renderSearchChrome(graphics, mouseX, mouseY);
        this.renderTooltips(graphics, mouseX, mouseY);
    }

    private void renderCurrentTransfers(GuiGraphicsExtractor graphics, StationGroup station, List<StationTransferLink> links, int mouseX, int mouseY) {
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.station_transfer.current"), this.currentArea.x() + 8, this.currentArea.y() + 7, RouteEditorGui.INK_PRIMARY);
        if (links.isEmpty()) {
            SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.station_transfer.none").getString(), this.currentArea.x() + 8, this.currentArea.y() + 28, RouteEditorGui.INK_MUTED, 0.72F);
            return;
        }
        int listTop = this.currentArea.y() + 24;
        int listBottom = this.currentArea.bottom() - 5;
        this.currentScroll = Math.max(0.0D, Math.min(maxCurrentScroll(), this.currentScroll));
        graphics.enableScissor(this.currentArea.x() + 1, listTop, this.currentArea.right() - 1, listBottom);
        int y = listTop - (int) this.currentScroll;
        for (StationTransferLink link : links) {
            Optional<UUID> otherId = link.other(station.id());
            Optional<StationGroup> other = otherId.flatMap(ClientRouteDataCache::stationGroup);
            if (other.isPresent()) {
                SPSGui.Rect card = new SPSGui.Rect(this.currentArea.x() + 6, y, this.currentArea.width() - 12, 30);
                if (card.bottom() >= listTop && card.y() <= listBottom) {
                    renderTransferCard(graphics, station, other.get(), link, card, true, card.contains(mouseX, mouseY));
                    SPSGui.Rect clipped = clip(card, new SPSGui.Rect(this.currentArea.x() + 1, listTop, this.currentArea.width() - 2, listBottom - listTop));
                    if (clipped.width() > 0 && clipped.height() > 0) {
                        this.addClick(clipped, () -> removeTransfer(other.get().id()), Component.translatable("screen.superpipeslide.station_transfer.action.remove"));
                    }
                }
            }
            y += CURRENT_ROW_HEIGHT;
        }
        graphics.disableScissor();
        RouteEditorGui.scrollEdges(graphics, this.currentArea, this.currentScroll > 0.5D, this.currentScroll < maxCurrentScroll() - 0.5D, false);
        RouteEditorGui.thinScrollbar(graphics, this.currentArea, this.currentScroll, maxCurrentScroll(), this.currentArea.contains(mouseX, mouseY));
    }

    private void renderCandidateStations(GuiGraphicsExtractor graphics, StationGroup station, int mouseX, int mouseY) {
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.station_transfer.all_stations"), this.candidateArea.x() + 8, this.candidateArea.y() + 7, RouteEditorGui.INK_PRIMARY);
        layoutSearchBox();
        List<StationGroup> stations = filteredStations(station);
        if (stations.isEmpty()) {
            SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.station_transfer.no_results").getString(), this.candidateArea.x() + 8, this.candidateArea.y() + 32, RouteEditorGui.INK_MUTED, 0.72F);
            return;
        }
        int listTop = this.candidateArea.y() + 28;
        int listBottom = this.candidateArea.bottom() - 5;
        this.candidateScroll = Math.max(0.0D, Math.min(maxCandidateScroll(stations.size()), this.candidateScroll));
        graphics.enableScissor(this.candidateArea.x() + 1, listTop, this.candidateArea.right() - 1, listBottom);
        int y = listTop - (int) this.candidateScroll;
        for (StationGroup other : stations) {
            StationTransferLink link = transferLinkBetween(station.id(), other.id()).orElse(null);
            SPSGui.Rect card = new SPSGui.Rect(this.candidateArea.x() + 6, y, this.candidateArea.width() - 12, 32);
            if (card.bottom() >= listTop && card.y() <= listBottom) {
                renderTransferCard(graphics, station, other, link, card, false, card.contains(mouseX, mouseY));
                SPSGui.Rect clipped = clip(card, new SPSGui.Rect(this.candidateArea.x() + 1, listTop, this.candidateArea.width() - 2, listBottom - listTop));
                if (clipped.width() > 0 && clipped.height() > 0) {
                    if (link == null) {
                        this.addClick(clipped, () -> addTransfer(station, other, false), Component.translatable("screen.superpipeslide.station_transfer.action.add"));
                    } else {
                        this.addClick(clipped, () -> removeTransfer(other.id()), Component.translatable("screen.superpipeslide.station_transfer.action.remove"));
                    }
                }
            }
            y += CANDIDATE_ROW_HEIGHT;
        }
        graphics.disableScissor();
        RouteEditorGui.scrollEdges(graphics, this.candidateArea, this.candidateScroll > 0.5D, this.candidateScroll < maxCandidateScroll(stations.size()) - 0.5D, true);
        RouteEditorGui.thinScrollbar(graphics, this.candidateArea, this.candidateScroll, maxCandidateScroll(stations.size()), this.candidateArea.contains(mouseX, mouseY));
    }

    private void renderTransferCard(GuiGraphicsExtractor graphics, StationGroup station, StationGroup other, StationTransferLink link, SPSGui.Rect card, boolean currentList, boolean hovered) {
        boolean linked = link != null;
        RouteEditorGui.paperSection(graphics, card, hovered, linked);
        int color = linked && link.risky() ? RouteEditorGui.WARNING : linked ? RouteEditorGui.BLUE : RouteEditorGui.INK_MUTED;
        SPSGui.icon(graphics, new SPSGui.Rect(card.x() + 5, card.y() + 7, 14, 14), linked ? SPSGui.Icon.CONFIRM : SPSGui.Icon.PLUS, color);
        int textX = card.x() + 23;
        String name = FullStationName.display(other);
        int stampWidth = linked ? 46 : 38;
        SPSGui.text(graphics, this.font, SPSGui.scrollingText(this.font, name, card.width() - stampWidth - 28, other.id().hashCode()), textX, card.y() + 5, RouteEditorGui.INK_PRIMARY);
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, transferMeta(station, other, link), Math.round((card.width() - 36) / 0.72F)), textX, card.y() + 18, RouteEditorGui.INK_SECONDARY, 0.72F);
        if (linked) {
            String stamp = link.risky()
                    ? Component.translatable("screen.superpipeslide.station_transfer.risky").getString()
                    : (link.manual() ? Component.translatable("screen.superpipeslide.station_transfer.manual").getString() : Component.translatable("screen.superpipeslide.station_transfer.auto").getString());
            RouteEditorGui.stamp(graphics, this.font, stamp, card.right() - this.font.width(stamp) - 12, card.y() + 4, link.risky() ? RouteEditorGui.WARNING : RouteEditorGui.BLUE);
            SPSGui.icon(graphics, new SPSGui.Rect(card.right() - 18, card.bottom() - 17, 13, 13), currentList ? SPSGui.Icon.REMOVE : SPSGui.Icon.CHECKBOX_ON, currentList ? RouteEditorGui.DANGER : RouteEditorGui.BLUE);
        }
    }

    private void renderSearchChrome(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.searchBox == null || this.candidateArea.width() <= 0) {
            return;
        }
        SPSGui.Rect searchIcon = new SPSGui.Rect(this.searchBox.getX() - 14, this.searchBox.getY() - 1, 12, 12);
        SPSGui.icon(graphics, searchIcon, SPSGui.Icon.SEARCH, RouteEditorGui.INK_MUTED);
        this.drawSearchPlaceholder(graphics, this.searchBox);
        int underlineY = this.searchBox.getY() + 12;
        graphics.fill(this.searchBox.getX(), underlineY, this.searchBox.getX() + this.searchBox.getWidth(), underlineY + 1, this.searchBox.isFocused() ? RouteEditorGui.BLUE : RouteEditorGui.PAPER_LINE);
    }

    private void layoutSearchBox() {
        if (this.searchBox == null) {
            return;
        }
        int width = Math.min(128, Math.max(68, this.candidateArea.width() - 106));
        this.searchBox.setX(this.candidateArea.right() - width - 9);
        this.searchBox.setY(this.candidateArea.y() + 4);
        this.searchBox.setWidth(width);
    }

    private List<StationTransferLink> transferLinks(StationGroup station) {
        return ClientRouteDataCache.stationTransferLinksForStation(station.id()).stream()
                .sorted(Comparator
                        .comparingDouble((StationTransferLink link) -> link.other(station.id())
                                .flatMap(ClientRouteDataCache::stationGroup)
                                .map(other -> distanceSortKey(station, other))
                                .orElse(Double.POSITIVE_INFINITY))
                        .thenComparing(link -> link.other(station.id())
                        .flatMap(ClientRouteDataCache::stationGroup)
                        .map(FullStationName::display)
                        .orElse("")))
                .toList();
    }

    private List<StationGroup> filteredStations(StationGroup station) {
        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        return ClientRouteDataCache.stationGroups().stream()
                .filter(other -> !other.id().equals(station.id()))
                .filter(other -> query.isBlank() || searchText(other).contains(query))
                .sorted(Comparator.comparing((StationGroup other) -> !other.levelKey().equals(station.levelKey()))
                        .thenComparingDouble(other -> distanceSortKey(station, other))
                        .thenComparing(FullStationName::display))
                .toList();
    }

    private Optional<StationTransferLink> transferLinkBetween(UUID stationId, UUID otherId) {
        return ClientRouteDataCache.stationTransferLinksForStation(stationId).stream()
                .filter(link -> link.connects(stationId, otherId))
                .findFirst();
    }

    private void addTransfer(StationGroup station, StationGroup other, boolean confirmedRisk) {
        if (!confirmedRisk && transferRisky(station, other)) {
            this.requestDangerConfirmation(
                    Component.translatable("screen.superpipeslide.station_transfer.confirm.title"),
                    Component.translatable("screen.superpipeslide.station_transfer.confirm.body", FullStationName.display(other)),
                    Component.translatable("screen.superpipeslide.station_transfer.confirm.accept"),
                    () -> addTransfer(station, other, true)
            );
            return;
        }
        ClientPacketDistributor.sendToServer(new ServerboundStationTransferEditPayload(ServerboundStationTransferEditPayload.ADD, ClientRouteDataCache.revision(), station.id(), other.id(), confirmedRisk));
    }

    private void removeTransfer(UUID otherStationId) {
        ClientPacketDistributor.sendToServer(new ServerboundStationTransferEditPayload(ServerboundStationTransferEditPayload.REMOVE, ClientRouteDataCache.revision(), this.stationGroupId, otherStationId, false));
    }

    private boolean transferRisky(StationGroup station, StationGroup other) {
        if (!station.levelKey().equals(other.levelKey())) {
            return true;
        }
        double warningDistance = Config.FAR_OUT_OF_STATION_TRANSFER_WARNING_DISTANCE.getAsDouble();
        return distance(station, other) > warningDistance;
    }

    private String transferMeta(StationGroup station, StationGroup other, StationTransferLink link) {
        String dimension = other.levelKey().equals(station.levelKey())
                ? Component.translatable("screen.superpipeslide.station_transfer.same_dimension").getString()
                : Component.translatable("screen.superpipeslide.station_transfer.cross_dimension").getString();
        String distance = other.levelKey().equals(station.levelKey())
                ? Component.translatable("screen.superpipeslide.station_transfer.blocks", Math.round(distance(station, other))).getString()
                : dimensionName(other);
        int seconds = Math.max(1, Math.round((link == null ? estimatedWalkTicks(station, other) : link.estimatedWalkTicks()) / 20.0F));
        return Component.translatable("screen.superpipeslide.station_transfer.meta", dimension, distance, seconds).getString();
    }

    private static int estimatedWalkTicks(StationGroup station, StationGroup other) {
        double value = station.levelKey().equals(other.levelKey()) ? distance(station, other) : 192.0D;
        return Math.max(20 * 6, (int) Math.round(20 * 8 + value * 8.0D));
    }

    private static double distance(StationGroup station, StationGroup other) {
        long dx = (long) station.stationBlockPos().getX() - other.stationBlockPos().getX();
        long dy = (long) station.stationBlockPos().getY() - other.stationBlockPos().getY();
        long dz = (long) station.stationBlockPos().getZ() - other.stationBlockPos().getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static String dimensionName(StationGroup station) {
        return station.levelKey().identifier().toString();
    }

    private static String searchText(StationGroup station) {
        return (FullStationName.display(station) + " " + dimensionName(station) + " " + station.stationBlockPos().toShortString()).toLowerCase(Locale.ROOT);
    }

    private double maxCurrentScroll() {
        int contentHeight = ClientRouteDataCache.stationGroup(this.stationGroupId)
                .map(station -> transferLinks(station).size())
                .orElse(0) * CURRENT_ROW_HEIGHT;
        int visibleHeight = Math.max(0, this.currentArea.height() - 29);
        return Math.max(0, contentHeight - visibleHeight);
    }

    private static double distanceSortKey(StationGroup station, StationGroup other) {
        return station.levelKey().equals(other.levelKey()) ? distance(station, other) : Double.POSITIVE_INFINITY;
    }

    private double maxCandidateScroll(int stationCount) {
        int contentHeight = stationCount * CANDIDATE_ROW_HEIGHT;
        int visibleHeight = Math.max(0, this.candidateArea.height() - 33);
        return Math.max(0, contentHeight - visibleHeight);
    }

    private static SPSGui.Rect clip(SPSGui.Rect rect, SPSGui.Rect clip) {
        int x1 = Math.max(rect.x(), clip.x());
        int y1 = Math.max(rect.y(), clip.y());
        int x2 = Math.min(rect.right(), clip.right());
        int y2 = Math.min(rect.bottom(), clip.bottom());
        return new SPSGui.Rect(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.currentArea.contains(mouseX, mouseY)) {
            this.currentScroll = Math.max(0.0D, Math.min(maxCurrentScroll(), this.currentScroll - scrollY * 14.0D));
            return true;
        }
        if (this.candidateArea.contains(mouseX, mouseY)) {
            double maxScroll = ClientRouteDataCache.stationGroup(this.stationGroupId)
                    .map(station -> maxCandidateScroll(filteredStations(station).size()))
                    .orElse(0.0D);
            this.candidateScroll = Math.max(0.0D, Math.min(maxScroll, this.candidateScroll - scrollY * 16.0D));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void onBack() {
        this.minecraft.setScreen(new StationEditorScreen(this.stationGroupId));
    }

    private static final class FullStationName {
        private FullStationName() {
        }

        static String display(StationGroup station) {
            String translated = SPSGui.translatedNamesLine(station.translatedNames());
            return translated.isEmpty() ? station.primaryName() : station.primaryName() + " / " + translated;
        }
    }
}
