package dev.marblegate.superpipeslide.client.fullmap.screen;

import dev.marblegate.superpipeslide.client.SuperPipeSlideClient;
import dev.marblegate.superpipeslide.client.core.accessibility.ClientSafetyOptions;
import dev.marblegate.superpipeslide.client.core.navigation.ClientNavigationController;
import dev.marblegate.superpipeslide.client.core.navigation.NavigationSemanticColors;
import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.fullmap.cache.FullRouteMapCache;
import dev.marblegate.superpipeslide.client.fullmap.cache.FullRouteMapSessionState;
import dev.marblegate.superpipeslide.client.fullmap.card.CardKind;
import dev.marblegate.superpipeslide.client.fullmap.card.MapCard;
import dev.marblegate.superpipeslide.client.fullmap.cluster.hit.ClusterCardHit;
import dev.marblegate.superpipeslide.client.fullmap.cluster.hit.ClusterCardHitKind;
import dev.marblegate.superpipeslide.client.fullmap.cluster.layout.ClusterCardLayoutSolver;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardEdge;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardEdgeKind;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardNode;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardNodeKind;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardProfile;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardSemanticGraph;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardState;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardViewport;
import dev.marblegate.superpipeslide.client.fullmap.cluster.render.ClusterCardRenderResult;
import dev.marblegate.superpipeslide.client.fullmap.cluster.render.ClusterCardRenderer;
import dev.marblegate.superpipeslide.client.fullmap.cluster.semantic.ClusterCardSemanticBuilder;
import dev.marblegate.superpipeslide.client.fullmap.cluster.visual.ClusterCardVisualGraph;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapConfig;
import dev.marblegate.superpipeslide.client.fullmap.config.FullRouteMapLayoutMode;
import dev.marblegate.superpipeslide.client.fullmap.diagnostic.DiagnosticType;
import dev.marblegate.superpipeslide.client.fullmap.diagnostic.MapBuildDiagnostic;
import dev.marblegate.superpipeslide.client.fullmap.diagnostic.MissingCrossDimensionReason;
import dev.marblegate.superpipeslide.client.fullmap.model.MapCluster;
import dev.marblegate.superpipeslide.client.fullmap.model.MapDimensionGraph;
import dev.marblegate.superpipeslide.client.fullmap.model.MapEdge;
import dev.marblegate.superpipeslide.client.fullmap.model.MapEdgeOccurrence;
import dev.marblegate.superpipeslide.client.fullmap.model.MapNode;
import dev.marblegate.superpipeslide.client.fullmap.model.MapTransferHint;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;
import dev.marblegate.superpipeslide.client.fullmap.model.NodeKind;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Aabb2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.ViewportState;
import dev.marblegate.superpipeslide.client.fullmap.model.hit.HitTarget;
import dev.marblegate.superpipeslide.client.fullmap.model.search.SearchKind;
import dev.marblegate.superpipeslide.client.fullmap.model.search.SearchResult;
import dev.marblegate.superpipeslide.client.fullmap.navigation.FullMapNavigationOverlayRenderer;
import dev.marblegate.superpipeslide.client.fullmap.navigation.FullMapNavigationViewModel;
import dev.marblegate.superpipeslide.client.fullmap.physical.PhysicalMapEdge;
import dev.marblegate.superpipeslide.client.fullmap.physical.PhysicalMapNode;
import dev.marblegate.superpipeslide.client.fullmap.physical.PhysicalNodeKind;
import dev.marblegate.superpipeslide.client.fullmap.physical.PhysicalRouteMapGraph;
import dev.marblegate.superpipeslide.client.fullmap.physical.PhysicalStationFrame;
import dev.marblegate.superpipeslide.client.fullmap.render.FullRouteMapRenderer;
import dev.marblegate.superpipeslide.client.fullmap.render.SmoothGuiPrimitives;
import dev.marblegate.superpipeslide.client.fullmap.routecard.hit.LayoutChipHit;
import dev.marblegate.superpipeslide.client.fullmap.routecard.hit.RouteLineCardHit;
import dev.marblegate.superpipeslide.client.fullmap.routecard.hit.RouteLineCardHitKind;
import dev.marblegate.superpipeslide.client.fullmap.routecard.hit.ViewModeChipHit;
import dev.marblegate.superpipeslide.client.fullmap.routecard.layout.RouteCardLayoutSolver;
import dev.marblegate.superpipeslide.client.fullmap.routecard.layout.RouteCardPhysicalLayoutBuilder;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardEdge;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNode;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNodeId;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardNodeKind;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardSemanticGraph;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardViewMode;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardViewport;
import dev.marblegate.superpipeslide.client.fullmap.routecard.render.RouteLineCardRenderResult;
import dev.marblegate.superpipeslide.client.fullmap.routecard.render.RouteLineCardRenderer;
import dev.marblegate.superpipeslide.client.fullmap.routecard.render.RouteLineCardState;
import dev.marblegate.superpipeslide.client.fullmap.routecard.semantic.RouteCardSemanticBuilder;
import dev.marblegate.superpipeslide.client.fullmap.routecard.visual.RouteCardVisualGraph;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SchematicQualityReport;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualEdgePath;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualNode;
import dev.marblegate.superpipeslide.client.fullmap.schematic.visual.VisualRouteMapGraph;
import dev.marblegate.superpipeslide.client.fullmap.ui.DisplayNameStack;
import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapText;
import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapTheme;
import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapTooltipCard;
import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapUi;
import dev.marblegate.superpipeslide.client.gui.base.RouteDataAwareScreen;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.client.gui.base.SPSScreen;
import dev.marblegate.superpipeslide.client.gui.station.StationTransferEditorScreen;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.route.model.layout.RouteLayout;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationTransferLink;
import dev.marblegate.superpipeslide.config.ClientConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

public class FullRouteMapScreen extends SPSScreen implements RouteDataAwareScreen {
    private static final int MAX_ROUTE_CARD_GRAPH_CACHE_ENTRIES = 96;
    private static final int MAX_CLUSTER_CARD_GRAPH_CACHE_ENTRIES = 32;
    private static final int NAVIGATION_RESULT_LIMIT = 32;
    private static final int NAVIGATION_RESULT_ROW_HEIGHT = 30;
    private static final int NAVIGATION_SIMPLE_STEP_HEIGHT = 22;
    private static final int NAVIGATION_RIDE_STATION_ROW_HEIGHT = 10;
    private static final int NAVIGATION_ITINERARY_STEP_GAP = 2;
    private static final double MAP_CLICK_DRAG_THRESHOLD_PX = 6.0D;
    private static final long MAP_DOUBLE_CLICK_INTERVAL_MILLIS = 400L;
    private static final long NAVIGATION_CANCEL_CONFIRM_TIMEOUT_MILLIS = 3000L;
    private static final long NAVIGATION_UNDO_TOAST_DURATION_MILLIS = 5000L;
    // D1: viewport tween / inertia / soft bounds tuning.
    private static final long VIEWPORT_TWEEN_DURATION_MILLIS = 240L;
    private static final long VIEWPORT_SCROLL_TWEEN_DURATION_MILLIS = 120L;
    private static final double MAP_WHEEL_ZOOM_STEP = 1.18D;
    private static final double MAP_KEY_ZOOM_STEP = 1.25D;
    private static final double MAP_DOUBLE_CLICK_ZOOM_FACTOR = 1.5D;
    private static final double MAP_PAN_KEY_STEP_RATIO = 0.15D;
    private static final double MAP_PAN_KEY_SHIFT_MULTIPLIER = 3.0D;
    private static final long TOAST_DURATION_MILLIS = 1800L;

    /** Wheel zoom step with the player's configured sensitivity applied (1.0 = default). */
    private static double wheelZoomStep() {
        return Math.pow(MAP_WHEEL_ZOOM_STEP, ClientConfig.FULL_ROUTE_MAP_ZOOM_SENSITIVITY.get());
    }

    private static final double MAP_SOFT_BOUNDS_PADDING_RATIO = 0.2D;
    private static final double MAP_SOFT_BOUNDS_MIN_PADDING_BLOCKS = 64.0D;
    private static final double MAP_INERTIA_DECAY_PER_FRAME = 0.88D;
    private static final long MAP_INERTIA_MAX_MILLIS = 1200L;
    private static final double MAP_INERTIA_MIN_SPEED_PX_PER_FRAME = 0.4D;
    private static final double MAP_INERTIA_MAX_SPEED_PX_PER_FRAME = 60.0D;
    // F5: hover focus ring / tooltip fade-in durations. Both transitions are skipped
    // entirely while the photosensitivity safety option is on (same gating as the
    // world-space pulses in phase A).
    private static final long HOVER_FOCUS_FADE_IN_MILLIS = 120L;
    private static final long TOOLTIP_FADE_IN_MILLIS = 80L;
    // F6: named text scales for labels that have no FullMapTheme.TYPE_* counterpart
    // (the theme only defines TYPE_TITLE/BODY/META/TINY at 1.0/0.80/0.64/0.55, and it
    // is owned by another change, so these stay local to the screen).
    private static final float ROW_TITLE_TEXT_SCALE = 0.68F;
    private static final float ROW_DETAIL_TEXT_SCALE = 0.56F;
    private static final float ROW_META_TEXT_SCALE = 0.54F;
    private static final float ROW_SUB_TEXT_SCALE = 0.52F;
    private static final float ROW_ENDPOINT_TEXT_SCALE = 0.57F;
    private static final float ACTION_LABEL_TEXT_SCALE = 0.58F;
    private static final float SCALE_BAR_TEXT_SCALE = 0.62F;
    private static final float COMPASS_TEXT_SCALE = 0.50F;

