package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.CatalogBarterIngredient;
import com.enviouse.futureshops.data.CatalogBarterRecipe;
import com.enviouse.futureshops.data.CatalogItem;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SBuyRequestPacket;
import com.enviouse.futureshops.network.packets.C2SInventorySyncPacket;
import com.enviouse.futureshops.network.packets.C2SSellRequestPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Item detail view — redesigned with bottom-aligned controls and modern layout.
 */
public class ItemDetailScreen extends Screen implements ShopScreenMarker {
    private static final int DEFAULT_GUI_W = 340;
    private static final int DEFAULT_GUI_H = 260;
    private static final int PREVIEW_W = 130;

    private final Screen parent;
    /** Catalog resolution key (listingId) this detail view is bound to — NOT the registry itemId. */
    private final String listingId;

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;

    private EditBox quantityBox;

    /** Per-frame flat-button hit regions (see ShopUiUtil.button / dispatchClicks). */
    private final java.util.List<ShopUiUtil.ClickZone> clickZones = new ArrayList<>();
    /** Deferred hover tooltip for a flat button, rendered on top after super.render(). */
    private Component pendingButtonTooltip;

    // Item 6: advanced tooltip tracking
    private boolean showItemTooltip = false;

    // Spec §8: Confirmation modal overlay
    private ConfirmationModal confirmationModal = null;
    private java.util.UUID pendingSellRequestId;

    public ItemDetailScreen(Screen parent, String listingId) {
        super(Component.translatable("gui.futureshops.detail.title"));
        this.parent = parent;
        this.listingId = listingId;
    }

    @Override
    protected void init() {
        // Centered dialog (Nocturne item-detail): a compact box over the dimmed ground, capped so
        // it reads as a modal rather than a full-screen takeover. Layout supports down to 280 wide.
        guiW = Math.min(Math.max(300, this.width - 20), 380);
        guiH = Math.min(Math.max(220, this.height - 20), 320);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;
        int actualPreviewW = Math.min(PREVIEW_W, guiW / 2 - 10);

        ShopPackets.CHANNEL.sendToServer(new C2SInventorySyncPacket(ShopClientState.getActiveShopId()));

        // ═══ Quantity input box — flat +/-/Max steppers are drawn in render() ═══
        int previewCenterX = guiLeft + 8 + PREVIEW_W / 2;
        // Preview panel bottom = guiTop + guiH - 32. Stack: controls → Total → "Quantity"
        int qtyY = guiTop + guiH - 32 - 42; // controls Y

        quantityBox = new EditBox(this.font, previewCenterX - 30, qtyY, 36, 14,
                Component.translatable("gui.futureshops.detail.quantity"));
        quantityBox.setValue("1");
        quantityBox.setMaxLength(4);
        // Only allow digits — prevents junk input without fighting the user mid-type.
        quantityBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        // Responder is intentionally empty: getQuantity() clamps on demand when an
        // action fires. Writing a clamped value back during typing erases the caret
        // and was perceived as "field not accepting input" when the client catalog
        // hadn't loaded yet (resolveMaxQuantity() returns 1 → every keystroke reset).
        quantityBox.setResponder(value -> { /* no-op; clamp on use */ });
        addRenderableWidget(quantityBox);
    }

    /** Buy confirmation modal — mirrors the former buyButton.onPress. */
    private void openBuyConfirm(CatalogItem item) {
        if (item == null) return;
        long effectivePrice = item.hasPromo() ? item.promoPrice() : item.buyPrice();
        int qty = getQuantity();
        String totalStr = ShopUiUtil.formatMinorUnits(effectivePrice * qty);
        confirmationModal = new ConfirmationModal(
                I18n.get("gui.futureshops.item_detail.confirm_buy_title"),
                java.util.List.of(
                        ConfirmationModal.SummaryLine.item(item.itemId(), item.displayName() + " ×" + qty, item.nbtJson())
                ),
                I18n.get("gui.futureshops.item_detail.total_cost", totalStr, ShopClientState.getCurrencyName()),
                (modal, paymentSource) -> {
                    modal.setProcessing();
                    ShopPackets.CHANNEL.sendToServer(C2SBuyRequestPacket.single(
                            ShopClientState.getActiveShopId(), item.listingId(), qty, paymentSource));
                },
                () -> confirmationModal = null
        );
    }

