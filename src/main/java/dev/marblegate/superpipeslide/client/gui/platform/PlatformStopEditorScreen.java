package dev.marblegate.superpipeslide.client.gui.platform;


import dev.marblegate.superpipeslide.client.gui.base.RouteDataAwareScreen;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.client.gui.route.RouteEditorGui;
import dev.marblegate.superpipeslide.client.gui.route.RouteEditorScreenBase;
import dev.marblegate.superpipeslide.client.gui.station.StationEditorScreen;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.network.platform.ServerboundPlatformStopEditPayload;
import dev.marblegate.superpipeslide.network.route.ServerboundRouteEditPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PlatformStopEditorScreen extends RouteEditorScreenBase implements RouteDataAwareScreen {
    private final UUID platformStopId;
    private EditBox platformNumber;
    private EditBox displayName;

    public PlatformStopEditorScreen(UUID platformStopId) {
        super(Component.translatable("screen.superpipeslide.platform_editor"));
        this.platformStopId = platformStopId;
    }

    @Override
    protected SPSGui.Rect createPanelRect() {
        return RouteEditorGui.panelRect(this.width, this.height, 360, 184);
    }

    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();
        Optional<PlatformStop> stop = ClientRouteDataCache.platformStop(this.platformStopId);
        if (stop.isEmpty()) {
            return;
        }
        int left = this.panel.x() + 18;
        int top = this.panel.y() + 44;
        this.platformNumber = this.borderlessBox(left + 44, top + 25, 76, stop.get().platformNumber());
        this.displayName = this.borderlessBox(left + 44, top + 49, 76, stop.get().displayName().orElse(""));
    }

    @Override
    public void refreshFromRouteSnapshot() {
        if (this.platformNumber == null || (!this.platformNumber.isFocused() && !this.displayName.isFocused())) {
            this.rebuildWidgets();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.beginFrame();
        this.drawEditorFrame(graphics);
        Optional<PlatformStop> stop = ClientRouteDataCache.platformStop(this.platformStopId);
        if (stop.isEmpty()) {
            this.drawTitle(graphics, Component.translatable("screen.superpipeslide.platform.missing"), true, List.of(), mouseX, mouseY);
            SPSGui.centeredText(graphics, this.font, Component.translatable("screen.superpipeslide.platform.missing.body"), this.panel.x() + this.panel.width() / 2, this.panel.y() + 92, RouteEditorGui.INK_MUTED);
            this.renderTooltips(graphics, mouseX, mouseY);
            return;
        }
        String stationName = ClientRouteDataCache.stationGroup(stop.get().stationGroupId()).map(StationGroup::primaryName).orElse("?");
        SPSGui.Rect save = RouteEditorGui.titleActionBounds(this.panel, 0);
        SPSGui.Rect delete = RouteEditorGui.titleActionBounds(this.panel, 1);
        boolean saveActive = isDirty(stop.get());
        this.drawTitle(graphics, Component.translatable("screen.superpipeslide.platform.title", stationName, stop.get().platformNumber()), true, List.of(new SPSGui.IconButton(SPSGui.Icon.REMOVE, RouteEditorGui.DANGER, false), new SPSGui.IconButton(SPSGui.Icon.SAVE, RouteEditorGui.BLUE, !saveActive)), mouseX, mouseY);
        SPSGui.Rect content = this.editorContent();
        this.drawDocumentHeader(graphics, SPSGui.Icon.STATION_ORDER, List.of(Component.translatable("screen.superpipeslide.platform_editor"), Component.literal(stationName), Component.literal(stop.get().platformNumber())), saveActive ? Component.translatable("screen.superpipeslide.editor.unsaved") : Component.translatable("screen.superpipeslide.editor.saved"), saveActive ? RouteEditorGui.SAVE : RouteEditorGui.SUCCESS, stop.get().id().hashCode());
        if (saveActive) {
            this.addClick(save, this::save, Component.translatable("screen.superpipeslide.action.save_platform"));
        }
        this.addClick(delete, () -> this.requestDangerConfirmation(
                Component.translatable("screen.superpipeslide.confirm.delete_platform.title"),
                Component.translatable("screen.superpipeslide.confirm.delete_platform.body"),
                Component.translatable("screen.superpipeslide.action.delete_platform"),
                this::deletePlatform
        ), Component.translatable("screen.superpipeslide.action.delete_platform"));

        int top = this.documentBodyY();
        SPSGui.Rect info = new SPSGui.Rect(content.x(), top, 142, content.bottom() - top);
        SPSGui.Rect status = new SPSGui.Rect(info.right() + 8, top, content.right() - info.right() - 8, content.bottom() - top);
        RouteEditorGui.paperSection(graphics, info, false, false);
        RouteEditorGui.worksheetPane(graphics, status, false, false);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.platform.info"), info.x() + 8, info.y() + 7, RouteEditorGui.INK_PRIMARY);
        SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.platform.station", SPSGui.scrollingText(this.font, stationName, Math.round((info.width() - 18) / 0.72F), stationName.hashCode())).getString(), info.x() + 8, info.y() + 24, RouteEditorGui.INK_SECONDARY, 0.72F);
        Component numberLabel = Component.translatable("screen.superpipeslide.field.number");
        Component displayLabel = Component.translatable("screen.superpipeslide.field.display");
        RouteEditorGui.fieldLabel(graphics, this.font, numberLabel, info.x() + 8, info.y() + 48);
        RouteEditorGui.fieldLabel(graphics, this.font, displayLabel, info.x() + 8, info.y() + 74);
        this.layoutBoxAfterLabel(this.platformNumber, numberLabel, info.x() + 8, info.y() + 44, info.right() - 8, 5);
        this.layoutBoxAfterLabel(this.displayName, displayLabel, info.x() + 8, info.y() + 70, info.right() - 8, 5);
        this.drawInputEditableIcon(graphics, this.platformNumber);
        this.drawInputEditableIcon(graphics, this.displayName);
        if (duplicateNumber(stop.get())) {
            graphics.fill(info.x() + 7, info.bottom() - 24, info.right() - 7, info.bottom() - 9, 0x44F3C44C);
            SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.platform_editor.duplicate"), info.x() + 10, info.bottom() - 20, RouteEditorGui.WARNING);
        }

        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.platform.pipe_routes"), status.x() + 8, status.y() + 7, RouteEditorGui.INK_PRIMARY);
        SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.platform.pipe_length", String.format("%.1f", stop.get().length())).getString(), status.x() + 8, status.y() + 23, RouteEditorGui.INK_SECONDARY, 0.72F);
        RouteEditorGui.stamp(graphics, this.font, Component.translatable("screen.superpipeslide.platform.connection_ok").getString(), status.x() + 8, status.y() + 36, RouteEditorGui.SUCCESS);
        renderServices(graphics, stop.get(), status.x() + 8, status.y() + 50);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.renderTooltips(graphics, mouseX, mouseY);
    }

    private void renderServices(GuiGraphicsExtractor graphics, PlatformStop stop, int x, int y) {
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.platform.service_routes"), x, y, RouteEditorGui.INK_PRIMARY);
        int row = y + 14;
        List<UUID> layoutIds = ClientRouteDataCache.routeLayoutIdsForPlatformStop(stop.id());
        if (layoutIds.isEmpty()) {
            SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.platform.no_routes"), x, row, RouteEditorGui.INK_MUTED);
            return;
        }
        for (UUID layoutId : layoutIds) {
            Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(layoutId);
            Optional<RouteLine> line = layout.flatMap(value -> ClientRouteDataCache.routeLine(value.routeLineId()));
            if (layout.isEmpty() || line.isEmpty()) {
                continue;
            }
            RouteEditorGui.routeStripe(graphics, x, row, 10, line.get().themeColors());
            String label = line.get().displayName() + " / " + SPSGui.layoutName(layout.get());
            SPSGui.text(graphics, this.font, SPSGui.scrollingText(this.font, label, this.panel.right() - x - 20, layoutId.hashCode()), x + 7, row + 1, RouteEditorGui.INK_SECONDARY);
            row += 12;
        }
    }

    private boolean duplicateNumber(PlatformStop current) {
        String number = this.platformNumber == null ? current.platformNumber() : this.platformNumber.getValue();
        return ClientRouteDataCache.platformStops().stream()
                .filter(platformStop -> !platformStop.id().equals(current.id()))
                .anyMatch(platformStop -> platformStop.stationGroupId().equals(current.stationGroupId()) && platformStop.platformNumber().equals(number));
    }

    private void save() {
        String display = this.displayName.getValue().trim();
        ClientPacketDistributor.sendToServer(new ServerboundPlatformStopEditPayload(ClientRouteDataCache.revision(), this.platformStopId, this.platformNumber.getValue(), display.isEmpty() ? Optional.empty() : Optional.of(display)));
    }

    private boolean isDirty(PlatformStop stop) {
        if (this.platformNumber == null) {
            return false;
        }
        String display = this.displayName.getValue().trim();
        Optional<String> displayValue = display.isEmpty() ? Optional.empty() : Optional.of(display);
        return !stop.platformNumber().equals(this.platformNumber.getValue()) || !stop.displayName().equals(displayValue);
    }

    private void deletePlatform() {
        Optional<PlatformStop> stop = ClientRouteDataCache.platformStop(this.platformStopId);
        ClientPacketDistributor.sendToServer(new ServerboundRouteEditPayload(ServerboundRouteEditPayload.DELETE_PLATFORM_STOP, ClientRouteDataCache.revision(), Optional.of(this.platformStopId), "", List.of(), List.of(), List.of(), false, false));
        stop.ifPresentOrElse(value -> this.minecraft.setScreen(new StationEditorScreen(value.stationGroupId())), this::onClose);
    }
}
