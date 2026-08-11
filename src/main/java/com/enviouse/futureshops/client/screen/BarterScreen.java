package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.CatalogBarterIngredient;
import com.enviouse.futureshops.data.CatalogBarterRecipe;
import com.enviouse.futureshops.data.CatalogItem;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SBarterRequestPacket;
import com.enviouse.futureshops.network.packets.C2SInventorySyncPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

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
    private EditBox qtyBox;
    // LGB#19: Tooltip tracking for item hover
    private String hoveredItemId = null;
    private String hoveredNbtJson = null;
    // Flat Nocturne buttons: per-frame click zones + hovered control tooltip.
    private final java.util.List<ShopUiUtil.ClickZone> clickZones = new java.util.ArrayList<>();
    private Component hoveredTooltip = null;

    // Spec §8: Confirmation modal overlay
    private ConfirmationModal confirmationModal = null;
    private java.util.UUID pendingRequestId;

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
        ShopPackets.CHANNEL.sendToServer(new C2SInventorySyncPacket(ShopClientState.getActiveShopId()));

        // ═══ Bottom row: Qty LEFT | Confirm RIGHT — buttons are drawn immediate-mode in render() ═══
        int bottomY = guiTop + guiH - 24;

        // Qty box remains a real EditBox widget; the ± / Max / Add / Confirm buttons are flat
        // Nocturne primitives registered as ClickZones each frame.
        int qtyX = guiLeft + 10;
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
    }

    private void setMultiplier(int value) {
        int max = resolveMaxMultiplier();
        multiplier = Math.max(1, Math.min(max, value));
        if (qtyBox != null) qtyBox.setValue(Integer.toString(multiplier));
    }

    /** The wire recipe currently selected in the tab strip, or {@code null} when none exist. */
    private CatalogBarterRecipe selectedRecipe() {
        List<CatalogBarterRecipe> recipes = recipes();
        if (recipes.isEmpty()) return null;
        return recipes.get(Math.min(selectedIndex, recipes.size() - 1));
    }

    /**
     * Resolves the catalog listing this recipe rewards — the recipe's {@code targetListingId}
     * first (exact NBT variant), falling back to the first catalog row with the screen's registry
     * {@code targetItemId} when the listingId is blank (legacy recipes) or no longer in catalog.
     */
    private Optional<CatalogItem> resolveTargetItem(CatalogBarterRecipe recipe) {
        if (recipe != null && !recipe.targetListingId().isBlank()) {
            Optional<CatalogItem> byListing = ShopClientState.getCatalogItem(recipe.targetListingId());
            if (byListing.isPresent()) return byListing;
        }
        return ShopClientState.getCatalogItemByRegistryId(targetItemId);
    }

    /** Display NBT for the recipe's reward — blank when the resolved listing carries none. */
    private String resolveTargetNbt(CatalogBarterRecipe recipe) {
        return resolveTargetItem(recipe).map(CatalogItem::nbtJson).orElse("");
    }

    /** Cart key for the recipe's reward — the resolved listingId (registry id as a last resort). */
    private String resolveTargetListingId(CatalogBarterRecipe recipe) {
        return resolveTargetItem(recipe).map(CatalogItem::listingId).orElse(targetItemId);
    }

    /** Owned count matching the server: NBT-strict when the ingredient pins a tag, else lenient. */
    private static int ownedCount(CatalogBarterIngredient ingredient) {
        return ShopUiUtil.countPlayerInventoryNbt(
                ingredient.itemId(), ingredient.nbtJson(), !ingredient.nbtJson().isBlank());
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
            int owned = ownedCount(ingredient);
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
        hoveredTooltip = null;
        clickZones.clear();

        List<CatalogBarterRecipe> recipes = recipes();
        selectedIndex = Math.min(selectedIndex, Math.max(0, recipes.size() - 1));

        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + 2, ShopColors.ACCENT_PRIMARY);

        graphics.drawCenteredString(this.font, this.title, guiLeft + guiW / 2, guiTop + 10, ShopColors.TEXT_STRONG);

        // Back button top-left — always available, even when there are no recipes.
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 6, guiTop + 6, 44, 14,
                Component.translatable("gui.futureshops.barter.back"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, this::onClose);

        if (recipes.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.barter.no_recipes"),
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
        String summaryNbt = resolveTargetNbt(recipe);
        String summaryName = this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayNameWithNbt(targetItemId, summaryNbt), Math.max(60, guiW - 40));
        String totalText = Component.translatable("gui.futureshops.barter.receive_summary", totalOutput, summaryName).getString();
        graphics.drawCenteredString(this.font, totalText, guiLeft + guiW / 2, bottomY - 12, ShopColors.TEXT_SECONDARY);

        // ═══ Bottom-row flat buttons: [−] qty [+] [Max] … [Add Cart] [Confirm] ═══
        int qtyX = guiLeft + 10;
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                qtyX, bottomY, 16, 16, null, ShopUiUtil.ButtonStyle.SECONDARY, true,
                "-", null, () -> setMultiplier(multiplier - 1));
        boolean plusHov = ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                qtyX + 52, bottomY, 16, 16, null, ShopUiUtil.ButtonStyle.SECONDARY, true,
                "+", null, () -> {
                    if (hasShiftDown()) setMultiplier(resolveMaxMultiplier());
                    else setMultiplier(multiplier + 1);
                });
        if (plusHov) hoveredTooltip = Component.translatable("gui.futureshops.barter.tooltip.shift_max");
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                qtyX + 70, bottomY, 28, 16, Component.translatable("gui.futureshops.barter.max"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, () -> setMultiplier(resolveMaxMultiplier()));
        // Add to Cart — cart lines are keyed by listingId, so add the selected recipe's resolved
        // targetListingId (adding the raw registry id was a silent no-op for NBT-variant listings).
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 160, bottomY, 60, 16, Component.translatable("gui.futureshops.barter.add_cart"),
                ShopUiUtil.ButtonStyle.PRIMARY, true,
                () -> ShopClientState.addToCart(resolveTargetListingId(selectedRecipe()), multiplier));
        // Confirm — disabled until the player can afford the current multiplier.
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 90, bottomY, 80, 16, Component.translatable("gui.futureshops.barter.confirm"),
                ShopUiUtil.ButtonStyle.PRIMARY, canAfford(recipe, multiplier), this::showBarterConfirmation);

        ShopUiUtil.renderStatusPanel(graphics, this.font, guiLeft + 10, Math.max(6, guiTop - 24), guiW - 20);

        super.render(graphics, mouseX, mouseY, partialTick);

        // LGB#19: Render tooltip after everything
        if (hoveredItemId != null) {
            ShopUiUtil.renderItemTooltip(graphics, this.font, hoveredItemId,
                    hoveredNbtJson != null ? hoveredNbtJson : "", mouseX, mouseY);
        } else if (hoveredTooltip != null) {
            graphics.renderTooltip(this.font, hoveredTooltip, mouseX, mouseY);
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
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.barter.recipe_tab", i + 1), tx + tabW / 2, tabY + 2,
                    sel ? ShopColors.TEXT_STRONG : ShopColors.TEXT_MUTED);
        }
    }

    private void renderReceivePanel(GuiGraphics graphics, int x, int y, int w, int h, CatalogBarterRecipe recipe, int mouseX, int mouseY) {
        ShopUiUtil.renderCard(graphics, x, y, w, h);
        graphics.fill(x, y, x + w, y + 2, ShopColors.STATUS_SUCCESS);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.barter.you_receive"), x + 8, y + 6, ShopColors.STATUS_SUCCESS, false);

        // Recover the reward listing's display NBT via the recipe's resolved targetListingId
        // (registry-id fallback for legacy recipes). Without it BEWLR items like TacZ guns
        // render as a missing texture.
        String targetNbt = resolveTargetNbt(recipe);
        ShopUiUtil.renderLargeItemPreviewWithNbt(graphics, this.font, targetItemId, targetNbt, x + 4, y + 22, w - 8);

        // LGB#19: Detect hover on receive item preview for tooltip
        if (mouseX >= x + 4 && mouseX <= x + w - 4 && mouseY >= y + 22 && mouseY <= y + 22 + 60) {
            hoveredItemId = targetItemId;
            hoveredNbtJson = targetNbt;
        }

        String name = this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayNameWithNbt(targetItemId, targetNbt), w - 16);
        graphics.drawCenteredString(this.font, name, x + w / 2, y + h - 26, ShopColors.TEXT_STRONG);

        int totalOutput = recipe.outputCount() * multiplier;
        graphics.drawCenteredString(this.font, "§a×" + totalOutput, x + w / 2, y + h - 14, ShopColors.STATUS_SUCCESS);
    }

    private void renderGivePanel(GuiGraphics graphics, int x, int y, int w, int h, CatalogBarterRecipe recipe, int mouseX, int mouseY) {
        ShopUiUtil.renderCard(graphics, x, y, w, h);
        graphics.fill(x, y, x + w, y + 2, ShopColors.TEXT_BARTER_SOFT);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.barter.you_give"), x + 8, y + 6, ShopColors.TEXT_BARTER_SOFT, false);

        List<CatalogBarterIngredient> ingredients = recipe.ingredients();
        int rowY = y + 22;
        int maxRows = Math.max(1, ingredients.size());
        int rowH = Math.min(36, (h - 30) / maxRows);

        for (int i = 0; i < ingredients.size(); i++) {
            CatalogBarterIngredient ingredient = ingredients.get(i);
            int ry = rowY + i * rowH;
            if (ry + rowH > y + h - 4) break;

            int needed = ingredient.count() * multiplier;
            // NBT-strict owned count when the ingredient pins a tag — mirrors what the server
            // will actually accept as payment.
            int owned = ownedCount(ingredient);
            boolean canPay = owned >= needed;

            ShopUiUtil.renderPanel(graphics, x + 6, ry, w - 12, rowH - 4,
                    canPay ? ShopColors.SURFACE_RAISED : ShopColors.SURFACE_BASE,
                    canPay ? ShopColors.BORDER_MUTED : ShopColors.STATUS_DANGER);
            ShopUiUtil.renderItemIconWithNbt(graphics, this.font, ingredient.itemId(), ingredient.nbtJson(),
                    x + 10, ry + (rowH - 20) / 2);

            // LGB#19: Detect hover on ingredient icon for tooltip
            int iconY = ry + (rowH - 20) / 2;
            if (mouseX >= x + 10 && mouseX <= x + 26 && mouseY >= iconY && mouseY <= iconY + 16) {
                hoveredItemId = ingredient.itemId();
                hoveredNbtJson = ingredient.nbtJson();
            }

            String iName = this.font.plainSubstrByWidth(
                    ShopUiUtil.getItemDisplayNameWithNbt(ingredient.itemId(), ingredient.nbtJson()), w - 60);
            graphics.drawString(this.font, iName, x + 30, ry + 4, ShopColors.TEXT_STRONG, false);

            String needStr = Component.translatable("gui.futureshops.barter.need_have", needed, owned).getString();
            graphics.drawString(this.font, this.font.plainSubstrByWidth(needStr, w - 44), x + 30, ry + 16,
                    canPay ? ShopColors.STATUS_SUCCESS : ShopColors.STATUS_DANGER, false);
        }
    }

    private List<CatalogBarterRecipe> recipes() {
        return ShopClientState.getBarterRecipesForItem(targetItemId);
    }

    private boolean canAfford(CatalogBarterRecipe recipe, int tradeMultiplier) {
        return recipe.ingredients().stream()
                .allMatch(ingredient -> ownedCount(ingredient) >= ingredient.count() * tradeMultiplier);
    }

    private void sendConfirm() {
        List<CatalogBarterRecipe> recipes = recipes();
        if (recipes.isEmpty()) return;
        pendingRequestId = java.util.UUID.randomUUID();
        ShopPackets.CHANNEL.sendToServer(new C2SBarterRequestPacket(
                ShopClientState.getActiveShopId(),
                recipes.get(selectedIndex).recipeId(),
                multiplier,
                pendingRequestId));
    }

    private void showBarterConfirmation() {
        List<CatalogBarterRecipe> recipes = recipes();
        if (recipes.isEmpty() || selectedIndex >= recipes.size()) return;
        CatalogBarterRecipe recipe = recipes.get(selectedIndex);
        java.util.List<ConfirmationModal.SummaryLine> lines = new java.util.ArrayList<>();
        int totalOutput = recipe.outputCount() * multiplier;
        String targetNbt = resolveTargetNbt(recipe);
        lines.add(ConfirmationModal.SummaryLine.item(targetItemId,
                Component.translatable("gui.futureshops.barter.confirm.receive_line",
                        ShopUiUtil.getItemDisplayNameWithNbt(targetItemId, targetNbt), totalOutput).getString(), targetNbt));
        for (CatalogBarterIngredient ingredient : recipe.ingredients()) {
            int needed = ingredient.count() * multiplier;
            // Pass the ingredient's nbt so the Give line's icon/name/tooltip show the exact
            // tagged variant the server will demand.
            lines.add(ConfirmationModal.SummaryLine.item(ingredient.itemId(),
                    Component.translatable("gui.futureshops.barter.confirm.give_line",
                            ShopUiUtil.getItemDisplayNameWithNbt(ingredient.itemId(), ingredient.nbtJson()), needed).getString(),
                    ingredient.nbtJson()));
        }
        confirmationModal = new ConfirmationModal(
                Component.translatable("gui.futureshops.barter.confirm.title").getString(),
                lines,
                Component.translatable("gui.futureshops.barter.confirm.total", multiplier).getString(),
                modal -> {
                    modal.setProcessing();
                    sendConfirm();
                },
                () -> confirmationModal = null
        );
    }

    public void onTransactionResult(
            java.util.UUID requestId,
            boolean success,
            String message
    ) {
        if (requestId == null || !requestId.equals(pendingRequestId)) {
            return;
        }
        pendingRequestId = null;
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
        // Flat Nocturne buttons: run the top-most hit ClickZone first.
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
            return true;
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
