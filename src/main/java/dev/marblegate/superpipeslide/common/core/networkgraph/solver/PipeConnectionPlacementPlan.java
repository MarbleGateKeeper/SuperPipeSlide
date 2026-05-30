package dev.marblegate.superpipeslide.common.core.networkgraph.solver;

import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionLengthPolicy;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PipeConnectionPlacementPlan(
        PipeConnection candidate,
        Map<UUID, PipeConnection> affectedConnections,
        List<LengthViolation> violations) {
    public PipeConnectionPlacementPlan {
        Objects.requireNonNull(candidate, "candidate");
        affectedConnections = Collections.unmodifiableMap(new LinkedHashMap<>(affectedConnections));
        violations = List.copyOf(violations);
    }

    public boolean hasLengthViolations() {
        return !this.violations.isEmpty();
    }

    public Optional<LengthViolation> largestViolation() {
        return this.violations.stream()
                .max(Comparator.comparingDouble(LengthViolation::measuredLength));
    }

    public double maxMeasuredLength() {
        return this.affectedConnections.values().stream()
                .mapToDouble(PipeConnectionLengthPolicy::measuredLength)
                .max()
                .orElse(0.0D);
    }

    public boolean isNearLimit(double maxLength, double margin) {
        if (this.hasLengthViolations()) {
            return false;
        }
        double threshold = maxLength - Math.max(0.0D, margin);
        return Double.isFinite(threshold) && this.maxMeasuredLength() > threshold;
    }

    public record LengthViolation(PipeConnection connection, double measuredLength, double maxLength, boolean candidateConnection) {
        public LengthViolation {
            Objects.requireNonNull(connection, "connection");
        }

        public double excessLength() {
            return this.measuredLength - this.maxLength;
        }
    }
}
