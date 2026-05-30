package dev.marblegate.superpipeslide.client.gui.route;

import dev.marblegate.superpipeslide.client.core.route.ClientRouteDataCache;
import dev.marblegate.superpipeslide.client.fullmap.screen.FullRouteMapScreen;
import dev.marblegate.superpipeslide.client.gui.base.RouteDataAwareScreen;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.common.core.route.model.line.RouteLine;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class RouteLineListScreen extends RouteEditorScreenBase implements RouteDataAwareScreen {
    private EditBox searchBox;
    private double scroll;

    public RouteLineListScreen() {
        super(Component.translatable("screen.superpipeslide.route_editor"));
    }

    @Override
    protected void rebuildWidgets() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue();
        this.clearWidgets();
        int searchWidth = Math.max(92, this.panel.width() - 110);
        this.searchBox = this.borderlessBox(this.panel.x() + 32, this.panel.y() + 33, searchWidth, query);
        this.updateSearchBoxState(!ClientRouteDataCache.routeLines().isEmpty());
    }

    public void refreshFromRouteSnapshot() {
        this.rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.beginFrame();
        this.drawEditorFrame(graphics);
        boolean hasLines = !ClientRouteDataCache.routeLines().isEmpty();
        if (this.searchBox == null) {
            this.rebuildWidgets();
        }
        this.updateSearchBoxState(hasLines);
        SPSGui.Rect plus = RouteEditorGui.titleActionBounds(this.panel, 0);
        SPSGui.Rect map = RouteEditorGui.titleActionBounds(this.panel, hasLines ? 1 : 0);
        this.drawTitle(graphics, Component.translatable("screen.superpipeslide.route_editor"), false, hasLines ? List.of(SPSGui.IconButton.of(SPSGui.Icon.INFO), SPSGui.IconButton.of(SPSGui.Icon.PLUS)) : List.of(SPSGui.IconButton.of(SPSGui.Icon.INFO)), mouseX, mouseY);
        SPSGui.Rect content = this.editorContent();
        this.drawDocumentHeader(graphics, SPSGui.Icon.ROUTE_LINE, List.of(Component.translatable("screen.superpipeslide.route_editor")), null, RouteEditorGui.INK_MUTED, 17);
        this.addClick(map, () -> this.minecraft.setScreen(new FullRouteMapScreen()), Component.translatable("screen.superpipeslide.action.open_full_map"));
        if (hasLines && this.searchBox != null) {
            int searchWidth = Math.max(86, Math.min(128, content.width() / 2));
            this.searchBox.setX(content.right() - searchWidth);
            this.searchBox.setY(content.y() + 1);
            this.searchBox.setWidth(searchWidth);
            this.addClick(plus, () -> this.minecraft.setScreen(new RouteLineCreateScreen()), Component.translatable("screen.superpipeslide.action.create_route"));
            SPSGui.icon(graphics, new SPSGui.Rect(this.searchBox.getX() - 13, this.searchBox.getY(), 12, 12), SPSGui.Icon.SEARCH, RouteEditorGui.INK_MUTED);
            this.drawSearchPlaceholder(graphics, this.searchBox);
        }
        if (hasLines) {
            this.scroll = Math.max(0.0D, Math.min(maxScroll(), this.scroll));
            this.renderLineCards(graphics, mouseX, mouseY);
        } else {
            this.renderEmptyState(graphics, mouseX, mouseY);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.renderTooltips(graphics, mouseX, mouseY);
    }

    private void renderEmptyState(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        SPSGui.Rect content = this.editorContent();
        SPSGui.Rect create = new SPSGui.Rect(content.x() + 28, content.y() + Math.max(20, (content.height() - 54) / 2), content.width() - 56, 44);
        RouteEditorGui.actionButton(graphics, this.font, create, SPSGui.Icon.PLUS, Component.translatable("screen.superpipeslide.route_editor.create_line"), false, create.contains(mouseX, mouseY), RouteEditorGui.BLUE);
        SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.route_editor.empty").getString(), create.x() + 24, create.bottom() + 6, RouteEditorGui.INK_MUTED, 0.72F);
        this.addClick(create, () -> this.minecraft.setScreen(new RouteLineCreateScreen()), Component.translatable("screen.superpipeslide.action.create_route"));
    }

    private void renderLineCards(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<RouteLine> lines = filteredLines();
        SPSGui.Rect content = this.editorContent();
        int x = content.x();
        int y = content.y() + 24 - (int) this.scroll;
        int width = content.width();
        int contentTop = content.y() + 21;
        int contentBottom = content.bottom();
        graphics.enableScissor(content.x(), contentTop, content.right(), contentBottom);
        for (RouteLine line : lines) {
            int groupCount = directionGroupCount(line);
            boolean ellipsis = groupCount > 1;
            int height = lineCardHeight(line, ellipsis);
            SPSGui.Rect card = new SPSGui.Rect(x, y, width, height);
            if (card.bottom() >= contentTop && card.y() <= contentBottom) {
                renderLineCard(graphics, line, card, mouseX, mouseY, ellipsis, groupCount);
            }
            y += height + 5;
        }
        graphics.disableScissor();
        RouteEditorGui.scrollEdges(graphics, new SPSGui.Rect(content.x(), contentTop, content.width(), contentBottom - contentTop), this.scroll > 0.5D, this.scroll < maxScroll() - 0.5D, false);
        RouteEditorGui.thinScrollbar(graphics, new SPSGui.Rect(content.x(), contentTop, content.width(), contentBottom - contentTop), this.scroll, maxScroll(), new SPSGui.Rect(content.x(), contentTop, content.width(), contentBottom - contentTop).contains(mouseX, mouseY));
    }

    private void renderLineCard(GuiGraphicsExtractor graphics, RouteLine line, SPSGui.Rect card, int mouseX, int mouseY, boolean ellipsis, int groupCount) {
        boolean hovered = card.contains(mouseX, mouseY);
        RouteEditorGui.paperSection(graphics, card, hovered, false);
        RouteEditorGui.routeStripe(graphics, card.x(), card.y(), card.height(), line.themeColors());
        String status = SPSGui.lineStatus(line);
        RouteEditorGui.nameBlock(graphics, this.font, line.displayName(), line.translatedNames(), card.x() + 10, card.y() + 5, card.width() - 104, line.id().hashCode());
        Component statusLabel = Component.translatable("screen.superpipeslide.status." + status.toLowerCase(java.util.Locale.ROOT));
        RouteEditorGui.stamp(graphics, this.font, statusLabel.getString(), card.right() - this.font.width(statusLabel) - 35, card.y() + 4, RouteEditorGui.statusColor(status));
        SPSGui.Rect edit = new SPSGui.Rect(card.right() - 20, card.y() + 3, 16, 16);
        RouteEditorGui.toolButton(graphics, edit, SPSGui.Icon.EDIT, false, edit.contains(mouseX, mouseY), RouteEditorGui.INK_SECONDARY);
        this.addClick(edit, () -> this.minecraft.setScreen(new RouteLineScreen(line.id(), true)), Component.translatable("screen.superpipeslide.action.edit_route"));
        this.addClick(new SPSGui.Rect(card.x(), card.y(), card.width() - 22, card.height()), () -> this.minecraft.setScreen(new RouteLineScreen(line.id(), false)), Component.translatable("screen.superpipeslide.action.open_route"));

        SPSGui.LineSummary lineSummary = SPSGui.lineSummary(line);
        String summary = Component.translatable("screen.superpipeslide.route.summary", lineSummary.layoutCount(), lineSummary.stationCount(), lineSummary.problemCount()).getString();
        int summaryY = card.y() + 5 + RouteEditorGui.nameBlockHeight(line.translatedNames()) + 2;
        int mapY = summaryY + 9;
        int mapHeight = Math.max(22, card.bottom() - mapY - (ellipsis ? 11 : 4));
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, summary, Math.round((card.width() - 22) / 0.72F)), card.x() + 10, summaryY, RouteEditorGui.INK_SECONDARY, 0.72F);
        SPSGui.representativeLayout(line).ifPresentOrElse(
                layout -> SPSGui.stationMap(graphics, this.font, layout, line.themeColors(), new SPSGui.Rect(card.x() + 10, mapY, card.width() - 20, mapHeight), false, line.id().hashCode(), true),
                () -> SPSGui.stationMap(graphics, this.font, List.of(), line.themeColors(), new SPSGui.Rect(card.x() + 10, mapY, card.width() - 20, mapHeight), false, line.id().hashCode(), true));
        if (ellipsis) {
            int phase = (int) ((System.currentTimeMillis() / 120L) % 6L);
            int dotX = card.x() + card.width() / 2 - 5;
            for (int i = 0; i < 3; i++) {
                int dy = phase == i ? -1 : 0;
                graphics.fill(dotX + i * 4, card.bottom() - 7 + dy, dotX + i * 4 + 2, card.bottom() - 5 + dy, RouteEditorGui.INK_MUTED);
            }
        }
    }

    private List<RouteLine> filteredLines() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        return ClientRouteDataCache.routeLines().stream()
                .filter(line -> query.isEmpty() || line.displayName().toLowerCase(java.util.Locale.ROOT).contains(query) || SPSGui.translatedNamesLine(line.translatedNames()).toLowerCase(java.util.Locale.ROOT).contains(query))
                .sorted(Comparator.comparing((RouteLine line) -> "Draft".equals(SPSGui.lineStatus(line))).thenComparing(RouteLine::displayName))
                .toList();
    }

    private static int directionGroupCount(RouteLine line) {
        return SPSGui.lineSummary(line).layoutCount();
    }

    private static int lineCardHeight(RouteLine line, boolean ellipsis) {
        int height = SPSGui.translatedNamesLine(line.translatedNames()).isEmpty() ? 58 : 68;
        return ellipsis ? height + 8 : height;
    }

    private void updateSearchBoxState(boolean hasLines) {
        if (this.searchBox != null) {
            this.searchBox.visible = hasLines;
            this.searchBox.active = hasLines;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.panel.contains(mouseX, mouseY) && !ClientRouteDataCache.routeLines().isEmpty()) {
            this.scroll = Math.max(0.0D, Math.min(maxScroll(), this.scroll - scrollY * 14.0D));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private double maxScroll() {
        int contentHeight = filteredLines().stream()
                .mapToInt(line -> lineCardHeight(line, directionGroupCount(line) > 1) + 5)
                .sum();
        int visibleHeight = this.editorContent().height() - 21;
        return Math.max(0, contentHeight - visibleHeight);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick);
    }
}
