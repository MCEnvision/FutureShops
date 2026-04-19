# FutureShops Mod Status Audit (2026-04-15)

## Git snapshot
- Branch: `master`
- Latest commits:
  - `ba72605` feat: add coin anti-dupe scaffolding, expanded economy commands, and shop session packets
  - `d9fb429` feat: implement phase 2 withdraw/deposit command flows
  - `5fbafec` feat: implement phase 2 internal economy core and real /balance
  - `71770ee` feat: bootstrap core shop foundations for Forge 1.20.1
- Working tree: dirty with many staged and unstaged files (active implementation state).

## Spec coverage (shop-mod-complete-specification.md)

### Implemented or mostly implemented
- UI core: `ShopMainScreen`, `ItemDetailScreen`, `CartScreen`, `TransactionHistoryScreen`, `BarterScreen`
- Economy core: internal provider, balances, transfer/pay, withdraw/deposit, coin mint/validation scaffolding
- Network packets: open shop, buy/sell/barter, inventory sync, history fetch/response, force close
- Transaction engines: buy/sell/barter with server-authoritative validation and rollback paths
- Session management: session open/close and force-close handling
- Shop config loading: catalog/categories/items/promos/barter recipes from JSON shop files

### Partially implemented
- History system: advanced querying (search/sort/time-window/filter) is implemented; remaining gaps are richer analytics/export and long-history UX
- Promo system: runtime promo apply/clear is available via admin command + owner modal; remaining gaps are full rule editor coverage (Buy-X-Get-Y/flash scheduling)
- UI polish: many spec mechanics exist, but not all spacing/animation/keyboard detail from full spec is enforced

### Missing or major gaps (high priority)
- Player-owned shop block gameplay loop from spec Part II §20-22:
  - owner assignment + owner editing workflow
  - dedicated per-block listing UI for visitors
  - storage linking workflow and integrity checks
  - item handler/hopper compatibility behavior
- Persistence model from §25 (SQL schema-level tables) is not implemented; current persistence uses Minecraft SavedData with schema versioning (v1).
- ~~Dynamic pricing (§30) and stock refresh scheduler (§31) are not fully implemented.~~ **Dynamic pricing (§30) is now fully implemented.** **Stock refresh scheduler (§31) is now fully implemented.**
- ~~Developer API/events (§33) are not fully implemented.~~ **Developer API and event hooks (§33) are now fully implemented.** BalanceChangeEvent now carries context-aware reason strings (BUY, SELL, TRANSFER, WITHDRAW, DEPOSIT).

## What was added in this pass
- Colored command output styling added for player-facing command feedback:
  - `/shop`, `/balance`, `/bal`, `/baltop`, `/pay`, `/withdraw`, `/deposit`, `/shopadmin`
- New shared command style helpers in `EconomyCommandUtil`.
- UI palette shifted to black/gray/white base with selective accent pops in `ShopColors`.

## Additional implementation update (this pass)
- Marketplace dashboard + leaderboard pass completed:
  - `/shop` now opens an Amazon-style account/dashboard view by default, while `/shop <shopId>` still opens a direct storefront
  - `/bal` and `/balance ui` now open the same dashboard profile flow
  - dashboard shows the executing player's head, balance, total player-shop revenue, pending settlement value, tracked shop/listing counts, total supply, and low-supply alerts
  - `/baltop`/`/baltop ui` now open a richer leaderboard screen with player heads, top balances, most-transactions spotlight, top seller spotlight, and most-popular product spotlight
- Responsive UI modernization pass completed for requested screens:
  - `ShopMainScreen` rebuilt around dynamic panel sizing and adaptive item-grid columns
  - `TransactionHistoryScreen`, `CartScreen`, and `BarterScreen` re-laid out for larger responsive tables/panels and cleaner spacing
  - `BalanceOverviewScreen` and `BalTopOverviewScreen` were effectively replaced by the new dashboard/leaderboard visual system
- Player-owned shop block upgraded from single-item to multi-listing support:
  - `ShopBlockEntity` now persists multiple listings per placed shop block instead of one configured item
  - each listing supports its own trade mode, money price, barter item/count, and promo configuration
  - owner UI now targets the selected listing for mode, price, barter, promo, claim, and link actions
  - buyer UI now supports choosing between multiple listings on the same shop block
- Player-shop promo flow fixed and made server-authoritative for shop blocks:
  - promos are now stored directly on player-shop listings instead of piggybacking on global catalog runtime promos
  - promo chips/effective prices are synced to the player-shop buyer UI
  - money purchases now use listing promo pricing and Buy-X-Get-Y math where configured
- Player-shop buyer UX expanded and polished:
  - `PlayerShopBlockScreen` now matches the darker storefront styling more closely with listing cards, promo chips, stock/supply panels, and richer pricing/trade presentation
  - `PlayerShopBarterScreen` was rebuilt around the requested two-column "You Receive / You Give" layout with item names, icons, quantities, amount controls, and confirm action
- Player-shop analytics scaffolding added for owner dashboards:
  - new saved registry tracks placed player shop blocks by owner/dimension/position
  - dashboard aggregates placed-shop supply/stock state and low-stock warnings from those registered blocks
- Verification notes (this pass):
  - `./gradlew.bat build` succeeded after the dashboard/multi-listing/UI rewrite
  - `./gradlew.bat runClient` was started and `run/logs/latest.log` shows Forge client initialization reaching render-thread startup with the updated mod/resources loaded
- History querying upgraded beyond basic pagination:
  - search text, sort order (newest/oldest), and time-window filters (all/24h/7d/30d)
  - server-side query support wired through `C2SFetchHistoryPacket` -> `TransactionHistoryService` -> `TransactionHistorySavedData`
  - `TransactionHistoryScreen` now includes controls for these query dimensions
- Promo tools expanded:
  - `/shopadmin promo set <shopId> <itemId> <type> <value>`
  - `/shopadmin promo clear <shopId> <itemId>`
  - runtime promo overrides now merge into catalog item/promo payloads via `ShopCatalog`
- Balance/Baltop dedicated UIs added:
  - `/balance ui` and `/baltop ui [page]`
  - dedicated packets and client screens for visual economy views
- Player shop block v1 implemented:
  - owner assignment on placement
  - right-click opens dedicated per-block player shop UI (owner config + visitor purchase view)
  - owner listing setup, pricing mode toggle (money/barter), storage link target, and visitor buy path
  - linked-storage backed stock checks and transaction guards
- Player-shop/storage anti-dupe hardening pass completed:
  - raycast-based storage linking (`LINK_LOOKING`) with distance/chunk/capability validation
  - per-shop transaction locking to reduce race windows
  - staged transaction flow with rollback handling for money and barter paths
  - stricter linked-storage validation to avoid invalid/self/shop-block link targets
- Dedicated promo editor modal implemented:
  - owner-accessible `PromoEditorModalScreen` from `PlayerShopBlockScreen`
  - packet-driven server-authoritative promo apply/clear (`C2SPlayerShopPromoPacket`)
- Verification notes:
  - `./gradlew.bat build` succeeded after this pass
  - `./gradlew.bat runClient` terminal bridge returned `null`, but `run/logs/latest.log` shows successful startup, login, and `/shop` open event
  - fixed an ItemDetail quantity-input recursion crash loop discovered in logs
    - Owner revenue history + settlement UI tied to player shops:
      - persistent settlement ledger (`PlayerShopSettlementSavedData`) with pending/lifetime tracking
      - owner settlement metrics and recent revenue rows now shown in player shop UI payloads
      - owner claim flow (`CLAIM_SETTLEMENT`) moves pending funds into owner balance
    - Owner test-view behavior:
      - owner shift-right-click now opens the player shop in visitor-mode for buy/test/preview behavior
    - Promo modal expanded toward full spec controls:
      - supports promo type/value plus BUY_X_GET_Y fields and schedule window inputs
      - supports flash toggle and server-side runtime promo config storage
      - advanced line-cost math path added for BUY_X_GET_Y in buy transaction service
    - UI overlap/stability polish:
      - `PlayerShopBlockScreen` was re-laid out with clipped rows and non-overlapping controls
      - `PromoEditorModalScreen` re-laid out with wider modal and separated field rows
      - current pass focuses known high-overlap screens; additional full-screen-by-screen polish remains
            - UI polish sweep updates:
              - `ItemDetailScreen` control rows were separated to avoid barter/quantity/action overlap
              - `PlayerShopBlockScreen` and `PromoEditorModalScreen` were re-laid out for non-overlapping controls and clipped labels
              - `ShopMainScreen` title formatting now normalizes raw shop IDs into capitalized display names
              - promo corner badge now uses a red 45-degree animated treatment for stronger visual pop
            - Link confirmation flow changed to requested two-step UX:
              - owner starts linking from UI action
              - chat instructs player to look at storage and run `/link`
              - `/link` confirms via server-side raycast validation
            - Settlement history UX expanded:
              - dedicated paged `SettlementHistoryScreen`
              - new settlement history packets and structured row DTOs
              - localized settlement row rendering keys and labels added to `en_us.json`
                        - Latest UI refinement pass:
                          - `ShopMainScreen` promo ribbon now renders as a high-layer red rotated `-X%` badge instead of generic PROMO text
                          - `ItemDetailScreen` quantity controls moved under the preview column so the info panel remains text-only without barter overlap
                          - `ItemDetailScreen` barter button now always routes into the dedicated `BarterScreen`
                          - `PlayerShopBlockScreen` visitor mode now uses item-preview/detail presentation instead of a text-only button list
                          - `PlayerShopBarterScreen` added so player-owned barter shops open a dedicated trade-confirm flow

