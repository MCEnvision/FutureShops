# OverStars Custom Shop Mod — Beta Feedback Tracker

> **Last updated:** 2026-04-13  
> **Status:** 37/37 items implemented ✅  
> **Spec reference:** [`shop-mod-complete-specification.md`](shop-mod-complete-specification.md)  
> **Audit ledger:** [`mod-status-audit.md`](mod-status-audit.md)

---

## Beta Feedback Items

### ✅ 1. Scrollbar / Arrows When Listings Go Off-Screen
**What:** Added reusable `ShopUiUtil.renderScrollIndicators()` rendering ▲/▼ arrows, a scrollbar track + thumb, and a page counter. Wired into the listing rail, department sidebar, and item grid.  
**How to verify:** Open any shop with more items than fit on-screen (>3 in manage, >4 in browse at GUI scale 3). Scroll with the mouse wheel — arrows and scrollbar should appear and track position. They auto-hide when content fits.

### ✅ 2. Franchise System
**What:** Full franchise grouping system via `FranchiseSavedData`. Commands: `/franchise create <name>`, `invite`, `accept`, `decline`, `kick`, `promote`, `manage`, `leave`, `disband`. Franchise members can manage each other's shops. Top-10 franchise leaderboard in `BalTopOverviewScreen`.  
**How to verify:** `/franchise create TestFranchise` → `/franchise invite <player>` → other player does `/franchise accept`. Both can now right-click each other's shop blocks and manage them. Leaderboard shows in `/baltop ui`.

### ✅ 3. /shop Loads Nearby Player Shops + Admin Shop Toggle
**What:** `NearbyShopScanner` finds player shops within 25 blocks. `AdminShopToggleSavedData` toggles the global admin shop. "📍 Nearby" tab in `ShopMainScreen` shows nearby shops sorted by distance. `/shopadmin adminshop toggle` enables/disables the admin storefront.  
**How to verify:** Place a player shop block near your position. Run `/shop` — the "📍 Nearby" tab should list it. `/shopadmin adminshop toggle` hides or shows the admin catalog items.

### ✅ 4. Refined Storage 2 Network Linking
**What:** `ExternalStorageAdapter` + `ExternalStorageRegistry` abstraction. `RefinedStorage2StorageAdapter` auto-detects RS2 blocks via mod namespace. Falls back to Forge `IItemHandler` if RS2 isn't present.  
**How to verify:** With RS2 installed, place a shop block, look at an RS2 storage block, run `/link`. Shop should list items from the RS2 network. Without RS2, linking works with chests/barrels as before.

### ✅ 5. Selling Max Past 64 Items
**What:** `MAX_SELL_QUANTITY` and `MAX_BUY_QUANTITY` increased to 2304 (36 slots × 64). Sell max is based on total inventory count. Quantity edit box max length increased to 4 digits. Overflow items drop at feet.  
**How to verify:** Hold 3 stacks of diamonds (192). Open shop → sell → click Max. Should show 192, not 64.

### ✅ 6. Advanced Tooltip Info (Enchants, NBT, Lore)
**What:** `ShopUiUtil.renderItemTooltip()` builds full `ItemStack` (with optional NBT) and renders vanilla tooltips. Hovering item cards, preview icons, and listing rail entries shows enchantments, lore, durability, etc.  
**How to verify:** List an enchanted diamond sword in a player shop. Open as visitor → hover the item icon. Full enchantment info should display.

### ✅ 7. Money or Barter Mode + Compound Mode
**What:** Four trade modes: `MONEY`, `BARTER`, `BOTH` (money-or-barter fallback), `MONEY_AND_BARTER` (both required). Mode cycles with toggle button. Compound mode requires both payment types simultaneously.  
**How to verify:** Toggle mode on a shop listing. Set barter item + money price. In `MONEY_AND_BARTER` mode, buyer must pay BOTH money and barter items. In `BOTH` mode, buyer can choose either.

### ✅ 8. NBT-Aware Listings (Fuel Tanks, Enchanted Gear)
**What:** `nbtAware` flag + `nbtTag` storage per listing. When enabled, the listing copies the held item's full NBT and matches it during buy/sell. Orange "NBT" badge shown in UI.  
**How to verify:** Hold a full fuel tank, create listing with `ADD_LISTING_MAINHAND`. Toggle NBT aware on. Listing should show the full tank icon and only match full tanks.

### ✅ 8a. Distinguish Items with Specific NBT
**What:** NBT-aware extraction/counting in all stock/buy/sell paths via `NbtMatchUtil.matches()`.  
**How to verify:** Same as #8 — listing a Sharpness V sword will only sell Sharpness V swords, not plain ones.

