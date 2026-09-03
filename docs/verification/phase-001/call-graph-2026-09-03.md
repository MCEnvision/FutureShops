# Phase 001 monetary call graph and persistence inventory

Date: 2026-09-03
Base: `e10fc75ca6431828141cadd2908d2d5f4fea1d15`, the merged Phase 000 result on `1.21.1`

This inventory is the `P001-TASK-001` entry record. It is a discovery artifact, not a claim that the listed legacy paths already satisfy the Phase 001 coordinator contract.

## Authority boundary

`BalanceManager` is the current static entry point. It initializes the internal provider for `internal` and exposes `getBalance`, `getProvider`, `transfer`, and `getTopBalances`. The Phase 000 external selection boundary replaces the provider with a fail closed legacy implementation when the selected provider is missing or unresolved. The Phase 001 coordinator must replace these direct legacy calls with one server authoritative route.

The direct call scan found these production callers.

| Area | Callers and effects | Required Phase 001 route |
| --- | --- | --- |
| Lifecycle | `Futureshops.serverStarting` calls `BalanceManager.initialize`; `serverStopping` calls `BalanceManager.clear` | Lifecycle controller, drain, checkpoint, clean marker last |
| Public API | `ShopModAPI.getProvider`, `getBalance`, `withdraw`, `deposit`, `transfer`, and administrative balance mutation helpers | Typed public coordinator requests with server identity and permissions |
| Commands | `BalanceCommand`, `BalTopCommand`, `PayCommand`, `DepositCommand`, `WithdrawCommand`, and `ShopAdminCommand` | Server revalidation, capability preflight, typed unavailable and recovery results |
| Admin and analytics | `MarketplaceAnalyticsService` reads balances and top balances; admin commands grant, remove, set, and inspect balances | Confirmed facts only, no external balance mirror, no hidden fallback |
| Money items | `MoneyItem` deposits validated bills through the selected legacy provider | Ready internal only, inert and retained outside that state |
| Server shops | `ShopBuyService` reads balances, withdraws buys, deposits rollback; `ShopSellService` reads balances, deposits sells, withdraws rollback | Intent and custody before dependent value movement, stable request and leg identity |
| Player shops | `PlayerShopBlockService` performs buyer debit, seller proceeds, buyback, claim settlement, rollback, refunds, and balance display | Multi leg coordinator, durable custody, claims, compensation, and offline proceeds |
| Shop data | `ShopDataService` reads a balance for server supplied shop data | Presentation snapshot, typed unavailable instead of zero |
| Events | `InternalEconomyProvider` emits balance change events directly around saved balance mutation; shop services emit transaction events after work | Confirmed coordinator outcomes only, no event on ambiguous or rejected work |

The scan also found a default `EconomyProvider.transfer` implementation that withdraws and then deposits, with a best effort deposit rollback. It cannot remain the external mutation path because it has no durable request identity, receipt lookup, or at most once guarantee. The internal implementation may be retained behind the coordinator only after its durable leg records are written.

## Monetary surfaces

The master and phase plan surface rows map to concrete code as follows.

| Surface | Current implementation | Current risk before coordinator |
| --- | --- | --- |
| Balance query | `BalanceManager`, `ShopModAPI`, balance commands, analytics, shop data, player shop screens | A provider failure can throw through presentation and there is no typed lifecycle result |
| Buy and sell | `ShopBuyService`, `ShopSellService` | Item and value ordering is local and rollback is not a durable multi leg record |
| Cart | `C2SBuyRequestPacket` to `ShopBuyService` and client `CartScreen` | Aggregate validation and duplicate checkout have no root request journal |
| Player shop purchase | `PlayerShopBlockService` | Buyer debit, item custody, owner proceeds, delivery, claim, and compensation are not one durable state machine |
| Pay and transfer | `PayCommand`, `ShopModAPI.transfer`, default provider transfer | Debit and credit can be separated without durable leg identities |
| Admin mutations | `ShopAdminCommand`, `ShopModAPI` helpers | Administrative bypasses can mutate the internal provider directly |
| Deposit and withdrawal | `DepositCommand`, `WithdrawCommand`, `MoneyItem` | Bills and balance effects are not gated by selected provider lifecycle |
| Fees and refunds | Shop and player shop rollback paths | Refunds are direct provider calls and have no independent receipt identity |
| Events and analytics | `BalanceChangeEvent`, `ShopTransactionEvent`, `TransactionHistoryService`, `MarketplaceAnalyticsService` | Success events can be emitted from legacy local results rather than confirmed durable outcomes |
| Reload, startup, shutdown | `Config.onLoad`, `Futureshops` server events, `BalanceManager` | No draining, clean marker, recovery checkpoint, or frozen state |
| Browsing and barter | Catalog and `ShopBarterService` | These should remain usable without a provider money leg |
| ATM | No ATM command or screen was found in the current source inventory | Keep absent and add a source scan regression |

## Persistence inventory

The existing saved data and item records are read only inputs for the new schema. No existing record stores a strict external provider receipt or a FutureShops external balance mirror.

