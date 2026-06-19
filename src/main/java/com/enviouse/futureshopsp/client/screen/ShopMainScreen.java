package com.enviouse.futureshopsp.client.screen;

import com.enviouse.futureshopsp.client.ShopClientState;
import com.enviouse.futureshopsp.client.ShopColors;
import com.enviouse.futureshopsp.data.CatalogBarterRecipe;
import com.enviouse.futureshopsp.data.CatalogCategory;
import com.enviouse.futureshopsp.data.CatalogItem;
import com.enviouse.futureshopsp.data.LocalShopOwnerEntry;
import com.enviouse.futureshopsp.data.NearbyShopEntry;
import com.enviouse.futureshopsp.network.ShopPackets;
import com.enviouse.futureshopsp.network.packets.C2SFetchLocalShopsPacket;
import com.enviouse.futureshopsp.network.packets.C2SOpenBalanceUiPacket;
import com.enviouse.futureshopsp.network.packets.C2SOpenShopPacket;
import com.enviouse.futureshopsp.network.packets.C2SPlayerShopActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private boolean nearbyMode;
    private int nearbyScrollIdx;
    private String searchQuery = "";

    private EditBox searchField;
    private Button cartBtn;
    private Button historyBtn;
    private Button modeBtn;

    private List<CatalogItem> filteredItems = List.of();
    // Item 6: advanced tooltip tracking
    private String tooltipItemId = null;
    private String tooltipNbtJson = null;
    private int tooltipMouseX;
    private int tooltipMouseY;
    // Cache to skip per-frame Component.literal allocation for cart button label.
    private int cachedCartLineCount = -1;

    public ShopMainScreen() {
        super(Component.translatable("gui.futureshops.shop.title"));
    }

    @Override
    protected void init() {
        // Full-screen layout — use almost all available pixels
        guiW = Math.max(300, this.width - 4);
        guiH = Math.max(200, this.height - 4);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;
        headerH = guiH < 260 ? 32 : 44;
        footerH = guiH < 260 ? 26 : 36;
        stripH = guiH < 260 ? 18 : 26;
        sidebarW = Math.min(130, Math.max(80, guiW / 5));

        rebuildFilteredItems();

        // ═══ Top bar: Search + buttons all at same height ═══
        int topBarY = guiTop + 14;
        int topBarH = 16;
        int closeW = 20;
        int closePad = 4;
        // Tight mode collapses button labels to icons so the search field always gets a usable width
        boolean tight = guiW < 520;
        int historyW = tight ? 22 : 58;
        int cartW = tight ? 28 : 58;
        int modeW = tight ? 24 : 56;
        int localW = tight ? 24 : 50;
        int pad = tight ? 2 : closePad;
        // Buttons from right to left: Close | History | Cart | Mode | Local | Search
        int btnRightEdge = guiLeft + guiW - 8;

        // Close button (rightmost)
        addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.shop_main.close"), button -> onClose())
                .bounds(btnRightEdge - closeW, topBarY - 2, closeW, closeW)
                .build());
        btnRightEdge -= closeW + pad;


        // History button
        historyBtn = addRenderableWidget(Button.builder(
                        tight ? Component.literal("§7🕑")
                              : Component.translatable("gui.futureshops.shop_main.history"),
                        button -> this.minecraft.setScreen(new TransactionHistoryScreen(this)))
                .tooltip(tight ? net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.futureshops.shop_main.history")) : null)
                .bounds(btnRightEdge - historyW, topBarY, historyW, topBarH)
                .build());
        btnRightEdge -= historyW + pad;

        // Cart button
        cartBtn = addRenderableWidget(Button.builder(
                        tight ? Component.literal("§6🛒")
                              : Component.translatable("gui.futureshops.shop_main.cart"),
                        button -> this.minecraft.setScreen(new CartScreen(this)))
                .tooltip(tight ? net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.futureshops.shop_main.cart")) : null)
                .bounds(btnRightEdge - cartW, topBarY, cartW, topBarH)
                .build());
        btnRightEdge -= cartW + pad;

        // Mode toggle
        modeBtn = addRenderableWidget(Button.builder(modeButtonMessage(tight), button -> {
                    barterMode = !barterMode;
                    if (!barterMode && isBarterTabSelected()) {
                        selectedCategoryIdx = 0;
                    }
                    button.setMessage(modeButtonMessage(tight));
                    gridScrollRows = 0;
                    rebuildFilteredItems();
                })
                .tooltip(tight ? net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.futureshops.shop_main.mode_tooltip")) : null)
                .bounds(btnRightEdge - modeW, topBarY, modeW, topBarH)
                .build());
        btnRightEdge -= modeW + pad;

        // Local Shops button — always visible, toggles nearby player shops view
        addRenderableWidget(Button.builder(
                        tight ? Component.literal("§e📍")
                              : Component.translatable("gui.futureshops.shop_main.local"),
                        button -> {
                    nearbyMode = !nearbyMode;
                    gridScrollRows = 0;
                    nearbyScrollIdx = 0;
                    if (nearbyMode) {
                        ShopPackets.sendToServer(new C2SOpenShopPacket(ShopClientState.getActiveShopId()));
                        ShopPackets.sendToServer(new C2SFetchLocalShopsPacket(""));
                    } else {
                        rebuildFilteredItems();
                    }
                })
                .tooltip(tight ? net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.futureshops.shop_main.local")) : null)
                .bounds(btnRightEdge - localW, topBarY, localW, topBarH)
                .build());
        btnRightEdge -= localW + pad;

        // Search field fills remaining space — no lower floor so it never overlaps buttons
        int searchX = guiLeft + sidebarW + (tight ? 12 : 24);
        int searchW = Math.max(60, btnRightEdge - searchX - pad);
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

    /**
     * Called by ShopClientPacketHandler when new shop data arrives while this screen is already open.
     * Updates the catalog / nearby list without resetting UI state (nearbyMode, scroll, tabs).
     */
    public void refreshAfterDataUpdate() {
        rebuildFilteredItems();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        tooltipItemId = null;
        tooltipNbtJson = null;
        // Full-screen dim backdrop
        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        // Main panel — raised neon-glass surface with accent top rule
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + 2, ShopColors.ACCENT_PRIMARY);

        renderHeader(graphics);
        renderSidebar(graphics, mouseX, mouseY);
        renderGrid(graphics, mouseX, mouseY);
        renderStrip(graphics);
        renderFooter(graphics, mouseX, mouseY);
        int cartLineCount = ShopClientState.getCartLineCount();
        if (cartLineCount != cachedCartLineCount) {
            cachedCartLineCount = cartLineCount;
            cartBtn.setMessage(Component.translatable("gui.futureshops.shop_main.cart_count", cartLineCount));
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        // Item 6: Render advanced item tooltip (full enchants/lore) after everything else
        if (tooltipItemId != null) {
            ShopUiUtil.renderItemTooltip(graphics, this.font, tooltipItemId,
                    tooltipNbtJson != null ? tooltipNbtJson : "", tooltipMouseX, tooltipMouseY);
        }
    }

    private void renderHeader(GuiGraphics graphics) {
        int hx = guiLeft + 8;
        int hy = guiTop + 6;
        int hw = guiW - 16;
        int barH = headerH - 10;
        // Neon-glass header: gradient + subtle highlight + bottom glow rule
        graphics.fillGradient(hx, hy, hx + hw, hy + barH, ShopColors.HEADER_GRADIENT_L, ShopColors.HEADER_GRADIENT_R);
        graphics.fill(hx, hy, hx + hw, hy + 1, ShopColors.ACCENT_PRIMARY_DIM);
        graphics.fill(hx, hy + barH - 1, hx + hw, hy + barH, ShopColors.BORDER_GLOW);
        ShopUiUtil.drawBorder(graphics, hx, hy, hw, barH, ShopColors.BORDER_MUTED);

        String shopTitle = prettyName(ShopClientState.getActiveShopId());
        graphics.drawString(this.font, this.font.plainSubstrByWidth(shopTitle, sidebarW - 10), hx + 12, hy + 6, ShopColors.TEXT_STRONG, true);
        String subtitle = barterMode ? "§9⚒ Barter catalog" : "§7Browse the storefront";
        graphics.drawString(this.font, this.font.plainSubstrByWidth(subtitle, sidebarW - 10), hx + 12, hy + 18, ShopColors.TEXT_MUTED, false);
    }

    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = guiLeft + 8;
        int y = guiTop + headerH + 2;
        int h = guiH - headerH - stripH - footerH - 14;
        ShopUiUtil.renderCard(graphics, x, y, sidebarW, h);
        graphics.fill(x, y, x + sidebarW, y + 2, ShopColors.ACCENT_PRIMARY);
        graphics.drawString(this.font, "§lDepartments", x + 8, y + 6, ShopColors.TEXT_STRONG, false);

        List<CatalogCategory> cats = ShopClientState.getCatalogCategories();
        int tabCount = totalTabCount();
        int tabHeight = 18;
        int maxVisible = Math.max(1, (h - 24) / tabHeight);
        sidebarScrollIdx = Math.max(0, Math.min(sidebarScrollIdx, Math.max(0, tabCount - maxVisible)));
        int drawY = y + 22;
        for (int i = sidebarScrollIdx; i < tabCount && i < sidebarScrollIdx + maxVisible; i++) {
            boolean selected = i == selectedCategoryIdx;
            boolean hovered = mouseX >= x + 4 && mouseX <= x + sidebarW - 4 && mouseY >= drawY && mouseY <= drawY + tabHeight - 2;
            int tabBg = selected ? ShopColors.SURFACE_PRESSED : (hovered ? ShopColors.SURFACE_OVERLAY : ShopColors.SURFACE_BASE);
            graphics.fill(x + 4, drawY, x + sidebarW - 4, drawY + tabHeight - 2, tabBg);
            if (selected) {
                graphics.fill(x + 4, drawY, x + 7, drawY + tabHeight - 2, ShopColors.ACCENT_PRIMARY);
            }
            String label = this.font.plainSubstrByWidth(labelForTab(i, cats.size()), sidebarW - 22);
            graphics.drawString(this.font, label, x + 12, drawY + 4, selected ? ShopColors.TEXT_STRONG : ShopColors.TEXT_MUTED, false);
            drawY += tabHeight;
        }

        // Scroll indicators for the sidebar tabs
        ShopUiUtil.renderScrollIndicators(graphics, this.font, x, y + 20, sidebarW, h - 22, sidebarScrollIdx, maxVisible, tabCount);
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int gridX = guiLeft + sidebarW + 16;
        int gridY = guiTop + headerH + 2;
        int gridW = guiW - sidebarW - 24;
        int gridH = guiH - headerH - stripH - footerH - 14;
        ShopUiUtil.renderCard(graphics, gridX, gridY, gridW, gridH);
        graphics.fill(gridX, gridY, gridX + gridW, gridY + 2, ShopColors.ACCENT_PRIMARY);

        // ═══ Nearby shops mode ═══
        if (nearbyMode) {
            renderNearbyShopsGrid(graphics, gridX, gridY, gridW, gridH, mouseX, mouseY);
            return;
        }

        if (filteredItems.isEmpty()) {
            if (!ShopClientState.isAdminShopEnabled()) {
                graphics.drawCenteredString(this.font, "§7Admin shop is disabled", gridX + gridW / 2, gridY + gridH / 2 - 14, ShopColors.TEXT_SECONDARY);
                graphics.drawCenteredString(this.font, "§7Check the §e📍 Nearby §7tab for player shops", gridX + gridW / 2, gridY + gridH / 2 + 2, ShopColors.TEXT_SECONDARY);
            } else {
                graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.shop.no_items"), gridX + gridW / 2, gridY + gridH / 2 - 8, ShopColors.TEXT_SECONDARY);
                graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.shop.no_items_hint"), gridX + gridW / 2, gridY + gridH / 2 + 6, ShopColors.TEXT_SECONDARY);
            }
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
            boolean keySelected = index == selectedGridIndex;
            renderItemCard(graphics, filteredItems.get(index), cardX, cardY, cardW, cardH, mouseX, mouseY, keySelected);
        }

        // Scroll indicators for the item grid
        ShopUiUtil.renderScrollIndicators(graphics, this.font, gridX, gridY, gridW, gridH, gridScrollRows, visibleRows, totalRows);
    }

    private void renderNearbyShopsGrid(GuiGraphics graphics, int gridX, int gridY, int gridW, int gridH, int mouseX, int mouseY) {
        List<LocalShopOwnerEntry> owners = ShopClientState.getLocalShopOwners();

        // Fall back to legacy nearby list if aggregated data hasn't arrived yet
        if (owners.isEmpty()) {
            List<NearbyShopEntry> nearby = ShopClientState.getNearbyShops();
            if (nearby.isEmpty()) {
                graphics.drawCenteredString(this.font, "§7No player shops found nearby", gridX + gridW / 2, gridY + gridH / 2 - 8, ShopColors.TEXT_SECONDARY);
                graphics.drawCenteredString(this.font, "§7Place a shop block to get started!", gridX + gridW / 2, gridY + gridH / 2 + 6, ShopColors.TEXT_SECONDARY);
                return;
            }
            // Render legacy nearby list while waiting for aggregated data
            renderLegacyNearbyGrid(graphics, gridX, gridY, gridW, gridH, mouseX, mouseY, nearby);
            return;
        }

        // ═══ Aggregated owner/franchise view ═══
        int cardH = 52;
        int gap = 4;
        int contentX = gridX + 8;
        int contentY = gridY + 8;
        int contentW = gridW - 16;
        int contentH = gridH - 16;
        int maxVisible = Math.max(1, (contentH + gap) / (cardH + gap));
        nearbyScrollIdx = Math.max(0, Math.min(nearbyScrollIdx, Math.max(0, owners.size() - maxVisible)));

        // Header
        graphics.drawString(this.font, "§l📍 Local Shops", contentX, contentY - 2, ShopColors.TEXT_PRIMARY, false);
        int listStartY = contentY + 14;
        int listH = contentH - 14;
        maxVisible = Math.max(1, (listH + gap) / (cardH + gap));

        for (int i = nearbyScrollIdx; i < owners.size() && i < nearbyScrollIdx + maxVisible; i++) {
            LocalShopOwnerEntry owner = owners.get(i);
            int y = listStartY + (i - nearbyScrollIdx) * (cardH + gap);
            boolean hovered = mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= y && mouseY <= y + cardH;

            ShopUiUtil.renderPanel(graphics, contentX, y, contentW, cardH,
                    hovered ? ShopColors.SURFACE_OVERLAY : ShopColors.SURFACE_RAISED,
                    hovered ? ShopColors.BORDER_GLOW : ShopColors.BORDER_MUTED);
            if (hovered) {
                graphics.fill(contentX, y, contentX + contentW, y + 1, ShopColors.ACCENT_PRIMARY);
            }

            // Owner head
            ShopUiUtil.renderPlayerFace(graphics, owner.ownerUuid(), contentX + 6, y + 10, 30);

            // Display name (or franchise name)
            String displayName = owner.franchiseName().isBlank()
                    ? owner.displayName() + "'s Shop"
                    : owner.franchiseName() + " — " + owner.displayName();
            ShopUiUtil.renderScrollingString(graphics, this.font, displayName,
                    contentX + 42, y + 6, contentW - 100, ShopColors.TEXT_PRIMARY);

            // Info line
            String info = owner.shopBlockCount() + " shops • " + owner.totalListings() + " items • " +
                    owner.totalStock() + " stock • " + String.format("%.0f", owner.closestDistance()) + "m";
            ShopUiUtil.renderScrollingString(graphics, this.font, "§7" + info,
                    contentX + 42, y + 18, contentW - 60, ShopColors.TEXT_SECONDARY);

            // Department summary (precomputed when the owner list was received)
            String deptStr = "§7Depts: " + ShopClientState.getLocalShopDeptSummary(owner.ownerUuid());
            ShopUiUtil.renderScrollingString(graphics, this.font, deptStr,
                    contentX + 42, y + 30, contentW - 60, ShopColors.TEXT_SECONDARY);

            if (hovered) {
                String visitText = "§a▶ Browse";
                int vw = this.font.width(visitText);
                graphics.drawString(this.font, visitText, contentX + contentW - vw - 8, y + 18, ShopColors.ACCENT_CYAN, false);
            }
        }

        ShopUiUtil.renderScrollIndicators(graphics, this.font, gridX, gridY, gridW, gridH, nearbyScrollIdx, maxVisible, owners.size());
    }

    /** Legacy nearby grid rendering (used as fallback before aggregated data arrives). */
    private void renderLegacyNearbyGrid(GuiGraphics graphics, int gridX, int gridY, int gridW, int gridH,
                                         int mouseX, int mouseY, List<NearbyShopEntry> nearby) {
        int cardH = 48;
        int gap = 4;
        int contentX = gridX + 8;
        int contentY = gridY + 8;
        int contentW = gridW - 16;
        int contentH = gridH - 16;
        int maxVisible = Math.max(1, (contentH + gap) / (cardH + gap));
        nearbyScrollIdx = Math.max(0, Math.min(nearbyScrollIdx, Math.max(0, nearby.size() - maxVisible)));

        for (int i = nearbyScrollIdx; i < nearby.size() && i < nearbyScrollIdx + maxVisible; i++) {
            NearbyShopEntry entry = nearby.get(i);
            int y = contentY + (i - nearbyScrollIdx) * (cardH + gap);
            boolean hovered = mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= y && mouseY <= y + cardH;

            ShopUiUtil.renderPanel(graphics, contentX, y, contentW, cardH,
                    hovered ? ShopColors.SURFACE_OVERLAY : ShopColors.SURFACE_RAISED,
                    hovered ? ShopColors.BORDER_GLOW : ShopColors.BORDER_MUTED);
            if (hovered) {
                graphics.fill(contentX, y, contentX + contentW, y + 1, ShopColors.ACCENT_PRIMARY);
            }

            ShopUiUtil.renderPlayerFace(graphics, entry.ownerUuid(), contentX + 6, y + 8, 30);
            ShopUiUtil.renderScrollingString(graphics, this.font, entry.shopName(),
                    contentX + 42, y + 6, contentW - 100, ShopColors.TEXT_PRIMARY);
            ShopUiUtil.renderScrollingString(graphics, this.font, "§7by " + entry.ownerName(),
                    contentX + 42, y + 18, contentW - 100, ShopColors.TEXT_SECONDARY);
            String infoStr = entry.listingCount() + " items • " + entry.totalStock() + " stock • " + String.format("%.0f", entry.distance()) + "m away";
            ShopUiUtil.renderScrollingString(graphics, this.font, infoStr,
                    contentX + 42, y + 30, contentW - 60, ShopColors.TEXT_SECONDARY);
            if (hovered) {
                String visitText = "§a▶ Click to visit";
                int vw = this.font.width(visitText);
                graphics.drawString(this.font, visitText, contentX + contentW - vw - 8, y + 18, ShopColors.ACCENT_CYAN, false);
            }
        }
        ShopUiUtil.renderScrollIndicators(graphics, this.font, gridX, gridY, gridW, gridH, nearbyScrollIdx, maxVisible, nearby.size());
    }

    private void renderItemCard(GuiGraphics graphics, CatalogItem item, int x, int y, int width, int height, int mouseX, int mouseY, boolean keySelected) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        boolean highlighted = hovered || keySelected;
        boolean outOfStock = !item.unlimited() && item.stock() <= 0;
        int borderColor = highlighted ? ShopColors.BORDER_GLOW : ShopColors.BORDER_MUTED;
        int fillColor = highlighted ? ShopColors.SURFACE_OVERLAY : ShopColors.SURFACE_RAISED;
        ShopUiUtil.renderPanel(graphics, x, y, width, height, fillColor, borderColor);
        // Hover glow: soft cyan accent at top
        if (highlighted) {
            graphics.fill(x, y, x + width, y + 1, ShopColors.ACCENT_PRIMARY);
        }

        // Item icon — use NBT-aware rendering only when NBT differs from default
        String nbt = item.nbtJson();
        if (nbt != null && !nbt.isBlank() && ShopUiUtil.hasNonDefaultNbt(item.itemId(), nbt)) {
            ShopUiUtil.renderItemIconWithNbt(graphics, this.font, item.itemId(), nbt, x + (width - 16) / 2, y + 6);
        } else {
            ShopUiUtil.renderItemIcon(graphics, this.font, item.itemId(), x + (width - 16) / 2, y + 6);
        }

        // Name — scrolls when too long so modded names with many words don't get clipped.
        ShopUiUtil.renderScrollingCentered(graphics, this.font, item.displayName(),
                x + width / 2, y + 28, width - 8, ShopColors.TEXT_PRIMARY);

        if (!outOfStock) {
            // Price
            long price = item.hasPromo() ? item.promoPrice() : item.buyPrice();
            String priceStr = ShopUiUtil.formatMinorUnits(price);
            graphics.drawCenteredString(this.font, "§a" + priceStr, x + width / 2, y + 42, ShopColors.TEXT_PRICE);

            // Stock line — truncated
            String stockStr = item.unlimited() ? "∞ Stock" : item.stock() + " left";
            graphics.drawCenteredString(this.font, stockStr, x + width / 2, y + 56, ShopColors.TEXT_SECONDARY);

            // Animated discount badge
            if (item.hasPromo()) {
                int percent = ShopUiUtil.computePromoPercent(item.buyPrice(), item.promoPrice());
                if (percent > 0) {
                    String badgeText = percent >= 100 ? "Free!" : "-" + percent + "%";
                    ShopUiUtil.renderAnimatedDiscountBadge(graphics, this.font, x + width - 6, y + 8, badgeText);
                }
            }
            // LGB#22: Always show barter badge when barter recipes exist (not exclusive with promo)
            if (item.hasBarterRecipes()) {
                int badgeY = item.hasPromo() ? y + height - 14 : y + height - 14;
                ShopUiUtil.renderPill(graphics, this.font, x + width - 48, badgeY, "⚒ Barter",
                        ShopColors.SURFACE_BASE, ShopColors.TEXT_BARTER_SOFT, ShopColors.TEXT_BARTER_SOFT);
            }
        } else {
            graphics.drawCenteredString(this.font, "§cSold Out", x + width / 2, y + 49, ShopColors.ERROR);
        }

        // Tooltip on hover — Item 6: full advanced tooltip
        if (hovered && !outOfStock) {
            // Store the item ID + nbtJson for advanced tooltip rendering after super.render()
            tooltipItemId = item.itemId();
            // Only pass NBT for tooltip when it actually differs from default
            tooltipNbtJson = (nbt != null && !nbt.isBlank() && ShopUiUtil.hasNonDefaultNbt(item.itemId(), nbt))
                    ? nbt : "";
            tooltipMouseX = mouseX;
            tooltipMouseY = mouseY;
        }
    }

    private void renderStrip(GuiGraphics graphics) {
        int x = guiLeft + 8;
        int y = guiTop + guiH - footerH - stripH - 2;
        int w = guiW - 16;
        // Strip: elevated card with top accent rule
        ShopUiUtil.renderCard(graphics, x, y, w, stripH);
        graphics.fill(x, y, x + w, y + 1, ShopColors.ACCENT_CURRENCY);

        // Balance with coin icon (gold accent)
        String balance = "§6⛃ §a" + ShopUiUtil.formatMinorUnits(ShopClientState.getCurrentBalanceMinorUnits());
        graphics.drawString(this.font, balance, x + 10, y + 9, ShopColors.TEXT_CURRENCY, false);

        // Item count & cart info — truncated
        String info = filteredItems.size() + " items • " + ShopClientState.getCartTotalQuantity() + " in cart";
        String clipped = this.font.plainSubstrByWidth(info, w / 3);
        graphics.drawString(this.font, clipped, x + w - this.font.width(clipped) - 10, y + 9, ShopColors.TEXT_MUTED, false);

        // Status panel in middle
        ShopUiUtil.renderStatusPanel(graphics, this.font, x + 160, y + 4, Math.max(80, w - 320));
    }

    private void renderFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = guiLeft + 8;
        int y = guiTop + guiH - footerH;
        int w = guiW - 16;
        ShopUiUtil.renderCard(graphics, x, y, w, footerH - 4);

        // Help text
        String footerText = barterMode ? "§9⚒ Barter mode active" : "§7Left click for details • Right click to quick-add";
        String clippedFooter = this.font.plainSubstrByWidth(footerText, w - 120);
        graphics.drawString(this.font, clippedFooter, x + 10, y + 6, ShopColors.TEXT_MUTED, false);

        // ═══ Profile button (bottom right) — elevated chip with cyan glow on hover ═══
        int profileW = 108;
        int profileH = footerH - 8;
        int profileX = x + w - profileW - 4;
        int profileY = y + 2;
        boolean profileHovered = mouseX >= profileX && mouseX <= profileX + profileW && mouseY >= profileY && mouseY <= profileY + profileH;

        graphics.fill(profileX, profileY, profileX + profileW, profileY + profileH,
                profileHovered ? ShopColors.SURFACE_OVERLAY : ShopColors.SURFACE_RAISED);
        ShopUiUtil.drawBorder(graphics, profileX, profileY, profileW, profileH,
                profileHovered ? ShopColors.BORDER_GLOW : ShopColors.BORDER_STRONG);
        graphics.fill(profileX, profileY, profileX + 2, profileY + profileH, ShopColors.ACCENT_PRIMARY);

        // Player head
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ShopUiUtil.renderPlayerFace(graphics, mc.player.getUUID(), profileX + 5, profileY + 3, profileH - 6);
            // Player name — truncated
            String playerName = this.font.plainSubstrByWidth(mc.player.getGameProfile().getName(), profileW - profileH - 12);
            graphics.drawString(this.font, playerName, profileX + profileH + 2, profileY + 3, ShopColors.TEXT_STRONG, false);
            // Balance
            String bal = "§a" + ShopUiUtil.formatMinorUnits(ShopClientState.getCurrentBalanceMinorUnits());
            String clippedBal = this.font.plainSubstrByWidth(bal, profileW - profileH - 12);
            graphics.drawString(this.font, clippedBal, profileX + profileH + 2, profileY + 14, ShopColors.TEXT_CURRENCY, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
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
            if (nearbyMode) {
                nearbyScrollIdx = Math.max(0, nearbyScrollIdx - (int) delta);
                return true;
            }
            gridScrollRows = Math.max(0, gridScrollRows - (int) delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
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
            ShopPackets.sendToServer(new C2SOpenBalanceUiPacket());
            return true;
        }

        // Sidebar clicks
        int sidebarX = guiLeft + 8;
        int sidebarY = guiTop + headerH + 2;
        int sidebarH = guiH - headerH - stripH - footerH - 14;
        if (mouseX >= sidebarX && mouseX <= sidebarX + sidebarW && mouseY >= sidebarY + 22 && mouseY <= sidebarY + sidebarH) {
            List<CatalogCategory> cats = ShopClientState.getCatalogCategories();
            int tabCount = totalTabCount();
            int tabHeight = 18;
            int maxVisible = Math.max(1, (sidebarH - 24) / tabHeight);
            for (int i = sidebarScrollIdx; i < tabCount && i < sidebarScrollIdx + maxVisible; i++) {
                int y = sidebarY + 22 + (i - sidebarScrollIdx) * tabHeight;
                if (mouseY >= y && mouseY <= y + tabHeight - 2) {
                    selectedCategoryIdx = i;
                    nearbyMode = isNearbyTabIndex(i, cats.size());
                    if (isBarterTabIndex(i, cats.size())) {
                        barterMode = true;
                        modeBtn.setMessage(modeButtonMessage(guiW < 520));
                    } else if (!nearbyMode) {
                        barterMode = false;
                        modeBtn.setMessage(modeButtonMessage(guiW < 520));
                    }
                    gridScrollRows = 0;
                    nearbyScrollIdx = 0;
                    rebuildFilteredItems();
                    // Item 3 fix: When entering nearby mode, re-request shop data for immediate scan refresh
                    if (nearbyMode) {
                        ShopPackets.sendToServer(new C2SOpenShopPacket(ShopClientState.getActiveShopId()));
                        ShopPackets.sendToServer(new C2SFetchLocalShopsPacket(""));
                    }
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
            // ═══ Nearby mode click handling ═══
            if (nearbyMode) {
                List<LocalShopOwnerEntry> owners = ShopClientState.getLocalShopOwners();
                if (!owners.isEmpty()) {
                    // Aggregated owner view
                    int ownerCardH = 52;
                    int ownerGap = 4;
                    int ownerContentX = gridX + 8;
                    int ownerContentY = gridY + 8 + 14; // after header
                    int ownerContentW = gridW - 16;
                    int ownerContentH = gridH - 16 - 14;
                    int ownerMaxVisible = Math.max(1, (ownerContentH + ownerGap) / (ownerCardH + ownerGap));
                    for (int i = nearbyScrollIdx; i < owners.size() && i < nearbyScrollIdx + ownerMaxVisible; i++) {
                        int ny = ownerContentY + (i - nearbyScrollIdx) * (ownerCardH + ownerGap);
                        if (mouseX >= ownerContentX && mouseX <= ownerContentX + ownerContentW && mouseY >= ny && mouseY <= ny + ownerCardH) {
                            LocalShopOwnerEntry ownerEntry = owners.get(i);
                            Minecraft.getInstance().setScreen(new LocalShopBrowserScreen(this, ownerEntry));
                            return true;
                        }
                    }
                    return super.mouseClicked(mouseX, mouseY, button);
                }

                // Legacy fallback
                List<NearbyShopEntry> nearby = ShopClientState.getNearbyShops();
                int nearbyCardH = 48;
                int nearbyGap = 4;
                int nearbyContentX = gridX + 8;
                int nearbyContentY = gridY + 8;
                int nearbyContentW = gridW - 16;
                int nearbyContentH = gridH - 16;
                int nearbyMaxVisible = Math.max(1, (nearbyContentH + nearbyGap) / (nearbyCardH + nearbyGap));
                for (int i = nearbyScrollIdx; i < nearby.size() && i < nearbyScrollIdx + nearbyMaxVisible; i++) {
                    int ny = nearbyContentY + (i - nearbyScrollIdx) * (nearbyCardH + nearbyGap);
                    if (mouseX >= nearbyContentX && mouseX <= nearbyContentX + nearbyContentW && mouseY >= ny && mouseY <= ny + nearbyCardH) {
                        NearbyShopEntry entry = nearby.get(i);
                        // Send a packet to the server to open this player's shop block
                        ShopPackets.sendToServer(new C2SPlayerShopActionPacket(
                                entry.pos(), "VISIT", 0, 0));
                        return true;
                    }
                }
                return super.mouseClicked(mouseX, mouseY, button);
            }

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
                    selectedGridIndex = index;
                    if (button == 0) {
                        // Shift+Click → quick-add to cart (keyed by listingId so NBT variants stay distinct)
                        if (hasShiftDown() && item.buyPrice() > 0L && (item.unlimited() || item.stock() > 0)) {
                            ShopClientState.addToCart(item.listingId(), 1);
                            return true;
                        }
                        // Item 13: When in barter mode (barter tab selected), open BarterScreen directly.
                        // Barter is registry-itemId keyed; the detail screen is listingId keyed.
                        if ((barterMode || isBarterTabSelected()) && item.hasBarterRecipes()) {
                            Minecraft.getInstance().setScreen(new BarterScreen(this, item.itemId()));
                        } else {
                            Minecraft.getInstance().setScreen(new ItemDetailScreen(this, item.listingId()));
                        }
                    } else if (button == 1 && item.buyPrice() > 0L) {
                        ShopClientState.addToCart(item.listingId(), 1);
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

    private boolean hasNearbyTab() {
        return true;
    }

    private boolean isBarterTabSelected() {
        return hasBarterTab() && isBarterTabIndex(selectedCategoryIdx, ShopClientState.getCatalogCategories().size());
    }

    private boolean isNearbyTabSelected() {
        return nearbyMode;
    }

    private boolean isBarterTabIndex(int index, int categoryCount) {
        return hasBarterTab() && index == categoryCount + 1;
    }

    private boolean isNearbyTabIndex(int index, int categoryCount) {
        int base = categoryCount + 1 + (hasBarterTab() ? 1 : 0);
        return hasNearbyTab() && index == base;
    }

    private int totalTabCount() {
        List<CatalogCategory> cats = ShopClientState.getCatalogCategories();
        return cats.size() + 1 + (hasBarterTab() ? 1 : 0) + (hasNearbyTab() ? 1 : 0);
    }

    private String labelForTab(int index, int categoryCount) {
        if (index == 0) {
            return "All";
        }
        if (isBarterTabIndex(index, categoryCount)) {
            return "⚒ Barter";
        }
        if (isNearbyTabIndex(index, categoryCount)) {
            return "📍 Nearby";
        }
        return ShopClientState.getCatalogCategories().get(index - 1).displayName();
    }

    private Component modeButtonMessage(boolean tight) {
        if (tight) {
            // Icon-only variants stay inline — they're glyphs, not translatable prose.
            return Component.literal(barterMode ? "§9⚒" : "§a$");
        }
        return Component.translatable(barterMode
                ? "gui.futureshops.shop_main.mode_barter"
                : "gui.futureshops.shop_main.mode_buy");
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

    // ═══ Spec §15: Keyboard Shortcuts ═══
    // Grid navigation state for arrow keys
    private int selectedGridIndex = -1;

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // "/" (slash) → focus search
        if (keyCode == 47 && searchField != null && !searchField.isFocused()) {
            searchField.setFocused(true);
            return true;
        }
        // "B" → toggle barter mode (only when search not focused)
        if (keyCode == 66 && searchField != null && !searchField.isFocused()) {
            barterMode = !barterMode;
            if (!barterMode && isBarterTabSelected()) {
                selectedCategoryIdx = 0;
            }
            if (modeBtn != null) {
                modeBtn.setMessage(modeButtonMessage(guiW < 520));
            }
            gridScrollRows = 0;
            rebuildFilteredItems();
            return true;
        }

        // Tab → cycle focus: search → sidebar → grid → search
        if (keyCode == 258 && !nearbyMode) { // Tab key
            if (searchField != null && searchField.isFocused()) {
                searchField.setFocused(false);
                // Focus sidebar
                return true;
            }
            // If in grid → focus search
            if (selectedGridIndex >= 0) {
                selectedGridIndex = -1;
                if (searchField != null) searchField.setFocused(true);
                return true;
            }
            // Default → focus grid
            selectedGridIndex = 0;
            if (searchField != null) searchField.setFocused(false);
            return true;
        }

        // Arrow keys → grid navigation (only when search not focused)
        if (searchField != null && !searchField.isFocused() && !nearbyMode && !filteredItems.isEmpty()) {
            int gridW = guiW - sidebarW - 24;
            int contentW = gridW - 16;
            int gap = 6;
            int columns = Math.max(2, Math.min(5, (contentW + gap) / 88));

            if (selectedGridIndex < 0) selectedGridIndex = 0;

            switch (keyCode) {
                case 263 -> { // Left
                    if (selectedGridIndex % columns > 0) selectedGridIndex--;
                    return true;
                }
                case 262 -> { // Right
                    if (selectedGridIndex % columns < columns - 1 && selectedGridIndex + 1 < filteredItems.size()) selectedGridIndex++;
                    return true;
                }
                case 265 -> { // Up
                    if (selectedGridIndex >= columns) selectedGridIndex -= columns;
                    else { sidebarScrollIdx = Math.max(0, sidebarScrollIdx - 1); }
                    return true;
                }
                case 264 -> { // Down
                    if (selectedGridIndex + columns < filteredItems.size()) selectedGridIndex += columns;
                    return true;
                }
                case 257 -> { // Enter → open selected item
                    if (selectedGridIndex >= 0 && selectedGridIndex < filteredItems.size()) {
                        CatalogItem item = filteredItems.get(selectedGridIndex);
                        if ((barterMode || isBarterTabSelected()) && item.hasBarterRecipes()) {
                            Minecraft.getInstance().setScreen(new BarterScreen(this, item.itemId()));
                        } else {
                            Minecraft.getInstance().setScreen(new ItemDetailScreen(this, item.listingId()));
                        }
                        return true;
                    }
                }
            }

            // Keep selected index visible — scroll if needed
            int row = selectedGridIndex / columns;
            int cardH = 82;
            int contentH = guiH - headerH - stripH - footerH - 14 - 16;
            int visibleRows = Math.max(1, (contentH + gap) / (cardH + gap));
            if (row < gridScrollRows) gridScrollRows = row;
            else if (row >= gridScrollRows + visibleRows) gridScrollRows = row - visibleRows + 1;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // Forward '/' to search field focus instead of typing it
        if (codePoint == '/' && searchField != null && !searchField.isFocused()) {
            searchField.setFocused(true);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }
}
