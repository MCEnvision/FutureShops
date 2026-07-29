package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.catalog.AdminShopOfferConfigWriter;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.client.editor.OfferEditorTemplates;
import com.enviouse.futureshops.command.EconomyCommandUtil;
import com.enviouse.futureshops.data.CatalogCategory;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SAdminOfferSavePacket;
import com.enviouse.futureshops.network.packets.C2SAdminShopAddItemsPacket;
import com.enviouse.futureshops.network.packets.S2CAdminOfferSaveResultPacket;
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
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

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
    private static final int MAX_COLUMNS = 21;
    private static final int MAX_VISIBLE_ROWS = 8;

    public enum QuickAddMode {
        BUY,
        SELL,
        BARTER,
        BUNDLE
    }

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
    /** Present when opened from the contextual Server Shop Add Item action. */
    private final QuickAddMode quickAddMode;
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
    private UUID pendingRequestId;
    private Component resultMessage;
    private boolean resultSuccess;

    public AdminItemPickerScreen(Screen parent, String categoryId) {
        this(parent, categoryId, false);
    }

    /**
     * Barter-aware overload. When {@code barterMode}, the batch is created as safe empty-recipe
     * targets and the server ack opens an ingredient editor for each selection.
     */
    public AdminItemPickerScreen(Screen parent, String categoryId, boolean barterMode) {
        this(parent, categoryId, barterMode, "", 1, null);
    }

    private AdminItemPickerScreen(Screen parent, String categoryId, boolean barterMode,
                                  String ingredientTargetId, int initialIngredientCount,
                                  QuickAddMode quickAddMode) {
        super(Component.translatable(titleKey(
                ingredientTargetId, barterMode, quickAddMode)));
        this.parent = parent;
        this.categoryId = categoryId == null ? "" : categoryId;
        this.barterMode = barterMode;
        this.ingredientTargetId = ingredientTargetId;
        this.initialIngredientCount = Math.max(1, initialIngredientCount);
        this.quickAddMode = quickAddMode;
    }

    public static AdminItemPickerScreen forBarterIngredients(Screen parent, String listingId, int initialCount) {
        return new AdminItemPickerScreen(parent, "", false,
                listingId == null ? "" : listingId, initialCount,
                null);
    }

    public static AdminItemPickerScreen forQuickAdd(
            Screen parent,
            String categoryId,
            QuickAddMode mode
    ) {
        return new AdminItemPickerScreen(
                parent, categoryId, false, "", 1,
                java.util.Objects.requireNonNull(mode, "mode"));
    }

    private static String titleKey(
            String ingredientTargetId,
            boolean barterMode,
            QuickAddMode quickAddMode
    ) {
        if (!ingredientTargetId.isBlank()) {
            return "gui.futureshops.admin_edit.picker.title_barter_ingredients";
        }
        if (quickAddMode != null) {
            return "gui.futureshops.admin_edit.picker.title_quick_"
                    + quickAddMode.name().toLowerCase(Locale.ROOT);
        }
        return barterMode
                ? "gui.futureshops.admin_edit.picker.title_barter"
                : "gui.futureshops.admin_edit.picker.title";
    }

    private boolean ingredientMode() {
        return !ingredientTargetId.isBlank();
    }

    private boolean quickAdd() {
        return quickAddMode != null;
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

        int footerHeight = quickAdd()
                ? (guiW < 720 ? 82 : 56) : 44;
        footerY = guiTop + guiH - footerHeight;
        gridX = guiLeft + 8;
        gridY = guiTop + 40;
        gridW = guiW - 16;
        gridH = footerY - gridY - 4;
        contentY = gridY + 8;
        int contentW = gridW - 24;
        int contentH = gridH - 16;
        columns = Math.min(MAX_COLUMNS, Math.max(
                2, (contentW + CELL_GAP) / (CELL + CELL_GAP)));
        visibleRows = Math.min(MAX_VISIBLE_ROWS, Math.max(
                1, (contentH + CELL_GAP) / (CELL + CELL_GAP)));
        int cellsWidth = columns * CELL
                + Math.max(0, columns - 1) * CELL_GAP;
        contentX = gridX + Math.max(
                8, (gridW - 12 - cellsWidth) / 2);

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
        int rowY = fieldRowY();

        // Barter modes reuse the first price box as an output/ingredient-count input.
        java.util.function.Predicate<String> filter =
                (barterMode || ingredientMode())
                        ? AdminItemPickerScreen::isCountText
                        : AdminItemPickerScreen::isPriceText;

        boolean showBasePrice = !quickAdd()
                || quickAddMode != QuickAddMode.BARTER;
        buyBox = new EditBox(this.font, innerX, rowY,
                quickAdd() ? 72 : 46, 14,
                Component.translatable(ingredientMode()
                        ? "gui.futureshops.admin_edit.picker.ingredient_count_label"
                        : quickAdd()
                        ? "gui.futureshops.admin_edit.picker.base_price_label"
                        : barterMode
                        ? "gui.futureshops.admin_edit.picker.output_count_label"
                        : "gui.futureshops.admin_edit.picker.buy_label"));
        buyBox.setMaxLength((barterMode || ingredientMode()) ? 4 : 18);
        buyBox.setFilter(filter);
        buyBox.setValue(buyPrev);
        buyBox.visible = showBasePrice;
        buyBox.active = showBasePrice;
        if (showBasePrice) {
            addRenderableWidget(buyBox);
        }

        sellBox = null;
        if (!quickAdd() && !barterMode && !ingredientMode()) {
            sellBox = new EditBox(this.font, innerX + 52, rowY, 46, 14,
                    Component.translatable("gui.futureshops.admin_edit.picker.sell_label"));
            sellBox.setMaxLength(10);
            sellBox.setFilter(filter);
            sellBox.setValue(sellPrev);
            addRenderableWidget(sellBox);
        }

        stockBox = null;
        if (!ingredientMode()) {
            int stockX = quickAdd()
                    ? innerX + (showBasePrice ? 80
                    : Math.min(260, Math.max(150, guiW / 3)))
                    : innerX + (barterMode ? 52 : 104);
            stockBox = new EditBox(this.font, stockX, rowY,
                    quickAdd() ? 54 : 38, 14,
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
        if (quickAdd()) {
            renderQuickAddFooterButtons(graphics, mouseX, mouseY);
            return;
        }
        int innerX = guiLeft + 16;
        int rowY = fieldRowY();

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

    private void renderQuickAddFooterButtons(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        int rowY = fieldRowY();
        int stockToggleX = stockBox.getX() + stockBox.getWidth() + 4;
        boolean enabled = pendingRequestId == null;
        boolean infHover = ShopUiUtil.button(
                graphics, this.font, clickZones, mouseX, mouseY,
                stockToggleX, rowY, 18, 14, Component.literal("∞"),
                ShopUiUtil.ButtonStyle.SECONDARY, enabled,
                () -> stockBox.setValue("∞"));
        if (infHover) {
            pendingTooltip = Component.translatable(
                    "gui.futureshops.admin_edit.picker.unlimited_tooltip");
        }

        int categoryLeft = stockToggleX + 26;
        int actionY = guiW < 720 ? footerY + 58 : rowY;
        int cancelW = 62;
        int cancelX = guiLeft + guiW - 16 - cancelW;
        ShopUiUtil.button(
                graphics, this.font, clickZones, mouseX, mouseY,
                cancelX, actionY, cancelW, 14,
                Component.translatable("gui.futureshops.modal.cancel"),
                ShopUiUtil.ButtonStyle.SECONDARY, enabled, this::onClose);

        int primaryRight = cancelX - 4;
        if (quickAddMode == QuickAddMode.BUY
                || quickAddMode == QuickAddMode.SELL) {
            int addW = 92;
            int simpleW = 118;
            int addX = primaryRight - addW;
            int simpleX = addX - 4 - simpleW;
            ShopUiUtil.button(
                    graphics, this.font, clickZones, mouseX, mouseY,
                    addX, actionY, addW, 14,
                    Component.translatable(quickAddMode
                            == QuickAddMode.BUY
                            ? "gui.futureshops.admin_edit.picker.add_buy"
                            : "gui.futureshops.admin_edit.picker.add_sell"),
                    ShopUiUtil.ButtonStyle.PRIMARY,
                    enabled && selectedIds.size() == 1,
                    this::sendQuickAdd);
            ShopUiUtil.button(
                    graphics, this.font, clickZones, mouseX, mouseY,
                    simpleX, actionY, simpleW, 14,
                    Component.translatable(
                            "gui.futureshops.admin_edit.picker.open_simple"),
                    ShopUiUtil.ButtonStyle.SECONDARY,
                    enabled && selectedIds.size() == 1,
                    this::openSimpleEditor);
        } else {
            int simpleW = 126;
            ShopUiUtil.button(
                    graphics, this.font, clickZones, mouseX, mouseY,
                    primaryRight - simpleW, actionY, simpleW, 14,
                    Component.translatable(
                            "gui.futureshops.admin_edit.picker.open_simple"),
                    ShopUiUtil.ButtonStyle.PRIMARY,
                    enabled && !selectedIds.isEmpty(),
                    this::openSimpleEditor);
        }

        int actionLeft = quickActionLeft();
        int categoryAvailable = Math.max(
                0, actionLeft - categoryLeft - 20);
        String categoryText = Component.translatable(
                "gui.futureshops.admin_edit.picker.category_label",
                categoryLabel).getString();
        int categoryWidth = this.font.width(this.font.plainSubstrByWidth(
                categoryText, categoryAvailable));
        ShopUiUtil.button(
                graphics, this.font, clickZones, mouseX, mouseY,
                categoryLeft, rowY, 14, 14, Component.literal("<"),
                ShopUiUtil.ButtonStyle.SECONDARY, enabled,
                () -> cycleCategory(-1));
        ShopUiUtil.button(
                graphics, this.font, clickZones, mouseX, mouseY,
                categoryLeft + 18 + categoryWidth + 2, rowY,
                14, 14, Component.literal(">"),
                ShopUiUtil.ButtonStyle.SECONDARY, enabled,
                () -> cycleCategory(1));
    }

    private int quickActionLeft() {
        if (guiW < 720) {
            return guiLeft + guiW;
        }
        int cancelX = guiLeft + guiW - 16 - 62;
        return quickAddMode == QuickAddMode.BUY
                || quickAddMode == QuickAddMode.SELL
                ? cancelX - 4 - 92 - 4 - 118
                : cancelX - 4 - 126;
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
        if (barterMode || ingredientMode() || quickAdd()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable(pickerHintKey()),
                    guiLeft + guiW / 2, guiTop + 8, ShopColors.ACCENT_300);
        }
        int selectionLimit = selectionLimit();
        boolean atLimit = !quickAdd()
                && selectedIds.size() >= selectionLimit;
        Component selectedLabel = atLimit
                ? Component.translatable("gui.futureshops.admin_edit.picker.limit", selectionLimit)
                : Component.translatable("gui.futureshops.admin_edit.picker.selected", selectedIds.size());
        int selectedColor = atLimit
                ? ShopColors.STATUS_WARNING : ShopColors.TEXT_MUTED;
        graphics.drawString(this.font, selectedLabel,
                guiLeft + guiW - 12 - this.font.width(selectedLabel), guiTop + 8, selectedColor, false);

        renderGrid(graphics, mouseX, mouseY);
        renderFooter(graphics);
        renderFooterButtons(graphics, mouseX, mouseY);
        if (resultMessage != null) {
            graphics.drawString(
                    this.font, resultMessage, guiLeft + 16,
                    footerY - 11,
                    resultSuccess ? ShopColors.STATUS_SUCCESS
                            : ShopColors.STATUS_DANGER,
                    false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (tooltipItemId != null) {
            ShopUiUtil.renderItemTooltip(graphics, this.font, tooltipItemId, "", tooltipMouseX, tooltipMouseY);
        } else if (pendingTooltip != null) {
            graphics.renderTooltip(this.font, pendingTooltip, mouseX, mouseY);
        }
    }

    private String pickerHintKey() {
        if (ingredientMode()) {
            return "gui.futureshops.admin_edit.picker.barter_ingredient_hint";
        }
        if (quickAdd()) {
            return "gui.futureshops.admin_edit.picker.hint_quick_"
                    + quickAddMode.name().toLowerCase(Locale.ROOT);
        }
        return "gui.futureshops.admin_edit.picker.barter_hint";
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
        int footerHeight = guiTop + guiH - footerY - 8;
        ShopUiUtil.renderCard(graphics, fx, footerY, fw, footerHeight);
        graphics.fill(fx, footerY, fx + fw, footerY + 1, ShopColors.ACCENT_CURRENCY);

        int innerX = guiLeft + 16;
        int labelY = footerY + 5;
        if (quickAdd()) {
            renderQuickAddFooter(graphics, innerX, labelY);
            return;
        }
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

    private void renderQuickAddFooter(
            GuiGraphics graphics,
            int innerX,
            int labelY
    ) {
        boolean showBasePrice = quickAddMode != QuickAddMode.BARTER;
        if (showBasePrice) {
            graphics.drawString(
                    this.font,
                    Component.translatable(
                            "gui.futureshops.admin_edit.picker.base_price_label"),
                    innerX, labelY, ShopColors.TEXT_FAINT, false);
        } else {
            graphics.drawString(
                    this.font,
                    Component.translatable(
                            "gui.futureshops.admin_edit.picker.barter_next_label"),
                    innerX, fieldRowY() + 3,
                    ShopColors.TEXT_MUTED, false);
        }
        graphics.drawString(
                this.font,
                Component.translatable(
                        quickAddMode == QuickAddMode.SELL
                                ? "gui.futureshops.admin_edit.picker.buyback_limit_label"
                                : "gui.futureshops.admin_edit.picker.stock_label"),
                stockBox.getX(), labelY,
                ShopColors.TEXT_FAINT, false);

        int stockToggleX = stockBox.getX() + stockBox.getWidth() + 4;
        int categoryLeft = stockToggleX + 26;
        int categoryAvailable = Math.max(
                0, quickActionLeft() - categoryLeft - 20);
        if (categoryAvailable > 20) {
            String categoryText = Component.translatable(
                    "gui.futureshops.admin_edit.picker.category_label",
                    categoryLabel).getString();
            graphics.drawString(
                    this.font,
                    this.font.plainSubstrByWidth(
                            categoryText, categoryAvailable),
                    categoryLeft + 18, fieldRowY() + 3,
                    ShopColors.TEXT_MUTED, false);
        }
    }

    private int fieldRowY() {
        return footerY + (quickAdd() ? 24 : 16);
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
                        if (selectedIds.size() >= selectionLimit()) {
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

    private int selectionLimit() {
        if (!quickAdd()) {
            return MAX_SELECTION;
        }
        return quickAddMode == QuickAddMode.BUNDLE
                ? 36 : 1;
    }

    private void sendAddItems() {
        if (selectedIds.isEmpty()) {
            return;
        }
        if (quickAdd()) {
            sendQuickAdd();
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

    private void sendQuickAdd() {
        if (quickAddMode != QuickAddMode.BUY
                && quickAddMode != QuickAddMode.SELL) {
            openSimpleEditor();
            return;
        }
        Optional<QuickAddDraft> prepared = prepareQuickAdd();
        if (prepared.isEmpty()) {
            return;
        }
        QuickAddDraft quickDraft = prepared.orElseThrow();
        pendingRequestId = UUID.randomUUID();
        resultMessage = Component.translatable(
                "gui.futureshops.admin_edit.picker.saving");
        resultSuccess = true;
        ShopPackets.CHANNEL.sendToServer(new C2SAdminOfferSavePacket(
                pendingRequestId,
                ShopClientState.getActiveShopId(),
                "",
                0L,
                AdminShopOfferConfigWriter.Operation.CREATE,
                Optional.of(quickDraft.listing())));
    }

    private void openSimpleEditor() {
        Optional<QuickAddDraft> prepared = prepareQuickAdd();
        if (prepared.isEmpty() || this.minecraft == null) {
            return;
        }
        QuickAddDraft quickDraft = prepared.orElseThrow();
        this.minecraft.setScreen(AdminOfferEditorScreen.createQuickAdd(
                parent,
                quickDraft.components(),
                categoryId,
                quickDraft.template(),
                quickDraft.displayName(),
                quickDraft.basePriceMinor(),
                quickDraft.stock()));
    }

    private Optional<QuickAddDraft> prepareQuickAdd() {
        if (selectedIds.isEmpty()) {
            resultMessage = Component.translatable(
                    "gui.futureshops.admin_edit.picker.error_select_item");
            resultSuccess = false;
            return Optional.empty();
        }
        if (quickAddMode != QuickAddMode.BUNDLE
                && selectedIds.size() != 1) {
            resultMessage = Component.translatable(
                    "gui.futureshops.admin_edit.picker.error_select_one");
            resultSuccess = false;
            return Optional.empty();
        }
        OptionalLong stock = parseStockExact(stockBox.getValue());
        if (stock.isEmpty()) {
            resultMessage = Component.translatable(
                    "gui.futureshops.admin_edit.picker.error_stock");
            resultSuccess = false;
            return Optional.empty();
        }
        long priceMinor = 0L;
        if (quickAddMode != QuickAddMode.BARTER) {
            OptionalLong price = parsePositivePriceMinor(
                    buyBox.getValue());
            if (price.isEmpty()) {
                resultMessage = Component.translatable(
                        "gui.futureshops.admin_edit.picker.error_base_price");
                resultSuccess = false;
                return Optional.empty();
            }
            priceMinor = price.getAsLong();
        }

        List<OfferItemComponent> components = new ArrayList<>();
        int index = 1;
        for (String itemId : selectedIds) {
            String prefix = quickAddMode == QuickAddMode.SELL
                    ? "sell_input_" : "output_";
            components.add(new OfferItemComponent(
                    prefix + index++, itemId, 1, ""));
        }
        OfferEditorTemplates.Template template = switch (quickAddMode) {
            case BUY -> OfferEditorTemplates.Template.MONEY;
            case SELL -> OfferEditorTemplates.Template.SELL;
            case BARTER -> OfferEditorTemplates.Template.BARTER;
            case BUNDLE -> OfferEditorTemplates.Template.BUNDLE;
        };
        String displayName = quickDisplayName(components);
        ServerShopOfferListing listing =
                AdminOfferEditorScreen.buildQuickAddListing(
                        components, categoryId, template,
                        displayName, priceMinor, stock.getAsLong());
        resultMessage = null;
        return Optional.of(new QuickAddDraft(
                components, template, displayName,
                priceMinor, stock.getAsLong(), listing));
    }

    private String quickDisplayName(
            List<OfferItemComponent> components
    ) {
        if (quickAddMode == QuickAddMode.BUNDLE
                && components.size() > 1) {
            return Component.translatable(
                    "gui.futureshops.admin_edit.picker.bundle_name",
                    components.size()).getString();
        }
        ResourceLocation identifier = ResourceLocation.tryParse(
                components.get(0).itemId());
        Item item = identifier == null ? null
                : ForgeRegistries.ITEMS.getValue(identifier);
        return item == null
                ? components.get(0).itemId()
                : item.getDescription().getString();
    }

    public void applySaveResult(
            S2CAdminOfferSaveResultPacket result
    ) {
        if (pendingRequestId == null
                || !pendingRequestId.equals(result.requestId())) {
            return;
        }
        pendingRequestId = null;
        resultSuccess = result.success();
        if (result.success()) {
            resultMessage = Component.translatable(
                    "gui.futureshops.admin_edit.picker.saved");
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
            return;
        }
        resultMessage = Component.translatable(
                "gui.futureshops.offer_editor.result."
                        + result.status().name()
                        .toLowerCase(Locale.ROOT));
    }

    private record QuickAddDraft(
            List<OfferItemComponent> components,
            OfferEditorTemplates.Template template,
            String displayName,
            long basePriceMinor,
            long stock,
            ServerShopOfferListing listing
    ) {
        private QuickAddDraft {
            components = List.copyOf(components);
        }
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

    /** Shop prices are parsed exactly into integer minor units. */
    private static long parsePriceMinor(String text, long fallbackMinor) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return fallbackMinor;
        }
        try {
            return Math.max(0L,
                    EconomyCommandUtil.parseAmountToMinorUnits(
                            trimmed,
                            ShopClientState.getCurrencyDecimals()));
        } catch (IllegalArgumentException ignored) {
            return fallbackMinor;
        }
    }

    private static OptionalLong parsePositivePriceMinor(String text) {
        try {
            long value = EconomyCommandUtil.parseAmountToMinorUnits(
                    text.trim(), ShopClientState.getCurrencyDecimals());
            return value > 0L
                    ? OptionalLong.of(value) : OptionalLong.empty();
        } catch (IllegalArgumentException exception) {
            return OptionalLong.empty();
        }
    }

    private static OptionalLong parseStockExact(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty() || "∞".equals(trimmed)) {
            return OptionalLong.of(-1L);
        }
        try {
            long value = Long.parseLong(trimmed);
            return value >= 0L
                    ? OptionalLong.of(value) : OptionalLong.empty();
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
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
