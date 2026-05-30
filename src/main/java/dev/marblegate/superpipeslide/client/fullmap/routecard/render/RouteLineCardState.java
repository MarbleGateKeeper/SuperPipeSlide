package dev.marblegate.superpipeslide.client.fullmap.routecard.render;

import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardViewMode;
import dev.marblegate.superpipeslide.client.fullmap.routecard.model.RouteCardViewport;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record RouteLineCardState(
        UUID routeLineId,
        Optional<UUID> selectedLayoutId,
        Optional<ResourceKey<Level>> sourceLevelKey,
        RouteCardViewMode viewMode,
        Map<RouteCardViewMode, RouteCardViewport> viewports) {
    public RouteLineCardState {
        selectedLayoutId = selectedLayoutId == null ? Optional.empty() : selectedLayoutId;
        sourceLevelKey = sourceLevelKey == null ? Optional.empty() : sourceLevelKey;
        viewMode = viewMode == null ? RouteCardViewMode.PRACTICAL : viewMode;
        EnumMap<RouteCardViewMode, RouteCardViewport> copy = new EnumMap<>(RouteCardViewMode.class);
        if (viewports != null) {
            copy.putAll(viewports);
        }
        viewports = Map.copyOf(copy);
    }

    public static RouteLineCardState create(UUID routeLineId, Optional<UUID> selectedLayoutId, Optional<ResourceKey<Level>> sourceLevelKey) {
        return new RouteLineCardState(routeLineId, selectedLayoutId, sourceLevelKey, RouteCardViewMode.PRACTICAL, Map.of());
    }

    public RouteLineCardState withSelectedLayout(UUID layoutId) {
        return new RouteLineCardState(this.routeLineId, Optional.of(layoutId), this.sourceLevelKey, this.viewMode, Map.of());
    }

    public RouteLineCardState withViewMode(RouteCardViewMode viewMode) {
        return new RouteLineCardState(this.routeLineId, this.selectedLayoutId, this.sourceLevelKey, viewMode, this.viewports);
    }

    public RouteLineCardState withViewport(RouteCardViewport viewport) {
        EnumMap<RouteCardViewMode, RouteCardViewport> next = new EnumMap<>(RouteCardViewMode.class);
        next.putAll(this.viewports);
        next.put(this.viewMode, viewport);
        return new RouteLineCardState(this.routeLineId, this.selectedLayoutId, this.sourceLevelKey, this.viewMode, next);
    }

    public RouteLineCardState fitViewport() {
        EnumMap<RouteCardViewMode, RouteCardViewport> next = new EnumMap<>(RouteCardViewMode.class);
        next.putAll(this.viewports);
        next.remove(this.viewMode);
        return new RouteLineCardState(this.routeLineId, this.selectedLayoutId, this.sourceLevelKey, this.viewMode, next);
    }

    public Optional<RouteCardViewport> viewport() {
        return Optional.ofNullable(this.viewports.get(this.viewMode));
    }
}
