# Phase 006 Execution Plan

> **Plan ID:** PLAN-PHASE-006
> **Phase ID:** CORE-PHASE-006
> **Owner:** Server integration architecture
> **Classification:** MANDATORY
> **Master plan:** [plan.md](../plan.md)
> **Phase sequence:** 006 of 007

## Purpose and Ownership

This phase proves that the individually clean authoritative services delivered through `CORE-PHASE-005` compose as one safe product. It audits and closes backend integration and failure handling across client and server networking, readiness, Server Shops, Player Shops, every configured economy provider and payment source, escrow, Auction House, Bazaar, ATM, claims, configuration reload, shutdown, restart, reconnect, concurrency, replay, diagnostics, and controlled multiplayer.

The canonical requirement is `CORE-REQ-013`. The phase consumes the complete merged Phase 005 persistence, recovery, and conservation contract, including the locally generated issue 32 corpus and its ownership-isolation proof. It also consumes the merged Phase 004 security and command boundaries and the Phase 002 finite-stock multiplayer proof. An integration result may reopen affected upstream evidence, but it does not silently weaken or replace an upstream invariant.

The master plan owns product scope, support-line policy, the fixed phase sequence, and final completion authority. This file owns the dependency-ordered execution, issue routing, repair, local runtime verification, Forge integration, signed phase tag, and exact handoff for `CORE-PHASE-006`.

## Evidence-Based Entry State

| Evidence class | Area | Finding | Source or command | Freshness condition |
|---|---|---|---|---|
| VERIFIED | Product contract | `CORE-REQ-013` requires every named backend integration and failure state to compose under exact-revision server, client, restart, reconnect, and multiple-client evidence | `plan.md`, `CORE-REQ-013`, `DEC-006`, and `DEC-007` | Invalidated only by an owner-authorized Plan Creator revision |
| VERIFIED | Upstream integration | Phase 006 depends on the full `CORE-PHASE-005` merge and completion packet, not a partial branch, queued pull request, pre-merge result, or alternate transition | `plan.md`, `plan.index.json`, and `phases/plan-phase-005.md` | Revalidate after any Phase 005 merge, tag, issue, or evidence change |
| ENTRY CONTRACT | Issue 32 local proof | Phase 005 supplies the complete locally controlled issue 32 successor corpus, player-state ownership map, modded-item and unrelated-NBT sentinels, recovery results, and merged exact-revision evidence | Phase 002 and Phase 005 completion packets, `CORE-REQ-005`, `CORE-REQ-012`, and `DEC-007` | Invalid after any player persistence, receipt, claim, inventory proof, recovery, or relevant codec change |
| OBSERVED | Forge implementation | The Forge line contains packet handlers, Server Shop and Player Shop services, economy-provider adapters, escrow runtime services, Auction House and Bazaar services, ATM workflows, claims, sessions, configuration owners, and client response trackers | `src/main/java/com/enviouse/futureshops/` and `SRC-011` | Reinventory after any relevant source, registration, protocol, or ownership change |
| OBSERVED | Readiness and lifecycle | Escrow readiness, maintenance, module controls, capability projection, sessions, schedulers, configuration generations, and Bazaar initialization are distinct integration surfaces that must agree | Current Forge source and Phase 004 through Phase 005 interface packets | Reconfirm after any readiness, lifecycle, session, configuration, or scheduler change |
| OBSERVED | Existing tests | Focused unit, source-contract, recovery, replay, packet, market, ATM, shop, claims, and configuration tests exist, but do not replace the full Phase 006 runtime matrix | `src/test/java/com/enviouse/futureshops/` and `SRC-011` | Invalid after any affected implementation, fixture, harness, or dependency change |
| AVAILABLE | Controlled multiplayer capacity | The 64 GB workstation is the default host for one isolated Forge dedicated server and at least two independent client JVMs and profiles. The 96 GB node1 host is an authorized temporary isolated-server fallback | `SRC-014` and `DEC-007` | Revalidate process, memory, port, display, host, profile, world, revision, and JAR identities before each runtime campaign |
| HISTORICAL | Prior prerequisite identifiers | `EXT-001` through `EXT-004` are resolved or superseded historical traceability only. `EXT-004` has no dependency, blocker, failure-route, or endpoint role because `DEC-007` owns the required local multiplayer proof | `plan.md`, Section 10, and `DEC-007` | Changes only through an owner-authorized Plan Creator revision |
| AVAILABLE | GitHub traceability | Authenticated EnVisione access is available for duplicate search, issue creation, pull requests, checks, merges, tags, and evidence synchronization | `EXT-005` | Revalidate identity, remote, and permission before remote mutation |
| PROPOSED | Phase evidence | The Phase 006 matrices, repairs, local runtime packet, Forge merge, merged-revision reruns, and phase tag have not executed at authoring time | This execution blueprint | Becomes verified only through retained evidence at the exact merged revision |

No `OBSERVED`, `ENTRY CONTRACT`, or `PROPOSED` result becomes `VERIFIED` without an exact revision, fixture or corpus identity, environment, procedure, expected result, actual result, and retained decisive evidence.

## Scope Boundaries

### Included Scope

- `CORE-REQ-013` is the sole canonical requirement owned by this phase. The phase inventories, audits, repairs, integrates, and proves every named cross-component success and failure path.
- `CORE-REQ-002`, `CORE-REQ-009`, `CORE-REQ-010`, `CORE-REQ-011`, `CORE-REQ-012`, and `CORE-REQ-014` are consumed contracts. A verified integration defect that violates one of them reopens the affected evidence and follows issue-before-repair routing.
- The complete Phase 005 merge, persistence inventory, schema and migration matrix, local issue 32 corpus, recovery contract, conservation packet, and stable Phase 005 tag are mandatory Phase 006 inputs.
- Forge 1.20.1 is the full runtime and phase-integration line. NeoForge 1.21.1 receives an exact source-backed impact decision and independent regression or repair only when a Phase 006 finding or shared-contract change is proven to affect that line.
- Successful workflows, deterministic rejections, interrupted workflows, crash cuts, provider faults, stale and replayed requests, concurrent actors, module transitions, configuration generations, shutdown, restart, reconnect, recovery, diagnostics, and local controlled multiplayer are included.
- The Forge phase pull request, required checks, independent private review if the optional private review capability exists, GitHub merge into `1.20.1`, exact merged-revision reruns, signed annotated phase tag, issue synchronization, and Phase 007 handoff are included.

### Explicit Exclusions

- `CORE-REQ-015`, `CORE-REQ-017`, `CORE-REQ-018`, `CORE-REQ-019`, and `CORE-REQ-020` remain canonically owned by `CORE-PHASE-007`. Phase 006 provides complete integration inputs without claiming final candidate closure.
- `FUT-001` and `FUT-002` remain excluded. No GitHub Release, CurseForge or Modrinth upload, release announcement, stable designation, public product version tag, or published candidate is authorized.
- `FUT-003` remains excluded. A verified integration defect is repaired, but an unrelated feature or subsystem requires a plan revision.
- `FUT-005` remains excluded. This phase does not introduce distributed live market state or direct external-storage listing without deterministic receipts.
- `NG-003` forbids deleting player data, worlds, journals, checkpoints, ledgers, custody, claims, receipts, or selected persistent files during testing or recovery.
- `NG-004` forbids weakening server authority, readiness, maintenance, permissions, request idempotency, claims access, compatibility, or recovery to make a client route appear usable.
- `NG-005` forbids assumed cross-line transfer. Forge and NeoForge changes require separate proof, branches, builds, pull requests, merges, and exact-revision verification.
- `EXT-001` through `EXT-004` may appear only as historical traceability. None is an executable dependency, completion alternative, or transition condition.

## Phase Contract

### CORE-PHASE-006 — Backend Integration and Failure-Handling Closure

**Objective:** Prove at exact merged revisions that every named subsystem combination reaches one deterministic, server-authoritative, conservation-safe result in success and failure states, and repair every verified integration defect through the rolling issue contract.
**Owner:** Server integration architecture
**Dependencies:** CORE-PHASE-005
**Canonical requirements:** CORE-REQ-013
**Documentation and release impact:** Update integration architecture, lifecycle, configuration reload, diagnostics, recovery, and multiplayer verification documentation for behavior proven in this phase. Produce exact-revision evidence for Phase 007. Do not publish or prepare final release artifacts.
**Next transition:** CORE-PHASE-007

**Entry criteria**

