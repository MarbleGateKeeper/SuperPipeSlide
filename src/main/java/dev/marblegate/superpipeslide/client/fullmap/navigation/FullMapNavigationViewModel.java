package dev.marblegate.superpipeslide.client.fullmap.navigation;

import dev.marblegate.superpipeslide.client.core.navigation.ClientNavigationController;
import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class FullMapNavigationViewModel {
    private FullMapNavigationViewModel() {
    }

    public static DestinationCard destinationCard(LocalPlayer player, ClientNavigationController.DestinationSearchResult result, boolean selected) {
        boolean sameDimension = result.levelKey().equals(player.level().dimension());
        ChipTone statusTone = !result.reachable() ? ChipTone.WARNING : sameDimension ? ChipTone.SUCCESS : ChipTone.INFO;
        String translated = result.translatedNames().isEmpty() ? "" : result.translatedNames().getFirst();
        String statusText = !result.reachable()
                ? Component.translatable("screen.superpipeslide.navigation.badge.unreachable").getString()
                : sameDimension
                ? Component.translatable("screen.superpipeslide.navigation.badge.reachable").getString()
                : Component.translatable("screen.superpipeslide.navigation.badge.cross_dimension").getString();
        return new DestinationCard(
                result.stationGroupId(),
                result.primaryName(),
                translated,
                dimensionLabel(result.levelKey()),
                result.reachable(),
                selected,
                !sameDimension,
                statusText,
                statusTone
        );
    }

    public static RoutePreview emptyPreview() {
        return new RoutePreview(
                Component.translatable("screen.superpipeslide.navigation.pick_destination").getString(),
                Component.translatable("screen.superpipeslide.navigation.pick_destination.body").getString(),
                "",
                false,
                false,
                false,
                false,
                PrimaryAction.UNAVAILABLE,
                Component.translatable("screen.superpipeslide.navigation.start").getString(),
                List.of(),
                List.of(),
                0xFF47A6FF
        );
    }

    public static RoutePreview unreachablePreview(UUID stationGroupId) {
        StationGroup station = ClientRouteDataCache.stationGroup(stationGroupId).orElse(null);
        String name = station == null ? Component.translatable("screen.superpipeslide.station.missing").getString() : station.primaryName();
        String subtitle = station == null || station.translatedNames().isEmpty()
                ? Component.translatable("screen.superpipeslide.navigation.unreachable.body").getString()
                : station.translatedNames().getFirst();
        return new RoutePreview(
                name,
                subtitle,
                Component.translatable("screen.superpipeslide.navigation.unreachable").getString(),
                false,
                false,
                false,
                false,
                PrimaryAction.UNAVAILABLE,
                Component.translatable("screen.superpipeslide.navigation.unreachable").getString(),
                List.of(new ItineraryStep(
                        ItineraryKind.UNREACHABLE,
                        Component.translatable("screen.superpipeslide.navigation.unreachable").getString(),
                        Component.translatable("screen.superpipeslide.navigation.unreachable.body").getString(),
                        "",
                        List.of(),
                        List.of(0xFFFFB13B),
                        true,
                        false
                )),
                List.of(),
                0xFFFFB13B
        );
    }

    public static RoutePreview routePreview(ClientNavigationController.NavigationPlan plan) {
        String destinationName = stationName(plan.destinationStationGroupId());
        String destinationSubtitle = ClientRouteDataCache.stationGroup(plan.destinationStationGroupId())
                .map(StationGroup::translatedNames)
                .filter(values -> !values.isEmpty())
                .map(List::getFirst)
                .orElseGet(() -> dimensionOfStation(plan.destinationStationGroupId()).map(FullMapNavigationViewModel::dimensionLabel).orElse(""));
        boolean activeSameDestination = ClientNavigationController.activeSessionSnapshot()
                .map(snapshot -> snapshot.plan().destinationStationGroupId().equals(plan.destinationStationGroupId()))
                .orElse(false);
        boolean activeOtherDestination = ClientNavigationController.isNavigating() && !activeSameDestination;
        PrimaryAction action = activeSameDestination ? PrimaryAction.REPLAN : activeOtherDestination ? PrimaryAction.REPLACE : PrimaryAction.START;
        String actionLabel = switch (action) {
            case REPLAN -> Component.translatable("screen.superpipeslide.navigation.replan").getString();
            case REPLACE -> Component.translatable("screen.superpipeslide.navigation.replace").getString();
            case START -> Component.translatable("screen.superpipeslide.navigation.start").getString();
            default -> Component.translatable("screen.superpipeslide.navigation.start").getString();
        };
        List<ItineraryStep> itinerary = itinerary(plan);
        List<String> warnings = warnings(plan);
        return new RoutePreview(
                destinationName,
                destinationSubtitle,
                routeSummary(plan),
                true,
                activeSameDestination,
                activeOtherDestination,
                plan.crossDimensionTransferCount() > 0 || plan.crossDimensionFinalWalk(),
                action,
                actionLabel,
                itinerary,
                warnings,
                primaryColor(plan.primaryColors())
        );
    }

    private static String routeSummary(ClientNavigationController.NavigationPlan plan) {
        ArrayList<String> parts = new ArrayList<>();
        if (plan.segments().isEmpty()) {
            parts.add(Component.translatable("screen.superpipeslide.navigation.badge.already_here").getString());
            return String.join(" / ", parts);
        }
        parts.add(Component.translatable("screen.superpipeslide.navigation.badge.time", timeText(plan.estimatedTicks())).getString());
        parts.add(Component.translatable("screen.superpipeslide.navigation.badge.transfers", plan.transferCount()).getString());
        if (plan.outOfStationTransferCount() > 0) {
            parts.add(Component.translatable("screen.superpipeslide.navigation.badge.out_station", plan.outOfStationTransferCount()).getString());
        }
        if (plan.crossDimensionTransferCount() > 0 || plan.crossDimensionFinalWalk()) {
            parts.add(Component.translatable("screen.superpipeslide.navigation.badge.cross_dimension").getString());
        }
        if (plan.finalWalk()) {
            parts.add(Component.translatable("screen.superpipeslide.navigation.badge.final_walk").getString());
        }
        return String.join(" / ", parts);
    }

    private static List<ItineraryStep> itinerary(ClientNavigationController.NavigationPlan plan) {
        ArrayList<ItineraryStep> steps = new ArrayList<>();
        int primary = primaryColor(plan.primaryColors());
        if (plan.segments().isEmpty()) {
            steps.add(new ItineraryStep(
                    ItineraryKind.DESTINATION,
                    Component.translatable("screen.superpipeslide.navigation.timeline.already_here").getString(),
                    stationName(plan.destinationStationGroupId()),
                    "",
                    List.of(),
                    List.of(primary),
                    false,
                    false
            ));
            return steps;
        }

        steps.add(new ItineraryStep(
                ItineraryKind.ORIGIN,
                Component.translatable("screen.superpipeslide.navigation.itinerary.origin").getString(),
                "",
                "",
                List.of(),
                List.of(primary),
                false,
                false
        ));

        ClientNavigationController.NavigationSegment first = plan.segments().getFirst();
        steps.add(new ItineraryStep(
                ItineraryKind.WALK_TO_BOARD,
                Component.translatable("screen.superpipeslide.navigation.timeline.walk_to_board").getString(),
                Component.translatable("screen.superpipeslide.navigation.itinerary.walk_to_board.detail",
                        stationName(first.boardingPlatformStopId()),
                        distanceText(plan.initialWalkDistance())).getString(),
                "",
                List.of(),
                first.colors(),
                false,
                false
        ));

        for (int i = 0; i < plan.segments().size(); i++) {
            ClientNavigationController.NavigationSegment segment = plan.segments().get(i);
            int stopCount = Math.max(0, segment.stationSequence().size() - 1);
            steps.add(new ItineraryStep(
                    ItineraryKind.RIDE,
                    segment.lineName(),
                    Component.translatable("screen.superpipeslide.navigation.itinerary.ride.detail",
                            stationName(segment.alightingPlatformStopId()),
                            stopCount).getString(),
                    segment.lineName(),
                    stationNames(segment.stationSequence()),
                    segment.colors(),
                    false,
                    segment.stationSequence().size() > 7
            ));
            if (segment.finalWalkInstruction().isPresent()) {
                ClientNavigationController.FinalWalkInstruction instruction = segment.finalWalkInstruction().get();
                boolean crossDimension = instruction.kind() == ClientNavigationController.TransferKind.CROSS_DIMENSION_OUT_OF_STATION;
                steps.add(new ItineraryStep(
                        crossDimension ? ItineraryKind.CROSS_DIMENSION_FINAL_WALK : ItineraryKind.FINAL_WALK,
                        Component.translatable(crossDimension
                                ? "screen.superpipeslide.navigation.timeline.cross_dimension_final_walk"
                                : "screen.superpipeslide.navigation.timeline.final_walk").getString(),
                        Component.translatable("screen.superpipeslide.navigation.timeline.final_walk.detail", stationName(plan.destinationStationGroupId())).getString(),
                        "",
                        List.of(),
                        segment.colors(),
                        crossDimension,
                        false
                ));
            }
            if (i + 1 < plan.segments().size()) {
                ClientNavigationController.NavigationSegment next = plan.segments().get(i + 1);
                ClientNavigationController.TransferInstruction transfer = segment.transferInstruction()
                        .orElseGet(() -> ClientNavigationController.TransferInstruction.sameStationFallback(segment, next));
                steps.add(transferStep(transfer, next));
            }
        }
        steps.add(new ItineraryStep(
                ItineraryKind.DESTINATION,
                Component.translatable("screen.superpipeslide.navigation.timeline.destination").getString(),
                stationName(plan.destinationStationGroupId()),
                "",
                List.of(),
                List.of(primary),
                false,
                false
        ));
        return List.copyOf(steps);
    }

    private static ItineraryStep transferStep(ClientNavigationController.TransferInstruction transfer, ClientNavigationController.NavigationSegment next) {
        ItineraryKind kind = switch (transfer.kind()) {
            case SAME_STATION -> ItineraryKind.SAME_STATION_TRANSFER;
            case OUT_OF_STATION -> ItineraryKind.OUT_OF_STATION_TRANSFER;
            case CROSS_DIMENSION_OUT_OF_STATION -> ItineraryKind.CROSS_DIMENSION_TRANSFER;
        };
        String titleKey = switch (transfer.kind()) {
            case SAME_STATION -> "screen.superpipeslide.navigation.timeline.same_station_transfer";
            case OUT_OF_STATION -> "screen.superpipeslide.navigation.timeline.out_station_transfer";
            case CROSS_DIMENSION_OUT_OF_STATION -> "screen.superpipeslide.navigation.timeline.cross_dimension_transfer";
        };
        String detailKey = switch (transfer.kind()) {
            case SAME_STATION -> "screen.superpipeslide.navigation.timeline.same_station_transfer.detail";
            case OUT_OF_STATION -> "screen.superpipeslide.navigation.timeline.out_station_transfer.detail";
            case CROSS_DIMENSION_OUT_OF_STATION -> "screen.superpipeslide.navigation.timeline.cross_dimension_transfer.detail";
        };
        return new ItineraryStep(
                kind,
                Component.translatable(titleKey).getString(),
                Component.translatable(detailKey, stationName(next.boardingPlatformStopId()), next.lineName()).getString(),
                next.lineName(),
                List.of(),
                next.colors(),
                transfer.kind() == ClientNavigationController.TransferKind.CROSS_DIMENSION_OUT_OF_STATION,
                false
        );
    }

    private static List<String> stationNames(List<UUID> platformStopIds) {
        ArrayList<String> names = new ArrayList<>(platformStopIds.size());
        for (UUID id : platformStopIds) {
            names.add(stationName(id));
        }
        return List.copyOf(names);
    }

    private static List<String> warnings(ClientNavigationController.NavigationPlan plan) {
        ArrayList<String> warnings = new ArrayList<>();
        if (plan.crossDimensionTransferCount() > 0) {
            warnings.add(Component.translatable("screen.superpipeslide.navigation.warning.cross_dimension_transfer").getString());
        }
        if (plan.crossDimensionFinalWalk()) {
            warnings.add(Component.translatable("screen.superpipeslide.navigation.warning.cross_dimension_final_walk").getString());
        } else if (plan.finalWalk()) {
            warnings.add(Component.translatable("screen.superpipeslide.navigation.warning.final_walk").getString());
        }
        if (plan.outOfStationTransferCount() > 0) {
            warnings.add(Component.translatable("screen.superpipeslide.navigation.warning.out_station_transfer").getString());
        }
        return warnings;
    }

    public static String stationName(UUID platformStopOrStationGroupId) {
        Optional<PlatformStop> platformStop = ClientRouteDataCache.platformStop(platformStopOrStationGroupId);
        if (platformStop.isPresent()) {
            return ClientRouteDataCache.stationGroup(platformStop.get().stationGroupId())
                    .map(StationGroup::primaryName)
                    .orElseGet(() -> Component.translatable("screen.superpipeslide.station.missing").getString());
        }
        return ClientRouteDataCache.stationGroup(platformStopOrStationGroupId)
                .map(StationGroup::primaryName)
                .orElseGet(() -> Component.translatable("screen.superpipeslide.station.missing").getString());
    }

    public static String dimensionLabel(ResourceKey<Level> dimension) {
        String id = dimension.identifier().toString();
        return switch (id) {
            case "minecraft:overworld" -> Component.translatable("screen.superpipeslide.full_map.dimension.overworld").getString();
            case "minecraft:the_nether" -> Component.translatable("screen.superpipeslide.full_map.dimension.nether").getString();
            case "minecraft:the_end" -> Component.translatable("screen.superpipeslide.full_map.dimension.end").getString();
            default -> dimension.identifier().getPath();
        };
    }

    private static Optional<ResourceKey<Level>> dimensionOfStation(UUID stationGroupId) {
        return ClientRouteDataCache.stationGroup(stationGroupId).map(StationGroup::levelKey);
    }

    private static String distanceText(double distance) {
        if (!Double.isFinite(distance) || distance > 99999.0D) {
            return Component.translatable("screen.superpipeslide.navigation.distance_far").getString();
        }
        if (distance >= 1000.0D) {
            return Component.translatable("screen.superpipeslide.navigation.distance_km", String.format(java.util.Locale.ROOT, "%.1f", distance / 1000.0D)).getString();
        }
        return Component.translatable("screen.superpipeslide.navigation.distance_m", Math.round(distance)).getString();
    }

    private static String timeText(int ticks) {
        int seconds = Math.max(0, Math.round(ticks / 20.0F));
        int minutes = seconds / 60;
        int remain = seconds % 60;
        return minutes > 0 ? minutes + ":" + String.format(java.util.Locale.ROOT, "%02d", remain) : remain + "s";
    }

    private static int primaryColor(List<Integer> colors) {
        return colors == null || colors.isEmpty() ? 0xFF47A6FF : 0xFF000000 | colors.getFirst() & 0x00FFFFFF;
    }

    public record DestinationCard(
            UUID stationGroupId,
            String primaryName,
            String translatedName,
            String dimensionText,
            boolean reachable,
            boolean selected,
            boolean crossDimension,
            String statusText,
            ChipTone statusTone
    ) {
    }

    public record RoutePreview(
            String destinationName,
            String destinationSubtitle,
            String summaryText,
            boolean reachable,
            boolean activeSameDestination,
            boolean activeOtherDestination,
            boolean needsCrossDimensionConfirmation,
            PrimaryAction primaryAction,
            String primaryActionLabel,
            List<ItineraryStep> itinerary,
            List<String> warnings,
            int primaryColor
    ) {
        public RoutePreview {
            itinerary = List.copyOf(itinerary);
            warnings = List.copyOf(warnings);
        }
    }

    public record ItineraryStep(
            ItineraryKind kind,
            String title,
            String detail,
            String lineName,
            List<String> stationNames,
            List<Integer> colors,
            boolean warning,
            boolean expandable
    ) {
        public ItineraryStep {
            stationNames = List.copyOf(stationNames);
            colors = List.copyOf(colors);
        }
    }

    public enum ChipTone {
        NEUTRAL,
        SUCCESS,
        WARNING,
        INFO
    }

    public enum ItineraryKind {
        ORIGIN,
        WALK_TO_BOARD,
        RIDE,
        SAME_STATION_TRANSFER,
        OUT_OF_STATION_TRANSFER,
        CROSS_DIMENSION_TRANSFER,
        FINAL_WALK,
        CROSS_DIMENSION_FINAL_WALK,
        DESTINATION,
        UNREACHABLE
    }

    public enum PrimaryAction {
        START,
        REPLACE,
        REPLAN,
        UNAVAILABLE
    }
}
