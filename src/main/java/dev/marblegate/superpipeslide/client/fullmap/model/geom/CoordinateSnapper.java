package dev.marblegate.superpipeslide.client.fullmap.model.geom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Post-layout coordinate snapping shared by the full-map layout solvers. Relaxation,
 * annealing, and scaling stages leave stations that should share one axis coordinate a
 * tiny fraction of the station spacing apart, so connections that should be exactly
 * horizontal, vertical, or at 45 degrees render slightly tilted. Snapping merges those
 * near-equal coordinates back onto a single shared value after the solver finishes moving
 * nodes.
 */
public final class CoordinateSnapper {
    private CoordinateSnapper() {}

    /**
     * Merges near-equal axis coordinates: x (resp. y) values that differ by less than
     * {@code epsilon} are snapped to a single shared value (the cluster mean), so stations
     * that should line up exactly share an exact coordinate. The two axes are clustered
     * independently, and a cluster spans at most {@code epsilon}, so a chain of small gaps
     * cannot drag far-apart endpoints into one value. The input iteration order is
     * preserved. Returns the input unchanged when it is null, holds fewer than two
     * entries, or {@code epsilon} is not positive.
     */
    public static <K> Map<K, Vec2> mergeNearEqualAxes(Map<K, Vec2> positions, double epsilon) {
        if (positions == null || positions.size() < 2 || epsilon <= 0.0D) {
            return positions;
        }
        Map<Double, Double> snappedX = snapClusters(positions.values().stream().mapToDouble(Vec2::x).toArray(), epsilon);
        Map<Double, Double> snappedY = snapClusters(positions.values().stream().mapToDouble(Vec2::y).toArray(), epsilon);
        Map<K, Vec2> snapped = new LinkedHashMap<>();
        for (Map.Entry<K, Vec2> entry : positions.entrySet()) {
            snapped.put(entry.getKey(), new Vec2(snappedX.get(entry.getValue().x()), snappedY.get(entry.getValue().y())));
        }
        return snapped;
    }

    /**
     * Sorts the given axis values, groups them into clusters of width below
     * {@code epsilon}, and maps every value onto the mean of its cluster.
     */
    private static Map<Double, Double> snapClusters(double[] values, double epsilon) {
        List<Double> sorted = new ArrayList<>(values.length);
        for (double value : values) {
            sorted.add(value);
        }
        sorted.sort(null);
        Map<Double, Double> snapped = new HashMap<>();
        int clusterStart = 0;
        for (int i = 1; i <= sorted.size(); i++) {
            if (i < sorted.size() && sorted.get(i) - sorted.get(clusterStart) < epsilon) {
                continue;
            }
            double mean = 0.0D;
            for (int j = clusterStart; j < i; j++) {
                mean += sorted.get(j);
            }
            mean /= i - clusterStart;
            for (int j = clusterStart; j < i; j++) {
                snapped.put(sorted.get(j), mean);
            }
            clusterStart = i;
        }
        return snapped;
    }
}