- The Phase 005 Forge pull request is fully merged into `1.20.1`, `origin/1.20.1` contains the merge, all Phase 005 exact merged-revision checks passed, and signed annotated tag `phase-005-persistence-recovery` targets that merge and verifies as EnVisione.
- The complete Phase 005 packet is readable and reports no unresolved mandatory gate, skipped mandatory row, unclassified persistence surface, unresolved phase defect, destructive recovery path, stale evidence, or unmerged repair.
- The local issue 32 corpus, ownership-isolation map, modded-item and unrelated-player-NBT sentinels, repeated recovery, restart, reconnect, claim, receipt, and delivery-slot evidence all pass at the Phase 005 merged revision.
- The Phase 006 Forge branch is created only from the latest approved fetched `origin/1.20.1` merge. It is not stacked on the Phase 005 branch or based on a local approximation of the merge.
- Phase 004 security and command evidence and Phase 002 finite-stock issue 34 evidence are bound to the consumed ancestry, with every later invalidation rerun.
- Packet registration, protocol version, sessions, route nonces, configured economy providers and payment sources, module controls, persistent stores, configuration generations, recovery identities, and test-only fault injectors are inventoried at the exact phase-start revision.
- The default 64 GB workstation can start one isolated dedicated Forge 1.20.1 server and at least two independent client processes with separate profiles, game directories, logs, and session state. If the server is placed temporarily on node1, the exact server revision, JAR, world, config, ports, logs, and return path remain pinned while the independent clients remain controlled locally.
- EnVisione GitHub identity, repository access, commit and tag signing, authoritative remotes, phase milestone, and existing issue state are verified before any remote mutation.

**Implementation scope**

- `CORE-REQ-013` and `P006-TASK-001` bind Phase 006 to the full Phase 005 merge, exact issue 32 local proof, current support-line ancestry, configured providers, isolated fixtures, and mandatory DEC-007 runtime topology.
- `CORE-REQ-013` and `P006-TASK-002` inventory every named ingress, authoritative owner, identity, durable transition, projection, diagnostic, lifecycle state, failure, and terminal result.
- `CORE-REQ-013` and `P006-TASK-003` close all protocol, sender, session, route, permission, replay, readiness, maintenance, capability, response-correlation, and client-convergence boundaries.
- `CORE-REQ-013`, `P006-TASK-004`, `P006-TASK-005`, and `P006-TASK-006` exercise Server Shops, Player Shops, every configured economy provider and payment source, physical currency, escrow, Auction House, Bazaar, ATM, and claims through success, rejection, concurrency, partial failure, and recovery.
- `CORE-REQ-013`, `P006-TASK-007`, and `P006-TASK-008` execute the complete reload, lifecycle, shutdown, restart, reconnect, replay, controlled-multiplayer, fault-injection, issue 32 ownership-isolation, recovery, and conservation matrices.
- `CORE-REQ-013`, `P006-TASK-009`, `P006-TASK-010`, `P006-TASK-011`, and `P006-TASK-012` file every verified defect before repair, close the affected evidence graph, verify the final branch, merge through GitHub, rerun the exact merge, create the signed tag, and produce the clean Phase 007 handoff.

**Execution order**

1. `P006-TASK-001` executes `CORE-REQ-013` by freezing the full Phase 005 merged input, issue 32 local proof, exact branch ancestry, environment topology, support-line impact, and fixture catalog.
2. `P006-TASK-002` executes `CORE-REQ-013` by mapping every ingress, authoritative service call, durable transition, response, client projection, lifecycle state, configuration generation, and recovery identity.
3. `P006-TASK-003` executes `CORE-REQ-013` by closing protocol, session, correlation, replay, capability, readiness, and diagnostic boundaries before value-bearing workflows run.
4. `P006-TASK-004` executes `CORE-REQ-013` by proving Server Shop and Player Shop composition across catalog, offers, stock, inventory, every applicable payment source, escrow, delivery, claims, and client responses.
5. `P006-TASK-005` executes `CORE-REQ-013` by proving every configured economy provider, internal wallet, physical currency, ATM, cash-claim, provider-lifecycle, and configuration-generation composition.
6. `P006-TASK-006` executes `CORE-REQ-013` by proving Auction House and Bazaar listing, order, custody, hold, settlement, fee, expiry, module control, page, profile, and claim composition.
7. `P006-TASK-007` executes `CORE-REQ-013` through delayed readiness, module lifecycle, valid and invalid reload, shutdown, restart, reconnect, replay, concurrency, diagnostics, and locally controlled multiplayer across the completed workflows.
8. `P006-TASK-008` executes `CORE-REQ-013` by injecting bounded failures at every pre-commit, custody, durable-commit, delivery, response, persistence, provider, scheduler, reload, shutdown, and reconnect boundary and reconciling recovery and conservation.
9. `P006-TASK-009` executes `CORE-REQ-009` and `CORE-REQ-013` by immediately deduplicating and filing every verified defect before repair, applying the smallest authoritative-owner correction, and rerunning the complete affected blast radius.
10. `P006-TASK-010` executes `CORE-REQ-013` by running the complete branch-revision verification ladder and all local integration and controlled-multiplayer matrices after the final material repair.
11. `P006-TASK-011` executes `CORE-REQ-013` by reconciling tracked documentation, committing and pushing the verified Forge phase work, completing review and checks, merging the phase pull request into `1.20.1`, and verifying the authoritative remote merge. Any proven NeoForge repair completes its independent line-specific integration first.
12. `P006-TASK-012` executes `CORE-REQ-013` by rerunning every required Phase 006 matrix at the exact merged revision, closing phase findings, creating and pushing the signed annotated Phase 006 tag, auditing the completion packet, and handing the exact clean state to Phase 007.

Tasks 001 through 003 are ordered gates. Tasks 004 through 006 may prepare immutable fixtures in parallel only after Task 003 freezes shared protocol, identity, and readiness semantics. Tests that share a world, journal, provider, port, player profile, or configuration directory run serially or on isolated copies. Tasks 007 and 008 consume all completed domain matrices. Task 009 activates as soon as a finding is verified and precedes the first repair edit for that finding. Tasks 010 through 012 are strictly ordered after the last material repair.

**Required evidence**

- Exact Phase 005 Forge merge, tag, completion-packet identity, issue 32 corpus identity, starting Phase 006 ancestry, toolchain, mod list, configuration, fixture hashes, and environment topology.
- A complete cross-component call, state-transition, ownership, lifecycle, and failure matrix with stable request, transaction, custody, claim, session, listing, order, configuration, and recovery identities.
- Focused integration and negative tests for every matrix row and every repaired defect.
- Dedicated-server and client smoke logs covering startup, delayed readiness, module states, Server Shops, Player Shops, every economy provider and payment source, escrow, Auction House, Bazaar, ATM, claims, valid and invalid reload, shutdown, restart, reconnect, and diagnostics.
- Mandatory local multiple-client evidence from the DEC-007 environment, including finite and infinite stock, concurrent buyers and sellers, market races, provider faults, claims, dropped responses, restart, reconnect, and stale client state.
- Issue 32 successor integration evidence proving player login, logout, transaction, receipt, claim, restart, reconnect, and recovery composition does not alter unrelated or modded player state or recreate an unusable state.
- Conservation reports for every value-bearing success, rejection, race, retry, and injected partial failure.
- Duplicate-search and issue timestamps that precede every repair, plus failing regressions, repair commits, review, checks, merge records, evidence invalidation, and rerun results.
- Exact Forge pull request merge into `1.20.1`, fresh remote containment, complete merged-revision reruns, and signed annotated tag `phase-006-backend-integration` on the verified merge commit.

**Exit criteria**

- Every named subsystem, provider, lifecycle combination, value transition, diagnostic, and failure class has one exact source-backed row and passing local evidence at the final merged revision.
- Server authority, stable UUID idempotency, checked integer value, custody conservation, durable accessible claims, fail-closed readiness, compatibility, ownership isolation, and zero silent loss hold across all matrix rows.
- Disabled, frozen, draining, recovering, and unavailable modules do not advertise unusable mutation paths. Enabled and ready modules become usable from authoritative server truth without a relog. Claims remain reachable independent of module mutation availability.
- Reload, shutdown, restart, reconnect, replay, dropped response, concurrency, provider failure, full inventory, and scheduler races preserve committed truth and do not duplicate, lose, corrupt, or silently strand value.
- The locally controlled DEC-007 server and at least two independent clients complete every required multiplayer row. A single client, integrated server, mock, source scan, synthetic packet-only test, or unit test cannot satisfy this gate.
- Every verified defect has a canonical issue or confidential record created before repair, a failing regression where safe and feasible, a minimal line-specific correction, required review, merged repair, and exact-revision blast-radius rerun.
- The Phase 006 Forge pull request is merged into `1.20.1` through GitHub, required checks pass, `origin/1.20.1` contains the merge, and any separately proven NeoForge repair is merged and verified on `1.21.1`.
- Every Phase 006 local matrix reruns clean at the exact Forge merge revision, the signed annotated tag `phase-006-backend-integration` targets that merge and verifies as EnVisione, no phase-owned defect or unresolved mandatory gate remains, and the Phase 007 handoff is complete.
- No known mandatory phase-owned defect remains.

## Requirement and Source Traceability

