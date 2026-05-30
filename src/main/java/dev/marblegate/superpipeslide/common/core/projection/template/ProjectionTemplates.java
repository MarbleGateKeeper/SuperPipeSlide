package dev.marblegate.superpipeslide.common.core.projection.template;

import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponent;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponentSettings;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponentType;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionOverflowMode;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionTextAlign;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionVisibleCondition;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionCanvas;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutDefinition;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutTarget;
import java.util.ArrayList;
import java.util.List;

public final class ProjectionTemplates {
    private static final PlatformTemplateTheme PLATFORM_DARK = new PlatformTemplateTheme(0xFFFFFFFF, 0xFFBFD4DC, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xAA101820, 0xFFFFFFFF);
    private static final PlatformTemplateTheme PLATFORM_LIGHT = new PlatformTemplateTheme(0xFF17242A, 0xFF53626A, 0xFF17242A, 0xFF17242A, 0xFF1E2A30, 0xEAF5F7F8, 0xFF17242A);
    private static final PlatformTemplateTheme PLATFORM_NETWORK = new PlatformTemplateTheme(0xFFD8FFF2, 0xFF8CB8AB, 0xFFD8FFF2, 0xFFD8FFF2, 0xFFD8FFF2, 0x66101820, 0xFFD8FFF2);

    private ProjectionTemplates() {}

    public static ProjectionLayoutDefinition defaultLayout() {
        return urbanBeam();
    }

    public static ProjectionLayoutDefinition defaultLayout(ProjectionLayoutTarget target) {
        return target == ProjectionLayoutTarget.PLATFORM ? platformBeam() : urbanBeam();
    }

    public static List<ProjectionLayoutDefinition> defaultLibrary() {
        return List.of(
                urbanBeam(),
                splitLightbox(),
                circleMarker(),
                blackTransfer(),
                verticalPylon(),
                verticalMetroPylon(),
                minimalStrip(),
                blockProjection());
    }

    public static List<ProjectionLayoutDefinition> defaultLibrary(ProjectionLayoutTarget target) {
        return target == ProjectionLayoutTarget.PLATFORM ? platformLibrary() : defaultLibrary();
    }

    public static List<ProjectionLayoutDefinition> platformLibrary() {
        return List.of(
                platformBeam(),
                platformDirectionBoard(),
                platformLayoutWall(),
                platformVerticalPylon(),
                platformDoorStripWide(),
                platformMultiLayoutWall(),
                platformCompactNeighbors(),
                platformBidirectionalBoard(),
                platformLoopBoard(),
                platformTransferBoard(),
                platformMinimalProjection());
    }

    public static ProjectionLayoutDefinition urbanBeam() {
        List<ProjectionComponent> c = new ArrayList<>();
        c.add(panel(0.0F, 0.0F, 3.2F, 0.9F, 0, 0xFF27313A, 0xFF4E5D66));
        c.add(routeList(0.14F, 0.18F, 0.50F, 0.54F, 5, 0xFFFFFFFF));
        c.add(titleGroup(0.72F, 0.14F, 1.54F, 0.62F, 10, 0xFFFFFFFF, 0xFFBFD4DC, 0.25F, 0.09F, ProjectionTextAlign.CENTER));
        c.add(exit(2.43F, 0.17F, 0.60F, 0.56F, 12, 0xFFFFD34D, 0xFF202018));
        return ProjectionLayoutDefinition.create("都市横梁", new ProjectionCanvas(3.2F, 0.9F), c);
    }

    public static ProjectionLayoutDefinition splitLightbox() {
        List<ProjectionComponent> c = new ArrayList<>();
        c.add(panel(0.0F, 0.0F, 2.75F, 0.95F, 0, 0xFFF7F4EC, 0xFFCAD2D6));
        c.add(backplate(0.0F, 0.0F, 0.82F, 0.95F, 4));
        c.add(titleGroup(0.94F, 0.13F, 1.22F, 0.62F, 10, 0xFF17242A, 0xFF59676E, 0.23F, 0.082F, ProjectionTextAlign.LEFT));
        c.add(exit(2.25F, 0.08F, 0.36F, 0.27F, 12, 0xFF27313A, 0xFFFFFFFF));
        return ProjectionLayoutDefinition.create("分区灯箱", new ProjectionCanvas(2.75F, 0.95F), c);
    }

