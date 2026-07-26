package dev.marblegate.superpipeslide.client.core.slide;

import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Scene probe for the cinematic director: every 0.5s it samples the space around the
 * rider (and ahead along the actual pipe path) and distills it into an immutable
 * SceneFeatures snapshot. It deliberately measures the SHAPE of space — sky ratio,
 * corridor anisotropy, forward depth, depth layering, building edges — instead of a
 * single isotropic "openness" scalar, so streets, trenches, canyons, and building
 * sides are classified by the framings they actually support.
 */
public final class ClientSceneProbe {
    private static final int RING_DIRECTIONS = 16;
    private static final double RING_MAX_DISTANCE = 28.0D;
    private static final double SKY_MAX_DISTANCE = 32.0D;
    private static final double FORWARD_MAX_DISTANCE = 30.0D;
    private static final double EDGE_MIN_WALL = 4.0D;
    private static final double EDGE_MAX_WALL = 20.0D;
    private static final double[] EDGE_HEIGHTS = { 2.0D, 4.0D, 7.0D, 10.0D };

    private ClientSceneProbe() {}

    public record EdgePoint(Vec3 topPos, Vec3 outwardDir, double wallHeight) {}

    public record SceneFeatures(
            double openness,
            double skyOpenness,
            double forwardDepth,
            double forwardBreadth,
            double layerScore,
            Vec3 axisDirection,
            double axisLength,
            double axisRatio,
            List<EdgePoint> edgeTops,
            double[] ringDistances) {}

    public enum SceneShape {
        FIELD, CORRIDOR, BACKDROP, WELL, EDGE, INTERIOR
    }

    public record SceneAssessment(SceneShape shape, double skyOpenness, double forwardDepth, double layerScore, Vec3 axisDirection, double axisLength, List<EdgePoint> edgeTops) {}