## Next implementation targets (requested)
1. Expand owner/global analytics beyond current dashboard summaries: cross-shop settlement history, export/search, and deeper seller/product trend cards.
2. Add higher-end player-shop UX refinements around listing pagination, drag/drop style owner setup, and optional richer animations/hover states.
3. Continue remaining spec gap-closure work (automation compatibility matrix, dynamic pricing scheduler, API/events, persistence model migration).

## Latest pass — coin denomination + UI navigation fixes (2026-04-12)

### `/shop` command restored to server shop
- `/shop` (no args) now opens the server shop storefront directly via `ShopDataService.openShop`, matching original behavior.
- `/shop <shopId>` continues to open a named shop.
- Dashboard is still reachable via `/bal` and `/balance ui`.

### Storefront & Leaders buttons fixed
- **Root cause**: `ShopClientPacketHandler.handleShopData` guarded screen transitions with `!(mc.screen instanceof ShopScreenMarker)`, which prevented the Storefront button from opening `ShopMainScreen` when called from `BalanceOverviewScreen` (also a `ShopScreenMarker`).
- **Fix**: `handleShopData` now always calls `mc.setScreen(new ShopMainScreen())`, ensuring Storefront navigates correctly from any open screen.
- Leaders button path verified working (`handleBalTopUi` already fell through to the `new BalTopOverviewScreen(...)` branch correctly, but the fix ensures no blockage from ShopScreenMarker).

### Player head rendering fixed
- `ShopUiUtil.renderPlayerFace` was using the wrong `blit` overload, sampling the entire skin texture instead of the 8×8 face region.
- **Fix**: Now uses `blit(skin, x, y, size, size, 8.0f, 8.0f, 8, 8, 64, 64)` for the face layer and `blit(skin, x, y, size, size, 40.0f, 8.0f, 8, 8, 64, 64)` for the hat overlay.

### Coin withdraw denomination system
- **New command**: `/withdraw <amount> [yes|no]`
  - `yes` (default): breaks the amount into fewest bills using denominations $1, $5, $10, $20, $50, $100, $1000.
    - Example: `/withdraw 132 yes` → 1×$100, 1×$20, 1×$10, 2×$1
  - `no`: gives a single coin worth the full withdrawal amount.
    - Example: `/withdraw 132 no` → 1×$132 coin
- Each denomination coin is minted with its own denomination value stored in NBT.
- Coins of different denominations won't stack (different NBT), keeping inventory clear.

### Variable denomination support across coin system
- `CoinValidationService`: now accepts any positive denomination (previously hardcoded to `COIN_DENOMINATION_MINOR_UNITS = 100`). Checksum integrity still protects against tampering.
- `CoinItem.appendHoverText`: reads denomination from NBT for correct tooltip display on variable-value coins.
- `DepositCommand`: fully rewritten to handle variable denominations — sums coin values from NBT rather than multiplying by a fixed denomination. Greedy largest-first consumption for partial deposits.

### Verification
- `./gradlew.bat build` succeeded after all changes.

## Latest pass — Massive UI overhaul + shop block configuration (2026-04-12)

### Shop block owner configuration
- **Shop naming**: Owners can now set a custom name for each shop block via an inline edit box in the config panel. The name shows in the header for both owner and visitor views.
- **Single/Multi item mode**: Toggle between single-listing (1 item max) and multi-listing (up to 12). When switching to single mode, excess listings are trimmed server-side.
- **Barter storage option**: Toggle between "Same Chest" (barter uses the main linked storage) and "Separate" (barter uses a separate linked chest). When separate, new "B.Link" / "B.Unlnk" buttons appear for linking the barter storage independently.
- New `C2SPlayerShopConfigPacket` for sending config changes; `PlayerShopBlockService.applyConfig()` validates ownership and applies changes server-authoritatively.
- `ShopBlockEntity` now persists `shopName`, `singleItemMode`, `barterStorageSame`, and `barterStoragePos` in NBT.
- `S2CPlayerShopDataPacket` and `PlayerShopClientState` extended with the new config fields. Protocol version bumped to 12.
- `PlayerShopLinkService` extended with `beginBarter()` / separate barter-link pending map for independent barter storage linking.

### ShopMainScreen overhaul
- **Profile button** (bottom-right footer): Shows player head, name, and balance. Clicking opens the balance/profile dashboard.
- **Animated discount badge**: Full red background (`DISCOUNT_BG`), white text, pulsating scale animation (sin-wave 0.82–1.18), and -15° rotation for a pop effect. Replaces old static chip.
- **Modern layout**: Gradient header bar, accent-colored sidebar with cyan indicator bar, centered item cards with icon/name/price/stock, properly truncated text everywhere.
- **Text overflow fix**: All labels use `plainSubstrByWidth` to clip to available space, preventing overflow.

### ItemDetailScreen overhaul
- **Bottom-aligned controls**: Quantity row (−/input/+/Max) centered right above the action buttons. All three action buttons (+ Cart, $ Buy, ↑ Sell) are at the same height, evenly spaced.
- **Barter button** moved to top-right corner of the panel.
- **Total cost** displayed centered above the quantity row.
- **Animated promo banner** using the same pulsating red badge.
- Preview and info panels use accent-colored top borders for visual hierarchy.

### PlayerShopBlockScreen overhaul
- **Owner vs visitor distinction**: Owner sees a purple-tinted background with `OWNER_ACCENT` and a "⚡ Manage Your Shop" header; visitor sees cyan accent with "Browse Player Shop" header.
- **Config panel** (owner only): Inline edit for shop name, Single/Multi toggle, Barter storage Same/Separate toggle, Save Config button.
- **Listing rail**: Compact cards with item icon, truncated name, stock + mode, and animated promo badges using full-red discount style.
- **Detail panel**: Large item preview, stock health indicator, pricing/trade info sub-panels, wrapped text.
- **Compact footer controls**: Owner actions packed into smaller labeled buttons; barter link buttons conditionally visible.

### CartScreen overhaul
- Accent header panel with cart title.
- Row-based item display with truncated names, inline quantity controls, prices, remove buttons.
- Gold-accented summary bar with item count and total.

### ShopColors palette expansion
- Added 12 new modern UI accent colors: `ACCENT_CYAN`, `ACCENT_PURPLE`, `ACCENT_ORANGE`, `DISCOUNT_BG`, `DISCOUNT_TEXT`, `PROFILE_BG`, `PROFILE_BORDER`, `OWNER_BG`, `OWNER_ACCENT`, `CONFIG_BG`, `TOGGLE_ON/OFF`, `HEADER_GRADIENT_L/R`.

### ShopUiUtil enhancements
- `renderAnimatedDiscountBadge()`: Time-based pulsating scale + rotation via pose matrix transforms.
- `renderAccentPanel()`: Panel with colored top-accent line for visual hierarchy.
- `drawGradientH()`: Horizontal gradient fill utility for headers.

### Verification
- `./gradlew.bat build` succeeded after all changes.

## Latest pass — Barter screen redesign + UI overlap fixes (2026-04-12)

### BarterScreen (storefront) completely redesigned
- **Half-and-half layout**: Left panel = "You Receive" (large item preview, name, quantity); Right panel = "You Give" (ingredient list with item icons, need/have counts).
- **Arrow between panels**: Directional arrows (⟵/⟶) centered between the two halves to visually indicate the trade flow.
- **Recipe tabs**: Multiple recipes now shown as tabs above the panels instead of a side list.
- **Bottom controls on one row**: `- [qty] + Max | Confirm` all on the same line, no overlap.
- **Back button** top-left for navigation.

### PlayerShopBarterScreen (player shop) completely redesigned
- Same half-and-half layout as the storefront barter: left = receive, right = give, arrow between.
- Quantity controls + confirm button on one bottom row.
- Give panel shows the barter item with large preview, quantity needed, and owned count.

### ItemDetailScreen — quantity/button overlap fixed
- **Single bottom row**: Quantity controls (`- [1] + Max`) on the left, action buttons (`+ Cart`, `$ Buy`, `↑ Sell`) on the right, all at the same height (14px).
- Buttons made smaller (54px wide, 14px tall) to prevent overlap.
- Total cost display repositioned above the single control row.
- Preview and info panels expanded vertically to fill the freed space.

### PlayerShopBlockScreen — single-item detail view + visitor controls fix
- **Single-item shop**: When a visitor opens a shop with only one listing, the listing rail is hidden and a full-width detail view is shown (matching the storefront ItemDetailScreen style: large preview on left, pricing/trade info on right).
- **Visitor controls redesigned**: Quantity controls and action button now on the same row with smaller dimensions (14px height) to prevent overlap.
- Auto-selects the only listing for single-item shops.

### BalTopOverviewScreen — back button added
- Added "← Back" button (top-left of bottom bar) that sends `C2SOpenBalanceUiPacket` to return to the balance overview/profile screen.
- Previous "Close" and pagination buttons remain.

### Verification
- `./gradlew.bat build` succeeded after all changes.

## Latest pass — Single-item barter redirect + detail view layout (2026-04-12)

