# Phase 005 Execution Plan

> **Plan ID:** PLAN-PHASE-005
> **Phase ID:** CORE-PHASE-005
> **Owner:** Persistence, escrow, and recovery owners
> **Classification:** MANDATORY
> **Master plan:** [plan.md](../plan.md)
> **Phase sequence:** 005 of 007

## Purpose and Ownership

This phase inventories, audits, repairs, and proves every FutureShops persistence, database, migration, atomicity, concurrency, integrity, recovery, and economic conservation path. Its measurable outcome is one source grounded persistence and database audit in which every named durable surface has an accountable owner and complete read, write, schema, migration, backup, restore, corruption, concurrency, and invariant evidence. Every verified repository owned defect must be filed before repair, merged on the correct support line, and retested at the exact merged revision.

The master plan owns product scope, support line topology, owner decisions, requirement assignment, external prerequisite policy, and the global Definition of Done. This file owns only the dependency ordered execution of `CORE-PHASE-005` for `CORE-REQ-012` and `CORE-REQ-014`. It does not move requirements between phases, authorize destructive recovery, permit publication, or start the backend integration work assigned to `CORE-PHASE-006`.

## Evidence-Based Entry State

| Evidence class | Area | Finding | Source or command | Freshness condition |
|---|---|---|---|---|
| VERIFIED | Phase assignment | `CORE-PHASE-005` owns `CORE-REQ-012` and `CORE-REQ-014`, depends on `CORE-PHASE-004`, and transitions only to `CORE-PHASE-006` | `plan.md`, Sections 12 and 13 | Any authoritative Plan Creator revision |
| VERIFIED | Safety boundary | Player data, worlds, journals, checkpoints, ledgers, custody, claims, and isolated recovery files may not be deleted as repair or verification | `plan.md`, `NG-003`, Sections 15 and 17, and `SRC-013` | Any authoritative Plan Creator revision |
| OBSERVED | Durable architecture | World `SavedData`, escrow journal generations, checkpoints, replay receipts, player data, block entity NBT, JSON catalogs, TOML configuration, and migration records form the current persistence surface | `SRC-002`, `SRC-003`, `DOCUMENTATION.md`, `docs/backup-restore.md`, and current source under `src/main/java/com/enviouse/futureshops/` | Any persistence source, storage path, codec, or documentation change |
| OBSERVED | Escrow lineage | Runtime state, journal lineage, transactions, ledger, custody, claims, mints, stock, item inventory evidence, prepared records, commits, terminal receipts, and replay records are cross checked recovery state | `SRC-002`, `docs/backup-restore.md`, and `server/escrow` source | Any lineage, checkpoint, replay, or store binding change |
| OBSERVED | Migration contracts | Legacy balances, mints, history, settlements, catalog stock, Server Shop offers, Player Shop offers, configuration paths, and replay evidence have compatibility or migration responsibilities | `SRC-002`, `SRC-003`, `SavedDataMigrations`, migration packages, and persistence codecs | Any schema, identifier, migration, or loader change |
| OBSERVED | Existing verification | Focused tests exist for escrow journals, checkpoints, SavedData stores, custody, claims, ledger conservation, stock, migrations, item inventory recovery, market state, and catalog atomicity | `SRC-011` and tests under `src/test/java/com/enviouse/futureshops/` | Any relevant implementation, fixture, harness, or dependency change |
| UNKNOWN | Inventory completeness | Documentation does not itself prove that every current reader, writer, cache, recovery record, and retained legacy store is represented with complete evidence | Reconciliation required by `P005-TASK-001` | Frozen only at an exact phase baseline revision |
| UNKNOWN | Issue 32 exact state | The exact invalid player state or an equivalent deterministic reproduction remains dependent on `EXT-003` | `SRC-006`, `EXT-003` | Exact evidence arrives or deterministic reproduction is proven |
| AVAILABLE | GitHub traceability | Authenticated EnVisione GitHub access is available for duplicate search, issue filing, pull request, check, merge, and closure evidence | `EXT-005` | Authentication, permission, remote, or repository identity changes |
| ENTRY GATE | Upstream integration | Security, privacy, command, and permission repairs from `CORE-PHASE-004` must be integrated on every affected support line before this phase branches or freezes its baseline | `CORE-PHASE-004` completion packet and remote ancestry | Any late upstream change or unmerged Phase 004 repair |

Evidence marked `UNKNOWN` is not a defect finding by itself. It becomes verified only through the inventory and reproduction work in this phase. No observation may be promoted to `VERIFIED` without the exact revision, environment, procedure, and decisive result required by the master.

## Scope Boundaries

### Included Scope

- `CORE-REQ-012`: Audit every persistence and database path, including every `SavedData` object, file backed store, NBT payload, JSON and TOML input, migration, atomic write, backup, reload, concurrency, integrity, recovery, and corruption path. Repair every verified defect non destructively.
- `CORE-REQ-014`: Prove server authority, signed integer minor units, checked arithmetic, stable request UUID idempotency, custody conservation, durable accessible claims, compatible persistence, fail closed readiness, and zero silent item or currency loss across every state mutation.
- `CORE-REQ-009` process dependency: Deduplicate and file every newly verified repository owned defect before repair, then link failing evidence, implementation, merged revision, and verification.
- `DEC-006`: Complete the mandatory persistence and database audit and repair loop, including all named marketplace, economy, escrow, ATM, claim, lifecycle, restart, reconnect, and recovery persistence boundaries.
- `EXT-003`: Preserve or deterministically reproduce issue 32 evidence and prove non destructive recovery without altering unrelated player data.
- `EXT-005`: Maintain authoritative issue, branch, pull request, check, merge, and evidence links for every phase finding.
- Exact support line isolation for Forge `1.20.1` and NeoForge `1.21.1`. A surface absent on one line receives an evidence backed not applicable result. A shared concept does not authorize copying loader specific code or persisted formats between lines.

### Explicit Exclusions

- `CORE-REQ-013` cross component backend integration and the full named subsystem combination matrix belong to `CORE-PHASE-006`. This phase supplies individually clean persistence and invariant contracts for that work.
- `CORE-REQ-015`, `CORE-REQ-017`, `CORE-REQ-018`, `CORE-REQ-019`, and `CORE-REQ-020` final full stack proof, final documentation reconciliation, candidate artifacts, repeated candidate audit, and final issue closure belong to `CORE-PHASE-007`. Phase 005 still performs all local verification and documentation needed for its own changes.
- `FUT-001` through `FUT-005` remain excluded. This phase does not publish artifacts, declare stable status, add distributed market state, or add direct external storage listings without deterministic receipts.
- No test or recovery procedure may delete, isolate, replace, force clear, or selectively restore player data, worlds, journals, checkpoints, ledgers, custody, claims, receipts, migration markers, or recovery evidence.
- No repair may bypass maintenance, readiness, request replay, journal lineage, conservation verification, claim access, schema compatibility, or server authority merely to make a market route available.
- Dependency upgrades, platform upgrades, schema redesigns unrelated to a verified defect, and broad storage framework replacement are outside this phase.

## Phase Contract

### CORE-PHASE-005 — Persistence, Recovery, and Conservation Closure

**Objective:** Freeze the complete persistence surface, prove every store and value lineage under normal and injected failure, repair every verified defect through the issue first workflow, and merge exact revisions whose persistence, recovery, restart, reconnect, backup, restore, idempotency, concurrency, and conservation evidence is clean.
**Owner:** Persistence subsystem
**Dependencies:** CORE-PHASE-004, CORE-REQ-009, CORE-REQ-010, CORE-REQ-011, DEC-006, EXT-003, EXT-005
**Canonical requirements:** CORE-REQ-012, CORE-REQ-014
**Documentation and release impact:** Update the canonical technical, persistence, backup, restore, migration, configuration, and recovery documentation for verified behavior. Record release facing migration and recovery implications without changing locked candidate versions or publishing any artifact.
**Next transition:** CORE-PHASE-006

**Entry criteria**

