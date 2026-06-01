package dev.marblegate.superpipeslide.integration.sodium.client;

import dev.marblegate.superpipeslide.client.renderer.pipe.ClientPipeRenderer;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.config.ClientConfig;
import dev.marblegate.superpipeslide.config.ShaderpackPipeRenderMode;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.EnumOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class SuperPipeSlideSodiumConfig implements ConfigEntryPoint {
    private static final Identifier PIPE_RENDER_MODE = Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "shaderpack_pipe_render_mode");
    private static final StorageEventHandler SAVE_CLIENT_CONFIG = ClientConfig::save;

    @Override
    public void registerConfigEarly(ConfigBuilder builder) {
    }

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        ModOptionsBuilder modOptions = builder.registerOwnModOptions();
        OptionPageBuilder page = builder.createOptionPage()
                .setName(Component.translatable("sodium.options.superpipeslide.page"));
        OptionGroupBuilder group = builder.createOptionGroup()
                .setName(Component.translatable("sodium.options.superpipeslide.group.shaderpack"));

        EnumOptionBuilder<ShaderpackPipeRenderMode> option = builder.createEnumOption(PIPE_RENDER_MODE, ShaderpackPipeRenderMode.class)
                .setName(Component.translatable("sodium.options.superpipeslide.shaderpack_pipe_render_mode"))
                .setTooltip(mode -> Component.translatable(mode.tooltipKey()))
                .setElementNameProvider(mode -> Component.translatable(mode.translationKey()))
                .setDefaultValue(ShaderpackPipeRenderMode.PERFORMANCE)
                .setBinding(ClientConfig.SHADERPACK_PIPE_RENDER_MODE::set, ClientConfig.SHADERPACK_PIPE_RENDER_MODE::get)
                .setStorageHandler(SAVE_CLIENT_CONFIG)
                .setImpact(OptionImpact.VARIES)
                .setApplyHook(this::onApply);

        group.addOption(option);
        page.addOptionGroup(group);
        modOptions.addPage(page);
    }

    private void onApply(ConfigState state) {
        ClientPipeRenderer.clearRenderCache();
    }
}
