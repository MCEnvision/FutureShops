package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.OwnedShopSummary;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SOpenBalTopUiPacket;
import com.enviouse.futureshops.network.packets.C2SOpenShopPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

public class BalanceOverviewScreen extends Screen implements ShopScreenMarker {
    private final UUID playerUuid;
    private final String playerName;
    private final long balanceMinorUnits;
    private final String currencyName;
    private final int currencyDecimals;
    private final long totalRevenueMinor;
    private final long pendingSettlementMinor;
    private final int shopCount;
    private final int listingCount;
    private final int totalStock;
    private final int lowSupplyCount;
    private final List<OwnedShopSummary> shopSummaries;
    private final List<String> alerts;

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private int shopScroll;
    private int alertScroll;
    private int visibleShopCards;
    private int visibleAlerts;

    public BalanceOverviewScreen(UUID playerUuid, String playerName, long balanceMinorUnits, String currencyName, int currencyDecimals,
                                 long totalRevenueMinor, long pendingSettlementMinor, int shopCount, int listingCount,
                                 int totalStock, int lowSupplyCount, List<OwnedShopSummary> shopSummaries, List<String> alerts) {
        super(Component.literal("Marketplace Profile"));
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.balanceMinorUnits = balanceMinorUnits;
        this.currencyName = currencyName;
        this.currencyDecimals = currencyDecimals;
        this.totalRevenueMinor = totalRevenueMinor;
        this.pendingSettlementMinor = pendingSettlementMinor;
        this.shopCount = shopCount;
        this.listingCount = listingCount;
        this.totalStock = totalStock;
        this.lowSupplyCount = lowSupplyCount;
        this.shopSummaries = List.copyOf(shopSummaries);
        this.alerts = List.copyOf(alerts);
    }

    @Override
    protected void init() {
        guiW = Math.min(540, Math.max(360, this.width - 24));
        guiH = Math.min(320, Math.max(240, this.height - 24));
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;
        visibleShopCards = Math.max(2, (guiH - 156) / 52);
        visibleAlerts = Math.max(3, (guiH - 156) / 18);

        addRenderableWidget(Button.builder(Component.literal("Storefront"), button ->
                        ShopPackets.CHANNEL.sendToServer(new C2SOpenShopPacket("default")))
                .bounds(guiLeft + guiW - 214, guiTop + guiH - 24, 68, 18)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Leaders"), button ->
                        ShopPackets.CHANNEL.sendToServer(new C2SOpenBalTopUiPacket(1)))
                .bounds(guiLeft + guiW - 142, guiTop + guiH - 24, 60, 18)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(guiLeft + guiW - 78, guiTop + guiH - 24, 60, 18)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, ShopColors.BG_PRIMARY);
        ShopUiUtil.renderPanel(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BG_PANEL, ShopColors.BORDER_DEFAULT);

        renderHeader(graphics);
        renderMetrics(graphics);
        renderOwnedShops(graphics, mouseX, mouseY);
        renderAlerts(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics) {
        ShopUiUtil.renderPanel(graphics, guiLeft + 10, guiTop + 10, guiW - 20, 56, ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT);
        ShopUiUtil.renderPlayerFace(graphics, playerUuid, guiLeft + 18, guiTop + 18, 40);
        graphics.drawString(this.font, this.title, guiLeft + 68, guiTop + 18, ShopColors.TEXT_PRIMARY, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(playerName, 132), guiLeft + 68, guiTop + 32, ShopColors.TEXT_SECONDARY, false);
        String balanceText = formatMinorUnits(balanceMinorUnits) + " " + currencyName;
        graphics.drawString(this.font, balanceText, guiLeft + guiW - this.font.width(balanceText) - 18, guiTop + 24, ShopColors.TEXT_PRICE, false);
        ShopUiUtil.drawChip(graphics, this.font, guiLeft + guiW - 112, guiTop + 44,
                lowSupplyCount > 0 ? lowSupplyCount + " low stock" : "Supply stable",
                ShopColors.BG_PANEL,
                lowSupplyCount > 0 ? ShopColors.ERROR : ShopColors.SUCCESS,
                lowSupplyCount > 0 ? ShopColors.ERROR : ShopColors.SUCCESS);
    }

