package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.CatalogCategory;
import com.enviouse.futureshops.data.CatalogItem;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SAdminShopEditPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Modal-as-Screen for editing one admin-shop listing (EDIT mode) or adding the player's
 * main-hand item as a new listing (ADD-HELD mode). The parent screen keeps rendering
 * underneath a dim backdrop. All mutations go through {@link C2SAdminShopEditPacket};
 * the server re-validates permission level 2 and every field.
 */
public class AdminListingEditModal extends Screen implements ShopScreenMarker {

    private static final int MODAL_W = 260;
    private static final int MODAL_H = 230;
    private static final int NAME_MAX = 48;
    /** Near-opaque backdrop so NO underlying card / text / item icon shows behind the modal. */
    private static final int MODAL_BACKDROP = 0xF2060608;
    /** Z push so the modal draws in front of the parent's item icons (GUI item-z ≈ 150). */
    private static final float MODAL_Z = 200f;
    /** Inline remove confirmation stays armed this long before reverting. */
    private static final long REMOVE_CONFIRM_WINDOW_MS = 3000L;
    private static final long DEFAULT_BUY_MINOR = 100L;

    private final Screen parent;
    /** null → ADD-HELD mode. */
    private final String listingId;
    /** ADD-HELD only: when true the two price boxes become barter output/ingredient counts. */
    private boolean barterMode;

    private int guiLeft;
    private int guiTop;
    private int modalW;
    private int modalH;
    /** False until the first {@link #init()} completes, so resize preserves typed values. */
    private boolean initialized;

    private EditBox nameBox;
    private EditBox buyBox;
    private EditBox sellBox;
    private EditBox stockBox;
    // Barter add-held mode: replaces buyBox / sellBox with the two trade-count inputs.
    private EditBox outputCountBox;
    private EditBox ingredientCountBox;

    /** Per-frame flat-button hit regions, populated in {@link #render}, consulted in mouseClicked. */
    private final java.util.List<ShopUiUtil.ClickZone> clickZones = new java.util.ArrayList<>();
    /** Tooltip to draw after super.render() (∞ unlimited-stock control). */
    private Component pendingTooltip;

    /** Category options snapshot (index 0 = All, i > 0 = categories.get(i - 1)). */
    private List<CatalogCategory> categories = List.of();
    private int categoryIdx;
    private long removeArmedUntilMs;
    // EDIT-mode icon preview, captured at init from the catalog row
    private String iconItemId = "";
    private String iconNbtJson = "";

    /** EDIT mode: prefills every field from the catalog row for {@code listingId}. */
    public AdminListingEditModal(Screen parent, String listingId) {
        super(Component.translatable("gui.futureshops.admin_edit.modal.title"));
        this.parent = parent;
        this.listingId = listingId;
    }

    /** ADD-HELD mode: previews the player's main-hand item; server captures id + NBT + name. */
    public AdminListingEditModal(Screen parent) {
        this(parent, false);
    }

    /**
     * ADD-HELD mode with an explicit buy/barter selector. When {@code barterMode} the modal captures
     * MAIN-hand = item sold, OFF-hand = payment; the two price boxes become output/ingredient counts.
     */
    public AdminListingEditModal(Screen parent, boolean barterMode) {
        super(Component.translatable(barterMode
                ? "gui.futureshops.admin_edit.modal.title_add_barter"
                : "gui.futureshops.admin_edit.modal.title_add"));
        this.parent = parent;
        this.listingId = null;
        this.barterMode = barterMode;
    }

    private boolean isAddHeldMode() {
        return listingId == null;
    }

    /** True only for the barter variant of the ADD-HELD flow. */
    private boolean isBarterAdd() {
        return barterMode && isAddHeldMode();
    }

