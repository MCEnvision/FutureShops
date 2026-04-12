package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.CatalogBarterRecipe;
import com.enviouse.futureshops.data.CatalogCategory;
import com.enviouse.futureshops.data.CatalogItem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ShopMainScreen — spec §5.
 *
 * <p>Layout summary (GUI-scaled pixels, 340 × 230 container):
 * <pre>
 *  ┌──────────────────────────────────────────────────┐  y=0
 *  │  HEADER BAR (340 × 22)                           │  y=22
 *  ├────────┬─────────────────────────────────────────┤
 *  │        │                                         │
 *  │ SIDEBAR│       ITEM GRID (278 × 170)             │
 *  │  62px  │                         [scrollbar 6px] │  y=192
 *  │        ├─────────────────────────────────────────┤
 *  │        │  INVENTORY STRIP (278 × 26)             │  y=218
 *  ├────────┴─────────────────────────────────────────┤
 *  │  FOOTER BAR (340 × 12)                           │  y=230
 *  └──────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>Item cards: 58 × 56 px, 4 columns, 4 px gap, centred in the 272 px grid content area.
 */
public class ShopMainScreen extends Screen implements ShopScreenMarker {

    // ─── Container dimensions ────────────────────────────────────────────────
    private static final int GUI_W  = 340;
    private static final int GUI_H  = 230;

    // ─── Zone dimensions ─────────────────────────────────────────────────────
    private static final int HEADER_H   = 22;
    private static final int SIDEBAR_W  = 62;   // 60 content + 2px right divider
    private static final int FOOTER_H   = 12;
    /** Height of the scrollable grid zone. */
    private static final int GRID_H     = 170;
    /** Height of the inventory/balance strip below the grid. */
    private static final int STRIP_H    = GUI_H - HEADER_H - FOOTER_H - GRID_H; // 26
    /** Width of the right pane (sidebar to right edge). */
    private static final int RIGHT_W    = GUI_W - SIDEBAR_W; // 278
    /** Width of the scrollbar rail. */
    private static final int SCROLLBAR_W = 6;
    /** Width of the scrollable grid content (excluding scrollbar). */
    private static final int GRID_CONTENT_W = RIGHT_W - SCROLLBAR_W; // 272

    // ─── Card dimensions ─────────────────────────────────────────────────────
    private static final int CARD_W        = 58;
    private static final int CARD_H        = 56;
    private static final int CARD_GAP      = 4;
    private static final int CARDS_PER_ROW = 4;

    // Horizontal padding to centre the 4-column grid inside GRID_CONTENT_W
    // 4*58 + 3*4 = 244 px of cards; (272 - 244) / 2 = 14
    private static final int GRID_PAD_X    = (GRID_CONTENT_W - (CARDS_PER_ROW * CARD_W + (CARDS_PER_ROW - 1) * CARD_GAP)) / 2;

    // ─── Runtime state ───────────────────────────────────────────────────────
    private int guiLeft, guiTop;

    private EditBox searchField;
    private Button  closeBtn, cartBtn, historyBtn, modeBtn, sortBtn;

    private boolean barterMode          = false;
    private int     gridScrollPx        = 0;   // vertical scroll in pixels
    private int     sidebarScrollIdx    = 0;   // first visible tab index
    private int     selectedCategoryIdx = 0;   // 0 = "All"
    private String  searchQuery         = "";

    private List<CatalogItem> filteredItems = List.of();
    private List<Component> barterBadgeTooltip = List.of();
    private List<PromoBadgeOverlay> promoBadgeOverlays = List.of();

    // ─── Constructor ─────────────────────────────────────────────────────────

    public ShopMainScreen() {
        super(Component.translatable("gui.futureshops.shop.title"));
    }

    // ─── Screen lifecycle ────────────────────────────────────────────────────

