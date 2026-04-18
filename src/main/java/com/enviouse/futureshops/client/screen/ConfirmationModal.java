package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
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
        // Dim layer
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
            graphics.drawString(font, "OK", btnX + 20, btnY + 4, ShopColors.TEXT_STRONG, true);
            return;
        }

        // Title
        int tw = font.width(title);
        graphics.drawString(font, title, modalX + (modalW - tw) / 2, modalY + 8, ShopColors.TEXT_STRONG, true);

        // Summary lines
        int lineY = modalY + 26;
        for (SummaryLine line : summaryLines) {
            if (line.itemId != null && !line.itemId.isBlank()) {
                ShopUiUtil.renderItemIcon(graphics, font, line.itemId, modalX + 10, lineY - 2);
                graphics.drawString(font, font.plainSubstrByWidth(line.text, modalW - 42), modalX + 30, lineY + 2, ShopColors.TEXT_STRONG, false);
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
        String cancelText = "Cancel";
        graphics.drawString(font, cancelText, startX + (btnW - font.width(cancelText)) / 2, lineY + 4, ShopColors.TEXT_MUTED, true);

        // Confirm
        int confirmX = startX + btnW + gap;
        if (state == State.PROCESSING) {
            graphics.fill(confirmX, lineY, confirmX + btnW, lineY + btnH, ShopColors.BTN_REST);
            ShopUiUtil.drawBorder(graphics, confirmX, lineY, btnW, btnH, ShopColors.BORDER_MUTED);
            String proc = "Processing...";
            graphics.drawString(font, proc, confirmX + (btnW - font.width(proc)) / 2, lineY + 4, ShopColors.TEXT_MUTED, false);
        } else {
            boolean confirmHover = mouseX >= confirmX && mouseX <= confirmX + btnW && mouseY >= lineY && mouseY <= lineY + btnH;
            graphics.fill(confirmX, lineY, confirmX + btnW, lineY + btnH, confirmHover ? ShopColors.BTN_CTA_HOVER : ShopColors.BTN_CTA_REST);
            ShopUiUtil.drawBorder(graphics, confirmX, lineY, btnW, btnH, ShopColors.BORDER_GLOW);
            String confirmText = "Confirm";
            graphics.drawString(font, confirmText, confirmX + (btnW - font.width(confirmText)) / 2, lineY + 4, ShopColors.TEXT_STRONG, true);
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
        if (state == State.PROCESSING || state == State.SUCCESS) {
            return true; // consume all clicks
        }

        // Check if click is outside modal → cancel
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

        // Cancel button
        if (mouseX >= startX && mouseX <= startX + btnW && mouseY >= lineY && mouseY <= lineY + btnH) {
            onCancel.run();
            return true;
        }

        // Confirm button
        int confirmX = startX + btnW + gap;
        if (mouseX >= confirmX && mouseX <= confirmX + btnW && mouseY >= lineY && mouseY <= lineY + btnH) {
            onConfirm.accept(this);
            return true;
        }

        return true; // consume click inside modal
    }

    /**
     * Handle Escape key in modal. Returns true if consumed.
     */
    public boolean keyPressed(int keyCode) {
        if (keyCode == 256) { // Escape
            if (state == State.WAITING) {
                onCancel.run();
            }
            return true;
        }
        return false;
    }

    public record SummaryLine(String itemId, String text) {
        public static SummaryLine item(String itemId, String text) {
            return new SummaryLine(itemId, text);
        }

        public static SummaryLine text(String text) {
            return new SummaryLine("", text);
        }
    }
}

