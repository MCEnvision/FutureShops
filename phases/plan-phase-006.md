# Phase 006 Execution Plan

> **Plan ID:** PLAN-PHASE-006
> **Phase ID:** CORE-PHASE-006
> **Owner:** FutureShops server integration architecture
> **Classification:** MANDATORY
> **Master plan:** [plan.md](../plan.md)
> **Phase sequence:** 006 of 007

## Purpose and Ownership

This phase proves that the individually clean authoritative services delivered through CORE-PHASE-005 compose as one safe product. It audits and closes backend integration and failure handling across client and server networking, module readiness, server shops, player shops, every configured economy source, escrow, Auction House, Bazaar, ATM, claims, configuration reload, restart, reconnect, and controlled multiplayer.

The canonical requirement is `CORE-REQ-013`. The phase consumes the security, command, persistence, recovery, and economic invariants established upstream; it does not reopen those audits without evidence of an integration defect. The master plan owns product scope, the fixed phase sequence, external blocker policy, and the final completion endpoint. This blueprint owns only dependency-ordered execution and proof for `CORE-PHASE-006`.

## Evidence-Based Entry State

| Evidence class | Area | Finding | Source or command | Freshness condition |
|---|---|---|---|---|
| VERIFIED | Product contract | Backend integration and failure handling is mandatory and includes every named client, server, market, shop, economy, escrow, ATM, claim, lifecycle, reload, restart, reconnect, and multiplayer boundary | `plan.md`, `CORE-REQ-013`, `DEC-006`, SRC-001 | Invalidated by an owner-authorized master-plan revision |
| OBSERVED | Forge implementation | The Forge line contains distinct network packet handlers, server shop and player shop services, economy provider interfaces, escrow runtime services, market services, ATM workflows, claims, sessions, configuration, and client response trackers | `src/main/java/com/enviouse/futureshops/`, SRC-009, SRC-011 | Reinventory after any relevant source move, deletion, or material change |
| OBSERVED | Protocol surface | `ShopPackets` and the `network/packets` package expose request and response families for shops, markets, ATM, claims, profiles, pages, and module navigation | `src/main/java/com/enviouse/futureshops/network/ShopPackets.java`, `src/main/java/com/enviouse/futureshops/network/packets/`, SRC-011 | Reinventory after packet registration, discriminator, codec, direction, or protocol-version changes |
| OBSERVED | Server authority | Server-side services exist under `server/shop`, `server/transaction`, `server/economy`, `server/escrow`, and `server/market`; client code maintains presentation and response state | SRC-002, SRC-003, SRC-011, repository package inventory | Reconfirm after an authority-boundary or package-ownership change |
| OBSERVED | Readiness and lifecycle | Escrow runtime coordination, market capability projection, market module access policy, module control, sessions, and Bazaar initialization gates are separate integration surfaces | `EscrowRuntimeCoordinator`, `MarketCapabilityProjectionService`, `MarketModuleAccessPolicy`, `MarketModuleControl`, `MarketServerSessionRegistry`, `BazaarRuntimeInitializationGate` | Reconfirm after readiness, maintenance, recovery, capability, or module-control changes |
| OBSERVED | Existing tests | Unit and source-contract coverage exists for packets, navigation, capability snapshots, requests, claims, ATM trackers, shop responses, escrow recovery, conservation, and module configuration, but this does not prove the complete exact-revision runtime matrix | `src/test/java/com/enviouse/futureshops/`, SRC-011 | All affected evidence invalidates after a material integration change |
| OBSERVED | Finite-stock integration risk | Issue 34 records a Forge multiplayer failure in the finite-stock money-purchase path while infinite stock succeeds, making stock, transaction, provider, escrow, response, and diagnostic composition a required regression surface | SRC-008 | Replace with verified exact-revision evidence only after the controlled multiplayer reproduction and repair matrix runs |
| VERIFIED | Upstream dependency | Phase entry requires authoritative services, command paths, and every repository-controlled persistence, recovery, and invariant interface to be integrated and individually clean | `CORE-PHASE-005` full or internal integration contract in `plan.md` | Entry fails if Phase 005 is unmerged, its independent checks regress, its handoff packet is incomplete, or an inherited external blocker is hidden |
| UNKNOWN | Controlled multiplayer | The isolated Forge 1.20.1 server with two independent clients and complete state capture is not guaranteed available at planning time | EXT-004 | Becomes verified only through the exact environment and evidence required by EXT-004 |
| VERIFIED | Issue workflow | Authenticated EnVisione GitHub access is available for duplicate search, issue-before-repair routing, integration links, checks, and blocker synchronization | EXT-005 | Revalidate identity and access before remote issue or pull-request mutation |
| PROPOSED | Phase evidence | The matrices, fault-injection suite, exact-revision runtime packet, and completion packet in this file have not yet been executed | This phase plan | Becomes verified only at the exact integrated revision with retained decisive evidence |

No `OBSERVED`, `PROPOSED`, or `UNKNOWN` entry may be promoted to `VERIFIED` without the exact revision, environment, procedure, expected result, actual result, and retained evidence.

## Scope Boundaries

### Included Scope

- `CORE-REQ-013` is the sole canonical requirement owned by this phase. The phase inventories, audits, repairs, and proves every named backend integration and failure path.
- `CORE-REQ-009`, `CORE-REQ-010`, `CORE-REQ-011`, `CORE-REQ-012`, and `CORE-REQ-014` are mandatory upstream contracts consumed as invariants. If Phase 005 used its internal integration gate, Phase 006 consumes only its clean repository-controlled interfaces and carries EXT-003 unchanged. Any integration finding that violates an upstream contract reopens the affected audit evidence and follows issue-before-repair routing.
- Forge 1.20.1 is the primary runtime line because server shops, player shops, Auction House, Bazaar, ATM, claims, economy, escrow, reload, and EXT-004 are present in its scoped integration matrix.
- NeoForge 1.21.1 receives a bounded integration regression only for shared product behavior or an independently proven NeoForge-specific boundary affected by Phase 006 work. Forge code must not be transferred across loader lines by assumption.
- Successful workflows, deterministic rejections, interrupted workflows, crash and restart recovery, stale and replayed requests, concurrent actors, module state transitions, configuration generations, and external dependency failures are included.
- Runtime evidence is non-destructive and uses isolated fixtures or complete matching backups. State capture includes balances, stock, inventory, custody, claims, transaction identities, durable receipts, module status, config generation, and client-visible result.

### Explicit Exclusions

- `CORE-REQ-015` and final full-stack verification, `CORE-REQ-017` final documentation reconciliation, `CORE-REQ-018` artifact preparation, `CORE-REQ-019` repeated final audit, and `CORE-REQ-020` issue closure belong to `CORE-PHASE-007`. Phase 006 supplies their integration inputs.
- `FUT-001` and `FUT-002` remain excluded. This phase does not publish artifacts, create releases, announce a version, or declare stable status.
- `FUT-003` remains excluded. An integration defect may be repaired, but an unrelated enhancement or new subsystem requires a master-plan revision.
- `FUT-005` remains excluded. This phase does not add distributed live market state or direct external-storage listing without deterministic receipts.
- `NG-003` forbids deleting player data, journals, checkpoints, ledgers, custody, claims, or worlds during failure injection or recovery.
- `NG-004` forbids making a client path appear available by weakening readiness, maintenance, escrow, claims, permissions, idempotency, or server authority.
- `NG-005` forbids cross-line integration without independent proof and a line-compatible implementation.
- An unavailable EXT-004 may be represented only as an explicit blocker. Unit tests, a single client, an integrated server, mocks, or synthetic packet calls do not replace it.

