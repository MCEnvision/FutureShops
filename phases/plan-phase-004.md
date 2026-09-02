# Phase 004 Execution Plan

> **Plan ID:** PLAN-PHASE-004
> **Phase ID:** CORE-PHASE-004
> **Owner:** Security review
> **Classification:** MANDATORY
> **Master plan:** [plan.md](../plan.md)
> **Phase sequence:** 004 of 007

## Purpose and Ownership

This phase closes the security, privacy, command, permission, dependency, and packaging risks that are visible at every FutureShops entry point. It owns the complete execution blueprint for `CORE-REQ-010` and `CORE-REQ-011`. It also consumes the continuous duplicate-before-repair, economic invariant, branch isolation, documentation, and evidence contracts established by `CORE-REQ-009`, `CORE-REQ-014`, `CORE-REQ-016`, and `CORE-REQ-017`; those requirements remain canonically owned by the master and their assigned phases.

The phase begins only after the complete `CORE-PHASE-003` Forge change is merged, verified from `origin/1.20.1`, tagged, and closed with its issue and evidence packet. It consumes that merged revision as one indivisible upstream contract and audits the Forge 1.20.1 line in full. It separately inventories and reviews the exact approved NeoForge 1.21.1 line wherever the same risk class, command, packet, path, dependency, packaging rule, or trust boundary exists. A finding is repaired only on a support line proven affected. No Forge implementation is copied or merged into NeoForge, and no NeoForge implementation is copied or merged into Forge, without line-specific proof and verification.

The master plan owns product scope, the frozen phase sequence, branch and version policy, rolling defect policy, completion authority, and publication exclusions. This file owns only Phase 004 ordering, matrices, tests, issue routing, repair gates, evidence, and the handoff to `CORE-PHASE-005`.

## Evidence-Based Entry State

| Evidence class | Area | Finding | Source or command | Freshness condition |
|---|---|---|---|---|
| OBSERVED | Forge command registration | `ModCommandEvents` registers sixteen command providers. The currently visible roots and aliases include `/shop`, `/ah`, `/auctionhouse`, `/bz`, `/bazaar`, `/balance`, `/bal`, `/pay`, `/baltop`, `/withdraw`, `/atm`, `/deposit`, `/shopadmin`, `/marketadmin`, `/madmin`, `/claims`, `/claimall`, `/escrow`, `/link`, `/franchise`, `/shopdesc`, and `/sellall`. | `src/main/java/com/enviouse/futureshops/command/ModCommandEvents.java` and command registration sources in SRC-011 | Invalid after any command registration, alias, argument, redirect, or requirement predicate changes, including Phase 003 integration |
| OBSERVED | Forge administrative trees | `/shopadmin` is assembled from `ShopAdminCommand` and `AdminModeCommand`; its visible families include reload, promotions, coin audit, admin shop editing, item editing, wizard response, balance mutation, player view, categories, limits, and admin mode. `/marketadmin` and `/madmin` include status, lifecycle control, audit, recovery, maintenance, catalog validation and quarantine, transaction inspection, sweep, auction cancellation, and Bazaar product state. | `src/main/java/com/enviouse/futureshops/command/ShopAdminCommand.java`, `AdminModeCommand.java`, and `MarketAdminCommand.java` | Invalid after any tree, service dispatch, permission, confirmation, or Phase 003 bulk workflow change |
| OBSERVED | Forge command permissions | Command predicates currently combine operator levels, sender type, Forge permission nodes, ownership checks, and service-layer validation. Market permission nodes cover Auction House use, create, bid, buy, claim, and admin; Bazaar use, order, instant, claim, and admin; and escrow claim and admin. | `src/main/java/com/enviouse/futureshops/server/market/MarketPermissions.java`, command sources, configuration, and SRC-011 | Invalid after permission node, fallback operator level, source predicate, ownership, or configuration changes |
| OBSERVED | Forge network surface | `ShopPackets` uses a versioned Forge `SimpleChannel`, explicit directions, main-thread consumers, and a large C2S/S2C packet set covering shops, admin editing, player shops, balances, ATM, markets, Auction House, Bazaar, claims, sessions, profiles, settlements, and bulk sales. Protocol identity is append-sensitive. | `src/main/java/com/enviouse/futureshops/network/ShopPackets.java` and `src/main/java/com/enviouse/futureshops/network/packets/` | Invalid after packet order, protocol version, codec, handler, or Phase 003 packet changes |
| OBSERVED | Forge path and parser surfaces | FutureShops reads and writes bounded configuration, catalog, Bazaar product, escrow, journal, backup, and migration paths. Existing code includes normalized config roots, symbolic-link checks, file count and byte bounds, known-field JSON validation, canonical NBT handling, atomic replacement, and last-known-good behavior that must be proven across every path rather than assumed globally. | `FutureShopsConfigPaths`, `BazaarProductDefinitionLoader`, `ShopDefinitionLoader`, `AdminShopConfigWriter`, escrow journal and codec packages in SRC-011 | Invalid after path construction, storage root, file operation, parser, schema, or migration changes |
| OBSERVED | Forge dependency and artifact surface | Forge uses Forge 47.4.20, a local GeckoLib Forge 4.8.3 JAR resolved through `flatDir`, mclib 20, Mixin processor 0.8.5, and JUnit 6.1.2. Mod metadata declares GeckoLib as mandatory. | `build.gradle`, `gradle.properties`, `libs/`, and `src/main/resources/META-INF/mods.toml` | Invalid after dependency declarations, local JAR bytes, repositories, metadata, lock or verification state, or packaging logic changes |
| OBSERVED | NeoForge differential surface | The `1.21.1` line has a distinct package and loader implementation, a smaller command and packet inventory at intake, Java 21, NeoForge 21.1.233, ModDevGradle 2.0.141, and GeckoLib NeoForge 4.8.4. It requires independent review rather than assumptions from the Forge line. | `origin/1.21.1` tree and build metadata in SRC-009 and SRC-011 | Invalid when the approved `1.21.1` head, dependency graph, command set, packet set, or packaging changes |
| OBSERVED | Existing regression surfaces | Tests already cover selected command language keys and logic, packet round trips and bounds, market permissions, path safety, dependency baselines, money protection, replay, escrow, and transaction invariants. Coverage is evidence input, not proof that the complete matrices are closed. | `src/test/` in SRC-011 | Invalid after affected implementation, fixtures, test exclusions, or exact support-line revision changes |
| OBSERVED | Local runtime capacity | The 64 GB workstation is the normal isolated dedicated-server and multiple-client environment. The 96 GB node1 host is the authorized temporary isolated-server fallback when it improves capacity or repeatability. | SRC-014 and DEC-007 | Invalid only by a verified capacity or access change; repair or reschedule the local harness without lowering required runtime fidelity |
| UNKNOWN | Phase 004 findings | No clean audit result is asserted at authoring time. Every suspected weakness must be reproduced or disproved and classified before it becomes a verified finding. | DEC-006 and CORE-REQ-010 through CORE-REQ-011 | Becomes known only through exact-revision Phase 004 evidence |
| VERIFIED | Confidentiality rule | Exploit-enabling details and private player evidence must not be disclosed publicly before safe remediation. | CORE-REQ-009, CORE-REQ-010, DEC-002, DEC-006, and SRC-013 | Never expires within this plan |

## Scope Boundaries

### Included Scope

- `CORE-REQ-010`: inventory, threat model, test, repair, and close every supported security and privacy boundary, including leaks, backdoors, packets, commands, secrets, paths, deserialization, permissions, duplication, replay, dependency resolution, and packaged artifacts.
- `CORE-REQ-011`: inventory every registered command and executable leaf and close its syntax, argument bounds, source type, permission, identity, target, confirmation, authority, idempotency, feedback, logging, recovery, localization, and dedicated-server behavior.
- The Phase 004 application of `CORE-REQ-009`: search for duplicates and create or enrich the canonical GitHub record before any verified finding is repaired. Sensitive findings use the private vulnerability process.
- The Phase 004 preservation duties of `CORE-REQ-014`: no security or command repair may weaken logical-server authority, checked minor-unit arithmetic, stable request identity, custody, claims, recovery lineage, readiness, compatibility, or conservation.
- The Phase 004 line-isolation duties of `CORE-REQ-016`: audit both supported lines, repair only proven affected lines, and rerun each affected line independently.
- Documentation and operational changes caused by verified Phase 004 repairs, as required by `CORE-REQ-017` and the master documentation gates.

### Explicit Exclusions

