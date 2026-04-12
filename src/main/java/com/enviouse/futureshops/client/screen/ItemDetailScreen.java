package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.CatalogBarterIngredient;
import com.enviouse.futureshops.data.CatalogBarterRecipe;
import com.enviouse.futureshops.data.CatalogItem;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SBarterRequestPacket;
import com.enviouse.futureshops.network.packets.C2SBuyRequestPacket;
import com.enviouse.futureshops.network.packets.C2SInventorySyncPacket;
import com.enviouse.futureshops.network.packets.C2SSellRequestPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Item detail view scaffolded against spec §6.
 * Uses the authoritative CatalogItem from ShopClientState and sends buy requests to the server.
 */
public class ItemDetailScreen extends Screen implements ShopScreenMarker {
    private static final int GUI_W = 270;
    private static final int GUI_H = 200;

    private final Screen parent;
    private final String itemId;

    private int guiLeft;
    private int guiTop;
    private EditBox quantityBox;
    private Button buyButton;
    private Button sellButton;
    private Button barterButton;
    private Button addToCartButton;
    private int quantityRowY;
    private int actionRowY;

    public ItemDetailScreen(Screen parent, String itemId) {
        super(Component.translatable("gui.futureshops.detail.title"));
        this.parent = parent;
        this.itemId = itemId;
    }