## Phase Contract

### CORE-PHASE-006 — Backend Integration and Failure-Handling Closure

**Objective:** Prove at an exact integrated revision that every named subsystem combination reaches one deterministic, server-authoritative, conservation-safe result in success and failure states, and repair every verified integration defect through the rolling issue contract.
**Owner:** FutureShops server integration architecture
**Dependencies:** CORE-PHASE-005, CORE-REQ-009, CORE-REQ-010, CORE-REQ-011, CORE-REQ-012, CORE-REQ-014, DEC-006, EXT-004, EXT-005
**Canonical requirements:** CORE-REQ-013
**Documentation and release impact:** Update integration architecture, operator diagnostics, configuration reload, module lifecycle, recovery, and multiplayer verification documentation for behavior proven in this phase. Produce exact-revision evidence for Phase 007. Do not publish or prepare final release artifacts here.
**Next transition:** CORE-PHASE-007

**Entry criteria**

- `CORE-PHASE-005` is integrated on the correct Forge support branch, `origin/1.20.1` contains the merge, and its full completion packet or blocker-aware internal integration packet is current.
- Security and command repairs consumed by the runtime are integrated; no unresolved known defect prevents safe mutation testing.
- The authoritative runtime can start from a copied fixture without unexpected maintenance or integrity failure. Any expected maintenance fixture is separately labeled and never used as the healthy baseline.
- Packet registration, protocol version, session and route-nonce rules, configured economy sources, module settings, persistent stores, recovery identities, and available fault injectors are inventoried at the exact phase-start revision.
- EXT-005 identity and repository access are verified before any issue or integration state is changed.
- EXT-004 is either available with the required dedicated server and two-client topology or recorded as unavailable with the exact missing capability. Its absence does not permit false completion.

**Implementation scope**

- CORE-REQ-013 defines the complete mandatory implementation boundary detailed below.

**Detailed implementation scope**

- Build a traceable subsystem interface and state-flow matrix from every ingress to every terminal response or durable recovery state.
- Establish one authoritative readiness and capability truth across server startup, recovery, maintenance, module control, sessions, navigation, reload, and reconnect.
- Verify the protocol boundary from decoding through response correlation, including payload bounds, direction, identity, route nonce, request UUID, permission, rate limit, stale state, replay, and disconnect.
- Exercise server shops, player shops, every configured economy source, escrow, Auction House, Bazaar, ATM, and claims under success, rejection, partial failure, retry, concurrency, restart, reconnect, and reload.
- File or link every verified defect before repair, implement the smallest authoritative-boundary correction, rerun the affected matrix, and preserve all upstream invariants.
- Produce the exact-revision integration packet required to enter Phase 007, carrying EXT-004 visibly if the controlled environment remains unavailable.

**Execution order**

- `P006-TASK-001` through `P006-TASK-012` execute the CORE-PHASE-006 task sequence in order.

**Detailed task sequence**

1. `P006-TASK-001` freezes the exact entry revision, upstream completion evidence, environment inventory, and external blocker state.
2. `P006-TASK-002` maps every ingress, authoritative service call, durable transition, response, client projection, lifecycle state, and recovery identity.
3. `P006-TASK-003` closes protocol, session, correlation, replay, and readiness boundaries before value-bearing workflows run.
4. `P006-TASK-004` proves server shop and player shop composition across catalog, stock, inventory, economy, escrow, delivery, claims, and client responses.
5. `P006-TASK-005` proves economy provider, ATM, physical currency, wallet, and cash-claim composition across provider and configuration lifecycles.
6. `P006-TASK-006` proves Auction House and Bazaar order, custody, settlement, expiry, module control, page, profile, and claim composition.
7. `P006-TASK-007` executes delayed readiness, module lifecycle, reload, shutdown, restart, reconnect, concurrency, and recovery matrices across workflows.
8. `P006-TASK-008` injects bounded failures at every pre-commit, durable-commit, delivery, response, persistence, provider, and reconnect boundary and reconciles conservation.
9. `P006-TASK-009` routes every verified defect through duplicate search and issue creation before repair, then repeats Tasks 003 through 008 for the affected blast radius.
10. `P006-TASK-010` runs exact-revision deterministic, dedicated-server, client, and EXT-004 multiplayer evidence in repository order.
11. `P006-TASK-011` updates integration and operator documentation and assembles the Phase 006 completion packet.
12. `P006-TASK-012` performs the clean closure pass and hands the exact revisions, invalidation graph, issue state, and any EXT-004 blocker to `CORE-PHASE-007`.

Tasks 004, 005, and 006 may develop fixtures in parallel only after Tasks 001 through 003 freeze shared identities and readiness semantics. Mutation and fault tests that share a world, journal, or provider must run serially or on isolated copies. Tasks 007 and 008 must consume the completed domain matrices. Task 009 is an immediate gate whenever a finding becomes verified, not a deferred batch step.

**Required evidence**

- Exact commit, branch ancestry, toolchain, configuration, mod list, fixture hashes, environment topology, and upstream completion-packet references.
- A complete cross-component call, state-transition, and ownership matrix with stable request, transaction, custody, claim, session, configuration, and recovery identities.
- Focused integration and negative tests for every matrix row and every repaired defect.
- Dedicated-server and client smoke logs covering startup, delayed readiness, module state, server shops, player shops, Auction House, Bazaar, ATM, claims, reload, restart, reconnect, and shutdown.
- EXT-004 logs and before-and-after state snapshots from an isolated dedicated Forge server and at least two independent clients, including finite and infinite stock fixtures.
- Conservation reports for every value-bearing workflow and injected partial failure.
- Issue-before-repair links, merged revision links, check results, and the evidence invalidation and rerun record.
- Documentation diff and operator rehearsal evidence for every changed diagnostic, lifecycle, reload, or recovery behavior.

**Exit criteria**

- Every named subsystem and lifecycle combination has one tested success result and every applicable failure class has one deterministic, safe, actionable terminal or recoverable result.
- Server authority, stable UUID idempotency, checked integer value, custody conservation, accessible durable claims, fail-closed readiness, compatibility, and no-loss behavior hold across all matrix rows.
- Disabled, frozen, draining, recovering, and unavailable modules do not advertise unusable mutation paths; enabled and ready modules become usable from authoritative server state without requiring a relog; claims remain reachable independent of module availability.
- Reload, restart, reconnect, replay, disconnect, concurrency, and provider failure preserve committed truth and do not duplicate, lose, or silently strand value.
- Every verified phase finding has a canonical issue created or linked before repair, a focused regression, an integrated correction on the correct support line, and rerun blast-radius evidence.
- Exact-revision unit, integration, dedicated-server, client, and required multiplayer evidence pass. If EXT-004 is unavailable, all independent internal evidence must pass and EXT-004 remains an explicit final blocker attached to `CORE-REQ-013`, `CORE-REQ-015`, and `CORE-REQ-019`; the phase may make only the master-defined internal integration transition and is not fully complete.
- No known mandatory phase-owned defect remains.

### Requirement Traceability