- `CORE-REQ-012` persistence, migration, integrity, backup, restore, corruption, and full recovery closure belongs to `CORE-PHASE-005`. Phase 004 reviews persistence codecs and paths only as security, privacy, deserialization, authorization, or duplication boundaries and hands the complete inventory forward.
- `CORE-REQ-013` full cross-component backend and lifecycle closure belongs to `CORE-PHASE-006`. Phase 004 exercises only integrations necessary to prove command, permission, packet, path, dependency, and trust-boundary behavior.
- `CORE-REQ-015` through `CORE-REQ-020` final exact-candidate verification, repeated clean audits, final issue closure, documentation reconciliation, and artifact preparation belong to `CORE-PHASE-007`. Phase 004 still produces exact-revision evidence and closes its own internally complete findings after merge.
- `FUT-001` and `FUT-002`: release publication, uploads, announcements, public tags, and stable designation remain excluded.
- `FUT-003` through `FUT-005`: unrelated features, unbounded bulk selection, distributed market state, and unsupported external-storage listing behavior remain excluded.
- `NG-003` and `NG-004`: destructive data recovery and any weakening of authority, readiness, escrow, idempotency, claims, permissions, or dedicated-server safety are forbidden.
- Dependency modernization is not an audit repair by itself. Pinned platform changes and unrelated Dependabot work remain separate unless a verified vulnerability cannot be safely repaired without a master-plan revision.

## Requirement and Source Traceability

| Contract or evidence ID | Phase interpretation | Owned tasks | Acceptance proof |
|---|---|---|---|
| CORE-REQ-010 | Close every known security and privacy defect across trust, packet, command, secret, path, deserialization, permission, duplication, dependency, and packaging boundaries | P004-TASK-002, P004-TASK-003, P004-TASK-005 through P004-TASK-015 | Complete clean threat, packet, command, path, serialization, permission, duplication, privacy, dependency, and JAR matrices at exact merged revisions |
| CORE-REQ-011 | Close every administrative command and permission path from dispatcher registration through authoritative service, output, audit, and recovery | P004-TASK-004, P004-TASK-005, P004-TASK-011 through P004-TASK-015 | Exhaustive command tree and behavior matrix plus dedicated-server proof for every executable leaf and negative path |
| CORE-REQ-009, DEC-002, EXT-005 | Roll scope forward for verified repository-owned defects and create the deduplicated GitHub record before repair | P004-TASK-001, P004-TASK-011, P004-TASK-014, P004-TASK-015 | Duplicate queries, issue or private advisory timestamp, repair and merge links, final classified finding register |
| CORE-REQ-014 | Preserve economic, custody, claims, authority, idempotency, readiness, and no-loss invariants while hardening entry points | P004-TASK-003, P004-TASK-009, P004-TASK-012, P004-TASK-013 | Conservation, replay, retry, concurrency, restart, and recovery evidence for every affected mutation |
| CORE-REQ-016 | Keep Forge and NeoForge findings, changes, versions, APIs, and verification isolated | P004-TASK-001, P004-TASK-002, P004-TASK-010, P004-TASK-012 through P004-TASK-015 | Separate ancestry, dependency, build, JAR, pull request, merge, and clean-matrix evidence |
| SRC-001 | The owner requires every active and audit-discovered defect to converge before candidate completion | All tasks | No unclassified finding and no silent scope reduction |
| SRC-002 | Legacy 3.0 escrow, market, command, permission, recovery, and administration invariants remain binding | P004-TASK-002 through P004-TASK-010, P004-TASK-012 | Source trace and regression coverage preserve the mapped legacy contracts without editing the legacy plan |
| SRC-003 | Legacy 3.1 normalized offers, editor, networking, cart, and atomic transaction boundaries remain binding | P004-TASK-002, P004-TASK-003, P004-TASK-006, P004-TASK-009, P004-TASK-012 | Packet, permission, stale-state, replay, and conservation evidence preserves implemented behavior |
| SRC-009 | Repository identity, support lines, toolchains, versions, and branch boundaries define the audited targets | P004-TASK-001, P004-TASK-010, P004-TASK-013 through P004-TASK-015 | Exact revision, toolchain, metadata, dependency, and ancestry records |
| SRC-010 | Current documentation supplies the command and operator baseline that must be reconciled with verified behavior | P004-TASK-004, P004-TASK-005, P004-TASK-014 | Source-to-document comparison and updated command, permission, and recovery references |
| SRC-011 | Current source, tests, CI, and line-specific branch evidence supply the inventory and regression baseline | P004-TASK-001 through P004-TASK-010, P004-TASK-013, P004-TASK-015 | Exact-revision inventories and rerun results, never historical success alone |
| SRC-013, DEC-006, DEC-007 | Repository rules require the full mandatory audits, non-destructive recovery, security-safe evidence, normal local server and client verification, verification order, and merge discipline | All tasks | Completion packet proves every gate; missing mandatory evidence keeps the phase open and has no lower-fidelity substitute |

## Phase Contract

### CORE-PHASE-004 — Security, Privacy, Command, and Permission Closure

**Objective:** Produce complete line-specific entry-point, trust-boundary, command, permission, privacy, path, codec, dependency, and JAR matrices; route every verified finding through a deduplicated record before repair; merge every in-scope repair on the affected support line; and rerun the exact-revision Phase 004 matrices with no known finding remaining.
**Owner:** Security review
**Dependencies:** CORE-PHASE-003
**Canonical requirements:** CORE-REQ-010, CORE-REQ-011
**Documentation and release impact:** Update command, permission, security, configuration, recovery, compatibility, dependency, and operator documentation only for verified behavior. Record candidate implications without publishing a release.
**Next transition:** CORE-PHASE-005

**Entry criteria**

- `CORE-PHASE-003` has passed its complete integration gate, its Forge pull request is merged, `origin/1.20.1` contains the merge commit, the signed Phase 003 tag verifies against that commit, issue 33 is closed with exact evidence, and no partial Phase 003 branch or pre-merge result is consumed.
- The active Forge Phase 004 branch starts from that exact approved `origin/1.20.1` revision. The NeoForge audit records the exact approved `origin/1.21.1` revision and uses a separate work branch only if an independently proven NeoForge repair is required.
- A read-only GitHub preflight confirms available `EXT-005`, authoritative remote identity, issue and advisory visibility, review and check state, open security and dependency alerts, and no unresolved earlier-phase change that invalidates the integrated entry point.
- The runtime command tree, command providers, packet registrations, event handlers, menus, screens that initiate C2S work, configuration and data loaders, codecs, saved-data and journal readers, compatibility adapters, filesystem paths, service entry points, dependencies, and JAR inputs can all be enumerated at the exact entry revisions.
- The Phase 003 bulk listing command and packet surface, if any, is included in the frozen inventory rather than inferred from the authoring-time evidence above.
- Sanitized fixtures, isolated server worlds, non-privileged and privileged test identities, permission-plugin and operator-level configurations, malformed payload generators, safe path fixtures, and artifact inspection tools are available without using real private player data. The 64 GB workstation supplies the normal dedicated-server and multiple-client environment, with node1 available only as the authorized temporary isolated-server fallback under DEC-007.

**Implementation scope**

- CORE-REQ-010 and CORE-REQ-011 define the complete mandatory implementation boundary detailed below.

**Detailed implementation scope**

- Build and freeze complete inventories and a scoped threat model before evaluating findings.
- Prove command registration and behavior from both dispatcher snapshots and source-to-service tracing.
- Prove all server-bound payloads and other untrusted entry points reject invalid authority, identity, direction, size, state, and replay before mutation.
- Prove path, parser, codec, NBT, JSON, TOML, dependency, logging, privacy, and packaging boundaries.
- Classify, deduplicate, privately route when sensitive, repair, review, merge, and reverify every verified Phase 004 defect.
- Produce a security-safe completion packet and a complete handoff inventory for Phase 005.

**Execution order**

- `P004-TASK-001` through `P004-TASK-015` execute the CORE-PHASE-004 task sequence in order.

**Detailed task sequence**

