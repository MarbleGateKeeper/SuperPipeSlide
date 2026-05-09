package dev.marblegate.superpipeslide.client.gui.route;


import dev.marblegate.superpipeslide.client.gui.base.RouteDataAwareScreen;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSection;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;
import dev.marblegate.superpipeslide.network.route.ServerboundRouteEditPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class RouteLayoutScreen extends RouteEditorScreenBase implements RouteDataAwareScreen {
    private static final double MAP_OVERSCROLL = 34.0D;
    private static final double MAP_OVERSCROLL_RETURN = 0.88D;

    private final UUID routeLayoutId;
    private EditBox nameBox;
    private EditBox translatedBox;
    private double mapScroll;
    private boolean draggingMap;
    private double dragStartX;
    private double dragStartScroll;
    private boolean mapDragged;
    private SPSGui.Rect mapBounds = new SPSGui.Rect(0, 0, 0, 0);
    private double renderedMapScroll;
    private long lastMapInteractionMs;
    private boolean nameAsSectionName = false;

    public RouteLayoutScreen(UUID routeLayoutId) {
        super(Component.translatable("screen.superpipeslide.layout"));
        this.routeLayoutId = routeLayoutId;
    }

    @Override
    protected SPSGui.Rect createPanelRect() {
        return RouteEditorGui.panelRect(this.width, this.height, 420, 248);
    }

    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();
        Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(this.routeLayoutId);
        if (layout.isEmpty()) {
            return;
        }
        this.nameAsSectionName = layout.get().nameAsSectionName();
        int top = this.panel.y() + 31;
        int left = this.panel.x() + 18;
        this.nameBox = box(left + 28, top + 123, 130, layout.get().displayName().orElse(""));
        this.translatedBox = box(left + 206, top + 123, 130, String.join(", ", layout.get().translatedNames()));
    }

    private EditBox box(int x, int y, int width, String value) {
        return this.borderlessBox(x, y, width, value);
    }

    @Override
    public void refreshFromRouteSnapshot() {
        if (this.nameBox == null || (!this.nameBox.isFocused() && !this.translatedBox.isFocused())) {
            this.rebuildWidgets();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.beginFrame();
        this.drawEditorFrame(graphics);
        Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(this.routeLayoutId);
        if (layout.isEmpty()) {
            this.drawTitle(graphics, Component.translatable("screen.superpipeslide.layout.missing"), true, List.of(), mouseX, mouseY);
            SPSGui.centeredText(graphics, this.font, Component.translatable("screen.superpipeslide.layout.missing.body"), this.panel.x() + this.panel.width() / 2, this.panel.y() + 92, RouteEditorGui.INK_MUTED);
            this.renderTooltips(graphics, mouseX, mouseY);
            return;
        }
        Optional<RouteLine> line = ClientRouteDataCache.routeLine(layout.get().routeLineId());
        String lineName = line.map(RouteLine::displayName).orElse(Component.translatable("screen.superpipeslide.route").getString());
        SPSGui.Rect save = RouteEditorGui.titleActionBounds(this.panel, 0);
        SPSGui.Rect refresh = RouteEditorGui.titleActionBounds(this.panel, 1);
        SPSGui.Rect delete = RouteEditorGui.titleActionBounds(this.panel, 2);
        boolean saveActive = canSave() && isDirty(layout.get());
        String status = SPSGui.layoutStatus(layout.get());
        Component statusLabel = Component.translatable("screen.superpipeslide.status." + status.toLowerCase(java.util.Locale.ROOT));
        this.drawTitle(graphics, Component.translatable("screen.superpipeslide.layout.title", lineName, SPSGui.layoutName(layout.get())), true, List.of(
                new SPSGui.IconButton(SPSGui.Icon.REMOVE, RouteEditorGui.DANGER, false),
                SPSGui.IconButton.of(SPSGui.Icon.RECALCULATE),
                new SPSGui.IconButton(SPSGui.Icon.SAVE, RouteEditorGui.BLUE, !saveActive)
        ), mouseX, mouseY);
        this.drawDocumentHeader(graphics, SPSGui.Icon.LAYOUT, List.of(Component.literal(lineName), Component.literal(SPSGui.layoutName(layout.get()))), saveActive ? Component.translatable("screen.superpipeslide.editor.unsaved") : statusLabel, saveActive ? RouteEditorGui.SAVE : RouteEditorGui.statusColor(status), layout.get().id().hashCode());
        if (saveActive) {
            this.addClick(save, this::saveMetadata, Component.translatable("screen.superpipeslide.action.save_layout"));
        }
        this.addClick(refresh, this::recomputeSections, Component.translatable("screen.superpipeslide.action.recompute_sections"));
        this.addClick(delete, () -> this.requestDangerConfirmation(
                Component.translatable("screen.superpipeslide.confirm.delete_layout.title"),
                Component.translatable("screen.superpipeslide.confirm.delete_layout.body"),
                Component.translatable("screen.superpipeslide.action.delete_layout"),
                this::deleteLayout
        ), Component.translatable("screen.superpipeslide.action.delete_layout"));

        SPSGui.Rect content = this.editorContent();
        int top = this.documentBodyY();
        SPSGui.smallText(graphics, this.font, layoutType(layout.get()).getString(), content.x(), top + 2, RouteEditorGui.INK_SECONDARY, 0.72F);

        this.mapBounds = new SPSGui.Rect(content.x(), top + 16, content.width(), 96);
        this.relaxMapOverscroll();
        List<SPSGui.StationNode> nodes = SPSGui.nodesForLayout(layout.get());
        if (this.mapBounds.contains(mouseX, mouseY)) {
            this.lastMapInteractionMs = System.currentTimeMillis();
        }
        this.renderedMapScroll = displayMapScroll(nodes);
        RouteEditorGui.worksheetPane(graphics, this.mapBounds, this.mapBounds.contains(mouseX, mouseY), false);
        SPSGui.stationMap(graphics, this.font, layout.get(), line.map(RouteLine::themeColors).orElse(List.of()), new SPSGui.Rect(this.mapBounds.x() + 6, this.mapBounds.y() + 3, this.mapBounds.width() - 12, this.mapBounds.height() - 6), true, this.renderedMapScroll, true);
        this.renderMapIssueTooltip(graphics, layout.get(), nodes, mouseX, mouseY);

        int formY = top + 122;
        Component nameLabel = Component.translatable("screen.superpipeslide.field.name");
        Component namesLabel = Component.translatable("screen.superpipeslide.field.names");
        int slotW = (content.width() - 8) / 2;
        int x1 = content.x();
        int x2 = x1 + slotW + 6;
        RouteEditorGui.fieldLabel(graphics, this.font, nameLabel, x1, formY);
        RouteEditorGui.fieldLabel(graphics, this.font, namesLabel, x2, formY);
        this.nameBox.setX(x1);
        this.nameBox.setY(formY + 10);
        this.nameBox.setWidth(slotW);
        this.translatedBox.setX(x2);
        this.translatedBox.setY(formY + 10);
        this.translatedBox.setWidth(slotW);
        this.drawInputEditableIcon(graphics, this.nameBox);
        this.drawInputEditableIcon(graphics, this.translatedBox);

        SPSGui.Rect visibleBox = new SPSGui.Rect(x1, formY + 35, 12, 12);
        RouteEditorGui.checkbox(graphics, visibleBox, this.nameAsSectionName, visibleBox.contains(mouseX, mouseY));
        Component visibleLabel = Component.translatable("screen.superpipeslide.layout.name_as_section_name");
        SPSGui.text(graphics, this.font, visibleLabel, visibleBox.right() + 6, formY + 37, RouteEditorGui.INK_SECONDARY);
        this.addClick(new SPSGui.Rect(x1, formY + 33, 18 + this.font.width(visibleLabel), 16), () -> this.nameAsSectionName = !this.nameAsSectionName, visibleLabel);

        if (layout.get().orderedPlatformStops().size() >= 2) {
            int buttonY = formY + 33;
            SPSGui.Rect loopToggle = new SPSGui.Rect(content.right() - 46, buttonY, 16, 16);
            SPSGui.Rect directionToggle = new SPSGui.Rect(content.right() - 24, buttonY, 16, 16);
            Component loopTooltip = layout.get().loop() ? Component.translatable("screen.superpipeslide.layout.make_linear") : Component.translatable("screen.superpipeslide.layout.make_loop");
            RouteEditorGui.toolButton(graphics, loopToggle, SPSGui.Icon.LOOP, false, loopToggle.contains(mouseX, mouseY), layout.get().loop() ? RouteEditorGui.BLUE : RouteEditorGui.INK_SECONDARY);
            this.addClick(loopToggle, () -> toggleLoop(layout.get()), loopTooltip);
            boolean splitting = layout.get().bidirectional();
            Component directionTooltip = splitting ? Component.translatable("screen.superpipeslide.layout.split_bidirectional.tooltip") : Component.translatable("screen.superpipeslide.layout.make_bidirectional");
            RouteEditorGui.toolButton(graphics, directionToggle, splitting ? SPSGui.Icon.SPLIT : SPSGui.Icon.BIDIRECTIONAL, false, directionToggle.contains(mouseX, mouseY), splitting ? RouteEditorGui.DANGER : RouteEditorGui.SUCCESS);
            this.addClick(directionToggle, () -> {
                if (splitting) {
                    this.requestDangerConfirmation(
                            Component.translatable("screen.superpipeslide.confirm.split_layout.title"),
                            Component.translatable("screen.superpipeslide.confirm.split_layout.body"),
                            Component.translatable("screen.superpipeslide.layout.split_bidirectional"),
                            () -> toggleBidirectional(layout.get())
                    );
                } else {
                    toggleBidirectional(layout.get());
                }
            }, directionTooltip);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.renderTooltips(graphics, mouseX, mouseY);
    }

    private void renderMapIssueTooltip(GuiGraphicsExtractor graphics, RouteLayout layout, List<SPSGui.StationNode> nodes, int mouseX, int mouseY) {
        if (!this.mapBounds.contains(mouseX, mouseY)) {
            return;
        }
        int count = nodes.size();
        SPSGui.Rect inner = new SPSGui.Rect(this.mapBounds.x() + 6, this.mapBounds.y() + 3, this.mapBounds.width() - 12, this.mapBounds.height() - 6);
        int dot = 12;
        int spacing = SPSGui.stationMapSpacing(nodes, inner.width(), true, layout.loop());
        int firstX = SPSGui.stationMapFirstX(inner, nodes, true, layout.loop(), this.renderedMapScroll);
        int lineY = SPSGui.stationMapLineY(inner, true);
        for (int i = 0; i < count; i++) {
            int x = firstX + i * spacing + dot / 2;
            if (nodes.get(i).missing() && Math.abs(mouseX - x) <= 9 && Math.abs(mouseY - lineY) <= 10) {
                this.renderSmallTooltip(graphics, Component.translatable("screen.superpipeslide.station_order.missing_platform").getString(), mouseX, mouseY, RouteEditorGui.DANGER);
                return;
            }
        }
        if (count < 2) {
            return;
        }
        if (layout.loop() && !layout.orderedSectionRefs().isEmpty()) {
            Optional<RouteSection> loopSection = ClientRouteDataCache.routeSection(layout.orderedSectionRefs().getLast().routeSectionId());
            if (loopSection.isPresent() && loopSection.get().statusForLayout(layout) != RouteSectionStatus.VALID) {
                int width = 6;
                int firstCenterX = firstX + dot / 2;
                int leftX = firstCenterX - Math.max(12, width * 3);
                int breakY = (lineY + inner.bottom() - 2) / 2;
                if (Math.abs(mouseX - leftX) <= 10 && Math.abs(mouseY - breakY) <= 12) {
                    this.renderSectionIssueTooltip(graphics, layout, loopSection.get(), mouseX, mouseY);
                    return;
                }
            }
        }
        int sectionIndex = -1;
        for (int i = 0; i + 1 < count; i++) {
            if (nodes.get(i).missing() || nodes.get(i + 1).missing()) {
                continue;
            }
            int x1 = firstX + i * spacing + dot / 2;
            int x2 = firstX + (i + 1) * spacing + dot / 2;
            if (mouseX >= x1 && mouseX <= x2 && Math.abs(mouseY - lineY) <= 12) {
                sectionIndex = i;
                break;
            }
        }
        if (sectionIndex < 0 || sectionIndex >= layout.orderedSectionRefs().size()) {
            return;
        }
        Optional<RouteSection> section = ClientRouteDataCache.routeSection(layout.orderedSectionRefs().get(sectionIndex).routeSectionId());
        if (section.isEmpty() || section.get().statusForLayout(layout) == RouteSectionStatus.VALID) {
            return;
        }
        this.renderSectionIssueTooltip(graphics, layout, section.get(), mouseX, mouseY);
    }

    private void renderSectionIssueTooltip(GuiGraphicsExtractor graphics, RouteLayout layout, RouteSection section, int mouseX, int mouseY) {
        String line1;
        if (layout.bidirectional()) {
            String forward = directionText(section, 1).getString();
            String reverse = directionText(section, -1).getString();
            line1 = forward + " / " + reverse;
        } else {
            String status = statusLabel(section.forwardStatus());
            line1 = Component.translatable("screen.superpipeslide.layout.issue_status", Component.translatable("screen.superpipeslide.status." + status.toLowerCase(java.util.Locale.ROOT))).getString();
        }
        this.renderSmallTooltip(graphics, line1, mouseX, mouseY, RouteEditorGui.statusColor(statusLabel(section.statusForLayout(layout))));
    }

    private void renderSmallTooltip(GuiGraphicsExtractor graphics, String line, int mouseX, int mouseY, int color) {
        int w = this.font.width(line) + 12;
        int x = Math.min(mouseX + 8, this.panel.right() - w - 6);
        int y = Math.max(this.mapBounds.y() + 6, mouseY - 18);
        RouteEditorGui.paperSection(graphics, new SPSGui.Rect(x, y, w, 18), false, false);
        SPSGui.text(graphics, this.font, line, x + 6, y + 6, color);
    }

    private double displayMapScroll(List<SPSGui.StationNode> nodes) {
        long now = System.currentTimeMillis();
        if (this.lastMapInteractionMs == 0L || now - this.lastMapInteractionMs > 3200L) {
            boolean loop = ClientRouteDataCache.routeLayout(this.routeLayoutId).map(RouteLayout::loop).orElse(false);
            double auto = SPSGui.autoStationMapScroll(nodes, this.mapBounds.width() - 12, true, this.routeLayoutId.hashCode(), loop);
            this.mapScroll = auto;
            return auto;
        }
        return this.mapScroll;
    }

    private void saveMetadata() {
        Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(this.routeLayoutId);
        boolean bidirectional = layout.map(RouteLayout::bidirectional).orElse(false);
        boolean loop = layout.map(RouteLayout::loop).orElse(false);
        ClientPacketDistributor.sendToServer(new ServerboundRouteEditPayload(ServerboundRouteEditPayload.UPDATE_LAYOUT, ClientRouteDataCache.revision(), Optional.of(this.routeLayoutId), this.nameBox.getValue(), RouteLineCreateScreen.splitCsv(this.translatedBox.getValue()), List.of(), List.of(), bidirectional, loop, this.nameAsSectionName));
    }

    private boolean canSave() {
        return this.nameBox != null;
    }

    private boolean isDirty(RouteLayout layout) {
        String name = this.nameBox.getValue().trim();
        return !layout.displayName().orElse("").equals(name)
                || !layout.translatedNames().equals(RouteLineCreateScreen.splitCsv(this.translatedBox.getValue()))
                || layout.nameAsSectionName() != this.nameAsSectionName;
    }

    private void recomputeSections() {
        ClientRouteDataCache.routeLayout(this.routeLayoutId).ifPresent(layout -> ClientPacketDistributor.sendToServer(new ServerboundRouteEditPayload(ServerboundRouteEditPayload.SET_LAYOUT_STOPS, ClientRouteDataCache.revision(), Optional.of(this.routeLayoutId), "", List.of(), List.of(), layout.orderedPlatformStops(), layout.bidirectional(), layout.loop(), layout.nameAsSectionName())));
    }

    private void deleteLayout() {
        Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(this.routeLayoutId);
        ClientPacketDistributor.sendToServer(new ServerboundRouteEditPayload(ServerboundRouteEditPayload.DELETE_LAYOUT, ClientRouteDataCache.revision(), Optional.of(this.routeLayoutId), "", List.of(), List.of(), List.of(), false, false));
        layout.ifPresentOrElse(value -> this.minecraft.setScreen(new RouteLineScreen(value.routeLineId(), false)), () -> this.minecraft.setScreen(new RouteLineListScreen()));
    }

    private Component layoutType(RouteLayout layout) {
        if (layout.bidirectional() && layout.loop()) {
            return Component.translatable("screen.superpipeslide.layout.type_bidirectional_loop");
        }
        if (!layout.bidirectional() && layout.loop()) {
            return Component.translatable("screen.superpipeslide.layout.type_oneway_loop");
        }
        if (layout.bidirectional()) {
            return Component.translatable("screen.superpipeslide.layout.type_bidirectional_linear");
        }
        return Component.translatable("screen.superpipeslide.layout.type_oneway_linear");
    }

    private void toggleLoop(RouteLayout layout) {
        ClientPacketDistributor.sendToServer(new ServerboundRouteEditPayload(ServerboundRouteEditPayload.UPDATE_LAYOUT, ClientRouteDataCache.revision(), Optional.of(layout.id()), layout.displayName().orElse(""), layout.translatedNames(), List.of(), List.of(), layout.bidirectional(), !layout.loop(), layout.nameAsSectionName()));
    }

    private void toggleBidirectional(RouteLayout layout) {
        if (layout.bidirectional()) {
            String suffix = Component.translatable("screen.superpipeslide.layout.reverse_suffix").getString();
            ClientPacketDistributor.sendToServer(new ServerboundRouteEditPayload(ServerboundRouteEditPayload.SPLIT_LAYOUT, ClientRouteDataCache.revision(), Optional.of(layout.id()), suffix, List.of(), List.of(), List.of(), false, layout.loop(), layout.nameAsSectionName()));
        } else {
            ClientPacketDistributor.sendToServer(new ServerboundRouteEditPayload(ServerboundRouteEditPayload.UPDATE_LAYOUT, ClientRouteDataCache.revision(), Optional.of(layout.id()), layout.displayName().orElse(""), layout.translatedNames(), List.of(), List.of(), true, layout.loop(), layout.nameAsSectionName()));
        }
    }

    @Override
    protected void onBack() {
        Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(this.routeLayoutId);
        layout.ifPresentOrElse(value -> this.minecraft.setScreen(new RouteLineScreen(value.routeLineId(), false)), () -> this.minecraft.setScreen(new RouteLineListScreen()));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.confirmationOpen()) {
            return super.mouseClicked(event, doubleClick);
        }
        if (event.button() == 0 && this.mapBounds.contains(event.x(), event.y())) {
            this.draggingMap = true;
            this.lastMapInteractionMs = System.currentTimeMillis();
            this.dragStartX = event.x();
            this.dragStartScroll = this.mapScroll;
            this.mapDragged = false;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingMap) {
            this.lastMapInteractionMs = System.currentTimeMillis();
            this.mapDragged = this.mapDragged || Math.abs(event.x() - this.dragStartX) > 3.0D;
            this.mapScroll = clampMapScrollElastic(this.dragStartScroll - (event.x() - this.dragStartX), maxMapScroll());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.draggingMap) {
            this.draggingMap = false;
            if (!this.mapDragged && this.mapBounds.contains(event.x(), event.y())) {
                this.minecraft.setScreen(new RouteStationOrderScreen(this.routeLayoutId));
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.mapBounds.contains(mouseX, mouseY)) {
            this.lastMapInteractionMs = System.currentTimeMillis();
            double wheel = scrollY == 0.0D ? scrollX : scrollY;
            this.mapScroll = clampMapScrollElastic(this.mapScroll - wheel * 28.0D, maxMapScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private double maxMapScroll() {
        Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(this.routeLayoutId);
        return layout.map(SPSGui::nodesForLayout)
                .map(nodes -> SPSGui.maxStationMapScroll(nodes, this.mapBounds.width() - 12, true, layout.map(RouteLayout::loop).orElse(false)))
                .orElse(0.0D);
    }

    private void relaxMapOverscroll() {
        double max = maxMapScroll();
        if (this.draggingMap) {
            this.mapScroll = clampMapScrollElastic(this.mapScroll, max);
            return;
        }
        if (this.mapScroll < 0.0D) {
            this.mapScroll *= MAP_OVERSCROLL_RETURN;
            if (this.mapScroll > -0.35D) {
                this.mapScroll = 0.0D;
            }
        } else if (this.mapScroll > max) {
            this.mapScroll = max + (this.mapScroll - max) * MAP_OVERSCROLL_RETURN;
            if (this.mapScroll < max + 0.35D) {
                this.mapScroll = max;
            }
        }
        this.mapScroll = clampMapScrollElastic(this.mapScroll, max);
    }

    private static double clampMapScrollElastic(double value, double max) {
        return Math.max(-MAP_OVERSCROLL, Math.min(max + MAP_OVERSCROLL, value));
    }

    private static String statusLabel(RouteSectionStatus status) {
        return switch (status) {
            case BROKEN -> "Broken";
            case AMBIGUOUS -> "Ambiguous";
            case INCOMPLETE -> "Incomplete";
            case DISABLED -> "Disabled";
            case STALE -> "Stale";
            case VALID -> "Valid";
        };
    }

    private static Component directionText(RouteSection section, int direction) {
        RouteSectionStatus status = section.statusForDirection(direction);
        Component name = Component.translatable(direction > 0 ? "screen.superpipeslide.direction.forward" : "screen.superpipeslide.direction.reverse");
        if (status == RouteSectionStatus.VALID) {
            return Component.translatable("screen.superpipeslide.layout.direction_distance", name, String.format("%.1f", section.lengthForDirection(direction)));
        }
        if (status == RouteSectionStatus.DISABLED) {
            return Component.translatable("screen.superpipeslide.layout.direction_disabled", name);
        }
        return Component.translatable("screen.superpipeslide.layout.direction_broken", name);
    }
}
