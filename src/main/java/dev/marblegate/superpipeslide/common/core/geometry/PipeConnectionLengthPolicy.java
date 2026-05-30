package dev.marblegate.superpipeslide.common.core.geometry;

public final class PipeConnectionLengthPolicy {
    public static final int VALIDATION_SAMPLES = 96;

    private PipeConnectionLengthPolicy() {
    }

    public static double measuredLength(PipeConnection connection) {
        double chordLength = connection.fromSurface().distanceTo(connection.toSurface());
        double sampledLength = connection.sampledLength(VALIDATION_SAMPLES);
        if (!Double.isFinite(chordLength) || !Double.isFinite(sampledLength)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(chordLength, sampledLength);
    }

    public static boolean exceedsLimit(PipeConnection connection, double maxLength) {
        return exceedsLimit(measuredLength(connection), maxLength);
    }

    public static boolean exceedsLimit(double measuredLength, double maxLength) {
        return !Double.isFinite(measuredLength) || !Double.isFinite(maxLength) || measuredLength > maxLength;
    }
}
