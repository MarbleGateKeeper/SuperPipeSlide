package dev.marblegate.superpipeslide.client.core.sync;

import dev.marblegate.superpipeslide.network.sync.pipe.PipeNetworkResyncRequestPayload;
import dev.marblegate.superpipeslide.network.sync.route.RouteDataResyncRequestPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class ClientDataResyncRequests {
    private static final long RESYNC_COOLDOWN_MILLIS = 1000L;

    private static long lastPipeRequestMillis;
    private static long lastRouteRequestMillis;

    private ClientDataResyncRequests() {}

    public static boolean shouldRequestPipeNetwork() {
        long now = System.currentTimeMillis();
        if (now - lastPipeRequestMillis < RESYNC_COOLDOWN_MILLIS) {
            return false;
        }
        lastPipeRequestMillis = now;
        return true;
    }

    public static boolean shouldRequestRouteData() {
        long now = System.currentTimeMillis();
        if (now - lastRouteRequestMillis < RESYNC_COOLDOWN_MILLIS) {
            return false;
        }
        lastRouteRequestMillis = now;
        return true;
    }

    public static void requestPipeAndRouteFromServer() {
        if (shouldRequestPipeNetwork()) {
            ClientPacketDistributor.sendToServer(new PipeNetworkResyncRequestPayload());
        }
        if (shouldRequestRouteData()) {
            ClientPacketDistributor.sendToServer(new RouteDataResyncRequestPayload());
        }
    }

    public static void clear() {
        lastPipeRequestMillis = 0L;
        lastRouteRequestMillis = 0L;
    }
}