| `CORE-REQ-013` acceptance criterion | Execution ownership | Required proof | Blocking rule |
|---|---|---|---|
| Every named subsystem, lifecycle state, and cross-component value transition is covered on its supported line | `P006-TASK-002`, `P006-TASK-004`, `P006-TASK-005`, `P006-TASK-006` | Complete source-backed interface and state-flow matrix plus domain workflow and conservation packets | Any unclassified ingress, transition, terminal state, or supported-line disposition blocks exit |
| Delayed readiness, disabled modules, enable after restart, stale snapshots, dropped or replayed requests, disconnect, provider failure, full inventory, claim delivery, reload, shutdown, and recovery are deterministic and safe | `P006-TASK-003`, `P006-TASK-007`, `P006-TASK-008` | Protocol and readiness packet, lifecycle timeline, fault manifest, restart and recovery state diffs | A missing, flaky, lower-fidelity, or unexplained row remains unverified |
| Disabled modules do not advertise unusable navigation, enabled modules become usable from server truth, and claims remain reachable independent of module availability | `P006-TASK-003`, `P006-TASK-005`, `P006-TASK-006`, `P006-TASK-007` | Capability revisions, two-client stale-state checks, module transition traces, ATM and market claim collection results | Any mutation bypass, false availability, required relog, or inaccessible durable claim blocks exit |
| Every verified defect follows issue-before-repair routing and exact candidates rerun clean | `P006-TASK-009`, `P006-TASK-010`, `P006-TASK-012` | Duplicate search, canonical issue, failing regression, merged fix, blast-radius rerun, exact-revision packet | Repair without prior issue, stale evidence, failed checks, or an unresolved repository-owned defect blocks transition |

## Inputs and Upstream Contracts

| Input or contract | Provider | Required state | Validation | Failure behavior |
|---|---|---|---|---|
| Integrated Forge revision | `CORE-PHASE-005` | Latest approved `1.20.1` head contains all prior phase merges and no stacked phase branch; any inherited EXT-003 blocker is explicit | Git ancestry, merged pull request, required checks, clean working tree inventory, blocker readback | Stop. Do not create or test Phase 006 on stale or stacked ancestry or a handoff that hides inherited blockers |
| Security and command closure | `CORE-PHASE-004` | Network, permission, command, path, codec, and packaging findings that affect integration are repaired and evidenced | Completion packet and exact-revision regression links | Reopen affected evidence and route any new defect through CORE-REQ-009 |
| Persistence and conservation closure | `CORE-PHASE-005` | Every repository-controlled persistence surface, recovery lineage, claim, custody, ledger, stock, configuration, migration, and economic invariant is individually clean; exact issue 32 evidence may remain blocked only by EXT-003 | Inventory, compatibility matrix, recovery rehearsal, conservation evidence, and inherited blocker record | Stop unsafe mutation. Preserve state and use the documented recovery contract |
| Defect evidence packet | `CORE-PHASE-000` and CORE-REQ-009 | One canonical issue, affected line, exact reproduction or gap, acceptance criteria, and evidence links for each finding | Duplicate search by behavior, component, result code, exception, and identifier | Do not repair until the issue or confidential record exists |
| Network contract | `ShopPackets`, packet codecs, protocol policy | Stable registration, correct direction, bounded decoding, authenticated sender context, route nonce, request UUID, and response correlation | Registration inventory, codec round trips, wrong-side and malformed-input tests | Reject before mutation with stable bounded response or disconnect policy |
| Runtime readiness | Escrow runtime, module access, capability projection, session registries | Mutation is allowed only when required durable state is ready; recovery and maintenance remain authoritative | Startup trace, readiness state transitions, capabilities snapshots, session trace | Fail closed, keep safe read or claim paths available, return actionable unavailability |
| Configuration generations | Common, escrow, Auction House, Bazaar, client, and shop catalog configuration owners | Complete validated snapshots, last-known-good fallback, and stable generation or revision for in-flight work | Valid and invalid reload tests plus before-and-after snapshot comparison | Reject invalid candidate, retain last valid snapshot, do not reinterpret committed or in-flight work |
| Economy and currency authority | `EconomyProvider`, wallet services, currency manager, physical funding services | Checked integer minor units, explicit provider result, stable configuration lease, and no silent fallback | Provider contract tests, boundary arithmetic, balances and liabilities snapshot | Fail closed; retain or compensate custody once; expose claim or recovery state |
| EXT-004 environment | External prerequisite | Isolated Forge 1.20.1 dedicated server at exact revision, two independent clients, finite and infinite stock fixtures, state capture | Environment manifest, client identities, logs, fixture hashes, exact revision | Keep dependent evidence blocked; do not substitute lower-fidelity proof |
| GitHub synchronization | EXT-005 | Authenticated EnVisione access to the authoritative repository | Identity, remote, issue, branch, check, and pull-request readback | Preserve local evidence, stop remote mutation, record exact access blocker |

## Outputs and Downstream Contracts

| Output or contract | Consumer | Guaranteed state | Compatibility or versioning | Evidence |
|---|---|---|---|---|
| Integration inventory | `CORE-PHASE-007` | Every named subsystem, ingress, authoritative owner, state transition, output, and failure class is classified | Bound to exact Forge and any independently affected NeoForge revision | Matrix hash, source references, exact revision |
| Protocol and readiness proof | Final runtime audit | Requests cannot bypass server identity, permission, route, readiness, module, replay, or bounds checks; client projections converge to server truth | Preserve protocol compatibility or document an explicit versioned change | Codec, handler, session, capability, reconnect, and negative results |
| Workflow conservation packet | Final candidate verification | Each shop, market, ATM, provider, and claim workflow balances sources, custody, destinations, fees, stock, and claims before and after faults | Signed integer minor units and existing persistence identities remain stable | Machine-readable or tabular state snapshots and reconciliation result |
| Failure-injection packet | Final security, persistence, and integration audits | Each injected interruption has an expected durable boundary, restart result, retry result, and recovery route | Fixture and fault-point identifiers remain stable for rerun | Fault manifest, logs, state diffs, recovery result |
| Integration repairs | Correct support branch | Every verified integration defect is minimally repaired at its authoritative owner and regression tested | No assumed cross-line port; schema or protocol change requires compatibility proof | Issue, pull request, merged revision, focused and full results |
| Operator documentation | Phase 007 documentation reconciliation | Proven lifecycle, reload, failure, diagnostic, restart, reconnect, and recovery behavior is documented without claiming unverified release status | Existing documentation layout and command, config, and identifier stability preserved | Documentation diff, link check, operator rehearsal |
| External blocker record | Phase 007 final gate | EXT-004 is either satisfied at the exact revision or remains precise, current, and visibly blocking | Cannot be waived by internal completion | Environment evidence or missing-capability record |
| Phase completion packet | `CORE-PHASE-007` | Exact revisions, results, issues, invalidations, residual blockers, and downstream rerun requirements are complete | Any later material change invalidates the named evidence | Packet manifest and readback audit |

## Subsystem Interface and State-Flow Matrix

Execution expands every row into concrete source references, fixtures, success cases, negative cases, fault points, and retained evidence. A row is incomplete if it proves only request acceptance without proving the durable result and client-visible outcome.

