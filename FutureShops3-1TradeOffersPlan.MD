# FutureShops 3.1 advanced trade offers plan

## Status

Owner approval was granted and the 3.1 implementation is present on the active beta phase branch. The implementation is undergoing its final plan audit and acceptance run. It is not approved for release until the complete automated, client, dedicated server, multiplayer, reconnect, restart, migration, and injected recovery gates in this plan pass.

This document remains the acceptance source of truth for the 3.1 trade offer expansion. Requirements use plan language so an unverified gate is never mistaken for a passed gate. The audited current behavior section records the 3.0 baseline that motivated the work, not the current 3.1 beta behavior. Current code, tests, and the final verification record determine which requirements are implemented. The [FutureShops 3.0 plan](FutureShops3-0Plan.MD) remains the source of truth for unfinished 3.0 work.

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

### Current administrator editing limitations

The current Server Shop administrator flow explains why advanced listing setup feels confusing:

* The main editor is a fixed 260 by 230 modal containing display name, buy price, sell price, stock, and category.
* The meaning of the two price fields changes to output count and ingredient count when the modal was opened through the barter add path.
* The batch item picker reuses the same footer fields for prices, output count, or ingredient count depending on hidden mode.
* Add Held Barter assigns the main hand as output and the offhand as payment. The relationship is explained through a short hint rather than explicit component roles.
* Existing barter opens a separate recipe editor, which opens another item picker. Each ingredient count and removal is sent to the server immediately.
* Removing barter may remove the entire listing when both legacy prices are zero.
* Category selection uses left and right arrows instead of a visible searchable choice.
* Blank or unparsable values silently become zero, one, or unlimited depending on the field.
* The main modal provides a tooltip only for the unlimited stock button. The item picker provides item tooltips and one unlimited stock tooltip, while price, direction, barter, category, count, save, and remove behavior lack contextual explanations.
* Save and Remove close the modal immediately after sending an uncorrelated generic action packet.
* The server acknowledgement appears later in the parent screen status strip. A failed save cannot highlight a field and the closed modal no longer owns the typed draft.
* The generic edit packet uses action strings and positional string and long slots, making client intent difficult to inspect and validation errors difficult to associate with one control.

The 3.1 editor must replace these hidden mode changes and fragmented mutations with one persistent, explicit offer draft.

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

## Configuration layout and migration

All FutureShops owned configuration must live below the single `config/futureshops/` directory. This includes the common, client, escrow, Auction House, and Bazaar TOML files, Server Shop catalogs, Bazaar product catalogs, migration backups, and any future module configuration. Forge owned files such as `fml.toml` and `forge-client.toml` remain in the configuration root.

Existing installations may contain loose FutureShops TOML files and Forge generated backups in the configuration root. Startup must migrate every recognized FutureShops file before Forge registers the specifications:

* Move each recognized loose active file into `config/futureshops/` without changing its bytes.
* Move recognized loose backup files into the same FutureShops directory so no FutureShops artifact remains at the configuration root.
* Keep an existing nested active file authoritative when both locations contain a file.
* Preserve the conflicting loose file under `config/futureshops/migration-backups/` with a unique name instead of overwriting or deleting either copy.
* Reject symbolic links, directories, unsafe names, and incomplete migrations rather than silently loading defaults over an existing configuration.
* Make migration idempotent. A second startup must not move, copy, rewrite, or back up anything again.
* Leave unrelated files and directories untouched.

Optional empty identifier and tag lists must use a Forge specification that accepts empty lists. A valid default configuration must remain correct after load and save, without recurring correction warnings or numbered backup files.

Migration and registration apply in both integrated and dedicated server environments. A clean installation must create `config/futureshops/` before configuration loading, including when a modpack developer opens a singleplayer world to prepare an administrator shop.

Acceptance requires:

* A clean startup creates no `futureshops-*.toml` files at the configuration root.
* An upgrade preserves exact values from every recognized loose active file.
* A nested and loose conflict preserves both files and continues with the nested file.
* Existing `config/futureshops/shops/` and `config/futureshops/bazaar/products/` catalogs remain in place.
* A second startup reports no correction for valid empty optional lists and creates no new configuration backup.
* Client, integrated server, and dedicated server startup use the same canonical FutureShops directory.
* Configuration documentation and examples show only canonical nested paths.

### Implementation status

This configuration hardening is implemented and locally verified on the active phase branch as of July 29, 2026. It remains in review until the phase change is merged.

* All five Forge managed specifications register below `config/futureshops/`.
* The migration preserves active files and numbered Forge backups, archives conflicts without overwriting either copy, rejects unsafe paths, and is idempotent.
* Currency item lists and Auction House restriction lists accept valid empty defaults without correction.
* Focused regression tests, the complete test suite, and the packaged build pass.
* A dedicated server migrated 17 existing loose files and backups, reached ready, then reached ready again without migration, correction, or backup churn.
* A client under a virtual display loaded all five canonical TOML paths and reached the render thread without configuration correction.
* Documentation and examples contain only the canonical nested paths.
* No implementation deviation or unresolved configuration migration risk remains. Merge and release approval remain outside this work item.

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

Sell to Shop transactions remain outside the acquire cart. The 3.1 bulk sell workflow is a separate quoted batch with its own inventory reservation, confirmation, replay, and result model. It must not reuse acquire cart state or make Sell to Shop options appear in the acquire cart.

## Networking

The network protocol is currently 55. Bulk sell packets append at the end of the registration order and move the protocol to 56.

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

Bulk sell adds bounded quote request, quote response, commit request, and result packets. The same packet and service contracts serve command opened and shop opened flows.

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

### Bulk sell workflow

GitHub issue 2 tracks the visitor bulk sell addition.

Branch implementation status on July 29, 2026: the bulk sell command, shared quote and commit service, Server Shop entry flow, confirmation interface, packet protocol, localization, tests, and operator documentation are implemented on the active phase branch. Local verification passed 1,861 unit and contract tests, all five required GameTests, the full build, dedicated server startup, virtual display client startup, and a protocol 56 multiplayer disconnect and reconnect cycle. The phase remains in review until the branch is approved and merged.

The command surface is:

* `/sellall adminshop` quotes the `default` Server Shop and opens the bulk sell confirmation screen.
* `/sellall adminshop confirm` creates the same server authoritative quote and immediately submits every eligible line without opening a screen.
* `/sellall playershops` searches the Player Shops visible through the configured local listings scan radius and opens the same confirmation screen.
* `/sellall playershops confirm` creates the same server authoritative quote and immediately submits every eligible line without opening a screen.

The active Server Shop also exposes a `Sell Inventory` action. It uses the active shop identifier instead of forcing `default`. The action opens a short choice between selecting inventory items and starting with every eligible item selected. Both choices continue into the same confirmation screen. The existing Sell filter, detail action, and option chooser remain the one listing workflow.

The server creates every quote from the current main inventory and offhand. Worn armor is excluded so bulk actions never remove equipped gear. Empty slots are ignored. Exact NBT variants remain separate. Every carried stack group is represented in the confirmation screen. Accepted groups begin selected. Unaccepted groups are disabled with a localized reason instead of disappearing.

A quoted line contains a server generated stable line identifier, exact input components, maximum executable quantity, destination label, shop identity, listing and option identity, observed revision, unit payout, line payout, and eligibility state. Input bundles appear as one line with all required components. The planner reserves inventory components across lines so one stack cannot contribute to two quoted exchanges.

The client sends only the quote identifier and selected line identifiers. It never supplies item identities, quantities, prices, destinations, revisions, totals, or transaction outcomes. Quotes are player bound, single use, memory only, bounded, and short lived. Disconnect, expiration, catalog reload, and explicit cancellation discard an uncommitted quote without mutating inventory or money.

