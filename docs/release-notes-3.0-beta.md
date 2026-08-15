# FutureShops 3.0.0 beta release notes

FutureShops `3.0.0-beta.1` packages the complete merged 3.0 implementation for live validation. It replaces separate special case shop trades with one clear offer model and includes the recovery, catalog, dependency, concurrency, interface, and transaction fixes from the historical 3.0 beta builds. This is a beta build for Minecraft 1.20.1 and Forge 47.4.20, not a stable release. Client and server must use the same build because the network protocol is 57.

## 3.0.0 beta 1

* The public artifact and metadata version is `3.0.0-beta.1`.
* The same verified JAR must be distributed on CurseForge and Modrinth as a beta for client and server installations.
* Existing `3.0.0` beta worlds remain compatible. Back up the complete world and configuration before upgrading, and do not delete escrow recovery data.
* Minecraft remains `1.20.1`, Forge remains `47.4.20`, Java remains 17, and the network protocol remains 57.
* This beta includes every repair listed below through the legacy exact item journal compatibility work from `3.0.0-beta.11`.
* Open reporter validation remains tracked separately. This beta publication does not claim stable release acceptance.

## Issue 23 ATM and catalog recovery

* Legacy exact item inventory intents now validate their slot hashes from the exact serialized evidence that originally created the token. A decode and reencode cycle cannot invalidate an otherwise intact checkpoint.
* Exact item comparisons treat compound key order as irrelevant while preserving registry ID, count, item tag, Forge capability data, list order, and primitive values.
* New item inventory plans derive slot hashes and journal evidence from the same owned snapshots, preventing defensive copies from producing different proofs for one mutation.
* The unsafe force clear path proposed in the supplied combined patch is intentionally excluded. Maintenance still requires journal alignment, conservation verification, permission, confirmation, reason, and a durable audit record.
* Modded item capability compounds use deterministic canonical evidence, so semantically equal copied stacks no longer reject ATM cash collection because of compound key order.
* A delivery receipt verifies only the slots changed by that delivery. Unrelated inventory changes no longer create a false unknown result, while a changed delivery slot still fails closed.
* Existing version one inventory delivery tokens and receipts remain readable under their original hash contract.
* ATM claim rejection, request gate failure, and escrow maintenance entry now retain bounded recovery diagnostics in the server log.
* `/marketadmin maintenance status`, `/marketadmin maintenance verify`, and `/marketadmin maintenance resume confirm <reason>` expose the existing verified and journaled maintenance recovery workflow.
* `admin_shop.maximum_listings` defaults to 512 and can be configured up to the hard limit of 10000.
* `/marketadmin adminshop validate` identifies exact invalid listings and fields. `/marketadmin adminshop quarantine_missing confirm <reason>` preserves complete removed mod listings in a recovery file before updating and reloading the active catalog.
* This repair was first verified in the historical `3.0.0-beta.11` build and is included in the new public `3.0.0-beta.1` build.

## Exact item delivery maintenance

* Large purchases no longer deliver dozens of exact item claims and force dozens of player saves in one server tick.
* Automatic delivery permits one exact item durability operation per tick while continuing to service bounded money claims.
* Existing configurations receive the safe exact item operation limit automatically. The older general delivery work setting cannot override it.
* Full inventories continue preserving exact items as durable claims. No item value is dropped, deleted, or converted.
* This repair was first verified in the historical `3.0.0-beta.9` build and is included with the later recovery work in the new public `3.0.0-beta.1` build.

## Beta 7 escrow concurrency maintenance

* Escrow value operations now acquire the active runtime through the owning logical server thread.
* Readiness checks and their dependent wallet, claim, recovery, checkpoint, and replay operations use the same monitor.
* Runtime shutdown, recovery state changes, and replay compaction cannot invalidate an accepted operation between its check and use.
* Persistent data, journals, packets, configuration, pricing, and replay formats are unchanged.
* The beta artifact and metadata version is `3.0.0-beta.7`.

## Beta 6 dependency maintenance

* MixinGradle now uses the fixed `0.7.38` release instead of a moving snapshot.
* The Foojay toolchain resolver convention is updated to `1.0.0` for the Java 17 build.
* JUnit Jupiter and the JUnit Platform launcher are aligned at `6.1.2` through JUnit dependency metadata.
* Gradle remains pinned to `8.14.4` because ForgeGradle rejects Gradle 9 before project compilation.
* These build and test dependency changes do not alter gameplay, saved data, configuration, or the network protocol.
* The beta artifact and metadata version is `3.0.0-beta.6`.

## Beta 5 dependency maintenance

* Every reported transitive dependency alert now has a runtime or build classification, FutureShops reachability evidence, a patched boundary, and an explicit accepted risk disposition in [Dependency alert disposition for 3.0.0 beta 5](security/dependency-alerts-3.0-beta.5.md).
* The build now fails if Netty, Apache Commons, Guava, Log4j, Plexus, or Forge Jar in Jar metadata is accidentally bundled in the FutureShops JAR.
* Dependabot monitors Gradle and GitHub Actions weekly. Minor and patch updates are grouped, while major platform changes remain visible for compatibility planning.
* This maintenance build does not replace launcher supplied libraries or change the pinned Minecraft 1.20.1 and Forge 47.4.20 compatibility boundary.
* The beta artifact and metadata version is `3.0.0-beta.5`.

