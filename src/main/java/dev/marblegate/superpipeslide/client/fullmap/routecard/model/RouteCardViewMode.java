package dev.marblegate.superpipeslide.client.fullmap.routecard.model;

public enum RouteCardViewMode {
    PHYSICAL("\u771f\u5b9e"),
    PRACTICAL("\u5b9e\u7528"),
    SCHEMATIC("\u793a\u610f");

    private final String label;

    RouteCardViewMode(String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }
}
