package dev.marblegate.superpipeslide.common.item.pipe;

import dev.marblegate.superpipeslide.common.core.geometry.CurveSpec;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkSavedData;
import dev.marblegate.superpipeslide.common.registry.SPSBlocks;
import dev.marblegate.superpipeslide.common.registry.SPSDataComponents;
import dev.marblegate.superpipeslide.config.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;

public class PipeConnectorItem extends Item {
    private static final int MAX_CONTROL_POINTS = 8;
    private static final int CONNECTION_LENGTH_VALIDATION_SAMPLES = 96;
    private final PipeConnectorMode connectorMode;

    public PipeConnectorItem(Properties properties) {
        this(properties, PipeConnectorMode.GAZE_CURVE);
    }

    public PipeConnectorItem(Properties properties, PipeConnectorMode connectorMode) {
        super(properties);
        this.connectorMode = connectorMode;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (stack.get(SPSDataComponents.SELECTED_ANCHOR.get()) == null) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.no_anchor_selected").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS_SERVER;
        }

        clearSelectedAnchor(stack);
        player.sendOverlayMessage(Component.translatable("message.superpipeslide.selection_cleared").withStyle(ChatFormatting.GRAY));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        PipeConnectorMode mode = mode(itemStack);
        builder.accept(Component.translatable("tooltip.superpipeslide.pipe_connector.mode", modeName(mode)).withStyle(ChatFormatting.AQUA));

        PipeAnchorId selected = itemStack.get(SPSDataComponents.SELECTED_ANCHOR.get());
        if (selected == null) {
            builder.accept(Component.translatable("tooltip.superpipeslide.pipe_connector.no_selection").withStyle(ChatFormatting.GRAY));
        } else {
            BlockPos pos = selected.blockPos();
            builder.accept(Component.translatable("tooltip.superpipeslide.pipe_connector.selected_anchor", pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GRAY));
        }