    public static ProjectionLayoutDefinition circleMarker() {
        List<ProjectionComponent> c = new ArrayList<>();
        c.add(panel(0.0F, 0.0F, 2.3F, 0.8F, 0, 0xFFF8F8F2, 0xFFBDC8CC));
        c.add(routeIcons(0.14F, 0.14F, 0.44F, 0.52F, 5, 0.13F, ProjectionComponentSettings.FlowDirection.VERTICAL, false, true, 2));
        c.add(titleGroup(0.68F, 0.14F, 1.36F, 0.56F, 10, 0xFF17242A, 0xFF5B6970, 0.20F, 0.075F, ProjectionTextAlign.LEFT));
        return ProjectionLayoutDefinition.create("圆标站牌", new ProjectionCanvas(2.3F, 0.8F), c);
    }

    public static ProjectionLayoutDefinition blackTransfer() {
        List<ProjectionComponent> c = new ArrayList<>();
        c.add(panel(0.0F, 0.0F, 3.0F, 1.05F, 0, 0xFF0A0A0A, 0xFF3A3A3A));
        c.add(titleGroup(0.18F, 0.10F, 2.10F, 0.52F, 10, 0xFFFFFFFF, 0xFFC8C8C8, 0.20F, 0.080F, ProjectionTextAlign.LEFT));
        c.add(routeIcons(0.18F, 0.70F, 1.90F, 0.18F, 12, 0.105F, ProjectionComponentSettings.FlowDirection.HORIZONTAL, true));
        c.add(exit(2.48F, 0.20F, 0.38F, 0.52F, 13, 0x00000000, 0xFFFFFFFF));
        return ProjectionLayoutDefinition.create("大都会", new ProjectionCanvas(3.0F, 1.05F), c);
    }

    public static ProjectionLayoutDefinition verticalPylon() {
        List<ProjectionComponent> c = new ArrayList<>();
        c.add(panel(0.0F, 0.0F, 0.9F, 3.5F, 0, 0xFFF4F2EA, 0xFFCED5D8));
        c.add(backplate(0.0F, 0.0F, 0.9F, 0.42F, 4, ProjectionComponentSettings.StripeDirection.HORIZONTAL));
        c.add(customText(0.16F, 0.55F, 0.58F, 0.22F, 7, "M", 0xFF1D2A32, 0.15F, ProjectionTextAlign.CENTER));
        c.add(text(ProjectionComponentType.STATION_NAME_TEXT, 0.12F, 0.95F, 0.66F, 1.42F, 10, 0xFF1D2A32, 0.17F, ProjectionTextAlign.CENTER, ProjectionComponentSettings.TextOrientation.VERTICAL_STACK));
        c.add(exit(0.18F, 2.85F, 0.54F, 0.34F, 12, 0x00000000, 0xFF7A5A10));
        return ProjectionLayoutDefinition.create("竖向立柱", ProjectionCanvas.verticalPylon(), c);
    }

    public static ProjectionLayoutDefinition verticalMetroPylon() {
        List<ProjectionComponent> c = new ArrayList<>();
        c.add(panel(0.0F, 0.0F, 0.92F, 3.8F, 0, 0xFF0B0C0E, 0xFF3C3F42));
        c.add(backplate(0.0F, 0.0F, 0.92F, 0.32F, 4, ProjectionComponentSettings.StripeDirection.HORIZONTAL));
        c.add(customText(0.25F, 0.42F, 0.42F, 0.20F, 7, "METRO", 0xFFD8DEE5, 0.075F, ProjectionTextAlign.CENTER));
        c.add(text(ProjectionComponentType.STATION_NAME_TEXT, 0.14F, 0.68F, 0.56F, 2.05F, 10, 0xFFFFFFFF, 0.22F, ProjectionTextAlign.CENTER, ProjectionComponentSettings.TextOrientation.ROTATE_CW));
        c.add(text(ProjectionComponentType.TRANSLATION_TEXT, 0.70F, 0.74F, 0.13F, 1.88F, 11, 0xFFBFC7D0, 0.074F, ProjectionTextAlign.CENTER, ProjectionComponentSettings.TextOrientation.ROTATE_CW).withVisibleCondition(ProjectionVisibleCondition.HAS_TRANSLATION));
        c.add(exit(0.20F, 3.12F, 0.52F, 0.36F, 12, 0xFFFFD34D, 0xFF151515));
        return ProjectionLayoutDefinition.create("地铁柱", new ProjectionCanvas(0.92F, 3.8F), c);
    }

