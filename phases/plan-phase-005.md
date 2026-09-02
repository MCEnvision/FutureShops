# Phase 005 Execution Plan

> **Plan ID:** PLAN-PHASE-005
> **Phase ID:** CORE-PHASE-005
> **Owner:** Persistence subsystem
> **Classification:** MANDATORY
> **Master plan:** [plan.md](../plan.md)
> **Phase sequence:** 005 of 007

## Purpose and Ownership

This phase completes the source grounded persistence and database audit for FutureShops and proves every economic invariant that depends on durable state. It inventories every authoritative store, recovery record, retained legacy source, write capable cache, configuration file, catalog, player or block NBT field, reader, writer, migration, reload path, backup cohort, and recovery path. It then exercises normal behavior, bounded corruption, crash cuts, disk and force failures, concurrency, restart, reconnect, and repeated recovery until every repository controlled persistence surface has exact local evidence and no known phase owned defect remains.

This file owns the execution blueprint for `CORE-REQ-012` and `CORE-REQ-014`. The master plan owns product scope, support line topology, decisions, phase order, and the plan wide Definition of Done. `CORE-PHASE-002` owns issue 32 defect resolution and supplies its player state ownership map and deterministic corpus. This phase consumes, expands, and reruns that corpus as a mandatory part of the complete persistence audit. A newly verified persistence or conservation defect follows `CORE-REQ-009` and is filed before repair.

`EXT-003` is superseded historical traceability only. It is never a dependency, evidence request, blocker, transition condition, or endpoint gate. Missing reporter data cannot qualify, delay, or weaken Phase 005. The required issue 32 successor proof is generated locally from deterministic fixtures under `DEC-007`.

Phase 005 has one full exit. All local evidence must pass, every verified repair must merge through the correct pull request, the Phase 005 pull request must merge into `1.20.1`, exact merged revision checks must pass, the signed phase tag must verify, and no mandatory gate may remain unresolved at Phase 006 entry. This phase does not authorize destructive recovery or release publication.

## Evidence-Based Entry State

| Evidence class | Area | Finding | Source or command | Freshness condition |
|---|---|---|---|---|
| VERIFIED | Phase assignment | `CORE-PHASE-005` owns `CORE-REQ-012` and `CORE-REQ-014`, depends on `CORE-PHASE-004`, and transitions only to `CORE-PHASE-006` | `plan.md`, Sections 12 and 13 | Any authoritative Plan Creator revision |
| VERIFIED | Safety boundary | Player data, worlds, journals, checkpoints, ledgers, custody, claims, receipts, migration markers, and recovery evidence may not be deleted or selectively restored as repair or verification | `NG-003`, `NG-004`, master Sections 15 and 17, and `SRC-013` | Any authoritative Plan Creator revision |
| OBSERVED | Durable architecture | World `SavedData`, escrow journal generations, checkpoints, replay receipts, player and block NBT, JSON catalogs, TOML configuration, migration records, and file backed recovery stores comprise the observed durable surface | `SRC-002`, `SRC-003`, `SRC-011`, `DOCUMENTATION.md`, `docs/backup-restore.md`, and current source | Any persistence source, storage path, codec, build dependency, or documentation change |
| OBSERVED | Escrow lineage | Runtime state, journal sequence, checkpoints, transactions, ledger, custody, claims, mints, stock, item inventory evidence, prepared records, commit decisions, delivery receipts, terminal receipts, and replay records form one recovery lineage | `SRC-002`, escrow documentation, and `server/escrow` source | Any lineage, checkpoint, replay, receipt, claim, or store binding change |
| OBSERVED | Migration and reload contracts | Legacy balances, mints, history, settlements, catalog stock, Server Shop offers, Player Shop offers, configuration paths, Bazaar products, and replay evidence have compatibility or migration duties | `SRC-002`, `SRC-003`, migration packages, loaders, writers, and codecs | Any schema, identifier, migration, loader, writer, or reload change |
| OBSERVED | Existing verification | Focused tests cover journals, checkpoints, `SavedData`, custody, claims, ledger conservation, stock, migrations, item inventory recovery, markets, and catalog atomicity, but do not by themselves prove complete Phase 005 coverage | `SRC-011` and the exact merged Phase 004 test inventory | Any relevant implementation, fixture, harness, dependency, or test selection change |
| ENTRY CONTRACT | Issue 32 successor evidence | Phase 002 provides a deterministic local corruption and recovery corpus, ownership classifications, modded item and unrelated player NBT sentinels, and merged repair evidence. Phase 005 must rerun and extend it across the full persistence surface | Phase 002 completion packet, `DEC-004`, `DEC-007`, and historical `EXT-003` | Any player persistence, receipt, delivery, claim, transaction, codec, lifecycle, or recovery change |
| AVAILABLE | Local runtime capacity | The 64 GB workstation is the default isolated dedicated server and multiple client environment. The 96 GB node1 host may run the temporary isolated server when it improves capacity or repeatability | `SRC-014` and `DEC-007` | A verified local capacity change, which requires rescheduling or harness repair before phase exit |
| AVAILABLE | GitHub traceability | Authenticated EnVisione access is available for duplicate search, issue filing, pull requests, checks, merges, tags, and evidence synchronization | `EXT-005` | Authentication, permission, remote, or repository identity changes |
| ENTRY GATE | Upstream integration | Every Phase 004 repair is merged on its affected support line and the Phase 005 Forge branch starts from the exact verified `origin/1.20.1` merge | Phase 004 completion packet and fresh remote ancestry | Any late upstream change, failed check, or unmerged repair |

No observed or proposed result becomes `VERIFIED` without an exact revision, fixture or corpus identity, environment, procedure, decisive result, and retained evidence. Historical `EXT-003` text cannot invalidate or replace local proof.

## Scope Boundaries

### Included Scope

- `CORE-REQ-012`: Complete the persistence and database inventory and audit every journal, checkpoint, ledger, custody store, claim store, receipt store, `SavedData` object, player and block NBT field, JSON file, TOML file, migration, atomic write, backup, restore, reload, concurrency, integrity, corruption, and recovery path.
- `CORE-REQ-014`: Prove logical server authority, signed integer minor units, checked arithmetic, stable request UUID idempotency, exact item and money conservation, durable accessible claims, compatible persistence, fail closed readiness, and zero silent loss.
- `CORE-REQ-009`: Deduplicate and file every verified repository owned defect before its first repair edit. Confidential findings use the private vulnerability route.
- Issue 32 successor verification: Build and retain a mandatory local deterministic corruption and recovery corpus that covers bounded fuzzing, truncation, partial writes, schema versions, unknown fields, bad checksums, ambiguous ownership, modded item sentinels, unrelated player NBT sentinels, crash cuts, disk and force faults, login, logout, restart, reconnect, receipts, delivery slot proofs, claims, and non destructive recovery.
- `DEC-007`: Use a local dedicated Forge server and at least two independent client JVMs and profiles for player lifecycle, reconnect, claim, receipt, delivery, and concurrent state proof. Node1 may host only the isolated server fallback.
- Support line isolation: Audit both exact supported trees. Repair only a line proven affected. A surface absent from one line receives source backed not applicable evidence.
- Phase integration: Merge the Phase 005 Forge work through a pull request into `1.20.1`, verify the exact remote merge, rerun required evidence, and create the signed annotated tag `phase-005-persistence-recovery` on that merge commit.

