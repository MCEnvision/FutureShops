# FutureShops 3.0 Plan

Status. Implementation complete on the beta phase branch and pending approval.

This document is the implementation contract for FutureShops 3.0. It covers the shared escrow foundation, migration of all existing value moving features, the Auction House, the Bazaar, the shared market interface, configuration, recovery, abuse controls, testing, and release sequencing.

The required build order is.

1. Freeze and verify the regression baseline for all community bug reports.
2. Build mandatory durable escrow and the shared money ledger.
3. Migrate every existing value moving operation onto escrow.
4. Extract the current shop interface into a reusable themed market shell.
5. Build the Auction House.
6. Build the Bazaar and matching engine.
7. Complete crash, migration, abuse, performance, and interface hardening.

Auction House and Bazaar transaction code must not ship before escrow protects the existing economy. Adding long lived bids and orders to the current best effort rollback model would multiply the existing crash windows.

## 1. Non negotiable requirements

- Escrow is mandatory for every operation that moves money, items, stock, barter assets, or claims.
- Escrow cannot be disabled while value moving features are available.
- Bazaar and Auction House are independently toggleable in the main config.
- Disabling a module never deletes listings, orders, bids, history, custody, or claims.
- Claims, refunds, cancellation, and administrative recovery remain available while a module is disabled.
- The client never supplies trusted prices, fees, ownership, stock, deadlines, balances, or item identity.
- Every mutating request has a request UUID and can produce at most one economic result.
- Inventory overflow never drops or deletes value. It creates or retains a claim.
- Maximum wallet balance never deletes proceeds. Money remains claimable.
- Every monetary calculation uses integer minor units and exact overflow checked arithmetic.
- Active listings and orders snapshot the applicable rules. Config reload cannot alter a live contract retroactively.
- Semantic status colors remain separate from module themes.
- Foreign mod currency uses escrow request safety but receives no FutureShops physical currency duplication guarantee.
- Newer, corrupt, or unverifiable custody data fails closed.
- No value bearing record is archived while it still owns or owes an asset.

## 2. Target architecture

FutureShops Core owns shared safety and infrastructure. Shop, Auction House, Bazaar, ATM, deposit, withdrawal, and pay become domain clients of that core.

```text
Shop screens and commands
Auction House screens and commands
Bazaar screens and commands
ATM, deposit, withdrawal, and pay
             |
             v
      Domain transaction plans
             |
             v
        Escrow coordinator
             |
     +-------+--------+
     |                |
 Money ledger      Asset vault
     |                |
 Wallets          Exact item stacks
 Holds            Protected bills
 Claims           Foreign currency
 Fees             Barter assets
 Sinks            Stock reservations
     |                |
     +-------+--------+
             |
        Claim center
             |
     Automatic delivery
     Lost and Found screen
     Administrative recovery
```

Core responsibilities.

- Durable escrow journal and recovery.
- Double entry money ledger.
- Item and physical currency custody.
- Claims and Lost and Found.
- Transaction IDs and idempotency.
- Shared market interface and themes.
- Module lifecycle and public capability synchronization.
- Packet bounds and request rate limits.
- Audit events and history projection.
- Permission helpers.

Module responsibilities.

- Shop owns catalog and player shop business rules.
- Auction House owns listings, bids, expiration, settlement, and moderation.
- Bazaar owns products, orders, matching, fills, and market statistics.

## 3. Current risks addressed by escrow

The existing code has strong server validation and careful best effort rollback, but most rollback receipts live only in memory. Minecraft SavedData being dirty does not force a durable write at the transaction boundary.

The escrow migration must address the following known risks.

- Internal balances have no holds, transaction IDs, or applied operation index.
- Payment receipts exist only in memory.
- Protected bill deposits can consume mint capacity before wallet credit succeeds.
- Foreign currency deposits can credit value before the ordinary stack is durably removed.
- ATM debit, minting, delivery, and refund are not restart safe.
- Player shop settlement claims clear pending revenue before wallet credit.
- Player shop settlements omit dimension from shop identity.
- Reused shop coordinates can confuse ownership of old pending proceeds.
- Admin shop stock is runtime state rather than durable reserved stock.
- Shop purchase and sale paths have several crash boundaries between payment, stock, item delivery, and rollback.
- Player shop money purchases can record seller settlement before seller output is secured.
- Barter payment can enter owner storage before seller output is secured.
- Buyback can insert seller items before owner money and seller payout are committed.
- External storage adapters lack transaction IDs and durable mutation receipts.
- Partial adapter insertion and extraction can be mistaken for all or nothing behavior.
- Several calculations require exact overflow checks.
- Event modified prices require consistent post event validation.
- Transaction history has no transaction ID and cannot safely deduplicate replay.
- Administrative balance changes can bypass ordinary provider rules.
- Newer SavedData schemas currently warn rather than always failing closed.

## 4. Shared escrow system

### 4.1 Transaction record

Every transaction contains.

- Transaction UUID.
- Optional parent UUID for listing, order, fill, or claim trees.
- Client request UUID or deterministic idempotency key.
- Module and operation type.
- Initiator, payer, beneficiary, seller, buyer, and system account roles.
- Exact shop reference including dimension and block position.
- Creation time, update time, server tick, schema version, and revision.
- Config revision and immutable price, fee, and rule snapshot.
- Source assets.
- Expected destination assets.
- Money ledger legs.
- Stock and storage reservations.
- Retry count, next retry time, and last failure.
- Protection level for each asset.
- Journal sequence references.
- Administrative recovery reason when applicable.

