package com.enviouse.futureshopsp.client.screen;

import com.enviouse.futureshopsp.client.PlayerShopCartState;
import com.enviouse.futureshopsp.client.ShopClientState;
import com.enviouse.futureshopsp.client.ShopColors;
import com.enviouse.futureshopsp.network.ShopPackets;
import com.enviouse.futureshopsp.network.packets.C2SPlayerShopActionPacket;
import com.enviouse.futureshopsp.network.packets.C2SPlayerShopBuyPacket;
import com.enviouse.futureshopsp.network.packets.C2SVerifyCartPacket;
import com.enviouse.futureshopsp.network.packets.S2CVerifyCartResponsePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Items 34+35: Cart screen for player shop purchases.
 * Shows all items added from various player shops, with quantity controls and checkout.
 */
public class PlayerShopCartScreen extends AbstractShopScreen implements ShopScreenMarker {
    private final Screen parent;
    private int guiLeft, guiTop, guiW, guiH;
    private int scroll;
    // LGB#18: Tooltip tracking
    private String hoveredItemId = null;
    private String hoveredNbtJson = null;
    private String hoverTooltipText = null;
    private int hoverTooltipX = 0;
    private int hoverTooltipY = 0;
    private boolean awaitingVerification = false;
    private static final int SCROLLBAR_RESERVE = 16;
    private static final int REMOVE_BTN_W = 18;

    public PlayerShopCartScreen(Screen parent) {
        super(Component.translatable("gui.futureshops.player_shop_cart.title_raw"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        guiW = Math.max(300, this.width - 4);
        guiH = Math.max(200, this.height - 4);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;

        // Close / Back button
        addRenderableWidget(Button.builder(Component.literal("§c✕"), button -> onClose())
                .bounds(guiLeft + guiW - 24, guiTop + 6, 18, 14).build());

        // Clear cart button
        addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_cart.clear"), button -> {
            PlayerShopCartState.clear();
            ShopClientState.clearCartVerification();
            rebuildWidgets();
        }).bounds(guiLeft + 8, guiTop + guiH - 22, 50, 16).build());