### Explicit Exclusions

- `CORE-REQ-013` full cross component backend combination testing remains owned by `CORE-PHASE-006`. Phase 005 supplies individually clean persistence and invariant contracts.
- `CORE-REQ-015`, `CORE-REQ-017`, `CORE-REQ-018`, `CORE-REQ-019`, and `CORE-REQ-020` plan wide verification, final documentation reconciliation, candidate packaging, repeated clean audits, and final closure remain owned by `CORE-PHASE-007`. Phase 005 still completes all verification, documentation, merge, tag, and issue work required for its own exit.
- `FUT-001` through `FUT-005` remain excluded. No GitHub Release, CurseForge or Modrinth upload, announcement, stable designation, or public product release tag is authorized.
- No test or recovery may delete, force clear, edit in place, selectively replace, or selectively restore player data, worlds, journals, checkpoints, ledgers, custody, claims, receipts, migration markers, or recovery evidence.
- No repair may bypass maintenance, readiness, replay protection, journal lineage, conservation, claim access, schema compatibility, permissions, or logical server authority.
- Dependency upgrades, platform upgrades, unrelated schema redesign, a new storage framework, distributed live market state, and direct external storage listings without deterministic receipts are outside this phase.
- `EXT-003` may appear only in historical traceability statements. It cannot appear in executable dependencies, blockers, failure routing, completion alternatives, or downstream handoff conditions.

## Phase Contract

### CORE-PHASE-005 — Persistence, Recovery, and Conservation Closure

**Objective:** At exact merged support line revisions, prove every FutureShops persistence and database surface, recovery lineage, request identity, delivery receipt, claim, and economic mutation under normal, concurrent, corrupt, partial, crash, disk, restart, reconnect, backup, and restore conditions, with every verified defect filed before repair, no destructive recovery, and no unresolved mandatory gate.
**Owner:** Persistence subsystem
**Dependencies:** CORE-PHASE-004, CORE-REQ-009, CORE-REQ-010, CORE-REQ-011, DEC-006, DEC-007, EXT-005
**Canonical requirements:** CORE-REQ-012, CORE-REQ-014
**Documentation and release impact:** Update the canonical technical, persistence audit, schema, migration, configuration, backup, restore, recovery, and troubleshooting documentation. Record candidate implications without changing locked versions or publishing any artifact.
**Next transition:** CORE-PHASE-006

**Entry criteria**

- Phase 004 is fully merged, required checks pass, the signed Phase 004 tag is verified, and fresh remote ancestry identifies the exact Forge and NeoForge entry revisions.
- The Phase 005 Forge branch derives from the latest approved `origin/1.20.1`. A separate NeoForge branch is created only if exact evidence proves that line needs a repair.
- The Phase 002 issue 32 corpus, ownership map, local evidence, and merged repair packet are readable and its historical `EXT-003` reference is classified as superseded traceability only.
- The complete persistence surface can be enumerated from source, build dependencies, runtime layout, tests, configuration, and tracked documentation.
- EnVisione GitHub access and signing configuration are verified before issue, pull request, merge, or tag operations.
- Disposable current, legacy, corrupt, and mixed generation fixture families exist with complete matching backups, deterministic hashes, stable seeds, exact starting totals, and at least two independent client profiles.

**Implementation scope**

- `CORE-REQ-012` freezes every durable and quasi durable surface, owner, data identity, reader, writer, lifecycle trigger, schema, bounds, lock, atomicity unit, durability boundary, integrity check, migration, backup cohort, corruption response, recovery path, privacy class, and conservation relationship.
- `CORE-REQ-012` proves by source, dependency, configuration, and runtime inspection whether any embedded, remote, or external database exists. A negative result is recorded with evidence. A discovered store receives complete audit rows before further work.
- `CORE-REQ-012` and `CORE-REQ-014` trace each economic intent from request UUID and immutable fingerprint through validation, prepare, custody or reservation, durable journal evidence, commit decision, ledger and stock application, exact delivery slot receipt, claim or compensation, terminal receipt, checkpoint, replay, restart, and recovery.
- `CORE-REQ-012` exercises bounded deterministic corruptions and fuzz inputs across journal, checkpoint, replay, `SavedData`, player and block NBT, JSON, TOML, catalog, market, migration, receipt, and claim families.
- `CORE-REQ-012` and `CORE-REQ-014` inject faults only into isolated copies, preserve every failed state, and prove bounded, repeatable, non destructive recovery.
- `CORE-REQ-009`, `CORE-REQ-012`, and `CORE-REQ-014` require every verified correction to be filed before repair, merged through the correct support line pull request, and rerun at the exact merged revision before tagging and transition.

**Execution order**

1. `P005-TASK-001` executes `CORE-REQ-012` by freezing exact support line revisions and reconciling the complete persistence and database inventory.
2. `P005-TASK-002` executes `CORE-REQ-012` and `CORE-REQ-014` by mapping canonical and derived state ownership, journal lineage, commit boundaries, receipt and delivery slot proof, and backup cohorts.
3. `P005-TASK-003` executes `CORE-REQ-012` by completing the detailed persistence and database audit and proving whether any unlisted storage system exists.
4. `P005-TASK-004` executes `CORE-REQ-012` and `CORE-REQ-014` by auditing schemas, codecs, bounds, unknown fields, old, current, and newer versions, canonical item semantics, and incompatible input behavior.
5. `P005-TASK-005` executes `CORE-REQ-012` and `CORE-REQ-014` by auditing every migration and reload path with deterministic legacy, current, malformed, newer, conflicting, and interrupted fixtures.
6. `P005-TASK-006` executes `CORE-REQ-012` and `CORE-REQ-014` by auditing atomicity, journal and checkpoint force ordering, file replacement, receipt persistence, shutdown, and crash cuts.
7. `P005-TASK-007` executes `CORE-REQ-012` and `CORE-REQ-014` by auditing logical thread ownership, locks, stale revisions, simultaneous writers, local multiple client races, scheduler bounds, and shutdown races.
8. `P005-TASK-008` executes `CORE-REQ-014` by proving checked arithmetic and conservation across wallets, ledgers, stock, mints, custody, claims, shops, markets, ATM, fees, and explicit sources or sinks.
9. `P005-TASK-009` executes `CORE-REQ-012` and `CORE-REQ-014` by proving request UUID idempotency and replay identity across retries, disconnect, logout, reconnect, restart, checkpoint, compaction, and repeated recovery.
10. `P005-TASK-010` executes `CORE-REQ-012` and `CORE-REQ-014` by running the mandatory local issue 32 successor corpus and complete corruption and fault campaign.
11. `P005-TASK-011` executes `CORE-REQ-012` and `CORE-REQ-014` by rehearsing cold backup, whole cohort restore, rollback refusal or compatibility, startup replay, login, logout, restart, reconnect, and repeated recovery.
12. `P005-TASK-012` executes `CORE-REQ-009` by performing duplicate search and creating or enriching the canonical issue before any repair for each verified finding.
13. `P005-TASK-013` executes `CORE-REQ-012` and `CORE-REQ-014` by implementing the smallest compatible repair and rerunning every invalidated local evidence row.
14. `P005-TASK-014` executes `CORE-REQ-012` and `CORE-REQ-014` by running the complete verification ladder on the final phase branch, merging through the required pull request, and rerunning required checks at the exact merged revision.
15. `P005-TASK-015` executes `CORE-REQ-012` and `CORE-REQ-014` by reconciling tracked technical documentation, the persistence audit, compatibility matrices, and operator procedures with merged behavior.
16. `P005-TASK-016` executes `CORE-REQ-012` and `CORE-REQ-014` by verifying remote ancestry, the signed phase tag, issue and check state, evidence freshness, no unresolved mandatory gate, and the exact Phase 006 handoff.

