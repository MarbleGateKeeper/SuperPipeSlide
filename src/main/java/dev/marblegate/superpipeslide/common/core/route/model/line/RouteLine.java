package dev.marblegate.superpipeslide.common.core.route.model.line;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record RouteLine(UUID id, String displayName, List<String> translatedNames, List<Integer> themeColors, List<UUID> layoutIds, boolean visibleOnHud) {

    private static final int MAX_TRANSLATED_NAMES = 1;
    private static final int MAX_THEME_COLORS = 3;
    private static final int MAX_LAYOUTS = 128;

    public static final Codec<RouteLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(RouteLine::id),
            Codec.STRING.optionalFieldOf("display_name", "Unnamed Line").forGetter(RouteLine::displayName),
            Codec.STRING.listOf().optionalFieldOf("translated_names", List.of()).forGetter(RouteLine::translatedNames),
            Codec.INT.listOf().optionalFieldOf("theme_colors", List.of(0xE03366FF)).forGetter(RouteLine::themeColors),
            UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("layout_ids", List.of()).forGetter(RouteLine::layoutIds),
            Codec.BOOL.optionalFieldOf("visible_on_hud", true).forGetter(RouteLine::visibleOnHud)).apply(instance, RouteLine::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RouteLine> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            RouteLine::id,
            ByteBufCodecs.STRING_UTF8,
            RouteLine::displayName,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_TRANSLATED_NAMES)),
            RouteLine::translatedNames,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_THEME_COLORS)),
            RouteLine::themeColors,
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LAYOUTS)).cast(),
            RouteLine::layoutIds,
            ByteBufCodecs.BOOL,
            RouteLine::visibleOnHud,
            RouteLine::new);
    public RouteLine {
        translatedNames = translatedNames.stream().filter(name -> !name.isBlank()).limit(MAX_TRANSLATED_NAMES).toList();
        themeColors = normalizeColors(themeColors);
        layoutIds = List.copyOf(layoutIds);
        displayName = displayName.isBlank() ? "Unnamed Line" : displayName;
    }

    public RouteLine withMetadata(String displayName, List<String> translatedNames, List<Integer> themeColors) {
        return new RouteLine(this.id, displayName, translatedNames, themeColors, this.layoutIds, this.visibleOnHud);
    }

    public RouteLine withLayoutIds(List<UUID> layoutIds) {
        return new RouteLine(this.id, this.displayName, this.translatedNames, this.themeColors, layoutIds, this.visibleOnHud);
    }

    private static List<Integer> normalizeColors(List<Integer> colors) {
        List<Integer> normalized = colors.isEmpty() ? List.of(0xE03366FF) : colors.stream().limit(MAX_THEME_COLORS).toList();
        return List.copyOf(normalized);
    }
}
