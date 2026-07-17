package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.CatalogBarterIngredient;
import com.enviouse.futureshops.data.CatalogBarterRecipe;
import com.enviouse.futureshops.data.CatalogItem;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SAdminShopEditPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * OP-only editor for one shop item's barter recipe. The recipe's ingredients (the payment) are added
 * from the searchable registry picker or, for NBT-aware variants, from the OP's MAIN HAND, so a
 * single shop item can cost several different items. All state lives server-side: every action sends a
 * {@link C2SAdminShopEditPacket} (permission-gated + re-validated), the server rewrites admin.json and
 * resends the catalog, and this screen re-reads the live recipe each frame. Nothing is cached locally
 * except the "count to add" box.
 *
 * <p>Keyed by the target listing's resolution key. A recipe with zero ingredients is rejected at the
 * buy path, so the "add your first ingredient" state is safe.
 */
public class BarterRecipeEditorScreen extends Screen implements ShopScreenMarker {

    private final Screen parent;
    private final String listingId;
    private final List<String> listingQueue;
    private final int queueIndex;

    private int guiLeft;
    private int guiTop;
    private int modalW;
    private int modalH;
    private int scrollIndex;

    private EditBox countBox;
    private final java.util.List<ShopUiUtil.ClickZone> clickZones = new java.util.ArrayList<>();

    public BarterRecipeEditorScreen(Screen parent, String listingId) {
        this(parent, List.of(listingId == null ? "" : listingId), 0);
    }

    public BarterRecipeEditorScreen(Screen parent, List<String> listingIds) {
        this(parent, listingIds, 0);
    }

    private BarterRecipeEditorScreen(Screen parent, List<String> listingIds, int queueIndex) {
        super(Component.translatable("gui.futureshops.barter_editor.title"));
        this.parent = parent;
        this.listingQueue = listingIds == null || listingIds.isEmpty() ? List.of("") : List.copyOf(listingIds);
        this.queueIndex = Math.max(0, Math.min(queueIndex, this.listingQueue.size() - 1));
        this.listingId = this.listingQueue.get(this.queueIndex);
    }

    @Override
    protected void init() {
        modalW = Math.max(240, Math.min(340, this.width - 8));
        modalH = Math.max(200, Math.min(300, this.height - 8));
        guiLeft = (this.width - modalW) / 2;
        guiTop = (this.height - modalH) / 2;

        countBox = new EditBox(this.font, 0, 0, 32, 14, Component.translatable("gui.futureshops.barter_editor.count_hint"));
        countBox.setMaxLength(4);
        countBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        countBox.setValue("1");
        addRenderableWidget(countBox);
    }

    private CatalogBarterRecipe currentRecipe() {
        List<CatalogBarterRecipe> rs = ShopClientState.getBarterRecipesForItem(listingId);
        return rs.isEmpty() ? null : rs.get(0);
    }

    private CatalogItem target() {
        return ShopClientState.getCatalogItem(listingId).orElse(null);
    }

