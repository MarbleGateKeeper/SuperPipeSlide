package dev.marblegate.superpipeslide.config;

public enum ShaderpackPipeRenderMode {
    PERFORMANCE("performance"),
    NATIVE("native");

    private final String key;

    ShaderpackPipeRenderMode(String key) {
        this.key = key;
    }

    public String translationKey() {
        return "sodium.options.superpipeslide.shaderpack_pipe_render_mode." + this.key;
    }

    public String tooltipKey() {
        return "sodium.options.superpipeslide.shaderpack_pipe_render_mode.tooltip." + this.key;
    }
}
