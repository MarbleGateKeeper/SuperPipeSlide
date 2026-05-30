package dev.marblegate.superpipeslide.common.core.route.storage;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class RouteSectionWorkQueue {
    private final Set<UUID> dirtyRouteSectionIds = new LinkedHashSet<>();
    private final Set<UUID> repairRouteSectionIds = new LinkedHashSet<>();
    private final Map<UUID, Long> lastRepairAttemptRevisionBySection = new HashMap<>();

    boolean hasWork() {
        return !this.dirtyRouteSectionIds.isEmpty() || !this.repairRouteSectionIds.isEmpty();
    }

    void markDirty(UUID sectionId) {
        this.dirtyRouteSectionIds.add(sectionId);
    }

    void enqueueRepair(UUID sectionId, long pipeRevision) {
        if (this.dirtyRouteSectionIds.contains(sectionId)) {
            return;
        }
        if (this.lastRepairAttemptRevisionBySection.getOrDefault(sectionId, Long.MIN_VALUE) == pipeRevision) {
            return;
        }
        this.repairRouteSectionIds.add(sectionId);
    }

    Optional<WorkItem> poll(long pipeRevision) {
        if (!this.dirtyRouteSectionIds.isEmpty()) {
            UUID sectionId = this.dirtyRouteSectionIds.iterator().next();
            this.dirtyRouteSectionIds.remove(sectionId);
            this.repairRouteSectionIds.remove(sectionId);
            return Optional.of(new WorkItem(sectionId, false));
        }
        if (!this.repairRouteSectionIds.isEmpty()) {
            UUID sectionId = this.repairRouteSectionIds.iterator().next();
            this.repairRouteSectionIds.remove(sectionId);
            this.lastRepairAttemptRevisionBySection.put(sectionId, pipeRevision);
            return Optional.of(new WorkItem(sectionId, true));
        }
        return Optional.empty();
    }

    void remove(UUID sectionId) {
        this.dirtyRouteSectionIds.remove(sectionId);
        this.repairRouteSectionIds.remove(sectionId);
        this.lastRepairAttemptRevisionBySection.remove(sectionId);
    }

    void clearRepairMarker(UUID sectionId) {
        this.lastRepairAttemptRevisionBySection.remove(sectionId);
    }

    record WorkItem(UUID sectionId, boolean repairAttempt) {}
}
