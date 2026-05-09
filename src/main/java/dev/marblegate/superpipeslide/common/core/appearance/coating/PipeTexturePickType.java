package dev.marblegate.superpipeslide.common.core.appearance.coating;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public enum PipeTexturePickType {
    AUTO("auto"),
    FACE("face"),
    SPRITE("sprite");

    public static final Codec<PipeTexturePickType> CODEC = Codec.STRING.xmap(PipeTexturePickType::byId, PipeTexturePickType::id);
    public static final StreamCodec<io.netty.buffer.ByteBuf, PipeTexturePickType> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(PipeTexturePickType::byId, PipeTexturePickType::id);

    private final String id;

    PipeTexturePickType(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public static PipeTexturePickType byId(String id) {
        for (PipeTexturePickType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return AUTO;
    }
}
