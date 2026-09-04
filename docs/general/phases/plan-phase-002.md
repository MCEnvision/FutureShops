# Phase 002 Execution Plan

> **Plan ID:** PLAN-PHASE-002
> **Phase ID:** CORE-PHASE-002
> **Owner:** Exact external provider integrations
> **Classification:** MANDATORY
> **Master plan:** [plan.md](../plan.md)
> **Phase sequence:** 002 of 003

## Purpose and Ownership

This phase implements and proves the two external integration outcomes authorized for FutureShops `2.3.0` on Minecraft `1.21.1` and NeoForge `21.1.248`. First, the same FutureShops jar contains an optional capability adapter for exactly Pixelmon `9.4.0`. That adapter may expose verified balance query and precheck behavior, but it must refuse every mutation-required surface before journal, custody, or provider effects while the exact direct Pixelmon API lacks durable request identity, receipt lookup, and idempotent retry. Second, a separately installed and reviewed bridge may register provider identifier `vault` through the public FutureShops API, but only an exact stack whose declared mutation capabilities and crash-recovery behavior are proven may enable mutation-required surfaces.

The canonical requirements owned here are `CORE-REQ-017` and `CORE-REQ-018`. The master owns product scope, locked decisions, global phase topology, compatibility claims, and the unpublished completion endpoint. This blueprint owns the dependency-ordered implementation, exact runtime proof, optional dependency isolation, failure and recovery behavior, packaging evidence, and downstream handoff for the two integrations. It does not reinterpret a boolean Pixelmon `add` or `take` result as a durable receipt, add an unsafe override, embed a mirrored balance ledger, bundle external artifacts, or claim generic Pixelmon, Vault, Bukkit, economy plugin, or hybrid-server compatibility.

## Evidence-Based Entry State

| Evidence class | Area | Finding | Source or command | Freshness condition |
| --- | --- | --- | --- | --- |
| VERIFIED | Sequential dependency | `CORE-PHASE-001` is integrated on the approved default branch, and `CORE-REQ-004` through `CORE-REQ-016` pass their owned lifecycle, journal, escrow, recovery, surface-routing, backup, and security gates. | Integrated `CORE-PHASE-001` completion packet | Any change to the provider API, capability schema, lifecycle, transaction schema, persistence, recovery, or routed monetary surfaces invalidates this entry proof. |
| VERIFIED | Pixelmon artifact identity | `EXT-001` identifies exact official Pixelmon `9.4.0` inputs, provenance, byte sizes, SHA-256 and SHA-512 hashes, terms, archive contents, dependencies, and security conclusions. | `EXT-001`, `EVD-EXT-001`, and `EVD-EXT-002` | Any byte, source, publisher, URL, version, dependency, terms, or archive change invalidates affected compile, compatibility, runtime, and packaging evidence. |
| VERIFIED | Pixelmon capability classification | Exact API review shows query and precheck behavior plus boolean mutation calls, but no durable request identity, receipt lookup, idempotent retry, or outcome lookup. This is valid negative feasibility evidence under `DEC-018`; it mandates safe mutation refusal rather than blocking the query adapter. | `EXT-003`, `EVD-EXT-003`, and the Phase 000 exact class and signature inventory | Stronger exact evidence, an artifact change, or changed runtime behavior requires capability reclassification. Mutation remains disabled until all required capabilities are positively proven. |
| VERIFIED | Pixelmon environment manifest | `EXT-002` defines a disposable exact Minecraft `1.21.1`, NeoForge `21.1.248`, Pixelmon `9.4.0`, Java, configuration, and FutureShops artifact environment. | `EXT-002` and `EVD-EXT-004` | Any runtime, configuration, Java, mod set, launcher, or artifact hash change requires environment recreation or revalidation. |
| VERIFIED | Bridge artifact identity | `EXT-004` identifies the exact separately installed bridge, source and binary provenance, version, byte sizes, hashes, license or terms, compatibility, archive contents, dependencies, registration contract, and security conclusions. | `EXT-004` and `EVD-EXT-005` | Any source, binary, dependency, compatibility promise, registration contract, license, or hash change invalidates bridge evidence. |
| VERIFIED | Exact hybrid stack | `EXT-005` identifies one exact hybrid runtime, Vault artifact, economy plugin, and supporting dependency set with versions, sources, sizes, hashes, licenses, archive review, and security conclusions. | `EXT-005` and `EVD-EXT-006` | Any component, configuration, dependency, load order, or hash change invalidates affected `vault` interoperability claims. |
| VERIFIED | Hybrid environment manifest | `EXT-006` defines a disposable exact environment containing the reviewed FutureShops artifact, bridge, hybrid runtime, Vault artifact, economy plugin, and supporting dependencies. | `EXT-006` and `EVD-EXT-007` | Any installed bytes, configuration, plugin load order, Java, client count, or FutureShops artifact change requires revalidation. |
| VERIFIED | Strict economy gate | The integrated core declares capabilities explicitly, journals intent and receipt audit transitions before permitted external effects, preserves item custody and claims, drains on orderly shutdown, recovers before writes after an unclean shutdown or unknown receipt record, and freezes unresolved uncertainty. | `DEC-017`, `CORE-REQ-002`, `CORE-REQ-004`, `CORE-REQ-005`, `CORE-REQ-007`, `CORE-REQ-009`, and `CORE-REQ-016` completion evidence | Any API, lifecycle, journal, receipt audit, escrow, claim, or recovery change invalidates both adapter integration proofs. |
| VERIFIED | Continuation issue | Plan authoring created and read back open issue 66. Phase 002 consumes only the frozen authoring record and performs no live issue query or mutation. | `DEC-015`, `EXT-007`, and `EVD-GH-001` | Any execution-side issue access before the Phase 003 post-artifact gate is a timing defect. Phase 003 alone verifies and updates the same issue. |
| UNKNOWN | Exact source paths | Concrete adapter and test paths remain implementation evidence to discover from the integrated repository layout. | Repository inspection at phase entry | The first edit records affected paths; later moves or source-set changes require scan and traceability updates. |

