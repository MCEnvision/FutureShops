package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.CatalogItem;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SBuyRequestPacket;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.network.packets.C2SVerifyAdminCartPacket;
import com.enviouse.futureshops.network.packets.S2CVerifyCartResponsePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CartScreen extends Screen implements ShopScreenMarker {
    private final Screen parent;
    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private int visibleRows;
    private int scrollIndex;
    private boolean awaitingVerification = false;
    private ConfirmationModal confirmationModal = null;

    // Flat Nocturne buttons: draw + hit-region come from the same ShopUiUtil.button call,
    // registered here each frame and consulted in mouseClicked via dispatchClicks.
    private final java.util.List<ShopUiUtil.ClickZone> clickZones = new java.util.ArrayList<>();
    // Set during renderRows when the "+" stepper is hovered, so the Shift=Max tooltip can be
    // drawn on top after super.render (geometry now comes straight from the stepper button).
    private int maxTipX = -1;
    private int maxTipY = -1;

    public CartScreen(Screen parent) {
        super(Component.translatable("gui.futureshops.cart.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Right-side drawer (Nocturne cart): a fixed-width panel anchored to the right edge over
        // the dimmed ground, rather than a full-screen takeover.
        guiW = Math.min(360, this.width - 8);
        guiH = Math.max(250, this.height - 20);
        guiLeft = this.width - guiW - 8;
        guiTop = (this.height - guiH) / 2;
        // Reserve a taller footer band (rows list shrunk to guiH-150 in renderRows) so verification
        // warnings have somewhere to go below the card; keep visibleRows in step so rows never
        // overflow the shorter card into that band.
        visibleRows = Math.max(3, (guiH - 170) / 28);
        // Footer buttons are drawn immediate-mode in render(); no widgets to register here.
    }

    @Override
    public void tick() {
        super.tick();
        if (awaitingVerification && ShopClientState.isCartVerified()) {
            awaitingVerification = false;
            List<S2CVerifyCartResponsePacket.CartWarning> warnings = ShopClientState.getCartWarnings();
            if (warnings.isEmpty()) {
                // All OK — show confirmation modal before checkout
                showCheckoutModal();
            }
            // If warnings exist, they'll render — user must click Checkout again to force
        }
    }

    private void showCheckoutModal() {
        List<ShopClientState.CartEntry> entries = ShopClientState.getCartEntries();
        List<ConfirmationModal.SummaryLine> lines = new java.util.ArrayList<>();
        for (ShopClientState.CartEntry entry : entries) {
            CatalogItem item = ShopClientState.getCatalogItem(entry.listingId()).orElse(null);
            String name = item != null ? item.displayName() : entry.listingId();
            // Icon id must be a registry itemId (a valid ResourceLocation); the listingId is only the
            // cart key and may not parse. Fall back to the listingId string only when the row is unknown.
            String iconId = item != null ? item.itemId() : entry.listingId();
            String nbt = item != null ? item.nbtJson() : "";
            lines.add(ConfirmationModal.SummaryLine.item(iconId, name + " ×" + entry.quantity(), nbt));
        }
        String totalStr = ShopUiUtil.formatMinorUnits(ShopClientState.getCartTotalMinorUnits());
        confirmationModal = new ConfirmationModal(
                Component.translatable("gui.futureshops.cart.confirm.title").getString(),
                lines,
                Component.translatable("gui.futureshops.item_detail.total_cost", totalStr, ShopClientState.getCurrencyName()).getString(),
                (modal, paymentSource) -> {
                    modal.setProcessing();
                    sendCheckout(paymentSource);
                },
                () -> confirmationModal = null
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();
        maxTipX = -1;
        maxTipY = -1;
        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        ShopUiUtil.renderAccentLine(graphics, guiLeft + 2, guiTop, guiW - 4);
        renderHeader(graphics);
        renderRows(graphics, mouseX, mouseY);
        renderSummary(graphics);

        // Cart verification warnings — drawn TOP-DOWN in the band reserved just below the rows
        // card (listH was shrunk to guiH-150 for exactly this), clipped to a hard lower bound so
        // extra warnings can never spill back up over the last row or down onto the summary bar.
        List<S2CVerifyCartResponsePacket.CartWarning> warnings = ShopClientState.getCartWarnings();
        if (!warnings.isEmpty()) {
            int warnY = (guiTop + 52) + (guiH - 150) + 2; // just below the rows card
            int maxWarnY = guiTop + guiH - 68;            // stay above the summary bar
            int shown = 0;
            for (int wi = 0; wi < Math.min(warnings.size(), 3) && warnY <= maxWarnY; wi++) {
                S2CVerifyCartResponsePacket.CartWarning w = warnings.get(wi);
                // Localized on the client from the warning code + args (server sends no English).
                String warnText = ShopUiUtil.cartWarningLine(w.cartLineIndex(), w.warningCode(), w.detail()).getString();
                graphics.drawString(this.font, this.font.plainSubstrByWidth(warnText, guiW - 30),
                        guiLeft + 10, warnY, ShopColors.ERROR, false);
                warnY += 12;
                shown++;
            }
            if (warnings.size() > shown && warnY <= maxWarnY) {
                graphics.drawString(this.font, Component.translatable("gui.futureshops.cart.more_warnings", warnings.size() - shown),
                        guiLeft + 10, warnY, ShopColors.ERROR, false);
            }
        }

        // Awaiting verification indicator
        if (awaitingVerification) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.cart.verifying"),
                    guiLeft + guiW / 2, guiTop + guiH - 62, ShopColors.ACCENT_ORANGE);
        }

        // Status toast floats above the drawer, but when the drawer is flush to the top edge there
        // is no room there — drop it just below the window instead of clamping it onto the header.
        int statusY = (guiTop - 24 >= 6) ? (guiTop - 24) : (guiTop + guiH + 2);
        ShopUiUtil.renderStatusPanel(graphics, this.font, guiLeft + 10, statusY, guiW - 20);

        // Footer buttons — flat Nocturne primitives at their former bounds.
        boolean canCheckout = !ShopClientState.getCartEntries().isEmpty();
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 10, guiTop + guiH - 24, 48, 18,
                Component.translatable("gui.futureshops.cart.back"), ShopUiUtil.ButtonStyle.SECONDARY, true, this::onClose);
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 64, guiTop + guiH - 24, 48, 18,
                Component.translatable("gui.futureshops.cart.clear_btn"), ShopUiUtil.ButtonStyle.DANGER, true, () -> {
                    ShopClientState.clearCart();
                    ShopClientState.clearCartVerification();
                });
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 100, guiTop + guiH - 24, 90, 18,
                Component.translatable("gui.futureshops.cart.checkout_btn"), ShopUiUtil.ButtonStyle.PRIMARY, canCheckout, this::requestVerifyAndCheckout);

        super.render(graphics, mouseX, mouseY, partialTick);

        // Tooltip for cart "+" hover (Shift+Click: Max) — geometry captured by the stepper button.
        if (maxTipX >= 0) {
            graphics.renderTooltip(this.font, Component.translatable("gui.futureshops.cart.tooltip.shift_max"), maxTipX, maxTipY);
        }

        // Spec §8: Render confirmation modal on top of everything
        if (confirmationModal != null) {
            confirmationModal.render(graphics, this.font, this.width, this.height, mouseX, mouseY);
            if (confirmationModal.shouldAutoDismiss()) {
                confirmationModal = null;
            }
        }
    }

    private void renderHeader(GuiGraphics graphics) {
        int hx = guiLeft + 8;
        int hy = guiTop + 8;
        int hw = guiW - 16;
        ShopUiUtil.renderHeroHeader(graphics, this.font, hx, hy, hw,
                this.title.getString(),
                Component.translatable("gui.futureshops.cart.subtitle").getString());
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int listX = guiLeft + 10;
        int listY = guiTop + 52;
        int listW = guiW - 20;
        // Shrunk by 30px vs. the old guiH-120 to reserve a warning band below the card (Fix 1).
        int listH = guiH - 150;
        ShopUiUtil.renderCard(graphics, listX, listY, listW, listH);

        List<ShopClientState.CartEntry> entries = ShopClientState.getCartEntries();
        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.cart.empty"), listX + listW / 2, listY + listH / 2 - 8, ShopColors.TEXT_MUTED);
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.cart.empty_hint"), listX + listW / 2, listY + listH / 2 + 6, ShopColors.TEXT_FAINT);
            return;
        }

        int maxScroll = Math.max(0, entries.size() - visibleRows);
        scrollIndex = Math.max(0, Math.min(scrollIndex, maxScroll));
        int rowY = listY + 8;
        for (int row = 0; row < visibleRows && row + scrollIndex < entries.size(); row++) {
            ShopClientState.CartEntry entry = entries.get(row + scrollIndex);
            CatalogItem item = ShopClientState.getCatalogItem(entry.listingId()).orElse(null);
            if (item == null) continue;
            int y = rowY + row * 28;
            int rowBg = row % 2 == 0 ? ShopColors.SURFACE_RAISED : ShopColors.SURFACE_OVERLAY;
            ShopUiUtil.renderPanel(graphics, listX + 6, y, listW - 12, 22, rowBg, ShopColors.BORDER_SUBTLE);
            // NBT-aware icon: BEWLR items (TacZ guns etc.) resolve their model
            // from the listing tag and render as a missing texture without it.
            ShopUiUtil.renderItemIconWithNbt(graphics, this.font, item.itemId(), item.nbtJson(), listX + 10, y + 3);

            // Name — scrolls (ping-pong) when too long so modded items with lengthy names stay readable.
            ShopUiUtil.renderScrollingString(graphics, this.font, item.displayName(),
                    listX + 30, y + 7, listW - 240, ShopColors.TEXT_STRONG);

            // Quantity controls — flat mini steppers ([−] [N] [+]) with registered ClickZones.
            final ShopClientState.CartEntry rowEntry = entry;
            int ctrlX = listX + listW - 130;
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    ctrlX, y + 3, 14, 16, Component.literal("-"), ShopUiUtil.ButtonStyle.SECONDARY, true,
                    () -> ShopClientState.setCartQuantity(rowEntry.listingId(), rowEntry.quantity() - 1));
            // Wider (18px) qty window between the steppers so 3-4 digit quantities no longer
            // slide under the "+"; the value is clipped and centered inside that window.
            int qtyWinX = ctrlX + 16;
            int qtyWinW = 18;
            String qtyStr = this.font.plainSubstrByWidth(String.valueOf(entry.quantity()), qtyWinW);
            graphics.drawString(this.font, qtyStr, qtyWinX + (qtyWinW - this.font.width(qtyStr)) / 2, y + 7, ShopColors.TEXT_STRONG, false);
            boolean plusHover = ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    ctrlX + 34, y + 3, 14, 16, Component.literal("+"), ShopUiUtil.ButtonStyle.SECONDARY, true,
                    () -> {
                        if (hasShiftDown()) {
                            CatalogItem it = ShopClientState.getCatalogItem(rowEntry.listingId()).orElse(null);
                            int max = 2304;
                            if (it != null) {
                                if (!it.unlimited()) max = Math.min(max, Math.max(1, it.stock()));
                                long price = it.hasPromo() ? it.promoPrice() : it.buyPrice();
                                if (price > 0) {
                                    long bal = ShopClientState.getCurrentBalanceMinorUnits();
                                    max = Math.min(max, (int) Math.min(bal / price, 2304));
                                }
                            }
                            ShopClientState.setCartQuantity(rowEntry.listingId(), Math.max(1, max));
                        } else {
                            ShopClientState.setCartQuantity(rowEntry.listingId(), rowEntry.quantity() + 1);
                        }
                    });
            if (plusHover) {
                maxTipX = mouseX;
                maxTipY = mouseY;
            }

            // Price — currency amber
            long unitPrice = item.hasPromo() ? item.promoPrice() : item.buyPrice();
            // Start clear of the shifted "+" stepper (now ends at listW-82) and clip a touch
            // tighter so the price still stops short of the remove button.
            String priceStr = this.font.plainSubstrByWidth(ShopUiUtil.formatMinorUnits(unitPrice * entry.quantity()), 54);
            graphics.drawString(this.font, priceStr, listX + listW - 80, y + 7, ShopColors.TEXT_CURRENCY, false);

            // Remove — flat DANGER "✕" button with a registered ClickZone.
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    listX + listW - 24, y + 3, 16, 16, Component.literal("✕"), ShopUiUtil.ButtonStyle.DANGER, true,
                    () -> ShopClientState.removeFromCart(rowEntry.listingId()));
        }
    }

    private void renderSummary(GuiGraphics graphics) {
        int x = guiLeft + 10;
        int y = guiTop + guiH - 56;
        int w = guiW - 20;
        graphics.fill(x, y, x + w, y + 24, ShopColors.SURFACE_RAISED);
        ShopUiUtil.drawBorder(graphics, x, y, w, 24, ShopColors.BORDER_MUTED);
        graphics.fill(x, y, x + w, y + 2, ShopColors.ACCENT_CURRENCY);
        // Item count — truncated
        String items = this.font.plainSubstrByWidth(
                Component.translatable("gui.futureshops.cart.items_in_cart", ShopClientState.getCartTotalQuantity()).getString(), w / 2 - 10);
        graphics.drawString(this.font, items, x + 10, y + 8, ShopColors.TEXT_MUTED, false);
        // Total — amber
        String total = Component.translatable("gui.futureshops.cart.total_line",
                ShopUiUtil.formatMinorUnits(ShopClientState.getCartTotalMinorUnits())).getString();
        String clipped = this.font.plainSubstrByWidth(total, w / 2);
        graphics.drawString(this.font, clipped, x + w - this.font.width(clipped) - 10, y + 8, ShopColors.TEXT_CURRENCY, false);
    }

    private void requestVerifyAndCheckout() {
        List<ShopClientState.CartEntry> entries = ShopClientState.getCartEntries();
        if (entries.isEmpty()) return;

        // If we already have warnings (user saw them), force checkout on second click
        if (!ShopClientState.getCartWarnings().isEmpty()) {
            ShopClientState.clearCartVerification();
            showCheckoutModal();
            return;
        }

        // Build verification lines from cart state
        String shopId = ShopClientState.getActiveShopId();
        List<C2SVerifyAdminCartPacket.AdminCartLine> lines = entries.stream()
                .map(e -> {
                    CatalogItem item = ShopClientState.getCatalogItem(e.listingId()).orElse(null);
                    long expectedPrice = 0;
                    if (item != null) {
                        expectedPrice = item.hasPromo() ? item.promoPrice() : item.buyPrice();
                    }
                    return new C2SVerifyAdminCartPacket.AdminCartLine(e.listingId(), e.quantity(), expectedPrice,
                            ShopClientState.getCartNbtSnapshot(e.listingId()));
                })
                .toList();
        ShopClientState.clearCartVerification();
        awaitingVerification = true;
        ShopPackets.CHANNEL.sendToServer(new C2SVerifyAdminCartPacket(shopId, lines));
    }

    private void sendCheckout(PaymentSource paymentSource) {
        List<C2SBuyRequestPacket.LineItem> lines = ShopClientState.getCartEntries().stream()
                .map(entry -> new C2SBuyRequestPacket.LineItem(entry.listingId(), entry.quantity()))
                .toList();
        if (!lines.isEmpty()) {
            ShopPackets.CHANNEL.sendToServer(C2SBuyRequestPacket.cart(
                    ShopClientState.getActiveShopId(), lines, paymentSource));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        List<ShopClientState.CartEntry> entries = ShopClientState.getCartEntries();
        if (entries.size() > visibleRows) {
            scrollIndex = Math.max(0, Math.min(entries.size() - visibleRows, scrollIndex - (int) delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (confirmationModal != null) {
            return confirmationModal.mouseClicked(mouseX, mouseY, button, this.font);
        }
        // Flat Nocturne buttons (footer + per-row steppers/remove): run the top-most hit zone.
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmationModal != null) {
            if (confirmationModal.keyPressed(keyCode)) return true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void onTransactionResult(boolean success, String message) {
        if (confirmationModal != null) {
            if (success) {
                confirmationModal.setSuccess(message);
            } else {
                confirmationModal.setFailed(message);
            }
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
