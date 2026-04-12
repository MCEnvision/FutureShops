package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.PlayerShopClientState;
import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.PlayerShopListingData;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SPlayerShopActionPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopBuyPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopConfigPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public class PlayerShopBlockScreen extends Screen implements ShopScreenMarker {
    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private int listingScroll;
    private EditBox quantityBox;
    private EditBox shopNameBox;

    // Owner controls
    private Button addListingButton;
    private Button removeListingButton;
    private Button toggleModeButton;
    private EditBox priceBox;
    private Button priceMinusButton;
    private Button pricePlusButton;
    private Button barterSetButton;
    private EditBox barterCountBox;
    private Button barterMinusButton;
    private Button barterPlusButton;
    private Button promoButton;
    private Button claimButton;
    private Button historyButton;
    private Button linkButton;
    private Button unlinkButton;
    private Button singleMultiButton;
    private Button barterStorageButton;
    private Button linkBarterButton;
    private Button unlinkBarterButton;
    private Button saveConfigButton;

    // Visitor controls
    private Button visitorBuyButton;
    private Button visitorBarterButton;

    public PlayerShopBlockScreen() {
        super(Component.literal("Player Shop"));
    }

    @Override
    protected void init() {
        guiW = Math.min(580, Math.max(400, this.width - 20));
        guiH = Math.min(380, Math.max(280, this.height - 20));
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;

        addRenderableWidget(Button.builder(Component.literal("§c✕"), button -> onClose())
                .bounds(guiLeft + guiW - 24, guiTop + 6, 18, 14)
                .build());

        if (PlayerShopClientState.owner()) {
            initOwnerWidgets();
        } else {
            // Auto-select the only listing for single-item shops
            List<PlayerShopListingData> listings = PlayerShopClientState.listings();
            if (listings.size() == 1) {
                PlayerShopClientState.setSelectedListingIndex(0);
                PlayerShopListingData only = listings.get(0);
                // Single-item barter-only → go straight to barter screen
                if ("BARTER".equalsIgnoreCase(only.tradeMode()) && this.minecraft != null) {
                    this.minecraft.tell(() -> this.minecraft.setScreen(new PlayerShopBarterScreen(null)));
                    return;
                }
            }
            initVisitorWidgets();
        }
    }

    private void initOwnerWidgets() {
        // ═══ Config Panel (top area) ═══
        int configY = guiTop + 66;

        // Shop name editor
        shopNameBox = new EditBox(this.font, guiLeft + 74, configY, Math.min(160, guiW - 280), 14,
                Component.literal("Shop Name"));
        shopNameBox.setMaxLength(32);
        shopNameBox.setValue(PlayerShopClientState.shopName());
        addRenderableWidget(shopNameBox);

        // Single/Multi toggle
        boolean single = PlayerShopClientState.singleItemMode();
        singleMultiButton = addRenderableWidget(Button.builder(
                        Component.literal(single ? "§eSingle Item" : "§aMulti Item"),
                        button -> {
                            boolean nowSingle = button.getMessage().getString().contains("Multi");
                            button.setMessage(Component.literal(nowSingle ? "§eSingle Item" : "§aMulti Item"));
                        })
                .bounds(guiLeft + 74 + Math.min(164, guiW - 276), configY, 78, 14)
                .build());

        // Barter storage same/separate toggle
        boolean same = PlayerShopClientState.barterStorageSame();
        barterStorageButton = addRenderableWidget(Button.builder(
                        Component.literal(same ? "§7Barter: Same Chest" : "§dBarter: Separate"),
                        button -> {
                            boolean nowSame = button.getMessage().getString().contains("Same");
                            button.setMessage(Component.literal(nowSame ? "§dBarter: Separate" : "§7Barter: Same Chest"));
                        })
                .bounds(guiLeft + 74, configY + 18, 140, 14)
                .build());

        // Save config button
        saveConfigButton = addRenderableWidget(Button.builder(Component.literal("§aSave Config"), button -> saveConfig())
                .bounds(guiLeft + 218, configY + 18, 72, 14)
                .build());

        // ═══ Footer owner controls ═══
        int footerY = guiTop + guiH - 22;
        int bx = guiLeft + 8;

        addListingButton = addRenderableWidget(Button.builder(Component.literal("§a+ Add"), button -> sendAction("ADD_LISTING_MAINHAND", 0))
                .bounds(bx, footerY, 44, 16).build());
        bx += 48;
        removeListingButton = addRenderableWidget(Button.builder(Component.literal("§c- Del"), button -> sendAction("REMOVE_LISTING", 0))
                .bounds(bx, footerY, 40, 16).build());
        bx += 44;
        promoButton = addRenderableWidget(Button.builder(Component.literal("§6Promo"), button -> this.minecraft.setScreen(new PromoEditorModalScreen(this)))
                .bounds(bx, footerY, 42, 16).build());
        bx += 46;
        claimButton = addRenderableWidget(Button.builder(Component.literal("§aClaim"), button -> sendAction("CLAIM_SETTLEMENT", 0))
                .bounds(bx, footerY, 40, 16).build());
        bx += 44;
        historyButton = addRenderableWidget(Button.builder(Component.literal("Hist"), button -> this.minecraft.setScreen(new SettlementHistoryScreen(this)))
                .bounds(bx, footerY, 34, 16).build());
        bx += 38;
        linkButton = addRenderableWidget(Button.builder(Component.literal("Link"), button -> sendAction("LINK_LOOKING", 0))
                .bounds(bx, footerY, 34, 16).build());
        bx += 38;
        unlinkButton = addRenderableWidget(Button.builder(Component.literal("Unlink"), button -> sendAction("UNLINK", 0))
                .bounds(bx, footerY, 40, 16).build());

        // Barter link/unlink buttons
        bx += 44;
        linkBarterButton = addRenderableWidget(Button.builder(Component.literal("§dB.Link"), button -> sendAction("LINK_BARTER_LOOKING", 0))
                .bounds(bx, footerY, 44, 16).build());
        bx += 48;
        unlinkBarterButton = addRenderableWidget(Button.builder(Component.literal("§dB.Unlnk"), button -> sendAction("UNLINK_BARTER", 0))
                .bounds(bx, footerY, 48, 16).build());

        // ═══ Detail adjustment controls — editable text fields with -/+ ═══
        int adjY = guiTop + guiH - 58;
        int adjX = guiLeft + guiW - 260;

        // Mode toggle (cycles MONEY → BARTER → BOTH)
        toggleModeButton = addRenderableWidget(Button.builder(Component.literal("Mode"), button -> sendAction("TOGGLE_MODE", 0))
                .bounds(adjX, adjY, 42, 14).build());

        // Price: - [editbox] +
        priceMinusButton = addRenderableWidget(Button.builder(Component.literal("-"), button -> adjustPrice(-100))
                .bounds(adjX + 46, adjY, 16, 14).build());
        priceBox = new EditBox(this.font, adjX + 64, adjY, 48, 14, Component.literal("Price"));
        priceBox.setMaxLength(10);
        priceBox.setValue(currentPriceText());
        priceBox.setResponder(value -> {
            // Will send on enter or when +/- are clicked
        });
        addRenderableWidget(priceBox);
        pricePlusButton = addRenderableWidget(Button.builder(Component.literal("+"), button -> adjustPrice(100))
                .bounds(adjX + 114, adjY, 16, 14).build());

        // Barter set from mainhand
        barterSetButton = addRenderableWidget(Button.builder(Component.literal("§dSet"), button -> sendAction("SET_BARTER_MAINHAND", currentBarterCount()))
                .bounds(adjX + 134, adjY, 28, 14).build());

        // Barter count: - [editbox] +
        barterMinusButton = addRenderableWidget(Button.builder(Component.literal("-"), button -> adjustBarterCount(-1))
                .bounds(adjX + 166, adjY, 16, 14).build());
        barterCountBox = new EditBox(this.font, adjX + 184, adjY, 32, 14, Component.literal("Qty"));
        barterCountBox.setMaxLength(4);
        barterCountBox.setValue(String.valueOf(currentBarterCount()));
        barterCountBox.setResponder(value -> {
            // Will send on +/- or explicit action
        });
        addRenderableWidget(barterCountBox);
        barterPlusButton = addRenderableWidget(Button.builder(Component.literal("+"), button -> adjustBarterCount(1))
                .bounds(adjX + 218, adjY, 16, 14).build());

        // Apply price button (sends the typed value)
        addRenderableWidget(Button.builder(Component.literal("§a✓"), button -> applyPriceFromBox())
                .bounds(adjX + 238, adjY, 16, 14).build());
    }

    private void initVisitorWidgets() {
        int y = guiTop + guiH - 22;
        int ctrlX = guiLeft + guiW - 260;

        // Quantity: - [box] + Max
        addRenderableWidget(Button.builder(Component.literal("-"), button -> setQuantity(getQuantity() - 1))
                .bounds(ctrlX, y, 14, 14).build());
        quantityBox = new EditBox(this.font, ctrlX + 16, y, 36, 14, Component.literal("Qty"));
        quantityBox.setValue("1");
        quantityBox.setMaxLength(4);
        quantityBox.setResponder(value -> {
            if (value.isBlank()) return;
            try {
                int parsed = Integer.parseInt(value);
                int clamped = clampQuantity(parsed);
                if (clamped != parsed) quantityBox.setValue(String.valueOf(clamped));
            } catch (NumberFormatException ignored) {
                quantityBox.setValue("1");
            }
        });
        addRenderableWidget(quantityBox);
        addRenderableWidget(Button.builder(Component.literal("+"), button -> setQuantity(getQuantity() + 1))
                .bounds(ctrlX + 54, y, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("Max"), button -> setQuantity(resolveMaxQuantity()))
                .bounds(ctrlX + 70, y, 28, 14).build());

        // Buy button (money)
        visitorBuyButton = addRenderableWidget(Button.builder(Component.literal("§a$ Buy"), button -> buy(getQuantity()))
                .bounds(ctrlX + 104, y, 70, 14).build());

        // Barter button
        visitorBarterButton = addRenderableWidget(Button.builder(Component.literal("§d⚒ Barter"), button -> {
                    if (this.minecraft != null)
                        this.minecraft.setScreen(new PlayerShopBarterScreen(this));
                })
                .bounds(ctrlX + 178, y, 72, 14).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, ShopColors.BG_PRIMARY);

        int panelBg = PlayerShopClientState.owner() ? ShopColors.OWNER_BG : ShopColors.BG_PANEL;
        int accentColor = PlayerShopClientState.owner() ? ShopColors.OWNER_ACCENT : ShopColors.ACCENT_CYAN;
        ShopUiUtil.renderAccentPanel(graphics, guiLeft, guiTop, guiW, guiH, panelBg, ShopColors.BORDER_DEFAULT, accentColor);

        renderHeader(graphics);
        if (PlayerShopClientState.owner()) {
            renderConfigPanel(graphics);
        }

        boolean singleItemVisitor = !PlayerShopClientState.owner() && PlayerShopClientState.listings().size() == 1;
        if (singleItemVisitor) {
            renderSingleItemDetail(graphics);
        } else {
            renderListingRail(graphics, mouseX, mouseY);
            renderDetailPanel(graphics);
        }

        renderStatus(graphics);
        syncButtonStates();

        // Update owner text fields to current listing data
        if (PlayerShopClientState.owner()) {
            syncOwnerFields();
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics) {
        int hx = guiLeft + 8;
        int hy = guiTop + 8;
        int hw = guiW - 16;
        int hh = 50;
        int accentColor = PlayerShopClientState.owner() ? ShopColors.OWNER_ACCENT : ShopColors.ACCENT_CYAN;
        ShopUiUtil.renderAccentPanel(graphics, hx, hy, hw, hh, ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT, accentColor);

        ShopUiUtil.renderPlayerFace(graphics, PlayerShopClientState.ownerUuid(), hx + 8, hy + 8, 34);

        String title = PlayerShopClientState.owner() ? "§d⚡ Manage Your Shop" : "§fBrowse Player Shop";
        graphics.drawString(this.font, title, hx + 50, hy + 8, ShopColors.TEXT_PRIMARY, false);

        String shopName = PlayerShopClientState.shopName().isBlank()
                ? PlayerShopClientState.ownerName() + "'s Shop"
                : PlayerShopClientState.shopName();
        graphics.drawString(this.font, this.font.plainSubstrByWidth("§7" + shopName, hw - 200), hx + 50, hy + 20, ShopColors.TEXT_SECONDARY, false);

        graphics.drawString(this.font, this.font.plainSubstrByWidth("Owner: " + PlayerShopClientState.ownerName(), 130), hx + 50, hy + 32, ShopColors.TEXT_SECONDARY, false);

        ShopUiUtil.drawChip(graphics, this.font, hx + hw - 140, hy + 8,
                this.font.plainSubstrByWidth(
                        PlayerShopClientState.linked() ? "✓ Storage Linked" : "⚠ Needs Link",
                        120),
                ShopColors.BG_PANEL,
                PlayerShopClientState.linked() ? ShopColors.SUCCESS : ShopColors.ERROR,
                PlayerShopClientState.linked() ? ShopColors.SUCCESS : ShopColors.ERROR);

        String revenue = "Pending " + ShopUiUtil.formatMinorUnits(PlayerShopClientState.pendingSettlementMinor())
                + " • Total " + ShopUiUtil.formatMinorUnits(PlayerShopClientState.lifetimeRevenueMinor());
        graphics.drawString(this.font, this.font.plainSubstrByWidth(revenue, hw - 160), hx + hw - 140, hy + 30, ShopColors.TEXT_PRICE, false);
    }

    private void renderConfigPanel(GuiGraphics graphics) {
        int cx = guiLeft + 8;
        int cy = guiTop + 62;
        int cw = guiW - 16;
        int ch = 38;
        ShopUiUtil.renderPanel(graphics, cx, cy, cw, ch, ShopColors.CONFIG_BG, ShopColors.OWNER_ACCENT);
        graphics.drawString(this.font, "§dConfig", cx + 8, cy + 4, ShopColors.OWNER_ACCENT, false);
        graphics.drawString(this.font, "§7Name:", cx + 8, cy + 18, ShopColors.TEXT_SECONDARY, false);
    }

    private void renderListingRail(GuiGraphics graphics, int mouseX, int mouseY) {
        int railX = guiLeft + 8;
        int railY = PlayerShopClientState.owner() ? guiTop + 104 : guiTop + 66;
        int railW = 170;
        int railH = guiH - (PlayerShopClientState.owner() ? 136 : 100);
        ShopUiUtil.renderAccentPanel(graphics, railX, railY, railW, railH,
                ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT, ShopColors.ACCENT_PURPLE);
        graphics.drawString(this.font, "§lListings", railX + 8, railY + 6, ShopColors.TEXT_PRIMARY, false);

        List<PlayerShopListingData> listings = PlayerShopClientState.listings();
        String countText = listings.isEmpty() ? "§7No items yet" : "§7" + listings.size() + " configured";
        graphics.drawString(this.font, countText, railX + 8, railY + 18, ShopColors.TEXT_SECONDARY, false);

        int cardY = railY + 32;
        int maxVisible = Math.max(1, (railH - 40) / 44);
        listingScroll = Math.max(0, Math.min(listingScroll, Math.max(0, listings.size() - maxVisible)));
        for (int i = 0; i < maxVisible && i + listingScroll < listings.size(); i++) {
            int listingIndex = i + listingScroll;
            PlayerShopListingData listing = listings.get(listingIndex);
            int y = cardY + i * 44;
            boolean selected = listingIndex == PlayerShopClientState.selectedListingIndex();
            int cardBorder = selected ? ShopColors.ACCENT_CYAN : ShopColors.BORDER_DEFAULT;
            ShopUiUtil.renderPanel(graphics, railX + 6, y, railW - 12, 38,
                    selected ? ShopColors.BG_CARD_HOVER : ShopColors.BG_PANEL, cardBorder);
            ShopUiUtil.renderItemIcon(graphics, this.font, listing.itemId(), railX + 12, y + 11);

            String name = this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayName(listing.itemId()), railW - 66);
            graphics.drawString(this.font, name, railX + 34, y + 6, ShopColors.TEXT_PRIMARY, false);

            String meta = this.font.plainSubstrByWidth(listing.stock() + " stock • " + prettyMode(listing.tradeMode()), railW - 50);
            graphics.drawString(this.font, meta, railX + 34, y + 20,
                    "MONEY".equalsIgnoreCase(listing.tradeMode()) ? ShopColors.TEXT_PRICE : ShopColors.TEXT_BARTER, false);

            // ═══ Promo badge — percentage at top-right of card ═══
            if (listing.promo().configured()) {
                int percent = computeListingPromoPercent(listing);
                if (percent > 0) {
                    ShopUiUtil.renderAnimatedDiscountBadge(graphics, this.font,
                            railX + railW - 18, y + 10, "-" + percent + "%");
                } else {
                    ShopUiUtil.drawChip(graphics, this.font, railX + railW - 62, y + 6, promoLabel(listing),
                            ShopColors.DISCOUNT_BG, ShopColors.DISCOUNT_BG, ShopColors.DISCOUNT_TEXT);
                }
            }
        }
    }

    private void renderDetailPanel(GuiGraphics graphics) {
        int detailX = guiLeft + 186;
        int detailY = PlayerShopClientState.owner() ? guiTop + 104 : guiTop + 66;
        int detailW = guiW - 194;
        int detailH = guiH - (PlayerShopClientState.owner() ? 136 : 100);
        ShopUiUtil.renderPanel(graphics, detailX, detailY, detailW, detailH, ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT);

        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        if (listing == null) {
            graphics.drawCenteredString(this.font, "§7Select a listing", detailX + detailW / 2, detailY + detailH / 2, ShopColors.TEXT_SECONDARY);
            return;
        }

        // Item preview
        ShopUiUtil.renderLargeItemPreview(graphics, this.font, listing.itemId(), detailX + 6, detailY + 6, Math.min(130, detailW / 2));

        int previewBottomY = detailY + 82;
        String name = this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayName(listing.itemId()), detailW / 2 - 8);
        graphics.drawString(this.font, name, detailX + 10, previewBottomY, ShopColors.TEXT_PRIMARY, false);

        String stockStr = "Stock " + listing.stock() + (listing.stock() <= 16 ? " §c• low" : " §a• healthy");
        graphics.drawString(this.font, this.font.plainSubstrByWidth(stockStr, detailW / 2 - 8), detailX + 10, previewBottomY + 12,
                listing.stock() <= 16 ? ShopColors.ERROR : ShopColors.SUCCESS, false);

        // Mode
        String modeStr = "Mode: " + prettyMode(listing.tradeMode());
        int modeColor = switch (listing.tradeMode().toUpperCase(Locale.ROOT)) {
            case "BARTER" -> ShopColors.TEXT_BARTER;
            case "BOTH" -> ShopColors.ACCENT_PURPLE;
            default -> ShopColors.TEXT_PRICE;
        };
        graphics.drawString(this.font, modeStr, detailX + 10, previewBottomY + 24, modeColor, false);

        // Promo badge (works for all modes: money, barter, both)
        if (listing.promo().configured()) {
            int percent = computeListingPromoPercent(listing);
            String badge = percent > 0 ? "-" + percent + "%" : promoLabel(listing);
            ShopUiUtil.renderAnimatedDiscountBadge(graphics, this.font, detailX + 60, previewBottomY + 42, badge);
        }

        // ═══ Right info panels ═══
        int infoX = detailX + Math.max(140, detailW / 2);
        int infoW = detailW - Math.max(140, detailW / 2) - 8;
        if (infoW < 60) {
            infoX = detailX + 10;
            infoW = detailW - 20;
        }
        int panelH = 60;

        // Pricing panel
        ShopUiUtil.renderPanel(graphics, infoX, detailY + 8, infoW, panelH, ShopColors.BG_PANEL, ShopColors.BORDER_DEFAULT);
        graphics.drawString(this.font, "§lPricing", infoX + 6, detailY + 14, ShopColors.TEXT_SECONDARY, false);

        boolean hasMoney = !"BARTER".equalsIgnoreCase(listing.tradeMode());
        boolean hasBarter = !"MONEY".equalsIgnoreCase(listing.tradeMode());

        if (hasMoney) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth("Base: " + ShopUiUtil.formatMinorUnits(listing.moneyPriceMinor()), infoW - 12),
                    infoX + 6, detailY + 28, ShopColors.TEXT_SECONDARY, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth("§aNow: " + ShopUiUtil.formatMinorUnits(listing.effectiveUnitPriceMinor()), infoW - 12),
                    infoX + 6, detailY + 42, ShopColors.TEXT_PRICE, false);
        }
        if (hasBarter) {
            int barterY = hasMoney ? detailY + 52 : detailY + 28;
            String barter = this.font.plainSubstrByWidth(listing.barterItemCount() + " × " + ShopUiUtil.getItemDisplayName(listing.barterItemId()), infoW - 12);
            graphics.drawString(this.font, barter, infoX + 6, barterY, ShopColors.TEXT_BARTER, false);
            if (!PlayerShopClientState.owner()) {
                graphics.drawString(this.font, "Owned: " + ShopUiUtil.countPlayerInventory(listing.barterItemId()),
                        infoX + 6, barterY + 12, ShopColors.TEXT_SECONDARY, false);
            }
        }

        // Trade summary panel
        int summaryY = detailY + panelH + 14;
        ShopUiUtil.renderPanel(graphics, infoX, summaryY, infoW, panelH, ShopColors.BG_PANEL, ShopColors.BORDER_DEFAULT);
        graphics.drawString(this.font, "§lTrade Info", infoX + 6, summaryY + 6, ShopColors.TEXT_SECONDARY, false);

        if ("BOTH".equalsIgnoreCase(listing.tradeMode())) {
            ShopUiUtil.drawWrappedString(graphics, this.font, Component.literal("Pay with coins or barter items."),
                    infoX + 6, summaryY + 20, infoW - 12, ShopColors.TEXT_PRIMARY, 10);
            String promoStatus = listing.promo().configured() ? "§aPromo active (all modes)" : "§7No promo";
            graphics.drawString(this.font, promoStatus, infoX + 6, summaryY + 40, ShopColors.TEXT_SECONDARY, false);
        } else if ("MONEY".equalsIgnoreCase(listing.tradeMode())) {
            ShopUiUtil.drawWrappedString(graphics, this.font, Component.literal("Instant purchase with balance."),
                    infoX + 6, summaryY + 20, infoW - 12, ShopColors.TEXT_PRIMARY, 10);
            String promoStatus = listing.promo().configured() ? "§aPromo active" : "§7No promo";
            graphics.drawString(this.font, promoStatus, infoX + 6, summaryY + 40, ShopColors.TEXT_SECONDARY, false);
        } else {
            String summary = this.font.plainSubstrByWidth(
                    listing.barterItemCount() + " × " + ShopUiUtil.getItemDisplayName(listing.barterItemId()) + " per item",
                    infoW - 12);
            graphics.drawString(this.font, summary, infoX + 6, summaryY + 20, ShopColors.TEXT_BARTER, false);
            String promoStatus = listing.promo().configured() ? "§aPromo active (barter)" : "§7No promo";
            graphics.drawString(this.font, promoStatus, infoX + 6, summaryY + 40, ShopColors.TEXT_SECONDARY, false);
        }

        // Owner-specific info
        if (PlayerShopClientState.owner()) {
            graphics.drawString(this.font, "§7Barter amt: " + listing.barterItemCount(), detailX + 10, detailY + detailH - 18, ShopColors.TEXT_PRIMARY, false);
        }
    }

    /**
     * Single-item visitor detail — mirrors ItemDetailScreen layout:
     * Left = preview panel with item icon, name, stock, qty controls.
     * Right = info panels with pricing, trade info, promo.
     */
    private void renderSingleItemDetail(GuiGraphics graphics) {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        if (listing == null) return;

        int detailX = guiLeft + 8;
        int detailY = guiTop + 66;
        int detailW = guiW - 16;
        int detailH = guiH - 100;
        int previewW = Math.min(140, detailW / 2 - 10);

        ShopUiUtil.renderPanel(graphics, detailX, detailY, detailW, detailH, ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT);

        // ═══ Left: Preview panel (like ItemDetailScreen) ═══
        ShopUiUtil.renderAccentPanel(graphics, detailX + 8, detailY + 8, previewW, detailH - 16,
                ShopColors.BG_PANEL, ShopColors.BORDER_DEFAULT, ShopColors.ACCENT_PURPLE);
        ShopUiUtil.renderLargeItemPreview(graphics, this.font, listing.itemId(), detailX + 10, detailY + 16, previewW - 4);

        String name = this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayName(listing.itemId()), previewW - 10);
        graphics.drawCenteredString(this.font, name, detailX + 8 + previewW / 2, detailY + detailH - 58, ShopColors.TEXT_PRIMARY);

        // You own count
        int owned = ShopUiUtil.countPlayerInventory(listing.itemId());
        graphics.drawCenteredString(this.font, "§7You own: " + owned, detailX + 8 + previewW / 2, detailY + detailH - 46, ShopColors.TEXT_SECONDARY);

        // Total cost
        if (!"BARTER".equalsIgnoreCase(listing.tradeMode())) {
            long total = listing.effectiveUnitPriceMinor() * getQuantity();
            String totalStr = "§6Total: §a" + ShopUiUtil.formatMinorUnits(total);
            graphics.drawCenteredString(this.font, totalStr, detailX + 8 + previewW / 2, detailY + detailH - 34, ShopColors.TEXT_PRICE);
        }

        String stockStr = "Stock: " + listing.stock() + (listing.stock() <= 16 ? " §c• low" : " §a• healthy");
        graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(stockStr, previewW - 8),
                detailX + 8 + previewW / 2, detailY + detailH - 22,
                listing.stock() <= 16 ? ShopColors.ERROR : ShopColors.SUCCESS);

        // ═══ Right: Info panels (like ItemDetailScreen) ═══
        int infoX = detailX + previewW + 20;
        int infoW = detailW - previewW - 28;

        // Title (scaled)
        graphics.pose().pushPose();
        graphics.pose().translate(infoX + 8, detailY + 14, 0);
        graphics.pose().scale(1.2f, 1.2f, 1f);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayName(listing.itemId()), (int)(infoW / 1.2f) - 8), 0, 0, ShopColors.TEXT_PRIMARY, true);
        graphics.pose().popPose();

        // Shop owner line
        String shopLabel = PlayerShopClientState.shopName().isBlank()
                ? PlayerShopClientState.ownerName() + "'s Shop"
                : PlayerShopClientState.shopName();
        graphics.drawString(this.font, this.font.plainSubstrByWidth("§7" + shopLabel + " • " + PlayerShopClientState.ownerName(), infoW - 8),
                infoX + 8, detailY + 28, ShopColors.TEXT_SECONDARY, false);

        int nextY = detailY + 42;

        // Promo banner
        if (listing.promo().configured()) {
            int promoPercent = computeListingPromoPercent(listing);
            if (promoPercent > 0) {
                ShopUiUtil.renderAnimatedDiscountBadge(graphics, this.font,
                        infoX + infoW / 2, nextY + 6, "-" + promoPercent + "% OFF!");
                nextY += 20;
            }
        }

        // Divider
        graphics.fill(infoX + 8, nextY, infoX + infoW - 8, nextY + 1, ShopColors.BORDER_DEFAULT);
        nextY += 6;

        // Mode
        String modeStr = "Mode: " + prettyMode(listing.tradeMode());
        graphics.drawString(this.font, modeStr, infoX + 8, nextY, ShopColors.TEXT_SECONDARY, false);
        nextY += 14;

        boolean hasMoney = !"BARTER".equalsIgnoreCase(listing.tradeMode());
        boolean hasBarter = !"MONEY".equalsIgnoreCase(listing.tradeMode());

        // Money pricing
        if (hasMoney) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth("Buy: " + ShopUiUtil.formatMinorUnits(listing.effectiveUnitPriceMinor()), infoW - 16),
                    infoX + 8, nextY, ShopColors.TEXT_PRICE, false);
            if (listing.moneyPriceMinor() != listing.effectiveUnitPriceMinor()) {
                String base = "§7§m" + ShopUiUtil.formatMinorUnits(listing.moneyPriceMinor());
                graphics.drawString(this.font, base, infoX + 8 + this.font.width("Buy: " + ShopUiUtil.formatMinorUnits(listing.effectiveUnitPriceMinor())) + 4, nextY, ShopColors.TEXT_SECONDARY, false);
            }
            nextY += 12;
        }

        // Barter info
        if (hasBarter) {
            String barterId = listing.barterItemId();
            if (barterId != null && !barterId.isBlank()) {
                String barterText = listing.barterItemCount() + " × " + ShopUiUtil.getItemDisplayName(barterId);
                graphics.drawString(this.font, this.font.plainSubstrByWidth("§d⚒ " + barterText, infoW - 16),
                        infoX + 8, nextY, ShopColors.TEXT_BARTER, false);
                nextY += 12;
                int ownedBarter = ShopUiUtil.countPlayerInventory(barterId);
                graphics.drawString(this.font, "Owned: " + ownedBarter,
                        infoX + 8, nextY, ownedBarter >= listing.barterItemCount() ? ShopColors.SUCCESS : ShopColors.ERROR, false);
                nextY += 12;
            }
        }

        // Stock
        String stockLabel = listing.stock() > 0 ? "§a" + listing.stock() + " in stock" : "§cOut of stock";
        graphics.drawString(this.font, stockLabel, infoX + 8, nextY, ShopColors.TEXT_SECONDARY, false);
    }

    private void renderStatus(GuiGraphics graphics) {
        if (!PlayerShopClientState.resultCode().isBlank()) {
            String status = this.font.plainSubstrByWidth(
                    Component.translatable("gui.futureshops.player_shop.status", localizeResultCode(PlayerShopClientState.resultCode())).getString(),
                    guiW - 20);
            int statusY = PlayerShopClientState.owner() ? guiTop + guiH - 38 : guiTop + guiH - 36;
            graphics.drawString(this.font, status, guiLeft + 10, statusY, ShopColors.TEXT_SECONDARY, false);
        }
    }

    private void saveConfig() {
        String name = shopNameBox == null ? "" : shopNameBox.getValue().trim();
        boolean singleItem = singleMultiButton != null && singleMultiButton.getMessage().getString().contains("Single");
        boolean barterSame = barterStorageButton != null && barterStorageButton.getMessage().getString().contains("Same");
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopConfigPacket(
                PlayerShopClientState.shopPos(), name, singleItem, barterSame));
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
    }

    private void syncButtonStates() {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        boolean hasSelection = listing != null;
        if (removeListingButton != null) removeListingButton.active = hasSelection;
        if (toggleModeButton != null) toggleModeButton.active = hasSelection;
        if (priceMinusButton != null) priceMinusButton.active = hasSelection;
        if (pricePlusButton != null) pricePlusButton.active = hasSelection;
        if (priceBox != null) priceBox.setEditable(hasSelection);
        if (barterSetButton != null) barterSetButton.active = hasSelection;
        if (barterMinusButton != null) barterMinusButton.active = hasSelection;
        if (barterPlusButton != null) barterPlusButton.active = hasSelection;
        if (barterCountBox != null) barterCountBox.setEditable(hasSelection);
        if (promoButton != null) promoButton.active = hasSelection;

        // Visitor button states
        if (hasSelection && !PlayerShopClientState.owner()) {
            boolean inStock = listing.stock() > 0;
            boolean hasMoney = !"BARTER".equalsIgnoreCase(listing.tradeMode());
            boolean hasBarter = !"MONEY".equalsIgnoreCase(listing.tradeMode());

            if (visitorBuyButton != null) {
                visitorBuyButton.visible = hasMoney;
                visitorBuyButton.active = hasMoney && inStock;
            }
            if (visitorBarterButton != null) {
                visitorBarterButton.visible = hasBarter;
                visitorBarterButton.active = hasBarter && inStock;
            }
        } else if (!PlayerShopClientState.owner()) {
            if (visitorBuyButton != null) { visitorBuyButton.active = false; visitorBuyButton.visible = true; }
            if (visitorBarterButton != null) { visitorBarterButton.active = false; visitorBarterButton.visible = true; }
        }

        // Show/hide barter link buttons based on barterStorageSame setting
        boolean showBarterLink = !PlayerShopClientState.barterStorageSame();
        if (linkBarterButton != null) linkBarterButton.visible = showBarterLink;
        if (unlinkBarterButton != null) unlinkBarterButton.visible = showBarterLink;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int railX = guiLeft + 8;
        int railY = PlayerShopClientState.owner() ? guiTop + 104 : guiTop + 66;
        int railW = 170;
        int railH = guiH - (PlayerShopClientState.owner() ? 136 : 100);
        int cardY = railY + 32;
        List<PlayerShopListingData> listings = PlayerShopClientState.listings();
        int maxVisible = Math.max(1, (railH - 40) / 44);
        for (int i = 0; i < maxVisible && i + listingScroll < listings.size(); i++) {
            int y = cardY + i * 44;
            if (mouseX >= railX + 6 && mouseX <= railX + railW - 6 && mouseY >= y && mouseY <= y + 38) {
                PlayerShopClientState.setSelectedListingIndex(i + listingScroll);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int railX = guiLeft + 8;
        int railY = PlayerShopClientState.owner() ? guiTop + 104 : guiTop + 66;
        int railW = 170;
        int railH = guiH - (PlayerShopClientState.owner() ? 136 : 100);
        if (mouseX >= railX && mouseX <= railX + railW && mouseY >= railY && mouseY <= railY + railH) {
            int maxVisible = Math.max(1, (railH - 40) / 44);
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
            // Parse the formatted value (e.g. "10.00" → 1000 minor units)
            String text = priceBox.getValue().trim();
            double parsed = Double.parseDouble(text);
            int decimals = ShopClientState.getCurrencyDecimals();
            long minor = Math.round(parsed * Math.pow(10, decimals));
            sendAction("SET_PRICE", (int) Math.min(Math.max(0, minor), Integer.MAX_VALUE));
        } catch (NumberFormatException ignored) {
            // Reset to current value
        }
    }

    private void adjustBarterCount(int delta) {
        int newCount = Math.max(1, currentBarterCount() + delta);
        sendAction("SET_BARTER_COUNT", newCount);
    }

    private String currentPriceText() {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        return listing == null ? "0" : ShopUiUtil.formatMinorUnits(listing.moneyPriceMinor());
    }

    // ═══ Visitor helpers ═══

    private void buy(int quantity) {
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopBuyPacket(
                PlayerShopClientState.shopPos(), PlayerShopClientState.selectedListingIndex(), quantity));
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

    /**
     * Smart max: for MONEY = balance / price, for BARTER = inventory / cost,
     * for BOTH = max of the two. No hard 64 cap — excess drops on floor.
     */
    private int resolveMaxQuantity() {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        if (listing == null) return 1;
        int stock = Math.max(1, listing.stock());
        String mode = listing.tradeMode().toUpperCase(Locale.ROOT);
        int maxMoney = Integer.MAX_VALUE;
        int maxBarter = Integer.MAX_VALUE;

        if (!"BARTER".equals(mode) && listing.effectiveUnitPriceMinor() > 0) {
            long balance = ShopClientState.getCurrentBalanceMinorUnits();
            maxMoney = (int) Math.min(balance / listing.effectiveUnitPriceMinor(), Integer.MAX_VALUE);
        }
        if (!"MONEY".equals(mode)) {
            String barterId = listing.barterItemId();
            int barterCost = listing.barterItemCount();
            if (barterId != null && !barterId.isBlank() && barterCost > 0) {
                maxBarter = ShopUiUtil.countPlayerInventory(barterId) / barterCost;
            }
        }

        int affordable;
        if ("BOTH".equals(mode)) {
            affordable = Math.max(maxMoney, maxBarter); // player can pick either
        } else if ("BARTER".equals(mode)) {
            affordable = maxBarter;
        } else {
            affordable = maxMoney;
        }

        return Math.max(1, Math.min(stock, affordable));
    }

    private int clampQuantity(int quantity) {
        return Math.max(1, Math.min(resolveMaxQuantity(), quantity));
    }

    // ═══ Common helpers ═══

    private void sendAction(String action, int amount) {
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopActionPacket(
                PlayerShopClientState.shopPos(), action, PlayerShopClientState.selectedListingIndex(), amount));
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

    private String promoLabel(PlayerShopListingData listing) {
        if (listing == null || !listing.promo().configured()) return "sale";
        return switch (listing.promo().promoType()) {
            case "BUY_X_GET_Y" -> "B" + listing.promo().buyX() + "G" + listing.promo().buyY();
            case "FLAT" -> "flat";
            case "FLASH" -> "flash";
            default -> "sale";
        };
    }

    private String prettyMode(String mode) {
        if (mode == null || mode.isBlank()) return "Money";
        return switch (mode.toUpperCase(Locale.ROOT)) {
            case "BARTER" -> "Barter";
            case "BOTH" -> "Money + Barter";
            default -> "Money";
        };
    }

    private String localizeResultCode(String code) {
        String key = "gui.futureshops.player_shop.result." + code.toLowerCase(Locale.ROOT);
        return Component.translatable(key).getString();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
