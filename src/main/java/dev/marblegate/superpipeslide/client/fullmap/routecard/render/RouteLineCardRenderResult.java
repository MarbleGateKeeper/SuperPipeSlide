package dev.marblegate.superpipeslide.client.fullmap.routecard.render;


import dev.marblegate.superpipeslide.client.fullmap.routecard.hit.LayoutChipHit;
import dev.marblegate.superpipeslide.client.fullmap.routecard.hit.RouteLineCardHit;
import dev.marblegate.superpipeslide.client.fullmap.routecard.hit.ViewModeChipHit;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

public record RouteLineCardRenderResult(
        SPSGui.Rect layoutStripBounds,
        double layoutMaxScroll,
        List<LayoutChipHit> layoutChips,
        List<ViewModeChipHit> viewModeChips,
        SPSGui.Rect viewModeStripBounds,
        SPSGui.Rect stopListBounds,
        SPSGui.Rect mapBounds,
        SPSGui.Rect fitViewportBounds,
        SPSGui.Rect locateFirstBounds,
        SPSGui.Rect locateLayoutBounds,
        double stopListMaxScroll,
        RouteLineCardHit hover,
        Optional<Component> tooltipOverride
) {
    public RouteLineCardRenderResult {
        layoutChips = List.copyOf(layoutChips);
        viewModeChips = List.copyOf(viewModeChips);
        tooltipOverride = tooltipOverride == null ? Optional.empty() : tooltipOverride;
    }
}
