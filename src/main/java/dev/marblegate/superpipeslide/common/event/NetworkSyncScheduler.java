package dev.marblegate.superpipeslide.common.event;

import dev.marblegate.superpipeslide.common.core.appearance.storage.PipeAppearanceSavedData;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkSavedData;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.ServerPipeNetworkView;
import dev.marblegate.superpipeslide.common.core.route.storage.RouteNetworkSavedData;
import dev.marblegate.superpipeslide.network.pipe.appearance.ClientboundPipeAppearanceSyncPayload;
import dev.marblegate.superpipeslide.network.sync.route.ClientboundRouteDataDeltaPayload;
import dev.marblegate.superpipeslide.network.sync.pipe.PipeNetworkDeltaPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

final class NetworkSyncScheduler {
    private boolean pendingRouteSnapshot;
    private boolean pendingRouteDelta;

    void queueRouteDataSnapshot() {
        this.pendingRouteSnapshot = true;
    }

    void queueRouteDataDelta() {
        this.pendingRouteDelta = true;
    }

    PipeNetworkSavedData.PendingPipeNetworkChanges broadcastPendingPipeNetworkChanges(ServerLevel level) {
        PipeNetworkSavedData.PendingPipeNetworkChanges pendingChanges = PipeNetworkSavedData.get(level).consumePendingNetworkChanges();
        PipeNetworkDeltaPayload delta = pendingChanges.payload();
        if (delta.isEmpty()) {
            return pendingChanges;
        }

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, delta);
        }
        return pendingChanges;
    }

    void broadcastRouteDataDelta(ServerLevel level) {
        ClientboundRouteDataDeltaPayload delta = RouteNetworkSavedData.get(level.getServer()).consumePendingRouteDelta(ServerPipeNetworkView.of(level.getServer()).revision());
        if (delta.isEmpty()) {
            return;
        }
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, delta);
        }
    }

    void broadcastPipeAppearanceDelta(ServerLevel level) {
        PipeAppearanceSavedData data = PipeAppearanceSavedData.get(level.getServer());
        if (!data.hasPendingSync()) {
            return;
        }
        ClientboundPipeAppearanceSyncPayload payload = data.consumePendingSyncPayload();
        if (payload.isEmpty()) {
            return;
        }
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    void flush(ServerLevel level) {
        this.broadcastPipeAppearanceDelta(level);
        if (this.pendingRouteDelta) {
            this.pendingRouteDelta = false;
            this.broadcastRouteDataDelta(level);
        }
        if (this.pendingRouteSnapshot) {
            this.pendingRouteSnapshot = false;
            ServerEvents.broadcastRouteDataSnapshot(level);
        }
    }

    void clear() {
        this.pendingRouteSnapshot = false;
        this.pendingRouteDelta = false;
    }
}