| Flow | Ingress and presentation | Authoritative orchestration | Durable or external state | Required terminal states | Principal failure states |
|---|---|---|---|---|---|
| Server shop money purchase | Shop screen, buy request, correlated buy response | Packet handler, catalog and quote validation, stock reservation, `ShopBuyService` or normalized server-shop service, payment and escrow | Stock, ledger or provider balance, payment receipt, item custody, transaction history, claim | Rejected before mutation, committed and delivered once, or committed with durable claim | Stale quote, finite-stock conflict, insufficient funds, provider failure, full inventory, dropped response, retry, restart |
| Server shop sell | Shop screen, sell request, correlated response | Sell validation, exact item proof, `ServerShopSellService`, stock and escrow commit | Exact item custody, stock, payout, ledger, claim, history | Rejected untouched, committed payout once, or recoverable custody and claim | Inventory change, stock revision conflict, payout/provider failure, disconnect, replay, crash cut |
| Server shop barter and cart | Offer or cart screen, barter or cart request, correlated result | Offer validation, cart fingerprint, item and money fanout, escrow transaction | Inputs, outputs, stock, use limits, custody, claims, replay receipts | Entire request rejected, atomically committed, or recoverable claims | Mixed invalid line, duplicate line, stale cart, partial delivery, response loss, restart |
| Player shop buy, acquire, sell, and barter | Player shop screens and request families | Block and ownership validation, normalized offer service, storage adapter, player-shop escrow lifecycle | Shop inventory or receipt-backed storage, buyer and owner value, settlement, custody, claim | Rejected untouched, one atomic settlement, or durable recovery state | Block removed, owner or offer changes, external storage loss, capacity conflict, disconnect, replay, restart |
| Economy provider and wallet | Any value-bearing service | `EconomyProvider`, internal wallet and ledger adapters, payment source selection, mutation guard | Provider balance, escrow ledger, payment receipt, configuration generation | One debit and credit, deterministic denial, or one compensating or claim path | Provider unavailable, false success, timeout, exception, overflow, config change, duplicate call |
| Physical currency funding | Shop, market, or ATM request | Currency validation, mint protection, selection planner, physical funding or deposit workflow | Inventory stacks, custody evidence, wallet credit, overflow claim, spent-mint state | Valid cash consumed once and credited once, or rejected untouched | Foreign or invalid cash, inventory race, partial custody, crash, duplicate mint, overflow |
| ATM deposit | ATM screen, deposit and recovery packets | ATM service, security policy, currency validation, escrow cash deposit | Inventory, custody, wallet, deposit intent, claim or recovery queue | Deposit applied once, safely rejected, or recoverable intent | Invalid stack, provider failure, response loss, disconnect, crash before or after commit |
| ATM withdrawal and cash claim | ATM screen, withdrawal and collection packets | Selection plan, `EscrowAtmWithdrawalService`, claim center and delivery planner | Wallet, minted cash custody, withdrawal commit, cash claim | Cash delivered once, claim retained, or debit not applied | Insufficient balance, inventory full, configuration lease change, mint failure, restart, replay |
| Auction House | Market navigation, page query, create, bid, buy-now, cancel, claim | Market access policy, `AuctionActionService`, Auction House book and escrow lifecycle | Listing, lot custody, bid holds, sale, fees, receipts, expiry, claims | Deterministic rejection, committed lifecycle transition, or recoverable settlement and claims | Module disabled, stale listing, self-action, outbid, provider failure, expiry race, disconnect, restart |
| Bazaar | Market navigation, page query, register, order, cancel, claim | Market access policy, `BazaarActionService`, product runtime, order book, matching and escrow | Product revision, order, holds, exact lot custody, fills, fees, receipts, claims | Deterministic rejection, atomic order or cancellation, exact fills, or recoverable claims | Disabled or unready module, stale product, FOK rejection, self trade, provider failure, match race, expiry, restart |
| Market capability and navigation | Main screen, capability request, module open, page/profile queries | Capability projector, module access policy, session registry, server-selected route | Module control state, readiness, config revision, route nonce, session state | Allowed route opens from server truth, denied route gives actionable state, stale client converges | Delayed readiness, stale snapshot, module transition, invalid route nonce, reconnect, protocol mismatch |
| Market claims | Claim center and collection request | Claim collection service and processor independent of market mutation availability | Item and money claims, custody, collection receipt | Delivered once, partially retained with exact remainder, or safely unavailable due only to recovery integrity | Module disabled, full inventory, provider failure, disconnect, replay, restart |
| Configuration and catalog reload | Operator reload and runtime listeners | Complete parse and validation, generation/revision publication, affected services | Last-known-good snapshots, in-flight rule snapshots, catalogs, module settings | Valid snapshot published atomically; invalid snapshot rejected with old state active | Malformed field, missing registry entry, incompatible rule, provider config change, concurrent transaction |
| Server lifecycle | Dedicated-server start, tick, stop, restart | Runtime binding, journal replay, recovery scheduler, market initialization, sessions and schedulers | Journal lineage, checkpoints, SavedData, catalogs, controls, queued recovery | Readiness becomes true only after verification; shutdown quiesces safely; restart converges | Delayed binding, corruption, incomplete transaction, scheduler overlap, stop during commit |
| Client reconnect and multiplayer | Logout, disconnect, reconnect, two-client actions | Server sessions, route nonces, request receipts, authoritative snapshots and response trackers | Durable transaction and claim state, ephemeral sessions and client pending state | New session receives current truth; safe retry returns original result; two clients serialize conflicts | Stale nonce, orphaned pending UI, duplicate packet, simultaneous stock/order action, delayed response |

## Protocol and Readiness Boundaries

The following order is mandatory for every client-originating operation. A handler may collapse non-mutating checks where repository APIs require it, but it must not mutate before all applicable gates pass.

1. Decode with fixed field, collection, string, NBT, and numeric bounds. Reject a wrong protocol or wrong side without constructing authoritative state from the payload.
2. Obtain player identity and server context from the network framework, never from a client-authored UUID, name, owner, balance, permission, price, stock, or completion flag.
3. Verify session and route nonce for navigation-scoped requests. A reconnect creates fresh ephemeral session authority and invalidates old route capability without invalidating durable request receipts.
4. Validate permission, module access, rate budget, target existence, ownership, registry identity, quantities, arithmetic, and request semantics.
5. Resolve stable request UUID and fingerprint. An identical replay returns the original terminal result without consuming a new rate budget or reapplying value. A conflicting payload under the same UUID fails closed.
6. Check authoritative runtime readiness, maintenance, recovery, module state, and configuration generation. Presentation snapshots never override this check.
7. Revalidate quote, listing, order, stock, inventory, provider, and custody state on the logical server immediately before prepare.
8. Execute prepare, durable custody, commit, delivery, and claim or compensation through the owning service. Never emit success solely from an in-memory transition.
9. Persist the terminal receipt and correlation data required for replay before or atomically with the corresponding durable value transition.
10. Send one bounded result correlated to the request. If the response is lost, retry and reconnect recover the durable result rather than infer failure or repeat mutation.
11. Project updated capability, stock, balance, listing, order, and claim state from server truth. Client state may remain pending only for a bounded interval and must reconcile on timeout, reconnect, or fresh snapshot.

Readiness is a product state, not a screen toggle. The execution matrix must distinguish at least `starting`, `recovering`, `maintenance`, `ready`, `frozen`, `draining`, `disabled`, and `stopping` where the owning subsystem supports those states. Unsupported labels must map explicitly to the nearest real state rather than create a parallel state machine.

- Mutation paths require the exact ready and enabled predicates of every required service.
- Safe diagnostics, status, and claim access remain available to the extent allowed by integrity state, even when a market module is disabled or draining.
- Capability projections include enough authoritative state and revision data to prevent unusable navigation. A stale projection is tolerated only as presentation; the server chooses the allowed response and route.
- Enabling a module after startup publishes an updated capability and permits new sessions only after its authoritative runtime is ready.
- Disabling a module stops new mutation, preserves in-flight durable work according to its lifecycle contract, removes unusable navigation, and does not hide claims.
- Shutdown stops new work before services and schedulers detach, drains or records in-flight work, persists the required boundary, and invalidates ephemeral sessions.

