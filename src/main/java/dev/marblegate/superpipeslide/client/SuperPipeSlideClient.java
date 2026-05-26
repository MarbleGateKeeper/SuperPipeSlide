package dev.marblegate.superpipeslide.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.marblegate.superpipeslide.client.core.fold.ClientFoldTraversalEffectController;
import dev.marblegate.superpipeslide.client.core.gaze.ClientGazeChoiceController;
import dev.marblegate.superpipeslide.client.core.navigation.ClientNavigationController;
import dev.marblegate.superpipeslide.client.core.navigation.ClientNavigationHudController;
import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeAppearanceCache;
import dev.marblegate.superpipeslide.client.core.pipe.ClientPipeNetworkCache;
import dev.marblegate.superpipeslide.client.core.projection.cache.ProjectionBuiltinIconTextureCache;
import dev.marblegate.superpipeslide.client.core.projection.cache.ProjectionNetworkImageCache;
import dev.marblegate.superpipeslide.client.core.projection.cache.ProjectionTextMeasureCache;
import dev.marblegate.superpipeslide.client.core.projection.render.ProjectionWorldTextRenderer;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteHudController;
import dev.marblegate.superpipeslide.client.core.slide.ClientSlideController;
import dev.marblegate.superpipeslide.client.core.slide.ClientSlideFeedbackController;
import dev.marblegate.superpipeslide.client.core.slide.ClientSlideNoticeController;
import dev.marblegate.superpipeslide.client.core.slide.ClientSlidePoseController;
import dev.marblegate.superpipeslide.client.core.sync.ClientDataResyncRequests;
import dev.marblegate.superpipeslide.client.fullmap.screen.FullRouteMapScreen;
import dev.marblegate.superpipeslide.client.gui.navigation.DestinationNavigationScreen;
import dev.marblegate.superpipeslide.client.renderer.anchor.ClientAnchorVisibilityRenderer;
import dev.marblegate.superpipeslide.client.renderer.fold.ClientFoldTraversalEffectRenderer;
import dev.marblegate.superpipeslide.client.renderer.fold.ClientFoldTraversalPostEffectRenderer;
import dev.marblegate.superpipeslide.client.renderer.gaze.ClientGazeChoiceGeometryRenderer;
import dev.marblegate.superpipeslide.client.renderer.gaze.ClientGazeChoiceRenderer;
import dev.marblegate.superpipeslide.client.renderer.navigation.ClientNavigationWorldHighlighter;
import dev.marblegate.superpipeslide.client.renderer.pipe.ClientPipeRenderer;
import dev.marblegate.superpipeslide.client.renderer.projection.ClientProjectionProjectorIndex;
import dev.marblegate.superpipeslide.client.renderer.projection.PlatformProjectorRenderer;
import dev.marblegate.superpipeslide.client.renderer.projection.ProjectionWorldRenderer;
import dev.marblegate.superpipeslide.client.renderer.projection.StationNameProjectorRenderer;
import dev.marblegate.superpipeslide.client.renderer.slide.ClientSlideFeedbackGeometryRenderer;
import dev.marblegate.superpipeslide.client.renderer.slide.ClientSlideFeedbackPlayerRenderer;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

