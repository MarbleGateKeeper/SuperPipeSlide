package dev.marblegate.superpipeslide.common.core.geometry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable pipe connection value object. This is a hand-written class rather than a
 * record only so it can carry the lazily computed {@link #cachedLength} field (records
 * cannot declare extra instance state); the public API and equals/hashCode/toString keep
 * record semantics, and every withX method still derives a new instance, so the cached
 * length can never go stale.
 */
public final class PipeConnection {
    private static final int LENGTH_SAMPLES = 32;
    public static final int TRANSIENT_CONNECTION_KEY = 0;

    private final UUID id;
    private final int connectionKey;
    private final ResourceKey<Level> levelKey;
    private final PipeAnchorId fromAnchor;
    private final PipeAnchorId toAnchor;
    private final CurveSpec curveSpec;
    private final Optional<PipeEndpoints> endpoints;
    private final Optional<PipeConnectionAttributes> attributes;
    private final Optional<UUID> platformStopId;
    // Lazily computed arc length for the default sample count (NaN = not computed yet).
    // volatile so the racy-but-idempotent double compute is safe when the full route map
    // build samples connections on its background builder thread.
    private volatile double cachedLength = Double.NaN;

    public static final Codec<PipeConnection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(PipeConnection::id),
            Codec.INT.optionalFieldOf("connection_key", TRANSIENT_CONNECTION_KEY).forGetter(PipeConnection::connectionKey),
            Level.RESOURCE_KEY_CODEC.fieldOf("level").forGetter(PipeConnection::levelKey),
            PipeAnchorId.CODEC.fieldOf("from").forGetter(PipeConnection::fromAnchor),
            PipeAnchorId.CODEC.fieldOf("to").forGetter(PipeConnection::toAnchor),
            CurveSpec.CODEC.optionalFieldOf("curve_spec", CurveSpec.line()).forGetter(PipeConnection::curveSpec),
            PipeEndpoints.CODEC.optionalFieldOf("endpoints").forGetter(PipeConnection::endpoints),
            PipeConnectionAttributes.CODEC.optionalFieldOf("attributes").forGetter(PipeConnection::attributes),
            UUIDUtil.STRING_CODEC.optionalFieldOf("platform_stop_id").forGetter(PipeConnection::platformStopId)).apply(instance, PipeConnection::new));
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
            ByteBufCodecs.optional(PipeEndpoints.STREAM_CODEC).cast(),
            PipeConnection::endpoints,
            ByteBufCodecs.optional(PipeConnectionAttributes.STREAM_CODEC),
            PipeConnection::attributes,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).cast(),
            PipeConnection::platformStopId,
            PipeConnection::newFromAnchors);

    private static PipeConnection newFromAnchors(UUID id, int connectionKey, PipeAnchorId fromAnchor, PipeAnchorId toAnchor, CurveSpec curveSpec, Optional<PipeEndpoints> endpoints, Optional<PipeConnectionAttributes> attributes, Optional<UUID> platformStopId) {
        return new PipeConnection(id, connectionKey, fromAnchor.levelKey(), fromAnchor, toAnchor, curveSpec, endpoints, attributes, platformStopId);
    }

    public PipeConnection(UUID id, int connectionKey, ResourceKey<Level> levelKey, PipeAnchorId fromAnchor, PipeAnchorId toAnchor, CurveSpec curveSpec, Optional<PipeEndpoints> endpoints, Optional<PipeConnectionAttributes> attributes, Optional<UUID> platformStopId) {
        if (!fromAnchor.levelKey().equals(toAnchor.levelKey())) {
            throw new IllegalArgumentException("Pipe connections cannot cross dimensions");
        }
        if (fromAnchor.equals(toAnchor)) {
            throw new IllegalArgumentException("Pipe connections cannot connect an anchor to itself");
        }
        this.id = id;
        this.connectionKey = Math.max(TRANSIENT_CONNECTION_KEY, connectionKey);
        this.levelKey = fromAnchor.levelKey();
        this.fromAnchor = fromAnchor;
        this.toAnchor = toAnchor;
        this.curveSpec = curveSpec;
        this.endpoints = endpoints;
        this.attributes = normalizeAttributes(attributes);
        this.platformStopId = platformStopId;
    }

    public UUID id() {
        return this.id;
    }

    public int connectionKey() {
        return this.connectionKey;
    }

    public ResourceKey<Level> levelKey() {
        return this.levelKey;
    }

    public PipeAnchorId fromAnchor() {
        return this.fromAnchor;
    }

    public PipeAnchorId toAnchor() {
        return this.toAnchor;
    }

    public CurveSpec curveSpec() {
        return this.curveSpec;
    }

    public Optional<PipeEndpoints> endpoints() {
        return this.endpoints;
    }

    public Optional<PipeConnectionAttributes> attributes() {
        return this.attributes;
    }

    public Optional<UUID> platformStopId() {
        return this.platformStopId;
    }

    public static PipeConnection straight(PipeAnchorId fromAnchor, PipeAnchorId toAnchor) {
        return withCurve(fromAnchor, toAnchor, CurveSpec.line());
    }

    public static PipeConnection withCurve(PipeAnchorId fromAnchor, PipeAnchorId toAnchor, CurveSpec curveSpec) {
        return new PipeConnection(UUID.randomUUID(), TRANSIENT_CONNECTION_KEY, fromAnchor.levelKey(), fromAnchor, toAnchor, curveSpec, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public PipeConnection withConnectionKey(int connectionKey) {
        return new PipeConnection(this.id, connectionKey, this.levelKey, this.fromAnchor, this.toAnchor, this.curveSpec, this.endpoints, this.attributes, this.platformStopId);
    }

    public PipeConnection withCurveSpec(CurveSpec curveSpec) {
        return new PipeConnection(this.id, this.connectionKey, this.levelKey, this.fromAnchor, this.toAnchor, curveSpec, this.endpoints, this.attributes, this.platformStopId);
    }

    public PipeConnection withEndpoints(Vec3 from, Vec3 to) {
        return new PipeConnection(this.id, this.connectionKey, this.levelKey, this.fromAnchor, this.toAnchor, this.curveSpec, Optional.of(new PipeEndpoints(from, to)), this.attributes, this.platformStopId);
    }

    public PipeConnection withEndpointAt(PipeAnchorId anchorId, Vec3 point) {
        if (this.fromAnchor.equals(anchorId)) {
            return this.withEndpoints(point, this.toSurface());
        }
        if (this.toAnchor.equals(anchorId)) {
            return this.withEndpoints(this.fromSurface(), point);
        }
        return this;
    }

    public PipeConnection withAttributes(Optional<PipeConnectionAttributes> attributes) {
        return new PipeConnection(this.id, this.connectionKey, this.levelKey, this.fromAnchor, this.toAnchor, this.curveSpec, this.endpoints, attributes, this.platformStopId);
    }

    public PipeConnection withPlatformStopId(Optional<UUID> platformStopId) {
        return new PipeConnection(this.id, this.connectionKey, this.levelKey, this.fromAnchor, this.toAnchor, this.curveSpec, this.endpoints, this.attributes, platformStopId);
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
        return this.endpoints.map(PipeEndpoints::from).orElseGet(() -> Vec3.atCenterOf(this.fromAnchor.blockPos()));
    }

    public Vec3 toSurface() {
        return this.endpoints.map(PipeEndpoints::to).orElseGet(() -> Vec3.atCenterOf(this.toAnchor.blockPos()));
    }

    public double length() {
        double cached = this.cachedLength;
        if (Double.isNaN(cached)) {
            cached = this.sampledLength(LENGTH_SAMPLES);
            this.cachedLength = cached;
        }
        return cached;
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
        if (this.curveSpec.type() == CurveType.PATH) {
            return this.samplePath(from, to, Mth.clamp(t, 0.0D, 1.0D));
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

    /**
     * Samples a PATH curve: a piecewise cubic bezier chain through
     * [from, ...pathNodes, to]. Segment handles are resolved by PathCurves (manual node
     * handles first, Catmull-Rom automatic handles otherwise), so chains with no manual
     * handles stay C1-smooth and follow edits applied to neighbouring points. The global
     * parameter t is distributed uniformly across segments; arc-length mapping happens in
     * tAtDistance.
     */
    private Vec3 samplePath(Vec3 from, Vec3 to, double t) {
        List<PipePathNode> nodes = this.curveSpec.pathNodes();
        int segments = nodes.size() + 1;
        double scaled = t * segments;
        int segment = Math.min((int) scaled, segments - 1);
        double localT = scaled - segment;
        Vec3 p0 = PathCurves.pointAt(from, to, nodes, segment);
        Vec3 p1 = PathCurves.outHandle(from, to, nodes, this.curveSpec.startTangent(), segment);
        Vec3 p2 = PathCurves.inHandle(from, to, nodes, this.curveSpec.endTangent(), segment + 1);
        Vec3 p3 = PathCurves.pointAt(from, to, nodes, segment + 1);
        return cubic(p0, p1, p2, p3, localT);
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PipeConnection that)) {
            return false;
        }
        return this.connectionKey == that.connectionKey
                && this.id.equals(that.id)
                && this.levelKey.equals(that.levelKey)
                && this.fromAnchor.equals(that.fromAnchor)
                && this.toAnchor.equals(that.toAnchor)
                && this.curveSpec.equals(that.curveSpec)
                && this.endpoints.equals(that.endpoints)
                && this.attributes.equals(that.attributes)
                && this.platformStopId.equals(that.platformStopId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.connectionKey, this.levelKey, this.fromAnchor, this.toAnchor, this.curveSpec, this.endpoints, this.attributes, this.platformStopId);
    }

    @Override
    public String toString() {
        return "PipeConnection[id=" + this.id
                + ", connectionKey=" + this.connectionKey
                + ", levelKey=" + this.levelKey
                + ", fromAnchor=" + this.fromAnchor
                + ", toAnchor=" + this.toAnchor
                + ", curveSpec=" + this.curveSpec
                + ", endpoints=" + this.endpoints
                + ", attributes=" + this.attributes
                + ", platformStopId=" + this.platformStopId + "]";
    }
}
