package dev.marblegate.superpipeslide.client.fullmap.ui;

import java.util.List;

public record DisplayNameStack(String primary, String secondary, List<String> aliases) {
    public DisplayNameStack {
        primary = primary == null || primary.isBlank() ? "?" : primary.trim();
        secondary = secondary == null ? "" : secondary.trim();
        String safePrimary = primary;
        String safeSecondary = secondary;
        aliases = aliases == null ? List.of() : aliases.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .filter(name -> !name.equalsIgnoreCase(safePrimary))
                .filter(name -> safeSecondary.isBlank() || !name.equalsIgnoreCase(safeSecondary))
                .distinct()
                .toList();
    }

    public static DisplayNameStack of(String primary) {
        return new DisplayNameStack(primary, "", List.of());
    }

    public static DisplayNameStack of(String primary, List<String> translatedNames) {
        List<String> safe = translatedNames == null
                ? List.of()
                : translatedNames.stream()
                        .filter(name -> name != null && !name.isBlank())
                        .map(String::trim)
                        .filter(name -> !name.equalsIgnoreCase(primary == null ? "" : primary.trim()))
                        .distinct()
                        .toList();
        String secondary = safe.isEmpty() ? "" : String.join(" · ", safe);
        return new DisplayNameStack(primary, secondary, List.of());
    }

    public boolean hasSecondary() {
        return !this.secondary.isBlank();
    }

    public boolean hasAliases() {
        return !this.aliases.isEmpty();
    }

    public DisplayNameStack withoutSecondary() {
        if (!this.hasSecondary()) {
            return this;
        }
        return new DisplayNameStack(this.primary, "", this.aliases);
    }

    public String flat() {
        if (!this.hasSecondary() && this.aliases.isEmpty()) {
            return this.primary;
        }
        StringBuilder value = new StringBuilder(this.primary);
        if (this.hasSecondary()) {
            value.append(" / ").append(this.secondary);
        }
        for (String alias : this.aliases) {
            value.append(" / ").append(alias);
        }
        return value.toString();
    }

    public String searchText() {
        if (!this.hasSecondary() && this.aliases.isEmpty()) {
            return this.primary;
        }
        return this.primary + " " + this.secondary + " " + String.join(" ", this.aliases);
    }
}
