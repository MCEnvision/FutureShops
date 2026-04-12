package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.CatalogBarterIngredient;
import com.enviouse.futureshops.data.CatalogBarterRecipe;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public final class ShopUiUtil {
    private ShopUiUtil() {
    }

    public static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    public static void renderItemIcon(GuiGraphics graphics, Font font, String itemId, int x, int y) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
        if (item == null) {
            graphics.fill(x, y, x + 16, y + 16, ShopColors.BTN_BARTER);
            return;
        }

        ItemStack stack = new ItemStack(item);
        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(font, stack, x, y);
    }

    public static String formatMinorUnits(long minorUnits) {
        int decimals = ShopClientState.getCurrencyDecimals();
        if (decimals <= 0) {
            return Long.toString(minorUnits);
        }

        long divisor = (long) Math.pow(10, decimals);
        long whole = minorUnits / divisor;
        long fractional = Math.abs(minorUnits % divisor);
        return whole + "." + String.format("%0" + decimals + "d", fractional);
    }

    public static int countPlayerInventory(String itemId) {
        return ShopClientState.getOwnedCount(itemId);
    }

    public static String getItemDisplayName(String itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
        if (item == null) {
            return itemId;
        }
        return item.getDescription().getString();
    }

    public static Component buildFirstIngredientSummary(List<CatalogBarterRecipe> recipes) {
        if (recipes.isEmpty() || recipes.get(0).ingredients().isEmpty()) {
            return Component.translatable("gui.futureshops.shop.badge.barter.tooltip.summary.none");
        }

        CatalogBarterIngredient firstIngredient = recipes.get(0).ingredients().get(0);
        int remaining = recipes.get(0).ingredients().size() - 1;
        String itemName = getItemDisplayName(firstIngredient.itemId());
        if (remaining > 0) {
            return Component.translatable(
                    "gui.futureshops.shop.badge.barter.tooltip.summary.more",
                    firstIngredient.count(),
                    itemName,
                    remaining);
        }
        return Component.translatable(
                "gui.futureshops.shop.badge.barter.tooltip.summary.single",
                firstIngredient.count(),
                itemName);
    }

    public static void renderStatusPanel(GuiGraphics graphics, Font font, int x, int y, int width) {
        ShopClientState.ShopStatus status = ShopClientState.getStatus();
        if (status == null) {
            return;
        }

        int background = status.success() ? 0xCC163B26 : 0xCC471B1B;
        int border = status.success() ? ShopColors.SUCCESS : ShopColors.ERROR;
        int height = 18;
        graphics.fill(x, y, x + width, y + height, background);
        drawBorder(graphics, x, y, width, height, border);
        Component clipped = Component.literal(font.plainSubstrByWidth(status.message().getString(), width - 8));
        graphics.drawString(font, clipped, x + 4, y + 5, ShopColors.TEXT_PRIMARY, false);
    }
}



