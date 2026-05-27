package dev.marblegate.superpipeslide.integration;

import net.neoforged.fml.ModList;

public enum ModIntegration {
    IRIS(Constants.IRIS);

    private final String id;

    ModIntegration(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public boolean enabled() {
        return ModList.get().isLoaded(this.id);
    }

    public static class Constants {
        public static final String IRIS = "iris";
    }
}
