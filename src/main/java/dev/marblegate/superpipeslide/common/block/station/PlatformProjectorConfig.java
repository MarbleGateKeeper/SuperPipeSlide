package dev.marblegate.superpipeslide.common.block.station;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record PlatformProjectorConfig(
        BindingMode bindingMode,
        Optional<UUID> platformStopId,
        Optional<UUID> routeLayoutId,
        PlatformProjectionDirection direction,
        float offsetX,
        float offsetY,
        boolean backsideProjection) {

    public static final float MIN_OFFSET_X = -10.0F;
    public static final float MAX_OFFSET_X = 10.0F;
    public static final float MIN_OFFSET_Y = -10.0F;
    public static final float MAX_OFFSET_Y = 10.0F;

    public static final StreamCodec<RegistryFriendlyByteBuf, PlatformProjectorConfig> STREAM_CODEC = StreamCodec.of(
            PlatformProjectorConfig::encode,
            PlatformProjectorConfig::decode);
    public PlatformProjectorConfig {
        bindingMode = bindingMode == null ? BindingMode.AUTO : bindingMode;
        platformStopId = platformStopId == null ? Optional.empty() : platformStopId;
        routeLayoutId = routeLayoutId == null ? Optional.empty() : routeLayoutId;
        direction = direction == null ? PlatformProjectionDirection.AUTO : direction;
        offsetX = clampOffsetX(offsetX);
        offsetY = clampOffsetY(offsetY);
    }

    public static PlatformProjectorConfig defaults() {
        return new PlatformProjectorConfig(BindingMode.AUTO, Optional.empty(), Optional.empty(), PlatformProjectionDirection.AUTO, 0.0F, 0.82F, false);
    }

    public PlatformProjectorConfig withBinding(BindingMode mode, Optional<UUID> platformStopId, Optional<UUID> routeLayoutId) {
        return new PlatformProjectorConfig(mode, platformStopId, routeLayoutId, this.direction, this.offsetX, this.offsetY, this.backsideProjection);
    }

    public PlatformProjectorConfig withDirection(PlatformProjectionDirection direction) {
        return new PlatformProjectorConfig(this.bindingMode, this.platformStopId, this.routeLayoutId, direction, this.offsetX, this.offsetY, this.backsideProjection);
    }

    public PlatformProjectorConfig withOffset(float offsetX, float offsetY) {
        return new PlatformProjectorConfig(this.bindingMode, this.platformStopId, this.routeLayoutId, this.direction, offsetX, offsetY, this.backsideProjection);
    }

    public PlatformProjectorConfig withBacksideProjection(boolean backsideProjection) {
        return new PlatformProjectorConfig(this.bindingMode, this.platformStopId, this.routeLayoutId, this.direction, this.offsetX, this.offsetY, backsideProjection);
    }

    public static float clampOffsetX(float value) {
        return Math.max(MIN_OFFSET_X, Math.min(MAX_OFFSET_X, value));
    }

    public static float clampOffsetY(float value) {
        return Math.max(MIN_OFFSET_Y, Math.min(MAX_OFFSET_Y, value));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, PlatformProjectorConfig config) {
        buffer.writeEnum(config.bindingMode);
        buffer.writeOptional(config.platformStopId, UUIDUtil.STREAM_CODEC::encode);
        buffer.writeOptional(config.routeLayoutId, UUIDUtil.STREAM_CODEC::encode);
        buffer.writeEnum(config.direction);
        buffer.writeFloat(config.offsetX);
        buffer.writeFloat(config.offsetY);
        buffer.writeBoolean(config.backsideProjection);
    }

    private static PlatformProjectorConfig decode(RegistryFriendlyByteBuf buffer) {
        return new PlatformProjectorConfig(
                buffer.readEnum(BindingMode.class),
                buffer.readOptional(UUIDUtil.STREAM_CODEC::decode),
                buffer.readOptional(UUIDUtil.STREAM_CODEC::decode),
                buffer.readEnum(PlatformProjectionDirection.class),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readBoolean());
    }

    public static <E extends Enum<E>> E enumByName(Class<E> type, String name, E fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public enum BindingMode {
        AUTO,
        MANUAL
    }

    public enum PlatformProjectionDirection {
        AUTO,
        FORWARD,
        REVERSE,
        BIDIRECTIONAL
    }
}
