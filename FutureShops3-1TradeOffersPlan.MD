# FutureShops 3.1 advanced trade offers plan

## Status

Planning is complete enough to begin implementation after owner approval.

This document describes planned behavior. It does not claim that the features are implemented. The [FutureShops 3.0 plan](FutureShops3-0Plan.MD) remains the source of truth for unfinished 3.0 work. This plan is the source of truth for the 3.1 trade offer expansion.

## Purpose

FutureShops needs one clear trade model for server shops and player shops. The model must support free items, sell only listings, money, barter, mixed payments, alternative payments, multi item trades, and discounted bundles without weakening server authority or escrow recovery.

The first release target is the Server Shop shown in the supplied screenshots. Player Shop support follows through the same normalized offer concepts after the server shop path is proven.

## Audited current behavior

### Confirmed free purchase defect

The free item problem has three independent causes.

1. `ItemDetailScreen` displays an effective buy price of zero or less as `Free`.
2. The same screen enables Cart and Buy only when `buyPrice() > 0`.
3. `ShopBuyService` rejects a catalog buy price or calculated line cost of zero or less.

The escrow contract also assumes every purchase has positive money:

* `ServerShopPurchaseCommit.Line` rejects `lineCostMinorUnits <= 0`.
* Server shop purchase replay requires exactly one money evidence lot.
* Physical funding records require a positive amount.

Changing the button condition alone would therefore create an incomplete and unsafe fix. A free trade must be explicit and must use an escrow path with no payment leg while retaining stock reservation, item claims, idempotency, replay verification, history, and rate limits.

### Current server shop capabilities

Server Shop catalog items currently contain one output item, buy and sell prices, listing stock, optional exact NBT, category, expiry, and a barter flag.

Price zero has an ambiguous legacy meaning. The client can label it free, while transaction services treat it as disabled or invalid.

Sell only behavior is partly possible by setting buy price to zero and sell price above zero. It is implicit rather than a first class listing mode. The detail screen disables Buy and enables Sell when the player owns the item, but the browse toolbar has no Sell filter and the Buy filter also includes listings that only buy from the player.

Server barter recipes already support several required ingredients in one recipe. Those ingredients are an atomic AND condition. Several recipes can target the same item, which can represent alternatives, but this is not presented as a clear OR choice in the visitor or administrator experience.

Server barter produces one item identity with a count. Server money purchases also produce one item identity with a count. Neither path supports a bundle containing different output items.

Money purchase, sell to shop, and barter are separate transaction and escrow paths.

### Current player shop capabilities

Player Shop listings already have the following trade modes:

* Money.
* Barter.
* Money or barter.
* Money and barter.

Player Shop listings also have directional behavior:

* The shop sells to the visitor.
* The shop buys from the visitor.
* Both directions.

Player Shop output bundles already support up to 36 component stacks and calculate available bundle stock from the least available component.

The current player shop model still has important limits:

* One barter item template is stored per listing.
* Free is not an explicit mode.
* Several validations require a positive money or barter amount.
* Direction names such as `BUY` and `SELL` are easy to read from the wrong perspective.
* The mode is represented by a growing set of fields rather than a normalized offer.

The 3.1 design must reuse proven player shop bundle and escrow behavior where appropriate, but it must not copy the current field limitations into the new contract.

## Product terminology

User interface wording is always from the visitor perspective:

* Buy means receive an item or bundle from the shop.
* Sell to Shop means give an item or bundle to the shop.
* Barter means give one or more items instead of using only money.
* Free means receive the output without giving money or items.
* Bundle means one transaction produces more than one item type.

New domain code must use unambiguous direction names:

* `ACQUIRE_FROM_SHOP`
* `SELL_TO_SHOP`

Legacy Player Shop directions remain readable for migration and replay, but new code must not add more behavior to the ambiguous legacy names.

## Required trade combinations

The implementation must support this complete matrix.

