package dev.marblegate.superpipeslide.client.fullmap.model;

import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record NodeId(NodeKind kind, ResourceKey<Level> levelKey, UUID primaryId, int occurrence) implements Comparable<NodeId> {
    @Override
    public int compareTo(NodeId other) {
        int kindCompare = this.kind.compareTo(other.kind);
        if (kindCompare != 0) {
            return kindCompare;
        }
        int levelCompare = this.levelKey.identifier().toString().compareTo(other.levelKey.identifier().toString());
        if (levelCompare != 0) {
            return levelCompare;
        }
        int idCompare = this.primaryId.compareTo(other.primaryId);
        return idCompare != 0 ? idCompare : Integer.compare(this.occurrence, other.occurrence);
    }
}
