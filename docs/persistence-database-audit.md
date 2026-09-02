# Persistence and database audit

This document records the phase 005 audit of FutureShops persistence at the Forge
`1.20.1` phase branch. It is an implementation audit, not a release statement.

## Result

FutureShops has no embedded or remote database dependency. A repository and build
scan found no SQLite, JDBC, H2, MongoDB, PostgreSQL, Redis, LevelDB, RocksDB, or
other database driver in `build.gradle`, `gradle.properties`, `settings.gradle`,
`libs`, resources, or Java sources. Authoritative state uses Minecraft world
`SavedData`, block and player NBT, bounded file stores, JSON catalogs, and Forge
TOML configuration.

The audit found and repaired three classes of bounded decoding defects before the
phase repair commit. Admin category state now bounds categories, hidden base
categories, assignments, names, and item identifiers. Shop block state now bounds
persisted listings, bundle outputs, listing identifiers, and listing NBT, and its
config snapshot and save paths enforce the same listing bound. The remaining world
stores now reject oversized collections, invalid nested types, invalid identities,
and oversized text or snapshots. Replay and market profile readers also assert
the raw NBT list element type, so a malformed list cannot be silently read as an
empty list and discard durable outcomes. Each finding was filed before its
first repair edit in GitHub issues
[48](https://github.com/MCEnvision/FutureShops/issues/48),
[49](https://github.com/MCEnvision/FutureShops/issues/49),
[50](https://github.com/MCEnvision/FutureShops/issues/50),
[51](https://github.com/MCEnvision/FutureShops/issues/51), and
[52](https://github.com/MCEnvision/FutureShops/issues/52). Player inventory
evidence readers now reject malformed raw list element types before hashing or
delivery, so unrelated modded inventory data cannot be normalized or accepted
as an empty evidence list.

## Authoritative state inventory

All world `SavedData` is obtained from overworld `DataStorage.computeIfAbsent`.
Escrow stores bind to the active server through `EscrowRuntimeStoreBinding`. The
following inventory lists every concrete `SavedData` class found under
`src/main/java`.

| Store identity | Owner and purpose | Schema or bounds | Recovery relationship |
| --- | --- | --- | --- |
| `futureshops_escrow_runtime` | Journal cursor, lineage, maintenance, and recovery state | schema 3, bounded incident text | Root of escrow readiness and replay |
| `futureshops_escrow_transactions` | Canonical escrow transactions | schema 1, 1,000,000 records | Transaction identity and journal lineage |
| `futureshops_escrow_ledger` | Double entry money ledger | schema 2, 1,000,000 entries | Conservation and replay receipt |
| `futureshops_escrow_custody` | Held money and exact item lots | schema 3, 1,000,000 entries | Claims and delivery evidence |
| `futureshops_escrow_claims` | Durable money and item claims | schema 3, 1,000,000 claims | Remains reachable during freeze and disable |
| `futureshops_escrow_protected_mints` | Protected physical currency batches | schema 1, repository batch and receipt bounds | Mint authenticity and deposit redemption |
| `futureshops_escrow_stock` | Durable catalog stock and reservations | schema 1, encoded snapshot bounds | Stock reservation and release |
| `futureshops_escrow_item_inventory_journal` | Item inventory mutation evidence | schema 1, codec bounds | Exact slot proof and recovery |
| `futureshops_escrow_server_shop_intents` | Server shop intent records | schema 1, 100,000 entries and payload bounds | Prepare and replay identity |
| `futureshops_escrow_player_shop` | Player shop escrow state | schema 1, 100,000 entries and payload bounds | Player shop custody and claims |
| `futureshops_escrow_administrative_audit` | Immutable administrative audit rows | schema 1, 1,000,000 records | Recovery actions and operator accountability |
| `futureshops_server_shop_offer_prepared` | Prepared normalized offer evidence | schema 2, entry and archive bounds | Single offer prepare boundary |
| `futureshops_server_shop_offer_commits` | Committed normalized offer evidence | schema 2, commit and archive bounds | Single offer commit boundary |
| `futureshops_server_shop_offer_cart_prepared` | Prepared cart evidence | schema 2, entry and archive bounds | Cart prepare boundary |
| `futureshops_server_shop_offer_cart_commits` | Committed cart evidence | schema 2, commit and archive bounds | Cart commit boundary |
| `futureshops_server_shop_offer_terminal_receipts` | Terminal offer results | schema 2, 262,144 total and 4,096 per player | Idempotent terminal replay |
| `futureshops_server_shop_offer_usage` | Usage, cooldown, and capacity projections | schema 2, scope and request bounds | Limits remain deterministic after restart |
| `futureshops_admin_offer_save_receipts` | Idempotent admin offer save results | schema 1, receipt and issue bounds | Catalog write acknowledgement |
| `futureshops_auction_house` | Auction listings, bids, and claims | schema 2, encoded snapshot bounds | Auction escrow and claim flow |
| `futureshops_bazaar` | Bazaar products, orders, and fills | schema 2, encoded snapshot bounds | Bazaar order and fill replay |
| `futureshops_market_control` | Module lifecycle control | schema 1, digest checked snapshot | Freeze, drain, and resume decisions |
| `futureshops_market_profiles` | Per player watchlists and preferences | schema 3, explicit per collection bounds and raw list type checks | Client profile synchronization |
| `futureshops_balances` | Retained internal wallet balances | schema 2, 1,000,000 entries | Legacy compatibility and migration source |
| `futureshops_coin_mints` | Retained physical mint registry | schema 2, 100,000 mints and bounded identities | Legacy mint migration and deposit proof |
| `futureshops_legacy_balance_migration` | Wallet migration progress and failures | schema 1, bounded snapshot | One way migration marker |
| `futureshops_catalog_stock_migration` | Catalog stock seed marker | schema 1, bounded migration record | Prevents duplicate stock seeding |
| `futureshops_player_shop_registry` | Player shop identity and location registry | schema 2, owners and shop bounds | Block identity ownership |
| `futureshops_saved_configs` | Named player shop config snapshots | per player 16, bounded players and snapshot NBT | Copy and paste configuration |
| `futureshops_player_shop_settlements` | Pending and lifetime seller settlement | schema 2, shop, owner, and row bounds | Claims, rollback, and seller history |
| `futureshops_admin_categories` | Admin category assignments | schema 2, bounded categories and assignments | Catalog presentation only |
| `futureshops_departments` | Custom shop departments | schema 1, 512 names of 48 characters | Catalog search and presentation |
| `futureshops_franchises` | Franchise membership | schema 1, 10,000 franchises and 20 members each | Shop ownership policy |
| `futureshops_shop_limits` | Per player shop placement limits | bounded player map | Placement policy |
| `futureshops_stock_refresh` | Stock refresh timestamps | schema 1, 100,000 keys | Scheduler continuity |
| `futureshops_dynamic_pricing` | Price activity and state | schema 2, 100,000 keys and 10,000 receipts | Pricing continuity and replay |

The administrator bulk replay store and normalized Server Shop prepared,
commit, terminal, usage, and replay receipt stores use the same raw list type
assertion. They are durable replay state rather than an independent economy
authority, and malformed entries fail closed before live state is replaced.

`EscrowManagedSavedData` is an abstract binding superclass and is not an
additional store. The concrete `PlayerShopEscrowSavedData` identity is included
above. No other `SavedData` subclass exists in the source tree.

## File backed state

The world escrow cohort is `world/futureshops/escrow`. Journal generations,
`journal.active`, checkpoints, replay receipts, usage cursors, and lock files are
validated as one lineage. Checkpoints use bounded payloads, SHA 256 manifests,
temporary sibling files, forced file and directory writes, atomic replacement,
and orphan cleanup. The replay ledger uses sharded request UUID paths, a forced
index, CRC validation, file locks, safe child path checks, bounded receipt bytes,
and atomic moves.

Catalog state lives in `config/futureshops/shops` and Bazaar products live in
`config/futureshops/bazaar/products`. Catalog writes use a temporary sibling,
bounded backup, atomic replacement, reload, and rollback to the last valid file.
Forge generated TOML lives in `config/futureshops`, and recognized legacy loose
files are migrated into `config/futureshops/migration-backups` without following
symlinks. Player and block NBT remain in the vanilla world and chunk save paths.

No file backed store is an independent economy authority. File records are
reconciled with the world `SavedData` lineage, checksums, request identity,
receipts, custody, claims, and conservation state before mutation resumes.

## State and conservation lineage

Every value moving request follows this logical chain.

1. A command or packet creates a stable request UUID and immutable fingerprint.
2. Readiness, permissions, module lifecycle, schema, and input bounds are checked
   on the logical server.
3. A prepare record and journal entry establish intent before a wallet, provider,
   item inventory, stock reservation, or custody side effect.
4. Custody, stock, and ledger applications carry the transaction identity and
   exact item or signed integer minor unit values.
5. Delivery writes exact inventory slot receipts. Ambiguous ownership keeps
   custody or becomes a durable claim, never a silent drop.
6. Commit and terminal receipts are forced and replayable. A duplicate request
   with the same fingerprint returns the original outcome. A changed fingerprint
   for the same UUID is rejected.
7. Checkpoints capture the verified cursor and lineage. Startup replays later
   journal generations and repeats recovery without a second economic effect.

The conservation audit uses signed integer minor units for money and exact item
counts. Fees transfer value to their recorded sink. Only protected mint creation
and explicit administrative sources or sinks change total supply. A missing or
duplicate leg freezes mutation and retains all evidence until recovery resolves
it.

## Schema and corruption policy

Current and older schemas are loaded through each store's migration path. Newer
escrow and market schemas fail closed. Unknown compatible fields remain in the
source tag or snapshot codec and are not silently rewritten by a repair. Lists,
maps, identifiers, text, payloads, NBT, and nested records are bounded before
being copied into live state. Duplicate identities, invalid numeric ranges,
wrong NBT types, bad checksums, truncation, mixed lineages, and unsupported
versions produce a bounded error and preserve the last valid state or maintenance
mode.

Issue 32 successor fixtures use synthetic player data, a modded item with nested
NBT and capability data, unrelated top level and nested NBT sentinels, exact
delivery slot proofs, receipts, login, logout, restart, reconnect, and repeated
recovery. Production worlds and unique player state are never test inputs.

## Phase 005 verification record

The phase branch was created from merged Forge `1.20.1` commit
`c555d17e25bc87b84507b452c7d73c2bedcef6a9`. Focused bounds tests and the complete
Forge test suite passed with Java 17 and one Gradle worker.

```text
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 bash ./gradlew test --no-daemon --max-workers=1
BUILD SUCCESSFUL
```

The issue 51 and issue 52 strict list regressions are included in
`PersistenceSavedDataBoundsTest`, `PlayerInventoryDeliveryTest`, and
`ProtectedCashRedemptionEvidenceTest`. They reject wrong outer and nested list
element types for administrator replay, market profile state, player inventory
delivery, and protected cash redemption while preserving valid round trips.

The merged phase evidence packet records the exact fixture hashes, corruption
campaign, crash cuts, backup cohort, restore rehearsal, dedicated server, client,
and two profile lifecycle results. The merged `1.20.1` revision is
`c43dae7d8348c51a91368cd73dd7e3bc68c01e19`. It passed the complete unit suite,
data generation, all five required GameTests, the packaged build, a restored
cohort server smoke, and two client joins. Its beta jar digest is
`6c1d0f94b308a91e10bf754d5768cb1d48fbbb30eb169c5e7088471926a839ba`. A timed
smoke command is only accepted when the server reaches `Done`, FutureShops
initializes, and no crash report or startup exception is produced. A timeout
after those observations is a bounded process stop, not a product failure.

The integration pull request checks all passed. The quality Gradle, quality
dependency review, quality secret scan, Java and Kotlin CodeQL, and Python
CodeQL checks completed successfully. Documentation, Node, and dependency
submission jobs were intentionally skipped because this repository has no
matching inputs. The signed annotated tag `phase-005-persistence-recovery`
points to the merged revision.

## Operator recovery boundary

Recovery must use one complete matching world, escrow, playerdata, and config
cohort. Run `/marketadmin maintenance status`, then
`/marketadmin maintenance verify`. Resume only through the journaled command
`/marketadmin maintenance resume confirm <reason>` after verification reports
clear lineage and conservation. Never delete, reset, selectively replace, or
mix journals, checkpoints, ledgers, custody, claims, player data, migration
markers, or receipts.
