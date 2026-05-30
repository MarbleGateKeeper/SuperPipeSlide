package dev.marblegate.superpipeslide.common.core.networkgraph.solver;

import dev.marblegate.superpipeslide.common.core.geometry.CurveSpec;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionLengthPolicy;
import dev.marblegate.superpipeslide.common.core.networkgraph.branch.BranchConnectionSlot;
import dev.marblegate.superpipeslide.common.core.networkgraph.branch.BranchNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class PipeConnectionPlacementPlanner {
    private PipeConnectionPlacementPlanner() {
    }

    public static PipeConnectionPlacementPlan plan(PipeNetworkView network, PipeConnection candidate, double maxLength) {
        OverlayPipeNetworkView overlay = OverlayPipeNetworkView.withUpsert(network, candidate, branchOverlays(network, candidate));
        Set<PipeAnchorId> affectedAnchors = new LinkedHashSet<>();
        affectedAnchors.add(candidate.fromAnchor());
        affectedAnchors.add(candidate.toAnchor());

        Set<UUID> affectedConnectionIds = new LinkedHashSet<>(AutoCurveSolver.affectedAutoCurveIdsAround(overlay, affectedAnchors));
        Map<UUID, CurveSpec> updatedSpecs = AutoCurveSolver.recomputeAutoCurveSpecs(overlay, affectedConnectionIds);
        Map<UUID, PipeConnection> affectedConnections = new LinkedHashMap<>();
        addPlannedConnection(candidate.id(), candidate, overlay, updatedSpecs, affectedConnections);
        for (UUID connectionId : affectedConnectionIds) {
            if (!connectionId.equals(candidate.id())) {
                addPlannedConnection(connectionId, candidate, overlay, updatedSpecs, affectedConnections);
            }
        }

        PipeConnection plannedCandidate = affectedConnections.getOrDefault(candidate.id(), candidate);

        List<PipeConnectionPlacementPlan.LengthViolation> violations = new ArrayList<>();
        for (PipeConnection connection : affectedConnections.values()) {
            double measuredLength = PipeConnectionLengthPolicy.measuredLength(connection);
            if (PipeConnectionLengthPolicy.exceedsLimit(measuredLength, maxLength)) {
                violations.add(new PipeConnectionPlacementPlan.LengthViolation(connection, measuredLength, maxLength, connection.id().equals(plannedCandidate.id())));
            }
        }
        return new PipeConnectionPlacementPlan(plannedCandidate, affectedConnections, violations);
    }

    private static void addPlannedConnection(UUID connectionId, PipeConnection candidate, OverlayPipeNetworkView overlay, Map<UUID, CurveSpec> updatedSpecs, Map<UUID, PipeConnection> affectedConnections) {
        Optional<PipeConnection> connection = connectionId.equals(candidate.id())
                ? Optional.of(candidate)
                : overlay.connection(connectionId);
        connection.map(value -> value.withCurveSpec(updatedSpecs.getOrDefault(value.id(), value.curveSpec())))
                .ifPresent(updated -> affectedConnections.put(updated.id(), updated));
    }

    private static Map<PipeAnchorId, BranchNode> branchOverlays(PipeNetworkView network, PipeConnection candidate) {
        Map<PipeAnchorId, BranchNode> overlays = new LinkedHashMap<>();
        addBranchOverlay(network, candidate, candidate.fromAnchor(), overlays);
        addBranchOverlay(network, candidate, candidate.toAnchor(), overlays);
        return overlays;
    }

    private static void addBranchOverlay(PipeNetworkView network, PipeConnection candidate, PipeAnchorId anchorId, Map<PipeAnchorId, BranchNode> overlays) {
        Optional<BranchNode> branchNode = network.branchNodeAt(anchorId);
        if (branchNode.isEmpty() || branchNode.get().referencesConnection(candidate.id())) {
            return;
        }

        List<BranchConnectionSlot> managedConnections = new ArrayList<>(branchNode.get().connections());
        if (managedConnections.size() >= BranchNode.MAX_CONNECTIONS) {
            return;
        }
        Vec3 localDirection = new Vec3(0.0D, 0.0D, 1.0D);
        String displayName = managedConnections.isEmpty() ? "Default" : "Path " + (managedConnections.size() + 1);
        managedConnections.add(new BranchConnectionSlot(unusedBranchSlotId(candidate.id(), managedConnections), candidate.id(), displayName, localDirection, localDirection.scale(3.0D), Optional.empty()));
        overlays.put(anchorId, branchNode.get().withConnections(managedConnections));
    }

    private static UUID unusedBranchSlotId(UUID seed, List<BranchConnectionSlot> existingSlots) {
        UUID slotId = seed;
        long salt = 1L;
        while (branchSlotIdExists(existingSlots, slotId)) {
            slotId = new UUID(seed.getMostSignificantBits() ^ salt, seed.getLeastSignificantBits() + salt);
            salt++;
        }
        return slotId;
    }

    private static boolean branchSlotIdExists(List<BranchConnectionSlot> slots, UUID slotId) {
        for (BranchConnectionSlot slot : slots) {
            if (slot.id().equals(slotId)) {
                return true;
            }
        }
        return false;
    }
}