1. `P004-TASK-001` freezes exact support-line revisions, Phase 003 integration state, GitHub security capability, artifact inputs, and the evidence directory structure.
2. `P004-TASK-002` generates the complete entry-point and trust-boundary inventory for both lines and fails closed on any unclassified entry point.
3. `P004-TASK-003` writes the scoped threat model, asset inventory, attacker capabilities, privacy classes, abuse cases, and required controls.
4. `P004-TASK-004` generates the complete command tree and command behavior matrix, including aliases, merged roots, executable leaves, suggestions, and service dispatch.
5. `P004-TASK-005` audits command registration, syntax, sources, permissions, identities, targets, confirmations, authority, idempotency, outputs, logging, and recovery.
6. `P004-TASK-006` audits every packet and non-command mutation entry point for direction, decoding bounds, correlation, identity, state, permission, rate, replay, threading, and response safety.
7. `P004-TASK-007` audits path construction, symlink handling, atomic replacement, parser and deserializer bounds, schemas, unknown fields, NBT, JSON, TOML, and failure disclosure.
8. `P004-TASK-008` audits secrets, logs, history, telemetry-like records, issue evidence, crash output, player data, NBT, UUIDs, balances, transaction references, and retention boundaries.
9. `P004-TASK-009` audits duplication, replay, concurrent command and packet execution, stale state, partial failure, and conservation at every Phase 004 mutation boundary.
10. `P004-TASK-010` audits dependency provenance, repository restrictions, local JAR integrity, optional integration isolation, mod metadata, classpath contents, known advisories, and final JAR contents on each line.
11. `P004-TASK-011` classifies every candidate finding, records duplicate searches, and creates or enriches the correct public issue or private security advisory before any repair begins.
12. `P004-TASK-012` adds failing regressions where feasible, repairs verified findings on separate affected-line branches, preserves compatibility and invariants, and completes focused review.
13. `P004-TASK-013` runs focused and full deterministic verification, dedicated-server command exercises, client and controlled-multiplayer checks for every affected client or network path, and exact JAR inspection at each repaired line revision.
14. `P004-TASK-014` updates tracked documentation and sanitized finding records, commits and pushes the verified changes, integrates every affected-line repair through the required pull request, and verifies the exact remote merge revision.
15. `P004-TASK-015` reruns the complete security, privacy, dependency, packet, command, path, codec, permission, duplication, and JAR matrices at the exact merged revisions, closes the remediated findings, creates and pushes the signed Phase 004 tag on the verified Forge merge, and assembles the completion packet.

**Required evidence**

- Exact support-line revisions, branch ancestry, toolchains, dependency graphs, source inventories, and post-merge revisions.
- A complete scoped threat model and trust-boundary matrix for Forge and NeoForge.
- Complete command dispatcher snapshots and source-to-service behavior matrices, with every executable leaf classified.
- Packet registration, codec, handler, permission, replay, and mutation matrices for every C2S packet and relevant S2C privacy boundary.
- Path, serialization, deserialization, parser, privacy, secret, dependency, duplication, and artifact matrices.
- Duplicate-search records and canonical public issue or confidential security-advisory links created before each repair.
- Before-and-after regression results, line-specific commits and pull requests, required green checks, and one private independent review per ready phase pull request if the optional capability exists.
- Dedicated-server command transcripts for console, supported command block, authorized player, unauthorized player, malformed input, stale state, recovery state, repeated confirmation, and offline target behavior.
- Test, build, runtime, client and controlled-multiplayer results for every affected client or network path, JAR listing, metadata, dependency, secret-scan, and full-diff results at each exact merged revision.
- Documentation and operator guidance tied to verified behavior without exploit-enabling detail or private data.

**Exit criteria**

- Every entry point, executable command leaf, permission path, packet, path, decoder, dependency, packaged component, and privacy-bearing output is represented in a frozen matrix for the exact merged Forge revision and the exact approved NeoForge revision, or its exact merged revision when NeoForge repair was required.
- Every suspected finding is classified as disproven, accepted design with evidence, duplicate, verified defect, confidential verified defect, or excluded scope. There is no unclassified concern.
- Every verified finding has a duplicate search and canonical GitHub record before repair. Sensitive findings remain confidential until safe remediation and disclosure review.
- Every verified in-scope finding is repaired on every independently proven affected support line, merged through the correct pull request, and connected to failing and passing evidence. The Forge repair set merges through the Phase 004 pull request into `1.20.1`; any independently required NeoForge repair uses a separate `1.21.1` pull request and completes before phase closure.
- The command matrix passes for all required source, permission, target, confirmation, stale, retry, and recovery combinations. Diagnostic commands are non-mutating. Destructive or irreversible administrative paths have explicit confirmation and durable audit context.
- The threat, packet, command, path, serialization, permission, duplication, privacy, dependency, and JAR matrices rerun clean at the exact merged revisions. Fresh fetch and ancestry proof bind the Forge packet to `origin/1.20.1`, and the signed annotated Phase 004 tag points to that verified merge commit.
- No repair weakens the invariants in `CORE-REQ-014`, deletes data, exposes private evidence, changes pinned platform boundaries without approval, or crosses support lines implicitly.
- Documentation accurately describes the merged command, permission, dependency, and recovery behavior without publishing exploit details.
- No known mandatory phase-owned defect remains. `CORE-PHASE-005` has an exact, immutable entry revision and the complete persistence-boundary handoff.

## Inputs and Upstream Contracts

| Input or contract | Provider | Required state | Validation | Failure behavior |
|---|---|---|---|---|
| Integrated Forge revision | `CORE-PHASE-003` | Phase 003 is merged into `1.20.1`; catalog and bulk-listing entry points are stable | Git ancestry, merged pull request, exact revision, green required checks | Stop Phase 004. Do not audit or branch from an unmerged or stacked revision |
| Approved NeoForge revision | `CORE-PHASE-001` and branch governance | Issue 22 integration is complete and the current approved `1.21.1` head is explicit | Git ancestry, merge and tag state, metadata, and prior evidence | Refresh the approved revision before audit. Do not import Forge changes or consume a partial upstream state |
| Rolling defect contract | `CORE-REQ-009`, DEC-002, EXT-005 | Search before creation; canonical record exists before repair; final inventory retains every finding | GitHub duplicate queries, issue or advisory link, timestamps, affected line | Stop repair. Create or enrich the record first. Keep sensitive details confidential |
| Economic and recovery invariants | `CORE-REQ-014` | Server authority, minor units, checked arithmetic, UUID idempotency, custody, claims, and no-loss behavior remain mandatory | Existing tests, source trace, conservation checks, runtime evidence | Reject any repair that weakens an invariant; route material conflict as `PLAN_REVISION_REQUIRED` |
| Support-line isolation | `CORE-REQ-016`, DEC-005 | Forge and NeoForge remain independent in branches, APIs, versions, and migrations | Branch, diff, build metadata, package, loader, and JAR checks | Revert or separate contaminated change through reviewed line-specific work |
| Repository evidence | SRC-009, SRC-010, SRC-011, SRC-013 | Build, code, tests, docs, and repository rules reflect the exact audited revision | Fresh source inventory and read-only preflight | Treat stale evidence as invalid and regenerate it |
| Security disclosure boundary | CORE-REQ-009, CORE-REQ-010, SRC-013 | No public exploit detail or private player evidence before safe remediation | Evidence-content review and transport review | Stop publication, move details to a private advisory, sanitize retained evidence, and assess credential rotation if relevant |

## Outputs and Downstream Contracts

| Output or contract | Consumer | Guaranteed state | Compatibility or versioning | Evidence |
|---|---|---|---|---|
| Exact Phase 004 merged revisions | `CORE-PHASE-005` and later phases | Every Phase 004 repair is merged on the correct support line; no stacked branch remains | Forge stays on `1.20.1` toward `3.0.0-beta.2`; NeoForge stays on `1.21.1` toward `2.2.1` | Git ancestry, pull requests, checks, merge commits |
| Complete entry-point and trust-boundary inventory | Phases 005 through 007 | Every command, packet, event, config and file loader, codec, service entry, compatibility adapter, dependency, and package input has an owner and risk classification | Inventory is revision-bound and regenerated after affected changes | Machine-readable or tabular inventory plus source trace |
| Command inventory and behavior matrix | Operators, documentation, Phases 006 and 007 | Every root, alias, executable leaf, argument, permission, sender, target, confirmation, mutation, feedback, audit, recovery, and test is explicit | Stable literals and permission nodes remain unchanged unless a documented compatible repair requires change | Dispatcher snapshot, source mapping, dedicated-server transcript, tests |
| Scoped threat model | Phases 005 through 007 | Assets, attackers, trust boundaries, abuse cases, controls, residual risks, and evidence are explicit for each support line | Reopened when an entry point, trust boundary, dependency, or data class changes | Threat and trust-boundary matrices with exact revision |
| Confidential and public finding records | Repository closure governance | Every verified finding was recorded before repair and is safely classified | Sensitive details stay in a private advisory until disclosure is safe | Duplicate searches, advisory or issue links, disclosure decision |
| Clean Phase 004 matrices | `CORE-PHASE-005`, `CORE-PHASE-007` | Threat, packet, command, path, codec, permission, duplication, privacy, dependency, and JAR results are clean at exact revisions | Any affected later change invalidates named rows and requires rerun | Timestamped exact-revision evidence packet |
| Signed Phase 004 integration tag | Sequential phase governance | The signed annotated tag `phase-004-security-command-audit` identifies the exact verified Forge merge commit | Internal phase evidence only; it is not a product release or publication | Tag object, EnVisione signature verification, exact target, and remote presence |
| Persistence security handoff | `CORE-PHASE-005` | Every discovered persistence path, codec, privacy class, authority boundary, and security-relevant failure is listed without claiming persistence closure | Phase 005 owns complete schema, migration, recovery, and conservation closure | Handoff matrix linked to Phase 004 source evidence |
| Verified documentation updates | Operators and Phases 006 through 007 | Commands, permissions, configuration, recovery, dependencies, and security guidance describe only merged behavior | Documentation follows each affected support line and does not expose confidential details | Documentation diff, link checks, source-to-doc review |

