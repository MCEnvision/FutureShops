package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.CatalogCategory;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SAdminShopAddItemsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen admin item picker: browse the entire item registry, multi-select entries,
 * set shared buy/sell/stock values, and add them all to the admin catalog in ONE
 * {@link C2SAdminShopAddItemsPacket}. Opened from ShopMainScreen's edit mode — the
 * edit-mode button is a convenience only; the server re-validates permission level 2.
 */
public class AdminItemPickerScreen extends Screen implements ShopScreenMarker {

    /** Client-side mirror of C2SAdminShopAddItemsPacket's decode cap (256 ids per batch). */
    private static final int MAX_SELECTION = 256;
    /** Default buy price in minor units: 100 = $1.00 at the usual 2 currency decimals. */
    private static final long DEFAULT_BUY_MINOR = 100L;
    private static final int CELL = 24;
    private static final int CELL_GAP = 4;

    private record PickerEntry(
            String id,
            String searchText,
            String modDisplayName,
            String tagText
    ) {
    }

    private final Screen parent;
    /** Target admin category id ("" = All / uncategorized). Mutable — cycled via the footer selector. */
    private String categoryId;
    /** When true the selected items become barter outputs, then open ingredient editors. */
    private final boolean barterMode;
    /** Non-blank when this picker is selecting payment ingredients for one existing barter target. */
    private final String ingredientTargetId;
    private final int initialIngredientCount;
    /** Insertion-ordered so the server receives ids in the order the admin picked them. */
    private final LinkedHashSet<String> selectedIds = new LinkedHashSet<>();

    // Registry snapshot — built once per screen instance (the registry is frozen after load,
    // and localized names cannot change while the screen is open). Filtered ONLY when the
    // search responder fires, never per frame.
    private List<PickerEntry> allEntries;
    private List<PickerEntry> filteredEntries = List.of();
    private String searchQuery = "";
    private int gridScrollRows;

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private int gridX;
    private int gridY;
    private int gridW;
    private int gridH;
    private int contentX;
    private int contentY;
    private int columns;
    private int visibleRows;
    private int footerY;

    private EditBox searchBox;
    private EditBox buyBox;
    private EditBox sellBox;
    private EditBox stockBox;

    /** Per-frame flat-button hit regions, populated in {@link #render}, consulted in mouseClicked. */
    private final java.util.List<ShopUiUtil.ClickZone> clickZones = new java.util.ArrayList<>();
    /** Tooltip to draw after super.render() (∞ unlimited-stock control). */
    private Component pendingTooltip;

    private String categoryLabel = "";

    /** Snapshot of selectable categories (index 0 in the selector = "All"/uncategorized). */
    private List<CatalogCategory> categories = List.of();
    /** 0 = All; 1+ maps to categories.get(idx-1). */
    private int categoryIdx;

    // Deferred hover tooltip (rendered after super.render so widgets never cover it)
    private String tooltipItemId;
    private int tooltipMouseX;
    private int tooltipMouseY;

    public AdminItemPickerScreen(Screen parent, String categoryId) {
        this(parent, categoryId, false);
    }

    /**
     * Barter-aware overload. When {@code barterMode}, the batch is created as safe empty-recipe
     * targets and the server ack opens an ingredient editor for each selection.
     */
    public AdminItemPickerScreen(Screen parent, String categoryId, boolean barterMode) {
        this(parent, categoryId, barterMode, "", 1);
    }

    private AdminItemPickerScreen(Screen parent, String categoryId, boolean barterMode,
                                  String ingredientTargetId, int initialIngredientCount) {
        super(Component.translatable(!ingredientTargetId.isBlank()
                ? "gui.futureshops.admin_edit.picker.title_barter_ingredients"
                : (barterMode
                    ? "gui.futureshops.admin_edit.picker.title_barter"
                    : "gui.futureshops.admin_edit.picker.title")));
        this.parent = parent;
        this.categoryId = categoryId == null ? "" : categoryId;
        this.barterMode = barterMode;
        this.ingredientTargetId = ingredientTargetId;
        this.initialIngredientCount = Math.max(1, initialIngredientCount);
    }

