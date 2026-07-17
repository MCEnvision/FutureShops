package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.OwnedShopSummary;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SFranchiseActionPacket;
import com.enviouse.futureshops.network.packets.C2SOpenBalTopUiPacket;
import com.enviouse.futureshops.network.packets.C2SOpenAtmPacket;
import com.enviouse.futureshops.network.packets.C2SOpenShopPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopActionPacket;
import net.minecraft.client.gui.GuiGraphics;
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
    private final Screen parent;

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private int shopScroll;
    private int alertScroll;
    private int visibleShopCards;
    private int visibleAlerts;

    /** Per-frame flat-button hit regions, populated in {@link #render}, consulted in mouseClicked. */
    private final java.util.List<ShopUiUtil.ClickZone> clickZones = new java.util.ArrayList<>();

    public BalanceOverviewScreen(Screen parent, UUID playerUuid, String playerName, long balanceMinorUnits, String currencyName, int currencyDecimals,
                                 long totalRevenueMinor, long pendingSettlementMinor, int shopCount, int listingCount,
                                 int totalStock, int lowSupplyCount, List<OwnedShopSummary> shopSummaries, List<String> alerts) {
        super(Component.translatable("gui.futureshops.balance.title"));
        this.parent = parent;
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
        guiW = Math.max(360, this.width - 4);
        guiH = Math.max(240, this.height - 4);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;
        visibleShopCards = Math.max(2, (guiH - 200) / 52);
        visibleAlerts = Math.max(1, (guiH - 200) / 18);
        // Footer nav buttons are drawn immediate-mode in render().
    }

    /** Draws the footer nav buttons and registers their click zones for this frame. */
    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = guiTop + guiH - 24;
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 10, y, 68, 18,
                Component.translatable("gui.futureshops.local.back"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, this::onClose);
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 82, y, 62, 18,
                Component.translatable("gui.futureshops.balance.atm"),
                ShopUiUtil.ButtonStyle.PRIMARY, true,
                () -> ShopPackets.CHANNEL.sendToServer(new C2SOpenAtmPacket()));
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 214, y, 68, 18,
                Component.translatable("gui.futureshops.balance.franchise"),
                ShopUiUtil.ButtonStyle.SECONDARY, true,
                () -> ShopPackets.CHANNEL.sendToServer(new C2SFranchiseActionPacket("OPEN", "")));
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 142, y, 60, 18,
                Component.translatable("gui.futureshops.balance.leaders"),
                ShopUiUtil.ButtonStyle.SECONDARY, true,
                () -> ShopPackets.CHANNEL.sendToServer(new C2SOpenBalTopUiPacket(1)));
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 78, y, 60, 18,
                Component.translatable("gui.futureshops.balance.close"),
                ShopUiUtil.ButtonStyle.SECONDARY, true,
                () -> ShopPackets.CHANNEL.sendToServer(new C2SOpenShopPacket("default")));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();
        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + 2, ShopColors.ACCENT_CURRENCY);

        renderHeader(graphics);
        renderMetrics(graphics);
        renderOwnedShops(graphics, mouseX, mouseY);
        renderAlerts(graphics, mouseX, mouseY);
        renderButtons(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics) {
        ShopUiUtil.renderElevatedCard(graphics, guiLeft + 10, guiTop + 10, guiW - 20, 56);
        ShopUiUtil.renderPlayerFace(graphics, playerUuid, guiLeft + 18, guiTop + 18, 40);
        graphics.drawString(this.font, this.title, guiLeft + 68, guiTop + 18, ShopColors.TEXT_STRONG, false);
        ShopUiUtil.renderScrollingString(graphics, this.font, playerName,
                guiLeft + 68, guiTop + 32, 132, ShopColors.TEXT_MUTED);
        String balanceText = formatMinorUnits(balanceMinorUnits) + " " + currencyName;
        graphics.drawString(this.font, balanceText, guiLeft + guiW - this.font.width(balanceText) - 18, guiTop + 24, ShopColors.TEXT_CURRENCY, false);
        boolean warn = lowSupplyCount > 0;
        ShopUiUtil.renderPill(graphics, this.font, guiLeft + guiW - 112, guiTop + 44,
                warn ? Component.translatable("gui.futureshops.balance.low_stock", lowSupplyCount).getString()
                     : Component.translatable("gui.futureshops.balance.supply_stable").getString(),
                ShopColors.SURFACE_OVERLAY,
                warn ? ShopColors.STATUS_DANGER : ShopColors.STATUS_SUCCESS,
                warn ? ShopColors.STATUS_DANGER : ShopColors.STATUS_SUCCESS);
    }

    private void renderMetrics(GuiGraphics graphics) {
        int cardY = guiTop + 74;
        int gap = 8;
        int cardW = (guiW - 20 - (gap * 3)) / 4;
        renderMetricCard(graphics, guiLeft + 10, cardY, cardW, Component.translatable("gui.futureshops.balance.metric.revenue").getString(), formatMinorUnits(totalRevenueMinor), ShopColors.TEXT_CURRENCY);
        renderMetricCard(graphics, guiLeft + 10 + (cardW + gap), cardY, cardW, Component.translatable("gui.futureshops.balance.metric.pending").getString(), formatMinorUnits(pendingSettlementMinor), ShopColors.TEXT_BARTER_SOFT);
        renderShopsListingsCard(graphics, guiLeft + 10 + (cardW + gap) * 2, cardY, cardW);
        renderMetricCard(graphics, guiLeft + 10 + (cardW + gap) * 3, cardY, cardW, Component.translatable("gui.futureshops.balance.metric.supply").getString(), Component.translatable("gui.futureshops.balance.items_tracked", totalStock).getString(), ShopColors.TEXT_MUTED);
    }

    private void renderMetricCard(GuiGraphics graphics, int x, int y, int width, String title, String value, int accent) {
        ShopUiUtil.renderCard(graphics, x, y, width, 44);
        // Top accent rule in the supplied color
        graphics.fill(x, y, x + width, y + 2, accent);
        graphics.drawString(this.font, title.toUpperCase(), x + 8, y + 8, ShopColors.TEXT_FAINT, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(value, width - 16), x + 8, y + 22, accent, false);
    }

    /**
     * Redesigned Shops / Listings metric card. The original "3 / 2 listings" caption read
     * like a limit ratio; users kept asking whether they were capped. New layout uses a
     * split "SHOPS │ LISTINGS" header with the two numbers underneath so each count is
     * unambiguously tied to its label.
     */
    private void renderShopsListingsCard(GuiGraphics graphics, int x, int y, int width) {
        ShopUiUtil.renderCard(graphics, x, y, width, 44);
        graphics.fill(x, y, x + width, y + 2, ShopColors.ACCENT_PRIMARY);
        int midX = x + width / 2;
        // Thin vertical separator between the two columns.
        graphics.fill(midX, y + 6, midX + 1, y + 40, ShopColors.BORDER_MUTED);

        // Center the label + value pair in each half so the SHOPS and LISTINGS halves look
        // like matching columns (labels above numbers, both axis-aligned to the sub-column center)
        // instead of label-left / label-right with numbers floating in between.
        String shopsLabel = Component.translatable("gui.futureshops.balance.shops_label").getString();
        String listingsLabel = Component.translatable("gui.futureshops.balance.listings_label").getString();
        String shopsVal = Integer.toString(shopCount);
        String listingsVal = Integer.toString(listingCount);

        int leftCenter = x + width / 4;
        int rightCenter = x + (3 * width) / 4;

        graphics.drawString(this.font, shopsLabel, leftCenter - this.font.width(shopsLabel) / 2, y + 8, ShopColors.TEXT_FAINT, false);
        graphics.drawString(this.font, listingsLabel, rightCenter - this.font.width(listingsLabel) / 2, y + 8, ShopColors.TEXT_FAINT, false);
        graphics.drawString(this.font, shopsVal, leftCenter - this.font.width(shopsVal) / 2, y + 22, ShopColors.ACCENT_PRIMARY, false);
        graphics.drawString(this.font, listingsVal, rightCenter - this.font.width(listingsVal) / 2, y + 22, ShopColors.ACCENT_PRIMARY, false);
    }

    private void renderOwnedShops(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = guiLeft + 10;
        int panelY = guiTop + 126;
        int panelW = (guiW - 28) / 2;
        int panelH = guiH - 160;
        ShopUiUtil.renderCard(graphics, panelX, panelY, panelW, panelH);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, ShopColors.ACCENT_PRIMARY);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.balance.placed_shops"), panelX + 8, panelY + 8, ShopColors.TEXT_STRONG, false);
        String subtitle = (shopCount == 0
                ? Component.translatable("gui.futureshops.balance.no_shops")
                : Component.translatable("gui.futureshops.balance.shops_tracked", shopCount)).getString();
        graphics.drawString(this.font, this.font.plainSubstrByWidth(subtitle, panelW - 16), panelX + 8, panelY + 20, ShopColors.TEXT_MUTED, false);

        int maxScroll = Math.max(0, shopSummaries.size() - visibleShopCards);
        shopScroll = Math.max(0, Math.min(shopScroll, maxScroll));
        if (shopSummaries.isEmpty()) {
            ShopUiUtil.drawWrappedString(graphics, this.font, Component.translatable("gui.futureshops.balance.empty_hint"), panelX + 8, panelY + 44, panelW - 16, ShopColors.TEXT_FAINT, 10);
            return;
        }

        int cardY = panelY + 36;
        graphics.enableScissor(panelX, panelY + 30, panelX + panelW, panelY + panelH - 2);
        for (int i = 0; i < visibleShopCards && i + shopScroll < shopSummaries.size(); i++) {
            OwnedShopSummary summary = shopSummaries.get(i + shopScroll);
            int y = cardY + i * 52;
            boolean hovered = mouseX >= panelX + 8 && mouseX <= panelX + panelW - 8 && mouseY >= y && mouseY <= y + 46;
            ShopUiUtil.renderPanel(graphics, panelX + 8, y, panelW - 16, 46,
                    hovered ? ShopColors.SURFACE_OVERLAY : ShopColors.SURFACE_RAISED,
                    hovered ? ShopColors.BORDER_GLOW : ShopColors.BORDER_MUTED);
            if (hovered) {
                graphics.fill(panelX + 8, y, panelX + panelW - 8, y + 1, ShopColors.ACCENT_PRIMARY);
            }
            if (!summary.featuredItemId().isBlank()) {
                ShopUiUtil.renderItemIconWithNbt(graphics, this.font, summary.featuredItemId(), summary.featuredNbtJson(), panelX + 14, y + 14);
            }
            BlockPos pos = BlockPos.of(summary.shopPosLong());
            Component title = Component.translatable(
                    summary.listingCount() == 1 ? "gui.futureshops.balance.shop_card.one" : "gui.futureshops.balance.shop_card.many",
                    summary.listingCount(), displayDimension(summary.dimensionKey()));
            // A low/linked pill is drawn at panelX + panelW - 88; when one is present, clip the
            // title so it ends ~6px before the pill instead of running underneath it.
            boolean hasPill = summary.lowStockListings() > 0 || summary.linked();
            int titleW = hasPill ? (panelW - 130) : (panelW - 76);
            ShopUiUtil.renderScrollingString(graphics, this.font, title,
                    panelX + 36, y + 6, titleW, ShopColors.TEXT_STRONG);
            graphics.drawString(this.font, pos.getX() + ", " + pos.getY() + ", " + pos.getZ(), panelX + 36, y + 18, ShopColors.TEXT_MUTED, false);
            // Clip the stats line so it stops short of the "▶ Click to visit" CTA on hover.
            String stats = Component.translatable("gui.futureshops.balance.shop_card.stats", summary.totalStock(), formatMinorUnits(summary.lifetimeMinor())).getString();
            graphics.drawString(this.font, this.font.plainSubstrByWidth(stats, panelW - 125), panelX + 36, y + 30, ShopColors.TEXT_CURRENCY, false);
            if (summary.lowStockListings() > 0) {
                // Item icons in owned-shop cards render at z≈+150 internally; lift pills above
                // that layer so the "low"/"linked" badges can't be occluded by a wide icon.
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 200.0F);
                ShopUiUtil.renderPill(graphics, this.font, panelX + panelW - 88, y + 6, Component.translatable("gui.futureshops.balance.pill.low", summary.lowStockListings()).getString(),
                        ShopColors.SURFACE_BASE, ShopColors.STATUS_DANGER, ShopColors.STATUS_DANGER);
                graphics.pose().popPose();
            } else if (summary.linked()) {
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 200.0F);
                ShopUiUtil.renderPill(graphics, this.font, panelX + panelW - 88, y + 6, Component.translatable("gui.futureshops.balance.pill.linked").getString(),
                        ShopColors.SURFACE_BASE, ShopColors.STATUS_INFO, ShopColors.STATUS_INFO);
                graphics.pose().popPose();
            }
            if (hovered) {
                String visitText = Component.translatable("gui.futureshops.balance.click_to_visit").getString();
                int vw = this.font.width(visitText);
                graphics.drawString(this.font, visitText, panelX + panelW - vw - 14, y + 34, ShopColors.ACCENT_PRIMARY, false);
            }
        }
        graphics.disableScissor();
    }

    private void renderAlerts(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelW = (guiW - 28) / 2;
        int panelX = guiLeft + guiW - panelW - 10;
        int panelY = guiTop + 126;
        int panelH = guiH - 160;
        ShopUiUtil.renderCard(graphics, panelX, panelY, panelW, panelH);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, ShopColors.STATUS_WARNING);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.balance.supply_warnings"), panelX + 8, panelY + 8, ShopColors.TEXT_STRONG, false);
        graphics.drawString(this.font, alerts.isEmpty()
                ? Component.translatable("gui.futureshops.balance.healthy")
                : Component.translatable("gui.futureshops.balance.attention_points", alerts.size()), panelX + 8, panelY + 20, ShopColors.TEXT_MUTED, false);

        int maxScroll = Math.max(0, alerts.size() - visibleAlerts);
        alertScroll = Math.max(0, Math.min(alertScroll, maxScroll));
        if (alerts.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.futureshops.balance.no_warnings"), panelX + 8, panelY + 44, ShopColors.STATUS_SUCCESS, false);
            return;
        }

        int lineY = panelY + 40;
        graphics.enableScissor(panelX, panelY + 36, panelX + panelW, panelY + panelH - 2);
        for (int i = 0; i < visibleAlerts && i + alertScroll < alerts.size(); i++) {
            int y = lineY + i * 18;
            graphics.fill(panelX + 8, y, panelX + panelW - 8, y + 16, i % 2 == 0 ? ShopColors.SURFACE_RAISED : ShopColors.SURFACE_OVERLAY);
            graphics.fill(panelX + 8, y, panelX + 10, y + 16, ShopColors.STATUS_DANGER);
            // Use a scrolling (ping-pong) string instead of hard-clipping so long alert lines —
            // e.g. "<shop name> low at <dimension> <x, y, z>" — never swallow the coordinates.
            ShopUiUtil.renderScrollingString(graphics, this.font, alerts.get(i + alertScroll),
                    panelX + 14, y + 4, panelW - 24, ShopColors.TEXT_STRONG);
        }
        graphics.disableScissor();
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Flat Nocturne footer buttons take priority over owned-shop card clicks.
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
            return true;
        }
        // Item 9: Clickable owned shop cards → navigate to shop manage view
        int panelX = guiLeft + 10;
        int panelY = guiTop + 126;
        int panelW = (guiW - 28) / 2;
        int cardY = panelY + 36;
        for (int i = 0; i < visibleShopCards && i + shopScroll < shopSummaries.size(); i++) {
            int y = cardY + i * 52;
            if (mouseX >= panelX + 8 && mouseX <= panelX + panelW - 8 && mouseY >= y && mouseY <= y + 46) {
                OwnedShopSummary summary = shopSummaries.get(i + shopScroll);
                BlockPos pos = BlockPos.of(summary.shopPosLong());
                ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopActionPacket(pos, "VISIT", 0, 0));
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

    /** The shop screen this dashboard returns to (used to keep sibling overviews from becoming the parent). */
    public net.minecraft.client.gui.screens.Screen getParent() {
        return parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
