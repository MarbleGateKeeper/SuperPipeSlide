package dev.marblegate.superpipeslide.common.block.station;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record StationNameProjectorConfig(
        BindingMode bindingMode,
        Optional<UUID> stationGroupId,
        float offsetX,
        float offsetY,
        boolean showExit,
        String exitLabel,
        boolean backsideProjection) {

    public static final float MIN_OFFSET_X = -10.0F;
    public static final float MAX_OFFSET_X = 10.0F;
    public static final float MIN_OFFSET_Y = -10.0F;
    public static final float MAX_OFFSET_Y = 10.0F;
    public static final float MAX_CENTER_DISTANCE = 10.0F;
    private static final int MAX_EXIT_LABEL_LENGTH = 4;

    public static final StreamCodec<RegistryFriendlyByteBuf, StationNameProjectorConfig> STREAM_CODEC = StreamCodec.of(
            StationNameProjectorConfig::encode,
            StationNameProjectorConfig::decode);
    public StationNameProjectorConfig {
        bindingMode = bindingMode == null ? BindingMode.AUTO : bindingMode;
        stationGroupId = stationGroupId == null ? Optional.empty() : stationGroupId;
        offsetX = clampOffsetX(offsetX);
        offsetY = clampOffsetY(offsetY);
        exitLabel = normalizeExitLabel(exitLabel);
    }

    public static StationNameProjectorConfig defaults() {
        return new StationNameProjectorConfig(
                BindingMode.AUTO,
                Optional.empty(),
                0.0F,
                0.82F,
                false,
                "",
                false);
    }

    public StationNameProjectorConfig withBinding(BindingMode mode, Optional<UUID> stationGroupId) {
        return new StationNameProjectorConfig(mode, stationGroupId, this.offsetX, this.offsetY, this.showExit, this.exitLabel, this.backsideProjection);
    }

    public StationNameProjectorConfig withOffset(float offsetX, float offsetY) {
        return new StationNameProjectorConfig(this.bindingMode, this.stationGroupId, offsetX, offsetY, this.showExit, this.exitLabel, this.backsideProjection);
    }

    public StationNameProjectorConfig withShowExit(boolean showExit) {
        return new StationNameProjectorConfig(this.bindingMode, this.stationGroupId, this.offsetX, this.offsetY, showExit, this.exitLabel, this.backsideProjection);
    }

    public StationNameProjectorConfig withExitLabel(String exitLabel) {
        return new StationNameProjectorConfig(this.bindingMode, this.stationGroupId, this.offsetX, this.offsetY, this.showExit, exitLabel, this.backsideProjection);
    }

    public StationNameProjectorConfig withBacksideProjection(boolean backsideProjection) {
        return new StationNameProjectorConfig(this.bindingMode, this.stationGroupId, this.offsetX, this.offsetY, this.showExit, this.exitLabel, backsideProjection);
    }

    public static float clampOffsetX(float value) {
        return Math.max(MIN_OFFSET_X, Math.min(MAX_OFFSET_X, value));
    }

    public static float clampOffsetY(float value) {
        return Math.max(MIN_OFFSET_Y, Math.min(MAX_OFFSET_Y, value));
    }

    public static float clampOffsetToRange(float value, boolean xAxis) {
        return xAxis ? clampOffsetX(value) : clampOffsetY(value);
    }

    private static String normalizeExitLabel(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() <= MAX_EXIT_LABEL_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_EXIT_LABEL_LENGTH);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, StationNameProjectorConfig config) {
        buffer.writeEnum(config.bindingMode);
        buffer.writeOptional(config.stationGroupId, UUIDUtil.STREAM_CODEC::encode);
        buffer.writeFloat(config.offsetX);
        buffer.writeFloat(config.offsetY);
        buffer.writeBoolean(config.showExit);
        buffer.writeUtf(config.exitLabel, MAX_EXIT_LABEL_LENGTH * 4);
        buffer.writeBoolean(config.backsideProjection);
    }

    private static StationNameProjectorConfig decode(RegistryFriendlyByteBuf buffer) {
        return new StationNameProjectorConfig(
                buffer.readEnum(BindingMode.class),
                buffer.readOptional(UUIDUtil.STREAM_CODEC::decode),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readBoolean(),
                buffer.readUtf(MAX_EXIT_LABEL_LENGTH * 4),
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
}
