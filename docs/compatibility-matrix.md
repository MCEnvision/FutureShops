# FutureShops 3.0 compatibility matrix

This matrix describes the supported release boundary for the escrow, ATM, Auction House, Bazaar,
and shop transaction system.

| Area | Supported | Behavior and boundary |
| --- | --- | --- |
| Minecraft and loader | Minecraft 1.20.1 with the repository Forge toolchain | Client and server must run the same FutureShops build. Protocol 47 rejects mismatched market packets. |
| Dedicated and integrated servers | Yes | Escrow, claims, auctions, Bazaar orders, physical cash, and recovery use server-owned data. |
| Windows world saves | Yes | Checkpoint and journal files are forced before atomic moves. Windows does not expose directory fsync through Java, so the unavailable directory barrier uses an explicit best-effort fallback and logs one warning instead of crashing world startup. |
| Built-in physical currency | `currency.provider = "futureshops"` | Mint IDs, checksums, reservations, spent-mint tracking, replay protection, and replacement-mint refunds are enforced. |
| Apocalypse Now currency | `currency.provider = "apocalypsenow"` | Currency items can be deposited and dispensed, but their supply is not protected by FutureShops mint controls. Verify the installed mod item IDs before production. |
| Other mod currency | `currency.provider = "custom"` | Up to 32 configured dispense denominations plus accept-only items. Requests remain journaled and idempotent, but copied items, recipes, loot, and source-mod bugs are outside FutureShops duplication protection. |
| Wallet and inventory cash funding | Yes | Shop purchases, auction listing fees, bids, Buy Now, and Bazaar buys can prompt for wallet or inventory cash. Cash is consumed through escrow before the economic action. |
| Forge permission plugins | Forge PermissionAPI Boolean nodes | Dedicated Auction House, Bazaar, and escrow nodes are registered. Vanilla operator levels remain the fallback. Claims are not confiscated when trade access is revoked. |
| Bazaar item identity | Admin JSON catalog or player-added registry commodities | Admin mode supports plain-item and configured exact NBT identity. Player mode accepts tagless, undamaged held items. Product versions are immutable after escrow use. |
| Auction item identity | Complete serialized stack | NBT, damage, names, enchantments, contents, and supported capability data remain in custody. Unsafe containers and capabilities follow the Auction House restrictions config. |
| External item storage | Player inventory for release contracts | Direct listing from third-party storage is unsupported until an adapter provides deterministic transaction receipts and exact preimage and postimage reconciliation. |
| Single-server worlds | Yes | One authoritative server owns the journal and saved data. |
| Distributed networks | No shared order book | Do not mount one world or escrow journal on multiple live servers. Proxy networks need one authoritative economy server or a future receipt-based bridge. |
| Config reload | Validated snapshots | Invalid module TOML or Bazaar product JSON is rejected atomically and the last valid snapshot remains active. Existing contracts retain their rule snapshots. |
| Module disable and re-enable | Freeze, drain, and cancel-and-refund controls | Data is preserved. Claims and owner cancellation routes remain available according to the selected lifecycle policy. |
| Backup and restore | Whole-world consistent snapshots | Back up world data, the escrow journal directory, player data, and config together. Mixed-generation restores fail closed. Follow [backup-restore.md](backup-restore.md). |
| Legacy data | Versioned migration readers | Supported older FutureShops data is migrated once with durable markers. Newer, corrupt, or unverifiable custody data fails closed instead of being guessed. |
| Client presentation | Wide, medium, and narrow shared market layouts | Shop uses blurple, Bazaar green, and Auction House red by default. GUI density, scale, accessibility, theme, time format, and market accents are configurable. |

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
