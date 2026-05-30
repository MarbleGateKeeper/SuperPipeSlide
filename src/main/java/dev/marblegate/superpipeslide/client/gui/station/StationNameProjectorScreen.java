package dev.marblegate.superpipeslide.client.gui.station;

import dev.marblegate.superpipeslide.client.core.projection.preview.ProjectionLayoutPreviewPainter;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.gui.base.RouteDataAwareScreen;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.client.gui.route.RouteEditorGui;
import dev.marblegate.superpipeslide.client.gui.route.RouteEditorScreenBase;
import dev.marblegate.superpipeslide.common.block.station.StationNameProjectorConfig;
import dev.marblegate.superpipeslide.common.core.projection.layout.AppliedProjectionLayout;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.network.station.ServerboundStationNameProjectorSavePayload;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class StationNameProjectorScreen extends RouteEditorScreenBase implements RouteDataAwareScreen {
    private static final double STATION_SELECT_RADIUS = 64.0D;

    private final BlockPos pos;
    private StationNameProjectorConfig config;
    private final AppliedProjectionLayout appliedLayout;
    private EditBox exitBox;
    private SPSGui.Rect stationListArea = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect rightScrollArea = new SPSGui.Rect(0, 0, 0, 0);
    private double stationScroll;
    private double rightScroll;
    private int rightContentHeight;

    public StationNameProjectorScreen(BlockPos pos, StationNameProjectorConfig config, AppliedProjectionLayout appliedLayout) {
        super(Component.translatable("screen.superpipeslide.station_projector"));
        this.pos = pos;
        this.config = config;
        this.appliedLayout = appliedLayout;
    }

    @Override
    protected SPSGui.Rect createPanelRect() {
        return RouteEditorGui.panelRect(this.width, this.height, 520, 296);
    }

    @Override
    protected void rebuildWidgets() {
        String exit = this.exitBox == null ? this.config.exitLabel() : this.exitBox.getValue();
        this.clearWidgets();
        this.exitBox = this.borderlessBox(0, 0, 44, exit);
        this.exitBox.setMaxLength(4);
    }

    @Override
    public void refreshFromRouteSnapshot() {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.beginFrame();
        this.drawEditorFrame(graphics);

        SPSGui.Rect save = RouteEditorGui.titleActionBounds(this.panel, 0);
        this.drawTitle(graphics, Component.translatable("screen.superpipeslide.station_projector"), true, List.of(new SPSGui.IconButton(SPSGui.Icon.SAVE, RouteEditorGui.BLUE, false)), mouseX, mouseY);
        this.addClick(save, this::save, Component.translatable("screen.superpipeslide.action.save"));

        SPSGui.Rect content = this.editorContent();
        this.drawDocumentHeader(
                graphics,
                SPSGui.Icon.STATION_ORDER,
                List.of(Component.translatable("screen.superpipeslide.station_projector"), Component.translatable("screen.superpipeslide.station_projector.position", this.pos.toShortString())),
                Component.translatable("screen.superpipeslide.station_projector.device"),
                RouteEditorGui.BLUE,
                this.pos.asLong() == Long.MIN_VALUE ? 0 : Long.hashCode(this.pos.asLong()));

        int bodyTop = this.documentBodyY();
        int gap = 8;
        int leftWidth = Math.min(232, Math.max(196, (int) (content.width() * 0.44F)));
        SPSGui.Rect binding = new SPSGui.Rect(content.x(), bodyTop, leftWidth, content.bottom() - bodyTop);
        SPSGui.Rect right = new SPSGui.Rect(binding.right() + gap, bodyTop, content.right() - binding.right() - gap, content.bottom() - bodyTop);
        this.rightScrollArea = new SPSGui.Rect(right.x(), right.y(), Math.max(80, right.width() - 3), right.height());
        int positionHeight = 58;
        int deviceHeight = 62;
        int previewHeight = Math.max(78, right.height() - positionHeight - deviceHeight - gap * 2);
        this.rightContentHeight = previewHeight + positionHeight + deviceHeight + gap * 2;
        this.rightScroll = Math.max(0.0D, Math.min(maxRightScroll(), this.rightScroll));
        int scrollOffset = (int) Math.round(this.rightScroll);
        SPSGui.Rect preview = new SPSGui.Rect(this.rightScrollArea.x(), this.rightScrollArea.y() - scrollOffset, this.rightScrollArea.width(), previewHeight);
        SPSGui.Rect position = new SPSGui.Rect(this.rightScrollArea.x(), preview.bottom() + gap, this.rightScrollArea.width(), positionHeight);
        SPSGui.Rect device = new SPSGui.Rect(this.rightScrollArea.x(), position.bottom() + gap, this.rightScrollArea.width(), deviceHeight);

        drawBindingSection(graphics, binding, mouseX, mouseY);
        if (this.exitBox != null) {
            this.exitBox.visible = false;
            this.exitBox.active = false;
        }
        graphics.enableScissor(this.rightScrollArea.x(), this.rightScrollArea.y(), this.rightScrollArea.right(), this.rightScrollArea.bottom());
        if (intersects(preview, this.rightScrollArea)) {
            drawAppliedLayoutSection(graphics, preview, mouseX, mouseY);
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
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.station_projector.binding"), rect.x() + 8, rect.y() + 7, RouteEditorGui.INK_PRIMARY);
        int x = rect.x() + 8;
        int y = rect.y() + 23;
        int right = rect.right() - 8;
        int buttonWidth = (right - x - 5) / 2;
        drawSegmentButton(graphics, new SPSGui.Rect(x, y, buttonWidth, 17), Component.translatable("screen.superpipeslide.station_projector.binding.auto"), this.config.bindingMode() == StationNameProjectorConfig.BindingMode.AUTO, mouseX, mouseY, () -> this.config = this.config.withBinding(StationNameProjectorConfig.BindingMode.AUTO, nearestStation().map(StationGroup::id)));
        drawSegmentButton(graphics, new SPSGui.Rect(x + buttonWidth + 5, y, right - x - buttonWidth - 5, 17), Component.translatable("screen.superpipeslide.station_projector.binding.manual"), this.config.bindingMode() == StationNameProjectorConfig.BindingMode.MANUAL, mouseX, mouseY, () -> this.config = this.config.withBinding(StationNameProjectorConfig.BindingMode.MANUAL, this.config.stationGroupId()));

        this.stationListArea = new SPSGui.Rect(x, y + 24, right - x, rect.bottom() - y - 32);
        drawStationList(graphics, this.stationListArea, mouseX, mouseY);
    }

    private void drawAppliedLayoutSection(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        RouteEditorGui.worksheetPane(graphics, rect, rect.contains(mouseX, mouseY), false);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.station_projector.applied_layout"), rect.x() + 8, rect.y() + 7, RouteEditorGui.INK_PRIMARY);
        SPSGui.Rect preview = new SPSGui.Rect(rect.x() + 8, rect.y() + 24, rect.width() - 16, Math.max(42, rect.height() - 53));
        ProjectionLayoutPreviewPainter.drawApplied(graphics, this.font, this.appliedLayout, preview);
        String name = this.appliedLayout == null ? "" : this.appliedLayout.sourceLayoutName();
        RouteEditorGui.stamp(graphics, this.font, SPSGui.ellipsize(this.font, name, rect.width() - 72), rect.x() + 8, rect.bottom() - 20, RouteEditorGui.BLUE);
        SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.station_projector.apply_hint").getString(), rect.x() + 8, rect.bottom() - 8, RouteEditorGui.INK_MUTED, 0.62F);
    }

    private void drawPositionSection(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        RouteEditorGui.paperSection(graphics, rect, rect.contains(mouseX, mouseY), false);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.station_projector.offset"), rect.x() + 8, rect.y() + 7, RouteEditorGui.INK_PRIMARY);
        int x = rect.x() + 8;
        int right = rect.right() - 8;
        drawAxisControl(graphics, "X", this.config.offsetX(), StationNameProjectorConfig.MIN_OFFSET_X, StationNameProjectorConfig.MAX_OFFSET_X, x, rect.y() + 23, right, mouseX, mouseY, true);
        drawAxisControl(graphics, "Y", this.config.offsetY(), StationNameProjectorConfig.MIN_OFFSET_Y, StationNameProjectorConfig.MAX_OFFSET_Y, x, rect.y() + 40, right, mouseX, mouseY, false);
    }

    private void drawDeviceSection(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        RouteEditorGui.paperSection(graphics, rect, rect.contains(mouseX, mouseY), false);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.station_projector.device"), rect.x() + 8, rect.y() + 7, RouteEditorGui.INK_PRIMARY);
        int x = rect.x() + 8;
        int y = rect.y() + 22;
        int columnWidth = Math.max(62, (rect.width() - 22) / 2);
        drawCheckboxOption(graphics, new SPSGui.Rect(x, y, columnWidth, 14), Component.translatable("screen.superpipeslide.station_projector.backside"), this.config.backsideProjection(), mouseX, mouseY, () -> this.config = this.config.withBacksideProjection(!this.config.backsideProjection()));
        drawCheckboxOption(graphics, new SPSGui.Rect(x + columnWidth + 6, y, rect.right() - x - columnWidth - 14, 14), Component.translatable("screen.superpipeslide.station_projector.exit.toggle"), this.config.showExit(), mouseX, mouseY, () -> this.config = this.config.withShowExit(!this.config.showExit()));
        if (this.exitBox != null && this.config.showExit()) {
            Component label = Component.translatable("screen.superpipeslide.station_projector.exit.label");
            int labelX = x;
            RouteEditorGui.fieldLabel(graphics, this.font, label, labelX, y + 18);
            this.exitBox.setX(labelX + 34);
            this.exitBox.setY(y + 15);
            this.exitBox.setWidth(Math.min(52, Math.max(34, rect.right() - this.exitBox.getX() - 8)));
            SPSGui.Rect boxBounds = new SPSGui.Rect(this.exitBox.getX(), this.exitBox.getY(), this.exitBox.getWidth(), 14);
            boolean visible = contains(this.rightScrollArea, boxBounds);
            this.exitBox.visible = visible;
            this.exitBox.active = visible;
            if (visible) {
                this.drawInputEditableIcon(graphics, this.exitBox);
            }
        }
    }

    private void drawStationList(GuiGraphicsExtractor graphics, SPSGui.Rect area, int mouseX, int mouseY) {
        graphics.fill(area.x(), area.y(), area.right(), area.bottom(), RouteEditorGui.PAPER_RECESSED);
        graphics.outline(area.x(), area.y(), area.width(), area.height(), RouteEditorGui.PAPER_LINE);
        List<StationGroup> stations = nearbyStations();
        if (stations.isEmpty()) {
            SPSGui.centeredText(graphics, this.font, Component.translatable("screen.superpipeslide.station_projector.no_station"), area.x() + area.width() / 2, area.y() + 20, RouteEditorGui.INK_MUTED);
            return;
        }
        int y = area.y() + 5 - (int) this.stationScroll;
        graphics.enableScissor(area.x() + 1, area.y() + 1, area.right() - 1, area.bottom() - 1);
        for (StationGroup station : stations) {
            SPSGui.Rect card = new SPSGui.Rect(area.x() + 5, y, area.width() - 10, 20);
            if (card.bottom() >= area.y() && card.y() <= area.bottom()) {
                boolean selected = this.config.stationGroupId().filter(station.id()::equals).isPresent();
                boolean hovered = card.contains(mouseX, mouseY);
                RouteEditorGui.paperSection(graphics, card, hovered, selected);
                int nameWidth = card.width() - 44;
                SPSGui.text(graphics, this.font, SPSGui.scrollingText(this.font, station.primaryName(), nameWidth, station.id().hashCode()), card.x() + 6, card.y() + 6, selected ? RouteEditorGui.BLUE : RouteEditorGui.INK_PRIMARY);
                String distance = Math.round(Math.sqrt(station.stationBlockPos().distSqr(this.pos))) + "m";
                RouteEditorGui.stamp(graphics, this.font, distance, card.right() - this.font.width(distance) - 11, card.y() + 4, RouteEditorGui.INK_MUTED);
                this.addClick(card, () -> this.config = this.config.withBinding(StationNameProjectorConfig.BindingMode.MANUAL, Optional.of(station.id())), Component.translatable("screen.superpipeslide.station_projector.select_station"));
            }
            y += 24;
        }
        graphics.disableScissor();
        RouteEditorGui.scrollEdges(graphics, area, this.stationScroll > 0.5D, this.stationScroll < maxStationScroll() - 0.5D, false);
        RouteEditorGui.thinScrollbar(graphics, area, this.stationScroll, maxStationScroll(), area.contains(mouseX, mouseY));
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

    private String exitValue() {
        return this.exitBox == null ? this.config.exitLabel() : this.exitBox.getValue().trim();
    }

    private Optional<StationGroup> nearestStation() {
        return nearbyStations().stream().findFirst();
    }

    private List<StationGroup> nearbyStations() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return List.of();
        }
        double radiusSqr = STATION_SELECT_RADIUS * STATION_SELECT_RADIUS;
        return ClientRouteDataCache.stationGroups().stream()
                .filter(station -> station.levelKey().equals(this.minecraft.level.dimension()))
                .filter(station -> station.stationBlockPos().distSqr(this.pos) <= radiusSqr)
                .sorted(Comparator.comparingDouble(station -> station.stationBlockPos().distSqr(this.pos)))
                .toList();
    }

    private void save() {
        StationNameProjectorConfig saved = this.config.withExitLabel(exitValue());
        ClientPacketDistributor.sendToServer(new ServerboundStationNameProjectorSavePayload(this.pos, saved));
        this.onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.stationListArea.contains(mouseX, mouseY)) {
            this.stationScroll = Math.max(0.0D, Math.min(maxStationScroll(), this.stationScroll - scrollY * 12.0D));
            return true;
        }
        if (this.rightScrollArea.contains(mouseX, mouseY)) {
            this.rightScroll = Math.max(0.0D, Math.min(maxRightScroll(), this.rightScroll - scrollY * 18.0D));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private double maxStationScroll() {
        int contentHeight = nearbyStations().size() * 24 + 10;
        return Math.max(0, contentHeight - Math.max(0, this.stationListArea.height()));
    }

    private double maxRightScroll() {
        return Math.max(0, this.rightContentHeight - Math.max(0, this.rightScrollArea.height()));
    }

    private static boolean intersects(SPSGui.Rect a, SPSGui.Rect b) {
        return a.right() > b.x() && a.x() < b.right() && a.bottom() > b.y() && a.y() < b.bottom();
    }

    private static boolean contains(SPSGui.Rect outer, SPSGui.Rect inner) {
        return inner.x() >= outer.x() && inner.right() <= outer.right() && inner.y() >= outer.y() && inner.bottom() <= outer.bottom();
    }
}