Tasks 001 through 003 are strictly ordered. After Task 003, read only analysis and fixture preparation for Tasks 004 through 010 may proceed independently only when they do not mutate a shared store or assume an unverified boundary. Task 012 precedes the first repair edit for each finding. Tasks 014 through 016 are strictly ordered after the last material repair.

**Required evidence**

- One exact revision persistence and database audit with no unclassified durable, retained, derived, write capable, or external state path.
- One schema and migration matrix with deterministic fixtures for supported older data, current data, unsupported newer data, unknown compatible fields, malformed types, boundary sizes, missing registry values, bad checksums, partial records, and interrupted migrations.
- One lineage and state transition packet connecting request UUID, transaction UUID, journal sequence, checkpoint generation, ledger receipt, custody lot, stock reservation, inventory receipt, exact delivery slots, claim, replay receipt, and recovery action.
- A mandatory local issue 32 corpus with stable seeds and fixture hashes, minimized failures, ambiguous ownership controls, modded item sentinels, unrelated player NBT sentinels, before and after semantic diffs, and clean repeated recovery.
- Bounded corruption and fuzzing results for journals, checkpoints, ledgers, custody, claims, receipts, every applicable `SavedData`, NBT, JSON, TOML, migrations, reloads, backups, and restore manifests.
- Fault results for truncation, partial write, checksum mismatch, stale or mixed lineage, disk full, access denial, short write, file force, directory force, atomic move, shutdown, checkpoint, compaction, and every defined transaction crash cut.
- Local dedicated server and at least two independent client evidence for login, logout, disconnect, retry, restart, reconnect, claim access, receipt replay, delivery slot reconciliation, concurrent mutation, and repeated recovery.
- Per workflow and global conservation reports using checked integer arithmetic, exact item units, stable identities, explicit sources and sinks, and no unexplained delta.
- For every verified defect, duplicate search, issue or private advisory timestamp before repair, failing regression, repair, review, pull request, merge, green checks, and exact post merge verification.
- Phase 005 Forge pull request merge into `1.20.1`, fresh `origin/1.20.1` containment, exact merged revision verification, and a verified signed annotated tag `phase-005-persistence-recovery` on the merge commit.

**Exit criteria**

- Every persistence inventory row has all mandatory audit fields and exact evidence. No reader, writer, write capable cache, retained source, file family, recovery record, or storage dependency remains unclassified.
- The deterministic issue 32 successor corpus and all bounded corruption, fuzz, partial write, schema, checksum, ownership, sentinel, lifecycle, disk, force, crash, backup, restore, atomicity, concurrency, idempotency, and conservation rows pass locally at the final merged Forge revision.
- Every verified repository defect was filed before repair, repaired on the correct support line, reviewed, merged through GitHub, and verified at the exact remote merge revision.
- No destructive recovery, selective restore, silent schema discard, unsafe acknowledged partial write, unbounded mutation, inaccessible durable claim, ambiguous receipt, unreconciled delivery slot, or unexplained value creation or loss remains.
- The Phase 005 pull request is merged into `1.20.1`, all required checks are green, `origin/1.20.1` contains the merge, and any independently required NeoForge repair is also merged and verified on `1.21.1`.
- The exact merged revision reruns focused tests, full tests, applicable data generation, applicable GameTests, build, dedicated server, client, at least two client workflows, restart, reconnect, backup, restore, JAR inspection, and full diff inspection successfully.
- The signed annotated tag `phase-005-persistence-recovery` targets the exact merged `1.20.1` commit, verifies as EnVisione, and is present on the remote.
- The final audit rerun finds no unclassified finding, no known phase owned defect, no stale evidence, and no blocker to carry into Phase 006.
- No known mandatory phase-owned defect remains.
- Phase 005 does not publish a product release, upload an artifact, announce a release, or create a public product version tag.

## Inputs and Upstream Contracts

| Input or contract | Provider | Required state | Validation | Failure behavior |
|---|---|---|---|---|
| Phase 004 completion packet | `CORE-PHASE-004` | Repairs merged on every affected line, green checks, exact remote revisions, signed tag, and complete persistence security handoff | GitHub pull request, check, ancestry, tag, and packet comparison | Stop entry and finish the upstream gate. Do not branch from an open or stacked phase |
| Live product contract | `plan.md` | `CORE-REQ-012`, `CORE-REQ-014`, `DEC-006`, `DEC-007`, non goals, and branch boundaries remain coherent | Read through EOF and compare stable IDs and topology | Stop on a material contract conflict. Do not reinterpret scope locally |
| Phase 002 player state packet | `CORE-PHASE-002` | Deterministic issue 32 corpus, ownership map, sentinels, merged repair evidence, and no unresolved mandatory gate | Recreate fixtures from seeds and hashes, rerun control cases, compare exact merged revision | Repair corpus or product defects locally under the issue first gate. Never request `EXT-003` input |
| Legacy invariants | `SRC-002`, `SRC-003` | Journal, ledger, custody, claims, migration, catalog, receipt, and safe writing rules remain mapped | Trace every invariant to current source, tests, or retained historical classification | A verified gap enters Task 012 before repair |
| Exact persistence source | `SRC-011` | Current source, dependencies, tests, runtime layouts, and docs for both support lines | Source inventory, CodeGraph, build dependency, data name, codec, and test discovery | Inventory remains open until every reference is classified |
| GitHub and signing authority | `EXT-005` | EnVisione identity, correct remote, issue and pull request authority, registered signing key | Auth status, repository identity, test signature, issue and check query | Stop remote operations and restore access before phase exit. Do not carry the blocker forward |
| Local runtime harness | `DEC-007` | Disposable dedicated server, at least two client JVMs and profiles, complete fixture cohorts, and matching backups | Environment, process identity, world and config hashes, starting state, recovery rehearsal | Repair or reschedule the local harness and rerun. No lower fidelity substitute can satisfy the mandatory runtime gate |

## Outputs and Downstream Contracts

