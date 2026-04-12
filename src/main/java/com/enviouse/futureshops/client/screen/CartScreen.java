package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.CatalogItem;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SBuyRequestPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
    private Button checkoutButton;

    public CartScreen(Screen parent) {
        super(Component.translatable("gui.futureshops.cart.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        guiW = Math.min(520, Math.max(360, this.width - 20));
        guiH = Math.min(340, Math.max(250, this.height - 20));
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;
        visibleRows = Math.max(4, (guiH - 140) / 28);

        addRenderableWidget(Button.builder(Component.literal("§7← Back"), button -> onClose())
                .bounds(guiLeft + 10, guiTop + guiH - 24, 48, 18)
                .build());
        addRenderableWidget(Button.builder(Component.literal("§cClear"), button -> ShopClientState.clearCart())
                .bounds(guiLeft + 64, guiTop + guiH - 24, 48, 18)
                .build());
        checkoutButton = addRenderableWidget(Button.builder(Component.literal("§a$ Checkout"), button -> sendCheckout())
                .bounds(guiLeft + guiW - 100, guiTop + guiH - 24, 90, 18)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, ShopColors.BG_PRIMARY);
        ShopUiUtil.renderAccentPanel(graphics, guiLeft, guiTop, guiW, guiH,
                ShopColors.BG_PANEL, ShopColors.BORDER_DEFAULT, ShopColors.ACCENT_CYAN);
        renderHeader(graphics);
        renderRows(graphics);
        renderSummary(graphics);
        ShopUiUtil.renderStatusPanel(graphics, this.font, guiLeft + 10, Math.max(6, guiTop - 24), guiW - 20);
        checkoutButton.active = !ShopClientState.getCartEntries().isEmpty();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics) {
        int hx = guiLeft + 8;
        int hy = guiTop + 8;
        int hw = guiW - 16;
        ShopUiUtil.renderAccentPanel(graphics, hx, hy, hw, 34, ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT, ShopColors.ACCENT_PURPLE);
        graphics.drawString(this.font, "§l" + this.title.getString(), hx + 10, hy + 6, ShopColors.TEXT_PRIMARY, false);
        graphics.drawString(this.font, "§7Review every cart line before checkout.", hx + 10, hy + 18, ShopColors.TEXT_SECONDARY, false);
    }

    private void renderRows(GuiGraphics graphics) {
        int listX = guiLeft + 10;
        int listY = guiTop + 52;
        int listW = guiW - 20;
        int listH = guiH - 120;
        ShopUiUtil.renderPanel(graphics, listX, listY, listW, listH, ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT);

        List<ShopClientState.CartEntry> entries = ShopClientState.getCartEntries();
        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.cart.empty"), listX + listW / 2, listY + listH / 2 - 8, ShopColors.TEXT_SECONDARY);
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.cart.empty_hint"), listX + listW / 2, listY + listH / 2 + 6, ShopColors.TEXT_SECONDARY);
            return;
        }

        int maxScroll = Math.max(0, entries.size() - visibleRows);
        scrollIndex = Math.max(0, Math.min(scrollIndex, maxScroll));
        int rowY = listY + 8;
        for (int row = 0; row < visibleRows && row + scrollIndex < entries.size(); row++) {
            ShopClientState.CartEntry entry = entries.get(row + scrollIndex);
            CatalogItem item = ShopClientState.getCatalogItem(entry.itemId()).orElse(null);
            if (item == null) continue;
            int y = rowY + row * 28;
            int rowBg = row % 2 == 0 ? ShopColors.BG_PANEL : ShopColors.BG_CARD_HOVER;
            ShopUiUtil.renderPanel(graphics, listX + 6, y, listW - 12, 22, rowBg, ShopColors.BORDER_DEFAULT);
            ShopUiUtil.renderItemIcon(graphics, this.font, item.itemId(), listX + 10, y + 3);

            // Name — truncated
            String name = this.font.plainSubstrByWidth(item.displayName(), listW - 240);
            graphics.drawString(this.font, name, listX + 30, y + 7, ShopColors.TEXT_PRIMARY, false);

            // Quantity controls
            int ctrlX = listX + listW - 130;
            graphics.drawString(this.font, "§7-", ctrlX, y + 7, ShopColors.TEXT_SECONDARY, false);
            graphics.drawString(this.font, "§f" + entry.quantity(), ctrlX + 14, y + 7, ShopColors.TEXT_PRIMARY, false);
            graphics.drawString(this.font, "§7+", ctrlX + 28, y + 7, ShopColors.TEXT_SECONDARY, false);

            // Price
            long unitPrice = item.hasPromo() ? item.promoPrice() : item.buyPrice();
            String priceStr = this.font.plainSubstrByWidth(ShopUiUtil.formatMinorUnits(unitPrice * entry.quantity()), 60);
            graphics.drawString(this.font, "§a" + priceStr, listX + listW - 86, y + 7, ShopColors.TEXT_PRICE, false);

            // Remove
            graphics.drawString(this.font, "§c✕", listX + listW - 22, y + 7, ShopColors.ERROR, false);
        }
    }

    private void renderSummary(GuiGraphics graphics) {
        int x = guiLeft + 10;
        int y = guiTop + guiH - 56;
        int w = guiW - 20;
        ShopUiUtil.renderAccentPanel(graphics, x, y, w, 24, ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT, ShopColors.ACCENT_GOLD);
        // Item count — truncated
        String items = this.font.plainSubstrByWidth(ShopClientState.getCartTotalQuantity() + " items in cart", w / 2 - 10);
        graphics.drawString(this.font, items, x + 10, y + 8, ShopColors.TEXT_SECONDARY, false);
        // Total
        String total = "§6Total: §a" + ShopUiUtil.formatMinorUnits(ShopClientState.getCartTotalMinorUnits());
        String clipped = this.font.plainSubstrByWidth(total, w / 2);
        graphics.drawString(this.font, clipped, x + w - this.font.width(clipped) - 10, y + 8, ShopColors.TEXT_PRICE, false);
    }

    private void sendCheckout() {
        List<C2SBuyRequestPacket.LineItem> lines = ShopClientState.getCartEntries().stream()
                .map(entry -> new C2SBuyRequestPacket.LineItem(entry.itemId(), entry.quantity()))
                .toList();
        if (!lines.isEmpty()) {
            ShopPackets.CHANNEL.sendToServer(C2SBuyRequestPacket.cart(ShopClientState.getActiveShopId(), lines));
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
        int listX = guiLeft + 10;
        int rowY = guiTop + 60;
        int listW = guiW - 20;
        List<ShopClientState.CartEntry> entries = ShopClientState.getCartEntries();
        for (int row = 0; row < visibleRows && row + scrollIndex < entries.size(); row++) {
            ShopClientState.CartEntry entry = entries.get(row + scrollIndex);
            int y = rowY + row * 28;
            int ctrlX = listX + listW - 130;
            // Minus
            if (mouseX >= ctrlX && mouseX <= ctrlX + 10 && mouseY >= y && mouseY <= y + 22) {
                ShopClientState.setCartQuantity(entry.itemId(), entry.quantity() - 1);
                return true;
            }
            // Plus
            if (mouseX >= ctrlX + 24 && mouseX <= ctrlX + 38 && mouseY >= y && mouseY <= y + 22) {
                ShopClientState.setCartQuantity(entry.itemId(), entry.quantity() + 1);
                return true;
            }
            // Remove
            if (mouseX >= listX + listW - 24 && mouseX <= listX + listW - 10 && mouseY >= y && mouseY <= y + 22) {
                ShopClientState.removeFromCart(entry.itemId());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
