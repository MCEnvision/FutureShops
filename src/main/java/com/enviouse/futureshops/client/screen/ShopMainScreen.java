package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.CatalogBarterRecipe;
import com.enviouse.futureshops.data.CatalogCategory;
import com.enviouse.futureshops.data.CatalogItem;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SOpenBalanceUiPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class ShopMainScreen extends Screen implements ShopScreenMarker {
    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private int headerH;
    private int footerH;
    private int sidebarW;
    private int stripH;
    private int gridScrollRows;
    private int sidebarScrollIdx;
    private int selectedCategoryIdx;
    private boolean barterMode;
    private String searchQuery = "";

    private EditBox searchField;
    private Button cartBtn;
    private Button historyBtn;
    private Button modeBtn;

    private List<CatalogItem> filteredItems = List.of();
    private List<Component> tooltipLines = List.of();

    public ShopMainScreen() {
        super(Component.translatable("gui.futureshops.shop.title"));
    }

    @Override
    protected void init() {
        guiW = Math.min(640, Math.max(400, this.width - 20));
        guiH = Math.min(400, Math.max(280, this.height - 20));
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;
        headerH = 44;
        footerH = 36;
        stripH = 26;
        sidebarW = Math.min(130, Math.max(100, guiW / 5));

        rebuildFilteredItems();

        // ═══ Top bar: Search + buttons all at same height ═══
        int topBarY = guiTop + 14;
        int topBarH = 16;
        int closeW = 20;
        int closePad = 4;
        // Buttons from right to left: Close | History | Cart | Mode | Search
        int btnRightEdge = guiLeft + guiW - 8;

        // Close button (rightmost)
        addRenderableWidget(Button.builder(Component.literal("§c✕"), button -> onClose())
                .bounds(btnRightEdge - closeW, topBarY - 2, closeW, closeW)
                .build());
        btnRightEdge -= closeW + closePad;

        // History button
        historyBtn = addRenderableWidget(Button.builder(Component.literal("History"), button -> this.minecraft.setScreen(new TransactionHistoryScreen(this)))
                .bounds(btnRightEdge - 58, topBarY, 58, topBarH)
                .build());
        btnRightEdge -= 58 + closePad;

        // Cart button
        cartBtn = addRenderableWidget(Button.builder(Component.literal("Cart"), button -> this.minecraft.setScreen(new CartScreen(this)))
                .bounds(btnRightEdge - 58, topBarY, 58, topBarH)
                .build());
        btnRightEdge -= 58 + closePad;

        // Mode toggle
        modeBtn = addRenderableWidget(Button.builder(Component.literal(barterMode ? "§d⚒ Barter" : "§a$ Buy"), button -> {
                    barterMode = !barterMode;
                    if (!barterMode && isBarterTabSelected()) {
                        selectedCategoryIdx = 0;
                    }
                    button.setMessage(Component.literal(barterMode ? "§d⚒ Barter" : "§a$ Buy"));
                    gridScrollRows = 0;
                    rebuildFilteredItems();
                })
                .bounds(btnRightEdge - 56, topBarY, 56, topBarH)
                .build());
        btnRightEdge -= 56 + closePad;

        // Search field fills remaining space
        int searchX = guiLeft + sidebarW + 24;
        int searchW = Math.max(80, btnRightEdge - searchX - closePad);
        searchField = new EditBox(this.font, searchX, topBarY, searchW, topBarH,
                Component.translatable("gui.futureshops.shop.search"));
        searchField.setMaxLength(32);
        searchField.setResponder(query -> {
            searchQuery = query.toLowerCase(Locale.ROOT);
            gridScrollRows = 0;
            rebuildFilteredItems();
        });
        addRenderableWidget(searchField);
    }

    private void rebuildFilteredItems() {
        List<CatalogCategory> cats = ShopClientState.getCatalogCategories();
        List<CatalogItem> all = ShopClientState.getCatalogItems();
        String activeCategoryId = null;
        if (selectedCategoryIdx > 0 && selectedCategoryIdx <= cats.size()) {
            activeCategoryId = cats.get(selectedCategoryIdx - 1).id();
        }
        final String categoryFilter = activeCategoryId;
        boolean barterOnly = barterMode || isBarterTabSelected();
        filteredItems = all.stream()
                .filter(item -> !barterOnly || item.hasBarterRecipes())
                .filter(item -> categoryFilter == null || categoryFilter.equals(item.categoryId()))
                .filter(item -> searchQuery.isBlank()
                        || item.displayName().toLowerCase(Locale.ROOT).contains(searchQuery)
                        || item.itemId().toLowerCase(Locale.ROOT).contains(searchQuery))
                .collect(Collectors.toList());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        tooltipLines = List.of();
        // Full-screen dim
        graphics.fill(0, 0, this.width, this.height, ShopColors.BG_PRIMARY);
        // Main panel
        ShopUiUtil.renderAccentPanel(graphics, guiLeft, guiTop, guiW, guiH,
                ShopColors.BG_PANEL, ShopColors.BORDER_DEFAULT, ShopColors.ACCENT_CYAN);

        renderHeader(graphics);
        renderSidebar(graphics, mouseX, mouseY);
        renderGrid(graphics, mouseX, mouseY);
        renderStrip(graphics);
        renderFooter(graphics, mouseX, mouseY);
        cartBtn.setMessage(Component.literal("Cart (" + ShopClientState.getCartLineCount() + ")"));

        super.render(graphics, mouseX, mouseY, partialTick);
        if (!tooltipLines.isEmpty()) {
            graphics.renderTooltip(this.font, tooltipLines, Optional.empty(), mouseX, mouseY);
        }
    }

    private void renderHeader(GuiGraphics graphics) {
        int hx = guiLeft + 8;
        int hy = guiTop + 6;
        int hw = guiW - 16;
        // Gradient header bar
        ShopUiUtil.drawGradientH(graphics, hx, hy, hw, headerH - 10, ShopColors.HEADER_GRADIENT_L, ShopColors.HEADER_GRADIENT_R);
        ShopUiUtil.drawBorder(graphics, hx, hy, hw, headerH - 10, ShopColors.BORDER_DEFAULT);

        String shopTitle = prettyName(ShopClientState.getActiveShopId());
        // Truncate & wrap shop title
        graphics.drawString(this.font, this.font.plainSubstrByWidth(shopTitle, sidebarW - 10), hx + 10, hy + 6, ShopColors.TEXT_PRIMARY, true);
        String subtitle = barterMode ? "§d⚒ Barter catalog" : "§7Browse the storefront";
        graphics.drawString(this.font, this.font.plainSubstrByWidth(subtitle, sidebarW - 10), hx + 10, hy + 18, ShopColors.TEXT_SECONDARY, false);
    }

    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = guiLeft + 8;
        int y = guiTop + headerH + 2;
        int h = guiH - headerH - stripH - footerH - 14;
        ShopUiUtil.renderAccentPanel(graphics, x, y, sidebarW, h,
                ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT, ShopColors.ACCENT_PURPLE);
        graphics.drawString(this.font, "§lDepartments", x + 8, y + 6, ShopColors.TEXT_PRIMARY, false);

        List<CatalogCategory> cats = ShopClientState.getCatalogCategories();
        int tabCount = cats.size() + 1 + (hasBarterTab() ? 1 : 0);
        int tabHeight = 18;
        int maxVisible = Math.max(1, (h - 24) / tabHeight);
        sidebarScrollIdx = Math.max(0, Math.min(sidebarScrollIdx, Math.max(0, tabCount - maxVisible)));
        int drawY = y + 22;
        for (int i = sidebarScrollIdx; i < tabCount && i < sidebarScrollIdx + maxVisible; i++) {
            boolean selected = i == selectedCategoryIdx;
            boolean hovered = mouseX >= x + 4 && mouseX <= x + sidebarW - 4 && mouseY >= drawY && mouseY <= drawY + tabHeight - 2;
            int tabBg = selected ? ShopColors.BG_CARD_HOVER : (hovered ? 0xFF222222 : ShopColors.BG_PANEL);
            graphics.fill(x + 4, drawY, x + sidebarW - 4, drawY + tabHeight - 2, tabBg);
            if (selected) {
                graphics.fill(x + 4, drawY, x + 7, drawY + tabHeight - 2, ShopColors.ACCENT_CYAN);
            }
            String label = this.font.plainSubstrByWidth(labelForTab(i, cats.size()), sidebarW - 22);
            graphics.drawString(this.font, label, x + 12, drawY + 4, selected ? ShopColors.TEXT_PRIMARY : ShopColors.TEXT_SECONDARY, false);
            drawY += tabHeight;
        }
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int gridX = guiLeft + sidebarW + 16;
        int gridY = guiTop + headerH + 2;
        int gridW = guiW - sidebarW - 24;
        int gridH = guiH - headerH - stripH - footerH - 14;
        ShopUiUtil.renderPanel(graphics, gridX, gridY, gridW, gridH, ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT);

        if (filteredItems.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.shop.no_items"), gridX + gridW / 2, gridY + gridH / 2 - 8, ShopColors.TEXT_SECONDARY);
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.shop.no_items_hint"), gridX + gridW / 2, gridY + gridH / 2 + 6, ShopColors.TEXT_SECONDARY);
            return;
        }

        int contentX = gridX + 8;
        int contentY = gridY + 8;
        int contentW = gridW - 16;
        int contentH = gridH - 16;
        int gap = 6;
        int columns = Math.max(2, Math.min(5, (contentW + gap) / 88));
        int cardW = Math.max(76, Math.min(100, (contentW - gap * (columns - 1)) / columns));
        int cardH = 82;
        int visibleRows = Math.max(1, (contentH + gap) / (cardH + gap));
        int totalRows = (filteredItems.size() + columns - 1) / columns;
        gridScrollRows = Math.max(0, Math.min(gridScrollRows, Math.max(0, totalRows - visibleRows)));

        for (int index = 0; index < filteredItems.size(); index++) {
            int row = index / columns;
            if (row < gridScrollRows || row >= gridScrollRows + visibleRows) {
                continue;
            }
            int visibleRow = row - gridScrollRows;
            int col = index % columns;
            int cardX = contentX + col * (cardW + gap);
            int cardY = contentY + visibleRow * (cardH + gap);
            renderItemCard(graphics, filteredItems.get(index), cardX, cardY, cardW, cardH, mouseX, mouseY);
        }
    }

    private void renderItemCard(GuiGraphics graphics, CatalogItem item, int x, int y, int width, int height, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        boolean outOfStock = !item.unlimited() && item.stock() <= 0;
        int borderColor = hovered ? ShopColors.ACCENT_CYAN : ShopColors.BORDER_DEFAULT;
        ShopUiUtil.renderPanel(graphics, x, y, width, height,
                hovered ? ShopColors.BG_CARD_HOVER : ShopColors.BG_PANEL, borderColor);

        // Item icon
        ShopUiUtil.renderItemIcon(graphics, this.font, item.itemId(), x + (width - 16) / 2, y + 6);

        // Name — truncated to fit
        String name = this.font.plainSubstrByWidth(item.displayName(), width - 8);
        graphics.drawCenteredString(this.font, name, x + width / 2, y + 28, ShopColors.TEXT_PRIMARY);

        // Price
        long price = item.hasPromo() ? item.promoPrice() : item.buyPrice();
        String priceStr = ShopUiUtil.formatMinorUnits(price);
        graphics.drawCenteredString(this.font, "§a" + priceStr, x + width / 2, y + 42, ShopColors.TEXT_PRICE);

        // Stock line — truncated
        String stockStr = item.unlimited() ? "∞ Stock" : item.stock() + " left";
        graphics.drawCenteredString(this.font, stockStr, x + width / 2, y + 56,
                outOfStock ? ShopColors.ERROR : ShopColors.TEXT_SECONDARY);

        // Animated discount badge
        if (item.hasPromo()) {
            int percent = ShopUiUtil.computePromoPercent(item.buyPrice(), item.promoPrice());
            if (percent > 0) {
                ShopUiUtil.renderAnimatedDiscountBadge(graphics, this.font, x + width - 6, y + 8, "-" + percent + "%");
            }
        } else if (item.hasBarterRecipes()) {
            ShopUiUtil.drawChip(graphics, this.font, x + width - 44, y + height - 14, "⚒ Barter",
                    ShopColors.BG_PANEL, ShopColors.TEXT_BARTER, ShopColors.TEXT_BARTER);
        }

        // Tooltip on hover
        if (hovered && !outOfStock) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("§f" + item.displayName()));
            if (item.hasPromo()) {
                lines.add(Component.literal("§cPromo: " + ShopUiUtil.formatMinorUnits(item.promoPrice())));
            }
            List<CatalogBarterRecipe> recipes = ShopClientState.getBarterRecipesForItem(item.itemId());
            if (!recipes.isEmpty()) {
                lines.add(ShopUiUtil.buildFirstIngredientSummary(recipes));
            }
            tooltipLines = lines;
        }

        // Out of stock overlay
        if (outOfStock) {
            graphics.fill(x, y, x + width, y + height, 0x88000000);
            graphics.drawCenteredString(this.font, "§cSold Out", x + width / 2, y + height / 2 - 4, ShopColors.ERROR);
        }
    }

    private void renderStrip(GuiGraphics graphics) {
        int x = guiLeft + 8;
        int y = guiTop + guiH - footerH - stripH - 2;
        int w = guiW - 16;
        ShopUiUtil.renderPanel(graphics, x, y, w, stripH, ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT);

        // Balance with coin icon
        String balance = "§6⛃ §a" + ShopUiUtil.formatMinorUnits(ShopClientState.getCurrentBalanceMinorUnits());
        graphics.drawString(this.font, balance, x + 8, y + 9, ShopColors.TEXT_PRICE, false);

        // Item count & cart info — truncated
        String info = filteredItems.size() + " items • " + ShopClientState.getCartTotalQuantity() + " in cart";
        String clipped = this.font.plainSubstrByWidth(info, w / 3);
        graphics.drawString(this.font, clipped, x + w - this.font.width(clipped) - 8, y + 9, ShopColors.TEXT_SECONDARY, false);

        // Status panel in middle
        ShopUiUtil.renderStatusPanel(graphics, this.font, x + 160, y + 4, Math.max(80, w - 320));
    }

    private void renderFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = guiLeft + 8;
        int y = guiTop + guiH - footerH;
        int w = guiW - 16;
        ShopUiUtil.renderPanel(graphics, x, y, w, footerH - 4, ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT);

        // Help text
        String footerText = barterMode ? "§d⚒ Barter mode active" : "§7Left click for details • Right click to quick-add";
        String clippedFooter = this.font.plainSubstrByWidth(footerText, w - 120);
        graphics.drawString(this.font, clippedFooter, x + 10, y + 6, ShopColors.TEXT_SECONDARY, false);

        // ═══ Profile button (bottom right) ═══
        int profileW = 108;
        int profileH = footerH - 8;
        int profileX = x + w - profileW - 4;
        int profileY = y + 2;
        boolean profileHovered = mouseX >= profileX && mouseX <= profileX + profileW && mouseY >= profileY && mouseY <= profileY + profileH;

        ShopUiUtil.renderPanel(graphics, profileX, profileY, profileW, profileH,
                profileHovered ? ShopColors.BG_CARD_HOVER : ShopColors.PROFILE_BG,
                profileHovered ? ShopColors.ACCENT_CYAN : ShopColors.PROFILE_BORDER);

        // Player head
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ShopUiUtil.renderPlayerFace(graphics, mc.player.getUUID(), profileX + 3, profileY + 3, profileH - 6);
            // Player name — truncated
            String playerName = this.font.plainSubstrByWidth(mc.player.getGameProfile().getName(), profileW - profileH - 10);
            graphics.drawString(this.font, playerName, profileX + profileH, profileY + 3, ShopColors.TEXT_PRIMARY, false);
            // Balance
            String bal = "§a" + ShopUiUtil.formatMinorUnits(ShopClientState.getCurrentBalanceMinorUnits());
            String clippedBal = this.font.plainSubstrByWidth(bal, profileW - profileH - 10);
            graphics.drawString(this.font, clippedBal, profileX + profileH, profileY + 14, ShopColors.TEXT_PRICE, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int sidebarX = guiLeft + 8;
        int contentY = guiTop + headerH + 2;
        int contentH = guiH - headerH - stripH - footerH - 14;
        if (mouseX >= sidebarX && mouseX <= sidebarX + sidebarW && mouseY >= contentY && mouseY <= contentY + contentH) {
            sidebarScrollIdx = Math.max(0, sidebarScrollIdx - (int) delta);
            return true;
        }
        int gridX = guiLeft + sidebarW + 16;
        int gridW = guiW - sidebarW - 24;
        if (mouseX >= gridX && mouseX <= gridX + gridW && mouseY >= contentY && mouseY <= contentY + contentH) {
            gridScrollRows = Math.max(0, gridScrollRows - (int) delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // ═══ Profile button click ═══
        int footerX = guiLeft + 8;
        int footerY = guiTop + guiH - footerH;
        int footerW = guiW - 16;
        int profileW = 108;
        int profileH = footerH - 8;
        int profileX = footerX + footerW - profileW - 4;
        int profileY = footerY + 2;
        if (mouseX >= profileX && mouseX <= profileX + profileW && mouseY >= profileY && mouseY <= profileY + profileH) {
            ShopPackets.CHANNEL.sendToServer(new C2SOpenBalanceUiPacket());
            return true;
        }

        // Sidebar clicks
        int sidebarX = guiLeft + 8;
        int sidebarY = guiTop + headerH + 2;
        int sidebarH = guiH - headerH - stripH - footerH - 14;
        if (mouseX >= sidebarX && mouseX <= sidebarX + sidebarW && mouseY >= sidebarY + 22 && mouseY <= sidebarY + sidebarH) {
            List<CatalogCategory> cats = ShopClientState.getCatalogCategories();
            int tabCount = cats.size() + 1 + (hasBarterTab() ? 1 : 0);
            int tabHeight = 18;
            int maxVisible = Math.max(1, (sidebarH - 24) / tabHeight);
            for (int i = sidebarScrollIdx; i < tabCount && i < sidebarScrollIdx + maxVisible; i++) {
                int y = sidebarY + 22 + (i - sidebarScrollIdx) * tabHeight;
                if (mouseY >= y && mouseY <= y + tabHeight - 2) {
                    selectedCategoryIdx = i;
                    if (isBarterTabIndex(i, cats.size())) {
                        barterMode = true;
                        modeBtn.setMessage(Component.literal("§d⚒ Barter"));
                    }
                    gridScrollRows = 0;
                    rebuildFilteredItems();
                    return true;
                }
            }
        }

        // Grid clicks
        int gridX = guiLeft + sidebarW + 16;
        int gridY = guiTop + headerH + 2;
        int gridW = guiW - sidebarW - 24;
        int gridH = guiH - headerH - stripH - footerH - 14;
        int contentX = gridX + 8;
        int contentY = gridY + 8;
        int contentW = gridW - 16;
        int contentH = gridH - 16;
        int gap = 6;
        int columns = Math.max(2, Math.min(5, (contentW + gap) / 88));
        int cardW = Math.max(76, Math.min(100, (contentW - gap * (columns - 1)) / columns));
        int cardH = 82;
        int visibleRows = Math.max(1, (contentH + gap) / (cardH + gap));

        if (mouseX >= gridX && mouseX <= gridX + gridW && mouseY >= gridY && mouseY <= gridY + gridH) {
            for (int index = 0; index < filteredItems.size(); index++) {
                int row = index / columns;
                if (row < gridScrollRows || row >= gridScrollRows + visibleRows) {
                    continue;
                }
                int visibleRow = row - gridScrollRows;
                int col = index % columns;
                int x = contentX + col * (cardW + gap);
                int y = contentY + visibleRow * (cardH + gap);
                if (mouseX >= x && mouseX <= x + cardW && mouseY >= y && mouseY <= y + cardH) {
                    CatalogItem item = filteredItems.get(index);
                    if (button == 0) {
                        Minecraft.getInstance().setScreen(new ItemDetailScreen(this, item.itemId()));
                    } else if (button == 1 && item.buyPrice() > 0L) {
                        ShopClientState.addToCart(item.itemId(), 1);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean hasBarterTab() {
        return ShopClientState.getCatalogItems().stream().anyMatch(CatalogItem::hasBarterRecipes);
    }

    private boolean isBarterTabSelected() {
        return hasBarterTab() && isBarterTabIndex(selectedCategoryIdx, ShopClientState.getCatalogCategories().size());
    }

    private boolean isBarterTabIndex(int index, int categoryCount) {
        return hasBarterTab() && index == categoryCount + 1;
    }

    private String labelForTab(int index, int categoryCount) {
        if (index == 0) {
            return "All";
        }
        if (isBarterTabIndex(index, categoryCount)) {
            return "⚒ Barter";
        }
        return ShopClientState.getCatalogCategories().get(index - 1).displayName();
    }

    private String prettyName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Server Shop";
        }
        String[] parts = raw.replace('_', ' ').replace('-', ' ').split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.isEmpty() ? "Server Shop" : builder.toString();
    }
}
