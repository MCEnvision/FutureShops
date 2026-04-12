package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.PlayerShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SPlayerShopActionPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopBuyPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public class PlayerShopBlockScreen extends Screen implements ShopScreenMarker {
    private static final int DEFAULT_GUI_W = 324;
    private static final int DEFAULT_GUI_H = 228;
    private static final int PREVIEW_W = 124;

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private EditBox quantityBox;

    public PlayerShopBlockScreen() {
        super(Component.literal("Player Shop"));
    }

    @Override
    protected void init() {
        guiW = Math.min(DEFAULT_GUI_W, this.width - 16);
        guiH = Math.min(DEFAULT_GUI_H, this.height - 16);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;

        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(guiLeft + guiW - 58, guiTop + 8, 50, 14)
                .build());

        if (PlayerShopClientState.owner()) {
            initOwnerWidgets();
        } else {
            initVisitorWidgets();
        }
    }

    private void initOwnerWidgets() {
        addRenderableWidget(Button.builder(Component.literal("Set Item"), button -> sendAction("SET_LISTING_MAINHAND", 0))
                .bounds(guiLeft + 10, guiTop + 110, 62, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), button -> sendAction("CLEAR_LISTING", 0))
                .bounds(guiLeft + 76, guiTop + 110, 48, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Mode"), button -> sendAction("TOGGLE_MODE", 0))
                .bounds(guiLeft + 128, guiTop + 110, 48, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Promo"), button -> this.minecraft.setScreen(new PromoEditorModalScreen(this)))
                .bounds(guiLeft + 180, guiTop + 110, 50, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Claim"), button -> sendAction("CLAIM_SETTLEMENT", 0))
                .bounds(guiLeft + 234, guiTop + 110, 42, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("History"), button -> this.minecraft.setScreen(new SettlementHistoryScreen(this)))
                .bounds(guiLeft + 280, guiTop + 110, 34, 14)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Price +"), button -> sendAction("PRICE_UP", 100))
                .bounds(guiLeft + 128, guiTop + 128, 56, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Price -"), button -> sendAction("PRICE_DOWN", 100))
                .bounds(guiLeft + 188, guiTop + 128, 56, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Barter = Hand"), button -> sendAction("SET_BARTER_MAINHAND", 1))
                .bounds(guiLeft + 128, guiTop + 146, 90, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Start Link"), button -> sendAction("LINK_LOOKING", 0))
                .bounds(guiLeft + 222, guiTop + 146, 54, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Unlink"), button -> sendAction("UNLINK", 0))
                .bounds(guiLeft + 280, guiTop + 146, 34, 14)
                .build());
    }

    private void initVisitorWidgets() {
        int quantityY = guiTop + guiH - 56;
        quantityBox = new EditBox(this.font, guiLeft + 46, quantityY, 32, 14, Component.literal("Quantity"));
        quantityBox.setValue("1");
        quantityBox.setMaxLength(2);
        quantityBox.setResponder(value -> {
            if (value.isBlank()) {
                return;
            }
            try {
                String clamped = Integer.toString(clampQuantity(Integer.parseInt(value)));
                if (!clamped.equals(value)) {
                    quantityBox.setValue(clamped);
                }
            } catch (NumberFormatException ignored) {
                if (!"1".equals(value)) {
                    quantityBox.setValue("1");
                }
            }
        });
        addRenderableWidget(quantityBox);

        addRenderableWidget(Button.builder(Component.literal("-"), button -> setQuantity(getQuantity() - 1))
                .bounds(guiLeft + 28, quantityY, 14, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> setQuantity(getQuantity() + 1))
                .bounds(guiLeft + 82, quantityY, 14, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Max"), button -> setQuantity(resolveMaxQuantity()))
                .bounds(guiLeft + 100, quantityY, 28, 14)
                .build());

        String cta = "MONEY".equalsIgnoreCase(PlayerShopClientState.tradeMode()) ? "Buy" : "Barter";
        addRenderableWidget(Button.builder(Component.literal(cta), button -> {
                    if ("MONEY".equalsIgnoreCase(PlayerShopClientState.tradeMode())) {
                        buy(getQuantity());
                    } else if (this.minecraft != null) {
                        this.minecraft.setScreen(new PlayerShopBarterScreen(this));
                    }
                })
                .bounds(guiLeft + 12, guiTop + guiH - 36, 108, 16)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, ShopColors.BG_PRIMARY);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.BG_PANEL);
        ShopUiUtil.drawBorder(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_DEFAULT);

        graphics.drawString(this.font, PlayerShopClientState.owner() ? "Manage Player Shop" : "Player Shop", guiLeft + 8, guiTop + 10, ShopColors.TEXT_PRIMARY, false);

        renderPreview(graphics);
        renderInfoPanel(graphics);

        if (PlayerShopClientState.owner()) {
            renderRevenueSummary(graphics);
        }

        if (!PlayerShopClientState.resultCode().isBlank()) {
            String status = this.font.plainSubstrByWidth(
                    Component.translatable("gui.futureshops.player_shop.status", localizeResultCode(PlayerShopClientState.resultCode())).getString(),
                    guiW - 16);
            graphics.drawString(this.font, status, guiLeft + 8, guiTop + guiH - 14, ShopColors.TEXT_SECONDARY, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPreview(GuiGraphics graphics) {
        int leftX = guiLeft + 10;
        int panelY = guiTop + 24;
        int panelH = guiH - 48;
        graphics.fill(leftX, panelY, leftX + PREVIEW_W, panelY + panelH, ShopColors.BG_CARD);
        ShopUiUtil.drawBorder(graphics, leftX, panelY, PREVIEW_W, panelH, ShopColors.BORDER_DEFAULT);

        if (!PlayerShopClientState.listedItemId().isBlank()) {
            ShopUiUtil.renderLargeItemPreview(graphics, this.font, PlayerShopClientState.listedItemId(), leftX, panelY + 6, PREVIEW_W);
        }
        graphics.drawCenteredString(this.font,
                this.font.plainSubstrByWidth(displayItemName(), PREVIEW_W - 10),
                leftX + PREVIEW_W / 2,
                panelY + 92,
                ShopColors.TEXT_PRIMARY);
        graphics.drawCenteredString(this.font,
                "Owner: " + PlayerShopClientState.ownerName(),
                leftX + PREVIEW_W / 2,
                panelY + 108,
                ShopColors.TEXT_SECONDARY);

        if (!PlayerShopClientState.owner() && quantityBox != null) {
            graphics.drawCenteredString(this.font, "Quantity", leftX + PREVIEW_W / 2, guiTop + guiH - 66, ShopColors.TEXT_SECONDARY);
        }
    }

    private void renderInfoPanel(GuiGraphics graphics) {
        int infoX = guiLeft + PREVIEW_W + 20;
        int infoY = guiTop + 26;
        int infoW = guiW - PREVIEW_W - 30;

        drawInfoRow(graphics, "Item", displayItemName(), infoX, infoW, infoY, ShopColors.TEXT_PRIMARY);
        drawInfoRow(graphics, "Stock", Integer.toString(PlayerShopClientState.stock()), infoX, infoW, infoY + 14, ShopColors.TEXT_SECONDARY);
        drawInfoRow(graphics, "Linked", PlayerShopClientState.linked() ? "Connected" : "Not Linked", infoX, infoW, infoY + 28, ShopColors.TEXT_SECONDARY);
        drawInfoRow(graphics, "Mode", prettyMode(PlayerShopClientState.tradeMode()), infoX, infoW, infoY + 42,
                "MONEY".equalsIgnoreCase(PlayerShopClientState.tradeMode()) ? ShopColors.TEXT_PRICE : ShopColors.TEXT_BARTER);

        if ("MONEY".equalsIgnoreCase(PlayerShopClientState.tradeMode())) {
            drawInfoRow(graphics, "Price", ShopUiUtil.formatMinorUnits(PlayerShopClientState.moneyPriceMinor()) + " coins",
                    infoX, infoW, infoY + 58, ShopColors.TEXT_PRICE);
            if (!PlayerShopClientState.owner()) {
                graphics.drawString(this.font, "This shop sells directly for balance currency.", infoX, infoY + 78, ShopColors.TEXT_SECONDARY, false);
            }
        } else {
            String barterName = PlayerShopClientState.barterItemId().isBlank()
                    ? "(unset)"
                    : ShopUiUtil.getItemDisplayName(PlayerShopClientState.barterItemId());
            drawInfoRow(graphics, "Trade", PlayerShopClientState.barterItemCount() + "x " + barterName,
                    infoX, infoW, infoY + 58, ShopColors.TEXT_BARTER);
            if (!PlayerShopClientState.owner()) {
                graphics.drawString(this.font, "Open barter to review the trade and confirm quantity.", infoX, infoY + 78, ShopColors.TEXT_SECONDARY, false);
            }
        }
    }

    private void renderRevenueSummary(GuiGraphics graphics) {
        int infoX = guiLeft + PREVIEW_W + 20;
        int baseY = guiTop + 118;
        int infoW = guiW - PREVIEW_W - 30;

        drawInfoRow(graphics, "Pending", ShopUiUtil.formatMinorUnits(PlayerShopClientState.pendingSettlementMinor()) + " coins",
                infoX, infoW, baseY, ShopColors.TEXT_PRICE);
        drawInfoRow(graphics, "Lifetime", ShopUiUtil.formatMinorUnits(PlayerShopClientState.lifetimeRevenueMinor()) + " coins",
                infoX, infoW, baseY + 14, ShopColors.TEXT_SECONDARY);

        graphics.drawString(this.font, "Recent Revenue", infoX, baseY + 34, ShopColors.TEXT_PRIMARY, false);
        List<String> rows = PlayerShopClientState.recentRevenueRows();
        for (int i = 0; i < Math.min(3, rows.size()); i++) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(rows.get(i), infoW), infoX, baseY + 46 + i * 10, ShopColors.TEXT_SECONDARY, false);
        }
    }

    private void drawInfoRow(GuiGraphics graphics, String label, String value, int x, int width, int y, int valueColor) {
        graphics.drawString(this.font, label + ":", x, y, ShopColors.TEXT_SECONDARY, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(value, width - 48), x + 48, y, valueColor, false);
    }

    private String displayItemName() {
        return PlayerShopClientState.listedItemId().isBlank()
                ? "No Item Configured"
                : ShopUiUtil.getItemDisplayName(PlayerShopClientState.listedItemId());
    }

    private String prettyMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "Money";
        }
        String lower = mode.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private void sendAction(String action, int amount) {
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopActionPacket(PlayerShopClientState.shopPos(), action, amount));
    }

    private void buy(int quantity) {
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopBuyPacket(PlayerShopClientState.shopPos(), quantity));
    }

    private int getQuantity() {
        try {
            return clampQuantity(Integer.parseInt(quantityBox.getValue()));
        } catch (Exception ignored) {
            return 1;
        }
    }

    private void setQuantity(int quantity) {
        if (quantityBox != null) {
            quantityBox.setValue(Integer.toString(clampQuantity(quantity)));
        }
    }

    private int resolveMaxQuantity() {
        return Math.max(1, Math.min(64, PlayerShopClientState.stock()));
    }

    private int clampQuantity(int quantity) {
        return Math.max(1, Math.min(resolveMaxQuantity(), quantity));
    }

    private String localizeResultCode(String code) {
        String key = "gui.futureshops.player_shop.result." + code.toLowerCase(Locale.ROOT);
        return Component.translatable(key).getString();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
