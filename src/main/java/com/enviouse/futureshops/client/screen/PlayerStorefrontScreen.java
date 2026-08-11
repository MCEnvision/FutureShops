package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.PlayerShopCartState;
import com.enviouse.futureshops.client.PlayerShopClientState;
import com.enviouse.futureshops.client.PlayerShopResponseTracker;
import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.PlayerShopListingData;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.network.packets.C2SPlayerShopBuyPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Player-shop <b>visitor</b> experience, rebuilt onto the shared Nocturne shell (Phase 4).
 *
 * <p>Driven entirely by {@link PlayerShopClientState} — the owner MANAGE view stays on
 * {@link PlayerShopBlockScreen}. Two open modes, both mapped onto the existing
 * {@code singleItemMode} flag (no new wire field):
 *
 * <ul>
 *   <li><b>Option A — specific listing</b> ({@code singleItemMode == true}): the server sends a single
 *       listing (the block's visible index); we show it <i>detail-forward</i> — big preview + price +
 *       quantity + Buy/Barter/Sell — inside the shell.</li>
 *   <li><b>Option B — storefront</b> ({@code singleItemMode == false}): the whole shop browses inside the
 *       shell — a department sidebar built from the listings' departments plus an item-card grid that
 *       mirrors {@link ShopMainScreen}'s server browse. Clicking a card drills into the same detail
 *       view; a Back button returns to the grid.</li>
 * </ul>
 *
 * <p><b>Storage gating:</b> Option B pulls fulfilment from the shop's linked storage, so a storefront
 * with no linked storage (and not an admin/infinite shop) can't fulfil anything — it renders an
 * "unavailable" empty state instead of unbuyable listings.
 *
 * <p>Every buy/sell/barter path reuses the existing packet senders unchanged: money buys go through
 * {@link C2SPlayerShopBuyPacket} (always with a non-empty 4th {@code paymentMethod} arg — the
 * BuyPacketCallSiteTest invariant), barter/sell hand off to {@link PlayerShopBarterScreen} /
 * {@link PlayerShopSellScreen} exactly as {@link PlayerShopBlockScreen} does. All the reused senders
 * read {@link PlayerShopClientState#selectedListingIndex()}, so the detail view keeps that index
 * pointed at whatever listing is on screen.
 */
public class PlayerStorefrontScreen extends Screen implements ShopScreenMarker {

    // Grid card geometry — shared by render + click hit-testing so they can't disagree (mirrors
    // ShopMainScreen's server-browse card so the two views read as one system).
    private static final int CARD_H = 62;
    private static final int GRID_GAP = 8;
    private static final int TOOLBAR_H = 22;

    private final Screen parent;

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private int headerH;
    private int breadcrumbH;
    private int footerH;
    private int sidebarW;

    /** Fixed for the lifetime of an open session (owner can't flip modes without re-opening). */
    private boolean optionA;    // singleItemMode → detail-forward single listing
    private boolean stocked;    // linked() || adminShopMode() → Option B can fulfil

    /** -1 = grid (Option B); ≥0 = showing the detail view for that real listing index. */
    private int detailIndex = -1;

    private int selectedDeptIdx;   // 0 = "All"; 1..N = a real department
    private int gridScrollRows;
    private int sidebarScrollIdx;
    private boolean barterMode;    // Buy/Barter segmented (grid) — filters to barter-capable listings

    private String searchQuery = "";
    private String searchTextRaw = "";

    // Filtered listing indices (into PlayerShopClientState.listings()) + sidebar dept model.
    private List<Integer> filtered = List.of();
    private List<String> deptLabels = List.of();
    private List<String> deptCounts = List.of();
    private boolean anyBarter;

    // Widgets — detail controls (created once in init, visibility toggled per frame).
    private EditBox searchField;
    private EditBox qtyBox;

    /** Per-frame flat-button hit regions (see ShopUiUtil.button / dispatchClicks). */
    private final java.util.List<ShopUiUtil.ClickZone> clickZones = new ArrayList<>();

    // Buy confirmation overlay (composed, like PlayerShopBlockScreen).
    private ConfirmationModal confirmationModal;

    // Hit models stashed each frame so mouseClicked routes against the exact drawn geometry.
    private ShopUiUtil.HeaderHit headerHit;
    private int[] footerCartRect;
    private int[] segEdges;
    private int segY;
    private int segH;

    // Advanced-tooltip tracking.
    private String tooltipItemId;
    private String tooltipNbtJson;
    private int tooltipMouseX;
    private int tooltipMouseY;

    public PlayerStorefrontScreen(Screen parent) {
        super(Component.translatable("gui.futureshops.storefront.title"));
        this.parent = parent;
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

    /** Y of the detail action row (qty + Buy/Barter/Sell), just above the footer. */
    private int detailRowY() { return guiTop + guiH - footerH - 6 - 16; }

    /**
     * Quantity row — one line ABOVE the action row so the left-anchored steppers/box never share
     * horizontal space with the right-anchored Buy/Sell/Barter buttons (which would collide and
     * steal clicks at narrow GUI widths). Content above is reserved down to this row.
     */
    private int qtyRowY() { return detailRowY() - 20; }

    private boolean compact() { return guiW < 560; }

    /** No top tabs — this is a focused storefront (Back returns to the parent; X closes the GUI). */
    private static String[] tabLabels() { return new String[0]; }

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

        optionA = PlayerShopClientState.singleItemMode();
        // Admin shops fulfil from an infinite supply, not linked storage, so they're never "unstocked".
        stocked = PlayerShopClientState.linked() || PlayerShopClientState.adminShopMode();

        List<PlayerShopListingData> listings = PlayerShopClientState.listings();
        if (optionA) {
            // Server sent exactly the one visible listing (index 0) for single-item visitors.
            detailIndex = listings.isEmpty() ? -1
                    : Math.max(0, Math.min(PlayerShopClientState.selectedListingIndex(), listings.size() - 1));
            if (detailIndex >= 0) {
                PlayerShopClientState.setSelectedListingIndex(detailIndex);
            }
        } else {
            detailIndex = -1; // Option B starts on the grid
        }

        rebuildFiltered();

        // Search box lives inside the header search pill — grid state only (Option B, stocked).
        if (!optionA && stocked) {
            String balance = ShopUiUtil.formatMinorUnits(ShopClientState.getCurrentBalanceMinorUnits());
            String playerName = this.minecraft != null && this.minecraft.player != null
                    ? this.minecraft.player.getGameProfile().getName() : "";
            ShopUiUtil.HeaderHit hh = ShopUiUtil.headerLayout(this.font, guiLeft, guiTop, guiW, headerH,
                    tabLabels(), balance, playerName, compact());
            int[] sr = hh.searchRect();
            searchField = new EditBox(this.font, sr[0] + 14, sr[1], Math.max(24, sr[2] - 18), sr[3],
                    Component.translatable("gui.futureshops.shop.search"));
            searchField.setBordered(false);
            searchField.setMaxLength(32);
            searchField.setValue(searchTextRaw);
            searchField.setResponder(query -> {
                searchTextRaw = query;
                searchQuery = query.toLowerCase(Locale.ROOT);
                gridScrollRows = 0;
                rebuildFiltered();
            });
            addRenderableWidget(searchField);
        }

        initDetailWidgets();
        updateWidgets();
    }

    /**
     * Detail action row: [Back] [− qty +] [Max] … [Buy][Barter][Sell][+Cart].
     * Only the quantity EditBox is a real widget; every button is drawn immediate-mode in render().
     */
    private void initDetailWidgets() {
        int btnH = 16;
        // Quantity box sits between the − and + steppers on the DEDICATED quantity row (above the
        // action row), left-anchored at contentX() — no Back-button offset (Back is on the action row).
        qtyBox = new EditBox(this.font, contentX() + 18, qtyRowY(), 32, btnH,
                Component.translatable("gui.futureshops.player_shop_block.visitor.qty"));
        qtyBox.setValue("1");
        qtyBox.setMaxLength(4);
        qtyBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        addRenderableWidget(qtyBox);
    }

    /** Flat Nocturne detail action row + quantity steppers (formerly vanilla Buttons). */
    private void renderDetailButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        PlayerShopListingData listing = currentDetailListing();
        if (listing == null) return;

        int btnH = 16;
        int rowY = detailRowY();
        int leftX = contentX();

        // Back is explicit in both open modes. In Option A it returns to the marketplace/profile
        // that opened this listing; in Option B detail it returns to this storefront's grid.
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY, leftX, rowY, 44, btnH,
                Component.translatable("gui.futureshops.player_shop_block.visitor.back"),
                ShopUiUtil.ButtonStyle.SECONDARY, true,
                () -> applyNavigation(ClientNavigationPolicy.storefrontBack(optionA)));

        // Quantity steppers: − [box] + Max — on the DEDICATED quantity row above the actions, so they
        // never overlap the right-anchored action buttons (which would steal clicks at narrow widths).
        int qtyY = qtyRowY();
        int qx = contentX();
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY, qx, qtyY, 16, btnH,
                Component.literal("-"), ShopUiUtil.ButtonStyle.SECONDARY, true, () -> setQuantity(getQuantity() - 1));
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY, qx + 52, qtyY, 16, btnH,
                Component.literal("+"), ShopUiUtil.ButtonStyle.SECONDARY, true, () -> {
                    if (hasShiftDown()) setQuantity(resolveMaxQuantity());
                    else setQuantity(getQuantity() + 1);
                });
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY, qx + 70, qtyY, 30, btnH,
                Component.translatable("gui.futureshops.player_shop_block.visitor.max"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, () -> setQuantity(resolveMaxQuantity()));

        // Action gating — mirrors the former per-frame updateWidgets() button toggles.
        boolean showcaseOnly = listing.showcase();
        boolean inStock = listing.stock() > 0 || PlayerShopClientState.adminShopMode();
        boolean money = hasMoney(listing);
        boolean barter = supportsBarter(listing);
        String dir = listing.direction() == null ? "SELL" : listing.direction().toUpperCase(Locale.ROOT);
        boolean allowsBuy = !"BUY".equals(dir);            // SELL or BOTH
        boolean allowsSell = "BUY".equals(dir) || "BOTH".equals(dir);
        boolean capOk = listing.buybackCap() == 0 || listing.buybackRemaining() > 0;
        boolean canSell = allowsSell && listing.buybackPriceMinor() > 0 && capOk && !showcaseOnly;

        // Actions — laid out from the right edge inward, only the visible ones drawn.
        int rightEdge = contentX() + contentW();
        int cartW = 40;
        if (money && allowsBuy && !showcaseOnly) {
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY, rightEdge - cartW, rowY, cartW, btnH,
                    Component.translatable("gui.futureshops.player_shop_block.visitor.add_cart"),
                    ShopUiUtil.ButtonStyle.PRIMARY, inStock, this::addToCart);
        }
        rightEdge -= cartW + 4;
        int sellW = 68;
        if (canSell) {
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY, rightEdge - sellW, rowY, sellW, btnH,
                    Component.translatable("gui.futureshops.player_shop_block.visitor.sell_button"),
                    ShopUiUtil.ButtonStyle.SECONDARY, true, this::openSell);
        }
        rightEdge -= sellW + 4;
        int barterW = 62;
        if (barter && allowsBuy && !showcaseOnly) {
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY, rightEdge - barterW, rowY, barterW, btnH,
                    Component.translatable("gui.futureshops.player_shop_block.visitor.barter_btn"),
                    ShopUiUtil.ButtonStyle.SECONDARY, inStock, this::openBarter);
        }
        rightEdge -= barterW + 4;
        int buyW = 52;
        if (money && allowsBuy && !showcaseOnly) {
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY, rightEdge - buyW, rowY, buyW, btnH,
                    Component.translatable("gui.futureshops.player_shop_block.visitor.buy_btn"),
                    ShopUiUtil.ButtonStyle.PRIMARY, inStock, () -> showBuyConfirmation(getQuantity()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // State + filtering.
    // ═══════════════════════════════════════════════════════════════════════

    /** The listing currently shown detail-forward, or null when browsing the grid. */
    private PlayerShopListingData currentDetailListing() {
        List<PlayerShopListingData> listings = PlayerShopClientState.listings();
        if (detailIndex >= 0 && detailIndex < listings.size()) {
            return listings.get(detailIndex);
        }
        return null;
    }

    private boolean detailActive() {
        return currentDetailListing() != null && (optionA || (stocked && detailIndex >= 0));
    }

    private static boolean supportsBarter(PlayerShopListingData l) {
        return !"MONEY".equalsIgnoreCase(l.tradeMode());
    }

    private static boolean hasMoney(PlayerShopListingData l) {
        return !"BARTER".equalsIgnoreCase(l.tradeMode());
    }

    /**
     * Rebuilds the department sidebar model + the filtered grid (department + search + barter
     * segment), clamping the selected department. Called on every state change — never per frame.
     */
    private void rebuildFiltered() {
        List<PlayerShopListingData> listings = PlayerShopClientState.listings();

        // Distinct departments (in first-seen order) + per-department counts. Hidden listings
        // are concealed from visitors entirely (this screen is always the visitor view), so they
        // contribute to neither the dept model nor the "All" count.
        Map<String, Integer> counts = new LinkedHashMap<>();
        anyBarter = false;
        int visibleCount = 0;
        for (PlayerShopListingData l : listings) {
            if (l.hidden()) {
                continue;
            }
            visibleCount++;
            if (supportsBarter(l)) {
                anyBarter = true;
            }
            String dept = l.department();
            if (dept != null && !dept.isBlank()) {
                counts.merge(dept, 1, Integer::sum);
            }
        }
        List<String> labels = new ArrayList<>();
        List<String> countStrs = new ArrayList<>();
        labels.add(Component.translatable("gui.futureshops.shop_main.tab_all").getString());
        countStrs.add(Integer.toString(visibleCount));
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            labels.add(e.getKey());
            countStrs.add(Integer.toString(e.getValue()));
        }
        deptLabels = labels;
        deptCounts = countStrs;
        if (selectedDeptIdx < 0 || selectedDeptIdx >= deptLabels.size()) {
            selectedDeptIdx = 0;
        }
        // Turning off barter mode is only meaningful while a barter listing exists.
        if (barterMode && !anyBarter) {
            barterMode = false;
        }

        String deptFilter = selectedDeptIdx >= 1 && selectedDeptIdx < labels.size()
                ? labels.get(selectedDeptIdx) : null;
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < listings.size(); i++) {
            PlayerShopListingData l = listings.get(i);
            if (l.hidden()) {
                continue;
            }
            if (barterMode && !supportsBarter(l)) {
                continue;
            }
            if (deptFilter != null && !deptFilter.equals(l.department())) {
                continue;
            }
            if (!searchQuery.isBlank()) {
                String name = ShopUiUtil.getItemDisplayNameWithNbt(l.itemId(), l.nbtJson()).toLowerCase(Locale.ROOT);
                if (!name.contains(searchQuery) && !l.itemId().toLowerCase(Locale.ROOT).contains(searchQuery)) {
                    continue;
                }
            }
            result.add(i);
        }
        filtered = result;
    }

    /**
     * Called by ShopClientPacketHandler when fresh shop data arrives while this screen is already
     * open (e.g. a buy ack resending the shop payload). Only reconciles the filter / department
     * selection against the new listings — never re-runs init(), so the browse/detail state,
     * scroll, search and any open confirmation modal survive.
     */
    public void refreshAfterDataUpdate() {
        List<PlayerShopListingData> listings = PlayerShopClientState.listings();
        // A drilled listing index can fall off the end if the owner removed listings mid-session.
        if (detailIndex >= listings.size()) {
            detailIndex = optionA ? (listings.isEmpty() ? -1 : listings.size() - 1) : -1;
        }
        stocked = PlayerShopClientState.linked() || PlayerShopClientState.adminShopMode();
        rebuildFiltered();
        updateWidgets();
    }

    /**
     * Per-frame EditBox visibility + keeps the reused packet senders pointed at the on-screen
     * listing. The action buttons are drawn (and gated) immediate-mode in renderDetailButtons().
     */
    private void updateWidgets() {
        boolean detail = detailActive();

        // Keep the reused senders pointed at the on-screen listing.
        if (detail && detailIndex >= 0) {
            PlayerShopClientState.setSelectedListingIndex(detailIndex);
        }

        boolean showSearch = !optionA && stocked && detailIndex < 0;
        if (searchField != null) {
            searchField.visible = showSearch;
        }
        if (qtyBox != null) qtyBox.visible = detail;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Render.
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();
        tooltipItemId = null;
        tooltipNbtJson = null;
        updateWidgets();

        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        ShopUiUtil.renderShellWindow(graphics, guiLeft, guiTop, guiW, guiH);

        // ── Header (brand + search pill + balance/profile pills + close) ──
        String balance = ShopUiUtil.formatMinorUnits(ShopClientState.getCurrentBalanceMinorUnits());
        Minecraft mc = this.minecraft;
        String playerName = mc != null && mc.player != null ? mc.player.getGameProfile().getName() : "";
        UUID uuid = mc != null && mc.player != null ? mc.player.getUUID() : null;
        headerHit = ShopUiUtil.renderShellHeader(graphics, this.font, guiLeft, guiTop, guiW, headerH,
                tabLabels(), -1, balance, playerName, uuid, compact(), mouseX, mouseY);

        // ── Breadcrumb ──
        if (breadcrumbH > 0) {
            renderBreadcrumbStrip(graphics);
        }

        // ── Content ──
        if (optionA) {
            if (currentDetailListing() != null) {
                renderDetail(graphics, mouseX, mouseY);
            } else {
                renderMessage(graphics, "gui.futureshops.storefront.no_listing", null);
            }
        } else if (!stocked) {
            renderMessage(graphics, "gui.futureshops.storefront.unstocked_title",
                    "gui.futureshops.storefront.unstocked_body");
        } else if (detailIndex >= 0) {
            renderDetail(graphics, mouseX, mouseY);
        } else {
            renderSidebar(graphics, mouseX, mouseY);
            renderToolbar(graphics, mouseX, mouseY);
            renderGrid(graphics, mouseX, mouseY);
        }

        // ── Detail action row (flat Nocturne buttons, on top of the detail content) ──
        if (detailActive()) {
            renderDetailButtons(graphics, mouseX, mouseY);
        }

        // ── Footer (cart) ──
        footerCartRect = ShopUiUtil.renderShellFooter(graphics, this.font, contentX(),
                guiTop + guiH - footerH, contentW(), footerH, footerHint(),
                PlayerShopCartState.size(), mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (confirmationModal == null) {
            ShopUiUtil.renderShellHeaderTooltip(graphics, this.font,
                    headerHit, mouseX, mouseY);
        }
        if (tooltipItemId != null && confirmationModal == null) {
            ShopUiUtil.renderItemTooltip(graphics, this.font, tooltipItemId,
                    tooltipNbtJson != null ? tooltipNbtJson : "", tooltipMouseX, tooltipMouseY);
        }
        if (confirmationModal != null) {
            confirmationModal.render(graphics, this.font, this.width, this.height, mouseX, mouseY);
            if (confirmationModal.shouldAutoDismiss()) {
                confirmationModal = null;
            }
        }
    }

    /** The one shop crumb ("<owner>'s Shop" / shopName) + right-aligned context (item count / mode). */
    private void renderBreadcrumbStrip(GuiGraphics graphics) {
        int x = contentX();
        int y = guiTop + headerH + (breadcrumbH - 8) / 2;
        int w = contentW();
        String shopTitle = PlayerShopClientState.shopName().isBlank()
                ? Component.translatable("gui.futureshops.player_shop_block.header.shop_suffix",
                        PlayerShopClientState.ownerName()).getString()
                : PlayerShopClientState.shopName();

        PlayerShopListingData detail = currentDetailListing();
        if (detail != null && (optionA || detailIndex >= 0)) {
            String itemName = ShopUiUtil.getItemDisplayNameWithNbt(detail.itemId(), detail.nbtJson());
            String[] crumbs = { shopTitle, itemName };
            ShopUiUtil.renderBreadcrumb(graphics, this.font, x, y, w, crumbs,
                    ShopUiUtil.tradeModeLabel(detail.tradeMode()));
        } else {
            String dept = selectedDeptIdx == 0
                    ? Component.translatable("gui.futureshops.shop_main.tab_all").getString()
                    : deptLabels.get(Math.min(selectedDeptIdx, deptLabels.size() - 1));
            String[] crumbs = { shopTitle, dept };
            String right = Component.translatable("gui.futureshops.shell.crumb_items", filtered.size()).getString();
            ShopUiUtil.renderBreadcrumb(graphics, this.font, x, y, w, crumbs, right);
        }
    }

    /** Centered package-glyph empty state (unstocked storefront / no listings). */
    private void renderMessage(GuiGraphics graphics, String titleKey, String bodyKey) {
        int cx = contentX() + contentW() / 2;
        int cy = contentY() + contentH() / 2;
        graphics.fill(cx - 12, cy - 30, cx + 12, cy - 10, ShopColors.SURFACE_RAISED);
        ShopUiUtil.drawBorder(graphics, cx - 12, cy - 30, 24, 20, ShopColors.BORDER_MUTED);
        graphics.fill(cx - 12, cy - 21, cx + 12, cy - 19, ShopColors.ACCENT_800);
        graphics.drawCenteredString(this.font, Component.translatable(titleKey), cx, cy, ShopColors.TEXT_SECONDARY);
        if (bodyKey != null) {
            List<net.minecraft.util.FormattedCharSequence> lines =
                    this.font.split(Component.translatable(bodyKey), Math.min(300, contentW() - 40));
            for (int i = 0; i < lines.size(); i++) {
                net.minecraft.util.FormattedCharSequence line = lines.get(i);
                graphics.drawString(this.font, line, cx - this.font.width(line) / 2, cy + 14 + i * 11,
                        ShopColors.TEXT_FAINT, false);
            }
        }
    }

    // ── Detail-forward view (Option A, and Option B drilled-in) ──

    private void renderDetail(GuiGraphics graphics, int mouseX, int mouseY) {
        PlayerShopListingData l = currentDetailListing();
        if (l == null) return;

        int cx = contentX();
        int cy = contentY();
        int cw = contentW();
        int dh = qtyRowY() - cy - 8; // reserve the quantity row (above the action row)
        if (dh < 40) return;

        // Left: big preview panel with a fading accent top edge.
        int leftW = Math.max(120, Math.min(220, cw * 2 / 5));
        ShopUiUtil.renderNocturnePanelAccentTop(graphics, cx, cy, leftW, dh);
        int previewY = cy + Math.max(10, (dh - 48) / 2 - 10);
        ShopUiUtil.renderLargeItemPreviewWithNbt(graphics, this.font, l.itemId(), l.nbtJson(), cx + 4, previewY, leftW - 8);
        if (mouseX >= cx + 4 && mouseX < cx + leftW - 4 && mouseY >= previewY && mouseY < previewY + 54) {
            tooltipItemId = l.itemId();
            tooltipNbtJson = l.nbtJson();
            tooltipMouseX = mouseX;
            tooltipMouseY = mouseY;
        }

        // Right: name / id / price / stock / mode / description.
        int rx = cx + leftW + 14;
        int rw = cx + cw - rx;
        int ry = cy + 6;

        String name = ShopUiUtil.getItemDisplayNameWithNbtAndQty(l.itemId(), l.nbtJson(), l.baseQuantity());
        ShopUiUtil.renderScrollingString(graphics, this.font, name, rx, ry, rw, ShopColors.TEXT_STRONG);
        ry += 12;
        graphics.drawString(this.font,
                Component.translatable("gui.futureshops.storefront.item_id",
                        this.font.plainSubstrByWidth(l.itemId(), rw)),
                rx, ry, ShopColors.TEXT_FAINT, false);
        ry += 16;

        boolean outOfStock = l.stock() <= 0 && !PlayerShopClientState.adminShopMode();
        if (hasMoney(l)) {
            long price = l.effectiveUnitPriceMinor();
            int coinW = ShopUiUtil.renderCoinAmount(graphics, this.font, rx, ry, ShopUiUtil.formatMinorUnits(price),
                    outOfStock ? ShopColors.TEXT_FAINT : ShopColors.TEXT_STRONG);
            int percent = ShopUiUtil.computePromoPercent(l.moneyPriceMinor(), l.effectiveUnitPriceMinor());
            if (percent > 0) {
                graphics.drawString(this.font,
                        Component.literal(ShopUiUtil.formatMinorUnits(l.moneyPriceMinor()))
                                .withStyle(ChatFormatting.STRIKETHROUGH),
                        rx + coinW + 6, ry, ShopColors.NEUTRAL_600, false);
                String promoLabel = percent >= 100
                        ? Component.translatable("gui.futureshops.player_shop_block.detail.promo_free").getString()
                        : "-" + percent + "%";
                ShopUiUtil.renderTag(graphics, this.font, rx + rw - Math.max(20, this.font.width(promoLabel) + 12),
                        ry - 2, promoLabel, ShopUiUtil.TagStyle.ACCENT);
            }
        } else {
            graphics.drawString(this.font, Component.translatable("gui.futureshops.storefront.barter_only"),
                    rx, ry, ShopColors.TEXT_BARTER, false);
        }
        ry += 14;

        // Stock (colored) + Trade tag when barter is possible.
        String stockStr;
        int stockColor;
        if (PlayerShopClientState.adminShopMode()) {
            stockStr = Component.translatable("gui.futureshops.shop_main.stock_unlimited").getString();
            stockColor = ShopColors.NEUTRAL_500;
        } else if (outOfStock) {
            stockStr = Component.translatable("gui.futureshops.shop_main.sold_out").getString();
            stockColor = ShopColors.STATUS_DANGER;
        } else {
            stockStr = Component.translatable("gui.futureshops.shop_main.stock_left", l.stock()).getString();
            stockColor = l.stock() <= 5 ? ShopColors.STATUS_WARNING : ShopColors.NEUTRAL_500;
        }
        graphics.drawString(this.font, stockStr, rx, ry, stockColor, false);
        if (l.showcase()) {
            // Display-only listing — flag it instead of the Trade tag (showcase blocks trades).
            ShopUiUtil.renderTag(graphics, this.font,
                    Math.min(rx + this.font.width(stockStr) + 6, rx + rw - 40), ry - 2,
                    Component.translatable("gui.futureshops.shell.showcase_tag").getString(), ShopUiUtil.TagStyle.ACCENT2);
        } else if (supportsBarter(l)) {
            ShopUiUtil.renderTag(graphics, this.font,
                    Math.min(rx + this.font.width(stockStr) + 6, rx + rw - 40), ry - 2,
                    Component.translatable("gui.futureshops.shell.trade_tag").getString(), ShopUiUtil.TagStyle.OUTLINE);
        }
        ry += 14;

        graphics.drawString(this.font, ShopUiUtil.tradeModeLabel(l.tradeMode()), rx, ry, ShopColors.TEXT_MUTED, false);
        ry += 16;

        ShopUiUtil.renderFadingRule(graphics, rx, ry, rw);
        ry += 6;
        Component desc = (l.listingDescription() == null || l.listingDescription().isBlank())
                ? Component.translatable("gui.futureshops.player_shop_block.detail.no_description")
                : Component.literal(l.listingDescription());
        int descColor = (l.listingDescription() == null || l.listingDescription().isBlank())
                ? ShopColors.TEXT_FAINT : ShopColors.TEXT_MUTED;
        int maxLines = Math.max(1, (qtyRowY() - 8 - ry) / 11); // reserve the quantity row
        ShopUiUtil.drawWrappedClamped(graphics, this.font, desc, rx, ry, rw, maxLines, descColor, 11);
    }

    // ── Grid (Option B storefront) ──

    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = contentX();
        int y = contentY();
        int h = contentH();
        ShopUiUtil.renderNocturnePanel(graphics, x, y, sidebarW, h);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.shop_main.departments"),
                x + 12, y + 8, ShopColors.NEUTRAL_500, false);

        int rowH = 20;
        int listY = y + 22;
        int listH = h - 22;
        int count = deptLabels.size();
        int maxVisible = Math.max(1, listH / rowH);
        sidebarScrollIdx = Math.max(0, Math.min(sidebarScrollIdx, Math.max(0, count - maxVisible)));

        for (int i = sidebarScrollIdx; i < count && i < sidebarScrollIdx + maxVisible; i++) {
            int rowY = listY + (i - sidebarScrollIdx) * rowH;
            boolean selected = i == selectedDeptIdx;
            boolean hovered = mouseX >= x + 4 && mouseX <= x + sidebarW - 4 && mouseY >= rowY && mouseY < rowY + rowH - 2;
            ShopUiUtil.renderDeptRow(graphics, this.font, x + 4, rowY, sidebarW - 8, rowH - 2,
                    deptLabels.get(i), i < deptCounts.size() ? deptCounts.get(i) : "", selected, hovered);
        }
        ShopUiUtil.renderScrollIndicators(graphics, this.font, x, listY, sidebarW, listH, sidebarScrollIdx, maxVisible, count);
    }

    private void renderToolbar(GuiGraphics graphics, int mouseX, int mouseY) {
        int tx = gridX();
        int ty = gridY();
        segH = 18;
        segY = ty + (TOOLBAR_H - segH) / 2;

        if (anyBarter) {
            String[] segLabels = {
                    Component.translatable("gui.futureshops.shell.seg_buy").getString(),
                    Component.translatable("gui.futureshops.shell.seg_barter").getString()
            };
            segEdges = ShopUiUtil.renderSegmented(graphics, this.font, tx, segY, segH, segLabels, barterMode ? 1 : 0);
        } else {
            segEdges = null;
        }
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int gx = gridX();
        int itemsX = gx;
        int itemsY = gridY() + TOOLBAR_H;
        int itemsW = gridW();
        int itemsH = gridH() - TOOLBAR_H;

        if (filtered.isEmpty()) {
            int cx = gx + itemsW / 2;
            int cy = itemsY + itemsH / 2;
            graphics.fill(cx - 10, cy - 24, cx + 10, cy - 8, ShopColors.SURFACE_RAISED);
            ShopUiUtil.drawBorder(graphics, cx - 10, cy - 24, 20, 16, ShopColors.BORDER_MUTED);
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.storefront.no_listing"),
                    cx, cy, ShopColors.TEXT_SECONDARY);
            return;
        }

        int cardMinW = 190;
        int columns = Math.max(1, Math.min(4, (itemsW + GRID_GAP) / (cardMinW + GRID_GAP)));
        int cardW = Math.max(150, (itemsW - GRID_GAP * (columns - 1)) / columns);
        int visibleRows = Math.max(1, (itemsH + GRID_GAP) / (CARD_H + GRID_GAP));
        int totalRows = (filtered.size() + columns - 1) / columns;
        gridScrollRows = Math.max(0, Math.min(gridScrollRows, Math.max(0, totalRows - visibleRows)));

        for (int index = 0; index < filtered.size(); index++) {
            int row = index / columns;
            if (row < gridScrollRows || row >= gridScrollRows + visibleRows) {
                continue;
            }
            int col = index % columns;
            int cardX = itemsX + col * (cardW + GRID_GAP);
            int cardY = itemsY + (row - gridScrollRows) * (CARD_H + GRID_GAP);
            renderListingCard(graphics, PlayerShopClientState.listings().get(filtered.get(index)),
                    cardX, cardY, cardW, CARD_H, mouseX, mouseY);
        }
        ShopUiUtil.renderScrollIndicators(graphics, this.font, gx, itemsY, itemsW, itemsH,
                gridScrollRows, visibleRows, totalRows);
    }

    private void renderListingCard(GuiGraphics graphics, PlayerShopListingData l, int x, int y, int width, int height,
                                   int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        boolean outOfStock = l.stock() <= 0 && !PlayerShopClientState.adminShopMode();
        int border = hovered ? ShopColors.BORDER_GLOW : ShopColors.BORDER_MUTED;
        int fill = hovered ? ShopColors.SURFACE_OVERLAY : ShopColors.SURFACE_RAISED;
        ShopUiUtil.renderNocturnePanel(graphics, x, y, width, height, fill, border);

        int slot = 40;
        int slotX = x + 8;
        int slotY = y + (height - slot) / 2;
        ShopUiUtil.renderNocturnePanel(graphics, slotX, slotY, slot, slot, ShopColors.SURFACE_BASE, ShopColors.BORDER_MUTED);
        ShopUiUtil.renderItemIconWithNbt(graphics, this.font, l.itemId(), l.nbtJson(),
                slotX + (slot - 16) / 2, slotY + (slot - 16) / 2);

        int tx = slotX + slot + 8;
        int tRight = x + width - 8;

        // Name (+ promo chip).
        int nameY = y + 8;
        int nameBudget = tRight - tx;
        int percent = hasMoney(l) ? ShopUiUtil.computePromoPercent(l.moneyPriceMinor(), l.effectiveUnitPriceMinor()) : 0;
        if (percent > 0) {
            String promoLabel = percent >= 100
                    ? Component.translatable("gui.futureshops.player_shop_block.detail.promo_free").getString()
                    : "-" + percent + "%";
            int tagW = Math.max(20, this.font.width(promoLabel) + 12);
            ShopUiUtil.renderTag(graphics, this.font, tRight - tagW, nameY - 2, promoLabel, ShopUiUtil.TagStyle.ACCENT);
            nameBudget -= tagW + 6;
        }
        ShopUiUtil.renderScrollingString(graphics, this.font,
                ShopUiUtil.getItemDisplayNameWithNbtAndQty(l.itemId(), l.nbtJson(), l.baseQuantity()),
                tx, nameY, Math.max(20, nameBudget), ShopColors.TEXT_STRONG);

        // Price row.
        int priceY = y + 24;
        if (hasMoney(l)) {
            int coinW = ShopUiUtil.renderCoinAmount(graphics, this.font, tx, priceY,
                    ShopUiUtil.formatMinorUnits(l.effectiveUnitPriceMinor()),
                    outOfStock ? ShopColors.TEXT_FAINT : ShopColors.TEXT_STRONG);
            if (percent > 0) {
                graphics.drawString(this.font,
                        Component.literal(ShopUiUtil.formatMinorUnits(l.moneyPriceMinor()))
                                .withStyle(ChatFormatting.STRIKETHROUGH),
                        tx + coinW + 6, priceY, ShopColors.NEUTRAL_600, false);
            }
        } else {
            graphics.drawString(this.font, Component.translatable("gui.futureshops.storefront.barter_only"),
                    tx, priceY, ShopColors.TEXT_BARTER, false);
        }

        // Stock (colored) + Trade tag.
        int stockY = y + 40;
        String stockStr;
        int stockColor;
        if (PlayerShopClientState.adminShopMode()) {
            stockStr = Component.translatable("gui.futureshops.shop_main.stock_unlimited").getString();
            stockColor = ShopColors.NEUTRAL_500;
        } else if (outOfStock) {
            stockStr = Component.translatable("gui.futureshops.shop_main.sold_out").getString();
            stockColor = ShopColors.STATUS_DANGER;
        } else {
            stockStr = Component.translatable("gui.futureshops.shop_main.stock_left", l.stock()).getString();
            stockColor = l.stock() <= 5 ? ShopColors.STATUS_WARNING : ShopColors.NEUTRAL_500;
        }
        graphics.drawString(this.font, stockStr, tx, stockY, stockColor, false);
        if (supportsBarter(l)) {
            ShopUiUtil.renderTag(graphics, this.font,
                    Math.min(tx + this.font.width(stockStr) + 6, tRight - 40), stockY - 2,
                    Component.translatable("gui.futureshops.shell.trade_tag").getString(), ShopUiUtil.TagStyle.OUTLINE);
        }

        if (hovered) {
            tooltipItemId = l.itemId();
            tooltipNbtJson = l.nbtJson() == null ? "" : l.nbtJson();
            tooltipMouseX = mouseX;
            tooltipMouseY = mouseY;
        }
    }

    private String footerHint() {
        if (detailActive()) {
            return Component.translatable("gui.futureshops.storefront.footer_detail").getString();
        }
        return Component.translatable("gui.futureshops.storefront.footer_browse").getString();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Visitor actions — reuse the existing packet senders / child screens verbatim.
    // ═══════════════════════════════════════════════════════════════════════

    private void enterDetail(int realIndex) {
        detailIndex = realIndex;
        PlayerShopClientState.setSelectedListingIndex(realIndex);
        setQuantity(1);
        updateWidgets();
    }

    private void openBarter() {
        if (detailIndex >= 0) {
            PlayerShopClientState.setSelectedListingIndex(detailIndex);
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(new PlayerShopBarterScreen(this, getQuantity()));
        }
    }

    private void openSell() {
        if (detailIndex >= 0) {
            PlayerShopClientState.setSelectedListingIndex(detailIndex);
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(new PlayerShopSellScreen(this, getQuantity()));
        }
    }

    /** Mirrors PlayerShopBlockScreen.addToCart — keyed by (shopPos, listingIndex). */
    private void addToCart() {
        PlayerShopListingData listing = currentDetailListing();
        if (listing == null || (listing.stock() <= 0 && !PlayerShopClientState.adminShopMode())) return;
        if (detailIndex >= 0) {
            PlayerShopClientState.setSelectedListingIndex(detailIndex);
        }
        int qty = getQuantity();
        String shopName = PlayerShopClientState.shopName().isBlank()
                ? Component.translatable("gui.futureshops.player_shop_block.header.shop_suffix",
                        PlayerShopClientState.ownerName()).getString()
                : PlayerShopClientState.shopName();
        PlayerShopCartState.addToCart(
                PlayerShopClientState.shopPos(),
                PlayerShopClientState.selectedListingIndex(),
                qty,
                listing.itemId(),
                shopName,
                listing.effectiveUnitPriceMinor(),
                listing.baseQuantity(),
                listing.tradeMode(),
                listing.barterItemId(),
                listing.barterItemCount(),
                listing.barterNbtJson(),
                listing.nbtJson(),
                listing.nbtAware());
    }

    /**
     * Sends the money buy. Always passes a non-empty 4th {@code paymentMethod} arg — the
     * BuyPacketCallSiteTest invariant — so the server's BOTH-mode guard honours the button
     * instead of auto-detecting and silently bartering.
     */
    private void buy(int quantity, String paymentMethod, PaymentSource paymentSource) {
        if (detailIndex >= 0) {
            PlayerShopClientState.setSelectedListingIndex(detailIndex);
        }
        PlayerShopResponseTracker.PendingRequest request =
                ShopClientPacketHandler.beginPlayerShopRequest(
                        PlayerShopResponseTracker.Operation.PURCHASE, 0);
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopBuyPacket(
                PlayerShopClientState.shopPos(), PlayerShopClientState.selectedListingIndex(), quantity,
                paymentMethod, paymentSource.wire(), request.requestId(),
                request.responseToken()));
    }

    /** Buy confirmation modal — mirrors PlayerShopBlockScreen.showBuyConfirmation (money / compound). */
    private void showBuyConfirmation(int quantity) {
        PlayerShopListingData listing = currentDetailListing();
        if (listing == null) return;
        String itemName = ShopUiUtil.getItemDisplayNameWithNbtAndQty(listing.itemId(), listing.nbtJson(), listing.baseQuantity());
        long total = listing.effectiveUnitPriceMinor() * quantity;
        String totalStr = total <= 0
                ? Component.translatable("gui.futureshops.player_shop_block.confirm.free").getString()
                : ShopUiUtil.formatMinorUnits(total) + " " + ShopClientState.getCurrencyName();

        boolean compound = "MONEY_AND_BARTER".equalsIgnoreCase(listing.tradeMode());
        List<ConfirmationModal.SummaryLine> summary = new ArrayList<>();
        summary.add(ConfirmationModal.SummaryLine.item(listing.itemId(),
                Component.translatable("gui.futureshops.player_shop_block.confirm.item_line", itemName, quantity).getString(),
                listing.nbtJson()));

        String totalLine;
        String paymentMethod;
        if (compound) {
            // Compound listing — the server withdraws coins AND the barter items. Surface both.
            int barterAmount = listing.barterItemCount() * quantity;
            String barterId = listing.barterItemId();
            String barterName = barterId == null || barterId.isBlank()
                    ? Component.translatable("gui.futureshops.player_shop_block.confirm.unknown_item").getString()
                    : ShopUiUtil.getItemDisplayNameWithNbt(barterId, listing.barterNbtJson());
            summary.add(ConfirmationModal.SummaryLine.item(
                    barterId != null ? barterId : "",
                    Component.translatable("gui.futureshops.player_shop_block.confirm.plus_give", barterAmount, barterName).getString(),
                    listing.barterNbtJson()));
            totalLine = Component.translatable("gui.futureshops.player_shop_block.confirm.total_compound",
                    totalStr, barterAmount, barterName).getString();
            // Server treats MONEY_AND_BARTER as compound regardless of paymentMethod; send the
            // explicit tag so the BOTH-mode blank-guard can't misfire and the invariant holds.
            paymentMethod = "MONEY_AND_BARTER";
        } else {
            totalLine = Component.translatable("gui.futureshops.player_shop_block.confirm.total", totalStr).getString();
            // Signal MONEY explicitly so a BOTH-mode listing always buys with coins here.
            paymentMethod = "MONEY";
        }

        String pm = paymentMethod;
        if (total > 0L) {
            confirmationModal = new ConfirmationModal(
                    Component.translatable("gui.futureshops.player_shop_block.confirm.title").getString(),
                    summary,
                    totalLine,
                    (modal, paymentSource) -> {
                        modal.setProcessing();
                        buy(quantity, pm, paymentSource);
                    },
                    () -> confirmationModal = null);
        } else {
            confirmationModal = new ConfirmationModal(
                    Component.translatable("gui.futureshops.player_shop_block.confirm.title").getString(),
                    summary,
                    totalLine,
                    modal -> {
                        modal.setProcessing();
                        buy(quantity, pm, PaymentSource.WALLET);
                    },
                    () -> confirmationModal = null);
        }
    }

    /** Routed from ShopClientPacketHandler on the buy result so the modal can resolve. */
    public void onTransactionResult(boolean success, String message) {
        if (confirmationModal != null) {
            if (success) {
                confirmationModal.setSuccess(message);
            } else {
                confirmationModal.setFailed(message);
            }
        }
    }

    // ── Quantity helpers (mirror PlayerShopBlockScreen's smart-max) ──

    private int getQuantity() {
        if (qtyBox == null) return 1;
        try {
            return clampQuantity(Integer.parseInt(qtyBox.getValue()));
        } catch (Exception ignored) {
            return 1;
        }
    }

    private void setQuantity(int quantity) {
        if (qtyBox != null) qtyBox.setValue(Integer.toString(clampQuantity(quantity)));
    }

    private int clampQuantity(int quantity) {
        PlayerShopListingData listing = currentDetailListing();
        int stockCap = listing == null ? 999 : PurchaseQuantityPolicy.playerShopStockMaximum(
                PlayerShopClientState.adminShopMode(), listing.stock());
        return Math.max(1, Math.min(stockCap, quantity));
    }

    /** Smart max uses stock for money and the exact barter inventory cap when barter is required. */
    private int resolveMaxQuantity() {
        PlayerShopListingData listing = currentDetailListing();
        if (listing == null) return 1;
        int stock = PurchaseQuantityPolicy.playerShopStockMaximum(
                PlayerShopClientState.adminShopMode(), listing.stock());
        String mode = listing.tradeMode().toUpperCase(Locale.ROOT);
        int maxBarter = Integer.MAX_VALUE;

        if (!"MONEY".equals(mode)) {
            String barterId = listing.barterItemId();
            int barterCost = listing.barterItemCount();
            if (barterId != null && !barterId.isBlank() && barterCost > 0) {
                maxBarter = ShopUiUtil.countPlayerInventoryNbt(barterId, listing.barterNbtJson(), listing.barterNbtAware()) / barterCost;
            }
        }
        return PurchaseQuantityPolicy.playerShopMaximum(mode, stock, maxBarter);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Input.
    // ═══════════════════════════════════════════════════════════════════════

    private static boolean inRect(int[] r, double mx, double my) {
        return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (confirmationModal != null) {
            return true;
        }
        if (!optionA && stocked && detailIndex < 0) {
            int cy = contentY();
            int ch = contentH();
            if (mouseX >= contentX() && mouseX <= contentX() + sidebarW && mouseY >= cy && mouseY <= cy + ch) {
                sidebarScrollIdx = Math.max(0, sidebarScrollIdx - (int) delta);
                return true;
            }
            if (mouseX >= gridX() && mouseX <= gridX() + gridW() && mouseY >= cy && mouseY <= cy + ch) {
                gridScrollRows = Math.max(0, gridScrollRows - (int) delta);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (confirmationModal != null) {
            return confirmationModal.mouseClicked(mouseX, mouseY, button, this.font);
        }
        // Flat detail-row buttons first (they only exist while a detail view is showing).
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) return true;

        // ── Header ──
        if (headerHit != null && headerHit.hitClose(mouseX, mouseY)) {
            onClose();
            return true;
        }
        // ── Footer cart ──
        if (inRect(footerCartRect, mouseX, mouseY)) {
            if (this.minecraft != null) this.minecraft.setScreen(new PlayerShopCartScreen(this));
            return true;
        }

        // Detail state → let the widget buttons handle it.
        if (optionA || detailIndex >= 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (!stocked) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // ── Grid: Buy/Barter segmented ──
        if (segEdges != null && mouseY >= segY && mouseY < segY + segH) {
            for (int i = 0; i + 1 < segEdges.length; i++) {
                if (mouseX >= segEdges[i] && mouseX < segEdges[i + 1]) {
                    setBarterMode(i == 1);
                    return true;
                }
            }
        }

        // ── Grid: sidebar dept selection ──
        int sbX = contentX();
        int sbY = contentY();
        int sbH = contentH();
        if (mouseX >= sbX && mouseX <= sbX + sidebarW && mouseY >= sbY + 22 && mouseY <= sbY + sbH) {
            int rowH = 20;
            int listY = sbY + 22;
            int maxVisible = Math.max(1, (sbH - 22) / rowH);
            int count = deptLabels.size();
            for (int i = sidebarScrollIdx; i < count && i < sidebarScrollIdx + maxVisible; i++) {
                int rowY = listY + (i - sidebarScrollIdx) * rowH;
                if (mouseY >= rowY && mouseY < rowY + rowH - 2) {
                    selectedDeptIdx = i;
                    gridScrollRows = 0;
                    rebuildFiltered();
                    return true;
                }
            }
        }

        // ── Grid: card clicks ──
        int gx = gridX();
        int itemsX = gx;
        int itemsY = gridY() + TOOLBAR_H;
        int itemsW = gridW();
        int itemsH = gridH() - TOOLBAR_H;
        if (!filtered.isEmpty() && mouseX >= itemsX && mouseX <= itemsX + itemsW && mouseY >= itemsY && mouseY <= itemsY + itemsH) {
            int cardMinW = 190;
            int columns = Math.max(1, Math.min(4, (itemsW + GRID_GAP) / (cardMinW + GRID_GAP)));
            int cardW = Math.max(150, (itemsW - GRID_GAP * (columns - 1)) / columns);
            int visibleRows = Math.max(1, (itemsH + GRID_GAP) / (CARD_H + GRID_GAP));
            for (int index = 0; index < filtered.size(); index++) {
                int row = index / columns;
                if (row < gridScrollRows || row >= gridScrollRows + visibleRows) {
                    continue;
                }
                int col = index % columns;
                int cardX = itemsX + col * (cardW + GRID_GAP);
                int cardY = itemsY + (row - gridScrollRows) * (CARD_H + GRID_GAP);
                if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + CARD_H) {
                    onCardClicked(filtered.get(index), button);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Left = drill into detail (or straight to barter in barter mode); right/shift = quick add to cart. */
    private void onCardClicked(int realIndex, int button) {
        PlayerShopListingData l = PlayerShopClientState.listings().get(realIndex);
        if (button == 0 && hasShiftDown() && hasMoney(l) && (l.stock() > 0 || PlayerShopClientState.adminShopMode())) {
            enterDetail(realIndex);
            addToCart();
            detailIndex = -1;
            updateWidgets();
            return;
        }
        if (button == 1 && hasMoney(l) && (l.stock() > 0 || PlayerShopClientState.adminShopMode())) {
            enterDetail(realIndex);
            addToCart();
            detailIndex = -1;
            updateWidgets();
            return;
        }
        if (button == 0) {
            PlayerShopClientState.setSelectedListingIndex(realIndex);
            // Barter segment jumps straight to the barter screen (mirrors ShopMainScreen's barterMode).
            if (barterMode && supportsBarter(l)) {
                if (this.minecraft != null) this.minecraft.setScreen(new PlayerShopBarterScreen(this, 1));
                return;
            }
            enterDetail(realIndex);
        }
    }

    private void setBarterMode(boolean on) {
        if (barterMode == on) return;
        barterMode = on;
        gridScrollRows = 0;
        rebuildFiltered();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmationModal != null) {
            confirmationModal.keyPressed(keyCode);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            applyNavigation(ClientNavigationPolicy.storefrontEscape(
                    optionA, optionA || detailIndex >= 0));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    private void returnToParent() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    private void applyNavigation(ClientNavigationPolicy.Action action) {
        if (this.minecraft == null) {
            return;
        }
        switch (action) {
            case RETURN_TO_PARENT -> returnToParent();
            case RETURN_TO_GRID -> {
                detailIndex = -1;
                updateWidgets();
            }
            case CLOSE -> onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
