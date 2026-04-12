package com.enviouse.futureshops.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.CatalogBarterIngredient;
import com.enviouse.futureshops.data.CatalogBarterRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;

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
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
            if (item != null) {
                int total = 0;
                for (ItemStack stack : minecraft.player.getInventory().items) {
                    if (stack.getItem() == item) {
                        total += stack.getCount();
                    }
                }
                for (ItemStack stack : minecraft.player.getInventory().offhand) {
                    if (stack.getItem() == item) {
                        total += stack.getCount();
                    }
                }
                return total;
            }
        }
        return ShopClientState.getOwnedCount(itemId);
    }

    public static String getItemDisplayName(String itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
        if (item == null) {
            return itemId;
        }
        return item.getDescription().getString();
    }

    public static int computePromoPercent(long basePrice, long promoPrice) {
        if (basePrice <= 0L || promoPrice <= 0L || promoPrice >= basePrice) {
            return 0;
        }
        return (int) Math.max(1L, Math.round((1.0D - (double) promoPrice / (double) basePrice) * 100.0D));
    }

    public static void renderLargeItemPreview(GuiGraphics graphics, Font font, String itemId, int panelX, int panelY, int panelW) {
        graphics.pose().pushPose();
        graphics.pose().translate(panelX + (panelW / 2f) - 24f, panelY + 10f, 0f);
        graphics.pose().scale(3.0f, 3.0f, 1f);
        renderItemIcon(graphics, font, itemId, 0, 0);
        graphics.pose().popPose();
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

    public static void renderPanel(GuiGraphics graphics, int x, int y, int width, int height, int fill, int border) {
        graphics.fill(x, y, x + width, y + height, fill);
        drawBorder(graphics, x, y, width, height, border);
    }

    public static void drawChip(GuiGraphics graphics, Font font, int x, int y, String text, int fill, int border, int color) {
        int width = Math.max(28, font.width(text) + 10);
        graphics.fill(x, y, x + width, y + 12, fill);
        drawBorder(graphics, x, y, width, 12, border);
        graphics.drawString(font, text, x + 5, y + 2, color, false);
    }

    /**
     * Renders an animated discount badge that pops (scales big→small→big) and is tilted 45°.
     * Fully red background with white text. Uses system time for animation.
     */
    public static void renderAnimatedDiscountBadge(GuiGraphics graphics, Font font, int centerX, int centerY, String text) {
        long time = System.currentTimeMillis();
        // Pulsating scale: oscillates between 0.82 and 1.18
        float scale = 1.0f + 0.18f * (float) Math.sin(time * 0.005D);
        float angle = (float) Math.toRadians(-15.0);

        int textW = font.width(text);
        int badgeW = textW + 10;
        int badgeH = 12;

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 200f);
        graphics.pose().scale(scale, scale, 1f);
        com.mojang.math.Axis axis = com.mojang.math.Axis.ZP;
        graphics.pose().mulPose(axis.rotation(angle));

        int halfW = badgeW / 2;
        int halfH = badgeH / 2;
        graphics.fill(-halfW, -halfH, halfW, halfH, ShopColors.DISCOUNT_BG);
        drawBorder(graphics, -halfW, -halfH, badgeW, badgeH, 0xFFCC0033);
        graphics.drawString(font, text, -textW / 2, -halfH + 2, ShopColors.DISCOUNT_TEXT, true);

        graphics.pose().popPose();
    }

    /**
     * Draws a panel with rounded-looking corners via corner accents.
     */
    public static void renderAccentPanel(GuiGraphics graphics, int x, int y, int width, int height, int fill, int border, int accentColor) {
        graphics.fill(x, y, x + width, y + height, fill);
        drawBorder(graphics, x, y, width, height, border);
        // Accent line at top
        graphics.fill(x, y, x + width, y + 2, accentColor);
    }

    /**
     * Draws a gradient horizontal bar.
     */
    public static void drawGradientH(GuiGraphics graphics, int x, int y, int width, int height, int colorLeft, int colorRight) {
        graphics.fillGradient(x, y, x + width, y + height, colorLeft, colorRight);
    }

    public static int drawWrappedString(GuiGraphics graphics, Font font, Component component, int x, int y, int width, int color, int lineHeight) {
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(component, width);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), x, y + i * lineHeight, color, false);
        }
        return lines.size();
    }

    public static void renderPlayerFace(GuiGraphics graphics, UUID playerUuid, int x, int y, int size) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation skin = DefaultPlayerSkin.getDefaultSkin(playerUuid);
        if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(playerUuid);
            if (info != null) {
                skin = info.getSkinLocation();
            }
        }
        RenderSystem.enableBlend();
        // Face layer (8,8)-(16,16) in the 64x64 skin texture
        graphics.blit(skin, x, y, size, size, 8.0f, 8.0f, 8, 8, 64, 64);
        // Hat overlay layer (40,8)-(48,16) in the 64x64 skin texture
        graphics.blit(skin, x, y, size, size, 40.0f, 8.0f, 8, 8, 64, 64);
        RenderSystem.disableBlend();
        drawBorder(graphics, x, y, size, size, ShopColors.BORDER_DEFAULT);
    }
}



