package dev.marblegate.superpipeslide.client.core.navigation;

/**
 * Canonical palette for navigation semantics (destination, transfers, final walk,
 * warnings). Four surfaces render these semantics and must reference this palette
 * instead of declaring local constants, so one semantic keeps one hue everywhere: the
 * route HUD ({@code ClientRouteHudController}), the navigation HUD and its in-world
 * markers ({@code ClientNavigationHudController}), the full-route-map overlay
 * ({@code FullMapNavigationOverlayRenderer}), and the itinerary timeline
 * ({@code FullRouteMapScreen}). Any new navigation-semantic color is added here first.
 * Generic feedback colors that are not navigation semantics (info blue, danger red)
 * stay in {@code SPSGui}, and route line colors keep coming from the route data itself.
 */
public final class NavigationSemanticColors {
    /**
     * Destination / journey-origin endpoint marker: soft mint green. Mid-brightness
     * between the three previous endpoint greens, and calm enough to sit next to
     * {@link #CROSS_DIMENSION_TRANSFER}; readable on dark HUD panels and, behind the
     * dark marker outline, on light map backgrounds.
     */
    public static final int DESTINATION = 0xFF7CC7A2;

    /**
     * Same-station transfer: a deeper, more saturated green from the same family as
     * {@link #DESTINATION} — related enough to read as "no friction", distinct enough
     * to not be mistaken for an endpoint. Matches the long-standing map overlay value.
     */
    public static final int SAME_STATION_TRANSFER = 0xFF30B76B;

    /** Out-of-station transfer (on-foot leg between stations): warm amber. */
    public static final int OUT_OF_STATION_TRANSFER = 0xFFFFB13B;

    /**
     * Cross-dimension transfer: lavender violet, matching the full-map overlay. Blue is
     * deliberately reserved for generic info accents ({@code SPSGui.INFO}).
     */
    public static final int CROSS_DIMENSION_TRANSFER = 0xFFC59BFF;

    /**
     * Final on-foot walk to the destination. Intentionally identical to
     * {@link #OUT_OF_STATION_TRANSFER}: both are on-foot legs and every surface has
     * always rendered them in the same amber.
     */
    public static final int FINAL_WALK = 0xFFFFB13B;

    /**
     * Warning accent for navigation content (unreachable-plan banner, step warnings).
     * Shares the amber hue with the on-foot legs in this palette revision.
     */
    public static final int WARNING = 0xFFFFB13B;

    private NavigationSemanticColors() {}
}
