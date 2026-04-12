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
1. Finish responsive scaling pass for all screens (`ShopMainScreen`, `CartScreen`, `BarterScreen`, `Balance/BalTop`, player shop screens) under extreme GUI scales.
2. Expand settlement history with richer filters/export and owner-global multi-shop views.
3. Continue full spec gap-closure pass (automation compatibility matrix, dynamic pricing scheduler, API/events, persistence model migration).

