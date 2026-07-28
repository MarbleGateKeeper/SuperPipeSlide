package dev.marblegate.superpipeslide.client.fullmap.screen;

import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.fullmap.card.CardKind;
import dev.marblegate.superpipeslide.client.fullmap.card.MapCard;
import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardState;
import dev.marblegate.superpipeslide.client.fullmap.routecard.render.RouteLineCardState;
import dev.marblegate.superpipeslide.client.fullmap.ui.DisplayNameStack;
import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapText;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Owns the full route map's card stack and card-window bookkeeping for
 * {@code FullRouteMapScreen}: the stack itself (same-key dedupe, depth cap with
 * oldest-first eviction), the persistent per-card window bounds, the per-frame
 * resize/clamp pass, and the z-order and hit-test queries (bring-to-front,
 * topmost-card-at, card-by-key).
 *
 * <p>Deliberately free of rendering and of any back-reference to the screen: this
 * class never draws, never reads input state beyond the coordinates handed to
 * {@link #topmostCardAt(double, double)}, and never touches the screen's per-card
 * render regions, focus keys, or drag state. Side effects the screen must perform
 * when a card leaves the stack (dropping stale render regions, clearing focus keys)
 * or when a push evicts the oldest card (the card-limit toast) are delivered through
 * the constructor callbacks, so the dependency points one way only: screen &rarr;
 * manager.
 *
 * <p>Session-state wiring ({@code FullRouteMapSessionState}) likewise stays on the
 * screen; the manager only exposes the immutable views a snapshot needs
 * ({@link #cards()}, {@link #windowBoundsView()}) plus the restore entry point
 * ({@link #restore(Map, List)}).
 */
final class MapCardManager {
    private static final int MAX_CARD_STACK_DEPTH = 10;
    // D8: card windows ease toward their preferred size instead of snapping.
    private static final double CARD_RESIZE_LERP_PER_FRAME = 0.25D;

    private final List<MapCard> cardStack = new ArrayList<>();
    private final Map<String, SPSGui.Rect> cardWindowBounds = new HashMap<>();
    // Per-frame bounds (stack order) and top-card bounds, rebuilt by
    // updateWindowBounds. Nothing reads them today; kept for parity with the
    // pre-extraction screen state.
    private final List<SPSGui.Rect> cardBounds = new ArrayList<>();
    private SPSGui.Rect topCardBounds = new SPSGui.Rect(0, 0, 0, 0);
    /** Screen-side cleanup for a card that left the stack (render regions, focus keys). */
    private final Consumer<String> cardRemovedListener;
    /** Screen-side notification that a push evicted the oldest card (card-limit toast). */
    private final Runnable cardEvictionListener;

    MapCardManager(Consumer<String> cardRemovedListener, Runnable cardEvictionListener) {
        this.cardRemovedListener = cardRemovedListener;
        this.cardEvictionListener = cardEvictionListener;
    }

    /**
     * Adds a card to the top of the stack. A card already on the stack under the same
     * window key moves to the top and is replaced by the given instance (its window
     * bounds are kept). Past {@code MAX_CARD_STACK_DEPTH} the oldest card is evicted:
     * its bounds drop here, the removed-card callback lets the screen drop its render
     * regions, and the eviction callback lets the screen show the card-limit toast.
     */
    void push(MapCard card) {
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
            this.cardRemovedListener.accept(removed.windowKey());
            this.cardEvictionListener.run();
        }
        this.cardStack.add(card);
    }

    /** Removes the top card, if any, dropping its window bounds. */
    void pop() {
        if (!this.cardStack.isEmpty()) {
            MapCard removed = this.cardStack.removeLast();
            this.cardWindowBounds.remove(removed.windowKey());
            this.cardRemovedListener.accept(removed.windowKey());
        }
    }

    /**
     * Replaces the top card with the given one, carrying the window bounds over when
     * the window key changes (e.g. a layout-chip swap opens the sibling card in the
     * same window). Behaves like {@link #push(MapCard)} on an empty stack.
     */
    void replaceTop(MapCard card) {
        if (this.cardStack.isEmpty()) {
            this.push(card);
            return;
        }
        MapCard previous = this.cardStack.removeLast();
        if (!previous.windowKey().equals(card.windowKey())) {
            SPSGui.Rect bounds = this.cardWindowBounds.remove(previous.windowKey());
            if (bounds != null) {
                this.cardWindowBounds.put(card.windowKey(), bounds);
            }
        }
        this.push(card);
    }

    /** Removes every card with the given window key and drops its window bounds. */
    void remove(String key) {
        this.cardStack.removeIf(card -> card.windowKey().equals(key));
        this.cardWindowBounds.remove(key);
        this.cardRemovedListener.accept(key);
    }

    /** Moves the card with the given window key to the top of the stack; no-op when already on top. */
    void bringToFront(String key) {
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

    /** Window key of the topmost card whose window contains the point, top-down. */
    Optional<String> topmostCardAt(double mouseX, double mouseY) {
        for (int i = this.cardStack.size() - 1; i >= 0; i--) {
            String key = this.cardStack.get(i).windowKey();
            SPSGui.Rect bounds = this.cardWindowBounds.get(key);
            if (bounds != null && bounds.contains(mouseX, mouseY)) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    /** First card on the stack with the given window key. */
    Optional<MapCard> cardByKey(String key) {
        return this.cardStack.stream().filter(card -> card.windowKey().equals(key)).findFirst();
    }

    boolean isEmpty() {
        return this.cardStack.isEmpty();
    }

    /** True when the given window key belongs to the top card; false on an empty stack. */
    boolean isTopCard(String key) {
        return !this.cardStack.isEmpty() && this.cardStack.getLast().windowKey().equals(key);
    }

    /** Bottom-to-top view of the stack. Unmodifiable; live (reflects later mutations). */
    List<MapCard> cards() {
        return Collections.unmodifiableList(this.cardStack);
    }

    /** Current window bounds by window key. Unmodifiable; live (reflects later mutations). */
    Map<String, SPSGui.Rect> windowBoundsView() {
        return Collections.unmodifiableMap(this.cardWindowBounds);
    }

    /** Current window bounds of one card, or null when none was computed yet. */
    @Nullable
    SPSGui.Rect windowBounds(String key) {
        return this.cardWindowBounds.get(key);
    }

    /** Stores the window bounds of one card (window drag on the screen). */
    void setWindowBounds(String key, SPSGui.Rect bounds) {
        this.cardWindowBounds.put(key, bounds);
    }

    /** Adopts a previous session's card stack and window bounds (screen constructor). */
    void restore(Map<String, SPSGui.Rect> windowBounds, List<MapCard> cards) {
        this.cardWindowBounds.putAll(windowBounds);
        this.cardStack.addAll(cards);
    }

    /** Replaces the route-line state of the route card with the given window key, if present. */
    void updateRouteCardState(String key, RouteLineCardState state) {
        for (int i = 0; i < this.cardStack.size(); i++) {
            MapCard card = this.cardStack.get(i);
            if (card.windowKey().equals(key) && card.kind() == CardKind.ROUTE_LINE) {
                this.cardStack.set(i, card.withRouteLineState(state));
                return;
            }
        }
    }

    /** Replaces the cluster state of the cluster/deep-cluster card with the given window key, if present. */
    void updateClusterCardState(String key, ClusterCardState state) {
        for (int i = 0; i < this.cardStack.size(); i++) {
            MapCard card = this.cardStack.get(i);
            if (card.windowKey().equals(key) && (card.kind() == CardKind.CLUSTER || card.kind() == CardKind.DEEP_CLUSTER)) {
                this.cardStack.set(i, card.withClusterState(state));
                return;
            }
        }
    }

    /**
     * Rebuilds the window-bounds map once per frame, ahead of any hover testing, so
     * {@link #topmostCardAt(double, double)} never falls through a card on the first
     * frame after it opens (previously bounds were (re)computed inside the render pass,
     * after the map hover test had already run against last frame's bounds).
     */
    void updateWindowBounds(int screenWidth, int screenHeight) {
        this.topCardBounds = new SPSGui.Rect(0, 0, 0, 0);
        this.cardBounds.clear();
        Set<String> activeKeys = this.cardStack.stream().map(MapCard::windowKey).collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        this.cardWindowBounds.keySet().removeIf(key -> !activeKeys.contains(key));
        for (int i = 0; i < this.cardStack.size(); i++) {
            MapCard card = this.cardStack.get(i);
            int cardIndex = i;
            String key = card.windowKey();
            SPSGui.Rect bounds = this.cardWindowBounds.computeIfAbsent(key, ignored -> this.defaultCardBounds(card, cardIndex, screenWidth, screenHeight));
            bounds = this.resizeAndClampCard(card, bounds, screenWidth, screenHeight);
            this.cardWindowBounds.put(key, bounds);
            this.cardBounds.add(bounds);
            if (i == this.cardStack.size() - 1) {
                this.topCardBounds = bounds;
            }
        }
    }

    /**
     * Eases a card window toward its preferred size (content-driven) while leaving the
     * position — which the user may have dragged — untouched. Runs once per frame from
     * {@link #updateWindowBounds(int, int)}, so a 25% per-frame lerp converges in ~15 frames.
     */
    private SPSGui.Rect resizeAndClampCard(MapCard card, SPSGui.Rect bounds, int screenWidth, int screenHeight) {
        int targetWidth = preferredCardWidth(card, screenWidth);
        int targetHeight = Math.max(76, Math.min(screenHeight - 16, preferredCardHeight(card)));
        int width = targetWidth;
        int height = targetHeight;
        if (bounds.width() > 0 && bounds.height() > 0) {
            width = (int) Math.round(bounds.width() + (targetWidth - bounds.width()) * CARD_RESIZE_LERP_PER_FRAME);
            height = (int) Math.round(bounds.height() + (targetHeight - bounds.height()) * CARD_RESIZE_LERP_PER_FRAME);
            // Snap the final pixel so the size actually converges instead of approaching forever.
            if (Math.abs(width - targetWidth) <= 1) {
                width = targetWidth;
            }
            if (Math.abs(height - targetHeight) <= 1) {
                height = targetHeight;
            }
        }
        return this.clampBounds(new SPSGui.Rect(bounds.x(), bounds.y(), width, height), screenWidth, screenHeight);
    }

    private SPSGui.Rect defaultCardBounds(MapCard card, int index, int screenWidth, int screenHeight) {
        int width = preferredCardWidth(card, screenWidth);
        int height = Math.min(screenHeight - 16, preferredCardHeight(card));
        int x = screenWidth - width - 10 - (index % 4) * 14;
        int y = 34 + (index % 5) * 18;
        return this.clampBounds(new SPSGui.Rect(x, y, width, Math.max(76, height)), screenWidth, screenHeight);
    }

    /** Clamps a card window to the screen, keeping the minimum sizes the cards assume. */
    SPSGui.Rect clampBounds(SPSGui.Rect bounds, int screenWidth, int screenHeight) {
        int width = Math.min(bounds.width(), Math.max(156, screenWidth - 8));
        int height = Math.min(bounds.height(), Math.max(76, screenHeight - 8));
        int maxX = Math.max(4, screenWidth - width - 4);
        int minY = 4;
        int maxY = Math.max(minY, screenHeight - height - 4);
        int x = Math.max(4, Math.min(maxX, bounds.x()));
        int y = Math.max(minY, Math.min(maxY, bounds.y()));
        return new SPSGui.Rect(x, y, width, height);
    }

    private static int preferredCardWidth(MapCard card, int screenWidth) {
        return switch (card.kind()) {
            case ROUTE_LINE -> Math.min(screenWidth - 20, Math.max(460, Math.min(620, (int) Math.round(screenWidth * 0.36D))));
            case DEEP_CLUSTER -> Math.min(330, Math.max(300, screenWidth / 4));
            case CLUSTER -> Math.min(310, Math.max(280, screenWidth / 4));
            case FOLD_PEEK -> Math.min(300, Math.max(260, screenWidth / 4));
            case STATION -> Math.min(280, Math.max(248, screenWidth / 5));
        };
    }

    private static int preferredCardHeight(MapCard card) {
        return switch (card.kind()) {
            case ROUTE_LINE -> 320;
            case FOLD_PEEK -> 200;
            case STATION -> preferredStationCardHeight(card.id());
            case DEEP_CLUSTER -> 208;
            case CLUSTER -> 188;
        };
    }

    private static int preferredStationCardHeight(UUID stationId) {
        int layoutRows = ClientRouteDataCache.platformStopsInStation(stationId).stream()
                .flatMap(platform -> ClientRouteDataCache.routeLayoutIdsForPlatformStop(platform.id()).stream())
                .distinct()
                .toList()
                .size();
        int transferRows = ClientRouteDataCache.stationTransferLinksForStation(stationId).size();
        int visibleRows = Math.min(6, layoutRows);
        int visibleTransferRows = Math.min(2, transferRows);
        int headerExtra = ClientRouteDataCache.stationGroup(stationId)
                .map(FullMapText::displayNameStack)
                .filter(DisplayNameStack::hasSecondary)
                .map(ignored -> 8)
                .orElse(0);
        return Math.max(116, Math.min(214, 72 + headerExtra + visibleTransferRows * 16 + visibleRows * 16 + (layoutRows > visibleRows ? 12 : 8)));
    }
}
