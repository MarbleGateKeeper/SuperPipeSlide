package dev.marblegate.superpipeslide.common.core.appearance.coating;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public record PipeCoatingTexturePick(
        PipeTexturePickType type,
        Optional<Direction> face,
        Optional<Identifier> fallbackSprite
) {
    public static final PipeCoatingTexturePick AUTO = new PipeCoatingTexturePick(PipeTexturePickType.AUTO, Optional.empty(), Optional.empty());

    public static final Codec<PipeCoatingTexturePick> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PipeTexturePickType.CODEC.optionalFieldOf("type", PipeTexturePickType.AUTO).forGetter(PipeCoatingTexturePick::type),
            Direction.CODEC.optionalFieldOf("face").forGetter(PipeCoatingTexturePick::face),
            Identifier.CODEC.optionalFieldOf("sprite").forGetter(PipeCoatingTexturePick::fallbackSprite)
    ).apply(instance, PipeCoatingTexturePick::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PipeCoatingTexturePick> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PipeCoatingTexturePick decode(RegistryFriendlyByteBuf buffer) {
            PipeTexturePickType type = PipeTexturePickType.STREAM_CODEC.decode(buffer);
            Optional<Direction> face = buffer.readBoolean() ? Optional.of(Direction.STREAM_CODEC.decode(buffer)) : Optional.empty();
            Optional<Identifier> sprite = buffer.readBoolean() ? Optional.of(Identifier.STREAM_CODEC.decode(buffer)) : Optional.empty();
            return new PipeCoatingTexturePick(type, face, sprite);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, PipeCoatingTexturePick pick) {
            PipeCoatingTexturePick normalized = pick == null ? AUTO : pick;
            PipeTexturePickType.STREAM_CODEC.encode(buffer, normalized.type());
            buffer.writeBoolean(normalized.face().isPresent());
            normalized.face().ifPresent(face -> Direction.STREAM_CODEC.encode(buffer, face));
            buffer.writeBoolean(normalized.fallbackSprite().isPresent());
            normalized.fallbackSprite().ifPresent(sprite -> Identifier.STREAM_CODEC.encode(buffer, sprite));
        }
    };

    public PipeCoatingTexturePick {
        type = type == null ? PipeTexturePickType.AUTO : type;
        face = face == null ? Optional.empty() : face;
        fallbackSprite = fallbackSprite == null ? Optional.empty() : fallbackSprite;
        if (type == PipeTexturePickType.FACE && face.isEmpty()) {
            type = PipeTexturePickType.AUTO;
        }
        if (type == PipeTexturePickType.SPRITE && fallbackSprite.isEmpty()) {
            type = PipeTexturePickType.AUTO;
        }
        if (type == PipeTexturePickType.AUTO) {
            face = Optional.empty();
            fallbackSprite = Optional.empty();
        }
    }

    public static PipeCoatingTexturePick face(Direction face) {
        return face == null ? AUTO : new PipeCoatingTexturePick(PipeTexturePickType.FACE, Optional.of(face), Optional.empty());
    }

    public static PipeCoatingTexturePick sprite(Identifier sprite) {
        return sprite == null ? AUTO : new PipeCoatingTexturePick(PipeTexturePickType.SPRITE, Optional.empty(), Optional.of(sprite));
    }

    public String contentKey() {
        return switch (this.type) {
            case FACE -> "face:" + this.face.map(Direction::getSerializedName).orElse("auto");
            case SPRITE -> "sprite:" + this.fallbackSprite.map(Identifier::toString).orElse("auto");
            case AUTO -> "auto";
        };
    }
}
