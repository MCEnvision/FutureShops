package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ClientRouteGuard;
import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.client.market.MarketCapabilityClientState;
import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.data.BulkSellTarget;
import com.enviouse.futureshops.data.CatalogCategory;
import com.enviouse.futureshops.data.CatalogItem;
import com.enviouse.futureshops.data.LocalShopOwnerEntry;
import com.enviouse.futureshops.data.NearbyShopEntry;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SAdminShopEditPacket;
import com.enviouse.futureshops.network.packets.C2SFetchLocalShopsPacket;
import com.enviouse.futureshops.network.packets.C2SOpenBalanceUiPacket;
import com.enviouse.futureshops.network.packets.C2SOpenMarketModulePacket;
import com.enviouse.futureshops.network.packets.C2SOpenShopPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopActionPacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Server-shop + player-shops browse, rebuilt onto the shared Nocturne shell (Phase 3).
 *
 * <p>Top-level tabs live in the header: <b>Server Shop</b> (the admin catalog browse) and
 * <b>Player Shops</b> (the nearby aggregation — mapped from the old {@code nearbyMode}). A third
 * "My Shop" tab is intentionally hidden until Phase 5 but the tab model is a plain array so adding
 * it later is a one-line change. Trade type is an All/Buy/Barter <b>segmented control</b> in the grid
 * toolbar rather than a sidebar pseudo-tab, and a Sort button client-sorts the
 * filtered items. The old top-bar buttons (Cart / History / Mode / Local / Close) are absorbed by the
 * shell: Cart moves to the footer, Close to the header X, Local becomes the Player Shops tab, History
 * is reached by clicking the balance pill, and the profile pill opens the balance overview.
 *
 * <p>Every prior behavior is preserved — see the per-region comments and the class-level task notes.
 */
public class ShopMainScreen extends Screen implements ShopScreenMarker {
    // Grid card geometry — shared by render, click hit-testing, and keyboard nav so the three code
    // paths can never disagree about where a card is.
    private static final int CARD_H = 62;
    private static final int GRID_GAP = 8;
    private static final int TOOLBAR_H = 22;
    /** Vertical room reserved below the toolbar for the edit-mode [+ Add Items]/[+ Held Item] row. */
    private static final int EDIT_GRID_HEADER_H = 24;
    /** Vertical room reserved at the sidebar bottom for edit-mode category management. */
    private static final int EDIT_SIDEBAR_RESERVED = 66;
    /** Vertical room reserved at the sidebar bottom for the visitor bulk sell action. */
    private static final int SELL_SIDEBAR_RESERVED = 28;
    private static final long CAPABILITY_RETRY_INTERVAL_MILLIS = 1_000L;

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private int headerH;
    private int breadcrumbH;
    private int footerH;
    private int sidebarW;

    private int gridScrollRows;
    private int sidebarScrollIdx;
    private int selectedCategoryIdx;   // 0 = "All"; 1..N = real category (sidebar dept selection)
    private int nearbyScrollIdx;

    /** 0 = All, 1 = Buy, 2 = Barter. All is the default so mixed departments never hide listings. */
    private int tradeFilter = ServerShopTradeFilterPolicy.defaultFilter().ordinal();
    private boolean nearbyMode;        // false = Server Shop tab, true = Player Shops tab
    private int sortMode;              // 0 = Name, 1 = Price, 2 = Stock

    private String searchQuery = "";
    // Raw (un-lowercased) search text so init()/resize can restore the box contents.
    private String searchTextRaw = "";

    // Admin edit mode — survives refreshAfterDataUpdate (init is not re-run there).
    private boolean editMode;

    // Bulk-select-and-delete within the OP editor grid. Only meaningful while editingGridActive().
    private boolean selectMode = false;
    /** listingIds toggled for bulk delete while {@link #selectMode} is on (insertion-ordered). */
    private final java.util.Set<String> selectedListingIds = new java.util.LinkedHashSet<>();

    private EditBox searchField;

    /** Per-frame flat-button hit regions (edit-mode controls); see ShopUiUtil.button / dispatchClicks. */
    private final java.util.List<ShopUiUtil.ClickZone> clickZones = new ArrayList<>();
    /** Deferred hover tooltip for a flat button, rendered on top after super.render(). */
    private Component pendingButtonTooltip;

    // Category-delete confirmation (composed overlay, ConfirmationModal-style)
    private ConfirmationModal confirmModal;

    // Inline text-prompt overlay (category add/rename): one EditBox + OK/Cancel above everything.
    private EditBox promptField;
    private Component promptTitle = Component.empty();
    private Consumer<String> promptAction;

    private List<CatalogItem> filteredItems = List.of();
    // Sidebar department labels + counts — rebuilt with the filter, never per frame.
    private List<String> deptLabels = List.of();
    private List<String> deptCounts = List.of();

    // Advanced tooltip tracking
    private String tooltipItemId = null;
    private String tooltipNbtJson = null;
    private int tooltipMouseX;
    private int tooltipMouseY;

    // Grid navigation state for arrow keys
    private int selectedGridIndex = -1;

    // ── Hit models stashed each frame so mouseClicked can route against the exact drawn geometry ──
    private ShopUiUtil.HeaderHit headerHit;
    /** capability refresh state for recovery retries. */
    private boolean capabilitiesRequested;
    private long lastCapabilityRequestAtMillis;
    private int[] footerCartRect;
    private int[] segEdges;
    private int segY;
    private int segH;
    private int[] sortRect;
    private int[] editToggleRect;

    public ShopMainScreen() {
        this(false);
    }

