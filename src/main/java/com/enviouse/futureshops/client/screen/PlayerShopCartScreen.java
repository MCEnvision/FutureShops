package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.PlayerShopCartState;
import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SPlayerShopBuyPacket;
import com.enviouse.futureshops.network.packets.C2SVerifyCartPacket;
import com.enviouse.futureshops.network.packets.S2CVerifyCartResponsePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Items 34+35: Cart screen for player shop purchases.
 * Shows all items added from various player shops, with quantity controls and checkout.
 */
public class PlayerShopCartScreen extends Screen implements ShopScreenMarker {
    private final Screen parent;
    private int guiLeft, guiTop, guiW, guiH;
    private int scroll;
    // LGB#18: Tooltip tracking
    private String hoveredItemId = null;
    private String hoveredNbtJson = null;
    private boolean awaitingVerification = false;

    public PlayerShopCartScreen(Screen parent) {
        super(Component.literal("Player Shop Cart"));
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
        addRenderableWidget(Button.builder(Component.literal("§cClear"), button -> {
            PlayerShopCartState.clear();
            rebuildWidgets();
        }).bounds(guiLeft + 8, guiTop + guiH - 22, 50, 16).build());

        // Checkout button — sends verification request first
        addRenderableWidget(Button.builder(Component.literal("§a✓ Checkout"), button -> requestVerifyAndCheckout())
                .bounds(guiLeft + guiW - 100, guiTop + guiH - 22, 92, 16).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoveredItemId = null;
        hoveredNbtJson = null;

        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + 2, ShopColors.ACCENT_PRIMARY);

        // Header
        graphics.drawString(this.font, "§l🛒 Player Shop Cart", guiLeft + 10, guiTop + 10, ShopColors.TEXT_STRONG, false);

        List<PlayerShopCartState.CartEntry> entries = PlayerShopCartState.getEntries();
        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, "§7Your cart is empty", guiLeft + guiW / 2, guiTop + guiH / 2 - 10, ShopColors.TEXT_MUTED);
            graphics.drawCenteredString(this.font, "§7Browse player shops to add items", guiLeft + guiW / 2, guiTop + guiH / 2 + 4, ShopColors.TEXT_FAINT);
        } else {
            // LGB#12: Adaptive row height for small screens / high GUI scale
            int rowH = guiH < 240 ? 28 : 36;
            int contentY = guiTop + 30;
            int contentH = guiH - 64;
            int maxVisible = contentH / rowH;
            scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size() - maxVisible)));

            for (int i = 0; i < maxVisible && i + scroll < entries.size(); i++) {
                int idx = i + scroll;
                PlayerShopCartState.CartEntry entry = entries.get(idx);
                int y = contentY + i * rowH;

                boolean hovered = mouseX >= guiLeft + 8 && mouseX <= guiLeft + guiW - 8 && mouseY >= y && mouseY <= y + rowH - 2;
                ShopUiUtil.renderPanel(graphics, guiLeft + 8, y, guiW - 16, rowH - 2,
                        hovered ? ShopColors.SURFACE_OVERLAY : ShopColors.SURFACE_RAISED,
                        hovered ? ShopColors.BORDER_GLOW : ShopColors.BORDER_MUTED);
                if (hovered) {
                    graphics.fill(guiLeft + 8, y, guiLeft + guiW - 8, y + 1, ShopColors.ACCENT_PRIMARY);
                }

                // Item icon — NBT-aware when listing has non-default NBT
                boolean hasRealNbt = entry.nbtAware() && ShopUiUtil.hasNonDefaultNbt(entry.itemId(), entry.nbtJson());
                if (hasRealNbt) {
                    ShopUiUtil.renderItemIconWithNbt(graphics, this.font, entry.itemId(), entry.nbtJson(), guiLeft + 14, y + (rowH - 18) / 2);
                } else {
                    ShopUiUtil.renderItemIcon(graphics, this.font, entry.itemId(), guiLeft + 14, y + (rowH - 18) / 2);
                }

                // LGB#18: Detect hover over item icon for tooltip
                int iconY = y + (rowH - 18) / 2;
                if (mouseX >= guiLeft + 14 && mouseX <= guiLeft + 30 && mouseY >= iconY && mouseY <= iconY + 16) {
                    hoveredItemId = entry.itemId();
                    hoveredNbtJson = hasRealNbt ? entry.nbtJson() : "";
                }

                // LGB#15: Item name with inline base quantity (e.g. "Stick ×6")
                String itemName = ShopUiUtil.getItemDisplayNameWithQty(entry.itemId(), entry.baseQuantity());
                itemName = this.font.plainSubstrByWidth(itemName, guiW / 2 - 40);
                graphics.drawString(this.font, itemName, guiLeft + 36, y + 4, ShopColors.TEXT_STRONG, false);

                // LGB#12: Compact layout — shop name on same line or second line based on row height
                String shopLabel = this.font.plainSubstrByWidth("§7" + entry.shopName(), guiW / 2 - 40);
                graphics.drawString(this.font, shopLabel, guiLeft + 36, y + 14, ShopColors.TEXT_MUTED, false);

                // LGB#2: Trade mode abbreviation badge + LGB#4: BOTH toggle indicator
                boolean isBoth = "BOTH".equalsIgnoreCase(entry.tradeMode());
                String modeBadge;
                if (isBoth) {
                    boolean chosenBarter = "BARTER".equalsIgnoreCase(entry.chosenPayment());
                    modeBadge = chosenBarter ? "§9⚒ Barter" : "§a$ Money";
                } else {
                    modeBadge = switch (entry.tradeMode().toUpperCase(java.util.Locale.ROOT)) {
                        case "BARTER" -> "§9B";
                        case "MONEY_AND_BARTER" -> "§dM+B";
                        default -> "§a$";
                    };
                }
                int badgeX = guiLeft + 36;
                int badgeY = rowH >= 36 ? y + 24 : y + 14;
                // For compact rows, shift badge right of shop name
                if (rowH < 36) {
                    badgeX = guiLeft + 36 + this.font.width(shopLabel) + 6;
                }
                graphics.drawString(this.font, modeBadge, badgeX, badgeY, ShopColors.TEXT_MUTED, false);

                // LGB#4: Clickable toggle hint for BOTH mode
                if (isBoth) {
                    int toggleX = badgeX + this.font.width(modeBadge) + 4;
                    graphics.drawString(this.font, "§8[toggle]", toggleX, badgeY, ShopColors.TEXT_FAINT, false);
                }

                // LGB#18: NBT badge — only when nbtAware and item has non-default NBT
                if (hasRealNbt) {
                    int nbtBadgeX = badgeX + this.font.width(modeBadge) + (isBoth ? this.font.width("§8[toggle]") + 8 : 4);
                    ShopUiUtil.drawChip(graphics, this.font, nbtBadgeX, badgeY - 1, "NBT",
                            ShopColors.SURFACE_BASE, ShopColors.STATUS_WARNING, ShopColors.STATUS_WARNING);
                }

                // Quantity
                int qtyX = guiLeft + guiW - 200;
                graphics.drawString(this.font, "Qty: " + entry.quantity(), qtyX, y + 4, ShopColors.TEXT_STRONG, false);

                // LGB#3/#4: Price display — depends on trade mode and chosen payment
                boolean showBarter;
                if (isBoth) {
                    showBarter = "BARTER".equalsIgnoreCase(entry.chosenPayment());
                } else {
                    showBarter = "BARTER".equalsIgnoreCase(entry.tradeMode());
                }
                boolean isCompound = "MONEY_AND_BARTER".equalsIgnoreCase(entry.tradeMode());

                if (isCompound) {
                    // Show both money and barter cost
                    String moneyStr = ShopUiUtil.formatMinorUnits(entry.totalPrice());
                    graphics.drawString(this.font, moneyStr, guiLeft + guiW - 120, y + 4, ShopColors.TEXT_CURRENCY, false);
                    // LGB#16: Barter info with item name
                    String barterStr = "+" + (entry.barterItemCount() * entry.quantity()) + "× " +
                            this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayName(entry.barterItemId()), 50);
                    graphics.drawString(this.font, barterStr, guiLeft + guiW - 120, y + 14, ShopColors.TEXT_BARTER_SOFT, false);
                } else if (showBarter) {
                    // LGB#16: Barter cost with base quantity context
                    int totalBarter = entry.barterItemCount() * entry.quantity();
                    String barterStr = totalBarter + "× " +
                            this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayName(entry.barterItemId()), 60);
                    graphics.drawString(this.font, barterStr, guiLeft + guiW - 120, y + 4, ShopColors.TEXT_BARTER_SOFT, false);
                    if (entry.baseQuantity() > 1) {
                        String perTx = "per " + entry.baseQuantity() + "/tx";
                        graphics.drawString(this.font, perTx, guiLeft + guiW - 120, y + 14, ShopColors.TEXT_FAINT, false);
                    }
                } else {
                    String priceStr = ShopUiUtil.formatMinorUnits(entry.totalPrice());
                    graphics.drawString(this.font, priceStr, guiLeft + guiW - 120, y + 4, ShopColors.TEXT_CURRENCY, false);
                    if (entry.baseQuantity() > 1) {
                        String perTx = "per " + entry.baseQuantity() + "/tx";
                        graphics.drawString(this.font, perTx, guiLeft + guiW - 120, y + 14, ShopColors.TEXT_FAINT, false);
                    }
                }

                // Remove button area
                String removeStr = "§c✕";
                int removeX = guiLeft + guiW - 30;
                graphics.drawString(this.font, removeStr, removeX, y + (rowH - 10) / 2, ShopColors.STATUS_DANGER, false);
            }

            // Scroll indicators
            ShopUiUtil.renderScrollIndicators(graphics, this.font, guiLeft + 8, contentY, guiW - 16, contentH, scroll, maxVisible, entries.size());
        }

        // Summary bar — aggregated money + barter costs
        int summaryY = guiTop + guiH - 40;
        graphics.fill(guiLeft + 8, summaryY, guiLeft + guiW - 8, summaryY + 1, ShopColors.BORDER_MUTED);

        // NBT warning: alert buyer that NBT items may have changed since adding to cart
        boolean hasNbtEntries = entries.stream().anyMatch(e -> e.nbtAware() && ShopUiUtil.hasNonDefaultNbt(e.itemId(), e.nbtJson()));
        if (hasNbtEntries) {
            graphics.drawString(this.font, "§e⚠ NBT items in cart — verify listings haven't changed before checkout",
                    guiLeft + 66, summaryY - 10, ShopColors.STATUS_WARNING, false);
        }

        // Cart verification warnings from server
        List<S2CVerifyCartResponsePacket.CartWarning> warnings = ShopClientState.getCartWarnings();
        if (!warnings.isEmpty()) {
            int warnY = summaryY - (hasNbtEntries ? 22 : 10);
            for (int wi = Math.min(warnings.size(), 3) - 1; wi >= 0; wi--) {
                S2CVerifyCartResponsePacket.CartWarning w = warnings.get(wi);
                String warnText = "§c⚠ #" + (w.cartLineIndex() + 1) + ": " + w.detail();
                graphics.drawString(this.font, this.font.plainSubstrByWidth(warnText, guiW - 80),
                        guiLeft + 66, warnY, ShopColors.STATUS_DANGER, false);
                warnY -= 12;
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

        PlayerShopCartState.CartSummary cartSummary = PlayerShopCartState.buildSummary();
        StringBuilder sb = new StringBuilder();
        sb.append(cartSummary.itemCount()).append(cartSummary.itemCount() == 1 ? " item" : " items").append("  •  Total: ");

        boolean hasMoney = cartSummary.moneyTotal() > 0;
        boolean hasBarter = !cartSummary.barterTotals().isEmpty();
        if (hasMoney) {
            sb.append("§a").append(ShopUiUtil.formatMinorUnits(cartSummary.moneyTotal()));
        }
        if (hasMoney && hasBarter) {
            sb.append(" §f+ ");
        }
        if (hasBarter) {
            sb.append("§dBarter");
        }
        if (!hasMoney && !hasBarter) {
            sb.append("§7—");
        }
        String summary = sb.toString();
        graphics.drawString(this.font, summary, guiLeft + 66, summaryY + 6, ShopColors.TEXT_STRONG, false);

        super.render(graphics, mouseX, mouseY, partialTick);

        // LGB#18: Render tooltip after everything
        if (hoveredItemId != null) {
            ShopUiUtil.renderItemTooltip(graphics, this.font, hoveredItemId,
                    hoveredNbtJson != null ? hoveredNbtJson : "", mouseX, mouseY);
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

        for (int i = 0; i < maxVisible && i + scroll < entries.size(); i++) {
            int idx = i + scroll;
            PlayerShopCartState.CartEntry entry = entries.get(idx);
            int y = contentY + i * rowH;

            // Remove button click
            int removeX = guiLeft + guiW - 30;
            if (mouseX >= removeX - 4 && mouseX <= removeX + 14 && mouseY >= y + 4 && mouseY <= y + rowH - 6) {
                PlayerShopCartState.remove(idx);
                return true;
            }

            // LGB#4: Toggle payment click for BOTH-mode entries (on the badge/toggle area)
            if ("BOTH".equalsIgnoreCase(entry.tradeMode())) {
                int badgeY = rowH >= 36 ? y + 24 : y + 14;
                if (mouseX >= guiLeft + 36 && mouseX <= guiLeft + guiW / 2 && mouseY >= badgeY - 2 && mouseY <= badgeY + 12) {
                    PlayerShopCartState.togglePayment(idx);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
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
        ShopPackets.CHANNEL.sendToServer(new C2SVerifyCartPacket(lines));
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
            ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopBuyPacket(
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

