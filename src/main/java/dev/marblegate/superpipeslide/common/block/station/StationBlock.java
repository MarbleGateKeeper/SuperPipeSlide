package dev.marblegate.superpipeslide.common.block.station;

import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkSavedData;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.ServerPipeNetworkView;
import dev.marblegate.superpipeslide.common.core.route.storage.RouteNetworkSavedData;
import dev.marblegate.superpipeslide.common.core.route.model.station.StationGroup;
import dev.marblegate.superpipeslide.common.event.ServerEvents;
import dev.marblegate.superpipeslide.network.station.ClientboundOpenStationEditorPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

public class StationBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE_NORTH = Shapes.join(Shapes.box(0.0625, 0, 0.0625, 0.9375, 0.6875, 0.9375),
            Shapes.join(Shapes.box(0.0625, 0.6875, 0.25, 0.9375, 0.8125, 0.9375),
                    Shapes.box(0.0625, 0.8125, 0.5, 0.9375, 0.9375, 0.9375), BooleanOp.OR), BooleanOp.OR);
    private static final VoxelShape SHAPE_EAST = rotateShape(SHAPE_NORTH, 1);
    private static final VoxelShape SHAPE_SOUTH = rotateShape(SHAPE_NORTH, 2);
    private static final VoxelShape SHAPE_WEST = rotateShape(SHAPE_NORTH, 3);

    public StationBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            String language = placer instanceof ServerPlayer serverPlayer ? serverPlayer.clientInformation().language() : null;
            RouteNetworkSavedData.get(serverLevel.getServer()).createStationGroup(serverLevel.dimension(), pos, language);
            ServerEvents.queueRouteDataDelta(serverLevel);
        }
        super.setPlacedBy(level, pos, state, placer, stack);
    }

    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    private static VoxelShape rotateShape(VoxelShape shape, int quarterTurnsClockwise) {
        VoxelShape rotated = Shapes.empty();
        int turns = Math.floorMod(quarterTurnsClockwise, 4);
        for (AABB box : shape.toAabbs()) {
            rotated = Shapes.join(rotated, switch (turns) {
                case 1 -> Shapes.box(1.0D - box.maxZ, box.minY, box.minX, 1.0D - box.minZ, box.maxY, box.maxX);
                case 2 -> Shapes.box(1.0D - box.maxX, box.minY, 1.0D - box.maxZ, 1.0D - box.minX, box.maxY, 1.0D - box.minZ);
                case 3 -> Shapes.box(box.minZ, box.minY, 1.0D - box.maxX, box.maxZ, box.maxY, 1.0D - box.minX);
                default -> Shapes.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            }, BooleanOp.OR);
        }
        return rotated;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (RouteNetworkSavedData.get(level.getServer()).deleteStationGroupAt(level.dimension(), pos, PipeNetworkSavedData.get(level), ServerPipeNetworkView.of(level.getServer()))) {
            ServerEvents.queueRouteDataDelta(level);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        return this.open(level, pos, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return this.open(level, pos, player);
    }

    private InteractionResult open(Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        StationGroup stationGroup = RouteNetworkSavedData.get(serverLevel.getServer()).createStationGroup(serverLevel.dimension(), pos, serverPlayer.clientInformation().language());
        ServerEvents.sendRouteDataSnapshot(serverPlayer);
        PacketDistributor.sendToPlayer(serverPlayer, new ClientboundOpenStationEditorPayload(stationGroup.id()));
        return InteractionResult.SUCCESS_SERVER;
    }
}

