package dev.marblegate.superpipeslide.common.registry;

import com.mojang.serialization.Codec;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.appearance.model.PipeAppearanceProfile;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutTarget;
import dev.marblegate.superpipeslide.common.item.pipe.PipeConnectorMode;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.UUID;

public final class SPSDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(
            Registries.DATA_COMPONENT_TYPE,
            SuperPipeSlide.MODID
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PipeAnchorId>> SELECTED_ANCHOR = DATA_COMPONENTS.registerComponentType(
            "selected_anchor",
            builder -> builder.persistent(PipeAnchorId.CODEC)
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Vec3>> SELECTED_START_TANGENT = DATA_COMPONENTS.registerComponentType(
            "selected_start_tangent",
            builder -> builder.persistent(Vec3.CODEC)
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PipeConnectorMode>> PIPE_CONNECTOR_MODE = DATA_COMPONENTS.registerComponentType(
            "pipe_connector_mode",
            builder -> builder.persistent(PipeConnectorMode.CODEC)
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<Vec3>>> PENDING_CONTROL_POINTS = DATA_COMPONENTS.registerComponentType(
            "pending_control_points",
            builder -> builder.persistent(Vec3.CODEC.listOf())
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> PLATFORM_CLAIMER_STATION = DATA_COMPONENTS.registerComponentType(
            "platform_claimer_station",
            builder -> builder.persistent(UUIDUtil.STRING_CODEC)
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> PLATFORM_CLAIMER_STATION_NAME = DATA_COMPONENTS.registerComponentType(
            "platform_claimer_station_name",
            builder -> builder.persistent(Codec.STRING)
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PipeAppearanceProfile>> PIPE_APPEARANCE_DRAFT = DATA_COMPONENTS.registerComponentType(
            "pipe_appearance_draft",
            builder -> builder.persistent(PipeAppearanceProfile.CODEC)
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> PROJECTION_LAYOUT_SELECTED = DATA_COMPONENTS.registerComponentType(
            "projection_layout_selected",
            builder -> builder.persistent(UUIDUtil.STRING_CODEC)
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ProjectionLayoutTarget>> PROJECTION_LAYOUT_ACTIVE_TARGET = DATA_COMPONENTS.registerComponentType(
            "projection_layout_active_target",
            builder -> builder.persistent(ProjectionLayoutTarget.CODEC)
    );

    private SPSDataComponents() {
    }
}