| Output or contract | Consumer | Guaranteed state | Compatibility or versioning | Evidence |
|---|---|---|---|---|
| Frozen persistence inventory | `CORE-PHASE-006`, `CORE-PHASE-007`, maintainers | Every durable surface, write capable projection, reader, writer, path, and owner is classified | Bound to exact merged support line revisions | Audit rows, source links, data identities, paths, and revision manifest |
| State ownership and lineage map | `CORE-PHASE-006` | Canonical and derived state, commit decision, receipts, delivery slots, replay, claims, and recovery transitions are explicit | Stable identities and backward compatible lineage are preserved | Call paths, transition tables, crash cuts, and restart traces |
| Schema and migration matrix | `CORE-PHASE-006`, operators | Old, current, newer, unknown field, invalid, reload, and rollback behavior is deterministic | Changed writers retain compatible readers or an explicit migration | Fixture hashes, codec tests, and migration results |
| Issue 32 local corpus | `CORE-PHASE-006`, `CORE-PHASE-007` | Repository controlled corruption and recovery proof covers the owned boundary without reporter evidence | Historical `EXT-003` has no executable role | Seeds, hashes, minimized cases, sentinels, semantic diffs, and repeated recovery |
| Recovery and backup runbook | Operators and `CORE-PHASE-007` | Cold backup, whole cohort restore, refusal conditions, and recovery are non destructive | Restore uses one matching generation and compatible build | Rehearsal logs, manifests, checksums, and tracked guide |
| Conservation and idempotency packet | `CORE-PHASE-006`, `CORE-PHASE-007` | Every mutation balances and every replay converges to one result | Checked minor units, exact items, and stable UUIDs remain authoritative | Property tests, runtime traces, and per workflow equations |
| Merged Phase 005 revision and tag | Sequential phase governance | Phase work is merged into `1.20.1`, exact merged evidence passes, and the signed tag marks that commit | Forge 1.20.1 and candidate identity remain unchanged until Phase 007 | Pull request, checks, merge, ancestry, tag, signature, and remote proof |

## Complete Persistence Inventory Baseline

`P005-TASK-001` must reconcile every named surface below against both exact support line trees. Group headings are discovery aids only. Each data identity, file family, codec, retained source, and write capable cache receives its own row. A newly discovered surface is added before the audit can close.

### Canonical stores and world state

| Inventory family | Required individual surfaces |
|---|---|
| Escrow and value state | `EscrowRuntimeSavedData`, `EscrowTransactionSavedData`, `LedgerSavedData`, `CustodySavedData`, `ClaimSavedData`, `ProtectedMintSavedData`, `StockSavedData`, `ItemInventoryJournalSavedData`, `ServerShopIntentSavedData`, `PlayerShopEscrowSavedData`, `EscrowAdministrativeAuditSavedData` |
| Server Shop replay and receipts | `ServerShopOfferPreparedSavedData`, `ServerShopOfferCommitSavedData`, `ServerShopOfferCartPreparedSavedData`, `ServerShopOfferCartCommitSavedData`, `ServerShopOfferTerminalReceiptSavedData`, `ServerShopOfferUsageSavedData`, `AdminShopOfferSaveSavedData`, inventory receipts, delivery receipts, and exact changed slot evidence |
| Markets | `AuctionHouseSavedData`, `BazaarSavedData`, `MarketControlSavedData`, `MarketProfileSavedData`, listing and order custody, fills, cancellations, expiry, and claim handoffs |
| Economy and migration | `InternalBalanceSavedData`, `SpentMintsSavedData`, `LegacyBalanceMigrationSavedData`, `CatalogStockMigrationSavedData`, `TransactionHistorySavedData`, explicit mint and sink records |
| Player Shop and administration | `PlayerShopRegistrySavedData`, `PlayerShopSettlementSavedData`, `PlayerShopSavedConfigs`, `AdminShopToggleSavedData`, `FranchiseSavedData`, `DepartmentSavedData`, `AdminCategorySavedData`, `ShopLimitsSavedData`, `StockRefreshSavedData`, `DynamicPricingSavedData` |

### File backed, NBT, configuration, and recovery surfaces

| Inventory family | Required individual surfaces and proof |
|---|---|
| Escrow write ahead journals | `WriteAheadJournal`, record framing and codec, active and retained generations, legacy journal, writer ownership, sequence, checksum, force, truncation, compaction, and recovery |
| Checkpoints | `EscrowCheckpointStore`, manager, codec, bundle, generation manager, manifests, retained journal pairs, snapshot boundary, force and move order, verification, lineage, and restore selection |
| Replay ledger | `ServerShopOfferReplayLedger`, index WAL, sharded receipt files, process lock, receipt authority, cursor, bounds, atomic move, rebuild, compaction, and restart behavior |
| Exact item evidence | `PersistentItemInventoryJournal`, receipt codecs, before and after slot proof, delivery slot proof, canonical NBT and Forge capability semantics, player save and force results, and reconciliation owner |
| ATM and redemption | Protected and foreign cash intent stores, active player recovery identity, settlement evidence, mint linkage, orphan handling, claim owner, and manual review boundary |
| Player and block NBT | FutureShops owned player fields, unrelated top level and nested sentinels, modded item sentinels, `ShopBlockEntity`, Player Shop offer codecs, legacy migrations, login and logout hooks, and block removal |
| Server Shop JSON | Shop config root, parsers, normalized offer writers, temporary files, backups, recovery files, whole candidate validation, force, atomic move, reload, rollback, and unknown field preservation |
| Bazaar JSON | Product root, storage, registry and coordinator snapshots, identity and version, size and path bounds, atomic publication, last valid state, and retirement |
| Forge TOML | Common, client, escrow, Auction House, and Bazaar configuration, path migration, validation, last valid snapshot, symlink rejection, conflict backup, reload thread, and restart |
| Derived state | Repositories, indexes, caches, runtime bindings, capability snapshots, usage projections, and schedulers with their canonical source, rebuild, publication order, invalidation, and concurrency owner |
| Backup cohort | Complete world, `data/`, escrow files, replay files, `playerdata/`, configuration, catalogs, migration backups, manifests, exact build, shutdown state, checksums, restore order, and post restore proof |

The audit must prove whether a separate embedded database, remote database, external transaction store, or hidden storage adapter exists. If none exists, record the bounded negative result with build dependency, configuration, source, and runtime evidence. If one exists, add rows for connection ownership, transactions, isolation, schema, migrations, backup, restore, secrets, failure behavior, and reconciliation before continuing.

## Detailed Persistence and Database Audit Deliverable

Maintain one tracked source grounded audit at the established focused documentation location. If none exists, use `docs/persistence-database-audit.md` and link it from `docs/README.md` and `DOCUMENTATION.md`. Each inventory row must record:

1. Stable inventory ID, support line, component owner, source owner, and operator owner.
2. Data identity, relative path or `SavedData` name, containment, authority class, privacy class, and retention.
3. Record keys, parent and child relationships, request, transaction, receipt, slot, claim, lineage, revision, and replay identities.
4. Current schema, supported older versions, newer schema response, unknown field policy, duplicate field policy, type and size bounds, and canonical NBT rules.
5. Every reader and load trigger and every writer and save trigger, including startup, login, logout, reload, request, prepare, commit, delivery, claim, recovery, shutdown, and compaction.
6. Logical side, thread, lock, lock order, reentrancy, concurrent access, stale revision, and second process behavior.
7. Atomicity unit, force and flush order, temporary file and move rule, checkpoint coupling, receipt boundary, delivery proof, and crash guarantee.
8. Checksums, framing, lineage, fingerprints, registry validation, semantic equality, cross store reconciliation, and failure diagnostics.
9. Migration chain, deterministic identity, repeat behavior, completion marker, backup point, rollback boundary, retained source, and last valid reload behavior.
10. Backup cohort, required stop state, manifest, checksum, restore order, compatible build, post restore verification, and refusal conditions.
11. Malformed, missing, truncated, partial, stale, conflicting, bad checksum, ambiguous ownership, unsupported newer data, disk, force, and recovery behavior.
12. Maintenance, quarantine, manual review, retry, compensation, claim, evidence preservation, and non destructive operator recovery.
13. Money, item, stock, mint, fee, custody, claim, and source or sink legs with exact conservation equations.
14. Existing tests, missing tests, deterministic fixtures, seeds, runtime procedure, exact result, evidence revision, and finding disposition.

