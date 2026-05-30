package dev.marblegate.superpipeslide.common.core.projection.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionRect;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record ProjectionComponent(
        UUID id,
        ProjectionComponentType type,
        ProjectionRect bounds,
        float rotationDegrees,
        int layer,
        ProjectionVisibleCondition visibleCondition,
        ProjectionComponentSettings settings) {

    public static final int MAX_TEXT_LENGTH = ProjectionComponentSettings.MAX_TEXT_LENGTH;
    public static final int MAX_COMPONENTS = 48;

    public static final Codec<ProjectionComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(ProjectionComponent::id),
            ProjectionComponentType.CODEC.optionalFieldOf("type", ProjectionComponentType.CUSTOM_TEXT).forGetter(ProjectionComponent::type),
            ProjectionRect.CODEC.optionalFieldOf("bounds", new ProjectionRect(0.0F, 0.0F, 0.25F, 0.12F)).forGetter(ProjectionComponent::bounds),
            Codec.FLOAT.optionalFieldOf("rotation", 0.0F).forGetter(ProjectionComponent::rotationDegrees),
            Codec.INT.optionalFieldOf("layer", 0).forGetter(ProjectionComponent::layer),
            ProjectionVisibleCondition.CODEC.optionalFieldOf("visible", ProjectionVisibleCondition.ALWAYS).forGetter(ProjectionComponent::visibleCondition),
            ProjectionComponentSettings.CODEC.optionalFieldOf("settings", ProjectionComponentSettings.Text.customText()).forGetter(ProjectionComponent::settings)).apply(instance, ProjectionComponent::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectionComponent> STREAM_CODEC = StreamCodec.of(
            ProjectionComponent::encode,
            ProjectionComponent::decode);
    public ProjectionComponent {
        id = id == null ? UUID.randomUUID() : id;
        type = type == null ? ProjectionComponentType.CUSTOM_TEXT : type;
        bounds = bounds == null ? new ProjectionRect(0.0F, 0.0F, 0.25F, 0.12F) : bounds;
        rotationDegrees = normalizeRotation(finiteOr(rotationDegrees, 0.0F));
        layer = Math.max(-1000, Math.min(1000, layer));
        visibleCondition = visibleCondition == null ? ProjectionVisibleCondition.ALWAYS : visibleCondition;
        settings = normalizeSettings(type, settings);
    }

    public static ProjectionComponent of(ProjectionComponentType type, float x, float y, float width, float height, int layer, ProjectionComponentSettings settings) {
        return new ProjectionComponent(UUID.randomUUID(), type, new ProjectionRect(x, y, width, height), 0.0F, layer, ProjectionVisibleCondition.ALWAYS, settings);
    }

    public float x() {
        return this.bounds.x();
    }

    public float y() {
        return this.bounds.y();
    }

    public float width() {
        return this.bounds.width();
    }

    public float height() {
        return this.bounds.height();
    }

    public float centerX() {
        return this.bounds.centerX();
    }

    public float centerY() {
        return this.bounds.centerY();
    }

    public String text() {
        return this.settings instanceof ProjectionComponentSettings.Text text ? text.text() : "";
    }

    public ProjectionComponent withBounds(float x, float y, float width, float height) {
        return new ProjectionComponent(this.id, this.type, this.bounds.withBounds(x, y, width, height), this.rotationDegrees, this.layer, this.visibleCondition, this.settings);
    }

    public ProjectionComponent withFreshId() {
        return new ProjectionComponent(UUID.randomUUID(), this.type, this.bounds, this.rotationDegrees, this.layer, this.visibleCondition, this.settings);
    }

    public ProjectionComponent withTransform(float centerX, float centerY, float width, float height, float rotationDegrees) {
        return new ProjectionComponent(this.id, this.type, this.bounds.withCenter(centerX, centerY, width, height), rotationDegrees, this.layer, this.visibleCondition, this.settings);
    }

    public ProjectionComponent withLayer(int layer) {
        return new ProjectionComponent(this.id, this.type, this.bounds, this.rotationDegrees, layer, this.visibleCondition, this.settings);
    }

    public ProjectionComponent withText(String text) {
        if (this.settings instanceof ProjectionComponentSettings.Text textSettings) {
            return withSettings(textSettings.withText(text));
        }
        return this;
    }

    public ProjectionComponent withSettings(ProjectionComponentSettings settings) {
        return new ProjectionComponent(this.id, this.type, this.bounds, this.rotationDegrees, this.layer, this.visibleCondition, settings);
    }

    public ProjectionComponent withVisibleCondition(ProjectionVisibleCondition condition) {
        return new ProjectionComponent(this.id, this.type, this.bounds, this.rotationDegrees, this.layer, condition, this.settings);
    }

    private static ProjectionComponentSettings normalizeSettings(ProjectionComponentType type, ProjectionComponentSettings settings) {
        if (settings == null || settings.type() != type) {
            return ProjectionComponentSettings.defaultFor(type);
        }
        return settings;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ProjectionComponent component) {
        UUIDUtil.STREAM_CODEC.encode(buffer, component.id);
        ProjectionComponentType.STREAM_CODEC.encode(buffer, component.type);
        ProjectionRect.STREAM_CODEC.encode(buffer, component.bounds);
        buffer.writeFloat(component.rotationDegrees);
        buffer.writeVarInt(component.layer);
        ProjectionVisibleCondition.STREAM_CODEC.encode(buffer, component.visibleCondition);
        component.settings.encode(buffer);
    }

    private static ProjectionComponent decode(RegistryFriendlyByteBuf buffer) {
        UUID id = UUIDUtil.STREAM_CODEC.decode(buffer);
        ProjectionComponentType type = ProjectionComponentType.STREAM_CODEC.decode(buffer);
        ProjectionRect bounds = ProjectionRect.STREAM_CODEC.decode(buffer);
        float rotation = buffer.readFloat();
        int layer = buffer.readVarInt();
        ProjectionVisibleCondition visible = ProjectionVisibleCondition.STREAM_CODEC.decode(buffer);
        ProjectionComponentSettings settings = type.decodeSettings(buffer);
        return new ProjectionComponent(id, type, bounds, rotation, layer, visible, settings);
    }

    private static float normalizeRotation(float value) {
        float normalized = value % 360.0F;
        if (normalized > 180.0F) {
            normalized -= 360.0F;
        }
        if (normalized < -180.0F) {
            normalized += 360.0F;
        }
        return normalized;
    }

    private static float finiteOr(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }
}