Representative operation types.

- SERVER_SHOP_BUY.
- SERVER_SHOP_SELL.
- SERVER_SHOP_BARTER.
- PLAYER_SHOP_BUY.
- PLAYER_SHOP_BARTER.
- PLAYER_SHOP_COMPOUND.
- PLAYER_SHOP_BUYBACK.
- ATM_WITHDRAW.
- CURRENCY_DEPOSIT.
- PLAYER_PAY.
- AUCTION_LISTING.
- AUCTION_BID.
- AUCTION_SETTLEMENT.
- BAZAAR_ORDER.
- BAZAAR_FILL.
- CLAIM.
- ADMIN_BALANCE_MUTATION.

### 4.2 State machine

```text
CREATED
  to VALIDATED
  to HOLDING
  to HELD
  to COMMIT_DECIDED
  to COMMITTED
  to CLAIMS_CREATED
  to COMPLETED

VALIDATED, HOLDING, or HELD
  to ABORTING
  to REFUND_PENDING
  to REFUNDED

Any unresolved state
  to RECOVERY_REQUIRED
  to resumed normal processing or MANUAL_REVIEW
```

COMMIT_DECIDED is the durable ownership boundary.

- Before COMMIT_DECIDED, recovery restores assets to original owners.
- After COMMIT_DECIDED, ownership has changed and recovery completes delivery to new owners.
- Delivery failure never erases a committed sale. It leaves a claim.
- Each input asset has its own held state so partial acquisition can be recovered.
- Invalid transitions fail closed.

### 4.3 Durable journal

A new SavedData class alone is insufficient. The implementation requires an append only write ahead journal.

- Records are framed with length, schema, journal sequence, transaction ID, step ID, and checksum.
- Critical records are forced to disk before the associated side effect.
- Checkpoints store materialized state and the last applied journal sequence.
- Startup loads the latest verified checkpoint and replays subsequent records.
- A truncated final record may be discarded without losing valid earlier records.
- Checksum mismatch, disk failure, or unsupported newer schema enters economy maintenance mode.
- New value moving operations stop in maintenance mode.
- Safe browsing, inspection, claims, and recovery remain available when writes are safe.
- Journal compaction happens only after a verified checkpoint.
- Clean shutdown blocks new transactions, drains current work, forces the journal, and checkpoints.

### 4.4 Money ledger

The internal balance store evolves into a double entry ledger or equivalent atomic account model.

Account types.

- Player available wallet.
- Player reserved funds.
- Transaction escrow.
- Player money claim.
- Protected physical currency outstanding.
- Foreign currency source and sink.
- Server shop source and sink.
- Bazaar fee account.
- Auction fee account.
- Optional server treasury.
- Administrative source and sink.

All ledger operations balance. Only explicit system mint or sink operations create or destroy value.

All calculations use exact addition, subtraction, and multiplication. Percentage fees use basis points and one explicit rounding policy.

### 4.5 Asset custody

Escrow asset types.

- Wallet money.
- Protected FutureShops physical money.
- Foreign physical currency.
- Ordinary exact item stacks.
- Barter bundles.
- Admin catalog stock reservations.
- Player shop storage reservations.
- Auction listing payloads.
- Bazaar commodity lots.
- Fees and taxes.
- Explicit system sources and sinks.

Each item lot stores.

- Lot UUID.
- Previous and intended owner.
- Complete serialized item stack.
- Registry ID.
- Count.
- Full NBT and Forge capability data.
- Fingerprint.
- Original source.
- Protection tier.
- Allocated and unallocated quantities.
- Claim status.
- Raw fallback data if the item registry entry disappears.

Items are never reconstructed from only registry ID and count. Damage, enchantments, names, lore, contents, and mod specific data must survive custody.

### 4.6 Protection tiers

Protected assets include the internal wallet, FutureShops money, FutureShops vaults, and claims. These receive journal, mint ledger, idempotency, and recovery protection.

Reconciled assets include player inventory and supported vanilla storage. These receive exact stack custody plus preimage and postimage reconciliation.

External assets include foreign currency and unsupported third party storage. FutureShops prevents duplicate processing of its own requests, but cannot guarantee the external item supply or storage implementation.

Auction House and Bazaar first release listing items come from player inventory into a FutureShops controlled vault. Direct listing from external storage waits until that adapter can provide deterministic receipts.

### 4.7 Foreign currency warning

The generated TOML warning is.

```toml
# WARNING. Changing the currency provider from futureshops disables all FutureShops physical currency duplication protection. Currency items from other mods are spawned and accepted without mint ids, checksums, or spent mint tracking.
```

Mint checksums and spent mint protection are disabled for foreign currency. Escrow request deduplication remains active. The foreign mod remains responsible for crafting, loot, creative access, duplication bugs, and currency supply.

### 4.8 Protected bills

The mint ledger gains explicit authorized, available, reserved, spent, refunded, and quarantined states.

