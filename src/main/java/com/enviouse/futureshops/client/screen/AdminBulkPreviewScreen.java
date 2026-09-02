package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.catalog.AdminBulkListingPlanner;
import com.enviouse.futureshops.catalog.AdminBulkListingService;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SAdminBulkCommitPacket;
import com.enviouse.futureshops.network.packets.S2CAdminBulkResultPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Confirmation screen for an administrator bulk catalog preview. */
public final class AdminBulkPreviewScreen extends Screen implements ShopScreenMarker {
    private static final int ROW_HEIGHT = 54;

    private final Screen parent;
    private S2CAdminBulkResultPacket result;
    private final Set<String> replacements = new LinkedHashSet<>();
    private final List<ShopUiUtil.ClickZone> clickZones = new ArrayList<>();
    private Button cancelButton;
    private Button commitButton;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listY;
    private int listH;
    private int scroll;
    private boolean processing;

    public AdminBulkPreviewScreen(Screen parent, S2CAdminBulkResultPacket result) {
        super(Component.translatable("gui.futureshops.admin_edit.bulk.title"));
        this.parent = parent;
        this.result = result;
    }

    @Override
    protected void init() {
        panelX = Math.max(8, this.width / 16);
        panelY = Math.max(8, this.height / 16);
        panelW = this.width - panelX * 2;
        panelH = this.height - panelY * 2;
        listY = panelY + 50;
        listH = Math.max(ROW_HEIGHT, panelH - 98);
        int buttonY = panelY + panelH - 24;
        cancelButton = FutureShopsButton.styled(
                        Component.translatable("gui.futureshops.modal.cancel"),
                        ignored -> onClose())
                .bounds(panelX + 12, buttonY, 88, 16)
                .style(ShopUiUtil.ButtonStyle.SECONDARY)
                .build();
        commitButton = FutureShopsButton.styled(
                        commitLabel(), ignored -> commit())
                .bounds(panelX + panelW - 152, buttonY, 140, 16)
                .style(ShopUiUtil.ButtonStyle.PRIMARY)
                .build();
        addRenderableWidget(cancelButton);
        addRenderableWidget(commitButton);
        clampScroll();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();
        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, panelX, panelY, panelW, panelH,
                ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, ShopColors.ACCENT_CURRENCY);
        graphics.drawString(this.font, this.title, panelX + 12, panelY + 10,
                ShopColors.TEXT_STRONG, false);
        Component summary = Component.translatable(
                "gui.futureshops.admin_edit.bulk.summary",
                ShopUiUtil.formatMinorUnits(result.priceMinor()),
                result.stock() < 0 ? Component.translatable("gui.futureshops.admin_edit.bulk.unlimited")
                        : Integer.toString(result.stock()),
                result.rows().size());
        graphics.drawString(this.font, summary, panelX + 12, panelY + 27,
                ShopColors.TEXT_MUTED, false);
        renderRows(graphics, mouseX, mouseY);
        if (!processing && result.status() != AdminBulkListingService.Status.PREVIEW_READY) {
            graphics.drawString(this.font, Component.literal(result.message()), panelX + 12,
                    panelY + panelH - 42, result.status() == AdminBulkListingService.Status.COMMITTED
                            || result.status() == AdminBulkListingService.Status.NO_CHANGES
                            || result.status() == AdminBulkListingService.Status.REPLAY
                            ? ShopColors.STATUS_SUCCESS : ShopColors.STATUS_DANGER, false);
        }
        commitButton.setMessage(commitLabel());
        commitButton.active = !processing && result.status()
                == AdminBulkListingService.Status.PREVIEW_READY;
        cancelButton.active = !processing;
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        List<S2CAdminBulkResultPacket.Row> rows = result.rows();
        if (rows.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.futureshops.admin_edit.bulk.empty"),
                    panelX + panelW / 2, listY + listH / 2, ShopColors.TEXT_MUTED);
            return;
        }
        int visible = Math.max(1, listH / ROW_HEIGHT);
        int first = Math.min(scroll, Math.max(0, rows.size() - visible));
        graphics.enableScissor(panelX + 8, listY, panelX + panelW - 8, listY + listH);
        for (int index = first; index < rows.size() && index < first + visible; index++) {
            S2CAdminBulkResultPacket.Row row = rows.get(index);
            int y = listY + (index - first) * ROW_HEIGHT;
            boolean hovered = mouseX >= panelX + 10 && mouseX < panelX + panelW - 10
                    && mouseY >= y + 2 && mouseY < y + ROW_HEIGHT - 2;
            int color = switch (row.action()) {
                case CREATE -> ShopColors.STATUS_SUCCESS;
                case REPLACE -> ShopColors.ACCENT_PRIMARY;
                case BLOCKING -> ShopColors.STATUS_DANGER;
                case SKIP -> ShopColors.TEXT_MUTED;
            };
            graphics.fill(panelX + 10, y + 2, panelX + panelW - 10,
                    y + ROW_HEIGHT - 2, hovered ? ShopColors.SURFACE_OVERLAY : ShopColors.SURFACE_RAISED);
            ShopUiUtil.drawSoftOutline(graphics, panelX + 10, y + 2, panelW - 20,
                    ROW_HEIGHT - 4, ShopColors.BORDER_MUTED, ShopColors.BORDER_SUBTLE);
            ShopUiUtil.renderItemIcon(graphics, this.font, row.itemId(), panelX + 18, y + 7);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(row.displayName(),
                    Math.max(40, panelW / 3)), panelX + 40, y + 5, color, false);
            graphics.drawString(this.font, actionLabel(row.action()), panelX + 40, y + 17,
                    color, false);
            String identity = row.itemId() + "  •  "
                    + (row.listingId().isBlank()
                    ? Component.translatable("gui.futureshops.admin_edit.bulk.new_listing").getString()
                    : row.listingId());
            graphics.drawString(this.font, this.font.plainSubstrByWidth(identity,
                    Math.max(80, panelW / 2)), panelX + 40, y + 29,
                    ShopColors.TEXT_MUTED, false);
            String nbt = row.canonicalNbt().isBlank()
                    ? Component.translatable("gui.futureshops.admin_edit.bulk.registry_identity").getString()
                    : Component.translatable("gui.futureshops.admin_edit.bulk.exact_nbt",
                    row.canonicalNbt()).getString();
            graphics.drawString(this.font, this.font.plainSubstrByWidth(nbt,
                    Math.max(80, panelW / 2)), panelX + 40, y + 41,
                    ShopColors.TEXT_FAINT, false);
            int right = panelX + panelW - 20;
            if (row.replaceEligible()) {
                String id = row.listingId();
                boolean selected = replacements.contains(id.toLowerCase(java.util.Locale.ROOT));
                int buttonX = right - 70;
                ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                        buttonX, y + 20, 66, 14,
                        Component.translatable(selected
                                ? "gui.futureshops.admin_edit.bulk.replace"
                                : "gui.futureshops.admin_edit.bulk.skip"),
                        selected ? ShopUiUtil.ButtonStyle.PRIMARY : ShopUiUtil.ButtonStyle.SECONDARY,
                        !processing, () -> toggleReplacement(id));
                right = buttonX - 6;
            }
            String reason = this.font.plainSubstrByWidth(row.reason(), Math.max(36, right - panelX - 46));
            int reasonX = Math.max(panelX + panelW / 2, right - this.font.width(reason));
            graphics.drawString(this.font, reason, reasonX, y + 6, ShopColors.TEXT_MUTED, false);
        }
        graphics.disableScissor();
        ShopUiUtil.renderScrollIndicators(graphics, this.font, panelX + 8, listY,
                panelW - 16, listH, first, visible, rows.size());
    }

    private void toggleReplacement(String listingId) {
        String normalized = listingId == null ? "" : listingId.trim().toLowerCase(java.util.Locale.ROOT);
        if (!replacements.remove(normalized)) {
            replacements.add(normalized);
        }
    }

    private Component commitLabel() {
        if (processing) {
            return Component.translatable("gui.futureshops.admin_edit.bulk.committing");
        }
        return Component.translatable("gui.futureshops.admin_edit.bulk.commit");
    }

    private Component actionLabel(AdminBulkListingPlanner.Action action) {
        return Component.translatable("gui.futureshops.admin_edit.bulk.action."
                + action.name().toLowerCase(java.util.Locale.ROOT));
    }

    private void commit() {
        if (processing || result.status() != AdminBulkListingService.Status.PREVIEW_READY) {
            return;
        }
        processing = true;
        ShopPackets.CHANNEL.sendToServer(new C2SAdminBulkCommitPacket(
                result.requestId(), result.previewFingerprint(), result.catalogFingerprint(),
                result.registryFingerprint(), replacements));
    }

    public void applyCommitResult(S2CAdminBulkResultPacket packet) {
        if (!packet.requestId().equals(result.requestId()) || packet.preview()) {
            return;
        }
        result = packet;
        processing = false;
        if (packet.status() == AdminBulkListingService.Status.COMMITTED
                || packet.status() == AdminBulkListingService.Status.NO_CHANGES
                || packet.status() == AdminBulkListingService.Status.REPLAY) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= panelX + 8 && mouseX < panelX + panelW - 8
                && mouseY >= listY && mouseY < listY + listH) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int maxScroll() {
        return Math.max(0, result.rows().size() - Math.max(1, listH / ROW_HEIGHT));
    }

    private void clampScroll() {
        scroll = Math.min(scroll, maxScroll());
    }

    @Override
    public void onClose() {
        if (!processing && this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