    @Override
    protected void init() {
        guiLeft = (this.width - GUI_W) / 2;
        guiTop = (this.height - GUI_H) / 2;
        quantityRowY = guiTop + GUI_H - 56;
        actionRowY = guiTop + GUI_H - 34;
        ShopPackets.CHANNEL.sendToServer(new C2SInventorySyncPacket(ShopClientState.getActiveShopId()));

        addRenderableWidget(Button.builder(Component.literal("←"), button -> onClose())
                .bounds(guiLeft + 6, guiTop + 6, 18, 16)
                .build());

        quantityBox = new EditBox(this.font, guiLeft + 157, quantityRowY, 30, 14,
                Component.translatable("gui.futureshops.detail.quantity"));
        quantityBox.setValue("1");
        quantityBox.setMaxLength(2);
        quantityBox.setResponder(value -> {
            if (value.isBlank()) {
                return;
            }
            try {
                int quantity = Integer.parseInt(value);
                String clamped = Integer.toString(clampQuantity(quantity));
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
                .bounds(guiLeft + 140, quantityRowY, 14, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> setQuantity(getQuantity() + 1))
                .bounds(guiLeft + 190, quantityRowY, 14, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Max"), button -> setQuantity(resolveMaxQuantity()))
                .bounds(guiLeft + 208, quantityRowY, 28, 14)
                .build());

        addToCartButton = Button.builder(Component.translatable("gui.futureshops.detail.add_to_cart"), button -> {
                    CatalogItem item = currentItem();
                    if (item != null) {
                        ShopClientState.addToCart(item.itemId(), getQuantity());
                    }
                })
                .bounds(guiLeft + 108, actionRowY, 70, 16)
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
                .bounds(guiLeft + 182, actionRowY, 24, 16)
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
                .bounds(guiLeft + 210, actionRowY, 24, 16)
                .build();
        addRenderableWidget(sellButton);

        barterButton = Button.builder(Component.translatable("gui.futureshops.detail.barter"), button -> {
                    CatalogItem item = currentItem();
                    if (item != null) {
                        if (ShopClientState.getBarterRecipesForItem(item.itemId()).size() == 1) {
                            CatalogBarterRecipe recipe = ShopClientState.getBarterRecipesForItem(item.itemId()).get(0);
                            ShopPackets.CHANNEL.sendToServer(new C2SBarterRequestPacket(
                                    ShopClientState.getActiveShopId(),
                                    recipe.recipeId(),
                                    getQuantity()));
                        } else {
                            this.minecraft.setScreen(new BarterScreen(this, item.itemId()));
                        }
                    }
                })
                .bounds(guiLeft + 238, actionRowY, 24, 16)
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

        this.buyButton.active = item.buyPrice() > 0L && (item.unlimited() || item.stock() > 0);
        this.sellButton.active = item.sellPrice() > 0L && ShopUiUtil.countPlayerInventory(item.itemId()) > 0;
        this.barterButton.visible = item.hasBarterRecipes();
        this.barterButton.active = item.hasBarterRecipes();
        this.addToCartButton.active = this.buyButton.active;

        graphics.fill(0, 0, this.width, this.height, 0x88000000);
        graphics.fill(guiLeft, guiTop, guiLeft + GUI_W, guiTop + GUI_H, ShopColors.BG_PANEL);
        ShopUiUtil.drawBorder(graphics, guiLeft, guiTop, GUI_W, GUI_H, ShopColors.BORDER_DEFAULT);

        renderPreviewPanel(graphics, item);
        renderInfoPanel(graphics, item);
        ShopUiUtil.renderStatusPanel(graphics, this.font, guiLeft, Math.max(4, guiTop - 22), GUI_W);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPreviewPanel(GuiGraphics graphics, CatalogItem item) {
        int leftX = guiLeft + 10;
        int panelY = guiTop + 24;
        graphics.fill(leftX, panelY, leftX + 90, panelY + 150, ShopColors.BG_CARD);
        ShopUiUtil.drawBorder(graphics, leftX, panelY, 90, 150, ShopColors.BORDER_DEFAULT);

        graphics.pose().pushPose();
        graphics.pose().translate(leftX + 21f, panelY + 18f, 0f);
        graphics.pose().scale(3.0f, 3.0f, 1f);
        ShopUiUtil.renderItemIcon(graphics, this.font, item.itemId(), 0, 0);
        graphics.pose().popPose();

        graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(item.displayName(), 84), leftX + 45, panelY + 82,
                ShopColors.TEXT_PRIMARY);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.futureshops.detail.you_own", ShopUiUtil.countPlayerInventory(item.itemId())),
                leftX + 45, panelY + 98, ShopColors.TEXT_SECONDARY);
    }

    private void renderInfoPanel(GuiGraphics graphics, CatalogItem item) {
        int infoX = guiLeft + 108;
        int infoY = guiTop + 24;
        int infoW = 150;
        long effectiveBuyPrice = item.hasPromo() ? item.promoPrice() : item.buyPrice();
        int promoPercent = item.buyPrice() > 0L && item.hasPromo()
                ? (int) Math.round((1.0d - (double) item.promoPrice() / (double) item.buyPrice()) * 100.0d)
                : 0;

        graphics.pose().pushPose();
        graphics.pose().translate(infoX, infoY, 0);
        graphics.pose().scale(1.25f, 1.25f, 1f);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(item.displayName(), 110), 0, 0, ShopColors.TEXT_PRIMARY, true);
        graphics.pose().popPose();

        graphics.drawString(this.font, Component.translatable("gui.futureshops.detail.no_description"),
                infoX, infoY + 22, ShopColors.TEXT_SECONDARY, false);

        int nextY = infoY + 40;
        if (item.hasPromo()) {
            graphics.fill(infoX, nextY, infoX + infoW, nextY + 16, 0xFFD11A2A);
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.futureshops.detail.promo_active.percent", promoPercent),
                    infoX + infoW / 2, nextY + 4, ShopColors.PROMO_TEXT);
            nextY += 22;
        }

        graphics.fill(infoX, nextY, infoX + infoW, nextY + 1, ShopColors.BORDER_DEFAULT);
        nextY += 8;

        graphics.drawString(this.font, Component.translatable("gui.futureshops.detail.buy_price"), infoX, nextY, ShopColors.TEXT_SECONDARY, false);
        graphics.drawString(this.font, ShopUiUtil.formatMinorUnits(effectiveBuyPrice), infoX + 90, nextY, ShopColors.TEXT_PRICE, true);
        nextY += 14;

        graphics.drawString(this.font, Component.translatable("gui.futureshops.detail.sell_price"), infoX, nextY, ShopColors.TEXT_SECONDARY, false);
        graphics.drawString(this.font,
                item.sellPrice() > 0L ? ShopUiUtil.formatMinorUnits(item.sellPrice()) : "—",
                infoX + 90, nextY, item.sellPrice() > 0L ? ShopColors.TEXT_PRICE : ShopColors.TEXT_SECONDARY, false);
        nextY += 14;

        graphics.drawString(this.font,
                item.unlimited()
                        ? Component.translatable("gui.futureshops.detail.stock.unlimited")
                        : Component.translatable("gui.futureshops.detail.stock.remaining", item.stock()),
                infoX, nextY, ShopColors.TEXT_SECONDARY, false);
        nextY += 16;

        if (item.hasBarterRecipes()) {
            graphics.drawString(this.font, Component.translatable("gui.futureshops.detail.barter_available"), infoX, nextY,
                    ShopColors.TEXT_BARTER, false);
            nextY += 12;
            CatalogBarterRecipe previewRecipe = ShopClientState.getBarterRecipesForItem(item.itemId()).stream().findFirst().orElse(null);
            if (previewRecipe != null) {
                int shown = 0;
                for (CatalogBarterIngredient ingredient : previewRecipe.ingredients()) {
                    if (shown >= 3 || nextY > quantityRowY - 18) {
                        graphics.drawString(this.font, "...", infoX, nextY, ShopColors.TEXT_SECONDARY, false);
                        break;
                    }
                    int owned = ShopUiUtil.countPlayerInventory(ingredient.itemId());
                    int needed = ingredient.count() * getQuantity();
                    int color = owned >= needed ? ShopColors.SUCCESS : ShopColors.ERROR;
                    String label = ShopUiUtil.getItemDisplayName(ingredient.itemId()) + " ×" + needed;
                    graphics.drawString(this.font, this.font.plainSubstrByWidth(label, 110), infoX, nextY,
                            ShopColors.TEXT_BARTER, false);
                    graphics.drawString(this.font, "(have: " + owned + ")", infoX + 84, nextY, color, false);
                    nextY += 10;
                    shown++;
                }
            }
        }

        graphics.drawString(this.font, Component.translatable("gui.futureshops.detail.quantity"), infoX, quantityRowY - 4, ShopColors.TEXT_SECONDARY, false);
        graphics.drawString(this.font,
                Component.translatable("gui.futureshops.detail.total", ShopUiUtil.formatMinorUnits((long) effectiveBuyPrice * getQuantity())),
                infoX, actionRowY - 6, ShopColors.TEXT_PRICE, true);
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