## Work Packages

| Task ID | Requirement IDs | Work | Inputs and dependencies | Outputs | Affected components or interfaces | Verification |
|---|---|---|---|---|---|---|
| `P006-TASK-001` | CORE-REQ-013 | Freeze exact entry and environment inventory | Phase 005 packet, EXT-004, EXT-005 | Revision and environment manifest, blocker status, fixture catalog | Git ancestry, toolchains, configs, mods, support branches, runtime topology | Readback audit and hashes |
| `P006-TASK-002` | CORE-REQ-013 | Build complete interface, state-flow, identity, and failure matrix | Task 001, SRC-002, SRC-003, SRC-011 | Traceable row per ingress-to-terminal path | Packets, sessions, shops, transactions, providers, escrow, markets, ATM, claims, reload, lifecycle | Source-to-matrix coverage review |
| `P006-TASK-003` | CORE-REQ-013 | Audit protocol, response correlation, session, replay, capability, and readiness gates | Tasks 001 and 002, prior security evidence | Closed protocol/readiness matrix and regressions | `ShopPackets`, packet families, client trackers, capability and session services | Codec, wrong-side, bounds, nonce, replay, stale snapshot, reconnect tests |
| `P006-TASK-004` | CORE-REQ-013 | Exercise server and player shop integration | Task 003, Phase 005 invariants | Shop success, failure, and conservation packet | Catalogs, stock, offers, player shops, storage adapters, transactions, escrow, claims | Unit, integration, dedicated server, client, multiplayer rows |
| `P006-TASK-005` | CORE-REQ-013 | Exercise economy providers, payment sources, physical cash, ATM, and cash claims | Task 003, config leases, provider inventory | Provider lifecycle and ATM packet | Economy, currency, wallet, escrow ATM and deposit workflows, claim center | Provider faults, config changes, restart, reconnect, conservation |
| `P006-TASK-006` | CORE-REQ-013 | Exercise Auction House, Bazaar, market sessions, profiles, pages, expiry, and claims | Task 003, market controls and rules | Market lifecycle and conservation packet | Market services, auction and bazaar books, escrow lifecycles, schedulers, claim collection | Workflow, concurrency, expiry, disable, restart, reconnect tests |
| `P006-TASK-007` | CORE-REQ-013 | Run cross-domain lifecycle and reload matrix | Tasks 004 through 006 | Readiness, toggle, reload, shutdown, restart, reconnect proof | Runtime manager, module control, capability projection, schedulers, configs, sessions | Exact state transitions and client convergence |
| `P006-TASK-008` | CORE-REQ-013 | Inject failures at durable boundaries and reconcile recovery | Tasks 004 through 007, Phase 005 recovery contract | Fault manifest, state diffs, recovery and conservation report | Prepare, custody, commit, delivery, response, persistence, provider, scheduler boundaries | Crash-cut, retry, replay, restore, claim-access proof |
| `P006-TASK-009` | CORE-REQ-009, CORE-REQ-013 | Deduplicate, file, repair, integrate, and rerun verified findings | Any verified Task 002 through 008 finding, EXT-005 | Canonical issue and exact repair evidence | Authoritative owning component only | Failing regression, focused pass, blast-radius rerun, green integration |
| `P006-TASK-010` | CORE-REQ-013 | Execute full exact-revision evidence order | All repairs integrated, EXT-004 environment | Complete deterministic and runtime results | Forge line and independently affected NeoForge boundary only | Focused tests, `test`, applicable data and GameTests, `build`, server, client, multiplayer, restart, reconnect, JAR and diff inspection |
| `P006-TASK-011` | CORE-REQ-013, CORE-REQ-017 input | Document proven integration and operations behavior | Tasks 002 through 010 | Updated tracked docs and runbook evidence | `README.md`, `DOCUMENTATION.md`, `docs/README.md`, affected focused guides | Source-to-doc review, command and config verification, link check |
| `P006-TASK-012` | CORE-REQ-013 | Perform clean closure pass and handoff | Tasks 001 through 011 | Completion packet, invalidation graph, Phase 007 entry decision | Evidence store, issues, pull request, checks, blocker record | Independent packet audit and exact revision readback |

### Work Package Controls

- Task 002 must trace actual implementation. When an expected conceptual boundary has no distinct class, record the actual owning service instead of inventing an abstraction.
- Tasks 004 through 006 use immutable fixture seeds and a fresh world copy per destructive fault schedule. They may share read-only fixture definitions but not mutated state.
- Every value-bearing case captures pre-state, durable intermediate state when applicable, terminal state, and conservation equation. A UI success message without durable proof fails the row.
- A provider fault driver must model only behaviors permitted by the real provider interface, including denial, exception, stale result, unavailable service, and reported success where receipt reconciliation can detect inconsistency. It must not add production backdoors.
- Fault injection is test-only or uses existing repository fault surfaces. Production builds must not expose hidden toggles, bypasses, or arbitrary mutation hooks.
- A finding remains a concern until reproduced. Once verified as repository-owned, Task 009 blocks repair until duplicate search and canonical issue routing complete.
- Confidential exploitable findings follow private vulnerability reporting and contain only sanitized public state.

## Architecture and Implementation Boundaries

### Authority and Dependency Direction

- Client screens, navigation models, response trackers, and local drafts may request actions and render snapshots. They never decide money, stock, ownership, permission, module status, custody, claim completion, or transaction success.
- Packet handlers are adapters. They validate transport and actor context, then call authoritative services. They do not duplicate transaction state machines or mutate persistence directly.
- Shop, economy, ATM, Auction House, and Bazaar services own domain validation and call escrow or persistence through existing interfaces. They must not bypass recovery or maintenance because a request is otherwise valid.
- Escrow owns prepare, custody, commit, delivery, compensation, claims, journal lineage, replay, and recovery. Domain services may interpret a durable result but may not synthesize one.
- Persistence and configuration owners publish validated snapshots and durable revisions. Readers do not mutate shared state during projection.
- Market capability and page services project authoritative state. They do not enable modules or repair runtime state as a side effect of reads.
- Schedulers run on the logical server with bounded work and the same readiness and mutation permits as interactive requests. Expiry must serialize against bids, orders, cancellation, shutdown, and recovery.

### Identity and Revision Contract

| Identity | Creation and ownership | Required propagation | Conflict behavior |
|---|---|---|---|
| Request UUID | Created once by the initiating workflow and validated server-side | Packet, intent, custody, commit, claim, response, replay receipt, logs | Same fingerprint replays; different fingerprint fails closed |
| Transaction or commit UUID | Deterministically derived or durably assigned by the authoritative service | Ledger, stock, custody, history, recovery, claims | Duplicate application forbidden; missing lineage enters recovery |
| Session or route nonce | Server session owner | Navigation and session-scoped requests only | Stale or foreign nonce rejects without mutation and client requests fresh capability |
| Listing, offer, product, or order revision | Owning catalog or market service | Preview, request, preflight, commit, response | Stale revision yields stable conflict; no implicit overwrite |
| Configuration generation | Configuration owner or durable rule snapshot | New contract creation and any in-flight semantic lease | Existing durable work keeps captured semantics; new work uses the current valid generation |
| Claim identity | Escrow or claim owner | Durable claim, delivery attempt, receipt, client presentation | Repeated collection delivers at most once and retains exact remainder |
| Recovery lineage | Escrow journal and checkpoint owner | Every durable event, replay cursor, checkpoint, maintenance and repair action | Mismatch stops mutation and preserves evidence |