Broad labels such as atomic, thread safe, uses `SavedData`, or backed up are insufficient. A not applicable result must prove the concept is absent from that support line.

## Work Packages

| Task ID | Requirement IDs | Work | Inputs and dependencies | Outputs | Affected components or interfaces | Verification |
|---|---|---|---|---|---|---|
| `P005-TASK-001` | `CORE-REQ-012` | Freeze exact revisions and enumerate every persistent, retained, derived, database, and write capable surface on both lines | Phase 004 packet, `SRC-002`, `SRC-003`, `SRC-009`, `SRC-011` | Complete per surface inventory | All stores, files, NBT, JSON, TOML, caches, and backup cohorts | Source, dependency, data name, reader, writer, runtime file tree, and test reconciliation |
| `P005-TASK-002` | `CORE-REQ-012`, `CORE-REQ-014` | Trace canonical ownership, journal and checkpoint lineage, receipts, exact delivery slots, claims, replay, compensation, and backup cohorts | Task 001 and legacy invariants | Ownership map, lineage tables, receipt and slot proof model, backup graph | Escrow, ledger, custody, claims, stock, markets, shops, ATM, player data | Call paths and controlled traces before and after restart |
| `P005-TASK-003` | `CORE-REQ-012` | Complete every audit field and prove whether any external or embedded database exists | Tasks 001 and 002 | Tracked audit and exact revision manifest | Dependencies, configuration, adapters, and every inventory row | Independent source to audit reconciliation and no unclassified I/O |
| `P005-TASK-004` | `CORE-REQ-012`, `CORE-REQ-014` | Audit schemas, codecs, bounds, unknown fields, versions, checksums, canonical item semantics, and safe refusal | Tasks 001 through 003 | Compatibility matrix and fixture catalog | `SavedData`, journals, checkpoints, receipts, offers, player NBT, JSON, TOML | Round trip, old, new, malformed, boundary, unknown field, registry, and checksum tests |
| `P005-TASK-005` | `CORE-REQ-012`, `CORE-REQ-014` | Audit migrations and reloads for determinism, idempotency, preservation, last valid state, and rollback | Task 004 | Migration and reload matrix | Balance, mint, settlement, stock, offers, Player Shop, config, catalogs, Bazaar | First, interrupted, repeated, mixed version, conflict, invalid input, restart, and rollback tests |
| `P005-TASK-006` | `CORE-REQ-012`, `CORE-REQ-014` | Prove atomicity and durability at journal, force, side effect, commit, receipt, claim, checkpoint, compaction, file replacement, and shutdown boundaries | Tasks 002 through 005 | Durability tables and crash cut matrix | WAL, checkpoints, replay, `SavedData`, player saves, JSON and TOML writers | Fault immediately before and after each boundary, restart twice, and reconcile state |
| `P005-TASK-007` | `CORE-REQ-012`, `CORE-REQ-014` | Audit thread ownership, locks, stale revisions, concurrent writers, scheduler bounds, and shutdown races | Tasks 002 and 003, `DEC-007` | Concurrency matrix | Logical server services, repositories, file locks, listings, orders, stock, recovery | At least two clients exercise every player or network state race, while isolated process tests cover store locks and second writer refusal |
| `P005-TASK-008` | `CORE-REQ-014` | Define and prove conservation equations for every value mutation family | Task 002 and exact starting manifests | Per workflow and global conservation reports | Wallets, ledger, money, mints, stock, custody, claims, shops, markets, ATM, fees | Checked arithmetic properties, bounds, partial failure, recovery, and exact deltas |
| `P005-TASK-009` | `CORE-REQ-012`, `CORE-REQ-014` | Prove request identity, idempotent retries, replay, compaction survival, and fingerprint conflict rejection | Tasks 002, 004, and 006 | Request and replay matrix | Packets, commands, prepared records, commits, receipts, usage, replay, recovery | Duplicate at each transition, changed payload, dropped response, logout, reconnect, restart, and compaction |
| `P005-TASK-010` | `CORE-REQ-012`, `CORE-REQ-014` | Run bounded corruption and fuzzing across all applicable inventory families and the mandatory local issue 32 corpus | Tasks 004 through 009 and Phase 002 corpus | Minimized corpus, fault packet, ownership classifications, preserved failed states, recovery proof | Player state, modded items, receipts, delivery slots, claims, escrow, journals, checkpoints, ledgers, custody, `SavedData`, JSON, TOML | Seeds, hashes, bounds, truncation, partial writes, schema versions, unknown fields, bad checksums, ownership ambiguity, disk and force faults, crash cuts, login, logout, restart, reconnect |
| `P005-TASK-011` | `CORE-REQ-012`, `CORE-REQ-014` | Rehearse cold backup, whole cohort restore, rollback boundaries, startup replay, lifecycle transitions, and repeated recovery | Tasks 005, 006, 009, and 010 | Backup manifest, restore log, post restore audit | Whole world, data, escrow, replay, player data, configuration, catalogs, exact build | Stop, backup, mutate copy, restore one generation, start, login, logout, reconnect, verify, repeat |
| `P005-TASK-012` | `CORE-REQ-009`, `CORE-REQ-012`, `CORE-REQ-014`, `EXT-005` | Search duplicates and create or enrich the canonical issue before repair for every verified finding | Reproducible sanitized finding | Issue or private advisory with acceptance criteria | GitHub workflow and confidential route | Timestamped query and issue record predating repair diff and commit |
| `P005-TASK-013` | `CORE-REQ-012`, `CORE-REQ-014` | Implement the smallest compatible repair and preserve lineage, schemas, unknown fields, and unrelated player state | Task 012 for that finding | Regression, focused change, documentation delta, reviewed merge candidate | Only proven responsible components | Failing before, passing after, invalidation reruns, review, checks, merge, and post merge proof |
| `P005-TASK-014` | `CORE-REQ-012`, `CORE-REQ-014` | Run complete branch verification, merge through the required pull request, and rerun exact merged revision proof | Last Task 013 repair | Merged clean revision packet | Tests, data, GameTests, build, server, clients, backup, restore, JAR, diff | Required ordered commands and every real workflow, with no skipped mandatory row |
| `P005-TASK-015` | `CORE-REQ-012`, `CORE-REQ-014` | Reconcile audit, architecture, migration, configuration, backup, restore, recovery, and troubleshooting docs with merged behavior | Tasks 003 through 014 | Updated tracked documentation | `DOCUMENTATION.md`, `docs/backup-restore.md`, `docs/README.md`, focused guides, `README.md` when needed | Source review, link checks, literal path and command checks, destructive advice scan |
| `P005-TASK-016` | `CORE-REQ-012`, `CORE-REQ-014`, `EXT-005` | Verify traceability, remote merge, exact evidence freshness, signed tag, no unresolved mandatory gate, and Phase 006 handoff | Tasks 001 through 015 | Full completion packet and accepted Phase 006 entry | Git, GitHub, audit docs, tests, runtime evidence, tag | Ancestry, checks, signature, final clean audit, no open phase defect, no unresolved mandatory gate |

