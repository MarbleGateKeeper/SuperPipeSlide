package dev.marblegate.superpipeslide.client.gui.platform;


import dev.marblegate.superpipeslide.client.gui.base.RouteDataAwareScreen;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.client.gui.route.RouteEditorGui;
import dev.marblegate.superpipeslide.client.gui.route.RouteEditorScreenBase;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.core.projection.preview.ProjectionLayoutPreviewPainter;
import dev.marblegate.superpipeslide.common.block.station.PlatformProjectorConfig;
import dev.marblegate.superpipeslide.common.core.projection.layout.AppliedProjectionLayout;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.network.platform.ServerboundPlatformProjectorSavePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PlatformProjectorScreen extends RouteEditorScreenBase implements RouteDataAwareScreen {
    private static final double PLATFORM_SELECT_RADIUS = 96.0D;

    private final BlockPos pos;
    private PlatformProjectorConfig config;
    private final AppliedProjectionLayout appliedLayout;
    private SPSGui.Rect platformListArea = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect rightScrollArea = new SPSGui.Rect(0, 0, 0, 0);
    private double platformScroll;
    private double rightScroll;
    private int rightContentHeight;

    public PlatformProjectorScreen(BlockPos pos, PlatformProjectorConfig config, AppliedProjectionLayout appliedLayout) {
        super(Component.translatable("screen.superpipeslide.platform_projector"));
        this.pos = pos;
        this.config = config;
        this.appliedLayout = appliedLayout;
    }

    @Override
    protected SPSGui.Rect createPanelRect() {
        return RouteEditorGui.panelRect(this.width, this.height, 548, 310);
    }

    @Override
    public void refreshFromRouteSnapshot() {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.beginFrame();
        this.drawEditorFrame(graphics);

        SPSGui.Rect save = RouteEditorGui.titleActionBounds(this.panel, 0);
        this.drawTitle(graphics, Component.translatable("screen.superpipeslide.platform_projector"), true, List.of(new SPSGui.IconButton(SPSGui.Icon.SAVE, RouteEditorGui.BLUE, false)), mouseX, mouseY);
        this.addClick(save, this::save, Component.translatable("screen.superpipeslide.action.save"));

        SPSGui.Rect content = this.editorContent();
        this.drawDocumentHeader(
                graphics,
                SPSGui.Icon.STATION_ORDER,
                List.of(Component.translatable("screen.superpipeslide.platform_projector"), Component.translatable("screen.superpipeslide.station_projector.position", this.pos.toShortString())),
                Component.translatable("screen.superpipeslide.platform_projector.device"),
                RouteEditorGui.BLUE,
                this.pos.asLong() == Long.MIN_VALUE ? 0 : Long.hashCode(this.pos.asLong())
        );

        int bodyTop = this.documentBodyY();
        int gap = 8;
        int leftWidth = Math.min(238, Math.max(204, (int) (content.width() * 0.44F)));
        SPSGui.Rect binding = new SPSGui.Rect(content.x(), bodyTop, leftWidth, content.bottom() - bodyTop);
        SPSGui.Rect right = new SPSGui.Rect(binding.right() + gap, bodyTop, content.right() - binding.right() - gap, content.bottom() - bodyTop);
        this.rightScrollArea = new SPSGui.Rect(right.x(), right.y(), Math.max(80, right.width() - 3), right.height());

        List<RouteLayout> layouts = selectedLayouts();
        int previewHeight = 74;
        int routeHeight = Math.max(44, 32 + layouts.size() * 22);
        int directionHeight = 48;
        int positionHeight = 58;
        int deviceHeight = 36;
        this.rightContentHeight = previewHeight + routeHeight + directionHeight + positionHeight + deviceHeight + gap * 4;
        this.rightScroll = Math.max(0.0D, Math.min(maxRightScroll(), this.rightScroll));
        int scrollOffset = (int) Math.round(this.rightScroll);
        SPSGui.Rect preview = new SPSGui.Rect(this.rightScrollArea.x(), this.rightScrollArea.y() - scrollOffset, this.rightScrollArea.width(), previewHeight);
        SPSGui.Rect route = new SPSGui.Rect(this.rightScrollArea.x(), preview.bottom() + gap, this.rightScrollArea.width(), routeHeight);
        SPSGui.Rect direction = new SPSGui.Rect(this.rightScrollArea.x(), route.bottom() + gap, this.rightScrollArea.width(), directionHeight);
        SPSGui.Rect position = new SPSGui.Rect(this.rightScrollArea.x(), direction.bottom() + gap, this.rightScrollArea.width(), positionHeight);
        SPSGui.Rect device = new SPSGui.Rect(this.rightScrollArea.x(), position.bottom() + gap, this.rightScrollArea.width(), deviceHeight);

        drawBindingSection(graphics, binding, mouseX, mouseY);
        graphics.enableScissor(this.rightScrollArea.x(), this.rightScrollArea.y(), this.rightScrollArea.right(), this.rightScrollArea.bottom());
        if (intersects(preview, this.rightScrollArea)) {
            drawAppliedLayoutSection(graphics, preview, mouseX, mouseY);
        }
        if (intersects(route, this.rightScrollArea)) {
            drawRouteLayoutSection(graphics, route, layouts, mouseX, mouseY);
        }
        if (intersects(direction, this.rightScrollArea)) {
            drawDirectionSection(graphics, direction, mouseX, mouseY);
        }
        if (intersects(position, this.rightScrollArea)) {
            drawPositionSection(graphics, position, mouseX, mouseY);
        }
        if (intersects(device, this.rightScrollArea)) {
            drawDeviceSection(graphics, device, mouseX, mouseY);
        }
        graphics.disableScissor();
        RouteEditorGui.scrollEdges(graphics, this.rightScrollArea, this.rightScroll > 0.5D, this.rightScroll < maxRightScroll() - 0.5D, false);
        RouteEditorGui.thinScrollbar(graphics, this.rightScrollArea, this.rightScroll, maxRightScroll(), this.rightScrollArea.contains(mouseX, mouseY));

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.renderTooltips(graphics, mouseX, mouseY);
    }

    private void drawBindingSection(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        RouteEditorGui.paperSection(graphics, rect, rect.contains(mouseX, mouseY), false);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.platform_projector.binding"), rect.x() + 8, rect.y() + 7, RouteEditorGui.INK_PRIMARY);
        int x = rect.x() + 8;
        int y = rect.y() + 23;
        int right = rect.right() - 8;
        int buttonWidth = (right - x - 5) / 2;
        drawSegmentButton(graphics, new SPSGui.Rect(x, y, buttonWidth, 17), Component.translatable("screen.superpipeslide.station_projector.binding.auto"), this.config.bindingMode() == PlatformProjectorConfig.BindingMode.AUTO, mouseX, mouseY, () -> nearestPlatform().ifPresentOrElse(this::selectAutoPlatform, () -> this.config = this.config.withBinding(PlatformProjectorConfig.BindingMode.AUTO, Optional.empty(), Optional.empty())));
        drawSegmentButton(graphics, new SPSGui.Rect(x + buttonWidth + 5, y, right - x - buttonWidth - 5, 17), Component.translatable("screen.superpipeslide.station_projector.binding.manual"), this.config.bindingMode() == PlatformProjectorConfig.BindingMode.MANUAL, mouseX, mouseY, () -> this.config = this.config.withBinding(PlatformProjectorConfig.BindingMode.MANUAL, this.config.platformStopId(), this.config.routeLayoutId()));

        this.platformListArea = new SPSGui.Rect(x, y + 24, right - x, rect.bottom() - y - 32);
        drawPlatformList(graphics, this.platformListArea, mouseX, mouseY);
    }

    private void drawAppliedLayoutSection(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        RouteEditorGui.worksheetPane(graphics, rect, rect.contains(mouseX, mouseY), false);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.station_projector.applied_layout"), rect.x() + 8, rect.y() + 7, RouteEditorGui.INK_PRIMARY);
        SPSGui.Rect preview = new SPSGui.Rect(rect.x() + 8, rect.y() + 23, rect.width() - 16, Math.max(34, rect.height() - 47));
        ProjectionLayoutPreviewPainter.drawApplied(graphics, this.font, this.appliedLayout, preview);
        String name = this.appliedLayout == null ? "" : this.appliedLayout.sourceLayoutName();
        RouteEditorGui.stamp(graphics, this.font, SPSGui.ellipsize(this.font, name, rect.width() - 16), rect.x() + 8, rect.bottom() - 15, RouteEditorGui.BLUE);
    }

    private void drawRouteLayoutSection(GuiGraphicsExtractor graphics, SPSGui.Rect rect, List<RouteLayout> layouts, int mouseX, int mouseY) {
        RouteEditorGui.paperSection(graphics, rect, rect.contains(mouseX, mouseY), false);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.platform_projector.route_layout"), rect.x() + 8, rect.y() + 7, RouteEditorGui.INK_PRIMARY);
        if (layouts.isEmpty()) {
            SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.platform_projector.no_layout").getString(), rect.x() + 8, rect.y() + 25, RouteEditorGui.INK_MUTED, 0.70F);
            return;
        }
        int y = rect.y() + 24;
        for (RouteLayout layout : layouts) {
            SPSGui.Rect row = new SPSGui.Rect(rect.x() + 8, y, rect.width() - 16, 18);
            boolean selected = this.config.routeLayoutId().filter(layout.id()::equals).isPresent();
            drawSegmentButton(graphics, row, Component.literal(layoutLabel(layout)), selected, mouseX, mouseY, () -> this.config = this.config.withBinding(this.config.bindingMode(), this.config.platformStopId(), Optional.of(layout.id())));
            y += 22;
        }
    }

    private void drawDirectionSection(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        RouteEditorGui.paperSection(graphics, rect, rect.contains(mouseX, mouseY), false);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.platform_projector.direction"), rect.x() + 8, rect.y() + 7, RouteEditorGui.INK_PRIMARY);
        PlatformProjectorConfig.PlatformProjectionDirection[] values = PlatformProjectorConfig.PlatformProjectionDirection.values();
        int x = rect.x() + 8;
        int y = rect.y() + 24;
        int gap = 4;
        int w = Math.max(42, (rect.width() - 16 - gap * (values.length - 1)) / values.length);
        for (int i = 0; i < values.length; i++) {
            PlatformProjectorConfig.PlatformProjectionDirection value = values[i];
            SPSGui.Rect button = new SPSGui.Rect(x + i * (w + gap), y, i == values.length - 1 ? rect.right() - 8 - (x + i * (w + gap)) : w, 16);
            drawSegmentButton(graphics, button, Component.translatable("screen.superpipeslide.platform_projector.direction." + value.name().toLowerCase(java.util.Locale.ROOT)), this.config.direction() == value, mouseX, mouseY, () -> this.config = this.config.withDirection(value));
        }
    }

    private void drawPositionSection(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        RouteEditorGui.paperSection(graphics, rect, rect.contains(mouseX, mouseY), false);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.station_projector.offset"), rect.x() + 8, rect.y() + 7, RouteEditorGui.INK_PRIMARY);
        int x = rect.x() + 8;
        int right = rect.right() - 8;
        drawAxisControl(graphics, "X", this.config.offsetX(), PlatformProjectorConfig.MIN_OFFSET_X, PlatformProjectorConfig.MAX_OFFSET_X, x, rect.y() + 23, right, mouseX, mouseY, true);
        drawAxisControl(graphics, "Y", this.config.offsetY(), PlatformProjectorConfig.MIN_OFFSET_Y, PlatformProjectorConfig.MAX_OFFSET_Y, x, rect.y() + 40, right, mouseX, mouseY, false);
    }

    private void drawDeviceSection(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        RouteEditorGui.paperSection(graphics, rect, rect.contains(mouseX, mouseY), false);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.station_projector.device"), rect.x() + 8, rect.y() + 7, RouteEditorGui.INK_PRIMARY);
        drawCheckboxOption(graphics, new SPSGui.Rect(rect.x() + 8, rect.y() + 22, rect.width() - 16, 14), Component.translatable("screen.superpipeslide.station_projector.backside"), this.config.backsideProjection(), mouseX, mouseY, () -> this.config = this.config.withBacksideProjection(!this.config.backsideProjection()));
    }

    private void drawPlatformList(GuiGraphicsExtractor graphics, SPSGui.Rect area, int mouseX, int mouseY) {
        graphics.fill(area.x(), area.y(), area.right(), area.bottom(), RouteEditorGui.PAPER_RECESSED);
        graphics.outline(area.x(), area.y(), area.width(), area.height(), RouteEditorGui.PAPER_LINE);
        List<PlatformStop> platforms = nearbyPlatforms();
        if (platforms.isEmpty()) {
            SPSGui.centeredText(graphics, this.font, Component.translatable("screen.superpipeslide.platform_projector.no_platform"), area.x() + area.width() / 2, area.y() + 20, RouteEditorGui.INK_MUTED);
            return;
        }
        int y = area.y() + 5 - (int) this.platformScroll;
        graphics.enableScissor(area.x() + 1, area.y() + 1, area.right() - 1, area.bottom() - 1);
        for (PlatformStop platform : platforms) {
            SPSGui.Rect card = new SPSGui.Rect(area.x() + 5, y, area.width() - 10, 26);
            if (card.bottom() >= area.y() && card.y() <= area.bottom()) {
                boolean selected = this.config.platformStopId().filter(platform.id()::equals).isPresent();
                boolean hovered = card.contains(mouseX, mouseY);
                RouteEditorGui.paperSection(graphics, card, hovered, selected);
                SPSGui.text(graphics, this.font, SPSGui.scrollingText(this.font, platformLabel(platform), card.width() - 12, platform.id().hashCode()), card.x() + 6, card.y() + 6, selected ? RouteEditorGui.BLUE : RouteEditorGui.INK_PRIMARY);
                SPSGui.smallText(graphics, this.font, Component.literal(layoutHint(platform)).getString(), card.x() + 6, card.y() + 17, RouteEditorGui.INK_MUTED, 0.62F);
                this.addClick(card, () -> selectManualPlatform(platform), Component.translatable("screen.superpipeslide.platform_projector.select_platform"));
            }
            y += 30;
        }
        graphics.disableScissor();
        RouteEditorGui.scrollEdges(graphics, area, this.platformScroll > 0.5D, this.platformScroll < maxPlatformScroll() - 0.5D, false);
        RouteEditorGui.thinScrollbar(graphics, area, this.platformScroll, maxPlatformScroll(), area.contains(mouseX, mouseY));
    }

    private void drawAxisControl(GuiGraphicsExtractor graphics, String axis, float value, float min, float max, int x, int y, int right, int mouseX, int mouseY, boolean xAxis) {
        SPSGui.text(graphics, this.font, axis, x, y + 3, RouteEditorGui.INK_SECONDARY);
        int buttonW = 23;
        int smallButtonW = 30;
        int gap = 3;
        int bx = x + 13;
        drawStepper(graphics, new SPSGui.Rect(bx, y, buttonW, 14), "-1", mouseX, mouseY, () -> nudgeAxis(xAxis, -1.0F));
        drawStepper(graphics, new SPSGui.Rect(bx + buttonW + gap, y, smallButtonW, 14), "-.25", mouseX, mouseY, () -> nudgeAxis(xAxis, -0.25F));
        int plusLargeX = right - buttonW;
        int plusSmallX = plusLargeX - gap - smallButtonW;
        drawStepper(graphics, new SPSGui.Rect(plusSmallX, y, smallButtonW, 14), "+.25", mouseX, mouseY, () -> nudgeAxis(xAxis, 0.25F));
        drawStepper(graphics, new SPSGui.Rect(plusLargeX, y, buttonW, 14), "+1", mouseX, mouseY, () -> nudgeAxis(xAxis, 1.0F));

        int meterX = bx + buttonW + gap + smallButtonW + 8;
        int meterW = Math.max(24, plusSmallX - meterX - 8);
        graphics.fill(meterX, y + 5, meterX + meterW, y + 9, 0x66FFFFFF);
        graphics.outline(meterX, y + 5, meterW, 4, RouteEditorGui.PAPER_LINE);
        int thumbX = meterX + Math.round((value - min) / Math.max(0.01F, max - min) * Math.max(1, meterW - 4));
        graphics.fill(thumbX, y + 2, thumbX + 4, y + 12, RouteEditorGui.BLUE);
        String label = String.format(java.util.Locale.ROOT, "%.2f", value);
        SPSGui.smallText(graphics, this.font, label, meterX + Math.max(0, (meterW - Math.round(this.font.width(label) * 0.66F)) / 2), y - 5, RouteEditorGui.INK_MUTED, 0.66F);
    }

    private void drawSegmentButton(GuiGraphicsExtractor graphics, SPSGui.Rect rect, Component label, boolean selected, int mouseX, int mouseY, Runnable action) {
        boolean hovered = rect.contains(mouseX, mouseY);
        int bg = selected ? 0xFFE9F2F6 : hovered ? 0xFFFFFBF1 : RouteEditorGui.PAPER_ELEVATED;
        int border = selected ? RouteEditorGui.BLUE : hovered ? 0xFFB8C9D4 : RouteEditorGui.PAPER_LINE;
        int color = selected ? RouteEditorGui.BLUE : RouteEditorGui.INK_SECONDARY;
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), bg);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), border);
        SPSGui.centeredText(graphics, this.font, SPSGui.ellipsize(this.font, label.getString(), rect.width() - 6), rect.x() + rect.width() / 2, rect.y() + (rect.height() - 8) / 2, color);
        this.addClick(rect, action, label);
    }

    private void drawCheckboxOption(GuiGraphicsExtractor graphics, SPSGui.Rect rect, Component label, boolean selected, int mouseX, int mouseY, Runnable action) {
        boolean hovered = rect.contains(mouseX, mouseY);
        SPSGui.Rect box = new SPSGui.Rect(rect.x(), rect.y() + 2, 11, 11);
        RouteEditorGui.checkbox(graphics, box, selected, hovered);
        SPSGui.text(graphics, this.font, SPSGui.ellipsize(this.font, label.getString(), rect.width() - 16), rect.x() + 15, rect.y() + 4, selected ? RouteEditorGui.INK_PRIMARY : RouteEditorGui.INK_SECONDARY);
        if (intersects(rect, this.rightScrollArea)) {
            this.addClick(rect, action, label);
        }
    }

    private void drawStepper(GuiGraphicsExtractor graphics, SPSGui.Rect rect, String label, int mouseX, int mouseY, Runnable action) {
        boolean hovered = rect.contains(mouseX, mouseY);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), hovered ? RouteEditorGui.BLUE_LIGHT : RouteEditorGui.PAPER_ELEVATED);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), hovered ? RouteEditorGui.BLUE : RouteEditorGui.PAPER_LINE);
        SPSGui.centeredText(graphics, this.font, label, rect.x() + rect.width() / 2, rect.y() + 3, hovered ? RouteEditorGui.BLUE : RouteEditorGui.INK_SECONDARY);
        if (intersects(rect, this.rightScrollArea)) {
            this.addClick(rect, action, Component.literal(label));
        }
    }

    private void nudgeAxis(boolean xAxis, float delta) {
        if (xAxis) {
            this.config = this.config.withOffset(this.config.offsetX() + delta, this.config.offsetY());
        } else {
            this.config = this.config.withOffset(this.config.offsetX(), this.config.offsetY() + delta);
        }
    }

    private void selectAutoPlatform(PlatformStop platform) {
        this.config = this.config.withBinding(PlatformProjectorConfig.BindingMode.AUTO, Optional.of(platform.id()), firstLayoutId(platform));
    }

    private void selectManualPlatform(PlatformStop platform) {
        this.config = this.config.withBinding(PlatformProjectorConfig.BindingMode.MANUAL, Optional.of(platform.id()), firstLayoutId(platform));
    }

    private Optional<UUID> firstLayoutId(PlatformStop platform) {
        List<UUID> layoutIds = ClientRouteDataCache.routeLayoutIdsForPlatformStop(platform.id());
        return this.config.routeLayoutId().filter(layoutIds::contains).or(() -> layoutIds.stream().findFirst());
    }

    private Optional<PlatformStop> nearestPlatform() {
        return nearbyPlatforms().stream().findFirst();
    }

    private List<PlatformStop> nearbyPlatforms() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return List.of();
        }
        double radiusSqr = PLATFORM_SELECT_RADIUS * PLATFORM_SELECT_RADIUS;
        return ClientRouteDataCache.platformStops().stream()
                .filter(stop -> stop.connectionRef().levelKey().equals(this.minecraft.level.dimension()))
                .filter(stop -> station(stop).map(station -> station.stationBlockPos().distSqr(this.pos) <= radiusSqr).orElse(false))
                .sorted(Comparator.comparingDouble(stop -> station(stop).map(station -> station.stationBlockPos().distSqr(this.pos)).orElse(Double.MAX_VALUE)))
                .toList();
    }

    private List<RouteLayout> selectedLayouts() {
        return this.config.platformStopId()
                .map(ClientRouteDataCache::routeLayoutIdsForPlatformStop)
                .orElse(List.of())
                .stream()
                .map(id -> ClientRouteDataCache.routeLayout(id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(this::layoutLabel))
                .toList();
    }

    private Optional<StationGroup> station(PlatformStop stop) {
        return ClientRouteDataCache.stationGroup(stop.stationGroupId());
    }

    private String platformLabel(PlatformStop platform) {
        String stationName = station(platform).map(StationGroup::primaryName).orElse("?");
        String platformName = platform.displayName().filter(name -> !name.isBlank()).orElse(Component.translatable("screen.superpipeslide.platform_projector.platform_number", platform.platformNumber()).getString());
        return stationName + " / " + platformName;
    }

    private String layoutHint(PlatformStop platform) {
        int count = ClientRouteDataCache.routeLayoutIdsForPlatformStop(platform.id()).size();
        return Component.translatable("screen.superpipeslide.platform_projector.layout_count", count).getString();
    }

    private String layoutLabel(RouteLayout layout) {
        String line = ClientRouteDataCache.routeLine(layout.routeLineId()).map(RouteLine::displayName).orElse("?");
        return layout.displayName().filter(name -> !name.isBlank()).map(name -> line + " / " + name).orElse(line);
    }

    private void save() {
        ClientPacketDistributor.sendToServer(new ServerboundPlatformProjectorSavePayload(this.pos, this.config));
        this.onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.platformListArea.contains(mouseX, mouseY)) {
            this.platformScroll = Math.max(0.0D, Math.min(maxPlatformScroll(), this.platformScroll - scrollY * 12.0D));
            return true;
        }
        if (this.rightScrollArea.contains(mouseX, mouseY)) {
            this.rightScroll = Math.max(0.0D, Math.min(maxRightScroll(), this.rightScroll - scrollY * 18.0D));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private double maxPlatformScroll() {
        int contentHeight = nearbyPlatforms().size() * 30 + 10;
        return Math.max(0, contentHeight - Math.max(0, this.platformListArea.height()));
    }

    private double maxRightScroll() {
        return Math.max(0, this.rightContentHeight - Math.max(0, this.rightScrollArea.height()));
    }

    private static boolean intersects(SPSGui.Rect a, SPSGui.Rect b) {
        return a.right() > b.x() && a.x() < b.right() && a.bottom() > b.y() && a.y() < b.bottom();
    }
}
