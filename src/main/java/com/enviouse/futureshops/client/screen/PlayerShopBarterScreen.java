package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.PlayerShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.PlayerShopListingData;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SPlayerShopBuyPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
    private Button confirmButton;
    private EditBox qtyBox;

    public PlayerShopBarterScreen(Screen parent) {
        super(Component.literal("Barter Confirmation"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        guiW = Math.min(440, Math.max(320, this.width - 24));
        guiH = Math.min(260, Math.max(200, this.height - 24));
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;

        // Back button top-left
        addRenderableWidget(Button.builder(Component.literal("§7← Back"), button -> onClose())
                .bounds(guiLeft + 6, guiTop + 6, 44, 14)
                .build());

        // ═══ Bottom row: Qty LEFT | Confirm RIGHT ═══
        int bottomY = guiTop + guiH - 24;

        // Qty controls — left side
        int qtyX = guiLeft + 10;
        addRenderableWidget(Button.builder(Component.literal("-"), button -> setQuantity(quantity - 1))
                .bounds(qtyX, bottomY, 16, 16)
                .build());
        qtyBox = new EditBox(this.font, qtyX + 18, bottomY, 32, 16, Component.literal("Qty"));
        qtyBox.setValue("1");
        qtyBox.setMaxLength(4);
        qtyBox.setResponder(value -> {
            if (value.isBlank()) return;
            try {
                int parsed = Integer.parseInt(value);
                int clamped = Math.max(1, Math.min(resolveMaxQuantity(), parsed));
                if (clamped != parsed) qtyBox.setValue(Integer.toString(clamped));
                else quantity = clamped;
            } catch (NumberFormatException ignored) {
                qtyBox.setValue("1");
            }
        });
        addRenderableWidget(qtyBox);
        addRenderableWidget(Button.builder(Component.literal("+"), button -> setQuantity(quantity + 1))
                .bounds(qtyX + 52, bottomY, 16, 16)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Max"), button -> setQuantity(resolveMaxQuantity()))
                .bounds(qtyX + 70, bottomY, 28, 16)
                .build());

        // Confirm button — right side
        confirmButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.futureshops.barter.confirm"), button -> confirm())
                .bounds(guiLeft + guiW - 90, bottomY, 80, 16)
                .build());
    }

    private void setQuantity(int value) {
        quantity = Math.max(1, Math.min(resolveMaxQuantity(), value));
        if (qtyBox != null) qtyBox.setValue(Integer.toString(quantity));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();

        graphics.fill(0, 0, this.width, this.height, 0x88000000);
        ShopUiUtil.renderAccentPanel(graphics, guiLeft, guiTop, guiW, guiH,
                ShopColors.BG_PANEL, ShopColors.BORDER_DEFAULT, ShopColors.ACCENT_PURPLE);

        graphics.drawCenteredString(this.font, this.title, guiLeft + guiW / 2, guiTop + 10, ShopColors.TEXT_PRIMARY);

        if (listing == null) {
            graphics.drawCenteredString(this.font, "No barter listing selected.",
                    guiLeft + guiW / 2, guiTop + guiH / 2, ShopColors.TEXT_SECONDARY);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        int contentY = guiTop + 26;
        int contentH = guiH - 58;
        int halfW = (guiW - 40) / 2;

        // ═══ Left: You Receive ═══
        renderReceivePanel(graphics, guiLeft + 10, contentY, halfW, contentH, listing);

        // ═══ Arrow in between ═══
        int arrowX = guiLeft + guiW / 2;
        int arrowY = contentY + contentH / 2;
        graphics.drawCenteredString(this.font, "§6⟵", arrowX, arrowY - 6, ShopColors.ACCENT_GOLD);
        graphics.drawCenteredString(this.font, "§d⟶", arrowX, arrowY + 6, ShopColors.TEXT_BARTER);

        // ═══ Right: You Give ═══
        renderGivePanel(graphics, guiLeft + guiW - halfW - 10, contentY, halfW, contentH, listing);

        // ═══ Receive summary at same height as bottom controls ═══
        int bottomY = guiTop + guiH - 24;
        int totalOutput = quantity;
        String totalText = "Receive " + totalOutput + "x "
                + this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayName(listing.itemId()), 100);
        // Center it between qty controls and confirm button
        int textX = guiLeft + 110;
        int textW = guiLeft + guiW - 94 - textX;
        graphics.drawCenteredString(this.font, totalText, textX + textW / 2, bottomY + 4, ShopColors.TEXT_SECONDARY);

        // Confirm button active state
        String barterId = listing.barterItemId();
        int needed = listing.barterItemCount() * quantity;
        int owned = (barterId != null && !barterId.isBlank()) ? ShopUiUtil.countPlayerInventory(barterId) : 0;
        confirmButton.active = listing.stock() >= quantity && owned >= needed;

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderReceivePanel(GuiGraphics graphics, int x, int y, int w, int h, PlayerShopListingData listing) {
        ShopUiUtil.renderAccentPanel(graphics, x, y, w, h,
                ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT, ShopColors.SUCCESS);
        graphics.drawString(this.font, "§aYou Receive", x + 8, y + 6, ShopColors.SUCCESS, false);

        ShopUiUtil.renderLargeItemPreview(graphics, this.font, listing.itemId(), x + 4, y + 22, w - 8);

        String name = this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayName(listing.itemId()), w - 16);
        graphics.drawCenteredString(this.font, name, x + w / 2, y + h - 26, ShopColors.TEXT_PRIMARY);

        graphics.drawCenteredString(this.font, "§a×" + quantity, x + w / 2, y + h - 14, ShopColors.TEXT_PRICE);
    }

    private void renderGivePanel(GuiGraphics graphics, int x, int y, int w, int h, PlayerShopListingData listing) {
        ShopUiUtil.renderAccentPanel(graphics, x, y, w, h,
                ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT, ShopColors.TEXT_BARTER);
        graphics.drawString(this.font, "§dYou Give", x + 8, y + 6, ShopColors.TEXT_BARTER, false);

        String barterId = listing.barterItemId();
        if (barterId == null || barterId.isBlank()) {
            graphics.drawCenteredString(this.font, "§cNot configured",
                    x + w / 2, y + h / 2, ShopColors.ERROR);
            return;
        }

        int needed = listing.barterItemCount() * quantity;
        int owned = ShopUiUtil.countPlayerInventory(barterId);
        boolean canPay = owned >= needed;

        // Item preview
        ShopUiUtil.renderLargeItemPreview(graphics, this.font, barterId, x + 4, y + 22, w - 8);

        String iName = this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayName(barterId), w - 16);
        graphics.drawCenteredString(this.font, iName, x + w / 2, y + h - 38, ShopColors.TEXT_PRIMARY);

        graphics.drawCenteredString(this.font, "§d×" + needed, x + w / 2, y + h - 26, ShopColors.TEXT_BARTER);

        String ownedStr = "Have " + owned;
        graphics.drawCenteredString(this.font, ownedStr, x + w / 2, y + h - 14,
                canPay ? ShopColors.SUCCESS : ShopColors.ERROR);
    }

    private void confirm() {
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopBuyPacket(
                PlayerShopClientState.shopPos(),
                PlayerShopClientState.selectedListingIndex(),
                quantity));
    }

    /**
     * Smart max: inventory of barter item / cost per item, capped at stock.
     * No hard 64 cap — excess drops on floor.
     */
    private int resolveMaxQuantity() {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        if (listing == null) return 1;
        int stock = Math.max(1, listing.stock());
        String barterId = listing.barterItemId();
        int barterCost = listing.barterItemCount();
        if (barterId == null || barterId.isBlank() || barterCost <= 0) return Math.max(1, stock);
        int affordable = ShopUiUtil.countPlayerInventory(barterId) / barterCost;
        return Math.max(1, Math.min(stock, affordable));
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