### Concurrency and Performance

- All authoritative Minecraft state mutation executes on the logical server thread or through an existing thread-safe persistence boundary. Network decoding and asynchronous work must enqueue mutation rather than touch world state off-thread.
- Competing buyers, sellers, bidders, orders, claim collectors, reloads, expiry ticks, and shutdown are serialized by expected revisions, reservations, or existing mutation permits. Last-writer ambiguity is not an acceptable result.
- Per-tick and packet work remains bounded. Tests must detect unbounded scans of all listings, orders, players, registries, or claims in hot paths.
- Logs are one bounded actionable record per failed workflow or recovery identity, not per-tick spam. They include stable identifiers and state codes but exclude private NBT, credentials, and raw player data.
- Optional economy or storage integrations remain behind runtime checks. Absence must produce a supported unavailable path and must not prevent dedicated-server startup.

## Provider and Module Lifecycle Contract

| Lifecycle event | New mutations | In-flight durable work | Claims and recovery | Client projection | Required proof |
|---|---|---|---|---|---|
| Server starting | Rejected until all required owners are bound and verified | None except replayed durable work | Recovery begins from preserved lineage | Starting or unavailable, no false navigation | Delayed binding and startup trace |
| Runtime recovering or maintenance | Rejected fail closed | Resumed only by the recovery contract | Diagnostics and safe claim visibility remain available as integrity permits | Actionable recovery state | Corrupt or interrupted fixture and recovery result |
| Module enabled but not ready | Rejected | Existing durable work remains owned | Claims remain reachable | Not advertised as usable | Enable-before-readiness test |
| Module ready | Allowed subject to normal validation | Executes through durable state machines | Collection available | Capability revision enables route | Capability refresh without relog |
| Module frozen | New value mutation rejected | Frozen at documented safe boundary | Claims remain reachable | Visible as unavailable with reason | Freeze during active workflow |
| Module draining | New work rejected | Existing committed work settles or becomes recoverable | Claims remain reachable | No new navigation to mutation | Drain and shutdown test |
| Module disabled | New work rejected | No hidden abandonment of durable state | Claims remain reachable independently | Removed or marked unavailable | Disable, reconnect, and claim collection |
| Provider unavailable | Provider-dependent mutation rejected before unsafe custody, or durable custody retained for recovery | No repeated debit or credit | Compensation or claim path remains exact | Stable provider-unavailable or recovery result | Fault, retry, restart, and reconciliation |
| Provider restored | New work uses current valid config; old request UUID resolves previous state first | Recoverable work resumes once | Claims still collect exactly once | Fresh server snapshot | Restoration and replay test |
| Server stopping | Rejected after quiesce gate | Commit finishes to a durable boundary or records recovery | Claims and lineage persisted | Sessions invalidated | Stop at each durable boundary and restart |

Configuration reload never mutates old durable semantics in place. A valid reload publishes a complete new generation. New requests use it after publication; prepared or committed work retains the rule, fee, denomination, provider, and module semantics captured by its durable identity. An invalid reload preserves the entire prior valid snapshot and identifies the source and field.

## Failure, Recovery, and Edge Cases

| Scenario | Detection | Required behavior | Recovery or rollback | Regression proof |
|---|---|---|---|---|
| Malformed or oversized payload | Decoder or semantic bounds | Reject before allocation growth or mutation; no private echo | None; client receives bounded error or protocol disconnect | Boundary and fuzz corpus for every changed codec |
| Wrong-side or unauthenticated request | Network context | Reject before reading private state | None | Direct wrong-side and missing-sender tests |
| Stale route nonce after reconnect | Session registry | Reject mutation, expire old client pending state, issue fresh capability route | Fresh session and snapshot | Disconnect between preview and commit |
| Same UUID and same fingerprint replay | Durable receipt | Return the original terminal result without new debit, stock change, delivery, rate charge, or event | Reproject current state | Duplicate packet before and after restart |
| Same UUID with different semantics | Fingerprint mismatch | Fail closed and log bounded conflict | Preserve original durable result | Conflicting replay test |
| Delayed escrow readiness | Runtime state | Deny mutations and avoid advertising usable modules | Automatically republish capability when ready | Start with blocked recovery queue, then release |
| Module disabled between screen open and action | Authoritative module state | Reject action; do not trust stale screen; claims remain accessible | Client returns to an allowed route | Toggle with two clients and stale snapshot |
| Catalog or product reload fails | Candidate validation | Keep complete last-known-good snapshot and current module usability if safe | Correct file and reload again | Malformed JSON or missing registry fixture |
| Reload races with prepared request | Config generation mismatch | Request uses captured durable semantics or rejects before custody; never mixes generations | Retry as a new request only after original resolves | Fault at validation, prepare, and commit boundaries |
| Finite stock contention | Expected revision or reservation conflict | Exactly one allowed commit per available stock; losers remain unchanged | Fresh quote and new request | Two clients race last unit |
| Player shop block or storage changes | Ownership, block, offer, and receipt validation | Reject or use durable receipt-backed reservation; never consume unmatched value | Release reservation or enter claim recovery | Break block or mutate storage during request |
| Economy provider denial or exception | Provider result and receipt reconciliation | Fail closed before commit or retain exact durable custody for compensation or claim | Restore provider, replay same UUID, reconcile once | Fault each provider operation |
| Provider reports success but response is lost | Durable receipt or balance reconciliation | Do not repeat blind mutation | Resolve receipt and continue or enter maintenance | Dropped provider response simulation |
| Inventory becomes full after commit | Delivery result | Keep committed value in durable item or cash claim | Collect after space becomes available | Fill final slot between prepare and delivery |
| Client disconnects before response | Connection state | Continue or recover server-owned durable work; do not infer rollback from disconnect | Reconnect and replay original UUID or query snapshot | Disconnect at every durable boundary |
| Server stops before durable prepare | No journaled intent | No mutation persists | New request may start after restart | Stop before prepare |
| Server stops after custody but before commit | Durable intent and custody, no terminal commit | Recovery resumes or compensates exactly once | Automated recovery from copied fixture | Crash cut after each persisted step |
| Server stops after commit before delivery | Commit and undelivered custody | Deliver once or create/retain claim | Reconnect or claim collection | Restart and repeated delivery attempt |
| Server stops after delivery before response | Delivery and terminal receipt | Replay reports success without redelivery | Reconnect and retry same UUID | Response-loss crash cut |
| Auction bid races expiry or buy-now | Expected listing state and server time | One serialized lifecycle result, correct release of losing holds | Recovery settles receipts and claims | Two clients plus scheduler boundary |
| Bazaar match races cancel or expiry | Order revisions and durable match receipt | One valid transition per lot and hold, exact fill conservation | Resume matching or cancellation from receipt | Two-sided orders at tick boundary |
| Claim collection is retried | Claim delivery receipt and remainder | Deliver each unit or currency amount at most once; preserve remainder | Retry same claim identity after capacity or provider recovery | Full inventory, partial capacity, disconnect, restart |
| Recovery lineage or conservation mismatch | Verification snapshot | Enter maintenance, stop mutation, preserve all evidence | Restore one complete matching backup or execute documented repair | Tampered or mismatched fixture |
| Optional provider or storage mod absent | Runtime integration discovery | Dedicated server starts; dependent action is clearly unavailable | Install supported dependency or use supported source | Start without optional dependency |
| Arithmetic boundary or negative value | Checked arithmetic and validation | Reject before mutation | None | Maximum quantity, price, fee, and total matrix |
| Scheduler repeats after restart | Durable expiry or recovery receipt | Repeated tick is idempotent | Resume from recorded cursor | Restart around expiry and recovery schedule |
| EXT-004 unavailable | Environment preflight | Mark dependent runtime rows blocked, run independent proof only, do not claim phase completion | Supply the required isolated server and two clients | Exact blocker readback in completion packet |