    @Override
    protected void init() {
        guiLeft = (this.width  - GUI_W) / 2;
        guiTop  = (this.height - GUI_H) / 2;

        rebuildFilteredItems();

        // -- Search field (centered in the header) ---------------------------
        int sfX = guiLeft + 80;
        int sfY = guiTop  + 4;
        searchField = new EditBox(this.font, sfX, sfY, 90, 14,
                Component.translatable("gui.futureshops.shop.search"));
        searchField.setMaxLength(32);
        searchField.setBordered(true);
        searchField.setTextColor(ShopColors.TEXT_PRIMARY);
        searchField.setResponder(q -> {
            this.searchQuery = q.toLowerCase();
            this.gridScrollPx = 0;
            rebuildFilteredItems();
        });
        addRenderableWidget(searchField);

        // -- Mode toggle (Buy / Barter) ----------------------------------------
        modeBtn = Button.builder(Component.literal(barterMode ? "Barter" : "Buy"), btn -> {
            barterMode = !barterMode;
            if (!barterMode && isBarterTabSelected()) {
                selectedCategoryIdx = 0;
            }
            btn.setMessage(Component.literal(barterMode ? "Barter" : "Buy"));
            rebuildFilteredItems();
        }).bounds(guiLeft + 176, guiTop + 4, 40, 14).build();
        addRenderableWidget(modeBtn);

        // -- Cart button -------------------------------------------------------
        cartBtn = Button.builder(Component.literal("Cart"), btn -> this.minecraft.setScreen(new CartScreen(this)))
                .bounds(guiLeft + 220, guiTop + 4, 44, 14).build();
        addRenderableWidget(cartBtn);

        // -- History button ----------------------------------------------------
        historyBtn = Button.builder(Component.literal("Log"), btn -> this.minecraft.setScreen(new TransactionHistoryScreen(this)))
                .bounds(guiLeft + 266, guiTop + 4, 26, 14).build();
        addRenderableWidget(historyBtn);

        // -- Close button ------------------------------------------------------
        closeBtn = Button.builder(Component.literal("×"), btn -> this.onClose())
                .bounds(guiLeft + GUI_W - 16, guiTop + 4, 14, 14).build();
        addRenderableWidget(closeBtn);

        // -- Footer sort button ------------------------------------------------
        sortBtn = Button.builder(Component.literal("Sort"), btn -> {
            // TODO: cycle sort order
        }).bounds(guiLeft + 2, guiTop + GUI_H - FOOTER_H + 1, 28, 10).build();
        addRenderableWidget(sortBtn);
    }

    private void rebuildFilteredItems() {
        List<CatalogCategory> cats  = ShopClientState.getCatalogCategories();
        List<CatalogItem>     all   = ShopClientState.getCatalogItems();

        String activeCatId = null;
        if (selectedCategoryIdx > 0 && selectedCategoryIdx <= cats.size()) {
            activeCatId = cats.get(selectedCategoryIdx - 1).id();
        }

        final String catFilter = activeCatId;
        boolean barterFilterOnly = barterMode || isBarterTabSelected();
        filteredItems = all.stream()
                .filter(item -> !barterFilterOnly || item.hasBarterRecipes())
                .filter(item -> catFilter == null || catFilter.equals(item.categoryId()))
                .filter(item -> searchQuery.isEmpty()
                        || item.displayName().toLowerCase().contains(searchQuery)
                        || item.itemId().toLowerCase().contains(searchQuery))
                .collect(Collectors.toList());
    }

    // ─── Rendering ───────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        barterBadgeTooltip = List.of();
        promoBadgeOverlays = new java.util.ArrayList<>();

        // Full-screen dimming
        g.fill(0, 0, this.width, this.height, ShopColors.BG_PRIMARY);

        // Main panel
        g.fill(guiLeft, guiTop, guiLeft + GUI_W, guiTop + GUI_H, ShopColors.BG_PANEL);
        ShopUiUtil.drawBorder(g, guiLeft, guiTop, GUI_W, GUI_H, ShopColors.BORDER_DEFAULT);

        // Header divider
        g.fill(guiLeft, guiTop + HEADER_H, guiLeft + GUI_W, guiTop + HEADER_H + 1, ShopColors.BORDER_DEFAULT);

        renderTitle(g);
        renderSidebar(g, mx, my);
        renderItemGrid(g, mx, my);
        renderInventoryStrip(g);
        renderFooter(g);
        renderPromoBadgeOverlays(g);

        cartBtn.setMessage(Component.literal("Cart (" + ShopClientState.getCartLineCount() + ")"));

