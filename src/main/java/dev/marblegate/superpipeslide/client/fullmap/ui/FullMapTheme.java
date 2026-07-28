package dev.marblegate.superpipeslide.client.fullmap.ui;

/**
 * Design tokens for the full-route-map UI. Surface, border, text, focus and
 * background/grid colors resolve through the active {@link FullMapPalette}; the
 * color constants below are equal-value forwards kept so existing call sites do
 * not change. Decorative accents, spacing and type scales stay plain constants.
 */
public final class FullMapTheme {
    public static final int SURFACE_CARD = palette().surfaceCard();
    public static final int SURFACE_CARD_ACTIVE = palette().surfaceCardActive();
    public static final int SURFACE_CARD_INACTIVE = palette().surfaceCardInactive();
    public static final int SURFACE_HEADER = palette().surfaceHeader();
    public static final int SURFACE_HEADER_ACTIVE = palette().surfaceHeaderActive();
    public static final int SURFACE_TOOLBAR = palette().surfaceToolbar();
    public static final int SURFACE_CONTROL = palette().surfaceControl();
    public static final int SURFACE_CONTROL_HOVER = palette().surfaceControlHover();
    public static final int SURFACE_CONTROL_SELECTED = palette().surfaceControlSelected();
    public static final int SURFACE_CONTROL_DISABLED = palette().surfaceControlDisabled();

    public static final int BORDER = palette().border();
    public static final int BORDER_ACTIVE = palette().borderActive();
    public static final int BORDER_SELECTED = palette().borderSelected();
    public static final int BORDER_MUTED = palette().borderMuted();

    public static final int TEXT_PRIMARY = palette().textPrimary();
    public static final int TEXT_SECONDARY = palette().textSecondary();
    public static final int TEXT_MUTED = palette().textMuted();
    public static final int TEXT_DISABLED = palette().textDisabled();
    public static final int TEXT_ON_DARK = palette().textOnDark();

    public static final int FOCUS_HALO = palette().focusHalo();
    public static final int FOCUS_RING = palette().focusRing();
    public static final int RELATED_HALO = 0x26FFD166;
    public static final int DIMMED_LINE = 0x668B98A7;

    public static final int WARNING = 0xFFFFB13B;

    public static final int INNER_HIGHLIGHT_STRONG = 0xBFFFFFFF;
    public static final int INNER_HIGHLIGHT_MEDIUM = 0x99FFFFFF;
    public static final int INNER_HIGHLIGHT_SOFT = 0x66FFFFFF;
    public static final int INNER_HIGHLIGHT_EXTRA_SOFT = 0x77FFFFFF;

    public static final int SHADOW = 0x1C000000;
    public static final int HIGHLIGHT_SOFT = 0x171F73B7;

    public static final int PHYSICAL_BACKGROUND = palette().physicalBackground();
    public static final int PHYSICAL_GRID = palette().physicalGrid();
    public static final int PHYSICAL_GRID_MAJOR = palette().physicalGridMajor();
    public static final int GEOGRAPHIC_BACKGROUND = palette().geographicBackground();
    public static final int GEOGRAPHIC_GRID = palette().geographicGrid();
    public static final int GEOGRAPHIC_GRID_MAJOR = palette().geographicGridMajor();
    public static final int PRACTICAL_BACKGROUND = palette().practicalBackground();
    public static final int PRACTICAL_GRID = palette().practicalGrid();
    public static final int PRACTICAL_GRID_MAJOR = palette().practicalGridMajor();
    public static final int SCHEMATIC_BACKGROUND = palette().schematicBackground();

    public static final float TYPE_TITLE = 1.0F;
    public static final float TYPE_BODY = 0.80F;
    public static final float TYPE_META = 0.64F;
    // 0.60F is the smallest scale at which CJK glyphs stay legible.
    public static final float TYPE_TINY = 0.60F;

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
    public static final int CARD_HEADER_TITLE_RESERVE = 58;
    public static final int CARD_HEADER_PRIMARY_ADVANCE = 11;
    public static final int CARD_HEADER_SECONDARY_ADVANCE = 9;
    public static final int ROUTE_CHIP_HEIGHT = 13;
    public static final int ROUTE_CHIP_TINY_HEIGHT = 12;

    /** Returns the active color palette. */
    public static FullMapPalette palette() {
        return FullMapPalette.current();
    }

    private FullMapTheme() {}
}
