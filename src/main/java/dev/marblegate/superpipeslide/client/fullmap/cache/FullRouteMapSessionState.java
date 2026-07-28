package dev.marblegate.superpipeslide.client.fullmap.cache;

import dev.marblegate.superpipeslide.client.fullmap.card.MapCard;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.ViewportState;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Static holder for the full route map's cross-open session state: a new
 * {@code FullRouteMapScreen} instance is created on every open, so anything the user
 * arranged (viewports, card windows, card stack, search text, navigation drawer position,
 * list scrolls) would be lost without this hand-off. The screen adopts the snapshot in its
 * constructor and writes a fresh one back from {@code removed()}.
 *
 * <p>Unlike {@link FullRouteMapCache} (60s TTL on the built map data), the session state
 * never expires: it holds pure UI state, not map data, so there is nothing to go stale.
 * Restored cards whose referenced routes/stations/clusters meanwhile disappeared are
 * covered by the renderers' existing "missing" fallbacks, and card windows outside the
 * current window size are clamped back onto the screen by the per-frame bounds pass.
 * The state is cleared together with the client caches on logout/disconnect.
 */
public final class FullRouteMapSessionState {
    private static Snapshot state = Snapshot.empty();

    private FullRouteMapSessionState() {}

    /** Returns the currently stored snapshot. Never null; empty until the first save. */
    public static Snapshot snapshot() {
        return state;
    }

    /** Replaces the stored snapshot with a defensive copy of the given one. */
    public static void save(Snapshot snapshot) {
        state = snapshot == null ? Snapshot.empty() : snapshot.defensiveCopy();
    }

    /** Drops all remembered state (logout/disconnect, alongside the client caches). */
    public static void clear() {
        state = Snapshot.empty();
    }

    /**
     * Immutable view of everything preserved across screen opens. Maps and the card list
     * must be treated as read-only by consumers; {@link #save(Snapshot)} copies them.
     */
    public record Snapshot(
            Map<ResourceKey<Level>, ViewportState> viewports,
            Map<String, SPSGui.Rect> cardWindowBounds,
            List<MapCard> cardStack,
            String searchText,
            double navigationDrawerUserXRatio,
            double navigationDrawerUserYRatio,
            Map<String, Double> lineStripScrolls,
            Map<String, Double> routeCardStopListScrolls,
            Map<String, Double> stationCardRouteScrolls,
            double navigationResultScroll,
            double navigationItineraryScroll,
            double schematicLegendScroll) {
        private static Snapshot empty() {
            return new Snapshot(
                    Map.of(),
                    Map.of(),
                    List.of(),
                    "",
                    Double.NaN,
                    Double.NaN,
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    0.0D,
                    0.0D,
                    0.0D);
        }

        private Snapshot defensiveCopy() {
            return new Snapshot(
                    new HashMap<>(this.viewports),
                    new HashMap<>(this.cardWindowBounds),
                    new ArrayList<>(this.cardStack),
                    this.searchText,
                    this.navigationDrawerUserXRatio,
                    this.navigationDrawerUserYRatio,
                    new HashMap<>(this.lineStripScrolls),
                    new HashMap<>(this.routeCardStopListScrolls),
                    new HashMap<>(this.stationCardRouteScrolls),
                    this.navigationResultScroll,
                    this.navigationItineraryScroll,
                    this.schematicLegendScroll);
        }
    }
}
