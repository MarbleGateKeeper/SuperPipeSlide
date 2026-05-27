package dev.marblegate.superpipeslide.client;


import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeAppearanceCache;
import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.core.slide.ClientSlideController;
import dev.marblegate.superpipeslide.client.core.slide.ClientSlideNoticeController;
import dev.marblegate.superpipeslide.client.core.sync.ClientDataResyncRequests;
import dev.marblegate.superpipeslide.client.fullmap.screen.FullRouteMapScreen;
import dev.marblegate.superpipeslide.client.gui.anchor.FoldAnchorEditorScreen;
import dev.marblegate.superpipeslide.client.gui.base.RouteDataAwareScreen;
import dev.marblegate.superpipeslide.client.gui.pipe.PipeAppearanceEditorScreen;
import dev.marblegate.superpipeslide.client.gui.platform.PlatformProjectorScreen;
import dev.marblegate.superpipeslide.client.gui.platform.PlatformStopEditorScreen;
import dev.marblegate.superpipeslide.client.gui.projection.ProjectionLayoutCanvasScreen;
import dev.marblegate.superpipeslide.client.gui.projection.ProjectionLayoutLibraryScreen;
import dev.marblegate.superpipeslide.client.gui.route.RouteEditorScreen;
import dev.marblegate.superpipeslide.client.gui.station.StationEditorScreen;
import dev.marblegate.superpipeslide.client.gui.station.StationNameProjectorScreen;
import dev.marblegate.superpipeslide.common.block.station.PlatformProjectorBlockEntity;
import dev.marblegate.superpipeslide.common.block.station.StationNameProjectorBlockEntity;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.network.editor.ClientboundEditorResultPayload;
import dev.marblegate.superpipeslide.network.fold.ClientboundOpenFoldAnchorEditorPayload;
import dev.marblegate.superpipeslide.network.pipe.appearance.ClientboundOpenPipeAppearanceEditorPayload;
import dev.marblegate.superpipeslide.network.pipe.appearance.ClientboundPipeAppearanceSyncPayload;
import dev.marblegate.superpipeslide.network.platform.ClientboundOpenPlatformEditorPayload;
import dev.marblegate.superpipeslide.network.platform.ClientboundOpenPlatformProjectorPayload;
import dev.marblegate.superpipeslide.network.platform.ClientboundPlatformProjectorConfigPayload;
import dev.marblegate.superpipeslide.network.projection.ClientboundOpenProjectionLayoutDesignerPayload;
import dev.marblegate.superpipeslide.network.route.ClientboundOpenFullRouteMapPayload;
import dev.marblegate.superpipeslide.network.route.ClientboundOpenRouteEditorPayload;
import dev.marblegate.superpipeslide.network.slide.ClientboundSlideNoticePayload;
import dev.marblegate.superpipeslide.network.slide.ClientboundSlideTeleportCommitPayload;
import dev.marblegate.superpipeslide.network.slide.ClientboundSlideTeleportFailedPayload;
import dev.marblegate.superpipeslide.network.station.ClientboundOpenStationEditorPayload;
import dev.marblegate.superpipeslide.network.station.ClientboundOpenStationNameProjectorPayload;
import dev.marblegate.superpipeslide.network.station.ClientboundStationNameProjectorConfigPayload;
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
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = SuperPipeSlide.MODID, value = Dist.CLIENT)
public final class ClientNetworkHandlers {
    private ClientNetworkHandlers() {
    }

