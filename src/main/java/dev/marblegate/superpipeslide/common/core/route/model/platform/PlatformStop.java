package dev.marblegate.superpipeslide.common.core.route.model.platform;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record PlatformStop(
        UUID id,
        UUID stationGroupId,
        Optional<UUID> routeLineId,
        String platformNumber,
        Optional<String> displayName,
        PipeConnectionRef connectionRef,
        double length) {

    public static final Comparator<PlatformStop> DISPLAY_ORDER = Comparator
            .comparingInt(PlatformStop::platformNumberSortKey)
            .thenComparing(PlatformStop::platformNumber)
            .thenComparing(PlatformStop::id);

    public static final Codec<PlatformStop> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(PlatformStop::id),
            UUIDUtil.STRING_CODEC.fieldOf("station_group_id").forGetter(PlatformStop::stationGroupId),
            UUIDUtil.STRING_CODEC.optionalFieldOf("route_line_id").forGetter(PlatformStop::routeLineId),
            Codec.STRING.optionalFieldOf("platform_number", "").forGetter(PlatformStop::platformNumber),
            Codec.STRING.optionalFieldOf("display_name").forGetter(PlatformStop::displayName),
            PipeConnectionRef.CODEC.fieldOf("connection").forGetter(PlatformStop::connectionRef),
            Codec.DOUBLE.optionalFieldOf("length", 0.0D).forGetter(PlatformStop::length)).apply(instance, PlatformStop::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlatformStop> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            PlatformStop::id,
            UUIDUtil.STREAM_CODEC,
            PlatformStop::stationGroupId,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).cast(),
            PlatformStop::routeLineId,
            ByteBufCodecs.STRING_UTF8,
            PlatformStop::platformNumber,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8),
            PlatformStop::displayName,
            PipeConnectionRef.STREAM_CODEC,
            PlatformStop::connectionRef,
            ByteBufCodecs.DOUBLE.cast(),
            PlatformStop::length,
            PlatformStop::new);
    public PlatformStop {
        platformNumber = sanitizePlatformNumber(platformNumber);
        length = Math.max(0.0D, length);
    }

    public UUID connectionId() {
        return this.connectionRef.connectionId();
    }

    public PlatformStop withDisplay(String platformNumber, Optional<String> displayName) {
        return new PlatformStop(this.id, this.stationGroupId, this.routeLineId, platformNumber, displayName, this.connectionRef, this.length);
    }

    public PlatformStop withRouteLine(Optional<UUID> routeLineId) {
        return new PlatformStop(this.id, this.stationGroupId, routeLineId, this.platformNumber, this.displayName, this.connectionRef, this.length);
    }

    public PlatformStop withLength(double length) {
        return new PlatformStop(this.id, this.stationGroupId, this.routeLineId, this.platformNumber, this.displayName, this.connectionRef, length);
    }

    public static String sanitizePlatformNumber(String platformNumber) {
        return platformNumber == null ? "" : platformNumber.trim();
    }

    private static int platformNumberSortKey(PlatformStop platformStop) {
        String number = platformStop.platformNumber();
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }
}