- Each stack uses one immutable mint batch ID, denomination, authorized count, and checksum. This matches Minecraft stack NBT semantics and avoids pretending that indistinguishable items in one stack carry separate identities.
- The ledger tracks exact quantities in authorized, available, reserved by transaction, spent by transaction, refunded, and quarantined buckets. Their sum must always equal the batch authorized count.
- Splitting and merging stacks preserves the same batch ID and checksum while the ledger caps their aggregate usable quantity. Stacks from different mint batches never merge unless the operation first consumes them and journals a replacement batch.
- Holding protected bills reserves an exact quantity from each mint batch for one transaction.
- Commit marks the reserved quantities spent.
- Refund returns protected value using a journaled replacement mint batch ID and exact count when needed. Spent batch quantities are never made available again.
- ATM mint plans, mint batch IDs, authorized counts, denominations, and checksums are persisted before item creation.
- Recovery reuses the same plan and does not mint a second unrelated batch.
- Custody provenance records the batch ID and exact bill count represented by each stack.
- A stack is rejected or quarantined when its count exceeds the batch quantity still available to that location, its checksum is invalid, its denomination differs, or its batch is spent, unknown, or reserved elsewhere.
- A protected bill does not expire while held in escrow.
- Long lived bid and order payments become escrow monetary value with an explicit refund policy.

### 4.9 Claims and Lost and Found

Access points.

- The escrow command.
- Claim and Claim All commands.
- Profile and balance screens.
- Claims tab in Shop, Auction House, and Bazaar.
- Shared header notification badge.

Claim types.

- Money.
- Items.
- Protected cash.
- Exact foreign cash.
- Auction winnings.
- Auction refunds.
- Expired auction items.
- Bazaar purchased goods.
- Bazaar proceeds.
- Price improvement refunds.
- Player shop proceeds.
- Barter proceeds.

Claim rules.

- Claims are idempotent.
- Full inventory leaves undelivered items claimable.
- Maximum wallet balance leaves money claimable.
- Offline players retain claims.
- Claim All is bounded and reports partial success.
- Module disable never blocks ownership claims.
- Permission revocation cannot confiscate claims.
- Unknown items remain quarantined with raw serialized data.
- Automatic delivery is a convenience attempt rather than the source of truth.

## 5. Existing flow migration

Every current flow migrates before Auction House or Bazaar launches.

### Server shop buy

Hold payment, reserve durable stock, commit payment to the system sink, create the exact output claim, and attempt delivery.

### Server shop sell

Hold exact player items, reserve stock capacity, commit items to stock or sink, create payout, and attempt wallet claim.

### Server shop barter

Hold every ingredient and reserve every output before one commit decision. Bundles remain atomic.

### Server shop cart

Use one parent transaction with child lines. Default to atomic all or nothing behavior.

### Player shop purchase

Snapshot the beneficiary, hold buyer payment, secure seller output, commit seller proceeds and buyer item claim, then attempt delivery.

### Player shop barter and compound trade

Hold buyer money when present, buyer barter assets, and seller output before one swap commit. Owner barter proceeds become an item claim by default rather than being inserted before commit.

### Player shop buyback

Hold owner money and seller items, then commit money to seller and items to owner claim or post commit storage delivery.

### ATM and withdrawal

Hold wallet value, persist denomination and mint plan, commit mint liability, create cash claim, and attempt inventory delivery.

### Deposit and right click deposit

Hold exact physical stacks, validate maximum balance, and commit physical consumption with wallet credit using the same durable decision.

### Pay

Hold sender funds and commit recipient wallet credit or claim.

### Settlement claim

Treat settlement as an existing claim. Wallet failure leaves the claim unchanged.

### Administrative balance changes

Use journaled administrative source or sink entries. Do not overwrite unrelated holds.

Additional migration requirements.

- Split player shop transaction logic from interface configuration logic.
- Make shop identity dimension aware and stable across owner changes.
- Make catalog stock persistent with reservations.
- Prevent stock refresh from overwriting reservations.
- Convert the current purchase payment service into a custody adapter.
- Allow cancellation only before custody. Post hold events are non cancellable and carry transaction IDs.

## 6. Payment source selection

Every purchase or money commitment shows a source confirmation unless server policy selects one.

Sources.

- Wallet balance.
- Inventory cash using the active provider.
- Optional automatic priority policy.
- Configurable wallet or physical payment with an explicit per-purchase choice.

The confirmation displays.

- Purchase or hold amount.
- Wallet balance.
- Available physical value.
- Exact representability.
- Provider name.
- Protected or unprotected indicator.
- Denomination breakdown.
- Fees and taxes.
- Physical remainder destination.
- Expected output.
- Quote expiration and changed price warning.

Policies.

- PROMPT is the default.
- WALLET_ONLY.
- PHYSICAL_ONLY.
- PREFER_WALLET.
- PREFER_PHYSICAL.
- Optional MIXED.

Long lived bids and buy orders consume physical money into escrow at placement. It is never merely checked and left accessible.

Recommended partial fill policy.

- Wallet refunds return to wallet claim.
- Protected physical change defaults to wallet claim.
- Foreign physical refund returns exact stacks only when representable.
- Otherwise unused value returns to wallet claim after explicit confirmation.
- Seller proceeds default to wallet claims regardless of buyer source.

## 7. Shared market interface

The current Shop layout is generalized rather than copied.

Every module uses.

