package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.PlayerShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.PlayerShopListingData;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SPlayerShopSellPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Visitor sell-to-shop confirmation screen.
 * Mirrors PlayerShopBarterScreen layout but simplified for a one-sided "Sell N units".
 */
public class PlayerShopSellScreen extends Screen implements ShopScreenMarker {
    private final Screen parent;
    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private int quantity = 1;
    private final int initialQuantity;
    private EditBox qtyBox;
    // Flat Nocturne buttons: per-frame click zones populated by ShopUiUtil.button in render().
    private final java.util.List<ShopUiUtil.ClickZone> clickZones = new java.util.ArrayList<>();

    public PlayerShopSellScreen(Screen parent) {
        this(parent, 1);
    }

    public PlayerShopSellScreen(Screen parent, int initialQuantity) {
        super(Component.translatable("gui.futureshops.player_shop_block.sell.title", PlayerShopClientState.shopName()));
        this.parent = parent;
        this.initialQuantity = Math.max(1, initialQuantity);
    }

    @Override
    protected void init() {
        guiW = Math.max(260, Math.min(360, this.width - 4));
        guiH = Math.max(160, Math.min(220, this.height - 4));
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;

        quantity = Math.max(1, Math.min(resolveMaxQuantity(), initialQuantity));

        // The Cancel / ± / Max / Confirm buttons are flat Nocturne primitives drawn immediate-mode
        // in render(); only the qty box stays a real EditBox widget.
        int bottomY = guiTop + guiH - 24;
        int qtyX = guiLeft + 10;
        qtyBox = new EditBox(this.font, qtyX + 18, bottomY, 36, 16,
                Component.translatable("gui.futureshops.player_shop_block.sell.qty"));
        qtyBox.setValue(Integer.toString(quantity));
        qtyBox.setMaxLength(4);
        qtyBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        qtyBox.setResponder(value -> {
            if (value.isBlank()) { quantity = 1; return; }
            try {
                int parsed = Integer.parseInt(value);
                quantity = Math.max(1, Math.min(Math.max(1, resolveMaxQuantity()), parsed));
            } catch (NumberFormatException ignored) { }
        });
        addRenderableWidget(qtyBox);
    }

    private void setQuantity(int value) {
        quantity = Math.max(1, Math.min(Math.max(1, resolveMaxQuantity()), value));
        if (qtyBox != null) qtyBox.setValue(Integer.toString(quantity));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();

        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + 2, ShopColors.ACCENT_CURRENCY);

        graphics.drawCenteredString(this.font, this.title, guiLeft + guiW / 2, guiTop + 10, ShopColors.TEXT_STRONG);

        // Cancel button top-left — always available, even when no listing is selected.
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 6, guiTop + 6, 50, 14,
                Component.translatable("gui.futureshops.player_shop_block.sell.cancel"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, this::onClose);

        if (listing == null) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.player_shop_block.sell.no_listing"),
                    guiLeft + guiW / 2, guiTop + guiH / 2, ShopColors.TEXT_MUTED);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        // Item preview — NBT-aware so BEWLR items (TacZ guns etc.) keep their model
        int previewY = guiTop + 28;
        ShopUiUtil.renderLargeItemPreviewWithNbt(graphics, this.font, listing.itemId(), listing.nbtJson(),
                guiLeft + (guiW - 80) / 2, previewY, 80);

        // Item name — NBT-aware: tag-dependent items (TacZ guns) take their name from the tag
        String name = ShopUiUtil.getItemDisplayNameWithNbt(listing.itemId(), listing.nbtJson());
        graphics.drawCenteredString(this.font, name, guiLeft + guiW / 2, previewY + 64, ShopColors.TEXT_STRONG);

        // "Shop pays X each"
        String unitPriceStr = ShopUiUtil.formatMinorUnits(listing.buybackPriceMinor());
        graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.player_shop_block.sell.pays_each", unitPriceStr),
                guiLeft + guiW / 2, previewY + 76, ShopColors.TEXT_SECONDARY);

        // Cap line
        int cap = listing.buybackCap();
        String capStr = cap == 0 ? "∞" : (listing.buybackRemaining() + "/" + cap);
        graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.player_shop_block.sell.cap", capStr),
                guiLeft + guiW / 2, previewY + 88, ShopColors.TEXT_SECONDARY);

        // Total
        long total = listing.buybackPriceMinor() * (long) quantity;
        String totalStr = ShopUiUtil.formatMinorUnits(total);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.futureshops.player_shop_block.sell.total", totalStr),
                guiLeft + guiW / 2, previewY + 102, ShopColors.STATUS_SUCCESS);

        // Owned line — counted the way the server's NBT-strict sell path matches
        int owned = ShopUiUtil.countPlayerInventoryNbt(listing.itemId(), listing.nbtJson(), listing.nbtAware());
        graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.player_shop_block.sell.you_have", owned),
                guiLeft + guiW / 2, previewY + 114, ShopColors.TEXT_MUTED);

        // ═══ Bottom-row flat buttons: [−] qty [+] [Max] … [Confirm] ═══
        int bottomY = guiTop + guiH - 24;
        int qtyX = guiLeft + 10;
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                qtyX, bottomY, 16, 16, null, ShopUiUtil.ButtonStyle.SECONDARY, true,
                "-", null, () -> setQuantity(quantity - 1));
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                qtyX + 56, bottomY, 16, 16, null, ShopUiUtil.ButtonStyle.SECONDARY, true,
                "+", null, () -> {
                    if (hasShiftDown()) setQuantity(resolveMaxQuantity());
                    else setQuantity(quantity + 1);
                });
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                qtyX + 74, bottomY, 28, 16, Component.translatable("gui.futureshops.player_shop_block.sell.max"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, () -> setQuantity(resolveMaxQuantity()));

        int max = resolveMaxQuantity();
        boolean canConfirm = max > 0 && quantity > 0 && quantity <= max
                && listing.buybackPriceMinor() > 0;
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 100, bottomY, 90, 16,
                Component.translatable("gui.futureshops.player_shop_block.sell.confirm"),
                ShopUiUtil.ButtonStyle.PRIMARY, canConfirm, this::confirm);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Flat Nocturne buttons: run the top-most hit ClickZone first.
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int resolveMaxQuantity() {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        if (listing == null) return 1;
        int owned = ShopUiUtil.countPlayerInventoryNbt(listing.itemId(), listing.nbtJson(), listing.nbtAware());
        int capRemaining = listing.buybackCap() == 0 ? 9999 : Math.max(0, listing.buybackRemaining());
        return Math.max(1, Math.min(owned > 0 ? owned : 1, capRemaining > 0 ? capRemaining : 1));
    }

    private void confirm() {
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopSellPacket(
                PlayerShopClientState.shopPos(),
                PlayerShopClientState.selectedListingIndex(),
                quantity));
        onClose();
    }

    @Override
    public void onClose() {
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        if (mc != null) mc.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