| Contract or evidence ID | Phase interpretation | Owned tasks | Acceptance proof |
|---|---|---|---|
| `CORE-REQ-013` | Close all named backend integration and failure behavior on supported lines | `P006-TASK-002` through `P006-TASK-012` | Complete exact-revision interface, lifecycle, domain, fault, runtime, multiplayer, merge, and tag packets |
| `CORE-REQ-009` and `EXT-005` | Search for duplicates and create the canonical record before the first repair edit for every verified defect | `P006-TASK-001`, `P006-TASK-009`, `P006-TASK-011`, `P006-TASK-012` | Duplicate queries, issue or advisory timestamps, repair and merge links, closed finding register |
| `CORE-REQ-010` and `CORE-REQ-011` | Preserve packet, command, permission, path, privacy, and diagnostic boundaries while composing workflows | `P006-TASK-002`, `P006-TASK-003`, `P006-TASK-007` through `P006-TASK-012` | Reopened affected audit rows, negative tests, runtime transcripts, exact merged reruns |
| `CORE-REQ-012` and `CORE-REQ-014` | Consume the full persistence, recovery, issue 32, idempotency, and conservation contract | `P006-TASK-001`, `P006-TASK-004` through `P006-TASK-010`, `P006-TASK-012` | Lineage, restart, reconnect, fault, ownership-isolation, claim, and conservation evidence |
| `DEC-007` and `SRC-014` | Controlled multiplayer is normal mandatory local verification | `P006-TASK-001`, `P006-TASK-004` through `P006-TASK-010`, `P006-TASK-012` | Pinned 64 GB workstation topology or authorized node1 server fallback, multiple-client logs, state captures, and conservation |
| `SRC-002` and `SRC-003` | Preserve implemented 3.0 and 3.1 transaction, market, UI, protocol, recovery, and compatibility contracts | `P006-TASK-002` through `P006-TASK-008` | Source trace plus cross-domain regression and runtime matrix |
| `SRC-009`, `SRC-010`, `SRC-011`, and `SRC-013` | Bind implementation, support lines, documentation, repository rules, and verification to exact revisions | All tasks | Ancestry, source inventory, required commands, documentation review, JAR and diff inspection |

## Inputs and Upstream Contracts

| Input or contract | Provider | Required state | Validation | Failure behavior |
|---|---|---|---|---|
| Integrated Forge revision | `CORE-PHASE-005` | Latest approved `1.20.1` contains the full Phase 005 merge and all earlier merges | Pull request state, required checks, fresh fetch, merge containment, Phase 005 tag target and signature | Stop Phase 006 entry. Complete the upstream merge and exact-revision evidence before branching |
| Phase 005 completion packet | `CORE-PHASE-005` | Persistence inventory, schemas, migrations, issue 32 corpus, ownership map, recovery, replay, claims, receipts, and conservation are complete and clean | Packet manifest, exact revisions, fixture hashes, invalidation log, no unresolved mandatory gate | Stop unsafe integration testing and correct or rerun the missing upstream gate |
| Security and command closure | `CORE-PHASE-004` | Network, command, permission, path, codec, privacy, dependency, and packaging findings that affect integration are closed | Completion packet and revision-bound regression links | Reopen affected rows and route any verified defect through `CORE-REQ-009` |
| Finite-stock multiplayer proof | `CORE-PHASE-002` | Issue 34 finite-stock money purchase and issue 32 player-state proof remain valid on consumed ancestry | Completion packet, later invalidation record, local runtime and conservation evidence | Rerun the invalidated local proof before relying on the contract |
| Network contract | Packet registration, codecs, handlers, sessions, client trackers | Stable version, direction, bounds, authenticated sender, route nonce, request UUID, response correlation, and replay | Registration inventory, codec tests, wrong-side and malformed-input tests | Reject before mutation and return one bounded stable result or documented protocol disconnect |
| Runtime readiness | Escrow runtime, module access, capability projection, session and scheduler owners | Mutation only after required durable owners are ready; claims and diagnostics remain reachable as integrity allows | Startup timeline, transitions, capability snapshots, sessions, scheduler and shutdown traces | Fail closed, preserve safe claim and diagnostic routes, publish actionable server truth |
| Configuration generations | Common, escrow, Auction House, Bazaar, client, Server Shop, and Player Shop owners | Complete validated snapshots, last-known-good fallback, and captured semantics for in-flight work | Valid, invalid, semantic-change, and concurrent reload tests | Reject invalid candidate, retain prior snapshot, never reinterpret prepared or committed work |
| Economy authority | Every discovered provider, internal wallet, payment-source selector, and physical-currency service | Checked minor units, explicit provider result, stable configuration lease, receipt reconciliation, and no silent fallback | Provider inventory, adapter contract tests, fault matrix, balances and liabilities snapshot | Reject before unsafe custody or retain exact recoverable custody and resolve once under the original identity |
| Local runtime environment | `DEC-007` | Isolated dedicated server and at least two independent clients at exact revision | Host, memory, process, port, profile, game directory, world, config, mod, JAR, and log manifest | Repair or reschedule the local harness, or move only the isolated server to node1 and rerun the full affected matrix |
| GitHub synchronization | `EXT-005` | Authenticated EnVisione access to the authoritative repository and phase records | Identity, remote, issue, milestone, branch, pull request, check, merge, and tag readback | Preserve local evidence and stop only the affected remote operation until access is restored |

## Outputs and Downstream Contracts

| Output or contract | Consumer | Guaranteed state | Compatibility or versioning | Evidence |
|---|---|---|---|---|
| Exact Phase 006 Forge merge | `CORE-PHASE-007` | Every Phase 006 repair and documentation change is merged into `1.20.1` with no stacked branch | Forge stays on Minecraft 1.20.1, Java 17, and pinned Forge/toolchain versions | Pull request, checks, review, merge commit, fresh remote containment |
| Signed Phase 006 tag | Sequential phase governance | `phase-006-backend-integration` identifies the exact verified Forge merge | Internal phase evidence only, not a public product release | Annotated tag object, EnVisione signature, exact target, remote presence |
| Integration inventory | `CORE-PHASE-007` | Every named ingress, owner, transition, terminal result, diagnostic, and failure class is classified | Bound to exact merged support-line revisions | Matrix hash, source references, source-to-row reconciliation |
| Protocol and readiness proof | Final verification | Requests cannot bypass identity, permission, route, readiness, module, replay, bounds, or server authority and client projections converge | Protocol or schema changes carry explicit compatible versioning evidence | Codec, handler, session, capability, reload, reconnect, and negative results |
| Workflow conservation packet | Final candidate verification | Each shop, market, provider, ATM, currency, and claim workflow balances all sources, custody, destinations, fees, stock, and claims | Checked minor units, exact items, stable UUIDs, and lineage remain authoritative | Pre-state, durable intermediate state, terminal state, equations, and reconciliation |
| Issue 32 integration packet | Final persistence and product audits | Player lifecycle and cross-component workflows preserve owned and unrelated player state through failure and recovery | Uses the exact Phase 005 corpus and records every later invalidation | Seed and fixture hashes, field diffs, receipt and slot proof, restart and reconnect traces |
| Local multiplayer packet | Final runtime audit | Required multiple-client success, contention, failure, restart, reconnect, and stale-state rows pass under DEC-007 | Environment identities are revision-bound and reproducible | Server and client logs, request identities, snapshots, timing, conservation |
| Failure-injection packet | Final security, persistence, and integration audits | Every injected interruption has an expected durable boundary, restart result, retry result, and recovery route | Fixture and fault-point identities remain stable for rerun | Fault manifest, logs, state diffs, recovery results |
| Verified documentation | Operators and `CORE-PHASE-007` | Lifecycle, reload, diagnostics, failure, restart, reconnect, and recovery behavior describes merged implementation only | Existing commands, identifiers, configuration keys, and layout remain stable unless verified change requires updates | Documentation diff, links, source-to-doc and operator-procedure review |
| Phase completion packet | `CORE-PHASE-007` | All exact revisions, matrices, issues, invalidations, merged reruns, tag, and next-phase entry facts are complete with no unresolved mandatory gate | Any later material change invalidates named rows and requires exact-revision rerun | Packet manifest, readback audit, handoff acceptance |

## Subsystem Interface and State-Flow Matrix

Execution expands every row into exact source references, fixtures, success cases, deterministic rejection cases, injected failures, recovery routes, local multiplayer steps, and retained evidence. A row is incomplete if it proves only request acceptance or a UI message without durable truth and the client-visible terminal result.

