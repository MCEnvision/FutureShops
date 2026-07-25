package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.catalog.offer.SellOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class ServerShopOfferOptionScreen extends Screen
        implements ShopScreenMarker {
    private final ItemDetailScreen parent;
    private final Screen returnScreen;
    private final String listingId;
    private final OfferAction action;
    private final boolean addToCart;
    private final int fixedCartQuantity;
    private final java.util.Set<String> allowedOptionIds;
    private final List<ShopUiUtil.ClickZone> clickZones =
            new ArrayList<>();
    private int scroll;
    private Component pendingTooltip;

    public ServerShopOfferOptionScreen(
            ItemDetailScreen parent,
            String listingId,
            OfferAction action
    ) {
        this(parent, listingId, action, false, List.of());
    }

    public ServerShopOfferOptionScreen(
            ItemDetailScreen parent,
            String listingId,
            OfferAction action,
            boolean addToCart
    ) {
        this(parent, listingId, action, addToCart, List.of());
    }

    public ServerShopOfferOptionScreen(
            ItemDetailScreen parent,
            String listingId,
            OfferAction action,
            boolean addToCart,
            List<String> allowedOptionIds
    ) {
        this(parent, parent, listingId, action, addToCart,
                allowedOptionIds, 0);
    }

    private ServerShopOfferOptionScreen(
            ItemDetailScreen parent,
            Screen returnScreen,
            String listingId,
            OfferAction action,
            boolean addToCart,
            List<String> allowedOptionIds,
            int fixedCartQuantity
    ) {
        super(Component.translatable(
                "gui.futureshops.offer.choose_option"));
        this.parent = parent;
        this.returnScreen = java.util.Objects.requireNonNull(
                returnScreen, "returnScreen");
        this.listingId = java.util.Objects.requireNonNull(
                listingId, "listingId");
        this.action = java.util.Objects.requireNonNull(action, "action");
        this.addToCart = addToCart;
        this.allowedOptionIds = java.util.Set.copyOf(
                java.util.Objects.requireNonNull(
                        allowedOptionIds, "allowedOptionIds"));
        this.fixedCartQuantity = fixedCartQuantity;
    }

    public static ServerShopOfferOptionScreen quickCart(
            Screen returnScreen,
            String listingId
    ) {
        return new ServerShopOfferOptionScreen(
                null, returnScreen, listingId,
                OfferAction.ACQUIRE_FROM_SHOP, true,
                List.of(), 1);
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        clickZones.clear();
        pendingTooltip = null;
        ShopUiUtil.renderDimBackdrop(graphics, width, height);
        int panelW = Math.min(420, width - 24);
        int panelH = Math.min(320, height - 24);
        int left = (width - panelW) / 2;
        int top = (height - panelH) / 2;
        graphics.fill(left, top, left + panelW, top + panelH,
                ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, left, top, panelW, panelH,
                ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        ShopUiUtil.renderAccentLine(graphics, left + 2, top, panelW - 4);
        graphics.drawString(font, title, left + 12, top + 10,
                ShopColors.TEXT_STRONG, false);
        ShopUiUtil.button(graphics, font, clickZones, mouseX, mouseY,
                left + panelW - 22, top + 6, 16, 16,
                Component.translatable(
                        "gui.futureshops.offer_option.multiply"),
                ShopUiUtil.ButtonStyle.GHOST,
                true, this::onClose);

        ServerShopOfferListing offer = ShopClientState
                .getCatalogOffer(listingId).orElse(null);
        if (offer == null) {
            graphics.drawCenteredString(font,
                    Component.translatable(
                            "gui.futureshops.offer.quote_changed"),
                    width / 2, top + 48, ShopColors.ERROR);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        int rowY = top + 32;
        int rowH = 54;
        int visible = Math.max(1, (panelH - 66) / rowH);
        List<SellOfferOption> visibleSellOptions =
                visibleSellOptions(offer);
        int optionCount = action == OfferAction.ACQUIRE_FROM_SHOP
                ? offer.acquireOptions().size()
                : visibleSellOptions.size();
        scroll = Math.max(0, Math.min(scroll,
                Math.max(0, optionCount - visible)));
        for (int visibleIndex = 0; visibleIndex < visible;
             visibleIndex++) {
            int optionIndex = scroll + visibleIndex;
            if (optionIndex >= optionCount) {
                break;
            }
            int y = rowY + visibleIndex * rowH;
            ShopUiUtil.renderElevatedCard(
                    graphics, left + 10, y, panelW - 20, rowH - 6);
            if (action == OfferAction.ACQUIRE_FROM_SHOP) {
                renderAcquire(graphics, offer,
                        offer.acquireOptions().get(optionIndex),
                        left + 18, y + 7, panelW - 36,
                        mouseX, mouseY);
            } else {
                renderSell(graphics, offer,
                        visibleSellOptions.get(optionIndex),
                        left + 18, y + 7, panelW - 36,
                        mouseX, mouseY);
            }
            if (optionIndex + 1 < optionCount
                    && visibleIndex + 1 < visible) {
                graphics.drawCenteredString(font,
                        Component.translatable(
                                "gui.futureshops.offer.or"),
                        width / 2, y + rowH - 8,
                        ShopColors.TEXT_MUTED);
            }
        }
        ShopUiUtil.renderScrollIndicators(graphics, font,
                left + 10, rowY, panelW - 20,
                visible * rowH, scroll, visible, optionCount);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (pendingTooltip != null) {
            graphics.renderTooltip(font, pendingTooltip,
                    mouseX, mouseY);
        }
    }

    private List<SellOfferOption> visibleSellOptions(
            ServerShopOfferListing offer
    ) {
        if (allowedOptionIds.isEmpty()) {
            return offer.sellOptions();
        }
        return offer.sellOptions().stream()
                .filter(option -> allowedOptionIds.contains(
                        option.optionId()))
                .toList();
    }

    private void renderAcquire(
            GuiGraphics graphics,
            ServerShopOfferListing offer,
            AcquireOfferOption option,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY
    ) {
        graphics.drawString(font,
                option.label().isBlank()
                        ? option.optionId() : option.label(),
                x, y, ShopColors.TEXT_STRONG, false);
        String summary = ServerShopOfferPresentation
                .acquireSummary(option, offer,
                        ShopClientState.getCurrencyName());
        String clippedSummary = font.plainSubstrByWidth(
                summary, width - 90);
        graphics.drawString(font,
                clippedSummary,
                x, y + 15, ShopColors.TEXT_MUTED, false);
        if (!clippedSummary.equals(summary)
                && mouseX >= x && mouseX < x + width - 84
                && mouseY >= y + 13 && mouseY < y + 25) {
            pendingTooltip = Component.literal(summary);
        }
        ShopUiUtil.button(graphics, font, clickZones, mouseX, mouseY,
                x + width - 74, y + 9, 70, 18,
                Component.translatable(addToCart
                        ? "gui.futureshops.item_detail.add_cart"
                        : option.free()
                        ? "gui.futureshops.offer.get"
                        : "gui.futureshops.offer.select"),
                ShopUiUtil.ButtonStyle.PRIMARY, true, () -> {
                    if (minecraft != null) {
                        minecraft.setScreen(returnScreen);
                        if (addToCart && fixedCartQuantity > 0) {
                            ShopClientState.addOfferToCart(
                                    offer.listingId(),
                                    option.optionId(),
                                    fixedCartQuantity,
                                    offer.revision());
                        } else if (addToCart) {
                            parent.addAcquireOfferToCart(option);
                        } else {
                            parent.openAcquireOfferConfirm(option);
                        }
                    }
                });
    }

    private void renderSell(
            GuiGraphics graphics,
            ServerShopOfferListing offer,
            SellOfferOption option,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY
    ) {
        graphics.drawString(font,
                option.label().isBlank()
                        ? option.optionId() : option.label(),
                x, y, ShopColors.TEXT_STRONG, false);
        String summary = ServerShopOfferPresentation
                .sellSummary(option,
                        ShopClientState.getCurrencyName());
        String clippedSummary = font.plainSubstrByWidth(
                summary, width - 90);
        graphics.drawString(font,
                clippedSummary,
                x, y + 15, ShopColors.TEXT_MUTED, false);
        if (!clippedSummary.equals(summary)
                && mouseX >= x && mouseX < x + width - 84
                && mouseY >= y + 13 && mouseY < y + 25) {
            pendingTooltip = Component.literal(summary);
        }
        ShopUiUtil.button(graphics, font, clickZones, mouseX, mouseY,
                x + width - 74, y + 9, 70, 18,
                Component.translatable(
                        "gui.futureshops.offer.select"),
                ShopUiUtil.ButtonStyle.PRIMARY, true, () -> {
                    if (minecraft != null) {
                        minecraft.setScreen(parent);
                        parent.openSellOfferConfirm(option);
                    }
                });
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        scroll = Math.max(0, scroll + (delta < 0 ? 1 : -1));
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(returnScreen);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
