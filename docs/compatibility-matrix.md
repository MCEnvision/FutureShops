# FutureShops 3.1 compatibility matrix

This matrix describes the supported release boundary for the escrow, ATM, Auction House, Bazaar,
shop transaction system, and normalized trade offers.

| Area | Supported | Behavior and boundary |
| --- | --- | --- |
| Minecraft and loader | Minecraft 1.20.1 with the repository Forge toolchain | Client and server must run compatible FutureShops builds. Protocol 56 rejects peers that do not share normalized offers, bulk sell quotes and commits, owner saves, escrow readiness, market capability recovery, and exact ATM deposit recovery contracts. |
| Dedicated and integrated servers | Yes | Escrow, claims, auctions, Bazaar orders, physical cash, and recovery use server-owned data. |
| Windows world saves | Yes | Checkpoint and journal files are forced before atomic moves. Windows does not expose directory fsync through Java, so the unavailable directory barrier uses an explicit best-effort fallback and logs one warning instead of crashing world startup. |
| Built-in physical currency | `currency.provider = "futureshops"` | Mint IDs, checksums, reservations, spent-mint tracking, replay protection, and replacement-mint refunds are enforced. |
| Apocalypse Now currency | `currency.provider = "apocalypsenow"` | Currency items can be deposited and dispensed, but their supply is not protected by FutureShops mint controls. Verify the installed mod item IDs before production. |
| Other mod currency | `currency.provider = "custom"` | Up to 32 configured dispense denominations plus accept-only items. Requests remain journaled and idempotent, but copied items, recipes, loot, and source-mod bugs are outside FutureShops duplication protection. |
| Wallet and inventory cash funding | Yes | Shop purchases, auction listing fees, bids, Buy Now, and Bazaar buys can prompt for wallet or inventory cash. Cash is consumed through escrow before the economic action. |
| Forge permission plugins | Forge PermissionAPI Boolean nodes | Dedicated Auction House, Bazaar, and escrow nodes are registered. Vanilla operator levels remain the fallback. Claims are not confiscated when trade access is revoked. |
| Bazaar item identity | Admin JSON catalog or player selected registry commodities | Admin mode supports plain items and configured exact NBT identity. Players mode searches every registered base item and can create a buy market without possession. Every sell remains inventory backed. Product versions are immutable after escrow use. |
| Auction item identity | Complete serialized stack | NBT, damage, names, enchantments, contents, and supported capability data remain in custody. Unsafe containers and capabilities follow the Auction House restrictions config. |
| External item storage | Player inventory for release contracts | Direct listing from third-party storage is unsupported until an adapter provides deterministic transaction receipts and exact preimage and postimage reconciliation. |
| Server Shop offer schema | Legacy catalogs and schema version 2 | Legacy positive prices and barter recipes compile into normalized offers. Legacy zero prices stay disabled. Schema version 2 adds explicit free, alternative and compound payment, Sell to Shop, bundles, limits, schedules, permissions, and validated comparisons. |
| Player Shop offers | Versioned block entity persistence | Existing money, barter, direction, bundle output, stock, and settlement data migrates in memory. New offers keep owner storage authoritative and support explicit free, several required item components, alternatives, input bundles, and both directions. |
| Bulk inventory selling | Main inventory and offhand through authoritative Sell to Shop offers | Server Shop and nearby Player Shop targets share one bounded confirmation contract. Worn armor, unloaded chunks, and shops outside the configured nearby radius are excluded. Every destination reuses its existing escrow transaction and recovery behavior. |
| Interrupted Server Shop offers | Automatic bounded recovery | Exact persisted single and cart evidence is retried on player login and in round robin background passes while escrow is ready. Recovery does not require the shop module or visitor screen to remain open. |
| Successful Server Shop replay identity | Immutable filesystem ledger | Exact receipts and an append only discovery index live under `<world>/futureshops/escrow/offer_replay/`. Completed success identities have no lifetime transaction count cap. Disk, path, identity, or checksum failure stops new mutations instead of forgetting an older request. |
| Linked Server Shop bundle stock | No | Schema version 2 rejects `LINKED` stock until atomic multi component linked reservation is implemented. Player Shop linked physical storage remains supported through its existing escrow adapter. |
| Bundle savings | Validated live comparisons only | The server calculates individual value from active compatible money offers and matching output components. Missing, stale, recursive, item cost, or incompatible comparisons do not produce a savings claim. |
| Single-server worlds | Yes | One authoritative server owns the journal and saved data. |
| Distributed networks | No shared order book | Do not mount one world or escrow journal on multiple live servers. Proxy networks need one authoritative economy server or a future receipt-based bridge. |
| Config reload | Validated snapshots | Invalid module TOML or Bazaar product JSON is rejected atomically and the last valid snapshot remains active. Existing contracts retain their rule snapshots. |
| Module disable and re-enable | Freeze, drain, and cancel-and-refund controls | Data is preserved. Claims and owner cancellation routes remain available according to the selected lifecycle policy. |
| Backup and restore | Whole-world consistent snapshots | Back up world data, the escrow journal directory, player data, and config together. Mixed-generation restores fail closed. Follow [backup-restore.md](backup-restore.md). |
| Legacy data | Versioned migration readers | Supported older FutureShops data is migrated once with durable markers. Newer, corrupt, or unverifiable custody data fails closed instead of being guessed. |
| Client presentation | Wide, medium, and narrow shared market layouts | Server Shop, Player Shops, Bazaar, and Auction House use the same Nocturne shell and unified accent. GUI density, scale, accessibility, global theme, and time format remain configurable. |

## Permission nodes

Auction House nodes are `futureshops.auction.use`, `futureshops.auction.create`,
`futureshops.auction.bid`, `futureshops.auction.buy`, `futureshops.auction.claim`, and
`futureshops.auction.admin`.

Bazaar nodes are `futureshops.bazaar.use`, `futureshops.bazaar.order`,
`futureshops.bazaar.instant`, `futureshops.bazaar.claim`, and `futureshops.bazaar.admin`.

Shared escrow nodes are `futureshops.escrow.claim` and `futureshops.escrow.admin`.

## Foreign currency safety boundary

Selecting any provider other than `futureshops` intentionally disables FutureShops physical
currency duplication protection. Escrow still prevents the same FutureShops request from applying
twice, but it cannot establish whether a foreign item was legitimately crafted, looted, copied,
or supplied by another mod. Test every conversion recipe and storage path before assigning a
production exchange value.