- `CORE-PHASE-004` has an accepted completion packet, every Phase 004 repair is merged on the correct support line, required checks are green, and remote ancestry proves the phase baseline starts from those merged heads.
- The active Forge phase branch derives from the latest approved `origin/1.20.1`. Any independently required NeoForge correction uses a separate line specific branch from the latest approved `origin/1.21.1`. Branches are never stacked or cross merged.
- The complete persistence source surface can be enumerated from source, build metadata, configuration, tracked documentation, tests, and runtime file layout without modifying a live or unbacked world.
- `EXT-005` confirms the EnVisione identity and `MCEnvision/FutureShops` remote before any finding is filed or any repair branch is pushed.
- `EXT-003` evidence is preserved and sanitized if available. If it is unavailable, all independent inventory and deterministic fixture work proceeds, but issue 32 and any gate that requires its exact state remain explicitly blocked.
- A cold, isolated, disposable test world and a matching complete backup location exist for fault, restart, backup, and restore rehearsals. No production world is used.

**Implementation scope**

- CORE-REQ-012 and CORE-REQ-014 define the complete mandatory implementation boundary detailed below.

**Detailed implementation scope**

- Freeze and reconcile the complete durable surface, including authoritative stores, derived indexes, retained legacy stores, external file sets, player and block NBT, configuration, catalogs, migration markers, caches with writeback responsibility, and backup cohorts.
- Trace each economic intent from request identity through validation, prepare, custody or reservation, forced journal evidence, commit decision, ledger and stock application, delivery, claim or compensation, history, checkpoint, replay, restart, and recovery.
- Verify schema readers and writers, bounds, integrity checks, newer and older schema behavior, migration idempotency, rollback boundaries, atomic replacement, durability ordering, concurrency ownership, lock scope, and shutdown behavior.
- Inject failures and corruption only into isolated fixtures or copies, preserve the failed state, and prove bounded fail closed recovery.
- Prove item and currency conservation for every authoritative mutation family and prove idempotency over retry, replay, reconnect, restart, and repeated recovery.
- File every verified defect before repair, implement only the smallest compatible correction, and merge only after exact required evidence passes.

**Execution order**

- `P005-TASK-001` through `P005-TASK-016` execute the CORE-PHASE-005 task sequence in order.

**Detailed task sequence**

1. `P005-TASK-001` freezes exact support line revisions and reconciles the complete persistence and database inventory.
2. `P005-TASK-002` maps state ownership, authoritative versus derived data, journal lineage, commit boundaries, and backup cohorts.
3. `P005-TASK-003` completes the detailed database audit deliverable for every inventory row and proves whether any external database or hidden storage path exists.
4. `P005-TASK-004` audits schemas, codecs, bounds, unknown fields, older and newer data, and compatibility behavior.
5. `P005-TASK-005` audits every migration and reload path with deterministic legacy, current, malformed, and newer schema fixtures.
6. `P005-TASK-006` audits atomicity, force ordering, file replacement, checkpoint, journal generation, shutdown, and crash cut behavior.
7. `P005-TASK-007` audits concurrency, thread ownership, locks, stale revisions, simultaneous mutations, and bounded work.
8. `P005-TASK-008` proves checked arithmetic and conservation across wallets, ledger accounts, stock, mints, custody, claims, markets, shops, ATM, and explicit sources or sinks.
9. `P005-TASK-009` proves request UUID idempotency and replay identity across failure, retry, disconnect, restart, compaction, and recovery.
10. `P005-TASK-010` executes corruption, partial write, disk, registry, codec, player state, and recovery fault injection on isolated copies.
11. `P005-TASK-011` rehearses cold backup, matching restore, rollback, startup replay, restart, reconnect, and repeated recovery without selective deletion.
12. `P005-TASK-012` applies the duplicate search and issue before repair gate to each verified finding.
13. `P005-TASK-013` implements and verifies each issue bounded repair while preserving compatibility and recovery lineage.
14. `P005-TASK-014` runs focused and complete exact revision verification, including required runtime, restart, reconnect, backup, restore, and final diff inspection.
15. `P005-TASK-015` reconciles technical documentation, the persistence audit, schema compatibility matrix, and operator recovery procedures.
16. `P005-TASK-016` assembles the completion packet, verifies merged remote state, and hands the exact individually clean contracts to `CORE-PHASE-006`.

Tasks 001 through 003 are strictly ordered because they freeze scope and ownership. After Task 003, schema review, migration fixture preparation, concurrency modeling, conservation modeling, and fault harness preparation may proceed in parallel only when they do not edit the same store or rely on an unverified contract. Tasks 012 and 013 repeat once per finding. No repair work may begin before Task 012 completes for that finding. Tasks 014 through 016 are ordered and run after the last material repair.

**Required evidence**

- One exact revision persistence and database audit with no unclassified durable or quasi durable state path.
- One schema compatibility and migration matrix with deterministic fixtures for every supported reader and every changed writer.
- Lineage diagrams and state transition tables that connect request UUID, transaction UUID, journal sequence, checkpoint generation, ledger receipt, custody lot, stock reservation, claim, replay receipt, and administrative recovery record.
- Focused unit, property, codec, migration, atomicity, concurrency, corruption, restart, reconnect, backup, restore, and conservation evidence.
- Exact dedicated server logs for startup, clean shutdown, crash replay, maintenance refusal, verified recovery, restart, and reconnect, with bounded actionable context and no private raw data.
- Duplicate search, issue, failing regression, repair, review, pull request, merged revision, and green check links for every verified defect.
- Before and after field level evidence for issue 32 or an exact deterministic reproduction, proving unrelated player and inventory state remains unchanged.

**Exit criteria**

- No known mandatory phase-owned defect remains.
- Every inventory row has a named owner, exact identity or path, authority classification, schema and compatibility contract, reader, writer, thread and lock model, atomicity and durability boundary, integrity check, migration, backup cohort, restore order, corruption response, recovery procedure, privacy classification, conservation relationship, and exact evidence.
- Every verified defect was deduplicated and filed before repair, repaired on the correct support line, reviewed, merged through GitHub, and verified at the exact merged revision.
- No destructive recovery path, force clear path, selective state deletion, silent schema discard, unsafe partial write, unbounded concurrent mutation, or unexplained value creation or loss remains.
- Invariant, property, failure injection, crash, restart, reconnect, cold backup, matching restore, repeated recovery, and full line specific tests pass at the exact merged revisions.
- `EXT-003` is satisfied by preserved exact evidence or deterministic equivalent proof. If it remains unavailable, Phase 005 is not reported complete and the precise blocked evidence remains recorded, but all source-controlled and deterministic internal work may use the master-defined internal integration transition to Phase 006.
- The complete phase audit reruns clean after the final repair, no mandatory phase owned defect remains, and all evidence invalidated by the final change has been rerun.
- The Phase 005 completion packet is accepted and `origin/1.20.1`, plus `origin/1.21.1` if independently changed, contains the reviewed merge commits before `CORE-PHASE-006` begins.

## Inputs and Upstream Contracts

| Input or contract | Provider | Required state | Validation | Failure behavior |
|---|---|---|---|---|
| Phase 004 merged security and command repairs | `CORE-PHASE-004` | Integrated on each affected support line with green checks and no unresolved review finding | Completion packet, Git ancestry, pull request state, and exact check results | Stop entry. Do not branch from unmerged or stacked work |
| Live product contract | `plan.md` | `CORE-REQ-012`, `CORE-REQ-014`, `DEC-006`, non goals, and support line boundaries unchanged | Read master through EOF and compare its digest or revision to the execution record | Stop with the master plan conflict process. Do not reinterpret scope locally |
| Legacy persistence invariants | `SRC-002`, `SRC-003` | Commit decision, journal, ledger, custody, claims, migration, catalog, and safe writing rules preserved | Trace each legacy invariant to current code, tests, or an explicit retained historical classification | Record the gap. A verified repository defect enters Task 012 |
| Current persistence source | `SRC-011` | Exact baseline source and tests for both support lines | Source inventory, CodeGraph, build metadata, data names, codec references, and test discovery | Freeze nothing until all unclassified references are resolved |
| Issue 32 state | `EXT-003` | Sanitized preserved state or deterministic exact reproduction | Hash and provenance the fixture, identify exact FutureShops owned field or transition, preserve unrelated data | Continue only independent work. Keep issue 32 and phase closure blocked |
| GitHub state | `EXT-005` | EnVisione identity, correct remote, issue and pull request permissions | Auth status, repository identity, duplicate search, issue and check query | Stop remote mutation and retain local evidence until access is restored |
| Isolated runtime fixture | Verification owner | Disposable world, matching cold backup, test configuration, known starting balances and inventories | Fixture manifest, checksums, versions, expected totals, and restore rehearsal | Do not run mutation or fault tests without a recoverable matching fixture |