The negative Pixelmon capability finding is not an invitation to guess and is not a reason to omit the adapter. It is the required input for a query-capable, mutation-refusing implementation. `EXT-008` must be satisfied before changing a disposable environment from `eula=false` or performing full runtime mutation, restart, or recovery exercises. Read-only artifact inspection, implementation, deterministic tests, packaging inspection, and environment assembly may proceed without inferring acceptance.

## Scope Boundaries

### Included Scope

- `CORE-REQ-017`: implement exact Pixelmon `9.4.0` presence and version detection, declared capability reporting, exact balance query and precheck behavior supported by reviewed evidence, typed failure mapping, and deterministic refusal of unsupported mutation-required surfaces.
- `CORE-REQ-017`: prove refusal occurs before write-ahead intent, item custody, claims, inventory movement, Pixelmon `add` or `take`, analytics, or transaction-success events; no balance mirror or inferred compensation is created.
- `CORE-REQ-017`: prove unsupported Pixelmon versions do not register, exact Pixelmon absence does not link optional classes, and the same FutureShops jar starts on a standard NeoForge client and dedicated server.
- `CORE-REQ-018`: validate one exact separately installed bridge and hybrid stack that registers `vault` solely through the public provider API and declares query, precheck, withdraw, deposit, durable receipt lookup, and idempotent retry capabilities.
- `CORE-REQ-018`: enable only the exact `vault` surfaces whose complete capability set and crash-recovery behavior are proven. Capability-incomplete, missing, late, incompatible, duplicate, or failing bridge states remain unavailable and fail closed.
- `CORE-REQ-018`: exercise the strict economy gate, journal, durable receipt audit records under `world/data/futureshops/receipts`, escrow, claims, orderly draining, unclean-start recovery, unknown-record classification, frozen uncertainty, duplicate request handling, service loss, restart, reconnect, and multiplayer behavior against the exact reviewed stack.
- Dependency, classloading, archive, provenance, license, security, packaging, documentation, and operator evidence required for Phase 003.

### Explicit Exclusions

- `FUT-001` and `FUT-002`: no FutureShops `3.0.0` implementation is performed. Issue 66 remains unchanged in this phase.
- `FUT-003`: no ATM user interface or command is added.
- `FUT-004`: no additional provider, bridge, Pixelmon release, hybrid stack, or provider-priority system is supported.
- `NG-002`, `NG-004`, and `NG-005`: no balance migration, hot activation, provider fallback, unsafe override, or external balance mirroring is introduced.
- `NG-003`: no Pixelmon, Bukkit, Vault, hybrid runtime, economy plugin, or bridge artifact is bundled in FutureShops.
- Phase 003 owns final plan-wide validation, final candidate assembly, publication-exclusion proof, and the post-artifact update and readback of issue 66.

## Phase Contract

### CORE-PHASE-002 — Exact capability-gated integrations

**Objective:** Deliver one isolated exact Pixelmon `9.4.0` query adapter that safely refuses unsupported mutations and prove mutation-capable `vault` interoperability only through one exact reviewed separately installed bridge stack, with strict lifecycle, escrow, restart, recovery, security, and packaging evidence.
**Owner:** Exact external provider integrations
**Dependencies:** CORE-PHASE-001, CORE-REQ-002, CORE-REQ-004, CORE-REQ-005, CORE-REQ-006, CORE-REQ-007, CORE-REQ-009, CORE-REQ-015, CORE-REQ-016, DEC-013, DEC-017, DEC-018, EXT-001, EXT-002, EXT-003, EXT-004, EXT-005, EXT-006, EXT-008
**Canonical requirements:** CORE-REQ-017, CORE-REQ-018
**Documentation and release impact:** Update compatibility, configuration, integration, transaction-safety, recovery, operator, and troubleshooting documentation with exact supported and refused behavior. Produce no release, tag, upload, announcement, or public artifact.
**Next transition:** `CORE-PHASE-003` after this phase is integrated through the sequential branch and pull-request workflow and all completion evidence is accepted.

**Entry criteria**

- `CORE-PHASE-001` is merged and verified on the approved default branch; no future phase branch is stacked.
- `EXT-001` through `EXT-006` evidence is reproduced against exact hashes and remains compatible with the frozen core API and capability model.
- `EXT-003` records the direct Pixelmon API's missing receipt lookup and idempotent retry as a negative capability result and routes implementation to deterministic refusal under `DEC-018`.
- `EXT-008` is satisfied before any full disposable runtime launch that requires terms acceptance. The authorization record contains no credentials or private account data.
- Frozen authoring `EVD-GH-001` identifies issue 66. Phase entry and execution perform no live issue search, query, readback, or mutation.

**Implementation scope**

- Implement `CORE-REQ-017` as an optional exact-version adapter that exposes only reviewed query and precheck capabilities and rejects every mutation-required operation before any local or external side effect.
- Implement `CORE-REQ-018` as public-API interoperability with an external `vault` registrant. Enable a surface only after exact runtime proof confirms every required mutation and recovery capability.
- CORE-PHASE-001, CORE-REQ-004, CORE-REQ-005, CORE-REQ-006, CORE-REQ-007, CORE-REQ-009, CORE-REQ-015, and CORE-REQ-016 preserve the journal, escrow, claim, lifecycle, server-authority, checked-value, and no-fallback invariants across both integration paths.
- CORE-REQ-001 and CORE-REQ-015 preserve one client/server jar and keep all optional external types and bytes outside ordinary startup and packaged output.

**Execution order**

