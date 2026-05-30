package dev.marblegate.superpipeslide.common.core.appearance.style;

import dev.marblegate.superpipeslide.common.core.appearance.material.MaterialSlotDefinition;
import java.util.List;

public record PipeVariantDefinition(
        String id,
        String styleId,
        String nameKey,
        String subtitleKey,
        List<MaterialSlotDefinition> slots,
        double sizeMultiplier,
        float lineWidthMultiplier,
        boolean splitCoating,
        boolean reinforced,
        boolean extraRibs,
        boolean curved) {
    public PipeVariantDefinition {
        slots = List.copyOf(slots);
    }
}
