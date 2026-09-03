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

## Routing refresh after `9a20579`

The direct mutation scan was rerun after the coordinator routing changes. No production caller invokes `EconomyProvider.withdraw`, `EconomyProvider.deposit`, or `EconomyProvider.transfer` through a provider handle. The only remaining provider mutation call is inside `EconomyTransactionCoordinator`, after lifecycle admission, capability preflight, durable intent, and pending state persistence.

`BalanceManager.withdraw`, `deposit`, and `transfer` now create coordinator requests and map typed results to the legacy transaction result without exposing a provider bypass. `ShopModAPI` uses those routes for public mutations and uses `queryBalance` for its legacy balance method. `PayCommand` therefore reaches the durable transfer coordinator. Administrative add and remove operations use the same routes. Deposit, withdrawal, and money item confirmation messages use a typed balance query when a receipt does not include a resulting balance and render an explicit unavailable component instead of reading a legacy balance or substituting zero.

The source regression tests in `ShopModAPISafetySourceTest` and `EconomyMoneySafetySourceTest` prove the public and administrative mutation routes, reject legacy provider mutation strings, and reject legacy balance fallbacks in bill and command paths. `codegraph status .` reports an up to date index after the refresh. Full unit tests, build, four GameTests, bounded dedicated server startup, bounded client startup, jar integrity, and the no optional provider class scan passed for source commit `9a20579`.

The follow up scan for `9638572` also found no gameplay or command consumer reading the legacy provider handle for currency metadata. Commands, money items, shop data, and analytics now receive currency name and decimal precision through `BalanceManager` accessors, leaving the provider handle itself at the compatibility API boundary and coordinator internals.

## Presentation balance regression

`ShopClientStateTest.unavailableBalanceNeverSubstitutesZero` now proves that an unavailable server balance clears the availability flag without rewriting the last observed minor-unit value. Screens therefore render the localized unavailable state rather than treating a provider failure as a zero balance. Focused and complete Gradle test suites and the build passed after this test was added.

## Settlement claim preflight refresh

The player shop settlement path now calls `previewClaim` before economy admission. Preview derives the stable claim request without writing settlement state. The path performs coordinator preflight first, then persists the claim identity with `beginClaim`, verifies that the identity is unchanged, and only then creates or replays the durable deposit claim. An unavailable or capability incomplete provider therefore leaves pending settlement data unchanged. `PlayerShopSettlementSavedDataPagingTest.previewClaimDoesNotPersistBeforeProviderPreflight` covers the read only preview and subsequent persistence boundary.

The post change verification for `4acdf44` passed. The focused settlement source and paging tests, complete `test`, and `build` all completed successfully. The real GameTest server completed all five required tests. Bounded dedicated server startup reached `FutureShops common setup complete` and `FutureShops server starting`, and bounded client startup reached `FutureShops common setup complete` without a FutureShops failure. The candidate jar passed `unzip -tq` with SHA 256 `6e22379ace7eb6432889b6d37b2142f3cf7683dee2914b5ffb47963a8e740148` and SHA 512 `abc296833db80b65ad10f2e64351188780e4daac070f215dc4f3e5baa2e1afd931b74d961e44821c1043e1be95c19a08176f26f0885d0f3c15770b0f1c9c1eb2`.

## Coordinator checkpoint

The first phase implementation checkpoint adds `EconomyLifecycleController`, `EconomyTransactionCoordinator`, and the checksummed `EconomyJournalSavedData`. Legacy internal calls now use the coordinator through `CoordinatedEconomyProvider`; a selected public provider uses `ExternalLegacyEconomyProvider`. The coordinator refuses capability incomplete mutations before journal intent, preserves duplicate results by request identity, freezes ambiguous outcomes, and performs receipt lookup during recovery. Durable `EconomyCustodySavedData` and `EconomyClaimSavedData` now preserve item custody and offline proceeds with checksummed state transitions and clean markers. Server shop buy and sell paths now preflight through the coordinator and secure a delivery entitlement before the external debit or credit. Player shop buys now also persist exact serialized sale stacks in `PlayerShopSaleEscrowSavedData`, verify physical extraction, and advance delivery through `DELIVERED` and `CLAIMED` or fail closed into `REFUNDED` or `RECOVERY_REQUIRED`. Player shop buyback now preflights both provider legs, persists exact seller stacks in the shared item escrow before removal, and keeps ambiguous debit, credit, or compensation states in `RECOVERY_REQUIRED`. The full player shop crash matrix, owner proceeds edge coverage, presentation, and security hardening remain unfinished work in this phase.

## Claim creation lifecycle gate

New offline settlement claims are now admitted only while the economy lifecycle is `READY`. The coordinator checks lifecycle admission while holding its transaction lock immediately before creating a claim, so a shutdown race cannot persist a new claim after `DRAINING` begins. Existing claims remain readable and resolvable for recovery. The player shop settlement handler catches a refused or failed claim creation and reports a server error without calling the provider or changing settlement state. `EconomyTransactionCoordinatorTest.newClaimIsRefusedDuringDrainBeforeStoreMutation` proves the claim store remains empty when draining has begun.