- Header.
- Module switcher.
- Module local tabs.
- Search.
- Balance pill.
- Claims and profile pill.
- Close button.
- Breadcrumb.
- Left category rail.
- Toolbar and segmented filters.
- Grid, list, or master detail content.
- Contextual footer.
- Tooltips and keyboard navigation.
- Responsive compact layout.

Default module identity.

- Shop keeps Nocturne blurple with primary accent 9184D9.
- Bazaar uses emerald green with primary accent 48B978.
- Auction House uses crimson red with primary accent D85B68.

Themes change identity accents, selected surfaces, active borders, calls to action, header gradient, module icon, and focus highlight. They do not replace semantic success, warning, error, information, currency, or disabled colors.

### Navigation rules

- X always closes the complete interface.
- Back always includes an arrow and restores the exact prior route.
- Detail screens preserve query, page, filter, sorting, category, selection, and scroll.
- Escape returns from detail and closes a root screen.
- Switching modules closes any block bound shop session.
- Delayed packets cannot reopen a closed interface.
- No redundant Storefront and Close behavior.
- Disabled modules with claims appear as Claims Only.

Navigation uses explicit market routes and request nonces rather than arbitrary parent screen chains.

### Responsive behavior

Wide layout shows full brand, module chips, search, account controls, sidebar, and multiple columns.

Medium layout uses compact brand and tabs while preserving search.

Narrow layout uses a category drawer, one column rows, a secondary tab row, and child detail screens.

Required tests include 1280 by 720, 1920 by 1080, GUI scale 2, 3, and 4, long translations, high contrast, keyboard only use, reduced motion, and colorblind presets.

### Client customization

- Theme presets and accent overrides.
- High contrast.
- Colorblind modes.
- Reduced motion.
- Animation speed.
- Sound and volume.
- Compact, normal, and comfortable density.
- Card sizing.
- Remembered tab, filter, and sorting.
- Remembered payment source per session.
- 12 or 24 hour timestamps.
- Search behavior.
- Confirmation preferences.

Accessibility overrides always win over server branding.

## 8. Auction House

Commands include the short and long Auction House commands plus create, mine, bids, and claims routes.

### Screens

The Auction House uses the exact shared Nocturne shell used by `/shop`. Server Shop, Player Shops,
Bazaar, and Auction House remain in one stable top navigation row with the same blurple palette,
logo, wordmark, search, balance, profile, and close controls. A client theme override recolors the
whole marketplace consistently instead of assigning a different color to each module.

Tabs.

- Browse.
- My Auctions.
- My Bids.
- Watchlist.
- Claims.
- History.

Categories and saved filters live in the left rail. Sorting includes ending soon, newest, lowest price, highest current bid, most bids, seller, price range, enchantment, exact variant, mod ID, and category.

Listing cards show the exact item, seller name and skin head, buy now or current bid, minimum next
bid, bid count, remaining time, watched state, listing status, live listing count, and recent market
activity. Hovering the item shows the complete vanilla tooltip from the escrowed item snapshot,
including enchantments, lore, damage, custom names, and other NBT. Hovering a seller shows their
UUID and transaction participant context. Details show the top bidder or buyer, hourly and daily
activity, average price, units sold, and a bounded recent price graph.

The footer shows Create Listing, active listing count, active bid holds, outbid notifications, and claim count.

### Listing model

Each listing stores.

- Listing UUID.
- Seller UUID.
- Escrow item lot IDs.
- Exact item fingerprint and search document.
- Listing type.
- State and revision.
- Starting bid.
- Optional buyout.
- Bid increment snapshot.
- Listing fee and sale tax snapshots.
- Creation and deadline.
- Original duration and frozen remaining duration.
- Anti snipe count and cap.
- Highest bid reference and accepted bid count.
- Category.
- Moderation status.
- Config revision.

Listing types are Buy Now, Timed Auction, and Auction with Buyout.

States include Draft, Holding, Active, Frozen, Sold Pending, Ended Sold, Ended Unsold, Cancel Pending, Cancelled, Settled, and Manual Review.

### Listing creation

1. Select an inventory item.
2. Preview exact NBT and quantity.
3. Select listing type.
4. Enter price, optional buyout, and duration.
5. Receive server fee quote.
6. Select payment source for fee.
7. Server rechecks slot, fingerprint, permissions, restrictions, limits, and fee.
8. Escrow holds item and fee.
9. Listing becomes visible only after custody is durable.
10. Activation failure returns both item and fee.

First release supports one logical stack or exact item lot. Multi stack bundle listings remain off until the base market is proven.

### Bidding

Each bid request contains listing ID, expected revision, amount, payment source, and request UUID.

The server locks the listing, rechecks active state and deadline, rejects seller self bid, validates limits and minimum bid, holds funds, compares revision, commits the new highest bid, creates the former bidder refund claim, and applies any anti snipe extension.

Rules.

- Equal bids never replace an earlier accepted bid.
- Raising an existing bid holds only the delta.
- A request at the deadline succeeds only if processed while active.
- Lost responses return the stored result.
- Bid retraction is disabled by default.
- Seller cancellation is allowed only before an accepted bid by default.
- Proxy bidding remains disabled in the first release.

### Completion

No bid returns the exact item as a seller claim.

A winning bid allocates item claim, seller proceeds, tax, fees, and all refunds.

Buy Now locks the listing, holds the buyer funds, allocates item and proceeds, refunds any top bid, and stops further actions.

