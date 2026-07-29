# FutureShops 3.1 beta release notes

FutureShops 3.1 replaces separate special case shop trades with one clear offer model. This is a beta build for Minecraft 1.20.1 and Forge 47.4.20. Client and server must use the same build because the network protocol is now 57.

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
* Live multiplayer, reconnect, restart, migration, and injected recovery acceptance must pass before this beta is approved for release.
