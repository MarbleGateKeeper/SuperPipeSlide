package dev.marblegate.superpipeslide.client.fullmap.config;

public enum FullRouteMapLayoutMode {
    PHYSICAL("screen.superpipeslide.full_map.layout_mode.physical"),
    GEOGRAPHIC("screen.superpipeslide.full_map.layout_mode.geographic"),
    PRACTICAL("screen.superpipeslide.full_map.layout_mode.practical"),
    SCHEMATIC("screen.superpipeslide.full_map.layout_mode.schematic");

    private final String translationKey;

    FullRouteMapLayoutMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return this.translationKey;
    }

    public boolean physical() {
        return this == PHYSICAL;
    }

    public boolean schematic() {
        return this != PHYSICAL;
    }

    public FullRouteMapLayoutMode next() {
        FullRouteMapLayoutMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