| Visitor action | Required behavior |
| --- | --- |
| Free | Receive an item or bundle with no payment component |
| Money only | Pay one money amount |
| Barter only | Pay one or more item components |
| Money or barter | Choose one money option or one barter option |
| Money and barter | Pay money and all item components in one atomic option |
| Multiple barter | Choose between several barter options |
| Multi item barter | Give several item components in one atomic option |
| Buy only | The shop provides outputs, with no Sell to Shop action |
| Sell only | The shop accepts inputs, with no Buy or Cart action |
| Two way | The listing exposes both Buy and Sell to Shop actions |
| Multiple sell options | Choose between separately identified Sell to Shop exchanges |
| Output bundle | Receive several item types in one transaction |
| Input bundle | Give several item types in one Sell to Shop transaction |
| Discounted bundle | Show a verified individual total and savings amount |

No combination may be inferred from a numeric zero.

## Normalized offer model

### Listing

A trade listing owns presentation, stock policy, and two independent action collections.

Required fields:

* Stable `listingId`.
* Monotonic or content derived `revision`.
* Display name, description, category, icon, expiry, and permission metadata.
* One or more output components for acquire actions.
* Zero or more acquire options.
* Zero or more sell options.
* Explicit stock policy.
* Optional bundle comparison references.

A listing is invalid if it has no acquire options and no sell options.

### Acquire options

Each acquire option is one complete way to receive the listing output.

Required fields:

* Stable `optionId`.
* Optional nonnegative money cost.
* Zero or more item cost components.
* Explicit `free` flag.
* Optional limits, schedule, and permission override.

Options are alternatives. The visitor chooses one option.

Components inside one option are cumulative. Every component must be satisfied.

Examples:

* Ten coins is one money only option.
* Four iron ingots and one stick is one barter option with two required components.
* Five coins and two emeralds is one compound option.
* Ten coins, or four iron ingots and one stick, is two alternative options.
* Free is one explicit option with no money or item costs.

### Sell options

Each sell option is one complete exchange offered by the shop to the visitor.

Required fields:

* Stable `optionId`.
* One or more exact item input components.
* A positive money payout for the initial 3.1 scope.
* Optional capacity, schedule, permission, and per player limits.

Several sell options on one listing are alternatives. Each option may require an input bundle.

Item payouts for Sell to Shop exchanges are intentionally deferred. The normalized model may reserve room for them, but 3.1 must not expose or persist an unsupported payout type.

### Item components

Each item component contains:

* Registry item identifier.
* Positive count.
* Optional exact NBT template.
* A stable component identifier inside the option or output bundle.

Initial matching remains exact item plus optional exact NBT, consistent with existing escrow evidence. Tag based and fuzzy component matching are deferred until their resolution and snapshot rules are specified.

### Validation rules

The offer compiler must reject:

* Negative money values.
* Empty output bundles.
* Empty sell input bundles.
* A free option containing money or item costs.
* A nonfree acquire option with no cost.
* Duplicate listing, option, or component identifiers.
* Duplicate components that were not normalized.
* Counts outside existing inventory, packet, or escrow bounds.
* Arithmetic overflow at every quantity multiplication or sum.
* Recursive listing references.
* Unknown item identifiers or invalid exact NBT.
* Acquire and sell options that move money in both directions.

The normalized form is OR between options and AND within an option. Arbitrary nested Boolean expressions are not allowed.

## Free offer behavior

Free is an explicit acquire option, not a zero price.

The visitor experience must:

* Show `Free` and a `Get` or `Claim Free` action.
* Skip the payment source chooser.
* Permit a free line in the cart.
* Request a payment source only when the selected cart options contain a positive money total.
* Show stock, quantity, limits, and cooldowns before confirmation.

The server must:

* Resolve the trusted listing and selected option by identifier.
* Verify the option is explicitly free.
* Reserve and commit stock.
* Create exact item claims for every output component.
* Preserve request UUID idempotency and replay checks.
* Record transaction history with zero cost and the selected option identifier.
* Apply normal shop access, quantity, rate, schedule, and permission checks.
* Deliver through the claim system when the inventory is full.

Transaction events must not silently convert a positive paid option into a giveaway. A zero event result is accepted only for an option whose trusted definition permits free settlement.

Free offers need configurable abuse controls:

* Maximum quantity per request.
* Optional per player lifetime limit.
* Optional per player rolling or fixed period limit.
* Optional cooldown.
* Existing listing stock and stock refresh behavior.

Defaults must preserve ordinary catalog behavior and must not create hidden global limits.

## Sell only behavior

