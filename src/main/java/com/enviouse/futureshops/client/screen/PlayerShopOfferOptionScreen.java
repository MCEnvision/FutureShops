package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.SellOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.PlayerShopNormalizedOfferData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PlayerShopOfferOptionScreen extends Screen
        implements ShopScreenMarker {
    private final PlayerShopBlockScreen parent;
    private final PlayerShopNormalizedOfferData normalized;
    private final OfferAction action;
    private final int quantity;
    private final List<ShopUiUtil.ClickZone> clickZones =
            new ArrayList<>();
    private int scroll;
    private Component pendingTooltip;

    public PlayerShopOfferOptionScreen(
            PlayerShopBlockScreen parent,
            PlayerShopNormalizedOfferData normalized,
            OfferAction action,
            int quantity
    ) {
        super(Component.translatable(
                "gui.futureshops.offer.choose_option"));
        this.parent = Objects.requireNonNull(parent, "parent");
        this.normalized = Objects.requireNonNull(normalized,
                "normalized");
        this.action = Objects.requireNonNull(action, "action");
        this.quantity = Math.max(1, quantity);
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
        int panelWidth = Math.min(460, width - 24);
        int panelHeight = Math.min(340, height - 24);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        graphics.fill(left, top, left + panelWidth,
                top + panelHeight, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, left, top,
                panelWidth, panelHeight, ShopColors.BORDER_STRONG,
                ShopColors.BORDER_SUBTLE);
        ShopUiUtil.renderAccentLine(graphics, left + 2, top,
                panelWidth - 4);
        graphics.drawString(font, title, left + 12, top + 10,
                ShopColors.TEXT_STRONG, false);
        ShopUiUtil.button(graphics, font, clickZones, mouseX, mouseY,
                left + panelWidth - 22, top + 6, 16, 16,
                Component.translatable(
                        "gui.futureshops.player_shop_block.close"),
                ShopUiUtil.ButtonStyle.GHOST,
                true, this::onClose);

        ServerShopOfferListing offer = normalized.offer().orElse(null);
        if (normalized.unavailable() || offer == null) {
            graphics.drawCenteredString(font,
                    Component.translatable(
                            "gui.futureshops.offer.unavailable"),
                    width / 2, top + 48, ShopColors.ERROR);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        int rowY = top + 34;
        int rowHeight = 60;
        int visible = Math.max(1,
                (panelHeight - 70) / rowHeight);
        int optionCount = action == OfferAction.ACQUIRE_FROM_SHOP
                ? offer.acquireOptions().size()
                : offer.sellOptions().size();
        scroll = Math.max(0, Math.min(scroll,
                Math.max(0, optionCount - visible)));
        for (int visibleIndex = 0;
             visibleIndex < visible; visibleIndex++) {
            int optionIndex = scroll + visibleIndex;
            if (optionIndex >= optionCount) {
                break;
            }
            int y = rowY + visibleIndex * rowHeight;
            ShopUiUtil.renderElevatedCard(graphics,
                    left + 10, y, panelWidth - 20, rowHeight - 6);
            if (action == OfferAction.ACQUIRE_FROM_SHOP) {
                renderAcquire(graphics, offer,
                        offer.acquireOptions().get(optionIndex),
                        left + 18, y + 7, panelWidth - 36,
                        mouseX, mouseY);
            } else {
                renderSell(graphics, offer,
                        offer.sellOptions().get(optionIndex),
                        left + 18, y + 7, panelWidth - 36,
                        mouseX, mouseY);
            }
            if (optionIndex + 1 < optionCount
                    && visibleIndex + 1 < visible) {
                graphics.drawCenteredString(font,
                        Component.translatable(
                                "gui.futureshops.offer.or"),
                        width / 2, y + rowHeight - 8,
                        ShopColors.TEXT_MUTED);
            }
        }
        ShopUiUtil.renderScrollIndicators(graphics, font,
                left + 10, rowY, panelWidth - 20,
                visible * rowHeight, scroll, visible, optionCount);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (pendingTooltip != null) {
            graphics.renderTooltip(font, pendingTooltip,
                    mouseX, mouseY);
        }
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
        graphics.drawString(font, optionName(option.optionId(),
                        option.label()), x, y,
                ShopColors.TEXT_STRONG, false);
        String summary;
        boolean arithmeticValid = true;
        try {
            summary = ServerShopOfferPresentation.acquireSummary(
                    option, offer,
                    ShopClientState.getCurrencyName(), quantity);
        } catch (ArithmeticException exception) {
            summary = I18n.get(
                    "gui.futureshops.offer.invalid_request");
            arithmeticValid = false;
        }
        renderSummary(graphics, summary, x, y, width,
                mouseX, mouseY);
        boolean termsAvailable = arithmeticValid
                && optionAvailable(offer, option.limits(),
                option.schedule());
        boolean componentsAvailable = hasComponents(
                option.itemCosts(), quantity);
        boolean complete = termsAvailable && componentsAvailable;
        ShopUiUtil.button(graphics, font, clickZones,
                mouseX, mouseY, x + width - 82, y + 12,
                78, 18, Component.translatable(option.free()
                        ? "gui.futureshops.offer.get"
                        : "gui.futureshops.offer.select"),
                ShopUiUtil.ButtonStyle.PRIMARY, complete,
                () -> {
                    if (minecraft != null) {
                        minecraft.setScreen(parent);
                        parent.openNormalizedAcquireConfirm(
                                normalized, offer, option, quantity);
                    }
                });
        if (!complete && mouseX >= x + width - 82
                && mouseX < x + width - 4
                && mouseY >= y + 12 && mouseY < y + 30) {
            pendingTooltip = Component.translatable(termsAvailable
                    ? "gui.futureshops.offer.missing_components"
                    : "gui.futureshops.offer.unavailable");
        }
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
        graphics.drawString(font, optionName(option.optionId(),
                        option.label()), x, y,
                ShopColors.TEXT_STRONG, false);
        String summary;
        boolean arithmeticValid = true;
        try {
            summary = ServerShopOfferPresentation.sellSummary(
                    option, ShopClientState.getCurrencyName(),
                    quantity);
        } catch (ArithmeticException exception) {
            summary = I18n.get(
                    "gui.futureshops.offer.invalid_request");
            arithmeticValid = false;
        }
        renderSummary(graphics, summary, x, y, width,
                mouseX, mouseY);
        boolean termsAvailable = arithmeticValid
                && optionAvailable(offer, option.limits(),
                option.schedule());
        boolean componentsAvailable = hasComponents(
                option.itemInputs(), quantity);
        boolean complete = termsAvailable && componentsAvailable;
        ShopUiUtil.button(graphics, font, clickZones,
                mouseX, mouseY, x + width - 82, y + 12,
                78, 18, Component.translatable(
                        "gui.futureshops.offer.select"),
                ShopUiUtil.ButtonStyle.PRIMARY, complete,
                () -> {
                    if (minecraft != null) {
                        minecraft.setScreen(parent);
                        parent.openNormalizedSellConfirm(
                                normalized, offer, option, quantity);
                    }
                });
        if (!complete && mouseX >= x + width - 82
                && mouseX < x + width - 4
                && mouseY >= y + 12 && mouseY < y + 30) {
            pendingTooltip = Component.translatable(termsAvailable
                    ? "gui.futureshops.offer.missing_components"
                    : "gui.futureshops.offer.unavailable");
        }
    }

    private void renderSummary(
            GuiGraphics graphics,
            String summary,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY
    ) {
        String clipped = font.plainSubstrByWidth(summary,
                width - 94);
        graphics.drawString(font, clipped, x, y + 17,
                ShopColors.TEXT_MUTED, false);
        if (!clipped.equals(summary)
                && mouseX >= x && mouseX < x + width - 88
                && mouseY >= y + 15 && mouseY < y + 28) {
            pendingTooltip = Component.literal(summary);
        }
    }

    private boolean hasComponents(
            List<OfferItemComponent> components,
            int multiplier
    ) {
        for (OfferItemComponent component : components) {
            int required;
            try {
                required = Math.multiplyExact(
                        component.count(), multiplier);
            } catch (ArithmeticException exception) {
                return false;
            }
            if (ShopUiUtil.countPlayerInventoryNbt(
                    component.itemId(), component.exactNbt(),
                    component.exactMatch()) < required) {
                return false;
            }
        }
        return true;
    }

    private boolean optionAvailable(
            ServerShopOfferListing offer,
            com.enviouse.futureshops.catalog.offer.OfferLimitPolicy
                    optionLimits,
            com.enviouse.futureshops.catalog.offer.OfferSchedule
                    optionSchedule
    ) {
        long now = java.time.Instant.now().getEpochSecond();
        return offer.active()
                && (offer.expiresAtEpoch() == 0L
                || now < offer.expiresAtEpoch())
                && offer.schedule().activeAt(now)
                && optionSchedule.activeAt(now)
                && quantity <= offer.limits().maximumPerRequest()
                && quantity <= optionLimits.maximumPerRequest();
    }

    private String optionName(String optionId, String label) {
        return label == null || label.isBlank()
                ? optionId : label;
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (ShopUiUtil.dispatchClicks(
                clickZones, mouseX, mouseY)) {
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
        scroll = Math.max(0,
                scroll + (delta < 0 ? 1 : -1));
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
