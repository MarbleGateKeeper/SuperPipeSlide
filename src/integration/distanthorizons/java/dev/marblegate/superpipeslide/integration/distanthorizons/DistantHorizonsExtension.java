package dev.marblegate.superpipeslide.integration.distanthorizons;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.integration.ModIntegration;
import dev.marblegate.superpipeslide.integration.distanthorizons.client.ClientPipeFarLodProxyProvider;
import dev.marblegate.superpipeslide.integration.distanthorizons.client.DistantHorizonsPipeLodBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = SuperPipeSlide.MODID, dist = Dist.CLIENT)
public class DistantHorizonsExtension {
    public DistantHorizonsExtension(IEventBus modBus, ModContainer modContainer) {
        if (ModIntegration.DISTANT_HORIZONS.enabled()) {
            modBus.register(new Client());
        }
    }

    public static class Client {
        @SubscribeEvent
        public void construct(FMLConstructModEvent event) {
            DistantHorizonsPipeLodBridge.initialize();
            NeoForge.EVENT_BUS.addListener(Client::clientTick);
        }

        public static void clientTick(ClientTickEvent.Post event) {
            DistantHorizonsPipeLodBridge.tick();
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player == null || player.level() == null) {
                ClientPipeFarLodProxyProvider.clear();
            }
        }
    }
}
