package com.enviouse.futureshops.client;

/**
 * Central ARGB color palette for all FutureShops GUI rendering.
 *
 * <p>The palette is organised as a small, opinionated design system:
 * <ul>
 *   <li><b>Surfaces</b> — three tiers of neutral slate that form the background hierarchy.</li>
 *   <li><b>Borders</b> — subtle / default / strong / glow, in ascending prominence.</li>
 *   <li><b>Text</b> — primary, secondary, muted, plus semantic text tints.</li>
 *   <li><b>Accents</b> — cyan (primary), amber (currency), magenta (promo), plus status
 *       success / warning / error.</li>
 * </ul>
 *
 * <p>All values are 32-bit ARGB (0xAARRGGBB). Legacy constants remain as aliases so
 * older call sites continue to compile while the system is rolled out.
 */
public final class ShopColors {

    private ShopColors() {
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Design tokens (prefer these in new code)
    // ═══════════════════════════════════════════════════════════════════════

    // ─── Surfaces ──────────────────────────────────────────────────────────
    /** Full-screen dim behind everything. */
    public static final int SURFACE_DIM       = 0xD40D0D0D;
    /** Base panel sitting on the dim. */
    public static final int SURFACE_BASE      = 0xEE141414;
    /** A raised surface (card, sidebar) sitting on a base panel. */
    public static final int SURFACE_RAISED    = 0xFF1E1E1E;
    /** An overlay surface (hover, active selection). */
    public static final int SURFACE_OVERLAY   = 0xFF2B2B2B;
    /** Pressed / heavy tint for selected states. */
    public static final int SURFACE_PRESSED   = 0xFF3A3A3A;

    // ─── Borders ───────────────────────────────────────────────────────────
    /** Almost invisible — for deep interior dividers. */
    public static final int BORDER_SUBTLE     = 0xFF262626;
    /** Default card / input outline. */
    public static final int BORDER_MUTED      = 0xFF3A3A3A;
    /** Prominent border (focused / hovered). */
    public static final int BORDER_STRONG     = 0xFF5A5A5A;
    /** Glow border — only for primary accents (active tab, focused input). */
    public static final int BORDER_GLOW       = 0xFF00D4FF;

    // ─── Text ──────────────────────────────────────────────────────────────
    /** Primary text: titles, item names, values. */
    public static final int TEXT_STRONG       = 0xFFF5F7FA;
    /** Default secondary text: descriptions, row labels. */
    public static final int TEXT_MUTED        = 0xFFB8C0D0;
    /** Disabled / deep-muted text. */
    public static final int TEXT_FAINT        = 0xFF6B7487;
    /** Currency amounts. */
    public static final int TEXT_CURRENCY     = 0xFFFFCB6B;
    /** Barter / trade readout. */
    public static final int TEXT_BARTER_SOFT  = 0xFF8AB4FF;

    // ─── Accents ───────────────────────────────────────────────────────────
    /** Primary accent — cyan. Use sparingly: active, focused, CTA highlights. */
    public static final int ACCENT_PRIMARY    = 0xFF00D4FF;
    /** Primary accent — softer variant for fills. */
    public static final int ACCENT_PRIMARY_DIM = 0x4400D4FF;
    /** Secondary accent — amber / gold. For currency and rewards. */
    public static final int ACCENT_CURRENCY   = 0xFFFFCB6B;
    /** Tertiary accent — magenta. For promos / sale / discounts. */
    public static final int ACCENT_PROMO_HI   = 0xFFFF4FA8;
    /** Magenta soft fill variant. */
    public static final int ACCENT_PROMO_DIM  = 0x55FF4FA8;

    // ─── Semantic status ───────────────────────────────────────────────────
    public static final int STATUS_SUCCESS    = 0xFF5DD89B;
    public static final int STATUS_WARNING    = 0xFFFFB55C;
    public static final int STATUS_DANGER     = 0xFFFF6E6E;
    public static final int STATUS_INFO       = 0xFF8AB4FF;

    // ─── Button fills ──────────────────────────────────────────────────────
    /** Neutral button rest state. */
    public static final int BTN_REST          = 0xFF1E2432;
    /** Neutral button hovered. */
    public static final int BTN_HOVER         = 0xFF2B3348;
    /** Primary button rest state (CTA). */
    public static final int BTN_CTA_REST      = 0xFF003D52;
    /** Primary button hovered. */
    public static final int BTN_CTA_HOVER     = 0xFF005E7A;
    /** Danger button fill. */
    public static final int BTN_DANGER        = 0xFF5A1F28;

    // ═══════════════════════════════════════════════════════════════════════
    // Legacy aliases — kept for compatibility with screens not yet migrated.
    // New code should reference the tokens above.
    // ═══════════════════════════════════════════════════════════════════════

    public static final int BG_PRIMARY          = SURFACE_DIM;
    public static final int BG_PANEL            = SURFACE_BASE;
    public static final int BG_CARD             = SURFACE_RAISED;
    public static final int BG_CARD_HOVER       = SURFACE_OVERLAY;

    public static final int BORDER_DEFAULT      = BORDER_MUTED;
    public static final int BORDER_ACCENT       = BORDER_STRONG;

    public static final int TEXT_PRIMARY        = TEXT_STRONG;
    public static final int TEXT_SECONDARY      = TEXT_MUTED;
    public static final int TEXT_PRICE          = 0xFF7CFCB6;
    public static final int TEXT_BARTER         = TEXT_BARTER_SOFT;

    public static final int BTN_PRIMARY         = BTN_REST;
    public static final int BTN_PRIMARY_HOVER   = BTN_HOVER;
    public static final int BTN_SECONDARY       = BTN_REST;
    public static final int BTN_BARTER          = 0xFF44506A;
    public static final int BTN_BARTER_HOVER    = 0xFF5A6886;

    public static final int ACCENT_GOLD         = ACCENT_CURRENCY;
    public static final int ACCENT_PROMO        = ACCENT_PROMO_HI;
    public static final int ACCENT_CYAN         = ACCENT_PRIMARY;
    // ACCENT_PURPLE is a legacy name from before the minimalist palette pass.
    // It is still used by non-promo surfaces (department chips, trade-mode badges, misc accents),
    // so it now points at the neutral cyan accent — magenta is reserved for real promo/discount UI.
    public static final int ACCENT_PURPLE       = ACCENT_PRIMARY;
    public static final int ACCENT_ORANGE       = STATUS_WARNING;

    public static final int SUCCESS             = STATUS_SUCCESS;
    public static final int ERROR               = STATUS_DANGER;

    public static final int PROMO_BANNER        = 0xFF2A1B28;
    public static final int PROMO_TEXT          = TEXT_STRONG;

    public static final int INVENTORY_HIGHLIGHT = 0x4455AA77;
    public static final int STORAGE_LINKED      = STATUS_INFO;

    public static final int DISCOUNT_BG         = 0xFFD92030; // solid red body
    public static final int DISCOUNT_TEXT       = 0xFF000000; // pure black text for contrast
    public static final int PROFILE_BG          = SURFACE_BASE;
    public static final int PROFILE_BORDER      = ACCENT_PRIMARY;
    public static final int OWNER_BG            = SURFACE_BASE;
    // Owner-context accent: amber/gold evokes ownership and pairs with currency UI,
    // and keeps magenta reserved for promo/sale surfaces.
    public static final int OWNER_ACCENT        = ACCENT_CURRENCY;
    public static final int CONFIG_BG           = SURFACE_BASE;
    public static final int TOGGLE_ON           = STATUS_SUCCESS;
    public static final int TOGGLE_OFF          = BORDER_STRONG;
    public static final int HEADER_GRADIENT_L   = 0xFF0E2336;
    public static final int HEADER_GRADIENT_R   = 0xFF06111A;
}
