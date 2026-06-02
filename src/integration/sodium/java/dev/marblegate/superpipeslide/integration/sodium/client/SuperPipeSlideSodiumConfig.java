package dev.marblegate.superpipeslide.integration.sodium.client;

import dev.marblegate.superpipeslide.client.core.accessibility.ClientSafetyOptions;
import dev.marblegate.superpipeslide.client.renderer.pipe.ClientPipeRenderer;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.config.ClientConfig;
import dev.marblegate.superpipeslide.config.ShaderpackPipeRenderMode;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.EnumOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class SuperPipeSlideSodiumConfig implements ConfigEntryPoint {
    private static final Identifier MENU_ICON = Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "textures/gui/config-icon.png");
    private static final Identifier PIPE_RENDER_MODE = Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "shaderpack_pipe_render_mode");
    private static final Identifier REDUCE_MOTION_SICKNESS_RISK = Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "reduce_motion_sickness_risk");
    private static final Identifier REDUCE_PHOTOSENSITIVITY_RISK = Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "reduce_photosensitivity_risk");
    private static final StorageEventHandler SAVE_CLIENT_CONFIG = ClientConfig::save;

    @Override
    public void registerConfigEarly(ConfigBuilder builder) {}

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        ModOptionsBuilder modOptions = builder.registerOwnModOptions()
                .setNonTintedIcon(MENU_ICON);
        OptionPageBuilder page = builder.createOptionPage()
                .setName(Component.translatable("sodium.options.superpipeslide.page"));
        OptionGroupBuilder shaderpackGroup = builder.createOptionGroup()
                .setName(Component.translatable("sodium.options.superpipeslide.group.shaderpack"));

        EnumOptionBuilder<ShaderpackPipeRenderMode> pipeRenderModeOption = builder.createEnumOption(PIPE_RENDER_MODE, ShaderpackPipeRenderMode.class)
                .setName(Component.translatable("sodium.options.superpipeslide.shaderpack_pipe_render_mode"))
                .setTooltip(mode -> Component.translatable(mode.tooltipKey()))
                .setElementNameProvider(mode -> Component.translatable(mode.translationKey()))
                .setDefaultValue(ShaderpackPipeRenderMode.PERFORMANCE)
                .setBinding(ClientConfig.SHADERPACK_PIPE_RENDER_MODE::set, ClientConfig.SHADERPACK_PIPE_RENDER_MODE::get)
                .setStorageHandler(SAVE_CLIENT_CONFIG)
                .setImpact(OptionImpact.VARIES)
                .setApplyHook(this::onApply);

        OptionGroupBuilder accessibilityGroup = builder.createOptionGroup()
                .setName(Component.translatable("sodium.options.superpipeslide.group.accessibility"));
        BooleanOptionBuilder reduceMotionSicknessRiskOption = builder.createBooleanOption(REDUCE_MOTION_SICKNESS_RISK)
                .setName(Component.translatable("superpipeslide.configuration.reduceMotionSicknessRisk"))
                .setTooltip(Component.translatable("superpipeslide.configuration.reduceMotionSicknessRisk.tooltip"))
                .setDefaultValue(false)
                .setBinding(ClientSafetyOptions::setReduceMotionSicknessRisk, ClientSafetyOptions::reduceMotionSicknessRisk)
                .setStorageHandler(SAVE_CLIENT_CONFIG)
                .setImpact(OptionImpact.VARIES);
        BooleanOptionBuilder reducePhotosensitivityRiskOption = builder.createBooleanOption(REDUCE_PHOTOSENSITIVITY_RISK)
                .setName(Component.translatable("superpipeslide.configuration.reducePhotosensitivityRisk"))
                .setTooltip(Component.translatable("superpipeslide.configuration.reducePhotosensitivityRisk.tooltip"))
                .setDefaultValue(false)
                .setBinding(ClientSafetyOptions::setReducePhotosensitivityRisk, ClientSafetyOptions::reducePhotosensitivityRisk)
                .setStorageHandler(SAVE_CLIENT_CONFIG)
                .setImpact(OptionImpact.VARIES);

        shaderpackGroup.addOption(pipeRenderModeOption);
        accessibilityGroup.addOption(reduceMotionSicknessRiskOption);
        accessibilityGroup.addOption(reducePhotosensitivityRiskOption);
        page.addOptionGroup(shaderpackGroup);
        page.addOptionGroup(accessibilityGroup);
        modOptions.addPage(page);
    }

    private void onApply(ConfigState state) {
        ClientPipeRenderer.clearRenderCache();
    }
}