Unexpected maintenance, conservation mismatch, duplicate terminal effect, partial catalog publication, inaccessible claim, or unusable player state is a stop condition. Preserve the world copy, all FutureShops durable files, configuration, logs, exact binaries, and environment manifest. Recovery restores one complete matching snapshot or uses the already verified repair workflow; it never deletes selected state.

## Verification Matrix

| Requirement or task | Static or unit | Integration | Real workflow or runtime | Negative and recovery | Evidence artifact |
|---|---|---|---|---|---|
| `P006-TASK-001` | Manifest schema and hash checks | Upstream packet linkage | Environment startup without mutation | Missing dependency and blocker classification | Entry manifest |
| `P006-TASK-002` | Source-to-interface coverage | Call path and identity propagation | One dry-run trace per workflow family | Unmapped ingress or terminal state blocks execution | Integration matrix |
| `P006-TASK-003` | Packet codec, bounds, handshake, result-code, client tracker tests | Handler-to-service and response-correlation tests | Login, open, stale route, timeout, reconnect | Wrong side, spoofed identity, replay conflict, delayed readiness | Protocol and readiness packet |
| `P006-TASK-004` | Pricing, stock, offer, cart, inventory proof, replay tests | Shop-to-economy-to-escrow-to-claim tests | Server and player shop buy, sell, barter, cart with two clients | Last-stock race, stale offer, full inventory, disconnect, restart | Shop workflow and conservation packet |
| `P006-TASK-005` | Provider, currency, denomination, mint, lease, ATM planner tests | Wallet and physical funding through escrow and claims | ATM deposit, withdrawal, cash claim, supported payment sources | Provider unavailable, config reload, invalid cash, full inventory, response loss | Provider and ATM packet |
| `P006-TASK-006` | Auction and Bazaar command, state machine, fee, expiry, replay tests | Market sessions, controls, escrow, pages, profiles, claims | Create, bid, buy-now, cancel, order, match, expire, claim | Disable, stale revision, concurrency, provider fault, disconnect, restart | Market lifecycle packet |
| `P006-TASK-007` | Lifecycle transition and generation tests | Cross-domain reload and scheduler integration | Delayed start, enable, freeze, drain, disable, stop, restart, reconnect | Invalid reload, stop during work, stale client snapshot | Lifecycle and reload timeline |
| `P006-TASK-008` | Fault-point completeness and invariant properties | Crash-cut and recovery integration | Restart copied worlds at every durable boundary | Corruption, partial persistence, lost response, repeated recovery | Fault manifest and recovery report |
| `P006-TASK-009` | Failing regression for each issue | Affected blast-radius suite | Exact original or equivalent runtime reproduction | Before-and-after failure proof | Issue and change evidence packet |
| `P006-TASK-010` | Full `test` result | Applicable data and GameTest plus build | Dedicated server, client, EXT-004 two-client matrix | Restart, reconnect, reload, recovery, provider faults | Exact-revision verification packet |
| `P006-TASK-011` | Documentation terminology and link checks | Source, config, command, result-code cross-check | Operator follows lifecycle and recovery procedure | Procedure stop conditions and rollback rehearsal | Documentation evidence |
| `P006-TASK-012` | Packet completeness audit | Revision and issue reconciliation | Final smoke of unchanged revision | External blocker and invalidation readback | Phase completion packet |

### Fixtures and Test Data

- Use a clean Forge world plus copied, hash-identified legacy and repaired fixtures supplied by prior phases. Do not mutate the only copy.
- Seed at least two players, one authorized operator, server shops with finite and infinite stock, a player shop with inventory-backed offers, wallet and physical-currency balances, Auction House listings, Bazaar products and opposing orders, ATM denominations, and both item and money claims.
- Include registry-only and exact-NBT items, empty and nearly full inventories, minimum and maximum valid quantities and prices, insufficient balances, stale revisions, and bounded invalid payloads.
- Record fixture configuration for all five Forge config domains and relevant server-shop and Bazaar product files. Valid reload, invalid reload, and semantic-change generations are distinct fixtures.
- Every fault schedule starts from a fresh copied fixture and identifies the last expected durable step. Reusing a post-failure world for an unrelated case invalidates the evidence.
- EXT-004 uses an isolated dedicated Forge 1.20.1 server at the exact phase revision and at least two independent clients. Integrated-server or two accounts on one unsafely shared client state do not satisfy the prerequisite.

### Expected Results and Failure Interpretation

- A passing success case proves pre-state, terminal state, exact request identity, response correlation, and conservation. Absence of an exception is insufficient.
- A passing rejection proves zero authoritative mutation and a stable actionable result.
- A passing partial-failure case proves either exact compensation or durable reachable custody and claims, followed by idempotent recovery.
- A skipped, flaky, timed-out, unavailable, or manually inferred row remains not verified. It blocks the owning matrix row and any dependent exit criterion.
- Unexpected warnings about maintenance, recovery, replay conflict, provider mismatch, missing registry state, failed delivery, or invalid persistence are failures until explained with retained evidence.
- A test that passes only after retry must identify whether the first attempt durably committed. Retry must not conceal duplicate or lost value.

### Rerun Order

For each changed support line, run the repository-required sequence: focused regressions, complete unit tests, applicable `runData`, applicable `runGameTestServer`, `build`, dedicated-server smoke, client smoke, multiplayer for networked state, restart and reconnect, JAR inspection, and complete diff inspection. On Forge Linux, use `bash ./gradlew` for wrapper tasks. A material change reruns every downstream row it can affect.

## Documentation, Operations, and Release

This phase updates only documentation supported by integrated behavior. Phase 007 performs the final plan-wide reconciliation.

- Update `DOCUMENTATION.md` with the actual ingress-to-authority flow, readiness ownership, request and recovery identity propagation, provider lifecycle, module lifecycle, reload generation behavior, and failure terminal states.
- Update `README.md` only if verified user or administrator behavior, setup, compatibility, configuration, or commands changed.
- Update `docs/markets-guide.md` for Auction House, Bazaar, module availability, claims, reconnect, and failure behavior that changed or was clarified by repair.
- Update `docs/physical-currency-atm.md` for provider, deposit, withdrawal, cash-claim, full-inventory, retry, and recovery behavior.
- Update `docs/backup-restore.md` for exact shutdown, complete snapshot scope, startup validation, stop conditions, and non-destructive recovery when Phase 006 proves a changed procedure.
- Update the relevant config guides for validated reload, last-known-good fallback, configuration generations, provider changes, and module transitions. Do not document an invented key or unimplemented hot-reload behavior.
- Update `docs/community-bug-regression-test-gaps.md` or the repository's current verification guide with the exact multiplayer and fault matrix, remaining external evidence, and safe reproduction instructions.
- Update `docs/README.md` when a focused document is added or its route changes. Preserve the established layout and filename casing.
- Operator evidence names the exact revision, configuration, mods, server and client commands, expected result, failure stop condition, log location, backup scope, and recovery action.
- Issue and pull-request state records verified behavior and exact evidence without leaking private NBT, player data, secrets, or exploit-enabling detail. Public GitHub text follows repository language and punctuation rules.
- No candidate artifact is published, no public release or announcement is created, and no stable designation or release tag is authorized by this phase.

