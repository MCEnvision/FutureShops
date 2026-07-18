package com.enviouse.futureshops.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
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
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

    /**
     * Localizes a cart-verification warning in the CLIENT's language. The server sends only a
     * stable {@code warningCode} plus any dynamic args packed into {@code detail} (never English),
     * so non-English clients see the warning in their own language. Unknown codes fall back to the
     * raw detail string.
     */
    public static Component cartWarningComponent(String warningCode, String detail) {
        String[] a = (detail == null || detail.isEmpty())
                ? new String[0]
                : detail.split(com.enviouse.futureshops.network.packets.S2CVerifyCartResponsePacket.WARNING_ARG_SEP, -1);
        String arg0 = a.length > 0 ? a[0] : "";
        String arg1 = a.length > 1 ? a[1] : "";
        return switch (warningCode == null ? "" : warningCode) {
            case "SHOP_REMOVED" -> Component.translatable("gui.futureshops.cart.warn.shop_removed");
            case "LISTING_REMOVED" -> Component.translatable("gui.futureshops.cart.warn.listing_removed");
            case "ITEM_REMOVED" -> Component.translatable("gui.futureshops.cart.warn.item_removed");
            case "ITEM_CHANGED" -> Component.translatable("gui.futureshops.cart.warn.item_changed",
                    getItemDisplayName(arg0), getItemDisplayName(arg1));
            case "NBT_DISABLED" -> Component.translatable("gui.futureshops.cart.warn.nbt_disabled");
            case "NBT_CHANGED" -> Component.translatable("gui.futureshops.cart.warn.nbt_changed");
            case "MODE_CHANGED" -> Component.translatable("gui.futureshops.cart.warn.mode_changed",
                    tradeModeLabel(arg0), tradeModeLabel(arg1));
            case "PRICE_CHANGED" -> Component.translatable("gui.futureshops.cart.warn.price_changed");
            case "LOW_STOCK" -> Component.translatable("gui.futureshops.cart.warn.low_stock", arg0, arg1);
            default -> Component.literal(detail == null ? "" : detail);
        };
    }

    /** Full warning line "§c⚠ #N: <localized message>" for the cart screens. */
    public static Component cartWarningLine(int cartLineIndex, String warningCode, String detail) {
        return Component.translatable("gui.futureshops.cart.warn.line",
                cartLineIndex + 1, cartWarningComponent(warningCode, detail));
    }

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

        java.math.BigInteger value = java.math.BigInteger.valueOf(
                minorUnits);
        java.math.BigInteger divisor = java.math.BigInteger.TEN.pow(
                decimals);
        java.math.BigInteger[] parts = value.abs()
                .divideAndRemainder(divisor);
        String sign = value.signum() < 0 ? "-" : "";
        return sign + parts[0] + "." + String.format(
                java.util.Locale.ROOT, "%0" + decimals + "d",
                parts[1].longValueExact());
    }

    public static int countPlayerInventory(String itemId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
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
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
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
            if (stack.getItem() == item && java.util.Objects.equals(stack.getTag(), requiredTag)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : minecraft.player.getInventory().offhand) {
            if (stack.getItem() == item && java.util.Objects.equals(stack.getTag(), requiredTag)) {
                total += stack.getCount();
            }
        }
        return total;
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
        String itemName = getItemDisplayNameWithNbt(firstIngredient.itemId(), firstIngredient.nbtJson());
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

    // ═══════════════════════════════════════════════════════════════════════
    // Nocturne primitives — the component vocabulary for the redesign.
    // See docs/redesign-nocturne-spec.md. Dark blurple system: chamfered panels,
    // one accent, rules + accent lines that fade to transparent at both ends.
    // ═══════════════════════════════════════════════════════════════════════

    /** Returns {@code color} with its alpha channel replaced by {@code alpha} (0-255). */
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /**
     * Chamfered "rounded" panel: 1px-inset corners (they show the ground behind, reading as a
     * radius) with a 1px divider outline. The Nocturne surface primitive — use for cards, sidebars,
     * inspector panels, list rows.
     */
    public static void renderNocturnePanel(GuiGraphics graphics, int x, int y, int width, int height, int fill, int border) {
        // Interior fill, inset 1px so the outline reads cleanly and corners stay open.
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        // Chamfered outline: each edge stops 1px short of the corners.
        graphics.fill(x + 1, y, x + width - 1, y + 1, border);                 // top
        graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, border); // bottom
        graphics.fill(x, y + 1, x + 1, y + height - 1, border);                 // left
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, border); // right
    }

    /** {@link #renderNocturnePanel} with the default surface + divider colors. */
    public static void renderNocturnePanel(GuiGraphics graphics, int x, int y, int width, int height) {
        renderNocturnePanel(graphics, x, y, width, height, ShopColors.SURFACE_RAISED, ShopColors.BORDER_MUTED);
    }

    /** Panel + a 2px accent top-line that fades at both ends (inspector / featured cards). */
    public static void renderNocturnePanelAccentTop(GuiGraphics graphics, int x, int y, int width, int height) {
        renderNocturnePanel(graphics, x, y, width, height);
        renderAccentLine(graphics, x + 2, y, width - 4);
    }

    /**
     * Horizontal 1px rule that fades to transparent at both ends — the Nocturne signature. Solid in
     * the middle, alpha-stepped over a ~48px ramp at each end.
     */
    public static void renderFadingRule(GuiGraphics graphics, int x, int y, int width, int color) {
        int fade = Math.min(48, Math.max(1, width / 3));
        int mid0 = x + fade;
        int mid1 = x + width - fade;
        if (mid1 > mid0) {
            graphics.fill(mid0, y, mid1, y + 1, color);
        }
        int steps = 8;
        int seg = Math.max(1, fade / steps);
        for (int i = 0; i < steps; i++) {
            int a = (int) (((i + 1) / (float) (steps + 1)) * (color >>> 24));
            int c = withAlpha(color, a);
            // left ramp: faint at the outer edge, stronger toward the middle
            graphics.fill(x + i * seg, y, x + (i + 1) * seg, y + 1, c);
            // right ramp mirrored
            graphics.fill(x + width - (i + 1) * seg, y, x + width - i * seg, y + 1, c);
        }
    }

    /** {@link #renderFadingRule} with the default divider color. */
    public static void renderFadingRule(GuiGraphics graphics, int x, int y, int width) {
        renderFadingRule(graphics, x, y, width, ShopColors.BORDER_MUTED);
    }

    /** 2px accent line fading at both ends — the window/dialog top signature. */
    public static void renderAccentLine(GuiGraphics graphics, int x, int y, int width) {
        renderFadingRule(graphics, x, y, width, ShopColors.ACCENT_PRIMARY);
        renderFadingRule(graphics, x, y + 1, width, ShopColors.ACCENT_PRIMARY);
    }

    /** Tag/pill styles matching the Nocturne {@code .tag-*} classes. */
    public enum TagStyle { ACCENT, ACCENT2, NEUTRAL, OUTLINE }

    /**
     * Draws a small tag/pill and returns its width. ACCENT/ACCENT2/NEUTRAL are filled chips;
     * OUTLINE is a bordered accent chip (the "Trade" badge).
     */
    public static int renderTag(GuiGraphics graphics, Font font, int x, int y, String text, TagStyle style) {
        int textW = font.width(text);
        int w = Math.max(20, textW + 12);
        switch (style) {
            case ACCENT -> { renderNocturnePanel(graphics, x, y, w, 14, ShopColors.ACCENT_800, ShopColors.ACCENT_800);
                graphics.drawString(font, text, x + 6, y + 3, ShopColors.ACCENT_100, false); }
            case ACCENT2 -> { renderNocturnePanel(graphics, x, y, w, 14, ShopColors.ACCENT2_800, ShopColors.ACCENT2_800);
                graphics.drawString(font, text, x + 6, y + 3, ShopColors.ACCENT2_100, false); }
            case NEUTRAL -> { renderNocturnePanel(graphics, x, y, w, 14, ShopColors.NEUTRAL_900, ShopColors.NEUTRAL_900);
                graphics.drawString(font, text, x + 6, y + 3, ShopColors.NEUTRAL_300, false); }
            case OUTLINE -> { renderNocturnePanel(graphics, x, y, w, 14, ShopColors.SURFACE_RAISED, ShopColors.ACCENT_700);
                graphics.drawString(font, text, x + 6, y + 3, ShopColors.ACCENT_300, false); }
        }
        return w;
    }

    /** Toggle switch: 26x14 pill track + knob, accent when on. Returns its width (26). */
    public static void renderToggle(GuiGraphics graphics, int x, int y, boolean on) {
        int trackColor = on ? ShopColors.ACCENT_PRIMARY : ShopColors.NEUTRAL_800;
        renderNocturnePanel(graphics, x, y, 26, 14, trackColor, trackColor);
        int knobX = on ? x + 26 - 11 : x + 2;
        graphics.fill(knobX, y + 2, knobX + 9, y + 12, ShopColors.TEXT_STRONG);
    }

    /**
     * Coin glyph + amount, in the lavender currency color. Returns the total width drawn so callers
     * can lay out following content. The "coin" is a small filled lozenge (no Phosphor in MC).
     */
    public static int renderCoinAmount(GuiGraphics graphics, Font font, int x, int y, String amount, int amountColor) {
        // coin: a 7px filled diamond in the currency lavender
        int cx = x + 3, cy = y + 4;
        for (int dy = -3; dy <= 3; dy++) {
            int half = 3 - Math.abs(dy);
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, ShopColors.ACCENT_CURRENCY);
        }
        graphics.drawString(font, amount, x + 9, y, amountColor, false);
        return 9 + font.width(amount);
    }

    /** A [−] value-box [+] stepper. Returns the clickable regions + total width. */
    public record Stepper(int minusX, int plusX, int btn, int width) {
        public boolean hitMinus(int mx, int my, int y) { return mx >= minusX && mx < minusX + btn && my >= y && my < y + btn; }
        public boolean hitPlus(int mx, int my, int y) { return mx >= plusX && mx < plusX + btn && my >= y && my < y + btn; }
    }

    /** Draws a stepper of button size {@code btn} (e.g. 24 or 20). */
    public static Stepper renderStepper(GuiGraphics graphics, Font font, int x, int y, String value, int valueW, int btn) {
        renderNocturnePanel(graphics, x, y, btn, btn, ShopColors.SURFACE_RAISED, ShopColors.BORDER_MUTED);
        graphics.drawCenteredString(font, "-", x + btn / 2, y + (btn - 8) / 2, ShopColors.TEXT_MUTED);
        int boxX = x + btn + 4;
        renderNocturnePanel(graphics, boxX, y, valueW, btn, ShopColors.SURFACE_BASE, ShopColors.BORDER_MUTED);
        graphics.drawCenteredString(font, value, boxX + valueW / 2, y + (btn - 8) / 2, ShopColors.TEXT_STRONG);
        int plusX = boxX + valueW + 4;
        renderNocturnePanel(graphics, plusX, y, btn, btn, ShopColors.SURFACE_RAISED, ShopColors.BORDER_MUTED);
        graphics.drawCenteredString(font, "+", plusX + btn / 2, y + (btn - 8) / 2, ShopColors.TEXT_MUTED);
        return new Stepper(x, plusX, btn, btn + 4 + valueW + 4 + btn);
    }

    /** Segmented control; active option gets an inset accent outline + accent text. Returns segment x-edges (length = labels+1) for hit-testing. */
    public static int[] renderSegmented(GuiGraphics graphics, Font font, int x, int y, int height, String[] labels, int activeIdx) {
        int[] edges = new int[labels.length + 1];
        edges[0] = x;
        int cx = x;
        for (int i = 0; i < labels.length; i++) {
            int segW = font.width(labels[i]) + 22;
            boolean active = i == activeIdx;
            int fill = active ? ShopColors.SURFACE_PRESSED : ShopColors.SURFACE_RAISED;
            graphics.fill(cx, y, cx + segW, y + height, fill);
            if (active) {
                drawBorder(graphics, cx, y, segW, height, ShopColors.ACCENT_PRIMARY);
            }
            if (i > 0) {
                graphics.fill(cx, y, cx + 1, y + height, ShopColors.BORDER_MUTED);
            }
            graphics.drawCenteredString(font, labels[i], cx + segW / 2, y + (height - 8) / 2,
                    active ? ShopColors.ACCENT_300 : ShopColors.TEXT_MUTED);
            cx += segW;
            edges[i + 1] = cx;
        }
        // outer outline
        drawBorder(graphics, x, y, cx - x, height, ShopColors.BORDER_MUTED);
        return edges;
    }

    /** Big number + unit label (shop cards, payouts). Returns width consumed. */
    public static int renderStatBlock(GuiGraphics graphics, Font font, int x, int y, String number, String unit) {
        graphics.drawString(font, number, x, y, ShopColors.TEXT_STRONG, false);
        int nw = font.width(number);
        graphics.drawString(font, unit, x + nw + 4, y, ShopColors.NEUTRAL_500, false);
        return nw + 4 + font.width(unit);
    }

    /**
     * Department / nav sidebar row: optional glyph slot, label, right-aligned count, and a 3px accent
     * left bar when selected. Caller supplies the row rectangle.
     */
    public static void renderDeptRow(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                                     String label, String count, boolean selected, boolean hovered) {
        if (selected) {
            graphics.fill(x, y, x + width, y + height, ShopColors.SURFACE_PRESSED);
            graphics.fill(x, y + 2, x + 3, y + height - 2, ShopColors.ACCENT_PRIMARY);
        } else if (hovered) {
            graphics.fill(x, y, x + width, y + height, ShopColors.SURFACE_OVERLAY);
        }
        int labelColor = selected ? ShopColors.TEXT_STRONG : ShopColors.TEXT_MUTED;
        graphics.drawString(font, font.plainSubstrByWidth(label, width - 34), x + 10, y + (height - 8) / 2, labelColor, false);
        if (count != null && !count.isEmpty()) {
            int cw = font.width(count);
            graphics.drawString(font, count, x + width - cw - 8, y + (height - 8) / 2, ShopColors.NEUTRAL_600, false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Shell chrome — the shared window / header / breadcrumb / footer used by
    // every top-level view (ShopMainScreen in Phase 3; the player-shop browse in
    // Phase 4). Deliberately generic: the caller supplies its own tab list,
    // balance string, crumbs and hint, and gets back a hit model for input
    // routing. All wordmark / label text routes through Component.translatable so
    // the i18n guard stays green.
    // ═══════════════════════════════════════════════════════════════════════

    /** Deterministic per-player accent swatch (profile square / shop avatar). */
    private static final int[] PLAYER_SWATCH = {
            0xFF9184D9, 0xFF7FCF9E, 0xFFE0B87A, 0xFFE08B93,
            0xFF6FB7E0, 0xFFC792EA, 0xFFB5ABFC, 0xFF8FD3B0
    };

    public static int playerColor(UUID uuid) {
        if (uuid == null) return ShopColors.ACCENT_PRIMARY;
        return PLAYER_SWATCH[Math.floorMod(uuid.hashCode(), PLAYER_SWATCH.length)];
    }

    private static Component shellBrand()    { return Component.translatable("gui.futureshops.shell.brand"); }
    private static Component shellBrandSub() { return Component.translatable("gui.futureshops.shell.brand_sub"); }

    /** SURFACE_BASE ground + hairline outline + the fading blurple accent top-line. */
    public static void renderShellWindow(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, ShopColors.SURFACE_BASE);
        drawBorder(g, x, y, width, height, ShopColors.BORDER_STRONG);
        renderAccentLine(g, x, y, width);
    }

    /** Clickable bounds returned by {@link #renderShellHeader}. Rects are {x, y, w, h}. */
    public record HeaderHit(int[] tabEdges, int tabY, int tabH,
                            int[] closeRect, int[] profileRect,
                            int[] searchRect, int[] balanceRect) {
        static boolean in(int[] r, double mx, double my) {
            return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
        }
        public boolean hitClose(double mx, double my)   { return in(closeRect, mx, my); }
        public boolean hitProfile(double mx, double my) { return in(profileRect, mx, my); }
        public boolean hitBalance(double mx, double my) { return in(balanceRect, mx, my); }
        public boolean hitSearch(double mx, double my)  { return in(searchRect, mx, my); }
        /** Index of the tab under (mx,my), or -1. */
        public int tabAt(double mx, double my) {
            if (tabEdges == null || my < tabY || my >= tabY + tabH) return -1;
            for (int i = 0; i + 1 < tabEdges.length; i++) {
                if (mx >= tabEdges[i] && mx < tabEdges[i + 1]) return i;
            }
            return -1;
        }
    }

    /**
     * Pure layout for {@link #renderShellHeader} — computes the clickable bounds without drawing so
     * the owning screen can position its search {@code EditBox} in {@code init()} against the exact
     * geometry the renderer will hit-test. The balance / player-name widths are fixed (content is
     * clipped), so the search box position stays stable as the balance changes at runtime.
     */
    public static HeaderHit headerLayout(Font font, int x, int y, int width, int headerH,
                                         String[] tabLabels, String balance, String playerName, boolean compact) {
        compact = effectiveShellCompact(font, width, tabLabels, compact);
        int pad = PAD_LG;
        int ctrlH = 16;
        int ctrlY = y + (headerH - ctrlH) / 2;

        // In compact mode the logo tile + wordmark are not drawn, so don't reserve their width.
        int wmW = Math.max(font.width(shellBrand().getString()), font.width(shellBrandSub().getString()));
        int logoBlockW = compact ? 0 : (18 + 7 + wmW);
        int tabsX = x + pad + logoBlockW + (compact ? 2 : 14);

        int[] tabEdges = new int[tabLabels.length + 1];
        tabEdges[0] = tabsX;
        int cx = tabsX;
        for (int i = 0; i < tabLabels.length; i++) {
            cx += font.width(tabLabels[i]) + 20;
            tabEdges[i + 1] = cx;
        }
        int tabsEndX = cx;

        int rightX = x + width - pad;
        int closeW = 16;
        int closeX = rightX - closeW;
        int gap = 8;
        int profileW = compact ? 22 : 100;
        int profileX = closeX - gap - profileW;
        int balanceW = compact ? 46 : 74;
        int balanceX = profileX - gap - balanceW;

        int searchX = tabsEndX + 12;
        int searchW = Math.max(46, balanceX - gap - searchX);

        return new HeaderHit(tabEdges, y + 3, headerH - 6,
                new int[]{closeX, ctrlY, closeW, ctrlH},
                new int[]{profileX, ctrlY, profileW, ctrlH},
                new int[]{searchX, ctrlY, searchW, ctrlH},
                new int[]{balanceX, ctrlY, balanceW, ctrlH});
    }

    /**
     * Draws the shell header row (logo + wordmark | primary tabs | search | balance pill | profile
     * pill | close) and returns the {@link HeaderHit} clickable model. The search pill is drawn here
     * but the actual {@code EditBox} is a widget the caller overlays inside {@code searchRect}.
     */
    public static HeaderHit renderShellHeader(GuiGraphics g, Font font, int x, int y, int width, int headerH,
                                              String[] tabLabels, int activeIdx, String balance,
                                              String playerName, UUID playerUuid, boolean compact,
                                              int mouseX, int mouseY) {
        compact = effectiveShellCompact(font, width, tabLabels, compact);
        HeaderHit hit = headerLayout(font, x, y, width, headerH, tabLabels, balance, playerName, compact);
        int ctrlY = hit.searchRect()[1];
        int ctrlH = hit.searchRect()[3];
        int cy = y + headerH / 2;

        // logo tile + wordmark
        if (!compact) {
            int tile = 18;
            int tileX = x + PAD_LG;
            int tileY = cy - tile / 2;
            g.fillGradient(tileX, tileY, tileX + tile, tileY + tile, ShopColors.ACCENT_900, ShopColors.SURFACE_BASE);
            drawBorder(g, tileX, tileY, tile, tile, ShopColors.ACCENT_700);
            g.fill(tileX + 4, tileY + 5, tileX + tile - 4, tileY + 7, ShopColors.ACCENT_300);       // awning
            g.fill(tileX + 7, tileY + 8, tileX + tile - 7, tileY + tile - 4, ShopColors.ACCENT_400); // doorway
            int wmX = tileX + tile + 7;
            g.drawString(font, shellBrand(), wmX, cy - 9, ShopColors.TEXT_STRONG, false);
            g.drawString(font, shellBrandSub(), wmX, cy + 1, ShopColors.NEUTRAL_500, false);
        }

        // primary tabs
        int[] edges = hit.tabEdges();
        for (int i = 0; i < tabLabels.length; i++) {
            int mid = (edges[i] + edges[i + 1]) / 2;
            boolean active = i == activeIdx;
            boolean hover = mouseX >= edges[i] && mouseX < edges[i + 1] && mouseY >= hit.tabY() && mouseY < hit.tabY() + hit.tabH();
            g.drawCenteredString(font, tabLabels[i], mid, ctrlY + (ctrlH - 8) / 2,
                    active || hover ? ShopColors.TEXT_STRONG : ShopColors.TEXT_MUTED);
            if (active) {
                renderAccentLine(g, edges[i] + 10, y + headerH - 3, (edges[i + 1] - edges[i]) - 20);
            }
        }

        // search pill (+ magnifier glyph)
        int[] s = hit.searchRect();
        renderNocturnePanel(g, s[0], s[1], s[2], s[3], ShopColors.SURFACE_RAISED, ShopColors.BORDER_MUTED);
        int gx = s[0] + 4, gy = s[1] + s[3] / 2 - 3;
        drawBorder(g, gx, gy, 5, 5, ShopColors.NEUTRAL_500);
        g.fill(gx + 4, gy + 4, gx + 6, gy + 6, ShopColors.NEUTRAL_500);

        // balance pill (coin + amount) — clicks route to history on the screen side
        int[] b = hit.balanceRect();
        renderNocturnePanel(g, b[0], b[1], b[2], b[3], ShopColors.SURFACE_RAISED,
                hit.hitBalance(mouseX, mouseY) ? ShopColors.BORDER_STRONG : ShopColors.BORDER_MUTED);
        renderCoinAmount(g, font, b[0] + 4, b[1] + (b[3] - 8) / 2,
                font.plainSubstrByWidth(balance, b[2] - 12), ShopColors.TEXT_STRONG);

        // profile pill
        int[] p = hit.profileRect();
        renderNocturnePanel(g, p[0], p[1], p[2], p[3], ShopColors.SURFACE_RAISED,
                hit.hitProfile(mouseX, mouseY) ? ShopColors.BORDER_GLOW : ShopColors.BORDER_MUTED);
        int sw = 12, swX = p[0] + 4, swY = p[1] + (p[3] - sw) / 2;
        if (playerUuid != null) {
            renderPlayerFace(g, playerUuid, playerName, swX, swY, sw);
        } else {
            g.fill(swX, swY, swX + sw, swY + sw,
                    playerColor(playerUuid));
            drawBorder(g, swX, swY, sw, sw,
                    withAlpha(0xFFFFFFFF, 0x30));
        }
        if (!compact) {
            String nm = font.plainSubstrByWidth(playerName == null ? "" : playerName, p[2] - sw - 12);
            g.drawString(font, nm, swX + sw + 4, p[1] + (p[3] - 8) / 2, ShopColors.NEUTRAL_200, false);
        }

        // close
        int[] c = hit.closeRect();
        boolean closeHover = hit.hitClose(mouseX, mouseY);
        renderNocturnePanel(g, c[0], c[1], c[2], c[3], ShopColors.SURFACE_RAISED,
                closeHover ? ShopColors.BORDER_STRONG : ShopColors.BORDER_MUTED);
        g.drawCenteredString(font, "✕", c[0] + c[2] / 2, c[1] + (c[3] - 8) / 2,
                closeHover ? ShopColors.TEXT_STRONG : ShopColors.TEXT_MUTED);
        return hit;
    }

    public static void renderShellHeaderTooltip(
            GuiGraphics graphics,
            Font font,
            HeaderHit hit,
            int mouseX,
            int mouseY
    ) {
        if (hit == null) {
            return;
        }
        if (hit.hitProfile(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(
                    "gui.futureshops.shell.profile_tooltip"),
                    mouseX, mouseY);
        } else if (hit.hitBalance(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(
                    "gui.futureshops.shell.transactions_tooltip"),
                    mouseX, mouseY);
        }
    }

    private static boolean effectiveShellCompact(Font font, int width,
                                                 String[] tabLabels,
                                                 boolean requested) {
        if (requested) {
            return true;
        }
        int wordmark = Math.max(font.width(shellBrand().getString()),
                font.width(shellBrandSub().getString()));
        int tabs = 0;
        for (String label : tabLabels) {
            tabs += font.width(label) + 20;
        }
        int minimum = PAD_LG * 2 + 18 + 7 + wordmark + 14 + tabs
                + 12 + 46 + 8 + 74 + 8 + 100 + 8 + 16;
        return minimum > width;
    }

    /**
     * Breadcrumb strip: crumbs left-aligned with caret separators (last crumb is emphasized),
     * right-aligned context text. Non-interactive for Phase 3 (the header tabs drive navigation).
     */
    public static void renderBreadcrumb(GuiGraphics g, Font font, int x, int y, int width,
                                        String[] crumbs, String rightText) {
        int cxp = x;
        for (int i = 0; i < crumbs.length; i++) {
            if (i > 0) {
                g.drawString(font, ">", cxp, y, ShopColors.NEUTRAL_600, false);
                cxp += font.width(">") + 6;
            }
            boolean last = i == crumbs.length - 1;
            g.drawString(font, crumbs[i], cxp, y, last ? ShopColors.TEXT_STRONG : ShopColors.TEXT_MUTED, false);
            cxp += font.width(crumbs[i]) + 6;
        }
        if (rightText != null && !rightText.isEmpty()) {
            g.drawString(font, rightText, x + width - font.width(rightText), y, ShopColors.NEUTRAL_500, false);
        }
    }

    /**
     * Footer strip: fading top rule, hint text (left), Cart button with a count badge (right).
     * Returns the Cart button rect {x, y, w, h} for hit-testing.
     */
    public static int[] renderShellFooter(GuiGraphics g, Font font, int x, int y, int width, int height,
                                          String hint, int cartCount, int mouseX, int mouseY) {
        renderFadingRule(g, x, y, width);

        Component cartLabel = Component.translatable("gui.futureshops.shell.cart");
        String cartStr = cartLabel.getString();
        int badgeW = cartCount > 0 ? Math.max(14, font.width(Integer.toString(cartCount)) + 8) : 0;
        int glyphW = 12;
        int btnW = glyphW + 4 + font.width(cartStr) + (badgeW > 0 ? 4 + badgeW : 0) + 16;
        int btnH = Math.min(height - 2, 18);
        int btnX = x + width - btnW;
        int btnY = y + (height - btnH) / 2;

        if (hint != null && !hint.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(hint, Math.max(20, btnX - x - 8)),
                    x, y + (height - 8) / 2, ShopColors.NEUTRAL_500, false);
        }

        boolean hover = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
        renderNocturnePanel(g, btnX, btnY, btnW, btnH, ShopColors.SURFACE_RAISED,
                hover ? ShopColors.ACCENT_PRIMARY : ShopColors.BORDER_MUTED);
        // cart glyph (basket + wheels) drawn from fills
        int ix = btnX + 8, iy = btnY + (btnH - 8) / 2;
        g.fill(ix, iy, ix + 8, iy + 5, ShopColors.ACCENT_300);
        g.fill(ix + 1, iy + 5, ix + 2, iy + 7, ShopColors.ACCENT_300);
        g.fill(ix + 6, iy + 5, ix + 7, iy + 7, ShopColors.ACCENT_300);
        int tx = ix + glyphW;
        g.drawString(font, cartStr, tx, btnY + (btnH - 8) / 2, ShopColors.TEXT_STRONG, false);
        if (badgeW > 0) {
            int bx = tx + font.width(cartStr) + 4;
            int by = btnY + (btnH - 12) / 2;
            renderNocturnePanel(g, bx, by, badgeW, 12, ShopColors.ACCENT_PRIMARY, ShopColors.ACCENT_PRIMARY);
            g.drawCenteredString(font, Integer.toString(cartCount), bx + badgeW / 2, by + 2, 0xFFFFFFFF);
        }
        return new int[]{btnX, btnY, btnW, btnH};
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

    private static final ConcurrentMap<UUID, ResourceLocation>
            PLAYER_SKIN_CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> PLAYER_SKIN_REQUESTS =
            ConcurrentHashMap.newKeySet();

    public static void renderPlayerFace(GuiGraphics graphics,
                                        UUID playerUuid,
                                        int x, int y, int size) {
        renderPlayerFace(graphics, playerUuid, "", x, y, size);
    }

    public static void renderPlayerFace(GuiGraphics graphics,
                                        UUID playerUuid,
                                        String playerName,
                                        int x, int y, int size) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation skin = PLAYER_SKIN_CACHE.getOrDefault(playerUuid,
                DefaultPlayerSkin.getDefaultSkin(playerUuid));
        if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(playerUuid);
            if (info != null) {
                skin = info.getSkinLocation();
                PLAYER_SKIN_CACHE.put(playerUuid, skin);
            }
        }
        if (!PLAYER_SKIN_CACHE.containsKey(playerUuid)
                && PLAYER_SKIN_REQUESTS.add(playerUuid)) {
            requestPlayerSkin(playerUuid, playerName);
        }
        RenderSystem.enableBlend();
        // Face layer (8,8)-(16,16) in the 64x64 skin texture
        graphics.blit(skin, x, y, size, size, 8.0f, 8.0f, 8, 8, 64, 64);
        // Hat overlay layer (40,8)-(48,16) in the 64x64 skin texture
        graphics.blit(skin, x, y, size, size, 40.0f, 8.0f, 8, 8, 64, 64);
        RenderSystem.disableBlend();
        drawBorder(graphics, x, y, size, size, ShopColors.BORDER_DEFAULT);
    }

    private static void requestPlayerSkin(UUID playerUuid,
                                          String playerName) {
        try {
            GameProfile profile = new GameProfile(playerUuid,
                    playerName == null ? "" : playerName);
            net.minecraft.world.level.block.entity.SkullBlockEntity
                    .updateGameprofile(profile, filled -> {
                        if (filled == null) {
                            return;
                        }
                        Minecraft minecraft = Minecraft.getInstance();
                        minecraft.execute(() -> minecraft.getSkinManager()
                                .registerSkins(filled, (type, location,
                                        texture) -> {
                                    if (type
                                            == MinecraftProfileTexture.Type.SKIN
                                            && location != null) {
                                        PLAYER_SKIN_CACHE.put(playerUuid,
                                                location);
                                    }
                                }, false));
                    });
        } catch (Throwable throwable) {
            PLAYER_SKIN_REQUESTS.remove(playerUuid);
        }
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
     * Icon stacks are rebuilt per icon per frame and SNBT parsing is expensive,
     * so cache them by (itemId, nbtJson). Cached stacks are shared display-only
     * instances — callers must never mutate them. Cleared wholesale when full.
     */
    private static final int STACK_CACHE_CAP = 512;
    private static final java.util.Map<String, ItemStack> STACK_CACHE = new java.util.HashMap<>();

    /**
     * Builds an ItemStack from an item ID and optional NBT JSON string.
     * Returns an empty stack if the item can't be resolved. The returned stack
     * is a shared cached instance intended for rendering/tooltips only.
     */
    public static ItemStack buildItemStack(String itemId, String nbtJson) {
        String cacheKey = itemId + ' ' + (nbtJson == null ? "" : nbtJson);
        ItemStack cached = STACK_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        ItemStack stack;
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
        if (item == null) {
            stack = ItemStack.EMPTY;
        } else {
            stack = new ItemStack(item);
            if (nbtJson != null && !nbtJson.isBlank()) {
                try {
                    CompoundTag tag = TagParser.parseTag(nbtJson);
                    stack.setTag(tag);
                } catch (Exception ex) {
                    // Invalid NBT — render the plain stack. Logged (once, thanks to
                    // the cache) because BEWLR items like TacZ guns render as a
                    // missing texture without their tag, which reads as a mod bug.
                    com.mojang.logging.LogUtils.getLogger()
                            .warn("FutureShops: listing NBT for {} failed to parse, rendering without it: {}",
                                    itemId, ex.getMessage());
                }
            }
        }

        if (STACK_CACHE.size() >= STACK_CACHE_CAP) {
            STACK_CACHE.clear();
        }
        STACK_CACHE.put(cacheKey, stack);
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
    private static final java.util.Map<String, Boolean> NBT_BADGE_CACHE = new java.util.HashMap<>();

    public static boolean hasNonDefaultNbt(String itemId, String nbtJson) {
        if (nbtJson == null || nbtJson.isBlank()) return false;
        String cacheKey = itemId + ' ' + nbtJson;
        Boolean cached = NBT_BADGE_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        boolean result = computeHasNonDefaultNbt(itemId, nbtJson);
        if (NBT_BADGE_CACHE.size() >= STACK_CACHE_CAP) {
            NBT_BADGE_CACHE.clear();
        }
        NBT_BADGE_CACHE.put(cacheKey, result);
        return result;
    }

    private static boolean computeHasNonDefaultNbt(String itemId, String nbtJson) {
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

    // ═══════════════════════════════════════════════════════════════════════
    //  Flat Nocturne buttons — one immediate-mode primitive replacing vanilla
    //  Button widgets across every screen. Draw + hit-region come from the SAME
    //  call (registered into a per-screen ClickZone list), so the two can never
    //  drift apart the way the old hand-mirrored geometry did.
    // ═══════════════════════════════════════════════════════════════════════

    /** Flat-button variants (see the design's button styles). */
    public enum ButtonStyle { SECONDARY, PRIMARY, DANGER, DASHED, GHOST }

    /** Danger palette — matches the design's remove-button hex (border/hover fill). */
    private static final int DANGER_BORDER = 0xFF5A2B33;
    private static final int DANGER_HOVER  = 0xFF2A181C;

    /**
     * A registered clickable rectangle with its handler. Screens keep a per-frame list, populated
     * by {@link #button} as each button is drawn, and consult it in {@code mouseClicked} via
     * {@link #dispatchClicks}. Disabled zones never fire.
     */
    public record ClickZone(int x, int y, int w, int h, Runnable onClick, boolean enabled) {
        public boolean hit(double mx, double my) {
            return enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /**
     * Dispatches a click to the TOP-MOST registered zone that contains {@code (mx,my)} (later-drawn
     * wins), runs its handler, and returns true if one fired. Iterate a snapshot so a handler that
     * mutates the list (e.g. opens a sub-screen) can't ConcurrentModify.
     */
    public static boolean dispatchClicks(List<ClickZone> zones, double mx, double my) {
        if (zones == null) return false;
        for (int i = zones.size() - 1; i >= 0; i--) {
            ClickZone z = zones.get(i);
            if (z.hit(mx, my)) {
                z.onClick().run();
                return true;
            }
        }
        return false;
    }

    /** Convenience: flat button with a centered label, no leading symbol/icon. */
    public static boolean button(GuiGraphics g, Font font, List<ClickZone> zones, int mx, int my,
                                 int x, int y, int w, int h, Component label, ButtonStyle style,
                                 boolean enabled, Runnable onClick) {
        return button(g, font, zones, mx, my, x, y, w, h, label, style, enabled, null, null, onClick);
    }

    /**
     * Flat Nocturne button. Optionally draws a leading {@code symbol} (a short glyph string like
     * "+", "-", "✔", "✕") OR a 16px item {@code icon} before the label. Registers its {@link ClickZone}
     * into {@code zones} (pass null for a non-interactive draw). Returns whether it is hovered so the
     * caller can attach a tooltip.
     */
    public static boolean button(GuiGraphics g, Font font, List<ClickZone> zones, int mx, int my,
                                 int x, int y, int w, int h, Component label, ButtonStyle style,
                                 boolean enabled, String symbol, ItemStack icon, Runnable onClick) {
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        int fill, border, text;
        switch (style) {
            case PRIMARY -> {
                border = ShopColors.ACCENT_PRIMARY;
                text = ShopColors.ACCENT_300;
                fill = hover ? withAlpha(ShopColors.ACCENT_PRIMARY, 0x22) : 0;
            }
            case DANGER -> {
                border = DANGER_BORDER;
                text = ShopColors.STATUS_DANGER;
                fill = hover ? DANGER_HOVER : 0;
            }
            case DASHED -> {
                border = ShopColors.NEUTRAL_700;
                text = hover ? ShopColors.TEXT_STRONG : ShopColors.TEXT_MUTED;
                fill = hover ? ShopColors.SURFACE_OVERLAY : 0;
            }
            case GHOST -> {
                border = 0;
                text = hover ? ShopColors.ACCENT_300 : ShopColors.TEXT_MUTED;
                fill = hover ? ShopColors.SURFACE_OVERLAY : 0;
            }
            default -> { // SECONDARY
                border = hover ? ShopColors.ACCENT_PRIMARY : ShopColors.BORDER_MUTED;
                text = ShopColors.TEXT_STRONG;
                fill = hover ? ShopColors.SURFACE_OVERLAY : ShopColors.SURFACE_RAISED;
            }
        }
        if (!enabled) {
            text = ShopColors.NEUTRAL_700;
            border = ShopColors.BORDER_MUTED;
            fill = ShopColors.SURFACE_BASE;
        }
        // Draw the shape.
        if (style == ButtonStyle.DASHED) {
            if ((fill >>> 24) != 0) g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
            drawDashedRect(g, x, y, w, h, border);
        } else if (style == ButtonStyle.GHOST) {
            if ((fill >>> 24) != 0) g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        } else {
            renderNocturnePanel(g, x, y, w, h, fill == 0 ? 0 : fill, border);
        }
        // Leading item icon or symbol, then the label.
        int labelX = x + 8;
        boolean centered = true;
        if (icon != null && !icon.isEmpty()) {
            g.renderItem(icon, x + 6, y + (h - 16) / 2);
            labelX = x + 26;
            centered = false;
        } else if (symbol != null && !symbol.isBlank()) {
            g.drawString(font, symbol, x + 8, y + (h - 8) / 2, text, false);
            labelX = x + 8 + font.width(symbol) + 5;
            centered = false;
        }
        if (label != null) {
            String s = label.getString();
            if (centered) {
                // Only clip when the label genuinely overflows, and reserve just 2px each side —
                // the old w-12 padding erased symbols on small (~16px) buttons and cut "Back"→"Ba".
                int avail = Math.max(4, w - 4);
                if (font.width(s) > avail) {
                    s = font.plainSubstrByWidth(s, avail);
                }
                g.drawString(font, s, x + (w - font.width(s)) / 2, y + (h - 8) / 2, text, false);
            } else {
                s = font.plainSubstrByWidth(s, Math.max(4, x + w - labelX - 6));
                g.drawString(font, s, labelX, y + (h - 8) / 2, text, false);
            }
        }
        if (zones != null && onClick != null) {
            zones.add(new ClickZone(x, y, w, h, onClick, enabled));
        }
        return hover;
    }

    /** Registers a clickable zone for a non-button control (toggle / segment / row) already drawn. */
    public static void zone(List<ClickZone> zones, int x, int y, int w, int h, boolean enabled, Runnable onClick) {
        if (zones != null && onClick != null) {
            zones.add(new ClickZone(x, y, w, h, onClick, enabled));
        }
    }

    /** 1px dashed rectangle border (for DASHED "Add" buttons). */
    private static void drawDashedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        int dash = 3, gap = 2, step = dash + gap;
        for (int i = x + 1; i < x + w - 1; i += step) {
            int e = Math.min(i + dash, x + w - 1);
            g.fill(i, y, e, y + 1, color);                 // top
            g.fill(i, y + h - 1, e, y + h, color);         // bottom
        }
        for (int j = y + 1; j < y + h - 1; j += step) {
            int e = Math.min(j + dash, y + h - 1);
            g.fill(x, j, x + 1, e, color);                 // left
            g.fill(x + w - 1, j, x + w, e, color);         // right
        }
    }
}
