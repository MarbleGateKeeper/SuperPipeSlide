package dev.marblegate.superpipeslide.common.core.projection.layout;


import dev.marblegate.superpipeslide.common.core.projection.component.ProjectionComponentSettings;
public final class ProjectionImageLayout {
    private ProjectionImageLayout() {
    }

    public static Resolved resolve(float x, float y, float width, float height, int imageWidth, int imageHeight, ProjectionComponentSettings.ImageFitMode fitMode, ProjectionComponentSettings.ImageAnchor anchor, float padding, float cropX, float cropY, float cropW, float cropH) {
        float safeWidth = Math.max(0.0001F, width - padding * 2.0F);
        float safeHeight = Math.max(0.0001F, height - padding * 2.0F);
        float areaX = x + padding;
        float areaY = y + padding;
        float imageRatio = imageWidth > 0 && imageHeight > 0 ? imageWidth / (float) imageHeight : 1.0F;
        ProjectionComponentSettings.ImageFitMode fit = fitMode == null ? ProjectionComponentSettings.ImageFitMode.CONTAIN : fitMode;
        ProjectionComponentSettings.ImageAnchor safeAnchor = anchor == null ? ProjectionComponentSettings.ImageAnchor.CENTER : anchor;
        float cx = clamp01(cropX);
        float cy = clamp01(cropY);
        float cw = Math.max(0.01F, Math.min(1.0F - cx, cropW));
        float ch = Math.max(0.01F, Math.min(1.0F - cy, cropH));
        float cropRatio = imageRatio * cw / ch;

        return switch (fit) {
            case STRETCH -> new Resolved(areaX, areaY, safeWidth, safeHeight, cx, cy, cx + cw, cy + ch);
            case CENTER -> {
                float naturalScale = Math.max(0.08F, Math.min(1.0F, 0.62F));
                float naturalBase = Math.min(safeWidth / Math.max(0.0001F, cropRatio), safeHeight) * naturalScale;
                float drawW = Math.min(safeWidth, naturalBase * cropRatio);
                float drawH = Math.min(safeHeight, naturalBase);
                yield aligned(areaX, areaY, safeWidth, safeHeight, safeAnchor, drawW, drawH, cx, cy, cx + cw, cy + ch);
            }
            case CONTAIN -> {
                float drawW = safeWidth;
                float drawH = drawW / Math.max(0.0001F, cropRatio);
                if (drawH > safeHeight) {
                    drawH = safeHeight;
                    drawW = drawH * cropRatio;
                }
                yield aligned(areaX, areaY, safeWidth, safeHeight, safeAnchor, drawW, drawH, cx, cy, cx + cw, cy + ch);
            }
            case COVER -> {
                float areaRatio = safeWidth / safeHeight;
                float u0 = cx;
                float v0 = cy;
                float u1 = cx + cw;
                float v1 = cy + ch;
                if (cropRatio > areaRatio) {
                    float visibleW = ch * areaRatio / imageRatio;
                    float extra = Math.max(0.0F, cw - visibleW);
                    float offset = extra * safeAnchor.xFactor();
                    u0 = cx + offset;
                    u1 = u0 + visibleW;
                } else if (cropRatio < areaRatio) {
                    float visibleH = cw * imageRatio / areaRatio;
                    float extra = Math.max(0.0F, ch - visibleH);
                    float offset = extra * safeAnchor.yFactor();
                    v0 = cy + offset;
                    v1 = v0 + visibleH;
                }
                yield new Resolved(areaX, areaY, safeWidth, safeHeight, u0, v0, u1, v1);
            }
            case TILE -> new Resolved(areaX, areaY, safeWidth, safeHeight, cx, cy, cx + cw, cy + ch);
        };
    }

    public static Resolved resolveIcon(float x, float y, float width, float height, ProjectionComponentSettings.ImageFitMode fitMode, ProjectionComponentSettings.ImageAnchor anchor, float padding, float imageScale) {
        ProjectionComponentSettings.ImageFitMode fit = fitMode == null ? ProjectionComponentSettings.ImageFitMode.CONTAIN : fitMode;
        if (fit != ProjectionComponentSettings.ImageFitMode.CENTER) {
            return resolve(x, y, width, height, 1, 1, fit, anchor, padding, 0.0F, 0.0F, 1.0F, 1.0F);
        }
        float safeWidth = Math.max(0.0001F, width - padding * 2.0F);
        float safeHeight = Math.max(0.0001F, height - padding * 2.0F);
        float areaX = x + padding;
        float areaY = y + padding;
        float scale = !Float.isFinite(imageScale) ? 0.62F : Math.max(0.08F, Math.min(3.0F, imageScale));
        float draw = Math.max(0.0001F, Math.min(safeWidth, safeHeight) * scale);
        draw = Math.min(draw, Math.min(safeWidth, safeHeight));
        return aligned(areaX, areaY, safeWidth, safeHeight, anchor == null ? ProjectionComponentSettings.ImageAnchor.CENTER : anchor, draw, draw, 0.0F, 0.0F, 1.0F, 1.0F);
    }

    public static TileGrid resolveIconTiles(float x, float y, float width, float height, ProjectionComponentSettings.ImageAnchor anchor, float padding, float imageScale, float tileGap) {
        float safeWidth = Math.max(0.0001F, width - padding * 2.0F);
        float safeHeight = Math.max(0.0001F, height - padding * 2.0F);
        float areaX = x + padding;
        float areaY = y + padding;
        float scale = !Float.isFinite(imageScale) ? 0.62F : Math.max(0.08F, Math.min(3.0F, imageScale));
        float tile = Math.max(0.0001F, Math.min(safeWidth, safeHeight) * scale);
        tile = Math.min(tile, Math.max(safeWidth, safeHeight));
        float gap = !Float.isFinite(tileGap) ? 0.0F : Math.max(0.0F, Math.min(0.50F, tileGap)) * Math.min(safeWidth, safeHeight);
        float step = Math.max(0.0001F, tile + gap);
        int columns = Math.max(1, Math.min(32, (int) Math.ceil((safeWidth + gap) / step)));
        int rows = Math.max(1, Math.min(32, (int) Math.ceil((safeHeight + gap) / step)));
        float totalWidth = columns * tile + (columns - 1) * gap;
        float totalHeight = rows * tile + (rows - 1) * gap;
        ProjectionComponentSettings.ImageAnchor safeAnchor = anchor == null ? ProjectionComponentSettings.ImageAnchor.CENTER : anchor;
        float startX = areaX + Math.min(0.0F, safeWidth - totalWidth) * safeAnchor.xFactor();
        float startY = areaY + Math.min(0.0F, safeHeight - totalHeight) * safeAnchor.yFactor();
        return new TileGrid(startX, startY, safeWidth, safeHeight, tile, gap, columns, rows);
    }

    private static Resolved aligned(float areaX, float areaY, float areaW, float areaH, ProjectionComponentSettings.ImageAnchor anchor, float drawW, float drawH, float u0, float v0, float u1, float v1) {
        float x = areaX + (areaW - drawW) * anchor.xFactor();
        float y = areaY + (areaH - drawH) * anchor.yFactor();
        return new Resolved(x, y, drawW, drawH, u0, v0, u1, v1);
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public record Resolved(float x, float y, float width, float height, float u0, float v0, float u1, float v1) {
    }

    public record TileGrid(float x, float y, float width, float height, float tileSize, float gap, int columns, int rows) {
    }
}
