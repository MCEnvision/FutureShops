# Phase 000 monetary route inventory

Observation date: 2026-09-24
Phase: CORE-PHASE-000
Task: P000-TASK-002
Requirement: CORE-REQ-001
Interface: CORE-IF-007

## Reading this inventory

Each row starts at a concrete Forge command, packet, API, lifecycle callback, or authoritative service. `Z0` is the required zero effect refusal oracle: return a typed refusal before any wallet, provider, ledger, item, stock, liability, claim, event, analytics, or success response changes. Every later route test must exercise this oracle for invalid input, unavailable provider, unsupported account, stale revision, permission denial, and binding conflict where applicable.

The route inventory is a coverage contract, not a claim that the current beta.2 implementation already satisfies it. `CORE-PHASE-005` through `CORE-PHASE-010` own the real mutation, fault, restart, multiplayer, and client proofs. Direct WAL lanes are listed separately from provider selection so a helper cannot hide an internal balance effect.

## Route records

| Route ID | Concrete Forge entrypoint | Provider and payment modes | Value and item legs | Custody or liability owner | Direct WAL or durable lane | Refusal oracle | Later owner and regression |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ROUTE-001 | `BalanceCommand` and `BalanceManager.getBalance` | Internal wallet query, selected provider query | No mutation, one account read | Economy coordinator | `EscrowWalletService` query and `WalletLedgerBackend.balance` | Z0 | P005-TASK-001, REG-P000-ROUTE-001 |
| ROUTE-002 | `BalTopCommand` and `BalanceManager.getTopBalances` | Internal leaderboard query | No mutation, ordered account reads | Economy coordinator | `WalletLedgerBackend.snapshotBalances` | Z0 | P005-TASK-001, REG-P000-ROUTE-002 |
| ROUTE-003 | `ShopAdminCommand` administrative add, remove, set, reset, and transfer | Internal only unless a bound provider proves the operation | One or two account legs, no item leg | Administrative balance backend and audit | `LiveAdministrativeBalanceBackend` and ledger transaction commit | Z0 | P005-TASK-001, REG-P000-ROUTE-003 |
| ROUTE-004 | `ShopModAPI` public balance and mutation calls | Internal API, selected provider API | Account and amount legs | Economy coordinator | API request root and ledger commit | Z0 | P005-TASK-001, REG-P000-ROUTE-004 |
| ROUTE-005 | `PayCommand` and `PlayerPaymentService.pay` | Internal wallet, external mutation only after provider gate | Sender debit and recipient credit | Payment service and original bindings | `PlayerPaymentService` root, `debitLegs`, `creditLegs` | Z0 | P005-TASK-002, REG-P000-ROUTE-005 |
| ROUTE-006 | `PayCommand` status and `PlayerPaymentService.status` | Replay and recovery lookup | No new effect, one root lookup | Recovery handler | `PlayerPaymentRecoveryHandler` and stored root | Z0 | P003-TASK-003, REG-P000-ROUTE-006 |
| ROUTE-007 | `MarketAdminCommand` fee, refund, maintenance, and repair paths | Internal administrative effects, external effects gated | Fee debit, refund credit, or claim liability | Admin audit and escrow runtime | `EscrowRuntimeService` administrative commit | Z0 | P005-TASK-002, REG-P000-ROUTE-007 |
| ROUTE-008 | `MarketplaceAnalyticsService` completion and adjustment records | Follows the confirmed route provider | No independent money leg, event reference only | Analytics service | Completion event after durable terminal state | Z0 | P005-TASK-002, REG-P000-ROUTE-008 |
| ROUTE-009 | Balance, money, shop, market, and barter events | Follows originating route | Event adjustment or cancellation only after admission | Event dispatcher | Existing event and commit ordering | Z0 | P005-TASK-002, REG-P000-ROUTE-009 |
| ROUTE-010 | `InternalEconomyProvider` and legacy provider wrappers | Forge internal provider | Account read and internal account mutation | Internal provider and coordinator | `EscrowWalletService` and `WalletLedgerBackend.commit` | Z0 | P005-TASK-001, REG-P000-ROUTE-010 |
| ROUTE-011 | `ServerShopPurchaseService.purchase` and `execute` | Wallet, physical internal funding, pure free, external gated | Wallet debit, item output claims, stock reservation | Server shop custody and item claims | `ServerShopPurchaseCommit`, `EscrowRuntimeService`, ledger transaction | Z0 | P006-TASK-001, REG-P000-ROUTE-011 |
| ROUTE-012 | `ServerShopOfferCartService` cart checkout | Wallet, physical funding, free and barter lines, mixed money gated | Stable child money legs, item claims, per-line stock | Cart reservation and child transactions | Parent root plus stable child WAL commits | Z0 | P006-TASK-001, REG-P000-ROUTE-012 |
| ROUTE-013 | `ServerShopOfferService` offer checkout and replay | Wallet, pure barter, free, mixed money gated | Offer money leg and normalized item components | Offer escrow fanout | `OfferEscrowFanout` and `EscrowRuntimeService` | Z0 | P006-TASK-002, REG-P000-ROUTE-013 |
| ROUTE-014 | `ServerShopSellService.sell` | Wallet credit, external provider gated | Seller item custody, shop wallet credit | Item extraction and payout liability | Sell transaction root and ledger credit | Z0 | P006-TASK-001, REG-P000-ROUTE-014 |
| ROUTE-015 | `ServerShopBarterService.barter` | Pure barter, mixed money gated | Input item lots and output item claims | Barter custody | `ServerShopBarterIntent` and item transaction | Z0 | P006-TASK-002, REG-P000-ROUTE-015 |
| ROUTE-016 | `BulkSellService.quote` and `commit` | Internal wallet payout, external gated | Each eligible inventory stack, one payout leg per line | Bulk sell custody planner | Bulk sell root and line ledger commits | Z0 | P006-TASK-002, REG-P000-ROUTE-016 |
| ROUTE-017 | `SellAllCommand` and bulk sell packets | Player shop or admin shop target, internal wallet | Inventory snapshot, excluded stacks, total payout | `BulkSellService` and inventory executor | Quote revision and commit root | Z0 | P006-TASK-002, REG-P000-ROUTE-017 |
| ROUTE-018 | Normalized offer option selection | Free, barter only, money only, money or barter, money and barter, sell only | Option-specific input and output legs | Offer validation and custody | Offer revision and option transaction | Z0 | P006-TASK-002, REG-P000-ROUTE-018 |
| ROUTE-019 | Bundle and savings calculation | Money, barter, or mixed bundle gated by provider | Multiple output components and one composite payment | Bundle component normalizer | `OfferBundleComparison` and offer root | Z0 | P006-TASK-002, REG-P000-ROUTE-019 |
| ROUTE-020 | `ShopBuyService` legacy buy | Internal wallet and free mode | Exact output stack, stock decrement, wallet debit | Legacy shop transaction | Existing shop transaction WAL | Z0 | P006-TASK-001, REG-P000-ROUTE-020 |
| ROUTE-021 | `ShopSellService` legacy sell | Internal wallet payout | Exact input stack, wallet credit | Legacy shop transaction | Existing shop transaction WAL | Z0 | P006-TASK-001, REG-P000-ROUTE-021 |
| ROUTE-022 | `ShopBarterService` legacy barter | Pure item barter | Exact input and output item stacks | Legacy barter custody | Existing barter transaction WAL | Z0 | P006-TASK-002, REG-P000-ROUTE-022 |
| ROUTE-023 | `PlayerShopLiveEscrowService` live buy and sell | Wallet, barter, mixed money gated | Buyer debit, seller credit, item transfer | Player shop live escrow | Player shop atomic commit and runtime ledger | Z0 | P006-TASK-003, REG-P000-ROUTE-023 |
| ROUTE-024 | `PlayerShopSettlementEscrowService` settlement | Internal or original bound provider | Proceeds, fees, item claims | Settlement escrow and original binding | Settlement root and claim records | Z0 | P006-TASK-003, REG-P000-ROUTE-024 |
| ROUTE-025 | `PlayerShopEscrowTransactionService` storage and buyback | Wallet, barter, sell only, mixed money gated | Storage item custody and payout legs | Player shop storage adapter | Player shop transaction receipt pair | Z0 | P006-TASK-003, REG-P000-ROUTE-025 |
| ROUTE-026 | `RefinedStorage2StorageAdapter` exact custody | No provider mutation by adapter | Stored item extraction and return | Adapter receipt and player shop | Item mutation receipt, no shadow wallet | Z0 | P006-TASK-003, REG-P000-ROUTE-026 |
| ROUTE-027 | `EscrowMoneyClaimService` collect | Original provider and currency binding only | Claim balance debit, player wallet credit | Durable money claim | Claim account and collection attempt WAL | Z0 | P006-TASK-004, REG-P000-ROUTE-027 |
| ROUTE-028 | `ExactItemClaimLoginCollectionHandler` login delivery | No money provider needed | Exact item payload and quantity | Item claim custody | Delivery attempt receipt | Z0 | P006-TASK-004, REG-P000-ROUTE-028 |
| ROUTE-029 | `MarketClaimCollectionService` money, item, and cash claims | Original market provider binding | Claim payout, item payload, or internal cash | Market claim center | Claim collection root and delivery attempts | Z0 | P007-TASK-003, REG-P000-ROUTE-029 |
| ROUTE-030 | `BazaarActionService.registerProduct` | Provider funding selected before mutation | Product metadata and optional sell inventory | Bazaar product catalog | Bazaar mutation commit | Z0 | P007-TASK-001, REG-P000-ROUTE-030 |
| ROUTE-031 | `BazaarActionService.order` buy order and matching | Wallet or internal physical funding | Buyer hold, order liability, product lot, matching fill | Bazaar hold and order book | `BazaarEscrowLifecycleRepository` and ledger root | Z0 | P007-TASK-001, REG-P000-ROUTE-031 |
| ROUTE-032 | `BazaarActionService.order` sell order | Seller inventory and proceeds binding | Seller item lot and proceeds claim | Bazaar lot custody | Bazaar lot and order commit | Z0 | P007-TASK-001, REG-P000-ROUTE-032 |
| ROUTE-033 | `BazaarActionService.cancel` | Original order binding | Release remaining hold or lot | Bazaar cancellation | Cancellation mutation and claim | Z0 | P007-TASK-001, REG-P000-ROUTE-033 |
| ROUTE-034 | `BazaarExpirationScheduler` | Original provider and order binding | Expired hold, lot, fee, and refund | Expiration scheduler | Durable expiration transition | Z0 | P007-TASK-001, REG-P000-ROUTE-034 |
| ROUTE-035 | Bazaar partial fill and claim collection | Original provider and currency | Partial buyer debit, seller credit, remaining hold | Market claim center | Fill root, child legs, and claims | Z0 | P007-TASK-001, REG-P000-ROUTE-035 |
| ROUTE-036 | `AuctionActionService.create` | Listing fee provider and seller binding | Item custody and listing fee | Auction escrow | Auction listing root | Z0 | P007-TASK-002, REG-P000-ROUTE-036 |
| ROUTE-037 | `AuctionActionService.bid` and bids | Bidder wallet or internal funding | Bid hold, previous bid release, item liability | Auction bid escrow | Bid root and hold ledger | Z0 | P007-TASK-002, REG-P000-ROUTE-037 |
| ROUTE-038 | `AuctionActionService.buyNow` | Buyer wallet and seller original binding | Buyer debit, seller proceeds, item delivery | Auction settlement escrow | Buyout composite commit | Z0 | P007-TASK-002, REG-P000-ROUTE-038 |
| ROUTE-039 | `AuctionActionService.cancel` | Seller original binding and policy | Release item or held bid | Auction custody | Cancellation and release transaction | Z0 | P007-TASK-002, REG-P000-ROUTE-039 |
| ROUTE-040 | `AuctionExpirationScheduler` | Original listing and bid bindings | Expiry item return, winner settlement, refunds | Auction expiration scheduler | Expiration WAL and claims | Z0 | P007-TASK-002, REG-P000-ROUTE-040 |
| ROUTE-041 | Auction outbid refund and fee | Original bidder provider | Refund hold and listing fee | Auction liability ledger | Child refund leg, never shadow credit | Z0 | P007-TASK-002, REG-P000-ROUTE-041 |
| ROUTE-042 | Auction seller proceeds and collection | Original seller provider and currency | Proceeds claim and item claim | Market claim center | Proceeds claim account | Z0 | P007-TASK-003, REG-P000-ROUTE-042 |
| ROUTE-043 | `EscrowCashDepositService` and `DepositCommand` | Internal protected or configured foreign physical currency | Item removal, cash claim, wallet credit | Cash custody and mint authority | Cash deposit root and claim account | Z0 | P005-TASK-003, REG-P000-ROUTE-043 |
| ROUTE-044 | `EscrowAtmWithdrawalService` and `WithdrawCommand` | Internal protected or configured foreign physical currency | Wallet debit, bill delivery, pending withdrawal | ATM withdrawal escrow | Withdrawal root and delivery receipt | Z0 | P005-TASK-003, REG-P000-ROUTE-044 |
| ROUTE-045 | `AtmCashClaimCenter.collect` and `C2SAtmCollectCashPacket` | Internal cash only | Pending bills and exact item delivery | Cash claim center | Claim attempt and item receipt | Z0 | P005-TASK-003, REG-P000-ROUTE-045 |
| ROUTE-046 | `MarketPhysicalFundingService` | Internal physical currency only | Funding bill custody and market hold | Physical funding evidence | Funding claim and market root | Z0 | P005-TASK-003, REG-P000-ROUTE-046 |
| ROUTE-047 | `MoneyMintService`, `MoneyValidationService`, and `MoneyItem` | Internal mint only | Minted or redeemed bills, checksum and spent mint | Protected mint authority | Mint record and spent mint saved data | Z0 | P005-TASK-003, REG-P000-ROUTE-047 |
| ROUTE-048 | `LegacyBalanceMigrationManager` and `LiveEscrowWalletInitializationGateway` | Existing internal wallet only | Legacy balance to ledger account | Migration manager | Migration batch and initialization barrier | Z0 | P009-TASK-001, REG-P000-ROUTE-048 |
| ROUTE-049 | `MarketAdminCommand` maintenance and recovery processor | No new value effect until verified | Root freeze, repair, and audit reason | Maintenance and recovery authority | Existing journal sequence and repair record | Z0 | P009-TASK-003, REG-P000-ROUTE-049 |
| ROUTE-050 | Server packet handlers and `ClientRouteGuard` | Server selected provider and session binding | Authenticated request, revision, amount, item selection | Server handler and client state | Request root and response identity | Z0 | P008-TASK-001, REG-P000-ROUTE-050 |
| ROUTE-051 | `CatalogStockCutoverCoordinator` and `DurableCatalogStockAuthority` | No independent money effect | Stock seed, revision, and migration state | Catalog stock authority | Stock migration durability barrier | Z0 | P009-TASK-001, REG-P000-ROUTE-051 |
| ROUTE-052 | `AdminBulkListingService` and admin bulk packets | Admin configured offer modes | Listing creation, stock, price, and item definitions | Admin catalog custody | Bulk replay saved data and catalog revision | Z0 | P006-TASK-002, REG-P000-ROUTE-052 |
| ROUTE-053 | ATM, market, claim, and shop client response trackers | No client authority to mutate value | Response identity, replay state, and UI projection | Server authoritative result | Server response request UUID and revision | Z0 | P008-TASK-002, REG-P000-ROUTE-053 |

