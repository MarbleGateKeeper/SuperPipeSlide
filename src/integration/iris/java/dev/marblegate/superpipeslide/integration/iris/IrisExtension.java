package dev.marblegate.superpipeslide.integration.iris;

import dev.marblegate.superpipeslide.client.renderer.pipe.ClientPipeRenderer;
import dev.marblegate.superpipeslide.client.renderer.fold.ClientFoldTraversalPostEffectRenderer;
import dev.marblegate.superpipeslide.client.renderer.ClientRenderCompatibility;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.integration.ModIntegration;
import dev.marblegate.superpipeslide.integration.iris.client.IrisFoldTraversalPostEffectExtension;
import dev.marblegate.superpipeslide.integration.iris.client.IrisPipeRenderExtension;
import dev.marblegate.superpipeslide.integration.iris.client.IrisRenderTypeAdapter;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

@Mod(value = SuperPipeSlide.MODID, dist = Dist.CLIENT)
public class IrisExtension {
    public IrisExtension(IEventBus modBus, ModContainer modContainer) {
        if (ModIntegration.IRIS.enabled()) {
            modBus.register(new Client());
        }
    }

    public static class Client {
        @SubscribeEvent
        public void construct(FMLConstructModEvent event) {
            ClientRenderCompatibility.registerRenderTypeAdapter(new IrisRenderTypeAdapter());
            ClientPipeRenderer.registerRenderExtension(new IrisPipeRenderExtension());
            ClientFoldTraversalPostEffectRenderer.registerRenderExtension(new IrisFoldTraversalPostEffectExtension());
        }
    }
}
