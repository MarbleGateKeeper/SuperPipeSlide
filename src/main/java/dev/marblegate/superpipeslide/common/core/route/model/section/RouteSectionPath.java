package dev.marblegate.superpipeslide.common.core.route.model.section;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record RouteSectionPath(List<PipeConnectionRef> forwardConnections, List<PipeConnectionRef> reverseConnections) {
    private static final int MAX_CONNECTIONS = 4096;
    private static final StreamCodec<RegistryFriendlyByteBuf, List<PipeConnectionRef>> CONNECTION_REF_LIST_CODEC = PipeConnectionRef.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CONNECTIONS));

    public static final Codec<RouteSectionPath> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PipeConnectionRef.CODEC.listOf().optionalFieldOf("forward_connections", List.of()).forGetter(RouteSectionPath::forwardConnections),
            PipeConnectionRef.CODEC.listOf().optionalFieldOf("reverse_connections", List.of()).forGetter(RouteSectionPath::reverseConnections)).apply(instance, RouteSectionPath::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RouteSectionPath> STREAM_CODEC = StreamCodec.composite(
            CONNECTION_REF_LIST_CODEC,
            RouteSectionPath::forwardConnections,
            CONNECTION_REF_LIST_CODEC,
            RouteSectionPath::reverseConnections,
            RouteSectionPath::new);

    public RouteSectionPath {
        forwardConnections = List.copyOf(forwardConnections);
        reverseConnections = List.copyOf(reverseConnections);
    }
}
