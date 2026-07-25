package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.client.CartResponsePolicy;
import com.enviouse.futureshops.data.CatalogItem;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SBuyRequestPacket;
import com.enviouse.futureshops.network.packets.C2SServerShopOfferCartPacket;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.network.packets.C2SVerifyAdminCartPacket;
import com.enviouse.futureshops.network.packets.S2CVerifyCartResponsePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CartScreen extends Screen implements ShopScreenMarker {
    private final Screen parent;
    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private int visibleRows;
    private int scrollIndex;
    private boolean awaitingVerification = false;
    private ConfirmationModal confirmationModal = null;

    // Flat Nocturne buttons: draw + hit-region come from the same ShopUiUtil.button call,
    // registered here each frame and consulted in mouseClicked via dispatchClicks.
    private final java.util.List<ShopUiUtil.ClickZone> clickZones = new java.util.ArrayList<>();
    // Set during renderRows when the "+" stepper is hovered, so the Shift=Max tooltip can be
    // drawn on top after super.render (geometry now comes straight from the stepper button).
    private int maxTipX = -1;
    private int maxTipY = -1;

    public CartScreen(Screen parent) {
        super(Component.translatable("gui.futureshops.cart.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Right-side drawer (Nocturne cart): a fixed-width panel anchored to the right edge over
        // the dimmed ground, rather than a full-screen takeover.
        guiW = Math.min(360, this.width - 8);
        guiH = Math.max(250, this.height - 20);
        guiLeft = this.width - guiW - 8;
        guiTop = (this.height - guiH) / 2;
        // Reserve a taller footer band (rows list shrunk to guiH-150 in renderRows) so verification
        // warnings have somewhere to go below the card; keep visibleRows in step so rows never
        // overflow the shorter card into that band.
        visibleRows = Math.max(3, (guiH - 170) / 28);
        // Footer buttons are drawn immediate-mode in render(); no widgets to register here.
    }

    @Override
    public void tick() {
        super.tick();
        if (ShopClientState.expireCartCheckout(System.currentTimeMillis())
                == CartResponsePolicy.TimeoutDecision.TIMED_OUT) {
            onTransactionResult(false,
                    Component.translatable("gui.futureshops.cart.checkout_timeout").getString());
        }
        if (awaitingVerification && ShopClientState.isCartVerified()) {
            awaitingVerification = false;
            List<S2CVerifyCartResponsePacket.CartWarning> warnings = ShopClientState.getCartWarnings();
            if (warnings.isEmpty()) {
                // All OK — show confirmation modal before checkout
                showCheckoutModal();
            }
            // If warnings exist, they'll render — user must click Checkout again to force
        }
    }

    private void showCheckoutModal() {
        List<ShopClientState.CartEntry> entries = ShopClientState.getCartEntries();
        List<ConfirmationModal.SummaryLine> lines = new java.util.ArrayList<>();
        for (ShopClientState.CartEntry entry : entries) {
            CatalogItem item = ShopClientState.getCatalogItem(entry.listingId()).orElse(null);
            com.enviouse.futureshops.catalog.offer.ServerShopOfferListing
                    offer = ShopClientState.getCatalogOffer(
                    entry.listingId()).orElse(null);
            if (entry.normalized() && offer != null) {
                appendOfferSummary(lines, entry, offer);
                continue;
            }
            String name = item != null ? item.displayName()
                    : offer != null ? offer.displayName()
                    : entry.listingId();
            // Icon id must be a registry itemId (a valid ResourceLocation); the listingId is only the
            // cart key and may not parse. Fall back to the listingId string only when the row is unknown.
            String iconId = item != null ? item.itemId()
                    : offer != null ? offer.iconItemId()
                    : entry.listingId();
            String nbt = item != null ? item.nbtJson()
                    : offer != null ? offer.iconNbt() : "";
            lines.add(ConfirmationModal.SummaryLine.item(iconId, name + " ×" + entry.quantity(), nbt));
        }
        boolean requiresMoney = entries.stream()
                .anyMatch(this::requiresMoney);
        String totalText;
        if (requiresMoney) {
            String totalStr = ShopUiUtil.formatMinorUnits(
                    ShopClientState.getCartTotalMinorUnits());
            totalText = Component.translatable(
                    "gui.futureshops.item_detail.total_cost",
                    totalStr, ShopClientState.getCurrencyName()).getString();
        } else {
            boolean requiresItems = entries.stream().anyMatch(entry ->
                    entry.normalized()
                            && ShopClientState.getCatalogOffer(
                            entry.listingId()).stream()
                            .flatMap(offer ->
                                    offer.acquireOptions().stream())
                            .anyMatch(option ->
                                    option.optionId().equals(
                                            entry.optionId())
                                            && option.hasItemCosts()));
            totalText = Component.translatable(requiresItems
                    ? "gui.futureshops.offer.barter_only"
                    : "gui.futureshops.offer.free").getString();
        }
        if (requiresMoney) {
            confirmationModal = new ConfirmationModal(
                    Component.translatable(
                            "gui.futureshops.cart.confirm.title")
                            .getString(),
                    lines, totalText,
                    (modal, paymentSource) -> {
                        modal.setProcessing();
                        sendCheckout(Optional.of(paymentSource));
                    },
                    () -> confirmationModal = null);
        } else {
            confirmationModal = new ConfirmationModal(
                    Component.translatable(
                            "gui.futureshops.cart.confirm.title")
                            .getString(),
                    lines, totalText,
                    modal -> {
                        modal.setProcessing();
                        sendCheckout(Optional.empty());
                    },
                    () -> confirmationModal = null);
        }
    }

    private void appendOfferSummary(
            List<ConfirmationModal.SummaryLine> lines,
            ShopClientState.CartEntry entry,
            com.enviouse.futureshops.catalog.offer
                    .ServerShopOfferListing offer
    ) {
        var option = offer.acquireOptions().stream()
                .filter(candidate -> candidate.optionId()
                        .equals(entry.optionId()))
                .findFirst().orElse(null);
        if (option == null) {
            lines.add(ConfirmationModal.SummaryLine.text(
                    Component.translatable(
                            "gui.futureshops.offer.quote_changed")
                            .getString()));
            return;
        }
        lines.add(ConfirmationModal.SummaryLine.text(
                offer.displayName() + " ×" + entry.quantity()));
        for (var output : offer.outputs()) {
            int count = Math.multiplyExact(
                    Math.multiplyExact(output.count(),
                            option.outputMultiplier()),
                    entry.quantity());
            lines.add(offerComponentLine(output, count,
                    "gui.futureshops.offer.receive"));
        }
        if (option.moneyCostPresent()
                || !option.itemCosts().isEmpty()) {
            lines.add(ConfirmationModal.SummaryLine.text(
                    Component.translatable(
                            "gui.futureshops.offer.all_required")
                            .getString()));
        }
        if (option.moneyCostPresent()) {
            long total = Math.multiplyExact(
                    option.moneyCostMinorUnits(),
                    (long) entry.quantity());
            lines.add(ConfirmationModal.SummaryLine.text(
                    Component.translatable(
                            "gui.futureshops.item_detail.total_cost",
                            ShopUiUtil.formatMinorUnits(total),
                            ShopClientState.getCurrencyName())
                            .getString()));
        }
        for (var input : option.itemCosts()) {
            lines.add(offerComponentLine(input,
                    Math.multiplyExact(
                            input.count(), entry.quantity()),
                    "gui.futureshops.offer.give"));
        }
        var listings = ShopClientState.getCatalogOffers().stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.enviouse.futureshops.catalog.offer
                                .ServerShopOfferListing::listingId,
                        value -> value,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new));
        com.enviouse.futureshops.catalog.offer.ServerShopBundleSavings
                .calculate(offer, option, entry.quantity(), listings,
                        java.time.Instant.now())
                .ifPresent(savings -> lines.add(
                        ConfirmationModal.SummaryLine.text(
                                Component.translatable(
                                        "gui.futureshops.offer.savings",
                                        ShopUiUtil.formatMinorUnits(
                                                savings
                                                        .individualTotalMinorUnits()),
                                        ShopUiUtil.formatMinorUnits(
                                                savings
                                                        .bundleTotalMinorUnits()),
                                        ShopUiUtil.formatMinorUnits(
                                                savings
                                                        .savingsMinorUnits()),
                                        savings.savingsBasisPoints()
                                                / 100.0D)
                                        .getString())));
    }

    private ConfirmationModal.SummaryLine offerComponentLine(
            com.enviouse.futureshops.catalog.offer.OfferItemComponent
                    component,
            int count,
            String translation
    ) {
        return ConfirmationModal.SummaryLine.item(
                component.itemId(),
                Component.translatable(
                        translation,
                        ShopUiUtil.getItemDisplayNameWithNbt(
                                component.itemId(),
                                component.exactNbt()),
                        count).getString(),
                component.exactNbt());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();
        maxTipX = -1;
        maxTipY = -1;
        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        ShopUiUtil.renderAccentLine(graphics, guiLeft + 2, guiTop, guiW - 4);
        renderHeader(graphics);
        renderRows(graphics, mouseX, mouseY);
        renderSummary(graphics);

        // Cart verification warnings — drawn TOP-DOWN in the band reserved just below the rows
        // card (listH was shrunk to guiH-150 for exactly this), clipped to a hard lower bound so
        // extra warnings can never spill back up over the last row or down onto the summary bar.
        List<S2CVerifyCartResponsePacket.CartWarning> warnings = ShopClientState.getCartWarnings();
        if (!warnings.isEmpty()) {
            int warnY = (guiTop + 52) + (guiH - 150) + 2; // just below the rows card
            int maxWarnY = guiTop + guiH - 68;            // stay above the summary bar
            int shown = 0;
            for (int wi = 0; wi < Math.min(warnings.size(), 3) && warnY <= maxWarnY; wi++) {
                S2CVerifyCartResponsePacket.CartWarning w = warnings.get(wi);
                // Localized on the client from the warning code + args (server sends no English).
                String warnText = ShopUiUtil.cartWarningLine(w.cartLineIndex(), w.warningCode(), w.detail()).getString();
                graphics.drawString(this.font, this.font.plainSubstrByWidth(warnText, guiW - 30),
                        guiLeft + 10, warnY, ShopColors.ERROR, false);
                warnY += 12;
                shown++;
            }
            if (warnings.size() > shown && warnY <= maxWarnY) {
                graphics.drawString(this.font, Component.translatable("gui.futureshops.cart.more_warnings", warnings.size() - shown),
                        guiLeft + 10, warnY, ShopColors.ERROR, false);
            }
        }

        // Awaiting verification indicator
        if (awaitingVerification) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.cart.verifying"),
                    guiLeft + guiW / 2, guiTop + guiH - 62, ShopColors.ACCENT_ORANGE);
        } else if (ShopClientState.isCartCheckoutPending()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.futureshops.cart.processing"),
                    guiLeft + guiW / 2, guiTop + guiH - 62, ShopColors.ACCENT_ORANGE);
        } else if (ShopClientState.hasTrackedCartCheckout()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.futureshops.cart.awaiting_result"),
                    guiLeft + guiW / 2, guiTop + guiH - 62, ShopColors.ACCENT_ORANGE);
        }

        // Status toast floats above the drawer, but when the drawer is flush to the top edge there
        // is no room there — drop it just below the window instead of clamping it onto the header.
        int statusY = (guiTop - 24 >= 6) ? (guiTop - 24) : (guiTop + guiH + 2);
        ShopUiUtil.renderStatusPanel(graphics, this.font, guiLeft + 10, statusY, guiW - 20);

        // Footer buttons — flat Nocturne primitives at their former bounds.
        boolean checkoutTracked = ShopClientState.hasTrackedCartCheckout();
        boolean canCheckout = !ShopClientState.getCartEntries().isEmpty()
                && !checkoutTracked
                && !awaitingVerification;
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 10, guiTop + guiH - 24, 48, 18,
                Component.translatable("gui.futureshops.cart.back"), ShopUiUtil.ButtonStyle.SECONDARY, true, this::onClose);
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 64, guiTop + guiH - 24, 48, 18,
                Component.translatable("gui.futureshops.cart.clear_btn"), ShopUiUtil.ButtonStyle.DANGER,
                !checkoutTracked, () -> {
                    ShopClientState.clearCart();
                    ShopClientState.clearCartVerification();
                });
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 100, guiTop + guiH - 24, 90, 18,
                Component.translatable("gui.futureshops.cart.checkout_btn"), ShopUiUtil.ButtonStyle.PRIMARY, canCheckout, this::requestVerifyAndCheckout);

        super.render(graphics, mouseX, mouseY, partialTick);

        // Tooltip for cart "+" hover (Shift+Click: Max) — geometry captured by the stepper button.
        if (maxTipX >= 0) {
            graphics.renderTooltip(this.font, Component.translatable("gui.futureshops.cart.tooltip.shift_max"), maxTipX, maxTipY);
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
        int hy = guiTop + 8;
        int hw = guiW - 16;
        ShopUiUtil.renderHeroHeader(graphics, this.font, hx, hy, hw,
                this.title.getString(),
                Component.translatable("gui.futureshops.cart.subtitle").getString());
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int listX = guiLeft + 10;
        int listY = guiTop + 52;
        int listW = guiW - 20;
        // Shrunk by 30px vs. the old guiH-120 to reserve a warning band below the card (Fix 1).
        int listH = guiH - 150;
        ShopUiUtil.renderCard(graphics, listX, listY, listW, listH);

        List<ShopClientState.CartEntry> entries = ShopClientState.getCartEntries();
        boolean checkoutTracked = ShopClientState.hasTrackedCartCheckout();
        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.cart.empty"), listX + listW / 2, listY + listH / 2 - 8, ShopColors.TEXT_MUTED);
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.cart.empty_hint"), listX + listW / 2, listY + listH / 2 + 6, ShopColors.TEXT_FAINT);
            return;
        }

        int maxScroll = Math.max(0, entries.size() - visibleRows);
        scrollIndex = Math.max(0, Math.min(scrollIndex, maxScroll));
        int rowY = listY + 8;
        for (int row = 0; row < visibleRows && row + scrollIndex < entries.size(); row++) {
            ShopClientState.CartEntry entry = entries.get(row + scrollIndex);
            CatalogItem item = ShopClientState.getCatalogItem(entry.listingId()).orElse(null);
            com.enviouse.futureshops.catalog.offer.ServerShopOfferListing
                    offer = ShopClientState.getCatalogOffer(
                    entry.listingId()).orElse(null);
            if (item == null && offer == null) continue;
            int y = rowY + row * 28;
            int rowBg = row % 2 == 0 ? ShopColors.SURFACE_RAISED : ShopColors.SURFACE_OVERLAY;
            ShopUiUtil.renderPanel(graphics, listX + 6, y, listW - 12, 22, rowBg, ShopColors.BORDER_SUBTLE);
            // NBT-aware icon: BEWLR items (TacZ guns etc.) resolve their model
            // from the listing tag and render as a missing texture without it.
            ShopUiUtil.renderItemIconWithNbt(
                    graphics, this.font,
                    item != null ? item.itemId() : offer.iconItemId(),
                    item != null ? item.nbtJson() : offer.iconNbt(),
                    listX + 10, y + 3);

            // Name — scrolls (ping-pong) when too long so modded items with lengthy names stay readable.
            ShopUiUtil.renderScrollingString(graphics, this.font,
                    item != null ? item.displayName()
                            : offer.displayName(),
                    listX + 30, y + 7, listW - 240, ShopColors.TEXT_STRONG);

            // Quantity controls — flat mini steppers ([−] [N] [+]) with registered ClickZones.
            final ShopClientState.CartEntry rowEntry = entry;
            int ctrlX = listX + listW - 130;
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    ctrlX, y + 3, 14, 16, Component.literal("-"), ShopUiUtil.ButtonStyle.SECONDARY,
                    !checkoutTracked,
                    () -> ShopClientState.setCartQuantity(
                            rowEntry, rowEntry.quantity() - 1));
            // Wider (18px) qty window between the steppers so 3-4 digit quantities no longer
            // slide under the "+"; the value is clipped and centered inside that window.
            int qtyWinX = ctrlX + 16;
            int qtyWinW = 18;
            String qtyStr = this.font.plainSubstrByWidth(String.valueOf(entry.quantity()), qtyWinW);
            graphics.drawString(this.font, qtyStr, qtyWinX + (qtyWinW - this.font.width(qtyStr)) / 2, y + 7, ShopColors.TEXT_STRONG, false);
            boolean plusHover = ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    ctrlX + 34, y + 3, 14, 16, Component.literal("+"), ShopUiUtil.ButtonStyle.SECONDARY,
                    !checkoutTracked,
                    () -> {
                        if (hasShiftDown()) {
                            ShopClientState.setCartQuantity(
                                    rowEntry,
                                    maximumQuantity(rowEntry));
                        } else {
                            ShopClientState.setCartQuantity(
                                    rowEntry, rowEntry.quantity() + 1);
                        }
                    });
            if (plusHover) {
                maxTipX = mouseX;
                maxTipY = mouseY;
            }

            // Price — currency amber
            long unitPrice = unitMoneyCost(entry);
            // Start clear of the shifted "+" stepper (now ends at listW-82) and clip a touch
            // tighter so the price still stops short of the remove button.
            String priceStr = this.font.plainSubstrByWidth(ShopUiUtil.formatMinorUnits(unitPrice * entry.quantity()), 54);
            graphics.drawString(this.font, priceStr, listX + listW - 80, y + 7, ShopColors.TEXT_CURRENCY, false);

            // Remove — flat DANGER "✕" button with a registered ClickZone.
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    listX + listW - 24, y + 3, 16, 16, Component.literal("✕"), ShopUiUtil.ButtonStyle.DANGER,
                    !checkoutTracked,
                    () -> ShopClientState.removeFromCart(rowEntry));
        }
    }

    private void renderSummary(GuiGraphics graphics) {
        int x = guiLeft + 10;
        int y = guiTop + guiH - 56;
        int w = guiW - 20;
        graphics.fill(x, y, x + w, y + 24, ShopColors.SURFACE_RAISED);
        ShopUiUtil.drawBorder(graphics, x, y, w, 24, ShopColors.BORDER_MUTED);
        graphics.fill(x, y, x + w, y + 2, ShopColors.ACCENT_CURRENCY);
        // Item count — truncated
        String items = this.font.plainSubstrByWidth(
                Component.translatable("gui.futureshops.cart.items_in_cart", ShopClientState.getCartTotalQuantity()).getString(), w / 2 - 10);
        graphics.drawString(this.font, items, x + 10, y + 8, ShopColors.TEXT_MUTED, false);
        // Total — amber
        String total = Component.translatable("gui.futureshops.cart.total_line",
                ShopUiUtil.formatMinorUnits(ShopClientState.getCartTotalMinorUnits())).getString();
        String clipped = this.font.plainSubstrByWidth(total, w / 2);
        graphics.drawString(this.font, clipped, x + w - this.font.width(clipped) - 10, y + 8, ShopColors.TEXT_CURRENCY, false);
    }

    private void requestVerifyAndCheckout() {
        if (ShopClientState.isCartCheckoutPending() || awaitingVerification) return;
        if (ShopClientState.hasTrackedCartCheckout()) {
            onTransactionResult(false,
                    Component.translatable(
                            "gui.futureshops.cart.checkout_awaiting_result").getString());
            return;
        }
        List<ShopClientState.CartEntry> entries = ShopClientState.getCartEntries();
        if (entries.isEmpty()) return;
        boolean normalized = entries.stream()
                .anyMatch(ShopClientState.CartEntry::normalized);
        boolean legacy = entries.stream()
                .anyMatch(entry -> !entry.normalized());
        if (normalized && legacy) {
            onTransactionResult(false, Component.translatable(
                    "gui.futureshops.cart.mixed_legacy").getString());
            return;
        }
        if (normalized) {
            showCheckoutModal();
            return;
        }

        // If we already have warnings (user saw them), force checkout on second click
        if (!ShopClientState.getCartWarnings().isEmpty()) {
            ShopClientState.clearCartVerification();
            showCheckoutModal();
            return;
        }

        // Build verification lines from cart state
        String shopId = ShopClientState.getActiveShopId();
        List<C2SVerifyAdminCartPacket.AdminCartLine> lines = entries.stream()
                .map(e -> {
                    CatalogItem item = ShopClientState.getCatalogItem(e.listingId()).orElse(null);
                    long expectedPrice = 0;
                    if (item != null) {
                        expectedPrice = item.hasPromo() ? item.promoPrice() : item.buyPrice();
                    }
                    return new C2SVerifyAdminCartPacket.AdminCartLine(e.listingId(), e.quantity(), expectedPrice,
                            ShopClientState.getCartNbtSnapshot(e.listingId()));
                })
                .toList();
        ShopClientState.clearCartVerification();
        awaitingVerification = true;
        ShopPackets.CHANNEL.sendToServer(new C2SVerifyAdminCartPacket(shopId, lines));
    }

    private void sendCheckout(Optional<PaymentSource> paymentSource) {
        long nowMillis = System.currentTimeMillis();
        if (ShopClientState.hasTrackedCartCheckout()) {
            onTransactionResult(false,
                    Component.translatable(
                            "gui.futureshops.cart.checkout_awaiting_result").getString());
            return;
        }
        List<ShopClientState.CartEntry> cartEntries = ShopClientState.getCartEntries();
        if (!cartEntries.isEmpty()) {
            UUID requestId = UUID.randomUUID();
            CartResponsePolicy.BeginDecision decision = ShopClientState.beginCartCheckout(
                    requestId, cartEntries,
                    paymentSource.map(PaymentSource::wire)
                            .orElse(""), nowMillis);
            if (decision != CartResponsePolicy.BeginDecision.STARTED) {
                onTransactionResult(false,
                        Component.translatable("gui.futureshops.cart.checkout_pending").getString());
                return;
            }
            sendCheckoutSubmission(new ShopClientState.CartCheckoutSubmission(
                    requestId, ShopClientState.getActiveShopId(),
                    cartEntries, paymentSource
                    .map(PaymentSource::wire).orElse("")));
        }
    }

    private void sendCheckoutSubmission(ShopClientState.CartCheckoutSubmission submission) {
        if (submission.entries().stream()
                .allMatch(ShopClientState.CartEntry::normalized)) {
            Optional<PaymentSource> source =
                    submission.paymentSource().isBlank()
                            ? Optional.empty()
                            : PaymentSource.fromWire(
                            submission.paymentSource());
            ShopPackets.CHANNEL.sendToServer(
                    new C2SServerShopOfferCartPacket(
                            submission.shopId(),
                            submission.entries().stream()
                                    .map(entry ->
                                            new C2SServerShopOfferCartPacket
                                                    .Line(
                                                    entry.listingId(),
                                                    entry.optionId(),
                                                    entry.quantity(),
                                                    entry.observedRevision()))
                                    .toList(),
                            submission.requestId(), source));
            return;
        }
        List<C2SBuyRequestPacket.LineItem> lines = submission.entries().stream()
                .map(entry -> new C2SBuyRequestPacket.LineItem(
                        entry.listingId(), entry.quantity()))
                .toList();
        ShopPackets.CHANNEL.sendToServer(new C2SBuyRequestPacket(
                submission.shopId(), true, lines,
                submission.paymentSource(), submission.requestId()));
    }

    private boolean requiresMoney(ShopClientState.CartEntry entry) {
        if (!entry.normalized()) {
            return unitMoneyCost(entry) > 0L;
        }
        return ShopClientState.getCatalogOffer(entry.listingId())
                .stream().flatMap(listing ->
                        listing.acquireOptions().stream())
                .filter(option -> option.optionId()
                        .equals(entry.optionId()))
                .anyMatch(com.enviouse.futureshops.catalog.offer
                        .AcquireOfferOption::moneyCostPresent);
    }

    private long unitMoneyCost(ShopClientState.CartEntry entry) {
        if (!entry.normalized()) {
            CatalogItem item = ShopClientState.getCatalogItem(
                    entry.listingId()).orElse(null);
            return item == null ? 0L
                    : item.hasPromo()
                    ? item.promoPrice() : item.buyPrice();
        }
        return ShopClientState.getCatalogOffer(entry.listingId())
                .stream().flatMap(listing ->
                        listing.acquireOptions().stream())
                .filter(option -> option.optionId()
                        .equals(entry.optionId()))
                .filter(com.enviouse.futureshops.catalog.offer
                        .AcquireOfferOption::moneyCostPresent)
                .mapToLong(com.enviouse.futureshops.catalog.offer
                        .AcquireOfferOption::moneyCostMinorUnits)
                .findFirst().orElse(0L);
    }

    private int maximumQuantity(ShopClientState.CartEntry entry) {
        int maximum = 2304;
        if (!entry.normalized()) {
            CatalogItem item = ShopClientState.getCatalogItem(
                    entry.listingId()).orElse(null);
            if (item != null && !item.unlimited()) {
                maximum = Math.min(maximum,
                        Math.max(1, item.stock()));
            }
        } else {
            com.enviouse.futureshops.catalog.offer
                    .ServerShopOfferListing listing =
                    ShopClientState.getCatalogOffer(
                            entry.listingId()).orElse(null);
            if (listing != null) {
                if (listing.stockPolicy().type()
                        != com.enviouse.futureshops.catalog.offer
                        .OfferStockPolicy.Type.UNLIMITED) {
                    maximum = Math.min(maximum,
                            (int) Math.max(1L,
                                    listing.stockPolicy().quantity()));
                }
                com.enviouse.futureshops.catalog.offer
                        .AcquireOfferOption option =
                        listing.acquireOptions().stream()
                                .filter(value -> value.optionId()
                                        .equals(entry.optionId()))
                                .findFirst().orElse(null);
                if (option != null) {
                    maximum = Math.min(maximum,
                            Math.min(
                                    listing.limits()
                                            .maximumPerRequest(),
                                    option.limits()
                                            .maximumPerRequest()));
                }
            }
        }
        long unitCost = unitMoneyCost(entry);
        if (unitCost > 0L) {
            maximum = Math.min(maximum,
                    (int) Math.min(
                            ShopClientState
                                    .getCurrentBalanceMinorUnits()
                                    / unitCost,
                            2304L));
        }
        return Math.max(1, maximum);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        List<ShopClientState.CartEntry> entries = ShopClientState.getCartEntries();
        if (entries.size() > visibleRows) {
            scrollIndex = Math.max(0, Math.min(entries.size() - visibleRows, scrollIndex - (int) delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (confirmationModal != null) {
            return confirmationModal.mouseClicked(mouseX, mouseY, button, this.font);
        }
        // Flat Nocturne buttons (footer + per-row steppers/remove): run the top-most hit zone.
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmationModal != null) {
            if (confirmationModal.keyPressed(keyCode)) return true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void onTransactionResult(boolean success, String message) {
        ShopClientState.setStatus(Component.literal(message), success);
        if (confirmationModal != null) {
            if (success) {
                confirmationModal.setSuccess(message);
            } else {
                confirmationModal.setFailed(message);
            }
        }
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