    public static ProjectionLayoutDefinition minimalStrip() {
        List<ProjectionComponent> c = new ArrayList<>();
        c.add(panel(0.0F, 0.0F, 2.5F, 0.48F, 0, 0xFF111820, 0xFF2F3C45));
        c.add(backplate(0.0F, 0.0F, 0.18F, 0.48F, 5, ProjectionComponentSettings.StripeDirection.VERTICAL));
        c.add(titleGroup(0.28F, 0.06F, 1.62F, 0.36F, 10, 0xFFFFFFFF, 0xFFB8CAD0, 0.15F, 0.055F, ProjectionTextAlign.LEFT));
        return ProjectionLayoutDefinition.create("极简站名条", new ProjectionCanvas(2.5F, 0.48F), c);
    }

    public static ProjectionLayoutDefinition blockProjection() {
        List<ProjectionComponent> c = new ArrayList<>();
        c.add(panel(0.0F, 0.0F, 2.75F, 0.9F, 0, 0xFF06110E, 0xFF34F0B8));
        c.add(backplate(0.16F, 0.12F, 2.42F, 0.12F, 5, ProjectionComponentSettings.StripeDirection.HORIZONTAL));
        c.add(titleGroup(0.22F, 0.28F, 2.31F, 0.50F, 10, 0xFFD8FFF2, 0xFF8CB8AB, 0.20F, 0.065F, ProjectionTextAlign.CENTER));
        return ProjectionLayoutDefinition.create("网络投影牌", new ProjectionCanvas(2.75F, 0.9F), c);
    }

    public static ProjectionLayoutDefinition platformBeam() {
        List<ProjectionComponent> c = new ArrayList<>();
        c.add(platformPanel(0.0F, 0.0F, 4.8F, 0.9F, 0, 0xFF182126, 0xFF4E5D66));
        c.add(platformLine(ProjectionComponentType.PLATFORM_LINE_BAND, 0.0F, 0.0F, 4.8F, 0.16F, 2, PLATFORM_DARK));
        c.add(platformBadge(0.18F, 0.24F, 0.56F, 0.36F, 8));
        c.add(platformTitle(0.86F, 0.17F, 1.68F, 0.52F, 10, PLATFORM_DARK));
        c.add(platformDirection(2.62F, 0.18F, 1.34F, 0.50F, 12, PLATFORM_DARK));
        c.add(platformTransfer(ProjectionComponentType.PLATFORM_TRANSFER_MATRIX, 3.92F, 0.18F, 0.72F, 0.50F, 14, PLATFORM_DARK));
        return ProjectionLayoutDefinition.create("站台横梁", ProjectionLayoutTarget.PLATFORM, new ProjectionCanvas(4.8F, 0.9F), c);
    }

    public static ProjectionLayoutDefinition platformDirectionBoard() {
        List<ProjectionComponent> c = new ArrayList<>();
        c.add(platformPanel(0.0F, 0.0F, 3.2F, 0.75F, 0, 0xFFF7F4EC, 0xFFCAD2D6));
        c.add(platformLineIcon(0.14F, 0.16F, 0.42F, 0.42F, 8, PLATFORM_LIGHT));
        c.add(platformDirection(0.66F, 0.14F, 2.16F, 0.46F, 10, PLATFORM_LIGHT));
        return ProjectionLayoutDefinition.create("站台方向牌", ProjectionLayoutTarget.PLATFORM, new ProjectionCanvas(3.2F, 0.75F), c);
    }

    public static ProjectionLayoutDefinition platformLayoutWall() {
        List<ProjectionComponent> c = new ArrayList<>();
        c.add(platformPanel(0.0F, 0.0F, 5.8F, 1.35F, 0, 0xFFF4F2EA, 0xFFCED5D8));
        c.add(platformLine(ProjectionComponentType.PLATFORM_LINE_CURRENT, 0.22F, 0.16F, 5.36F, 0.24F, 4, PLATFORM_LIGHT));
        c.add(platformTitle(0.30F, 0.48F, 1.68F, 0.54F, 10, PLATFORM_LIGHT));
        c.add(platformLayoutMap(ProjectionComponentType.PLATFORM_LAYOUT_EDITOR_MAP, 2.10F, 0.34F, 3.42F, 0.70F, 12, PLATFORM_LIGHT));
        return ProjectionLayoutDefinition.create("Layout 线路墙牌", ProjectionLayoutTarget.PLATFORM, new ProjectionCanvas(5.8F, 1.35F), c);
    }