A listing is sell only when it has one or more sell options and no acquire options.

The visitor experience must:

* Show a `Sell to Shop` badge in browse results.
* Hide Buy and Cart actions.
* Show only valid sell options.
* Show owned quantities for every required input component.
* Disable confirmation until the complete input bundle is available.

The server must custody every required input before creating the payout. The payout, stock mutation, history entry, and custody consumption must commit atomically.

Failed or interrupted transactions must return all held inputs through recoverable claims. Partial input consumption is forbidden.

## Barter and compound payments

Barter options may contain several item components.

Several barter options on one listing represent alternatives. The selected `optionId` is part of the request identity and replay evidence.

Money and barter in one option is one atomic exchange. The transaction must hold the selected money source and all item components before committing outputs.

The server must never accept component lists, prices, output payloads, or comparison prices supplied by the client. The client sends only stable identifiers, quantity, expected revision, request UUID, and a payment source when the trusted option contains money.

## Bundle behavior

### Output bundles

An output bundle contains several item components. One quantity means one complete bundle.

For Player Shops, available quantity remains the minimum complete bundle count available in storage.

For Server Shops, bundle stock belongs to the bundle listing by default because catalog stock is virtual listing stock. An optional linked stock policy may reserve referenced component listings, but it must be implemented as a separate, explicit policy and must reserve every stock key atomically.

Every bundle output is snapshotted into exact escrow claim payloads. If one output cannot be prepared, no output or payment commits.

### Input bundles

A sell option may require several input components. The player must have every component for the requested quantity.

All components enter custody as one prepared batch. No payout occurs until the complete batch is verified.

### Verified savings

Savings are informational and must never be trusted from client text or an administrator supplied total.

Each comparable bundle output may reference a stable standalone listing in the same shop. The server validates that:

* The referenced listing is active.
* Its output item, exact NBT, and count match the bundle component.
* It has a comparable money acquire option.
* Its current revision is available.

The individual total is calculated from current effective standalone prices for the selected quantity. The bundle total is the current effective money price of the selected bundle option.

The detail screen may show:

* `Individual total 12.00`
* `Bundle price 10.00`
* `You save 2.00, 16.7 percent`

If any reference is missing, expired, incompatible, or cheaper through a noncomparable option, the savings claim is omitted rather than guessed.

Comparison snapshots and revisions are recorded for audit history, but they do not change settlement. Promotions may update the displayed savings whenever the catalog revision changes.

## Catalog schema and migration

### Versioned schema

Server Shop JSON gains an explicit schema version. The implementation target is version 2.

Version 2 stores listings, output components, acquire options, sell options, stock policy, limits, and bundle comparisons directly.

`ShopDefinitionLoader` must compile both supported legacy input and version 2 input into the same immutable runtime offer model.

### Legacy conversion

Legacy conversion must preserve behavior:

* `buyPrice > 0` becomes one money acquire option.
* `buyPrice == 0` leaves money acquisition disabled.
* `sellPrice > 0` becomes one sell option.
* `sellPrice == 0` leaves Sell to Shop disabled.
* A legacy item becomes a one component output bundle.
* Each legacy barter recipe becomes one acquire option.
* Every ingredient in one legacy barter recipe remains required.
* Existing listing identifiers, categories, expiry, NBT, and stock keys remain stable.

Legacy zero prices must not become free automatically. Administrators must select the explicit Free option.

The legacy `barterEnabled` field becomes derived compatibility data and is not authoritative in version 2.

### Safe writing

The administrator writer must:

* Validate the complete candidate catalog before replacing the live file.
* Write to a temporary sibling file.
* Flush and atomically replace where the platform supports it.
* Keep a bounded backup of the last valid administrator catalog.
* Reload only after the new file parses and compiles.
* Preserve the prior live catalog when any step fails.
* Fail closed on a newer unknown schema.

## Escrow and transaction architecture

### New offer transaction

The positive money assumptions in `ServerShopPurchaseCommit` make it unsafe to retrofit every new exchange into the old commit shape.

Version 2 offers use a new `ServerShopOfferCommit` and `ServerShopOfferService`. Legacy commit codecs remain readable for old journal recovery and replay.

The new commit contains:

