package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.CatalogBarterIngredient;
import com.enviouse.futureshops.data.CatalogBarterRecipe;
import com.enviouse.futureshops.data.CatalogItem;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SBuyRequestPacket;
import com.enviouse.futureshops.network.packets.C2SInventorySyncPacket;
import com.enviouse.futureshops.network.packets.C2SSellRequestPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Item detail view — redesigned with bottom-aligned controls and modern layout.
 */
public class ItemDetailScreen extends Screen implements ShopScreenMarker {
    private static final int DEFAULT_GUI_W = 340;
    private static final int DEFAULT_GUI_H = 260;
    private static final int PREVIEW_W = 130;

    private final Screen parent;
    private final String itemId;

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;

    private EditBox quantityBox;
    private Button buyButton;
    private Button sellButton;
    private Button barterButton;
    private Button addToCartButton;

    public ItemDetailScreen(Screen parent, String itemId) {
        super(Component.translatable("gui.futureshops.detail.title"));
        this.parent = parent;
        this.itemId = itemId;
    }

    @Override
    protected void init() {
        guiW = Math.min(DEFAULT_GUI_W, this.width - 16);
        guiH = Math.min(DEFAULT_GUI_H, this.height - 16);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;

        ShopPackets.CHANNEL.sendToServer(new C2SInventorySyncPacket(ShopClientState.getActiveShopId()));

        // Back button
        addRenderableWidget(Button.builder(Component.literal("§7← Back"), button -> onClose())
                .bounds(guiLeft + 6, guiTop + 6, 44, 14)
                .build());

        // ═══ Quantity controls — inside the preview panel, centered above "Quantity" label ═══
        int previewCenterX = guiLeft + 8 + PREVIEW_W / 2;
        // Preview panel bottom = guiTop + guiH - 32. Stack: controls → Total → "Quantity"
        int qtyY = guiTop + guiH - 32 - 42; // controls Y

        addRenderableWidget(Button.builder(Component.literal("-"), button -> setQuantity(getQuantity() - 1))
                .bounds(previewCenterX - 42, qtyY, 14, 14)
                .build());
        quantityBox = new EditBox(this.font, previewCenterX - 24, qtyY, 24, 14,
                Component.translatable("gui.futureshops.detail.quantity"));
        quantityBox.setValue("1");
        quantityBox.setMaxLength(2);
        quantityBox.setResponder(value -> {
            if (value.isBlank()) return;
            try {
                String clamped = Integer.toString(clampQuantity(Integer.parseInt(value)));
                if (!clamped.equals(value)) quantityBox.setValue(clamped);
            } catch (NumberFormatException ignored) {
                if (!"1".equals(value)) quantityBox.setValue("1");
            }
        });
        addRenderableWidget(quantityBox);
        addRenderableWidget(Button.builder(Component.literal("+"), button -> setQuantity(getQuantity() + 1))
                .bounds(previewCenterX + 4, qtyY, 14, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Max"), button -> setQuantity(resolveMaxQuantity()))
                .bounds(previewCenterX + 22, qtyY, 26, 14)
                .build());

        // ═══ Action buttons row — centered at the very bottom ═══
        int bottomY = guiTop + guiH - 22;
        int btnW = 54;
        int gap = 3;
        int totalBtnW = btnW * 3 + gap * 2;
        int startX = guiLeft + (guiW - totalBtnW) / 2;

        addToCartButton = Button.builder(Component.literal("§e+ Cart"), button -> {
                    CatalogItem item = currentItem();
                    if (item != null) ShopClientState.addToCart(item.itemId(), getQuantity());
                })
                .bounds(startX, bottomY, btnW, 14)
                .build();
        addRenderableWidget(addToCartButton);

        buyButton = Button.builder(Component.literal("§a$ Buy"), button -> {
                    CatalogItem item = currentItem();
                    if (item != null) {
                        ShopPackets.CHANNEL.sendToServer(C2SBuyRequestPacket.single(
                                ShopClientState.getActiveShopId(), item.itemId(), getQuantity()));
                    }
                })
                .bounds(startX + btnW + gap, bottomY, btnW, 14)
                .build();
        addRenderableWidget(buyButton);

        sellButton = Button.builder(Component.literal("§6↑ Sell"), button -> {
                    CatalogItem item = currentItem();
                    if (item != null) {
                        ShopPackets.CHANNEL.sendToServer(new C2SSellRequestPacket(
                                ShopClientState.getActiveShopId(), item.itemId(), getQuantity()));
                    }
                })
                .bounds(startX + (btnW + gap) * 2, bottomY, btnW, 14)
                .build();
        addRenderableWidget(sellButton);

        barterButton = Button.builder(Component.literal("§d⚒ Barter"), button -> {
                    CatalogItem item = currentItem();
                    if (item != null) this.minecraft.setScreen(new BarterScreen(this, item.itemId()));
                })
                .bounds(guiLeft + guiW - 68, guiTop + 6, 60, 14)
                .build();
        addRenderableWidget(barterButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        CatalogItem item = currentItem();
        if (item == null) {
            onClose();
            return;
        }

        buyButton.active = item.buyPrice() > 0L && (item.unlimited() || item.stock() > 0);
        sellButton.active = item.sellPrice() > 0L && ShopUiUtil.countPlayerInventory(item.itemId()) > 0;
        barterButton.visible = item.hasBarterRecipes();
        barterButton.active = item.hasBarterRecipes();
        addToCartButton.active = buyButton.active;

        // Dimmed background
        graphics.fill(0, 0, this.width, this.height, 0x88000000);
        // Main panel with accent
        ShopUiUtil.renderAccentPanel(graphics, guiLeft, guiTop, guiW, guiH,
                ShopColors.BG_PANEL, ShopColors.BORDER_DEFAULT, ShopColors.ACCENT_CYAN);

        renderPreviewPanel(graphics, item);
        renderInfoPanel(graphics, item);
        renderBottomArea(graphics, item);
        ShopUiUtil.renderStatusPanel(graphics, this.font, guiLeft, Math.max(4, guiTop - 22), guiW);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPreviewPanel(GuiGraphics graphics, CatalogItem item) {
        int leftX = guiLeft + 8;
        int panelY = guiTop + 24;
        int panelH = guiH - 56;

        ShopUiUtil.renderAccentPanel(graphics, leftX, panelY, PREVIEW_W, panelH,
                ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT, ShopColors.ACCENT_PURPLE);

        ShopUiUtil.renderLargeItemPreview(graphics, this.font, item.itemId(), leftX, panelY + 8, PREVIEW_W);

        // Name — wrapped
        String name = this.font.plainSubstrByWidth(item.displayName(), PREVIEW_W - 10);
        graphics.drawCenteredString(this.font, name, leftX + PREVIEW_W / 2, panelY + 82, ShopColors.TEXT_PRIMARY);

        // Owned count
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.futureshops.detail.you_own", ShopUiUtil.countPlayerInventory(item.itemId())),
                leftX + PREVIEW_W / 2, panelY + 96, ShopColors.TEXT_SECONDARY);

        // Total cost — right below the qty controls (controls are at panelBottom - 42)
        long effectiveBuyPrice = item.hasPromo() ? item.promoPrice() : item.buyPrice();
        String total = "§6Total: §a" + ShopUiUtil.formatMinorUnits(effectiveBuyPrice * getQuantity());
        graphics.drawCenteredString(this.font, total, leftX + PREVIEW_W / 2, panelY + panelH - 26, ShopColors.TEXT_PRICE);

        // Quantity label — below Total
        graphics.drawCenteredString(this.font, "§7Quantity", leftX + PREVIEW_W / 2, panelY + panelH - 14, ShopColors.TEXT_SECONDARY);
    }

    private void renderInfoPanel(GuiGraphics graphics, CatalogItem item) {
        int infoX = guiLeft + PREVIEW_W + 18;
        int infoY = guiTop + 24;
        int infoW = guiW - PREVIEW_W - 26;
        int infoH = guiH - 56;
        long effectiveBuyPrice = item.hasPromo() ? item.promoPrice() : item.buyPrice();
        int promoPercent = ShopUiUtil.computePromoPercent(item.buyPrice(), item.promoPrice());

        ShopUiUtil.renderPanel(graphics, infoX, infoY, infoW, infoH, ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT);

        int contentX = infoX + 8;
        int contentW = infoW - 16;

        // Title — scaled, truncated
        graphics.pose().pushPose();
        graphics.pose().translate(contentX, infoY + 8, 0);
        graphics.pose().scale(1.2f, 1.2f, 1f);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(item.displayName(), (int) (contentW / 1.2f)), 0, 0, ShopColors.TEXT_PRIMARY, true);
        graphics.pose().popPose();

        // Description placeholder — wrapped
        ShopUiUtil.drawWrappedString(graphics, this.font,
                Component.translatable("gui.futureshops.detail.no_description"),
                contentX, infoY + 24, contentW, ShopColors.TEXT_SECONDARY, 10);

        int nextY = infoY + 38;

        // Animated promo banner
        if (item.hasPromo() && promoPercent > 0) {
            ShopUiUtil.renderAnimatedDiscountBadge(graphics, this.font,
                    infoX + infoW / 2, nextY + 6, "-" + promoPercent + "% OFF!");
            nextY += 20;
        }

        // Divider
        graphics.fill(contentX, nextY, contentX + contentW, nextY + 1, ShopColors.BORDER_DEFAULT);
        nextY += 6;

        // Buy price
        drawInfoLine(graphics, "Buy:", ShopUiUtil.formatMinorUnits(effectiveBuyPrice), contentX, contentW, nextY, ShopColors.TEXT_PRICE);
        nextY += 12;

        // Sell price
        String sellStr = item.sellPrice() > 0L ? ShopUiUtil.formatMinorUnits(item.sellPrice()) : "—";
        drawInfoLine(graphics, "Sell:", sellStr, contentX, contentW, nextY,
                item.sellPrice() > 0L ? ShopColors.TEXT_PRICE : ShopColors.TEXT_SECONDARY);
        nextY += 12;

        // Stock
        String stockLabel = item.unlimited() ? "§a∞ Unlimited" : (item.stock() > 0 ? "§a" + item.stock() + " in stock" : "§cOut of stock");
        graphics.drawString(this.font, this.font.plainSubstrByWidth(stockLabel, contentW), contentX, nextY, ShopColors.TEXT_SECONDARY, false);
        nextY += 14;

        // Barter preview
        if (item.hasBarterRecipes()) {
            graphics.drawString(this.font, "§d⚒ Barter available", contentX, nextY, ShopColors.TEXT_BARTER, false);
            nextY += 10;
            renderBarterPreview(graphics, item, contentX, contentW, nextY, infoY + infoH - 20);
        }
    }

    private void renderBottomArea(GuiGraphics graphics, CatalogItem item) {
        // Total is now rendered inside renderPreviewPanel — nothing else needed here
    }

    private void renderBarterPreview(GuiGraphics graphics, CatalogItem item, int contentX, int contentW, int startY, int maxY) {
        CatalogBarterRecipe previewRecipe = ShopClientState.getBarterRecipesForItem(item.itemId()).stream().findFirst().orElse(null);
        if (previewRecipe == null) return;

        int nextY = startY;
        List<CatalogBarterIngredient> ingredients = previewRecipe.ingredients();
        int shown = 0;
        for (CatalogBarterIngredient ingredient : ingredients) {
            if (shown >= 3 || nextY > maxY) {
                graphics.drawString(this.font, "§7...", contentX, nextY, ShopColors.TEXT_SECONDARY, false);
                break;
            }
            int owned = ShopUiUtil.countPlayerInventory(ingredient.itemId());
            int needed = ingredient.count() * getQuantity();
            int color = owned >= needed ? ShopColors.SUCCESS : ShopColors.ERROR;
            String label = this.font.plainSubstrByWidth(
                    ShopUiUtil.getItemDisplayName(ingredient.itemId()) + " ×" + needed,
                    contentW - 50);
            graphics.drawString(this.font, label, contentX, nextY, ShopColors.TEXT_BARTER, false);
            String haveStr = this.font.plainSubstrByWidth("have " + owned, 44);
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

    private CatalogItem currentItem() {
        return ShopClientState.getCatalogItem(itemId).orElse(null);
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
        int limit = item.unlimited() ? 64 : Math.max(1, item.stock());
        return Math.min(64, limit);
    }

    private int clampQuantity(int quantity) {
        return Math.max(1, Math.min(resolveMaxQuantity(), quantity));
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
