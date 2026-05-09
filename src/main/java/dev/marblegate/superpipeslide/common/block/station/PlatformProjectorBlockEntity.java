package dev.marblegate.superpipeslide.common.block.station;

import dev.marblegate.superpipeslide.common.core.projection.layout.AppliedProjectionLayout;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutTarget;
import dev.marblegate.superpipeslide.common.core.projection.template.ProjectionTemplates;
import dev.marblegate.superpipeslide.network.platform.ClientboundPlatformProjectorConfigPayload;
import dev.marblegate.superpipeslide.common.core.route.storage.RouteNetworkSavedData;
import dev.marblegate.superpipeslide.common.core.route.model.platform.PlatformStop;
import dev.marblegate.superpipeslide.common.registry.SPSBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

public class PlatformProjectorBlockEntity extends BlockEntity {
    private static final double CONFIG_SYNC_DISTANCE_SQR = 192.0D * 192.0D;

    private PlatformProjectorConfig config = PlatformProjectorConfig.defaults();
    private AppliedProjectionLayout appliedLayout = ProjectionTemplates.defaultLayout(ProjectionLayoutTarget.PLATFORM).compile("");

    public PlatformProjectorBlockEntity(BlockPos pos, BlockState blockState) {
        super(SPSBlockEntities.PLATFORM_PROJECTOR.get(), pos, blockState);
    }

    public PlatformProjectorConfig config() {
        return this.config;
    }

    public AppliedProjectionLayout appliedLayout() {
        return this.appliedLayout;
    }

    public void applyConfig(PlatformProjectorConfig config) {
        this.config = config;
        this.setChangedAndSyncConfig();
    }

    public void acceptClientConfig(PlatformProjectorConfig config) {
        this.config = config;
    }

    public void applyLayout(AppliedProjectionLayout layout) {
        this.appliedLayout = layout == null || layout.components().isEmpty() || layout.target() != ProjectionLayoutTarget.PLATFORM
                ? ProjectionTemplates.defaultLayout(ProjectionLayoutTarget.PLATFORM).compile("")
                : layout;
        this.setChangedAndSyncFull();
    }

    public void bindNearestPlatform(ServerLevel level, double radius) {
        Optional<PlatformStop> nearest = nearestPlatform(level, level.dimension(), this.worldPosition, radius);
        Optional<UUID> platformStopId = nearest.map(PlatformStop::id);
        Optional<UUID> layoutId = platformStopId.flatMap(id -> RouteNetworkSavedData.get(level.getServer()).routeLayoutIdsForPlatformStop(id).stream().findFirst());
        this.config = this.config.withBinding(PlatformProjectorConfig.BindingMode.AUTO, platformStopId, layoutId);
        this.setChangedAndSyncConfig();
    }

    private static Optional<PlatformStop> nearestPlatform(ServerLevel level, ResourceKey<Level> levelKey, BlockPos pos, double radius) {
        RouteNetworkSavedData data = RouteNetworkSavedData.get(level.getServer());
        double radiusSqr = radius * radius;
        return data.platformStops().stream()
                .filter(stop -> stop.connectionRef().levelKey().equals(levelKey))
                .filter(stop -> data.stationGroup(stop.stationGroupId()).map(station -> station.stationBlockPos().distSqr(pos) <= radiusSqr).orElse(false))
                .min(Comparator.comparingDouble(stop -> data.stationGroup(stop.stationGroupId()).map(station -> station.stationBlockPos().distSqr(pos)).orElse(Double.MAX_VALUE)));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        writeConfig(output, this.config);
        output.store("applied_layout", AppliedProjectionLayout.CODEC, this.appliedLayout);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.config = readConfig(input);
        this.appliedLayout = input.read("applied_layout", AppliedProjectionLayout.CODEC).orElseGet(() -> ProjectionTemplates.defaultLayout(ProjectionLayoutTarget.PLATFORM).compile(""));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void setChangedAndSyncConfig() {
        this.setChanged();
        if (this.level instanceof ServerLevel serverLevel) {
            ClientboundPlatformProjectorConfigPayload payload = new ClientboundPlatformProjectorConfigPayload(this.worldPosition, this.config);
            for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
                if (player.level() == serverLevel && player.blockPosition().distSqr(this.worldPosition) <= CONFIG_SYNC_DISTANCE_SQR) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
        }
    }

    private void setChangedAndSyncFull() {
        this.setChanged();
        if (this.level != null) {
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
        }
    }

    private static void writeConfig(ValueOutput output, PlatformProjectorConfig config) {
        output.putString("binding_mode", config.bindingMode().name());
        config.platformStopId().ifPresent(id -> output.store("platform_stop", UUIDUtil.STRING_CODEC, id));
        config.routeLayoutId().ifPresent(id -> output.store("route_layout", UUIDUtil.STRING_CODEC, id));
        output.putString("direction", config.direction().name());
        output.putFloat("offset_x", config.offsetX());
        output.putFloat("offset_y", config.offsetY());
        output.putBoolean("backside_projection", config.backsideProjection());
    }

    private static PlatformProjectorConfig readConfig(ValueInput input) {
        PlatformProjectorConfig defaults = PlatformProjectorConfig.defaults();
        Optional<UUID> platformStop = input.read("platform_stop", UUIDUtil.STRING_CODEC);
        Optional<UUID> routeLayout = input.read("route_layout", UUIDUtil.STRING_CODEC);
        return new PlatformProjectorConfig(
                PlatformProjectorConfig.enumByName(PlatformProjectorConfig.BindingMode.class, input.getStringOr("binding_mode", defaults.bindingMode().name()), defaults.bindingMode()),
                platformStop,
                routeLayout,
                PlatformProjectorConfig.enumByName(PlatformProjectorConfig.PlatformProjectionDirection.class, input.getStringOr("direction", defaults.direction().name()), defaults.direction()),
                input.getFloatOr("offset_x", defaults.offsetX()),
                input.getFloatOr("offset_y", defaults.offsetY()),
                input.getBooleanOr("backside_projection", defaults.backsideProjection())
        );
    }
}
