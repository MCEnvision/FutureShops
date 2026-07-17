package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.BalanceTopEntry;
import com.enviouse.futureshops.data.FranchiseLeaderboardEntry;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SOpenBalTopUiPacket;
import com.enviouse.futureshops.network.packets.C2SOpenBalanceUiPacket;
import net.minecraft.client.gui.GuiGraphics;
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
    private String popularItemNbtJson;
    private List<FranchiseLeaderboardEntry> franchises;

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;

    private int highlightScroll;
    private int highlightPanelX;
    private int highlightPanelY;
    private int highlightPanelW;
    private int highlightPanelH;
    private int highlightContentHeight;
    private int highlightViewportHeight;

    /** Per-frame flat-button hit regions, populated in {@link #render}, consulted in mouseClicked. */
    private final java.util.List<ShopUiUtil.ClickZone> clickZones = new java.util.ArrayList<>();

    public BalTopOverviewScreen(int page, int totalPages, List<BalanceTopEntry> entries, String currencyName, int currencyDecimals,
                                UUID activityLeaderUuid, String activityLeaderName, int activityLeaderCount,
                                UUID topSellerUuid, String topSellerName, int topSellerCount,
                                String popularItemId, int popularItemTrades, long popularItemQuantity, String popularItemNbtJson,
                                List<FranchiseLeaderboardEntry> franchises) {
        super(Component.translatable("gui.futureshops.baltop.title"));
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
        this.popularItemNbtJson = popularItemNbtJson == null ? "" : popularItemNbtJson;
        this.franchises = List.copyOf(franchises);
    }

    @Override
    protected void init() {
        guiW = Math.max(360, this.width - 4);
        guiH = Math.max(280, this.height - 4);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;
        // Footer nav buttons are drawn immediate-mode in render().
    }

    /** Draws the footer nav buttons and registers their click zones for this frame. */
    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = guiTop + guiH - 24;
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 10, y, 48, 18,
                Component.translatable("gui.futureshops.baltop.back"),
                ShopUiUtil.ButtonStyle.SECONDARY, true,
                () -> ShopPackets.CHANNEL.sendToServer(new C2SOpenBalanceUiPacket()));
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 106, y, 18, 18, Component.literal("<"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, () -> request(Math.max(1, page - 1)));
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 84, y, 18, 18, Component.literal(">"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, () -> request(Math.min(totalPages, page + 1)));
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 62, y, 54, 18,
                Component.translatable("gui.futureshops.baltop.close"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, this::onClose);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();
        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + 2, ShopColors.ACCENT_CURRENCY);

        renderHeader(graphics);
        renderTopBalances(graphics);
        renderHighlights(graphics);
        renderFranchiseLeaderboard(graphics);
        renderButtons(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderHeader(GuiGraphics graphics) {
        ShopUiUtil.renderHeroHeader(graphics, this.font, guiLeft + 10, guiTop + 10, guiW - 20,
                this.title.getString(),
                Component.translatable("gui.futureshops.history.page", page, totalPages).getString());
    }

    private void renderTopBalances(GuiGraphics graphics) {
        int panelX = guiLeft + 10;
        int panelY = guiTop + 58;
        int panelW = (guiW - 28) / 2;
        int panelH = guiH - 92;
        ShopUiUtil.renderCard(graphics, panelX, panelY, panelW, panelH);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, ShopColors.ACCENT_CURRENCY);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.baltop.top_balances"), panelX + 8, panelY + 8, ShopColors.TEXT_STRONG, false);

        int rowY = panelY + 28;
        for (int i = 0; i < Math.min(10, entries.size()); i++) {
            BalanceTopEntry entry = entries.get(i);
            int y = rowY + i * 22;
            graphics.fill(panelX + 8, y, panelX + panelW - 8, y + 18, i % 2 == 0 ? ShopColors.SURFACE_RAISED : ShopColors.SURFACE_OVERLAY);
            ShopUiUtil.renderPlayerFace(graphics, entry.playerUuid(), panelX + 12, y + 1, 16);
            String rank = "#" + (((page - 1) * 10) + i + 1);
            int rankColor = i == 0 ? ShopColors.ACCENT_CURRENCY : (i <= 2 ? ShopColors.TEXT_STRONG : ShopColors.TEXT_FAINT);
            graphics.drawString(this.font, rank, panelX + 34, y + 5, rankColor, false);
            ShopUiUtil.renderScrollingString(graphics, this.font, entry.playerName(),
                    panelX + 56, y + 5, panelW - 162, ShopColors.TEXT_STRONG);
            String balance = formatMinorUnits(entry.balanceMinorUnits()) + " " + currencyName;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(balance, 90), panelX + panelW - 98, y + 5, ShopColors.TEXT_CURRENCY, false);
        }
        if (entries.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.futureshops.baltop.no_balance_data"), panelX + 8, panelY + 32, ShopColors.TEXT_FAINT, false);
        }
    }

    private void renderHighlights(GuiGraphics graphics) {
        int panelW = (guiW - 28) / 2;
        int panelX = guiLeft + guiW - panelW - 10;
        int panelY = guiTop + 58;
        int highlightH = Math.min(160, (guiH - 92) / 2 + 20);
        ShopUiUtil.renderCard(graphics, panelX, panelY, panelW, highlightH);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, ShopColors.ACCENT_PRIMARY);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.baltop.spotlights"), panelX + 8, panelY + 8, ShopColors.TEXT_STRONG, false);

        highlightPanelX = panelX;
        highlightPanelY = panelY;
        highlightPanelW = panelW;
        highlightPanelH = highlightH;

        int cardH = 48;
        int productH = 30;
        int gap = 6;
        highlightContentHeight = cardH + gap + cardH + gap + productH;
        int viewportY = panelY + 24;
        int viewportH = highlightH - 28;
        highlightViewportHeight = viewportH;

        int maxScroll = Math.max(0, highlightContentHeight - viewportH);
        highlightScroll = Math.max(0, Math.min(highlightScroll, maxScroll));

        int contentX = panelX + 8;
        int contentW = panelW - 16 - (maxScroll > 0 ? 8 : 0);
        int cursorY = viewportY - highlightScroll;

        graphics.enableScissor(panelX + 4, viewportY, panelX + panelW - 4, viewportY + viewportH);

        renderHighlightCard(graphics, contentX, cursorY, contentW, cardH, Component.translatable("gui.futureshops.baltop.most_transactions").getString(),
                activityLeaderUuid, activityLeaderName, Component.translatable("gui.futureshops.baltop.total_actions", activityLeaderCount).getString(), ShopColors.TEXT_BARTER_SOFT);
        cursorY += cardH + gap;

        renderHighlightCard(graphics, contentX, cursorY, contentW, cardH, Component.translatable("gui.futureshops.baltop.top_seller").getString(),
                topSellerUuid, topSellerName, Component.translatable("gui.futureshops.baltop.shop_sales", topSellerCount).getString(), ShopColors.TEXT_CURRENCY);
        cursorY += cardH + gap;

        graphics.fill(contentX, cursorY, contentX + contentW, cursorY + productH, ShopColors.SURFACE_OVERLAY);
        ShopUiUtil.drawBorder(graphics, contentX, cursorY, contentW, productH, ShopColors.BORDER_SUBTLE);
        if (!popularItemId.isBlank()) {
            ShopUiUtil.renderItemIconWithNbt(graphics, this.font, popularItemId, popularItemNbtJson, contentX + 6, cursorY + 7);
            ShopUiUtil.renderScrollingString(graphics, this.font,
                    Component.translatable("gui.futureshops.baltop.popular_item", ShopUiUtil.getItemDisplayNameWithNbt(popularItemId, popularItemNbtJson)),
                    contentX + 26, cursorY + 4, contentW - 32, ShopColors.TEXT_STRONG);
            graphics.drawString(this.font, Component.translatable("gui.futureshops.baltop.popular_stats", popularItemTrades, popularItemQuantity),
                    contentX + 26, cursorY + 16, ShopColors.TEXT_CURRENCY, false);
        } else {
            graphics.drawString(this.font, Component.translatable("gui.futureshops.baltop.no_product_data"), contentX + 6, cursorY + 10, ShopColors.TEXT_FAINT, false);
        }

        graphics.disableScissor();

        if (maxScroll > 0) {
            int trackX = panelX + panelW - 6;
            int trackY = viewportY + 2;
            int trackH = viewportH - 4;
            graphics.fill(trackX, trackY, trackX + 3, trackY + trackH, ShopColors.BORDER_DEFAULT);
            int thumbH = Math.max(8, (int) ((float) viewportH / highlightContentHeight * trackH));
            int thumbY = trackY + (int) ((float) highlightScroll / maxScroll * (trackH - thumbH));
            graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, ShopColors.ACCENT_CYAN);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (highlightViewportHeight > 0
                && mouseX >= highlightPanelX && mouseX <= highlightPanelX + highlightPanelW
                && mouseY >= highlightPanelY && mouseY <= highlightPanelY + highlightPanelH) {
            int maxScroll = Math.max(0, highlightContentHeight - highlightViewportHeight);
            if (maxScroll > 0) {
                highlightScroll = Math.max(0, Math.min(maxScroll, highlightScroll - (int) (delta * 18)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, ShopColors.ACCENT_PRIMARY);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.baltop.top_franchises"), panelX + 8, panelY + 6, ShopColors.ACCENT_PRIMARY, false);

        if (franchises.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.futureshops.baltop.no_franchises"), panelX + 8, panelY + 22, ShopColors.TEXT_FAINT, false);
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

            int nameWidth = panelW / 2 - 30;
            ShopUiUtil.renderScrollingString(graphics, this.font, f.name(),
                    panelX + 30, y + 3, nameWidth, ShopColors.TEXT_STRONG);

            // Keep the detail right-anchored but allow scrolling within its half so long leader
            // names + member counts don't truncate with a hard cut.
            Component detail = Component.translatable("gui.futureshops.baltop.franchise_detail", f.memberCount(), f.leaderName());
            ShopUiUtil.renderScrollingString(graphics, this.font, detail,
                    panelX + panelW / 2 + 4, y + 3, panelW / 2 - 10, ShopColors.TEXT_BARTER_SOFT);
        }
    }

    private void renderHighlightCard(GuiGraphics graphics, int x, int y, int width, int height, String title, UUID uuid, String name, String detail, int accent) {
        ShopUiUtil.renderPanel(graphics, x, y, width, height, ShopColors.SURFACE_RAISED, ShopColors.BORDER_SUBTLE);
        graphics.fill(x, y, x + 2, y + height, accent);
        int faceSize = Math.min(24, height - 8);
        ShopUiUtil.renderPlayerFace(graphics, uuid, x + 6, y + (height - faceSize) / 2, faceSize);
        int textX = x + faceSize + 12;
        graphics.drawString(this.font, title, textX, y + 4, ShopColors.TEXT_SECONDARY, false);
        ShopUiUtil.renderScrollingString(graphics, this.font, name,
                textX, y + 16, width - faceSize - 20, ShopColors.TEXT_PRIMARY);
        if (height > 32) {
            ShopUiUtil.renderScrollingString(graphics, this.font, detail,
                    textX, y + 28, width - faceSize - 20, accent);
        }
    }

    public void updatePage(int page, int totalPages, List<BalanceTopEntry> entries,
                           UUID activityLeaderUuid, String activityLeaderName, int activityLeaderCount,
                           UUID topSellerUuid, String topSellerName, int topSellerCount,
                           String popularItemId, int popularItemTrades, long popularItemQuantity, String popularItemNbtJson,
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
        this.popularItemNbtJson = popularItemNbtJson == null ? "" : popularItemNbtJson;
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