### Time and anti sniping

Real time is the recommended default. Online server time is optional. Deadline and module freeze intervals are persisted. Backward clock movement is clamped and overdue records are processed in bounded batches.

Anti snipe configuration includes threshold, extension length, maximum cumulative extension, maximum extension count, and buyout availability after bids.

### Moderation and abuse controls

- Active listing and bid limits.
- Maximum held value.
- Price and quantity limits.
- NBT size limits.
- Duration presets.
- Listing and bid rate limits.
- Listing fee and sale tax.
- Fee destination.
- Cancellation rules.
- Allowed and denied item IDs and tags.
- Container and capability item policy.
- Creative mode listing policy.
- Privacy and notification defaults.

Soulbound items, oversized NBT, nested containers, unsupported capability items, and active creative mode listing are denied by default.

## 9. Bazaar

Commands include the short and long Bazaar commands plus orders and claims routes.

### Screens

The Bazaar uses the same exact shared Nocturne shell and stable top navigation as `/shop` and the
Auction House. A theme choice applies to the entire marketplace so switching modules never changes
the navigation language or visual identity.

Tabs.

- Products.
- My Orders.
- Portfolio.
- Watchlist.
- Claims.
- History.

Product cards show item, instant buy, instant sell, spread, recent volume, trend, market status,
live buy and sell order counts, hourly trades, daily units, average price, and recent price history.
Player owned order and fill views resolve display names instead of exposing UUIDs as labels. UUIDs
remain available in identity tooltips for administrators and players who need exact diagnostics.

Product detail shows identity, order books, recent trades, quantity presets, price, total, fees, slippage, availability, payment source, average execution estimate, worst allowed execution, and claimable fills.

### Product definitions

Each product has stable ID, version, registry item, optional exact NBT identity, category, display overrides, enabled or retired state, lot size, price tick, quantity and price bounds, identity policy, dimension rules, and item restrictions.

Default commodity identity accepts tagless and undamaged stacks only. Named, damaged, enchanted, container, and capability backed variants require an explicit product definition.

Rich product definitions live under config slash futureshops slash bazaar slash products as JSON files. Bazaar TOML controls global market rules.

Tag discovery is opt in preview tooling rather than automatic exposure of every registry item.

### Orders

Each order stores order UUID, owner, product version, side, order type, time in force, state, revision, limit price, original quantity, remaining quantity, filled quantity, custody lots, maximum fee reserve, accrued fee and rounding remainder, accepted sequence, timestamps, and rule snapshot.

States include Holding, Matching, Open, Partially Filled, Filled, Cancel Pending, Cancelled, Expired, Frozen, Settled, and Manual Review.

### Order book and matching

Buy orders sort by price descending then accepted sequence. Sell orders sort by price ascending then accepted sequence. Canonical orders remain persisted and runtime indexes are rebuilt and validated.

A buy crosses when its limit is at least the best sell. A sell crosses when its limit is at most the best buy. Fill quantity is the smaller remainder. Default execution price is the resting maker price.

Every fill has its own UUID, order references, product version, quantity, price, gross, buyer fee, seller fee, price improvement, journal sequence, and escrow settlement.

The fill commits before public remaining quantities change.

### Buyer reserve

A limit buy reserves limit price multiplied by quantity plus maximum buyer fee. Lower execution price releases price improvement while retaining the amount needed for the remaining quantity.

Physical payment becomes escrow value at placement. Partial fill change defaults to wallet claim.

### Seller custody

Sell items enter a FutureShops vault before the order opens. Partial fills split exact held stacks. Cancelling returns only unfilled items. Committed fills never reverse.

### Order types

Initial release supports limit orders, instant buy, instant sell, Good Until Cancelled, and configurable expiration.

Immediate Or Cancel and Fill Or Kill follow after the core matching path. Market requests always include maximum spend or minimum proceeds and a slippage bound.

### Fees and rounding

Fees use basis points. Cumulative fee calculation or carried numerator remainder prevents tiny fill fragmentation from changing total fees.

Fees can route to a void sink, server treasury, or configured system account.

### Self trading and manipulation

Self trade policies are Cancel Taker, Cancel Maker, or Skip Self. Cancel Taker is the recommended default.

Controls include order and cancellation rate limits, open order limits, maximum notional and escrow value, optional minimum lifetime, price bands, global and product circuit breakers, emergency halt, abnormal price audit signals, and wash trade reporting.

A halt stops matching but never blocks cancellation or claims. Bazaar prices do not automatically control unrelated shop prices, taxes, or collateral.

### Market information and fun features

- Favorites and watchlists.
- Price alerts.
- Recent products.
- Lightweight sparklines.
- OHLCV history buckets.
- Daily and weekly volume.
- Spread and depth summaries.
- Estimated portfolio value.
- Fill and expiration notifications.
- Configurable market sounds.
- Public volume leaders.
- Treasury dashboard.
- Optional daily market bulletin.

## 10. Configuration layout

The main config is the single source of module enablement.

```toml
[modules]
bazaar_enabled = true
auction_house_enabled = true
show_module_navigation = true
default_module = "shop"
```

Escrow has no enabled toggle.

Files.

- futureshops common TOML.
- futureshops escrow TOML.
- futureshops auction house TOML.
- futureshops bazaar TOML.
- futureshops client TOML.
- Bazaar product JSON directory.
- Optional Auction House category JSON.

