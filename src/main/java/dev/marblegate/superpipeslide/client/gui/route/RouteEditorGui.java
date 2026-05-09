package dev.marblegate.superpipeslide.client.gui.route;


import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class RouteEditorGui {
    public static final int BOARD = 0xFF6D5438;
    public static final int BOARD_DARK = 0xFF3E2C1D;
    public static final int BOARD_LIGHT = 0xFF8D704B;
    public static final int PAPER = 0xFFF7F1E3;
    public static final int PAPER_ELEVATED = 0xFFFFFBF1;
    public static final int PAPER_RECESSED = 0xFFEDE2CB;
    public static final int PAPER_LINE = 0xFFD5C4A4;
    public static final int PAPER_RULE = 0x30A68E63;
    public static final int BLUEPRINT = 0xFFEAF3F4;
    public static final int BLUEPRINT_GRID = 0x2C6E91A6;
    public static final int CLIP = 0xFFC2C8CC;
    public static final int CLIP_DARK = 0xFF778087;
    public static final int CLIP_LIGHT = 0xFFE7ECEF;
    public static final int INK_PRIMARY = 0xFF27302A;
    public static final int INK_SECONDARY = 0xFF5D594C;
    public static final int INK_MUTED = 0xFF877C68;
    public static final int INK_DISABLED = 0xFFB0A58F;
    public static final int BLUE = 0xFF226C9E;
    public static final int BLUE_LIGHT = 0xFFE9F2F6;
    public static final int WARNING = 0xFFB06C18;
    public static final int DANGER = 0xFFC54232;
    public static final int SUCCESS = 0xFF2C7E44;
    public static final int SAVE = 0xFFD45C25;

    private RouteEditorGui() {
    }

    public static SPSGui.Rect panelRect(int screenWidth, int screenHeight, int preferredWidth, int preferredHeight) {
        int width = Math.min(preferredWidth, screenWidth - 18);
        int height = Math.min(preferredHeight, screenHeight - 12);
        return new SPSGui.Rect((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
    }

    public static SPSGui.Rect paperRect(SPSGui.Rect panel) {
        return new SPSGui.Rect(panel.x() + 6, panel.y() + 22, panel.width() - 12, panel.height() - 28);
    }

    public static SPSGui.Rect contentRect(SPSGui.Rect panel) {
        SPSGui.Rect paper = paperRect(panel);
        return new SPSGui.Rect(paper.x() + 8, paper.y() + 8, paper.width() - 16, paper.height() - 13);
    }

    public static void clipboard(GuiGraphicsExtractor graphics, SPSGui.Rect panel) {
        graphics.fill(panel.x() + 3, panel.y() + 4, panel.right() + 3, panel.bottom() + 4, 0x55000000);
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), BOARD);
        graphics.fill(panel.x() + 1, panel.y() + 1, panel.right() - 1, panel.bottom() - 1, BOARD_LIGHT);
        graphics.fill(panel.x() + 3, panel.y() + 4, panel.right() - 3, panel.bottom() - 3, BOARD);
        graphics.outline(panel.x(), panel.y(), panel.width(), panel.height(), BOARD_DARK);

        SPSGui.Rect paper = paperRect(panel);
        graphics.fill(paper.x(), paper.y(), paper.right(), paper.bottom(), PAPER);
        drawPaperRules(graphics, paper, 12);
        graphics.outline(paper.x(), paper.y(), paper.width(), paper.height(), PAPER_LINE);
        graphics.fill(paper.x() + 1, paper.y() + 1, paper.right() - 1, paper.y() + 2, 0x88FFFFFF);

        int clipWidth = Math.min(150, Math.max(82, panel.width() - 116));
        int clipX = panel.x() + (panel.width() - clipWidth) / 2;
        int clipY = panel.y() + 2;
        graphics.fill(clipX, clipY + 2, clipX + clipWidth, clipY + 20, CLIP_DARK);
        graphics.fill(clipX + 2, clipY, clipX + clipWidth - 2, clipY + 18, CLIP);
        graphics.fill(clipX + 4, clipY + 2, clipX + clipWidth - 4, clipY + 5, CLIP_LIGHT);
        graphics.outline(clipX + 2, clipY, clipWidth - 4, 18, CLIP_DARK);
        rivet(graphics, clipX + 14, clipY + 8);
        rivet(graphics, clipX + clipWidth - 18, clipY + 8);
    }

    public static void titleBar(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect panel, Component title, boolean hasBack, List<SPSGui.IconButton> actions, int mouseX, int mouseY) {
        if (hasBack) {
            SPSGui.Rect back = titleBackBounds(panel);
            toolButton(graphics, back, SPSGui.Icon.BACK, false, back.contains(mouseX, mouseY), INK_SECONDARY);
        }

        for (int i = actions.size() - 1; i >= 0; i--) {
            SPSGui.IconButton action = actions.get(i);
            SPSGui.Rect bounds = titleActionBounds(panel, actions.size() - 1 - i);
            toolButton(graphics, bounds, action.icon(), action.disabled(), bounds.contains(mouseX, mouseY), action.color());
        }
    }

    public static void documentHeader(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect content, SPSGui.Icon icon, List<Component> path, Optional<Component> stamp, int stampColor, int seed) {
        int y = content.y();
        int rightLimit = content.right();
        if (stamp.isPresent()) {
            String stampText = stamp.get().getString();
            int stampWidth = Math.min(92, Math.max(36, font.width(stampText) + 9));
            stamp(graphics, font, SPSGui.ellipsize(font, stampText, stampWidth - 8), content.right() - stampWidth, y, stampColor);
            rightLimit = content.right() - stampWidth - 6;
        }
        SPSGui.icon(graphics, new SPSGui.Rect(content.x(), y - 1, 14, 14), icon, INK_SECONDARY);
        String joined = path.stream()
                .map(Component::getString)
                .filter(value -> !value.isBlank())
                .reduce((first, second) -> first + " / " + second)
                .orElse("");
        int textWidth = Math.max(28, rightLimit - content.x() - 18);
        SPSGui.text(graphics, font, SPSGui.scrollingText(font, joined, textWidth, seed), content.x() + 18, y + 2, INK_PRIMARY);
        graphics.fill(content.x(), y + 15, content.right(), y + 16, PAPER_RULE);
    }

    public static int documentBodyY(SPSGui.Rect content) {
        return content.y() + 20;
    }

    public static SPSGui.Rect titleBackBounds(SPSGui.Rect panel) {
        return new SPSGui.Rect(panel.x() + 6, panel.y() + 5, 16, 16);
    }

    public static SPSGui.Rect titleActionBounds(SPSGui.Rect panel, int indexFromRight) {
        return new SPSGui.Rect(panel.right() - 22 - indexFromRight * 18, panel.y() + 5, 16, 16);
    }

    public static void paperSection(GuiGraphicsExtractor graphics, SPSGui.Rect rect, boolean hovered, boolean selected) {
        int fill = hovered ? 0xFFFFF7E8 : PAPER_ELEVATED;
        int border = selected ? BLUE : PAPER_LINE;
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
        drawPaperRules(graphics, rect, 10);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), border);
        graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.y() + 2, 0x99FFFFFF);
    }

    public static void worksheetPane(GuiGraphicsExtractor graphics, SPSGui.Rect rect, boolean hovered, boolean selected) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), hovered ? 0xFFF2F8F8 : BLUEPRINT);
        drawGrid(graphics, rect, 12, BLUEPRINT_GRID);
        drawGrid(graphics, rect, 48, 0x386E91A6);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), selected ? BLUE : PAPER_LINE);
    }

    public static void scrollEdges(GuiGraphicsExtractor graphics, SPSGui.Rect rect, boolean hasTop, boolean hasBottom, boolean blueprint) {
        int color = blueprint ? 0xDDEAF3F4 : 0xDDF7F1E3;
        if (hasTop) {
            graphics.fillGradient(rect.x(), rect.y(), rect.right(), rect.y() + 7, color, 0x00000000);
        }
        if (hasBottom) {
            graphics.fillGradient(rect.x(), rect.bottom() - 7, rect.right(), rect.bottom(), 0x00000000, color);
        }
    }

    public static void thinScrollbar(GuiGraphicsExtractor graphics, SPSGui.Rect rect, double scroll, double maxScroll, boolean hovered) {
        if (maxScroll <= 0.5D || rect.height() <= 16) {
            return;
        }
        int trackX = rect.right() + 2;
        int trackTop = rect.y() + 6;
        int trackHeight = Math.max(8, rect.height() - 12);
        graphics.fill(trackX, trackTop, trackX + 1, trackTop + trackHeight, 0x33877C68);
        double ratio = Math.max(0.0D, Math.min(1.0D, scroll / maxScroll));
        int thumbHeight = Math.max(10, Math.min(trackHeight, (int) Math.round(trackHeight * 0.38D)));
        int thumbY = trackTop + (int) Math.round((trackHeight - thumbHeight) * ratio);
        graphics.fill(trackX - (hovered ? 1 : 0), thumbY, trackX + 1, thumbY + thumbHeight, hovered ? BLUE : INK_MUTED);
    }

    public static void toolButton(GuiGraphicsExtractor graphics, SPSGui.Rect rect, SPSGui.Icon icon, boolean disabled, boolean hovered, int color) {
        int semantic = icon == SPSGui.Icon.SAVE ? SAVE : color;
        int effectiveColor = disabled ? INK_DISABLED : semantic;
        int bg;
        int border;
        if (disabled) {
            bg = PAPER_RECESSED;
            border = 0xFFCFC1A6;
        } else if (icon == SPSGui.Icon.SAVE) {
            bg = hovered ? 0xFFFFEAD9 : 0xFFFFF2E7;
            border = SPSGui.withAlpha(SAVE, hovered ? 0xDD : 0xA0);
        } else if (semantic == DANGER || color == SPSGui.DANGER) {
            bg = hovered ? 0xFFFFECE7 : PAPER_ELEVATED;
            border = SPSGui.withAlpha(DANGER, hovered ? 0xDD : 0x99);
        } else {
            bg = hovered ? BLUE_LIGHT : PAPER_ELEVATED;
            border = hovered ? SPSGui.withAlpha(BLUE, 0xAA) : PAPER_LINE;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), bg);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), border);
        SPSGui.icon(graphics, rect, icon, effectiveColor);
    }

    public static void actionButton(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect rect, SPSGui.Icon icon, Component label, boolean disabled, boolean hovered, int color) {
        int bg = disabled ? PAPER_RECESSED : hovered ? BLUE_LIGHT : PAPER_ELEVATED;
        int border = disabled ? 0xFFCFC1A6 : hovered ? SPSGui.withAlpha(color, 0xBB) : PAPER_LINE;
        int textColor = disabled ? INK_DISABLED : color;
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), bg);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), border);
        SPSGui.icon(graphics, new SPSGui.Rect(rect.x() + 3, rect.y() + (rect.height() - 16) / 2, 16, 16), icon, textColor);
        SPSGui.text(graphics, font, SPSGui.ellipsize(font, label.getString(), rect.width() - 24), rect.x() + 22, rect.y() + (rect.height() - 8) / 2, textColor);
    }

    public static void stamp(GuiGraphicsExtractor graphics, Font font, String label, int x, int y, int color) {
        int width = font.width(label) + 9;
        graphics.fill(x, y, x + width, y + 12, SPSGui.withAlpha(color, 0x18));
        graphics.outline(x, y, width, 12, SPSGui.withAlpha(color, 0x99));
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, SPSGui.withAlpha(color, 0x20));
        SPSGui.text(graphics, font, label, x + 4, y + 2, color);
    }

    public static int statusColor(String status) {
        return switch (status) {
            case "Broken" -> DANGER;
            case "Ambiguous" -> SAVE;
            case "Incomplete" -> WARNING;
            case "Stale" -> BLUE;
            case "Disabled" -> INK_DISABLED;
            case "Valid" -> SUCCESS;
            default -> INK_MUTED;
        };
    }

    public static void checkbox(GuiGraphicsExtractor graphics, SPSGui.Rect rect, boolean checked, boolean hovered) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), hovered ? BLUE_LIGHT : PAPER_ELEVATED);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), checked ? BLUE : PAPER_LINE);
        SPSGui.icon(graphics, rect, checked ? SPSGui.Icon.CHECKBOX_ON : SPSGui.Icon.CHECKBOX_OFF, checked ? BLUE : INK_MUTED);
    }

    public static void routeStripe(GuiGraphicsExtractor graphics, int x, int y, int height, List<Integer> colors) {
        graphics.fill(x, y, x + 5, y + height, 0x33000000);
        SPSGui.colorStripe(graphics, x + 1, y + 1, Math.max(1, height - 2), colors);
    }

    public static void nameBlock(GuiGraphicsExtractor graphics, Font font, String primary, List<String> translatedNames, int x, int y, int width, int seed) {
        String primaryText = SPSGui.scrollingText(font, primary, width, seed);
        SPSGui.text(graphics, font, primaryText, x, y, INK_PRIMARY);
        String translated = SPSGui.translatedNamesLine(translatedNames);
        if (!translated.isEmpty()) {
            SPSGui.smallText(graphics, font, SPSGui.scrollingText(font, translated, Math.round(width / 0.72F), seed + 37), x, y + 11, INK_MUTED, 0.72F);
        }
    }

    public static int nameBlockHeight(List<String> translatedNames) {
        return SPSGui.translatedNamesLine(translatedNames).isEmpty() ? 9 : 18;
    }

    public static void fieldLabel(GuiGraphicsExtractor graphics, Font font, Component label, int x, int y) {
        SPSGui.smallText(graphics, font, label.getString(), x, y, INK_MUTED, 0.72F);
    }

    public static void sectionTitle(GuiGraphicsExtractor graphics, Font font, Component title, int x, int y) {
        SPSGui.text(graphics, font, title, x, y, INK_PRIMARY);
        graphics.fill(x, y + 11, x + Math.min(64, font.width(title)), y + 12, PAPER_LINE);
    }

    public static void documentTooltip(GuiGraphicsExtractor graphics, Font font, Component tooltip, int mouseX, int mouseY, SPSGui.Rect panel) {
        List<String> lines = Arrays.stream(tooltip.getString().split("\\n"))
                .filter(line -> !line.isEmpty())
                .toList();
        if (lines.isEmpty()) {
            return;
        }
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }
        width = Math.min(Math.max(80, width + 16), Math.max(92, panel.width() - 20));
        int height = 10 + lines.size() * 11;
        int x = Math.min(mouseX + 10, panel.right() - width - 6);
        int y = Math.min(mouseY + 10, panel.bottom() - height - 6);
        x = Math.max(panel.x() + 6, x);
        y = Math.max(panel.y() + 6, y);
        paperSection(graphics, new SPSGui.Rect(x, y, width, height), false, false);
        for (int i = 0; i < lines.size(); i++) {
            SPSGui.text(graphics, font, SPSGui.ellipsize(font, lines.get(i), width - 12), x + 8, y + 6 + i * 11, i == 0 ? INK_PRIMARY : INK_SECONDARY);
        }
    }

    public static void colorSwatch(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int color, boolean hovered) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), hovered ? BLUE_LIGHT : PAPER_ELEVATED);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), hovered ? BLUE : PAPER_LINE);
        graphics.fill(rect.x() + 3, rect.y() + 3, rect.right() - 3, rect.bottom() - 3, SPSGui.opaque(color));
        graphics.outline(rect.x() + 3, rect.y() + 3, rect.width() - 6, rect.height() - 6, 0x66000000);
    }

    public static void emptyColorSwatch(GuiGraphicsExtractor graphics, SPSGui.Rect rect, boolean hovered) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), hovered ? BLUE_LIGHT : PAPER_ELEVATED);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), hovered ? BLUE : PAPER_LINE);
        SPSGui.icon(graphics, rect, SPSGui.Icon.PLUS, hovered ? BLUE : INK_MUTED);
    }

    public static void colorPicker(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int selectedColor, int mouseX, int mouseY) {
        SPSGui.colorPicker(graphics, rect, selectedColor, mouseX, mouseY);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), PAPER_LINE);
    }

    private static void drawPaperRules(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int step) {
        for (int y = rect.y() + step; y < rect.bottom() - 1; y += step) {
            graphics.fill(rect.x() + 2, y, rect.right() - 2, y + 1, PAPER_RULE);
        }
    }

    private static void drawGrid(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int step, int color) {
        for (int x = rect.x() + step; x < rect.right(); x += step) {
            graphics.fill(x, rect.y() + 1, x + 1, rect.bottom() - 1, color);
        }
        for (int y = rect.y() + step; y < rect.bottom(); y += step) {
            graphics.fill(rect.x() + 1, y, rect.right() - 1, y + 1, color);
        }
    }

    private static void rivet(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + 5, y + 5, CLIP_DARK);
        graphics.fill(x + 1, y + 1, x + 4, y + 4, CLIP_LIGHT);
        graphics.fill(x + 2, y + 2, x + 4, y + 4, CLIP);
    }
}
