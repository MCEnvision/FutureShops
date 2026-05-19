package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.PlayerShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.PlayerShopListingData;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SPlayerShopBuyPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Player shop barter confirmation — half-and-half layout:
 * left = what you receive, right = what you give, arrow between,
 * bottom row: qty controls LEFT | receive summary CENTER | confirm RIGHT.
 */
public class PlayerShopBarterScreen extends Screen implements ShopScreenMarker {
    private final Screen parent;
    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private int quantity = 1;
    private final int initialQuantity; // LGB#6
    private Button confirmButton;
    private EditBox qtyBox;
    // LGB#19: Tooltip tracking for item hover
    private String hoveredItemId = null;
    private String hoveredNbtJson = null;

    // Spec §8: Confirmation modal overlay
    private ConfirmationModal confirmationModal = null;

    public PlayerShopBarterScreen(Screen parent) {
        this(parent, 1);
    }

    // LGB#6: Accept initial quantity from parent screen
    public PlayerShopBarterScreen(Screen parent, int initialQuantity) {
        super(Component.translatable("gui.futureshops.player_shop_barter.title"));
        this.parent = parent;
        this.initialQuantity = Math.max(1, initialQuantity);
    }

    @Override
    protected void init() {
        guiW = Math.max(280, this.width - 4);
        guiH = Math.max(180, this.height - 4);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;

        // LGB#6: Initialize quantity from parent
        quantity = Math.max(1, Math.min(resolveMaxQuantity(), initialQuantity));

        // Back button top-left
        addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_barter.back"), button -> onClose())
                .bounds(guiLeft + 6, guiTop + 6, 44, 14)
                .build());

        // ═══ Bottom row: Qty controls left | receive summary center | Confirm right ═══
        int bottomY = guiTop + guiH - 24;

        // Qty controls — left side of bottom row
        int qtyX = guiLeft + 10;
        addRenderableWidget(Button.builder(Component.literal("-"), button -> setQuantity(quantity - 1))
                .bounds(qtyX, bottomY, 16, 16)
                .build());
        qtyBox = new EditBox(this.font, qtyX + 18, bottomY, 32, 16, Component.translatable("gui.futureshops.player_shop_barter.qty_placeholder"));
        qtyBox.setValue(Integer.toString(quantity));
        qtyBox.setMaxLength(4);
        qtyBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        // Don't overwrite the field mid-type (that's what made the box feel broken
        // when resolveMaxQuantity was momentarily 0/1). Only update backing state.
        qtyBox.setResponder(value -> {
            if (value.isBlank()) {
                quantity = 1;
                return;
            }
            try {
                int parsed = Integer.parseInt(value);
                quantity = Math.max(1, Math.min(Math.max(1, resolveMaxQuantity()), parsed));
            } catch (NumberFormatException ignored) { /* keep last good */ }
        });
        addRenderableWidget(qtyBox);
        addRenderableWidget(Button.builder(Component.literal("+"), button -> {
                    if (hasShiftDown()) setQuantity(resolveMaxQuantity());
                    else setQuantity(quantity + 1);
                })
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_barter.tooltip.shift_max")))
                .bounds(qtyX + 52, bottomY, 16, 16)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_barter.max"), button -> setQuantity(resolveMaxQuantity()))
                .bounds(qtyX + 70, bottomY, 28, 16)
                .build());

