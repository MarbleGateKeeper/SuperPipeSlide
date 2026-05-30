package dev.marblegate.superpipeslide.common.core.projection.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;

public sealed interface ProjectionComponentSettings permits
        ProjectionComponentSettings.Panel,
        ProjectionComponentSettings.StationTitleGroup,
        ProjectionComponentSettings.Text,
        ProjectionComponentSettings.ExitBadge,
        ProjectionComponentSettings.Divider,
        ProjectionComponentSettings.RouteList,
        ProjectionComponentSettings.RouteIcon,
        ProjectionComponentSettings.RouteCapsules,
        ProjectionComponentSettings.RouteBackplate,
        ProjectionComponentSettings.RouteText,
        ProjectionComponentSettings.PlatformTitleGroup,
        ProjectionComponentSettings.PlatformBadge,
        ProjectionComponentSettings.PlatformDirection,
        ProjectionComponentSettings.PlatformStatusTags,
        ProjectionComponentSettings.PlatformLine,
        ProjectionComponentSettings.PlatformLineIcon,
        ProjectionComponentSettings.PlatformTransferList,
        ProjectionComponentSettings.PlatformTransferMatrix,
        ProjectionComponentSettings.PlatformLayoutMap,
        ProjectionComponentSettings.BuiltinIcon,
        ProjectionComponentSettings.NetworkImage {
    int MAX_TEXT_LENGTH = 128;
    int MAX_BINDING_LENGTH = 64;
    int MAX_URL_LENGTH = 2048;
    int MAX_ICON_ID_LENGTH = 64;

    Codec<ProjectionComponentSettings> CODEC = ProjectionComponentType.CODEC.dispatch("kind", ProjectionComponentSettings::type, ProjectionComponentType::settingsCodec);

    ProjectionComponentType type();

    void encode(RegistryFriendlyByteBuf buffer);

    static ProjectionComponentSettings defaultFor(ProjectionComponentType type) {
        return switch (type) {
            case BACKGROUND_PANEL -> Panel.defaults();
            case STATION_TITLE_GROUP -> StationTitleGroup.defaults();
            case STATION_NAME_TEXT -> Text.stationName();
            case TRANSLATION_TEXT -> Text.translationName();
            case CUSTOM_TEXT -> Text.customText();
            case EXIT_BADGE -> ExitBadge.defaults();
            case DIVIDER -> Divider.defaults();
            case ROUTE_LIST -> RouteList.defaults();
            case ROUTE_TEXT -> RouteText.defaults();
            case ROUTE_ICONS -> RouteIcon.solidDefaults();
            case ROUTE_OUTLINE_ICONS -> RouteIcon.outlineDefaults();
            case ROUTE_CAPSULES -> RouteCapsules.defaults();
            case ROUTE_BACKPLATE -> RouteBackplate.defaults();
            case PLATFORM_TITLE_GROUP -> PlatformTitleGroup.defaults();
            case PLATFORM_BADGE -> PlatformBadge.defaults();
            case PLATFORM_DIRECTION_TITLE -> PlatformDirection.defaults();
            case PLATFORM_STATUS_TAGS -> PlatformStatusTags.defaults();
            case PLATFORM_LINE_CURRENT -> PlatformLine.currentDefaults();
            case PLATFORM_LINE_BAND -> PlatformLine.bandDefaults();
            case PLATFORM_LINE_ICON -> PlatformLineIcon.defaults();
            case PLATFORM_TERMINAL_STRIP -> PlatformLine.terminalDefaults();
            case PLATFORM_TRANSFER_LIST -> PlatformTransferList.defaults();
            case PLATFORM_TRANSFER_MATRIX -> PlatformTransferMatrix.defaults();
            case PLATFORM_LAYOUT_STOP_LIST -> PlatformLayoutMap.stopListDefaults();
            case PLATFORM_LAYOUT_PHYSICAL_MAP -> PlatformLayoutMap.physicalMapDefaults();
            case PLATFORM_LAYOUT_PRACTICAL_MAP -> PlatformLayoutMap.practicalMapDefaults();
            case PLATFORM_LAYOUT_SCHEMATIC_MAP -> PlatformLayoutMap.schematicMapDefaults();
            case PLATFORM_LAYOUT_EDITOR_MAP -> PlatformLayoutMap.editorMapDefaults();
            case BUILTIN_ICON -> BuiltinIcon.defaults();
            case NETWORK_IMAGE -> NetworkImage.defaults();
        };
    }

    static String trim(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    static float clamp(float value, float min, float max, float fallback) {
        return Math.max(min, Math.min(max, finite(value, fallback)));
    }

    static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static <E extends Enum<E>> E enumByName(Class<E> type, String name, E fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    enum FlowDirection {
        HORIZONTAL,
        VERTICAL;

        public static final Codec<FlowDirection> CODEC = Codec.STRING.xmap(name -> enumByName(FlowDirection.class, name, HORIZONTAL), FlowDirection::name);
    }

    enum RouteOverflowMode {
        CLIP,
        PLUS_COUNT,
        ROTATE;

        public static final Codec<RouteOverflowMode> CODEC = Codec.STRING.xmap(name -> enumByName(RouteOverflowMode.class, name, PLUS_COUNT), RouteOverflowMode::name);
    }

    enum StripeDirection {
        HORIZONTAL,
        VERTICAL;

        public static final Codec<StripeDirection> CODEC = Codec.STRING.xmap(name -> enumByName(StripeDirection.class, name, HORIZONTAL), StripeDirection::name);
    }

    enum ColorPolicy {
        ROUTE_ORDER,
        FIRST_ROUTE;

        public static final Codec<ColorPolicy> CODEC = Codec.STRING.xmap(name -> enumByName(ColorPolicy.class, name, ROUTE_ORDER), ColorPolicy::name);
    }

    enum IconShape {
        CIRCLE,
        SQUARE;

        public static final Codec<IconShape> CODEC = Codec.STRING.xmap(name -> enumByName(IconShape.class, name, CIRCLE), IconShape::name);
    }

    enum CapsuleContentOrientation {
        HORIZONTAL,
        ROTATE_CW,
        ROTATE_CCW;

        public static final Codec<CapsuleContentOrientation> CODEC = Codec.STRING.xmap(name -> enumByName(CapsuleContentOrientation.class, name, HORIZONTAL), CapsuleContentOrientation::name);
    }

    enum TextOrientation {
        HORIZONTAL,
        ROTATE_CW,
        ROTATE_CCW,
        VERTICAL_STACK;

        public static final Codec<TextOrientation> CODEC = Codec.STRING.xmap(name -> enumByName(TextOrientation.class, name, HORIZONTAL), TextOrientation::name);
    }

    enum MissingTranslationMode {
        KEEP_PRIMARY_SLOT,
        CENTER_PRIMARY,
        EXPAND_PRIMARY;

        public static final Codec<MissingTranslationMode> CODEC = Codec.STRING.xmap(name -> enumByName(MissingTranslationMode.class, name, CENTER_PRIMARY), MissingTranslationMode::name);
    }

    enum PlatformTitleContent {
        STATION_AND_PLATFORM,
        PLATFORM_AND_STATION,
        STATION_ONLY,
        PLATFORM_ONLY;

        public static final Codec<PlatformTitleContent> CODEC = Codec.STRING.xmap(name -> enumByName(PlatformTitleContent.class, name, STATION_AND_PLATFORM), PlatformTitleContent::name);
    }

    enum PlatformBadgeStyle {
        SOLID,
        OUTLINE,
        CAPSULE,
        TEXT_ONLY;

        public static final Codec<PlatformBadgeStyle> CODEC = Codec.STRING.xmap(name -> enumByName(PlatformBadgeStyle.class, name, SOLID), PlatformBadgeStyle::name);
    }

    enum PlatformDirectionSource {
        TERMINAL,
        NEXT_STOP,
        PREVIOUS_STOP,
        ORIGIN,
        LAYOUT_NAME;

        public static final Codec<PlatformDirectionSource> CODEC = Codec.STRING.xmap(name -> enumByName(PlatformDirectionSource.class, name, TERMINAL), PlatformDirectionSource::name);
    }

    enum ArrowDirection {
        NONE,
        AUTO,
        LEFT,
        RIGHT,
        BOTH;

        public static final Codec<ArrowDirection> CODEC = Codec.STRING.xmap(name -> enumByName(ArrowDirection.class, name, AUTO), ArrowDirection::name);
    }

    enum ArrowPlacement {
        BEFORE,
        AFTER;

        public static final Codec<ArrowPlacement> CODEC = Codec.STRING.xmap(name -> enumByName(ArrowPlacement.class, name, BEFORE), ArrowPlacement::name);
    }

    enum PlatformDirectionPrefix {
        NONE,
        TOWARDS,
        NEXT_STOP,
        PREVIOUS_STOP,
        TERMINAL,
        ORIGIN;

        public static final Codec<PlatformDirectionPrefix> CODEC = Codec.STRING.xmap(name -> enumByName(PlatformDirectionPrefix.class, name, TOWARDS), PlatformDirectionPrefix::name);
    }

    enum PlatformNodeStyle {
        NONE,
        SOLID,
        OUTLINE;

        public static final Codec<PlatformNodeStyle> CODEC = Codec.STRING.xmap(name -> enumByName(PlatformNodeStyle.class, name, SOLID), PlatformNodeStyle::name);
    }

    enum PlatformLineStyle {
        CURRENT_NODE,
        BAND,
        TERMINAL_STRIP;

        public static final Codec<PlatformLineStyle> CODEC = Codec.STRING.xmap(name -> enumByName(PlatformLineStyle.class, name, CURRENT_NODE), PlatformLineStyle::name);
    }

    enum PlatformLayoutMapStyle {
        STOP_LIST,
        PHYSICAL,
        PRACTICAL,
        SCHEMATIC,
        EDITOR;

        public static final Codec<PlatformLayoutMapStyle> CODEC = Codec.STRING.xmap(name -> enumByName(PlatformLayoutMapStyle.class, name, SCHEMATIC), PlatformLayoutMapStyle::name);
    }

    enum PlatformStatusScope {
        PLATFORM_SERVICE,
        ACTIVE_LAYOUT;

        public static final Codec<PlatformStatusScope> CODEC = Codec.STRING.xmap(name -> enumByName(PlatformStatusScope.class, name, PLATFORM_SERVICE), PlatformStatusScope::name);
    }

    enum ImageFitMode {
        CONTAIN,
        COVER,
        STRETCH,
        CENTER,
        TILE;

        public static final Codec<ImageFitMode> CODEC = Codec.STRING.xmap(name -> enumByName(ImageFitMode.class, name, CONTAIN), ImageFitMode::name);
    }

    enum ImageAnchor {
        TOP_LEFT(0.0F, 0.0F),
        TOP(0.5F, 0.0F),
        TOP_RIGHT(1.0F, 0.0F),
        LEFT(0.0F, 0.5F),
        CENTER(0.5F, 0.5F),
        RIGHT(1.0F, 0.5F),
        BOTTOM_LEFT(0.0F, 1.0F),
        BOTTOM(0.5F, 1.0F),
        BOTTOM_RIGHT(1.0F, 1.0F);

        public static final Codec<ImageAnchor> CODEC = Codec.STRING.xmap(name -> enumByName(ImageAnchor.class, name, CENTER), ImageAnchor::name);
        private final float xFactor;
        private final float yFactor;

        ImageAnchor(float xFactor, float yFactor) {
            this.xFactor = xFactor;
            this.yFactor = yFactor;
        }

        public float xFactor() {
            return this.xFactor;
        }

        public float yFactor() {
            return this.yFactor;
        }
    }

    enum IconTintMode {
        ORIGINAL,
        TINT,
        MULTIPLY,
        DUOTONE;

        public static final Codec<IconTintMode> CODEC = Codec.STRING.xmap(name -> enumByName(IconTintMode.class, name, ORIGINAL), IconTintMode::name);
    }

    enum ImageFallbackMode {
        PLACEHOLDER,
        COMPACT,
        HIDDEN;

        public static final Codec<ImageFallbackMode> CODEC = Codec.STRING.xmap(name -> enumByName(ImageFallbackMode.class, name, PLACEHOLDER), ImageFallbackMode::name);
    }

    enum ImageLoadingMode {
        SUBTLE,
        PLACEHOLDER,
        HIDDEN;

        public static final Codec<ImageLoadingMode> CODEC = Codec.STRING.xmap(name -> enumByName(ImageLoadingMode.class, name, SUBTLE), ImageLoadingMode::name);
    }

    record Panel(int fillColor, int borderColor, float borderWidth, float opacity) implements ProjectionComponentSettings {

        public static final Codec<Panel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("fill", 0xFF182126).forGetter(Panel::fillColor),
                Codec.INT.optionalFieldOf("border", 0xFF4B5C62).forGetter(Panel::borderColor),
                Codec.FLOAT.optionalFieldOf("border_width", 0.018F).forGetter(Panel::borderWidth),
                Codec.FLOAT.optionalFieldOf("opacity", 1.0F).forGetter(Panel::opacity)).apply(instance, Panel::new));
        public Panel {
            borderWidth = clamp(borderWidth, 0.0F, 0.50F, 0.018F);
            opacity = clamp(opacity, 0.0F, 1.0F, 1.0F);
        }

        public static Panel defaults() {
            return new Panel(0xFF182126, 0xFF4B5C62, 0.018F, 1.0F);
        }

        @Override
        public ProjectionComponentType type() {
            return ProjectionComponentType.BACKGROUND_PANEL;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeInt(this.fillColor);
            buffer.writeInt(this.borderColor);
            buffer.writeFloat(this.borderWidth);
            buffer.writeFloat(this.opacity);
        }

        public static Panel decode(RegistryFriendlyByteBuf buffer) {
            return new Panel(buffer.readInt(), buffer.readInt(), buffer.readFloat(), buffer.readFloat());
        }
    }

    record StationTitleGroup(
            int primaryColor,
            int translationColor,
            float primaryFontSize,
            float translationFontSize,
            float gap,
            ProjectionTextAlign align,
            ProjectionOverflowMode primaryOverflow,
            ProjectionOverflowMode translationOverflow,
            TextOrientation orientation,
            MissingTranslationMode missingTranslationMode,
            float missingPrimaryScale) implements ProjectionComponentSettings {

        public static final Codec<StationTitleGroup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("primary_color", 0xFFFFFFFF).forGetter(StationTitleGroup::primaryColor),
                Codec.INT.optionalFieldOf("translation_color", 0xFFBFD4DC).forGetter(StationTitleGroup::translationColor),
                Codec.FLOAT.optionalFieldOf("primary_font_size", 0.23F).forGetter(StationTitleGroup::primaryFontSize),
                Codec.FLOAT.optionalFieldOf("translation_font_size", 0.085F).forGetter(StationTitleGroup::translationFontSize),
                Codec.FLOAT.optionalFieldOf("gap", 0.035F).forGetter(StationTitleGroup::gap),
                ProjectionTextAlign.CODEC.optionalFieldOf("align", ProjectionTextAlign.CENTER).forGetter(StationTitleGroup::align),
                ProjectionOverflowMode.CODEC.optionalFieldOf("primary_overflow", ProjectionOverflowMode.SCALE).forGetter(StationTitleGroup::primaryOverflow),
                ProjectionOverflowMode.CODEC.optionalFieldOf("translation_overflow", ProjectionOverflowMode.MARQUEE).forGetter(StationTitleGroup::translationOverflow),
                TextOrientation.CODEC.optionalFieldOf("orientation", TextOrientation.HORIZONTAL).forGetter(StationTitleGroup::orientation),
                MissingTranslationMode.CODEC.optionalFieldOf("missing_translation", MissingTranslationMode.EXPAND_PRIMARY).forGetter(StationTitleGroup::missingTranslationMode),
                Codec.FLOAT.optionalFieldOf("missing_primary_scale", 1.26F).forGetter(StationTitleGroup::missingPrimaryScale)).apply(instance, StationTitleGroup::new));
        public StationTitleGroup {
            primaryFontSize = clamp(primaryFontSize, 0.01F, 2.0F, 0.23F);
            translationFontSize = clamp(translationFontSize, 0.01F, 2.0F, 0.085F);
            gap = clamp(gap, 0.0F, 0.50F, 0.035F);
            align = align == null ? ProjectionTextAlign.CENTER : align;
            primaryOverflow = primaryOverflow == null ? ProjectionOverflowMode.SCALE : primaryOverflow;
            translationOverflow = translationOverflow == null ? ProjectionOverflowMode.MARQUEE : translationOverflow;
            orientation = orientation == null ? TextOrientation.HORIZONTAL : orientation;
            missingTranslationMode = missingTranslationMode == null ? MissingTranslationMode.EXPAND_PRIMARY : missingTranslationMode;
            missingPrimaryScale = clamp(missingPrimaryScale, 1.0F, 2.5F, 1.26F);
        }

        public static StationTitleGroup defaults() {
            return new StationTitleGroup(0xFFFFFFFF, 0xFFBFD4DC, 0.23F, 0.085F, 0.035F, ProjectionTextAlign.CENTER, ProjectionOverflowMode.SCALE, ProjectionOverflowMode.MARQUEE, TextOrientation.HORIZONTAL, MissingTranslationMode.EXPAND_PRIMARY, 1.26F);
        }

        public StationTitleGroup withPrimaryColor(int primaryColor) {
            return new StationTitleGroup(primaryColor, this.translationColor, this.primaryFontSize, this.translationFontSize, this.gap, this.align, this.primaryOverflow, this.translationOverflow, this.orientation, this.missingTranslationMode, this.missingPrimaryScale);
        }

        public StationTitleGroup withTranslationColor(int translationColor) {
            return new StationTitleGroup(this.primaryColor, translationColor, this.primaryFontSize, this.translationFontSize, this.gap, this.align, this.primaryOverflow, this.translationOverflow, this.orientation, this.missingTranslationMode, this.missingPrimaryScale);
        }

        public StationTitleGroup withPrimaryFontSize(float primaryFontSize) {
            return new StationTitleGroup(this.primaryColor, this.translationColor, primaryFontSize, this.translationFontSize, this.gap, this.align, this.primaryOverflow, this.translationOverflow, this.orientation, this.missingTranslationMode, this.missingPrimaryScale);
        }

        public StationTitleGroup withTranslationFontSize(float translationFontSize) {
            return new StationTitleGroup(this.primaryColor, this.translationColor, this.primaryFontSize, translationFontSize, this.gap, this.align, this.primaryOverflow, this.translationOverflow, this.orientation, this.missingTranslationMode, this.missingPrimaryScale);
        }

        public StationTitleGroup withGap(float gap) {
            return new StationTitleGroup(this.primaryColor, this.translationColor, this.primaryFontSize, this.translationFontSize, gap, this.align, this.primaryOverflow, this.translationOverflow, this.orientation, this.missingTranslationMode, this.missingPrimaryScale);
        }

        public StationTitleGroup withAlign(ProjectionTextAlign align) {
            return new StationTitleGroup(this.primaryColor, this.translationColor, this.primaryFontSize, this.translationFontSize, this.gap, align, this.primaryOverflow, this.translationOverflow, this.orientation, this.missingTranslationMode, this.missingPrimaryScale);
        }

        public StationTitleGroup withPrimaryOverflow(ProjectionOverflowMode primaryOverflow) {
            return new StationTitleGroup(this.primaryColor, this.translationColor, this.primaryFontSize, this.translationFontSize, this.gap, this.align, primaryOverflow, this.translationOverflow, this.orientation, this.missingTranslationMode, this.missingPrimaryScale);
        }

        public StationTitleGroup withTranslationOverflow(ProjectionOverflowMode translationOverflow) {
            return new StationTitleGroup(this.primaryColor, this.translationColor, this.primaryFontSize, this.translationFontSize, this.gap, this.align, this.primaryOverflow, translationOverflow, this.orientation, this.missingTranslationMode, this.missingPrimaryScale);
        }

        public StationTitleGroup withOrientation(TextOrientation orientation) {
            return new StationTitleGroup(this.primaryColor, this.translationColor, this.primaryFontSize, this.translationFontSize, this.gap, this.align, this.primaryOverflow, this.translationOverflow, orientation, this.missingTranslationMode, this.missingPrimaryScale);
        }

        public StationTitleGroup withMissingTranslationMode(MissingTranslationMode missingTranslationMode) {
            return new StationTitleGroup(this.primaryColor, this.translationColor, this.primaryFontSize, this.translationFontSize, this.gap, this.align, this.primaryOverflow, this.translationOverflow, this.orientation, missingTranslationMode, this.missingPrimaryScale);
        }

        public StationTitleGroup withMissingPrimaryScale(float missingPrimaryScale) {
            return new StationTitleGroup(this.primaryColor, this.translationColor, this.primaryFontSize, this.translationFontSize, this.gap, this.align, this.primaryOverflow, this.translationOverflow, this.orientation, this.missingTranslationMode, missingPrimaryScale);
        }

        @Override
        public ProjectionComponentType type() {
            return ProjectionComponentType.STATION_TITLE_GROUP;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeInt(this.primaryColor);
            buffer.writeInt(this.translationColor);
            buffer.writeFloat(this.primaryFontSize);
            buffer.writeFloat(this.translationFontSize);
            buffer.writeFloat(this.gap);
            ProjectionTextAlign.STREAM_CODEC.encode(buffer, this.align);
            ProjectionOverflowMode.STREAM_CODEC.encode(buffer, this.primaryOverflow);
            ProjectionOverflowMode.STREAM_CODEC.encode(buffer, this.translationOverflow);
            buffer.writeEnum(this.orientation);
            buffer.writeEnum(this.missingTranslationMode);
            buffer.writeFloat(this.missingPrimaryScale);
        }

        public static StationTitleGroup decode(RegistryFriendlyByteBuf buffer) {
            return new StationTitleGroup(
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    ProjectionTextAlign.STREAM_CODEC.decode(buffer),
                    ProjectionOverflowMode.STREAM_CODEC.decode(buffer),
                    ProjectionOverflowMode.STREAM_CODEC.decode(buffer),
                    buffer.readEnum(TextOrientation.class),
                    buffer.readEnum(MissingTranslationMode.class),
                    buffer.readFloat());
        }
    }

    record Text(ProjectionComponentType type, String binding, String text, int textColor, float fontSize, ProjectionTextAlign align, ProjectionOverflowMode overflow, TextOrientation orientation, float lineSpacing, int maxLines) implements ProjectionComponentSettings {
        public static Codec<Text> codec(ProjectionComponentType type) {
            return RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("binding", "").forGetter(Text::binding),
                    Codec.STRING.optionalFieldOf("text", "").forGetter(Text::text),
                    Codec.INT.optionalFieldOf("text_color", 0xFFFFFFFF).forGetter(Text::textColor),
                    Codec.FLOAT.optionalFieldOf("font_size", 0.18F).forGetter(Text::fontSize),
                    ProjectionTextAlign.CODEC.optionalFieldOf("align", ProjectionTextAlign.CENTER).forGetter(Text::align),
                    ProjectionOverflowMode.CODEC.optionalFieldOf("overflow", ProjectionOverflowMode.SCALE).forGetter(Text::overflow),
                    TextOrientation.CODEC.optionalFieldOf("orientation", TextOrientation.HORIZONTAL).forGetter(Text::orientation),
                    Codec.FLOAT.optionalFieldOf("line_spacing", 0.02F).forGetter(Text::lineSpacing),
                    Codec.INT.optionalFieldOf("max_lines", 1).forGetter(Text::maxLines)).apply(instance, (binding, text, textColor, fontSize, align, overflow, orientation, lineSpacing, maxLines) -> new Text(type, binding, text, textColor, fontSize, align, overflow, orientation, lineSpacing, maxLines)));
        }

        public Text(ProjectionComponentType type, String binding, String text, int textColor, float fontSize, ProjectionTextAlign align, ProjectionOverflowMode overflow, float lineSpacing, int maxLines) {
            this(type, binding, text, textColor, fontSize, align, overflow, TextOrientation.HORIZONTAL, lineSpacing, maxLines);
        }

        public Text {
            type = textType(type);
            binding = trim(binding, MAX_BINDING_LENGTH);
            text = trim(text, MAX_TEXT_LENGTH);
            fontSize = clamp(fontSize, 0.01F, 2.0F, 0.18F);
            align = align == null ? ProjectionTextAlign.CENTER : align;
            overflow = overflow == null ? ProjectionOverflowMode.SCALE : overflow;
            orientation = orientation == null ? TextOrientation.HORIZONTAL : orientation;
            lineSpacing = clamp(lineSpacing, 0.0F, 0.50F, 0.02F);
            maxLines = clampInt(maxLines, 1, 8);
        }

        public static Text stationName() {
            return new Text(ProjectionComponentType.STATION_NAME_TEXT, "station.primaryName", "", 0xFFFFFFFF, 0.22F, ProjectionTextAlign.CENTER, ProjectionOverflowMode.SCALE, TextOrientation.HORIZONTAL, 0.02F, 1);
        }

        public static Text translationName() {
            return new Text(ProjectionComponentType.TRANSLATION_TEXT, "station.translationName", "", 0xFFBFD4DC, 0.095F, ProjectionTextAlign.CENTER, ProjectionOverflowMode.SCALE, TextOrientation.HORIZONTAL, 0.018F, 1);
        }

        public static Text customText() {
            return new Text(ProjectionComponentType.CUSTOM_TEXT, "", "Custom Text", 0xFFFFFFFF, 0.11F, ProjectionTextAlign.CENTER, ProjectionOverflowMode.SCALE, TextOrientation.HORIZONTAL, 0.02F, 1);
        }

        public Text withText(String text) {
            return new Text(this.type, this.binding, text, this.textColor, this.fontSize, this.align, this.overflow, this.orientation, this.lineSpacing, this.maxLines);
        }

        public Text withFontSize(float fontSize) {
            return new Text(this.type, this.binding, this.text, this.textColor, fontSize, this.align, this.overflow, this.orientation, this.lineSpacing, this.maxLines);
        }

        public Text withTextColor(int textColor) {
            return new Text(this.type, this.binding, this.text, textColor, this.fontSize, this.align, this.overflow, this.orientation, this.lineSpacing, this.maxLines);
        }

        public Text withAlign(ProjectionTextAlign align) {
            return new Text(this.type, this.binding, this.text, this.textColor, this.fontSize, align, this.overflow, this.orientation, this.lineSpacing, this.maxLines);
        }

        public Text withOverflow(ProjectionOverflowMode overflow) {
            return new Text(this.type, this.binding, this.text, this.textColor, this.fontSize, this.align, overflow, this.orientation, this.lineSpacing, this.maxLines);
        }

        public Text withOrientation(TextOrientation orientation) {
            return new Text(this.type, this.binding, this.text, this.textColor, this.fontSize, this.align, this.overflow, orientation, this.lineSpacing, this.maxLines);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            ByteBufCodecs.STRING_UTF8.encode(buffer, this.binding);
            ByteBufCodecs.STRING_UTF8.encode(buffer, this.text);
            buffer.writeInt(this.textColor);
            buffer.writeFloat(this.fontSize);
            ProjectionTextAlign.STREAM_CODEC.encode(buffer, this.align);
            ProjectionOverflowMode.STREAM_CODEC.encode(buffer, this.overflow);
            buffer.writeEnum(this.orientation);
            buffer.writeFloat(this.lineSpacing);
            buffer.writeVarInt(this.maxLines);
        }

        public static Text decode(RegistryFriendlyByteBuf buffer, ProjectionComponentType type) {
            return new Text(type,
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    buffer.readInt(),
                    buffer.readFloat(),
                    ProjectionTextAlign.STREAM_CODEC.decode(buffer),
                    ProjectionOverflowMode.STREAM_CODEC.decode(buffer),
                    buffer.readEnum(TextOrientation.class),
                    buffer.readFloat(),
                    buffer.readVarInt());
        }

        private static ProjectionComponentType textType(ProjectionComponentType type) {
            return switch (type) {
                case STATION_NAME_TEXT, TRANSLATION_TEXT, CUSTOM_TEXT -> type;
                default -> ProjectionComponentType.CUSTOM_TEXT;
            };
        }
    }

    record ExitBadge(boolean fillEnabled, boolean borderEnabled, int fillColor, int borderColor, int textColor, float fontSize) implements ProjectionComponentSettings {

        public static final Codec<ExitBadge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("fill_enabled", true).forGetter(ExitBadge::fillEnabled),
                Codec.BOOL.optionalFieldOf("border_enabled", true).forGetter(ExitBadge::borderEnabled),
                Codec.INT.optionalFieldOf("fill", 0xFFFFCF4A).forGetter(ExitBadge::fillColor),
                Codec.INT.optionalFieldOf("border", 0xFFE5AA18).forGetter(ExitBadge::borderColor),
                Codec.INT.optionalFieldOf("text", 0xFF152026).forGetter(ExitBadge::textColor),
                Codec.FLOAT.optionalFieldOf("font_size", 0.14F).forGetter(ExitBadge::fontSize)).apply(instance, ExitBadge::new));
        public ExitBadge {
            fontSize = clamp(fontSize, 0.02F, 1.0F, 0.14F);
        }

        public static ExitBadge defaults() {
            return new ExitBadge(true, true, 0xFFFFCF4A, 0xFFE5AA18, 0xFF152026, 0.14F);
        }

        @Override
        public ProjectionComponentType type() {
            return ProjectionComponentType.EXIT_BADGE;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeBoolean(this.fillEnabled);
            buffer.writeBoolean(this.borderEnabled);
            buffer.writeInt(this.fillColor);
            buffer.writeInt(this.borderColor);
            buffer.writeInt(this.textColor);
            buffer.writeFloat(this.fontSize);
        }

        public static ExitBadge decode(RegistryFriendlyByteBuf buffer) {
            return new ExitBadge(buffer.readBoolean(), buffer.readBoolean(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readFloat());
        }
    }

    record Divider(int color, float thickness, boolean dashed) implements ProjectionComponentSettings {

        public static final Codec<Divider> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("color", 0xFF34F0B8).forGetter(Divider::color),
                Codec.FLOAT.optionalFieldOf("thickness", 0.025F).forGetter(Divider::thickness),
                Codec.BOOL.optionalFieldOf("dashed", false).forGetter(Divider::dashed)).apply(instance, Divider::new));
        public Divider {
            thickness = clamp(thickness, 0.005F, 0.50F, 0.025F);
        }

        public static Divider defaults() {
            return new Divider(0xFF34F0B8, 0.025F, false);
        }

        @Override
        public ProjectionComponentType type() {
            return ProjectionComponentType.DIVIDER;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeInt(this.color);
            buffer.writeFloat(this.thickness);
            buffer.writeBoolean(this.dashed);
        }

        public static Divider decode(RegistryFriendlyByteBuf buffer) {
            return new Divider(buffer.readInt(), buffer.readFloat(), buffer.readBoolean());
        }
    }

    record RouteList(float rowHeight, float gap, float stripeWidth, float fontSize, int maxVisible, RouteOverflowMode overflow, ProjectionOverflowMode labelOverflow, int textColor, int plusTextColor, int rotateIntervalTicks) implements ProjectionComponentSettings {

        public static final Codec<RouteList> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("row_height", 0.10F).forGetter(RouteList::rowHeight),
                Codec.FLOAT.optionalFieldOf("gap", 0.012F).forGetter(RouteList::gap),
                Codec.FLOAT.optionalFieldOf("stripe_width", 0.028F).forGetter(RouteList::stripeWidth),
                Codec.FLOAT.optionalFieldOf("font_size", 0.055F).forGetter(RouteList::fontSize),
                Codec.INT.optionalFieldOf("max_visible", 4).forGetter(RouteList::maxVisible),
                RouteOverflowMode.CODEC.optionalFieldOf("overflow", RouteOverflowMode.ROTATE).forGetter(RouteList::overflow),
                ProjectionOverflowMode.CODEC.optionalFieldOf("label_overflow", ProjectionOverflowMode.MARQUEE).forGetter(RouteList::labelOverflow),
                Codec.INT.optionalFieldOf("text_color", 0xFFFFFFFF).forGetter(RouteList::textColor),
                Codec.INT.optionalFieldOf("plus_text_color", 0xFFFFFFFF).forGetter(RouteList::plusTextColor),
                Codec.INT.optionalFieldOf("rotate_interval", 35).forGetter(RouteList::rotateIntervalTicks)).apply(instance, RouteList::new));
        public RouteList {
            rowHeight = clamp(rowHeight, 0.025F, 1.5F, 0.10F);
            gap = clamp(gap, 0.0F, 0.50F, 0.012F);
            stripeWidth = clamp(stripeWidth, 0.005F, 0.50F, 0.028F);
            fontSize = clamp(fontSize, 0.01F, 1.0F, 0.055F);
            maxVisible = clampInt(maxVisible, 1, 16);
            overflow = overflow == null ? RouteOverflowMode.ROTATE : overflow;
            labelOverflow = labelOverflow == null ? ProjectionOverflowMode.MARQUEE : labelOverflow;
            rotateIntervalTicks = clampInt(rotateIntervalTicks, 10, 400);
        }

        public static RouteList defaults() {
            return new RouteList(0.10F, 0.012F, 0.028F, 0.055F, 4, RouteOverflowMode.ROTATE, ProjectionOverflowMode.MARQUEE, 0xFFFFFFFF, 0xFFFFFFFF, 35);
        }

        @Override
        public ProjectionComponentType type() {
            return ProjectionComponentType.ROUTE_LIST;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeFloat(this.rowHeight);
            buffer.writeFloat(this.gap);
            buffer.writeFloat(this.stripeWidth);
            buffer.writeFloat(this.fontSize);
            buffer.writeVarInt(this.maxVisible);
            buffer.writeEnum(this.overflow);
            ProjectionOverflowMode.STREAM_CODEC.encode(buffer, this.labelOverflow);
            buffer.writeInt(this.textColor);
            buffer.writeInt(this.plusTextColor);
            buffer.writeVarInt(this.rotateIntervalTicks);
        }

        public static RouteList decode(RegistryFriendlyByteBuf buffer) {
            return new RouteList(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readVarInt(), buffer.readEnum(RouteOverflowMode.class), ProjectionOverflowMode.STREAM_CODEC.decode(buffer), buffer.readInt(), buffer.readInt(), buffer.readVarInt());
        }
    }

    record RouteIcon(ProjectionComponentType type, IconShape shape, float iconSize, float gap, float fontSize, int maxVisible, FlowDirection flow, RouteOverflowMode overflow, boolean showLabel, int textColor, int borderColor, float borderWidth, float ringThicknessRatio, int plusTextColor, int rotateIntervalTicks, boolean wrapEnabled, int wrapTracks) implements ProjectionComponentSettings {
        public static Codec<RouteIcon> codec(ProjectionComponentType type) {
            return RecordCodecBuilder.create(instance -> instance.group(
                    IconShape.CODEC.optionalFieldOf("shape", IconShape.CIRCLE).forGetter(RouteIcon::shape),
                    Codec.FLOAT.optionalFieldOf("icon_size", 0.12F).forGetter(RouteIcon::iconSize),
                    Codec.FLOAT.optionalFieldOf("gap", 0.032F).forGetter(RouteIcon::gap),
                    Codec.FLOAT.optionalFieldOf("font_size", 0.042F).forGetter(RouteIcon::fontSize),
                    Codec.INT.optionalFieldOf("max_visible", 6).forGetter(RouteIcon::maxVisible),
                    FlowDirection.CODEC.optionalFieldOf("flow", FlowDirection.HORIZONTAL).forGetter(RouteIcon::flow),
                    RouteOverflowMode.CODEC.optionalFieldOf("overflow", RouteOverflowMode.ROTATE).forGetter(RouteIcon::overflow),
                    Codec.BOOL.optionalFieldOf("show_label", true).forGetter(RouteIcon::showLabel),
                    Codec.INT.optionalFieldOf("text_color", 0xFFFFFFFF).forGetter(RouteIcon::textColor),
                    Codec.INT.optionalFieldOf("border_color", 0xFFFFFFFF).forGetter(RouteIcon::borderColor),
                    Codec.FLOAT.optionalFieldOf("border_width", 0.0F).forGetter(RouteIcon::borderWidth),
                    Codec.FLOAT.optionalFieldOf("ring_thickness_ratio", 0.22F).forGetter(RouteIcon::ringThicknessRatio),
                    Codec.INT.optionalFieldOf("plus_text_color", 0xFFFFFFFF).forGetter(RouteIcon::plusTextColor),
                    Codec.INT.optionalFieldOf("rotate_interval", 35).forGetter(RouteIcon::rotateIntervalTicks),
                    Codec.BOOL.optionalFieldOf("wrap_enabled", false).forGetter(RouteIcon::wrapEnabled),
                    Codec.INT.optionalFieldOf("wrap_tracks", 1).forGetter(RouteIcon::wrapTracks)).apply(instance, (shape, iconSize, gap, fontSize, maxVisible, flow, overflow, showLabel, textColor, borderColor, borderWidth, ringThicknessRatio, plusTextColor, rotateIntervalTicks, wrapEnabled, wrapTracks) -> new RouteIcon(type, shape, iconSize, gap, fontSize, maxVisible, flow, overflow, showLabel, textColor, borderColor, borderWidth, ringThicknessRatio, plusTextColor, rotateIntervalTicks, wrapEnabled, wrapTracks)));
        }

        public RouteIcon(ProjectionComponentType type, IconShape shape, float iconSize, float gap, float fontSize, int maxVisible, FlowDirection flow, RouteOverflowMode overflow, boolean showLabel, int textColor, int borderColor, float borderWidth, float ringThicknessRatio, int plusTextColor, int rotateIntervalTicks) {
            this(type, shape, iconSize, gap, fontSize, maxVisible, flow, overflow, showLabel, textColor, borderColor, borderWidth, ringThicknessRatio, plusTextColor, rotateIntervalTicks, false, 1);
        }

        public RouteIcon {
            type = iconType(type);
            shape = shape == null ? IconShape.CIRCLE : shape;
            iconSize = clamp(iconSize, 0.025F, 1.5F, 0.12F);
            gap = clamp(gap, 0.0F, 0.50F, 0.032F);
            fontSize = clamp(fontSize, 0.005F, 1.0F, 0.042F);
            maxVisible = clampInt(maxVisible, 1, 16);
            flow = flow == null ? FlowDirection.HORIZONTAL : flow;
            overflow = overflow == null ? RouteOverflowMode.ROTATE : overflow;
            borderWidth = clamp(borderWidth, 0.0F, 0.50F, 0.012F);
            ringThicknessRatio = clamp(ringThicknessRatio, 0.08F, 0.45F, 0.22F);
            rotateIntervalTicks = clampInt(rotateIntervalTicks, 10, 400);
            wrapTracks = clampInt(wrapTracks, 1, 8);
        }

        public static RouteIcon solidDefaults() {
            return new RouteIcon(ProjectionComponentType.ROUTE_ICONS, IconShape.CIRCLE, 0.12F, 0.032F, 0.042F, 6, FlowDirection.HORIZONTAL, RouteOverflowMode.ROTATE, true, 0xFFFFFFFF, 0xFFFFFFFF, 0.0F, 0.22F, 0xFFFFFFFF, 35, false, 1);
        }

        public static RouteIcon outlineDefaults() {
            return new RouteIcon(ProjectionComponentType.ROUTE_OUTLINE_ICONS, IconShape.CIRCLE, 0.14F, 0.036F, 0.046F, 6, FlowDirection.HORIZONTAL, RouteOverflowMode.ROTATE, true, 0xFFFFFFFF, 0xFFFFFFFF, 0.0F, 0.22F, 0xFFFFFFFF, 35, false, 1);
        }

        public RouteIcon withIconSize(float iconSize) {
            return new RouteIcon(this.type, this.shape, iconSize, this.gap, this.fontSize, this.maxVisible, this.flow, this.overflow, this.showLabel, this.textColor, this.borderColor, this.borderWidth, this.ringThicknessRatio, this.plusTextColor, this.rotateIntervalTicks, this.wrapEnabled, this.wrapTracks);
        }

        public RouteIcon withFontSize(float fontSize) {
            return new RouteIcon(this.type, this.shape, this.iconSize, this.gap, fontSize, this.maxVisible, this.flow, this.overflow, this.showLabel, this.textColor, this.borderColor, this.borderWidth, this.ringThicknessRatio, this.plusTextColor, this.rotateIntervalTicks, this.wrapEnabled, this.wrapTracks);
        }

        public RouteIcon withMaxVisible(int maxVisible) {
            return new RouteIcon(this.type, this.shape, this.iconSize, this.gap, this.fontSize, maxVisible, this.flow, this.overflow, this.showLabel, this.textColor, this.borderColor, this.borderWidth, this.ringThicknessRatio, this.plusTextColor, this.rotateIntervalTicks, this.wrapEnabled, this.wrapTracks);
        }

        public RouteIcon withFlow(FlowDirection flow) {
            return new RouteIcon(this.type, this.shape, this.iconSize, this.gap, this.fontSize, this.maxVisible, flow, this.overflow, this.showLabel, this.textColor, this.borderColor, this.borderWidth, this.ringThicknessRatio, this.plusTextColor, this.rotateIntervalTicks, this.wrapEnabled, this.wrapTracks);
        }

        public RouteIcon withOverflow(RouteOverflowMode overflow) {
            return new RouteIcon(this.type, this.shape, this.iconSize, this.gap, this.fontSize, this.maxVisible, this.flow, overflow, this.showLabel, this.textColor, this.borderColor, this.borderWidth, this.ringThicknessRatio, this.plusTextColor, this.rotateIntervalTicks, this.wrapEnabled, this.wrapTracks);
        }

        public RouteIcon withShape(IconShape shape) {
            return new RouteIcon(this.type, shape, this.iconSize, this.gap, this.fontSize, this.maxVisible, this.flow, this.overflow, this.showLabel, this.textColor, this.borderColor, this.borderWidth, this.ringThicknessRatio, this.plusTextColor, this.rotateIntervalTicks, this.wrapEnabled, this.wrapTracks);
        }

        public RouteIcon withShowLabel(boolean showLabel) {
            return new RouteIcon(this.type, this.shape, this.iconSize, this.gap, this.fontSize, this.maxVisible, this.flow, this.overflow, showLabel, this.textColor, this.borderColor, this.borderWidth, this.ringThicknessRatio, this.plusTextColor, this.rotateIntervalTicks, this.wrapEnabled, this.wrapTracks);
        }

        public RouteIcon withTextColor(int textColor) {
            return new RouteIcon(this.type, this.shape, this.iconSize, this.gap, this.fontSize, this.maxVisible, this.flow, this.overflow, this.showLabel, textColor, this.borderColor, this.borderWidth, this.ringThicknessRatio, this.plusTextColor, this.rotateIntervalTicks, this.wrapEnabled, this.wrapTracks);
        }

        public RouteIcon withBorderColor(int borderColor) {
            return new RouteIcon(this.type, this.shape, this.iconSize, this.gap, this.fontSize, this.maxVisible, this.flow, this.overflow, this.showLabel, this.textColor, borderColor, this.borderWidth, this.ringThicknessRatio, this.plusTextColor, this.rotateIntervalTicks, this.wrapEnabled, this.wrapTracks);
        }

        public RouteIcon withPlusTextColor(int plusTextColor) {
            return new RouteIcon(this.type, this.shape, this.iconSize, this.gap, this.fontSize, this.maxVisible, this.flow, this.overflow, this.showLabel, this.textColor, this.borderColor, this.borderWidth, this.ringThicknessRatio, plusTextColor, this.rotateIntervalTicks, this.wrapEnabled, this.wrapTracks);
        }

        public RouteIcon withBorderWidth(float borderWidth) {
            return new RouteIcon(this.type, this.shape, this.iconSize, this.gap, this.fontSize, this.maxVisible, this.flow, this.overflow, this.showLabel, this.textColor, this.borderColor, borderWidth, this.ringThicknessRatio, this.plusTextColor, this.rotateIntervalTicks, this.wrapEnabled, this.wrapTracks);
        }

        public RouteIcon withRingThicknessRatio(float ringThicknessRatio) {
            return new RouteIcon(this.type, this.shape, this.iconSize, this.gap, this.fontSize, this.maxVisible, this.flow, this.overflow, this.showLabel, this.textColor, this.borderColor, this.borderWidth, ringThicknessRatio, this.plusTextColor, this.rotateIntervalTicks, this.wrapEnabled, this.wrapTracks);
        }

        public RouteIcon withRotateIntervalTicks(int rotateIntervalTicks) {
            return new RouteIcon(this.type, this.shape, this.iconSize, this.gap, this.fontSize, this.maxVisible, this.flow, this.overflow, this.showLabel, this.textColor, this.borderColor, this.borderWidth, this.ringThicknessRatio, this.plusTextColor, rotateIntervalTicks, this.wrapEnabled, this.wrapTracks);
        }

        public RouteIcon withWrapEnabled(boolean wrapEnabled) {
            return new RouteIcon(this.type, this.shape, this.iconSize, this.gap, this.fontSize, this.maxVisible, this.flow, this.overflow, this.showLabel, this.textColor, this.borderColor, this.borderWidth, this.ringThicknessRatio, this.plusTextColor, this.rotateIntervalTicks, wrapEnabled, this.wrapTracks);
        }

        public RouteIcon withWrapTracks(int wrapTracks) {
            return new RouteIcon(this.type, this.shape, this.iconSize, this.gap, this.fontSize, this.maxVisible, this.flow, this.overflow, this.showLabel, this.textColor, this.borderColor, this.borderWidth, this.ringThicknessRatio, this.plusTextColor, this.rotateIntervalTicks, this.wrapEnabled, wrapTracks);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeEnum(this.shape);
            buffer.writeFloat(this.iconSize);
            buffer.writeFloat(this.gap);
            buffer.writeFloat(this.fontSize);
            buffer.writeVarInt(this.maxVisible);
            buffer.writeEnum(this.flow);
            buffer.writeEnum(this.overflow);
            buffer.writeBoolean(this.showLabel);
            buffer.writeInt(this.textColor);
            buffer.writeInt(this.borderColor);
            buffer.writeFloat(this.borderWidth);
            buffer.writeFloat(this.ringThicknessRatio);
            buffer.writeInt(this.plusTextColor);
            buffer.writeVarInt(this.rotateIntervalTicks);
            buffer.writeBoolean(this.wrapEnabled);
            buffer.writeVarInt(this.wrapTracks);
        }

        public static RouteIcon decode(RegistryFriendlyByteBuf buffer, ProjectionComponentType type) {
            return new RouteIcon(type, buffer.readEnum(IconShape.class), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readVarInt(), buffer.readEnum(FlowDirection.class), buffer.readEnum(RouteOverflowMode.class), buffer.readBoolean(), buffer.readInt(), buffer.readInt(), buffer.readFloat(), buffer.readFloat(), buffer.readInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt());
        }

        private static ProjectionComponentType iconType(ProjectionComponentType type) {
            return type == ProjectionComponentType.ROUTE_OUTLINE_ICONS ? ProjectionComponentType.ROUTE_OUTLINE_ICONS : ProjectionComponentType.ROUTE_ICONS;
        }
    }

    record RouteCapsules(float capsuleWidth, float capsuleHeight, float gap, float fontSize, int maxVisible, FlowDirection flow, CapsuleContentOrientation contentOrientation, RouteOverflowMode overflow, ProjectionOverflowMode labelOverflow, boolean showShortName, int textColor, int fillColor, int plusTextColor, int rotateIntervalTicks) implements ProjectionComponentSettings {

        public static final Codec<RouteCapsules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("capsule_width", 0.36F).forGetter(RouteCapsules::capsuleWidth),
                Codec.FLOAT.optionalFieldOf("capsule_height", 0.11F).forGetter(RouteCapsules::capsuleHeight),
                Codec.FLOAT.optionalFieldOf("gap", 0.024F).forGetter(RouteCapsules::gap),
                Codec.FLOAT.optionalFieldOf("font_size", 0.052F).forGetter(RouteCapsules::fontSize),
                Codec.INT.optionalFieldOf("max_visible", 4).forGetter(RouteCapsules::maxVisible),
                FlowDirection.CODEC.optionalFieldOf("flow", FlowDirection.VERTICAL).forGetter(RouteCapsules::flow),
                CapsuleContentOrientation.CODEC.optionalFieldOf("content_orientation", CapsuleContentOrientation.HORIZONTAL).forGetter(RouteCapsules::contentOrientation),
                RouteOverflowMode.CODEC.optionalFieldOf("overflow", RouteOverflowMode.ROTATE).forGetter(RouteCapsules::overflow),
                ProjectionOverflowMode.CODEC.optionalFieldOf("label_overflow", ProjectionOverflowMode.SCALE).forGetter(RouteCapsules::labelOverflow),
                Codec.BOOL.optionalFieldOf("show_short_name", false).forGetter(RouteCapsules::showShortName),
                Codec.INT.optionalFieldOf("text_color", 0xFFFFFFFF).forGetter(RouteCapsules::textColor),
                Codec.INT.optionalFieldOf("fill", 0x55333333).forGetter(RouteCapsules::fillColor),
                Codec.INT.optionalFieldOf("plus_text_color", 0xFFFFFFFF).forGetter(RouteCapsules::plusTextColor),
                Codec.INT.optionalFieldOf("rotate_interval", 35).forGetter(RouteCapsules::rotateIntervalTicks)).apply(instance, RouteCapsules::new));
        public RouteCapsules {
            capsuleWidth = clamp(capsuleWidth, 0.06F, 4.0F, 0.36F);
            capsuleHeight = clamp(capsuleHeight, 0.025F, 1.5F, 0.11F);
            gap = clamp(gap, 0.0F, 0.50F, 0.024F);
            fontSize = clamp(fontSize, 0.01F, 1.0F, 0.052F);
            maxVisible = clampInt(maxVisible, 1, 16);
            flow = flow == null ? FlowDirection.VERTICAL : flow;
            contentOrientation = contentOrientation == null ? CapsuleContentOrientation.HORIZONTAL : contentOrientation;
            overflow = overflow == null ? RouteOverflowMode.ROTATE : overflow;
            labelOverflow = labelOverflow == null ? ProjectionOverflowMode.SCALE : labelOverflow;
            rotateIntervalTicks = clampInt(rotateIntervalTicks, 10, 400);
        }

        public static RouteCapsules defaults() {
            return new RouteCapsules(0.36F, 0.11F, 0.024F, 0.052F, 4, FlowDirection.VERTICAL, CapsuleContentOrientation.HORIZONTAL, RouteOverflowMode.ROTATE, ProjectionOverflowMode.SCALE, false, 0xFFFFFFFF, 0x55333333, 0xFFFFFFFF, 35);
        }

        @Override
        public ProjectionComponentType type() {
            return ProjectionComponentType.ROUTE_CAPSULES;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeFloat(this.capsuleWidth);
            buffer.writeFloat(this.capsuleHeight);
            buffer.writeFloat(this.gap);
            buffer.writeFloat(this.fontSize);
            buffer.writeVarInt(this.maxVisible);
            buffer.writeEnum(this.flow);
            buffer.writeEnum(this.contentOrientation);
            buffer.writeEnum(this.overflow);
            ProjectionOverflowMode.STREAM_CODEC.encode(buffer, this.labelOverflow);
            buffer.writeBoolean(this.showShortName);
            buffer.writeInt(this.textColor);
            buffer.writeInt(this.fillColor);
            buffer.writeInt(this.plusTextColor);
            buffer.writeVarInt(this.rotateIntervalTicks);
        }

        public static RouteCapsules decode(RegistryFriendlyByteBuf buffer) {
            return new RouteCapsules(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readVarInt(), buffer.readEnum(FlowDirection.class), buffer.readEnum(CapsuleContentOrientation.class), buffer.readEnum(RouteOverflowMode.class), ProjectionOverflowMode.STREAM_CODEC.decode(buffer), buffer.readBoolean(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readVarInt());
        }
    }

    record RouteBackplate(StripeDirection direction, ColorPolicy colorPolicy, int maxVisible, float opacity, RouteOverflowMode overflow, int plusTextColor, int rotateIntervalTicks) implements ProjectionComponentSettings {

        public static final Codec<RouteBackplate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                StripeDirection.CODEC.optionalFieldOf("direction", StripeDirection.HORIZONTAL).forGetter(RouteBackplate::direction),
                ColorPolicy.CODEC.optionalFieldOf("color_policy", ColorPolicy.ROUTE_ORDER).forGetter(RouteBackplate::colorPolicy),
                Codec.INT.optionalFieldOf("max_visible", 8).forGetter(RouteBackplate::maxVisible),
                Codec.FLOAT.optionalFieldOf("opacity", 1.0F).forGetter(RouteBackplate::opacity),
                RouteOverflowMode.CODEC.optionalFieldOf("overflow", RouteOverflowMode.ROTATE).forGetter(RouteBackplate::overflow),
                Codec.INT.optionalFieldOf("plus_text_color", 0xFFFFFFFF).forGetter(RouteBackplate::plusTextColor),
                Codec.INT.optionalFieldOf("rotate_interval", 35).forGetter(RouteBackplate::rotateIntervalTicks)).apply(instance, RouteBackplate::new));
        public RouteBackplate {
            direction = direction == null ? StripeDirection.HORIZONTAL : direction;
            colorPolicy = colorPolicy == null ? ColorPolicy.ROUTE_ORDER : colorPolicy;
            maxVisible = clampInt(maxVisible, 1, 16);
            opacity = clamp(opacity, 0.0F, 1.0F, 1.0F);
            overflow = overflow == null ? RouteOverflowMode.ROTATE : overflow;
            rotateIntervalTicks = clampInt(rotateIntervalTicks, 10, 400);
        }

        public static RouteBackplate defaults() {
            return new RouteBackplate(StripeDirection.HORIZONTAL, ColorPolicy.ROUTE_ORDER, 8, 1.0F, RouteOverflowMode.ROTATE, 0xFFFFFFFF, 35);
        }

        @Override
        public ProjectionComponentType type() {
            return ProjectionComponentType.ROUTE_BACKPLATE;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeEnum(this.direction);
            buffer.writeEnum(this.colorPolicy);
            buffer.writeVarInt(this.maxVisible);
            buffer.writeFloat(this.opacity);
            buffer.writeEnum(this.overflow);
            buffer.writeInt(this.plusTextColor);
            buffer.writeVarInt(this.rotateIntervalTicks);
        }

        public static RouteBackplate decode(RegistryFriendlyByteBuf buffer) {
            return new RouteBackplate(buffer.readEnum(StripeDirection.class), buffer.readEnum(ColorPolicy.class), buffer.readVarInt(), buffer.readFloat(), buffer.readEnum(RouteOverflowMode.class), buffer.readInt(), buffer.readVarInt());
        }
    }

    record RouteText(float fontSize, RouteOverflowMode overflow, boolean shortName, int textColor, int plusTextColor, ProjectionTextAlign align, int rotateIntervalTicks) implements ProjectionComponentSettings {

        public static final Codec<RouteText> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("font_size", 0.12F).forGetter(RouteText::fontSize),
                RouteOverflowMode.CODEC.optionalFieldOf("overflow", RouteOverflowMode.ROTATE).forGetter(RouteText::overflow),
                Codec.BOOL.optionalFieldOf("short_name", false).forGetter(RouteText::shortName),
                Codec.INT.optionalFieldOf("text_color", 0xFFFFFFFF).forGetter(RouteText::textColor),
                Codec.INT.optionalFieldOf("plus_text_color", 0xFFFFFFFF).forGetter(RouteText::plusTextColor),
                ProjectionTextAlign.CODEC.optionalFieldOf("align", ProjectionTextAlign.CENTER).forGetter(RouteText::align),
                Codec.INT.optionalFieldOf("rotate_interval", 35).forGetter(RouteText::rotateIntervalTicks)).apply(instance, RouteText::new));
        public RouteText {
            fontSize = clamp(fontSize, 0.01F, 1.0F, 0.12F);
            overflow = overflow == null ? RouteOverflowMode.ROTATE : overflow;
            align = align == null ? ProjectionTextAlign.CENTER : align;
            rotateIntervalTicks = clampInt(rotateIntervalTicks, 10, 400);
        }

        public static RouteText defaults() {
            return new RouteText(0.12F, RouteOverflowMode.ROTATE, false, 0xFFFFFFFF, 0xFFFFFFFF, ProjectionTextAlign.CENTER, 35);
        }

        @Override
        public ProjectionComponentType type() {
            return ProjectionComponentType.ROUTE_TEXT;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeFloat(this.fontSize);
            buffer.writeEnum(this.overflow);
            buffer.writeBoolean(this.shortName);
            buffer.writeInt(this.textColor);
            buffer.writeInt(this.plusTextColor);
            ProjectionTextAlign.STREAM_CODEC.encode(buffer, this.align);
            buffer.writeVarInt(this.rotateIntervalTicks);
        }

        public static RouteText decode(RegistryFriendlyByteBuf buffer) {
            return new RouteText(buffer.readFloat(), buffer.readEnum(RouteOverflowMode.class), buffer.readBoolean(), buffer.readInt(), buffer.readInt(), ProjectionTextAlign.STREAM_CODEC.decode(buffer), buffer.readVarInt());
        }
    }

    record PlatformTitleGroup(
            PlatformTitleContent content,
            int primaryColor,
            int secondaryColor,
            float primaryFontSize,
            float secondaryFontSize,
            float gap,
            ProjectionTextAlign align,
            ProjectionOverflowMode primaryOverflow,
            ProjectionOverflowMode secondaryOverflow,
            TextOrientation orientation,
            MissingTranslationMode missingSecondaryMode,
            float missingPrimaryScale) implements ProjectionComponentSettings {

        public static final Codec<PlatformTitleGroup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                PlatformTitleContent.CODEC.optionalFieldOf("content", PlatformTitleContent.STATION_AND_PLATFORM).forGetter(PlatformTitleGroup::content),
                Codec.INT.optionalFieldOf("primary_color", 0xFFFFFFFF).forGetter(PlatformTitleGroup::primaryColor),
                Codec.INT.optionalFieldOf("secondary_color", 0xFFBFD4DC).forGetter(PlatformTitleGroup::secondaryColor),
                Codec.FLOAT.optionalFieldOf("primary_font_size", 0.18F).forGetter(PlatformTitleGroup::primaryFontSize),
                Codec.FLOAT.optionalFieldOf("secondary_font_size", 0.075F).forGetter(PlatformTitleGroup::secondaryFontSize),
                Codec.FLOAT.optionalFieldOf("gap", 0.025F).forGetter(PlatformTitleGroup::gap),
                ProjectionTextAlign.CODEC.optionalFieldOf("align", ProjectionTextAlign.CENTER).forGetter(PlatformTitleGroup::align),
                ProjectionOverflowMode.CODEC.optionalFieldOf("primary_overflow", ProjectionOverflowMode.SCALE).forGetter(PlatformTitleGroup::primaryOverflow),
                ProjectionOverflowMode.CODEC.optionalFieldOf("secondary_overflow", ProjectionOverflowMode.MARQUEE).forGetter(PlatformTitleGroup::secondaryOverflow),
                TextOrientation.CODEC.optionalFieldOf("orientation", TextOrientation.HORIZONTAL).forGetter(PlatformTitleGroup::orientation),
                MissingTranslationMode.CODEC.optionalFieldOf("missing_secondary", MissingTranslationMode.EXPAND_PRIMARY).forGetter(PlatformTitleGroup::missingSecondaryMode),
                Codec.FLOAT.optionalFieldOf("missing_primary_scale", 1.18F).forGetter(PlatformTitleGroup::missingPrimaryScale)).apply(instance, PlatformTitleGroup::new));
        public PlatformTitleGroup {
            content = content == null ? PlatformTitleContent.STATION_AND_PLATFORM : content;
            primaryFontSize = clamp(primaryFontSize, 0.01F, 2.0F, 0.18F);
            secondaryFontSize = clamp(secondaryFontSize, 0.01F, 2.0F, 0.075F);
            gap = clamp(gap, 0.0F, 0.50F, 0.025F);
            align = align == null ? ProjectionTextAlign.CENTER : align;
            primaryOverflow = primaryOverflow == null ? ProjectionOverflowMode.SCALE : primaryOverflow;
            secondaryOverflow = secondaryOverflow == null ? ProjectionOverflowMode.MARQUEE : secondaryOverflow;
            orientation = orientation == null ? TextOrientation.HORIZONTAL : orientation;
            missingSecondaryMode = missingSecondaryMode == null ? MissingTranslationMode.EXPAND_PRIMARY : missingSecondaryMode;
            missingPrimaryScale = clamp(missingPrimaryScale, 1.0F, 2.5F, 1.18F);
        }

        public static PlatformTitleGroup defaults() {
            return new PlatformTitleGroup(PlatformTitleContent.STATION_AND_PLATFORM, 0xFFFFFFFF, 0xFFBFD4DC, 0.18F, 0.075F, 0.025F, ProjectionTextAlign.CENTER, ProjectionOverflowMode.SCALE, ProjectionOverflowMode.MARQUEE, TextOrientation.HORIZONTAL, MissingTranslationMode.EXPAND_PRIMARY, 1.18F);
        }

        @Override
        public ProjectionComponentType type() {
            return ProjectionComponentType.PLATFORM_TITLE_GROUP;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeEnum(this.content);
            buffer.writeInt(this.primaryColor);
            buffer.writeInt(this.secondaryColor);
            buffer.writeFloat(this.primaryFontSize);
            buffer.writeFloat(this.secondaryFontSize);
            buffer.writeFloat(this.gap);
            ProjectionTextAlign.STREAM_CODEC.encode(buffer, this.align);
            ProjectionOverflowMode.STREAM_CODEC.encode(buffer, this.primaryOverflow);
            ProjectionOverflowMode.STREAM_CODEC.encode(buffer, this.secondaryOverflow);
            buffer.writeEnum(this.orientation);
            buffer.writeEnum(this.missingSecondaryMode);
            buffer.writeFloat(this.missingPrimaryScale);
        }

        public static PlatformTitleGroup decode(RegistryFriendlyByteBuf buffer) {
            return new PlatformTitleGroup(buffer.readEnum(PlatformTitleContent.class), buffer.readInt(), buffer.readInt(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), ProjectionTextAlign.STREAM_CODEC.decode(buffer), ProjectionOverflowMode.STREAM_CODEC.decode(buffer), ProjectionOverflowMode.STREAM_CODEC.decode(buffer), buffer.readEnum(TextOrientation.class), buffer.readEnum(MissingTranslationMode.class), buffer.readFloat());
        }
    }

    record PlatformBadge(PlatformBadgeStyle style, boolean useLineColor, int fillColor, int borderColor, int textColor, float fontSize, float borderWidth, String prefix, String suffix) implements ProjectionComponentSettings {

        public static final Codec<PlatformBadge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                PlatformBadgeStyle.CODEC.optionalFieldOf("style", PlatformBadgeStyle.SOLID).forGetter(PlatformBadge::style),
                Codec.BOOL.optionalFieldOf("use_line_color", true).forGetter(PlatformBadge::useLineColor),
                Codec.INT.optionalFieldOf("fill", 0xFF1E88E5).forGetter(PlatformBadge::fillColor),
                Codec.INT.optionalFieldOf("border", 0xFFFFFFFF).forGetter(PlatformBadge::borderColor),
                Codec.INT.optionalFieldOf("text", 0xFFFFFFFF).forGetter(PlatformBadge::textColor),
                Codec.FLOAT.optionalFieldOf("font_size", 0.10F).forGetter(PlatformBadge::fontSize),
                Codec.FLOAT.optionalFieldOf("border_width", 0.010F).forGetter(PlatformBadge::borderWidth),
                Codec.STRING.optionalFieldOf("prefix", "").forGetter(PlatformBadge::prefix),
                Codec.STRING.optionalFieldOf("suffix", "").forGetter(PlatformBadge::suffix)).apply(instance, PlatformBadge::new));
        public PlatformBadge {
            style = style == null ? PlatformBadgeStyle.SOLID : style;
            fontSize = clamp(fontSize, 0.01F, 1.0F, 0.10F);
            borderWidth = clamp(borderWidth, 0.0F, 0.50F, 0.010F);
            prefix = trim(prefix, 24);
            suffix = trim(suffix, 24);
        }

        public static PlatformBadge defaults() {
            return new PlatformBadge(PlatformBadgeStyle.CAPSULE, true, 0xFF1E88E5, 0xFFFFFFFF, 0xFFFFFFFF, 0.10F, 0.010F, "", "");
        }

        @Override
        public ProjectionComponentType type() {
            return ProjectionComponentType.PLATFORM_BADGE;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeEnum(this.style);
            buffer.writeBoolean(this.useLineColor);
            buffer.writeInt(this.fillColor);
            buffer.writeInt(this.borderColor);
            buffer.writeInt(this.textColor);
            buffer.writeFloat(this.fontSize);
            buffer.writeFloat(this.borderWidth);
            buffer.writeUtf(this.prefix, MAX_TEXT_LENGTH);
            buffer.writeUtf(this.suffix, MAX_TEXT_LENGTH);
        }

        public static PlatformBadge decode(RegistryFriendlyByteBuf buffer) {
            return new PlatformBadge(buffer.readEnum(PlatformBadgeStyle.class), buffer.readBoolean(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readFloat(), buffer.readFloat(), buffer.readUtf(MAX_TEXT_LENGTH), buffer.readUtf(MAX_TEXT_LENGTH));
        }
    }

    record PlatformDirection(PlatformDirectionSource source, PlatformDirectionPrefix prefix, ArrowDirection arrow, ArrowPlacement arrowPlacement, int textColor, int arrowColor, float fontSize, ProjectionTextAlign align, ProjectionOverflowMode overflow) implements ProjectionComponentSettings {

        public static final Codec<PlatformDirection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                PlatformDirectionSource.CODEC.optionalFieldOf("source", PlatformDirectionSource.TERMINAL).forGetter(PlatformDirection::source),
                PlatformDirectionPrefix.CODEC.optionalFieldOf("prefix", PlatformDirectionPrefix.TOWARDS).forGetter(PlatformDirection::prefix),
                ArrowDirection.CODEC.optionalFieldOf("arrow", ArrowDirection.AUTO).forGetter(PlatformDirection::arrow),
                ArrowPlacement.CODEC.optionalFieldOf("arrow_placement", ArrowPlacement.BEFORE).forGetter(PlatformDirection::arrowPlacement),
                Codec.INT.optionalFieldOf("text_color", 0xFFFFFFFF).forGetter(PlatformDirection::textColor),
                Codec.INT.optionalFieldOf("arrow_color", 0xFFFFFFFF).forGetter(PlatformDirection::arrowColor),
                Codec.FLOAT.optionalFieldOf("font_size", 0.115F).forGetter(PlatformDirection::fontSize),
                ProjectionTextAlign.CODEC.optionalFieldOf("align", ProjectionTextAlign.CENTER).forGetter(PlatformDirection::align),
                ProjectionOverflowMode.CODEC.optionalFieldOf("overflow", ProjectionOverflowMode.MARQUEE).forGetter(PlatformDirection::overflow)).apply(instance, PlatformDirection::new));
        public PlatformDirection {
            source = source == null ? PlatformDirectionSource.TERMINAL : source;
            prefix = prefix == null ? PlatformDirectionPrefix.TOWARDS : prefix;
            arrow = arrow == null ? ArrowDirection.AUTO : arrow;
            arrowPlacement = arrowPlacement == null ? ArrowPlacement.BEFORE : arrowPlacement;
            fontSize = clamp(fontSize, 0.01F, 1.0F, 0.115F);
            align = align == null ? ProjectionTextAlign.CENTER : align;
            overflow = overflow == null ? ProjectionOverflowMode.MARQUEE : overflow;
        }

        public static PlatformDirection defaults() {
            return new PlatformDirection(PlatformDirectionSource.TERMINAL, PlatformDirectionPrefix.TOWARDS, ArrowDirection.AUTO, ArrowPlacement.BEFORE, 0xFFFFFFFF, 0xFFFFFFFF, 0.115F, ProjectionTextAlign.CENTER, ProjectionOverflowMode.MARQUEE);
        }

        @Override
        public ProjectionComponentType type() {
            return ProjectionComponentType.PLATFORM_DIRECTION_TITLE;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeEnum(this.source);
            buffer.writeEnum(this.prefix);
            buffer.writeEnum(this.arrow);
            buffer.writeEnum(this.arrowPlacement);
            buffer.writeInt(this.textColor);
            buffer.writeInt(this.arrowColor);
            buffer.writeFloat(this.fontSize);
            ProjectionTextAlign.STREAM_CODEC.encode(buffer, this.align);
            ProjectionOverflowMode.STREAM_CODEC.encode(buffer, this.overflow);
        }

        public static PlatformDirection decode(RegistryFriendlyByteBuf buffer) {
            return new PlatformDirection(buffer.readEnum(PlatformDirectionSource.class), buffer.readEnum(PlatformDirectionPrefix.class), buffer.readEnum(ArrowDirection.class), buffer.readEnum(ArrowPlacement.class), buffer.readInt(), buffer.readInt(), buffer.readFloat(), ProjectionTextAlign.STREAM_CODEC.decode(buffer), ProjectionOverflowMode.STREAM_CODEC.decode(buffer));
        }
    }

    record PlatformStatusTags(boolean showTerminal, boolean showLoop, boolean showBidirectional, boolean showTransfer, boolean showMissingLine, PlatformStatusScope scope, ProjectionTextAlign align, int fillColor, int textColor, float fontSize, float gap) implements ProjectionComponentSettings {

        public static final Codec<PlatformStatusTags> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("terminal", true).forGetter(PlatformStatusTags::showTerminal),
                Codec.BOOL.optionalFieldOf("loop", true).forGetter(PlatformStatusTags::showLoop),
                Codec.BOOL.optionalFieldOf("bidirectional", true).forGetter(PlatformStatusTags::showBidirectional),
                Codec.BOOL.optionalFieldOf("transfer", true).forGetter(PlatformStatusTags::showTransfer),
                Codec.BOOL.optionalFieldOf("missing_line", true).forGetter(PlatformStatusTags::showMissingLine),
                PlatformStatusScope.CODEC.optionalFieldOf("scope", PlatformStatusScope.PLATFORM_SERVICE).forGetter(PlatformStatusTags::scope),
                ProjectionTextAlign.CODEC.optionalFieldOf("align", ProjectionTextAlign.CENTER).forGetter(PlatformStatusTags::align),
                Codec.INT.optionalFieldOf("fill", 0xAA111820).forGetter(PlatformStatusTags::fillColor),
                Codec.INT.optionalFieldOf("text", 0xFFFFFFFF).forGetter(PlatformStatusTags::textColor),
                Codec.FLOAT.optionalFieldOf("font_size", 0.060F).forGetter(PlatformStatusTags::fontSize),
                Codec.FLOAT.optionalFieldOf("gap", 0.020F).forGetter(PlatformStatusTags::gap)).apply(instance, PlatformStatusTags::new));
        public PlatformStatusTags {
            scope = scope == null ? PlatformStatusScope.PLATFORM_SERVICE : scope;
            align = align == null ? ProjectionTextAlign.CENTER : align;
            fontSize = clamp(fontSize, 0.01F, 1.0F, 0.060F);
            gap = clamp(gap, 0.0F, 0.50F, 0.020F);
        }

        public static PlatformStatusTags defaults() {
            return new PlatformStatusTags(true, true, true, true, true, PlatformStatusScope.PLATFORM_SERVICE, ProjectionTextAlign.CENTER, 0xAA111820, 0xFFFFFFFF, 0.060F, 0.020F);
        }

        @Override
        public ProjectionComponentType type() {
            return ProjectionComponentType.PLATFORM_STATUS_TAGS;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeBoolean(this.showTerminal);
            buffer.writeBoolean(this.showLoop);
            buffer.writeBoolean(this.showBidirectional);
            buffer.writeBoolean(this.showTransfer);
            buffer.writeBoolean(this.showMissingLine);
            buffer.writeEnum(this.scope);
            ProjectionTextAlign.STREAM_CODEC.encode(buffer, this.align);
            buffer.writeInt(this.fillColor);
            buffer.writeInt(this.textColor);
            buffer.writeFloat(this.fontSize);
            buffer.writeFloat(this.gap);
        }

        public static PlatformStatusTags decode(RegistryFriendlyByteBuf buffer) {
            return new PlatformStatusTags(buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readEnum(PlatformStatusScope.class), ProjectionTextAlign.STREAM_CODEC.decode(buffer), buffer.readInt(), buffer.readInt(), buffer.readFloat(), buffer.readFloat());
        }

        public PlatformStatusTags withAlign(ProjectionTextAlign align) {
            return new PlatformStatusTags(this.showTerminal, this.showLoop, this.showBidirectional, this.showTransfer, this.showMissingLine, this.scope, align, this.fillColor, this.textColor, this.fontSize, this.gap);
        }
    }

    record PlatformLine(ProjectionComponentType type, PlatformLineStyle style, StripeDirection direction, float lineWidth, float nodeSize, PlatformNodeStyle nodeStyle, boolean showLabel, int textColor, float fontSize, ProjectionOverflowMode overflow) implements ProjectionComponentSettings {
        public static Codec<PlatformLine> codec(ProjectionComponentType type) {
            return RecordCodecBuilder.create(instance -> instance.group(
                    PlatformLineStyle.CODEC.optionalFieldOf("style", styleFor(type)).forGetter(PlatformLine::style),
                    StripeDirection.CODEC.optionalFieldOf("direction", StripeDirection.HORIZONTAL).forGetter(PlatformLine::direction),
                    Codec.FLOAT.optionalFieldOf("line_width", 0.055F).forGetter(PlatformLine::lineWidth),
                    Codec.FLOAT.optionalFieldOf("node_size", 0.115F).forGetter(PlatformLine::nodeSize),
                    PlatformNodeStyle.CODEC.optionalFieldOf("node_style", PlatformNodeStyle.SOLID).forGetter(PlatformLine::nodeStyle),
                    Codec.BOOL.optionalFieldOf("show_label", true).forGetter(PlatformLine::showLabel),
                    Codec.INT.optionalFieldOf("text_color", 0xFFFFFFFF).forGetter(PlatformLine::textColor),
                    Codec.FLOAT.optionalFieldOf("font_size", 0.070F).forGetter(PlatformLine::fontSize),
                    ProjectionOverflowMode.CODEC.optionalFieldOf("overflow", ProjectionOverflowMode.MARQUEE).forGetter(PlatformLine::overflow)).apply(instance, (style, direction, lineWidth, nodeSize, nodeStyle, showLabel, textColor, fontSize, overflow) -> new PlatformLine(type, style, direction, lineWidth, nodeSize, nodeStyle, showLabel, textColor, fontSize, overflow)));
        }

        public PlatformLine {
            type = platformLineType(type);
            style = style == null ? styleFor(type) : style;
            direction = direction == null ? StripeDirection.HORIZONTAL : direction;
            lineWidth = clamp(lineWidth, 0.004F, 1.0F, 0.055F);
            nodeSize = clamp(nodeSize, 0.010F, 1.5F, 0.115F);
            nodeStyle = nodeStyle == null ? PlatformNodeStyle.SOLID : nodeStyle;
            fontSize = clamp(fontSize, 0.01F, 1.0F, 0.070F);
            overflow = overflow == null ? ProjectionOverflowMode.MARQUEE : overflow;
        }

        public static PlatformLine currentDefaults() {
            return new PlatformLine(ProjectionComponentType.PLATFORM_LINE_CURRENT, PlatformLineStyle.CURRENT_NODE, StripeDirection.HORIZONTAL, 0.055F, 0.115F, PlatformNodeStyle.SOLID, true, 0xFFFFFFFF, 0.070F, ProjectionOverflowMode.MARQUEE);
        }

        public static PlatformLine bandDefaults() {
            return new PlatformLine(ProjectionComponentType.PLATFORM_LINE_BAND, PlatformLineStyle.BAND, StripeDirection.HORIZONTAL, 0.18F, 0.0F, PlatformNodeStyle.NONE, true, 0xFFFFFFFF, 0.080F, ProjectionOverflowMode.MARQUEE);
        }

        public static PlatformLine terminalDefaults() {
            return new PlatformLine(ProjectionComponentType.PLATFORM_TERMINAL_STRIP, PlatformLineStyle.TERMINAL_STRIP, StripeDirection.HORIZONTAL, 0.10F, 0.0F, PlatformNodeStyle.NONE, true, 0xFFFFFFFF, 0.080F, ProjectionOverflowMode.MARQUEE);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeEnum(this.style);
            buffer.writeEnum(this.direction);
            buffer.writeFloat(this.lineWidth);
            buffer.writeFloat(this.nodeSize);
            buffer.writeEnum(this.nodeStyle);
            buffer.writeBoolean(this.showLabel);
            buffer.writeInt(this.textColor);
            buffer.writeFloat(this.fontSize);
            ProjectionOverflowMode.STREAM_CODEC.encode(buffer, this.overflow);
        }

        public static PlatformLine decode(RegistryFriendlyByteBuf buffer, ProjectionComponentType type) {
            return new PlatformLine(type, buffer.readEnum(PlatformLineStyle.class), buffer.readEnum(StripeDirection.class), buffer.readFloat(), buffer.readFloat(), buffer.readEnum(PlatformNodeStyle.class), buffer.readBoolean(), buffer.readInt(), buffer.readFloat(), ProjectionOverflowMode.STREAM_CODEC.decode(buffer));
        }

        private static PlatformLineStyle styleFor(ProjectionComponentType type) {
            return switch (type) {
                case PLATFORM_LINE_BAND -> PlatformLineStyle.BAND;
                case PLATFORM_TERMINAL_STRIP -> PlatformLineStyle.TERMINAL_STRIP;
                default -> PlatformLineStyle.CURRENT_NODE;
            };
        }

        private static ProjectionComponentType platformLineType(ProjectionComponentType type) {
            return switch (type) {
                case PLATFORM_LINE_BAND, PLATFORM_TERMINAL_STRIP -> type;
                default -> ProjectionComponentType.PLATFORM_LINE_CURRENT;
            };
        }
    }

    record PlatformLineIcon(IconShape shape, boolean outline, boolean useLineColor, int fillColor, int borderColor, int textColor, float iconSize, float fontSize, float borderWidth, float ringThicknessRatio, boolean showLabel) implements ProjectionComponentSettings {

        public static final Codec<PlatformLineIcon> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                IconShape.CODEC.optionalFieldOf("shape", IconShape.CIRCLE).forGetter(PlatformLineIcon::shape),
                Codec.BOOL.optionalFieldOf("outline", false).forGetter(PlatformLineIcon::outline),
                Codec.BOOL.optionalFieldOf("use_line_color", true).forGetter(PlatformLineIcon::useLineColor),
                Codec.INT.optionalFieldOf("fill", 0xFF1E88E5).forGetter(PlatformLineIcon::fillColor),
                Codec.INT.optionalFieldOf("border", 0x00000000).forGetter(PlatformLineIcon::borderColor),
                Codec.INT.optionalFieldOf("text", 0xFFFFFFFF).forGetter(PlatformLineIcon::textColor),
                Codec.FLOAT.optionalFieldOf("icon_size", 0.16F).forGetter(PlatformLineIcon::iconSize),
                Codec.FLOAT.optionalFieldOf("font_size", 0.060F).forGetter(PlatformLineIcon::fontSize),
                Codec.FLOAT.optionalFieldOf("border_width", 0.0F).forGetter(PlatformLineIcon::borderWidth),
                Codec.FLOAT.optionalFieldOf("ring_thickness", 0.22F).forGetter(PlatformLineIcon::ringThicknessRatio),
                Codec.BOOL.optionalFieldOf("show_label", true).forGetter(PlatformLineIcon::showLabel)).apply(instance, PlatformLineIcon::new));
        public PlatformLineIcon {
            shape = shape == null ? IconShape.CIRCLE : shape;
            iconSize = clamp(iconSize, 0.025F, 1.5F, 0.16F);
            fontSize = clamp(fontSize, 0.005F, 1.0F, 0.060F);
            borderWidth = clamp(borderWidth, 0.0F, 0.50F, 0.0F);
            ringThicknessRatio = clamp(ringThicknessRatio, 0.08F, 0.45F, 0.22F);
        }

        public static PlatformLineIcon defaults() {
            return new PlatformLineIcon(IconShape.CIRCLE, false, true, 0xFF1E88E5, 0x00000000, 0xFFFFFFFF, 0.16F, 0.060F, 0.0F, 0.22F, true);
        }

        @Override
        public ProjectionComponentType type() {
            return ProjectionComponentType.PLATFORM_LINE_ICON;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeEnum(this.shape);
            buffer.writeBoolean(this.outline);
            buffer.writeBoolean(this.useLineColor);
            buffer.writeInt(this.fillColor);
            buffer.writeInt(this.borderColor);
            buffer.writeInt(this.textColor);
            buffer.writeFloat(this.iconSize);
            buffer.writeFloat(this.fontSize);
            buffer.writeFloat(this.borderWidth);
            buffer.writeFloat(this.ringThicknessRatio);
            buffer.writeBoolean(this.showLabel);
        }

        public static PlatformLineIcon decode(RegistryFriendlyByteBuf buffer) {
            return new PlatformLineIcon(buffer.readEnum(IconShape.class), buffer.readBoolean(), buffer.readBoolean(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readBoolean());
        }
    }

    record PlatformTransferList(int maxVisible, FlowDirection flow, RouteOverflowMode overflow, boolean includeOutStation, boolean showStation, boolean showPlatform, int textColor, int plusTextColor, int fillColor, float fontSize, float gap, int rotateIntervalTicks) implements ProjectionComponentSettings {

        public static final Codec<PlatformTransferList> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("max_visible", 4).forGetter(PlatformTransferList::maxVisible),
                FlowDirection.CODEC.optionalFieldOf("flow", FlowDirection.HORIZONTAL).forGetter(PlatformTransferList::flow),
                RouteOverflowMode.CODEC.optionalFieldOf("overflow", RouteOverflowMode.ROTATE).forGetter(PlatformTransferList::overflow),
                Codec.BOOL.optionalFieldOf("include_out_station", true).forGetter(PlatformTransferList::includeOutStation),
                Codec.BOOL.optionalFieldOf("show_station", true).forGetter(PlatformTransferList::showStation),
                Codec.BOOL.optionalFieldOf("show_platform", true).forGetter(PlatformTransferList::showPlatform),
                Codec.INT.optionalFieldOf("text_color", 0xFFFFFFFF).forGetter(PlatformTransferList::textColor),
                Codec.INT.optionalFieldOf("plus_text_color", 0xFFFFFFFF).forGetter(PlatformTransferList::plusTextColor),
                Codec.INT.optionalFieldOf("fill_color", 0xAA101820).forGetter(PlatformTransferList::fillColor),
                Codec.FLOAT.optionalFieldOf("font_size", 0.058F).forGetter(PlatformTransferList::fontSize),
                Codec.FLOAT.optionalFieldOf("gap", 0.020F).forGetter(PlatformTransferList::gap),
                Codec.INT.optionalFieldOf("rotate_interval", 45).forGetter(PlatformTransferList::rotateIntervalTicks)).apply(instance, PlatformTransferList::new));
        public PlatformTransferList {
            maxVisible = clampInt(maxVisible, 1, 16);
            flow = flow == null ? FlowDirection.HORIZONTAL : flow;
            overflow = overflow == null ? RouteOverflowMode.ROTATE : overflow;
            fontSize = clamp(fontSize, 0.01F, 1.0F, 0.058F);
            gap = clamp(gap, 0.0F, 0.50F, 0.020F);
            rotateIntervalTicks = clampInt(rotateIntervalTicks, 10, 400);
        }

        public static PlatformTransferList defaults() {
            return new PlatformTransferList(4, FlowDirection.VERTICAL, RouteOverflowMode.ROTATE, true, true, true, 0xFFFFFFFF, 0xFFFFFFFF, 0xAA101820, 0.058F, 0.020F, 45);
        }

        @Override
        public ProjectionComponentType type() {
            return ProjectionComponentType.PLATFORM_TRANSFER_LIST;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(this.maxVisible);
            buffer.writeEnum(this.flow);
            buffer.writeEnum(this.overflow);
            buffer.writeBoolean(this.includeOutStation);
            buffer.writeBoolean(this.showStation);
            buffer.writeBoolean(this.showPlatform);
            buffer.writeInt(this.textColor);
            buffer.writeInt(this.plusTextColor);
            buffer.writeInt(this.fillColor);
            buffer.writeFloat(this.fontSize);
            buffer.writeFloat(this.gap);
            buffer.writeVarInt(this.rotateIntervalTicks);
        }

        public static PlatformTransferList decode(RegistryFriendlyByteBuf buffer) {
            return new PlatformTransferList(buffer.readVarInt(), buffer.readEnum(FlowDirection.class), buffer.readEnum(RouteOverflowMode.class), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readFloat(), buffer.readFloat(), buffer.readVarInt());
        }
    }

    record PlatformTransferMatrix(int columns, int maxVisible, RouteOverflowMode overflow, boolean includeOutStation, boolean showStation, boolean showPlatform, int textColor, int plusTextColor, int fillColor, float fontSize, float gap, int rotateIntervalTicks) implements ProjectionComponentSettings {

        public static final Codec<PlatformTransferMatrix> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("columns", 2).forGetter(PlatformTransferMatrix::columns),
                Codec.INT.optionalFieldOf("max_visible", 6).forGetter(PlatformTransferMatrix::maxVisible),
                RouteOverflowMode.CODEC.optionalFieldOf("overflow", RouteOverflowMode.ROTATE).forGetter(PlatformTransferMatrix::overflow),
                Codec.BOOL.optionalFieldOf("include_out_station", true).forGetter(PlatformTransferMatrix::includeOutStation),
                Codec.BOOL.optionalFieldOf("show_station", true).forGetter(PlatformTransferMatrix::showStation),
                Codec.BOOL.optionalFieldOf("show_platform", true).forGetter(PlatformTransferMatrix::showPlatform),
                Codec.INT.optionalFieldOf("text_color", 0xFFFFFFFF).forGetter(PlatformTransferMatrix::textColor),
                Codec.INT.optionalFieldOf("plus_text_color", 0xFFFFFFFF).forGetter(PlatformTransferMatrix::plusTextColor),
                Codec.INT.optionalFieldOf("fill_color", 0xAA101820).forGetter(PlatformTransferMatrix::fillColor),
                Codec.FLOAT.optionalFieldOf("font_size", 0.050F).forGetter(PlatformTransferMatrix::fontSize),
                Codec.FLOAT.optionalFieldOf("gap", 0.016F).forGetter(PlatformTransferMatrix::gap),
                Codec.INT.optionalFieldOf("rotate_interval", 45).forGetter(PlatformTransferMatrix::rotateIntervalTicks)).apply(instance, PlatformTransferMatrix::new));
        public PlatformTransferMatrix {
            columns = clampInt(columns, 1, 6);
            maxVisible = clampInt(maxVisible, 1, 24);
            overflow = overflow == null ? RouteOverflowMode.ROTATE : overflow;
            fontSize = clamp(fontSize, 0.01F, 1.0F, 0.050F);
            gap = clamp(gap, 0.0F, 0.50F, 0.016F);
            rotateIntervalTicks = clampInt(rotateIntervalTicks, 10, 400);
        }

        public static PlatformTransferMatrix defaults() {
            return new PlatformTransferMatrix(2, 6, RouteOverflowMode.ROTATE, true, true, true, 0xFFFFFFFF, 0xFFFFFFFF, 0xAA101820, 0.050F, 0.016F, 45);
        }

        @Override
        public ProjectionComponentType type() {
            return ProjectionComponentType.PLATFORM_TRANSFER_MATRIX;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(this.columns);
            buffer.writeVarInt(this.maxVisible);
            buffer.writeEnum(this.overflow);
            buffer.writeBoolean(this.includeOutStation);
            buffer.writeBoolean(this.showStation);
            buffer.writeBoolean(this.showPlatform);
            buffer.writeInt(this.textColor);
            buffer.writeInt(this.plusTextColor);
            buffer.writeInt(this.fillColor);
            buffer.writeFloat(this.fontSize);
            buffer.writeFloat(this.gap);
            buffer.writeVarInt(this.rotateIntervalTicks);
        }

        public static PlatformTransferMatrix decode(RegistryFriendlyByteBuf buffer) {
            return new PlatformTransferMatrix(buffer.readVarInt(), buffer.readVarInt(), buffer.readEnum(RouteOverflowMode.class), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readFloat(), buffer.readFloat(), buffer.readVarInt());
        }
    }

    record PlatformLayoutMap(
            ProjectionComponentType type,
            PlatformLayoutMapStyle style,
            boolean showStopNames,
            boolean showTransferMarks,
            boolean showTerminalLabels,
            boolean followProjectionDirection,
            PlatformNodeStyle nodeStyle,
            int textColor,
            int lineColor,
            float fontSize,
            float lineWidth,
            float nodeSize,
            ProjectionOverflowMode labelOverflow) implements ProjectionComponentSettings {
        public static Codec<PlatformLayoutMap> codec(ProjectionComponentType type) {
            return RecordCodecBuilder.create(instance -> instance.group(
                    PlatformLayoutMapStyle.CODEC.optionalFieldOf("style", styleFor(type)).forGetter(PlatformLayoutMap::style),
                    Codec.BOOL.optionalFieldOf("show_stop_names", showStopNamesFor(type)).forGetter(PlatformLayoutMap::showStopNames),
                    Codec.BOOL.optionalFieldOf("show_transfer_marks", true).forGetter(PlatformLayoutMap::showTransferMarks),
                    Codec.BOOL.optionalFieldOf("show_terminal_labels", showTerminalLabelsFor(type)).forGetter(PlatformLayoutMap::showTerminalLabels),
                    Codec.BOOL.optionalFieldOf("follow_projection_direction", true).forGetter(PlatformLayoutMap::followProjectionDirection),
                    PlatformNodeStyle.CODEC.optionalFieldOf("node_style", PlatformNodeStyle.SOLID).forGetter(PlatformLayoutMap::nodeStyle),
                    Codec.INT.optionalFieldOf("text_color", textColorFor(type)).forGetter(PlatformLayoutMap::textColor),
                    Codec.INT.optionalFieldOf("line_color", 0xFFFFFFFF).forGetter(PlatformLayoutMap::lineColor),
                    Codec.FLOAT.optionalFieldOf("font_size", fontSizeFor(type)).forGetter(PlatformLayoutMap::fontSize),
                    Codec.FLOAT.optionalFieldOf("line_width", lineWidthFor(type)).forGetter(PlatformLayoutMap::lineWidth),
                    Codec.FLOAT.optionalFieldOf("node_size", nodeSizeFor(type)).forGetter(PlatformLayoutMap::nodeSize),
                    ProjectionOverflowMode.CODEC.optionalFieldOf("label_overflow", ProjectionOverflowMode.MARQUEE).forGetter(PlatformLayoutMap::labelOverflow)).apply(instance, (style, showStopNames, showTransferMarks, showTerminalLabels, followProjectionDirection, nodeStyle, textColor, lineColor, fontSize, lineWidth, nodeSize, labelOverflow) -> new PlatformLayoutMap(type, style, showStopNames, showTransferMarks, showTerminalLabels, followProjectionDirection, nodeStyle, textColor, lineColor, fontSize, lineWidth, nodeSize, labelOverflow)));
        }

        public PlatformLayoutMap {
            type = platformLayoutType(type);
            style = style == null ? styleFor(type) : style;
            nodeStyle = nodeStyle == null ? PlatformNodeStyle.SOLID : nodeStyle;
            fontSize = clamp(fontSize, 0.006F, 2.0F, fontSizeFor(type));
            lineWidth = clamp(lineWidth, 0.003F, 1.0F, lineWidthFor(type));
            nodeSize = clamp(nodeSize, 0.006F, 1.5F, nodeSizeFor(type));
            labelOverflow = labelOverflow == null ? ProjectionOverflowMode.MARQUEE : labelOverflow;
        }

        public static PlatformLayoutMap stopListDefaults() {
            return defaults(ProjectionComponentType.PLATFORM_LAYOUT_STOP_LIST);
        }

        public static PlatformLayoutMap physicalMapDefaults() {
            return defaults(ProjectionComponentType.PLATFORM_LAYOUT_PHYSICAL_MAP);
        }

        public static PlatformLayoutMap practicalMapDefaults() {
            return defaults(ProjectionComponentType.PLATFORM_LAYOUT_PRACTICAL_MAP);
        }

        public static PlatformLayoutMap schematicMapDefaults() {
            return defaults(ProjectionComponentType.PLATFORM_LAYOUT_SCHEMATIC_MAP);
        }

        public static PlatformLayoutMap editorMapDefaults() {
            return defaults(ProjectionComponentType.PLATFORM_LAYOUT_EDITOR_MAP);
        }

        private static PlatformLayoutMap defaults(ProjectionComponentType type) {
            return new PlatformLayoutMap(type, styleFor(type), showStopNamesFor(type), true, showTerminalLabelsFor(type), true, PlatformNodeStyle.SOLID, textColorFor(type), 0xFFFFFFFF, fontSizeFor(type), lineWidthFor(type), nodeSizeFor(type), ProjectionOverflowMode.MARQUEE);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeEnum(this.style);
            buffer.writeBoolean(this.showStopNames);
            buffer.writeBoolean(this.showTransferMarks);
            buffer.writeBoolean(this.showTerminalLabels);
            buffer.writeBoolean(this.followProjectionDirection);
            buffer.writeEnum(this.nodeStyle);
            buffer.writeInt(this.textColor);
            buffer.writeInt(this.lineColor);
            buffer.writeFloat(this.fontSize);
            buffer.writeFloat(this.lineWidth);
            buffer.writeFloat(this.nodeSize);
            ProjectionOverflowMode.STREAM_CODEC.encode(buffer, this.labelOverflow);
        }

        public static PlatformLayoutMap decode(RegistryFriendlyByteBuf buffer, ProjectionComponentType type) {
            return new PlatformLayoutMap(type, buffer.readEnum(PlatformLayoutMapStyle.class), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readEnum(PlatformNodeStyle.class), buffer.readInt(), buffer.readInt(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), ProjectionOverflowMode.STREAM_CODEC.decode(buffer));
        }

        public PlatformLayoutMap withShowStopNames(boolean showStopNames) {
            return new PlatformLayoutMap(this.type, this.style, showStopNames, this.showTransferMarks, this.showTerminalLabels, this.followProjectionDirection, this.nodeStyle, this.textColor, this.lineColor, this.fontSize, this.lineWidth, this.nodeSize, this.labelOverflow);
        }

        public PlatformLayoutMap withShowTransferMarks(boolean showTransferMarks) {
            return new PlatformLayoutMap(this.type, this.style, this.showStopNames, showTransferMarks, this.showTerminalLabels, this.followProjectionDirection, this.nodeStyle, this.textColor, this.lineColor, this.fontSize, this.lineWidth, this.nodeSize, this.labelOverflow);
        }

        public PlatformLayoutMap withShowTerminalLabels(boolean showTerminalLabels) {
            return new PlatformLayoutMap(this.type, this.style, this.showStopNames, this.showTransferMarks, showTerminalLabels, this.followProjectionDirection, this.nodeStyle, this.textColor, this.lineColor, this.fontSize, this.lineWidth, this.nodeSize, this.labelOverflow);
        }

        public PlatformLayoutMap withFollowProjectionDirection(boolean followProjectionDirection) {
            return new PlatformLayoutMap(this.type, this.style, this.showStopNames, this.showTransferMarks, this.showTerminalLabels, followProjectionDirection, this.nodeStyle, this.textColor, this.lineColor, this.fontSize, this.lineWidth, this.nodeSize, this.labelOverflow);
        }

        public PlatformLayoutMap withNodeStyle(PlatformNodeStyle nodeStyle) {
            return new PlatformLayoutMap(this.type, this.style, this.showStopNames, this.showTransferMarks, this.showTerminalLabels, this.followProjectionDirection, nodeStyle, this.textColor, this.lineColor, this.fontSize, this.lineWidth, this.nodeSize, this.labelOverflow);
        }

        public PlatformLayoutMap withTextColor(int textColor) {
            return new PlatformLayoutMap(this.type, this.style, this.showStopNames, this.showTransferMarks, this.showTerminalLabels, this.followProjectionDirection, this.nodeStyle, textColor, this.lineColor, this.fontSize, this.lineWidth, this.nodeSize, this.labelOverflow);
        }

        public PlatformLayoutMap withLineColor(int lineColor) {
            return new PlatformLayoutMap(this.type, this.style, this.showStopNames, this.showTransferMarks, this.showTerminalLabels, this.followProjectionDirection, this.nodeStyle, this.textColor, lineColor, this.fontSize, this.lineWidth, this.nodeSize, this.labelOverflow);
        }

        public PlatformLayoutMap withFontSize(float fontSize) {
            return new PlatformLayoutMap(this.type, this.style, this.showStopNames, this.showTransferMarks, this.showTerminalLabels, this.followProjectionDirection, this.nodeStyle, this.textColor, this.lineColor, fontSize, this.lineWidth, this.nodeSize, this.labelOverflow);
        }

        public PlatformLayoutMap withLineWidth(float lineWidth) {
            return new PlatformLayoutMap(this.type, this.style, this.showStopNames, this.showTransferMarks, this.showTerminalLabels, this.followProjectionDirection, this.nodeStyle, this.textColor, this.lineColor, this.fontSize, lineWidth, this.nodeSize, this.labelOverflow);
        }

        public PlatformLayoutMap withNodeSize(float nodeSize) {
            return new PlatformLayoutMap(this.type, this.style, this.showStopNames, this.showTransferMarks, this.showTerminalLabels, this.followProjectionDirection, this.nodeStyle, this.textColor, this.lineColor, this.fontSize, this.lineWidth, nodeSize, this.labelOverflow);
        }

        public PlatformLayoutMap withLabelOverflow(ProjectionOverflowMode labelOverflow) {
            return new PlatformLayoutMap(this.type, this.style, this.showStopNames, this.showTransferMarks, this.showTerminalLabels, this.followProjectionDirection, this.nodeStyle, this.textColor, this.lineColor, this.fontSize, this.lineWidth, this.nodeSize, labelOverflow);
        }

        private static PlatformLayoutMapStyle styleFor(ProjectionComponentType type) {
            return switch (type) {
                case PLATFORM_LAYOUT_STOP_LIST -> PlatformLayoutMapStyle.STOP_LIST;
                case PLATFORM_LAYOUT_PHYSICAL_MAP -> PlatformLayoutMapStyle.PHYSICAL;
                case PLATFORM_LAYOUT_PRACTICAL_MAP -> PlatformLayoutMapStyle.PRACTICAL;
                case PLATFORM_LAYOUT_EDITOR_MAP -> PlatformLayoutMapStyle.EDITOR;
                default -> PlatformLayoutMapStyle.SCHEMATIC;
            };
        }

        private static boolean showStopNamesFor(ProjectionComponentType type) {
            return true;
        }

        private static boolean showTerminalLabelsFor(ProjectionComponentType type) {
            return true;
        }

        private static int textColorFor(ProjectionComponentType type) {
            return switch (type) {
                case PLATFORM_LAYOUT_STOP_LIST -> 0xFFFFFFFF;
                case PLATFORM_LAYOUT_PHYSICAL_MAP, PLATFORM_LAYOUT_PRACTICAL_MAP, PLATFORM_LAYOUT_SCHEMATIC_MAP -> 0xFF1E2A30;
                default -> 0xFFFFFFFF;
            };
        }

        private static float fontSizeFor(ProjectionComponentType type) {
            return switch (type) {
                case PLATFORM_LAYOUT_STOP_LIST -> 0.050F;
                case PLATFORM_LAYOUT_EDITOR_MAP -> 0.040F;
                default -> 0.044F;
            };
        }

        private static float lineWidthFor(ProjectionComponentType type) {
            return switch (type) {
                case PLATFORM_LAYOUT_STOP_LIST -> 0.018F;
                case PLATFORM_LAYOUT_EDITOR_MAP -> 0.024F;
                default -> 0.030F;
            };
        }

        private static float nodeSizeFor(ProjectionComponentType type) {
            return switch (type) {
                case PLATFORM_LAYOUT_STOP_LIST -> 0.046F;
                case PLATFORM_LAYOUT_EDITOR_MAP -> 0.056F;
                default -> 0.060F;
            };
        }

        private static ProjectionComponentType platformLayoutType(ProjectionComponentType type) {
            return switch (type) {
                case PLATFORM_LAYOUT_STOP_LIST, PLATFORM_LAYOUT_PHYSICAL_MAP, PLATFORM_LAYOUT_PRACTICAL_MAP, PLATFORM_LAYOUT_SCHEMATIC_MAP, PLATFORM_LAYOUT_EDITOR_MAP -> type;
                default -> ProjectionComponentType.PLATFORM_LAYOUT_SCHEMATIC_MAP;
            };
        }
    }

    record BuiltinIcon(
            String iconId,
            IconTintMode tintMode,
            int tintColor,
            int secondaryColor,
            float opacity,
            ImageFitMode fitMode,
            ImageAnchor anchor,
            float imageScale,
            float tileGap,
            float padding,
            boolean backgroundEnabled,
            int backgroundColor,
            boolean borderEnabled,
            int borderColor,
            float borderWidth) implements ProjectionComponentSettings {

        public static final Codec<BuiltinIcon> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("icon", "pipe.platform").forGetter(BuiltinIcon::iconId),
                IconTintMode.CODEC.optionalFieldOf("tint_mode", IconTintMode.ORIGINAL).forGetter(BuiltinIcon::tintMode),
                Codec.INT.optionalFieldOf("tint", 0xFFFFFFFF).forGetter(BuiltinIcon::tintColor),
                Codec.INT.optionalFieldOf("secondary", 0xFF37C3BB).forGetter(BuiltinIcon::secondaryColor),
                Codec.FLOAT.optionalFieldOf("opacity", 1.0F).forGetter(BuiltinIcon::opacity),
                ImageFitMode.CODEC.optionalFieldOf("fit", ImageFitMode.CONTAIN).forGetter(BuiltinIcon::fitMode),
                ImageAnchor.CODEC.optionalFieldOf("anchor", ImageAnchor.CENTER).forGetter(BuiltinIcon::anchor),
                Codec.FLOAT.optionalFieldOf("image_scale", 0.62F).forGetter(BuiltinIcon::imageScale),
                Codec.FLOAT.optionalFieldOf("tile_gap", 0.02F).forGetter(BuiltinIcon::tileGap),
                Codec.FLOAT.optionalFieldOf("padding", 0.02F).forGetter(BuiltinIcon::padding),
                Codec.BOOL.optionalFieldOf("background_enabled", false).forGetter(BuiltinIcon::backgroundEnabled),
                Codec.INT.optionalFieldOf("background", 0x66000000).forGetter(BuiltinIcon::backgroundColor),
                Codec.BOOL.optionalFieldOf("border_enabled", false).forGetter(BuiltinIcon::borderEnabled),
                Codec.INT.optionalFieldOf("border", 0xFFFFFFFF).forGetter(BuiltinIcon::borderColor),
                Codec.FLOAT.optionalFieldOf("border_width", 0.01F).forGetter(BuiltinIcon::borderWidth)).apply(instance, BuiltinIcon::new));
        public BuiltinIcon {
            iconId = trim(iconId, MAX_ICON_ID_LENGTH);
            if (iconId.isBlank()) {
                iconId = "pipe.platform";
            }
            tintMode = tintMode == null ? IconTintMode.ORIGINAL : tintMode;
            opacity = clamp(opacity, 0.0F, 1.0F, 1.0F);
            fitMode = fitMode == null ? ImageFitMode.CONTAIN : fitMode;
            if (fitMode == ImageFitMode.COVER) {
                fitMode = ImageFitMode.STRETCH;
            }
            anchor = anchor == null ? ImageAnchor.CENTER : anchor;
            imageScale = clamp(imageScale, 0.08F, 3.0F, 0.62F);
            tileGap = clamp(tileGap, 0.0F, 0.50F, 0.02F);
            padding = clamp(padding, 0.0F, 0.50F, 0.02F);
            borderWidth = clamp(borderWidth, 0.0F, 0.50F, 0.01F);
        }

        public static BuiltinIcon defaults() {
            return new BuiltinIcon("pipe.platform", IconTintMode.ORIGINAL, 0xFFFFFFFF, 0xFF37C3BB, 1.0F, ImageFitMode.CONTAIN, ImageAnchor.CENTER, 0.62F, 0.02F, 0.02F, false, 0x66000000, false, 0xFFFFFFFF, 0.01F);
        }

        @Override
        public ProjectionComponentType type() {
            return ProjectionComponentType.BUILTIN_ICON;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            ByteBufCodecs.STRING_UTF8.encode(buffer, this.iconId);
            buffer.writeEnum(this.tintMode);
            buffer.writeInt(this.tintColor);
            buffer.writeInt(this.secondaryColor);
            buffer.writeFloat(this.opacity);
            buffer.writeEnum(this.fitMode);
            buffer.writeEnum(this.anchor);
            buffer.writeFloat(this.imageScale);
            buffer.writeFloat(this.tileGap);
            buffer.writeFloat(this.padding);
            buffer.writeBoolean(this.backgroundEnabled);
            buffer.writeInt(this.backgroundColor);
            buffer.writeBoolean(this.borderEnabled);
            buffer.writeInt(this.borderColor);
            buffer.writeFloat(this.borderWidth);
        }

        public static BuiltinIcon decode(RegistryFriendlyByteBuf buffer) {
            return new BuiltinIcon(ByteBufCodecs.STRING_UTF8.decode(buffer), buffer.readEnum(IconTintMode.class), buffer.readInt(), buffer.readInt(), buffer.readFloat(), buffer.readEnum(ImageFitMode.class), buffer.readEnum(ImageAnchor.class), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readBoolean(), buffer.readInt(), buffer.readBoolean(), buffer.readInt(), buffer.readFloat());
        }

        public BuiltinIcon withIconId(String iconId) {
            return new BuiltinIcon(iconId, this.tintMode, this.tintColor, this.secondaryColor, this.opacity, this.fitMode, this.anchor, this.imageScale, this.tileGap, this.padding, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth);
        }

        public BuiltinIcon withTintMode(IconTintMode tintMode) {
            return new BuiltinIcon(this.iconId, tintMode, this.tintColor, this.secondaryColor, this.opacity, this.fitMode, this.anchor, this.imageScale, this.tileGap, this.padding, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth);
        }

        public BuiltinIcon withTintColor(int tintColor) {
            return new BuiltinIcon(this.iconId, this.tintMode, tintColor, this.secondaryColor, this.opacity, this.fitMode, this.anchor, this.imageScale, this.tileGap, this.padding, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth);
        }

        public BuiltinIcon withSecondaryColor(int secondaryColor) {
            return new BuiltinIcon(this.iconId, this.tintMode, this.tintColor, secondaryColor, this.opacity, this.fitMode, this.anchor, this.imageScale, this.tileGap, this.padding, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth);
        }

        public BuiltinIcon withOpacity(float opacity) {
            return new BuiltinIcon(this.iconId, this.tintMode, this.tintColor, this.secondaryColor, opacity, this.fitMode, this.anchor, this.imageScale, this.tileGap, this.padding, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth);
        }

        public BuiltinIcon withFitMode(ImageFitMode fitMode) {
            return new BuiltinIcon(this.iconId, this.tintMode, this.tintColor, this.secondaryColor, this.opacity, fitMode, this.anchor, this.imageScale, this.tileGap, this.padding, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth);
        }

        public BuiltinIcon withAnchor(ImageAnchor anchor) {
            return new BuiltinIcon(this.iconId, this.tintMode, this.tintColor, this.secondaryColor, this.opacity, this.fitMode, anchor, this.imageScale, this.tileGap, this.padding, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth);
        }

        public BuiltinIcon withImageScale(float imageScale) {
            return new BuiltinIcon(this.iconId, this.tintMode, this.tintColor, this.secondaryColor, this.opacity, this.fitMode, this.anchor, imageScale, this.tileGap, this.padding, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth);
        }

        public BuiltinIcon withTileGap(float tileGap) {
            return new BuiltinIcon(this.iconId, this.tintMode, this.tintColor, this.secondaryColor, this.opacity, this.fitMode, this.anchor, this.imageScale, tileGap, this.padding, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth);
        }

        public BuiltinIcon withPadding(float padding) {
            return new BuiltinIcon(this.iconId, this.tintMode, this.tintColor, this.secondaryColor, this.opacity, this.fitMode, this.anchor, this.imageScale, this.tileGap, padding, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth);
        }

        public BuiltinIcon withBackgroundEnabled(boolean backgroundEnabled) {
            return new BuiltinIcon(this.iconId, this.tintMode, this.tintColor, this.secondaryColor, this.opacity, this.fitMode, this.anchor, this.imageScale, this.tileGap, this.padding, backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth);
        }

        public BuiltinIcon withBackgroundColor(int backgroundColor) {
            return new BuiltinIcon(this.iconId, this.tintMode, this.tintColor, this.secondaryColor, this.opacity, this.fitMode, this.anchor, this.imageScale, this.tileGap, this.padding, this.backgroundEnabled, backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth);
        }

        public BuiltinIcon withBorderEnabled(boolean borderEnabled) {
            return new BuiltinIcon(this.iconId, this.tintMode, this.tintColor, this.secondaryColor, this.opacity, this.fitMode, this.anchor, this.imageScale, this.tileGap, this.padding, this.backgroundEnabled, this.backgroundColor, borderEnabled, this.borderColor, this.borderWidth);
        }

        public BuiltinIcon withBorderColor(int borderColor) {
            return new BuiltinIcon(this.iconId, this.tintMode, this.tintColor, this.secondaryColor, this.opacity, this.fitMode, this.anchor, this.imageScale, this.tileGap, this.padding, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, borderColor, this.borderWidth);
        }

        public BuiltinIcon withBorderWidth(float borderWidth) {
            return new BuiltinIcon(this.iconId, this.tintMode, this.tintColor, this.secondaryColor, this.opacity, this.fitMode, this.anchor, this.imageScale, this.tileGap, this.padding, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, borderWidth);
        }
    }

    record NetworkImage(
            String url,
            ImageFitMode fitMode,
            ImageAnchor anchor,
            float cropX,
            float cropY,
            float cropW,
            float cropH,
            float opacity,
            boolean backgroundEnabled,
            int backgroundColor,
            boolean borderEnabled,
            int borderColor,
            float borderWidth,
            ImageFallbackMode fallbackMode,
            ImageLoadingMode loadingMode) implements ProjectionComponentSettings {

        public static final Codec<NetworkImage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("url", "").forGetter(NetworkImage::url),
                ImageFitMode.CODEC.optionalFieldOf("fit", ImageFitMode.COVER).forGetter(NetworkImage::fitMode),
                ImageAnchor.CODEC.optionalFieldOf("anchor", ImageAnchor.CENTER).forGetter(NetworkImage::anchor),
                Codec.FLOAT.optionalFieldOf("crop_x", 0.0F).forGetter(NetworkImage::cropX),
                Codec.FLOAT.optionalFieldOf("crop_y", 0.0F).forGetter(NetworkImage::cropY),
                Codec.FLOAT.optionalFieldOf("crop_w", 1.0F).forGetter(NetworkImage::cropW),
                Codec.FLOAT.optionalFieldOf("crop_h", 1.0F).forGetter(NetworkImage::cropH),
                Codec.FLOAT.optionalFieldOf("opacity", 1.0F).forGetter(NetworkImage::opacity),
                Codec.BOOL.optionalFieldOf("background_enabled", true).forGetter(NetworkImage::backgroundEnabled),
                Codec.INT.optionalFieldOf("background", 0x6610151A).forGetter(NetworkImage::backgroundColor),
                Codec.BOOL.optionalFieldOf("border_enabled", false).forGetter(NetworkImage::borderEnabled),
                Codec.INT.optionalFieldOf("border", 0xFFFFFFFF).forGetter(NetworkImage::borderColor),
                Codec.FLOAT.optionalFieldOf("border_width", 0.01F).forGetter(NetworkImage::borderWidth),
                ImageFallbackMode.CODEC.optionalFieldOf("fallback", ImageFallbackMode.PLACEHOLDER).forGetter(NetworkImage::fallbackMode),
                ImageLoadingMode.CODEC.optionalFieldOf("loading", ImageLoadingMode.SUBTLE).forGetter(NetworkImage::loadingMode)).apply(instance, NetworkImage::new));
        public NetworkImage {
            url = trim(url, MAX_URL_LENGTH);
            fitMode = fitMode == null ? ImageFitMode.COVER : fitMode;
            anchor = anchor == null ? ImageAnchor.CENTER : anchor;
            cropX = clamp(cropX, 0.0F, 0.99F, 0.0F);
            cropY = clamp(cropY, 0.0F, 0.99F, 0.0F);
            cropW = clamp(cropW, 0.01F, 1.0F - cropX, 1.0F);
            cropH = clamp(cropH, 0.01F, 1.0F - cropY, 1.0F);
            opacity = clamp(opacity, 0.0F, 1.0F, 1.0F);
            borderWidth = clamp(borderWidth, 0.0F, 0.50F, 0.01F);
            fallbackMode = fallbackMode == null ? ImageFallbackMode.PLACEHOLDER : fallbackMode;
            loadingMode = loadingMode == null ? ImageLoadingMode.SUBTLE : loadingMode;
        }

        public static NetworkImage defaults() {
            return new NetworkImage("", ImageFitMode.COVER, ImageAnchor.CENTER, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, true, 0x6610151A, false, 0xFFFFFFFF, 0.01F, ImageFallbackMode.PLACEHOLDER, ImageLoadingMode.SUBTLE);
        }

        @Override
        public ProjectionComponentType type() {
            return ProjectionComponentType.NETWORK_IMAGE;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer) {
            ByteBufCodecs.STRING_UTF8.encode(buffer, this.url);
            buffer.writeEnum(this.fitMode);
            buffer.writeEnum(this.anchor);
            buffer.writeFloat(this.cropX);
            buffer.writeFloat(this.cropY);
            buffer.writeFloat(this.cropW);
            buffer.writeFloat(this.cropH);
            buffer.writeFloat(this.opacity);
            buffer.writeBoolean(this.backgroundEnabled);
            buffer.writeInt(this.backgroundColor);
            buffer.writeBoolean(this.borderEnabled);
            buffer.writeInt(this.borderColor);
            buffer.writeFloat(this.borderWidth);
            buffer.writeEnum(this.fallbackMode);
            buffer.writeEnum(this.loadingMode);
        }

        public static NetworkImage decode(RegistryFriendlyByteBuf buffer) {
            return new NetworkImage(ByteBufCodecs.STRING_UTF8.decode(buffer), buffer.readEnum(ImageFitMode.class), buffer.readEnum(ImageAnchor.class), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readBoolean(), buffer.readInt(), buffer.readBoolean(), buffer.readInt(), buffer.readFloat(), buffer.readEnum(ImageFallbackMode.class), buffer.readEnum(ImageLoadingMode.class));
        }

        public NetworkImage withUrl(String url) {
            return new NetworkImage(url, this.fitMode, this.anchor, this.cropX, this.cropY, this.cropW, this.cropH, this.opacity, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth, this.fallbackMode, this.loadingMode);
        }

        public NetworkImage withFitMode(ImageFitMode fitMode) {
            return new NetworkImage(this.url, fitMode, this.anchor, this.cropX, this.cropY, this.cropW, this.cropH, this.opacity, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth, this.fallbackMode, this.loadingMode);
        }

        public NetworkImage withAnchor(ImageAnchor anchor) {
            return new NetworkImage(this.url, this.fitMode, anchor, this.cropX, this.cropY, this.cropW, this.cropH, this.opacity, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth, this.fallbackMode, this.loadingMode);
        }

        public NetworkImage withCrop(float cropX, float cropY, float cropW, float cropH) {
            return new NetworkImage(this.url, this.fitMode, this.anchor, cropX, cropY, cropW, cropH, this.opacity, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth, this.fallbackMode, this.loadingMode);
        }

        public NetworkImage withOpacity(float opacity) {
            return new NetworkImage(this.url, this.fitMode, this.anchor, this.cropX, this.cropY, this.cropW, this.cropH, opacity, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth, this.fallbackMode, this.loadingMode);
        }

        public NetworkImage withBackgroundEnabled(boolean backgroundEnabled) {
            return new NetworkImage(this.url, this.fitMode, this.anchor, this.cropX, this.cropY, this.cropW, this.cropH, this.opacity, backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth, this.fallbackMode, this.loadingMode);
        }

        public NetworkImage withBackgroundColor(int backgroundColor) {
            return new NetworkImage(this.url, this.fitMode, this.anchor, this.cropX, this.cropY, this.cropW, this.cropH, this.opacity, this.backgroundEnabled, backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth, this.fallbackMode, this.loadingMode);
        }

        public NetworkImage withBorderEnabled(boolean borderEnabled) {
            return new NetworkImage(this.url, this.fitMode, this.anchor, this.cropX, this.cropY, this.cropW, this.cropH, this.opacity, this.backgroundEnabled, this.backgroundColor, borderEnabled, this.borderColor, this.borderWidth, this.fallbackMode, this.loadingMode);
        }

        public NetworkImage withBorderColor(int borderColor) {
            return new NetworkImage(this.url, this.fitMode, this.anchor, this.cropX, this.cropY, this.cropW, this.cropH, this.opacity, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, borderColor, this.borderWidth, this.fallbackMode, this.loadingMode);
        }

        public NetworkImage withBorderWidth(float borderWidth) {
            return new NetworkImage(this.url, this.fitMode, this.anchor, this.cropX, this.cropY, this.cropW, this.cropH, this.opacity, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, borderWidth, this.fallbackMode, this.loadingMode);
        }

        public NetworkImage withFallbackMode(ImageFallbackMode fallbackMode) {
            return new NetworkImage(this.url, this.fitMode, this.anchor, this.cropX, this.cropY, this.cropW, this.cropH, this.opacity, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth, fallbackMode, this.loadingMode);
        }

        public NetworkImage withLoadingMode(ImageLoadingMode loadingMode) {
            return new NetworkImage(this.url, this.fitMode, this.anchor, this.cropX, this.cropY, this.cropW, this.cropH, this.opacity, this.backgroundEnabled, this.backgroundColor, this.borderEnabled, this.borderColor, this.borderWidth, this.fallbackMode, loadingMode);
        }
    }
}
