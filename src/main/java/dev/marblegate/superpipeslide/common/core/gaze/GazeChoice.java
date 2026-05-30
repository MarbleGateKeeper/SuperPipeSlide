package dev.marblegate.superpipeslide.common.core.gaze;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;

public record GazeChoice(
        UUID id,
        GazeChoicePlacement placement,
        GazeChoiceShape shape,
        Component label,
        Component detail,
        List<Integer> colors,
        boolean recommended,
        double requiredLookPrecision) {

    public static final int MAX_LABEL_LENGTH = 64;
    public static final int MAX_DETAIL_LENGTH = 64;
    public static final int MAX_COLORS = 3;
    public GazeChoice {
        label = label == null ? Component.empty() : label;
        detail = detail == null ? Component.empty() : detail;
        colors = List.copyOf((colors == null ? List.<Integer>of() : colors).stream().limit(MAX_COLORS).toList());
        if (colors.isEmpty()) {
            colors = List.of(0xFFD8F4FF);
        }
        if (label.getString().length() > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException("Gaze choice label is too long");
        }
        if (detail.getString().length() > MAX_DETAIL_LENGTH) {
            throw new IllegalArgumentException("Gaze choice detail is too long");
        }
        if (!Double.isFinite(requiredLookPrecision) || requiredLookPrecision < -1.0D || requiredLookPrecision > 1.0D) {
            throw new IllegalArgumentException("Gaze choice required look precision must be a finite dot product");
        }
    }

    public int primaryColor() {
        return this.colors.getFirst();
    }
}
