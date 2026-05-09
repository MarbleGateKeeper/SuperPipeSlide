package dev.marblegate.superpipeslide.common.core.gaze;

import net.minecraft.world.phys.Vec3;


public record GazeChoiceShape(GazeChoiceShapeType type, Vec3 arrowDirectionLocal) {
    public GazeChoiceShape {
        if (type == GazeChoiceShapeType.ARROW && arrowDirectionLocal.lengthSqr() < 1.0E-6D) {
            throw new IllegalArgumentException("Arrow Gaze Choice shape requires a non-zero local direction");
        }
    }

    public static GazeChoiceShape circle() {
        return new GazeChoiceShape(GazeChoiceShapeType.CIRCLE, Vec3.ZERO);
    }

    public static GazeChoiceShape arrow(Vec3 arrowDirectionLocal) {
        return new GazeChoiceShape(GazeChoiceShapeType.ARROW, arrowDirectionLocal);
    }
}
