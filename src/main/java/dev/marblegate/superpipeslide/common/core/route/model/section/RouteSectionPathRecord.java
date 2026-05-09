package dev.marblegate.superpipeslide.common.core.route.model.section;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record RouteSectionPathRecord(UUID routeSectionId, RouteSectionPath path) {
    public static final Codec<RouteSectionPathRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("route_section_id").forGetter(RouteSectionPathRecord::routeSectionId),
            RouteSectionPath.CODEC.fieldOf("path").forGetter(RouteSectionPathRecord::path)
    ).apply(instance, RouteSectionPathRecord::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RouteSectionPathRecord> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            RouteSectionPathRecord::routeSectionId,
            RouteSectionPath.STREAM_CODEC,
            RouteSectionPathRecord::path,
            RouteSectionPathRecord::new
    );
}
