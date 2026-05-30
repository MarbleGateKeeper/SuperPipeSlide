package dev.marblegate.superpipeslide.common.core.geometry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record PipeConnectionAttributes(boolean acceleration, boolean highway, boolean hiddenCandidate, int directionLimit, int reservedFlags) {

    private static final int ACCELERATION_FLAG = 1;
    private static final int HIGHWAY_FLAG = 1 << 1;
    private static final int HIDDEN_CANDIDATE_FLAG = 1 << 2;
    private static final int FORWARD_ONLY_FLAG = 1 << 3;
    private static final int REVERSE_ONLY_FLAG = 1 << 4;

    public static final PipeConnectionAttributes EMPTY = new PipeConnectionAttributes(false, false, false, 0, 0);

    public static final Codec<PipeConnectionAttributes> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("acceleration", false).forGetter(PipeConnectionAttributes::acceleration),
            Codec.BOOL.optionalFieldOf("highway", false).forGetter(PipeConnectionAttributes::highway),
            Codec.BOOL.optionalFieldOf("hidden_candidate", false).forGetter(PipeConnectionAttributes::hiddenCandidate),
            Codec.INT.optionalFieldOf("direction_limit", 0).forGetter(PipeConnectionAttributes::directionLimit),
            Codec.INT.optionalFieldOf("reserved_flags", 0).forGetter(PipeConnectionAttributes::reservedFlags)).apply(instance, PipeConnectionAttributes::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PipeConnectionAttributes> STREAM_CODEC = ByteBufCodecs.VAR_INT
            .map(PipeConnectionAttributes::fromFlags, PipeConnectionAttributes::flags)
            .cast();
    public boolean isEmpty() {
        return !this.acceleration && !this.highway && !this.hiddenCandidate && this.directionLimit == 0 && this.reservedFlags == 0;
    }

    public PipeConnectionAttributes normalized() {
        int normalizedDirectionLimit = this.directionLimit > 0 ? 1 : this.directionLimit < 0 ? -1 : 0;
        PipeConnectionAttributes normalized = normalizedDirectionLimit == this.directionLimit ? this : new PipeConnectionAttributes(this.acceleration, this.highway, this.hiddenCandidate, normalizedDirectionLimit, this.reservedFlags);
        return normalized.isEmpty() ? EMPTY : normalized;
    }

    public PipeConnectionAttributes withAcceleration(boolean enabled) {
        return new PipeConnectionAttributes(enabled, this.highway, this.hiddenCandidate, this.directionLimit, this.reservedFlags).normalized();
    }

    public PipeConnectionAttributes withHighway(boolean enabled) {
        return new PipeConnectionAttributes(this.acceleration, enabled, this.hiddenCandidate, this.directionLimit, this.reservedFlags).normalized();
    }

    public PipeConnectionAttributes withHiddenCandidate(boolean enabled) {
        return new PipeConnectionAttributes(this.acceleration, this.highway, enabled, this.directionLimit, this.reservedFlags).normalized();
    }

    public PipeConnectionAttributes withDirectionLimit(int directionLimit) {
        return new PipeConnectionAttributes(this.acceleration, this.highway, this.hiddenCandidate, directionLimit, this.reservedFlags).normalized();
    }

    public PipeConnectionAttributes toggleAcceleration() {
        return this.withAcceleration(!this.acceleration);
    }

    public PipeConnectionAttributes toggleHighway() {
        return this.withHighway(!this.highway);
    }

    public PipeConnectionAttributes cycleDirectionLimit() {
        if (this.directionLimit == 0) {
            return this.withDirectionLimit(1);
        }
        if (this.directionLimit > 0) {
            return this.withDirectionLimit(-1);
        }
        return this.withDirectionLimit(0);
    }

    public boolean allowsDirection(int direction) {
        return this.directionLimit == 0 || Integer.signum(direction) == this.directionLimit;
    }

    public int flags() {
        int flags = this.reservedFlags & ~knownFlags();
        if (this.acceleration) {
            flags |= ACCELERATION_FLAG;
        }
        if (this.highway) {
            flags |= HIGHWAY_FLAG;
        }
        if (this.hiddenCandidate) {
            flags |= HIDDEN_CANDIDATE_FLAG;
        }
        if (this.directionLimit > 0) {
            flags |= FORWARD_ONLY_FLAG;
        } else if (this.directionLimit < 0) {
            flags |= REVERSE_ONLY_FLAG;
        }
        return flags;
    }

    public static PipeConnectionAttributes fromFlags(int flags) {
        int directionLimit = 0;
        if ((flags & FORWARD_ONLY_FLAG) != 0) {
            directionLimit = 1;
        } else if ((flags & REVERSE_ONLY_FLAG) != 0) {
            directionLimit = -1;
        }
        return new PipeConnectionAttributes(
                (flags & ACCELERATION_FLAG) != 0,
                (flags & HIGHWAY_FLAG) != 0,
                (flags & HIDDEN_CANDIDATE_FLAG) != 0,
                directionLimit,
                flags & ~knownFlags()).normalized();
    }

    private static int knownFlags() {
        return ACCELERATION_FLAG | HIGHWAY_FLAG | HIDDEN_CANDIDATE_FLAG | FORWARD_ONLY_FLAG | REVERSE_ONLY_FLAG;
    }
}
