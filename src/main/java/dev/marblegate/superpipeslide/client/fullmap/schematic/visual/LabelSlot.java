package dev.marblegate.superpipeslide.client.fullmap.schematic.visual;

/**
 * Coarse placement slot of a station label relative to its node icon. The schematic solvers
 * pick a slot per label during layout; the renderer projects slots deterministically into
 * screen rects (see {@code FullRouteMapRenderer#labelSlotBounds}), so a label keeps its
 * chosen side across zoom levels instead of being re-solved from raw coordinates.
 *
 * <p>The declaration order matches the deterministic preference order of the solver-side
 * candidate lists: side-near first, then below/above near and close tiers, the four
 * diagonals, and the far side tiers as last resort.</p>
 */
public enum LabelSlot {
    RIGHT_NEAR,
    LEFT_NEAR,
    BELOW_NEAR,
    ABOVE_NEAR,
    BELOW_CLOSE,
    ABOVE_CLOSE,
    DIAGONAL_DOWN_RIGHT,
    DIAGONAL_DOWN_LEFT,
    DIAGONAL_UP_RIGHT,
    DIAGONAL_UP_LEFT,
    RIGHT_FAR,
    LEFT_FAR
}