    @Override
    protected void init() {
        modalW = Math.min(MODAL_W, this.width - 8);
        modalH = Math.min(MODAL_H, this.height - 8);
        guiLeft = (this.width - modalW) / 2;
        guiTop = (this.height - modalH) / 2;
        int fieldX = guiLeft + 70;
        int fieldW = modalW - 80;

        categories = ShopClientState.getCatalogCategories();
        if (categoryIdx > categories.size()) {
            categoryIdx = 0;
        }

        // Preserve typed values across re-init (resize); prefill on first init only.
        // NOTE: barter add-held never creates buyBox, so gate on an explicit `initialized` flag
        // rather than `buyBox != null` (which would re-default the count boxes on every resize).
        String namePrev;
        String buyPrev;
        String sellPrev;
        String stockPrev;
        String outputCountPrev;
        String ingredientCountPrev;
        if (initialized) {
            namePrev = nameBox != null ? nameBox.getValue() : "";
            buyPrev = buyBox != null ? buyBox.getValue() : ShopUiUtil.formatMinorUnits(DEFAULT_BUY_MINOR);
            sellPrev = sellBox != null ? sellBox.getValue() : ShopUiUtil.formatMinorUnits(0L);
            stockPrev = stockBox != null ? stockBox.getValue() : "∞";
            outputCountPrev = outputCountBox != null ? outputCountBox.getValue() : "1";
            ingredientCountPrev = ingredientCountBox != null ? ingredientCountBox.getValue() : "1";
        } else {
            namePrev = "";
            buyPrev = ShopUiUtil.formatMinorUnits(DEFAULT_BUY_MINOR);
            sellPrev = ShopUiUtil.formatMinorUnits(0L);
            stockPrev = "∞";
            outputCountPrev = "1";
            ingredientCountPrev = "1";
            if (!isAddHeldMode()) {
                CatalogItem item = ShopClientState.getCatalogItem(listingId).orElse(null);
                if (item != null) {
                    namePrev = item.displayName();
                    buyPrev = ShopUiUtil.formatMinorUnits(item.buyPrice());
                    sellPrev = ShopUiUtil.formatMinorUnits(item.sellPrice());
                    // Edit the CONFIGURED max (admin.json value), NOT the live remaining stock —
                    // otherwise a name/price-only save would rewrite the max down to what's left.
                    stockPrev = item.configuredStock() < 0 ? "∞" : Integer.toString(item.configuredStock());
                    iconItemId = item.itemId();
                    iconNbtJson = item.nbtJson() == null ? "" : item.nbtJson();
                    categoryIdx = indexOfCategory(item.categoryId());
                }
            }
        }

        // Display name row — EDIT mode only: ADD_HELD_ITEM derives the name from the held
        // stack's hover name server-side, so an editable box here would be silently ignored.
        if (!isAddHeldMode()) {
            nameBox = new EditBox(this.font, fieldX, guiTop + 54, fieldW, 14,
                    Component.translatable("gui.futureshops.admin_edit.modal.name_label"));
            nameBox.setMaxLength(NAME_MAX);
            nameBox.setValue(namePrev);
            addRenderableWidget(nameBox);
        }

        // Barter add-held: the two price rows become output-count / ingredient-count inputs;
        // otherwise the usual buy / sell price boxes.
        buyBox = null;
        sellBox = null;
        outputCountBox = null;
        ingredientCountBox = null;
        if (isBarterAdd()) {
            outputCountBox = new EditBox(this.font, fieldX, guiTop + 72, fieldW, 14,
                    Component.translatable("gui.futureshops.admin_edit.modal.output_count_label"));
            outputCountBox.setMaxLength(4);
            outputCountBox.setFilter(AdminListingEditModal::isCountText);
            outputCountBox.setValue(outputCountPrev);
            addRenderableWidget(outputCountBox);

            ingredientCountBox = new EditBox(this.font, fieldX, guiTop + 90, fieldW, 14,
                    Component.translatable("gui.futureshops.admin_edit.modal.ingredient_count_label"));
            ingredientCountBox.setMaxLength(4);
            ingredientCountBox.setFilter(AdminListingEditModal::isCountText);
            ingredientCountBox.setValue(ingredientCountPrev);
            addRenderableWidget(ingredientCountBox);
        } else {
            buyBox = new EditBox(this.font, fieldX, guiTop + 72, fieldW, 14,
                    Component.translatable("gui.futureshops.admin_edit.modal.buy_label"));
            buyBox.setMaxLength(10);
            buyBox.setFilter(AdminListingEditModal::isPriceText);
            buyBox.setValue(buyPrev);
            addRenderableWidget(buyBox);

            sellBox = new EditBox(this.font, fieldX, guiTop + 90, fieldW, 14,
                    Component.translatable("gui.futureshops.admin_edit.modal.sell_label"));
            sellBox.setMaxLength(10);
            sellBox.setFilter(AdminListingEditModal::isPriceText);
            sellBox.setValue(sellPrev);
            addRenderableWidget(sellBox);
        }

        stockBox = new EditBox(this.font, fieldX, guiTop + 108, fieldW - 20, 14,
                Component.translatable("gui.futureshops.admin_edit.modal.stock_label"));
        stockBox.setMaxLength(7);
        stockBox.setFilter(AdminListingEditModal::isStockText);
        stockBox.setValue(stockPrev);
        addRenderableWidget(stockBox);

        // Flat Nocturne buttons are drawn immediate-mode in render().
        initialized = true;
    }