    public static SceneFeatures sample(Level level, LocalPlayer player, ClientSlideFeedbackController.Frame snapshot) {
        Vec3 rider = snapshot.position();
        Vec3 travel = safeNormalize(snapshot.tangent());

        // 1) 16-direction horizontal ring
        double[] ring = new double[RING_DIRECTIONS];
        for (int i = 0; i < RING_DIRECTIONS; i++) {
            double angle = Math.toRadians(i * 360.0D / RING_DIRECTIONS);
            ring[i] = freeDistance(level, rider, new Vec3(Math.cos(angle), 0.0D, Math.sin(angle)), RING_MAX_DISTANCE, player);
        }
        double openness = 0.0D;
        for (double d : ring) {
            openness += d;
        }
        openness /= RING_DIRECTIONS;

        // Axis detection: opposite pairs must both be open
        double axisLength = 0.0D;
        double axisRatio = 0.0D;
        Vec3 axisDirection = null;
        double transverseSum = 0.0D;
        int transverseCount = 0;
        for (int i = 0; i < RING_DIRECTIONS / 2; i++) {
            double pair = Math.min(ring[i], ring[i + RING_DIRECTIONS / 2]);
            if (pair > axisLength) {
                axisLength = pair;
                axisDirection = new Vec3(Math.cos(Math.toRadians(i * 360.0D / RING_DIRECTIONS)), 0.0D, Math.sin(Math.toRadians(i * 360.0D / RING_DIRECTIONS)));
                if (ring[i + RING_DIRECTIONS / 2] > ring[i]) {
                    axisDirection = axisDirection.scale(-1.0D);
                }
            }
        }
        if (axisDirection != null) {
            for (int i = 0; i < RING_DIRECTIONS; i++) {
                double angle = Math.toRadians(i * 360.0D / RING_DIRECTIONS);
                Vec3 direction = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
                if (Math.abs(direction.dot(axisDirection)) < 0.5D) {
                    transverseSum += ring[i];
                    transverseCount++;
                }
            }
            double transverse = transverseCount > 0 ? transverseSum / transverseCount : 0.0D;
            axisRatio = axisLength / Math.max(1.0D, transverse);
        }

        // 2) Sky fan (skip in ceiling dimensions)
        double skyOpenness = 0.0D;
        if (!level.dimensionType().hasCeiling()) {
            Vec3 eye = rider.add(0.0D, 0.9D, 0.0D);
            double fullClear = 0.0D;
            double meanUp = 0.0D;
            meanUp += freeDistance(level, eye, new Vec3(0.0D, 1.0D, 0.0D), SKY_MAX_DISTANCE, player);
            for (int i = 0; i < 4; i++) {
                double angle = Math.toRadians(i * 90.0D);
                Vec3 direction = new Vec3(Math.cos(angle) * 0.7D, 0.7D, Math.sin(angle) * 0.7D).normalize();
                meanUp += freeDistance(level, eye, direction, SKY_MAX_DISTANCE, player);
            }
            meanUp /= 5.0D;
            fullClear = meanUp >= 31.5D / SKY_MAX_DISTANCE ? 1.0D : meanUp / 31.5D;
            skyOpenness = 0.5D * fullClear + 0.5D * (meanUp / SKY_MAX_DISTANCE);
        }

        // 3) Forward observation along the real pipe path
        double speedBps = snapshot.speed() * 20.0D;
        double[] leads = {
                clamp(speedBps * 0.8D, 6.0D, 8.0D),
                clamp(speedBps * 1.6D, 15.0D, 18.0D),
                clamp(speedBps * 2.6D, 27.0D, 30.0D) };
        double forwardDepth = 0.0D;
        double forwardBreadth = 0.0D;
        double[] weights = { 0.5D, 0.3D, 0.2D };
        List<Double> hitDistances = new ArrayList<>();
        for (double d : ring) {
            hitDistances.add(d / RING_MAX_DISTANCE);
        }
        for (int i = 0; i < leads.length; i++) {
            Vec3 point = forwardPoint(level, snapshot, travel, leads[i]);
            double forward = freeDistance(level, point, travel, FORWARD_MAX_DISTANCE, player);
            Vec3 left = travel.cross(new Vec3(0.0D, 1.0D, 0.0D));
            left = left.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : left.normalize();
            double leftFree = freeDistance(level, point, left.scale(-1.0D), 24.0D, player);
            double rightFree = freeDistance(level, point, left, 24.0D, player);
            double upFree = freeDistance(level, point, new Vec3(0.0D, 1.0D, 0.0D), SKY_MAX_DISTANCE, player);
            forwardDepth += weights[i] * Math.min(forward, FORWARD_MAX_DISTANCE) / FORWARD_MAX_DISTANCE;
            forwardBreadth += weights[i] * (leftFree + rightFree) / 48.0D;
            hitDistances.add(Math.min(forward, FORWARD_MAX_DISTANCE) / FORWARD_MAX_DISTANCE);
            hitDistances.add(upFree / SKY_MAX_DISTANCE);
        }

        // 4) Depth layering from every hit so far (zero new rays)
        double layerScore = depthLayerScore(hitDistances);

        // 5) Building edge detection (walls from the ring, at most one per quadrant)
        List<EdgePoint> edgeTops = new ArrayList<>();
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            int bestIndex = -1;
            double bestDistance = Double.MAX_VALUE;
            for (int i = quadrant * 4; i < quadrant * 4 + 4; i++) {
                if (ring[i] >= EDGE_MIN_WALL && ring[i] <= EDGE_MAX_WALL && ring[i] < bestDistance) {
                    bestDistance = ring[i];
                    bestIndex = i;
                }
            }
            if (bestIndex < 0) {
                continue;
            }
            double angle = Math.toRadians(bestIndex * 360.0D / RING_DIRECTIONS);
            Vec3 outward = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            Vec3 wallTop = rider.add(outward.scale(bestDistance - 0.6D));
            double ceilingFree = freeDistance(level, wallTop, new Vec3(0.0D, 1.0D, 0.0D), 24.0D, player);
            if (ceilingFree < 24.0D) {
                continue; // ceiling above the wall: interior corridor, not a skyline edge
            }
            double topY = wallTop.y;
            for (double height : EDGE_HEIGHTS) {
                Vec3 candidate = new Vec3(wallTop.x, wallTop.y + height, wallTop.z);
                if (level.noCollision(new net.minecraft.world.phys.AABB(candidate.x - 0.3D, candidate.y - 0.3D, candidate.z - 0.3D, candidate.x + 0.3D, candidate.y + 0.3D, candidate.z + 0.3D))) {
                    topY = wallTop.y + height;
                }
            }
            edgeTops.add(new EdgePoint(new Vec3(wallTop.x, topY, wallTop.z), outward, topY - rider.y));
        }

