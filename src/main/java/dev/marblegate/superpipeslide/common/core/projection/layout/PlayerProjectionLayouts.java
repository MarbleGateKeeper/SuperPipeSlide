package dev.marblegate.superpipeslide.common.core.projection.layout;


import dev.marblegate.superpipeslide.common.core.projection.storage.ProjectionLayoutSavedData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public record PlayerProjectionLayouts(UUID owner, List<ProjectionLayoutDefinition> layouts, Map<ProjectionLayoutTarget, UUID> selectedLayoutIds) {
    public static final int MAX_LAYOUTS_PER_PLAYER = 96;

    public static final Codec<PlayerProjectionLayouts> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(PlayerProjectionLayouts::owner),
            ProjectionLayoutDefinition.CODEC.listOf().optionalFieldOf("layouts", List.of()).forGetter(PlayerProjectionLayouts::layouts),
            Codec.unboundedMap(ProjectionLayoutTarget.CODEC, UUIDUtil.STRING_CODEC).optionalFieldOf("selected_by_target", Map.of()).forGetter(PlayerProjectionLayouts::selectedLayoutIds)
    ).apply(instance, PlayerProjectionLayouts::new));

    public PlayerProjectionLayouts {
        layouts = layouts == null ? List.of() : layouts.stream().limit(MAX_LAYOUTS_PER_PLAYER).toList();
        selectedLayoutIds = selectedLayoutIds == null ? Map.of() : Map.copyOf(selectedLayoutIds);
        Map<UUID, ProjectionLayoutDefinition> validById = layouts.stream()
                .filter(ProjectionLayoutSavedData::isValid)
                .collect(Collectors.toMap(ProjectionLayoutDefinition::id, Function.identity(), (a, b) -> a));
        java.util.EnumMap<ProjectionLayoutTarget, UUID> normalized = new java.util.EnumMap<>(ProjectionLayoutTarget.class);
        for (ProjectionLayoutTarget target : ProjectionLayoutTarget.values()) {
            UUID requested = selectedLayoutIds.get(target);
            if (requested != null) {
                ProjectionLayoutDefinition selected = validById.get(requested);
                if (selected != null && selected.target() == target) {
                    normalized.put(target, requested);
                    continue;
                }
            }
            layouts.stream()
                    .filter(ProjectionLayoutSavedData::isValid)
                    .filter(layout -> layout.target() == target)
                    .findFirst()
                    .map(ProjectionLayoutDefinition::id)
                    .ifPresent(id -> normalized.put(target, id));
        }
        selectedLayoutIds = Map.copyOf(normalized);
    }

    public Optional<UUID> selectedLayoutId(ProjectionLayoutTarget target) {
        return Optional.ofNullable(this.selectedLayoutIds.get(target == null ? ProjectionLayoutTarget.STATION_NAME : target));
    }
}
