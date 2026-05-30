package dev.marblegate.superpipeslide.network.slide;

import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import java.util.Arrays;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundSlideNoticePayload(
        Kind kind,
        List<Integer> accentColors,
        Component title,
        List<NoticeLine> lines) implements CustomPacketPayload {

    private static final int MAX_COLORS = 3;
    private static final int MAX_LINES = 32;

    public static final Type<ClientboundSlideNoticePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "slide_notice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Kind> KIND_STREAM_CODEC = ByteBufCodecs.STRING_UTF8
            .map(Kind::bySerializedName, Kind::serializedName)
            .cast();
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSlideNoticePayload> STREAM_CODEC = StreamCodec.composite(
            KIND_STREAM_CODEC,
            ClientboundSlideNoticePayload::kind,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_COLORS)),
            ClientboundSlideNoticePayload::accentColors,
            ComponentSerialization.STREAM_CODEC,
            ClientboundSlideNoticePayload::title,
            NoticeLine.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)),
            ClientboundSlideNoticePayload::lines,
            ClientboundSlideNoticePayload::new);
    public ClientboundSlideNoticePayload {
        kind = kind == null ? Kind.STANDARD : kind;
        accentColors = List.copyOf((accentColors == null ? List.<Integer>of() : accentColors).stream().limit(MAX_COLORS).toList());
        lines = List.copyOf((lines == null ? List.<NoticeLine>of() : lines).stream().limit(MAX_LINES).toList());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Kind {
        STANDARD("standard"),
        ENTER_ROUTE("enter_route"),
        CHOICE("choice"),
        PASS_STATION("pass_station"),
        ARRIVAL("arrival"),
        TERMINAL("terminal"),
        WARNING("warning"),
        BLOCKED("blocked");

        private final String serializedName;

        Kind(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return this.serializedName;
        }

        public static Kind bySerializedName(String serializedName) {
            return Arrays.stream(values())
                    .filter(kind -> kind.serializedName.equals(serializedName))
                    .findFirst()
                    .orElse(STANDARD);
        }
    }

    public record NoticeLine(Component text, List<Integer> colors, boolean chip) {

        public static final StreamCodec<RegistryFriendlyByteBuf, NoticeLine> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.STREAM_CODEC,
                NoticeLine::text,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_COLORS)),
                NoticeLine::colors,
                ByteBufCodecs.BOOL,
                NoticeLine::chip,
                NoticeLine::new);
        public NoticeLine {
            colors = List.copyOf((colors == null ? List.<Integer>of() : colors).stream().limit(MAX_COLORS).toList());
        }
    }
}
