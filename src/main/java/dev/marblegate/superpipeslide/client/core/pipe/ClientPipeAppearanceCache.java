package dev.marblegate.superpipeslide.client.core.pipe;

import dev.marblegate.superpipeslide.common.core.appearance.model.PipeAppearanceAssignment;
import dev.marblegate.superpipeslide.common.core.appearance.model.PipeAppearanceProfile;
import dev.marblegate.superpipeslide.network.pipe.appearance.ClientboundPipeAppearanceSyncPayload;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClientPipeAppearanceCache {
    private static final Map<Integer, PipeAppearanceProfile> PROFILES_BY_ID = new LinkedHashMap<>();
    private static final Map<Integer, Integer> PROFILE_IDS_BY_CONNECTION_KEY = new LinkedHashMap<>();
    private static final Set<Integer> RENDER_CHANGED_CONNECTION_KEYS = new LinkedHashSet<>();
    private static long revision = -1L;
    private static boolean renderFullInvalidation;

    static {
        PROFILES_BY_ID.put(PipeAppearanceProfile.DEFAULT_PROFILE_ID, PipeAppearanceProfile.defaultProfile().normalizedToDefinitions());
    }

    private ClientPipeAppearanceCache() {}

    public static void handleSync(ClientboundPipeAppearanceSyncPayload payload) {
        if (payload.full()) {
            PROFILES_BY_ID.clear();
            PROFILE_IDS_BY_CONNECTION_KEY.clear();
            PROFILES_BY_ID.put(PipeAppearanceProfile.DEFAULT_PROFILE_ID, PipeAppearanceProfile.defaultProfile().normalizedToDefinitions());
            PipeCoatingRenderResolver.clear();
            markRenderFullInvalidation();
        }
        for (PipeAppearanceProfile profile : payload.profiles()) {
            PipeAppearanceProfile normalized = profile.normalizedToDefinitions();
            PROFILES_BY_ID.put(normalized.profileId(), normalized);
        }
        for (PipeAppearanceAssignment assignment : payload.assignments()) {
            if (assignment.profileId() <= PipeAppearanceProfile.DEFAULT_PROFILE_ID) {
                PROFILE_IDS_BY_CONNECTION_KEY.remove(assignment.connectionKey());
            } else {
                PROFILE_IDS_BY_CONNECTION_KEY.put(assignment.connectionKey(), assignment.profileId());
            }
            markConnectionKeyChangedForRender(assignment.connectionKey());
        }
        for (int connectionKey : payload.clearedConnectionKeys()) {
            PROFILE_IDS_BY_CONNECTION_KEY.remove(connectionKey);
            markConnectionKeyChangedForRender(connectionKey);
        }
        revision = payload.revision();
    }

    public static PipeAppearanceProfile profileFor(int connectionKey) {
        Integer profileId = PROFILE_IDS_BY_CONNECTION_KEY.get(connectionKey);
        if (profileId == null) {
            return PipeAppearanceProfile.defaultProfile().normalizedToDefinitions();
        }
        return PROFILES_BY_ID.getOrDefault(profileId, PipeAppearanceProfile.defaultProfile().normalizedToDefinitions());
    }

    public static long revision() {
        return revision;
    }

    public static void clear() {
        PROFILES_BY_ID.clear();
        PROFILE_IDS_BY_CONNECTION_KEY.clear();
        PROFILES_BY_ID.put(PipeAppearanceProfile.DEFAULT_PROFILE_ID, PipeAppearanceProfile.defaultProfile().normalizedToDefinitions());
        PipeCoatingRenderResolver.clear();
        revision = -1L;
        markRenderFullInvalidation();
    }

    public static PipeAppearanceRenderInvalidation consumeRenderInvalidation() {
        PipeAppearanceRenderInvalidation invalidation = new PipeAppearanceRenderInvalidation(
                renderFullInvalidation,
                List.copyOf(RENDER_CHANGED_CONNECTION_KEYS));
        renderFullInvalidation = false;
        RENDER_CHANGED_CONNECTION_KEYS.clear();
        return invalidation;
    }

    private static void markRenderFullInvalidation() {
        renderFullInvalidation = true;
        RENDER_CHANGED_CONNECTION_KEYS.clear();
    }

    private static void markConnectionKeyChangedForRender(int connectionKey) {
        if (renderFullInvalidation) {
            return;
        }
        RENDER_CHANGED_CONNECTION_KEYS.add(connectionKey);
    }

    public record PipeAppearanceRenderInvalidation(boolean full, List<Integer> changedConnectionKeys) {
        public PipeAppearanceRenderInvalidation {
            changedConnectionKeys = List.copyOf(changedConnectionKeys);
        }

        public boolean isEmpty() {
            return !this.full && this.changedConnectionKeys.isEmpty();
        }
    }
}