        if (mode == PipeConnectorMode.CONTROLLED) {
            builder.accept(Component.translatable("tooltip.superpipeslide.pipe_connector.control_points", controlPoints(itemStack).size(), MAX_CONTROL_POINTS).withStyle(ChatFormatting.GRAY));
            builder.accept(Component.translatable("tooltip.superpipeslide.pipe_connector.control_hint").withStyle(ChatFormatting.DARK_GRAY));
            builder.accept(Component.translatable("tooltip.superpipeslide.pipe_connector.control_finish_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
        builder.accept(Component.translatable("tooltip.superpipeslide.pipe_connector.clear_hint").withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable("tooltip.superpipeslide.pipe_connector.quick_build_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();

        if (player == null) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return isConnectorAnchor(state) || context.getItemInHand().get(SPSDataComponents.SELECTED_ANCHOR.get()) != null ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        ItemStack stack = context.getItemInHand();
        PipeNetworkSavedData data = PipeNetworkSavedData.get(serverLevel);

        if (!isConnectorAnchor(state)) {
            return useOnBlockFace(context, serverLevel, stack, data);
        }

        PipeAnchorId clicked = PipeAnchorId.of(level, pos);
        boolean clickedBranchAnchor = state.is(SPSBlocks.BRANCH_ANCHOR.get());
        boolean clickedFoldAnchor = state.is(SPSBlocks.SPACE_FOLD_ANCHOR.get()) || state.is(SPSBlocks.DIMENSION_FOLD_ANCHOR.get());
        if (!clickedBranchAnchor) {
            if (!clickedFoldAnchor) {
                data.addOrdinaryNode(clicked);
            }
        } else if (data.branchNodeAt(clicked).isEmpty()) {
            data.initializeBranchNode(clicked);
        }

        if (player.isShiftKeyDown()) {
            int removed = clickedBranchAnchor ? data.removeBranchNodeAndConnections(clicked) : data.removeConnectionsTouching(clicked);
            clearSelectedAnchor(stack);
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.connections_removed", removed).withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS_SERVER;
        }

        PipeAnchorId first = stack.get(SPSDataComponents.SELECTED_ANCHOR.get());
        if (first == null) {
            int maxConnections = clickedFoldAnchor ? 1 : 2;
            if (!clickedBranchAnchor && data.connectionCount(clicked) >= maxConnections) {
                player.sendOverlayMessage(Component.translatable("message.superpipeslide.anchor_connection_limit").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }

            stack.set(SPSDataComponents.SELECTED_ANCHOR.get(), clicked);
            stack.set(SPSDataComponents.SELECTED_START_TANGENT.get(), player.getLookAngle().normalize());
            stack.remove(SPSDataComponents.PENDING_CONTROL_POINTS.get());
            String selectedMessage = clickedBranchAnchor ? "message.superpipeslide.branch_anchor_selected" : "message.superpipeslide.anchor_selected";
            player.sendOverlayMessage(Component.translatable(selectedMessage, pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS_SERVER;
        }

        if (first.equals(clicked)) {
            clearSelectedAnchor(stack);
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.selection_cleared").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS_SERVER;
        }

        return createConnection(serverLevel, player, stack, data, first, clicked, false);
    }

    private static InteractionResult useOnBlockFace(UseOnContext context, ServerLevel level, ItemStack stack, PipeNetworkSavedData data) {
        Player player = context.getPlayer();
        PipeAnchorId first = stack.get(SPSDataComponents.SELECTED_ANCHOR.get());
        if (player == null || player.isShiftKeyDown() || first == null) {
            return InteractionResult.PASS;
        }

        if (!isConnectorAnchor(level.getBlockState(first.blockPos()))) {
            clearSelectedAnchor(stack);
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.first_anchor_missing").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        if (mode(stack) == PipeConnectorMode.CONTROLLED) {
            return addControlPoint(context, player, stack);
        }

        if (!player.getAbilities().instabuild) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.creative_quick_anchor_only").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        BlockPos anchorPos = context.getClickedPos().relative(context.getClickedFace());
        if (!level.isEmptyBlock(anchorPos)) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.anchor_space_blocked").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        PipeAnchorId created = PipeAnchorId.of(level, anchorPos);
        InteractionResult result = createConnection(level, player, stack, data, first, created, true);
        if (result == InteractionResult.SUCCESS_SERVER) {
            level.setBlock(anchorPos, SPSBlocks.PIPE_ANCHOR.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        return result;
    }

    private static InteractionResult createConnection(ServerLevel level, Player player, ItemStack stack, PipeNetworkSavedData data, PipeAnchorId first, PipeAnchorId clicked, boolean keepBuilding) {
        if (!first.levelKey().equals(clicked.levelKey())) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.cross_dimension_connection").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (first.equals(clicked)) {
            clearSelectedAnchor(stack);
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.selection_cleared").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }

        if (!isConnectorAnchor(level.getBlockState(first.blockPos()))) {
            clearSelectedAnchor(stack);
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.first_anchor_missing").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        if (data.hasConnectionBetween(first, clicked)) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.connection_already_exists").withStyle(ChatFormatting.YELLOW));
            return InteractionResult.FAIL;
        }

        CurveSpec curveSpec = curveSpec(stack, player, first, clicked);
        PipeConnection candidateConnection = PipeConnection.withCurve(first, clicked, curveSpec);
        double length = Math.max(candidateConnection.length(), candidateConnection.sampledLength(CONNECTION_LENGTH_VALIDATION_SAMPLES));
        if (length > Config.MAX_CONNECTION_LENGTH.getAsDouble()) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.connection_too_long", String.format("%.1f", length)).withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        boolean createdOrdinaryNode = false;
        if (keepBuilding && data.node(clicked).isEmpty()) {
            if (!player.getAbilities().instabuild) {
                player.sendOverlayMessage(Component.translatable("message.superpipeslide.creative_quick_anchor_only").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            data.addOrdinaryNode(clicked);
            createdOrdinaryNode = true;
        }

        boolean firstBranch = data.isBranchNode(first);
        boolean clickedBranch = data.isBranchNode(clicked);
        boolean firstFold = data.isFoldAnchorNode(first);
        boolean clickedFold = data.isFoldAnchorNode(clicked);
        boolean firstBranchBlock = level.getBlockState(first.blockPos()).is(SPSBlocks.BRANCH_ANCHOR.get());
        boolean clickedBranchBlock = level.getBlockState(clicked.blockPos()).is(SPSBlocks.BRANCH_ANCHOR.get());
        if ((firstBranchBlock && !firstBranch) || (clickedBranchBlock && !clickedBranch)) {
            cleanupCreatedOrdinaryNode(data, clicked, createdOrdinaryNode);
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.branch_node_missing").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (data.isSpecialAnchorNode(first) && data.isSpecialAnchorNode(clicked)) {
            cleanupCreatedOrdinaryNode(data, clicked, createdOrdinaryNode);
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.connection_not_allowed").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if ((!firstBranch && !data.canUseStandardConnectionEndpoint(first)) || (!clickedBranch && !data.canUseStandardConnectionEndpoint(clicked))) {
            cleanupCreatedOrdinaryNode(data, clicked, createdOrdinaryNode);
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.anchor_connection_limit").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        if (!data.canAddConnectionWithBranchSupport(first, clicked)) {
            cleanupCreatedOrdinaryNode(data, clicked, createdOrdinaryNode);
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.connection_not_allowed").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        PipeConnection connection = data.addConnectionWithBranchSupport(first, clicked, curveSpec).orElse(null);
        if (connection == null) {
            cleanupCreatedOrdinaryNode(data, clicked, createdOrdinaryNode);
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.connection_not_allowed").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        if (keepBuilding) {
            stack.set(SPSDataComponents.SELECTED_ANCHOR.get(), clicked);
            stack.set(SPSDataComponents.SELECTED_START_TANGENT.get(), connection.tangentAt(connection.length()));
            stack.remove(SPSDataComponents.PENDING_CONTROL_POINTS.get());
        } else {
            clearSelectedAnchor(stack);
        }
        String createdMessage = firstBranch || clickedBranch ? "message.superpipeslide.branch_connection_created" : firstFold || clickedFold ? "message.superpipeslide.fold_connection_created" : "message.superpipeslide.connection_created";
        player.sendOverlayMessage(Component.translatable(createdMessage, shortId(connection), String.format("%.1f", connection.length())).withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void cleanupCreatedOrdinaryNode(PipeNetworkSavedData data, PipeAnchorId created, boolean createdOrdinaryNode) {
        if (createdOrdinaryNode) {
            data.removeOrdinaryNodeAndConnections(created);
        }
    }

    private static String shortId(PipeConnection connection) {
        return connection.id().toString().substring(0, 8);
    }

    private static void clearSelectedAnchor(ItemStack stack) {
        stack.remove(SPSDataComponents.SELECTED_ANCHOR.get());
        stack.remove(SPSDataComponents.SELECTED_START_TANGENT.get());
        stack.remove(SPSDataComponents.PENDING_CONTROL_POINTS.get());
    }

    public static void clearSelectedAnchorFromPlayers(ServerLevel level, PipeAnchorId anchorId) {
        if (!anchorId.levelKey().equals(level.dimension())) {
            return;
        }
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            clearSelectedAnchorFromPlayer(player, anchorId);
        }
    }

    private static void clearSelectedAnchorFromPlayer(ServerPlayer player, PipeAnchorId anchorId) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isConnector(stack) || !anchorId.equals(stack.get(SPSDataComponents.SELECTED_ANCHOR.get()))) {
                continue;
            }
            clearSelectedAnchor(stack);
            inventory.setChanged();
            player.connection.send(inventory.createInventoryUpdatePacket(slot));
        }
    }

    public static CurveSpec curveSpec(ItemStack stack, Player player, PipeAnchorId first, PipeAnchorId second) {
        Vec3 chord = Vec3.atCenterOf(second.blockPos()).subtract(Vec3.atCenterOf(first.blockPos()));
        PipeConnectorMode mode = mode(stack);
        return switch (mode) {
            case LINE -> CurveSpec.line();
            case AUTO_CURVE -> autoCurveSpec(stack, chord, first, second);
            case GAZE_CURVE -> gazeCurveSpec(stack, player, chord);
            case CONTROLLED -> controlledCurveSpec(stack, player, chord, first, second);
        };
    }

    public static PipeConnectorMode mode(ItemStack stack) {
        if (stack.getItem() instanceof PipeConnectorItem connectorItem) {
            return connectorItem.connectorMode;
        }
        return stack.getOrDefault(SPSDataComponents.PIPE_CONNECTOR_MODE.get(), PipeConnectorMode.GAZE_CURVE);
    }

    public static boolean isConnector(ItemStack stack) {
        return stack.getItem() instanceof PipeConnectorItem;
    }

    private static boolean isConnectorAnchor(BlockState state) {
        return state.is(SPSBlocks.PIPE_ANCHOR.get())
                || state.is(SPSBlocks.BRANCH_ANCHOR.get())
                || state.is(SPSBlocks.SPACE_FOLD_ANCHOR.get())
                || state.is(SPSBlocks.DIMENSION_FOLD_ANCHOR.get());
    }

    public static CurveSpec autoCurveSpec(ItemStack stack, PipeAnchorId first, PipeAnchorId second) {
        Vec3 chord = Vec3.atCenterOf(second.blockPos()).subtract(Vec3.atCenterOf(first.blockPos()));
        return autoCurveSpec(stack, chord, first, second);
    }

    private static CurveSpec autoCurveSpec(ItemStack stack, Vec3 chord, PipeAnchorId first, PipeAnchorId second) {
        Vec3 direction = chord.lengthSqr() < 1.0E-6D ? Vec3.ZERO : chord.normalize();
        Vec3 startTangent = stack.getOrDefault(SPSDataComponents.SELECTED_START_TANGENT.get(), direction);
        boolean overshootStart = false;
        if (startTangent.lengthSqr() < 1.0E-6D) {
            startTangent = direction;
        } else if (startTangent.normalize().dot(direction) < 0.0D) {
            startTangent = startTangent.normalize();
            overshootStart = true;
        }
        if (!overshootStart) {
            return CurveSpec.autoCurve(orientAxis(startTangent.normalize(), chord), direction);
        }

        double chordLength = chord.length();
        double startHandle = Math.max(1.25D, chordLength * 1.15D);
        double endHandle = Math.max(0.75D, chordLength * 0.32D);
        Vec3 start = Vec3.atCenterOf(first.blockPos());
        Vec3 end = Vec3.atCenterOf(second.blockPos());
        Vec3 firstControl = start.add(startTangent.scale(startHandle));
        Vec3 secondControl = end.subtract(direction.scale(endHandle));
        return CurveSpec.autoCurve(startTangent, direction, List.of(firstControl, secondControl));
    }

    private static CurveSpec gazeCurveSpec(ItemStack stack, Player player, Vec3 chord) {
        Vec3 startAxis = stack.getOrDefault(SPSDataComponents.SELECTED_START_TANGENT.get(), player.getLookAngle()).normalize();
        Vec3 endAxis = player.getLookAngle().normalize();
        return CurveSpec.gazeCurve(orientAxis(startAxis, chord), orientAxis(endAxis, chord));
    }

    private static CurveSpec controlledCurveSpec(ItemStack stack, Player player, Vec3 chord, PipeAnchorId first, PipeAnchorId second) {
        List<Vec3> controlPoints = controlPoints(stack);
        if (!controlPoints.isEmpty()) {
            return CurveSpec.controlled(controlPoints);
        }

        Vec3 start = Vec3.atCenterOf(first.blockPos());
        Vec3 end = Vec3.atCenterOf(second.blockPos());
        Vec3 startAxis = orientAxis(stack.getOrDefault(SPSDataComponents.SELECTED_START_TANGENT.get(), player.getLookAngle()).normalize(), chord);
        Vec3 endAxis = orientAxis(player.getLookAngle().normalize(), chord);
        double handleLength = Math.max(0.75D, chord.length() * 0.32D);
        return CurveSpec.controlled(List.of(start.add(startAxis.scale(handleLength)), end.subtract(endAxis.scale(handleLength))));
    }

    private static InteractionResult addControlPoint(UseOnContext context, Player player, ItemStack stack) {
        List<Vec3> current = controlPoints(stack);
        if (current.size() >= MAX_CONTROL_POINTS) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.control_point_limit", MAX_CONTROL_POINTS).withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        Vec3 point = context.getClickLocation();
        List<Vec3> updated = new java.util.ArrayList<>(current);
        updated.add(point);
        stack.set(SPSDataComponents.PENDING_CONTROL_POINTS.get(), List.copyOf(updated));
        player.sendOverlayMessage(Component.translatable("message.superpipeslide.control_point_added", updated.size(), MAX_CONTROL_POINTS).withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS_SERVER;
    }

    private static List<Vec3> controlPoints(ItemStack stack) {
        return stack.getOrDefault(SPSDataComponents.PENDING_CONTROL_POINTS.get(), List.of());
    }

    private static Component modeName(PipeConnectorMode mode) {
        return Component.translatable("tooltip.superpipeslide.pipe_connector.mode." + mode.getSerializedName());
    }

    private static Vec3 orientAxis(Vec3 axis, Vec3 chord) {
        if (axis.lengthSqr() < 1.0E-6D || chord.lengthSqr() < 1.0E-6D) {
            return chord;
        }
        Vec3 normalizedChord = chord.normalize();
        return axis.dot(normalizedChord) < 0.0D ? axis.scale(-1.0D) : axis;
    }

}
