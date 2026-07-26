package dev.marblegate.superpipeslide.client.core.slide;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * Looping wind sound for pipe sliding, mirroring the vanilla elytra flight sound
 * (same sound event, same absolute air-speed loudness mapping). The instance manages its
 * own lifecycle: it fades in on start, tolerates short slide-frame gaps from
 * collision-driven detach/recapture flapping, and stops itself once the slide is over.
 * Volume is additionally scaled down by the cinematic camera blend so the wind falls
 * silent exactly as the detached cinematic shot takes over.
 */
final class SlideWindSoundInstance extends AbstractTickableSoundInstance {
    private static final int FADE_IN_TICKS = 10;
    private static final int GAP_STOP_TICKS = 8;
    private static final float VOLUME_SMOOTHING = 0.3F;
    private static final float PITCH_SPEED_FACTOR = 0.25F;
    // Overall loudness trim; the quadratic curve below keeps low speeds near-silent.
    private static final float WIND_GAIN = 0.9F;
    // Air speed (blocks/tick) that maps to full volume: normal-pipe cruise is 0.9 b/t,
    // so this keeps ordinary pipes clearly audible while highways still reach full.
    // (The elytra's squared |v|²/4 mapping left normal-pipe speeds near-silent.)
    private static final float FULL_VOLUME_SPEED_BPT = 1.2F;

    private final LocalPlayer player;
    private int ticksAlive;
    private int gapTicks;

    SlideWindSoundInstance(LocalPlayer player) {
        super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        // The rider's own wind: keep loudness independent of the camera distance so
        // third-person views sound the same as first-person. Cinematic shots mute the
        // sound explicitly through the blend factor below.
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    @Override
    public boolean canStartSilent() {
        // The loop starts at zero volume and fades in through the per-tick volume
        // re-application. Without this the sound engine drops a zero-volume instance at
        // play() time (NOT_STARTED), so tick() would never run. (Vanilla's elytra loop
        // sidesteps this by starting at 0.1F.)
        return true;
    }

    @Override
    public void tick() {
        if (this.player.isRemoved()) {
            this.stop();
            return;
        }
        this.ticksAlive++;
        boolean framePresent = ClientSlideFeedbackController.currentRenderFrame().isPresent();
        this.gapTicks = framePresent ? 0 : this.gapTicks + 1;
        if (this.gapTicks > GAP_STOP_TICKS) {
            this.stop();
            return;
        }
        this.x = (float) this.player.getX();
        this.y = (float) this.player.getY();
        this.z = (float) this.player.getZ();
        // Absolute air speed (blocks/tick), linear so slow pipes whisper and highways
        // roar; station holds fall silent and dismounts decay naturally.
        float speedLevel = Mth.clamp((float) this.player.getDeltaMovement().length() / FULL_VOLUME_SPEED_BPT, 0.0F, 1.0F);
        // Quadratic loudness: real wind noise is barely audible at walking-ish speeds
        // and ramps up with speed, so the low end stays essentially silent.
        float target = speedLevel * speedLevel * WIND_GAIN;
        target *= Math.min(1.0F, this.ticksAlive / (float) FADE_IN_TICKS);
        target *= 1.0F - (float) ClientCinematicCameraController.blendFactor();
        this.volume += (target - this.volume) * VOLUME_SMOOTHING;
        this.pitch = 1.0F + PITCH_SPEED_FACTOR * speedLevel;
    }
}