1. `P002-TASK-001` freezes EXT-001, EXT-002, EXT-003, EXT-004, EXT-005, and EXT-006 exact artifacts, environment manifests, capability classifications, and legal and security evidence for CORE-REQ-017 and CORE-REQ-018.
2. `P002-TASK-002` maps the EXT-003 Pixelmon `9.4.0` query API and negative mutation capability result onto CORE-REQ-017 and the frozen provider contract.
3. `P002-TASK-003` implements CORE-REQ-017 optional Pixelmon presence, version, metadata, query, precheck, lifecycle, and exception isolation.
4. `P002-TASK-004` implements and proves CORE-REQ-017 pre-effect refusal for every Pixelmon mutation-required surface.
5. `P002-TASK-005` proves CORE-REQ-017 Pixelmon absence, exact presence, incompatible presence, restart, reconnect, and clean packaging behavior.
6. `P002-TASK-006` reviews the CORE-REQ-018 exact bridge registration and capability contract without adding a FutureShops bridge-specific dependency or route.
7. `P002-TASK-007` verifies CORE-REQ-018 exact `vault` value conversion, query, precheck, withdraw, deposit, receipt lookup, and idempotent retry behavior.
8. `P002-TASK-008` exercises all CORE-REQ-018 enabled `vault` surfaces through journal and receipt audit records under `world/data/futureshops/receipts`, escrow, claims, draining, recovery, frozen ambiguity, duplicate, restart, and service-loss workflows.
9. `P002-TASK-009` validates CORE-REQ-018 missing, incomplete, late, duplicate, incompatible, and failed bridge states and proves fail-closed operation with no internal fallback.
10. `P002-TASK-010` performs CORE-REQ-017 and CORE-REQ-018 optional dependency, classloading, security, archive, and packaging isolation checks using the same FutureShops artifact in all environments.
11. `P002-TASK-011` updates and rehearses CORE-REQ-017 and CORE-REQ-018 exact integration, recovery, backup, and rollback documentation while preserving issue 66 unchanged.
12. `P002-TASK-012` runs the complete CORE-REQ-017 and CORE-REQ-018 verification order, inspects the diff and jar, records the completion packet, and integrates the phase sequentially.

**Required evidence**

- Exact artifact records and disposable environment manifests for `EXT-001` through `EXT-006`, with SHA-256 and SHA-512 hashes, provenance, compatibility, license or terms, archive, dependency, and security conclusions.
- Pixelmon adapter unit, integration, surface, optional-linkage, query, refusal, lifecycle, restart, reconnect, and packaging evidence tied to exact `9.4.0` bytes.
- Proof that Pixelmon mutation refusal precedes journal, custody, inventory, provider, analytics, event, or claim side effects and never calls `add` or `take`.
- Exact `vault` bridge registration, capability, conversion, mutation, provider receipt, local receipt audit, idempotency, duplicate, crash, recovery, restart, service-loss, multiplayer, and surface matrices.
- Standard NeoForge client and dedicated-server startup with every optional integration absent, plus jar and dependency inspection showing no forbidden external bytes or linkage.
- Focused tests, complete tests, applicable data generation and GameTests, complete build, runtime logs, operator rehearsal, documentation diff, full Git diff, and artifact contents.

**Exit criteria**

- `CORE-REQ-017` passes with exact query and precheck behavior, explicit missing mutation capabilities, deterministic pre-effect mutation refusal, optional dependency isolation, exact runtime evidence permitted by `EXT-008`, and no direct Pixelmon production mutation claim.
- `CORE-REQ-018` passes only if the exact reviewed bridge stack proves every enabled surface's complete capability and recovery contract; otherwise the requirement remains open and the product makes no Vault production support claim.
- Both integration paths preserve journal, escrow, claims, lifecycle, checked arithmetic, server authority, no fallback, no balance mirroring, restart, recovery, security, and packaging invariants.
- The authoring issue record remains locally unchanged, and phase evidence proves no live issue access or execution-side mutation occurred. Phase 003 owns remote confirmation after artifact validation.
- No known mandatory phase-owned defect remains.
- No unresolved artifact concern, unsupported compatibility claim, or invalidated evidence remains.

## Inputs and Upstream Contracts

| Input or contract | Provider | Required state | Validation | Failure behavior |
| --- | --- | --- | --- | --- |
| Public provider API and capability schema | `CORE-PHASE-000` and `CORE-PHASE-001` | Frozen versioned registration, metadata, typed outcomes, server threading, query, mutation, receipt, and retry capability contract | API compatibility and integration tests | Stop and return the defect to its owning phase; do not patch around it in an adapter. |
| Strict economy gate and lifecycle | `CORE-PHASE-001` | `READY`, `DRAINING`, `RECOVERING`, and `FROZEN` behavior with no new writes outside `READY` | Lifecycle, shutdown, and restart evidence | Fail closed and invalidate both runtime integration matrices. |
| Journal, receipt audit, escrow, custody, and claims | `CORE-PHASE-001` | Intent-before-permitted-effect, a durable receipt audit record under `world/data/futureshops/receipts` for each transition, durable custody, claim conservation, provider binding, and uncertain outcome preservation | Persistence, receipt-directory, and crash matrices | Freeze affected mutation path; never guess, retry blindly, or restore an external balance from local data or a local receipt record. |
| Exact Pixelmon evidence | `EXT-001`, `EXT-002`, `EXT-003` | Exact `9.4.0` artifacts and environment, exact query signatures, and negative receipt and idempotency classification | Hash, archive, signature, capability, and environment checks | Preserve query-only classification and refuse mutation; artifact mismatch blocks affected evidence. |
| Exact bridge and hybrid evidence | `EXT-004`, `EXT-005`, `EXT-006` | Reviewed registrant and exact runtime stack with complete declared capabilities | Hash, source, archive, license, dependency, registration, and runtime checks | Do not enable `vault`; leave server online and monetary mutations unavailable. |
| Runtime terms authorization | `EXT-008` | Owner acceptance for the exact disposable environments before full launch | Owner-provided authorization record without secrets | Keep `eula=false`; continue only read-only inspection, implementation, deterministic tests, packaging checks, and assembly. |
| Issue 66 identity | `EVD-GH-001` | Frozen authoring evidence identifies the existing continuation issue and creation readback | Validate only the local evidence record; perform no live issue access | Preserve it for Phase 003; do not query, mutate, or create a replacement. |

## Outputs and Downstream Contracts

