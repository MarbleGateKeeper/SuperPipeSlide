package dev.marblegate.superpipeslide.common.core.gaze;

import net.minecraft.world.phys.Vec3;


public record GazeChoicePlacement(GazeChoicePlacementType type, Vec3 anchor, Vec3 forward, Vec3 up, Vec3 localOffset) {
    public GazeChoicePlacement {
    }

    public static GazeChoicePlacement worldFrame(Vec3 anchor, Vec3 forward, Vec3 up, Vec3 localOffset) {
        return new GazeChoicePlacement(GazeChoicePlacementType.WORLD_FRAME, anchor, forward, up, localOffset);
    }

    public static GazeChoicePlacement slideFrame(Vec3 localOffset) {
        return new GazeChoicePlacement(GazeChoicePlacementType.SLIDE_FRAME, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, localOffset);
    }
}
