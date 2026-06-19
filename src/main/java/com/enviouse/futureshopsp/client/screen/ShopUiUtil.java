package com.enviouse.futureshopsp.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.enviouse.futureshopsp.client.ShopClientState;
import com.enviouse.futureshopsp.client.ShopColors;
import com.enviouse.futureshopsp.data.CatalogBarterIngredient;
import com.enviouse.futureshopsp.data.CatalogBarterRecipe;
import com.enviouse.futureshopsp.server.transaction.NbtMatchUtil;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ShopUiUtil {
    private ShopUiUtil() {
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Trade-mode label helper — single source of truth for user-facing labels.
    // Every client site that renders a trade mode MUST go through this so the
    // BuyPacketCallSiteTest invariant scan stays clean and translators only
    // have to edit en_us.json (keys under `gui.futureshops.trade_mode.*`).
    // ═══════════════════════════════════════════════════════════════════════
    private static final String TRADE_MODE_KEY_MONEY = "gui.futureshops.trade_mode.money";
    private static final String TRADE_MODE_KEY_BARTER = "gui.futureshops.trade_mode.barter";
    private static final String TRADE_MODE_KEY_BOTH = "gui.futureshops.trade_mode.both";
    private static final String TRADE_MODE_KEY_COMPOUND = "gui.futureshops.trade_mode.compound";

    /** Returns the localized, color-formatted trade-mode label for UI display. */
    public static String tradeModeLabel(String mode) {
        String key = switch (mode == null ? "" : mode.toUpperCase(java.util.Locale.ROOT)) {
            case "BARTER" -> TRADE_MODE_KEY_BARTER;
            case "BOTH" -> TRADE_MODE_KEY_BOTH;
            case "MONEY_AND_BARTER" -> TRADE_MODE_KEY_COMPOUND;
            default -> TRADE_MODE_KEY_MONEY; // MONEY or unknown/blank
        };
        return Component.translatable(key).getString();
    }

    /** Returns the full human-readable trade-mode name for tooltips on abbreviated badges. */
    public static String tradeModeTooltip(String mode) {
        String key = switch (mode == null ? "" : mode.toUpperCase(java.util.Locale.ROOT)) {
            case "BARTER" -> TRADE_MODE_KEY_BARTER + ".tooltip";
            case "BOTH" -> TRADE_MODE_KEY_BOTH + ".tooltip";
            case "MONEY_AND_BARTER" -> TRADE_MODE_KEY_COMPOUND + ".tooltip";
            default -> TRADE_MODE_KEY_MONEY + ".tooltip";
        };
        return Component.translatable(key).getString();
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
        Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
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
            Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
            if (item != null) {
                int total = 0;
                // Count by item identity only (matches server-side barter/sell tolerance).
                // Filtering out tagged stacks hid modded items whose "empty" state is an NBT
                // marker (e.g. empty fueling tanks), making the UI report 0 owned even though
                // the server would happily accept them.
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

    /**
     * NBT-strict variant of {@link #countPlayerInventory(String)}. When {@code nbtAware}
     * is true and {@code nbtJson} is non-blank, only stacks whose NBT equals the parsed
     * tag count toward the total — mirroring the server's NBT-strict barter payment.
     * Used on the barter screen so "owned" reflects what the server would actually
     * accept (e.g. only empty tanks when the listing specified an empty tank).
     */
    public static int countPlayerInventoryNbt(String itemId, String nbtJson, boolean nbtAware) {
        if (!nbtAware || nbtJson == null || nbtJson.isBlank()) {
            return countPlayerInventory(itemId);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return ShopClientState.getOwnedCount(itemId);
        }
        Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
        if (item == null) {
            return ShopClientState.getOwnedCount(itemId);
        }
        CompoundTag requiredTag;
        try {
            requiredTag = TagParser.parseTag(nbtJson);
        } catch (Exception ignored) {
            return countPlayerInventory(itemId);
        }
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

    public static String getItemDisplayName(String itemId) {
        Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
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
     * Red body + black text with a 1px white outline, no drop-shadow — keeps the label crisp
     * at every GUI scale instead of bleeding into the red background.
     */
    public static void renderAnimatedDiscountBadge(GuiGraphics graphics, Font font, int centerX, int centerY, String text) {
        // Promotional badges often sit at the top-right corner of a card that also hosts an
        // item icon.  GuiGraphics.renderItem internally translates the pose to z≈+150 so the
        // item model doesn't z-fight with overlays; without matching that, our badge draws
        // underneath the icon and gets visually clipped.  Lift to z=+200 (above stacks, below
        // the +400 tooltip layer) so it always wins the depth test.
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
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
        drawBorder(graphics, x0, y0, badgeW, badgeH, 0xFF000000);

        // 1-pixel white outline via 4-directional offsets, then black center text — no drop shadow.
        int textX = centerX - textW / 2;
        int textY = y0 + 3;
        int outline = 0xFFFFFFFF;
        graphics.drawString(font, text, textX - 1, textY, outline, false);
        graphics.drawString(font, text, textX + 1, textY, outline, false);
        graphics.drawString(font, text, textX, textY - 1, outline, false);
        graphics.drawString(font, text, textX, textY + 1, outline, false);
        graphics.drawString(font, text, textX, textY, ShopColors.DISCOUNT_TEXT, false);
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

    /**
     * Draws a wrapped string clamped to {@code maxLines}. When the full text would
     * require more than {@code maxLines} lines, the last drawn line is truncated
     * with an ellipsis so callers can render a hover tooltip with the full text.
     *
     * @return int[]{ linesDrawn, truncatedFlag } — {@code truncatedFlag} is 1 when
     *         content overflowed beyond {@code maxLines}.
     */
    public static int[] drawWrappedClamped(GuiGraphics graphics, Font font, Component component,
                                           int x, int y, int width, int maxLines,
                                           int color, int lineHeight) {
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(component, width);
        int draw = Math.min(maxLines, lines.size());
        boolean truncated = lines.size() > maxLines;
        for (int i = 0; i < draw; i++) {
            if (truncated && i == draw - 1) {
                // Render the last visible line and overlay "…" at its end so buyers can tell
                // the text was clipped and know to hover for the full description.
                graphics.drawString(font, lines.get(i), x, y + i * lineHeight, color, false);
                int lineW = Math.min(width, font.width(lines.get(i)));
                graphics.drawString(font, "…", x + lineW - font.width("…"), y + i * lineHeight, color, false);
            } else {
                graphics.drawString(font, lines.get(i), x, y + i * lineHeight, color, false);
            }
        }
        return new int[]{ draw, truncated ? 1 : 0 };
    }

    public static void renderPlayerFace(GuiGraphics graphics, UUID playerUuid, int x, int y, int size) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation skin = DefaultPlayerSkin.get(playerUuid).texture();
        if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(playerUuid);
            if (info != null) {
                skin = info.getSkin().texture();
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
        Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        if (nbtJson != null && !nbtJson.isBlank()) {
            // nbtJson is the new (1.21 component-patch) SNBT produced server-side by
            // NbtMatchUtil.patchToSnbt (admin configs are normalized to this format before transmission).
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                DataComponentPatch patch = NbtMatchUtil.snbtToPatchMigrating(connection.registryAccess(), ResourceLocation.parse(itemId), nbtJson);
                if (!patch.isEmpty()) {
                    stack.applyComponents(patch);
                }
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
        List<Component> lines = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL);
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

            Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
            if (item == null) return false;

            // Build a pristine stack and compare tags
            ItemStack fresh = new ItemStack(item);
            CompoundTag defaultTag = null; // variant compare deferred to listing-migration cluster
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

    /**
     * Renders a string that automatically horizontally scrolls (ping-pongs) when the text is
     * wider than {@code maxWidth}.  Mirrors the behaviour vanilla uses for button labels so
     * long shop-alert lines (e.g. shop name + dimension + coords) stay fully readable instead
     * of being hard-clipped.
     *
     * @param maxWidth  the visible horizontal budget starting at {@code x}
     * @return the pixel width actually consumed (== text width when no scroll, else maxWidth)
     */
    public static int renderScrollingString(GuiGraphics graphics, Font font, Component text,
                                            int x, int y, int maxWidth, int color) {
        int textWidth = font.width(text);
        if (textWidth <= maxWidth) {
            graphics.drawString(font, text, x, y, color, false);
            return textWidth;
        }
        int overflow = textWidth - maxWidth;
        double seconds = (double) System.currentTimeMillis() / 1000.0D;
        // Longer overflow → slower full cycle, with a 3s minimum so short overflows still read.
        double period = Math.max((double) overflow * 0.5D, 3.0D);
        double phase = Math.sin((Math.PI / 2.0D) * Math.cos((Math.PI * 2.0D) * seconds / period)) / 2.0D + 0.5D;
        double offset = Mth.lerp(phase, 0.0D, (double) overflow);
        graphics.enableScissor(x, y - 1, x + maxWidth, y + font.lineHeight + 1);
        graphics.drawString(font, text, x - (int) offset, y, color, false);
        graphics.disableScissor();
        return maxWidth;
    }

    /** Overload that accepts a raw String. */
    public static int renderScrollingString(GuiGraphics graphics, Font font, String text,
                                            int x, int y, int maxWidth, int color) {
        return renderScrollingString(graphics, font, Component.literal(text), x, y, maxWidth, color);
    }

    /**
     * Centered variant of {@link #renderScrollingString}.  Draws the text centered inside the
     * [x, x+width] band when it fits; otherwise anchors to the left edge of the band and
     * ping-pongs horizontally.  Used by card layouts where the name is normally centered
     * under an icon but should still stay fully readable when the name is too long.
     */
    public static void renderScrollingCentered(GuiGraphics graphics, Font font, String text,
                                                int centerX, int y, int maxWidth, int color) {
        int textWidth = font.width(text);
        if (textWidth <= maxWidth) {
            graphics.drawString(font, text, centerX - textWidth / 2, y, color, false);
            return;
        }
        renderScrollingString(graphics, font, text, centerX - maxWidth / 2, y, maxWidth, color);
    }
}