## Outputs and Downstream Contracts

| Output or contract | Consumer | Guaranteed state | Compatibility or versioning | Evidence |
|---|---|---|---|---|
| Frozen persistence inventory | `CORE-PHASE-006`, `CORE-PHASE-007`, maintainers | Every durable surface and writer is classified with no unknown row | Exact to merged support line revisions, rerun after any persistence change | Audit table, source links, data names, paths, and revision manifest |
| State ownership and lineage map | `CORE-PHASE-006` | Canonical and derived ownership, commit decision, replay, claim, and recovery transitions are explicit | Stable identities and backward compatible lineage are preserved | Call and transition map plus restart and recovery traces |
| Schema compatibility matrix | `CORE-PHASE-006`, operators | Supported old, current, and newer schema behavior is defined and tested | Unknown newer data fails closed, compatible older data migrates or remains preserved | Fixture inventory and codec or migration results |
| Persistence and database audit | `CORE-PHASE-007`, maintainers | Every inventory row contains the mandatory audit fields and a final disposition | Exact merged revisions, no assertion survives a material late change without rerun | Signed or hashed audit packet and documentation links |
| Recovery and backup runbook | Operators and `CORE-PHASE-007` | Cold backup, whole snapshot restore, maintenance verification, and rollback are non destructive | Restore uses one matching generation and a compatible build | Rehearsal logs, checksums, and updated tracked guide |
| Conservation and idempotency report | `CORE-PHASE-006`, `CORE-PHASE-007` | Every named value mutation balances and every replay returns or resumes one outcome | Checked integer minor units and stable UUID identities remain authoritative | Property tests, fault traces, account and asset deltas |
| Merged repairs | `CORE-PHASE-006` | All Phase 005 findings are merged on their correct support lines with green checks | No cross branch merge and no silent schema or platform change | Issues, pull requests, merge commits, and exact check URLs |
| Phase completion packet | `CORE-PHASE-006` entry owner | Exact baseline, merged heads, evidence validity, blockers, and interfaces are current | Invalidated by any later persistence or economic change | Packet manifest and downstream acceptance record |

## Complete Persistence Inventory Baseline

`P005-TASK-001` must reconcile the following baseline against both exact support line trees. Each listed class, data identity, directory, file family, codec, retained source, and write capable cache receives its own audit row. Grouping below is for discovery only and may not replace per surface evidence. A newly discovered surface is added before audit completion. A surface absent from one support line receives source proof and an explicit not applicable disposition.

### World SavedData and canonical stores

| Inventory family | Named surfaces that require individual rows | Primary source ownership |
|---|---|---|
| Escrow runtime and canonical value state | `EscrowRuntimeSavedData`, `EscrowTransactionSavedData`, `LedgerSavedData`, `CustodySavedData`, `ClaimSavedData`, `ProtectedMintSavedData`, `StockSavedData`, `ItemInventoryJournalSavedData`, `ServerShopIntentSavedData`, `PlayerShopEscrowSavedData`, `EscrowAdministrativeAuditSavedData` | `server/escrow/runtime`, `store`, `ledger`, `custody`, `claim`, `mint`, `stock`, `item`, and `admin` |
| Normalized Server Shop replay state | `ServerShopOfferPreparedSavedData`, `ServerShopOfferCommitSavedData`, `ServerShopOfferCartPreparedSavedData`, `ServerShopOfferCartCommitSavedData`, `ServerShopOfferTerminalReceiptSavedData`, `ServerShopOfferUsageSavedData`, `AdminShopOfferSaveSavedData` | `server/escrow/runtime`, `server/transaction`, and `server/shop` |
| Market canonical state | `AuctionHouseSavedData`, `BazaarSavedData`, `MarketControlSavedData`, `MarketProfileSavedData` | `server/market/auction`, `bazaar`, `control`, and `profile` |
| Economy and migration state | `InternalBalanceSavedData`, `SpentMintsSavedData`, `LegacyBalanceMigrationSavedData`, `CatalogStockMigrationSavedData`, `TransactionHistorySavedData` | `server/economy`, `server/economy/migration`, `money`, `server/escrow/stock/migration`, and `server/transaction` |
| Player Shop and shop administration state | `PlayerShopRegistrySavedData`, `PlayerShopSettlementSavedData`, `PlayerShopSavedConfigs`, `AdminShopToggleSavedData`, `FranchiseSavedData`, `DepartmentSavedData`, `AdminCategorySavedData`, `ShopLimitsSavedData`, `StockRefreshSavedData`, `DynamicPricingSavedData` | `server/shop` and `server/pricing` |

The audit records the exact data identity for every store. Known identities include `futureshops_escrow_runtime`, `futureshops_escrow_transactions`, `futureshops_escrow_ledger`, `futureshops_escrow_custody`, `futureshops_escrow_claims`, `futureshops_escrow_protected_mints`, `futureshops_escrow_stock`, `futureshops_escrow_item_inventory_journal`, `futureshops_escrow_server_shop_intents`, `futureshops_escrow_player_shop`, `futureshops_escrow_administrative_audit`, `futureshops_server_shop_offer_prepared`, `futureshops_server_shop_offer_commits`, `futureshops_server_shop_offer_cart_prepared`, `futureshops_server_shop_offer_cart_commits`, `futureshops_server_shop_offer_terminal_receipts`, `futureshops_server_shop_offer_usage`, `futureshops_admin_offer_save_receipts`, `futureshops_auction_house`, `futureshops_bazaar`, `futureshops_market_control`, `futureshops_market_profiles`, `futureshops_legacy_balance_migration`, `futureshops_catalog_stock_migration`, `futureshops_balances`, `futureshops_coin_mints`, `futureshops_tx_history`, `futureshops_player_shop_registry`, `futureshops_player_shop_settlements`, `futureshops_saved_configs`, `futureshops_admin_shop_toggle`, `futureshops_franchises`, `futureshops_departments`, `futureshops_admin_categories`, `futureshops_shop_limits`, `futureshops_stock_refresh`, and `futureshops_dynamic_pricing`.

### File backed, NBT, configuration, and recovery surfaces

| Inventory family | Named surfaces that require individual rows | Required ownership proof |
|---|---|---|
| Escrow write ahead journal | `WriteAheadJournal`, journal record framing and codec, `journal.active`, `journal-<uuid>.wal`, legacy `journal.wal`, and journal generation transitions | Writer thread, force boundary, checksum, sequence, truncation rule, lineage, lock, compaction, and recovery owner |
| Checkpoints and generation retention | `EscrowCheckpointStore`, `EscrowCheckpointManager`, `EscrowCheckpointCodec`, `EscrowSavedDataCheckpointBundle`, `EscrowJournalGenerationManager`, checkpoint manifests, `checkpoint-<uuid>.fscp`, and retained journal pairs | Snapshot boundary, included components, force and move ordering, verification, retention, lineage, restore selection, and failure owner |
| Server Shop replay ledger | `ServerShopOfferReplayLedger`, `offer_replay/index.wal`, sharded immutable receipt files, and `offer_replay/ledger.lock` | Receipt authority, index cursor, file size and path bounds, lock ownership, atomic move, migration cache, compaction, and rebuild owner |
| Exact item inventory evidence | `PersistentItemInventoryJournal`, inventory journal codecs and snapshots, changed slot evidence, and player save durability interaction | Exact item identity, tag and Forge capability semantics, before and after proof, legacy hash handling, player file force, and recovery owner |
| ATM and redemption recovery evidence | `ProtectedCashRedemptionIntentStore`, `ForeignCashDepositIntentStore`, active player recovery identity, settlement evidence, and cash claim linkage | Player binding, request and transaction identity, save boundary, orphan cleanup proof, manual review boundary, and claim owner |
| Player and block NBT | Vanilla `<world>/playerdata/<uuid>.dat` fields read or written by FutureShops, `ShopBlockEntity`, `PlayerShopOfferPersistenceCodec`, and `PlayerShopLegacyOfferMigration` | Exact owned fields, load and save hooks, unrelated field preservation, block removal behavior, schema version, and recovery owner |
| Server Shop JSON | `config/futureshops/shops/`, `ShopDefinitionLoader`, normalized offer JSON parser and writer, `AdminShopConfigWriter`, `AdminShopOfferConfigWriter`, backups, and `shops/recovery/` quarantine files | Root containment, schema, whole candidate validation, temporary sibling, force, atomic move, backup, reload, rollback, and preservation owner |
| Bazaar JSON | `config/futureshops/bazaar/products/`, `BazaarProductCatalogStorage`, registry and coordinator snapshots | Product identity and version, full snapshot validation, path and size bounds, atomic publication, last known good behavior, and retirement owner |
| Forge TOML and path migration | `config/futureshops/futureshops-common.toml`, `futureshops-client.toml`, `futureshops-escrow.toml`, `futureshops-auction-house.toml`, `futureshops-bazaar.toml`, `FutureShopsConfigPaths`, and `migration-backups/` | Spec owner, load and reload thread, validation, last known good state, loose file migration, symlink rejection, conflict archive, backup, and restart owner |
| Derived and in memory projections | Repositories, indexes, caches, registries, runtime store bindings, capability snapshots, usage projections, and schedulers that rebuild from or write to durable state | Canonical source, rebuild procedure, invalidation, publication ordering, no independent value authority, and concurrency owner |
| Backup and restore cohort | Complete world, `data/`, `futureshops/escrow/`, `offer_replay/`, `playerdata/`, and matching `config/` | Snapshot identity, shutdown state, checksums, copy atomicity, restore order, version compatibility, post restore verification, and rollback owner |

