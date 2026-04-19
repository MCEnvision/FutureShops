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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ShopUiUtil {
    private ShopUiUtil() {
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Design-system constants — spacing rhythm used across the redesign.
    // ═══════════════════════════════════════════════════════════════════════
    public static final int PAD_XS = 4;
    public static final int PAD_SM = 8;
    public static final int PAD_MD = 12;
    public static final int PAD_LG = 16;
    public static final int PAD_XL = 24;

    public static final int ROW_HEIGHT = 18;
    public static final int SECTION_GAP = 14;

    // ═══════════════════════════════════════════════════════════════════════
    // Neon-glass surface helpers.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Full-screen translucent dim behind modal-style panels.
     */
    public static void renderDimBackdrop(GuiGraphics graphics, int screenW, int screenH) {
        graphics.fill(0, 0, screenW, screenH, ShopColors.SURFACE_DIM);
    }

    /**
     * Soft two-pixel inset border around a rectangle — the core "glass" outline:
     *  outer line is strong (visible edge), inner line is a dim accent tint.
     */
    public static void drawSoftOutline(GuiGraphics graphics, int x, int y, int width, int height, int outerColor, int innerColor) {
        drawBorder(graphics, x, y, width, height, outerColor);
        // Inner 1px tint
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, innerColor);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, innerColor);
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, innerColor);
        graphics.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, innerColor);
    }

    /**
     * Standard neon-glass card: raised surface + muted border.
     */
    public static void renderCard(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, ShopColors.SURFACE_RAISED);
        drawBorder(graphics, x, y, width, height, ShopColors.BORDER_MUTED);
    }

    /**
     * Elevated card with a 2px cyan accent line on the top edge.
     * Use for primary content cards (item preview, featured row, summary panels).
     */
    public static void renderElevatedCard(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, ShopColors.SURFACE_RAISED);
        drawBorder(graphics, x, y, width, height, ShopColors.BORDER_MUTED);
        graphics.fill(x, y, x + width, y + 2, ShopColors.ACCENT_PRIMARY);
    }

    /**
     * Hero header block — a gradient bar with title + optional subtitle.
     * Use at the top of every screen to anchor the layout.
     */
    public static void renderHeroHeader(GuiGraphics graphics, Font font, int x, int y, int width, String title, String subtitle) {
        int height = subtitle != null && !subtitle.isEmpty() ? 36 : 24;
        // Gradient bar
        graphics.fillGradient(x, y, x + width, y + height, ShopColors.HEADER_GRADIENT_L, ShopColors.HEADER_GRADIENT_R);
        // Subtle top highlight + bottom rule
        graphics.fill(x, y, x + width, y + 1, ShopColors.ACCENT_PRIMARY_DIM);
        graphics.fill(x, y + height - 1, x + width, y + height, ShopColors.BORDER_GLOW);
        // Title
        graphics.drawString(font, title, x + PAD_MD, y + 6, ShopColors.TEXT_STRONG, false);
        if (subtitle != null && !subtitle.isEmpty()) {
            graphics.drawString(font, subtitle, x + PAD_MD, y + 20, ShopColors.TEXT_MUTED, false);
        }
    }

    /**
     * Rounded-looking pill badge — a short label framed with a soft border.
     */
    public static void renderPill(GuiGraphics graphics, Font font, int x, int y, String text, int fill, int border, int textColor) {
        int w = Math.max(24, font.width(text) + 12);
        int h = 12;
        // Body
        graphics.fill(x + 1, y, x + w - 1, y + h, fill);
        graphics.fill(x, y + 1, x + w, y + h - 1, fill);
        // Border
        graphics.fill(x + 1, y, x + w - 1, y + 1, border);
        graphics.fill(x + 1, y + h - 1, x + w - 1, y + h, border);
        graphics.fill(x, y + 1, x + 1, y + h - 1, border);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
        graphics.drawString(font, text, x + 6, y + 2, textColor, false);
    }

    /**
     * Key-value row: left-aligned label in muted text, right-aligned value in strong text.
     */
    public static void renderKVRow(GuiGraphics graphics, Font font, int x, int y, int width, String label, String value, int valueColor) {
        graphics.drawString(font, label, x, y, ShopColors.TEXT_MUTED, false);
        int vw = font.width(value);
        graphics.drawString(font, value, x + width - vw, y, valueColor, false);
    }

    /**
     * Section divider: horizontal rule + label text.
     * Use to break long screens into visual sections.
     */
    public static void renderSectionDivider(GuiGraphics graphics, Font font, int x, int y, int width, String label) {
        if (label == null || label.isEmpty()) {
            graphics.fill(x, y, x + width, y + 1, ShopColors.BORDER_MUTED);
            return;
        }
        int textW = font.width(label);
        int rulePad = 6;
        int leftEnd = x + rulePad;
        int rightStart = x + rulePad + textW + rulePad;
        // Left rule
        graphics.fill(x, y + 3, leftEnd, y + 4, ShopColors.BORDER_MUTED);
        // Label
        graphics.drawString(font, label, leftEnd + rulePad, y, ShopColors.TEXT_FAINT, false);
        // Right rule
        graphics.fill(rightStart, y + 3, x + width, y + 4, ShopColors.BORDER_MUTED);
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
                    if (stack.getItem() == item && !stack.hasTag()) {
                        total += stack.getCount();
                    }
                }
                for (ItemStack stack : minecraft.player.getInventory().offhand) {
                    if (stack.getItem() == item && !stack.hasTag()) {
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

    /**
     * LGB#15: Returns the item display name with inline quantity suffix when baseQuantity > 1.
     * E.g. "Stick ×6" instead of just "Stick".
     */
    public static String getItemDisplayNameWithQty(String itemId, int baseQuantity) {
        String name = getItemDisplayName(itemId);
        return baseQuantity > 1 ? name + " ×" + baseQuantity : name;
    }

    /**
     * LGB#15: NBT-aware variant — uses custom display name if NBT provides one.
     */
    public static String getItemDisplayNameWithNbtAndQty(String itemId, String nbtJson, int baseQuantity) {
        String name = (nbtJson != null && !nbtJson.isBlank())
                ? getItemDisplayNameWithNbt(itemId, nbtJson)
                : getItemDisplayName(itemId);
        return baseQuantity > 1 ? name + " ×" + baseQuantity : name;
    }

    public static int computePromoPercent(long basePrice, long promoPrice) {
        if (basePrice <= 0L || promoPrice < 0L || promoPrice >= basePrice) {
            return 0;
        }
        if (promoPrice == 0L) return 100;
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
     * Subtle, readable discount badge. Static geometry + gentle glow pulse so the text stays sharp.
     */
    public static void renderAnimatedDiscountBadge(GuiGraphics graphics, Font font, int centerX, int centerY, String text) {
        long time = System.currentTimeMillis();
        // Gentle glow — text does not move or rotate, so it remains legible.
        float glow = 0.5f + 0.5f * (float) Math.sin(time * 0.004D);
        int glowAlpha = 0x40 + (int) (0x60 * glow);
        int glowColor = (glowAlpha << 24) | 0x00FF2233;

        int textW = font.width(text);
        int badgeW = textW + 14;
        int badgeH = 14;
        int halfW = badgeW / 2;
        int halfH = badgeH / 2;
        int x0 = centerX - halfW;
        int y0 = centerY - halfH;

        // Soft halo behind the badge
        graphics.fill(x0 - 2, y0 - 2, x0 + badgeW + 2, y0 + badgeH + 2, glowColor);
        // Solid, readable pill
        graphics.fill(x0, y0, x0 + badgeW, y0 + badgeH, ShopColors.DISCOUNT_BG);
        drawBorder(graphics, x0, y0, badgeW, badgeH, 0xFFCC0033);
        graphics.drawString(font, text, centerX - textW / 2, y0 + 3, ShopColors.DISCOUNT_TEXT, true);
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

    /**
     * Renders scroll indicators (▲/▼ arrows + page counter) on the right side of a scrollable region.
     *
     * @param graphics     the graphics context
     * @param font         the font renderer
     * @param x            left edge of the scrollable region
     * @param y            top edge of the scrollable region
     * @param width        width of the scrollable region
     * @param height       height of the scrollable region
     * @param scrollIndex  current scroll offset (first visible index/row)
     * @param maxVisible   how many items/rows are visible at once
     * @param totalItems   total number of items/rows
     */
    public static void renderScrollIndicators(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                                               int scrollIndex, int maxVisible, int totalItems) {
        if (totalItems <= maxVisible) {
            return; // No scrolling needed — nothing to draw
        }

        // LGB#13: Arrows inline with scrollbar track (track at x + width - 6, 3px wide)
        int trackX = x + width - 6;
        int arrowX = trackX - 2; // center arrows near the track

        // ▲ Up arrow — active when not at the top
        boolean canScrollUp = scrollIndex > 0;
        int upColor = canScrollUp ? ShopColors.ACCENT_CYAN : ShopColors.BORDER_DEFAULT;
        graphics.drawString(font, "▲", arrowX, y + 4, upColor, false);

        // ▼ Down arrow — active when not at the bottom
        boolean canScrollDown = scrollIndex + maxVisible < totalItems;
        int downColor = canScrollDown ? ShopColors.ACCENT_CYAN : ShopColors.BORDER_DEFAULT;
        graphics.drawString(font, "▼", arrowX, y + height - 12, downColor, false);

        // Scrollbar track + thumb
        int trackY = y + 16;
        int trackH = height - 32;
        if (trackH > 10) {
            // Track background
            graphics.fill(trackX, trackY, trackX + 3, trackY + trackH, ShopColors.BORDER_DEFAULT);

            // Thumb
            int thumbH = Math.max(6, (int) ((float) maxVisible / totalItems * trackH));
            int maxScroll = Math.max(1, totalItems - maxVisible);
            int thumbY = trackY + (int) ((float) scrollIndex / maxScroll * (trackH - thumbH));
            graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, ShopColors.ACCENT_CYAN);
        }

        // Continuous scroll — no page counter (Item 1 fix: infinite scrollbar, no pages)
    }

    // ═══ Advanced Tooltip Rendering (Item 6) ═══

    /**
     * Builds an ItemStack from an item ID and optional NBT JSON string.
     * Returns an empty stack if the item can't be resolved.
     */
    public static ItemStack buildItemStack(String itemId, String nbtJson) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        if (nbtJson != null && !nbtJson.isBlank()) {
            try {
                CompoundTag tag = TagParser.parseTag(nbtJson);
                stack.setTag(tag);
            } catch (Exception ignored) {
                // Invalid NBT — use plain stack
            }
        }
        return stack;
    }

    /**
     * Renders a full vanilla-style tooltip for an item at the mouse position.
     * Shows enchantments, lore, durability, and all other tooltip lines.
     */
    public static void renderItemTooltip(GuiGraphics graphics, Font font, String itemId, String nbtJson, int mouseX, int mouseY) {
        ItemStack stack = buildItemStack(itemId, nbtJson);
        if (stack.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = stack.getTooltipLines(mc.player, mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL);
        graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    /**
     * Renders an item icon from an ItemStack with NBT data (for correct visual representation).
     */
    public static void renderItemIconWithNbt(GuiGraphics graphics, Font font, String itemId, String nbtJson, int x, int y) {
        ItemStack stack = buildItemStack(itemId, nbtJson);
        if (stack.isEmpty()) {
            graphics.fill(x, y, x + 16, y + 16, ShopColors.BTN_BARTER);
            return;
        }
        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(font, stack, x, y);
    }

    /**
     * Renders a large item preview using an ItemStack with optional NBT.
     */
    public static void renderLargeItemPreviewWithNbt(GuiGraphics graphics, Font font, String itemId, String nbtJson, int panelX, int panelY, int panelW) {
        graphics.pose().pushPose();
        graphics.pose().translate(panelX + (panelW / 2f) - 24f, panelY + 10f, 0f);
        graphics.pose().scale(3.0f, 3.0f, 1f);
        renderItemIconWithNbt(graphics, font, itemId, nbtJson, 0, 0);
        graphics.pose().popPose();
    }

    /**
     * Returns true only when the stored nbtJson represents NBT that differs from the
     * item's default tag.  Items like tools that always carry {Damage:0} are considered
     * "default" and will return false, preventing spurious NBT badges.
     */
    public static boolean hasNonDefaultNbt(String itemId, String nbtJson) {
        if (nbtJson == null || nbtJson.isBlank()) return false;
        try {
            CompoundTag stored = TagParser.parseTag(nbtJson);
            if (stored.isEmpty()) return false;

            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
            if (item == null) return false;

            // Build a pristine stack and compare tags
            ItemStack fresh = new ItemStack(item);
            CompoundTag defaultTag = fresh.getTag(); // may be null
            if (defaultTag == null) {
                // The fresh item has no tag at all — any stored tag is non-default
                // UNLESS the stored tag only contains Damage:0 (vanilla tools do this)
                if (stored.size() == 1 && stored.contains("Damage") && stored.getInt("Damage") == 0) {
                    return false;
                }
                return true;
            }
            return !stored.equals(defaultTag);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Gets the display name for an item with optional NBT (shows custom name if present).
     */
    public static String getItemDisplayNameWithNbt(String itemId, String nbtJson) {
        ItemStack stack = buildItemStack(itemId, nbtJson);
        if (stack.isEmpty()) {
            return itemId;
        }
        return stack.getHoverName().getString();
    }
}