## Architecture and Implementation Boundaries

### Authority and State Flow

The logical server is the sole authority for balances, stock, item ownership, listings, orders, bids, claims, permissions, module state, and persistence mutation. Client state and packets may request or display state but cannot author value, ownership, schema, completion, or recovery.

Every mutation uses one stable request UUID and immutable fingerprint. Validation precedes prepare. Custody or reservation and durable journal evidence precede externally visible side effects. `COMMIT_DECIDED` is the ownership boundary. Before it, recovery returns assets to the original owner. After it, recovery completes exact delivery or creates a durable claim. Receipts identify the exact inventory slots and before and after item proofs used for delivery. A receipt is confirmed only after the player mutation and required player save or force evidence succeed. Ambiguous slot or save results retain custody or enter bounded manual review without rewriting unrelated player state.

Checkpoints include only state known durable through a journal sequence. Compaction begins only after checkpoint verification. Startup binds one journal lineage to matching `SavedData`, checkpoint, replay, player data, configuration, catalog, and receipt cohorts. A mismatch fails closed.

### Schema, Atomicity, Concurrency, and Performance

- Durable records define current, supported older, and unsupported newer behavior. Compatible unknown fields are preserved by operations that do not own them.
- Missing registry identities preserve raw recoverable evidence. They are not replaced, dropped, or rewritten as another item.
- Writers validate a complete candidate, write inside the approved root, force content, atomically replace where supported, establish the documented directory durability result, verify the result, and preserve the prior valid generation on failure.
- Simulated disk full, access denial, short write, force failure, directory force failure, and atomic move failure must produce no acknowledged unsafe state.
- All authoritative mutation runs on the logical server thread or through an explicitly serialized boundary. Lock ownership and lock order are documented. A second live server process cannot share a write store.
- Recovery, checkpoint, migration, matching, expiration, replay, and claim work remains bounded. No audit repair adds filesystem I/O, broad scans, or blocking waits to a tick, render, or packet hot path.

## Failure, Recovery, and Edge Cases

| Scenario | Detection | Required behavior | Recovery or rollback | Regression proof |
|---|---|---|---|---|
| Truncated final journal frame | Length, framing, sequence, or end of file scan | Ignore only the incomplete tail and preserve all earlier valid records | Replay from the latest verified checkpoint and valid sequence | Truncate at every byte boundary of the final frame |
| Interior corruption or bad checksum | Frame checksum, sequence, manifest, or store reconciliation | Enter maintenance and refuse new value mutation | Preserve the cohort and restore one matching verified backup or use verified recovery | Interior byte mutations and bad checksum fixtures |
| Mixed journal, checkpoint, or `SavedData` lineage | Lineage, generation, cursor, or checksum mismatch | Fail closed with bounded diagnostics | Restore one complete matching cohort | Mixed generation rejection and complete restore success |
| Unsupported newer schema | Version exceeds reader support | Preserve bytes and refuse unsafe mutation | Use a compatible build or restore the preupgrade cohort | Newer fixture remains unchanged through repeated load |
| Compatible unknown field | Reader sees an unowned valid field | Preserve it across read, write, migration, reload, and recovery | No recovery required | Old and current writer round trips with sentinel fields |
| Malformed or oversized NBT, JSON, TOML, or binary | Type, bound, nesting, count, path, or registry validation | Reject before publication or mutation and keep last valid state | Preserve the input for diagnosis and use documented non destructive correction | Boundary, one over bound, malformed type, depth, and count corpus |
| Partial write, disk full, access denial, or force failure | Injected I/O result or short write | Do not acknowledge commit or publish partial state | Correct the isolated environment and resume under original identity | Fault at write, file force, directory force, move, verify, and reload |
| Ambiguous player ownership or delivery slots | Receipt and slot proof match neither safe preimage nor safe postimage | Retain custody or enter manual review, do not alter unrelated slots | Reconcile from exact receipt and complete matching cohort | Changed slot, reordered compound, modded item, unrelated NBT, login, logout, reconnect |
| Duplicate request | UUID and fingerprint match durable evidence | Return the original outcome without another effect | None beyond replay | Duplicate at every transition, after restart, and after compaction |
| Same UUID with changed fingerprint | Actor, target, amount, option, quantity, or identity differs | Reject before mutation | Preserve the original outcome | Packet, command, and recovery conflict matrix |
| Concurrent last stock, fill, bid, claim, or reload | Revision, reservation, or lock conflict | One deterministic winner and safe losers | Release reservations or retain recoverable claims | At least two client concurrent workflows and final conservation |
| Provider or player save fails after prepare | Durable prepare exists without confirmed side effect | Fail closed and retain custody, claim, or review state | Retry or compensate once under original identity | Fault before and after provider, inventory, save, receipt, and delivery boundaries |
| Claim store unavailable or module disabled | Claim access or lifecycle matrix | Claims remain durable and reachable independent of module mutation availability | Recover the store or module without expiry or deletion | Freeze, disable, restart, reconnect, collect, and repeat |
| Backup mixes generations | Cohort manifest, lineage, cursor, schema, or checksum mismatch | Refuse restore and new mutations | Select one complete compatible snapshot | Mixed cohort rejection and matching restore proof |
| Conservation mismatch | Per workflow or global equation has a nonzero unexplained delta | Freeze mutation and preserve all evidence | Recover through the journaled procedure under stable identities | Inject a missing and duplicate leg and prove refusal |

## Conservation, Idempotency, and Failure Injection Model

Conservation reports cover player wallets, provider balances, ledger accounts, protected money, mint states, stock, exact items, custody, claims, Server Shops, Player Shops, Auction House, Bazaar, ATM, fees, treasury, and administrative sources or sinks. Fees transfer value. Only explicitly journaled mints and sinks change total supply. Every equation is evaluated before mutation, around each durable transition, after success or rejection, after injected failure, after recovery, after restart, and after replay.

Failure injection covers both sides of every relevant boundary: intent, journal append, journal force, wallet or provider hold, item or cash reservation, inventory mutation, player save, receipt persistence, stock reservation, `COMMIT_DECIDED`, ledger and custody application, claim persistence, exact delivery slot mutation, delivery receipt, terminal receipt, replay publication, checkpoint snapshot and manifest, checkpoint force and move, generation switch, compaction, JSON or TOML candidate write and reload, migration entry and marker, recovery enqueue and handler, compensation, manual review, shutdown drain, file force, directory force, disk full, access denial, and process lock acquisition.

For every cut, the expected owner before and after commit is declared first. The harness preserves the failed bytes and complete cohort, restarts, runs bounded recovery twice, reconnects the affected players, and proves identical terminal results, accessible claims, conserved totals, stable receipts, reconciled slots, and unchanged unrelated state.

