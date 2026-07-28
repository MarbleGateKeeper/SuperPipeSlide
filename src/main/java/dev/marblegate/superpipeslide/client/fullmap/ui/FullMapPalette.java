package dev.marblegate.superpipeslide.client.fullmap.ui;

import dev.marblegate.superpipeslide.client.gui.base.SPSGui;

/**
 * Color palette for the full-route-map UI. Surface, border, text, focus and
 * background/grid colors are resolved through a palette so a dark variant can be
 * introduced later without touching render code. Route line theme colors are
 * intentionally not part of the palette.
 *
 * <p>Only {@link #LIGHT} exists for now; a future DARK variant hooks in through
 * {@link #current()}.
 */
public record FullMapPalette(
        int surfaceCard,
        int surfaceCardActive,
        int surfaceCardInactive,
        int surfaceHeader,
        int surfaceHeaderActive,
        int surfaceToolbar,
        int surfaceControl,
        int surfaceControlHover,
        int surfaceControlSelected,
        int surfaceControlDisabled,
        int border,
        int borderActive,
        int borderSelected,
        int borderMuted,
        int textPrimary,
        int textSecondary,
        int textMuted,
        int textDisabled,
        int textOnDark,
        int focusRing,
        int focusHalo,
        int mapBackground,
        int physicalBackground,
        int physicalGrid,
        int physicalGridMajor,
        int geographicBackground,
        int geographicGrid,
        int geographicGridMajor,
        int practicalBackground,
        int practicalGrid,
        int practicalGridMajor,
        int schematicBackground) {

    public static final FullMapPalette LIGHT = new FullMapPalette(
            SPSGui.PANEL_BASE,
            0xFAF8FBFF,
            0xF4F4F7FA,
            0xF4F2F6FB,
            0xF8EAF4FF,
            0xDFFFFFFF,
            SPSGui.PANEL_ELEVATED,
            0xFFF6FAFE,
            SPSGui.PANEL_HIGHLIGHT,
            SPSGui.PANEL_RECESSED,
            SPSGui.PANEL_LINE,
            SPSGui.INFO,
            SPSGui.INFO,
            0xFFD7DEE8,
            SPSGui.TEXT_PRIMARY,
            SPSGui.TEXT_SECONDARY,
            SPSGui.TEXT_MUTED,
            SPSGui.TEXT_DISABLED,
            0xFFFFFFFF,
            0xFFB45309,
            0x66B45309,
            0xFFFFFFFF,
            0xFFF7FBFF,
            0x5FD7E4F3,
            0x88AEC7E2,
            0xFFF8FAF5,
            0x3FD4DECA,
            0x66B8C5A9,
            0xFFFFFFFF,
            0x4FE3E9F0,
            0x77CBD5E1,
            0xFFFFFFFF);
    /** Returns the active palette. Only {@link #LIGHT} exists for now. */
    public static FullMapPalette current() {
        return LIGHT;
    }
}
