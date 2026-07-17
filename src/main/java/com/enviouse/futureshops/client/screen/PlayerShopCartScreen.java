package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.PlayerShopCartState;
import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SPlayerShopActionPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopBuyPacket;
import com.enviouse.futureshops.network.packets.C2SVerifyCartPacket;
import com.enviouse.futureshops.network.packets.S2CVerifyCartResponsePacket;
import net.minecraft.client.gui.GuiGraphics;
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
    private String hoverTooltipText = null;
    private int hoverTooltipX = 0;
    private int hoverTooltipY = 0;
    private boolean awaitingVerification = false;
    private static final int SCROLLBAR_RESERVE = 16;
    private static final int REMOVE_BTN_W = 18;

    // Flat Nocturne buttons: draw + hit-region come from the same ShopUiUtil.button/zone calls,
    // registered here each frame and consulted in mouseClicked via dispatchClicks.
    private final java.util.List<ShopUiUtil.ClickZone> clickZones = new java.util.ArrayList<>();

    public PlayerShopCartScreen(Screen parent) {
        super(Component.translatable("gui.futureshops.player_shop_cart.title_raw"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Right-side drawer, matching the server-shop CartScreen.
        guiW = Math.min(340, this.width - 8);
        guiH = Math.max(200, this.height - 20);
        guiLeft = this.width - guiW - 8;
        guiTop = (this.height - guiH) / 2;
        // Close / Clear / Checkout are drawn immediate-mode in render(); no widgets to register here.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();
        hoveredItemId = null;
        hoveredNbtJson = null;
        hoverTooltipText = null;

        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        ShopUiUtil.renderAccentLine(graphics, guiLeft + 2, guiTop, guiW - 4);

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
            // Reserve a taller footer for the summary + warning lines + button row (see below).
            int contentH = guiH - 114;
            int maxVisible = Math.max(1, contentH / rowH);
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

                // Item icon — always apply the wire NBT for display (nbtAware only
                // governs transaction matching; TacZ guns etc. need the tag to render)
                int iconX = panelLeft + 6;
                int iconY = y + (rowH - 18) / 2;
                ShopUiUtil.renderItemIconWithNbt(graphics, this.font, entry.itemId(), entry.nbtJson(), iconX, iconY);

                // LGB#18: Detect hover over item icon for tooltip
                if (mouseX >= iconX && mouseX <= iconX + 16 && mouseY >= iconY && mouseY <= iconY + 16) {
                    hoveredItemId = entry.itemId();
                    hoveredNbtJson = entry.nbtJson();
                }

                // Reserve right-side columns (remove X + price column) and clip name/shop so
                // long names never slide underneath the price or the X button.
                int removeX = panelRight - REMOVE_BTN_W;
                int priceColRight = removeX - 6;
                int priceColLeft = priceColRight - 126; // 126px reserved for price/barter text
                int leftContentX = iconX + 22;
                // Clip the name BEFORE the inline qty triplet (which occupies priceColLeft-64 ..
                // priceColLeft-4, mirrored below), not merely before the price column — otherwise a
                // long name slides under the [-][N][+] controls in the narrowed drawer.
                int leftContentMax = priceColLeft - 64 - 6;
                int leftContentW = Math.max(40, leftContentMax - leftContentX);

                // Row body (icon + name area) → reopen the source shop at this listing. Registered
                // FIRST so the qty steppers / remove / BOTH-badge zones (added later) win on overlap.
                final PlayerShopCartState.CartEntry rowEntry = entry;
                int rowBodyRight = priceColLeft - 6;
                ShopUiUtil.zone(clickZones, iconX, y, rowBodyRight - iconX, rowH - 2, true,
                        () -> ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopActionPacket(
                                rowEntry.shopPos(), "VISIT", rowEntry.listingIndex(), 0)));

                // LGB#15: Item name with inline base quantity (e.g. "Stick ×6")
                String itemName = ShopUiUtil.getItemDisplayNameWithNbtAndQty(entry.itemId(), entry.nbtJson(), entry.baseQuantity());
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
                    modeBadge = Component.translatable(chosenBarter
                            ? "gui.futureshops.player_shop_cart.mode.both_barter"
                            : "gui.futureshops.player_shop_cart.mode.both_money").getString();
                    modeTooltip = Component.translatable("gui.futureshops.player_shop_cart.mode.both_tooltip").getString();
                } else if (isCompound) {
                    modeBadge = Component.translatable("gui.futureshops.player_shop_cart.mode.compound").getString();
                    modeTooltip = Component.translatable("gui.futureshops.player_shop_cart.mode.compound_tooltip").getString();
                } else {
                    boolean barter = "BARTER".equalsIgnoreCase(entry.tradeMode());
                    modeBadge = Component.translatable(barter
                            ? "gui.futureshops.player_shop_cart.mode.barter"
                            : "gui.futureshops.player_shop_cart.mode.money").getString();
                    modeTooltip = Component.translatable(barter
                            ? "gui.futureshops.trade_mode.barter.tooltip"
                            : "gui.futureshops.trade_mode.money.tooltip").getString();
                }
                int badgeX = leftContentX;
                int badgeY = rowH >= 36 ? y + 24 : y + 14;
                if (rowH < 36) {
                    badgeX = leftContentX + this.font.width(shopLabel) + 6;
                    // Clamp so the badge's right edge never reaches the qty column when the shop
                    // name is long (leftContentMax is the left-content boundary).
                    badgeX = Math.min(badgeX, leftContentMax - this.font.width(modeBadge));
                }
                graphics.drawString(this.font, modeBadge, badgeX, badgeY, ShopColors.TEXT_MUTED, false);
                int badgeW = this.font.width(modeBadge);
                if (mouseX >= badgeX && mouseX <= badgeX + badgeW && mouseY >= badgeY - 1 && mouseY <= badgeY + 10) {
                    hoverTooltipText = modeTooltip;
                    hoverTooltipX = mouseX;
                    hoverTooltipY = mouseY;
                }
                // BOTH-mode: the mode badge doubles as a money/barter payment toggle. Register its
                // (wide) clickable band AFTER the row-body zone so it wins there; the qty steppers
                // (registered later still) win over it in their overlap, matching the old ordering.
                if (isBoth) {
                    int toggleIdx = idx;
                    ShopUiUtil.zone(clickZones, panelLeft + 28, badgeY - 2,
                            (panelRight - 140) - (panelLeft + 28), 14, true,
                            () -> PlayerShopCartState.togglePayment(toggleIdx));
                }

                // LGB#18: NBT badge — only when it fits inside the reserved left content
                // area. Unlike the icon (always NBT for display), the badge keeps the
                // nbtAware gate: it flags listings that MATCH by NBT.
                boolean hasRealNbt = entry.nbtAware() && ShopUiUtil.hasNonDefaultNbt(entry.itemId(), entry.nbtJson());
                if (hasRealNbt) {
                    String nbtBadge = Component.translatable("gui.futureshops.player_shop_block.rail.badge_nbt").getString();
                    int nbtBadgeX = badgeX + badgeW + 4;
                    int nbtBadgeW = Math.max(28, this.font.width(nbtBadge) + 10);
                    if (nbtBadgeX + nbtBadgeW <= leftContentMax) {
                        ShopUiUtil.drawChip(graphics, this.font, nbtBadgeX, badgeY - 1, nbtBadge,
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
                final PlayerShopCartState.CartEntry qtyEntry = entry;
                final int qtyIdx = idx;
                ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                        qtyMinusX, qtyBtnY, qtyBtnSize, qtyBtnSize, Component.literal("-"),
                        ShopUiUtil.ButtonStyle.SECONDARY, true,
                        () -> PlayerShopCartState.setQuantity(qtyIdx,
                                qtyEntry.quantity() - (Screen.hasShiftDown() ? 10 : 1)));
                ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                        qtyPlusX, qtyBtnY, qtyBtnSize, qtyBtnSize, Component.literal("+"),
                        ShopUiUtil.ButtonStyle.SECONDARY, true,
                        () -> PlayerShopCartState.setQuantity(qtyIdx,
                                Math.min(999, qtyEntry.quantity() + (Screen.hasShiftDown() ? 10 : 1))));
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
                    int totalBarter = entry.barterItemCount() * entry.quantity();
                    String barterName = ShopUiUtil.getItemDisplayNameWithNbt(entry.barterItemId(), entry.barterNbtJson());
                    String combined = "§a" + moneyStr + "§f + §9" + totalBarter + "× " + barterName;
                    if (this.font.width(combined) > priceColW) {
                        int prefixW = this.font.width("§a" + moneyStr + "§f + §9" + totalBarter + "× ");
                        String trimmedName = this.font.plainSubstrByWidth(barterName, Math.max(10, priceColW - prefixW));
                        combined = "§a" + moneyStr + "§f + §9" + totalBarter + "× " + trimmedName;
                    }
                    int combinedW = this.font.width(combined);
                    graphics.drawString(this.font, combined, priceColLeft + (priceColW - combinedW) / 2, centerY, ShopColors.TEXT_STRONG, false);
                } else if (showBarter) {
                    int totalBarter = entry.barterItemCount() * entry.quantity();
                    String barterStr = totalBarter + "× " +
                            this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayNameWithNbt(entry.barterItemId(), entry.barterNbtJson()), priceColW - 30);
                    int barterW = this.font.width(barterStr);
                    graphics.drawString(this.font, barterStr, priceColLeft + (priceColW - barterW) / 2, centerY, ShopColors.TEXT_BARTER_SOFT, false);
                } else {
                    String priceStr = ShopUiUtil.formatMinorUnits(entry.totalPrice());
                    int priceW = this.font.width(priceStr);
                    graphics.drawString(this.font, priceStr, priceColLeft + (priceColW - priceW) / 2, centerY, ShopColors.TEXT_CURRENCY, false);
                }

                // Remove — flat DANGER "✕" button, inside the panel but outside the scrollbar reserve.
                int rmY = y + (rowH - 2 - 14) / 2;
                ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                        removeX, rmY, REMOVE_BTN_W, 14, Component.literal("✕"),
                        ShopUiUtil.ButtonStyle.DANGER, true, () -> PlayerShopCartState.remove(qtyIdx));
            }

            // Scroll indicators
            ShopUiUtil.renderScrollIndicators(graphics, this.font, guiLeft + 8, contentY, guiW - 16, contentH, scroll, maxVisible, entries.size());
        }

        // Summary bar — "N items · Total X$ · Barter Included" (Barter phrase in blue).
        int summaryY = guiTop + guiH - 82;
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
            sb.append(" §8· ").append(Component.translatable("gui.futureshops.player_shop_cart.summary.barter_included").getString());
        }
        String summary = sb.toString();
        int summaryMaxW = guiW - 150;
        if (this.font.width(summary) > summaryMaxW) {
            summary = this.font.plainSubstrByWidth(summary, summaryMaxW - 6) + "§8…";
        }
        // Suppress the summary while the centered "Verifying…" indicator occupies this same band.
        if (!awaitingVerification) {
            graphics.drawString(this.font, summary, guiLeft + 66, summaryY + 6, ShopColors.TEXT_STRONG, false);
        }

        // NBT + verification warnings stack below the summary. Each line is clipped to the space
        // ABOVE the button row (maxWarnY) so it can never render on top of the Clear/Checkout
        // buttons regardless of how many warnings there are.
        int maxWarnY = guiTop + guiH - 34; // last line's top; a 10px line then ends at guiH-24, above buttons (guiH-22)
        boolean hasNbtEntries = entries.stream().anyMatch(e -> e.nbtAware() && ShopUiUtil.hasNonDefaultNbt(e.itemId(), e.nbtJson()));
        int warnY = summaryY + 18;
        if (hasNbtEntries && warnY <= maxWarnY) {
            graphics.drawString(this.font, Component.translatable("gui.futureshops.player_shop_cart.nbt_warning"),
                    guiLeft + 66, warnY, ShopColors.STATUS_WARNING, false);
            warnY += 10;
        }

        // Cart verification warnings from server — stack under the NBT warning, clipped to fit.
        List<S2CVerifyCartResponsePacket.CartWarning> warnings = ShopClientState.getCartWarnings();
        if (!warnings.isEmpty()) {
            int shown = 0;
            for (int wi = 0; wi < Math.min(warnings.size(), 3) && warnY <= maxWarnY; wi++) {
                S2CVerifyCartResponsePacket.CartWarning w = warnings.get(wi);
                // Localized on the client from the warning code + args (server sends no English).
                String warnText = ShopUiUtil.cartWarningLine(w.cartLineIndex(), w.warningCode(), w.detail()).getString();
                graphics.drawString(this.font, this.font.plainSubstrByWidth(warnText, guiW - 80),
                        guiLeft + 66, warnY, ShopColors.STATUS_DANGER, false);
                warnY += 10;
                shown++;
            }
            if (warnings.size() > shown && warnY <= maxWarnY) {
                graphics.drawString(this.font, Component.translatable("gui.futureshops.player_shop_cart.more_warnings", warnings.size() - shown),
                        guiLeft + 66, warnY, ShopColors.STATUS_DANGER, false);
            }
        }

        // Awaiting verification indicator
        if (awaitingVerification) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.player_shop_cart.verifying"),
                    guiLeft + guiW / 2, summaryY + 6, ShopColors.STATUS_WARNING);
        }

        // Footer buttons — flat Nocturne primitives at their former bounds.
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 24, guiTop + 6, 18, 14,
                Component.literal("✕"), ShopUiUtil.ButtonStyle.SECONDARY, true, this::onClose);
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 8, guiTop + guiH - 22, 50, 16,
                Component.translatable("gui.futureshops.player_shop_cart.clear"), ShopUiUtil.ButtonStyle.DANGER, true, () -> {
                    PlayerShopCartState.clear();
                    ShopClientState.clearCartVerification();
                });
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 100, guiTop + guiH - 22, 92, 16,
                Component.translatable("gui.futureshops.player_shop_cart.checkout"), ShopUiUtil.ButtonStyle.PRIMARY,
                !entries.isEmpty(), this::requestVerifyAndCheckout);

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
        // Flat Nocturne zones (footer + per-row visit/steppers/remove/payment-toggle): run the
        // top-most hit zone. Registration order (visit → badge → steppers) gives steppers priority.
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
            return true;
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
                        e.nbtAware(), e.tradeMode(),
                        e.nbtJson() == null ? "" : e.nbtJson()))
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