## Verification Matrix

| Requirement or task | Static or unit | Integration | Real workflow or runtime | Negative and recovery | Evidence artifact |
|---|---|---|---|---|---|
| Tasks 001 through 003 | Reader, writer, data identity, dependency, and test mapping | Runtime file tree and binding reconciliation | Clean server creates every reachable store | Hidden path, second authority, and unlisted writer search | Frozen inventory and detailed audit |
| Task 004 | Codec round trips, bounds, schema and canonical NBT properties | Load every `SavedData`, journal, checkpoint, receipt, player, block, JSON, and TOML fixture | Start legacy and current fixture cohorts | Old, new, unknown, malformed, truncated, missing registry, bad checksum | Compatibility matrix and fixture hashes |
| Task 005 | Migration and reload tests | Interrupted and repeated migration and last valid reload | Upgrade, restart, reload, and compatible rollback rehearsal | Conflict, invalid input, unsupported newer state, partial migration | Before and after manifests and logs |
| Task 006 | Atomic writer, receipt, journal, and checkpoint fault tests | Complete durability sequence | Crash or forced stop at every cut, then restart twice | Short write, disk, force, move, shutdown, checkpoint, compaction | Crash cut matrix and recovered checksums |
| Task 007 | Lock, revision, scheduler, and repository tests | Concurrent mutation and reload | Local dedicated server and at least two clients | Last stock, fill, bid, claim, reload, second process, shutdown | Concurrency trace and final state |
| Task 008 | Checked arithmetic and conservation properties | Cross domain verifier | Shop, Player Shop, Auction House, Bazaar, ATM, claim, and admin workflows | Overflow, partial failure, fee boundary, missing or duplicate leg | Per workflow and global reports |
| Task 009 | UUID, fingerprint, receipt, replay, and compaction tests | Prepared, committed, terminal, usage, and replay lifecycle | Retry before and after logout, reconnect, restart, and dropped response | Changed payload, duplicate response, ambiguous receipt | Idempotency and replay matrix |
| Task 010 | Deterministic bounded fuzzer and corpus properties | Corrupt every applicable storage family and minimize failures | Dedicated server, player login and logout, multiple clients, restart and reconnect | Truncation, partial writes, versions, unknown fields, checksums, ownership, sentinels, disk and force faults | Seeds, hashes, minimized corpus, semantic diffs, recovery packet |
| Task 011 | Backup manifest and checksum validation | Whole cohort restore and replay | Cold backup, mutate copy, restore, start, reconnect, repeat recovery | Mixed generation and incompatible rollback refusal | Backup and restore packet |
| CORE-REQ-012 | Complete audit row assertions and regressions | Full persistence lineage suite | Dedicated server, multiple clients, backup, restore, reload, restart, reconnect | Corruption, partial write, schema, disk, force, concurrency | Exact merged persistence packet |
| CORE-REQ-014 | Arithmetic, custody, claim, receipt, idempotency, and replay properties | Every authoritative mutation family | Real workflow conservation across restart and reconnect | Every injected partial failure and repeated recovery | Exact merged conservation packet |

### Fixture and Environment Contract

- Use a clean current world with known balances, mints, finite stock, Player Shops, one auction, Bazaar buy and sell orders, claims, history, configuration, and exact starting conservation totals.
- Preserve deterministic fixtures for every supported older `SavedData`, journal, checkpoint, receipt, offer, Player Shop, catalog, Bazaar product, TOML, and replay format. Add unsupported newer fixtures and compatible unknown field sentinels.
- The issue 32 corpus includes clean controls; corrupt, truncated, partial, duplicate, oversized within bounds, unsupported newer, incompatible old, bad checksum, and ambiguous ownership variants; a modded item with nested NBT and capability data; unrelated top level and nested player NBT sentinels; exact receipt and delivery slot expectations; and crash copies around transaction, claim, player save, login, logout, shutdown, restart, and reconnect.
- The bounded fuzzer records generator version, seed, input size, nesting, collection count, operation count, lifecycle count, timeout, expected owner, fixture hash, and minimized result. A minimized FutureShops owned failure becomes a permanent regression fixture.
- Use the 64 GB workstation for the isolated server and independent client processes by default. Node1 may run the temporary isolated server, while clients remain independent and all world, config, revision, artifact, and evidence identities remain pinned.
- Production worlds, live economies, reporter files, and unique player state are never test targets.

### Command and Rerun Order

For every changed support line, retain exact revision, Java, command, duration, exit status, and decisive result:

1. Confirm no formatter or static analysis task exists, or run it if present.
2. Run focused store, codec, migration, atomicity, corruption, receipt, recovery, and invariant tests.
3. Run `bash ./gradlew test` on Forge with Java 17. Run the exact NeoForge test task with Java 21 if that line changed.
4. Run `bash ./gradlew runData` when providers, generated resources, examples, or data contracts changed.
5. Run `bash ./gradlew runGameTestServer` for applicable persistence or recovery GameTests.
6. Run `bash ./gradlew build`.
7. Run a dedicated server through clean startup, transaction preparation, clean shutdown, crash recovery, backup, restore, reload, restart, and second restart.
8. Run `bash ./gradlew runClient` for player state, synchronization, claim, receipt, recovery, configuration, or client visible changes.
9. Run at least two independent clients for every networked, concurrent, login, logout, disconnect, reconnect, claim, receipt, and delivery slot row.
10. Inspect the JAR, complete diff, and status for schemas, metadata, dependencies, secrets, private data, logs, worlds, caches, absolute paths, debug output, generated drift, and unrelated edits.
11. After pull request merge, fetch `origin/1.20.1` and rerun focused tests, full tests, applicable data and GameTests, build, server, client, multiple client lifecycle, backup, restore, JAR, and diff gates at the exact merge commit before tagging.

A failed, skipped, flaky, stale, timed out, or unavailable mandatory result is not a pass. Repair the local environment or product, rerun the affected dependency chain, and retain the failed evidence. No lower fidelity scan substitutes for real runtime, recovery, or multiple client proof.

## Documentation, Operations, and Release

- Maintain the detailed persistence and database audit and link it from `DOCUMENTATION.md` and `docs/README.md`.
- Update `docs/backup-restore.md` with the verified backup cohort, stop state, journal and checkpoint lineage, receipts, player data coupling, configuration, restore order, compatibility, expected startup proof, refusal conditions, and non destructive recovery.
- Update focused configuration, market, ATM, shop, migration, test, and troubleshooting guides when their persistence, reload, claim, receipt, or recovery behavior changes. Update `README.md` only for user or operator visible changes.
- Document exact schemas, read versions, migrations, rollback boundaries, retained legacy state, unknown field behavior, unsupported newer behavior, and verified fixture identities.
- Recovery instructions may verify, replay, resume, compensate, quarantine, create or deliver claims, or restore one matching cohort. They may not delete, move aside one file as a fix, force clear maintenance, reset markers, edit custody, or mix generations.
- GitHub text follows repository lowercase rules. Every finding records sanitized reproduction, affected line, acceptance criteria, migration and rollback effects, and exact merged evidence.
- The Phase 005 pull request targets `1.20.1`. Any separately proven NeoForge repair uses its own line specific pull request. Required checks and one private independent review if the optional private review capability exists complete before merge.
- After exact merged verification, create and push the signed annotated tag `phase-005-persistence-recovery` on the Forge merge commit. This is an internal phase marker, not a product release.
- Do not create a GitHub Release, upload to CurseForge or Modrinth, announce a release, declare stable status, or publish a product version tag.

