package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.CatalogBarterIngredient;
import com.enviouse.futureshops.data.CatalogBarterRecipe;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SBarterRequestPacket;
import com.enviouse.futureshops.network.packets.C2SInventorySyncPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Focused barter screen for recipes targeting one shop item. */
public class BarterScreen extends Screen implements ShopScreenMarker {
    private static final int DEFAULT_GUI_W = 300;
    private static final int DEFAULT_GUI_H = 220;

    private final Screen parent;
    private final String targetItemId;

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private int selectedIndex;
    private int multiplier = 1;
    private Button confirmButton;

    public BarterScreen(Screen parent, String targetItemId) {
        super(Component.translatable("gui.futureshops.barter.title"));
        this.parent = parent;
        this.targetItemId = targetItemId;
    }

    @Override
    protected void init() {
        guiW = Math.min(DEFAULT_GUI_W, this.width - 16);
        guiH = Math.min(DEFAULT_GUI_H, this.height - 16);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;
        ShopPackets.CHANNEL.sendToServer(new C2SInventorySyncPacket(ShopClientState.getActiveShopId()));

        addRenderableWidget(Button.builder(Component.literal("←"), button -> onClose())
                .bounds(guiLeft + 6, guiTop + 6, 18, 16)
                .build());
        addRenderableWidget(Button.builder(Component.literal("-"), button -> multiplier = Math.max(1, multiplier - 1))
                .bounds(guiLeft + guiW - 82, guiTop + guiH - 52, 14, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> multiplier = Math.min(64, multiplier + 1))
                .bounds(guiLeft + guiW - 30, guiTop + guiH - 52, 14, 14)
                .build());

        confirmButton = Button.builder(Component.translatable("gui.futureshops.barter.confirm"), button -> sendConfirm())
                .bounds(guiLeft + guiW - 110, guiTop + guiH - 30, 104, 18)
                .build();
        addRenderableWidget(confirmButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        List<CatalogBarterRecipe> recipes = recipes();
        selectedIndex = Math.min(selectedIndex, Math.max(0, recipes.size() - 1));

        graphics.fill(0, 0, this.width, this.height, 0x88000000);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.BG_PANEL);
        ShopUiUtil.drawBorder(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BTN_BARTER);

        graphics.drawCenteredString(this.font, this.title, guiLeft + guiW / 2, guiTop + 10, ShopColors.TEXT_PRIMARY);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.barter.subtitle"), guiLeft + 28, guiTop + 10,
                ShopColors.TEXT_BARTER, false);

        renderRecipeList(graphics, recipes);
        renderRecipeDetail(graphics, recipes.isEmpty() ? null : recipes.get(selectedIndex));
        ShopUiUtil.renderStatusPanel(graphics, this.font, guiLeft, Math.max(4, guiTop - 22), guiW);

        confirmButton.active = !recipes.isEmpty() && canAfford(recipes.get(selectedIndex), multiplier);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRecipeList(GuiGraphics graphics, List<CatalogBarterRecipe> recipes) {
        int listX = guiLeft + 8;
        int listY = guiTop + 28;
        int listW = Math.max(130, guiW - 126);
        int rowH = 30;

        if (recipes.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.barter.none"),
                    listX + listW / 2, listY + 50, ShopColors.TEXT_SECONDARY);
            return;
        }

