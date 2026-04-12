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
 * Item detail view scaffolded against spec §6.
 * Uses the authoritative CatalogItem from ShopClientState and sends buy requests to the server.
 */
public class ItemDetailScreen extends Screen implements ShopScreenMarker {
    private static final int DEFAULT_GUI_W = 324;
    private static final int DEFAULT_GUI_H = 236;
    private static final int PREVIEW_W = 124;

    private final Screen parent;
    private final String itemId;

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private int quantityRowY;
    private int previewPanelY;
    private int previewPanelH;

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
        previewPanelY = guiTop + 24;
        previewPanelH = guiH - 48;
        quantityRowY = guiTop + guiH - 58;

        ShopPackets.CHANNEL.sendToServer(new C2SInventorySyncPacket(ShopClientState.getActiveShopId()));

        addRenderableWidget(Button.builder(Component.literal("←"), button -> onClose())
                .bounds(guiLeft + 6, guiTop + 6, 18, 16)
                .build());

        quantityBox = new EditBox(this.font, guiLeft + 46, quantityRowY, 32, 14,
                Component.translatable("gui.futureshops.detail.quantity"));
        quantityBox.setValue("1");
        quantityBox.setMaxLength(2);
        quantityBox.setResponder(value -> {
            if (value.isBlank()) {
                return;
            }
            try {
                String clamped = Integer.toString(clampQuantity(Integer.parseInt(value)));
                if (!clamped.equals(value)) {
                    quantityBox.setValue(clamped);
                }
            } catch (NumberFormatException ignored) {
                if (!"1".equals(value)) {
                    quantityBox.setValue("1");
                }
            }
        });
        addRenderableWidget(quantityBox);

        addRenderableWidget(Button.builder(Component.literal("-"), button -> setQuantity(getQuantity() - 1))
                .bounds(guiLeft + 28, quantityRowY, 14, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> setQuantity(getQuantity() + 1))
                .bounds(guiLeft + 82, quantityRowY, 14, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Max"), button -> setQuantity(resolveMaxQuantity()))
                .bounds(guiLeft + 100, quantityRowY, 28, 14)
                .build());

        addToCartButton = Button.builder(Component.translatable("gui.futureshops.detail.add_to_cart"), button -> {
                    CatalogItem item = currentItem();
                    if (item != null) {
                        ShopClientState.addToCart(item.itemId(), getQuantity());
                    }
                })
                .bounds(guiLeft + 12, guiTop + guiH - 38, 108, 16)
                .build();
        addRenderableWidget(addToCartButton);

        buyButton = Button.builder(Component.translatable("gui.futureshops.detail.buy"), button -> {
                    CatalogItem item = currentItem();
                    if (item != null) {
                        ShopPackets.CHANNEL.sendToServer(C2SBuyRequestPacket.single(
                                ShopClientState.getActiveShopId(),
                                item.itemId(),
                                getQuantity()));
                    }
                })
                .bounds(guiLeft + 132, guiTop + guiH - 24, 54, 16)
                .build();
        addRenderableWidget(buyButton);

        sellButton = Button.builder(Component.translatable("gui.futureshops.detail.sell"), button -> {
                    CatalogItem item = currentItem();
                    if (item != null) {
                        ShopPackets.CHANNEL.sendToServer(new C2SSellRequestPacket(
                                ShopClientState.getActiveShopId(),
                                item.itemId(),
                                getQuantity()));
                    }
                })
                .bounds(guiLeft + 190, guiTop + guiH - 24, 54, 16)
                .build();
        addRenderableWidget(sellButton);

        barterButton = Button.builder(Component.translatable("gui.futureshops.detail.barter"), button -> {
                    CatalogItem item = currentItem();
                    if (item != null) {
                        this.minecraft.setScreen(new BarterScreen(this, item.itemId()));
                    }
                })
                .bounds(guiLeft + 248, guiTop + guiH - 24, 60, 16)
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

        graphics.fill(0, 0, this.width, this.height, 0x88000000);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.BG_PANEL);
        ShopUiUtil.drawBorder(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_DEFAULT);

        renderPreviewPanel(graphics, item);
        renderInfoPanel(graphics, item);
        ShopUiUtil.renderStatusPanel(graphics, this.font, guiLeft, Math.max(4, guiTop - 22), guiW);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPreviewPanel(GuiGraphics graphics, CatalogItem item) {
        int leftX = guiLeft + 10;
        graphics.fill(leftX, previewPanelY, leftX + PREVIEW_W, previewPanelY + previewPanelH, ShopColors.BG_CARD);
        ShopUiUtil.drawBorder(graphics, leftX, previewPanelY, PREVIEW_W, previewPanelH, ShopColors.BORDER_DEFAULT);

        ShopUiUtil.renderLargeItemPreview(graphics, this.font, item.itemId(), leftX, previewPanelY + 6, PREVIEW_W);

        graphics.drawCenteredString(this.font,
                this.font.plainSubstrByWidth(item.displayName(), PREVIEW_W - 10),
                leftX + PREVIEW_W / 2,
                previewPanelY + 92,
                ShopColors.TEXT_PRIMARY);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.futureshops.detail.you_own", ShopUiUtil.countPlayerInventory(item.itemId())),
                leftX + PREVIEW_W / 2,
                previewPanelY + 108,
                ShopColors.TEXT_SECONDARY);

