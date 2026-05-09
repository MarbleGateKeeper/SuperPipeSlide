package dev.marblegate.superpipeslide.network;


import dev.marblegate.superpipeslide.network.editor.ClientboundEditorResultPayload;
import dev.marblegate.superpipeslide.network.fold.ClientboundOpenFoldAnchorEditorPayload;
import dev.marblegate.superpipeslide.network.fold.ServerboundFoldAnchorSavePayload;
import dev.marblegate.superpipeslide.network.pipe.appearance.ClientboundOpenPipeAppearanceEditorPayload;
import dev.marblegate.superpipeslide.network.pipe.appearance.ClientboundPipeAppearanceSyncPayload;
import dev.marblegate.superpipeslide.network.pipe.appearance.ServerboundPipeAppearanceApplyPayload;
import dev.marblegate.superpipeslide.network.platform.ClientboundOpenPlatformEditorPayload;
import dev.marblegate.superpipeslide.network.platform.ClientboundOpenPlatformProjectorPayload;
import dev.marblegate.superpipeslide.network.platform.ClientboundPlatformProjectorConfigPayload;
import dev.marblegate.superpipeslide.network.platform.ServerboundPlatformProjectorSavePayload;
import dev.marblegate.superpipeslide.network.platform.ServerboundPlatformStopEditPayload;
import dev.marblegate.superpipeslide.network.projection.ClientboundOpenProjectionLayoutDesignerPayload;
import dev.marblegate.superpipeslide.network.projection.ServerboundProjectionLayoutDeletePayload;
import dev.marblegate.superpipeslide.network.projection.ServerboundProjectionLayoutSavePayload;
import dev.marblegate.superpipeslide.network.projection.ServerboundProjectionLayoutSelectPayload;
import dev.marblegate.superpipeslide.network.route.ClientboundOpenRouteEditorPayload;
import dev.marblegate.superpipeslide.network.route.ServerboundRouteEditPayload;
import dev.marblegate.superpipeslide.network.slide.ClientboundSlideNoticePayload;
import dev.marblegate.superpipeslide.network.slide.ClientboundSlideTeleportCommitPayload;
import dev.marblegate.superpipeslide.network.slide.ClientboundSlideTeleportFailedPayload;
import dev.marblegate.superpipeslide.network.slide.ServerboundSlideModePayload;
import dev.marblegate.superpipeslide.network.slide.ServerboundSlideTeleportRequestPayload;
import dev.marblegate.superpipeslide.network.station.ClientboundOpenStationEditorPayload;
import dev.marblegate.superpipeslide.network.station.ClientboundOpenStationNameProjectorPayload;
import dev.marblegate.superpipeslide.network.station.ClientboundStationNameProjectorConfigPayload;
import dev.marblegate.superpipeslide.network.station.ServerboundStationEditPayload;
import dev.marblegate.superpipeslide.network.station.ServerboundStationNameProjectorSavePayload;
import dev.marblegate.superpipeslide.network.station.ServerboundStationTransferEditPayload;
import dev.marblegate.superpipeslide.network.sync.pipe.PipeNetworkDeltaPayload;
import dev.marblegate.superpipeslide.network.sync.pipe.PipeNetworkResyncRequestPayload;
import dev.marblegate.superpipeslide.network.sync.pipe.PipeNetworkSnapshotChunkPayload;
import dev.marblegate.superpipeslide.network.sync.pipe.PipeNetworkSnapshotEndPayload;
import dev.marblegate.superpipeslide.network.sync.pipe.PipeNetworkSnapshotStartPayload;
import dev.marblegate.superpipeslide.network.sync.route.ClientboundRouteDataDeltaPayload;
import dev.marblegate.superpipeslide.network.sync.route.ClientboundRouteDataSnapshotChunkPayload;
import dev.marblegate.superpipeslide.network.sync.route.ClientboundRouteDataSnapshotEndPayload;
import dev.marblegate.superpipeslide.network.sync.route.ClientboundRouteDataSnapshotStartPayload;
import dev.marblegate.superpipeslide.network.sync.route.RouteDataResyncRequestPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class SPSNetworking {
    private static final String NETWORK_VERSION = "1";

    private SPSNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(NETWORK_VERSION)
                .playToClient(PipeNetworkSnapshotStartPayload.TYPE, PipeNetworkSnapshotStartPayload.STREAM_CODEC)
                .playToClient(PipeNetworkSnapshotChunkPayload.TYPE, PipeNetworkSnapshotChunkPayload.STREAM_CODEC)
                .playToClient(PipeNetworkSnapshotEndPayload.TYPE, PipeNetworkSnapshotEndPayload.STREAM_CODEC)
                .playToClient(PipeNetworkDeltaPayload.TYPE, PipeNetworkDeltaPayload.STREAM_CODEC)
                .playToClient(ClientboundPipeAppearanceSyncPayload.TYPE, ClientboundPipeAppearanceSyncPayload.STREAM_CODEC)
                .playToClient(ClientboundOpenPipeAppearanceEditorPayload.TYPE, ClientboundOpenPipeAppearanceEditorPayload.STREAM_CODEC)
                .playToClient(ClientboundSlideNoticePayload.TYPE, ClientboundSlideNoticePayload.STREAM_CODEC)
                .playToClient(ClientboundSlideTeleportCommitPayload.TYPE, ClientboundSlideTeleportCommitPayload.STREAM_CODEC)
                .playToClient(ClientboundSlideTeleportFailedPayload.TYPE, ClientboundSlideTeleportFailedPayload.STREAM_CODEC)
                .playToClient(ClientboundRouteDataSnapshotStartPayload.TYPE, ClientboundRouteDataSnapshotStartPayload.STREAM_CODEC)
                .playToClient(ClientboundRouteDataSnapshotChunkPayload.TYPE, ClientboundRouteDataSnapshotChunkPayload.STREAM_CODEC)
                .playToClient(ClientboundRouteDataSnapshotEndPayload.TYPE, ClientboundRouteDataSnapshotEndPayload.STREAM_CODEC)
                .playToClient(ClientboundRouteDataDeltaPayload.TYPE, ClientboundRouteDataDeltaPayload.STREAM_CODEC)
                .playToClient(ClientboundOpenStationEditorPayload.TYPE, ClientboundOpenStationEditorPayload.STREAM_CODEC)
                .playToClient(ClientboundOpenStationNameProjectorPayload.TYPE, ClientboundOpenStationNameProjectorPayload.STREAM_CODEC)
                .playToClient(ClientboundOpenPlatformProjectorPayload.TYPE, ClientboundOpenPlatformProjectorPayload.STREAM_CODEC)
                .playToClient(ClientboundStationNameProjectorConfigPayload.TYPE, ClientboundStationNameProjectorConfigPayload.STREAM_CODEC)
                .playToClient(ClientboundPlatformProjectorConfigPayload.TYPE, ClientboundPlatformProjectorConfigPayload.STREAM_CODEC)
                .playToClient(ClientboundOpenProjectionLayoutDesignerPayload.TYPE, ClientboundOpenProjectionLayoutDesignerPayload.STREAM_CODEC)
                .playToClient(ClientboundOpenPlatformEditorPayload.TYPE, ClientboundOpenPlatformEditorPayload.STREAM_CODEC)
                .playToClient(ClientboundOpenRouteEditorPayload.TYPE, ClientboundOpenRouteEditorPayload.STREAM_CODEC)
                .playToClient(ClientboundOpenFoldAnchorEditorPayload.TYPE, ClientboundOpenFoldAnchorEditorPayload.STREAM_CODEC)
                .playToClient(ClientboundEditorResultPayload.TYPE, ClientboundEditorResultPayload.STREAM_CODEC)
                .playToServer(ServerboundSlideModePayload.TYPE, ServerboundSlideModePayload.STREAM_CODEC, ServerboundSlideModePayload::handleServer)
                .playToServer(ServerboundSlideTeleportRequestPayload.TYPE, ServerboundSlideTeleportRequestPayload.STREAM_CODEC, ServerboundSlideTeleportRequestPayload::handleServer)
                .playToServer(PipeNetworkResyncRequestPayload.TYPE, PipeNetworkResyncRequestPayload.STREAM_CODEC, PipeNetworkResyncRequestPayload::handleServer)
                .playToServer(RouteDataResyncRequestPayload.TYPE, RouteDataResyncRequestPayload.STREAM_CODEC, RouteDataResyncRequestPayload::handleServer)
                .playToServer(ServerboundStationEditPayload.TYPE, ServerboundStationEditPayload.STREAM_CODEC, ServerboundStationEditPayload::handleServer)
                .playToServer(ServerboundStationNameProjectorSavePayload.TYPE, ServerboundStationNameProjectorSavePayload.STREAM_CODEC, ServerboundStationNameProjectorSavePayload::handleServer)
                .playToServer(ServerboundPlatformProjectorSavePayload.TYPE, ServerboundPlatformProjectorSavePayload.STREAM_CODEC, ServerboundPlatformProjectorSavePayload::handleServer)
                .playToServer(ServerboundProjectionLayoutSavePayload.TYPE, ServerboundProjectionLayoutSavePayload.STREAM_CODEC, ServerboundProjectionLayoutSavePayload::handleServer)
                .playToServer(ServerboundProjectionLayoutDeletePayload.TYPE, ServerboundProjectionLayoutDeletePayload.STREAM_CODEC, ServerboundProjectionLayoutDeletePayload::handleServer)
                .playToServer(ServerboundProjectionLayoutSelectPayload.TYPE, ServerboundProjectionLayoutSelectPayload.STREAM_CODEC, ServerboundProjectionLayoutSelectPayload::handleServer)
                .playToServer(ServerboundStationTransferEditPayload.TYPE, ServerboundStationTransferEditPayload.STREAM_CODEC, ServerboundStationTransferEditPayload::handleServer)
                .playToServer(ServerboundPlatformStopEditPayload.TYPE, ServerboundPlatformStopEditPayload.STREAM_CODEC, ServerboundPlatformStopEditPayload::handleServer)
                .playToServer(ServerboundRouteEditPayload.TYPE, ServerboundRouteEditPayload.STREAM_CODEC, ServerboundRouteEditPayload::handleServer)
                .playToServer(ServerboundFoldAnchorSavePayload.TYPE, ServerboundFoldAnchorSavePayload.STREAM_CODEC, ServerboundFoldAnchorSavePayload::handleServer)
                .playToServer(ServerboundPipeAppearanceApplyPayload.TYPE, ServerboundPipeAppearanceApplyPayload.STREAM_CODEC, ServerboundPipeAppearanceApplyPayload::handleServer);
    }
}
