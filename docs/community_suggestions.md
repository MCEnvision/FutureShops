# FutureShops — Community Suggestions & Status

Feedback gathered from the live test server (players + admins, relayed by Isty [IRON]).
This is the working tracker: every suggestion, its status, and — for anything **Planned** — the
direct implementation plan (files + approach) so it can be picked up and built immediately.

**Status legend**
- ✅ **Implemented** — done in the current v2.3 build (uncommitted tree; *not yet deployed* to the test server unless noted).
- 🟡 **Partial** — some of it exists; the rest is Planned.
- 🔧 **Fixed** — a regression that was repaired.
- 📋 **Planned** — committed to do; direct plan included below.
- 💡 **Proposed** — a larger feature that needs a scoping/product decision before planning.

> Note: the test server is running a jar from several days ago. Much of the "Implemented" work
> (in‑GUI admin editor, item picker, i18n, NBT fixes) ships in the **v2.3 build that still needs to
> be deployed**. Redeploy before re-evaluating.

---

## 0. Regressions

### 0.1 — Gun textures broke again in `/shop` 🔧 Fixed (pending redeploy confirmation)
The whole `/shop` NBT render path was re-verified end to end (render call sites, shared
`ShopUiUtil.buildItemStack`, the `CatalogItem.nbtJson` wire field, server catalog build, and all
three editor write paths); all preserve and render gun NBT correctly in the current tree. A guard
test (`AdminShopIconNbtTest`) now fails the build if any `/shop` screen reverts to bare icon
rendering. **Action:** redeploy the fresh v2.3 jar. If one specific gun is still broken it lost its
NBT at the data level — `/shopadmin items list` shows an NBT column, and catalog load logs a warning
naming any `tacz:*` listing missing NBT.

---

## 1. Clearer listings screen

### 1.1 — Make the local/player-shop browser look like the `/shop` customer view 📋 Planned
Players prefer the `/shop` grid (denser, clearer, price-forward). Adopt it for player-shop browsing.
- **Files:** `client/screen/LocalShopBrowserScreen.java` (and the customer view of
  `PlayerShopBlockScreen.java`).
- **Plan:** reuse the `/shop` design-system building blocks (`ShopUiUtil.renderHeroHeader`,
  `renderAccentPanel`, and the `ShopMainScreen.renderItemCard` card layout: icon → name → price row
  → stock row → pills). Refactor the shared card into a `ShopUiUtil.renderListingCard(...)` helper so
  `/shop` and the local browser render identically. No wire change — `LocalShopOwnerEntry.LocalListing`
  already carries price/stock/nbt.

### 1.2 — Show price on local-listing cards without opening the listing 📋 Planned
- **Files:** `client/screen/LocalShopBrowserScreen.java`.
- **Plan:** add a price line to each grid card (`§a` + `ShopUiUtil.formatMinorUnits(listing.priceMinor())`,
  showing promo price struck-through-base when a promo is active). Folded into 1.1's shared card.

### 1.3 — The bare word "Money" confuses people 📋 Planned
"Money" only makes sense once you know the barter/money split.
- **Files:** `client/screen/ShopUiUtil.java` (`tradeModeLabel`), lang `gui.futureshops.trade_mode.*`.
- **Plan:** only show a trade-mode label when the listing actually supports barter (money-only
  listings just show the price, no "Money" tag). For mixed listings, relabel to
  "Money or Trade" / "Trade only" for clarity. Lang-only + one render guard.

### 1.4 — "stk" abbreviation is confusing → spell out "stock" 📋 Planned
- **Files:** lang `gui.futureshops.player_shop_block.rail.stock_short` (`"%s stk"`), and any card
  stock line.
- **Plan:** change to `"%s in stock"` (full-screen HUD has the room). Lang-only.

### 1.5 — Configurable low-stock threshold 📋 Planned
"15 in stock" flagged as low when it's 15 batches of 100.
- **Files:** `Config.java` (new `shop.low_stock_threshold`, default e.g. 8), per-listing optional
  override on `ShopBlockEntity.Listing` + admin.json `lowStockThreshold`; consumers in
  `MarketplaceAnalyticsService` (low-supply alerts) and the card stock-color logic.
