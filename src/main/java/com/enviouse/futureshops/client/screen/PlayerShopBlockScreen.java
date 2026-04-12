package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.PlayerShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SPlayerShopActionPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopBuyPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class PlayerShopBlockScreen extends Screen implements ShopScreenMarker {
    private static final int GUI_W = 266;
    private static final int GUI_H = 188;

    private int guiLeft;
    private int guiTop;

    public PlayerShopBlockScreen() {
        super(Component.literal("Player Shop"));
    }

    @Override
    protected void init() {
        guiLeft = (this.width - GUI_W) / 2;
        guiTop = (this.height - GUI_H) / 2;

        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(guiLeft + GUI_W - 58, guiTop + 8, 50, 14)
                .build());

        if (PlayerShopClientState.owner()) {
            addRenderableWidget(Button.builder(Component.literal("Set Item"), button -> sendAction("SET_LISTING_MAINHAND", 0))
                    .bounds(guiLeft + 8, guiTop + 94, 62, 14)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Clear"), button -> sendAction("CLEAR_LISTING", 0))
                    .bounds(guiLeft + 74, guiTop + 94, 48, 14)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Mode"), button -> sendAction("TOGGLE_MODE", 0))
                    .bounds(guiLeft + 126, guiTop + 94, 48, 14)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Promo"), button -> this.minecraft.setScreen(new PromoEditorModalScreen(this)))
                    .bounds(guiLeft + 178, guiTop + 94, 48, 14)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Claim"), button -> sendAction("CLAIM_SETTLEMENT", 0))
                    .bounds(guiLeft + 230, guiTop + 94, 28, 14)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("History"), button -> this.minecraft.setScreen(new SettlementHistoryScreen(this)))
                    .bounds(guiLeft + 176, guiTop + 130, 52, 14)
                    .build());

            addRenderableWidget(Button.builder(Component.literal("Price +"), button -> sendAction("PRICE_UP", 100))
                    .bounds(guiLeft + 8, guiTop + 112, 56, 14)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Price -"), button -> sendAction("PRICE_DOWN", 100))
                    .bounds(guiLeft + 68, guiTop + 112, 56, 14)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Barter=Hand"), button -> sendAction("SET_BARTER_MAINHAND", 1))
                    .bounds(guiLeft + 128, guiTop + 112, 70, 14)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Link Look"), button -> sendAction("LINK_LOOKING", 0))
                    .bounds(guiLeft + 202, guiTop + 112, 56, 14)
                    .build());

            addRenderableWidget(Button.builder(Component.literal("Unlink"), button -> sendAction("UNLINK", 0))
                    .bounds(guiLeft + 230, guiTop + 130, 28, 14)
                    .build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Buy x1"), button -> buy(1))
                    .bounds(guiLeft + 8, guiTop + 156, 80, 16)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Buy x8"), button -> buy(8))
                    .bounds(guiLeft + 92, guiTop + 156, 80, 16)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Buy x16"), button -> buy(16))
                    .bounds(guiLeft + 176, guiTop + 156, 82, 16)
                    .build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, ShopColors.BG_PRIMARY);
        graphics.fill(guiLeft, guiTop, guiLeft + GUI_W, guiTop + GUI_H, ShopColors.BG_PANEL);
        ShopUiUtil.drawBorder(graphics, guiLeft, guiTop, GUI_W, GUI_H, ShopColors.BORDER_DEFAULT);

        graphics.drawString(this.font, "Player Shop", guiLeft + 8, guiTop + 10, ShopColors.TEXT_PRIMARY, false);
        drawRow(graphics, "Owner", PlayerShopClientState.ownerName(), guiTop + 28, ShopColors.TEXT_SECONDARY);
        drawRow(graphics, "Item", PlayerShopClientState.listedItemId().isBlank() ? "(none)" : ShopUiUtil.getItemDisplayName(PlayerShopClientState.listedItemId()),
                guiTop + 40, ShopColors.TEXT_PRIMARY);
        drawRow(graphics, "Stock", PlayerShopClientState.stock() + "   Linked: " + (PlayerShopClientState.linked() ? "Yes" : "No"),
                guiTop + 52, ShopColors.TEXT_SECONDARY);

        if ("MONEY".equalsIgnoreCase(PlayerShopClientState.tradeMode())) {
            drawRow(graphics, "Cost", ShopUiUtil.formatMinorUnits(PlayerShopClientState.moneyPriceMinor()) + " coins",
                    guiTop + 64, ShopColors.TEXT_PRICE);
        } else {
            String barterName = PlayerShopClientState.barterItemId().isBlank()
                    ? "(unset)"
                    : ShopUiUtil.getItemDisplayName(PlayerShopClientState.barterItemId());
            drawRow(graphics, "Cost", PlayerShopClientState.barterItemCount() + "x " + barterName,
                    guiTop + 64, ShopColors.TEXT_BARTER);
        }

        drawRow(graphics, "Pending", ShopUiUtil.formatMinorUnits(PlayerShopClientState.pendingSettlementMinor()) + " coins",
                guiTop + 76, ShopColors.TEXT_PRICE);
        drawRow(graphics, "Lifetime", ShopUiUtil.formatMinorUnits(PlayerShopClientState.lifetimeRevenueMinor()) + " coins",
                guiTop + 88, ShopColors.TEXT_SECONDARY);

        int historyY = guiTop + 130;
        graphics.drawString(this.font, "Revenue", guiLeft + 8, historyY, ShopColors.TEXT_PRIMARY, false);
        List<String> rows = PlayerShopClientState.recentRevenueRows();
        for (int i = 0; i < Math.min(2, rows.size()); i++) {
            String clipped = this.font.plainSubstrByWidth(rows.get(i), GUI_W - 16);
            graphics.drawString(this.font, clipped, guiLeft + 8, historyY + 10 + i * 10, ShopColors.TEXT_SECONDARY, false);
        }

        if (!PlayerShopClientState.resultCode().isBlank()) {
            String status = this.font.plainSubstrByWidth(
                    Component.translatable("gui.futureshops.player_shop.status", localizeResultCode(PlayerShopClientState.resultCode())).getString(),
                    GUI_W - 16);
            graphics.drawString(this.font, status, guiLeft + 8, guiTop + GUI_H - 14, ShopColors.TEXT_SECONDARY, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawRow(GuiGraphics graphics, String label, String value, int y, int valueColor) {
        graphics.drawString(this.font, label + ":", guiLeft + 8, y, ShopColors.TEXT_SECONDARY, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(value, GUI_W - 58), guiLeft + 46, y, valueColor, false);
    }

    private void sendAction(String action, int amount) {
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopActionPacket(PlayerShopClientState.shopPos(), action, amount));
    }

    private void buy(int quantity) {
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopBuyPacket(PlayerShopClientState.shopPos(), quantity));
    }

    private String localizeResultCode(String code) {
        String key = "gui.futureshops.player_shop.result." + code.toLowerCase(java.util.Locale.ROOT);
        return Component.translatable(key).getString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
