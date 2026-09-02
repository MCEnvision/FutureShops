# Phase 001 Execution Plan

> **Plan ID:** PLAN-PHASE-001
> **Phase ID:** CORE-PHASE-001
> **Owner:** FutureShops repository
> **Classification:** MANDATORY
> **Master plan:** [plan.md](../plan.md)
> **Phase sequence:** 001 of 003

## Purpose and Ownership

This phase delivers the complete server-authoritative economy implementation behind the public provider API and restart-only provider selection contract frozen by `CORE-PHASE-000`. It makes the selected provider lifecycle, exact minor-unit arithmetic, durable transaction coordination, custody, claims, every monetary entry point, presentation, diagnostics, security, and recovery behave as one fail-closed system.

This phase owns `CORE-REQ-004` through `CORE-REQ-016` inclusive. The master owns product scope, locked decisions, requirements, global phase order, and the final endpoint. This file owns only the dependency-ordered execution and proof for `CORE-PHASE-001`. It does not redefine the public API or configuration contract established by `CORE-PHASE-000`, implement the Pixelmon or Vault integrations owned by `CORE-PHASE-002`, perform final release-candidate validation owned by `CORE-PHASE-003`, or authorize publication.

## Evidence-Based Entry State

| Evidence class | Area | Finding | Source or command | Freshness condition |
|---|---|---|---|---|
| VERIFIED | Master contract | `CORE-PHASE-001` owns `CORE-REQ-004` through `CORE-REQ-016`, and its entry gate is an integrated `CORE-PHASE-000` with a stable public contract | `docs/general/plan.md`, phase catalog and requirement ownership freeze | Invalidated by an owner-authorized master or plan-index revision |
| OBSERVED | Existing economy boundary | The current boundary includes `EconomyProvider`, `InternalEconomyProvider`, and `BalanceManager`; actual callers and persistence behavior require a fresh trace before edits | `EVD-REP-002` in `docs/general/plan.md` | Invalidated by changes to these components or their call graph after the trace |
| VERIFIED | Provider contract dependency | Phase 000 must supply the frozen provider registry, compatibility version, metadata, lifecycle capability, query, idempotent mutation, durable outcome lookup, and restart-only selection contracts | `CORE-REQ-002`, `CORE-REQ-003`, `DEC-004` through `DEC-009` | Invalidated by any Phase 000 API or configuration contract change |
| VERIFIED | External feasibility dependency | `EXT-003` must prove exact-value and durable-outcome feasibility before this phase can rely on provider receipt semantics | `EXT-003`, `CORE-REQ-007` | Invalidated by a different provider API shape or loss of the reviewed feasibility evidence |
| VERIFIED | Continuation issue | Plan authoring already created the required open issue after plan validation. This phase preserves its identity and state without modifying or closing it. | `DEC-015`, `EXT-007`, `EVD-GH-001` authoring evidence | Any issue drift is recorded for Phase 003 revalidation; it does not authorize issue mutation here. |
| PROPOSED | Surface inventory | The master surface table is the minimum inventory; implementation discovery must add every concrete legacy API, command, packet, screen action, event, lifecycle hook, analytics path, and rollback route without narrowing it | Required behavior by surface, `CORE-REQ-009`, `RISK-004` | Re-run after any monetary call-site, registration, packet, command, or market-flow change |
| UNKNOWN | Current persistence schema | Existing request, transaction, custody, claim, analytics, bill, and balance schemas and migration versions require inspection before schema design | `CORE-REQ-007`, `CORE-REQ-008`, `CORE-REQ-010`, `CORE-REQ-011` | Must be established before `P001-TASK-006`; invalidated by saved-data changes |
| UNKNOWN | Current test harness coverage | Focused tests, property-test support, persistence fixtures, GameTests, and runtime smoke procedures must be inventoried against repository commands | Master verification contract | Reconfirm when build scripts, source sets, test fixtures, or run configurations change |

Phase execution must stop at entry if `CORE-PHASE-000` is not integrated, any `EXT-001` through `EXT-006` Phase 000 exit proof is unresolved, the public API/configuration contract is not frozen, or `EXT-003` cannot prove exact and recoverable mutation semantics. A contract gap is not permission to guess an adapter API or weaken `DEC-007` or `DEC-009`.

## Scope Boundaries

### Included Scope

- `CORE-REQ-004`: implement and enforce `UNRESOLVED`, `READY`, `MISSING`, `INCOMPATIBLE`, `FAILED`, `RECOVERY_REQUIRED`, and `STOPPED` lifecycle behavior.
- `CORE-REQ-005`: establish one logical-server orchestration route for every balance query and mutation, with server-side identity, permission, readiness, request, amount, product, and custody validation.
- `CORE-REQ-006`: use provider-owned currency metadata and checked signed integer minor units without authoritative floating-point conversion.
- `CORE-REQ-007`: persist root and leg identities, intent, provider receipts or lookup facts, outcomes, recovery state, and compensation relationships so every logical leg occurs at most once.
- `CORE-REQ-008`: keep internal balances inside the internal provider, external balances inside the external provider, and FutureShops records limited to market, custody, claim, request, outcome, and confirmed analytics facts.
- `CORE-REQ-009`: inventory, route, and test every monetary surface, including APIs, administration, analytics, shops, carts, player shops, owner proceeds, claims, pay, money items, fees, refunds, events, rollback, reload, startup, and shutdown.
- `CORE-REQ-010`: preserve independent provider data across restart-only selection changes, prohibit transfer or conversion, retain originating-provider recovery binding, and apply starting balance only to eligible new internal balances.
- `CORE-REQ-011`: keep physical money registration and decoding save-safe while disabling activation, minting, deposit, withdrawal, redemption, and future ATM mutations outside ready internal mode; add no ATM UI or command.
- `CORE-REQ-012`: present provider metadata and lifecycle accurately and accessibly in screens, commands, tooltips, and public snapshots while keeping browsing and pure barter usable.
- `CORE-REQ-013`: provide concise, structured, privacy-safe, rate-bounded lifecycle and transaction diagnostics with stable correlation.
- `CORE-REQ-014`: freeze provider lookup outside hot paths and keep provider calls, allocations, retries, logging, and transaction work bounded by the number of logical legs.
- `CORE-REQ-015`: validate untrusted API, packet, command, config, metadata, value, receipt, item, and persisted inputs; contain provider failures; preserve permissions, identity, optional linkage isolation, and reviewed artifact boundaries.
- `CORE-REQ-016`: deliver deterministic restart recovery, durable custody and claims, backup and restore procedures, selection rollback, reconciliation, and safe operator blockers without guessed external balance rewrites.

### Explicit Exclusions

- `CORE-REQ-001` through `CORE-REQ-003` remain owned by `CORE-PHASE-000`; this phase consumes rather than silently changes their platform, API, registry, metadata, and selection contracts.
- `CORE-REQ-017`, `CORE-REQ-018`, `EXT-001` through `EXT-006`, and concrete Pixelmon or `vault` bridge code and runtime validation belong to `CORE-PHASE-002` or its prerequisite phase. Fixture external providers are used here only to prove the generic contract.
- `CORE-REQ-019` through `CORE-REQ-021` and Phase 003 verification and update work for `CORE-REQ-022` belong to `CORE-PHASE-003`. The actual continuation issue already exists from validated plan authoring and remains open and unmodified in this phase.
- `FUT-001` and `FUT-002` are tracking subjects only. No `3.0.0` feature or Forge `1.20.1` implementation enters this phase.
- `FUT-003` and `DEC-011` exclude any ATM screen or command. This phase only ensures no present or future ATM mutation can bypass the provider policy.
- `FUT-004` excludes additional production adapters. Test fixtures do not create compatibility claims.
- `NG-001` through `NG-009` remain excluded, especially publication, balance migration, external artifact bundling, hot switching, fallback, balance mirroring, telemetry, remote authority, and weakened custody or save safety.

## Phase Contract

### CORE-PHASE-001 — Server-Authoritative Economy Orchestration

**Objective:** Integrate every FutureShops monetary surface with one fail-closed, exact, durable, server-authoritative provider transaction boundary and prove safe behavior for internal, fixture-external, unavailable, failed, recovery, retry, reconnect, and restart states.
**Owner:** FutureShops repository
**Dependencies:** CORE-PHASE-000, CORE-REQ-001, CORE-REQ-002, CORE-REQ-003, DEC-003, DEC-004, DEC-005, DEC-006, DEC-007, DEC-008, DEC-009, DEC-010, DEC-011, DEC-014, EXT-003
**Canonical requirements:** CORE-REQ-004, CORE-REQ-005, CORE-REQ-006, CORE-REQ-007, CORE-REQ-008, CORE-REQ-009, CORE-REQ-010, CORE-REQ-011, CORE-REQ-012, CORE-REQ-013, CORE-REQ-014, CORE-REQ-015, CORE-REQ-016
**Documentation and release impact:** Produce implementation-matched API behavior notes, configuration behavior, monetary-surface guidance, migration and price-review guidance, bill behavior, diagnostics, backup, recovery, restore, rollback, verification, and known-limit documentation. These are phase evidence and inputs to the final documentation audit in `CORE-PHASE-003`; no release or publication occurs, and the authoring-created continuation issue is not modified, duplicated, or closed.
**Next transition:** `CORE-PHASE-002`, only after Phase 001 is integrated and all phase-owned evidence passes.