| Output or contract | Consumer | Guaranteed state | Compatibility or versioning | Evidence |
| --- | --- | --- | --- | --- |
| Pixelmon capability adapter | `CORE-PHASE-003` | Exact `9.4.0` query and precheck only, with unsupported mutation refusal before all effects | No registration for another version; no Pixelmon artifact bundled | Adapter tests, exact runtime logs, refusal matrix, and jar scan |
| Direct Pixelmon capability statement | Operators and Phase 003 documentation | Missing receipt lookup and idempotent retry are visible; direct production mutations are not claimed | Reclassification requires stronger reviewed exact evidence and renewed tests | Capability report, diagnostics, configuration behavior, and documentation |
| Exact `vault` interoperability result | `CORE-PHASE-003` | Only one separately installed reviewed stack may provide enabled mutations, and only for positively proven capabilities | Claims bind exact versions and hashes; no generic Vault or hybrid promise | Registration, capability, runtime, recovery, and environment matrices |
| Isolation and packaging proof | Phase 003 artifact audit | Same FutureShops jar starts without optional stacks and contains no external bytes or forbidden linkage | Client/server parity and NeoForge `21.1.248` preserved | Dependency report, bytecode scans, jar listing, and standard startup logs |
| Phase completion packet | `CORE-PHASE-003` | Source revision, artifact hash, external hashes, manifests, test results, runtime proof, docs, and known limitations are reproducible | Any changed byte or contract invalidates dependent evidence | Completion packet and rerun ledger |
| Preserved issue evidence | `CORE-PHASE-003` | Frozen authoring identity plus proof that phases 000 through 002 made no live issue access or mutation | Phase 003 alone owns remote verification, post-artifact update, and readback | Local authoring-record integrity and no-live-access audit |

## Work Packages

| Task ID | Requirement IDs | Work | Inputs and dependencies | Outputs | Affected components or interfaces | Verification |
| --- | --- | --- | --- | --- | --- | --- |
| `P002-TASK-001` | CORE-REQ-017, CORE-REQ-018 | Reproduce all exact artifact, environment, provenance, license, dependency, security, and capability records before implementation. | EXT-001 through EXT-006, DEC-017, DEC-018 | Frozen artifact and capability ledger tied to hashes | External compile inputs, disposable environments, evidence store | Hash, archive, manifest, source, license, dependency, and security review |
| `P002-TASK-002` | CORE-REQ-017 | Map exact Pixelmon query and precheck signatures and explicitly classify `add` and `take` as unusable for strict production mutation without receipts and safe replay. | EXT-003, public provider API | Exact adapter mapping and refusal table | Provider capabilities, typed query results, failure taxonomy | Signature tests, exact conversion fixtures, negative capability assertions |
| `P002-TASK-003` | CORE-REQ-017 | Implement isolated presence and exact-version detection, optional registration, metadata, balance query, precheck, lifecycle, threading, and exception containment. | P002-TASK-001, P002-TASK-002 | Query-capable exact Pixelmon adapter | Optional integration boundary and provider registry | Absent, exact, incompatible, exception, server-thread, and repeated-start tests |
| `P002-TASK-004` | CORE-REQ-017 | Route every mutation-required surface to capability refusal before journal, custody, inventory, provider, claim, analytics, or success-event effects. | P002-TASK-003, Phase 001 surface matrix | Complete Pixelmon safe-refusal implementation | Shops, carts, player shops, pay, claims, administration, bills, fees, refunds, and public APIs | Per-surface spies and state comparisons proving zero `add` or `take` calls and zero side effects |
| `P002-TASK-005` | CORE-REQ-017 | Exercise standard NeoForge, internal with Pixelmon present, exact Pixelmon query mode, absent and incompatible Pixelmon, reconnect, orderly restart, unclean restart, and packaging. | EXT-002, EXT-008, P002-TASK-003, P002-TASK-004 | Pixelmon runtime and isolation matrix | Dedicated server, client, multiplayer, persistence, diagnostics | Environment hashes, sanitized logs, restart state inspection, jar scan |
| `P002-TASK-006` | CORE-REQ-018 | Review and prove external registration of `vault` through the public API, declared capabilities, metadata, lifecycle timing, duplicate handling, and no bridge-specific FutureShops route. | EXT-004 through EXT-006, public API | Exact registration and capability report | Provider registry and external bridge boundary | Registration integration tests, dependency and source scans, missing and duplicate cases |
| `P002-TASK-007` | CORE-REQ-018 | Verify exact integer conversion and every claimed query, precheck, withdraw, deposit, durable receipt lookup, and idempotent retry operation against the reviewed stack. | P002-TASK-006, exact hybrid environment | Proven per-operation capability matrix | Provider outcomes and external service boundary | Boundary values, response mapping, request replay, receipt lookup, concurrent duplicate tests |
| `P002-TASK-008` | CORE-REQ-018 | Exercise each enabled `vault` surface through write-ahead intent, a durable receipt audit transition under `world/data/futureshops/receipts`, item custody, external effect, durable provider outcome, delivery or claim, draining, restart, recovery, and frozen ambiguity. Confirm that local receipt records preserve evidence but do not authorize replay without the bridge's provider receipt and idempotent contract. | P002-TASK-007, Phase 001 journal, receipt audit, and escrow | Exact surface and recovery matrix | All monetary surfaces, journal, receipt audit directory, escrow, claims, lifecycle | Injected failures at every boundary, crash and restart, missing or contradictory local records, multiplayer, reconnect, conservation assertions |
| `P002-TASK-009` | CORE-REQ-018 | Prove missing, late, incompatible, duplicate, capability-incomplete, throwing, disconnected, and ambiguous bridge states fail closed with no internal fallback or blind retry. | P002-TASK-006 through P002-TASK-008 | Negative bridge lifecycle matrix | Provider selection, diagnostics, presentation, recovery | Runtime service loss, startup order, malformed result, interruption, and frozen-state tests |
| `P002-TASK-010` | CORE-REQ-017, CORE-REQ-018 | Inspect dependency graphs, classpaths, bytecode, reflection strings, services, archives, resources, licenses, secrets, generated output, and jar contents. | Both integration implementations and one built artifact | Isolation and packaging report | Build, runtime classloading, final jar | Clean standard client and server startup plus forbidden dependency and embedded-byte scans |
| `P002-TASK-011` | CORE-REQ-017, CORE-REQ-018 | Update exact installation, compatibility, capability, refusal, transaction safety, restart, backup, recovery, and rollback documentation and rehearse it on disposable copies. | Passing integration behavior and exact manifests | Accurate operator docs and rehearsal record | README and detected focused documentation | Link, version, hash, command, expected-result, and recovery rehearsal checks |
| `P002-TASK-012` | CORE-REQ-017, CORE-REQ-018 | Run the complete verification sequence, inspect full diff and artifact, assemble evidence, preserve the frozen issue 66 authoring record without live access, and integrate the phase sequentially. | P002-TASK-001 through P002-TASK-011 | Accepted completion packet and Phase 003 handoff | Repository, CI, pull request, evidence store | Required tests and runtimes pass, signed integration evidence, local authoring-record integrity and no-live-access audit, downstream reproducibility check |

