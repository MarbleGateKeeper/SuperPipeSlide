package dev.marblegate.superpipeslide.client.gui.station;


import dev.marblegate.superpipeslide.client.gui.base.RouteDataAwareScreen;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.client.gui.platform.PlatformStopEditorScreen;
import dev.marblegate.superpipeslide.client.gui.route.RouteEditorGui;
import dev.marblegate.superpipeslide.client.gui.route.RouteEditorScreenBase;
import dev.marblegate.superpipeslide.client.gui.route.RouteLineCreateScreen;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.network.station.ServerboundStationEditPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class StationEditorScreen extends RouteEditorScreenBase implements RouteDataAwareScreen {
    private final UUID stationGroupId;
    private EditBox primaryName;
    private EditBox translatedNames;
    private double platformScroll;
    private SPSGui.Rect platformArea = new SPSGui.Rect(0, 0, 0, 0);

    public StationEditorScreen(UUID stationGroupId) {
        super(Component.translatable("screen.superpipeslide.station_editor"));
        this.stationGroupId = stationGroupId;
    }

    @Override
    protected SPSGui.Rect createPanelRect() {
        return RouteEditorGui.panelRect(this.width, this.height, 400, 222);
    }

    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();
        Optional<StationGroup> station = ClientRouteDataCache.stationGroup(this.stationGroupId);
        if (station.isEmpty()) {
            return;
        }
        int left = this.panel.x() + 18;
        int top = this.panel.y() + 54;
        this.primaryName = this.borderlessBox(left + 40, top, 82, station.get().primaryName());
        this.translatedNames = this.borderlessBox(left + 40, top + 26, 82, String.join(", ", station.get().translatedNames()));
    }

    @Override
    public void refreshFromRouteSnapshot() {
        if (this.primaryName == null || (!this.primaryName.isFocused() && !this.translatedNames.isFocused())) {
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
        SPSGui.Rect save = RouteEditorGui.titleActionBounds(this.panel, 0);
        SPSGui.Rect claim = RouteEditorGui.titleActionBounds(this.panel, 1);
        SPSGui.Rect transfers = RouteEditorGui.titleActionBounds(this.panel, 2);
        boolean saveActive = isDirty(station.get());
        this.drawTitle(graphics, Component.translatable("screen.superpipeslide.station.title", station.get().primaryName()), true, List.of(new SPSGui.IconButton(SPSGui.Icon.SPLIT, RouteEditorGui.BLUE, false), new SPSGui.IconButton(SPSGui.Icon.ITEM_PLUS, RouteEditorGui.BLUE, false), new SPSGui.IconButton(SPSGui.Icon.SAVE, RouteEditorGui.BLUE, !saveActive)), mouseX, mouseY);
        SPSGui.Rect content = this.editorContent();
        this.drawDocumentHeader(graphics, SPSGui.Icon.STATION_ORDER, List.of(Component.translatable("screen.superpipeslide.station_editor"), Component.literal(station.get().primaryName())), saveActive ? Component.translatable("screen.superpipeslide.editor.unsaved") : Component.translatable("screen.superpipeslide.editor.saved"), saveActive ? RouteEditorGui.SAVE : RouteEditorGui.SUCCESS, station.get().id().hashCode());
        if (saveActive) {
            this.addClick(save, () -> save(false), Component.translatable("screen.superpipeslide.action.save_station"));
        }
        this.addClick(claim, () -> save(true), Component.translatable("screen.superpipeslide.action.generate_claimer"));
        this.addClick(transfers, () -> this.minecraft.setScreen(new StationTransferEditorScreen(this.stationGroupId)), Component.translatable("screen.superpipeslide.station_transfer.open"));

        int top = this.documentBodyY();
        SPSGui.Rect info = new SPSGui.Rect(content.x(), top, 138, content.bottom() - top);
        SPSGui.Rect platforms = new SPSGui.Rect(info.right() + 8, top, content.right() - info.right() - 8, content.bottom() - top);
        RouteEditorGui.paperSection(graphics, info, false, false);
        RouteEditorGui.worksheetPane(graphics, platforms, platforms.contains(mouseX, mouseY), false);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.station.info"), info.x() + 8, info.y() + 7, RouteEditorGui.INK_PRIMARY);
        Component nameLabel = Component.translatable("screen.superpipeslide.field.name");
        Component namesLabel = Component.translatable("screen.superpipeslide.field.names");
        RouteEditorGui.fieldLabel(graphics, this.font, nameLabel, info.x() + 8, info.y() + 27);
        RouteEditorGui.fieldLabel(graphics, this.font, namesLabel, info.x() + 8, info.y() + 53);
        this.layoutBoxAfterLabel(this.primaryName, nameLabel, info.x() + 8, info.y() + 23, info.right() - 8, 5);
        this.layoutBoxAfterLabel(this.translatedNames, namesLabel, info.x() + 8, info.y() + 49, info.right() - 8, 5);
        this.drawInputEditableIcon(graphics, this.primaryName);
        this.drawInputEditableIcon(graphics, this.translatedNames);
        SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.station.claim_radius", String.format("%.0f", station.get().platformClaimRadius())).getString(), info.x() + 8, info.y() + 88, RouteEditorGui.INK_MUTED, 0.72F);
        SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.station.position", station.get().stationBlockPos().toShortString()).getString(), info.x() + 8, info.y() + 100, RouteEditorGui.INK_MUTED, 0.72F);
        SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.station_transfer.count", ClientRouteDataCache.stationTransferLinksForStation(station.get().id()).size()).getString(), info.x() + 8, info.y() + 112, RouteEditorGui.INK_MUTED, 0.72F);

        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.platforms"), platforms.x() + 8, platforms.y() + 7, RouteEditorGui.INK_PRIMARY);
        this.platformArea = platforms;
        this.platformScroll = Math.max(0.0D, Math.min(maxPlatformScroll(), this.platformScroll));
        renderPlatformCards(graphics, platforms, mouseX, mouseY);
        RouteEditorGui.scrollEdges(graphics, platforms, this.platformScroll > 0.5D, this.platformScroll < maxPlatformScroll() - 0.5D, true);
        RouteEditorGui.thinScrollbar(graphics, platforms, this.platformScroll, maxPlatformScroll(), platforms.contains(mouseX, mouseY));
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.renderTooltips(graphics, mouseX, mouseY);
    }

    private void renderPlatformCards(GuiGraphicsExtractor graphics, SPSGui.Rect area, int mouseX, int mouseY) {
        List<PlatformStop> stops = ClientRouteDataCache.platformStopsInStation(this.stationGroupId).stream()
                .sorted(Comparator.comparing(PlatformStop::platformNumber))
                .toList();
        if (stops.isEmpty()) {
            SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.station.no_platforms"), area.x() + 8, area.y() + 28, SPSGui.TEXT_MUTED);
            return;
        }
        int top = area.y() + 24;
        int bottom = area.bottom() - 5;
        int y = top - (int) this.platformScroll;
        graphics.enableScissor(area.x() + 1, top, area.right() - 1, bottom);
        for (PlatformStop stop : stops) {
                SPSGui.Rect card = new SPSGui.Rect(area.x() + 6, y, area.width() - 12, 28);
                if (card.bottom() >= top && card.y() <= bottom) {
                    RouteEditorGui.paperSection(graphics, card, card.contains(mouseX, mouseY), false);
                    List<RouteLine> serviceLines = serviceLines(stop);
                    if (!serviceLines.isEmpty()) {
                        RouteEditorGui.routeStripe(graphics, card.x(), card.y(), card.height(), serviceLines.getFirst().themeColors());
                    }
                    SPSGui.text(graphics, this.font, SPSGui.scrollingText(this.font, stop.platformNumber() + " / " + stop.displayName().orElse(Component.translatable("screen.superpipeslide.platform").getString()), card.width() - 62, stop.id().hashCode()), card.x() + 7, card.y() + 5, RouteEditorGui.INK_PRIMARY);
                    int layoutCount = ClientRouteDataCache.routeLayoutIdsForPlatformStop(stop.id()).size();
                    String summary = Component.translatable("screen.superpipeslide.platform.summary", String.format("%.1f", stop.length()), layoutCount).getString();
                    SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, summary, Math.round((card.width() - 62) / 0.72F)), card.x() + 7, card.y() + 18, RouteEditorGui.INK_SECONDARY, 0.72F);
                    if (serviceLines.isEmpty()) {
                        String draft = Component.translatable("screen.superpipeslide.status.draft").getString();
                        RouteEditorGui.stamp(graphics, this.font, draft, card.right() - this.font.width(draft) - 12, card.y() + 4, RouteEditorGui.INK_MUTED);
                    }
                    this.addClick(card, () -> this.minecraft.setScreen(new PlatformStopEditorScreen(stop.id())), Component.translatable("screen.superpipeslide.action.open_platform"));
                }
            y += 32;
        }
        graphics.disableScissor();
    }

    private static List<RouteLine> serviceLines(PlatformStop stop) {
        return ClientRouteDataCache.routeLayoutIdsForPlatformStop(stop.id()).stream()
                .map(ClientRouteDataCache::routeLayout)
                .flatMap(Optional::stream)
                .map(RouteLayout::routeLineId)
                .distinct()
                .map(ClientRouteDataCache::routeLine)
                .flatMap(Optional::stream)
                .toList();
    }

    private void save(boolean generateClaimer) {
        ClientPacketDistributor.sendToServer(new ServerboundStationEditPayload(ClientRouteDataCache.revision(), this.stationGroupId, this.primaryName.getValue(), RouteLineCreateScreen.splitCsv(this.translatedNames.getValue()), generateClaimer));
    }

    private boolean isDirty(StationGroup station) {
        return this.primaryName != null
                && (!station.primaryName().equals(this.primaryName.getValue())
                || !station.translatedNames().equals(RouteLineCreateScreen.splitCsv(this.translatedNames.getValue())));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.platformArea.contains(mouseX, mouseY)) {
            this.platformScroll = Math.max(0.0D, Math.min(maxPlatformScroll(), this.platformScroll - scrollY * 14.0D));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private double maxPlatformScroll() {
        int contentHeight = ClientRouteDataCache.platformStopsInStation(this.stationGroupId).size() * 32;
        int visibleHeight = Math.max(0, this.platformArea.height() - 29);
        return Math.max(0, contentHeight - visibleHeight);
    }
}