Server Shop quoting considers only active money payout Sell to Shop options in the requested shop. Player Shop quoting considers only active money payout Sell to Shop options in the current dimension, loaded chunks, and the same configured radius used by the Player Shops browser. Candidates that require unavailable storage, exceeded buyback capacity, an insolvent shop, an inaccessible permission, or an inactive schedule are ineligible.

For Player Shops, candidates that compete for the same inventory are ranked by highest effective payout per required input, then highest total payout for one complete exchange, shortest distance, and stable shop, listing, and option identifiers. A lower ranked offer is never selected while a higher ranked offer remains executable. Distinct input signatures remain distinct confirmation lines. Scan count, candidate count, quote lines, component count, strings, and encoded NBT are bounded.

Confirmation shows each item or input bundle, quantity, destination, unit payout, line payout, selection control, and selected total. It provides Select All, Clear All, Confirm Sale, and Cancel. Mouse, keyboard, narration, hover help, and focus help expose the same information. Escape returns once to the originating shop screen, or closes the command opened screen once, without stacking duplicate parent screens.

Commit revalidates the player, quote ownership, quote lifetime, exact inventory, offer revision, permission, schedule, limits, capacity, storage, shop balance, player maximum balance, and escrow readiness. The batch has one parent request UUID and deterministic child request UUIDs derived from the parent and stable line identifiers. Each child executes through the existing authoritative Sell to Shop escrow path.

Bulk sell is intentionally best effort across independent destinations rather than falsely claiming cross shop atomicity. A line succeeds once, fails once, or enters the existing durable recovery path. A failed line leaves its exact inputs in inventory or recoverable custody. Successful lines are not repeated when the parent request is retried. The final result reports sold lines, skipped or changed lines, recovery lines, and the authoritative paid total. A stale quote returns to a refreshed confirmation state instead of silently accepting a changed price.

This phase does not add item payouts, sell worn armor, scan unloaded chunks, bypass the configured Player Shops radius, or merge bulk sell state into the acquire cart.

### Accessibility and error behavior

Every icon and color state needs text. Disabled actions explain the reason through text or tooltip.

Stale revisions return to a refreshed detail state with a clear quote changed message. Recovery states do not invite blind retries with a new request UUID.

## Administrator offer builder

### Design goals

The editor must optimize the common path without hiding advanced behavior.

Required principles:

* One field has one meaning.
* The complete draft remains visible and editable in one workflow.
* Common listings start from plain language templates.
* Advanced sections appear through progressive disclosure.
* AND and OR relationships are visible, not implied by screen order.
* No mutation occurs while choosing items or changing draft fields.
* Save is acknowledged before the editor closes or discards its baseline.
* Every error identifies the affected section and field.
* Every administrator field and icon only control has contextual help.
* Mouse and keyboard focus behavior are first class.

### Screen structure

The fixed compact modal becomes a responsive full editor surface.

At ordinary resolutions the screen uses three regions:

* A left outline lists General, Outputs, Get Options, Sell Options, Stock and Limits, Schedule and Permissions, Bundle Value, and Preview.
* A central work area displays the selected section.
* A right summary shows the visitor facing offer, current validation state, and unsaved changes.

At narrow resolutions the outline becomes a top step strip, the summary becomes a collapsible panel, and the work area uses one scrollable column. Fields must not overlap, clip labels, or require a GUI scale reduction.

The footer remains visible and contains:

* Revert.
* Apply.
* Save and Close.
* Cancel.

Apply saves and keeps the editor open. Save and Close waits for a successful acknowledgement before returning to the shop.

### Persistent draft

The client owns an `OfferEditorDraft` while the editor and its child pickers are open.

The draft contains:

* The loaded listing identifier and revision.
* Every presentation, component, option, stock, limit, schedule, permission, and comparison field.
* Dirty paths.
* Client validation issues.
* The last successfully acknowledged baseline.
* Current outline section, scroll position, focused field, and expanded cards.