Verification for this refresh passed on the phase branch. `EconomyTransactionCoordinatorTest`, the complete `test` task, `build`, and the five required GameTests passed. The bounded dedicated server reached `FutureShops common setup complete` and `FutureShops server starting` before the expected timeout in `/tmp/futureshops-runserver-claim-drain-20260903.log`. The bounded Xvfb client reached `FutureShops common setup complete` before the expected timeout in `/tmp/futureshops-runclient-claim-drain-20260903.log`. Existing GeckoLib and Mixin Java 21 class version warnings remained the only client warnings. The rebuilt unpublished jar passed `unzip -tq`. Its SHA 256 is `9667aebf86034eae90e104525487fecec2699f8bef7f2eeb92e4d7cb4fa9f1c5` and its SHA 512 is `b5bdfe63afead024f5e283b71ab9e04d3ec130fdf20c7028dd115ca7362bd47f07812517be265a4daa37b0fc6a09c7e4214a313ea23e0669592f1ee94f6aa156`.

## Offline claim restart GameTest

`EconomyGameTests.offlineClaimUncleanRestartPreservesRecoveryState` now exercises the real NeoForge GameTest server with a checksummed offline proceeds claim. It verifies that an unclean save retains integrity, marks recovery as required, keeps the claim incomplete, and preserves the original request identity and pending state. The six required GameTests passed for this refresh in `/tmp/futureshops-gametest-claim-restart-20260903.log`. The rebuilt unpublished `futureshops-2.3.0.jar` passed `unzip -tq` with SHA 256 `9667aebf86034eae90e104525487fecec2699f8bef7f2eeb92e4d7cb4fa9f1c5` and SHA 512 `b5bdfe63afead024f5e283b71ab9e04d3ec130fdf20c7028dd115ca7362bd47f07812517be265a4daa37b0fc6a09c7e4214a313ea23e0669592f1ee94f6aa156`.

## Terminal receipt consistency

Terminal journal records now require status and receipt agreement. A confirmed record without a durable receipt is rejected during SavedData loading and marks the journal invalid. An in-memory or replayed terminal record with the same inconsistency freezes the lifecycle and returns `RECOVERY_REQUIRED` without another provider call. `EconomyJournalSavedDataTest.confirmedStatusWithoutReceiptIsReadOnlyAndNeverLoadedAsSuccess` and `EconomyTransactionCoordinatorTest.confirmedTerminalRecordWithoutReceiptFreezesInsteadOfReplayingRejection` cover both boundaries.

The focused journal and coordinator tests, complete `test` task, `build`, six required GameTests, bounded dedicated server smoke, and bounded client smoke passed for this refresh. The dedicated server reached `FutureShops common setup complete` and `FutureShops server starting` in `/tmp/futureshops-runserver-receipt-consistency-20260903.log` before expected timeout exit `124`. The client reached `FutureShops common setup complete` in `/tmp/futureshops-runclient-receipt-consistency-20260903.log` before expected timeout exit `124`; only existing GeckoLib and Mixin Java 21 class version warnings remained. The rebuilt unpublished jar passed `unzip -tq` with SHA 256 `c528b83546a6e97f1d9000f985350d16cc5f3051c15bb1d4792ae9b61de5a380` and SHA 512 `7e82b5182b579fa6e59e41151789a48ba773ed651f01d3cfcc499b15f38f70f55503228c29cea6fbaa4baef38bf082ae22f0c426adb50504bcc68fd81d252f49`.

The receipt consistency checkpoint was rerun after the offline claim GameTest and terminal replay hardening. The six required GameTests passed in `/tmp/futureshops-gametest-terminal-receipt-20260903.log`. The dedicated server reached both FutureShops startup markers in `/tmp/futureshops-runserver-terminal-receipt-20260903.log` and the client reached common setup in `/tmp/futureshops-runclient-terminal-receipt-20260903.log`; both ended at expected timeout exit `124` and no FutureShops failure was observed. The client retained only the existing GeckoLib and Mixin Java 21 class version warnings.

The terminal replay hardening was rerun from the same source revision. `EconomyTransactionCoordinatorTest.terminalRecordWithMismatchedReceiptFreezesDuringRecovery` passed with zero provider mutation calls for the invalid receipt. The complete `test` task and `build` task passed before the runtime checks above.

Source commit `b38c437` records an actionable error when durable settlement claim creation is refused or fails after provider preflight. The handler still returns a server error without calling the provider or changing settlement state, while the log includes the shop position, stable request identity, and exception for operator recovery. `PlayerShopSaleEscrowSourceTest.claimPersistenceFailureRecordsRecoveryContext` covers the logging and response boundary. The focused source test, complete `test` task, and `build` task passed for this change.

Source commit `11bba4e` adds `EconomyGameTests.reconnectReplayPreservesStableRequestIdentity`. The real NeoForge GameTest server confirms that a reconnect replay using the same server owned request identity returns the original receipt and leaves the authoritative balance unchanged after the first debit. All seven required GameTests passed in `/tmp/futureshops-gametest-reconnect-20260903.log`. The dedicated server reached `FutureShops server starting` in `/tmp/futureshops-runserver-reconnect-20260903.log` before the expected timeout, and the Xvfb client reached `FutureShops common setup complete` in `/tmp/futureshops-runclient-reconnect-20260903.log` before the expected timeout. The client retained only the existing GeckoLib and Mixin Java 21 class version warnings.

The rebuilt unpublished `futureshops-2.3.0.jar` for source commit `989cef3` passed `unzip -tq` and the optional provider class scan. Its SHA 256 is `51e8085466c58431fbcd2f46c6f74540d0986f39599a00b5c1c3cb58613dc18b` and its SHA 512 is `5d8fa35ce6dcded18a74bcf4715d042fc17ed4addbf9da87ac6dbade4c1bbf7ddb1392b1cd08a15265569483ded38551bcab56e6277fb014b2001d01c5227d0f`.
