package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.SellOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.data.CatalogBarterIngredient;
import com.enviouse.futureshops.data.CatalogBarterRecipe;
import com.enviouse.futureshops.data.CatalogItem;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SBuyRequestPacket;
import com.enviouse.futureshops.network.packets.C2SInventorySyncPacket;
import com.enviouse.futureshops.network.packets.C2SSellRequestPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Item detail view — redesigned with bottom-aligned controls and modern layout.
 */
public class ItemDetailScreen extends Screen implements ShopScreenMarker {
    private static final int DEFAULT_GUI_W = 340;
    private static final int DEFAULT_GUI_H = 260;
    private static final int PREVIEW_W = 130;

    private final Screen parent;
    /** Catalog resolution key (listingId) this detail view is bound to — NOT the registry itemId. */
    private final String listingId;

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;

    private EditBox quantityBox;

    /** Per-frame flat-button hit regions (see ShopUiUtil.button / dispatchClicks). */
    private final java.util.List<ShopUiUtil.ClickZone> clickZones = new ArrayList<>();
    /** Deferred hover tooltip for a flat button, rendered on top after super.render(). */
    private Component pendingButtonTooltip;

    // Item 6: advanced tooltip tracking
    private boolean showItemTooltip = false;

    // Spec §8: Confirmation modal overlay
    private ConfirmationModal confirmationModal = null;
    private java.util.UUID pendingSellRequestId;
    private java.util.UUID pendingOfferRequestId;
    private int bundleComponentScroll;
    private int[] bundleComponentRect;

    public ItemDetailScreen(Screen parent, String listingId) {
        super(Component.translatable("gui.futureshops.detail.title"));
        this.parent = parent;
        this.listingId = listingId;
    }

    @Override
    protected void init() {
        int preservedQuantity = quantityBox == null ? 1 : getQuantity();
        // Centered dialog (Nocturne item-detail): a compact box over the dimmed ground, capped so
        // it reads as a modal rather than a full-screen takeover. Layout supports down to 280 wide.
        guiW = Math.min(Math.max(300, this.width - 20), 380);
        guiH = Math.min(Math.max(220, this.height - 20), 320);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;
        int actualPreviewW = Math.min(PREVIEW_W, guiW / 2 - 10);

        ShopPackets.CHANNEL.sendToServer(new C2SInventorySyncPacket(ShopClientState.getActiveShopId()));

        // ═══ Quantity input box — flat +/-/Max steppers are drawn in render() ═══
        int previewCenterX = guiLeft + 8 + PREVIEW_W / 2;
        // Preview panel bottom = guiTop + guiH - 32. Stack: controls → Total → "Quantity"
        int qtyY = guiTop + guiH - 32 - 42; // controls Y

        quantityBox = new EditBox(this.font, previewCenterX - 30, qtyY, 36, 14,
                Component.translatable("gui.futureshops.detail.quantity"));
        quantityBox.setValue(Integer.toString(preservedQuantity));
        quantityBox.setMaxLength(4);
        // Only allow digits — prevents junk input without fighting the user mid-type.
        quantityBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        // Responder is intentionally empty: getQuantity() clamps on demand when an
        // action fires. Writing a clamped value back during typing erases the caret
        // and was perceived as "field not accepting input" when the client catalog
        // hadn't loaded yet (resolveMaxQuantity() returns 1 → every keystroke reset).
        quantityBox.setResponder(value -> { /* no-op; clamp on use */ });
        addRenderableWidget(quantityBox);
    }

    /** Buy confirmation modal — mirrors the former buyButton.onPress. */
    private void openBuyConfirm(CatalogItem item) {
        if (item == null) return;
        long effectivePrice = item.hasPromo() ? item.promoPrice() : item.buyPrice();
        int qty = getQuantity();
        String totalStr = ShopUiUtil.formatMinorUnits(effectivePrice * qty);
        confirmationModal = new ConfirmationModal(
                I18n.get("gui.futureshops.item_detail.confirm_buy_title"),
                java.util.List.of(
                        ConfirmationModal.SummaryLine.item(item.itemId(), item.displayName() + " ×" + qty, item.nbtJson())
                ),
                I18n.get("gui.futureshops.item_detail.total_cost", totalStr, ShopClientState.getCurrencyName()),
                (modal, paymentSource) -> {
                    modal.setProcessing();
                    ShopPackets.CHANNEL.sendToServer(C2SBuyRequestPacket.single(
                            ShopClientState.getActiveShopId(), item.listingId(), qty, paymentSource));
                },
                () -> confirmationModal = null
        );
    }

    /** Sell confirmation modal — mirrors the former sellButton.onPress. */
    private void openSellConfirm(CatalogItem item) {
        if (item == null) return;
        int qty = getQuantity();
        String totalStr = ShopUiUtil.formatMinorUnits(item.sellPrice() * qty);
        confirmationModal = new ConfirmationModal(
                I18n.get("gui.futureshops.item_detail.confirm_sell_title"),
                java.util.List.of(
                        ConfirmationModal.SummaryLine.item(item.itemId(),
                                I18n.get("gui.futureshops.item_detail.sell_summary", item.displayName(), qty),
                                item.nbtJson())
                ),
                I18n.get("gui.futureshops.item_detail.earn", totalStr, ShopClientState.getCurrencyName()),
                modal -> {
                    modal.setProcessing();
                    pendingSellRequestId = java.util.UUID.randomUUID();
                    ShopPackets.CHANNEL.sendToServer(new C2SSellRequestPacket(
                            ShopClientState.getActiveShopId(), item.listingId(),
                            qty, pendingSellRequestId));
                },
                () -> confirmationModal = null
        );
    }