| Flow | Ingress and presentation | Authoritative orchestration | Durable or external state | Required terminal states | Principal failure states |
|---|---|---|---|---|---|
| Server Shop money purchase | Shop screen, quote, buy request, correlated response | Packet handler, catalog and quote validation, stock reservation, transaction service, selected payment source, escrow | Stock, provider or wallet balance, ledger, custody, history, receipt, claim | Rejected untouched, committed and delivered once, or committed with durable claim | Stale quote, finite-stock race, insufficient funds, provider fault, full inventory, dropped response, replay, restart |
| Server Shop sell | Shop screen, sell request, response tracker | Exact inventory proof, sell service, stock and escrow commit, payout provider | Exact item custody, stock, payout, ledger, claim, history | Rejected untouched, committed payout once, or recoverable custody and claim | Inventory change, stock conflict, payout failure, disconnect, replay, crash cut |
| Server Shop barter and cart | Offer or cart UI and correlated request | Offer and cart validation, canonical fingerprint, item and money fanout, escrow transaction | Inputs, outputs, stock, usage limits, custody, claims, replay receipts | Entire request rejected, atomically committed, or fully recoverable | Mixed invalid row, duplicate line, stale cart, partial delivery, response loss, restart |
| Player Shop workflows | Player Shop screens, buy, acquire, sell, barter, and link requests | Block, owner, offer, storage, distance, revision, and receipt validation through authoritative transaction services | Shop inventory or receipt-backed adapter, buyer and owner value, settlement, custody, claim | Rejected untouched, one atomic settlement, or durable recovery state | Block removed, owner or offer changed, storage changed, capacity conflict, disconnect, replay, restart |
| Economy providers and wallet | Any value-bearing domain request | Provider inventory, payment-source selector, internal wallet and provider adapters, transaction guard | Provider balance, internal balance, ledger, receipt, configuration generation | Exactly one debit and credit, deterministic denial, or one recoverable compensation or claim route | Unavailable provider, false success, exception, timeout, overflow, config switch, duplicate call |
| Physical currency | Shop, market, ATM, deposit, withdrawal, or cash-claim request | Currency validation, mint protection, denomination selection, physical funding, delivery planner | Inventory stacks, mint state, custody, wallet, cash claim, receipts | Valid cash consumed or minted once, value credited or debited once, or rejected untouched | Foreign or invalid cash, inventory race, partial custody, mint failure, duplicate mint, full inventory, restart |
| ATM deposit | ATM screen and deposit or recovery request | ATM service, currency validation, provider and escrow deposit workflow | Inventory, deposit intent, custody, wallet, receipt, claim or recovery queue | Applied once, rejected untouched, or recoverable under original identity | Invalid cash, provider fault, response loss, disconnect, crash before or after commit |
| ATM withdrawal and cash claim | ATM screen, withdrawal and claim collection | Selection plan, withdrawal escrow, currency minting, claim and delivery services | Wallet, minted cash custody, withdrawal commit, cash claim, receipt | Cash delivered once, exact claim retained, or debit not applied | Insufficient funds, full inventory, config generation change, mint failure, restart, replay |
| Auction House | Navigation, pages, create, bid, buy-now, cancel, claim | Access policy, sessions, Auction House state machine, escrow lifecycle, scheduler | Listing, item custody, bid holds, settlement, fees, expiry, receipts, claims | Deterministic rejection, one lifecycle transition, or recoverable settlement and claim | Disabled module, stale listing, self-action, outbid, provider fault, expiry race, disconnect, restart |
| Bazaar | Navigation, pages, register, buy, sell, order, cancel, claim | Access policy, sessions, product runtime, order book, matcher, escrow lifecycle | Product revision, order, holds, exact lots, fills, fees, receipts, claims | Deterministic rejection, exact order or cancel, conserved fill, or recoverable claims | Unready module, stale product, FOK rejection, self-trade, provider fault, match race, expiry, restart |
| Market capability and navigation | Main screen, capability request, module open, page and profile query | Capability projection, module access policy, session registry, server-selected route | Module control, readiness, config revision, route nonce, session | Allowed route opens from server truth, denied route is actionable, stale client converges | Delayed readiness, stale snapshot, transition, invalid nonce, reconnect, version mismatch |
| Claims | Claim center, module claim routes, collection request | Claim service and processor independent of module mutation availability | Item and money claims, custody, delivery receipt, remainder | Delivered once, exact remainder retained, or safely unavailable only for integrity recovery | Disabled module, full inventory, provider fault, disconnect, replay, restart |
| Configuration and catalogs | Operator reload, file watcher or listener, runtime publication | Complete parse, validation, generation publication, last-known-good owner | Common, escrow, Auction House, Bazaar, client, Server Shop, Player Shop snapshots and catalogs | Valid complete snapshot published; invalid candidate rejected with prior state active | Malformed value, missing registry ID, provider change, module change, concurrent transaction, partial publication |
| Server lifecycle and diagnostics | Dedicated start, tick, status, audit, stop, restart | Runtime binding, journal replay, recovery, readiness, schedulers, sessions, diagnostics | Journal lineage, checkpoints, SavedData, catalogs, controls, queued recovery | Ready only after verification, diagnostics stay non-mutating, shutdown quiesces, restart converges | Delayed binding, corrupt state, incomplete work, scheduler overlap, stop during commit, misleading status |
| Client reconnect and controlled multiplayer | Logout, disconnect, reconnect, stale UI, concurrent client actions | Server sessions, route nonces, receipts, authoritative snapshots, response trackers | Durable transactions and claims, ephemeral sessions and pending client state | Fresh session receives truth, safe replay returns original result, concurrent requests serialize | Stale nonce, orphaned pending UI, duplicate packet, last-stock and order races, delayed response |

## Protocol, Readiness, and Identity Contract

Every client-originating operation follows this order before mutation:

1. Decode fixed fields, strings, collections, NBT, enums, quantities, prices, and total bytes within explicit bounds. Reject wrong protocol and wrong direction without constructing authoritative state from the payload.
2. Obtain the player and server context from the network framework. Never trust a client-authored UUID, name, owner, balance, permission, price, stock, provider, module state, or completion flag.
3. Validate the server-owned session and route nonce for scoped navigation. Reconnect creates new ephemeral authority while durable request receipts remain valid.
4. Validate permissions, rate or work bounds, target existence, ownership, dimension and distance where applicable, registry identity, quantities, checked arithmetic, and request semantics.
5. Resolve the stable request UUID and immutable fingerprint. An identical replay returns the prior terminal result without another rate charge or value effect. A changed fingerprint under the same UUID fails closed.
6. Check runtime readiness, maintenance, recovery, module control, and the complete configuration generation required by the operation. Client capability snapshots never override server truth.
7. Revalidate quotes, listings, orders, stock, inventory, provider availability, storage receipts, and custody immediately before prepare on the logical server.
8. Execute prepare, durable custody or reservation, commit, delivery, compensation, and claims through the owning authoritative service. No adapter synthesizes a durable result.
9. Persist the receipt and correlation evidence needed for restart and replay at the documented durable boundary.
10. Send one bounded correlated result. Response loss is resolved by replay or fresh snapshot, not by blind mutation retry.
11. Publish updated capabilities, balances, stock, listings, orders, and claims from server truth. Client pending state times out and reconciles through a fresh server snapshot.

Stable identities include request UUID, immutable request fingerprint, authoritative transaction or commit UUID, session or route nonce, listing or offer revision, product and order revision, configuration generation, claim identity, receipt identity, exact delivery-slot proof, and recovery lineage. Execution records creation owner, required propagation, persistence boundary, replay behavior, conflict behavior, privacy class, and tests for each identity.

Readiness distinguishes every real state exposed by the owning services, including starting, recovering, maintenance, ready, frozen, draining, disabled, and stopping where supported. Unsupported conceptual labels map to the actual state and do not create a competing state machine.

- Mutation requires every owning service to be ready and enabled.
- Safe diagnostics and claims remain reachable to the extent integrity permits during maintenance, recovery, freeze, drain, and disable states.
- Enabling a module publishes a new capability only after its runtime is ready and must not require a relog.
- Disabling or draining stops new work, preserves or settles durable in-flight work, removes unusable mutation navigation, and never hides claims.
- Shutdown rejects new work after the quiesce gate, persists or records in-flight work at a durable boundary, stops schedulers safely, and invalidates ephemeral sessions.
- A valid reload publishes one complete generation. Prepared and committed work retains captured semantics. An invalid reload preserves the entire previous valid generation.

## Work Packages