Opening an item picker passes the draft and a precise destination path. Returning restores the same section, scroll position, focus, and unsaved values.

Resizing, changing GUI scale, opening Preview, and switching editor sections must not recreate or default draft values.

Cancel or Escape with unsaved changes opens a clear discard confirmation. Escape without changes returns immediately.

The first 3.1 implementation preserves drafts for the active editor session. Persistence across game restarts is deferred.

### Quick creation flows

The first screen asks what the shop should do in visitor language:

* Give an item for money.
* Give an item for barter.
* Let the player choose money or barter.
* Require money and barter.
* Give an item for free.
* Buy items from the player.
* Offer both Buy and Sell to Shop.
* Create a bundle.
* Start from a blank advanced offer.

Each choice initializes the same normalized draft. Templates do not create a separate schema or transaction path.

A basic money listing from a held item requires only:

1. Choose Use Held Item.
2. Choose Money Only.
3. Enter the price.
4. Save.

The screen must not ask for barter, sell, schedule, comparison, or permission values unless the administrator enables those sections.

### General section

General contains:

* Display name.
* Description.
* Category.
* Icon source.
* Active state.
* Optional expiry summary.

Category uses a searchable dropdown with an explicit No Category choice. It must not require cycling through categories with arrow buttons.

Icon and exact NBT previews show the actual stack tooltip. The administrator can choose:

* Use Held Item.
* Pick from Inventory.
* Search Item Registry.

Main hand and offhand never gain hidden roles. Every picker title and destination card states whether the selected item is an output, payment component, or Sell to Shop input.

### Output editor

Outputs are item cards containing:

* Icon and full item tooltip.
* Item name and registry identifier.
* Exact NBT indicator.
* Count stepper and direct count entry.
* Drag or button based reorder controls.
* Duplicate and Remove actions.

The Add Output action opens the item picker without saving. Bundles are created by adding another output card.

The editor shows the total output stack count and warns when a bundle approaches codec or claim limits.

### Acquire option editor

The Get Options section displays option cards separated by a visible `OR`.

Each card starts with a payment type:

* Free.
* Money.
* Items.
* Money and Items.

Components inside one option are separated by a visible plus sign and an `All required` label.

Free is a radio or card choice. It is never entered as price zero.

Money fields display the configured currency name and decimal precision. They validate exact minor units without using floating point parsing.

Item payment components use the same item card and picker behavior as outputs.

Each option card supports:

* Plain language label.
* Duplicate.
* Move up and down.
* Limits and schedule override.
* Remove with confirmation when the option already exists on the server.

The summary renders examples such as:

* `10 coins`
* `or 4 iron ingots and 1 stick`
* `or 5 coins plus 2 emeralds`

### Sell option editor

The Sell Options section uses `Sell to Shop` consistently.

Each option card contains:

* One or more item input components.
* Money payout.
* Capacity behavior.
* Limits, schedule, and permission override.
* A visitor facing summary.

An empty Sell Options section means the listing does not buy from players.

Sell only is a visible listing summary, not a side effect of a zero buy price.

### Stock and limits editor

Stock uses an explicit choice:

* Unlimited stock.
* Limited stock.

Limited stock reveals the quantity and refresh controls. No text field uses blank text or an infinity character as a hidden sentinel.

Limits use separate labeled controls for request quantity, per player lifetime limit, period limit, and cooldown. Disabled limits remain summarized as Not Limited.

Bundle stock policy is shown with a concise explanation of Independent and Linked behavior. Linked remains unavailable until its transaction phase is implemented.

### Schedule, permission, and bundle value editors

Schedule shows start, end, timezone interpretation, and current active state.

Permission shows the exact permission node or access rule and a plain language visitor impact.

Bundle Value lets the administrator select comparison listings for each output. It previews:

* Individual total.
* Bundle price.
* Savings amount.
* Savings percentage.
* Any component that cannot be compared.

The editor never accepts a manually typed savings claim.

### Preview

