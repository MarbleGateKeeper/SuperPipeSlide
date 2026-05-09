package dev.marblegate.superpipeslide.common.core.route.model.station;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;
import java.util.UUID;

public record StationTransferLink(
        UUID id,
        UUID firstStationGroupId,
        UUID secondStationGroupId,
        int estimatedWalkTicks,
        boolean manual,
        boolean risky
) {
    public static final Codec<StationTransferLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(StationTransferLink::id),
            UUIDUtil.STRING_CODEC.fieldOf("first_station_group_id").forGetter(StationTransferLink::firstStationGroupId),
            UUIDUtil.STRING_CODEC.fieldOf("second_station_group_id").forGetter(StationTransferLink::secondStationGroupId),
            Codec.INT.optionalFieldOf("estimated_walk_ticks", 20 * 20).forGetter(StationTransferLink::estimatedWalkTicks),
            Codec.BOOL.optionalFieldOf("manual", true).forGetter(StationTransferLink::manual),
            Codec.BOOL.optionalFieldOf("risky", false).forGetter(StationTransferLink::risky)
    ).apply(instance, StationTransferLink::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, StationTransferLink> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            StationTransferLink::id,
            UUIDUtil.STREAM_CODEC,
            StationTransferLink::firstStationGroupId,
            UUIDUtil.STREAM_CODEC,
            StationTransferLink::secondStationGroupId,
            ByteBufCodecs.VAR_INT,
            StationTransferLink::estimatedWalkTicks,
            ByteBufCodecs.BOOL,
            StationTransferLink::manual,
            ByteBufCodecs.BOOL,
            StationTransferLink::risky,
            StationTransferLink::new
    );

    public StationTransferLink {
        if (firstStationGroupId.equals(secondStationGroupId)) {
            throw new IllegalArgumentException("A station transfer link cannot point to the same station twice");
        }
        if (firstStationGroupId.compareTo(secondStationGroupId) > 0) {
            UUID swapped = firstStationGroupId;
            firstStationGroupId = secondStationGroupId;
            secondStationGroupId = swapped;
        }
        estimatedWalkTicks = Math.max(20, estimatedWalkTicks);
    }

    public static StationTransferLink manual(UUID firstStationGroupId, UUID secondStationGroupId, int estimatedWalkTicks, boolean risky) {
        return new StationTransferLink(UUID.randomUUID(), firstStationGroupId, secondStationGroupId, estimatedWalkTicks, true, risky);
    }

    public boolean connects(UUID stationGroupId) {
        return this.firstStationGroupId.equals(stationGroupId) || this.secondStationGroupId.equals(stationGroupId);
    }

    public boolean connects(UUID first, UUID second) {
        StationPair pair = StationPair.of(first, second);
        return this.firstStationGroupId.equals(pair.first()) && this.secondStationGroupId.equals(pair.second());
    }

    public Optional<UUID> other(UUID stationGroupId) {
        if (this.firstStationGroupId.equals(stationGroupId)) {
            return Optional.of(this.secondStationGroupId);
        }
        if (this.secondStationGroupId.equals(stationGroupId)) {
            return Optional.of(this.firstStationGroupId);
        }
        return Optional.empty();
    }

    public record StationPair(UUID first, UUID second) {
        public static StationPair of(UUID a, UUID b) {
            return a.compareTo(b) <= 0 ? new StationPair(a, b) : new StationPair(b, a);
        }
    }
}