| Task ID | Requirement IDs | Work | Inputs and dependencies | Outputs | Affected components or interfaces | Verification |
|---|---|---|---|---|---|---|
| `P006-TASK-001` | `CORE-REQ-013` | Freeze full Phase 005 merge, issue 32 proof, branch ancestry, support-line impact, runtime topology, provider inventory, and immutable fixtures | Phase 005 packet, Phase 004 packet, Phase 002 issue 32 and 34 packets, `DEC-007`, `EXT-005` | Exact entry manifest and fixture catalog | Git, GitHub, toolchains, configs, mods, providers, worlds, clients, evidence storage | Remote ancestry, tag signature, packet readback, environment launch and hash audit |
| `P006-TASK-002` | `CORE-REQ-013` | Build the complete interface, state-flow, identity, lifecycle, diagnostic, and failure matrix | Task 001, `SRC-002`, `SRC-003`, `SRC-011` | One traceable row per ingress-to-terminal path | Packets, commands, sessions, shops, providers, escrow, markets, ATM, claims, config, schedulers, persistence | Source-to-registration and terminal-to-owner reconciliation, zero unclassified path |
| `P006-TASK-003` | `CORE-REQ-013` | Audit protocol, correlation, session, replay, capability, readiness, and diagnostic gates | Tasks 001 and 002, Phase 004 security evidence | Closed protocol and readiness matrix | `ShopPackets`, codecs, handlers, response trackers, capability and session owners, diagnostics | Bounds, wrong side, sender, nonce, permission, replay, stale state, delayed readiness, privacy tests |
| `P006-TASK-004` | `CORE-REQ-013` | Exercise Server Shop and Player Shop composition for money, item, barter, cart, finite and infinite stock, and storage-backed workflows | Task 003 and Phase 005 invariants | Shop workflow, recovery, and conservation packet | Catalogs, offers, stock, player shops, storage adapters, transactions, providers, escrow, delivery, claims | Unit, integration, dedicated-server, client, multiple-client contention, restart and reconnect |
| `P006-TASK-005` | `CORE-REQ-013` | Exercise every configured economy provider and payment source, physical cash, ATM, cash claims, provider lifecycle, and configuration generations | Task 003, provider inventory, Phase 005 conservation | Provider and ATM lifecycle packet | Providers, wallet, currency, mint, deposit, withdrawal, escrow, claims, configuration | Provider denial, exception, timeout, false-success reconciliation, config reload, full inventory, replay, restart |
| `P006-TASK-006` | `CORE-REQ-013` | Exercise Auction House, Bazaar, market sessions, pages, profiles, module controls, holds, fills, fees, expiry, settlement, and claims | Task 003 and market controls | Market lifecycle and conservation packet | Auction and Bazaar books, matcher, schedulers, escrow, capabilities, sessions, claim collection | Create, bid, buy, cancel, order, match, expire, disable, race, provider fault, disconnect, restart |
| `P006-TASK-007` | `CORE-REQ-013` | Run cross-domain readiness, lifecycle, reload, shutdown, restart, reconnect, replay, diagnostic, and controlled-multiplayer matrix | Tasks 004 through 006 and `DEC-007` | Lifecycle timeline, client convergence packet, local multiplayer packet | Runtime owners, configs, catalogs, modules, sessions, schedulers, every networked domain | Default workstation server plus at least two clients, or authorized node1 server fallback, with exact state snapshots |
| `P006-TASK-008` | `CORE-REQ-013` | Inject bounded failures at every durable boundary and reconcile recovery, issue 32 ownership isolation, idempotency, claims, and conservation | Tasks 004 through 007 and Phase 005 fault contract | Fault manifest, state diffs, recovery and conservation report | Prepare, custody, commit, delivery, receipt, provider, persistence, reload, scheduler, shutdown and reconnect boundaries | Crash cuts, restart twice, reconnect, replay, claim collection, sentinel diff, repeated recovery |
| `P006-TASK-009` | `CORE-REQ-009`, `CORE-REQ-013` | Deduplicate and file every verified defect before repair, implement the smallest line-specific correction, and rerun its blast radius | Any verified Task 002 through Task 008 finding and `EXT-005` | Canonical issue or advisory, regression, repair, review and exact rerun evidence | Authoritative owning components and only proven affected support lines | Timestamped issue-before-edit proof, failing before where safe, passing after, upstream invalidation reruns |
| `P006-TASK-010` | `CORE-REQ-013` | Execute the complete final branch-revision verification ladder and every local matrix after the last material repair | Tasks 001 through 009 | Clean branch verification packet | Focused tests, full tests, data, GameTests, build, server, clients, multiplayer, reload, restart, reconnect, JAR and diff | Every mandatory row passes at one unchanged phase-branch revision |
| `P006-TASK-011` | `CORE-REQ-013`, `CORE-REQ-017` input | Reconcile documentation, commit and push verified work, complete private review if the optional private review capability exists and all required checks, merge the Forge phase pull request into `1.20.1`, and verify remote containment | Task 010 and `EXT-005` | Exact Forge merge and any independent NeoForge merge, synchronized issues and documentation | Pull requests, issues, milestones, Project, README, technical and focused docs | Source-to-doc review, green checks, review result or recorded capability absence, GitHub merge records, fresh fetch and ancestry |
| `P006-TASK-012` | `CORE-REQ-013` | Rerun every required local matrix at the exact merged revision, close findings, create and push the signed phase tag, audit the packet, and hand off to Phase 007 | Task 011 | Full Phase 006 completion packet and accepted Phase 007 entry | Exact merged source, artifacts, evidence, issues, tag, downstream invalidation graph | Complete merged-revision rerun, tag target and signature, remote tag, no defect or unresolved mandatory gate |

### Work Package Controls

- Task 002 traces actual implementation. When a conceptual boundary has no separate class, name the real owning service instead of inventing an abstraction.
- Every configured or discoverable economy provider receives an inventory row with selection precedence, availability, result semantics, timeout or exception behavior, receipt or reconciliation capability, configuration generation, retry rule, and supported workflow coverage. A source-backed absence is recorded, not assumed.
- Every value-bearing case captures pre-state, durable intermediate state, terminal state, exact identities, and a conservation equation. A UI message, command success, empty exception log, or balance-only snapshot is insufficient.
- Provider fault drivers model only the real interface behaviors and remain test-scoped. No production-accessible bypass, arbitrary mutation hook, or hidden fault toggle may enter the built JAR.
- A suspected finding remains under investigation until reproducible. Once verified as repository-owned, Task 009 blocks the first repair edit until duplicate search and canonical issue or confidential advisory creation complete.
- Material changes invalidate every reachable Phase 004, Phase 005, and Phase 006 row. The invalidation graph names and reruns those rows before Task 010 or Task 012 may pass.
- Documentation and evidence contain synthetic or sanitized player state only. Raw private NBT, credentials, secrets, unbounded logs, and exploit-enabling details use neither public GitHub nor tracked documentation.

## Architecture and Implementation Boundaries

### Authority and Dependency Direction

- Client screens, navigation models, response trackers, and drafts request actions and render snapshots only. They never decide money, stock, ownership, permission, provider, module state, custody, claim completion, transaction success, or recovery.
- Packet and command handlers validate transport and actor context, then call authoritative server services. They do not implement duplicate transaction state machines or write persistent value directly.
- Shop, economy, ATM, Auction House, and Bazaar services own domain validation and use existing escrow and persistence interfaces. They do not bypass recovery, maintenance, or readiness.
- Escrow owns prepare, custody, commit, delivery, compensation, durable claims, journal lineage, replay, and recovery. Domain services interpret a durable result but never synthesize one.
- Persistence and configuration owners publish validated snapshots and durable revisions. Client projection, page, profile, status, and diagnostic reads are non-mutating.
- Schedulers run through the logical-server mutation permit with bounded work. Expiry and recovery serialize against bids, orders, cancellations, claims, reload, shutdown, and interactive actions.
- Common initialization remains free of client-only classes. Optional economy and storage integrations remain isolated behind runtime checks and their absence cannot crash the dedicated server.

### Concurrency and Performance

- Minecraft state mutation occurs on the logical server thread or through an existing explicitly thread-safe persistence boundary. Network decoding and asynchronous work enqueue authoritative mutation.
- Competing buyers, sellers, bidders, orders, claim collectors, reloads, schedulers, and shutdown use revisions, reservations, receipts, or mutation permits to produce one deterministic winner and safe losers.
- Packet, tick, scheduler, render, and claim work remains bounded. No phase repair adds filesystem I/O, registry discovery, full-player scans, or full-market scans to a hot path.
- Logs produce one bounded actionable event per workflow or recovery identity, not per-tick spam. They retain stable identifiers and state codes but exclude credentials, private NBT, and raw player data.

## Provider, Module, Reload, and Lifecycle Contract

| Lifecycle state or event | New mutations | In-flight durable work | Claims and recovery | Client and diagnostic projection | Required proof |
|---|---|---|---|---|---|
| Server starting | Rejected until owners bind and verify | Durable replay may proceed | Recovery begins from preserved lineage | Starting or unavailable, no usable mutation route | Delayed binding and startup timeline |
| Recovering or maintenance | Rejected fail closed | Recovery contract alone advances work | Safe diagnostics and claim visibility remain available as integrity permits | Actionable server-owned state and bounded reason | Interrupted and corrupt fixtures, recovery and claim results |
| Module enabled but not ready | Rejected | Existing durable work remains owned | Claims remain reachable | Not advertised as usable | Enable-before-readiness and stale-client tests |
| Module ready | Allowed after normal validation | Runs through durable services | Claims collect normally | Capability revision enables route without relog | Two-client capability convergence |
| Module frozen | Rejected | Stops at documented safe boundary | Claims remain reachable | Unavailable with reason | Freeze during active workflow |
| Module draining | Rejected | Committed work settles or enters recovery | Claims remain reachable | No new mutation navigation | Drain, scheduler and shutdown test |
| Module disabled | Rejected | No hidden abandonment | Claims remain reachable independently | Removed or marked unavailable | Disable, stale action, reconnect and claim collection |
| Provider unavailable | Rejected before unsafe custody, or exact custody retained for recovery | No repeated debit or credit | Compensation or claim resolves once | Stable provider-unavailable or recovery result | Fault, replay, restart and reconciliation |
| Provider restored | New work uses current validated configuration | Old identities resolve prior state before new request | Claims still collect exactly once | Fresh server snapshot | Restoration, replay and new transaction |
| Valid configuration reload | New work uses the published complete generation | Prepared and committed work keeps captured semantics | Recovery reads the captured generation or compatible durable form | One new revision published atomically | Concurrent requests before, during and after reload |
| Invalid configuration reload | Continues using prior valid generation | Unchanged | Unchanged and reachable | Actionable rejection, no partial capability change | Malformed, missing registry and incompatible provider fixtures |
| Server stopping | Rejected after quiesce | Finishes to a durable boundary or records recovery | Claims and lineage persist | Sessions invalidated and diagnostics stop cleanly | Stop at every durable boundary and restart twice |