    void openAcquireOfferConfirm(AcquireOfferOption option) {
        ServerShopOfferListing offer = currentOffer();
        if (offer == null || option == null
                || !offer.acquireOptions().contains(option)) {
            return;
        }
        int quantity = getQuantity();
        List<ConfirmationModal.SummaryLine> summary =
                offer.outputs().stream().map(component ->
                        componentLine(component,
                                Math.multiplyExact(
                                        component.count(),
                                        option.outputMultiplier()
                                                * quantity),
                                "gui.futureshops.offer.receive"))
                        .collect(java.util.stream.Collectors
                                .toCollection(ArrayList::new));
        prependOfferContext(summary, offer,
                option.label().isBlank()
                        ? option.optionId() : option.label(),
                option.limits(), option.schedule(), quantity,
                option.moneyCostPresent());
        if (!option.itemCosts().isEmpty()) {
            summary.add(ConfirmationModal.SummaryLine.text(
                    I18n.get("gui.futureshops.offer.all_required")));
            option.itemCosts().forEach(component -> summary.add(
                    componentLine(component,
                            Math.multiplyExact(component.count(), quantity),
                            "gui.futureshops.offer.give")));
        }
        String total = acquireTotal(option, quantity);
        Runnable cancel = () -> confirmationModal = null;
        if (option.moneyCostPresent()) {
            confirmationModal = new ConfirmationModal(
                    offerActionTitle(option), summary, total,
                    (modal, source) -> submitAcquireOffer(
                            modal, offer, option, quantity,
                            Optional.of(source)), cancel);
        } else {
            confirmationModal = new ConfirmationModal(
                    offerActionTitle(option), summary, total,
                    modal -> submitAcquireOffer(
                            modal, offer, option, quantity,
                            Optional.empty()), cancel);
        }
    }

    void addAcquireOfferToCart(AcquireOfferOption option) {
        ServerShopOfferListing offer = currentOffer();
        if (offer == null || option == null
                || !offer.acquireOptions().contains(option)) {
            return;
        }
        ShopClientState.addOfferToCart(
                offer.listingId(),
                option.optionId(),
                getQuantity(),
                offer.revision());
    }

    void openSellOfferConfirm(SellOfferOption option) {
        ServerShopOfferListing offer = currentOffer();
        if (offer == null || option == null
                || !offer.sellOptions().contains(option)) {
            return;
        }
        int quantity = getQuantity();
        List<ConfirmationModal.SummaryLine> summary =
                new ArrayList<>();
        prependOfferContext(summary, offer,
                option.label().isBlank()
                        ? option.optionId() : option.label(),
                option.limits(), option.schedule(), quantity, false);
        option.itemInputs().forEach(component -> summary.add(
                componentLine(component,
                        Math.multiplyExact(component.count(), quantity),
                        "gui.futureshops.offer.give")));
        String payout = ShopUiUtil.formatMinorUnits(
                Math.multiplyExact(
                        option.moneyPayoutMinorUnits(), quantity));
        confirmationModal = new ConfirmationModal(
                I18n.get("gui.futureshops.offer.sell_to_shop"),
                summary,
                I18n.get("gui.futureshops.item_detail.earn",
                        payout, ShopClientState.getCurrencyName()),
                modal -> {
                    modal.setProcessing();
                    Optional<com.enviouse.futureshops.client
                            .ServerShopOfferResponseTracker.PendingRequest>
                            pending = ShopClientPacketHandler
                            .submitServerShopOffer(
                                    ShopClientState.getActiveShopId(),
                                    offer.listingId(), option.optionId(),
                                    OfferAction.SELL_TO_SHOP, quantity,
                                    offer.revision(), Optional.empty());
                    pendingOfferRequestId = pending
                            .map(value -> value.requestId()).orElse(null);
                    if (pendingOfferRequestId == null) {
                        modal.setFailed(I18n.get(
                                "gui.futureshops.offer.result.unavailable"));
                    }
                },
                () -> confirmationModal = null);
    }

    private void prependOfferContext(
            List<ConfirmationModal.SummaryLine> summary,
            ServerShopOfferListing offer,
            String optionLabel,
            OfferLimitPolicy optionLimits,
            com.enviouse.futureshops.catalog.offer.OfferSchedule optionSchedule,
            int quantity,
            boolean paymentSourceRequired
    ) {
        List<ConfirmationModal.SummaryLine> context = new ArrayList<>();
        context.add(ConfirmationModal.SummaryLine.text(
                I18n.get("gui.futureshops.offer.context.option_quantity",
                        optionLabel, quantity)));
        CatalogItem item = currentItem();
        if (item != null) {
            context.add(ConfirmationModal.SummaryLine.text(
                    item.unlimited()
                            ? I18n.get(
                            "gui.futureshops.offer.context.stock_unlimited")
                            : I18n.get(
                            "gui.futureshops.offer.context.stock",
                            item.stock())));
        }
        int maximum = Math.min(offer.limits().maximumPerRequest(),
                optionLimits.maximumPerRequest());
        context.add(ConfirmationModal.SummaryLine.text(
                I18n.get("gui.futureshops.offer.context.maximum",
                        maximum)));
        long lifetime = positiveMinimum(
                offer.limits().lifetimeLimit(),
                optionLimits.lifetimeLimit());
        if (lifetime > 0L) {
            context.add(ConfirmationModal.SummaryLine.text(
                    I18n.get("gui.futureshops.offer.context.lifetime",
                            lifetime)));
        }
        long periodQuantity = positiveMinimum(
                offer.limits().periodLimit(),
                optionLimits.periodLimit());
        long periodSeconds = positiveMinimum(
                offer.limits().periodSeconds(),
                optionLimits.periodSeconds());
        if (periodQuantity > 0L && periodSeconds > 0L) {
            context.add(ConfirmationModal.SummaryLine.text(
                    I18n.get("gui.futureshops.offer.context.period",
                            periodQuantity, periodSeconds)));
        }
        long cooldown = Math.max(offer.limits().cooldownSeconds(),
                optionLimits.cooldownSeconds());
        if (cooldown > 0L) {
            context.add(ConfirmationModal.SummaryLine.text(
                    I18n.get("gui.futureshops.offer.context.cooldown",
                            cooldown)));
        }
        appendScheduleContext(context, offer.schedule(),
                I18n.get("gui.futureshops.offer.context.listing"));
        appendScheduleContext(context, optionSchedule,
                I18n.get("gui.futureshops.offer.context.option"));
        if (paymentSourceRequired) {
            context.add(ConfirmationModal.SummaryLine.text(
                    I18n.get("gui.futureshops.offer.context.payment_source",
                            I18n.get(
                                    "gui.futureshops.modal.inventory_cash"),
                            I18n.get(
                                    "gui.futureshops.modal.wallet_balance"))));
        }
        summary.addAll(0, context);
    }