    /** Draws every flat Nocturne button and registers its click zone for this frame. */
    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int fieldX = guiLeft + 70;
        int fieldW = modalW - 80;

        // ∞ unlimited-stock toggle
        boolean infHover = ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                fieldX + fieldW - 16, guiTop + 108, 16, 14, Component.literal("∞"),
                ShopUiUtil.ButtonStyle.SECONDARY, true,
                () -> stockBox.setValue("∞".equals(stockBox.getValue().trim()) ? "0" : "∞"));
        if (infHover) {
            pendingTooltip = Component.translatable("gui.futureshops.admin_edit.modal.unlimited_tooltip");
        }

        // Category selector: left/right cycle through All + admin categories
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                fieldX, guiTop + 126, 14, 14, Component.literal("<"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, () -> cycleCategory(-1));
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                fieldX + fieldW - 14, guiTop + 126, 14, 14, Component.literal(">"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, () -> cycleCategory(1));

        if (!isAddHeldMode()) {
            int halfW = (modalW - 24) / 2;
            // "Barter…" opens the dedicated ingredient editor (add ingredients one held item at a
            // time, set output count, remove barter). Returns to the shop when done. Replaces the
            // old single-shot "Barter off" button — that action now lives inside the editor.
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    guiLeft + 10, guiTop + 146, halfW, 14,
                    Component.translatable("gui.futureshops.admin_edit.modal.barter_edit"),
                    ShopUiUtil.ButtonStyle.SECONDARY, true, () -> {
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(new BarterRecipeEditorScreen(parent, listingId));
                        }
                    });
            boolean armed = removeArmedUntilMs != 0 && System.currentTimeMillis() < removeArmedUntilMs;
            Component removeLabel = Component.translatable(armed
                    ? "gui.futureshops.admin_edit.modal.remove_confirm"
                    : "gui.futureshops.admin_edit.modal.remove");
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    guiLeft + 14 + halfW, guiTop + 146, halfW, 14, removeLabel,
                    ShopUiUtil.ButtonStyle.DANGER, true, this::onRemoveClicked);
        }

        int btnW = (modalW - 24) / 2;
        boolean saveEnabled = true;
        if (isAddHeldMode()) {
            var player = this.minecraft != null ? this.minecraft.player : null;
            ItemStack held = player != null ? player.getMainHandItem() : ItemStack.EMPTY;
            saveEnabled = !held.isEmpty();
            // Barter add pays with the OFF-hand item; without it the server rejects the whole add
            // (EMPTY_OFFHAND) and nothing appears — so gate Save on both hands being filled.
            if (isBarterAdd() && (player == null || player.getOffhandItem().isEmpty())) {
                saveEnabled = false;
            }
        }
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 10, guiTop + modalH - 26, btnW, 16,
                Component.translatable("gui.futureshops.admin_edit.modal.save"),
                ShopUiUtil.ButtonStyle.PRIMARY, saveEnabled, this::save);
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 14 + btnW, guiTop + modalH - 26, btnW, 16,
                Component.translatable("gui.futureshops.modal.cancel"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, this::onClose);
    }

    private int indexOfCategory(String id) {
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).id().equals(id)) {
                return i + 1;
            }
        }
        return 0;
    }

    private void cycleCategory(int delta) {
        int count = categories.size() + 1;
        categoryIdx = Math.floorMod(categoryIdx + delta, count);
    }

    private String selectedCategoryId() {
        return categoryIdx <= 0 || categoryIdx > categories.size() ? "" : categories.get(categoryIdx - 1).id();
    }

    private String selectedCategoryName() {
        return categoryIdx <= 0 || categoryIdx > categories.size()
                ? Component.translatable("gui.futureshops.shop_main.tab_all").getString()
                : categories.get(categoryIdx - 1).displayName();
    }

    private void onRemoveClicked() {
        long now = System.currentTimeMillis();
        if (now < removeArmedUntilMs) {
            ShopPackets.CHANNEL.sendToServer(new C2SAdminShopEditPacket(
                    "REMOVE_LISTING", listingId, "", "", 0L, 0L, 0L));
            onClose();
            return;
        }
        removeArmedUntilMs = now + REMOVE_CONFIRM_WINDOW_MS;
    }

    private void save() {
        long stock = parseStock(stockBox.getValue());
        if (isBarterAdd()) {
            // Field order (action, targetId, stringA, stringB, longA, longB, longC):
            // stringB = category, longA = output count, longB = ingredient count, longC = stock.
            long outputCount = parseCount(outputCountBox.getValue());
            long ingredientCount = parseCount(ingredientCountBox.getValue());
            ShopPackets.CHANNEL.sendToServer(new C2SAdminShopEditPacket(
                    "ADD_HELD_ITEM_BARTER", "", "", selectedCategoryId(), outputCount, ingredientCount, stock));
            onClose();
            return;
        }
        long buyMinor = parsePriceMinor(buyBox.getValue());
        long sellMinor = parsePriceMinor(sellBox.getValue());
        if (isAddHeldMode()) {
            ShopPackets.CHANNEL.sendToServer(new C2SAdminShopEditPacket(
                    "ADD_HELD_ITEM", "", "", selectedCategoryId(), buyMinor, sellMinor, stock));
        } else {
            ShopPackets.CHANNEL.sendToServer(new C2SAdminShopEditPacket(
                    "SAVE_LISTING", listingId, nameBox.getValue().trim(), selectedCategoryId(),
                    buyMinor, sellMinor, stock));
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();
        pendingTooltip = null;
        // Modal renders over the live parent; offscreen mouse coords so the parent
        // shows no hover states beneath the dim.
        if (parent != null) {
            parent.render(graphics, -1, -1, partialTick);
            // Commit the parent's BATCHED TEXT + ITEM icons now — Minecraft flushes them in a later
            // layer, so without this they bleed up THROUGH the backdrop + modal panel.
            graphics.flush();
        }
        // Everything below is pushed in front of the parent's item icons (rendered at GUI item-z
        // ≈ 150). A plain z=0 fill would let those icons — and centered text like "No items found" —
        // poke through the modal. Translate past that depth, then lay a near-opaque backdrop.
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, MODAL_Z);
        graphics.fill(0, 0, this.width, this.height, MODAL_BACKDROP);

        graphics.fill(guiLeft, guiTop, guiLeft + modalW, guiTop + modalH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, modalW, modalH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        // Amber top accent = editing surface (mirrors ShopMainScreen edit mode)
        graphics.fill(guiLeft, guiTop, guiLeft + modalW, guiTop + 2, ShopColors.ACCENT_CURRENCY);

        graphics.drawString(this.font, this.title, guiLeft + 10, guiTop + 8, ShopColors.TEXT_STRONG, false);

        // Disarm the inline remove confirmation once the window lapses
        if (removeArmedUntilMs != 0 && System.currentTimeMillis() >= removeArmedUntilMs) {
            removeArmedUntilMs = 0;
        }

        renderPreview(graphics);

        if (!isAddHeldMode()) {
            drawLabel(graphics, "gui.futureshops.admin_edit.modal.name_label", guiTop + 57);
        }
        if (isBarterAdd()) {
            // Instruction line sits in the (unused) name-row gap; clipped to the modal width.
            String hint = Component.translatable("gui.futureshops.admin_edit.modal.barter_hint").getString();
            graphics.drawString(this.font, this.font.plainSubstrByWidth(hint, modalW - 20),
                    guiLeft + 10, guiTop + 55, ShopColors.TEXT_FAINT, false);
            drawLabel(graphics, "gui.futureshops.admin_edit.modal.output_count_label", guiTop + 75);
            drawLabel(graphics, "gui.futureshops.admin_edit.modal.ingredient_count_label", guiTop + 93);
        } else {
            drawLabel(graphics, "gui.futureshops.admin_edit.modal.buy_label", guiTop + 75);
            drawLabel(graphics, "gui.futureshops.admin_edit.modal.sell_label", guiTop + 93);
        }
        drawLabel(graphics, "gui.futureshops.admin_edit.modal.stock_label", guiTop + 111);
        drawLabel(graphics, "gui.futureshops.admin_edit.modal.category_label", guiTop + 129);

        // Current category between the < > cycle buttons
        int fieldX = guiLeft + 70;
        int fieldW = modalW - 80;
        String categoryName = this.font.plainSubstrByWidth(selectedCategoryName(), fieldW - 36);
        graphics.drawCenteredString(this.font, categoryName, fieldX + fieldW / 2, guiTop + 129, ShopColors.TEXT_STRONG);

        renderButtons(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popPose();

        if (pendingTooltip != null) {
            graphics.renderTooltip(this.font, pendingTooltip, mouseX, mouseY);
        }
    }

    private void renderPreview(GuiGraphics graphics) {
        int prevX = guiLeft + 10;
        int prevY = guiTop + 20;
        int prevW = modalW - 20;
        ShopUiUtil.renderCard(graphics, prevX, prevY, prevW, 30);

        if (isAddHeldMode()) {
            ItemStack held = this.minecraft != null && this.minecraft.player != null
                    ? this.minecraft.player.getMainHandItem()
                    : ItemStack.EMPTY;
            if (held.isEmpty()) {
                graphics.drawString(this.font, Component.translatable("gui.futureshops.admin_edit.modal.empty_hand"),
                        prevX + 8, prevY + 11, ShopColors.STATUS_DANGER, false);
                return;
            }
            graphics.renderItem(held, prevX + 7, prevY + 7);
            graphics.renderItemDecorations(this.font, held, prevX + 7, prevY + 7);
            graphics.drawString(this.font,
                    this.font.plainSubstrByWidth(held.getHoverName().getString(), prevW - 38),
                    prevX + 30, prevY + 11, ShopColors.TEXT_STRONG, false);
            return;
        }

        if (!iconItemId.isBlank()) {
            ShopUiUtil.renderItemIconWithNbt(graphics, this.font, iconItemId, iconNbtJson, prevX + 7, prevY + 7);
            graphics.drawString(this.font,
                    this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayNameWithNbt(iconItemId, iconNbtJson), prevW - 38),
                    prevX + 30, prevY + 11, ShopColors.TEXT_STRONG, false);
        }
    }

    private void drawLabel(GuiGraphics graphics, String key, int y) {
        graphics.drawString(this.font, Component.translatable(key), guiLeft + 10, y, ShopColors.TEXT_FAINT, false);
    }

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
    private static long parseCount(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return 1L;
        }
        try {
            return Math.max(1L, Long.parseLong(trimmed));
        } catch (NumberFormatException ignored) {
            return 1L;
        }
    }

    /** House price convention: display value is a double, wire value is minor units ×10^decimals. */
    private static long parsePriceMinor(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return 0L;
        }
        try {
            double parsed = Double.parseDouble(trimmed);
            return Math.max(0L, Math.round(parsed * Math.pow(10, ShopClientState.getCurrencyDecimals())));
        } catch (NumberFormatException ignored) {
            return 0L;
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Flat Nocturne buttons: run the top-most hit ClickZone first, then EditBox focus.
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        // The parent renders underneath every frame, so its layout must track resizes too.
        if (parent != null) {
            parent.resize(minecraft, width, height);
        }
        super.resize(minecraft, width, height);
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