## Risks and Evidence Invalidation

| Risk | Prevention | Detection | Recovery | Evidence invalidated | Reverification |
|---|---|---|---|---|---|
| Matrix omits an ingress or terminal path | Source-backed registration and service inventory with reverse trace | Unmapped packet, command, scheduler, session, or claim owner | Add the row before mutation testing | Integration coverage and closure audit | Tasks 002 through affected domain |
| Client state is mistaken for authority | Trace every decision to server owner and durable receipt | Result differs after reconnect or fresh snapshot | Repair server boundary and client reconciliation | Protocol, workflow, reconnect proof | Tasks 003, affected domain, 007, 010 |
| Fault test creates a production bypass | Use existing or test-scoped injection only; inspect JAR | Production-accessible toggle or hidden path | Remove bypass and reopen security audit | Security, fault, JAR evidence | Phase 004 affected audit plus Tasks 008 and 010 |
| Shared fixture contamination | Immutable seeds and fresh copied worlds | Hash or baseline mismatch | Discard only the derived copy and recreate safely | All cases using that copy | Rerun from verified seed |
| Provider result cannot be reconciled | Require real receipt or deterministic adapter evidence | Balance and ledger disagree or outcome is unknown | Freeze mutation, preserve evidence, recover once | Provider, ATM, every dependent conservation row | Tasks 005, 007, 008, 010 |
| Module becomes visible before ready | Single authoritative access policy and revisioned capability | Client opens unusable route or action reaches unready service | Fail closed and republish corrected capability | Readiness, navigation, reconnect rows | Tasks 003, 006, 007, 010 |
| Claims become coupled to disabled module | Independent claim access contract | Claim route disappears or collection rejects only due to module disable | Restore claim route without enabling mutation | Market, ATM, lifecycle, recovery proof | Tasks 005 through 008 and 010 |
| Reload changes in-flight semantics | Captured durable config generation | Mixed fees, denominations, rules, or provider outcome | Resolve old work under captured semantics, new work under new snapshot | Reload and all affected workflow rows | Tasks 005, 006, 007, 008, 010 |
| Scheduler races shutdown or player action | Logical-server serialization and durable expected revision | Duplicate expiry, fill, settlement, or claim | Replay receipt and recovery lineage reconcile once | Market lifecycle and restart evidence | Tasks 006 through 010 |
| New finding repaired before issue | Mandatory duplicate-before-repair checkpoint | Diff lacks a prior canonical issue timestamp or record | Stop, revert or hold the repair, create or link issue first | Traceability and repair acceptance | Task 009 and affected domain |
| Cross-line contamination | Independent ancestry, toolchains, APIs, and diffs | Forge code or metadata appears on NeoForge line or reverse | Remove through reviewed line-specific change | Compatibility and all affected evidence | Line-specific build, runtime, JAR, diff |
| Late repair invalidates upstream proof | Explicit blast-radius and evidence graph | Changed interface, schema, persistence, security, or command boundary | Reopen the affected earlier audit and rerun | Named Phase 004, Phase 005, and Phase 006 packets | All downstream checks at new revision |
| EXT-004 is absent or incomplete | Preflight environment and required capture list | Fewer than two independent clients, wrong revision, missing state capture | Keep blocker open and request exact missing capability | Multiplayer-dependent CORE-REQ-013 evidence | Full EXT-004 matrix at unchanged revision |
| Sensitive runtime evidence leaks | Sanitized state schema and private confidential route | Raw NBT, credentials, private player data, or exploit detail in artifacts | Remove exposure where possible and follow security response | Affected evidence packet and public links | Sanitized recapture and review |
| Exact revision changes after verification | Record source and artifact hashes in every packet | Git or JAR hash mismatch | Mark downstream proof stale | All affected integration and runtime evidence | Complete affected matrix at new revision |

Any source change to packet registration or codecs invalidates protocol and all consuming workflow rows. Any change to readiness, module control, sessions, or capability projection invalidates navigation, lifecycle, reconnect, and every domain that uses that gate. Any change to economy, escrow, custody, claims, persistence, or configuration invalidates every affected conservation and recovery row. Any change to test fixtures or fault drivers invalidates results produced with their earlier hash.

## Phase Completion Packet

The packet is retained outside the protected plan set and contains all of the following before the phase may close or take the internal integration transition.

1. Exact Forge source revision, branch ancestry, merge and check state, Java 17, Forge 47.4.20, Gradle 8.14.4, mod and dependency inventory, dirty-state report, and configuration manifest.
2. Any independently affected NeoForge revision and Java 21 evidence, with a written reason for inclusion. If NeoForge is unaffected, record the source-backed impact decision rather than running unrelated port work.
3. Phase 005 completion-packet reference and confirmation that no unexpected entry regression exists.
4. Final subsystem interface, state-flow, readiness, identity, protocol, provider, module lifecycle, reload, and failure matrices with source references and coverage disposition for every row.
5. Immutable fixture manifest and hashes, exact fault-point manifest, isolated world-copy procedure, and sanitized environment topology.
6. Focused regression results for every protocol and workflow family and every repaired defect.
7. Complete Forge `test`, applicable `runData`, applicable `runGameTestServer`, `build`, dedicated-server smoke, client smoke, restart, reconnect, reload, and recovery results in required order.
8. EXT-004 server and two-client evidence with finite and infinite stock fixtures, Auction House, Bazaar, player shop, ATM, claim, disconnect, retry, restart, and reconnect rows, or the exact current unavailable prerequisite statement.
9. Pre-state, intermediate durable state, terminal state, and conservation reports for every value-bearing success and injected failure.
10. One canonical issue-before-repair record for every verified finding, including duplicate search, failing evidence, implementation, review, merged revision, checks, and rerun links. Confidential findings use the private route.
11. Evidence invalidation graph showing which upstream and Phase 006 checks were reopened by each material change and proof that every affected result reran at the final phase revision.
12. Documentation diff, link and terminology results, operator procedure rehearsal, and verification that no secrets, raw private data, local-only paths, logs, caches, generated worlds, or unrelated edits entered tracked output.
13. JAR and complete diff inspection sufficient to prove no test-only fault hook, loader contamination, debug output, or hidden bypass ships.
14. Residual blocker statement. The only permitted external runtime blocker owned by this phase is the precisely stated EXT-004 evidence gap; any repository-owned defect remains a failed exit gate.
15. Downstream handoff with exact revisions, inherited EXT-001 through EXT-003 blockers, the Phase 006 EXT-004 result or blocker, issue inventory, required Phase 007 reruns, and confirmation that publication remains excluded.

## Next Transition

After every repository-controlled Phase 006 task is integrated, every applicable check is green, and the completion packet is audited, fetch the authoritative support branches and verify that the exact merged revisions match the packet. Then read `phases/plan-phase-007.md` through EOF and begin its first entry gate from those revisions.

Do not start `CORE-PHASE-007` from an unmerged Phase 006 branch, a stale local branch, or a revision whose invalidated evidence has not rerun. If EXT-004 passed, hand off its exact environment and evidence links. If EXT-004 remains unavailable, hand off the precise blocker under the master-defined internal integration gate; keep `CORE-REQ-013`, `CORE-REQ-015`, `CORE-REQ-019`, and the final completion endpoint blocked, and do not represent Phase 006 as fully complete. Any repository-owned defect, conservation mismatch, inaccessible claim, failed recovery, failed required check, or unintegrated repair prevents the transition.