## Threat Model and Trust-Boundary Contract

### Protected Assets

| Asset class | Examples | Required security property |
|---|---|---|
| Value | Wallet minor units, physical currency, stock, listed items, custody, claims, bids, orders, settlements | Server authority, conservation, checked arithmetic, exactly-once effect, recoverability |
| Identity and authorization | Player UUID, command source, target profile, shop owner, franchise role, operator level, Forge permission node, session and route identity | Authenticated source binding, explicit authorization, least privilege, no confused deputy |
| Persistent integrity | Catalogs, configuration, player and world state, journals, checkpoints, ledgers, receipts, replay records | Bounded parsing, compatible schema, atomic mutation, no traversal, no silent discard |
| Availability | Server startup, command dispatcher, module readiness, market routes, recovery and claims | Fail closed without crash loops, unbounded work, deadlock, or inaccessible claims |
| Privacy | Balances, transaction history, NBT, inventories, UUID relationships, world paths, operator reasons, logs and issue attachments | Minimum disclosure, bounded logging, correct recipient, sanitized retention |
| Supply chain | Gradle plugins, Forge or NeoForge, GeckoLib, mclib, Mixin, local JARs, repositories, generated resources | Pinned provenance, expected metadata, no undeclared or vulnerable packaged code |

### Actors and Capabilities

- An unauthenticated or incompatible network peer during handshake.
- A connected unprivileged player able to send arbitrary packet bytes rather than only packets produced by the official client.
- An authorized player attempting to exceed ownership, module, session, route, listing, stock, target, or replay boundaries.
- An operator at permission levels 2, 3, or 4 with only the permissions explicitly granted by configuration or a permission provider.
- The dedicated-server console and, where Minecraft supports the leaf, a command block or non-player command source.
- A malicious or malformed local configuration, JSON, TOML, NBT, catalog, backup, symlink, dependency JAR, or restored world fixture supplied to an isolated test environment.
- A curious reader of public logs, issues, pull requests, CI artifacts, JARs, documentation, and crash reports.
- Accidental concurrency, duplicate delivery, disconnect, retry, restart, stale client state, and partial I/O that can create the same outcome as deliberate abuse.

### Boundary Matrix

| Boundary | Untrusted input | Required validation before effect | Authority owner | Required negative proof |
|---|---|---|---|---|
| Brigadier command dispatcher | Literal, alias, arguments, sender, target, suggestions | Tree registration, bounds, sender type, permission predicate, target identity, confirmation, readiness | Logical server command and service layers | Unauthorized and malformed paths never call mutation services |
| C2S network channel | Direction, bytes, collections, strings, NBT, registry IDs, UUIDs, positions, quantities, prices, actions, sessions | Direction, decode bounds, sender identity, registry membership, loaded level and entity, distance, ownership, permission, nonce, request identity, state, rate and replay | Logical server packet handler and authoritative service | Malformed, oversized, stale, replayed, wrong-side, and unauthorized payloads fail before mutation |
| S2C and user feedback | Balances, inventories, history, NBT, claims, operator context, paths, errors | Recipient binding, field minimization, size bounds, localization, no secret or private cross-player disclosure | Server response builder and client presentation | A player cannot obtain another player's private state through response or error differences |
| Filesystem and configuration | File name, directory entry, symlink, backup, JSON, TOML, journal, restored state | Approved root, normalized containment, no-follow checks, type and size bounds, schema, known fields, atomicity | Persistence and configuration adapters | Traversal, symlink swap, oversized file, malformed encoding, and partial write fail safely |
| NBT and codecs | Tags, SNBT, wire fields, saved data, receipts, unknown schema | Depth and size limits where available, canonical identity, type, count, enum, version, duplicate-field and trailing-data rules | Codec plus domain validator | Malformed or adversarial input cannot allocate unboundedly, crash the server, or bypass domain validation |
| Economy and inventory mutation | Price, amount, stock, slot, item identity, request UUID, quote, confirmation | Checked arithmetic, live revalidation, source ownership, stable fingerprint, reservation, custody, replay record | Transaction, escrow, and shop services | Duplicate, concurrent, stale, and partial execution conserves value and produces one durable outcome |
| Dependencies and JAR | Repository content, local JAR, transitive dependencies, mixins, optional integrations, resources | Pinned coordinate or digest, trusted repository scope, license and advisory review, metadata and classpath inspection | Build and packaging | Unexpected class, duplicate library, undeclared dependency, dev artifact, or vulnerable component blocks closure |
| GitHub and evidence | Logs, screenshots, issue text, attachments, review output | Sanitization, disclosure classification, least visibility, exact-revision links | Repository and security owners | Exploit steps, credentials, private NBT, or raw player data never enter public surfaces before safe remediation |

## Command Inventory and Behavior Matrix

The table below identifies the authoring-time command families. `P004-TASK-004` must replace this observed baseline with an exact dispatcher-derived inventory of every executable leaf after Phase 003. A family is not complete merely because its root appears here.

| Command family | Authoring-time roots or aliases | Primary behavior class | Required authority review | Mandatory runtime cases |
|---|---|---|---|---|
| Shop access | `/shop` | Player-only screen and session open | Player identity, readiness, route and snapshot authority | Player, console, stale readiness, reconnect |
| Auction House access | `/ah`, `/auctionhouse` with browse, create, mine, bids, watched, claims, history | Player-only module navigation | Module use permission, claims exception, server route decision | Allowed, denied, disabled, recovering, stale snapshot |
| Bazaar access | `/bz`, `/bazaar` with products, buy, sell, orders, portfolio, watched, claims, history | Player-only module navigation | Use and action permissions, claims access, server route decision | Allowed, denied, disabled, recovering, stale snapshot |
| Balance | `/balance`, `/bal`, UI variants | Read-only self balance or UI | Sender and recipient binding, privacy, provider readiness | Player, console behavior, unavailable provider, localization |
| Payment | `/pay`, status and transfer leaves present in the exact tree | Value mutation and request status | Target resolution, amount bounds, self-payment policy, request UUID, idempotency, privacy | Authorized player, offline or unknown target, retry, overflow, provider failure |
| Leaderboard | `/baltop`, UI variant | Read-only aggregate presentation | Privacy policy, pagination bounds, recipient binding | Player, console behavior, large dataset bound, unavailable provider |
| Withdrawal | `/withdraw` and exact confirmation flow | Value mutation and item delivery | Amount parsing, pending request identity, confirmation freshness, inventory capacity, custody and claim fallback | Preview, confirm, repeat confirm, disconnect, full inventory, restart |
| ATM | `/atm` | Player-only ATM open | Sender identity, readiness, recovery state | Player, console, maintenance, reconnect |
| Deposit | `/deposit` with optional amount | Inventory and wallet mutation | Amount and denomination bounds, exact source evidence, request identity, reconciliation | Valid, malformed, insufficient items, duplicate, manual review, retry |
| Claims | `/claims`, `/claimall`, `/escrow`, module leaves | Claims navigation and collection entry | Beneficiary binding, claim permission, access while disabled or recovering | Self, unauthorized cross-player attempt, disabled module, full inventory |
| Shop administration | `/shopadmin` including reload, promo, coin audit, admin shop, items, respond, balance, view, category, limits, on, and off families | Administrative read and mutation | Operator level, player and console support, target scope, service-layer permission, confirmation, audit context | Levels 0 through 4, permission-provider allow and deny, console, command block where supported, offline targets, malformed and repeated operations |
| Market administration | `/marketadmin`, `/madmin` including status, lifecycle control, audit, recovery, maintenance, admin shop, inspect, sweep, auction, and Bazaar families | Administrative diagnostics, recovery, and mutation | Combined operator and market nodes, module-specific admin rights, confirmation, reason, readiness, durable audit | Levels 0 through 4, node permutations, console, command block where supported, stale state, invalid UUID or module, recovery and maintenance |
| Storage linking | `/link` | Owner-confirmed player-shop storage mutation | Player identity, selected shop, ownership, dimension, distance, storage identity, confirmation freshness | Non-owner, stale selection, moved block, disconnect, repeat |
| Franchise | `/franchise` create, invite, accept, decline, kick, promote, manage, leave, disband | Group and role mutation | Player identity, leader and member roles, target UUID, name bounds, confirmation where irreversible, offline behavior | Unauthorized role, self-target, offline target, race, repeat, disband recovery |
| Description | `/shopdesc` | Player-shop metadata mutation | Player identity, owner and block binding, text bounds, formatting and privacy | Non-owner, oversized or control text, no selected shop, repeat |
| Bulk sell | `/sellall adminshop`, `/sellall playershops`, and confirm leaves | Quote followed by value mutation | Player identity, quote fingerprint and freshness, selected inventory proof, readiness, idempotency, conservation | Preview, confirm, repeated confirm, stale quote, disconnect, concurrent inventory change |