Dedicated specs are registered unconditionally so disabled modules can be configured before enabling.

Main config owns module toggles, navigation, economy, currency provider, and global compatibility.

Escrow config owns recovery work, retry timing, checkpoint interval, checkpoint journal byte and record thresholds, claim delivery, claim limits, retention, asset bounds, storage strictness, refund policy, audit detail, and administrative recovery rules.

Auction config owns types, durations, limits, increments, anti snipe, fees, taxes, payment policy, restrictions, cancellation, notifications, privacy, disable behavior, and branding.

Bazaar config owns order types, limits, fees, tick and lot defaults, expiration, self trade policy, fill budget, slippage, price bands, circuit breakers, history, payment policy, disable behavior, and branding.

Client config owns time format, density, themes, accessibility, animation, sound, remembered views, and confirmation preferences.

### Reload behavior

Safe immediate reload includes module state, labels, theme, notifications, new record limits, and bounded page sizes.

Rules such as fees, durations, increments, expiration, price tick, and anti snipe apply only to new contracts.

Command aliases, currency precision, product identity, persistent key formats, and journal formats require restart or migration.

Invalid reload keeps the last valid snapshot and reports exact rejected fields.

## 11. Module disable behavior

FREEZE is the recommended default.

- No new listing, bid, order, instant trade, or match.
- Existing custody remains.
- Auction time pauses when configured.
- Cancellation and refund remain available.
- Claims remain available.
- Browse may become read only or Claims Only.
- No data is deleted.
- Re enable resumes after recovery and validation.

Optional explicit administration modes are Freeze, Drain, and Cancel and Refund. Config reload does not silently trigger mass cancellation.

## 12. Networking

The existing strict SimpleChannel remains. New packet registrations are appended in stable order and registered regardless of module state. Protocol is bumped when the first wire changes land.

Packet groups.

- Market capabilities and module status.
- Escrow and claims.
- Auction queries and mutations.
- Bazaar queries, orders, and fills.
- Notifications.
- Administrative inspection.

Every mutation includes request UUID, expected revision, session or route nonce, and bounded input.

Every response includes request UUID, transaction UUID when created, result status, new revision, public config revision, and server timestamp.

Auction House and Bazaar use server pagination rather than complete data downloads. Stale response IDs are discarded. Search is debounced. Small invalidations refresh affected pages.

Hard bounds cover query length, page size, item list size, NBT bytes, labels, bid history, order book depth, chart points, and request rate.

## 13. Permissions and administration

Permission nodes cover Auction House use, creation, bidding, buying, claiming, and administration. Bazaar nodes cover use, orders, instant trades, claiming, and administration. Escrow nodes cover claiming and administration.

Use a permission integration when available with configurable operator level fallback.

Administrative functions include module status, freeze, resume, escrow inspect, retry, refund, auction cancel, Bazaar product halt, cancel all, and audit export.

Forced settlement or refund requires elevated permission, explicit confirmation, written reason, immutable audit record, and idempotent administration request ID.

Commands remain registered while modules are disabled.

## 14. Persistence and migration

New stores include escrow checkpoints, ledger data, Auction House canonical data, Bazaar canonical data, market controls, notifications, and aggregated history. Derived indexes rebuild from canonical records.

Migration sequence.

1. Back up the world.
2. Import wallet balances with deterministic migration IDs.
3. Import pending player shop settlement money as owner claims.
4. Preserve lifetime and rows as analytics only.
5. Resolve legacy dimensionless positions only when unambiguous.
6. Import mint records into authorized, available, and spent states.
7. Preserve legacy history without fabricated transaction IDs.
8. Seed durable catalog stock from configuration once.
9. Verify totals and conservation.
10. Write migration checksum and completion sequence.
11. Retain legacy files until the new checkpoint verifies.

Newer schema enters read only recovery mode.

Initial release supports one world directory and one server process. Cross server markets require an external transactional database and distributed locks.

## 15. Edge cases

- Duplicate packet returns original result.
- Timeout retry cannot charge twice.
- Disconnect, death, or dimension change preserves UUID transaction ownership.
- Full inventory and maximum wallet preserve claims.
- Concurrent last stock produces one winner and one safe refund.
- Equal Auction House bids preserve earlier sequence.
- Bid, expiration, and buy now races resolve under one listing lock.
- Order cancellation waits for the current fill boundary.
- Product retirement preserves active product versions.
- Module disable preserves custody.
- Config reload preserves contract snapshots.
- Clock rollback is clamped and forward jumps are processed in bounded batches.
- Missing item registry entries create quarantined claims.
- Oversized or malformed NBT is rejected before custody.
- Damaged or enchanted Bazaar items require explicit variants.
- Counterfeit and spent protected bills are rejected without credit.
- Currency provider changes invalidate stale quotes.
- Foreign creative deposits are blocked by default.
- Unrepresentable foreign amounts are rejected or explicitly converted to wallet remainder.
- External storage failure leaves post commit claims.
- Storage divergence enters reconciliation or manual review.
- Arithmetic overflow fails closed.
- Invalid event modified price is rejected.
- Listener exception after hold triggers recovery.
- Permission revocation blocks new actions but preserves resolution and claims.
- Offline or banned beneficiaries retain UUID claims.
- Shop destruction or owner change does not alter snapshotted beneficiary.
- Dimension aware identity prevents coordinate collision.
- Truncated journal tail loses only the incomplete record.
- Checksum mismatch and disk full stop value mutations.
- Backup lineage mismatch is detected.
- Repeated forced refund is idempotent.
- Claim All reports partial completion.
- Recovery can itself restart safely.
- Protected bills do not age out while held.
- Fee greater than gross is rejected.
- Tiny fills cannot evade fees through rounding.
- Self trading follows configured prevention.
- Stale interface revisions refresh rather than execute.