        for (int i = 0; i < Math.min(recipes.size(), 5); i++) {
            CatalogBarterRecipe recipe = recipes.get(i);
            int y = listY + i * rowH;
            int bg = i == selectedIndex ? ShopColors.BG_CARD_HOVER : ShopColors.BG_CARD;
            graphics.fill(listX, y, listX + listW, y + rowH - 2, bg);
            ShopUiUtil.drawBorder(graphics, listX, y, listW, rowH - 2,
                    i == selectedIndex ? ShopColors.BORDER_ACCENT : ShopColors.BORDER_DEFAULT);

            graphics.drawString(this.font, ShopUiUtil.getItemDisplayName(targetItemId), listX + 6, y + 5,
                    ShopColors.TEXT_PRIMARY, false);
            graphics.drawString(this.font,
                    Component.translatable("gui.futureshops.barter.recipe_label", i + 1, recipe.outputCount()),
                    listX + 6, y + 17, ShopColors.TEXT_BARTER, false);
        }
    }

    private void renderRecipeDetail(GuiGraphics graphics, CatalogBarterRecipe recipe) {
        int detailX = guiLeft + 186;
        int detailY = guiTop + 28;
        int detailW = guiW - (detailX - guiLeft) - 8;
        int detailH = guiH - 66;
        graphics.fill(detailX, detailY, detailX + detailW, detailY + detailH, ShopColors.BG_CARD);
        ShopUiUtil.drawBorder(graphics, detailX, detailY, detailW, detailH, ShopColors.BORDER_DEFAULT);

        if (recipe == null) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.barter.none"),
                    detailX + detailW / 2, detailY + 60, ShopColors.TEXT_SECONDARY);
            return;
        }

        graphics.drawString(this.font, Component.translatable("gui.futureshops.barter.you_give"), detailX + 6, detailY + 8,
                ShopColors.TEXT_BARTER, true);

        int y = detailY + 24;
        for (CatalogBarterIngredient ingredient : recipe.ingredients()) {
            String label = ShopUiUtil.getItemDisplayName(ingredient.itemId()) + " ×" + (ingredient.count() * multiplier);
            int owned = ShopUiUtil.countPlayerInventory(ingredient.itemId());
            int needed = ingredient.count() * multiplier;
            int color = owned >= needed ? ShopColors.SUCCESS : ShopColors.ERROR;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(label, detailW - 12), detailX + 6, y,
                    ShopColors.TEXT_PRIMARY, false);
            graphics.drawString(this.font, "(have: " + owned + ")", detailX + 6, y + 10, color, false);
            y += 24;
        }

        graphics.fill(detailX + 6, detailY + 108, detailX + detailW - 6, detailY + 109, ShopColors.BORDER_DEFAULT);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.barter.you_receive"), detailX + 6, detailY + 116,
                ShopColors.TEXT_PRIMARY, true);
        graphics.drawString(this.font,
                ShopUiUtil.getItemDisplayName(targetItemId) + " ×" + (recipe.outputCount() * multiplier),
                detailX + 6, detailY + 130, ShopColors.SUCCESS, false);

        graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.barter.multiplier", multiplier),
                detailX + detailW / 2, guiTop + guiH - 49, ShopColors.TEXT_SECONDARY);
    }

    private List<CatalogBarterRecipe> recipes() {
        return ShopClientState.getBarterRecipesForItem(targetItemId);
    }

    private boolean canAfford(CatalogBarterRecipe recipe, int tradeMultiplier) {
        return recipe.ingredients().stream()
                .allMatch(ingredient -> ShopUiUtil.countPlayerInventory(ingredient.itemId()) >= ingredient.count() * tradeMultiplier);
    }

    private void sendConfirm() {
        List<CatalogBarterRecipe> recipes = recipes();
        if (recipes.isEmpty()) {
            return;
        }
        CatalogBarterRecipe recipe = recipes.get(selectedIndex);
        ShopPackets.CHANNEL.sendToServer(new C2SBarterRequestPacket(
                ShopClientState.getActiveShopId(),
                recipe.recipeId(),
                multiplier));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listX = guiLeft + 8;
        int listY = guiTop + 28;
        int listW = Math.max(130, guiW - 126);
        int rowH = 30;
        List<CatalogBarterRecipe> recipes = recipes();
        for (int i = 0; i < Math.min(recipes.size(), 5); i++) {
            int y = listY + i * rowH;
            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= y && mouseY <= y + rowH - 2) {
                selectedIndex = i;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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