        // Checkout button — sends verification request first
        addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_cart.checkout"),
                        button -> requestVerifyAndCheckout())
                .bounds(guiLeft + guiW - 100, guiTop + guiH - 22, 92, 16).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoveredItemId = null;
        hoveredNbtJson = null;
        hoverTooltipText = null;

        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + 2, ShopColors.ACCENT_PRIMARY);

        // Header
        graphics.drawString(this.font,
                Component.translatable("gui.futureshops.player_shop_cart.title").getString(),
                guiLeft + 10, guiTop + 10, ShopColors.TEXT_STRONG, false);

        List<PlayerShopCartState.CartEntry> entries = PlayerShopCartState.getEntries();
        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.futureshops.player_shop_cart.empty").getString(),
                    guiLeft + guiW / 2, guiTop + guiH / 2 - 10, ShopColors.TEXT_MUTED);
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.futureshops.player_shop_cart.empty_hint").getString(),
                    guiLeft + guiW / 2, guiTop + guiH / 2 + 4, ShopColors.TEXT_FAINT);
        } else {
            // LGB#12: Adaptive row height for small screens / high GUI scale
            int rowH = guiH < 240 ? 28 : 36;
            int contentY = guiTop + 30;
            int contentH = guiH - 64;
            int maxVisible = contentH / rowH;
            scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size() - maxVisible)));

            // Reserve right-margin space for scrollbar so it never overlaps the X remove button.
            int panelLeft = guiLeft + 8;
            int panelRight = guiLeft + guiW - 8 - SCROLLBAR_RESERVE;
            int panelWidth = panelRight - panelLeft;

            for (int i = 0; i < maxVisible && i + scroll < entries.size(); i++) {
                int idx = i + scroll;
                PlayerShopCartState.CartEntry entry = entries.get(idx);
                int y = contentY + i * rowH;

                boolean hovered = mouseX >= panelLeft && mouseX <= panelRight && mouseY >= y && mouseY <= y + rowH - 2;
                ShopUiUtil.renderPanel(graphics, panelLeft, y, panelWidth, rowH - 2,
                        hovered ? ShopColors.SURFACE_OVERLAY : ShopColors.SURFACE_RAISED,
                        hovered ? ShopColors.BORDER_GLOW : ShopColors.BORDER_MUTED);
                if (hovered) {
                    graphics.fill(panelLeft, y, panelRight, y + 1, ShopColors.ACCENT_PRIMARY);
                }

                // Item icon — NBT-aware when listing has non-default NBT
                boolean hasRealNbt = entry.nbtAware() && ShopUiUtil.hasNonDefaultNbt(entry.itemId(), entry.nbtJson());
                int iconX = panelLeft + 6;
                int iconY = y + (rowH - 18) / 2;
                if (hasRealNbt) {
                    ShopUiUtil.renderItemIconWithNbt(graphics, this.font, entry.itemId(), entry.nbtJson(), iconX, iconY);
                } else {
                    ShopUiUtil.renderItemIcon(graphics, this.font, entry.itemId(), iconX, iconY);
                }

                // LGB#18: Detect hover over item icon for tooltip
                if (mouseX >= iconX && mouseX <= iconX + 16 && mouseY >= iconY && mouseY <= iconY + 16) {
                    hoveredItemId = entry.itemId();
                    hoveredNbtJson = hasRealNbt ? entry.nbtJson() : "";
                }

                // Reserve right-side columns (remove X + price column) and clip name/shop so
                // long names never slide underneath the price or the X button.
                int removeX = panelRight - REMOVE_BTN_W;
                int priceColRight = removeX - 6;
                int priceColLeft = priceColRight - 126; // 126px reserved for price/barter text
                int leftContentX = iconX + 22;
                int leftContentMax = priceColLeft - 6;
                int leftContentW = Math.max(40, leftContentMax - leftContentX);

                // LGB#15: Item name with inline base quantity (e.g. "Stick ×6")
                String itemName = ShopUiUtil.getItemDisplayNameWithQty(entry.itemId(), entry.baseQuantity());
                itemName = this.font.plainSubstrByWidth(itemName, leftContentW);
                graphics.drawString(this.font, itemName, leftContentX, y + 4, ShopColors.TEXT_STRONG, false);

                // LGB#12: Compact layout — shop name on same line or second line based on row height
                String shopLabel = this.font.plainSubstrByWidth("§7" + entry.shopName(), leftContentW);
                graphics.drawString(this.font, shopLabel, leftContentX, y + 14, ShopColors.TEXT_MUTED, false);

                // LGB#2: Trade mode abbreviation badge + LGB#4: BOTH toggle / M+B locked indicator
                boolean isBoth = "BOTH".equalsIgnoreCase(entry.tradeMode());
                boolean isCompound = "MONEY_AND_BARTER".equalsIgnoreCase(entry.tradeMode());
                String modeBadge;
                String modeTooltip;
                if (isBoth) {
                    boolean chosenBarter = "BARTER".equalsIgnoreCase(entry.chosenPayment());
                    modeBadge = chosenBarter ? "§9M/B §f(B)" : "§aM/B §f($)";
                    modeTooltip = "Money or Barter — click to toggle";
                } else if (isCompound) {
                    modeBadge = "§6🔒 M+B";
                    modeTooltip = "Money + Barter (both required)";
                } else {
                    boolean barter = "BARTER".equalsIgnoreCase(entry.tradeMode());
                    modeBadge = barter ? "§9B" : "§a$";
                    modeTooltip = barter ? "Barter" : "Money";
                }
                int badgeX = leftContentX;
                int badgeY = rowH >= 36 ? y + 24 : y + 14;
                if (rowH < 36) {
                    badgeX = leftContentX + this.font.width(shopLabel) + 6;
                }
                graphics.drawString(this.font, modeBadge, badgeX, badgeY, ShopColors.TEXT_MUTED, false);
                int badgeW = this.font.width(modeBadge);
                if (mouseX >= badgeX && mouseX <= badgeX + badgeW && mouseY >= badgeY - 1 && mouseY <= badgeY + 10) {
                    hoverTooltipText = modeTooltip;
                    hoverTooltipX = mouseX;
                    hoverTooltipY = mouseY;
                }

                // LGB#18: NBT badge — only when it fits inside the reserved left content area.
                if (hasRealNbt) {
                    int nbtBadgeX = badgeX + badgeW + 4;
                    int nbtBadgeW = Math.max(28, this.font.width("NBT") + 10);
                    if (nbtBadgeX + nbtBadgeW <= leftContentMax) {
                        ShopUiUtil.drawChip(graphics, this.font, nbtBadgeX, badgeY - 1, "NBT",
                                ShopColors.SURFACE_BASE, ShopColors.STATUS_WARNING, ShopColors.STATUS_WARNING);
                    }
                }

                // ── Centered quantity / price / barter columns ───────────────────────
                int centerY = y + (rowH - 10) / 2;
                // Quantity — clickable [-] [N] [+] triplet so buyers can tweak cart lines
                // without going back to the shop block. Bounds mirror what mouseClicked
                // reads so hit-testing and rendering can't drift apart.
                String qtyStr = String.valueOf(entry.quantity());
                int qtyColRight = priceColLeft - 4;
                int qtyColLeft = qtyColRight - 60;
                int qtyColW = 60;
                int qtyBtnSize = 10;
                int qtyTextW = this.font.width(qtyStr);
                int qtyTextX = qtyColLeft + (qtyColW - qtyTextW) / 2;
                int qtyMinusX = qtyColLeft + 2;
                int qtyPlusX = qtyColRight - qtyBtnSize - 2;
                int qtyBtnY = centerY - 1;
                boolean minusHover = mouseX >= qtyMinusX && mouseX <= qtyMinusX + qtyBtnSize
                        && mouseY >= qtyBtnY && mouseY <= qtyBtnY + qtyBtnSize;
                boolean plusHover = mouseX >= qtyPlusX && mouseX <= qtyPlusX + qtyBtnSize
                        && mouseY >= qtyBtnY && mouseY <= qtyBtnY + qtyBtnSize;
                graphics.fill(qtyMinusX, qtyBtnY, qtyMinusX + qtyBtnSize, qtyBtnY + qtyBtnSize,
                        minusHover ? ShopColors.BTN_HOVER : ShopColors.BTN_REST);
                ShopUiUtil.drawBorder(graphics, qtyMinusX, qtyBtnY, qtyBtnSize, qtyBtnSize, ShopColors.BORDER_MUTED);
                graphics.drawString(this.font, "-", qtyMinusX + 3, qtyBtnY + 1, ShopColors.TEXT_STRONG, false);
                graphics.fill(qtyPlusX, qtyBtnY, qtyPlusX + qtyBtnSize, qtyBtnY + qtyBtnSize,
                        plusHover ? ShopColors.BTN_HOVER : ShopColors.BTN_REST);
                ShopUiUtil.drawBorder(graphics, qtyPlusX, qtyBtnY, qtyBtnSize, qtyBtnSize, ShopColors.BORDER_MUTED);
                graphics.drawString(this.font, "+", qtyPlusX + 3, qtyBtnY + 1, ShopColors.TEXT_STRONG, false);
                graphics.drawString(this.font, qtyStr, qtyTextX, centerY, ShopColors.TEXT_STRONG, false);

                // LGB#3/#4: Price display — depends on trade mode and chosen payment
                boolean showBarter;
                if (isBoth) {
                    showBarter = "BARTER".equalsIgnoreCase(entry.chosenPayment());
                } else {
                    showBarter = "BARTER".equalsIgnoreCase(entry.tradeMode());
                }

                int priceColW = priceColRight - priceColLeft;
                if (isCompound) {
                    String moneyStr = ShopUiUtil.formatMinorUnits(entry.totalPrice());
                    int totalBarter = saturatingMultiplyToInt(entry.barterItemCount(), entry.quantity());
                    String barterName = ShopUiUtil.getItemDisplayName(entry.barterItemId());
                    String combined = "§a" + moneyStr + "§f + §9" + totalBarter + "× " + barterName;
                    if (this.font.width(combined) > priceColW) {
                        int prefixW = this.font.width("§a" + moneyStr + "§f + §9" + totalBarter + "× ");
                        String trimmedName = this.font.plainSubstrByWidth(barterName, Math.max(10, priceColW - prefixW));
                        combined = "§a" + moneyStr + "§f + §9" + totalBarter + "× " + trimmedName;
                    }
                    int combinedW = this.font.width(combined);
                    graphics.drawString(this.font, combined, priceColLeft + (priceColW - combinedW) / 2, centerY, ShopColors.TEXT_STRONG, false);
                } else if (showBarter) {
                    int totalBarter = saturatingMultiplyToInt(entry.barterItemCount(), entry.quantity());
                    String barterStr = totalBarter + "× " +
                            this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayName(entry.barterItemId()), priceColW - 30);
                    int barterW = this.font.width(barterStr);
                    graphics.drawString(this.font, barterStr, priceColLeft + (priceColW - barterW) / 2, centerY, ShopColors.TEXT_BARTER_SOFT, false);
                } else {
                    String priceStr = ShopUiUtil.formatMinorUnits(entry.totalPrice());
                    int priceW = this.font.width(priceStr);
                    graphics.drawString(this.font, priceStr, priceColLeft + (priceColW - priceW) / 2, centerY, ShopColors.TEXT_CURRENCY, false);
                }

                // Remove button — sits inside the panel but outside the scrollbar reserve.
                String removeStr = "§c✕";
                graphics.drawString(this.font, removeStr, removeX + 4, centerY, ShopColors.STATUS_DANGER, false);
            }

            // Scroll indicators
            ShopUiUtil.renderScrollIndicators(graphics, this.font, guiLeft + 8, contentY, guiW - 16, contentH, scroll, maxVisible, entries.size());
        }

        // Summary bar — "N items · Total X$ · Barter Included" (Barter phrase in blue).
        int summaryY = guiTop + guiH - 40;
        graphics.fill(guiLeft + 8, summaryY, guiLeft + guiW - 8, summaryY + 1, ShopColors.BORDER_MUTED);

        PlayerShopCartState.CartSummary cartSummary = PlayerShopCartState.buildSummary();
        boolean hasMoney = cartSummary.moneyTotal() > 0;
        boolean hasBarter = !cartSummary.barterTotals().isEmpty();

        StringBuilder sb = new StringBuilder();
        String itemsKey = cartSummary.itemCount() == 1
                ? "gui.futureshops.player_shop_cart.items_one"
                : "gui.futureshops.player_shop_cart.items_many";
        sb.append("§f").append(Component.translatable(itemsKey, cartSummary.itemCount()).getString())
                .append(" §8· §f")
                .append(Component.translatable("gui.futureshops.player_shop_cart.total").getString())
                .append(' ');
        if (hasMoney) {
            sb.append("§a").append(ShopUiUtil.formatMinorUnits(cartSummary.moneyTotal())).append("$");
        } else if (!hasBarter) {
            sb.append("§7—");
        } else {
            sb.append("§7—");
        }
        if (hasBarter) {
            // Replace the per-item barter listing with a short blue "Barter Included" marker
            // so the summary never overflows as more barter ingredients are added.
            sb.append(" §8· §9Barter Included");
        }
        String summary = sb.toString();
        int summaryMaxW = guiW - 150;
        if (this.font.width(summary) > summaryMaxW) {
            summary = this.font.plainSubstrByWidth(summary, summaryMaxW - 6) + "§8…";
        }
        graphics.drawString(this.font, summary, guiLeft + 66, summaryY + 6, ShopColors.TEXT_STRONG, false);

        // NBT warning — drawn BELOW the summary, not above, so long summaries never overlap it.
        boolean hasNbtEntries = entries.stream().anyMatch(e -> e.nbtAware() && ShopUiUtil.hasNonDefaultNbt(e.itemId(), e.nbtJson()));
        int warnY = summaryY + 20;
        if (hasNbtEntries) {
            graphics.drawString(this.font, "§e⚠ NBT items in cart — verify listings haven't changed before checkout",
                    guiLeft + 66, warnY, ShopColors.STATUS_WARNING, false);
            warnY += 10;
        }

        // Cart verification warnings from server — stack under the NBT warning.
        List<S2CVerifyCartResponsePacket.CartWarning> warnings = ShopClientState.getCartWarnings();
        if (!warnings.isEmpty()) {
            for (int wi = 0; wi < Math.min(warnings.size(), 3); wi++) {
                S2CVerifyCartResponsePacket.CartWarning w = warnings.get(wi);
                String warnText = "§c⚠ #" + (w.cartLineIndex() + 1) + ": " + w.detail();
                graphics.drawString(this.font, this.font.plainSubstrByWidth(warnText, guiW - 80),
                        guiLeft + 66, warnY, ShopColors.STATUS_DANGER, false);
                warnY += 10;
            }
            if (warnings.size() > 3) {
                graphics.drawString(this.font, "§c... and " + (warnings.size() - 3) + " more warnings",
                        guiLeft + 66, warnY, ShopColors.STATUS_DANGER, false);
            }
        }

        // Awaiting verification indicator
        if (awaitingVerification) {
            graphics.drawCenteredString(this.font, "§eVerifying cart...",
                    guiLeft + guiW / 2, summaryY + 6, ShopColors.STATUS_WARNING);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        // LGB#18: Render tooltip after everything
        if (hoveredItemId != null) {
            ShopUiUtil.renderItemTooltip(graphics, this.font, hoveredItemId,
                    hoveredNbtJson != null ? hoveredNbtJson : "", mouseX, mouseY);
        } else if (hoverTooltipText != null) {
            graphics.renderTooltip(this.font, Component.literal(hoverTooltipText), hoverTooltipX, hoverTooltipY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<PlayerShopCartState.CartEntry> entries = PlayerShopCartState.getEntries();
        // LGB#12: Adaptive row height matching render()
        int rowH = guiH < 240 ? 28 : 36;
        int contentY = guiTop + 30;
        int contentH = guiH - 64;
        int maxVisible = contentH / rowH;

        int panelLeft = guiLeft + 8;
        int panelRight = guiLeft + guiW - 8 - SCROLLBAR_RESERVE;
        for (int i = 0; i < maxVisible && i + scroll < entries.size(); i++) {
            int idx = i + scroll;
            PlayerShopCartState.CartEntry entry = entries.get(idx);
            int y = contentY + i * rowH;

            int removeX = panelRight - REMOVE_BTN_W;
            if (mouseX >= removeX && mouseX <= removeX + REMOVE_BTN_W && mouseY >= y && mouseY <= y + rowH - 2) {
                PlayerShopCartState.remove(idx);
                return true;
            }

            // Inline qty [-] / [+] buttons — bounds must mirror render() exactly.
            int priceColRight = removeX - 6;
            int priceColLeft = priceColRight - 126;
            int qtyColRight = priceColLeft - 4;
            int qtyColLeft = qtyColRight - 60;
            int centerY = y + (rowH - 10) / 2;
            int qtyBtnSize = 10;
            int qtyMinusX = qtyColLeft + 2;
            int qtyPlusX = qtyColRight - qtyBtnSize - 2;
            int qtyBtnY = centerY - 1;
            if (mouseX >= qtyMinusX && mouseX <= qtyMinusX + qtyBtnSize
                    && mouseY >= qtyBtnY && mouseY <= qtyBtnY + qtyBtnSize) {
                int delta = Screen.hasShiftDown() ? 10 : 1;
                // Server re-validates stock on checkout; setQuantity(<=0) removes the line.
                PlayerShopCartState.setQuantity(idx, entry.quantity() - delta);
                return true;
            }
            if (mouseX >= qtyPlusX && mouseX <= qtyPlusX + qtyBtnSize
                    && mouseY >= qtyBtnY && mouseY <= qtyBtnY + qtyBtnSize) {
                int delta = Screen.hasShiftDown() ? 10 : 1;
                int newQty = Math.min(999, entry.quantity() + delta);
                PlayerShopCartState.setQuantity(idx, newQty);
                return true;
            }

            // Click on item icon or name column → reopen the source shop at this listing.
            // Uses the existing VISIT action which opens any shop block in visitor mode.
            int iconX = panelLeft + 6;
            int leftContentMax = priceColLeft - 6;
            if (mouseX >= iconX && mouseX <= leftContentMax
                    && mouseY >= y && mouseY <= y + rowH - 2) {
                // Reserve the BOTH-mode badge toggle so it still wins the click below.
                boolean clickedBothBadge = false;
                if ("BOTH".equalsIgnoreCase(entry.tradeMode())) {
                    int badgeY = rowH >= 36 ? y + 24 : y + 14;
                    if (mouseX >= panelLeft + 28 && mouseX <= panelRight - 140
                            && mouseY >= badgeY - 2 && mouseY <= badgeY + 12) {
                        clickedBothBadge = true;
                    }
                }
                if (!clickedBothBadge) {
                    ShopPackets.sendToServer(new C2SPlayerShopActionPacket(
                            entry.shopPos(), "VISIT", entry.listingIndex(), 0));
                    return true;
                }
            }

            if ("BOTH".equalsIgnoreCase(entry.tradeMode())) {
                int badgeY = rowH >= 36 ? y + 24 : y + 14;
                if (mouseX >= panelLeft + 28 && mouseX <= panelRight - 140 && mouseY >= badgeY - 2 && mouseY <= badgeY + 12) {
                    PlayerShopCartState.togglePayment(idx);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
        List<PlayerShopCartState.CartEntry> entries = PlayerShopCartState.getEntries();
        // LGB#12: Adaptive row height matching render()
        int rowH = guiH < 240 ? 28 : 36;
        int contentH = guiH - 64;
        int maxVisible = contentH / rowH;
        scroll = Math.max(0, Math.min(Math.max(0, entries.size() - maxVisible), scroll - (int) delta));
        return true;
    }

    private void requestVerifyAndCheckout() {
        List<PlayerShopCartState.CartEntry> entries = PlayerShopCartState.getEntries();
        if (entries.isEmpty()) return;

        // Build verification lines from cart state
        List<C2SVerifyCartPacket.CartLine> lines = entries.stream()
                .map(e -> new C2SVerifyCartPacket.CartLine(
                        e.shopPos(), e.listingIndex(), e.quantity(),
                        e.itemId(), e.unitPriceMinor(),
                        e.nbtAware(), e.tradeMode()))
                .toList();
        ShopClientState.clearCartVerification();
        awaitingVerification = true;
        ShopPackets.sendToServer(new C2SVerifyCartPacket(lines));
    }

    private static int saturatingMultiplyToInt(int left, int right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ignored) {
            return (left < 0) == (right < 0) ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (awaitingVerification && ShopClientState.isCartVerified()) {
            awaitingVerification = false;
            List<S2CVerifyCartResponsePacket.CartWarning> warnings = ShopClientState.getCartWarnings();
            if (warnings.isEmpty()) {
                // All OK — proceed with checkout
                checkout();
            }
            // If there are warnings, they'll be shown in render() — user must click Checkout again to confirm
        }
    }

    private void checkout() {
        List<PlayerShopCartState.CartEntry> entries = PlayerShopCartState.getEntries();
        for (PlayerShopCartState.CartEntry entry : entries) {
            // LGB#4: Pass chosen payment method for BOTH-mode trades
            ShopPackets.sendToServer(new C2SPlayerShopBuyPacket(
                    entry.shopPos(), entry.listingIndex(), entry.quantity(), entry.chosenPayment()));
        }
        PlayerShopCartState.clear();
        ShopClientState.clearCartVerification();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
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
