package dev.marblegate.superpipeslide.client.core.projection.preview;


import dev.marblegate.superpipeslide.client.core.projection.cache.ProjectionBuiltinIconTextureCache;
import dev.marblegate.superpipeslide.client.core.projection.cache.ProjectionNetworkImageCache;
import dev.marblegate.superpipeslide.client.core.projection.engine.PlatformLayoutProjectionEngine;
import dev.marblegate.superpipeslide.client.core.projection.engine.PlatformStatusTagProjectionEngine;
import dev.marblegate.superpipeslide.client.core.projection.engine.PlatformTransferProjectionEngine;
import dev.marblegate.superpipeslide.client.core.projection.render.ProjectionTextScroller;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.render.SmoothGuiPrimitives;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionBuiltinIcon;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponent;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponentSettings;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponentType;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionOverflowMode;
import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionTextAlign;
import dev.marblegate.superpipeslide.common.core.projection.layout.AppliedProjectionLayout;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionImageLayout;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutDefinition;
import dev.marblegate.superpipeslide.common.core.projection.template.ProjectionTemplates;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ProjectionLayoutPreviewPainter {
    private static final int FALLBACK_ROUTE_COLOR = 0xFF3366FF;

    private ProjectionLayoutPreviewPainter() {
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, ProjectionLayoutDefinition layout, SPSGui.Rect rect, boolean selected) {
        draw(graphics, font, layout, rect, selected, ProjectionPreviewScenario.standard());
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, ProjectionLayoutDefinition layout, SPSGui.Rect rect, boolean selected, ProjectionPreviewScenario scenario) {
        ProjectionLayoutDefinition safe = layout == null ? ProjectionTemplates.defaultLayout() : layout;
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), 0xFF10151A);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), selected ? 0xFF37C3BB : 0xFF28353B);
        drawExact(graphics, font, safe, fittedCanvas(rect, safe), scenario);
    }

    public static void drawExact(GuiGraphicsExtractor graphics, Font font, ProjectionLayoutDefinition layout, SPSGui.Rect canvas, ProjectionPreviewScenario scenario) {
        if (layout == null) {
            return;
        }
        graphics.enableScissor(canvas.x(), canvas.y(), canvas.right(), canvas.bottom());
        drawComponents(graphics, font, layout, canvas, scenario == null ? ProjectionPreviewScenario.standard() : scenario);
        graphics.disableScissor();
    }

    public static SPSGui.Rect fittedCanvas(SPSGui.Rect rect, ProjectionLayoutDefinition layout) {
        float canvasRatio = layout.canvas().width() / Math.max(0.001F, layout.canvas().height());
        int previewW = Math.max(1, rect.width() - 10);
        int previewH = Math.round(previewW / canvasRatio);
        if (previewH > rect.height() - 10) {
            previewH = Math.max(1, rect.height() - 10);
            previewW = Math.max(1, Math.round(previewH * canvasRatio));
        }
        return new SPSGui.Rect(rect.x() + (rect.width() - previewW) / 2, rect.y() + (rect.height() - previewH) / 2, previewW, previewH);
    }

    public static void drawApplied(GuiGraphicsExtractor graphics, Font font, AppliedProjectionLayout layout, SPSGui.Rect rect) {
        if (layout == null || layout.invalid()) {
            drawInvalid(graphics, font, rect, layout == null ? "Missing layout" : layout.errorMessage());
            return;
        }
        ProjectionLayoutDefinition preview = new ProjectionLayoutDefinition(layout.sourceLayoutId(), layout.sourceLayoutName(), layout.sourceSchemaVersion(), layout.target(), layout.canvas(), layout.components(), layout.appliedAt());
        draw(graphics, font, preview, rect, false);
    }

    public static void drawInvalid(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect rect, String message) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), 0xFF401B20);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), 0xFFD65B57);
        SPSGui.centeredText(graphics, font, Component.translatable("screen.superpipeslide.projection_designer.invalid"), rect.x() + rect.width() / 2, rect.y() + rect.height() / 2 - 8, 0xFFFFD6D6);
        SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, message == null ? "" : message, Math.round((rect.width() - 12) / 0.66F)), rect.x() + 6, rect.y() + rect.height() / 2 + 5, 0xFFFFA0A0, 0.66F);
    }

    private static void drawComponents(GuiGraphicsExtractor graphics, Font font, ProjectionLayoutDefinition layout, SPSGui.Rect canvas, ProjectionPreviewScenario data) {
        float scale = canvas.width() / Math.max(0.001F, layout.canvas().width());
        for (ProjectionComponent component : layout.components()) {
            if (!visible(component, data)) {
                continue;
            }
            int x0 = Math.round(canvas.x() + component.x() * scale);
            int y0 = Math.round(canvas.y() + component.y() * scale);
            int x1 = Math.round(canvas.x() + (component.x() + component.width()) * scale);
            int y1 = Math.round(canvas.y() + (component.y() + component.height()) * scale);
            int width = Math.max(1, x1 - x0);
            int height = Math.max(1, y1 - y0);
            SPSGui.Rect local = new SPSGui.Rect(Math.round(-width * 0.5F), Math.round(-height * 0.5F), width, height);
            graphics.pose().pushMatrix();
            graphics.pose().translate((x0 + x1) * 0.5F, (y0 + y1) * 0.5F);
            graphics.pose().rotate((float) Math.toRadians(component.rotationDegrees()));
            drawComponent(graphics, font, component, local, data, scale);
            graphics.pose().popMatrix();
        }
    }

    private static void drawComponent(GuiGraphicsExtractor graphics, Font font, ProjectionComponent component, SPSGui.Rect r, ProjectionPreviewScenario data, float scale) {
        ProjectionComponentSettings settings = component.settings();
        switch (component.type()) {
            case BACKGROUND_PANEL -> drawPanel(graphics, r, (ProjectionComponentSettings.Panel) settings, scale);
            case STATION_TITLE_GROUP -> drawStationTitleGroup(graphics, font, r, data.renderedPrimaryName(), data.renderedTranslationName(), (ProjectionComponentSettings.StationTitleGroup) settings, scale);
            case STATION_NAME_TEXT -> drawText(graphics, font, r, data.renderedPrimaryName(), (ProjectionComponentSettings.Text) settings, scale);
            case TRANSLATION_TEXT -> drawText(graphics, font, r, data.renderedTranslationName(), (ProjectionComponentSettings.Text) settings, scale);
            case CUSTOM_TEXT -> drawText(graphics, font, r, ((ProjectionComponentSettings.Text) settings).text(), (ProjectionComponentSettings.Text) settings, scale);
            case EXIT_BADGE -> drawExit(graphics, font, r, Component.translatable("screen.superpipeslide.station_projector.exit", data.renderedExitLabel()).getString(), (ProjectionComponentSettings.ExitBadge) settings, scale);
            case DIVIDER -> drawDivider(graphics, r, (ProjectionComponentSettings.Divider) settings, scale);
            case ROUTE_LIST -> drawRouteList(graphics, font, r, data.routes(), (ProjectionComponentSettings.RouteList) settings, scale);
            case ROUTE_TEXT -> drawRouteText(graphics, font, r, data.routes(), (ProjectionComponentSettings.RouteText) settings, scale);
            case ROUTE_ICONS, ROUTE_OUTLINE_ICONS -> drawRouteIcons(graphics, font, r, data.routes(), (ProjectionComponentSettings.RouteIcon) settings, component.type() == ProjectionComponentType.ROUTE_OUTLINE_ICONS, scale);
            case ROUTE_CAPSULES -> drawRouteCapsules(graphics, font, r, data.routes(), (ProjectionComponentSettings.RouteCapsules) settings, scale);
            case ROUTE_BACKPLATE -> drawRouteBackplate(graphics, font, r, data.routes(), (ProjectionComponentSettings.RouteBackplate) settings, scale);
            case BUILTIN_ICON -> drawBuiltinIcon(graphics, r, (ProjectionComponentSettings.BuiltinIcon) settings, scale);
            case NETWORK_IMAGE -> drawNetworkImage(graphics, font, r, (ProjectionComponentSettings.NetworkImage) settings, scale);
            case PLATFORM_TITLE_GROUP -> drawPlatformTitleGroup(graphics, font, r, data, (ProjectionComponentSettings.PlatformTitleGroup) settings, scale);
            case PLATFORM_BADGE -> drawPlatformBadge(graphics, font, r, data, (ProjectionComponentSettings.PlatformBadge) settings, scale);
            case PLATFORM_DIRECTION_TITLE -> drawPlatformDirection(graphics, font, r, data, (ProjectionComponentSettings.PlatformDirection) settings, scale);
            case PLATFORM_STATUS_TAGS -> drawPlatformStatusTags(graphics, font, r, data, (ProjectionComponentSettings.PlatformStatusTags) settings, scale);
            case PLATFORM_LINE_CURRENT, PLATFORM_LINE_BAND, PLATFORM_TERMINAL_STRIP -> drawPlatformLine(graphics, font, r, data, (ProjectionComponentSettings.PlatformLine) settings, scale);
            case PLATFORM_LINE_ICON -> drawPlatformLineIcon(graphics, font, r, data, (ProjectionComponentSettings.PlatformLineIcon) settings, scale);
            case PLATFORM_TRANSFER_LIST -> drawPlatformTransferList(graphics, font, r, data, (ProjectionComponentSettings.PlatformTransferList) settings, scale);
            case PLATFORM_TRANSFER_MATRIX -> drawPlatformTransferMatrix(graphics, font, r, data, (ProjectionComponentSettings.PlatformTransferMatrix) settings, scale);
            case PLATFORM_LAYOUT_STOP_LIST, PLATFORM_LAYOUT_PHYSICAL_MAP, PLATFORM_LAYOUT_PRACTICAL_MAP, PLATFORM_LAYOUT_SCHEMATIC_MAP, PLATFORM_LAYOUT_EDITOR_MAP -> drawPlatformLayoutMap(graphics, font, r, data, (ProjectionComponentSettings.PlatformLayoutMap) settings, scale);
        }
    }

    private static void drawBuiltinIcon(GuiGraphicsExtractor graphics, SPSGui.Rect r, ProjectionComponentSettings.BuiltinIcon settings, float scale) {
        drawImageChrome(graphics, r, settings.backgroundEnabled(), settings.backgroundColor(), settings.borderEnabled(), settings.borderColor(), settings.borderWidth(), scale);
        ProjectionBuiltinIcon icon = ProjectionBuiltinIcon.byId(settings.iconId());
        int padding = Math.max(0, Math.round(Math.min(r.width(), r.height()) * settings.padding()));
        ProjectionBuiltinIconTextureCache.IconTexture texture = ProjectionBuiltinIconTextureCache.textureFor(icon, settings);
        if (settings.fitMode() == ProjectionComponentSettings.ImageFitMode.TILE) {
            drawTiledIcon(graphics, texture, ProjectionImageLayout.resolveIconTiles(r.x(), r.y(), r.width(), r.height(), settings.anchor(), padding, settings.imageScale(), settings.tileGap()));
            return;
        }
        ProjectionImageLayout.Resolved resolved = ProjectionImageLayout.resolveIcon(r.x(), r.y(), r.width(), r.height(), settings.fitMode(), settings.anchor(), padding, settings.imageScale());
        drawTexturedResolved(graphics, texture.textureId(), resolved, texture.u0(), texture.v0(), texture.u1(), texture.v1(), texture.color(), 0, 0);
    }

    private static void drawNetworkImage(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, ProjectionComponentSettings.NetworkImage settings, float scale) {
        drawImageChrome(graphics, r, settings.backgroundEnabled(), settings.backgroundColor(), settings.borderEnabled(), settings.borderColor(), settings.borderWidth(), scale);
        ProjectionNetworkImageCache.State state = ProjectionNetworkImageCache.state(settings.url());
        if (state.ready()) {
            ProjectionImageLayout.Resolved resolved = ProjectionImageLayout.resolve(r.x(), r.y(), r.width(), r.height(), state.width(), state.height(), settings.fitMode(), settings.anchor(), 0.0F, settings.cropX(), settings.cropY(), settings.cropW(), settings.cropH());
            int color = withAlphaMultiplier(0xFFFFFFFF, settings.opacity());
            if (settings.fitMode() == ProjectionComponentSettings.ImageFitMode.TILE) {
                drawTiledTexture(graphics, state.textureId(), resolved, state.width(), state.height(), settings, color);
            } else {
                drawTexturedResolved(graphics, state.textureId(), resolved, resolved.u0(), resolved.v0(), resolved.u1(), resolved.v1(), color, 0, 0);
            }
            return;
        }
        boolean loading = state.status() == ProjectionNetworkImageCache.Status.QUEUED || state.status() == ProjectionNetworkImageCache.Status.DOWNLOADING || state.status() == ProjectionNetworkImageCache.Status.DECODING;
        if (loading && settings.loadingMode() == ProjectionComponentSettings.ImageLoadingMode.HIDDEN) {
            return;
        }
        if (!loading && settings.fallbackMode() == ProjectionComponentSettings.ImageFallbackMode.HIDDEN) {
            return;
        }
        drawImageStatus(graphics, font, r, state, loading, !loading && settings.fallbackMode() == ProjectionComponentSettings.ImageFallbackMode.COMPACT, loading ? settings.loadingMode() == ProjectionComponentSettings.ImageLoadingMode.PLACEHOLDER : settings.fallbackMode() == ProjectionComponentSettings.ImageFallbackMode.PLACEHOLDER);
    }

    private static void drawImageChrome(GuiGraphicsExtractor graphics, SPSGui.Rect r, boolean backgroundEnabled, int backgroundColor, boolean borderEnabled, int borderColor, float borderWidth, float scale) {
        if (backgroundEnabled && (backgroundColor >>> 24) > 0) {
            graphics.fill(r.x(), r.y(), r.right(), r.bottom(), backgroundColor);
        }
        int border = Math.max(0, Math.round(borderWidth * scale));
        if (borderEnabled && border > 0 && (borderColor >>> 24) > 0) {
            drawBorder(graphics, r, border, borderColor);
        }
    }

    private static void drawTexturedResolved(GuiGraphicsExtractor graphics, net.minecraft.resources.Identifier textureId, ProjectionImageLayout.Resolved resolved, float u0, float v0, float u1, float v1, int color, int offsetX, int offsetY) {
        int x = Math.round(resolved.x()) + offsetX;
        int y = Math.round(resolved.y()) + offsetY;
        int width = Math.max(1, Math.round(resolved.width()));
        int height = Math.max(1, Math.round(resolved.height()));
        SmoothGuiPrimitives.texturedQuad(graphics, textureId, new Vec2(x, y), new Vec2(x + width, y), new Vec2(x + width, y + height), new Vec2(x, y + height), color, u0, u1, v0, v1);
    }

    private static void drawTiledTexture(GuiGraphicsExtractor graphics, net.minecraft.resources.Identifier textureId, ProjectionImageLayout.Resolved area, int imageWidth, int imageHeight, ProjectionComponentSettings.NetworkImage settings, int color) {
        float cropRatio = Math.max(0.01F, (imageWidth / (float) Math.max(1, imageHeight)) * settings.cropW() / Math.max(0.01F, settings.cropH()));
        float base = Math.max(10.0F, Math.min(area.width(), area.height()) * 0.42F);
        float tileW = cropRatio >= 1.0F ? base * cropRatio : base;
        float tileH = cropRatio >= 1.0F ? base : base / cropRatio;
        tileW = Math.max(6.0F, Math.min(area.width(), tileW));
        tileH = Math.max(6.0F, Math.min(area.height(), tileH));
        int columns = Math.min(16, Math.max(1, (int) Math.ceil(area.width() / tileW)));
        int rows = Math.min(16, Math.max(1, (int) Math.ceil(area.height() / tileH)));
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                float x = area.x() + column * tileW;
                float y = area.y() + row * tileH;
                float w = Math.min(tileW, area.x() + area.width() - x);
                float h = Math.min(tileH, area.y() + area.height() - y);
                if (w <= 0.5F || h <= 0.5F) {
                    continue;
                }
                float u1 = area.u0() + (area.u1() - area.u0()) * (w / tileW);
                float v1 = area.v0() + (area.v1() - area.v0()) * (h / tileH);
                drawTexturedResolved(graphics, textureId, new ProjectionImageLayout.Resolved(x, y, w, h, area.u0(), area.v0(), u1, v1), area.u0(), area.v0(), u1, v1, color, 0, 0);
            }
        }
    }

    private static void drawTiledIcon(GuiGraphicsExtractor graphics, ProjectionBuiltinIconTextureCache.IconTexture texture, ProjectionImageLayout.TileGrid grid) {
        for (int row = 0; row < grid.rows(); row++) {
            for (int column = 0; column < grid.columns(); column++) {
                float x = grid.x() + column * (grid.tileSize() + grid.gap());
                float y = grid.y() + row * (grid.tileSize() + grid.gap());
                float w = Math.min(grid.tileSize(), grid.x() + grid.width() - x);
                float h = Math.min(grid.tileSize(), grid.y() + grid.height() - y);
                if (w <= 0.5F || h <= 0.5F) {
                    continue;
                }
                float u1 = texture.u0() + (texture.u1() - texture.u0()) * (w / grid.tileSize());
                float v1 = texture.v0() + (texture.v1() - texture.v0()) * (h / grid.tileSize());
                drawTexturedResolved(graphics, texture.textureId(), new ProjectionImageLayout.Resolved(x, y, w, h, texture.u0(), texture.v0(), u1, v1), texture.u0(), texture.v0(), u1, v1, texture.color(), 0, 0);
            }
        }
    }

    private static void drawImageStatus(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, ProjectionNetworkImageCache.State state, boolean loading, boolean compact, boolean placeholder) {
        int tint = loading ? 0xFF37C3BB : 0xFFFF8A6A;
        int fill = loading ? 0x2219E4D8 : 0x22FF705C;
        if (placeholder) {
            graphics.fill(r.x(), r.y(), r.right(), r.bottom(), fill);
        }
        int cx = r.x() + r.width() / 2;
        int cy = r.y() + r.height() / 2;
        int mark = Math.max(6, Math.min(r.width(), r.height()) / (compact ? 5 : 4));
        SmoothGuiPrimitives.line(graphics, new Vec2(cx - mark, cy), new Vec2(cx + mark, cy), Math.max(1.0D, mark * 0.12D), tint);
        SmoothGuiPrimitives.line(graphics, new Vec2(cx, cy - mark), new Vec2(cx, cy + mark), Math.max(1.0D, mark * 0.12D), tint);
        if (!compact && r.width() > 34 && r.height() > 22) {
            String key = state.messageKey() == null || state.messageKey().isBlank() ? "screen.superpipeslide.projection_image.failed" : state.messageKey();
            String label = Component.translatable(key).getString();
            float textScale = Math.max(0.42F, Math.min(0.72F, r.height() / 58.0F));
            int maxWidth = Math.max(10, Math.round(r.width() / textScale) - 8);
            String rendered = SPSGui.ellipsize(font, label, maxWidth);
            SPSGui.smallText(graphics, font, rendered, Math.round(cx - font.width(rendered) * textScale * 0.5F), cy + mark + 4, tint, textScale);
        }
    }

    private static void drawPanel(GuiGraphicsExtractor graphics, SPSGui.Rect r, ProjectionComponentSettings.Panel settings, float scale) {
        int fill = withAlpha(settings.fillColor(), Math.round(((settings.fillColor() >>> 24) & 0xFF) * settings.opacity()));
        if ((fill >>> 24) > 0) {
            graphics.fill(r.x(), r.y(), r.right(), r.bottom(), fill);
        }
        int border = Math.max(0, Math.round(settings.borderWidth() * scale));
        if (border > 0 && ((settings.borderColor() >>> 24) & 0xFF) > 0) {
            drawBorder(graphics, r, border, settings.borderColor());
        }
    }

    private static void drawExit(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, String text, ProjectionComponentSettings.ExitBadge settings, float scale) {
        if (settings.fillEnabled() && (settings.fillColor() >>> 24) > 0) {
            graphics.fill(r.x(), r.y(), r.right(), r.bottom(), settings.fillColor());
        }
        if (settings.borderEnabled() && (settings.borderColor() >>> 24) > 0) {
            graphics.outline(r.x(), r.y(), r.width(), r.height(), settings.borderColor());
        }
        ProjectionComponentSettings.Text textSettings = new ProjectionComponentSettings.Text(ProjectionComponentType.CUSTOM_TEXT, "", "", settings.textColor(), settings.fontSize(), ProjectionTextAlign.CENTER, ProjectionOverflowMode.SCALE, 0.0F, 1);
        drawText(graphics, font, r, text, textSettings, scale);
    }

    private static void drawPlatformTitleGroup(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, ProjectionPreviewScenario data, ProjectionComponentSettings.PlatformTitleGroup settings, float scale) {
        String primary = switch (settings.content()) {
            case PLATFORM_AND_STATION, PLATFORM_ONLY -> data.renderedPlatformName();
            case STATION_ONLY, STATION_AND_PLATFORM -> data.renderedPrimaryName();
        };
        String secondary = switch (settings.content()) {
            case PLATFORM_AND_STATION -> data.renderedPrimaryName();
            case STATION_AND_PLATFORM -> data.renderedPlatformName();
            case STATION_ONLY, PLATFORM_ONLY -> data.renderedTranslationName();
        };
        ProjectionComponentSettings.StationTitleGroup equivalent = new ProjectionComponentSettings.StationTitleGroup(settings.primaryColor(), settings.secondaryColor(), settings.primaryFontSize(), settings.secondaryFontSize(), settings.gap(), settings.align(), settings.primaryOverflow(), settings.secondaryOverflow(), settings.orientation(), settings.missingSecondaryMode(), settings.missingPrimaryScale());
        drawStationTitleGroup(graphics, font, r, primary, secondary, equivalent, scale);
    }

    private static void drawPlatformBadge(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, ProjectionPreviewScenario data, ProjectionComponentSettings.PlatformBadge settings, float scale) {
        int fill = settings.useLineColor() ? firstColor(data.lineRoute().colors()) : settings.fillColor();
        int border = Math.max(0, Math.round(settings.borderWidth() * scale));
        if (settings.style() == ProjectionComponentSettings.PlatformBadgeStyle.CAPSULE) {
            if (border > 0 && (settings.borderColor() >>> 24) > 0) {
                drawCapsule(graphics, r, settings.borderColor());
                drawCapsule(graphics, inset(r, border), fill);
            } else {
                drawCapsule(graphics, r, fill);
            }
        } else if (settings.style() == ProjectionComponentSettings.PlatformBadgeStyle.SOLID) {
            graphics.fill(r.x(), r.y(), r.right(), r.bottom(), fill);
        } else if (border > 0 && settings.style() == ProjectionComponentSettings.PlatformBadgeStyle.OUTLINE) {
            drawBorder(graphics, r, border, settings.borderColor());
        }
        String label = settings.prefix() + data.renderedPlatformNumber() + settings.suffix();
        ProjectionComponentSettings.Text text = new ProjectionComponentSettings.Text(ProjectionComponentType.CUSTOM_TEXT, "", "", settings.style() == ProjectionComponentSettings.PlatformBadgeStyle.SOLID ? contrast(fill) : settings.textColor(), settings.fontSize(), ProjectionTextAlign.CENTER, ProjectionOverflowMode.SCALE, 0.0F, 1);
        drawText(graphics, font, r, label, text, scale);
    }

    private static void drawPlatformDirection(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, ProjectionPreviewScenario data, ProjectionComponentSettings.PlatformDirection settings, float scale) {
        String target = switch (settings.source()) {
            case NEXT_STOP -> data.renderedNextStop();
            case PREVIOUS_STOP -> data.renderedPreviousStop();
            case ORIGIN -> data.renderedOriginStop();
            case LAYOUT_NAME -> data.renderedLineName();
            case TERMINAL -> data.renderedTerminalStop();
        };
        String prefix = platformPrefix(settings.prefix());
        String label = (prefix.isBlank() ? "" : prefix + " ") + target;
        String arrow = arrowText(settings.arrow(), settings.source() != ProjectionComponentSettings.PlatformDirectionSource.PREVIOUS_STOP && settings.source() != ProjectionComponentSettings.PlatformDirectionSource.ORIGIN);
        if (!arrow.isBlank() && settings.arrowPlacement() == ProjectionComponentSettings.ArrowPlacement.BEFORE) label = arrow + " " + label;
        if (!arrow.isBlank() && settings.arrowPlacement() == ProjectionComponentSettings.ArrowPlacement.AFTER) label = label + " " + arrow;
        ProjectionComponentSettings.Text text = new ProjectionComponentSettings.Text(ProjectionComponentType.CUSTOM_TEXT, "", "", settings.textColor(), settings.fontSize(), settings.align(), settings.overflow(), 0.0F, 1);
        drawText(graphics, font, r, label, text, scale);
    }

    private static void drawPlatformStatusTags(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, ProjectionPreviewScenario data, ProjectionComponentSettings.PlatformStatusTags settings, float scale) {
        List<String> tags = new ArrayList<>();
        if (settings.showTerminal() && data.platformTerminal()) tags.add(Component.translatable("screen.superpipeslide.platform_projector.tag.terminal").getString());
        if (settings.showLoop() && data.platformLoop()) tags.add(Component.translatable("screen.superpipeslide.platform_projector.tag.loop").getString());
        if (settings.showTransfer() && !data.transferRoutes().isEmpty()) tags.add(Component.translatable("screen.superpipeslide.platform_projector.tag.transfer").getString());
        if (settings.showBidirectional() && data.platformBidirectional()) tags.add(Component.translatable("screen.superpipeslide.platform_projector.tag.bidirectional").getString());
        PlatformStatusTagProjectionEngine.Layout layout = PlatformStatusTagProjectionEngine.build(tags, settings, r.width() / Math.max(1.0F, scale), r.height() / Math.max(1.0F, scale));
        for (PlatformStatusTagProjectionEngine.Primitive primitive : layout.primitives()) {
            if (primitive instanceof PlatformStatusTagProjectionEngine.Capsule capsule) {
                drawCapsule(graphics, normalizedRect(r, capsule.x(), capsule.y(), capsule.width(), capsule.height()), capsule.color());
                continue;
            }
            if (primitive instanceof PlatformStatusTagProjectionEngine.Text text) {
                ProjectionComponentSettings.Text textSettings = new ProjectionComponentSettings.Text(ProjectionComponentType.CUSTOM_TEXT, "", "", text.color(), text.fontSize(), text.align(), text.overflow(), 0.0F, 1);
                drawText(graphics, font, normalizedRect(r, text.x(), text.y(), text.width(), text.height()), text.value(), textSettings, scale);
            }
        }
    }

    private static void drawPlatformLine(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, ProjectionPreviewScenario data, ProjectionComponentSettings.PlatformLine settings, float scale) {
        ProjectionPreviewScenario.RoutePreview line = data.lineRoute();
        boolean horizontal = settings.direction() == ProjectionComponentSettings.StripeDirection.HORIZONTAL;
        int lineWidth = Math.max(1, Math.round(settings.lineWidth() * scale));
        if (settings.style() == ProjectionComponentSettings.PlatformLineStyle.BAND || settings.style() == ProjectionComponentSettings.PlatformLineStyle.TERMINAL_STRIP) {
            drawRouteColorBlocks(graphics, r, line.colors(), r.width() >= r.height());
        } else {
            if (horizontal) {
                int y = r.y() + (r.height() - lineWidth) / 2;
                drawRouteColorBlocks(graphics, new SPSGui.Rect(r.x(), y, r.width(), lineWidth), line.colors(), true);
                int node = Math.max(lineWidth + 2, Math.round(settings.nodeSize() * scale));
                drawPlatformNode(graphics, new SPSGui.Rect(r.x() + (r.width() - node) / 2, r.y() + (r.height() - node) / 2, node, node), settings.nodeStyle(), firstColor(line.colors()), Math.max(1, lineWidth));
            } else {
                int x = r.x() + (r.width() - lineWidth) / 2;
                drawRouteColorBlocks(graphics, new SPSGui.Rect(x, r.y(), lineWidth, r.height()), line.colors(), false);
                int node = Math.max(lineWidth + 2, Math.round(settings.nodeSize() * scale));
                drawPlatformNode(graphics, new SPSGui.Rect(r.x() + (r.width() - node) / 2, r.y() + (r.height() - node) / 2, node, node), settings.nodeStyle(), firstColor(line.colors()), Math.max(1, lineWidth));
            }
        }
        if (settings.showLabel()) {
            ProjectionComponentSettings.Text text = new ProjectionComponentSettings.Text(ProjectionComponentType.CUSTOM_TEXT, "", "", settings.textColor(), settings.fontSize(), ProjectionTextAlign.CENTER, settings.overflow(), 0.0F, 1);
            drawText(graphics, font, r, settings.style() == ProjectionComponentSettings.PlatformLineStyle.TERMINAL_STRIP ? data.renderedTerminalStop() : line.name(), text, scale);
        }
    }

    private static void drawPlatformLineIcon(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, ProjectionPreviewScenario data, ProjectionComponentSettings.PlatformLineIcon settings, float scale) {
        int size = Math.max(1, Math.round(settings.iconSize() * scale));
        SPSGui.Rect cell = new SPSGui.Rect(r.x() + (r.width() - size) / 2, r.y() + (r.height() - size) / 2, size, size);
        int fill = settings.useLineColor() ? firstColor(data.lineRoute().colors()) : settings.fillColor();
        int border = settings.borderWidth() > 0.0005F ? Math.max(1, Math.round(settings.borderWidth() * scale)) : 0;
        int ring = Math.max(1, Math.round(size * settings.ringThicknessRatio()));
        drawIconShape(graphics, cell, settings.shape(), settings.outline(), fill, settings.borderColor(), border, ring);
        if (settings.showLabel()) {
            float textScale = Math.max(0.04F, settings.fontSize() * scale / Math.max(1.0F, font.lineHeight));
            drawScaledText(graphics, font, shortRoute(data.renderedLineName()), cell.x() + cell.width() * 0.5F, cell.y() + cell.height() * 0.5F, textScale, settings.outline() ? settings.textColor() : contrast(fill));
        }
    }

    private static void drawPlatformTransferList(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, ProjectionPreviewScenario data, ProjectionComponentSettings.PlatformTransferList settings, float scale) {
        PlatformTransferProjectionEngine.Layout layout = PlatformTransferProjectionEngine.buildList(previewTransfers(data.transferRoutes()), settings, System.currentTimeMillis(), r.hashCode());
        for (PlatformTransferProjectionEngine.Primitive primitive : layout.primitives()) {
            drawPlatformTransferPrimitive(graphics, font, r, primitive, scale);
        }
    }

    private static void drawPlatformTransferMatrix(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, ProjectionPreviewScenario data, ProjectionComponentSettings.PlatformTransferMatrix settings, float scale) {
        PlatformTransferProjectionEngine.Layout layout = PlatformTransferProjectionEngine.buildMatrix(previewTransfers(data.transferRoutes()), settings, System.currentTimeMillis(), r.hashCode());
        for (PlatformTransferProjectionEngine.Primitive primitive : layout.primitives()) {
            drawPlatformTransferPrimitive(graphics, font, r, primitive, scale);
        }
    }

    private static void drawPlatformTransferPrimitive(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, PlatformTransferProjectionEngine.Primitive primitive, float scale) {
        if (primitive instanceof PlatformTransferProjectionEngine.Rect rect) {
            SPSGui.Rect rr = normalizedRect(r, rect.x(), rect.y(), rect.width(), rect.height());
            graphics.fill(rr.x(), rr.y(), rr.right(), rr.bottom(), rect.color());
            return;
        }
        if (primitive instanceof PlatformTransferProjectionEngine.Capsule capsule) {
            drawCapsule(graphics, normalizedRect(r, capsule.x(), capsule.y(), capsule.width(), capsule.height()), capsule.color());
            return;
        }
        if (primitive instanceof PlatformTransferProjectionEngine.Icon icon) {
            SPSGui.Rect rr = normalizedSquare(r, icon.centerX(), icon.centerY(), icon.size());
            int border = icon.borderWidth() > 0.0005F ? Math.max(1, Math.round(icon.borderWidth() * scale)) : 0;
            int ring = Math.max(1, Math.round(Math.min(rr.width(), rr.height()) * icon.ringThicknessRatio()));
            drawIconShape(graphics, rr, icon.shape(), icon.outline(), icon.fillColor(), icon.borderColor(), border, ring);
            return;
        }
        if (primitive instanceof PlatformTransferProjectionEngine.Text text) {
            ProjectionComponentSettings.Text textSettings = new ProjectionComponentSettings.Text(ProjectionComponentType.CUSTOM_TEXT, "", "", text.color(), text.fontSize(), text.align(), text.overflow(), 0.0F, 1);
            drawText(graphics, font, normalizedRect(r, text.x(), text.y(), text.width(), text.height()), text.value(), textSettings, scale);
        }
    }

    private static void drawPlatformLayoutMap(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, ProjectionPreviewScenario data, ProjectionComponentSettings.PlatformLayoutMap settings, float scale) {
        PlatformLayoutProjectionEngine.Layout layout = PlatformLayoutProjectionEngine.build(data.platformLayoutData(), settings, System.currentTimeMillis());
        for (PlatformLayoutProjectionEngine.Primitive primitive : layout.primitives()) {
            drawPlatformLayoutPrimitive(graphics, font, r, primitive, scale);
        }
    }

    private static void drawPlatformLayoutPrimitive(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, PlatformLayoutProjectionEngine.Primitive primitive, float scale) {
        if (primitive instanceof PlatformLayoutProjectionEngine.Rect rect) {
            SPSGui.Rect rr = normalizedRect(r, rect.x(), rect.y(), rect.width(), rect.height());
            graphics.fill(rr.x(), rr.y(), rr.right(), rr.bottom(), rect.color());
            return;
        }
        if (primitive instanceof PlatformLayoutProjectionEngine.Band band) {
            drawRouteColorBlocks(graphics, normalizedRect(r, band.x(), band.y(), band.width(), band.height()), band.colors(), band.horizontal());
            return;
        }
        if (primitive instanceof PlatformLayoutProjectionEngine.Capsule capsule) {
            drawCapsule(graphics, normalizedRect(r, capsule.x(), capsule.y(), capsule.width(), capsule.height()), capsule.color());
            return;
        }
        if (primitive instanceof PlatformLayoutProjectionEngine.Circle circle) {
            double radius = Math.max(0.5D, circle.radius() * Math.min(r.width(), r.height()));
            double cx = r.x() + circle.x() * r.width();
            double cy = r.y() + circle.y() * r.height();
            drawSmoothCircleAt(graphics, cx, cy, radius, circle.color());
            return;
        }
        if (primitive instanceof PlatformLayoutProjectionEngine.Ring ring) {
            double radius = Math.max(0.5D, ring.radius() * Math.min(r.width(), r.height()));
            double thickness = Math.max(1.0D, ring.thickness() * Math.min(r.width(), r.height()));
            double cx = r.x() + ring.x() * r.width();
            double cy = r.y() + ring.y() * r.height();
            drawSmoothCircleRingAt(graphics, cx, cy, radius, thickness, ring.color());
            return;
        }
        if (primitive instanceof PlatformLayoutProjectionEngine.Line line) {
            Vec2 a = new Vec2(r.x() + line.x1() * r.width(), r.y() + line.y1() * r.height());
            Vec2 b = new Vec2(r.x() + line.x2() * r.width(), r.y() + line.y2() * r.height());
            SmoothGuiPrimitives.line(graphics, a, b, Math.max(1.0D, line.width() * Math.min(r.width(), r.height())), line.color());
            return;
        }
        if (primitive instanceof PlatformLayoutProjectionEngine.Text text) {
            ProjectionComponentSettings.Text textSettings = new ProjectionComponentSettings.Text(ProjectionComponentType.CUSTOM_TEXT, "", "", text.color(), text.fontSize(), text.align(), text.overflow(), 0.0F, 1);
            SPSGui.Rect textRect = normalizedRect(r, text.x(), text.y(), text.width(), text.height());
            if (Math.abs(text.rotationDegrees()) > 0.01F) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(textRect.x() + textRect.width() * 0.5F, textRect.y() + textRect.height() * 0.5F);
                graphics.pose().rotate((float) Math.toRadians(text.rotationDegrees()));
                drawText(graphics, font, new SPSGui.Rect(-textRect.width() / 2, -textRect.height() / 2, textRect.width(), textRect.height()), text.value(), textSettings, scale);
                graphics.pose().popMatrix();
                return;
            }
            drawText(graphics, font, textRect, text.value(), textSettings, scale);
        }
    }

    private static SPSGui.Rect normalizedRect(SPSGui.Rect r, float x, float y, float width, float height) {
        return new SPSGui.Rect(r.x() + Math.round(x * r.width()), r.y() + Math.round(y * r.height()), Math.max(1, Math.round(width * r.width())), Math.max(1, Math.round(height * r.height())));
    }

    private static SPSGui.Rect normalizedSquare(SPSGui.Rect r, float centerX, float centerY, float size) {
        int diameter = Math.max(1, Math.round(size * Math.min(r.width(), r.height())));
        int cx = r.x() + Math.round(centerX * r.width());
        int cy = r.y() + Math.round(centerY * r.height());
        return new SPSGui.Rect(cx - diameter / 2, cy - diameter / 2, diameter, diameter);
    }

    private static void drawStationTitleGroup(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, String primaryName, String translationName, ProjectionComponentSettings.StationTitleGroup settings, float scale) {
        if (settings.orientation() == ProjectionComponentSettings.TextOrientation.ROTATE_CW || settings.orientation() == ProjectionComponentSettings.TextOrientation.ROTATE_CCW) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(r.x() + r.width() * 0.5F, r.y() + r.height() * 0.5F);
            graphics.pose().rotate((float) Math.toRadians(settings.orientation() == ProjectionComponentSettings.TextOrientation.ROTATE_CW ? 90.0D : -90.0D));
            SPSGui.Rect rotated = new SPSGui.Rect(Math.round(-r.height() * 0.5F), Math.round(-r.width() * 0.5F), r.height(), r.width());
            drawStationTitleGroupHorizontal(graphics, font, rotated, primaryName, translationName, settings, scale);
            graphics.pose().popMatrix();
            return;
        }
        if (settings.orientation() == ProjectionComponentSettings.TextOrientation.VERTICAL_STACK) {
            drawStationTitleGroupVertical(graphics, font, r, primaryName, translationName, settings, scale);
            return;
        }
        drawStationTitleGroupHorizontal(graphics, font, r, primaryName, translationName, settings, scale);
    }

    private static void drawStationTitleGroupHorizontal(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, String primaryName, String translationName, ProjectionComponentSettings.StationTitleGroup settings, float scale) {
        String primary = primaryName == null ? "" : primaryName.trim();
        String translation = translationName == null ? "" : translationName.trim();
        if (primary.isEmpty()) {
            return;
        }
        int primaryHeight = Math.max(1, Math.round(settings.primaryFontSize() * scale));
        int translationHeight = Math.max(1, Math.round(settings.translationFontSize() * scale));
        int gap = Math.max(0, Math.round(settings.gap() * scale));
        ProjectionComponentSettings.Text primaryText = titleText(settings.primaryColor(), settings.primaryFontSize(), settings.align(), settings.primaryOverflow(), ProjectionComponentSettings.TextOrientation.HORIZONTAL);
        ProjectionComponentSettings.Text translationText = titleText(settings.translationColor(), settings.translationFontSize(), settings.align(), settings.translationOverflow(), ProjectionComponentSettings.TextOrientation.HORIZONTAL);
        if (!translation.isEmpty()) {
            int totalHeight = Math.min(r.height(), primaryHeight + gap + translationHeight);
            int top = r.y() + (r.height() - totalHeight) / 2;
            SPSGui.Rect primaryRect = new SPSGui.Rect(r.x(), top, r.width(), Math.max(1, Math.min(primaryHeight, totalHeight)));
            SPSGui.Rect translationRect = new SPSGui.Rect(r.x(), top + primaryRect.height() + gap, r.width(), Math.max(1, Math.min(translationHeight, r.bottom() - top - primaryRect.height() - gap)));
            drawText(graphics, font, primaryRect, primary, primaryText, scale);
            drawText(graphics, font, translationRect, translation, translationText, scale);
            return;
        }
        ProjectionComponentSettings.Text missingText = settings.missingTranslationMode() == ProjectionComponentSettings.MissingTranslationMode.EXPAND_PRIMARY
                ? titleText(settings.primaryColor(), settings.primaryFontSize() * settings.missingPrimaryScale(), settings.align(), settings.primaryOverflow(), ProjectionComponentSettings.TextOrientation.HORIZONTAL)
                : primaryText;
        SPSGui.Rect primaryRect = switch (settings.missingTranslationMode()) {
            case KEEP_PRIMARY_SLOT -> {
                int totalHeight = Math.min(r.height(), primaryHeight + gap + translationHeight);
                yield new SPSGui.Rect(r.x(), r.y() + (r.height() - totalHeight) / 2, r.width(), Math.max(1, Math.min(primaryHeight, totalHeight)));
            }
            case CENTER_PRIMARY, EXPAND_PRIMARY -> r;
        };
        drawText(graphics, font, primaryRect, primary, missingText, scale);
    }

    private static void drawStationTitleGroupVertical(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, String primaryName, String translationName, ProjectionComponentSettings.StationTitleGroup settings, float scale) {
        String primary = primaryName == null ? "" : primaryName.trim();
        String translation = translationName == null ? "" : translationName.trim();
        if (primary.isEmpty()) {
            return;
        }
        ProjectionComponentSettings.Text primaryText = titleText(settings.primaryColor(), settings.primaryFontSize(), settings.align(), settings.primaryOverflow(), ProjectionComponentSettings.TextOrientation.VERTICAL_STACK);
        ProjectionComponentSettings.Text translationText = titleText(settings.translationColor(), settings.translationFontSize(), settings.align(), settings.translationOverflow(), ProjectionComponentSettings.TextOrientation.VERTICAL_STACK);
        if (!translation.isEmpty()) {
            int gap = Math.max(0, Math.round(settings.gap() * scale));
            int primaryWidth = Math.max(1, Math.round(r.width() * 0.62F));
            SPSGui.Rect primaryRect = new SPSGui.Rect(r.x(), r.y(), Math.max(1, primaryWidth - gap / 2), r.height());
            SPSGui.Rect translationRect = new SPSGui.Rect(r.x() + primaryWidth + gap / 2, r.y(), Math.max(1, r.width() - primaryWidth - gap / 2), r.height());
            drawText(graphics, font, primaryRect, primary, primaryText, scale);
            drawText(graphics, font, translationRect, translation, translationText, scale);
            return;
        }
        ProjectionComponentSettings.Text missingText = settings.missingTranslationMode() == ProjectionComponentSettings.MissingTranslationMode.EXPAND_PRIMARY
                ? titleText(settings.primaryColor(), settings.primaryFontSize() * settings.missingPrimaryScale(), settings.align(), settings.primaryOverflow(), ProjectionComponentSettings.TextOrientation.VERTICAL_STACK)
                : primaryText;
        SPSGui.Rect primaryRect = settings.missingTranslationMode() == ProjectionComponentSettings.MissingTranslationMode.KEEP_PRIMARY_SLOT
                ? new SPSGui.Rect(r.x(), r.y(), Math.max(1, Math.round(r.width() * 0.62F)), r.height())
                : r;
        drawText(graphics, font, primaryRect, primary, missingText, scale);
    }

    private static ProjectionComponentSettings.Text titleText(int color, float fontSize, ProjectionTextAlign align, ProjectionOverflowMode overflow, ProjectionComponentSettings.TextOrientation orientation) {
        return new ProjectionComponentSettings.Text(ProjectionComponentType.CUSTOM_TEXT, "", "", color, fontSize, align, overflow, orientation, 0.02F, 1);
    }

    private static void drawDivider(GuiGraphicsExtractor graphics, SPSGui.Rect r, ProjectionComponentSettings.Divider settings, float scale) {
        int thickness = Math.max(1, Math.round(settings.thickness() * scale));
        int y = r.y() + (r.height() - thickness) / 2;
        if (!settings.dashed()) {
            graphics.fill(r.x(), y, r.right(), y + thickness, settings.color());
            return;
        }
        int dash = Math.max(2, thickness * 3);
        for (int x = r.x(); x < r.right(); x += dash * 2) {
            graphics.fill(x, y, Math.min(r.right(), x + dash), y + thickness, settings.color());
        }
    }

    private static void drawText(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, String text, ProjectionComponentSettings.Text settings, float scale) {
        if (settings.orientation() == ProjectionComponentSettings.TextOrientation.ROTATE_CW || settings.orientation() == ProjectionComponentSettings.TextOrientation.ROTATE_CCW) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(r.x() + r.width() * 0.5F, r.y() + r.height() * 0.5F);
            graphics.pose().rotate((float) Math.toRadians(settings.orientation() == ProjectionComponentSettings.TextOrientation.ROTATE_CW ? 90.0D : -90.0D));
            SPSGui.Rect rotated = new SPSGui.Rect(-r.height() / 2, -r.width() / 2, r.height(), r.width());
            drawHorizontalText(graphics, font, rotated, text, settings, scale, false);
            graphics.pose().popMatrix();
            return;
        }
        if (settings.orientation() == ProjectionComponentSettings.TextOrientation.VERTICAL_STACK) {
            drawVerticalStackText(graphics, font, r, text, settings, scale);
            return;
        }
        drawHorizontalText(graphics, font, r, text, settings, scale, true);
    }

    private static void drawHorizontalText(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, String text, ProjectionComponentSettings.Text settings, float scale, boolean clipToRect) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return;
        }
        int padding = Math.max(1, Math.round(Math.min(r.width(), r.height()) * 0.05F));
        int maxWidth = Math.max(1, r.width() - padding * 2);
        float textScale = Math.max(0.04F, settings.fontSize() * scale / Math.max(1.0F, font.lineHeight));
        if (settings.overflow() == ProjectionOverflowMode.WRAP) {
            drawWrappedText(graphics, font, r, value, settings, textScale, maxWidth);
            return;
        }
        boolean overflow = font.width(value) * textScale > maxWidth;
        if (settings.overflow() == ProjectionOverflowMode.HIDE && overflow) {
            return;
        }
        if (settings.overflow() == ProjectionOverflowMode.SCALE && overflow) {
            textScale = Math.max(0.04F, Math.min(textScale, maxWidth / Math.max(1.0F, font.width(value))));
        }
        if (settings.overflow() == ProjectionOverflowMode.MARQUEE && overflow) {
            int y = r.y() + (r.height() - Math.round(font.lineHeight * textScale)) / 2;
            if (clipToRect) {
                drawSmoothMarquee(graphics, font, value, r.x() + padding, y, maxWidth, r.height(), textScale, settings.textColor(), value.hashCode());
            } else {
                ProjectionTextScroller.TextWindow window = ProjectionTextScroller.window(font, value, maxWidth / Math.max(0.001F, textScale), value.hashCode());
                SPSGui.smallText(graphics, font, window.text(), Math.round(r.x() + padding - window.leadingOffset() * textScale), y, settings.textColor(), textScale);
            }
            return;
        }
        String rendered = settings.overflow() == ProjectionOverflowMode.PLUS_COUNT && overflow
                ? fittedLabel(font, value, Math.round(maxWidth / textScale))
                : value;
        int textWidth = Math.round(font.width(rendered) * textScale);
        int x = switch (settings.align()) {
            case CENTER -> r.x() + (r.width() - textWidth) / 2;
            case RIGHT -> r.right() - textWidth - padding;
            case LEFT -> r.x() + padding;
        };
        int y = r.y() + (r.height() - Math.round(font.lineHeight * textScale)) / 2;
        SPSGui.smallText(graphics, font, rendered, x, y, settings.textColor(), textScale);
    }

    private static void drawVerticalStackText(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, String text, ProjectionComponentSettings.Text settings, float scale) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return;
        }
        List<String> glyphs = new ArrayList<>();
        int maxGlyphWidth = 1;
        int index = 0;
        while (index < value.length()) {
            int codePoint = value.codePointAt(index);
            String glyph = new String(Character.toChars(codePoint));
            glyphs.add(glyph);
            maxGlyphWidth = Math.max(maxGlyphWidth, font.width(glyph));
            index += Character.charCount(codePoint);
        }
        if (glyphs.isEmpty()) {
            return;
        }
        float preferredScale = Math.max(0.04F, settings.fontSize() * scale / Math.max(1.0F, font.lineHeight));
        float lineGap = Math.max(0.0F, settings.lineSpacing() * scale);
        float totalUnits = glyphs.size() * font.lineHeight + Math.max(0, glyphs.size() - 1) * (lineGap / Math.max(0.001F, preferredScale));
        float fitWidth = r.width() * 0.86F / Math.max(1.0F, maxGlyphWidth);
        float fitHeight = r.height() * 0.94F / Math.max(1.0F, totalUnits);
        float textScale = Math.max(0.04F, Math.min(preferredScale, Math.min(fitWidth, fitHeight)));
        float scaledGap = Math.max(0.0F, settings.lineSpacing() * scale);
        float totalHeight = glyphs.size() * font.lineHeight * textScale + Math.max(0, glyphs.size() - 1) * scaledGap;
        float y = r.y() + (r.height() - totalHeight) * 0.5F;
        for (String glyph : glyphs) {
            int glyphWidth = Math.round(font.width(glyph) * textScale);
            int x = switch (settings.align()) {
                case CENTER -> r.x() + (r.width() - glyphWidth) / 2;
                case RIGHT -> r.right() - glyphWidth - Math.max(1, Math.round(r.width() * 0.06F));
                case LEFT -> r.x() + Math.max(1, Math.round(r.width() * 0.06F));
            };
            SPSGui.smallText(graphics, font, glyph, x, Math.round(y), settings.textColor(), textScale);
            y += font.lineHeight * textScale + scaledGap;
        }
    }

    private static void drawWrappedText(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, String value, ProjectionComponentSettings.Text settings, float scale, int maxWidth) {
        List<String> lines = wrapLines(font, value, Math.max(1, Math.round(maxWidth / scale)), settings.maxLines());
        int lineSpacing = Math.round(settings.lineSpacing() * scale);
        int totalHeight = Math.round(lines.size() * font.lineHeight * scale + Math.max(0, lines.size() - 1) * lineSpacing);
        int y = r.y() + Math.max(0, (r.height() - totalHeight) / 2);
        for (String line : lines) {
            int textWidth = Math.round(font.width(line) * scale);
            int x = switch (settings.align()) {
                case CENTER -> r.x() + (r.width() - textWidth) / 2;
                case RIGHT -> r.right() - textWidth - 2;
                case LEFT -> r.x() + 2;
            };
            SPSGui.smallText(graphics, font, line, x, y, settings.textColor(), scale);
            y += Math.round(font.lineHeight * scale) + lineSpacing;
        }
    }

    private static void drawRouteList(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, List<ProjectionPreviewScenario.RoutePreview> routes, ProjectionComponentSettings.RouteList settings, float scale) {
        RouteWindow<ProjectionPreviewScenario.RoutePreview> window = routeWindow(routes, settings.maxVisible(), settings.overflow(), settings.rotateIntervalTicks(), r.hashCode());
        int row = Math.max(1, Math.round(settings.rowHeight() * scale));
        int gap = Math.max(0, Math.round(settings.gap() * scale));
        int stripe = Math.max(1, Math.round(settings.stripeWidth() * scale));
        float textScale = Math.max(0.04F, settings.fontSize() * scale / Math.max(1.0F, font.lineHeight));
        int y = r.y();
        int drawn = 0;
        for (ProjectionPreviewScenario.RoutePreview route : window.items()) {
            if (y + row > r.bottom()) {
                break;
            }
            drawRouteColorBlocks(graphics, new SPSGui.Rect(r.x(), y, stripe, row), route.colors(), false);
            String label = route.name();
            int textX = r.x() + stripe + 4;
            int textY = Math.round(y + (row - font.lineHeight * textScale) * 0.5F);
            int maxTextPixels = Math.max(1, r.right() - textX);
            if (settings.labelOverflow() == ProjectionOverflowMode.MARQUEE && font.width(label) * textScale > maxTextPixels) {
                drawSmoothMarquee(graphics, font, label, textX, textY, maxTextPixels, row, textScale, settings.textColor(), label.hashCode());
            } else {
                int maxText = Math.max(1, Math.round(maxTextPixels / textScale));
                label = fitRouteLabel(font, label, maxText, settings.labelOverflow());
                SPSGui.smallText(graphics, font, label, textX, textY, settings.textColor(), textScale);
            }
            y += row + gap;
            drawn++;
        }
        drawPlusCount(graphics, font, r, window.hidden() + Math.max(0, window.items().size() - drawn), settings.plusTextColor(), scale, settings.overflow());
    }

    private static void drawRouteText(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, List<ProjectionPreviewScenario.RoutePreview> routes, ProjectionComponentSettings.RouteText settings, float scale) {
        RouteWindow<ProjectionPreviewScenario.RoutePreview> window = routeWindow(routes, 1, settings.overflow(), settings.rotateIntervalTicks(), r.hashCode());
        if (window.items().isEmpty()) {
            return;
        }
        ProjectionPreviewScenario.RoutePreview route = window.items().getFirst();
        String label = settings.shortName() ? shortRoute(route.name()) : route.name();
        ProjectionComponentSettings.Text text = new ProjectionComponentSettings.Text(ProjectionComponentType.CUSTOM_TEXT, "", "", settings.textColor(), settings.fontSize(), settings.align(), ProjectionOverflowMode.MARQUEE, 0.02F, 1);
        drawText(graphics, font, r, label, text, scale);
        drawPlusCount(graphics, font, r, window.hidden(), settings.plusTextColor(), scale, settings.overflow());
    }

    private static void drawRouteIcons(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, List<ProjectionPreviewScenario.RoutePreview> routes, ProjectionComponentSettings.RouteIcon settings, boolean outline, float scale) {
        int size = Math.max(1, Math.round(settings.iconSize() * scale));
        int gap = Math.max(0, Math.round(settings.gap() * scale));
        List<SPSGui.Rect> cells = iconCells(r, settings, size, gap);
        if (cells.isEmpty()) {
            return;
        }
        int cellCapacity = Math.min(settings.maxVisible(), cells.size());
        boolean reservePlusCell = settings.overflow() == ProjectionComponentSettings.RouteOverflowMode.PLUS_COUNT && routes != null && routes.size() > cellCapacity;
        int routeSlots = Math.max(0, reservePlusCell ? cellCapacity - 1 : cellCapacity);
        RouteWindow<ProjectionPreviewScenario.RoutePreview> window = routeWindow(routes, routeSlots, settings.overflow(), settings.rotateIntervalTicks(), r.hashCode());
        int border = settings.borderWidth() > 0.0005F ? Math.max(1, Math.round(settings.borderWidth() * scale)) : 0;
        int ring = Math.max(1, Math.round(size * settings.ringThicknessRatio()));
        float textScale = Math.max(0.04F, settings.fontSize() * scale / Math.max(1.0F, font.lineHeight));
        int drawn = 0;
        for (int i = 0; i < window.items().size() && i < cells.size(); i++) {
            SPSGui.Rect cell = cells.get(i);
            if (cell.right() > r.right() || cell.bottom() > r.bottom()) {
                break;
            }
            ProjectionPreviewScenario.RoutePreview route = window.items().get(i);
            int color = firstColor(route.colors());
            drawIconShape(graphics, cell, settings.shape(), outline, color, settings.borderColor(), border, ring);
            if (settings.showLabel()) {
                int textColor = outline ? settings.textColor() : contrast(color);
                drawScaledText(graphics, font, shortRoute(route.name()), cell.x() + cell.width() * 0.5F, cell.y() + cell.height() * 0.5F, textScale, textColor);
            }
            drawn++;
        }
        int hidden = window.hidden() + Math.max(0, window.items().size() - drawn);
        if (reservePlusCell && drawn < cells.size()) {
            drawPlusIconCell(graphics, font, cells.get(drawn), "+" + Math.max(1, hidden), settings.plusTextColor(), textScale, settings.shape(), outline);
        } else {
            drawPlusCount(graphics, font, r, hidden, settings.plusTextColor(), scale, settings.overflow());
        }
    }

    private static List<SPSGui.Rect> iconCells(SPSGui.Rect r, ProjectionComponentSettings.RouteIcon settings, int size, int gap) {
        if (size <= 0 || r.width() <= 0 || r.height() <= 0) {
            return List.of();
        }
        int columnsPossible = Math.max(0, (r.width() + gap) / Math.max(1, size + gap));
        int rowsPossible = Math.max(0, (r.height() + gap) / Math.max(1, size + gap));
        if (columnsPossible <= 0 || rowsPossible <= 0) {
            return List.of();
        }
        int columns;
        int rows;
        if (settings.flow() == ProjectionComponentSettings.FlowDirection.HORIZONTAL) {
            rows = settings.wrapEnabled() ? Math.min(settings.wrapTracks(), rowsPossible) : 1;
            columns = columnsPossible;
        } else {
            columns = settings.wrapEnabled() ? Math.min(settings.wrapTracks(), columnsPossible) : 1;
            rows = rowsPossible;
        }
        int maxCells = Math.min(settings.maxVisible(), Math.max(0, columns * rows));
        List<SPSGui.Rect> cells = new ArrayList<>(maxCells);
        if (settings.flow() == ProjectionComponentSettings.FlowDirection.HORIZONTAL) {
            for (int row = 0; row < rows && cells.size() < maxCells; row++) {
                for (int column = 0; column < columns && cells.size() < maxCells; column++) {
                    cells.add(new SPSGui.Rect(r.x() + column * (size + gap), r.y() + row * (size + gap), size, size));
                }
            }
        } else {
            for (int column = 0; column < columns && cells.size() < maxCells; column++) {
                for (int row = 0; row < rows && cells.size() < maxCells; row++) {
                    cells.add(new SPSGui.Rect(r.x() + column * (size + gap), r.y() + row * (size + gap), size, size));
                }
            }
        }
        return List.copyOf(cells);
    }

    private static void drawPlusIconCell(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect cell, String label, int color, float textScale, ProjectionComponentSettings.IconShape shape, boolean outline) {
        int fill = outline ? color : 0xAA10151A;
        drawIconShape(graphics, cell, shape, outline, fill, color, 0, Math.max(1, Math.round(cell.width() * 0.16F)));
        float fittedScale = Math.min(textScale, Math.min(cell.width() * 0.76F / Math.max(1.0F, font.width(label)), cell.height() * 0.76F / Math.max(1.0F, font.lineHeight)));
        drawScaledText(graphics, font, label, cell.x() + cell.width() * 0.5F, cell.y() + cell.height() * 0.5F, Math.max(0.04F, fittedScale), color);
    }

    private static void drawRouteCapsules(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, List<ProjectionPreviewScenario.RoutePreview> routes, ProjectionComponentSettings.RouteCapsules settings, float scale) {
        RouteWindow<ProjectionPreviewScenario.RoutePreview> window = routeWindow(routes, settings.maxVisible(), settings.overflow(), settings.rotateIntervalTicks(), r.hashCode());
        int width = Math.max(1, Math.round(settings.capsuleWidth() * scale));
        int height = Math.max(1, Math.round(settings.capsuleHeight() * scale));
        int gap = Math.max(0, Math.round(settings.gap() * scale));
        float textScale = Math.max(0.04F, settings.fontSize() * scale / Math.max(1.0F, font.lineHeight));
        int drawn = 0;
        for (int i = 0; i < window.items().size(); i++) {
            int x = settings.flow() == ProjectionComponentSettings.FlowDirection.HORIZONTAL ? r.x() + i * (width + gap) : r.x();
            int y = settings.flow() == ProjectionComponentSettings.FlowDirection.HORIZONTAL ? r.y() : r.y() + i * (height + gap);
            if (x + width > r.right() || y + height > r.bottom()) {
                break;
            }
            ProjectionPreviewScenario.RoutePreview route = window.items().get(i);
            graphics.fill(x, y, x + width, y + height, settings.fillColor());
            drawRouteColorBlocks(graphics, new SPSGui.Rect(x, y, Math.max(1, Math.round(height * 0.18F)), height), route.colors(), false);
            String label = settings.showShortName() ? shortRoute(route.name()) : route.name();
            int labelX = x + Math.max(3, Math.round(height * 0.26F));
            int labelWidth = width - Math.max(3, Math.round(height * 0.26F));
            drawCapsuleLabel(graphics, font, label, labelX, y, labelWidth, height, textScale, settings.textColor(), settings.contentOrientation(), settings.labelOverflow());
            drawn++;
        }
        drawPlusCount(graphics, font, r, window.hidden() + Math.max(0, window.items().size() - drawn), settings.plusTextColor(), scale, settings.overflow());
    }

    private static void drawRouteBackplate(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, List<ProjectionPreviewScenario.RoutePreview> routes, ProjectionComponentSettings.RouteBackplate settings, float scale) {
        RouteWindow<ProjectionPreviewScenario.RoutePreview> window = routeWindow(routes, settings.maxVisible(), settings.overflow(), settings.rotateIntervalTicks(), r.hashCode());
        boolean horizontal = settings.direction() == ProjectionComponentSettings.StripeDirection.HORIZONTAL;
        drawRouteBands(graphics, r, window.items(), settings.colorPolicy(), horizontal, settings.opacity());
        drawPlusCount(graphics, font, r, window.hidden(), settings.plusTextColor(), scale, settings.overflow());
    }

    private static void drawCapsuleLabel(GuiGraphicsExtractor graphics, Font font, String label, int x, int y, int width, int height, float textScale, int color, ProjectionComponentSettings.CapsuleContentOrientation orientation, ProjectionOverflowMode overflow) {
        int maxText = Math.max(1, Math.round(width / textScale));
        boolean overflows = font.width(label) > maxText;
        if (orientation == ProjectionComponentSettings.CapsuleContentOrientation.HORIZONTAL) {
            int textY = Math.round(y + (height - font.lineHeight * textScale) * 0.5F);
            if (overflow == ProjectionOverflowMode.MARQUEE && overflows) {
                drawSmoothMarquee(graphics, font, label, x, textY, Math.max(1, width), height, textScale, color, label.hashCode());
            } else {
                SPSGui.smallText(graphics, font, fitRouteLabel(font, label, maxText, overflow), x, textY, color, textScale);
            }
            return;
        }
        String rendered = overflow == ProjectionOverflowMode.MARQUEE && overflows
                ? ProjectionTextScroller.window(font, label, maxText, label.hashCode()).text()
                : fitRouteLabel(font, label, maxText, overflow);
        graphics.pose().pushMatrix();
        float cx = x + width * 0.5F;
        float cy = y + height * 0.5F;
        graphics.pose().translate(cx, cy);
        graphics.pose().rotate((float) Math.toRadians(orientation == ProjectionComponentSettings.CapsuleContentOrientation.ROTATE_CW ? 90.0D : -90.0D));
        int textWidth = Math.round(font.width(rendered) * textScale);
        SPSGui.smallText(graphics, font, rendered, -textWidth / 2, Math.round(-font.lineHeight * textScale * 0.5F), color, textScale);
        graphics.pose().popMatrix();
    }

    private static void drawIconShape(GuiGraphicsExtractor graphics, SPSGui.Rect r, ProjectionComponentSettings.IconShape shape, boolean outline, int fill, int border, int borderWidth, int ringWidth) {
        int limit = Math.max(1, Math.min(r.width(), r.height()) / 3);
        int bw = borderWidth > 0 ? Math.max(1, Math.min(borderWidth, limit)) : 0;
        int rw = Math.max(1, Math.min(ringWidth, limit));
        if (shape == ProjectionComponentSettings.IconShape.SQUARE) {
            if (!outline) {
                graphics.fill(r.x(), r.y(), r.right(), r.bottom(), fill);
                if ((border >>> 24) > 0 && bw > 0) {
                    drawBorder(graphics, r, bw, border);
                }
            } else {
                if ((border >>> 24) > 0 && bw > 0) {
                    drawBorder(graphics, r, bw, border);
                }
                drawBorder(graphics, inset(r, bw), rw, fill);
            }
            return;
        }
        if (!outline) {
            drawSmoothCircle(graphics, r, fill);
            if ((border >>> 24) > 0 && bw > 0) {
                drawSmoothCircleRing(graphics, r, bw, border);
            }
        } else {
            if ((border >>> 24) > 0 && bw > 0) {
                drawSmoothCircleRing(graphics, r, bw, border);
            }
            drawSmoothCircleRing(graphics, inset(r, bw), rw, fill);
        }
    }

    private static void drawCapsule(GuiGraphicsExtractor graphics, SPSGui.Rect r, int color) {
        if (r.width() == r.height()) {
            drawSmoothCircle(graphics, r, color);
            return;
        }
        SmoothGuiPrimitives.capsule(graphics, new Vec2(r.x() + r.width() * 0.5D, r.y() + r.height() * 0.5D), r.width(), r.height(), color);
    }

    private static void drawSmoothCircle(GuiGraphicsExtractor graphics, SPSGui.Rect r, int color) {
        double radius = Math.max(0.5D, Math.min(r.width(), r.height()) * 0.5D);
        drawSmoothCircleAt(graphics, r.x() + r.width() * 0.5D, r.y() + r.height() * 0.5D, radius, color);
    }

    private static void drawSmoothCircleRing(GuiGraphicsExtractor graphics, SPSGui.Rect r, int thickness, int color) {
        double radius = Math.max(0.5D, Math.min(r.width(), r.height()) * 0.5D);
        drawSmoothCircleRingAt(graphics, r.x() + r.width() * 0.5D, r.y() + r.height() * 0.5D, radius, Math.max(1, thickness), color);
    }

    private static void drawSmoothCircleAt(GuiGraphicsExtractor graphics, double centerX, double centerY, double radius, int color) {
        SmoothGuiPrimitives.circle(graphics, new Vec2(centerX, centerY), radius, color);
    }

    private static void drawSmoothCircleRingAt(GuiGraphicsExtractor graphics, double centerX, double centerY, double radius, double thickness, int color) {
        SmoothGuiPrimitives.ring(graphics, new Vec2(centerX, centerY), radius, thickness, color);
    }

    private static SPSGui.Rect inset(SPSGui.Rect r, int inset) {
        int safe = Math.max(0, Math.min(inset, Math.max(0, Math.min(r.width(), r.height()) / 2 - 1)));
        return new SPSGui.Rect(r.x() + safe, r.y() + safe, Math.max(1, r.width() - safe * 2), Math.max(1, r.height() - safe * 2));
    }

    private static void drawFilledCircle(GuiGraphicsExtractor graphics, SPSGui.Rect r, int color) {
        double cx = r.x() + r.width() * 0.5D;
        double cy = r.y() + r.height() * 0.5D;
        double rx = Math.max(0.5D, r.width() * 0.5D);
        double ry = Math.max(0.5D, r.height() * 0.5D);
        for (int y = r.y(); y < r.bottom(); y++) {
            double dy = ((y + 0.5D) - cy) / ry;
            double half = Math.sqrt(Math.max(0.0D, 1.0D - dy * dy)) * rx;
            int x0 = (int) Math.ceil(cx - half);
            int x1 = (int) Math.floor(cx + half);
            if (x1 >= x0) {
                graphics.fill(Math.max(r.x(), x0), y, Math.min(r.right(), x1 + 1), y + 1, color);
            }
        }
    }

    private static void drawCircleRing(GuiGraphicsExtractor graphics, SPSGui.Rect r, int thickness, int color) {
        int t = Math.max(1, Math.min(thickness, Math.max(1, Math.min(r.width(), r.height()) / 2)));
        SPSGui.Rect inner = inset(r, t);
        double cx = r.x() + r.width() * 0.5D;
        double cy = r.y() + r.height() * 0.5D;
        double rx = Math.max(0.5D, r.width() * 0.5D);
        double ry = Math.max(0.5D, r.height() * 0.5D);
        double innerRx = Math.max(0.0D, inner.width() * 0.5D);
        double innerRy = Math.max(0.0D, inner.height() * 0.5D);
        for (int y = r.y(); y < r.bottom(); y++) {
            double outerDy = ((y + 0.5D) - cy) / ry;
            double outerHalf = Math.sqrt(Math.max(0.0D, 1.0D - outerDy * outerDy)) * rx;
            int outerX0 = (int) Math.ceil(cx - outerHalf);
            int outerX1 = (int) Math.floor(cx + outerHalf);
            if (outerX1 < outerX0) {
                continue;
            }
            double innerDy = innerRy <= 0.0D ? 2.0D : ((y + 0.5D) - cy) / innerRy;
            if (Math.abs(innerDy) >= 1.0D || innerRx <= 0.0D) {
                graphics.fill(Math.max(r.x(), outerX0), y, Math.min(r.right(), outerX1 + 1), y + 1, color);
                continue;
            }
            double innerHalf = Math.sqrt(Math.max(0.0D, 1.0D - innerDy * innerDy)) * innerRx;
            int innerX0 = (int) Math.floor(cx - innerHalf);
            int innerX1 = (int) Math.ceil(cx + innerHalf);
            int left0 = Math.max(r.x(), outerX0);
            int left1 = Math.min(r.right(), innerX0);
            int right0 = Math.max(r.x(), innerX1);
            int right1 = Math.min(r.right(), outerX1 + 1);
            if (left1 > left0) {
                graphics.fill(left0, y, left1, y + 1, color);
            }
            if (right1 > right0) {
                graphics.fill(right0, y, right1, y + 1, color);
            }
        }
    }

    private static void drawRouteBands(GuiGraphicsExtractor graphics, SPSGui.Rect r, List<ProjectionPreviewScenario.RoutePreview> routes, ProjectionComponentSettings.ColorPolicy colorPolicy, boolean horizontal) {
        drawRouteBands(graphics, r, routes, colorPolicy, horizontal, 1.0F);
    }

    private static void drawRouteBands(GuiGraphicsExtractor graphics, SPSGui.Rect r, List<ProjectionPreviewScenario.RoutePreview> routes, ProjectionComponentSettings.ColorPolicy colorPolicy, boolean horizontal, float opacity) {
        List<ProjectionPreviewScenario.RoutePreview> normalized = routes == null || routes.isEmpty()
                ? List.of(new ProjectionPreviewScenario.RoutePreview("Line", List.of(FALLBACK_ROUTE_COLOR)))
                : routes;
        if (colorPolicy == ProjectionComponentSettings.ColorPolicy.FIRST_ROUTE) {
            drawRouteColorBlocks(graphics, r, applyOpacity(normalized.getFirst().colors(), opacity), horizontal);
            return;
        }
        int count = normalized.size();
        for (int i = 0; i < count; i++) {
            SPSGui.Rect segment;
            if (horizontal) {
                int x0 = r.x() + Math.round(r.width() * i / (float) count);
                int x1 = r.x() + Math.round(r.width() * (i + 1) / (float) count);
                segment = new SPSGui.Rect(x0, r.y(), Math.max(1, x1 - x0), r.height());
            } else {
                int y0 = r.y() + Math.round(r.height() * i / (float) count);
                int y1 = r.y() + Math.round(r.height() * (i + 1) / (float) count);
                segment = new SPSGui.Rect(r.x(), y0, r.width(), Math.max(1, y1 - y0));
            }
            drawRouteColorBlocks(graphics, segment, applyOpacity(normalized.get(i).colors(), opacity), horizontal);
        }
    }

    private static void drawRouteColorBlocks(GuiGraphicsExtractor graphics, SPSGui.Rect r, List<Integer> colors, boolean horizontal) {
        List<Integer> normalized = colors == null || colors.isEmpty() ? List.of(FALLBACK_ROUTE_COLOR) : colors;
        if (horizontal) {
            for (int i = 0; i < normalized.size(); i++) {
                int x0 = r.x() + Math.round(r.width() * i / (float) normalized.size());
                int x1 = r.x() + Math.round(r.width() * (i + 1) / (float) normalized.size());
                graphics.fill(x0, r.y(), i == normalized.size() - 1 ? r.right() : x1, r.bottom(), normalized.get(i));
            }
        } else {
            for (int i = 0; i < normalized.size(); i++) {
                int y0 = r.y() + Math.round(r.height() * i / (float) normalized.size());
                int y1 = r.y() + Math.round(r.height() * (i + 1) / (float) normalized.size());
                graphics.fill(r.x(), y0, r.right(), i == normalized.size() - 1 ? r.bottom() : y1, normalized.get(i));
            }
        }
    }

    private static void drawPlusCount(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect r, int extra, int color, float scale, ProjectionComponentSettings.RouteOverflowMode overflow) {
        if (extra <= 0 || overflow != ProjectionComponentSettings.RouteOverflowMode.PLUS_COUNT) {
            return;
        }
        float textScale = Math.max(0.04F, 0.055F * scale / Math.max(1.0F, font.lineHeight));
        String label = "+" + extra;
        SPSGui.smallText(graphics, font, label, r.right() - Math.round(font.width(label) * textScale) - 2, r.bottom() - Math.round(font.lineHeight * textScale) - 1, color, textScale);
    }

    private static RouteWindow<ProjectionPreviewScenario.RoutePreview> routeWindow(List<ProjectionPreviewScenario.RoutePreview> routes, int maxVisible, ProjectionComponentSettings.RouteOverflowMode overflow, int intervalTicks, int seed) {
        if (routes == null || routes.isEmpty()) {
            return new RouteWindow<>(List.of(), 0);
        }
        if (maxVisible <= 0) {
            return new RouteWindow<>(List.of(), routes.size());
        }
        int visible = Math.max(1, Math.min(maxVisible, routes.size()));
        if (overflow == ProjectionComponentSettings.RouteOverflowMode.ROTATE && routes.size() > visible) {
            long intervalMs = Math.max(10L, intervalTicks) * 50L;
            int start = Math.floorMod((int) (System.currentTimeMillis() / intervalMs) + seed, routes.size());
            List<ProjectionPreviewScenario.RoutePreview> result = new ArrayList<>(visible);
            for (int i = 0; i < visible; i++) {
                result.add(routes.get((start + i) % routes.size()));
            }
            return new RouteWindow<>(List.copyOf(result), 0);
        }
        return new RouteWindow<>(routes.subList(0, visible), routes.size() - visible);
    }

    private static String fitRouteLabel(Font font, String label, int maxText, ProjectionOverflowMode overflow) {
        return switch (overflow) {
            case HIDE -> font.width(label) > maxText ? "" : label;
            case SCALE, PLUS_COUNT -> fittedLabel(font, label, maxText);
            case MARQUEE -> font.width(label) > maxText ? ProjectionTextScroller.window(font, label, maxText, label.hashCode()).text() : label;
            case WRAP -> fittedLabel(font, label, maxText);
        };
    }

    private static void drawBorder(GuiGraphicsExtractor graphics, SPSGui.Rect r, int border, int color) {
        graphics.fill(r.x(), r.y(), r.right(), r.y() + border, color);
        graphics.fill(r.x(), r.bottom() - border, r.right(), r.bottom(), color);
        graphics.fill(r.x(), r.y(), r.x() + border, r.bottom(), color);
        graphics.fill(r.right() - border, r.y(), r.right(), r.bottom(), color);
    }

    private static boolean visible(ProjectionComponent component, ProjectionPreviewScenario data) {
        return switch (component.visibleCondition()) {
            case ALWAYS -> true;
            case HAS_TRANSLATION -> !data.renderedTranslationName().isBlank();
            case HAS_ROUTES -> !data.routes().isEmpty();
            case HAS_EXIT -> !data.renderedExitLabel().isBlank();
            case MULTI_ROUTE -> data.routes().size() > 1;
            case HAS_PLATFORM -> true;
            case HAS_PLATFORM_DISPLAY_NAME -> !data.renderedPlatformName().isBlank();
            case HAS_CURRENT_LINE -> !data.renderedLineName().isBlank();
            case HAS_ROUTE_LAYOUT -> true;
            case HAS_NEXT_STOP -> !data.renderedNextStop().isBlank();
            case HAS_PREVIOUS_STOP -> !data.renderedPreviousStop().isBlank();
            case HAS_TERMINAL -> data.platformTerminal() && !data.renderedTerminalStop().isBlank();
            case HAS_TRANSFERS -> !data.transferRoutes().isEmpty();
            case HAS_OUT_OF_STATION_TRANSFERS -> data.outStationTransfers() && !data.transferRoutes().isEmpty();
            case MULTI_TRANSFER -> data.transferRoutes().size() > 1;
            case BIDIRECTIONAL_LAYOUT -> data.platformBidirectional();
            case LOOP_LAYOUT -> data.platformLoop();
        };
    }

    private static String shortRoute(String value) {
        String normalized = value == null || value.isBlank() ? "?" : value.trim();
        return normalized.length() <= 3 ? normalized : normalized.substring(0, 3);
    }

    private static String arrowText(ProjectionComponentSettings.ArrowDirection direction, boolean forward) {
        return switch (direction == null ? ProjectionComponentSettings.ArrowDirection.AUTO : direction) {
            case NONE -> "";
            case LEFT -> "<";
            case RIGHT -> ">";
            case BOTH -> "<>";
            case AUTO -> forward ? ">" : "<";
        };
    }

    private static String platformPrefix(ProjectionComponentSettings.PlatformDirectionPrefix prefix) {
        return switch (prefix == null ? ProjectionComponentSettings.PlatformDirectionPrefix.TOWARDS : prefix) {
            case NONE -> "";
            case TOWARDS -> Component.translatable("screen.superpipeslide.platform_projector.towards").getString();
            case NEXT_STOP -> Component.translatable("screen.superpipeslide.projection_designer.platform.next_stop_prefix").getString();
            case PREVIOUS_STOP -> Component.translatable("screen.superpipeslide.projection_designer.platform.previous_stop_prefix").getString();
            case TERMINAL -> Component.translatable("screen.superpipeslide.projection_designer.platform.terminal_prefix").getString();
            case ORIGIN -> Component.translatable("screen.superpipeslide.projection_designer.platform.origin_prefix").getString();
        };
    }

    private static void drawPlatformNode(GuiGraphicsExtractor graphics, SPSGui.Rect rect, ProjectionComponentSettings.PlatformNodeStyle style, int color, int thickness) {
        if (style == ProjectionComponentSettings.PlatformNodeStyle.NONE) {
            return;
        }
        if (style == ProjectionComponentSettings.PlatformNodeStyle.OUTLINE) {
            drawSmoothCircleRing(graphics, rect, Math.max(1, thickness), color);
            return;
        }
        drawSmoothCircle(graphics, rect, color);
    }

    private static List<PlatformTransferProjectionEngine.TransferData> previewTransfers(List<ProjectionPreviewScenario.RoutePreview> routes) {
        if (routes == null || routes.isEmpty()) {
            return List.of();
        }
        List<PlatformTransferProjectionEngine.TransferData> result = new ArrayList<>(routes.size());
        for (ProjectionPreviewScenario.RoutePreview route : routes) {
            result.add(new PlatformTransferProjectionEngine.TransferData(null, route.name(), route.station(), route.platform(), route.outStation(), route.colors()));
        }
        return List.copyOf(result);
    }

    private static String fittedLabel(Font font, String value, int maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }
        return SPSGui.ellipsize(font, value, maxWidth);
    }

    private static void drawSmoothMarquee(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int width, int height, float scale, int color, int seed) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return;
        }
        int safeWidth = Math.max(1, width);
        float offset = ProjectionTextScroller.offset(font, value, safeWidth / Math.max(0.001F, scale), seed);
        graphics.enableScissor(x, y - Math.max(1, Math.round(font.lineHeight * scale * 0.25F)), x + safeWidth, y + Math.max(1, height));
        SPSGui.smallText(graphics, font, value, Math.round(x - offset * scale), y, color, scale);
        graphics.disableScissor();
    }

    private static void drawScaledText(GuiGraphicsExtractor graphics, Font font, String text, float centerX, float centerY, float scale, int color) {
        float width = font.width(text) * scale;
        float x = centerX - width * 0.5F;
        float y = centerY - font.lineHeight * scale * 0.5F;
        SPSGui.smallText(graphics, font, text, Math.round(x), Math.round(y), color, scale);
    }

    private static List<String> wrapLines(Font font, String value, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        int index = 0;
        while (index < value.length() && lines.size() < maxLines) {
            int lineEnd = fitEnd(font, value, index, maxWidth);
            if (lineEnd <= index) {
                lineEnd = index + Character.charCount(value.codePointAt(index));
            }
            String line = value.substring(index, lineEnd).trim();
            if (lines.size() == maxLines - 1 && lineEnd < value.length()) {
                line = SPSGui.ellipsize(font, value.substring(index).trim(), maxWidth);
                lines.add(line);
                break;
            }
            lines.add(line);
            index = lineEnd;
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
                index++;
            }
        }
        return lines.isEmpty() ? List.of(value) : lines;
    }

    private static int fitEnd(Font font, String value, int start, int maxWidth) {
        int best = start;
        int lastSpace = -1;
        int index = start;
        while (index < value.length()) {
            int next = index + Character.charCount(value.codePointAt(index));
            if (Character.isWhitespace(value.charAt(index))) {
                lastSpace = next;
            }
            if (font.width(value.substring(start, next)) > maxWidth) {
                return lastSpace > start ? lastSpace : best;
            }
            best = next;
            index = next;
        }
        return best;
    }

    private static int firstColor(List<Integer> colors) {
        return colors == null || colors.isEmpty() ? FALLBACK_ROUTE_COLOR : colors.getFirst();
    }

    private static int contrast(int color) {
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 150 ? 0xFF111111 : 0xFFFFFFFF;
    }

    private static List<Integer> applyOpacity(List<Integer> colors, float opacity) {
        if (opacity >= 0.999F) {
            return colors == null || colors.isEmpty() ? List.of(FALLBACK_ROUTE_COLOR) : colors;
        }
        List<Integer> result = new ArrayList<>();
        for (int color : colors == null || colors.isEmpty() ? List.of(FALLBACK_ROUTE_COLOR) : colors) {
            result.add(withAlpha(color, Math.round(((color >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, opacity)))));
        }
        return List.copyOf(result);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static int withAlphaMultiplier(int color, float multiplier) {
        return withAlpha(color, Math.round(((color >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, multiplier))));
    }

    private record RouteWindow<T>(List<T> items, int hidden) {
    }
}