        graphics.drawCenteredString(this.font,
                Component.translatable("gui.futureshops.detail.quantity"),
                leftX + PREVIEW_W / 2,
                quantityRowY - 10,
                ShopColors.TEXT_SECONDARY);
    }

    private void renderInfoPanel(GuiGraphics graphics, CatalogItem item) {
        int infoX = guiLeft + PREVIEW_W + 20;
        int infoY = guiTop + 24;
        int infoW = guiW - PREVIEW_W - 30;
        long effectiveBuyPrice = item.hasPromo() ? item.promoPrice() : item.buyPrice();
        int promoPercent = ShopUiUtil.computePromoPercent(item.buyPrice(), item.promoPrice());

        graphics.pose().pushPose();
        graphics.pose().translate(infoX, infoY, 0);
        graphics.pose().scale(1.2f, 1.2f, 1f);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(item.displayName(), (int) ((infoW - 8) / 1.2f)), 0, 0, ShopColors.TEXT_PRIMARY, true);
        graphics.pose().popPose();

        graphics.drawString(this.font, Component.translatable("gui.futureshops.detail.no_description"),
                infoX, infoY + 20, ShopColors.TEXT_SECONDARY, false);

        int nextY = infoY + 36;
        if (item.hasPromo() && promoPercent > 0) {
            graphics.fill(infoX, nextY, infoX + infoW, nextY + 16, 0xFFD11A2A);
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.futureshops.detail.promo_active.percent", promoPercent),
                    infoX + infoW / 2,
                    nextY + 4,
                    ShopColors.PROMO_TEXT);
            nextY += 22;
        }

        graphics.fill(infoX, nextY, infoX + infoW, nextY + 1, ShopColors.BORDER_DEFAULT);
        nextY += 8;

        drawInfoLine(graphics, Component.translatable("gui.futureshops.detail.buy_price").getString(),
                ShopUiUtil.formatMinorUnits(effectiveBuyPrice), infoX, infoW, nextY, ShopColors.TEXT_PRICE);
        nextY += 14;
        drawInfoLine(graphics, Component.translatable("gui.futureshops.detail.sell_price").getString(),
                item.sellPrice() > 0L ? ShopUiUtil.formatMinorUnits(item.sellPrice()) : "—",
                infoX, infoW, nextY,
                item.sellPrice() > 0L ? ShopColors.TEXT_PRICE : ShopColors.TEXT_SECONDARY);
        nextY += 14;

        graphics.drawString(this.font,
                item.unlimited()
                        ? Component.translatable("gui.futureshops.detail.stock.unlimited")
                        : Component.translatable("gui.futureshops.detail.stock.remaining", item.stock()),
                infoX,
                nextY,
                ShopColors.TEXT_SECONDARY,
                false);
        nextY += 18;

        if (item.hasBarterRecipes()) {
            graphics.drawString(this.font, Component.translatable("gui.futureshops.detail.barter_available"), infoX, nextY,
                    ShopColors.TEXT_BARTER, false);
            nextY += 12;
            renderBarterPreview(graphics, item, infoX, infoW, nextY, quantityRowY - 22);
        }

        graphics.drawString(this.font,
                Component.translatable("gui.futureshops.detail.total", ShopUiUtil.formatMinorUnits(effectiveBuyPrice * getQuantity())),
                infoX,
                guiTop + guiH - 44,
                ShopColors.TEXT_PRICE,
                true);
    }

    private void renderBarterPreview(GuiGraphics graphics, CatalogItem item, int infoX, int infoW, int startY, int maxY) {
        CatalogBarterRecipe previewRecipe = ShopClientState.getBarterRecipesForItem(item.itemId()).stream().findFirst().orElse(null);
        if (previewRecipe == null) {
            return;
        }

        int nextY = startY;
        List<CatalogBarterIngredient> ingredients = previewRecipe.ingredients();
        int shown = 0;
        for (CatalogBarterIngredient ingredient : ingredients) {
            if (shown >= 4 || nextY > maxY) {
                graphics.drawString(this.font, "...", infoX, nextY, ShopColors.TEXT_SECONDARY, false);
                break;
            }
            int owned = ShopUiUtil.countPlayerInventory(ingredient.itemId());
            int needed = ingredient.count() * getQuantity();
            int color = owned >= needed ? ShopColors.SUCCESS : ShopColors.ERROR;
            String label = ShopUiUtil.getItemDisplayName(ingredient.itemId()) + " ×" + needed;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(label, infoW - 54), infoX, nextY, ShopColors.TEXT_BARTER, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth("have " + owned, 48), infoX + infoW - 48, nextY, color, false);
            nextY += 10;
            shown++;
        }
    }

    private void drawInfoLine(GuiGraphics graphics, String label, String value, int infoX, int infoW, int y, int valueColor) {
        graphics.drawString(this.font, label, infoX, y, ShopColors.TEXT_SECONDARY, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(value, 74), infoX + Math.max(70, infoW - 74), y, valueColor, false);
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
        if (item == null) {
            return 1;
        }
        int limit = item.unlimited() ? 64 : Math.max(1, item.stock());
        return Math.min(64, limit);
    }

    private int clampQuantity(int quantity) {
        return Math.max(1, Math.min(resolveMaxQuantity(), quantity));
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
