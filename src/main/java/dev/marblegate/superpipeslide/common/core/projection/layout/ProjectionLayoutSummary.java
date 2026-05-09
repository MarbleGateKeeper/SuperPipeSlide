package dev.marblegate.superpipeslide.common.core.projection.layout;


import dev.marblegate.superpipeslide.common.core.projection.template.ProjectionTemplates;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record ProjectionLayoutSummary(
        UUID id,
        String name,
        ProjectionLayoutTarget target,
        ProjectionCanvas canvas,
        long updatedAt,
        boolean invalid,
        String errorMessage,
        ProjectionLayoutDefinition preview
) {
    public static final int MAX_ERROR_LENGTH = 96;

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectionLayoutSummary> STREAM_CODEC = StreamCodec.of(
            ProjectionLayoutSummary::encode,
            ProjectionLayoutSummary::decode
    );

    public ProjectionLayoutSummary {
        name = name == null || name.isBlank() ? "Projection Layout" : name.trim();
        if (name.length() > ProjectionLayoutDefinition.MAX_NAME_LENGTH) {
            name = name.substring(0, ProjectionLayoutDefinition.MAX_NAME_LENGTH);
        }
        target = target == null ? ProjectionLayoutTarget.STATION_NAME : target;
        canvas = canvas == null ? ProjectionCanvas.standardHorizontal() : canvas;
        errorMessage = errorMessage == null ? "" : errorMessage.trim();
        if (errorMessage.length() > MAX_ERROR_LENGTH) {
            errorMessage = errorMessage.substring(0, MAX_ERROR_LENGTH);
        }
        if (invalid) {
            preview = null;
        } else if (preview == null) {
            preview = ProjectionTemplates.defaultLayout(target);
        }
    }

    public static ProjectionLayoutSummary of(ProjectionLayoutDefinition layout) {
        return new ProjectionLayoutSummary(layout.id(), layout.name(), layout.target(), layout.canvas(), layout.updatedAt(), false, "", layout);
    }

    public static ProjectionLayoutSummary invalid(UUID id, String name, String message) {
        return invalid(id, name, ProjectionLayoutTarget.STATION_NAME, message);
    }

    public static ProjectionLayoutSummary invalid(UUID id, String name, ProjectionLayoutTarget target, String message) {
        return new ProjectionLayoutSummary(id, name, target, ProjectionCanvas.standardHorizontal(), 0L, true, message, null);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ProjectionLayoutSummary summary) {
        UUIDUtil.STREAM_CODEC.encode(buffer, summary.id);
        ByteBufCodecs.STRING_UTF8.encode(buffer, summary.name);
        ProjectionLayoutTarget.STREAM_CODEC.encode(buffer, summary.target);
        ProjectionCanvas.STREAM_CODEC.encode(buffer, summary.canvas);
        buffer.writeLong(summary.updatedAt);
        buffer.writeBoolean(summary.invalid);
        ByteBufCodecs.STRING_UTF8.encode(buffer, summary.errorMessage);
        if (!summary.invalid) {
            ProjectionLayoutDefinition.STREAM_CODEC.encode(buffer, summary.preview);
        }
    }

    private static ProjectionLayoutSummary decode(RegistryFriendlyByteBuf buffer) {
        UUID id = UUIDUtil.STREAM_CODEC.decode(buffer);
        String name = ByteBufCodecs.STRING_UTF8.decode(buffer);
        ProjectionLayoutTarget target = ProjectionLayoutTarget.STREAM_CODEC.decode(buffer);
        ProjectionCanvas canvas = ProjectionCanvas.STREAM_CODEC.decode(buffer);
        long updatedAt = buffer.readLong();
        boolean invalid = buffer.readBoolean();
        String error = ByteBufCodecs.STRING_UTF8.decode(buffer);
        ProjectionLayoutDefinition preview = invalid ? null : ProjectionLayoutDefinition.STREAM_CODEC.decode(buffer);
        return new ProjectionLayoutSummary(id, name, target, canvas, updatedAt, invalid, error, preview);
    }
}