Tasks are dependency ordered. Read-only artifact review for both integrations and deterministic unit-fixture preparation may proceed independently after `P002-TASK-001`, but no task may mutate the same implementation surface concurrently. A failure returns to its earliest owning task and invalidates all dependent runtime, documentation, and artifact proof. No test may invoke Pixelmon mutation merely to demonstrate that it is unsafe; the required result is refusal before that boundary.

## Architecture and Implementation Boundaries

The server-owned economy gate remains the only entry point for balance-sensitive workflows. Provider capability metadata is frozen at restart-time selection and is not inferred from method presence alone. A surface declares the capabilities it requires. If any capability is absent, the gate returns a typed unavailable or unsupported result before preparing a write-ahead record or moving an item into custody. This early-refusal invariant is especially mandatory for direct Pixelmon `9.4.0`.

The Pixelmon adapter may refer to reviewed optional types only behind the repository's established optional integration boundary. Ordinary common initialization, dedicated-server startup without Pixelmon, and client startup must not resolve Pixelmon classes. The adapter checks the exact mod identifier and version and exposes only exact, lossless query values. Boolean `add` and `take`, balance events, and prechecks are not receipts and do not authorize mutation. The adapter stores no external balance and provides no compensation path that invents or rewrites Pixelmon money.

FutureShops does not implement a Vault adapter. The separately installed bridge registers `vault` through the same public NeoForge provider contract used by any external registrant. FutureShops contains no Bukkit or Vault imports, dependencies, reflection, service lookup, plugin lifecycle code, or bridge special case. The bridge owns translation to the exact hybrid runtime and economy plugin. FutureShops writes local request and transition evidence to `world/data/futureshops/receipts`, but the directory is an audit trail and cannot establish that a Vault operation completed or is safe to replay. FutureShops accepts its mutation capabilities only after runtime proof demonstrates stable request identity, durable provider receipt lookup, idempotent retry, exact value conversion, and typed failure behavior.

For an enabled `vault` mutation, the Phase 001 state machine remains authoritative: persist intent, the local receipt audit transition, and custody before the external effect, use stable root and leg identities, persist a proven provider outcome before delivery, and turn failed delivery into a durable claim. `DRAINING` rejects new work and lets bounded in-flight work settle before a clean marker that is written after the journal, receipt audit directory, custody, and claims are flushed. An unclean start or unknown receipt record enters `RECOVERING`; receipt lookup by stable identity decides a proven outcome. Missing or contradictory evidence enters `FROZEN`; no blind retry, fabricated refund, automatic external balance restore, or internal fallback is permitted.

All provider work executes on the required logical-server thread. Inputs, amounts, precision, player identity, permissions, provider identity, request identity, returned values, and collection or string bounds are validated. Checked integer minor-unit arithmetic is mandatory. External exceptions are contained and mapped to typed provider failure. Logs identify provider, request, operation, and recovery category without balances not needed for diagnosis, private player data, credentials, or secret-bearing configuration.

## Failure, Recovery, and Edge Cases

| Scenario | Detection | Required behavior | Recovery or rollback | Regression proof |
| --- | --- | --- | --- | --- |
| Pixelmon absent | Mod and optional class check | Standard NeoForge starts; adapter does not register; `internal` remains available | Install exact reviewed version and restart if desired | Dedicated server and client startup without Pixelmon |
| Pixelmon version is not exactly `9.4.0` | Exact metadata comparison | Do not register adapter; report incompatible version | Install exact version and restart | Version-boundary tests and incompatible-runtime log |
| Pixelmon query fails or returns invalid data | Exception, null, nonexact, overflow, or domain validation | Return typed query failure; never return zero or insufficient funds as a substitute | Correct dependency or data and retry a new query | Query failure and numeric boundary fixtures |
| Pixelmon mutation surface is requested | Required capability set lacks receipt lookup or idempotent retry | Refuse before journal, custody, provider, inventory, claim, analytics, or event effects | Select `internal` or install a proven external bridge and restart; do not call `add` or `take` | Per-surface spy assertions and saved-state comparison |
| Stronger Pixelmon mutation evidence appears | Reviewed exact signatures and runtime proof differ from `EXT-003` | Treat prior evidence as invalid; do not enable automatically | Reclassify through the authoritative plan and rerun all affected tasks | Evidence invalidation test and capability snapshot comparison |
| Bridge absent or late | Registration window closes without exact `vault` provider | Server stays online; selected external mutations are unavailable; no hot activation or fallback | Install exact stack and restart | Missing and late registration tests |
| Bridge claims incomplete capabilities | Frozen capability descriptor lacks any surface requirement | Disable that surface before journal or custody | Correct bridge and restart; never infer capability from method presence | Capability-combination matrix |
| Bridge returns ambiguous mutation result | Missing provider receipt, conflicting provider receipt, timeout, or disconnect after effect boundary | Preserve journal, local receipt audit records, and custody, enter recovery or frozen state, and do not retry blindly | Query durable provider receipt by stable identity; operator intervention if still unknowable | Injected timeout, missing-record, and crash matrix |
| Duplicate request arrives | Stable root or leg identity already exists | Return the recorded outcome or current recovery state; perform no second external effect | Complete recovery for the original request | Concurrent, reconnect, and restart replay tests |
| External service disappears during runtime | Provider exception or readiness transition | Reject new mutation work, settle or freeze in-flight requests from evidence, keep browsing and pure barter available | Restore exact service and recover by receipt identity | Service-loss tests at every effect boundary |
| Orderly restart begins | Server lifecycle enters `DRAINING` | Reject new writes, finish bounded proven work, flush journal and custody, write clean marker last | Restart and select provider normally | Drain race and clean-marker ordering tests |
| Unclean restart or unknown receipt record occurs | Clean marker absent, in-flight intent remains, or `world/data/futureshops/receipts` contains an unknown, incomplete, or integrity-invalid record | Enter `RECOVERING` before admitting writes; reconcile enabled `vault` requests by durable provider receipt; direct Pixelmon has no mutation entries because refusal was pre-intent | Resume only after all outcomes and local records are proven; otherwise remain `FROZEN` | Crash at each state transition, receipt-directory corruption, and recovery inspection |
| Item delivery fails after proven debit | Inventory or delivery result fails | Preserve item in durable claim under original provider identity | Claim after safe readiness returns; never refund from a guess | Full inventory and reconnect claim tests |
| Optional type links during ordinary startup | Linkage error or bytecode scan | Fail the phase; no artifact may proceed | Repair isolation and rebuild | Clean standard NeoForge startup and class-reference scan |
| Artifact or stack hash drifts | Pre-run manifest comparison | Discard affected result and make no compatibility claim | Restore reviewed bytes or repeat artifact review | Hash guard and evidence freshness test |

