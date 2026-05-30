package dev.marblegate.superpipeslide.client.core.slide;

import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class ClientSlideMotion {
    private ClientSlideMotion() {}

    static void freeze(LocalPlayer player) {
        player.noPhysics = true;
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }

    static void apply(LocalPlayer player, PipeConnection connection, double distanceOnConnection, int direction, double speed) {
        double distance = Mth.clamp(distanceOnConnection, 0.0D, connection.length());
        Vec3 tangent = connection.tangentAt(distance).scale(direction);
        Vec3 target = connection.positionAt(distance);
        player.setPos(target.x, target.y, target.z);
        player.noPhysics = true;
        player.setDeltaMovement(Math.abs(speed) <= 1.0E-6D ? Vec3.ZERO : tangent.scale(speed));
        player.setYBodyRot(yawFrom(tangent));
        player.resetFallDistance();
    }

    static void snap(LocalPlayer player, PipeConnection connection, double distanceOnConnection, int direction, double speed) {
        double distance = Mth.clamp(distanceOnConnection, 0.0D, connection.length());
        Vec3 tangent = connection.tangentAt(distance).scale(direction);
        Vec3 target = connection.positionAt(distance);
        player.setPos(target.x, target.y, target.z);
        player.noPhysics = true;
        player.setDeltaMovement(Math.abs(speed) <= 1.0E-6D ? Vec3.ZERO : tangent.scale(speed));
        player.setYBodyRot(yawFrom(tangent));
        player.resetFallDistance();
    }

    private static float yawFrom(Vec3 tangent) {
        return (float) (Mth.atan2(tangent.z, tangent.x) * 180.0F / Math.PI) - 90.0F;
    }
}