    /** Sell confirmation modal — mirrors the former sellButton.onPress. */
    private void openSellConfirm(CatalogItem item) {
        if (item == null) return;
        int qty = getQuantity();
        String totalStr = ShopUiUtil.formatMinorUnits(item.sellPrice() * qty);
        confirmationModal = new ConfirmationModal(
                I18n.get("gui.futureshops.item_detail.confirm_sell_title"),
                java.util.List.of(
                        ConfirmationModal.SummaryLine.item(item.itemId(),
                                I18n.get("gui.futureshops.item_detail.sell_summary", item.displayName(), qty),
                                item.nbtJson())
                ),
                I18n.get("gui.futureshops.item_detail.earn", totalStr, ShopClientState.getCurrencyName()),
                modal -> {
                    modal.setProcessing();
                    pendingSellRequestId = java.util.UUID.randomUUID();
                    ShopPackets.CHANNEL.sendToServer(new C2SSellRequestPacket(
                            ShopClientState.getActiveShopId(), item.listingId(),
                            qty, pendingSellRequestId));
                },
                () -> confirmationModal = null
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();
        pendingButtonTooltip = null;
        CatalogItem item = currentItem();
        if (item == null) {
            onClose();
            return;
        }

        showItemTooltip = false;

        // Dimmed background
        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        // Main panel — elevated neon-glass
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        ShopUiUtil.renderAccentLine(graphics, guiLeft + 2, guiTop, guiW - 4);

        renderPreviewPanel(graphics, item, mouseX, mouseY);
        renderInfoPanel(graphics, item);
        renderBottomArea(graphics, item);
        ShopUiUtil.renderStatusPanel(graphics, this.font, guiLeft, Math.max(4, guiTop - 22), guiW);
        renderActionButtons(graphics, item, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);

        // Item 6: Render advanced tooltip after everything
        if (showItemTooltip) {
            ShopUiUtil.renderItemTooltip(graphics, this.font, item.itemId(),
                    item.nbtJson() != null ? item.nbtJson() : "", mouseX, mouseY);
        }
        if (pendingButtonTooltip != null && confirmationModal == null) {
            graphics.renderTooltip(this.font, pendingButtonTooltip, mouseX, mouseY);
        }

        // Spec §8: Render confirmation modal on top of everything
        if (confirmationModal != null) {
            confirmationModal.render(graphics, this.font, this.width, this.height, mouseX, mouseY);
            if (confirmationModal.shouldAutoDismiss()) {
                confirmationModal = null;
            }
        }
    }

    private void renderPreviewPanel(GuiGraphics graphics, CatalogItem item, int mouseX, int mouseY) {
        int leftX = guiLeft + 8;
        int panelY = guiTop + 24;
        int panelH = guiH - 56;

        ShopUiUtil.renderCard(graphics, leftX, panelY, PREVIEW_W, panelH);
        graphics.fill(leftX, panelY, leftX + PREVIEW_W, panelY + 2, ShopColors.ACCENT_PRIMARY);

        ShopUiUtil.renderLargeItemPreviewWithNbt(graphics, this.font, item.itemId(),
                item.nbtJson() != null ? item.nbtJson() : "", leftX, panelY + 8, PREVIEW_W);

        // Item 6: Detect hover over the large item preview area for tooltip
        int iconCenterX = leftX + PREVIEW_W / 2;
        int iconY = panelY + 18; // approximate preview center
        if (mouseX >= iconCenterX - 24 && mouseX <= iconCenterX + 24 && mouseY >= iconY && mouseY <= iconY + 48) {
            showItemTooltip = true;
        }

        // Name — wrapped (moved up to leave room at scale 4 where guiH is small)
        String name = this.font.plainSubstrByWidth(item.displayName(), PREVIEW_W - 10);
        graphics.drawCenteredString(this.font, name, leftX + PREVIEW_W / 2, panelY + 66, ShopColors.TEXT_STRONG);

        // Owned count (moved up to clear qty controls row)
        String ownedStr = Component.translatable("gui.futureshops.detail.you_own", ShopUiUtil.countPlayerInventoryNbt(item.itemId(), item.nbtJson(), true)).getString();
        graphics.drawCenteredString(this.font,
                this.font.plainSubstrByWidth(ownedStr, PREVIEW_W - 10),
                leftX + PREVIEW_W / 2, panelY + 80, ShopColors.TEXT_MUTED);

        // Total cost — currency amber, right below the qty controls
        long effectiveBuyPrice = item.hasPromo() ? item.promoPrice() : item.buyPrice();
        String total = Component.translatable("gui.futureshops.cart.total_line",
                ShopUiUtil.formatMinorUnits(effectiveBuyPrice * getQuantity())).getString();
        graphics.drawCenteredString(this.font, total, leftX + PREVIEW_W / 2, panelY + panelH - 26, ShopColors.TEXT_CURRENCY);

        // Quantity label — below Total
        graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.item_detail.quantity_label"),
                leftX + PREVIEW_W / 2, panelY + panelH - 14, ShopColors.TEXT_FAINT);
    }

