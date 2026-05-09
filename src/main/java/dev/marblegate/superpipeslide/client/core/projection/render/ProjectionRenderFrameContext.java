package dev.marblegate.superpipeslide.client.core.projection.render;


import dev.marblegate.superpipeslide.client.core.projection.cache.ProjectionNetworkImageCache;
public final class ProjectionRenderFrameContext {
    private static final ThreadLocal<FrameState> FRAME_STATE = new ThreadLocal<>();

    private ProjectionRenderFrameContext() {
    }

    public static void begin(long frameTimeMillis) {
        FRAME_STATE.set(new FrameState(frameTimeMillis));
    }

    public static long timeMillis() {
        FrameState value = FRAME_STATE.get();
        return value == null ? System.currentTimeMillis() : value.frameTimeMillis();
    }

    public static ProjectionNetworkImageCache.State networkImageState(String url) {
        FrameState value = FRAME_STATE.get();
        if (value == null) {
            return ProjectionNetworkImageCache.state(url);
        }
        String key = url == null ? "" : url;
        return value.networkImageStates().computeIfAbsent(key, ProjectionNetworkImageCache::state);
    }

    public static void end() {
        FRAME_STATE.remove();
    }

    private record FrameState(long frameTimeMillis, java.util.Map<String, ProjectionNetworkImageCache.State> networkImageStates) {
        private FrameState(long frameTimeMillis) {
            this(frameTimeMillis, new java.util.HashMap<>());
        }
    }
}