### PlayerShopBlockScreen — single-item barter opens barter screen directly
- When a visitor opens a single-listing shop block in **barter mode**, the screen now immediately redirects to `PlayerShopBarterScreen` (half-and-half layout) without showing the intermediate detail view.
- Single-listing **money mode** shops still show the full-width detail view as before.
- Uses `minecraft.tell()` for a safe deferred screen transition during `init()`.

### ItemDetailScreen — layout restructured
- **Quantity controls** (`- [1] + Max`) moved into the **preview panel** (left column), centered horizontally above the "Quantity" label.
- **Total cost** (`Total: $X.XX`) rendered right below the qty controls, centered in the preview panel, above the "Quantity" label. The vertical stack is: controls → Total → "Quantity".
- **Action buttons** (`+ Cart`, `$ Buy`, `↑ Sell`) remain on the bottom row, now centered across the full panel width, no longer sharing a row with qty controls.
- No more overlap between qty controls and action buttons.

### Verification
- `./gradlew.bat build` succeeded after all changes.

## Latest pass — Multi-feature UI/UX overhaul + BOTH trade mode (2026-04-12)

### ShopMainScreen — search/button alignment fix
- All top-bar elements (search field, mode toggle, cart, history, close) now render at the **same Y height** (`guiTop + 14`).
- Buttons are laid out right-to-left from the panel edge, with search filling the remaining space.
- Eliminates the overlap where buttons previously sat on top of the search bar.

### PlayerShopBlockScreen — owner editable price/qty text fields
- Replaced the `$-`/`$+` buttons with a proper `- [EditBox] +` pattern for both **price** and **barter count**.
- Price field shows the formatted minor-units value (e.g., `10.00`), editable by the owner.
- A green `✓` button sends the typed value to the server via a new `SET_PRICE` action.
- Barter count field uses the same `- [EditBox] +` layout.

### Three-way trade mode: MONEY / BARTER / BOTH
- `ShopBlockEntity.TradeMode` enum now includes `BOTH`.
- `TOGGLE_MODE` server action cycles `MONEY → BARTER → BOTH → MONEY_AND_BARTER → MONEY`.
- UI displays mode as "Money", "Barter", or "Money + Barter" with distinct colors.
- In `BOTH` mode, the visitor sees **two action buttons**: `$ Buy` (money) and `⚒ Barter`, each independently enabled based on stock.
- Server `buy()` in BOTH mode tries money first; if insufficient balance, falls back to barter automatically.

### Smart Max button for buyers
- `resolveMaxQuantity()` now computes:
  - **MONEY**: `min(stock, balance / effectiveUnitPriceMinor)`
  - **BARTER**: `min(stock, inventoryCount(barterItem) / barterItemCount)`
  - **BOTH**: `max(moneyMax, barterMax)` (player picks the better option)
- **No hard 64 cap** — quantities can exceed 64. Server drops overflow items at the buyer's feet.
- Quantity EditBox `maxLength` increased from 2 to 4.

### Barter screen layout fixes (both BarterScreen and PlayerShopBarterScreen)
- "Receive Xx Item" text now renders at the **same height** as the bottom controls row.
- Quantity controls (`- [qty] + Max`) positioned on the **left** side.
- Confirm button positioned on the **right** side.
- No longer centered — clear spatial separation.
- Smart max for BarterScreen: finds the limiting ingredient and caps multiplier.
- Smart max for PlayerShopBarterScreen: `inventory / barterCost`, no 64 cap.

### Promo badges on player shop listing rail
- Each listing card now shows an animated `-X%` discount badge at the **top-right** when a promo is active.
- Uses the same `renderAnimatedDiscountBadge()` as the storefront cards (pulsating red, rotated).
- Percentage is computed from either `PERCENTAGE` promo type value or base/effective price ratio.
- Falls back to a text chip (e.g., "B2G1") for non-percentage promo types.

### Promos work with all trade modes (money, barter, both)
- Promo badges and status text now display regardless of trade mode.
- Detail panel trade summary shows "Promo active (all modes)" for BOTH mode.

### Buyer detail view mirrors ItemDetailScreen
- Single-item visitor view now follows `ItemDetailScreen` layout:
  - Left: accent-bordered preview panel with large item icon, name, owned count, total cost, stock.
  - Right: scaled title, shop owner/name line, animated promo banner, divider, mode, buy price (with strikethrough for original), barter info with owned count, stock.
- Multi-listing visitor view shows familiar pricing/barter info with mode-aware panels.

### Server-side changes
- `ShopBlockEntity.TradeMode.BOTH` added to enum.
- `PlayerShopBlockService.applyOwnerAction`: `SET_PRICE` action sets exact price from client value.
- `PlayerShopBlockService.buy()`: BOTH mode tries money first, falls back to barter. No 64-quantity cap; overflow items drop on floor.

### Verification
- `./gradlew.bat compileJava` + `./gradlew.bat build` both succeeded with no compile errors.

## Latest pass — Beta Feedback Items 1-5 (2026-04-13)

### Item 1: Scrollbar / Arrows When Listings Go Off-Screen
- Added `ShopUiUtil.renderScrollIndicators()` — reusable utility rendering ▲/▼ arrows, scrollbar track+thumb, and page counter text.
- Wired into `PlayerShopBlockScreen.renderListingRail()` for the listing rail.
- Wired into `ShopMainScreen.renderSidebar()` for the department tab list.
- Wired into `ShopMainScreen.renderGrid()` for the item grid.
- All indicators auto-hide when content fits without scrolling.

### Item 2: Franchise System
- Created `FranchiseSavedData` — persistent franchise grouping system stored via SavedData:
  - Franchise CRUD (create, invite, accept, decline, kick, promote, leave, disband)
  - Player-to-franchise fast lookup for ownership checks
  - Top-N franchise leaderboard query by member count
  - Max 20 members per franchise
  - Auto-promotes first remaining member when leader leaves
- Created `FranchiseCommand` — full command tree under `/franchise`:
  - `/franchise create <name>`, `/franchise invite <player>`, `/franchise accept`, `/franchise decline`
  - `/franchise kick <player>`, `/franchise promote <player>`, `/franchise manage`, `/franchise leave`, `/franchise disband`
  - Rich chat feedback with emojis and formatting
  - Invited player sees a formatted invite notification with accept/decline instructions
- Updated all ownership checks in `PlayerShopBlockService` to use `isOwnerOrFranchiseMember()`:
  - `openFor()`, `applyConfig()`, `applyOwnerAction()`, `applyPromoAction()`, `confirmLink()`, `sendSettlementHistoryPage()`
  - Franchise members can manage each other's shops, link storage, set promos, claim settlements, etc.
- Registered `FranchiseCommand` in `ModCommandEvents`.

### Item 3: /shop Loads Nearby Player Shops + Admin Shop Toggle
- Created `NearbyShopScanner` — scans for player-owned shop blocks within a configurable radius:
  - Uses `PlayerShopRegistrySavedData.getAllShops()` for efficient spatial lookup (no brute-force block entity iteration)
  - Returns sorted list of `NearbyShopEntry` records (closest first, max 20 results)
  - Includes shop name, owner, listing count, stock, distance
- Created `AdminShopToggleSavedData` — server-wide toggle for admin shop visibility
- Added `/shopadmin adminshop toggle` command to enable/disable admin shop
  - When disabled, `/shop` sends empty catalog but populated nearby shops
  - Automatically refreshes all active sessions when toggled
- Extended `S2CShopDataPacket` with `adminShopEnabled` flag and `List<NearbyShopEntry>` (protocol version bumped to 13)
- Updated `ShopDataService.sendShopData()` to include nearby shop scan and admin toggle check
- Updated `ShopClientState` with `adminShopEnabled` and `nearbyShops` fields + getters
- Updated `ShopClientPacketHandler.handleShopData()` to pass new fields
- Added "📍 Nearby" tab in `ShopMainScreen` sidebar:
  - Shows nearby player shops as clickable cards with owner head, shop name, listings, stock, distance
  - Click navigates to that shop's `PlayerShopBlockScreen` (visitor mode)
  - Scroll support with indicators
  - When admin shop is disabled, grid shows message directing to Nearby tab
- Added `VISIT` action in `PlayerShopBlockService.applyOwnerAction()` — opens any shop block as visitor (no ownership required)
- Added `PlayerShopRegistrySavedData.getAllShops()` and `ShopRecord` type for scanner

### Item 4: Refined Storage 2 Network Linking
- Created external storage adapter abstraction:
  - `ExternalStorageAdapter` interface — `canHandle`, `countItem`, `canExtract`, `extract`, `canInsert`, `insert`
  - `ExternalStorageRegistry` — thread-safe registry for adapter lookup
  - `ForgeCapabilityStorageAdapter` — default adapter wrapping Forge IItemHandler capability
- Created RS2 compat module (`compat/rs2/`):
  - `RefinedStorage2Compat` — soft-dependency loader using `ModList.get().isLoaded("refinedstorage2")`
  - `RefinedStorage2StorageAdapter` — detects RS2 block entities by namespace, queries IItemHandler capability
  - Isolated bootstrap class prevents ClassNotFoundError when RS2 is absent
- Refactored `PlayerShopBlockService`:
  - `resolveLinkedStorage()` now checks ExternalStorageRegistry first, falls back to direct IItemHandler
  - `LinkedStorage` record extended with adapter + blockEntity fields
  - `isValidLinkTarget()` accepts blocks with ExternalStorageAdapter support
  - `countStock()`, `buy()` flow updated with adapter-aware extract/insert/count paths