* Request UUID.
* Player, shop, listing, option, action, and quantity identifiers.
* Trusted offer revision and configuration fingerprint.
* Optional money debit evidence.
* Optional money payout evidence.
* Zero or more item custody inputs.
* One or more exact output claim payloads for acquire actions.
* All stock reservations and mutations.
* Payment source only when money is debited.
* Timestamps and exact shop reference.

Free settlement has no money asset lot. Replay validation distinguishes an intentionally absent money leg from missing evidence through the trusted option snapshot and commit schema.

Sell settlement contains item custody and payout evidence. Acquire settlement contains output evidence and any required payment custody.

### Atomic execution order

Acquire execution:

1. Resolve access, listing, option, revision, limits, stock, and output templates.
2. Capture an immutable quote.
3. Prepare money funding when required.
4. Prepare all item cost custody when required.
5. Revalidate the complete quote and access state.
6. Reserve all stock keys.
7. Commit payment, custody, stock, transaction evidence, and output claims.
8. Deliver claims and preserve undelivered claims.
9. Record history and refresh affected sessions.

Sell execution:

1. Resolve access, listing, option, revision, capacity, and payout.
2. Capture an immutable quote.
3. Prepare all player item inputs in one custody batch.
4. Revalidate the complete quote and access state.
5. Reserve inbound stock or configured sink capacity.
6. Commit custody, payout, stock, and transaction evidence.
7. Return recoverable claims on abort.
8. Record history and refresh affected sessions.

Every prepared resource needs deterministic release or recovery behavior. Exceptions must resolve replay before reporting failure.

### Cart behavior

Each cart line stores `listingId`, `optionId`, quantity, and observed revision.

The server merges only identical listing and option pairs. It recalculates all totals and item components.

Acquire cart checkout may combine free, money, barter, and compound lines in one atomic plan when all selected options are compatible. Money components share one selected payment source. Item components are normalized into one custody batch.

Sell to Shop transactions remain outside the acquire cart for 3.1. A future sell basket may reuse the offer batch planner after single sell option recovery is proven.

## Networking

The network protocol must move from 51 to the next available value, currently 52 if no earlier change consumes it.

New packet registrations are appended. Existing discriminator positions are never reordered.

Offer requests contain only:

* Shop identifier.
* Listing identifier.
* Option identifier.
* Action.
* Quantity.
* Expected offer revision.
* Request UUID.
* Payment source when required.

Catalog packets contain bounded immutable offer summaries and detail payloads. All list counts, strings, NBT payloads, output components, options, and cost components have explicit codec limits.

Packets reject unknown actions, unknown option identifiers, zero UUIDs, invalid quantities, oversized collections, invalid identifiers, and arithmetic overflow before scheduling work.

All mutation handling runs on the server thread. The server resolves the trusted current offer after enqueueing.

## Visitor interface

### Browse filters

The Server Shop toolbar becomes:

* All.
* Buy.
* Sell.
* Barter.
* Bundles.

The layout must remain usable when the toolbar does not fit one row. A scrollable strip or compact overflow control is preferred over shrinking labels below readability.

Filtering rules:

* Buy includes money and free acquire options.
* Sell includes any Sell to Shop option.
* Barter includes any acquire option with item costs.
* Bundles includes multiple output components or multiple sell input components.
* All includes every active listing.

Cards display concise badges such as Free, Money, Barter, Money and Barter, Sell Only, and Bundle.

### Detail screen

Actions are generated from the listing instead of reserving fixed Buy and Sell positions.

Free uses `Get` or `Claim Free`.

Sell only hides Buy and Cart.

Listings with several acquire options open an option chooser. Each option card shows all required components together. Alternative cards use `or` between them.

The confirmation screen shows the exact selected option, all inputs, all outputs, quantity, limits, current stock, payment source, and final effective amounts.

Bundle details show each output component, the verified individual total when available, the bundle price, and savings.

### Accessibility and error behavior

Every icon and color state needs text. Disabled actions explain the reason through text or tooltip.

Stale revisions return to a refreshed detail state with a clear quote changed message. Recovery states do not invite blind retries with a new request UUID.

## Administrator interface

The listing editor becomes an offer builder with these sections:

* Outputs.
* Ways to get this listing.
* Ways to sell to this shop.
* Stock and limits.
* Bundle comparison.
* Schedule and permissions.
* Preview.

