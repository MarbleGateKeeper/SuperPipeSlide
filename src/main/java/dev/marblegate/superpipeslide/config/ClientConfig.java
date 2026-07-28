package dev.marblegate.superpipeslide.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<ShaderpackPipeRenderMode> SHADERPACK_PIPE_RENDER_MODE = BUILDER
            .comment("Controls how pipes are rendered when a shader pack integration is active. PERFORMANCE uses the optimized instanced renderer and is the default. NATIVE uses the integration's native submit path and usually matches shader pack effects more closely, but may cost more performance.")
            .defineEnum("shaderpackPipeRenderMode", ShaderpackPipeRenderMode.PERFORMANCE);

    public static final ModConfigSpec.BooleanValue ENABLE_SLIDE_CAMERA_FEEDBACK = BUILDER
            .comment("Whether sliding may adjust camera pitch and roll for slope, turning, and upcoming sharp-turn anticipation. Disable this to keep sliding mechanics and visual effects while removing camera tilt feedback.")
            .define("enableSlideCameraFeedback", true);

    public static final ModConfigSpec.BooleanValue ENABLE_CINEMATIC_CAMERA = BUILDER
            .comment("Whether sliding may switch to the cinematic perspective: the camera parks at scenic vantage points and cuts between wallpaper-like shots as you slide through the frame. Disable to always keep the normal first-person view.")
            .define("enableCinematicCamera", false);

    public static final ModConfigSpec.DoubleValue CINEMATIC_CAMERA_INTENSITY = BUILDER
            .comment("Strength of the cinematic perspective blend. 0.0 disables it without turning the feature off, 1.0 is the full shot.")
            .defineInRange("cinematicCameraIntensity", 0.75D, 0.0D, 1.0D);

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

    public static final ModConfigSpec.BooleanValue FULL_MAP_DEBUG_OVERLAY = BUILDER
            .comment("Whether the full route map screen draws a debug overlay line with the active dimension's schematic solver quality summary (solve time, node overlaps, edge crossings, label overlaps, layout profile). Intended for layout tuning and development.")
            .define("fullMapDebugOverlay", false);

    public static final ModConfigSpec.BooleanValue ENABLE_ROUTE_HUD = BUILDER
            .comment("Whether the route HUD (the horizontal line strip shown at the top of the screen while sliding) is visible.")
            .define("enableRouteHud", true);

    public static final ModConfigSpec.BooleanValue ENABLE_NAVIGATION_HUD = BUILDER
            .comment("Whether the navigation HUD (the vertical progress rail, info card, and target indicator shown during navigation) is visible.")
            .define("enableNavigationHud", true);

    public static final ModConfigSpec.ConfigValue<String> FULL_ROUTE_MAP_DEFAULT_LAYOUT_MODE = BUILDER
            .comment("Layout mode the full route map opens with, and the mode restored on game restart. One of PHYSICAL, GEOGRAPHIC, PRACTICAL, SCHEMATIC. PHYSICAL shows real track geometry, GEOGRAPHIC and PRACTICAL are relaxed geographic layouts, SCHEMATIC is the pure metro-style line diagram. Stored as a string so this common config class never references client-only classes.")
            .define("fullRouteMapDefaultLayoutMode", "PRACTICAL", value -> value instanceof String name && java.util.Set.of("PHYSICAL", "GEOGRAPHIC", "PRACTICAL", "SCHEMATIC").contains(name));

    public static final ModConfigSpec.DoubleValue FULL_ROUTE_MAP_ZOOM_SENSITIVITY = BUILDER
            .comment("Mouse wheel zoom sensitivity of the full route map. 1.0 is the default step, lower values zoom in smaller steps, higher values in larger steps.")
            .defineInRange("fullRouteMapZoomSensitivity", 1.0D, 0.5D, 2.0D);

    public static final ModConfigSpec.DoubleValue HUD_ANIMATION_SCALE = BUILDER
            .comment("Speed multiplier for HUD animations (route HUD and navigation HUD pulses, flows, and transitions). 1.0 is full speed, 0.0 freezes all HUD animation.")
            .defineInRange("hudAnimationScale", 1.0D, 0.0D, 1.0D);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static void save() {
        SPEC.save();
    }

    private ClientConfig() {}
}