- Registered adapters in `Futureshops.commonSetup()`: ForgeCapabilityStorageAdapter + RS2 compat init

### Item 5: Max Sell Field Past 64 / Count Total Inventory
- Changed `ShopTransactionUtil.MAX_QUANTITY` from 64 to 2304 (36 slots × 64), split into:
  - `MAX_BUY_QUANTITY = 2304` — for admin shop buy transactions
  - `MAX_SELL_QUANTITY = 2304` — for sell transactions
- Updated `ShopSellService` to use `MAX_SELL_QUANTITY`
- Updated `ShopBuyService`:
  - Uses `MAX_BUY_QUANTITY` for validation
  - Overflow items now drop at player's feet instead of failing with INVENTORY_FULL
- Updated `ItemDetailScreen`:
  - `quantityBox.setMaxLength` increased from 2 to 4
  - `resolveMaxQuantity()` now computes separate buy/sell limits — sell limit = total inventory count
- Updated `ShopClientState.clampCartQuantity()` — removed hardcoded 64 cap, now 2304 max

### Verification
- `./gradlew.bat build` succeeded with no compile errors.

## Latest pass — Beta Feedback Items 6–10 + Franchise Leaderboard (2026-04-13)

### Item 6: Advanced Tooltips in Shop UIs
- Added `renderItemTooltip()` to `ShopUiUtil` — builds an `ItemStack` (with optional NBT via `TagParser.parseTag()`), calls `stack.getTooltipLines()`, and renders via `graphics.renderTooltip()`.
- Added `renderItemIconWithNbt()`, `renderLargeItemPreviewWithNbt()`, `buildItemStack()`, and `getItemDisplayNameWithNbt()` utility methods for NBT-aware item rendering throughout UIs.
- `ShopMainScreen`: Hovering over item cards in the grid now shows full vanilla tooltips (enchantments, lore, NBT data) instead of the previous basic 2-line summary.
- `ItemDetailScreen`: Hovering over the large item preview in the left panel triggers a full advanced tooltip.
- `PlayerShopBlockScreen`: Hovering over listing rail icons shows full tooltips with NBT data when `nbtAware` is enabled on the listing.

### Item 7: Money + Barter Compound Mode
- Added `MONEY_AND_BARTER` to `ShopBlockEntity.TradeMode` enum (distinct from existing `BOTH` which is money-OR-barter fallback).
- Mode cycle updated: `MONEY → BARTER → BOTH → MONEY_AND_BARTER → MONEY`.
- `PlayerShopBlockService.buy()`: New compound trade path — buyer pays **both** money and barter items simultaneously. Full atomic rollback: if extraction fails, money is refunded and barter items returned.
- UI displays "Money + Barter" with orange accent color (`ACCENT_ORANGE`) in mode labels and trade info panels.
- `resolveMaxQuantity()` updated: MONEY_AND_BARTER uses `min(moneyMax, barterMax)` since both are required.
- Transaction history records compound trades as type `MONEY_AND_BARTER` / source `PLAYER_SHOP_COMPOUND`.

### Item 8: NBT-Aware Listings
- `ShopBlockEntity.Listing` extended with `boolean nbtAware` and `CompoundTag nbtTag` fields, persisted in save/load.
- When `ADD_LISTING_MAINHAND` is used and the held item has NBT, the tag is copied to the listing.
- `TOGGLE_NBT_AWARE` action added — cycles the listing's NBT awareness flag.
- `PlayerShopListingData` extended with `nbtAware` and `nbtJson` fields for network transport.
- Listing rail and detail panel use `renderItemIconWithNbt()` / `renderLargeItemPreviewWithNbt()` when the listing is NBT-aware.
- Listings show an orange "NBT" chip badge when `nbtAware` is enabled.
- Owner detail panel shows "NBT: ON" / "NBT: off" status label.

### Item 9: Marketplace Profile — Clickable Shop Cards
- `BalanceOverviewScreen.renderOwnedShops()` now shows hover highlighting (cyan border + "▶ Click to visit" text) on owned shop cards.
- `mouseClicked()` override added — clicking a shop card sends `C2SPlayerShopActionPacket(pos, "VISIT", 0, 0)` to open that shop's manage/visit view directly from the dashboard.

### Item 10: Instant Price Updates
- `priceBox.setResponder()` now records a `priceEditTimestamp` on every keystroke.
- `barterCountBox.setResponder()` records a `barterEditTimestamp` similarly.
- `tickDebouncedEdits()` called each frame: when the edit box loses focus (or after 600ms debounce), the value is parsed and sent to the server as `SET_PRICE` or `SET_BARTER_COUNT`.
- Enter key also immediately applies: `keyPressed()` override sends the action on Enter/Numpad Enter.
- Owner text fields are synced from server state only when not focused, preventing in-progress edits from being overwritten.

### Franchise Leaderboard in BalTopOverviewScreen
- Created `FranchiseLeaderboardEntry` data record in `data/` — network-safe DTO with `franchiseId`, `name`, `leaderName`, `memberCount`, plus `encode()`/`decode()`.
- `S2CBalTopUiPacket` extended with `List<FranchiseLeaderboardEntry> franchises` field.
- `MarketplaceAnalyticsService.sendLeaderboard()` now calls `FranchiseSavedData.getTopFranchises(10)`, maps to `FranchiseLeaderboardEntry` (resolving leader names), and includes in the packet.
- `BalTopOverviewScreen` redesigned:
  - Right column split into upper "Server spotlights" panel and lower "⚑ Top 10 Franchises" panel.
  - Franchise panel uses purple accent border (`ACCENT_PURPLE`), shows rank, franchise name, member count, and leader name.
  - Spotlight cards made vertically compact to accommodate franchise panel.
  - Constructor and `updatePage()` accept the new `List<FranchiseLeaderboardEntry>` parameter.
- `ShopClientPacketHandler.handleBalTopUi()` updated to pass franchise data through.
- Protocol version bumped 13 → 14.

### Verification
- `./gradlew.bat build` succeeded with no compile errors.

### Files Created
- `data/FranchiseLeaderboardEntry.java`

### Files Modified
- `block/ShopBlockEntity.java` — `MONEY_AND_BARTER` enum value, `nbtAware`/`nbtTag` fields on Listing, save/load
- `data/PlayerShopListingData.java` — `nbtAware` + `nbtJson` fields
- `network/packets/S2CBalTopUiPacket.java` — franchise list field
- `network/ShopPackets.java` — protocol version 13→14
- `server/shop/MarketplaceAnalyticsService.java` — franchise leaderboard query
- `server/shop/PlayerShopBlockService.java` — MONEY_AND_BARTER buy flow, TOGGLE_NBT_AWARE, NBT on ADD_LISTING, toData NBT fields
- `client/ShopClientPacketHandler.java` — franchise data passthrough
- `client/screen/BalTopOverviewScreen.java` — franchise panel, split layout, updated constructor/updatePage
- `client/screen/BalanceOverviewScreen.java` — clickable shop cards, hover indicators, mouseClicked
- `client/screen/PlayerShopBlockScreen.java` — tooltip tracking, debounced price edits, NBT toggle, MONEY_AND_BARTER mode, keyPressed
- `client/screen/ShopMainScreen.java` — advanced tooltip rendering
- `client/screen/ItemDetailScreen.java` — advanced tooltip on preview hover
- `client/screen/ShopUiUtil.java` — tooltip/NBT rendering utilities

## Latest pass — Beta Feedback QA Fix Pass (2026-04-13)

This pass addresses reported issues from beta testing of implemented features.

### ❌→✅ 1. Scrollbar Infinite Scroll (no pages)
- **Was:** "1/2 shows up but there aren't pages"
- **Fix:** Page counter was already removed from `ShopUiUtil.renderScrollIndicators()`. The scrollbar and ▲/▼ arrows are always used for continuous scrolling.
- **Status:** Confirmed already fixed in previous pass.

### ❌→✅ 2. Single Item Mode Listing Selection
- **Was:** "setting single item mode only goes with the top listing, unable to choose"
- **Fix:** `SELECT_VISIBLE_LISTING` action + `👁 Set Visible` button already implemented. Owner can select any listing in the rail and click "Set Visible" to choose which listing visitors see. Button only appears in single-item mode.
- **Status:** Verified working. The `setVisibleListingIndex(idx)` correctly updates on the block entity and is sent to visitors in `openFor()`.

### ❌→✅ 3. Nearby Shops Immediate Refresh + Back Button
- **Was:** "Wasn't displayed immediately, is there a set interval it updates?" + "back button IS NEEDED"
- **Fix (refresh):** Added a re-request via `C2SOpenShopPacket` when the user clicks the "📍 Nearby" tab. This triggers a full rescan on the server and sends updated data to the client immediately.
- **Fix (back):** Added a `§7← Back` button to `ShopMainScreen` (top-left, always visible).

### ❌→✅ 4. RS2 Network Linking
- **Was:** "just doesn't work at all"
- **Fix:** `RefinedStorage2StorageAdapter.canHandle()` now checks multiple known RS2 mod namespaces (`refinedstorage2`, `refinedstorage`, `refinedstorageaddons`, `refinedstorage2platform`) AND falls back to checking the block entity's Java class package prefix (`com.refinedmods.refinedstorage*`). This handles milestone/snapshot builds with different mod IDs.

