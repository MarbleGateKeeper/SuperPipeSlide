package dev.marblegate.superpipeslide.integration.distanthorizons.client;

import java.util.List;
import java.util.Objects;

public record PipeFarLodGroup(PipeFarLodGroupKey key, List<PipeFarLodBox> boxes) {
    public PipeFarLodGroup {
        boxes = List.copyOf(boxes);
    }

    public int contentHash() {
        return Objects.hash(this.key, this.boxes);
    }
}
