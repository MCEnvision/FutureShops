# FutureShops UI Redesign — Nocturne spec

Source of truth: the imported Claude Design project **"Shop and playershop redesign"**
(`FutureShops.dc.html`, Nocturne design system). This doc is the Minecraft-side translation
plan. The design is HTML/CSS; Minecraft GUIs are immediate-mode Java drawing, so we replicate the
*visual system* (palette, spacing, component shapes, layout) via `ShopColors` + `ShopUiUtil`, not
literal HTML. "Exact as-is" = faithful in structure, hierarchy, and color; not pixel-identical.

## Button system (post-feedback pass)

All vanilla Minecraft `Button` widgets were replaced by ONE flat Nocturne primitive:
`ShopUiUtil.button(g, font, zones, mx, my, x,y,w,h, label, ButtonStyle, enabled, [symbol,] [icon,] onClick)`
draws + registers a `ClickZone` (single source → render/hit can't drift); `ShopUiUtil.dispatchClicks`
runs the top-most hit. Styles: SECONDARY / PRIMARY (accent) / DANGER / DASHED / GHOST. Every screen
keeps a `List<ClickZone>` cleared each render and consulted first in `mouseClicked`. **Zero vanilla
`Button.builder` remain across the 19 shop screens.** The owner screen was rebuilt to the design: a
Listings **inspector** (no footer button row) with labeled sections + Department chips, a Storefront
with **three floating-icon mode buttons** (item icons: first-listing / player_head / custom), rounded
Storage rows, Payout cards, and hover tooltips. Category creation is now discoverable
(`DepartmentPickerScreen` shows existing departments as chips + a dynamic "Create & assign" confirm).
Adversarially reviewed (click-zone wiring): all handlers correct; one narrow-width qty/action overlap
in `PlayerStorefrontScreen` fixed by putting the quantity row on its own line above the actions.

## Design language (Nocturne)

- **Ground:** near-black indigo. Window `--color-bg #161826`; panels/cards `--color-surface #232532`;
  hover `#282a39`; selected `#2f3142`; overlay backdrop `#0a0b12 ~66%`.
- **One primary accent:** blurple `--color-accent #9184d9` — used *sparingly* for active tab,
  focus/selection, primary CTA, accent bars. Secondary lavender `accent-2` for currency/coins.
- **Signature:** a 2px accent top-line that **fades to transparent at both ends**
  (`linear-gradient(90deg, transparent, accent 14%, accent 86%, transparent)`). Freestanding rules
  also fade at both ends (48px ramp). Box outlines stay solid 1px `--color-divider`.
- **Divider:** `#e9e9ed @16%` over the ground ≈ `#383a47`.
- **Corners:** radius 4/8/14px (immediate-mode approximates with tight fills; keep it subtle).
- **Type:** Inter-ish → Minecraft font. Sizes map by relative scale (headings bolder/larger).
- **Icons:** design uses Phosphor. In MC: real item icons where an item exists; otherwise a small
  drawn glyph or a unicode symbol. Don't ship Phosphor.

### Token → ShopColors mapping (Phase 1, DONE)
`SURFACE_BASE=#161826`, `SURFACE_RAISED=#232532`, `SURFACE_OVERLAY=#282a39`,
`SURFACE_PRESSED=#2f3142`, `SURFACE_DIM=0xC00A0B12`, `BORDER_MUTED=#383a47`,
`BORDER_STRONG=neutral-700`, `BORDER_GLOW=ACCENT_PRIMARY=#9184d9`, `TEXT_STRONG=#e9e9ed`,
`TEXT_MUTED=neutral-400`, `TEXT_FAINT=neutral-600`, `TEXT_CURRENCY=accent2-300 (lavender coin)`,
`ACCENT_PRIMARY=#9184d9`, `ACCENT_CURRENCY=accent2-300`, promo pill = `accent-800`/`accent-100`
(not red), `OWNER_ACCENT=ACCENT_PRIMARY` (design uses one accent). Full ramps `NEUTRAL_/ACCENT_/ACCENT2_100..900`.

## Component vocabulary (Phase 2 — `ShopUiUtil` primitives)

Build these once; every screen uses them:
- **Window chrome:** `renderWindow` = ground fill + hairline outline + fading accent top-line.
- **Header bar:** logo tile (accent-gradient rounded square + storefront glyph) + "FutureShops /
  MARKETPLACE" wordmark; **primary tabs** (icon+label, active gets the fading underline);
  search field; **balance pill** (coin + amount); **profile pill** (player-color square + initial +
  name); close button. One `renderHeader(...)` with a tab model.
- **Breadcrumb strip:** crumb buttons + caret separators; right-aligned context text.
- **Footer strip:** hint text left, **Cart button** with count badge right.
- **Fading rule** `renderFadingRule(x,y,w)` — the both-ends-fade divider.
- **Panel/card** `renderNocturnePanel(x,y,w,h[,accentTop])` — surface fill + 1px divider outline,
  optional 2px accent top edge (used by inspector, payout cards).
- **Segmented control** `renderSegmented(options, activeIdx)` — joined buttons, divider outline,
  active option gets inset accent border + accent text. (Buy/Barter, Trade-mode, Same/Separate.)
- **Sidebar dept row** — icon + label + count; selected = surface-pressed + 3px accent left bar.
- **Item card** (browse grid) — icon slot (rounded, inset border) + name (+promo chip) + price row
  (coin + price, struck base if promo) + stock label (colored) + optional "Trade" outline tag.
- **Tags/pills** — `TAG_ACCENT` (accent-800/accent-100), `TAG_ACCENT2`, `TAG_NEUTRAL` (neutral-900/300),
  `TAG_OUTLINE` (accent border/text). Extend existing `drawChip`.
- **Stepper** `renderStepper` — [−] value-box [+] (and small variant); value box = bg + divider.
- **Toggle switch** `renderToggle(on)` — pill track + knob, accent when on (Exact-item-match, etc.).
- **Stat block** — big number + unit label (shop cards, payouts).
- **Table rows** — for settlements/history, with the row-level fading rule.

## Views (Phases 3–6)

All views live inside the one shell (header + breadcrumb + content + footer), switched by state —
mirroring the design's single-window, `sc-if`-switched structure. This unifies today's separate
screens (`ShopMainScreen`, `LocalShopBrowserScreen`, `ItemDetailScreen`, `CartScreen`,
`PlayerShopBlockScreen`, `BarterScreen`) into a coherent system.

1. **Browse** (server shop AND drilled player shop): dept sidebar (194px) + grid column
   (toolbar: Buy/Barter segmented, filter chips, sort button; then item-card grid). Empty state =
   package glyph + message.
2. **Player-shops list:** responsive grid of shop cards — color avatar+initial, name (+franchise
   flag tag), "by owner", distance, stat row (shops/items/in-stock), department chips, "Browse →".
3. **My Shop (owner manage):** sub-tabs Listings / Storefront / Storage / Payouts.
   - **Listings:** left = your-listings list (slot icon, name, hidden/showcase glyphs, price·stock,
     "Add listing"); right = **inspector** (header icon+name+itemId + Visible/Showcase toggles;
     Trade-mode segmented; Price stepper + Promo button; Stock + **Batch size** stepper; Barter chips
     + Add; **Exact item match** toggle w/ explanation = the relabeled NBT; Listing description;
     Buyback direction/price/cap; Remove listing).
   - **Storefront:** shop name + description; Departments (chips w/ ×, Add); **Floating shop icon**
     modes (Cycle listings / Owner head / Custom item).
   - **Storage:** **Linked storage · N/cap** list (each: package glyph, label, item count, Unlink) +
     "Link a container you're looking at"; **Bartered-goods storage** Same/Separate segment + note.
   - **Payouts:** Pending settlement card (Collect) + Lifetime revenue card; Recent settlements
     table; **Saved configurations** (Save current as… / Copy / Paste).
4. **Item detail overlay** (centered dialog): left = big preview + Quantity stepper + Max + Total;
   right = name/itemId/close, price(+promo+stock), description, "Or trade for it" barter list,
   actions **Buy now** (accent CTA) / **Add cart** (outline) / Barter.
5. **Cart overlay** (right-side drawer): header, line rows (slot, name, line-total, qty stepper,
   remove), footer total + checkout.

## Player-shop block: two open modes (Phase 4)

The placed block opens one of two ways (owner-configurable — extends today's `singleItemMode`):
- **Option A — specific listing:** opens straight to the Item-detail view of one chosen listing.
- **Option B — storefront:** opens the Browse view scoped to that owner's shop (all listings, depts).
  **Requires working linked storage** — if nothing is linked / storage is empty, the storefront tells
  the customer it's unavailable rather than showing buyable-but-unfulfillable listings.

## Backend work implied by the design (Phase 5)

Several design elements need server support (each an additive wire field + persistence, protocol bump):
- ✅ **Multiple linked storage** (`ShopBlockEntity.linkedStoragePos` → list; resolution iterates).
  DONE (5a) — composite `CompositeStorageOps`, double-chest aliasing deduped by `canonicalStorageKey`.
- ✅ **Showcase** + **hidden** per-listing flags (`Listing.showcase`/`hidden`, protocol 27). DONE — persisted,
  on the `PlayerShopListingData` wire, `TOGGLE_SHOWCASE`/`TOGGLE_HIDDEN` owner actions, buy/sell/cart
  server-rejection for visitors, storefront hides hidden + tags/blocks showcase. Owner *toggles* land in 5b.
- ✅ **Floating icon mode** (cycle / owner-head / custom-item, protocol 27). DONE — `ShopBlockEntity.FloatingIconMode`,
  resolved into display stacks in `handleUpdateTag` (no renderer change), `C2SPlayerShopIconPacket` +
  `applyFloatingIcon`. Owner *control* lands in 5b's Storefront sub-tab.
- **Saved named shop configs** (SavedData) beyond the block clipboard. → community list. NOT STARTED (pairs with 5b Payouts).
- **Exact item match** = existing `nbtAware`, just relabeled in UI. No backend change.
- **Departments/promo/buyback/barter** already exist server-side.

Backends land before their controls by design. **5b stage 1 (done):** `PlayerShopBlockScreen` (owner-only
now — visitors use `PlayerStorefrontScreen`) gained a 4-sub-tab layout (Listings / Storefront / Storage /
Payouts) that declutters the previously-stacked footer/config buttons — each tab shows only its own controls
(the existing `reflowFooterButtons` auto-declutters visible buttons). The **hidden/showcase** toggles are now
wired into the Listings inspector (`TOGGLE_HIDDEN`/`TOGGLE_SHOWCASE`), so that feature is fully end-to-end.

**5b stage 2 (done):** the S2C owner-data packet (`S2CPlayerShopDataPacket`) gained trailing
`floatingIconMode`/`floatingIconItem` + a `PlayerShopStorageEntry` list (protocol 27). The **Storefront tab**
has a floating-icon selector (cycles CYCLE / OWNER_HEAD / CUSTOM_ITEM = held item; sends `C2SPlayerShopIconPacket`),
so floating-icon is now fully end-to-end. The **Storage tab** lists every linked container (client-localized
block name + item count) with a per-entry **Unlink** button (`UNLINK_INDEX` action, index-aligned + bounds-checked).

**5b stage 3 (done):** saved-named-configs — `PlayerShopSavedConfigs` (per-player, persistent `SavedData`,
capped at 16, insertion-ordered), `C2SPlayerShopSavedConfigPacket` (SAVE/APPLY/DELETE, owner-gated), the
`savedConfigNames` list on the owner-data packet, and the Payouts-tab UI (name box + Save/Apply/Delete + names
list). Adversarially reviewed (no exploitable bugs; name normalization made symmetric across save/get/delete).

**All Phase 5 backends are now built.** 5b visual polish applied (accent-top panels + fading rules on all three
non-Listings tabs; Payouts revenue as stat blocks). Remaining 5b cosmetics: finer design-matching (mode cards,
storage glyphs) and footer-overflow at very high UI scales — both best driven by in-game feedback.

**Phase 6 (done — presentational).** Item-detail (`ItemDetailScreen`) is now a **centered dialog** (capped
~380×320, centered over the dim backdrop, fading accent top) instead of a full-screen takeover; both carts
(`CartScreen`, `PlayerShopCartScreen`) are **right-side drawers** (anchored right, near-full height, fading accent
top). All three leverage existing relative positioning so their layouts re-flow automatically. Deliberately NOT
done: rendering these as true overlays on top of a *live* browse view behind them (a high-risk interaction-model
refactor); the dim-backdrop + centered/anchored box already gives the modal look. Refine with visual feedback.

## Phasing

1. ✅ Palette retune (done — reskins everything immediately).
2. ✅ Component primitives in `ShopUiUtil`.
3. ✅ Shell + Server-shop Browse.
4. ✅ Player-shops list + player-shop Browse + two-mode block.
5. ✅ My Shop management + the backends above (finer visual polish pending in-game feedback).
6. ✅ Item-detail + Cart overlays (centered dialog + right drawers; presentational).
7. ✅ Integrate, i18n sweep, final adversarial pass, boot smoke-test, build.

**Phase 7 (done).** i18n guard green (one orphan key removed). Boot smoke-test: the mod loads,
registers (43 packets, block-entity fields, SavedData, mixins), starts and shuts down cleanly on a
real Forge 1.20.1 game-test server. Final 4-dimension adversarial review found 6 issues, all fixed:
one **MAJOR** barter-payment dupe — `insertAll` committed stacks partially then returned false while
the caller refunded the full payment, so the committed portion was duplicated; fixed by making
`insertAll` all-or-nothing (per-slot delta tracking + roll-back-via-extract on shortfall). Five minor
UI fixes: footer wraps onto the link row instead of overflowing off-screen; `mouseScrolled` gated to
the Listings tab; cart name clips before the qty triplet; cart warnings clipped above the button row;
(one flagged item was unreachable dead visitor code). Build green at 110 tests. **The redesign is
complete and verified end-to-end.**

Each phase compiles and is reviewable on its own; the palette retune already makes the current
screens look Nocturne while the structural rebuild proceeds.