    @SubscribeEvent
    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(PipeNetworkSnapshotStartPayload.TYPE, ClientNetworkHandlers::handleSnapshotStart);
        event.register(PipeNetworkSnapshotChunkPayload.TYPE, ClientNetworkHandlers::handleSnapshotChunk);
        event.register(PipeNetworkSnapshotEndPayload.TYPE, ClientNetworkHandlers::handleSnapshotEnd);
        event.register(PipeNetworkDeltaPayload.TYPE, ClientNetworkHandlers::handleDelta);
        event.register(ClientboundPipeAppearanceSyncPayload.TYPE, ClientNetworkHandlers::handlePipeAppearanceSync);
        event.register(ClientboundOpenPipeAppearanceEditorPayload.TYPE, ClientNetworkHandlers::handleOpenPipeAppearanceEditor);
        event.register(ClientboundSlideNoticePayload.TYPE, ClientNetworkHandlers::handleSlideNotice);
        event.register(ClientboundSlideTeleportCommitPayload.TYPE, ClientNetworkHandlers::handleSlideTeleportCommit);
        event.register(ClientboundSlideTeleportFailedPayload.TYPE, ClientNetworkHandlers::handleSlideTeleportFailed);
        event.register(ClientboundRouteDataSnapshotStartPayload.TYPE, ClientNetworkHandlers::handleRouteDataSnapshotStart);
        event.register(ClientboundRouteDataSnapshotChunkPayload.TYPE, ClientNetworkHandlers::handleRouteDataSnapshotChunk);
        event.register(ClientboundRouteDataSnapshotEndPayload.TYPE, ClientNetworkHandlers::handleRouteDataSnapshotEnd);
        event.register(ClientboundRouteDataDeltaPayload.TYPE, ClientNetworkHandlers::handleRouteDataDelta);
        event.register(ClientboundOpenStationEditorPayload.TYPE, ClientNetworkHandlers::handleOpenStationEditor);
        event.register(ClientboundOpenStationNameProjectorPayload.TYPE, ClientNetworkHandlers::handleOpenStationNameProjector);
        event.register(ClientboundOpenPlatformProjectorPayload.TYPE, ClientNetworkHandlers::handleOpenPlatformProjector);
        event.register(ClientboundStationNameProjectorConfigPayload.TYPE, ClientNetworkHandlers::handleStationNameProjectorConfig);
        event.register(ClientboundPlatformProjectorConfigPayload.TYPE, ClientNetworkHandlers::handlePlatformProjectorConfig);
        event.register(ClientboundOpenProjectionLayoutDesignerPayload.TYPE, ClientNetworkHandlers::handleOpenProjectionLayoutDesigner);
        event.register(ClientboundOpenPlatformEditorPayload.TYPE, ClientNetworkHandlers::handleOpenPlatformEditor);
        event.register(ClientboundOpenFullRouteMapPayload.TYPE, ClientNetworkHandlers::handleOpenFullRouteMap);
        event.register(ClientboundOpenRouteEditorPayload.TYPE, ClientNetworkHandlers::handleOpenRouteEditor);
        event.register(ClientboundOpenFoldAnchorEditorPayload.TYPE, ClientNetworkHandlers::handleOpenFoldAnchorEditor);
        event.register(ClientboundEditorResultPayload.TYPE, ClientNetworkHandlers::handleEditorResult);
    }

    private static void handleSnapshotStart(PipeNetworkSnapshotStartPayload payload, IPayloadContext context) {
        ClientPipeNetworkCache.handleSnapshotStart(payload);
    }

    private static void handleSnapshotChunk(PipeNetworkSnapshotChunkPayload payload, IPayloadContext context) {
        ClientPipeNetworkCache.handleSnapshotChunk(payload, () -> requestResync(context));
    }

    private static void handleSnapshotEnd(PipeNetworkSnapshotEndPayload payload, IPayloadContext context) {
        ClientPipeNetworkCache.handleSnapshotEnd(payload, () -> requestResync(context));
    }

    private static void handleDelta(PipeNetworkDeltaPayload payload, IPayloadContext context) {
        ClientPipeNetworkCache.handleDelta(payload, () -> requestResync(context));
    }

    private static void handlePipeAppearanceSync(ClientboundPipeAppearanceSyncPayload payload, IPayloadContext context) {
        ClientPipeAppearanceCache.handleSync(payload);
        if (Minecraft.getInstance().screen instanceof PipeAppearanceEditorScreen screen) {
            screen.acceptAppearanceRevision(payload.revision());
        }
    }

    private static void handleOpenPipeAppearanceEditor(ClientboundOpenPipeAppearanceEditorPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof PipeAppearanceEditorScreen screen) {
            screen.applyPayload(payload);
        } else {
            minecraft.setScreen(new PipeAppearanceEditorScreen(payload));
        }
    }

    private static void handleSlideNotice(ClientboundSlideNoticePayload payload, IPayloadContext context) {
        ClientSlideNoticeController.handleNotice(payload);
    }

    private static void handleSlideTeleportCommit(ClientboundSlideTeleportCommitPayload payload, IPayloadContext context) {
        ClientSlideController.acceptTeleportCommit(Minecraft.getInstance().player, payload);
    }

    private static void handleSlideTeleportFailed(ClientboundSlideTeleportFailedPayload payload, IPayloadContext context) {
        ClientSlideController.acceptTeleportFailed(Minecraft.getInstance().player, payload);
    }

    private static void handleRouteDataSnapshotStart(ClientboundRouteDataSnapshotStartPayload payload, IPayloadContext context) {
        ClientRouteDataCache.handleSnapshotStart(payload);
    }

    private static void handleRouteDataSnapshotChunk(ClientboundRouteDataSnapshotChunkPayload payload, IPayloadContext context) {
        ClientRouteDataCache.handleSnapshotChunk(payload);
    }

    private static void handleRouteDataSnapshotEnd(ClientboundRouteDataSnapshotEndPayload payload, IPayloadContext context) {
        if (!ClientRouteDataCache.handleSnapshotEnd(payload)) {
            requestRouteDataResync(context);
            return;
        }
        if (Minecraft.getInstance().screen instanceof RouteDataAwareScreen routeDataAwareScreen) {
            routeDataAwareScreen.refreshFromRouteSnapshot();
        }
    }

    private static void handleRouteDataDelta(ClientboundRouteDataDeltaPayload payload, IPayloadContext context) {
        if (!ClientRouteDataCache.handleDelta(payload)) {
            requestRouteDataResync(context);
            return;
        }
        if (Minecraft.getInstance().screen instanceof RouteDataAwareScreen routeDataAwareScreen) {
            routeDataAwareScreen.refreshFromRouteSnapshot();
        }
    }

    private static void handleOpenStationEditor(ClientboundOpenStationEditorPayload payload, IPayloadContext context) {
        Minecraft.getInstance().setScreen(new StationEditorScreen(payload.stationGroupId()));
    }

    private static void handleOpenStationNameProjector(ClientboundOpenStationNameProjectorPayload payload, IPayloadContext context) {
        Minecraft.getInstance().setScreen(new StationNameProjectorScreen(payload.pos(), payload.config(), payload.appliedLayout()));
    }

    private static void handleOpenPlatformProjector(ClientboundOpenPlatformProjectorPayload payload, IPayloadContext context) {
        Minecraft.getInstance().setScreen(new PlatformProjectorScreen(payload.pos(), payload.config(), payload.appliedLayout()));
    }

    private static void handleStationNameProjectorConfig(ClientboundStationNameProjectorConfigPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (minecraft.level.getBlockEntity(payload.pos()) instanceof dev.marblegate.superpipeslide.common.block.station.StationNameProjectorBlockEntity projector) {
            projector.acceptClientConfig(payload.config());
        }
    }

    private static void handlePlatformProjectorConfig(ClientboundPlatformProjectorConfigPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (minecraft.level.getBlockEntity(payload.pos()) instanceof dev.marblegate.superpipeslide.common.block.station.PlatformProjectorBlockEntity projector) {
            projector.acceptClientConfig(payload.config());
        }
    }

    private static void handleOpenProjectionLayoutDesigner(ClientboundOpenProjectionLayoutDesignerPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ProjectionLayoutCanvasScreen screen) {
            screen.acceptLibraryPayload(payload);
            return;
        }
        if (minecraft.screen instanceof ProjectionLayoutLibraryScreen screen) {
            screen.applyPayload(payload);
        } else if (payload.editSelected()) {
            payload.selectedLayoutId()
                    .flatMap(id -> payload.layouts().stream().filter(summary -> summary.id().equals(id) && !summary.invalid()).findFirst())
                    .map(summary -> summary.preview())
                    .ifPresentOrElse(
                            layout -> minecraft.setScreen(new ProjectionLayoutCanvasScreen(payload, layout)),
                            () -> minecraft.setScreen(new ProjectionLayoutLibraryScreen(payload))
                    );
        } else {
            minecraft.setScreen(new ProjectionLayoutLibraryScreen(payload));
        }
    }

    private static void handleOpenPlatformEditor(ClientboundOpenPlatformEditorPayload payload, IPayloadContext context) {
        Minecraft.getInstance().setScreen(new PlatformStopEditorScreen(payload.platformStopId()));
    }

    private static void handleOpenFullRouteMap(ClientboundOpenFullRouteMapPayload payload, IPayloadContext context) {
        Minecraft.getInstance().setScreen(new FullRouteMapScreen());
    }

    private static void handleOpenRouteEditor(ClientboundOpenRouteEditorPayload payload, IPayloadContext context) {
        Minecraft.getInstance().setScreen(new RouteEditorScreen());
    }

    private static void handleOpenFoldAnchorEditor(ClientboundOpenFoldAnchorEditorPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof FoldAnchorEditorScreen screen) {
            screen.applyPayload(payload);
        } else {
            minecraft.setScreen(new FoldAnchorEditorScreen(payload));
        }
    }

    private static void handleEditorResult(ClientboundEditorResultPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof FoldAnchorEditorScreen screen) {
            screen.handleEditorResult(payload);
        }
        if (minecraft.player != null) {
            minecraft.player.sendOverlayMessage(Component.literal(payload.message()));
        }
    }

    private static void requestResync(IPayloadContext context) {
        if (ClientDataResyncRequests.shouldRequestPipeNetwork()) {
            context.reply(new PipeNetworkResyncRequestPayload());
        }
    }

    private static void requestRouteDataResync(IPayloadContext context) {
        if (ClientDataResyncRequests.shouldRequestRouteData()) {
            context.reply(new RouteDataResyncRequestPayload());
        }
    }
}
