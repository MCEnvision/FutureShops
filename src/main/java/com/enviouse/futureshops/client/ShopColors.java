package com.enviouse.futureshops.client;

/**
 * Central ARGB color palette for all FutureShops GUI rendering.
 *
 * <p><b>Nocturne redesign.</b> The palette is the Minecraft translation of the "Nocturne" design
 * system (dark blurple): surface hierarchy on a near-black indigo ground, one primary blurple
 * accent ({@link #ACCENT_PRIMARY} = {@code #9184d9}) used sparingly for active / focused / CTA,
 * a lavender secondary used for currency, and functional success/warning/danger tints. The design
 * tokens below are the Nocturne CSS variables; the tonal ramps ({@code NEUTRAL_*}, {@code ACCENT_*},
 * {@code ACCENT2_*}) are its OKLCH ramps, for components that need precise steps.
 *
 * <p>All values are 32-bit ARGB (0xAARRGGBB). The legacy alias block keeps older call sites
 * compiling — retuning a token here re-skins every screen that references it.
 */
public final class ShopColors {

    private ShopColors() {
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Nocturne tonal ramps — precise OKLCH steps (prefer for new components).
    // ═══════════════════════════════════════════════════════════════════════

    public static final int NEUTRAL_100 = 0xFFF3F5FE;
    public static final int NEUTRAL_200 = 0xFFE4E7F5;
    public static final int NEUTRAL_300 = 0xFFCFD3E5;
    public static final int NEUTRAL_400 = 0xFFB2B6CA;
    public static final int NEUTRAL_500 = 0xFF9397AB;
    public static final int NEUTRAL_600 = 0xFF75798C;
    public static final int NEUTRAL_700 = 0xFF595D6C;
    public static final int NEUTRAL_800 = 0xFF3F424D;
    public static final int NEUTRAL_900 = 0xFF292B31;

    public static final int ACCENT_100 = 0xFFF5F4FF;
    public static final int ACCENT_200 = 0xFFE7E5FE;
    public static final int ACCENT_300 = 0xFFD2CEFD;
    public static final int ACCENT_400 = 0xFFB5ABFC;
    public static final int ACCENT_500 = 0xFF968AE0;
    public static final int ACCENT_600 = 0xFF796CBF;
    public static final int ACCENT_700 = 0xFF5D5294;
    public static final int ACCENT_800 = 0xFF423A6A;
    public static final int ACCENT_900 = 0xFF2B2741;

    public static final int ACCENT2_100 = 0xFFF5F4FF;
    public static final int ACCENT2_200 = 0xFFE7E5FE;
    public static final int ACCENT2_300 = 0xFFD2CEFD;
    public static final int ACCENT2_400 = 0xFFB5AFE8;
    public static final int ACCENT2_500 = 0xFF9690C9;
    public static final int ACCENT2_600 = 0xFF7972A9;
    public static final int ACCENT2_700 = 0xFF5C5783;
    public static final int ACCENT2_800 = 0xFF423E5D;
    public static final int ACCENT2_900 = 0xFF2B293A;

    // ═══════════════════════════════════════════════════════════════════════
    // Design tokens (prefer these in new code)
    // ═══════════════════════════════════════════════════════════════════════

    // ─── Surfaces ──────────────────────────────────────────────────────────
    /** Full-screen dim behind everything / overlay backdrop (#0a0b12 ~66%). */
    public static final int SURFACE_DIM       = 0xC00A0B12;
    /** Base panel / window ground (--color-bg #161826). */
    public static final int SURFACE_BASE      = 0xFF161826;
    /** A raised surface — card, sidebar, panel (--color-surface #232532). */
    public static final int SURFACE_RAISED    = 0xFF232532;
    /** An overlay surface — hover state (#282a39). */
    public static final int SURFACE_OVERLAY   = 0xFF282A39;
    /** Pressed / selected tint (#2f3142). */
    public static final int SURFACE_PRESSED   = 0xFF2F3142;

    // ─── Borders ───────────────────────────────────────────────────────────
    /** Almost invisible interior divider (neutral-900). */
    public static final int BORDER_SUBTLE     = NEUTRAL_900;
    /** Default card / input outline (--color-divider, resolved over the ground). */
    public static final int BORDER_MUTED      = 0xFF383A47;
    /** Prominent border — hovered (neutral-700). */
    public static final int BORDER_STRONG     = NEUTRAL_700;
    /** Glow / active border — the blurple accent (active tab, focused input, selection). */
    public static final int BORDER_GLOW       = 0xFF9184D9;

    // ─── Text ──────────────────────────────────────────────────────────────
    /** Primary text: titles, item names, values (--color-text). */
    public static final int TEXT_STRONG       = 0xFFE9E9ED;
    /** Default secondary text: descriptions, row labels (neutral-400). */
    public static final int TEXT_MUTED        = NEUTRAL_400;
    /** Disabled / deep-muted text (neutral-600). */
    public static final int TEXT_FAINT        = NEUTRAL_600;
    /** Currency amounts (lavender coin — accent-2-300). */
    public static final int TEXT_CURRENCY     = ACCENT2_300;
    /** Barter / trade readout (accent-300). */
    public static final int TEXT_BARTER_SOFT  = ACCENT_300;

    // ─── Accents ───────────────────────────────────────────────────────────
    /** Primary accent — blurple #9184d9. Use sparingly: active, focused, CTA. */
    public static final int ACCENT_PRIMARY    = 0xFF9184D9;
    /** Primary accent — soft fill (accent @ ~27%). */
    public static final int ACCENT_PRIMARY_DIM = 0x449184D9;
    /** Secondary accent — lavender, for currency and owner context. */
    public static final int ACCENT_CURRENCY   = ACCENT2_300;
    /** Promo / sale highlight — stays in the accent family (accent-400). */
    public static final int ACCENT_PROMO_HI   = ACCENT_400;
    /** Promo soft fill (accent-800). */
    public static final int ACCENT_PROMO_DIM  = 0x55423A6A;

    // ─── Semantic status ───────────────────────────────────────────────────
    public static final int STATUS_SUCCESS    = 0xFF7FCF9E;
    public static final int STATUS_WARNING    = 0xFFE0B87A;
    public static final int STATUS_DANGER     = 0xFFE08B93;
    public static final int STATUS_INFO       = ACCENT_300;

    // ─── Button fills ──────────────────────────────────────────────────────
    /** Neutral button rest state (surface). */
    public static final int BTN_REST          = SURFACE_RAISED;
    /** Neutral button hovered. */
    public static final int BTN_HOVER         = SURFACE_OVERLAY;
    /** Primary CTA fill (accent). */
    public static final int BTN_CTA_REST      = 0xFF9184D9;
    /** Primary CTA hovered (accent-400). */
    public static final int BTN_CTA_HOVER     = ACCENT_400;
    /** Danger button fill (deep rose). */
    public static final int BTN_DANGER        = 0xFF5A2B33;

    // ═══════════════════════════════════════════════════════════════════════
    // Legacy aliases — kept for compatibility with screens not yet migrated.
    // New code should reference the tokens / ramps above.
    // ═══════════════════════════════════════════════════════════════════════

    public static final int BG_PRIMARY          = SURFACE_DIM;
    public static final int BG_PANEL            = SURFACE_BASE;
    public static final int BG_CARD             = SURFACE_RAISED;
    public static final int BG_CARD_HOVER       = SURFACE_OVERLAY;

    public static final int BORDER_DEFAULT      = BORDER_MUTED;
    public static final int BORDER_ACCENT       = BORDER_STRONG;

    public static final int TEXT_PRIMARY        = TEXT_STRONG;
    public static final int TEXT_SECONDARY      = TEXT_MUTED;
    public static final int TEXT_PRICE          = 0xFFCFD3E5;
    public static final int TEXT_BARTER         = TEXT_BARTER_SOFT;

    public static final int BTN_PRIMARY         = BTN_REST;
    public static final int BTN_PRIMARY_HOVER   = BTN_HOVER;
    public static final int BTN_SECONDARY       = BTN_REST;
    public static final int BTN_BARTER          = ACCENT_900;
    public static final int BTN_BARTER_HOVER    = ACCENT_800;

    public static final int ACCENT_GOLD         = ACCENT_CURRENCY;
    public static final int ACCENT_PROMO        = ACCENT_PROMO_HI;
    public static final int ACCENT_CYAN         = ACCENT_PRIMARY;
    public static final int ACCENT_PURPLE       = ACCENT_PRIMARY;
    public static final int ACCENT_ORANGE       = STATUS_WARNING;

    public static final int SUCCESS             = STATUS_SUCCESS;
    public static final int ERROR               = STATUS_DANGER;

    public static final int PROMO_BANNER        = ACCENT_900;
    public static final int PROMO_TEXT          = TEXT_STRONG;

    public static final int INVENTORY_HIGHLIGHT = 0x449184D9;
    public static final int STORAGE_LINKED      = STATUS_INFO;

    // Promo pill — Nocturne uses an accent chip (accent-800 body, accent-100 text), not red.
    public static final int DISCOUNT_BG         = ACCENT_800;
    public static final int DISCOUNT_TEXT       = ACCENT_100;
    public static final int PROFILE_BG          = SURFACE_RAISED;
    public static final int PROFILE_BORDER      = ACCENT_PRIMARY;
    public static final int OWNER_BG            = SURFACE_RAISED;
    // Owner-context accent: the design uses the single blurple accent for owner/editing surfaces.
    public static final int OWNER_ACCENT        = ACCENT_PRIMARY;
    public static final int CONFIG_BG           = SURFACE_RAISED;
    public static final int TOGGLE_ON           = ACCENT_PRIMARY;
    public static final int TOGGLE_OFF          = BORDER_STRONG;
    public static final int HEADER_GRADIENT_L   = ACCENT_900;
    public static final int HEADER_GRADIENT_R   = SURFACE_BASE;
}