        // Confirm button — right side
        confirmButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.futureshops.barter.confirm"), button -> showBarterConfirmation())
                .bounds(guiLeft + guiW - 90, bottomY, 80, 16)
                .build());
    }

    private void setQuantity(int value) {
        quantity = Math.max(1, Math.min(resolveMaxQuantity(), value));
        if (qtyBox != null) qtyBox.setValue(Integer.toString(quantity));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // LGB#19: Reset tooltip tracking
        hoveredItemId = null;
        hoveredNbtJson = null;

        PlayerShopListingData listing = PlayerShopClientState.selectedListing();

        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + 2, ShopColors.ACCENT_PRIMARY);

        graphics.drawCenteredString(this.font, this.title, guiLeft + guiW / 2, guiTop + 10, ShopColors.TEXT_STRONG);

        if (listing == null) {
            graphics.drawCenteredString(this.font, "No barter listing selected.",
                    guiLeft + guiW / 2, guiTop + guiH / 2, ShopColors.TEXT_MUTED);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        int contentY = guiTop + 26;
        int contentH = guiH - 58;
        int halfW = (guiW - 40) / 2;

        // ═══ Left: You Receive ═══
        renderReceivePanel(graphics, guiLeft + 10, contentY, halfW, contentH, listing, mouseX, mouseY);

        // ═══ Arrow in between ═══
        int arrowX = guiLeft + guiW / 2;
        int arrowY = contentY + contentH / 2;
        graphics.drawCenteredString(this.font, "§6⟵", arrowX, arrowY - 6, ShopColors.ACCENT_CURRENCY);
        graphics.drawCenteredString(this.font, "§9⟶", arrowX, arrowY + 6, ShopColors.TEXT_BARTER_SOFT);

        // ═══ Right: You Give ═══
        renderGivePanel(graphics, guiLeft + guiW - halfW - 10, contentY, halfW, contentH, listing, mouseX, mouseY);

        // ═══ Receive summary at same height as bottom controls ═══
        int bottomY = guiTop + guiH - 24;
        int totalOutput = quantity;
        String totalText = "Receive " + totalOutput + "x "
                + this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayNameWithQty(listing.itemId(), listing.baseQuantity()), 100);
        // Center it between qty controls and confirm button
        int textX = guiLeft + 110;
        int textW = guiLeft + guiW - 94 - textX;
        graphics.drawCenteredString(this.font, totalText, textX + textW / 2, bottomY + 4, ShopColors.TEXT_MUTED);

        // Confirm button active state
        String barterId = listing.barterItemId();
        int needed = listing.barterItemCount() * quantity;
        int owned = (barterId != null && !barterId.isBlank())
                ? ShopUiUtil.countPlayerInventoryNbt(barterId, listing.barterNbtJson(), listing.barterNbtAware())
                : 0;
        confirmButton.active = listing.stock() >= quantity && owned >= needed;

        super.render(graphics, mouseX, mouseY, partialTick);

        // LGB#19: Render tooltip after everything
        if (hoveredItemId != null) {
            ShopUiUtil.renderItemTooltip(graphics, this.font, hoveredItemId,
                    hoveredNbtJson != null ? hoveredNbtJson : "", mouseX, mouseY);
        }

        // Spec §8: Render confirmation modal on top of everything
        if (confirmationModal != null) {
            confirmationModal.render(graphics, this.font, this.width, this.height, mouseX, mouseY);
            if (confirmationModal.shouldAutoDismiss()) {
                confirmationModal = null;
            }
        }
    }

    private void renderReceivePanel(GuiGraphics graphics, int x, int y, int w, int h, PlayerShopListingData listing, int mouseX, int mouseY) {
        ShopUiUtil.renderCard(graphics, x, y, w, h);
        graphics.fill(x, y, x + w, y + 2, ShopColors.STATUS_SUCCESS);
        graphics.drawString(this.font, "§l§aYOU RECEIVE", x + 8, y + 6, ShopColors.STATUS_SUCCESS, false);

        // NBT-aware large preview — center in available space above text stack
        int textStackH = 42; // name + qty + stock (3 lines × 14px)
        int previewAreaH = h - 22 - textStackH - 4; // between header and text
        boolean hasRealNbt = listing.nbtAware() && ShopUiUtil.hasNonDefaultNbt(listing.itemId(), listing.nbtJson());
        if (previewAreaH > 20) {
            if (hasRealNbt) {
                ShopUiUtil.renderLargeItemPreviewWithNbt(graphics, this.font, listing.itemId(), listing.nbtJson(), x + 4, y + 22, w - 8);
            } else {
                ShopUiUtil.renderLargeItemPreview(graphics, this.font, listing.itemId(), x + 4, y + 22, w - 8);
            }
        }

        // LGB#19: Detect hover on item preview for tooltip
        if (mouseX >= x + 4 && mouseX <= x + w - 4 && mouseY >= y + 22 && mouseY <= y + 22 + Math.min(60, previewAreaH)) {
            hoveredItemId = listing.itemId();
            hoveredNbtJson = hasRealNbt ? listing.nbtJson() : "";
        }

        // Text stack at bottom — name, ×qty, stock
        int stackY = y + h - textStackH;
        String name = this.font.plainSubstrByWidth(
                ShopUiUtil.getItemDisplayNameWithNbtAndQty(listing.itemId(), listing.nbtJson(), listing.baseQuantity()), w - 16);
        graphics.drawCenteredString(this.font, name, x + w / 2, stackY, ShopColors.TEXT_STRONG);

        graphics.drawCenteredString(this.font, "§a×" + quantity, x + w / 2, stackY + 14, ShopColors.STATUS_SUCCESS);

        // LGB#7: Show stock number under receive quantity
        String stockText = listing.stock() > 999 ? "∞ stock" : listing.stock() + " in stock";
        graphics.drawCenteredString(this.font, "§7" + stockText, x + w / 2, stackY + 28,
                listing.stock() > 0 ? ShopColors.TEXT_FAINT : ShopColors.STATUS_DANGER);
    }

    private void renderGivePanel(GuiGraphics graphics, int x, int y, int w, int h, PlayerShopListingData listing, int mouseX, int mouseY) {
        ShopUiUtil.renderCard(graphics, x, y, w, h);
        graphics.fill(x, y, x + w, y + 2, ShopColors.TEXT_BARTER_SOFT);
        graphics.drawString(this.font, "§l§9YOU GIVE", x + 8, y + 6, ShopColors.TEXT_BARTER_SOFT, false);

        String barterId = listing.barterItemId();
        if (barterId == null || barterId.isBlank()) {
            graphics.drawCenteredString(this.font, "§cNot configured",
                    x + w / 2, y + h / 2, ShopColors.STATUS_DANGER);
            return;
        }

        int needed = listing.barterItemCount() * quantity;
        int owned = ShopUiUtil.countPlayerInventoryNbt(barterId, listing.barterNbtJson(), listing.barterNbtAware());
        boolean canPay = owned >= needed;

        // Text stack: name, base-strikethrough, ×needed, have (4 lines max × 14px = 56px)
        boolean hasBaseDiscount = listing.baseBarterItemCount() > listing.barterItemCount();
        int textLines = hasBaseDiscount ? 4 : 3;
        int textStackH = textLines * 14;

        // Item preview in available space — NBT-aware so enchanted / tagged barter items
        // render with their correct visual (enchantment glint, etc.).
        int previewAreaH = h - 22 - textStackH - 4;
        boolean hasRealBarterNbt = listing.barterNbtAware()
                && ShopUiUtil.hasNonDefaultNbt(barterId, listing.barterNbtJson());
        if (previewAreaH > 20) {
            if (hasRealBarterNbt) {
                ShopUiUtil.renderLargeItemPreviewWithNbt(graphics, this.font, barterId, listing.barterNbtJson(), x + 4, y + 22, w - 8);
            } else {
                ShopUiUtil.renderLargeItemPreview(graphics, this.font, barterId, x + 4, y + 22, w - 8);
            }
        }

        // LGB#19: Detect hover on barter item preview for tooltip (pass NBT so the
        // tooltip shows enchantments / custom name when present).
        if (mouseX >= x + 4 && mouseX <= x + w - 4 && mouseY >= y + 22 && mouseY <= y + 22 + Math.min(60, previewAreaH)) {
            hoveredItemId = barterId;
            hoveredNbtJson = hasRealBarterNbt ? listing.barterNbtJson() : "";
        }

        // Text stack at bottom
        int stackY = y + h - textStackH;
        String iName = this.font.plainSubstrByWidth(
                hasRealBarterNbt
                        ? ShopUiUtil.getItemDisplayNameWithNbt(barterId, listing.barterNbtJson())
                        : ShopUiUtil.getItemDisplayName(barterId),
                w - 16);
        graphics.drawCenteredString(this.font, iName, x + w / 2, stackY, ShopColors.TEXT_STRONG);
        stackY += 14;

        // LGB#1: Show base barter rate (strikethrough) when promo discounts it
        if (hasBaseDiscount) {
            String baseNeeded = "§7§m×" + (listing.baseBarterItemCount() * quantity);
            graphics.drawCenteredString(this.font, baseNeeded, x + w / 2, stackY, ShopColors.TEXT_FAINT);
            stackY += 14;
        }

        graphics.drawCenteredString(this.font, "§9×" + needed, x + w / 2, stackY, ShopColors.TEXT_BARTER_SOFT);
        stackY += 14;

        String ownedStr = "Have " + owned;
        graphics.drawCenteredString(this.font, ownedStr, x + w / 2, stackY,
                canPay ? ShopColors.STATUS_SUCCESS : ShopColors.STATUS_DANGER);
    }

    private void confirm() {
        // Fix: when trade mode is BOTH, the server previously defaulted to MONEY
        // whenever the buyer could afford it, making the Barter button silently
        // deduct coins instead of bartering. Explicitly signal BARTER here.
        // Server ignores paymentMethod for MONEY-only (enforced MONEY) and
        // MONEY_AND_BARTER (compound takes both) modes, so "BARTER" is a safe
        // default to send from this screen in every trade mode.
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        String mode = listing != null ? listing.tradeMode() : "";
        // Send the explicit mode tag for compound so the server-side BOTH-mode
        // guard (which rejects blank paymentMethod for BOTH listings) can tell
        // a genuine compound intent apart from a missing/legacy field.
        String paymentMethod = "MONEY_AND_BARTER".equalsIgnoreCase(mode) ? "MONEY_AND_BARTER" : "BARTER";
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopBuyPacket(
                PlayerShopClientState.shopPos(),
                PlayerShopClientState.selectedListingIndex(),
                quantity,
                paymentMethod));
    }

    private void showBarterConfirmation() {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        if (listing == null) return;
        String receiveName = ShopUiUtil.getItemDisplayNameWithNbtAndQty(listing.itemId(), listing.nbtJson(), listing.baseQuantity());
        String barterId = listing.barterItemId();
        int needed = listing.barterItemCount() * quantity;
        boolean hasRealBarterNbt = listing.barterNbtAware()
                && ShopUiUtil.hasNonDefaultNbt(barterId != null ? barterId : "", listing.barterNbtJson());
        String giveName;
        if (barterId == null) {
            giveName = "???";
        } else if (hasRealBarterNbt) {
            giveName = ShopUiUtil.getItemDisplayNameWithNbt(barterId, listing.barterNbtJson());
        } else {
            giveName = ShopUiUtil.getItemDisplayName(barterId);
        }

        // MONEY_AND_BARTER (compound): server will also withdraw coins on top of the
        // barter items, so surface the money cost here too — otherwise buyers get
        // silently charged money from the "barter" screen.
        boolean compound = "MONEY_AND_BARTER".equalsIgnoreCase(listing.tradeMode());
        java.util.List<ConfirmationModal.SummaryLine> summary = new java.util.ArrayList<>();
        // Order + colors mirror the on-screen arrows: §6⟵ (orange "give") above §9⟶ (blue "receive").
        summary.add(ConfirmationModal.SummaryLine.item(
                barterId != null ? barterId : "",
                "§6Give: §f" + giveName + " ×" + needed,
                hasRealBarterNbt ? listing.barterNbtJson() : ""));
        summary.add(ConfirmationModal.SummaryLine.item(
                listing.itemId(),
                "§9Receive: §f" + receiveName + " ×" + quantity,
                listing.nbtJson()));

        String totalText;
        if (compound) {
            long money = Math.max(0L, listing.effectiveUnitPriceMinor()) * quantity;
            String moneyStr = money <= 0 ? "Free"
                    : ShopUiUtil.formatMinorUnits(money) + " " + com.enviouse.futureshops.client.ShopClientState.getCurrencyName();
            // Unified compound total format — "Total: §a$X §f+ §9N× item" — matches cart row
            // + cart summary bar + buy confirmation, so buyers see one consistent rendering.
            totalText = "Total: §a" + moneyStr + " §f+ §9" + needed + "× " + giveName;
        } else {
            totalText = "Barter " + quantity + "× trade(s)";
        }

        confirmationModal = new ConfirmationModal(
                "Confirm Barter",
                summary,
                totalText,
                modal -> {
                    modal.setProcessing();
                    confirm();
                },
                () -> confirmationModal = null
        );
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

    /**
     * Smart max: inventory of barter item / cost per item, capped at stock.
     * No hard 64 cap — excess drops on floor.
     * Returns the real achievable quantity (may be 0 if stock is empty or the player can't afford any).
     * The display field is clamped to ≥1 by {@link #setQuantity(int)} separately.
     */
    private int resolveMaxQuantity() {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        if (listing == null) return 1;
        int stock = Math.max(0, listing.stock());
        String barterId = listing.barterItemId();
        int barterCost = listing.barterItemCount();
        if (barterId == null || barterId.isBlank() || barterCost <= 0) {
            return stock;
        }
        int affordable = ShopUiUtil.countPlayerInventoryNbt(barterId, listing.barterNbtJson(), listing.barterNbtAware()) / barterCost;
        return Math.min(stock, affordable);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (confirmationModal != null) {
            return confirmationModal.mouseClicked(mouseX, mouseY, button, this.font);
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

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
