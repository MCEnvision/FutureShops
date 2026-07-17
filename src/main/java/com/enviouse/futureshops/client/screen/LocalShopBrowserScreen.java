package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ClientRouteGuard;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.LocalShopOwnerEntry;
import com.enviouse.futureshops.data.LocalShopOwnerEntry.LocalDepartment;
import com.enviouse.futureshops.data.LocalShopOwnerEntry.LocalListing;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SPlayerShopActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Browsing screen for a single player/franchise's shops in local mode.
 * Shows their custom departments in the sidebar and all listings from all shop blocks in the grid.
 */
public class LocalShopBrowserScreen extends Screen implements ShopScreenMarker {
    private final Screen parent;
    private final LocalShopOwnerEntry owner;
    private int guiLeft, guiTop, guiW, guiH;
    private int sidebarW;
    private int selectedDeptIdx;
    private int gridScrollRows;
    private int sidebarScrollIdx;
    private EditBox searchBox;
    private String searchQuery = "";

    private enum SortMode {
        NAME_AZ("gui.futureshops.local.sort.name_az"), NAME_ZA("gui.futureshops.local.sort.name_za"),
        PRICE_LOW("gui.futureshops.local.sort.price_low"), PRICE_HIGH("gui.futureshops.local.sort.price_high"),
        STOCK_HIGH("gui.futureshops.local.sort.stock_high"), STOCK_LOW("gui.futureshops.local.sort.stock_low");

        final String labelKey;
        SortMode(String labelKey) { this.labelKey = labelKey; }
        SortMode next() { return values()[(ordinal() + 1) % values().length]; }
    }
    private SortMode sortMode = SortMode.NAME_AZ;

    /** Per-frame flat-button hit regions (see ShopUiUtil.button / dispatchClicks). */
    private final java.util.List<ShopUiUtil.ClickZone> clickZones = new ArrayList<>();

    // Tooltip tracking
    private String tooltipItemId = null;
    private String tooltipNbtJson = null;
    private int tooltipMouseX, tooltipMouseY;

    public LocalShopBrowserScreen(Screen parent, LocalShopOwnerEntry owner) {
        super(Component.translatable("gui.futureshops.local.title", owner.displayName()));
        this.parent = parent;
        this.owner = owner;
    }

    @Override
    protected void init() {
        guiW = Math.max(300, this.width - 4);
        guiH = Math.max(200, this.height - 4);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;
        sidebarW = Math.min(130, Math.max(80, guiW / 5));

        // Search box above the grid
        int gridX = guiLeft + sidebarW + 16;
        int sortW = 64;
        int searchW = Math.min(200, guiW - sidebarW - 40 - sortW - 6);
        searchBox = new EditBox(this.font, gridX + 8, guiTop + 36, searchW, 14, Component.translatable("gui.futureshops.local.search"));
        searchBox.setHint(Component.translatable("gui.futureshops.local.search_hint"));
        searchBox.setBordered(true);
        searchBox.setMaxLength(64);
        searchBox.setResponder(query -> {
            searchQuery = query.toLowerCase(Locale.ROOT).trim();
            gridScrollRows = 0;
        });
        searchBox.setValue(searchQuery);
        addRenderableWidget(searchBox);
        // Sort + Back buttons are drawn as flat Nocturne buttons in render().
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();
        tooltipItemId = null;
        tooltipNbtJson = null;

        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + 2, ShopColors.ACCENT_PRIMARY);

        // Header
        String title = Component.translatable("gui.futureshops.local.title", owner.displayName()).getString();
        if (!owner.franchiseName().isBlank()) {
            title = owner.franchiseName() + " — " + owner.displayName();
        }
        graphics.drawString(this.font, this.font.plainSubstrByWidth("§l" + title, guiW - 20),
                guiLeft + 10, guiTop + 8, ShopColors.TEXT_STRONG, false);
        String info = Component.translatable("gui.futureshops.local.header_info",
                owner.shopBlockCount(), owner.totalListings(), owner.totalStock()).getString();
        graphics.drawString(this.font, this.font.plainSubstrByWidth("§7" + info, guiW - 20),
                guiLeft + 10, guiTop + 20, ShopColors.TEXT_MUTED, false);

        renderSidebar(graphics, mouseX, mouseY);
        renderGrid(graphics, mouseX, mouseY);