- **Plan:** add the config + optional per-listing field (wire + persistence, minor), and replace the
  hard-coded low-stock comparison with `count <= threshold`. Pairs naturally with 1.6.

### 1.6 — Show stock as item count, not batch count 📋 Planned (needs a semantics decision)
600 bullets in a listing sold in ×60 batches currently shows "10". People expect "600".
- **Files:** wherever stock is displayed (`ShopMainScreen`, `PlayerShopBlockScreen`,
  `LocalShopBrowserScreen`, `ItemDetailScreen`) and the stock/low-stock math.
- **Plan:** display `stock × baseQuantity` as the item count (keep the internal accounting in
  batches). **Decision needed:** show pure item count ("600"), or "600 (10×60)"? I recommend
  "600 in stock" with the batch size shown on the buy/detail screen. Once decided this is a
  display-layer change; the low-stock threshold (1.5) should then be expressed in items too.

### 1.7 — Collapse the Base/Now price rows 📋 Planned
Two rows (base + now) clutter when there's usually no sale.
- **Files:** `client/screen/PlayerShopBlockScreen.java` (detail + rail), `ItemDetailScreen.java`.
- **Plan:** render a single price by default; when a promo is active show the sale price in accent
  with the base struck through inline (one row, not two). Render-only change.

### 1.8 — Quantity selector is hard to find (bottom of screen) 📋 Planned
- **Files:** `client/screen/ItemDetailScreen.java` (and player-shop buy/barter/sell screens).
- **Plan:** move the −/qty/+/Max quantity controls up next to the item preview/price (where the eye
  lands) instead of the action-button row at the bottom. Pure layout math.

### 1.9 — NBT system + NBT button are a mystery to ~95% of people 📋 Planned (UX relabel)
- **Files:** `client/screen/PlayerShopBlockScreen.java` (the `TOGGLE_NBT_AWARE` button), lang.
- **Plan:** relabel the button from "NBT" to plain language — "Exact item match: On/Off" — with a
  tooltip explaining it in one sentence ("On = buyers get/pay this exact variant, e.g. this specific
  enchanted book or configured gun"). No behavior change, just naming + tooltip. (The admin editor
  already hides raw NBT behind display-name editing.)

---

## 2. Creating & managing listings

### 2.1 — Create listings without holding the item / item search & selector 🟡 Partial
Holding an item to list is slow and blocks listing things you don't have on you (e.g. to gauge
demand first).
- **Done (✅ admin shop):** the in-GUI admin editor's `AdminItemPickerScreen` is a searchable,
  multi-select registry picker (JEI/EMI-style, text search) that adds listings **without holding
  anything** — this uses the previously-unused horizontal space (also addresses 2.2).
- **📋 Planned (player shops):** bring the same picker to player-owned shops. **Files:**
  new client screen reusing `AdminItemPickerScreen`'s pattern + a new `C2SPlayerShopAddByIdPacket`
  (or a new action on `C2SPlayerShopActionPacket` carrying a registry id) handled in
  `PlayerShopBlockService`. Player-shop listings are NBT-only when added by hand; picker-added ones
  are bare registry items (fine for the "list something I'll craft later" use case).

### 2.2 — Use the unused horizontal space for search/selector ✅ Implemented (admin shop)
Covered by 2.1's `AdminItemPickerScreen`. 📋 Planned to extend to player shops with 2.1.