Quick templates:

* Money only.
* Barter only.
* Money or barter.
* Money and barter.
* Free.
* Sell only.
* Buy and Sell.
* Bundle.

Templates initialize the editor but do not create a separate runtime model.

Administrators can add, remove, reorder, and label options and components. Save remains disabled until the complete offer compiles.

The preview shows visitor facing wording and the normalized JSON. It must make AND and OR relationships visually explicit.

Removing or changing a live option increments the listing revision. Pending transactions retain their captured evidence and follow normal replay or quote changed rules.

## Player Shop convergence

Player Shop migration begins after Server Shop offer transactions pass recovery and multiplayer testing.

Legacy fields map as follows:

* Money becomes one acquire option with a money component.
* Barter becomes one acquire option with an item component.
* Money or barter becomes two acquire options.
* Money and barter becomes one acquire option with both components.
* Existing bundle outputs become output components.
* Shop sells becomes acquire options.
* Shop buys becomes sell options.
* Both directions populate both collections.

Player Shop storage remains the authoritative physical source and sink. The normalized offer does not bypass owner storage, proceeds claims, exact NBT checks, access controls, or break protection.

NBT persistence gains a version. Old listings migrate in memory and save in the new version only after validation. Existing escrow snapshots and transaction evidence remain readable.

## Limits and adjacent features

The model must leave room for:

* Per player purchase limits.
* Cooldowns.
* Scheduled offers.
* Permission gated offers.
* Quantity tiers and volume discounts.
* Promotion integration.
* Linked Server Shop bundle stock.
* A future sell basket.

The following are not part of 3.1:

* Arbitrary scripts.
* Recursive offers.
* Randomized or hidden bundle outputs.
* Negative prices.
* Fractional item counts.
* Fuzzy NBT matching.
* Client supplied prices or savings.
* Partial fulfillment.
* Cross shop bundle references.

These exclusions keep settlement explainable, bounded, and recoverable.

## Implementation phases

### Phase 1, offer contracts and migration

Deliver:

* Immutable version 2 listing, action, option, component, stock, limit, and comparison records.
* Complete validation and normalization.
* Legacy Server Shop compiler.
* Version 2 JSON parser.
* Deterministic offer and option revisions.
* Catalog summary and detail projections.
* Migration fixtures for existing administrator JSON.

Gate:

* Every required trade combination compiles or rejects predictably.
* Legacy catalogs compile to behavior equivalent normalized offers.
* Zero legacy prices stay disabled.
* Explicit free options compile.
* No production mutation path uses the new model yet.

### Phase 2, server escrow and network contracts

Deliver:

* `ServerShopOfferCommit`.
* Versioned commit codec.
* `ServerShopOfferService`.
* Atomic item custody, money funding, stock, payout, output claim, replay, and recovery behavior.
* Acquire and Sell to Shop packets.
* Catalog offer summary and detail packets.
* Protocol bump.

Gate:

* Free, money, barter, compound, alternatives, sell only, output bundles, and input bundles pass service tests.
* Recovery reconstructs exact evidence after every injected interruption point.
* Legacy transaction codecs still replay.

### Phase 3, visitor vertical slice

Deliver:

* Correct free action.
* Dynamic detail actions.
* Option chooser.
* Sell only flow.
* All, Buy, Sell, Barter, and Bundles filters.
* Mixed acquire cart.
* Bundle component and savings presentation.
* Accessible failure and stale quote states.

Gate:

* Every required trade combination is usable without commands or manual JSON requests.
* Free never opens a payment chooser.
* Sell only never exposes Buy or Cart.
* AND and OR requirements are visually unambiguous.

### Phase 4, administrator offer builder

Deliver:

* Quick templates and complete offer editing.
* Multi component input and output editing.
* Alternative option editing.
* Bundle comparison references.
* Limits, schedule, and permission editing.
* Validated atomic JSON writing and backups.
* Visitor preview.

Gate:

* Every required combination can be created, edited, reloaded, and removed in game.
* Invalid partial offers cannot replace the live catalog.
* Reload failures preserve the last valid catalog.

### Phase 5, Player Shop convergence

Deliver:

* Versioned Player Shop offer persistence.
* Legacy listing migration.
* Multiple barter components and alternatives.
* Explicit free support where owner stock and limits allow it.
* Input and output bundle editor support.
* Unified visitor wording and option chooser.

Gate:

* Existing Player Shops retain stock, proceeds, direction, price, barter, and bundle behavior.
* Dedicated server startup and old world loading succeed.
* Break protection and escrow recovery remain intact.

### Phase 6, hardening and release

Deliver:

* Performance and packet bound review.
* Security review.
* Client, dedicated server, multiplayer, reconnect, restart, and migration smoke tests.
* Configuration examples.
* Administrator and player documentation.
* Release notes and compatibility matrix update.

Gate:

* No unresolved recovery or conservation failure.
* No misleading savings display.
* No unsupported old transaction or catalog state.
* The final JAR contains the expected metadata, assets, and no local artifacts.

## Verification plan

### Unit and contract tests

Add focused tests for:

* Every row in the required trade matrix.
* Legacy catalog conversion.
* Explicit free compared with legacy zero.
* Option and component normalization.
* Duplicate identifiers.
* Exact NBT matching.
* Collection and payload bounds.
* Arithmetic overflow.
* Offer revision stability and changes.
* Browse filter classification.
* Bundle comparison validation and savings math.
* Promotion interactions.
* Limits, cooldowns, schedules, and permissions.
* Packet round trips and malformed packet rejection.
* Old and new commit codec round trips.

### Escrow and recovery tests

Inject failures before and after:

* Money funding.
* Item custody prepare.
* Item custody commit.
* Stock reserve.
* Stock commit.
* Payout ledger commit.
* Output claim creation.
* Claim delivery.
* History publication.

Verify:

* Free has no money evidence and still has complete offer, stock, and output evidence.
* Money is conserved for paid acquire and sell actions.
* Every held item is either committed or recoverable.
* Alternative options consume only the selected option.
* Compound options consume every component.
* Bundles deliver all outputs or none.
* Input bundles consume all inputs or none.
* Request replay returns the original outcome.
* Reusing a request UUID with another option or quantity returns a conflict.
* Full inventories leave exact pending claims.
* Concurrent stock races produce one valid winner without oversell.

### Game and interface tests

Test at minimum:

* Single player.
* Dedicated server.
* Two players buying the last stock concurrently.
* Disconnect during prepared custody.
* Reconnect with pending claims.
* Server restart with pending and completed offers.
* Wallet and physical money sources.
* Full inventory.
* Exact NBT components.
* Finite and unlimited stock.
* Expiry and promotion changes during confirmation.

Visual acceptance sizes include the supplied 1640 by 1350 detail screen, 568 by 112 toolbar crop, and 1430 by 1233 barter editor, plus 1280 by 720 and common GUI scales from 2 through 4.

### Required commands

Run applicable checks in this order:

1. `./gradlew test`
2. `./gradlew runData` when generated resources change
3. `./gradlew runGameTestServer` when GameTests are added
4. `./gradlew build`
5. `./gradlew runServer` for dedicated server smoke testing
6. `./gradlew runClient` for client and interface smoke testing

Multiplayer and reconnect scenarios require a real client connected to the dedicated server.

## Documentation obligations

Implementation must update:

* `README.md` for user visible feature and setup changes.
* `DOCUMENTATION.md` for architecture, schema, commands, recovery, and migration.
* `docs/config-3.0-examples.md` or its versioned successor with complete offer examples.
* `docs/compatibility-matrix.md` for protocol and save compatibility.
* Administrator tooltips and translations for every new action and validation error.

Planned features must not be described as implemented before their phase gate passes.

## Approval decisions

The following defaults are part of this plan unless the owner changes them before implementation:

* Legacy zero prices remain disabled, not free.
* Free requires an explicit option.
* Alternatives use OR, components inside an option use AND.
* Server Shop bundle stock is independent by default.
* Savings use validated same shop listing references and current effective money prices.
* Sell to Shop pays money in 3.1.
* Acquire cart supports mixed option types.
* Sell to Shop stays outside the acquire cart in 3.1.
* Server Shop is implemented before Player Shop convergence.
* Old transaction codecs remain readable.
* Arbitrary scripts, randomized rewards, partial fulfillment, and negative prices remain out of scope.
