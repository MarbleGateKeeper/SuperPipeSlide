package dev.marblegate.superpipeslide.client.gui.navigation;

import dev.marblegate.superpipeslide.client.core.navigation.ClientNavigationController;
import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.render.SmoothGuiPrimitives;
import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapTheme;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.client.gui.base.SPSScreen;
import dev.marblegate.superpipeslide.client.gui.navigation.DestinationNavigationViewModel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class DestinationNavigationScreen extends SPSScreen {
    private static final int RESULT_LIMIT = 48;
    private static final int FRAME = FullMapTheme.SURFACE_CARD;
    private static final int SCREEN = FullMapTheme.PRACTICAL_BACKGROUND;
    private static final int SCREEN_RAISED = FullMapTheme.SURFACE_CONTROL;
    private static final int LINE = FullMapTheme.BORDER_SELECTED;
    private static final int LINE_DIM = FullMapTheme.BORDER;
    private static final int TEXT = FullMapTheme.TEXT_PRIMARY;
    private static final int TEXT_MUTED = FullMapTheme.TEXT_SECONDARY;
    private static final int TEXT_DIM = FullMapTheme.TEXT_MUTED;
    private static final int GREEN = SPSGui.SUCCESS;
    private static final int BLUE = SPSGui.INFO;
    private static final int AMBER = FullMapTheme.FOCUS_RING;
    private static final int RED = SPSGui.DANGER;
    private static final int RESULT_ROW_HEIGHT = 18;
    private static final int RESULT_ROW_GAP = 1;
    private static final int SIMPLE_STEP_HEIGHT = 21;
    private static final int RIDE_STATION_ROW_HEIGHT = 10;
    private static final int ITINERARY_STEP_GAP = 1;

    private EditBox searchBox;
    private double resultScroll;
    private double itineraryScroll;
    @Nullable
    private UUID selectedStationGroupId;
    @Nullable
    private ClientNavigationController.NavigationPlan selectedPlan;
    private long selectedPlanRouteRevision = Long.MIN_VALUE;
    private long selectedPlanPipeRevision = Long.MIN_VALUE;
    private String cachedQuery = "";
    private long cachedRouteRevision = Long.MIN_VALUE;
    private long cachedPipeRevision = Long.MIN_VALUE;
    @Nullable
    private ResourceKey<Level> cachedLevelKey;
    private List<ClientNavigationController.DestinationSearchResult> cachedResults = List.of();
    private boolean crossDimensionConfirmationArmed;
    private final Set<Integer> expandedRideSteps = new HashSet<>();

    public DestinationNavigationScreen() {
        super(Component.translatable("screen.superpipeslide.navigation"));
    }

    @Override
    protected SPSGui.Rect createPanelRect() {
        int width = Math.max(244, Math.min(252, this.width - 18));
        int height = Math.max(238, Math.min(322, this.height - 14));
        return new SPSGui.Rect((this.width - width) / 2, (this.height - height) / 2, width, height);
    }

    @Override
    protected void rebuildWidgets() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue();
        this.clearWidgets();
        NavigationLayout layout = layout();
        this.searchBox = this.borderlessBox(layout.search().x() + 20, layout.search().y() + 4, Math.max(58, layout.search().width() - 24), query);
        this.searchBox.setTextColor(TEXT);
        this.searchBox.setTextColorUneditable(TEXT_DIM);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.beginFrame();
        if (this.searchBox == null) {
            this.rebuildWidgets();
        }
        NavigationLayout layout = layout();
        List<ClientNavigationController.DestinationSearchResult> results = results();
        DestinationNavigationViewModel.RoutePreview preview = previewModel();

        renderNavigationShell(graphics, mouseX, mouseY);
        renderSearch(graphics, layout.search());
        renderResults(graphics, layout.results(), results, mouseX, mouseY);
        renderPreview(graphics, layout.preview(), preview, mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.renderTooltips(graphics, mouseX, mouseY);
    }

    private NavigationLayout layout() {
        int pad = 4;
        SPSGui.Rect screen = new SPSGui.Rect(this.panel.x() + pad, this.panel.y() + 27, this.panel.width() - pad * 2, this.panel.height() - 33);
        int gap = 3;
        int leftWidth = Math.max(86, Math.min(90, (int) Math.round(screen.width() * 0.36D)));
        SPSGui.Rect search = new SPSGui.Rect(screen.x(), screen.y(), leftWidth, 16);
        SPSGui.Rect results = new SPSGui.Rect(screen.x(), search.bottom() + 3, leftWidth, screen.bottom() - search.bottom() - 3);
        SPSGui.Rect preview = new SPSGui.Rect(results.right() + gap, screen.y(), screen.right() - results.right() - gap, screen.height());
        return new NavigationLayout(screen, search, results, preview);
    }

    private void renderNavigationShell(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(this.panel.x(), this.panel.y(), this.panel.right(), this.panel.bottom(), FRAME);
        graphics.outline(this.panel.x(), this.panel.y(), this.panel.width(), this.panel.height(), LINE_DIM);
        graphics.fill(this.panel.x() + 1, this.panel.y() + 1, this.panel.right() - 1, this.panel.y() + 24, FullMapTheme.SURFACE_HEADER);
        graphics.fill(this.panel.x() + 1, this.panel.y() + 24, this.panel.right() - 1, this.panel.y() + 25, LINE_DIM);
        graphics.fill(this.panel.x() + 5, this.panel.y() + 27, this.panel.right() - 5, this.panel.bottom() - 6, SCREEN);
        graphics.outline(this.panel.x() + 5, this.panel.y() + 27, this.panel.width() - 10, this.panel.height() - 33, FullMapTheme.BORDER_MUTED);
        renderMapGrid(graphics, new SPSGui.Rect(this.panel.x() + 5, this.panel.y() + 27, this.panel.width() - 10, this.panel.height() - 33));

        SPSGui.Rect back = new SPSGui.Rect(this.panel.x() + 9, this.panel.y() + 7, 16, 16);
        boolean backHover = back.contains(mouseX, mouseY);
        SPSGui.drawIconButton(graphics, back, SPSGui.Icon.BACK, false, backHover, backHover ? BLUE : TEXT_MUTED);
        this.addClick(back, this::onBack, Component.translatable("screen.superpipeslide.action.back"));

        drawText(graphics, Component.translatable("screen.superpipeslide.navigation").getString(), this.panel.x() + 31, this.panel.y() + 8, TEXT, false);
        String status = ClientNavigationController.isNavigating()
                ? Component.translatable("screen.superpipeslide.navigation.status.active").getString()
                : Component.translatable("screen.superpipeslide.navigation.status.ready").getString();
        int maxStatusWidth = Math.max(34, this.panel.width() - 182);
        String compactStatus = SPSGui.ellipsize(this.font, status, Math.round(maxStatusWidth / 0.66F));
        int statusWidth = Math.round(this.font.width(compactStatus) * 0.66F);
        drawSmall(graphics, compactStatus, this.panel.right() - statusWidth - 12, this.panel.y() + 10, ClientNavigationController.isNavigating() ? AMBER : BLUE, 0.66F, false);
    }

    private void renderMapGrid(GuiGraphicsExtractor graphics, SPSGui.Rect rect) {
        for (int x = rect.x() + 8; x < rect.right(); x += 16) {
            graphics.fill(x, rect.y() + 1, x + 1, rect.bottom() - 1, FullRouteMapConfig.MAP_GRID);
        }
        for (int y = rect.y() + 9; y < rect.bottom(); y += 14) {
            graphics.fill(rect.x() + 1, y, rect.right() - 1, y + 1, FullRouteMapConfig.MAP_GRID);
        }
        graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.y() + 2, 0x80FFFFFF);
    }

    private void renderSearch(GuiGraphicsExtractor graphics, SPSGui.Rect search) {
        graphics.fill(search.x(), search.y(), search.right(), search.bottom(), SCREEN_RAISED);
        graphics.outline(search.x(), search.y(), search.width(), search.height(), this.searchBox != null && this.searchBox.isFocused() ? BLUE : LINE_DIM);
        SPSGui.icon(graphics, new SPSGui.Rect(search.x() + 2, search.y() + 2, 12, 12), SPSGui.Icon.SEARCH, this.searchBox != null && this.searchBox.isFocused() ? BLUE : TEXT_MUTED);
        if (this.searchBox != null) {
            this.searchBox.setX(search.x() + 16);
            this.searchBox.setY(search.y() + 2);
            this.searchBox.setWidth(Math.max(48, search.width() - 19));
        }
        if (this.searchBox != null && this.searchBox.getValue().isEmpty() && !this.searchBox.isFocused()) {
            drawSmall(graphics, Component.translatable("screen.superpipeslide.search").getString(), this.searchBox.getX() + 1, this.searchBox.getY() + 2, TEXT_DIM, 0.60F, false);
        }
    }

    private void renderResults(GuiGraphicsExtractor graphics, SPSGui.Rect list, List<ClientNavigationController.DestinationSearchResult> results, int mouseX, int mouseY) {
        this.resultScroll = clamp(this.resultScroll, 0.0D, maxResultScroll(results, list));
        graphics.fill(list.x(), list.y(), list.right(), list.bottom(), 0xF3FFFFFF);
        graphics.outline(list.x(), list.y(), list.width(), list.height(), LINE_DIM);
        if (results.isEmpty()) {
            drawCentered(graphics, Component.translatable("screen.superpipeslide.navigation.no_results").getString(), list.x() + list.width() / 2, list.y() + list.height() / 2 - 4, TEXT_DIM, false);
            return;
        }
        graphics.enableScissor(list.x() + 1, list.y() + 1, list.right() - 1, list.bottom() - 1);
        int y = list.y() + 2 - (int) Math.round(this.resultScroll);
        for (ClientNavigationController.DestinationSearchResult result : results) {
            SPSGui.Rect row = new SPSGui.Rect(list.x() + 2, y, list.width() - 4, RESULT_ROW_HEIGHT);
            if (row.bottom() >= list.y() && row.y() <= list.bottom()) {
                DestinationNavigationViewModel.DestinationCard model = DestinationNavigationViewModel.destinationCard(
                        this.minecraft.player,
                        result,
                        result.stationGroupId().equals(this.selectedStationGroupId)
                );
                renderDestinationRow(graphics, model, row, mouseX, mouseY);
            }
            y += RESULT_ROW_HEIGHT + RESULT_ROW_GAP;
        }
        graphics.disableScissor();
    }

    private void renderDestinationRow(GuiGraphicsExtractor graphics, DestinationNavigationViewModel.DestinationCard model, SPSGui.Rect row, int mouseX, int mouseY) {
        boolean hovered = row.contains(mouseX, mouseY);
        int accent = chipColor(model.statusTone());
        if (model.selected() || hovered) {
            graphics.fill(row.x(), row.y(), row.right(), row.bottom(), model.selected() ? withAlpha(BLUE, 0x18) : withAlpha(BLUE, 0x0C));
        }
        if (model.selected()) {
            graphics.outline(row.x(), row.y(), row.width(), row.height(), withAlpha(BLUE, 0xAA));
        }
        graphics.fill(row.x(), row.y() + 2, row.x() + 2, row.bottom() - 2, withAlpha(accent, model.selected() ? 0xEA : 0xA8));
        int iconX = row.x() + 7;
        int iconY = row.y() + row.height() / 2;
        if (!model.reachable()) {
            SmoothGuiPrimitives.diamond(graphics, new Vec2(iconX, iconY), 3.8D, withAlpha(RED, 0xD8));
        } else if (model.crossDimension()) {
            SmoothGuiPrimitives.diamond(graphics, new Vec2(iconX, iconY), 3.6D, withAlpha(BLUE, 0xD8));
            SmoothGuiPrimitives.circle(graphics, new Vec2(iconX, iconY), 1.2D, withAlpha(0xFFFFFFFF, 0xB8));
        } else {
            SmoothGuiPrimitives.circle(graphics, new Vec2(iconX, iconY), 3.5D, withAlpha(accent, 0xC8));
        }
        int textX = row.x() + 15;
        String name = SPSGui.ellipsize(this.font, model.primaryName(), Math.round((row.right() - textX - 4) / 0.62F));
        drawSmall(graphics, name, textX, row.y() + 6, model.reachable() ? TEXT : TEXT_MUTED, 0.62F, false);
        String detail = model.primaryName();
        if (!model.translatedName().isBlank()) {
            detail += " / " + model.translatedName();
        }
        detail += " · " + model.dimensionText() + " · " + model.statusText();
        this.addClick(row, () -> selectDestination(model.stationGroupId()), Component.literal(detail));
    }

    private void renderPreview(GuiGraphicsExtractor graphics, SPSGui.Rect previewRect, DestinationNavigationViewModel.RoutePreview preview, int mouseX, int mouseY) {
        graphics.fill(previewRect.x(), previewRect.y(), previewRect.right(), previewRect.bottom(), 0xF6FFFFFF);
        graphics.outline(previewRect.x(), previewRect.y(), previewRect.width(), previewRect.height(), LINE_DIM);
        SPSGui.Rect summary = new SPSGui.Rect(previewRect.x() + 3, previewRect.y() + 3, previewRect.width() - 6, 29);
        renderRouteSummaryBar(graphics, summary, preview, mouseX, mouseY);
        SPSGui.Rect itinerary = itineraryRect(previewRect);
        renderItinerary(graphics, itinerary, preview, mouseX, mouseY);
    }

    private void renderRouteSummaryBar(GuiGraphicsExtractor graphics, SPSGui.Rect rect, DestinationNavigationViewModel.RoutePreview preview, int mouseX, int mouseY) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), 0xEAFFFFFF);
        graphics.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), LINE_DIM);
        int right = rect.right() - 3;
        if (ClientNavigationController.isNavigating()) {
            SPSGui.Rect cancel = new SPSGui.Rect(right - 14, rect.y() + 7, 14, 14);
            renderCancelButton(graphics, cancel, mouseX, mouseY);
            right = cancel.x() - 3;
        }
        String actionLabel = crossDimensionConfirmationArmed && preview.needsCrossDimensionConfirmation()
                ? Component.translatable("screen.superpipeslide.navigation.confirm_cross_dimension").getString()
                : preview.primaryActionLabel();
        int actionWidth = Math.min(54, Math.max(38, Math.round(this.font.width(actionLabel) * 0.54F) + 12));
        SPSGui.Rect action = new SPSGui.Rect(right - actionWidth, rect.y() + 7, actionWidth, 16);
        renderPrimaryAction(graphics, action, preview, actionLabel, mouseX, mouseY);

        int titleX = rect.x() + 4;
        int titleMax = Math.max(30, action.x() - titleX - 6);
        drawSmall(graphics, SPSGui.ellipsize(this.font, preview.destinationName(), Math.round(titleMax / 0.68F)), titleX, rect.y() + 4, preview.reachable() ? TEXT : AMBER, 0.68F, false);
        String summary = preview.summaryText().isBlank() ? preview.destinationSubtitle() : preview.summaryText();
        int summaryColor = preview.warnings().isEmpty() ? TEXT_DIM : AMBER;
        drawSmall(graphics, SPSGui.ellipsize(this.font, summary, Math.round(titleMax / 0.50F)), titleX, rect.y() + 17, summaryColor, 0.50F, false);
    }

    private void renderPrimaryAction(GuiGraphicsExtractor graphics, SPSGui.Rect button, DestinationNavigationViewModel.RoutePreview preview, String label, int mouseX, int mouseY) {
        boolean disabled = preview.primaryAction() == DestinationNavigationViewModel.PrimaryAction.UNAVAILABLE;
        boolean hovered = button.contains(mouseX, mouseY) && !disabled;
        int accent = disabled ? TEXT_DIM : crossDimensionConfirmationArmed ? AMBER : preview.primaryColor();
        graphics.fill(button.x(), button.y(), button.right(), button.bottom(), disabled ? FullMapTheme.SURFACE_CONTROL_DISABLED : hovered ? withAlpha(accent, 0x1F) : FullMapTheme.SURFACE_CONTROL);
        graphics.outline(button.x(), button.y(), button.width(), button.height(), disabled ? FullMapTheme.BORDER_MUTED : hovered ? withAlpha(accent, 0xE0) : withAlpha(accent, 0x99));
        drawCenteredSmall(graphics, SPSGui.ellipsize(this.font, label, Math.round((button.width() - 6) / 0.52F)), button.x() + button.width() / 2, button.y() + 5, disabled ? TEXT_DIM : TEXT, 0.52F);
        if (!disabled) {
            this.addClick(button, () -> handlePrimaryAction(preview), Component.literal(preview.primaryActionLabel()));
        }
    }

    private void renderCancelButton(GuiGraphicsExtractor graphics, SPSGui.Rect button, int mouseX, int mouseY) {
        boolean hovered = button.contains(mouseX, mouseY);
        graphics.fill(button.x(), button.y(), button.right(), button.bottom(), hovered ? 0xFFFFEBE8 : 0xFFFFF6F4);
        graphics.outline(button.x(), button.y(), button.width(), button.height(), hovered ? RED : withAlpha(RED, 0x88));
        SPSGui.icon(graphics, button, SPSGui.Icon.CLOSE, hovered ? 0xFFFFB4A8 : RED);
        this.addPriorityClick(button, ClientNavigationController::cancelNavigation, Component.translatable("screen.superpipeslide.navigation.cancel"));
    }

    private void renderItinerary(GuiGraphicsExtractor graphics, SPSGui.Rect rect, DestinationNavigationViewModel.RoutePreview preview, int mouseX, int mouseY) {
        this.itineraryScroll = clamp(this.itineraryScroll, 0.0D, maxItineraryScroll(preview, rect));
        if (preview.itinerary().isEmpty()) {
            drawCentered(graphics, Component.translatable("screen.superpipeslide.navigation.pick_destination").getString(), rect.x() + rect.width() / 2, rect.y() + rect.height() / 2 - 4, TEXT_DIM, false);
            return;
        }
        graphics.enableScissor(rect.x(), rect.y(), rect.right(), rect.bottom());
        int y = rect.y() + 3 - (int) Math.round(this.itineraryScroll);
        for (int i = 0; i < preview.itinerary().size(); i++) {
            DestinationNavigationViewModel.ItineraryStep step = preview.itinerary().get(i);
            int height = itineraryStepHeight(step, i, rect.width());
            SPSGui.Rect stepRect = new SPSGui.Rect(rect.x() + 2, y, rect.width() - 4, height);
            if (stepRect.bottom() >= rect.y() && stepRect.y() <= rect.bottom()) {
                renderItineraryStep(graphics, stepRect, step, i, preview.primaryColor(), mouseX, mouseY);
            }
            y += height + ITINERARY_STEP_GAP;
        }
        graphics.disableScissor();
    }

    private void renderItineraryStep(GuiGraphicsExtractor graphics, SPSGui.Rect rect, DestinationNavigationViewModel.ItineraryStep step, int index, int primaryColor, int mouseX, int mouseY) {
        if (step.kind() == DestinationNavigationViewModel.ItineraryKind.RIDE) {
            renderRideStep(graphics, rect, step, index, mouseX, mouseY);
            return;
        }
        renderSimpleStep(graphics, rect, step, primaryColor);
    }

    private void renderSimpleStep(GuiGraphicsExtractor graphics, SPSGui.Rect rect, DestinationNavigationViewModel.ItineraryStep step, int primaryColor) {
        int lineX = rect.x() + 9;
        int nodeY = rect.y() + 9;
        int color = stepColor(step, primaryColor);
        if (step.kind() != DestinationNavigationViewModel.ItineraryKind.ORIGIN
                && step.kind() != DestinationNavigationViewModel.ItineraryKind.DESTINATION
                && step.kind() != DestinationNavigationViewModel.ItineraryKind.UNREACHABLE) {
            drawDottedLine(graphics, lineX, rect.y(), rect.bottom(), withAlpha(color, 0x8A));
        }
        switch (step.kind()) {
            case ORIGIN -> {
                SmoothGuiPrimitives.circle(graphics, new Vec2(lineX, nodeY), 6.2D, withAlpha(GREEN, 0xD8));
                SmoothGuiPrimitives.circle(graphics, new Vec2(lineX, nodeY), 2.0D, withAlpha(0xFFFFFFFF, 0xE0));
            }
            case DESTINATION -> SmoothGuiPrimitives.diamond(graphics, new Vec2(lineX, nodeY), 6.2D, withAlpha(primaryColor, 0xE2));
            case UNREACHABLE -> SmoothGuiPrimitives.diamond(graphics, new Vec2(lineX, nodeY), 6.2D, withAlpha(RED, 0xE2));
            case CROSS_DIMENSION_TRANSFER, CROSS_DIMENSION_FINAL_WALK -> {
                SmoothGuiPrimitives.circle(graphics, new Vec2(lineX, nodeY), 6.3D, withAlpha(BLUE, 0x42));
                SmoothGuiPrimitives.diamond(graphics, new Vec2(lineX, nodeY), 4.4D, withAlpha(0xFFC59BFF, 0xE2));
            }
            case OUT_OF_STATION_TRANSFER, FINAL_WALK -> {
                SmoothGuiPrimitives.diamond(graphics, new Vec2(lineX, nodeY), 5.2D, withAlpha(AMBER, 0xD8));
                SmoothGuiPrimitives.circle(graphics, new Vec2(lineX, nodeY), 1.7D, withAlpha(0xFFFFFFFF, 0xCC));
            }
            case SAME_STATION_TRANSFER -> {
                SmoothGuiPrimitives.circle(graphics, new Vec2(lineX, nodeY), 5.5D, withAlpha(GREEN, 0x70));
                SmoothGuiPrimitives.circle(graphics, new Vec2(lineX, nodeY), 2.8D, withAlpha(GREEN, 0xD8));
            }
            case WALK_TO_BOARD -> SmoothGuiPrimitives.circle(graphics, new Vec2(lineX, nodeY), 4.4D, withAlpha(TEXT_DIM, 0xB4));
            default -> SmoothGuiPrimitives.circle(graphics, new Vec2(lineX, nodeY), 4.4D, withAlpha(color, 0xB4));
        }
        int textX = rect.x() + 22;
        int textWidth = Math.max(24, rect.right() - textX - 4);
        drawSmall(graphics, SPSGui.ellipsize(this.font, step.title(), Math.round(textWidth / 0.62F)), textX, rect.y() + 2, step.warning() ? AMBER : TEXT, 0.62F, false);
        drawSmall(graphics, SPSGui.ellipsize(this.font, step.detail(), Math.round(textWidth / 0.49F)), textX, rect.y() + 12, step.warning() ? AMBER : TEXT_DIM, 0.49F, false);
    }

    private void renderRideStep(GuiGraphicsExtractor graphics, SPSGui.Rect rect, DestinationNavigationViewModel.ItineraryStep step, int index, int mouseX, int mouseY) {
        boolean hovered = step.expandable() && rect.contains(mouseX, mouseY);
        if (hovered) {
            graphics.fill(rect.x() + 18, rect.y(), rect.right(), rect.bottom(), withAlpha(BLUE, 0x08));
        }
        int lineX = rect.x() + 9;
        int textX = rect.x() + 22;
        int railColor = firstColor(step.colors());
        List<StationDisplay> stations = displayedStations(step, index);
        int stationStartY = rect.y() + 21;
        int railTop = stationStartY + 4;
        int railBottom = stations.size() <= 1 ? rect.bottom() - 6 : stationStartY + (stations.size() - 1) * RIDE_STATION_ROW_HEIGHT + 4;
        drawVerticalRail(graphics, lineX, railTop, railBottom, step.colors(), 4);

        int maxBadgeWidth = Math.max(28, Math.min(48, rect.right() - textX - 5));
        int badgeWidth = drawLineBadge(graphics, textX, rect.y() + 2, step.lineName(), railColor, maxBadgeWidth);
        int detailX = textX + badgeWidth + 3;
        int detailWidth = rect.right() - detailX - 4;
        if (detailWidth >= 22) {
            drawSmall(graphics, SPSGui.ellipsize(this.font, step.detail(), Math.round(detailWidth / 0.50F)), detailX, rect.y() + 6, TEXT_DIM, 0.50F, false);
        }

        for (int i = 0; i < stations.size(); i++) {
            StationDisplay station = stations.get(i);
            int y = stationStartY + i * RIDE_STATION_ROW_HEIGHT;
            if (station.placeholder()) {
                drawSmall(graphics, station.label(), textX + 10, y + 2, TEXT_DIM, 0.54F, false);
                continue;
            }
            boolean endpoint = station.endpoint();
            if (endpoint) {
                SmoothGuiPrimitives.circle(graphics, new Vec2(lineX, y + 4), 4.2D, withAlpha(0xFFFFFFFF, 0xE8));
                SmoothGuiPrimitives.circle(graphics, new Vec2(lineX, y + 4), 2.7D, withAlpha(railColor, 0xE2));
            } else {
                SmoothGuiPrimitives.circle(graphics, new Vec2(lineX, y + 4), 2.4D, withAlpha(0xFFFFFFFF, 0x92));
            }
            int color = endpoint ? TEXT : TEXT_MUTED;
            float scale = endpoint ? 0.57F : 0.52F;
            drawSmall(graphics, SPSGui.ellipsize(this.font, station.label(), Math.round((rect.right() - textX - 9) / scale)), textX + 7, y + 1, color, scale, false);
        }
        if (step.expandable()) {
            this.addClick(rect, () -> toggleRideExpanded(index), Component.literal(expandedRideSteps.contains(index) ? "Collapse stops" : "Expand stops"));
        }
    }

    private int drawLineBadge(GuiGraphicsExtractor graphics, int x, int y, String label, int color, int maxWidth) {
        String text = SPSGui.ellipsize(this.font, label, Math.max(8, Math.round((maxWidth - 7) / 0.56F)));
        int width = Math.min(maxWidth, Math.max(20, Math.round(this.font.width(text) * 0.56F) + 7));
        graphics.fill(x, y, x + width, y + 12, withAlpha(color, 0xDD));
        graphics.outline(x, y, width, 12, withAlpha(color, 0xF2));
        drawSmall(graphics, text, x + 4, y + 3, 0xFFFFFFFF, 0.56F, false);
        return width;
    }

    private void toggleRideExpanded(int index) {
        if (!this.expandedRideSteps.add(index)) {
            this.expandedRideSteps.remove(index);
        }
    }

    private List<StationDisplay> displayedStations(DestinationNavigationViewModel.ItineraryStep step, int index) {
        List<String> names = step.stationNames();
        ArrayList<StationDisplay> result = new ArrayList<>();
        if (names.isEmpty()) {
            return result;
        }
        if (!step.expandable() || this.expandedRideSteps.contains(index) || names.size() <= 7) {
            for (int i = 0; i < names.size(); i++) {
                result.add(new StationDisplay(names.get(i), false, i == 0 || i == names.size() - 1));
            }
            return result;
        }
        result.add(new StationDisplay(names.get(0), false, true));
        result.add(new StationDisplay(names.get(1), false, false));
        int hidden = Math.max(0, names.size() - 4);
        result.add(new StationDisplay(Component.translatable("screen.superpipeslide.navigation.itinerary.more_stops", hidden).getString(), true, false));
        result.add(new StationDisplay(names.get(names.size() - 2), false, false));
        result.add(new StationDisplay(names.getLast(), false, true));
        return result;
    }

    private int itineraryStepHeight(DestinationNavigationViewModel.ItineraryStep step, int index, int width) {
        if (step.kind() != DestinationNavigationViewModel.ItineraryKind.RIDE) {
            return SIMPLE_STEP_HEIGHT;
        }
        int stationRows = Math.max(1, displayedStations(step, index).size());
        return 24 + stationRows * RIDE_STATION_ROW_HEIGHT;
    }

    private void drawDottedLine(GuiGraphicsExtractor graphics, int x, int y1, int y2, int color) {
        for (int y = y1; y < y2; y += 7) {
            SmoothGuiPrimitives.line(graphics, new Vec2(x, y), new Vec2(x, Math.min(y2, y + 3)), 2.0D, color);
        }
    }

    private void drawVerticalRail(GuiGraphicsExtractor graphics, int x, int y1, int y2, List<Integer> colors, int width) {
        if (y2 <= y1) {
            return;
        }
        List<Integer> normalized = normalizedColors(colors);
        if (normalized.size() == 1) {
            SmoothGuiPrimitives.line(graphics, new Vec2(x, y1), new Vec2(x, y2), width, normalized.getFirst());
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

    private void handlePrimaryAction(DestinationNavigationViewModel.RoutePreview preview) {
        if (preview.needsCrossDimensionConfirmation() && !this.crossDimensionConfirmationArmed) {
            this.crossDimensionConfirmationArmed = true;
            return;
        }
        startSelectedNavigation();
    }

    private DestinationNavigationViewModel.RoutePreview previewModel() {
        if (this.selectedStationGroupId == null) {
            return DestinationNavigationViewModel.emptyPreview();
        }
        if (this.selectedPlan == null) {
            return DestinationNavigationViewModel.unreachablePreview(this.selectedStationGroupId);
        }
        return DestinationNavigationViewModel.routePreview(this.selectedPlan);
    }

    private void selectDestination(UUID stationGroupId) {
        this.selectedStationGroupId = stationGroupId;
        this.crossDimensionConfirmationArmed = false;
        this.itineraryScroll = 0.0D;
        this.expandedRideSteps.clear();
        this.selectedPlanRouteRevision = ClientRouteDataCache.revision();
        this.selectedPlanPipeRevision = ClientPipeNetworkCache.aggregateRevision();
        this.selectedPlan = this.minecraft != null && this.minecraft.player != null
                ? ClientNavigationController.previewPlan(this.minecraft.player, stationGroupId).orElse(null)
                : null;
    }

    private void startSelectedNavigation() {
        if (this.minecraft == null || this.minecraft.player == null || this.selectedStationGroupId == null) {
            return;
        }
        Optional<ClientNavigationController.NavigationPlan> plan = ClientNavigationController.startNavigation(this.minecraft.player, this.selectedStationGroupId);
        if (plan.isPresent()) {
            this.onClose();
        } else {
            this.selectedPlan = null;
        }
    }

    private List<ClientNavigationController.DestinationSearchResult> results() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return List.of();
        }
        String query = this.searchBox == null ? "" : this.searchBox.getValue();
        long routeRevision = ClientRouteDataCache.revision();
        long pipeRevision = ClientPipeNetworkCache.aggregateRevision();
        ResourceKey<Level> levelKey = this.minecraft.player.level().dimension();
        boolean queryChanged = !query.equals(this.cachedQuery);
        if (queryChanged
                || this.cachedRouteRevision != routeRevision
                || this.cachedPipeRevision != pipeRevision
                || this.cachedLevelKey == null
                || !this.cachedLevelKey.equals(levelKey)) {
            this.cachedQuery = query;
            this.cachedRouteRevision = routeRevision;
            this.cachedPipeRevision = pipeRevision;
            this.cachedLevelKey = levelKey;
            this.cachedResults = ClientNavigationController.searchDestinations(this.minecraft.player, query, RESULT_LIMIT);
            if (queryChanged) {
                this.resultScroll = 0.0D;
                this.crossDimensionConfirmationArmed = false;
            }
        }
        refreshSelectedPlanIfStale(routeRevision, pipeRevision);
        if (this.cachedResults.isEmpty()) {
            this.selectedStationGroupId = null;
            this.selectedPlan = null;
            this.expandedRideSteps.clear();
            return this.cachedResults;
        }
        if (this.selectedStationGroupId == null || this.cachedResults.stream().noneMatch(result -> result.stationGroupId().equals(this.selectedStationGroupId))) {
            this.selectDestination(this.cachedResults.getFirst().stationGroupId());
        }
        return this.cachedResults;
    }

    private void refreshSelectedPlanIfStale(long routeRevision, long pipeRevision) {
        if (this.minecraft == null || this.minecraft.player == null || this.selectedStationGroupId == null) {
            return;
        }
        if (this.selectedPlanRouteRevision == routeRevision && this.selectedPlanPipeRevision == pipeRevision) {
            return;
        }
        this.selectedPlanRouteRevision = routeRevision;
        this.selectedPlanPipeRevision = pipeRevision;
        this.selectedPlan = ClientNavigationController.previewPlan(this.minecraft.player, this.selectedStationGroupId).orElse(null);
        this.crossDimensionConfirmationArmed = false;
        this.itineraryScroll = 0.0D;
        this.expandedRideSteps.clear();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<ClientNavigationController.DestinationSearchResult> results = results();
        NavigationLayout layout = layout();
        if (layout.results().contains(mouseX, mouseY)) {
            this.resultScroll = clamp(this.resultScroll - scrollY * 22.0D, 0.0D, maxResultScroll(results, layout.results()));
            return true;
        }
        if (layout.preview().contains(mouseX, mouseY)) {
            DestinationNavigationViewModel.RoutePreview preview = previewModel();
            SPSGui.Rect itinerary = itineraryRect(layout.preview());
            this.itineraryScroll = clamp(this.itineraryScroll - scrollY * 22.0D, 0.0D, maxItineraryScroll(preview, itinerary));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void renderTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (ClickAction clickAction : this.clickActions) {
            if (clickAction.bounds().contains(mouseX, mouseY)) {
                renderNavigationTooltip(graphics, clickAction.tooltip().getString(), mouseX, mouseY);
                return;
            }
        }
    }

    private void renderNavigationTooltip(GuiGraphicsExtractor graphics, String text, int mouseX, int mouseY) {
        if (text.isBlank()) {
            return;
        }
        int width = Math.min(190, this.font.width(text) + 14);
        int x = Math.min(this.width - width - 6, mouseX + 10);
        int y = Math.min(this.height - 25, mouseY + 10);
        graphics.fill(x, y, x + width, y + 18, FullMapTheme.SURFACE_TOOLBAR);
        graphics.outline(x, y, width, 18, LINE);
        drawSmall(graphics, SPSGui.ellipsize(this.font, text, Math.round((width - 10) / 0.68F)), x + 6, y + 5, TEXT, 0.68F, false);
    }

    private double maxResultScroll(List<ClientNavigationController.DestinationSearchResult> results, SPSGui.Rect list) {
        return Math.max(0.0D, results.size() * (RESULT_ROW_HEIGHT + RESULT_ROW_GAP) + 2.0D - list.height());
    }

    private double maxItineraryScroll(DestinationNavigationViewModel.RoutePreview preview, SPSGui.Rect rect) {
        int height = 8;
        for (int i = 0; i < preview.itinerary().size(); i++) {
            height += itineraryStepHeight(preview.itinerary().get(i), i, rect.width()) + ITINERARY_STEP_GAP;
        }
        return Math.max(0.0D, height - rect.height());
    }

    private SPSGui.Rect itineraryRect(SPSGui.Rect previewRect) {
        return new SPSGui.Rect(previewRect.x() + 3, previewRect.y() + 34, previewRect.width() - 6, Math.max(28, previewRect.height() - 37));
    }

    private int chipColor(DestinationNavigationViewModel.ChipTone tone) {
        return switch (tone) {
            case SUCCESS -> GREEN;
            case WARNING -> AMBER;
            case INFO -> BLUE;
            case NEUTRAL -> TEXT_MUTED;
        };
    }

    private int stepColor(DestinationNavigationViewModel.ItineraryStep step, int primaryColor) {
        return switch (step.kind()) {
            case ORIGIN, SAME_STATION_TRANSFER -> GREEN;
            case WALK_TO_BOARD -> TEXT_DIM;
            case OUT_OF_STATION_TRANSFER, FINAL_WALK -> AMBER;
            case CROSS_DIMENSION_TRANSFER, CROSS_DIMENSION_FINAL_WALK -> BLUE;
            case DESTINATION -> primaryColor;
            case UNREACHABLE -> RED;
            case RIDE -> firstColor(step.colors());
        };
    }

    private int firstColor(List<Integer> colors) {
        return colors == null || colors.isEmpty() ? BLUE : 0xFF000000 | colors.getFirst() & 0x00FFFFFF;
    }

    private List<Integer> normalizedColors(List<Integer> colors) {
        if (colors == null || colors.isEmpty()) {
            return List.of(BLUE);
        }
        return colors.stream().limit(3).map(color -> 0xFF000000 | color & 0x00FFFFFF).toList();
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean shadow) {
        graphics.text(this.font, text, x, y, color, false);
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int centerX, int y, int color, boolean shadow) {
        graphics.text(this.font, text, centerX - this.font.width(text) / 2, y, color, false);
    }

    private void drawCenteredSmall(GuiGraphicsExtractor graphics, String text, int centerX, int y, int color, float scale) {
        int width = Math.round(this.font.width(text) * scale);
        drawSmall(graphics, text, centerX - width / 2, y, color, scale, false);
    }

    private void drawSmall(GuiGraphicsExtractor graphics, String text, int x, int y, int color, float scale, boolean shadow) {
        if (text == null || text.isBlank()) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(this.font, text, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | color & 0x00FFFFFF;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record NavigationLayout(SPSGui.Rect screen, SPSGui.Rect search, SPSGui.Rect results, SPSGui.Rect preview) {
    }

    private record StationDisplay(String label, boolean placeholder, boolean endpoint) {
    }
}
