package dev.marblegate.superpipeslide.client.gui.anchor;

import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import dev.marblegate.superpipeslide.client.fullmap.render.SmoothGuiPrimitives;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;
import dev.marblegate.superpipeslide.client.gui.base.SPSScreen;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorKind;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorMode;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.fold.FoldAnchorRef;
import dev.marblegate.superpipeslide.common.registry.SPSBlocks;
import dev.marblegate.superpipeslide.network.editor.ClientboundEditorResultPayload;
import dev.marblegate.superpipeslide.network.fold.ClientboundOpenFoldAnchorEditorPayload;
import dev.marblegate.superpipeslide.network.fold.ServerboundFoldAnchorSavePayload;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class FoldAnchorEditorScreen extends SPSScreen {
    private static final int SWITCH_ANIMATION_TICKS = 12;
    private static final int PANEL_WIDTH = 430;
    private static final int PANEL_HEIGHT = 238;
    private static final int TITLE_BAR_HEIGHT = 23;
    private static final int OUTER_PAD = 8;
    private static final int HEADER_HEIGHT = 48;
    private static final int LINK_PREVIEW_HEIGHT = 40;
    private static final int IDENTITY_WIDTH = 104;
    private static final int GAP = 6;
    private static final long RESULT_MESSAGE_MS = 1800L;

    private FoldAnchorNode anchor;
    private int sourceConnectionCount;
    private List<ClientboundOpenFoldAnchorEditorPayload.Candidate> candidates;
    private FoldAnchorMode draftMode;
    private Optional<FoldAnchorRef> selectedTarget;
    private EditBox nameBox;
    private EditBox searchBox;
    private int switchCooldownTicks;
    private int switchAnimationTicks;
    private SPSGui.Rect listArea = new SPSGui.Rect(0, 0, 0, 0);
    private double listScroll;
    private Optional<UUID> pendingSaveRequestId = Optional.empty();
    private String lastResultMessage = "";
    private boolean lastResultAccepted;
    private long lastResultUntilMillis;

    public FoldAnchorEditorScreen(ClientboundOpenFoldAnchorEditorPayload payload) {
        super(Component.translatable("screen.superpipeslide.fold_anchor"));
        this.applyState(payload);
    }

    @Override
    protected SPSGui.Rect createPanelRect() {
        int width = Math.min(PANEL_WIDTH, this.width - 18);
        int height = Math.min(PANEL_HEIGHT, this.height - 14);
        return new SPSGui.Rect((this.width - width) / 2, (this.height - height) / 2, width, height);
    }

    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();
        String name = this.anchor.mode() == FoldAnchorMode.A_END ? this.anchor.displayName() : "";
        this.nameBox = this.borderlessBox(0, 0, 120, name);
        this.nameBox.setTextColor(0xFFEAF7F4);
        this.nameBox.setTextColorUneditable(0xFF5B6670);
        this.nameBox.visible = this.draftMode == FoldAnchorMode.A_END;
        this.searchBox = this.borderlessBox(0, 0, 90, "");
        this.searchBox.setTextColor(0xFFEAF7F4);
        this.searchBox.setTextColorUneditable(0xFF5B6670);
        this.searchBox.visible = this.draftMode == FoldAnchorMode.B_END;
    }

    public void applyPayload(ClientboundOpenFoldAnchorEditorPayload payload) {
        this.applyState(payload);
        this.rebuildWidgets();
    }

    private void applyState(ClientboundOpenFoldAnchorEditorPayload payload) {
        this.anchor = payload.anchor();
        this.sourceConnectionCount = payload.sourceConnectionCount();
        this.candidates = payload.candidates();
        this.draftMode = payload.anchor().mode();
        this.selectedTarget = payload.anchor().boundTarget();
        this.listScroll = 0.0D;
        this.pendingSaveRequestId = Optional.empty();
    }

    public void handleEditorResult(ClientboundEditorResultPayload payload) {
        if (this.pendingSaveRequestId.filter(payload.requestId()::equals).isPresent()) {
            this.pendingSaveRequestId = Optional.empty();
            this.lastResultAccepted = payload.accepted();
            this.lastResultMessage = payload.message();
            this.lastResultUntilMillis = System.currentTimeMillis() + RESULT_MESSAGE_MS;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.switchCooldownTicks > 0) {
            this.switchCooldownTicks--;
        }
        if (this.switchAnimationTicks > 0) {
            this.switchAnimationTicks--;
        }
        if (this.nameBox != null) {
            this.nameBox.visible = this.draftMode == FoldAnchorMode.A_END;
        }
        if (this.searchBox != null) {
            this.searchBox.visible = this.draftMode == FoldAnchorMode.B_END;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.beginFrame();
        FoldAnchorSkin skin = this.skin();
        boolean canSave = this.canSave();
        this.renderCalibrationFrame(graphics, skin);
        this.renderTitleBar(graphics, skin, canSave, mouseX, mouseY);

        SPSGui.Rect content = this.contentRect();
        SPSGui.Rect header = new SPSGui.Rect(content.x(), content.y(), content.width(), HEADER_HEIGHT);
        this.renderDeviceHeader(graphics, header, skin, mouseX, mouseY);

        SPSGui.Rect lower = new SPSGui.Rect(content.x(), header.bottom() + GAP, content.width(), content.bottom() - header.bottom() - GAP);
        SPSGui.Rect linkPreview = new SPSGui.Rect(lower.x(), lower.bottom() - LINK_PREVIEW_HEIGHT, lower.width(), LINK_PREVIEW_HEIGHT);
        SPSGui.Rect body = new SPSGui.Rect(lower.x(), lower.y(), lower.width(), lower.height() - LINK_PREVIEW_HEIGHT - GAP);

        if (this.draftMode == FoldAnchorMode.UNCONFIGURED) {
            this.renderUnconfiguredBody(graphics, skin, body, mouseX, mouseY);
        } else {
            this.renderConfiguredBody(graphics, skin, body, mouseX, mouseY);
        }
        this.renderModeTransition(graphics, skin, body);
        this.renderLinkPreview(graphics, skin, linkPreview, canSave, mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.renderTooltips(graphics, mouseX, mouseY);
    }

    private SPSGui.Rect contentRect() {
        return new SPSGui.Rect(this.panel.x() + OUTER_PAD, this.panel.y() + TITLE_BAR_HEIGHT + 6, this.panel.width() - OUTER_PAD * 2, this.panel.height() - TITLE_BAR_HEIGHT - 14);
    }

    private void renderCalibrationFrame(GuiGraphicsExtractor graphics, FoldAnchorSkin skin) {
        graphics.fill(this.panel.x(), this.panel.y(), this.panel.right(), this.panel.bottom(), 0xF21B1E22);
        graphics.outline(this.panel.x(), this.panel.y(), this.panel.width(), this.panel.height(), 0xFF525960);
        graphics.fill(this.panel.x() + 1, this.panel.y() + 1, this.panel.right() - 1, this.panel.y() + 2, 0x445B6670);
        graphics.fill(this.panel.x() + 2, this.panel.bottom() - 3, this.panel.right() - 2, this.panel.bottom() - 1, 0x99101418);

        SPSGui.Rect screen = new SPSGui.Rect(this.panel.x() + 4, this.panel.y() + TITLE_BAR_HEIGHT, this.panel.width() - 8, this.panel.height() - TITLE_BAR_HEIGHT - 4);
        graphics.fill(screen.x(), screen.y(), screen.right(), screen.bottom(), skin.screen());
        graphics.fillGradient(screen.x(), screen.y(), screen.right(), screen.y() + 20, SPSGui.withAlpha(skin.accent(), 0x14), 0x00000000);
        graphics.fillGradient(screen.x(), screen.bottom() - 18, screen.right(), screen.bottom(), 0x00000000, 0xAA000000);
        this.drawPixelField(graphics, screen, skin);
        this.drawCornerHardware(graphics, this.panel, skin);
    }

    private void drawPixelField(GuiGraphicsExtractor graphics, SPSGui.Rect screen, FoldAnchorSkin skin) {
        int minor = skin.kind() == FoldAnchorKind.SPACE ? SPSGui.withAlpha(skin.accent(), 0x11) : SPSGui.withAlpha(skin.energy(), 0x0E);
        int major = skin.kind() == FoldAnchorKind.SPACE ? SPSGui.withAlpha(skin.accent(), 0x23) : SPSGui.withAlpha(skin.energy(), 0x1B);
        for (int x = screen.x() + 9; x < screen.right(); x += 12) {
            graphics.fill(x, screen.y() + 1, x + 1, screen.bottom() - 1, minor);
        }
        for (int y = screen.y() + 8; y < screen.bottom(); y += 12) {
            graphics.fill(screen.x() + 1, y, screen.right() - 1, y + 1, minor);
        }
        for (int x = screen.x() + 29; x < screen.right(); x += 48) {
            graphics.fill(x, screen.y() + 2, x + 1, screen.bottom() - 2, major);
        }
        for (int y = screen.y() + 28; y < screen.bottom(); y += 48) {
            graphics.fill(screen.x() + 2, y, screen.right() - 2, y + 1, major);
        }
        long now = System.currentTimeMillis();
        if (skin.kind() == FoldAnchorKind.SPACE) {
            int span = Math.max(1, screen.width() + 80);
            int sweep = (int) ((now / 28L) % span) - 40;
            graphics.fill(screen.x() + sweep, screen.y() + 1, Math.min(screen.right(), screen.x() + sweep + 28), screen.y() + 2, SPSGui.withAlpha(skin.energy(), 0x58));
            for (int i = 0; i < 16; i++) {
                int px = screen.x() + 10 + Math.floorMod(i * 37, Math.max(1, screen.width() - 18));
                int py = screen.y() + 9 + Math.floorMod(i * 23, Math.max(1, screen.height() - 18));
                graphics.fill(px, py, px + 2, py + 2, SPSGui.withAlpha(skin.accent(), i % 3 == 0 ? 0x35 : 0x1B));
            }
            return;
        }
        double breath = 0.5D + 0.5D * Math.sin(now / 360.0D);
        int rift = SPSGui.withAlpha(skin.energy(), (int) Math.round(18 + breath * 32));
        int centerX = screen.x() + screen.width() / 2;
        for (int i = -3; i <= 3; i++) {
            int y = screen.y() + 14 + Math.floorMod(i * 31 + (int) (now / 90L), Math.max(1, screen.height() - 32));
            graphics.fill(centerX + i * 3, y, centerX + i * 3 + 2, y + 8, rift);
        }
        for (int i = 0; i < 18; i++) {
            int px = screen.x() + 8 + Math.floorMod(i * 41, Math.max(1, screen.width() - 16));
            int py = screen.y() + 8 + Math.floorMod(i * 17 + (int) (now / 180L), Math.max(1, screen.height() - 16));
            graphics.fill(px, py, px + 1 + i % 2, py + 1 + (i + 1) % 2, SPSGui.withAlpha(i % 4 == 0 ? skin.energy() : skin.accent(), 0x24));
        }
    }

    private void drawCornerHardware(GuiGraphicsExtractor graphics, SPSGui.Rect frame, FoldAnchorSkin skin) {
        int metal = 0xFF343A40;
        int shine = 0xFF6D7680;
        int hot = SPSGui.withAlpha(skin.accent(), 0xAA);
        int[][] corners = {
                { frame.x() + 4, frame.y() + 4, 1, 1 },
                { frame.right() - 16, frame.y() + 4, -1, 1 },
                { frame.x() + 4, frame.bottom() - 16, 1, -1 },
                { frame.right() - 16, frame.bottom() - 16, -1, -1 }
        };
        for (int[] corner : corners) {
            int x = corner[0];
            int y = corner[1];
            graphics.fill(x, y, x + 12, y + 12, 0xB6121518);
            graphics.outline(x, y, 12, 12, metal);
            graphics.fill(x + 2, y + 2, x + 6, y + 4, shine);
            graphics.fill(x + 8, y + 8, x + 10, y + 10, hot);
        }
    }

    private void renderModeTransition(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, SPSGui.Rect rect) {
        if (this.switchAnimationTicks <= 0 || rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        double progress = 1.0D - this.switchAnimationTicks / (double) SWITCH_ANIMATION_TICKS;
        double eased = easeOutCubic(progress);
        int veilAlpha = (int) Math.round((1.0D - progress) * 56.0D);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), SPSGui.withAlpha(skin.dark(), veilAlpha));

        int sweepX = rect.x() + (int) Math.round(eased * rect.width());
        int core = SPSGui.withAlpha(skin.energy(), 0xB8);
        int soft = SPSGui.withAlpha(skin.accent(), 0x40);
        graphics.fill(Math.max(rect.x(), sweepX - 1), rect.y() + 2, Math.min(rect.right(), sweepX + 1), rect.bottom() - 2, core);
        for (int i = 0; i < 5; i++) {
            int x = sweepX - 18 + i * 8;
            if (x >= rect.x() && x < rect.right()) {
                graphics.fill(x, rect.y() + 5, x + 4, rect.y() + 6, soft);
                graphics.fill(x, rect.bottom() - 7, x + 4, rect.bottom() - 6, soft);
            }
        }

        int bracket = Math.max(8, (int) Math.round(18.0D - progress * 7.0D));
        this.drawBracketCorners(graphics, rect.x() + 3, rect.y() + 3, rect.width() - 6, rect.height() - 6, SPSGui.withAlpha(skin.energy(), 0x72), bracket);
    }

    private void renderTitleBar(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, boolean canSave, int mouseX, int mouseY) {
        SPSGui.Rect title = new SPSGui.Rect(this.panel.x() + 1, this.panel.y() + 1, this.panel.width() - 2, TITLE_BAR_HEIGHT - 1);
        graphics.fill(title.x(), title.y(), title.right(), title.bottom(), 0xF2181B1F);
        graphics.fill(title.x(), title.bottom() - 1, title.right(), title.bottom(), 0xFF4A5359);
        SPSGui.Rect back = new SPSGui.Rect(this.panel.x() + 6, this.panel.y() + 4, 15, 15);
        this.drawIconButton(graphics, back, skin, SPSGui.Icon.BACK, false, back.contains(mouseX, mouseY), Component.translatable("screen.superpipeslide.action.back"));
        this.addClick(back, this::onBack, this.tooltip("screen.superpipeslide.action.back", "screen.superpipeslide.fold_anchor.tooltip.back"));

        String titleText = Component.translatable("screen.superpipeslide.fold_anchor.calibrator").getString();
        SPSGui.text(graphics, this.font, SPSGui.ellipsize(this.font, titleText, this.panel.width() - 110), back.right() + 6, this.panel.y() + 8, skin.text());
        int pulse = (int) ((System.currentTimeMillis() / 360L) % 4L);
        for (int i = 0; i < 4; i++) {
            int color = i == pulse ? skin.energy() : 0xFF3A444A;
            graphics.fill(this.panel.right() - 47 + i * 6, this.panel.y() + 9, this.panel.right() - 44 + i * 6, this.panel.y() + 12, color);
        }

        SPSGui.Rect save = new SPSGui.Rect(this.panel.right() - 21, this.panel.y() + 4, 15, 15);
        this.drawIconButton(graphics, save, skin, SPSGui.Icon.SAVE, !canSave, save.contains(mouseX, mouseY), canSave ? Component.translatable("screen.superpipeslide.fold_anchor.save_calibration") : this.saveDisabledMessage());
        this.addClick(save, canSave ? this::save : () -> {}, canSave ? this.tooltip("screen.superpipeslide.fold_anchor.save_calibration", "screen.superpipeslide.fold_anchor.tooltip.save") : this.saveDisabledMessage());
    }

    private void drawIconButton(GuiGraphicsExtractor graphics, SPSGui.Rect rect, FoldAnchorSkin skin, SPSGui.Icon icon, boolean disabled, boolean hovered, Component tooltip) {
        int fill = disabled ? 0xD70A0E12 : hovered ? SPSGui.withAlpha(skin.accent(), 0x24) : 0xE20F151A;
        int border = disabled ? 0x88525B64 : hovered ? skin.energy() : SPSGui.withAlpha(skin.accent(), 0x78);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), border);
        graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.y() + 2, hovered ? SPSGui.withAlpha(skin.energy(), 0x44) : SPSGui.withAlpha(skin.accent(), 0x18));
        SPSGui.icon(graphics, rect, icon, disabled ? skin.textDisabled() : hovered ? 0xFFFFFFFF : skin.textSecondary());
    }

    private void renderDeviceHeader(GuiGraphicsExtractor graphics, SPSGui.Rect header, FoldAnchorSkin skin, int mouseX, int mouseY) {
        this.drawPanelShell(graphics, header, skin, header.contains(mouseX, mouseY), true);
        SPSGui.Rect preview = new SPSGui.Rect(header.x() + 7, header.y() + 6, 38, 36);
        this.drawAnchorModelPreview(graphics, skin, preview);

        int textX = preview.right() + 8;
        String title = this.kindName().getString();
        String subtitle = Component.translatable(skin.subtitleKey()).getString();
        String state = this.modeName(this.draftMode).getString();
        String count = Component.translatable("screen.superpipeslide.fold_anchor.connections").getString() + " " + this.sourceConnectionCount + "/1";
        int badgeWidth = Math.min(88, Math.max(48, this.font.width(state) + 12));
        int countBadgeWidth = Math.min(104, Math.max(54, this.font.width(count) + 12));
        int rightColumnWidth = Math.max(badgeWidth, countBadgeWidth);
        SPSGui.text(graphics, this.font, SPSGui.ellipsize(this.font, title, Math.max(40, header.width() - rightColumnWidth - 64)), textX, header.y() + 8, skin.text());
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, subtitle, Math.max(48, Math.round((header.width() - rightColumnWidth - 70) / 0.62F))), textX, header.y() + 22, skin.textMuted(), 0.62F);
        SPSGui.smallText(graphics, this.font, this.anchor.anchorId().blockPos().toShortString(), textX, header.y() + 34, skin.textSecondary(), 0.56F);

        SPSGui.Rect badge = new SPSGui.Rect(header.right() - badgeWidth - 7, header.y() + 8, badgeWidth, 16);
        this.drawBadge(graphics, badge, skin, state, this.draftMode == FoldAnchorMode.UNCONFIGURED ? skin.warning() : skin.accent());
        SPSGui.Rect countBadge = new SPSGui.Rect(header.right() - countBadgeWidth - 7, header.y() + 27, countBadgeWidth, 14);
        this.drawTinyBadge(graphics, countBadge, skin, count, this.sourceConnectionCount == 1 ? skin.success() : skin.warning());

        this.addClick(header, () -> {}, this.tooltip("screen.superpipeslide.fold_anchor.tooltip.header", skin.subtitleKey()));
    }

    private void drawAnchorModelPreview(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, SPSGui.Rect rect) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), 0xB0060A0D);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), SPSGui.withAlpha(skin.accent(), 0x8C));
        this.drawTypeMicroPattern(graphics, rect, skin);
        this.drawBracketCorners(graphics, rect.x() + 3, rect.y() + 3, rect.width() - 6, rect.height() - 6, skin.energy(), 5);
        ItemStack stack = new ItemStack(skin.kind() == FoldAnchorKind.SPACE ? SPSBlocks.SPACE_FOLD_ANCHOR.get() : SPSBlocks.DIMENSION_FOLD_ANCHOR.get());
        float itemScale = 1.25F;
        int itemSize = Math.round(16.0F * itemScale);
        int itemX = rect.x() + (rect.width() - itemSize) / 2;
        int itemY = rect.y() + (rect.height() - itemSize) / 2;
        graphics.pose().pushMatrix();
        graphics.pose().translate(itemX, itemY);
        graphics.pose().scale(itemScale, itemScale);
        graphics.item(stack, 0, 0, 1097);
        graphics.pose().popMatrix();
        double breath = 0.5D + 0.5D * Math.sin(System.currentTimeMillis() / (skin.kind() == FoldAnchorKind.SPACE ? 300.0D : 430.0D));
        SmoothGuiPrimitives.circle(graphics, new Vec2(rect.x() + rect.width() / 2.0D, rect.y() + rect.height() / 2.0D), 15.0D + breath * 1.6D, SPSGui.withAlpha(skin.energy(), 0x24));
    }

    private void renderUnconfiguredBody(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, SPSGui.Rect body, int mouseX, int mouseY) {
        int half = (body.width() - GAP) / 2;
        SPSGui.Rect left = new SPSGui.Rect(body.x(), body.y(), half, body.height());
        SPSGui.Rect right = new SPSGui.Rect(left.right() + GAP, body.y(), body.right() - left.right() - GAP, body.height());
        this.renderRoleChoiceCard(graphics, skin, left, FoldAnchorMode.A_END, left.contains(mouseX, mouseY), true);
        this.renderRoleChoiceCard(graphics, skin, right, FoldAnchorMode.B_END, right.contains(mouseX, mouseY), true);
        this.addClick(left, () -> this.switchMode(FoldAnchorMode.A_END), this.tooltip("screen.superpipeslide.fold_anchor.configure_a", "screen.superpipeslide.fold_anchor.tooltip.configure_a"));
        this.addClick(right, () -> this.switchMode(FoldAnchorMode.B_END), this.tooltip("screen.superpipeslide.fold_anchor.configure_b", "screen.superpipeslide.fold_anchor.tooltip.configure_b"));
    }

    private void renderConfiguredBody(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, SPSGui.Rect body, int mouseX, int mouseY) {
        SPSGui.Rect identity = new SPSGui.Rect(body.x(), body.y(), IDENTITY_WIDTH, body.height());
        SPSGui.Rect main = new SPSGui.Rect(identity.right() + GAP, body.y(), body.right() - identity.right() - GAP, body.height());
        this.renderIdentityRail(graphics, skin, identity, mouseX, mouseY);
        this.drawPanelShell(graphics, main, skin, main.contains(mouseX, mouseY), false);
        if (this.draftMode == FoldAnchorMode.A_END) {
            this.renderAEnd(graphics, skin, main);
        } else {
            this.renderBEnd(graphics, skin, main, mouseX, mouseY);
        }
    }

    private void renderIdentityRail(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, SPSGui.Rect rect, int mouseX, int mouseY) {
        this.drawPanelShell(graphics, rect, skin, rect.contains(mouseX, mouseY), false);
        int labelY = rect.y() + 7;
        SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.fold_anchor.endpoint_identity").getString(), rect.x() + 8, labelY, skin.textMuted(), 0.56F);
        int slotGap = rect.height() >= 94 ? 5 : 3;
        int slotTop = rect.y() + 21;
        int slotBottomPadding = 7;
        int available = Math.max(0, rect.bottom() - slotBottomPadding - slotTop - slotGap);
        int slotHeight = Math.max(1, Math.min(33, available / 2));
        SPSGui.Rect a = new SPSGui.Rect(rect.x() + 7, slotTop, rect.width() - 14, slotHeight);
        SPSGui.Rect b = new SPSGui.Rect(rect.x() + 7, a.bottom() + slotGap, rect.width() - 14, slotHeight);
        this.renderEndpointSlot(graphics, skin, a, FoldAnchorMode.A_END, a.contains(mouseX, mouseY));
        this.renderEndpointSlot(graphics, skin, b, FoldAnchorMode.B_END, b.contains(mouseX, mouseY));
        if (this.switchCooldownTicks <= 0) {
            this.addClick(a, () -> this.switchMode(FoldAnchorMode.A_END), this.tooltip("screen.superpipeslide.fold_anchor.switch_to_a", "screen.superpipeslide.fold_anchor.tooltip.configure_a"));
            this.addClick(b, () -> this.switchMode(FoldAnchorMode.B_END), this.tooltip("screen.superpipeslide.fold_anchor.switch_to_b", "screen.superpipeslide.fold_anchor.tooltip.configure_b"));
        }
    }

    private void renderEndpointSlot(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, SPSGui.Rect rect, FoldAnchorMode mode, boolean hovered) {
        boolean selected = this.draftMode == mode;
        int fill = selected ? SPSGui.withAlpha(skin.accent(), 0x20) : hovered ? SPSGui.withAlpha(skin.accent(), 0x10) : 0x9C070C10;
        int border = selected ? skin.energy() : hovered ? SPSGui.withAlpha(skin.energy(), 0xAA) : SPSGui.withAlpha(skin.accent(), 0x42);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), border);
        if (selected) {
            graphics.fill(rect.x(), rect.y(), rect.x() + 3, rect.bottom(), skin.energy());
        }
        int glyphSize = rect.height() < 30 ? 13 : 15;
        this.drawEndpointGlyph(graphics, skin, rect.x() + 16, rect.y() + rect.height() / 2, glyphSize, mode);
        if (rect.height() < 22) {
            return;
        }
        String titleKey = mode == FoldAnchorMode.A_END ? "screen.superpipeslide.fold_anchor.mode.a" : "screen.superpipeslide.fold_anchor.mode.b";
        String bodyKey = mode == FoldAnchorMode.A_END ? "screen.superpipeslide.fold_anchor.endpoint.publish" : "screen.superpipeslide.fold_anchor.endpoint.bind";
        int titleY = rect.y() + (rect.height() < 30 ? 4 : 5);
        int bodyY = rect.y() + (rect.height() < 30 ? 17 : 18);
        SPSGui.text(graphics, this.font, Component.translatable(titleKey), rect.x() + 31, titleY, selected ? skin.text() : skin.textSecondary());
        if (rect.height() >= 25) {
            SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, Component.translatable(bodyKey).getString(), Math.round((rect.width() - 36) / 0.50F)), rect.x() + 31, bodyY, selected ? skin.textSecondary() : skin.textMuted(), 0.50F);
        }
    }

    private void renderRoleChoiceCard(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, SPSGui.Rect rect, FoldAnchorMode mode, boolean hovered, boolean large) {
        this.drawPanelShell(graphics, rect, skin, hovered, false);
        int cx = rect.x() + rect.width() / 2;
        int iconY = rect.y() + 27;
        double phase = System.currentTimeMillis() / (mode == FoldAnchorMode.A_END ? 360.0D : 420.0D);
        int glowAlpha = (int) Math.round(26 + (0.5D + 0.5D * Math.sin(phase)) * 26);
        SmoothGuiPrimitives.circle(graphics, new Vec2(cx, iconY), 23.0D, SPSGui.withAlpha(mode == FoldAnchorMode.A_END ? skin.accent() : skin.energy(), glowAlpha));
        this.drawEndpointGlyph(graphics, skin, cx, iconY, large ? 28 : 22, mode);
        String titleKey = mode == FoldAnchorMode.A_END ? "screen.superpipeslide.fold_anchor.configure_a.short" : "screen.superpipeslide.fold_anchor.configure_b.short";
        String hintKey = mode == FoldAnchorMode.A_END ? "screen.superpipeslide.fold_anchor.endpoint.publish.long" : "screen.superpipeslide.fold_anchor.endpoint.bind.long";
        SPSGui.centeredText(graphics, this.font, Component.translatable(titleKey), cx, rect.y() + 54, skin.text());
        this.drawCenteredSmallText(graphics, this.centered(Component.translatable(hintKey).getString(), rect.width(), 0.62F), cx, rect.y() + 70, skin.textMuted(), 0.62F);
        this.drawRoleMicroDiagram(graphics, skin, new SPSGui.Rect(rect.x() + 14, rect.bottom() - 24, rect.width() - 28, 13), mode);
    }

    private void drawRoleMicroDiagram(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, SPSGui.Rect rect, FoldAnchorMode mode) {
        int y = rect.y() + rect.height() / 2;
        int leftColor = mode == FoldAnchorMode.A_END ? skin.energy() : skin.textMuted();
        int rightColor = mode == FoldAnchorMode.B_END ? skin.energy() : skin.textMuted();
        SmoothGuiPrimitives.circle(graphics, new Vec2(rect.x() + 6, y), 3.0D, leftColor);
        SmoothGuiPrimitives.circle(graphics, new Vec2(rect.right() - 6, y), 3.0D, rightColor);
        this.drawDashedEnergyLine(graphics, rect.x() + 13, rect.right() - 13, y, skin, 0.0D, mode == FoldAnchorMode.A_END ? 0.85D : -0.85D);
    }

    private String centered(String text, int width, float scale) {
        int maxWidth = Math.round((width - 16) / scale);
        return SPSGui.ellipsize(this.font, text, maxWidth);
    }

    private void renderAEnd(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, SPSGui.Rect area) {
        int x = area.x() + 10;
        int y = area.y() + 9;
        this.drawEndpointGlyph(graphics, skin, x + 9, y + 9, 17, FoldAnchorMode.A_END);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.fold_anchor.a_publish_title"), x + 25, y + 2, skin.text());
        SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.fold_anchor.a_role_hint").getString(), x, y + 22, skin.textMuted(), 0.60F);

        int inputY = y + 44;
        SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.fold_anchor.name_field").getString(), x, inputY - 11, skin.textSecondary(), 0.58F);
        this.layoutNameBox(area, inputY);
        SPSGui.Rect input = new SPSGui.Rect(this.nameBox.getX() - 3, this.nameBox.getY() - 2, this.nameBox.getWidth() + 6, 16);
        this.drawInputSlot(graphics, input, skin, this.nameBox.isFocused(), this.nameBox.getValue().isBlank());
        this.drawInputEditableIcon(graphics, this.nameBox);
        SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.fold_anchor.a_name_hint").getString(), x, inputY + 23, skin.textMuted(), 0.58F);
        if (this.anchor.mode() == FoldAnchorMode.B_END) {
            SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.fold_anchor.switch_to_a_warning").getString(), x, inputY + 35, skin.warning(), 0.56F);
        }
    }

    private void layoutNameBox(SPSGui.Rect area, int y) {
        if (this.nameBox == null) {
            return;
        }
        this.nameBox.setX(area.x() + 10);
        this.nameBox.setY(y);
        this.nameBox.setWidth(area.width() - 20);
    }

    private void renderBEnd(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, SPSGui.Rect area, int mouseX, int mouseY) {
        int x = area.x() + 10;
        int y = area.y() + 7;
        this.drawEndpointGlyph(graphics, skin, x + 9, y + 10, 16, FoldAnchorMode.B_END);
        SPSGui.text(graphics, this.font, Component.translatable("screen.superpipeslide.fold_anchor.available_a"), x + 25, y + 3, skin.text());
        this.layoutSearchBox(area);
        SPSGui.Rect search = new SPSGui.Rect(this.searchBox.getX() - 3, this.searchBox.getY() - 2, this.searchBox.getWidth() + 6, 16);
        this.drawInputSlot(graphics, search, skin, this.searchBox.isFocused(), false);
        this.drawSearchPlaceholderDark(graphics, this.searchBox, skin);

        List<ClientboundOpenFoldAnchorEditorPayload.Candidate> filteredCandidates = this.filteredCandidates();
        this.listArea = new SPSGui.Rect(area.x() + 6, area.y() + 30, area.width() - 12, area.height() - 36);
        int contentHeight = Math.max(0, filteredCandidates.size() * 35 - 3);
        this.listScroll = Math.min(this.listScroll, Math.max(0, contentHeight - this.listArea.height()));
        int rowY = this.listArea.y() - (int) Math.round(this.listScroll);
        graphics.enableScissor(this.listArea.x(), this.listArea.y(), this.listArea.right(), this.listArea.bottom());
        if (filteredCandidates.isEmpty()) {
            String key = this.searchBox != null && !this.searchBox.getValue().isBlank()
                    ? "screen.superpipeslide.fold_anchor.no_search_results"
                    : "screen.superpipeslide.fold_anchor.no_candidates";
            this.drawEmptyScanResult(graphics, skin, this.listArea, key);
        }
        for (ClientboundOpenFoldAnchorEditorPayload.Candidate candidate : filteredCandidates) {
            SPSGui.Rect card = new SPSGui.Rect(this.listArea.x() + 1, rowY, this.listArea.width() - 2, 32);
            if (card.bottom() >= this.listArea.y() && card.y() <= this.listArea.bottom()) {
                this.renderCandidate(graphics, skin, card, candidate, mouseX, mouseY);
            }
            rowY += 35;
        }
        graphics.disableScissor();
        this.drawListFades(graphics, skin, this.listArea, this.listScroll > 0.5D, this.listScroll < Math.max(0, contentHeight - this.listArea.height()) - 0.5D);
    }

    private void drawEmptyScanResult(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, SPSGui.Rect rect, String key) {
        int cx = rect.x() + rect.width() / 2;
        int cy = rect.y() + Math.max(22, rect.height() / 2);
        this.drawFoldAnchorIcon(graphics, skin, cx, cy - 8, 20, true);
        this.drawCenteredSmallText(graphics, SPSGui.ellipsize(this.font, Component.translatable(key).getString(), Math.round((rect.width() - 14) / 0.66F)), cx, cy + 10, skin.textMuted(), 0.66F);
    }

    private void renderCandidate(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, SPSGui.Rect card, ClientboundOpenFoldAnchorEditorPayload.Candidate candidate, int mouseX, int mouseY) {
        boolean selected = this.selectedTarget.filter(candidate.ref()::equals).isPresent();
        boolean hovered = card.contains(mouseX, mouseY) && this.listArea.contains(mouseX, mouseY);
        boolean selectable = candidate.available() || selected;
        int fill = selected ? SPSGui.withAlpha(skin.accent(), 0x24) : hovered && selectable ? SPSGui.withAlpha(skin.accent(), 0x13) : 0xCC070D12;
        int border = selected ? skin.energy() : hovered && selectable ? SPSGui.withAlpha(skin.energy(), 0xAA) : SPSGui.withAlpha(skin.accent(), 0x3B);
        graphics.fill(card.x(), card.y(), card.right(), card.bottom(), fill);
        graphics.outline(card.x(), card.y(), card.width(), card.height(), border);
        if (selected) {
            graphics.fill(card.x(), card.y(), card.x() + 3, card.bottom(), skin.energy());
            this.drawSelectedPulse(graphics, skin, card);
        }
        this.drawEndpointGlyph(graphics, skin, card.x() + 15, card.y() + 16, 14, FoldAnchorMode.A_END);
        int textX = card.x() + 29;
        int badgeWidth = this.candidateBadgeWidth(candidate);
        int nameWidth = Math.max(20, card.width() - 36 - badgeWidth);
        int color = selectable ? skin.text() : skin.textDisabled();
        SPSGui.text(graphics, this.font, SPSGui.ellipsize(this.font, candidate.displayName(), nameWidth), textX, card.y() + 5, color);
        String detail = this.candidateLocation(candidate.ref());
        if (!candidate.available() && !candidate.disabledReason().isBlank()) {
            detail = this.disabledReason(candidate);
        }
        int detailColor = !candidate.available() && !selected ? skin.warning() : skin.textMuted();
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, detail, Math.round((card.width() - 38) / 0.58F)), textX, card.y() + 20, detailColor, 0.58F);
        this.drawCandidateBadge(graphics, skin, candidate, new SPSGui.Rect(card.right() - badgeWidth - 5, card.y() + 6, badgeWidth, 13), selected);
        SPSGui.Rect clickBounds = intersect(card, this.listArea);
        if (selectable && clickBounds.width() > 0 && clickBounds.height() > 0) {
            this.addClick(clickBounds, () -> this.toggleTarget(candidate.ref()), Component.literal((selected
                    ? Component.translatable("screen.superpipeslide.fold_anchor.unbind").getString()
                    : Component.translatable("screen.superpipeslide.fold_anchor.select_a").getString()) + "\n" + detail));
        }
    }

    private void drawSelectedPulse(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, SPSGui.Rect card) {
        int span = Math.max(1, card.width() - 12);
        int x = card.x() + 6 + (int) ((System.currentTimeMillis() / 33L) % span);
        graphics.fill(x, card.bottom() - 2, Math.min(card.right() - 6, x + 18), card.bottom() - 1, SPSGui.withAlpha(skin.energy(), 0x88));
    }

    private static SPSGui.Rect intersect(SPSGui.Rect a, SPSGui.Rect b) {
        int x1 = Math.max(a.x(), b.x());
        int y1 = Math.max(a.y(), b.y());
        int x2 = Math.min(a.right(), b.right());
        int y2 = Math.min(a.bottom(), b.bottom());
        return new SPSGui.Rect(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
    }

    private int candidateBadgeWidth(ClientboundOpenFoldAnchorEditorPayload.Candidate candidate) {
        String label = this.candidateBadgeLabel(candidate);
        return Math.min(56, Math.max(34, this.font.width(label) + 8));
    }

    private void drawCandidateBadge(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, ClientboundOpenFoldAnchorEditorPayload.Candidate candidate, SPSGui.Rect rect, boolean selected) {
        int color = selected ? skin.energy() : candidate.available() ? skin.success() : skin.warning();
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), SPSGui.withAlpha(color, 0x19));
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), SPSGui.withAlpha(color, 0x94));
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, this.candidateBadgeLabel(candidate), Math.round((rect.width() - 6) / 0.56F)), rect.x() + 4, rect.y() + 4, color, 0.56F);
    }

    private String candidateBadgeLabel(ClientboundOpenFoldAnchorEditorPayload.Candidate candidate) {
        if (this.selectedTarget.filter(candidate.ref()::equals).isPresent()) {
            return Component.translatable("screen.superpipeslide.fold_anchor.candidate.selected").getString();
        }
        if (candidate.available()) {
            return Component.translatable("screen.superpipeslide.fold_anchor.candidate.available").getString();
        }
        return Component.translatable("screen.superpipeslide.fold_anchor.candidate.unavailable").getString();
    }

    private String disabledReason(ClientboundOpenFoldAnchorEditorPayload.Candidate candidate) {
        if ("Already bound".equals(candidate.disabledReason())) {
            return Component.translatable("screen.superpipeslide.fold_anchor.candidate.already_bound").getString();
        }
        return candidate.disabledReason();
    }

    private void renderLinkPreview(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, SPSGui.Rect rect, boolean canSave, int mouseX, int mouseY) {
        this.drawPanelShell(graphics, rect, skin, rect.contains(mouseX, mouseY), true);
        int statusColor = this.statusColor(skin);
        String status = this.statusText();
        int saveW = Math.min(86, Math.max(68, this.font.width(Component.translatable("screen.superpipeslide.fold_anchor.save_calibration")) + 16));
        SPSGui.Rect save = new SPSGui.Rect(rect.right() - saveW - 7, rect.y() + 9, saveW, 22);
        SPSGui.Rect chain = new SPSGui.Rect(rect.x() + 8, rect.y() + 6, save.x() - rect.x() - 14, rect.height() - 12);
        this.drawLinkDiagram(graphics, skin, chain, statusColor);
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, status, Math.round((chain.width() - 12) / 0.55F)), chain.x() + 6, chain.bottom() - 10, statusColor, 0.55F);
        this.drawCommandButton(graphics, save, skin, Component.translatable("screen.superpipeslide.fold_anchor.save_calibration"), save.contains(mouseX, mouseY), !canSave, true);
        this.addClick(save, canSave ? this::save : () -> {}, canSave ? this.tooltip("screen.superpipeslide.fold_anchor.save_calibration", "screen.superpipeslide.fold_anchor.tooltip.save") : this.saveDisabledMessage());
    }

    private void drawLinkDiagram(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, SPSGui.Rect rect, int statusColor) {
        int centerY = rect.y() + 13;
        int leftX = rect.x() + 15;
        int rightX = rect.right() - 15;
        boolean hasTarget = this.selectedTarget.isPresent();
        SmoothGuiPrimitives.circle(graphics, new Vec2(leftX, centerY), 6.0D, SPSGui.withAlpha(skin.accent(), 0xAA));
        SmoothGuiPrimitives.circle(graphics, new Vec2(leftX, centerY), 3.0D, skin.energy());
        if (skin.kind() == FoldAnchorKind.DIMENSION) {
            int gateX = rect.x() + rect.width() / 2;
            this.drawDashedEnergyLine(graphics, leftX + 10, gateX - 10, centerY, skin, 0.0D, hasTarget ? 1.0D : 0.0D);
            this.drawDimensionGate(graphics, skin, gateX, centerY, hasTarget);
            this.drawDashedEnergyLine(graphics, gateX + 10, rightX - 10, centerY, skin, 0.5D, hasTarget ? 1.0D : 0.0D);
        } else {
            this.drawSpaceFoldLine(graphics, leftX + 10, rightX - 10, centerY, skin, hasTarget);
        }
        SmoothGuiPrimitives.circle(graphics, new Vec2(rightX, centerY), 6.0D, hasTarget ? SPSGui.withAlpha(skin.energy(), 0xC0) : SPSGui.withAlpha(skin.textMuted(), 0x66));
        SmoothGuiPrimitives.circle(graphics, new Vec2(rightX, centerY), 3.0D, hasTarget ? statusColor : skin.textMuted());
        if (hasTarget) {
            String label = this.selectedTarget.map(this::targetShortLabel).orElse("");
            SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, label, Math.round((rect.width() - 48) / 0.50F)), leftX + 13, rect.y() + 2, skin.textSecondary(), 0.50F);
        }
    }

    private void drawSpaceFoldLine(GuiGraphicsExtractor graphics, int x0, int x1, int y, FoldAnchorSkin skin, boolean active) {
        int color = active ? skin.energy() : SPSGui.withAlpha(skin.textMuted(), 0x72);
        int segment = 8;
        int gap = 5;
        int offset = active ? (int) ((System.currentTimeMillis() / 42L) % (segment + gap)) : 0;
        for (int x = x0 - offset; x < x1; x += segment + gap) {
            int start = Math.max(x0, x);
            int end = Math.min(x1, x + segment);
            if (end > start) {
                SmoothGuiPrimitives.line(graphics, new Vec2(start, y), new Vec2(end, y), 2.0D, color);
            }
        }
        if (active) {
            int sweep = x0 + (int) ((System.currentTimeMillis() / 24L) % Math.max(1, x1 - x0));
            SmoothGuiPrimitives.line(graphics, new Vec2(sweep - 7, y), new Vec2(Math.min(x1, sweep + 9), y), 3.0D, SPSGui.withAlpha(skin.energy(), 0xAA));
        }
    }

    private void drawDashedEnergyLine(GuiGraphicsExtractor graphics, int x0, int x1, int y, FoldAnchorSkin skin, double phase, double active) {
        int color = active > 0.5D ? skin.energy() : SPSGui.withAlpha(skin.textMuted(), 0x72);
        int period = 13;
        int offset = active > 0.5D ? (int) ((System.currentTimeMillis() / 50L + Math.round(phase * period)) % period) : 0;
        for (int x = x0 - offset; x < x1; x += period) {
            int start = Math.max(x0, x);
            int end = Math.min(x1, x + 7);
            if (end > start) {
                SmoothGuiPrimitives.line(graphics, new Vec2(start, y), new Vec2(end, y), 1.8D, color);
            }
        }
    }

    private void drawDimensionGate(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, int centerX, int centerY, boolean active) {
        double breath = active ? 0.5D + 0.5D * Math.sin(System.currentTimeMillis() / 310.0D) : 0.0D;
        int glow = SPSGui.withAlpha(skin.energy(), active ? (int) Math.round(70 + breath * 48) : 0x44);
        SmoothGuiPrimitives.diamond(graphics, new Vec2(centerX, centerY), 9.0D + breath, SPSGui.withAlpha(skin.dark(), 0xEE));
        SmoothGuiPrimitives.diamond(graphics, new Vec2(centerX, centerY), 6.0D + breath, glow);
        SmoothGuiPrimitives.line(graphics, new Vec2(centerX, centerY - 8), new Vec2(centerX, centerY + 8), 1.7D, active ? skin.energy() : skin.textMuted());
    }

    private String targetShortLabel(FoldAnchorRef ref) {
        if (this.anchor.kind() == FoldAnchorKind.SPACE) {
            return ref.anchorId().blockPos().toShortString();
        }
        return dimensionLabel(ref.levelKey());
    }

    private String statusText() {
        if (System.currentTimeMillis() < this.lastResultUntilMillis && !this.lastResultMessage.isBlank()) {
            return this.lastResultAccepted
                    ? Component.translatable("screen.superpipeslide.fold_anchor.saved").getString()
                    : this.lastResultMessage;
        }
        if (this.pendingSaveRequestId.isPresent()) {
            return Component.translatable("screen.superpipeslide.fold_anchor.save.saving").getString();
        }
        if (this.canSave()) {
            return Component.translatable("screen.superpipeslide.fold_anchor.save.ready").getString();
        }
        return this.saveDisabledMessage().getString();
    }

    private int statusColor(FoldAnchorSkin skin) {
        if (System.currentTimeMillis() < this.lastResultUntilMillis && !this.lastResultMessage.isBlank()) {
            return this.lastResultAccepted ? skin.success() : skin.warning();
        }
        if (this.pendingSaveRequestId.isPresent() || this.canSave()) {
            return skin.energy();
        }
        return this.sourceConnectionCount == 1 ? skin.textMuted() : skin.warning();
    }

    private void drawPanelShell(GuiGraphicsExtractor graphics, SPSGui.Rect rect, FoldAnchorSkin skin, boolean hovered, boolean strong) {
        int fill = strong ? SPSGui.withAlpha(skin.surface(), 0xF2) : SPSGui.withAlpha(skin.surface(), 0xE6);
        int border = hovered ? SPSGui.withAlpha(skin.energy(), 0xAA) : SPSGui.withAlpha(skin.accent(), strong ? 0x80 : 0x52);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), border);
        graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.y() + 2, SPSGui.withAlpha(skin.energy(), strong ? 0x22 : 0x14));
        graphics.fill(rect.x() + 1, rect.bottom() - 2, rect.right() - 1, rect.bottom() - 1, 0x55000000);
        this.drawPanelCircuitMarks(graphics, rect, skin, strong);
    }

    private void drawPanelCircuitMarks(GuiGraphicsExtractor graphics, SPSGui.Rect rect, FoldAnchorSkin skin, boolean strong) {
        int color = SPSGui.withAlpha(skin.accent(), strong ? 0x24 : 0x18);
        for (int x = rect.x() + 10; x < rect.right() - 6; x += 32) {
            graphics.fill(x, rect.y() + 4, x + 7, rect.y() + 5, color);
            graphics.fill(x + 6, rect.y() + 4, x + 7, rect.y() + 9, color);
        }
        if (skin.kind() == FoldAnchorKind.DIMENSION) {
            int cx = rect.right() - 18;
            graphics.fill(cx, rect.y() + 5, cx + 2, rect.y() + 11, SPSGui.withAlpha(skin.energy(), 0x22));
            graphics.fill(cx - 3, rect.y() + 8, cx + 5, rect.y() + 10, SPSGui.withAlpha(skin.energy(), 0x18));
        }
    }

    private void drawTypeMicroPattern(GuiGraphicsExtractor graphics, SPSGui.Rect rect, FoldAnchorSkin skin) {
        if (skin.kind() == FoldAnchorKind.SPACE) {
            for (int i = 0; i < 3; i++) {
                int inset = 5 + i * 5;
                graphics.outline(rect.x() + inset, rect.y() + inset, rect.width() - inset * 2, rect.height() - inset * 2, SPSGui.withAlpha(skin.accent(), 0x22));
            }
            return;
        }
        int cx = rect.x() + rect.width() / 2;
        for (int y = rect.y() + 5; y < rect.bottom() - 5; y += 5) {
            int shift = ((y - rect.y()) / 5) % 2 == 0 ? -2 : 2;
            graphics.fill(cx + shift, y, cx + shift + 2, y + 3, SPSGui.withAlpha(skin.energy(), 0x24));
        }
    }

    private void drawInputSlot(GuiGraphicsExtractor graphics, SPSGui.Rect rect, FoldAnchorSkin skin, boolean focused, boolean warning) {
        int border = warning ? skin.warning() : focused ? skin.energy() : SPSGui.withAlpha(skin.accent(), 0x78);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), 0xE805090D);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), border);
        graphics.fill(rect.x() + 2, rect.bottom() - 2, rect.right() - 2, rect.bottom() - 1, SPSGui.withAlpha(border, 0xAA));
    }

    private void drawSearchPlaceholderDark(GuiGraphicsExtractor graphics, EditBox box, FoldAnchorSkin skin) {
        if (box != null && box.getValue().isEmpty() && !box.isFocused()) {
            SPSGui.smallText(graphics, this.font, Component.translatable("screen.superpipeslide.search").getString(), box.getX() + 2, box.getY() + 3, skin.textMuted(), 0.66F);
        }
    }

    private void drawCommandButton(GuiGraphicsExtractor graphics, SPSGui.Rect rect, FoldAnchorSkin skin, Component text, boolean hovered, boolean disabled, boolean primary) {
        int fill = disabled ? 0xD0070B0E : primary ? hovered ? SPSGui.withAlpha(skin.accent(), 0x46) : SPSGui.withAlpha(skin.accent(), 0x30) : 0xDD0D1519;
        int border = disabled ? 0x77525B64 : primary ? hovered ? skin.energy() : skin.accent() : SPSGui.withAlpha(skin.accent(), 0x88);
        int textColor = disabled ? skin.textDisabled() : primary ? 0xFFFFFFFF : skin.text();
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), border);
        graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.y() + 2, disabled ? 0x18525B64 : SPSGui.withAlpha(skin.energy(), 0x32));
        if (!disabled && primary) {
            int pulse = rect.x() + 3 + (int) ((System.currentTimeMillis() / 38L) % Math.max(1, rect.width() - 14));
            graphics.fill(pulse, rect.bottom() - 3, Math.min(rect.right() - 4, pulse + 12), rect.bottom() - 2, SPSGui.withAlpha(skin.energy(), 0x88));
        }
        SPSGui.centeredText(graphics, this.font, SPSGui.ellipsize(this.font, text.getString(), rect.width() - 8), rect.x() + rect.width() / 2, rect.y() + 7, textColor);
    }

    private void drawBadge(GuiGraphicsExtractor graphics, SPSGui.Rect rect, FoldAnchorSkin skin, String label, int color) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), SPSGui.withAlpha(color, 0x1D));
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), SPSGui.withAlpha(color, 0xA0));
        graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.y() + 2, SPSGui.withAlpha(color, 0x28));
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, label, Math.round((rect.width() - 8) / 0.60F)), rect.x() + 5, rect.y() + 5, color, 0.60F);
    }

    private void drawTinyBadge(GuiGraphicsExtractor graphics, SPSGui.Rect rect, FoldAnchorSkin skin, String label, int color) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), SPSGui.withAlpha(color, 0x12));
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), SPSGui.withAlpha(color, 0x76));
        SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, label, Math.round((rect.width() - 8) / 0.52F)), rect.x() + 5, rect.y() + 4, color, 0.52F);
    }

    private void drawListFades(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, SPSGui.Rect rect, boolean hasTop, boolean hasBottom) {
        if (hasTop) {
            graphics.fillGradient(rect.x(), rect.y(), rect.right(), rect.y() + 8, 0xE0060A0D, 0x00060A0D);
        }
        if (hasBottom) {
            graphics.fillGradient(rect.x(), rect.bottom() - 8, rect.right(), rect.bottom(), 0x00060A0D, 0xE0060A0D);
        }
    }

    private void drawBracketCorners(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color, int length) {
        graphics.fill(x, y, x + length, y + 1, color);
        graphics.fill(x, y, x + 1, y + length, color);
        graphics.fill(x + width - length, y, x + width, y + 1, color);
        graphics.fill(x + width - 1, y, x + width, y + length, color);
        graphics.fill(x, y + height - 1, x + length, y + height, color);
        graphics.fill(x, y + height - length, x + 1, y + height, color);
        graphics.fill(x + width - length, y + height - 1, x + width, y + height, color);
        graphics.fill(x + width - 1, y + height - length, x + width, y + height, color);
    }

    private void drawCenteredSmallText(GuiGraphicsExtractor graphics, String text, int centerX, int y, int color, float scale) {
        int width = SPSGui.scaledWidth(this.font, text, scale);
        SPSGui.smallText(graphics, this.font, text, centerX - width / 2, y, color, scale);
    }

    private void drawFoldAnchorIcon(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, int centerX, int centerY, int size, boolean disabled) {
        int accent = disabled ? skin.textDisabled() : skin.accent();
        int energy = disabled ? skin.textMuted() : skin.energy();
        int dark = disabled ? 0xFF283039 : skin.dark();
        Vec2 center = new Vec2(centerX, centerY);
        if (skin.kind() == FoldAnchorKind.DIMENSION) {
            double w = size * 0.52D;
            double h = size * 0.80D;
            SmoothGuiPrimitives.diamond(graphics, center, size * 0.52D, SPSGui.withAlpha(dark, 0xEE));
            SmoothGuiPrimitives.capsule(graphics, center, w + 5.0D, h + 5.0D, SPSGui.withAlpha(accent, 0x72));
            SmoothGuiPrimitives.capsule(graphics, center, Math.max(3.0D, w - 2.0D), Math.max(6.0D, h - 4.0D), SPSGui.withAlpha(energy, 0xC8));
            SmoothGuiPrimitives.line(graphics, new Vec2(centerX, centerY - h * 0.38D), new Vec2(centerX, centerY + h * 0.38D), Math.max(1.0D, size * 0.08D), 0xFFFFFFFF);
            return;
        }
        SmoothGuiPrimitives.diamond(graphics, center, size * 0.48D, SPSGui.withAlpha(dark, 0xEE));
        SmoothGuiPrimitives.diamond(graphics, center, size * 0.38D, SPSGui.withAlpha(accent, 0xD8));
        SmoothGuiPrimitives.diamond(graphics, center, size * 0.25D, 0xFFE9FFF9);
        SmoothGuiPrimitives.line(graphics, new Vec2(centerX - size * 0.30D, centerY), new Vec2(centerX + size * 0.30D, centerY), Math.max(1.0D, size * 0.06D), energy);
        SmoothGuiPrimitives.line(graphics, new Vec2(centerX, centerY - size * 0.30D), new Vec2(centerX, centerY + size * 0.30D), Math.max(1.0D, size * 0.06D), energy);
    }

    private void drawEndpointGlyph(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, int centerX, int centerY, int size, FoldAnchorMode mode) {
        this.drawFoldAnchorIcon(graphics, skin, centerX, centerY, size, false);
        int color = mode == FoldAnchorMode.A_END ? skin.energy() : skin.accent();
        if (mode == FoldAnchorMode.A_END) {
            SmoothGuiPrimitives.circle(graphics, new Vec2(centerX, centerY), Math.max(2.0D, size * 0.13D), color);
        } else {
            SmoothGuiPrimitives.circle(graphics, new Vec2(centerX, centerY), Math.max(2.4D, size * 0.15D), color);
            SmoothGuiPrimitives.circle(graphics, new Vec2(centerX, centerY), Math.max(1.1D, size * 0.07D), 0xFFFFFFFF);
        }
    }

    @Override
    protected void renderTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (ClickAction clickAction : this.clickActions) {
            if (clickAction.bounds().contains(mouseX, mouseY)) {
                this.renderCalibrationTooltip(graphics, this.skin(), clickAction.tooltip(), mouseX, mouseY);
                return;
            }
        }
    }

    private void renderCalibrationTooltip(GuiGraphicsExtractor graphics, FoldAnchorSkin skin, Component tooltip, int mouseX, int mouseY) {
        List<String> lines = Arrays.stream(tooltip.getString().split("\\R"))
                .filter(line -> !line.isBlank())
                .toList();
        if (lines.isEmpty()) {
            return;
        }
        int width = 112;
        for (String line : lines) {
            width = Math.max(width, this.font.width(line) + 26);
        }
        width = Math.min(width, Math.max(112, this.panel.width() - 12));
        width = Math.min(width, 270);
        int height = 14 + lines.size() * 11;
        SPSGui.Rect bounds = placeTooltip(new SPSGui.Rect(mouseX + 11, mouseY + 11, width, height), new SPSGui.Rect(0, 0, this.width, this.height), 6);
        graphics.fill(bounds.x() + 2, bounds.y() + 3, bounds.right() + 2, bounds.bottom() + 3, 0x99000000);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0xF7070C11);
        graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), SPSGui.withAlpha(skin.accent(), 0xCC));
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + 3, bounds.bottom(), skin.energy());
        this.drawTypeMicroPattern(graphics, new SPSGui.Rect(bounds.x() + 4, bounds.y() + 2, 20, bounds.height() - 4), skin);
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? skin.text() : skin.textSecondary();
            float scale = i == 0 ? 1.0F : 0.62F;
            if (i == 0) {
                SPSGui.text(graphics, this.font, SPSGui.ellipsize(this.font, lines.get(i), bounds.width() - 16), bounds.x() + 9, bounds.y() + 6, color);
            } else {
                SPSGui.smallText(graphics, this.font, SPSGui.ellipsize(this.font, lines.get(i), Math.round((bounds.width() - 16) / scale)), bounds.x() + 9, bounds.y() + 9 + i * 11, color, scale);
            }
        }
    }

    private static SPSGui.Rect placeTooltip(SPSGui.Rect preferred, SPSGui.Rect boundary, int padding) {
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

    private Component tooltip(String titleKey, String bodyKey) {
        return Component.literal(Component.translatable(titleKey).getString() + "\n" + Component.translatable(bodyKey).getString());
    }

    private void layoutSearchBox(SPSGui.Rect area) {
        if (this.searchBox == null) {
            return;
        }
        int width = Math.min(96, Math.max(70, area.width() / 3));
        this.searchBox.setX(area.right() - width - 10);
        this.searchBox.setY(area.y() + 9);
        this.searchBox.setWidth(width);
    }

    private List<ClientboundOpenFoldAnchorEditorPayload.Candidate> filteredCandidates() {
        if (this.searchBox == null) {
            return this.candidates;
        }
        String query = this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return this.candidates;
        }
        return this.candidates.stream()
                .filter(candidate -> candidate.displayName().toLowerCase(Locale.ROOT).contains(query)
                        || this.candidateLocation(candidate.ref()).toLowerCase(Locale.ROOT).contains(query)
                        || candidate.ref().levelKey().toString().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private void switchMode(FoldAnchorMode mode) {
        if (this.switchCooldownTicks > 0 || this.draftMode == mode) {
            return;
        }
        this.draftMode = mode;
        this.switchAnimationTicks = SWITCH_ANIMATION_TICKS;
        this.switchCooldownTicks = SWITCH_ANIMATION_TICKS;
        if (mode == FoldAnchorMode.A_END) {
            this.selectedTarget = Optional.empty();
            if (this.nameBox != null && this.nameBox.getValue().isBlank()) {
                this.nameBox.setValue(this.anchor.displayName());
            }
        }
    }

    private void toggleTarget(FoldAnchorRef ref) {
        this.selectedTarget = this.selectedTarget.filter(ref::equals).isPresent() ? Optional.empty() : Optional.of(ref);
    }

    private boolean canSave() {
        if (this.pendingSaveRequestId.isPresent() || !this.isDirty()) {
            return false;
        }
        if (this.draftMode == FoldAnchorMode.A_END) {
            return this.nameBox != null && !this.nameBox.getValue().isBlank();
        }
        if (this.draftMode == FoldAnchorMode.B_END && this.selectedTarget.isEmpty()) {
            return this.anchor.boundTarget().isPresent();
        }
        return this.draftMode == FoldAnchorMode.B_END
                && this.selectedTarget.isPresent()
                && this.candidates.stream().anyMatch(candidate -> candidate.available() && this.selectedTarget.filter(candidate.ref()::equals).isPresent());
    }

    private Component saveDisabledMessage() {
        if (this.pendingSaveRequestId.isPresent()) {
            return Component.translatable("screen.superpipeslide.fold_anchor.save.saving");
        }
        if (this.draftMode == FoldAnchorMode.UNCONFIGURED) {
            return Component.translatable("screen.superpipeslide.fold_anchor.save.choose_role");
        }
        if (!this.isDirty()) {
            return Component.translatable("screen.superpipeslide.fold_anchor.save.no_changes");
        }
        if (this.draftMode == FoldAnchorMode.A_END && (this.nameBox == null || this.nameBox.getValue().isBlank())) {
            return Component.translatable("screen.superpipeslide.fold_anchor.save.name_required");
        }
        if (this.draftMode == FoldAnchorMode.B_END && this.selectedTarget.isEmpty()) {
            return Component.translatable("screen.superpipeslide.fold_anchor.save.target_required");
        }
        if (this.draftMode == FoldAnchorMode.B_END && this.selectedTarget.isPresent()) {
            return Component.translatable("screen.superpipeslide.fold_anchor.save.target_unavailable");
        }
        return Component.translatable("screen.superpipeslide.fold_anchor.save.no_changes");
    }

    private void save() {
        UUID requestId = UUID.randomUUID();
        this.pendingSaveRequestId = Optional.of(requestId);
        ClientPacketDistributor.sendToServer(new ServerboundFoldAnchorSavePayload(requestId, this.anchor.anchorId(), this.anchor.configRevision(), this.draftMode, this.nameBox == null ? "" : this.nameBox.getValue(), this.selectedTarget));
    }

    private boolean isDirty() {
        if (this.draftMode != this.anchor.mode()) {
            return true;
        }
        if (this.draftMode == FoldAnchorMode.A_END) {
            String draftName = this.nameBox == null ? "" : this.nameBox.getValue().trim();
            return !draftName.equals(this.anchor.displayName());
        }
        if (this.draftMode == FoldAnchorMode.B_END) {
            return !this.selectedTarget.equals(this.anchor.boundTarget());
        }
        return false;
    }

    private String candidateLocation(FoldAnchorRef ref) {
        String pos = ref.anchorId().blockPos().toShortString();
        if (this.anchor.kind() == FoldAnchorKind.SPACE) {
            return pos;
        }
        return dimensionLabel(ref.levelKey()) + " / " + pos;
    }

    private static String dimensionLabel(ResourceKey<Level> dimension) {
        String id = dimension.identifier().toString();
        String path = dimension.identifier().getPath();
        return switch (id) {
            case "minecraft:overworld" -> Component.translatable("screen.superpipeslide.full_map.dimension.overworld").getString();
            case "minecraft:the_nether" -> Component.translatable("screen.superpipeslide.full_map.dimension.nether").getString();
            case "minecraft:the_end" -> Component.translatable("screen.superpipeslide.full_map.dimension.end").getString();
            default -> path;
        };
    }

    private Component kindName() {
        return Component.translatable(this.anchor.kind() == FoldAnchorKind.SPACE
                ? "screen.superpipeslide.fold_anchor.kind.space"
                : "screen.superpipeslide.fold_anchor.kind.dimension");
    }

    private Component modeName(FoldAnchorMode mode) {
        return Component.translatable(switch (mode) {
            case UNCONFIGURED -> "screen.superpipeslide.fold_anchor.mode.unconfigured";
            case A_END -> "screen.superpipeslide.fold_anchor.mode.a";
            case B_END -> "screen.superpipeslide.fold_anchor.mode.b";
        });
    }

    private FoldAnchorSkin skin() {
        if (this.anchor.kind() == FoldAnchorKind.DIMENSION) {
            return new FoldAnchorSkin(
                    FoldAnchorKind.DIMENSION,
                    0xFF8A5CFF,
                    0xFFFF35E8,
                    0xFF120D20,
                    0xFF1A1330,
                    0xF7080610,
                    0xFFEDE7FF,
                    0xFFBDAEFF,
                    0xFF74678E,
                    0xFF59606A,
                    0xFFFFB84A,
                    0xFF58F0A0,
                    "screen.superpipeslide.fold_anchor.subtitle.dimension");
        }
        return new FoldAnchorSkin(
                FoldAnchorKind.SPACE,
                0xFF23C8A9,
                0xFF58F0CC,
                0xFF0F2A28,
                0xFF102F2C,
                0xF706100D,
                0xFFE9FFF9,
                0xFFA6ECDC,
                0xFF6E948D,
                0xFF59606A,
                0xFFFFB84A,
                0xFF58F0A0,
                "screen.superpipeslide.fold_anchor.subtitle.space");
    }

    private static double easeOutCubic(double value) {
        double t = Math.max(0.0D, Math.min(1.0D, value));
        double inverse = 1.0D - t;
        return 1.0D - inverse * inverse * inverse;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.listArea.contains(mouseX, mouseY)) {
            this.listScroll = Math.max(0.0D, this.listScroll - scrollY * 18.0D);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private record FoldAnchorSkin(
            FoldAnchorKind kind,
            int accent,
            int energy,
            int dark,
            int surface,
            int screen,
            int text,
            int textSecondary,
            int textMuted,
            int textDisabled,
            int warning,
            int success,
            String subtitleKey) {}
}
