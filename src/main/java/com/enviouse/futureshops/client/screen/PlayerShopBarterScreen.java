package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.PlayerShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SPlayerShopBuyPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PlayerShopBarterScreen extends Screen implements ShopScreenMarker {
    private final Screen parent;
    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private int quantity = 1;

    public PlayerShopBarterScreen(Screen parent) {
        super(Component.literal("Player Barter"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        guiW = Math.min(300, this.width - 16);
        guiH = Math.min(210, this.height - 16);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;

        addRenderableWidget(Button.builder(Component.literal("←"), button -> onClose())
                .bounds(guiLeft + 8, guiTop + 8, 18, 16)
                .build());
        addRenderableWidget(Button.builder(Component.literal("-"), button -> quantity = Math.max(1, quantity - 1))
                .bounds(guiLeft + 192, guiTop + guiH - 34, 14, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> quantity = Math.min(resolveMaxQuantity(), quantity + 1))
                .bounds(guiLeft + 244, guiTop + guiH - 34, 14, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Max"), button -> quantity = resolveMaxQuantity())
                .bounds(guiLeft + 262, guiTop + guiH - 34, 28, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Accept Trade"), button -> confirm())
                .bounds(guiLeft + 188, guiTop + guiH - 18, 102, 16)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, ShopColors.BG_PRIMARY);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.BG_PANEL);
        ShopUiUtil.drawBorder(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BTN_BARTER);

        graphics.drawCenteredString(this.font, "Player Barter", guiLeft + guiW / 2, guiTop + 10, ShopColors.TEXT_PRIMARY);

        int leftX = guiLeft + 10;
        int panelY = guiTop + 28;
        graphics.fill(leftX, panelY, leftX + 122, panelY + 142, ShopColors.BG_CARD);
        ShopUiUtil.drawBorder(graphics, leftX, panelY, 122, 142, ShopColors.BORDER_DEFAULT);
        ShopUiUtil.renderLargeItemPreview(graphics, this.font, PlayerShopClientState.listedItemId(), leftX, panelY + 4, 122);
        graphics.drawCenteredString(this.font,
                this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayName(PlayerShopClientState.listedItemId()), 112),
                leftX + 61, panelY + 90, ShopColors.TEXT_PRIMARY);
        graphics.drawCenteredString(this.font,
                "Stock: " + PlayerShopClientState.stock(), leftX + 61, panelY + 104, ShopColors.TEXT_SECONDARY);
        graphics.drawCenteredString(this.font,
                "Trades: " + quantity, leftX + 61, panelY + 118, ShopColors.TEXT_BARTER);

        int infoX = guiLeft + 144;
        graphics.drawString(this.font, "You Give", infoX, guiTop + 34, ShopColors.TEXT_BARTER, true);
        String costName = PlayerShopClientState.barterItemId().isBlank()
                ? "(unset)"
                : ShopUiUtil.getItemDisplayName(PlayerShopClientState.barterItemId());
        graphics.drawString(this.font,
                this.font.plainSubstrByWidth((PlayerShopClientState.barterItemCount() * quantity) + "x " + costName, guiW - 156),
                infoX, guiTop + 50, ShopColors.TEXT_PRIMARY, false);
        graphics.drawString(this.font,
                "Owned: " + ShopUiUtil.countPlayerInventory(PlayerShopClientState.barterItemId()),
                infoX, guiTop + 64, ShopColors.TEXT_SECONDARY, false);

        graphics.fill(infoX, guiTop + 82, guiLeft + guiW - 10, guiTop + 83, ShopColors.BORDER_DEFAULT);
        graphics.drawString(this.font, "You Receive", infoX, guiTop + 92, ShopColors.SUCCESS, true);
        graphics.drawString(this.font,
                this.font.plainSubstrByWidth(quantity + "x " + ShopUiUtil.getItemDisplayName(PlayerShopClientState.listedItemId()), guiW - 156),
                infoX, guiTop + 108, ShopColors.TEXT_PRIMARY, false);
        graphics.drawString(this.font,
                "Quantity controls are below.", infoX, guiTop + 126, ShopColors.TEXT_SECONDARY, false);

        graphics.drawCenteredString(this.font, Integer.toString(quantity), guiLeft + 225, guiTop + guiH - 32, ShopColors.TEXT_PRIMARY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void confirm() {
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopBuyPacket(PlayerShopClientState.shopPos(), quantity));
    }

    private int resolveMaxQuantity() {
        return Math.max(1, Math.min(64, PlayerShopClientState.stock()));
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