## 16. Community regression gate

The following behaviors are checked before escrow work and after every major interface or transaction phase.

- Player shop item detail has a clear Back arrow.
- X closes rather than acting as Back.
- Marketplace Profile and placed shop routes return correctly.
- Transaction History Back has an arrow.
- Funds is replaced with a context specific label.
- 12 hour time works in all modules.
- Add Barter Items exists.
- Add Barter Held does not lead to a dead end.
- Newly added barter entries open a functional editor.
- Barter Add opens the searchable picker.
- Multi select and quantity changes work.
- Tag and namespace search predicts partial mod names.
- Marketplace Profile has no redundant Storefront and Close behavior.
- Main Shop defaults to All.
- Cart changes have a visible badge or count.
- Purchase confirmation asks Wallet or Inventory Cash.
- Every shop purchase path preserves selected payment source.
- Foreign currency warning appears in generated TOML.
- ATM planning works for internal and external providers.
- Internal ATM minting registers protected bills.
- Foreign ATM minting does not claim internal protection.

## 17. Testing strategy

Unit tests cover escrow transitions, idempotency, ledger conservation, overflow, mint states, serialization, partial claims, Auction House bidding and completion, Bazaar priority and matching, partial fills, cancellation, self trade, and fee rounding.

Property tests preserve the invariant that wallets, escrow, claims, protected physical outstanding, system accounts, and fee accounts equal starting supply plus explicit sources minus explicit sinks.

Random order streams prove that price limits hold, quantities stay nonnegative, priority remains deterministic, claims do not repeat, and fee fragmentation does not change totals.

Crash injection occurs before and after every journal append, wallet hold, inventory mutation, mint reservation, stock reservation, commit decision, claim creation, delivery, and recovery step.

Fault adapters test partial insertion, partial extraction, thrown exceptions, unload, slot reorder, simulation mismatch, network loss, and unsupported reconciliation.

Game tests cover dedicated server startup, real inventory, offline claims, player shop storage, protected money, foreign currency, config reload, full inventory, maximum wallet, restart, mod removal, dimensions, simultaneous bids, and high load matching.

Network tests cover round trips, protocol constant, invalid enums, oversized fields, duplicate request IDs, stale revisions, packet flood, delayed responses, and disabled modules.

Interface tests cover the regression gate, GUI scale 2, 3, and 4, wide and narrow layouts, keyboard use, long translations, both time formats, themes, accessibility, Back, X, Escape, resize, and stale response handling.

Performance tests cover thousands of auctions, tens of thousands of orders, large partial fill counts, claim queues, startup rebuild, recovery, search, pagination, and expiration spikes. Matching, expiry, and recovery use bounded work per tick.

## 18. Implementation phases

Each phase uses an envy branch created from the latest approved version branch. Each completed phase is tested, committed and pushed as EnVy, reviewed, fast forwarded after approval, and given a lightweight phase tag.

### Phase 0. Baseline and contracts

- Community regression acceptance tests.
- Escrow invariants and schemas.
- Module states and config ownership.
- Transaction and asset serialization fixtures.
- Performance scenarios.

### Phase 1. Escrow foundation

Branch. envy slash escrow foundation.

- Durable journal.
- Checkpoints and replay.
- Double entry ledger.
- Transaction repository.
- Claim center.
- Wallet and item custody.
- Protected and foreign currency custody.
- Recovery scheduler.
- Administration and maintenance mode.
- Conservation and crash tests.

### Phase 2. Existing flow migration

Branch. envy slash escrow existing flows.

Migration order.

1. ATM and withdrawal.
2. Deposit and right click deposit.
3. Pay.
4. Server shop buy and cart.
5. Server shop sell.
6. Server shop barter.
7. Player shop purchases.
8. Player shop barter and compound.
9. Player shop buyback.
10. Settlement claims.
11. Administrative balance changes.
12. Persistent stock reservations.

No normal value path may directly mutate a balance, mint ledger, stock, settlement, or trade inventory outside escrow when this phase completes.

### Phase 3. Shared market shell

Branch. envy slash market shell.

- Immutable market theme.
- Shared layout.
- Generic header, rail, toolbar, content, and footer.
- Scoped screens and sessions.
- Explicit market routes.
- Module capability packets.
- Green and red presets.
- Accessibility and client overrides.
- Existing Shop migration without behavior change.

### Phase 4. Auction House

Branch. envy slash auction house.

- Browse and search.
- Item custody and Buy Now.
- Claims and cancellation.
- Timed bidding.
- Outbid refunds.
- Expiration.
- Anti sniping.
- Watchlists and notifications.
- Moderation.
- History and analytics.

### Phase 5. Bazaar

Branch. envy slash bazaar.

