package dev.marblegate.superpipeslide.common.core.appearance.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.marblegate.superpipeslide.common.core.appearance.model.PipeAppearanceAssignment;
import dev.marblegate.superpipeslide.common.core.appearance.model.PipeAppearanceProfile;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionUtils;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkSavedData;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.network.pipe.appearance.ClientboundPipeAppearanceSyncPayload;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class PipeAppearanceSavedData extends SavedData {
    private static final int MAX_CONNECTED_APPLY = 4096;

    public static final Codec<PipeAppearanceSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PipeAppearanceProfile.CODEC.listOf().optionalFieldOf("profiles", List.of()).forGetter(PipeAppearanceSavedData::profilesForCodec),
            PipeAppearanceAssignment.CODEC.listOf().optionalFieldOf("assignments", List.of()).forGetter(PipeAppearanceSavedData::assignmentsForCodec),
            Codec.LONG.optionalFieldOf("revision", 0L).forGetter(PipeAppearanceSavedData::revision),
            Codec.INT.optionalFieldOf("next_profile_id", 1).forGetter(PipeAppearanceSavedData::nextProfileIdForCodec)
    ).apply(instance, PipeAppearanceSavedData::new));

    public static final SavedDataType<PipeAppearanceSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipe_appearance"),
            PipeAppearanceSavedData::new,
            CODEC
    );

    private final Map<Integer, PipeAppearanceProfile> profilesById = new LinkedHashMap<>();
    private final Map<String, Integer> profileIdsByContent = new HashMap<>();
    private final Map<Integer, Integer> assignmentsByConnectionKey = new LinkedHashMap<>();
    private final Map<Integer, PipeAppearanceProfile> pendingProfiles = new LinkedHashMap<>();
    private final Map<Integer, Integer> pendingAssignments = new LinkedHashMap<>();
    private final Set<Integer> pendingClearedAssignments = new LinkedHashSet<>();
    private long revision;
    private int nextProfileId;
    private int batchDepth;
    private boolean batchRevisionDirty;

    public PipeAppearanceSavedData() {
        this(List.of(), List.of(), 0L, 1);
    }

    public PipeAppearanceSavedData(List<PipeAppearanceProfile> profiles, List<PipeAppearanceAssignment> assignments, long revision, int nextProfileId) {
        this.revision = revision;
        this.nextProfileId = Math.max(1, nextProfileId);
        this.profilesById.put(PipeAppearanceProfile.DEFAULT_PROFILE_ID, PipeAppearanceProfile.defaultProfile().normalizedToDefinitions());
        for (PipeAppearanceProfile profile : profiles) {
            PipeAppearanceProfile normalized = profile.normalizedToDefinitions();
            if (normalized.profileId() <= PipeAppearanceProfile.DEFAULT_PROFILE_ID || normalized.isDefaultAppearance()) {
                continue;
            }
            this.profilesById.put(normalized.profileId(), normalized);
            this.nextProfileId = Math.max(this.nextProfileId, normalized.profileId() + 1);
        }
        this.rebuildProfileContentIndex();
        for (PipeAppearanceAssignment assignment : assignments) {
            if (assignment.connectionKey() <= PipeConnection.TRANSIENT_CONNECTION_KEY || assignment.profileId() <= PipeAppearanceProfile.DEFAULT_PROFILE_ID) {
                continue;
            }
            if (this.profilesById.containsKey(assignment.profileId())) {
                this.assignmentsByConnectionKey.put(assignment.connectionKey(), assignment.profileId());
            }
        }
    }

    public static PipeAppearanceSavedData get(ServerLevel level) {
        return get(level.getServer());
    }

    public static PipeAppearanceSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public long revision() {
        return this.revision;
    }

    public PipeAppearanceProfile profileForConnectionKey(int connectionKey) {
        Integer profileId = this.assignmentsByConnectionKey.get(connectionKey);
        return profileId == null ? PipeAppearanceProfile.defaultProfile() : this.profilesById.getOrDefault(profileId, PipeAppearanceProfile.defaultProfile());
    }

    public Optional<PipeAppearanceProfile> assignedProfile(int connectionKey) {
        Integer profileId = this.assignmentsByConnectionKey.get(connectionKey);
        return profileId == null ? Optional.empty() : Optional.ofNullable(this.profilesById.get(profileId));
    }

    public ApplyResult applySingle(PipeNetworkSavedData pipeData, int connectionKey, PipeAppearanceProfile draft) {
        if (pipeData.connectionByKey(connectionKey).isEmpty()) {
            return ApplyResult.failure("Pipe connection no longer exists");
        }
        boolean changed = this.assign(connectionKey, draft);
        return ApplyResult.success(changed ? 1 : 0, this.revision);
    }

    public ApplyResult applyConnected(PipeNetworkSavedData pipeData, int connectionKey, PipeAppearanceProfile draft) {
        Optional<PipeConnection> target = pipeData.connectionByKey(connectionKey);
        if (target.isEmpty()) {
            return ApplyResult.failure("Pipe connection no longer exists");
        }
        Set<Integer> keys = connectedConnectionKeys(pipeData, target.get());
        int changed = 0;
        try (RevisionBatch ignored = this.beginRevisionBatch()) {
            for (int key : keys) {
                if (this.assign(key, draft)) {
                    changed++;
                }
            }
        }
        return ApplyResult.success(changed, this.revision);
    }

    public boolean pruneMissingConnections(PipeNetworkSavedData pipeData) {
        List<Integer> removed = this.assignmentsByConnectionKey.keySet().stream()
                .filter(key -> pipeData.connectionByKey(key).isEmpty())
                .toList();
        if (removed.isEmpty()) {
            return false;
        }
        try (RevisionBatch ignored = this.beginRevisionBatch()) {
            removed.forEach(this::clearAssignment);
            this.compactUnusedProfiles();
        }
        return true;
    }

    public ClientboundPipeAppearanceSyncPayload fullSyncPayload() {
        return new ClientboundPipeAppearanceSyncPayload(
                this.revision,
                profilesForSync(),
                assignmentsForSync(),
                List.of(),
                true
        );
    }

    public ClientboundPipeAppearanceSyncPayload consumePendingSyncPayload() {
        ClientboundPipeAppearanceSyncPayload payload = new ClientboundPipeAppearanceSyncPayload(
                this.revision,
                List.copyOf(this.pendingProfiles.values()),
                this.pendingAssignments.entrySet().stream()
                        .map(entry -> new PipeAppearanceAssignment(entry.getKey(), entry.getValue()))
                        .toList(),
                List.copyOf(this.pendingClearedAssignments),
                false
        );
        this.pendingProfiles.clear();
        this.pendingAssignments.clear();
        this.pendingClearedAssignments.clear();
        return payload;
    }

    public boolean hasPendingSync() {
        return !this.pendingProfiles.isEmpty() || !this.pendingAssignments.isEmpty() || !this.pendingClearedAssignments.isEmpty();
    }

    private boolean assign(int connectionKey, PipeAppearanceProfile draft) {
        if (connectionKey <= PipeConnection.TRANSIENT_CONNECTION_KEY) {
            return false;
        }
        PipeAppearanceProfile normalized = draft.normalizedToDefinitions();
        if (normalized.isDefaultAppearance()) {
            return this.clearAssignment(connectionKey);
        }
        int profileId = this.internProfile(normalized);
        Integer previous = this.assignmentsByConnectionKey.put(connectionKey, profileId);
        if (previous != null && previous == profileId) {
            return false;
        }
        this.pendingAssignments.put(connectionKey, profileId);
        this.pendingClearedAssignments.remove(connectionKey);
        this.bumpRevision();
        return true;
    }

    private boolean clearAssignment(int connectionKey) {
        Integer removed = this.assignmentsByConnectionKey.remove(connectionKey);
        if (removed == null) {
            return false;
        }
        this.pendingAssignments.remove(connectionKey);
        this.pendingClearedAssignments.add(connectionKey);
        this.bumpRevision();
        return true;
    }

    private int internProfile(PipeAppearanceProfile profile) {
        PipeAppearanceProfile normalized = profile.normalizedToDefinitions().withoutServerId();
        Integer existing = this.profileIdsByContent.get(normalized.contentKey());
        if (existing != null) {
            return existing;
        }
        int profileId = this.nextProfileId++;
        PipeAppearanceProfile stored = normalized.withProfileId(profileId);
        this.profilesById.put(profileId, stored);
        this.profileIdsByContent.put(stored.withoutServerId().contentKey(), profileId);
        this.pendingProfiles.put(profileId, stored);
        return profileId;
    }

    private void compactUnusedProfiles() {
        Set<Integer> usedProfileIds = new HashSet<>(this.assignmentsByConnectionKey.values());
        usedProfileIds.add(PipeAppearanceProfile.DEFAULT_PROFILE_ID);
        if (this.profilesById.keySet().removeIf(profileId -> !usedProfileIds.contains(profileId))) {
            this.rebuildProfileContentIndex();
            this.setDirty();
        }
    }

    private void bumpRevision() {
        if (this.batchDepth > 0) {
            this.batchRevisionDirty = true;
            return;
        }
        this.bumpRevisionNow();
    }

    private void bumpRevisionNow() {
        this.revision++;
        this.setDirty();
    }

    private RevisionBatch beginRevisionBatch() {
        this.batchDepth++;
        return new RevisionBatch();
    }

    private void endRevisionBatch() {
        if (this.batchDepth <= 0) {
            return;
        }
        this.batchDepth--;
        if (this.batchDepth > 0 || !this.batchRevisionDirty) {
            return;
        }
        this.batchRevisionDirty = false;
        this.bumpRevisionNow();
    }

    private final class RevisionBatch implements AutoCloseable {
        private boolean closed;

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            PipeAppearanceSavedData.this.endRevisionBatch();
            this.closed = true;
        }
    }

    private void rebuildProfileContentIndex() {
        this.profileIdsByContent.clear();
        this.profilesById.values().stream()
                .filter(profile -> profile.profileId() > PipeAppearanceProfile.DEFAULT_PROFILE_ID)
                .forEach(profile -> this.profileIdsByContent.put(profile.withoutServerId().contentKey(), profile.profileId()));
    }

    private List<PipeAppearanceProfile> profilesForCodec() {
        return this.profilesById.values().stream()
                .filter(profile -> profile.profileId() > PipeAppearanceProfile.DEFAULT_PROFILE_ID)
                .toList();
    }

    private List<PipeAppearanceAssignment> assignmentsForCodec() {
        return assignmentsForSync();
    }

    private List<PipeAppearanceProfile> profilesForSync() {
        return List.copyOf(this.profilesById.values());
    }

    private List<PipeAppearanceAssignment> assignmentsForSync() {
        return this.assignmentsByConnectionKey.entrySet().stream()
                .map(entry -> new PipeAppearanceAssignment(entry.getKey(), entry.getValue()))
                .toList();
    }

    private int nextProfileIdForCodec() {
        return this.nextProfileId;
    }

    private static Set<Integer> connectedConnectionKeys(PipeNetworkSavedData pipeData, PipeConnection target) {
        Set<Integer> result = new LinkedHashSet<>();
        Set<PipeAnchorId> visitedAnchors = new HashSet<>();
        ArrayDeque<PipeConnection> queue = new ArrayDeque<>();
        queue.add(target);
        while (!queue.isEmpty() && result.size() < MAX_CONNECTED_APPLY) {
            PipeConnection connection = queue.removeFirst();
            if (connection.connectionKey() <= PipeConnection.TRANSIENT_CONNECTION_KEY || !result.add(connection.connectionKey())) {
                continue;
            }
            enqueueNeighborConnections(pipeData, connection.fromAnchor(), visitedAnchors, queue, result);
            enqueueNeighborConnections(pipeData, connection.toAnchor(), visitedAnchors, queue, result);
        }
        return result;
    }

    private static void enqueueNeighborConnections(PipeNetworkSavedData pipeData, PipeAnchorId anchorId, Set<PipeAnchorId> visitedAnchors, ArrayDeque<PipeConnection> queue, Set<Integer> alreadyQueued) {
        if (!visitedAnchors.add(anchorId) || pipeData.isSpecialAnchorNode(anchorId)) {
            return;
        }
        for (PipeConnection neighbor : pipeData.connectionsTouching(anchorId)) {
            if (!alreadyQueued.contains(neighbor.connectionKey()) && PipeConnectionUtils.targetFor(neighbor, anchorId).isPresent()) {
                queue.add(neighbor);
            }
        }
    }

    public record ApplyResult(boolean accepted, String message, int changedConnections, long revision) {
        public static ApplyResult success(int changedConnections, long revision) {
            String message = changedConnections <= 0 ? "Appearance already matches" : "Applied appearance to " + changedConnections + " pipe connection(s)";
            return new ApplyResult(true, message, changedConnections, revision);
        }

        public static ApplyResult failure(String message) {
            return new ApplyResult(false, message, 0, 0L);
        }
    }
}