    public static AdminItemPickerScreen forBarterIngredients(Screen parent, String listingId, int initialCount) {
        return new AdminItemPickerScreen(parent, "", false,
                listingId == null ? "" : listingId, initialCount);
    }

    private boolean ingredientMode() {
        return !ingredientTargetId.isBlank();
    }

    @Override
    protected void init() {
        buildRegistryCache();
        categoryLabel = resolveCategoryLabel();
        categories = ShopClientState.getCatalogCategories();
        categoryIdx = indexOfCategory(categoryId);

        guiW = Math.max(300, this.width - 4);
        guiH = Math.max(200, this.height - 4);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;

        footerY = guiTop + guiH - 44;
        gridX = guiLeft + 8;
        gridY = guiTop + 40;
        gridW = guiW - 16;
        gridH = footerY - gridY - 4;
        contentX = gridX + 8;
        contentY = gridY + 8;
        int contentW = gridW - 24;
        int contentH = gridH - 16;
        columns = Math.max(2, (contentW + CELL_GAP) / (CELL + CELL_GAP));
        visibleRows = Math.max(1, (contentH + CELL_GAP) / (CELL + CELL_GAP));

        // Preserve typed values across re-init (window resize). Barter modes use the first box as a
        // plain output/ingredient count, so their first-init defaults are positive integers.
        String buyPrev = buyBox != null ? buyBox.getValue()
                : (ingredientMode() ? Integer.toString(initialIngredientCount)
                    : (barterMode ? "1" : ShopUiUtil.formatMinorUnits(DEFAULT_BUY_MINOR)));
        String sellPrev = sellBox != null ? sellBox.getValue()
                : ShopUiUtil.formatMinorUnits(0L);
        String stockPrev = stockBox != null ? stockBox.getValue() : "∞";

        searchBox = new EditBox(this.font, guiLeft + 12, guiTop + 22, guiW - 24, 14,
                Component.translatable("gui.futureshops.shop.search"));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.translatable("gui.futureshops.admin_edit.picker.search_hint"));
        searchBox.setResponder(query -> {
            String lowered = query.toLowerCase(Locale.ROOT);
            if (!lowered.equals(searchQuery)) {
                searchQuery = lowered;
                gridScrollRows = 0;
                recomputeFiltered();
            }
        });
        searchBox.setValue(searchQuery);
        addRenderableWidget(searchBox);

        // ═══ Footer form: shared buy/sell/stock for the whole batch ═══
        int innerX = guiLeft + 16;
        int rowY = footerY + 16;

        // Barter modes reuse the first price box as an output/ingredient-count input.
        java.util.function.Predicate<String> filter =
                (barterMode || ingredientMode())
                        ? AdminItemPickerScreen::isCountText
                        : AdminItemPickerScreen::isPriceText;

        buyBox = new EditBox(this.font, innerX, rowY, 46, 14,
                Component.translatable(ingredientMode()
                        ? "gui.futureshops.admin_edit.picker.ingredient_count_label"
                        : barterMode
                        ? "gui.futureshops.admin_edit.picker.output_count_label"
                        : "gui.futureshops.admin_edit.picker.buy_label"));
        buyBox.setMaxLength((barterMode || ingredientMode()) ? 4 : 10);
        buyBox.setFilter(filter);
        buyBox.setValue(buyPrev);
        addRenderableWidget(buyBox);

        sellBox = null;
        if (!barterMode && !ingredientMode()) {
            sellBox = new EditBox(this.font, innerX + 52, rowY, 46, 14,
                    Component.translatable("gui.futureshops.admin_edit.picker.sell_label"));
            sellBox.setMaxLength(10);
            sellBox.setFilter(filter);
            sellBox.setValue(sellPrev);
            addRenderableWidget(sellBox);
        }