    private void renderMetrics(GuiGraphics graphics) {
        int cardY = guiTop + 74;
        int gap = 8;
        int cardW = (guiW - 20 - (gap * 3)) / 4;
        renderMetricCard(graphics, guiLeft + 10, cardY, cardW, "Revenue", formatMinorUnits(totalRevenueMinor), ShopColors.TEXT_PRICE);
        renderMetricCard(graphics, guiLeft + 10 + (cardW + gap), cardY, cardW, "Pending", formatMinorUnits(pendingSettlementMinor), ShopColors.TEXT_BARTER);
        renderMetricCard(graphics, guiLeft + 10 + (cardW + gap) * 2, cardY, cardW, "Shops", shopCount + " / " + listingCount + " listings", ShopColors.TEXT_PRIMARY);
        renderMetricCard(graphics, guiLeft + 10 + (cardW + gap) * 3, cardY, cardW, "Supply", totalStock + " items tracked", ShopColors.TEXT_SECONDARY);
    }

    private void renderMetricCard(GuiGraphics graphics, int x, int y, int width, String title, String value, int accent) {
        ShopUiUtil.renderPanel(graphics, x, y, width, 44, ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT);
        graphics.drawString(this.font, title, x + 8, y + 8, ShopColors.TEXT_SECONDARY, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(value, width - 16), x + 8, y + 22, accent, false);
    }

    private void renderOwnedShops(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = guiLeft + 10;
        int panelY = guiTop + 126;
        int panelW = (guiW - 28) / 2;
        int panelH = guiH - 160;
        ShopUiUtil.renderPanel(graphics, panelX, panelY, panelW, panelH, ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT);
        graphics.drawString(this.font, "Your placed shops", panelX + 8, panelY + 8, ShopColors.TEXT_PRIMARY, false);
        graphics.drawString(this.font, shopCount == 0 ? "No shops registered yet" : shopCount + " tracked across the server", panelX + 8, panelY + 20, ShopColors.TEXT_SECONDARY, false);

        int maxScroll = Math.max(0, shopSummaries.size() - visibleShopCards);
        shopScroll = Math.max(0, Math.min(shopScroll, maxScroll));
        if (shopSummaries.isEmpty()) {
            graphics.drawString(this.font, "Place and configure a shop block to see revenue, stock, and supply health here.", panelX + 8, panelY + 44, ShopColors.TEXT_SECONDARY, false);
            return;
        }

        int cardY = panelY + 36;
        for (int i = 0; i < visibleShopCards && i + shopScroll < shopSummaries.size(); i++) {
            OwnedShopSummary summary = shopSummaries.get(i + shopScroll);
            int y = cardY + i * 52;
            ShopUiUtil.renderPanel(graphics, panelX + 8, y, panelW - 16, 46, i % 2 == 0 ? ShopColors.BG_PANEL : ShopColors.BG_CARD_HOVER, ShopColors.BORDER_DEFAULT);
            if (!summary.featuredItemId().isBlank()) {
                ShopUiUtil.renderItemIcon(graphics, this.font, summary.featuredItemId(), panelX + 14, y + 14);
            }
            BlockPos pos = BlockPos.of(summary.shopPosLong());
            String title = summary.listingCount() + " listing" + (summary.listingCount() == 1 ? "" : "s") + " • " + displayDimension(summary.dimensionKey());
            graphics.drawString(this.font, this.font.plainSubstrByWidth(title, panelW - 76), panelX + 36, y + 6, ShopColors.TEXT_PRIMARY, false);
            graphics.drawString(this.font, pos.getX() + ", " + pos.getY() + ", " + pos.getZ(), panelX + 36, y + 18, ShopColors.TEXT_SECONDARY, false);
            graphics.drawString(this.font, "Stock " + summary.totalStock() + " • Revenue " + formatMinorUnits(summary.lifetimeMinor()), panelX + 36, y + 30, ShopColors.TEXT_PRICE, false);
            if (summary.lowStockListings() > 0) {
                ShopUiUtil.drawChip(graphics, this.font, panelX + panelW - 88, y + 6, summary.lowStockListings() + " low", ShopColors.BG_PANEL, ShopColors.ERROR, ShopColors.ERROR);
            } else if (summary.linked()) {
                ShopUiUtil.drawChip(graphics, this.font, panelX + panelW - 88, y + 6, "linked", ShopColors.BG_PANEL, ShopColors.STORAGE_LINKED, ShopColors.STORAGE_LINKED);
            }
        }
    }