**Entry criteria**

- The integrated result of `CORE-PHASE-000` is the approved base, and no later phase branch is stacked on an unintegrated result.
- The public provider API, compatibility version, registration window, registry freeze point, currency metadata contract, result taxonomy, durable outcome lookup, and restart-only configuration are stable and have passing Phase 000 evidence.
- `EXT-003` proves that an adapter can produce exact minor-unit semantics and a durable receipt or outcome lookup that distinguishes confirmed, rejected, duplicate-completed, and ambiguous results.
- The current economy call graph, persistence formats, configuration behavior, packet and command entry points, screen actions, money item registrations, analytics paths, and available test harnesses are inventoried before implementation changes.
- Representative clean and upgraded world copies, bill-containing inventories, internal-balance fixtures, external-provider fixtures, malformed data fixtures, and crash injection controls are available without using the only production data.
- The immutable goal and master plan remain unchanged except for authorized plan-set integration, and no unresolved product-contract conflict exists.

**Implementation scope**

- CORE-REQ-004, CORE-REQ-006, CORE-REQ-007, and CORE-REQ-013 establish lifecycle, value, request, outcome, and rejection models before routing gameplay surfaces.
- CORE-REQ-007 and CORE-REQ-016 create a durable coordinator whose ordering makes intent persistent before external effects and whose recovery never guesses an unknown effect.
- CORE-REQ-005, CORE-REQ-008, and CORE-REQ-009 move all monetary reads and writes behind the orchestrator while leaving `BalanceManager` solely within the internal provider.
- CORE-REQ-009, CORE-REQ-012, CORE-REQ-013, and CORE-REQ-016 integrate simple one-leg surfaces, multi-leg custody and owner-proceeds flows, lifecycle, presentation, diagnostics, and operator recovery.
- CORE-REQ-008, CORE-REQ-010, CORE-REQ-011, and CORE-REQ-016 preserve existing item, world, and internal balance data while introducing explicit versioning and defensive migrations for new transaction facts.
- CORE-REQ-004, CORE-REQ-014, CORE-REQ-015, and CORE-REQ-016 complete the owned coverage by proving each surface under `internal` ready, fixture external ready, external unavailable or unsafe, and `RECOVERY_REQUIRED` where meaningful.

**Execution order**

1. `P001-TASK-001` establishes the fresh economy call graph, persistence inventory, test inventory, and traceable surface matrix for `CORE-REQ-005`, `CORE-REQ-008`, and `CORE-REQ-009`.
2. `P001-TASK-002` freezes phase-local invariants against the integrated Phase 000 API for `CORE-REQ-004`, `CORE-REQ-005`, `CORE-REQ-006`, and `CORE-REQ-007`.
3. `P001-TASK-003` implements the deterministic lifecycle state machine and immutable selected-provider runtime handle for `CORE-REQ-004` and `CORE-REQ-014`.
4. `P001-TASK-004` implements exact minor-unit validation, metadata freezing, checked arithmetic, and typed rejection for `CORE-REQ-006` and `CORE-REQ-015`.
5. `P001-TASK-005` defines server-owned root and child identity derivation, provider receipt validation, and outcome lookup rules for `CORE-REQ-005` and `CORE-REQ-007`.
6. `P001-TASK-006` implements versioned durable intent, outcome, recovery, compensation, custody, claim, and analytics-fact persistence for `CORE-REQ-007`, `CORE-REQ-008`, and `CORE-REQ-016`.
7. `P001-TASK-007` implements the server-authoritative coordinator and crash-safe one-leg ordering for `CORE-REQ-004`, `CORE-REQ-005`, and `CORE-REQ-007`.
8. `P001-TASK-008` routes public API, administrative query and mutation, pay, fee, refund, event, analytics, rollback, reload, startup, and shutdown surfaces for `CORE-REQ-005`, `CORE-REQ-008`, and `CORE-REQ-009`.
9. `P001-TASK-009` routes server shops, carts, and checkout with preflight validation and custody ordering for `CORE-REQ-007` and `CORE-REQ-009`.
10. `P001-TASK-010` routes player shops, buyer debit, owner proceeds, offline delivery, claims, and idempotent compensation for `CORE-REQ-007`, `CORE-REQ-009`, and `CORE-REQ-016`.
11. `P001-TASK-011` enforces provider-switching, independent balance ownership, starting-balance, price interpretation, and unresolved-provider binding for `CORE-REQ-008` and `CORE-REQ-010`.
12. `P001-TASK-012` preserves physical bill data while gating activation, minting, deposit, withdrawal, and redemption, and proves ATM absence for `CORE-REQ-011`.
13. `P001-TASK-013` integrates server-supplied capability snapshots, localized presentation, disabled actions, stale-state handling, browsing, and pure barter for `CORE-REQ-004`, `CORE-REQ-005`, and `CORE-REQ-012`.
14. `P001-TASK-014` adds structured diagnostics, request correlation, privacy controls, and log-volume bounds for `CORE-REQ-013`, `CORE-REQ-014`, and `CORE-REQ-015`.
15. `P001-TASK-015` hardens malformed inputs, permissions, replay, concurrency, provider exceptions, persistence decoding, and optional-class boundaries for `CORE-REQ-005`, `CORE-REQ-007`, and `CORE-REQ-015`.
16. `P001-TASK-016` validates bounded work, frozen lookup, no shadow synchronization, no retry storms, and representative operation cost for `CORE-REQ-014`.
17. `P001-TASK-017` implements and rehearses restart recovery, backup, restore, provider correction, selection rollback, and claim reconciliation for `CORE-REQ-007`, `CORE-REQ-010`, and `CORE-REQ-016`.
18. `P001-TASK-018` executes CORE-PHASE-001 focused, unit, property, persistence, migration, GameTest, server, client, multiplayer, reconnect, restart, crash, security, and full-build proof for every owned requirement.
19. `P001-TASK-019` executes CORE-PHASE-001 documentation updates for all affected user, maintainer, API-behavior, migration, operations, recovery, troubleshooting, and verification guidance.
20. `P001-TASK-020` audits the complete surface matrix, diff, evidence invalidation, phase integration state, and handoff to `CORE-PHASE-002`.

**Required evidence**

- A complete call graph and surface matrix mapping each concrete entry point to server preflight, coordinator, provider call count, persistence transitions, custody transitions, rejection behavior, presentation, and tests.
- Lifecycle state-transition and fault-injection evidence covering every declared state and prohibited transition.
- Boundary and property tests for exact conversion, checked arithmetic, aggregation, precision, formatting, and invalid metadata.
- Versioned persistence fixtures and migration tests for clean, old, current, unknown-newer, truncated, malformed, and crash-interrupted records.
- A crash-point matrix for every one-leg and multi-leg flow, including the gap after provider effect and before local outcome persistence.
- Receipt and outcome-lookup evidence proving retry and restart converge without duplicate debit, credit, refund, or compensation.
- Focused tests for every surface and negative proof that unavailable or unauthorized attempts make no provider, custody, item, listing, claim, or analytics-success mutation.
- Applicable GameTests for world, inventory, shop, custody, claim, bill, and restart-sensitive behavior.
- Standard NeoForge dedicated-server and client smoke evidence with the same build, plus multiplayer, reconnect, stale-snapshot, replay, concurrent-duplicate, and delayed-readiness evidence.
- Provider-switch, upgraded-world, bill-retention, internal-balance dormancy, no-migration, no-shadow-ledger, no-ATM, and no-bypass evidence.
- Sanitized lifecycle, failure, recovery, and compensation logs; performance observations; security review; secrets scan; jar and dependency isolation checks relevant to this phase.
- Documentation and operator-runbook rehearsal tied to the source commit and test environment.

**Exit criteria**

- Every `CORE-REQ-004` through `CORE-REQ-016` acceptance criterion has traceable passing evidence against ready internal and fixture external providers and all applicable unavailable and recovery states.
- Every discovered monetary surface is present in the matrix, routes through the server-authoritative coordinator, and has positive and forbidden-side-effect proof.
- Every monetary write outside `READY` is rejected before provider-independent custody, item, listing, order, cart, or market mutation; browsing and pure barter remain usable.
- Exact minor-unit, lifecycle, idempotency, persistence, migration, retry, concurrency, restart, recovery, compensation, claims, provider-switch, bill-safety, localization, security, and performance gates pass.
- No external balance mirror, direct external `BalanceManager` access, hot provider discovery, fallback, hot activation, automatic balance transfer, ATM surface, or provider-bypass path remains.
- Required focused tests, complete tests, applicable data validation and GameTests, build, dedicated-server, client, multiplayer, reconnect, and restart checks pass in the master verification order.
- Phase documentation matches implemented behavior, and the completion packet identifies the integrated commit and all non-plan evidence without turning this file into a status diary.
- The phase result is integrated through the repository's required pull-request and default-branch gates before `CORE-PHASE-002` starts.
- No known mandatory phase-owned defect remains.

