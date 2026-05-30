package dev.marblegate.superpipeslide.client.core.accessibility;

import dev.marblegate.superpipeslide.client.renderer.pipe.ClientPipeRenderer;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.config.ClientConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;

public final class ClientSafetyOptions {
    private static final String STATE_FILE = "superpipeslide-client-safety.properties";
    private static final String SLIDE_WARNING_ACKNOWLEDGED = "slideSafetyWarningAcknowledged";
    private static boolean stateLoaded;
    private static boolean slideSafetyWarningAcknowledged;
    private static boolean lastReducePhotosensitivityRisk;

    private ClientSafetyOptions() {}

    public static void tick() {
        loadStateIfNeeded();
        boolean photic = reducePhotosensitivityRisk();
        if (photic != lastReducePhotosensitivityRisk) {
            ClientPipeRenderer.clearRenderCache();
            lastReducePhotosensitivityRisk = photic;
        }
    }

    public static boolean slideCameraFeedbackEnabled() {
        return ClientConfig.ENABLE_SLIDE_CAMERA_FEEDBACK.get() && !reduceMotionSicknessRisk();
    }

    public static boolean reduceMotionSicknessRisk() {
        return ClientConfig.REDUCE_MOTION_SICKNESS_RISK.get();
    }

    public static boolean reducePhotosensitivityRisk() {
        return ClientConfig.REDUCE_PHOTOSENSITIVITY_RISK.get();
    }

    public static void setReduceMotionSicknessRisk(boolean value) {
        ClientConfig.REDUCE_MOTION_SICKNESS_RISK.set(value);
        saveClientConfig();
    }

    public static void setReducePhotosensitivityRisk(boolean value) {
        ClientConfig.REDUCE_PHOTOSENSITIVITY_RISK.set(value);
        saveClientConfig();
        if (value != lastReducePhotosensitivityRisk) {
            ClientPipeRenderer.clearRenderCache();
            lastReducePhotosensitivityRisk = value;
        }
    }

    public static boolean slideSafetyWarningAcknowledged() {
        loadStateIfNeeded();
        return slideSafetyWarningAcknowledged;
    }

    public static void acknowledgeSlideSafetyWarning() {
        loadStateIfNeeded();
        if (slideSafetyWarningAcknowledged) {
            return;
        }
        slideSafetyWarningAcknowledged = true;
        saveState();
    }

    private static void loadStateIfNeeded() {
        if (stateLoaded) {
            return;
        }
        stateLoaded = true;
        lastReducePhotosensitivityRisk = reducePhotosensitivityRisk();
        Path path = statePath();
        if (!Files.isRegularFile(path)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            slideSafetyWarningAcknowledged = Boolean.parseBoolean(properties.getProperty(SLIDE_WARNING_ACKNOWLEDGED, "false"));
        } catch (IOException exception) {
            SuperPipeSlide.LOGGER.warn("Failed to load SuperPipeSlide client safety state from {}", path, exception);
        }
    }

    private static void saveState() {
        Path path = statePath();
        Properties properties = new Properties();
        properties.setProperty(SLIDE_WARNING_ACKNOWLEDGED, Boolean.toString(slideSafetyWarningAcknowledged));
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "SuperPipeSlide client safety state");
            }
        } catch (IOException exception) {
            SuperPipeSlide.LOGGER.warn("Failed to save SuperPipeSlide client safety state to {}", path, exception);
        }
    }

    private static Path statePath() {
        return FMLPaths.CONFIGDIR.get().resolve(STATE_FILE);
    }

    private static void saveClientConfig() {
        try {
            ClientConfig.SPEC.save();
        } catch (Exception exception) {
            SuperPipeSlide.LOGGER.debug("Client config save is unavailable on this runtime", exception);
        }
    }
}