@Mod(value = SuperPipeSlide.MODID, dist = Dist.CLIENT)
public class SuperPipeSlideClient {
    private static final KeyMapping.Category KEY_CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "controls"));
    private static final KeyMapping OPEN_FULL_ROUTE_MAP = new KeyMapping(
            "key.superpipeslide.full_route_map",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_M,
            KEY_CATEGORY
    );
    private static final KeyMapping OPEN_NAVIGATION = new KeyMapping(
            "key.superpipeslide.navigation",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_N,
            KEY_CATEGORY
    );

    public SuperPipeSlideClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(SuperPipeSlideClient::registerGuiLayers);
        modEventBus.addListener(SuperPipeSlideClient::registerKeyMappings);
        modEventBus.addListener(SuperPipeSlideClient::registerRenderPipelines);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        Identifier routeHudLayer = Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "route_hud");
        Identifier navigationHudLayer = Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "navigation_hud");
        Identifier slideNoticeLayer = Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "slide_notice");
        event.registerAbove(VanillaGuiLayers.HOTBAR, routeHudLayer, ClientRouteHudController::render);
        event.registerAbove(routeHudLayer, navigationHudLayer, ClientNavigationHudController::render);
        event.registerAbove(navigationHudLayer, slideNoticeLayer, ClientSlideNoticeController::render);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(KEY_CATEGORY);
        event.register(OPEN_FULL_ROUTE_MAP);
        event.register(OPEN_NAVIGATION);
    }

    private static void registerRenderPipelines(RegisterRenderPipelinesEvent event) {
        ClientFoldTraversalPostEffectRenderer.registerPipelines(event);
        ClientPipeRenderer.registerPipelines(event);
        StationNameProjectorRenderer.registerPipelines(event);
        PlatformProjectorRenderer.registerPipelines(event);
    }

    @EventBusSubscriber(modid = SuperPipeSlide.MODID, value = Dist.CLIENT)
    private static final class ClientEvents {
        private ClientEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player == null || player.level() == null) {
                ClientSlideController.resetClientSession(player);
                ClientPipeAppearanceCache.clear();
                ClientPipeNetworkCache.clear();
                ClientRouteDataCache.clear();
                ClientDataResyncRequests.clear();
                ClientGazeChoiceController.clear();
                ClientRouteHudController.clear();
                ClientNavigationController.clear();
                ClientNavigationHudController.clear();
                ClientSlideNoticeController.clear();
                ClientSlideFeedbackController.clear();
                ClientSlidePoseController.clear();
                ClientFoldTraversalEffectController.clear();
                ClientSlideFeedbackPlayerRenderer.clear();
                ClientAnchorVisibilityRenderer.clear();
                ClientPipeRenderer.clearRenderCache();
                ClientProjectionProjectorIndex.clear();
                StationNameProjectorRenderer.clearCaches();
                PlatformProjectorRenderer.clearCaches();
                ProjectionBuiltinIconTextureCache.clear();
                ProjectionNetworkImageCache.clear();
                ProjectionTextMeasureCache.clear();
                ProjectionWorldTextRenderer.clear();
                return;
            }

            while (OPEN_FULL_ROUTE_MAP.consumeClick()) {
                if (minecraft.screen == null) {
                    minecraft.setScreen(new FullRouteMapScreen());
                }
            }
            while (OPEN_NAVIGATION.consumeClick()) {
                if (minecraft.screen == null) {
                    minecraft.setScreen(new DestinationNavigationScreen());
                }
            }
            ClientNavigationController.tick(minecraft, player);
            ClientSlideController.tick(minecraft, player);
            ClientFoldTraversalEffectController.tick(minecraft, player);
            ClientSlideFeedbackController.tick(minecraft, player);
            ClientSlidePoseController.tick(minecraft, player);
            ClientGazeChoiceController.tick(minecraft, player);
            ClientRouteHudController.tick();
            ClientNavigationHudController.tick();
            ClientSlideNoticeController.tick();
            ProjectionNetworkImageCache.tick();
        }

        @SubscribeEvent
        public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
            if (!event.isAttack()) {
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player != null && player.level() != null && ClientGazeChoiceController.handleAttackClick(player)) {
                event.setSwingHand(false);
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onExtractLevelRenderState(ExtractLevelRenderStateEvent event) {
            ClientPipeRenderer.extract(event);
            ClientAnchorVisibilityRenderer.extract(event);
            ClientSlideFeedbackGeometryRenderer.extract(event);
            ClientNavigationWorldHighlighter.extract(event);
            ClientFoldTraversalEffectRenderer.extract(event);
            ClientGazeChoiceGeometryRenderer.extract(event);
            ClientGazeChoiceRenderer.extract(event);
            StationNameProjectorRenderer.extract(event);
            PlatformProjectorRenderer.extract(event);
        }

        @SubscribeEvent
        public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
            ClientPipeRenderer.submit(event);
            ClientAnchorVisibilityRenderer.submit(event);
            ClientSlideFeedbackGeometryRenderer.submit(event);
            ClientNavigationWorldHighlighter.submit(event);
            ClientFoldTraversalEffectRenderer.submit(event);
            ClientGazeChoiceGeometryRenderer.submit(event);
            ClientGazeChoiceRenderer.submit(event);
        }

        @SubscribeEvent
        public static void onRenderLevelAfterOpaqueBlocks(RenderLevelStageEvent.AfterOpaqueBlocks event) {
            ClientPipeRenderer.renderAfterOpaqueBlocks(event);
        }

        @SubscribeEvent
        public static void onRenderLevelAfterTranslucentFeatures(RenderLevelStageEvent.AfterTranslucentFeatures event) {
            ClientPipeRenderer.renderAfterTranslucentFeatures(event);
        }

        @SubscribeEvent
        public static void onRenderLevelAfter(RenderLevelStageEvent.AfterLevel event) {
            ProjectionWorldRenderer.renderAfterLevel(event);
        }

        @SubscribeEvent
        public static void onComputeFov(ViewportEvent.ComputeFov event) {
            ClientSlideFeedbackPlayerRenderer.onComputeFov(event);
            ClientFoldTraversalEffectRenderer.onComputeFov(event);
        }

        @SubscribeEvent
        public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
            ClientSlideFeedbackPlayerRenderer.onComputeCameraAngles(event);
            ClientFoldTraversalEffectRenderer.onComputeCameraAngles(event);
        }

        @SubscribeEvent
        public static void onRenderFramePost(RenderFrameEvent.Post event) {
            ClientFoldTraversalPostEffectRenderer.endFrame();
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onRenderPlayerPre(RenderPlayerEvent.Pre<?> event) {
            ClientSlideFeedbackPlayerRenderer.onRenderPlayerPre(event);
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onRenderPlayerPost(RenderPlayerEvent.Post<?> event) {
            ClientSlideFeedbackPlayerRenderer.onRenderPlayerPost(event);
        }

        @SubscribeEvent
        public static void onResourceReload(AddClientReloadListenersEvent event) {
            event.addListener(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "projection_cache"), new SimplePreparableReloadListener<Void>() {
                @Override
                protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                    return null;
                }

                @Override
                protected void apply(Void value, ResourceManager resourceManager, ProfilerFiller profiler) {
                    ProjectionBuiltinIconTextureCache.clear();
                    ProjectionNetworkImageCache.clear();
                    ProjectionTextMeasureCache.clear();
                    ProjectionWorldTextRenderer.clear();
                    StationNameProjectorRenderer.clearCaches();
                    PlatformProjectorRenderer.clearCaches();
                }
            });
        }
    }

}
