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

/** Cart review screen scaffolded from spec §7. */
public class CartScreen extends Screen implements ShopScreenMarker {
    private static final int DEFAULT_GUI_W = 280;
    private static final int DEFAULT_GUI_H = 200;
    private static final int ROW_H = 24;

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
        guiW = Math.min(DEFAULT_GUI_W, this.width - 16);
        guiH = Math.min(DEFAULT_GUI_H, this.height - 16);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;
        visibleRows = Math.max(3, Math.min(7, (guiH - 92) / ROW_H));

        addRenderableWidget(Button.builder(Component.literal("←"), button -> onClose())
                .bounds(guiLeft + 6, guiTop + 6, 18, 16)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.cart.clear"), button -> ShopClientState.clearCart())
                .bounds(guiLeft + guiW - 58, guiTop + 6, 52, 16)
                .build());

        checkoutButton = Button.builder(Component.translatable("gui.futureshops.cart.checkout"), button -> sendCheckout())
                .bounds(guiLeft + guiW - 86, guiTop + guiH - 24, 80, 18)
                .build();
        addRenderableWidget(checkoutButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x88000000);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.BG_PANEL);
        ShopUiUtil.drawBorder(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_DEFAULT);

        graphics.drawCenteredString(this.font, this.title, guiLeft + guiW / 2, guiTop + 10, ShopColors.TEXT_PRIMARY);
        renderCartRows(graphics);
        renderSummary(graphics);
        ShopUiUtil.renderStatusPanel(graphics, this.font, guiLeft, Math.max(4, guiTop - 22), guiW);

        checkoutButton.active = !ShopClientState.getCartEntries().isEmpty();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCartRows(GuiGraphics graphics) {
        List<ShopClientState.CartEntry> entries = ShopClientState.getCartEntries();
        int listX = guiLeft + 6;
        int listY = guiTop + 28;
        int listW = guiW - 12;

        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.cart.empty"),
                    guiLeft + guiW / 2, guiTop + 80, ShopColors.TEXT_SECONDARY);
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.cart.empty_hint"),
                    guiLeft + guiW / 2, guiTop + 94, ShopColors.TEXT_SECONDARY);
            return;
        }

        int maxScroll = Math.max(0, entries.size() - visibleRows);
        scrollIndex = Math.max(0, Math.min(scrollIndex, maxScroll));

        for (int row = 0; row < visibleRows && row + scrollIndex < entries.size(); row++) {
            ShopClientState.CartEntry entry = entries.get(row + scrollIndex);
            CatalogItem item = ShopClientState.getCatalogItem(entry.itemId()).orElse(null);
            if (item == null) {
                continue;
            }

            int y = listY + row * ROW_H;
            int bg = row % 2 == 0 ? ShopColors.BG_CARD : ShopColors.BG_PANEL;
            graphics.fill(listX, y, listX + listW, y + ROW_H - 2, bg);
            ShopUiUtil.drawBorder(graphics, listX, y, listW, ROW_H - 2, ShopColors.BORDER_DEFAULT);

            ShopUiUtil.renderItemIcon(graphics, this.font, item.itemId(), listX + 4, y + 4);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(item.displayName(), 86), listX + 24, y + 5,
                    ShopColors.TEXT_PRIMARY, false);

            int qtyX = listX + 120;
            graphics.drawString(this.font, "-", qtyX, y + 6, ShopColors.TEXT_SECONDARY, false);
            graphics.drawCenteredString(this.font, Integer.toString(entry.quantity()), qtyX + 18, y + 6, ShopColors.TEXT_PRIMARY);
            graphics.drawString(this.font, "+", qtyX + 30, y + 6, ShopColors.TEXT_SECONDARY, false);

            long unitPrice = item.hasPromo() ? item.promoPrice() : item.buyPrice();
            graphics.drawString(this.font, ShopUiUtil.formatMinorUnits(unitPrice * entry.quantity()), listX + 176, y + 6,
                    ShopColors.TEXT_PRICE, false);
            graphics.drawString(this.font, "×", listX + listW - 14, y + 6, ShopColors.ERROR, false);
        }
    }

    private void renderSummary(GuiGraphics graphics) {
        int summaryY = guiTop + guiH - 50;
        graphics.fill(guiLeft + 6, summaryY, guiLeft + guiW - 6, summaryY + 20, ShopColors.BG_CARD);
        graphics.fill(guiLeft + 6, summaryY, guiLeft + guiW - 6, summaryY + 1, ShopColors.BORDER_ACCENT);
        graphics.drawString(this.font,
                Component.translatable("gui.futureshops.cart.items", ShopClientState.getCartTotalQuantity()),
                guiLeft + 12, summaryY + 6, ShopColors.TEXT_SECONDARY, false);
        graphics.drawString(this.font,
                Component.translatable("gui.futureshops.cart.total", ShopUiUtil.formatMinorUnits(ShopClientState.getCartTotalMinorUnits())),
                guiLeft + 130, summaryY + 6, ShopColors.TEXT_PRICE, true);
    }

    private void sendCheckout() {
        List<C2SBuyRequestPacket.LineItem> lines = ShopClientState.getCartEntries().stream()
                .map(entry -> new C2SBuyRequestPacket.LineItem(entry.itemId(), entry.quantity()))
                .toList();
        if (lines.isEmpty()) {
            return;
        }

        ShopPackets.CHANNEL.sendToServer(C2SBuyRequestPacket.cart(ShopClientState.getActiveShopId(), lines));
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
        int listX = guiLeft + 6;
        int listY = guiTop + 28;
        int listW = guiW - 12;
        List<ShopClientState.CartEntry> entries = ShopClientState.getCartEntries();

        for (int row = 0; row < visibleRows && row + scrollIndex < entries.size(); row++) {
            ShopClientState.CartEntry entry = entries.get(row + scrollIndex);
            int y = listY + row * ROW_H;
            if (mouseX < listX || mouseX > listX + listW || mouseY < y || mouseY > y + ROW_H - 2) {
                continue;
            }

            int qtyX = listX + 120;
            if (mouseX >= qtyX && mouseX <= qtyX + 8) {
                ShopClientState.setCartQuantity(entry.itemId(), entry.quantity() - 1);
                return true;
            }
            if (mouseX >= qtyX + 28 && mouseX <= qtyX + 36) {
                ShopClientState.setCartQuantity(entry.itemId(), entry.quantity() + 1);
                return true;
            }
            if (mouseX >= listX + listW - 14 && mouseX <= listX + listW - 6) {
                ShopClientState.removeFromCart(entry.itemId());
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}




