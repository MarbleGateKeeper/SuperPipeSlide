package dev.marblegate.superpipeslide.client.fullmap.ui;

import dev.marblegate.superpipeslide.client.gui.base.SPSGui;

public final class FullMapTheme {
    public static final int SURFACE_CARD = SPSGui.PANEL_BASE;
    public static final int SURFACE_CARD_ACTIVE = 0xFAF8FBFF;
    public static final int SURFACE_CARD_INACTIVE = 0xF4F4F7FA;
    public static final int SURFACE_HEADER = 0xF4F2F6FB;
    public static final int SURFACE_HEADER_ACTIVE = 0xF8EAF4FF;
    public static final int SURFACE_TOOLBAR = 0xDFFFFFFF;
    public static final int SURFACE_CONTROL = SPSGui.PANEL_ELEVATED;
    public static final int SURFACE_CONTROL_HOVER = 0xFFF6FAFE;
    public static final int SURFACE_CONTROL_SELECTED = SPSGui.PANEL_HIGHLIGHT;
    public static final int SURFACE_CONTROL_DISABLED = SPSGui.PANEL_RECESSED;

    public static final int BORDER = SPSGui.PANEL_LINE;
    public static final int BORDER_ACTIVE = SPSGui.INFO;
    public static final int BORDER_SELECTED = SPSGui.INFO;
    public static final int BORDER_MUTED = 0xFFD7DEE8;

    public static final int TEXT_PRIMARY = SPSGui.TEXT_PRIMARY;
    public static final int TEXT_SECONDARY = SPSGui.TEXT_SECONDARY;
    public static final int TEXT_MUTED = SPSGui.TEXT_MUTED;
    public static final int TEXT_DISABLED = SPSGui.TEXT_DISABLED;
    public static final int TEXT_ON_DARK = 0xFFFFFFFF;

    public static final int FOCUS_HALO = 0x55FFD166;
    public static final int FOCUS_RING = 0xFFF59E0B;
    public static final int RELATED_HALO = 0x26FFD166;
    public static final int DIMMED_LINE = 0x668B98A7;

    public static final int SHADOW = 0x1C000000;
    public static final int HIGHLIGHT_SOFT = 0x171F73B7;

    public static final int PHYSICAL_BACKGROUND = 0xFFF7FBFF;
    public static final int PHYSICAL_GRID = 0x5FD7E4F3;
    public static final int PHYSICAL_GRID_MAJOR = 0x88AEC7E2;
    public static final int GEOGRAPHIC_BACKGROUND = 0xFFF8FAF5;
    public static final int GEOGRAPHIC_GRID = 0x3FD4DECA;
    public static final int GEOGRAPHIC_GRID_MAJOR = 0x66B8C5A9;
    public static final int PRACTICAL_BACKGROUND = 0xFFFFFFFF;
    public static final int PRACTICAL_GRID = 0x4FE3E9F0;
    public static final int PRACTICAL_GRID_MAJOR = 0x77CBD5E1;
    public static final int SCHEMATIC_BACKGROUND = 0xFFFFFFFF;

    public static final float TYPE_TITLE = 1.0F;
    public static final float TYPE_BODY = 0.80F;
    public static final float TYPE_META = 0.64F;
    public static final float TYPE_TINY = 0.55F;

    public static final int SPACE_XS = 3;
    public static final int SPACE_SM = 4;
    public static final int SPACE_MD = 6;
    public static final int SPACE_LG = 12;
    public static final int SPACE_XL = 16;
    public static final int ICON_BUTTON = 20;
    public static final int ICON_BUTTON_SMALL = 16;
    public static final int CARD_PADDING = 8;
    public static final int CARD_PADDING_COMPACT = 6;
    public static final int CARD_HEADER_HEIGHT = 24;
    public static final int CARD_HEADER_WITH_META = 30;
    public static final int CARD_HEADER_WITH_STACK = 38;
    public static final int ROUTE_CHIP_HEIGHT = 13;
    public static final int ROUTE_CHIP_TINY_HEIGHT = 12;

    private FullMapTheme() {
    }
}
