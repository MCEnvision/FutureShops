package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.PlayerShopCartState;
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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

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

    // Owner detail-panel section positions (set in initOwnerWidgets, read in renderDetailPanel)
    private int ownerInfoX, ownerInfoW;
    private int priceSecY, barterSecY, configSecY;
    private int sectionH;

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

    // Spec §8: Confirmation modal overlay
    private ConfirmationModal confirmationModal = null;

    // Debounced price/barter editing (Item 10)
    private long priceEditTimestamp = 0L;
    private long barterEditTimestamp = 0L;
    private long baseQtyEditTimestamp = 0L;
    private static final long DEBOUNCE_MS = 600L;

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
    private Button nbtAwareButton;
    private Button qtyMinusButton;
    private Button qtyPlusButton;
    private EditBox baseQtyBox;
    private Button setVisibleButton;
    private Button saveConfigButton;
    private Button deptButton;
    private Button descButton;
    private Button lDescButton;

    // Footer layout helpers — used to reflow visible buttons dynamically
    private final java.util.List<Button> actionRowButtons = new java.util.ArrayList<>();
    private final java.util.List<Button> linkRowButtons = new java.util.ArrayList<>();
    private int actionRowY;
    private int linkRowY;

    // Visitor controls
    private Button visitorBuyButton;
    private Button visitorBarterButton;
    private Button addToCartButton; // LGB#5: field reference for greying out

    public PlayerShopBlockScreen() {
        this(null);
    }

    public PlayerShopBlockScreen(Screen parent) {
        super(Component.literal("Player Shop"));
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

        addRenderableWidget(Button.builder(Component.literal("§c✕"), button -> onClose())
                .bounds(guiLeft + guiW - 24, guiTop + 6, 18, 14)
                .build());

        if (PlayerShopClientState.owner()) {
            configPanelHeight = compact ? 22 : 28;
            // Content starts after header + config + padding
            contentStartY = guiTop + headerHeight + configPanelHeight + 8;
            // contentAreaH is computed inside initOwnerWidgets() after bottom zone layout
            initOwnerWidgets();
        } else {
            configPanelHeight = 0;
            contentStartY = guiTop + headerHeight + 4;
            // Reserve space for visitor controls (18px) + status (14px) + gaps
            contentAreaH = guiH - (contentStartY - guiTop) - 40;
            contentAreaH = Math.max(40, contentAreaH);
            statusY = guiTop + guiH - 36;
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
        // ═══ Config Panel — single row: [Name:] [nameBox] [Single/Multi] [Same/Sep.] ... [Save] ═══
        int configY = guiTop + headerHeight + 2;
        int configRowY = configY + (compact ? 4 : 10);
        int nameBoxW = Math.min(120, guiW / 4);

        // Shop name editor
        shopNameBox = new EditBox(this.font, guiLeft + 44, configRowY, Math.max(60, nameBoxW), 14,
                Component.literal("Shop Name"));
        shopNameBox.setMaxLength(32);
        shopNameBox.setValue(PlayerShopClientState.shopName());
        addRenderableWidget(shopNameBox);

        // Single/Multi toggle — right of shop name
        boolean single = PlayerShopClientState.singleItemMode();
        int toggleX = guiLeft + 48 + Math.max(60, nameBoxW);
        int singleMultiW = compact ? 36 : 44;
        singleMultiButton = addRenderableWidget(Button.builder(
                        Component.literal(single ? "§eSingle" : "§aMulti"),
                        button -> {
                            boolean nowSingle = button.getMessage().getString().contains("Multi");
                            button.setMessage(Component.literal(nowSingle ? "§eSingle" : "§aMulti"));
                        })
                .tooltip(Tooltip.create(Component.literal("Toggle single-item / multi-listing mode")))
                .bounds(toggleX, configRowY, singleMultiW, 14)
                .build());

        // Barter storage toggle — right of Single/Multi
        boolean same = PlayerShopClientState.barterStorageSame();
        int barterToggleX = toggleX + singleMultiW + 4;
        int barterToggleW = compact ? 36 : 52;
        barterStorageButton = addRenderableWidget(Button.builder(
                        Component.literal(same ? (compact ? "§7Same" : "§7Same Chest") : (compact ? "§9Sep." : "§9Separate")),
                        button -> {
                            boolean nowSame = button.getMessage().getString().contains("Same");
                            button.setMessage(Component.literal(nowSame
                                    ? (compact ? "§9Sep." : "§9Separate")
                                    : (compact ? "§7Same" : "§7Same Chest")));
                        })
                .tooltip(Tooltip.create(Component.literal("Same chest or separate for barter items")))
                .bounds(barterToggleX, configRowY, barterToggleW, 14)
                .build());

        // Save Config — right-aligned on same row
        int saveW = compact ? 36 : 50;
        saveConfigButton = addRenderableWidget(Button.builder(
                        Component.literal(compact ? "§aSave" : "§aSave Config"), button -> saveConfig())
                .tooltip(Tooltip.create(Component.literal("Save shop name, mode, and barter storage settings")))
                .bounds(guiLeft + guiW - saveW - 16, configRowY, saveW, 14)
                .build());

        // ═══ Bottom zone: footer only — controls are now in the detail panel ═══
        int bh = 14;
        int gap = 2;
        int footerRowCount = 2;
        int bottomZoneH = footerRowCount * (bh + gap) + 6;

        contentAreaH = guiH - (contentStartY - guiTop) - bottomZoneH;
        contentAreaH = Math.max(40, contentAreaH);

        int guiBottom = guiTop + guiH;
        int curY = guiBottom - 4;
        curY -= bh;
        this.linkRowY = curY;
        curY -= gap;
        curY -= bh;
        this.actionRowY = curY;
        curY -= gap;

        statusY = guiTop + guiH - bottomZoneH - 14;

        // ═══ Detail panel inline sections — controls placed near the data they modify ═══
        int detailX = guiLeft + listingRailW + 16;
        int detailW = guiW - listingRailW - 24;
        boolean narrowDetail = detailW < 240;
        int previewW = narrowDetail ? Math.min(80, detailW - 20) : Math.min(130, detailW / 2);
        ownerInfoX = detailX + previewW + 10;
        ownerInfoW = detailW - previewW - 16;
        boolean sideBySide = ownerInfoW >= 90;
        if (!sideBySide) {
            ownerInfoX = detailX + 8;
            ownerInfoW = detailW - 16;
        }
        sectionH = compact ? 32 : 42;
        int secGap = compact ? 2 : 4;
        int pmW = 14;

        // ═══ Section 1: Pricing — [Mode] [-] [$price] [+] ═══
        priceSecY = contentStartY + (sideBySide ? 8 : (compact ? 62 : 82));
        int ctrlY1 = priceSecY + (compact ? 14 : 18);
        int cx = ownerInfoX + 4;
        int modeW = Math.max(28, this.font.width("Mode") + 8);
        toggleModeButton = addRenderableWidget(Button.builder(Component.literal("Mode"), button -> sendAction("TOGGLE_MODE", 0))
                .tooltip(Tooltip.create(Component.literal("Cycle: Money → Barter → Both → M+B")))
                .bounds(cx, ctrlY1, modeW, bh).build());
        cx += modeW + gap;
        priceMinusButton = addRenderableWidget(Button.builder(Component.literal("-"), button -> adjustPrice(-100))
                .tooltip(Tooltip.create(Component.literal("Decrease price")))
                .bounds(cx, ctrlY1, pmW, bh).build());
        cx += pmW + 1;
        int priceBoxW = Math.max(36, Math.min(60, ownerInfoW / 3));
        priceBox = new EditBox(this.font, cx, ctrlY1, priceBoxW, bh, Component.literal("Price"));
        priceBox.setMaxLength(10);
        priceBox.setValue(currentPriceText());
        priceBox.setResponder(value -> priceEditTimestamp = System.currentTimeMillis());
        addRenderableWidget(priceBox);
        cx += priceBoxW + 1;
        pricePlusButton = addRenderableWidget(Button.builder(Component.literal("+"), button -> adjustPrice(100))
                .tooltip(Tooltip.create(Component.literal("Increase price")))
                .bounds(cx, ctrlY1, pmW, bh).build());

        // ═══ Section 2: Barter — [Set] [-] [count] [+] ═══
        barterSecY = priceSecY + sectionH + secGap;
        int ctrlY2 = barterSecY + (compact ? 14 : 18);
        cx = ownerInfoX + 4;
        int setW = Math.max(22, this.font.width("Set") + 8);
        barterSetButton = addRenderableWidget(Button.builder(Component.literal("§9Set"), button -> sendAction("SET_BARTER_MAINHAND", currentBarterCount()))
                .tooltip(Tooltip.create(Component.literal("Set barter item from held item")))
                .bounds(cx, ctrlY2, setW, bh).build());
        cx += setW + gap;
        barterMinusButton = addRenderableWidget(Button.builder(Component.literal("-"), button -> adjustBarterCount(-1))
                .tooltip(Tooltip.create(Component.literal("Decrease barter count")))
                .bounds(cx, ctrlY2, pmW, bh).build());
        cx += pmW + 1;
        int barterBoxW = Math.max(24, Math.min(40, ownerInfoW / 4));
        barterCountBox = new EditBox(this.font, cx, ctrlY2, barterBoxW, bh, Component.literal("Qty"));
        barterCountBox.setMaxLength(4);
        barterCountBox.setValue(String.valueOf(currentBarterCount()));
        barterCountBox.setResponder(value -> barterEditTimestamp = System.currentTimeMillis());
        addRenderableWidget(barterCountBox);
        cx += barterBoxW + 1;
        barterPlusButton = addRenderableWidget(Button.builder(Component.literal("+"), button -> adjustBarterCount(1))
                .tooltip(Tooltip.create(Component.literal("Increase barter count")))
                .bounds(cx, ctrlY2, pmW, bh).build());

        // ═══ Section 3: Config — [Q-] [qty] [Q+] [NBT] [Vis] ═══
        configSecY = barterSecY + sectionH + secGap;
        int ctrlY3 = configSecY + (compact ? 14 : 18);
        cx = ownerInfoX + 4;
        int qLabelW = Math.max(18, this.font.width("Q-") + 6);
        qtyMinusButton = addRenderableWidget(Button.builder(Component.literal("§6Q-"), button -> adjustBaseQty(-1))
                .tooltip(Tooltip.create(Component.literal("Decrease base quantity")))
                .bounds(cx, ctrlY3, qLabelW, bh).build());
        cx += qLabelW + 1;
        int bqBoxW = Math.max(24, Math.min(36, ownerInfoW / 5));
        baseQtyBox = new EditBox(this.font, cx, ctrlY3, bqBoxW, bh, Component.literal("BQ"));
        baseQtyBox.setMaxLength(4);
        baseQtyBox.setValue(String.valueOf(currentBaseQty()));
        baseQtyBox.setResponder(value -> baseQtyEditTimestamp = System.currentTimeMillis());
        addRenderableWidget(baseQtyBox);
        cx += bqBoxW + 1;
        qtyPlusButton = addRenderableWidget(Button.builder(Component.literal("§6Q+"), button -> adjustBaseQty(1))
                .tooltip(Tooltip.create(Component.literal("Increase base quantity")))
                .bounds(cx, ctrlY3, qLabelW, bh).build());
        cx += qLabelW + gap + 2;
        int visW = Math.max(30, this.font.width("\uD83D\uDC41 Vis") + 8);
        setVisibleButton = addRenderableWidget(Button.builder(Component.literal("§e\uD83D\uDC41 Vis"), button -> {
                    int idx = PlayerShopClientState.selectedListingIndex();
                    sendAction("SELECT_VISIBLE_LISTING", idx);
                })
                .tooltip(Tooltip.create(Component.literal("Featured listing (single-item mode)")))
                .bounds(cx, ctrlY3, visW, bh).build());
        cx += visW + gap;
        // NBT toggle — placed on config row under pricing mode
        int nbtW = Math.max(24, this.font.width("NBT") + 8);
        nbtAwareButton = addRenderableWidget(Button.builder(Component.literal("NBT"), button -> sendAction("TOGGLE_NBT_AWARE", 0))
                .tooltip(Tooltip.create(Component.literal("Toggle NBT matching")))
                .bounds(cx, ctrlY3, nbtW, bh).build());

        // ═══ Footer: action buttons on actionRowY, link buttons on linkRowY ═══
        // Buttons are tracked in lists for dynamic reflowing when some are hidden
        actionRowButtons.clear();
        linkRowButtons.clear();
        int bx = guiLeft + 4;

        // Action buttons — initial positions are placeholders; reflowFooterButtons() sets real positions
        int addBtnW = this.font.width("+ Add") + 12;
        addListingButton = addRenderableWidget(Button.builder(Component.literal("§a+ Add"), button -> sendAction("ADD_LISTING_MAINHAND", 0))
                .tooltip(Tooltip.create(Component.literal("Add held item as a new listing")))
                .bounds(bx, actionRowY, addBtnW, bh).build());
        actionRowButtons.add(addListingButton);

        int delBtnW = this.font.width("- Del") + 12;
        removeListingButton = addRenderableWidget(Button.builder(Component.literal("§c- Del"), button -> sendAction("REMOVE_LISTING", 0))
                .tooltip(Tooltip.create(Component.literal("Remove the selected listing")))
                .bounds(bx, actionRowY, delBtnW, bh).build());
        actionRowButtons.add(removeListingButton);

        int promoBtnW = this.font.width("Promo") + 12;
        promoButton = addRenderableWidget(Button.builder(Component.literal("§6Promo"), button -> this.minecraft.setScreen(new PromoEditorModalScreen(this)))
                .tooltip(Tooltip.create(Component.literal("Configure promo / discount for selected listing")))
                .bounds(bx, actionRowY, promoBtnW, bh).build());
        actionRowButtons.add(promoButton);

        int collectBtnW = this.font.width("Collect") + 12;
        claimButton = addRenderableWidget(Button.builder(Component.literal("§aCollect"), button -> sendAction("CLAIM_SETTLEMENT", 0))
                .tooltip(Tooltip.create(Component.literal("Collect pending settlement revenue")))
                .bounds(bx, actionRowY, collectBtnW, bh).build());
        actionRowButtons.add(claimButton);

        int histBtnW = this.font.width("Hist") + 12;
        historyButton = addRenderableWidget(Button.builder(Component.literal("Hist"), button -> this.minecraft.setScreen(new SettlementHistoryScreen(this)))
                .tooltip(Tooltip.create(Component.literal("View settlement history")))
                .bounds(bx, actionRowY, histBtnW, bh).build());
        actionRowButtons.add(historyButton);

        int deptBtnW = this.font.width("Dept") + 12;
        deptButton = addRenderableWidget(Button.builder(Component.literal("§6Dept"), button -> this.minecraft.setScreen(new DepartmentPickerScreen(this)))
                .tooltip(Tooltip.create(Component.literal("Set department category for selected listing")))
                .bounds(bx, actionRowY, deptBtnW, bh).build());
        actionRowButtons.add(deptButton);

        // Description button — registers pending desc on server, then closes + prompts chat
        int descBtnW = this.font.width("Desc") + 12;
        descButton = addRenderableWidget(Button.builder(Component.literal("§bDesc"), button -> {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        sendAction("PENDING_DESC", 0);
                        this.minecraft.player.displayClientMessage(
                                Component.literal("§e➤ Type in chat: §f/desc <your description> §7(supports §r§lcolor §7§ocodes§7)"), false);
                        onClose();
                    }
                })
                .tooltip(Tooltip.create(Component.literal("Set a shop description (closes screen, type /desc in chat)")))
                .bounds(bx, actionRowY, descBtnW, bh).build());
        actionRowButtons.add(descButton);

        // Listing description button
        int lDescBtnW = this.font.width("L.Desc") + 12;
        lDescButton = addRenderableWidget(Button.builder(Component.literal("§3L.Desc"), button -> {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        sendAction("PENDING_LISTING_DESC", 0);
                        this.minecraft.player.displayClientMessage(
                                Component.literal("§e➤ Type in chat: §f/desc <listing description> §7(applies to selected listing)"), false);
                        onClose();
                    }
                })
                .tooltip(Tooltip.create(Component.literal("Set a description for the selected listing (closes screen, type /desc in chat)")))
                .bounds(bx, actionRowY, lDescBtnW, bh).build());
        actionRowButtons.add(lDescButton);

        // Link buttons
        int linkBtnW = this.font.width("Link") + 12;
        linkButton = addRenderableWidget(Button.builder(Component.literal("Link"), button -> sendAction("LINK_LOOKING", 0))
                .tooltip(Tooltip.create(Component.literal("Link main storage — look at a chest first")))
                .bounds(bx, linkRowY, linkBtnW, bh).build());
        linkRowButtons.add(linkButton);

        int unlkBtnW = this.font.width("Unlk") + 12;
        unlinkButton = addRenderableWidget(Button.builder(Component.literal("Unlk"), button -> sendAction("UNLINK", 0))
                .tooltip(Tooltip.create(Component.literal("Unlink main storage")))
                .bounds(bx, linkRowY, unlkBtnW, bh).build());
        linkRowButtons.add(unlinkButton);

        int blnkBtnW = this.font.width("B.Lnk") + 12;
        linkBarterButton = addRenderableWidget(Button.builder(Component.literal("§6B.Lnk"), button -> sendAction("LINK_BARTER_LOOKING", 0))
                .tooltip(Tooltip.create(Component.literal("Link barter storage — look at a chest first")))
                .bounds(bx, linkRowY, blnkBtnW, bh).build());
        linkRowButtons.add(linkBarterButton);

        int bulkBtnW = this.font.width("B.Ulk") + 12;
        unlinkBarterButton = addRenderableWidget(Button.builder(Component.literal("§6B.Ulk"), button -> sendAction("UNLINK_BARTER", 0))
                .tooltip(Tooltip.create(Component.literal("Unlink barter storage")))
                .bounds(bx, linkRowY, bulkBtnW, bh).build());
        linkRowButtons.add(unlinkBarterButton);

        // Initial reflow
        reflowFooterButtons();
    }

    private void initVisitorWidgets() {
        // Item 4 & 9: Back button when navigated from another screen — placed above header to avoid overlap
        if (parent != null) {
            addRenderableWidget(Button.builder(Component.literal("§7← Back"), button -> onClose())
                    .bounds(guiLeft + 6, Math.max(2, guiTop - 16), 44, 14)
                    .build());
        }

        int y = guiTop + guiH - 18;
        // Adaptive: squeeze controls toward right edge, scale to available width
        int availW = guiW - 20;
        boolean tightFit = availW < 320;
        int btnGap = tightFit ? 1 : 4;

        // Position from right edge inward
        int rightEdge = guiLeft + guiW - 8;

        // Cart button (rightmost)
        int cartCount = PlayerShopCartState.size();
        String cartLabel = cartCount > 0 ? "§6🛒 " + cartCount : "§7🛒";
        int cartBtnW = tightFit ? 28 : 40;
        addRenderableWidget(Button.builder(Component.literal(cartLabel), button -> {
                    if (this.minecraft != null)
                        this.minecraft.setScreen(new PlayerShopCartScreen(this));
                })
                .bounds(rightEdge - cartBtnW, y, cartBtnW, 14).build());
        rightEdge -= cartBtnW + btnGap;

        // Add to Cart
        int cartAddW = tightFit ? 32 : 42;
        addToCartButton = addRenderableWidget(Button.builder(Component.literal("§e+ Cart"), button -> addToCart())
                .bounds(rightEdge - cartAddW, y, cartAddW, 14).build());
        rightEdge -= cartAddW + btnGap;

        // Barter button — LGB#6: pass current quantity
        int barterBtnW = tightFit ? 42 : 58;
        visitorBarterButton = addRenderableWidget(Button.builder(Component.literal("§9⚒ Barter"), button -> {
                    if (this.minecraft != null)
                        this.minecraft.setScreen(new PlayerShopBarterScreen(this, getQuantity()));
                })
                .bounds(rightEdge - barterBtnW, y, barterBtnW, 14).build());
        rightEdge -= barterBtnW + btnGap;

        // Buy button (money)
        int buyBtnW = tightFit ? 40 : 56;
        visitorBuyButton = addRenderableWidget(Button.builder(Component.literal("§a$ Buy"), button -> showBuyConfirmation(getQuantity()))
                .bounds(rightEdge - buyBtnW, y, buyBtnW, 14).build());
        rightEdge -= buyBtnW + btnGap + 4;

        // Quantity: - [box] + Max — fill remaining space from left
        int qtyX = Math.max(guiLeft + 8, rightEdge - 100);
        addRenderableWidget(Button.builder(Component.literal("-"), button -> setQuantity(getQuantity() - 1))
                .bounds(qtyX, y, 14, 14).build());
        quantityBox = new EditBox(this.font, qtyX + 16, y, 32, 14, Component.literal("Qty"));
        quantityBox.setValue("1");
        quantityBox.setMaxLength(4);
        quantityBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        // Don't rewrite the field mid-type; clamp is applied at getQuantity() call sites.
        quantityBox.setResponder(value -> { /* no-op; clamp on use */ });
        addRenderableWidget(quantityBox);
        addRenderableWidget(Button.builder(Component.literal("+"), button -> {
                    if (hasShiftDown()) setQuantity(resolveMaxQuantity());
                    else setQuantity(getQuantity() + 1);
                })
                .tooltip(Tooltip.create(Component.literal("Shift+Click: Max")))
                .bounds(qtyX + 50, y, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("Max"), button -> setQuantity(resolveMaxQuantity()))
                .bounds(qtyX + 66, y, 28, 14).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Reset hover tooltip
        hoveredItemId = null;
        hoveredNbtJson = null;

        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);

        int accentColor = PlayerShopClientState.owner() ? ShopColors.ACCENT_CURRENCY : ShopColors.ACCENT_PRIMARY;
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + 2, accentColor);

        renderHeader(graphics);
        if (PlayerShopClientState.owner()) {
            renderConfigPanel(graphics);
        }

        boolean singleItemVisitor = !PlayerShopClientState.owner() && PlayerShopClientState.listings().size() == 1;
        if (singleItemVisitor) {
            renderSingleItemDetail(graphics, mouseX, mouseY);
        } else {
            renderListingRail(graphics, mouseX, mouseY);
            renderDetailPanel(graphics, mouseX, mouseY);
        }

        renderStatus(graphics);
        syncButtonStates();

        // Update owner text fields to current listing data
        if (PlayerShopClientState.owner()) {
            syncOwnerFields();
            tickDebouncedEdits();
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        // Render advanced tooltip last (Item 6) — on top of everything
        if (hoveredItemId != null) {
            ShopUiUtil.renderItemTooltip(graphics, this.font, hoveredItemId,
                    hoveredNbtJson != null ? hoveredNbtJson : "", mouseX, mouseY);
        }

        // Spec §8: Render confirmation modal on top of everything
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
            ShopUiUtil.renderPlayerFace(graphics, PlayerShopClientState.ownerUuid(), hx + 4, hy + 4, hh - 8);
            String title = PlayerShopClientState.owner() ? "§6⚡ Manage Shop" : "§fPlayer Shop";
            graphics.drawString(this.font, title, hx + hh, hy + 4, ShopColors.TEXT_PRIMARY, false);
            String shopName = PlayerShopClientState.shopName().isBlank()
                    ? PlayerShopClientState.ownerName() + "'s Shop"
                    : PlayerShopClientState.shopName();
            graphics.drawString(this.font, this.font.plainSubstrByWidth("§7" + shopName, hw / 2 - hh), hx + hh, hy + 14, ShopColors.TEXT_SECONDARY, false);
            // Franchise badge (compact)
            String compactFranchise = PlayerShopClientState.franchiseName();
            if (!compactFranchise.isBlank()) {
                ShopUiUtil.drawChip(graphics, this.font, hx + hw / 2, hy + 4,
                        "⚑ " + this.font.plainSubstrByWidth(compactFranchise, 60),
                        ShopColors.BG_PANEL, ShopColors.ACCENT_PURPLE, ShopColors.ACCENT_PURPLE);
            }
            // Description in compact header (truncated)
            String compactDesc = PlayerShopClientState.description();
            if (!compactDesc.isBlank()) {
                graphics.drawString(this.font, this.font.plainSubstrByWidth("§o" + compactDesc, hw / 2 - hh), hx + hh + this.font.width(this.font.plainSubstrByWidth("§7" + shopName, hw / 2 - hh)) + 6, hy + 14, ShopColors.TEXT_SECONDARY, false);
            }
            // Link chip — right aligned
            ShopUiUtil.drawChip(graphics, this.font, hx + hw - 100, hy + 6,
                    this.font.plainSubstrByWidth(
                            PlayerShopClientState.linked() ? "✓ Linked" : "⚠ No Link", 80),
                    ShopColors.BG_PANEL,
                    PlayerShopClientState.linked() ? ShopColors.SUCCESS : ShopColors.ERROR,
                    PlayerShopClientState.linked() ? ShopColors.SUCCESS : ShopColors.ERROR);
        } else {
            // Normal header: face + two-line title + franchise/desc mid | link chip + revenue right
            ShopUiUtil.renderPlayerFace(graphics, PlayerShopClientState.ownerUuid(), hx + 8, hy + 8, 34);
            String title = PlayerShopClientState.owner() ? "§6⚡ Manage Your Shop" : "§fBrowse Player Shop";
            String shopName = PlayerShopClientState.shopName().isBlank()
                    ? PlayerShopClientState.ownerName() + "'s Shop"
                    : PlayerShopClientState.shopName();

            // Reserve right region for link chip + revenue. Narrows responsively so the mid
            // region (franchise chip + description) cannot collide with it.
            int rightRegionW = Math.min(140, Math.max(90, hw / 3));
            int rightRegionX = hx + hw - rightRegionW;

            int centerX = hx + 50;
            int centerMaxW = Math.max(60, rightRegionX - centerX - 6);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(title, centerMaxW), centerX, hy + 8, ShopColors.TEXT_PRIMARY, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth("§7" + shopName, centerMaxW), centerX, hy + 20, ShopColors.TEXT_SECONDARY, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth("Owner: " + PlayerShopClientState.ownerName(), Math.min(130, centerMaxW)), centerX, hy + 32, ShopColors.TEXT_SECONDARY, false);

            // Mid region (after owner text) — franchise chip + description, only when there's room
            int midX = hx + 190;
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
                graphics.drawString(this.font, this.font.plainSubstrByWidth("§7§o" + normalDesc, midAvailW), midX, hy + 32, ShopColors.TEXT_SECONDARY, false);
            }

            // Right region — link chip + revenue, both width-capped to rightRegionW
            int chipTextCap = Math.max(20, rightRegionW - 10);
            ShopUiUtil.drawChip(graphics, this.font, rightRegionX, hy + 8,
                    this.font.plainSubstrByWidth(
                            PlayerShopClientState.linked() ? "✓ Storage Linked" : "⚠ Needs Link", chipTextCap),
                    ShopColors.BG_PANEL,
                    PlayerShopClientState.linked() ? ShopColors.SUCCESS : ShopColors.ERROR,
                    PlayerShopClientState.linked() ? ShopColors.SUCCESS : ShopColors.ERROR);
            String revenue = "Pending " + ShopUiUtil.formatMinorUnits(PlayerShopClientState.pendingSettlementMinor())
                    + " • Total " + ShopUiUtil.formatMinorUnits(PlayerShopClientState.lifetimeRevenueMinor());
            graphics.drawString(this.font, this.font.plainSubstrByWidth(revenue, rightRegionW - 4), rightRegionX, hy + 30, ShopColors.TEXT_PRICE, false);
        }
    }

    private void renderConfigPanel(GuiGraphics graphics) {
        int cx = guiLeft + 8;
        int cy = guiTop + headerHeight;
        int cw = guiW - 16;
        int ch = configPanelHeight;
        ShopUiUtil.renderCard(graphics, cx, cy, cw, ch);
        graphics.fill(cx, cy, cx + cw, cy + 2, ShopColors.ACCENT_CURRENCY);
        int labelY = cy + (compact ? 6 : 12);
        graphics.drawString(this.font, "§7Name:", cx + 6, labelY, ShopColors.TEXT_FAINT, false);
    }

    private void renderListingRail(GuiGraphics graphics, int mouseX, int mouseY) {
        int railX = guiLeft + 8;
        int railY = contentStartY;
        int railW = listingRailW;
        int railH = contentAreaH;
        ShopUiUtil.renderCard(graphics, railX, railY, railW, railH);
        graphics.fill(railX, railY, railX + railW, railY + 2, ShopColors.ACCENT_PRIMARY);
        graphics.drawString(this.font, "§lListings", railX + 8, railY + 6, ShopColors.TEXT_STRONG, false);

        List<PlayerShopListingData> listings = PlayerShopClientState.listings();
        String countText = listings.isEmpty() ? "§7No items yet" : "§7" + listings.size() + " items";
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

            // Item icon — NBT-aware only when listing is nbtAware and has non-default NBT
            if (listing.nbtAware() && ShopUiUtil.hasNonDefaultNbt(listing.itemId(), listing.nbtJson())) {
                ShopUiUtil.renderItemIconWithNbt(graphics, this.font, listing.itemId(), listing.nbtJson(), railX + 10, y + (cardH - 20) / 2);
            } else {
                ShopUiUtil.renderItemIcon(graphics, this.font, listing.itemId(), railX + 10, y + (cardH - 20) / 2);
            }

            // Item 6: detect icon hover for tooltip
            int iconY = y + (cardH - 20) / 2;
            if (mouseX >= railX + 10 && mouseX <= railX + 26 && mouseY >= iconY && mouseY <= iconY + 16) {
                hoveredItemId = listing.itemId();
                hoveredNbtJson = (listing.nbtAware() && ShopUiUtil.hasNonDefaultNbt(listing.itemId(), listing.nbtJson()))
                        ? listing.nbtJson() : "";
                hoveredMouseX = mouseX;
                hoveredMouseY = mouseY;
            }

            // Name — truncated to rail width
            int nameW = railW - 42;
            String name = this.font.plainSubstrByWidth(
                    ShopUiUtil.getItemDisplayNameWithNbtAndQty(listing.itemId(), listing.nbtJson(), listing.baseQuantity()),
                    nameW);
            graphics.drawString(this.font, name, railX + 30, y + 4, selected ? ShopColors.TEXT_STRONG : ShopColors.TEXT_MUTED, false);

            // Meta line — stock + mode
            String meta;
            if (listing.baseQuantity() == 0) {
                meta = "§c⚠ " + listing.stock() + " stk • " + prettyMode(listing.tradeMode());
            } else if (listing.baseQuantity() > 1 && !PlayerShopClientState.owner()) {
                // Show ×qty badge only for visitors; owners have Q-/Q+ controls
                meta = "×" + listing.baseQuantity() + " • " + listing.stock() + " stk • " + prettyMode(listing.tradeMode());
            } else {
                meta = listing.stock() + " stk • " + prettyMode(listing.tradeMode());
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
                    ShopUiUtil.drawChip(graphics, this.font, badgeX, y + cardH - 16, "NBT",
                            ShopColors.BG_PANEL, ShopColors.ACCENT_ORANGE, ShopColors.ACCENT_ORANGE);
                    badgeX += this.font.width("NBT") + 12;
                }
                if (PlayerShopClientState.owner() && PlayerShopClientState.singleItemMode() && listing.visible()) {
                    ShopUiUtil.drawChip(graphics, this.font, badgeX, y + cardH - 16, "👁",
                            ShopColors.BG_PANEL, ShopColors.ACCENT_CYAN, ShopColors.ACCENT_CYAN);
                }
            }

            // Promo badge at top-right of card
            if (listing.promo().configured()) {
                int percent = computeListingPromoPercent(listing);
                if (percent > 0) {
                    String badgeText = percent >= 100 ? "Free!" : "-" + percent + "%";
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
            graphics.drawCenteredString(this.font, "§7Select a listing", detailX + detailW / 2, detailY + detailH / 2, ShopColors.TEXT_FAINT);
            return;
        }

        // ═══ Adaptive layout: preview on left, info on right ═══
        boolean narrowDetail = detailW < 240;
        int previewW = narrowDetail ? Math.min(80, detailW - 20) : Math.min(130, detailW / 2);

        // Item preview — NBT-aware only when listing is nbtAware and has non-default NBT
        boolean detailHasRealNbt = listing.nbtAware() && ShopUiUtil.hasNonDefaultNbt(listing.itemId(), listing.nbtJson());
        if (detailHasRealNbt) {
            ShopUiUtil.renderLargeItemPreviewWithNbt(graphics, this.font, listing.itemId(), listing.nbtJson(), detailX + 6, detailY + 6, previewW);
        } else {
            ShopUiUtil.renderLargeItemPreview(graphics, this.font, listing.itemId(), detailX + 6, detailY + 6, previewW);
        }

        // Hover detection for tooltip on preview
        if (mouseX >= detailX + 6 && mouseX <= detailX + 6 + previewW && mouseY >= detailY + 6 && mouseY <= detailY + 76) {
            hoveredItemId = listing.itemId();
            hoveredNbtJson = detailHasRealNbt ? listing.nbtJson() : "";
        }

        // Item name below preview
        int nameY = detailY + (compact ? 62 : 82);
        String name = this.font.plainSubstrByWidth(
                ShopUiUtil.getItemDisplayNameWithNbtAndQty(listing.itemId(), listing.nbtJson(), listing.baseQuantity()),
                previewW);
        graphics.drawString(this.font, name, detailX + 8, nameY, ShopColors.TEXT_PRIMARY, false);

        // Description below name (gray) — prefer listing description, fall back to shop desc
        String listingDesc = listing.listingDescription();
        String shopDesc = PlayerShopClientState.description();
        String descDisplay = !listingDesc.isBlank() ? listingDesc : (!shopDesc.isBlank() ? shopDesc : "No description");
        graphics.drawString(this.font, this.font.plainSubstrByWidth("§7§o" + descDisplay, previewW),
                detailX + 8, nameY + 12, ShopColors.TEXT_SECONDARY, false);

        // Stock below description
        String stockStr = "Stock " + listing.stock() + (listing.stock() <= 16 ? " §c• low" : " §a• ok");
        graphics.drawString(this.font, this.font.plainSubstrByWidth(stockStr, previewW), detailX + 8, nameY + 24,
                listing.stock() <= 16 ? ShopColors.ERROR : ShopColors.SUCCESS, false);

        // Mode + effective price below stock
        String modeStr = prettyMode(listing.tradeMode());
        int modeColor = switch (listing.tradeMode().toUpperCase(Locale.ROOT)) {
            case "BARTER" -> ShopColors.TEXT_BARTER;
            case "MONEY_AND_BARTER" -> ShopColors.ACCENT_CURRENCY;
            case "BOTH" -> ShopColors.TEXT_PRIMARY;
            default -> ShopColors.TEXT_PRICE;
        };
        graphics.drawString(this.font, modeStr, detailX + 8, nameY + 36, modeColor, false);

        // Promo badge
        if (listing.promo().configured()) {
            int percent = computeListingPromoPercent(listing);
            String badge = percent >= 100 ? "Free!" : (percent > 0 ? "-" + percent + "%" : promoLabel(listing));
            ShopUiUtil.renderAnimatedDiscountBadge(graphics, this.font, detailX + 50, nameY + 54, badge);
        }

        // ═══ Right info panels ═══
        if (PlayerShopClientState.owner()) {
            // ── Owner view: section panels with inline controls ──
            // Section 1: Pricing
            ShopUiUtil.renderPanel(graphics, ownerInfoX, priceSecY, ownerInfoW, sectionH, ShopColors.SURFACE_RAISED, ShopColors.BORDER_SUBTLE);
            graphics.fill(ownerInfoX, priceSecY, ownerInfoX + 2, priceSecY + sectionH, ShopColors.ACCENT_CURRENCY);
            graphics.drawString(this.font, "§lPRICING", ownerInfoX + 6, priceSecY + 3, ShopColors.TEXT_FAINT, false);
            String effectivePrice = listing.effectiveUnitPriceMinor() <= 0 ? "§aFree" : ShopUiUtil.formatMinorUnits(listing.effectiveUnitPriceMinor());
            int epW = this.font.width(this.font.plainSubstrByWidth(effectivePrice, ownerInfoW / 2));
            graphics.drawString(this.font, this.font.plainSubstrByWidth(effectivePrice, ownerInfoW / 2),
                    ownerInfoX + ownerInfoW - epW - 4, priceSecY + 3, ShopColors.TEXT_CURRENCY, false);

            // Section 2: Barter
            ShopUiUtil.renderPanel(graphics, ownerInfoX, barterSecY, ownerInfoW, sectionH, ShopColors.SURFACE_RAISED, ShopColors.BORDER_SUBTLE);
            graphics.fill(ownerInfoX, barterSecY, ownerInfoX + 2, barterSecY + sectionH, ShopColors.TEXT_BARTER_SOFT);
            graphics.drawString(this.font, "§lBARTER", ownerInfoX + 6, barterSecY + 3, ShopColors.TEXT_BARTER_SOFT, false);
            if (listing.barterItemId() != null && !listing.barterItemId().isBlank()) {
                String barterLabel = listing.barterItemCount() + "× " + ShopUiUtil.getItemDisplayName(listing.barterItemId());
                String truncBarter = this.font.plainSubstrByWidth(barterLabel, ownerInfoW / 2);
                int blW = this.font.width(truncBarter);
                graphics.drawString(this.font, truncBarter, ownerInfoX + ownerInfoW - blW - 4, barterSecY + 3,
                        ShopColors.TEXT_BARTER_SOFT, false);
            }

            // Section 3: Config
            ShopUiUtil.renderPanel(graphics, ownerInfoX, configSecY, ownerInfoW, sectionH, ShopColors.SURFACE_RAISED, ShopColors.BORDER_SUBTLE);
            graphics.fill(ownerInfoX, configSecY, ownerInfoX + 2, configSecY + sectionH, ShopColors.ACCENT_CURRENCY);
            graphics.drawString(this.font, "§lCONFIG", ownerInfoX + 6, configSecY + 3, ShopColors.ACCENT_CURRENCY, false);
            // Status badges at bottom of config section — only department (NBT and qty are redundant with inline controls)
            if (!compact) {
                int badgeY = configSecY + sectionH - 14;
                int badgeX = ownerInfoX + 4;
                if (listing.department() != null && !listing.department().isBlank()) {
                    String deptClip = this.font.plainSubstrByWidth(listing.department(), ownerInfoW / 3);
                    ShopUiUtil.drawChip(graphics, this.font, badgeX, badgeY, deptClip,
                            ShopColors.BG_PANEL, ShopColors.ACCENT_PURPLE, ShopColors.ACCENT_PURPLE);
                }
            }

            // ── Held item preview (bottom-left of detail panel) ──
            if (this.minecraft != null && this.minecraft.player != null) {
                ItemStack heldItem = this.minecraft.player.getMainHandItem();
                if (!heldItem.isEmpty()) {
                    int heldY = detailY + detailH - 22;
                    int heldX = detailX + 6;
                    graphics.drawString(this.font, "§7Held:", heldX, heldY - 10, ShopColors.TEXT_SECONDARY, false);
                    ShopUiUtil.renderItemIcon(graphics, this.font,
                            net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(heldItem.getItem()).toString(),
                            heldX, heldY);
                    String heldName = this.font.plainSubstrByWidth(heldItem.getHoverName().getString(), previewW - 24);
                    graphics.drawString(this.font, "§f" + heldName, heldX + 20, heldY + 4, ShopColors.TEXT_PRIMARY, false);
                }
            }

        } else {
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
                graphics.drawString(this.font, "§lPRICING", infoX + 4, pricePanelY + 4, ShopColors.TEXT_FAINT, false);

                boolean hasMoney = !"BARTER".equalsIgnoreCase(listing.tradeMode());
                boolean hasBarter = !"MONEY".equalsIgnoreCase(listing.tradeMode());
                int py = pricePanelY + 16;

                if (hasMoney) {
                    graphics.drawString(this.font, this.font.plainSubstrByWidth("Base: " + ShopUiUtil.formatMinorUnits(listing.moneyPriceMinor()), infoW - 8),
                            infoX + 4, py, ShopColors.TEXT_SECONDARY, false);
                    py += 11;
                    String nowLabel = listing.effectiveUnitPriceMinor() <= 0 ? "§aNow: Free" : "§aNow: " + ShopUiUtil.formatMinorUnits(listing.effectiveUnitPriceMinor());
                    graphics.drawString(this.font, this.font.plainSubstrByWidth(nowLabel, infoW - 8),
                            infoX + 4, py, ShopColors.TEXT_PRICE, false);
                    py += 11;
                }
                if (hasBarter) {
                    String barter = this.font.plainSubstrByWidth(listing.barterItemCount() + "× " + ShopUiUtil.getItemDisplayName(listing.barterItemId()), infoW - 8);
                    graphics.drawString(this.font, barter, infoX + 4, py, ShopColors.TEXT_BARTER, false);
                    py += 11;
                    if (listing.baseBarterItemCount() > listing.barterItemCount()) {
                        String baseBarter = "§7§m" + listing.baseBarterItemCount() + "×";
                        graphics.drawString(this.font, baseBarter, infoX + 4, py, ShopColors.TEXT_SECONDARY, false);
                        py += 11;
                    }
                    graphics.drawString(this.font, "Owned: " + ShopUiUtil.countPlayerInventory(listing.barterItemId()),
                            infoX + 4, py, ShopColors.TEXT_SECONDARY, false);
                    py += 13;

                    // Barter item icon preview — only render if it fits within the pricing panel with margin
                    if (py + 18 <= pricePanelY + panelH - 4) {
                        ShopUiUtil.renderItemIcon(graphics, this.font, listing.barterItemId(), infoX + 4, py);
                        String barterName = this.font.plainSubstrByWidth("§9" + ShopUiUtil.getItemDisplayName(listing.barterItemId()), infoW - 28);
                        graphics.drawString(this.font, barterName, infoX + 24, py + 4, ShopColors.TEXT_BARTER, false);
                        // Hover detection for barter item icon
                        if (mouseX >= infoX + 4 && mouseX <= infoX + 20 && mouseY >= py && mouseY <= py + 16) {
                            hoveredItemId = listing.barterItemId();
                            hoveredNbtJson = "";
                        }
                    }
                }

                // Trade summary panel
                int summaryY = pricePanelY + panelH + 4;
                int summaryH = Math.min(panelH, detailH - panelH - 12);
                if (summaryH > 20) {
                    ShopUiUtil.renderPanel(graphics, infoX, summaryY, infoW, summaryH, ShopColors.SURFACE_RAISED, ShopColors.BORDER_SUBTLE);
                    graphics.fill(infoX, summaryY, infoX + infoW, summaryY + 1, ShopColors.ACCENT_PRIMARY);
                    graphics.drawString(this.font, "§lTRADE", infoX + 4, summaryY + 4, ShopColors.TEXT_FAINT, false);

                    if ("MONEY_AND_BARTER".equalsIgnoreCase(listing.tradeMode())) {
                        ShopUiUtil.drawWrappedString(graphics, this.font, Component.literal("§6Pay coins §7AND §9barter."),
                                infoX + 4, summaryY + 16, infoW - 8, ShopColors.TEXT_PRIMARY, 10);
                    } else if ("BOTH".equalsIgnoreCase(listing.tradeMode())) {
                        ShopUiUtil.drawWrappedString(graphics, this.font, Component.literal("Coins or barter items."),
                                infoX + 4, summaryY + 16, infoW - 8, ShopColors.TEXT_PRIMARY, 10);
                    } else if ("MONEY".equalsIgnoreCase(listing.tradeMode())) {
                        graphics.drawString(this.font, "Instant buy with balance.", infoX + 4, summaryY + 16, ShopColors.TEXT_PRIMARY, false);
                    } else {
                        String barterItemName = ShopUiUtil.getItemDisplayName(listing.barterItemId());
                        String saleItemName = ShopUiUtil.getItemDisplayNameWithQty(listing.itemId(), listing.baseQuantity());
                        String summary = this.font.plainSubstrByWidth(
                                listing.barterItemCount() + "× " + barterItemName + " per " + saleItemName,
                                infoW - 8);
                        graphics.drawString(this.font, summary, infoX + 4, summaryY + 16, ShopColors.TEXT_BARTER, false);
                    }
                    // Description for visitor — prefer listing desc, fall back to shop desc
                    String visitorListingDesc = listing.listingDescription();
                    String visitorDesc = !visitorListingDesc.isBlank() ? visitorListingDesc : PlayerShopClientState.description();
                    if (!visitorDesc.isBlank() && summaryH > 34) {
                        graphics.drawString(this.font, "§7§o" + this.font.plainSubstrByWidth(visitorDesc, infoW - 8),
                                infoX + 4, summaryY + summaryH - 12, ShopColors.TEXT_SECONDARY, false);
                    } else {
                        String promoStatus = listing.promo().configured() ? "§aPromo active" : "§7No promo";
                        if (summaryH > 34) {
                            graphics.drawString(this.font, promoStatus, infoX + 4, summaryY + summaryH - 12, ShopColors.TEXT_SECONDARY, false);
                        }
                    }
                }
            }

            // Visitor: total cost at bottom of detail
            if (!"BARTER".equalsIgnoreCase(listing.tradeMode())) {
                long total = listing.effectiveUnitPriceMinor() * getQuantity();
                String totalStr = total <= 0 ? "§6Total: §aFree" : "§6Total: §a" + ShopUiUtil.formatMinorUnits(total);
                if (listing.baseQuantity() > 1) {
                    totalStr += " §7(×" + listing.baseQuantity() + " ea)";
                }
                graphics.drawString(this.font, totalStr, detailX + 8, detailY + detailH - 18, ShopColors.TEXT_PRICE, false);
            }
            if (listing.department() != null && !listing.department().isBlank()) {
                ShopUiUtil.drawChip(graphics, this.font, detailX + 8, detailY + detailH - 6,
                        listing.department(), ShopColors.BG_PANEL, ShopColors.ACCENT_PURPLE, ShopColors.ACCENT_PURPLE);
            }
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
            hoveredNbtJson = listing.nbtAware() ? listing.nbtJson() : "";
        }

        // Name, owned count, total, stock — stacked at bottom of preview
        int bottomStackY = detailY + detailH - (compact ? 42 : 58);
        String dispName = this.font.plainSubstrByWidth(
                ShopUiUtil.getItemDisplayNameWithNbtAndQty(listing.itemId(), listing.nbtJson(), listing.baseQuantity()),
                previewW - 10);
        graphics.drawCenteredString(this.font, dispName, detailX + 8 + previewW / 2, bottomStackY, ShopColors.TEXT_PRIMARY);

        int owned = ShopUiUtil.countPlayerInventory(listing.itemId());
        graphics.drawCenteredString(this.font, "§7Own: " + owned, detailX + 8 + previewW / 2, bottomStackY + 12, ShopColors.TEXT_SECONDARY);

        if (!"BARTER".equalsIgnoreCase(listing.tradeMode())) {
            long total = listing.effectiveUnitPriceMinor() * getQuantity();
            String totalLabel = total <= 0 ? "§aFree" : "§a" + ShopUiUtil.formatMinorUnits(total);
            graphics.drawCenteredString(this.font, totalLabel, detailX + 8 + previewW / 2, bottomStackY + 24, ShopColors.TEXT_PRICE);
        }

        String stockStr = listing.stock() + (listing.stock() <= 16 ? " §c• low" : " §a• ok");
        graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(stockStr, previewW - 8),
                detailX + 8 + previewW / 2, bottomStackY + 36,
                listing.stock() <= 16 ? ShopColors.ERROR : ShopColors.SUCCESS);

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
        graphics.drawString(this.font, "Mode: " + prettyMode(listing.tradeMode()), infoX + 8, nextY, ShopColors.TEXT_SECONDARY, false);
        nextY += 14;

        boolean hasMoney = !"BARTER".equalsIgnoreCase(listing.tradeMode());
        boolean hasBarter = !"MONEY".equalsIgnoreCase(listing.tradeMode());

        if (hasMoney) {
            String priceLabel = listing.effectiveUnitPriceMinor() <= 0 ? "Buy: §aFree" : "Buy: " + ShopUiUtil.formatMinorUnits(listing.effectiveUnitPriceMinor());
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
                    String baseBarter = "§7§m" + listing.baseBarterItemCount() + " × " + ShopUiUtil.getItemDisplayName(barterId);
                    graphics.drawString(this.font, this.font.plainSubstrByWidth(baseBarter, infoW - 16),
                            infoX + 8, nextY, ShopColors.TEXT_SECONDARY, false);
                    nextY += 12;
                }
                String barterText = listing.barterItemCount() + " × " + ShopUiUtil.getItemDisplayName(barterId);
                graphics.drawString(this.font, this.font.plainSubstrByWidth("§9⚒ " + barterText, infoW - 16),
                        infoX + 8, nextY, ShopColors.TEXT_BARTER, false);
                nextY += 12;
                int ownedBarter = ShopUiUtil.countPlayerInventory(barterId);
                graphics.drawString(this.font, "Owned: " + ownedBarter,
                        infoX + 8, nextY, ownedBarter >= listing.barterItemCount() ? ShopColors.SUCCESS : ShopColors.ERROR, false);
                nextY += 14;

                // Barter item icon preview — only if space permits (avoid overlapping stock/dept below)
                int maxInfoY = detailY + detailH - 40;
                if (nextY + 18 <= maxInfoY) {
                    ShopUiUtil.renderItemIcon(graphics, this.font, barterId, infoX + 8, nextY);
                    String barterName = this.font.plainSubstrByWidth("§9" + ShopUiUtil.getItemDisplayName(barterId), infoW - 32);
                    graphics.drawString(this.font, barterName, infoX + 28, nextY + 4, ShopColors.TEXT_BARTER, false);
                    // Hover detection for barter item icon
                    if (mouseX >= infoX + 8 && mouseX <= infoX + 24 && mouseY >= nextY && mouseY <= nextY + 16) {
                        hoveredItemId = barterId;
                        hoveredNbtJson = "";
                    }
                    nextY += 20;
                }
            }
        }

        // Stock
        String stockLabel = listing.stock() > 0 ? "§a" + listing.stock() + " in stock" : "§cOut of stock";
        graphics.drawString(this.font, stockLabel, infoX + 8, nextY, ShopColors.TEXT_SECONDARY, false);
        nextY += 14;

        // Department
        if (listing.department() != null && !listing.department().isBlank()) {
            ShopUiUtil.drawChip(graphics, this.font, infoX + 8, nextY,
                    listing.department(), ShopColors.BG_PANEL, ShopColors.ACCENT_PURPLE, ShopColors.ACCENT_PURPLE);
            nextY += 16;
        }

        // Description for single-item visitor — prefer listing desc, fall back to shop desc
        String singleListingDesc = listing.listingDescription();
        String singleDesc = !singleListingDesc.isBlank() ? singleListingDesc : PlayerShopClientState.description();
        if (!singleDesc.isBlank()) {
            nextY += 4;
            graphics.fill(infoX + 8, nextY, infoX + infoW - 8, nextY + 1, ShopColors.BORDER_DEFAULT);
            nextY += 4;
            ShopUiUtil.drawWrappedString(graphics, this.font, Component.literal("§7§o" + singleDesc),
                    infoX + 8, nextY, infoW - 16, ShopColors.TEXT_SECONDARY, 10);
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
     * Item 25: Returns a buyer-friendly message for known result codes. Returns null for codes buyers shouldn't see.
     */
    private String buyerFriendlyMessage(String code) {
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "BOUGHT" -> "§a✓ Purchase complete!";
            case "NO_MONEY" -> "§cInsufficient balance.";
            case "OUT_OF_STOCK" -> "§cThis item is out of stock.";
            case "MISSING_BARTER_ITEMS" -> "§cYou don't have enough barter items.";
            case "STORAGE_FULL" -> "§cThe shop's storage is full.";
            case "INVALID_ITEM" -> "§cInvalid item.";
            case "NO_LINK" -> "§cShop storage not linked.";
            case "ROLLBACK" -> "§cTransaction failed. No items were taken.";
            case "UNCONFIGURED" -> "§cThis listing is not yet configured by the shop owner.";
            case "RS_NOT_CONTROLLER" -> "§cBlock not supported — link directly to the System Controller.";
            default -> null; // Hide all other codes from buyers
        };
    }

    private void saveConfig() {
        String name = shopNameBox == null ? "" : shopNameBox.getValue().trim();
        boolean singleItem = singleMultiButton != null && singleMultiButton.getMessage().getString().contains("Single");
        boolean barterSame = barterStorageButton != null && barterStorageButton.getMessage().getString().contains("Same");
        // LGB#24: Pass selected listing index for single-item mode
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopConfigPacket(
                PlayerShopClientState.shopPos(), name, singleItem, barterSame, PlayerShopClientState.selectedListingIndex()));
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
        if (nbtAwareButton != null) nbtAwareButton.active = hasSelection;
        if (baseQtyBox != null) baseQtyBox.setEditable(hasSelection);

        // Hide all detail-panel section controls when no listing is selected
        // Prevents overlap with "Select a listing" text, especially at UI scale 4
        if (toggleModeButton != null) toggleModeButton.visible = hasSelection;
        if (priceMinusButton != null) priceMinusButton.visible = hasSelection;
        if (pricePlusButton != null) pricePlusButton.visible = hasSelection;
        if (priceBox != null) priceBox.visible = hasSelection;
        if (barterSetButton != null) barterSetButton.visible = hasSelection;
        if (barterMinusButton != null) barterMinusButton.visible = hasSelection;
        if (barterPlusButton != null) barterPlusButton.visible = hasSelection;
        if (barterCountBox != null) barterCountBox.visible = hasSelection;
        if (qtyMinusButton != null) qtyMinusButton.visible = hasSelection;
        if (baseQtyBox != null) baseQtyBox.visible = hasSelection;
        if (qtyPlusButton != null) qtyPlusButton.visible = hasSelection;
        if (nbtAwareButton != null) nbtAwareButton.visible = hasSelection;
        if (removeListingButton != null) removeListingButton.visible = hasSelection;
        if (promoButton != null) promoButton.visible = hasSelection;
        if (deptButton != null) deptButton.visible = hasSelection;

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
            // LGB#5: Grey out +Cart when out of stock
            if (addToCartButton != null) {
                addToCartButton.active = inStock;
            }
        } else if (!PlayerShopClientState.owner()) {
            if (visitorBuyButton != null) { visitorBuyButton.active = false; visitorBuyButton.visible = true; }
            if (visitorBarterButton != null) { visitorBarterButton.active = false; visitorBarterButton.visible = true; }
            if (addToCartButton != null) { addToCartButton.active = false; }
        }

        // Show/hide barter link buttons based on barterStorageSame setting
        boolean showBarterLink = !PlayerShopClientState.barterStorageSame();
        if (linkBarterButton != null) linkBarterButton.visible = showBarterLink;
        if (unlinkBarterButton != null) unlinkBarterButton.visible = showBarterLink;

        // Item 2: Show "Set Visible" button only in single-item mode
        if (setVisibleButton != null) {
            setVisibleButton.visible = PlayerShopClientState.singleItemMode();
            setVisibleButton.active = PlayerShopClientState.singleItemMode() && hasSelection;
        }

        // L.Desc visibility tied to selection (like Del, Promo, Dept)
        if (lDescButton != null) lDescButton.visible = hasSelection;

        // Reflow footer buttons so visible ones fill space without gaps
        reflowFooterButtons();
    }

    /**
     * Dynamically repositions visible footer buttons so there are no gaps
     * when some buttons are hidden (e.g. no listing selected hides Del/Promo/Dept).
     */
    private void reflowFooterButtons() {
        int gap = 2;
        int bx = guiLeft + 4;
        for (Button btn : actionRowButtons) {
            if (btn.visible) {
                btn.setX(bx);
                btn.setY(actionRowY);
                bx += btn.getWidth() + gap;
            }
        }
        bx = guiLeft + 4;
        for (Button btn : linkRowButtons) {
            if (btn.visible) {
                btn.setX(bx);
                btn.setY(linkRowY);
                bx += btn.getWidth() + gap;
            }
        }
    }

    // LGB#17: Allow Enter key to apply text field values
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmationModal != null) {
            if (confirmationModal.keyPressed(keyCode)) return true;
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
        int railX = guiLeft + 8;
        int railY = contentStartY;
        int railW = listingRailW;
        int railH = contentAreaH;
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int railX = guiLeft + 8;
        int railY = contentStartY;
        int railW = listingRailW;
        int railH = contentAreaH;
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
        if (listing == null || listing.stock() <= 0) return;
        int qty = getQuantity();
        String shopName = PlayerShopClientState.shopName().isBlank()
                ? PlayerShopClientState.ownerName() + "'s Shop"
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
                listing.nbtJson(),
                listing.nbtAware());
        rebuildWidgets();
    }

    private void buy(int quantity) {
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopBuyPacket(
                PlayerShopClientState.shopPos(), PlayerShopClientState.selectedListingIndex(), quantity));
    }

    private void showBuyConfirmation(int quantity) {
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        if (listing == null) return;
        String itemName = ShopUiUtil.getItemDisplayNameWithNbtAndQty(listing.itemId(), listing.nbtJson(), listing.baseQuantity());
        long total = listing.effectiveUnitPriceMinor() * quantity;
        String totalStr = total <= 0 ? "Free" : ShopUiUtil.formatMinorUnits(total) + " " + ShopClientState.getCurrencyName();
        confirmationModal = new ConfirmationModal(
                "Confirm Purchase",
                java.util.List.of(
                        ConfirmationModal.SummaryLine.item(listing.itemId(), itemName + " ×" + quantity)
                ),
                "Total: " + totalStr,
                modal -> {
                    modal.setProcessing();
                    buy(quantity);
                },
                () -> confirmationModal = null
        );
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

    /**
     * Smart max: for MONEY = balance / price, for BARTER = inventory / cost,
     * for BOTH = max of the two, for MONEY_AND_BARTER = min of the two (need both).
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
        if ("MONEY_AND_BARTER".equals(mode)) {
            affordable = Math.min(maxMoney, maxBarter);
        } else if ("BOTH".equals(mode)) {
            affordable = Math.max(maxMoney, maxBarter);
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
            case "FLAT" -> "-$" + ShopUiUtil.formatMinorUnits(
                    Math.round(listing.promo().promoValue() * Math.pow(10, ShopClientState.getCurrencyDecimals())));
            case "FLASH" -> "flash";
            default -> "sale";
        };
    }

    // LGB#11: Use short abbreviations for the listing rail to avoid text cut-off
    private String prettyMode(String mode) {
        if (mode == null || mode.isBlank()) return "§aMoney";
        return switch (mode.toUpperCase(Locale.ROOT)) {
            case "BARTER" -> "§9Barter";
            case "BOTH" -> "§aMoney§7/§9Barter";
            case "MONEY_AND_BARTER" -> "§6M+B";
            default -> "§aMoney";
        };
    }

    private String localizeResultCode(String code) {
        String key = "gui.futureshops.player_shop.result." + code.toLowerCase(Locale.ROOT);
        return Component.translatable(key).getString();
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