Preview uses the same presentation model as the visitor detail screen.

It supports:

* Browse card preview.
* Detail preview.
* Option chooser preview.
* Sell only preview.
* Bundle and savings preview.
* Empty, disabled, expired, out of stock, and limit reached states.

Preview does not mutate the shop and does not bypass validation.

An Advanced Data panel may show the normalized JSON and stable identifiers for debugging. It is collapsed by default and clearly separated from normal editing.

### Contextual tooltips and help

Every administrator specific field, toggle, badge, option header, count control, icon only button, disabled action, and destructive action must provide a localized tooltip.

Visible labels remain primary. Tooltips explain behavior and edge cases rather than replacing labels.

Tooltip content follows this order:

1. What the control changes.
2. How visitors experience it.
3. Important stock, escrow, migration, or compatibility consequences.

Examples:

* Free explains that no money or items are taken, while stock and claim limits still apply.
* Money or Barter explains that the visitor chooses one option.
* Money and Barter explains that every component is required.
* Sell to Shop explains that the player gives the listed inputs and receives the payout.
* Unlimited Stock explains that Server Shop outputs are a configured system source.
* Exact NBT explains that damage, enchantments, names, and other saved data must match.
* Bundle Comparison explains that savings are calculated from validated live listing references.
* Apply explains that the editor remains open after a successful server acknowledgement.
* A disabled Save action lists the first validation issue and the number of remaining issues.

Tooltip behavior:

* Mouse hover and keyboard focus expose the same help.
* Tooltips appear after a short stable hover to reduce visual noise.
* Long text wraps inside the screen and never covers the focused field when space is available.
* Holding Shift may reveal advanced identifiers and migration details.
* An optional Help toggle keeps contextual descriptions visible beneath fields for administrators who do not want hover only help.
* Icon only controls always have an accessible name and tooltip.

Tooltip coverage is enforced by tests over the editor control registry and translation keys.

### Inline validation

Client validation runs after each draft change and uses the same pure validation rules as the offer compiler where possible.

Invalid fields show:

* A visible error outline.
* A short message beneath the field or card.
* A tooltip with the full explanation.
* A section error badge in the outline.
* A summary entry that moves focus to the field when selected.

Warnings do not block Save unless settlement would be ambiguous or unsafe.

The editor must never silently convert invalid or blank values to zero, one, or unlimited. Defaults are inserted only when a template is created and remain visible.

Apply and Save and Close remain disabled while client validation has blocking errors. Their tooltips explain why.

### Save acknowledgement and conflict handling

Version 2 editor packets use typed draft payloads rather than a generic action string with positional fields.

Every save contains:

* Request UUID.
* Shop and listing identifiers.
* Expected listing revision.
* The complete normalized candidate offer.
* The intended operation, Create, Update, Duplicate, or Remove.

The server returns:

* Correlated request UUID.
* Success or failure.
* Current listing revision.
* Structured validation issues with field paths and localized codes.
* A stale revision result with the current server snapshot.

While a save is pending, the editor shows progress and prevents duplicate submission. The draft remains intact.

On success, the acknowledged server snapshot becomes the new baseline. Apply stays open. Save and Close returns to the refreshed shop.

On validation failure, the editor stays open and focuses the first affected field.

On stale revision, the editor offers:

* Review Changes.
* Reload Server Version.
* Cancel.

The editor must not overwrite a newer server listing silently.

### Destructive actions and recovery

Remove Listing and Remove Option use named confirmation dialogs that explain the result.

Removing barter must never silently remove the entire listing. Listing removal is a separate action.

Revert restores the last acknowledged baseline after confirmation.

Reset Section restores only the selected section.

Duplicate Listing creates a new stable listing identifier and keeps the original unchanged.

Server side catalog writing retains validation, temporary file, atomic replacement, backup, and last valid catalog recovery requirements.

### Keyboard and accessibility behavior

Required behavior:

* Tab and Shift Tab follow visible field order.
* Arrow keys navigate option and component cards only when focus is not inside text entry.
* Enter activates the focused primary control.
* Escape follows picker, section, unsaved draft, and editor hierarchy predictably.
* Search fields support `Ctrl F`.
* Tooltips are available from keyboard focus.
* Color is never the only error, option, or stock indicator.
* All text clips or wraps safely at GUI scales 2 through 4.

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
* Shared bulk sell quote, confirmation, deselection, command, and Server Shop entry flow.
* All, Buy, Sell, Barter, and Bundles filters.
* Mixed acquire cart.
* Bundle component and savings presentation.
* Accessible failure and stale quote states.

Gate:

* Every required trade combination is usable without commands or manual JSON requests.
* Free never opens a payment chooser.
* Sell only never exposes Buy or Cart.
* `/sellall adminshop` and `/sellall playershops` show the same authoritative confirmation used by `Sell Inventory`.
* The `confirm` suffix skips only the screen, never server quoting, validation, escrow, replay, or recovery.
* Every selected bulk line is paid at its authoritative committed value or retains recoverable exact inputs.
* Player Shop selection prefers the highest effective valid payout within the configured browse radius and uses deterministic tie breaking.
* AND and OR requirements are visually unambiguous.

### Phase 4, administrator offer builder

Deliver:

* Responsive full screen editor and persistent `OfferEditorDraft`.
* Quick templates and complete offer editing.
* Multi component input and output editing.
* Alternative option editing.
* Bundle comparison references.
* Limits, schedule, and permission editing.
* Explicit stock, free, direction, and component role controls.
* Searchable category and item selectors.
* Contextual tooltips, visible help mode, and translation coverage.
* Inline field and section validation.
* Typed, correlated save packets with expected revisions.
* Apply, Save and Close, Revert, Duplicate, and safe Remove flows.
* Structured server errors and stale revision review.
* Validated atomic JSON writing and backups.
* Visitor preview.

Gate:

* Every required combination can be created, edited, reloaded, and removed in game.
* No field changes meaning because of the screen entry path.
* A basic held item money listing uses the four step common flow.
* Returning from any picker preserves every draft value, section, scroll position, and focus target.
* Invalid values remain visible and are never silently converted to another value.
* A rejected save keeps the editor open and identifies the exact field.
* A successful Apply keeps the editor open with a new acknowledged baseline.
* Save and Close waits for the matching successful acknowledgement.
* Stale edits cannot overwrite a newer listing without review.
* Every administrator field and icon only control has localized hover and focus help.
* Keyboard only editing reaches every control in a predictable order.
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
* Unified `config/futureshops/` registration and safe migration of legacy loose FutureShops files.
* Stable optional list specifications that do not trigger recurring Forge correction.
* Client, dedicated server, multiplayer, reconnect, restart, and migration smoke tests.
* Configuration examples.
* Administrator and player documentation.
* Release notes and compatibility matrix update.

Gate:

