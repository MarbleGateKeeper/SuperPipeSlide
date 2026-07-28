package dev.marblegate.superpipeslide.client.fullmap.routecard.model;

public enum RouteCardViewMode {
    PHYSICAL("screen.superpipeslide.full_map.route_card.view_mode.physical"),
    PRACTICAL("screen.superpipeslide.full_map.route_card.view_mode.practical"),
    SCHEMATIC("screen.superpipeslide.full_map.route_card.view_mode.schematic");

    private final String translationKey;

    RouteCardViewMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return this.translationKey;
    }
}
