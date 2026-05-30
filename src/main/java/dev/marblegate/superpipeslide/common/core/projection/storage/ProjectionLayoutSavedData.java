package dev.marblegate.superpipeslide.common.core.projection.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponent;
import dev.marblegate.superpipeslide.common.core.projection.layout.PlayerProjectionLayouts;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionCanvas;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutDefinition;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutSummary;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutTarget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class ProjectionLayoutSavedData extends SavedData {
    public static final Codec<ProjectionLayoutSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PlayerProjectionLayouts.CODEC.listOf().optionalFieldOf("players", List.of()).forGetter(ProjectionLayoutSavedData::playersForCodec),
            Codec.LONG.optionalFieldOf("revision", 0L).forGetter(ProjectionLayoutSavedData::revision)).apply(instance, ProjectionLayoutSavedData::new));

    public static final SavedDataType<ProjectionLayoutSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "projection_layouts"),
            ProjectionLayoutSavedData::new,
            CODEC);

    private final Map<UUID, PlayerProjectionLayouts> players = new LinkedHashMap<>();
    private long revision;

    public ProjectionLayoutSavedData() {
        this(List.of(), 0L);
    }

    public ProjectionLayoutSavedData(List<PlayerProjectionLayouts> players, long revision) {
        if (players != null) {
            for (PlayerProjectionLayouts library : players) {
                this.players.put(library.owner(), library);
            }
        }
        this.revision = Math.max(0L, revision);
    }

    public static ProjectionLayoutSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public long revision() {
        return this.revision;
    }

    public PlayerProjectionLayouts library(UUID playerId) {
        return this.getOrCreateLibrary(playerId);
    }

    private PlayerProjectionLayouts getLibraryOrEmpty(UUID playerId) {
        PlayerProjectionLayouts existing = this.players.get(playerId);
        return existing == null ? new PlayerProjectionLayouts(playerId, List.of(), Map.of()) : existing;
    }

    private PlayerProjectionLayouts getOrCreateLibrary(UUID playerId) {
        PlayerProjectionLayouts existing = this.players.get(playerId);
        if (existing != null) {
            return existing;
        }
        PlayerProjectionLayouts seeded = new PlayerProjectionLayouts(playerId, List.of(), Map.of());
        this.players.put(playerId, seeded);
        this.bumpRevision();
        return seeded;
    }

    public List<ProjectionLayoutSummary> summaries(UUID playerId) {
        return this.getLibraryOrEmpty(playerId).layouts().stream()
                .sorted(Comparator.comparingLong(ProjectionLayoutDefinition::updatedAt).reversed())
                .map(ProjectionLayoutSavedData::summaryFor)
                .toList();
    }

    public Optional<UUID> selectedLayoutId(UUID playerId, ProjectionLayoutTarget target) {
        return this.getLibraryOrEmpty(playerId).selectedLayoutId(target);
    }

    public Map<ProjectionLayoutTarget, UUID> selectedLayoutIds(UUID playerId) {
        return this.getLibraryOrEmpty(playerId).selectedLayoutIds();
    }

    public Optional<ProjectionLayoutDefinition> selectedLayout(UUID playerId, ProjectionLayoutTarget target) {
        PlayerProjectionLayouts library = this.getLibraryOrEmpty(playerId);
        ProjectionLayoutTarget normalizedTarget = target == null ? ProjectionLayoutTarget.STATION_NAME : target;
        return library.selectedLayoutId(normalizedTarget)
                .flatMap(id -> layout(playerId, id).filter(ProjectionLayoutSavedData::isValid).filter(layout -> layout.target() == normalizedTarget))
                .or(() -> library.layouts().stream().filter(ProjectionLayoutSavedData::isValid).filter(layout -> layout.target() == normalizedTarget).findFirst());
    }

    public Optional<ProjectionLayoutDefinition> layout(UUID playerId, UUID layoutId) {
        return this.getLibraryOrEmpty(playerId).layouts().stream().filter(layout -> layout.id().equals(layoutId)).findFirst();
    }

    public ProjectionLayoutDefinition save(UUID playerId, ProjectionLayoutDefinition layout, boolean select) {
        PlayerProjectionLayouts library = this.getOrCreateLibrary(playerId);
        List<ProjectionLayoutDefinition> next = new ArrayList<>(library.layouts().stream().filter(existing -> !existing.id().equals(layout.id())).toList());
        next.add(layout);
        next = next.stream()
                .sorted(Comparator.comparingLong(ProjectionLayoutDefinition::updatedAt).reversed())
                .limit(PlayerProjectionLayouts.MAX_LAYOUTS_PER_PLAYER)
                .toList();
        Map<ProjectionLayoutTarget, UUID> selected = new EnumMap<>(ProjectionLayoutTarget.class);
        selected.putAll(library.selectedLayoutIds());
        if (select) {
            selected.put(layout.target(), layout.id());
        }
        this.players.put(playerId, new PlayerProjectionLayouts(playerId, next, selected));
        this.bumpRevision();
        return layout;
    }

    public ProjectionLayoutDefinition saveCopy(UUID playerId, ProjectionLayoutDefinition layout, String name, boolean select) {
        ProjectionLayoutDefinition copy = new ProjectionLayoutDefinition(UUID.randomUUID(), name, ProjectionLayoutDefinition.CURRENT_SCHEMA_VERSION, layout.target(), layout.canvas(), freshComponents(layout.components()), System.currentTimeMillis());
        return this.save(playerId, copy, select);
    }

    public boolean delete(UUID playerId, UUID layoutId) {
        PlayerProjectionLayouts library = this.getLibraryOrEmpty(playerId);
        List<ProjectionLayoutDefinition> next = library.layouts().stream().filter(layout -> !layout.id().equals(layoutId)).toList();
        if (next.size() == library.layouts().size()) {
            return false;
        }
        ProjectionLayoutTarget deletedTarget = library.layouts().stream()
                .filter(layout -> layout.id().equals(layoutId))
                .findFirst()
                .map(ProjectionLayoutDefinition::target)
                .orElse(ProjectionLayoutTarget.STATION_NAME);
        Map<ProjectionLayoutTarget, UUID> selected = new EnumMap<>(ProjectionLayoutTarget.class);
        selected.putAll(library.selectedLayoutIds());
        UUID current = selected.get(deletedTarget);
        if (layoutId.equals(current) || current == null || next.stream().noneMatch(layout -> layout.id().equals(current) && layout.target() == deletedTarget && isValid(layout))) {
            next.stream()
                    .filter(ProjectionLayoutSavedData::isValid)
                    .filter(layout -> layout.target() == deletedTarget)
                    .findFirst()
                    .map(ProjectionLayoutDefinition::id)
                    .ifPresentOrElse(id -> selected.put(deletedTarget, id), () -> selected.remove(deletedTarget));
        }
        this.players.put(playerId, new PlayerProjectionLayouts(playerId, next, selected));
        this.bumpRevision();
        return true;
    }

    public void select(UUID playerId, ProjectionLayoutTarget target, UUID layoutId) {
        PlayerProjectionLayouts library = this.getOrCreateLibrary(playerId);
        ProjectionLayoutTarget normalizedTarget = target == null ? ProjectionLayoutTarget.STATION_NAME : target;
        Map<ProjectionLayoutTarget, UUID> selected = new EnumMap<>(ProjectionLayoutTarget.class);
        selected.putAll(library.selectedLayoutIds());
        boolean valid = library.layouts().stream()
                .filter(ProjectionLayoutSavedData::isValid)
                .anyMatch(layout -> layout.id().equals(layoutId) && layout.target() == normalizedTarget);
        if (valid) {
            selected.put(normalizedTarget, layoutId);
        }
        this.players.put(playerId, new PlayerProjectionLayouts(playerId, library.layouts(), selected));
        this.bumpRevision();
    }

    private static ProjectionLayoutSummary summaryFor(ProjectionLayoutDefinition layout) {
        if (!isValid(layout)) {
            return ProjectionLayoutSummary.invalid(layout.id(), layout.name(), layout.target(), "Layout contains invalid bounds or no renderable components");
        }
        return ProjectionLayoutSummary.of(layout);
    }

    public static List<ProjectionComponent> freshComponents(List<ProjectionComponent> components) {
        return components == null ? List.of() : components.stream().map(ProjectionComponent::withFreshId).toList();
    }

    public static boolean isValid(ProjectionLayoutDefinition layout) {
        return layout != null
                && layout.schemaVersion() == ProjectionLayoutDefinition.CURRENT_SCHEMA_VERSION
                && Float.isFinite(layout.canvas().width())
                && Float.isFinite(layout.canvas().height())
                && layout.canvas().width() >= ProjectionCanvas.MIN_WIDTH
                && layout.canvas().height() >= ProjectionCanvas.MIN_HEIGHT
                && !layout.components().isEmpty()
                && layout.components().stream().allMatch(component -> Float.isFinite(component.x())
                        && Float.isFinite(component.y())
                        && Float.isFinite(component.width())
                        && Float.isFinite(component.height())
                        && Float.isFinite(component.rotationDegrees())
                        && component.width() > 0.0F
                        && component.height() > 0.0F);
    }

    private List<PlayerProjectionLayouts> playersForCodec() {
        return List.copyOf(this.players.values());
    }

    private void bumpRevision() {
        this.revision++;
        this.setDirty();
    }
}
