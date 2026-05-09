package dev.marblegate.superpipeslide.common.core.geometry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public record CurveSpec(CurveType type, Optional<Vec3> startTangent, Optional<Vec3> endTangent, List<Vec3> controlPoints) {
    private static final int MAX_CONTROL_POINTS = 16;

    public static final Codec<CurveSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CurveType.CODEC.optionalFieldOf("type", CurveType.LINE).forGetter(CurveSpec::type),
            Vec3.CODEC.optionalFieldOf("start_tangent").forGetter(CurveSpec::startTangent),
            Vec3.CODEC.optionalFieldOf("end_tangent").forGetter(CurveSpec::endTangent),
            Vec3.CODEC.listOf().optionalFieldOf("control_points", List.of()).forGetter(CurveSpec::controlPoints)
    ).apply(instance, CurveSpec::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, CurveSpec> STREAM_CODEC = StreamCodec.composite(
            CurveType.STREAM_CODEC,
            CurveSpec::type,
            ByteBufCodecs.optional(Vec3.STREAM_CODEC).cast(),
            CurveSpec::startTangent,
            ByteBufCodecs.optional(Vec3.STREAM_CODEC).cast(),
            CurveSpec::endTangent,
            Vec3.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CONTROL_POINTS)).cast(),
            CurveSpec::controlPoints,
            CurveSpec::new
    );

    public CurveSpec {
        controlPoints = List.copyOf(controlPoints);
    }

    public static CurveSpec line() {
        return new CurveSpec(CurveType.LINE, Optional.empty(), Optional.empty(), List.of());
    }

    public static CurveSpec autoCurve(Vec3 startTangent, Vec3 endTangent) {
        return new CurveSpec(CurveType.AUTO_CURVE, Optional.of(normalizeOrZero(startTangent)), Optional.of(normalizeOrZero(endTangent)), List.of());
    }

    public static CurveSpec autoCurve(Vec3 startTangent, Vec3 endTangent, List<Vec3> controlPoints) {
        return new CurveSpec(CurveType.AUTO_CURVE, Optional.of(normalizeOrZero(startTangent)), Optional.of(normalizeOrZero(endTangent)), List.copyOf(controlPoints));
    }

    public static CurveSpec gazeCurve(Vec3 startTangent, Vec3 endTangent) {
        return new CurveSpec(CurveType.GAZE_CURVE, Optional.of(normalizeOrZero(startTangent)), Optional.of(normalizeOrZero(endTangent)), List.of());
    }

    public static CurveSpec controlled(List<Vec3> controlPoints) {
        return new CurveSpec(CurveType.CONTROLLED, Optional.empty(), Optional.empty(), List.copyOf(controlPoints));
    }

    private static Vec3 normalizeOrZero(Vec3 vector) {
        return vector.lengthSqr() < 1.0E-6D ? Vec3.ZERO : vector.normalize();
    }
}