## Verification Matrix

| Requirement or task | Static or unit | Integration | Real workflow or runtime | Negative and recovery | Evidence artifact |
| --- | --- | --- | --- | --- | --- |
| `P002-TASK-001` | Manifest and digest schema checks | Artifact-to-environment matching | Reproduce acquisition and installed hashes | Mismatch, missing license, or dependency finding blocks affected path | `EVD-EXT-001` through `EVD-EXT-007` refresh record |
| `P002-TASK-002` | Signature, value, and capability mapping tests | Provider descriptor validation | Exact Pixelmon query signatures exercised | No receipt or idempotency maps to mutation unavailable | Pixelmon API and capability report |
| `P002-TASK-003` | Presence, version, metadata, threading, query, and exception tests | Registry and lifecycle integration | Exact Pixelmon query and precheck on server | Absent, incompatible, invalid value, and thrown query | Adapter test report and sanitized logs |
| `P002-TASK-004` | One refusal assertion set per surface | Coordinator and presentation integration | Attempt every mutation-required workflow with Pixelmon selected | Zero journal, custody, inventory, provider, claim, analytics, and success-event changes | Pixelmon refusal and state-conservation matrix |
| `P002-TASK-005` | Optional reference and archive scans | Standard and Pixelmon-present startup | Client, dedicated server, multiplayer, reconnect, clean and unclean restart | Missing and incompatible version, query failure | `EXT-002` manifest, runtime logs, jar report |
| `P002-TASK-006` | Registration, descriptor, duplicate identifier, dependency, and source scans | Exact bridge registration through public API | Exact hybrid startup and frozen selection | Missing, late, incompatible API, duplicate `vault` | Bridge registration and boundary report |
| `P002-TASK-007` | Conversion, bounds, response, receipt, and replay tests | Coordinator against exact external services | Balance, precheck, debit, credit, receipt lookup, and replay | Overflow, precision loss, malformed response, concurrent duplicate | Exact `vault` capability matrix |
| `P002-TASK-008` | Journal, receipt-audit, and state transition assertions | All enabled surfaces through escrow and claims | Multiplayer, restart, reconnect, shutdown, crash, and recovery with `world/data/futureshops/receipts` included in the matching backup set | Interrupt every state and external-effect boundary, remove or corrupt a local receipt record, and prove `RECOVERING` or `FROZEN` | Surface, receipt-directory, conservation, and recovery matrix |
| `P002-TASK-009` | Failure taxonomy and lifecycle assertions | Registry and coordinator failure integration | Remove or fail each exact component in turn | Capability incomplete, timeout, exception, ambiguous outcome | Negative stack and frozen-state report |
| `P002-TASK-010` | Dependency, bytecode, string, service, secret, and archive scans | Same jar in all environments | Standard client and dedicated server without optional stacks | Forbidden class, reflection, embedded bytes, debug or private data | Isolation, security, and packaging report |
| `P002-TASK-011` | Link, identifier, version, hash, and example checks | Follow docs on clean disposable copies | Install, select, restart, diagnose, backup, recover, and roll back | Unsafe deletion, guessed balance correction, unsupported claim | Documentation diff and rehearsal record |
| `P002-TASK-012` | Focused and complete tests, diff inspection | Applicable data and GameTests, complete build | Repeat standard, exact Pixelmon, and exact hybrid workflows with one artifact | Rerun every invalidated refusal and recovery case | Phase completion packet tied to source revision and hashes |
| `CORE-REQ-017` | Query, exact conversion, capability, refusal, lifecycle, and isolation suites | Complete Pixelmon surface mapping | Exact `9.4.0` runtime proof authorized by `EXT-008` | Absent, incompatible, query failure, all mutation refusals, restart | `EVD-EXT-001` through `EVD-EXT-004`, matrices, logs, jar scan |
| `CORE-REQ-018` | API boundary, capability, conversion, provider receipt, local receipt-audit, replay, and isolation suites | Complete exact `vault` surface mapping | Exact hybrid multiplayer, restart, service loss, crash, and recovery | Every component absent, incomplete capability, missing or contradictory local record, ambiguity, duplicate | `EVD-EXT-005` through `EVD-EXT-007`, matrices, logs, dependency scan |