The inventory audit must also prove by dependency, source, configuration, and runtime inspection whether a separate database, embedded database, remote service, or external transaction store exists. The observed architecture is single server, world and file backed persistence. If the proof confirms no external database, the database audit records that bounded negative result. If one is discovered, its connection ownership, transactions, isolation, schema, migration, backup, restore, secrets, failure behavior, and reconciliation are mandatory new rows before any phase exit.

## Detailed Database Audit Deliverable

Create one tracked, source grounded audit document at the repository's established focused documentation location. If no existing persistence audit exists, use `docs/persistence-database-audit.md` and add it to `docs/README.md`. The audit is a maintainable architecture and verification artifact, not a status diary. It must contain one row per inventory surface and these mandatory fields:

1. Stable inventory ID and exact support line applicability.
2. Component owner, owning package or file, and operator owner.
3. Exact data identity, filesystem root, relative path or SavedData name, and containment rule.
4. Authority class: canonical, recovery evidence, migration marker, retained legacy source, derived index, cache, configuration, or presentation only.
5. Record model, key identity, parent and child relationships, stable request or transaction identifiers, and retention policy.
6. Current schema version, supported older versions, newer schema behavior, unknown field policy, and codec size bounds.
7. Every reader and load trigger, including startup, login, block load, reload, request, recovery, and background work.
8. Every writer and save trigger, including prepare, commit, delivery, claim, migration, admin mutation, shutdown, compaction, and cleanup.
9. Logical server or client boundary, executing thread, synchronization or lock, reentrancy rule, and concurrent access policy.
10. Atomicity unit, transaction boundary, force or flush ordering, temporary file and replacement rule, checkpoint coupling, and crash guarantee.
11. Integrity protection, including checksum, lineage, revision, fingerprint, semantic NBT equality, bounds, registry validation, and cross store reconciliation.
12. Migration chain, deterministic migration identity, repeat behavior, completion marker, backup point, rollback boundary, and retained source behavior.
13. Backup cohort, required shutdown or snapshot state, included related stores, checksum or manifest, and restore ordering.
14. Malformed, missing, truncated, partial, stale, conflicting, corrupt, and unsupported newer data response.
15. Maintenance, quarantine, manual review, retry, compensation, claim, and operator recovery behavior.
16. Privacy class, sensitive fields, logging restrictions, evidence sanitization, and access boundary.
17. Related money, item, stock, mint, fee, custody, claim, and explicit source or sink legs, with the conservation equation.
18. Existing tests, missing tests, new deterministic fixtures, runtime procedure, exact result, and evidence revision.
19. Verified finding disposition: clean, duplicate issue, new issue, confidential finding, excluded future work, or disproven concern.

Every row must link to exact current source and tests. Broad statements such as handled by SavedData, atomic, thread safe, or backed up are insufficient without the concrete boundary and evidence. A row may be marked not applicable only when the audit names the absent concept and proves why it cannot participate on that support line.

## Work Packages

| Task ID | Requirement IDs | Work | Inputs and dependencies | Outputs | Affected components or interfaces | Verification |
|---|---|---|---|---|---|---|
| `P005-TASK-001` | `CORE-REQ-012` | Freeze exact revisions and enumerate every persistent, database, retained, derived, and write capable surface on both support lines | Phase 004 completion, `SRC-002`, `SRC-003`, `SRC-009`, `SRC-011` | Complete per surface inventory with no unknown owner or path | All `SavedData`, file stores, NBT, JSON, TOML, repositories, caches, and backup cohorts named above | Source and dependency scans, data name extraction, reader and writer call paths, runtime file tree, test mapping, and two reviewer reconciliation passes |
| `P005-TASK-002` | `CORE-REQ-012`, `CORE-REQ-014` | Trace canonical state ownership, derived state, commit decision, journal and checkpoint lineage, replay, claim, and compensation relationships | Task 001 inventory and legacy invariants | State ownership map, transaction lineage map, backup cohort graph, and recovery ownership table | Escrow runtime, ledger, custody, claims, stock, mints, markets, shops, ATM, player and block data | Call path inspection plus controlled transaction traces before and after restart |
| `P005-TASK-003` | `CORE-REQ-012` | Complete every field in the detailed database audit and prove whether any external or embedded database exists | Tasks 001 and 002 | `docs/persistence-database-audit.md` or existing equivalent, with exact revision manifest | Build dependencies, configs, storage adapters, every inventory row | Independent source to audit reconciliation and no unclassified read or write call |
| `P005-TASK-004` | `CORE-REQ-012`, `CORE-REQ-014` | Audit schema versions, codecs, bounds, unknown fields, legacy bytes, exact NBT semantics, and newer schema refusal | Tasks 001 through 003 | Schema compatibility matrix and deterministic fixture catalog | SavedData codecs, journal and checkpoint codecs, replay receipts, offers, player shop NBT, market records, configuration | Round trip, legacy, malformed, oversized, missing registry, semantic NBT, unknown field, and newer schema tests |
| `P005-TASK-005` | `CORE-REQ-012`, `CORE-REQ-014` | Audit migrations and reloads for determinism, idempotency, preservation, last known good state, and rollback | Task 004 | Migration table, before and after fixture manifests, failure and retry results | Balance and mint import, settlements, stock seeding, offer schemas, player shop NBT, config paths, catalog and Bazaar reload | First run, interrupted run, repeated run, mixed old and current data, conflict, invalid input, restart, and rollback tests |
| `P005-TASK-006` | `CORE-REQ-012`, `CORE-REQ-014` | Prove atomicity and durability ordering from intent through force, side effect, commit, claim, checkpoint, compaction, and shutdown | Tasks 002 through 005 | Durability sequence tables and crash cut result matrix | WAL, checkpoint, replay ledger, SavedData, player saves, catalog writers, recovery files | Fault injection immediately before and after every durability boundary, followed by restart and conservation verification |
| `P005-TASK-007` | `CORE-REQ-012`, `CORE-REQ-014` | Audit thread ownership, synchronization, locks, stale revisions, simultaneous writers, scheduler budgets, and shutdown races | Tasks 002 and 003 | Concurrency matrix and proven serialization or conflict behavior | Logical server services, synchronized repositories, file locks, listing and order locks, stock, recovery schedulers, reload listeners | Concurrent buyers, bids, fills, saves, reloads, claim collection, retry, shutdown, and second process lock tests where safe |
| `P005-TASK-008` | `CORE-REQ-014` | Define and prove conservation equations and checked arithmetic for every value mutation family | Task 002 and exact starting fixtures | Per workflow conservation report with explicit sources and sinks | Wallets, ledger accounts, physical money, mints, stock, custody, claims, server shops, player shops, Auction House, Bazaar, ATM, fees | Property tests, arithmetic bounds, rounding, partial failure, recovery, and exact before and after totals |
| `P005-TASK-009` | `CORE-REQ-012`, `CORE-REQ-014` | Prove stable request identity, idempotent retries, replay, compaction survival, and identity conflict rejection | Tasks 002, 004, and 006 | Request and replay matrix linked to durable evidence | Packets, commands, transactions, prepared records, commits, terminal receipts, usage, replay ledger, recovery work | Duplicate before and after each state transition, changed payload under same UUID, reconnect, restart, compaction, and repeated recovery |
| `P005-TASK-010` | `CORE-REQ-012`, `CORE-REQ-014`, `EXT-003` | Inject bounded corruption and failures into isolated copies, including exact issue 32 state when available | Tasks 004 through 009 and preserved fixtures | Fault packet, failed state preservation, safe response, and recovery evidence | All inventory families, player owned fields, item proof, disk and registry boundaries | Corruption, truncation, partial write, disk full or access denial simulation, missing item, newer schema, bad checksum, lineage mismatch, and ambiguous player state tests |
| `P005-TASK-011` | `CORE-REQ-012`, `CORE-REQ-014` | Rehearse complete cold backup, matching restore, code rollback boundary, startup replay, restart, reconnect, and repeated recovery | Tasks 005, 006, 009, and 010 | Backup manifest, checksums, restore log, post restore audit, and reversible rollback record | Whole world, data, escrow, replay, player data, configuration, exact build | Stop, backup, mutate fixture, restore one complete snapshot, start, verify balances, claims, listings, orders, lineage, and repeat |
| `P005-TASK-012` | `CORE-REQ-009`, `CORE-REQ-012`, `CORE-REQ-014`, `EXT-005` | For each verified finding, search duplicates and create or enrich the canonical issue before repair | A reproducible finding and sanitized evidence | Issue or private advisory with acceptance criteria and links | GitHub issue workflow and confidential security route | Timestamped search queries and proof that issue creation precedes repair commit |
| `P005-TASK-013` | `CORE-REQ-012`, `CORE-REQ-014` | Implement the smallest compatible issue bounded repair and preserve lineage, schemas, unknown data, and unrelated player state | Task 012 for that finding | Failing regression, focused change, documentation delta, and reviewed merged revision | Only components proven responsible by the finding | Before failure, after pass, complete affected matrix, review, green checks, merge, and post merge rerun |
| `P005-TASK-014` | `CORE-REQ-012`, `CORE-REQ-014` | Rerun all affected and complete verification at exact final phase revisions | Last merged Task 013 repair | Clean exact revision verification packet for each changed support line | Tests, data, GameTests, build, server, client if affected, multiplayer persistence, JAR, and diff | Ordered commands and real workflows defined below, with failures retained and no false completion |
| `P005-TASK-015` | `CORE-REQ-012`, `CORE-REQ-014` | Reconcile architecture, migration, backup, restore, recovery, configuration, and troubleshooting documentation with verified behavior | Tasks 003 through 014 | Updated audit and operator documentation, index links, and issue references | `DOCUMENTATION.md`, `docs/backup-restore.md`, `docs/README.md`, affected config and focused guides, `README.md` when user behavior changes | Source to documentation review, link check, command and path verification, and destructive advice scan |
| `P005-TASK-016` | `CORE-REQ-012`, `CORE-REQ-014`, `EXT-005` | Audit final traceability, verify merges and evidence freshness, and hand off exact contracts under either the full exit or internal integration gate | Tasks 001 through 015 | Phase completion packet or blocker-aware internal integration packet and accepted `CORE-PHASE-006` entry state | Git, GitHub, audit documents, tests, runtime evidence, blockers | Remote ancestry, green checks, no unclassified repository-owned finding, clean independent audit rerun, and downstream acceptance |

