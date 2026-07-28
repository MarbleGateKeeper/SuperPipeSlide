package dev.marblegate.superpipeslide.client.fullmap.screen;

import dev.marblegate.superpipeslide.client.core.navigation.ClientNavigationController;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Owns the full route map's navigation sheet state for {@code FullRouteMapScreen}: the
 * selected destination and its preview plan (with the data revisions it was computed
 * against), the sheet-expanded flag, the two independent confirm-arm flags (cross-dimension
 * start and destructive cancel), and the user-dragged drawer position as window-size ratios.
 *
 * <p>No rendering, no screen back-reference, no Minecraft calls: every method that would
 * need a player, a toast, or a screen transition stays on the screen, which drives this
 * controller through the smallest possible state API. The dependency points one way only:
 * screen &rarr; controller.
 */
final class FullMapNavigationSheetController {
    private static final long CANCEL_CONFIRM_TIMEOUT_MILLIS = 3000L;

    @Nullable
    private UUID selectedStationGroupId;
    @Nullable
    private ClientNavigationController.NavigationPlan selectedPlan;
    private boolean selectedPlanFromActiveSession;
    private long selectedPlanRouteRevision = Long.MIN_VALUE;
    private long selectedPlanPipeRevision = Long.MIN_VALUE;
    private boolean sheetExpanded;
    private boolean crossDimensionConfirmationArmed;
    private boolean cancelArmed;
    private long cancelArmedAtMillis;
    private double drawerUserXRatio = Double.NaN;
    private double drawerUserYRatio = Double.NaN;

    @Nullable
    UUID selectedStationGroupId() {
        return this.selectedStationGroupId;
    }

    @Nullable
    ClientNavigationController.NavigationPlan selectedPlan() {
        return this.selectedPlan;
    }

    boolean selectedPlanFromActiveSession() {
        return this.selectedPlanFromActiveSession;
    }

    long selectedPlanRouteRevision() {
        return this.selectedPlanRouteRevision;
    }

    long selectedPlanPipeRevision() {
        return this.selectedPlanPipeRevision;
    }

    boolean sheetExpanded() {
        return this.sheetExpanded;
    }

    void setSheetExpanded(boolean expanded) {
        this.sheetExpanded = expanded;
    }

    boolean crossDimensionConfirmationArmed() {
        return this.crossDimensionConfirmationArmed;
    }

    void setCrossDimensionConfirmationArmed(boolean armed) {
        this.crossDimensionConfirmationArmed = armed;
    }

    /** Lazy-expiring arm flag for the destructive cancel action (3s confirm window). */
    boolean cancelArmed() {
        if (this.cancelArmed && System.currentTimeMillis() - this.cancelArmedAtMillis > CANCEL_CONFIRM_TIMEOUT_MILLIS) {
            this.cancelArmed = false;
        }
        return this.cancelArmed;
    }

    void armCancel() {
        this.cancelArmed = true;
        this.cancelArmedAtMillis = System.currentTimeMillis();
    }

    void disarmCancel() {
        this.cancelArmed = false;
    }

    double drawerUserXRatio() {
        return this.drawerUserXRatio;
    }

    double drawerUserYRatio() {
        return this.drawerUserYRatio;
    }

    void setDrawerRatios(double xRatio, double yRatio) {
        this.drawerUserXRatio = xRatio;
        this.drawerUserYRatio = yRatio;
    }

    /** Records a new destination selection together with the plan preview for it. */
    void selectDestination(UUID stationGroupId, @Nullable ClientNavigationController.NavigationPlan plan, long routeRevision, long pipeRevision) {
        this.selectedStationGroupId = stationGroupId;
        this.selectedPlan = plan;
        this.selectedPlanFromActiveSession = false;
        this.selectedPlanRouteRevision = routeRevision;
        this.selectedPlanPipeRevision = pipeRevision;
        this.sheetExpanded = true;
        this.crossDimensionConfirmationArmed = false;
    }

    /** Adopts the active navigation session's plan (map opened while navigating). */
    void adoptActiveSession(ClientNavigationController.NavigationPlan plan) {
        this.selectedStationGroupId = plan.destinationStationGroupId();
        this.selectedPlan = plan;
        this.selectedPlanFromActiveSession = true;
        this.selectedPlanRouteRevision = plan.routeRevision();
        this.selectedPlanPipeRevision = plan.pipeRevision();
        this.sheetExpanded = true;
        this.crossDimensionConfirmationArmed = false;
    }

    /** Updates the preview after a data revision bump, keeping the selection. */
    void refreshPlan(@Nullable ClientNavigationController.NavigationPlan plan, long routeRevision, long pipeRevision) {
        this.selectedPlan = plan;
        this.selectedPlanRouteRevision = routeRevision;
        this.selectedPlanPipeRevision = pipeRevision;
        this.crossDimensionConfirmationArmed = false;
    }

    /** Clears the whole selection and both arm flags (cancel, start, close sheet). */
    void clearSelection() {
        this.selectedStationGroupId = null;
        this.selectedPlan = null;
        this.selectedPlanFromActiveSession = false;
        this.selectedPlanRouteRevision = Long.MIN_VALUE;
        this.selectedPlanPipeRevision = Long.MIN_VALUE;
        this.sheetExpanded = false;
        this.crossDimensionConfirmationArmed = false;
        this.cancelArmed = false;
    }

    /** Whether the stored plan's revisions already match the current data. */
    boolean planFresh(long routeRevision, long pipeRevision) {
        return this.selectedPlanRouteRevision == routeRevision && this.selectedPlanPipeRevision == pipeRevision;
    }

    /** Marks the stored plan as adopted from the active session (its revisions are truth). */
    void markPlanFromActiveSession(ClientNavigationController.NavigationPlan plan) {
        this.selectedStationGroupId = plan.destinationStationGroupId();
        this.selectedPlan = plan;
        this.selectedPlanFromActiveSession = true;
        this.selectedPlanRouteRevision = plan.routeRevision();
        this.selectedPlanPipeRevision = plan.pipeRevision();
    }

    Optional<UUID> selectedDestination() {
        return Optional.ofNullable(this.selectedStationGroupId);
    }
}