    public ShopMainScreen(boolean playerShops) {
        super(Component.translatable("gui.futureshops.shop.title"));
        nearbyMode = playerShops;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Layout helpers — one source of truth for every region rectangle.
    // ═══════════════════════════════════════════════════════════════════════

    private int contentX() { return guiLeft + 12; }
    private int contentY() { return guiTop + headerH + breadcrumbH; }
    private int contentW() { return guiW - 24; }
    private int contentH() { return guiH - headerH - breadcrumbH - footerH; }

    private int gridX() { return contentX() + sidebarW + 12; }
    private int gridY() { return contentY(); }
    private int gridW() { return contentX() + contentW() - gridX(); }
    private int gridH() { return contentH(); }

    private boolean compact() { return guiW < 560; }

    private String[] tabLabels() {
        // Base Shop tabs, then the module switcher (plan §7: every module shows the switcher).
        // Extra tabs map 1:1 onto moduleNavTargets() — keep both derivations in sync.
        List<String> labels = new ArrayList<>();
        labels.add(Component.translatable("gui.futureshops.shop.title").getString());
        labels.add(Component.translatable("gui.futureshops.shell.tab_player_shops").getString());
        for (MarketModule target : moduleNavTargets()) {
            labels.add(Component.translatable(target == MarketModule.BAZAAR
                    ? "gui.futureshops.shell.tab_bazaar"
                    : "gui.futureshops.shell.tab_auction").getString());
        }
        return labels.toArray(new String[0]);
    }

    /**
     * Modules offered in the Shop header switcher, in stable order. Server-authoritative: shown only
     * when the latest capability snapshot says navigation is on and the module is visible; the server
     * re-validates on open and falls back per MarketModuleAccessPolicy (a disabled module with open
     * claims opens as Claims Only rather than disappearing — claims are never hidden).
     */
    private List<MarketModule> moduleNavTargets() {
        return MarketCapabilityClientState.latest()
                .filter(snapshot -> snapshot.showNavigation())
                .map(snapshot -> {
                    List<MarketModule> targets = new ArrayList<>(2);
                    var byModule = snapshot.byModule();
                    for (MarketModule candidate : new MarketModule[]{
                            MarketModule.BAZAAR, MarketModule.AUCTION_HOUSE}) {
                        var capability = byModule.get(candidate);
                        if (capability != null && capability.availability().visible()) {
                            targets.add(candidate);
                        }
                    }
                    return targets;
                })
                .orElse(List.of());
    }

    /** Header-tab click for index ≥ 2 → open that market module (server routes + falls back). */
    private boolean switchToModule(int tabIndex) {
        List<MarketModule> targets = moduleNavTargets();
        int moduleIdx = tabIndex - 2;
        if (moduleIdx < 0 || moduleIdx >= targets.size()) {
            return false;
        }
        MarketModule target = targets.get(moduleIdx);
        // If the shell handed off to this screen its coordinator is still live and the open-response
        // gate only ACCEPTs requestIds it began — route through it (mirrors the shell's own
        // switchModule) or the response would be silently dropped. Fresh opens (no live shell
        // session) take the coordinator-less path, which the handler accepts as a new session.
        var coordinator = ShopClientPacketHandler.activeMarketNavigation();
        UUID requestId = UUID.randomUUID();
        String view = target.rootView();
        if (coordinator != null && coordinator.isOpen()) {
            var request = coordinator.beginSwitchModule(requestId, target);
            requestId = request.requestId();
            view = request.viewId();
        }
        ShopPackets.CHANNEL.sendToServer(new C2SOpenMarketModulePacket(
                requestId, target.id(), view));
        return true;
    }

    private boolean canEdit() {
        return ShopClientState.canEditAdminShop()
                && this.minecraft != null
                && this.minecraft.player != null
                && this.minecraft.player.hasPermissions(2);
    }

    /**
     * True when edit affordances apply to the grid. Edit mode covers BOTH the Buy and Barter
     * segments of the server shop (a card click edits the listing in either); only the Player-Shops
     * view behaves normally. Excluding barter here would let an admin who flips to the Barter segment
     * fall through to the buyer path and open a purchase while restocking.
     */
    private boolean editingGridActive() {
        return editMode && !nearbyMode;
    }

    @Override
    protected void init() {
        guiW = Math.max(320, this.width - 4);
        guiH = Math.max(200, this.height - 4);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;
        headerH = guiH < 300 ? 30 : 36;
        breadcrumbH = guiH < 300 ? 0 : 16;
        footerH = guiH < 300 ? 24 : 28;
        sidebarW = Math.min(194, Math.max(120, guiW / 5));

        // refresh capabilities so the header reflects server availability.
        if (!capabilitiesRequested && this.minecraft != null && this.minecraft.getConnection() != null) {
            capabilitiesRequested = true;
            requestMarketCapabilities();
        }

        rebuildFilteredItems();

        // ── Search EditBox positioned inside the header search pill ──
        String balance = ShopUiUtil.formatMinorUnits(ShopClientState.getCurrentBalanceMinorUnits());
        String playerName = this.minecraft != null && this.minecraft.player != null
                ? this.minecraft.player.getGameProfile().getName() : "";
        ShopUiUtil.HeaderHit hh = ShopUiUtil.headerLayout(this.font, guiLeft, guiTop, guiW, headerH,
                tabLabels(), balance, playerName, compact());
        int[] sr = hh.searchRect();
        searchField = new EditBox(this.font, sr[0] + 14, sr[1] + (sr[3] - 8) / 2, Math.max(24, sr[2] - 18), 8,
                Component.translatable("gui.futureshops.shop.search"));
        searchField.setBordered(false);
        searchField.setMaxLength(32);
        // Restore the text BEFORE wiring the responder so this programmatic set doesn't fire the
        // scroll-to-top reset on every init() (window resize / edit-mode toggle re-run init).
        searchField.setValue(searchTextRaw);
        searchField.setResponder(query -> {
            searchTextRaw = query;
            searchQuery = query.toLowerCase(Locale.ROOT);
            gridScrollRows = 0;
            rebuildFilteredItems();
        });
        addRenderableWidget(searchField);
        // Edit-mode controls (grid add buttons + sidebar category management) are drawn as flat
        // Nocturne buttons in renderEditControls().
    }

    @Override
    public void tick() {
        super.tick();
        long now = Util.getMillis();
        boolean recoverySnapshot = MarketCapabilityClientState.latest()
                .map(snapshot -> !snapshot.escrowReady())
                .orElse(true);
        if (capabilitiesRequested && recoverySnapshot
                && now - lastCapabilityRequestAtMillis
                >= CAPABILITY_RETRY_INTERVAL_MILLIS) {
            requestMarketCapabilities();
        }
    }

    private void requestMarketCapabilities() {
        if (this.minecraft != null
                && this.minecraft.getConnection() != null) {
            lastCapabilityRequestAtMillis = Util.getMillis();
            ShopClientPacketHandler.requestMarketCapabilities();
        }
    }

    /**
     * Edit-mode flat buttons: grid add row + sidebar category management. Drawn on top of the
     * server-shop panels each frame; only the currently-applicable buttons are drawn/registered.
     */
    private void renderEditControls(GuiGraphics graphics, int mouseX, int mouseY) {
        // ── Grid header row: [+ Add Items] | [+ Held Item] (below the toolbar) ──
        int rowX = gridX();
        int rowY = gridY() + TOOLBAR_H + 4;
        int addW = Math.min(84, Math.max(48,
                (gridW() - 2 * ShopUiUtil.PAD_XS - 62) / 3));
        int newOfferColumn = 2;
        AdminItemPickerScreen.QuickAddMode quickMode =
                quickAddMode();
        ShopUiUtil.button(
                graphics, this.font, clickZones, mouseX, mouseY,
                rowX, rowY, addW, 16,
                Component.translatable(
                        "gui.futureshops.admin_edit.add_items"),
                ShopUiUtil.ButtonStyle.PRIMARY, true,
                () -> this.minecraft.setScreen(
                        AdminItemPickerScreen.forQuickAdd(
                                this, activeCategoryId(), quickMode)));
        ShopUiUtil.button(
                graphics, this.font, clickZones, mouseX, mouseY,
                rowX + addW + ShopUiUtil.PAD_XS, rowY, addW, 16,
                Component.translatable(
                        "gui.futureshops.admin_edit.add_held"),
                ShopUiUtil.ButtonStyle.PRIMARY, true,
                () -> this.minecraft.setScreen(
                        AdminOfferEditorScreen.create(this)));
        int newOfferX = rowX
                + newOfferColumn * (addW + ShopUiUtil.PAD_XS);
        if (selectMode) {
            ShopUiUtil.button(graphics, this.font, clickZones,
                    mouseX, mouseY, newOfferX, rowY, addW, 16,
                    Component.translatable(
                            "gui.futureshops.admin_edit.remove_selected",
                            selectedListingIds.size()),
                    ShopUiUtil.ButtonStyle.DANGER,
                    !selectedListingIds.isEmpty(),
                    this::openBulkDeleteConfirm);
        } else {
            ShopUiUtil.button(graphics, this.font, clickZones,
                    mouseX, mouseY, newOfferX, rowY, addW, 16,
                    Component.translatable(
                            "gui.futureshops.admin_edit.new_offer"),
                    ShopUiUtil.ButtonStyle.PRIMARY, true,
                    () -> this.minecraft.setScreen(
                            AdminOfferEditorScreen.create(this)));
        }

        // ── Bulk select + delete: a Select toggle, then a Remove(N) button while selecting ──
        int selX = rowX
                + (newOfferColumn + 1) * (addW + ShopUiUtil.PAD_XS);
        int selW = Math.min(62,
                Math.max(48, rowX + gridW() - selX));
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY, selX, rowY, selW, 16,
                Component.translatable(selectMode
                        ? "gui.futureshops.admin_edit.done"
                        : "gui.futureshops.admin_edit.select"),
                selectMode ? ShopUiUtil.ButtonStyle.PRIMARY : ShopUiUtil.ButtonStyle.SECONDARY, true,
                () -> {
                    selectMode = !selectMode;
                    if (!selectMode) {
                        selectedListingIds.clear();
                    }
                });

        // ── Sidebar category management (bottom of the sidebar panel) ──
        int sbX = contentX();
        int sbY = contentY();
        int sbH = contentH();
        int ex = sbX + 6;
        int ew = sidebarW - 12;
        int ey = sbY + sbH - EDIT_SIDEBAR_RESERVED + 4;
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY, ex, ey, ew, 16,
                Component.translatable("gui.futureshops.admin_edit.category_add"), ShopUiUtil.ButtonStyle.PRIMARY, true,
                () -> openTextPrompt(
                        Component.translatable("gui.futureshops.admin_edit.category_add_prompt"), "",
                        name -> ShopPackets.CHANNEL.sendToServer(new C2SAdminShopEditPacket(
                                "ADD_CATEGORY", "", name, "", 0L, 0L, 0L))));