* No unresolved recovery or conservation failure.
* No misleading savings display.
* No unsupported old transaction or catalog state.
* No FutureShops owned configuration remains outside `config/futureshops/`.
* Valid configuration produces no recurring correction warning or backup churn.
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
* Bulk sell target parsing, quote ownership, expiry, single use, bounds, and deterministic child request identities.
* Bulk sell inventory grouping, exact NBT separation, input bundle reservation, deselection, and authoritative total calculation.
* Player Shop payout ordering, distance tie breaking, configured radius enforcement, and unloaded chunk exclusion.
* Bulk sell stale revision refresh, duplicate commit replay, partial failure summaries, and arithmetic overflow.
* Old and new commit codec round trips.
* `OfferEditorDraft` creation, dirty tracking, section reset, revert, and acknowledged baseline updates.
* Draft preservation through picker, preview, resize, and section transitions.
* Client validation issue paths and section summaries.
* Structured server validation issue mapping.
* Save request correlation, duplicate acknowledgement handling, and stale revision behavior.
* Exact minor unit price parsing without floating point.
* Canonical configuration path registration for every FutureShops specification.
* Legacy active and backup configuration migration, exact content preservation, conflict archival, unsafe path rejection, and idempotent restart.
* Empty optional currency and Auction House restriction lists remaining valid after Forge correction checks.
* Tooltip registration and translation key coverage for every administrator control.
* Removal paths that distinguish listing removal from option or barter removal.

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
* Bulk sell never reserves one inventory component for two lines.
* A failed bulk line leaves exact inputs in inventory or the existing durable recovery path.
* Replaying a bulk parent request never repeats a successful child transaction.

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
* Basic money, free, barter, compound, sell only, two way, and bundle administrator workflows.
* `/sellall adminshop`, `/sellall adminshop confirm`, `/sellall playershops`, and `/sellall playershops confirm`.
* Server Shop `Sell Inventory`, inventory selection, Select All, Clear All, line deselection, confirmation, and one step Escape return.
* Player Shop best payout selection with two competing shops and one shop becoming invalid after quote.
* Item selection from held item, inventory, and registry search.
* Picker cancellation and return with an unchanged draft.
* Client validation failure and server validation failure.
* Apply, Save and Close, Revert, Duplicate, and Remove.
* Concurrent administrator edits producing a stale revision review.
* Mouse hover and keyboard focus tooltips.
* Help mode, tab order, arrow navigation, Enter, Escape, and `Ctrl F`.
* Resize and GUI scale changes with an unsaved advanced draft.

Visual acceptance sizes include the supplied 1640 by 1350 detail screen, 568 by 112 toolbar crop, and 1430 by 1233 barter editor, plus 1280 by 720 and common GUI scales from 2 through 4.

### Required commands

Run applicable checks in this order:

1. `bash ./gradlew test`
2. `bash ./gradlew runData` when generated resources change
3. `bash ./gradlew runGameTestServer` when GameTests are added
4. `bash ./gradlew build`
5. `bash ./gradlew runServer` for dedicated server smoke testing
6. `bash ./gradlew runClient` for client and interface smoke testing

Multiplayer and reconnect scenarios require a real client connected to the dedicated server.

## Documentation obligations

Implementation must update:

* `README.md` for user visible feature and setup changes.
* `DOCUMENTATION.md` for architecture, schema, commands, recovery, and migration.
* `docs/config-3.0-examples.md` or its versioned successor with complete offer examples.
* `docs/compatibility-matrix.md` for protocol and save compatibility.
* Administrator tooltips and translations for every new action and validation error.
* Command help, bulk sell confirmation help, target rules, best effort behavior, and recovery troubleshooting.

Unverified features must not be described as accepted before their phase gate passes.

## Approval decisions

The following owner approved defaults are part of this plan:

* Legacy zero prices remain disabled, not free.
* Free requires an explicit option.
* Alternatives use OR, components inside an option use AND.
* Server Shop bundle stock is independent by default.
* Savings use validated same shop listing references and current effective money prices.
* Sell to Shop pays money in 3.1.
* Acquire cart supports mixed option types.
* Sell to Shop stays outside the acquire cart in 3.1.
* Bulk sell is a separate quoted batch shared by commands and the Server Shop interface.
* Player Shop bulk sell searches only the configured local listings radius and prefers the highest effective valid payout.
* Bulk sell excludes worn armor and preserves exact NBT variants.
* Server Shop is implemented before Player Shop convergence.
* Old transaction codecs remain readable.
* Administrator editing uses one persistent draft rather than immediate component mutations.
* Free, unlimited stock, direction, and component roles use explicit controls.
* Apply stays open, while Save and Close waits for a successful correlated acknowledgement.
* Tooltips are available through mouse hover and keyboard focus, with an optional visible help mode.
* Removing barter never removes the listing implicitly.
* Arbitrary scripts, randomized rewards, partial fulfillment, and negative prices remain out of scope.
