package dev.marblegate.superpipeslide.common;

import com.mojang.logging.LogUtils;
import dev.marblegate.superpipeslide.common.registry.SPSBlockEntities;
import dev.marblegate.superpipeslide.common.registry.SPSBlocks;
import dev.marblegate.superpipeslide.common.registry.SPSCreativeTabs;
import dev.marblegate.superpipeslide.common.registry.SPSDataComponents;
import dev.marblegate.superpipeslide.common.registry.SPSItems;
import dev.marblegate.superpipeslide.config.ClientConfig;
import dev.marblegate.superpipeslide.config.Config;
import dev.marblegate.superpipeslide.network.SPSNetworking;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(SuperPipeSlide.MODID)
public class SuperPipeSlide {
    public static final String MODID = "superpipeslide";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SuperPipeSlide(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        SPSBlocks.BLOCKS.register(modEventBus);
        SPSBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        SPSItems.ITEMS.register(modEventBus);
        SPSDataComponents.DATA_COMPONENTS.register(modEventBus);
        SPSCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(SPSNetworking::register);

        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {}
}
