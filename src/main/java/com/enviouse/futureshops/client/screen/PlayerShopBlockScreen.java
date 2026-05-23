package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.PlayerShopCartState;
import com.enviouse.futureshops.client.PlayerShopClientState;
import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.PlayerShopListingData;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SPlayerShopActionPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopBuyPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopBuybackConfigPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopConfigPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
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
    // Backing state for the two config toggles. Pre-i18n the code inferred state by
    // parsing the button message ("contains("Multi")"), which breaks under translation.
    // Keep the authoritative value here and resync button messages via helpers.
    private boolean configSingleMode;
    private boolean configBarterSame;
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
    private Button visitorSellButton;
    private Button addToCartButton; // LGB#5: field reference for greying out

    // Owner buyback controls
    private Button dirButton;
    private Button buybackPriceButton;
    private Button buybackCapButton;
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

        addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.close"), button -> onClose())
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
                Component.translatable("gui.futureshops.player_shop_block.config.shop_name_narration"));
        shopNameBox.setMaxLength(32);
        shopNameBox.setValue(PlayerShopClientState.shopName());
        addRenderableWidget(shopNameBox);

        // Single/Multi toggle — right of shop name
        configSingleMode = PlayerShopClientState.singleItemMode();
        int toggleX = guiLeft + 48 + Math.max(60, nameBoxW);
        int singleMultiW = compact ? 36 : 44;
        singleMultiButton = addRenderableWidget(Button.builder(
                        singleMultiMessage(),
                        button -> {
                            configSingleMode = !configSingleMode;
                            button.setMessage(singleMultiMessage());
                        })
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.config.mode_tooltip")))
                .bounds(toggleX, configRowY, singleMultiW, 14)
                .build());

        // Barter storage toggle — right of Single/Multi
        configBarterSame = PlayerShopClientState.barterStorageSame();
        int barterToggleX = toggleX + singleMultiW + 4;
        int barterToggleW = compact ? 36 : 52;
        barterStorageButton = addRenderableWidget(Button.builder(
                        barterStorageMessage(),
                        button -> {
                            configBarterSame = !configBarterSame;
                            button.setMessage(barterStorageMessage());
                        })
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.config.storage_tooltip")))
                .bounds(barterToggleX, configRowY, barterToggleW, 14)
                .build());

        // Save Config — right-aligned on same row
        int saveW = compact ? 36 : 50;
        saveConfigButton = addRenderableWidget(Button.builder(
                        Component.translatable(compact
                                ? "gui.futureshops.player_shop_block.config.save_short"
                                : "gui.futureshops.player_shop_block.config.save_long"),
                        button -> saveConfig())
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.config.save_tooltip")))
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
        String modeLabel = I18n.get("gui.futureshops.player_shop_block.detail.mode_btn");
        int modeW = Math.max(28, this.font.width(modeLabel) + 8);
        toggleModeButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.detail.mode_btn"), button -> sendAction("TOGGLE_MODE", 0))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.trade_mode.cycle_tooltip")))
                .bounds(cx, ctrlY1, modeW, bh).build());
        cx += modeW + gap;
        priceMinusButton = addRenderableWidget(Button.builder(Component.literal("-"), button -> adjustPrice(-100))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.detail.price_dec_tooltip")))
                .bounds(cx, ctrlY1, pmW, bh).build());
        cx += pmW + 1;
        int priceBoxW = Math.max(36, Math.min(60, ownerInfoW / 3));
        priceBox = new EditBox(this.font, cx, ctrlY1, priceBoxW, bh, Component.translatable("gui.futureshops.player_shop_block.detail.price_narration"));
        priceBox.setMaxLength(10);
        priceBox.setValue(currentPriceText());
        priceBox.setResponder(value -> priceEditTimestamp = System.currentTimeMillis());
        addRenderableWidget(priceBox);
        cx += priceBoxW + 1;
        pricePlusButton = addRenderableWidget(Button.builder(Component.literal("+"), button -> adjustPrice(100))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.detail.price_inc_tooltip")))
                .bounds(cx, ctrlY1, pmW, bh).build());

        // ═══ Section 2: Barter — [Set] [-] [count] [+] ═══
        barterSecY = priceSecY + sectionH + secGap;
        int ctrlY2 = barterSecY + (compact ? 14 : 18);
        cx = ownerInfoX + 4;
        String setLabel = I18n.get("gui.futureshops.player_shop_block.detail.barter_set");
        int setW = Math.max(22, this.font.width(setLabel) + 8);
        barterSetButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.detail.barter_set"), button -> sendAction("SET_BARTER_MAINHAND", currentBarterCount()))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.detail.barter_set_tooltip")))
                .bounds(cx, ctrlY2, setW, bh).build());
        cx += setW + gap;
        barterMinusButton = addRenderableWidget(Button.builder(Component.literal("-"), button -> adjustBarterCount(-1))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.detail.barter_dec_tooltip")))
                .bounds(cx, ctrlY2, pmW, bh).build());
        cx += pmW + 1;
        int barterBoxW = Math.max(24, Math.min(40, ownerInfoW / 4));
        barterCountBox = new EditBox(this.font, cx, ctrlY2, barterBoxW, bh, Component.translatable("gui.futureshops.player_shop_block.detail.qty_narration"));
        barterCountBox.setMaxLength(4);
        barterCountBox.setValue(String.valueOf(currentBarterCount()));
        barterCountBox.setResponder(value -> barterEditTimestamp = System.currentTimeMillis());
        addRenderableWidget(barterCountBox);
        cx += barterBoxW + 1;
        barterPlusButton = addRenderableWidget(Button.builder(Component.literal("+"), button -> adjustBarterCount(1))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.detail.barter_inc_tooltip")))
                .bounds(cx, ctrlY2, pmW, bh).build());

        // ═══ Section 3: Config — [Q-] [qty] [Q+] [NBT] [Vis] ═══
        configSecY = barterSecY + sectionH + secGap;
        int ctrlY3 = configSecY + (compact ? 14 : 18);
        cx = ownerInfoX + 4;
        String qMinusLabel = I18n.get("gui.futureshops.player_shop_block.detail.qty_dec");
        int qLabelW = Math.max(18, this.font.width(qMinusLabel) + 6);
        qtyMinusButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.detail.qty_dec"), button -> adjustBaseQty(-1))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.detail.qty_dec_tooltip")))
                .bounds(cx, ctrlY3, qLabelW, bh).build());
        cx += qLabelW + 1;
        int bqBoxW = Math.max(24, Math.min(36, ownerInfoW / 5));
        baseQtyBox = new EditBox(this.font, cx, ctrlY3, bqBoxW, bh, Component.translatable("gui.futureshops.player_shop_block.detail.bq_narration"));
        baseQtyBox.setMaxLength(4);
        baseQtyBox.setValue(String.valueOf(currentBaseQty()));
        baseQtyBox.setResponder(value -> baseQtyEditTimestamp = System.currentTimeMillis());
        addRenderableWidget(baseQtyBox);
        cx += bqBoxW + 1;
        qtyPlusButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.detail.qty_inc"), button -> adjustBaseQty(1))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.detail.qty_inc_tooltip")))
                .bounds(cx, ctrlY3, qLabelW, bh).build());
        cx += qLabelW + gap + 2;
        String visLabel = I18n.get("gui.futureshops.player_shop_block.detail.visible_btn");
        int visW = Math.max(30, this.font.width(visLabel) + 8);
        setVisibleButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.detail.visible_btn"), button -> {
                    int idx = PlayerShopClientState.selectedListingIndex();
                    sendAction("SELECT_VISIBLE_LISTING", idx);
                })
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.detail.visible_tooltip")))
                .bounds(cx, ctrlY3, visW, bh).build());
        cx += visW + gap;
        // NBT toggle — placed on config row under pricing mode
        String nbtLabel = I18n.get("gui.futureshops.player_shop_block.detail.nbt_btn");
        int nbtW = Math.max(24, this.font.width(nbtLabel) + 8);
        nbtAwareButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.detail.nbt_btn"), button -> sendAction("TOGGLE_NBT_AWARE", 0))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.detail.nbt_tooltip")))
                .bounds(cx, ctrlY3, nbtW, bh).build());

        // ═══ Footer: action buttons on actionRowY, link buttons on linkRowY ═══
        // Buttons are tracked in lists for dynamic reflowing when some are hidden
        actionRowButtons.clear();
        linkRowButtons.clear();
        int bx = guiLeft + 4;

        // Action buttons — initial positions are placeholders; reflowFooterButtons() sets real positions
        String addLabel = I18n.get("gui.futureshops.player_shop_block.footer.add");
        int addBtnW = this.font.width(addLabel) + 12;
        addListingButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.footer.add"), button -> sendAction("ADD_LISTING_MAINHAND", 0))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.add_tooltip")))
                .bounds(bx, actionRowY, addBtnW, bh).build());
        actionRowButtons.add(addListingButton);

        String delLabel = I18n.get("gui.futureshops.player_shop_block.footer.del");
        int delBtnW = this.font.width(delLabel) + 12;
        removeListingButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.footer.del"), button -> sendAction("REMOVE_LISTING", 0))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.del_tooltip")))
                .bounds(bx, actionRowY, delBtnW, bh).build());
        actionRowButtons.add(removeListingButton);

        String promoLabel = I18n.get("gui.futureshops.player_shop_block.footer.promo");
        int promoBtnW = this.font.width(promoLabel) + 12;
        promoButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.footer.promo"), button -> this.minecraft.setScreen(new PromoEditorModalScreen(this)))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.promo_tooltip")))
                .bounds(bx, actionRowY, promoBtnW, bh).build());
        actionRowButtons.add(promoButton);

        String collectLabel = I18n.get("gui.futureshops.player_shop_block.footer.collect");
        int collectBtnW = this.font.width(collectLabel) + 12;
        claimButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.footer.collect"), button -> sendAction("CLAIM_SETTLEMENT", 0))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.collect_tooltip")))
                .bounds(bx, actionRowY, collectBtnW, bh).build());
        actionRowButtons.add(claimButton);

        String histLabel = I18n.get("gui.futureshops.player_shop_block.footer.hist");
        int histBtnW = this.font.width(histLabel) + 12;
        historyButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.footer.hist"), button -> this.minecraft.setScreen(new SettlementHistoryScreen(this)))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.hist_tooltip")))
                .bounds(bx, actionRowY, histBtnW, bh).build());
        actionRowButtons.add(historyButton);

        String deptLabel = I18n.get("gui.futureshops.player_shop_block.footer.dept");
        int deptBtnW = this.font.width(deptLabel) + 12;
        deptButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.footer.dept"), button -> this.minecraft.setScreen(new DepartmentPickerScreen(this)))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.dept_tooltip")))
                .bounds(bx, actionRowY, deptBtnW, bh).build());
        actionRowButtons.add(deptButton);

        // Description button — registers pending desc on server, then closes + prompts chat
        String descLabel = I18n.get("gui.futureshops.player_shop_block.footer.desc");
        int descBtnW = this.font.width(descLabel) + 12;
        descButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.footer.desc"), button -> {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        sendAction("PENDING_DESC", 0);
                        this.minecraft.player.displayClientMessage(
                                Component.translatable("gui.futureshops.player_shop_block.chat.desc_prompt"), false);
                        onClose();
                    }
                })
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.desc_tooltip")))
                .bounds(bx, actionRowY, descBtnW, bh).build());
        actionRowButtons.add(descButton);

        // Listing description button
        String lDescLabel = I18n.get("gui.futureshops.player_shop_block.footer.ldesc");
        int lDescBtnW = this.font.width(lDescLabel) + 12;
        lDescButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.footer.ldesc"), button -> {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        sendAction("PENDING_LISTING_DESC", 0);
                        this.minecraft.player.displayClientMessage(
                                Component.translatable("gui.futureshops.player_shop_block.chat.ldesc_prompt"), false);
                        onClose();
                    }
                })
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.ldesc_tooltip")))
                .bounds(bx, actionRowY, lDescBtnW, bh).build());
        actionRowButtons.add(lDescButton);

        // Buyback / direction controls (owner-only). Labels reflect current per-listing state.
        String dirLabel = I18n.get("gui.futureshops.player_shop_block.footer.dir", currentDirection());
        int dirBtnW = this.font.width(dirLabel) + 12;
        dirButton = addRenderableWidget(Button.builder(
                        Component.literal(dirLabel),
                        button -> cycleDirection())
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.dir_tooltip")))
                .bounds(bx, actionRowY, dirBtnW, bh).build());
        actionRowButtons.add(dirButton);

        String bpLabel = I18n.get("gui.futureshops.player_shop_block.footer.buyback_price");
        int bpBtnW = this.font.width(bpLabel) + 12;
        buybackPriceButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.futureshops.player_shop_block.footer.buyback_price"),
                        button -> cycleBuybackPrice())
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.buyback_price_tooltip")))
                .bounds(bx, actionRowY, bpBtnW, bh).build());
        actionRowButtons.add(buybackPriceButton);

        String bcLabel = I18n.get("gui.futureshops.player_shop_block.footer.buyback_cap");
        int bcBtnW = this.font.width(bcLabel) + 12;
        buybackCapButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.futureshops.player_shop_block.footer.buyback_cap"),
                        button -> cycleBuybackCap())
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.buyback_cap_tooltip")))
                .bounds(bx, actionRowY, bcBtnW, bh).build());
        actionRowButtons.add(buybackCapButton);

        // Copy Config / Paste Config — clones all owner-editable shop data (name, description,
        // listings incl. promos + barter items + bundle outputs) into a server-side per-player
        // clipboard so the same catalogue can be stamped onto another shop block.
        String copyLabel = I18n.get("gui.futureshops.player_shop_block.footer.copy_config");
        int copyBtnW = this.font.width(copyLabel) + 12;
        Button copyConfigButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.futureshops.player_shop_block.footer.copy_config"),
                        button -> sendAction("COPY_CONFIG", 0))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.copy_config_tooltip")))
                .bounds(bx, actionRowY, copyBtnW, bh).build());
        actionRowButtons.add(copyConfigButton);

        String pasteLabel = I18n.get("gui.futureshops.player_shop_block.footer.paste_config");
        int pasteBtnW = this.font.width(pasteLabel) + 12;
        Button pasteConfigButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.futureshops.player_shop_block.footer.paste_config"),
                        button -> sendAction("PASTE_CONFIG", 0))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.paste_config_tooltip")))
                .bounds(bx, actionRowY, pasteBtnW, bh).build());
        actionRowButtons.add(pasteConfigButton);

        // Display-item controls: nudge the floating listing preview up/down
        // (step = 0.05 world units) and scale it bigger/smaller (step = 0.10×).
        // Per-shop, persists with the block entity, owner-only.
        Button dispUpButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.futureshops.player_shop_block.footer.disp_up"),
                        button -> sendAction("DISPLAY_Y_UP", 1))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.disp_up_tooltip")))
                .bounds(bx, actionRowY, this.font.width(I18n.get("gui.futureshops.player_shop_block.footer.disp_up")) + 12, bh).build());
        actionRowButtons.add(dispUpButton);

        Button dispDownButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.futureshops.player_shop_block.footer.disp_down"),
                        button -> sendAction("DISPLAY_Y_DOWN", 1))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.disp_down_tooltip")))
                .bounds(bx, actionRowY, this.font.width(I18n.get("gui.futureshops.player_shop_block.footer.disp_down")) + 12, bh).build());
        actionRowButtons.add(dispDownButton);

        Button dispBigButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.futureshops.player_shop_block.footer.disp_bigger"),
                        button -> sendAction("DISPLAY_SCALE_UP", 1))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.disp_bigger_tooltip")))
                .bounds(bx, actionRowY, this.font.width(I18n.get("gui.futureshops.player_shop_block.footer.disp_bigger")) + 12, bh).build());
        actionRowButtons.add(dispBigButton);

        Button dispSmallButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.futureshops.player_shop_block.footer.disp_smaller"),
                        button -> sendAction("DISPLAY_SCALE_DOWN", 1))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.disp_smaller_tooltip")))
                .bounds(bx, actionRowY, this.font.width(I18n.get("gui.futureshops.player_shop_block.footer.disp_smaller")) + 12, bh).build());
        actionRowButtons.add(dispSmallButton);

        Button toggleNameplateButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.futureshops.player_shop_block.footer.toggle_nameplate"),
                        button -> sendAction("TOGGLE_NAMEPLATE", 0))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.toggle_nameplate_tooltip")))
                .bounds(bx, actionRowY, this.font.width(I18n.get("gui.futureshops.player_shop_block.footer.toggle_nameplate")) + 12, bh).build());
        actionRowButtons.add(toggleNameplateButton);

        // Link buttons
        String linkLabel = I18n.get("gui.futureshops.player_shop_block.footer.link");
        int linkBtnW = this.font.width(linkLabel) + 12;
        linkButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.footer.link"), button -> sendAction("LINK_LOOKING", 0))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.link_tooltip")))
                .bounds(bx, linkRowY, linkBtnW, bh).build());
        linkRowButtons.add(linkButton);

        String unlkLabel = I18n.get("gui.futureshops.player_shop_block.footer.unlink");
        int unlkBtnW = this.font.width(unlkLabel) + 12;
        unlinkButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.footer.unlink"), button -> sendAction("UNLINK", 0))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.unlink_tooltip")))
                .bounds(bx, linkRowY, unlkBtnW, bh).build());
        linkRowButtons.add(unlinkButton);

        String blnkLabel = I18n.get("gui.futureshops.player_shop_block.footer.blink");
        int blnkBtnW = this.font.width(blnkLabel) + 12;
        linkBarterButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.footer.blink"), button -> sendAction("LINK_BARTER_LOOKING", 0))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.blink_tooltip")))
                .bounds(bx, linkRowY, blnkBtnW, bh).build());
        linkRowButtons.add(linkBarterButton);

        String bulkLabel = I18n.get("gui.futureshops.player_shop_block.footer.bulink");
        int bulkBtnW = this.font.width(bulkLabel) + 12;
        unlinkBarterButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.footer.bulink"), button -> sendAction("UNLINK_BARTER", 0))
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.footer.bulink_tooltip")))
                .bounds(bx, linkRowY, bulkBtnW, bh).build());
        linkRowButtons.add(unlinkBarterButton);

        // Initial reflow
        reflowFooterButtons();
    }

    private void initVisitorWidgets() {
        // Item 4 & 9: Back button when navigated from another screen — placed LEFT of the
        // close (×) button on the top-right so it never slides onto the owner's player head
        // in the header (which sits at guiLeft+16, guiTop+8).
        if (parent != null) {
            addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.visitor.back"), button -> onClose())
                    .bounds(guiLeft + guiW - 24 - 44 - 4, guiTop + 6, 44, 14)
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
        Component cartLabel = cartCount > 0
                ? Component.translatable("gui.futureshops.player_shop_block.visitor.cart_count", cartCount)
                : Component.translatable("gui.futureshops.player_shop_block.visitor.cart_empty");
        int cartBtnW = tightFit ? 28 : 40;
        addRenderableWidget(Button.builder(cartLabel, button -> {
                    if (this.minecraft != null)
                        this.minecraft.setScreen(new PlayerShopCartScreen(this));
                })
                .bounds(rightEdge - cartBtnW, y, cartBtnW, 14).build());
        rightEdge -= cartBtnW + btnGap;

        // Add to Cart
        int cartAddW = tightFit ? 32 : 42;
        addToCartButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.visitor.add_cart"), button -> addToCart())
                .bounds(rightEdge - cartAddW, y, cartAddW, 14).build());
        rightEdge -= cartAddW + btnGap;

        // Barter button — LGB#6: pass current quantity
        int barterBtnW = tightFit ? 42 : 58;
        visitorBarterButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.visitor.barter_btn"), button -> {
                    if (this.minecraft != null)
                        this.minecraft.setScreen(new PlayerShopBarterScreen(this, getQuantity()));
                })
                .bounds(rightEdge - barterBtnW, y, barterBtnW, 14).build());
        rightEdge -= barterBtnW + btnGap;

        // Sell-to-shop button (buyback). Visibility/active state managed in syncButtonStates.
        int sellBtnW = tightFit ? 44 : 60;
        visitorSellButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.futureshops.player_shop_block.visitor.sell_button"),
                        button -> {
                            if (this.minecraft != null)
                                this.minecraft.setScreen(new PlayerShopSellScreen(this, getQuantity()));
                        })
                .bounds(rightEdge - sellBtnW, y, sellBtnW, 14).build());
        rightEdge -= sellBtnW + btnGap;

        // Buy button (money)
        int buyBtnW = tightFit ? 40 : 56;
        visitorBuyButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.visitor.buy_btn"), button -> showBuyConfirmation(getQuantity()))
                .bounds(rightEdge - buyBtnW, y, buyBtnW, 14).build());
        rightEdge -= buyBtnW + btnGap + 4;

        // Quantity: - [box] + Max — fill remaining space from left
        int qtyX = Math.max(guiLeft + 8, rightEdge - 100);
        addRenderableWidget(Button.builder(Component.literal("-"), button -> setQuantity(getQuantity() - 1))
                .bounds(qtyX, y, 14, 14).build());
        quantityBox = new EditBox(this.font, qtyX + 16, y, 32, 14, Component.translatable("gui.futureshops.player_shop_block.visitor.qty"));
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
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.player_shop_block.visitor.shift_max")))
                .bounds(qtyX + 50, y, 14, 14).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.player_shop_block.visitor.max"), button -> setQuantity(resolveMaxQuantity()))
                .bounds(qtyX + 66, y, 28, 14).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Reset hover tooltip
        hoveredItemId = null;
        hoveredNbtJson = null;
        hoveredDescriptionFull = null;

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
        } else if (hoveredDescriptionFull != null && !hoveredDescriptionFull.isBlank()) {
            // Let vanilla tooltip rendering handle its own wrapping/positioning for the full text.
            graphics.renderTooltip(this.font, Component.literal(hoveredDescriptionFull),
                    hoveredDescriptionMouseX, hoveredDescriptionMouseY);
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
            String title = I18n.get(PlayerShopClientState.owner()
                    ? "gui.futureshops.player_shop_block.header.owner_compact"
                    : "gui.futureshops.player_shop_block.header.visitor_compact");
            graphics.drawString(this.font, title, hx + hh, hy + 4, ShopColors.TEXT_PRIMARY, false);
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
                int descMaxW = Math.max(20, hx + hw - 130 - descX);
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
            ShopUiUtil.renderPlayerFace(graphics, PlayerShopClientState.ownerUuid(), hx + 8, hy + 8, 34);
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

            int centerX = hx + 50;
            int centerMaxW = Math.max(60, rightRegionX - centerX - 6);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(title, centerMaxW), centerX, hy + 8, ShopColors.TEXT_PRIMARY, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth("§7" + shopName, centerMaxW), centerX, hy + 20, ShopColors.TEXT_SECONDARY, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(
                    I18n.get("gui.futureshops.player_shop_block.header.owner_label", PlayerShopClientState.ownerName()),
                    Math.min(130, centerMaxW)), centerX, hy + 32, ShopColors.TEXT_SECONDARY, false);

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

    private void renderConfigPanel(GuiGraphics graphics) {
        int cx = guiLeft + 8;
        int cy = guiTop + headerHeight;
        int cw = guiW - 16;
        int ch = configPanelHeight;
        ShopUiUtil.renderCard(graphics, cx, cy, cw, ch);
        graphics.fill(cx, cy, cx + cw, cy + 2, ShopColors.ACCENT_CURRENCY);
        int labelY = cy + (compact ? 6 : 12);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.player_shop_block.config.name_label"), cx + 6, labelY, ShopColors.TEXT_FAINT, false);
    }

    private void renderListingRail(GuiGraphics graphics, int mouseX, int mouseY) {
        int railX = guiLeft + 8;
        int railY = contentStartY;
        int railW = listingRailW;
        int railH = contentAreaH;
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

            // Name — scrolls (ping-pong) when wider than the rail so long modded names stay readable.
            int nameW = railW - 42;
            String dirPrefix = "";
            if (listing.direction() != null) {
                String dirU = listing.direction().toUpperCase(Locale.ROOT);
                if ("BUY".equals(dirU)) dirPrefix = "§6[BUY] ";
                else if ("BOTH".equals(dirU)) dirPrefix = "§e[B+S] ";
            }
            String fullName = dirPrefix + ShopUiUtil.getItemDisplayNameWithNbtAndQty(listing.itemId(), listing.nbtJson(), listing.baseQuantity());
            ShopUiUtil.renderScrollingString(graphics, this.font, fullName,
                    railX + 30, y + 4, nameW, selected ? ShopColors.TEXT_STRONG : ShopColors.TEXT_MUTED);

            // Meta line — stock + mode
            boolean admin = PlayerShopClientState.adminShopMode();
            String stockStr = admin ? "∞" : (listing.stock() + " stk");
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
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.player_shop_block.detail.select_listing"), detailX + detailW / 2, detailY + detailH / 2, ShopColors.TEXT_FAINT);
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
                ? "§d∞ §7unlimited"
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
        if (PlayerShopClientState.owner()) {
            // ── Owner view: section panels with inline controls ──
            // Section 1: Pricing
            ShopUiUtil.renderPanel(graphics, ownerInfoX, priceSecY, ownerInfoW, sectionH, ShopColors.SURFACE_RAISED, ShopColors.BORDER_SUBTLE);
            graphics.fill(ownerInfoX, priceSecY, ownerInfoX + 2, priceSecY + sectionH, ShopColors.ACCENT_CURRENCY);
            graphics.drawString(this.font, Component.translatable("gui.futureshops.player_shop_block.detail.section.pricing"), ownerInfoX + 6, priceSecY + 3, ShopColors.TEXT_FAINT, false);
            String effectivePrice = listing.effectiveUnitPriceMinor() <= 0
                    ? I18n.get("gui.futureshops.player_shop_block.detail.free")
                    : ShopUiUtil.formatMinorUnits(listing.effectiveUnitPriceMinor());
            int epW = this.font.width(this.font.plainSubstrByWidth(effectivePrice, ownerInfoW / 2));
            graphics.drawString(this.font, this.font.plainSubstrByWidth(effectivePrice, ownerInfoW / 2),
                    ownerInfoX + ownerInfoW - epW - 4, priceSecY + 3, ShopColors.TEXT_CURRENCY, false);

            // Section 2: Barter
            ShopUiUtil.renderPanel(graphics, ownerInfoX, barterSecY, ownerInfoW, sectionH, ShopColors.SURFACE_RAISED, ShopColors.BORDER_SUBTLE);
            graphics.fill(ownerInfoX, barterSecY, ownerInfoX + 2, barterSecY + sectionH, ShopColors.TEXT_BARTER_SOFT);
            graphics.drawString(this.font, Component.translatable("gui.futureshops.player_shop_block.detail.section.barter"), ownerInfoX + 6, barterSecY + 3, ShopColors.TEXT_BARTER_SOFT, false);
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
            graphics.drawString(this.font, Component.translatable("gui.futureshops.player_shop_block.detail.section.config"), ownerInfoX + 6, configSecY + 3, ShopColors.ACCENT_CURRENCY, false);
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

            // ── Held item preview (bottom-left of detail panel) — always shown for owners,
            //    including an empty-hand placeholder so the slot never silently disappears. ──
            if (this.minecraft != null && this.minecraft.player != null) {
                ItemStack heldItem = this.minecraft.player.getMainHandItem();
                int heldY = detailY + detailH - 22;
                int heldX = detailX + 6;
                graphics.drawString(this.font, Component.translatable("gui.futureshops.player_shop_block.detail.held_label"), heldX, heldY - 10, ShopColors.TEXT_SECONDARY, false);
                if (!heldItem.isEmpty()) {
                    ShopUiUtil.renderItemIcon(graphics, this.font,
                            net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(heldItem.getItem()).toString(),
                            heldX, heldY);
                    String heldName = this.font.plainSubstrByWidth(heldItem.getHoverName().getString(), previewW - 24);
                    graphics.drawString(this.font, "§f" + heldName, heldX + 20, heldY + 4, ShopColors.TEXT_PRIMARY, false);
                } else {
                    // Empty-slot placeholder keeps the row visible so owners always see where the held item goes.
                    graphics.fill(heldX, heldY, heldX + 16, heldY + 16, 0x33000000);
                    graphics.drawString(this.font, "§8(empty hand)", heldX + 20, heldY + 4, ShopColors.TEXT_FAINT, false);
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
                    String barter = listing.barterItemCount() + "× " + ShopUiUtil.getItemDisplayName(listing.barterItemId());
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
                                            ShopUiUtil.countPlayerInventory(listing.barterItemId())),
                                    infoW - 8),
                            infoX + 4, py, ShopColors.TEXT_SECONDARY, false);
                    py += 13;

                    // Barter item icon preview — only render if it fits within the pricing panel with margin
                    if (py + 18 <= pricePanelY + panelH - 4) {
                        ShopUiUtil.renderItemIcon(graphics, this.font, listing.barterItemId(), infoX + 4, py);
                        String barterName = "§9" + ShopUiUtil.getItemDisplayName(listing.barterItemId());
                        ShopUiUtil.renderScrollingString(graphics, this.font, barterName,
                                infoX + 24, py + 4, infoW - 28, ShopColors.TEXT_BARTER);
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
                        String barterItemName = ShopUiUtil.getItemDisplayName(listing.barterItemId());
                        String saleItemName = ShopUiUtil.getItemDisplayNameWithQty(listing.itemId(), listing.baseQuantity());
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
        int bottomStackY = detailY + detailH - (compact ? 52 : 58);
        String dispName = ShopUiUtil.getItemDisplayNameWithNbtAndQty(listing.itemId(), listing.nbtJson(), listing.baseQuantity());
        ShopUiUtil.renderScrollingCentered(graphics, this.font, dispName,
                detailX + 8 + previewW / 2, bottomStackY, previewW - 10, ShopColors.TEXT_PRIMARY);

        int owned = ShopUiUtil.countPlayerInventory(listing.itemId());
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
                ? "§d∞ §7unlimited"
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
                ? PlayerShopClientState.ownerName() + "'s Shop"
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
                graphics.drawString(this.font, this.font.plainSubstrByWidth("Owned: " + ownedBarter, infoW - 16),
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

        // Stock (∞ in admin mode)
        String stockLabel = PlayerShopClientState.adminShopMode()
                ? "§d∞ §7unlimited stock"
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
            case "BOUGHT", "INSUFFICIENT_FUNDS", "OUT_OF_STOCK", "MISSING_BARTER_ITEMS", "STORAGE_FULL",
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
        // Read authoritative state from backing fields — button messages are translatable
        // and locale-dependent, so string-inspecting them is unsafe.
        boolean singleItem = singleMultiButton != null && configSingleMode;
        boolean barterSame = barterStorageButton != null && configBarterSame;
        // LGB#24: Pass selected listing index for single-item mode
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopConfigPacket(
                PlayerShopClientState.shopPos(), name, singleItem, barterSame, PlayerShopClientState.selectedListingIndex()));
    }

    private Component singleMultiMessage() {
        return Component.translatable(configSingleMode
                ? "gui.futureshops.player_shop_block.config.single"
                : "gui.futureshops.player_shop_block.config.multi");
    }

    private Component barterStorageMessage() {
        String key;
        if (configBarterSame) {
            key = compact
                    ? "gui.futureshops.player_shop_block.config.same_short"
                    : "gui.futureshops.player_shop_block.config.same_long";
        } else {
            key = compact
                    ? "gui.futureshops.player_shop_block.config.sep_short"
                    : "gui.futureshops.player_shop_block.config.sep_long";
        }
        return Component.translatable(key);
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
            boolean inStock = listing.stock() > 0 || PlayerShopClientState.adminShopMode();
            boolean hasMoney = !"BARTER".equalsIgnoreCase(listing.tradeMode());
            boolean hasBarter = !"MONEY".equalsIgnoreCase(listing.tradeMode());

            // Direction-aware buy/barter visibility — SELL/BOTH allow visitors to buy.
            String dir = listing.direction() == null ? "SELL" : listing.direction().toUpperCase(Locale.ROOT);
            boolean allowsBuy = !"BUY".equals(dir); // SELL or BOTH
            boolean allowsSell = "BUY".equals(dir) || "BOTH".equals(dir);

            if (visitorBuyButton != null) {
                visitorBuyButton.visible = hasMoney && allowsBuy;
                visitorBuyButton.active = hasMoney && inStock && allowsBuy;
            }
            if (visitorBarterButton != null) {
                visitorBarterButton.visible = hasBarter && allowsBuy;
                visitorBarterButton.active = hasBarter && inStock && allowsBuy;
            }
            if (visitorSellButton != null) {
                boolean capOk = listing.buybackCap() == 0 || listing.buybackRemaining() > 0;
                boolean canSell = allowsSell && listing.buybackPriceMinor() > 0 && capOk;
                visitorSellButton.visible = canSell;
                visitorSellButton.active = canSell;
            }
            // LGB#5: Grey out +Cart when out of stock
            if (addToCartButton != null) {
                addToCartButton.active = inStock && allowsBuy;
                addToCartButton.visible = allowsBuy;
            }
        } else if (!PlayerShopClientState.owner()) {
            if (visitorBuyButton != null) { visitorBuyButton.active = false; visitorBuyButton.visible = true; }
            if (visitorBarterButton != null) { visitorBarterButton.active = false; visitorBarterButton.visible = true; }
            if (visitorSellButton != null) { visitorSellButton.active = false; visitorSellButton.visible = false; }
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

        // Buyback controls: owner-only and require a selected listing.
        boolean ownerSel = PlayerShopClientState.owner() && hasSelection;
        if (dirButton != null) {
            dirButton.visible = ownerSel;
            dirButton.active = ownerSel;
            if (ownerSel) {
                dirButton.setMessage(Component.literal(
                        I18n.get("gui.futureshops.player_shop_block.footer.dir", currentDirection())));
            }
        }
        if (buybackPriceButton != null) {
            buybackPriceButton.visible = ownerSel;
            buybackPriceButton.active = ownerSel;
            if (ownerSel) {
                buybackPriceButton.setMessage(Component.literal(
                        I18n.get("gui.futureshops.player_shop_block.footer.buyback_price")
                                + " §7" + ShopUiUtil.formatMinorUnits(listing.buybackPriceMinor())));
            }
        }
        if (buybackCapButton != null) {
            buybackCapButton.visible = ownerSel;
            buybackCapButton.active = ownerSel;
            if (ownerSel) {
                int cap = listing.buybackCap();
                buybackCapButton.setMessage(Component.literal(
                        I18n.get("gui.futureshops.player_shop_block.footer.buyback_cap")
                                + " §7" + (cap == 0 ? "∞" : Integer.toString(cap))));
            }
        }

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
        if (listing == null || (listing.stock() <= 0 && !PlayerShopClientState.adminShopMode())) return;
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
        buy(quantity, "MONEY");
    }

    private void buy(int quantity, String paymentMethod) {
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopBuyPacket(
                PlayerShopClientState.shopPos(), PlayerShopClientState.selectedListingIndex(), quantity, paymentMethod));
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
            boolean hasRealBarterNbt = listing.barterNbtAware()
                    && ShopUiUtil.hasNonDefaultNbt(barterId != null ? barterId : "", listing.barterNbtJson());
            String barterName;
            if (barterId == null || barterId.isBlank()) {
                barterName = I18n.get("gui.futureshops.player_shop_block.confirm.unknown_item");
            } else if (hasRealBarterNbt) {
                barterName = ShopUiUtil.getItemDisplayNameWithNbt(barterId, listing.barterNbtJson());
            } else {
                barterName = ShopUiUtil.getItemDisplayName(barterId);
            }
            summary.add(ConfirmationModal.SummaryLine.item(
                    barterId != null ? barterId : "",
                    I18n.get("gui.futureshops.player_shop_block.confirm.plus_give", barterAmount, barterName),
                    hasRealBarterNbt ? listing.barterNbtJson() : ""));
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

        confirmationModal = new ConfirmationModal(
                I18n.get("gui.futureshops.player_shop_block.confirm.title"),
                summary,
                totalLine,
                modal -> {
                    modal.setProcessing();
                    buy(quantity, paymentMethod);
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
        // Only clamp against shop stock at input time. Don't reject based on affordability
        // (balance / inventory) — before the client balance sync arrives those numbers read
        // as zero, which previously pinned the quantity at 1 until the player ran /shop to
        // force a balance refresh. The server-side buy path re-validates both stock and
        // funds, so letting the user freely dial up a number here is safe.
        PlayerShopListingData listing = PlayerShopClientState.selectedListing();
        int stockCap = listing == null ? 999 : Math.max(1, listing.stock());
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
