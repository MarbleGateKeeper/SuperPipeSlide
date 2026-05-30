package dev.marblegate.superpipeslide.common.block.anchor;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkSavedData;
import dev.marblegate.superpipeslide.common.item.pipe.PipeConnectorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PipeAnchorBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.box(0.25, 0.125, 0.25, 0.75, 0.875, 0.75);

    public PipeAnchorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel && !state.is(oldState.getBlock())) {
            PipeNetworkSavedData.get(serverLevel).addOrdinaryNode(PipeAnchorId.of(level, pos));
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        PipeAnchorId anchorId = PipeAnchorId.of(level, pos);
        PipeNetworkSavedData.get(level).removeOrdinaryNodeAndConnections(anchorId);
        PipeConnectorItem.clearSelectedAnchorFromPlayers(level, anchorId);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
