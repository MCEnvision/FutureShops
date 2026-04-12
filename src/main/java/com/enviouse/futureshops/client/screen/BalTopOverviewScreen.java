package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.BalanceTopEntry;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SOpenBalTopUiPacket;
import com.enviouse.futureshops.network.packets.C2SOpenBalanceUiPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

public class BalTopOverviewScreen extends Screen implements ShopScreenMarker {
    private int page;
    private int totalPages;
    private List<BalanceTopEntry> entries;
    private final String currencyName;
    private final int currencyDecimals;
    private UUID activityLeaderUuid;
    private String activityLeaderName;
    private int activityLeaderCount;
    private UUID topSellerUuid;
    private String topSellerName;
    private int topSellerCount;
    private String popularItemId;
    private int popularItemTrades;
    private long popularItemQuantity;

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;

    public BalTopOverviewScreen(int page, int totalPages, List<BalanceTopEntry> entries, String currencyName, int currencyDecimals,
                                UUID activityLeaderUuid, String activityLeaderName, int activityLeaderCount,
                                UUID topSellerUuid, String topSellerName, int topSellerCount,
                                String popularItemId, int popularItemTrades, long popularItemQuantity) {
        super(Component.literal("Marketplace Leaders"));
        this.page = page;
        this.totalPages = Math.max(1, totalPages);
        this.entries = List.copyOf(entries);
        this.currencyName = currencyName;
        this.currencyDecimals = currencyDecimals;
        this.activityLeaderUuid = activityLeaderUuid;
        this.activityLeaderName = activityLeaderName;
        this.activityLeaderCount = activityLeaderCount;
        this.topSellerUuid = topSellerUuid;
        this.topSellerName = topSellerName;
        this.topSellerCount = topSellerCount;
        this.popularItemId = popularItemId;
        this.popularItemTrades = popularItemTrades;
        this.popularItemQuantity = popularItemQuantity;
    }

    @Override
    protected void init() {
        guiW = Math.min(560, Math.max(360, this.width - 24));
        guiH = Math.min(320, Math.max(250, this.height - 24));
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;

        addRenderableWidget(Button.builder(Component.literal("§7← Back"), button ->
                        ShopPackets.CHANNEL.sendToServer(new C2SOpenBalanceUiPacket()))
                .bounds(guiLeft + 10, guiTop + guiH - 24, 48, 18)
                .build());
        addRenderableWidget(Button.builder(Component.literal("<"), button -> request(Math.max(1, page - 1)))
                .bounds(guiLeft + guiW - 106, guiTop + guiH - 24, 18, 18)
                .build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> request(Math.min(totalPages, page + 1)))
                .bounds(guiLeft + guiW - 84, guiTop + guiH - 24, 18, 18)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(guiLeft + guiW - 62, guiTop + guiH - 24, 54, 18)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, ShopColors.BG_PRIMARY);
        ShopUiUtil.renderPanel(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BG_PANEL, ShopColors.BORDER_DEFAULT);

        renderHeader(graphics);
        renderTopBalances(graphics);
        renderHighlights(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics) {
        ShopUiUtil.renderPanel(graphics, guiLeft + 10, guiTop + 10, guiW - 20, 40, ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT);
        graphics.drawString(this.font, this.title, guiLeft + 18, guiTop + 18, ShopColors.TEXT_PRIMARY, false);
        String pageText = "Page " + page + " / " + totalPages;
        graphics.drawString(this.font, pageText, guiLeft + guiW - this.font.width(pageText) - 18, guiTop + 18, ShopColors.TEXT_SECONDARY, false);
    }

