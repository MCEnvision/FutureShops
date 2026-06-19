package com.enviouse.futureshopsp.client.screen;

import com.enviouse.futureshopsp.client.ShopClientState;
import com.enviouse.futureshopsp.client.ShopColors;
import com.enviouse.futureshopsp.data.CatalogBarterIngredient;
import com.enviouse.futureshopsp.data.CatalogBarterRecipe;
import com.enviouse.futureshopsp.network.ShopPackets;
import com.enviouse.futureshopsp.network.packets.C2SBarterRequestPacket;
import com.enviouse.futureshopsp.network.packets.C2SInventorySyncPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
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
    // LGB#19: Tooltip tracking for item hover
    private String hoveredItemId = null;
    private String hoveredNbtJson = null;

    // Spec §8: Confirmation modal overlay
    private ConfirmationModal confirmationModal = null;

    public BarterScreen(Screen parent, String targetItemId) {
        super(Component.translatable("gui.futureshops.barter.title"));
        this.parent = parent;
        this.targetItemId = targetItemId;
    }

    @Override
    protected void init() {
        guiW = Math.max(280, this.width - 4);
        guiH = Math.max(180, this.height - 4);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;
        ShopPackets.sendToServer(new C2SInventorySyncPacket(ShopClientState.getActiveShopId()));

        // Back button top-left
        addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.barter.back"), button -> onClose())
                .bounds(guiLeft + 6, guiTop + 6, 44, 14)
                .build());

        // ═══ Bottom row: Qty LEFT | Confirm RIGHT ═══
        int bottomY = guiTop + guiH - 24;

        // Qty controls — left side
        int qtyX = guiLeft + 10;
        addRenderableWidget(Button.builder(Component.literal("-"), button -> setMultiplier(multiplier - 1))
                .bounds(qtyX, bottomY, 16, 16)
                .build());
        qtyBox = new EditBox(this.font, qtyX + 18, bottomY, 32, 16, Component.translatable("gui.futureshops.barter.qty_placeholder"));
        qtyBox.setValue("1");
        qtyBox.setMaxLength(4);
        qtyBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        // Don't write clamped values back during typing — it kills the caret and made
        // the field feel broken when the catalog wasn't loaded yet. Only update state.
        qtyBox.setResponder(value -> {
            if (value.isBlank()) {
                multiplier = 1;
                return;
            }
            try {
                int parsed = Integer.parseInt(value);
                multiplier = Math.max(1, Math.min(resolveMaxMultiplier(), parsed));
            } catch (NumberFormatException ignored) { /* keep last good */ }
        });
        addRenderableWidget(qtyBox);
        addRenderableWidget(Button.builder(Component.literal("+"), button -> {
                    if (hasShiftDown()) setMultiplier(resolveMaxMultiplier());
                    else setMultiplier(multiplier + 1);
                })
                .tooltip(Tooltip.create(Component.translatable("gui.futureshops.barter.tooltip.shift_max")))
                .bounds(qtyX + 52, bottomY, 16, 16)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.barter.max"), button -> setMultiplier(resolveMaxMultiplier()))
                .bounds(qtyX + 70, bottomY, 28, 16)
                .build());

        // Add to Cart button — left of Confirm
        addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.barter.add_cart"), button -> {
                    ShopClientState.addToCart(targetItemId, multiplier);
                })
                .bounds(guiLeft + guiW - 160, bottomY, 60, 16)
                .build());

        // Confirm button — right side
        confirmButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.barter.confirm"), button -> showBarterConfirmation())
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
        // LGB#19: Reset tooltip tracking
        hoveredItemId = null;
        hoveredNbtJson = null;

        List<CatalogBarterRecipe> recipes = recipes();
        selectedIndex = Math.min(selectedIndex, Math.max(0, recipes.size() - 1));

        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + 2, ShopColors.ACCENT_PRIMARY);

        graphics.drawCenteredString(this.font, this.title, guiLeft + guiW / 2, guiTop + 10, ShopColors.TEXT_STRONG);

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
        // Reserve 14px above the button row for the receive-summary line
        int contentH = guiH - (recipes.size() > 1 ? 86 : 72);
        int halfW = (guiW - 40) / 2;

        // ═══ Left panel: You Receive ═══
        renderReceivePanel(graphics, guiLeft + 10, contentY, halfW, contentH, recipe, mouseX, mouseY);

        // ═══ Arrow in between ═══
        int arrowX = guiLeft + guiW / 2;
        int arrowY = contentY + contentH / 2;
        graphics.drawCenteredString(this.font, "§6⟵", arrowX, arrowY - 6, ShopColors.ACCENT_GOLD);
        graphics.drawCenteredString(this.font, "§9⟶", arrowX, arrowY + 6, ShopColors.TEXT_BARTER);

        // ═══ Right panel: You Give ═══
        renderGivePanel(graphics, guiLeft + guiW - halfW - 10, contentY, halfW, contentH, recipe, mouseX, mouseY);

        // ═══ Receive summary — on its own line above the bottom button row ═══
        int bottomY = guiTop + guiH - 24;
        int totalOutput = recipe.outputCount() * multiplier;
        String totalText = "Receive " + totalOutput + "x "
                + this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayName(targetItemId), Math.max(60, guiW - 40));
        graphics.drawCenteredString(this.font, totalText, guiLeft + guiW / 2, bottomY - 12, ShopColors.TEXT_SECONDARY);

        confirmButton.active = canAfford(recipe, multiplier);
        ShopUiUtil.renderStatusPanel(graphics, this.font, guiLeft + 10, Math.max(6, guiTop - 24), guiW - 20);

        super.render(graphics, mouseX, mouseY, partialTick);

        // LGB#19: Render tooltip after everything
        if (hoveredItemId != null) {
            ShopUiUtil.renderItemTooltip(graphics, this.font, hoveredItemId,
                    hoveredNbtJson != null ? hoveredNbtJson : "", mouseX, mouseY);
        }

        // Spec §8: Render confirmation modal on top of everything
        if (confirmationModal != null) {
            confirmationModal.render(graphics, this.font, this.width, this.height, mouseX, mouseY);
            if (confirmationModal.shouldAutoDismiss()) {
                confirmationModal = null;
            }
        }
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
                    sel ? ShopColors.SURFACE_PRESSED : (hov ? ShopColors.SURFACE_OVERLAY : ShopColors.SURFACE_RAISED));
            if (sel) {
                graphics.fill(tx, tabY + 10, tx + tabW, tabY + 12, ShopColors.ACCENT_PRIMARY);
            }
            graphics.drawCenteredString(this.font, "Recipe " + (i + 1), tx + tabW / 2, tabY + 2,
                    sel ? ShopColors.TEXT_STRONG : ShopColors.TEXT_MUTED);
        }
    }

    private void renderReceivePanel(GuiGraphics graphics, int x, int y, int w, int h, CatalogBarterRecipe recipe, int mouseX, int mouseY) {
        ShopUiUtil.renderCard(graphics, x, y, w, h);
        graphics.fill(x, y, x + w, y + 2, ShopColors.STATUS_SUCCESS);
        graphics.drawString(this.font, "§aYou Receive", x + 8, y + 6, ShopColors.STATUS_SUCCESS, false);

        ShopUiUtil.renderLargeItemPreview(graphics, this.font, targetItemId, x + 4, y + 22, w - 8);

        // LGB#19: Detect hover on receive item preview for tooltip
        if (mouseX >= x + 4 && mouseX <= x + w - 4 && mouseY >= y + 22 && mouseY <= y + 22 + 60) {
            hoveredItemId = targetItemId;
            hoveredNbtJson = "";
        }

        String name = this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayName(targetItemId), w - 16);
        graphics.drawCenteredString(this.font, name, x + w / 2, y + h - 26, ShopColors.TEXT_STRONG);

        int totalOutput = recipe.outputCount() * multiplier;
        graphics.drawCenteredString(this.font, "§a×" + totalOutput, x + w / 2, y + h - 14, ShopColors.STATUS_SUCCESS);
    }

    private void renderGivePanel(GuiGraphics graphics, int x, int y, int w, int h, CatalogBarterRecipe recipe, int mouseX, int mouseY) {
        ShopUiUtil.renderCard(graphics, x, y, w, h);
        graphics.fill(x, y, x + w, y + 2, ShopColors.TEXT_BARTER_SOFT);
        graphics.drawString(this.font, "§9You Give", x + 8, y + 6, ShopColors.TEXT_BARTER_SOFT, false);

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
                    canPay ? ShopColors.SURFACE_RAISED : ShopColors.SURFACE_BASE,
                    canPay ? ShopColors.BORDER_MUTED : ShopColors.STATUS_DANGER);
            ShopUiUtil.renderItemIcon(graphics, this.font, ingredient.itemId(), x + 10, ry + (rowH - 20) / 2);

            // LGB#19: Detect hover on ingredient icon for tooltip
            int iconY = ry + (rowH - 20) / 2;
            if (mouseX >= x + 10 && mouseX <= x + 26 && mouseY >= iconY && mouseY <= iconY + 16) {
                hoveredItemId = ingredient.itemId();
                hoveredNbtJson = "";
            }

            String iName = this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayName(ingredient.itemId()), w - 60);
            graphics.drawString(this.font, iName, x + 30, ry + 4, ShopColors.TEXT_STRONG, false);

            String needStr = "Need " + needed + " / Have " + owned;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(needStr, w - 44), x + 30, ry + 16,
                    canPay ? ShopColors.STATUS_SUCCESS : ShopColors.STATUS_DANGER, false);
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
        ShopPackets.sendToServer(new C2SBarterRequestPacket(
                ShopClientState.getActiveShopId(),
                recipes.get(selectedIndex).recipeId(),
                multiplier));
    }

    private void showBarterConfirmation() {
        List<CatalogBarterRecipe> recipes = recipes();
        if (recipes.isEmpty() || selectedIndex >= recipes.size()) return;
        CatalogBarterRecipe recipe = recipes.get(selectedIndex);
        java.util.List<ConfirmationModal.SummaryLine> lines = new java.util.ArrayList<>();
        int totalOutput = recipe.outputCount() * multiplier;
        lines.add(ConfirmationModal.SummaryLine.item(targetItemId, "Receive: " + ShopUiUtil.getItemDisplayName(targetItemId) + " ×" + totalOutput));
        for (CatalogBarterIngredient ingredient : recipe.ingredients()) {
            int needed = ingredient.count() * multiplier;
            lines.add(ConfirmationModal.SummaryLine.item(ingredient.itemId(), "Give: " + ShopUiUtil.getItemDisplayName(ingredient.itemId()) + " ×" + needed));
        }
        confirmationModal = new ConfirmationModal(
                "Confirm Barter",
                lines,
                "Barter " + multiplier + "× trade(s)",
                modal -> {
                    modal.setProcessing();
                    sendConfirm();
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (confirmationModal != null) {
            return confirmationModal.mouseClicked(mouseX, mouseY, button, this.font);
        }
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmationModal != null) {
            if (confirmationModal.keyPressed(keyCode)) return true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
