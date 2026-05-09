package dev.marblegate.superpipeslide.client.renderer.projection;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class ClientProjectionGeometry {
    private static final double WALL_LIFT = 0.012D;

    private ClientProjectionGeometry() {
    }

    public static Vec3 projectionCenter(BlockPos pos, Direction facing, float offsetX, float offsetY) {
        return Vec3.atLowerCornerOf(pos).add(localProjectionCenter(facing, offsetX, offsetY));
    }

    public static void translateToProjectionPlane(PoseStack poseStack, Direction facing, float offsetX, float offsetY) {
        Vec3 center = localProjectionCenter(facing, offsetX, offsetY);
        poseStack.translate(center.x, center.y, center.z);
    }

    private static Vec3 localProjectionCenter(Direction facing, float offsetX, float offsetY) {
        Vec3 normal = facing.getUnitVec3();
        Vec3 right = new Vec3(normal.z, 0.0D, -normal.x);
        return new Vec3(0.5D, 0.5D, 0.5D)
                .subtract(normal.scale(0.5D))
                .add(normal.scale(WALL_LIFT))
                .add(right.scale(offsetX))
                .add(0.0D, offsetY, 0.0D);
    }
}