Fixtures use disposable players, balances, products, shops, carts, player shops, claims, and provider records. Numeric coverage includes zero where legal, invalid negative values, one minor unit, maximum accepted values, aggregate overflow, external precision boundaries, fractional values when the external type permits them, and nonfinite values when representable. Stable root and leg UUIDs are retained across retries and restarts; logically new actions use new identities.

Every runtime manifest records source commit, FutureShops artifact SHA-256 and SHA-512, Minecraft, NeoForge, Java, Pixelmon, bridge, hybrid runtime, Vault artifact, economy plugin, supporting dependencies, configuration, client count, acquisition and run dates, expected and actual outcomes, and sanitized evidence locations. Installed hashes are verified before each run. A result from mismatched bytes is discarded.

Verification follows repository order: confirm formatting and static-analysis task availability, run focused tests, complete tests, applicable generated-data validation, applicable GameTests, complete build, standard dedicated server, client, multiplayer and reconnect, restart and recovery matrices, exact Pixelmon workflows, exact hybrid workflows, then dependency, security, jar, generated-output, secret, and complete-diff inspection. Lower-fidelity evidence never substitutes for a required exact runtime. If `EXT-008` remains unavailable, affected full runtime gates stay open and the phase cannot exit.

## Documentation, Operations, and Release

Update the existing README, technical documentation hub, configuration guide, integration guide, transaction and recovery documentation, and troubleshooting material identified by the repository. Documentation must distinguish these two outcomes without ambiguity:

- Exact Pixelmon `9.4.0` is detected and may provide verified query and precheck behavior. Its currently reviewed direct API is not mutation safe under strict mode, so FutureShops refuses buys, sells, carts, player-shop money legs, pay, deposit, withdrawal, fees, refunds, and other mutation-required paths before any side effect. Do not advertise direct Pixelmon production mutation support.
- `vault` is available only when a separately installed exact reviewed bridge registers through the public API and proves the required capabilities. Name the exact tested bridge, hybrid runtime, Vault artifact, economy plugin, supporting dependencies, versions, and hashes. Do not advertise generic Vault or hybrid compatibility.

Document restart-only provider selection, `internal` default, no hot activation or fallback, `READY`, `DRAINING`, `RECOVERING`, and `FROZEN` states, exact minor units, provider-owned balances, no balance migration, no mirrored ledger, no automatic external balance restore, physical money restrictions, browsing and pure barter availability, custody and claims, clean and unclean restart behavior, diagnostics, and operator price review before changing provider.

Backup and recovery procedures preserve a complete matching world, journal, the receipt audit directory at `world/data/futureshops/receipts`, custody, claims, internal data, and external provider data. They must never instruct operators to delete transaction or receipt evidence, infer an external outcome from a local balance snapshot or local receipt record, retry an uncertain mutation blindly, or edit a balance as guessed compensation. Unknown or integrity-invalid receipt records force `RECOVERING` or `FROZEN` before monetary writes. Operator rehearsals use disposable copies and demonstrate exact installation, provider selection and restart, safe Pixelmon refusal, exact `vault` transaction and recovery when proven, dependency loss, claim preservation, complete backup restoration, and return to `internal` without balance transfer.

One jar is installed on client and server. Pixelmon and every bridge-stack component are acquired separately under their own terms and are absent from FutureShops packaging. No release, tag, GitHub release, CurseForge or Modrinth upload, announcement, or public artifact is created. Issue 66 remains open and unchanged; Phase 003 alone verifies and updates it after artifact validation.

## Risks and Evidence Invalidation

| Risk | Prevention | Detection | Recovery | Evidence invalidated | Reverification |
| --- | --- | --- | --- | --- | --- |
| Direct Pixelmon mutation is enabled from method presence | Explicit capability requirements and `DEC-018` negative classification | Capability snapshot or call trace shows `add` or `take` reachable | Disable mutation path and restore pre-effect refusal | All Pixelmon adapter, surface, restart, docs, and artifact evidence | Restart at `P002-TASK-002` and rerun all Pixelmon tasks |
| Pixelmon refusal occurs after a local side effect | Capability check before journal and custody | State comparison shows journal, item, claim, event, or analytics change | Repair ordering and restore disposable snapshot | Pixelmon refusal, surface, persistence, and docs evidence | Full `P002-TASK-004` and affected runtime workflows |
| Pixelmon optional types link without Pixelmon | Strict optional boundary and clean-environment scans | Linkage failure or forbidden bytecode reference | Refactor isolation and rebuild | Standard startup, registration, packaging, client, and server evidence | `P002-TASK-003`, `P002-TASK-005`, and `P002-TASK-010` |
| External bytes are redistributed | External compile and test inputs, archive matching | Jar or nested-jar content match | Remove bytes, clean build, repeat legal and packaging review | Artifact, license, security, and all artifact-based runtime evidence | `P002-TASK-001`, `P002-TASK-010`, and runtime replay |
| Bridge overstates a capability | Independent exact runtime behavior proof | Receipt lookup, replay, or failure test contradicts descriptor | Reject capability or entire provider; keep mutations unavailable | Exact `vault` capability, surface, recovery, and docs evidence | Restart at `P002-TASK-006` |
| Exact hybrid outcome is generalized | Bind every claim to hashes and explicit stack identity | Documentation or metadata omits exact qualifier | Correct claim and rerun documentation and operator review | Compatibility, docs, operator, and completion evidence | `P002-TASK-011` and completion audit |
| External effect is ambiguous after crash | Intent first, stable identity, durable provider receipt lookup, and local receipt audit record | Incomplete or contradictory outcome after restart | Reconcile by provider receipt or remain `FROZEN`; never blind retry based only on `world/data/futureshops/receipts` | Affected mutation, surface, receipt-directory, crash, and recovery evidence | Earliest failed boundary through `P002-TASK-008` completion |
| Duplicate external effect occurs | Stable identity and proven idempotent retry | More than one receipt or balance delta for one leg | Block provider, preserve evidence, repair bridge or adapter, restore clean snapshot | Entire `vault` mutation and recovery evidence | Full `P002-TASK-007` through `P002-TASK-009` matrices |
| Artifact or environment drifts | Verify exact hashes before every run | Manifest mismatch | Restore exact bytes or repeat review | All results from changed environment | Restart at `P002-TASK-001` for affected stack |
| Upstream core contract changes | Bind integration to integrated Phase 001 revision | API, schema, call graph, or fixture diff | Return to owning requirement and reintegrate sequentially | Both integrations wherever changed contract is used | Reenter Phase 002 after upstream integration |
| Runtime terms are not accepted | Keep `eula=false` until explicit `EXT-008` record exists | Missing owner authorization | Continue safe nonruntime work; keep runtime gates open | Full launch, mutation, restart, and recovery evidence | Run affected exact environments only after authorization |
| Evidence exposes private data | Disposable identities, sanitization, secret scans | Privacy or secret finding | Quarantine and regenerate evidence | Affected logs, docs, security, and completion packet | Reproduce and rescan affected workflows |
| Issue 66 is accessed early | Freeze authoring evidence and prohibit live issue operations in this phase | Execution log or audit shows a live issue search, query, readback, or mutation | Stop phase and report the timing defect; do not create or modify an issue | Continuation issue timing and phase closure evidence | Repeat the local evidence and no-live-access audit; Phase 003 performs the only later remote readback |