    private static long positiveMinimum(long first, long second) {
        if (first <= 0L) {
            return Math.max(0L, second);
        }
        if (second <= 0L) {
            return first;
        }
        return Math.min(first, second);
    }

    private static void appendScheduleContext(
            List<ConfirmationModal.SummaryLine> summary,
            com.enviouse.futureshops.catalog.offer.OfferSchedule schedule,
            String label
    ) {
        if (schedule.startsAtEpoch() > 0L) {
            summary.add(ConfirmationModal.SummaryLine.text(
                    I18n.get("gui.futureshops.offer.context.starts",
                            label, java.time.Instant.ofEpochSecond(
                                    schedule.startsAtEpoch()))));
        }
        if (schedule.endsAtEpoch() > 0L) {
            summary.add(ConfirmationModal.SummaryLine.text(
                    I18n.get("gui.futureshops.offer.context.ends",
                            label, java.time.Instant.ofEpochSecond(
                                    schedule.endsAtEpoch()))));
        }
    }

    private void submitAcquireOffer(
            ConfirmationModal modal,
            ServerShopOfferListing offer,
            AcquireOfferOption option,
            int quantity,
            Optional<com.enviouse.futureshops.money.PaymentSource> source
    ) {
        modal.setProcessing();
        Optional<com.enviouse.futureshops.client
                .ServerShopOfferResponseTracker.PendingRequest> pending =
                ShopClientPacketHandler.submitServerShopOffer(
                        ShopClientState.getActiveShopId(),
                        offer.listingId(), option.optionId(),
                        OfferAction.ACQUIRE_FROM_SHOP, quantity,
                        offer.revision(), source);
        pendingOfferRequestId = pending
                .map(value -> value.requestId()).orElse(null);
        if (pendingOfferRequestId == null) {
            modal.setFailed(I18n.get(
                    "gui.futureshops.offer.result.unavailable"));
        }
    }

    private ConfirmationModal.SummaryLine componentLine(
            OfferItemComponent component,
            int count,
            String translation
    ) {
        String name = ShopUiUtil.getItemDisplayNameWithNbt(
                component.itemId(), component.exactNbt());
        return ConfirmationModal.SummaryLine.item(
                component.itemId(),
                I18n.get(translation, name, count),
                component.exactNbt());
    }

    private String acquireTotal(
            AcquireOfferOption option,
            int quantity
    ) {
        if (option.free()) {
            return I18n.get("gui.futureshops.offer.free");
        }
        if (!option.moneyCostPresent()) {
            return I18n.get("gui.futureshops.offer.barter_only");
        }
        String money = ShopUiUtil.formatMinorUnits(
                Math.multiplyExact(
                        option.moneyCostMinorUnits(), quantity));
        return option.hasItemCosts()
                ? I18n.get("gui.futureshops.offer.money_and_items",
                money, ShopClientState.getCurrencyName())
                : I18n.get("gui.futureshops.item_detail.total_cost",
                money, ShopClientState.getCurrencyName());
    }

    private String offerActionTitle(AcquireOfferOption option) {
        return option.free()
                ? I18n.get("gui.futureshops.offer.claim_free")
                : I18n.get("gui.futureshops.item_detail.confirm_buy_title");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();
        pendingButtonTooltip = null;
        CatalogItem item = currentItem();
        if (item == null) {
            onClose();
            return;
        }

        showItemTooltip = false;

        // Dimmed background
        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        // Main panel — elevated neon-glass
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        ShopUiUtil.renderAccentLine(graphics, guiLeft + 2, guiTop, guiW - 4);

        renderPreviewPanel(graphics, item, mouseX, mouseY);
        renderInfoPanel(graphics, item, mouseX, mouseY);
        renderBottomArea(graphics, item);
        ShopUiUtil.renderStatusPanel(graphics, this.font, guiLeft, Math.max(4, guiTop - 22), guiW);
        renderActionButtons(graphics, item, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);

        // Item 6: Render advanced tooltip after everything
        if (showItemTooltip) {
            ShopUiUtil.renderItemTooltip(graphics, this.font, item.itemId(),
                    item.nbtJson() != null ? item.nbtJson() : "", mouseX, mouseY);
        }
        if (pendingButtonTooltip != null && confirmationModal == null) {
            graphics.renderTooltip(this.font, pendingButtonTooltip, mouseX, mouseY);
        }

        // Spec §8: Render confirmation modal on top of everything
        if (confirmationModal != null) {
            confirmationModal.render(graphics, this.font, this.width, this.height, mouseX, mouseY);
            if (confirmationModal.shouldAutoDismiss()) {
                confirmationModal = null;
            }
        }
    }