## Risks and Evidence Invalidation

| Risk | Prevention | Detection | Recovery | Evidence invalidated | Reverification |
|---|---|---|---|---|---|
| Inventory omits a hidden writer or store | Reader, writer, dependency, runtime tree, and independent reconciliation | New file, data name, save hook, cache flush, or adapter appears | Add rows and stop clean audit claim | Inventory, lineage, schemas, and downstream proof | Tasks 001 through 003 and every affected task |
| Issue 32 historical gate is reactivated | Explicit historical only rule and dependency scan | `EXT-003` appears as an executable dependency, blocker, request, or transition alternative | Remove the false gate and rerun local successor evidence | Corpus, exit, and handoff evidence | Tasks 010, 011, 014, and 016 |
| Corpus is nondeterministic or unbounded | Stable generator, seeds, hashes, limits, minimization, and timeouts | Repeated run differs or exceeds a bound | Fix harness and regenerate from clean controls | All corpus derived evidence | Task 010 and downstream recovery checks |
| Mixed snapshot damages lineage | Whole cohort manifest and cold restore rule | Lineage, cursor, checksum, receipt, or conservation mismatch | Preserve both and restore one matching cohort | Restore, restart, recovery, and conservation | Tasks 006, 008 through 011, and 014 |
| Migration rewrites unknown or unrelated state | Owned field map, old bytes, unknown sentinels, and last valid snapshot | Unexpected semantic diff | Stop and restore complete premigration copy | Schema, migration, player, and compatibility | Tasks 004, 005, 010, 011, and 014 |
| Crash cut yields both or neither owner | Durable prepare and commit boundary with receipts | Custody, claim, slot, or conservation mismatch | Freeze mutation and recover under original UUID | Atomicity, replay, recovery, and workflows | Tasks 006, 008 through 011, and 014 |
| Concurrent writer bypasses authority | Logical server ownership and explicit locks | Race, stale revision bypass, or second process write | Reject, release or retain custody, preserve evidence | Concurrency and conservation | Tasks 007 through 010 and runtime reruns |
| Sensitive player data enters evidence | Synthetic fixtures, minimization, redaction, and private routing | Privacy scan or review | Remove unsafe evidence and assess exposure | Evidence, issues, docs, and packet | Recreate sanitized evidence and rerun review |
| Repair occurs before issue filing | Task 012 gate and timestamp audit | Diff or commit predates canonical issue | Stop and create or link issue before further repair | Traceability and completion | Task 012 and affected finding packet |
| Platform durability differs | Explicit file and directory durability outcomes | Force or move behavior differs by platform | Use documented fail closed fallback and retain result | Atomicity and recovery | Targeted platform fault, restart, and restore |
| Late repair makes evidence stale | Dependency map and exact revision manifests | Evidence revision differs from merged head | Rerun from earliest invalidated gate | All reachable results | Focused proof then Tasks 014 through 016 |
| Local runtime capacity fails | Pinned process, memory, host, world, and artifact contract | Launch, memory, port, display, or timing failure | Repair or reschedule locally, or move only server to node1 | Runtime and lifecycle evidence | Full affected local matrix with independent clients |

## Phase Completion Packet

The packet is retained outside this protected plan file and contains:

1. Exact starting branch heads, Phase 005 branch, signed implementation commits, pull request, reviewed head, merge commit, verified `origin/1.20.1` head, and any separately affected NeoForge ancestry.
2. Complete persistence inventory and database audit with all mandatory row fields and bounded proof about external or embedded databases.
3. State ownership, journal lineage, checkpoint, replay, transaction, receipt, exact delivery slot, custody, ledger, stock, mint, claim, player data, market, shop, and backup cohort maps.
4. Schema and migration matrix, fixture catalog, seeds, hashes, old, current, newer, unknown field, malformed, bad checksum, interrupted, reload, and rollback results.
5. Mandatory issue 32 local corpus with bounded fuzzer configuration, minimized cases, ambiguous ownership controls, modded item and unrelated player NBT sentinels, field and semantic diffs, login, logout, restart, reconnect, receipts, delivery slots, claims, and repeated non destructive recovery.
6. Atomicity, crash cut, disk, access, short write, force, directory force, move, checkpoint, compaction, shutdown, concurrency, backup, restore, and repeated recovery matrices.
7. Per workflow and global conservation reports and the complete UUID, fingerprint, idempotency, receipt, and replay matrix.
8. For every finding, duplicate search, canonical issue or private advisory, failing evidence, regression, repair, review, pull request, merge, checks, and post merge exact revision proof, with issue creation predating repair.
9. Complete branch and merged revision commands for focused tests, `test`, applicable `runData`, applicable `runGameTestServer`, `build`, dedicated server, client, at least two clients, login, logout, restart, reconnect, reload, backup, restore, JAR, and diff inspection.
10. Updated technical, audit, migration, backup, restore, configuration, test, recovery, and troubleshooting documentation with source, link, path, command, and destructive advice review.
11. Evidence invalidation log and final clean audit showing no unclassified finding, known phase defect, stale result, destructive recovery path, or unresolved mandatory gate.
12. GitHub proof that the Phase 005 pull request merged into `1.20.1`, required checks passed, `origin/1.20.1` contains the merge, and every separately required support line repair is merged and verified.
13. Signed annotated tag `phase-005-persistence-recovery`, exact target, verified EnVisione signature, and remote tag presence.
14. Downstream handoff naming every stable interface, schema, readiness state, failure code, persistence guarantee, recovery entry point, and exact merged revision consumed by Phase 006.

Any failed, skipped, flaky, stale, unavailable, or lower fidelity mandatory evidence keeps Phase 005 open. The packet has no substitute completion state.

## Next Transition

Transition only to `CORE-PHASE-006` after the full Phase 005 exit passes. Fetch the authoritative remotes and prove that `origin/1.20.1` contains the Phase 005 pull request merge, every required check and exact merged revision rerun is green, the signed annotated tag `phase-005-persistence-recovery` targets that merge and verifies, any separately affected NeoForge repair is also merged and verified, all issues and documentation are synchronized, and the final audit contains no unresolved mandatory gate.

Then read [Phase 006](plan-phase-006.md) through EOF and validate its entry against the exact inventory, lineage, schema, corpus, conservation, recovery, merge, and tag evidence. Create the Phase 006 branch only from the latest approved merged `origin/1.20.1` head. Do not stack it on Phase 005, start from an open or queued pull request, use a local approximation of the merge, or transition with unresolved evidence.

Any later change to a Phase 005 store, schema, migration, transaction boundary, receipt, delivery slot, claim, recovery path, or conservation rule invalidates the affected handoff rows. Rerun those rows at the new exact revision before Phase 006 may rely on them. No destructive recovery or release publication is authorized by this transition.