    public static ProjectionLayoutDefinition platformVerticalPylon() {
        List<ProjectionComponent> c = new ArrayList<>();
        c.add(platformPanel(0.0F, 0.0F, 1.05F, 4.2F, 0, 0xFF0B0C0E, 0xFF3C3F42));
        c.add(platformLine(ProjectionComponentType.PLATFORM_LINE_BAND, 0.0F, 0.0F, 1.05F, 0.28F, 2, PLATFORM_DARK));
        c.add(platformBadge(0.24F, 0.46F, 0.57F, 0.34F, 8));
        c.add(platformTitle(0.16F, 0.92F, 0.72F, 1.18F, 10, ProjectionComponentSettings.TextOrientation.ROTATE_CW, PLATFORM_DARK));
        c.add(platformLayoutMap(ProjectionComponentType.PLATFORM_LAYOUT_STOP_LIST, 0.18F, 2.22F, 0.69F, 1.62F, 12, PLATFORM_DARK));
        return ProjectionLayoutDefinition.create("竖向柱牌", ProjectionLayoutTarget.PLATFORM, new ProjectionCanvas(1.05F, 4.2F), c);
    }

    public static ProjectionLayoutDefinition platformTransferBoard() {
        List<ProjectionComponent> c = new ArrayList<>();
        c.add(platformPanel(0.0F, 0.0F, 3.8F, 1.15F, 0, 0xFF10151A, 0xFF4E5D66));
        c.add(platformTitle(0.18F, 0.14F, 1.42F, 0.40F, 10, PLATFORM_DARK));
        c.add(platformLine(ProjectionComponentType.PLATFORM_LINE_CURRENT, 0.18F, 0.66F, 1.42F, 0.24F, 8, PLATFORM_DARK));
        c.add(platformTransfer(ProjectionComponentType.PLATFORM_TRANSFER_LIST, 1.78F, 0.14F, 1.78F, 0.82F, 12, PLATFORM_DARK));
        return ProjectionLayoutDefinition.create("换乘提示牌", ProjectionLayoutTarget.PLATFORM, new ProjectionCanvas(3.8F, 1.15F), c);
    }

    public static ProjectionLayoutDefinition platformDoorStripWide() {
        List<ProjectionComponent> c = new ArrayList<>();
        c.add(platformPanel(0.0F, 0.0F, 6.2F, 1.05F, 0, 0xFFF7F4EC, 0xFFB8C4C8));
        c.add(platformLine(ProjectionComponentType.PLATFORM_LINE_BAND, 0.0F, 0.0F, 6.2F, 0.14F, 2, PLATFORM_LIGHT));
        c.add(platformLayoutMap(ProjectionComponentType.PLATFORM_LAYOUT_EDITOR_MAP, 0.18F, 0.20F, 5.84F, 0.78F, 8, PLATFORM_LIGHT));
        return ProjectionLayoutDefinition.create("站台门线路图", ProjectionLayoutTarget.PLATFORM, new ProjectionCanvas(6.2F, 1.05F), c);
    }

    public static ProjectionLayoutDefinition platformMultiLayoutWall() {
        List<ProjectionComponent> c = new ArrayList<>();
        c.add(platformPanel(0.0F, 0.0F, 5.4F, 1.55F, 0, 0xFF162026, 0xFF4E5D66));
        c.add(platformTitle(0.18F, 0.12F, 1.25F, 0.44F, 8, PLATFORM_DARK));
        c.add(platformLayoutMap(ProjectionComponentType.PLATFORM_LAYOUT_PRACTICAL_MAP, 1.52F, 0.18F, 3.66F, 1.12F, 10, PLATFORM_DARK));
        c.add(platformTransfer(ProjectionComponentType.PLATFORM_TRANSFER_MATRIX, 0.18F, 0.70F, 1.18F, 0.62F, 12, PLATFORM_DARK));
        return ProjectionLayoutDefinition.create("实用线路图墙", ProjectionLayoutTarget.PLATFORM, new ProjectionCanvas(5.4F, 1.55F), c);
    }