## Controlled Multiplayer Contract

Controlled multiplayer is a normal mandatory verification surface under `DEC-007`.

- Default topology is one isolated Forge 1.20.1 dedicated-server process and at least two independent Forge client JVMs on the 64 GB workstation. Each client has its own profile, game directory, logs, session state, request tracker, window, and captured identity.
- The authorized fallback moves only the temporary isolated-server role to the 96 GB node1 host. Record the exact server JAR, source revision, Java, mods, configuration, world hash, address, ports, memory, start command, logs, and secure copy or cleanup procedure. Clients remain independent and controlled by the test operator.
- Every campaign records host roles, memory allocations, Java version, Forge version, FutureShops revision and JAR hash, dependency hashes, configuration generations, fixture hash, client identities, request UUIDs, route nonces, timing controls, logs, balances, inventories, stock, listings, orders, custody, claims, receipts, journal lineage, and conservation totals.
- The matrix covers at minimum concurrent finite-stock purchase, infinite-stock control, Player Shop last-item and storage mutation races, provider interruption, ATM deposit and withdrawal, Auction House bid or buy against expiry, Bazaar match against cancel or expiry, module disable while two clients hold stale screens, claim collection with capacity changes, response loss, server stop at durable boundaries, restart, reconnect, replay, and fresh-snapshot convergence.
- Issue 32 successor rows use the Phase 005 corpus and sentinels during player login, logout, transactions, delivery, claim collection, restart, reconnect, and recovery. No FutureShops action may rewrite unrelated or modded player data or make the player unusable.
- Each contention row declares the legal winner set and exact terminal state before execution. Nondeterministic ordering may choose among legal winners, but all outcomes must conserve value and converge to one auditable state.
- A failed launch, insufficient memory, port conflict, display failure, authentication failure, lost session, or host timing drift is a harness failure. Repair or reschedule the harness and rerun the affected matrix. Do not lower fidelity or omit the row.

## Failure, Recovery, and Edge Cases

| Scenario | Detection | Required behavior | Recovery or rollback | Regression proof |
|---|---|---|---|---|
| Malformed, oversized, wrong-side, or unauthenticated request | Codec and network context | Reject before allocation growth, private-state access, or mutation | None beyond bounded response or protocol disconnect | Boundary and fuzz corpus for every packet family changed or consumed |
| Stale route nonce or client snapshot | Session and capability revision | Reject mutation, expire stale pending state, and return current server route or capability | Open a fresh session and snapshot | Disconnect or module transition between preview and action |
| Same UUID and fingerprint replay | Durable receipt | Return original terminal result without another debit, stock change, delivery, rate charge, or event | Reproject current state | Duplicate before and after reconnect, restart, and compaction |
| Same UUID with changed semantics | Fingerprint mismatch | Fail closed and preserve original result | Use a new UUID only after original outcome is resolved | Conflicting replay across packet and command paths |
| Delayed escrow or module readiness | Runtime state | Reject mutation and avoid false navigation | Republish capability automatically when ready | Start with blocked recovery, release, and open without relog |
| Module changes while screen is open | Authoritative control revision | Reject stale action, preserve claims, and route client safely | Refresh capability and allowed screen | Two clients across freeze, drain, disable and reenable |
| Reload races prepared or committed work | Captured configuration generation | Use captured semantics or reject before custody, never mix generations | Resolve original request, then start new request on new generation | Fault at validation, prepare, commit, delivery and response |
| Finite-stock contention | Stock revision or reservation | Exactly one commit per available unit and unchanged losing actors | Fresh quote and new UUID after original resolution | At least two clients race the final unit, then restart |
| Player Shop block, owner, offer, or storage changes | Block, ownership, revision and receipt validation | Reject or use exact receipt-backed reservation without unmatched value movement | Release reservation or retain recoverable custody and claim | Break block, change offer and mutate storage during two-client request |
| Economy provider denial, exception, timeout, or inconsistent result | Provider result and receipt reconciliation | Reject before commit or retain exact custody for one compensation, recovery, or claim path | Restore provider and replay original UUID exactly once | Fault every provider operation and configuration transition |
| Inventory becomes full after commit | Delivery receipt | Keep committed item or cash in a durable claim | Collect after capacity is available | Fill final slot between prepare and delivery |
| Client disconnects or response is lost | Connection state and durable receipt | Continue or recover server-owned work, never infer rollback from disconnect | Reconnect, replay original UUID, or query authoritative state | Disconnect at every durable boundary with two clients |
| Server stops before durable prepare | No journaled intent | No mutation persists | New request may start after clean restart | Stop immediately before prepare |
| Server stops after custody before commit | Durable intent and custody without commit decision | Resume or compensate exactly once | Automatic recovery from copied fixture | Crash after each persisted custody step and restart twice |
| Server stops after commit before delivery | Commit plus undelivered custody | Deliver once or retain durable claim | Reconnect or collect claim | Restart and repeat delivery attempt |
| Server stops after delivery before response | Delivery and terminal receipt | Replay success without redelivery | Reconnect and retry original UUID | Response-loss crash cut and fresh client snapshot |
| Auction action races expiry or buy-now | Listing revision, scheduler and server time | One serialized lifecycle result and exact release of losing holds | Recovery settles receipts and claims | Two clients at scheduler boundary, restart and replay |
| Bazaar match races cancel or expiry | Order revisions and durable match receipt | One valid transition per lot and hold with exact conservation | Resume matcher or cancellation from receipt | Opposing clients at scheduler boundary, restart and replay |
| Claim collection is retried or partially delivered | Claim receipt and remainder | Deliver each unit or amount at most once and retain exact remainder | Retry same claim after capacity or provider recovery | Full inventory, partial capacity, disconnect, restart and repeated collection |
| Issue 32 sentinel or ownership mismatch | Field-level and exact-item semantic diff | Stop mutation, preserve full player and world evidence, and do not rewrite ambiguous state | Restore one complete matching fixture and repair only FutureShops-owned boundary | Modded item, unrelated NBT, ambiguous slots, login, logout, restart and reconnect |
| Recovery lineage or conservation mismatch | Snapshot and equation verifier | Enter maintenance, stop mutation, and preserve all evidence | Restore one complete matching backup or use verified recovery | Tampered lineage, missing leg and duplicate leg fixtures |
| Optional provider or storage integration absent | Runtime discovery | Dedicated server starts and dependent action is precisely unavailable | Install supported integration or use supported configured source | Start with each optional integration absent |
| Arithmetic boundary or negative value | Checked parser and arithmetic | Reject before mutation | None | Minimum, maximum, maximum plus one, overflow and forbidden negative matrix |
| Scheduler repeats after restart | Durable expiry or recovery receipt | Repeat is idempotent and bounded | Resume from recorded cursor | Restart around expiry, recovery and checkpoint schedule |

Unexpected maintenance, conservation mismatch, duplicate terminal effect, partial catalog publication, unusable player state, inaccessible claim, unknown provider outcome, or ownership-isolation failure stops mutation testing. Preserve the complete world copy, FutureShops durable state, player data, configuration, exact binaries, fixture hashes, process manifests, and sanitized logs. Recovery restores one complete matching snapshot or uses the already verified recovery contract. It never deletes selected state.

## Verification Matrix