## Inputs and Upstream Contracts

| Input or contract | Provider | Required state | Validation | Failure behavior |
|---|---|---|---|---|
| Public provider API and compatibility version | `CORE-PHASE-000`, `CORE-REQ-002` | Stable identifiers, metadata, readiness, exact mutation, typed outcome, receipt, and lookup semantics are frozen | Compile internal and fixture providers; read Phase 000 API evidence | Stop phase implementation if required semantics are absent or unstable |
| Registry and freeze point | `CORE-PHASE-000`, `CORE-REQ-002` | Registration is deterministic and closed before monetary readiness | Accepted, duplicate, malformed, incompatible, and late fixture registration tests | Selected rejected or late provider remains unavailable until clean restart |
| Restart-only selection | `CORE-PHASE-000`, `CORE-REQ-003` | Default is `internal`; active identifier resolves once; reload only stages a future selection | Dedicated-server reload and restart evidence | Never hot switch, hot activate, or fall back |
| Exact outcome feasibility | `EXT-003` | Durable provider receipt or lookup can prove whether a mutation occurred | Review exact Phase 000 feasibility evidence and fixture behavior | Stop with product-contract blocker; never guess an outcome |
| Existing economy implementation | Current repository | `EconomyProvider`, `InternalEconomyProvider`, `BalanceManager`, and all consumers are traced | Fresh call graph and direct-access scan | Untraced monetary callers block surface integration |
| Existing saved data | Current repository and representative `2.2.1` copies | Internal balances, bills, shops, carts, claims, analytics, and world data can be loaded without loss | Read-only fixture inventory and backup hashes | Preserve original copies; do not write new schema until migration is defined |
| Provider metadata | Frozen selected provider | Identifier, currency names, precision, API version, and capabilities are valid and immutable for lifecycle | Startup validation and boundary tests | Enter `INCOMPATIBLE`; do not expose internal metadata as substitute |
| Logical-server identity and permissions | Minecraft server and existing authorization rules | Actor, target, ownership, menu context, and command permission are current | Packet, command, public API, and reconnect tests | Reject before provider call or FutureShops mutation |
| Reviewed external artifact boundaries | `EXT-001`, `EXT-004`, `EXT-005` evidence | No unreviewed or bundled external implementation is needed for generic core work | Dependency, classpath, archive, and jar boundary review | Block only affected integration proof; keep core and standard NeoForge safe |

## Outputs and Downstream Contracts

| Output or contract | Consumer | Guaranteed state | Compatibility or versioning | Evidence |
|---|---|---|---|---|
| Economy orchestration boundary | Every FutureShops monetary surface and `CORE-PHASE-002` adapters | One logical-server route owns readiness, validation, request identity, mutation ordering, and typed result | Uses frozen Phase 000 API compatibility version; implementation internals remain private | Call graph, surface matrix, boundary tests |
| Deterministic lifecycle runtime | Client presentation, commands, markets, adapters, operators | State is one of the seven declared values and transitions are server-owned, fail closed, and restart-safe | State identifiers and meanings remain stable public behavior | State-transition tests and runtime logs |
| Exact money model | Providers, shops, carts, player shops, commands, UI | Checked signed integer minor units and frozen selected-provider metadata govern every authoritative value | No lossy conversion; configured integer price magnitude is preserved | Property tests, conversion tests, UI evidence |
| Durable transaction schema | Recovery, operations, adapters, Phase 003 validation | Root, leg, provider, intent, receipt, outcome, status, recovery, and compensation facts survive restart without storing external balances | Explicit schema version, stable fields, defensive decoding, migration and unknown-newer handling | Persistence fixtures, migration and restart tests |
| Provider receipt and recovery contract use | Pixelmon adapter and separate `vault` bridge validation | Ambiguous effects enter `RECOVERY_REQUIRED`; only proven-safe lookup, retry, or compensation can proceed | Records remain bound to originating provider and request identity | Crash matrix and provider fixture receipts |
| Custody, claims, and offline proceeds workflow | Shops, player shops, operators, Phase 002 provider tests | Items and proceeds converge to delivery or durable claim without duplicate value or discarded custody | Claim and custody schema preserves existing data and explicit versioning | Multi-leg, offline, restart, and restore evidence |
| Internal and external ownership separation | Internal provider, adapters, analytics, migration | `BalanceManager` is internal-only; external balances remain provider-owned; analytics records confirmed facts only | No automatic migration or shadow synchronization | Saved-data inspection and provider-switch tests |
| Physical money compatibility | Existing worlds, inventories, internal provider | Registration and decoding remain; bills stay inert under external or unsafe selection and reactivate only under ready internal after restart | Existing identifiers and serialized data remain stable unless a tested migration is required | Bill fixtures, registry inspection, server/client tests |
| Capability and presentation snapshot | Screens, commands, tooltips, public consumers | Server-supplied metadata, state, allowed actions, and typed reasons are presentation hints, never authority | Snapshot changes remain protocol-compatible with the frozen Phase 000 contract | Localization, stale-state, reconnect, and dedicated-server tests |
| Diagnostics and operator recovery contract | Operators and `CORE-PHASE-003` documentation | Safe provider, lifecycle, category, request correlation, and next-action evidence exists without secrets or balance leakage | Stable categories; log emission is transition or event bounded | Sanitized logs, privacy and volume review |
| Phase completion packet | `CORE-PHASE-002` and final validation | Integrated source, tests, runtime proof, docs, known limitations, and invalidation rules are reproducible | Any upstream code, schema, API, provider fixture, build, or environment change invalidates named evidence | Packet audit and default-branch verification |

## Work Packages

