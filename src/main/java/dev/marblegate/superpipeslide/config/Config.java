package dev.marblegate.superpipeslide.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue MAX_CONNECTION_LENGTH = BUILDER
            .comment("Maximum length, in blocks, for any pipe connection after curve solving.")
            .defineInRange("maxConnectionLength", 64.0D, 2.0D, 512.0D);

    public static final ModConfigSpec.DoubleValue CAPTURE_RADIUS = BUILDER
            .comment("Maximum distance from the straight pipe centerline for a player to be captured into sliding.")
            .defineInRange("captureRadius", 0.72D, 0.25D, 2.0D);

    public static final ModConfigSpec.DoubleValue CAPTURE_VERTICAL_TOLERANCE = BUILDER
            .comment("Maximum vertical foot-position difference from the pipe surface for automatic capture.")
            .defineInRange("captureVerticalTolerance", 1.15D, 0.25D, 3.0D);

    public static final ModConfigSpec.DoubleValue INITIAL_SLIDE_SPEED = BUILDER
            .comment("Initial sliding speed, in blocks per tick, when captured with little forward motion.")
            .defineInRange("initialSlideSpeed", 0.18D, 0.03D, 1.25D);

    public static final ModConfigSpec.DoubleValue NORMAL_MAX_SPEED = BUILDER
            .comment("Normal pipe maximum sliding speed in blocks per tick.")
            .defineInRange("normalMaxSpeed", 0.90D, 0.05D, 3.0D);

    public static final ModConfigSpec.DoubleValue NORMAL_ACCELERATION = BUILDER
            .comment("Normal pipe acceleration toward its maximum speed, in blocks per tick per tick.")
            .defineInRange("normalAcceleration", 0.015D, 0.0D, 0.5D);

    public static final ModConfigSpec.DoubleValue NORMAL_OVERSPEED_DECELERATION = BUILDER
            .comment("Deceleration used by normal pipe connections when current speed is above the normal pipe maximum.")
            .defineInRange("normalOverspeedDeceleration", 0.12D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue ACCELERATION_ATTRIBUTE_ACCELERATION = BUILDER
            .comment("Acceleration used by pipe connections with the acceleration attribute.")
            .defineInRange("accelerationAttributeAcceleration", 0.14D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue HIGHWAY_MAX_SPEED = BUILDER
            .comment("Maximum sliding speed for pipe connections with the highway attribute.")
            .defineInRange("highwayMaxSpeed", 4.80D, 0.05D, 12.0D);

    public static final ModConfigSpec.DoubleValue HIGHWAY_ACCELERATION = BUILDER
            .comment("Default acceleration for highway pipe connections without the acceleration attribute.")
            .defineInRange("highwayAcceleration", 0.08D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue HIGHWAY_OVERSPEED_DECELERATION = BUILDER
            .comment("Deceleration used by highway pipe connections when current speed is above the highway pipe maximum.")
            .defineInRange("highwayOverspeedDeceleration", 0.03D, 0.0D, 0.5D);

    public static final ModConfigSpec.DoubleValue BRANCH_CHOICE_MAX_SPEED = BUILDER
            .comment("Forced maximum sliding speed while approaching an unresolved branch choice.")
            .defineInRange("branchChoiceMaxSpeed", 0.22D, 0.03D, 2.0D);

    public static final ModConfigSpec.DoubleValue BRANCH_CHOICE_PREVIEW_DISTANCE = BUILDER
            .comment("Distance before a branch node where Gaze Choice opens and branch choice slow-down begins.")
            .defineInRange("branchChoicePreviewDistance", 24.0D, 4.0D, 96.0D);

    public static final ModConfigSpec.DoubleValue BRANCH_CHOICE_DECELERATION = BUILDER
            .comment("Forced deceleration while approaching an unresolved branch choice.")
            .defineInRange("branchChoiceDeceleration", 12.0D, 0.0D, 24.0D);

    public static final ModConfigSpec.DoubleValue PLATFORM_CLAIM_RADIUS = BUILDER
            .comment("Maximum distance, in blocks, from a station block where its Platform Claimer can claim pipe connections.")
            .defineInRange("platformClaimRadius", 64.0D, 8.0D, 512.0D);

    public static final ModConfigSpec.DoubleValue AUTO_OUT_OF_STATION_TRANSFER_RADIUS = BUILDER
            .comment("When a new station is placed, existing stations within this same-dimension radius are automatically configured as out-of-station transfer stations.")
            .defineInRange("autoOutOfStationTransferRadius", 64.0D, 0.0D, 512.0D);

    public static final ModConfigSpec.DoubleValue FAR_OUT_OF_STATION_TRANSFER_WARNING_DISTANCE = BUILDER
            .comment("Same-dimension station transfer links farther than this distance require an explicit confirmation in the station editor.")
            .defineInRange("farOutOfStationTransferWarningDistance", 128.0D, 16.0D, 4096.0D);

    public static final ModConfigSpec.IntValue ROUTE_PATHFINDER_MAX_VISITED_NODES = BUILDER
            .comment("Maximum graph nodes visited by one route section pathfinding attempt before the section is marked incomplete.")
            .defineInRange("routePathfinderMaxVisitedNodes", 16384, 1024, 262144);

    public static final ModConfigSpec.DoubleValue STATION_SLOW_MAX_SPEED = BUILDER
            .comment("Forced maximum speed while a player is in StationSlow.")
            .defineInRange("stationSlowMaxSpeed", 0.18D, 0.03D, 2.0D);

    public static final ModConfigSpec.DoubleValue STATION_SLOW_DECELERATION = BUILDER
            .comment("Forced deceleration while entering or staying in StationSlow.")
            .defineInRange("stationSlowDeceleration", 0.45D, 0.0D, 4.0D);

    public static final ModConfigSpec.DoubleValue MAX_STEP_DISTANCE = BUILDER
            .comment("Maximum slide distance, in blocks, that can be consumed in one server tick before segment handoff checks.")
            .defineInRange("maxStepDistance", 6.0D, 0.05D, 16.0D);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
