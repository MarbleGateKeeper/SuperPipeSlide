package dev.marblegate.superpipeslide.common.core.appearance.style;


import dev.marblegate.superpipeslide.common.core.appearance.material.MaterialSlotDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record PipeStyleDefinition(
        String id,
        String nameKey,
        String subtitleKey,
        String category,
        PipeStyleShape shape,
        String defaultVariantId,
        List<MaterialSlotDefinition> slots,
        List<PipeStyleParameterDefinition> parameters,
        float lineWidth,
        int ringInterval,
        boolean openTop
) {
    public PipeStyleDefinition {
        slots = List.copyOf(slots);
        parameters = List.copyOf(parameters);
    }

    public MaterialSlotDefinition primarySlot() {
        return this.slots.isEmpty() ? new MaterialSlotDefinition("body", "pipe_appearance.superpipeslide.slot.body") : this.slots.getFirst();
    }

    public Optional<PipeStyleParameterDefinition> parameter(String id) {
        return this.parameters.stream()
                .filter(parameter -> parameter.id().equals(id))
                .findFirst();
    }

    public double parameterValue(Map<String, Double> values, String id) {
        return this.parameter(id)
                .map(parameter -> parameter.clamp(values == null ? parameter.defaultValue() : values.getOrDefault(id, parameter.defaultValue())))
                .orElse(0.0D);
    }

    public Map<String, Double> normalizeParameters(Map<String, Double> values) {
        Map<String, Double> normalized = new LinkedHashMap<>();
        for (PipeStyleParameterDefinition parameter : this.parameters) {
            normalized.put(parameter.id(), parameter.clamp(values == null ? parameter.defaultValue() : values.getOrDefault(parameter.id(), parameter.defaultValue())));
        }
        return Map.copyOf(normalized);
    }
}