| Task ID | Requirement IDs | Work | Inputs and dependencies | Outputs | Affected components or interfaces | Verification |
|---|---|---|---|---|---|---|
| `P001-TASK-001` | `CORE-REQ-005`, `CORE-REQ-008`, `CORE-REQ-009` | Trace every balance read, balance write, price calculation, money item action, market value leg, event, packet, command, API, analytics, lifecycle, and rollback route; classify server authority, custody order, persistence, and direct `BalanceManager` or provider access | Integrated Phase 000; current code and tests; master surface table | Complete, reviewable call graph and surface matrix with no unclassified call site | `EconomyProvider`, `InternalEconomyProvider`, `BalanceManager`, public services, commands, packets, shops, carts, player shops, markets, money items, analytics, events, lifecycle | Architecture scan, caller review, and baseline focused tests for every discovered surface |
| `P001-TASK-002` | `CORE-REQ-004` through `CORE-REQ-007`, `CORE-REQ-015` | Convert the frozen Phase 000 API into phase invariants for lifecycle transition ownership, thread context, metadata validation, exact values, request identity, typed outcomes, exception containment, and receipt lookup | `CORE-REQ-002`, `CORE-REQ-003`, `EXT-003` | Approved implementation boundary and fixtures that exercise every contract result | Public provider API, frozen registry, selection snapshot, internal and fixture providers | Contract fixtures compile and demonstrate ready, unavailable, rejected, duplicate-completed, ambiguous, and lookup outcomes |
| `P001-TASK-003` | `CORE-REQ-004`, `CORE-REQ-014` | Implement startup resolution, validation, recovery gate, ready activation, runtime failure containment, recovery-required transition, clean stop, and rejection of late activation using one immutable selected-provider handle | `P001-TASK-002`; restart-only selection | Deterministic lifecycle controller and typed state snapshot | Economy orchestration, server lifecycle, provider registry consumer, status surfaces | Transition-table unit tests, fixture failures, reload, restart, shutdown, server smoke, and no-fallback assertions |
| `P001-TASK-004` | `CORE-REQ-006`, `CORE-REQ-015` | Validate currency identifiers, singular/plural display names, precision, values, domain constraints, aggregation, fees, refunds, and external conversions; use checked arithmetic throughout | Frozen metadata contract; surface inventory | Exact minor-unit value and formatting boundary with typed validation failures | Provider metadata, pricing, carts, fees, refunds, commands, analytics labels, screens, snapshots | Boundary and property tests across minima, maxima, zero, negatives, overflow, precision, locale, non-finite and lossy input; forbidden floating-point source scan |
| `P001-TASK-005` | `CORE-REQ-005`, `CORE-REQ-007`, `CORE-REQ-015` | Define server-owned root UUID creation, deterministic or persisted child identity per leg role, replay ownership, immutable request fields, receipt validation, outcome lookup, and compensation identity | Frozen provider mutation contract; exact money model | Stable request lineage model and result taxonomy | Public mutation service, transaction coordinator, packets, commands, provider receipts | Identity determinism, cross-player replay, duplicate, collision resistance, malformed receipt, reordered delivery, and completed-retry tests |
| `P001-TASK-006` | `CORE-REQ-007`, `CORE-REQ-008`, `CORE-REQ-010`, `CORE-REQ-016` | Introduce or evolve explicit-version persistence for intent, legs, confirmed outcomes, provider binding, receipts, custody, claims, analytics facts, recovery, and compensation; define defensive decoding and migrations | Persistence inventory; request lineage model; existing world fixtures | Crash-safe schema and migration path without an external balance field | Saved data, internal balance data boundary, transaction records, claims, custody, analytics | Round-trip, old-version, new-version, unknown-newer, malformed, truncated, interrupted-write, migration, backup comparison, and no-shadow-schema tests |
| `P001-TASK-007` | `CORE-REQ-004`, `CORE-REQ-005`, `CORE-REQ-007` | Implement coordinator preflight, intent-before-effect ordering, provider invocation, outcome persistence, ambiguous-result lookup, safe completion, and one-leg compensation rules on the logical server | Lifecycle controller; exact values; request schema | One authoritative mutation pipeline with at-most-once logical effects | Economy orchestration, provider API, persistence, server execution context | Crash injection at every ordering boundary, concurrent duplicate tests, provider exception tests, restart recovery, and no-partial-local-state assertions |
| `P001-TASK-008` | `CORE-REQ-005`, `CORE-REQ-008`, `CORE-REQ-009` | Route public balance and mutation APIs, administrative balance and mutations, pay, fees, refunds, confirmed events, analytics, rollback, reload, startup, and shutdown; remove bypasses | Surface matrix; coordinator | Routed simple and lifecycle surfaces with typed results and confirmed-only events | Public services, administration, pay or transfer, analytics, events, rollback, lifecycle | One focused positive and negative test per surface under internal ready, external ready, unavailable, and recovery-required states where meaningful |
| `P001-TASK-009` | `CORE-REQ-005`, `CORE-REQ-007`, `CORE-REQ-009` | Route server-shop purchase and sale plus cart checkout; validate the whole money plan before item movement; persist intent; order provider and custody legs; preserve the cart on preflight failure | Coordinator; inventory and product-state validation; surface matrix | Safe shop and cart workflows | Server shops, carts, checkout, inventory custody, fees, events | Focused tests, GameTests, insufficient funds, unavailable provider, stale cart, overflow aggregate, duplicate checkout, crash points, restart, and multiplayer proof |
| `P001-TASK-010` | `CORE-REQ-007`, `CORE-REQ-009`, `CORE-REQ-016` | Route player-shop purchase as durable buyer debit, item custody/delivery, owner credit or offline proceeds, claim creation, refund, and compensation legs; keep failed owner delivery pending | Coordinator; claim and custody persistence; player-shop ownership | Multi-leg player-shop and offline-proceeds state machine | Player shops, owner proceeds, offline delivery, custody, claims, refunds, compensation | Crash before and after every leg, owner offline and reconnect, full inventory, provider unavailable mid-flow, duplicate request, compensation retry, restore, GameTest, and multiplayer evidence |
| `P001-TASK-011` | `CORE-REQ-008`, `CORE-REQ-010` | Enforce independent provider ownership through internal-to-external, external-to-internal, and external-to-external restarts; preserve integer price magnitude; restrict starting balance; retain originating-provider recovery binding | Restart-only selection; existing internal balances; transaction provider IDs | No-migration provider-switch behavior and operator-visible price/recovery status | Internal provider, `BalanceManager`, selection lifecycle, config prices, unresolved records | Upgraded-world fixtures, new/existing players, restart switches, precision changes, dormant balance assertions, unresolved request tests, and saved-data comparison |
| `P001-TASK-012` | `CORE-REQ-009`, `CORE-REQ-011` | Preserve money item registration, decoding, inventory and world data; gate activation, gameplay minting, deposit, withdrawal, and redemption to ready internal mode; scan for and reject any ATM surface | Surface matrix; bill fixtures; lifecycle controller | Save-safe inert external-mode bills and no ATM | Money item registration and behavior, deposit/withdraw surfaces, commands, screens, save decoding | Every supported bill fixture under each lifecycle state, registry inspection, external no-provider-call proof, switch-back redemption, client/server smoke, command/screen scan |
| `P001-TASK-013` | `CORE-REQ-004`, `CORE-REQ-005`, `CORE-REQ-006`, `CORE-REQ-012` | Supply server-owned provider metadata, lifecycle, allowed-action and typed-reason snapshots; render exact currency; disable unsafe controls; preserve browsing and barter; revalidate every submitted action | Lifecycle and exact money models; routed surfaces | Accurate localized and accessible player/operator presentation | Network snapshots, packet handlers, commands, screens, tooltips, market views, localization | Localization key checks, UI screenshots or recordings, keyboard and scale checks, internal/external/unavailable states, stale snapshot, replay, reconnect, and dedicated-server isolation |
| `P001-TASK-014` | `CORE-REQ-013`, `CORE-REQ-014`, `CORE-REQ-015` | Add structured selection, lifecycle, rejection, ambiguity, recovery, and compensation diagnostics with safe request correlation, exception context, privacy filtering, and transition/event-based rate bounds | Result taxonomy; lifecycle; coordinator | Sanitized operator status and log evidence | Server logs, status command or existing operator surface, transaction and provider boundaries | Golden or structured log assertions, exception injection, repeated-view volume test, sensitive-data review, and troubleshooting rehearsal |
| `P001-TASK-015` | `CORE-REQ-005`, `CORE-REQ-007`, `CORE-REQ-015` | Harden packet, command, public API, metadata, receipt, config, item, persistence, and provider boundaries against malformed values, identity spoofing, permission bypass, replay, concurrent mutation, stale state, exceptions, and absent optional classes | All routed surfaces and persistence | Deterministic rejection and containment without unsafe side effects | Trust boundaries across client, server, provider, persistence, config, and optional integration linkage | Fuzz or parameterized malformed-input tests, permission matrix, cross-player replay, concurrency barriers, stale menus, corrupt fixture, exception fixtures, classpath and secret scans |
| `P001-TASK-016` | `CORE-REQ-014` | Remove per-tick or per-request discovery, filesystem access, full registry or balance scans, balance synchronization, unbounded retries, and repetitive logging; measure representative work by transaction-leg count | Complete routed call graph | Bounded integration cost and retry behavior | Provider handle, shops, carts, player shops, pay, analytics, views, recovery scheduler | Call-path review, representative timing and allocation observations where tooling permits, call-count assertions, repeated unavailable views, log-volume and retry-storm tests |
| `P001-TASK-017` | `CORE-REQ-007`, `CORE-REQ-010`, `CORE-REQ-016` | Implement operator-safe recovery by request identity and originating provider; rehearse backup, exact-provider restoration, idempotent lookup or retry, proven compensation, pending claims, complete restore, and restart selection rollback | Durable schema; crash matrix; representative backups | Tested recovery and rollback procedure that never deletes or guesses balance data | Recovery service, lifecycle, claims, custody, provider selection, backup and restore operations | Full crash/restart matrix, provider removal/restoration, controlled corrupt metadata, backup hashes, clean-copy runbook rehearsal, and no-duplicate/no-loss assertions |
| `P001-TASK-018` | `CORE-REQ-004` through `CORE-REQ-016` | Run the complete phase verification matrix in master order and rerun all evidence invalidated by fixes | All implementation tasks | Reproducible passing phase evidence | Unit, property, persistence, migration, GameTest, build, server, client, multiplayer, reconnect, restart, security, jar and diff surfaces | Exact commands and procedures recorded with commit, environment, date, expected and actual result, sanitized log location, and artifact identity |
| `P001-TASK-019` | `CORE-REQ-010` through `CORE-REQ-016` | Update implementation-matched user, API behavior, maintainer, configuration, migration, money item, diagnostics, backup, recovery, restore, rollback, security, verification, and known-limit documentation | Verified implementation and evidence | Accurate tracked documentation and rehearsed operator guidance | Root documentation, maintainer documentation, documentation index, relevant focused guides | Link and example checks, behavior cross-check, configuration and message verification, disposable-world procedure rehearsal |
| `P001-TASK-020` | `CORE-REQ-004` through `CORE-REQ-016` | Audit completeness, invalidated evidence, secrets and generated output, no-bypass and no-shadow constraints, required integration state, and Phase 002 handoff | All tasks and phase evidence | Phase completion packet and exact downstream contract | Complete diff, test evidence, documentation, branch and pull-request integration evidence | Independent trace audit, complete diff and jar inspection, default-branch verification, and next-phase entry checklist |