### Per Task Controls

- Task 001 records both code and runtime discovery. A class name alone is not a persistence row if another writer, migration, file, or cache participates in its durability.
- Task 002 distinguishes canonical state from rebuildable projections. Derived indexes must be rebuildable from canonical records and may never become an unjournaled second authority.
- Task 003 is incomplete if a surface lacks any mandatory audit field, even when no defect was found.
- Tasks 004 and 005 preserve original bytes or semantically compatible unknown data as required. Tests never normalize old data into a new meaning merely to make decoding succeed.
- Task 006 injects failures on copies. After each cut, restart chooses one valid outcome, before commit restores original ownership, after commit completes delivery or retains a claim.
- Task 007 requires one logical server owner for mutation. File locks prevent two live server processes from sharing a store. Lock order must be explicit wherever more than one store or listing lock is held.
- Task 008 treats only explicit system mint and sink records as supply changes. Fees move value between accounts and do not disappear. Inventory and stock quantities remain nonnegative and exact.
- Task 009 reuses the original UUID. A repeated request with different identity inputs is a conflict, never a second transaction or silent reuse.
- Task 010 stops mutation on any conservation mismatch, inaccessible claim, duplicate outcome, unknown custody, invalid lineage, or unsupported schema. The failed fixture and logs are preserved.
- Task 011 moves the current test world aside and restores one complete snapshot. It never deletes the current world or combines components from different generations.
- Task 012 precedes every repair edit. Exploitable findings use confidential handling and sanitized public state.
- Task 013 records which downstream evidence the repair invalidates before implementation and reruns it after merge.
- Task 014 fails the full phase exit on any required failed, skipped, flaky, stale, or unavailable evidence. An unavailable `EXT-003` remains a named blocker rather than a substituted mock, but it does not block the internal integration gate after every independent source-controlled result passes.
- Task 015 uses normal documentation language and never advertises planned or unmerged behavior as implemented.
- Task 016 does not close an issue whose master level acceptance or external evidence belongs to Phase 007. It records merged internal evidence and the exact remaining blocker.

## Architecture and Implementation Boundaries

### Authority and Data Flow

The logical server is the sole authority for balances, stock, item ownership, listings, orders, bids, claims, permissions, module state, and persistence mutation. Client state, packets, screens, and capability snapshots may request or display state but cannot authorize value, schema, ownership, completion, or recovery decisions.

Every economic mutation follows one traceable lineage:

1. A stable request UUID binds actor, operation, target, and immutable request fingerprint.
2. Authoritative validation resolves permissions, readiness, registry values, limits, revisions, stock, ownership, and exact item identity.
3. Prepare records capture money, item, stock, configuration, and beneficiary evidence before externally visible mutation.
4. Custody or reservation becomes durable before the associated source side effect.
5. `COMMIT_DECIDED` is the ownership boundary. Before it, recovery returns assets to original owners. After it, recovery completes delivery to new owners or creates durable claims.
6. Ledger, stock, custody, market, shop, and claim updates use stable transaction and step identities and reject conflicting replay.
7. Delivery is not the source of truth. Failed delivery retains claimable value.
8. Checkpoints materialize verified state only through a journal sequence that is known durable. Compaction occurs only after checkpoint verification.
9. Startup binds one journal lineage to the matching SavedData, checkpoint, replay ledger, player data, and configuration cohort. Mismatch fails closed.

### Schema and Compatibility

- Every durable record declares or derives a current schema and has explicit behavior for missing legacy version fields, supported older versions, and unsupported newer versions.
- A writer change requires compatible read behavior, deterministic fixtures, migration or preservation, a backup point, rollback boundary, and restart proof.
- Stable data names, serialized field names, request identities, resource locations, stock keys, listing identifiers, configuration keys, and journal framing do not change without a verified migration need.
- Exact item identity preserves registry ID, count, complete item tag, Forge capability data, list order, primitive values, and semantic compound equality. Compound key insertion order alone is not identity. Legacy hashes are validated against the evidence contract under which they were written.
- Missing registry entries preserve raw recoverable evidence and enter quarantine or maintenance as defined. They are not dropped, replaced with air, or rewritten as a different item.
- Compatible unknown fields survive operations that do not own them. Catalog and bulk replacement may alter only the fields explicitly owned by the operation.

### Atomicity and Durability

- Journal records are framed, bounded, sequenced, checksummed, and forced before the side effect they authorize.
- File replacement validates the complete candidate, writes a temporary sibling inside the approved root, flushes or forces as supported, atomically moves where available, verifies the result, and retains the prior last valid snapshot on failure.
- Platform specific inability to force a directory may be treated only according to the documented bounded fallback after file content and replacement durability succeed. Other I/O failures fail closed.
- SavedData dirty marking, server save boundaries, player data durability, file stores, and checkpoint sequences must not create an acknowledged state whose required evidence was not persisted.
- Shutdown blocks new mutations, drains bounded active work, forces journal evidence, checkpoints, and closes locks. A crash at any injected boundary must recover to exactly one ownership outcome.