    private void renderPreviewPanel(GuiGraphics graphics, CatalogItem item, int mouseX, int mouseY) {
        int leftX = guiLeft + 8;
        int panelY = guiTop + 24;
        int panelH = guiH - 56;

        ShopUiUtil.renderCard(graphics, leftX, panelY, PREVIEW_W, panelH);
        graphics.fill(leftX, panelY, leftX + PREVIEW_W, panelY + 2, ShopColors.ACCENT_PRIMARY);

        ShopUiUtil.renderLargeItemPreviewWithNbt(graphics, this.font, item.itemId(),
                item.nbtJson() != null ? item.nbtJson() : "", leftX, panelY + 8, PREVIEW_W);

        // Item 6: Detect hover over the large item preview area for tooltip
        int iconCenterX = leftX + PREVIEW_W / 2;
        int iconY = panelY + 18; // approximate preview center
        if (mouseX >= iconCenterX - 24 && mouseX <= iconCenterX + 24 && mouseY >= iconY && mouseY <= iconY + 48) {
            showItemTooltip = true;
        }

        // Name — wrapped (moved up to leave room at scale 4 where guiH is small)
        String name = this.font.plainSubstrByWidth(item.displayName(), PREVIEW_W - 10);
        graphics.drawCenteredString(this.font, name, leftX + PREVIEW_W / 2, panelY + 66, ShopColors.TEXT_STRONG);

        // Owned count (moved up to clear qty controls row)
        String ownedStr = Component.translatable("gui.futureshops.detail.you_own", ShopUiUtil.countPlayerInventoryNbt(item.itemId(), item.nbtJson(), true)).getString();
        graphics.drawCenteredString(this.font,
                this.font.plainSubstrByWidth(ownedStr, PREVIEW_W - 10),
                leftX + PREVIEW_W / 2, panelY + 80, ShopColors.TEXT_MUTED);

        ServerShopOfferListing offer = currentOffer();
        String total;
        if (offer != null && offer.sellOnly()) {
            total = offer.sellOptions().size() == 1
                    ? I18n.get("gui.futureshops.item_detail.earn",
                    ShopUiUtil.formatMinorUnits(Math.multiplyExact(
                            offer.sellOptions().get(0)
                                    .moneyPayoutMinorUnits(),
                            getQuantity())),
                    ShopClientState.getCurrencyName())
                    : I18n.get("gui.futureshops.offer.option_count",
                    offer.sellOptions().size());
        } else if (offer != null
                && offer.acquireOptions().size() == 1) {
            total = acquireTotal(offer.acquireOptions().get(0),
                    getQuantity());
        } else if (offer != null) {
            total = I18n.get("gui.futureshops.offer.option_count",
                    offer.acquireOptions().size());
        } else {
            long effectiveBuyPrice = item.hasPromo()
                    ? item.promoPrice() : item.buyPrice();
            total = Component.translatable(
                    "gui.futureshops.cart.total_line",
                    ShopUiUtil.formatMinorUnits(
                            effectiveBuyPrice * getQuantity()))
                    .getString();
        }
        graphics.drawCenteredString(this.font, total, leftX + PREVIEW_W / 2, panelY + panelH - 26, ShopColors.TEXT_CURRENCY);

        // Quantity label — below Total
        graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.item_detail.quantity_label"),
                leftX + PREVIEW_W / 2, panelY + panelH - 14, ShopColors.TEXT_FAINT);
    }

    private void renderInfoPanel(
            GuiGraphics graphics,
            CatalogItem item,
            int mouseX,
            int mouseY
    ) {
        ServerShopOfferListing offer = currentOffer();
        int infoX = guiLeft + PREVIEW_W + 18;
        int infoY = guiTop + 24;
        int infoW = guiW - PREVIEW_W - 26;
        int infoH = guiH - 56;
        long effectiveBuyPrice = item.hasPromo() ? item.promoPrice() : item.buyPrice();
        int promoPercent = ShopUiUtil.computePromoPercent(item.buyPrice(), item.promoPrice());

        ShopUiUtil.renderElevatedCard(graphics, infoX, infoY, infoW, infoH);

        int contentX = infoX + 8;
        int contentW = infoW - 16;

        // Title — scaled, truncated
        graphics.pose().pushPose();
        graphics.pose().translate(contentX, infoY + 8, 0);
        graphics.pose().scale(1.2f, 1.2f, 1f);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(item.displayName(), (int) (contentW / 1.2f)), 0, 0, ShopColors.TEXT_STRONG, true);
        graphics.pose().popPose();

        Component description = offer != null
                && !offer.description().isBlank()
                ? Component.literal(offer.description())
                : Component.translatable(
                "gui.futureshops.detail.no_description");
        List<net.minecraft.util.FormattedCharSequence> descriptionLines =
                this.font.split(description, contentW);
        int descriptionY = infoY + 24;
        int renderedDescriptionLines = Math.min(2,
                descriptionLines.size());
        for (int index = 0; index < renderedDescriptionLines; index++) {
            graphics.drawString(this.font,
                    descriptionLines.get(index), contentX,
                    descriptionY + index * 10,
                    ShopColors.TEXT_MUTED, false);
        }
        if (descriptionLines.size() > renderedDescriptionLines
                && mouseX >= contentX
                && mouseX < contentX + contentW
                && mouseY >= descriptionY
                && mouseY < descriptionY
                + renderedDescriptionLines * 10) {
            pendingButtonTooltip = description;
        }

        int nextY = descriptionY
                + Math.max(1, renderedDescriptionLines) * 10 + 4;

        // Divider
        graphics.fill(contentX, nextY, contentX + contentW, nextY + 1, ShopColors.BORDER_MUTED);
        nextY += 6;

        if (offer == null || !offer.sellOnly()) {
            String buyStr = offer == null
                    ? effectiveBuyPrice <= 0
                    ? Component.translatable(
                    "gui.futureshops.player_shop_block.confirm.free").getString()
                    : ShopUiUtil.formatMinorUnits(effectiveBuyPrice)
                    : acquireSummary(offer);
            if (item.hasPromo() && promoPercent > 0
                    && effectiveBuyPrice > 0) {
                buyStr = buyStr + " §7(-" + promoPercent + "%)";
            }
            drawInfoLine(graphics,
                    Component.translatable(
                            "gui.futureshops.item_detail.buy_label")
                            .getString(),
                    buyStr, contentX, contentW, nextY,
                    ShopColors.TEXT_CURRENCY);
            nextY += 12;
        }

        // Sell price
        String sellStr = offer == null
                ? item.sellPrice() > 0L
                ? ShopUiUtil.formatMinorUnits(item.sellPrice()) : "—"
                : sellSummary(offer);
        drawInfoLine(graphics, Component.translatable("gui.futureshops.item_detail.sell_label").getString(), sellStr, contentX, contentW, nextY,
                offer != null && !offer.sellOptions().isEmpty()
                        || item.sellPrice() > 0L
                        ? ShopColors.TEXT_CURRENCY
                        : ShopColors.TEXT_FAINT);
        nextY += 12;

        // Stock
        String stockLabel = item.unlimited()
                ? Component.translatable("gui.futureshops.item_detail.stock_unlimited").getString()
                : (item.stock() > 0
                        ? Component.translatable("gui.futureshops.player_shop_block.detail.single.stock_in", item.stock()).getString()
                        : Component.translatable("gui.futureshops.player_shop_block.detail.single.stock_out").getString());
        graphics.drawString(this.font, this.font.plainSubstrByWidth(stockLabel, contentW), contentX, nextY, ShopColors.TEXT_MUTED, false);
        nextY += 14;

        bundleComponentRect = null;
        if (offer != null) {
            nextY = renderOfferBadges(graphics, offer, contentX,
                    contentW, nextY);
            Optional<com.enviouse.futureshops.catalog.offer
                    .ServerShopBundleSavings.Snapshot> savings =
                    calculateBundleSavings(offer);
            if (savings.isPresent()) {
                String value = I18n.get(
                        "gui.futureshops.offer.savings",
                        ShopUiUtil.formatMinorUnits(
                                savings.get()
                                        .individualTotalMinorUnits()),
                        ShopUiUtil.formatMinorUnits(
                                savings.get()
                                        .bundleTotalMinorUnits()),
                        ShopUiUtil.formatMinorUnits(
                                savings.get().savingsMinorUnits()),
                        savings.get().savingsBasisPoints() / 100.0D);
                graphics.drawString(this.font,
                        this.font.plainSubstrByWidth(value, contentW),
                        contentX, nextY, ShopColors.SUCCESS, false);
                nextY += 12;
            }
            if (offer.bundle() && !offer.outputs().isEmpty()) {
                renderBundleComponents(graphics, offer, contentX,
                        nextY, contentW, infoY + infoH - 6);
            }
        }

        // Barter preview
        if (offer == null && item.hasBarterRecipes()) {
            ShopUiUtil.renderPill(graphics, this.font, contentX, nextY,
                    Component.translatable("gui.futureshops.item_detail.barter_available").getString(),
                    ShopColors.SURFACE_OVERLAY, ShopColors.TEXT_BARTER_SOFT, ShopColors.TEXT_BARTER_SOFT);
            nextY += 16;
            renderBarterPreview(graphics, item, contentX, contentW, nextY, infoY + infoH - 20);
        }
    }

    private int renderOfferBadges(
            GuiGraphics graphics,
            ServerShopOfferListing offer,
            int x,
            int width,
            int y
    ) {
        List<String> badges = offerBadges(offer);
        int cursorX = x;
        int cursorY = y;
        for (String badge : badges) {
            int badgeWidth = Math.max(24, this.font.width(badge) + 12);
            if (cursorX > x && cursorX + badgeWidth > x + width) {
                cursorX = x;
                cursorY += 14;
            }
            ShopUiUtil.renderPill(graphics, this.font,
                    cursorX, cursorY, badge,
                    ShopColors.SURFACE_OVERLAY,
                    ShopColors.ACCENT_PRIMARY,
                    ShopColors.ACCENT_PRIMARY);
            cursorX += badgeWidth + 4;
        }
        return cursorY + (badges.isEmpty() ? 0 : 16);
    }

    private List<String> offerBadges(ServerShopOfferListing offer) {
        List<String> badges = new ArrayList<>();
        if (offer.sellOnly()) {
            badges.add(I18n.get("gui.futureshops.offer.sell_only"));
        } else {
            boolean free = offer.acquireOptions().stream()
                    .anyMatch(AcquireOfferOption::free);
            boolean compound = offer.acquireOptions().stream()
                    .anyMatch(AcquireOfferOption::compound);
            boolean money = offer.acquireOptions().stream()
                    .anyMatch(AcquireOfferOption::moneyCostPresent);
            boolean barter = offer.acquireOptions().stream()
                    .anyMatch(AcquireOfferOption::hasItemCosts);
            if (free) {
                badges.add(I18n.get("gui.futureshops.offer.free"));
            }
            if (compound) {
                badges.add(I18n.get(
                        "gui.futureshops.offer.money_and_barter"));
            } else {
                if (money) {
                    badges.add(I18n.get(
                            "gui.futureshops.offer.money"));
                }
                if (barter) {
                    badges.add(I18n.get(
                            "gui.futureshops.offer.barter"));
                }
            }
        }
        if (offer.bundle()) {
            badges.add(I18n.get("gui.futureshops.offer.bundle"));
        }
        return badges;
    }

    private Optional<com.enviouse.futureshops.catalog.offer
            .ServerShopBundleSavings.Snapshot> calculateBundleSavings(
            ServerShopOfferListing offer
    ) {
        return offer.acquireOptions().stream()
                .filter(AcquireOfferOption::moneyCostPresent)
                .map(option ->
                        com.enviouse.futureshops.catalog.offer
                                .ServerShopBundleSavings.calculate(
                                        offer, option, getQuantity(),
                                        ShopClientState.getCatalogOffers()
                                                .stream()
                                                .collect(java.util.stream
                                                        .Collectors.toMap(
                                                                ServerShopOfferListing::listingId,
                                                                value -> value,
                                                                (first, second) ->
                                                                        first)),
                                        java.time.Instant.now()))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private void renderBundleComponents(
            GuiGraphics graphics,
            ServerShopOfferListing offer,
            int x,
            int y,
            int width,
            int bottom
    ) {
        int height = bottom - y;
        if (height < 10) {
            return;
        }
        int rowHeight = 10;
        int visible = Math.max(1, height / rowHeight);
        bundleComponentScroll = Math.max(0,
                Math.min(bundleComponentScroll,
                        Math.max(0, offer.outputs().size() - visible)));
        bundleComponentRect = new int[]{x, y, width, height};
        graphics.enableScissor(x, y, x + width, bottom);
        for (int row = 0; row < visible; row++) {
            int index = bundleComponentScroll + row;
            if (index >= offer.outputs().size()) {
                break;
            }
            OfferItemComponent component = offer.outputs().get(index);
            String name = ShopUiUtil.getItemDisplayNameWithNbt(
                    component.itemId(), component.exactNbt());
            String line = I18n.get("gui.futureshops.offer.receive",
                    name, component.count());
            graphics.drawString(this.font,
                    this.font.plainSubstrByWidth(line, width - 10),
                    x, y + row * rowHeight,
                    ShopColors.TEXT_SECONDARY, false);
        }
        graphics.disableScissor();
        ShopUiUtil.renderScrollIndicators(graphics, this.font,
                x, y, width, height, bundleComponentScroll,
                visible, offer.outputs().size());
    }

    private void renderBottomArea(GuiGraphics graphics, CatalogItem item) {
        // Total is now rendered inside renderPreviewPanel — nothing else needed here
    }

    /** Flat Nocturne action row + quantity steppers (formerly vanilla Buttons). */
    private void renderActionButtons(GuiGraphics graphics, CatalogItem item, int mouseX, int mouseY) {
        // Top-right close (the dialog previously had no on-screen exit — only ESC).
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 18, guiTop + 4, 14, 14, Component.literal("✕"),
                ShopUiUtil.ButtonStyle.GHOST, true, this::onClose);

        int previewCenterX = guiLeft + 8 + PREVIEW_W / 2;
        int qtyY = guiTop + guiH - 32 - 42;

        // Quantity steppers: −  [box]  +  Max
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                previewCenterX - 48, qtyY, 14, 14, Component.literal("-"), ShopUiUtil.ButtonStyle.SECONDARY, true,
                () -> setQuantity(getQuantity() - 1));
        boolean plusHover = ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                previewCenterX + 10, qtyY, 14, 14, Component.literal("+"), ShopUiUtil.ButtonStyle.SECONDARY, true,
                () -> {
                    if (hasShiftDown()) setQuantity(resolveMaxQuantity());
                    else setQuantity(getQuantity() + 1);
                });
        if (plusHover) {
            pendingButtonTooltip = Component.translatable("gui.futureshops.cart.tooltip.shift_max");
        }
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                previewCenterX + 28, qtyY, 26, 14, Component.translatable("gui.futureshops.barter.max"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, () -> setQuantity(resolveMaxQuantity()));

        // Action row: [+ Cart] [Buy] [Sell] [Barter]
        int bottomY = guiTop + guiH - 22;
        int btnW = 52;
        int gap = 4;
        int totalBtnW = btnW * 4 + gap * 3;
        int startX = guiLeft + (guiW - totalBtnW) / 2;

        ServerShopOfferListing offer = currentOffer();
        boolean buyEnabled = offer == null
                ? item.buyPrice() > 0L
                && (item.unlimited() || item.stock() > 0)
                : !offer.acquireOptions().isEmpty()
                && (item.unlimited() || item.stock() > 0)
                && ShopClientPacketHandler.pendingServerShopOffer()
                .isEmpty();
        // NBT-strict count: the server sell path only accepts the listing's exact tagged variant.
        // Fall back to the server-pushed owned count (blank-NBT listings only, since that count is
        // not NBT-aware) so the Sell button lights up right after a buy without waiting for the
        // client inventory to re-sync / reopening the screen.
        int owned = ShopUiUtil.countPlayerInventoryNbt(item.itemId(), item.nbtJson(), true);
        if (owned == 0 && (item.nbtJson() == null || item.nbtJson().isBlank())) {
            owned = ShopClientState.getOwnedCount(item.itemId());
        }
        boolean sellEnabled = offer == null
                ? item.sellPrice() > 0L && owned > 0
                : offer.sellOptions().stream().anyMatch(
                        this::hasSellInputs)
                && ShopClientPacketHandler.pendingServerShopOffer()
                .isEmpty();

        if (offer != null) {
            AcquireOfferOption acquire =
                    offer.acquireOptions().size() == 1
                            ? offer.acquireOptions().get(0) : null;
            Component acquireLabel = acquire != null && acquire.free()
                    ? Component.translatable(
                    "gui.futureshops.offer.get")
                    : offer.acquireOptions().size() > 1
                    ? Component.translatable(
                    "gui.futureshops.offer.options")
                    : Component.translatable(
                    "gui.futureshops.item_detail.buy");
            Component sellLabel = offer.sellOptions().size() > 1
                    ? Component.translatable(
                    "gui.futureshops.offer.sell_options")
                    : Component.translatable(
                    "gui.futureshops.offer.sell_to_shop");
            boolean showAcquire = !offer.acquireOptions().isEmpty();
            boolean showCart =
                    ServerShopOfferVisitorActionPolicy
                            .showsAcquireCart(offer);
            boolean showSell = !offer.sellOptions().isEmpty();
            int actionCount = (showCart ? 1 : 0)
                    + (showAcquire ? 1 : 0)
                    + (showSell ? 1 : 0);
            int actionWidth = Math.min(82,
                    (guiW - 24 - gap * Math.max(0, actionCount - 1))
                            / Math.max(1, actionCount));
            int actionX = guiLeft
                    + (guiW - actionWidth * actionCount
                    - gap * Math.max(0, actionCount - 1)) / 2;
            if (showCart) {
                renderOfferActionButton(graphics, mouseX, mouseY,
                        actionX, bottomY, actionWidth,
                        Component.translatable(
                                "gui.futureshops.item_detail.add_cart"),
                        ShopUiUtil.ButtonStyle.PRIMARY, buyEnabled,
                        () -> openAcquireCartOptions(offer),
                        buyDisabledReason(item));
                actionX += actionWidth + gap;
            }
            if (showAcquire) {
                renderOfferActionButton(graphics, mouseX, mouseY,
                        actionX, bottomY, actionWidth, acquireLabel,
                        ShopUiUtil.ButtonStyle.PRIMARY, buyEnabled,
                        () -> openAcquireOptions(offer),
                        buyDisabledReason(item));
                actionX += actionWidth + gap;
            }
            if (showSell) {
                renderOfferActionButton(graphics, mouseX, mouseY,
                        actionX, bottomY, actionWidth, sellLabel,
                        ShopUiUtil.ButtonStyle.SECONDARY, sellEnabled,
                        () -> openSellOptions(offer),
                        Component.translatable(
                                "gui.futureshops.offer.result.rejected"));
            }
            return;
        }

        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                startX, bottomY, btnW, 14, Component.translatable("gui.futureshops.item_detail.add_cart"),
                ShopUiUtil.ButtonStyle.PRIMARY, buyEnabled,
                () -> { CatalogItem it = currentItem(); if (it != null) ShopClientState.addToCart(it.listingId(), getQuantity()); });
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                startX + btnW + gap, bottomY, btnW, 14, Component.translatable("gui.futureshops.item_detail.buy"),
                ShopUiUtil.ButtonStyle.PRIMARY, buyEnabled, () -> openBuyConfirm(currentItem()));
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                startX + (btnW + gap) * 2, bottomY, btnW, 14, Component.translatable("gui.futureshops.item_detail.sell"),
                ShopUiUtil.ButtonStyle.SECONDARY, sellEnabled, () -> openSellConfirm(currentItem()));
        // Barter only when the listing has recipes (former barterButton.visible gate).
        if (item.hasBarterRecipes()) {
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    startX + (btnW + gap) * 3, bottomY, btnW, 14, Component.translatable("gui.futureshops.item_detail.barter"),
                    ShopUiUtil.ButtonStyle.SECONDARY, true,
                    () -> { CatalogItem it = currentItem(); if (it != null) this.minecraft.setScreen(new BarterScreen(this, it.itemId())); });
        }
    }

    private void renderOfferActionButton(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int width,
            Component label,
            ShopUiUtil.ButtonStyle style,
            boolean enabled,
            Runnable action,
            Component disabledReason
    ) {
        ShopUiUtil.button(graphics, this.font, clickZones,
                mouseX, mouseY, x, y, width, 14, label,
                style, enabled, action);
        if (!enabled && mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + 14) {
            pendingButtonTooltip = disabledReason;
        }
    }

    private Component buyDisabledReason(CatalogItem item) {
        if (!item.unlimited() && item.stock() <= 0) {
            return Component.translatable(
                    "gui.futureshops.offer.result.out_of_stock");
        }
        return Component.translatable(
                "gui.futureshops.offer.result.unavailable");
    }

    private void renderBarterPreview(GuiGraphics graphics, CatalogItem item, int contentX, int contentW, int startY, int maxY) {
        // Prefer the recipe whose resolved targetListingId rewards THIS listing (exact NBT
        // variant); fall back to the first registry-id match for legacy recipes.
        List<CatalogBarterRecipe> recipes = ShopClientState.getBarterRecipesForItem(item.itemId());
        CatalogBarterRecipe previewRecipe = recipes.stream()
                .filter(recipe -> item.listingId().equals(recipe.targetListingId()))
                .findFirst()
                .orElseGet(() -> recipes.stream().findFirst().orElse(null));
        if (previewRecipe == null) return;

        int nextY = startY;
        List<CatalogBarterIngredient> ingredients = previewRecipe.ingredients();
        int shown = 0;
        for (CatalogBarterIngredient ingredient : ingredients) {
            if (shown >= 3 || nextY > maxY) {
                graphics.drawString(this.font, "§7...", contentX, nextY, ShopColors.TEXT_SECONDARY, false);
                break;
            }
            // NBT-strict owned count / display name when the ingredient pins a tag — mirrors
            // the BarterScreen and what the server will accept.
            int owned = ShopUiUtil.countPlayerInventoryNbt(
                    ingredient.itemId(), ingredient.nbtJson(), !ingredient.nbtJson().isBlank());
            int needed = ingredient.count() * getQuantity();
            int color = owned >= needed ? ShopColors.SUCCESS : ShopColors.ERROR;
            String label = this.font.plainSubstrByWidth(
                    ShopUiUtil.getItemDisplayNameWithNbt(ingredient.itemId(), ingredient.nbtJson()) + " ×" + needed,
                    contentW - 50);
            graphics.drawString(this.font, label, contentX, nextY, ShopColors.TEXT_BARTER, false);
            String haveStr = this.font.plainSubstrByWidth(Component.translatable("gui.futureshops.item_detail.have", owned).getString(), 44);
            graphics.drawString(this.font, haveStr, contentX + contentW - 44, nextY, color, false);
            nextY += 10;
            shown++;
        }
    }

    private void drawInfoLine(GuiGraphics graphics, String label, String value, int x, int w, int y, int valueColor) {
        graphics.drawString(this.font, label, x, y, ShopColors.TEXT_SECONDARY, false);
        String clipped = this.font.plainSubstrByWidth(value, w - 40);
        graphics.drawString(this.font, clipped, x + Math.max(36, w - this.font.width(clipped) - 4), y, valueColor, false);
    }

    private String acquireSummary(ServerShopOfferListing offer) {
        if (offer.acquireOptions().isEmpty()) {
            return "—";
        }
        if (offer.acquireOptions().size() > 1) {
            return I18n.get("gui.futureshops.offer.option_count",
                    offer.acquireOptions().size());
        }
        return ServerShopOfferPresentation.acquireCostSummary(
                offer.acquireOptions().get(0),
                ShopClientState.getCurrencyName());
    }

    private String sellSummary(ServerShopOfferListing offer) {
        if (offer.sellOptions().isEmpty()) {
            return "—";
        }
        if (offer.sellOptions().size() > 1) {
            return I18n.get("gui.futureshops.offer.option_count",
                    offer.sellOptions().size());
        }
        return ServerShopOfferPresentation.sellPayoutSummary(
                offer.sellOptions().get(0),
                ShopClientState.getCurrencyName());
    }

    private void openAcquireOptions(ServerShopOfferListing offer) {
        if (offer.acquireOptions().size() == 1) {
            openAcquireOfferConfirm(offer.acquireOptions().get(0));
            return;
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ServerShopOfferOptionScreen(
                    this, offer.listingId(),
                    OfferAction.ACQUIRE_FROM_SHOP));
        }
    }

    private void openAcquireCartOptions(ServerShopOfferListing offer) {
        if (offer.acquireOptions().size() == 1) {
            addAcquireOfferToCart(offer.acquireOptions().get(0));
            return;
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ServerShopOfferOptionScreen(
                    this, offer.listingId(),
                    OfferAction.ACQUIRE_FROM_SHOP, true));
        }
    }

    private void openSellOptions(ServerShopOfferListing offer) {
        List<SellOfferOption> available = offer.sellOptions().stream()
                .filter(this::hasSellInputs).toList();
        if (available.isEmpty()) {
            return;
        }
        if (available.size() == 1) {
            openSellOfferConfirm(available.get(0));
            return;
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ServerShopOfferOptionScreen(
                    this, offer.listingId(),
                    OfferAction.SELL_TO_SHOP, false,
                    available.stream().map(SellOfferOption::optionId)
                            .toList()));
        }
    }

    private boolean hasSellInputs(SellOfferOption option) {
        int quantity = getQuantity();
        return option.itemInputs().stream().allMatch(component ->
                ShopUiUtil.countPlayerInventoryNbt(
                        component.itemId(), component.exactNbt(),
                        component.exactMatch())
                        >= (long) component.count() * quantity);
    }

    private Optional<ServerShopOfferListing> currentOfferOptional() {
        return ShopClientState.getCatalogOffer(listingId);
    }

    private ServerShopOfferListing currentOffer() {
        return currentOfferOptional().orElse(null);
    }

    private CatalogItem currentItem() {
        return ShopClientState.getCatalogItem(listingId).orElse(null);
    }

    private int getQuantity() {
        try {
            return clampQuantity(Integer.parseInt(quantityBox.getValue()));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private void setQuantity(int quantity) {
        quantityBox.setValue(Integer.toString(clampQuantity(quantity)));
    }

    private int resolveMaxQuantity() {
        CatalogItem item = currentItem();
        if (item == null) return 1;

        ServerShopOfferListing offer = currentOffer();
        if (offer != null) {
            int listingMaximum = Math.min(
                    OfferLimitPolicy.DEFAULT_MAXIMUM_PER_REQUEST,
                    offer.limits().maximumPerRequest());
            int acquireMaximum = offer.acquireOptions().stream()
                    .mapToInt(option -> {
                        int maximum = Math.min(listingMaximum,
                                option.limits().maximumPerRequest());
                        if (!item.unlimited()) {
                            maximum = Math.min(maximum,
                                    item.stock()
                                            / option.outputMultiplier());
                        }
                        return maximum;
                    }).max().orElse(0);
            int sellMaximum = offer.sellOptions().stream()
                    .mapToInt(option -> {
                        int maximum = Math.min(listingMaximum,
                                option.limits().maximumPerRequest());
                        if (option.capacity() > 0L) {
                            maximum = (int) Math.min(maximum,
                                    option.capacity());
                        }
                        for (OfferItemComponent component
                                : option.itemInputs()) {
                            int owned = ShopUiUtil
                                    .countPlayerInventoryNbt(
                                            component.itemId(),
                                            component.exactNbt(),
                                            component.exactMatch());
                            maximum = Math.min(maximum,
                                    owned / component.count());
                        }
                        return maximum;
                    }).max().orElse(0);
            return Math.max(1,
                    Math.max(acquireMaximum, sellMaximum));
        }

        // For selling: limited by how many the player has in inventory (NBT-strict, matching the server)
        int sellLimit = ShopUiUtil.countPlayerInventoryNbt(item.itemId(), item.nbtJson(), true);
        return PurchaseQuantityPolicy.serverShopMaximum(
                item.unlimited(), item.stock(), sellLimit);
    }

    private int clampQuantity(int quantity) {
        return Math.max(1, Math.min(resolveMaxQuantity(), quantity));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (confirmationModal != null) {
            return confirmationModal.mouseClicked(mouseX, mouseY, button, this.font);
        }
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        if (confirmationModal != null
                && confirmationModal.mouseScrolled(
                mouseX, mouseY, delta)) {
            return true;
        }
        if (inRect(bundleComponentRect, mouseX, mouseY)) {
            bundleComponentScroll = Math.max(0,
                    bundleComponentScroll
                            + (delta < 0.0D ? 1 : -1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private static boolean inRect(
            int[] rectangle,
            double mouseX,
            double mouseY
    ) {
        return rectangle != null
                && mouseX >= rectangle[0]
                && mouseX < rectangle[0] + rectangle[2]
                && mouseY >= rectangle[1]
                && mouseY < rectangle[1] + rectangle[3];
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmationModal != null) {
            if (confirmationModal.keyPressed(keyCode)) return true;
            return true; // block all keys while modal open
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Called by the client packet handler when a buy/sell response arrives.
     * Updates the confirmation modal state accordingly.
     */
    public void onTransactionResult(boolean success, String message) {
        if (confirmationModal != null) {
            if (success) {
                confirmationModal.setSuccess(message);
            } else {
                confirmationModal.setFailed(message);
            }
        }
    }

    public void onSellTransactionResult(
            java.util.UUID requestId,
            boolean success,
            String message
    ) {
        if (requestId == null || !requestId.equals(pendingSellRequestId)) {
            return;
        }
        pendingSellRequestId = null;
        onTransactionResult(success, message);
    }

    public void onOfferTransactionResult(
            java.util.UUID requestId,
            boolean success,
            String message,
            com.enviouse.futureshops.server.escrow.runtime
                    .ServerShopOfferService.Status status
    ) {
        if (requestId == null
                || !requestId.equals(pendingOfferRequestId)) {
            return;
        }
        if (status
                != com.enviouse.futureshops.server.escrow.runtime
                .ServerShopOfferService.Status.RECOVERY_REQUIRED
                && status
                != com.enviouse.futureshops.server.escrow.runtime
                .ServerShopOfferService.Status.QUARANTINED) {
            pendingOfferRequestId = null;
        }
        if (status == com.enviouse.futureshops.server.escrow.runtime
                .ServerShopOfferService.Status.STALE_REVISION) {
            message = I18n.get(
                    "gui.futureshops.offer.quote_changed");
        }
        onTransactionResult(success, message);
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
