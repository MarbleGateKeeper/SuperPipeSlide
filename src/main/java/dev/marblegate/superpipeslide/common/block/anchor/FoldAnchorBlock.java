package dev.marblegate.superpipeslide.common.block.anchor;

import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorDirectory;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorKind;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkSavedData;
import dev.marblegate.superpipeslide.common.item.pipe.PipeConnectorItem;
import dev.marblegate.superpipeslide.network.fold.ClientboundOpenFoldAnchorEditorPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

public class FoldAnchorBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.box(0.25, 0.125, 0.25, 0.75, 0.875, 0.75);
    private final FoldAnchorKind kind;

    public FoldAnchorBlock(BlockBehaviour.Properties properties, FoldAnchorKind kind) {
        super(properties);
        this.kind = kind;
    }

    public FoldAnchorKind kind() {
        return this.kind;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel && !state.is(oldState.getBlock())) {
            PipeNetworkSavedData.get(serverLevel).initializeFoldAnchor(PipeAnchorId.of(level, pos), this.kind);
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        PipeAnchorId anchorId = PipeAnchorId.of(level, pos);
        PipeNetworkSavedData.get(level).removeFoldAnchorAndConnections(anchorId, new FoldAnchorDirectory(level.getServer()));
        PipeConnectorItem.clearSelectedAnchorFromPlayers(level, anchorId);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof PipeConnectorItem) {
            return InteractionResult.PASS;
        }
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

        PipeAnchorId anchorId = PipeAnchorId.of(level, pos);
        PipeNetworkSavedData data = PipeNetworkSavedData.get(serverLevel);
        data.initializeFoldAnchor(anchorId, this.kind);
        PacketDistributor.sendToPlayer(serverPlayer, ClientboundOpenFoldAnchorEditorPayload.create(serverLevel.getServer(), anchorId));
        return InteractionResult.SUCCESS_SERVER;
    }
}
