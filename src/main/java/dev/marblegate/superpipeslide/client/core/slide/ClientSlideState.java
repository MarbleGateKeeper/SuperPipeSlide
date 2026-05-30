package dev.marblegate.superpipeslide.client.core.slide;

import java.util.UUID;

public record ClientSlideState(
        UUID sessionId,
        UUID startedConnectionId,
        UUID connectionId,
        int direction,
        double distanceOnConnection,
        double speed,
        double temporarySpeedCapRestoreSpeed,
        int ticksSliding,
        boolean previousNoPhysics) {

    private static final double NO_TEMPORARY_SPEED_CAP_RESTORE_SPEED = -1.0D;
    static ClientSlideState start(UUID sessionId, UUID connectionId, int direction, double distanceOnConnection, double speed, boolean previousNoPhysics) {
        return new ClientSlideState(sessionId, connectionId, connectionId, direction, distanceOnConnection, speed, NO_TEMPORARY_SPEED_CAP_RESTORE_SPEED, 0, previousNoPhysics);
    }

    ClientSlideState advance(UUID connectionId, int direction, double distanceOnConnection, double speed) {
        return new ClientSlideState(this.sessionId, this.startedConnectionId, connectionId, direction, distanceOnConnection, speed, this.temporarySpeedCapRestoreSpeed, this.ticksSliding + 1, this.previousNoPhysics);
    }

    ClientSlideState withSpeed(double speed) {
        return new ClientSlideState(this.sessionId, this.startedConnectionId, this.connectionId, this.direction, this.distanceOnConnection, speed, this.temporarySpeedCapRestoreSpeed, this.ticksSliding, this.previousNoPhysics);
    }

    ClientSlideState rememberTemporarySpeedCapRestoreSpeed(double speed) {
        if (this.hasTemporarySpeedCapRestoreSpeed()) {
            return this;
        }
        return new ClientSlideState(this.sessionId, this.startedConnectionId, this.connectionId, this.direction, this.distanceOnConnection, this.speed, speed, this.ticksSliding, this.previousNoPhysics);
    }

    ClientSlideState clearTemporarySpeedCapRestoreSpeed() {
        if (!this.hasTemporarySpeedCapRestoreSpeed()) {
            return this;
        }
        return new ClientSlideState(this.sessionId, this.startedConnectionId, this.connectionId, this.direction, this.distanceOnConnection, this.speed, NO_TEMPORARY_SPEED_CAP_RESTORE_SPEED, this.ticksSliding, this.previousNoPhysics);
    }

    boolean hasTemporarySpeedCapRestoreSpeed() {
        return this.temporarySpeedCapRestoreSpeed >= 0.0D;
    }
}
