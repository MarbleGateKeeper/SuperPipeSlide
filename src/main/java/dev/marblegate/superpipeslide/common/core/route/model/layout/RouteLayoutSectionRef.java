package dev.marblegate.superpipeslide.common.core.route.model.layout;


import dev.marblegate.superpipeslide.common.core.route.model.section.StopBehavior;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record RouteLayoutSectionRef(UUID routeSectionId, StopBehavior stopBehavior) {
    public static final Codec<RouteLayoutSectionRef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("route_section_id").forGetter(RouteLayoutSectionRef::routeSectionId),
            StopBehavior.CODEC.optionalFieldOf("stop_behavior", StopBehavior.STOP).forGetter(RouteLayoutSectionRef::stopBehavior)
    ).apply(instance, RouteLayoutSectionRef::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RouteLayoutSectionRef> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            RouteLayoutSectionRef::routeSectionId,
            StopBehavior.STREAM_CODEC,
            RouteLayoutSectionRef::stopBehavior,
            RouteLayoutSectionRef::new
    );
}