        stockBox = null;
        if (!ingredientMode()) {
            stockBox = new EditBox(this.font, innerX + (barterMode ? 52 : 104), rowY, 38, 14,
                    Component.translatable("gui.futureshops.admin_edit.picker.stock_label"));
            stockBox.setMaxLength(7);
            stockBox.setFilter(AdminItemPickerScreen::isStockText);
            stockBox.setValue(stockPrev);
            addRenderableWidget(stockBox);
        }

        // ∞ / Cancel / Add are flat Nocturne buttons drawn immediate-mode in render().
        recomputeFiltered();
    }

    /** Draws the ∞ / Cancel / Add footer buttons and registers their click zones for this frame. */
    private void renderFooterButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int innerX = guiLeft + 16;
        int rowY = footerY + 16;

        if (!ingredientMode()) {
            int stockToggleX = innerX + (barterMode ? 94 : 146);
            boolean infHover = ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    stockToggleX, rowY, 14, 14, Component.literal("∞"),
                    ShopUiUtil.ButtonStyle.SECONDARY, true, () -> stockBox.setValue("∞"));
            if (infHover) {
                pendingTooltip = Component.translatable("gui.futureshops.admin_edit.picker.unlimited_tooltip");
            }

            // ═══ Target-category selector: < label > (label itself is drawn in renderFooter) ═══
            int catLabelX = innerX + 182;
            int addLeft = guiLeft + guiW - 8 - 8 - 56 - 4 - 60;
            int catAvail = addLeft - catLabelX - 6 - 14;
            String catText = Component.translatable("gui.futureshops.admin_edit.picker.category_label", categoryLabel).getString();
            int catTextW = catAvail > 0 ? this.font.width(this.font.plainSubstrByWidth(catText, catAvail)) : 0;
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    innerX + 168, rowY, 12, 14, Component.literal("<"),
                    ShopUiUtil.ButtonStyle.SECONDARY, true, () -> cycleCategory(-1));
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    catLabelX + catTextW + 2, rowY, 12, 14, Component.literal(">"),
                    ShopUiUtil.ButtonStyle.SECONDARY, true, () -> cycleCategory(1));
        }

        int cancelW = 56;
        int addW = 60;
        int cancelX = guiLeft + guiW - 8 - 8 - cancelW;
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                cancelX, rowY, cancelW, 14, Component.translatable("gui.futureshops.modal.cancel"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, this::onClose);
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                cancelX - 4 - addW, rowY, addW, 14,
                Component.translatable("gui.futureshops.admin_edit.picker.add", selectedIds.size()),
                ShopUiUtil.ButtonStyle.PRIMARY, !selectedIds.isEmpty(), this::sendAddItems);
    }

    /** Enumerates ForgeRegistries.ITEMS once (skipping minecraft:air), sorted by registry id. */
    private void buildRegistryCache() {
        if (allEntries != null) {
            return;
        }
        List<PickerEntry> out = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (item == Items.AIR) {
                continue;
            }
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key == null) {
                continue;
            }
            String id = key.toString();
            String name = item.getDescription().getString();
            String modName = ModList.get()
                    .getModContainerById(key.getNamespace())
                    .map(container -> container.getModInfo()
                            .getDisplayName())
                    .orElse(key.getNamespace());
            String tagText = item.builtInRegistryHolder().tags()
                    .map(tag -> tag.location().toString())
                    .collect(java.util.stream.Collectors.joining(" "));
            out.add(new PickerEntry(id,
                    (id + ' ' + name + ' ' + modName)
                            .toLowerCase(Locale.ROOT), modName, tagText));
        }
        out.sort(Comparator.comparing(PickerEntry::id));
        allEntries = out;
    }

    private void recomputeFiltered() {
        filteredEntries = allEntries.stream()
                .filter(entry -> AdminItemSearchPolicy.matches(
                        entry.id(), entry.searchText(),
                        entry.modDisplayName(), entry.tagText(),
                        searchQuery))
                .toList();
    }

    private String resolveCategoryLabel() {
        if (!categoryId.isBlank()) {
            for (CatalogCategory cat : ShopClientState.getCatalogCategories()) {
                if (cat.id().equals(categoryId)) {
                    return cat.displayName();
                }
            }
        }
        return Component.translatable("gui.futureshops.shop_main.tab_all").getString();
    }

    /** 0 for blank/"All", else 1 + index of the matching category. */
    private int indexOfCategory(String id) {
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).id().equals(id)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** Advances the selector (wrapping over the "All" slot + every category) and refreshes the target. */
    private void cycleCategory(int delta) {
        categoryIdx = Math.floorMod(categoryIdx + delta, categories.size() + 1);
        categoryId = selectedCategoryId();
        categoryLabel = selectedCategoryName();
    }

    private String selectedCategoryId() {
        return categoryIdx <= 0 || categoryIdx > categories.size() ? "" : categories.get(categoryIdx - 1).id();
    }

    private String selectedCategoryName() {
        return categoryIdx <= 0 || categoryIdx > categories.size()
                ? Component.translatable("gui.futureshops.shop_main.tab_all").getString()
                : categories.get(categoryIdx - 1).displayName();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();
        tooltipItemId = null;
        pendingTooltip = null;
        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        // Amber top accent = editing surface (mirrors ShopMainScreen edit mode)
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + 2, ShopColors.ACCENT_CURRENCY);

        graphics.drawString(this.font, this.title, guiLeft + 12, guiTop + 8, ShopColors.TEXT_STRONG, false);
        if (barterMode || ingredientMode()) {
            // The picker is full-screen, so the title row has ample room for a centered instruction.
            graphics.drawCenteredString(this.font,
                    Component.translatable(ingredientMode()
                            ? "gui.futureshops.admin_edit.picker.barter_ingredient_hint"
                            : "gui.futureshops.admin_edit.picker.barter_hint"),
                    guiLeft + guiW / 2, guiTop + 8, ShopColors.ACCENT_300);
        }
        Component selectedLabel = selectedIds.size() >= MAX_SELECTION
                ? Component.translatable("gui.futureshops.admin_edit.picker.limit", MAX_SELECTION)
                : Component.translatable("gui.futureshops.admin_edit.picker.selected", selectedIds.size());
        int selectedColor = selectedIds.size() >= MAX_SELECTION ? ShopColors.STATUS_WARNING : ShopColors.TEXT_MUTED;
        graphics.drawString(this.font, selectedLabel,
                guiLeft + guiW - 12 - this.font.width(selectedLabel), guiTop + 8, selectedColor, false);

        renderGrid(graphics, mouseX, mouseY);
        renderFooter(graphics);
        renderFooterButtons(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (tooltipItemId != null) {
            ShopUiUtil.renderItemTooltip(graphics, this.font, tooltipItemId, "", tooltipMouseX, tooltipMouseY);
        } else if (pendingTooltip != null) {
            graphics.renderTooltip(this.font, pendingTooltip, mouseX, mouseY);
        }
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        ShopUiUtil.renderCard(graphics, gridX, gridY, gridW, gridH);
        graphics.fill(gridX, gridY, gridX + gridW, gridY + 2, ShopColors.ACCENT_CURRENCY);

        if (filteredEntries.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.admin_edit.picker.no_results"),
                    gridX + gridW / 2, gridY + gridH / 2 - 4, ShopColors.TEXT_FAINT);
            return;
        }

        int totalRows = (filteredEntries.size() + columns - 1) / columns;
        gridScrollRows = Math.max(0, Math.min(gridScrollRows, Math.max(0, totalRows - visibleRows)));
        int first = gridScrollRows * columns;
        int last = Math.min(filteredEntries.size(), (gridScrollRows + visibleRows) * columns);

        for (int index = first; index < last; index++) {
            int visibleRow = index / columns - gridScrollRows;
            int col = index % columns;
            int x = contentX + col * (CELL + CELL_GAP);
            int y = contentY + visibleRow * (CELL + CELL_GAP);
            PickerEntry entry = filteredEntries.get(index);
            boolean selected = selectedIds.contains(entry.id());
            boolean hovered = mouseX >= x && mouseX <= x + CELL && mouseY >= y && mouseY <= y + CELL;

            int fill = selected ? ShopColors.SURFACE_PRESSED
                    : (hovered ? ShopColors.SURFACE_OVERLAY : ShopColors.SURFACE_RAISED);
            int border = selected ? ShopColors.BORDER_GLOW
                    : (hovered ? ShopColors.BORDER_STRONG : ShopColors.BORDER_MUTED);
            ShopUiUtil.renderPanel(graphics, x, y, CELL, CELL, fill, border);
            ShopUiUtil.renderItemIconWithNbt(graphics, this.font, entry.id(), "", x + 4, y + 4);
            if (selected) {
                // Check chip in the top-right corner of the cell
                graphics.fill(x + CELL - 9, y + 1, x + CELL - 1, y + 9, ShopColors.SURFACE_BASE);
                graphics.drawString(this.font, "✓", x + CELL - 8, y + 1, ShopColors.ACCENT_PRIMARY, false);
            }
            if (hovered) {
                tooltipItemId = entry.id();
                tooltipMouseX = mouseX;
                tooltipMouseY = mouseY;
            }
        }

        ShopUiUtil.renderScrollIndicators(graphics, this.font, gridX, gridY, gridW, gridH,
                gridScrollRows, visibleRows, totalRows);
    }

    private void renderFooter(GuiGraphics graphics) {
        int fx = guiLeft + 8;
        int fw = guiW - 16;
        ShopUiUtil.renderCard(graphics, fx, footerY, fw, 36);
        graphics.fill(fx, footerY, fx + fw, footerY + 1, ShopColors.ACCENT_CURRENCY);

        int innerX = guiLeft + 16;
        int labelY = footerY + 5;
        graphics.drawString(this.font, Component.translatable(ingredientMode()
                        ? "gui.futureshops.admin_edit.picker.ingredient_count_label"
                        : barterMode
                            ? "gui.futureshops.admin_edit.picker.output_count_label"
                            : "gui.futureshops.admin_edit.picker.buy_label"),
                innerX, labelY, ShopColors.TEXT_FAINT, false);
        if (!barterMode && !ingredientMode()) {
            graphics.drawString(this.font, Component.translatable("gui.futureshops.admin_edit.picker.sell_label"),
                    innerX + 52, labelY, ShopColors.TEXT_FAINT, false);
        }
        if (!ingredientMode()) {
            graphics.drawString(this.font, Component.translatable("gui.futureshops.admin_edit.picker.stock_label"),
                    innerX + (barterMode ? 52 : 104), labelY, ShopColors.TEXT_FAINT, false);
        }

        // Target category — sits between the < and > cycle buttons; clipped against the Add button.
        if (!ingredientMode()) {
            String categoryText = Component.translatable("gui.futureshops.admin_edit.picker.category_label", categoryLabel).getString();
            int categoryX = innerX + 182;
            int addX = guiLeft + guiW - 8 - 8 - 56 - 4 - 60;
            int available = addX - categoryX - 6 - 14;
            if (available > 20) {
                graphics.drawString(this.font, this.font.plainSubstrByWidth(categoryText, available),
                        categoryX, footerY + 19, ShopColors.TEXT_MUTED, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Flat Nocturne buttons (∞ / Cancel / Add) take priority over grid selection.
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && !filteredEntries.isEmpty()
                && mouseX >= gridX && mouseX <= gridX + gridW && mouseY >= gridY && mouseY <= gridY + gridH) {
            int first = gridScrollRows * columns;
            int last = Math.min(filteredEntries.size(), (gridScrollRows + visibleRows) * columns);
            for (int index = first; index < last; index++) {
                int visibleRow = index / columns - gridScrollRows;
                int col = index % columns;
                int x = contentX + col * (CELL + CELL_GAP);
                int y = contentY + visibleRow * (CELL + CELL_GAP);
                if (mouseX >= x && mouseX <= x + CELL && mouseY >= y && mouseY <= y + CELL) {
                    String id = filteredEntries.get(index).id();
                    if (!selectedIds.remove(id)) {
                        if (selectedIds.size() >= MAX_SELECTION) {
                            // Server decode hard-caps the batch at 256 — refuse further picks.
                            return true;
                        }
                        selectedIds.add(id);
                    }
                    return true;
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= gridX && mouseX <= gridX + gridW && mouseY >= gridY && mouseY <= gridY + gridH) {
            gridScrollRows = Math.max(0, gridScrollRows - (int) delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void sendAddItems() {
        if (selectedIds.isEmpty()) {
            return;
        }
        if (ingredientMode()) {
            int count = parseCount(buyBox.getValue());
            for (String itemId : selectedIds) {
                ShopPackets.CHANNEL.sendToServer(new com.enviouse.futureshops.network.packets.C2SAdminShopEditPacket(
                        "ADD_BARTER_INGREDIENT", ingredientTargetId, itemId, "", count, 0L, 0L));
            }
            onClose();
            return;
        }
        if (barterMode) {
            // Create safe empty-recipe targets; the server ack opens an ingredient editor for each.
            ShopPackets.CHANNEL.sendToServer(new C2SAdminShopAddItemsPacket(
                    new ArrayList<>(selectedIds),
                    categoryId,
                    0L,
                    0L,
                    parseStock(stockBox.getValue()),
                    true,
                    parseCount(buyBox.getValue()),
                    1));
            onClose();
            return;
        }
        ShopPackets.CHANNEL.sendToServer(new C2SAdminShopAddItemsPacket(
                new ArrayList<>(selectedIds),
                categoryId,
                parsePriceMinor(buyBox.getValue(), DEFAULT_BUY_MINOR),
                parsePriceMinor(sellBox.getValue(), 0L),
                parseStock(stockBox.getValue())));
        onClose();
    }

    /** Allows a plain decimal number ("12", "12.5", partial "12.") while typing. */
    private static boolean isPriceText(String text) {
        return text.isEmpty() || text.matches("\\d*\\.?\\d*");
    }

    private static boolean isStockText(String text) {
        return text.isEmpty() || "∞".equals(text) || text.chars().allMatch(Character::isDigit);
    }

    /** Barter counts are plain positive integers (no ∞, no decimals). */
    private static boolean isCountText(String text) {
        return text.isEmpty() || text.chars().allMatch(Character::isDigit);
    }

    /** Blank / 0 / unparsable → 1 (a trade must move at least one item on each side). */
    private static int parseCount(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(trimmed));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    /** House price convention: display value is a double, wire value is minor units ×10^decimals. */
    private static long parsePriceMinor(String text, long fallbackMinor) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return fallbackMinor;
        }
        try {
            double parsed = Double.parseDouble(trimmed);
            return Math.max(0L, Math.round(parsed * Math.pow(10, ShopClientState.getCurrencyDecimals())));
        } catch (NumberFormatException ignored) {
            return fallbackMinor;
        }
    }

    /** Blank or "∞" = unlimited (-1). */
    private static long parseStock(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty() || "∞".equals(trimmed)) {
            return -1L;
        }
        try {
            return Math.max(0L, Long.parseLong(trimmed));
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
