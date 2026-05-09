package dev.marblegate.superpipeslide.common.core.gaze;

public record GazeChoiceExpireCondition(GazeChoiceExpireType type, int timeoutTicks) {
    public GazeChoiceExpireCondition {
        if (type == null) {
            throw new IllegalArgumentException("Gaze choice expire type is required");
        }
        if (timeoutTicks < 0) {
            throw new IllegalArgumentException("Gaze choice timeout ticks cannot be negative");
        }
    }
}