Each exact executable leaf receives one row with these fields: support line, full literal and argument path, alias or redirect source, registration provider, allowed sender types, minimum operator level, permission node and fallback, ownership or role check, target resolution rule, argument and suggestion bounds, confirmation token or literal, diagnostic or mutating classification, authoritative service, stable request or audit identity, readiness and maintenance behavior, success output, every error output, localization key, log fields, private data exposed, idempotency and retry rule, recovery behavior, tests, runtime transcript, documentation anchor, and finding status.

The runtime behavior matrix must exercise the Cartesian combinations that can change authorization or outcome, while using pairwise reduction only for combinations proven equivalent. It must include console, supported command block, authorized player, unauthorized player, every relevant operator level, permission-provider allow and deny overrides, player-only rejection, online and offline targets, self-target, malformed and boundary arguments, stale selection or session, disabled and recovering modules, repeated confirmation, concurrent duplicate, restart between preview and confirmation, and unavailable authoritative service. A source-level predicate alone is not sufficient; each mutating service must independently validate the authority it relies on.

The administrative closure gate is exhaustive rather than sample-based. Every executable leaf under `/shopadmin`, `/marketadmin`, and `/madmin`, every alias or redirect that reaches those trees, and every other leaf that can inspect or mutate another player's balance, inventory, claim, shop, market, transaction, recovery, maintenance, catalog, Auction House, or Bazaar state must have one dispatcher-derived row and one dedicated-server result for every materially distinct sender, permission, target, confirmation, readiness, retry, and failure case. A missing row, missing service-level authority check, missing confirmation binding, unlocalized result, or runtime case keeps `CORE-REQ-011` open.

## Packet and Non-Command Entry-Point Matrix

Every registered network message and other externally reachable mutation adapter receives one exact-revision row. The row records support line, channel and protocol value, registration discriminator or order, required direction, encoder and decoder, maximum encoded bytes, collection and string limits, NBT or SNBT depth and size limits, authenticated sender source, player or target identity derivation, registry and resource validation, permission and ownership checks, route or session nonce, request UUID and fingerprint, expected state revision, readiness and maintenance behavior, rate or work bound, execution thread, authoritative service, mutation boundary, response recipient and fields, replay outcome, log fields, privacy class, tests, runtime evidence, and finding status.

The inventory must reconcile registration to codec, handler, service, and response in both directions. C2S handlers never trust a payload-carried player identity or client-authored price, stock, permission, ownership, target, completion, or recovery state. S2C builders minimize recipient data and never disclose another player's private balance, inventory, NBT, claims, history, recovery handles, or operator context. Wrong-direction, unknown, truncated, trailing, oversized, malformed, stale, spoofed, unauthorized, replayed, and concurrent inputs fail before mutation and leave the dedicated server healthy.

## Work Packages

| Task ID | Requirement IDs | Work | Inputs and dependencies | Outputs | Affected components or interfaces | Verification |
|---|---|---|---|---|---|---|
| P004-TASK-001 | CORE-REQ-010, CORE-REQ-011 | Freeze exact revisions, preflight GitHub and private advisory capability, record dependency inputs, and prepare sanitized evidence storage | CORE-PHASE-003, EXT-005, SRC-009, SRC-011, SRC-013 | Revision and capability manifest with no secrets | Git, GitHub, build metadata, evidence workflow | Ancestry and identity checks; evidence visibility review |
| P004-TASK-002 | CORE-REQ-010, CORE-REQ-011 | Enumerate all runtime entry points and trust boundaries on both support lines | P004-TASK-001 | Complete owner and risk inventory | Commands, packets, events, menus, config, codecs, filesystem, services, compatibility, dependencies | Source-to-registration reconciliation; zero orphan registrations or handlers |
| P004-TASK-003 | CORE-REQ-010 | Build the scoped threat model and abuse-case catalog | P004-TASK-002, DEC-006 | Assets, actors, boundaries, threats, controls, residual-risk matrix | Entire supported product surface | Review every inventory row against threat classes and invariants |
| P004-TASK-004 | CORE-REQ-011 | Generate dispatcher snapshots and exhaustive command-leaf matrices for Forge and NeoForge | P004-TASK-001, P004-TASK-002 | Exact command inventories and source-to-service maps | `command` packages, registration events, permission services | Dispatcher traversal equals source registration and documented roots |
| P004-TASK-005 | CORE-REQ-010, CORE-REQ-011 | Audit every command and permission behavior and add negative tests | P004-TASK-003, P004-TASK-004 | Command and permission findings with failing evidence or disproval | Brigadier trees, services, permission nodes, config, language, logs | Full behavior matrix and dedicated-server command transcript |
| P004-TASK-006 | CORE-REQ-010 | Audit packets and other mutation entry points | P004-TASK-002, P004-TASK-003 | Packet and mutation-boundary matrix | `ShopPackets`, packet codecs and handlers, server services, events | Wrong-side, malformed, bounds, auth, stale, replay, rate and thread tests |
| P004-TASK-007 | CORE-REQ-010 | Audit paths, parsers, serializers, and deserializers | P004-TASK-002, P004-TASK-003 | Path and codec matrices with adversarial fixtures | Config, catalog, Bazaar, NBT, saved data, journal and admin writer boundaries | Traversal, symlink, size, schema, duplicate field, malformed encoding, partial write tests |
| P004-TASK-008 | CORE-REQ-010 | Audit privacy, secrets, diagnostics, and evidence transport | P004-TASK-002, P004-TASK-003 | Data classification and disclosure matrix | Logs, command output, packets, history, issues, CI, JAR, docs | Secret scans, recipient tests, log snapshots, public-evidence content review |
| P004-TASK-009 | CORE-REQ-010 | Audit replay, duplication, concurrency, and conservation at mutation entries | P004-TASK-003, CORE-REQ-014 | Mutation and conservation matrix | Commands, packets, shops, ATM, economy, escrow, market, claims | Concurrent duplicate, retry, disconnect, restart, and partial-failure proof |
| P004-TASK-010 | CORE-REQ-010 | Audit dependency provenance and packaging | P004-TASK-001, P004-TASK-002 | Line-specific dependency, advisory, license, repository, and JAR matrix | Gradle files, local JARs, metadata, optional integrations, built archives | Dependency reports, hashes, advisory scan, archive listing, duplicate-class and secret checks |
| P004-TASK-011 | CORE-REQ-010, CORE-REQ-011, CORE-REQ-009 | Classify and deduplicate findings, then create canonical records before repair | P004-TASK-005 through P004-TASK-010, EXT-005 | Public issues, private advisories, duplicate links, or disproval records | GitHub issue and security workflow | Timestamp proves record predates repair; disclosure review passes |
| P004-TASK-012 | CORE-REQ-010, CORE-REQ-011, CORE-REQ-014, CORE-REQ-016 | Repair verified findings with regression-first, line-specific changes | P004-TASK-011 | Reviewed repair commits and migration or rollback notes | Only affected components and support lines | Failing-before and passing-after evidence; invariant and compatibility review |
| P004-TASK-013 | CORE-REQ-010, CORE-REQ-011 | Verify each repaired line through full required local and runtime gates | P004-TASK-012 | Exact-revision verification results | Tests, data, applicable GameTests, builds, server, client, controlled multiplayer for affected network paths, JAR | Repository-prescribed order with decisive results retained and a concrete not-applicable rationale only for data or GameTest surfaces absent from the changed line |
| P004-TASK-014 | CORE-REQ-010, CORE-REQ-011, CORE-REQ-017 | Reconcile documentation and safe finding records, commit and push verified affected-line changes, complete required review if the optional private review capability exists and all required checks, merge the Forge phase pull request into `1.20.1` and any independently required NeoForge pull request into `1.21.1`, then fetch and verify each remote merge | P004-TASK-013, EXT-005 | Merged revisions, safe records, updated docs and runbooks | Pull requests, issues or advisories, README, technical and focused docs | Green checks, private review result or recorded capability absence, exact PR bases, GitHub merge records, fresh remote ancestry, source-to-doc and disclosure review |
| P004-TASK-015 | CORE-REQ-010, CORE-REQ-011 | Rerun every Phase 004 matrix at exact merged revisions, close every remediated finding, create and push the signed Phase 004 tag on the verified Forge merge, and prepare the Phase 005 handoff | P004-TASK-014 | Clean final audit packet, verified phase tag, closed finding register, and persistence security inventory | Both supported lines and all Phase 004 matrices | Zero unclassified or open in-scope findings, exact merged-revision binding, tag target and signature, remote tag presence, and no substitute for any missing gate |

