package dev.marblegate.superpipeslide.common.core.projection.layout;


import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponent;
import dev.marblegate.superpipeslide.common.core.projection.storage.ProjectionLayoutSavedData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record AppliedProjectionLayout(
        UUID sourceLayoutId,
        String sourceLayoutName,
        int sourceSchemaVersion,
        ProjectionLayoutTarget target,
        ProjectionCanvas canvas,
        List<ProjectionComponent> components,
        String appliedBy,
        long appliedAt,
        boolean invalid,
        String errorMessage
) {
    public static final int MAX_APPLIED_BY_LENGTH = 32;
    public static final int MAX_ERROR_LENGTH = 96;

    public static final Codec<AppliedProjectionLayout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("source_layout_id").forGetter(AppliedProjectionLayout::sourceLayoutId),
            Codec.STRING.optionalFieldOf("source_layout_name", "Projection Layout").forGetter(AppliedProjectionLayout::sourceLayoutName),
            Codec.INT.optionalFieldOf("source_schema", 0).forGetter(AppliedProjectionLayout::sourceSchemaVersion),
            ProjectionLayoutTarget.CODEC.optionalFieldOf("target", ProjectionLayoutTarget.STATION_NAME).forGetter(AppliedProjectionLayout::target),
            ProjectionCanvas.CODEC.optionalFieldOf("canvas", ProjectionCanvas.standardHorizontal()).forGetter(AppliedProjectionLayout::canvas),
            ProjectionComponent.CODEC.listOf().optionalFieldOf("components", List.of()).forGetter(AppliedProjectionLayout::components),
            Codec.STRING.optionalFieldOf("applied_by", "").forGetter(AppliedProjectionLayout::appliedBy),
            Codec.LONG.optionalFieldOf("applied_at", 0L).forGetter(AppliedProjectionLayout::appliedAt),
            Codec.BOOL.optionalFieldOf("invalid", false).forGetter(AppliedProjectionLayout::invalid),
            Codec.STRING.optionalFieldOf("error", "").forGetter(AppliedProjectionLayout::errorMessage)
    ).apply(instance, AppliedProjectionLayout::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AppliedProjectionLayout> STREAM_CODEC = StreamCodec.of(
            AppliedProjectionLayout::encode,
            AppliedProjectionLayout::decode
    );

    public AppliedProjectionLayout {
        sourceLayoutId = sourceLayoutId == null ? UUID.randomUUID() : sourceLayoutId;
        sourceLayoutName = normalize(sourceLayoutName, ProjectionLayoutDefinition.MAX_NAME_LENGTH, "Projection Layout");
        sourceSchemaVersion = Math.max(0, sourceSchemaVersion);
        target = target == null ? ProjectionLayoutTarget.STATION_NAME : target;
        canvas = canvas == null ? ProjectionCanvas.standardHorizontal() : canvas;
        components = components == null ? List.of() : components.stream()
                .sorted(Comparator.comparingInt(ProjectionComponent::layer))
                .limit(ProjectionComponent.MAX_COMPONENTS)
                .toList();
        appliedBy = normalize(appliedBy, MAX_APPLIED_BY_LENGTH, "");
        appliedAt = Math.max(0L, appliedAt);
        errorMessage = normalize(errorMessage, MAX_ERROR_LENGTH, "");
        if (!invalid && (sourceSchemaVersion != ProjectionLayoutDefinition.CURRENT_SCHEMA_VERSION || components.isEmpty())) {
            invalid = true;
            errorMessage = "Layout format is outdated or incomplete";
        }
    }

    public static AppliedProjectionLayout invalid(String message) {
        return new AppliedProjectionLayout(UUID.randomUUID(), "Invalid Layout", ProjectionLayoutDefinition.CURRENT_SCHEMA_VERSION, ProjectionLayoutTarget.STATION_NAME, ProjectionCanvas.standardHorizontal(), List.of(), "", System.currentTimeMillis(), true, message);
    }

    public Optional<ProjectionLayoutDefinition> editableCopy() {
        if (this.invalid) {
            return Optional.empty();
        }
        return Optional.of(new ProjectionLayoutDefinition(UUID.randomUUID(), this.sourceLayoutName + " Copy", ProjectionLayoutDefinition.CURRENT_SCHEMA_VERSION, this.target, this.canvas, ProjectionLayoutSavedData.freshComponents(this.components), System.currentTimeMillis()));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, AppliedProjectionLayout layout) {
        UUIDUtil.STREAM_CODEC.encode(buffer, layout.sourceLayoutId);
        ByteBufCodecs.STRING_UTF8.encode(buffer, layout.sourceLayoutName);
        buffer.writeVarInt(layout.sourceSchemaVersion);
        ProjectionLayoutTarget.STREAM_CODEC.encode(buffer, layout.target);
        ProjectionCanvas.STREAM_CODEC.encode(buffer, layout.canvas);
        ProjectionComponent.STREAM_CODEC.apply(ByteBufCodecs.list(ProjectionComponent.MAX_COMPONENTS)).encode(buffer, layout.components);
        ByteBufCodecs.STRING_UTF8.encode(buffer, layout.appliedBy);
        buffer.writeLong(layout.appliedAt);
        buffer.writeBoolean(layout.invalid);
        ByteBufCodecs.STRING_UTF8.encode(buffer, layout.errorMessage);
    }

    private static AppliedProjectionLayout decode(RegistryFriendlyByteBuf buffer) {
        return new AppliedProjectionLayout(
                UUIDUtil.STREAM_CODEC.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                buffer.readVarInt(),
                ProjectionLayoutTarget.STREAM_CODEC.decode(buffer),
                ProjectionCanvas.STREAM_CODEC.decode(buffer),
                ProjectionComponent.STREAM_CODEC.apply(ByteBufCodecs.list(ProjectionComponent.MAX_COMPONENTS)).decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                buffer.readLong(),
                buffer.readBoolean(),
                ByteBufCodecs.STRING_UTF8.decode(buffer)
        );
    }

    private static String normalize(String value, int maxLength, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
