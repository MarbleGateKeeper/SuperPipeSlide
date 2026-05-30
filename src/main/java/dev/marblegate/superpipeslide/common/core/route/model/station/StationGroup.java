package dev.marblegate.superpipeslide.common.core.route.model.station;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record StationGroup(UUID id, ResourceKey<Level> levelKey, BlockPos stationBlockPos, String primaryName, List<String> translatedNames, double platformClaimRadius) {

    private static final int MAX_TRANSLATED_NAMES = 1;

    public static final Codec<StationGroup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(StationGroup::id),
            Level.RESOURCE_KEY_CODEC.fieldOf("level").forGetter(StationGroup::levelKey),
            BlockPos.CODEC.fieldOf("station_block_pos").forGetter(StationGroup::stationBlockPos),
            Codec.STRING.optionalFieldOf("primary_name", "Unnamed Station").forGetter(StationGroup::primaryName),
            Codec.STRING.listOf().optionalFieldOf("translated_names", List.of()).forGetter(StationGroup::translatedNames),
            Codec.DOUBLE.optionalFieldOf("platform_claim_radius", 64.0D).forGetter(StationGroup::platformClaimRadius)).apply(instance, StationGroup::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, StationGroup> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            StationGroup::id,
            ResourceKey.streamCodec(Registries.DIMENSION).cast(),
            StationGroup::levelKey,
            BlockPos.STREAM_CODEC,
            StationGroup::stationBlockPos,
            ByteBufCodecs.STRING_UTF8,
            StationGroup::primaryName,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_TRANSLATED_NAMES)),
            StationGroup::translatedNames,
            ByteBufCodecs.DOUBLE.cast(),
            StationGroup::platformClaimRadius,
            StationGroup::new);
    public StationGroup {
        translatedNames = translatedNames.stream().filter(name -> !name.isBlank()).limit(MAX_TRANSLATED_NAMES).toList();
        primaryName = primaryName.isBlank() ? "Unnamed Station" : primaryName;
        platformClaimRadius = Math.max(1.0D, platformClaimRadius);
    }

    public StationGroup withNames(String primaryName, List<String> translatedNames) {
        return new StationGroup(this.id, this.levelKey, this.stationBlockPos, primaryName, translatedNames, this.platformClaimRadius);
    }
}
