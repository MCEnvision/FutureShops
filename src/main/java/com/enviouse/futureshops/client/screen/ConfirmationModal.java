package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.money.PaymentSource;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;
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
    private final BiConsumer<ConfirmationModal, PaymentSource> onPaymentConfirm;
    private final Runnable onCancel;
    private final boolean paymentSourceRequired;
    private PaymentSource selectedPaymentSource;

    private State state = State.WAITING;
    private String resultMessage = "";
    private long resultTimestamp = 0;
    private long processingStartedAt = 0;
    /** How long to wait for a server response before auto-failing so the player can escape. */
    private static final long PROCESSING_TIMEOUT_MS = 10_000L;

    // Modal dimensions (computed in render)
    private int modalX, modalY, modalW, modalH;

    // Flat Nocturne buttons: draw + hit-region come from the same ShopUiUtil.button calls,
    // registered here each render and consulted in mouseClicked via dispatchClicks.
    private final List<ShopUiUtil.ClickZone> clickZones = new java.util.ArrayList<>();

    public ConfirmationModal(String title, List<SummaryLine> summaryLines, String totalText,
                             Consumer<ConfirmationModal> onConfirm, Runnable onCancel) {
        this.title = title;
        this.summaryLines = List.copyOf(summaryLines);
        this.totalText = totalText;
        this.onConfirm = onConfirm;
        this.onPaymentConfirm = null;
        this.onCancel = onCancel;
        this.paymentSourceRequired = false;
    }

    public ConfirmationModal(String title, List<SummaryLine> summaryLines, String totalText,
                             BiConsumer<ConfirmationModal, PaymentSource> onConfirm,
                             Runnable onCancel) {
        this.title = title;
        this.summaryLines = List.copyOf(summaryLines);
        this.totalText = totalText;
        this.onConfirm = null;
        this.onPaymentConfirm = onConfirm;
        this.onCancel = onCancel;
        this.paymentSourceRequired = true;
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
        clickZones.clear();
        // Dim layer — single pass is enough now that we're drawing above the item-stack
        // Z plane. Previously the layer appeared "weak" because bright item icons / glyphs
        // rendered at a higher Z were punching through.
        ShopUiUtil.renderDimBackdrop(graphics, screenW, screenH);

        // Modal panel — responsive to screen size, never exceeds viewport
        modalW = Math.min(260, Math.max(180, screenW - 20));
        modalH = Math.min(screenH - 20, 60 + summaryLines.size() * 16 + 40
                + (paymentSourceRequired ? 30 : 0));
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
            // OK button — flat Nocturne SECONDARY, dismisses via onCancel.
            int btnX = modalX + (modalW - 50) / 2;
            int btnY = modalY + modalH / 2 + 6;
            ShopUiUtil.button(graphics, font, clickZones, mouseX, mouseY, btnX, btnY, 50, 16,
                    Component.translatable("gui.futureshops.modal.ok"), ShopUiUtil.ButtonStyle.SECONDARY, true, onCancel);
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

        if (paymentSourceRequired) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.futureshops.modal.payment_source"),
                    modalX + modalW / 2, lineY, ShopColors.TEXT_MUTED);
            lineY += 11;
            int sourceGap = 8;
            int sourceW = (modalW - 28 - sourceGap) / 2;
            int sourceX = modalX + 14;
            boolean waiting = state == State.WAITING;
            ShopUiUtil.button(graphics, font, clickZones, mouseX, mouseY,
                    sourceX, lineY, sourceW, 16,
                    Component.translatable("gui.futureshops.modal.inventory_cash"),
                    selectedPaymentSource == PaymentSource.PHYSICAL
                            ? ShopUiUtil.ButtonStyle.PRIMARY : ShopUiUtil.ButtonStyle.SECONDARY,
                    waiting, () -> selectedPaymentSource = PaymentSource.PHYSICAL);
            ShopUiUtil.button(graphics, font, clickZones, mouseX, mouseY,
                    sourceX + sourceW + sourceGap, lineY, sourceW, 16,
                    Component.translatable("gui.futureshops.modal.wallet_balance"),
                    selectedPaymentSource == PaymentSource.WALLET
                            ? ShopUiUtil.ButtonStyle.PRIMARY : ShopUiUtil.ButtonStyle.SECONDARY,
                    waiting, () -> selectedPaymentSource = PaymentSource.WALLET);
            lineY += 22;
        }

        // Buttons — flat Nocturne primitives.
        int btnW = 70;
        int btnH = 16;
        int gap = 12;
        int totalBtnW = btnW * 2 + gap;
        int startX = modalX + (modalW - totalBtnW) / 2;

        // Cancel — always active (also the escape hatch from PROCESSING).
        ShopUiUtil.button(graphics, font, clickZones, mouseX, mouseY, startX, lineY, btnW, btnH,
                Component.translatable("gui.futureshops.modal.cancel"), ShopUiUtil.ButtonStyle.SECONDARY, true, onCancel);

        // Confirm — PRIMARY when WAITING; a disabled "Processing…" placeholder while PROCESSING.
        int confirmX = startX + btnW + gap;
        if (state == State.PROCESSING) {
            ShopUiUtil.button(graphics, font, clickZones, mouseX, mouseY, confirmX, lineY, btnW, btnH,
                    Component.translatable("gui.futureshops.modal.processing"), ShopUiUtil.ButtonStyle.PRIMARY, false, null);
        } else {
            boolean canConfirm = !paymentSourceRequired || selectedPaymentSource != null;
            ShopUiUtil.button(graphics, font, clickZones, mouseX, mouseY, confirmX, lineY, btnW, btnH,
                    Component.translatable("gui.futureshops.modal.confirm"), ShopUiUtil.ButtonStyle.PRIMARY, canConfirm,
                    () -> {
                        if (paymentSourceRequired) {
                            onPaymentConfirm.accept(this, selectedPaymentSource);
                        } else {
                            onConfirm.accept(this);
                        }
                    });
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
        // Flat Nocturne buttons (OK / Cancel / Confirm, registered during render).
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
            return true;
        }
        if (state == State.FAILED || state == State.SUCCESS) {
            return true; // OK handled above (FAILED) or auto-dismissing (SUCCESS); consume otherwise.
        }

        // Check if click is outside modal → cancel (works in WAITING and PROCESSING)
        if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
            onCancel.run();
            return true;
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
