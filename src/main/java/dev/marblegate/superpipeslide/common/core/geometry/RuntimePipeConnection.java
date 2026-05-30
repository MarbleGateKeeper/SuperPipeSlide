package dev.marblegate.superpipeslide.common.core.geometry;

import java.util.Arrays;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RuntimePipeConnection {
    private static final int MIN_SAMPLES = 8;
    private static final int MAX_SAMPLES = 96;

    private final PipeConnection connection;
    private final double length;
    private final Vec3[] samples;
    private final AABB bounds;

    private RuntimePipeConnection(PipeConnection connection, double length, Vec3[] samples, AABB bounds) {
        this.connection = connection;
        this.length = length;
        this.samples = samples;
        this.bounds = bounds;
    }

    public static RuntimePipeConnection create(PipeConnection connection) {
        double length = connection.length();
        int sampleCount = Math.max(MIN_SAMPLES, Math.min(MAX_SAMPLES, (int) Math.ceil(length * 1.5D)));
        Vec3[] samples = new Vec3[sampleCount + 1];

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (int i = 0; i <= sampleCount; i++) {
            Vec3 point = connection.positionAt(length * i / sampleCount);
            samples[i] = point;
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }

        return new RuntimePipeConnection(connection, length, samples, new AABB(minX, minY, minZ, maxX, maxY, maxZ));
    }

    public PipeConnection connection() {
        return this.connection;
    }

    public double length() {
        return this.length;
    }

    public Vec3[] samples() {
        return Arrays.copyOf(this.samples, this.samples.length);
    }

    public int sampleCount() {
        return this.samples.length;
    }

    public Vec3 sample(int index) {
        return this.samples[index];
    }

    public AABB bounds() {
        return this.bounds;
    }

    public boolean isNear(Vec3 position, double radius) {
        if (!this.bounds.inflate(radius).contains(position)) {
            return false;
        }

        double radiusSqr = radius * radius;
        for (Vec3 sample : this.samples) {
            if (sample.distanceToSqr(position) <= radiusSqr) {
                return true;
            }
        }
        return false;
    }
}