    private final FullRouteMapRenderer renderer = new FullRouteMapRenderer();
    private final FullMapNavigationOverlayRenderer navigationOverlayRenderer = new FullMapNavigationOverlayRenderer();
    private final RouteCardSemanticBuilder routeCardSemanticBuilder = new RouteCardSemanticBuilder();
    private final RouteCardLayoutSolver routeCardLayoutSolver = new RouteCardLayoutSolver();
    private final RouteCardPhysicalLayoutBuilder routeCardPhysicalLayoutBuilder = new RouteCardPhysicalLayoutBuilder();
    private final RouteLineCardRenderer routeLineCardRenderer = new RouteLineCardRenderer();
    private final ClusterCardSemanticBuilder clusterCardSemanticBuilder = new ClusterCardSemanticBuilder();
    private final ClusterCardLayoutSolver clusterCardLayoutSolver = new ClusterCardLayoutSolver();
    private final ClusterCardRenderer clusterCardRenderer = new ClusterCardRenderer();
    private final Map<ResourceKey<Level>, ViewportState> viewports = new HashMap<>();
    // G8: the card stack, its window bounds and all stack operations live in
    // MapCardManager; the callbacks wire the screen-side cleanup (render regions,
    // focus keys) and the card-limit eviction toast back in.
    private final MapCardManager cardManager = new MapCardManager(this::onCardRemovedFromStack, this::showCardLimitToast);
    private final Map<String, Double> lineStripScrolls = new HashMap<>();
    private final Map<String, SPSGui.Rect> lineStripRegions = new HashMap<>();
    private final Map<String, Double> lineStripMaxScrolls = new HashMap<>();
    private final Map<String, Double> routeCardStopListScrolls = new HashMap<>();
    private final Map<String, SPSGui.Rect> routeCardStopListRegions = new HashMap<>();
    private final Map<String, Double> routeCardStopListMaxScrolls = new HashMap<>();
    private final Map<String, Double> stationCardRouteScrolls = new HashMap<>();
    private final Map<String, SPSGui.Rect> stationCardRouteRegions = new HashMap<>();
    private final Map<String, Double> stationCardRouteMaxScrolls = new HashMap<>();
    private final Map<String, SPSGui.Rect> routeCardMapRegions = new HashMap<>();
    private final Map<String, List<SPSGui.Rect>> routeCardControlRegions = new HashMap<>();
    private final Map<String, RouteCardViewport> routeCardResolvedViewports = new HashMap<>();
    private final Map<RouteCardGraphCacheKey, RouteCardGraphBundle> routeCardGraphCache = new LinkedHashMap<>(32, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<RouteCardGraphCacheKey, RouteCardGraphBundle> eldest) {
            return this.size() > MAX_ROUTE_CARD_GRAPH_CACHE_ENTRIES;
        }
    };
    private final Map<ClusterCardGraphCacheKey, ClusterCardGraphBundle> clusterCardGraphCache = new LinkedHashMap<>(32, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ClusterCardGraphCacheKey, ClusterCardGraphBundle> eldest) {
            return this.size() > MAX_CLUSTER_CARD_GRAPH_CACHE_ENTRIES;
        }
    };
    private final Map<String, SPSGui.Rect> clusterCardMapRegions = new HashMap<>();
    private final Map<String, SPSGui.Rect> clusterCardFitRegions = new HashMap<>();
    private final Map<String, ClusterCardViewport> clusterCardResolvedViewports = new HashMap<>();
    private final Map<String, List<ClickAction>> cardClickActions = new HashMap<>();
    private final List<ClickAction> backgroundClickActions = new ArrayList<>();
    private final List<SPSGui.Rect> hoverOverlayAvoidRects = new ArrayList<>();
    private final List<Runnable> deferredCardOverlays = new ArrayList<>();
    private final List<SPSGui.Rect> mapChromeBounds = new ArrayList<>();
    private Optional<ContextPicker> contextPicker = Optional.empty();
    private SPSGui.Rect contextPickerBounds = new SPSGui.Rect(0, 0, 0, 0);
    private final List<SPSGui.Rect> contextPickerActionBounds = new ArrayList<>();
    private final List<SPSGui.Rect> contextPickerRowBounds = new ArrayList<>();
    private double contextPickerScroll;
    private double contextPickerMaxScroll;
    private Optional<String> renderingCardKey = Optional.empty();
    private Optional<String> draggingCardKey = Optional.empty();
    private Optional<String> draggingRouteCardViewportKey = Optional.empty();
    private Optional<String> draggingClusterCardViewportKey = Optional.empty();
    private Optional<String> focusedRouteCardKey = Optional.empty();
    private Optional<String> focusedClusterCardKey = Optional.empty();
    private ResourceKey<Level> activeDimension;
    private SPSGui.Rect mapRect = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect layoutModeStripBounds = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect cameraCompassBounds = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect schematicLegendBounds = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect dimensionChipBounds = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect diagnosticsBadgeBounds = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect diagnosticsPanelBounds = new SPSGui.Rect(0, 0, 0, 0);
    private boolean diagnosticsPanelOpen;
    private double diagnosticsScroll;
    private SPSGui.Rect dimensionMenuBounds = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect searchControlBounds = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect searchResultsBounds = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect navigationDrawerBounds = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect activeNavigationPillBounds = new SPSGui.Rect(0, 0, 0, 0);
    private SPSGui.Rect navigationItineraryBounds = new SPSGui.Rect(0, 0, 0, 0);
    @Nullable
    private SPSGui.Rect navigationDrawerUserBounds;
    private double navigationDrawerUserXRatio = Double.NaN;
    private double navigationDrawerUserYRatio = Double.NaN;
    private EditBox searchBox;
    private boolean dimensionMenuOpen;
    private boolean searchExpanded;
    private boolean navigationSheetExpanded;
    private boolean navigationCrossDimensionConfirmationArmed;
    private boolean navigationCancelArmed;
    private long navigationCancelArmedAtMillis;
    private boolean draggingMapCamera;
    private boolean draggingNavigationDrawer;
    private boolean schematicLegendCollapsed;
    private double schematicLegendScroll;
    private double schematicLegendMaxScroll;
    private Optional<UUID> schematicLegendHoverRouteLineId = Optional.empty();
    @Nullable
    private MapDimensionGraph cachedSchematicLegendGraph;
    @Nullable
    private VisualRouteMapGraph cachedSchematicLegendVisualGraph;
    @Nullable
    private ViewportState cachedSchematicLegendViewport;
    private int cachedSchematicLegendMapWidth = -1;
    private int cachedSchematicLegendMapHeight = -1;
    private List<SchematicLegendRow> cachedSchematicLegendRows = List.of();
    @Nullable
    private UUID selectedNavigationStationGroupId;
    @Nullable
    private ClientNavigationController.NavigationPlan selectedNavigationPlan;
    private boolean selectedNavigationPlanFromActiveSession;
    private long selectedNavigationPlanRouteRevision = Long.MIN_VALUE;
    private long selectedNavigationPlanPipeRevision = Long.MIN_VALUE;
    private String cachedNavigationQuery = "";
    private long cachedNavigationRouteRevision = Long.MIN_VALUE;
    private long cachedNavigationPipeRevision = Long.MIN_VALUE;
    @Nullable
    private ResourceKey<Level> cachedNavigationLevelKey;
    private List<ClientNavigationController.DestinationSearchResult> cachedNavigationResults = List.of();
    private String cachedSearchQuery = "";
    private long cachedSearchRouteRevision = Long.MIN_VALUE;
    @Nullable
    private ResourceKey<Level> cachedSearchDimension;
    private List<SearchResult> cachedSearchResults = List.of();
    private long cachedDimensionCountsRouteRevision = Long.MIN_VALUE;
    private long cachedDimensionCountsPipeRevision = Long.MIN_VALUE;
    private final Map<ResourceKey<Level>, int[]> cachedDimensionCounts = new HashMap<>();
    private double navigationResultScroll;
    private double navigationItineraryScroll;
    private final Set<Integer> navigationExpandedRideSteps = new HashSet<>();
    private HitTarget hover = HitTarget.none();
    // F5: hover focus ring fade-in. FullRouteMapRenderer owns the ring geometry but
    // exposes no alpha hook, so during the fade window the screen strips the node id
    // from the hit target handed to the renderer (suppressing its focus ring) and
    // draws an equivalent ring itself with a ramped alpha; once the ramp completes
    // the renderer takes over again.
    private Object hoverFocusKey = "";
    private long hoverFocusChangedAtMillis = Long.MIN_VALUE;
    private List<HoverFocusRing> hoverFocusRings = List.of();
    private float hoverFocusAlpha = 1.0F;
    // F5: tooltip fade-in. Keyed on the tooltip text; the ramp restarts when the
    // content changes or the tooltip was absent for at least one frame (re-hover).
    private String tooltipFadeKey = "";
    private long tooltipFadeShownAtMillis = Long.MIN_VALUE;
    private long tooltipFadeLastSeenFrame = Long.MIN_VALUE;
    private long tooltipFadeFrameCounter;
    private String toast = "";
    private long toastUntilMillis;
    @Nullable
    private Runnable toastAction;
    private String toastActionLabel = "";
    private Optional<PendingMapClick> pendingMapClick = Optional.empty();
    private long lastMapClickReleaseMillis = Long.MIN_VALUE;
    private double lastMapClickReleaseX;
    private double lastMapClickReleaseY;
    @Nullable
    private ViewportTween viewportTween;
    private boolean panningMap;
    private long lastPanSampleMillis;
    private double panVelocityX;
    private double panVelocityZ;
    private boolean mapInertiaActive;
    private double mapInertiaVelocityX;
    private double mapInertiaVelocityZ;
    private long mapInertiaStartedMillis;
    private long mapInertiaLastTickMillis;
    // D3: search text restored from the previous session; consumed by the first rebuildWidgets.
    private String restoredSearchText = "";
    // D4: per-frame scroll bar bindings (re-registered at each bar's draw site) plus the
    // transient scroll-drag state machine.
    private final Map<String, FullMapScrollBar.Binding> scrollBarBindings = new LinkedHashMap<>();
    @Nullable
    private FullMapScrollBar.Drag scrollBarDrag;
    @Nullable
    private PendingRowScroll pendingRowScroll;
    // D5: sticky tooltip/preview placement across frames (placement hysteresis).
    @Nullable
    private SPSGui.Rect tooltipPlacement;
    @Nullable
    private SPSGui.Rect hoverPreviewPlacement;

    public FullRouteMapScreen() {
        super(Component.translatable("screen.superpipeslide.full_map"));
        // D3: adopt the previous session's UI state. Restored cards whose data vanished
        // meanwhile fall back to the renderers' "missing" branches; card windows outside
        // the current window size are clamped by the card manager's per-frame bounds pass.
        FullRouteMapSessionState.Snapshot session = FullRouteMapSessionState.snapshot();
        this.viewports.putAll(session.viewports());
        this.cardManager.restore(session.cardWindowBounds(), session.cardStack());
        this.restoredSearchText = session.searchText();
        this.navigationDrawerUserXRatio = session.navigationDrawerUserXRatio();
        this.navigationDrawerUserYRatio = session.navigationDrawerUserYRatio();
        if (Double.isFinite(this.navigationDrawerUserXRatio) || Double.isFinite(this.navigationDrawerUserYRatio)) {
            // computeNavigationDrawerBounds derives the drawer position from the ratios
            // whenever userBounds is non-null; the ratios rescale to the new window size.
            this.navigationDrawerUserBounds = new SPSGui.Rect(0, 0, 0, 0);
        }
        this.lineStripScrolls.putAll(session.lineStripScrolls());
        this.routeCardStopListScrolls.putAll(session.routeCardStopListScrolls());
        this.stationCardRouteScrolls.putAll(session.stationCardRouteScrolls());
        this.navigationResultScroll = session.navigationResultScroll();
        this.navigationItineraryScroll = session.navigationItineraryScroll();
        this.schematicLegendScroll = session.schematicLegendScroll();
    }

    @Override
    protected SPSGui.Rect createPanelRect() {
        return new SPSGui.Rect(0, 0, this.width, this.height);
    }

    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();
        this.searchBox = this.borderlessBox(-1000, -1000, 1, this.searchBox == null ? this.restoredSearchText : this.searchBox.getValue());
    }

    @Override
    public void refreshFromRouteSnapshot() {
        FullRouteMapCache.invalidate();
        this.routeCardGraphCache.clear();
        this.clusterCardGraphCache.clear();
        this.cachedSearchRouteRevision = Long.MIN_VALUE;
        this.cachedDimensionCountsRouteRevision = Long.MIN_VALUE;
        this.cachedDimensionCountsPipeRevision = Long.MIN_VALUE;
        this.cachedDimensionCounts.clear();
        this.cachedSchematicLegendGraph = null;
        this.cachedSchematicLegendVisualGraph = null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.beginFrame();
        this.tooltipFadeFrameCounter++;
        this.lineStripRegions.clear();
        this.lineStripMaxScrolls.clear();
        this.routeCardMapRegions.clear();
        this.routeCardControlRegions.clear();
        this.routeCardResolvedViewports.clear();
        this.clusterCardMapRegions.clear();
        this.clusterCardFitRegions.clear();
        this.clusterCardResolvedViewports.clear();
        this.routeCardStopListRegions.clear();
        this.routeCardStopListMaxScrolls.clear();
        this.stationCardRouteRegions.clear();
        this.stationCardRouteMaxScrolls.clear();
        this.cardClickActions.clear();
        this.backgroundClickActions.clear();
        this.hoverOverlayAvoidRects.clear();
        this.deferredCardOverlays.clear();
        this.mapChromeBounds.clear();
        this.scrollBarBindings.clear();
        this.mapRect = new SPSGui.Rect(0, 0, this.width, this.height);
        FullRouteMapCache.refresh(false);
        this.refreshSelectedNavigationPlanIfStale(ClientRouteDataCache.revision(), ClientPipeNetworkCache.aggregateRevision());
        this.ensureActiveDimension();
        this.tickViewportDynamics();
        this.cardManager.updateWindowBounds(this.width, this.height);
        this.updateMapChromeBounds();

        Optional<MapDimensionGraph> graph = this.activeDimension == null ? Optional.empty() : FullRouteMapCache.graph(this.activeDimension);
        Optional<VisualRouteMapGraph> visualGraph = this.activeDimension == null ? Optional.empty() : FullRouteMapCache.visualGraph(this.activeDimension);
        Optional<PhysicalRouteMapGraph> physicalGraph = this.activeDimension == null ? Optional.empty() : FullRouteMapCache.physicalGraph(this.activeDimension);
        boolean mapChromeBlocksHover = this.mapChromeBlocks(mouseX, mouseY);
        boolean contextPickerBlocksHover = this.contextPicker.isPresent();
        this.schematicLegendHoverRouteLineId = Optional.empty();
        if (FullRouteMapCache.layoutMode().physical() && physicalGraph.isPresent()) {
            ViewportState viewport = this.viewportFor(physicalGraph.get());
            this.hover = this.cardManager.topmostCardAt(mouseX, mouseY).isPresent() || mapChromeBlocksHover || contextPickerBlocksHover
                    ? HitTarget.none()
                    : this.renderer.hitTestPhysical(physicalGraph.get(), viewport, this.mapRect, mouseX, mouseY);
            HitTarget renderHover = this.beginHoverFocusFade(physicalGraph.get(), viewport);
            this.renderer.renderPhysical(graphics, this.font, physicalGraph.get(), viewport, this.mapRect, renderHover, mouseX, mouseY);
            this.renderHoverFocusFade(graphics);
            this.currentNavigationOverlayPlan().ifPresent(plan -> this.navigationOverlayRenderer.renderPhysical(
                    graphics,
                    physicalGraph.get(),
                    viewport,
                    this.mapRect,
                    plan,
                    this.navigationActiveSegmentIndex(plan)));
        } else if (graph.isPresent()) {
            ViewportState viewport = this.displayViewport(graph.get());
            List<SchematicLegendRow> schematicLegendRows = this.schematicLegendRows(graph.get(), visualGraph.orElse(null), viewport);
            this.schematicLegendHoverRouteLineId = this.hoveredSchematicLegendRoute(schematicLegendRows, mouseX, mouseY);
            this.hover = this.cardManager.topmostCardAt(mouseX, mouseY).isPresent() || mapChromeBlocksHover || contextPickerBlocksHover
                    ? HitTarget.none()
                    : this.renderer.hitTest(graph.get(), visualGraph.orElse(null), viewport, this.mapRect, mouseX, mouseY);
            HitTarget renderHover = this.beginHoverFocusFade(graph.get(), visualGraph.orElse(null), viewport);
            this.renderer.render(graphics, this.font, graph.get(), visualGraph.orElse(null), viewport, this.mapRect, renderHover, mouseX, mouseY, this.schematicLegendHoverRouteLineId);
            this.renderHoverFocusFade(graphics);
            this.currentNavigationOverlayPlan().ifPresent(plan -> this.navigationOverlayRenderer.render(
                    graphics,
                    graph.get(),
                    visualGraph.orElse(null),
                    viewport,
                    this.mapRect,
                    plan,
                    this.navigationActiveSegmentIndex(plan)));
            // A dimension with nothing to draw usually means broken data: surface the
            // first diagnostic instead of leaving a blank map (ghost dimension).
            if (graph.get().nodes().isEmpty() && !graph.get().diagnostics().isEmpty()) {
                MapBuildDiagnostic first = graph.get().diagnostics().getFirst();
                SPSGui.centeredText(graphics, this.font, Component.translatable(diagnosticNameKey(first.type())), this.width / 2, this.height / 2 - 8, FullMapTheme.WARNING);
                SPSGui.centeredText(graphics, this.font, Component.translatable(diagnosticGuideKey(first.type())), this.width / 2, this.height / 2 + 6, FullMapTheme.TEXT_MUTED);
            }
        } else {
            FullRouteMapRenderer.drawMapBackground(graphics, this.mapRect, 0.0D, 0.0D, FullRouteMapConfig.BASE_SCALE, FullRouteMapCache.layoutMode());
            SPSGui.centeredText(graphics, this.font, Component.translatable("screen.superpipeslide.full_map.no_data"), this.width / 2, this.height / 2, FullRouteMapConfig.MAP_LABEL_MUTED);
            this.hover = HitTarget.none();
            this.trackHoverFocusChange();
            this.hoverFocusRings = List.of();
            this.hoverFocusAlpha = 1.0F;
        }

        this.renderScaleBar(graphics);
        this.renderLayoutModeStrip(graphics, mouseX, mouseY);
        this.renderBuildingIndicator(graphics);
        this.renderSchematicQualityOverlay(graphics, visualGraph.orElse(null));
        this.renderCameraCompass(graphics, mouseX, mouseY);
        this.renderSchematicRouteLegend(graphics, graph.orElse(null), visualGraph.orElse(null), mouseX, mouseY);
        this.renderDimensionControl(graphics, mouseX, mouseY);
        this.renderDiagnosticsControl(graphics, mouseX, mouseY);
        this.renderSearchControl(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.renderCards(graphics, mouseX, mouseY);
        this.renderFullMapNavigationSearch(graphics, mouseX, mouseY);
        this.renderFullMapNavigationPanel(graphics, mouseX, mouseY);
        this.renderDimensionMenu(graphics, mouseX, mouseY);
        this.renderDiagnosticsPanel(graphics, mouseX, mouseY);
        this.renderContextPicker(graphics, mouseX, mouseY);
        this.renderToast(graphics);
        if (this.cardManager.topmostCardAt(mouseX, mouseY).isEmpty() && this.contextPicker.isEmpty()) {
            if (FullRouteMapCache.layoutMode().physical()) {
                this.renderPhysicalFoldHoverPreview(graphics, physicalGraph.orElse(null), mouseX, mouseY);
                this.renderPhysicalHoverTooltip(graphics, physicalGraph.orElse(null), mouseX, mouseY);
            } else {
                this.renderFoldHoverPreview(graphics, graph.orElse(null), visualGraph.orElse(null), mouseX, mouseY);
                this.renderClusterHoverPreview(graphics, graph.orElse(null), mouseX, mouseY);
                this.renderHoverTooltip(graphics, graph.orElse(null), mouseX, mouseY);
            }
        }
        this.renderTooltips(graphics, mouseX, mouseY);
        this.renderDeferredCardOverlays();
    }

    @Override
    protected void addClick(SPSGui.Rect bounds, Runnable action, Component tooltip) {
        super.addClick(bounds, action, tooltip);
        this.registerScopedClick(this.clickAction(bounds, action, tooltip), false);
    }

    @Override
    protected void addPriorityClick(SPSGui.Rect bounds, Runnable action, Component tooltip) {
        super.addPriorityClick(bounds, action, tooltip);
        this.registerScopedClick(this.clickAction(bounds, action, tooltip), true);
    }

    @Override
    protected void renderTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.contextPicker.isPresent() && this.contextPickerBounds.contains(mouseX, mouseY)) {
            ContextPicker picker = this.contextPicker.get();
            for (int i = 0; i < Math.min(this.contextPickerActionBounds.size(), picker.actions().size()); i++) {
                if (this.contextPickerActionBounds.get(i).contains(mouseX, mouseY) && !picker.actions().get(i).tooltip().getString().isBlank()) {
                    this.tooltipPlacement = FullMapTooltipCard.renderComponent(graphics, this.font, this.screenBounds(), mouseX, mouseY, this.tooltipPlacement, picker.actions().get(i).tooltip(), this.tooltipFadeOpacity(picker.actions().get(i).tooltip().getString()));
                    return;
                }
            }
            return;
        }
        Optional<String> hoveredCard = this.mapPopoverBlocks(mouseX, mouseY) ? Optional.empty() : this.cardManager.topmostCardAt(mouseX, mouseY);
        List<ClickAction> actions = hoveredCard
                .map(key -> this.cardClickActions.getOrDefault(key, List.of()))
                .orElse(this.backgroundClickActions);
        for (ClickAction clickAction : actions) {
            if (clickAction.bounds().contains(mouseX, mouseY)) {
                if (clickAction.tooltip().getString().isBlank()) {
                    continue;
                }
                this.tooltipPlacement = FullMapTooltipCard.renderComponent(graphics, this.font, this.screenBounds(), mouseX, mouseY, this.tooltipPlacement, clickAction.tooltip(), this.tooltipFadeOpacity(clickAction.tooltip().getString()));
                return;
            }
        }
    }

    // === F5: hover focus ring fade-in ===
    // The renderer's focus ring reads static colors and takes no alpha parameter, and
    // FullRouteMapRenderer is out of scope for this change. The fade is therefore done
    // as a screen-layer takeover: while the ramp runs, the node id is stripped from the
    // hit target handed to the renderer (suppressing its ring and only its ring --
    // edge/hint hovers ride along untouched) and renderHoverFocusFade draws an
    // equivalent ring with a ramped alpha. Once the ramp completes the renderer draws
    // its own ring again, so no geometry duplication persists beyond the 120ms window.

    /**
     * Tracks hover-target changes and, while the fade-in ramp runs, returns the hit
     * target to hand to the renderer with the node id stripped. Outside the ramp the
     * live hover passes through untouched.
     */
    private HitTarget beginHoverFocusFade(MapDimensionGraph graph, @Nullable VisualRouteMapGraph visualGraph, ViewportState viewport) {
        this.trackHoverFocusChange();
        this.hoverFocusRings = List.of();
        this.hoverFocusAlpha = 1.0F;
        if (ClientSafetyOptions.reducePhotosensitivityRisk() || !(this.hover instanceof HitTarget.NodeHit(var hoveredNodeId))) {
            return this.hover;
        }
        MapNode hovered = graph.node(hoveredNodeId).orElse(null);
        if (hovered == null) {
            return this.hover;
        }
        // Only plain nodes fade: hovering a transfer station or a fold anchor also
        // lights auxiliary nodes (transfer group / fold peer) through renderer-side
        // derivations the screen cannot reproduce here, so those keep the instant
        // treatment rather than fading partially.
        if (hovered.kind() == NodeKind.FOLD_ANCHOR || hovered.isTransferStation()) {
            return this.hover;
        }
        float alpha = this.hoverFocusRamp();
        if (alpha >= 1.0F) {
            return this.hover;
        }
        Vec2 visual = FullRouteMapRenderer.visualPosition(graph, visualGraph, hovered);
        this.hoverFocusRings = List.of(new HoverFocusRing(FullRouteMapRenderer.worldToScreen(visual.x(), visual.y(), viewport, this.mapRect), mapNodeRingRadius(hovered, viewport.zoom()), false));
        this.hoverFocusAlpha = alpha;
        // The node id is the whole payload of a NodeHit, so the stripped target is "no hit".
        return HitTarget.none();
    }

    /** Physical-graph variant of {@link #beginHoverFocusFade(MapDimensionGraph, VisualRouteMapGraph, ViewportState)}. */
    private HitTarget beginHoverFocusFade(PhysicalRouteMapGraph graph, ViewportState viewport) {
        this.trackHoverFocusChange();
        this.hoverFocusRings = List.of();
        this.hoverFocusAlpha = 1.0F;
        if (ClientSafetyOptions.reducePhotosensitivityRisk() || !(this.hover instanceof HitTarget.PhysicalNodeHit(var hoveredNodeId))) {
            return this.hover;
        }
        PhysicalMapNode hovered = graph.node(hoveredNodeId).orElse(null);
        if (hovered == null) {
            return this.hover;
        }
        float alpha = this.hoverFocusRamp();
        if (alpha >= 1.0F) {
            return this.hover;
        }
        // Every node the renderer would ring for this hover (the direct target, the
        // same station group's platforms, a fold counterpart living in the same graph)
        // fades together, so nothing pops in when the renderer takes over at full alpha.
        Optional<UUID> stationGroup = hovered.stationGroupId();
        Optional<PipeAnchorId> foldCounterpart = hovered.foldAnchorId().flatMap(ClientPipeNetworkCache::globalFoldCounterpart);
        List<HoverFocusRing> rings = new ArrayList<>();
        for (PhysicalMapNode node : graph.nodes()) {
            boolean ringed = node.id().equals(hovered.id())
                    || stationGroup.isPresent() && node.stationGroupId().filter(stationGroup.get()::equals).isPresent()
                    || foldCounterpart.isPresent() && node.foldAnchorId().filter(foldCounterpart.get()::equals).isPresent();
            if (ringed) {
                rings.add(new HoverFocusRing(
                        FullRouteMapRenderer.worldToScreen(node.worldX(), node.worldZ(), viewport, this.mapRect),
                        physicalNodeRingRadius(node.kind(), viewport.zoom()),
                        node.kind() == PhysicalNodeKind.FOLD_ANCHOR));
            }
        }
        this.hoverFocusRings = rings;
        this.hoverFocusAlpha = alpha;
        // The node id is the whole payload of a PhysicalNodeHit, so the stripped target is "no hit".
        return HitTarget.none();
    }

    /** Draws the fade-in focus rings collected by {@code beginHoverFocusFade} this frame. */
    private void renderHoverFocusFade(GuiGraphicsExtractor graphics) {
        if (this.hoverFocusRings.isEmpty() || this.hoverFocusAlpha >= 1.0F) {
            return;
        }
        int halo = fadeAlpha(FullRouteMapConfig.MAP_FOCUS_HALO, this.hoverFocusAlpha);
        int ring = fadeAlpha(FullRouteMapConfig.MAP_FOCUS_RING, this.hoverFocusAlpha);
        for (HoverFocusRing focus : this.hoverFocusRings) {
            if (focus.diamond()) {
                SmoothGuiPrimitives.diamond(graphics, focus.center(), focus.radius() + 5.0D, halo);
                SmoothGuiPrimitives.diamond(graphics, focus.center(), focus.radius() + 2.0D, ring);
            } else {
                SmoothGuiPrimitives.circle(graphics, focus.center(), focus.radius() + 5.0D, halo);
                SmoothGuiPrimitives.circle(graphics, focus.center(), focus.radius() + 2.0D, ring);
            }
        }
    }

    private void trackHoverFocusChange() {
        Object key = switch (this.hover) {
            case HitTarget.NodeHit(var nodeId) -> nodeId;
            case HitTarget.PhysicalNodeHit(var nodeId) -> "physical:" + nodeId;
            default -> "";
        };
        if (!key.equals(this.hoverFocusKey)) {
            this.hoverFocusKey = key;
            this.hoverFocusChangedAtMillis = System.currentTimeMillis();
        }
    }

    private float hoverFocusRamp() {
        return Math.max(0.0F, Math.min(1.0F, (System.currentTimeMillis() - this.hoverFocusChangedAtMillis) / (float) HOVER_FOCUS_FADE_IN_MILLIS));
    }

    /**
     * Fade-in opacity for a tooltip identified by {@code key}. The ramp restarts when
     * the key changes or when the tooltip skipped at least one frame (it reappeared).
     */
    private float tooltipFadeOpacity(String key) {
        if (ClientSafetyOptions.reducePhotosensitivityRisk()) {
            return 1.0F;
        }
        long now = System.currentTimeMillis();
        if (!key.equals(this.tooltipFadeKey) || this.tooltipFadeLastSeenFrame < this.tooltipFadeFrameCounter - 1L) {
            this.tooltipFadeKey = key;
            this.tooltipFadeShownAtMillis = now;
        }
        this.tooltipFadeLastSeenFrame = this.tooltipFadeFrameCounter;
        return Math.min(1.0F, (now - this.tooltipFadeShownAtMillis) / (float) TOOLTIP_FADE_IN_MILLIS);
    }

    /** Scales a color's own alpha channel by {@code factor} (0..1). */
    private static int fadeAlpha(int color, float factor) {
        return SPSGui.withAlpha(color, Math.round((color >>> 24) * factor));
    }

    // Ring radii mirror FullRouteMapRenderer's node sizing (iconScale / nodeRadius /
    // physicalNodeRadius). Exact for the flat camera; with the perspective camera
    // tilted the overlay radius is approximate for the fade's 120ms, after which the
    // renderer's exact ring takes over.
    private static double mapNodeRingRadius(MapNode node, double zoom) {
        double scale = Math.max(0.35D, Math.min(1.0D, zoom));
        double base = node.kind() == NodeKind.CLUSTER || node.kind() == NodeKind.DEEP_CLUSTER ? FullRouteMapConfig.CLUSTER_RADIUS_PX : FullRouteMapConfig.NODE_RADIUS_PX;
        double min = node.kind() == NodeKind.CLUSTER || node.kind() == NodeKind.DEEP_CLUSTER ? 4.0D : 2.0D;
        return Math.max(min, (int) Math.round(base * scale));
    }

    private static double physicalNodeRingRadius(PhysicalNodeKind kind, double zoom) {
        double scale = Math.max(0.48D, Math.min(1.45D, 0.62D + zoom * 0.12D));
        double base = kind == PhysicalNodeKind.FOLD_ANCHOR ? FullRouteMapConfig.NODE_RADIUS_PX + 1.0D : FullRouteMapConfig.NODE_RADIUS_PX - 1.0D;
        return Math.max(3.0D, base * scale);
    }

    private record HoverFocusRing(Vec2 center, double radius, boolean diamond) {}

    private void renderDeferredCardOverlays() {
        for (Runnable overlay : this.deferredCardOverlays) {
            overlay.run();
        }
    }

    private void registerScopedClick(ClickAction action, boolean priority) {
        List<ClickAction> target = this.renderingCardKey
                .map(key -> this.cardClickActions.computeIfAbsent(key, ignored -> new ArrayList<>()))
                .orElse(this.backgroundClickActions);
        if (priority) {
            target.add(0, action);
        } else {
            target.add(action);
        }
    }

    private void updateMapChromeBounds() {
        this.mapChromeBounds.clear();
        this.cameraCompassBounds = this.computeCameraCompassBounds();
        this.schematicLegendBounds = this.computeSchematicLegendBounds();
        this.layoutModeStripBounds = this.computeLayoutModeStripBounds();
        this.dimensionChipBounds = this.computeDimensionChipBounds();
        this.searchControlBounds = this.computeSearchControlBounds();
        this.searchResultsBounds = this.computeSearchResultsBounds();
        this.navigationDrawerBounds = this.computeNavigationDrawerBounds();
        this.activeNavigationPillBounds = this.computeActiveNavigationPillBounds();
        this.dimensionMenuBounds = this.computeDimensionMenuBounds();
        this.diagnosticsBadgeBounds = this.computeDiagnosticsBadgeBounds();
        this.diagnosticsPanelBounds = this.computeDiagnosticsPanelBounds();
        this.mapChromeBounds.add(this.layoutModeStripBounds);
        if (this.cameraCompassBounds.width() > 0 && this.cameraCompassBounds.height() > 0) {
            this.mapChromeBounds.add(this.cameraCompassBounds);
        }
        if (this.schematicLegendBounds.width() > 0 && this.schematicLegendBounds.height() > 0) {
            this.mapChromeBounds.add(this.schematicLegendBounds);
        }
        this.mapChromeBounds.add(this.dimensionChipBounds);
        this.mapChromeBounds.add(this.searchControlBounds);
        if (this.searchResultsBounds.width() > 0 && this.searchResultsBounds.height() > 0) {
            this.mapChromeBounds.add(this.searchResultsBounds);
        }
        if (this.navigationDrawerBounds.width() > 0 && this.navigationDrawerBounds.height() > 0) {
            this.mapChromeBounds.add(this.navigationDrawerBounds);
        }
        if (this.activeNavigationPillBounds.width() > 0 && this.activeNavigationPillBounds.height() > 0) {
            this.mapChromeBounds.add(this.activeNavigationPillBounds);
        }
        if (this.dimensionMenuBounds.width() > 0 && this.dimensionMenuBounds.height() > 0) {
            this.mapChromeBounds.add(this.dimensionMenuBounds);
        }
        if (this.diagnosticsBadgeBounds.width() > 0 && this.diagnosticsBadgeBounds.height() > 0) {
            this.mapChromeBounds.add(this.diagnosticsBadgeBounds);
        }
        if (this.diagnosticsPanelOpen && this.diagnosticsPanelBounds.width() > 0 && this.diagnosticsPanelBounds.height() > 0) {
            this.mapChromeBounds.add(this.diagnosticsPanelBounds);
        }
    }

    private void renderDimensionControl(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        SPSGui.Rect bounds = this.dimensionChipBounds;
        boolean hovered = bounds.contains(mouseX, mouseY);
        FullMapUi.toolbarPanel(graphics, bounds);
        SPSGui.icon(graphics, new SPSGui.Rect(bounds.x() + 4, bounds.y() + 3, 16, 16), SPSGui.Icon.MAP, hovered || this.dimensionMenuOpen ? SPSGui.INFO : FullMapTheme.TEXT_SECONDARY);
        String label = this.activeDimension == null ? "?" : dimensionLabel(this.activeDimension);
        int textWidth = Math.max(24, bounds.width() - 42);
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, label, Math.round(textWidth / FullMapTheme.TYPE_BODY)), bounds.x() + 22, bounds.y() + 7, this.dimensionMenuOpen ? SPSGui.INFO : FullMapTheme.TEXT_PRIMARY, FullMapTheme.TYPE_BODY);
        SPSGui.smallText(graphics, this.font, this.dimensionMenuOpen ? "^" : "v", bounds.right() - 13, bounds.y() + 8, FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_META);
        this.addClick(bounds, () -> {
            this.dimensionMenuOpen = !this.dimensionMenuOpen;
            this.searchExpanded = false;
            if (this.searchBox != null && this.searchBox.getValue().isBlank()) {
                this.searchBox.setFocused(false);
            }
        }, Component.translatable("screen.superpipeslide.full_map.dimension_menu"));
    }

    private static final int DIAGNOSTIC_GROUP_HEIGHT = 34;

    private List<MapBuildDiagnostic> activeDiagnostics() {
        if (this.activeDimension == null) {
            return List.of();
        }
        List<MapBuildDiagnostic> result = new ArrayList<>(FullRouteMapCache.graph(this.activeDimension).map(MapDimensionGraph::diagnostics).orElse(List.of()));
        if (FullRouteMapCache.layoutMode().physical()) {
            result.addAll(FullRouteMapCache.physicalGraph(this.activeDimension).map(PhysicalRouteMapGraph::diagnostics).orElse(List.of()));
        }
        return result;
    }

    private SPSGui.Rect computeDiagnosticsBadgeBounds() {
        if (this.activeDiagnostics().isEmpty() || this.dimensionChipBounds.width() <= 0) {
            return new SPSGui.Rect(0, 0, 0, 0);
        }
        return new SPSGui.Rect(this.dimensionChipBounds.right() + 4, this.dimensionChipBounds.y(), 40, this.dimensionChipBounds.height());
    }

    private SPSGui.Rect computeDiagnosticsPanelBounds() {
        if (!this.diagnosticsPanelOpen || this.activeDiagnostics().isEmpty()) {
            return new SPSGui.Rect(0, 0, 0, 0);
        }
        int height = Math.min(280, 30 + this.diagnosticsGroups().size() * DIAGNOSTIC_GROUP_HEIGHT + 8);
        return new SPSGui.Rect(this.dimensionChipBounds.x(), this.dimensionChipBounds.bottom() + 4, 280, height);
    }

    private List<DiagnosticGroup> diagnosticsGroups() {
        Map<String, DiagnosticGroup> groups = new LinkedHashMap<>();
        for (MapBuildDiagnostic diagnostic : this.activeDiagnostics()) {
            groups.computeIfAbsent(diagnostic.type().name() + "|" + diagnostic.message(), ignored -> new DiagnosticGroup(diagnostic.type(), diagnostic.message())).count++;
        }
        return List.copyOf(groups.values());
    }

    private static String diagnosticNameKey(DiagnosticType type) {
        return "screen.superpipeslide.full_map.diagnostic." + type.name().toLowerCase(Locale.ROOT) + ".name";
    }

    private static String diagnosticGuideKey(DiagnosticType type) {
        return "screen.superpipeslide.full_map.diagnostic." + type.name().toLowerCase(Locale.ROOT) + ".guide";
    }

    private static final class DiagnosticGroup {
        private final DiagnosticType type;
        private final String message;
        private int count;

        private DiagnosticGroup(DiagnosticType type, String message) {
            this.type = type;
            this.message = message;
        }
    }

    private void renderDiagnosticsControl(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<MapBuildDiagnostic> diagnostics = this.activeDiagnostics();
        if (diagnostics.isEmpty()) {
            return;
        }
        SPSGui.Rect bounds = this.diagnosticsBadgeBounds;
        boolean hovered = bounds.contains(mouseX, mouseY);
        FullMapUi.toolbarPanel(graphics, bounds);
        SPSGui.smallText(graphics, this.font, "⚠ " + diagnostics.size(), bounds.x() + 6, bounds.y() + 7, FullMapTheme.WARNING, FullMapTheme.TYPE_BODY);
        this.addClick(bounds, () -> {
            this.diagnosticsPanelOpen = !this.diagnosticsPanelOpen;
            this.dimensionMenuOpen = false;
        }, Component.translatable("screen.superpipeslide.full_map.diagnostics.badge", diagnostics.size()));
    }

    private void renderDiagnosticsPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!this.diagnosticsPanelOpen || this.diagnosticsPanelBounds.width() <= 0 || this.diagnosticsPanelBounds.height() <= 0) {
            return;
        }
        SPSGui.Rect bounds = this.diagnosticsPanelBounds;
        graphics.fill(bounds.x() + 2, bounds.y() + 3, bounds.right() + 2, bounds.bottom() + 3, FullMapTheme.SHADOW);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), FullMapTheme.SURFACE_CARD_ACTIVE);
        graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), FullMapTheme.BORDER_ACTIVE);
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.y() + 2, FullMapTheme.INNER_HIGHLIGHT_STRONG);
        SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.full_map.diagnostics.title").getString(), bounds.x() + 8, bounds.y() + 7, FullMapTheme.TEXT_PRIMARY, FullMapTheme.TYPE_BODY);

        List<DiagnosticGroup> groups = this.diagnosticsGroups();
        SPSGui.Rect list = new SPSGui.Rect(bounds.x() + 6, bounds.y() + 22, bounds.width() - 12, bounds.height() - 30);
        int contentHeight = groups.size() * DIAGNOSTIC_GROUP_HEIGHT;
        double maxScroll = Math.max(0.0D, contentHeight - list.height());
        this.diagnosticsScroll = Math.max(0.0D, Math.min(this.diagnosticsScroll, maxScroll));
        graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        int y = list.y() + 2 - (int) Math.round(this.diagnosticsScroll);
        for (DiagnosticGroup group : groups) {
            if (y > list.bottom()) {
                break;
            }
            if (y + DIAGNOSTIC_GROUP_HEIGHT > list.y()) {
                String name = Component.translatable(diagnosticNameKey(group.type)).getString()
                        + (group.count > 1 ? Component.translatable("screen.superpipeslide.full_map.diagnostics.count_suffix", group.count).getString() : "");
                SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, name, Math.round((list.width() - 6) / FullMapTheme.TYPE_BODY)), list.x() + 2, y, FullMapTheme.WARNING, FullMapTheme.TYPE_BODY);
                SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, Component.translatable(diagnosticGuideKey(group.type)).getString(), Math.round((list.width() - 6) / FullMapTheme.TYPE_META)), list.x() + 2, y + 11, FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_META);
                SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, group.message, Math.round((list.width() - 6) / FullMapTheme.TYPE_TINY)), list.x() + 2, y + 21, FullMapTheme.TEXT_DISABLED, FullMapTheme.TYPE_TINY);
            }
            y += DIAGNOSTIC_GROUP_HEIGHT;
        }
        graphics.disableScissor();
        if (maxScroll > 0.0D) {
            FullMapScrollBar.Binding binding = new FullMapScrollBar.Binding(
                    "diagnostics",
                    list,
                    contentHeight,
                    this.diagnosticsScroll,
                    maxScroll,
                    FullMapScrollBar.INFO,
                    true,
                    value -> this.diagnosticsScroll = value);
            this.registerScrollBar(binding);
            FullMapScrollBar.render(graphics, binding);
        }
    }

    private void renderDimensionMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!this.dimensionMenuOpen || this.dimensionMenuBounds.width() <= 0 || this.dimensionMenuBounds.height() <= 0) {
            return;
        }
        SPSGui.Rect bounds = this.dimensionMenuBounds;
        graphics.fill(bounds.x() + 2, bounds.y() + 3, bounds.right() + 2, bounds.bottom() + 3, FullMapTheme.SHADOW);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), FullMapTheme.SURFACE_CARD_ACTIVE);
        graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), FullMapTheme.BORDER_ACTIVE);
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.y() + 2, FullMapTheme.INNER_HIGHLIGHT_STRONG);

        int y = bounds.y() + 4;
        int rowHeight = 24;
        for (ResourceKey<Level> dimension : sortedDimensions()) {
            if (y + rowHeight > bounds.bottom() - 3) {
                break;
            }
            String label = dimensionLabel(dimension);
            SPSGui.Rect row = new SPSGui.Rect(bounds.x() + 4, y, bounds.width() - 8, rowHeight - 2);
            boolean selected = dimension.equals(this.activeDimension);
            boolean hovered = row.contains(mouseX, mouseY);
            graphics.fill(row.x(), row.y(), row.right(), row.bottom(), selected ? FullMapTheme.SURFACE_CONTROL_SELECTED : hovered ? FullMapTheme.HIGHLIGHT_SOFT : FullMapTheme.SURFACE_CONTROL);
            graphics.outline(row.x(), row.y(), row.width(), row.height(), selected ? FullMapTheme.BORDER_SELECTED : FullMapTheme.BORDER);
            SPSGui.text(graphics, this.font, SPSGui.ellipsize(this.font, label, row.width() - 44), row.x() + 6, row.y() + 3, selected ? SPSGui.INFO : FullMapTheme.TEXT_PRIMARY);
            String summary = Component.translatable("screen.superpipeslide.full_map.dimension_summary", stationCountInDimension(dimension), routeCountInDimension(dimension)).getString();
            SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, summary, Math.round(52 / FullMapTheme.TYPE_TINY)), row.right() - 50, row.y() + 7, FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_TINY);
            this.addClick(row, () -> {
                this.switchDimension(dimension, false);
                this.dimensionMenuOpen = false;
            }, Component.translatable("screen.superpipeslide.full_map.switch_dimension", label));
            y += rowHeight;
        }
    }

    private void renderSearchControl(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.searchBox == null) {
            return;
        }
        SPSGui.Rect bounds = this.searchControlBounds;

        int clearSize = 16;
        boolean hasText = !this.searchBox.getValue().isEmpty();
        this.searchBox.setX(bounds.x() + 25);
        this.searchBox.setY(bounds.y() + 8);
        this.searchBox.setWidth(Math.max(32, bounds.width() - 36 - (hasText ? clearSize + 4 : 0)));
        graphics.fill(bounds.x() + 2, bounds.y() + 3, bounds.right() + 2, bounds.bottom() + 3, FullMapTheme.SHADOW);
        FullMapUi.toolbarPanel(graphics, bounds);
        SPSGui.Rect icon = new SPSGui.Rect(bounds.x() + 6, bounds.y() + 6, 14, 14);
        SPSGui.icon(graphics, icon, SPSGui.Icon.SEARCH, this.searchBox.isFocused() ? SPSGui.INFO : FullMapTheme.TEXT_SECONDARY);
        if (this.searchBox.getValue().isEmpty()) {
            SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.full_map.search_hint"), this.searchBox.getX() + 2, this.searchBox.getY() + 3, SPSGui.TEXT_MUTED);
        }
        if (hasText) {
            SPSGui.Rect clear = new SPSGui.Rect(bounds.right() - 21, bounds.y() + 6, clearSize, clearSize);
            FullMapUi.iconButton(graphics, clear, clear.contains(mouseX, mouseY), false, false, SPSGui.Icon.CLOSE);
            this.addPriorityClick(clear, () -> {
                this.searchBox.setValue("");
                this.navigationResultScroll = 0.0D;
                this.focusSearch();
            }, Component.translatable("screen.superpipeslide.full_map.search_clear"));
        }
        this.addClick(icon, this::focusSearch, Component.translatable("screen.superpipeslide.full_map.search_hint"));
    }

    /**
     * Renders the card stack bottom-to-top. Window bounds come from the card manager's
     * per-frame pass (see {@code MapCardManager#updateWindowBounds}), which runs ahead
     * of any hover testing so the topmost-card hit test never falls through a card on
     * the first frame after it opens.
     */
    private void renderCards(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Optional<String> hoveredCard = this.contextPicker.isEmpty() && !this.mapPopoverBlocks(mouseX, mouseY) ? this.cardManager.topmostCardAt(mouseX, mouseY) : Optional.empty();
        List<MapCard> cards = this.cardManager.cards();
        for (int i = 0; i < cards.size(); i++) {
            MapCard card = cards.get(i);
            SPSGui.Rect bounds = this.cardManager.windowBounds(card.windowKey());
            if (bounds == null) {
                continue;
            }
            boolean active = i == cards.size() - 1;
            boolean hoverable = hoveredCard.filter(card.windowKey()::equals).isPresent();
            this.renderCard(graphics, card, bounds, active, hoverable, mouseX, mouseY);
        }
    }

    private void renderCard(GuiGraphicsExtractor graphics, MapCard card, SPSGui.Rect bounds, boolean active, boolean hoverable, int mouseX, int mouseY) {
        this.renderingCardKey = Optional.of(card.windowKey());
        try {
            int scopedMouseX = hoverable ? mouseX : Integer.MIN_VALUE;
            int scopedMouseY = hoverable ? mouseY : Integer.MIN_VALUE;
            FullMapUi.cardFrame(graphics, bounds, active);
            switch (card.kind()) {
                case ROUTE_LINE -> this.renderRouteLineCard(graphics, card, bounds, active, hoverable, scopedMouseX, scopedMouseY);
                case CLUSTER -> this.renderClusterCard(graphics, card, bounds, active, hoverable, scopedMouseX, scopedMouseY);
                case STATION -> this.renderStationCard(graphics, card, bounds, active, hoverable, scopedMouseX, scopedMouseY);
                case FOLD_PEEK -> this.renderFoldPeekCard(graphics, card, bounds, active, hoverable, scopedMouseX, scopedMouseY);
                case DEEP_CLUSTER -> this.renderDeepClusterCard(graphics, card, bounds, active, hoverable, scopedMouseX, scopedMouseY);
            }
            SPSGui.Rect close = new SPSGui.Rect(bounds.right() - 20, bounds.y() + 4, FullMapTheme.ICON_BUTTON_SMALL, FullMapTheme.ICON_BUTTON_SMALL);
            FullMapUi.iconButton(graphics, close, hoverable && close.contains(scopedMouseX, scopedMouseY), false, false, SPSGui.Icon.CLOSE);
            this.addPriorityClick(close, () -> this.closeCard(card.windowKey()), Component.translatable("screen.superpipeslide.full_map.close_card"));
        } finally {
            this.renderingCardKey = Optional.empty();
        }
    }

    private void renderRouteLineCard(GuiGraphicsExtractor graphics, MapCard card, SPSGui.Rect bounds, boolean active, boolean hoverable, int mouseX, int mouseY) {
        RouteLineCardState state = card.routeLineState().orElse(RouteLineCardState.create(card.id(), Optional.empty(), card.levelKey()));
        Optional<RouteLine> line = ClientRouteDataCache.routeLine(state.routeLineId());
        if (line.isEmpty()) {
            SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.route.missing"), bounds.x() + 10, bounds.y() + 10, SPSGui.DANGER);
            return;
        }
        List<RouteLayout> layouts = ClientRouteDataCache.routeLayoutsForLine(line.get().id());
        if (layouts.isEmpty()) {
            SPSGui.colorStripe(graphics, bounds.x(), bounds.y(), bounds.height(), line.get().themeColors());
            DisplayNameStack name = FullMapText.displayNameStack(line.get());
            FullMapUi.drawNameStack(graphics, this.font, name, bounds.x() + 8, bounds.y() + 5, bounds.width() - FullMapTheme.CARD_HEADER_TITLE_RESERVE, FullMapTheme.TEXT_PRIMARY, FullMapTheme.TEXT_MUTED, 1.0F, FullMapTheme.TYPE_META, 0);
            SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.layout.none"), bounds.x() + 8, bounds.y() + (name.hasSecondary() ? 35 : 28), SPSGui.TEXT_MUTED);
            return;
        }
        UUID selectedLayoutId = state.selectedLayoutId().filter(id -> layouts.stream().anyMatch(layout -> layout.id().equals(id))).orElse(layouts.getFirst().id());
        RouteLayout selectedLayout = layouts.stream().filter(layout -> layout.id().equals(selectedLayoutId)).findFirst().orElse(layouts.getFirst());
        RouteLineCardState effectiveState = state.selectedLayoutId().filter(selectedLayout.id()::equals).isPresent() ? state : state.withSelectedLayout(selectedLayout.id());
        RouteCardViewMode viewMode = effectiveState.viewMode();
        RouteCardGraphBundle graphBundle = this.routeCardGraph(line.get(), selectedLayout, viewMode);
        RouteCardSemanticGraph stopListGraph = graphBundle.stopListGraph();
        RouteCardSemanticGraph semanticGraph = graphBundle.semanticGraph();
        SPSGui.Rect routeMapBounds = routeCardMapBounds(bounds);
        RouteCardVisualGraph visualGraph = graphBundle.visualGraph();
        RouteCardViewport viewport = effectiveState.viewport().orElseGet(() -> RouteLineCardRenderer.fitViewport(visualGraph.bounds(), routeMapBounds));
        String stripKey = lineStripKey(card);
        double scroll = this.lineStripScrolls.getOrDefault(stripKey, 0.0D);
        String cardKey = card.windowKey();
        double stopListScroll = this.routeCardStopListScrolls.getOrDefault(cardKey, 0.0D);
        RouteLineCardRenderResult result = this.routeLineCardRenderer.render(graphics, this.font, line.get(), selectedLayout, layouts, viewMode, stopListGraph, semanticGraph, visualGraph, viewport, bounds, scroll, stopListScroll, hoverable, mouseX, mouseY);
        this.lineStripRegions.put(stripKey, result.layoutStripBounds());
        this.lineStripMaxScrolls.put(stripKey, result.layoutMaxScroll());
        this.routeCardMapRegions.put(cardKey, result.mapBounds());
        this.routeCardResolvedViewports.put(cardKey, viewport);
        this.routeCardStopListRegions.put(cardKey, result.stopListBounds());
        this.routeCardStopListMaxScrolls.put(cardKey, result.stopListMaxScroll());
        List<SPSGui.Rect> controls = new ArrayList<>();
        controls.add(result.viewModeStripBounds());
        result.viewModeChips().stream().map(ViewModeChipHit::bounds).forEach(controls::add);
        controls.add(result.fitViewportBounds());
        this.routeCardControlRegions.put(cardKey, controls);
        if (scroll > result.layoutMaxScroll()) {
            this.lineStripScrolls.put(stripKey, result.layoutMaxScroll());
        }
        if (stopListScroll > result.stopListMaxScroll()) {
            this.routeCardStopListScrolls.put(cardKey, result.stopListMaxScroll());
        }
        if (active) {
            for (LayoutChipHit chip : result.layoutChips()) {
                if (chip.bounds().width() > 0 && chip.bounds().height() > 0) {
                    this.addClick(chip.bounds(), () -> this.replaceTop(card.withRouteLineState(effectiveState.withSelectedLayout(chip.layoutId()))), Component.translatable("screen.superpipeslide.full_map.select_layout", chip.label()));
                }
            }
            for (ViewModeChipHit chip : result.viewModeChips()) {
                this.addPriorityClick(chip.bounds(), () -> {
                    this.cardManager.updateRouteCardState(card.windowKey(), effectiveState.withViewMode(chip.viewMode()));
                    // Instant feedback for the mode flip, mirroring the main-map layout-mode toast.
                    this.toast(Component.translatable(chip.viewMode().translationKey()).getString());
                }, routeCardViewModeTooltip(chip.viewMode()));
            }
            if (result.hover().kind() != RouteLineCardHitKind.NONE) {
                this.addClick(result.mapBounds(), () -> this.handleRouteCardHit(card.withRouteLineState(effectiveState), semanticGraph, result.hover(), result.mapBounds(), mouseX, mouseY), Component.empty());
                this.addClick(result.stopListBounds(), () -> this.handleRouteCardHit(card.withRouteLineState(effectiveState), stopListGraph, result.hover(), result.stopListBounds(), mouseX, mouseY), Component.empty());
            }
            this.addPriorityClick(result.fitViewportBounds(), () -> this.cardManager.updateRouteCardState(card.windowKey(), effectiveState.fitViewport()), Component.translatable("screen.superpipeslide.full_map.route_card.fit_view"));
            this.addClick(result.locateFirstBounds(), () -> this.locateFirstStation(selectedLayout), Component.translatable("screen.superpipeslide.full_map.locate_first_station"));
            this.addClick(result.locateLayoutBounds(), () -> this.locateRouteCardLayout(line.get(), semanticGraph), Component.translatable("screen.superpipeslide.full_map.route_card.locate_layout"));
        }
        if (hoverable) {
            RouteCardSemanticGraph hoverGraph = result.mapBounds().contains(mouseX, mouseY) ? semanticGraph : stopListGraph;
            this.deferredCardOverlays.add(() -> result.tooltipOverride()
                    .ifPresentOrElse(
                            tooltip -> this.tooltipPlacement = FullMapTooltipCard.renderComponent(graphics, this.font, this.screenBounds(), mouseX, mouseY, this.tooltipPlacement, tooltip, this.tooltipFadeOpacity(tooltip.getString())),
                            () -> FullMapTooltipCard.withScopedOpacity(this.tooltipFadeOpacity("routeCard:" + result.hover()), () -> this.routeLineCardRenderer.renderHoverTooltip(graphics, this.font, hoverGraph, visualGraph, result.hover(), this.screenBounds(), List.of(), mouseX, mouseY))));
        }
    }

    private RouteCardGraphBundle routeCardGraph(RouteLine line, RouteLayout layout, RouteCardViewMode viewMode) {
        RouteCardGraphCacheKey key = new RouteCardGraphCacheKey(
                ClientRouteDataCache.revision(),
                ClientPipeNetworkCache.aggregateRevision(),
                line.id(),
                layout.id(),
                viewMode);
        return this.routeCardGraphCache.computeIfAbsent(key, ignored -> this.buildRouteCardGraph(line, layout, viewMode));
    }

    private RouteCardGraphBundle buildRouteCardGraph(RouteLine line, RouteLayout layout, RouteCardViewMode viewMode) {
        RouteCardSemanticGraph stopListGraph = this.routeCardSemanticBuilder.build(line, layout);
        RouteCardSemanticGraph semanticGraph = switch (viewMode) {
            case PHYSICAL -> this.routeCardSemanticBuilder.buildPlatform(line, layout);
            case PRACTICAL -> this.routeCardSemanticBuilder.build(line, layout);
            case SCHEMATIC -> this.routeCardSemanticBuilder.buildSchematic(line, layout);
        };
        RouteCardVisualGraph visualGraph = switch (viewMode) {
            case PHYSICAL -> this.routeCardPhysicalLayoutBuilder.build(semanticGraph);
            case PRACTICAL -> this.routeCardLayoutSolver.solvePractical(semanticGraph);
            case SCHEMATIC -> this.routeCardLayoutSolver.solveSchematic(semanticGraph);
        };
        return new RouteCardGraphBundle(stopListGraph, semanticGraph, visualGraph);
    }

    private ClusterCardGraphBundle clusterCardGraph(MapDimensionGraph graph, MapCluster cluster, ClusterCardProfile profile) {
        ClusterCardGraphCacheKey key = new ClusterCardGraphCacheKey(
                ClientRouteDataCache.revision(),
                ClientPipeNetworkCache.aggregateRevision(),
                cluster.nodeId(),
                profile);
        return this.clusterCardGraphCache.computeIfAbsent(key, ignored -> this.buildClusterCardGraph(graph, cluster, profile));
    }

    private ClusterCardGraphBundle buildClusterCardGraph(MapDimensionGraph graph, MapCluster cluster, ClusterCardProfile profile) {
        ClusterCardSemanticGraph semanticGraph = this.clusterCardSemanticBuilder.build(graph, cluster, profile);
        ClusterCardVisualGraph visualGraph = this.clusterCardLayoutSolver.solve(semanticGraph);
        return new ClusterCardGraphBundle(semanticGraph, visualGraph);
    }

    private static SPSGui.Rect routeCardMapBounds(SPSGui.Rect bounds) {
        return RouteLineCardRenderer.mapBoundsForCard(bounds);
    }

    private static boolean isClusterFocusCard(MapCard card) {
        return card.kind() == CardKind.CLUSTER || card.kind() == CardKind.DEEP_CLUSTER;
    }

    private static SPSGui.Rect cardHeaderBounds(SPSGui.Rect bounds) {
        return new SPSGui.Rect(bounds.x(), bounds.y(), Math.max(1, bounds.width() - 22), FullMapTheme.CARD_HEADER_WITH_STACK);
    }

    private void renderClusterCard(GuiGraphicsExtractor graphics, MapCard card, SPSGui.Rect bounds, boolean active, boolean hoverable, int mouseX, int mouseY) {
        MapDimensionGraph graph = this.graphForCard(card).orElse(null);
        MapCluster cluster = graph == null ? null : findCluster(graph, card.id(), NodeKind.CLUSTER);
        if (graph == null || cluster == null) {
            SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.full_map.cluster_missing"), bounds.x() + 10, bounds.y() + 10, SPSGui.DANGER);
            return;
        }
        ClusterCardState state = card.clusterState().orElse(ClusterCardState.create());
        ClusterCardGraphBundle graphBundle = this.clusterCardGraph(graph, cluster, ClusterCardProfile.ORDINARY);
        ClusterCardSemanticGraph semanticGraph = graphBundle.semanticGraph();
        ClusterCardVisualGraph visualGraph = graphBundle.visualGraph();
        DisplayNameStack title = DisplayNameStack.of(cluster.label());
        Component meta = Component.translatable("screen.superpipeslide.full_map.cluster_summary", cluster.stationGroupIds().size(), dimensionLabel(cluster.levelKey()));
        FullMapUi.cardHeader(
                graphics,
                this.font,
                bounds,
                title,
                meta,
                active);
        this.renderClusterLocateButton(graphics, graph, cluster, bounds, active, hoverable, mouseX, mouseY);
        int pad = FullMapTheme.CARD_PADDING;
        int mapTop = bounds.y() + FullMapUi.cardHeaderHeight(title, meta) + 6;
        SPSGui.Rect map = new SPSGui.Rect(bounds.x() + pad, mapTop, bounds.width() - pad * 2, Math.max(72, bounds.bottom() - mapTop - 30));
        ClusterCardViewport viewport = state.viewport().orElseGet(() -> ClusterCardRenderer.fitViewport(visualGraph.bounds(), map));
        ClusterCardRenderResult result = this.clusterCardRenderer.render(graphics, this.font, semanticGraph, visualGraph, viewport, map, hoverable, mouseX, mouseY);
        String cardKey = card.windowKey();
        this.clusterCardMapRegions.put(cardKey, result.mapBounds());
        this.clusterCardFitRegions.put(cardKey, result.fitViewportBounds());
        this.clusterCardResolvedViewports.put(cardKey, viewport);
        if (active) {
            if (result.hover().kind() != ClusterCardHitKind.NONE) {
                this.addClick(result.mapBounds(), () -> this.handleClusterCardHit(graph, semanticGraph, result.hover(), result.mapBounds(), mouseX, mouseY), Component.empty());
            }
            this.addPriorityClick(result.fitViewportBounds(), () -> this.cardManager.updateClusterCardState(card.windowKey(), state.resetViewport()), Component.translatable("screen.superpipeslide.full_map.route_card.fit_view"));
        }
        int lineY = bounds.bottom() - 22;
        SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.full_map.lines_inside").getString(), bounds.x() + pad, lineY + 2, SPSGui.TEXT_MUTED, FullMapTheme.TYPE_TINY);
        this.renderLineStrip(graphics, card, cluster.routeLineIds(), new SPSGui.Rect(bounds.x() + 58, lineY, Math.max(32, bounds.width() - 136), 14), hoverable, mouseX, mouseY);
        String external = Component.translatable("screen.superpipeslide.full_map.external_edges", semanticGraph.externalEdgeCount()).getString();
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, external, Math.round(68 / FullMapTheme.TYPE_TINY)), bounds.right() - 70, lineY + 2, SPSGui.TEXT_MUTED, FullMapTheme.TYPE_TINY);
        if (hoverable) {
            this.deferredCardOverlays.add(() -> {
                List<SPSGui.Rect> tooltipAvoidRects = this.renderClusterCardHoverPreview(graphics, graph, semanticGraph, visualGraph, viewport, map, result.hover(), this.screenBounds(), mouseX, mouseY);
                FullMapTooltipCard.withScopedOpacity(this.tooltipFadeOpacity("clusterCard:" + result.hover()), () -> this.clusterCardRenderer.renderHoverTooltip(graphics, this.font, semanticGraph, result.hover(), this.screenBounds(), tooltipAvoidRects, mouseX, mouseY));
            });
        }
    }

    private void renderDeepClusterCard(GuiGraphicsExtractor graphics, MapCard card, SPSGui.Rect bounds, boolean active, boolean hoverable, int mouseX, int mouseY) {
        MapDimensionGraph graph = this.graphForCard(card).orElse(null);
        MapCluster cluster = graph == null ? null : findCluster(graph, card.id(), NodeKind.DEEP_CLUSTER);
        if (graph == null || cluster == null) {
            SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.full_map.cluster_missing"), bounds.x() + 10, bounds.y() + 10, SPSGui.DANGER);
            return;
        }
        ClusterCardState state = card.clusterState().orElse(ClusterCardState.create());
        ClusterCardGraphBundle graphBundle = this.clusterCardGraph(graph, cluster, ClusterCardProfile.DEEP);
        ClusterCardSemanticGraph semanticGraph = graphBundle.semanticGraph();
        ClusterCardVisualGraph visualGraph = graphBundle.visualGraph();
        // Header meta keeps the compact form; the center/span readout lives in the
        // renderer's footer_meta row, where it fits without truncating.
        DisplayNameStack title = DisplayNameStack.of("\u229b " + cluster.label());
        Component meta = Component.translatable("screen.superpipeslide.full_map.deep_cluster_summary_compact", cluster.stationGroupIds().size(), dimensionLabel(cluster.levelKey()));
        FullMapUi.cardHeader(
                graphics,
                this.font,
                bounds,
                title,
                meta,
                active);
        this.renderClusterLocateButton(graphics, graph, cluster, bounds, active, hoverable, mouseX, mouseY);

        int pad = FullMapTheme.CARD_PADDING;
        int mapTop = bounds.y() + FullMapUi.cardHeaderHeight(title, meta) + 6;
        SPSGui.Rect map = new SPSGui.Rect(bounds.x() + pad, mapTop, bounds.width() - pad * 2, Math.max(72, bounds.bottom() - mapTop - 30));
        ClusterCardViewport viewport = state.viewport().orElseGet(() -> ClusterCardRenderer.fitViewport(visualGraph.bounds(), map));
        ClusterCardRenderResult result = this.clusterCardRenderer.render(graphics, this.font, semanticGraph, visualGraph, viewport, map, hoverable, mouseX, mouseY);
        String cardKey = card.windowKey();
        this.clusterCardMapRegions.put(cardKey, result.mapBounds());
        this.clusterCardFitRegions.put(cardKey, result.fitViewportBounds());
        this.clusterCardResolvedViewports.put(cardKey, viewport);
        if (active) {
            if (result.hover().kind() != ClusterCardHitKind.NONE) {
                this.addClick(result.mapBounds(), () -> this.handleClusterCardHit(graph, semanticGraph, result.hover(), result.mapBounds(), mouseX, mouseY), Component.empty());
            }
            this.addPriorityClick(result.fitViewportBounds(), () -> this.cardManager.updateClusterCardState(card.windowKey(), state.resetViewport()), Component.translatable("screen.superpipeslide.full_map.route_card.fit_view"));
        }
        int lineY = bounds.bottom() - 22;
        SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.full_map.lines_inside").getString(), bounds.x() + pad, lineY + 2, SPSGui.TEXT_MUTED, FullMapTheme.TYPE_TINY);
        this.renderLineStrip(graphics, card, semanticGraph.routeLineIds(), new SPSGui.Rect(bounds.x() + 58, lineY, Math.max(32, bounds.width() - 136), 14), hoverable, mouseX, mouseY);
        String external = Component.translatable("screen.superpipeslide.full_map.external_edges", semanticGraph.externalEdgeCount()).getString();
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, external, Math.round(68 / FullMapTheme.TYPE_TINY)), bounds.right() - 70, lineY + 2, SPSGui.TEXT_MUTED, FullMapTheme.TYPE_TINY);
        if (hoverable) {
            this.deferredCardOverlays.add(() -> {
                List<SPSGui.Rect> tooltipAvoidRects = this.renderClusterCardHoverPreview(graphics, graph, semanticGraph, visualGraph, viewport, map, result.hover(), this.screenBounds(), mouseX, mouseY);
                FullMapTooltipCard.withScopedOpacity(this.tooltipFadeOpacity("clusterCard:" + result.hover()), () -> this.clusterCardRenderer.renderHoverTooltip(graphics, this.font, semanticGraph, result.hover(), this.screenBounds(), tooltipAvoidRects, mouseX, mouseY));
            });
        }
    }

    /** Header button that jumps the main map to this cluster and closes the card. */
    private void renderClusterLocateButton(GuiGraphicsExtractor graphics, MapDimensionGraph graph, MapCluster cluster, SPSGui.Rect bounds, boolean active, boolean hoverable, int mouseX, int mouseY) {
        SPSGui.Rect locate = new SPSGui.Rect(bounds.right() - 38, bounds.y() + 4, FullMapTheme.ICON_BUTTON_SMALL, FullMapTheme.ICON_BUTTON_SMALL);
        FullMapUi.iconButton(graphics, locate, hoverable && locate.contains(mouseX, mouseY), false, false, SPSGui.Icon.LOCATE);
        if (active) {
            this.addPriorityClick(locate, () -> this.locateClusterOnMainMap(graph, cluster), Component.translatable("screen.superpipeslide.full_map.locate_station", cluster.label()));
        }
    }

    private void locateClusterOnMainMap(MapDimensionGraph graph, MapCluster cluster) {
        graph.node(cluster.nodeId()).ifPresent(this::locateNode);
        this.popCard();
    }

    private static List<Integer> routeLineColors(RouteLine line) {
        List<Integer> colors = line.themeColors().stream()
                .map(SPSGui::opaque)
                .toList();
        return colors.isEmpty() ? List.of(FullRouteMapConfig.MAP_TRUNK) : colors;
    }

    private void renderLineStrip(GuiGraphicsExtractor graphics, MapCard card, List<UUID> lineIds, SPSGui.Rect strip, boolean active, int mouseX, int mouseY) {
        if (card == null || strip.width() <= 0) {
            return;
        }
        String key = lineStripKey(card);
        this.lineStripRegions.put(key, strip);
        List<RouteLine> lines = lineIds.stream()
                .distinct()
                .map(ClientRouteDataCache::routeLine)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing((RouteLine line) -> FullMapText.displayNameStack(line).flat()))
                .toList();
        int contentWidth = 0;
        Map<UUID, Integer> widths = new LinkedHashMap<>();
        for (RouteLine line : lines) {
            String label = FullMapText.primaryName(line);
            int textWidth = (int) Math.ceil(this.font.width(label) * FullMapTheme.TYPE_META);
            int width = Math.min(FullRouteMapConfig.CARD_LINE_STRIP_CHIP_MAX_PX, Math.max(FullRouteMapConfig.CARD_LINE_STRIP_CHIP_MIN_PX, textWidth + 12));
            widths.put(line.id(), width);
            contentWidth += width + 3;
        }
        double maxScroll = Math.max(0, contentWidth - strip.width());
        this.lineStripMaxScrolls.put(key, maxScroll);
        double scroll = Math.max(0.0D, Math.min(this.lineStripScrolls.getOrDefault(key, 0.0D), maxScroll));
        this.lineStripScrolls.put(key, scroll);
        graphics.enableScissor(strip.x(), strip.y(), strip.right(), strip.bottom());
        int x = (int) Math.round(strip.x() - scroll);
        for (RouteLine line : lines) {
            int width = widths.getOrDefault(line.id(), 52);
            SPSGui.Rect chip = new SPSGui.Rect(x, strip.y() + 1, width, FullMapTheme.ROUTE_CHIP_HEIGHT);
            boolean hovered = active && chip.contains(mouseX, mouseY);
            String label = FullMapText.primaryName(line);
            FullMapUi.routeChip(graphics, this.font, chip, label, routeLineColors(line), hovered, false, line.id().hashCode());
            if (active && chip.right() >= strip.x() && chip.x() <= strip.right()) {
                SPSGui.Rect clipped = clipRect(chip, strip);
                if (clipped.width() > 0 && clipped.height() > 0) {
                    this.addClick(clipped, () -> this.pushCard(MapCard.routeLine(line.id(), Optional.empty(), card.levelKey())), Component.translatable("screen.superpipeslide.full_map.open_route_card", FullMapText.primaryName(line)));
                }
            }
            x += width + 3;
        }
        graphics.disableScissor();
        if (maxScroll > 0.0D) {
            int markerColor = SPSGui.withAlpha(SPSGui.TEXT_MUTED, 0xAA);
            if (scroll > 0.0D) {
                SPSGui.smallText(graphics, this.font, "<", strip.x() - 5, strip.y() + 4, markerColor, ROW_TITLE_TEXT_SCALE);
            }
            if (scroll < maxScroll) {
                SPSGui.smallText(graphics, this.font, ">", strip.right() + 1, strip.y() + 4, markerColor, ROW_TITLE_TEXT_SCALE);
            }
        }
    }

    private static String lineStripKey(MapCard card) {
        String routeState = card.routeLineState()
                .map(state -> state.routeLineId() + ":" + state.selectedLayoutId().map(UUID::toString).orElse(""))
                .orElse("");
        return card.kind() + ":" + card.id() + ":" + routeState + ":" + card.levelKey().map(level -> level.identifier().toString()).orElse("");
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

    private static MapCluster findCluster(MapDimensionGraph graph, UUID clusterId, NodeKind kind) {
        return graph.cluster(kind, clusterId).orElse(null);
    }

    private void renderStationCard(GuiGraphicsExtractor graphics, MapCard card, SPSGui.Rect bounds, boolean active, boolean hoverable, int mouseX, int mouseY) {
        Optional<StationGroup> station = ClientRouteDataCache.stationGroup(card.id());
        if (station.isEmpty()) {
            SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.station.missing"), bounds.x() + 8, bounds.y() + 8, SPSGui.DANGER);
            return;
        }
        DisplayNameStack stationName = FullMapText.displayNameStack(station.get());
        Component meta = Component.literal(dimensionLabel(station.get().levelKey()) + " " + station.get().stationBlockPos().toShortString());
        FullMapUi.cardHeader(
                graphics,
                this.font,
                bounds,
                stationName,
                meta,
                active);
        SPSGui.Rect transferEdit = new SPSGui.Rect(bounds.right() - 22, bounds.y() + 5, 16, 16);
        SPSGui.Rect navigate = new SPSGui.Rect(bounds.right() - 42, bounds.y() + 5, 16, 16);
        FullMapUi.iconButton(graphics, navigate, hoverable && navigate.contains(mouseX, mouseY), false, false, SPSGui.Icon.LOCATE);
        FullMapUi.iconButton(graphics, transferEdit, hoverable && transferEdit.contains(mouseX, mouseY), false, false, SPSGui.Icon.SPLIT);
        if (active) {
            this.addClick(navigate, () -> this.selectNavigationDestination(station.get().id(), true), Component.translatable("screen.superpipeslide.full_map.navigate_here"));
            this.addClick(transferEdit, () -> this.minecraft.setScreen(new StationTransferEditorScreen(station.get().id())), Component.translatable("screen.superpipeslide.station_transfer.open"));
        }
        List<PlatformStop> platforms = ClientRouteDataCache.platformStopsInStation(station.get().id());
        List<StationTransferLink> transferLinks = ClientRouteDataCache.stationTransferLinksForStation(station.get().id());
        int headerHeight = FullMapUi.cardHeaderHeight(stationName, meta);
        int contentY = bounds.y() + headerHeight + 7;
        SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.full_map.station_platforms", platforms.size()).getString(), bounds.x() + 8, contentY, SPSGui.TEXT_MUTED, FullMapTheme.TYPE_META);
        SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.full_map.station_transfers", transferLinks.size()).getString(), bounds.right() - 82, contentY, SPSGui.TEXT_MUTED, FullMapTheme.TYPE_META);
        contentY += 13;
        if (!transferLinks.isEmpty()) {
            int shownTransfers = Math.min(2, transferLinks.size());
            for (int i = 0; i < shownTransfers; i++) {
                StationTransferLink link = transferLinks.get(i);
                Optional<UUID> otherId = link.other(station.get().id());
                Optional<StationGroup> other = otherId.flatMap(ClientRouteDataCache::stationGroup);
                if (other.isEmpty()) {
                    continue;
                }
                SPSGui.Rect transfer = new SPSGui.Rect(bounds.x() + 8, contentY + i * 16, bounds.width() - 16, FullMapTheme.ROUTE_CHIP_HEIGHT);
                boolean hovered = active && hoverable && transfer.contains(mouseX, mouseY);
                String label = FullMapText.primaryName(other.get()) + " · " + dimensionLabel(other.get().levelKey());
                FullMapUi.routeChip(graphics, this.font, transfer, label, List.of(FullRouteMapConfig.MAP_TRANSFER_HINT), hovered, false, link.id().hashCode());
                if (active) {
                    this.addClick(transfer, () -> this.pushCard(MapCard.station(other.get().id(), other.get().levelKey())), Component.translatable("screen.superpipeslide.full_map.open_station_card", FullMapText.primaryName(other.get())));
                }
            }
            if (transferLinks.size() > shownTransfers) {
                SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.more_count", transferLinks.size() - shownTransfers).getString(), bounds.x() + 11, contentY + shownTransfers * 16 + 1, SPSGui.TEXT_MUTED, FullMapTheme.TYPE_TINY);
                contentY += shownTransfers * 16 + 11;
            } else {
                contentY += shownTransfers * 16 + 3;
            }
        }
        List<UUID> layoutIds = platforms.stream()
                .flatMap(platform -> ClientRouteDataCache.routeLayoutIdsForPlatformStop(platform.id()).stream())
                .distinct()
                .toList();
        int rowHeight = 16;
        int listTop = contentY;
        SPSGui.Rect list = new SPSGui.Rect(bounds.x() + 8, listTop, bounds.width() - 16, Math.max(20, bounds.bottom() - listTop - 8));
        String cardKey = card.windowKey();
        double maxScroll = Math.max(0.0D, layoutIds.size() * rowHeight - list.height());
        double scroll = Math.max(0.0D, Math.min(maxScroll, this.stationCardRouteScrolls.getOrDefault(cardKey, 0.0D)));
        this.stationCardRouteScrolls.put(cardKey, scroll);
        this.stationCardRouteRegions.put(cardKey, list);
        this.stationCardRouteMaxScrolls.put(cardKey, maxScroll);
        graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        int y = (int) Math.round(list.y() - scroll);
        for (UUID layoutId : layoutIds) {
            Optional<RouteLayout> layout = ClientRouteDataCache.routeLayout(layoutId);
            Optional<RouteLine> line = layout.flatMap(value -> ClientRouteDataCache.routeLine(value.routeLineId()));
            if (layout.isEmpty() || line.isEmpty()) {
                y += rowHeight;
                continue;
            }
            SPSGui.Rect row = new SPSGui.Rect(list.x(), y, list.width() - (maxScroll > 0.0D ? 5 : 0), FullMapTheme.ROUTE_CHIP_HEIGHT);
            boolean visible = rectsOverlap(row, list);
            if (visible) {
                boolean hovered = hoverable && list.contains(mouseX, mouseY) && row.contains(mouseX, mouseY);
                FullMapUi.routeChip(graphics, this.font, row, FullMapText.primaryName(line.get()) + " · " + FullMapText.primaryName(layout.get()), routeLineColors(line.get()), hovered, false, layout.get().id().hashCode());
                if (active) {
                    SPSGui.Rect clipped = clipRect(row, list);
                    if (clipped.width() > 0 && clipped.height() > 0) {
                        this.addClick(clipped, () -> this.pushCard(MapCard.routeLine(line.get().id(), Optional.of(layout.get().id()), card.levelKey())), Component.translatable("screen.superpipeslide.full_map.open_route_card", FullMapText.primaryName(line.get())));
                    }
                }
            }
            y += rowHeight;
        }
        graphics.disableScissor();
        if (maxScroll > 0.0D) {
            FullMapScrollBar.Binding binding = new FullMapScrollBar.Binding(
                    "stationRoutes:" + cardKey,
                    list,
                    layoutIds.size() * rowHeight,
                    scroll,
                    maxScroll,
                    FullMapScrollBar.MUTED,
                    true,
                    value -> this.stationCardRouteScrolls.put(cardKey, value));
            this.registerScrollBar(binding);
            FullMapScrollBar.render(graphics, binding);
        }
    }

    private void renderFoldPeekCard(GuiGraphicsExtractor graphics, MapCard card, SPSGui.Rect bounds, boolean active, boolean hoverable, int mouseX, int mouseY) {
        MapNode fold = this.findFoldNode(card.id(), card.levelKey()).orElse(null);
        if (fold == null || fold.foldPeerId().isEmpty()) {
            SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.full_map.fold_peer_missing"), bounds.x() + 8, bounds.y() + 8, SPSGui.DANGER);
            return;
        }
        PipeAnchorId peerId = fold.foldPeerId().get();
        FullMapUi.cardHeader(graphics, this.font, bounds, FullMapText.displayNameStack(fold), Component.translatable("screen.superpipeslide.full_map.peer_peek"), active);
        int headerHeight = FullMapUi.cardHeaderHeight(FullMapText.displayNameStack(fold), Component.translatable("screen.superpipeslide.full_map.peer_peek"));
        if (FullRouteMapCache.layoutMode().physical()) {
            this.physicalFoldNodeForAnchor(peerId).ifPresentOrElse(
                    target -> SPSGui.smallText(graphics, this.font, dimensionLabel(peerId.levelKey()) + " " + target.label(), bounds.x() + 8, bounds.y() + headerHeight + 3, SPSGui.TEXT_SECONDARY, FullMapTheme.TYPE_META),
                    () -> SPSGui.smallText(graphics, this.font, dimensionLabel(peerId.levelKey()) + " " + peerId.blockPos().toShortString(), bounds.x() + 8, bounds.y() + headerHeight + 3, SPSGui.TEXT_SECONDARY, FullMapTheme.TYPE_META));
        } else {
            this.displayNodeForFoldAnchor(peerId).ifPresentOrElse(
                    target -> SPSGui.smallText(graphics, this.font, dimensionLabel(peerId.levelKey()) + " " + FullMapText.primaryName(target), bounds.x() + 8, bounds.y() + headerHeight + 3, FullMapTheme.TEXT_SECONDARY, FullMapTheme.TYPE_META),
                    () -> SPSGui.smallText(graphics, this.font, dimensionLabel(peerId.levelKey()) + " " + peerId.blockPos().toShortString(), bounds.x() + 8, bounds.y() + headerHeight + 3, SPSGui.TEXT_SECONDARY, FullMapTheme.TYPE_META));
        }

        int previewTop = bounds.y() + headerHeight + 15;
        SPSGui.Rect preview = new SPSGui.Rect(bounds.x() + 8, previewTop, bounds.width() - 16, Math.max(72, bounds.bottom() - previewTop - 28));
        this.renderFoldPeerPreview(graphics, peerId, preview, false);

        SPSGui.Rect jump = new SPSGui.Rect(bounds.x() + 8, bounds.bottom() - 22, 104, 16);
        graphics.fill(jump.x(), jump.y(), jump.right(), jump.bottom(), hoverable && jump.contains(mouseX, mouseY) ? FullMapTheme.SURFACE_CONTROL_HOVER : FullMapTheme.SURFACE_CONTROL);
        graphics.outline(jump.x(), jump.y(), jump.width(), jump.height(), FullMapTheme.BORDER_SELECTED);
        SPSGui.centeredText(graphics, this.font, Component.translatable("screen.superpipeslide.full_map.jump_to_peer"), jump.x() + jump.width() / 2, jump.y() + 4, SPSGui.INFO);
        if (active) {
            this.addClick(jump, () -> this.jumpToFoldPeer(peerId), Component.translatable("screen.superpipeslide.full_map.jump_to_peer"));
        }
    }

    private void renderFullMapNavigationSearch(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.searchBox == null || this.searchResultsBounds.width() <= 0 || this.searchResultsBounds.height() <= 0) {
            return;
        }
        boolean searchOpen = this.searchExpanded || this.searchBox.isFocused();
        if (!searchOpen) {
            return;
        }
        List<ClientNavigationController.DestinationSearchResult> destinations = this.navigationDestinationResults();
        List<SearchResult> results = this.searchResults();
        SPSGui.Rect panel = this.searchResultsBounds;
        this.navigationResultScroll = clamp(this.navigationResultScroll, 0.0D, this.maxNavigationResultScroll(destinations, results, panel));
        graphics.fill(panel.x() + 2, panel.y() + 3, panel.right() + 2, panel.bottom() + 3, FullMapTheme.SHADOW);
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), FullMapTheme.SURFACE_CARD_ACTIVE);
        graphics.outline(panel.x(), panel.y(), panel.width(), panel.height(), FullMapTheme.BORDER);
        graphics.fill(panel.x() + 1, panel.y() + 1, panel.right() - 1, panel.y() + 2, FullMapTheme.INNER_HIGHLIGHT_SOFT);
        if (destinations.isEmpty() && results.isEmpty()) {
            Component hint = this.searchBox.getValue().trim().isEmpty()
                    ? Component.translatable("screen.superpipeslide.full_map.search_empty_hint")
                    : Component.translatable("screen.superpipeslide.full_map.search_no_results");
            SPSGui.text(graphics, this.font, hint, panel.x() + 10, panel.y() + 11, FullMapTheme.TEXT_MUTED);
            return;
        }
        SPSGui.Rect list = new SPSGui.Rect(panel.x() + 4, panel.y() + 4, panel.width() - 8, panel.height() - 8);
        graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        int y = (int) Math.round(list.y() - this.navigationResultScroll);
        for (ClientNavigationController.DestinationSearchResult result : destinations) {
            SPSGui.Rect row = new SPSGui.Rect(list.x(), y, list.width(), NAVIGATION_RESULT_ROW_HEIGHT - 2);
            if (rectsOverlap(row, list)) {
                this.renderNavigationDestinationRow(graphics, result, clipRect(row, list), mouseX, mouseY);
            }
            y += NAVIGATION_RESULT_ROW_HEIGHT;
        }
        if (!destinations.isEmpty() && !results.isEmpty()) {
            y += 4;
        }
        for (SearchResult result : results) {
            SPSGui.Rect row = new SPSGui.Rect(list.x(), y, list.width(), 21);
            if (rectsOverlap(row, list)) {
                SPSGui.Rect visibleRow = clipRect(row, list);
                graphics.fill(visibleRow.x(), visibleRow.y(), visibleRow.right(), visibleRow.bottom(), visibleRow.contains(mouseX, mouseY) ? FullMapTheme.HIGHLIGHT_SOFT : FullMapTheme.SURFACE_CARD_ACTIVE);
                DisplayNameStack title = result.title();
                SPSGui.text(graphics, this.font, SPSGui.ellipsize(this.font, title.primary(), visibleRow.width() - 8), visibleRow.x() + 4, visibleRow.y() + 2, FullMapTheme.TEXT_PRIMARY);
                String secondary = title.hasSecondary() ? title.secondary() + " / " + result.subtitle() : result.subtitle();
                SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, secondary, Math.round((visibleRow.width() - 8) / FullMapTheme.TYPE_TINY)), visibleRow.x() + 4, visibleRow.y() + 12, FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_TINY);
                this.addClick(visibleRow, () -> this.selectSearchResult(result), Component.translatable("screen.superpipeslide.full_map.search_select", title.flat()));
            }
            y += 23;
        }
        graphics.disableScissor();
        double maxScroll = this.maxNavigationResultScroll(destinations, results, panel);
        if (maxScroll > 0.0D) {
            int contentHeight = destinations.size() * NAVIGATION_RESULT_ROW_HEIGHT + results.size() * 23 + (destinations.isEmpty() || results.isEmpty() ? 0 : 4);
            FullMapScrollBar.Binding binding = new FullMapScrollBar.Binding(
                    "navigationResults",
                    list,
                    contentHeight,
                    this.navigationResultScroll,
                    maxScroll,
                    FullMapScrollBar.INFO,
                    true,
                    value -> this.navigationResultScroll = value);
            this.registerScrollBar(binding);
            FullMapScrollBar.render(graphics, binding);
        }
    }

    private void renderFullMapNavigationPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.navigationDrawerBounds.width() > 0 && this.navigationDrawerBounds.height() > 0) {
            this.renderNavigationRouteSheet(graphics, mouseX, mouseY);
            return;
        }
        this.navigationItineraryBounds = new SPSGui.Rect(0, 0, 0, 0);
        if (this.activeNavigationPillBounds.width() > 0 && this.activeNavigationPillBounds.height() > 0) {
            this.renderActiveNavigationCompact(graphics, mouseX, mouseY);
        }
    }

    private void renderNavigationDestinationRow(GuiGraphicsExtractor graphics, ClientNavigationController.DestinationSearchResult result, SPSGui.Rect row, int mouseX, int mouseY) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        FullMapNavigationViewModel.DestinationCard model = FullMapNavigationViewModel.destinationCard(
                this.minecraft.player,
                result,
                result.stationGroupId().equals(this.selectedNavigationStationGroupId));
        boolean hovered = row.contains(mouseX, mouseY);
        int accent = this.navigationChipColor(model.statusTone());
        graphics.fill(row.x(), row.y(), row.right(), row.bottom(), model.selected() ? SPSGui.withAlpha(SPSGui.INFO, 0x18) : hovered ? FullMapTheme.HIGHLIGHT_SOFT : FullMapTheme.SURFACE_CARD_ACTIVE);
        if (model.selected()) {
            graphics.outline(row.x(), row.y(), row.width(), row.height(), SPSGui.withAlpha(SPSGui.INFO, 0xAA));
        }
        graphics.fill(row.x(), row.y() + 3, row.x() + 3, row.bottom() - 3, SPSGui.withAlpha(accent, model.selected() ? 0xEA : 0xA8));
        Vec2 iconCenter = new Vec2(row.x() + 12, row.y() + row.height() / 2.0D);
        if (!model.reachable()) {
            SmoothGuiPrimitives.diamond(graphics, iconCenter, 4.2D, SPSGui.withAlpha(SPSGui.DANGER, 0xD8));
        } else if (model.crossDimension()) {
            SmoothGuiPrimitives.diamond(graphics, iconCenter, 4.0D, SPSGui.withAlpha(SPSGui.INFO, 0xD8));
            SmoothGuiPrimitives.circle(graphics, iconCenter, 1.3D, SPSGui.withAlpha(0xFFFFFFFF, 0xB8));
        } else {
            SmoothGuiPrimitives.circle(graphics, iconCenter, 4.0D, SPSGui.withAlpha(accent, 0xC8));
        }
        SPSGui.Rect action = new SPSGui.Rect(row.right() - 22, row.y() + 6, 16, 16);
        int textX = row.x() + 24;
        int textWidth = Math.max(32, action.x() - textX - 6);
        int textY = row.y() + Math.max(2, Math.min(3, row.height() - 24));
        SPSGui.text(graphics, this.font, SPSGui.ellipsize(this.font, model.primaryName(), textWidth), textX, textY, model.reachable() ? FullMapTheme.TEXT_PRIMARY : FullMapTheme.TEXT_SECONDARY);
        String detail = model.translatedName().isBlank() ? model.dimensionText() : model.translatedName() + " / " + model.dimensionText();
        detail += " / " + model.statusText();
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, detail, Math.round(textWidth / FullMapTheme.TYPE_TINY)), textX, textY + 12, FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_TINY);

        FullMapUi.iconButton(graphics, action, action.contains(mouseX, mouseY), model.selected(), false, SPSGui.Icon.LOCATE);
        this.addClick(row, () -> ClientRouteDataCache.stationGroup(model.stationGroupId()).ifPresent(this::locateStation), Component.translatable("screen.superpipeslide.full_map.search_select", model.primaryName()));
        this.addPriorityClick(action, () -> this.selectNavigationDestination(model.stationGroupId(), true), Component.translatable("screen.superpipeslide.full_map.navigate_here"));
    }

    private void renderNavigationRouteSheet(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        FullMapNavigationViewModel.RoutePreview preview = this.navigationPreviewModel();
        SPSGui.Rect panel = this.navigationDrawerBounds;
        if (panel.width() <= 0 || panel.height() <= 0) {
            return;
        }
        graphics.fill(panel.x() + 2, panel.y() + 3, panel.right() + 2, panel.bottom() + 3, FullMapTheme.SHADOW);
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), FullMapTheme.SURFACE_CARD_ACTIVE);
        graphics.outline(panel.x(), panel.y(), panel.width(), panel.height(), preview.reachable() ? FullMapTheme.BORDER_SELECTED : FullMapTheme.BORDER);
        graphics.fill(panel.x() + 1, panel.y() + 1, panel.right() - 1, panel.y() + 2, FullMapTheme.INNER_HIGHLIGHT_MEDIUM);
        SPSGui.Rect header = new SPSGui.Rect(panel.x() + 6, panel.y() + 5, panel.width() - 12, 58);
        this.renderNavigationSummary(graphics, header, preview, mouseX, mouseY);
        int listTop = header.bottom() + 4;
        this.navigationItineraryBounds = new SPSGui.Rect(panel.x() + 6, listTop, panel.width() - 12, Math.max(42, panel.bottom() - listTop - 6));
        this.renderNavigationItinerary(graphics, this.navigationItineraryBounds, preview, mouseX, mouseY);
    }

    private void renderActiveNavigationCompact(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ClientNavigationController.NavigationPlan plan = ClientNavigationController.activeSessionSnapshot().map(ClientNavigationController.NavigationSessionSnapshot::plan).orElse(null);
        if (plan == null) {
            return;
        }
        SPSGui.Rect panel = this.activeNavigationPillBounds;
        if (panel.width() <= 0 || panel.height() <= 0) {
            return;
        }
        FullMapNavigationViewModel.RoutePreview preview = FullMapNavigationViewModel.routePreview(plan);
        int accent = preview.primaryColor();
        graphics.fill(panel.x() + 2, panel.y() + 3, panel.right() + 2, panel.bottom() + 3, FullMapTheme.SHADOW);
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), FullMapTheme.SURFACE_CARD_ACTIVE);
        graphics.outline(panel.x(), panel.y(), panel.width(), panel.height(), FullMapTheme.BORDER_SELECTED);
        graphics.fill(panel.x(), panel.y(), panel.x() + 4, panel.bottom(), SPSGui.withAlpha(accent, 0xD8));

        SPSGui.Rect cancel = new SPSGui.Rect(panel.right() - 22, panel.y() + 6, 16, 16);
        SPSGui.Rect open = new SPSGui.Rect(cancel.x() - 20, panel.y() + 6, 16, 16);
        FullMapUi.iconButton(graphics, open, open.contains(mouseX, mouseY), false, false, SPSGui.Icon.ROUTE_LINE);
        this.renderNavigationDangerIconButton(graphics, cancel, cancel.contains(mouseX, mouseY), SPSGui.Icon.REMOVE, this.navigationCancelArmed());
        this.addPriorityClick(open, this::expandActiveNavigation, Component.translatable("screen.superpipeslide.full_map.navigation.view_route"));
        this.addPriorityClick(cancel, this::cancelNavigationFromMap, this.navigationCancelTooltip());
        this.addClick(panel, this::expandActiveNavigation, Component.translatable("screen.superpipeslide.full_map.navigation.view_route"));

        int textX = panel.x() + 12;
        int textWidth = Math.max(42, open.x() - textX - 6);
        SPSGui.text(graphics, this.font, SPSGui.scrollingText(this.font, preview.destinationName(), textWidth, preview.destinationName().hashCode()), textX, panel.y() + 5, FullMapTheme.TEXT_PRIMARY);
        String summary = preview.summaryText().isBlank() ? preview.destinationSubtitle() : preview.summaryText();
        SPSGui.smallText(graphics, this.font, SPSGui.scrollingText(this.font, summary, Math.round(textWidth / FullMapTheme.TYPE_TINY), summary.hashCode()), textX, panel.y() + 17, FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_TINY);
    }

    private void renderNavigationSummary(GuiGraphicsExtractor graphics, SPSGui.Rect rect, FullMapNavigationViewModel.RoutePreview preview, int mouseX, int mouseY) {
        int accent = preview.reachable() ? preview.primaryColor() : FullMapTheme.WARNING;
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), SPSGui.withAlpha(accent, 0x10));
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), SPSGui.withAlpha(accent, preview.reachable() ? 0xAA : 0x88));
        graphics.fill(rect.x(), rect.y(), rect.x() + 4, rect.bottom(), SPSGui.withAlpha(accent, 0xD0));
        int right = rect.right() - 4;
        SPSGui.Rect close = new SPSGui.Rect(right - 16, rect.y() + 5, 16, 16);
        FullMapUi.iconButton(graphics, close, close.contains(mouseX, mouseY), false, false, SPSGui.Icon.CLOSE);
        this.addPriorityClick(close, this::clearNavigationPreview, Component.translatable("screen.superpipeslide.full_map.navigation.close"));
        right = close.x() - 4;

        int titleRight = right;
        int detailRight = right;
        if (ClientNavigationController.isNavigating()) {
            SPSGui.Rect cancel = new SPSGui.Rect(right - 16, rect.bottom() - 18, 16, 16);
            this.renderNavigationDangerIconButton(graphics, cancel, cancel.contains(mouseX, mouseY), SPSGui.Icon.REMOVE, this.navigationCancelArmed());
            this.addPriorityClick(cancel, this::cancelNavigationFromMap, this.navigationCancelTooltip());
            right = cancel.x() - 4;
        }
        String actionLabel = this.navigationCrossDimensionConfirmationArmed && preview.needsCrossDimensionConfirmation()
                ? Component.translatable("screen.superpipeslide.navigation.confirm_cross_dimension").getString()
                : preview.primaryActionLabel();
        int maxActionWidth = Math.max(44, right - rect.x() - 4);
        int actionWidth = Math.min(maxActionWidth, Math.min(132, Math.max(62, Math.round(this.font.width(actionLabel) * ACTION_LABEL_TEXT_SCALE) + 16)));
        SPSGui.Rect action = new SPSGui.Rect(right - actionWidth, rect.bottom() - 18, actionWidth, 16);
        this.renderNavigationTextButton(graphics, action, actionLabel, accent, preview.primaryAction() != FullMapNavigationViewModel.PrimaryAction.UNAVAILABLE, action.contains(mouseX, mouseY));
        if (preview.primaryAction() != FullMapNavigationViewModel.PrimaryAction.UNAVAILABLE) {
            this.addClick(action, () -> this.handleNavigationPrimaryAction(preview), Component.literal(actionLabel));
        }

        int textX = rect.x() + 22;
        SPSGui.icon(graphics, new SPSGui.Rect(rect.x() + 6, rect.y() + 5, 12, 12), SPSGui.Icon.DRAG, FullMapTheme.TEXT_MUTED);
        int titleWidth = Math.max(32, titleRight - textX);
        int detailWidth = Math.max(32, detailRight - textX);
        SPSGui.text(graphics, this.font, SPSGui.scrollingText(this.font, preview.destinationName(), titleWidth, preview.destinationName().hashCode()), textX, rect.y() + 6, preview.reachable() ? FullMapTheme.TEXT_PRIMARY : FullMapTheme.WARNING);
        String summary = preview.summaryText().isBlank() ? preview.destinationSubtitle() : preview.summaryText();
        SPSGui.smallText(graphics, this.font, SPSGui.scrollingText(this.font, summary, Math.round(detailWidth / FullMapTheme.TYPE_META), summary.hashCode()), textX, rect.y() + 20, preview.warnings().isEmpty() ? FullMapTheme.TEXT_SECONDARY : FullMapTheme.WARNING, FullMapTheme.TYPE_META);
        if (!preview.destinationSubtitle().isBlank()) {
            SPSGui.smallText(graphics, this.font, SPSGui.scrollingText(this.font, preview.destinationSubtitle(), Math.round(detailWidth / FullMapTheme.TYPE_TINY), preview.destinationSubtitle().hashCode()), textX, rect.y() + 32, FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_TINY);
        }
    }

    private void renderNavigationTextButton(GuiGraphicsExtractor graphics, SPSGui.Rect button, String label, int accent, boolean enabled, boolean hovered) {
        int fill = !enabled ? FullMapTheme.SURFACE_CONTROL_DISABLED : hovered ? SPSGui.withAlpha(accent, 0x24) : FullMapTheme.SURFACE_CONTROL;
        int border = !enabled ? FullMapTheme.BORDER_MUTED : hovered ? SPSGui.withAlpha(accent, 0xE0) : SPSGui.withAlpha(accent, 0x9C);
        graphics.fill(button.x(), button.y(), button.right(), button.bottom(), fill);
        graphics.outline(button.x(), button.y(), button.width(), button.height(), border);
        SPSGui.centeredText(graphics, this.font, SPSGui.ellipsize(this.font, label, button.width() - 8), button.x() + button.width() / 2, button.y() + 4, enabled ? FullMapTheme.TEXT_PRIMARY : FullMapTheme.TEXT_DISABLED);
    }

    private void renderNavigationDangerIconButton(GuiGraphicsExtractor graphics, SPSGui.Rect rect, boolean hovered, SPSGui.Icon icon, boolean armed) {
        int accent = armed ? FullMapTheme.WARNING : SPSGui.DANGER;
        int fill = armed ? SPSGui.withAlpha(accent, 0x40) : hovered ? SPSGui.withAlpha(accent, 0x22) : FullMapTheme.SURFACE_CONTROL;
        int border = hovered || armed ? SPSGui.withAlpha(accent, 0xE0) : SPSGui.withAlpha(accent, 0x88);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), border);
        SPSGui.icon(graphics, rect, icon, hovered || armed ? accent : SPSGui.withAlpha(accent, 0xD8));
    }

    private boolean navigationCancelArmed() {
        if (this.navigationCancelArmed && System.currentTimeMillis() - this.navigationCancelArmedAtMillis > NAVIGATION_CANCEL_CONFIRM_TIMEOUT_MILLIS) {
            this.navigationCancelArmed = false;
        }
        return this.navigationCancelArmed;
    }

    private Component navigationCancelTooltip() {
        return this.navigationCancelArmed()
                ? Component.translatable("screen.superpipeslide.full_map.navigation.cancel_confirm")
                : Component.translatable("screen.superpipeslide.navigation.cancel");
    }

    private void renderNavigationItinerary(GuiGraphicsExtractor graphics, SPSGui.Rect rect, FullMapNavigationViewModel.RoutePreview preview, int mouseX, int mouseY) {
        this.navigationItineraryScroll = clamp(this.navigationItineraryScroll, 0.0D, this.maxNavigationItineraryScroll(preview, rect));
        if (preview.itinerary().isEmpty()) {
            SPSGui.centeredText(graphics, this.font, Component.translatable("screen.superpipeslide.navigation.pick_destination"), rect.x() + rect.width() / 2, rect.y() + rect.height() / 2 - 4, FullMapTheme.TEXT_MUTED);
            return;
        }
        graphics.enableScissor(rect.x(), rect.y(), rect.right(), rect.bottom());
        int y = rect.y() + 3 - (int) Math.round(this.navigationItineraryScroll);
        for (int i = 0; i < preview.itinerary().size(); i++) {
            FullMapNavigationViewModel.ItineraryStep step = preview.itinerary().get(i);
            int height = this.navigationItineraryStepHeight(step, i);
            SPSGui.Rect stepRect = new SPSGui.Rect(rect.x() + 2, y, rect.width() - 4, height);
            if (rectsOverlap(stepRect, rect)) {
                this.renderNavigationItineraryStep(graphics, stepRect, step, i, preview.primaryColor(), mouseX, mouseY);
            }
            y += height + NAVIGATION_ITINERARY_STEP_GAP;
        }
        graphics.disableScissor();
        double maxScroll = this.maxNavigationItineraryScroll(preview, rect);
        if (maxScroll > 0.0D) {
            int contentHeight = this.navigationItineraryContentHeight(preview);
            FullMapScrollBar.Binding binding = new FullMapScrollBar.Binding(
                    "navigationItinerary",
                    rect,
                    contentHeight,
                    this.navigationItineraryScroll,
                    maxScroll,
                    FullMapScrollBar.INFO,
                    true,
                    value -> this.navigationItineraryScroll = value);
            this.registerScrollBar(binding);
            FullMapScrollBar.render(graphics, binding);
        }
    }

    private void renderNavigationItineraryStep(GuiGraphicsExtractor graphics, SPSGui.Rect rect, FullMapNavigationViewModel.ItineraryStep step, int index, int primaryColor, int mouseX, int mouseY) {
        if (step.kind() == FullMapNavigationViewModel.ItineraryKind.RIDE) {
            this.renderNavigationRideStep(graphics, rect, step, index, mouseX, mouseY);
            return;
        }
        int lineX = rect.x() + 10;
        int nodeY = rect.y() + 10;
        int color = this.navigationStepColor(step, primaryColor);
        if (step.kind() != FullMapNavigationViewModel.ItineraryKind.ORIGIN
                && step.kind() != FullMapNavigationViewModel.ItineraryKind.DESTINATION
                && step.kind() != FullMapNavigationViewModel.ItineraryKind.UNREACHABLE) {
            this.drawNavigationDottedLine(graphics, lineX, rect.y(), rect.bottom(), SPSGui.withAlpha(color, 0x8A));
        }
        this.drawNavigationStepGlyph(graphics, step.kind(), new Vec2(lineX, nodeY), color, primaryColor);
        int textX = rect.x() + 24;
        int textWidth = Math.max(24, rect.right() - textX - 4);
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, step.title(), Math.round(textWidth / ROW_TITLE_TEXT_SCALE)), textX, rect.y() + 2, step.warning() ? FullMapTheme.WARNING : FullMapTheme.TEXT_PRIMARY, ROW_TITLE_TEXT_SCALE);
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, step.detail(), Math.round(textWidth / ROW_META_TEXT_SCALE)), textX, rect.y() + 13, step.warning() ? FullMapTheme.WARNING : FullMapTheme.TEXT_MUTED, ROW_META_TEXT_SCALE);
    }

    private void drawNavigationStepGlyph(GuiGraphicsExtractor graphics, FullMapNavigationViewModel.ItineraryKind kind, Vec2 center, int color, int primaryColor) {
        switch (kind) {
            case ORIGIN -> {
                SmoothGuiPrimitives.circle(graphics, center, 6.2D, SPSGui.withAlpha(NavigationSemanticColors.DESTINATION, 0xE0));
                SmoothGuiPrimitives.circle(graphics, center, 2.0D, 0xEFFFFFFF);
            }
            case DESTINATION -> {
                SmoothGuiPrimitives.diamond(graphics, center, 6.8D, SPSGui.withAlpha(primaryColor, 0xEA));
                SmoothGuiPrimitives.circle(graphics, center, 1.7D, 0xEFFFFFFF);
            }
            case UNREACHABLE -> {
                SmoothGuiPrimitives.diamond(graphics, center, 6.4D, SPSGui.withAlpha(SPSGui.DANGER, 0xE8));
                SmoothGuiPrimitives.line(graphics, new Vec2(center.x() - 3.0D, center.y() - 3.0D), new Vec2(center.x() + 3.0D, center.y() + 3.0D), 1.2D, 0xEEFFFFFF);
            }
            case SAME_STATION_TRANSFER -> this.drawSameStationTransferGlyph(graphics, center);
            case OUT_OF_STATION_TRANSFER, FINAL_WALK -> this.drawOutStationTransferGlyph(graphics, center, kind == FullMapNavigationViewModel.ItineraryKind.FINAL_WALK);
            case CROSS_DIMENSION_TRANSFER, CROSS_DIMENSION_FINAL_WALK -> this.drawCrossDimensionTransferGlyph(graphics, center);
            case WALK_TO_BOARD -> this.drawWalkGlyph(graphics, center, FullMapTheme.TEXT_MUTED);
            default -> SmoothGuiPrimitives.circle(graphics, center, 4.5D, SPSGui.withAlpha(color, 0xC8));
        }
    }

    private void drawSameStationTransferGlyph(GuiGraphicsExtractor graphics, Vec2 center) {
        SmoothGuiPrimitives.circle(graphics, center, 6.4D, 0xEFFFFFFF);
        SmoothGuiPrimitives.line(graphics, new Vec2(center.x() - 3.4D, center.y()), new Vec2(center.x() + 3.4D, center.y()), 1.8D, NavigationSemanticColors.SAME_STATION_TRANSFER);
        SmoothGuiPrimitives.circle(graphics, new Vec2(center.x() - 3.2D, center.y()), 2.4D, NavigationSemanticColors.SAME_STATION_TRANSFER);
        SmoothGuiPrimitives.circle(graphics, new Vec2(center.x() + 3.2D, center.y()), 2.4D, NavigationSemanticColors.SAME_STATION_TRANSFER);
    }

    private void drawOutStationTransferGlyph(GuiGraphicsExtractor graphics, Vec2 center, boolean walkOnly) {
        SmoothGuiPrimitives.circle(graphics, center, 6.4D, 0xEFFFFFFF);
        int color = FullMapTheme.WARNING;
        if (walkOnly) {
            this.drawWalkGlyph(graphics, center, color);
            return;
        }
        graphics.fill((int) Math.round(center.x() - 3.2D), (int) Math.round(center.y() - 4.0D), (int) Math.round(center.x() + 1.8D), (int) Math.round(center.y() + 4.0D), SPSGui.withAlpha(color, 0xE8));
        SmoothGuiPrimitives.line(graphics, new Vec2(center.x() - 0.6D, center.y()), new Vec2(center.x() + 4.4D, center.y()), 1.5D, color);
        SmoothGuiPrimitives.line(graphics, new Vec2(center.x() + 2.0D, center.y() - 2.0D), new Vec2(center.x() + 4.4D, center.y()), 1.2D, color);
        SmoothGuiPrimitives.line(graphics, new Vec2(center.x() + 2.0D, center.y() + 2.0D), new Vec2(center.x() + 4.4D, center.y()), 1.2D, color);
    }

    private void drawCrossDimensionTransferGlyph(GuiGraphicsExtractor graphics, Vec2 center) {
        SmoothGuiPrimitives.diamond(graphics, center, 6.7D, 0xF2FFFFFF);
        SmoothGuiPrimitives.ring(graphics, center, 4.2D, 1.6D, 0xFFC59BFF);
        SmoothGuiPrimitives.circle(graphics, center, 1.5D, 0xFFC59BFF);
    }

    private void drawWalkGlyph(GuiGraphicsExtractor graphics, Vec2 center, int color) {
        SmoothGuiPrimitives.circle(graphics, new Vec2(center.x(), center.y() - 4.0D), 1.5D, SPSGui.withAlpha(color, 0xE0));
        SmoothGuiPrimitives.line(graphics, new Vec2(center.x(), center.y() - 2.4D), new Vec2(center.x() - 1.2D, center.y() + 1.0D), 1.2D, color);
        SmoothGuiPrimitives.line(graphics, new Vec2(center.x() - 1.2D, center.y() - 0.5D), new Vec2(center.x() + 2.4D, center.y() - 1.7D), 1.0D, color);
        SmoothGuiPrimitives.line(graphics, new Vec2(center.x() - 1.0D, center.y() + 1.0D), new Vec2(center.x() - 3.4D, center.y() + 4.4D), 1.1D, color);
        SmoothGuiPrimitives.line(graphics, new Vec2(center.x() - 1.0D, center.y() + 1.0D), new Vec2(center.x() + 2.8D, center.y() + 4.1D), 1.1D, color);
    }

    private void renderNavigationRideStep(GuiGraphicsExtractor graphics, SPSGui.Rect rect, FullMapNavigationViewModel.ItineraryStep step, int index, int mouseX, int mouseY) {
        boolean hovered = step.expandable() && rect.contains(mouseX, mouseY);
        if (hovered) {
            graphics.fill(rect.x() + 18, rect.y(), rect.right(), rect.bottom(), SPSGui.withAlpha(SPSGui.INFO, 0x08));
        }
        int lineX = rect.x() + 10;
        int textX = rect.x() + 24;
        int railColor = this.firstNavigationColor(step.colors());
        List<NavigationStationDisplay> stations = this.displayedNavigationStations(step, index);
        int stationStartY = rect.y() + 22;
        int railTop = stationStartY + 4;
        int railBottom = stations.size() <= 1 ? rect.bottom() - 6 : stationStartY + (stations.size() - 1) * NAVIGATION_RIDE_STATION_ROW_HEIGHT + 4;
        this.drawNavigationVerticalRail(graphics, lineX, railTop, railBottom, step.colors(), 4);

        int maxBadgeWidth = Math.max(34, Math.min(78, rect.right() - textX - 6));
        int badgeWidth = this.drawNavigationLineBadge(graphics, textX, rect.y() + 3, step.lineName(), railColor, maxBadgeWidth);
        int detailX = textX + badgeWidth + 4;
        int detailWidth = rect.right() - detailX - 4;
        if (detailWidth >= 24) {
            SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, step.detail(), Math.round(detailWidth / ROW_DETAIL_TEXT_SCALE)), detailX, rect.y() + 7, FullMapTheme.TEXT_MUTED, ROW_DETAIL_TEXT_SCALE);
        }

        for (int i = 0; i < stations.size(); i++) {
            NavigationStationDisplay station = stations.get(i);
            int y = stationStartY + i * NAVIGATION_RIDE_STATION_ROW_HEIGHT;
            if (station.placeholder()) {
                SPSGui.smallText(graphics, this.font, station.label(), textX + 11, y + 2, FullMapTheme.TEXT_MUTED, ROW_META_TEXT_SCALE);
                continue;
            }
            boolean endpoint = station.endpoint();
            if (endpoint) {
                SmoothGuiPrimitives.circle(graphics, new Vec2(lineX, y + 4), 4.2D, 0xE8FFFFFF);
                SmoothGuiPrimitives.circle(graphics, new Vec2(lineX, y + 4), 2.7D, SPSGui.withAlpha(railColor, 0xE2));
            } else {
                SmoothGuiPrimitives.circle(graphics, new Vec2(lineX, y + 4), 2.4D, 0x92FFFFFF);
            }
            SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, station.label(), Math.round((rect.right() - textX - 9) / ROW_DETAIL_TEXT_SCALE)), textX + 8, y + 1, endpoint ? FullMapTheme.TEXT_PRIMARY : FullMapTheme.TEXT_SECONDARY, endpoint ? ROW_ENDPOINT_TEXT_SCALE : ROW_SUB_TEXT_SCALE);
        }
        if (step.expandable()) {
            this.addClick(rect, () -> this.toggleNavigationRideExpanded(index), Component.translatable(this.navigationExpandedRideSteps.contains(index)
                    ? "screen.superpipeslide.full_map.station_card.collapse_stops"
                    : "screen.superpipeslide.full_map.station_card.expand_stops"));
        }
    }

    private List<ClientNavigationController.DestinationSearchResult> navigationDestinationResults() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return List.of();
        }
        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim();
        if (query.isEmpty()) {
            this.cachedNavigationQuery = "";
            this.cachedNavigationResults = List.of();
            return List.of();
        }
        long routeRevision = ClientRouteDataCache.revision();
        long pipeRevision = ClientPipeNetworkCache.aggregateRevision();
        ResourceKey<Level> levelKey = this.minecraft.player.level().dimension();
        boolean queryChanged = !query.equals(this.cachedNavigationQuery);
        if (queryChanged
                || this.cachedNavigationRouteRevision != routeRevision
                || this.cachedNavigationPipeRevision != pipeRevision
                || this.cachedNavigationLevelKey == null
                || !this.cachedNavigationLevelKey.equals(levelKey)) {
            this.cachedNavigationQuery = query;
            this.cachedNavigationRouteRevision = routeRevision;
            this.cachedNavigationPipeRevision = pipeRevision;
            this.cachedNavigationLevelKey = levelKey;
            this.cachedNavigationResults = ClientNavigationController.searchDestinations(this.minecraft.player, query, NAVIGATION_RESULT_LIMIT);
            if (queryChanged) {
                this.navigationResultScroll = 0.0D;
                this.navigationCrossDimensionConfirmationArmed = false;
            }
        }
        this.refreshSelectedNavigationPlanIfStale(routeRevision, pipeRevision);
        return this.cachedNavigationResults;
    }

    private double maxNavigationResultScroll(List<ClientNavigationController.DestinationSearchResult> destinations, List<SearchResult> results, SPSGui.Rect panel) {
        int contentHeight = destinations.size() * NAVIGATION_RESULT_ROW_HEIGHT + results.size() * 23 + (destinations.isEmpty() || results.isEmpty() ? 0 : 4);
        return Math.max(0.0D, contentHeight + 8.0D - panel.height());
    }

    private FullMapNavigationViewModel.RoutePreview navigationPreviewModel() {
        if (this.selectedNavigationStationGroupId == null) {
            return FullMapNavigationViewModel.emptyPreview();
        }
        if (this.selectedNavigationPlan == null) {
            return FullMapNavigationViewModel.unreachablePreview(this.selectedNavigationStationGroupId);
        }
        return FullMapNavigationViewModel.routePreview(this.selectedNavigationPlan);
    }

    private void selectNavigationDestination(UUID stationGroupId, boolean locate) {
        this.selectedNavigationStationGroupId = stationGroupId;
        this.selectedNavigationPlanFromActiveSession = false;
        this.navigationSheetExpanded = true;
        this.searchExpanded = false;
        this.navigationCrossDimensionConfirmationArmed = false;
        this.navigationItineraryScroll = 0.0D;
        this.navigationExpandedRideSteps.clear();
        if (this.searchBox != null) {
            this.searchBox.setFocused(false);
            this.setFocused(null);
        }
        this.selectedNavigationPlanRouteRevision = ClientRouteDataCache.revision();
        this.selectedNavigationPlanPipeRevision = ClientPipeNetworkCache.aggregateRevision();
        this.selectedNavigationPlan = this.minecraft != null && this.minecraft.player != null
                ? ClientNavigationController.previewPlan(this.minecraft.player, stationGroupId).orElse(null)
                : null;
        if (locate) {
            ClientRouteDataCache.stationGroup(stationGroupId).ifPresent(this::locateStation);
        }
    }

    private void refreshSelectedNavigationPlanIfStale(long routeRevision, long pipeRevision) {
        if (this.minecraft == null || this.minecraft.player == null || this.selectedNavigationStationGroupId == null) {
            return;
        }
        if (this.selectedNavigationPlanFromActiveSession) {
            Optional<ClientNavigationController.NavigationSessionSnapshot> active = ClientNavigationController.activeSessionSnapshot();
            if (active.isEmpty()) {
                this.clearNavigationPreview();
                return;
            }
            this.selectedNavigationStationGroupId = active.get().plan().destinationStationGroupId();
            this.selectedNavigationPlan = active.get().plan();
            this.selectedNavigationPlanRouteRevision = active.get().plan().routeRevision();
            this.selectedNavigationPlanPipeRevision = active.get().plan().pipeRevision();
            return;
        }
        if (this.selectedNavigationPlanRouteRevision == routeRevision && this.selectedNavigationPlanPipeRevision == pipeRevision) {
            return;
        }
        this.selectedNavigationPlanRouteRevision = routeRevision;
        this.selectedNavigationPlanPipeRevision = pipeRevision;
        this.selectedNavigationPlan = ClientNavigationController.previewPlan(this.minecraft.player, this.selectedNavigationStationGroupId).orElse(null);
        this.navigationCrossDimensionConfirmationArmed = false;
        this.navigationItineraryScroll = 0.0D;
        this.navigationExpandedRideSteps.clear();
    }

    private void handleNavigationPrimaryAction(FullMapNavigationViewModel.RoutePreview preview) {
        if (preview.needsCrossDimensionConfirmation() && !this.navigationCrossDimensionConfirmationArmed) {
            this.navigationCrossDimensionConfirmationArmed = true;
            return;
        }
        this.startSelectedNavigationFromMap();
    }

    private void startSelectedNavigationFromMap() {
        if (this.minecraft == null || this.minecraft.player == null || this.selectedNavigationStationGroupId == null) {
            return;
        }
        Optional<ClientNavigationController.NavigationPlan> plan = ClientNavigationController.startNavigation(this.minecraft.player, this.selectedNavigationStationGroupId);
        if (plan.isPresent()) {
            this.toast(Component.translatable("navigation.superpipeslide.started", FullMapNavigationViewModel.stationName(plan.get().destinationStationGroupId())).getString());
            this.clearNavigationPreview();
            this.searchExpanded = false;
            if (this.searchBox != null) {
                this.searchBox.setFocused(false);
                this.setFocused(null);
            }
            this.minecraft.setScreen(null);
        } else {
            this.selectedNavigationPlan = null;
            this.navigationSheetExpanded = true;
        }
    }

    private void cancelNavigationFromMap() {
        if (!this.navigationCancelArmed()) {
            this.navigationCancelArmed = true;
            this.navigationCancelArmedAtMillis = System.currentTimeMillis();
            return;
        }
        this.navigationCancelArmed = false;
        Optional<UUID> destination = ClientNavigationController.activeSessionSnapshot().map(snapshot -> snapshot.plan().destinationStationGroupId());
        ClientNavigationController.cancelNavigation();
        this.clearNavigationPreview();
        destination.ifPresent(stationGroupId -> this.toast(
                Component.translatable("screen.superpipeslide.full_map.navigation.cancelled", FullMapNavigationViewModel.stationName(stationGroupId)).getString(),
                Component.translatable("screen.superpipeslide.full_map.navigation.undo").getString(),
                () -> this.restoreCancelledNavigation(stationGroupId),
                NAVIGATION_UNDO_TOAST_DURATION_MILLIS));
    }

    private void restoreCancelledNavigation(UUID stationGroupId) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        Optional<ClientNavigationController.NavigationPlan> plan = ClientNavigationController.startNavigation(this.minecraft.player, stationGroupId);
        if (plan.isPresent()) {
            this.toast(Component.translatable("screen.superpipeslide.full_map.navigation.restored", FullMapNavigationViewModel.stationName(plan.get().destinationStationGroupId())).getString());
        } else {
            this.toast(Component.translatable("screen.superpipeslide.full_map.navigation.restore_failed").getString());
        }
    }

    private void clearNavigationPreview() {
        this.selectedNavigationStationGroupId = null;
        this.selectedNavigationPlan = null;
        this.selectedNavigationPlanFromActiveSession = false;
        this.selectedNavigationPlanRouteRevision = Long.MIN_VALUE;
        this.selectedNavigationPlanPipeRevision = Long.MIN_VALUE;
        this.navigationSheetExpanded = false;
        this.navigationCrossDimensionConfirmationArmed = false;
        this.navigationCancelArmed = false;
        this.navigationItineraryScroll = 0.0D;
        this.navigationExpandedRideSteps.clear();
        this.navigationItineraryBounds = new SPSGui.Rect(0, 0, 0, 0);
        this.navigationDrawerBounds = new SPSGui.Rect(0, 0, 0, 0);
        this.navigationDrawerUserBounds = null;
        this.navigationDrawerUserXRatio = Double.NaN;
        this.navigationDrawerUserYRatio = Double.NaN;
        this.draggingNavigationDrawer = false;
        this.activeNavigationPillBounds = new SPSGui.Rect(0, 0, 0, 0);
    }

    private void expandActiveNavigation() {
        ClientNavigationController.activeSessionSnapshot().ifPresent(snapshot -> {
            this.selectedNavigationStationGroupId = snapshot.plan().destinationStationGroupId();
            this.selectedNavigationPlan = snapshot.plan();
            this.selectedNavigationPlanFromActiveSession = true;
            this.selectedNavigationPlanRouteRevision = snapshot.plan().routeRevision();
            this.selectedNavigationPlanPipeRevision = snapshot.plan().pipeRevision();
            this.navigationSheetExpanded = true;
            this.navigationCrossDimensionConfirmationArmed = false;
            this.navigationItineraryScroll = 0.0D;
        });
    }

    private Optional<ClientNavigationController.NavigationPlan> currentNavigationOverlayPlan() {
        if (this.selectedNavigationPlan != null && this.selectedNavigationStationGroupId != null && this.selectedNavigationAllowedForOverlay(this.selectedNavigationPlan)) {
            return Optional.of(this.selectedNavigationPlan);
        }
        return ClientNavigationController.activeSessionSnapshot().map(ClientNavigationController.NavigationSessionSnapshot::plan);
    }

    private boolean selectedNavigationAllowedForOverlay(ClientNavigationController.NavigationPlan plan) {
        Optional<ClientNavigationController.NavigationSessionSnapshot> active = ClientNavigationController.activeSessionSnapshot();
        if (this.selectedNavigationPlanFromActiveSession) {
            return active.filter(snapshot -> snapshot.plan().id().equals(plan.id())).isPresent();
        }
        return active.filter(snapshot -> snapshot.plan().destinationStationGroupId().equals(plan.destinationStationGroupId())).isEmpty();
    }

    private int navigationActiveSegmentIndex(ClientNavigationController.NavigationPlan plan) {
        return ClientNavigationController.activeSessionSnapshot()
                .filter(snapshot -> snapshot.plan().id().equals(plan.id()))
                .map(ClientNavigationController.NavigationSessionSnapshot::segmentIndex)
                .orElse(0);
    }

    private int navigationChipColor(FullMapNavigationViewModel.ChipTone tone) {
        return switch (tone) {
            case SUCCESS -> SPSGui.SUCCESS;
            case WARNING -> FullMapTheme.WARNING;
            case INFO -> SPSGui.INFO;
            case NEUTRAL -> FullMapTheme.TEXT_MUTED;
        };
    }

    private int navigationStepColor(FullMapNavigationViewModel.ItineraryStep step, int primaryColor) {
        return switch (step.kind()) {
            case ORIGIN -> NavigationSemanticColors.DESTINATION;
            case SAME_STATION_TRANSFER -> NavigationSemanticColors.SAME_STATION_TRANSFER;
            case WALK_TO_BOARD -> FullMapTheme.TEXT_MUTED;
            case OUT_OF_STATION_TRANSFER -> NavigationSemanticColors.OUT_OF_STATION_TRANSFER;
            case FINAL_WALK -> NavigationSemanticColors.FINAL_WALK;
            case CROSS_DIMENSION_TRANSFER, CROSS_DIMENSION_FINAL_WALK -> NavigationSemanticColors.CROSS_DIMENSION_TRANSFER;
            case DESTINATION -> primaryColor;
            case UNREACHABLE -> SPSGui.DANGER;
            case RIDE -> this.firstNavigationColor(step.colors());
        };
    }

    private int firstNavigationColor(List<Integer> colors) {
        return colors == null || colors.isEmpty() ? SPSGui.INFO : SPSGui.opaque(colors.getFirst());
    }

    private List<Integer> normalizedNavigationColors(List<Integer> colors) {
        if (colors == null || colors.isEmpty()) {
            return List.of(SPSGui.INFO);
        }
        return colors.stream().limit(3).map(SPSGui::opaque).toList();
    }

    private void toggleNavigationRideExpanded(int index) {
        if (!this.navigationExpandedRideSteps.add(index)) {
            this.navigationExpandedRideSteps.remove(index);
        }
    }

    private List<NavigationStationDisplay> displayedNavigationStations(FullMapNavigationViewModel.ItineraryStep step, int index) {
        List<String> names = step.stationNames();
        ArrayList<NavigationStationDisplay> result = new ArrayList<>();
        if (names.isEmpty()) {
            return result;
        }
        if (!step.expandable() || this.navigationExpandedRideSteps.contains(index) || names.size() <= 7) {
            for (int i = 0; i < names.size(); i++) {
                result.add(new NavigationStationDisplay(names.get(i), false, i == 0 || i == names.size() - 1));
            }
            return result;
        }
        result.add(new NavigationStationDisplay(names.get(0), false, true));
        result.add(new NavigationStationDisplay(names.get(1), false, false));
        int hidden = Math.max(0, names.size() - 4);
        result.add(new NavigationStationDisplay(Component.translatable("screen.superpipeslide.navigation.itinerary.more_stops", hidden).getString(), true, false));
        result.add(new NavigationStationDisplay(names.get(names.size() - 2), false, false));
        result.add(new NavigationStationDisplay(names.getLast(), false, true));
        return result;
    }

    private int navigationItineraryStepHeight(FullMapNavigationViewModel.ItineraryStep step, int index) {
        if (step.kind() != FullMapNavigationViewModel.ItineraryKind.RIDE) {
            return NAVIGATION_SIMPLE_STEP_HEIGHT;
        }
        int stationRows = Math.max(1, this.displayedNavigationStations(step, index).size());
        return 25 + stationRows * NAVIGATION_RIDE_STATION_ROW_HEIGHT;
    }

    private int navigationItineraryContentHeight(FullMapNavigationViewModel.RoutePreview preview) {
        int height = 8;
        for (int i = 0; i < preview.itinerary().size(); i++) {
            height += this.navigationItineraryStepHeight(preview.itinerary().get(i), i) + NAVIGATION_ITINERARY_STEP_GAP;
        }
        return height;
    }

    private double maxNavigationItineraryScroll(FullMapNavigationViewModel.RoutePreview preview, SPSGui.Rect rect) {
        return Math.max(0.0D, this.navigationItineraryContentHeight(preview) - rect.height());
    }

    private void drawNavigationDottedLine(GuiGraphicsExtractor graphics, int x, int y1, int y2, int color) {
        for (int y = y1; y < y2; y += 7) {
            SmoothGuiPrimitives.line(graphics, new Vec2(x, y), new Vec2(x, Math.min(y2, y + 3)), 2.0D, color);
        }
    }

    private void drawNavigationVerticalRail(GuiGraphicsExtractor graphics, int x, int y1, int y2, List<Integer> colors, int width) {
        if (y2 <= y1) {
            return;
        }
        List<Integer> normalized = this.normalizedNavigationColors(colors);
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

    private int drawNavigationLineBadge(GuiGraphicsExtractor graphics, int x, int y, String label, int color, int maxWidth) {
        String text = SPSGui.ellipsize(this.font, label, Math.max(8, Math.round((maxWidth - 7) / ROW_DETAIL_TEXT_SCALE)));
        int width = Math.min(maxWidth, Math.max(24, Math.round(this.font.width(text) * ROW_DETAIL_TEXT_SCALE) + 8));
        graphics.fill(x, y, x + width, y + 12, SPSGui.withAlpha(color, 0xDD));
        graphics.outline(x, y, width, 12, SPSGui.withAlpha(color, 0xF2));
        SPSGui.smallText(graphics, this.font, text, x + 4, y + 3, 0xFFFFFFFF, ROW_DETAIL_TEXT_SCALE);
        return width;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void renderToast(GuiGraphicsExtractor graphics) {
        if (this.toast.isEmpty() || System.currentTimeMillis() > this.toastUntilMillis) {
            return;
        }
        int width = this.font.width(this.toast) + 16;
        int actionWidth = this.toastAction == null ? 0 : this.font.width(this.toastActionLabel) + 14;
        SPSGui.Rect rect = new SPSGui.Rect((this.width - width - actionWidth) / 2, this.height - 30, width + actionWidth, 18);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), 0xDD111820);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), SPSGui.INFO);
        SPSGui.centeredText(graphics, this.font, this.toast, rect.x() + width / 2, rect.y() + 5, 0xFFFFFFFF);
        if (this.toastAction != null) {
            SPSGui.Rect action = new SPSGui.Rect(rect.right() - actionWidth + 1, rect.y() + 1, actionWidth - 2, 16);
            graphics.outline(action.x(), action.y(), action.width(), action.height(), SPSGui.withAlpha(SPSGui.INFO, 0x88));
            SPSGui.centeredText(graphics, this.font, this.toastActionLabel, action.x() + action.width() / 2, action.y() + 5, SPSGui.INFO);
            this.addClick(action, this::runToastAction, Component.translatable("screen.superpipeslide.full_map.navigation.undo"));
        }
    }

    private void renderContextPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.contextPickerBounds = new SPSGui.Rect(0, 0, 0, 0);
        this.contextPickerActionBounds.clear();
        this.contextPickerRowBounds.clear();
        this.contextPickerMaxScroll = 0.0D;
        ContextPicker picker = this.contextPicker.orElse(null);
        if (picker == null || (picker.rows().isEmpty() && picker.actions().isEmpty())) {
            return;
        }
        int width = Math.max(142, Math.min(Math.min(278, Math.max(142, picker.boundary().width() - 12)), this.contextPickerPreferredWidth(picker)));
        int headerHeight = picker.title().hasSecondary()
                ? (picker.subtitle().isBlank() ? 34 : 43)
                : (picker.subtitle().isBlank() ? 26 : 36);
        int rowHeight = 28;
        int contentHeight = picker.rows().size() * rowHeight;
        int maxHeight = Math.max(64, Math.min(360, picker.boundary().height() - 12));
        int height = Math.min(maxHeight, headerHeight + contentHeight + 8);
        this.contextPickerBounds = FullMapTooltipCard.place(new SPSGui.Rect(picker.anchorX() + 10, picker.anchorY() + 10, width, height), picker.boundary());
        this.contextPickerMaxScroll = Math.max(0.0D, headerHeight + contentHeight + 8 - height);
        this.contextPickerScroll = Math.max(0.0D, Math.min(this.contextPickerScroll, this.contextPickerMaxScroll));

        SPSGui.Rect bounds = this.contextPickerBounds;
        graphics.fill(bounds.x() + 2, bounds.y() + 3, bounds.right() + 2, bounds.bottom() + 3, FullMapTheme.SHADOW);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), FullMapTheme.SURFACE_CARD_ACTIVE);
        graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), FullMapTheme.BORDER_ACTIVE);
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.y() + 2, FullMapTheme.INNER_HIGHLIGHT_STRONG);
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.y() + headerHeight - 1, FullMapTheme.SURFACE_HEADER_ACTIVE);
        graphics.fill(bounds.x() + 1, bounds.y() + headerHeight, bounds.right() - 1, bounds.y() + headerHeight + 1, FullMapTheme.BORDER);
        int headerY = bounds.y() + 6;
        int actionRight = bounds.right() - 6;
        for (int i = picker.actions().size() - 1; i >= 0; i--) {
            ContextPickerAction action = picker.actions().get(i);
            SPSGui.Rect actionBounds = new SPSGui.Rect(actionRight - 16, bounds.y() + 5, 16, 16);
            this.contextPickerActionBounds.add(0, actionBounds);
            FullMapUi.iconButton(graphics, actionBounds, actionBounds.contains(mouseX, mouseY), false, false, action.icon());
            actionRight = actionBounds.x() - 4;
        }
        FullMapUi.drawNameStack(graphics, this.font, picker.title(), bounds.x() + 7, headerY, Math.max(32, actionRight - bounds.x() - 11), FullMapTheme.TEXT_PRIMARY, FullMapTheme.TEXT_MUTED, 1.0F, FullMapTheme.TYPE_META, 0);
        if (!picker.subtitle().isBlank()) {
            int subtitleY = headerY + FullMapUi.nameStackHeight(picker.title(), 1.0F, FullMapTheme.TYPE_META, 0) + 2;
            SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, picker.subtitle(), Math.round(Math.max(32, actionRight - bounds.x() - 11) / FullMapTheme.TYPE_META)), bounds.x() + 7, subtitleY, FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_META);
        }

        SPSGui.Rect list = new SPSGui.Rect(bounds.x() + 4, bounds.y() + headerHeight + 4, bounds.width() - 8, Math.max(1, bounds.height() - headerHeight - 8));
        graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        int y = (int) Math.round(list.y() - this.contextPickerScroll);
        for (int i = 0; i < picker.rows().size(); i++) {
            ContextPickerRow row = picker.rows().get(i);
            SPSGui.Rect rowBounds = new SPSGui.Rect(list.x(), y, list.width(), rowHeight - 3);
            this.contextPickerRowBounds.add(rectsOverlap(rowBounds, list) ? clipRect(rowBounds, list) : new SPSGui.Rect(0, 0, 0, 0));
            if (rowBounds.bottom() >= list.y() && rowBounds.y() <= list.bottom()) {
                boolean hovered = rowBounds.contains(mouseX, mouseY);
                graphics.fill(rowBounds.x(), rowBounds.y(), rowBounds.right(), rowBounds.bottom(), hovered ? FullMapTheme.HIGHLIGHT_SOFT : FullMapTheme.SURFACE_CONTROL);
                graphics.outline(rowBounds.x(), rowBounds.y(), rowBounds.width(), rowBounds.height(), hovered ? FullMapTheme.BORDER_SELECTED : FullMapTheme.BORDER);
                FullMapUi.drawThemeBands(graphics, rowBounds, row.colors());
                FullMapUi.drawNamePrimary(graphics, this.font, row.title(), rowBounds.x() + 8, rowBounds.y() + 3, rowBounds.width() - 23, FullMapTheme.TEXT_PRIMARY, 1.0F);
                if (!row.subtitle().isBlank() || row.title().hasSecondary()) {
                    String subtitle = row.title().hasSecondary()
                            ? row.title().secondary() + (row.subtitle().isBlank() ? "" : " · " + row.subtitle())
                            : row.subtitle();
                    SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, subtitle, Math.round((rowBounds.width() - 23) / FullMapTheme.TYPE_TINY)), rowBounds.x() + 8, rowBounds.y() + 15, FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_TINY);
                }
                SPSGui.smallText(graphics, this.font, ">", rowBounds.right() - 11, rowBounds.y() + 9, hovered ? SPSGui.INFO : FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_META);
            }
            y += rowHeight;
        }
        graphics.disableScissor();
        if (this.contextPickerMaxScroll > 0.0D) {
            FullMapScrollBar.Binding binding = new FullMapScrollBar.Binding(
                    "contextPicker",
                    list,
                    contentHeight,
                    this.contextPickerScroll,
                    this.contextPickerMaxScroll,
                    FullMapScrollBar.FLOATING,
                    true,
                    value -> this.contextPickerScroll = value);
            this.registerScrollBar(binding);
            FullMapScrollBar.render(graphics, binding);
        }
    }

    private int contextPickerPreferredWidth(ContextPicker picker) {
        int width = Math.max(FullMapUi.nameStackWidth(this.font, picker.title(), 1.0F, FullMapTheme.TYPE_META) + 20, Math.round(this.font.width(picker.subtitle()) * FullMapTheme.TYPE_META) + 20);
        if (!picker.actions().isEmpty()) {
            width += picker.actions().size() * 20;
        }
        for (ContextPickerRow row : picker.rows()) {
            width = Math.max(width, FullMapUi.nameStackWidth(this.font, row.title(), 1.0F, FullMapTheme.TYPE_TINY) + 36);
            if (!row.subtitle().isBlank()) {
                width = Math.max(width, Math.round(this.font.width(row.subtitle()) * FullMapTheme.TYPE_TINY) + 36);
            }
        }
        return width;
    }

    private void renderLayoutModeStrip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int size = FullMapTheme.ICON_BUTTON;
        int gap = 2;
        SPSGui.Rect panel = this.layoutModeStripBounds;
        FullMapUi.toolbarPanel(graphics, panel);
        int x = panel.x() + 3;
        int y = panel.y() + 3;
        for (FullRouteMapLayoutMode mode : FullRouteMapLayoutMode.values()) {
            SPSGui.Rect button = new SPSGui.Rect(x, y, size, size);
            boolean selected = FullRouteMapCache.layoutMode() == mode;
            boolean hovered = button.contains(mouseX, mouseY);
            FullMapUi.iconButton(graphics, button, hovered, selected, false, layoutModeGlyph(mode));
            this.addClick(button, () -> this.selectLayoutMode(mode), layoutModeTooltip(mode));
            x += size + gap;
        }
    }

    private SPSGui.Rect computeLayoutModeStripBounds() {
        int size = FullMapTheme.ICON_BUTTON;
        int gap = 2;
        int x = 10;
        int y = this.height - size - 8;
        return new SPSGui.Rect(x - 3, y - 3, FullRouteMapLayoutMode.values().length * size + (FullRouteMapLayoutMode.values().length - 1) * gap + 6, size + 6);
    }

    private void renderBuildingIndicator(GuiGraphicsExtractor graphics) {
        if (!FullRouteMapCache.building()) {
            return;
        }
        SPSGui.smallText(graphics, this.font, Component.literal("Building map…").getString(), this.layoutModeStripBounds.right() + 8, this.layoutModeStripBounds.y() + 7, FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_META);
    }

    /**
     * Opt-in debug overlay ({@link ClientConfig#FULL_MAP_DEBUG_OVERLAY}) drawing the active
     * dimension's schematic quality summary as a single line in the bottom-left corner, just
     * above the layout-mode strip. Every number comes straight from the solver's quality report.
     */
    private void renderSchematicQualityOverlay(GuiGraphicsExtractor graphics, VisualRouteMapGraph visualGraph) {
        if (!ClientConfig.FULL_MAP_DEBUG_OVERLAY.get() || visualGraph == null) {
            return;
        }
        SchematicQualityReport quality = visualGraph.quality();
        String summary = "solve " + quality.solveTimeMillis() + "ms"
                + " · overlaps " + quality.nodeOverlapCount()
                + " · crossings " + quality.edgeCrossingCount()
                + " · labels " + quality.labelOverlapCount()
                + " · profile " + quality.profileName()
                + (quality.timeout() ? " · timeout" : "");
        SPSGui.smallText(graphics, this.font, Component.literal(summary).getString(), this.layoutModeStripBounds.x(), this.layoutModeStripBounds.y() - 11, FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_META);
    }

    private void renderCameraCompass(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (FullRouteMapCache.layoutMode() == FullRouteMapLayoutMode.SCHEMATIC || this.cameraCompassBounds.width() <= 0 || this.cameraCompassBounds.height() <= 0) {
            return;
        }
        ViewportState viewport = this.currentViewport().orElse(null);
        if (viewport == null) {
            return;
        }
        SPSGui.Rect bounds = this.cameraCompassBounds;
        boolean active = viewport.cameraTilted();
        boolean hovered = bounds.contains(mouseX, mouseY);
        FullMapUi.toolbarPanel(graphics, bounds);
        SPSGui.Rect button = new SPSGui.Rect(bounds.x() + 3, bounds.y() + 3, FullMapTheme.ICON_BUTTON, FullMapTheme.ICON_BUTTON);
        FullMapUi.iconButton(graphics, button, hovered, active, false, SPSGui.Icon.RESET_VIEW);
        this.drawCompassNeedle(graphics, button, viewport);
        this.addClick(button, this::resetCamera, cameraTooltip(viewport));
    }

    private void drawCompassNeedle(GuiGraphicsExtractor graphics, SPSGui.Rect button, ViewportState viewport) {
        Vec2 center = new Vec2(button.x() + button.width() * 0.5D, button.y() + button.height() * 0.5D);
        double angle = Math.toRadians(viewport.bearingDegrees() - 90.0D);
        double dx = Math.cos(angle);
        double dy = Math.sin(angle);
        Vec2 tip = new Vec2(center.x() + dx * 6.0D, center.y() + dy * 6.0D);
        Vec2 tail = new Vec2(center.x() - dx * 4.0D, center.y() - dy * 4.0D);
        SmoothGuiPrimitives.line(graphics, tail, tip, 1.2D, viewport.cameraTilted() ? SPSGui.INFO : FullMapTheme.TEXT_MUTED);
        SPSGui.smallText(graphics, this.font, "N", (int) Math.round(tip.x() - 2.0D), (int) Math.round(tip.y() - 4.0D), viewport.cameraTilted() ? SPSGui.INFO : FullMapTheme.TEXT_MUTED, COMPASS_TEXT_SCALE);
    }

    private SPSGui.Rect computeCameraCompassBounds() {
        if (FullRouteMapCache.layoutMode() == FullRouteMapLayoutMode.SCHEMATIC) {
            return new SPSGui.Rect(0, 0, 0, 0);
        }
        int size = FullMapTheme.ICON_BUTTON + 6;
        return new SPSGui.Rect(this.width - size - 10, Math.max(36, this.height - size - 42), size, size);
    }

    private void resetCamera() {
        if (this.activeDimension == null) {
            return;
        }
        this.cancelViewportTween(this.activeDimension);
        ViewportState viewport = this.viewports.get(this.activeDimension);
        if (viewport != null) {
            this.viewports.put(this.activeDimension, viewport.withFlatCamera());
        }
        this.toast(Component.translatable("screen.superpipeslide.full_map.camera.reset_done").getString());
    }

    private static Component cameraTooltip(ViewportState viewport) {
        return Component.translatable(
                "screen.superpipeslide.full_map.camera.tooltip",
                String.format(Locale.ROOT, "%.0f", viewport.pitchDegrees()),
                String.format(Locale.ROOT, "%.0f", viewport.bearingDegrees()));
    }

    private SPSGui.Rect computeSchematicLegendBounds() {
        if (FullRouteMapCache.layoutMode() != FullRouteMapLayoutMode.SCHEMATIC) {
            return new SPSGui.Rect(0, 0, 0, 0);
        }
        Optional<MapDimensionGraph> graph = this.currentGraph();
        Optional<VisualRouteMapGraph> visualGraph = this.activeDimension == null ? Optional.empty() : FullRouteMapCache.visualGraph(this.activeDimension);
        if (graph.isEmpty() || visualGraph.isEmpty()) {
            return new SPSGui.Rect(0, 0, 0, 0);
        }
        ViewportState viewport = this.displayViewport(graph.get());
        List<SchematicLegendRow> rows = this.schematicLegendRows(graph.get(), visualGraph.get(), viewport);
        int rowHeight = schematicLegendRowHeight();
        int headerHeight = 22;
        int width = this.schematicLegendWidth(rows);
        if (this.schematicLegendCollapsed) {
            return new SPSGui.Rect(this.width - width - 10, this.height - headerHeight - 10, width, headerHeight);
        }
        int visibleRows = Math.max(1, Math.min(schematicLegendMaxRows(), Math.max(1, rows.size())));
        int height = headerHeight + visibleRows * rowHeight + 7;
        return new SPSGui.Rect(this.width - width - 10, this.height - height - 10, width, height);
    }

    private void renderSchematicRouteLegend(GuiGraphicsExtractor graphics, MapDimensionGraph graph, VisualRouteMapGraph visualGraph, int mouseX, int mouseY) {
        if (FullRouteMapCache.layoutMode() != FullRouteMapLayoutMode.SCHEMATIC || graph == null || visualGraph == null || this.schematicLegendBounds.width() <= 0) {
            this.schematicLegendMaxScroll = 0.0D;
            return;
        }
        ViewportState viewport = this.displayViewport(graph);
        List<SchematicLegendRow> rows = this.schematicLegendRows(graph, visualGraph, viewport);
        SPSGui.Rect bounds = this.schematicLegendBounds;
        FullMapUi.toolbarPanel(graphics, bounds);
        SPSGui.icon(graphics, new SPSGui.Rect(bounds.x() + 5, bounds.y() + 4, 14, 14), SPSGui.Icon.ROUTE_LINE, FullMapTheme.TEXT_SECONDARY);
        SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.full_map.visible_routes").getString(), bounds.x() + 22, bounds.y() + 7, FullMapTheme.TEXT_PRIMARY, FullMapTheme.TYPE_META);
        SPSGui.Rect toggle = new SPSGui.Rect(bounds.right() - 19, bounds.y() + 3, 16, 16);
        boolean toggleHovered = toggle.contains(mouseX, mouseY);
        FullMapUi.iconButton(graphics, toggle, toggleHovered, false, false, this.schematicLegendCollapsed ? SPSGui.Icon.PLUS : SPSGui.Icon.CLOSE);
        this.addClick(toggle, () -> {
            this.schematicLegendCollapsed = !this.schematicLegendCollapsed;
            if (this.schematicLegendCollapsed) {
                this.schematicLegendHoverRouteLineId = Optional.empty();
            }
        }, Component.translatable(this.schematicLegendCollapsed ? "screen.superpipeslide.full_map.visible_routes.expand" : "screen.superpipeslide.full_map.visible_routes.collapse"));
        int countRight = toggle.x() - 4;
        SPSGui.smallText(graphics, this.font, Integer.toString(rows.size()), countRight - Math.round(this.font.width(Integer.toString(rows.size())) * FullMapTheme.TYPE_META), bounds.y() + 7, FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_META);
        if (this.schematicLegendCollapsed) {
            this.schematicLegendMaxScroll = 0.0D;
            this.schematicLegendScroll = 0.0D;
            this.addClick(bounds, () -> this.schematicLegendCollapsed = false, Component.translatable("screen.superpipeslide.full_map.visible_routes.expand"));
            return;
        }

        SPSGui.Rect list = new SPSGui.Rect(bounds.x() + 4, bounds.y() + 22, bounds.width() - 8, bounds.height() - 26);
        int rowHeight = schematicLegendRowHeight();
        int contentHeight = rows.isEmpty() ? rowHeight : rows.size() * rowHeight;
        this.schematicLegendMaxScroll = Math.max(0.0D, contentHeight - list.height());
        this.schematicLegendScroll = Math.max(0.0D, Math.min(this.schematicLegendScroll, this.schematicLegendMaxScroll));
        if (rows.isEmpty()) {
            SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.full_map.visible_routes.empty").getString(), list.x() + 4, list.y() + 6, FullMapTheme.TEXT_MUTED, FullMapTheme.TYPE_TINY);
            return;
        }
        graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        int y = (int) Math.round(list.y() - this.schematicLegendScroll);
        for (SchematicLegendRow row : rows) {
            SPSGui.Rect rowBounds = new SPSGui.Rect(list.x(), y, list.width() - (this.schematicLegendMaxScroll > 0.0D ? 6 : 0), rowHeight - 2);
            boolean hovered = rowBounds.contains(mouseX, mouseY) && list.contains(mouseX, mouseY);
            boolean selected = this.schematicLegendHoverRouteLineId.filter(row.line().id()::equals).isPresent();
            if (rectsOverlap(rowBounds, list)) {
                this.drawSchematicLegendRow(graphics, row, rowBounds, hovered || selected);
                SPSGui.Rect clipped = clipRect(rowBounds, list);
                this.addClick(clipped, () -> this.pushCard(MapCard.routeLine(row.line().id(), row.layoutId(), graph.levelKey(), RouteCardViewMode.SCHEMATIC)), Component.translatable("screen.superpipeslide.full_map.open_route_card", FullMapText.primaryName(row.line())));
            }
            y += rowHeight;
        }
        graphics.disableScissor();
        if (this.schematicLegendMaxScroll > 0.0D) {
            FullMapScrollBar.Binding binding = new FullMapScrollBar.Binding(
                    "schematicLegend",
                    list,
                    contentHeight,
                    this.schematicLegendScroll,
                    this.schematicLegendMaxScroll,
                    FullMapScrollBar.LEGEND,
                    true,
                    value -> this.schematicLegendScroll = value);
            this.registerScrollBar(binding);
            FullMapScrollBar.render(graphics, binding);
        }
    }

    private void drawSchematicLegendRow(GuiGraphicsExtractor graphics, SchematicLegendRow row, SPSGui.Rect rowBounds, boolean hovered) {
        graphics.fill(rowBounds.x(), rowBounds.y(), rowBounds.right(), rowBounds.bottom(), hovered ? FullMapTheme.SURFACE_CONTROL_HOVER : FullMapTheme.SURFACE_CONTROL);
        graphics.outline(rowBounds.x(), rowBounds.y(), rowBounds.width(), rowBounds.height(), hovered ? FullMapTheme.BORDER_SELECTED : FullMapTheme.BORDER);
        FullMapUi.drawThemeBands(graphics, new SPSGui.Rect(rowBounds.x() + 2, rowBounds.y() + 2, 6, rowBounds.height() - 4), routeLineColors(row.line()));
        DisplayNameStack name = FullMapText.displayNameStack(row.line());
        int nameX = rowBounds.x() + 11;
        int nameWidth = Math.max(24, rowBounds.width() - 16);
        if (name.hasSecondary()) {
            FullMapUi.drawNameStack(graphics, this.font, name, nameX, rowBounds.y() + 3, nameWidth, hovered ? SPSGui.INFO : FullMapTheme.TEXT_PRIMARY, FullMapTheme.TEXT_MUTED, ROW_TITLE_TEXT_SCALE, ROW_SUB_TEXT_SCALE, 0);
        } else {
            SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, name.primary(), Math.round(nameWidth / ROW_TITLE_TEXT_SCALE)), nameX, rowBounds.y() + 6, hovered ? SPSGui.INFO : FullMapTheme.TEXT_PRIMARY, ROW_TITLE_TEXT_SCALE);
        }
    }

    private Optional<ViewportState> currentViewport() {
        if (this.activeDimension == null) {
            return Optional.empty();
        }
        if (FullRouteMapCache.layoutMode().physical()) {
            return FullRouteMapCache.physicalGraph(this.activeDimension).map(this::viewportFor);
        }
        return this.currentGraph().map(this::displayViewport);
    }

    private List<SchematicLegendRow> schematicLegendRows(MapDimensionGraph graph, VisualRouteMapGraph visualGraph, ViewportState viewport) {
        if (FullRouteMapCache.layoutMode() != FullRouteMapLayoutMode.SCHEMATIC || graph == null || visualGraph == null) {
            return List.of();
        }
        // Memoize per frame: chrome bounds, hover lookup and legend rendering all need the
        // same rows, and recomputing walks every edge path through worldToScreen.
        if (graph == this.cachedSchematicLegendGraph
                && visualGraph == this.cachedSchematicLegendVisualGraph
                && viewport.equals(this.cachedSchematicLegendViewport)
                && this.mapRect.width() == this.cachedSchematicLegendMapWidth
                && this.mapRect.height() == this.cachedSchematicLegendMapHeight) {
            return this.cachedSchematicLegendRows;
        }
        List<SchematicLegendRow> rows = this.computeSchematicLegendRows(graph, visualGraph, viewport);
        this.cachedSchematicLegendGraph = graph;
        this.cachedSchematicLegendVisualGraph = visualGraph;
        this.cachedSchematicLegendViewport = viewport;
        this.cachedSchematicLegendMapWidth = this.mapRect.width();
        this.cachedSchematicLegendMapHeight = this.mapRect.height();
        this.cachedSchematicLegendRows = rows;
        return rows;
    }

    private List<SchematicLegendRow> computeSchematicLegendRows(MapDimensionGraph graph, VisualRouteMapGraph visualGraph, ViewportState viewport) {
        Map<UUID, SchematicLegendAccumulator> accumulators = new LinkedHashMap<>();
        for (VisualEdgePath path : visualGraph.edgePaths()) {
            if (path.points().size() < 2 || path.routeLineIds().isEmpty()) {
                continue;
            }
            List<Vec2> screenPath = path.points().stream()
                    .map(point -> FullRouteMapRenderer.worldToScreen(point.x(), point.y(), viewport, this.mapRect))
                    .toList();
            double visibleLength = visibleScreenPathLength(screenPath, this.mapRect);
            if (visibleLength <= 0.0D) {
                continue;
            }
            for (UUID routeLineId : path.routeLineIds()) {
                SchematicLegendAccumulator accumulator = accumulators.computeIfAbsent(routeLineId, SchematicLegendAccumulator::new);
                accumulator.visibleLength += visibleLength;
                accumulator.pathCount++;
                if (accumulator.layoutId.isEmpty()) {
                    accumulator.layoutId = path.occurrences().stream()
                            .filter(occurrence -> occurrence.routeLineId().equals(routeLineId))
                            .map(MapEdgeOccurrence::routeLayoutId)
                            .findFirst();
                }
            }
        }
        return accumulators.values().stream()
                .map(accumulator -> ClientRouteDataCache.routeLine(accumulator.routeLineId)
                        .map(line -> new SchematicLegendRow(line, accumulator.visibleLength, accumulator.pathCount, accumulator.layoutId))
                        .orElse(null))
                .filter(row -> row != null)
                .sorted(Comparator.comparingDouble(SchematicLegendRow::visibleLength).reversed()
                        .thenComparing(row -> FullMapText.displayName(row.line())))
                .toList();
    }

    private Optional<UUID> hoveredSchematicLegendRoute(List<SchematicLegendRow> rows, int mouseX, int mouseY) {
        if (this.schematicLegendCollapsed || FullRouteMapCache.layoutMode() != FullRouteMapLayoutMode.SCHEMATIC || rows.isEmpty() || !this.schematicLegendBounds.contains(mouseX, mouseY)) {
            return Optional.empty();
        }
        SPSGui.Rect list = new SPSGui.Rect(this.schematicLegendBounds.x() + 4, this.schematicLegendBounds.y() + 22, this.schematicLegendBounds.width() - 8, this.schematicLegendBounds.height() - 26);
        if (!list.contains(mouseX, mouseY)) {
            return Optional.empty();
        }
        int rowHeight = schematicLegendRowHeight();
        int index = (int) Math.floor((mouseY - list.y() + this.schematicLegendScroll) / rowHeight);
        if (index < 0 || index >= rows.size()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(index).line().id());
    }

    private int schematicLegendWidth(List<SchematicLegendRow> rows) {
        int width = Math.round(this.font.width(Component.translatable("screen.superpipeslide.full_map.visible_routes").getString()) * FullMapTheme.TYPE_META) + 44;
        for (SchematicLegendRow row : rows) {
            width = Math.max(width, FullMapUi.nameStackWidth(this.font, FullMapText.displayNameStack(row.line()), ROW_TITLE_TEXT_SCALE, ROW_SUB_TEXT_SCALE) + 30);
        }
        return Math.max(126, Math.min(178, width));
    }

    private int schematicLegendMaxRows() {
        return Math.max(3, Math.min(9, (this.height - 86) / schematicLegendRowHeight()));
    }

    private static int schematicLegendRowHeight() {
        return 22;
    }

    private static double visibleScreenPathLength(List<Vec2> screenPath, SPSGui.Rect rect) {
        double length = 0.0D;
        SPSGui.Rect expanded = new SPSGui.Rect(rect.x() - 8, rect.y() - 8, rect.width() + 16, rect.height() + 16);
        for (int i = 0; i + 1 < screenPath.size(); i++) {
            Vec2 a = screenPath.get(i);
            Vec2 b = screenPath.get(i + 1);
            if (segmentMayIntersectRect(a, b, expanded)) {
                length += a.distanceTo(b);
            }
        }
        return length;
    }

    private static boolean segmentMayIntersectRect(Vec2 a, Vec2 b, SPSGui.Rect rect) {
        if (rect.contains(a.x(), a.y()) || rect.contains(b.x(), b.y())) {
            return true;
        }
        double minX = Math.min(a.x(), b.x());
        double maxX = Math.max(a.x(), b.x());
        double minY = Math.min(a.y(), b.y());
        double maxY = Math.max(a.y(), b.y());
        return maxX >= rect.x() && minX <= rect.right() && maxY >= rect.y() && minY <= rect.bottom();
    }

    private boolean mapChromeBlocks(double mouseX, double mouseY) {
        return this.mapChromeBounds.stream().anyMatch(bounds -> bounds.contains(mouseX, mouseY));
    }

    private boolean mapPopoverBlocks(double mouseX, double mouseY) {
        return (this.dimensionMenuBounds.width() > 0 && this.dimensionMenuBounds.contains(mouseX, mouseY))
                || (this.searchResultsBounds.width() > 0 && this.searchResultsBounds.contains(mouseX, mouseY))
                || (this.navigationDrawerBounds.width() > 0 && this.navigationDrawerBounds.contains(mouseX, mouseY))
                || (this.activeNavigationPillBounds.width() > 0 && this.activeNavigationPillBounds.contains(mouseX, mouseY));
    }

    private SPSGui.Rect computeDimensionChipBounds() {
        String label = this.activeDimension == null ? "?" : dimensionLabel(this.activeDimension);
        int width = Math.max(96, Math.min(168, this.font.width(label) + 46));
        return new SPSGui.Rect(8, 8, width, 22);
    }

    private SPSGui.Rect computeDimensionMenuBounds() {
        if (!this.dimensionMenuOpen) {
            return new SPSGui.Rect(0, 0, 0, 0);
        }
        List<ResourceKey<Level>> dimensions = sortedDimensions();
        if (dimensions.isEmpty()) {
            return new SPSGui.Rect(0, 0, 0, 0);
        }
        int width = Math.max(this.dimensionChipBounds.width(), Math.min(220, dimensions.stream().mapToInt(value -> this.font.width(dimensionLabel(value))).max().orElse(80) + 76));
        int height = Math.min(Math.max(32, this.height - 44), dimensions.size() * 24 + 8);
        return new SPSGui.Rect(this.dimensionChipBounds.x(), this.dimensionChipBounds.bottom() + 4, width, height);
    }

    private SPSGui.Rect computeSearchControlBounds() {
        int desiredWidth = Math.min(370, Math.max(260, (int) Math.round(this.width * 0.30D)));
        int x = this.dimensionChipBounds.right() + 8;
        int y = 8;
        int maxRight = this.width - 10;
        int width = Math.min(desiredWidth, maxRight - x);
        if (width < 220) {
            x = 8;
            y = this.dimensionChipBounds.bottom() + 6;
            width = Math.min(desiredWidth, maxRight - x);
        }
        width = Math.max(168, width);
        return new SPSGui.Rect(x, y, width, 28);
    }

    private SPSGui.Rect computeSearchResultsBounds() {
        if (this.searchBox == null) {
            return new SPSGui.Rect(0, 0, 0, 0);
        }
        boolean searchOpen = this.searchExpanded || this.searchBox.isFocused();
        if (!searchOpen) {
            return new SPSGui.Rect(0, 0, 0, 0);
        }
        int rows = Math.min(8, this.navigationDestinationResults().size() + this.searchResults().size());
        int height = rows <= 0 ? 42 : Math.min(Math.max(42, this.height - this.searchControlBounds.bottom() - 18), rows * 29 + 8);
        return new SPSGui.Rect(this.searchControlBounds.x(), this.searchControlBounds.bottom() + 5, this.searchControlBounds.width(), height);
    }

    private SPSGui.Rect computeNavigationDrawerBounds() {
        if (!this.navigationSheetExpanded && this.selectedNavigationStationGroupId == null) {
            this.draggingNavigationDrawer = false;
            return new SPSGui.Rect(0, 0, 0, 0);
        }
        int width = Math.min(280, Math.max(180, Math.round(this.width * 0.20F)));
        width = Math.min(width, Math.max(148, this.width - 24));
        int height = this.navigationSheetExpanded
                ? Math.min(360, Math.max(190, Math.round(this.height * 0.42F)))
                : 118;
        height = Math.min(height, Math.max(96, this.height - 72));
        if (this.navigationDrawerUserBounds != null) {
            int x = Double.isFinite(this.navigationDrawerUserXRatio) ? (int) Math.round(this.navigationDrawerUserXRatio * Math.max(1, this.width - width)) : this.navigationDrawerUserBounds.x();
            int y = Double.isFinite(this.navigationDrawerUserYRatio) ? (int) Math.round(this.navigationDrawerUserYRatio * Math.max(1, this.height - height)) : this.navigationDrawerUserBounds.y();
            return this.clampNavigationDrawerBounds(new SPSGui.Rect(x, y, width, height));
        }
        int x = this.width - width - 12;
        int y = Math.max(this.searchControlBounds.bottom() + 10, 48);
        return this.clampNavigationDrawerBounds(new SPSGui.Rect(x, y, width, height));
    }

    private SPSGui.Rect navigationDrawerDragHandleBounds() {
        SPSGui.Rect panel = this.navigationDrawerBounds;
        if (panel.width() <= 0 || panel.height() <= 0) {
            return new SPSGui.Rect(0, 0, 0, 0);
        }
        return new SPSGui.Rect(panel.x() + 6, panel.y() + 5, Math.max(0, panel.width() - 12), 58);
    }

    private SPSGui.Rect clampNavigationDrawerBounds(SPSGui.Rect bounds) {
        int margin = 8;
        int width = Math.min(bounds.width(), Math.max(1, this.width - margin * 2));
        int height = Math.min(bounds.height(), Math.max(1, this.height - margin * 2));
        int maxX = Math.max(margin, this.width - width - margin);
        int maxY = Math.max(margin, this.height - height - margin);
        int x = clampInt(bounds.x(), margin, maxX);
        int y = clampInt(bounds.y(), margin, maxY);
        return new SPSGui.Rect(x, y, width, height);
    }

    private SPSGui.Rect computeActiveNavigationPillBounds() {
        if (!ClientNavigationController.isNavigating() || this.navigationSheetExpanded || this.selectedNavigationStationGroupId != null) {
            return new SPSGui.Rect(0, 0, 0, 0);
        }
        int width = Math.min(330, Math.max(230, Math.round(this.width * 0.24F)));
        width = Math.min(width, Math.max(180, this.width - 24));
        int height = 34;
        int x = this.width - width - 12;
        int y = Math.max(this.searchControlBounds.bottom() + 10, 48);
        return new SPSGui.Rect(x, y, width, height);
    }

    private void focusSearch() {
        this.dimensionMenuOpen = false;
        this.searchExpanded = true;
        if (this.searchBox != null) {
            this.searchBox.setFocused(true);
            this.setFocused(this.searchBox);
        }
    }

    private SPSGui.Rect screenBounds() {
        return new SPSGui.Rect(0, 0, this.width, this.height);
    }

    private void selectLayoutMode(FullRouteMapLayoutMode next) {
        this.clearContextPicker();
        this.dimensionMenuOpen = false;
        this.searchExpanded = false;
        if (this.searchBox != null && this.searchBox.getValue().isBlank()) {
            this.searchBox.setFocused(false);
        }
        FullRouteMapCache.setLayoutMode(next);
        FullRouteMapCache.refresh(true);
        this.resetViewport();
        this.toast(Component.translatable("screen.superpipeslide.full_map.layout_mode.changed", layoutModeName(next)).getString());
    }

    private static SPSGui.Icon layoutModeGlyph(FullRouteMapLayoutMode mode) {
        return switch (mode) {
            case PHYSICAL -> SPSGui.Icon.MODE_PHYSICAL;
            case GEOGRAPHIC -> SPSGui.Icon.MODE_GEOGRAPHIC;
            case PRACTICAL -> SPSGui.Icon.MODE_PRACTICAL;
            case SCHEMATIC -> SPSGui.Icon.MODE_SCHEMATIC;
        };
    }

    private void renderScaleBar(GuiGraphicsExtractor graphics) {
        if (FullRouteMapCache.layoutMode() == FullRouteMapLayoutMode.SCHEMATIC) {
            return;
        }
        Optional<MapDimensionGraph> graph = this.currentGraph();
        if (graph.isEmpty()) {
            return;
        }
        ViewportState viewport = this.displayViewport(graph.get());
        double pixelsPerBlock = viewport.zoom() * FullRouteMapConfig.BASE_SCALE;
        if (pixelsPerBlock <= 0.001D) {
            return;
        }
        double blockLength = niceScaleLength(112.0D / pixelsPerBlock);
        int pixelLength = Math.max(32, (int) Math.round(blockLength * pixelsPerBlock));
        int x = this.mapRect.right() - pixelLength - 18;
        int y = this.mapRect.bottom() - 18;
        SPSGui.Rect box = new SPSGui.Rect(x - 6, y - 10, pixelLength + 12, 20);
        graphics.fill(box.x(), box.y(), box.right(), box.bottom(), FullMapTheme.SURFACE_TOOLBAR);
        graphics.outline(box.x(), box.y(), box.width(), box.height(), FullMapTheme.BORDER);
        graphics.fill(x, y, x + pixelLength, y + 2, FullRouteMapConfig.MAP_LABEL);
        graphics.fill(x, y - 4, x + 2, y + 6, FullRouteMapConfig.MAP_LABEL);
        graphics.fill(x + pixelLength - 2, y - 4, x + pixelLength, y + 6, FullRouteMapConfig.MAP_LABEL);
        SPSGui.smallText(graphics, this.font, scaleBarLabel(blockLength), x, y - 10, FullRouteMapConfig.MAP_LABEL, SCALE_BAR_TEXT_SCALE);
    }

    private static double niceScaleLength(double targetBlocks) {
        double[] nice = { 4.0D, 8.0D, 16.0D, 32.0D, 64.0D, 128.0D, 256.0D, 512.0D, 1024.0D, 2048.0D, 4096.0D };
        double best = nice[0];
        double bestDistance = Double.POSITIVE_INFINITY;
        for (double value : nice) {
            double distance = Math.abs(value - targetBlocks);
            if (distance < bestDistance) {
                best = value;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static String scaleBarLabel(double blocks) {
        if (blocks >= 1024.0D) {
            return Component.translatable("screen.superpipeslide.full_map.scale_bar_kblocks", String.format(Locale.ROOT, "%.1f", blocks / 1000.0D)).getString();
        }
        return Component.translatable("screen.superpipeslide.full_map.scale_bar_blocks", (int) Math.round(blocks)).getString();
    }

    private static Component layoutModeName(FullRouteMapLayoutMode mode) {
        return Component.translatable(mode.translationKey());
    }

    private static Component layoutModeTooltip(FullRouteMapLayoutMode mode) {
        return Component.literal(layoutModeName(mode).getString() + "\n" + Component.translatable(layoutModeDescriptionKey(mode)).getString());
    }

    private static String layoutModeDescriptionKey(FullRouteMapLayoutMode mode) {
        return "screen.superpipeslide.full_map.layout_mode.description." + mode.name().toLowerCase(Locale.ROOT);
    }

    private static Component routeCardViewModeTooltip(RouteCardViewMode mode) {
        return Component.literal(Component.translatable(mode.translationKey()).getString() + "\n" + Component.translatable(routeCardViewModeDescriptionKey(mode)).getString());
    }

    private static String routeCardViewModeDescriptionKey(RouteCardViewMode mode) {
        return "screen.superpipeslide.full_map.route_card.view_mode.description." + mode.name().toLowerCase(Locale.ROOT);
    }

    private void renderFoldHoverPreview(GuiGraphicsExtractor graphics, MapDimensionGraph graph, VisualRouteMapGraph visualGraph, int mouseX, int mouseY) {
        if (graph == null || !(this.hover instanceof HitTarget.NodeHit(var hoveredNodeId))) {
            return;
        }
        MapNode node = graph.node(hoveredNodeId).orElse(null);
        if (node == null || node.kind() != NodeKind.FOLD_ANCHOR || node.foldPeerId().isEmpty()) {
            return;
        }
        PipeAnchorId peerId = node.foldPeerId().get();
        if (this.foldPeerVisibleInCurrentViewport(graph, visualGraph, peerId)) {
            return;
        }
        int size = 120;
        SPSGui.Rect rect = this.hoverPreviewBounds(mouseX, mouseY, size, size);
        this.hoverOverlayAvoidRects.add(rect);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), SPSGui.PANEL_ELEVATED);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), SPSGui.PANEL_LINE);
        this.renderFoldPeerPreview(graphics, peerId, new SPSGui.Rect(rect.x() + 4, rect.y() + 4, rect.width() - 8, rect.height() - 8), true);
    }

    private void renderClusterHoverPreview(GuiGraphicsExtractor graphics, MapDimensionGraph graph, int mouseX, int mouseY) {
        if (graph == null || !(this.hover instanceof HitTarget.NodeHit(var hoveredNodeId))) {
            return;
        }
        MapNode node = graph.node(hoveredNodeId).orElse(null);
        if (node == null || (node.kind() != NodeKind.CLUSTER && node.kind() != NodeKind.DEEP_CLUSTER)) {
            return;
        }
        MapCluster cluster = findCluster(graph, node.id().primaryId(), node.kind());
        if (cluster == null) {
            return;
        }
        ClusterCardProfile profile = node.kind() == NodeKind.DEEP_CLUSTER ? ClusterCardProfile.DEEP : ClusterCardProfile.ORDINARY;
        ClusterCardGraphBundle graphBundle = this.clusterCardGraph(graph, cluster, profile);
        ClusterCardSemanticGraph semanticGraph = graphBundle.semanticGraph();
        if (semanticGraph.nodes().isEmpty()) {
            return;
        }
        ClusterCardVisualGraph visualGraph = graphBundle.visualGraph();
        SPSGui.Rect rect = this.hoverPreviewBounds(mouseX, mouseY, profile == ClusterCardProfile.DEEP ? 152 : 136, profile == ClusterCardProfile.DEEP ? 124 : 112);
        this.hoverOverlayAvoidRects.add(rect);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), SPSGui.PANEL_ELEVATED);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), SPSGui.PANEL_LINE);
        SPSGui.Rect preview = new SPSGui.Rect(rect.x() + 4, rect.y() + 4, rect.width() - 8, rect.height() - 8);
        ClusterCardViewport viewport = ClusterCardRenderer.fitViewport(visualGraph.bounds(), preview);
        graphics.enableScissor(preview.x(), preview.y(), preview.right(), preview.bottom());
        this.clusterCardRenderer.render(graphics, this.font, semanticGraph, visualGraph, viewport, preview, false, Integer.MIN_VALUE, Integer.MIN_VALUE);
        graphics.disableScissor();
    }

    private List<SPSGui.Rect> renderClusterCardHoverPreview(
            GuiGraphicsExtractor graphics,
            MapDimensionGraph graph,
            ClusterCardSemanticGraph semanticGraph,
            ClusterCardVisualGraph visualGraph,
            ClusterCardViewport viewport,
            SPSGui.Rect map,
            ClusterCardHit hover,
            SPSGui.Rect boundary,
            int mouseX,
            int mouseY) {
        if (hover.kind() != ClusterCardHitKind.NODE || hover.nodeId().isEmpty()) {
            return List.of();
        }
        ClusterCardNode node = semanticGraph.node(hover.nodeId().get()).orElse(null);
        if (node == null) {
            return List.of();
        }
        if (node.kind() == ClusterCardNodeKind.MEMBER_FOLD_ANCHOR && node.foldPeerId().isPresent()) {
            if (this.clusterFoldPeerVisibleInCurrentViewport(semanticGraph, visualGraph, viewport, map, node.foldPeerId().get())) {
                return List.of();
            }
            return this.renderClusterCardFoldPreview(graphics, node.foldPeerId().get(), boundary, mouseX, mouseY);
        }
        if (node.kind() == ClusterCardNodeKind.MEMBER_DEEP_CLUSTER && node.mapNode().isPresent()) {
            return this.renderClusterCardDeepClusterPreview(graphics, graph, node.mapNode().get(), boundary, mouseX, mouseY);
        }
        return List.of();
    }

    private boolean clusterFoldPeerVisibleInCurrentViewport(
            ClusterCardSemanticGraph semanticGraph,
            ClusterCardVisualGraph visualGraph,
            ClusterCardViewport viewport,
            SPSGui.Rect map,
            PipeAnchorId peerId) {
        return semanticGraph.nodes().stream()
                .filter(candidate -> candidate.kind() == ClusterCardNodeKind.MEMBER_FOLD_ANCHOR)
                .filter(candidate -> candidate.foldAnchorId().filter(peerId::equals).isPresent())
                .findFirst()
                .flatMap(candidate -> visualGraph.node(candidate.id()))
                .map(candidate -> clusterCardWorldToScreen(candidate.position(), viewport, map))
                .map(screen -> map.contains(screen.x(), screen.y()))
                .orElse(false);
    }

    private static Vec2 clusterCardWorldToScreen(Vec2 point, ClusterCardViewport viewport, SPSGui.Rect map) {
        double scale = ClusterCardRenderer.scale(viewport);
        return new Vec2(
                map.x() + map.width() * 0.5D + (point.x() - viewport.centerX()) * scale,
                map.y() + map.height() * 0.5D + (point.y() - viewport.centerY()) * scale);
    }

    private List<SPSGui.Rect> renderClusterCardFoldPreview(GuiGraphicsExtractor graphics, PipeAnchorId peerId, SPSGui.Rect boundary, int mouseX, int mouseY) {
        List<SPSGui.Rect> avoidRects = new ArrayList<>();
        SPSGui.Rect rect = this.hoverPreviewBounds(boundary, mouseX, mouseY, 120, 120, avoidRects);
        avoidRects.add(rect);
        this.drawHoverPreviewFrame(graphics, rect);
        this.renderFoldPeerPreview(graphics, peerId, new SPSGui.Rect(rect.x() + 4, rect.y() + 4, rect.width() - 8, rect.height() - 8), true);
        return avoidRects;
    }

    private List<SPSGui.Rect> renderClusterCardDeepClusterPreview(GuiGraphicsExtractor graphics, MapDimensionGraph graph, MapNode node, SPSGui.Rect boundary, int mouseX, int mouseY) {
        MapCluster cluster = findCluster(graph, node.id().primaryId(), NodeKind.DEEP_CLUSTER);
        if (cluster == null) {
            return List.of();
        }
        ClusterCardGraphBundle graphBundle = this.clusterCardGraph(graph, cluster, ClusterCardProfile.DEEP);
        ClusterCardSemanticGraph semanticGraph = graphBundle.semanticGraph();
        if (semanticGraph.nodes().isEmpty()) {
            return List.of();
        }
        ClusterCardVisualGraph visualGraph = graphBundle.visualGraph();
        List<SPSGui.Rect> avoidRects = new ArrayList<>();
        SPSGui.Rect rect = this.hoverPreviewBounds(boundary, mouseX, mouseY, 152, 124, avoidRects);
        avoidRects.add(rect);
        this.drawHoverPreviewFrame(graphics, rect);
        SPSGui.Rect preview = new SPSGui.Rect(rect.x() + 4, rect.y() + 4, rect.width() - 8, rect.height() - 8);
        ClusterCardViewport viewport = ClusterCardRenderer.fitViewport(visualGraph.bounds(), preview);
        graphics.enableScissor(preview.x(), preview.y(), preview.right(), preview.bottom());
        this.clusterCardRenderer.render(graphics, this.font, semanticGraph, visualGraph, viewport, preview, false, Integer.MIN_VALUE, Integer.MIN_VALUE);
        graphics.disableScissor();
        return avoidRects;
    }

    private void renderPhysicalFoldHoverPreview(GuiGraphicsExtractor graphics, PhysicalRouteMapGraph graph, int mouseX, int mouseY) {
        if (graph == null || !(this.hover instanceof HitTarget.PhysicalNodeHit(var hoveredNodeId))) {
            return;
        }
        PhysicalMapNode node = graph.node(hoveredNodeId).orElse(null);
        if (node == null || node.kind() != PhysicalNodeKind.FOLD_ANCHOR || node.foldAnchorId().isEmpty()) {
            return;
        }
        Optional<PipeAnchorId> peerId = ClientPipeNetworkCache.globalFoldCounterpart(node.foldAnchorId().get());
        if (peerId.isEmpty() || this.physicalFoldPeerVisibleInCurrentViewport(peerId.get())) {
            return;
        }
        int size = 120;
        SPSGui.Rect rect = this.hoverPreviewBounds(mouseX, mouseY, size, size);
        this.hoverOverlayAvoidRects.add(rect);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), SPSGui.PANEL_ELEVATED);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), SPSGui.PANEL_LINE);
        this.renderFoldPeerPreview(graphics, peerId.get(), new SPSGui.Rect(rect.x() + 4, rect.y() + 4, rect.width() - 8, rect.height() - 8), true);
    }

    private SPSGui.Rect hoverPreviewBounds(int mouseX, int mouseY, int width, int height) {
        this.hoverPreviewPlacement = FullMapTooltipCard.place(new SPSGui.Rect(mouseX + 14, mouseY + 14, width, height), this.screenBounds(), this.hoverOverlayAvoidRects, this.hoverPreviewPlacement);
        return this.hoverPreviewPlacement;
    }

    private SPSGui.Rect hoverPreviewBounds(SPSGui.Rect boundary, int mouseX, int mouseY, int width, int height, List<SPSGui.Rect> avoidRects) {
        this.hoverPreviewPlacement = FullMapTooltipCard.place(new SPSGui.Rect(mouseX + 14, mouseY + 14, width, height), boundary, avoidRects, this.hoverPreviewPlacement);
        return this.hoverPreviewPlacement;
    }

    private void drawHoverPreviewFrame(GuiGraphicsExtractor graphics, SPSGui.Rect rect) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), SPSGui.PANEL_ELEVATED);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), SPSGui.PANEL_LINE);
    }

    private boolean foldPeerVisibleInCurrentViewport(MapDimensionGraph graph, VisualRouteMapGraph visualGraph, PipeAnchorId peerId) {
        if (FullRouteMapCache.layoutMode().physical()) {
            return this.physicalFoldPeerVisibleInCurrentViewport(peerId);
        }
        if (!peerId.levelKey().equals(this.activeDimension)) {
            return false;
        }
        ViewportState viewport = this.displayViewport(graph);
        return this.displayNodeForFoldAnchor(peerId, viewport.zoom())
                .map(peer -> FullRouteMapRenderer.visualPosition(graph, visualGraph, peer))
                .map(peer -> FullRouteMapRenderer.worldToScreen(peer.x(), peer.y(), viewport, this.mapRect))
                .map(screen -> this.mapRect.contains(screen.x(), screen.y()))
                .orElse(false);
    }

    private boolean physicalFoldPeerVisibleInCurrentViewport(PipeAnchorId peerId) {
        if (!peerId.levelKey().equals(this.activeDimension)) {
            return false;
        }
        Optional<PhysicalRouteMapGraph> graph = FullRouteMapCache.physicalGraph(peerId.levelKey());
        Optional<PhysicalMapNode> peer = this.physicalFoldNodeForAnchor(peerId);
        if (graph.isEmpty() || peer.isEmpty()) {
            return false;
        }
        ViewportState viewport = this.viewportFor(graph.get());
        Vec2 screen = FullRouteMapRenderer.worldToScreen(peer.get().worldX(), peer.get().worldZ(), viewport, this.mapRect);
        return this.mapRect.contains(screen.x(), screen.y());
    }

    private void renderFoldPeerPreview(GuiGraphicsExtractor graphics, PipeAnchorId peerId, SPSGui.Rect preview, boolean mini) {
        if (FullRouteMapCache.layoutMode().physical()) {
            this.renderPhysicalFoldPeerPreview(graphics, peerId, preview, mini);
            return;
        }
        Optional<MapDimensionGraph> peerGraph = FullRouteMapCache.graph(peerId.levelKey());
        MapNode peerNode = this.displayNodeForFoldAnchor(peerId).orElse(null);
        if (peerGraph.isEmpty() || peerNode == null) {
            FullRouteMapRenderer.drawMapBackground(graphics, preview, 0.0D, 0.0D, FullRouteMapConfig.BASE_SCALE, FullRouteMapCache.layoutMode());
            SPSGui.centeredText(graphics, this.font, Component.translatable("screen.superpipeslide.full_map.fold_peer_missing"), preview.x() + preview.width() / 2, preview.y() + preview.height() / 2 - 4, SPSGui.TEXT_MUTED);
            return;
        }
        double zoom = this.currentGraph().map(this::displayViewport).map(ViewportState::zoom).orElse(FullRouteMapConfig.DEFAULT_ZOOM);
        if (peerNode.kind() == NodeKind.CLUSTER) {
            zoom = clusterVisibleZoom();
        }
        Vec2 peerVisual = FullRouteMapRenderer.visualPosition(peerGraph.get(), FullRouteMapCache.visualGraph(peerId.levelKey()).orElse(null), peerNode);
        ViewportState viewport = new ViewportState(peerId.levelKey(), peerVisual.x(), peerVisual.y(), zoom);
        graphics.enableScissor(preview.x(), preview.y(), preview.right(), preview.bottom());
        this.renderer.render(graphics, this.font, peerGraph.get(), FullRouteMapCache.visualGraph(peerId.levelKey()).orElse(null), viewport, preview, HitTarget.node(peerNode.id()), 0, 0);
        graphics.disableScissor();
        if (!mini) {
            graphics.outline(preview.x(), preview.y(), preview.width(), preview.height(), SPSGui.PANEL_LINE);
        }
    }

    private void renderPhysicalFoldPeerPreview(GuiGraphicsExtractor graphics, PipeAnchorId peerId, SPSGui.Rect preview, boolean mini) {
        Optional<PhysicalRouteMapGraph> peerGraph = FullRouteMapCache.physicalGraph(peerId.levelKey());
        Optional<PhysicalMapNode> peerNode = this.physicalFoldNodeForAnchor(peerId);
        if (peerGraph.isEmpty() || peerNode.isEmpty()) {
            FullRouteMapRenderer.drawMapBackground(graphics, preview, 0.0D, 0.0D, FullRouteMapConfig.BASE_SCALE, FullRouteMapCache.layoutMode());
            SPSGui.centeredText(graphics, this.font, Component.translatable("screen.superpipeslide.full_map.fold_peer_missing"), preview.x() + preview.width() / 2, preview.y() + preview.height() / 2 - 4, SPSGui.TEXT_MUTED);
            return;
        }
        double zoom = this.currentGraph().map(this::displayViewport).map(ViewportState::zoom).orElse(FullRouteMapConfig.DEFAULT_ZOOM);
        ViewportState viewport = new ViewportState(peerId.levelKey(), peerNode.get().worldX(), peerNode.get().worldZ(), zoom);
        graphics.enableScissor(preview.x(), preview.y(), preview.right(), preview.bottom());
        this.renderer.renderPhysical(graphics, this.font, peerGraph.get(), viewport, preview, HitTarget.physicalNode(peerNode.get().id()), 0, 0);
        graphics.disableScissor();
        if (!mini) {
            graphics.outline(preview.x(), preview.y(), preview.width(), preview.height(), SPSGui.PANEL_LINE);
        }
    }

    private void renderHoverTooltip(GuiGraphicsExtractor graphics, MapDimensionGraph graph, int mouseX, int mouseY) {
        if (graph == null) {
            return;
        }
        switch (this.hover) {
            case HitTarget.NodeHit(var nodeId) -> {
                MapNode node = graph.node(nodeId).orElse(null);
                if (node == null) {
                    this.syntheticPortalNode(graph, nodeId)
                            .ifPresent(portal -> this.renderTooltipCard(
                                    graphics,
                                    mouseX,
                                    mouseY,
                                    DisplayNameStack.of(Component.translatable("screen.superpipeslide.full_map.tooltip_card.portal").getString()),
                                    Component.translatable("screen.superpipeslide.full_map.tooltip_card.portal_target", schematicPortalTargetLabel(portal)).getString(),
                                    List.of(),
                                    List.of(),
                                    FullMapTheme.BORDER_SELECTED));
                    return;
                }
                this.renderNodeTooltip(graphics, node, mouseX, mouseY);
            }
            case HitTarget.EdgeHit(var edgeId) -> {
                MapEdge edge = graph.edge(edgeId).orElse(null);
                if (edge != null) {
                    this.renderEdgeTooltip(graphics, edge.routeLineIds(), mouseX, mouseY);
                    return;
                }
                FullRouteMapCache.visualGraph(graph.levelKey())
                        .flatMap(visualGraph -> visualGraph.edgePath(edgeId))
                        .ifPresent(path -> this.renderEdgeTooltip(graphics, path.routeLineIds(), mouseX, mouseY));
            }
            case HitTarget.TransferHintHit(var hintId) -> graph.transferHints().stream()
                    .filter(hint -> hint.id().equals(hintId))
                    .findFirst()
                    .ifPresent(hint -> this.renderTransferHintTooltip(graphics, graph, hint, mouseX, mouseY));
            case HitTarget.MissingCrossDimensionPathHit(var hintId) -> graph.missingCrossDimensionPathHints().stream()
                    .filter(hint -> hint.id().equals(hintId))
                    .findFirst()
                    .ifPresent(hint -> this.renderMissingCrossDimensionPathTooltip(graphics, hint.routeLineId(), hint.routeLayoutId(), hint.targetLevelKey(), hint.reason(), mouseX, mouseY));
            default -> {}
        }
    }

    private void renderPhysicalHoverTooltip(GuiGraphicsExtractor graphics, PhysicalRouteMapGraph graph, int mouseX, int mouseY) {
        if (graph == null) {
            return;
        }
        switch (this.hover) {
            case HitTarget.PhysicalNodeHit(var nodeId) -> {
                PhysicalMapNode node = graph.node(nodeId).orElse(null);
                if (node == null) {
                    return;
                }
                if (node.kind() == PhysicalNodeKind.PLATFORM) {
                    DisplayNameStack station = node.stationGroupId()
                            .flatMap(ClientRouteDataCache::stationGroup)
                            .map(FullMapText::displayNameStack)
                            .orElse(DisplayNameStack.of(node.label()));
                    this.renderTooltipCard(
                            graphics,
                            mouseX,
                            mouseY,
                            station,
                            Component.translatable("screen.superpipeslide.full_map.tooltip_card.physical_platform").getString(),
                            List.of(
                                    tooltipRow("screen.superpipeslide.full_map.tooltip_field.platform", node.label()),
                                    tooltipRow("screen.superpipeslide.full_map.tooltip_field.dimension", dimensionLabel(node.levelKey())),
                                    tooltipRow("screen.superpipeslide.full_map.tooltip_field.position", (int) node.worldX() + ", " + (int) node.worldY() + ", " + (int) node.worldZ())),
                            node.stationGroupId().stream()
                                    .flatMap(stationId -> ClientRouteDataCache.platformStopsInStation(stationId).stream())
                                    .flatMap(platform -> ClientRouteDataCache.routeLayoutIdsForPlatformStop(platform.id()).stream())
                                    .map(ClientRouteDataCache::routeLayout)
                                    .flatMap(Optional::stream)
                                    .map(RouteLayout::routeLineId)
                                    .distinct()
                                    .map(ClientRouteDataCache::routeLine)
                                    .flatMap(Optional::stream)
                                    .map(FullRouteMapScreen::routeChip)
                                    .toList(),
                            primaryRouteColor(node.stationGroupId().stream()
                                    .flatMap(stationId -> ClientRouteDataCache.platformStopsInStation(stationId).stream())
                                    .flatMap(platform -> ClientRouteDataCache.routeLayoutIdsForPlatformStop(platform.id()).stream())
                                    .map(ClientRouteDataCache::routeLayout)
                                    .flatMap(Optional::stream)
                                    .map(RouteLayout::routeLineId)
                                    .distinct()
                                    .toList()));
                    return;
                }
                String peerDimension = node.foldAnchorId()
                        .flatMap(ClientPipeNetworkCache::globalFoldCounterpart)
                        .map(peer -> dimensionLabel(peer.levelKey()))
                        .orElse("?");
                this.renderTooltipCard(
                        graphics,
                        mouseX,
                        mouseY,
                        DisplayNameStack.of(node.label()),
                        Component.translatable("screen.superpipeslide.full_map.tooltip_card.fold_anchor").getString(),
                        List.of(tooltipRow("screen.superpipeslide.full_map.tooltip_field.peer_dimension", peerDimension)),
                        List.of(),
                        FullRouteMapConfig.MAP_FOLD_MULTI_LINE);
            }
            case HitTarget.PhysicalEdgeHit(var edgeId) -> {
                PhysicalMapEdge edge = graph.edge(edgeId).orElse(null);
                if (edge == null) {
                    return;
                }
                List<UUID> routeLineIds = FullRouteMapRenderer.physicalRouteLineIdsForEdge(graph, edge.id());
                this.renderTooltipCard(
                        graphics,
                        mouseX,
                        mouseY,
                        edgeTitleStack(routeLineIds),
                        Component.translatable("screen.superpipeslide.full_map.tooltip_card.physical_edge").getString(),
                        List.of(
                                tooltipRow("screen.superpipeslide.full_map.tooltip_field.length", Component.translatable("screen.superpipeslide.full_map.tooltip_card.blocks", (int) Math.round(edge.metadata().lengthBlocks())).getString()),
                                tooltipRow("screen.superpipeslide.full_map.tooltip_field.dimension", dimensionLabel(edge.levelKey()))),
                        routeChips(routeLineIds),
                        primaryRouteColor(routeLineIds));
            }
            case HitTarget.MissingCrossDimensionPathHit(var hintId) -> graph.missingCrossDimensionPathHints().stream()
                    .filter(hint -> hint.id().equals(hintId))
                    .findFirst()
                    .ifPresent(hint -> this.renderMissingCrossDimensionPathTooltip(graphics, hint.routeLineId(), hint.routeLayoutId(), hint.targetLevelKey(), hint.reason(), mouseX, mouseY));
            default -> {}
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Any press is new input: an inertia glide never survives it.
        this.stopMapInertia();
        this.panningMap = false;
        if (event.button() == 0) {
            this.pendingMapClick = Optional.empty();
            this.scrollBarDrag = null;
            this.pendingRowScroll = null;
            // D4: scroll bar hit-testing precedes every other dispatch, since the bar
            // slot overlaps the rightmost pixels of the rows beneath it.
            if (this.handleScrollBarMousePress(event.x(), event.y())) {
                return true;
            }
            this.pendingRowScroll = this.rowScrollPressAt(event.x(), event.y());
        }
        if (event.button() == 0 && this.contextPicker.isPresent()) {
            if (this.contextPickerBounds.contains(event.x(), event.y())) {
                ContextPicker picker = this.contextPicker.get();
                for (int i = 0; i < Math.min(this.contextPickerActionBounds.size(), picker.actions().size()); i++) {
                    if (this.contextPickerActionBounds.get(i).contains(event.x(), event.y())) {
                        picker.actions().get(i).action().run();
                        return true;
                    }
                }
                if (this.pendingRowScroll != null) {
                    // Defer the row click to release: a drag past the 6px threshold
                    // becomes a scroll instead of a click (same rule as map clicks).
                    this.pendingMapClick = Optional.of(new PendingMapClick(
                            (releaseX, releaseY) -> this.dispatchContextPickerRow(picker, releaseX, releaseY),
                            null,
                            event.x(),
                            event.y()));
                    return true;
                }
                for (int i = 0; i < Math.min(this.contextPickerRowBounds.size(), picker.rows().size()); i++) {
                    if (this.contextPickerRowBounds.get(i).contains(event.x(), event.y())) {
                        picker.rows().get(i).action().run();
                        return true;
                    }
                }
                return true;
            }
            this.clearContextPicker();
            return true;
        }
        if (event.button() == 0) {
            boolean insideSearchChrome = this.searchControlBounds.contains(event.x(), event.y()) || this.searchResultsBounds.contains(event.x(), event.y());
            boolean insideDimensionChrome = this.dimensionChipBounds.contains(event.x(), event.y()) || this.dimensionMenuBounds.contains(event.x(), event.y());
            if (!insideSearchChrome && this.searchBox != null) {
                this.searchBox.setFocused(false);
                this.setFocused(null);
                if (this.searchBox.getValue().isBlank()) {
                    this.searchExpanded = false;
                }
            }
            if (!insideDimensionChrome) {
                this.dimensionMenuOpen = false;
            }
            if (this.mapPopoverBlocks(event.x(), event.y())) {
                if (this.pendingRowScroll != null) {
                    // Defer row clicks to release so a drag can become a scroll instead.
                    this.pendingMapClick = Optional.of(new PendingMapClick(
                            (releaseX, releaseY) -> this.dispatchClickActions(this.backgroundClickActions, releaseX, releaseY),
                            null,
                            event.x(),
                            event.y()));
                    return true;
                }
                if (this.dispatchClickActions(this.backgroundClickActions, event.x(), event.y())) {
                    return true;
                }
                if (this.navigationDrawerDragHandleBounds().contains(event.x(), event.y())) {
                    this.draggingNavigationDrawer = true;
                    this.navigationDrawerUserBounds = this.navigationDrawerBounds;
                    return true;
                }
                return true;
            }
            if (this.mapChromeBlocks(event.x(), event.y())) {
                if (this.pendingRowScroll != null) {
                    // Defer row clicks to release so a drag can become a scroll instead.
                    this.pendingMapClick = Optional.of(new PendingMapClick(
                            (releaseX, releaseY) -> this.dispatchClickActions(this.backgroundClickActions, releaseX, releaseY),
                            null,
                            event.x(),
                            event.y()));
                    return true;
                }
                if (this.dispatchClickActions(this.backgroundClickActions, event.x(), event.y())) {
                    return true;
                }
                if (this.searchControlBounds.contains(event.x(), event.y()) && this.dispatchWidgetMouseClicked(event, doubleClick)) {
                    this.searchExpanded = true;
                    return true;
                }
                if (this.searchControlBounds.contains(event.x(), event.y())) {
                    this.focusSearch();
                }
                return true;
            }
        }
        Optional<String> clickedCard = this.topmostCardAt(event.x(), event.y());
        if (clickedCard.isPresent()) {
            boolean wasTop = this.cardStack.isEmpty() || this.cardStack.getLast().windowKey().equals(clickedCard.get());
            this.bringCardToFront(clickedCard.get());
            SPSGui.Rect bounds = this.cardWindowBounds.get(clickedCard.get());
            Optional<MapCard> clicked = this.cardByKey(clickedCard.get());
            boolean routeCard = clicked.map(card -> card.kind() == CardKind.ROUTE_LINE).orElse(false);
            boolean clusterCard = clicked.map(FullRouteMapScreen::isClusterFocusCard).orElse(false);
            if (routeCard) {
                this.focusedRouteCardKey = clickedCard;
                this.focusedClusterCardKey = Optional.empty();
            } else if (clusterCard) {
                this.focusedRouteCardKey = Optional.empty();
                this.focusedClusterCardKey = clickedCard;
            } else {
                this.focusedRouteCardKey = Optional.empty();
                this.focusedClusterCardKey = Optional.empty();
            }
            boolean routeMapClick = routeCard
                    && this.routeCardMapRegions.getOrDefault(clickedCard.get(), new SPSGui.Rect(0, 0, 0, 0)).contains(event.x(), event.y())
                    && this.routeCardControlRegions.getOrDefault(clickedCard.get(), List.of()).stream().noneMatch(region -> region.contains(event.x(), event.y()));
            boolean clusterMapClick = clusterCard
                    && this.clusterCardMapRegions.getOrDefault(clickedCard.get(), new SPSGui.Rect(0, 0, 0, 0)).contains(event.x(), event.y())
                    && !this.clusterCardFitRegions.getOrDefault(clickedCard.get(), new SPSGui.Rect(0, 0, 0, 0)).contains(event.x(), event.y());
            this.draggingRouteCardViewportKey = routeMapClick ? clickedCard : Optional.empty();
            this.draggingClusterCardViewportKey = clusterMapClick ? clickedCard : Optional.empty();
            this.draggingCardKey = !routeMapClick && !clusterMapClick && bounds != null && cardHeaderBounds(bounds).contains(event.x(), event.y())
                    ? clickedCard
                    : Optional.empty();
            if (event.button() == 0) {
                List<ClickAction> actions = this.cardClickActions.getOrDefault(clickedCard.get(), List.of());
                boolean clickedClose = actions.stream().anyMatch(action -> isCloseAction(bounds, action.bounds()) && action.bounds().contains(event.x(), event.y()));
                if (wasTop || clickedClose) {
                    String cardKey = clickedCard.get();
                    // Double-click on a cluster card's map area fits its viewport, the
                    // same gesture the main map offers (there it zooms instead).
                    Runnable doubleClickAction = clusterMapClick ? () -> this.fitClusterCard(cardKey) : null;
                    this.pendingMapClick = Optional.of(new PendingMapClick(
                            (releaseX, releaseY) -> this.dispatchClickActions(this.cardClickActions.getOrDefault(cardKey, List.of()), releaseX, releaseY),
                            doubleClickAction,
                            event.x(),
                            event.y()));
                }
            }
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            if (this.mapRect.contains(event.x(), event.y())
                    && this.topmostCardAt(event.x(), event.y()).isEmpty()
                    && !this.mapChromeBlocks(event.x(), event.y())
                    && FullRouteMapCache.layoutMode() != FullRouteMapLayoutMode.SCHEMATIC
                    && this.currentViewport().isPresent()) {
                this.clearContextPicker();
                if (doubleClick) {
                    this.resetCamera();
                    return true;
                }
                this.draggingMapCamera = true;
                return true;
            }
        }
        if (event.button() != 0) {
            return this.dispatchWidgetMouseClicked(event, doubleClick);
        }
        if (this.dispatchWidgetMouseClicked(event, doubleClick)) {
            return true;
        }
        this.draggingCardKey = Optional.empty();
        this.draggingRouteCardViewportKey = Optional.empty();
        this.draggingClusterCardViewportKey = Optional.empty();
        this.focusedRouteCardKey = Optional.empty();
        this.focusedClusterCardKey = Optional.empty();
        Optional<MapDimensionGraph> graph = this.currentGraph();
        if (graph.isEmpty()) {
            return true;
        }
        if (FullRouteMapCache.layoutMode().physical()) {
            Optional<PhysicalRouteMapGraph> physicalGraph = FullRouteMapCache.physicalGraph(graph.get().levelKey());
            if (physicalGraph.isPresent()) {
                HitTarget hit = this.renderer.hitTestPhysical(physicalGraph.get(), this.viewportFor(physicalGraph.get()), this.mapRect, event.x(), event.y());
                PendingClickAction action = null;
                switch (hit) {
                    case HitTarget.PhysicalNodeHit(var nodeId) -> action = (releaseX, releaseY) -> this.handlePhysicalNodeClick(physicalGraph.get(), nodeId, releaseX, releaseY);
                    case HitTarget.PhysicalEdgeHit(var edgeId) -> action = (releaseX, releaseY) -> this.handlePhysicalEdgeClick(physicalGraph.get(), edgeId, releaseX, releaseY);
                    case HitTarget.MissingCrossDimensionPathHit(var hintId) -> action = (releaseX, releaseY) -> this.handlePhysicalMissingCrossDimensionPathClick(physicalGraph.get(), hintId);
                    default -> {}
                }
                this.pendingMapClick = Optional.of(new PendingMapClick(action, action == null ? () -> this.zoomMapViewport(event.x(), event.y(), MAP_DOUBLE_CLICK_ZOOM_FACTOR) : null, event.x(), event.y()));
                return true;
            }
        }
        HitTarget hit = this.renderer.hitTest(graph.get(), FullRouteMapCache.visualGraph(graph.get().levelKey()).orElse(null), this.displayViewport(graph.get()), this.mapRect, event.x(), event.y());
        PendingClickAction action = null;
        Runnable doubleClickAction = null;
        if (hit instanceof HitTarget.NodeHit(var nodeId)) {
            MapNode node = graph.get().node(nodeId).orElse(null);
            if (node != null) {
                action = (releaseX, releaseY) -> this.handleNodeClick(graph.get(), nodeId, releaseX, releaseY);
                if (node.kind() != NodeKind.DEEP_CLUSTER) {
                    doubleClickAction = () -> this.locateNode(node);
                }
            }
        }
        if (action == null && hit instanceof HitTarget.EdgeHit(var edgeId)) {
            action = (releaseX, releaseY) -> this.handleEdgeClick(graph.get(), edgeId, releaseX, releaseY);
        }
        if (action == null && hit instanceof HitTarget.MissingCrossDimensionPathHit(var hintId)) {
            action = (releaseX, releaseY) -> this.handleMissingCrossDimensionPathClick(graph.get(), hintId);
        }
        if (action == null && doubleClickAction == null) {
            // Double-click on blank map: zoom in one step, anchored at the clicked point.
            doubleClickAction = () -> this.zoomMapViewport(event.x(), event.y(), MAP_DOUBLE_CLICK_ZOOM_FACTOR);
        }
        this.pendingMapClick = Optional.of(new PendingMapClick(action, doubleClickAction, event.x(), event.y()));
        return true;
    }

    private static boolean isCloseAction(SPSGui.Rect cardBounds, SPSGui.Rect actionBounds) {
        return cardBounds != null
                && actionBounds.x() >= cardBounds.right() - FullMapTheme.ICON_BUTTON
                && actionBounds.y() <= cardBounds.y() + FullMapTheme.CARD_HEADER_HEIGHT - 2
                && actionBounds.right() <= cardBounds.right()
                && actionBounds.bottom() <= cardBounds.y() + FullMapTheme.CARD_HEADER_HEIGHT;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == 0 && this.scrollBarDrag != null) {
            FullMapScrollBar.Binding binding = this.scrollBarBindings.get(this.scrollBarDrag.bindingId());
            if (binding == null || !binding.scrollable()) {
                this.scrollBarDrag = null;
            } else {
                if (this.scrollBarDrag.thumbMode()) {
                    binding.write(FullMapScrollBar.scrollForThumbTop(binding, event.y() - this.scrollBarDrag.grabOffset()));
                } else {
                    binding.write(this.scrollBarDrag.startScroll() + (this.scrollBarDrag.pressY() - event.y()));
                }
                return true;
            }
        }
        if (event.button() == 0 && this.pendingRowScroll != null) {
            FullMapScrollBar.Binding binding = this.scrollBarBindings.get(this.pendingRowScroll.bindingId());
            if (binding == null || !binding.scrollable()) {
                this.pendingRowScroll = null;
            } else if (Math.hypot(event.x() - this.pendingRowScroll.pressX(), event.y() - this.pendingRowScroll.pressY()) > MAP_CLICK_DRAG_THRESHOLD_PX) {
                // Past the click/drag threshold: the gesture becomes a row-area scroll
                // drag and any deferred row click is cancelled.
                this.pendingMapClick = Optional.empty();
                this.scrollBarDrag = FullMapScrollBar.Drag.row(binding, this.pendingRowScroll.pressY(), this.pendingRowScroll.startScroll());
                this.pendingRowScroll = null;
                binding.write(this.scrollBarDrag.startScroll() + (this.scrollBarDrag.pressY() - event.y()));
                return true;
            }
        }
        if (this.contextPicker.isPresent()) {
            if (!this.contextPickerBounds.contains(event.x(), event.y())) {
                this.clearContextPicker();
            }
            return true;
        }
        if (event.button() == 0) {
            this.pendingMapClick.ifPresent(pending -> {
                if (Math.hypot(event.x() - pending.pressX(), event.y() - pending.pressY()) > MAP_CLICK_DRAG_THRESHOLD_PX) {
                    this.pendingMapClick = Optional.empty();
                }
            });
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && this.draggingMapCamera && this.activeDimension != null && FullRouteMapCache.layoutMode() != FullRouteMapLayoutMode.SCHEMATIC) {
            ViewportState viewport = this.currentViewport().isPresent() ? this.storedViewport(this.activeDimension) : null;
            if (viewport != null) {
                // Manual camera control takes over from any in-flight tween without a jump.
                this.cancelViewportTween(this.activeDimension);
                viewport = this.storedViewport(this.activeDimension);
                double nextPitch = viewport.pitchDegrees() - dragY * FullRouteMapConfig.CAMERA_PITCH_DRAG_DEGREES_PER_PIXEL;
                double nextBearing = viewport.bearingDegrees() + dragX * FullRouteMapConfig.CAMERA_BEARING_DRAG_DEGREES_PER_PIXEL;
                this.viewports.put(this.activeDimension, viewport.withCamera(nextPitch, nextBearing));
                return true;
            }
            this.draggingMapCamera = false;
        }
        if (event.button() == 0 && this.draggingNavigationDrawer) {
            SPSGui.Rect current = this.navigationDrawerUserBounds == null ? this.navigationDrawerBounds : this.navigationDrawerUserBounds;
            SPSGui.Rect moved = this.clampNavigationDrawerBounds(new SPSGui.Rect(
                    current.x() + (int) Math.round(dragX),
                    current.y() + (int) Math.round(dragY),
                    this.navigationDrawerBounds.width() <= 0 ? current.width() : this.navigationDrawerBounds.width(),
                    this.navigationDrawerBounds.height() <= 0 ? current.height() : this.navigationDrawerBounds.height()));
            this.navigationDrawerUserBounds = moved;
            this.navigationDrawerUserXRatio = moved.x() / (double) Math.max(1, this.width - moved.width());
            this.navigationDrawerUserYRatio = moved.y() / (double) Math.max(1, this.height - moved.height());
            this.navigationDrawerBounds = moved;
            return true;
        }
        if (event.button() == 0 && this.draggingRouteCardViewportKey.isPresent()) {
            String key = this.draggingRouteCardViewportKey.get();
            if (this.panRouteCardViewport(key, dragX, dragY)) {
                return true;
            }
            this.draggingRouteCardViewportKey = Optional.empty();
        }
        if (event.button() == 0 && this.draggingClusterCardViewportKey.isPresent()) {
            String key = this.draggingClusterCardViewportKey.get();
            if (this.panClusterCardViewport(key, dragX, dragY)) {
                return true;
            }
            this.draggingClusterCardViewportKey = Optional.empty();
        }
        if (event.button() == 0 && this.draggingCardKey.isPresent()) {
            String key = this.draggingCardKey.get();
            SPSGui.Rect bounds = this.cardWindowBounds.get(key);
            if (bounds != null) {
                this.cardWindowBounds.put(key, this.clampCardBounds(new SPSGui.Rect(
                        bounds.x() + (int) Math.round(dragX),
                        bounds.y() + (int) Math.round(dragY),
                        bounds.width(),
                        bounds.height())));
                return true;
            }
            this.draggingCardKey = Optional.empty();
        }
        if (event.button() == 0 && this.activeDimension != null && this.mapRect.contains(event.x(), event.y()) && this.topmostCardAt(event.x(), event.y()).isEmpty() && !this.mapChromeBlocks(event.x(), event.y())) {
            if (this.contextPicker.isPresent() && !this.contextPickerBounds.contains(event.x(), event.y())) {
                this.clearContextPicker();
                return true;
            }
            Optional<MapDimensionGraph> graph = this.currentGraph();
            if (graph.isPresent()) {
                if (!this.panningMap) {
                    // First event of a pan gesture: take over from tween/inertia seamlessly.
                    this.panningMap = true;
                    this.panVelocityX = 0.0D;
                    this.panVelocityZ = 0.0D;
                    this.stopMapInertia();
                    this.cancelViewportTween(this.activeDimension);
                    this.lastPanSampleMillis = System.currentTimeMillis();
                }
                ViewportState display = this.displayViewport(graph.get());
                Vec2 before = FullRouteMapRenderer.screenToWorld(event.x(), event.y(), display, this.mapRect);
                Vec2 after = FullRouteMapRenderer.screenToWorld(event.x() - dragX, event.y() - dragY, display, this.mapRect);
                double deltaX = after.x() - before.x();
                double deltaZ = after.y() - before.y();
                ViewportState stored = this.storedViewport(this.activeDimension);
                this.viewports.put(this.activeDimension, this.clampToSoftBounds(this.activeDimension, stored.withCenter(stored.centerWorldX() + deltaX, stored.centerWorldZ() + deltaZ)));
                // Sample the release velocity (EMA over the last drag events) for inertia.
                long now = System.currentTimeMillis();
                long dtMillis = now - this.lastPanSampleMillis;
                if (dtMillis > 0L) {
                    this.panVelocityX = this.panVelocityX * 0.5D + deltaX / dtMillis * 0.5D;
                    this.panVelocityZ = this.panVelocityZ * 0.5D + deltaZ / dtMillis * 0.5D;
                    this.lastPanSampleMillis = now;
                }
                return true;
            }
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && this.draggingMapCamera) {
            this.draggingMapCamera = false;
            return true;
        }
        if (event.button() == 0 && this.draggingNavigationDrawer) {
            this.draggingNavigationDrawer = false;
            return true;
        }
        if (event.button() == 0) {
            // A release ends every drag mode, not just the drawer/camera ones.
            this.draggingCardKey = Optional.empty();
            this.draggingRouteCardViewportKey = Optional.empty();
            this.draggingClusterCardViewportKey = Optional.empty();
            this.scrollBarDrag = null;
            this.pendingRowScroll = null;
            if (this.panningMap) {
                this.panningMap = false;
                this.startMapInertiaFromPan();
            }
        }
        if (event.button() == 0 && this.pendingMapClick.isPresent()) {
            PendingMapClick pending = this.pendingMapClick.get();
            this.pendingMapClick = Optional.empty();
            long now = System.currentTimeMillis();
            boolean doubleClickChain = pending.doubleClickAction() != null
                    && now - this.lastMapClickReleaseMillis <= MAP_DOUBLE_CLICK_INTERVAL_MILLIS
                    && Math.hypot(event.x() - this.lastMapClickReleaseX, event.y() - this.lastMapClickReleaseY) <= MAP_CLICK_DRAG_THRESHOLD_PX;
            this.lastMapClickReleaseMillis = now;
            this.lastMapClickReleaseX = event.x();
            this.lastMapClickReleaseY = event.y();
            if (doubleClickChain) {
                this.lastMapClickReleaseMillis = Long.MIN_VALUE;
                pending.doubleClickAction().run();
            } else if (pending.action() != null) {
                pending.action().run(event.x(), event.y());
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.diagnosticsPanelOpen && this.diagnosticsPanelBounds.width() > 0 && this.diagnosticsPanelBounds.contains(mouseX, mouseY)) {
            double maxScroll = Math.max(0.0D, this.diagnosticsGroups().size() * DIAGNOSTIC_GROUP_HEIGHT - (this.diagnosticsPanelBounds.height() - 30));
            this.diagnosticsScroll = clamp(this.diagnosticsScroll - scrollY * 26.0D, 0.0D, maxScroll);
            return true;
        }
        if (this.navigationItineraryBounds.width() > 0 && this.navigationItineraryBounds.contains(mouseX, mouseY)) {
            FullMapNavigationViewModel.RoutePreview preview = this.navigationPreviewModel();
            double maxScroll = this.maxNavigationItineraryScroll(preview, this.navigationItineraryBounds);
            if (maxScroll > 0.0D) {
                this.navigationItineraryScroll = clamp(this.navigationItineraryScroll - scrollY * 24.0D, 0.0D, maxScroll);
            }
            return true;
        }
        if (this.searchResultsBounds.width() > 0 && this.searchResultsBounds.contains(mouseX, mouseY)) {
            List<ClientNavigationController.DestinationSearchResult> destinations = this.navigationDestinationResults();
            List<SearchResult> results = this.searchResults();
            double maxScroll = this.maxNavigationResultScroll(destinations, results, this.searchResultsBounds);
            if (maxScroll > 0.0D) {
                this.navigationResultScroll = clamp(this.navigationResultScroll - scrollY * 26.0D, 0.0D, maxScroll);
            }
            return true;
        }
        if (this.contextPicker.isPresent()) {
            if (this.contextPickerBounds.contains(mouseX, mouseY)) {
                if (this.contextPickerMaxScroll > 0.0D) {
                    double delta = Math.abs(scrollX) > Math.abs(scrollY) ? scrollX : -scrollY;
                    this.contextPickerScroll = Math.max(0.0D, Math.min(this.contextPickerMaxScroll, this.contextPickerScroll + delta * 28.0D));
                }
                return true;
            }
            this.clearContextPicker();
            return true;
        }
        if (this.mapPopoverBlocks(mouseX, mouseY)) {
            return true;
        }
        Optional<String> hoveredCardKey = this.topmostCardAt(mouseX, mouseY);
        if (hoveredCardKey.isPresent()) {
            MapCard card = this.cardStack.stream().filter(value -> value.windowKey().equals(hoveredCardKey.get())).findFirst().orElse(null);
            String stripKey = card == null ? "" : lineStripKey(card);
            SPSGui.Rect strip = this.lineStripRegions.get(stripKey);
            if (strip != null && strip.contains(mouseX, mouseY)) {
                double maxScroll = this.lineStripMaxScrolls.getOrDefault(stripKey, 0.0D);
                if (maxScroll > 0.0D) {
                    double delta = Math.abs(scrollX) > Math.abs(scrollY) ? scrollX : -scrollY;
                    double current = this.lineStripScrolls.getOrDefault(stripKey, 0.0D);
                    double next = Math.max(0.0D, Math.min(maxScroll, current + delta * FullRouteMapConfig.CARD_LINE_STRIP_SCROLL_STEP_PX));
                    this.lineStripScrolls.put(stripKey, next);
                }
                return true;
            }
            SPSGui.Rect stationRoutes = this.stationCardRouteRegions.get(hoveredCardKey.get());
            if (card != null && card.kind() == CardKind.STATION && stationRoutes != null && stationRoutes.contains(mouseX, mouseY)) {
                double maxScroll = this.stationCardRouteMaxScrolls.getOrDefault(hoveredCardKey.get(), 0.0D);
                if (maxScroll > 0.0D) {
                    double current = this.stationCardRouteScrolls.getOrDefault(hoveredCardKey.get(), 0.0D);
                    double next = Math.max(0.0D, Math.min(maxScroll, current - scrollY * 28.0D));
                    this.stationCardRouteScrolls.put(hoveredCardKey.get(), next);
                }
                return true;
            }
            if (this.routeCardControlRegions.getOrDefault(hoveredCardKey.get(), List.of()).stream().anyMatch(region -> region.contains(mouseX, mouseY))) {
                return true;
            }
            if (this.clusterCardFitRegions.getOrDefault(hoveredCardKey.get(), new SPSGui.Rect(0, 0, 0, 0)).contains(mouseX, mouseY)) {
                return true;
            }
            SPSGui.Rect clusterMap = this.clusterCardMapRegions.get(hoveredCardKey.get());
            if (card != null && isClusterFocusCard(card) && clusterMap != null && clusterMap.contains(mouseX, mouseY)) {
                this.zoomClusterCardViewport(hoveredCardKey.get(), mouseX, mouseY, scrollY);
                this.focusedClusterCardKey = hoveredCardKey;
                this.focusedRouteCardKey = Optional.empty();
                return true;
            }
            SPSGui.Rect routeMap = this.routeCardMapRegions.get(hoveredCardKey.get());
            if (card != null && card.kind() == CardKind.ROUTE_LINE && routeMap != null && routeMap.contains(mouseX, mouseY)) {
                this.zoomRouteCardViewport(hoveredCardKey.get(), mouseX, mouseY, scrollY);
                this.focusedRouteCardKey = hoveredCardKey;
                return true;
            }
            SPSGui.Rect stopListRegion = this.routeCardStopListRegions.get(hoveredCardKey.get());
            if (card != null && card.kind() == CardKind.ROUTE_LINE && stopListRegion != null && stopListRegion.contains(mouseX, mouseY)) {
                double maxScroll = this.routeCardStopListMaxScrolls.getOrDefault(hoveredCardKey.get(), 0.0D);
                if (maxScroll > 0.0D) {
                    double current = this.routeCardStopListScrolls.getOrDefault(hoveredCardKey.get(), 0.0D);
                    double next = Math.max(0.0D, Math.min(maxScroll, current - scrollY * 34.0D));
                    this.routeCardStopListScrolls.put(hoveredCardKey.get(), next);
                }
                return true;
            }
            return true;
        }
        if (FullRouteMapCache.layoutMode() == FullRouteMapLayoutMode.SCHEMATIC && this.schematicLegendBounds.contains(mouseX, mouseY)) {
            if (!this.schematicLegendCollapsed && this.schematicLegendMaxScroll > 0.0D) {
                double delta = Math.abs(scrollX) > Math.abs(scrollY) ? scrollX : -scrollY;
                this.schematicLegendScroll = Math.max(0.0D, Math.min(this.schematicLegendMaxScroll, this.schematicLegendScroll + delta * 26.0D));
            }
            return true;
        }
        if (this.mapChromeBlocks(mouseX, mouseY)) {
            return true;
        }
        Optional<MapDimensionGraph> graph = this.currentGraph();
        if (graph.isPresent() && this.mapRect.contains(mouseX, mouseY)) {
            this.zoomMapViewport(mouseX, mouseY, scrollY > 0 ? wheelZoomStep() : 1.0D / wheelZoomStep());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            return this.closeTopmostOverlayOrScreen(event, true);
        }
        if (this.searchBox != null && this.searchBox.isFocused()) {
            return super.keyPressed(event);
        }
        // Toggle key bound to open this screen: mirrors ESC's layered close, one layer per
        // press, closing the screen itself once no overlay is left. (KeyMapping#matches is
        // this version's equivalent of the older isActiveAndMatches check.)
        if (SuperPipeSlideClient.OPEN_FULL_ROUTE_MAP.matches(event)) {
            return this.closeTopmostOverlayOrScreen(event, false);
        }
        if (key == GLFW.GLFW_KEY_0 || key == GLFW.GLFW_KEY_HOME) {
            // A focused cluster card owns the fit shortcut; otherwise reset the main map.
            if (this.fitFocusedClusterCard()) {
                return true;
            }
            this.resetViewport(true);
            return true;
        }
        if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD || key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
            boolean zoomIn = key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD;
            if (this.zoomFocusedClusterCardByKey(zoomIn)) {
                return true;
            }
            this.zoomMapViewport(
                    this.mapRect.x() + this.mapRect.width() * 0.5D,
                    this.mapRect.y() + this.mapRect.height() * 0.5D,
                    zoomIn ? MAP_KEY_ZOOM_STEP : 1.0D / MAP_KEY_ZOOM_STEP);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            if (this.panFocusedRouteCardByKey(key)) {
                return true;
            }
            if (this.panFocusedClusterCardByKey(key)) {
                return true;
            }
            this.panByKey(key, event.hasShiftDown());
            return true;
        }
        return super.keyPressed(event);
    }

    /**
     * Closes the topmost overlay layer, one layer per invocation: search first, then
     * context picker, dimension menu, navigation preview, and finally the topmost card.
     * When nothing is left, ESC delegates to the super implementation (which closes the
     * screen); other callers close directly.
     */
    private boolean closeTopmostOverlayOrScreen(KeyEvent event, boolean delegateToSuper) {
        if (this.searchBox != null && (this.searchBox.isFocused() || this.searchExpanded || !this.searchBox.getValue().isBlank())) {
            if (!this.searchBox.getValue().isBlank()) {
                this.searchBox.setValue("");
            } else {
                this.searchExpanded = false;
                this.searchBox.setFocused(false);
                this.setFocused(null);
            }
            return true;
        }
        if (this.contextPicker.isPresent()) {
            this.clearContextPicker();
            return true;
        }
        if (this.diagnosticsPanelOpen) {
            this.diagnosticsPanelOpen = false;
            return true;
        }
        if (this.dimensionMenuOpen) {
            this.dimensionMenuOpen = false;
            return true;
        }
        if (this.selectedNavigationStationGroupId != null || this.navigationSheetExpanded) {
            this.clearNavigationPreview();
            return true;
        }
        if (!this.cardStack.isEmpty()) {
            this.popCard();
            return true;
        }
        if (delegateToSuper) {
            return super.keyPressed(event);
        }
        this.onClose();
        return true;
    }

    @Override
    public void onClose() {
        FullRouteMapCache.markClosed();
        super.onClose();
    }

    /**
     * D3: single write-back point for the cross-open session state. Every exit path
     * (ESC/P via onClose, direct setScreen calls, being replaced by another screen)
     * funnels through removed(), so saving here covers them all.
     */
    @Override
    public void removed() {
        FullRouteMapSessionState.save(new FullRouteMapSessionState.Snapshot(
                this.viewports,
                this.cardWindowBounds,
                this.cardStack,
                this.searchBox == null ? this.restoredSearchText : this.searchBox.getValue(),
                this.navigationDrawerUserXRatio,
                this.navigationDrawerUserYRatio,
                this.lineStripScrolls,
                this.routeCardStopListScrolls,
                this.stationCardRouteScrolls,
                this.navigationResultScroll,
                this.navigationItineraryScroll,
                this.schematicLegendScroll));
        super.removed();
    }

    private void handleNodeClick(MapDimensionGraph graph, NodeId nodeId, double mouseX, double mouseY) {
        MapNode node = graph.node(nodeId).orElse(null);
        if (node == null) {
            return;
        }
        if (node.kind() == NodeKind.FOLD_ANCHOR && node.foldPeerId().isPresent()) {
            this.pushCard(MapCard.foldPeek(node.id().primaryId(), node.levelKey()));
            return;
        }
        if (node.kind() == NodeKind.CLUSTER) {
            this.pushCard(MapCard.cluster(node.id().primaryId(), node.levelKey()));
            return;
        }
        if (node.kind() == NodeKind.DEEP_CLUSTER) {
            this.pushCard(MapCard.deepCluster(node.id().primaryId(), node.levelKey()));
            return;
        }
        if (node.kind() == NodeKind.STATION && !node.stationGroupIds().isEmpty()) {
            this.openStationPicker(node.stationGroupIds().getFirst(), node.levelKey(), (int) Math.round(mouseX), (int) Math.round(mouseY), this.mapRect);
        }
    }

    private void handleEdgeClick(MapDimensionGraph graph, String edgeId, double mouseX, double mouseY) {
        if (graph == null) {
            return;
        }
        MapEdge edge = graph.edge(edgeId).orElse(null);
        if (edge != null) {
            List<UUID> lineIds = edge.routeLineIds();
            if (lineIds.size() == 1) {
                Optional<UUID> layoutId = edge.occurrences().stream()
                        .filter(occurrence -> occurrence.routeLineId().equals(lineIds.getFirst()))
                        .map(MapEdgeOccurrence::routeLayoutId)
                        .findFirst();
                this.pushCard(MapCard.routeLine(lineIds.getFirst(), layoutId, graph.levelKey(), RouteCardViewMode.PRACTICAL));
                return;
            }
            this.openEdgePicker(edge, graph.levelKey(), (int) Math.round(mouseX), (int) Math.round(mouseY), this.mapRect, RouteCardViewMode.PRACTICAL);
            return;
        }
        VisualEdgePath visualEdge = FullRouteMapCache.visualGraph(graph.levelKey()).flatMap(value -> value.edgePath(edgeId)).orElse(null);
        if (visualEdge == null) {
            return;
        }
        List<UUID> lineIds = visualEdge.routeLineIds();
        if (lineIds.size() == 1) {
            Optional<UUID> layoutId = visualEdge.occurrences().stream()
                    .filter(occurrence -> occurrence.routeLineId().equals(lineIds.getFirst()))
                    .map(MapEdgeOccurrence::routeLayoutId)
                    .findFirst();
            this.pushCard(MapCard.routeLine(lineIds.getFirst(), layoutId, graph.levelKey(), RouteCardViewMode.PRACTICAL));
            return;
        }
        if (!lineIds.isEmpty()) {
            this.openVisualEdgePicker(visualEdge, graph.levelKey(), (int) Math.round(mouseX), (int) Math.round(mouseY), this.mapRect);
        }
    }

    private void handlePhysicalNodeClick(PhysicalRouteMapGraph graph, String nodeId, double mouseX, double mouseY) {
        PhysicalMapNode node = graph.node(nodeId).orElse(null);
        if (node == null) {
            return;
        }
        if (node.kind() == PhysicalNodeKind.PLATFORM && node.stationGroupId().isPresent()) {
            this.openStationPicker(node.stationGroupId().get(), node.levelKey(), (int) Math.round(mouseX), (int) Math.round(mouseY), this.mapRect);
            return;
        }
        if (node.kind() == PhysicalNodeKind.FOLD_ANCHOR && node.foldAnchorId().isPresent()) {
            this.rawFoldNodeForAnchor(node.foldAnchorId().get()).ifPresent(value -> this.pushCard(MapCard.foldPeek(value.id().primaryId(), value.levelKey())));
        }
    }

    private void handlePhysicalEdgeClick(PhysicalRouteMapGraph graph, String edgeId, double mouseX, double mouseY) {
        PhysicalMapEdge edge = graph.edge(edgeId).orElse(null);
        if (edge == null) {
            return;
        }
        List<UUID> lineIds = FullRouteMapRenderer.physicalRouteLineIdsForEdge(graph, edgeId);
        if (lineIds.size() == 1) {
            PhysicalMapEdge representative = FullRouteMapRenderer.physicalRepresentativeEdgeForRouteLine(graph, edgeId, lineIds.getFirst()).orElse(edge);
            this.pushCard(MapCard.routeLine(lineIds.getFirst(), Optional.of(representative.metadata().routeLayoutId()), graph.levelKey(), RouteCardViewMode.PHYSICAL));
            return;
        }
        if (lineIds.size() > 1) {
            this.openPhysicalEdgePicker(graph, edgeId, (int) Math.round(mouseX), (int) Math.round(mouseY), this.mapRect);
        }
    }

    private void openStationPicker(UUID stationId, ResourceKey<Level> levelKey, int anchorX, int anchorY, SPSGui.Rect boundary) {
        Optional<StationGroup> station = ClientRouteDataCache.stationGroup(stationId);
        if (station.isEmpty()) {
            return;
        }
        List<ContextPickerRow> routeRows = ClientRouteDataCache.platformStopsInStation(stationId).stream()
                .flatMap(platform -> ClientRouteDataCache.routeLayoutIdsForPlatformStop(platform.id()).stream())
                .distinct()
                .map(ClientRouteDataCache::routeLayout)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing((RouteLayout layout) -> ClientRouteDataCache.routeLine(layout.routeLineId()).map(FullMapText::displayName).orElse(""))
                        .thenComparing(FullMapText::displayName))
                .map(layout -> {
                    Optional<RouteLine> line = ClientRouteDataCache.routeLine(layout.routeLineId());
                    if (line.isEmpty()) {
                        return null;
                    }
                    String subtitle = FullMapText.primaryName(layout) + " · " + dimensionLabel(levelKey);
                    return new ContextPickerRow(
                            FullMapText.displayNameStack(line.get()),
                            subtitle,
                            routeLineColors(line.get()),
                            () -> this.pushCard(MapCard.routeLine(line.get().id(), Optional.of(layout.id()), levelKey)));
                })
                .filter(row -> row != null)
                .toList();
        if (routeRows.isEmpty()) {
            this.pushCard(MapCard.station(stationId, levelKey));
            return;
        }
        this.contextPickerScroll = 0.0D;
        this.contextPicker = Optional.of(new ContextPicker(
                FullMapText.displayNameStack(station.get()),
                Component.translatable("screen.superpipeslide.full_map.station_platforms", ClientRouteDataCache.platformStopsInStation(stationId).size()).getString(),
                anchorX,
                anchorY,
                boundary,
                List.of(new ContextPickerAction(
                        SPSGui.Icon.LOCATE,
                        Component.translatable("screen.superpipeslide.full_map.navigate_here"),
                        () -> {
                            this.clearContextPicker();
                            this.selectNavigationDestination(stationId, true);
                        })),
                routeRows));
    }

    private void openEdgePicker(MapEdge edge, ResourceKey<Level> levelKey, int anchorX, int anchorY, SPSGui.Rect boundary, RouteCardViewMode viewMode) {
        List<ContextPickerRow> rows = edge.routeLineIds().stream()
                .map(ClientRouteDataCache::routeLine)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(FullMapText::displayName))
                .map(line -> {
                    Optional<UUID> layoutId = edge.occurrences().stream()
                            .filter(occurrence -> occurrence.routeLineId().equals(line.id()))
                            .map(MapEdgeOccurrence::routeLayoutId)
                            .findFirst();
                    return routeContextRow(line, layoutId, levelKey, viewMode);
                })
                .toList();
        this.openRoutePicker(Component.translatable("screen.superpipeslide.full_map.edge_picker").getString(), edgePickerSubtitle(rows.size()), anchorX, anchorY, boundary, rows);
    }

    private void openVisualEdgePicker(VisualEdgePath edge, ResourceKey<Level> levelKey, int anchorX, int anchorY, SPSGui.Rect boundary) {
        List<ContextPickerRow> rows = edge.routeLineIds().stream()
                .map(ClientRouteDataCache::routeLine)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(FullMapText::displayName))
                .map(line -> {
                    Optional<UUID> layoutId = edge.occurrences().stream()
                            .filter(occurrence -> occurrence.routeLineId().equals(line.id()))
                            .map(MapEdgeOccurrence::routeLayoutId)
                            .findFirst();
                    return routeContextRow(line, layoutId, levelKey, RouteCardViewMode.PRACTICAL);
                })
                .toList();
        this.openRoutePicker(Component.translatable("screen.superpipeslide.full_map.edge_picker").getString(), edgePickerSubtitle(rows.size()), anchorX, anchorY, boundary, rows);
    }

    private void openPhysicalEdgePicker(PhysicalRouteMapGraph graph, String edgeId, int anchorX, int anchorY, SPSGui.Rect boundary) {
        List<UUID> routeLineIds = FullRouteMapRenderer.physicalRouteLineIdsForEdge(graph, edgeId);
        List<ContextPickerRow> rows = routeLineIds.stream()
                .map(ClientRouteDataCache::routeLine)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(FullMapText::displayName))
                .map(line -> {
                    Optional<UUID> layoutId = FullRouteMapRenderer.physicalRepresentativeEdgeForRouteLine(graph, edgeId, line.id())
                            .map(edge -> edge.metadata().routeLayoutId());
                    return routeContextRow(line, layoutId, graph.levelKey(), RouteCardViewMode.PHYSICAL);
                })
                .toList();
        this.openRoutePicker(Component.translatable("screen.superpipeslide.full_map.edge_picker").getString(), edgePickerSubtitle(rows.size()), anchorX, anchorY, boundary, rows);
    }

    private ContextPickerRow routeContextRow(RouteLine line, Optional<UUID> layoutId, ResourceKey<Level> levelKey, RouteCardViewMode viewMode) {
        String subtitle = layoutId
                .flatMap(ClientRouteDataCache::routeLayout)
                .map(FullMapText::primaryName)
                .orElse(Component.translatable("screen.superpipeslide.layout").getString());
        return new ContextPickerRow(
                FullMapText.displayNameStack(line),
                subtitle,
                routeLineColors(line),
                () -> this.pushCard(MapCard.routeLine(line.id(), layoutId, levelKey, viewMode)));
    }

    private void openRoutePicker(String title, String subtitle, int anchorX, int anchorY, SPSGui.Rect boundary, List<ContextPickerRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        if (rows.size() == 1) {
            rows.getFirst().action().run();
            return;
        }
        this.contextPickerScroll = 0.0D;
        this.contextPicker = Optional.of(new ContextPicker(DisplayNameStack.of(title), subtitle, anchorX, anchorY, boundary, List.of(), rows));
    }

    private static String edgePickerSubtitle(int count) {
        return count <= 1
                ? Component.translatable("screen.superpipeslide.route").getString()
                : Component.translatable("screen.superpipeslide.full_map.tooltip_card.edge_subtitle", count).getString();
    }

    private void handleMissingCrossDimensionPathClick(MapDimensionGraph graph, String hintId) {
        graph.missingCrossDimensionPathHints().stream()
                .filter(hint -> hint.id().equals(hintId))
                .findFirst()
                .ifPresent(hint -> this.pushCard(MapCard.routeLine(hint.routeLineId(), Optional.of(hint.routeLayoutId()), graph.levelKey(), RouteCardViewMode.PRACTICAL)));
    }

    private void handlePhysicalMissingCrossDimensionPathClick(PhysicalRouteMapGraph graph, String hintId) {
        graph.missingCrossDimensionPathHints().stream()
                .filter(hint -> hint.id().equals(hintId))
                .findFirst()
                .ifPresent(hint -> this.pushCard(MapCard.routeLine(hint.routeLineId(), Optional.of(hint.routeLayoutId()), graph.levelKey(), RouteCardViewMode.PHYSICAL)));
    }

    private void handleRouteCardHit(MapCard card, RouteCardSemanticGraph graph, RouteLineCardHit hit, SPSGui.Rect boundary, int mouseX, int mouseY) {
        if (hit.kind() == RouteLineCardHitKind.NODE && hit.node().isPresent()) {
            RouteCardNode node = graph.nodes().stream().filter(value -> value.id().equals(hit.node().get())).findFirst().orElse(null);
            if (node == null) {
                return;
            }
            if (node.stationGroupId().isPresent()) {
                ClientRouteDataCache.stationGroup(node.stationGroupId().get()).ifPresent(this::locateStation);
                return;
            }
            if (node.kind() == RouteCardNodeKind.FOLD_BOUNDARY) {
                // The boundary node's world position is the anchor block center, so the
                // anchor id is recovered exactly and the click jumps to the fold peer.
                PipeAnchorId anchorId = new PipeAnchorId(node.levelKey(), BlockPos.containing(node.worldX(), node.worldY(), node.worldZ()));
                ClientPipeNetworkCache.globalFoldCounterpart(anchorId).ifPresent(this::jumpToFoldPeer);
                return;
            }
            if (node.kind() == RouteCardNodeKind.PORTAL_BOUNDARY) {
                this.locateRouteCardPortalPeer(graph, node);
                return;
            }
        }
        if (hit.kind() == RouteLineCardHitKind.EDGE && hit.edge().isPresent()) {
            RouteCardEdge edge = graph.edges().stream()
                    .filter(value -> value.id().equals(hit.edge().get()))
                    .findFirst()
                    .orElse(null);
            if (edge == null) {
                return;
            }
            RouteCardNode from = graph.nodes().stream().filter(node -> node.id().equals(edge.from())).findFirst().orElse(null);
            RouteCardNode to = graph.nodes().stream().filter(node -> node.id().equals(edge.to())).findFirst().orElse(null);
            // Card-only synthetic links (fold jumps, missing-path boundary links) have
            // no main-map edge; everything else maps back to one and opens the same
            // edge picker a main-map edge click would.
            if (!RouteLineCardRenderer.isAbstractBoundaryLink(graph, edge)) {
                Optional<MapEdge> mapEdge = this.mainMapEdgeForRouteCardEdge(graph, edge, from, to);
                if (mapEdge.isPresent()) {
                    RouteCardViewMode viewMode = card.routeLineState().map(RouteLineCardState::viewMode).orElse(RouteCardViewMode.PRACTICAL);
                    this.openEdgePicker(mapEdge.get(), mapEdge.get().levelKey(), mouseX, mouseY, boundary, viewMode);
                    return;
                }
            }
            if (from != null && to != null) {
                this.toast(RouteLineCardRenderer.routeCardNodeTooltipName(from) + " -> " + RouteLineCardRenderer.routeCardNodeTooltipName(to));
            }
        }
    }

    /**
     * Reverse-maps a route-card edge to the main-map edge it was derived from. Primary
     * key is (dimension, routeSectionId) — section ids are shared by both builders —
     * refined to an exact match on the backing path slice when several main-map edges
     * carry the same section (fold-split sections). When the route card synthesized its
     * own section id (missing section refs), the (line, layout, layoutIndex) slot is
     * used as fallback key instead.
     */
    private Optional<MapEdge> mainMapEdgeForRouteCardEdge(RouteCardSemanticGraph graph, RouteCardEdge edge, @Nullable RouteCardNode from, @Nullable RouteCardNode to) {
        ResourceKey<Level> levelKey = from != null ? from.levelKey() : to == null ? null : to.levelKey();
        if (levelKey == null) {
            return Optional.empty();
        }
        MapDimensionGraph map = FullRouteMapCache.graph(levelKey).orElse(null);
        if (map == null) {
            return Optional.empty();
        }
        MapEdge sectionMatch = null;
        MapEdge layoutSlotMatch = null;
        for (MapEdge candidate : map.edges()) {
            for (MapEdgeOccurrence occurrence : candidate.occurrences()) {
                boolean sameSection = occurrence.routeSectionId().equals(edge.routeSectionId());
                boolean sameLayoutSlot = occurrence.routeLineId().equals(graph.routeLineId())
                        && occurrence.routeLayoutId().equals(graph.routeLayoutId())
                        && occurrence.layoutIndex() == edge.layoutIndex();
                if (!sameSection && !sameLayoutSlot) {
                    continue;
                }
                if (sameSection && occurrence.backingPathSlice().equals(edge.backingPathSlice())) {
                    return Optional.of(candidate);
                }
                if (sameSection && sectionMatch == null) {
                    sectionMatch = candidate;
                }
                if (sameLayoutSlot && layoutSlotMatch == null) {
                    layoutSlotMatch = candidate;
                }
            }
        }
        return Optional.ofNullable(sectionMatch != null ? sectionMatch : layoutSlotMatch);
    }

    /**
     * Portal boundaries only know their target dimension; the far station is recovered
     * by walking across the abstract boundary link to the peer boundary and out to its
     * station neighbor. Falls back to a plain dimension switch when the walk fails.
     */
    private void locateRouteCardPortalPeer(RouteCardSemanticGraph graph, RouteCardNode boundary) {
        Identifier targetId = Identifier.tryParse(boundary.label());
        if (targetId == null) {
            return;
        }
        ResourceKey<Level> targetLevel = ResourceKey.create(Registries.DIMENSION, targetId);
        RouteCardNode peerBoundary = null;
        for (RouteCardEdge edge : graph.edges()) {
            RouteCardNode other = edge.from().equals(boundary.id()) ? this.routeCardNode(graph, edge.to()) : edge.to().equals(boundary.id()) ? this.routeCardNode(graph, edge.from()) : null;
            if (other != null && other.kind() == RouteCardNodeKind.PORTAL_BOUNDARY) {
                peerBoundary = other;
                break;
            }
        }
        if (peerBoundary != null) {
            for (RouteCardEdge edge : graph.edges()) {
                RouteCardNode other = edge.from().equals(peerBoundary.id()) ? this.routeCardNode(graph, edge.to()) : edge.to().equals(peerBoundary.id()) ? this.routeCardNode(graph, edge.from()) : null;
                if (other == null || other.id().equals(boundary.id()) || other.stationGroupId().isEmpty()) {
                    continue;
                }
                Optional<StationGroup> station = ClientRouteDataCache.stationGroup(other.stationGroupId().get());
                if (station.isPresent()) {
                    this.locateStation(station.get());
                    return;
                }
            }
        }
        this.switchDimension(targetLevel, false);
    }

    @Nullable
    private RouteCardNode routeCardNode(RouteCardSemanticGraph graph, RouteCardNodeId id) {
        return graph.nodes().stream().filter(node -> node.id().equals(id)).findFirst().orElse(null);
    }

    private void handleClusterCardHit(MapDimensionGraph graph, ClusterCardSemanticGraph semanticGraph, ClusterCardHit hit, SPSGui.Rect boundary, int mouseX, int mouseY) {
        if (hit.kind() == ClusterCardHitKind.NODE && hit.nodeId().isPresent()) {
            ClusterCardNode node = semanticGraph.node(hit.nodeId().get()).orElse(null);
            if (node == null) {
                return;
            }
            if (node.kind() == ClusterCardNodeKind.EXTERNAL_PORT) {
                node.outsideNodeId()
                        .flatMap(graph::node)
                        .ifPresent(this::locateNode);
                return;
            }
            node.mapNode().ifPresent(mapNode -> {
                if (mapNode.kind() == NodeKind.DEEP_CLUSTER) {
                    this.pushCard(MapCard.deepCluster(mapNode.id().primaryId(), mapNode.levelKey()));
                } else if (mapNode.kind() == NodeKind.CLUSTER) {
                    this.pushCard(MapCard.cluster(mapNode.id().primaryId(), mapNode.levelKey()));
                } else if (mapNode.kind() == NodeKind.FOLD_ANCHOR && mapNode.foldPeerId().isPresent()) {
                    this.pushCard(MapCard.foldPeek(mapNode.id().primaryId(), mapNode.levelKey()));
                } else if (mapNode.kind() == NodeKind.STATION && !mapNode.stationGroupIds().isEmpty()) {
                    this.openStationPicker(mapNode.stationGroupIds().getFirst(), mapNode.levelKey(), mouseX, mouseY, boundary);
                }
            });
            return;
        }
        if (hit.kind() == ClusterCardHitKind.EDGE && hit.edgeId().isPresent()) {
            ClusterCardEdge edge = semanticGraph.edge(hit.edgeId().get()).orElse(null);
            if (edge == null) {
                return;
            }
            if (edge.kind() == ClusterCardEdgeKind.FOLD_PEER_LINK) {
                this.handleFoldPeerLinkClick(semanticGraph, edge);
                return;
            }
            edge.mapEdge()
                    .ifPresent(mapEdge -> this.openEdgePicker(mapEdge, graph.levelKey(), mouseX, mouseY, boundary, RouteCardViewMode.PRACTICAL));
        }
    }

    /** Clicking a fold-peer link behaves like clicking its member fold anchor: open the fold peek card. */
    private void handleFoldPeerLinkClick(ClusterCardSemanticGraph semanticGraph, ClusterCardEdge edge) {
        ClusterCardNode node = semanticGraph.node(edge.from()).or(() -> semanticGraph.node(edge.to())).orElse(null);
        if (node == null) {
            return;
        }
        node.mapNode().ifPresent(mapNode -> this.pushCard(MapCard.foldPeek(mapNode.id().primaryId(), mapNode.levelKey())));
    }

    /**
     * Frames the whole route line on the main map: switches to the first stop's
     * dimension and fits the viewport around every main-map node the line touches
     * there. Degrades to the plain first-station locate when no main-map node of the
     * line can be found.
     */
    private void locateRouteCardLayout(RouteLine line, RouteCardSemanticGraph graph) {
        RouteCardNode anchor = graph.nodes().stream()
                .filter(node -> node.kind() == RouteCardNodeKind.STATION)
                .min(Comparator.comparingInt(RouteCardNode::layoutOccurrence))
                .orElseGet(() -> graph.nodes().isEmpty() ? null : graph.nodes().getFirst());
        if (anchor == null) {
            return;
        }
        ResourceKey<Level> levelKey = anchor.levelKey();
        MapDimensionGraph map = FullRouteMapCache.graph(levelKey).orElse(null);
        VisualRouteMapGraph visual = FullRouteMapCache.visualGraph(levelKey).orElse(null);
        Aabb2 bounds = Aabb2.empty();
        if (map != null) {
            for (MapNode node : map.nodes()) {
                if (!node.routeLineIds().contains(line.id())) {
                    continue;
                }
                Vec2 position = FullRouteMapRenderer.visualPosition(map, visual, node);
                bounds = bounds.include(position.x(), position.y());
            }
        }
        if (bounds.isEmpty()) {
            graph.nodes().stream()
                    .filter(node -> node.stationGroupId().isPresent())
                    .min(Comparator.comparingInt(RouteCardNode::layoutOccurrence))
                    .flatMap(node -> ClientRouteDataCache.stationGroup(node.stationGroupId().get()))
                    .ifPresent(this::locateStation);
            return;
        }
        double paddedWidth = Math.max(1.0D, bounds.maxX() - bounds.minX() + 96.0D);
        double paddedHeight = Math.max(1.0D, bounds.maxY() - bounds.minY() + 96.0D);
        double fitScale = Math.min(Math.max(1.0D, this.mapRect.width()) / paddedWidth, Math.max(1.0D, this.mapRect.height()) / paddedHeight);
        // Cap the zoom so a short line does not end up closer than a plain node locate.
        double zoom = Math.min(fitScale / FullRouteMapConfig.BASE_SCALE, Math.max(FullRouteMapConfig.CLUSTER_AUTO_EXPAND_ZOOM, FullRouteMapConfig.DEFAULT_ZOOM));
        this.switchDimension(levelKey, false);
        this.startViewportTween(levelKey, new ViewportState(levelKey, bounds.centerX(), bounds.centerY(), zoom), VIEWPORT_TWEEN_DURATION_MILLIS);
    }

    private void jumpToFoldPeer(PipeAnchorId peerId) {
        if (FullRouteMapCache.layoutMode().physical()) {
            this.physicalFoldNodeForAnchor(peerId).ifPresent(node -> {
                this.switchDimension(node.levelKey(), false);
                this.startViewportTween(node.levelKey(), new ViewportState(node.levelKey(), node.worldX(), node.worldZ(), Math.max(FullRouteMapConfig.CLUSTER_AUTO_EXPAND_ZOOM, FullRouteMapConfig.DEFAULT_ZOOM)), VIEWPORT_TWEEN_DURATION_MILLIS);
            });
            return;
        }
        this.displayNodeForFoldAnchor(peerId).ifPresent(this::locateNode);
    }

    private Optional<MapNode> displayNodeForFoldAnchor(PipeAnchorId anchorId) {
        return FullRouteMapCache.graph(anchorId.levelKey())
                .flatMap(graph -> graph.nodes().stream()
                        .filter(candidate -> candidate.foldAnchorId().filter(anchorId::equals).isPresent())
                        .findFirst()
                        .map(raw -> graph.node(FullRouteMapRenderer.aggregatedDisplayNodeId(graph, raw.id())).orElse(raw)));
    }

    private Optional<MapNode> rawFoldNodeForAnchor(PipeAnchorId anchorId) {
        return FullRouteMapCache.graph(anchorId.levelKey())
                .flatMap(graph -> graph.nodes().stream()
                        .filter(candidate -> candidate.kind() == NodeKind.FOLD_ANCHOR)
                        .filter(candidate -> candidate.foldAnchorId().filter(anchorId::equals).isPresent())
                        .findFirst());
    }

    private Optional<PhysicalMapNode> physicalFoldNodeForAnchor(PipeAnchorId anchorId) {
        return FullRouteMapCache.physicalGraph(anchorId.levelKey())
                .flatMap(graph -> graph.nodes().stream()
                        .filter(candidate -> candidate.kind() == PhysicalNodeKind.FOLD_ANCHOR)
                        .filter(candidate -> candidate.foldAnchorId().filter(anchorId::equals).isPresent())
                        .findFirst());
    }

    private Optional<MapNode> displayNodeForFoldAnchor(PipeAnchorId anchorId, double zoom) {
        return FullRouteMapCache.graph(anchorId.levelKey())
                .flatMap(graph -> graph.nodes().stream()
                        .filter(candidate -> candidate.foldAnchorId().filter(anchorId::equals).isPresent())
                        .findFirst()
                        .map(raw -> graph.node(FullRouteMapRenderer.displayNodeId(graph, raw.id(), zoom)).orElse(raw)));
    }

    private static double clusterVisibleZoom() {
        return Math.max(FullRouteMapConfig.DEFAULT_ZOOM, FullRouteMapConfig.CLUSTER_AUTO_EXPAND_ZOOM - 0.05D);
    }

    private Optional<MapNode> findFoldNode(UUID primaryId) {
        return FullRouteMapCache.graphs().values().stream()
                .flatMap(graph -> graph.nodes().stream())
                .filter(node -> node.kind() == NodeKind.FOLD_ANCHOR && node.id().primaryId().equals(primaryId))
                .findFirst();
    }

    private Optional<MapNode> findFoldNode(UUID primaryId, Optional<ResourceKey<Level>> levelKey) {
        if (levelKey.isPresent()) {
            return FullRouteMapCache.graph(levelKey.get()).stream()
                    .flatMap(graph -> graph.nodes().stream())
                    .filter(node -> node.kind() == NodeKind.FOLD_ANCHOR && node.id().primaryId().equals(primaryId))
                    .findFirst();
        }
        return this.findFoldNode(primaryId);
    }

    private static String routeLineNames(List<UUID> routeLineIds) {
        return FullMapText.routeLineNames(routeLineIds);
    }

    private void renderNodeTooltip(GuiGraphicsExtractor graphics, MapNode node, int mouseX, int mouseY) {
        switch (node.kind()) {
            case FOLD_ANCHOR -> this.renderTooltipCard(
                    graphics,
                    mouseX,
                    mouseY,
                    FullMapText.displayNameStack(node),
                    Component.translatable("screen.superpipeslide.full_map.tooltip_card.fold_anchor").getString(),
                    List.of(
                            tooltipRow("screen.superpipeslide.full_map.tooltip_field.dimension", dimensionLabel(node.levelKey())),
                            tooltipRow("screen.superpipeslide.full_map.tooltip_field.peer_dimension", node.foldPeerId().map(peer -> dimensionLabel(peer.levelKey())).orElse("?"))),
                    routeChips(node.routeLineIds()),
                    FullRouteMapConfig.MAP_FOLD_MULTI_LINE);
            case CLUSTER -> this.renderTooltipCard(
                    graphics,
                    mouseX,
                    mouseY,
                    FullMapText.displayNameStack(node),
                    Component.translatable("screen.superpipeslide.full_map.tooltip_card.cluster").getString(),
                    List.of(
                            tooltipRow("screen.superpipeslide.full_map.tooltip_field.dimension", dimensionLabel(node.levelKey())),
                            tooltipRow("screen.superpipeslide.full_map.tooltip_field.stations", Integer.toString(node.stationGroupIds().size())),
                            tooltipRow("screen.superpipeslide.full_map.tooltip_field.routes", Integer.toString(node.routeLineIds().size()))),
                    routeChips(node.routeLineIds()),
                    primaryRouteColor(node.routeLineIds()));
            case DEEP_CLUSTER -> this.renderTooltipCard(
                    graphics,
                    mouseX,
                    mouseY,
                    FullMapText.displayNameStack(node),
                    Component.translatable("screen.superpipeslide.full_map.tooltip_card.deep_cluster").getString() + " · " + dimensionLabel(node.levelKey()),
                    List.of(tooltipRow("screen.superpipeslide.full_map.tooltip_field.stations", Integer.toString(node.stationGroupIds().size()))),
                    routeChips(node.routeLineIds()),
                    FullRouteMapConfig.MAP_CLUSTER_OUTLINE);
            case STATION -> this.renderTooltipCard(
                    graphics,
                    mouseX,
                    mouseY,
                    FullMapText.displayNameStack(node),
                    Component.translatable("screen.superpipeslide.station.info").getString(),
                    List.of(
                            tooltipRow("screen.superpipeslide.full_map.tooltip_field.dimension", dimensionLabel(node.levelKey())),
                            tooltipRow("screen.superpipeslide.full_map.tooltip_field.position", (int) node.worldX() + ", " + (int) node.worldY() + ", " + (int) node.worldZ()),
                            tooltipRow("screen.superpipeslide.full_map.tooltip_field.routes", Integer.toString(node.routeLineIds().size())),
                            tooltipRow("screen.superpipeslide.full_map.tooltip_field.transfers", Integer.toString(node.stationGroupIds().stream().findFirst().map(id -> ClientRouteDataCache.stationTransferLinksForStation(id).size()).orElse(0)))),
                    routeChips(node.routeLineIds()),
                    primaryRouteColor(node.routeLineIds()));
        }
    }

    private void renderEdgeTooltip(GuiGraphicsExtractor graphics, List<UUID> routeLineIds, int mouseX, int mouseY) {
        this.renderTooltipCard(
                graphics,
                mouseX,
                mouseY,
                edgeTitleStack(routeLineIds),
                Component.translatable("screen.superpipeslide.full_map.tooltip_card.edge_subtitle", routeLineIds.size()).getString(),
                List.of(tooltipRow("screen.superpipeslide.full_map.tooltip_field.routes", Integer.toString(routeLineIds.size()))),
                routeChips(routeLineIds),
                primaryRouteColor(routeLineIds));
    }

    private void renderTransferHintTooltip(GuiGraphicsExtractor graphics, MapDimensionGraph graph, MapTransferHint hint, int mouseX, int mouseY) {
        MapNode from = graph.node(hint.from()).orElse(null);
        MapNode to = graph.node(hint.to()).orElse(null);
        if (from == null || to == null) {
            FullMapTooltipCard.renderComponent(graphics, this.font, this.mapRect, List.of(), mouseX, mouseY, null, Component.translatable("screen.superpipeslide.full_map.tooltip.transfer_missing"), this.tooltipFadeOpacity("transfer_missing"));
            return;
        }
        List<UUID> routes = new ArrayList<>();
        routes.addAll(from.routeLineIds());
        routes.addAll(to.routeLineIds());
        this.renderTooltipCard(
                graphics,
                mouseX,
                mouseY,
                DisplayNameStack.of(Component.translatable("screen.superpipeslide.full_map.tooltip_card.transfer").getString()),
                FullMapText.primaryName(from) + " -> " + FullMapText.primaryName(to),
                List.of(
                        tooltipRow("screen.superpipeslide.full_map.tooltip_field.distance", Component.translatable("screen.superpipeslide.full_map.tooltip_card.blocks", Math.round(hint.distance())).getString()),
                        tooltipRow("screen.superpipeslide.full_map.tooltip_field.from_routes", routeLineNames(from.routeLineIds())),
                        tooltipRow("screen.superpipeslide.full_map.tooltip_field.to_routes", routeLineNames(to.routeLineIds()))),
                routeChips(routes),
                FullRouteMapConfig.MAP_TRANSFER_HINT);
    }

    private void renderMissingCrossDimensionPathTooltip(GuiGraphicsExtractor graphics, UUID routeLineId, UUID routeLayoutId, ResourceKey<Level> targetLevelKey, MissingCrossDimensionReason reason, int mouseX, int mouseY) {
        DisplayNameStack lineName = ClientRouteDataCache.routeLine(routeLineId).map(FullMapText::displayNameStack).orElse(DisplayNameStack.of("?"));
        String layoutName = ClientRouteDataCache.routeLayout(routeLayoutId).map(FullMapText::primaryName).orElse("");
        this.renderTooltipCard(
                graphics,
                mouseX,
                mouseY,
                lineName,
                Component.translatable("screen.superpipeslide.full_map.tooltip_card.missing_path").getString(),
                List.of(
                        tooltipRow("screen.superpipeslide.layout", layoutName.isBlank() ? "-" : layoutName),
                        tooltipRow("screen.superpipeslide.full_map.tooltip_field.target_dimension", dimensionLabel(targetLevelKey)),
                        tooltipRow("screen.superpipeslide.full_map.missing_hint.reason." + reason.name().toLowerCase(Locale.ROOT), "")),
                routeChips(List.of(routeLineId)),
                primaryRouteColor(List.of(routeLineId)));
    }

    private void renderTooltipCard(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            String title,
            String subtitle,
            List<FullMapTooltipCard.Row> rows,
            List<FullMapTooltipCard.RouteChip> chips,
            int accentColor) {
        this.tooltipPlacement = FullMapTooltipCard.render(graphics, this.font, this.mapRect, this.hoverOverlayAvoidRects, mouseX, mouseY, this.tooltipPlacement, DisplayNameStack.of(title), subtitle, rows, chips, accentColor, this.tooltipFadeOpacity(title + "\n" + subtitle));
    }

    private void renderTooltipCard(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            DisplayNameStack title,
            String subtitle,
            List<FullMapTooltipCard.Row> rows,
            List<FullMapTooltipCard.RouteChip> chips,
            int accentColor) {
        this.tooltipPlacement = FullMapTooltipCard.render(graphics, this.font, this.mapRect, this.hoverOverlayAvoidRects, mouseX, mouseY, this.tooltipPlacement, title, subtitle, rows, chips, accentColor, this.tooltipFadeOpacity((title == null ? "?" : title.flat()) + "\n" + subtitle));
    }

    private static FullMapTooltipCard.Row tooltipRow(String labelKey, String value) {
        return new FullMapTooltipCard.Row(Component.translatable(labelKey).getString(), value, FullMapTheme.TEXT_SECONDARY);
    }

    private static List<FullMapTooltipCard.RouteChip> routeChips(List<UUID> routeLineIds) {
        return routeLineIds.stream()
                .distinct()
                .map(ClientRouteDataCache::routeLine)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing((RouteLine line) -> FullMapText.displayName(line)))
                .limit(6)
                .map(FullRouteMapScreen::routeChip)
                .toList();
    }

    private static FullMapTooltipCard.RouteChip routeChip(RouteLine line) {
        return new FullMapTooltipCard.RouteChip(FullMapText.primaryName(line), routeLineColors(line), line.id().hashCode());
    }

    private static int primaryRouteColor(List<UUID> routeLineIds) {
        return routeLineIds.stream()
                .map(ClientRouteDataCache::routeLine)
                .flatMap(Optional::stream)
                .findFirst()
                .map(FullRouteMapScreen::routeLineColors)
                .flatMap(colors -> colors.stream().findFirst())
                .orElse(FullMapTheme.BORDER_SELECTED);
    }

    private static DisplayNameStack edgeTitleStack(List<UUID> routeLineIds) {
        if (routeLineIds.size() == 1) {
            return routeLineIds.stream()
                    .findFirst()
                    .flatMap(ClientRouteDataCache::routeLine)
                    .map(FullMapText::displayNameStack)
                    .orElse(DisplayNameStack.of(Component.translatable("screen.superpipeslide.route").getString()));
        }
        return DisplayNameStack.of(Component.translatable("screen.superpipeslide.full_map.tooltip_card.shared_routes").getString());
    }

    private void ensureActiveDimension() {
        Collection<ResourceKey<Level>> dimensions = sortedDimensions();
        if (this.activeDimension != null && dimensions.contains(this.activeDimension)) {
            return;
        }
        ResourceKey<Level> playerDimension = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.dimension();
        if (playerDimension != null && dimensions.contains(playerDimension)) {
            this.activeDimension = playerDimension;
            return;
        }
        this.activeDimension = dimensions.stream().findFirst().orElse(null);
    }

    private List<ResourceKey<Level>> sortedDimensions() {
        return FullRouteMapCache.dimensions().stream()
                .sorted(Comparator.comparing(level -> level.identifier().toString()))
                .toList();
    }

    private Optional<MapDimensionGraph> currentGraph() {
        return this.activeDimension == null ? Optional.empty() : FullRouteMapCache.graph(this.activeDimension);
    }

    private Optional<MapDimensionGraph> graphForCard(MapCard card) {
        return card.levelKey().isPresent() ? FullRouteMapCache.graph(card.levelKey().get()) : this.currentGraph();
    }

    private ViewportState storedViewport(ResourceKey<Level> levelKey) {
        return this.viewports.computeIfAbsent(levelKey, key -> {
            ViewportState created;
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.level().dimension().equals(key)) {
                created = new ViewportState(key, Minecraft.getInstance().player.getX(), Minecraft.getInstance().player.getZ(), FullRouteMapConfig.DEFAULT_ZOOM);
            } else {
                Aabb2 bounds = FullRouteMapCache.displayBounds(key);
                created = new ViewportState(key, bounds.centerX(), bounds.centerY(), FullRouteMapConfig.DEFAULT_ZOOM);
            }
            // Keep the initial view inside the soft bounds so the first drag never snaps.
            return this.clampToSoftBounds(key, created);
        });
    }

    private ViewportState displayViewport(MapDimensionGraph graph) {
        ViewportState viewport = this.animatedViewport(graph.levelKey(), this.storedViewport(graph.levelKey()));
        return FullRouteMapCache.layoutMode() == FullRouteMapLayoutMode.SCHEMATIC ? viewport.withFlatCamera() : viewport;
    }

    private ViewportState viewportFor(PhysicalRouteMapGraph graph) {
        ViewportState committed = this.viewports.computeIfAbsent(graph.levelKey(), key -> {
            ViewportState created;
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.level().dimension().equals(key)) {
                created = new ViewportState(key, Minecraft.getInstance().player.getX(), Minecraft.getInstance().player.getZ(), FullRouteMapConfig.DEFAULT_ZOOM);
            } else {
                Aabb2 bounds = graph.worldBounds();
                created = new ViewportState(key, bounds.centerX(), bounds.centerY(), FullRouteMapConfig.DEFAULT_ZOOM);
            }
            return this.clampToSoftBounds(key, created);
        });
        return this.animatedViewport(graph.levelKey(), committed);
    }

    /**
     * Returns the viewport that must be rendered and hit-tested right now: the interpolated
     * value while a {@link ViewportTween} is running for this dimension, otherwise the
     * committed stored value. Writes always stay based on {@link #storedViewport}.
     */
    private ViewportState animatedViewport(ResourceKey<Level> levelKey, ViewportState committed) {
        ViewportTween tween = this.viewportTween;
        if (tween == null || !tween.to().levelKey().equals(levelKey)) {
            return committed;
        }
        long now = System.currentTimeMillis();
        if (tween.finished(now)) {
            this.viewportTween = null;
            return committed;
        }
        return tween.at(now);
    }

    /**
     * Commits {@code target} as the stored viewport and animates the displayed viewport from
     * its current visual value toward it. Committing the target up-front keeps every further
     * write (which is always based on the stored value) consistent with the final state.
     */
    private void startViewportTween(ResourceKey<Level> levelKey, ViewportState target, long durationMillis) {
        this.stopMapInertia();
        ViewportState from = this.animatedViewport(levelKey, this.storedViewport(levelKey));
        this.viewports.put(levelKey, target);
        if (durationMillis <= 0L || from.equals(target)) {
            this.viewportTween = null;
            return;
        }
        this.viewportTween = new ViewportTween(from, target, System.currentTimeMillis(), durationMillis);
    }

    /** Commits the in-flight tween's current interpolated value so manual input continues seamlessly. */
    private void cancelViewportTween(ResourceKey<Level> levelKey) {
        if (levelKey != null && this.viewportTween != null && this.viewportTween.to().levelKey().equals(levelKey)) {
            this.viewports.put(levelKey, this.viewportTween.at(System.currentTimeMillis()));
            this.viewportTween = null;
        }
    }

    /**
     * Hard-clamps the viewport center to the dimension's display bounds expanded by ~20% per
     * axis (with a small minimum padding so single-node maps stay pannable).
     */
    private ViewportState clampToSoftBounds(ResourceKey<Level> levelKey, ViewportState viewport) {
        Aabb2 bounds = FullRouteMapCache.displayBounds(levelKey);
        if (bounds.isEmpty()) {
            return viewport;
        }
        double padX = Math.max(MAP_SOFT_BOUNDS_MIN_PADDING_BLOCKS, (bounds.maxX() - bounds.minX()) * MAP_SOFT_BOUNDS_PADDING_RATIO);
        double padZ = Math.max(MAP_SOFT_BOUNDS_MIN_PADDING_BLOCKS, (bounds.maxY() - bounds.minY()) * MAP_SOFT_BOUNDS_PADDING_RATIO);
        double x = clamp(viewport.centerWorldX(), bounds.minX() - padX, bounds.maxX() + padX);
        double z = clamp(viewport.centerWorldZ(), bounds.minY() - padZ, bounds.maxY() + padZ);
        return x == viewport.centerWorldX() && z == viewport.centerWorldZ() ? viewport : viewport.withCenter(x, z);
    }

    private void stopMapInertia() {
        this.mapInertiaActive = false;
    }

    /** Starts coasting after a map pan if the pointer's release velocity clears the pixel-speed threshold. */
    private void startMapInertiaFromPan() {
        if (this.activeDimension == null) {
            return;
        }
        double pixelsPerBlock = Math.max(1.0E-6D, this.storedViewport(this.activeDimension).zoom() * FullRouteMapConfig.BASE_SCALE);
        double speedPxPerFrame = Math.hypot(this.panVelocityX, this.panVelocityZ) * (1000.0D / 60.0D) * pixelsPerBlock;
        if (speedPxPerFrame < MAP_INERTIA_MIN_SPEED_PX_PER_FRAME) {
            return;
        }
        // Cap flicks so a fast throw cannot skip across the whole map.
        double maxWorldPerMillis = MAP_INERTIA_MAX_SPEED_PX_PER_FRAME / ((1000.0D / 60.0D) * pixelsPerBlock);
        double worldPerMillis = Math.hypot(this.panVelocityX, this.panVelocityZ);
        double scale = worldPerMillis > maxWorldPerMillis ? maxWorldPerMillis / worldPerMillis : 1.0D;
        this.mapInertiaVelocityX = this.panVelocityX * scale;
        this.mapInertiaVelocityZ = this.panVelocityZ * scale;
        this.mapInertiaActive = true;
        this.mapInertiaStartedMillis = System.currentTimeMillis();
        this.mapInertiaLastTickMillis = this.mapInertiaStartedMillis;
    }

    /** Per-frame viewport dynamics: retires finished tweens and integrates pan inertia. */
    private void tickViewportDynamics() {
        long now = System.currentTimeMillis();
        if (this.viewportTween != null && this.viewportTween.finished(now)) {
            this.viewportTween = null;
        }
        if (!this.mapInertiaActive || this.activeDimension == null) {
            return;
        }
        if (now - this.mapInertiaStartedMillis > MAP_INERTIA_MAX_MILLIS) {
            this.mapInertiaActive = false;
            return;
        }
        double dtMillis = Math.max(1.0D, now - this.mapInertiaLastTickMillis);
        this.mapInertiaLastTickMillis = now;
        ResourceKey<Level> levelKey = this.activeDimension;
        ViewportState stored = this.storedViewport(levelKey);
        double nextX = stored.centerWorldX() + this.mapInertiaVelocityX * dtMillis;
        double nextZ = stored.centerWorldZ() + this.mapInertiaVelocityZ * dtMillis;
        ViewportState clamped = this.clampToSoftBounds(levelKey, stored.withCenter(nextX, nextZ));
        this.viewports.put(levelKey, clamped);
        double decay = Math.pow(MAP_INERTIA_DECAY_PER_FRAME, dtMillis / (1000.0D / 60.0D));
        this.mapInertiaVelocityX *= decay;
        this.mapInertiaVelocityZ *= decay;
        boolean hitSoftBounds = clamped.centerWorldX() != nextX || clamped.centerWorldZ() != nextZ;
        double speedPxPerFrame = Math.hypot(this.mapInertiaVelocityX, this.mapInertiaVelocityZ) * (1000.0D / 60.0D) * stored.zoom() * FullRouteMapConfig.BASE_SCALE;
        if (hitSoftBounds || speedPxPerFrame < MAP_INERTIA_MIN_SPEED_PX_PER_FRAME) {
            this.mapInertiaActive = false;
        }
    }

    /**
     * Zooms the map while keeping the world point under the given screen anchor fixed,
     * animating through a short tween so repeated wheel ticks accumulate smoothly.
     */
    private void zoomMapViewport(double anchorScreenX, double anchorScreenY, double factor) {
        if (this.activeDimension == null || this.currentGraph().isEmpty() || factor <= 0.0D || Math.abs(factor - 1.0D) < 1.0E-6D) {
            return;
        }
        ResourceKey<Level> levelKey = this.activeDimension;
        // Chain from the current target (the stored value) rather than the displayed value,
        // so rapid wheel ticks accumulate multiplicatively instead of restarting mid-tween.
        ViewportState stored = this.storedViewport(levelKey);
        ViewportState displayStored = FullRouteMapCache.layoutMode() == FullRouteMapLayoutMode.SCHEMATIC ? stored.withFlatCamera() : stored;
        Vec2 before = FullRouteMapRenderer.screenToWorld(anchorScreenX, anchorScreenY, displayStored, this.mapRect);
        ViewportState zoomed = displayStored.withZoom(displayStored.zoom() * factor);
        Vec2 after = FullRouteMapRenderer.screenToWorld(anchorScreenX, anchorScreenY, zoomed, this.mapRect);
        ViewportState target = this.clampToSoftBounds(levelKey, zoomed.withCenter(zoomed.centerWorldX() + before.x() - after.x(), zoomed.centerWorldZ() + before.y() - after.y()));
        this.startViewportTween(levelKey, target, VIEWPORT_SCROLL_TWEEN_DURATION_MILLIS);
    }

    private void switchDimension(ResourceKey<Level> dimension, boolean preserveCenter) {
        this.clearContextPicker();
        this.dimensionMenuOpen = false;
        this.diagnosticsPanelOpen = false;
        this.diagnosticsScroll = 0.0D;
        if (!dimension.equals(this.activeDimension)) {
            // A running tween or inertia glide only makes sense on its own dimension's map.
            this.viewportTween = null;
            this.stopMapInertia();
        }
        this.activeDimension = dimension;
        this.currentGraph().ifPresent(graph -> {
            Aabb2 bounds = FullRouteMapCache.displayBounds(dimension);
            this.viewports.putIfAbsent(dimension, this.clampToSoftBounds(dimension, new ViewportState(dimension, bounds.centerX(), bounds.centerY(), FullRouteMapConfig.DEFAULT_ZOOM)));
        });
    }

    private void resetViewport() {
        this.resetViewport(false);
    }

    private void resetViewport(boolean animate) {
        this.currentGraph().ifPresent(graph -> {
            Aabb2 bounds = FullRouteMapCache.displayBounds(graph.levelKey());
            ViewportState target = new ViewportState(graph.levelKey(), bounds.centerX(), bounds.centerY(), FullRouteMapConfig.DEFAULT_ZOOM);
            if (animate) {
                this.startViewportTween(graph.levelKey(), target, VIEWPORT_TWEEN_DURATION_MILLIS);
            } else {
                this.stopMapInertia();
                this.viewportTween = null;
                this.viewports.put(graph.levelKey(), target);
            }
        });
    }

    private void panByKey(int key, boolean shiftDown) {
        this.currentGraph().ifPresent(graph -> {
            this.stopMapInertia();
            this.cancelViewportTween(graph.levelKey());
            ViewportState viewport = this.storedViewport(graph.levelKey());
            // Step by ~15% of the visible map width (Shift: 3x) so one press is a nudge, not a jump.
            double step = Math.max(1, this.mapRect.width()) / (viewport.zoom() * FullRouteMapConfig.BASE_SCALE) * MAP_PAN_KEY_STEP_RATIO;
            if (shiftDown) {
                step *= MAP_PAN_KEY_SHIFT_MULTIPLIER;
            }
            double x = viewport.centerWorldX();
            double z = viewport.centerWorldZ();
            if (key == GLFW.GLFW_KEY_LEFT) {
                x -= step;
            } else if (key == GLFW.GLFW_KEY_RIGHT) {
                x += step;
            } else if (key == GLFW.GLFW_KEY_UP) {
                z -= step;
            } else if (key == GLFW.GLFW_KEY_DOWN) {
                z += step;
            }
            this.viewports.put(graph.levelKey(), this.clampToSoftBounds(graph.levelKey(), viewport.withCenter(x, z)));
        });
    }

    private boolean panFocusedRouteCardByKey(int key) {
        if (this.focusedRouteCardKey.isEmpty()) {
            return false;
        }
        String cardKey = this.focusedRouteCardKey.get();
        RouteCardViewport viewport = this.routeCardResolvedViewports.get(cardKey);
        if (viewport == null || this.cardByKey(cardKey).map(card -> card.kind() == CardKind.ROUTE_LINE).orElse(false) == false) {
            return false;
        }
        double step = 72.0D / RouteLineCardRenderer.scale(viewport);
        double x = viewport.centerX();
        double y = viewport.centerY();
        if (key == GLFW.GLFW_KEY_LEFT) {
            x -= step;
        } else if (key == GLFW.GLFW_KEY_RIGHT) {
            x += step;
        } else if (key == GLFW.GLFW_KEY_UP) {
            y -= step;
        } else if (key == GLFW.GLFW_KEY_DOWN) {
            y += step;
        }
        this.setRouteCardViewport(cardKey, viewport.withCenter(x, y));
        return true;
    }

    private boolean panFocusedClusterCardByKey(int key) {
        if (this.focusedClusterCardKey.isEmpty()) {
            return false;
        }
        String cardKey = this.focusedClusterCardKey.get();
        ClusterCardViewport viewport = this.clusterCardResolvedViewports.get(cardKey);
        if (viewport == null || this.cardByKey(cardKey).map(FullRouteMapScreen::isClusterFocusCard).orElse(false) == false) {
            return false;
        }
        double step = 72.0D / ClusterCardRenderer.scale(viewport);
        double x = viewport.centerX();
        double y = viewport.centerY();
        if (key == GLFW.GLFW_KEY_LEFT) {
            x -= step;
        } else if (key == GLFW.GLFW_KEY_RIGHT) {
            x += step;
        } else if (key == GLFW.GLFW_KEY_UP) {
            y -= step;
        } else if (key == GLFW.GLFW_KEY_DOWN) {
            y += step;
        }
        this.setClusterCardViewport(cardKey, viewport.withCenter(x, y));
        return true;
    }

    private boolean panRouteCardViewport(String cardKey, double dragX, double dragY) {
        RouteCardViewport viewport = this.routeCardResolvedViewports.get(cardKey);
        if (viewport == null || this.routeCardMapRegions.get(cardKey) == null) {
            return false;
        }
        double scale = RouteLineCardRenderer.scale(viewport);
        RouteCardViewport next = viewport.withCenter(viewport.centerX() - dragX / scale, viewport.centerY() - dragY / scale);
        this.setRouteCardViewport(cardKey, next);
        return true;
    }

    private boolean panClusterCardViewport(String cardKey, double dragX, double dragY) {
        ClusterCardViewport viewport = this.clusterCardResolvedViewports.get(cardKey);
        if (viewport == null || this.clusterCardMapRegions.get(cardKey) == null) {
            return false;
        }
        double scale = ClusterCardRenderer.scale(viewport);
        ClusterCardViewport next = viewport.withCenter(viewport.centerX() - dragX / scale, viewport.centerY() - dragY / scale);
        this.setClusterCardViewport(cardKey, next);
        return true;
    }

    private void zoomRouteCardViewport(String cardKey, double mouseX, double mouseY, double scrollY) {
        SPSGui.Rect map = this.routeCardMapRegions.get(cardKey);
        RouteCardViewport viewport = this.routeCardResolvedViewports.get(cardKey);
        if (map == null || viewport == null || Math.abs(scrollY) < 0.001D) {
            return;
        }
        Vec2 before = RouteLineCardRenderer.screenToWorld(mouseX, mouseY, viewport, map);
        double factor = scrollY > 0 ? wheelZoomStep() : 1.0D / wheelZoomStep();
        RouteCardViewport zoomed = viewport.withZoom(viewport.zoom() * factor);
        Vec2 after = RouteLineCardRenderer.screenToWorld(mouseX, mouseY, zoomed, map);
        this.setRouteCardViewport(cardKey, zoomed.withCenter(zoomed.centerX() + before.x() - after.x(), zoomed.centerY() + before.y() - after.y()));
    }

    private void zoomClusterCardViewport(String cardKey, double mouseX, double mouseY, double scrollY) {
        if (Math.abs(scrollY) < 0.001D) {
            return;
        }
        this.zoomClusterCardViewportByFactor(cardKey, mouseX, mouseY, scrollY > 0 ? wheelZoomStep() : 1.0D / wheelZoomStep());
    }

    private void zoomClusterCardViewportByFactor(String cardKey, double anchorX, double anchorY, double factor) {
        SPSGui.Rect map = this.clusterCardMapRegions.get(cardKey);
        ClusterCardViewport viewport = this.clusterCardResolvedViewports.get(cardKey);
        if (map == null || viewport == null || factor <= 0.0D || Math.abs(factor - 1.0D) < 1.0E-6D) {
            return;
        }
        Vec2 before = ClusterCardRenderer.screenToWorld(anchorX, anchorY, viewport, map);
        ClusterCardViewport zoomed = viewport.withZoom(viewport.zoom() * factor);
        Vec2 after = ClusterCardRenderer.screenToWorld(anchorX, anchorY, zoomed, map);
        this.setClusterCardViewport(cardKey, zoomed.withCenter(zoomed.centerX() + before.x() - after.x(), zoomed.centerY() + before.y() - after.y()));
    }

    private boolean zoomFocusedClusterCardByKey(boolean zoomIn) {
        if (this.focusedClusterCardKey.isEmpty()) {
            return false;
        }
        String cardKey = this.focusedClusterCardKey.get();
        SPSGui.Rect map = this.clusterCardMapRegions.get(cardKey);
        if (map == null || this.cardByKey(cardKey).filter(FullRouteMapScreen::isClusterFocusCard).isEmpty()) {
            return false;
        }
        // Keyboard zoom anchors at the card map's center, mirroring the main-map keys.
        this.zoomClusterCardViewportByFactor(
                cardKey,
                map.x() + map.width() * 0.5D,
                map.y() + map.height() * 0.5D,
                zoomIn ? MAP_KEY_ZOOM_STEP : 1.0D / MAP_KEY_ZOOM_STEP);
        return true;
    }

    private boolean fitFocusedClusterCard() {
        return this.focusedClusterCardKey.filter(this::fitClusterCard).isPresent();
    }

    /** Resets a cluster card's viewport to the auto-fit framing; false when the card is gone. */
    private boolean fitClusterCard(String cardKey) {
        MapCard card = this.cardByKey(cardKey).filter(FullRouteMapScreen::isClusterFocusCard).orElse(null);
        if (card == null) {
            return false;
        }
        this.updateClusterCardState(cardKey, this.clusterCardStateFor(card).resetViewport());
        return true;
    }

    private void setRouteCardViewport(String cardKey, RouteCardViewport viewport) {
        this.cardByKey(cardKey)
                .filter(card -> card.kind() == CardKind.ROUTE_LINE)
                .ifPresent(card -> {
                    RouteLineCardState state = this.routeLineStateFor(card);
                    UUID selectedLayoutId = this.currentRouteCardLayoutId(state).orElse(null);
                    RouteLineCardState normalized = selectedLayoutId == null || state.selectedLayoutId().filter(selectedLayoutId::equals).isPresent()
                            ? state
                            : state.withSelectedLayout(selectedLayoutId);
                    this.updateRouteCardState(cardKey, normalized.withViewport(viewport));
                });
        this.routeCardResolvedViewports.put(cardKey, viewport);
    }

    private void setClusterCardViewport(String cardKey, ClusterCardViewport viewport) {
        this.cardByKey(cardKey)
                .filter(FullRouteMapScreen::isClusterFocusCard)
                .ifPresent(card -> this.updateClusterCardState(cardKey, this.clusterCardStateFor(card).withViewport(viewport)));
        this.clusterCardResolvedViewports.put(cardKey, viewport);
    }

    private RouteLineCardState routeLineStateFor(MapCard card) {
        return card.routeLineState().orElse(RouteLineCardState.create(card.id(), Optional.empty(), card.levelKey()));
    }

    private ClusterCardState clusterCardStateFor(MapCard card) {
        return card.clusterState().orElse(ClusterCardState.create());
    }

    private Optional<UUID> currentRouteCardLayoutId(RouteLineCardState state) {
        List<RouteLayout> layouts = ClientRouteDataCache.routeLayoutsForLine(state.routeLineId());
        if (layouts.isEmpty()) {
            return state.selectedLayoutId();
        }
        return state.selectedLayoutId()
                .filter(id -> layouts.stream().anyMatch(layout -> layout.id().equals(id)))
                .or(() -> Optional.of(layouts.getFirst().id()));
    }

    private void locateNode(MapNode node) {
        this.switchDimension(node.levelKey(), false);
        double zoom = node.kind() == NodeKind.CLUSTER ? clusterVisibleZoom() : Math.max(FullRouteMapConfig.CLUSTER_AUTO_EXPAND_ZOOM, FullRouteMapConfig.DEFAULT_ZOOM);
        Vec2 visual = FullRouteMapCache.graph(node.levelKey())
                .map(graph -> FullRouteMapRenderer.visualPosition(graph, FullRouteMapCache.visualGraph(node.levelKey()).orElse(null), node))
                .orElse(new Vec2(node.worldX(), node.worldZ()));
        this.startViewportTween(node.levelKey(), new ViewportState(node.levelKey(), visual.x(), visual.y(), zoom), VIEWPORT_TWEEN_DURATION_MILLIS);
    }

    private void locateStation(StationGroup station) {
        this.switchDimension(station.levelKey(), false);
        if (FullRouteMapCache.layoutMode().physical()) {
            Optional<PhysicalStationFrame> frame = FullRouteMapCache.physicalGraph(station.levelKey()).flatMap(graph -> graph.stationFrame(station.id()));
            if (frame.isPresent()) {
                this.startViewportTween(station.levelKey(), new ViewportState(station.levelKey(), frame.get().centerX(), frame.get().centerZ(), Math.max(FullRouteMapConfig.CLUSTER_AUTO_EXPAND_ZOOM, FullRouteMapConfig.DEFAULT_ZOOM)), VIEWPORT_TWEEN_DURATION_MILLIS);
                return;
            }
        }
        Optional<MapNode> stationNode = FullRouteMapCache.graph(station.levelKey())
                .flatMap(graph -> graph.nodes().stream()
                        .filter(node -> node.kind() == NodeKind.STATION && node.stationGroupIds().contains(station.id()))
                        .findFirst());
        if (stationNode.isPresent()) {
            this.locateNode(stationNode.get());
            return;
        }
        this.startViewportTween(station.levelKey(), new ViewportState(station.levelKey(), station.stationBlockPos().getX(), station.stationBlockPos().getZ(), Math.max(FullRouteMapConfig.CLUSTER_AUTO_EXPAND_ZOOM, FullRouteMapConfig.DEFAULT_ZOOM)), VIEWPORT_TWEEN_DURATION_MILLIS);
    }

    private void locateFirstStation(RouteLayout layout) {
        layout.orderedPlatformStops().stream()
                .map(ClientRouteDataCache::platformStop)
                .flatMap(Optional::stream)
                .findFirst()
                .flatMap(platform -> ClientRouteDataCache.stationGroup(platform.stationGroupId()))
                .ifPresent(this::locateStation);
    }

    private List<SearchResult> searchResults() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            this.cachedSearchQuery = "";
            this.cachedSearchResults = List.of();
            return List.of();
        }
        long routeRevision = ClientRouteDataCache.revision();
        if (!query.equals(this.cachedSearchQuery)
                || this.cachedSearchRouteRevision != routeRevision
                || !Objects.equals(this.cachedSearchDimension, this.activeDimension)) {
            this.cachedSearchQuery = query;
            this.cachedSearchRouteRevision = routeRevision;
            this.cachedSearchDimension = this.activeDimension;
            this.cachedSearchResults = this.computeSearchResults(query);
        }
        return this.cachedSearchResults;
    }

    private List<SearchResult> computeSearchResults(String query) {
        List<SearchResult> results = new ArrayList<>();
        for (RouteLine line : ClientRouteDataCache.routeLines()) {
            DisplayNameStack name = FullMapText.displayNameStack(line);
            if (name.searchText().toLowerCase(Locale.ROOT).contains(query)) {
                ResourceKey<Level> dimension = firstStationDimension(line).orElse(this.activeDimension == null ? Level.OVERWORLD : this.activeDimension);
                results.add(new SearchResult(SearchKind.ROUTE_LINE, line.id(), dimension, name, Component.translatable("screen.superpipeslide.route").getString()));
            }
        }
        for (RouteLayout layout : ClientRouteDataCache.routeLayouts()) {
            DisplayNameStack name = FullMapText.displayNameStack(layout);
            if (name.searchText().toLowerCase(Locale.ROOT).contains(query)) {
                ResourceKey<Level> dimension = firstStationDimension(layout).orElse(this.activeDimension == null ? Level.OVERWORLD : this.activeDimension);
                results.add(new SearchResult(SearchKind.ROUTE_LAYOUT, layout.id(), dimension, name, Component.translatable("screen.superpipeslide.layout").getString()));
            }
        }
        return results.stream().limit(8).toList();
    }

    private void selectSearchResult(SearchResult result) {
        this.searchBox.setValue("");
        this.searchBox.setFocused(false);
        this.searchExpanded = false;
        this.setFocused(null);
        if (result.kind() == SearchKind.ROUTE_LINE) {
            this.switchDimension(result.levelKey(), false);
            this.pushCard(MapCard.routeLine(result.id(), Optional.empty(), result.levelKey()));
            return;
        }
        ClientRouteDataCache.routeLayout(result.id()).ifPresent(layout -> {
            this.switchDimension(result.levelKey(), false);
            this.pushCard(MapCard.routeLine(layout.routeLineId(), Optional.of(layout.id()), result.levelKey()));
        });
    }

    private Optional<ResourceKey<Level>> firstStationDimension(RouteLine line) {
        return ClientRouteDataCache.routeLayoutsForLine(line.id()).stream().findFirst().flatMap(this::firstStationDimension);
    }

    private Optional<ResourceKey<Level>> firstStationDimension(RouteLayout layout) {
        return layout.orderedPlatformStops().stream()
                .map(ClientRouteDataCache::platformStop)
                .flatMap(Optional::stream)
                .findFirst()
                .flatMap(platform -> ClientRouteDataCache.stationGroup(platform.stationGroupId()))
                .map(StationGroup::levelKey);
    }

    private void pushCard(MapCard card) {
        this.clearContextPicker();
        String key = card.windowKey();
        for (int i = 0; i < this.cardStack.size(); i++) {
            if (this.cardStack.get(i).windowKey().equals(key)) {
                this.cardStack.remove(i);
                this.cardStack.add(card);
                return;
            }
        }
        if (this.cardStack.size() >= MAX_CARD_STACK_DEPTH) {
            MapCard removed = this.cardStack.removeFirst();
            this.cardWindowBounds.remove(removed.windowKey());
            this.routeCardMapRegions.remove(removed.windowKey());
            this.routeCardControlRegions.remove(removed.windowKey());
            this.routeCardResolvedViewports.remove(removed.windowKey());
            this.clusterCardMapRegions.remove(removed.windowKey());
            this.clusterCardFitRegions.remove(removed.windowKey());
            this.clusterCardResolvedViewports.remove(removed.windowKey());
            this.routeCardStopListRegions.remove(removed.windowKey());
            this.routeCardStopListMaxScrolls.remove(removed.windowKey());
            this.routeCardStopListScrolls.remove(removed.windowKey());
            this.stationCardRouteRegions.remove(removed.windowKey());
            this.stationCardRouteMaxScrolls.remove(removed.windowKey());
            this.stationCardRouteScrolls.remove(removed.windowKey());
            if (this.focusedRouteCardKey.filter(removed.windowKey()::equals).isPresent()) {
                this.focusedRouteCardKey = Optional.empty();
            }
            if (this.focusedClusterCardKey.filter(removed.windowKey()::equals).isPresent()) {
                this.focusedClusterCardKey = Optional.empty();
            }
            this.toast(Component.translatable("screen.superpipeslide.full_map.card_limit").getString());
        }
        this.cardStack.add(card);
    }

    private void updateRouteCardState(String key, RouteLineCardState state) {
        for (int i = 0; i < this.cardStack.size(); i++) {
            MapCard card = this.cardStack.get(i);
            if (card.windowKey().equals(key) && card.kind() == CardKind.ROUTE_LINE) {
                this.cardStack.set(i, card.withRouteLineState(state));
                return;
            }
        }
    }

    private void updateClusterCardState(String key, ClusterCardState state) {
        for (int i = 0; i < this.cardStack.size(); i++) {
            MapCard card = this.cardStack.get(i);
            if (card.windowKey().equals(key) && isClusterFocusCard(card)) {
                this.cardStack.set(i, card.withClusterState(state));
                return;
            }
        }
    }

    private void replaceTop(MapCard card) {
        this.clearContextPicker();
        if (this.cardStack.isEmpty()) {
            this.pushCard(card);
            return;
        }
        MapCard previous = this.cardStack.removeLast();
        if (!previous.windowKey().equals(card.windowKey())) {
            SPSGui.Rect bounds = this.cardWindowBounds.remove(previous.windowKey());
            if (bounds != null) {
                this.cardWindowBounds.put(card.windowKey(), bounds);
            }
        }
        this.pushCard(card);
    }

    private void popCard() {
        this.clearContextPicker();
        if (!this.cardStack.isEmpty()) {
            MapCard removed = this.cardStack.removeLast();
            this.cardWindowBounds.remove(removed.windowKey());
            this.routeCardMapRegions.remove(removed.windowKey());
            this.routeCardControlRegions.remove(removed.windowKey());
            this.routeCardResolvedViewports.remove(removed.windowKey());
            this.clusterCardMapRegions.remove(removed.windowKey());
            this.clusterCardFitRegions.remove(removed.windowKey());
            this.clusterCardResolvedViewports.remove(removed.windowKey());
            this.routeCardStopListRegions.remove(removed.windowKey());
            this.routeCardStopListMaxScrolls.remove(removed.windowKey());
            this.routeCardStopListScrolls.remove(removed.windowKey());
            this.stationCardRouteRegions.remove(removed.windowKey());
            this.stationCardRouteMaxScrolls.remove(removed.windowKey());
            this.stationCardRouteScrolls.remove(removed.windowKey());
            if (this.focusedRouteCardKey.filter(removed.windowKey()::equals).isPresent()) {
                this.focusedRouteCardKey = Optional.empty();
            }
            if (this.focusedClusterCardKey.filter(removed.windowKey()::equals).isPresent()) {
                this.focusedClusterCardKey = Optional.empty();
            }
        }
    }

    private void closeCard(String key) {
        this.clearContextPicker();
        this.cardStack.removeIf(card -> card.windowKey().equals(key));
        this.cardWindowBounds.remove(key);
        this.routeCardMapRegions.remove(key);
        this.routeCardControlRegions.remove(key);
        this.routeCardResolvedViewports.remove(key);
        this.clusterCardMapRegions.remove(key);
        this.clusterCardFitRegions.remove(key);
        this.clusterCardResolvedViewports.remove(key);
        this.routeCardStopListRegions.remove(key);
        this.routeCardStopListMaxScrolls.remove(key);
        this.routeCardStopListScrolls.remove(key);
        this.stationCardRouteRegions.remove(key);
        this.stationCardRouteMaxScrolls.remove(key);
        this.stationCardRouteScrolls.remove(key);
        if (this.draggingCardKey.filter(key::equals).isPresent()) {
            this.draggingCardKey = Optional.empty();
        }
        if (this.draggingRouteCardViewportKey.filter(key::equals).isPresent()) {
            this.draggingRouteCardViewportKey = Optional.empty();
        }
        if (this.draggingClusterCardViewportKey.filter(key::equals).isPresent()) {
            this.draggingClusterCardViewportKey = Optional.empty();
        }
        if (this.focusedRouteCardKey.filter(key::equals).isPresent()) {
            this.focusedRouteCardKey = Optional.empty();
        }
        if (this.focusedClusterCardKey.filter(key::equals).isPresent()) {
            this.focusedClusterCardKey = Optional.empty();
        }
    }

    private void clearContextPicker() {
        this.contextPicker = Optional.empty();
        this.contextPickerBounds = new SPSGui.Rect(0, 0, 0, 0);
        this.contextPickerActionBounds.clear();
        this.contextPickerRowBounds.clear();
        this.contextPickerScroll = 0.0D;
        this.contextPickerMaxScroll = 0.0D;
    }

    /**
     * Fires a deferred context-picker row click for a release that stayed under the
     * click/drag threshold. Ignored when the picker was closed (or swapped) meanwhile.
     */
    private void dispatchContextPickerRow(ContextPicker picker, double releaseX, double releaseY) {
        if (this.contextPicker.filter(picker::equals).isEmpty()) {
            return;
        }
        for (int i = 0; i < Math.min(this.contextPickerRowBounds.size(), picker.rows().size()); i++) {
            if (this.contextPickerRowBounds.get(i).contains(releaseX, releaseY)) {
                picker.rows().get(i).action().run();
                return;
            }
        }
    }

    private void registerScrollBar(FullMapScrollBar.Binding binding) {
        this.scrollBarBindings.put(binding.id(), binding);
    }

    /**
     * Thumb drags and track paging, checked ahead of all other click targets. Uses the
     * previous frame's bindings, matching how the other per-frame region maps are read.
     */
    private boolean handleScrollBarMousePress(double mouseX, double mouseY) {
        for (FullMapScrollBar.Binding binding : this.scrollBarBindings.values()) {
            if (!binding.scrollable() || !binding.barSlotRect().contains(mouseX, mouseY)) {
                continue;
            }
            if (binding.thumbRect().contains(mouseX, mouseY)) {
                this.scrollBarDrag = FullMapScrollBar.Drag.thumb(binding, mouseY);
            } else {
                double direction = mouseY < binding.thumbY() ? -1.0D : 1.0D;
                binding.write(binding.scroll() + direction * FullMapScrollBar.pageAmount(binding));
            }
            return true;
        }
        return false;
    }

    /**
     * Starts watching a press inside a row-drag-scrollable list (outside the bar slot
     * itself). Returns non-null only when the list can actually scroll; the caller then
     * defers the row's click action to release via {@link #pendingMapClick}.
     */
    @Nullable
    private PendingRowScroll rowScrollPressAt(double mouseX, double mouseY) {
        for (FullMapScrollBar.Binding binding : this.scrollBarBindings.values()) {
            if (binding.scrollable()
                    && binding.rowDragScroll()
                    && binding.list().contains(mouseX, mouseY)
                    && !binding.barSlotRect().contains(mouseX, mouseY)) {
                return new PendingRowScroll(binding.id(), mouseX, mouseY, binding.scroll());
            }
        }
        return null;
    }

    private record ContextPicker(DisplayNameStack title, String subtitle, int anchorX, int anchorY, SPSGui.Rect boundary, List<ContextPickerAction> actions, List<ContextPickerRow> rows) {}

    private record ContextPickerAction(SPSGui.Icon icon, Component tooltip, Runnable action) {}

    private record ContextPickerRow(DisplayNameStack title, String subtitle, List<Integer> colors, Runnable action) {}

    private record NavigationStationDisplay(String label, boolean placeholder, boolean endpoint) {}

    private record PendingMapClick(@Nullable PendingClickAction action, @Nullable Runnable doubleClickAction, double pressX, double pressY) {}

    /** A press inside a row-drag-scrollable list that has not yet crossed the 6px click/drag threshold. */
    private record PendingRowScroll(String bindingId, double pressX, double pressY, double startScroll) {}

    /**
     * A short ease-out animation between two committed viewport states. The target is written
     * to the stored viewport as soon as the tween starts; this record only drives the value
     * used for rendering and hit-testing until the duration elapses. Zoom interpolates
     * exponentially (zoom feels multiplicative), center/pitch interpolate linearly, and
     * bearing takes the shortest angular path.
     */
    private record ViewportTween(ViewportState from, ViewportState to, long startMillis, long durationMillis) {
        private ViewportState at(long nowMillis) {
            double t = this.durationMillis <= 0L ? 1.0D : (nowMillis - this.startMillis) / (double) this.durationMillis;
            if (t >= 1.0D) {
                return this.to;
            }
            if (t <= 0.0D) {
                return this.from;
            }
            double eased = easeOutCubic(t);
            double zoom = this.from.zoom() * Math.pow(this.to.zoom() / this.from.zoom(), eased);
            double bearingDelta = (this.to.bearingDegrees() - this.from.bearingDegrees() + 540.0D) % 360.0D - 180.0D;
            return new ViewportState(
                    this.to.levelKey(),
                    lerp(this.from.centerWorldX(), this.to.centerWorldX(), eased),
                    lerp(this.from.centerWorldZ(), this.to.centerWorldZ(), eased),
                    zoom,
                    lerp(this.from.pitchDegrees(), this.to.pitchDegrees(), eased),
                    this.from.bearingDegrees() + bearingDelta * eased);
        }

        private boolean finished(long nowMillis) {
            return nowMillis - this.startMillis >= this.durationMillis;
        }

        private static double easeOutCubic(double t) {
            double inverse = 1.0D - t;
            return 1.0D - inverse * inverse * inverse;
        }

        private static double lerp(double a, double b, double t) {
            return a + (b - a) * t;
        }
    }

    @FunctionalInterface
    private interface PendingClickAction {
        void run(double releaseX, double releaseY);
    }

    private record RouteCardGraphCacheKey(long routeRevision, long pipeRevision, UUID routeLineId, UUID routeLayoutId, RouteCardViewMode viewMode) {}

    private record RouteCardGraphBundle(RouteCardSemanticGraph stopListGraph, RouteCardSemanticGraph semanticGraph, RouteCardVisualGraph visualGraph) {}

    private record ClusterCardGraphCacheKey(long routeRevision, long pipeRevision, NodeId clusterNodeId, ClusterCardProfile profile) {}

    private record ClusterCardGraphBundle(ClusterCardSemanticGraph semanticGraph, ClusterCardVisualGraph visualGraph) {}

    private void bringCardToFront(String key) {
        for (int i = 0; i < this.cardStack.size(); i++) {
            MapCard card = this.cardStack.get(i);
            if (card.windowKey().equals(key)) {
                if (i + 1 < this.cardStack.size()) {
                    this.cardStack.remove(i);
                    this.cardStack.add(card);
                }
                return;
            }
        }
    }

    private Optional<String> topmostCardAt(double mouseX, double mouseY) {
        for (int i = this.cardStack.size() - 1; i >= 0; i--) {
            String key = this.cardStack.get(i).windowKey();
            SPSGui.Rect bounds = this.cardWindowBounds.get(key);
            if (bounds != null && bounds.contains(mouseX, mouseY)) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    private Optional<MapCard> cardByKey(String key) {
        return this.cardStack.stream().filter(card -> card.windowKey().equals(key)).findFirst();
    }

    private void toast(String message) {
        this.toast = message;
        this.toastUntilMillis = System.currentTimeMillis() + TOAST_DURATION_MILLIS;
        this.toastAction = null;
        this.toastActionLabel = "";
    }

    private void toast(String message, String actionLabel, Runnable action, long durationMillis) {
        this.toast = message;
        this.toastUntilMillis = System.currentTimeMillis() + durationMillis;
        this.toastAction = action;
        this.toastActionLabel = actionLabel;
    }

    private void runToastAction() {
        Runnable action = this.toastAction;
        this.toastAction = null;
        this.toast = "";
        this.toastUntilMillis = 0L;
        if (action != null) {
            action.run();
        }
    }

    private static String dimensionLabel(ResourceKey<Level> dimension) {
        String path = dimension.identifier().getPath();
        return switch (dimension.identifier().toString()) {
            case "minecraft:overworld" -> Component.translatable("screen.superpipeslide.full_map.dimension.overworld").getString();
            case "minecraft:the_nether" -> Component.translatable("screen.superpipeslide.full_map.dimension.nether").getString();
            case "minecraft:the_end" -> Component.translatable("screen.superpipeslide.full_map.dimension.end").getString();
            default -> path;
        };
    }

    private int stationCountInDimension(ResourceKey<Level> dimension) {
        return this.dimensionCounts(dimension)[0];
    }

    private int routeCountInDimension(ResourceKey<Level> dimension) {
        return this.dimensionCounts(dimension)[1];
    }

    private int[] dimensionCounts(ResourceKey<Level> dimension) {
        long routeRevision = ClientRouteDataCache.revision();
        long pipeRevision = ClientPipeNetworkCache.aggregateRevision();
        if (this.cachedDimensionCountsRouteRevision != routeRevision || this.cachedDimensionCountsPipeRevision != pipeRevision) {
            this.cachedDimensionCountsRouteRevision = routeRevision;
            this.cachedDimensionCountsPipeRevision = pipeRevision;
            this.cachedDimensionCounts.clear();
        }
        return this.cachedDimensionCounts.computeIfAbsent(dimension, FullRouteMapScreen::computeDimensionCounts);
    }

    private static int[] computeDimensionCounts(ResourceKey<Level> dimension) {
        int stationCount = (int) ClientRouteDataCache.stationGroups().stream()
                .filter(station -> station.levelKey().equals(dimension))
                .count();
        Set<UUID> routeLineIds = new LinkedHashSet<>();
        FullRouteMapCache.graph(dimension).ifPresent(graph -> {
            graph.nodes().forEach(node -> routeLineIds.addAll(node.routeLineIds()));
            graph.edges().forEach(edge -> routeLineIds.addAll(edge.routeLineIds()));
        });
        return new int[] { stationCount, routeLineIds.size() };
    }

    private static String dimensionLabel(String dimensionId) {
        return switch (dimensionId) {
            case "minecraft:overworld" -> Component.translatable("screen.superpipeslide.full_map.dimension.overworld").getString();
            case "minecraft:the_nether" -> Component.translatable("screen.superpipeslide.full_map.dimension.nether").getString();
            case "minecraft:the_end" -> Component.translatable("screen.superpipeslide.full_map.dimension.end").getString();
            default -> {
                int separator = dimensionId.indexOf(':');
                yield separator >= 0 && separator + 1 < dimensionId.length() ? dimensionId.substring(separator + 1) : dimensionId;
            }
        };
    }

    private Optional<VisualNode> syntheticPortalNode(MapDimensionGraph graph, NodeId nodeId) {
        if (FullRouteMapCache.layoutMode() != FullRouteMapLayoutMode.SCHEMATIC) {
            return Optional.empty();
        }
        return FullRouteMapCache.visualGraph(graph.levelKey())
                .flatMap(visualGraph -> visualGraph.node(nodeId))
                .filter(node -> node.kind() == NodeKind.FOLD_ANCHOR);
    }

    private static String schematicPortalTargetLabel(VisualNode portal) {
        return portal.label().isBlank() || portal.label().startsWith("fold:") ? "?" : dimensionLabel(portal.label());
    }

    private record SchematicLegendRow(RouteLine line, double visibleLength, int pathCount, Optional<UUID> layoutId) {}

    private static final class SchematicLegendAccumulator {
        private final UUID routeLineId;
        private double visibleLength;
        private int pathCount;
        private Optional<UUID> layoutId = Optional.empty();

        private SchematicLegendAccumulator(UUID routeLineId) {
            this.routeLineId = routeLineId;
        }
    }
}