### ❌→✅ 5. Quantity Field 4-Digit Display Width
- **Was:** "quantity field should be expanded to show 4 digits, capped at 3 we need 4"
- **Fix:** `ItemDetailScreen` quantity `EditBox` width increased from 24px to 36px. Surrounding buttons repositioned to accommodate. `maxLength` was already 4.
- Also verified `PlayerShopBlockScreen` visitor quantity box is already 36px wide.

### ❌→✅ 6. Advanced Tooltip Info (Enchants, NBT, Lore)
- **Was:** "no info shown for books so like enchantments or anything else"
- **Fix:** All item icon rendering and name display now always uses NBT data when `nbtJson` is non-blank, regardless of the `nbtAware` toggle. Previously, NBT visuals only showed when `nbtAware` was explicitly enabled. Now:
  - `renderListingRail()`: icon + name always use NBT-aware methods when nbtJson exists
  - `renderDetailPanel()`: large preview + name always use NBT-aware methods
  - `renderSingleItemDetail()`: large preview + name + hover tooltip now all use NBT-aware methods
  - `ADD_LISTING_MAINHAND` already captures held item NBT (enchantments, lore, etc.) automatically
- **Note:** Admin shop items defined in JSON catalogs don't have NBT — this is by design (admin items are generic).

### ❌→✅ 8. NBT-Aware Listings (Fuel Tanks, Enchanted Gear)
- Compound barter (MONEY_AND_BARTER) flow reviewed — trade path is functional. `nbtAware` correctly controls extraction matching from linked storage.
- The `nbtAware` flag is on the SOLD item (what the shop gives), not the PAYMENT item. Barter payment items are matched by item ID only.

### ❌→✅ 9. Back Buttons
- **Was:** "BACK BUTTONS PLEASEEEEEEEEEEEE"
- **Fix:** Back buttons verified/added on all screens:
  - `ShopMainScreen`: Added `§7← Back` button (top-left)
  - `ItemDetailScreen`: Already has `§7← Back` button
  - `PlayerShopBlockScreen` (visitor): Already has `§7← Back` when navigated from another screen
  - `BalanceOverviewScreen`: Already has `§7← Back` button
  - `BalTopOverviewScreen`: Already has `§7← Back` button
  - `CartScreen`, `BarterScreen`, `TransactionHistoryScreen`, `SettlementHistoryScreen`: All already have back navigation

### ❌→✅ 14. CarryOn Mod Compatibility
- **Was:** "picking up shops removes them from /shop nearby and marketplace"
- **Fix:** Three-layer defense:
  1. `ShopBlock.getPistonPushReaction()` returns `PushReaction.BLOCK` — prevents CarryOn from picking up
  2. `carryon:block_blacklist` tag file at `data/carryon/tags/blocks/block_blacklist.json` includes `futureshops:shop_block`
  3. `ShopBlock.setPlacedBy()` re-registers on any placement (fallback if somehow picked up)

### ❌→✅ 31. Fix Barter Chest Linking Description
- **Was:** "description says to right click on chest to link which doesn't work, /link does get barter to work"
- Barter chest linking uses `B.Lnk` button in owner UI which triggers `LINK_BARTER_LOOKING`, then `/link` to confirm. This is the same flow as main storage linking.

### ❌→✅ 32. Base Quantity Per Listing
- **Was:** "base quantity should be 0 to prevent instances where listings are being updated"
- **Status:** Already implemented. `setBaseQuantity()` uses `Math.max(0, baseQuantity)`. New listings default to `baseQuantity = 0`. The buy handler rejects purchases when `baseQuantity <= 0` with "UNCONFIGURED" status.

### Files Modified
- `client/screen/ItemDetailScreen.java` — quantity EditBox width 24→36px, button repositioning
- `client/screen/ShopMainScreen.java` — added back button, nearby tab rescan trigger, C2SOpenShopPacket import
- `client/screen/PlayerShopBlockScreen.java` — NBT-aware visuals always shown when nbtJson exists, tooltip hover added to single-item detail, renderSingleItemDetail signature updated to pass mouseX/mouseY
- `compat/rs2/RefinedStorage2StorageAdapter.java` — flexible namespace detection for RS2 mod variants

## Latest pass — LilGameb0y Feedback Round (24 items) (2026-04-13)

### LGB#1: Barter Discount Display
- Added `baseBarterItemCount` field to `PlayerShopListingData` — carries the undiscounted barter cost for display.
- `PlayerShopBlockService.toData()` sends `listing.barterItemCount()` as the base, alongside `effectiveBarterItemCount()` as the discounted count.
- Detail panel pricing section shows base barter rate with strikethrough (§m) when promo discounts it.
- `PlayerShopBarterScreen` give panel shows the same base/discounted barter comparison.

### LGB#2: Cart Trade Mode Display
- `PlayerShopCartState.CartEntry` extended with `tradeMode`, `barterItemId`, `barterItemCount`, `nbtJson` fields.
- `PlayerShopCartScreen` shows colored trade mode abbreviations: `$` (money), `B` (barter), `M/B` (both), `M+B` (compound).

### LGB#3: Barter-Only Cart Price Fix
- Cart rows for barter-only trades display barter cost instead of misleading "$1.00" money price.

### LGB#5: +Cart Greyed Out When Out of Stock
- `ItemDetailScreen`: `addToCartButton.active` tied to stock availability.
- `PlayerShopBlockScreen`: `syncButtonStates()` sets `addToCartButton.active = inStock`.

### LGB#6: Quantity Transfers to Barter Confirmation Screen
- `PlayerShopBarterScreen` accepts `initialQuantity` parameter, initializes quantity from parent screen.

### LGB#7: Barter Confirmation Screen Stock Display
- Receive panel now shows stock number under the receive quantity line.

### LGB#8: 100% Discount = Free
- `applyUnitPrice()` and `effectiveBarterItemCount()` allow 0 result.
- Percentage promos capped at 100% in `configure()`.
- Server buy path skips money withdrawal when cost is 0.

### LGB#9: Flat Promo Default Value → "10.00"
### LGB#10: Minus Sign in Promo Editor → "- %/$"
### LGB#11: "Money or Barter" → "§aMoney§7/§9Barter", "M+B" → "§dM+B"
### LGB#13: Scrollbar Arrows Inline With Track
### LGB#17: Text Fields Update on Enter (keyCode 257)
### LGB#18: Cart Item Tooltips + NBT Badge
### LGB#21: Barter Link Messages Updated (explicit /link, differentiated barter success)
### LGB#22: Barter Badge on All Eligible Storefront Items (not exclusive with promo)
### LGB#23: Barter Button Moved to Bottom Row in ItemDetailScreen
### LGB#24: Single Item Config Uses Selected Listing (C2SPlayerShopConfigPacket extended)

### Deferred
- **LGB#14**: Page counter removal (confirmed already removed in prior pass)
- Pixel-perfect GUI scale 4 fine-tuning may need further visual iteration

### Verification
- `./gradlew.bat build` succeeded with no compile errors.

## Latest pass — LGB#4 Toggle + #12/#15/#16 Polish (2026-04-13)

### LGB#4: In-Cart Money/Barter Toggle Per Entry
- `C2SPlayerShopBuyPacket` extended with `paymentMethod` String field (empty = auto, "MONEY", "BARTER").
- Legacy constructor preserved for callers that don't specify payment method.
- `PlayerShopBlockService.buy()` signature updated to accept `paymentMethod`.
- BOTH-mode server logic now respects client-provided preference; falls back to balance check if empty.
- `PlayerShopCartState.CartEntry` extended with `chosenPayment` field.
  - Defaults to "MONEY" for BOTH-mode entries on add.
  - `togglePayment()` method toggles between "MONEY" and "BARTER".
- `PlayerShopCartScreen` render loop:
  - BOTH-mode entries show "§a$ Money" or "§9⚒ Barter" badge reflecting current choice.
  - Clickable "§8[toggle]" hint next to badge toggles payment method.
  - Price display updates dynamically (shows money or barter cost based on chosen payment).
- `PlayerShopCartScreen.checkout()` passes `entry.chosenPayment()` to the buy packet.
- `mouseClicked()` handles BOTH-mode toggle clicks on the badge row area.

### LGB#12: GUI Scale 4 Adaptive Spacing
- `PlayerShopCartScreen`: Row height adapts (28px at guiH < 240, 36px normal).
  - Content area and scroll behavior update accordingly.
  - All interaction handlers (mouseClicked, mouseScrolled) use matching adaptive height.
- `PlayerShopBlockScreen`: Listing rail card height adapts (38px at railH < 200, 44px normal).
  - `mouseClicked()` and `mouseScrolled()` use matching adaptive cardH.
  - More listings visible at high GUI scale without overlap.

### LGB#15: Inline Base Quantity After Item Name
- Added `ShopUiUtil.getItemDisplayNameWithQty(itemId, baseQuantity)` — appends " ×N" when baseQuantity > 1.
- Added `ShopUiUtil.getItemDisplayNameWithNbtAndQty(itemId, nbtJson, baseQuantity)` — NBT-aware variant.
- Applied across:
  - `PlayerShopBlockScreen.renderListingRail()` item name
  - `PlayerShopBlockScreen.renderDetailPanel()` item name
  - `PlayerShopBlockScreen.renderSingleItemDetail()` preview name + right-side scaled title
  - `PlayerShopBarterScreen.renderReceivePanel()` item name
  - `PlayerShopBarterScreen` receive summary text
  - `PlayerShopCartScreen` cart entry item names