| Requirement or task | Static or unit | Integration | Real workflow or runtime | Negative and recovery | Evidence artifact |
|---|---|---|---|---|---|
| `P006-TASK-001` | Manifest, hash and support-line assertions | Upstream packet and issue 32 linkage | Clean isolated server and client launch without mutation | Missing or stale upstream evidence fails entry | Exact entry manifest |
| `P006-TASK-002` | Source-to-interface coverage | Call paths, identity propagation and terminal owner reconciliation | One dry trace per workflow family | Unmapped ingress, transition, diagnostic or terminal state fails inventory | Integration matrix |
| `P006-TASK-003` | Codec, bounds, protocol, result code and client tracker tests | Handler-to-service, session, capability and response correlation | Login, open, stale route, delayed readiness, timeout, reconnect | Wrong side, spoofed identity, denial, replay conflict, private-state leak | Protocol and readiness packet |
| `P006-TASK-004` | Pricing, stock, offer, cart, inventory proof and replay tests | Shop-to-provider-to-escrow-to-claim composition | Server and Player Shop money, sell, barter and cart with multiple clients | Final-stock race, stale offer, storage mutation, full inventory, disconnect, restart | Shop workflow and conservation packet |
| `P006-TASK-005` | Provider, currency, denomination, mint, lease and ATM tests | Every provider and funding source through escrow and claims | ATM deposit, withdrawal, cash claim and every supported payment source | Unavailable or inconsistent provider, reload, invalid cash, full inventory, response loss | Provider and ATM packet |
| `P006-TASK-006` | Auction and Bazaar state-machine, fee, expiry and replay tests | Sessions, controls, escrow, pages, profiles, schedulers and claims | Create, bid, buy, cancel, order, match, expire and collect with multiple clients | Disable, stale revision, race, provider fault, disconnect and restart | Market lifecycle packet |
| `P006-TASK-007` | Lifecycle, configuration generation and client-state tests | Cross-domain reload, readiness and scheduler integration | Delay, enable, freeze, drain, disable, valid and invalid reload, stop, restart, reconnect | Stale screens, shutdown during work, diagnostics during failure | Lifecycle timeline and local multiplayer packet |
| `P006-TASK-008` | Fault-point completeness, issue 32 sentinels and invariant properties | Crash-cut, persistence, receipt and recovery integration | Restart copied worlds at every durable boundary and reconnect two clients | Partial persistence, corruption, ownership ambiguity, lost response, repeated recovery | Fault manifest, field diffs and recovery report |
| `P006-TASK-009` | Failing regression for each verified issue | Affected blast-radius suite and upstream invalidation reruns | Original or equivalent exact runtime reproduction | Before-and-after failure and confidential-routing proof | Issue and repair evidence packet |
| `P006-TASK-010` | Focused and complete `test` results | Applicable data and GameTests plus `build` | Dedicated server, client, complete controlled multiplayer, reload, restart and reconnect | Provider faults, recovery, replay, concurrency and sentinel cases | Exact branch-revision verification packet |
| `P006-TASK-011` | Documentation, link, diff and pull-request metadata checks | Review, required checks and source-to-doc reconciliation | Operator lifecycle and recovery rehearsal | Wrong base, failed check, unmerged repair or unsafe guidance prevents merge | Forge merge and documentation packet |
| `P006-TASK-012` | Complete matrix and packet audit | Issue, revision, artifact and ancestry reconciliation | Full unchanged merged-revision server, client and multiplayer rerun | Any new finding reopens Task 009, no omitted row or unresolved mandatory gate | Completion packet and verified signed tag |

### Fixtures and Environment

- Use clean, hash-identified current and recovered Forge worlds plus copies of the complete Phase 005 issue 32 corpus. Never mutate the only copy.
- Seed at least two normal players and one authorized operator, finite and infinite Server Shop stock, Player Shop inventory-backed offers, all configured provider and payment-source states, physical cash, Auction House listings and bids, Bazaar products and opposing orders, ATM denominations, item and money claims, and exact starting conservation totals.
- Include registry-only and exact-NBT items, modded item sentinels, unrelated player-NBT sentinels, empty and nearly full inventories, minimum and maximum values, insufficient funds, stale revisions, malformed bounded inputs, and valid and invalid configuration generations.
- Record all five Forge TOML domains and relevant Server Shop and Bazaar JSON files. Valid reload, invalid reload, provider change, module change, catalog change, and concurrent in-flight generations are separate fixtures.
- Every fault schedule starts from a fresh copied fixture and declares the expected owner before and after the durable boundary. A world used by one fault schedule is never a baseline for another.
- NeoForge evidence uses its exact Java 21 branch and own task names only when source impact proves it is affected. Forge fixtures, code, APIs, JARs, and assertions are not copied across by assumption.

### Command and Rerun Order

For the Forge phase branch and again at the exact merged `1.20.1` revision, retain the exact revision, Java, command, duration, exit status, decisive result, logs, fixture identity, and environment:

1. Confirm that no formatter or static-analysis task exists, or run it if one is added.
2. Run focused protocol, readiness, shop, provider, ATM, market, lifecycle, reload, fault, issue 32 ownership-isolation, replay, recovery, and conservation regressions.
3. Run `bash ./gradlew test` with Java 17.
4. Run `bash ./gradlew runData` when providers, generated resources, examples, or data contracts changed.
5. Run `bash ./gradlew runGameTestServer` when applicable GameTests exist for the affected integration.
6. Run `bash ./gradlew build`.
7. Run an isolated dedicated server through clean startup, delayed readiness, all workflow families, valid and invalid reload, controlled shutdown, crash recovery, restart, and second restart.
8. Run `bash ./gradlew runClient` for every client-visible, protocol, synchronization, session, claim, provider, lifecycle, or recovery path.
9. Run the complete DEC-007 server and at least two independent client matrix, including concurrency, dropped response, disconnect, restart, reconnect, replay, and stale-state convergence.
10. Inspect the JAR, complete diff, status, packet registrations, protocol constant, metadata, dependencies, resources, translations, schemas, test-only hooks, secrets, private data, local paths, logs, worlds, caches, debug output, generated drift, and loader contamination.
11. After the Forge pull request merges, fetch `origin/1.20.1` and rerun steps 1 through 10 at the exact merge commit before creating the phase tag.

A failed, skipped, flaky, stale, timed-out, or unavailable mandatory result is not a pass. Repair the product or local harness, rerun the affected dependency chain, and retain the failed evidence. A not-applicable result is permitted only for a genuinely absent data-generation, GameTest, provider, or support-line surface and must include exact source-backed proof. No lower-fidelity result substitutes for dedicated-server, client, controlled multiplayer, restart, reconnect, or recovery evidence.

## Documentation, Operations, and Release

- Update `DOCUMENTATION.md` with actual ingress-to-authority flow, readiness ownership, identity propagation, provider and module lifecycle, configuration generations, diagnostics, terminal failure states, restart, reconnect, and recovery.
- Update `README.md` only when verified user or operator behavior, commands, setup, compatibility, configuration, or supported workflows changed.
- Update `docs/markets-guide.md` for Auction House, Bazaar, module availability, claims, concurrency, reconnect, and failure behavior.
- Update `docs/physical-currency-atm.md` for provider selection, deposit, withdrawal, cash claims, full inventory, retry, restart, and recovery.
- Update `docs/backup-restore.md` when Phase 006 proves a changed shutdown, snapshot, startup validation, stop condition, ownership-isolation, or non-destructive recovery procedure.
- Update existing configuration guides for complete validated reload, last-known-good behavior, provider generations, module changes, in-flight semantics, and diagnostics. Do not invent keys or reload support.
- Update `docs/community-bug-regression-test-gaps.md` or its current successor with locally controlled issue 32 and issue 34 integration evidence, the complete multiple-client and fault matrices, exact environment, and safe reproduction procedure. Historical prerequisite IDs are traceability only.
- Update `docs/README.md` when a focused document is added, moved, or renamed. Preserve existing layout and filename casing.
- Operator procedures name exact revisions, configuration, mods, server and client commands, expected results, failure stop conditions, evidence locations, backup scope, and non-destructive recovery actions.
- GitHub issues, pull requests, reviews, closure comments, milestones, and Project state follow repository lowercase and punctuation rules and include sanitized exact evidence. Sensitive findings use the private advisory path.
- Create the sequential Forge Phase 006 pull request into `1.20.1` after Task 010 passes. Complete one private independent review if the optional private review capability exists, required checks, merge through GitHub, fresh remote containment, and exact merged-revision reruns.
- Create and push signed annotated tag `phase-006-backend-integration` only after all merged-revision evidence passes. It is internal phase integration evidence, not a product release.
- Do not create a GitHub Release, publish a release tag, upload to CurseForge or Modrinth, announce a release, declare stable status, or publish any candidate artifact.

## Risks and Evidence Invalidation