- Product registry and versioning.
- Browse and order book.
- Sell and buy limit orders.
- Matching and partial fills.
- Cancellation and expiration.
- Claims and price improvement.
- Instant buy and sell.
- Immediate Or Cancel and Fill Or Kill.
- Fees and treasury.
- Circuit breakers.
- History, charts, alerts, and portfolio.

### Phase 6. Hardening and release

Branch. envy slash markets hardening.

- Legacy migrations.
- Corruption drills.
- Backup and restore guide.
- Disable and re enable drills.
- Fault adapter tests.
- Performance soak.
- Documentation and config examples.
- Permission examples.
- API events.
- Compatibility matrix.
- Final complete regression pass.

## 19. Recommended defaults

- Escrow always required.
- Bazaar and Auction House independently enabled.
- Module disable behavior is Freeze.
- Claims and cancellation remain available.
- Auction House and Bazaar support wallet and physical payment.
- Wallet is default for long lived orders and bids.
- Physical partial fill remainder returns to wallet claim.
- Seller proceeds become wallet claims.
- First release market items come from player inventory.
- Third party storage waits for transaction receipts.
- Bazaar commodities are tagless and undamaged by default.
- NBT variants require explicit products.
- Seller self bidding is prohibited.
- Bazaar self trade policy is Cancel Taker.
- Bid retraction is disabled.
- Proxy bidding is initially disabled.
- Anti sniping is enabled.
- Auction time uses real time.
- Freeze pauses remaining auction time.
- Bazaar executes at maker price.
- Claims do not expire automatically.
- Initial release is single server.
- Shop, Player Shops, Bazaar, and Auction House share one blurple Nocturne schema. Optional client
  theme and accessibility overrides recolor the entire marketplace consistently.

These defaults maximize safety while retaining server and client customization. FutureShops does not claim control over foreign currency supply or arbitrary third party storage behavior that it cannot technically enforce.

## 20. Completion record

The implementation covers the escrow foundation, migrated shop and currency flows, shared market
shell, independently configurable Auction House and Bazaar modules, wallet and inventory cash
funding, protected and foreign currency handling, claims, administrative recovery, permissions,
module lifecycle controls, analytics, alerts, API events, migrations, backup guidance, and bounded
performance tests described above.

Release verification includes the complete automated regression suite, packet round trips,
durability and replay fixtures, corruption and recovery drills, module disable and re enable tests,
exact item identity tests, administrative cancellation with standing bid refunds, and restore
soaks with 2000 auctions and 10000 Bazaar orders. The supported platform and integration boundaries
are documented in [docs/compatibility-matrix.md](docs/compatibility-matrix.md).

The post release market usability pass adds `bazaar_control` with admin and players modes. Admin
mode keeps the curated JSON product registry and reconciliation rules. Players mode preserves the
durable registry and allows tagless, undamaged held items to be registered without a per-player
catalog quota. Both modes retain escrow, versioning, circuit breakers, request rate limits, and
history.

Auction listing creation now presents the exact server listing fee before payment selection and
reconciles the result against the durable escrow commit before showing a recovery warning. The
shared market shell now calls the exact `/shop` header renderer for its logo, wordmark, Server Shop,
Player Shops, Bazaar and Auction House tabs, search, balance, profile, and close controls. All market
surfaces use one Nocturne theme, contain empty states inside the content region, and use responsive
detail panels for exact item data, seller identity and skin, bidder or buyer status, price, timing,
state, live supply, hourly activity, daily volume, average price, recent price graphs, and actions.

The market body now mirrors the Server Shop structure as well as its header. Bazaar and Auction
House routes use the shared segmented control for their local views and the shared Departments
panel for All and category rows, including market wide category counts and the same selection,
hover, scrolling, spacing, and accent treatment. Routine escrow ready banners are intentionally
hidden. Escrow remains mandatory, while a temporary readiness or recovery failure produces a
deduplicated chat warning and an explicit Retry control that resubmits only the safe read request
or invites the player to repeat an economic action only after the server confirms that nothing
changed.

The Auction House creation route now exposes Create Listing both in the route content and in the
footer, so the listing wizard remains discoverable even when the route has no cards. Auction detail
pages use the available space for exact item condition and data, listing type, bid count, buyout
distance, abbreviated identity, seller context, live activity, and a black chart surface. Recorded
price history is drawn with gold, green, and red movement segments. When recorded history is not
available, the chart is explicitly labeled as a bid ladder and displays only the current bid, next
bid, and buyout. Market prices and fees use a configurable currency symbol, with the dollar sign as
the default, and gold price text across cards, details, payment prompts, and creation fees.

The community navigation and presentation followup keeps parent navigation visible on both owner
and visitor player shop block screens while the close control still closes the complete interface.
The shared marketplace header uses the player skin for the compact profile control and identifies
Marketplace Profile and Transaction History on hover. Bazaar and Auction House breadcrumbs use the
same Marketplace path formatting as the Server Shop, and Back appears only on detail routes instead
of replaying ordinary local tab history. Auction creation owns a complete market workspace so
underlying department and toolbar surfaces do not show through it. ATM deposit safety guidance wraps
inside the available panel. The Bazaar footer identifies admin JSON catalog control or player catalog
control and explains where Add Held Item is available. Admin item tag search matches both registry
namespaces and normalized Forge mod display names, including aliases such as Applied Energistics and
Macaw mod families.
