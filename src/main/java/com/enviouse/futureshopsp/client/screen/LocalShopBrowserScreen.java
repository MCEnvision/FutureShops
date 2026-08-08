package com.enviouse.futureshopsp.client.screen;

import com.enviouse.futureshopsp.client.ShopColors;
import com.enviouse.futureshopsp.data.LocalShopOwnerEntry;
import com.enviouse.futureshopsp.data.LocalShopOwnerEntry.LocalDepartment;
import com.enviouse.futureshopsp.data.LocalShopOwnerEntry.LocalListing;
import com.enviouse.futureshopsp.network.ShopPackets;
import com.enviouse.futureshopsp.network.packets.C2SPlayerShopActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Browsing screen for a single player/franchise's shops in local mode.
 * Shows their custom departments in the sidebar and all listings from all shop blocks in the grid.
 */
public class LocalShopBrowserScreen extends AbstractShopScreen implements ShopScreenMarker {
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
        NAME_AZ("Name A-Z"), NAME_ZA("Name Z-A"),
        PRICE_LOW("Price ↑"), PRICE_HIGH("Price ↓"),
        STOCK_HIGH("Stock ↓"), STOCK_LOW("Stock ↑");

        final String label;
        SortMode(String label) { this.label = label; }
        SortMode next() { return values()[(ordinal() + 1) % values().length]; }
    }
    private SortMode sortMode = SortMode.NAME_AZ;
    private Button sortButton;

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

        // Sort button right of the search box
        sortButton = addRenderableWidget(Button.builder(Component.literal("§7" + sortMode.label), button -> {
            sortMode = sortMode.next();
            button.setMessage(Component.literal("§7" + sortMode.label));
            gridScrollRows = 0;
        }).bounds(gridX + 8 + searchW + 4, guiTop + 36, sortW, 14).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.local.back"), button -> onClose())
                .bounds(guiLeft + 8, guiTop + guiH - 22, 50, 16).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        tooltipItemId = null;
        tooltipNbtJson = null;

        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + 2, ShopColors.ACCENT_PRIMARY);

        // Header
        String title = owner.displayName() + "'s Shop";
        if (!owner.franchiseName().isBlank()) {
            title = owner.franchiseName() + " — " + owner.displayName();
        }
        graphics.drawString(this.font, this.font.plainSubstrByWidth("§l" + title, guiW - 20),
                guiLeft + 10, guiTop + 8, ShopColors.TEXT_STRONG, false);
        String info = owner.shopBlockCount() + " shop blocks • " + owner.totalListings() + " listings • " + owner.totalStock() + " stock";
        graphics.drawString(this.font, this.font.plainSubstrByWidth("§7" + info, guiW - 20),
                guiLeft + 10, guiTop + 20, ShopColors.TEXT_MUTED, false);

        renderSidebar(graphics, mouseX, mouseY);
        renderGrid(graphics, mouseX, mouseY);

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
        graphics.drawString(this.font, "§lDepartments", x + 8, y + 6, ShopColors.TEXT_STRONG, false);

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
            String label = i == 0 ? "All" : this.font.plainSubstrByWidth(depts.get(i - 1).name(), sidebarW - 22);
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
                    .filter(l -> l.displayName().toLowerCase(Locale.ROOT).contains(searchQuery)
                            || l.itemId().toLowerCase(Locale.ROOT).contains(searchQuery))
                    .toList();
        }
        // Sort
        Comparator<LocalListing> cmp = switch (sortMode) {
            case NAME_AZ -> Comparator.comparing(l -> l.displayName().toLowerCase(Locale.ROOT));
            case NAME_ZA -> Comparator.<LocalListing, String>comparing(l -> l.displayName().toLowerCase(Locale.ROOT)).reversed();
            case PRICE_LOW -> Comparator.comparingLong(l -> l.hasPromo() ? l.promoPriceMinor() : l.priceMinor());
            case PRICE_HIGH -> Comparator.<LocalListing>comparingLong(l -> l.hasPromo() ? l.promoPriceMinor() : l.priceMinor()).reversed();
            case STOCK_HIGH -> Comparator.<LocalListing>comparingInt(LocalListing::stock).reversed();
            case STOCK_LOW -> Comparator.comparingInt(LocalListing::stock);
        };
        return filtered.stream().sorted(cmp).toList();
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
            String msg = searchQuery.isEmpty() ? "§7No listings in this department" : "§7No results for \"" + searchQuery + "\"";
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
                    String badgeText = percent >= 100 ? "Free!" : "-" + percent + "%";
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

        // Item icon
        String nbt = listing.nbtJson();
        if (listing.nbtAware() && nbt != null && !nbt.isBlank() && ShopUiUtil.hasNonDefaultNbt(listing.itemId(), nbt)) {
            ShopUiUtil.renderItemIconWithNbt(graphics, this.font, listing.itemId(), nbt, x + (width - 16) / 2, y + 6);
        } else {
            ShopUiUtil.renderItemIcon(graphics, this.font, listing.itemId(), x + (width - 16) / 2, y + 6);
        }

        // Name — scrolls when too long so modded items with lengthy names stay fully readable.
        ShopUiUtil.renderScrollingCentered(graphics, this.font, listing.displayName(),
                x + width / 2, y + 28, width - 8, ShopColors.TEXT_STRONG);

        if (!outOfStock) {
            // Price
            long price = listing.hasPromo() ? listing.promoPriceMinor() : listing.priceMinor();
            String priceStr;
            int priceColor;
            if ("BARTER".equalsIgnoreCase(listing.tradeMode())) {
                priceStr = "§9⚒ Barter";
                priceColor = ShopColors.TEXT_BARTER_SOFT;
            } else {
                priceStr = ShopUiUtil.formatMinorUnits(price);
                priceColor = ShopColors.TEXT_CURRENCY;
            }
            graphics.drawCenteredString(this.font, priceStr, x + width / 2, y + 42, priceColor);

            // Stock
            String stockStr = listing.stock() + " left";
            graphics.drawCenteredString(this.font, stockStr, x + width / 2, y + 56, ShopColors.TEXT_FAINT);

            // Promo badge
            if (listing.hasPromo() && listing.priceMinor() > 0) {
                int percent = ShopUiUtil.computePromoPercent(listing.priceMinor(), listing.promoPriceMinor());
                if (percent > 0) {
                    String badgeText = percent >= 100 ? "Free!" : "-" + percent + "%";
                    ShopUiUtil.renderAnimatedDiscountBadge(graphics, this.font, x + width - 6, y + 8, badgeText);
                }
            }
        } else {
            graphics.drawCenteredString(this.font, "§cSold Out", x + width / 2, y + 49, ShopColors.ERROR);
        }

        // Shop name tooltip on hover
        if (hovered) {
            tooltipItemId = listing.itemId();
            tooltipNbtJson = (listing.nbtAware() && nbt != null && !nbt.isBlank()) ? nbt : "";
            tooltipMouseX = mouseX;
            tooltipMouseY = mouseY;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
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
        return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
                    com.enviouse.futureshopsp.client.PlayerShopClientState.requestVisit(pos, listing.listingIndex());
                    ShopPackets.sendToServer(new C2SPlayerShopActionPacket(
                            pos, "VISIT", 0, 0));
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