### Concurrency and Performance

- All authoritative mutation executes on the logical server thread or through an explicitly serialized service boundary. Background work may prepare immutable data but may not mutate Minecraft or economic state off thread.
- Repository synchronization and listing, order, stock, file, and process locks have one documented owner and lock order. Timeout, interruption, or conflict releases reservations without value loss.
- Stale revisions are rejected and refreshed. Concurrent last stock, bid and expiry, order fill and cancellation, reload and commit, claim delivery and reconnect, checkpoint and shutdown, and retry races have deterministic winners.
- Recovery, checkpointing, migrations, expiration, matching, replay discovery, and claim delivery use bounded work per tick. No audit repair may move broad scans, file I/O, or blocking waits into a hot path.

### Recovery and Destructive Action Boundary

Recovery preserves all evidence. It may verify, replay, resume, compensate, quarantine, create or deliver claims, or restore one complete matching backup. It may not delete player data, clear maintenance without verification, discard an intent after ambiguous mutation, remove one journal or checkpoint, edit custody in place, reset a migration marker, or selectively copy files between generations. Administrative corrections must be permission checked, confirmed where required, journaled, auditable, idempotent, and conservation checked.

## Failure, Recovery, and Edge Cases

| Scenario | Detection | Required behavior | Recovery or rollback | Regression proof |
|---|---|---|---|---|
| Truncated final journal record | Frame length or end of file scan | Discard only the incomplete tail and preserve all earlier valid records | Replay from latest verified checkpoint and remaining valid sequence | Crash cut at every byte boundary of final frame |
| Interior journal corruption or checksum mismatch | Checksum, sequence, or framing failure | Enter maintenance and refuse new value mutations | Preserve files, inspect, restore one matching backup or use verified recovery | Corrupt interior record and prove no mutation resumes |
| Journal and SavedData lineage mismatch | Lineage UUID comparison | Fail closed with actionable bounded context | Restore the complete matching cohort, never delete the mismatched file | Mixed snapshot fixture is rejected and consistent restore passes |
| Unsupported newer schema | Version greater than supported | Keep data unchanged and enter read only recovery or maintenance | Use a compatible build or restore preupgrade complete snapshot | Newer fixture remains byte unchanged and mutation is unavailable |
| Malformed or oversized NBT, JSON, TOML, or binary record | Codec, size, type, path, registry, or validation bounds | Reject before publication or mutation and identify exact safe field context | Retain last valid snapshot and preserve offending source for repair | Boundary and malformed fixture matrix |
| Missing registry item | Registry resolution failure | Preserve exact raw identity and value evidence, quarantine or mark unavailable | Restore dependency or use documented bounded recovery without rewriting unrelated data | Missing item in custody, claim, catalog, player shop, Auction House, and Bazaar fixtures |
| Partial catalog or configuration write | Injected write, force, move, or reload failure | Keep target and in memory snapshot at the prior valid generation | Restore prior file from retained complete backup only if replacement occurred | Failure before and after each write stage, restart verifies prior state |
| Duplicate request | Existing request fingerprint and terminal evidence | Return original outcome without repeating any side effect | None beyond replay | Duplicate at every state transition, restart, and compaction |
| Same UUID with different request | Fingerprint, actor, target, amount, option, or quantity conflict | Reject as identity conflict before mutation | Preserve original durable outcome | Conflict matrix for packets, commands, and recovery |
| Concurrent last stock or capacity | Revision, reservation, or lock conflict | Exactly one valid winner, all losers release or retain recoverable value | Safe refund or claim according to commit boundary | Multi thread service test and controlled multiplayer trace |
| Provider fails after hold | Adapter exception or negative result after durable prepare | Fail closed, preserve custody, then compensate or claim exactly once | Stable recovery work under original identity | Fault before and after provider call, retry, restart, reconnect |
| Full inventory or maximum wallet | Authoritative capacity result | Preserve committed value as a durable accessible claim | Manual or automatic bounded claim collection | Partial collection, restart, module disabled, and repeated claim tests |
| Player save or slot evidence ambiguity | Save failure or changed slot matches neither before nor after | Enter recovery or manual review without altering unrelated slots | Verify exact recorded slots or restore matching backup | `EXT-003` fixture or deterministic equivalent plus unrelated slot mutations |
| Migration interrupted before completion marker | Marker, checksum, or record mismatch | Resume deterministic work without double import | Repeat original migration identity and preserve legacy source | Crash at each migration step followed by two restarts |
| Reload concurrent with transaction | Snapshot revision or lease mismatch | Transaction retains immutable quote, new work uses validated new snapshot | Reject stale request or finish under captured contract | Reload before prepare, before commit, and after commit decision |
| Disk full, access denied, or force failure | I/O exception or injected durability failure | Stop unsafe mutation and retain recovery evidence | Free capacity or correct access outside the process, then verified recovery | Fault adapter with no acknowledged partial commit |
| Second server process opens replay state | `ledger.lock` or equivalent process ownership failure | Refuse second writer | Stop the conflicting process and reopen the original intact state | Isolated process lock test with no file mutation by loser |
| Checkpoint fails or snapshot is incomplete | Manifest, component, sequence, checksum, or store binding mismatch | Keep prior verified checkpoint and active journal | Replay from prior pair, preserve failed candidate | Component omission and failure at each checkpoint stage |
| Backup mixes generations | Lineage, cursor, checksum, or state reconciliation mismatch | Refuse recovery and new mutations | Restore one complete matching snapshot | Mixed `data`, escrow, replay, or playerdata fixture fails, complete restore passes |
| Arithmetic overflow, negative quantity, or fee greater than gross | Checked arithmetic or domain validation | Reject before mutation | Release prepared resources under original identity | Minimum, maximum, multiplication, fee, rounding, and negative boundary properties |
| Conservation mismatch | Global verifier or per workflow equation | Freeze mutation and preserve all evidence | Investigate and recover through verified journaled procedure | Inject one missing or duplicated leg and prove maintenance refusal |
| Claim store unavailable while module disabled | Claim access and module lifecycle matrix | Claims remain reachable independent of module mutation availability | Recover store or module without expiring or deleting claims | Freeze, drain, disable, recover, restart, and claim collection tests |

## Conservation and Idempotency Proof Model

`P005-TASK-008` defines one equation for each workflow and one cross domain equation for the complete fixture. At minimum, the reports must prove:

- Money: player available wallets, reserved funds, transaction escrow, money claims, protected physical outstanding, server shop accounts, Bazaar and Auction fee accounts, treasury, and administrative accounts equal starting supply plus explicit journaled mints minus explicit journaled sinks.
- Protected bills: for every mint batch, available plus reserved plus spent plus refunded plus quarantined equals the authorized count. No state is negative and one unit appears in exactly one bucket.
- Items: player and storage preimages, escrow custody, market vaults, stock, delivered inventory, return or output claims, quarantined raw evidence, and explicit configured sinks account for every exact item unit.
- Server Shop stock: starting stock plus explicit restock or inbound committed sale minus committed acquire output equals current stock plus active reservations according to the store contract. Concurrent reservations cannot oversell.
- Auction House: listed custody, returned claim, winner delivery, seller proceeds, refunds, fees, and listing terminal state account for every item and money leg exactly once.
- Bazaar: open order reserve, filled quantities, commodity custody, proceeds, buyer goods, price improvement, cancellation refunds, fees, and claims reconcile by order and fill identity.
- Player Shop: owner storage or stock, buyer payment, barter input, seller proceeds, settlement state, custody, and claims reconcile across block removal, unload, restart, and owner offline state.
- ATM and player pay: inventory cash, mint state, wallet ledger, recovery evidence, refund destination, and cash or money claims reconcile under the original request and transaction IDs.
- Explicit free offers contain no fabricated money leg. Barter and compound trades account for every required input and output component. History is evidence, not a second value authority.

Every equation is evaluated before mutation, at each durable transition where observable, after success or rejection, after injected failure, after recovery, after restart, and after replay. The request identity matrix proves that retrying the same fingerprint returns the prior result, while a conflicting fingerprint under the same UUID fails without changing any equation.

## Failure Injection Plan

Failure injection must cover both sides of every relevant boundary:

