package dev.marblegate.superpipeslide.common.core.slide.traversal;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.networkgraph.branch.BranchNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkView;
import dev.marblegate.superpipeslide.common.core.slide.traversal.TraversalContext.RouteChoiceSelection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class TraversalContinuationResolver {
    private TraversalContinuationResolver() {}

    public static ContinuationDecision resolve(PipeNetworkView pipeNetwork, TraversalContext context, PipeConnection current, int currentDirection, PipeAnchorId exitAnchor) {
        if (pipeNetwork.foldAnchorAt(exitAnchor).isPresent()) {
            return ContinuationDecision.barrier(ContinuationDecision.Type.FOLD_TRANSITION_REQUIRED, exitAnchor);
        }

        Optional<BranchNode> branchNode = pipeNetwork.branchNodeManagingConnection(current.id());
        if (branchNode.isPresent() && exitAnchor.equals(branchNode.get().anchorId())) {
            return resolveBranch(pipeNetwork, context, current, currentDirection, branchNode.get());
        }

        List<PipeConnection> candidates = pipeNetwork.connectionsTouching(exitAnchor).stream()
                .filter(connection -> !connection.id().equals(current.id()))
                .toList();
        if (candidates.size() != 1) {
            return ContinuationDecision.barrier(ContinuationDecision.Type.NO_CONTINUATION, exitAnchor);
        }

        PipeConnection next = candidates.getFirst();
        int nextDirection = next.directionAwayFrom(exitAnchor);
        return next.allowsSlideDirection(nextDirection)
                ? ContinuationDecision.next(next, nextDirection)
                : ContinuationDecision.barrier(ContinuationDecision.Type.NO_CONTINUATION, exitAnchor);
    }

    private static ContinuationDecision resolveBranch(PipeNetworkView pipeNetwork, TraversalContext context, PipeConnection current, int currentDirection, BranchNode branch) {
        PipeAnchorId branchAnchor = branch.anchorId();
        Optional<PipeConnection> selected;
        if (context.activeRoute()) {
            selected = context.routeLayoutId()
                    .flatMap(layoutId -> context.routeChoiceLookup().routeChoiceForCurrentStep(
                            layoutId,
                            context.routeDirection(),
                            context.currentPlatformStopId(),
                            context.currentRouteSectionId(),
                            context.routeConnectionIndex(),
                            current.id(),
                            branch.id()))
                    .map(RouteChoiceSelection::selectedConnectionId)
                    .flatMap(pipeNetwork::connection)
                    .filter(connection -> validBranchExit(connection, branch, current));
            if (selected.isEmpty()) {
                return ContinuationDecision.barrier(ContinuationDecision.Type.INVALID_TOPOLOGY, branchAnchor);
            }
        } else {
            selected = selectedPendingBranchChoice(pipeNetwork, context, branch, current)
                    .or(() -> context.allowAutomaticBranchChoice() ? foremostConnection(pipeNetwork, branch, current, currentDirection) : Optional.empty())
                    .or(() -> context.allowAutomaticBranchChoice() ? defaultConnection(pipeNetwork, branch, current) : Optional.empty());
            if (selected.isEmpty()) {
                long viableOptions = branch.managedConnectionIdsInOrder().stream()
                        .filter(connectionId -> !connectionId.equals(current.id()))
                        .map(pipeNetwork::connection)
                        .flatMap(Optional::stream)
                        .filter(connection -> validBranchExit(connection, branch, current))
                        .count();
                return ContinuationDecision.barrier(viableOptions >= 2L ? ContinuationDecision.Type.BRANCH_CHOICE_REQUIRED : ContinuationDecision.Type.NO_CONTINUATION, branchAnchor);
            }
        }

        PipeConnection next = selected.get();
        int nextDirection = next.directionAwayFrom(branchAnchor);
        return next.allowsSlideDirection(nextDirection)
                ? ContinuationDecision.next(next, nextDirection)
                : ContinuationDecision.barrier(ContinuationDecision.Type.NO_CONTINUATION, branchAnchor);
    }

    private static Optional<PipeConnection> selectedPendingBranchChoice(PipeNetworkView pipeNetwork, TraversalContext context, BranchNode branch, PipeConnection current) {
        return context.pendingBranchChoiceConnectionId()
                .flatMap(pipeNetwork::connection)
                .filter(connection -> validBranchExit(connection, branch, current));
    }

    private static Optional<PipeConnection> defaultConnection(PipeNetworkView pipeNetwork, BranchNode branch, PipeConnection current) {
        return branch.defaultAlternativeTo(current.id())
                .flatMap(pipeNetwork::connection)
                .filter(connection -> validBranchExit(connection, branch, current));
    }

    private static Optional<PipeConnection> foremostConnection(PipeNetworkView pipeNetwork, BranchNode branch, PipeConnection current, int incomingDirection) {
        Vec3 incoming = safeNormalize(current.tangentAt(incomingDirection > 0 ? current.length() : 0.0D).scale(incomingDirection), new Vec3(0.0D, 0.0D, 1.0D));
        PipeConnection best = null;
        double bestScore = -Double.MAX_VALUE;
        for (UUID connectionId : branch.managedConnectionIdsInOrder()) {
            if (connectionId.equals(current.id())) {
                continue;
            }
            Optional<PipeConnection> candidate = pipeNetwork.connection(connectionId);
            if (candidate.isEmpty() || !validBranchExit(candidate.get(), branch, current)) {
                continue;
            }
            Vec3 candidateDirection = safeNormalize(directionAwayFrom(candidate.get(), branch.anchorId()), incoming);
            double score = incoming.dot(candidateDirection);
            if (best == null || score > bestScore) {
                best = candidate.get();
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean validBranchExit(PipeConnection connection, BranchNode branch, PipeConnection current) {
        if (connection.id().equals(current.id()) || !branch.referencesConnection(connection.id())) {
            return false;
        }
        int direction = connection.directionAwayFrom(branch.anchorId());
        return connection.allowsSlideDirection(direction);
    }

    private static Vec3 directionAwayFrom(PipeConnection connection, PipeAnchorId anchor) {
        Vec3 anchorPosition = Vec3.atCenterOf(anchor.blockPos());
        double length = connection.length();
        if (length < 1.0E-6D) {
            return Vec3.ZERO;
        }
        double lookahead = Math.min(length, Math.max(4.0D, length * 0.35D));
        if (connection.fromAnchor().equals(anchor)) {
            return connection.positionAt(lookahead).subtract(anchorPosition);
        }
        if (connection.toAnchor().equals(anchor)) {
            return connection.positionAt(Math.max(0.0D, length - lookahead)).subtract(anchorPosition);
        }
        return Vec3.ZERO;
    }

    private static Vec3 safeNormalize(Vec3 vector, Vec3 fallback) {
        return vector.lengthSqr() < 1.0E-6D ? fallback : vector.normalize();
    }
}