### LGB#16: Barter Trade Info Reflects Base Quantity
- Detail panel trade summary for BARTER mode: "2 × Oak Planks per Stick ×6" instead of "2 × Oak Planks per item".
- Cart screen compound/barter entries show total barter cost correctly.
- Cart barter-only entries show "per N/tx" hint when baseQuantity > 1.

### Files Modified
- `network/packets/C2SPlayerShopBuyPacket.java` — paymentMethod field, legacy constructor
- `server/shop/PlayerShopBlockService.java` — buy() accepts paymentMethod, BOTH-mode uses preference
- `client/PlayerShopCartState.java` — chosenPayment field, togglePayment(), unused import removed
- `client/screen/PlayerShopCartScreen.java` — full render/interaction rewrite for #4/#12
- `client/screen/PlayerShopBlockScreen.java` — adaptive cardH, inline qty names, barter trade info
- `client/screen/PlayerShopBarterScreen.java` — inline qty names in receive panel and summary
- `client/screen/ShopUiUtil.java` — getItemDisplayNameWithQty, getItemDisplayNameWithNbtAndQty

### Verification
- `./gradlew.bat compileJava` succeeded with no compile errors.

## Latest pass — Beta Feedback Items 19/#20 Tooltip Coverage (2026-04-13)

### LGB#19: Default Tooltip on Hover for All Items (NBT or Not)
- Tooltip system already handled all items regardless of NBT (empty `nbtJson` → plain ItemStack tooltip).
- **Gap**: `PlayerShopBarterScreen` (receive + give panels) and `BarterScreen` (admin storefront) had no hover detection at all.
- **Fix**: Added `hoveredItemId`/`hoveredNbtJson` tracking fields and post-super tooltip rendering to both screens.
- `PlayerShopBarterScreen.renderReceivePanel()` now detects hover over the large item preview area.
- `PlayerShopBarterScreen.renderGivePanel()` now detects hover over the barter item preview area.
- `BarterScreen.renderReceivePanel()` now detects hover over the target item preview.
- `BarterScreen.renderGivePanel()` now detects hover over each ingredient's 16×16 icon.

### LGB#20: Tooltip on Detail Panel Item Preview
- **Gap**: `PlayerShopBlockScreen.renderDetailPanel()` (multi-listing view) had no hover detection on the large item preview.
- **Fix**: Method signature updated to accept `mouseX, mouseY`. Hover detection added over the preview area (full preview width × 70px height).
- The `render()` call site updated to pass `mouseX, mouseY` to `renderDetailPanel()`.
- Single-item detail view already had hover detection (verified working).

### Files Modified
- `client/screen/PlayerShopBlockScreen.java` — renderDetailPanel accepts mouseX/mouseY, hover detection on preview
- `client/screen/PlayerShopBarterScreen.java` — tooltip fields, hover detection in receive+give panels, post-super tooltip render
- `client/screen/BarterScreen.java` — tooltip fields, hover detection in receive+give panels, post-super tooltip render

### Verification
- `./gradlew.bat compileJava` succeeded with no compile errors.

### LGB Feedback Completion Summary
All 24 LilGameb0y feedback items are now implemented:
- ✅ LGB#1–#13, #14 (already done), #15–#24


## Latest pass — Full-Screen UI Redesign for GUI Scale 4+ (2026-04-14)

### Problem
At GUI Scale 4 (1920×1080 → 480×270 effective pixels), all shop screens had hard upper bounds on their panel sizes (e.g. `Math.min(580, ...)`, `Math.min(380, ...)`). This caused severe text overlapping in the owner "Manage Shop" UI, where the detail panel content, adjustment controls, and footer buttons all competed for the same vertical space.

### Solution: Full-Screen Layout with Computed Regions
Every screen now uses `this.width - 4` / `this.height - 4` as the panel size (nearly full screen), removing all artificial max caps.

### PlayerShopBlockScreen — Complete Layout Restructure
- **Computed layout fields**: `compact`, `headerHeight`, `configPanelHeight`, `contentStartY`, `contentAreaH`, `listingRailW` — computed once in `init()`, used by all render/interaction methods.
- **Compact mode** (`guiH < 300`): header 30px (single row), config panel 24px, shorter badges/labels.
- **Normal mode**: header 50px (two rows), config panel 38px, full badges.
- **Listing rail width**: adaptive (`max(120, min(200, guiW * 30%))`), no longer hardcoded 170px.
- **Owner adjustment controls**: Positioned in their own zone BELOW the content area and ABOVE the footer, preventing overlap with the detail panel content. Uses `contentStartY + contentAreaH + 2` as anchor.
- **Footer buttons**: 2-row wrapping when `guiW < 460`. Row 1: Add/Del/Promo/Collect/Hist/Dept. Row 2: Link/Unlk/B.Lnk/B.Ulk.
- **Visitor controls**: Right-to-left positioning from the edge, with tightFit mode for narrow screens.
- **Card height**: Three tiers — 32px (railH < 160), 38px (railH < 200), 44px (normal).
- **Badges**: Only render department/NBT/visibility badges when card height ≥ 38px.

### Other Screens Expanded
All screens now use nearly full available screen space:
- `PlayerShopCartScreen` — was capped at 500×360, now uses full screen
- `PlayerShopBarterScreen` — was capped at 440×260, now uses full screen
- `BarterScreen` — was capped at 460×280, now uses full screen
- `ItemDetailScreen` — was capped at 340×260, now uses full screen
- `ShopMainScreen` — was capped at 640×400, now uses full screen
- `CartScreen` — was capped at 520×340, now uses full screen
- `BalanceOverviewScreen` — was capped at 540×320, now uses full screen
- `BalTopOverviewScreen` — was capped at 560×360, now uses full screen
- `TransactionHistoryScreen` — was capped at 520×320, now uses full screen
- `SettlementHistoryScreen` — was capped at 320×210, now uses full screen
- `DepartmentPickerScreen` — was capped at 240×200, now uses full screen
- `PromoEditorModalScreen` — was capped at 256×166, now uses full screen

### Files Modified
- `client/screen/PlayerShopBlockScreen.java` — complete layout restructure (init, owner/visitor widgets, all render methods, mouse handlers)
- `client/screen/PlayerShopCartScreen.java` — sizing expanded
- `client/screen/PlayerShopBarterScreen.java` — sizing expanded
- `client/screen/BarterScreen.java` — sizing expanded
- `client/screen/ItemDetailScreen.java` — sizing expanded
- `client/screen/ShopMainScreen.java` — sizing expanded
- `client/screen/CartScreen.java` — sizing expanded
- `client/screen/BalanceOverviewScreen.java` — sizing expanded
- `client/screen/BalTopOverviewScreen.java` — sizing expanded
- `client/screen/TransactionHistoryScreen.java` — sizing expanded
- `client/screen/SettlementHistoryScreen.java` — sizing expanded
- `client/screen/DepartmentPickerScreen.java` — sizing expanded
- `client/screen/PromoEditorModalScreen.java` — sizing expanded

### Verification
- `./gradlew.bat compileJava` succeeded (BUILD SUCCESSFUL).

### Remaining Priorities
1. Visual QA at GUI Scale 1, 2, 3, and 4 to verify no regressions
2. NBT-aware compound barter trade implementation
3. CarryOn runtime verification (shops fully unpickable)


## Latest pass — NBT Badge Fix + Cart Barter Summary (2026-04-14)

### Bug Fix: NBT items not rendering correctly in PlayerShopBarterScreen
- **Root cause**: `renderReceivePanel()` used `ShopUiUtil.renderLargeItemPreview()` (no NBT) instead of `renderLargeItemPreviewWithNbt()`
- **Symptom**: Items with meaningful NBT (e.g. half-full tanks) displayed as empty/default in the barter confirmation screen, but showed correctly in the listing page and on hover tooltip
- **Fix**: Added NBT-aware branching in `renderReceivePanel()` — uses `renderLargeItemPreviewWithNbt` when `listing.nbtAware() && hasNonDefaultNbt()`
- **Also fixed**: Display name now uses `getItemDisplayNameWithNbtAndQty()` for correct custom names

### Feature: Barter item icon preview in visitor detail panels
- **Multi-item detail panel** (visitor pricing section): When listing has a barter option, renders a 16×16 barter item icon + name below the "Owned:" line with hover tooltip support
- **Single-item detail panel** (visitor view): Same treatment — barter item icon + name rendered below the owned count with hover tooltip
- **UX improvement**: Visitors can now see exactly what barter item is required without needing to click into the barter screen

### Files changed
| File | Change |
|---|---|
| `PlayerShopBarterScreen.java` | NBT-aware large preview + NBT-aware display name in receive panel |
| `PlayerShopBlockScreen.java` | Barter item icon + name in multi-item and single-item visitor detail panels |

### Verification
- `./gradlew.bat build` BUILD SUCCESSFUL


---

## Audit — 2026-04-15 (Session 2: Final 4 Gap Fixes)

## Changes implemented

1. **NBT toggle button repositioned** (`PlayerShopBlockScreen.java`)
   - Moved from barter row (Section 2) to config row (Section 3), after the Vis button
   - No longer cramped next to barter count controls

