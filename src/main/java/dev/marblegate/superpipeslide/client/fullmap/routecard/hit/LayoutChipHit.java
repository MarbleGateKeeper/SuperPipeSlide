package dev.marblegate.superpipeslide.client.fullmap.routecard.hit;

import dev.marblegate.superpipeslide.client.gui.base.SPSGui;

import java.util.UUID;

public record LayoutChipHit(UUID layoutId, SPSGui.Rect bounds, String label) {
}
