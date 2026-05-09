package dev.marblegate.superpipeslide.network.pipe.appearance;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.appearance.model.PipeAppearanceAssignment;
import dev.marblegate.superpipeslide.common.core.appearance.model.PipeAppearanceProfile;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record ClientboundPipeAppearanceSyncPayload(
        long revision,
        List<PipeAppearanceProfile> profiles,
        List<PipeAppearanceAssignment> assignments,
        List<Integer> clearedConnectionKeys,
        boolean full
) implements CustomPacketPayload {
    private static final int MAX_PROFILES = 4096;
    private static final int MAX_ASSIGNMENTS = 16384;

    public static final Type<ClientboundPipeAppearanceSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "pipe_appearance_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundPipeAppearanceSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG.cast(),
            ClientboundPipeAppearanceSyncPayload::revision,
            PipeAppearanceProfile.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_PROFILES)).cast(),
            ClientboundPipeAppearanceSyncPayload::profiles,
            PipeAppearanceAssignment.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ASSIGNMENTS)).cast(),
            ClientboundPipeAppearanceSyncPayload::assignments,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_ASSIGNMENTS)).cast(),
            ClientboundPipeAppearanceSyncPayload::clearedConnectionKeys,
            ByteBufCodecs.BOOL,
            ClientboundPipeAppearanceSyncPayload::full,
            ClientboundPipeAppearanceSyncPayload::new
    );

    public ClientboundPipeAppearanceSyncPayload {
        profiles = List.copyOf(profiles);
        assignments = List.copyOf(assignments);
        clearedConnectionKeys = List.copyOf(clearedConnectionKeys);
    }

    public boolean isEmpty() {
        return this.profiles.isEmpty() && this.assignments.isEmpty() && this.clearedConnectionKeys.isEmpty();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
