package dev.marblegate.superpipeslide.common.core.route.model.layout;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record RouteLayout(
        UUID id,
        UUID routeLineId,
        Optional<String> displayName,
        List<String> translatedNames,
        Optional<UUID> terminalStationGroupId,
        List<UUID> orderedPlatformStops,
        List<RouteLayoutSectionRef> orderedSectionRefs,
        boolean bidirectional,
        boolean loop,
        boolean nameAsSectionName
) {
    private static final int MAX_TRANSLATED_NAMES = 1;
    private static final int MAX_STOPS = 512;
    private static final int MAX_SECTIONS = 512;

    public static final Codec<RouteLayout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(RouteLayout::id),
            UUIDUtil.STRING_CODEC.fieldOf("route_line_id").forGetter(RouteLayout::routeLineId),
            Codec.STRING.optionalFieldOf("display_name").forGetter(RouteLayout::displayName),
            Codec.STRING.listOf().optionalFieldOf("translated_names", List.of()).forGetter(RouteLayout::translatedNames),
            UUIDUtil.STRING_CODEC.optionalFieldOf("terminal_station_group_id").forGetter(RouteLayout::terminalStationGroupId),
            UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("ordered_platform_stops", List.of()).forGetter(RouteLayout::orderedPlatformStops),
            RouteLayoutSectionRef.CODEC.listOf().optionalFieldOf("ordered_section_refs", List.of()).forGetter(RouteLayout::orderedSectionRefs),
            Codec.BOOL.optionalFieldOf("bidirectional", false).forGetter(RouteLayout::bidirectional),
            Codec.BOOL.optionalFieldOf("loop", false).forGetter(RouteLayout::loop),
            Codec.BOOL.optionalFieldOf("name_as_section_name", false).forGetter(RouteLayout::nameAsSectionName)
    ).apply(instance, RouteLayout::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RouteLayout> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            RouteLayout::id,
            UUIDUtil.STREAM_CODEC,
            RouteLayout::routeLineId,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8),
            RouteLayout::displayName,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_TRANSLATED_NAMES)),
            RouteLayout::translatedNames,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).cast(),
            RouteLayout::terminalStationGroupId,
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STOPS)).cast(),
            RouteLayout::orderedPlatformStops,
            RouteLayoutSectionRef.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SECTIONS)),
            RouteLayout::orderedSectionRefs,
            ByteBufCodecs.BOOL,
            RouteLayout::bidirectional,
            ByteBufCodecs.BOOL,
            RouteLayout::loop,
            ByteBufCodecs.BOOL,
            RouteLayout::nameAsSectionName,
            RouteLayout::new
    );

    public RouteLayout {
        translatedNames = translatedNames.stream().filter(name -> !name.isBlank()).limit(MAX_TRANSLATED_NAMES).toList();
        orderedPlatformStops = List.copyOf(orderedPlatformStops);
        orderedSectionRefs = List.copyOf(orderedSectionRefs);
    }

    public RouteLayout withStopsAndSections(List<UUID> orderedPlatformStops, List<RouteLayoutSectionRef> orderedSectionRefs) {
        return new RouteLayout(this.id, this.routeLineId, this.displayName, this.translatedNames, this.terminalStationGroupId, orderedPlatformStops, orderedSectionRefs, this.bidirectional, this.loop, this.nameAsSectionName);
    }

    public RouteLayout withMetadata(Optional<String> displayName, List<String> translatedNames, boolean bidirectional, boolean loop, boolean nameAsSectionName) {
        return new RouteLayout(this.id, this.routeLineId, displayName, translatedNames, this.terminalStationGroupId, this.orderedPlatformStops, this.orderedSectionRefs, bidirectional, loop, nameAsSectionName);
    }
}

