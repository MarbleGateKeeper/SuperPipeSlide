package dev.marblegate.superpipeslide.client.fullmap.config;

import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapTheme;

public final class FullRouteMapConfig {
    public static final double CLUSTER_THRESHOLD = 64.0D;
    public static final double DEEP_CLUSTER_THRESHOLD = 12.0D;
    public static final double CLUSTER_AUTO_EXPAND_ZOOM = 2.5D;
    public static final double CLUSTER_CARD_SPREAD_FLAT_THRESHOLD = 16.0D;
    public static final double BASE_SCALE = 0.25D;
    public static final double ZOOM_MIN = 0.1D;
    public static final double ZOOM_MAX = 16.0D;
    public static final double DEFAULT_ZOOM = 1.0D;
    public static final double CAMERA_PITCH_MIN_DEGREES = 0.0D;
    public static final double CAMERA_PITCH_MAX_DEGREES = 58.0D;
    public static final double CAMERA_PITCH_DRAG_DEGREES_PER_PIXEL = 0.22D;
    public static final double CAMERA_BEARING_DRAG_DEGREES_PER_PIXEL = 0.35D;
    public static final double CAMERA_TILTED_THRESHOLD_DEGREES = 0.5D;
    public static final double CAMERA_ROTATED_THRESHOLD_DEGREES = 0.5D;
    public static final double CAMERA_FOCUS_Y_RATIO = 0.64D;
    public static final double CAMERA_HORIZON_Y_RATIO = 0.16D;
    public static final int NODE_RADIUS_PX = 6;
    public static final int CLUSTER_RADIUS_PX = 10;
    public static final int LINE_WIDTH_PX = 3;
    public static final int TRUNK_THRESHOLD = 4;
    public static final double TRUNK_DOT_MIN_ZOOM = 0.55D;
    public static final int FOLD_ANCHOR_AVOIDANCE_RADIUS_PX = 28;
    public static final int CARD_LINE_STRIP_SCROLL_STEP_PX = 36;
    public static final int CARD_LINE_STRIP_CHIP_MIN_PX = 36;
    public static final int CARD_LINE_STRIP_CHIP_MAX_PX = 112;
    public static final double CARD_LINE_STRIP_TEXT_SCALE = 0.62D;
    public static final int MAX_LABELS_PER_FRAME = 360;
    // Screen-space gap between a station icon and its label at full icon scale, in pixels.
    // The schematic solver uses the layout-space equivalent (LABEL_NODE_GAP_PX / BASE_SCALE
    // blocks) for its label candidate tiers; the renderer uses this value directly when
    // projecting label slots, so both sides share one definition of "next to the node".
    public static final double LABEL_NODE_GAP_PX = 4.0D;
    // A/B switch for the metro solver's axis-compaction pass
    // (MetroMapSchematicSolver#compactAxes): shrinks embedding voids after annealing and
    // rebuilds routing on the compacted positions, rolling back on any defect regression.
    public static final boolean SCHEMATIC_COMPACTION_ENABLED = true;
    public static final long UPDATE_DEBOUNCE_MILLIS = 500L;
    public static final long CACHE_TTL_MILLIS = 60_000L;
    public static final int SCHEMATIC_SOLVER_VERSION = 13;

    public static final int MAP_BACKGROUND = FullMapTheme.palette().mapBackground();
    public static final int MAP_GRID = FullMapTheme.PRACTICAL_GRID;
    public static final int MAP_GRID_MAJOR = FullMapTheme.PRACTICAL_GRID_MAJOR;
    public static final int MAP_LABEL = FullMapTheme.TEXT_PRIMARY;
    public static final int MAP_LABEL_MUTED = FullMapTheme.TEXT_MUTED;
    public static final int MAP_NODE_FILL = 0xFFF5F8FC;
    public static final int MAP_NODE_OUTLINE = 0xFF1B2633;
    public static final int MAP_CARD_NODE_OUTLINE = 0xFF1B2633;
    public static final int MAP_CARD_LABEL = 0xFF1B2633;
    public static final int MAP_CLUSTER_FILL = 0xFFF2F7FD;
    public static final int MAP_CLUSTER_OUTLINE = 0xFF1B2633;
    public static final int MAP_FOLD_FILL = 0xFFFFFFFF;
    public static final int MAP_TRANSFER_HINT = 0x99888888;
    public static final int MAP_TRANSFER_HINT_DIMMED_EDGE = 0x4D888888;
    public static final int MAP_TRUNK = 0xFF9AA6B2;
    public static final int MAP_FOCUS_HALO = FullMapTheme.FOCUS_HALO;
    public static final int MAP_FOCUS_RING = FullMapTheme.FOCUS_RING;
    public static final int MAP_FOLD_MULTI_LINE = 0xFF888888;
    public static final int MAP_PORT_FILL = 0xFFFFFFFF;
    public static final int MAP_VIEWPORT_HINT_FADE = 0x44EEF2F7;
    public static final int MAP_VIEWPORT_HINT_CHEVRON = 0xAA4B5563;

    private FullRouteMapConfig() {}
}