    private void renderInfoPanel(GuiGraphics graphics, CatalogItem item) {
        int infoX = guiLeft + PREVIEW_W + 18;
        int infoY = guiTop + 24;
        int infoW = guiW - PREVIEW_W - 26;
        int infoH = guiH - 56;
        long effectiveBuyPrice = item.hasPromo() ? item.promoPrice() : item.buyPrice();
        int promoPercent = ShopUiUtil.computePromoPercent(item.buyPrice(), item.promoPrice());

        ShopUiUtil.renderElevatedCard(graphics, infoX, infoY, infoW, infoH);

        int contentX = infoX + 8;
        int contentW = infoW - 16;

        // Title — scaled, truncated
        graphics.pose().pushPose();
        graphics.pose().translate(contentX, infoY + 8, 0);
        graphics.pose().scale(1.2f, 1.2f, 1f);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(item.displayName(), (int) (contentW / 1.2f)), 0, 0, ShopColors.TEXT_STRONG, true);
        graphics.pose().popPose();

        // Description placeholder — wrapped
        ShopUiUtil.drawWrappedString(graphics, this.font,
                Component.translatable("gui.futureshops.detail.no_description"),
                contentX, infoY + 24, contentW, ShopColors.TEXT_MUTED, 10);

        int nextY = infoY + 38;

        // Divider
        graphics.fill(contentX, nextY, contentX + contentW, nextY + 1, ShopColors.BORDER_MUTED);
        nextY += 6;

        // Buy price — with inline "(-X%)" suffix when promo is active.
        // We deliberately skip the animated discount badge in the info panel: the price
        // line is the pricing source of truth, and a duplicate badge next to it was
        // visually noisy.  The suffix is plain §7 text so it reads as secondary info.
        String buyStr = effectiveBuyPrice <= 0
                ? Component.translatable("gui.futureshops.player_shop_block.confirm.free").getString()
                : ShopUiUtil.formatMinorUnits(effectiveBuyPrice);
        if (item.hasPromo() && promoPercent > 0 && effectiveBuyPrice > 0) {
            buyStr = buyStr + " §7(-" + promoPercent + "%)";
        }
        drawInfoLine(graphics, Component.translatable("gui.futureshops.item_detail.buy_label").getString(), buyStr, contentX, contentW, nextY, ShopColors.TEXT_CURRENCY);
        nextY += 12;

        // Sell price
        String sellStr = item.sellPrice() > 0L ? ShopUiUtil.formatMinorUnits(item.sellPrice()) : "—";
        drawInfoLine(graphics, Component.translatable("gui.futureshops.item_detail.sell_label").getString(), sellStr, contentX, contentW, nextY,
                item.sellPrice() > 0L ? ShopColors.TEXT_CURRENCY : ShopColors.TEXT_FAINT);
        nextY += 12;

        // Stock
        String stockLabel = item.unlimited()
                ? Component.translatable("gui.futureshops.item_detail.stock_unlimited").getString()
                : (item.stock() > 0
                        ? Component.translatable("gui.futureshops.player_shop_block.detail.single.stock_in", item.stock()).getString()
                        : Component.translatable("gui.futureshops.player_shop_block.detail.single.stock_out").getString());
        graphics.drawString(this.font, this.font.plainSubstrByWidth(stockLabel, contentW), contentX, nextY, ShopColors.TEXT_MUTED, false);
        nextY += 14;

