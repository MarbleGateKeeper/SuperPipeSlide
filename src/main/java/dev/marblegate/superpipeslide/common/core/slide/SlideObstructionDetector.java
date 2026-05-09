package dev.marblegate.superpipeslide.common.core.slide;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class SlideObstructionDetector {
    private static final double EPSILON = 1.0E-5D;

    private SlideObstructionDetector() {
    }

    public static boolean isInsideSuffocatingBlock(Level level, AABB box) {
        int minX = Mth.floor(box.minX + EPSILON);
        int minY = Mth.floor(box.minY + EPSILON);
        int minZ = Mth.floor(box.minZ + EPSILON);
        int maxX = Mth.floor(box.maxX - EPSILON);
        int maxY = Mth.floor(box.maxY - EPSILON);
        int maxZ = Mth.floor(box.maxZ - EPSILON);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !state.isSuffocating(level, pos)) {
                        continue;
                    }
                    for (AABB localCollisionBox : state.getCollisionShape(level, pos).toAabbs()) {
                        if (localCollisionBox.move(pos).intersects(box)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
