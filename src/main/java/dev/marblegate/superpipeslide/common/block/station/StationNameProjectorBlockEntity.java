package dev.marblegate.superpipeslide.common.block.station;

import dev.marblegate.superpipeslide.common.core.projection.layout.AppliedProjectionLayout;
import dev.marblegate.superpipeslide.common.core.projection.template.ProjectionTemplates;
import dev.marblegate.superpipeslide.network.station.ClientboundStationNameProjectorConfigPayload;
import dev.marblegate.superpipeslide.common.core.route.storage.RouteNetworkSavedData;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
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

public class StationNameProjectorBlockEntity extends BlockEntity {
    private static final double CONFIG_SYNC_DISTANCE_SQR = 192.0D * 192.0D;

    private StationNameProjectorConfig config = StationNameProjectorConfig.defaults();
    private AppliedProjectionLayout appliedLayout = ProjectionTemplates.defaultLayout().compile("");

    public StationNameProjectorBlockEntity(BlockPos pos, BlockState blockState) {
        super(SPSBlockEntities.STATION_NAME_PROJECTOR.get(), pos, blockState);
    }

    public StationNameProjectorConfig config() {
        return this.config;
    }

    public AppliedProjectionLayout appliedLayout() {
        return this.appliedLayout;
    }

    public void applyConfig(StationNameProjectorConfig config) {
        this.config = config;
        this.setChangedAndSyncConfig();
    }

    public void acceptClientConfig(StationNameProjectorConfig config) {
        this.config = config;
    }

    public void applyLayout(AppliedProjectionLayout layout) {
        this.appliedLayout = layout == null || layout.components().isEmpty() ? ProjectionTemplates.defaultLayout().compile("") : layout;
        this.setChangedAndSyncFull();
    }

    public void bindNearestStation(ServerLevel level, double radius) {
        Optional<UUID> nearest = nearestStation(level, level.dimension(), this.worldPosition, radius)
                .map(StationGroup::id);
        this.config = this.config.withBinding(StationNameProjectorConfig.BindingMode.AUTO, nearest);
        this.setChangedAndSyncConfig();
    }

    private static Optional<StationGroup> nearestStation(ServerLevel level, ResourceKey<Level> levelKey, BlockPos pos, double radius) {
        double radiusSqr = radius * radius;
        return RouteNetworkSavedData.get(level.getServer()).stationGroups().stream()
                .filter(station -> station.levelKey().equals(levelKey))
                .filter(station -> station.stationBlockPos().distSqr(pos) <= radiusSqr)
                .min(Comparator.comparingDouble(station -> station.stationBlockPos().distSqr(pos)));
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
        this.appliedLayout = input.read("applied_layout", AppliedProjectionLayout.CODEC).orElseGet(() -> ProjectionTemplates.defaultLayout().compile(""));
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
            ClientboundStationNameProjectorConfigPayload payload = new ClientboundStationNameProjectorConfigPayload(this.worldPosition, this.config);
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

    private static void writeConfig(ValueOutput output, StationNameProjectorConfig config) {
        output.putString("binding_mode", config.bindingMode().name());
        config.stationGroupId().ifPresent(id -> output.store("station", UUIDUtil.STRING_CODEC, id));
        output.putFloat("offset_x", config.offsetX());
        output.putFloat("offset_y", config.offsetY());
        output.putBoolean("show_exit", config.showExit());
        output.putString("exit_label", config.exitLabel());
        output.putBoolean("backside_projection", config.backsideProjection());
    }

    private static StationNameProjectorConfig readConfig(ValueInput input) {
        StationNameProjectorConfig defaults = StationNameProjectorConfig.defaults();
        Optional<UUID> station = input.read("station", UUIDUtil.STRING_CODEC);
        return new StationNameProjectorConfig(
                StationNameProjectorConfig.enumByName(StationNameProjectorConfig.BindingMode.class, input.getStringOr("binding_mode", defaults.bindingMode().name()), defaults.bindingMode()),
                station,
                input.getFloatOr("offset_x", defaults.offsetX()),
                input.getFloatOr("offset_y", defaults.offsetY()),
                input.getBooleanOr("show_exit", defaults.showExit()),
                input.getStringOr("exit_label", defaults.exitLabel()),
                input.getBooleanOr("backside_projection", defaults.backsideProjection())
        );
    }
}