## Direct wallet and provider coverage

CodeGraph identified 51 `getProvider` symbol leads. A current source scan identified 58 call lines across 28 files. Both counts are retained because they measure different units. Direct ledger scan identified 39 lines containing `commitLedger`, `debitLegs`, `creditLegs`, `transfer`, or `setBalanceLegs`. The following direct lanes are mandatory independent coverage even when no provider lookup appears in the caller.

| Direct lane | Current Forge owner | Required proof |
| --- | --- | --- |
| Runtime wallet query and commit | `EscrowWalletService`, `RuntimeWalletLedgerBackend`, `EscrowRuntimeService` | Root and leg identity, idempotent transaction, restart replay, and zero effect refusal. |
| Administrative balance backend | `LiveAdministrativeBalanceBackend`, `ShopAdminCommand` | Permission, confirmation, two account binding, event ordering, and compensation fault cuts. |
| Player payment | `PlayerPaymentService`, `PlayerPaymentRecoveryHandler` | Sender and recipient original binding, durable receipt, duplicate request, and unknown outcome freeze. |
| Server shop purchase | `ServerShopPurchaseService`, `ServerShopOfferService`, `ServerShopOfferCartService` | Stable parent and child legs, item reservations, physical funding, and replay. |
| Server shop sell and bulk sell | `ServerShopSellService`, `BulkSellService`, `SellAllCommand` | Exact stack extraction, no double counting, finite stock and inventory overflow recovery. |
| Player shop settlement | `PlayerShopLiveEscrowService`, `PlayerShopSettlementEscrowService` | Offline owner, storage adapter, original provider restoration, and claim delivery. |
| Bazaar | `BazaarActionService`, `BazaarExpirationScheduler` | Holds are liabilities, partial fills, cancellation and expiry conserve value. |
| Auction | `AuctionActionService`, `AuctionExpirationScheduler` | Listing fee, bid holds, outbid refunds, buyout, expiry, and claims conserve value. |
| Cash and ATM | `EscrowCashDepositService`, `EscrowAtmWithdrawalService`, `AtmCashClaimCenter` | Internal protected cash works, external selection refuses before item or account effect. |
| Funding and migration | `MarketPhysicalFundingService`, `LegacyBalanceMigrationManager`, `CatalogStockCutoverCoordinator` | Funding claims and migration barriers are durable, bounded, and restart safe. |

## Coverage conclusion and proof limit

All master checklist route families have a concrete Forge entrypoint, payment mode matrix, value and item legs, custody or liability owner, direct durable lane, refusal oracle, later task, and stable regression identity. Several CodeGraph services report no covering tests; those are explicit later obligations. This inventory proves route ownership and zero effect test requirements only. It does not prove current behavior, provider support, atomic persistence, item conservation, multiplayer synchronization, or client rendering.
