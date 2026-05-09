package dev.marblegate.superpipeslide.common.core.gaze;

import java.util.List;
import java.util.UUID;

public record GazeChoiceSession(
        UUID sessionId,
        UUID slideSessionId,
        UUID playerId,
        GazeChoiceSource source,
        List<GazeChoice> choices,
        UUID defaultChoiceId,
        int requiredFocusTicks,
        GazeChoiceExpireCondition expireCondition
) {
    public static final int MAX_CHOICES = 16;

    public GazeChoiceSession {
        if (source == null) {
            throw new IllegalArgumentException("Gaze choice source is required");
        }
        choices = List.copyOf(choices);
        if (choices.isEmpty() || choices.size() > MAX_CHOICES) {
            throw new IllegalArgumentException("Gaze choice session must have 1-" + MAX_CHOICES + " choices");
        }
        if (choices.stream().noneMatch(choice -> choice.id().equals(defaultChoiceId))) {
            throw new IllegalArgumentException("Default choice must belong to the session");
        }
        if (requiredFocusTicks <= 0) {
            throw new IllegalArgumentException("Required focus ticks must be positive");
        }
    }
}
