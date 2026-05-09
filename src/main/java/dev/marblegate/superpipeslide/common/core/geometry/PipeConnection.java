package dev.marblegate.superpipeslide.common.core.geometry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

public record PipeConnection(UUID id, int connectionKey, ResourceKey<Level> levelKey, PipeAnchorId fromAnchor, PipeAnchorId toAnchor, CurveSpec curveSpec, Optional<PipeConnectionAttributes> attributes, Optional<UUID> platformStopId) {
    private static final int LENGTH_SAMPLES = 32;
    public static final int TRANSIENT_CONNECTION_KEY = 0;

    public static final Codec<PipeConnection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(PipeConnection::id),
            Codec.INT.optionalFieldOf("connection_key", TRANSIENT_CONNECTION_KEY).forGetter(PipeConnection::connectionKey),
            Level.RESOURCE_KEY_CODEC.fieldOf("level").forGetter(PipeConnection::levelKey),
            PipeAnchorId.CODEC.fieldOf("from").forGetter(PipeConnection::fromAnchor),
            PipeAnchorId.CODEC.fieldOf("to").forGetter(PipeConnection::toAnchor),
            CurveSpec.CODEC.optionalFieldOf("curve_spec", CurveSpec.line()).forGetter(PipeConnection::curveSpec),
            PipeConnectionAttributes.CODEC.optionalFieldOf("attributes").forGetter(PipeConnection::attributes),
            UUIDUtil.STRING_CODEC.optionalFieldOf("platform_stop_id").forGetter(PipeConnection::platformStopId)
    ).apply(instance, PipeConnection::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PipeConnection> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC.cast(),
            PipeConnection::id,
            ByteBufCodecs.VAR_INT.cast(),
            PipeConnection::connectionKey,
            PipeAnchorId.STREAM_CODEC,
            PipeConnection::fromAnchor,
            PipeAnchorId.STREAM_CODEC,
            PipeConnection::toAnchor,
            CurveSpec.STREAM_CODEC,
            PipeConnection::curveSpec,
            ByteBufCodecs.optional(PipeConnectionAttributes.STREAM_CODEC),
            PipeConnection::attributes,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).cast(),
            PipeConnection::platformStopId,
            PipeConnection::newFromAnchors
    );

    private static PipeConnection newFromAnchors(UUID id, int connectionKey, PipeAnchorId fromAnchor, PipeAnchorId toAnchor, CurveSpec curveSpec, Optional<PipeConnectionAttributes> attributes, Optional<UUID> platformStopId) {
        return new PipeConnection(id, connectionKey, fromAnchor.levelKey(), fromAnchor, toAnchor, curveSpec, attributes, platformStopId);
    }

    public PipeConnection {
        if (!fromAnchor.levelKey().equals(toAnchor.levelKey())) {
            throw new IllegalArgumentException("Pipe connections cannot cross dimensions");
        }
        levelKey = fromAnchor.levelKey();
        connectionKey = Math.max(TRANSIENT_CONNECTION_KEY, connectionKey);
        attributes = normalizeAttributes(attributes);
    }

    public static PipeConnection straight(PipeAnchorId fromAnchor, PipeAnchorId toAnchor) {
        return withCurve(fromAnchor, toAnchor, CurveSpec.line());
    }

    public static PipeConnection withCurve(PipeAnchorId fromAnchor, PipeAnchorId toAnchor, CurveSpec curveSpec) {
        return new PipeConnection(UUID.randomUUID(), TRANSIENT_CONNECTION_KEY, fromAnchor.levelKey(), fromAnchor, toAnchor, curveSpec, Optional.empty(), Optional.empty());
    }

    public PipeConnection withConnectionKey(int connectionKey) {
        return new PipeConnection(this.id, connectionKey, this.levelKey, this.fromAnchor, this.toAnchor, this.curveSpec, this.attributes, this.platformStopId);
    }

    public PipeConnection withCurveSpec(CurveSpec curveSpec) {
        return new PipeConnection(this.id, this.connectionKey, this.levelKey, this.fromAnchor, this.toAnchor, curveSpec, this.attributes, this.platformStopId);
    }

    public PipeConnection withAttributes(Optional<PipeConnectionAttributes> attributes) {
        return new PipeConnection(this.id, this.connectionKey, this.levelKey, this.fromAnchor, this.toAnchor, this.curveSpec, attributes, this.platformStopId);
    }

    public PipeConnection withPlatformStopId(Optional<UUID> platformStopId) {
        return new PipeConnection(this.id, this.connectionKey, this.levelKey, this.fromAnchor, this.toAnchor, this.curveSpec, this.attributes, platformStopId);
    }

    public PipeConnectionAttributes resolvedAttributes() {
        return this.attributes.orElse(PipeConnectionAttributes.EMPTY);
    }

    public boolean allowsSlideDirection(int direction) {
        return this.resolvedAttributes().allowsDirection(direction);
    }

    private static Optional<PipeConnectionAttributes> normalizeAttributes(Optional<PipeConnectionAttributes> attributes) {
        if (attributes.isEmpty() || attributes.get().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(attributes.get().normalized());
    }

    public Vec3 fromSurface() {
        return Vec3.atCenterOf(this.fromAnchor.blockPos());
    }

    public Vec3 toSurface() {
        return Vec3.atCenterOf(this.toAnchor.blockPos());
    }

    public double length() {
        return this.sampledLength(LENGTH_SAMPLES);
    }

    public double sampledLength(int samples) {
        int clampedSamples = Math.max(1, samples);
        double length = 0.0D;
        Vec3 previous = this.sampleAt(0.0D);
        for (int i = 1; i <= clampedSamples; i++) {
            Vec3 point = this.sampleAt((double) i / clampedSamples);
            length += point.distanceTo(previous);
            previous = point;
        }
        return length;
    }

    public Vec3 positionAt(double distance) {
        double length = this.length();
        if (length < 1.0E-6D) {
            return this.fromSurface();
        }
        return this.positionAtT(this.tAtDistance(distance));
    }

    public Vec3 tangentAt(double distance) {
        double t = this.tAtDistance(distance);
        double epsilon = 1.0D / LENGTH_SAMPLES;
        Vec3 before = this.positionAtT(Mth.clamp(t - epsilon, 0.0D, 1.0D));
        Vec3 after = this.positionAtT(Mth.clamp(t + epsilon, 0.0D, 1.0D));
        Vec3 tangent = after.subtract(before);
        return tangent.lengthSqr() < 1.0E-6D ? this.endpointTangent() : tangent.normalize();
    }

    public Vec3 tangentForward() {
        return this.endpointTangent();
    }

    public boolean touches(PipeAnchorId anchorId) {
        return this.fromAnchor.equals(anchorId) || this.toAnchor.equals(anchorId);
    }

    public PipeAnchorId anchorForDirectionEnd(int direction) {
        return direction >= 0 ? this.toAnchor : this.fromAnchor;
    }

    public int directionAwayFrom(PipeAnchorId anchorId) {
        return this.fromAnchor.equals(anchorId) ? 1 : -1;
    }

    Vec3 positionAtT(double t) {
        return this.sampleAt(Mth.clamp(t, 0.0D, 1.0D));
    }

    private double tAtDistance(double distance) {
        double totalLength = this.length();
        if (totalLength < 1.0E-6D) {
            return 0.0D;
        }

        double targetDistance = Mth.clamp(distance, 0.0D, totalLength);
        double walked = 0.0D;
        Vec3 previous = this.sampleAt(0.0D);
        for (int i = 1; i <= LENGTH_SAMPLES; i++) {
            double sampleT = (double) i / LENGTH_SAMPLES;
            Vec3 point = this.sampleAt(sampleT);
            double segmentLength = point.distanceTo(previous);
            if (walked + segmentLength >= targetDistance) {
                double segmentT = segmentLength < 1.0E-6D ? 0.0D : (targetDistance - walked) / segmentLength;
                return ((double) (i - 1) + segmentT) / LENGTH_SAMPLES;
            }
            walked += segmentLength;
            previous = point;
        }
        return 1.0D;
    }

    private Vec3 sampleAt(double t) {
        Vec3 from = this.fromSurface();
        Vec3 to = this.toSurface();
        if (this.curveSpec.type() == CurveType.LINE) {
            return from.lerp(to, t);
        }
        if (!this.curveSpec.controlPoints().isEmpty()) {
            return bezier(from, this.curveSpec.controlPoints(), to, t);
        }

        Vec3 axis = to.subtract(from);
        double handleLength = Math.max(0.75D, axis.length() * 0.32D);
        Vec3 startTangent = this.curveSpec.startTangent().orElse(axis).normalize();
        Vec3 endTangent = this.curveSpec.endTangent().orElse(axis).normalize();
        if (startTangent.lengthSqr() < 1.0E-6D || endTangent.lengthSqr() < 1.0E-6D) {
            return from.lerp(to, t);
        }

        Vec3 firstControl = from.add(startTangent.scale(handleLength));
        Vec3 secondControl = to.subtract(endTangent.scale(handleLength));
        return cubic(from, firstControl, secondControl, to, t);
    }

    private Vec3 endpointTangent() {
        Vec3 delta = this.toSurface().subtract(this.fromSurface());
        return delta.lengthSqr() < 1.0E-6D ? Vec3.ZERO : delta.normalize();
    }

    private static Vec3 cubic(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double inverse = 1.0D - t;
        return p0.scale(inverse * inverse * inverse)
                .add(p1.scale(3.0D * inverse * inverse * t))
                .add(p2.scale(3.0D * inverse * t * t))
                .add(p3.scale(t * t * t));
    }

    private static Vec3 bezier(Vec3 from, java.util.List<Vec3> controlPoints, Vec3 to, double t) {
        Vec3[] points = new Vec3[controlPoints.size() + 2];
        points[0] = from;
        for (int i = 0; i < controlPoints.size(); i++) {
            points[i + 1] = controlPoints.get(i);
        }
        points[points.length - 1] = to;

        for (int level = points.length - 1; level > 0; level--) {
            for (int i = 0; i < level; i++) {
                points[i] = points[i].lerp(points[i + 1], t);
            }
        }
        return points[0];
    }
}