1. Intent or prepare persistence.
2. Journal append and journal force.
3. Wallet or provider hold.
4. Exact item or physical cash reservation.
5. Player inventory mutation and player save.
6. Protected mint reservation and state transition.
7. Stock reservation and stock commit.
8. `COMMIT_DECIDED` persistence.
9. Ledger, custody, market, shop, or settlement application.
10. Claim creation and claim persistence.
11. Delivery, partial delivery, and delivery receipt persistence.
12. Terminal receipt, usage, history, and replay index publication.
13. Checkpoint snapshot, manifest write, force, replacement, verification, journal generation switch, and compaction.
14. Catalog or configuration candidate write, force, move, reload, and last valid publication.
15. Migration record, per entry application, checksum, completion marker, and retained source verification.
16. Recovery enqueue, recovery handler, compensation, manual review, verified resume, and shutdown drain.

For each cut, the expected owner before and after the commit boundary is recorded in advance. The test preserves the failed bytes and state, restarts the server or codec harness, executes bounded recovery twice, reconnects the affected player when applicable, and proves the same terminal result, conservation totals, claim accessibility, and no unrelated data change.

## Verification Matrix

| Requirement or task | Static or unit | Integration | Real workflow or runtime | Negative and recovery | Evidence artifact |
|---|---|---|---|---|---|
| `P005-TASK-001` through `P005-TASK-003` | Source, dependency, data name, reader, writer, and test mapping | Runtime file tree and store binding reconciliation | Clean server startup and controlled state creation | Hidden path and second authority search | Frozen inventory and detailed audit |
| `P005-TASK-004` | Codec round trips, bounds, semantic NBT, old and new schemas | SavedData, journal, checkpoint, player, block, JSON, and TOML loading | Start exact legacy and current fixtures | Malformed, oversized, missing registry, unknown field, and newer schema | Schema compatibility matrix and fixture hashes |
| `P005-TASK-005` | Migration and reload unit tests | Interrupted and repeated migration, last valid snapshot | Upgrade, restart, reload, and code rollback rehearsal | Conflict, unsafe path, invalid candidate, partial migration | Before and after manifests and logs |
| `P005-TASK-006` | Journal crash cut, checkpoint codec, atomic writer tests | Store and side effect durability sequence | Crash or forced stop at controlled boundaries, then restart | Force, move, checkpoint, compaction, and shutdown faults | Crash cut matrix and recovered state checksums |
| `P005-TASK-007` | Lock, revision, repository, and scheduler tests | Concurrent transaction and reload tests | At least two clients for state crossing the network when available | Last stock, bid or fill race, claim race, second process lock, shutdown race | Concurrency trace and final state |
| `P005-TASK-008` | Arithmetic and conservation properties | Cross domain global verifier | Shop, player shop, Auction House, Bazaar, ATM, claim, and admin mutation workflows | Overflow, partial failure, fee boundaries, missing or duplicate leg | Per workflow and global conservation reports |
| `P005-TASK-009` | Fingerprint, request UUID, replay codec, and compaction tests | Prepared, committed, terminal, usage, and replay lifecycle | Retry before and after reconnect and restart | Same UUID with changed inputs, duplicate response, dropped response | Idempotency and replay matrix |
| `P005-TASK-010` | Corrupt fixture and exception tests | Maintenance, quarantine, manual review, and recovery paths | Dedicated server startup with each safe isolated fault family | Checksum, lineage, disk, registry, player evidence, and unsupported schema | Fault packet with decisive safe outcome |
| `P005-TASK-011` | Backup manifest and checksum validation | Whole cohort restore and replay | Cold backup, mutate, restore, restart, reconnect, verify | Mixed generation restore and incompatible rollback refusal | Backup and restore rehearsal packet |
| `EXT-003` | Exact field and codec regression | Player lifecycle, transaction, claim, and recovery | Dedicated server restart and player reconnect | Repeated transaction, unrelated inventory change, ambiguous evidence | Sanitized before and after field level diff |
| `CORE-REQ-012` | Complete audit row checks and focused regressions | Full persistence lineage suite | Dedicated server startup, recovery, restart, backup, restore | Corruption, partial write, old and newer schema, concurrent access | Exact merged revision persistence packet |
| `CORE-REQ-014` | Arithmetic, custody, claim, idempotency, replay, and property tests | Every authoritative mutation family | Real workflow conservation across restart and reconnect | Injected partial failure and repeated recovery | Exact merged revision conservation packet |

### Fixtures and Test Data

- A clean current schema world with known balances, protected mint batches, stock, player shops, one auction, Bazaar buy and sell orders, claims, history, and configuration.
- Byte preserved fixtures for every supported older SavedData, journal, checkpoint, offer, Player Shop, catalog, Bazaar product, TOML path, and replay format.
- A newer schema fixture for each fail closed family, retained byte for byte through rejection.
- Corrupted fixtures for truncated tail, interior checksum failure, invalid bounds, duplicate key, missing registry entry, invalid exact NBT, mixed lineage, partial catalog replacement, and incomplete migration.
- A deterministic issue 32 fixture or sanitized preserved state with matching FutureShops and player context, plus a manifest of unrelated player fields that must remain identical.
- Transaction fixtures with exact starting account, mint, inventory, stock, custody, claim, listing, order, and fee totals.
- Two independent player identities for concurrency, reconnect, claim, stock, auction, and Bazaar paths. Multiplayer evidence needed by `EXT-004` remains a later phase and final gate, but Phase 005 does not substitute a single client for any persistence race that itself requires two actors.

### Command and Rerun Order

For every changed support line, run in this order and retain exact revision, Java version, command, duration, and decisive result:

1. Confirm there is no formatter or static analysis task, or run it if the build gained one.
2. Run focused tests for the changed stores and every invalidated invariant. Use Gradle test selection for the relevant packages and named regression classes.
3. Run `bash ./gradlew test` on Forge with Java 17. Run the NeoForge line's complete test task with Java 21 only if that line changed.
4. Run `bash ./gradlew runData` when providers, generated resources, examples, or data contracts changed.
5. Run `bash ./gradlew runGameTestServer` when applicable GameTests cover the changed persistence or recovery behavior.
6. Run `bash ./gradlew build`.
7. Run a dedicated server smoke and the phase persistence scenarios: clean startup, prepared mutation, clean shutdown, restart, recovery, backup, restore, and second restart.
8. Run `bash ./gradlew runClient` when player NBT, screens, synchronization, claims, reconnect presentation, configuration, or client visible recovery changed.
9. Run reconnect and multiplayer exercises whenever state crosses the network or concurrency requires independent actors.
10. Inspect the final JAR for data, configuration, migration, mixin, dependency, and metadata changes. Inspect the complete Git diff and status for secrets, logs, worlds, caches, absolute paths, debug output, unintended generated files, and unrelated edits.

A failed, stale, skipped, flaky, or unavailable required command remains visible. It is not converted into a pass by a lower fidelity source scan or mocked test. A material late change reruns focused proof first, then every downstream integration, runtime, backup, restore, documentation, and completion check it can affect.

## Documentation, Operations, and Release

- Maintain the detailed persistence and database audit described above and link it from `docs/README.md` and the relevant section of `DOCUMENTATION.md`.
- Update `docs/backup-restore.md` with the exact verified backup cohort, shutdown requirements, journal and checkpoint lineage, replay ledger coupling, player data coupling, configuration inclusion, restore ordering, compatibility boundary, expected startup evidence, stop conditions, and non destructive recovery steps.
- Update focused configuration, market, ATM, shop, migration, or troubleshooting guides when their persistence, reload, claim, or recovery behavior changes.
- Update `README.md` only when user or operator visible setup, compatibility, commands, configuration, limitations, or recovery behavior changes.
- Record exact schemas, supported read versions, migrations, rollback boundaries, retained legacy state, and unknown newer schema behavior. Do not claim compatibility that lacks a fixture.
- Document recovery commands as verification and journaled action paths. Do not describe deleting, moving aside one state file, force clearing maintenance, resetting a marker, or editing custody as recovery.
- Every operator procedure names the stopped or isolated state, backup scope, exact command or UI action, expected result, refusal condition, evidence to preserve, and complete snapshot rollback.
- GitHub issue and pull request text follows repository lowercase rules. Each finding records exact affected versions, sanitized reproduction, acceptance criteria, migration and recovery impact, and links to merged evidence.
- Release notes may record verified persistence fixes and migration requirements for the locked candidates, but this phase does not build final release packets, publish releases, create public release tags, upload, or announce.