| Risk | Prevention | Detection | Recovery | Evidence invalidated | Reverification |
|---|---|---|---|---|---|
| Phase starts from partial Phase 005 work | Require merged PR, remote containment, merged reruns, tag, full packet and issue 32 proof | Branch base, tag, packet or required row disagrees | Finish Phase 005, fetch, then recreate Phase 006 branch from approved `origin/1.20.1` | All Phase 006 evidence | Restart at `P006-TASK-001` |
| Matrix omits an ingress, provider or terminal path | Source-backed registration, provider discovery and reverse terminal trace | Unmapped packet, command, scheduler, adapter, session, claim or diagnostic | Add row before mutation testing | Integration inventory and all dependent rows | Task 002 through affected domains |
| Client state is treated as authority | Trace decisions to server owners and durable receipts | Result changes after reconnect or fresh snapshot | Repair server boundary and client reconciliation | Protocol, workflow, reconnect and multiplayer proof | Task 003, affected domain, Tasks 007, 010 and 012 |
| Issue 32 integration rewrites unrelated state | Phase 005 corpus, sentinels and exact field ownership | Player field or exact-item semantic diff exceeds owned paths | Stop, preserve full fixture, repair only owned boundary | Player lifecycle, transaction, receipt, claim, recovery and runtime evidence | Tasks 004 through 010 and 012 |
| Fault test adds a production bypass | Test-scoped injection and final archive inspection | Production-accessible toggle, hidden path or arbitrary mutation hook | Remove the bypass and reopen affected security evidence | Security, fault, build, JAR and runtime evidence | Phase 004 affected rows, Tasks 008, 010 and 012 |
| Shared fixture or client profile contamination | Immutable seeds, fresh copies, separate game directories and session state | Hash, path, identity, request or baseline mismatch | Discard only derived copies and recreate from verified seeds | Every case using contaminated state | Full affected matrix |
| Provider outcome cannot be reconciled | Require receipt, balance and ledger evidence for each adapter | Provider and FutureShops truth disagree or remain unknown | Freeze mutation, preserve evidence, recover once under original identity | Provider, ATM and every dependent conservation row | Tasks 005, 007, 008, 010 and 012 |
| Module becomes visible before ready | One server-owned access policy and revisioned capability | Client enters unusable route or action reaches unready service | Fail closed and republish correct capability | Readiness, navigation, market, reconnect and multiplayer rows | Tasks 003, 006, 007, 010 and 012 |
| Claims become coupled to disabled modules | Independent claim-access contract | Claim route disappears or collection fails solely due to module disable | Restore claim route without enabling mutation | ATM, market, lifecycle, recovery and multiplayer proof | Tasks 005 through 010 and 012 |
| Reload changes in-flight semantics | Captured complete configuration generation | Mixed fees, denominations, provider, stock or module rules | Resolve old work under captured semantics and new work under new snapshot | Reload and every affected workflow | Tasks 004 through 010 and 012 |
| Scheduler races shutdown or player action | Logical-server serialization and durable revisions | Duplicate expiry, fill, settlement, claim or recovery | Reconcile once through durable receipt and lineage | Market, lifecycle, recovery and conservation evidence | Tasks 006 through 010 and 012 |
| Verified defect is repaired before filing | Task 009 timestamp gate | First repair diff predates canonical issue or advisory | Stop and restore pre-repair state, file or link record, then begin repair | Finding traceability and acceptance | Task 009 and full affected blast radius |
| Cross-line contamination | Independent ancestry, toolchains, APIs, fixtures and pull requests | Forge code or metadata appears on NeoForge or reverse | Remove through line-specific reviewed repair | Compatibility, build, runtime, JAR and merge evidence | Full affected support-line ladder |
| Workstation or node1 harness drifts | Pin host role, process, memory, ports, clients, profiles, world, revision and JAR | Launch, memory, display, timing, identity or network mismatch | Repair local topology or move only server role to node1 | All affected runtime and timing evidence | Complete local matrix from clean fixtures |
| Late repair invalidates upstream or branch proof | Explicit dependency and evidence graph | Interface, schema, command, security, persistence or fixture hash changes | Reopen named rows and rerun from earliest invalidated gate | Named Phase 004, Phase 005 and Phase 006 packets | All reachable checks at final branch and merge revisions |
| Final merge differs from tested branch revision | Bind packet and artifacts to exact source hashes, then rerun after merge | Commit, tree or JAR hash differs | Discard closure claim and rerun full merge matrix | All Phase 006 runtime and completion evidence | Task 012 in full |
| Sensitive evidence enters tracked or public output | Synthetic fixtures, bounded identifiers and transport review | Secret, raw private NBT, player data or exploit detail scan | Remove unsafe evidence, follow security response and recapture safely | Evidence, docs, issue and pull-request surfaces | Sanitized recapture and complete disclosure review |

Any packet registration or codec change invalidates protocol and all consuming workflow rows. Any readiness, module control, session, capability, diagnostic, or configuration change invalidates navigation, lifecycle, reload, reconnect, and every domain using that gate. Any provider, economy, escrow, custody, claim, persistence, receipt, or issue 32 ownership change invalidates every affected conservation, recovery, and multiplayer row. Any fixture or fault-driver change invalidates results produced with its earlier hash.

## Phase Completion Packet

The packet is retained outside this protected plan set and contains:

1. The exact Phase 005 Forge merge and verified tag consumed as the base, full Phase 005 completion-packet identity, local issue 32 corpus and ownership proof, approved starting `origin/1.20.1`, Phase 006 branch revision, pull request, merge commit, remote containment, and signed implementation commits.
2. Exact Java 17, Forge 47.4.20, Gradle 8.14.4, dependency, mod, provider, configuration, fixture, world, host, memory, process, port, client profile, and JAR identities for every Forge campaign.
3. Any independently affected NeoForge revision, impact proof, Java 21 results, line-specific pull request and merge. If unaffected, retain the source-backed impact decision.
4. Final subsystem interface, state-flow, identity, protocol, readiness, provider, module, configuration, diagnostic, lifecycle, failure, and source-coverage matrices with no unclassified row.
5. Immutable fixture manifest, complete Phase 005 issue 32 corpus references, sentinel and ownership maps, fault-point manifest, isolated copy procedure, and evidence invalidation graph.
6. Focused regression, integration, property, codec, client-state, provider, market, shop, ATM, claim, reload, restart, reconnect, issue 32, recovery, and conservation results for every workflow family and repaired defect.
7. Complete branch-revision and exact merged-revision command records for focused tests, `test`, applicable `runData`, applicable `runGameTestServer`, `build`, dedicated server, client, controlled multiplayer, reload, shutdown, restart, reconnect, JAR inspection, and complete diff inspection.
8. The DEC-007 local multiplayer packet with default 64 GB workstation topology or exact node1 temporary-server fallback record, at least two independent clients, finite and infinite stock, Player Shops, every provider, Auction House, Bazaar, ATM, claims, concurrency, disconnect, response loss, restart, reconnect, replay, and stale-state rows.
9. Pre-state, durable intermediate state, terminal state, exact identities, and checked conservation reports for every value-bearing success, rejection, race, and injected failure.
10. One canonical issue-before-repair record for every verified finding, including duplicate search, failing evidence where safe, implementation, review, pull request, merge, checks, exact reruns, closure, and confidential handling where required.
11. Documentation diffs, link and terminology checks, operator procedure rehearsal, issue and Project synchronization, and proof that no secret, raw private data, local-only path, run output, cache, generated world, debug artifact, or unrelated edit entered tracked output.
12. Final JAR listing, hash, metadata, packet and protocol inventory, dependency contents, translations, resources, schema and compatibility inspection, and proof that no fault hook, hidden bypass, loader contamination, or private evidence ships.
13. GitHub proof that the Phase 006 pull request merged into `1.20.1`, all required checks passed, `origin/1.20.1` contains the merge, every independently required support-line repair is merged, and the exact merged revision produced every final result.
14. Signed annotated tag `phase-006-backend-integration`, exact Forge merge target, verified EnVisione signature, and remote tag presence, with explicit classification as internal phase evidence rather than release publication.
15. A final clean audit proving no mandatory row is failed, skipped, flaky, stale, unavailable, lower fidelity, or unclassified; no verified phase defect or unresolved mandatory gate remains; and publication did not occur.
16. The Phase 007 handoff naming exact revisions, artifact and matrix hashes, all closed issue records, every upstream evidence row reopened and rerun, final environment topology, tag, documentation state, and the first `CORE-PHASE-007` entry action.

Any failed, skipped, flaky, stale, unavailable, lower-fidelity, unmerged, unsigned, or incomplete mandatory evidence keeps Phase 006 open. There is one full exit.

## Next Transition

Transition only to `CORE-PHASE-007` after the Phase 006 Forge pull request is fully merged into `1.20.1`, fresh remote ancestry contains the merge, all required checks and every exact merged-revision local matrix pass, every verified finding is closed or otherwise fully resolved by the phase contract, any separately affected NeoForge repair is merged and verified, and signed annotated tag `phase-006-backend-integration` targets the verified Forge merge and validates as EnVisione.

Then read `plan.md` and `phases/plan-phase-007.md` through EOF. Create the Phase 007 branch only from the latest approved fetched `origin/1.20.1` merge and begin its first unfinished entry action using the complete Phase 006 packet. Do not stack Phase 007 on the Phase 006 branch, start from an open or queued pull request, use a local approximation of the merge, omit an invalidated rerun, or carry a phase-owned defect, failed local matrix, unsigned tag, or unresolved blocker into the final phase.

Phase 007 consumes the exact merged integration inventory, issue 32 successor packet, local multiplayer packet, failure and conservation matrices, documentation state, issue inventory, and signed phase tag. Phase 006 authorizes no release creation, upload, announcement, stable designation, or publication.