### 2.3 — "Showcase" mode (display menu, in-person pickup) ✅ Implemented (backend + storefront; owner toggle in redesign 5b)
Owners want to advertise items and have customers come in person, instead of marking everything
"out of stock" (which looks bad and doesn't convey intent).
- **Files:** `ShopBlockEntity.Listing` (new `showcase`/`hidden` booleans, protocol 27) + `PlayerShopListingData`
  wire fields + `PlayerShopBlockService` (visitor buy/sell rejected when showcase/hidden; `TOGGLE_SHOWCASE`/
  `TOGGLE_HIDDEN` owner actions) + `CartVerificationService` (flags a cart line toggled showcase mid-session)
  + `PlayerStorefrontScreen` (hidden listings concealed; showcase gets a "Showcase" tag + all trade buttons
  suppressed). **Owner toggle switches** land in the redesign 5b Listings inspector.
- **Plan:** add the per-listing flag end to end; when set, the listing renders normally with a
  "Visit in person" tag instead of a price/buy control, and purchase attempts are rejected server-side.
  Small wire + persistence + UI addition. (Protocol bump if batched with other wire changes.)

---

## 3. Storage & configuration

### 3.1 — Multiple storage linking ✅ Implemented
Requested by virtually everyone; linked storage fills up fast and more shop blocks are clunky/expensive.
- **Done:** `linkedStoragePositions` list (cap 6), `CompositeStorageOps` (sum stock, all-or-nothing pull
  across links, spill-insert), rollback reinserts drop-to-world instead of losing items, and double-chest
  aliasing is deduped by `canonicalStorageKey` so linking both halves can't double-count/dupe.
- **Files:** `ShopBlockEntity` (`linkedStoragePos` → a list), `PlayerShopBlockService`
  (storage resolution + stock counting + item pull/insert must iterate all linked storages),
  `PlayerShopLinkService` / `LinkCommand` (link/unlink multiple, list linked), persistence + wire.
- **Plan:** change the single linked position into an ordered `List<BlockPos>` (cap e.g. 6, honoring
  `player_shops.max_link_distance_blocks`). Stock = sum across all; sells insert into the first with
  room; buys pull across storages until satisfied (respecting NBT matching). `/link` gains
  add/remove/list; the GUI shows the linked set. Persistence is additive (old single-pos saves
  migrate to a one-element list).

### 3.2 — Separate chest for receiving bartered goods ✅ Implemented
Already supported: a shop can link a **separate barter storage** (barter-storage position) with a
"Same / Separate" toggle in the owner config. Should scale with 3.1 (multiple barter storages).

### 3.3 — Shop config as a data structure, not tied to a block ✅ Implemented
Copy/paste-into-another-block (via `ShopConfigClipboard`) is unintuitive, especially for relocating.
- **Done:** `PlayerShopSavedConfigs` (persistent per-player `SavedData`, named snapshots keyed by
  player UUID, capped at 16, insertion-ordered) + `C2SPlayerShopSavedConfigPacket` (SAVE/APPLY/DELETE,
  owner-gated) + `savedConfigNames` on the owner-data packet + the **Payouts sub-tab** UI (name box +
  Save/Apply/Delete + names list). APPLY reuses `applyConfigSnapshot` (never touches owner UUID / block
  pos / linked storages). `ShopConfigClipboard` stays as the quick session-scoped "copy this block" path.
  So relocating is "break old, place new, type the name, Apply". Adversarially reviewed + unit-tested.

---

## 4. Quality of life

### 4.1 — Remember the last-opened `/shop` screen 📋 Planned
- **Files:** `client/screen/ShopMainScreen.java` (persist last tab/category/mode in a static or
  client field; restore on open).
- **Plan:** store the last selected tab + barter/nearby mode client-side and restore it when `/shop`
  reopens. Client-only, small.

### 4.2 — Break the shop block without high-tier tools (ideally no tool) 📋 Planned
In the modpack a diamond pickaxe is a multi-machine ordeal; the block is a social outlet, not a gate.
- **Files:** `init/ModBlocks.java` (`SHOP_BLOCK` `BlockBehaviour`).
- **Plan:** drop `requiresCorrectToolForDrops()` and lower strength so it breaks by hand (or with any
  tool) reasonably fast, while keeping high blast resistance so it survives explosions. One-line
  behavior change.

### 4.3 — Cheaper shop-block recipe 📋 Planned
- **Files:** `src/main/resources/data/futureshops/recipes/shop_block.json`.
- **Plan:** replace the diamond/deepslate-tier recipe with cheap, early-game ingredients (e.g. planks
  + a chest + an emerald, or similar). Data-only.

### 4.4 — Custom floating shop icon ✅ Implemented (fully end-to-end)
Choose between: rotating listed items (current), owner's head, or a custom item icon. (Uploaded
images are out of scope — no way to ship arbitrary textures safely.)
- **Done:** `ShopBlockEntity.FloatingIconMode` (CYCLE | OWNER_HEAD | CUSTOM_ITEM) + `floatingIconItem`,
  synced on the block-top update tag (protocol 27) and resolved into display stacks in `handleUpdateTag`
  (owner-head builds a `player_head` stack; custom builds the item; invalid/blank falls back to CYCLE) so
  `ShopBlockGeoRenderer` needs no change. Owner-only `C2SPlayerShopIconPacket` → `applyFloatingIcon`.
  **Owner selector** lands in the redesign 5b Storefront sub-tab.

