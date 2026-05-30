package dev.marblegate.superpipeslide.client.gui.pipe;

import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

final class PipeAppearanceTerminalGui {
    static final int FRAME = 0xF224282C;
    static final int FRAME_DARK = 0xF2171A1D;
    static final int SCREEN = 0xF706100B;
    static final int SURFACE = 0xF70A1811;
    static final int SURFACE_RAISED = 0xF70E2118;
    static final int SURFACE_RECESSED = 0xF0040A07;
    static final int BORDER = 0xFF4A5359;
    static final int BORDER_MUTED = 0x994D7F5D;
    static final int ACCENT = 0xFF46FF7A;
    static final int ACCENT_SELECTED = 0xFF7DFF9B;
    static final int SUCCESS = 0xFF46FF7A;
    static final int WARNING = 0xFFFFB44A;
    static final int DANGER = 0xFFFF5C5C;
    static final int GLOW = 0xFFB7FF66;
    static final int TEXT = 0xFFB8F6C5;
    static final int TEXT_SECONDARY = 0xFF74B889;
    static final int TEXT_MUTED = 0xFF4D7F5D;
    static final int TEXT_DISABLED = 0xFF3C5845;

    private PipeAppearanceTerminalGui() {}

    static void frame(GuiGraphicsExtractor graphics, SPSGui.Rect panel) {
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), FRAME);
        graphics.outline(panel.x(), panel.y(), panel.width(), panel.height(), 0xFF4A5359);
        graphics.fill(panel.x() + 1, panel.y() + 1, panel.right() - 1, panel.y() + 2, 0x554A5359);
        graphics.fill(panel.x() + 1, panel.bottom() - 3, panel.right() - 1, panel.bottom() - 1, 0x88111214);
        SPSGui.Rect screen = new SPSGui.Rect(panel.x() + 4, panel.y() + 22, panel.width() - 8, panel.height() - 26);
        graphics.fill(screen.x(), screen.y(), screen.right(), screen.bottom(), SCREEN);
        drawGrid(graphics, screen, 12, 0x18143D2A);
        drawGrid(graphics, screen, 48, 0x2A143D2A);
    }

    static void titleBar(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect panel, Component title, boolean hasBack, int mouseX, int mouseY) {
        graphics.fill(panel.x() + 1, panel.y() + 1, panel.right() - 1, panel.y() + 21, FRAME_DARK);
        graphics.fill(panel.x() + 1, panel.y() + 20, panel.right() - 1, panel.y() + 21, BORDER);
        int titleX = panel.x() + 9;
        if (hasBack) {
            SPSGui.Rect back = backBounds(panel);
            iconButton(graphics, back, SPSGui.Icon.BACK, false, back.contains(mouseX, mouseY), TEXT_SECONDARY);
            titleX = back.right() + 5;
        }
        SPSGui.text(graphics, font, SPSGui.ellipsize(font, title.getString(), panel.width() - 92), titleX, panel.y() + 7, TEXT);
        int pulse = (int) ((System.currentTimeMillis() / 420L) % 3L);
        for (int i = 0; i < 3; i++) {
            int color = i == pulse ? ACCENT_SELECTED : 0xFF3B4549;
            graphics.fill(panel.right() - 21 + i * 5, panel.y() + 8, panel.right() - 18 + i * 5, panel.y() + 11, color);
        }
    }

    static SPSGui.Rect backBounds(SPSGui.Rect panel) {
        return new SPSGui.Rect(panel.x() + 5, panel.y() + 3, 16, 16);
    }

    static void panel(GuiGraphicsExtractor graphics, SPSGui.Rect rect) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), SURFACE);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), BORDER_MUTED);
        graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.y() + 2, 0x2446FF7A);
    }

    static void raisedPanel(GuiGraphicsExtractor graphics, SPSGui.Rect rect) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), SURFACE_RAISED);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), BORDER);
        graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.y() + 2, 0x3346FF7A);
    }

    static void previewStage(GuiGraphicsExtractor graphics, SPSGui.Rect rect, boolean hovered) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), SURFACE_RECESSED);
        drawGrid(graphics, rect, 10, 0x24143D2A);
        drawGrid(graphics, rect, 40, 0x3A143D2A);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), hovered ? ACCENT_SELECTED : BORDER);
        graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.y() + 2, 0x2246FF7A);
    }

    static void sectionLabel(GuiGraphicsExtractor graphics, Font font, Component label, int x, int y) {
        SPSGui.smallText(graphics, font, label.getString(), x, y, TEXT_MUTED, 0.68F);
        graphics.fill(x, y + 9, x + Math.min(58, font.width(label)), y + 10, 0x6646FF7A);
    }

    static void button(GuiGraphicsExtractor graphics, SPSGui.Rect rect, boolean hovered, boolean selected) {
        int fill = selected ? 0xF7143224 : hovered ? 0xF711281D : SURFACE_RAISED;
        int border = selected ? ACCENT_SELECTED : hovered ? ACCENT : BORDER_MUTED;
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), border);
        graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.y() + 2, selected ? 0x4446FF7A : 0x22143D2A);
        if (selected) {
            graphics.fill(rect.x() + 2, rect.bottom() - 3, rect.right() - 2, rect.bottom() - 1, ACCENT_SELECTED);
        }
    }

    static void commandButton(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect rect, Component text, boolean hovered, boolean disabled, boolean primary) {
        int fill = disabled ? 0xF00A120D : primary ? hovered ? 0xFF1A6235 : 0xFF144D2C : hovered ? 0xF711281D : SURFACE_RAISED;
        int border = disabled ? 0x66526B7A : primary ? ACCENT_SELECTED : hovered ? ACCENT : BORDER;
        int textColor = disabled ? TEXT_DISABLED : primary ? 0xFFE9FFEE : TEXT;
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), border);
        graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.y() + 2, primary ? 0x5546FF7A : 0x22143D2A);
        SPSGui.centeredText(graphics, font, SPSGui.ellipsize(font, text.getString(), rect.width() - 8), rect.x() + rect.width() / 2, rect.y() + (rect.height() - 8) / 2, textColor);
    }

    static void iconButton(GuiGraphicsExtractor graphics, SPSGui.Rect rect, SPSGui.Icon icon, boolean active, boolean hovered, int color) {
        button(graphics, rect, hovered, active);
        SPSGui.icon(graphics, rect, icon, active ? 0xFFFFFFFF : color);
    }

    static void badge(GuiGraphicsExtractor graphics, Font font, SPSGui.Rect rect, Component text, int color) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), SPSGui.withAlpha(color, 0x1F));
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), SPSGui.withAlpha(color, 0xAA));
        SPSGui.smallText(graphics, font, SPSGui.ellipsize(font, text.getString(), Math.round((rect.width() - 10) / 0.64F)), rect.x() + 5, rect.y() + 5, color, 0.64F);
    }

    static void swatchFrame(GuiGraphicsExtractor graphics, SPSGui.Rect rect, boolean hovered, boolean selected) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), selected ? 0xF7143224 : 0xF0040A07);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), selected ? ACCENT_SELECTED : hovered ? ACCENT : BORDER_MUTED);
    }

    static void scrollEdges(GuiGraphicsExtractor graphics, SPSGui.Rect rect, boolean hasTop, boolean hasBottom) {
        if (hasTop) {
            graphics.fillGradient(rect.x(), rect.y(), rect.right(), rect.y() + 8, 0xE506100B, 0x0006100B);
        }
        if (hasBottom) {
            graphics.fillGradient(rect.x(), rect.bottom() - 8, rect.right(), rect.bottom(), 0x0006100B, 0xE506100B);
        }
    }

    static void thinScrollbar(GuiGraphicsExtractor graphics, SPSGui.Rect rect, double scroll, double maxScroll, boolean hovered) {
        if (maxScroll <= 0.5D || rect.height() <= 18) {
            return;
        }
        int trackX = rect.right() - 2;
        int trackTop = rect.y() + 5;
        int trackHeight = Math.max(8, rect.height() - 10);
        graphics.fill(trackX, trackTop, trackX + 1, trackTop + trackHeight, 0x6646FF7A);
        double ratio = Math.max(0.0D, Math.min(1.0D, scroll / maxScroll));
        int thumbHeight = Math.max(10, Math.min(trackHeight, (int) Math.round(trackHeight * Math.max(0.22D, rect.height() / (rect.height() + maxScroll)))));
        int thumbY = trackTop + (int) Math.round((trackHeight - thumbHeight) * ratio);
        graphics.fill(trackX - (hovered ? 1 : 0), thumbY, trackX + 2, thumbY + thumbHeight, hovered ? ACCENT_SELECTED : ACCENT);
    }

    static void sideScrollEdges(GuiGraphicsExtractor graphics, SPSGui.Rect rect, boolean hasLeft, boolean hasRight) {
        if (hasLeft) {
            graphics.fill(rect.x(), rect.y(), rect.x() + 8, rect.bottom(), 0xDD06100B);
        }
        if (hasRight) {
            graphics.fill(rect.right() - 8, rect.y(), rect.right(), rect.bottom(), 0xDD06100B);
        }
    }

    static void thinHorizontalScrollbar(GuiGraphicsExtractor graphics, SPSGui.Rect rect, double scroll, double maxScroll, boolean hovered) {
        if (maxScroll <= 0.5D || rect.width() <= 18) {
            return;
        }
        int trackLeft = rect.x() + 6;
        int trackWidth = Math.max(8, rect.width() - 12);
        int trackY = rect.bottom() - 2;
        graphics.fill(trackLeft, trackY, trackLeft + trackWidth, trackY + 1, 0x6646FF7A);
        double ratio = Math.max(0.0D, Math.min(1.0D, scroll / maxScroll));
        int thumbWidth = Math.max(14, Math.min(trackWidth, (int) Math.round(trackWidth * Math.max(0.22D, rect.width() / (rect.width() + maxScroll)))));
        int thumbX = trackLeft + (int) Math.round((trackWidth - thumbWidth) * ratio);
        graphics.fill(thumbX, trackY - (hovered ? 1 : 0), thumbX + thumbWidth, trackY + 2, hovered ? ACCENT_SELECTED : ACCENT);
    }

    static void terminalTooltip(GuiGraphicsExtractor graphics, Font font, Component tooltip, int mouseX, int mouseY, SPSGui.Rect boundary) {
        List<String> lines = Arrays.stream(tooltip.getString().split("\\R"))
                .filter(line -> !line.isBlank())
                .toList();
        if (lines.isEmpty()) {
            return;
        }
        int width = 96;
        for (String line : lines) {
            width = Math.max(width, font.width(line) + 18);
        }
        width = Math.min(width, Math.max(96, boundary.width() - 12));
        width = Math.min(width, 260);
        int height = 12 + lines.size() * 11;
        SPSGui.Rect bounds = place(new SPSGui.Rect(mouseX + 10, mouseY + 10, width, height), boundary, 6);
        graphics.fill(bounds.x() + 2, bounds.y() + 3, bounds.right() + 2, bounds.bottom() + 3, 0x99000000);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0xF70A1811);
        graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), BORDER);
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + 2, bounds.bottom(), ACCENT_SELECTED);
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.y() + 2, 0x3346FF7A);
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? TEXT : TEXT_SECONDARY;
            SPSGui.text(graphics, font, SPSGui.ellipsize(font, lines.get(i), bounds.width() - 12), bounds.x() + 7, bounds.y() + 6 + i * 11, color);
        }
    }

    private static SPSGui.Rect place(SPSGui.Rect preferred, SPSGui.Rect boundary, int padding) {
        int x = preferred.x();
        int y = preferred.y();
        if (x + preferred.width() > boundary.right() - padding) {
            x = preferred.x() - preferred.width() - 18;
        }
        if (y + preferred.height() > boundary.bottom() - padding) {
            y = preferred.y() - preferred.height() - 18;
        }
        x = Math.max(boundary.x() + padding, Math.min(x, boundary.right() - preferred.width() - padding));
        y = Math.max(boundary.y() + padding, Math.min(y, boundary.bottom() - preferred.height() - padding));
        return new SPSGui.Rect(x, y, preferred.width(), preferred.height());
    }

    private static void drawGrid(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int step, int color) {
        for (int x = rect.x() + step; x < rect.right(); x += step) {
            graphics.fill(x, rect.y() + 1, x + 1, rect.bottom() - 1, color);
        }
        for (int y = rect.y() + step; y < rect.bottom(); y += step) {
            graphics.fill(rect.x() + 1, y, rect.right() - 1, y + 1, color);
        }
    }
}