### ✅ 9. Marketplace Profile — Clickable Shop Cards
**What:** `BalanceOverviewScreen` shows owned shops with hover highlighting and click-to-visit. Clicking a shop card opens that shop's manage/visit view.  
**How to verify:** Open `/bal` dashboard. Hover over a shop card → see cyan border + "▶ Click to visit". Click opens the shop.

### ✅ 10. Instant Price Updates Without Checkmark
**What:** Debounced `priceEditTimestamp` — price and barter count update automatically after 600ms idle or when the edit box loses focus. Enter key also applies immediately.  
**How to verify:** Open shop owner UI → click price field → type a new value → click away or wait 0.6s. Price updates without pressing ✓.

### ✅ 11. Bundle Listings (Multi-Item Output)
**What:** `itemCount` (bundle size) per listing. `SET_BUNDLE_SIZE` action. Delivery multiplied by bundle size. Stock shown as bundles available.  
**How to verify:** Create a listing, set bundle size to 6 via owner UI. Visitor buys 1 unit → receives 6 items. Price is per-bundle.

### ✅ 12. Settlement History with Item Info + Photo
**What:** `SettlementHistoryScreen` renders item icons, display names, quantities, and timestamps per row. Hover shows full tooltip.  
**How to verify:** Sell items through a player shop. Open owner settlement history. Rows show item icon + name + "x3" quantity.

### ✅ 13. Barter Department Defaults to Barter Page
**What:** Single-listing barter-mode shops auto-redirect visitor to `PlayerShopBarterScreen` on open.  
**How to verify:** Set a player shop to barter-only with one listing. Visitor opens → goes straight to barter confirmation screen.

### ✅ 14. CarryOn Mod Compatibility
**What:** `ShopBlock.setPlacedBy()` now always re-registers at new position if owner exists. CarryOn pickup/re-place preserves NBT + re-registers in scanner.  
**How to verify:** Pick up a shop with CarryOn mod → place it elsewhere. `/shop` nearby tab should show it at new position.

### ✅ 15. Tag-Based Department Classification (Deprecated → Admin Categories)
**What:** `TagDepartmentClassifier` (auto-classification by forge tags) was implemented then deprecated in favor of admin-controlled categories. `/shopadmin category add/remove/assign/unassign` commands manage departments.  
**How to verify:** `/shopadmin category add Weapons` → `/shopadmin category assign minecraft:diamond_sword Weapons`. Item appears under "Weapons" department in storefront.

### ✅ 16. Barter Overflow Dupe Prevention
**What:** Pre-validates linked storage capacity before executing barter. Atomic transaction flow — either everything succeeds or everything rolls back.  
**How to verify:** Fill a shop's linked storage to near-capacity. Attempt a large barter trade. Should fail cleanly with "storage full" message instead of partially completing.

### ✅ 17. Overflow Detection on Linked Storage
**What:** `canInsertAll()` / `canInsert()` pre-checks before insertion. Transaction cancelled if storage can't hold output.  
**How to verify:** Same as #16 — trade is rejected before items move.

### ✅ 18. Close UI + Chat Message on Failed Barter
**What:** `sendResultWithChat()` sends S2CForceClosePacket + chat explanation on failure. Player sees colored chat message explaining what went wrong.  
**How to verify:** Trigger a failed barter (storage full, missing items). UI closes and chat shows "§cTrade cancelled: <reason>".

### ✅ 19. Single-Item Mode Hides (Not Deletes) Listings
**What:** `visibleListingIndex` hides excess listings instead of deleting. Toggling back to multi-item reactivates them.  
**How to verify:** Add 3 listings. Toggle to single-item mode → only 1 visible. Toggle back to multi → all 3 return.

### ✅ 20. Select Visible Listing in Single-Item Mode
**What:** `SELECT_VISIBLE_LISTING` action + `setVisibleListingIndex()`. Owner chooses which listing to show.  
**How to verify:** In single-item mode, click different listings to select which one visitors see.

### ✅ 21. UI Scale 4 Overlap Fix
**What:** Responsive layout with `Math.min/max` sizing ensures elements don't overlap at high GUI scales.  
**How to verify:** Set GUI scale to 4. Open shop UIs — all elements should remain readable and non-overlapping.

### ✅ 22. UI Spacing at Different Scales
**What:** Adaptive `guiW`/`guiH` calculations and compact button modes at small panel sizes.  
**How to verify:** Test at GUI scales 1, 2, 3, 4. Layouts should adapt proportionally.

### ✅ 23. Toggle NBT Detection Per Listing
**What:** `TOGGLE_NBT_AWARE` action. Orange "NBT" badge in listing rail when enabled.  
**How to verify:** Open owner UI → toggle NBT on a listing. Badge appears. Only items matching exact NBT will transact.

### ✅ 24. Barter Discount Math (Round Up)
**What:** `effectiveBarterItemCount()` uses `Math.ceil()` for fractional discount results. 50% off 3 items = 2 (rounds up from 1.5).  
**How to verify:** Set 50% discount on a barter listing requiring 3 items. Visitor should see 2 required (ceil of 1.5).