## Beta 4 maintenance

* Sell to Shop now calculates the authoritative payout from the configured unit value and selected quantity. Direct selling and Sell Inventory use the same corrected quote.
* A cart response timeout no longer leaves quantity, remove, and Clear controls locked forever.
* Check Result resends the exact original checkout identity and item snapshot. Visible cart edits remain separate, and a different checkout cannot begin until the original request reaches a terminal result.
* Dependabot now covers Gradle and GitHub Actions without hiding major updates. The 25 inherited platform and toolchain alerts are classified in [Dependency alert disposition for 3.0.0 beta 4](security/dependency-alerts-3.0-beta.4.md).
* The beta artifact and metadata version is `3.0.0-beta.4`.

## Player changes

* Free offers have an explicit Get action and never open a payment source chooser.
* Shops can buy items without exposing an unrelated Buy action.
* Acquire options can use money, barter items, money plus barter items, or several alternative choices.
* One option can require several item components. Every component joined by And is required.
* Output bundles deliver every displayed item as one atomic trade.
* Sell to Shop bundles consume every displayed input or consume nothing.
* Bundle savings appear only when the server can verify every comparison against active compatible listings.
* All, Buy, Sell, Barter, and Bundles filters use the normalized offer data.
* Player Shop visitors use the same option wording and explicit free behavior as Server Shops.
* Missing required visitor items disable that option before confirmation. The server still performs the authoritative validation.
* Sell Inventory in the Server Shop opens a clear review screen showing every eligible line, its destination, its payout, and the selected total.
* `/sellall adminshop` and `/sellall playershops` open the same review. Adding `confirm` submits every eligible line immediately.
* Nearby Player Shop bulk selling stays inside the configured browse radius and prefers the best valid payout before distance.

## Administrator changes

* Add Items uses the selected Buy, Sell, Barter, or Bundles filter and opens a searchable grid with up to 21 columns by 8 rows.
* Buy and Sell can save one selected item directly with Base Price. Entering `1` or `1.00` means one major currency unit.
* Open Simple Editor preserves the quick selection and common values. Advanced Editor exposes uncommon limits, schedules, exact NBT, permissions, and option structures.
* The in game offer editor supports templates for money, free, barter, compound, alternative, Sell to Shop, two way, and bundle listings.
* Outputs, acquire item costs, and sell inputs can contain several components.
* Category and item pickers preserve the complete draft while switching screens.
* Limits, cooldowns, schedules, permissions, stock, exact NBT, and bundle comparisons are editable.
* Visitor preview uses the same offer presentation model as the live shop.
* Fields and controls provide localized contextual help, inline validation, section status, and a validation summary.
* Apply remains open after the matching successful acknowledgement. Save and Close waits for that acknowledgement before returning.
* Stale revisions cannot overwrite newer server data without review.
* Catalog writes validate the full candidate, use a temporary sibling and atomic replacement where supported, retain a backup, and preserve the last valid catalog after reload failure.
* Existing `ShopTransactionEvent` and `BarterTradeEvent` integrations remain available. Event price changes cannot turn a paid offer into a free offer.

## Player Shop migration

Existing Player Shop listings migrate into versioned normalized offers while preserving their owner, stock source, proceeds, direction, money price, barter requirement, exact NBT, and bundle output behavior. New normalized offers remain backed by the same physical shop storage and escrow recovery system.

Back up the complete world and configuration before testing an upgrade. Do not downgrade an upgraded world in place. Follow [Backup and restore](backup-restore.md).

## Recovery and security

Offer requests carry a stable request UUID and trusted listing revision. Replays with the same identity return the stored outcome. Reuse with different trade details fails as a conflict. Prepared evidence, value commits, terminal outcomes, and usage limits survive restart. Successful normalized Server Shop receipts move into an immutable sharded replay ledger with no lifetime success transaction cap. Capacity and durable replay storage are checked before stock or value mutation.

Interrupted normalized Server Shop single and cart requests retry automatically from exact persisted evidence when the player logs in and through bounded background recovery while escrow is ready. Recovery does not depend on an open screen or a currently enabled shop module, and it cannot substitute current client state for the original request.

Packet identifiers, collection counts, exact NBT, individual listings, and aggregate catalogs are bounded. Server rate limiting runs before replay lookup and storage work. Prices, savings, permissions, stock, schedules, and item identity are always rebound to server state.

An enabled Bazaar or Auction House remains visible while its escrow or lifecycle control is recovering and reports recovery instead of module disabled. A module disabled in the common server configuration is omitted from ordinary navigation.

## Known boundaries

* This beta assumes one authoritative server and world.
* Direct market listing from third party storage remains unsupported without deterministic transaction receipts.
* Server Shop `LINKED` bundle stock is rejected until atomic linked component reservation is available.
* Fractional item counts, fuzzy NBT, recursive offers, negative prices, partial fulfillment, and cross shop bundle references are unsupported.
* Live multiplayer, reconnect, restart, migration, and injected recovery acceptance must pass before a stable release is approved.
