package com.enviouse.futureshopsp.client.screen;

import com.enviouse.futureshopsp.client.ShopColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Spec §8: ConfirmationModal — an overlay widget rendered on top of any screen.
 * Dimming layer + centered modal with item summary, total, and Cancel/Confirm buttons.
 * Not a separate Screen — it's composed into the parent screen's render/input cycle.
 */
public class ConfirmationModal {
    public enum State { WAITING, PROCESSING, SUCCESS, FAILED }

    private final String title;
    private final List<SummaryLine> summaryLines;
    private final String totalText;
    private final Consumer<ConfirmationModal> onConfirm;
    private final Runnable onCancel;

    private State state = State.WAITING;
    private String resultMessage = "";
    private long resultTimestamp = 0;
    private long processingStartedAt = 0;
    /** How long to wait for a server response before auto-failing so the player can escape. */
    private static final long PROCESSING_TIMEOUT_MS = 10_000L;

    // Modal dimensions (computed in render)
    private int modalX, modalY, modalW, modalH;

    public ConfirmationModal(String title, List<SummaryLine> summaryLines, String totalText,
                             Consumer<ConfirmationModal> onConfirm, Runnable onCancel) {
        this.title = title;
        this.summaryLines = List.copyOf(summaryLines);
        this.totalText = totalText;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    public State getState() {
        return state;
    }

    public void setProcessing() {
        this.state = State.PROCESSING;
        this.processingStartedAt = System.currentTimeMillis();
    }

    public void setSuccess(String message) {
        this.state = State.SUCCESS;
        this.resultMessage = message;
        this.resultTimestamp = System.currentTimeMillis();
    }

    public void setFailed(String message) {
        this.state = State.FAILED;
        this.resultMessage = message;
    }

    /**
     * Returns true if the modal should auto-dismiss (success shown for 1.5s).
     */
    public boolean shouldAutoDismiss() {
        return state == State.SUCCESS && System.currentTimeMillis() - resultTimestamp > 1500;
    }

    /**
     * Render the modal overlay. Call this AFTER super.render() in the parent screen.
     */
    public void render(GuiGraphics graphics, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        // Safety: if the server never responded, unblock the UI so the player can escape.
        if (state == State.PROCESSING && processingStartedAt > 0
                && System.currentTimeMillis() - processingStartedAt > PROCESSING_TIMEOUT_MS) {
            setFailed(I18n.get("gui.futureshops.modal.request_timed_out"));
        }

        // Push the whole modal forward on the Z axis so it sits on top of *everything*
        // the parent screen drew — including item stacks rendered via ItemRenderer, which
        // otherwise blit at ~+150 and punch through any `fill()` we draw afterwards.
        // Vanilla tooltips use +400, so +500 guarantees the modal sits above them too.
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, 500f);
        try {
            renderInner(graphics, font, screenW, screenH, mouseX, mouseY);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void renderInner(GuiGraphics graphics, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        // Dim layer — single pass is enough now that we're drawing above the item-stack
        // Z plane. Previously the layer appeared "weak" because bright item icons / glyphs
        // rendered at a higher Z were punching through.
        ShopUiUtil.renderDimBackdrop(graphics, screenW, screenH);

        // Modal panel — responsive to screen size, never exceeds viewport
        modalW = Math.min(260, Math.max(180, screenW - 20));
        modalH = Math.min(screenH - 20, 60 + summaryLines.size() * 16 + 40);
        modalX = (screenW - modalW) / 2;
        modalY = (screenH - modalH) / 2;

        graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, modalX, modalY, modalW, modalH, ShopColors.BORDER_GLOW, ShopColors.BORDER_SUBTLE);
        graphics.fill(modalX, modalY, modalX + modalW, modalY + 2, ShopColors.ACCENT_PRIMARY);

        if (state == State.SUCCESS) {
            // Success feedback
            String msg = "✓ " + resultMessage;
            int tw = font.width(msg);
            graphics.drawString(font, msg, modalX + (modalW - tw) / 2, modalY + modalH / 2 - 4, ShopColors.STATUS_SUCCESS, true);
            return;
        }
        if (state == State.FAILED) {
            // Failure feedback
            String msg = "✗ " + resultMessage;
            int tw = font.width(msg);
            graphics.drawString(font, msg, modalX + (modalW - tw) / 2, modalY + modalH / 2 - 12, ShopColors.STATUS_DANGER, true);
            // OK button
            int btnX = modalX + (modalW - 50) / 2;
            int btnY = modalY + modalH / 2 + 6;
            boolean hovered = mouseX >= btnX && mouseX <= btnX + 50 && mouseY >= btnY && mouseY <= btnY + 16;
            graphics.fill(btnX, btnY, btnX + 50, btnY + 16, hovered ? ShopColors.BTN_HOVER : ShopColors.BTN_REST);
            ShopUiUtil.drawBorder(graphics, btnX, btnY, 50, 16, ShopColors.BORDER_MUTED);
            graphics.drawString(font, I18n.get("gui.futureshops.modal.ok"), btnX + 20, btnY + 4, ShopColors.TEXT_STRONG, true);
            return;
        }

        // Title
        int tw = font.width(title);
        graphics.drawString(font, title, modalX + (modalW - tw) / 2, modalY + 8, ShopColors.TEXT_STRONG, true);

        // Summary lines — track icon hover so we can render a vanilla tooltip at the end.
        int lineY = modalY + 26;
        String hoverItemId = null;
        String hoverNbtJson = null;
        for (SummaryLine line : summaryLines) {
            if (line.itemId != null && !line.itemId.isBlank()) {
                int iconX = modalX + 10;
                int iconY = lineY - 2;
                if (line.nbtJson != null && !line.nbtJson.isBlank()) {
                    ShopUiUtil.renderItemIconWithNbt(graphics, font, line.itemId, line.nbtJson, iconX, iconY);
                } else {
                    ShopUiUtil.renderItemIcon(graphics, font, line.itemId, iconX, iconY);
                }
                graphics.drawString(font, font.plainSubstrByWidth(line.text, modalW - 42), modalX + 30, lineY + 2, ShopColors.TEXT_STRONG, false);
                if (mouseX >= iconX && mouseX < iconX + 16 && mouseY >= iconY && mouseY < iconY + 16) {
                    hoverItemId = line.itemId;
                    hoverNbtJson = line.nbtJson != null ? line.nbtJson : "";
                }
            } else {
                graphics.drawString(font, font.plainSubstrByWidth(line.text, modalW - 20), modalX + 10, lineY + 2, ShopColors.TEXT_MUTED, false);
            }
            lineY += 16;
        }

        // Divider
        graphics.fill(modalX + 8, lineY, modalX + modalW - 8, lineY + 1, ShopColors.BORDER_MUTED);
        lineY += 6;

        // Total
        graphics.drawString(font, totalText, modalX + 10, lineY, ShopColors.TEXT_CURRENCY, true);
        lineY += 16;

        // Buttons
        int btnW = 70;
        int btnH = 16;
        int gap = 12;
        int totalBtnW = btnW * 2 + gap;
        int startX = modalX + (modalW - totalBtnW) / 2;

        // Cancel
        boolean cancelHover = mouseX >= startX && mouseX <= startX + btnW && mouseY >= lineY && mouseY <= lineY + btnH;
        graphics.fill(startX, lineY, startX + btnW, lineY + btnH, cancelHover ? ShopColors.BTN_HOVER : ShopColors.BTN_REST);
        ShopUiUtil.drawBorder(graphics, startX, lineY, btnW, btnH, ShopColors.BORDER_MUTED);
        String cancelText = I18n.get("gui.futureshops.modal.cancel");
        graphics.drawString(font, cancelText, startX + (btnW - font.width(cancelText)) / 2, lineY + 4, ShopColors.TEXT_MUTED, true);

        // Confirm
        int confirmX = startX + btnW + gap;
        if (state == State.PROCESSING) {
            graphics.fill(confirmX, lineY, confirmX + btnW, lineY + btnH, ShopColors.BTN_REST);
            ShopUiUtil.drawBorder(graphics, confirmX, lineY, btnW, btnH, ShopColors.BORDER_MUTED);
            String proc = I18n.get("gui.futureshops.modal.processing");
            graphics.drawString(font, proc, confirmX + (btnW - font.width(proc)) / 2, lineY + 4, ShopColors.TEXT_MUTED, false);
        } else {
            boolean confirmHover = mouseX >= confirmX && mouseX <= confirmX + btnW && mouseY >= lineY && mouseY <= lineY + btnH;
            graphics.fill(confirmX, lineY, confirmX + btnW, lineY + btnH, confirmHover ? ShopColors.BTN_CTA_HOVER : ShopColors.BTN_CTA_REST);
            ShopUiUtil.drawBorder(graphics, confirmX, lineY, btnW, btnH, ShopColors.BORDER_GLOW);
            String confirmText = I18n.get("gui.futureshops.modal.confirm");
            graphics.drawString(font, confirmText, confirmX + (btnW - font.width(confirmText)) / 2, lineY + 4, ShopColors.TEXT_STRONG, true);
        }

        // Vanilla-style tooltip on hovered item icon so buyers can inspect enchantments,
        // durability, and other NBT before confirming. Drawn last so it sits on top.
        if (hoverItemId != null) {
            ShopUiUtil.renderItemTooltip(graphics, font, hoverItemId, hoverNbtJson, mouseX, mouseY);
        }
    }

    /**
     * Handle mouse clicks. Returns true if the modal consumed the click.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button, Font font) {
        if (state == State.FAILED) {
            // OK button
            int btnX = modalX + (modalW - 50) / 2;
            int btnY = modalY + modalH / 2 + 6;
            if (mouseX >= btnX && mouseX <= btnX + 50 && mouseY >= btnY && mouseY <= btnY + 16) {
                onCancel.run();
                return true;
            }
            return true; // consume click anyway
        }
        if (state == State.SUCCESS) {
            return true; // success auto-dismisses; consume clicks meanwhile
        }

        // Check if click is outside modal → cancel (works in WAITING and PROCESSING)
        if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
            onCancel.run();
            return true;
        }

        // Button row Y
        int lineY = modalY + 26 + summaryLines.size() * 16 + 7 + 16;
        int btnW = 70;
        int btnH = 16;
        int gap = 12;
        int totalBtnW = btnW * 2 + gap;
        int startX = modalX + (modalW - totalBtnW) / 2;

        // Cancel button — always active (also escape hatch from PROCESSING)
        if (mouseX >= startX && mouseX <= startX + btnW && mouseY >= lineY && mouseY <= lineY + btnH) {
            onCancel.run();
            return true;
        }

        // Confirm button — only in WAITING state
        if (state == State.WAITING) {
            int confirmX = startX + btnW + gap;
            if (mouseX >= confirmX && mouseX <= confirmX + btnW && mouseY >= lineY && mouseY <= lineY + btnH) {
                onConfirm.accept(this);
                return true;
            }
        }

        return true; // consume click inside modal
    }

    /**
     * Handle Escape key in modal. Returns true if consumed.
     */
    public boolean keyPressed(int keyCode) {
        if (keyCode == 256) { // Escape — always dismiss so the player can never get stuck
            onCancel.run();
            return true;
        }
        return false;
    }

    public record SummaryLine(String itemId, String text, String nbtJson) {
        public static SummaryLine item(String itemId, String text) {
            return new SummaryLine(itemId, text, "");
        }

        public static SummaryLine item(String itemId, String text, String nbtJson) {
            return new SummaryLine(itemId, text, nbtJson != null ? nbtJson : "");
        }

        public static SummaryLine text(String text) {
            return new SummaryLine("", text, "");
        }
    }
}