| Record or file | Storage boundary | Existing version or identity | Phase 001 treatment |
| --- | --- | --- | --- |
| `InternalBalanceSavedData` | Overworld `SavedData` | `DATA_NAME` with existing migration helper | Remains internal provider owned. Never read as external authority. |
| `TransactionHistorySavedData` | Overworld `SavedData` | `CURRENT_VERSION`, per player history | Retain history. Add provider identity and confirmed outcome facts only through a compatible extension if required. |
| `PlayerShopSettlementSavedData` | Overworld `SavedData` | Settlement identity and paging | Preserve settlement records and route money claims through durable coordinator records. |
| `PlayerShopRegistrySavedData` | Overworld `SavedData` | Player shop and listing identity | Preserve listing state and connect custody and claim identities without deleting listings. |
| `DynamicPricingSavedData` | Overworld `SavedData` | Product pricing and activity state | Preserve integer configured price magnitude and avoid current external balance fields. |
| `StockRefreshSavedData` | Overworld `SavedData` | Refresh timestamps | Preserve stock state. Shop transactions must record custody and value legs separately. |
| `AdminCategorySavedData`, `DepartmentSavedData`, `FranchiseSavedData`, `ShopLimitsSavedData`, `AdminShopToggleSavedData` | Overworld `SavedData` | Existing `SavedDataMigrations` versions | Unchanged except where transaction routing needs a stable owner or listing reference. |
| `SpentMintsSavedData` | Overworld `SavedData` | Mint replay and anti dupe ledger | Preserve item anti dupe behavior. It is not a provider balance or transaction journal. |
| `CoinData` and money item custom data | Item data components and legacy custom data | Stable denomination, mint, authorization, and checksum keys | Keep registration and decoding. Gate activation, deposit, withdrawal, minting, and redemption by ready internal lifecycle. |
| Shop and product configuration | `config/futureshops/shops` and related config files | JSON and TOML files | Preserve values. Validate aggregate and conversion bounds before any coordinator intent. |
| New economy journal | Not present at entry | No existing schema | Add an explicit versioned and checksummed SavedData or equivalent with request, leg, custody, claim, receipt, checkpoint, marker, and compensation records. Do not add an external balance field. |

## Test and runtime inventory

The repository has JUnit coverage for existing saved data, item validation, packet bounds, listing matching, and screen policy. Phase 000 added public API, registry, selection, and fail closed selection tests. There are no current coordinator, crash, custody, claim, multiplayer, migration, or lifecycle transition tests. The Gradle tasks available for this phase are `test`, `build`, `runData`, `runGameTestServer`, `runServer`, and `runClient`.

Required additions are grouped by boundary.

| Boundary | Existing evidence | Required additions |
| --- | --- | --- |
| API and provider fixtures | `EconomyProviderApiTest`, `EconomyProviderRegistryTest` | Capability complete, incomplete, duplicate aware, durable lookup, timeout, exception, malformed receipt, and ambiguous fixtures |
| Selection | `ProviderSelectionManagerTest`, `BalanceManagerSelectionTest` | Lifecycle state and restart selection integration |
| Persistence | Existing SavedData tests only | Version, checksum, migration, unknown newer, truncated, interrupted write, clean marker, recovery checkpoint, backup, and restore fixtures |
| Transactions | Existing shop tests are service level. `EconomyTransactionCoordinatorTest` now covers strict one leg intent, capability refusal, duplicate replay, ambiguity freeze, and lookup recovery. | Root and leg identity, intent ordering, custody conservation, receipt validation, duplicate completion, concurrency, crash point, compensation, and claim tests |
| World and inventory | No new GameTest in Phase 000 | GameTests for shop, player shop, custody, claims, bills, restart, and full inventory |
| Network and UI | Existing packet bounds and screen policy tests | Server snapshots, stale state, replay, reconnect, localization, disabled actions, client and dedicated server isolation |
| Runtime | Standard server and Xvfb client startup evidence exists for Phase 000 | Clean and unclean restart, draining, recovery, frozen state, multiplayer, reconnect, provider switching, and full surface walkthrough |

## Direct access and preservation rules

Every direct `BalanceManager` reference listed above is a Phase 001 routing target. Public API and command code must not read internal balances when an external provider is selected. No provider failure may turn into zero, internal metadata, a local shadow balance, or a successful event. Existing worlds, bills, shops, claims, and internal balances are preserved as read only migration inputs until their owning state machines are versioned and tested.

Issue 66 remains frozen and was not queried or modified during this task. External Pixelmon and hybrid runtime evidence remains outside this generic core inventory and is owned by later phases.

## Coordinator checkpoint

The first phase implementation checkpoint adds `EconomyLifecycleController`, `EconomyTransactionCoordinator`, and the checksummed `EconomyJournalSavedData`. Legacy internal calls now use the coordinator through `CoordinatedEconomyProvider`; a selected public provider uses `ExternalLegacyEconomyProvider`. The coordinator refuses capability incomplete mutations before journal intent, preserves duplicate results by request identity, freezes ambiguous outcomes, and performs receipt lookup during recovery. Durable `EconomyCustodySavedData` and `EconomyClaimSavedData` now preserve item custody and offline proceeds with checksummed state transitions and clean markers. Server shop buy and sell paths now preflight through the coordinator and secure a delivery entitlement before the external debit or credit. Player shop money legs and settlement claims now use the coordinator, while exact player item custody, owner proceeds, compensation, presentation, and security hardening remain unfinished work in this phase.