    private void renderTopBalances(GuiGraphics graphics) {
        int panelX = guiLeft + 10;
        int panelY = guiTop + 58;
        int panelW = (guiW - 28) / 2;
        int panelH = guiH - 92;
        ShopUiUtil.renderPanel(graphics, panelX, panelY, panelW, panelH, ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT);
        graphics.drawString(this.font, "Top 10 balances", panelX + 8, panelY + 8, ShopColors.TEXT_PRIMARY, false);

        int rowY = panelY + 28;
        for (int i = 0; i < Math.min(10, entries.size()); i++) {
            BalanceTopEntry entry = entries.get(i);
            int y = rowY + i * 22;
            graphics.fill(panelX + 8, y, panelX + panelW - 8, y + 18, i % 2 == 0 ? ShopColors.BG_PANEL : ShopColors.BG_CARD_HOVER);
            ShopUiUtil.renderPlayerFace(graphics, entry.playerUuid(), panelX + 12, y + 1, 16);
            String rank = "#" + (((page - 1) * 10) + i + 1);
            graphics.drawString(this.font, rank, panelX + 34, y + 5, ShopColors.TEXT_SECONDARY, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(entry.playerName(), panelW - 136), panelX + 56, y + 5, ShopColors.TEXT_PRIMARY, false);
            String balance = formatMinorUnits(entry.balanceMinorUnits()) + " " + currencyName;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(balance, 90), panelX + panelW - 98, y + 5, ShopColors.TEXT_PRICE, false);
        }
        if (entries.isEmpty()) {
            graphics.drawString(this.font, "No balance data yet.", panelX + 8, panelY + 32, ShopColors.TEXT_SECONDARY, false);
        }
    }

    private void renderHighlights(GuiGraphics graphics) {
        int panelW = (guiW - 28) / 2;
        int panelX = guiLeft + guiW - panelW - 10;
        int panelY = guiTop + 58;
        int panelH = guiH - 92;
        ShopUiUtil.renderPanel(graphics, panelX, panelY, panelW, panelH, ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT);
        graphics.drawString(this.font, "Server spotlights", panelX + 8, panelY + 8, ShopColors.TEXT_PRIMARY, false);

        renderHighlightCard(graphics, panelX + 8, panelY + 26, panelW - 16, "Most transactions", activityLeaderUuid, activityLeaderName,
                activityLeaderCount + " total actions", ShopColors.TEXT_BARTER);
        renderHighlightCard(graphics, panelX + 8, panelY + 92, panelW - 16, "Top seller", topSellerUuid, topSellerName,
                topSellerCount + " shop sales", ShopColors.TEXT_PRICE);

        int productY = panelY + 158;
        ShopUiUtil.renderPanel(graphics, panelX + 8, productY, panelW - 16, 62, ShopColors.BG_PANEL, ShopColors.BORDER_DEFAULT);
        graphics.drawString(this.font, "Most popular product", panelX + 16, productY + 8, ShopColors.TEXT_SECONDARY, false);
        if (!popularItemId.isBlank()) {
            ShopUiUtil.renderItemIcon(graphics, this.font, popularItemId, panelX + 16, productY + 24);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayName(popularItemId), panelW - 64), panelX + 36, productY + 26, ShopColors.TEXT_PRIMARY, false);
            graphics.drawString(this.font, popularItemTrades + " trades • " + popularItemQuantity + " qty", panelX + 36, productY + 40, ShopColors.TEXT_PRICE, false);
        } else {
            graphics.drawString(this.font, "No product data yet.", panelX + 16, productY + 30, ShopColors.TEXT_SECONDARY, false);
        }
    }

    private void renderHighlightCard(GuiGraphics graphics, int x, int y, int width, String title, UUID uuid, String name, String detail, int accent) {
        ShopUiUtil.renderPanel(graphics, x, y, width, 58, ShopColors.BG_PANEL, ShopColors.BORDER_DEFAULT);
        ShopUiUtil.renderPlayerFace(graphics, uuid, x + 8, y + 12, 24);
        graphics.drawString(this.font, title, x + 40, y + 8, ShopColors.TEXT_SECONDARY, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(name, width - 52), x + 40, y + 22, ShopColors.TEXT_PRIMARY, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(detail, width - 52), x + 40, y + 36, accent, false);
    }

    public void updatePage(int page, int totalPages, List<BalanceTopEntry> entries,
                           UUID activityLeaderUuid, String activityLeaderName, int activityLeaderCount,
                           UUID topSellerUuid, String topSellerName, int topSellerCount,
                           String popularItemId, int popularItemTrades, long popularItemQuantity) {
        this.page = page;
        this.totalPages = Math.max(1, totalPages);
        this.entries = List.copyOf(entries);
        this.activityLeaderUuid = activityLeaderUuid;
        this.activityLeaderName = activityLeaderName;
        this.activityLeaderCount = activityLeaderCount;
        this.topSellerUuid = topSellerUuid;
        this.topSellerName = topSellerName;
        this.topSellerCount = topSellerCount;
        this.popularItemId = popularItemId;
        this.popularItemTrades = popularItemTrades;
        this.popularItemQuantity = popularItemQuantity;
    }

    private void request(int targetPage) {
        ShopPackets.CHANNEL.sendToServer(new C2SOpenBalTopUiPacket(targetPage));
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
    public boolean isPauseScreen() {
        return false;
    }
}