### 4.5 — Server-wide purchase history 📋 Planned
Today history is per-player; admins want the full server ledger.
- **Files:** `TransactionHistorySavedData` (already stores every entry) + a new admin query/screen;
  `/shopadmin history [player|shop|all]` or an admin history view.
- **Plan:** add an admin-only aggregated view over the existing `TransactionHistoryEntry` records
  (filter by player/shop/time, paged) — the data already exists, this is a read/query + screen.
  Gate on `hasPermissions(2)`.

### 4.6 — Tags + customer-side sorting/filtering 📋 Planned (larger)
Multiple tags per item (Iron/Gold/Diamond; Pickaxe/Axe/Shovel; Enchantment; price bands) and a
customer filter/sort.
- **Files:** listing model gains a `tags` list (admin.json + `ShopBlockEntity.Listing`), wire
  (`CatalogItem` / `PlayerShopListingData`), and grid UI filter chips + sort control in
  `ShopMainScreen` / the local browser; admin/owner tag editor in the edit modal.
- **Plan:** phase 1 — data model + wire + a manual tag editor; phase 2 — a filter/sort bar in the
  grid (select tags, sort by price/stock/name). Also auto-suggest tags from item tags
  (`ItemTags`) and enchantments to reduce manual work. Sizeable but self-contained; good candidate to
  build after the quick wins.

---

## 5. New subsystems (need a scoping decision before planning)

These are each multi-day features with real design decisions. Listed with a feasibility read; none
will be built without an explicit go-ahead and a scoping pass.

### 5.1 — Request system 💡 Proposed
Player posts a "want to buy X"; owners bid a price; on the requester's confirmation, funds are
escrowed from their balance and paid to the first seller who fulfills. Feasible on top of the
existing balance ledger + a new `RequestSavedData` + notification flow. Medium-large.

### 5.2 — Task / gig system (inverted request) 💡 Proposed
Post "I'll do X work for Y money"; someone accepts, work is confirmed, payout released. Shares the
escrow + listing infrastructure with 5.1 — build them together. Medium-large.

### 5.3 — Auction house 💡 Proposed
Timed listings, bids, auto-settle to highest bidder at close. Needs an auction store + tick-driven
close scheduler (the mod already has a server-tick heartbeat) + a dedicated UI. Large.

### 5.4 — Loans 💡 Proposed
Borrow from server or player at a set interest rate, with repayment tracking. Server-loan is
straightforward on the balance ledger; player-to-player loans need trust/collateral rules —
a design call. Medium (server) / large (P2P).

### 5.5 — Donation box 💡 Proposed
A "free items for new players" block with cooldown, per-player and global take caps. A new block +
block entity, mostly independent of the shop system. Medium.

### 5.6 — Land purchase 💡 Proposed
Buy/claim chunks for money, priced by distance-to-spawn or premium zones — via FTB Chunks (or
another claims mod) integration. Depends on a specific claims mod's API; scope hinges on which one
the modpack uses. Medium once the target integration is chosen.

### 5.7 — Gamba 💡 Proposed
No real money — spend soft/extra currency on a wheel-style game (Rust-like), ideally the
player-interacting variants. Fun, self-contained; pick the simplest game form first. Small-medium.

---

## Suggested sequencing

1. **Deploy the v2.3 build** (fixes textures; ships the admin editor, item picker, i18n).
2. **Quick-wins batch (Planned, low-risk):** 1.4, 1.7, 1.8, 4.1, 4.2, 4.3, 1.3, 1.2 — mostly
   lang/layout/data changes.
3. **Medium batch (pick 2–3):** 1.5+1.6 (stock semantics), 1.1 (unify local browser to `/shop` look),
   3.1 (multiple storage), 3.3 (portable config), 4.6 (tags).
4. **One subsystem** from §5, chosen and scoped with you, when the above land.
