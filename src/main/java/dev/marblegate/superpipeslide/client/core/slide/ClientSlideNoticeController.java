package dev.marblegate.superpipeslide.client.core.slide;

import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.network.slide.ClientboundSlideNoticePayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class ClientSlideNoticeController {
    private static final int MAX_VISIBLE = 3;
    private static final int WIDTH = 232;
    private static final int MIN_WIDTH = 176;
    private static final int MAX_WIDTH_RATIO_DIVISOR = 2;
    private static final int TOP_MARGIN = 12;
    private static final int RIGHT_MARGIN = 8;
    private static final int GAP = 5;
    private static final int PADDING = 7;
    private static final int ICON_SLOT = 22;
    private static final int ICON_SIZE = 16;
    private static final int ENTRY_MS = 260;
    private static final int EXIT_MS = 460;
    private static final int MAX_TITLE_LINES = 2;
    private static final int MAX_BODY_ROWS = 4;
    private static final int CHIP_ROW_HEIGHT = 12;
    private static final int CHIP_SCROLL_DELAY_MS = 850;
    private static final int CHIP_SCROLL_ROW_MS = 1050;
    private static final int DEFAULT_ACCENT = 0xFF47A6FF;
    private static final Deque<Notice> NOTICES = new ArrayDeque<>();

    private ClientSlideNoticeController() {
    }

    public static void handleNotice(ClientboundSlideNoticePayload payload) {
        long now = System.currentTimeMillis();
        Optional<Notice> existing = NOTICES.stream().filter(notice -> notice.sameIdentity(payload)).findFirst();
        if (existing.isPresent()) {
            Notice notice = existing.get();
            NOTICES.remove(notice);
            notice.update(payload, now);
            NOTICES.addFirst(notice);
        } else {
            NOTICES.addFirst(new Notice(payload, now));
        }
        while (NOTICES.size() > MAX_VISIBLE) {
            NOTICES.removeLast();
        }
    }

    public static void clear() {
        NOTICES.clear();
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        NOTICES.removeIf(notice -> now - notice.createdAtMs > durationMs(notice.kind) + EXIT_MS);
    }

    public static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || NOTICES.isEmpty()) {
            return;
        }

        Font font = minecraft.font;
        int width = noticeWidth(graphics.guiWidth());
        int targetX = graphics.guiWidth() - width - RIGHT_MARGIN;
        int y = TOP_MARGIN;
        long now = System.currentTimeMillis();
        double screenOpacity = minecraft.screen == null ? 1.0D : 0.42D;
        int index = 0;
        for (Notice notice : new ArrayList<>(NOTICES)) {
            MeasuredNotice measured = measure(font, notice, width);
            if (Double.isNaN(notice.renderY)) {
                notice.renderY = y;
            } else {
                notice.renderY += (y - notice.renderY) * 0.34D;
            }
            int drawY = (int) Math.round(notice.renderY);
            renderNotice(graphics, font, notice, measured, targetX, drawY, width, index, now, screenOpacity);
            y += measured.height() + GAP;
            index++;
        }
    }

    private static int noticeWidth(int guiWidth) {
        return Math.min(WIDTH, Math.max(MIN_WIDTH, guiWidth / MAX_WIDTH_RATIO_DIVISOR));
    }

    private static MeasuredNotice measure(Font font, Notice notice, int width) {
        int textWidth = Math.max(40, width - PADDING * 2 - ICON_SLOT - 7);
        List<FormattedCharSequence> titleLines = limit(font.split(notice.title, textWidth), MAX_TITLE_LINES);
        List<MeasuredBodyLine> bodyLines = new ArrayList<>();
        if (isScrollableChipNotice(notice)) {
            for (ClientboundSlideNoticePayload.NoticeLine line : notice.lines) {
                bodyLines.add(new MeasuredBodyLine(line, List.of(FormattedCharSequence.forward(line.text().getString(), line.text().getStyle())), true));
            }
            int headerHeight = 8 + Math.max(9, titleLines.size() * 9);
            int bodyHeight = 4 + MAX_BODY_ROWS * CHIP_ROW_HEIGHT;
            int height = Math.max(38, PADDING * 2 + headerHeight + bodyHeight + 3);
            return new MeasuredNotice(titleLines, bodyLines, height, true);
        }
        int rows = 0;
        int hiddenRows = 0;
        for (ClientboundSlideNoticePayload.NoticeLine line : notice.lines) {
            if (rows >= MAX_BODY_ROWS) {
                hiddenRows++;
                continue;
            }
            if (line.chip()) {
                bodyLines.add(new MeasuredBodyLine(line, List.of(FormattedCharSequence.forward(line.text().getString(), line.text().getStyle())), true));
                rows++;
                continue;
            }
            List<FormattedCharSequence> wrapped = font.split(line.text(), textWidth);
            for (FormattedCharSequence sequence : wrapped) {
                if (rows >= MAX_BODY_ROWS) {
                    hiddenRows++;
                    continue;
                }
                bodyLines.add(new MeasuredBodyLine(line, List.of(sequence), false));
                rows++;
            }
        }
        if (hiddenRows > 0 && rows < MAX_BODY_ROWS) {
            bodyLines.add(new MeasuredBodyLine(
                    new ClientboundSlideNoticePayload.NoticeLine(Component.translatable("notice.superpipeslide.slide.more_lines", hiddenRows), List.of(), false),
                    List.of(FormattedCharSequence.forward(Component.translatable("notice.superpipeslide.slide.more_lines", hiddenRows).getString(), net.minecraft.network.chat.Style.EMPTY)),
                    false
            ));
        }
        int headerHeight = 8 + Math.max(9, titleLines.size() * 9);
        int bodyHeight = bodyLines.isEmpty() ? 0 : 4 + bodyLines.stream().mapToInt(line -> line.chip() ? CHIP_ROW_HEIGHT : 9).sum();
        int height = Math.max(38, PADDING * 2 + headerHeight + bodyHeight + 3);
        return new MeasuredNotice(titleLines, bodyLines, height, false);
    }

    private static boolean isScrollableChipNotice(Notice notice) {
        return notice.lines.size() > MAX_BODY_ROWS && notice.lines.stream().allMatch(ClientboundSlideNoticePayload.NoticeLine::chip);
    }

    private static List<FormattedCharSequence> limit(List<FormattedCharSequence> lines, int maxLines) {
        if (lines.size() <= maxLines) {
            return lines;
        }
        return List.copyOf(lines.subList(0, maxLines));
    }

    private static void renderNotice(GuiGraphicsExtractor graphics, Font font, Notice notice, MeasuredNotice measured, int targetX, int y, int width, int stackIndex, long now, double screenOpacity) {
        long age = now - notice.createdAtMs;
        double duration = durationMs(notice.kind);
        double entry = clamp(age / (double) ENTRY_MS);
        double exit = clamp((age - duration) / (double) EXIT_MS);
        double opacity = (1.0D - exit) * (stackIndex == 0 ? 1.0D : stackIndex == 1 ? 0.88D : 0.76D) * screenOpacity;
        if (opacity <= 0.02D) {
            return;
        }

        double xOffset = (1.0D - easeOutBack(entry)) * (width + 22.0D) + easeInCubic(exit) * (width + 22.0D);
        int x = targetX + (int) Math.round(xOffset);
        if (notice.kind == ClientboundSlideNoticePayload.Kind.WARNING || notice.kind == ClientboundSlideNoticePayload.Kind.BLOCKED) {
            double shake = age < 190L ? Math.sin(age / 18.0D) * (notice.kind == ClientboundSlideNoticePayload.Kind.BLOCKED ? 2.4D : 1.8D) : 0.0D;
            x += (int) Math.round(shake);
        }

        int height = measured.height();
        List<Integer> colors = colorsFor(notice);
        int accent = colors.getFirst();
        int border = borderColor(notice.kind, accent);
        graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, color(0x66000000, opacity));
        graphics.fill(x, y, x + width, y + height, color(0xE9141B22, opacity));
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, color(0xF01B2630, opacity));
        graphics.outline(x, y, width, height, color(border, opacity));
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, color(0x44FFFFFF, opacity));
        drawVerticalColorBand(graphics, x, y, 4, height, colors, opacity);

        double focusPulse = focusPulse(notice.kind, age);
        int iconBg = color(0xFF22303D, Math.min(1.0D, opacity * (0.78D + focusPulse * 0.22D)));
        int iconBorder = color(SPSGui.withAlpha(accent, 0xCC), opacity);
        int iconX = x + PADDING + 3;
        int iconY = y + PADDING + 2;
        graphics.fill(iconX - 3, iconY - 3, iconX + ICON_SLOT - 3, iconY + ICON_SLOT - 3, iconBg);
        graphics.outline(iconX - 3, iconY - 3, ICON_SLOT, ICON_SLOT, iconBorder);
        int wobble = notice.kind == ClientboundSlideNoticePayload.Kind.CHOICE ? (int) Math.round(Math.sin(age / 155.0D) * 1.2D) : 0;
        SPSGui.icon(graphics, new SPSGui.Rect(iconX + wobble, iconY, ICON_SIZE, ICON_SIZE), iconFor(notice.kind), color(iconColor(notice.kind, accent), opacity));

        int textX = x + PADDING + ICON_SLOT + 7;
        int textY = y + PADDING;
        SPSGui.smallText(graphics, font, Component.translatable(labelKey(notice.kind)).getString(), textX, textY, color(labelColor(notice.kind, accent), opacity), 0.68F);
        textY += 8;
        for (FormattedCharSequence line : measured.titleLines()) {
            graphics.text(font, line, textX, textY, color(0xFFEAF1F8, opacity), false);
            textY += 9;
        }
        if (!measured.bodyLines().isEmpty()) {
            textY += 4;
            int bodyWidth = width - (textX - x) - PADDING;
            if (measured.scrollingChips()) {
                drawScrollingChipLines(graphics, font, measured.bodyLines(), textX, textY, bodyWidth, MAX_BODY_ROWS * CHIP_ROW_HEIGHT, age, opacity);
            } else {
                for (MeasuredBodyLine line : measured.bodyLines()) {
                    if (line.chip()) {
                        drawChipLine(graphics, font, line.source(), textX, textY, bodyWidth, opacity);
                        textY += CHIP_ROW_HEIGHT;
                    } else {
                        graphics.text(font, line.sequences().getFirst(), textX, textY, color(bodyColor(notice.kind), opacity), false);
                        textY += 9;
                    }
                }
            }
        }
        drawLifetimeRail(graphics, notice, x + 6, y + height - 5, width - 12, colors, age, duration, opacity);
    }

    private static void drawScrollingChipLines(GuiGraphicsExtractor graphics, Font font, List<MeasuredBodyLine> lines, int x, int y, int width, int height, long age, double opacity) {
        if (lines.isEmpty()) {
            return;
        }
        int scrollableRows = lines.size();
        double offset = chipScrollOffset(age, scrollableRows);
        int start = Math.floorMod((int) Math.floor(offset / CHIP_ROW_HEIGHT), scrollableRows);
        double localOffset = offset - Math.floor(offset / CHIP_ROW_HEIGHT) * CHIP_ROW_HEIGHT;
        int drawRows = Math.min(scrollableRows, MAX_BODY_ROWS + 2);
        graphics.enableScissor(x, y, x + width, y + height);
        for (int i = 0; i < drawRows; i++) {
            int index = (start + i) % scrollableRows;
            int rowY = y + i * CHIP_ROW_HEIGHT - (int) Math.round(localOffset);
            if (rowY > y - CHIP_ROW_HEIGHT && rowY < y + height) {
                drawChipLine(graphics, font, lines.get(index).source(), x, rowY, width, opacity);
            }
        }
        graphics.disableScissor();
        graphics.fill(x, y, x + width, y + 2, color(0x661B2630, opacity));
        graphics.fill(x, y + height - 2, x + width, y + height, color(0x661B2630, opacity));
    }

    private static double chipScrollOffset(long age, int rows) {
        if (rows <= MAX_BODY_ROWS) {
            return 0.0D;
        }
        long scrollAge = Math.max(0L, age - CHIP_SCROLL_DELAY_MS);
        double cycleHeight = rows * (double) CHIP_ROW_HEIGHT;
        return (scrollAge / (double) CHIP_SCROLL_ROW_MS * CHIP_ROW_HEIGHT) % cycleHeight;
    }

    private static void drawChipLine(GuiGraphicsExtractor graphics, Font font, ClientboundSlideNoticePayload.NoticeLine line, int x, int y, int width, double opacity) {
        int chipWidth = Math.max(34, Math.min(width, font.width(line.text().getString()) + 18));
        graphics.fill(x, y, x + chipWidth, y + 10, color(0x552A3540, opacity));
        graphics.outline(x, y, chipWidth, 10, color(0x885B6673, opacity));
        drawVerticalColorBand(graphics, x + 2, y + 2, 3, 6, line.colors().isEmpty() ? List.of(DEFAULT_ACCENT) : line.colors(), opacity);
        String label = SPSGui.ellipsize(font, line.text().getString(), chipWidth - 11);
        SPSGui.smallText(graphics, font, label, x + 8, y + 2, color(0xFFDCE7F0, opacity), 0.66F);
    }

    private static void drawLifetimeRail(GuiGraphicsExtractor graphics, Notice notice, int x, int y, int width, List<Integer> colors, long age, double duration, double opacity) {
        graphics.fill(x, y, x + width, y + 3, color(0x553E4A57, opacity));
        int liveWidth = (int) Math.round(width * Math.max(0.0D, 1.0D - clamp(age / duration)));
        drawHorizontalColorBand(graphics, x, y, Math.max(0, liveWidth), 3, colors, opacity);
    }

    private static void drawVerticalColorBand(GuiGraphicsExtractor graphics, int x, int y, int width, int height, List<Integer> colors, double opacity) {
        List<Integer> normalized = normalizeColors(colors);
        if (normalized.size() == 1) {
            graphics.fill(x, y, x + width, y + height, color(normalized.getFirst(), opacity));
            return;
        }
        int segmentHeight = Math.max(1, height / normalized.size());
        for (int i = 0; i < normalized.size(); i++) {
            int y1 = y + i * segmentHeight;
            int y2 = i == normalized.size() - 1 ? y + height : y1 + segmentHeight;
            graphics.fill(x, y1, x + width, y2, color(normalized.get(i), opacity));
        }
    }

    private static void drawHorizontalColorBand(GuiGraphicsExtractor graphics, int x, int y, int width, int height, List<Integer> colors, double opacity) {
        if (width <= 0 || height <= 0) {
            return;
        }
        List<Integer> normalized = normalizeColors(colors);
        if (normalized.size() == 1) {
            graphics.fill(x, y, x + width, y + height, color(normalized.getFirst(), opacity));
            return;
        }
        int stripeHeight = Math.max(1, height / normalized.size());
        for (int i = 0; i < normalized.size(); i++) {
            int y1 = y + i * stripeHeight;
            int y2 = i == normalized.size() - 1 ? y + height : Math.min(y + height, y1 + stripeHeight);
            graphics.fill(x, y1, x + width, y2, color(normalized.get(i), opacity));
        }
    }

    private static List<Integer> colorsFor(Notice notice) {
        if (!notice.accentColors.isEmpty()) {
            return normalizeColors(notice.accentColors);
        }
        return switch (notice.kind) {
            case WARNING -> List.of(0xFFFFB13B);
            case BLOCKED -> List.of(0xFFFF5E4D);
            case TERMINAL -> List.of(0xFFFFC857);
            case CHOICE -> List.of(0xFF47A6FF);
            default -> List.of(DEFAULT_ACCENT);
        };
    }

    private static List<Integer> normalizeColors(List<Integer> colors) {
        if (colors == null || colors.isEmpty()) {
            return List.of(DEFAULT_ACCENT);
        }
        return colors.stream().limit(3).map(SPSGui::opaque).toList();
    }

    private static int borderColor(ClientboundSlideNoticePayload.Kind kind, int accent) {
        return switch (kind) {
            case WARNING -> 0xFFFFB13B;
            case BLOCKED -> 0xFFFF5E4D;
            case TERMINAL -> 0xFFFFD46C;
            case CHOICE -> 0xFF64B8FF;
            default -> SPSGui.withAlpha(accent, 0xDD);
        };
    }

    private static int labelColor(ClientboundSlideNoticePayload.Kind kind, int accent) {
        return switch (kind) {
            case WARNING -> 0xFFFFC45F;
            case BLOCKED -> 0xFFFF7A69;
            case TERMINAL -> 0xFFFFD46C;
            case CHOICE -> 0xFF7CCBFF;
            default -> SPSGui.withAlpha(accent, 0xFF);
        };
    }

    private static int iconColor(ClientboundSlideNoticePayload.Kind kind, int accent) {
        return switch (kind) {
            case STANDARD -> 0xFFEAF1F8;
            case WARNING -> 0xFFFFC45F;
            case BLOCKED -> 0xFFFF7A69;
            case TERMINAL -> 0xFFFFD46C;
            case CHOICE -> 0xFF7CCBFF;
            default -> SPSGui.withAlpha(accent, 0xFF);
        };
    }

    private static int bodyColor(ClientboundSlideNoticePayload.Kind kind) {
        return switch (kind) {
            case WARNING -> 0xFFFFD8A6;
            case BLOCKED -> 0xFFFFC2B9;
            default -> 0xFFC8D3DF;
        };
    }

    private static SPSGui.Icon iconFor(ClientboundSlideNoticePayload.Kind kind) {
        return switch (kind) {
            case ENTER_ROUTE -> SPSGui.Icon.ROUTE_LINE;
            case CHOICE -> SPSGui.Icon.SPLIT;
            case PASS_STATION -> SPSGui.Icon.STATION_ORDER;
            case ARRIVAL -> SPSGui.Icon.LOCATE;
            case TERMINAL -> SPSGui.Icon.LOOP;
            case WARNING -> SPSGui.Icon.WARNING;
            case BLOCKED -> SPSGui.Icon.ERROR;
            case STANDARD -> SPSGui.Icon.INFO;
        };
    }

    private static String labelKey(ClientboundSlideNoticePayload.Kind kind) {
        return switch (kind) {
            case ENTER_ROUTE -> "notice.superpipeslide.slide.kind.enter_route";
            case CHOICE -> "notice.superpipeslide.slide.kind.choice";
            case PASS_STATION -> "notice.superpipeslide.slide.kind.pass_station";
            case ARRIVAL -> "notice.superpipeslide.slide.kind.arrival";
            case TERMINAL -> "notice.superpipeslide.slide.kind.terminal";
            case WARNING -> "notice.superpipeslide.slide.kind.warning";
            case BLOCKED -> "notice.superpipeslide.slide.kind.blocked";
            case STANDARD -> "notice.superpipeslide.slide.kind.standard";
        };
    }

    private static long durationMs(ClientboundSlideNoticePayload.Kind kind) {
        return switch (kind) {
            case PASS_STATION -> 3600L;
            case ENTER_ROUTE, STANDARD -> 4400L;
            case ARRIVAL -> 5200L;
            case TERMINAL, WARNING, BLOCKED -> 5800L;
            case CHOICE -> 6500L;
        };
    }

    private static double focusPulse(ClientboundSlideNoticePayload.Kind kind, long age) {
        if (kind == ClientboundSlideNoticePayload.Kind.ARRIVAL && age < 900L) {
            return 1.0D - clamp(age / 900.0D);
        }
        if (kind == ClientboundSlideNoticePayload.Kind.TERMINAL && age < 1100L) {
            return 1.0D - clamp(age / 1100.0D);
        }
        return 0.0D;
    }

    private static int color(int color, double opacity) {
        int alpha = (int) Math.round(((color >>> 24) & 0xFF) * clamp(opacity));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static double easeOutBack(double t) {
        double clamped = clamp(t);
        double c1 = 1.45D;
        double c3 = c1 + 1.0D;
        return 1.0D + c3 * Math.pow(clamped - 1.0D, 3.0D) + c1 * Math.pow(clamped - 1.0D, 2.0D);
    }

    private static double easeInCubic(double t) {
        double clamped = clamp(t);
        return clamped * clamped * clamped;
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static final class Notice {
        private ClientboundSlideNoticePayload.Kind kind;
        private List<Integer> accentColors;
        private Component title;
        private List<ClientboundSlideNoticePayload.NoticeLine> lines;
        private long createdAtMs;
        private double renderY = Double.NaN;

        private Notice(ClientboundSlideNoticePayload payload, long now) {
            this.update(payload, now);
        }

        private void update(ClientboundSlideNoticePayload payload, long now) {
            this.kind = payload.kind();
            this.accentColors = List.copyOf(payload.accentColors());
            this.title = payload.title();
            this.lines = List.copyOf(payload.lines());
            this.createdAtMs = now;
        }

        private boolean sameIdentity(ClientboundSlideNoticePayload payload) {
            return this.kind == payload.kind() && this.title.getString().equals(payload.title().getString());
        }
    }

    private record MeasuredNotice(List<FormattedCharSequence> titleLines, List<MeasuredBodyLine> bodyLines, int height, boolean scrollingChips) {
    }

    private record MeasuredBodyLine(ClientboundSlideNoticePayload.NoticeLine source, List<FormattedCharSequence> sequences, boolean chip) {
    }
}