`P001-TASK-001` and test-harness inventory may proceed together, but no surface implementation begins before its caller and persistence effects are classified. Tasks 003 through 007 are dependency ordered. Tasks 008 through 12 may be implemented in bounded parallel groups only after the common coordinator and schema are stable, and each group must use disjoint components and shared contract tests. Presentation, diagnostics, security hardening, and performance review follow the routed behavior they observe. Recovery proof and the complete verification pass follow all mutation surfaces. A failed migration, ambiguous receipt, untraced caller, or inability to prove at-most-once effect stops dependent tasks; rollback is the preservation of pre-change source and fixture copies plus schema-compatible migration correction, never deletion or direct external balance edits.

## Architecture and Implementation Boundaries

### Component ownership and dependency direction

All client screens, commands, packets, shops, carts, player shops, markets, public services, analytics, money items, and lifecycle hooks depend on the economy orchestration boundary. The orchestrator depends on the frozen public provider API and the durable transaction coordinator. Only `InternalEconomyProvider` may depend on `BalanceManager`. Fixture external providers and Phase 002 adapters depend on the public API and must not reach orchestration internals.

Client code receives immutable presentation snapshots and typed results. It never selects a provider, asserts readiness, supplies an authoritative balance, chooses a transaction outcome, or authorizes a request identity. Common and dedicated-server paths must not load client classes.

### Lifecycle state machine

| Current state | Input or condition | Next state | Reads | Mutations | Mandatory side behavior |
|---|---|---|---|---|---|
| `UNRESOLVED` | Registry frozen, provider valid, readiness and recovery pass | `READY` | Unavailable until transition completes | Rejected | Record selected identifier and successful resolution once |
| `UNRESOLVED` | Selected identifier not registered by freeze | `MISSING` | Typed unavailable | Rejected | Keep server, browsing, and barter available; require corrected install and restart |
| `UNRESOLVED` | API, runtime version, metadata, precision, or capability invalid | `INCOMPATIBLE` | Typed unavailable | Rejected | Report stable incompatibility category and restart action |
| `UNRESOLVED` | Readiness or provider call throws or returns invalid data | `FAILED` | Typed unavailable | Rejected | Contain exception, emit one actionable transition diagnostic, never fall back |
| `UNRESOLVED` | Durable request outcome is ambiguous or incomplete | `RECOVERY_REQUIRED` | Safe provider queries only | Only idempotent recovery or proven compensation | Preserve request and originating provider; expose operator action |
| `READY` | Valid query and confirmed mutation outcomes | `READY` | Provider-authoritative | Coordinator-only | Persist confirmed facts; never shadow balance |
| `READY` | Provider throws, returns malformed data, or becomes unsafe without ambiguous effect | `FAILED` | Typed unavailable | Rejected | Stop monetary work; keep browsing, barter, custody, and claims accessible |
| `READY` | Effect may have occurred but outcome cannot be proven | `RECOVERY_REQUIRED` | Safe provider queries only | Recovery and compensation only | Persist ambiguity before admitting further monetary writes |
| `MISSING`, `INCOMPATIBLE`, or `FAILED` | Provider appears or recovers during same lifecycle | Same state | Typed unavailable | Rejected | No hot activation; clean restart required |
| `RECOVERY_REQUIRED` | Every unresolved request reconciles deterministically during authorized recovery | `READY` only through defined recovery/startup gate | Provider-authoritative after gate | Coordinator-only after gate | Record each lookup and result; no manual guessed balance correction |
| Any nonterminal state | Server stop begins | `STOPPED` | Unavailable | Rejected | Flush already-confirmed durable state; do not initiate new provider effects |

Transitions must be serialized on the logical server or through an equivalent deterministic concurrency boundary. Observers receive immutable snapshots. One failing operation may enter `FAILED` when no external effect ambiguity exists or `RECOVERY_REQUIRED` when it does; this classification must never depend on client state, timing, or log text.

### Exact money and metadata

Authoritative balances, amounts, prices, fees, refunds, denominations, and aggregates are signed integer minor units. Domain rules determine whether zero or negative values are permitted; gameplay transfers, prices, and currency-item denominations reject invalid signs before any custody or provider effect. Addition, subtraction, multiplication, cart aggregation, fee calculation, and conversions use checked operations.

Provider metadata is validated and frozen with selection. Currency names, pluralization inputs, and precision flow to server output, public snapshots, analytics labels, and client formatting. Parsing and conversion are locale independent. Fractional minor units, excessive precision, non-finite values, lossy round trips, overflow, underflow, invalid names, and unsupported precision make the provider or operation unavailable or invalid according to the frozen contract. Unavailable balances are typed as unavailable, never `0` and never formatted with internal metadata.

### Request identity, provider effects, and crash ordering

The logical server owns one immutable root request UUID for each logical user or system action. Each debit, credit, fee, refund, item-custody, claim, offline-delivery, and compensation leg receives a deterministic child identity derived from immutable root and role data or an equivalently stable UUID persisted before use. A retry uses the same identity and immutable participants, amount, provider, and role. A new action cannot reuse a completed identity. Client-provided identifiers may correlate a request only after ownership and replay validation; they never authorize it.

The required ordering is:

1. Revalidate lifecycle, actor, permission, ownership, product, inventory, price, exact amount, and all planned legs on the logical server.
2. Persist the root intent, immutable leg plan, originating provider identifier, and initial custody state before the first external effect.
3. Invoke one provider leg with its stable child identity.
4. Validate the returned receipt or typed result. Persist every confirmed, rejected, duplicate-completed, or ambiguous outcome before advancing.
5. If acknowledgement is absent after a possible effect, query by the same child identity. Never repeat until the provider contract proves repetition idempotent. Enter `RECOVERY_REQUIRED` if the effect remains unknowable.
6. Move FutureShops item custody or market state only at the documented safe boundary after prerequisite value outcomes are confirmed, then persist that transition.
7. Execute subsequent legs, owner delivery, claim creation, refund, or compensation with their own stable identities and the same outcome discipline.
8. Mark the root complete only after every required value and custody state is durably confirmed. Publish success events and analytics facts only from confirmed completion.

Every crash point before, between, and after these steps must restart into either the same completed result, the next safe idempotent action, a durable pending claim, or `RECOVERY_REQUIRED`. Compensation is a first-class leg with a stable identity, a proven prerequisite, and at-most-once outcome. It cannot be an arithmetic rewrite of a mirrored balance.

### Persistence and migration

Every introduced or changed record has an explicit schema version, stable field names, bounded collection and text sizes, validated enum and identifier decoding, and a migration from each supported earlier representation. Request records may persist provider identifier, root and child IDs, operation and role, exact amount, participants, timestamps or monotonic sequence, status, provider receipt, error category, custody and claim link, recovery state, and compensation relationship. They must not persist a periodically refreshed or independently mutable external balance.

Unknown newer data is retained or causes a safe read-only blocker; it is never silently dropped. Malformed or partially written records cannot be interpreted as completed. Selection changes do not rewrite the originating provider on unresolved records. Migration runs on disposable copies first, preserves backup hashes, and proves that internal balances, bills, inventories, shops, custody, and claims survive.

### Multi-leg, custody, claims, and offline proceeds

Server-shop and cart flows reject an unavailable money leg before moving items or mutating listings and preserve the cart when preflight fails. Player-shop purchase has distinct buyer debit, item custody or delivery, owner credit, offline delivery or claim, refund, and compensation legs. The exact ordering may vary by existing architecture only if the invariant remains that neither buyer, seller, nor item can be silently lost and every external effect is recoverable by stable identity.

An offline owner credit is confirmed through the originating provider or remains a durable pending claim. A full inventory, disconnect, dimension change, server stop, provider failure, or delayed readiness cannot discard custody or proceeds. Claims remain visible and durable in every lifecycle state. A claim whose external credit cannot be proven stays pending. Recovery can complete or compensate only after durable provider lookup proves the original outcome.

### Provider switching and physical bills

`internal` remains the default for existing and new configurations. Restart selection changes authority only; they do not copy, convert, seed, reconcile, or erase balances. Internal balances remain dormant and unchanged under external selection and become authoritative again only after a restart selects ready `internal`. Starting balance applies only when the internal provider creates an eligible new balance. Configured integer price magnitude remains unchanged and becomes minor units under the selected provider, so operators must review price meaning and precision.

Existing physical bills, registrations, identifiers, decoding, inventories, and saved data remain intact under every provider state. Activation, gameplay minting, deposit, withdrawal, redemption, and any future ATM mutation are enabled only under existing validated ready-internal rules. Under external, unavailable, failed, or recovery-required states, bills are inert but retained. No ATM interface, command, packet, menu, or registration is added.

### Networking, permissions, stale state, and concurrency

Every packet and public mutation call is revalidated on the logical server for direction, authenticated player, target ownership, permission, menu or route context, current product state, current provider state, amount bounds, request ownership, and custody. A stale capability snapshot may allow a submission attempt, but the server returns a typed current-state rejection with no provider call or local side effect.

Concurrent duplicate packets, commands, API calls, reconnect retries, and server tasks serialize by stable request identity and affected transaction resources. Completed duplicates return the same confirmed result. Conflicting new operations observe current authoritative state and cannot reuse an existing request. Locks or serialization must not span blocking filesystem or network work, deadlock shutdown, or permit reentrant duplicate effects.