        return new SceneFeatures(openness, skyOpenness, forwardDepth, forwardBreadth, layerScore, axisDirection, axisLength, axisRatio, List.copyOf(edgeTops), ring);
    }

    /**
     * Classifies the snapshot into a scene shape, which drives the framing grammar.
     * The openness scalar only gates feasibility; it no longer decides how we shoot.
     */
    public static SceneAssessment assess(SceneFeatures features) {
        double transverse = features.axisRatio > 0.0D ? features.axisLength / features.axisRatio : features.openness;
        boolean corridor = features.axisLength >= 16.0D && features.axisRatio >= 2.2D;
        boolean openSky = features.skyOpenness >= 0.7D;

        SceneShape shape;
        if (!corridor && transverse >= 16.0D && features.axisLength >= 16.0D && openSky) {
            shape = SceneShape.FIELD;
        } else if (corridor && openSky) {
            shape = SceneShape.CORRIDOR;
        } else if (!features.edgeTops.isEmpty() && openSky && features.edgeTops.stream().anyMatch(edge -> edge.wallHeight() >= 4.0D)) {
            shape = SceneShape.EDGE;
        } else if (features.openness <= 6.0D) {
            shape = SceneShape.INTERIOR;
        } else if (transverse <= 6.0D && openSky && groundDepthIsShallow(features)) {
            shape = SceneShape.WELL;
        } else if (corridor) {
            shape = SceneShape.CORRIDOR;
        } else if (features.openness >= 12.0D) {
            shape = features.edgeTops.isEmpty() ? SceneShape.BACKDROP : SceneShape.EDGE;
        } else {
            shape = SceneShape.INTERIOR;
        }
        return new SceneAssessment(shape, features.skyOpenness, features.forwardDepth, features.layerScore, features.axisDirection, features.axisLength, features.edgeTops);
    }

    private static boolean groundDepthIsShallow(SceneFeatures features) {
        return features.forwardDepth < 0.6D;
    }

    private static Vec3 forwardPoint(Level level, ClientSlideFeedbackController.Frame snapshot, Vec3 travel, double lead) {
        Optional<dev.marblegate.superpipeslide.common.core.geometry.PipeConnection> connection = ClientPipeNetworkCache.globalConnection(snapshot.connectionId());
        if (connection.isPresent()) {
            return connection.get().positionAt(Mth.clamp(snapshot.distanceOnConnection() + lead, 0.0D, snapshot.connectionLength()));
        }
        return snapshot.position().add(travel.scale(lead));
    }

    private static double depthLayerScore(List<Double> normalizedHits) {
        if (normalizedHits.isEmpty()) {
            return 0.0D;
        }
        double mean = normalizedHits.stream().mapToDouble(Double::doubleValue).average().orElse(0.0D);
        double variance = 0.0D;
        for (double hit : normalizedHits) {
            variance += (hit - mean) * (hit - mean);
        }
        variance /= normalizedHits.size();
        double layerVar = Mth.clamp(variance / (1.0D / 12.0D), 0.0D, 1.0D);
        long nearCount = normalizedHits.stream().filter(hit -> hit < 0.15D).count();
        double nearFraction = Math.min(1.0D, nearCount / (0.15D * normalizedHits.size()));
        return 0.7D * layerVar + 0.3D * nearFraction;
    }

    static Vec3 safeNormalize(Vec3 vector) {
        return vector.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : vector.normalize();
    }

    static double freeDistance(Level level, Vec3 from, Vec3 direction, double maxDistance, LocalPlayer player) {
        Vec3 to = from.add(direction.normalize().scale(maxDistance));
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? maxDistance : from.distanceTo(hit.getLocation());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