    private void renderAlerts(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelW = (guiW - 28) / 2;
        int panelX = guiLeft + guiW - panelW - 10;
        int panelY = guiTop + 126;
        int panelH = guiH - 160;
        ShopUiUtil.renderPanel(graphics, panelX, panelY, panelW, panelH, ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT);
        graphics.drawString(this.font, "Supply warnings", panelX + 8, panelY + 8, ShopColors.TEXT_PRIMARY, false);
        graphics.drawString(this.font, alerts.isEmpty() ? "Everything looks healthy" : alerts.size() + " attention point(s)", panelX + 8, panelY + 20, ShopColors.TEXT_SECONDARY, false);

        int maxScroll = Math.max(0, alerts.size() - visibleAlerts);
        alertScroll = Math.max(0, Math.min(alertScroll, maxScroll));
        if (alerts.isEmpty()) {
            graphics.drawString(this.font, "No low-supply or missing-link warnings right now.", panelX + 8, panelY + 44, ShopColors.SUCCESS, false);
            return;
        }

        int lineY = panelY + 40;
        for (int i = 0; i < visibleAlerts && i + alertScroll < alerts.size(); i++) {
            int y = lineY + i * 18;
            graphics.fill(panelX + 8, y, panelX + panelW - 8, y + 16, i % 2 == 0 ? ShopColors.BG_PANEL : ShopColors.BG_CARD_HOVER);
            graphics.fill(panelX + 8, y, panelX + 9, y + 16, ShopColors.ERROR);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(alerts.get(i + alertScroll), panelW - 24), panelX + 14, y + 4, ShopColors.TEXT_PRIMARY, false);
        }
    }

    private String displayDimension(String dimensionKey) {
        String value = dimensionKey.substring(dimensionKey.indexOf(':') + 1).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String formatMinorUnits(long minorUnits) {
        if (currencyDecimals <= 0) {
            return Long.toString(minorUnits);
        }
        long divisor = (long) Math.pow(10, currencyDecimals);
        long whole = minorUnits / divisor;
        long fractional = Math.abs(minorUnits % divisor);
        return whole + "." + String.format("%0" + currencyDecimals + "d", fractional);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int shopsX = guiLeft + 10;
        int panelW = (guiW - 28) / 2;
        int panelY = guiTop + 126;
        int panelH = guiH - 160;
        if (mouseX >= shopsX && mouseX <= shopsX + panelW && mouseY >= panelY && mouseY <= panelY + panelH) {
            shopScroll = Math.max(0, Math.min(Math.max(0, shopSummaries.size() - visibleShopCards), shopScroll - (int) delta));
            return true;
        }
        int alertsX = guiLeft + guiW - panelW - 10;
        if (mouseX >= alertsX && mouseX <= alertsX + panelW && mouseY >= panelY && mouseY <= panelY + panelH) {
            alertScroll = Math.max(0, Math.min(Math.max(0, alerts.size() - visibleAlerts), alertScroll - (int) delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
