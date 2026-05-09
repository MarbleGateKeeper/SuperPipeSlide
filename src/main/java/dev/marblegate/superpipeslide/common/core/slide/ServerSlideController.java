package dev.marblegate.superpipeslide.common.core.slide;

import dev.marblegate.superpipeslide.common.registry.SPSDamageTypes;
import dev.marblegate.superpipeslide.network.slide.ClientboundSlideTeleportCommitPayload;
import dev.marblegate.superpipeslide.network.slide.ClientboundSlideTeleportFailedPayload;
import dev.marblegate.superpipeslide.network.slide.ServerboundSlideModePayload;
import dev.marblegate.superpipeslide.network.slide.ServerboundSlideTeleportRequestPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ServerSlideController {
    private static final int OBSTRUCTION_GRACE_TICKS = 10;
    private static final int OBSTRUCTION_DAMAGE_INTERVAL_TICKS = 10;
    private static final float OBSTRUCTION_DAMAGE = 2.0F;
    private static final Map<UUID, ServerSlideModeState> SLIDING_PLAYERS = new HashMap<>();

    private ServerSlideController() {
    }

    public static void tick(ServerPlayer player) {
        ServerSlideModeState state = SLIDING_PLAYERS.get(player.getUUID());
        if (state == null) {
            return;
        }
        if (player.isSpectator() || player.getAbilities().flying || !player.isAlive() || player.isPassenger()) {
            clear(player);
            return;
        }
        player.noPhysics = true;
        player.resetFallDistance();
        // Personally it's definitely a terrible implementation...but just leave it here
        // TODO: Maybe one day I'll reconsider it.
        tickObstructionDamage(player, state);
    }

    public static void handleSlideMode(ServerPlayer player, ServerboundSlideModePayload payload) {
        if (payload.sliding()) {
            startSlideMode(player, payload.sessionId());
        } else {
            clear(player, payload.sessionId());
        }
    }

    public static void handleTeleportRequest(ServerPlayer player, ServerboundSlideTeleportRequestPayload payload) {
        ServerLevel currentLevel = player.level() instanceof ServerLevel level ? level : null;
        ServerLevel targetLevel = currentLevel == null ? null : currentLevel.getServer().getLevel(payload.targetLevel());
        if (targetLevel == null) {
            failTeleport(player, payload.sessionId(), "Target dimension is unavailable");
            return;
        }
        if (!isFinite(payload.x()) || !isFinite(payload.y()) || !isFinite(payload.z())) {
            failTeleport(player, payload.sessionId(), "Target position is invalid");
            return;
        }

        ServerSlideModeState state = SLIDING_PLAYERS.get(player.getUUID());
        if (state == null || !state.sessionId().equals(payload.sessionId())) {
            startSlideMode(player, payload.sessionId());
        }

        targetLevel.getChunk(BlockPos.containing(payload.x(), payload.y(), payload.z()));
        player.teleportTo(targetLevel, payload.x(), payload.y(), payload.z(), Set.of(), player.getYRot(), player.getXRot(), true);
        player.noPhysics = true;
        player.resetFallDistance();
        PacketDistributor.sendToPlayer(player, new ClientboundSlideTeleportCommitPayload(
                payload.sessionId(),
                payload.targetLevel(),
                payload.x(),
                payload.y(),
                payload.z(),
                payload.targetConnectionId(),
                payload.direction(),
                payload.distanceOnConnection(),
                payload.speed()
        ));
    }

    public static void stop(ServerPlayer player) {
        clear(player, null);
    }

    public static void clear(ServerPlayer player) {
        clear(player, null);
    }

    private static void clear(ServerPlayer player, UUID expectedSessionId) {
        ServerSlideModeState state = SLIDING_PLAYERS.remove(player.getUUID());
        if (state != null && expectedSessionId != null && !state.sessionId().equals(expectedSessionId)) {
            SLIDING_PLAYERS.put(player.getUUID(), state);
            return;
        }
        if (state != null) {
            player.noPhysics = state.previousNoPhysics();
            player.resetFallDistance();
        }
    }

    public static void clearAllSessions() {
        SLIDING_PLAYERS.clear();
    }

    public static boolean isSliding(ServerPlayer player) {
        return SLIDING_PLAYERS.containsKey(player.getUUID());
    }

    private static void startSlideMode(ServerPlayer player, UUID sessionId) {
        ServerSlideModeState previous = SLIDING_PLAYERS.get(player.getUUID());
        boolean previousNoPhysics = previous == null ? player.noPhysics : previous.previousNoPhysics();
        SLIDING_PLAYERS.put(player.getUUID(), new ServerSlideModeState(sessionId, previousNoPhysics, 0, 0));
        player.noPhysics = true;
        player.resetFallDistance();
    }

    private static void tickObstructionDamage(ServerPlayer player, ServerSlideModeState state) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!SlideObstructionDetector.isInsideSuffocatingBlock(level, player.getBoundingBox())) {
            SLIDING_PLAYERS.put(player.getUUID(), state.withObstruction(0, 0));
            return;
        }

        int obstructionTicks = state.obstructionTicks() + 1;
        int damageCooldownTicks = Math.max(0, state.damageCooldownTicks() - 1);
        if (obstructionTicks >= OBSTRUCTION_GRACE_TICKS && damageCooldownTicks <= 0) {
            player.hurtServer(level, SPSDamageTypes.pipeSuffocation(level), OBSTRUCTION_DAMAGE);
            damageCooldownTicks = OBSTRUCTION_DAMAGE_INTERVAL_TICKS;
        }
        SLIDING_PLAYERS.put(player.getUUID(), state.withObstruction(obstructionTicks, damageCooldownTicks));
    }

    private static void failTeleport(ServerPlayer player, UUID sessionId, String reason) {
        PacketDistributor.sendToPlayer(player, new ClientboundSlideTeleportFailedPayload(sessionId, reason));
        clear(player, sessionId);
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private record ServerSlideModeState(UUID sessionId, boolean previousNoPhysics, int obstructionTicks, int damageCooldownTicks) {
        private ServerSlideModeState {
            sessionId = Optional.ofNullable(sessionId).orElseGet(UUID::randomUUID);
            obstructionTicks = Math.max(0, obstructionTicks);
            damageCooldownTicks = Math.max(0, damageCooldownTicks);
        }

        private ServerSlideModeState withObstruction(int obstructionTicks, int damageCooldownTicks) {
            return new ServerSlideModeState(this.sessionId, this.previousNoPhysics, obstructionTicks, damageCooldownTicks);
        }
    }
}