        // Barter preview
        if (item.hasBarterRecipes()) {
            ShopUiUtil.renderPill(graphics, this.font, contentX, nextY,
                    Component.translatable("gui.futureshops.item_detail.barter_available").getString(),
                    ShopColors.SURFACE_OVERLAY, ShopColors.TEXT_BARTER_SOFT, ShopColors.TEXT_BARTER_SOFT);
            nextY += 16;
            renderBarterPreview(graphics, item, contentX, contentW, nextY, infoY + infoH - 20);
        }
    }

    private void renderBottomArea(GuiGraphics graphics, CatalogItem item) {
        // Total is now rendered inside renderPreviewPanel — nothing else needed here
    }

    /** Flat Nocturne action row + quantity steppers (formerly vanilla Buttons). */
    private void renderActionButtons(GuiGraphics graphics, CatalogItem item, int mouseX, int mouseY) {
        // Top-right close (the dialog previously had no on-screen exit — only ESC).
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 18, guiTop + 4, 14, 14, Component.literal("✕"),
                ShopUiUtil.ButtonStyle.GHOST, true, this::onClose);

        int previewCenterX = guiLeft + 8 + PREVIEW_W / 2;
        int qtyY = guiTop + guiH - 32 - 42;

        // Quantity steppers: −  [box]  +  Max
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                previewCenterX - 48, qtyY, 14, 14, Component.literal("-"), ShopUiUtil.ButtonStyle.SECONDARY, true,
                () -> setQuantity(getQuantity() - 1));
        boolean plusHover = ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                previewCenterX + 10, qtyY, 14, 14, Component.literal("+"), ShopUiUtil.ButtonStyle.SECONDARY, true,
                () -> {
                    if (hasShiftDown()) setQuantity(resolveMaxQuantity());
                    else setQuantity(getQuantity() + 1);
                });
        if (plusHover) {
            pendingButtonTooltip = Component.translatable("gui.futureshops.cart.tooltip.shift_max");
        }
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                previewCenterX + 28, qtyY, 26, 14, Component.translatable("gui.futureshops.barter.max"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, () -> setQuantity(resolveMaxQuantity()));

        // Action row: [+ Cart] [Buy] [Sell] [Barter]
        int bottomY = guiTop + guiH - 22;
        int btnW = 52;
        int gap = 4;
        int totalBtnW = btnW * 4 + gap * 3;
        int startX = guiLeft + (guiW - totalBtnW) / 2;

        boolean buyEnabled = item.buyPrice() > 0L && (item.unlimited() || item.stock() > 0);
        // NBT-strict count: the server sell path only accepts the listing's exact tagged variant.
        // Fall back to the server-pushed owned count (blank-NBT listings only, since that count is
        // not NBT-aware) so the Sell button lights up right after a buy without waiting for the
        // client inventory to re-sync / reopening the screen.
        int owned = ShopUiUtil.countPlayerInventoryNbt(item.itemId(), item.nbtJson(), true);
        if (owned == 0 && (item.nbtJson() == null || item.nbtJson().isBlank())) {
            owned = ShopClientState.getOwnedCount(item.itemId());
        }
        boolean sellEnabled = item.sellPrice() > 0L && owned > 0;

        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                startX, bottomY, btnW, 14, Component.translatable("gui.futureshops.item_detail.add_cart"),
                ShopUiUtil.ButtonStyle.PRIMARY, buyEnabled,
                () -> { CatalogItem it = currentItem(); if (it != null) ShopClientState.addToCart(it.listingId(), getQuantity()); });
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                startX + btnW + gap, bottomY, btnW, 14, Component.translatable("gui.futureshops.item_detail.buy"),
                ShopUiUtil.ButtonStyle.PRIMARY, buyEnabled, () -> openBuyConfirm(currentItem()));
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                startX + (btnW + gap) * 2, bottomY, btnW, 14, Component.translatable("gui.futureshops.item_detail.sell"),
                ShopUiUtil.ButtonStyle.SECONDARY, sellEnabled, () -> openSellConfirm(currentItem()));
        // Barter only when the listing has recipes (former barterButton.visible gate).
        if (item.hasBarterRecipes()) {
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    startX + (btnW + gap) * 3, bottomY, btnW, 14, Component.translatable("gui.futureshops.item_detail.barter"),
                    ShopUiUtil.ButtonStyle.SECONDARY, true,
                    () -> { CatalogItem it = currentItem(); if (it != null) this.minecraft.setScreen(new BarterScreen(this, it.itemId())); });
        }
    }

    private void renderBarterPreview(GuiGraphics graphics, CatalogItem item, int contentX, int contentW, int startY, int maxY) {
        // Prefer the recipe whose resolved targetListingId rewards THIS listing (exact NBT
        // variant); fall back to the first registry-id match for legacy recipes.
        List<CatalogBarterRecipe> recipes = ShopClientState.getBarterRecipesForItem(item.itemId());
        CatalogBarterRecipe previewRecipe = recipes.stream()
                .filter(recipe -> item.listingId().equals(recipe.targetListingId()))
                .findFirst()
                .orElseGet(() -> recipes.stream().findFirst().orElse(null));
        if (previewRecipe == null) return;

        int nextY = startY;
        List<CatalogBarterIngredient> ingredients = previewRecipe.ingredients();
        int shown = 0;
        for (CatalogBarterIngredient ingredient : ingredients) {
            if (shown >= 3 || nextY > maxY) {
                graphics.drawString(this.font, "§7...", contentX, nextY, ShopColors.TEXT_SECONDARY, false);
                break;
            }
            // NBT-strict owned count / display name when the ingredient pins a tag — mirrors
            // the BarterScreen and what the server will accept.
            int owned = ShopUiUtil.countPlayerInventoryNbt(
                    ingredient.itemId(), ingredient.nbtJson(), !ingredient.nbtJson().isBlank());
            int needed = ingredient.count() * getQuantity();
            int color = owned >= needed ? ShopColors.SUCCESS : ShopColors.ERROR;
            String label = this.font.plainSubstrByWidth(
                    ShopUiUtil.getItemDisplayNameWithNbt(ingredient.itemId(), ingredient.nbtJson()) + " ×" + needed,
                    contentW - 50);
            graphics.drawString(this.font, label, contentX, nextY, ShopColors.TEXT_BARTER, false);
            String haveStr = this.font.plainSubstrByWidth(Component.translatable("gui.futureshops.item_detail.have", owned).getString(), 44);
            graphics.drawString(this.font, haveStr, contentX + contentW - 44, nextY, color, false);
            nextY += 10;
            shown++;
        }
    }

    private void drawInfoLine(GuiGraphics graphics, String label, String value, int x, int w, int y, int valueColor) {
        graphics.drawString(this.font, label, x, y, ShopColors.TEXT_SECONDARY, false);
        String clipped = this.font.plainSubstrByWidth(value, w - 40);
        graphics.drawString(this.font, clipped, x + Math.max(36, w - this.font.width(clipped) - 4), y, valueColor, false);
    }

    private CatalogItem currentItem() {
        return ShopClientState.getCatalogItem(listingId).orElse(null);
    }

    private int getQuantity() {
        try {
            return clampQuantity(Integer.parseInt(quantityBox.getValue()));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private void setQuantity(int quantity) {
        quantityBox.setValue(Integer.toString(clampQuantity(quantity)));
    }

    private int resolveMaxQuantity() {
        CatalogItem item = currentItem();
        if (item == null) return 1;

        // For selling: limited by how many the player has in inventory (NBT-strict, matching the server)
        int sellLimit = ShopUiUtil.countPlayerInventoryNbt(item.itemId(), item.nbtJson(), true);
        return PurchaseQuantityPolicy.serverShopMaximum(
                item.unlimited(), item.stock(), sellLimit);
    }

    private int clampQuantity(int quantity) {
        return Math.max(1, Math.min(resolveMaxQuantity(), quantity));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (confirmationModal != null) {
            return confirmationModal.mouseClicked(mouseX, mouseY, button, this.font);
        }
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmationModal != null) {
            if (confirmationModal.keyPressed(keyCode)) return true;
            return true; // block all keys while modal open
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Called by the client packet handler when a buy/sell response arrives.
     * Updates the confirmation modal state accordingly.
     */
    public void onTransactionResult(boolean success, String message) {
        if (confirmationModal != null) {
            if (success) {
                confirmationModal.setSuccess(message);
            } else {
                confirmationModal.setFailed(message);
            }
        }
    }

    public void onSellTransactionResult(
            java.util.UUID requestId,
            boolean success,
            String message
    ) {
        if (requestId == null || !requestId.equals(pendingSellRequestId)) {
            return;
        }
        pendingSellRequestId = null;
        onTransactionResult(success, message);
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