    public static ProjectionLayoutDefinition platformCompactNeighbors() {
        List<ProjectionComponent> c = new ArrayList<>();
        c.add(platformPanel(0.0F, 0.0F, 3.2F, 0.74F, 0, 0xFF10151A, 0xFF4E5D66));
        c.add(platformBadge(0.14F, 0.22F, 0.42F, 0.28F, 8));
        c.add(platformLayoutMap(ProjectionComponentType.PLATFORM_LAYOUT_EDITOR_MAP, 0.62F, 0.15F, 2.42F, 0.46F, 10, PLATFORM_DARK));
        return ProjectionLayoutDefinition.create("Layout 临近提示牌", ProjectionLayoutTarget.PLATFORM, new ProjectionCanvas(3.2F, 0.74F), c);
    }

    public static ProjectionLayoutDefinition platformBidirectionalBoard() {
        List<ProjectionComponent> c = new ArrayList<>();
        c.add(platformPanel(0.0F, 0.0F, 4.4F, 1.12F, 0, 0xFFF3F1E8, 0xFFCAD2D6));
        c.add(platformLine(ProjectionComponentType.PLATFORM_LINE_BAND, 0.0F, 0.0F, 4.4F, 0.16F, 2, PLATFORM_LIGHT));
        c.add(platformTitle(0.18F, 0.26F, 1.08F, 0.50F, 8, PLATFORM_LIGHT));
        c.add(platformLayoutMap(ProjectionComponentType.PLATFORM_LAYOUT_SCHEMATIC_MAP, 1.36F, 0.24F, 2.82F, 0.70F, 10, PLATFORM_LIGHT));
        return ProjectionLayoutDefinition.create("示意线路图牌", ProjectionLayoutTarget.PLATFORM, new ProjectionCanvas(4.4F, 1.12F), c);
    }

    public static ProjectionLayoutDefinition platformLoopBoard() {
        List<ProjectionComponent> c = new ArrayList<>();
        c.add(platformPanel(0.0F, 0.0F, 2.1F, 1.65F, 0, 0xFF0B0C0E, 0xFF3C3F42));
        c.add(platformTitle(0.14F, 0.12F, 1.82F, 0.36F, 8, PLATFORM_DARK));
        c.add(platformLayoutMap(ProjectionComponentType.PLATFORM_LAYOUT_SCHEMATIC_MAP, 0.38F, 0.48F, 1.34F, 1.04F, 10, PLATFORM_DARK));
        return ProjectionLayoutDefinition.create("环线示意图牌", ProjectionLayoutTarget.PLATFORM, new ProjectionCanvas(2.1F, 1.65F), c);
    }

    public static ProjectionLayoutDefinition platformMinimalProjection() {
        List<ProjectionComponent> c = new ArrayList<>();
        c.add(platformPanel(0.0F, 0.0F, 2.6F, 0.55F, 0, 0x6606110E, 0x5534F0B8));
        c.add(platformLine(ProjectionComponentType.PLATFORM_LINE_CURRENT, 0.12F, 0.09F, 2.36F, 0.12F, 4, PLATFORM_NETWORK));
        c.add(platformBadge(0.14F, 0.26F, 0.42F, 0.20F, 8));
        c.add(platformDirection(0.64F, 0.25F, 1.76F, 0.20F, 10, PLATFORM_NETWORK));
        return ProjectionLayoutDefinition.create("极简投影牌", ProjectionLayoutTarget.PLATFORM, new ProjectionCanvas(2.6F, 0.55F), c);
    }

    private static ProjectionComponent panel(float x, float y, float width, float height, int layer, int fill, int border) {
        return ProjectionComponent.of(ProjectionComponentType.BACKGROUND_PANEL, x, y, width, height, layer, new ProjectionComponentSettings.Panel(fill, border, 0.018F, 1.0F));
    }

    private static ProjectionComponent text(ProjectionComponentType type, float x, float y, float width, float height, int layer, int color, float fontSize, ProjectionTextAlign align) {
        return text(type, x, y, width, height, layer, color, fontSize, align, ProjectionComponentSettings.TextOrientation.HORIZONTAL);
    }

