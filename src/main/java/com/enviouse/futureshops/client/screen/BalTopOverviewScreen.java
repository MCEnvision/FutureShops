package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.BalanceTopEntry;
import com.enviouse.futureshops.data.FranchiseLeaderboardEntry;
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
    private List<FranchiseLeaderboardEntry> franchises;

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;

    public BalTopOverviewScreen(int page, int totalPages, List<BalanceTopEntry> entries, String currencyName, int currencyDecimals,
                                UUID activityLeaderUuid, String activityLeaderName, int activityLeaderCount,
                                UUID topSellerUuid, String topSellerName, int topSellerCount,
                                String popularItemId, int popularItemTrades, long popularItemQuantity,
                                List<FranchiseLeaderboardEntry> franchises) {
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
        this.franchises = List.copyOf(franchises);
    }

    @Override
    protected void init() {
        guiW = Math.max(360, this.width - 4);
        guiH = Math.max(280, this.height - 4);
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
        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + 2, ShopColors.ACCENT_CURRENCY);

        renderHeader(graphics);
        renderTopBalances(graphics);
        renderHighlights(graphics);
        renderFranchiseLeaderboard(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics) {
        ShopUiUtil.renderHeroHeader(graphics, this.font, guiLeft + 10, guiTop + 10, guiW - 20,
                this.title.getString(), "Page " + page + " / " + totalPages);
    }

    private void renderTopBalances(GuiGraphics graphics) {
        int panelX = guiLeft + 10;
        int panelY = guiTop + 58;
        int panelW = (guiW - 28) / 2;
        int panelH = guiH - 92;
        ShopUiUtil.renderCard(graphics, panelX, panelY, panelW, panelH);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, ShopColors.ACCENT_CURRENCY);
        graphics.drawString(this.font, "Top 10 balances", panelX + 8, panelY + 8, ShopColors.TEXT_STRONG, false);

        int rowY = panelY + 28;
        for (int i = 0; i < Math.min(10, entries.size()); i++) {
            BalanceTopEntry entry = entries.get(i);
            int y = rowY + i * 22;
            graphics.fill(panelX + 8, y, panelX + panelW - 8, y + 18, i % 2 == 0 ? ShopColors.SURFACE_RAISED : ShopColors.SURFACE_OVERLAY);
            ShopUiUtil.renderPlayerFace(graphics, entry.playerUuid(), panelX + 12, y + 1, 16);
            String rank = "#" + (((page - 1) * 10) + i + 1);
            int rankColor = i == 0 ? ShopColors.ACCENT_CURRENCY : (i <= 2 ? ShopColors.TEXT_STRONG : ShopColors.TEXT_FAINT);
            graphics.drawString(this.font, rank, panelX + 34, y + 5, rankColor, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(entry.playerName(), panelW - 136), panelX + 56, y + 5, ShopColors.TEXT_STRONG, false);
            String balance = formatMinorUnits(entry.balanceMinorUnits()) + " " + currencyName;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(balance, 90), panelX + panelW - 98, y + 5, ShopColors.TEXT_CURRENCY, false);
        }
        if (entries.isEmpty()) {
            graphics.drawString(this.font, "No balance data yet.", panelX + 8, panelY + 32, ShopColors.TEXT_FAINT, false);
        }
    }

    private void renderHighlights(GuiGraphics graphics) {
        int panelW = (guiW - 28) / 2;
        int panelX = guiLeft + guiW - panelW - 10;
        int panelY = guiTop + 58;
        // Highlights take upper portion, franchise takes lower
        int highlightH = Math.min(160, (guiH - 92) / 2 + 20);
        ShopUiUtil.renderCard(graphics, panelX, panelY, panelW, highlightH);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, ShopColors.ACCENT_PRIMARY);
        graphics.drawString(this.font, "Server spotlights", panelX + 8, panelY + 8, ShopColors.TEXT_STRONG, false);

        int cardH = Math.min(48, (highlightH - 28) / 2 - 4);
        renderHighlightCard(graphics, panelX + 8, panelY + 26, panelW - 16, cardH, "Most transactions", activityLeaderUuid, activityLeaderName,
                activityLeaderCount + " total actions", ShopColors.TEXT_BARTER_SOFT);
        renderHighlightCard(graphics, panelX + 8, panelY + 26 + cardH + 6, panelW - 16, cardH, "Top seller", topSellerUuid, topSellerName,
                topSellerCount + " shop sales", ShopColors.TEXT_CURRENCY);

        // Most popular product — compact
        int productY = panelY + highlightH - 38;
        graphics.fill(panelX + 8, productY, panelX + panelW - 8, productY + 30, ShopColors.SURFACE_OVERLAY);
        ShopUiUtil.drawBorder(graphics, panelX + 8, productY, panelW - 16, 30, ShopColors.BORDER_SUBTLE);
        if (!popularItemId.isBlank()) {
            ShopUiUtil.renderItemIcon(graphics, this.font, popularItemId, panelX + 14, productY + 7);
            graphics.drawString(this.font, this.font.plainSubstrByWidth("★ " + ShopUiUtil.getItemDisplayName(popularItemId), panelW - 72), panelX + 34, productY + 4, ShopColors.TEXT_STRONG, false);
            graphics.drawString(this.font, popularItemTrades + " trades • " + popularItemQuantity + " qty", panelX + 34, productY + 16, ShopColors.TEXT_CURRENCY, false);
        } else {
            graphics.drawString(this.font, "No product data yet.", panelX + 14, productY + 10, ShopColors.TEXT_FAINT, false);
        }
    }

    /**
     * Renders the Top 10 Franchises leaderboard panel in the lower-right area.
     */
    private void renderFranchiseLeaderboard(GuiGraphics graphics) {
        int panelW = (guiW - 28) / 2;
        int panelX = guiLeft + guiW - panelW - 10;
        int highlightH = Math.min(160, (guiH - 92) / 2 + 20);
        int panelY = guiTop + 58 + highlightH + 6;
        int panelH = guiH - 92 - highlightH - 6;
        if (panelH < 40) return;

        ShopUiUtil.renderCard(graphics, panelX, panelY, panelW, panelH);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, ShopColors.ACCENT_PROMO_HI);
        graphics.drawString(this.font, "§l⚑ Top 10 Franchises", panelX + 8, panelY + 6, ShopColors.ACCENT_PROMO_HI, false);

        if (franchises.isEmpty()) {
            graphics.drawString(this.font, "§7No franchises yet.", panelX + 8, panelY + 22, ShopColors.TEXT_FAINT, false);
            return;
        }

        int rowHeight = 16;
        int maxRows = Math.max(1, (panelH - 22) / rowHeight);
        int startY = panelY + 22;
        for (int i = 0; i < Math.min(maxRows, franchises.size()); i++) {
            FranchiseLeaderboardEntry f = franchises.get(i);
            int y = startY + i * rowHeight;
            graphics.fill(panelX + 6, y, panelX + panelW - 6, y + rowHeight - 2,
                    i % 2 == 0 ? ShopColors.SURFACE_RAISED : ShopColors.SURFACE_OVERLAY);

            String rank = "#" + (i + 1);
            graphics.drawString(this.font, rank, panelX + 10, y + 3, ShopColors.TEXT_FAINT, false);

            String fName = this.font.plainSubstrByWidth(f.name(), panelW / 2 - 30);
            graphics.drawString(this.font, fName, panelX + 30, y + 3, ShopColors.TEXT_STRONG, false);

            String detail = f.memberCount() + " mbr • " + this.font.plainSubstrByWidth(f.leaderName(), 50);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(detail, panelW / 2 - 10),
                    panelX + panelW / 2 + 4, y + 3, ShopColors.TEXT_BARTER_SOFT, false);
        }
    }

    private void renderHighlightCard(GuiGraphics graphics, int x, int y, int width, int height, String title, UUID uuid, String name, String detail, int accent) {
        ShopUiUtil.renderPanel(graphics, x, y, width, height, ShopColors.SURFACE_RAISED, ShopColors.BORDER_SUBTLE);
        graphics.fill(x, y, x + 2, y + height, accent);
        int faceSize = Math.min(24, height - 8);
        ShopUiUtil.renderPlayerFace(graphics, uuid, x + 6, y + (height - faceSize) / 2, faceSize);
        int textX = x + faceSize + 12;
        graphics.drawString(this.font, title, textX, y + 4, ShopColors.TEXT_SECONDARY, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(name, width - faceSize - 20), textX, y + 16, ShopColors.TEXT_PRIMARY, false);
        if (height > 32) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(detail, width - faceSize - 20), textX, y + 28, accent, false);
        }
    }

    public void updatePage(int page, int totalPages, List<BalanceTopEntry> entries,
                           UUID activityLeaderUuid, String activityLeaderName, int activityLeaderCount,
                           UUID topSellerUuid, String topSellerName, int topSellerCount,
                           String popularItemId, int popularItemTrades, long popularItemQuantity,
                           List<FranchiseLeaderboardEntry> franchises) {
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
        this.franchises = List.copyOf(franchises);
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
