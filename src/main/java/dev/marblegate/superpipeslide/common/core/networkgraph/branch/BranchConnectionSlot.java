package dev.marblegate.superpipeslide.common.core.networkgraph.branch;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

public record BranchConnectionSlot(UUID id, UUID connectionId, String displayName, Vec3 localDirection, Vec3 choicePosition, Optional<UUID> routeBindingId) {

    public static final int MAX_DISPLAY_NAME_LENGTH = 64;

    public static final Codec<BranchConnectionSlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(BranchConnectionSlot::id),
            UUIDUtil.STRING_CODEC.fieldOf("connection_id").forGetter(BranchConnectionSlot::connectionId),
            Codec.STRING.optionalFieldOf("display_name", "").forGetter(BranchConnectionSlot::displayName),
            Vec3.CODEC.fieldOf("local_direction").forGetter(BranchConnectionSlot::localDirection),
            Vec3.CODEC.fieldOf("choice_position").forGetter(BranchConnectionSlot::choicePosition),
            UUIDUtil.STRING_CODEC.optionalFieldOf("route_binding_id").forGetter(BranchConnectionSlot::routeBindingId)).apply(instance, BranchConnectionSlot::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BranchConnectionSlot> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            BranchConnectionSlot::id,
            UUIDUtil.STREAM_CODEC,
            BranchConnectionSlot::connectionId,
            ByteBufCodecs.stringUtf8(MAX_DISPLAY_NAME_LENGTH).cast(),
            BranchConnectionSlot::displayName,
            Vec3.STREAM_CODEC.cast(),
            BranchConnectionSlot::localDirection,
            Vec3.STREAM_CODEC.cast(),
            BranchConnectionSlot::choicePosition,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).cast(),
            BranchConnectionSlot::routeBindingId,
            BranchConnectionSlot::new);
    public BranchConnectionSlot {
        if (displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("Branch connection display name is too long");
        }
        validateFinite(localDirection, "localDirection");
        validateFinite(choicePosition, "choicePosition");
    }

    private static void validateFinite(Vec3 vector, String name) {
        if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)) {
            throw new IllegalArgumentException("Branch connection " + name + " must be finite");
        }
    }
}