    private static ProjectionComponent text(ProjectionComponentType type, float x, float y, float width, float height, int layer, int color, float fontSize, ProjectionTextAlign align, ProjectionComponentSettings.TextOrientation orientation) {
        ProjectionComponentSettings.Text defaults = (ProjectionComponentSettings.Text) ProjectionComponentSettings.defaultFor(type);
        return ProjectionComponent.of(type, x, y, width, height, layer,
                new ProjectionComponentSettings.Text(type, defaults.binding(), defaults.text(), color, fontSize, align, ProjectionOverflowMode.SCALE, orientation, defaults.lineSpacing(), defaults.maxLines()));
    }

    private static ProjectionComponent titleGroup(float x, float y, float width, float height, int layer, int primaryColor, int translationColor, float primaryFontSize, float translationFontSize, ProjectionTextAlign align) {
        return ProjectionComponent.of(ProjectionComponentType.STATION_TITLE_GROUP, x, y, width, height, layer,
                new ProjectionComponentSettings.StationTitleGroup(primaryColor, translationColor, primaryFontSize, translationFontSize, 0.035F, align, ProjectionOverflowMode.SCALE, ProjectionOverflowMode.MARQUEE, ProjectionComponentSettings.TextOrientation.HORIZONTAL, ProjectionComponentSettings.MissingTranslationMode.EXPAND_PRIMARY, 1.26F));
    }

    private static ProjectionComponent customText(float x, float y, float width, float height, int layer, String value, int color, float fontSize, ProjectionTextAlign align) {
        return ProjectionComponent.of(ProjectionComponentType.CUSTOM_TEXT, x, y, width, height, layer,
                new ProjectionComponentSettings.Text(ProjectionComponentType.CUSTOM_TEXT, "", value, color, fontSize, align, ProjectionOverflowMode.SCALE, ProjectionComponentSettings.TextOrientation.HORIZONTAL, 0.02F, 1));
    }

    private static ProjectionComponent exit(float x, float y, float width, float height, int layer, int fill, int text) {
        boolean fillEnabled = ((fill >>> 24) & 0xFF) > 0;
        return ProjectionComponent.of(ProjectionComponentType.EXIT_BADGE, x, y, width, height, layer,
                new ProjectionComponentSettings.ExitBadge(fillEnabled, fillEnabled, fill, fill, text, Math.min(0.18F, height * 0.45F))).withVisibleCondition(ProjectionVisibleCondition.HAS_EXIT);
    }

    private static ProjectionComponent routeList(float x, float y, float width, float height, int layer, int textColor) {
        return ProjectionComponent.of(ProjectionComponentType.ROUTE_LIST, x, y, width, height, layer,
                new ProjectionComponentSettings.RouteList(0.105F, 0.014F, 0.030F, 0.052F, 5, ProjectionComponentSettings.RouteOverflowMode.ROTATE, ProjectionOverflowMode.MARQUEE, textColor, textColor, 35)).withVisibleCondition(ProjectionVisibleCondition.HAS_ROUTES);
    }

    private static ProjectionComponent routeIcons(float x, float y, float width, float height, int layer, float iconSize, ProjectionComponentSettings.FlowDirection flow, boolean outline) {
        return routeIcons(x, y, width, height, layer, iconSize, flow, outline, false, 1);
    }

    private static ProjectionComponent routeIcons(float x, float y, float width, float height, int layer, float iconSize, ProjectionComponentSettings.FlowDirection flow, boolean outline, boolean wrapEnabled, int wrapTracks) {
        ProjectionComponentType type = outline ? ProjectionComponentType.ROUTE_OUTLINE_ICONS : ProjectionComponentType.ROUTE_ICONS;
        return ProjectionComponent.of(type, x, y, width, height, layer,
                new ProjectionComponentSettings.RouteIcon(type, ProjectionComponentSettings.IconShape.CIRCLE, iconSize, 0.035F, iconSize * 0.35F, 6, flow, ProjectionComponentSettings.RouteOverflowMode.PLUS_COUNT, true, 0xFFFFFFFF, 0xFFFFFFFF, 0.0F, 0.22F, 0xFFFFFFFF, 35, wrapEnabled, wrapTracks)).withVisibleCondition(ProjectionVisibleCondition.HAS_ROUTES);
    }

    private static ProjectionComponent backplate(float x, float y, float width, float height, int layer) {
        return backplate(x, y, width, height, layer, ProjectionComponentSettings.StripeDirection.HORIZONTAL);
    }

