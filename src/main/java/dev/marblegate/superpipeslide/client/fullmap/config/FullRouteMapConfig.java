package dev.marblegate.superpipeslide.client.fullmap.config;

import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapTheme;

public final class FullRouteMapConfig {
    public static final double CLUSTER_THRESHOLD = 64.0D;
    public static final double DEEP_CLUSTER_THRESHOLD = 12.0D;
    public static final double TRANSFER_HINT_THRESHOLD = 192.0D;
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
    public static final long UPDATE_DEBOUNCE_MILLIS = 500L;
    public static final long CACHE_TTL_MILLIS = 60_000L;
    public static final int SCHEMATIC_SOLVER_VERSION = 10;

    public static final int MAP_BACKGROUND = 0xFFFFFFFF;
    public static final int MAP_GRID = FullMapTheme.PRACTICAL_GRID;
    public static final int MAP_GRID_MAJOR = FullMapTheme.PRACTICAL_GRID_MAJOR;
    public static final int MAP_LABEL = FullMapTheme.TEXT_PRIMARY;
    public static final int MAP_LABEL_MUTED = FullMapTheme.TEXT_MUTED;
    public static final int MAP_NODE_FILL = 0xFFF5F8FC;
    public static final int MAP_NODE_OUTLINE = 0xFF1B2633;
    public static final int MAP_CARD_NODE_OUTLINE = 0xFF000000;
    public static final int MAP_CARD_LABEL = 0xFF000000;
    public static final int MAP_CLUSTER_FILL = 0xFFF2F7FD;
    public static final int MAP_CLUSTER_OUTLINE = 0xFF000000;
    public static final int MAP_FOLD_FILL = 0xFFFFFFFF;
    public static final int MAP_TRANSFER_HINT = 0x66888888;
    public static final int MAP_TRANSFER_HINT_DIMMED_EDGE = 0x4D888888;
    public static final int MAP_TRUNK = 0xFFB8C0CA;
    public static final int MAP_FOCUS_HALO = FullMapTheme.FOCUS_HALO;
    public static final int MAP_FOCUS_RING = FullMapTheme.FOCUS_RING;
    public static final int MAP_FOLD_MULTI_LINE = 0xFF888888;

    private FullRouteMapConfig() {}
}
