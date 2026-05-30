package dev.marblegate.superpipeslide.client.gui.projection;

import dev.marblegate.superpipeslide.client.core.projection.preview.ProjectionLayoutPreviewPainter;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.client.gui.route.RouteEditorGui;
import dev.marblegate.superpipeslide.client.gui.route.RouteEditorScreenBase;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutSummary;
import dev.marblegate.superpipeslide.common.core.projection.layout.ProjectionLayoutTarget;
import dev.marblegate.superpipeslide.network.projection.ClientboundOpenProjectionLayoutDesignerPayload;
import dev.marblegate.superpipeslide.network.projection.ServerboundProjectionLayoutDeletePayload;
import dev.marblegate.superpipeslide.network.projection.ServerboundProjectionLayoutSelectPayload;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ProjectionLayoutLibraryScreen extends RouteEditorScreenBase {
    private ClientboundOpenProjectionLayoutDesignerPayload payload;
    private double scroll;
    private SPSGui.Rect cardsArea = new SPSGui.Rect(0, 0, 0, 0);
    private final List<CardTarget> visibleCards = new ArrayList<>();

    public ProjectionLayoutLibraryScreen(ClientboundOpenProjectionLayoutDesignerPayload payload) {
        super(Component.translatable("screen.superpipeslide.projection_designer.library"));
        this.payload = payload;
    }

    public void applyPayload(ClientboundOpenProjectionLayoutDesignerPayload payload) {
        this.payload = payload;
        this.scroll = Math.min(this.scroll, maxScroll());
    }

    @Override
    protected SPSGui.Rect createPanelRect() {
        return RouteEditorGui.panelRect(this.width, this.height, 594, 342);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.beginFrame();
        this.drawEditorFrame(graphics);
        List<SPSGui.IconButton> actions = List.of(new SPSGui.IconButton(SPSGui.Icon.PLUS, RouteEditorGui.BLUE, false));
        this.drawTitle(graphics, Component.translatable("screen.superpipeslide.projection_designer"), true, actions, mouseX, mouseY);
        SPSGui.Rect add = RouteEditorGui.titleActionBounds(this.panel, 0);
        this.addClick(add, this::openCreate, Component.translatable("screen.superpipeslide.projection_designer.new"));
        this.drawDocumentHeader(graphics, SPSGui.Icon.LAYOUT,
                List.of(Component.translatable("screen.superpipeslide.projection_designer"), Component.translatable("screen.superpipeslide.projection_designer.library")),
                Component.translatable(activeTarget().translationKey()), RouteEditorGui.BLUE, 31);

        SPSGui.Rect content = this.editorContent();
        int top = this.documentBodyY();
        SPSGui.Rect tabs = new SPSGui.Rect(content.x(), top, content.width(), 22);
        drawTargetTabs(graphics, tabs, mouseX, mouseY);
        SPSGui.Rect header = new SPSGui.Rect(content.x(), tabs.bottom() + 6, content.width(), 26);
        RouteEditorGui.paperSection(graphics, header, false, false);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.my_layouts"), header.x() + 9, header.y() + 9, RouteEditorGui.INK_PRIMARY);
        List<ProjectionLayoutSummary> entries = summaries();
        String total = Component.translatable("screen.superpipeslide.projection_designer.count", entries.size()).getString();
        int stampRight = header.right() - 9;
        int stampMaxWidth = Math.max(24, header.width() - 118);
        String stamp = SPSGui.ellipsize(this.font, total, stampMaxWidth);
        RouteEditorGui.stamp(graphics, this.font, stamp, stampRight - this.font.width(stamp) - 9, header.y() + 7, RouteEditorGui.INK_MUTED);

        this.cardsArea = new SPSGui.Rect(content.x(), header.bottom() + 7, content.width(), content.bottom() - header.bottom() - 7);
        graphics.enableScissor(this.cardsArea.x(), this.cardsArea.y(), this.cardsArea.right(), this.cardsArea.bottom());
        if (entries.isEmpty()) {
            drawEmptyState(graphics, this.cardsArea, mouseX, mouseY);
        } else {
            drawCards(graphics, entries, mouseX, mouseY);
        }
        graphics.disableScissor();
        RouteEditorGui.scrollEdges(graphics, this.cardsArea, this.scroll > 0.5D, this.scroll < maxScroll() - 0.5D, false);
        RouteEditorGui.thinScrollbar(graphics, this.cardsArea, this.scroll, maxScroll(), this.cardsArea.contains(mouseX, mouseY));
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.renderTooltips(graphics, mouseX, mouseY);
    }

    private void drawTargetTabs(GuiGraphicsExtractor graphics, SPSGui.Rect rect, int mouseX, int mouseY) {
        ProjectionLayoutTarget[] targets = ProjectionLayoutTarget.values();
        int gap = 5;
        int available = Math.max(1, rect.width() - gap * (targets.length - 1));
        int width = Math.max(1, Math.min(126, available / targets.length));
        int x = rect.x();
        for (ProjectionLayoutTarget target : targets) {
            SPSGui.Rect tab = new SPSGui.Rect(x, rect.y(), width, rect.height());
            boolean active = target == activeTarget();
            boolean hovered = tab.contains(mouseX, mouseY);
            int bg = active ? 0xFFE9F2F6 : hovered ? 0xFFFFFBF1 : RouteEditorGui.PAPER_ELEVATED;
            int border = active ? RouteEditorGui.BLUE : hovered ? 0xFFB8C9D4 : RouteEditorGui.PAPER_LINE;
            int color = active ? RouteEditorGui.BLUE : RouteEditorGui.INK_SECONDARY;
            graphics.fill(tab.x(), tab.y(), tab.right(), tab.bottom(), bg);
            graphics.outline(tab.x(), tab.y(), tab.width(), tab.height(), border);
            SPSGui.centeredText(graphics, this.font, Component.translatable(target.translationKey()), tab.x() + tab.width() / 2, tab.y() + 7, color);
            this.addClick(tab, () -> switchTarget(target), Component.translatable(target.translationKey()));
            x += width + gap;
        }
    }

    private void drawCards(GuiGraphicsExtractor graphics, List<ProjectionLayoutSummary> entries, int mouseX, int mouseY) {
        this.visibleCards.clear();
        int gap = 7;
        int columns = libraryColumns();
        int cardWidth = (this.cardsArea.width() - gap * (columns - 1)) / columns;
        int cardHeight = 94;
        for (int i = 0; i < entries.size(); i++) {
            ProjectionLayoutSummary summary = entries.get(i);
            int col = i % columns;
            int row = i / columns;
            int x = this.cardsArea.x() + col * (cardWidth + gap);
            int y = this.cardsArea.y() + row * (cardHeight + gap) - (int) this.scroll;
            SPSGui.Rect card = new SPSGui.Rect(x, y, col == columns - 1 ? this.cardsArea.right() - x : cardWidth, cardHeight);
            if (card.bottom() < this.cardsArea.y() || card.y() > this.cardsArea.bottom()) {
                continue;
            }
            drawCard(graphics, card, summary, mouseX, mouseY);
            this.visibleCards.add(new CardTarget(card, summary));
        }
    }

    private void drawCard(GuiGraphicsExtractor graphics, SPSGui.Rect card, ProjectionLayoutSummary summary, int mouseX, int mouseY) {
        boolean active = this.payload.selectedLayoutId(activeTarget()).filter(summary.id()::equals).isPresent();
        boolean hovered = card.contains(mouseX, mouseY);
        if (summary.invalid()) {
            graphics.fill(card.x(), card.y(), card.right(), card.bottom(), hovered ? 0xFFFFEBE5 : 0xFFFFDFD9);
            graphics.outline(card.x(), card.y(), card.width(), card.height(), RouteEditorGui.DANGER);
            SPSGui.icon(graphics, new SPSGui.Rect(card.x() + 7, card.y() + 7, 14, 14), SPSGui.Icon.WARNING, RouteEditorGui.DANGER);
            SPSGui.text(graphics, this.font, SPSGui.ellipsize(this.font, summary.name(), card.width() - 36), card.x() + 25, card.y() + 10, RouteEditorGui.DANGER);
            SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, summary.errorMessage(), Math.round((card.width() - 14) / 0.66F)), card.x() + 7, card.y() + 32, RouteEditorGui.INK_SECONDARY, 0.66F);
            SPSGui.Rect delete = new SPSGui.Rect(card.right() - 23, card.bottom() - 21, 16, 16);
            RouteEditorGui.toolButton(graphics, delete, SPSGui.Icon.REMOVE, false, delete.contains(mouseX, mouseY), RouteEditorGui.DANGER);
            this.addPriorityClick(delete, () -> delete(summary), Component.translatable("screen.superpipeslide.projection_designer.delete"));
            return;
        }

        RouteEditorGui.paperSection(graphics, card, hovered, active);
        SPSGui.Rect preview = new SPSGui.Rect(card.x() + 6, card.y() + 6, card.width() - 12, 50);
        ProjectionLayoutPreviewPainter.draw(graphics, this.font, summary.preview(), preview, active);
        SPSGui.text(graphics, this.font, SPSGui.ellipsize(this.font, summary.name(), card.width() - 54), card.x() + 7, card.y() + 63, active ? RouteEditorGui.BLUE : RouteEditorGui.INK_PRIMARY);
        String size = String.format(java.util.Locale.ROOT, "%.2f x %.2f", summary.canvas().width(), summary.canvas().height());
        SPSGui.smallText(graphics, this.font, size, card.x() + 7, card.y() + 78, RouteEditorGui.INK_MUTED, 0.66F);
        SPSGui.Rect edit = new SPSGui.Rect(card.right() - 43, card.bottom() - 21, 16, 16);
        SPSGui.Rect remove = new SPSGui.Rect(card.right() - 23, card.bottom() - 21, 16, 16);
        RouteEditorGui.toolButton(graphics, edit, SPSGui.Icon.EDIT, false, edit.contains(mouseX, mouseY), RouteEditorGui.BLUE);
        RouteEditorGui.toolButton(graphics, remove, SPSGui.Icon.REMOVE, false, remove.contains(mouseX, mouseY), RouteEditorGui.DANGER);
        this.addPriorityClick(edit, () -> openCanvas(summary), Component.translatable("screen.superpipeslide.projection_designer.edit"));
        this.addPriorityClick(remove, () -> delete(summary), Component.translatable("screen.superpipeslide.projection_designer.delete"));
        this.addClick(card, () -> select(summary), Component.translatable("screen.superpipeslide.projection_designer.select"));
    }

    private void drawEmptyState(GuiGraphicsExtractor graphics, SPSGui.Rect area, int mouseX, int mouseY) {
        this.visibleCards.clear();
        int availableWidth = Math.max(0, area.width() - 16);
        int availableHeight = Math.max(0, area.height() - 12);
        if (availableWidth < 32 || availableHeight < 24) {
            return;
        }
        int emptyWidth = Math.min(260, availableWidth);
        int emptyHeight = Math.min(112, availableHeight);
        int emptyX = area.x() + Math.max(0, (area.width() - emptyWidth) / 2);
        int emptyY = area.y() + Math.max(0, (area.height() - emptyHeight) / 2);
        SPSGui.Rect empty = new SPSGui.Rect(emptyX, emptyY, emptyWidth, emptyHeight);
        RouteEditorGui.worksheetPane(graphics, empty, false, false);
        int centerX = empty.x() + empty.width() / 2;
        if (empty.height() >= 96) {
            SPSGui.icon(graphics, new SPSGui.Rect(centerX - 9, empty.y() + 13, 18, 18), SPSGui.Icon.LAYOUT, RouteEditorGui.BLUE);
            SPSGui.centeredText(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.empty"), centerX, empty.y() + 39, RouteEditorGui.INK_PRIMARY);
            SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, Component.translatable("screen.superpipeslide.projection_designer.empty_hint").getString(), Math.round((empty.width() - 24) / 0.72F)), empty.x() + 12, empty.y() + 54, RouteEditorGui.INK_MUTED, 0.72F);
            drawEmptyCreateButton(graphics, empty, mouseX, mouseY);
        } else if (empty.height() >= 62) {
            SPSGui.centeredText(graphics, this.font, Component.translatable("screen.superpipeslide.projection_designer.empty"), centerX, empty.y() + 12, RouteEditorGui.INK_PRIMARY);
            drawEmptyCreateButton(graphics, empty, mouseX, mouseY);
        } else {
            SPSGui.centeredText(graphics, this.font, SPSGui.ellipsize(this.font, Component.translatable("screen.superpipeslide.projection_designer.empty").getString(), empty.width() - 18), centerX, empty.y() + Math.max(8, empty.height() / 2 - 4), RouteEditorGui.INK_PRIMARY);
        }
    }

    private void drawEmptyCreateButton(GuiGraphicsExtractor graphics, SPSGui.Rect empty, int mouseX, int mouseY) {
        int buttonWidth = Math.min(112, empty.width() - 24);
        if (buttonWidth < 56 || empty.height() < 40) {
            return;
        }
        SPSGui.Rect create = new SPSGui.Rect(empty.x() + (empty.width() - buttonWidth) / 2, empty.bottom() - 27, buttonWidth, 18);
        RouteEditorGui.actionButton(graphics, this.font, create, SPSGui.Icon.PLUS, Component.translatable("screen.superpipeslide.projection_designer.new"), false, create.contains(mouseX, mouseY), RouteEditorGui.BLUE);
        this.addClick(create, this::openCreate, Component.translatable("screen.superpipeslide.projection_designer.new"));
    }

    private void openCreate() {
        Minecraft.getInstance().setScreen(new ProjectionLayoutCreateScreen(this.payload));
    }

    private void openCanvas(ProjectionLayoutSummary summary) {
        if (!summary.invalid() && summary.preview() != null) {
            Minecraft.getInstance().setScreen(new ProjectionLayoutCanvasScreen(this.payload, summary.preview()));
        }
    }

    private void select(ProjectionLayoutSummary summary) {
        if (summary.invalid()) {
            return;
        }
        java.util.Map<ProjectionLayoutTarget, UUID> selected = new EnumMap<>(ProjectionLayoutTarget.class);
        selected.putAll(this.payload.selectedLayoutIds());
        selected.put(activeTarget(), summary.id());
        this.payload = new ClientboundOpenProjectionLayoutDesignerPayload(activeTarget(), selected, this.payload.layouts(), false);
        ClientPacketDistributor.sendToServer(new ServerboundProjectionLayoutSelectPayload(activeTarget(), summary.id()));
    }

    private void delete(ProjectionLayoutSummary summary) {
        this.requestDangerConfirmation(Component.translatable("screen.superpipeslide.projection_designer.delete"),
                Component.translatable("screen.superpipeslide.projection_designer.delete_hint", summary.name()),
                Component.translatable("screen.superpipeslide.projection_designer.delete"),
                () -> ClientPacketDistributor.sendToServer(new ServerboundProjectionLayoutDeletePayload(summary.id())));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && doubleClick) {
            for (CardTarget target : this.visibleCards) {
                if (!target.summary().invalid() && target.bounds().contains(event.x(), event.y())) {
                    openCanvas(target.summary());
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.cardsArea.contains(mouseX, mouseY)) {
            this.scroll = Math.max(0.0D, Math.min(maxScroll(), this.scroll - scrollY * 22.0D));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private double maxScroll() {
        int columns = libraryColumns();
        int rows = (summaries().size() + columns - 1) / columns;
        return Math.max(0, rows * 101 - 7 - this.cardsArea.height());
    }

    private int libraryColumns() {
        if (this.cardsArea.width() >= 460) {
            return 3;
        }
        return this.cardsArea.width() >= 260 ? 2 : 1;
    }

    private ProjectionLayoutTarget activeTarget() {
        return this.payload.activeTarget();
    }

    private List<ProjectionLayoutSummary> summaries() {
        ProjectionLayoutTarget target = activeTarget();
        return this.payload.layouts().stream().filter(summary -> summary.target() == target).toList();
    }

    private void switchTarget(ProjectionLayoutTarget target) {
        if (target == activeTarget()) {
            return;
        }
        this.payload = this.payload.withActiveTarget(target);
        this.scroll = 0.0D;
    }

    private record CardTarget(SPSGui.Rect bounds, ProjectionLayoutSummary summary) {}
}