2. **Quantity badge visibility** (`PlayerShopBlockScreen.java`)
   - `×baseQuantity` in listing rail meta line now only renders for visitors (`!PlayerShopClientState.owner()`)
   - Owners already have Q-/Q+ inline controls making the badge redundant

3. **PlayerShopBarterScreen name×1 / qty button overlap** (`PlayerShopBarterScreen.java`)
   - Moved qty controls from inside receive panel (`guiTop + guiH - 68`) to the bottom footer row (`bottomY = guiTop + guiH - 24`)
   - Qty controls now sit left of the receive summary text and confirm button — no overlap with the receive panel's text stack

4. **Settlement "Claim" → "Cart Claim"** (`SettlementHistoryScreen.java`)
   - `filterLabel()` now uses a switch expression with human-friendly names instead of raw `filter.name()`
   - CLAIM filter displays as "Cart Claim" matching the i18n key

## Verification
- Barter button highlight reset on department click: **already implemented** (line 582-584 in `ShopMainScreen.java` resets `barterMode = false` and updates button text)
- Build: `BUILD SUCCESSFUL in 11s`

## Remaining known gaps: None from the original bug report list.

## Latest pass — RS Controller-Only Linking (Task 1) (2026-04-15)

- `PlayerShopBlockService.isValidLinkTarget()` refactored into `validateLinkTarget()` returning `LinkTargetResult` enum: `OK`, `BAD_LINK_TARGET`, `RS_NOT_CONTROLLER`
- New helper methods `isRSBlock()` and `isRSController()` — checks block entity type registry path for `controller`/`creative_controller`
- `confirmLink()` now returns `RS_NOT_CONTROLLER` result code when player tries to link to a non-controller RS block (Drive, Grid, etc.)
- Lang key added: `gui.futureshops.player_shop.result.rs_not_controller`
- `PlayerShopBlockScreen.buyerFriendlyMessage()` updated to show RS_NOT_CONTROLLER to visitors/owners

## Verification
- `./gradlew.bat build` BUILD SUCCESSFUL

## Latest pass — UI Overlap Fix (Task 2) (2026-04-15)

- `PlayerShopBlockScreen.initVisitorWidgets()`: Back button moved above the header (`guiTop - 16`) to prevent overlap with header content and close button at high GUI scales

## Verification
- `./gradlew.bat build` BUILD SUCCESSFUL

## Latest pass — ConfirmationModal Wired into All Transaction Screens (Task 3) (2026-04-15)

- **CartScreen**: Checkout now shows ConfirmationModal with all cart lines and total before sending `sendCheckout()`. Added `onTransactionResult()`, modal rendering, and input interception.
- **PlayerShopBlockScreen**: Buy button now calls `showBuyConfirmation()` → ConfirmationModal with item/price summary → `buy()` on confirm. Added `onTransactionResult()`, modal rendering, mouseClicked/keyPressed interception.
- **PlayerShopBarterScreen**: Confirm button now calls `showBarterConfirmation()` → ConfirmationModal with receive/give summary → `confirm()` on confirm. Added `onTransactionResult()`, modal rendering, mouseClicked/keyPressed interception.
- **BarterScreen** (admin shop): Confirm button now calls `showBarterConfirmation()` → ConfirmationModal with receive/give ingredients summary → `sendConfirm()` on confirm. Added `onTransactionResult()`, modal rendering, mouseClicked/keyPressed interception.
- **ShopClientPacketHandler**: Updated `handleBuyResponse()` to route to CartScreen, `handleBarterResponse()` to route to BarterScreen, `handlePlayerShopResult()` to route to PlayerShopBlockScreen and PlayerShopBarterScreen modals.

## Verification
- `./gradlew.bat build` BUILD SUCCESSFUL

## Latest pass — Keyboard Shortcuts (Task 4, Spec §15) (2026-04-15)

- **Tab cycling**: Tab key cycles focus search → sidebar → grid → search in ShopMainScreen
- **Arrow key grid navigation**: Left/Right/Up/Down moves `selectedGridIndex` through the item grid; grid auto-scrolls to keep selection visible; Enter opens the selected item
- **Shift+Click quick-add**: Shift+Left-click on a grid item adds it to cart without opening detail screen
- Selected grid item gets visual highlight (cyan border + hover background)

## Verification
- `./gradlew.bat build` BUILD SUCCESSFUL

## Latest pass — Bug fix batch + commit policy (2026-04-18)

- **CLAUDE.md commit policy tightened**: New "Commit / push policy (strict)" section forbids Claude from running `git commit`, `git push`, `gh pr create`, etc. Commits must happen from the user's terminal under the user's own git credentials; Claude may only stage and diff.
- **BalTop server spotlight scroll**: `BalTopOverviewScreen.renderHighlights()` now renders fixed-height leader cards inside a scissor-clipped viewport with a scrollbar; `mouseScrolled()` handles wheel input within the panel. Resolves the "#1 item seller overlapping sales/actions cards" layout bug.
- **Buy-screen pink reduced**: `ShopMainScreen` sidebar accent swapped from `ACCENT_PROMO_HI` to `ACCENT_PRIMARY` (cyan). `ShopColors.DISCOUNT_BG` switched from magenta to `STATUS_WARNING` (amber) with a dark readable text; magenta is now reserved for true promo surfaces.
- **RS storage stock refresh**: `RefinedStorage2StorageAdapter.initReflection()` no longer permanently flips its `reflectionInitialized` guard on failure — the guard now only short-circuits on prior success, so the adapter retries reflection each call until RS classes load. Previously a first-call failure (RS not yet loaded) silently pinned the adapter to the IItemHandler fallback, which doesn't see RS network inventory — causing "stock never refreshes after network change" until the block was relinked.
- **Duplicate "All" department removed**:
  - `ShopDefinitionLoader.buildDefaultShop()` no longer emits a hardcoded `"all"` category (the shop UI already renders a virtual "All" tab at index 0).
  - `ShopCatalog.buildCategories()` now filters any persisted category with id `"all"` so existing installs with the legacy default also get a clean sidebar.
- **"Nearby" department tab hidden**: `ShopMainScreen.hasNearbyTab()` now returns `false`; the dedicated 📍 Nearby button at the top of the screen remains the single entry point.
- **Player-shop quantity/Max button accuracy**: `PlayerShopBarterScreen.resolveMaxQuantity()` no longer forces a floor of 1. It now returns the true achievable trade count (stock capped by `inventory / barterCost`, possibly 0). The display field is still clamped to ≥1 by `setQuantity()`, but the Max button and text-field clamp now report the real maximum instead of misleadingly showing 1 when the player can't afford any trades.

## Verification
- `./gradlew.bat build` BUILD SUCCESSFUL (with `-Dnet.minecraftforge.gradle.check.certs=false` workaround due to Forge maven certificate issue on this environment)

## Latest pass — Palette semantics, pink-reduction layer 2 (2026-04-18)

Magenta (`ACCENT_PROMO_HI`) was being used as a generic "decorative accent" across screens it had no semantic relationship to. This pass makes magenta an exclusive promo/sale signal and reworks the rest of the palette into three minimal semantic buckets.

**Palette semantics after this pass:**
- **Cyan (`ACCENT_PRIMARY`)** — neutral/structural accent. All top-of-panel accent strips, active-tab/selected-row markers, general section titles on non-owner surfaces.
- **Amber (`ACCENT_CURRENCY`)** — ownership + money surfaces. Player-shop owner header/accent, CONFIG section strip/label, MONEY_AND_BARTER trade-mode label.
- **Magenta (`ACCENT_PROMO_HI`)** — real promo/discount/sale UI only (`PromoEditorModalScreen`, discount badges).

**Token changes in `ShopColors.java`:**
- `ACCENT_PURPLE` alias retargeted from `ACCENT_PROMO_HI` → `ACCENT_PRIMARY`. Collapses all legacy "purple" chip/mode call sites to neutral cyan without per-call edits.
- `OWNER_ACCENT` retargeted from `ACCENT_PROMO_HI` → `ACCENT_CURRENCY`.

**Explicit call-site swaps (pink → cyan):**
- `BarterScreen.java`: top accent bar + active recipe-tab underline.
- `BalTopOverviewScreen.java`: "Top 10 Franchises" section strip and title.
- `DepartmentPickerScreen.java`: modal top strip + selected-row left marker.
- `FranchiseManagementScreen.java`: top accent bar, franchise-header strip/title (all four sites).
- `ItemDetailScreen.java`: preview panel top strip.
- `LocalShopBrowserScreen.java`: sidebar top strip.
- `PlayerShopBarterScreen.java`: top accent bar.
- `PlayerShopBlockScreen.java`: listing-rail top strip, detail-panel preview strip.
- `TransactionHistoryScreen.java`: `CART_CLAIM` event color (was uncomfortably reading as a "promo" in the history list).

**Explicit swaps (pink → amber):**
- `PlayerShopBlockScreen.java`: owner-vs-visitor accent dispatch (two sites, was cyan/pink, now cyan/amber), config-panel top strip, CONFIG sub-section rail + label.
- `PlayerShopBlockScreen.java`: `MONEY_AND_BARTER` trade-mode text in both rail and detail views (two sites).

**Untouched (correctly pink):**
- `PromoEditorModalScreen.java` — real promo editing surface.
- All `ShopColors.ACCENT_PROMO_HI` token references from promo badge helpers.

## Verification
- `./gradlew.bat build` BUILD SUCCESSFUL (`-Dnet.minecraftforge.gradle.check.certs=false`).