## Issue and Repair Obligations

For every suspected finding:

1. Reproduce or disprove it at an exact revision. Preserve sanitized inputs, expected and actual behavior, and the decisive state transition.
2. Search open and closed issues by behavior, exception, component, data name, path, schema, and identifier.
3. If duplicate, enrich the canonical issue. If new, file one issue before any repair edit. If exploit details would increase risk, use the private vulnerability path and keep public text non sensitive.
4. Bind the issue to `CORE-REQ-012` or `CORE-REQ-014`, the affected support line, the persistence inventory rows, severity, migration and rollback implications, and acceptance evidence.
5. Add a regression that fails on the reproduced defect where feasible before implementation.
6. Implement the smallest compatible fix on the correct sequential phase branch. Do not transfer loader specific code or state across support lines.
7. Rerun every inventory row, schema, lineage, conservation equation, runtime path, and document invalidated by the change.
8. Complete private independent review when available, required checks, pull request merge, remote ancestry verification, and post merge exact revision verification.
9. Do not close a master scoped issue until the master required external evidence and Phase 007 closure criteria pass. The Phase 005 packet may record internally complete and merged with the remaining blocker.

## Risks and Evidence Invalidation

| Risk | Prevention | Detection | Recovery | Evidence invalidated | Reverification |
|---|---|---|---|---|---|
| Inventory omits a hidden writer or recovery file | Reader and writer call path scans plus runtime file tree and independent reconciliation | New file, data name, save hook, cache flush, or storage dependency appears | Add a row and reopen all affected audit tasks | Audit completeness and downstream lineage proof | Tasks 001 through 003 and affected tasks 004 through 011 |
| Mixed snapshot damages economic lineage | Whole cohort manifest and cold snapshot rule | Lineage, cursor, checksum, or conservation mismatch | Preserve both states and restore one complete matching snapshot | Restore, restart, recovery, and conservation evidence | Tasks 006, 008, 009, 010, 011, and 014 |
| Migration rewrites unknown or unrelated state | Byte fixtures, owned field map, and last valid snapshot | Unexpected before and after diff | Stop, preserve failed copy, restore complete pre migration snapshot | Schema, migration, issue 32, and compatibility proof | Tasks 004, 005, 010, 011, and 014 |
| Crash cut yields both or neither owner | Durable prepare and commit boundary with per asset evidence | Conservation or custody mismatch | Freeze mutation and recover under original request identity | Atomicity, recovery, idempotency, and every affected workflow | Tasks 006, 008, 009, 010, 011, and 014 |
| Concurrent writers bypass logical server authority | Explicit thread and lock ownership | Race test, stale revision bypass, process lock failure | Reject, release reservation, preserve evidence, and repair issue first | Concurrency and conservation proof | Tasks 007 through 010 and affected runtime checks |
| Late security or command repair changes persistence | Phase 004 integrated entry and change monitoring | Upstream merge, backport, or command mutation after baseline | Rebase only from approved support head after sequential gate and refreeze | Inventory, command to store mapping, all downstream evidence | Tasks 001 through 016 as affected |
| Issue 32 evidence remains unavailable | Early precise request and deterministic fixture work | `EXT-003` stays unknown | Continue independent work and keep phase closure blocked | Issue 32, player state, and complete Phase 005 exit | Exact `EXT-003` proof and affected Tasks 004, 005, 010, 011, 014 |
| Sensitive player or exploit data enters evidence | Sanitization, field minimization, private handling | Evidence and diff privacy review | Remove exposed material where possible and use confidential remediation | Affected evidence and GitHub packet | Recreate sanitized evidence and rerun privacy review |
| Platform durability differs on Windows | Explicit force, move, and directory synchronization contract | Platform specific I/O result | Use only documented safe fallback, otherwise fail closed | Atomic write and runtime proof | Targeted Windows or equivalent platform evidence plus restart |
| A repair changes a schema or identifier | Compatibility gate before implementation | Diff detects serialized field, data name, codec, protocol, or config key change | Add migration and rollback contract or stop as plan revision needed | All old fixture, migration, restore, JAR, and downstream integration evidence | Tasks 004 through 016 |
| Audit repair occurs before issue filing | Mandatory Task 012 gate and commit timestamp audit | Repair diff predates issue record | Stop work, file or link canonical record before continuing, retain trace | Traceability and phase completion | Task 012 audit and affected issue packet |
| Selective cleanup is mistaken for recovery | Explicit destructive action prohibition | Proposed deletion, force clear, marker reset, or partial restore | Stop, preserve current state, use verified whole snapshot recovery | Recovery, operator documentation, and safety review | Tasks 010, 011, 015, and 016 |
| A late material repair leaves stale clean results | Evidence dependency map and exact revision manifests | Result revision differs from merged head | Rerun every affected downstream check | All results reachable from changed component | Focused proof followed by Tasks 014 through 016 |

## Phase Completion Packet

The execution owner stores this packet outside the protected plan set and supplies it before full Phase 005 closure or the blocker-aware internal integration transition:

- Exact starting and final commit IDs for Forge `1.20.1` and NeoForge `1.21.1`, with support line applicability and proof that each work branch started from the latest approved merged head.
- Phase 004 completion packet reference and remote ancestry proof.
- Complete persistence inventory with one row per named surface, support line disposition, data identity or path, owner, reader, writer, and evidence links.
- Detailed persistence and database audit, including the bounded proof that no separate database exists or complete rows for any discovered database.
- State ownership, journal lineage, checkpoint generation, replay, transaction, custody, ledger, stock, mint, claim, player data, market, shop, and backup cohort diagrams.
- Schema compatibility matrix, fixture catalog, fixture hashes, migration results, old and newer schema behavior, and code rollback boundaries.
- Atomicity and crash cut matrix, concurrency matrix, fault injection packet, restart and reconnect logs, and recovery results.
- Per workflow and global conservation reports, idempotency and replay matrix, and proof that every explicit source or sink is authorized and journaled.
- `EXT-003` preserved evidence or deterministic exact reproduction, sanitized before and after field level diff, restart and reconnect proof, and unrelated player data preservation result.
- Complete cold backup and matching restore rehearsal with manifests, checksums, exact build, stop state, restore sequence, post restore verification, and reversible handling of the replaced test world.
- For every finding, duplicate search, issue or private advisory, failing evidence, regression, repair commit, migration and rollback notes, review, pull request, merge commit, green checks, and post merge verification.
- Focused test results, complete `test`, applicable `runData`, applicable `runGameTestServer`, `build`, dedicated server, client when affected, multiplayer when required, JAR inspection, complete diff inspection, and exact environment details.
- Updated `DOCUMENTATION.md`, persistence audit, `docs/backup-restore.md`, `docs/README.md`, and every affected guide, with link and source consistency results.
- Evidence invalidation log showing every late change and the exact downstream checks rerun.
- Final clean Phase 005 audit with no unclassified finding, no unresolved phase owned defect, no destructive recovery instruction or code path, and no stale evidence.
- GitHub proof that all Phase 005 repairs are merged on the correct support lines and required checks remain green. Issues needing Phase 007 external acceptance remain open with the exact blocker.
- A downstream handoff that names every stable interface, schema, readiness state, failure code, persistence guarantee, recovery entry point, and exact merged revision consumed by `CORE-PHASE-006`.

## Next Transition

After every repository-controlled Phase 005 criterion passes and the completion packet is accepted, fetch the authoritative remotes and verify that each affected support branch contains its Phase 005 merge commit. `CORE-PHASE-006` then begins from those exact merged heads by reading [Phase 006](plan-phase-006.md) through EOF and validating its entry state against the frozen persistence inventory, lineage map, schema matrix, conservation report, and recovery contracts.

Do not create or start a Phase 006 branch while any Phase 005 pull request is open, queued, awaiting checks, unmerged, or absent from the authoritative support branch. If `EXT-003` remains unavailable, use only the master-defined internal integration transition, carry the blocker visibly into Phase 007, and do not report Phase 005, `CORE-REQ-012`, issue 32, or the plan complete. Phase 006 may consume only the exact individually clean repository-controlled persistence and invariant interfaces in the accepted handoff. Any later change to a Phase 005 store, schema, migration, transaction boundary, claim, recovery path, or conservation rule invalidates the affected handoff evidence and must be reverified before backend integration can claim it as clean.
