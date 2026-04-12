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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Storefront barter screen — half-and-half layout:
 * left = what you receive, right = what you give, arrow in between,
 * bottom row: qty controls LEFT | receive summary CENTER | confirm RIGHT.
 */
public class BarterScreen extends Screen implements ShopScreenMarker {
    private final Screen parent;
    private final String targetItemId;

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private int selectedIndex;
    private int multiplier = 1;
    private Button confirmButton;
    private EditBox qtyBox;

    public BarterScreen(Screen parent, String targetItemId) {
        super(Component.translatable("gui.futureshops.barter.title"));
        this.parent = parent;
        this.targetItemId = targetItemId;
    }

    @Override
    protected void init() {
        guiW = Math.min(460, Math.max(340, this.width - 24));
        guiH = Math.min(280, Math.max(220, this.height - 24));
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;
        ShopPackets.CHANNEL.sendToServer(new C2SInventorySyncPacket(ShopClientState.getActiveShopId()));

        // Back button top-left
        addRenderableWidget(Button.builder(Component.literal("§7← Back"), button -> onClose())
                .bounds(guiLeft + 6, guiTop + 6, 44, 14)
                .build());

        // ═══ Bottom row: Qty LEFT | Confirm RIGHT ═══
        int bottomY = guiTop + guiH - 24;

        // Qty controls — left side
        int qtyX = guiLeft + 10;
        addRenderableWidget(Button.builder(Component.literal("-"), button -> setMultiplier(multiplier - 1))
                .bounds(qtyX, bottomY, 16, 16)
                .build());
        qtyBox = new EditBox(this.font, qtyX + 18, bottomY, 32, 16, Component.literal("Qty"));
        qtyBox.setValue("1");
        qtyBox.setMaxLength(4);
        qtyBox.setResponder(value -> {
            if (value.isBlank()) return;
            try {
                int parsed = Integer.parseInt(value);
                int max = resolveMaxMultiplier();
                int clamped = Math.max(1, Math.min(max, parsed));
                if (clamped != parsed) qtyBox.setValue(Integer.toString(clamped));
                else multiplier = clamped;
            } catch (NumberFormatException ignored) {
                qtyBox.setValue("1");
            }
        });
        addRenderableWidget(qtyBox);
        addRenderableWidget(Button.builder(Component.literal("+"), button -> setMultiplier(multiplier + 1))
                .bounds(qtyX + 52, bottomY, 16, 16)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Max"), button -> setMultiplier(resolveMaxMultiplier()))
                .bounds(qtyX + 70, bottomY, 28, 16)
                .build());

        // Confirm button — right side
        confirmButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.barter.confirm"), button -> sendConfirm())
                .bounds(guiLeft + guiW - 90, bottomY, 80, 16)
                .build());
    }

    private void setMultiplier(int value) {
        int max = resolveMaxMultiplier();
        multiplier = Math.max(1, Math.min(max, value));
        if (qtyBox != null) qtyBox.setValue(Integer.toString(multiplier));
    }

    /**
     * Smart max: find the limiting ingredient based on player inventory, capped at stock.
     * No hard 64 cap — excess drops on floor.
     */
    private int resolveMaxMultiplier() {
        List<CatalogBarterRecipe> recipes = recipes();
        if (recipes.isEmpty() || selectedIndex >= recipes.size()) return 1;
        CatalogBarterRecipe recipe = recipes.get(selectedIndex);
        int maxByIngredients = Integer.MAX_VALUE;
        for (CatalogBarterIngredient ingredient : recipe.ingredients()) {
            int owned = ShopUiUtil.countPlayerInventory(ingredient.itemId());
            int costPer = ingredient.count();
            if (costPer > 0) {
                maxByIngredients = Math.min(maxByIngredients, owned / costPer);
            }
        }
        // Also limit to item stock if applicable
        ShopClientState.getCatalogItem(targetItemId).ifPresent(item -> {
            // Not needed — we don't cap by stock for catalog items since they may be unlimited
        });
        return Math.max(1, maxByIngredients == Integer.MAX_VALUE ? 1 : maxByIngredients);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        List<CatalogBarterRecipe> recipes = recipes();
        selectedIndex = Math.min(selectedIndex, Math.max(0, recipes.size() - 1));

        graphics.fill(0, 0, this.width, this.height, 0x88000000);
        ShopUiUtil.renderAccentPanel(graphics, guiLeft, guiTop, guiW, guiH,
                ShopColors.BG_PANEL, ShopColors.BORDER_DEFAULT, ShopColors.ACCENT_PURPLE);

        graphics.drawCenteredString(this.font, this.title, guiLeft + guiW / 2, guiTop + 10, ShopColors.TEXT_PRIMARY);

        if (recipes.isEmpty()) {
            graphics.drawCenteredString(this.font, "No barter recipes available.",
                    guiLeft + guiW / 2, guiTop + guiH / 2, ShopColors.TEXT_SECONDARY);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        // Recipe selector tabs (if multiple)
        if (recipes.size() > 1) {
            renderRecipeTabs(graphics, recipes, mouseX, mouseY);
        }

        CatalogBarterRecipe recipe = recipes.get(selectedIndex);
        int contentY = guiTop + (recipes.size() > 1 ? 40 : 26);
        int contentH = guiH - (recipes.size() > 1 ? 72 : 58);
        int halfW = (guiW - 40) / 2;

        // ═══ Left panel: You Receive ═══
        renderReceivePanel(graphics, guiLeft + 10, contentY, halfW, contentH, recipe);

        // ═══ Arrow in between ═══
        int arrowX = guiLeft + guiW / 2;
        int arrowY = contentY + contentH / 2;
        graphics.drawCenteredString(this.font, "§6⟵", arrowX, arrowY - 6, ShopColors.ACCENT_GOLD);
        graphics.drawCenteredString(this.font, "§d⟶", arrowX, arrowY + 6, ShopColors.TEXT_BARTER);

        // ═══ Right panel: You Give ═══
        renderGivePanel(graphics, guiLeft + guiW - halfW - 10, contentY, halfW, contentH, recipe);

        // ═══ Receive summary at same height as bottom controls ═══
        int bottomY = guiTop + guiH - 24;
        int totalOutput = recipe.outputCount() * multiplier;
        String totalText = "Receive " + totalOutput + "x "
                + this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayName(targetItemId), 100);
        // Center between qty controls and confirm button
        int textX = guiLeft + 110;
        int textW = guiLeft + guiW - 94 - textX;
        graphics.drawCenteredString(this.font, totalText, textX + textW / 2, bottomY + 4, ShopColors.TEXT_SECONDARY);

        confirmButton.active = canAfford(recipe, multiplier);
        ShopUiUtil.renderStatusPanel(graphics, this.font, guiLeft + 10, Math.max(6, guiTop - 24), guiW - 20);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRecipeTabs(GuiGraphics graphics, List<CatalogBarterRecipe> recipes, int mouseX, int mouseY) {
        int tabY = guiTop + 24;
        int count = Math.min(recipes.size(), 5);
        int tabW = Math.min(60, (guiW - 20) / count);
        int totalTabW = tabW * count + 2 * (count - 1);
        int startX = guiLeft + (guiW - totalTabW) / 2;

        for (int i = 0; i < count; i++) {
            int tx = startX + i * (tabW + 2);
            boolean sel = i == selectedIndex;
            boolean hov = mouseX >= tx && mouseX <= tx + tabW && mouseY >= tabY && mouseY <= tabY + 12;
            graphics.fill(tx, tabY, tx + tabW, tabY + 12,
                    sel ? ShopColors.BG_CARD_HOVER : (hov ? 0xFF222222 : ShopColors.BG_PANEL));
            if (sel) {
                graphics.fill(tx, tabY + 10, tx + tabW, tabY + 12, ShopColors.ACCENT_PURPLE);
            }
            graphics.drawCenteredString(this.font, "Recipe " + (i + 1), tx + tabW / 2, tabY + 2,
                    sel ? ShopColors.TEXT_PRIMARY : ShopColors.TEXT_SECONDARY);
        }
    }

    private void renderReceivePanel(GuiGraphics graphics, int x, int y, int w, int h, CatalogBarterRecipe recipe) {
        ShopUiUtil.renderAccentPanel(graphics, x, y, w, h,
                ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT, ShopColors.SUCCESS);
        graphics.drawString(this.font, "§aYou Receive", x + 8, y + 6, ShopColors.SUCCESS, false);

        ShopUiUtil.renderLargeItemPreview(graphics, this.font, targetItemId, x + 4, y + 22, w - 8);

        String name = this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayName(targetItemId), w - 16);
        graphics.drawCenteredString(this.font, name, x + w / 2, y + h - 26, ShopColors.TEXT_PRIMARY);

        int totalOutput = recipe.outputCount() * multiplier;
        graphics.drawCenteredString(this.font, "§a×" + totalOutput, x + w / 2, y + h - 14, ShopColors.TEXT_PRICE);
    }

    private void renderGivePanel(GuiGraphics graphics, int x, int y, int w, int h, CatalogBarterRecipe recipe) {
        ShopUiUtil.renderAccentPanel(graphics, x, y, w, h,
                ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT, ShopColors.TEXT_BARTER);
        graphics.drawString(this.font, "§dYou Give", x + 8, y + 6, ShopColors.TEXT_BARTER, false);

        List<CatalogBarterIngredient> ingredients = recipe.ingredients();
        int rowY = y + 22;
        int maxRows = Math.max(1, ingredients.size());
        int rowH = Math.min(36, (h - 30) / maxRows);

        for (int i = 0; i < ingredients.size(); i++) {
            CatalogBarterIngredient ingredient = ingredients.get(i);
            int ry = rowY + i * rowH;
            if (ry + rowH > y + h - 4) break;

            int needed = ingredient.count() * multiplier;
            int owned = ShopUiUtil.countPlayerInventory(ingredient.itemId());
            boolean canPay = owned >= needed;

            ShopUiUtil.renderPanel(graphics, x + 6, ry, w - 12, rowH - 4,
                    ShopColors.BG_PANEL, canPay ? ShopColors.BORDER_DEFAULT : ShopColors.ERROR);
            ShopUiUtil.renderItemIcon(graphics, this.font, ingredient.itemId(), x + 10, ry + (rowH - 20) / 2);

            String iName = this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayName(ingredient.itemId()), w - 60);
            graphics.drawString(this.font, iName, x + 30, ry + 4, ShopColors.TEXT_PRIMARY, false);

            String needStr = "Need " + needed + " / Have " + owned;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(needStr, w - 44), x + 30, ry + 16,
                    canPay ? ShopColors.SUCCESS : ShopColors.ERROR, false);
        }
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
        if (recipes.isEmpty()) return;
        ShopPackets.CHANNEL.sendToServer(new C2SBarterRequestPacket(
                ShopClientState.getActiveShopId(),
                recipes.get(selectedIndex).recipeId(),
                multiplier));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<CatalogBarterRecipe> recipes = recipes();
        if (recipes.size() > 1) {
            int tabY = guiTop + 24;
            int count = Math.min(recipes.size(), 5);
            int tabW = Math.min(60, (guiW - 20) / count);
            int totalTabW = tabW * count + 2 * (count - 1);
            int startX = guiLeft + (guiW - totalTabW) / 2;
            for (int i = 0; i < count; i++) {
                int tx = startX + i * (tabW + 2);
                if (mouseX >= tx && mouseX <= tx + tabW && mouseY >= tabY && mouseY <= tabY + 12) {
                    selectedIndex = i;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