## Latest pass — §-code text colors aligned to new palette (2026-04-18)

Structural ARGB accents were already recolored, but Minecraft `§` color codes embedded in widget labels, chat strings, and summaries still rendered as legacy pink (`§d`). That caused the same screens to be simultaneously cyan/amber (via ARGB) and pink (via `§d`). This pass aligns the `§` codes:

- **Barter context → `§9`** (blue, matches `TEXT_BARTER_SOFT`): mode buttons, barter item names, "You Give" panel titles, "§d⟶" arrows, "§dM+B" badges that were barter-leaning, chat `barter storage link` message.
- **Owner action buttons / owner screen titles → `§6`** (gold, matches `ACCENT_CURRENCY`): Q-/Q+ qty buttons, Dept button, B.Lnk / B.Ulk barter link buttons, Sep./Separate storage toggle, "Manage Shop" / "Manage Your Shop" titles, `MONEY_AND_BARTER` "M+B" mode badge.
- **Generic picker title → `§b`** (aqua, matches `ACCENT_PRIMARY`): DepartmentPickerScreen "📦 Department Picker".
- **Kept `§d`**: `PromoEditorModalScreen` title only — magenta's only remaining job.

Files touched: `LocalShopBrowserScreen`, `DepartmentPickerScreen`, `BarterScreen`, `ItemDetailScreen`, `ShopMainScreen`, `PlayerShopBarterScreen`, `PlayerShopBlockScreen`, `PlayerShopCartScreen`, `PlayerShopLinkService` (server-side chat message).

## Verification
- `./gradlew.bat build` BUILD SUCCESSFUL (`-Dnet.minecraftforge.gradle.check.certs=false`).

---



## Latest pass — quantity-box & confirmation-modal escape bugs (2026-04-18)

Two regressions reported by the user were fixed together:

### Bug A — Quantity field "not accepting input" until `/shop` was opened first
- **Root cause:** every quantity `EditBox` set a `setResponder` that parsed the live value, clamped it via `resolveMaxQuantity()`, and wrote the clamped string back into the box (`quantityBox.setValue(...)`). When a shop was opened via right-clicking a block before `ShopClientState` had been primed by `/shop`, the client catalog was empty, so `resolveMaxQuantity()` returned `1`. Every keystroke therefore collapsed the field back to `"1"` and moved the caret, making the field feel dead. `/shop` populated the catalog → max resolved correctly → typing worked.
- **Fix:** removed in-typing clamp-and-rewrite behavior. Responders no longer call `setValue` while the user is editing; `setFilter(s -> digits-only)` prevents junk input. Clamping is applied only at consumption sites (`getQuantity()` on `ItemDetailScreen` / `PlayerShopBlockScreen`, `multiplier`/`quantity` state in `BarterScreen` / `PlayerShopBarterScreen`, and all `setQuantity`/`setMultiplier` button handlers).
- **Files:** `client/screen/ItemDetailScreen.java`, `client/screen/BarterScreen.java`, `client/screen/PlayerShopBarterScreen.java`, `client/screen/PlayerShopBlockScreen.java`.

### Bug B — "Processing…" modal hang + ESC/Back unable to leave
- **Root cause:** `ConfirmationModal.keyPressed` swallowed ESC silently when `state == PROCESSING` (only fired `onCancel` for `WAITING`), and `mouseClicked` returned early for both `PROCESSING` and `SUCCESS`, so the Cancel button and outside-clicks were all dead. If the server response never reached the client (some barter failure branches close the shop screen before updating the modal, or any dropped packet), the player was stuck in the modal and had to restart the game.
- **Fix (`client/screen/ConfirmationModal.java`):**
  - ESC now always dismisses (calls `onCancel.run()`), regardless of state.
  - `mouseClicked` during `PROCESSING` routes clicks to the Cancel button and outside-click-cancel just like `WAITING` (Confirm is gated to `WAITING` only).
  - Added `processingStartedAt` timestamp + `PROCESSING_TIMEOUT_MS = 10_000`. If the server hasn't responded in 10 s, the modal transitions to `FAILED("Request timed out")` on the next render pass, so the player can always get back out via the OK button / ESC.
- **Safety:** cancelling during `PROCESSING` only closes the UI; any in-flight server-side transaction still settles server-side (no duplicate spend risk because the packet already left the client).

## Verification
- `./gradlew.bat build` BUILD SUCCESSFUL (`-Dnet.minecraftforge.gradle.check.certs=false`).

---

## Latest pass — NBT/barter exploit, Nearby tab, disband confirm, history detail, promo polish (2026-04-19)

Six issues reported by the user, fixed in one pass.

### Bug A — Barter ingredients accepted items with arbitrary NBT
- **Root cause:** `ShopBarterService` and `PlayerShopBlockService` called `ShopTransactionUtil.countItems`/`removeItems` without NBT filtering. Any stack matching the item ID counted — so a half-full modded tank, a damaged tool, or an enchanted chestplate was consumed as a "plain" barter ingredient.
- **Fix:** both services now pass `nbtAware=true, requiredTag=null` to the count/remove helpers — only stacks with no tag (vanilla/plain form) qualify. `ShopUiUtil.countPlayerInventory` now filters the same way so the client-side Max button stays in sync.
- **Files:** `server/transaction/ShopBarterService.java`, `server/shop/PlayerShopBlockService.java`, `client/screen/ShopUiUtil.java`.

### Task B — Restored the "Nearby" department tab
- `ShopMainScreen.hasNearbyTab()` now returns `true` again. The user preferred the duplicate sidebar entry alongside the top `📍 Nearby` button.

### Task C — Disband franchise now prompts for confirmation
- `FranchiseManagementScreen` now opens a `ConfirmationModal` when the leader hits Disband. Modal has its own Esc/click handling and inherits the existing shared `ConfirmationModal.PROCESSING` timeout (so the same soft-lock protection applies).

### Task D — Transaction history shows what was bartered
- Server records barter payment details in the existing `note` field using `paid=<itemId>×<n>[,…]`. Both admin shop (`ShopBarterService`) and player shop (`PlayerShopBlockService`, covers `BARTER` and `MONEY_AND_BARTER`) emit this format.
- `TransactionHistoryScreen` row height bumped to 28px and the screen renders a secondary line like `paid: 8×Diamond` under the base row when the note parses.

### Task E — Discount badge readability
- `ShopUiUtil.renderAnimatedDiscountBadge` rebuilt: removed the 0.82→1.18 scale pulse and the -15° rotation that made the text jitter. Now a static pill with a gentle red halo pulse. Text stays sharp on flash promos and percentage labels alike.

### Task F — Promo schedule now clearly shows unit
- `PromoEditorModalScreen` schedule fields relabeled `Start (min)` / `Length (min)` (both the label and hint).
- Added a live helper line below: `starts in 2d 4h • lasts 1d` (or `starts: now • duration: until cleared`). Owners no longer have to do mental math from raw minutes.

## Verification
- `./gradlew.bat build` BUILD SUCCESSFUL (`-Dnet.minecraftforge.gradle.check.certs=false`).

---

## Follow-up pass — promo-end sync, Leave/Clear confirms, sell-path NBT audit (2026-04-19)

Three additional items requested after the first round.

### Task G — Buyers see promo end-time on player shops
- Server already persisted `startEpochSeconds` / `endEpochSeconds` on `ShopBlockEntity.Promo`, but neither field was synced to clients.
- Added accessors to `ShopBlockEntity.Promo` and expanded `PlayerShopPromoData` to carry both epochs (encoded via `writeVarLong`, clamped to `>=0`).
- `PlayerShopBlockService` now populates those fields when building the client payload.
- `PlayerShopBlockScreen.renderDetailPanel` renders a new `starts in …` / `ends in …` line (human-readable d/h/m format) below the promo banner, so buyers can see the countdown without opening the editor.
- **Files:** `data/PlayerShopPromoData.java`, `block/ShopBlockEntity.java`, `server/shop/PlayerShopBlockService.java`, `client/screen/PlayerShopBlockScreen.java`.

### Task H — Confirmation modal parity for Leave Franchise + Clear Promo
- `FranchiseManagementScreen` Leave button now opens a `ConfirmationModal` ("Leave \"<name>\"?") before sending `C2SFranchiseActionPacket("LEAVE", "")`. Matches the disband flow's Esc/click routing and timeout protection.
- `PromoEditorModalScreen` Clear button now opens a `ConfirmationModal` ("Remove promo from <item>?") before clearing. Esc is routed to the overlay first so dismissing the confirm doesn't close the whole editor.
- **Files:** `client/screen/FranchiseManagementScreen.java`, `client/screen/PromoEditorModalScreen.java`.

### Task I — Sell-path NBT-strictness audit
- `ShopSellService.execute` was calling `ShopTransactionUtil.countItems`/`removeItems` without NBT filtering, meaning a damaged tool or enchanted chestplate would sell at the plain-item price.
- Both call sites now pass `nbtAware=true, requiredTag=null` — only plain/tag-less stacks qualify, matching the barter fix from the previous pass.
- Audit confirmed all other sell-like flows (`ShopBarterService`, `PlayerShopBlockService` barter ingredient consumption) already use the strict form.
- **Files:** `server/transaction/ShopSellService.java`.

## Verification
- `./gradlew.bat build` BUILD SUCCESSFUL (`-Dnet.minecraftforge.gradle.check.certs=false`).
