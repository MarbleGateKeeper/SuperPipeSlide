package dev.marblegate.superpipeslide.common.core.projection.component;

import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutTarget;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionRect;
import java.util.List;
import net.minecraft.network.chat.Component;

public record ProjectionComponentDescriptor(
        ProjectionComponentType type,
        ProjectionLayoutTarget target,
        ProjectionRect defaultBounds,
        ProjectionComponentSettings defaultSettings,
        ProjectionVisibleCondition defaultVisibleCondition,
        List<ProjectionVisibleCondition> visibleConditions) {
    public Component label() {
        return Component.translatable("screen.superpipeslide.projection_designer.component." + this.type.name().toLowerCase(java.util.Locale.ROOT));
    }

    public ProjectionComponent create(int layer) {
        return ProjectionComponent.of(this.type, this.defaultBounds.x(), this.defaultBounds.y(), this.defaultBounds.width(), this.defaultBounds.height(), layer, this.defaultSettings)
                .withVisibleCondition(this.defaultVisibleCondition);
    }
}