    private static ProjectionComponent backplate(float x, float y, float width, float height, int layer, ProjectionComponentSettings.StripeDirection direction) {
        return ProjectionComponent.of(ProjectionComponentType.ROUTE_BACKPLATE, x, y, width, height, layer,
                new ProjectionComponentSettings.RouteBackplate(direction, ProjectionComponentSettings.ColorPolicy.ROUTE_ORDER, 8, 1.0F, ProjectionComponentSettings.RouteOverflowMode.ROTATE, 0xFFFFFFFF, 35)).withVisibleCondition(ProjectionVisibleCondition.HAS_ROUTES);
    }

    private static ProjectionComponent platformPanel(float x, float y, float width, float height, int layer, int fill, int border) {
        return ProjectionComponent.of(ProjectionComponentType.BACKGROUND_PANEL, x, y, width, height, layer, new ProjectionComponentSettings.Panel(fill, border, 0.018F, 1.0F));
    }

    private static ProjectionComponent platformTitle(float x, float y, float width, float height, int layer) {
        return platformTitle(x, y, width, height, layer, ProjectionComponentSettings.TextOrientation.HORIZONTAL, PLATFORM_DARK);
    }

    private static ProjectionComponent platformTitle(float x, float y, float width, float height, int layer, PlatformTemplateTheme theme) {
        return platformTitle(x, y, width, height, layer, ProjectionComponentSettings.TextOrientation.HORIZONTAL, theme);
    }

    private static ProjectionComponent platformTitle(float x, float y, float width, float height, int layer, ProjectionComponentSettings.TextOrientation orientation) {
        return platformTitle(x, y, width, height, layer, orientation, PLATFORM_DARK);
    }

    private static ProjectionComponent platformTitle(float x, float y, float width, float height, int layer, ProjectionComponentSettings.TextOrientation orientation, PlatformTemplateTheme theme) {
        return ProjectionComponent.of(ProjectionComponentType.PLATFORM_TITLE_GROUP, x, y, width, height, layer,
                new ProjectionComponentSettings.PlatformTitleGroup(ProjectionComponentSettings.PlatformTitleContent.STATION_AND_PLATFORM, theme.primaryText(), theme.secondaryText(), 0.18F, 0.075F, 0.025F, ProjectionTextAlign.CENTER, ProjectionOverflowMode.SCALE, ProjectionOverflowMode.MARQUEE, orientation, ProjectionComponentSettings.MissingTranslationMode.EXPAND_PRIMARY, 1.18F)).withVisibleCondition(ProjectionVisibleCondition.HAS_PLATFORM);
    }

    private static ProjectionComponent platformBadge(float x, float y, float width, float height, int layer) {
        return ProjectionComponent.of(ProjectionComponentType.PLATFORM_BADGE, x, y, width, height, layer, ProjectionComponentSettings.PlatformBadge.defaults()).withVisibleCondition(ProjectionVisibleCondition.HAS_PLATFORM);
    }

    private static ProjectionComponent platformDirection(float x, float y, float width, float height, int layer) {
        return platformDirection(x, y, width, height, layer, PLATFORM_DARK);
    }

    private static ProjectionComponent platformDirection(float x, float y, float width, float height, int layer, PlatformTemplateTheme theme) {
        ProjectionComponentSettings.PlatformDirection defaults = ProjectionComponentSettings.PlatformDirection.defaults();
        return ProjectionComponent.of(ProjectionComponentType.PLATFORM_DIRECTION_TITLE, x, y, width, height, layer,
                new ProjectionComponentSettings.PlatformDirection(defaults.source(), defaults.prefix(), defaults.arrow(), defaults.arrowPlacement(), theme.primaryText(), theme.arrowColor(), defaults.fontSize(), defaults.align(), defaults.overflow())).withVisibleCondition(ProjectionVisibleCondition.HAS_ROUTE_LAYOUT);
    }

    private static ProjectionComponent platformLine(ProjectionComponentType type, float x, float y, float width, float height, int layer) {
        return platformLine(type, x, y, width, height, layer, PLATFORM_DARK);
    }

