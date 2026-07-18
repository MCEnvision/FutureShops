# Backup and restore

FutureShops 3.0 stores the economy in durable escrow state inside the world save. This guide
covers what that state is, how the one-time 3.0 migration behaves, and how to take and restore a
backup without corrupting or forking the economy.

The single most important rule: **back up the world before starting it on 3.0 for the first
time.** The migration from 2.x wallets, mint records, and settlements into escrow is one-way. The
second most important rule: **a backup is the whole world directory, restored as one unit.**
FutureShops state is deliberately cross-checked between files, and a restore that mixes files from
different snapshots is detected and refused.

## Where FutureShops keeps its data

### SavedData stores — `<world>/data/futureshops_*.dat`

Each store is one `.dat` file named after its data name.

Escrow core (fail closed on damage or newer schemas):

| Store | Contents |
|---|---|
| `futureshops_escrow_runtime` | Journal cursor, journal lineage, maintenance state |
| `futureshops_escrow_transactions` | Canonical escrow transaction records |
| `futureshops_escrow_ledger` | Double-entry money ledger |
| `futureshops_escrow_custody` | Exact item stacks and cash held in escrow |
| `futureshops_escrow_claims` | Lost and Found claims (money, items, cash) |
| `futureshops_escrow_protected_mints` | Protected bill mint batches and their states |
| `futureshops_escrow_stock` | Durable admin catalog stock and reservations |
| `futureshops_escrow_item_inventory_journal` | Inventory mutation receipts |
| `futureshops_escrow_server_shop_intents` | Server shop transaction intents |
| `futureshops_escrow_player_shop` | Player shop escrow state |
| `futureshops_escrow_administrative_audit` | Immutable admin action audit records |

Markets:

| Store | Contents |
|---|---|
| `futureshops_auction_house` | Auction listings, bids, and settlement state |
| `futureshops_bazaar` | Bazaar products, orders, and fills |
| `futureshops_market_control` | Module freeze/halt control state |
| `futureshops_market_profiles` | Watchlists and per-player market profiles |

Migration markers (must never be separated from the stores they describe):

| Store | Contents |
|---|---|
| `futureshops_legacy_balance_migration` | Progress and IDs of the 2.x wallet import |
| `futureshops_catalog_stock_migration` | One-time catalog stock seeding marker |

Pre-3.0 stores that remain in use or are retained as the migration source:
`futureshops_balances`, `futureshops_coin_mints`, `futureshops_tx_history`,
`futureshops_player_shop_registry`, `futureshops_player_shop_settlements`,
`futureshops_saved_configs`, `futureshops_departments`, `futureshops_admin_categories`,
`futureshops_shop_limits`, `futureshops_stock_refresh`, `futureshops_dynamic_pricing`.

### Escrow journal and checkpoints — `<world>/futureshops/escrow/`

This directory is the write-ahead journal that makes escrow crash-safe. Critical records are
forced to disk *before* the side effect they describe, and startup replays the journal on top of
the latest verified checkpoint.

- `journal-<uuid>.wal` — append-only journal generations
- `journal.active` — pointer to the active journal generation
- `checkpoint-<uuid>.fscp` — verified materialized checkpoints
- `journal.wal` / `journal.legacy.wal` — the initial journal and its archived form after a
  generation upgrade

The server keeps `persistence.checkpoint_generation_retention` (default 2) verified checkpoint and
journal pairs. Never hand-edit, delete, or selectively restore files in this directory. A
truncated final record after a crash is discarded safely on its own; anything else — checksum
mismatch, missing files, an unsupported newer schema — sends the economy into maintenance mode,
where new value-moving operations stop and safe browsing, claims, and recovery remain available.

### Player data — `<world>/playerdata/`

Escrow delivery receipts are verified against vanilla `playerdata/<uuid>.dat` files during
recovery. This is another reason backups must be whole-world: restoring FutureShops data without
the matching player data can leave deliveries in manual review.

### Configuration — `config/`

