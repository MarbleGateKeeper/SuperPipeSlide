package dev.marblegate.superpipeslide.common.item.pipe;

import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionAttributes;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRaycast;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;
import dev.marblegate.superpipeslide.common.core.networkgraph.storage.PipeNetworkSavedData;
import dev.marblegate.superpipeslide.common.core.route.storage.RouteNetworkSavedData;
import dev.marblegate.superpipeslide.common.core.route.model.section.RouteSectionStatus;
import dev.marblegate.superpipeslide.common.event.ServerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.Consumer;

public class PipeAttributeToolItem extends Item {
    private static final double ATTRIBUTE_TOOL_REACH = 8.0D;
    private static final double ATTRIBUTE_TOOL_PIPE_PICK_RADIUS = 0.55D;
    private final AttributeKind attributeKind;
    private final boolean consumable;
    private final boolean allowDisable;

    public PipeAttributeToolItem(Properties properties, AttributeKind attributeKind) {
        this(properties, attributeKind, false, true);
    }

    public PipeAttributeToolItem(Properties properties, AttributeKind attributeKind, boolean consumable, boolean allowDisable) {
        super(properties);
        this.attributeKind = attributeKind;
        this.consumable = consumable;
        this.allowDisable = allowDisable;
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        builder.accept(Component.translatable("tooltip.superpipeslide.pipe_attribute_tool.attribute", this.attributeName()).withStyle(ChatFormatting.AQUA));
        builder.accept(Component.translatable("tooltip.superpipeslide.pipe_attribute_tool.use").withStyle(ChatFormatting.DARK_GRAY));
        if (this.consumable) {
            builder.accept(Component.translatable("tooltip.superpipeslide.consumed_on_use").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        PipeNetworkSavedData data = PipeNetworkSavedData.get(serverLevel);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Optional<PipeConnectionRaycast.Hit> target = PipeConnectionRaycast.find(
                data.connectionsNear(serverLevel, eye, ATTRIBUTE_TOOL_REACH + 2.0D),
                eye,
                look,
                ATTRIBUTE_TOOL_REACH,
                ATTRIBUTE_TOOL_PIPE_PICK_RADIUS
        );
        if (target.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.no_pipe_connection_targeted").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        PipeConnection connection = target.get().connection();
        PipeConnectionAttributes current = connection.resolvedAttributes();
        boolean alreadyEnabled = switch (this.attributeKind) {
            case ACCELERATION -> current.acceleration();
            case HIGHWAY -> current.highway();
            case DIRECTION_LIMIT -> current.directionLimit() != 0;
        };
        if (alreadyEnabled && !this.allowDisable) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.pipe_attribute_already_enabled", this.attributeName()).withStyle(ChatFormatting.YELLOW));
            return InteractionResult.FAIL;
        }

        PipeConnectionAttributes updatedAttributes = switch (this.attributeKind) {
            case ACCELERATION -> current.toggleAcceleration();
            case HIGHWAY -> current.toggleHighway();
            case DIRECTION_LIMIT -> current.cycleDirectionLimit();
        };
        Optional<PipeConnectionAttributes> storedAttributes = updatedAttributes.isEmpty() ? Optional.empty() : Optional.of(updatedAttributes);
        PipeConnection updated = data.updateConnectionAttributes(connection.id(), storedAttributes).orElse(null);
        if (updated == null) {
            player.sendOverlayMessage(Component.translatable("message.superpipeslide.pipe_connection_missing").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (this.attributeKind == AttributeKind.DIRECTION_LIMIT) {
            RouteNetworkSavedData routes = RouteNetworkSavedData.get(serverLevel.getServer());
            boolean routeChanged = false;
            RouteNetworkSavedData.Batch batch = routes.beginMutationBatch();
            try {
                routes.markSectionsReferencing(PipeConnectionRef.of(updated), RouteSectionStatus.STALE);
                batch.include(routes.recomputeDirtySections(data, 16));
            } finally {
                batch.close();
                routeChanged = batch.changed();
            }
            if (routeChanged) {
                ServerEvents.queueRouteDataDelta(serverLevel);
            }
        }

        boolean enabled = switch (this.attributeKind) {
            case ACCELERATION -> updated.resolvedAttributes().acceleration();
            case HIGHWAY -> updated.resolvedAttributes().highway();
            case DIRECTION_LIMIT -> updated.resolvedAttributes().directionLimit() != 0;
        };
        String key = this.attributeKind == AttributeKind.DIRECTION_LIMIT
                ? switch (updated.resolvedAttributes().directionLimit()) {
                    case 1 -> "message.superpipeslide.pipe_direction_forward";
                    case -1 -> "message.superpipeslide.pipe_direction_reverse";
                    default -> "message.superpipeslide.pipe_direction_bidirectional";
                }
                : enabled ? "message.superpipeslide.pipe_attribute_enabled" : "message.superpipeslide.pipe_attribute_disabled";
        if (enabled) {
            consume(player.getItemInHand(hand), player);
        }
        player.sendOverlayMessage(Component.translatable(key, this.attributeName()).withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        return InteractionResult.SUCCESS_SERVER;
    }

    private void consume(ItemStack stack, Player player) {
        if (this.consumable && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    private Component attributeName() {
        return Component.translatable("pipe_attribute.superpipeslide." + this.attributeKind.serializedName);
    }

    public enum AttributeKind {
        ACCELERATION("acceleration"),
        HIGHWAY("highway"),
        DIRECTION_LIMIT("direction_limit");

        private final String serializedName;

        AttributeKind(String serializedName) {
            this.serializedName = serializedName;
        }
    }
}

