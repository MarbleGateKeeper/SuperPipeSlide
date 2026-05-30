package dev.marblegate.superpipeslide.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_SLIDE_CAMERA_FEEDBACK = BUILDER
            .comment("Whether sliding may adjust camera pitch and roll for slope, turning, and upcoming sharp-turn anticipation. Disable this to keep sliding mechanics and visual effects while removing camera tilt feedback.")
            .define("enableSlideCameraFeedback", true);

    public static final ModConfigSpec.BooleanValue REDUCE_MOTION_SICKNESS_RISK = BUILDER
            .comment("Reduces 3D motion sickness risk during pipe sliding by disabling camera roll, FOV pushes, fold traversal screen distortion, and other strong first-person motion feedback. Sliding mechanics are unchanged.")
            .define("reduceMotionSicknessRisk", false);

    public static final ModConfigSpec.BooleanValue REDUCE_PHOTOSENSITIVITY_RISK = BUILDER
            .comment("Reduces photosensitivity risk by removing pulsing marker brightness, full-bright pipe glow, slide streak particles, fold traversal flashes, and high-emphasis navigation highlights.")
            .define("reducePhotosensitivityRisk", false);

    public static final ModConfigSpec.BooleanValue ENABLE_PROJECTION_NETWORK_IMAGES = BUILDER
            .comment("Whether projection layouts may load external image URLs on this client. Images are downloaded asynchronously and never by the server.")
            .define("enableProjectionNetworkImages", true);

    public static final ModConfigSpec.BooleanValue ALLOW_HTTP_PROJECTION_NETWORK_IMAGES = BUILDER
            .comment("Whether projection network images may use plain http:// URLs. HTTPS remains allowed when network images are enabled.")
            .define("allowHttpProjectionNetworkImages", false);

    public static final ModConfigSpec.IntValue PROJECTION_NETWORK_IMAGE_CACHE_SIZE = BUILDER
            .comment("Maximum number of decoded projection network images kept by this client.")
            .defineInRange("projectionNetworkImageCacheSize", 64, 8, 256);

    public static final ModConfigSpec.IntValue PROJECTION_NETWORK_IMAGE_MAX_BYTES = BUILDER
            .comment("Maximum downloaded byte size for one projection network image.")
            .defineInRange("projectionNetworkImageMaxBytes", 4 * 1024 * 1024, 256 * 1024, 16 * 1024 * 1024);

    public static final ModConfigSpec.IntValue PROJECTION_NETWORK_IMAGE_MAX_PIXELS = BUILDER
            .comment("Maximum decoded pixel count for one projection network image.")
            .defineInRange("projectionNetworkImageMaxPixels", 2048 * 2048, 64 * 64, 4096 * 4096);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {}
}
