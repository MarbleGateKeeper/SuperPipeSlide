package dev.marblegate.superpipeslide.client.core.gaze;


import dev.marblegate.superpipeslide.client.core.slide.ClientSlideController;
import dev.marblegate.superpipeslide.common.core.gaze.GazeChoice;
import dev.marblegate.superpipeslide.common.core.gaze.GazeChoicePlacement;
import dev.marblegate.superpipeslide.common.core.gaze.GazeChoicePlacementType;
import dev.marblegate.superpipeslide.common.core.gaze.GazeChoiceSession;
import dev.marblegate.superpipeslide.common.core.gaze.GazeChoiceShape;
import dev.marblegate.superpipeslide.common.core.gaze.GazeChoiceSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class ClientGazeChoiceController {
    private static final int FOCUS_GRACE_TICKS = 4;
    private static final int RESULT_FEEDBACK_TICKS = 9;

    @Nullable
    private static GazeChoiceSession activeSession;
    @Nullable
    private static UUID focusedChoiceId;
    @Nullable
    private static UUID submittedChoiceId;
    @Nullable
    private static UUID resultChoiceId;
    private static int focusTicks;
    private static int graceTicks;
    private static int sessionAgeTicks;
    private static int resultTicks;
    private static boolean sentChoice;
    private static boolean resultAccepted;

    private ClientGazeChoiceController() {
    }

    public static void openLocal(GazeChoiceSession session) {
        openSession(session);
    }

    private static void openSession(GazeChoiceSession session) {
        activeSession = session;
        focusedChoiceId = null;
        submittedChoiceId = null;
        resultChoiceId = null;
        focusTicks = 0;
        graceTicks = 0;
        sessionAgeTicks = 0;
        resultTicks = 0;
        sentChoice = false;
        resultAccepted = false;
    }

    public static void clear() {
        activeSession = null;
        focusedChoiceId = null;
        submittedChoiceId = null;
        resultChoiceId = null;
        focusTicks = 0;
        graceTicks = 0;
        sessionAgeTicks = 0;
        resultTicks = 0;
        sentChoice = false;
        resultAccepted = false;
    }

    public static boolean hasActiveStationChoice() {
        return activeSession != null && activeSession.source() == GazeChoiceSource.STATION;
    }

    public static void tick(Minecraft minecraft, LocalPlayer player) {
        if (activeSession == null) {
            return;
        }
        if (resultTicks > 0) {
            resultTicks--;
            if (resultTicks <= 0) {
                clear();
            }
            return;
        }
        if (sentChoice) {
            return;
        }
        sessionAgeTicks++;
        if (isLocallyExpired()) {
            submittedChoiceId = activeSession.defaultChoiceId();
            resultChoiceId = activeSession.defaultChoiceId();
            resultAccepted = false;
            resultTicks = RESULT_FEEDBACK_TICKS;
            sentChoice = true;
            return;
        }

        Optional<GazeChoice> focused = focusedChoice(player);
        if (focused.isPresent()) {
            GazeChoice choice = focused.get();
            if (choice.id().equals(focusedChoiceId)) {
                focusTicks++;
            } else {
                focusedChoiceId = choice.id();
                focusTicks = 1;
            }
            graceTicks = FOCUS_GRACE_TICKS;
            return;
        }

        if (graceTicks > 0) {
            graceTicks--;
            return;
        }

        focusedChoiceId = null;
        focusTicks = 0;
    }

    public static boolean handleAttackClick(LocalPlayer player) {
        if (activeSession == null || sentChoice) {
            return false;
        }

        Optional<GazeChoice> focused = focusedChoice(player);
        if (focused.isEmpty()) {
            return false;
        }

        GazeChoice choice = focused.get();
        if (!choice.id().equals(focusedChoiceId)) {
            focusedChoiceId = choice.id();
            focusTicks = 1;
        }
        graceTicks = FOCUS_GRACE_TICKS;

        if (ClientSlideController.handleLocalGazeChoice(activeSession, choice)) {
            acceptLocalChoice(choice.id());
            return true;
        }

        rejectLocalChoice(choice.id());
        return true;
    }

    private static void acceptLocalChoice(UUID choiceId) {
        focusedChoiceId = choiceId;
        submittedChoiceId = choiceId;
        resultChoiceId = choiceId;
        resultAccepted = true;
        resultTicks = RESULT_FEEDBACK_TICKS;
        focusTicks = activeSession == null ? 0 : activeSession.requiredFocusTicks();
        graceTicks = 0;
        sentChoice = true;
    }

    private static void rejectLocalChoice(UUID choiceId) {
        focusedChoiceId = choiceId;
        submittedChoiceId = choiceId;
        resultChoiceId = choiceId;
        resultAccepted = false;
        resultTicks = RESULT_FEEDBACK_TICKS;
        graceTicks = 0;
        sentChoice = true;
    }

    public static List<RenderChoice> renderChoices() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (activeSession == null || player == null) {
            return List.of();
        }

        return activeSession.choices().stream()
                .map(choice -> renderChoice(choice, player))
                .toList();
    }

    private static Optional<GazeChoice> focusedChoice(LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        GazeChoice best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (GazeChoice choice : activeSession.choices()) {
            Vec3 toChoice = resolveFrame(choice.placement(), player).position().subtract(eye);
            double distanceSqr = toChoice.lengthSqr();
            if (distanceSqr < 1.0E-6D) {
                continue;
            }

            double dot = look.dot(toChoice.normalize());
            double required = choice.id().equals(focusedChoiceId) ? Math.min(0.94D, choice.requiredLookPrecision() - 0.03D) : choice.requiredLookPrecision();
            if (dot < required) {
                continue;
            }

            double score = dot - distanceSqr * 0.0005D;
            if (score > bestScore) {
                best = choice;
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }

    private static RenderChoice renderChoice(GazeChoice choice, LocalPlayer player) {
        boolean focused = choice.id().equals(focusedChoiceId);
        double progress = focused && activeSession != null ? Math.min(1.0D, (double) focusTicks / activeSession.requiredFocusTicks()) : 0.0D;
        double dissolve = sessionDissolveProgress();
        boolean submitted = choice.id().equals(submittedChoiceId);
        boolean result = choice.id().equals(resultChoiceId);
        ChoiceVisualState visualState = visualState(focused, submitted, result, progress);
        double resultProgress = resultTicks <= 0 ? 0.0D : 1.0D - (double) resultTicks / RESULT_FEEDBACK_TICKS;
        ResolvedFrame frame = resolveFrame(choice.placement(), player);
        return new RenderChoice(
                choice.id(),
                frame.anchor(),
                frame.position(),
                choice.colors(),
                choice.recommended(),
                radiusFor(visualState, progress, resultProgress),
                progress,
                dissolve,
                resultProgress,
                visualState,
                choice.label(),
                choice.detail(),
                choice.shape(),
                frame.forward(),
                frame.up(),
                frame.right()
        );
    }

    private static ChoiceVisualState visualState(boolean focused, boolean submitted, boolean result, double progress) {
        if (result) {
            return resultAccepted ? ChoiceVisualState.ACCEPTED : ChoiceVisualState.REJECTED;
        }
        if (submitted) {
            return ChoiceVisualState.SUBMITTED;
        }
        if (focused && progress >= 1.0D) {
            return ChoiceVisualState.READY;
        }
        if (focused) {
            return ChoiceVisualState.FOCUSED;
        }
        return ChoiceVisualState.IDLE;
    }

    private static double radiusFor(ChoiceVisualState visualState, double focusProgress, double resultProgress) {
        return switch (visualState) {
            case READY -> 0.48D;
            case FOCUSED -> 0.39D + 0.07D * focusProgress;
            case SUBMITTED -> 0.44D;
            case ACCEPTED -> 0.44D - resultProgress * 0.05D;
            case REJECTED -> 0.40D;
            case IDLE -> 0.34D;
        };
    }

    private static ResolvedFrame resolveFrame(GazeChoicePlacement placement, LocalPlayer player) {
        if (player != null && placement.type() == GazeChoicePlacementType.SLIDE_FRAME) {
            Vec3 forward = ClientSlideController.currentSlideDirection(player).orElseGet(() -> bodyForward(player));
            Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
            Vec3 right = safeNormalize(forward.cross(up), new Vec3(1.0D, 0.0D, 0.0D));
            up = safeNormalize(right.cross(forward), up);
            Vec3 anchor = player.getEyePosition().add(0.0D, -0.1D, 0.0D);
            Vec3 position = applyFrame(anchor, forward, up, right, placement.localOffset());
            return new ResolvedFrame(anchor, position, forward, up, right);
        }

        Vec3 forward = safeNormalize(placement.forward(), new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 up = safeNormalize(placement.up(), new Vec3(0.0D, 1.0D, 0.0D));
        Vec3 right = safeNormalize(forward.cross(up), new Vec3(1.0D, 0.0D, 0.0D));
        up = safeNormalize(right.cross(forward), up);
        return new ResolvedFrame(placement.anchor(), applyFrame(placement.anchor(), forward, up, right, placement.localOffset()), forward, up, right);
    }

    private static Vec3 applyFrame(Vec3 anchor, Vec3 forward, Vec3 up, Vec3 right, Vec3 localOffset) {
        return anchor.add(right.scale(localOffset.x)).add(up.scale(localOffset.y)).add(forward.scale(localOffset.z));
    }

    private static Vec3 safeNormalize(Vec3 vector, Vec3 fallback) {
        return vector.lengthSqr() < 1.0E-6D ? fallback : vector.normalize();
    }

    private static Vec3 bodyForward(LocalPlayer player) {
        float yaw = player.getPreciseBodyRotation(1.0F) * (float) (Math.PI / 180.0D);
        return new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw)).normalize();
    }

    private static boolean isLocallyExpired() {
        return activeSession != null
                && activeSession.expireCondition().timeoutTicks() > 0
                && sessionAgeTicks > activeSession.expireCondition().timeoutTicks();
    }

    private static double sessionDissolveProgress() {
        if (activeSession == null || activeSession.expireCondition().timeoutTicks() <= 0) {
            return 0.0D;
        }

        int timeout = Math.max(1, activeSession.expireCondition().timeoutTicks());
        double age = Math.max(0.0D, Math.min(1.0D, (double) sessionAgeTicks / timeout));
        return age < 0.2D ? 0.0D : (age - 0.2D) / 0.8D;
    }

    public enum ChoiceVisualState {
        IDLE,
        FOCUSED,
        READY,
        SUBMITTED,
        ACCEPTED,
        REJECTED
    }

    public record RenderChoice(UUID id, Vec3 anchor, Vec3 position, List<Integer> colors, boolean recommended, double radius, double focusProgress, double dissolveProgress, double resultProgress, ChoiceVisualState visualState, Component label, Component detail, GazeChoiceShape shape, Vec3 forward, Vec3 up, Vec3 right) {
        public int primaryColor() {
            return this.colors.isEmpty() ? 0xFFD8F4FF : this.colors.getFirst();
        }
    }

    private record ResolvedFrame(Vec3 anchor, Vec3 position, Vec3 forward, Vec3 up, Vec3 right) {
    }
}