    private int addCount() {
        try {
            return Math.max(1, Integer.parseInt(countBox.getValue().trim()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();

        ShopUiUtil.renderDimBackdrop(g, this.width, this.height);
        g.fill(guiLeft, guiTop, guiLeft + modalW, guiTop + modalH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(g, guiLeft, guiTop, modalW, modalH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        g.fill(guiLeft, guiTop, guiLeft + modalW, guiTop + 2, ShopColors.ACCENT_CURRENCY);

        int x = guiLeft + 10;
        int w = modalW - 20;
        Component editorTitle = listingQueue.size() > 1
                ? Component.translatable("gui.futureshops.barter_editor.title_queue",
                        queueIndex + 1, listingQueue.size())
                : this.title;
        g.drawString(this.font, editorTitle, x, guiTop + 8, ShopColors.TEXT_STRONG, false);

        CatalogItem target = target();
        CatalogBarterRecipe recipe = currentRecipe();

        // ── Target (output) card: icon + name + output-count stepper ──
        int cardY = guiTop + 20;
        ShopUiUtil.renderCard(g, x, cardY, w, 28);
        if (target != null) {
            ShopUiUtil.renderItemIconWithNbt(g, this.font, target.itemId(), target.nbtJson(), x + 6, cardY + 6);
            g.drawString(this.font,
                    this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayNameWithNbt(target.itemId(), target.nbtJson()), w - 120),
                    x + 28, cardY + 5, ShopColors.TEXT_STRONG, false);
        } else {
            g.drawString(this.font, this.font.plainSubstrByWidth(listingId, w - 120), x + 8, cardY + 5, ShopColors.TEXT_MUTED, false);
        }
        // "Give N" output stepper on the card's right.
        int outCount = recipe != null ? recipe.outputCount() : 1;
        g.drawString(this.font, Component.translatable("gui.futureshops.barter_editor.give"),
                x + w - 118, cardY + 10, ShopColors.TEXT_MUTED, false);
        ShopUiUtil.Stepper os = ShopUiUtil.renderStepper(g, this.font, x + w - 80, cardY + 7, Integer.toString(outCount), 30, 14);
        ShopUiUtil.zone(clickZones, os.minusX(), cardY + 7, os.btn(), os.btn(), true, () -> setOutput(outCount - 1));
        ShopUiUtil.zone(clickZones, os.plusX(), cardY + 7, os.btn(), os.btn(), true, () -> setOutput(outCount + 1));

        // ── Ingredient list ──
        g.drawString(this.font, Component.translatable("gui.futureshops.barter_editor.cost_header"),
                x, cardY + 34, ShopColors.NEUTRAL_500, false);
        int listY = cardY + 46;
        int listBottom = guiTop + modalH - 52;
        int rowH = 18;
        List<CatalogBarterIngredient> ings = recipe != null ? recipe.ingredients() : List.of();

        if (ings.isEmpty()) {
            g.drawString(this.font, Component.translatable("gui.futureshops.barter_editor.empty_hint"),
                    x, listY + 4, ShopColors.STATUS_DANGER, false);
        } else {
            int visibleRows = Math.max(1, (listBottom - listY) / rowH);
            scrollIndex = Math.max(0, Math.min(scrollIndex, Math.max(0, ings.size() - visibleRows)));
            for (int i = scrollIndex; i < ings.size() && i < scrollIndex + visibleRows; i++) {
                CatalogBarterIngredient ing = ings.get(i);
                int ry = listY + (i - scrollIndex) * rowH;
                final int index = i;
                ShopUiUtil.renderItemIconWithNbt(g, this.font, ing.itemId(), ing.nbtJson(), x + 2, ry);
                String name = ShopUiUtil.getItemDisplayNameWithNbt(ing.itemId(), ing.nbtJson());
                g.drawString(this.font, this.font.plainSubstrByWidth(name, w - 112), x + 22, ry + 4, ShopColors.TEXT_STRONG, false);
                ShopUiUtil.Stepper ingredientStepper = ShopUiUtil.renderStepper(
                        g, this.font, x + w - 86, ry, Integer.toString(ing.count()), 24, 14);
                ShopUiUtil.zone(clickZones, ingredientStepper.minusX(), ry,
                        ingredientStepper.btn(), ingredientStepper.btn(), ing.count() > 1,
                        () -> setIngredientCount(index, ing.count() - 1));
                ShopUiUtil.zone(clickZones, ingredientStepper.plusX(), ry,
                        ingredientStepper.btn(), ingredientStepper.btn(), true,
                        () -> setIngredientCount(index, ing.count() + 1));
                ShopUiUtil.button(g, this.font, clickZones, mouseX, mouseY,
                        x + w - 18, ry, 16, 14, Component.literal("✕"),
                        ShopUiUtil.ButtonStyle.DANGER, true, () -> removeIngredient(index));
            }
            ShopUiUtil.renderScrollIndicators(g, this.font, x, listY, w, visibleRows * rowH, scrollIndex, visibleRows, ings.size());
        }

        // ── Add-ingredient row: held preview + count box + Add ──
        int addY = guiTop + modalH - 48;
        ItemStack held = this.minecraft != null && this.minecraft.player != null
                ? this.minecraft.player.getMainHandItem() : ItemStack.EMPTY;
        g.drawString(this.font, Component.translatable("gui.futureshops.barter_editor.hold_label"), x, addY + 4, ShopColors.TEXT_MUTED, false);
        int heldX = x + 52;
        int controlsX = x + w - 124;
        if (!held.isEmpty()) {
            g.renderItem(held, heldX, addY - 1);
            int heldNameW = Math.max(0, controlsX - heldX - 24);
            if (heldNameW > 8) {
                g.drawString(this.font, this.font.plainSubstrByWidth(held.getHoverName().getString(), heldNameW),
                        heldX + 20, addY + 4, ShopColors.TEXT_STRONG, false);
            }
        } else {
            String emptyText = Component.translatable("gui.futureshops.barter_editor.hold_empty").getString();
            g.drawString(this.font, this.font.plainSubstrByWidth(emptyText, Math.max(0, controlsX - heldX - 4)),
                    heldX, addY + 4, ShopColors.TEXT_FAINT, false);
        }
        countBox.setPosition(controlsX, addY);
        countBox.setWidth(24);
        countBox.setHeight(14);
        countBox.visible = true;
        ShopUiUtil.button(g, this.font, clickZones, mouseX, mouseY,
                controlsX + 28, addY, 54, 14, Component.translatable("gui.futureshops.barter_editor.add_items"),
                ShopUiUtil.ButtonStyle.PRIMARY, true, this::openIngredientPicker);
        ShopUiUtil.button(g, this.font, clickZones, mouseX, mouseY,
                controlsX + 84, addY, 40, 14, Component.translatable("gui.futureshops.barter_editor.add_held"),
                ShopUiUtil.ButtonStyle.SECONDARY, !held.isEmpty(), this::addHeldIngredient);

        // ── Bottom actions: Remove barter | Done ──
        int btnY = guiTop + modalH - 26;
        int half = (w - 8) / 2;
        ShopUiUtil.button(g, this.font, clickZones, mouseX, mouseY,
                x, btnY, half, 16, Component.translatable("gui.futureshops.barter_editor.remove"),
                ShopUiUtil.ButtonStyle.DANGER, true, this::removeBarter);
        ShopUiUtil.button(g, this.font, clickZones, mouseX, mouseY,
                x + half + 8, btnY, half, 16, Component.translatable("gui.futureshops.barter_editor.done"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, this::finishCurrent);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void setOutput(int count) {
        if (count < 1) return;
        send("SET_BARTER_OUTPUT", count);
    }

    private void addHeldIngredient() {
        send("ADD_BARTER_INGREDIENT_HELD", addCount());
    }

    private void openIngredientPicker() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(AdminItemPickerScreen.forBarterIngredients(this, listingId, addCount()));
        }
    }

    private void setIngredientCount(int index, int count) {
        if (count < 1) return;
        ShopPackets.CHANNEL.sendToServer(new C2SAdminShopEditPacket(
                "SET_BARTER_INGREDIENT_COUNT", listingId, "", "", index, count, 0L));
    }

    private void removeIngredient(int index) {
        send("REMOVE_BARTER_INGREDIENT", index);
    }

    /** Remove barter: delete the whole listing when it's barter-only (no money price), else just
     *  strip its barter recipe and keep the money listing. */
    private void removeBarter() {
        CatalogItem target = target();
        boolean barterOnly = target != null && target.buyPrice() <= 0 && target.sellPrice() <= 0;
        ShopPackets.CHANNEL.sendToServer(new C2SAdminShopEditPacket(
                barterOnly ? "REMOVE_LISTING" : "BARTER_OFF", listingId, "", "", 0L, 0L, 0L));
        finishCurrent();
    }

    private void send(String action, long longA) {
        ShopPackets.CHANNEL.sendToServer(new C2SAdminShopEditPacket(action, listingId, "", "", longA, 0L, 0L));
    }

    private void finishCurrent() {
        if (this.minecraft == null) return;
        if (queueIndex + 1 < listingQueue.size()) {
            this.minecraft.setScreen(new BarterRecipeEditorScreen(parent, listingQueue, queueIndex + 1));
        } else {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollIndex = Math.max(0, scrollIndex - (int) delta);
        return true;
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
