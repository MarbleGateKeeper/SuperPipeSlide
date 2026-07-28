package dev.marblegate.superpipeslide.client.fullmap.screen;

import dev.marblegate.superpipeslide.client.fullmap.ui.FullMapTheme;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared scroll bar for the full route map's scrollable lists. Replaces the five
 * copy-pasted bar renderers (search results, navigation itinerary, station card route
 * list, context picker, schematic legend) with one component that provides:
 *
 * <ul>
 * <li>rendering of track + thumb, keeping each list's established look via {@link Style};</li>
 * <li>hit geometry for thumb dragging and track click paging;</li>
 * <li>row-area press-drag scrolling, coordinated by the screen with the 6px
 * click/drag threshold (a drag past the threshold becomes a scroll, a short
 * press-release stays a row click).</li>
 * </ul>
 *
 * <p>Each list registers a fresh {@link Binding} every frame at the exact spot where the
 * bar is drawn; mouse handling then works off the previous frame's bindings, like the
 * other per-frame region maps in {@code FullRouteMapScreen}. Wheel scrolling is untouched
 * and keeps its per-list step sizes.
 */
final class FullMapScrollBar {
    /** Extra pixels added to the left of the drawn bar for hit-testing, so the thumb is grabbable. */
    private static final int HIT_SLOP_PX = 2;

    private FullMapScrollBar() {}

    /** Muted track + info-colored thumb, 3px wide, min thumb 18px (search results, itinerary). */
    static final Style INFO = new Style(SPSGui.withAlpha(FullMapTheme.TEXT_MUTED, 0x30), SPSGui.withAlpha(SPSGui.INFO, 0xAA), 18, true, 2, 1, 3, 3);
    /** Muted track + muted thumb, 3px wide, min thumb 14px (station card route list). */
    static final Style MUTED = new Style(SPSGui.withAlpha(FullMapTheme.TEXT_MUTED, 0x30), SPSGui.withAlpha(FullMapTheme.TEXT_MUTED, 0x88), 14, true, 2, 1, 3, 3);
    /** Trackless info-colored thumb, 2px wide, min thumb 18px (context picker). */
    static final Style FLOATING = new Style(0, SPSGui.withAlpha(SPSGui.INFO, 0xAA), 18, false, 2, 1, 2, 2);
    /** Muted-border track + selected-border thumb, min thumb 12px (schematic legend). */
    static final Style LEGEND = new Style(FullMapTheme.BORDER_MUTED, FullMapTheme.BORDER_SELECTED, 12, true, 3, 1, 4, 3);

    /**
     * Visual style of a bar. Insets and widths are measured from the list's right edge:
     * the track/thumb occupy {@code [list.right() - inset, list.right() - inset + width)}.
     */
    record Style(int trackColor, int thumbColor, int minThumbHeight, boolean drawTrack, int trackInset, int trackWidth, int thumbInset, int thumbWidth) {}

    /**
     * One frame's view of a scrollable list's bar: where it is, how far it is scrolled,
     * and where to write a new scroll offset. Registrations are per frame; the scroll
     * value itself lives in the screen's existing fields/maps and is read back fresh on
     * the next frame.
     *
     * @param rowDragScroll true when pressing and dragging inside the list's row area
     *                      should pan the content (row clicks are then deferred to release by the screen,
     *                      using the same 6px threshold as map clicks)
     */
    record Binding(String id, SPSGui.Rect list, int contentHeight, double scroll, double maxScroll, Style style, boolean rowDragScroll, DoubleConsumer scrollWriter) {
        boolean scrollable() {
            return this.maxScroll > 0.0D;
        }

        int thumbHeight() {
            int height = this.list.height();
            return Math.max(this.style.minThumbHeight(), (int) Math.round(height * height / (double) Math.max(height, this.contentHeight)));
        }

        int thumbY() {
            return this.list.y() + (int) Math.round((this.list.height() - this.thumbHeight()) * (this.scroll / this.maxScroll));
        }

        /** The drawn thumb rectangle. */
        SPSGui.Rect thumbRect() {
            return new SPSGui.Rect(this.list.right() - this.style.thumbInset(), this.thumbY(), this.style.thumbWidth(), this.thumbHeight());
        }

        /**
         * The hit rectangle of the whole bar slot (a few pixels wider than the drawn
         * bar). Inside it: a press on the thumb starts a drag, a press above/below the
         * thumb pages by one viewport.
         */
        SPSGui.Rect barSlotRect() {
            int inset = Math.max(this.style.trackInset(), this.style.thumbInset()) + HIT_SLOP_PX;
            return new SPSGui.Rect(this.list.right() - inset, this.list.y(), inset, this.list.height());
        }

        /** Clamps and stores a new scroll offset through the binding's writer. */
        void write(double value) {
            this.scrollWriter.accept(Math.max(0.0D, Math.min(this.maxScroll, value)));
        }
    }

    /** An in-progress scroll drag: thumb mode moves the thumb to the pointer, row mode pans the content with the pointer. */
    record Drag(String bindingId, boolean thumbMode, double grabOffset, double pressY, double startScroll) {
        static Drag thumb(Binding binding, double pressY) {
            return new Drag(binding.id(), true, pressY - binding.thumbY(), 0.0D, 0.0D);
        }

        static Drag row(Binding binding, double pressY, double startScroll) {
            return new Drag(binding.id(), false, 0.0D, pressY, startScroll);
        }
    }

    static void render(GuiGraphicsExtractor graphics, Binding binding) {
        if (!binding.scrollable()) {
            return;
        }
        Style style = binding.style();
        if (style.drawTrack()) {
            graphics.fill(
                    binding.list().right() - style.trackInset(),
                    binding.list().y(),
                    binding.list().right() - style.trackInset() + style.trackWidth(),
                    binding.list().bottom(),
                    style.trackColor());
        }
        SPSGui.Rect thumb = binding.thumbRect();
        graphics.fill(thumb.x(), thumb.y(), thumb.right(), thumb.bottom(), style.thumbColor());
    }

    /** Scroll offset that puts the thumb's top edge at the given screen Y. */
    static double scrollForThumbTop(Binding binding, double thumbTop) {
        int travel = binding.list().height() - binding.thumbHeight();
        if (travel <= 0) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, (thumbTop - binding.list().y()) / (double) travel)) * binding.maxScroll();
    }

    /** Track-click paging distance: roughly one viewport of content. */
    static double pageAmount(Binding binding) {
        return Math.max(24, binding.list().height() - 16);
    }
}