        // Vanilla widgets (buttons, edit box) render on top
        super.render(g, mx, my, pt);
        if (!barterBadgeTooltip.isEmpty()) {
            g.renderTooltip(this.font, barterBadgeTooltip, Optional.empty(), mx, my);
        }
    }

    // ── Header title (1.5× scaled) ────────────────────────────────────────────

    private void renderTitle(GuiGraphics g) {
        String shopName = ShopClientState.getActiveShopId().isBlank()
                ? "Server Shop"
                : prettyName(ShopClientState.getActiveShopId());
        g.pose().pushPose();
        g.pose().translate(guiLeft + 6f, guiTop + 7f, 0f);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawString(this.font, shopName, 0, 0, ShopColors.TEXT_PRIMARY, true);
        g.pose().popPose();
    }

    // ── Category sidebar ──────────────────────────────────────────────────────

    private void renderSidebar(GuiGraphics g, int mx, int my) {
        int sx = guiLeft;
        int sy = guiTop + HEADER_H + 1;
        int sw = SIDEBAR_W;
        int sh = GUI_H - HEADER_H - FOOTER_H - 1;

        g.fill(sx, sy, sx + sw - 1, sy + sh, ShopColors.BG_CARD);
        g.fill(sx + sw - 1, sy, sx + sw, sy + sh, ShopColors.BORDER_DEFAULT);

        List<CatalogCategory> cats = ShopClientState.getCatalogCategories();
        int tabCount = cats.size() + 1 + (hasBarterTab() ? 1 : 0);
        int tabH     = 20;
        int tabGap   = 2;
        int maxVisible = sh / (tabH + tabGap);

        sidebarScrollIdx = Math.max(0, Math.min(sidebarScrollIdx, tabCount - maxVisible));

        int tabY = sy + 2;
        for (int i = sidebarScrollIdx; i < tabCount && tabY + tabH <= sy + sh - 2; i++) {
            boolean selected = (selectedCategoryIdx == i);
            boolean hovered  = mx >= sx + 1 && mx < sx + sw - 2
                    && my >= tabY && my < tabY + tabH;

            int bg = (selected || hovered) ? ShopColors.BG_CARD_HOVER : ShopColors.BG_CARD;
            g.fill(sx + 1, tabY, sx + sw - 2, tabY + tabH, bg);
            if (selected) {
                // Active: 2 px left accent bar
                g.fill(sx + 1, tabY, sx + 3, tabY + tabH, ShopColors.BORDER_ACCENT);
            }

            String label;
            if (i == 0) {
                label = "All";
            } else if (isBarterTabIndex(i, cats.size())) {
                label = "Barter";
            } else {
                label = cats.get(i - 1).displayName();
            }
            String display = this.font.plainSubstrByWidth(label, sw - 8);
            g.drawString(this.font, display, sx + 5, tabY + (tabH - 8) / 2, ShopColors.TEXT_PRIMARY, false);

            tabY += tabH + tabGap;
        }

        // Scroll indicator when there are more tabs than visible
        if (tabCount > maxVisible) {
            int trackH  = sh - 4;
            int thumbH  = Math.max(10, trackH * maxVisible / tabCount);
            int thumbY  = sy + 2 + (trackH - thumbH) * sidebarScrollIdx / Math.max(1, tabCount - maxVisible);
            g.fill(sx + sw - 3, sy + 2, sx + sw - 1, sy + 2 + trackH, ShopColors.BORDER_DEFAULT & 0x4DFFFFFF);
            g.fill(sx + sw - 3, thumbY, sx + sw - 1, thumbY + thumbH, ShopColors.TEXT_SECONDARY);
        }
    }

    // ── Item grid ─────────────────────────────────────────────────────────────

    private void renderItemGrid(GuiGraphics g, int mx, int my) {
        int gx = guiLeft + SIDEBAR_W;
        int gy = guiTop  + HEADER_H + 1;
        int gw = RIGHT_W;
        int gh = GRID_H;

        g.fill(gx, gy, gx + gw, gy + gh, ShopColors.BG_PANEL);

        if (filteredItems.isEmpty()) {
            String msg = "No items found";
            g.drawCenteredString(this.font, msg,
                    gx + GRID_CONTENT_W / 2, gy + gh / 2 - 8, ShopColors.TEXT_SECONDARY);
            g.drawCenteredString(this.font, "Try a different search or category",
                    gx + GRID_CONTENT_W / 2, gy + gh / 2 + 2, ShopColors.TEXT_SECONDARY);
            return;
        }

        int totalRows  = (filteredItems.size() + CARDS_PER_ROW - 1) / CARDS_PER_ROW;
        int totalH     = totalRows * (CARD_H + CARD_GAP);
        int maxScroll  = Math.max(0, totalH - gh + CARD_GAP);
        gridScrollPx   = Math.max(0, Math.min(gridScrollPx, maxScroll));

        // Scissor-clip the grid content
        g.enableScissor(gx, gy, gx + GRID_CONTENT_W, gy + gh);

        int baseX = gx + GRID_PAD_X;
        int baseY = gy + 2 - gridScrollPx;

        for (int i = 0; i < filteredItems.size(); i++) {
            int col = i % CARDS_PER_ROW;
            int row = i / CARDS_PER_ROW;
            int cx  = baseX + col * (CARD_W + CARD_GAP);
            int cy  = baseY + row * (CARD_H + CARD_GAP);

            if (cy + CARD_H < gy || cy > gy + gh) continue; // off-screen
            renderItemCard(g, filteredItems.get(i), cx, cy, mx, my);
        }

        g.disableScissor();

        // Vertical scrollbar
        if (maxScroll > 0) {
            int tx = gx + GRID_CONTENT_W;
            int thumbH = Math.max(10, gh * gh / totalH);
            int thumbY = gy + (int) ((long) gridScrollPx * (gh - thumbH) / maxScroll);
            // track
            g.fill(tx, gy, tx + SCROLLBAR_W, gy + gh, ShopColors.BORDER_DEFAULT & 0x4DFFFFFF);
            // thumb
            g.fill(tx + 1, thumbY, tx + SCROLLBAR_W - 1, thumbY + thumbH, ShopColors.TEXT_SECONDARY);
        }
    }

    // ── Single item card ──────────────────────────────────────────────────────

    private void renderItemCard(GuiGraphics g, CatalogItem item, int x, int y, int mx, int my) {
        boolean hovered    = mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H;
        boolean outOfStock = !item.unlimited() && item.stock() == 0;

        // Background + border
        int bg     = (hovered && !outOfStock) ? ShopColors.BG_CARD_HOVER : ShopColors.BG_CARD;
        int border = (hovered && !outOfStock) ? ShopColors.BORDER_ACCENT  : ShopColors.BORDER_DEFAULT;
        g.fill(x, y, x + CARD_W, y + CARD_H, bg);
        ShopUiUtil.drawBorder(g, x, y, CARD_W, CARD_H, border);

        // Barter left-strip (spec §5.4)
        if (item.hasBarterRecipes()) {
            g.fill(x, y, x + 2, y + CARD_H, ShopColors.BTN_BARTER);
        }

        // ── Item icon (top 28 px, centred) ────────────────────────────────
        int iconX = x + (CARD_W - 16) / 2;
        int iconY = y + 6;
        ShopUiUtil.renderItemIcon(g, this.font, item.itemId(), iconX, iconY);

        // ── Item name (0.85×, below icon, truncated) ─────────────────────
        int maxNameW = (int) ((CARD_W - 4) / 0.85f);
        String name  = this.font.plainSubstrByWidth(item.displayName(), maxNameW);
        g.pose().pushPose();
        g.pose().translate(x + 2f, y + 28f, 0f);
        g.pose().scale(0.85f, 0.85f, 1f);
        g.drawString(this.font, name, 0, 0, ShopColors.TEXT_PRIMARY, false);
        g.pose().popPose();

        // ── Price row (bottom 14 px) ──────────────────────────────────────
        int priceY = y + CARD_H - 13;
        long price = item.hasPromo() ? item.promoPrice() : item.buyPrice();
        String priceStr = ShopUiUtil.formatMinorUnits(price);

        // 4×4 gold coin indicator
        g.fill(x + 2, priceY + 1, x + 6, priceY + 5, ShopColors.ACCENT_GOLD);

        g.pose().pushPose();
        g.pose().translate(x + 8f, priceY + 0.5f, 0f);
        g.pose().scale(0.85f, 0.85f, 1f);
        g.drawString(this.font, priceStr, 0, 0, ShopColors.TEXT_PRICE, false);
        g.pose().popPose();

        // ── Sale badge (top-right corner, spec §5.4) ──────────────────────
        if (item.hasPromo()) {
            int promoPercent = ShopUiUtil.computePromoPercent(item.buyPrice(), item.promoPrice());
            if (promoPercent > 0) {
                promoBadgeOverlays.add(new PromoBadgeOverlay(x, y, promoPercent));
            }
        }

        List<CatalogBarterRecipe> recipes = ShopClientState.getBarterRecipesForItem(item.itemId());
        int recipeCount = recipes.size();
        if (recipeCount > 0) {
            int badgeX1 = x + 1;
            int badgeY1 = y + 1;
            int badgeX2 = x + 20;
            int badgeY2 = y + 9;
            g.fill(badgeX1, badgeY1, badgeX2, badgeY2, (ShopColors.BTN_BARTER & 0x00FFFFFF) | 0xCC000000);
            g.drawString(this.font, "B" + recipeCount, x + 4, y + 2, ShopColors.TEXT_PRIMARY, false);

            boolean badgeHovered = mx >= badgeX1 && mx < badgeX2 && my >= badgeY1 && my < badgeY2;
            if (badgeHovered) {
                barterBadgeTooltip = List.of(
                        Component.translatable("gui.futureshops.shop.badge.barter.tooltip.title", recipeCount),
                        ShopUiUtil.buildFirstIngredientSummary(recipes));
            }
        }

        // ── 3 px promo strip at very bottom ──────────────────────────────
        if (item.hasPromo()) {
            g.fill(x, y + CARD_H - 3, x + CARD_W, y + CARD_H, ShopColors.PROMO_BANNER);
        }

        // ── Out-of-stock overlay ──────────────────────────────────────────
        if (outOfStock) {
            g.fill(x, y, x + CARD_W, y + CARD_H, 0x99000000);
            g.drawCenteredString(this.font, "OUT", x + CARD_W / 2, y + CARD_H / 2 - 4, ShopColors.ERROR);
        }
    }

    // ── Inventory / balance strip ─────────────────────────────────────────────

    private void renderInventoryStrip(GuiGraphics g) {
        int sx = guiLeft + SIDEBAR_W;
        int sy = guiTop  + HEADER_H + 1 + GRID_H;
        int sw = RIGHT_W;

        g.fill(sx, sy, sx + sw, sy + STRIP_H, ShopColors.BG_CARD);
        g.fill(sx, sy, sx + sw, sy + 1,       ShopColors.BORDER_DEFAULT);

        // Balance (left)
        long   bal     = ShopClientState.getCurrentBalanceMinorUnits();
        String balStr  = ShopUiUtil.formatMinorUnits(bal);
        g.fill(sx + 4, sy + STRIP_H / 2 - 2, sx + 8, sy + STRIP_H / 2 + 2, ShopColors.ACCENT_GOLD);
        g.drawString(this.font, balStr, sx + 11, sy + (STRIP_H - 8) / 2, ShopColors.TEXT_PRICE, true);

        // Page info (right)
        int total = filteredItems.size();
        if (total > 0) {
            int visRows  = GRID_H / (CARD_H + CARD_GAP);
            int startRow = gridScrollPx / (CARD_H + CARD_GAP);
            int fromIdx  = startRow * CARDS_PER_ROW + 1;
            int toIdx    = Math.min(total, (startRow + visRows) * CARDS_PER_ROW);
            String info  = fromIdx + "–" + toIdx + " of " + total;
            int iw       = this.font.width(info);
            g.drawString(this.font, info, sx + sw - iw - 4, sy + (STRIP_H - 8) / 2, ShopColors.TEXT_SECONDARY, false);
        }
    }

    // ── Footer bar ────────────────────────────────────────────────────────────

    private void renderFooter(GuiGraphics g) {
        int fx = guiLeft;
        int fy = guiTop + GUI_H - FOOTER_H;
        g.fill(fx, fy, fx + GUI_W, fy + FOOTER_H, ShopColors.BG_CARD);
        g.fill(fx, fy, fx + GUI_W, fy + 1, ShopColors.BORDER_DEFAULT);

        String mode = barterMode ? "▸ BARTER MODE" : "▸ BUY MODE";
        g.drawCenteredString(this.font, mode, fx + GUI_W / 2, fy + 2, ShopColors.TEXT_SECONDARY);
    }

    // ─── Input handling ───────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        // Grid scrolling
        int gx = guiLeft + SIDEBAR_W;
        int gy = guiTop  + HEADER_H + 1;
        if (mx >= gx && mx < gx + GRID_CONTENT_W && my >= gy && my < gy + GRID_H) {
            gridScrollPx -= (int) (delta * (CARD_H + CARD_GAP));
            return true;
        }
        // Sidebar scrolling
        if (mx >= guiLeft && mx < guiLeft + SIDEBAR_W && my >= guiTop + HEADER_H + 1) {
            sidebarScrollIdx = Math.max(0, sidebarScrollIdx - (int) delta);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Sidebar tab click
        int sx   = guiLeft;
        int sy   = guiTop + HEADER_H + 1;
        int sw   = SIDEBAR_W - 1;
        int sh   = GUI_H - HEADER_H - FOOTER_H - 1;

        if (mx >= sx + 1 && mx < sx + sw) {
            List<CatalogCategory> cats = ShopClientState.getCatalogCategories();
            int tabCount  = cats.size() + 1 + (hasBarterTab() ? 1 : 0);
            int tabH = 20, tabGap = 2;
            int maxVis    = sh / (tabH + tabGap);
            int start     = Math.max(0, Math.min(sidebarScrollIdx, tabCount - maxVis));
            int tabY      = sy + 2;
            for (int i = start; i < tabCount && tabY + tabH <= sy + sh - 2; i++) {
                if (my >= tabY && my < tabY + tabH) {
                    selectedCategoryIdx = i;
                    if (isBarterTabIndex(i, cats.size())) {
                        barterMode = true;
                        modeBtn.setMessage(Component.literal("Barter"));
                    }
                    gridScrollPx = 0;
                    rebuildFilteredItems();
                    return true;
                }
                tabY += tabH + tabGap;
            }
        }

        // Item card click (left-click = detail, right-click = quick add to cart)
        int gx = guiLeft + SIDEBAR_W;
        int gy = guiTop  + HEADER_H + 1;
        int gw = GRID_CONTENT_W;
        int gh = GRID_H;

        if (mx >= gx && mx < gx + gw && my >= gy && my < gy + gh) {
            int baseX = gx + GRID_PAD_X;
            int baseY = gy + 2 - gridScrollPx;
            for (int i = 0; i < filteredItems.size(); i++) {
                int col = i % CARDS_PER_ROW;
                int row = i / CARDS_PER_ROW;
                int cx  = baseX + col * (CARD_W + CARD_GAP);
                int cy  = baseY + row * (CARD_H + CARD_GAP);
                if (mx >= cx && mx < cx + CARD_W && my >= cy && my < cy + CARD_H) {
                    CatalogItem selectedItem = filteredItems.get(i);
                    if (btn == 0) {
                        Minecraft.getInstance().setScreen(new ItemDetailScreen(this, selectedItem.itemId()));
                    } else if (btn == 1 && selectedItem.buyPrice() > 0L) {
                        ShopClientState.addToCart(selectedItem.itemId(), 1);
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean hasBarterTab() {
        return ShopClientState.getCatalogItems().stream().anyMatch(CatalogItem::hasBarterRecipes);
    }

    private boolean isBarterTabSelected() {
        int categoryCount = ShopClientState.getCatalogCategories().size();
        return hasBarterTab() && isBarterTabIndex(selectedCategoryIdx, categoryCount);
    }

    private boolean isBarterTabIndex(int index, int categoryCount) {
        return hasBarterTab() && index == categoryCount + 1;
    }

    private void renderPromoBadgeOverlays(GuiGraphics g) {
        if (promoBadgeOverlays.isEmpty()) {
            return;
        }

        int gx = guiLeft + SIDEBAR_W;
        int gy = guiTop + HEADER_H + 1;
        g.enableScissor(gx, gy, gx + GRID_CONTENT_W, gy + GRID_H);
        for (PromoBadgeOverlay badge : promoBadgeOverlays) {
            float pulse = 1.0f + 0.08f * (float) Math.sin((System.currentTimeMillis() + badge.x() * 11L) / 170.0d);
            g.pose().pushPose();
            g.pose().translate(badge.x() + CARD_W - 2f, badge.y() + 4f, 300f);
            g.pose().mulPose(Axis.ZP.rotationDegrees(45f));
            g.pose().scale(pulse, pulse, 1f);
            g.fill(-16, -5, 16, 5, 0xF0D31728);
            g.drawString(this.font, "-" + badge.percentOff() + "%", -13, -4, ShopColors.PROMO_TEXT, false);
            g.pose().popPose();
        }
        g.disableScissor();
    }

    private String prettyName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Server Shop";
        }
        String normalized = raw.replace('_', ' ').replace('-', ' ').trim();
        String[] parts = normalized.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(java.util.Locale.ROOT));
            }
        }
        return builder.isEmpty() ? "Server Shop" : builder.toString();
    }

    private record PromoBadgeOverlay(int x, int y, int percentOff) {
    }

    // ─── Rendering helpers ────────────────────────────────────────────────────
}



