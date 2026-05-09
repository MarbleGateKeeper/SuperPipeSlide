package dev.marblegate.superpipeslide.common.core.projection.layout;


import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponent;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record ProjectionLayoutDefinition(
        UUID id,
        String name,
        int schemaVersion,
        ProjectionLayoutTarget target,
        ProjectionCanvas canvas,
        List<ProjectionComponent> components,
        long updatedAt
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_NAME_LENGTH = 48;

    public static final Codec<ProjectionLayoutDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(ProjectionLayoutDefinition::id),
            Codec.STRING.optionalFieldOf("name", "Projection Layout").forGetter(ProjectionLayoutDefinition::name),
            Codec.INT.optionalFieldOf("schema", 0).forGetter(ProjectionLayoutDefinition::schemaVersion),
            ProjectionLayoutTarget.CODEC.optionalFieldOf("target", ProjectionLayoutTarget.STATION_NAME).forGetter(ProjectionLayoutDefinition::target),
            ProjectionCanvas.CODEC.optionalFieldOf("canvas", ProjectionCanvas.standardHorizontal()).forGetter(ProjectionLayoutDefinition::canvas),
            ProjectionComponent.CODEC.listOf().optionalFieldOf("components", List.of()).forGetter(ProjectionLayoutDefinition::components),
            Codec.LONG.optionalFieldOf("updated_at", 0L).forGetter(ProjectionLayoutDefinition::updatedAt)
    ).apply(instance, ProjectionLayoutDefinition::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectionLayoutDefinition> STREAM_CODEC = StreamCodec.of(
            ProjectionLayoutDefinition::encode,
            ProjectionLayoutDefinition::decode
    );

    public ProjectionLayoutDefinition {
        id = id == null ? UUID.randomUUID() : id;
        name = normalizeName(name);
        schemaVersion = Math.max(0, schemaVersion);
        target = target == null ? ProjectionLayoutTarget.STATION_NAME : target;
        canvas = canvas == null ? ProjectionCanvas.standardHorizontal() : canvas;
        components = components == null ? List.of() : components.stream()
                .sorted(Comparator.comparingInt(ProjectionComponent::layer))
                .limit(ProjectionComponent.MAX_COMPONENTS)
                .toList();
        updatedAt = Math.max(0L, updatedAt);
    }

    public static ProjectionLayoutDefinition create(String name, ProjectionCanvas canvas, List<ProjectionComponent> components) {
        return new ProjectionLayoutDefinition(UUID.randomUUID(), name, CURRENT_SCHEMA_VERSION, ProjectionLayoutTarget.STATION_NAME, canvas, components, System.currentTimeMillis());
    }

    public static ProjectionLayoutDefinition create(String name, ProjectionLayoutTarget target, ProjectionCanvas canvas, List<ProjectionComponent> components) {
        return new ProjectionLayoutDefinition(UUID.randomUUID(), name, CURRENT_SCHEMA_VERSION, target, canvas, components, System.currentTimeMillis());
    }

    public ProjectionLayoutDefinition renamed(String name) {
        return new ProjectionLayoutDefinition(this.id, name, this.schemaVersion, this.target, this.canvas, this.components, System.currentTimeMillis());
    }

    public ProjectionLayoutDefinition withCanvas(ProjectionCanvas canvas) {
        return new ProjectionLayoutDefinition(this.id, this.name, this.schemaVersion, this.target, canvas, this.components, System.currentTimeMillis());
    }

    public ProjectionLayoutDefinition withComponents(List<ProjectionComponent> components) {
        return new ProjectionLayoutDefinition(this.id, this.name, this.schemaVersion, this.target, this.canvas, components, System.currentTimeMillis());
    }

    public AppliedProjectionLayout compile(String appliedBy) {
        return new AppliedProjectionLayout(this.id, this.name, this.schemaVersion, this.target, this.canvas, this.components, normalizeAppliedBy(appliedBy), System.currentTimeMillis(), false, "");
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ProjectionLayoutDefinition definition) {
        UUIDUtil.STREAM_CODEC.encode(buffer, definition.id);
        ByteBufCodecs.STRING_UTF8.encode(buffer, definition.name);
        buffer.writeVarInt(definition.schemaVersion);
        ProjectionLayoutTarget.STREAM_CODEC.encode(buffer, definition.target);
        ProjectionCanvas.STREAM_CODEC.encode(buffer, definition.canvas);
        ProjectionComponent.STREAM_CODEC.apply(ByteBufCodecs.list(ProjectionComponent.MAX_COMPONENTS)).encode(buffer, definition.components);
        buffer.writeLong(definition.updatedAt);
    }

    private static ProjectionLayoutDefinition decode(RegistryFriendlyByteBuf buffer) {
        return new ProjectionLayoutDefinition(
                UUIDUtil.STREAM_CODEC.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                buffer.readVarInt(),
                ProjectionLayoutTarget.STREAM_CODEC.decode(buffer),
                ProjectionCanvas.STREAM_CODEC.decode(buffer),
                ProjectionComponent.STREAM_CODEC.apply(ByteBufCodecs.list(ProjectionComponent.MAX_COMPONENTS)).decode(buffer),
                buffer.readLong()
        );
    }

    private static String normalizeName(String name) {
        String normalized = name == null || name.isBlank() ? "Projection Layout" : name.trim();
        return normalized.length() <= MAX_NAME_LENGTH ? normalized : normalized.substring(0, MAX_NAME_LENGTH);
    }

    private static String normalizeAppliedBy(String appliedBy) {
        String normalized = appliedBy == null ? "" : appliedBy.trim();
        return normalized.length() <= 32 ? normalized : normalized.substring(0, 32);
    }
}
