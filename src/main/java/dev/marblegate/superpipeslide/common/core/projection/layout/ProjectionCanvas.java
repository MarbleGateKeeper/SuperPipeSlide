package dev.marblegate.superpipeslide.common.core.projection.layout;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record ProjectionCanvas(float width, float height) {
    public static final float MIN_WIDTH = 0.5F;
    public static final float MAX_WIDTH = 12.0F;
    public static final float MIN_HEIGHT = 0.25F;
    public static final float MAX_HEIGHT = 8.0F;

    public static final Codec<ProjectionCanvas> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("width", 2.75F).forGetter(ProjectionCanvas::width),
            Codec.FLOAT.optionalFieldOf("height", 0.95F).forGetter(ProjectionCanvas::height)).apply(instance, ProjectionCanvas::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectionCanvas> STREAM_CODEC = StreamCodec.of(
            ProjectionCanvas::encode,
            ProjectionCanvas::decode);

    public ProjectionCanvas {
        width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, Float.isFinite(width) ? width : 2.75F));
        height = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, Float.isFinite(height) ? height : 0.95F));
    }

    public static ProjectionCanvas standardHorizontal() {
        return new ProjectionCanvas(2.75F, 0.95F);
    }

    public static ProjectionCanvas verticalPylon() {
        return new ProjectionCanvas(0.9F, 3.5F);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ProjectionCanvas canvas) {
        buffer.writeFloat(canvas.width);
        buffer.writeFloat(canvas.height);
    }

    private static ProjectionCanvas decode(RegistryFriendlyByteBuf buffer) {
        return new ProjectionCanvas(buffer.readFloat(), buffer.readFloat());
    }
}