        // Rename / reorder / delete apply only to a real (non-"All") category selection.
        final CatalogCategory cat = selectedRealCategory();
        if (cat == null) {
            return;
        }
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY, ex, ey + 20, ew, 16,
                Component.translatable("gui.futureshops.admin_edit.category_rename"), ShopUiUtil.ButtonStyle.SECONDARY, true,
                () -> openTextPrompt(
                        Component.translatable("gui.futureshops.admin_edit.category_rename_prompt"),
                        cat.displayName(),
                        name -> ShopPackets.CHANNEL.sendToServer(new C2SAdminShopEditPacket(
                                "RENAME_CATEGORY", cat.id(), name, "", 0L, 0L, 0L))));
        int glyphW = (ew - 2 * ShopUiUtil.PAD_XS) / 3;
        boolean upHover = ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                ex, ey + 40, glyphW, 16, Component.literal("▲"), ShopUiUtil.ButtonStyle.SECONDARY, true,
                () -> sendSelectedCategoryMove(-1L));
        boolean downHover = ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                ex + glyphW + ShopUiUtil.PAD_XS, ey + 40, glyphW, 16, Component.literal("▼"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, () -> sendSelectedCategoryMove(1L));
        boolean delHover = ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                ex + 2 * (glyphW + ShopUiUtil.PAD_XS), ey + 40, glyphW, 16, Component.literal("§c✕"),
                ShopUiUtil.ButtonStyle.DANGER, true, this::openDeleteCategoryConfirm);
        if (upHover) {
            pendingButtonTooltip = Component.translatable("gui.futureshops.admin_edit.category_move_up");
        } else if (downHover) {
            pendingButtonTooltip = Component.translatable("gui.futureshops.admin_edit.category_move_down");
        } else if (delHover) {
            pendingButtonTooltip = Component.translatable("gui.futureshops.admin_edit.category_delete");
        }
    }

    private AdminItemPickerScreen.QuickAddMode quickAddMode() {
        return switch (tradeFilter) {
            case 2 -> AdminItemPickerScreen.QuickAddMode.SELL;
            case 3 -> AdminItemPickerScreen.QuickAddMode.BARTER;
            case 4 -> AdminItemPickerScreen.QuickAddMode.BUNDLE;
            default -> AdminItemPickerScreen.QuickAddMode.BUY;
        };
    }

    private void sendSelectedCategoryMove(long delta) {
        CatalogCategory cat = selectedRealCategory();
        if (cat != null) {
            ShopPackets.CHANNEL.sendToServer(new C2SAdminShopEditPacket(
                    "MOVE_CATEGORY", cat.id(), "", "", delta, 0L, 0L));
            // Optimistically follow the moved category so a second click acts on the same
            // one after the server resends (real categories occupy sidebar indices 1..N).
            int target = selectedCategoryIdx + (int) delta;
            if (target >= 1 && target <= ShopClientState.getCatalogCategories().size()) {
                selectedCategoryIdx = target;
            }
        }
    }

    /**
     * Bulk-delete confirmation for the selected listings. Confirm loops the existing per-listing
     * REMOVE_LISTING packet once per selected id (N writes + resends — acceptable, no new packet),
     * then clears the selection. Mirrors {@link #openDeleteCategoryConfirm()}.
     */
    private void openBulkDeleteConfirm() {
        if (selectedListingIds.isEmpty()) {
            return;
        }
        int count = selectedListingIds.size();
        confirmModal = new ConfirmationModal(
                Component.translatable("gui.futureshops.admin_edit.bulk_delete_title").getString(),
                List.of(ConfirmationModal.SummaryLine.text(
                        Component.translatable("gui.futureshops.admin_edit.bulk_delete_line", count).getString())),
                Component.translatable("gui.futureshops.admin_edit.bulk_delete_hint").getString(),
                modal -> {
                    for (String id : selectedListingIds) {
                        ShopPackets.CHANNEL.sendToServer(new C2SAdminShopEditPacket(
                                "REMOVE_LISTING", id, "", "", 0L, 0L, 0L));
                    }
                    selectedListingIds.clear();
                    selectMode = false;
                    confirmModal = null; // acks land in the footer status line
                },
                () -> confirmModal = null);
    }

    private void openDeleteCategoryConfirm() {
        CatalogCategory cat = selectedRealCategory();
        if (cat == null) {
            return;
        }
        confirmModal = new ConfirmationModal(
                Component.translatable("gui.futureshops.admin_edit.category_delete_title").getString(),
                List.of(ConfirmationModal.SummaryLine.text(cat.displayName())),
                Component.translatable("gui.futureshops.admin_edit.category_delete_hint").getString(),
                modal -> {
                    ShopPackets.CHANNEL.sendToServer(new C2SAdminShopEditPacket(
                            "REMOVE_CATEGORY", cat.id(), "", "", 0L, 0L, 0L));
                    confirmModal = null; // ack lands in the footer status line
                },
                () -> confirmModal = null);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Filtering / sorting / dept labels.
    // ═══════════════════════════════════════════════════════════════════════

    private void rebuildFilteredItems() {
        List<CatalogCategory> cats = ShopClientState.getCatalogCategories();
        List<CatalogItem> all = ShopClientState.getCatalogItems();
        String activeCategoryId = null;
        if (selectedCategoryIdx > 0 && selectedCategoryIdx <= cats.size()) {
            activeCategoryId = cats.get(selectedCategoryIdx - 1).id();
        }
        final String categoryFilter = activeCategoryId;
        List<CatalogItem> result = all.stream()
                // Barter tab: only listings with a barter recipe. Buy tab: everything EXCEPT
                // pure-barter listings (barter recipe + no money price) — those belong only under
                // Barter, so a barter add no longer also shows up as a bogus 0-cost Buy entry.
                .filter(item -> ServerShopTradeFilterPolicy.matches(
                        ServerShopTradeFilterPolicy.fromIndex(tradeFilter),
                        item, ShopClientState.getCatalogOffer(
                                item.listingId())))
                .filter(item -> categoryFilter == null || categoryFilter.equals(item.categoryId()))
                .filter(item -> searchQuery.isBlank()
                        || item.displayName().toLowerCase(Locale.ROOT).contains(searchQuery)
                        || item.itemId().toLowerCase(Locale.ROOT).contains(searchQuery))
                .collect(Collectors.toList());
        sortItems(result);
        filteredItems = result;
        rebuildTabLabels();
    }

    /** Client-side sort of the filtered grid — Name / Price / Stock. */
    private void sortItems(List<CatalogItem> items) {
        switch (sortMode) {
            case 1 -> items.sort(Comparator.comparingLong(this::effectivePrice)
                    .thenComparing(item -> item.displayName().toLowerCase(Locale.ROOT)));
            case 2 -> items.sort(Comparator.comparingInt(this::sortStock).reversed()
                    .thenComparing(item -> item.displayName().toLowerCase(Locale.ROOT)));
            default -> items.sort(Comparator.comparing(item -> item.displayName().toLowerCase(Locale.ROOT)));
        }
    }

    private long effectivePrice(CatalogItem item) {
        return item.hasPromo() ? item.promoPrice() : item.buyPrice();
    }

    private int sortStock(CatalogItem item) {
        return item.unlimited() ? Integer.MAX_VALUE : item.stock();
    }

    /** Sidebar department labels + counts — computed once per rebuild, never per frame. */
    private void rebuildTabLabels() {
        List<CatalogCategory> cats = ShopClientState.getCatalogCategories();
        List<CatalogItem> all = ShopClientState.getCatalogItems();
        Map<String, Integer> perCategory = new HashMap<>();
        for (CatalogItem item : all) {
            perCategory.merge(item.categoryId(), 1, Integer::sum);
        }
        List<String> labels = new ArrayList<>();
        List<String> counts = new ArrayList<>();
        labels.add(Component.translatable("gui.futureshops.shop_main.tab_all").getString());
        counts.add(Integer.toString(all.size()));
        for (CatalogCategory cat : cats) {
            labels.add(cat.displayName());
            counts.add(Integer.toString(perCategory.getOrDefault(cat.id(), 0)));
        }
        deptLabels = labels;
        deptCounts = counts;
    }

    /**
     * Called by ShopClientPacketHandler when new shop data arrives while this screen is already open.
     * Contract: only rebuildFilteredItems + reconcile the department selection + exit edit mode if
     * the OP grant was revoked. Never re-runs init(); nearbyMode / tradeFilter / editMode survive.
     */
    public void refreshAfterDataUpdate() {
        int cats = ShopClientState.getCatalogCategories().size();
        // A raw dept index no longer means the same category after the catalog changes (e.g. a
        // category was deleted, shifting the rows). Clamp back to "All" if it fell off the end.
        if (selectedCategoryIdx < 0 || selectedCategoryIdx > cats) {
            selectedCategoryIdx = 0;
        }
        rebuildFilteredItems();
        if (editMode && !ShopClientState.canEditAdminShop()) {
            editMode = false;
            selectMode = false;
            selectedListingIds.clear();
            closePrompt();
            confirmModal = null;
            rebuildWidgets();
        }
    }

    /** The selected REAL category, or null on the "All" pseudo-row. */
    private CatalogCategory selectedRealCategory() {
        List<CatalogCategory> cats = ShopClientState.getCatalogCategories();
        if (selectedCategoryIdx >= 1 && selectedCategoryIdx <= cats.size()) {
            return cats.get(selectedCategoryIdx - 1);
        }
        return null;
    }

    /** Category id of the selected dept, or "" on "All". */
    private String activeCategoryId() {
        CatalogCategory cat = selectedRealCategory();
        return cat == null ? "" : cat.id();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Render.
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();
        pendingButtonTooltip = null;
        tooltipItemId = null;
        tooltipNbtJson = null;

        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        ShopUiUtil.renderShellWindow(graphics, guiLeft, guiTop, guiW, guiH);

        // ── Header ──
        String balance = ShopUiUtil.formatMinorUnits(ShopClientState.getCurrentBalanceMinorUnits());
        Minecraft mc = this.minecraft;
        String playerName = mc != null && mc.player != null ? mc.player.getGameProfile().getName() : "";
        UUID uuid = mc != null && mc.player != null ? mc.player.getUUID() : null;
        headerHit = ShopUiUtil.renderShellHeader(graphics, this.font, guiLeft, guiTop, guiW, headerH,
                tabLabels(), nearbyMode ? 1 : 0, balance, playerName, uuid, compact(), mouseX, mouseY);

        // ── Breadcrumb ──
        if (breadcrumbH > 0) {
            renderBreadcrumbStrip(graphics);
        }

        // ── Content ──
        if (nearbyMode) {
            segEdges = null;
            sortRect = null;
            editToggleRect = null;
            renderPlayerShopsView(graphics, mouseX, mouseY);
        } else {
            renderSidebar(graphics, mouseX, mouseY);
            renderToolbar(graphics, mouseX, mouseY);
            renderGrid(graphics, mouseX, mouseY);
            if (editMode) {
                renderEditControls(graphics, mouseX, mouseY);
            }
        }

        // ── Footer ──
        footerCartRect = ShopUiUtil.renderShellFooter(graphics, this.font, contentX(),
                guiTop + guiH - footerH, contentW(), footerH, footerHint(),
                ShopClientState.getCartTotalQuantity(), mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);

        boolean overlayOpen = confirmModal != null || promptField != null;
        if (!overlayOpen) {
            ShopUiUtil.renderShellHeaderTooltip(graphics, this.font,
                    headerHit, mouseX, mouseY);
        }
        if (tooltipItemId != null && !overlayOpen) {
            ShopUiUtil.renderItemTooltip(graphics, this.font, tooltipItemId,
                    tooltipNbtJson != null ? tooltipNbtJson : "", tooltipMouseX, tooltipMouseY);
        }
        if (pendingButtonTooltip != null && !overlayOpen) {
            graphics.renderTooltip(this.font, pendingButtonTooltip, mouseX, mouseY);
        }
        if (confirmModal != null) {
            confirmModal.render(graphics, this.font, this.width, this.height, mouseX, mouseY);
            if (confirmModal.shouldAutoDismiss()) {
                confirmModal = null;
            }
        }
        if (promptField != null) {
            renderPromptOverlay(graphics, mouseX, mouseY, partialTick);
        }
    }

    /** Footer hint — admin-edit acks and status messages surface here in the redesign. */
    private String footerHint() {
        ShopClientState.ShopStatus status = ShopClientState.getStatus();
        if (status != null) {
            return (status.success() ? "§a" : "§c") + status.message().getString();
        }
        if (nearbyMode) {
            return Component.translatable("gui.futureshops.shell.footer_player_shops").getString();
        }
        if (editMode) {
            return Component.translatable("gui.futureshops.admin_edit.footer_help").getString();
        }
        if (tradeFilter == 3) {
            return Component.translatable("gui.futureshops.shop_main.footer.barter_active").getString();
        }
        return Component.translatable("gui.futureshops.shop_main.footer.help").getString();
    }

    private void renderBreadcrumbStrip(GuiGraphics graphics) {
        int x = contentX();
        int y = guiTop + headerH + (breadcrumbH - 8) / 2;
        int w = contentW();
        String marketplace = Component.translatable("gui.futureshops.shell.brand_sub").getString();
        if (nearbyMode) {
            String[] crumbs = {
                    marketplace,
                    Component.translatable("gui.futureshops.shell.tab_player_shops").getString()
            };
            int shops = ShopClientState.getLocalShopOwners().size();
            String right = Component.translatable("gui.futureshops.shell.crumb_shops", shops).getString();
            ShopUiUtil.renderBreadcrumb(graphics, this.font, x, y, w, crumbs, right);
        } else {
            String dept = selectedCategoryIdx == 0
                    ? Component.translatable("gui.futureshops.shop_main.tab_all").getString()
                    : deptLabels.get(Math.min(selectedCategoryIdx, deptLabels.size() - 1));
            String[] crumbs = { marketplace, prettyName(ShopClientState.getActiveShopId()), dept };
            String right = Component.translatable("gui.futureshops.shell.crumb_items", filteredItems.size()).getString();
            ShopUiUtil.renderBreadcrumb(graphics, this.font, x, y, w, crumbs, right);
        }
    }

    // ── Server-shop sidebar ──

    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = contentX();
        int y = contentY();
        int h = contentH();
        ShopUiUtil.renderNocturnePanel(graphics, x, y, sidebarW, h);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.shop_main.departments"),
                x + 12, y + 8, ShopColors.NEUTRAL_500, false);

        int reserved = editMode
                ? EDIT_SIDEBAR_RESERVED
                : SELL_SIDEBAR_RESERVED;
        int rowH = 20;
        int listY = y + 22;
        int listH = h - 22 - reserved;
        int count = deptLabels.size();
        int maxVisible = Math.max(1, listH / rowH);
        sidebarScrollIdx = Math.max(0, Math.min(sidebarScrollIdx, Math.max(0, count - maxVisible)));

        for (int i = sidebarScrollIdx; i < count && i < sidebarScrollIdx + maxVisible; i++) {
            int rowY = listY + (i - sidebarScrollIdx) * rowH;
            boolean selected = i == selectedCategoryIdx;
            boolean hovered = mouseX >= x + 4 && mouseX <= x + sidebarW - 4 && mouseY >= rowY && mouseY < rowY + rowH - 2;
            ShopUiUtil.renderDeptRow(graphics, this.font, x + 4, rowY, sidebarW - 8, rowH - 2,
                    deptLabels.get(i), i < deptCounts.size() ? deptCounts.get(i) : "", selected, hovered);
        }

        ShopUiUtil.renderScrollIndicators(graphics, this.font, x, listY, sidebarW, listH, sidebarScrollIdx, maxVisible, count);

        if (editMode) {
            ShopUiUtil.renderFadingRule(graphics, x + 6, y + h - reserved + 2, sidebarW - 12);
        } else {
            int buttonY = y + h - SELL_SIDEBAR_RESERVED + 4;
            ShopUiUtil.renderFadingRule(
                    graphics, x + 6, buttonY - 4, sidebarW - 12);
            ShopUiUtil.button(
                    graphics, this.font, clickZones,
                    mouseX, mouseY,
                    x + 6, buttonY, sidebarW - 12, 18,
                    Component.translatable(
                            "gui.futureshops.bulk_sell.open"),
                    ShopUiUtil.ButtonStyle.PRIMARY,
                    ShopClientState.isAdminShopEnabled(),
                    () -> this.minecraft.setScreen(
                            new BulkSellModeScreen(
                                    this,
                                    BulkSellTarget.ADMIN_SHOP,
                                    ShopClientState.getActiveShopId())));
        }
    }

    // ── Server-shop grid toolbar ──

    private void renderToolbar(GuiGraphics graphics, int mouseX, int mouseY) {
        int tx = gridX();
        int ty = gridY();
        segH = 18;
        segY = ty + (TOOLBAR_H - segH) / 2;

        int rightX = gridX() + gridW();
        if (canEdit()) {
            String editLabel = Component.translatable(editMode
                    ? "gui.futureshops.shell.edit_on" : "gui.futureshops.shell.edit").getString();
            int editW = Math.min(this.font.width(editLabel) + 16,
                    Math.max(34, gridW() / 4));
            int editX = rightX - editW;
            editToggleRect = new int[]{editX, segY, editW, segH};
            boolean hover = inRect(editToggleRect, mouseX, mouseY);
            ShopUiUtil.renderNocturnePanel(graphics, editX, segY, editW, segH, ShopColors.SURFACE_RAISED,
                    editMode || hover ? ShopColors.ACCENT_PRIMARY : ShopColors.BORDER_MUTED);
            String clippedEdit = this.font.plainSubstrByWidth(
                    editLabel, editW - 8);
            graphics.drawCenteredString(this.font, clippedEdit, editX + editW / 2, segY + (segH - 8) / 2,
                    editMode ? ShopColors.ACCENT_300 : ShopColors.TEXT_MUTED);
            if (hover && !clippedEdit.equals(editLabel)) {
                pendingButtonTooltip = Component.literal(editLabel);
            }
            rightX = editX - 8;
        } else {
            editToggleRect = null;
        }

        String sortLabel = Component.translatable(switch (sortMode) {
            case 1 -> "gui.futureshops.shell.sort_price";
            case 2 -> "gui.futureshops.shell.sort_stock";
            default -> "gui.futureshops.shell.sort_name";
        }).getString();
        int sortW = Math.min(this.font.width(sortLabel) + 18,
                Math.max(34, gridW() / 4));
        int sortX = rightX - sortW;
        sortRect = new int[]{sortX, segY, sortW, segH};
        boolean sortHover = inRect(sortRect, mouseX, mouseY);
        ShopUiUtil.renderNocturnePanel(graphics, sortX, segY, sortW, segH, ShopColors.SURFACE_RAISED,
                sortHover ? ShopColors.BORDER_STRONG : ShopColors.BORDER_MUTED);
        String clippedSort = this.font.plainSubstrByWidth(
                sortLabel, sortW - 8);
        graphics.drawCenteredString(this.font, clippedSort,
                sortX + sortW / 2, segY + (segH - 8) / 2,
                ShopColors.TEXT_MUTED);
        if (sortHover && !clippedSort.equals(sortLabel)) {
            pendingButtonTooltip = Component.literal(sortLabel);
        }

        String[] segLabels = {
                Component.translatable("gui.futureshops.shell.seg_all").getString(),
                Component.translatable("gui.futureshops.shell.seg_buy").getString(),
                Component.translatable("gui.futureshops.shell.seg_sell").getString(),
                Component.translatable("gui.futureshops.shell.seg_barter").getString(),
                Component.translatable("gui.futureshops.shell.seg_bundles").getString()
        };
        int segmentBudget = Math.max(0, sortX - 8 - tx);
        segEdges = renderResponsiveSegments(graphics, tx, segY,
                segH, segmentBudget, segLabels, tradeFilter,
                mouseX, mouseY);
        int cursorX = segEdges == null
                ? tx : segEdges[segEdges.length - 1] + 8;
        if (editMode && !compact() && segEdges != null) {
            String editing = Component.translatable(
                    "gui.futureshops.admin_edit.editing_pill")
                    .getString();
            int tagW = Math.max(20, this.font.width(editing) + 12);
            if (cursorX + tagW <= sortX - 8) {
                ShopUiUtil.renderTag(graphics, this.font,
                        cursorX, segY + 2, editing,
                        ShopUiUtil.TagStyle.ACCENT);
            }
        }
    }

    private int[] renderResponsiveSegments(
            GuiGraphics graphics,
            int x,
            int y,
            int height,
            int maximumWidth,
            String[] labels,
            int active,
            int mouseX,
            int mouseY
    ) {
        if (maximumWidth < labels.length) {
            return null;
        }
        int naturalWidth = 0;
        for (String label : labels) {
            naturalWidth += this.font.width(label) + 22;
        }
        if (naturalWidth <= maximumWidth) {
            return ShopUiUtil.renderSegmented(graphics, this.font,
                    x, y, height, labels, active);
        }
        int[] edges = new int[labels.length + 1];
        edges[0] = x;
        int baseWidth = maximumWidth / labels.length;
        int remainder = maximumWidth % labels.length;
        for (int index = 0; index < labels.length; index++) {
            int segmentWidth = baseWidth
                    + (index < remainder ? 1 : 0);
            int segmentX = edges[index];
            edges[index + 1] = segmentX + segmentWidth;
            boolean selected = index == active;
            ShopUiUtil.renderNocturnePanel(graphics,
                    segmentX, y, segmentWidth, height,
                    selected ? ShopColors.SURFACE_PRESSED
                            : ShopColors.SURFACE_RAISED,
                    selected ? ShopColors.ACCENT_PRIMARY
                            : ShopColors.BORDER_MUTED);
            String clipped = this.font.plainSubstrByWidth(
                    labels[index], Math.max(1, segmentWidth - 6));
            graphics.drawCenteredString(this.font, clipped,
                    segmentX + segmentWidth / 2,
                    y + (height - 8) / 2,
                    selected ? ShopColors.ACCENT_300
                            : ShopColors.TEXT_MUTED);
            if (!clipped.equals(labels[index])
                    && mouseX >= segmentX
                    && mouseX < segmentX + segmentWidth
                    && mouseY >= y && mouseY < y + height) {
                pendingButtonTooltip =
                        Component.literal(labels[index]);
            }
        }
        return edges;
    }

    // ── Server-shop grid ──

    /** Grid geometry snapshot shared by render, click handling, and keyboard nav. */
    private record GridMetrics(int gridX, int gridY, int gridW, int gridH,
                               int contentX, int contentY, int contentW, int contentH,
                               int columns, int cardW, int visibleRows, int totalRows) {
    }

    private GridMetrics gridMetrics() {
        int gx = gridX();
        int gy = gridY();
        int gw = gridW();
        int gh = gridH();
        int editHdr = editingGridActive() ? EDIT_GRID_HEADER_H : 0;
        int itemsX = gx;
        int itemsY = gy + TOOLBAR_H + editHdr;
        int itemsW = gw;
        int itemsH = gh - TOOLBAR_H - editHdr;
        int cardMinW = 190;
        int columns = Math.max(1, Math.min(4, (itemsW + GRID_GAP) / (cardMinW + GRID_GAP)));
        int cardW = Math.max(150, (itemsW - GRID_GAP * (columns - 1)) / columns);
        int visibleRows = Math.max(1, (itemsH + GRID_GAP) / (CARD_H + GRID_GAP));
        int totalRows = (filteredItems.size() + columns - 1) / columns;
        return new GridMetrics(gx, gy, gw, gh, itemsX, itemsY, itemsW, itemsH,
                columns, cardW, visibleRows, totalRows);
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        GridMetrics m = gridMetrics();

        if (filteredItems.isEmpty()) {
            int cx = m.gridX() + m.gridW() / 2;
            int cy = m.contentY() + m.contentH() / 2;
            // package glyph (drawn box) + message
            graphics.fill(cx - 10, cy - 24, cx + 10, cy - 8, ShopColors.SURFACE_RAISED);
            ShopUiUtil.drawBorder(graphics, cx - 10, cy - 24, 20, 16, ShopColors.BORDER_MUTED);
            if (!ShopClientState.isAdminShopEnabled()) {
                graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.shop_main.admin_disabled"), cx, cy, ShopColors.TEXT_SECONDARY);
                graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.shop_main.admin_disabled_hint"), cx, cy + 14, ShopColors.TEXT_FAINT);
            } else if (editingGridActive()) {
                graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.shop.no_items"), cx, cy, ShopColors.TEXT_SECONDARY);
                graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.admin_edit.empty_hint"), cx, cy + 14, ShopColors.TEXT_FAINT);
            } else {
                graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.shop.no_items"), cx, cy, ShopColors.TEXT_SECONDARY);
                graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.shop.no_items_hint"), cx, cy + 14, ShopColors.TEXT_FAINT);
            }
            return;
        }

        gridScrollRows = Math.max(0, Math.min(gridScrollRows, Math.max(0, m.totalRows() - m.visibleRows())));

        for (int index = 0; index < filteredItems.size(); index++) {
            int row = index / m.columns();
            if (row < gridScrollRows || row >= gridScrollRows + m.visibleRows()) {
                continue;
            }
            int visibleRow = row - gridScrollRows;
            int col = index % m.columns();
            int cardX = m.contentX() + col * (m.cardW() + GRID_GAP);
            int cardY = m.contentY() + visibleRow * (CARD_H + GRID_GAP);
            renderItemCard(graphics, filteredItems.get(index), cardX, cardY, m.cardW(), CARD_H,
                    mouseX, mouseY, index == selectedGridIndex);
        }

        ShopUiUtil.renderScrollIndicators(graphics, this.font, m.gridX(), m.contentY(), m.gridW(),
                m.contentH(), gridScrollRows, m.visibleRows(), m.totalRows());
    }

    private void renderItemCard(GuiGraphics graphics, CatalogItem item, int x, int y, int width, int height,
                                int mouseX, int mouseY, boolean keySelected) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        boolean bulkSelected = selectMode && editingGridActive() && selectedListingIds.contains(item.listingId());
        boolean highlighted = hovered || keySelected || bulkSelected;
        boolean outOfStock = !item.unlimited() && item.stock() <= 0;
        int border = bulkSelected ? ShopColors.ACCENT_PRIMARY
                : (highlighted ? ShopColors.BORDER_GLOW : ShopColors.BORDER_MUTED);
        int fill = bulkSelected ? ShopColors.SURFACE_PRESSED
                : (highlighted ? ShopColors.SURFACE_OVERLAY : ShopColors.SURFACE_RAISED);
        ShopUiUtil.renderNocturnePanel(graphics, x, y, width, height, fill, border);
        if (bulkSelected) {
            // ✓ chip in the top-left corner (top-right is reserved for the promo tag).
            graphics.fill(x + 2, y + 2, x + 12, y + 12, ShopColors.SURFACE_BASE);
            graphics.drawString(this.font, "✓", x + 3, y + 3, ShopColors.ACCENT_PRIMARY, false);
        }

        // icon slot (rounded, inset border)
        int slot = 40;
        int slotX = x + 8;
        int slotY = y + (height - slot) / 2;
        ShopUiUtil.renderNocturnePanel(graphics, slotX, slotY, slot, slot, ShopColors.SURFACE_BASE, ShopColors.BORDER_MUTED);
        String nbt = item.nbtJson();
        ShopUiUtil.renderItemIconWithNbt(graphics, this.font, item.itemId(), nbt,
                slotX + (slot - 16) / 2, slotY + (slot - 16) / 2);
        ServerShopOfferListing offer = ShopClientState
                .getCatalogOffer(item.listingId()).orElse(null);

        int tx = slotX + slot + 8;
        int tRight = x + width - 8;

        // name row (+ promo chip on the right)
        int nameY = y + 8;
        int nameBudget = tRight - tx;
        if (item.hasPromo() && offer == null) {
            int percent = ShopUiUtil.computePromoPercent(item.buyPrice(), item.promoPrice());
            if (percent > 0) {
                String promoLabel = percent >= 100
                        ? Component.translatable("gui.futureshops.player_shop_block.detail.promo_free").getString()
                        : "-" + percent + "%";
                int tagW = Math.max(20, this.font.width(promoLabel) + 12);
                ShopUiUtil.renderTag(graphics, this.font, tRight - tagW, nameY - 2, promoLabel, ShopUiUtil.TagStyle.ACCENT);
                nameBudget -= tagW + 6;
            }
        } else if (offer != null && offer.bundle()) {
            String bundle = Component.translatable(
                    "gui.futureshops.offer.bundle").getString();
            int tagW = Math.max(20, this.font.width(bundle) + 12);
            ShopUiUtil.renderTag(graphics, this.font,
                    tRight - tagW, nameY - 2, bundle,
                    ShopUiUtil.TagStyle.ACCENT2);
            nameBudget -= tagW + 6;
        }
        ShopUiUtil.renderScrollingString(graphics, this.font, item.displayName(), tx, nameY,
                Math.max(20, nameBudget), ShopColors.TEXT_STRONG);

        // price row (coin + price, struck base if promo)
        int priceY = y + 24;
        long price = effectivePrice(item);
        boolean barterOnly = offer == null
                && item.hasBarterRecipes()
                && item.buyPrice() <= 0 && item.sellPrice() <= 0;
        int coinW = 0;
        String offerPrice = offer == null
                ? "" : offerCardPrice(offer);
        boolean offerMoneyPrice = offer != null
                && offer.acquireOptions().size() == 1
                && offer.acquireOptions().get(0).moneyCostPresent()
                && !offer.acquireOptions().get(0).hasItemCosts()
                && !offer.acquireOptions().get(0).free();
        if (offer != null && !offerMoneyPrice) {
            graphics.drawString(this.font,
                    this.font.plainSubstrByWidth(
                            offerPrice, tRight - tx),
                    tx, priceY,
                    offer.sellOnly()
                            ? ShopColors.TEXT_SECONDARY
                            : ShopColors.TEXT_BARTER,
                    false);
        } else if (barterOnly) {
            graphics.drawString(this.font, Component.translatable("gui.futureshops.storefront.barter_only"),
                    tx, priceY, ShopColors.TEXT_BARTER, false);
        } else {
            if (offerMoneyPrice) {
                price = offer.acquireOptions().get(0)
                        .moneyCostMinorUnits();
            }
            coinW = ShopUiUtil.renderCoinAmount(graphics, this.font, tx, priceY,
                    ShopUiUtil.formatMinorUnits(price), outOfStock ? ShopColors.TEXT_FAINT : ShopColors.TEXT_STRONG);
        }
        if (item.hasPromo() && offer == null && !barterOnly) {
            String base = ShopUiUtil.formatMinorUnits(item.buyPrice());
            graphics.drawString(this.font,
                    Component.literal(base).withStyle(net.minecraft.ChatFormatting.STRIKETHROUGH),
                    tx + coinW + 6, priceY, ShopColors.NEUTRAL_600, false);
        }

        // stock row (colored) + optional Trade tag
        int stockY = y + 40;
        String stockStr;
        int stockColor;
        if (outOfStock) {
            stockStr = Component.translatable("gui.futureshops.shop_main.sold_out").getString();
            stockColor = ShopColors.STATUS_DANGER;
        } else if (item.unlimited()) {
            stockStr = Component.translatable("gui.futureshops.shop_main.stock_unlimited").getString();
            stockColor = ShopColors.NEUTRAL_500;
        } else {
            stockStr = Component.translatable("gui.futureshops.shop_main.stock_left", item.stock()).getString();
            stockColor = item.stock() <= 5 ? ShopColors.STATUS_WARNING : ShopColors.NEUTRAL_500;
        }
        graphics.drawString(this.font, stockStr, tx, stockY, stockColor, false);
        if (offer != null) {
            int sWidth = this.font.width(stockStr);
            String badge = offerCardBadge(offer);
            int available = Math.max(12,
                    tRight - Math.min(tx + sWidth + 6, tRight));
            String clippedBadge = this.font.plainSubstrByWidth(
                    badge, Math.max(1, available - 12));
            int badgeWidth = Math.max(20,
                    this.font.width(clippedBadge) + 12);
            int badgeX = Math.min(tx + sWidth + 6,
                    tRight - badgeWidth);
            ShopUiUtil.renderTag(graphics, this.font,
                    badgeX,
                    stockY - 2, clippedBadge,
                    ShopUiUtil.TagStyle.OUTLINE);
            if (!clippedBadge.equals(badge)
                    && mouseX >= badgeX
                    && mouseX < badgeX + badgeWidth
                    && mouseY >= stockY - 2
                    && mouseY < stockY + 12) {
                pendingButtonTooltip = Component.literal(badge);
            }
        } else if (item.hasBarterRecipes()) {
            int sWidth = this.font.width(stockStr);
            ShopUiUtil.renderTag(graphics, this.font,
                    Math.min(tx + sWidth + 6, tRight - 40),
                    stockY - 2,
                    Component.translatable(
                            "gui.futureshops.shell.trade_tag")
                            .getString(),
                    ShopUiUtil.TagStyle.OUTLINE);
        }

        // advanced tooltip on hover
        if (hovered) {
            tooltipItemId = item.itemId();
            tooltipNbtJson = nbt == null ? "" : nbt;
            tooltipMouseX = mouseX;
            tooltipMouseY = mouseY;
        }
    }

    private String offerCardPrice(ServerShopOfferListing offer) {
        if (offer.sellOnly()) {
            if (offer.sellOptions().size() == 1) {
                return Component.translatable(
                        "gui.futureshops.offer.sell_to_shop")
                        .getString() + " "
                        + ShopUiUtil.formatMinorUnits(
                        offer.sellOptions().get(0)
                                .moneyPayoutMinorUnits());
            }
            return Component.translatable(
                    "gui.futureshops.offer.option_count",
                    offer.sellOptions().size()).getString();
        }
        if (offer.acquireOptions().size() != 1) {
            return Component.translatable(
                    "gui.futureshops.offer.option_count",
                    offer.acquireOptions().size()).getString();
        }
        AcquireOfferOption option = offer.acquireOptions().get(0);
        if (option.free()) {
            return Component.translatable(
                    "gui.futureshops.offer.free").getString();
        }
        if (option.compound()) {
            return Component.translatable(
                    "gui.futureshops.offer.money_and_barter")
                    .getString();
        }
        if (option.hasItemCosts()) {
            return Component.translatable(
                    "gui.futureshops.offer.barter").getString();
        }
        return ShopUiUtil.formatMinorUnits(
                option.moneyCostMinorUnits());
    }

    private String offerCardBadge(ServerShopOfferListing offer) {
        if (offer.sellOnly()) {
            return Component.translatable(
                    "gui.futureshops.offer.sell_only").getString();
        }
        if (offer.acquireOptions().stream()
                .anyMatch(AcquireOfferOption::free)) {
            return Component.translatable(
                    "gui.futureshops.offer.free").getString();
        }
        boolean money = offer.acquireOptions().stream()
                .anyMatch(AcquireOfferOption::moneyCostPresent);
        boolean barter = offer.acquireOptions().stream()
                .anyMatch(AcquireOfferOption::hasItemCosts);
        if (money && barter) {
            return Component.translatable(
                    "gui.futureshops.offer.money_and_barter")
                    .getString();
        }
        return Component.translatable(money
                ? "gui.futureshops.offer.money"
                : "gui.futureshops.offer.barter").getString();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Player Shops view (nearby aggregation) — design's PLAYER SHOPS LIST section.
    // ═══════════════════════════════════════════════════════════════════════

    private record ShopGrid(int gx, int gy, int gw, int gh, int cols, int cardW, int cardH,
                            int visRows, int totalRows) {
    }

    private ShopGrid shopGrid(int ownerCount) {
        int gx = contentX();
        int gy = contentY();
        int gw = contentW();
        int gh = contentH();
        int gap = 12;
        int cardMinW = 300;
        int cardH = 96;
        int cols = Math.max(1, Math.min(3, (gw + gap) / (cardMinW + gap)));
        int cardW = (gw - gap * (cols - 1)) / cols;
        int visRows = Math.max(1, (gh + gap) / (cardH + gap));
        int totalRows = (ownerCount + cols - 1) / cols;
        return new ShopGrid(gx, gy, gw, gh, cols, cardW, cardH, visRows, totalRows);
    }

    private void renderPlayerShopsView(GuiGraphics graphics, int mouseX, int mouseY) {
        List<LocalShopOwnerEntry> owners = ShopClientState.getLocalShopOwners();

        if (owners.isEmpty()) {
            List<NearbyShopEntry> nearby = ShopClientState.getNearbyShops();
            if (nearby.isEmpty()) {
                int cx = contentX() + contentW() / 2;
                int cy = contentY() + contentH() / 2;
                graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.shop_main.no_nearby"), cx, cy - 8, ShopColors.TEXT_SECONDARY);
                graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.shop_main.no_nearby_hint"), cx, cy + 6, ShopColors.TEXT_FAINT);
                return;
            }
            renderLegacyNearbyGrid(graphics, mouseX, mouseY, nearby);
            return;
        }

        ShopGrid g = shopGrid(owners.size());
        int gap = 12;
        nearbyScrollIdx = Math.max(0, Math.min(nearbyScrollIdx, Math.max(0, g.totalRows() - g.visRows())));

        for (int i = 0; i < owners.size(); i++) {
            int row = i / g.cols();
            if (row < nearbyScrollIdx || row >= nearbyScrollIdx + g.visRows()) {
                continue;
            }
            int col = i % g.cols();
            int cx = g.gx() + col * (g.cardW() + gap);
            int cy = g.gy() + (row - nearbyScrollIdx) * (g.cardH() + gap);
            renderShopCard(graphics, owners.get(i), cx, cy, g.cardW(), g.cardH(), mouseX, mouseY);
        }

        ShopUiUtil.renderScrollIndicators(graphics, this.font, g.gx(), g.gy(), g.gw(), g.gh(),
                nearbyScrollIdx, g.visRows(), g.totalRows());
    }

    private void renderShopCard(GuiGraphics graphics, LocalShopOwnerEntry owner, int x, int y, int w, int h,
                                int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        ShopUiUtil.renderNocturnePanel(graphics, x, y, w, h, hovered ? ShopColors.SURFACE_OVERLAY : ShopColors.SURFACE_RAISED,
                hovered ? ShopColors.BORDER_GLOW : ShopColors.BORDER_MUTED);
        if (hovered) {
            ShopUiUtil.renderAccentLine(graphics, x + 2, y, w - 4);
        }

        // owner head
        int head = 34;
        ShopUiUtil.renderPlayerFace(graphics, owner.ownerUuid(), owner.displayName(),
                x + 12, y + 12, head);

        int infoX = x + 12 + head + 12;
        int infoRight = x + w - 12;

        // distance (right-aligned on the name row)
        String dist = Component.translatable("gui.futureshops.shell.distance",
                String.format(Locale.ROOT, "%.0f", owner.closestDistance())).getString();
        int distW = this.font.width(dist);
        graphics.drawString(this.font, dist, infoRight - distW, y + 14, ShopColors.NEUTRAL_400, false);

        // owner name (title) + optional franchise flag tag — the franchise is a distinct piece of
        // info from the owner name, so both are shown without duplication.
        String title = owner.displayName();
        boolean hasFranchise = !owner.franchiseName().isBlank();
        int franchiseW = hasFranchise ? Math.max(20, this.font.width(owner.franchiseName()) + 12) : 0;
        int titleBudget = Math.max(20, (infoRight - distW - 8) - infoX - (franchiseW > 0 ? franchiseW + 6 : 0));
        ShopUiUtil.renderScrollingString(graphics, this.font, title, infoX, y + 14, titleBudget, ShopColors.TEXT_STRONG);
        if (hasFranchise) {
            int tagX = infoX + Math.min(this.font.width(title), titleBudget) + 6;
            ShopUiUtil.renderTag(graphics, this.font, tagX, y + 12, owner.franchiseName(), ShopUiUtil.TagStyle.ACCENT2);
        }

        // stat row: shops / items / in-stock
        int statY = y + 50;
        int sx = x + 12;
        sx += ShopUiUtil.renderStatBlock(graphics, this.font, sx, statY,
                Integer.toString(owner.shopBlockCount()), Component.translatable("gui.futureshops.shell.stat_shops").getString()) + 14;
        sx += ShopUiUtil.renderStatBlock(graphics, this.font, sx, statY,
                Integer.toString(owner.totalListings()), Component.translatable("gui.futureshops.shell.stat_items").getString()) + 14;
        ShopUiUtil.renderStatBlock(graphics, this.font, sx, statY,
                Integer.toString(owner.totalStock()), Component.translatable("gui.futureshops.shell.stat_stock").getString());

        // department chips + Browse CTA
        int chipY = y + h - 22;
        int chipX = x + 12;
        List<LocalShopOwnerEntry.LocalDepartment> depts = owner.departments();
        int maxChips = Math.min(depts.size(), 3);
        String browse = Component.translatable("gui.futureshops.shop_main.browse").getString();
        int browseW = this.font.width(browse);
        int chipLimit = infoRight - browseW - 12;
        for (int d = 0; d < maxChips && chipX < chipLimit; d++) {
            String name = depts.get(d).name();
            int cw = Math.max(20, this.font.width(name) + 12);
            if (chipX + cw > chipLimit) break;
            ShopUiUtil.renderTag(graphics, this.font, chipX, chipY, name, ShopUiUtil.TagStyle.NEUTRAL);
            chipX += cw + 5;
        }
        graphics.drawString(this.font, browse, infoRight - browseW, chipY + 3, ShopColors.ACCENT_300, false);
    }

    /** Legacy nearby list rendering (fallback before aggregated data arrives). Click = VISIT. */
    private void renderLegacyNearbyGrid(GuiGraphics graphics, int mouseX, int mouseY, List<NearbyShopEntry> nearby) {
        int cardH = 48;
        int gap = 6;
        int contentX = contentX();
        int contentY = contentY();
        int contentW = contentW();
        int contentH = contentH();
        int maxVisible = Math.max(1, (contentH + gap) / (cardH + gap));
        nearbyScrollIdx = Math.max(0, Math.min(nearbyScrollIdx, Math.max(0, nearby.size() - maxVisible)));

        for (int i = nearbyScrollIdx; i < nearby.size() && i < nearbyScrollIdx + maxVisible; i++) {
            NearbyShopEntry entry = nearby.get(i);
            int y = contentY + (i - nearbyScrollIdx) * (cardH + gap);
            boolean hovered = mouseX >= contentX && mouseX < contentX + contentW && mouseY >= y && mouseY < y + cardH;
            ShopUiUtil.renderNocturnePanel(graphics, contentX, y, contentW, cardH,
                    hovered ? ShopColors.SURFACE_OVERLAY : ShopColors.SURFACE_RAISED,
                    hovered ? ShopColors.BORDER_GLOW : ShopColors.BORDER_MUTED);
            ShopUiUtil.renderPlayerFace(graphics, entry.ownerUuid(), entry.ownerName(),
                    contentX + 6, y + 8, 30);
            ShopUiUtil.renderScrollingString(graphics, this.font, entry.shopName(),
                    contentX + 42, y + 6, contentW - 100, ShopColors.TEXT_PRIMARY);
            ShopUiUtil.renderScrollingString(graphics, this.font,
                    Component.translatable("gui.futureshops.shop_main.by_owner", entry.ownerName()).getString(),
                    contentX + 42, y + 18, contentW - 100, ShopColors.TEXT_SECONDARY);
            String infoStr = Component.translatable("gui.futureshops.shop_main.legacy_info",
                    entry.listingCount(), entry.totalStock(), String.format(Locale.ROOT, "%.0f", entry.distance())).getString();
            ShopUiUtil.renderScrollingString(graphics, this.font, infoStr,
                    contentX + 42, y + 30, contentW - 60, ShopColors.TEXT_SECONDARY);
            if (hovered) {
                String visitText = Component.translatable("gui.futureshops.shop_main.click_visit").getString();
                graphics.drawString(this.font, visitText, contentX + contentW - this.font.width(visitText) - 8, y + 18, ShopColors.ACCENT_300, false);
            }
        }
        ShopUiUtil.renderScrollIndicators(graphics, this.font, contentX, contentY, contentW, contentH, nearbyScrollIdx, maxVisible, nearby.size());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Inline text-prompt overlay (category add/rename).
    // ═══════════════════════════════════════════════════════════════════════

    private int[] promptRect() {
        int w = Math.min(220, this.width - 24);
        int h = 74;
        return new int[]{(this.width - w) / 2, (this.height - h) / 2, w, h};
    }

    private void openTextPrompt(Component title, String initialValue, Consumer<String> onAccept) {
        int[] r = promptRect();
        promptTitle = title;
        promptAction = onAccept;
        promptField = new EditBox(this.font, r[0] + ShopUiUtil.PAD_MD, r[1] + 24, r[2] - 2 * ShopUiUtil.PAD_MD, 16, title);
        promptField.setMaxLength(48); // matches the server-side display-name cap
        promptField.setValue(initialValue);
        promptField.setFocused(true);
        if (searchField != null) {
            searchField.setFocused(false);
        }
    }

    private void closePrompt() {
        promptField = null;
        promptAction = null;
    }

    private void acceptPrompt() {
        if (promptField == null || promptAction == null) {
            return;
        }
        String value = promptField.getValue().trim();
        if (value.isEmpty()) {
            return; // OK is inert until there is a name
        }
        Consumer<String> action = promptAction;
        closePrompt();
        action.accept(value);
    }

    private void renderPromptOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int[] r = promptRect();
        int x = r[0];
        int y = r[1];
        int w = r[2];
        int h = r[3];
        promptField.setX(x + ShopUiUtil.PAD_MD);
        promptField.setY(y + 24);
        promptField.setWidth(w - 2 * ShopUiUtil.PAD_MD);

        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, 500f);
        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        ShopUiUtil.renderNocturnePanel(graphics, x, y, w, h, ShopColors.SURFACE_BASE, ShopColors.BORDER_GLOW);
        ShopUiUtil.renderAccentLine(graphics, x + 2, y, w - 4);

        graphics.drawString(this.font, this.font.plainSubstrByWidth(promptTitle.getString(), w - 2 * ShopUiUtil.PAD_MD),
                x + ShopUiUtil.PAD_MD, y + ShopUiUtil.PAD_SM, ShopColors.TEXT_STRONG, true);
        promptField.render(graphics, mouseX, mouseY, partialTick);

        boolean canAccept = !promptField.getValue().trim().isEmpty();
        int btnW = 64;
        int btnH = 16;
        int startX = x + (w - btnW * 2 - ShopUiUtil.PAD_MD) / 2;
        int btnY = y + h - btnH - ShopUiUtil.PAD_SM;

        boolean cancelHover = mouseX >= startX && mouseX <= startX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        ShopUiUtil.renderNocturnePanel(graphics, startX, btnY, btnW, btnH,
                cancelHover ? ShopColors.SURFACE_OVERLAY : ShopColors.SURFACE_RAISED, ShopColors.BORDER_MUTED);
        String cancelText = Component.translatable("gui.futureshops.modal.cancel").getString();
        graphics.drawString(this.font, cancelText, startX + (btnW - this.font.width(cancelText)) / 2, btnY + 4, ShopColors.TEXT_MUTED, true);

        int okX = startX + btnW + ShopUiUtil.PAD_MD;
        boolean okHover = mouseX >= okX && mouseX <= okX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        ShopUiUtil.renderNocturnePanel(graphics, okX, btnY, btnW, btnH,
                canAccept ? (okHover ? ShopColors.BTN_CTA_HOVER : ShopColors.BTN_CTA_REST) : ShopColors.SURFACE_RAISED,
                canAccept ? ShopColors.BORDER_GLOW : ShopColors.BORDER_MUTED);
        String okText = Component.translatable("gui.futureshops.modal.ok").getString();
        graphics.drawString(this.font, okText, okX + (btnW - this.font.width(okText)) / 2, btnY + 4,
                canAccept ? ShopColors.TEXT_STRONG : ShopColors.TEXT_FAINT, true);
        graphics.pose().popPose();
    }

    /** Handles a click while the text prompt is open. Always consumes the click. */
    private boolean handlePromptClick(double mouseX, double mouseY, int button) {
        int[] r = promptRect();
        int x = r[0];
        int y = r[1];
        int w = r[2];
        int h = r[3];
        if (mouseX < x || mouseX > x + w || mouseY < y || mouseY > y + h) {
            closePrompt();
            return true;
        }
        int btnW = 64;
        int btnH = 16;
        int startX = x + (w - btnW * 2 - ShopUiUtil.PAD_MD) / 2;
        int btnY = y + h - btnH - ShopUiUtil.PAD_SM;
        if (mouseX >= startX && mouseX <= startX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            closePrompt();
            return true;
        }
        int okX = startX + btnW + ShopUiUtil.PAD_MD;
        if (mouseX >= okX && mouseX <= okX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            acceptPrompt();
            return true;
        }
        promptField.mouseClicked(mouseX, mouseY, button);
        promptField.setFocused(true);
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Input.
    // ═══════════════════════════════════════════════════════════════════════

    private static boolean inRect(int[] r, double mx, double my) {
        return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (promptField != null || confirmModal != null) {
            return true;
        }
        int cy = contentY();
        int ch = contentH();
        if (nearbyMode) {
            if (mouseX >= contentX() && mouseX <= contentX() + contentW() && mouseY >= cy && mouseY <= cy + ch) {
                nearbyScrollIdx = Math.max(0, nearbyScrollIdx - (int) delta);
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int sidebarX = contentX();
        if (mouseX >= sidebarX && mouseX <= sidebarX + sidebarW && mouseY >= cy && mouseY <= cy + ch) {
            sidebarScrollIdx = Math.max(0, sidebarScrollIdx - (int) delta);
            return true;
        }
        if (mouseX >= gridX() && mouseX <= gridX() + gridW() && mouseY >= cy && mouseY <= cy + ch) {
            gridScrollRows = Math.max(0, gridScrollRows - (int) delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Overlays swallow everything underneath them
        if (promptField != null) {
            return handlePromptClick(mouseX, mouseY, button);
        }
        if (confirmModal != null) {
            return confirmModal.mouseClicked(mouseX, mouseY, button, this.font);
        }
        // Edit-mode flat buttons — additive to the existing hit-tests (they never overlap the
        // header, footer, toolbar, sidebar dept rows, or grid cards).
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
            return true;
        }

        // ── Header ──
        if (headerHit != null) {
            if (headerHit.hitClose(mouseX, mouseY)) {
                onClose();
                return true;
            }
            int tab = headerHit.tabAt(mouseX, mouseY);
            if (tab == 0) {
                switchToServerShop();
                return true;
            }
            if (tab == 1) {
                switchToPlayerShops();
                return true;
            }
            if (tab >= 2 && switchToModule(tab)) {
                return true;
            }
            if (headerHit.hitBalance(mouseX, mouseY)) {
                this.minecraft.setScreen(new TransactionHistoryScreen(this));
                return true;
            }
            if (headerHit.hitProfile(mouseX, mouseY)) {
                ShopPackets.CHANNEL.sendToServer(new C2SOpenBalanceUiPacket());
                return true;
            }
            // clicks in the search pill fall through to super → focus the EditBox
        }

        // ── Footer cart ──
        if (inRect(footerCartRect, mouseX, mouseY)) {
            this.minecraft.setScreen(new CartScreen(this));
            return true;
        }

        if (nearbyMode) {
            if (handlePlayerShopsClick(mouseX, mouseY, button)) {
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // ── Toolbar (Server Shop) ──
        if (segEdges != null && mouseY >= segY && mouseY < segY + segH) {
            for (int i = 0; i + 1 < segEdges.length; i++) {
                if (mouseX >= segEdges[i] && mouseX < segEdges[i + 1]) {
                    setTradeFilter(i);
                    return true;
                }
            }
        }
        if (inRect(sortRect, mouseX, mouseY)) {
            sortMode = (sortMode + 1) % 3;
            gridScrollRows = 0;
            rebuildFilteredItems();
            return true;
        }
        if (inRect(editToggleRect, mouseX, mouseY)) {
            editMode = !editMode;
            // Leaving (or re-entering) edit mode drops any pending bulk-select state.
            selectMode = false;
            selectedListingIds.clear();
            closePrompt();
            confirmModal = null;
            rebuildWidgets();
            return true;
        }

        // ── Sidebar dept selection ──
        int sidebarX = contentX();
        int sidebarY = contentY();
        int sidebarH = contentH();
        if (mouseX >= sidebarX && mouseX <= sidebarX + sidebarW && mouseY >= sidebarY + 22 && mouseY <= sidebarY + sidebarH) {
            int reserved = editMode
                    ? EDIT_SIDEBAR_RESERVED
                    : SELL_SIDEBAR_RESERVED;
            int rowH = 20;
            int listY = sidebarY + 22;
            int listH = sidebarH - 22 - reserved;
            int maxVisible = Math.max(1, listH / rowH);
            int count = deptLabels.size();
            for (int i = sidebarScrollIdx; i < count && i < sidebarScrollIdx + maxVisible; i++) {
                int rowY = listY + (i - sidebarScrollIdx) * rowH;
                if (mouseY >= rowY && mouseY < rowY + rowH - 2) {
                    selectedCategoryIdx = i;
                    gridScrollRows = 0;
                    // A new category shows a different set of listings — drop stale selections so
                    // ids from the previous category can't linger in the bulk-delete set.
                    selectedListingIds.clear();
                    rebuildFilteredItems();
                    return true;
                }
            }
        }

        // ── Grid item clicks ──
        GridMetrics m = gridMetrics();
        if (mouseX >= m.gridX() && mouseX <= m.gridX() + m.gridW() && mouseY >= m.contentY() && mouseY <= m.contentY() + m.contentH()) {
            for (int index = 0; index < filteredItems.size(); index++) {
                int row = index / m.columns();
                if (row < gridScrollRows || row >= gridScrollRows + m.visibleRows()) {
                    continue;
                }
                int visibleRow = row - gridScrollRows;
                int col = index % m.columns();
                int x = m.contentX() + col * (m.cardW() + GRID_GAP);
                int y = m.contentY() + visibleRow * (CARD_H + GRID_GAP);
                if (mouseX >= x && mouseX <= x + m.cardW() && mouseY >= y && mouseY <= y + CARD_H) {
                    openItem(filteredItems.get(index), index, button);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Opens an item from a grid click (edit modal / barter / detail / quick-add-to-cart). */
    private void openItem(CatalogItem item, int index, int button) {
        selectedGridIndex = index;
        // Edit mode: left-click edits the listing; cart gestures are disabled entirely so a misclick
        // can't buy things while restocking the shop.
        if (editingGridActive()) {
            if (button == 0) {
                if (selectMode) {
                    // Bulk-select mode: toggle this listing in/out of the delete set instead of editing.
                    String id = item.listingId();
                    if (!selectedListingIds.remove(id)) {
                        selectedListingIds.add(id);
                    }
                } else {
                    Minecraft.getInstance().setScreen(
                            new AdminOfferEditorScreen(
                                    this, item.listingId()));
                }
            }
            return;
        }
        if (button == 0) {
            // Shift+Click → quick-add to cart (keyed by listingId so NBT variants stay distinct)
            if (hasShiftDown() && quickAddToCart(item)) {
                return;
            }
            // Barter mode: open BarterScreen directly (barter is registry-itemId keyed; the detail
            // screen is listingId keyed).
            boolean barterOnly = item.hasBarterRecipes() && item.buyPrice() <= 0 && item.sellPrice() <= 0;
            if ((tradeFilter == 3 || (tradeFilter == 0 && barterOnly))
                    && item.hasBarterRecipes()
                    && ShopClientState.getCatalogOffer(
                    item.listingId()).isEmpty()) {
                Minecraft.getInstance().setScreen(new BarterScreen(this, item.itemId()));
            } else {
                Minecraft.getInstance().setScreen(new ItemDetailScreen(this, item.listingId()));
            }
        } else if (button == 1) {
            quickAddToCart(item);
        }
    }

    private boolean quickAddToCart(CatalogItem item) {
        var offer = ShopClientState.getCatalogOffer(
                item.listingId()).orElse(null);
        if (offer != null) {
            if (offer.acquireOptions().isEmpty()
                    || !item.unlimited() && item.stock() <= 0) {
                return false;
            }
            if (offer.acquireOptions().size() != 1) {
                Minecraft.getInstance().setScreen(
                        ServerShopOfferOptionScreen.quickCart(
                                this, item.listingId()));
                return true;
            }
            ShopClientState.addOfferToCart(
                    offer.listingId(),
                    offer.acquireOptions().get(0).optionId(),
                    1,
                    offer.revision());
            return true;
        }
        if (item.buyPrice() <= 0L
                || !item.unlimited() && item.stock() <= 0) {
            return false;
        }
        ShopClientState.addToCart(item.listingId(), 1);
        return true;
    }

    /** Player-shops card / legacy-nearby click routing. Returns true if consumed. */
    private boolean handlePlayerShopsClick(double mouseX, double mouseY, int button) {
        List<LocalShopOwnerEntry> owners = ShopClientState.getLocalShopOwners();
        if (!owners.isEmpty()) {
            ShopGrid g = shopGrid(owners.size());
            int gap = 12;
            for (int i = 0; i < owners.size(); i++) {
                int row = i / g.cols();
                if (row < nearbyScrollIdx || row >= nearbyScrollIdx + g.visRows()) {
                    continue;
                }
                int col = i % g.cols();
                int cx = g.gx() + col * (g.cardW() + gap);
                int cy = g.gy() + (row - nearbyScrollIdx) * (g.cardH() + gap);
                if (mouseX >= cx && mouseX < cx + g.cardW() && mouseY >= cy && mouseY < cy + g.cardH()) {
                    Minecraft.getInstance().setScreen(new LocalShopBrowserScreen(this, owners.get(i)));
                    return true;
                }
            }
            return false;
        }

        // Legacy fallback → VISIT via C2SPlayerShopActionPacket
        List<NearbyShopEntry> nearby = ShopClientState.getNearbyShops();
        int cardH = 48;
        int gap = 6;
        int cX = contentX();
        int cY = contentY();
        int cW = contentW();
        int cH = contentH();
        int maxVisible = Math.max(1, (cH + gap) / (cardH + gap));
        for (int i = nearbyScrollIdx; i < nearby.size() && i < nearbyScrollIdx + maxVisible; i++) {
            int y = cY + (i - nearbyScrollIdx) * (cardH + gap);
            if (mouseX >= cX && mouseX < cX + cW && mouseY >= y && mouseY < y + cardH) {
                NearbyShopEntry target = nearby.get(i);
                ClientRouteGuard.expectStorefront(this, target.pos().asLong());
                ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopActionPacket(
                        target.pos(), "VISIT", 0, 0));
                return true;
            }
        }
        return false;
    }

    private void switchToServerShop() {
        ClientRouteGuard.cancelFor(this);
        nearbyMode = false;
        gridScrollRows = 0;
        rebuildFilteredItems();
    }

    private void switchToPlayerShops() {
        ClientRouteGuard.cancelFor(this);
        nearbyMode = true;
        gridScrollRows = 0;
        nearbyScrollIdx = 0;
        ShopPackets.CHANNEL.sendToServer(new C2SOpenShopPacket(ShopClientState.getActiveShopId()));
        ShopPackets.CHANNEL.sendToServer(new C2SFetchLocalShopsPacket(""));
    }

    private void setTradeFilter(int filter) {
        int next = Math.max(0, Math.min(4, filter));
        if (tradeFilter == next) {
            return;
        }
        tradeFilter = next;
        gridScrollRows = 0;
        rebuildFilteredItems();
    }

    @Override
    public void onClose() {
        ClientRouteGuard.cancelFor(this);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String prettyName(String raw) {
        if (raw == null || raw.isBlank()) {
            return Component.translatable("gui.futureshops.shop.title").getString();
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
        return builder.isEmpty() ? Component.translatable("gui.futureshops.shop.title").getString() : builder.toString();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Text prompt swallows all keys while open
        if (promptField != null) {
            if (keyCode == 256) { // Escape
                closePrompt();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter / keypad Enter
                acceptPrompt();
                return true;
            }
            promptField.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        // Confirmation modal swallows all keys too (Escape dismisses it)
        if (confirmModal != null) {
            confirmModal.keyPressed(keyCode);
            return true;
        }

        // "/" → focus search
        if (keyCode == 47 && searchField != null && !searchField.isFocused()) {
            searchField.setFocused(true);
            return true;
        }
        // "B" → toggle barter mode (Server Shop only, when search not focused)
        if (keyCode == 66 && searchField != null && !searchField.isFocused() && !nearbyMode) {
            setTradeFilter(tradeFilter == 3 ? 0 : 3);
            return true;
        }

        // Tab → cycle focus: search ↔ grid (Server Shop only)
        if (keyCode == 258 && !nearbyMode) { // Tab key
            if (searchField != null && searchField.isFocused()) {
                searchField.setFocused(false);
                return true;
            }
            if (selectedGridIndex >= 0) {
                selectedGridIndex = -1;
                if (searchField != null) searchField.setFocused(true);
                return true;
            }
            selectedGridIndex = 0;
            if (searchField != null) searchField.setFocused(false);
            return true;
        }

        // Arrow keys → grid navigation (Server Shop, when search not focused)
        if (searchField != null && !searchField.isFocused() && !nearbyMode && !filteredItems.isEmpty()) {
            GridMetrics m = gridMetrics();
            int columns = m.columns();
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
                    else sidebarScrollIdx = Math.max(0, sidebarScrollIdx - 1);
                    return true;
                }
                case 264 -> { // Down
                    if (selectedGridIndex + columns < filteredItems.size()) selectedGridIndex += columns;
                    return true;
                }
                case 257 -> { // Enter → open selected item (edit modal while editing)
                    if (selectedGridIndex >= 0 && selectedGridIndex < filteredItems.size()) {
                        openItem(filteredItems.get(selectedGridIndex), selectedGridIndex, 0);
                        return true;
                    }
                }
            }

            // Keep the selected index visible — scroll if needed
            int row = selectedGridIndex / columns;
            if (row < gridScrollRows) gridScrollRows = row;
            else if (row >= gridScrollRows + m.visibleRows()) gridScrollRows = row - m.visibleRows() + 1;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (promptField != null) {
            promptField.charTyped(codePoint, modifiers);
            return true;
        }
        if (confirmModal != null) {
            return true;
        }
        // Forward '/' to search-field focus instead of typing it
        if (codePoint == '/' && searchField != null && !searchField.isFocused()) {
            searchField.setFocused(true);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }
}