### Ordering, Parallelism, and Repair Gates

- Tasks 001 through 004 are sequential enough to establish a single frozen scope. Forge and NeoForge inventory collection may run in parallel only after each exact revision is independently recorded.
- Tasks 005 through 010 may run in parallel by matrix, but all use the same entry-point inventory and threat taxonomy. A finding discovered in one matrix must be cross-referenced in every affected matrix.
- Task 011 is a hard gate before implementation. Reproduction tests that do not alter product behavior may be prepared before the record, but no product repair begins until the canonical public issue or private advisory exists.
- Sensitive findings never include exploit strings, exact bypass sequences, private raw logs, player NBT, credentials, or weaponized patches in public issues, branches, commit messages, pull requests, Actions artifacts, or documentation. Use a private advisory and neutral public wording only after remediation is safe.
- Repairs may proceed independently on separate support lines only after separate affected-line proof. The Forge repair set uses the sequential Phase 004 branch and pull request into `1.20.1`. An independently proven NeoForge repair uses its own line-specific branch and pull request into `1.21.1`. Each line receives regression, review, checks, merge, and post-merge rerun evidence, and neither line consumes a partial or unmerged state from the other.
- If one repair changes a command literal, permission node, protocol, persistent representation, public API, dependency boundary, or product behavior beyond the master contract, stop with `PLAN_REVISION_REQUIRED` rather than widening scope.
- Phase 005 does not start until Task 015 binds clean matrices and the persistence handoff to exact merged revisions.

## Architecture and Implementation Boundaries

### Authority and Dependency Direction

- Client screens, buttons, cached capabilities, drafts, and command suggestions are presentation only. They may prevent obviously invalid requests but never prove authorization, price, stock, ownership, target identity, readiness, completion, or recovery state.
- Commands and packet handlers are adapters. They validate transport and source context, then call authoritative server services. They do not write balances, inventory, catalogs, market state, custody, or persistence directly when an existing authoritative service owns the mutation.
- Permission checks are layered. Dispatcher predicates control discovery and execution, while mutation services validate player identity, role or ownership, permission, target, readiness, and state at execution time. Console bypass is explicit per leaf, never inherited accidentally from the absence of a player.
- Diagnostic commands must be observational. A status, audit, verify, inspect, list, info, or check leaf may not repair, resume, sweep, mutate, or disclose another player's private data as a side effect.
- Confirmation is bound to the actor, exact operation, exact target, normalized arguments, expected revision, and a bounded validity window where a preview or pending record exists. Repeating a confirmed request is idempotent or returns the prior result.
- Mutation services preserve stable request UUIDs and canonical fingerprints across command, packet, journal, receipt, claim, retry, and audit paths. New random identity during retry is a defect if it can duplicate an effect.
- Common initialization never loads client-only classes. Optional integrations remain behind runtime checks. Dedicated-server tests prove command and packet registration without client classloading.

### Input and Resource Bounds

- Every string, collection, NBT or SNBT value, JSON object or array, file, directory, history page, audit count, suggestion set, quantity, price, stock, coordinate, distance, page, index, and duration has an explicit finite bound enforced before allocation or mutation.
- Registry identities are resolved through the active server registry and fail closed. Client-supplied display names, localized text, class names, item representations, permission strings, and NBT do not create authority.
- Numeric parsing rejects overflow, underflow, non-finite decimal inputs, invalid scale, negative values where forbidden, and values outside domain limits. Authoritative money stays in checked integer minor units.
- Packet decoders use explicit maximum lengths and safe collection bounds. Decode failure cannot disconnect unrelated players, mutate state, or leave partial server state.
- Command suggestion providers are bounded, non-mutating, permission-aware, and resilient to unavailable services. They do not disclose hidden players, private listing data, recovery handles, or unauthorized registry information.

### Path and Serialization Controls

- Every externally influenced path is normalized and proven to remain inside its approved FutureShops root after resolution. Directory traversal, absolute paths, alternate separators, symbolic links, hard-link surprises where detectable, special files, and time-of-check to time-of-use swaps fail closed.
- Readers bound file count, individual bytes, total bytes, encoding, recursion or nesting where supported, collection counts, string lengths, and schema versions. Unknown compatible fields are retained where the domain contract requires preservation; unsupported or ambiguous schemas do not partially load.
- Writers validate the complete candidate, write to a safe sibling temporary file, force or verify as required by the documented guarantee, atomically replace where supported, retain last-known-good state, and clean only their own temporary file. A cleanup failure does not delete authoritative evidence.
- NBT identity is canonical and exact only where selected. Parser input is bounded before parsing. No decoder instantiates arbitrary classes, executes expressions, accesses the network, or trusts polymorphic type names from data.
- Protocol changes are append-compatible only where the existing contract permits, increment the line-specific protocol when required, reject incompatible peers clearly, and are verified against packet registration order and wire fixtures.

### Dependency and Packaging Controls

- Record direct and transitive dependencies, Gradle plugins, repositories, local JAR names and hashes, mod metadata, licenses, known vulnerabilities, and packaging disposition separately for Forge and NeoForge.
- Restrict repositories to the groups they are intended to serve. A local `flatDir` JAR requires an exact hash and expected module identity because coordinates alone do not prove provenance.
- Confirm whether each runtime library is bundled, supplied as a mandatory mod, or provided transitively. Metadata and actual JAR contents must agree; duplicate classes and missing runtime dependencies block closure.
- A known applicable vulnerability is a verified finding only after version, code path, reachability, and supported-line impact are established. A scanner alert is evidence to investigate, not automatic proof. A disproval records why the vulnerable code is absent or unreachable.
- No audit repair silently upgrades Minecraft, Forge, NeoForge, Java, Gradle, mappings, or another pinned platform boundary. If the only safe dependency repair requires such a change, stop for master-plan revision.

### Privacy and Logging Controls

- Public output and ordinary logs use the minimum identifiers required to diagnose the event. They do not contain credentials, access tokens, full private NBT, raw player files, complete inventories, full filesystem paths when a relative FutureShops path is sufficient, or exploit-enabling payloads.
- Administrative balance, inventory, transaction, recovery, and claim details require explicit authorization and are returned only to the correct source. Broadcast flags are false unless public broadcast is intentional and documented.
- Audit records retain actor UUID, target identity, operation, reason, request or transaction identity, result, and revision only when needed for accountability. Free-form reasons are length-bounded and sanitized for logs and UI.
- Test fixtures are synthetic or irreversibly sanitized. Issue attachments and runtime transcripts are reviewed before upload. Sanitization preserves decisive structure without retaining player names, private UUID correlations, or sensitive NBT.

## Finding Classification and Confidential Routing

1. Reproduce or establish a precise violated invariant at a named support-line revision. Do not label a suspicion as verified.
2. Search open and closed public issues, pull requests, and available private advisories by behavior, component, exception, identifier, and affected version.
3. Classify disclosure risk before creating a record.
   - Non-sensitive correctness, permission, diagnostics, or packaging defects use a normal deduplicated GitHub issue with sanitized evidence.
   - Exploitable bypasses, duplication methods, path traversal, unsafe deserialization, secret exposure, private-data disclosure, or other weaponizable details use the repository private vulnerability process. The advisory is the canonical pre-repair GitHub record.
4. Create or enrich that canonical record before product repair. Record affected lines, severity, sanitized impact, exact evidence location, acceptance criteria, and disclosure restrictions. Keep proof of the timestamp outside this protected plan file.
5. Add a failing regression where feasible, repair only proven affected lines, and link private technical details to the advisory. Public branches, commit messages, and pull requests use neutral wording that does not reveal the exploit before remediation.
6. After merge and exact-revision verification, perform a disclosure review. Create or update a sanitized public issue only when doing so is safe and useful. Do not copy confidential reproduction details into the public record.
7. If private vulnerability reporting becomes unavailable, keep the phase open, disclose nothing publicly, and restore an approved repository-private channel before coordination continues. There is no public-report or lower-fidelity completion substitute.

## Failure, Recovery, and Edge Cases