### Performance, security, and privacy

Provider resolution and metadata validation occur at startup, not in ticks or individual screen refreshes. Ordinary work is proportional to the number of transaction legs. Recovery uses bounded records and explicit operator or lifecycle triggers rather than uncontrolled polling. There is no periodic external-balance synchronization, full-player balance scan, filesystem discovery, network access by FutureShops, or repeated unavailable-state log emission in a hot path.

All provider responses, receipts, identifiers, metadata, packets, commands, item data, config values, and saved records are untrusted. Validate length, format, bounds, ownership, permission, state transition, API compatibility, and provider binding before use. Provider exceptions are contained without crashing the server or turning failure into zero or success. Logs and evidence include only safe provider ID, state, category, and request correlation; they exclude credentials, private raw player data, unnecessary balance details, proprietary logs, and absolute private paths.

## Complete Monetary Surface Inventory

The implementation trace in `P001-TASK-001` must enumerate every concrete caller represented by these rows and add any discovered surface before editing it. A row is incomplete until it identifies server preflight, provider call or deliberate no-call, durable transitions, custody transitions, typed response, presentation, and regression proof.

| Surface | Ready `internal` | Ready external fixture | External unavailable, failed, or recovery required | Mandatory invariant |
|---|---|---|---|---|
| Public balance query API | Authoritative internal provider value | Authoritative selected provider value | Typed unavailable, never zero | No direct `BalanceManager` access by public caller |
| Public mutation API | Coordinator and internal provider | Coordinator and selected provider | Reject before mutation | Stable root and leg identity for every mutation |
| Administrative balance query | Authoritative internal value | Authoritative external value | Provider state and unavailable reason | Permission and identity enforced server-side |
| Administrative grant, set, remove, or equivalent | Idempotent internal leg | Only exact, supported, idempotent external leg | Reject, never change dormant internal data | Unsupported provider capability is typed, not emulated through a mirror |
| Analytics and audit views | Confirmed internal request facts | Confirmed external request facts plus provider ID | Historical facts remain; live state labeled unavailable | No current external balance field or inferred success |
| Server-shop purchase and sale | Coordinated internal value leg | Durable external value leg | Reject money trade before item movement | Pure barter path remains provider-independent |
| Cart and checkout | Coordinated atomic workflow | Durable multi-leg workflow | Preserve cart and reject before custody | Checked aggregate and one root request per checkout |
| Player-shop purchase and proceeds | Buyer and owner internal legs | Durable debit, item, credit or claim, refund and compensation legs | Browsing continues; reject money purchase before partial effect | Offline owner value never disappears |
| Offline proceeds and claims | Confirmed internal credit or durable claim | Confirmed external receipt or pending claim | Claim remains accessible and pending | Claim delivery retries use same stable identity |
| Player pay or transfer | Coordinated internal debit and credit | Durable external debit and credit | Reject without partial debit | Concurrency and duplicate retries cannot double either leg |
| Deposit and withdrawal | Existing validated internal behavior | Disabled | Disabled | No external provider call or bill consumption |
| Physical money activation and redemption | Existing validated internal behavior | Disabled and inert | Disabled and inert | Registration, decoding, and item data remain intact |
| Money registration and save decoding | Registered and decoded | Registered and decoded | Registered and decoded | No item deletion, remap, or silent decode fallback |
| Fees | Confirmed internal fee leg | Confirmed external fee leg | Reject parent flow before fee | Checked arithmetic and distinct stable leg identity |
| Refunds and compensation | Confirmed idempotent internal legs | Confirmed idempotent external legs | Pending until original outcome is proven | Compensation never runs twice or guesses a balance |
| Events and completion notifications | Emit only from confirmed state | Emit only from confirmed state | Emit typed failure or no success event | Replayed completion does not duplicate side effects |
| Rollback and recovery | Request-aware lookup and correction | Receipt or lookup-based reconciliation | Safe blocker until deterministic | No direct balance rewrite from a local snapshot |
| Lifecycle startup and reload | Resolve once; reload stages restart | Resolve once; reload stages restart | Stay online and fail closed | No fallback, late activation, or hot switch |
| Shutdown | Stop new work and persist confirmed state | Stop new work and persist confirmed state | Preserve blocker and pending claims | No new provider effect after stop begins |
| Browsing, search, and history | Available | Available | Available with explicit state | Read-only surface cannot trigger provider retry storm |
| Pure barter | Available without provider money call | Available without provider money call | Available without provider money call | No hidden fee, balance, bill, or currency leg |
| Screens, commands, tooltips, snapshots | Internal metadata and ready actions | Selected metadata and supported actions | Typed unavailable or recovery reason and disabled money controls | Snapshot is presentation only and localized |
| ATM interface, command, packet, or mutation | Absent | Absent | Absent | Source and registration scans prove absence |

## Failure, Recovery, and Edge Cases

| Scenario | Detection | Required behavior | Recovery or rollback | Regression proof |
|---|---|---|---|---|
| Selected identifier missing at registry freeze | Frozen lookup has no registration | Enter `MISSING`; keep server, browsing, barter, custody, and claims available; reject reads and writes appropriately | Install corrected provider and restart | Fixture registration and dedicated-server restart test |
| Duplicate, malformed, late, or incompatible registration | Phase 000 registry result | Selected provider becomes unavailable with deterministic category; no load-order winner | Correct installation or API version and restart | Registration fixture and no-hot-activation test |
| Invalid metadata or precision | Startup validation | Enter `INCOMPATIBLE`; never use internal display metadata | Correct provider and restart | Metadata boundary and UI test |
| Provider readiness false or known pre-effect exception | Readiness or pre-effect call result | Enter `FAILED`; reject further monetary work without partial local change | Correct provider and restart unless frozen contract defines safe startup recovery | Fault injection and no-side-effect test |
| Exception or timeout after possible provider effect | Missing valid receipt after invocation | Persist ambiguity and enter `RECOVERY_REQUIRED`; do not guess or retry unsafely | Durable outcome lookup with same child identity, otherwise operator blocker | Crash gap and timeout fixture across restart |
| Malformed or mismatched receipt | Receipt validation against immutable leg | Treat as unsafe ambiguity or invalid provider response; never accept success | Query exact request outcome or remain blocked | Forged provider ID, amount, actor, role, and request receipt tests |
| Duplicate request after completion | Existing durable root or leg outcome | Return same result without second provider or custody effect | None required | Concurrent packet, command, API, reconnect, and restart duplicate tests |
| Same request ID with changed immutable fields | Persisted identity mismatch | Reject as replay or conflict and make no provider call | Start a new logical action with a new server-owned identity | Cross-player and altered-amount replay tests |
| Concurrent distinct spends exceed funds | Provider and coordinator serialization/result | At most valid confirmed operations succeed; no local success inferred | Normal typed insufficient-funds or provider rejection | Barrier-controlled concurrency test |
| Checked arithmetic overflow or invalid sign | Exact-value preflight | Reject before intent, provider, or custody effect | Correct input or configuration | Property tests for cart, fee, refund, and boundary values |
| Lossy or locale-dependent conversion | Exact round-trip validation | Reject provider value or input; do not round | Correct provider metadata or input | Locale matrix and fractional-minor-unit tests |
| Stale screen says ready after server failure | Server revalidation differs from snapshot | Typed current-state rejection; refresh presentation; no provider/local effect | Receive updated snapshot; restart only if provider correction is required | Multiplayer stale-state and replay trace |
| Unauthorized command or spoofed player target | Server permission and identity checks | Reject before request allocation or provider call when possible | Correct permission or target | Permission matrix and packet spoof tests |
| Server-shop or cart provider unavailable | Full-flow preflight | Preserve items and cart; no listing, custody, fee, or analytics-success mutation | Retry after safe restart | Focused test and GameTest |
| Player-shop owner is offline | Owner delivery stage | Persist stable owner-credit leg; confirm provider outcome or durable claim | Deliver or reconcile using same identity on reconnect/recovery | Offline, reconnect, restart, and duplicate test |
| Inventory full or item delivery fails after debit | Custody transition failure | Preserve item custody and execute only proven-safe delivery, claim, refund, or compensation path | Claim or idempotent compensation based on confirmed outcomes | Full-inventory multi-leg crash matrix |
| Crash before intent persistence | Missing transaction record and no provider call | Treat retry as a new first execution of same server-correlated action only if identity mapping proves it | Retry through normal coordinator | Pre-intent crash test |
| Crash after intent but before provider call | Durable pending leg with no receipt | Retry same leg identity through provider idempotency contract | Normal startup recovery | Restart fixture |
| Crash after provider effect before local outcome write | Pending leg and provider lookup | Query outcome; persist confirmed result or enter `RECOVERY_REQUIRED` | Never blind retry | Effect-gap crash fixture |
| Crash before or after custody, claim, or compensation write | Durable preceding state and stable child identities | Resume next safe transition; no duplicate item or value | Idempotent state transition on restart | Per-transition crash matrix |
| Provider selection changes with unresolved request | Active config differs from record provider | Do not replay against newly selected provider; retain originating binding and block unsafe mutations | Restore originating provider or use safe backup procedure | Provider-switch recovery test |
| External selected with existing internal balances | Selection and saved internal data | Leave balances dormant and unchanged; do not expose or seed external values | Restart to `internal` to re-expose | Upgraded-world and round-trip selection test |
| External selected with bills | Provider state and money-item action | Retain and decode bills; deny activation, deposit, withdrawal, and redemption | Restart to ready `internal` for valid existing behavior | Inventory fixture and registry test |
| Unknown newer persistent schema | Version decoder | Preserve data and fail safely; do not downgrade or discard | Use compatible version or restore matching backup | Unknown-version fixture and backup comparison |
| Malformed or truncated persistent record | Defensive decoder and integrity validation | Do not interpret as success; preserve evidence and enter safe blocker when monetary certainty is affected | Restore complete matching backup or repair only through documented migration | Corrupt-fixture and recovery-runbook test |
| Repeated unavailable screen refresh or retry | Call and log counters | Return cached lifecycle snapshot or bounded result without provider discovery, disk scan, or log storm | Correct provider and restart | Load observation and log-volume assertion |
| Provider exception contains sensitive content | Exception boundary and log sanitizer | Record category and safe correlation without raw private payload | Secure operator inspection outside committed evidence if needed | Privacy review and injected-secret sentinel test |
| Shutdown races an active request | Stop transition and coordinator state | Reject new work; finish only a safely bounded transition or persist exact pending state | Startup recovery by stable identity | Coordinated shutdown crash test |

