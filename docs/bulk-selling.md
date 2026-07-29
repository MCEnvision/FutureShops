# Bulk inventory selling

FutureShops can quote and sell several eligible inventory items through the same authoritative Sell to Shop offers used by one item transactions.

## Entry points

| Entry point | Behavior |
| --- | --- |
| Server Shop, Sell Inventory, Choose Items | Opens the review with no lines selected |
| Server Shop, Sell Inventory, Sell All | Opens the review with every eligible line selected |
| `/sellall adminshop` | Reviews eligible sales to the default Server Shop |
| `/sellall adminshop confirm` | Immediately submits every eligible Server Shop line |
| `/sellall playershops` | Reviews eligible sales to nearby Player Shops |
| `/sellall playershops confirm` | Immediately submits every eligible nearby Player Shop line |

The existing Sell filter remains the one item at a time workflow.

## Quote behavior

The server scans the main inventory and offhand. Worn armor is excluded. Each line shows the item or required input bundle, quantity, destination, unit payout, and total payout. Lines without an accepted destination remain visible but disabled. Players may tick or untick eligible lines before confirming.

Server Shop quotes use the active shop when opened from its screen. The command targets the default Server Shop. Player Shop quotes scan only the configured nearby listing radius, current dimension, and already loaded chunks. They do not load chunks or search every shop on the server.

When several nearby destinations accept the same inventory, FutureShops prefers the highest effective payout. Equal effective values prefer the higher complete exchange payout, then the closer shop, then stable identifiers. Inventory components are reserved in the quote so one stack cannot appear in two selected lines.

Quote planning checks current offer limits, buyback capacity, stock insertion space, shop funds, player balance limits, permissions, and schedules. If a destination cannot accept the full inventory quantity, the planner finds the largest currently executable quantity. Accepted items with no current capacity remain visible and disabled instead of appearing sellable.

Exact NBT offers match only that exact variant. General item offers accept matching tagged, renamed, enchanted, damaged, or otherwise modified stacks. Exact requirements reserve first, then general requirements may consume the remaining matching variants. Operators should use exact NBT when a Sell to Shop offer must accept only one specific variant.

Building the quote is a read only operation and does not require an interactive shop session to exist first. Confirming a line opens and validates the short lived authoritative session required by the actual transaction.

## Confirmation and settlement

A quote is bound to one player, expires after 60 seconds, and may be committed once. The client commit contains only the quote UUID and selected line UUIDs. It cannot submit prices, quantities, shop locations, or offer revisions.

The server revalidates every selected line. A changed listing revision expires the quote before value mutation and the open review requests a replacement. An event or integration may raise a payout, but a line is rejected before mutation if it would settle below the displayed amount.

Use Tab and Shift Tab to move through visible line toggles and actions. Enter or Space activates the focused control. Page Up, Page Down, Home, and End move through long quotes. Focused controls expose the same help as mouse hover. Escape returns to the originating shop once, or closes a command opened review once.

Each destination uses the existing Server Shop or Player Shop escrow transaction. The batch is best effort because unrelated shops cannot share one atomic storage commit. Successful lines remain successful if another line rejects. Every line has a deterministic child request UUID, so a retry cannot sell it twice. Recovery and claims use the existing durable escrow evidence.

The result reports sold lines, failed lines, recovery lines, and the complete payout. Value routed into a durable claim is included in the payout and remains available through `/claims`.

## Failure handling

If nothing matches, the review shows the inventory as unavailable or reports that the sellable inventory is empty. If a quote expires, wait for the replacement quote before confirming again. If a line enters recovery, do not repeat the sale with another command. Open `/claims`, check `/marketadmin status`, and inspect the recovery handle when one is reported.

Bulk quotes are memory only. They are cleared on cancellation, logout, or server stop. Durable child transactions and claims remain recoverable through their normal escrow evidence.