| Scenario | Detection | Required behavior | Recovery or rollback | Regression proof |
|---|---|---|---|---|
| Inventory misses a registered leaf or packet | Dispatcher or channel snapshot differs from source matrix | Fail the inventory gate; no clean-audit claim | Regenerate all dependent matrices from exact revision | Snapshot reconciliation test |
| Unauthorized command reaches service | Spy or audit hook observes service call after denied predicate | Reject before data access or mutation and emit bounded feedback | Preserve state, file confidentially if exploitable, repair service defense | Permission matrix with zero mutation |
| Console or command block takes a player-only path | Source has no `ServerPlayer` or unsupported entity | Return precise failure without cast, crash, or mutation | No state recovery needed; add source-type test | Dedicated-server console and command-block transcript |
| Permission plugin and operator fallback disagree | Explicit node result differs from configured operator level | Follow the documented precedence consistently at dispatcher and service | Restore prior permission config; do not infer permission from UI | Node allow and deny matrix at levels 0 through 4 |
| Confirmation is stale, repeated, or targets changed state | Revision, actor, target, fingerprint, or pending identity mismatch | Reject stale input or replay the prior result without another effect | Preserve pending evidence and re-preview current state | Preview, mutation, repeat, restart test |
| Malformed or oversized packet | Decoder or handler rejects size, enum, index, UUID, NBT, or collection | Fail before allocation amplification or mutation; isolate to sender | Drop request, retain server health and state | Codec fuzz and dedicated-server liveness test |
| Wrong-side or spoofed identity packet | Direction or sender does not match payload identity | Reject and use authenticated sender only | No mutation; log bounded event if useful | Wrong-direction and identity-spoof tests |
| Path traversal or symbolic-link swap | Normalized containment or no-follow check fails | Refuse read and write without revealing sensitive absolute paths | Preserve original and last-known-good state | Traversal, symlink, special-file, and race fixture |
| Malformed JSON, TOML, NBT, or saved data | Bound, encoding, duplicate field, schema, type, or canonicalization fails | Reject complete candidate; never partially apply | Keep prior valid snapshot and preserve failing evidence | Parser and last-known-good tests |
| Duplicate or concurrent mutation | Same request, fingerprint, actor, or authoritative resource races | One durable effect; deterministic replay or conflict for others | Release reservations or retain recoverable custody | Parallel request and restart conservation test |
| Provider or persistence fails after prepare | Fault injection between validation, custody, commit, and delivery | Compensate once or retain durable recoverable custody and claim | Freeze unsafe mutation, preserve lineage, use documented recovery | Fault matrix and conservation report |
| Log or response leaks private state | Snapshot contains unapproved field or wrong recipient sees data | Block evidence publication and repair disclosure boundary | Remove exposed artifact where possible; rotate secrets outside repository scope if needed | Recipient isolation and sanitized-log golden tests |
| Vulnerable or unexpected dependency is present | Dependency report, advisory match, hash mismatch, duplicate class, JAR listing | Block merge or candidate claim; privately assess reachability | Restore known dependency input or perform approved line-specific repair | Dependency and archive comparison |
| Security scanner reports unreachable code | Alert lacks reachable affected path | Record evidence-based disproval; do not make unrelated upgrade | No product rollback | Reachability trace and exact version record |
| Repair changes schema or protocol | Diff identifies serialized name, field order, packet ID, version, or reader behavior | Add compatibility and migration proof or stop if out of phase | Revert repair or route required scope to plan revision | Old/new fixture and mixed-version rejection test |
| Runtime test detects loss, duplication, maintenance, or inaccessible claim | Conservation or recovery assertion fails | Stop all mutation testing and preserve complete state | Restore one complete matching snapshot; never delete selected files | Reproduce safely after root-cause repair |
| Sensitive finding cannot be recorded privately | Private advisory capability or authorization is unavailable | Keep the phase open, publish nothing, and do not begin public repair coordination | Restore an approved repository-private channel, then create the canonical confidential record before repair | Capability check and confidential record timestamp precede the repair |

## Verification Matrix

| Requirement or task | Static or unit | Integration | Real workflow or runtime | Negative and recovery | Evidence artifact |
|---|---|---|---|---|---|
| P004-TASK-002 | Registration and handler inventory assertions | Source-to-dispatch and source-to-channel reconciliation | Dedicated server registers expected commands and packets | Missing, duplicate, orphan, wrong-side entries fail | Exact-revision entry-point inventory |
| P004-TASK-003 | Threat taxonomy coverage check | Each boundary maps to an owner and control | Review actual runtime routes for representative workflows | Unowned asset or boundary blocks audit | Threat model and trust matrix |
| P004-TASK-004 | Dispatcher traversal and command language-key tests | Every leaf maps to one authoritative service | Server command tree snapshot for Forge and NeoForge | Alias collision, merged root, hidden unauthorized leaf | Command inventory artifact |
| P004-TASK-005 | Argument, permission, confirmation, output, idempotency tests | Dispatcher plus service defense in depth | Console, supported command block, authorized and unauthorized player matrix | Offline, malformed, stale, repeat, recovering, denied | Command behavior transcript and permission matrix |
| P004-TASK-006 | Codec round trip, bounds, enum, UUID, collection, and direction tests | Packet handler to authoritative service with authenticated sender | Client/server and multiplayer workflow where state crosses network | Fuzz, oversized, spoofed, stale, replayed, rate-limited, disconnect | Packet security matrix and liveness log |
| P004-TASK-007 | Path containment, symlink, parser, schema, NBT, and unknown-field tests | Load, reload, last-known-good, atomic write, and handler integration | Isolated config and restored-fixture server start and reload | Traversal, race, malformed, oversized, partial write, unsupported schema | Path and serialization matrix |
| P004-TASK-008 | Secret scan, log snapshot, output field and recipient tests | Command, packet, audit, history, claims, and issue evidence flow | Dedicated server logs and client output for private-data workflows | Wrong recipient, public artifact, control text, full path or NBT leak | Privacy and disclosure matrix |
| P004-TASK-009 | Replay, fingerprint, checked arithmetic, concurrency property tests | Command and packet mutations through escrow and services | Retry, disconnect, restart, and two-client concurrency for every affected networked mutation | Duplicate, stale state, provider and delivery failure | Conservation and idempotency report |
| P004-TASK-010 | Dependency graph, local-JAR hash, repository, metadata, and advisory checks | Runtime classpath and optional-integration startup | Clean server and client startup with required and absent optional dependencies | Hash mismatch, missing mandatory mod, duplicate class, unexpected package | Dependency and JAR matrix |
| P004-TASK-011 | Finding schema and duplicate-query validation | Issue or advisory link precedes repair commit | GitHub state visible at permitted disclosure level | Sensitive content transport review | Finding register with timestamps |
| P004-TASK-012 | Focused regressions fail before and pass after | Affected subsystem integration | Reproduced real behavior at repair revision | Rollback, compatibility, invariant and recovery checks | Change evidence packet |
| P004-TASK-013 | Full `test` and applicable validation tasks | Full line-specific build | Dedicated server, client, and multiplayer where required | Restart, reconnect, permission changes, malformed input | Exact-revision verification packet |
| P004-TASK-015 | Complete matrix rerun, ancestry, tag target, and signature checks | Cross-matrix consistency, issue-to-fix traceability, and exact merge reconciliation | Exact merged runtime rerun on the local isolated server and clients required by affected paths | Any new finding reopens Task 011 and invalidates affected evidence; no substitute closes the phase | Clean Phase 004 completion packet and verified signed tag |

### Fixtures, Environments, and Rerun Order

- Use synthetic players representing operator levels 0 through 4, explicit permission-node allow and deny states, console, and supported command-block sources. Use distinct online and offline targets and separate player identities for privacy and ownership checks.
- Use isolated Forge 1.20.1 Java 17 and NeoForge 1.21.1 Java 21 environments at named revisions. The 64 GB workstation is the normal dedicated-server and multiple-client host. The 96 GB node1 host may run only a temporary isolated server when it improves capacity or repeatability. Never reuse a mutated world as the pristine baseline. Back up the complete world and FutureShops state as one matching generation before fault tests.
- Use bounded adversarial corpora for strings, collections, identifiers, paths, symbolic links, JSON, TOML, SNBT, NBT, packet bytes, duplicate fields, unsupported schemas, numeric limits, malformed UTF-8, stale UUIDs, and replay sequences. Retain only synthetic sanitized inputs.
- For each changed line, run focused failing regressions, full unit tests, `runData` when resources or providers changed, applicable GameTests, full build, dedicated-server smoke, client smoke for client or network changes, multiplayer for cross-network state, restart and reconnect, JAR inspection, dependency inspection, secret scan, and full diff inspection.
- Forge commands use the checked-in wrapper with Java 17, including `bash ./gradlew test`, applicable `runData`, applicable `runGameTestServer`, `build`, `runServer`, and `runClient`. NeoForge uses the wrapper and tasks present on its exact Java 21 branch; record the discovered task names rather than assuming Forge task wiring.
- A lower-fidelity unit or source scan does not replace a required dispatcher snapshot, dedicated-server command, actual packet path, client/server workflow, concurrency exercise, recovery test, or JAR inspection.
- Any material change invalidates the inventory row, threat rows, focused regressions, integration evidence, runtime evidence, documentation, dependency result, and JAR result that it can affect. Task 015 reruns after the final merge, not merely before the pull request.

## Documentation, Operations, and Release