## Verification Matrix

| Requirement or task | Static or unit | Integration | Real workflow or runtime | Negative and recovery | Evidence artifact |
|---|---|---|---|---|---|
| `CORE-REQ-004`, `P001-TASK-003` | Complete state-transition table and invalid-transition tests | Registry, selection, provider fixture, recovery gate | Standard dedicated server startup, reload, failure, restart, shutdown | Missing, late, incompatible, invalid metadata, thrown query or mutation, malformed and ambiguous outcome | State test report and sanitized server logs |
| `CORE-REQ-005`, `P001-TASK-007`, `P001-TASK-008` | Boundary and direct-access scan; identity and permission tests | Packets, commands, APIs, lifecycle and coordinator | Multiplayer commands, menus, reconnect and direct service use | Spoofed player, stale menu, replay, permission denial, bypass attempt | Call graph, trace matrix, packet and command logs |
| `CORE-REQ-006`, `P001-TASK-004` | Boundary and property tests for arithmetic and conversions | Shop, cart, fee, refund, provider metadata formatting | Client and command display under multiple valid precisions | Overflow, underflow, fractional, excessive precision, negative domain, locale, invalid metadata | Property-test results and UI captures |
| `CORE-REQ-007`, `P001-TASK-005` through `P001-TASK-007` | Identity, transition, receipt and lookup unit tests | Durable coordinator with internal and fixture external providers | Retry and restart every one-leg and multi-leg workflow | Concurrent duplicates, reordered delivery, exception, timeout, effect-gap crash, compensation replay | Crash matrix, journal fixtures, receipt and recovery logs |
| `CORE-REQ-008`, `P001-TASK-006`, `P001-TASK-011` | Schema review and forbidden external-balance-field scan | Analytics, transaction facts, internal provider and provider switching | Upgraded world across internal/external/internal restarts | Attempted shadow synchronization, unresolved-provider mismatch | Saved-data inspection, before-and-after backup hashes |
| `CORE-REQ-009`, `P001-TASK-001`, `P001-TASK-008` through `P001-TASK-012` | One regression test and no-bypass scan per matrix row | All surfaces under internal, fixture external, unavailable and recovery states | GameTests, server, client and multiplayer surface walkthroughs | Prove forbidden side effects absent for every rejection | Completed surface matrix and call-path evidence |
| `CORE-REQ-010`, `P001-TASK-011` | Starting-balance, price and provider-binding tests | Representative `2.2.1` migration fixtures and provider switches | New and existing players, precision change, restart round trip | Selection change during unresolved recovery; no transfer or overwrite | Migration report, switch logs, balance assertions |
| `CORE-REQ-011`, `P001-TASK-012` | Registry, decoder, action-gate and ATM source scans | Bill inventories and internal/external lifecycle | Client and dedicated-server load, switch back and redeem | External activation, deposit, withdrawal, redemption and corrupt bill attempts | Fixture hashes, registry report, smoke logs |
| `CORE-REQ-012`, `P001-TASK-013` | Localization completeness, formatting and snapshot tests | Network snapshot and server rejection integration | Client screen, tooltip and command walkthrough at supported scales; multiplayer reconnect | Unavailable versus zero, stale readiness, typed rejection distinctions, dedicated-server client-class isolation | Screenshots or recordings, localization report, client/server logs |
| `CORE-REQ-013`, `P001-TASK-014` | Structured category and redaction tests | Lifecycle, coordinator, recovery and compensation diagnostics | Operator status and troubleshooting rehearsal | Repeated failures, injected sensitive sentinel, exception context | Sanitized log corpus, privacy and volume review |
| `CORE-REQ-014`, `P001-TASK-016` | Hot-path, discovery, disk, synchronization and retry source scan | Provider call-count assertions per transaction leg | Representative shop, cart, player-shop, pay, analytics and unavailable-view observations | Retry storm, repeated refresh, slow or throwing fixture provider | Performance notes, call counts, allocation or timing data where available |
| `CORE-REQ-015`, `P001-TASK-015` | Parameterized malformed-input, permission and optional-linkage tests | Every trust boundary and persistence decoder | Standard NeoForge server/client without optional stacks | Replay, spoofing, invalid receipt, corruption, exception, absent classes, secret scan | Security review, dependency and jar scan, negative-test logs |
| `CORE-REQ-016`, `P001-TASK-017` | Recovery-decision and provider-binding tests | Backup, restore, lookup, compensation, claim and selection rollback | Disposable-world operator runbook from clean backup | Provider removal, corrupted test metadata, every crash point, unavailable originating provider | Backup hashes, restore comparison, recovery logs, runbook record |
| `P001-TASK-018` | Complete focused and unit suite | Persistence, GameTest and full build | Dedicated server, client, multiplayer, reconnect and restart | Full failure, security and recovery matrices | Commit-bound verification manifest and sanitized outputs |
| `P001-TASK-019`, `P001-TASK-020` | Link, example, identifier, version and command checks | Documentation against implementation and evidence | Operator follows procedures on disposable data | Stale claim, unsafe deletion advice, publication claim, secret or generated-output scan | Documentation diff, rehearsal record, final diff and jar inspection |

### Fixtures, environments, and rerun order

The fixture set must include ready internal; ready fixture external; missing, late, incompatible, invalid-metadata, readiness-false, query-throwing, mutation-throwing, malformed-outcome, ambiguous-outcome, slow, duplicate-aware, and durable-lookup external providers; new and upgraded world copies; new and existing players; every supported bill state; carts at arithmetic boundaries; server and player shops; offline owners; full inventories; pending claims; unresolved requests; unknown-newer and malformed persistence; and controlled crash points.

Expected results include both visible outcomes and forbidden-side-effect assertions: provider call count, balance leg count, item and custody state, cart/listing/order state, claim state, analytics event count, lifecycle state, durable record, log category, and UI reason. A lower-fidelity source scan never substitutes for a GameTest, runtime restart, multiplayer reconnect, migration rehearsal, or crash recovery when that behavior crosses world, inventory, network, or persistence boundaries.

Run focused tests after each task, then all unit and property tests, persistence and migration tests, applicable data validation, applicable GameTests, the complete build, dedicated-server smoke, client smoke, multiplayer and reconnect, restart and crash matrices, security and dependency checks, jar inspection, and complete diff inspection. Any implementation or fixture change reruns its focused test and every dependent matrix row. Schema, coordinator, lifecycle, request identity, or provider contract changes invalidate all persistence, crash, restart, provider-switch, multi-leg, and downstream adapter evidence.

## Documentation, Operations, and Release

This phase updates the existing root user documentation, maintainer documentation, documentation index, and relevant focused guides without moving or duplicating established files. The updates must describe only verified implementation and cover:

- The `internal` default, selected provider identity, restart-only change, no fallback, late-provider behavior, and every lifecycle state.
- Provider-owned currency names and precision, checked minor units, configured integer price interpretation, overflow and lossy-conversion rejection, and required price review before switching.
- Server authority and behavior for public APIs, administration, analytics, server shops, carts, player shops, offline proceeds, claims, pay, deposit, withdrawal, bills, fees, refunds, events, rollback, reload, startup, shutdown, browsing, and pure barter.
- No automatic balance migration, no external balance mirror, dormant internal balances, internal-only starting balance, unresolved originating-provider binding, and safe switch-back semantics.
- Bill retention and decoding, external-mode inactivity, reactivation only under ready internal after restart, and the absence of any ATM UI or command.
- Typed user-facing distinctions, disabled controls, localization, accessibility, stale-state behavior, and provider-unavailable versus zero-balance semantics.
- Stable diagnostic categories, safe request correlation, expected startup and failure messages, log locations, privacy limits, and performance expectations.
- Pre-upgrade backup, disposable-copy migration rehearsal, provider correction, outcome lookup, pending claims, compensation prerequisites, exact-stack restoration, complete restore, selection rollback, and prohibition on deleting journals, claims, custody, bills, balances, or provider data.
- Exact phase verification commands available in the repository, fixtures and environment prerequisites, expected results, failure interpretation, and known limits.

Operational evidence must record source commit, exact command or procedure, date, environment manifest, fixture identity, expected and actual result, and sanitized log location. Backups record hashes and cover the complete matching world, configuration, mod set, and provider data. Restore and rollback occur only on disposable copies during this phase. Release metadata and publication are not produced here. This phase may prepare implementation-facing documentation for Phase 003, but final release-candidate validation, final artifact hashes, verification and update of the existing issue, tags, uploads, and publication remain outside scope. The existing issue stays open and unchanged.

## Risks and Evidence Invalidation

| Risk | Prevention | Detection | Recovery | Evidence invalidated | Reverification |
|---|---|---|---|---|---|
| A direct `BalanceManager` or provider bypass survives | Mandatory complete call graph, surface ownership, and architecture scan | Direct reference, differing result semantics, or dormant internal balance changes | Route through orchestrator and add focused regression | All affected surface, switch, analytics, and no-shadow evidence | Re-run call graph, complete surface row, provider switch, saved-data scan |
| Provider effect occurs before durable intent | Enforce intent-before-effect invariant in coordinator | Crash fixture finds provider receipt without local intent | Correct ordering; preserve fixture and reconcile only by provider lookup | All transaction, crash, compensation, and recovery evidence | Full crash matrix for every flow |
| Ambiguous effect is retried blindly | Require durable receipt or lookup and explicit `RECOVERY_REQUIRED` | Provider call count exceeds one without proven idempotent result | Block writes and recover by same request identity | Coordinator, multi-leg, retry, restart, and adapter handoff evidence | Effect-gap, duplicate, restart, and operator recovery tests |
| External balances become mirrored | Schema allowlist, ownership boundary, no synchronization jobs | Saved-data field, periodic query, analytics current-balance copy, or `BalanceManager` write under external | Remove mirror, migrate test data safely, preserve request facts | Persistence, analytics, performance, provider-switch evidence | Schema review, saved-data inspection, call scan and switches |
| Numeric conversion rounds or overflows | Checked minor-unit model and exact round-trip validation | Property-test counterexample or mismatched display/provider amount | Reject unsupported value or metadata before mutation | Every affected surface and persistence fixture | Full value property suite and surface aggregate tests |
| Crash loses custody or offline proceeds | Durable state machine, stable legs, pending claims | Item, claim, owner credit, or buyer debit fails conservation assertion | Resume safe leg or proven compensation; never manual balance rewrite | Player-shop, claim, compensation, restart evidence | Complete multi-leg crash and restore matrix |
| Provider switching rebinds unresolved work | Persist immutable originating provider | Request executes against active but different provider | Block, restore origin provider, or matching-backup restore | Switching, recovery and downstream provider evidence | Unresolved switch and restart tests |
| Bill disablement corrupts saves | Retain registration and decoder; gate behavior only | Missing item, decode failure, changed identifier, or inventory diff | Restore compatible code and fixture backup | Bill, migration, client/server smoke evidence | All bill fixtures, registry and save comparisons |
| Stale client state authorizes mutation | Server revalidation and typed result | Provider called after stale or forged snapshot | Fix handler boundary; preserve server authority | Packet, command, UI, multiplayer and security evidence | Replay, reconnect, stale-menu and permission matrix |
| Provider failure creates hot-loop retries or logs | Frozen handle, bounded calls, event-based recovery and logging | Provider call or log count grows with ticks or views | Stop automatic retry; require explicit bounded recovery | Performance, diagnostics and unavailable-state runtime evidence | Call-count, repeated-view and log-volume tests |
| Persistence migration discards unknown data | Version checks, defensive decoding, immutable backups | Byte or semantic diff loses fields, claim, bill, custody, or request | Stop migration and restore matching backup | All persistence, upgrade, recovery and artifact evidence | Full fixture matrix and backup comparison |
| Optional implementation classes leak into core | Dependency direction and clean standard NeoForge checks | Class-loading error, dependency or jar scan hit | Restore isolation before Phase 002 | Server/client smoke, security, packaging and Phase 002 entry evidence | Clean classpath, jar scan, server and client startup |
| Diagnostics expose private data or secrets | Safe fields and redaction tests | Sentinel appears in logs, docs, artifacts, or evidence | Remove exposure, rotate any real secret outside repository process, regenerate sanitized proof | All log, docs, security, jar and completion evidence | Secret scan, privacy review and full artifact inspection |
| Implementation or upstream API changes after proof | Commit-bound evidence and dependency manifest | Source, schema, fixture, API, config, build, or environment digest changes | Classify blast radius and rerun before integration | Named affected evidence plus all downstream results | Follow master rerun order; API/schema/coordinator changes require complete phase rerun |

## Phase Completion Packet

Before this phase may close, the execution record outside the protected plan set must contain:

1. The integrated source commit identity and phase pull-request state, required checks, review resolution, merge result, and verification that the approved default branch contains the integration result.
2. The complete monetary surface matrix and current call graph, including every discovered direct-access scan result and proof that no unclassified or bypassing surface remains.
3. Lifecycle transition tests and sanitized startup, missing, incompatible, failed, recovery-required, restart, reload, and shutdown logs.
4. Exact-value unit and property results, provider metadata fixtures, conversion round trips, formatting captures, and forbidden authoritative floating-point scan.
5. Durable schema specification evidence, supported-version migrations, old and unknown-newer fixtures, malformed and interrupted-write tests, and saved-data review proving no external balance mirror.
6. Root and leg identity, receipt validation, outcome lookup, duplicate-completion, replay, concurrency, compensation, and confirmed-event evidence.
7. The complete crash matrix for simple and multi-leg flows, including every provider-effect gap, custody, claim, offline-proceeds, refund, compensation, shutdown, restart, and retry point.
8. Provider-switch and migration proof for new and upgraded worlds, new and existing players, internal-to-external-to-internal restarts, precision changes, internal starting balance, dormant internal balances, integer prices, and unresolved originating-provider binding.
9. Physical bill inventory and world fixtures, registry and decoding inspection, external-mode action denials, internal switch-back redemption, deposit and withdrawal disablement, and ATM absence scan.
10. UI and command captures, localization completeness, accessibility observations, stale snapshot, multiplayer, reconnect, typed rejection, browsing, barter, and dedicated-server isolation evidence.
11. Diagnostics, privacy, malformed input, permissions, security, optional-class, performance, provider-call-count, retry-storm, log-volume, dependency, secrets, and relevant jar inspection results.
12. Focused tests, complete unit and property tests, persistence and migration tests, applicable data validation, applicable GameTests, full build, dedicated-server smoke, client smoke, multiplayer, reconnect, restart, restore, and full diff results in master order.
13. Updated implementation-matched documentation, link and example checks, backup hashes, migration rehearsal, recovery and rollback runbook rehearsal, known limitations, and exact evidence invalidation rules.
14. A downstream handoff stating that `CORE-PHASE-002` may use the frozen public API, orchestration boundary, lifecycle, transaction schema and behaviors, fixture expectations, complete surface matrix, and recovery contract without reaching implementation internals.

The completion packet records evidence; it does not amend the master or this execution blueprint. Any failed required command, unresolved mandatory defect, unknown provider effect, missing surface proof, unintegrated phase result, or stale evidence keeps the phase open.

## Next Transition

After `CORE-PHASE-001` is fully verified and integrated, fetch and verify that the approved default branch contains the resulting integration commit and that every completion-packet item is current. Then begin `CORE-PHASE-002` from that integrated base by re-reading its registered phase plan and reconfirming the exact reviewed `EXT-001` through `EXT-006` artifacts and disposable environments.

`CORE-PHASE-002` receives the frozen public provider API, server-authoritative orchestration boundary, lifecycle state machine, exact minor-unit model, durable request and outcome schema, complete routed surface matrix, claims and compensation semantics, security constraints, and recovery fixtures. It may implement the bundled Pixelmon `9.4.0` adapter and validate the separately installed `vault` bridge only through those contracts. It may not redefine Phase 001 guarantees, start before integration, absorb unresolved Phase 001 defects, or weaken idempotency, exactness, provider isolation, save safety, or fail-closed behavior.