        // ── Sort + Back (flat Nocturne buttons) ── same geometry the vanilla Buttons used.
        int gridX = guiLeft + sidebarW + 16;
        int sortW = 64;
        int searchW = Math.min(200, guiW - sidebarW - 40 - sortW - 6);
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                gridX + 8 + searchW + 4, guiTop + 36, sortW, 14,
                Component.translatable(sortMode.labelKey), ShopUiUtil.ButtonStyle.SECONDARY, true,
                () -> { sortMode = sortMode.next(); gridScrollRows = 0; });
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 8, guiTop + guiH - 22, 50, 16,
                Component.translatable("gui.futureshops.local.back"), ShopUiUtil.ButtonStyle.SECONDARY, true,
                this::onClose);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (tooltipItemId != null) {
            ShopUiUtil.renderItemTooltip(graphics, this.font, tooltipItemId,
                    tooltipNbtJson != null ? tooltipNbtJson : "", tooltipMouseX, tooltipMouseY);
        }
    }

    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = guiLeft + 8;
        int y = guiTop + 34;
        int h = guiH - 62;
        ShopUiUtil.renderCard(graphics, x, y, sidebarW, h);
        graphics.fill(x, y, x + sidebarW, y + 2, ShopColors.ACCENT_PRIMARY);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.shop_main.departments"), x + 8, y + 6, ShopColors.TEXT_STRONG, false);

        List<LocalDepartment> depts = owner.departments();
        // Add "All" virtual department
        int tabCount = 1 + depts.size();
        int tabHeight = 18;
        int maxVisible = Math.max(1, (h - 24) / tabHeight);
        sidebarScrollIdx = Math.max(0, Math.min(sidebarScrollIdx, Math.max(0, tabCount - maxVisible)));

        int drawY = y + 22;
        for (int i = sidebarScrollIdx; i < tabCount && i < sidebarScrollIdx + maxVisible; i++) {
            boolean selected = i == selectedDeptIdx;
            boolean hovered = mouseX >= x + 4 && mouseX <= x + sidebarW - 4 && mouseY >= drawY && mouseY <= drawY + tabHeight - 2;
            int tabBg = selected ? ShopColors.SURFACE_PRESSED : (hovered ? ShopColors.SURFACE_OVERLAY : ShopColors.SURFACE_BASE);
            graphics.fill(x + 4, drawY, x + sidebarW - 4, drawY + tabHeight - 2, tabBg);
            if (selected) {
                graphics.fill(x + 4, drawY, x + 7, drawY + tabHeight - 2, ShopColors.ACCENT_PRIMARY);
            }
            String label = i == 0
                    ? Component.translatable("gui.futureshops.shop_main.tab_all").getString()
                    : this.font.plainSubstrByWidth(depts.get(i - 1).name(), sidebarW - 22);
            graphics.drawString(this.font, label, x + 12, drawY + 4,
                    selected ? ShopColors.TEXT_STRONG : ShopColors.TEXT_MUTED, false);
            drawY += tabHeight;
        }

        ShopUiUtil.renderScrollIndicators(graphics, this.font, x, y + 20, sidebarW, h - 22, sidebarScrollIdx, maxVisible, tabCount);
    }

    private List<LocalListing> getFilteredListings() {
        List<LocalListing> base;
        if (selectedDeptIdx == 0) {
            base = owner.departments().stream()
                    .flatMap(d -> d.listings().stream())
                    .toList();
        } else {
            int deptIdx = selectedDeptIdx - 1;
            if (deptIdx >= 0 && deptIdx < owner.departments().size()) {
                base = owner.departments().get(deptIdx).listings();
            } else {
                base = List.of();
            }
        }
        // Text filter
        List<LocalListing> filtered = base;
        if (!searchQuery.isEmpty()) {
            filtered = base.stream()
                    .filter(l -> resolvedName(l).toLowerCase(Locale.ROOT).contains(searchQuery)
                            || l.itemId().toLowerCase(Locale.ROOT).contains(searchQuery))
                    .toList();
        }
        // Sort
        Comparator<LocalListing> cmp = switch (sortMode) {
            case NAME_AZ -> Comparator.comparing(l -> resolvedName(l).toLowerCase(Locale.ROOT));
            case NAME_ZA -> Comparator.<LocalListing, String>comparing(l -> resolvedName(l).toLowerCase(Locale.ROOT)).reversed();
            case PRICE_LOW -> Comparator.comparingLong(l -> l.hasPromo() ? l.promoPriceMinor() : l.priceMinor());
            case PRICE_HIGH -> Comparator.<LocalListing>comparingLong(l -> l.hasPromo() ? l.promoPriceMinor() : l.priceMinor()).reversed();
            case STOCK_HIGH -> Comparator.<LocalListing>comparingInt(LocalListing::stock).reversed();
            case STOCK_LOW -> Comparator.comparingInt(LocalListing::stock);
        };
        return filtered.stream().sorted(cmp).toList();
    }

    /**
     * Listing display name, resolved locally from the wire NBT when present —
     * tag-dependent items (TacZ guns) take their name from the stack tag and
     * only the client has the gun-pack name data, so the server-sent string is
     * just a fallback for tag-less listings. buildItemStack caches, so this is
     * cheap per frame.
     */
    private static String resolvedName(LocalListing listing) {
        String nbt = listing.nbtJson();
        if (nbt != null && !nbt.isBlank()) {
            return ShopUiUtil.getItemDisplayNameWithNbt(listing.itemId(), nbt);
        }
        return listing.displayName();
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int gridX = guiLeft + sidebarW + 16;
        int gridY = guiTop + 54; // shifted down to make room for search box
        int gridW = guiW - sidebarW - 24;
        int gridH = guiH - 82; // adjusted
        ShopUiUtil.renderCard(graphics, gridX, gridY, gridW, gridH);
        graphics.fill(gridX, gridY, gridX + gridW, gridY + 2, ShopColors.ACCENT_PRIMARY);

        List<LocalListing> listings = getFilteredListings();
        if (listings.isEmpty()) {
            String msg = searchQuery.isEmpty()
                    ? Component.translatable("gui.futureshops.local.no_listings").getString()
                    : Component.translatable("gui.futureshops.local.no_results", searchQuery).getString();
            graphics.drawCenteredString(this.font, msg,
                    gridX + gridW / 2, gridY + gridH / 2 - 4, ShopColors.TEXT_MUTED);
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
        int totalRows = (listings.size() + columns - 1) / columns;
        gridScrollRows = Math.max(0, Math.min(gridScrollRows, Math.max(0, totalRows - visibleRows)));

        for (int index = 0; index < listings.size(); index++) {
            int row = index / columns;
            if (row < gridScrollRows || row >= gridScrollRows + visibleRows) continue;
            int visibleRow = row - gridScrollRows;
            int col = index % columns;
            int cardX = contentX + col * (cardW + gap);
            int cardY = contentY + visibleRow * (cardH + gap);
            renderListingCard(graphics, listings.get(index), cardX, cardY, cardW, cardH, mouseX, mouseY);
        }

        ShopUiUtil.renderScrollIndicators(graphics, this.font, gridX, gridY, gridW, gridH, gridScrollRows, visibleRows, totalRows);

        // Overlay pass: promo badges above cards AND scrollbar so they never get clipped.
        for (int index = 0; index < listings.size(); index++) {
            int row = index / columns;
            if (row < gridScrollRows || row >= gridScrollRows + visibleRows) continue;
            int visibleRow = row - gridScrollRows;
            int col = index % columns;
            int cardX = contentX + col * (cardW + gap);
            int cardY = contentY + visibleRow * (cardH + gap);
            LocalListing listing = listings.get(index);
            if (listing.hasPromo() && listing.priceMinor() > 0) {
                int percent = ShopUiUtil.computePromoPercent(listing.priceMinor(), listing.promoPriceMinor());
                if (percent > 0) {
                    String badgeText = percent >= 100 ? Component.translatable("gui.futureshops.player_shop_block.detail.promo_free").getString() : "-" + percent + "%";
                    ShopUiUtil.renderAnimatedDiscountBadge(graphics, this.font, cardX + cardW - 6, cardY + 8, badgeText);
                }
            }
        }
    }

    private void renderListingCard(GuiGraphics graphics, LocalListing listing, int x, int y, int width, int height, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        boolean outOfStock = listing.stock() <= 0;
        ShopUiUtil.renderPanel(graphics, x, y, width, height,
                hovered ? ShopColors.SURFACE_OVERLAY : ShopColors.SURFACE_RAISED,
                hovered ? ShopColors.BORDER_GLOW : ShopColors.BORDER_MUTED);
        if (hovered) {
            graphics.fill(x, y, x + width, y + 1, ShopColors.ACCENT_PRIMARY);
        }

        // Item icon — always apply the wire NBT for display: nbtAware only governs
        // transaction matching, and BEWLR items (TacZ guns etc.) render as a
        // missing texture without their tag.
        ShopUiUtil.renderItemIconWithNbt(graphics, this.font, listing.itemId(), listing.nbtJson(),
                x + (width - 16) / 2, y + 6);

        // Name — scrolls when too long so modded items with lengthy names stay fully readable.
        ShopUiUtil.renderScrollingCentered(graphics, this.font, resolvedName(listing),
                x + width / 2, y + 28, width - 8, ShopColors.TEXT_STRONG);

        if (!outOfStock) {
            // Price
            long price = listing.hasPromo() ? listing.promoPriceMinor() : listing.priceMinor();
            String priceStr;
            int priceColor;
            if ("BARTER".equalsIgnoreCase(listing.tradeMode())) {
                priceStr = Component.translatable("gui.futureshops.item_detail.barter").getString();
                priceColor = ShopColors.TEXT_BARTER_SOFT;
            } else {
                priceStr = ShopUiUtil.formatMinorUnits(price);
                priceColor = ShopColors.TEXT_CURRENCY;
            }
            graphics.drawCenteredString(this.font, priceStr, x + width / 2, y + 42, priceColor);

            // Stock
            String stockStr = Component.translatable("gui.futureshops.shop_main.stock_left", listing.stock()).getString();
            graphics.drawCenteredString(this.font, stockStr, x + width / 2, y + 56, ShopColors.TEXT_FAINT);

            // Promo badge
            if (listing.hasPromo() && listing.priceMinor() > 0) {
                int percent = ShopUiUtil.computePromoPercent(listing.priceMinor(), listing.promoPriceMinor());
                if (percent > 0) {
                    String badgeText = percent >= 100 ? Component.translatable("gui.futureshops.player_shop_block.detail.promo_free").getString() : "-" + percent + "%";
                    ShopUiUtil.renderAnimatedDiscountBadge(graphics, this.font, x + width - 6, y + 8, badgeText);
                }
            }
        } else {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.shop_main.sold_out"), x + width / 2, y + 49, ShopColors.ERROR);
        }

        // Shop name tooltip on hover
        if (hovered) {
            tooltipItemId = listing.itemId();
            tooltipNbtJson = listing.nbtJson() == null ? "" : listing.nbtJson();
            tooltipMouseX = mouseX;
            tooltipMouseY = mouseY;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int sidebarX = guiLeft + 8;
        int sidebarY = guiTop + 34;
        int sidebarH = guiH - 62;
        if (mouseX >= sidebarX && mouseX <= sidebarX + sidebarW && mouseY >= sidebarY && mouseY <= sidebarY + sidebarH) {
            sidebarScrollIdx = Math.max(0, sidebarScrollIdx - (int) delta);
            return true;
        }
        int gridX = guiLeft + sidebarW + 16;
        int gridW = guiW - sidebarW - 24;
        int gridY = guiTop + 54;
        int gridH = guiH - 82;
        if (mouseX >= gridX && mouseX <= gridX + gridW && mouseY >= gridY && mouseY <= gridY + gridH) {
            gridScrollRows = Math.max(0, gridScrollRows - (int) delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) return true;

        // Sidebar department clicks
        int sidebarX = guiLeft + 8;
        int sidebarY = guiTop + 34;
        int sidebarH = guiH - 62;
        int tabHeight = 18;
        int tabCount = 1 + owner.departments().size();
        int maxVisible = Math.max(1, (sidebarH - 24) / tabHeight);

        if (mouseX >= sidebarX && mouseX <= sidebarX + sidebarW && mouseY >= sidebarY + 22 && mouseY <= sidebarY + sidebarH) {
            for (int i = sidebarScrollIdx; i < tabCount && i < sidebarScrollIdx + maxVisible; i++) {
                int y = sidebarY + 22 + (i - sidebarScrollIdx) * tabHeight;
                if (mouseY >= y && mouseY <= y + tabHeight - 2) {
                    selectedDeptIdx = i;
                    gridScrollRows = 0;
                    return true;
                }
            }
        }

        // Grid listing clicks — adjusted Y for search box offset
        int gridX = guiLeft + sidebarW + 16;
        int gridY = guiTop + 54;
        int gridW = guiW - sidebarW - 24;
        int gridH = guiH - 82;
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
            List<LocalListing> listings = getFilteredListings();
            for (int index = 0; index < listings.size(); index++) {
                int row = index / columns;
                if (row < gridScrollRows || row >= gridScrollRows + visibleRows) continue;
                int visibleRow = row - gridScrollRows;
                int col = index % columns;
                int cx = contentX + col * (cardW + gap);
                int cy = contentY + visibleRow * (cardH + gap);
                if (mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH) {
                    LocalListing listing = listings.get(index);
                    // Visit the shop block that contains this listing — and preselect the
                    // specific listing the visitor clicked, so they land on that trade
                    // instead of whatever listing was last viewed in that shop.
                    BlockPos pos = BlockPos.of(listing.shopPosLong());
                    com.enviouse.futureshops.client.PlayerShopClientState.requestVisit(pos, listing.listingIndex());
                    ClientRouteGuard.expectStorefront(this, pos.asLong());
                    ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopActionPacket(
                            pos, "VISIT", 0, 0));
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        ClientRouteGuard.cancelFor(this);
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
