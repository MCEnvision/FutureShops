package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.PlayerShopCartState;
import com.enviouse.futureshops.client.PlayerShopClientState;
import com.enviouse.futureshops.client.PlayerShopResponseTracker;
import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.PlayerShopListingData;
import com.enviouse.futureshops.data.PlayerShopStorageEntry;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.network.packets.C2SPlayerShopActionPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopBuyPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopBuybackConfigPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopConfigPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopIconPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopSavedConfigPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopSettlementClaimPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopUnlinkStoragePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

public class PlayerShopBlockScreen extends Screen implements ShopScreenMarker {
    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    // ═══ Computed layout regions — prevents overlap at any GUI scale ═══
    private boolean compact;         // true when guiH < 300 (high GUI scale)
    private int headerHeight;        // 30 compact, 50 normal
    private int configPanelHeight;   // 24 compact, 38 normal (owner only, 0 for visitor)
    private int contentStartY;       // absolute Y where listing rail / detail panel begin
    private int contentAreaH;        // height available for listing rail / detail panel
    private int listingRailW;        // adaptive rail width (fraction of guiW)
    private int statusY;             // Y position for status text (right-aligned, set in init)


    private int listingScroll;
    private EditBox quantityBox;
    private EditBox shopNameBox;

    // Parent screen for back-button navigation (Item 4 & 9)
    private final Screen parent;

    // Tooltip tracking for advanced item tooltips (Item 6)
    private String hoveredItemId = null;
    private String hoveredNbtJson = null;
    private int hoveredMouseX;
    private int hoveredMouseY;

    // Full per-listing description to surface as a hover tooltip when the inline
    // 3-line preview is truncated with an ellipsis.
    private String hoveredDescriptionFull = null;
    private int hoveredDescriptionMouseX;
    private int hoveredDescriptionMouseY;

    // Spec §8: Confirmation modal overlay
    private ConfirmationModal confirmationModal = null;

    // Debounced price/barter editing (Item 10)
    private long priceEditTimestamp = 0L;
    private long barterEditTimestamp = 0L;
    private long baseQtyEditTimestamp = 0L;
    private static final long DEBOUNCE_MS = 600L;

    // Owner controls — the buy/sell UI is now fully immediate-mode; only the text inputs
    // remain as real widgets. Everything clickable is drawn via ShopUiUtil.button and
    // registered into clickZones each frame.
    private EditBox priceBox;
    private EditBox barterCountBox;
    private EditBox baseQtyBox;
    private EditBox configNameBox;

    // Authoritative backing state for the two config toggles (Single/Multi + barter
    // Same/Separate). The segmented controls read/write these; Save persists them.
    private boolean configSingleMode;
    private boolean configBarterSame;

    // ═══ Flat Nocturne button infrastructure ═══
    // Per-frame click zones populated by ShopUiUtil.button(...) during render() and consulted
    // in mouseClicked via ShopUiUtil.dispatchClicks. Immediate-mode: draw + hit-region come
    // from the same call, so the two can never drift apart.
    private final java.util.List<ShopUiUtil.ClickZone> clickZones = new java.util.ArrayList<>();
    // A control tooltip surfaced after super.render() when a flat button/toggle is hovered.
    private Component hoveredTooltip;
    private int mouseXNow;
    private int mouseYNow;

    // Redesign: 4-sub-tab owner layout (Listings / Storefront / Storage / Payouts).
    private enum OwnerTab { LISTINGS, STOREFRONT, STORAGE, PAYOUTS }
    private OwnerTab activeTab = OwnerTab.LISTINGS;
    private int tabBarY;

    // Owner buyback price/cap step-through cycles.
    private static final long[] BUYBACK_PRICE_CYCLE_MINOR = new long[]{0L, 100L, 1000L, 5000L, 10000L, 50000L, 100000L};
    private static final int[] BUYBACK_CAP_CYCLE = new int[]{0, 16, 64, 256, 1024, 9999};

    public PlayerShopBlockScreen() {
        this(null);
    }

    public PlayerShopBlockScreen(Screen parent) {
        super(Component.translatable("gui.futureshops.player_shop_block.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // ═══ Full-screen layout — use almost all available pixels ═══
        guiW = Math.max(320, this.width - 4);
        guiH = Math.max(200, this.height - 4);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;

        compact = guiH < 300;
        headerHeight = compact ? 30 : 50;
        listingRailW = Math.max(120, Math.min(200, guiW * 30 / 100));

        if (PlayerShopClientState.owner()) {
            configPanelHeight = compact ? 22 : 28;
            tabBarY = guiTop + headerHeight + 2;
            contentStartY = tabBarY + 22;
            contentAreaH = Math.max(40, guiH - (contentStartY - guiTop) - 20);
            statusY = guiTop + guiH - 14;
            createOwnerEditBoxes();
        } else {
            configPanelHeight = 0;
            contentStartY = guiTop + headerHeight + 4;
            contentAreaH = Math.max(40, guiH - (contentStartY - guiTop) - 40);
            statusY = guiTop + guiH - 36;
            // Auto-select the only listing for single-item shops.
            List<PlayerShopListingData> listings = PlayerShopClientState.listings();
            if (listings.size() == 1) {
                PlayerShopClientState.setSelectedListingIndex(0);
                PlayerShopListingData only = listings.get(0);
                if ("BARTER".equalsIgnoreCase(only.tradeMode()) && this.minecraft != null) {
                    this.minecraft.tell(() -> this.minecraft.setScreen(new PlayerShopBarterScreen(null)));
                    return;
                }
            }
            createVisitorEditBoxes();
        }
    }

    /** Owner-only editable text fields (kept as real widgets); positioned each frame in render(). */
    private void createOwnerEditBoxes() {
        shopNameBox = new EditBox(this.font, guiLeft + 44, contentStartY + 26, 120, 14,
                Component.translatable("gui.futureshops.player_shop_block.config.shop_name_narration"));
        shopNameBox.setMaxLength(32);
        shopNameBox.setValue(PlayerShopClientState.shopName());
        addRenderableWidget(shopNameBox);

        priceBox = new EditBox(this.font, 0, 0, 56, 16,
                Component.translatable("gui.futureshops.player_shop_block.detail.price_narration"));
        priceBox.setMaxLength(10);
        priceBox.setValue(currentPriceText());
        priceBox.setResponder(value -> priceEditTimestamp = System.currentTimeMillis());
        addRenderableWidget(priceBox);

        barterCountBox = new EditBox(this.font, 0, 0, 40, 16,
                Component.translatable("gui.futureshops.player_shop_block.detail.qty_narration"));
        barterCountBox.setMaxLength(4);
        barterCountBox.setValue(String.valueOf(currentBarterCount()));
        barterCountBox.setResponder(value -> barterEditTimestamp = System.currentTimeMillis());
        addRenderableWidget(barterCountBox);

        baseQtyBox = new EditBox(this.font, 0, 0, 40, 16,
                Component.translatable("gui.futureshops.player_shop_block.detail.bq_narration"));
        baseQtyBox.setMaxLength(4);
        baseQtyBox.setValue(String.valueOf(currentBaseQty()));
        baseQtyBox.setResponder(value -> baseQtyEditTimestamp = System.currentTimeMillis());
        addRenderableWidget(baseQtyBox);

        configNameBox = new EditBox(this.font, 0, 0, 120, 14,
                Component.translatable("gui.futureshops.player_shop_block.savedcfg.name_narration"));
        configNameBox.setMaxLength(24);
        addRenderableWidget(configNameBox);

        configSingleMode = PlayerShopClientState.singleItemMode();
        configBarterSame = PlayerShopClientState.barterStorageSame();
    }

    /** Visitor-only quantity field. */
    private void createVisitorEditBoxes() {
        quantityBox = new EditBox(this.font, 0, 0, 32, 14,
                Component.translatable("gui.futureshops.player_shop_block.visitor.qty"));
        quantityBox.setValue("1");
        quantityBox.setMaxLength(4);
        quantityBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        quantityBox.setResponder(value -> { /* no-op; clamp on use */ });
        addRenderableWidget(quantityBox);
    }

    /** Payouts tab: sends a named saved-config op for the name typed in configNameBox. */
    private void sendSavedConfig(String op) {
        sendSavedConfigNamed(op, configNameBox == null ? "" : configNameBox.getValue());
    }

    /** Sends a named saved-config op ("SAVE"/"APPLY"/"DELETE") for a specific name. */
    private void sendSavedConfigNamed(String op, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return;
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopSavedConfigPacket(
                PlayerShopClientState.shopPos(), op, trimmed));
    }

    /**
     * Storefront tab: sets the floating-shop-icon mode explicitly (CYCLE / OWNER_HEAD /
     * CUSTOM_ITEM). For CUSTOM_ITEM it captures the held-item id.
     */
    private void sendFloatingIconMode(String mode) {
        String itemId = "";
        if ("CUSTOM_ITEM".equals(mode) && this.minecraft != null && this.minecraft.player != null) {
            ItemStack held = this.minecraft.player.getMainHandItem();
            if (!held.isEmpty()) {
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(held.getItem());
                if (key != null) itemId = key.toString();
            }
        }
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopIconPacket(
                PlayerShopClientState.shopPos(), mode, itemId));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();
        hoveredItemId = null;
        hoveredNbtJson = null;
        hoveredDescriptionFull = null;
        hoveredTooltip = null;
        mouseXNow = mouseX;
        mouseYNow = mouseY;

        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);