`futureshops-common.toml`, `futureshops-escrow.toml`, `futureshops-auction-house.toml`,
`futureshops-bazaar.toml`, plus the JSON directories `config/futureshops/shops/` and
`config/futureshops/bazaar/products/`. Config is not part of the world save, so include the
`config/` directory in your backup routine. Active listings and orders snapshot their rules, so a
restored world remains internally consistent even against a newer config, but keeping config and
world from the same point in time avoids surprises.

## The 3.0 migration

On first 3.0 startup against an existing world, FutureShops:

1. Imports 2.x wallet balances into the ledger with deterministic migration IDs
   (rate-limited by `migration.wallet_entries_per_tick`, so large servers migrate over
   several ticks without a startup stall).
2. Imports pending player shop settlement money as owner claims.
3. Preserves lifetime statistics as analytics only.
4. Imports mint records into authorized, available, and spent states.
5. Preserves legacy transaction history without fabricating transaction IDs.
6. Seeds durable catalog stock from configuration once.
7. Verifies totals and conservation, then writes a migration checksum and completion sequence.

The legacy stores are retained until the new checkpoint verifies. Because the import IDs are
deterministic, an interrupted migration resumes safely on the next start — it cannot double-import
a wallet.

## Newer schema enters read-only recovery

Every escrow and market store records a schema version. If a store or the journal was written by a
**newer** FutureShops build than the one loading it, loading fails closed
(`... schema is newer than this build`) and the escrow runtime enters maintenance mode rather than
guessing at data it does not understand. New value-moving operations stop; nothing is deleted.

Practical consequence: **you cannot downgrade a world in place.** To go back to an older build,
restore the whole-world backup taken before the upgrade. (Legacy 2.x-era stores log a downgrade
warning instead of failing closed — do not rely on that; treat all downgrades as restore-only.)

## Backup lineage

Each escrow journal carries a lineage UUID recorded both in the journal itself and in
`futureshops_escrow_runtime`. On startup the two are compared; if they disagree — the classic
symptom of restoring `<world>/data/` from one snapshot and `<world>/futureshops/escrow/` from
another, or of copying escrow files between worlds — the server refuses with
`Escrow journal lineage does not match` and enters maintenance instead of silently forking the
economy.

This is a safety feature. The fix is never to delete the "offending" file; it is to restore all
FutureShops state from one consistent snapshot.

## Taking a backup

1. Prefer a stopped server. FutureShops performs a clean shutdown drain — it blocks new
   transactions, forces the journal, and checkpoints — so a cold copy is always consistent.
2. Copy the **entire** world directory, including `data/`, `futureshops/`, and `playerdata/`.
3. Copy the `config/` directory (TOMLs plus `config/futureshops/`).
4. If you must back up hot, use a filesystem-level snapshot that captures the whole world
   atomically. Do not file-copy a running world piecemeal — the copy can span a commit boundary.

## Restore procedure

1. Stop the server completely.
2. Move the current world directory aside (do not delete it — it may hold committed transactions
   newer than your backup, and an aborted restore should be reversible).
3. Restore the whole world directory from **one** backup snapshot: `data/`,
   `futureshops/escrow/`, `playerdata/`, and everything else, together.
4. Restore or verify the matching `config/` contents if your configuration changed since the
   snapshot.
5. If you are also changing the mod version, remember: the restored world must not have been
   written by a newer FutureShops build than the jar you are starting.
6. Start the server and watch the log. Expect checkpoint load, journal replay, and recovery
   messages; escrow finishes recovery before new value operations are accepted.
7. Verify: spot-check a few balances, open the Claims tab on an affected account, and confirm the
   Auction House and Bazaar show the expected listings and orders.
8. If the server reports maintenance mode, read the logged reason. Lineage or schema complaints
   mean the restore mixed snapshots or builds — go back to step 2 with a consistent snapshot.
   Never respond to maintenance mode by deleting journal or checkpoint files.

Be aware of what a restore means economically: escrow guarantees the restored state is internally
consistent, but everything after the snapshot — sales, bids, fills, deposits — is gone for all
players equally. Restores are for disaster recovery, not for undoing individual trades; for those,
use administrative recovery, which produces auditable, journaled corrections instead.
