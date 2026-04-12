# FutureShops Mod Status Audit (2026-04-12)

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
- Persistence model from §25 (SQL schema-level tables) is not implemented; current persistence uses Minecraft SavedData.
- Dynamic pricing (§30) and stock refresh scheduler (§31) are not fully implemented.
- Developer API/events (§33) are not fully implemented.

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
- **Single bottom row**: Quantity controls (`- [qty] + Max`) on the left, action buttons (`+ Cart`, `$ Buy`, `↑ Sell`) on the right, all at the same height (14px).
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
- `TOGGLE_MODE` server action cycles `MONEY → BARTER → BOTH → MONEY`.
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
