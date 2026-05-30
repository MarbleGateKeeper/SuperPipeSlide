package dev.marblegate.superpipeslide.integration.distanthorizons.client;

import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record PipeFarLodSnapshot(ResourceKey<Level> levelKey, long networkRevision, long appearanceRevision,
        List<PipeFarLodGroup> groups) {
    public PipeFarLodSnapshot {
        groups = List.copyOf(groups);
    }
}