## Phase Completion Packet

Before `CORE-PHASE-002` may close, the evidence store outside the protected plan set must contain:

1. Integrated phase commits and artifact identity, with sequential branch, pull-request checks, review, merge, default-branch verification, and required phase-tag evidence.
2. Fresh `EXT-001` through `EXT-006` records with every exact source, publisher, version, byte size, SHA-256, SHA-512, compatibility, license or terms, archive, dependency, security, and environment fact.
3. The exact Pixelmon `9.4.0` API map and capability descriptor showing verified query and precheck behavior and explicitly absent durable request identity, receipt lookup, idempotent retry, and outcome lookup.
4. Pixelmon adapter source and tests proving exact presence and version gating, lossless query values, typed failures, server-thread behavior, mutation refusal before all local and external effects, zero `add` and `take` calls, and no balance mirror.
5. The complete Pixelmon surface-refusal matrix, including public API, administration, server shops, carts, player shops, pay, deposit, withdrawal, claims, bills, fees, refunds, analytics, events, browsing, pure barter, shutdown, restart, reconnect, and incompatible or absent dependency behavior.
6. `EXT-002` runtime evidence permitted by `EXT-008` for standard NeoForge without Pixelmon, Pixelmon present with `internal`, exact Pixelmon query mode, incompatible and failure states, client, dedicated server, multiplayer, reconnect, clean restart, unclean restart, and exact installed hashes.
7. Exact bridge provenance and proof that the separately installed bridge alone registers `vault` through the public API, with no FutureShops special case or forbidden dependency.
8. A complete exact `vault` capability, conversion, lifecycle, request, provider receipt, local receipt audit under `world/data/futureshops/receipts`, idempotency, duplicate, journal, escrow, claim, shutdown, crash, recovery, frozen, and surface matrix against `EXT-004` through `EXT-006`.
9. `EXT-006` runtime evidence permitted by `EXT-008` with every component present and each component absent or failed in turn, including dedicated server, clients, multiplayer, reconnect, orderly restart, crash boundaries, recovery, and installed hashes.
10. Standard NeoForge client and dedicated-server logs proving the same FutureShops artifact starts with Pixelmon, bridge, Bukkit, Vault, hybrid runtime, and economy plugin absent.
11. Dependency reports, class and service listings, bytecode and reflection scans, jar contents, embedded-artifact comparison, provenance and license report, secret scan, generated-output inspection, and complete Git diff proving no external redistribution or forbidden linkage.
12. Focused tests, complete tests, applicable generated-data and GameTest results, complete build, runtime results, and a rerun ledger showing every invalidated result was regenerated from its earliest affected gate.
13. Updated integration and operator documentation plus a passed disposable rehearsal for installation, restart-only selection, capability diagnostics, safe Pixelmon refusal, exact `vault` recovery, backup, claim preservation, frozen uncertainty, rollback to `internal`, and no balance transfer.
14. A trace from every `CORE-REQ-017` and `CORE-REQ-018` acceptance criterion to passing evidence, with exact unsupported variants and all remaining non-goals explicit.
15. A local integrity and execution audit proving `EVD-GH-001` remained frozen and phases 000 through 002 performed no live issue search, query, readback, or mutation. Phase 003 later verifies remote state after artifact validation.
16. A Phase 003 handoff naming the source revision, FutureShops artifact hashes, external artifact hashes, environment manifests, evidence locations, known exact compatibility limits, open external gates, and first final-validation action.

Any failed required command, unresolved artifact or license concern, missing exact query proof, Pixelmon mutation side effect, unproven `vault` capability, unsafe retry, unresolved exact runtime gate, optional classloading failure, forbidden dependency, packaging contamination, generic compatibility claim, early issue mutation, or known mandatory integration defect blocks closure. Pixelmon's negative mutation-capability result closes its requirement only through proven safe refusal. It does not permit a direct production mutation claim. Fixture proof cannot replace exact runtime evidence, and lack of `EXT-008` leaves affected runtime gates open.

## Next Transition

After `CORE-PHASE-002` is integrated and its completion packet is accepted, reread the complete master and registered `CORE-PHASE-003` blueprint from the approved default branch. Begin Phase 003 by verifying that the integrated Pixelmon refusal evidence, exact `vault` capability and recovery evidence, source revision, FutureShops artifact hashes, external artifact hashes, and environment manifests remain reproducible.

Do not create or stack a Phase 003 branch while this phase is open, awaiting checks, queued for merge, missing integration evidence, or absent from the approved default branch. Any transition failure returns to its owning stable requirement and invalidates dependent evidence. Final documentation reconciliation, the unpublished candidate, publication-exclusion proof, plan-wide Definition of Done, and the post-artifact verification and update of the existing open issue 66 remain exclusively within `CORE-PHASE-003`.
