package dev.marblegate.superpipeslide.common.core.projection.layout;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record ProjectionRect(float x, float y, float width, float height) {

    public static final float MAX_EXTENT = 64.0F;

    public static final Codec<ProjectionRect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("x", 0.0F).forGetter(ProjectionRect::x),
            Codec.FLOAT.optionalFieldOf("y", 0.0F).forGetter(ProjectionRect::y),
            Codec.FLOAT.optionalFieldOf("width", 0.25F).forGetter(ProjectionRect::width),
            Codec.FLOAT.optionalFieldOf("height", 0.12F).forGetter(ProjectionRect::height)).apply(instance, ProjectionRect::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectionRect> STREAM_CODEC = StreamCodec.of(
            ProjectionRect::encode,
            ProjectionRect::decode);
    public ProjectionRect {
        x = finiteOr(x, 0.0F);
        y = finiteOr(y, 0.0F);
        width = Math.max(0.005F, Math.min(MAX_EXTENT, finiteOr(width, 0.25F)));
        height = Math.max(0.005F, Math.min(MAX_EXTENT, finiteOr(height, 0.12F)));
    }

    public float right() {
        return this.x + this.width;
    }

    public float bottom() {
        return this.y + this.height;
    }

    public float centerX() {
        return this.x + this.width * 0.5F;
    }

    public float centerY() {
        return this.y + this.height * 0.5F;
    }

    public ProjectionRect withBounds(float x, float y, float width, float height) {
        return new ProjectionRect(x, y, width, height);
    }

    public ProjectionRect withCenter(float centerX, float centerY, float width, float height) {
        return new ProjectionRect(centerX - width * 0.5F, centerY - height * 0.5F, width, height);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ProjectionRect rect) {
        buffer.writeFloat(rect.x);
        buffer.writeFloat(rect.y);
        buffer.writeFloat(rect.width);
        buffer.writeFloat(rect.height);
    }

    private static ProjectionRect decode(RegistryFriendlyByteBuf buffer) {
        return new ProjectionRect(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    private static float finiteOr(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }
}