    private static ProjectionComponent platformLine(ProjectionComponentType type, float x, float y, float width, float height, int layer, PlatformTemplateTheme theme) {
        ProjectionComponentSettings.PlatformLine defaults = (ProjectionComponentSettings.PlatformLine) ProjectionComponentSettings.defaultFor(type);
        return ProjectionComponent.of(type, x, y, width, height, layer,
                new ProjectionComponentSettings.PlatformLine(type, defaults.style(), defaults.direction(), defaults.lineWidth(), defaults.nodeSize(), defaults.nodeStyle(), defaults.showLabel(), theme.lineText(), defaults.fontSize(), defaults.overflow())).withVisibleCondition(ProjectionVisibleCondition.HAS_CURRENT_LINE);
    }

    private static ProjectionComponent platformLineIcon(float x, float y, float width, float height, int layer) {
        return platformLineIcon(x, y, width, height, layer, PLATFORM_DARK);
    }

    private static ProjectionComponent platformLineIcon(float x, float y, float width, float height, int layer, PlatformTemplateTheme theme) {
        ProjectionComponentSettings.PlatformLineIcon defaults = ProjectionComponentSettings.PlatformLineIcon.defaults();
        return ProjectionComponent.of(ProjectionComponentType.PLATFORM_LINE_ICON, x, y, width, height, layer,
                new ProjectionComponentSettings.PlatformLineIcon(defaults.shape(), defaults.outline(), defaults.useLineColor(), defaults.fillColor(), defaults.borderColor(), theme.lineText(), defaults.iconSize(), defaults.fontSize(), defaults.borderWidth(), defaults.ringThicknessRatio(), defaults.showLabel())).withVisibleCondition(ProjectionVisibleCondition.HAS_CURRENT_LINE);
    }

    private static ProjectionComponent platformTransfer(ProjectionComponentType type, float x, float y, float width, float height, int layer) {
        return platformTransfer(type, x, y, width, height, layer, PLATFORM_DARK);
    }

    private static ProjectionComponent platformTransfer(ProjectionComponentType type, float x, float y, float width, float height, int layer, PlatformTemplateTheme theme) {
        ProjectionComponentSettings settings = ProjectionComponentSettings.defaultFor(type);
        ProjectionComponentSettings themed;
        if (settings instanceof ProjectionComponentSettings.PlatformTransferList list) {
            themed = new ProjectionComponentSettings.PlatformTransferList(list.maxVisible(), list.flow(), list.overflow(), list.includeOutStation(), list.showStation(), list.showPlatform(), theme.transferText(), theme.transferText(), theme.transferFill(), list.fontSize(), list.gap(), list.rotateIntervalTicks());
        } else if (settings instanceof ProjectionComponentSettings.PlatformTransferMatrix matrix) {
            themed = new ProjectionComponentSettings.PlatformTransferMatrix(matrix.columns(), matrix.maxVisible(), matrix.overflow(), matrix.includeOutStation(), matrix.showStation(), matrix.showPlatform(), theme.transferText(), theme.transferText(), theme.transferFill(), matrix.fontSize(), matrix.gap(), matrix.rotateIntervalTicks());
        } else {
            themed = settings;
        }
        return ProjectionComponent.of(type, x, y, width, height, layer, themed).withVisibleCondition(ProjectionVisibleCondition.HAS_TRANSFERS);
    }

    private static ProjectionComponent platformLayoutMap(ProjectionComponentType type, float x, float y, float width, float height, int layer) {
        return platformLayoutMap(type, x, y, width, height, layer, PLATFORM_DARK);
    }

    private static ProjectionComponent platformLayoutMap(ProjectionComponentType type, float x, float y, float width, float height, int layer, PlatformTemplateTheme theme) {
        ProjectionComponentSettings.PlatformLayoutMap defaults = (ProjectionComponentSettings.PlatformLayoutMap) ProjectionComponentSettings.defaultFor(type);
        return ProjectionComponent.of(type, x, y, width, height, layer,
                new ProjectionComponentSettings.PlatformLayoutMap(type, defaults.style(), defaults.showStopNames(), defaults.showTransferMarks(), defaults.showTerminalLabels(), defaults.followProjectionDirection(), defaults.nodeStyle(), theme.layoutText(), defaults.lineColor(), defaults.fontSize(), defaults.lineWidth(), defaults.nodeSize(), defaults.labelOverflow())).withVisibleCondition(ProjectionVisibleCondition.HAS_ROUTE_LAYOUT);
    }

    private record PlatformTemplateTheme(int primaryText, int secondaryText, int arrowColor, int lineText, int layoutText, int transferFill, int transferText) {}
}