### ✅ 25. Buyer-Friendly Status Messages
**What:** `buyerFriendlyMessage()` translates internal codes to player-friendly text. Technical status codes hidden from buyers.  
**How to verify:** Attempt to buy with insufficient funds. See "Not enough money" instead of "INSUFFICIENT_FUNDS".

### ✅ 26. "Collect $" Button Rename
**What:** Claim button label changed to `§aCollect $`.  
**How to verify:** Open owner shop UI — settlement button reads "Collect $" in green.

### ✅ 27. Blue "Set" Button + Multi-Item Barter
**What:** `§9Set` blue coloring for the set button. Multiple barter ingredients supported.  
**How to verify:** Owner UI shows blue "Set" button. Barter recipes can require multiple different items.

### ✅ 28. Blue "⚒ Barter" Buttons
**What:** `§9⚒ Barter` coloring in both listing rail and detail panel.  
**How to verify:** Open any barter listing — button text is blue.

### ✅ 29. MONEY_AND_BARTER Consistent Purple
**What:** Purple accent color in both listing rail and detail panel for compound mode.  
**How to verify:** Set a listing to MONEY_AND_BARTER. Both rail card and detail panel show purple "Money + Barter".

### ✅ 30. BOTH Mode Individual Colors (Money or Barter)
**What:** `§aMoney §7or §9Barter` — green for money, gray for "or", blue for barter.  
**How to verify:** Set listing to BOTH mode. Label shows "Money or Barter" with correct individual colors.

### ✅ 31. Fix Barter Chest Linking
**What:** `resolveBarterStorage()` with separate barter storage position. Independent barter-link pending map.  
**How to verify:** Link main storage → then link barter storage separately. Both function independently. Barter payments go to barter chest.

### ✅ 32. Base Quantity Per Listing (6 Sticks for $1)
**What:** `baseQuantity` field + `SET_BASE_QTY` action. Buyer receives `baseQuantity × purchaseQty` items.  
**How to verify:** Set base quantity to 6 on a stick listing priced at $1. Buyer buys 1 → gets 6 sticks.

### ✅ 33. Fix Browse Shop UI Total Cost Display
**What:** Visitor detail view shows `Total: $X.XX` with quantity controls. Cart and buy paths use correct totals.  
**How to verify:** Open a shop as visitor. Adjust quantity → total updates. Buy → correct amount deducted.

### ✅ 34. Player Shop Cart (Cleared on Logout)
**What:** `PlayerShopCartState` (client-side, cleared on disconnect) + `PlayerShopCartScreen`.  
**How to verify:** Add items to cart → view cart screen. Log out and back in → cart is empty.

### ✅ 35. Add to Cart Feature for Player Shops
**What:** `§e+ Cart` button in visitor detail. `addToCart()` accumulates items across shops.  
**How to verify:** Open player shop → click "+ Cart" on an item → open cart → item listed.

### ✅ 36. Promo Type as Cycling Button
**What:** `PromoEditorModalScreen` uses a cycling button instead of a text field for promo type selection.  
**How to verify:** Open promo editor → click type button → cycles through PERCENTAGE / FLAT / BUY_X_GET_Y / FLASH.

### ✅ 37. Promo Editor Conditional Field Visibility
**What:** `updateFieldVisibility()` hides irrelevant fields based on promo type. Percentage/flat hides BuyX/GetY fields; BuyX/GetY hides value field.  
**How to verify:** Select PERCENTAGE → only value field visible. Select BUY_X_GET_Y → only BuyX and GetY fields visible.

---

## Additional Systems Beyond Beta Items

| System | Status | Description |
|---|---|---|
| Admin catalog categories | ✅ Done | `/shopadmin category add/remove/list/assign/unassign/items` — server-op controlled departments |
| Custom player-shop departments | ✅ Done | `DepartmentSavedData` + department picker screen with search |
| Dynamic Pricing (spec §30) | ✅ Done | Demand/supply formula adjusts prices on configurable tick interval |
| Stock Refresh Scheduler (spec §31) | ✅ Done | Items with `stockRefreshSeconds > 0` auto-restock on timer; persists across restarts |
| Persistence migration / schema versioning | ✅ Done | `SavedDataMigrations` v1 on all 11 SavedData classes |
| Developer API & Event Hooks (spec §33) | ✅ Done | 8 custom events + `ShopModAPI` facade; BalanceChangeEvent now carries context-aware reason strings |
| Reason-aware BalanceChangeEvent | ✅ Done | `withdraw(uuid, amount, reason)` / `deposit(uuid, amount, reason)` — reasons: BUY, SELL, TRANSFER, WITHDRAW, DEPOSIT |