        int accentColor = PlayerShopClientState.owner() ? ShopColors.ACCENT_CURRENCY : ShopColors.ACCENT_PRIMARY;
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + 2, accentColor);

        renderHeader(graphics);
        renderCloseButton(graphics);
        renderBackButton(graphics);

        boolean owner = PlayerShopClientState.owner();
        hideAllEditBoxes();

        if (owner) {
            renderOwnerTabBar(graphics);
            switch (activeTab) {
                case LISTINGS -> renderOwnerListingsTab(graphics);
                case STOREFRONT -> renderStorefrontTab(graphics);
                case STORAGE -> renderStorageTab(graphics);
                case PAYOUTS -> renderPayoutsTab(graphics);
            }
            syncOwnerFields();
            tickDebouncedEdits();
        } else {
            boolean singleItemVisitor = PlayerShopClientState.listings().size() == 1;
            if (singleItemVisitor) {
                renderSingleItemDetail(graphics, mouseX, mouseY);
            } else {
                renderListingRail(graphics, mouseX, mouseY);
                renderDetailPanel(graphics, mouseX, mouseY);
            }
            renderVisitorActionBar(graphics);
        }

        renderStatus(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);

        // Advanced item tooltip wins; then a truncated-description tooltip; then a control tooltip.
        if (hoveredItemId != null) {
            ShopUiUtil.renderItemTooltip(graphics, this.font, hoveredItemId,
                    hoveredNbtJson != null ? hoveredNbtJson : "", mouseX, mouseY);
        } else if (hoveredDescriptionFull != null && !hoveredDescriptionFull.isBlank()) {
            graphics.renderTooltip(this.font, Component.literal(hoveredDescriptionFull),
                    hoveredDescriptionMouseX, hoveredDescriptionMouseY);
        } else if (hoveredTooltip != null) {
            graphics.renderTooltip(this.font, hoveredTooltip, mouseX, mouseY);
        }

        if (confirmationModal != null) {
            confirmationModal.render(graphics, this.font, this.width, this.height, mouseX, mouseY);
            if (confirmationModal.shouldAutoDismiss()) {
                confirmationModal = null;
            }
        }
    }

    private void renderHeader(GuiGraphics graphics) {
        int hx = guiLeft + 8;
        int hy = guiTop + 4;
        int hw = guiW - 16;
        int hh = headerHeight - 4;
        int accentColor = PlayerShopClientState.owner() ? ShopColors.ACCENT_CURRENCY : ShopColors.ACCENT_PRIMARY;
        ShopUiUtil.renderCard(graphics, hx, hy, hw, hh);
        graphics.fill(hx, hy, hx + hw, hy + 2, accentColor);

        if (compact) {
            // Compact header: single row — face + title + link status
            ShopUiUtil.renderPlayerFace(graphics, PlayerShopClientState.ownerUuid(),
                    PlayerShopClientState.ownerName(), hx + 4, hy + 4, hh - 8);
            String title = I18n.get(PlayerShopClientState.owner()
                    ? "gui.futureshops.player_shop_block.header.owner_compact"
                    : "gui.futureshops.player_shop_block.header.visitor_compact");
            // Clamp the compact title so it stops before the franchise badge at hx + hw/2.
            int compactTitleMaxW = Math.max(20, hw / 2 - hh - 6);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(title, compactTitleMaxW), hx + hh, hy + 4, ShopColors.TEXT_PRIMARY, false);
            String shopName = PlayerShopClientState.shopName().isBlank()
                    ? I18n.get("gui.futureshops.player_shop_block.header.shop_suffix", PlayerShopClientState.ownerName())
                    : PlayerShopClientState.shopName();
            graphics.drawString(this.font, this.font.plainSubstrByWidth("§7" + shopName, hw / 2 - hh), hx + hh, hy + 14, ShopColors.TEXT_SECONDARY, false);
            // Franchise badge (compact)
            String compactFranchise = PlayerShopClientState.franchiseName();
            if (!compactFranchise.isBlank()) {
                ShopUiUtil.drawChip(graphics, this.font, hx + hw / 2, hy + 4,
                        "⚑ " + this.font.plainSubstrByWidth(compactFranchise, 60),
                        ShopColors.BG_PANEL, ShopColors.ACCENT_PURPLE, ShopColors.ACCENT_PURPLE);
            }
            // Description in compact header (scrolling)
            String compactDesc = PlayerShopClientState.description();
            if (!compactDesc.isBlank()) {
                int descX = hx + hh + this.font.width(this.font.plainSubstrByWidth("§7" + shopName, hw / 2 - hh)) + 6;
                // Stop the description before the franchise badge at hx + hw/2.
                int descMaxW = Math.max(20, (hx + hw / 2) - descX - 6);
                ShopUiUtil.renderScrollingString(graphics, this.font, "§o" + compactDesc, descX, hy + 14, descMaxW, ShopColors.TEXT_SECONDARY);
            }
            // Link chip — right aligned, reserving 26px for the close (×) button
            ShopUiUtil.drawChip(graphics, this.font, hx + hw - 124, hy + 6,
                    this.font.plainSubstrByWidth(
                            I18n.get(PlayerShopClientState.linked()
                                    ? "gui.futureshops.player_shop_block.header.linked_short"
                                    : "gui.futureshops.player_shop_block.header.not_linked_short"), 80),
                    ShopColors.BG_PANEL,
                    PlayerShopClientState.linked() ? ShopColors.SUCCESS : ShopColors.ERROR,
                    PlayerShopClientState.linked() ? ShopColors.SUCCESS : ShopColors.ERROR);
        } else {
            // Normal header: face + two-line title + franchise/desc mid | link chip + revenue right
            ShopUiUtil.renderPlayerFace(graphics, PlayerShopClientState.ownerUuid(),
                    PlayerShopClientState.ownerName(), hx + 8, hy + 8, 34);
            String title = I18n.get(PlayerShopClientState.owner()
                    ? "gui.futureshops.player_shop_block.header.owner"
                    : "gui.futureshops.player_shop_block.header.visitor");
            String shopName = PlayerShopClientState.shopName().isBlank()
                    ? I18n.get("gui.futureshops.player_shop_block.header.shop_suffix", PlayerShopClientState.ownerName())
                    : PlayerShopClientState.shopName();

            // Reserve right region for link chip + revenue. Narrows responsively so the mid
            // region (franchise chip + description) cannot collide with it. Additionally
            // pushed 26px further left so the chip never slides under the close (×) button.
            int closeReserve = 26;
            int rightRegionW = Math.min(140, Math.max(90, hw / 3));
            int rightRegionX = hx + hw - rightRegionW - closeReserve;

            int midX = hx + 190;
            int centerX = hx + 50;
            int centerMaxW = Math.max(60, rightRegionX - centerX - 6);
            // When the mid column carries a franchise chip or description, the center title/name
            // must stop at the mid column so they can't slide underneath it.
            boolean hasMidContent = !PlayerShopClientState.franchiseName().isBlank()
                    || !PlayerShopClientState.description().isBlank();
            int centerTitleW = hasMidContent ? Math.min(centerMaxW, Math.max(20, midX - centerX - 6)) : centerMaxW;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(title, centerTitleW), centerX, hy + 8, ShopColors.TEXT_PRIMARY, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth("§7" + shopName, centerTitleW), centerX, hy + 20, ShopColors.TEXT_SECONDARY, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(
                    I18n.get("gui.futureshops.player_shop_block.header.owner_label", PlayerShopClientState.ownerName()),
                    Math.min(130, centerMaxW)), centerX, hy + 32, ShopColors.TEXT_SECONDARY, false);

            // Mid region (after owner text) — franchise chip + description, only when there's room
            int midAvailW = Math.max(0, rightRegionX - midX - 6);
            String fName = PlayerShopClientState.franchiseName();
            if (!fName.isBlank() && midAvailW > 40) {
                int fTextMax = Math.max(20, midAvailW - 12);
                ShopUiUtil.drawChip(graphics, this.font, midX, hy + 20,
                        "⚑ " + this.font.plainSubstrByWidth(fName, Math.min(80, fTextMax)),
                        ShopColors.BG_PANEL, ShopColors.ACCENT_PURPLE, ShopColors.ACCENT_PURPLE);
            }
            String normalDesc = PlayerShopClientState.description();
            if (!normalDesc.isBlank() && midAvailW > 20) {
                ShopUiUtil.renderScrollingString(graphics, this.font, "§7§o" + normalDesc, midX, hy + 32, midAvailW, ShopColors.TEXT_SECONDARY);
            }

            // Right region — link chip + revenue. The revenue string (pending / total) sits
            // to the LEFT of the storage-linked chip so that at GUI scale 4 it stays visible
            // instead of being pushed under the chip and truncated.
            String linkedText = I18n.get(PlayerShopClientState.linked()
                    ? "gui.futureshops.player_shop_block.header.linked_long"
                    : "gui.futureshops.player_shop_block.header.not_linked_long");
            int chipTextCap = Math.max(20, Math.min(rightRegionW - 10, this.font.width(linkedText)));
            String clippedChip = this.font.plainSubstrByWidth(linkedText, chipTextCap);
            int chipW = this.font.width(clippedChip) + 10;
            int chipX = rightRegionX + Math.max(0, rightRegionW - chipW);
            ShopUiUtil.drawChip(graphics, this.font, chipX, hy + 8, clippedChip,
                    ShopColors.BG_PANEL,
                    PlayerShopClientState.linked() ? ShopColors.SUCCESS : ShopColors.ERROR,
                    PlayerShopClientState.linked() ? ShopColors.SUCCESS : ShopColors.ERROR);
            String revenue = I18n.get("gui.futureshops.player_shop_block.header.revenue",
                    ShopUiUtil.formatMinorUnits(PlayerShopClientState.pendingSettlementMinor()),
                    ShopUiUtil.formatMinorUnits(PlayerShopClientState.lifetimeRevenueMinor()));
            int revenueAvail = Math.max(0, chipX - rightRegionX - 6);
            if (revenueAvail >= 40) {
                ShopUiUtil.renderScrollingString(graphics, this.font, revenue,
                        rightRegionX, hy + 10, revenueAvail, ShopColors.TEXT_PRICE);
            } else {
                // Not enough horizontal room — fall back to the old below-chip placement.
                ShopUiUtil.renderScrollingString(graphics, this.font, revenue,
                        rightRegionX, hy + 30, Math.max(40, rightRegionW - 4), ShopColors.TEXT_PRICE);
            }
        }
    }

    // ══════════════════════════ Flat-button helpers ══════════════════════════

    /** Draws a flat Nocturne button and records a control tooltip when hovered. */
    private boolean btn(GuiGraphics g, int x, int y, int w, int h, Component label,
                        ShopUiUtil.ButtonStyle style, boolean enabled, String symbol, ItemStack icon,
                        Component tip, Runnable onClick) {
        boolean hov = ShopUiUtil.button(g, this.font, clickZones, mouseXNow, mouseYNow,
                x, y, w, h, label, style, enabled, symbol, icon, onClick);
        if (hov && tip != null) hoveredTooltip = tip;
        return hov;
    }

    private boolean isHover(int x, int y, int w, int h) {
        return mouseXNow >= x && mouseXNow < x + w && mouseYNow >= y && mouseYNow < y + h;
    }

    private void sectionHeader(GuiGraphics g, int x, int y, String key) {
        g.drawString(this.font, Component.translatable(key), x, y, ShopColors.NEUTRAL_500, false);
    }

    private int modeIndex(String mode) {
        return switch (mode == null ? "" : mode.toUpperCase(Locale.ROOT)) {
            case "BARTER" -> 1;
            case "BOTH" -> 2;
            case "MONEY_AND_BARTER" -> 3;
            default -> 0;
        };
    }

    /** Owner listings-tab rail reserves a footer strip for the "+ Add listing" button. */
    private int listingRailHeight() {
        return PlayerShopClientState.owner() ? Math.max(40, contentAreaH - 28) : contentAreaH;
    }

    private void hideAllEditBoxes() {
        if (shopNameBox != null) shopNameBox.visible = false;
        if (priceBox != null) priceBox.visible = false;
        if (barterCountBox != null) barterCountBox.visible = false;
        if (baseQtyBox != null) baseQtyBox.visible = false;
        if (configNameBox != null) configNameBox.visible = false;
        if (quantityBox != null) quantityBox.visible = false;
    }

    private void renderCloseButton(GuiGraphics g) {
        btn(g, guiLeft + guiW - 24, guiTop + 6, 18, 14, Component.literal("✕"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, null, null, null, this::closeCompletely);
    }

    private void renderBackButton(GuiGraphics g) {
        if (parent == null) {
            return;
        }
        btn(g, guiLeft + guiW - 72, guiTop + 6, 44, 14,
                Component.translatable(
                        "gui.futureshops.player_shop_block.visitor.back"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, null, null, null,
                this::onClose);
    }

    private void renderOwnerTabBar(GuiGraphics g) {
        int x0 = guiLeft + 8;
        int x1 = guiLeft + guiW - 8;
        int totalW = x1 - x0;
        g.fill(x0, tabBarY + 17, x1, tabBarY + 18, ShopColors.BORDER_MUTED);
        String[] keys = {
                "gui.futureshops.player_shop_block.tab.listings",
                "gui.futureshops.player_shop_block.tab.storefront",
                "gui.futureshops.player_shop_block.tab.storage",
                "gui.futureshops.player_shop_block.tab.payouts"
        };
        OwnerTab[] vals = { OwnerTab.LISTINGS, OwnerTab.STOREFRONT, OwnerTab.STORAGE, OwnerTab.PAYOUTS };
        for (int i = 0; i < 4; i++) {
            int tx = x0 + totalW * i / 4;
            int txEnd = x0 + totalW * (i + 1) / 4;
            int w = Math.max(1, txEnd - tx - 2);
            boolean active = activeTab == vals[i];
            final OwnerTab target = vals[i];
            btn(g, tx, tabBarY, w, 16, Component.translatable(keys[i]),
                    active ? ShopUiUtil.ButtonStyle.SECONDARY : ShopUiUtil.ButtonStyle.GHOST,
                    true, null, null, null, () -> activeTab = target);
            if (active) {
                g.fill(tx, tabBarY + 16, tx + w, tabBarY + 18, ShopColors.ACCENT_PRIMARY);
            }
        }
    }

    // ══════════════════════════ LISTINGS tab ══════════════════════════

    private void renderOwnerListingsTab(GuiGraphics g) {
        // Left column: existing selectable listing rail + full-width Add button beneath it.
        renderListingRail(g, mouseXNow, mouseYNow);
        int railX = guiLeft + 8;
        int addY = contentStartY + listingRailHeight() + 6;
        btn(g, railX, addY, listingRailW, 18,
                Component.translatable("gui.futureshops.player_shop_block.footer.add"),
                ShopUiUtil.ButtonStyle.PRIMARY, true, "＋", null,
                Component.translatable("gui.futureshops.player_shop_block.footer.add_tooltip"),
                () -> sendAction("ADD_LISTING_MAINHAND", 0));

        int detailX = guiLeft + listingRailW + 16;
        int detailW = guiW - listingRailW - 24;
        renderOwnerInspector(g, detailX, contentStartY, detailW, contentAreaH);
    }

    private void inspectorToggle(GuiGraphics g, int rx, int ry, int rw, Component label, boolean on,
                                 Component tip, Runnable onClick) {
        g.drawString(this.font, label, rx, ry + 3, ShopColors.TEXT_MUTED, false);
        // Toggle sits snug after the label — not pinned to the far panel edge, which on the wide
        // inspector left the switch floating ~500px away from its label (looked disconnected).
        int tX = rx + Math.min(rw - 26, this.font.width(label) + 12);
        ShopUiUtil.renderToggle(g, tX, ry, on);
        ShopUiUtil.zone(clickZones, tX, ry, 26, 14, true, onClick);
        if (isHover(rx, ry, rw, 14) && tip != null) hoveredTooltip = tip;
    }

    private void renderOwnerInspector(GuiGraphics g, int px, int py, int pw, int ph) {
        ShopUiUtil.renderNocturnePanelAccentTop(g, px, py, pw, ph);
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        if (listing == null) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.futureshops.player_shop_block.detail.select_listing"),
                    px + pw / 2, py + ph / 2 - 4, ShopColors.TEXT_FAINT);
            return;
        }

        int x = px + 10;
        int w = pw - 20;
        int y = py + 8;

        // Remove button (danger) is pinned bottom-right; compute its rect up-front so that
        // running-y content on the same band (e.g. the stock line) can clip against it.
        int rmW = Math.max(96, this.font.width(Component.translatable("gui.futureshops.player_shop_block.footer.del").getString()) + 24);
        int rmX = px + pw - rmW - 10;
        int rmY = py + ph - 22;

        // Item header.
        ItemStack stack = ShopUiUtil.buildItemStack(listing.itemId(), listing.nbtJson());
        g.renderItem(stack, x, y);
        g.renderItemDecorations(this.font, stack, x, y);
        if (isHover(x, y, 16, 16)) { hoveredItemId = listing.itemId(); hoveredNbtJson = listing.nbtJson(); }
        String nm = ShopUiUtil.getItemDisplayNameWithNbtAndQty(listing.itemId(), listing.nbtJson(), listing.baseQuantity());
        ShopUiUtil.renderScrollingString(g, this.font, nm, x + 22, y, w - 22, ShopColors.TEXT_STRONG);
        g.drawString(this.font, this.font.plainSubstrByWidth(listing.itemId(), w - 22), x + 22, y + 10, ShopColors.NEUTRAL_600, false);
        y += 22;

        // Visibility — Visible + Showcase share one row (the panel is wide; stacking them wasted
        // vertical space and pushed BUYBACK off the bottom on high GUI scales).
        sectionHeader(g, x, y, "gui.futureshops.player_shop_block.inspector.visibility"); y += 8;
        int visHalf = w / 2;
        inspectorToggle(g, x, y, visHalf - 6, Component.translatable("gui.futureshops.player_shop_block.inspector.visible_label"),
                !listing.hidden(), Component.translatable("gui.futureshops.player_shop_block.tip.visible"),
                () -> sendAction("TOGGLE_HIDDEN", 0));
        inspectorToggle(g, x + visHalf, y, w - visHalf, Component.translatable("gui.futureshops.player_shop_block.inspector.showcase_label"),
                listing.showcase(), Component.translatable("gui.futureshops.player_shop_block.tip.showcase"),
                () -> sendAction("TOGGLE_SHOWCASE", 0));
        y += 16;
        if (PlayerShopClientState.singleItemMode()) {
            final int idx = PlayerShopClientState.selectedListingIndex();
            btn(g, x, y, 110, 14, Component.translatable("gui.futureshops.player_shop_block.inspector.set_visible"),
                    ShopUiUtil.ButtonStyle.SECONDARY, true, null, null,
                    Component.translatable("gui.futureshops.player_shop_block.detail.visible_tooltip"),
                    () -> sendAction("SELECT_VISIBLE_LISTING", idx));
            y += 18;
        }

        // Trade mode segmented (click cycles via TOGGLE_MODE).
        sectionHeader(g, x, y, "gui.futureshops.player_shop_block.inspector.trade_mode"); y += 8;
        String[] modeLabels = {
                ShopUiUtil.tradeModeLabel("MONEY"), ShopUiUtil.tradeModeLabel("BARTER"),
                ShopUiUtil.tradeModeLabel("BOTH"), ShopUiUtil.tradeModeLabel("MONEY_AND_BARTER")
        };
        int[] mEdges = ShopUiUtil.renderSegmented(g, this.font, x, y, 16, modeLabels, modeIndex(listing.tradeMode()));
        int mW = mEdges[mEdges.length - 1] - x;
        ShopUiUtil.zone(clickZones, x, y, mW, 16, true, () -> sendAction("TOGGLE_MODE", 0));
        if (isHover(x, y, mW, 16)) hoveredTooltip = Component.translatable("gui.futureshops.trade_mode.cycle_tooltip");
        y += 20;

        int btnSz = 16;

        // Price stepper (priceBox overlays the value slot) + Promo.
        sectionHeader(g, x, y, "gui.futureshops.player_shop_block.inspector.price"); y += 8;
        ShopUiUtil.Stepper ps = ShopUiUtil.renderStepper(g, this.font, x, y, "", 56, btnSz);
        ShopUiUtil.zone(clickZones, ps.minusX(), y, ps.btn(), ps.btn(), true, () -> adjustPrice(-100));
        ShopUiUtil.zone(clickZones, ps.plusX(), y, ps.btn(), ps.btn(), true, () -> adjustPrice(100));
        if (priceBox != null) {
            priceBox.setPosition(x + btnSz + 4, y);
            priceBox.setWidth(56);
            priceBox.setHeight(btnSz);
            priceBox.visible = true;
            priceBox.setEditable(true);
        }
        int promoX = x + ps.width() + 8;
        int promoW = Math.max(48, this.font.width(Component.translatable("gui.futureshops.player_shop_block.footer.promo").getString()) + 14);
        btn(g, promoX, y, promoW, btnSz, Component.translatable("gui.futureshops.player_shop_block.footer.promo"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, null, null,
                Component.translatable("gui.futureshops.player_shop_block.footer.promo_tooltip"),
                () -> { if (this.minecraft != null) this.minecraft.setScreen(new PromoEditorModalScreen(this)); });
        y += btnSz + 4;

        // Stock / batch stepper.
        sectionHeader(g, x, y, "gui.futureshops.player_shop_block.inspector.batch"); y += 8;
        ShopUiUtil.Stepper qs = ShopUiUtil.renderStepper(g, this.font, x, y, "", 40, btnSz);
        ShopUiUtil.zone(clickZones, qs.minusX(), y, qs.btn(), qs.btn(), true, () -> adjustBaseQty(-1));
        ShopUiUtil.zone(clickZones, qs.plusX(), y, qs.btn(), qs.btn(), true, () -> adjustBaseQty(1));
        if (baseQtyBox != null) {
            baseQtyBox.setPosition(x + btnSz + 4, y);
            baseQtyBox.setWidth(40);
            baseQtyBox.setHeight(btnSz);
            baseQtyBox.visible = true;
            baseQtyBox.setEditable(true);
        }
        boolean adminDetail = PlayerShopClientState.adminShopMode();
        String stockStr = adminDetail
                ? Component.translatable("gui.futureshops.player_shop_block.detail.stock_unlimited").getString()
                : I18n.get("gui.futureshops.player_shop_block.detail.stock_prefix", listing.stock());
        int stockTextX = x + qs.width() + 8;
        // Clip so the stock value stops before the pinned Remove button (they can share a band
        // on short panels), not just at the panel's right edge.
        int stockMaxW = Math.min(Math.max(10, w - qs.width() - 8), Math.max(10, rmX - stockTextX - 6));
        g.drawString(this.font, this.font.plainSubstrByWidth(stockStr, stockMaxW),
                stockTextX, y + (btnSz - 8) / 2,
                adminDetail ? ShopColors.SUCCESS : (listing.stock() <= 16 ? ShopColors.ERROR : ShopColors.SUCCESS), false);
        y += btnSz + 4;

        // Barter: Set + count stepper.
        sectionHeader(g, x, y, "gui.futureshops.player_shop_block.inspector.barter"); y += 8;
        int setW = Math.max(40, this.font.width(Component.translatable("gui.futureshops.player_shop_block.detail.barter_set").getString()) + 16);
        btn(g, x, y, setW, btnSz, Component.translatable("gui.futureshops.player_shop_block.detail.barter_set"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, null, null,
                Component.translatable("gui.futureshops.player_shop_block.detail.barter_set_tooltip"),
                () -> sendAction("SET_BARTER_MAINHAND", currentBarterCount()));
        int bStepX = x + setW + 8;
        ShopUiUtil.Stepper bs = ShopUiUtil.renderStepper(g, this.font, bStepX, y, "", 40, btnSz);
        ShopUiUtil.zone(clickZones, bs.minusX(), y, bs.btn(), bs.btn(), true, () -> adjustBarterCount(-1));
        ShopUiUtil.zone(clickZones, bs.plusX(), y, bs.btn(), bs.btn(), true, () -> adjustBarterCount(1));
        if (barterCountBox != null) {
            barterCountBox.setPosition(bStepX + btnSz + 4, y);
            barterCountBox.setWidth(40);
            barterCountBox.setHeight(btnSz);
            barterCountBox.visible = true;
            barterCountBox.setEditable(true);
        }
        y += btnSz + 4;

        // Exact item match.
        sectionHeader(g, x, y, "gui.futureshops.player_shop_block.inspector.matching"); y += 8;
        // The "Exact item match" explanation lives in the toggle tooltip (tip.exact) — no inline
        // sentence, which kept the inspector tall and cluttered.
        inspectorToggle(g, x, y, w, Component.translatable("gui.futureshops.player_shop_block.inspector.exact_label"),
                listing.nbtAware(), Component.translatable("gui.futureshops.player_shop_block.tip.exact"),
                () -> sendAction("TOGGLE_NBT_AWARE", 0));
        y += 18;

        // Department chip + Add, with the listing-description button sharing the same row on the
        // right (saves a whole row so BUYBACK stays on-panel).
        sectionHeader(g, x, y, "gui.futureshops.player_shop_block.inspector.department"); y += 8;
        int chipX = x;
        if (listing.department() != null && !listing.department().isBlank()) {
            chipX += ShopUiUtil.renderTag(g, this.font, x, y,
                    this.font.plainSubstrByWidth(listing.department(), w / 3), ShopUiUtil.TagStyle.NEUTRAL) + 6;
        }
        btn(g, chipX, y, 66, 14, Component.translatable("gui.futureshops.player_shop_block.footer.dept"),
                ShopUiUtil.ButtonStyle.DASHED, true, "＋", null,
                Component.translatable("gui.futureshops.player_shop_block.footer.dept_tooltip"),
                () -> { if (this.minecraft != null) this.minecraft.setScreen(new DepartmentPickerScreen(this)); });
        int ldescW = Math.max(96, this.font.width(Component.translatable("gui.futureshops.player_shop_block.footer.ldesc").getString()) + 16);
        btn(g, x + w - ldescW, y, ldescW, 14, Component.translatable("gui.futureshops.player_shop_block.footer.ldesc"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, null, null,
                Component.translatable("gui.futureshops.player_shop_block.footer.ldesc_tooltip"),
                () -> {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        sendAction("PENDING_LISTING_DESC", 0);
                        this.minecraft.player.displayClientMessage(
                                Component.translatable("gui.futureshops.player_shop_block.chat.ldesc_prompt"), false);
                        onClose();
                    }
                });
        y += 18;

        // Buyback: direction segmented (click cycles) + price/cap cycling buttons.
        sectionHeader(g, x, y, "gui.futureshops.player_shop_block.inspector.buyback"); y += 8;
        String[] dirLabels = {
                Component.translatable("gui.futureshops.player_shop_block.dir.sell").getString(),
                Component.translatable("gui.futureshops.player_shop_block.dir.buy").getString(),
                Component.translatable("gui.futureshops.player_shop_block.dir.both").getString()
        };
        int dirIdx = switch (currentDirection()) { case "BUY" -> 1; case "BOTH" -> 2; default -> 0; };
        int[] dEdges = ShopUiUtil.renderSegmented(g, this.font, x, y, 16, dirLabels, dirIdx);
        int dW = dEdges[dEdges.length - 1] - x;
        ShopUiUtil.zone(clickZones, x, y, dW, 16, true, this::cycleDirection);
        if (isHover(x, y, dW, 16)) hoveredTooltip = Component.translatable("gui.futureshops.player_shop_block.tip.buyback");
        y += 16;
        String bp = Component.translatable("gui.futureshops.player_shop_block.footer.buyback_price").getString()
                + " §7" + ShopUiUtil.formatMinorUnits(listing.buybackPriceMinor());
        int bpW = Math.max(72, this.font.width(bp) + 14);
        btn(g, x, y, bpW, 14, Component.literal(bp), ShopUiUtil.ButtonStyle.SECONDARY, true, null, null,
                Component.translatable("gui.futureshops.player_shop_block.footer.buyback_price_tooltip"),
                this::cycleBuybackPrice);
        int cap = listing.buybackCap();
        String bc = Component.translatable("gui.futureshops.player_shop_block.footer.buyback_cap").getString()
                + " §7" + (cap == 0 ? "∞" : Integer.toString(cap));
        int bcW = Math.max(60, this.font.width(bc) + 14);
        btn(g, x + bpW + 8, y, bcW, 14, Component.literal(bc), ShopUiUtil.ButtonStyle.SECONDARY, true, null, null,
                Component.translatable("gui.futureshops.player_shop_block.footer.buyback_cap_tooltip"),
                this::cycleBuybackCap);

        // Remove listing (danger, bottom-right) — rect computed up-front.
        btn(g, rmX, rmY, rmW, 16, Component.translatable("gui.futureshops.player_shop_block.footer.del"),
                ShopUiUtil.ButtonStyle.DANGER, true, "✕", null,
                Component.translatable("gui.futureshops.player_shop_block.footer.del_tooltip"),
                () -> sendAction("REMOVE_LISTING", 0));
    }

    // ══════════════════════════ STOREFRONT tab ══════════════════════════

    private void renderStorefrontTab(GuiGraphics g) {
        int cx = guiLeft + 12;
        int panelW = guiW - 24;
        ShopUiUtil.renderNocturnePanelAccentTop(g, cx, contentStartY, panelW, contentAreaH);
        int x = cx + 10;
        int w = panelW - 20;
        int y = contentStartY + 10;

        // Shop name + Save.
        g.drawString(this.font, Component.translatable("gui.futureshops.player_shop_block.config.name_label"), x, y + 3, ShopColors.TEXT_MUTED, false);
        int nameW = Math.min(160, w / 2);
        if (shopNameBox != null) {
            shopNameBox.setPosition(x + 40, y);
            shopNameBox.setWidth(nameW);
            shopNameBox.setHeight(14);
            shopNameBox.visible = true;
        }
        int saveW = Math.max(76, this.font.width(Component.translatable("gui.futureshops.player_shop_block.config.save_long").getString()) + 16);
        btn(g, x + 40 + nameW + 8, y, saveW, 14, Component.translatable("gui.futureshops.player_shop_block.config.save_long"),
                ShopUiUtil.ButtonStyle.PRIMARY, true, null, null,
                Component.translatable("gui.futureshops.player_shop_block.config.save_tooltip"), this::saveConfig);
        y += 22;

        // Single / Multi segmented + shop description button.
        String[] smLabels = {
                Component.translatable("gui.futureshops.player_shop_block.config.single").getString(),
                Component.translatable("gui.futureshops.player_shop_block.config.multi").getString()
        };
        int[] smEdges = ShopUiUtil.renderSegmented(g, this.font, x, y, 16, smLabels, configSingleMode ? 0 : 1);
        ShopUiUtil.zone(clickZones, smEdges[0], y, smEdges[1] - smEdges[0], 16, true, () -> configSingleMode = true);
        ShopUiUtil.zone(clickZones, smEdges[1], y, smEdges[2] - smEdges[1], 16, true, () -> configSingleMode = false);
        if (isHover(x, y, smEdges[2] - x, 16)) hoveredTooltip = Component.translatable("gui.futureshops.player_shop_block.config.mode_tooltip");
        btn(g, smEdges[2] + 8, y, 90, 16, Component.translatable("gui.futureshops.player_shop_block.footer.desc"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, null, null,
                Component.translatable("gui.futureshops.player_shop_block.footer.desc_tooltip"),
                () -> {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        sendAction("PENDING_DESC", 0);
                        this.minecraft.player.displayClientMessage(
                                Component.translatable("gui.futureshops.player_shop_block.chat.desc_prompt"), false);
                        onClose();
                    }
                });
        y += 24;

        // Floating shop icon — three mode buttons with item icons.
        sectionHeader(g, x, y, "gui.futureshops.player_shop_block.storefront.icon_header"); y += 12;
        String mode = PlayerShopClientState.floatingIconMode();
        String modeU = mode == null ? "CYCLE" : mode.toUpperCase(Locale.ROOT);
        List<PlayerShopListingData> listings = PlayerShopClientState.listings();
        ItemStack cycleIcon = listings.isEmpty() ? ItemStack.EMPTY
                : ShopUiUtil.buildItemStack(listings.get(0).itemId(), listings.get(0).nbtJson());
        ItemStack headIcon = new ItemStack(Items.PLAYER_HEAD);
        String customId = PlayerShopClientState.floatingIconItem();
        ItemStack customIcon;
        if (customId != null && !customId.isBlank()) {
            customIcon = ShopUiUtil.buildItemStack(customId, "");
        } else if (this.minecraft != null && this.minecraft.player != null) {
            customIcon = this.minecraft.player.getMainHandItem();
        } else {
            customIcon = ItemStack.EMPTY;
        }
        int iconBtnW = Math.max(70, Math.min(150, (w - 16) / 3));
        btn(g, x, y, iconBtnW, 18, Component.translatable("gui.futureshops.player_shop_block.icon.mode.cycle"),
                "CYCLE".equals(modeU) ? ShopUiUtil.ButtonStyle.PRIMARY : ShopUiUtil.ButtonStyle.SECONDARY,
                true, null, cycleIcon, Component.translatable("gui.futureshops.player_shop_block.tip.icon_cycle"),
                () -> sendFloatingIconMode("CYCLE"));
        btn(g, x + iconBtnW + 8, y, iconBtnW, 18, Component.translatable("gui.futureshops.player_shop_block.icon.mode.owner_head"),
                "OWNER_HEAD".equals(modeU) ? ShopUiUtil.ButtonStyle.PRIMARY : ShopUiUtil.ButtonStyle.SECONDARY,
                true, null, headIcon, Component.translatable("gui.futureshops.player_shop_block.tip.icon_owner_head"),
                () -> sendFloatingIconMode("OWNER_HEAD"));
        btn(g, x + 2 * (iconBtnW + 8), y, iconBtnW, 18, Component.translatable("gui.futureshops.player_shop_block.icon.mode.custom_item"),
                "CUSTOM_ITEM".equals(modeU) ? ShopUiUtil.ButtonStyle.PRIMARY : ShopUiUtil.ButtonStyle.SECONDARY,
                true, null, customIcon, Component.translatable("gui.futureshops.player_shop_block.tip.icon_custom"),
                () -> sendFloatingIconMode("CUSTOM_ITEM"));
        y += 26;

        // Display: Height / Scale nudge steppers + nameplate toggle.
        sectionHeader(g, x, y, "gui.futureshops.player_shop_block.storefront.display_header"); y += 12;
        g.drawString(this.font, Component.translatable("gui.futureshops.player_shop_block.storefront.display_y"), x, y + 4, ShopColors.TEXT_MUTED, false);
        int hx = x + 54;
        btn(g, hx, y, 16, 16, Component.literal("-"), ShopUiUtil.ButtonStyle.SECONDARY, true, null, null, null, () -> sendAction("DISPLAY_Y_DOWN", 1));
        btn(g, hx + 18, y, 16, 16, Component.literal("+"), ShopUiUtil.ButtonStyle.SECONDARY, true, null, null, null, () -> sendAction("DISPLAY_Y_UP", 1));
        int sLabelX = hx + 42;
        g.drawString(this.font, Component.translatable("gui.futureshops.player_shop_block.storefront.display_scale"), sLabelX, y + 4, ShopColors.TEXT_MUTED, false);
        int sx = sLabelX + 44;
        btn(g, sx, y, 16, 16, Component.literal("-"), ShopUiUtil.ButtonStyle.SECONDARY, true, null, null, null, () -> sendAction("DISPLAY_SCALE_DOWN", 1));
        btn(g, sx + 18, y, 16, 16, Component.literal("+"), ShopUiUtil.ButtonStyle.SECONDARY, true, null, null, null, () -> sendAction("DISPLAY_SCALE_UP", 1));
        y += 22;
        int npW = Math.max(120, this.font.width(Component.translatable("gui.futureshops.player_shop_block.footer.toggle_nameplate").getString()) + 16);
        btn(g, x, y, npW, 16, Component.translatable("gui.futureshops.player_shop_block.footer.toggle_nameplate"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, null, null,
                Component.translatable("gui.futureshops.player_shop_block.footer.toggle_nameplate_tooltip"),
                () -> sendAction("TOGGLE_NAMEPLATE", 0));
    }

    // ══════════════════════════ STORAGE tab ══════════════════════════

    private void renderStorageTab(GuiGraphics g) {
        int cx = guiLeft + 12;
        int panelW = guiW - 24;
        ShopUiUtil.renderNocturnePanelAccentTop(g, cx, contentStartY, panelW, contentAreaH);
        int x = cx + 10;
        int w = panelW - 20;
        int y = contentStartY + 10;

        List<PlayerShopStorageEntry> entries = PlayerShopClientState.linkedStorages();
        g.drawString(this.font,
                Component.translatable("gui.futureshops.player_shop_block.storage.linked_header", entries.size(), 6),
                x, y, ShopColors.NEUTRAL_500, false);
        y += 14;
        if (entries.isEmpty()) {
            g.drawString(this.font, Component.translatable("gui.futureshops.player_shop_block.storage.empty"),
                    x, y, ShopColors.TEXT_MUTED, false);
            y += 16;
        } else {
            ItemStack chest = new ItemStack(Items.CHEST);
            for (int i = 0; i < entries.size() && i < 6; i++) {
                PlayerShopStorageEntry entry = entries.get(i);
                int rowY = y + i * 22;
                ShopUiUtil.renderNocturnePanel(g, x, rowY, w, 20);
                g.renderItem(chest, x + 2, rowY + 2);
                ResourceLocation rl = ResourceLocation.tryParse(entry.blockId());
                net.minecraft.world.level.block.Block block = rl != null ? ForgeRegistries.BLOCKS.getValue(rl) : null;
                Component blockName = block != null ? block.getName()
                        : Component.translatable("gui.futureshops.player_shop_block.storage.unknown_block");
                // Clamp both lines so they stop before the unlink ✕ button at x + w - 20.
                g.drawString(this.font, this.font.plainSubstrByWidth(blockName.getString(), Math.max(10, w - 46)),
                        x + 22, rowY + 2, ShopColors.TEXT_STRONG, false);
                g.drawString(this.font,
                        this.font.plainSubstrByWidth(Component.translatable(
                                "gui.futureshops.player_shop_block.storage.item_count", entry.itemCount()).getString(),
                                Math.max(10, w - 46)),
                        x + 22, rowY + 11, ShopColors.TEXT_MUTED, false);
                final int idx = i;
                btn(g, x + w - 20, rowY + 3, 16, 14, Component.literal("✕"),
                        ShopUiUtil.ButtonStyle.DANGER, true, null, null,
                        Component.translatable("gui.futureshops.player_shop_block.storage.unlink_tooltip"),
                        () -> {
                            List<PlayerShopStorageEntry> cur = PlayerShopClientState.linkedStorages();
                            if (idx < cur.size()) {
                                ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopUnlinkStoragePacket(
                                        PlayerShopClientState.shopPos(), cur.get(idx).pos()));
                            }
                        });
            }
            y += entries.size() * 22;
        }
        y += 4;
        btn(g, x, y, Math.min(w, 260), 18,
                Component.translatable("gui.futureshops.player_shop_block.storage.link_looking"),
                ShopUiUtil.ButtonStyle.PRIMARY, true, "＋", null,
                Component.translatable("gui.futureshops.player_shop_block.footer.link_tooltip"),
                () -> sendAction("LINK_LOOKING", 0));
        y += 26;

        // Barter storage Same / Separate.
        sectionHeader(g, x, y, "gui.futureshops.player_shop_block.storage.barter_header"); y += 12;
        String[] bsLabels = {
                Component.translatable("gui.futureshops.player_shop_block.storage.same").getString(),
                Component.translatable("gui.futureshops.player_shop_block.storage.separate").getString()
        };
        int[] bsEdges = ShopUiUtil.renderSegmented(g, this.font, x, y, 16, bsLabels, configBarterSame ? 0 : 1);
        ShopUiUtil.zone(clickZones, bsEdges[0], y, bsEdges[1] - bsEdges[0], 16, true, () -> { configBarterSame = true; saveConfig(); });
        ShopUiUtil.zone(clickZones, bsEdges[1], y, bsEdges[2] - bsEdges[1], 16, true, () -> { configBarterSame = false; saveConfig(); });
        if (isHover(x, y, bsEdges[2] - x, 16)) hoveredTooltip = Component.translatable(configBarterSame
                ? "gui.futureshops.player_shop_block.tip.storage_same"
                : "gui.futureshops.player_shop_block.tip.storage_separate");
        y += 20;
        g.drawString(this.font, Component.translatable(configBarterSame
                        ? "gui.futureshops.player_shop_block.storage.barter_note_same"
                        : "gui.futureshops.player_shop_block.storage.barter_note_separate"),
                x, y, ShopColors.TEXT_FAINT, false);
        y += 14;
        if (!configBarterSame) {
            btn(g, x, y, 140, 16, Component.translatable("gui.futureshops.player_shop_block.footer.blink"),
                    ShopUiUtil.ButtonStyle.SECONDARY, true, "＋", null,
                    Component.translatable("gui.futureshops.player_shop_block.footer.blink_tooltip"),
                    () -> sendAction("LINK_BARTER_LOOKING", 0));
            btn(g, x + 148, y, 140, 16, Component.translatable("gui.futureshops.player_shop_block.footer.bulink"),
                    ShopUiUtil.ButtonStyle.DANGER, true, "✕", null,
                    Component.translatable("gui.futureshops.player_shop_block.footer.bulink_tooltip"),
                    () -> sendAction("UNLINK_BARTER", 0));
        }
    }

    // ══════════════════════════ PAYOUTS tab ══════════════════════════

    private void renderPayoutsTab(GuiGraphics g) {
        int cx = guiLeft + 12;
        int panelW = guiW - 24;
        ShopUiUtil.renderNocturnePanelAccentTop(g, cx, contentStartY, panelW, contentAreaH);
        int x = cx + 10;
        int w = panelW - 20;
        int y = contentStartY + 10;

        // Pending settlement card + Collect.
        int cardH = 40;
        ShopUiUtil.renderNocturnePanelAccentTop(g, x, y, w, cardH);
        g.drawString(this.font, Component.translatable("gui.futureshops.player_shop_block.payouts.pending_title"),
                x + 8, y + 6, ShopColors.NEUTRAL_500, false);
        // Collect button rect first, so the pending stat can be clipped to end before it.
        int collectW = Math.max(110, this.font.width(Component.translatable("gui.futureshops.player_shop_block.payouts.collect").getString()) + 16);
        int collectX = x + w - collectW - 8;
        int pendingStatX = x + 8;
        String pendingUnit = Component.translatable("gui.futureshops.player_shop_block.payouts.pending").getString();
        int pendingNumMaxW = Math.max(10, collectX - pendingStatX - 6 - this.font.width(pendingUnit) - 4);
        ShopUiUtil.renderStatBlock(g, this.font, pendingStatX, y + 18,
                this.font.plainSubstrByWidth(
                        ShopUiUtil.formatMinorUnits(PlayerShopClientState.pendingSettlementMinor()), pendingNumMaxW),
                pendingUnit);
        btn(g, collectX, y + 12, collectW, 18,
                Component.translatable("gui.futureshops.player_shop_block.payouts.collect"),
                ShopUiUtil.ButtonStyle.PRIMARY, true, null, null,
                Component.translatable("gui.futureshops.player_shop_block.footer.collect_tooltip"),
                this::claimSettlement);
        y += cardH + 6;

        // Lifetime revenue card + History.
        ShopUiUtil.renderNocturnePanel(g, x, y, w, 30);
        g.drawString(this.font, Component.translatable("gui.futureshops.player_shop_block.payouts.lifetime_title"),
                x + 8, y + 5, ShopColors.NEUTRAL_500, false);
        ShopUiUtil.renderStatBlock(g, this.font, x + 8, y + 16,
                ShopUiUtil.formatMinorUnits(PlayerShopClientState.lifetimeRevenueMinor()),
                Component.translatable("gui.futureshops.player_shop_block.payouts.lifetime").getString());
        int histW = Math.max(90, this.font.width(Component.translatable("gui.futureshops.player_shop_block.footer.hist").getString()) + 16);
        btn(g, x + w - histW - 8, y + 6, histW, 16, Component.translatable("gui.futureshops.player_shop_block.footer.hist"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, null, null,
                Component.translatable("gui.futureshops.player_shop_block.footer.hist_tooltip"),
                () -> { if (this.minecraft != null) this.minecraft.setScreen(new SettlementHistoryScreen(this)); });
        y += 36;

        // Copy / paste config.
        int copyW = Math.max(80, this.font.width(Component.translatable("gui.futureshops.player_shop_block.footer.copy_config").getString()) + 16);
        btn(g, x, y, copyW, 16, Component.translatable("gui.futureshops.player_shop_block.footer.copy_config"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, null, null,
                Component.translatable("gui.futureshops.player_shop_block.footer.copy_config_tooltip"),
                () -> sendAction("COPY_CONFIG", 0));
        int pasteW = Math.max(80, this.font.width(Component.translatable("gui.futureshops.player_shop_block.footer.paste_config").getString()) + 16);
        btn(g, x + copyW + 8, y, pasteW, 16, Component.translatable("gui.futureshops.player_shop_block.footer.paste_config"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, null, null,
                Component.translatable("gui.futureshops.player_shop_block.footer.paste_config_tooltip"),
                () -> sendAction("PASTE_CONFIG", 0));
        y += 22;

        // Saved configurations.
        sectionHeader(g, x, y, "gui.futureshops.player_shop_block.payouts.saved_title"); y += 12;
        int cfgW = Math.min(140, w / 2);
        if (configNameBox != null) {
            configNameBox.setPosition(x, y);
            configNameBox.setWidth(cfgW);
            configNameBox.setHeight(14);
            configNameBox.visible = true;
        }
        int saveCurW = Math.max(90, this.font.width(Component.translatable("gui.futureshops.player_shop_block.payouts.save_current").getString()) + 16);
        btn(g, x + cfgW + 8, y, saveCurW, 14,
                Component.translatable("gui.futureshops.player_shop_block.payouts.save_current"),
                ShopUiUtil.ButtonStyle.PRIMARY, true, null, null,
                Component.translatable("gui.futureshops.player_shop_block.savedcfg.save_tooltip"),
                () -> sendSavedConfig("SAVE"));
        if (isHover(x, y, cfgW, 14)) hoveredTooltip = Component.translatable("gui.futureshops.player_shop_block.tip.saved_config");
        y += 20;

        List<String> saved = PlayerShopClientState.savedConfigNames();
        if (saved.isEmpty()) {
            g.drawString(this.font, Component.translatable("gui.futureshops.player_shop_block.savedcfg.empty"),
                    x, y, ShopColors.TEXT_MUTED, false);
        } else {
            // Clamp the visible rows to what actually fits inside the panel: with 2+ saved
            // configs the fixed 18px step would otherwise spill rows below the panel bottom,
            // over the status line and off-screen. Rows can never leave the panel now.
            int listStartY = y;
            int panelBottom = contentStartY + contentAreaH;
            int maxRows = Math.max(1, (panelBottom - listStartY) / 18);
            int visibleRows = Math.min(saved.size(), maxRows);
            for (int i = 0; i < visibleRows; i++) {
                final String name = saved.get(i);
                int rowY = listStartY + i * 18;
                ShopUiUtil.renderNocturnePanel(g, x, rowY, w, 16);
                g.drawString(this.font, this.font.plainSubstrByWidth(name, Math.max(10, w - 110)), x + 6, rowY + 4, ShopColors.TEXT_STRONG, false);
                btn(g, x + w - 96, rowY + 1, 44, 14, Component.translatable("gui.futureshops.player_shop_block.savedcfg.apply"),
                        ShopUiUtil.ButtonStyle.SECONDARY, true, null, null,
                        Component.translatable("gui.futureshops.player_shop_block.savedcfg.apply_tooltip"),
                        () -> sendSavedConfigNamed("APPLY", name));
                btn(g, x + w - 48, rowY + 1, 44, 14, Component.translatable("gui.futureshops.player_shop_block.savedcfg.delete"),
                        ShopUiUtil.ButtonStyle.DANGER, true, null, null,
                        Component.translatable("gui.futureshops.player_shop_block.savedcfg.delete_tooltip"),
                        () -> sendSavedConfigNamed("DELETE", name));
            }
        }
    }

    // ══════════════════════════ Visitor action bar ══════════════════════════

    private void renderVisitorActionBar(GuiGraphics g) {
        int y = guiTop + guiH - 18;
        int h = 14;
        int gap = 4;
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        boolean hasSel = listing != null;
        boolean inStock = hasSel && (listing.stock() > 0 || PlayerShopClientState.adminShopMode());
        boolean hasMoney = hasSel && !"BARTER".equalsIgnoreCase(listing.tradeMode());
        boolean hasBarter = hasSel && !"MONEY".equalsIgnoreCase(listing.tradeMode());
        String dir = (!hasSel || listing.direction() == null) ? "SELL" : listing.direction().toUpperCase(Locale.ROOT);
        boolean allowsBuy = !"BUY".equals(dir);
        boolean allowsSell = "BUY".equals(dir) || "BOTH".equals(dir);
        boolean canSell = hasSel && allowsSell && listing.buybackPriceMinor() > 0
                && (listing.buybackCap() == 0 || listing.buybackRemaining() > 0);

        int right = guiLeft + guiW - 8;

        int cartCount = PlayerShopCartState.size();
        Component cartLabel = cartCount > 0
                ? Component.translatable("gui.futureshops.player_shop_block.visitor.cart_count", cartCount)
                : Component.translatable("gui.futureshops.player_shop_block.visitor.cart_empty");
        int cartW = Math.max(40, this.font.width(cartLabel.getString()) + 12);
        btn(g, right - cartW, y, cartW, h, cartLabel, ShopUiUtil.ButtonStyle.SECONDARY, true, null, null, null,
                () -> { if (this.minecraft != null) this.minecraft.setScreen(new PlayerShopCartScreen(this)); });
        right -= cartW + gap;

        if (allowsBuy) {
            int addW = Math.max(42, this.font.width(Component.translatable("gui.futureshops.player_shop_block.visitor.add_cart").getString()) + 8);
            btn(g, right - addW, y, addW, h, Component.translatable("gui.futureshops.player_shop_block.visitor.add_cart"),
                    ShopUiUtil.ButtonStyle.SECONDARY, hasSel && inStock, null, null, null, this::addToCart);
            right -= addW + gap;
        }
        if (hasBarter && allowsBuy) {
            int barW = Math.max(50, this.font.width(Component.translatable("gui.futureshops.player_shop_block.visitor.barter_btn").getString()) + 10);
            btn(g, right - barW, y, barW, h, Component.translatable("gui.futureshops.player_shop_block.visitor.barter_btn"),
                    ShopUiUtil.ButtonStyle.SECONDARY, inStock, null, null, null,
                    () -> { if (this.minecraft != null) this.minecraft.setScreen(new PlayerShopBarterScreen(this, getQuantity())); });
            right -= barW + gap;
        }
        if (canSell) {
            int sellW = Math.max(54, this.font.width(Component.translatable("gui.futureshops.player_shop_block.visitor.sell_button").getString()) + 10);
            btn(g, right - sellW, y, sellW, h, Component.translatable("gui.futureshops.player_shop_block.visitor.sell_button"),
                    ShopUiUtil.ButtonStyle.SECONDARY, true, null, null, null,
                    () -> { if (this.minecraft != null) this.minecraft.setScreen(new PlayerShopSellScreen(this, getQuantity())); });
            right -= sellW + gap;
        }
        if (hasMoney && allowsBuy) {
            int buyW = Math.max(50, this.font.width(Component.translatable("gui.futureshops.player_shop_block.visitor.buy_btn").getString()) + 10);
            btn(g, right - buyW, y, buyW, h, Component.translatable("gui.futureshops.player_shop_block.visitor.buy_btn"),
                    ShopUiUtil.ButtonStyle.PRIMARY, hasSel && inStock, null, null, null,
                    () -> showBuyConfirmation(getQuantity()));
            right -= buyW + gap + 4;
        }

        // Quantity: − [box] + Max.
        int qtyX = Math.max(guiLeft + 8, right - 96);
        btn(g, qtyX, y, 14, h, Component.literal("-"), ShopUiUtil.ButtonStyle.SECONDARY, true, null, null, null,
                () -> setQuantity(getQuantity() - 1));
        if (quantityBox != null) {
            quantityBox.setPosition(qtyX + 16, y);
            quantityBox.setWidth(32);
            quantityBox.setHeight(h);
            quantityBox.visible = true;
        }
        btn(g, qtyX + 50, y, 14, h, Component.literal("+"), ShopUiUtil.ButtonStyle.SECONDARY, true, null, null,
                Component.translatable("gui.futureshops.player_shop_block.visitor.shift_max"),
                () -> { if (hasShiftDown()) setQuantity(resolveMaxQuantity()); else setQuantity(getQuantity() + 1); });
        btn(g, qtyX + 66, y, 28, h, Component.translatable("gui.futureshops.player_shop_block.visitor.max"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, null, null, null, () -> setQuantity(resolveMaxQuantity()));
    }

    private void renderListingRail(GuiGraphics graphics, int mouseX, int mouseY) {
        int railX = guiLeft + 8;
        int railY = contentStartY;
        int railW = listingRailW;
        int railH = listingRailHeight();
        ShopUiUtil.renderCard(graphics, railX, railY, railW, railH);
        graphics.fill(railX, railY, railX + railW, railY + 2, ShopColors.ACCENT_PRIMARY);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.player_shop_block.rail.title"), railX + 8, railY + 6, ShopColors.TEXT_STRONG, false);

        List<PlayerShopListingData> listings = PlayerShopClientState.listings();
        String countText = listings.isEmpty()
                ? I18n.get("gui.futureshops.player_shop_block.rail.count_empty")
                : I18n.get("gui.futureshops.player_shop_block.rail.count", listings.size());
        graphics.drawString(this.font, countText, railX + 8, railY + 18, ShopColors.TEXT_SECONDARY, false);

        int cardY = railY + 32;
        // Adaptive card height: compact at small rail heights
        int cardH = railH < 160 ? 32 : (railH < 200 ? 38 : 44);
        int maxVisible = Math.max(1, (railH - 40) / cardH);
        listingScroll = Math.max(0, Math.min(listingScroll, Math.max(0, listings.size() - maxVisible)));
        for (int i = 0; i < maxVisible && i + listingScroll < listings.size(); i++) {
            int listingIndex = i + listingScroll;
            PlayerShopListingData listing = listings.get(listingIndex);
            int y = cardY + i * cardH;
            boolean selected = listingIndex == PlayerShopClientState.selectedListingIndex();
            int cardBg = selected ? ShopColors.SURFACE_PRESSED : ShopColors.SURFACE_RAISED;
            int cardBorder = selected ? ShopColors.BORDER_GLOW : ShopColors.BORDER_MUTED;
            ShopUiUtil.renderPanel(graphics, railX + 6, y, railW - 12, cardH - 4, cardBg, cardBorder);
            if (selected) {
                graphics.fill(railX + 6, y, railX + 9, y + cardH - 4, ShopColors.ACCENT_PRIMARY);
            }

            // Item icon — always apply the wire NBT for display: nbtAware only
            // governs transaction matching, and BEWLR items (TacZ guns etc.)
            // render as a missing texture without their tag.
            ShopUiUtil.renderItemIconWithNbt(graphics, this.font, listing.itemId(), listing.nbtJson(), railX + 10, y + (cardH - 20) / 2);

            // Item 6: detect icon hover for tooltip
            int iconY = y + (cardH - 20) / 2;
            if (mouseX >= railX + 10 && mouseX <= railX + 26 && mouseY >= iconY && mouseY <= iconY + 16) {
                hoveredItemId = listing.itemId();
                hoveredNbtJson = listing.nbtJson();
                hoveredMouseX = mouseX;
                hoveredMouseY = mouseY;
            }

            // Name — scrolls (ping-pong) when wider than the rail so long modded names stay readable.
            // Reserve ~46px on the right for the promo badge/chip so the name can't slide under it.
            int nameW = listing.promo().configured() ? railW - 42 - 46 : railW - 42;
            String dirPrefix = "";
            if (listing.direction() != null) {
                String dirU = listing.direction().toUpperCase(Locale.ROOT);
                if ("BUY".equals(dirU)) dirPrefix = Component.translatable("gui.futureshops.player_shop_block.rail.dir_buy").getString();
                else if ("BOTH".equals(dirU)) dirPrefix = Component.translatable("gui.futureshops.player_shop_block.rail.dir_both").getString();
            }
            String fullName = dirPrefix + ShopUiUtil.getItemDisplayNameWithNbtAndQty(listing.itemId(), listing.nbtJson(), listing.baseQuantity());
            ShopUiUtil.renderScrollingString(graphics, this.font, fullName,
                    railX + 30, y + 4, nameW, selected ? ShopColors.TEXT_STRONG : ShopColors.TEXT_MUTED);

            // Meta line — stock + mode
            boolean admin = PlayerShopClientState.adminShopMode();
            String stockStr = admin ? "∞" : Component.translatable("gui.futureshops.player_shop_block.rail.stock_short", listing.stock()).getString();
            String meta;
            if (!admin && listing.baseQuantity() == 0) {
                meta = "§c⚠ " + stockStr + " • " + prettyMode(listing.tradeMode());
            } else if (listing.baseQuantity() > 1 && !PlayerShopClientState.owner()) {
                // Show ×qty badge only for visitors; owners have Q-/Q+ controls
                meta = "×" + listing.baseQuantity() + " • " + stockStr + " • " + prettyMode(listing.tradeMode());
            } else {
                meta = stockStr + " • " + prettyMode(listing.tradeMode());
            }
            meta = this.font.plainSubstrByWidth(meta, nameW);
            int metaColor = switch (listing.tradeMode().toUpperCase(Locale.ROOT)) {
                case "BARTER" -> ShopColors.TEXT_BARTER;
                case "MONEY_AND_BARTER" -> ShopColors.ACCENT_CURRENCY;
                default -> ShopColors.TEXT_PRICE;
            };
            graphics.drawString(this.font, meta, railX + 30, y + 16, metaColor, false);

            // Badges — only show if card is tall enough
            if (cardH >= 38) {
                int badgeX = railX + 30;
                if (listing.department() != null && !listing.department().isBlank()) {
                    String deptLabel = this.font.plainSubstrByWidth(listing.department(), 50);
                    ShopUiUtil.drawChip(graphics, this.font, badgeX, y + cardH - 16, deptLabel,
                            ShopColors.BG_PANEL, ShopColors.ACCENT_PURPLE, ShopColors.ACCENT_PURPLE);
                    badgeX += this.font.width(deptLabel) + 12;
                }
                if (listing.nbtAware()) {
                    String nbtBadge = I18n.get("gui.futureshops.player_shop_block.rail.badge_nbt");
                    ShopUiUtil.drawChip(graphics, this.font, badgeX, y + cardH - 16, nbtBadge,
                            ShopColors.BG_PANEL, ShopColors.ACCENT_ORANGE, ShopColors.ACCENT_ORANGE);
                    badgeX += this.font.width(nbtBadge) + 12;
                }
                if (PlayerShopClientState.owner() && PlayerShopClientState.singleItemMode() && listing.visible()) {
                    ShopUiUtil.drawChip(graphics, this.font, badgeX, y + cardH - 16,
                            I18n.get("gui.futureshops.player_shop_block.rail.badge_visible"),
                            ShopColors.BG_PANEL, ShopColors.ACCENT_CYAN, ShopColors.ACCENT_CYAN);
                }
            }

            // Promo badge at top-right of card
            if (listing.promo().configured()) {
                int percent = computeListingPromoPercent(listing);
                if (percent > 0) {
                    String badgeText = percent >= 100 ? Component.translatable("gui.futureshops.player_shop_block.detail.promo_free").getString() : "-" + percent + "%";
                    ShopUiUtil.renderAnimatedDiscountBadge(graphics, this.font,
                            railX + railW - 18, y + 8, badgeText);
                } else {
                    ShopUiUtil.drawChip(graphics, this.font, railX + railW - 52, y + 4, promoLabel(listing),
                            ShopColors.DISCOUNT_BG, ShopColors.DISCOUNT_BG, ShopColors.DISCOUNT_TEXT);
                }
            }
        }

        // Scroll indicators
        ShopUiUtil.renderScrollIndicators(graphics, this.font, railX, railY + 30, railW, railH - 32, listingScroll, maxVisible, listings.size());
    }

    private void renderDetailPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int detailX = guiLeft + listingRailW + 16;
        int detailY = contentStartY;
        int detailW = guiW - listingRailW - 24;
        int detailH = contentAreaH;
        ShopUiUtil.renderCard(graphics, detailX, detailY, detailW, detailH);
        graphics.fill(detailX, detailY, detailX + detailW, detailY + 2, ShopColors.ACCENT_PRIMARY);

        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        if (listing == null) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.player_shop_block.detail.select_listing"), detailX + detailW / 2, detailY + detailH / 2, ShopColors.TEXT_FAINT);
            return;
        }

        // ═══ Adaptive layout: preview on left, info on right ═══
        boolean narrowDetail = detailW < 240;
        int previewW = narrowDetail ? Math.min(80, detailW - 20) : Math.min(130, detailW / 2);

        // Item preview — always apply the wire NBT for display (nbtAware only
        // governs transaction matching; TacZ guns etc. need the tag to render)
        ShopUiUtil.renderLargeItemPreviewWithNbt(graphics, this.font, listing.itemId(), listing.nbtJson(), detailX + 6, detailY + 6, previewW);

        // Hover detection for tooltip on preview
        if (mouseX >= detailX + 6 && mouseX <= detailX + 6 + previewW && mouseY >= detailY + 6 && mouseY <= detailY + 76) {
            hoveredItemId = listing.itemId();
            hoveredNbtJson = listing.nbtJson();
        }

        // Item name below preview — scrolls on overflow so long names stay fully visible.
        int nameY = detailY + (compact ? 62 : 82);
        String detailName = ShopUiUtil.getItemDisplayNameWithNbtAndQty(listing.itemId(), listing.nbtJson(), listing.baseQuantity());
        ShopUiUtil.renderScrollingString(graphics, this.font, detailName,
                detailX + 8, nameY, previewW, ShopColors.TEXT_PRIMARY);

        // Description below name (gray) — per-listing only; no fallback to shop description.
        // Clamps to 3 lines; overflow is marked with "…" and the full text surfaces as a
        // hover tooltip so buyers can read long listing notes without layout overflow.
        String listingDesc = listing.listingDescription();
        int descTopY = nameY + 12;
        int descLinesDrawn = 0;
        boolean descTruncated = false;
        if (listingDesc != null && !listingDesc.isBlank()) {
            int[] descResult = ShopUiUtil.drawWrappedClamped(graphics, this.font,
                    Component.literal("§7§o" + listingDesc),
                    detailX + 8, descTopY, previewW, 3, ShopColors.TEXT_SECONDARY, 10);
            descLinesDrawn = descResult[0];
            descTruncated = descResult[1] == 1;
            int descH = Math.max(10, descLinesDrawn * 10);
            if (descTruncated
                    && mouseX >= detailX + 8 && mouseX <= detailX + 8 + previewW
                    && mouseY >= descTopY && mouseY <= descTopY + descH) {
                hoveredDescriptionFull = listingDesc;
                hoveredDescriptionMouseX = mouseX;
                hoveredDescriptionMouseY = mouseY;
            }
        }
        int afterDescY = descTopY + Math.max(12, descLinesDrawn * 10 + 2);

        // Stock below description (∞ in admin mode)
        boolean adminDetail = PlayerShopClientState.adminShopMode();
        String stockStr = adminDetail
                ? Component.translatable("gui.futureshops.player_shop_block.detail.stock_unlimited").getString()
                : I18n.get("gui.futureshops.player_shop_block.detail.stock_prefix", listing.stock())
                        + (listing.stock() <= 16
                                ? I18n.get("gui.futureshops.player_shop_block.detail.stock_low")
                                : I18n.get("gui.futureshops.player_shop_block.detail.stock_ok"));
        graphics.drawString(this.font, this.font.plainSubstrByWidth(stockStr, previewW), detailX + 8, afterDescY,
                adminDetail ? ShopColors.SUCCESS : (listing.stock() <= 16 ? ShopColors.ERROR : ShopColors.SUCCESS), false);

        // Mode + effective price below stock
        String modeStr = prettyMode(listing.tradeMode());
        int modeColor = switch (listing.tradeMode().toUpperCase(Locale.ROOT)) {
            case "BARTER" -> ShopColors.TEXT_BARTER;
            case "MONEY_AND_BARTER" -> ShopColors.ACCENT_CURRENCY;
            case "BOTH" -> ShopColors.TEXT_PRIMARY;
            default -> ShopColors.TEXT_PRICE;
        };
        graphics.drawString(this.font, modeStr, detailX + 8, afterDescY + 12, modeColor, false);

        // Promo indicator — the details panel used to paint an animated discount badge
        // here, but the pricing section already carries the "(-X%)" inline suffix on the
        // "Now:" line, so a second badge was redundant.  For non-percent promos (flat
        // label / scheduled) we still surface a tiny static chip so the buyer knows a
        // promo is active.
        if (listing.promo().configured()) {
            int percent = computeListingPromoPercent(listing);
            if (percent <= 0) {
                ShopUiUtil.drawChip(graphics, this.font, detailX + 8, afterDescY + 26, promoLabel(listing),
                        ShopColors.DISCOUNT_BG, ShopColors.DISCOUNT_BG, ShopColors.DISCOUNT_TEXT);
            }
        }

        // ═══ Right info panels ═══
            // ── Visitor view: pricing + trade summary panels ──
            int infoX = detailX + Math.max(previewW + 10, narrowDetail ? 10 : previewW + 10);
            int infoW = detailW - (infoX - detailX) - 6;
            if (infoW < 50) {
                infoX = detailX + 8;
                infoW = detailW - 16;
            }
            int panelH = compact ? 48 : 60;

            int pricePanelY = detailY + 8;
            if (infoX > detailX + previewW) {
                ShopUiUtil.renderPanel(graphics, infoX, pricePanelY, infoW, panelH, ShopColors.SURFACE_RAISED, ShopColors.BORDER_SUBTLE);
                graphics.fill(infoX, pricePanelY, infoX + infoW, pricePanelY + 1, ShopColors.ACCENT_CURRENCY);
                graphics.drawString(this.font, I18n.get("gui.futureshops.player_shop_block.detail.visitor.pricing"), infoX + 4, pricePanelY + 4, ShopColors.TEXT_FAINT, false);

                boolean hasMoney = !"BARTER".equalsIgnoreCase(listing.tradeMode());
                boolean hasBarter = !"MONEY".equalsIgnoreCase(listing.tradeMode());
                int py = pricePanelY + 16;

                if (hasMoney) {
                    graphics.drawString(this.font, this.font.plainSubstrByWidth(
                                    I18n.get("gui.futureshops.player_shop_block.detail.visitor.base", ShopUiUtil.formatMinorUnits(listing.moneyPriceMinor())),
                                    infoW - 8),
                            infoX + 4, py, ShopColors.TEXT_SECONDARY, false);
                    py += 11;
                    String nowLabel = listing.effectiveUnitPriceMinor() <= 0
                            ? I18n.get("gui.futureshops.player_shop_block.detail.visitor.now_free")
                            : I18n.get("gui.futureshops.player_shop_block.detail.visitor.now", ShopUiUtil.formatMinorUnits(listing.effectiveUnitPriceMinor()));
                    // Inline discount suffix — replaces the redundant animated badge previously
                    // painted over this panel.  "Now: 90.00 §7(-10%)" reads as a single price line
                    // with secondary-muted discount info.
                    int nowPct = ShopUiUtil.computePromoPercent(listing.moneyPriceMinor(), listing.effectiveUnitPriceMinor());
                    if (nowPct > 0 && listing.effectiveUnitPriceMinor() > 0) {
                        nowLabel = nowLabel + " §7(-" + nowPct + "%)";
                    }
                    graphics.drawString(this.font, this.font.plainSubstrByWidth(nowLabel, infoW - 8),
                            infoX + 4, py, ShopColors.TEXT_PRICE, false);
                    py += 11;
                }
                if (hasBarter) {
                    String barter = listing.barterItemCount() + "× " + ShopUiUtil.getItemDisplayNameWithNbt(listing.barterItemId(), listing.barterNbtJson());
                    ShopUiUtil.renderScrollingString(graphics, this.font, barter,
                            infoX + 4, py, infoW - 8, ShopColors.TEXT_BARTER);
                    py += 11;
                    if (listing.baseBarterItemCount() > listing.barterItemCount()) {
                        String baseBarter = "§7§m" + listing.baseBarterItemCount() + "×";
                        graphics.drawString(this.font, baseBarter, infoX + 4, py, ShopColors.TEXT_SECONDARY, false);
                        py += 11;
                    }
                    graphics.drawString(this.font, this.font.plainSubstrByWidth(
                                    I18n.get("gui.futureshops.player_shop_block.detail.visitor.owned",
                                            ShopUiUtil.countPlayerInventoryNbt(listing.barterItemId(), listing.barterNbtJson(), listing.barterNbtAware())),
                                    infoW - 8),
                            infoX + 4, py, ShopColors.TEXT_SECONDARY, false);
                    py += 13;

                    // Barter item icon preview — only render if it fits within the pricing panel with margin
                    if (py + 18 <= pricePanelY + panelH - 4) {
                        ShopUiUtil.renderItemIconWithNbt(graphics, this.font, listing.barterItemId(), listing.barterNbtJson(), infoX + 4, py);
                        String barterName = "§9" + ShopUiUtil.getItemDisplayNameWithNbt(listing.barterItemId(), listing.barterNbtJson());
                        ShopUiUtil.renderScrollingString(graphics, this.font, barterName,
                                infoX + 24, py + 4, infoW - 28, ShopColors.TEXT_BARTER);
                        // Hover detection for barter item icon
                        if (mouseX >= infoX + 4 && mouseX <= infoX + 20 && mouseY >= py && mouseY <= py + 16) {
                            hoveredItemId = listing.barterItemId();
                            hoveredNbtJson = listing.barterNbtJson();
                        }
                    }
                }

                // Trade summary panel
                int summaryY = pricePanelY + panelH + 4;
                int summaryH = Math.min(panelH, detailH - panelH - 12);
                if (summaryH > 20) {
                    ShopUiUtil.renderPanel(graphics, infoX, summaryY, infoW, summaryH, ShopColors.SURFACE_RAISED, ShopColors.BORDER_SUBTLE);
                    graphics.fill(infoX, summaryY, infoX + infoW, summaryY + 1, ShopColors.ACCENT_PRIMARY);
                    graphics.drawString(this.font, I18n.get("gui.futureshops.player_shop_block.detail.visitor.trade"), infoX + 4, summaryY + 4, ShopColors.TEXT_FAINT, false);

                    if ("MONEY_AND_BARTER".equalsIgnoreCase(listing.tradeMode())) {
                        ShopUiUtil.drawWrappedString(graphics, this.font,
                                Component.translatable("gui.futureshops.player_shop_block.detail.visitor.trade.compound"),
                                infoX + 4, summaryY + 16, infoW - 8, ShopColors.TEXT_PRIMARY, 10);
                    } else if ("BOTH".equalsIgnoreCase(listing.tradeMode())) {
                        ShopUiUtil.drawWrappedString(graphics, this.font,
                                Component.translatable("gui.futureshops.player_shop_block.detail.visitor.trade.both"),
                                infoX + 4, summaryY + 16, infoW - 8, ShopColors.TEXT_PRIMARY, 10);
                    } else if ("MONEY".equalsIgnoreCase(listing.tradeMode())) {
                        graphics.drawString(this.font, I18n.get("gui.futureshops.player_shop_block.detail.visitor.trade.money"),
                                infoX + 4, summaryY + 16, ShopColors.TEXT_PRIMARY, false);
                    } else {
                        String barterItemName = ShopUiUtil.getItemDisplayNameWithNbt(listing.barterItemId(), listing.barterNbtJson());
                        String saleItemName = ShopUiUtil.getItemDisplayNameWithNbtAndQty(listing.itemId(), listing.nbtJson(), listing.baseQuantity());
                        String summary = this.font.plainSubstrByWidth(
                                I18n.get("gui.futureshops.player_shop_block.detail.visitor.trade.barter_summary",
                                        listing.barterItemCount(), barterItemName, saleItemName),
                                infoW - 8);
                        graphics.drawString(this.font, summary, infoX + 4, summaryY + 16, ShopColors.TEXT_BARTER, false);
                    }
                    // Trade summary panel intentionally no longer shows the listing description —
                    // buyers already see it under the item name. Fall back to promo status only.
                    String promoStatus = listing.promo().configured()
                            ? I18n.get("gui.futureshops.player_shop_block.detail.visitor.promo_active")
                            : I18n.get("gui.futureshops.player_shop_block.detail.visitor.promo_none");
                    if (summaryH > 34) {
                        graphics.drawString(this.font, promoStatus, infoX + 4, summaryY + summaryH - 12, ShopColors.TEXT_SECONDARY, false);
                    }
                }
            }

            // Visitor: total cost at bottom of detail
            if (!"BARTER".equalsIgnoreCase(listing.tradeMode())) {
                long total = listing.effectiveUnitPriceMinor() * getQuantity();
                String totalStr = total <= 0
                        ? I18n.get("gui.futureshops.player_shop_block.detail.visitor.total_free")
                        : I18n.get("gui.futureshops.player_shop_block.detail.visitor.total", ShopUiUtil.formatMinorUnits(total));
                if (listing.baseQuantity() > 1) {
                    totalStr += I18n.get("gui.futureshops.player_shop_block.detail.visitor.per_base", listing.baseQuantity());
                }
                graphics.drawString(this.font, totalStr, detailX + 8, detailY + detailH - 18, ShopColors.TEXT_PRICE, false);
            }
            if (listing.department() != null && !listing.department().isBlank()) {
                ShopUiUtil.drawChip(graphics, this.font, detailX + 8, detailY + detailH - 6,
                        listing.department(), ShopColors.BG_PANEL, ShopColors.ACCENT_PURPLE, ShopColors.ACCENT_PURPLE);
            }
    }

    /**
     * Single-item visitor detail — full-width layout when shop has only 1 listing.
     */
    private void renderSingleItemDetail(GuiGraphics graphics, int mouseX, int mouseY) {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        if (listing == null) return;

        int detailX = guiLeft + 8;
        int detailY = contentStartY;
        int detailW = guiW - 16;
        int detailH = contentAreaH;
        int previewW = Math.min(140, detailW / 2 - 10);

        ShopUiUtil.renderCard(graphics, detailX, detailY, detailW, detailH);
        graphics.fill(detailX, detailY, detailX + detailW, detailY + 2, ShopColors.ACCENT_PRIMARY);

        // ═══ Left: Preview panel ═══
        ShopUiUtil.renderCard(graphics, detailX + 8, detailY + 8, previewW, detailH - 16);
        graphics.fill(detailX + 8, detailY + 8, detailX + 8 + previewW, detailY + 10, ShopColors.ACCENT_PRIMARY);
        if (!listing.nbtJson().isBlank()) {
            ShopUiUtil.renderLargeItemPreviewWithNbt(graphics, this.font, listing.itemId(), listing.nbtJson(), detailX + 10, detailY + 16, previewW - 4);
        } else {
            ShopUiUtil.renderLargeItemPreview(graphics, this.font, listing.itemId(), detailX + 10, detailY + 16, previewW - 4);
        }
        // Hover detection for tooltip
        if (mouseX >= detailX + 8 && mouseX <= detailX + 8 + previewW && mouseY >= detailY + 8 && mouseY <= detailY + 8 + detailH - 16) {
            hoveredItemId = listing.itemId();
            hoveredNbtJson = listing.nbtJson();
        }

        // Name, owned count, total, stock — stacked at bottom of preview
        int bottomStackY = detailY + detailH - (compact ? 52 : 58);
        String dispName = ShopUiUtil.getItemDisplayNameWithNbtAndQty(listing.itemId(), listing.nbtJson(), listing.baseQuantity());
        ShopUiUtil.renderScrollingCentered(graphics, this.font, dispName,
                detailX + 8 + previewW / 2, bottomStackY, previewW - 10, ShopColors.TEXT_PRIMARY);

        int owned = ShopUiUtil.countPlayerInventoryNbt(listing.itemId(), listing.nbtJson(), listing.nbtAware());
        graphics.drawCenteredString(this.font,
                I18n.get("gui.futureshops.player_shop_block.detail.single.own", owned),
                detailX + 8 + previewW / 2, bottomStackY + 12, ShopColors.TEXT_SECONDARY);

        if (!"BARTER".equalsIgnoreCase(listing.tradeMode())) {
            long total = listing.effectiveUnitPriceMinor() * getQuantity();
            String totalLabel = total <= 0
                    ? I18n.get("gui.futureshops.player_shop_block.detail.free")
                    : "§a" + ShopUiUtil.formatMinorUnits(total);
            graphics.drawCenteredString(this.font, totalLabel, detailX + 8 + previewW / 2, bottomStackY + 24, ShopColors.TEXT_PRICE);
        }

        boolean adminVisitor = PlayerShopClientState.adminShopMode();
        String stockStr = adminVisitor
                ? Component.translatable("gui.futureshops.player_shop_block.detail.stock_unlimited").getString()
                : listing.stock()
                        + (listing.stock() <= 16
                                ? I18n.get("gui.futureshops.player_shop_block.detail.stock_low")
                                : I18n.get("gui.futureshops.player_shop_block.detail.stock_ok"));
        graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(stockStr, previewW - 8),
                detailX + 8 + previewW / 2, bottomStackY + 36,
                adminVisitor ? ShopColors.SUCCESS : (listing.stock() <= 16 ? ShopColors.ERROR : ShopColors.SUCCESS));

        // ═══ Right: Info panels ═══
        int infoX = detailX + previewW + 20;
        int infoW = detailW - previewW - 28;

        // Title (scaled)
        graphics.pose().pushPose();
        graphics.pose().translate(infoX + 8, detailY + 14, 0);
        float titleScale = compact ? 1.0f : 1.2f;
        graphics.pose().scale(titleScale, titleScale, 1f);
        String titleName = ShopUiUtil.getItemDisplayNameWithNbtAndQty(listing.itemId(), listing.nbtJson(), listing.baseQuantity());
        graphics.drawString(this.font, this.font.plainSubstrByWidth(titleName, (int)(infoW / titleScale) - 8), 0, 0, ShopColors.TEXT_PRIMARY, true);
        graphics.pose().popPose();

        // Shop owner line
        String shopLabel = PlayerShopClientState.shopName().isBlank()
                ? Component.translatable("gui.futureshops.player_shop_block.header.shop_suffix", PlayerShopClientState.ownerName()).getString()
                : PlayerShopClientState.shopName();
        graphics.drawString(this.font, this.font.plainSubstrByWidth("§7" + shopLabel + " • " + PlayerShopClientState.ownerName(), infoW - 8),
                infoX + 8, detailY + 28, ShopColors.TEXT_SECONDARY, false);

        int nextY = detailY + 42;

        // Promo schedule hint — the animated discount badge was removed from this panel
        // in favour of an inline "(-X%)" suffix on the buy-price line below.  The schedule
        // line still shows when the promo is time-boxed.
        if (listing.promo().configured()) {
            String scheduleLine = formatPromoSchedule(listing.promo());
            if (scheduleLine != null) {
                graphics.drawString(this.font, scheduleLine, infoX + 8, nextY, ShopColors.TEXT_FAINT, false);
                nextY += 10;
            }
        }

        // Divider
        graphics.fill(infoX + 8, nextY, infoX + infoW - 8, nextY + 1, ShopColors.BORDER_DEFAULT);
        nextY += 6;

        // Mode
        graphics.drawString(this.font,
                I18n.get("gui.futureshops.player_shop_block.detail.single.mode", prettyMode(listing.tradeMode())),
                infoX + 8, nextY, ShopColors.TEXT_SECONDARY, false);
        nextY += 14;

        boolean hasMoney = !"BARTER".equalsIgnoreCase(listing.tradeMode());
        boolean hasBarter = !"MONEY".equalsIgnoreCase(listing.tradeMode());

        if (hasMoney) {
            String priceLabel = listing.effectiveUnitPriceMinor() <= 0
                    ? I18n.get("gui.futureshops.player_shop_block.detail.single.buy_free")
                    : I18n.get("gui.futureshops.player_shop_block.detail.single.buy",
                            ShopUiUtil.formatMinorUnits(listing.effectiveUnitPriceMinor()));
            // Inline promo discount — "Buy: 90.00 §7(-10%)" replaces the previous animated
            // badge.  The §m strikethrough base price below still shows, giving buyers both
            // the old price and the percent off at a glance.
            int singlePct = ShopUiUtil.computePromoPercent(listing.moneyPriceMinor(), listing.effectiveUnitPriceMinor());
            if (singlePct > 0 && listing.effectiveUnitPriceMinor() > 0) {
                priceLabel = priceLabel + " §7(-" + singlePct + "%)";
            }
            graphics.drawString(this.font, this.font.plainSubstrByWidth(priceLabel, infoW - 16),
                    infoX + 8, nextY, ShopColors.TEXT_PRICE, false);
            if (listing.moneyPriceMinor() != listing.effectiveUnitPriceMinor()) {
                String base = "§7§m" + ShopUiUtil.formatMinorUnits(listing.moneyPriceMinor());
                graphics.drawString(this.font, base, infoX + 8 + this.font.width(priceLabel) + 4, nextY, ShopColors.TEXT_SECONDARY, false);
            }
            nextY += 12;
        }

        if (hasBarter) {
            String barterId = listing.barterItemId();
            if (barterId != null && !barterId.isBlank()) {
                if (listing.baseBarterItemCount() > listing.barterItemCount()) {
                    String baseBarter = "§7§m" + listing.baseBarterItemCount() + " × " + ShopUiUtil.getItemDisplayNameWithNbt(barterId, listing.barterNbtJson());
                    graphics.drawString(this.font, this.font.plainSubstrByWidth(baseBarter, infoW - 16),
                            infoX + 8, nextY, ShopColors.TEXT_SECONDARY, false);
                    nextY += 12;
                }
                String barterText = listing.barterItemCount() + " × " + ShopUiUtil.getItemDisplayNameWithNbt(barterId, listing.barterNbtJson());
                graphics.drawString(this.font, this.font.plainSubstrByWidth("§9⚒ " + barterText, infoW - 16),
                        infoX + 8, nextY, ShopColors.TEXT_BARTER, false);
                nextY += 12;
                int ownedBarter = ShopUiUtil.countPlayerInventoryNbt(barterId, listing.barterNbtJson(), listing.barterNbtAware());
                graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("gui.futureshops.player_shop_block.detail.visitor.owned", ownedBarter).getString(), infoW - 16),
                        infoX + 8, nextY, ownedBarter >= listing.barterItemCount() ? ShopColors.SUCCESS : ShopColors.ERROR, false);
                nextY += 14;

                // Barter item icon preview — only if space permits (avoid overlapping stock/dept below)
                int maxInfoY = detailY + detailH - 40;
                if (nextY + 18 <= maxInfoY) {
                    ShopUiUtil.renderItemIconWithNbt(graphics, this.font, barterId, listing.barterNbtJson(), infoX + 8, nextY);
                    String barterName = this.font.plainSubstrByWidth("§9" + ShopUiUtil.getItemDisplayNameWithNbt(barterId, listing.barterNbtJson()), infoW - 32);
                    graphics.drawString(this.font, barterName, infoX + 28, nextY + 4, ShopColors.TEXT_BARTER, false);
                    // Hover detection for barter item icon
                    if (mouseX >= infoX + 8 && mouseX <= infoX + 24 && mouseY >= nextY && mouseY <= nextY + 16) {
                        hoveredItemId = barterId;
                        hoveredNbtJson = listing.barterNbtJson();
                    }
                    nextY += 20;
                }
            }
        }

        // Stock (∞ in admin mode)
        String stockLabel = PlayerShopClientState.adminShopMode()
                ? Component.translatable("gui.futureshops.player_shop_block.detail.single.stock_unlimited").getString()
                : (listing.stock() > 0
                        ? I18n.get("gui.futureshops.player_shop_block.detail.single.stock_in", listing.stock())
                        : I18n.get("gui.futureshops.player_shop_block.detail.single.stock_out"));
        graphics.drawString(this.font, stockLabel, infoX + 8, nextY, ShopColors.TEXT_SECONDARY, false);
        nextY += 14;

        // Department
        if (listing.department() != null && !listing.department().isBlank()) {
            ShopUiUtil.drawChip(graphics, this.font, infoX + 8, nextY,
                    listing.department(), ShopColors.BG_PANEL, ShopColors.ACCENT_PURPLE, ShopColors.ACCENT_PURPLE);
            nextY += 16;
        }

        // Description for single-item visitor — per-listing only, no fallback to shop desc.
        // Clamps to 3 lines with ellipsis; full text appears as a hover tooltip.
        String singleListingDesc = listing.listingDescription();
        if (singleListingDesc != null && !singleListingDesc.isBlank()) {
            nextY += 4;
            graphics.fill(infoX + 8, nextY, infoX + infoW - 8, nextY + 1, ShopColors.BORDER_DEFAULT);
            nextY += 4;
            int descW = infoW - 16;
            int[] r = ShopUiUtil.drawWrappedClamped(graphics, this.font,
                    Component.literal("§7§o" + singleListingDesc),
                    infoX + 8, nextY, descW, 3, ShopColors.TEXT_SECONDARY, 10);
            int descH = Math.max(10, r[0] * 10);
            if (r[1] == 1
                    && mouseX >= infoX + 8 && mouseX <= infoX + 8 + descW
                    && mouseY >= nextY && mouseY <= nextY + descH) {
                hoveredDescriptionFull = singleListingDesc;
                hoveredDescriptionMouseX = mouseX;
                hoveredDescriptionMouseY = mouseY;
            }
        }
    }

    private void renderStatus(GuiGraphics graphics) {
        if (!PlayerShopClientState.resultCode().isBlank()) {
            String code = PlayerShopClientState.resultCode();
            int maxW = guiW / 2 - 8;
            int statusColor;
            String text;

            if (!PlayerShopClientState.owner()) {
                String buyerMsg = buyerFriendlyMessage(code);
                if (buyerMsg == null) return; // Hide technical status from buyer
                text = buyerMsg;
                statusColor = code.equals("BOUGHT") ? ShopColors.SUCCESS : ShopColors.ERROR;
            } else {
                text = Component.translatable("gui.futureshops.player_shop.status", localizeResultCode(code)).getString();
                statusColor = ShopColors.TEXT_SECONDARY;
            }

            String clipped = this.font.plainSubstrByWidth(text, maxW);
            int textW = this.font.width(clipped);
            // Right-aligned at bottom-right, on the statusY line
            int statusX = guiLeft + guiW - textW - 10;
            graphics.drawString(this.font, clipped, statusX, statusY, statusColor, false);
        }
    }

    /**
     * Item 25: Returns a buyer-friendly message for known result codes. Returns null for
     * codes buyers shouldn't see.
     *
     * Messages are resolved from the lang file under
     * {@code gui.futureshops.player_shop.buyer.<code>} so translators can override without
     * code edits. The allow-list below defines which codes are buyer-visible; every entry
     * maps 1:1 to an en_us.json key. The BuyPacketCallSiteTest invariant forbids any
     * hard-coded English fallback for these codes elsewhere in the client tree.
     */
    private String buyerFriendlyMessage(String code) {
        String upper = code.toUpperCase(Locale.ROOT);
        boolean buyerVisible = switch (upper) {
            case "BOUGHT", "INSUFFICIENT_FUNDS", "INSUFFICIENT_PHYSICAL_FUNDS", "OUT_OF_STOCK", "MISSING_BARTER_ITEMS", "STORAGE_FULL",
                 "INVALID_ITEM", "NO_LINK", "ROLLBACK", "UNCONFIGURED", "RS_NOT_CONTROLLER",
                 "INVALID_REQUEST" -> true;
            default -> false;
        };
        if (!buyerVisible) return null;
        return Component.translatable("gui.futureshops.player_shop.buyer." + upper.toLowerCase(Locale.ROOT))
                .getString();
    }

    private void saveConfig() {
        String name = shopNameBox == null ? "" : shopNameBox.getValue().trim();
        // Authoritative toggle state lives in the backing fields; the segmented controls
        // (Storefront / Storage tabs) mutate them directly.
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopConfigPacket(
                PlayerShopClientState.shopPos(), name, configSingleMode, configBarterSame,
                PlayerShopClientState.selectedListingIndex()));
    }


    private void syncOwnerFields() {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        if (listing == null) return;

        // Only update if the box is not currently focused (user is not typing)
        if (priceBox != null && !priceBox.isFocused()) {
            String expected = ShopUiUtil.formatMinorUnits(listing.moneyPriceMinor());
            if (!priceBox.getValue().equals(expected)) {
                priceBox.setValue(expected);
            }
        }
        if (barterCountBox != null && !barterCountBox.isFocused()) {
            String expected = String.valueOf(listing.barterItemCount());
            if (!barterCountBox.getValue().equals(expected)) {
                barterCountBox.setValue(expected);
            }
        }
        if (baseQtyBox != null && !baseQtyBox.isFocused()) {
            String expected = String.valueOf(listing.baseQuantity());
            if (!baseQtyBox.getValue().equals(expected)) {
                baseQtyBox.setValue(expected);
            }
        }
    }

    /**
     * Item 10: Debounced instant price and barter count updates.
     * Called each frame in render() for owners.
     */
    private void tickDebouncedEdits() {
        long now = System.currentTimeMillis();

        // Price: apply after debounce, even while focused (real-time updates)
        if (priceEditTimestamp > 0 && now - priceEditTimestamp >= DEBOUNCE_MS) {
            priceEditTimestamp = 0;
            applyPriceFromBox();
        }

        // Barter count
        if (barterEditTimestamp > 0 && now - barterEditTimestamp >= DEBOUNCE_MS) {
            barterEditTimestamp = 0;
            applyBarterCountFromBox();
        }

        // Base qty
        if (baseQtyEditTimestamp > 0 && now - baseQtyEditTimestamp >= DEBOUNCE_MS) {
            baseQtyEditTimestamp = 0;
            applyBaseQtyFromBox();
        }
    }

    private void applyBarterCountFromBox() {
        if (barterCountBox == null) return;
        try {
            int count = Integer.parseInt(barterCountBox.getValue().trim());
            if (count >= 1) {
                sendAction("SET_BARTER_COUNT", count);
            }
        } catch (NumberFormatException ignored) {
        }
    }


    // LGB#17: Allow Enter key to apply text field values
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmationModal != null) {
            if (confirmationModal.keyPressed(keyCode)) return true;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (ClientNavigationPolicy.playerShopBlockEscape(PlayerShopClientState.owner())
                    == ClientNavigationPolicy.Action.CLOSE) {
                closeCompletely();
            } else {
                onClose();
            }
            return true;
        }
        // Enter key = 257
        if (keyCode == 257) {
            if (PlayerShopClientState.owner()) {
                if (priceBox != null && priceBox.isFocused()) {
                    priceBox.setFocused(false);
                    applyPriceFromBox();
                    return true;
                }
                if (barterCountBox != null && barterCountBox.isFocused()) {
                    barterCountBox.setFocused(false);
                    applyBarterCountFromBox();
                    return true;
                }
                if (baseQtyBox != null && baseQtyBox.isFocused()) {
                    baseQtyBox.setFocused(false);
                    applyBaseQtyFromBox();
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (confirmationModal != null) {
            return confirmationModal.mouseClicked(mouseX, mouseY, button, this.font);
        }
        // Flat Nocturne buttons: run the top-most hit ClickZone first.
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
            return true;
        }
        // Listing-rail row selection applies on the Listings tab (visitors always see the rail
        // since their activeTab stays LISTINGS). The Add button is a ClickZone handled above.
        if (activeTab == OwnerTab.LISTINGS) {
            int railX = guiLeft + 8;
            int railY = contentStartY;
            int railW = listingRailW;
            int railH = listingRailHeight();
            int cardY = railY + 32;
            List<PlayerShopListingData> listings = PlayerShopClientState.listings();
            int cardH = railH < 160 ? 32 : (railH < 200 ? 38 : 44);
            int maxVisible = Math.max(1, (railH - 40) / cardH);
            for (int i = 0; i < maxVisible && i + listingScroll < listings.size(); i++) {
                int y = cardY + i * cardH;
                if (mouseX >= railX + 6 && mouseX <= railX + railW - 6 && mouseY >= y && mouseY <= y + cardH - 4) {
                    PlayerShopClientState.setSelectedListingIndex(i + listingScroll);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // Only the LISTINGS tab draws the scrollable rail; on the other tabs the rail region is
        // covered by the tab panel, so scrolling there must NOT mutate the (hidden) listing scroll.
        if (activeTab != OwnerTab.LISTINGS) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int railX = guiLeft + 8;
        int railY = contentStartY;
        int railW = listingRailW;
        int railH = listingRailHeight();
        if (mouseX >= railX && mouseX <= railX + railW && mouseY >= railY && mouseY <= railY + railH) {
            int cardH = railH < 160 ? 32 : (railH < 200 ? 38 : 44);
            int maxVisible = Math.max(1, (railH - 40) / cardH);
            listingScroll = Math.max(0, Math.min(Math.max(0, PlayerShopClientState.listings().size() - maxVisible), listingScroll - (int) delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ═══ Owner helpers ═══

    private void adjustPrice(int deltaMinor) {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        if (listing == null) return;
        long current = listing.moneyPriceMinor();
        long newPrice = Math.max(0, current + deltaMinor);
        sendAction("SET_PRICE", (int) Math.min(newPrice, Integer.MAX_VALUE));
    }

    private void applyPriceFromBox() {
        if (priceBox == null) return;
        try {
            String text = priceBox.getValue().trim();
            double parsed = Double.parseDouble(text);
            int decimals = ShopClientState.getCurrencyDecimals();
            long minor = Math.round(parsed * Math.pow(10, decimals));
            sendAction("SET_PRICE", (int) Math.min(Math.max(0, minor), Integer.MAX_VALUE));
        } catch (NumberFormatException ignored) {
        }
    }

    private void adjustBarterCount(int delta) {
        int newCount = Math.max(1, currentBarterCount() + delta);
        sendAction("SET_BARTER_COUNT", newCount);
    }

    private void adjustBaseQty(int delta) {
        int newQty = Math.max(0, currentBaseQty() + delta);
        sendAction("SET_BASE_QTY", newQty);
    }

    private int currentBaseQty() {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        return listing == null ? 0 : listing.baseQuantity();
    }

    private void applyBaseQtyFromBox() {
        if (baseQtyBox == null) return;
        try {
            int qty = Integer.parseInt(baseQtyBox.getValue().trim());
            if (qty >= 0) {
                sendAction("SET_BASE_QTY", qty);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private String currentPriceText() {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        return listing == null ? "0" : ShopUiUtil.formatMinorUnits(listing.moneyPriceMinor());
    }

    // ═══ Visitor helpers ═══

    private void addToCart() {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        if (listing == null || (listing.stock() <= 0 && !PlayerShopClientState.adminShopMode())) return;
        int qty = getQuantity();
        String shopName = PlayerShopClientState.shopName().isBlank()
                ? Component.translatable("gui.futureshops.player_shop_block.header.shop_suffix", PlayerShopClientState.ownerName()).getString()
                : PlayerShopClientState.shopName();
        // LGB#2/#3/#4: Pass trade mode and barter info to cart
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
        rebuildWidgets();
    }

    private void buy(int quantity, String paymentMethod, PaymentSource paymentSource) {
        PlayerShopResponseTracker.PendingRequest request =
                ShopClientPacketHandler.beginPlayerShopRequest(
                        PlayerShopResponseTracker.Operation.PURCHASE, 0);
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopBuyPacket(
                PlayerShopClientState.shopPos(), PlayerShopClientState.selectedListingIndex(), quantity,
                paymentMethod, paymentSource.wire(), request.requestId(),
                request.responseToken()));
    }

    private void showBuyConfirmation(int quantity) {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        if (listing == null) return;
        String itemName = ShopUiUtil.getItemDisplayNameWithNbtAndQty(listing.itemId(), listing.nbtJson(), listing.baseQuantity());
        long total = listing.effectiveUnitPriceMinor() * quantity;
        String totalStr = total <= 0
                ? I18n.get("gui.futureshops.player_shop_block.confirm.free")
                : ShopUiUtil.formatMinorUnits(total) + " " + ShopClientState.getCurrencyName();

        boolean compound = "MONEY_AND_BARTER".equalsIgnoreCase(listing.tradeMode());
        java.util.List<ConfirmationModal.SummaryLine> summary = new java.util.ArrayList<>();
        summary.add(ConfirmationModal.SummaryLine.item(listing.itemId(),
                I18n.get("gui.futureshops.player_shop_block.confirm.item_line", itemName, quantity),
                listing.nbtJson()));

        String totalLine;
        String paymentMethod;
        if (compound) {
            // MONEY_AND_BARTER (compound) — server withdraws both coins AND the barter items.
            // Surface the barter cost in the confirmation and use the unified
            //   "Total: §a$X §f+ §9N× item"
            // rendering that cart rows + cart summary bar use end-to-end.
            int barterAmount = listing.barterItemCount() * quantity;
            String barterId = listing.barterItemId();
            // Display always uses the wire NBT — barterNbtAware only governs matching.
            String barterName = barterId == null || barterId.isBlank()
                    ? I18n.get("gui.futureshops.player_shop_block.confirm.unknown_item")
                    : ShopUiUtil.getItemDisplayNameWithNbt(barterId, listing.barterNbtJson());
            summary.add(ConfirmationModal.SummaryLine.item(
                    barterId != null ? barterId : "",
                    I18n.get("gui.futureshops.player_shop_block.confirm.plus_give", barterAmount, barterName),
                    listing.barterNbtJson()));
            totalLine = I18n.get("gui.futureshops.player_shop_block.confirm.total_compound",
                    totalStr, barterAmount, barterName);
            // Server treats MONEY_AND_BARTER as compound regardless of paymentMethod.
            paymentMethod = "";
        } else {
            totalLine = I18n.get("gui.futureshops.player_shop_block.confirm.total", totalStr);
            // Fix: BOTH mode previously relied on the server's "can afford?" fallback,
            // meaning the Buy button could unintentionally pick barter. Signal MONEY
            // explicitly so the BOTH branch on the server always honours the button.
            paymentMethod = "MONEY";
        }

        if (total > 0L) {
            confirmationModal = new ConfirmationModal(
                    I18n.get("gui.futureshops.player_shop_block.confirm.title"),
                    summary,
                    totalLine,
                    (modal, paymentSource) -> {
                        modal.setProcessing();
                        buy(quantity, paymentMethod, paymentSource);
                    },
                    () -> confirmationModal = null
            );
        } else {
            confirmationModal = new ConfirmationModal(
                    I18n.get("gui.futureshops.player_shop_block.confirm.title"),
                    summary,
                    totalLine,
                    modal -> {
                        modal.setProcessing();
                        buy(quantity, paymentMethod, PaymentSource.WALLET);
                    },
                    () -> confirmationModal = null
            );
        }
    }

    public void onTransactionResult(boolean success, String message) {
        if (confirmationModal != null) {
            if (success) {
                confirmationModal.setSuccess(message);
            } else {
                confirmationModal.setFailed(message);
            }
        }
    }

    private int getQuantity() {
        if (quantityBox == null) return 1;
        try {
            return clampQuantity(Integer.parseInt(quantityBox.getValue()));
        } catch (Exception ignored) {
            return 1;
        }
    }

    private void setQuantity(int quantity) {
        if (quantityBox != null) quantityBox.setValue(Integer.toString(clampQuantity(quantity)));
    }

    /** Smart max uses stock for money and the exact barter inventory cap when barter is required. */
    private int resolveMaxQuantity() {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
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

    private int clampQuantity(int quantity) {
        // Only clamp against shop stock at input time. Don't reject based on affordability
        // (balance / inventory) — before the client balance sync arrives those numbers read
        // as zero, which previously pinned the quantity at 1 until the player ran /shop to
        // force a balance refresh. The server-side buy path re-validates both stock and
        // funds, so letting the user freely dial up a number here is safe.
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        int stockCap = listing == null ? 999 : PurchaseQuantityPolicy.playerShopStockMaximum(
                PlayerShopClientState.adminShopMode(), listing.stock());
        return Math.max(1, Math.min(stockCap, quantity));
    }

    // ═══ Common helpers ═══

    private String currentDirection() {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        if (listing == null || listing.direction() == null || listing.direction().isBlank()) return "SELL";
        return listing.direction().toUpperCase(Locale.ROOT);
    }

    private void cycleDirection() {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        if (listing == null) return;
        String dir = currentDirection();
        String next = switch (dir) {
            case "SELL" -> "BUY";
            case "BUY" -> "BOTH";
            default -> "SELL";
        };
        sendBuybackConfig(next, listing.buybackPriceMinor(), listing.buybackCap());
    }

    private void cycleBuybackPrice() {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        if (listing == null) return;
        long cur = listing.buybackPriceMinor();
        long next = BUYBACK_PRICE_CYCLE_MINOR[0];
        for (int i = 0; i < BUYBACK_PRICE_CYCLE_MINOR.length; i++) {
            if (BUYBACK_PRICE_CYCLE_MINOR[i] > cur) { next = BUYBACK_PRICE_CYCLE_MINOR[i]; break; }
            if (i == BUYBACK_PRICE_CYCLE_MINOR.length - 1) next = BUYBACK_PRICE_CYCLE_MINOR[0];
        }
        sendBuybackConfig(currentDirection(), next, listing.buybackCap());
    }

    private void cycleBuybackCap() {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        if (listing == null) return;
        int cur = listing.buybackCap();
        int next = BUYBACK_CAP_CYCLE[0];
        for (int i = 0; i < BUYBACK_CAP_CYCLE.length; i++) {
            if (BUYBACK_CAP_CYCLE[i] > cur) { next = BUYBACK_CAP_CYCLE[i]; break; }
            if (i == BUYBACK_CAP_CYCLE.length - 1) next = BUYBACK_CAP_CYCLE[0];
        }
        sendBuybackConfig(currentDirection(), listing.buybackPriceMinor(), next);
    }

    private void sendBuybackConfig(String direction, long priceMinor, int cap) {
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopBuybackConfigPacket(
                PlayerShopClientState.shopPos(),
                PlayerShopClientState.selectedListingIndex(),
                direction, priceMinor, cap));
    }

    private void sendAction(String action, int amount) {
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopActionPacket(
                PlayerShopClientState.shopPos(), action, PlayerShopClientState.selectedListingIndex(), amount));
    }

    private void claimSettlement() {
        PlayerShopResponseTracker.PendingRequest request =
                ShopClientPacketHandler.beginPlayerShopRequest(
                        PlayerShopResponseTracker.Operation.SETTLEMENT, 0);
        ShopPackets.CHANNEL.sendToServer(
                new C2SPlayerShopSettlementClaimPacket(
                        PlayerShopClientState.shopPos(),
                        request.requestId(), request.responseToken()));
    }

    private int currentBarterCount() {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        return listing == null ? 1 : listing.barterItemCount();
    }

    private int computeListingPromoPercent(PlayerShopListingData listing) {
        if (listing == null || !listing.promo().configured()) return 0;
        if ("PERCENTAGE".equalsIgnoreCase(listing.promo().promoType())) {
            return (int) Math.round(listing.promo().promoValue());
        }
        return ShopUiUtil.computePromoPercent(listing.moneyPriceMinor(), listing.effectiveUnitPriceMinor());
    }

    private String formatPromoSchedule(com.enviouse.futureshops.data.PlayerShopPromoData promo) {
        long now = System.currentTimeMillis() / 1000L;
        long start = promo.startEpochSeconds();
        long end = promo.endEpochSeconds();
        if (start > now) {
            return I18n.get("gui.futureshops.player_shop_block.promo.starts_in", humanDuration(start - now));
        }
        if (end > 0L && end > now) {
            return I18n.get("gui.futureshops.player_shop_block.promo.ends_in", humanDuration(end - now));
        }
        return null;
    }

    private static String humanDuration(long seconds) {
        if (seconds <= 0L) return "0m";
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        long mins = (seconds % 3_600L) / 60L;
        StringBuilder out = new StringBuilder();
        if (days > 0) out.append(days).append("d ");
        if (hours > 0) out.append(hours).append("h ");
        if (mins > 0 || out.length() == 0) out.append(Math.max(1, mins)).append("m");
        return out.toString().trim();
    }

    private String promoLabel(PlayerShopListingData listing) {
        if (listing == null || !listing.promo().configured()) return I18n.get("gui.futureshops.player_shop_block.promo.label.sale");
        return switch (listing.promo().promoType()) {
            case "BUY_X_GET_Y" -> I18n.get("gui.futureshops.player_shop_block.promo.label.buy_x_get_y",
                    listing.promo().buyX(), listing.promo().buyY());
            case "FLAT" -> I18n.get("gui.futureshops.player_shop_block.promo.label.flat",
                    ShopUiUtil.formatMinorUnits(
                            Math.round(listing.promo().promoValue() * Math.pow(10, ShopClientState.getCurrencyDecimals()))));
            case "FLASH" -> I18n.get("gui.futureshops.player_shop_block.promo.label.flash");
            default -> I18n.get("gui.futureshops.player_shop_block.promo.label.sale");
        };
    }

    // LGB#11: Listing-rail meta label. Delegates to ShopUiUtil.tradeModeLabel so every
    // user-facing trade-mode string resolves through the lang file (keys under
    // `gui.futureshops.trade_mode.*`). Invariant enforced by BuyPacketCallSiteTest.
    private String prettyMode(String mode) {
        return ShopUiUtil.tradeModeLabel(mode);
    }

    private String localizeResultCode(String code) {
        String key = "gui.futureshops.player_shop.result." + code.toLowerCase(Locale.ROOT);
        return Component.translatable(key).getString();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    private void closeCompletely() {
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
