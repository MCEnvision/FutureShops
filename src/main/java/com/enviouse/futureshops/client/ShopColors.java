package com.enviouse.futureshops.client;

/**
 * Central ARGB color palette for all FutureShops GUI rendering.
 * Never use raw hex literals outside this class — always reference a named constant.
 *
 * <p>All values are 32-bit ARGB (0xAARRGGBB) compatible with Minecraft's
 * {@code GuiGraphics.fill()}, {@code drawString()}, and related primitives.
 */
public final class ShopColors {

    private ShopColors() {
    }

    // ─── Backgrounds ─────────────────────────────────────────────────────────
    /** Full-screen dimming overlay behind the shop panel. */
    public static final int BG_PRIMARY          = 0xCC0A0A0A;
    /** Main panel / modal background. */
    public static final int BG_PANEL            = 0xEA141414;
    /** Item card / inactive tab fill. */
    public static final int BG_CARD             = 0xFF1E1E1E;
    /** Hovered card / active tab fill. */
    public static final int BG_CARD_HOVER       = 0xFF2A2A2A;

    // ─── Borders ─────────────────────────────────────────────────────────────
    /** Subtle border: card edges, dividers, input field outlines. */
    public static final int BORDER_DEFAULT      = 0xFF4A4A4A;
    /** Accent border: selected card, focused input, active tab. */
    public static final int BORDER_ACCENT       = 0xFFBDBDBD;

    // ─── Text ────────────────────────────────────────────────────────────────
    /** Primary text: item names, headers, labels. */
    public static final int TEXT_PRIMARY        = 0xFFFFFFFF;
    /** Secondary / muted text: descriptions, quantities, helper text. */
    public static final int TEXT_SECONDARY      = 0xFFB0B0B0;
    /** Price / currency text. */
    public static final int TEXT_PRICE          = 0xFF7CFCB6;
    /** Barter / trade text. */
    public static final int TEXT_BARTER         = 0xFF8AB4FF;

    // ─── Buttons ─────────────────────────────────────────────────────────────
    /** Primary call-to-action (Buy, Checkout, Confirm). */
    public static final int BTN_PRIMARY         = 0xFF333333;
    /** Primary CTA hover. */
    public static final int BTN_PRIMARY_HOVER   = 0xFF444444;
    /** Secondary action (Cancel, Back, Add to Cart). */
    public static final int BTN_SECONDARY       = 0xFF2D2D2D;
    /** Barter action button. */
    public static final int BTN_BARTER          = 0xFF5A5A5A;
    /** Barter button hover. */
    public static final int BTN_BARTER_HOVER    = 0xFF6A6A6A;

    // ─── Accents ─────────────────────────────────────────────────────────────
    /** Currency icon / coin indicator (gold). */
    public static final int ACCENT_GOLD         = 0xFFFBBF24;
    /** Promo / discount accent. */
    public static final int ACCENT_PROMO        = 0xFFFF6B6B;

    // ─── Status ──────────────────────────────────────────────────────────────
    /** Positive feedback: purchase confirmed, in-stock. */
    public static final int SUCCESS             = 0xFF61D98A;
    /** Error / out-of-stock / invalid. */
    public static final int ERROR               = 0xFFFF6E6E;

    // ─── Promos ──────────────────────────────────────────────────────────────
    /** Sale banner background. */
    public static final int PROMO_BANNER        = 0xFF2C2C2C;
    /** Text on sale banners. */
    public static final int PROMO_TEXT          = 0xFFFFFFFF;

    // ─── Overlays ────────────────────────────────────────────────────────────
    /** Player-owns-this-item inventory count badge (translucent green). */
    public static final int INVENTORY_HIGHLIGHT = 0x4455AA77;
    /** Storage link status indicator. */
    public static final int STORAGE_LINKED      = 0xFF8AB4FF;
}