- Update `README.md` when common commands, aliases, permissions, setup, dependencies, or user-visible behavior change.
- Update `DOCUMENTATION.md` for trust boundaries, server authority, command registration, permission precedence, packet validation, logging, privacy, dependency packaging, and recovery behavior.
- Update `docs/README.md` if any focused document is added or moved. Preserve existing tracked layout and links.
- Update the existing command reference or create a focused command guide only if the repository has no adequate location. It must list exact syntax, sender types, permissions, confirmation, success and failure behavior, recovery-state behavior, and examples for every administrative leaf.
- Update permission and configuration guidance with exact Forge nodes, fallback operator levels, provider precedence, reload behavior, and claims access. Document NeoForge only for nodes and behavior actually present on that line.
- Update security and recovery guidance with safe diagnostic commands, stop conditions, backup scope, log sanitization, private vulnerability reporting, and non-destructive recovery. Do not publish exploit reproduction details.
- Update dependency and compatibility documentation if required runtime mods, packaging, repository provenance, or optional-integration boundaries change.
- Every normal GitHub issue, pull request, review, and closure message follows repository lowercase and punctuation rules. Security advisories retain the same neutral maintainer voice while remaining private.
- Issues and advisories link exact failing evidence, affected line, repair, regression, merge, and post-merge result. Public records contain only sanitized information that is safe after disclosure review.
- This phase may prepare JARs only for verification. It does not publish a GitHub Release, CurseForge or Modrinth file, announcement, public release tag, or stable designation.
- Wiki-ready documentation changes are prepared from tracked documentation and published only after approved merge under the repository workflow.

## Risks and Evidence Invalidation

| Risk | Prevention | Detection | Recovery | Evidence invalidated | Reverification |
|---|---|---|---|---|---|
| Phase 003 changes the entry surface after inventory | Bind all matrices to merged revision and compare revisions before each run | Revision mismatch or inventory diff | Regenerate inventory and dependent matrices | All Forge Phase 004 evidence | Restart at Task 001 for Forge |
| NeoForge is assumed equivalent to Forge | Independent tree, build, permission, packet, path, and dependency inventory | Cross-line file or API assumption lacks proof | Remove assumption and perform line-specific review | Affected NeoForge evidence | Rerun exact NeoForge matrix |
| A command leaf is hidden by alias or merged root | Dispatcher traversal plus source registration reconciliation | Tree count or path mismatch | Add missing leaf and all behavior rows | Command, permission, docs | Rerun Tasks 004, 005, 013, 015 |
| Dispatcher check exists but service trusts caller | Defense-in-depth source trace and direct service tests | Unauthorized direct adapter reaches mutation | Add service validation without duplicating authority incorrectly | Permission, command or packet, mutation evidence | Focused negative, integration, runtime, full matrices |
| Sensitive finding leaks through public workflow | Disclosure classification and final transport review | Secret or exploit content scanner or review | Remove exposure where possible, use advisory, rotate secrets if needed | Privacy, issue, PR, CI, docs evidence | Full public-surface and secret-scan rerun |
| Parser test causes destructive or unbounded behavior | Bounded synthetic corpora, isolated process, time and memory limits | Timeout, memory growth, state mutation | Terminate isolated run, preserve fixture and logs, restore snapshot | Parser and runtime evidence | Rerun after bounded repair |
| Fix breaks protocol or save compatibility | Diff gates on packet order, schema, identifier, and reader changes | Old fixture or mixed-version test fails | Revert or add compatible versioned path; stop for plan revision if material | Packet, serialization, runtime, JAR | Compatibility suite and all downstream gates |
| Security fix creates duplication or loss | CORE-REQ-014 review and conservation tests for every mutation change | Balance, stock, custody, claim, or replay mismatch | Freeze mutation, preserve lineage, restore full snapshot | Mutation, runtime, recovery evidence | Focused conservation through complete rerun |
| Scanner alert is treated as a verified defect without reachability | Require exact version, code path, impact, and reproduction or sound reachability | No supported execution path | Classify as disproven or informational with evidence | Finding classification only | Repeat dependency scan at final revision |
| Local JAR changes without coordinate change | Record cryptographic hash and inspect contents | Hash or archive listing mismatch | Restore approved input or file confidential supply-chain finding | Dependency, build, JAR, runtime | Full dependency through runtime rerun |
| Late documentation exposes confidential detail | Source and disclosure review before merge | Public diff contains exploit or private evidence | Remove sensitive content and keep private reference | Documentation, issue, PR evidence | Link, content, privacy and disclosure checks |
| Private review or required confidential GitHub capability is unavailable | Separate optional private review from mandatory confidential finding routing at entry | Authentication or feature failure | Record optional review absence without weakening deterministic gates. If confidential routing is affected, keep the phase open and restore an approved private channel before repair coordination | Finding and merge evidence | Restore required capability and rerun the affected workflow; optional review absence remains a capability record only |
| Final merge differs from tested revision | Post-merge revision binding and rerun | Commit mismatch | Discard pre-merge closure claim | All affected exact-revision results | Task 015 on actual merge commit |

## Phase Completion Packet

The packet is retained outside this protected plan set and contains:

1. The exact Phase 003 merge and signed tag consumed as the Forge base, the Phase 004 entry and merged Forge and NeoForge revisions, branch ancestry, toolchains, pull requests, merge commits, fresh remote containment, and required green checks. NeoForge merge fields are required only when a NeoForge finding produced a repair.
2. The complete entry-point inventory and scoped threat model for each support line.
3. Exact dispatcher snapshots and exhaustive command behavior matrices, including aliases, merged roots, permission nodes, fallback levels, sender types, confirmations, service mappings, outputs, logs, and recovery behavior.
4. Packet registration and handler matrices, with direction, codec bounds, identity, permission, state, replay, rate, threading, mutation, and response privacy evidence.
5. Path, JSON, TOML, NBT, saved-data, codec, privacy, secret, duplication, dependency, repository, local-JAR hash, advisory, optional integration, mod metadata, and final JAR matrices.
6. The finding register with classifications, duplicate searches, canonical issue or advisory timestamps, disclosure levels, affected lines, repairs, regressions, pull requests, reviews, merges, and closed or safely remediated states. No finding may remain open as a substitute for phase closure.
7. Failing-before and passing-after focused regression results for every repaired finding where feasible, with explicit rationale where a failing test cannot be safely retained.
8. Full unit, applicable data and GameTest, build, dedicated-server, client, controlled multiplayer for every affected network path, restart, reconnect, dependency, JAR, secret, and diff results at exact merged revisions. The runtime packet records use of the 64 GB workstation or the authorized node1 temporary-server fallback under DEC-007.
9. Sanitized dedicated-server command transcripts covering every required sender, permission, target, malformed, stale, repeated, and recovery case.
10. Conservation and idempotency reports for every Phase 004 mutation path affected by a repair.
11. Documentation diffs, source-to-document review, link results, safe operator procedures, and disclosure review.
12. A persistence security handoff listing every path, codec, owner, authority boundary, privacy class, schema observation, security-relevant failure, and open Phase 005 evidence need.
13. The signed annotated tag `phase-004-security-command-audit`, its exact Forge merge target, verified EnVisione signature, remote presence, and confirmation that it is internal phase evidence rather than release publication.
14. A signed statement that no public exploit detail or private player evidence was exposed before safe remediation, no publication action occurred, no completion substitute was used, and no known Phase 004 finding remains.

## Next Transition

After `CORE-PHASE-004` has merged every repair, fetch and verify the actual support-line merge revisions, rerun Task 015 on those exact revisions, create and push the verified signed Phase 004 tag on the Forge merge, close every remediated Phase 004 finding, and assemble the complete packet, begin `CORE-PHASE-005` by reading `phases/plan-phase-005.md` through EOF. Freeze the complete persistence inventory against the exact merged Phase 004 Forge revision and the exact approved NeoForge revision, or its exact merged revision when Phase 004 repaired that line. Carry forward every path, codec, serialization, privacy, authorization, duplication, journal, SavedData, configuration, catalog, NBT, JSON, TOML, backup, and recovery boundary discovered here.

Do not start `CORE-PHASE-005` from an open Phase 004 pull request, a pre-merge test commit, an untagged Forge merge, a branch that does not descend from the verified support-line merge, an open Phase 004 finding, or incomplete dedicated-server command evidence. Phase 005 starts from the latest approved `origin/1.20.1` merge, never from the Phase 004 branch. A later Phase 005 change that affects a Phase 004 boundary invalidates the named Phase 004 matrix rows and requires their exact-revision rerun. Phase 005 owns persistence, migration, integrity, recovery, and economic conservation closure; it does not reopen a clean Phase 004 finding unless new evidence proves a defect, in which case the rolling `CORE-REQ-009` issue-before-repair loop applies again.
