# Phase 000 Execution Plan

> **Plan ID:** PLAN-PHASE-000
> **Phase ID:** CORE-PHASE-000
> **Owner:** FutureShops repository
> **Classification:** MANDATORY
> **Master plan:** [plan.md](../plan.md)
> **Phase sequence:** 000 of 003

## Purpose and Ownership

This phase establishes the platform, public provider, capability, and restart-only selection contracts consumed by every later phase. It reconfirms the repository baseline, records exact external artifact evidence, applies the FutureShops `2.3.0` and NeoForge `21.1.248` pins, freezes API compatibility version `1`, implements deterministic registration and selection, and proves that unsupported external mutation is refused before transaction intent, custody, or provider mutation.

This phase canonically owns `CORE-REQ-001`, `CORE-REQ-002`, and `CORE-REQ-003`. The master owns product scope, decisions, prerequisites, global phase order, and release boundaries. This file owns only execution detail and evidence gates for `CORE-PHASE-000`. Under `DEC-017` and `DEC-018`, a negative direct Pixelmon capability result is an expected supported result: it becomes deterministic safe refusal, never an unsafe mutation path or an unqualified production-support claim.

## Evidence-Based Entry State

| Evidence class | Area | Finding | Source or command | Freshness condition |
| --- | --- | --- | --- | --- |
| OBSERVED | Repository baseline | FutureShops is `2.2.1`, Minecraft is `1.21.1`, NeoForge is `21.1.233`, Java is `21`, Gradle is `8.8`, ModDevGradle is `2.0.141`, and GeckoLib is `4.8.4` before the phase change. | `docs/verification/phase-000/baseline-2026-09-02.md` and repository metadata | Any build, dependency, wrapper, metadata, or source-set edit invalidates the comparison. |
| VERIFIED | Baseline build | Gradle task discovery, unit tests, and build passed. The baseline jar SHA-256 is `d1e2e61e8ec9cba4b34ae2e506381a7ae437ce912f3e448ef0b3138d5efa4b7e`. | Named commands and artifact evidence in the phase baseline | Any tracked source, resource, build, test, or dependency change invalidates the result. |
| OBSERVED | Economy ownership | `EconomyProvider`, `InternalEconomyProvider`, and `BalanceManager` form the current internal-only boundary. | CodeGraph and source evidence recorded in the phase baseline | Any economy, shop, command, packet, market, persistence, or money-item edit invalidates the call graph. |
| VERIFIED | Pixelmon artifact | Official file `Pixelmon-1.21.1-9.4.0-universal.jar`, id `8661427`, size `400154994`, SHA-256 `9020393f98382ae8794ef2694e7bec1984c1a0eca735ea3eea06e0cb151c61f2`, and SHA-512 `b1485031c27cbe0dd7125f11d3b003954e654f66c102479d443841071a37131067371bfc5e1fc2d8bf96a7195afa3ca02fc1525d343fc096d5bc598680bccafe` passed archive integrity. | Official file record, exact bytes outside the repository, hashes, and archive evidence in the phase baseline | Different bytes, file id, publisher, manifest, or archive contents invalidate all dependent conclusions. |
| VERIFIED | Pixelmon platform | The runtime declares Pixelmon `9.4.0`, Java `[21,)`, Minecraft `[1.21.1,1.21.2)`, NeoForge `[21.1.0,)`, and implementation `9.4.0-PIPE30861`. | Exact runtime metadata and manifest | A different exact artifact invalidates the finding. |
| VERIFIED | Pixelmon economy surface | Public runtime declarations and API docs expose UUID accounts, `BigDecimal` balance and precheck, boolean `add` and `take`, manager and proxy lookup, initialization access, and pre and post transaction events. | `javap`, full economy-package inventory, bytecode review, and official API pages recorded in the phase baseline | New exact bytes or an additional authoritative API contract requires re-review. |
| VERIFIED | Strict capability gap | No reviewed economy declaration accepts a request UUID or receipt key, and no reviewed economy class exposes durable receipt lookup, operation identity, idempotent retry, or outcome recovery. Direct mutation cannot satisfy `DEC-017`. | Exact bytecode and class inventory, `DEC-018`, and `EXT-003` | New exact evidence of an atomic durable mechanism invalidates the negative classification and requires the crash and replay matrix again. |
| OBSERVED | Development access and terms | The official download links to a public MDK for an older line, the exact file has no additional files, and the authenticated developer application is the remaining unverified route. Terms restrict distribution and compiled alteration. | Official downloads, file record, MDK, application, and terms evidence in the phase baseline | New official development bytes or terms invalidate the availability and permitted-use conclusions. |
| OBSERVED | Hybrid candidates | Candidate bridge and stack artifacts are inventoried, but the bridge targets older Pixelmon and NeoForge versions and the stack lacks complete exact-version, licensing, lifecycle, and runtime proof. | Candidate review and PRTS probe in the phase baseline | Any byte, tag, source, license, runtime, or configuration change invalidates the related review. |
| VERIFIED | Terms gate | A disposable probe reached `eula=false` and stopped. No acceptance, plugin enablement, Vault registration, mutation, recovery, or FutureShops behavior was claimed. | Sanitized PRTS probe evidence in the phase baseline | `EXT-008` and a new exact environment manifest are required before full launch evidence. |
| VERIFIED | Issue boundary | Issue 66 was created and read back during plan authoring. Phase 000 consumes only the frozen authoring record and performs no live issue query or mutation. | `EVD-GH-001`, `DEC-015`, and `EXT-007` | Any execution-side issue access or mutation before the Phase 003 post-artifact gate invalidates timing evidence. |

## Scope Boundaries

### Included Scope

- `CORE-REQ-001`: Update FutureShops to `2.3.0`, pin NeoForge exactly `21.1.248`, preserve Minecraft `1.21.1` and unrelated pins, and prove identical client and server bytes.
- `CORE-REQ-002`: Implement public API compatibility version `1`, deterministic registration, immutable verified capabilities, exact minor-unit values, typed readiness and outcomes, request identity, receipt and recovery contracts, and strict safe refusal without internal-package access.
- `CORE-REQ-003`: Implement one server-authoritative selector with `internal` as the absent-key default, one startup resolution, active and staged values, restart-only activation, and no fallback.
- `DEC-017`: Require verified balance, precheck, withdraw, deposit, durable receipt lookup, and idempotent retry capabilities as applicable to each surface. Missing capabilities reject the surface.
- `DEC-018` and `EXT-003`: Preserve exact Pixelmon query and precheck evidence and classify direct Pixelmon production mutation as unavailable unless durable identity, receipt lookup, and replay safety are later proven.
- `EXT-001` through `EXT-006`: Complete or accurately classify each artifact, feasibility, stack, and environment prerequisite with exact evidence. Unavailability remains visible and blocks dependent gates.
- `EXT-008`: Allow read-only download, hashing, archive inspection, environment assembly, and an `eula=false` stop probe. Full launch, mutation, restart, and recovery execution require explicit owner acceptance for the exact environment.
- Phase-owned API, configuration, compatibility, operator, provenance, verification, and migration documentation.

### Explicit Exclusions

- `CORE-REQ-004` through `CORE-REQ-016`: The economy gate, journal, custody, claims, draining, recovery, frozen operations, routing, backup, and recovery implementation belong to `CORE-PHASE-001`.
- `CORE-REQ-017` and `CORE-REQ-018`: The Pixelmon adapter and exact separate `vault` bridge interoperability belong to `CORE-PHASE-002`. Phase 000 implements generic capability refusal only.
- `CORE-REQ-019` through `CORE-REQ-021` and the Phase 003 portion of `CORE-REQ-022`: Final validation, terminal documentation, unpublished artifact, and issue update belong to `CORE-PHASE-003`.
- `FUT-001` through `FUT-004`: No 3.0.0 code, ATM, extra adapter, or future port work is implemented.
- `NG-001` through `NG-009`: No publication, balance migration, bundled external stack, hot switching, balance mirror, extra Pixelmon-version claim, unrelated upgrade, telemetry, or weakened save and custody safety.
- Owner acceptance of an EULA or external terms. No repository file, environment, command, or agent sets acceptance for the owner.

## Phase Contract

### CORE-PHASE-000 — Platform, Evidence, Public API, and Restart-Only Selection

**Objective:** Produce an integrated `2.3.0` and NeoForge `21.1.248` foundation whose public API verifies capabilities, whose selection freezes once per server lifecycle, and whose exact Pixelmon evidence causes unsupported production mutation to refuse safely.
**Owner:** FutureShops repository
**Dependencies:** DEC-001, DEC-002, DEC-003, DEC-004, DEC-005, DEC-006, DEC-007, DEC-009, DEC-013, DEC-017, DEC-018, EXT-001, EXT-002, EXT-003, EXT-004, EXT-005, EXT-006, EXT-008
**Canonical requirements:** CORE-REQ-001, CORE-REQ-002, CORE-REQ-003
**Documentation and release impact:** Update user, maintainer, API, configuration, integration, migration, and verification docs. Produce no release, tag, upload, or issue mutation.
**Next transition:** `CORE-PHASE-001` after integration and default-branch verification.

**Entry criteria**

- The master and every registered phase plan are accepted as one coherent contract.
- Repository identity, branch, starting revision, remote, dirty state, ignored evidence, and protected goal identity are recorded.
- Frozen authoring `EVD-GH-001` identifies issue 66 and its creation readback. This phase performs no live issue search, query, readback, or mutation.
- The platform, task, economy ownership, and build baseline is reproducible.
- Every external byte used for evidence is outside tracked content and has authoritative provenance and hashes.

**Implementation scope**

- Implement `CORE-REQ-001` with a minimal platform-pin diff and optional-class isolation.
- Implement `CORE-REQ-002` as provider-neutral strict API. It must express unsupported capabilities and ambiguous outcomes and must not imply that a local UUID makes an external boolean call idempotent.
- Implement `CORE-REQ-003` so selection is resolved once, frozen, observable, and changed only by restart.
- Convert the exact `EXT-003` negative result into generic capability tests and the direct-Pixelmon safe-refusal contract.
- EXT-001, EXT-002, EXT-003, EXT-004, EXT-005, EXT-006, and EXT-008 remain explicit blockers when unavailable. Do not substitute candidates or lower-fidelity evidence.

**Execution order**

1. `P000-TASK-001` reconfirms repository, toolchain, metadata, tasks, call graph, and issue preservation for CORE-REQ-001, CORE-REQ-002, and CORE-REQ-003.
2. `P000-TASK-002` completes exact Pixelmon runtime, development-access, provenance, terms, hash, archive, dependency, and security evidence for `EXT-001`.
3. `P000-TASK-003` locks the `EXT-003` capability matrix and safe-refusal conclusion from exact API and bytecode evidence.
4. `P000-TASK-004` reviews the exact separate bridge candidate for `EXT-004` without adding external platform code to FutureShops.
5. `P000-TASK-005` reviews and classifies the exact hybrid, Vault, economy-plugin, and support stack for `EXT-005`.
6. `P000-TASK-006` freezes the CORE-REQ-002 public API compatibility version `1`, including strict capabilities and unavailable and ambiguous outcomes.
7. `P000-TASK-007` implements deterministic NeoForge registration, capability verification, reserved identifiers, and registry freeze for CORE-REQ-002.
8. `P000-TASK-008` implements CORE-REQ-003 server config, startup resolution, active and staged values, restart-only changes, status, and no fallback.
9. `P000-TASK-009` applies the CORE-REQ-001 target `2.3.0` and NeoForge `21.1.248` and proves unrelated pins did not change.
10. `P000-TASK-010` builds independent fixtures and verifies CORE-REQ-001, CORE-REQ-002, and CORE-REQ-003 platform, API, capability, selection, failure, and isolation behavior.
11. `P000-TASK-011` assembles and fingerprints the Pixelmon environment. Full launch proceeds only after `EXT-008`; otherwise preserve `eula=false` and the blocker.
12. `P000-TASK-012` assembles and fingerprints the hybrid environment. Full launch proceeds only after `EXT-008`; otherwise preserve `eula=false` and the blocker.
13. `P000-TASK-013` reconciles CORE-REQ-001, CORE-REQ-002, and CORE-REQ-003 documentation and evidence, reruns invalidated checks, inspects the diff and jar, and assembles the completion packet.

Tasks `P000-TASK-002`, `P000-TASK-004`, and artifact discovery in `P000-TASK-005` may proceed in parallel after `P000-TASK-001`. `P000-TASK-003` depends on exact runtime and API evidence, not an unsafe mutation experiment. `P000-TASK-006` depends on the call graph and capability classification. Environment assembly may proceed after artifact review, but no full launch may cross `EXT-008`. No later phase compensates for an unresolved Phase 000 defect.

**Required evidence**

- Baseline and post-change manifests, economy call graph, dependency reports, build logs, API report, config schema, fixtures, standard client and server logs, and candidate contents and hashes.
- Exact Pixelmon identity, archive integrity, economy signatures, bytecode and class inventory, official sources and terms, development-artifact conclusion, and security review.
- A capability matrix proving which Pixelmon operations are query, precheck, or mutation and proving that missing receipt and retry capabilities refuse before intent, custody, or mutation.
- Exact bridge and hybrid provenance, compatibility, license, dependency, and security conclusions without generic claims.
- Environment manifests and installed hashes. Full runtime logs require `EXT-008`; until then the result is an explicit terms blocker and untouched `eula=false`.
- Final evidence that no external bytes, secrets, private data, caches, worlds, unrelated changes, publication metadata, or issue mutation entered the output.

**Exit criteria**

- `CORE-REQ-001`, `CORE-REQ-002`, and `CORE-REQ-003` pass all Phase 000 acceptance and deterministic proof.
- `EXT-001` through `EXT-006` have exact evidence and an explicit resolved, unavailable, incompatible, or blocked classification. Classification never weakens dependent runtime gates or the completion endpoint.
- `EXT-008` is recorded as owner-action-required for pending full runtime proof. No acceptance was inferred or written.
- Direct Pixelmon `9.4.0` is classified per `DEC-018`: read and precheck may be represented, production mutation refuses unless the complete strict capability set is proven, and no unsafe override exists.
- Independent fixtures integrate without internal packages. Duplicate, malformed, incompatible, reserved, late, throwing, capability-deficient, and ambiguous providers return deterministic typed outcomes.
- Missing or failed external selection keeps the server online, disables monetary readiness, and never falls back to `internal`.
- Reload changes only staged selection and restart status. It never changes the active provider.
- Same-byte standard client and server smoke checks pass without optional integration classes.
- No known mandatory phase-owned defect remains. External blockers remain explicit for downstream and plan-wide gates.

## Inputs and Upstream Contracts

| Input or contract | Provider | Required state | Validation | Failure behavior |
| --- | --- | --- | --- | --- |
| Plan set | Master and index | IDs, phases, `DEC-017`, `DEC-018`, and prerequisites agree | Read every registered file and trace ownership | Stop for master repair; do not edit around contradictions. |
| Repository baseline | Current branch and starting revision | Identity, pins, tasks, call graph, tests, and dirty state are known | Git, Gradle, metadata, CodeGraph, and source inspection | Record drift and keep implementation closed until reconciled. |
| Pixelmon runtime | `EXT-001` | Official exact `9.4.0` bytes match recorded hashes | Hash, archive, manifest, metadata, publisher | Reject changed or unofficial bytes and invalidate dependent evidence. |
| Development access and terms | `EXT-001`, `EXT-008` | Exact access and permitted operations are documented | Official sources and owner acceptance record | No substitution, redistribution, alteration, acceptance, or full launch. |
| Pixelmon feasibility | `EXT-003` | Capabilities reflect exact reviewed API and missing receipt and retry | API, bytecode, inventory, capability review | Mark mutation unsupported and require safe refusal. |
| Bridge and stack | `EXT-004`, `EXT-005` | Every component has exact provenance, hashes, compatibility, terms, and security disposition | Source, release, archive, dependency, license review | Keep `vault` blocked and standard NeoForge isolated. |
| Runtime authorization | `EXT-008` | Acceptance applies to the exact disposable environment | Owner record before changing `eula=false` | Stop at terms gate; read-only inspection may continue. |
| Issue record | `EVD-GH-001` | Frozen authoring evidence identifies issue 66 and its creation readback | Validate the local evidence record only; perform no live issue access | Preserve the record for Phase 003, which alone performs the post-artifact search, verification, update, and readback. |

## Outputs and Downstream Contracts

| Output or contract | Consumer | Guaranteed state | Compatibility or versioning | Evidence |
| --- | --- | --- | --- | --- |
| Platform foundation | Phases 001 through 003 | `2.3.0`, Minecraft `1.21.1`, NeoForge `21.1.248`, unrelated pins preserved | Same FutureShops jar bytes on client and server | Metadata, dependency report, build, smoke, jar hashes |
| Public provider API | Economy gate, adapters, separate bridge | Provider-neutral API version `1` without internal dependencies | Breaking change requires version change and full rerun | API report, fixture compile, dependency scan |
| Frozen registry | Lifecycle and selection | Deterministic validation, reserved IDs, verified capabilities, one freeze | Registration order cannot resolve conflicts | Permutation and lifecycle tests |
| Selection snapshot | Phase 001 | Active and staged identifiers are distinct and observable | Absent legacy key selects `internal`; change requires restart | Config, reload, restart, status tests |
| Pixelmon capability result | Phase 002 and release claims | Exact 9.4.0 query and precheck evidence; mutation lacks strict guarantees and refuses | Exact Pixelmon `9.4.0` only | Matrix, bytecode, negative fixtures, docs |
| External evidence | Phases 002 and 003 | Every byte and environment has exact identity and blocker status | Any byte or manifest change invalidates evidence | Hash manifests, reviews, terms status, logs |
| Completion packet | Phase 001 entry | Outputs and remaining blockers are reproducible | Bound to integrated revision and hashes | Packet and default-branch verification |

## Work Packages

| Task ID | Requirement IDs | Work | Inputs and dependencies | Outputs | Affected components or interfaces | Verification |
| --- | --- | --- | --- | --- | --- | --- |
| `P000-TASK-001` | `CORE-REQ-001`, `CORE-REQ-002`, `CORE-REQ-003` | Reconfirm identity, pins, tasks, source sets, config and lifecycle conventions, all economy callers and persistence owners, and frozen issue-evidence preservation. | Plan set and current repository | Baseline, command matrix, call graph, drift record, and no-live-issue-access record | Build metadata and existing economy, persistence, config, lifecycle, packet, command, shop, market, money boundaries, and authoring evidence | Metadata, Gradle tasks, baseline tests and build, CodeGraph, source inspection, and local `EVD-GH-001` integrity check |
| `P000-TASK-002` | `EXT-001`, `CORE-REQ-001` | Complete exact Pixelmon artifact and development-access intake without redistribution, including hashes, manifest, inventory, integrity, dependencies, terms, and security. | `P000-TASK-001`; official sources | Artifact evidence and explicit permitted-use and availability conclusions | External intake and evidence only | Hashes, archive, dependency, source, terms, and security review |
| `P000-TASK-003` | `EXT-003`, `CORE-REQ-002`, `DEC-017`, `DEC-018` | Freeze direct Pixelmon capabilities from exact API and bytecode. Separate query and precheck from mutation and prove missing or present receipt and retry guarantees. | `P000-TASK-002`; exact API docs and bytes | Matrix, crash-window analysis, adapter constraints, negative cases | Pixelmon economy boundary and public capability model | `javap`, class inventory, value analysis, signature search, deficient fixtures |
| `P000-TASK-004` | `EXT-004`, `CORE-REQ-002`, `DEC-013` | Review exact separate bridge identity, intended `vault` boundary, provenance, hashes, terms, dependencies, security, and compatibility gaps. | `P000-TASK-001`; authoritative bridge source | Bridge evidence, compatibility, no-bundling conclusion | External bridge and public API only | Hash, source, archive, dependency, terms, security, API scan |
| `P000-TASK-005` | `EXT-005`, `CORE-REQ-018` contributor | Review one exact hybrid, Vault, economy plugin, and support set and reject experimental or mismatched substitutes. | `P000-TASK-004`; authoritative sources | Stack manifest, compatibility and terms matrix, security blockers | External stack only | Hashes, archives, dependencies, licenses, compatibility, source review |
| `P000-TASK-006` | `CORE-REQ-002`, `EXT-003`, `DEC-017`, `DEC-018` | Freeze API version `1`, identifiers, metadata, readiness, values, capabilities, request IDs, outcomes, receipt lookup, recovery, threading, exceptions, and compatibility. | `P000-TASK-001`, `P000-TASK-003`, bridge evidence | Reviewed public API independent of internals | Public API types and docs | Surface review, fixture compile, exhaustive outcomes and capabilities, dependency scan |
| `P000-TASK-007` | `CORE-REQ-002`, `CORE-REQ-003` | Implement deterministic NeoForge registration, validate factories and capabilities, reserve IDs, reject invalid and late registration, and freeze once. | `P000-TASK-006`; lifecycle evidence | Frozen registry and diagnostics | Public registration, registry, internal registration, lifecycle | Permutations, reservation, malformed, compatibility, late, capability, concurrency tests |
| `P000-TASK-008` | `CORE-REQ-003`, `DEC-005`, `DEC-006`, `DEC-018` | Add server selection with `internal` default, one resolution, active and staged values, restart status, and no fallback. Capability-deficient selection remains selected but unavailable for unsupported surfaces. | `P000-TASK-007`; config conventions | Selection snapshot, state, diagnostics | Server config, resolution, reload, startup | Omitted, internal, external, unknown, malformed, missing, deficient, late, reload, restart tests |
| `P000-TASK-009` | `CORE-REQ-001`, `DEC-001`, `DEC-002` | Set `2.3.0` and NeoForge `21.1.248` while preserving all unrelated boundaries. | Baseline and compatibility evidence | Minimal metadata and dependency diff | Build, settings, dependencies, mod metadata found by task 001 | Metadata, resolution, unrelated-pin diff, compile, jar metadata |
| `P000-TASK-010` | `CORE-REQ-001`, `CORE-REQ-002`, `CORE-REQ-003` | Build current, incompatible, throwing, ambiguous, and capability-deficient fixtures; run deterministic checks; smoke standard NeoForge client and server. | Tasks 006 through 009 | Reports, same-byte candidate, logs, scans | API, registry, selection, build, client and server | Focused and full tests, data and GameTest disposition, build, smoke, hashes, scans |
| `P000-TASK-011` | `EXT-002`, `EXT-008`, `CORE-REQ-017` contributor | Assemble exact Pixelmon environment. Preserve `eula=false` without owner acceptance; after acceptance run only approved startup, safe query, refusal, restart, and recovery prerequisites. | Tasks 002, 003, 010; `EXT-008` for launch | Manifest, blocker or authorized logs, hashes, recreation | Disposable Pixelmon and public boundary | Hash, isolation, terms gate, exact startup, safe query and refusal after authorization |
| `P000-TASK-012` | `EXT-006`, `EXT-008`, `CORE-REQ-018` contributor | Assemble exact hybrid environment. Preserve `eula=false` without owner acceptance; after acceptance run approved registration, isolation, capability, restart, and recovery prerequisites. | Tasks 004, 005, 010; `EXT-008` for launch | Manifest, blocker or authorized logs, hashes, recreation | Disposable hybrid, bridge, public boundary | Hash, isolation, terms gate, registration and removal cases after authorization |
| `P000-TASK-013` | `CORE-REQ-001`, `CORE-REQ-002`, `CORE-REQ-003`, `EXT-001` through `EXT-006`, `EXT-008` | Reconcile docs and evidence, rerun invalidated gates, inspect diff and jar, assemble packet, and integrate after honest classification. | All prior tasks | Completion packet and Phase 001 handoff | User, maintainer, index, API, config, integration, verification docs and integration state | Docs and links, full rerun, secret and external-byte scan, jar, diff, integration verification |

Task failure preserves reviewed inputs and blocks dependent work. Repeating a task reuses its stable ID and marks stale evidence explicitly. A task never closes a predecessor by weakening strict capabilities or selecting a substitute artifact.

## Architecture and Implementation Boundaries

- Public API types may not depend on `BalanceManager`, internal implementations, Pixelmon, Bukkit, Vault, bridge, hybrid, or plugin classes.
- Capability verification is independent per operation. Balance and precheck never imply withdraw, deposit, receipt lookup, or idempotent retry.
- A FutureShops request UUID is insufficient unless the external effect durably binds it to lookup and safe replay.
- Registration is deterministic; duplicate, incompatible, or ambiguous candidates never resolve by load order.
- `internal` and `vault` are reserved. Phase 000 does not implement the Pixelmon adapter.
- Selection freezes after registration and before monetary readiness. Reload changes staged state only; failure never triggers fallback.
- Money values are checked integer minor units. Invalid metadata, overflow, fractional values, and lossy conversions reject before invocation.
- Provider calls are logical-server operations. Exceptions become typed failures and bounded sanitized logs, never zero, success, or crash.
- Phase 000 defines request, result, receipt, capability, lifecycle, and selection contracts but does not create journal, custody, claim, or clean-marker persistence.
- Optional integration types must not link during standard startup. Common initialization must not load client-only classes.
- An absent config key selects `internal`; no balance, price, or unresolved-request migration occurs.
- Evidence excludes external bytes, credentials, private player data, proprietary raw logs, worlds, and owner acceptance values.

## Failure, Recovery, and Edge Cases

| Scenario | Detection | Required behavior | Recovery or rollback | Regression proof |
| --- | --- | --- | --- | --- |
| Exact development artifact unavailable | Official file, MDK, and access-route review | Keep `EXT-001` blocked; no substitute or complete claim | Obtain exact authorized input and rerun | Source and hash checks |
| Pixelmon lacks receipt and retry | Capability matrix | Expose supported query only and reject mutations before intent or custody | Only new exact durable evidence reopens classification | Deficient fixture and direct-Pixelmon contract tests |
| Provider lies about capability | Probe fails or result is ambiguous | Reject readiness or return typed unavailable | Correct provider and restart | Lying, timeout, exception, ambiguity fixtures |
| Owner acceptance absent | `eula=false` or equivalent | Do not accept or fully launch; preserve manifest and blocker | Owner accepts exact terms; recreate clean environment | File-state and no-runtime-claim audit |
| Artifact changes | Digest or manifest mismatch | Discard dependent evidence and prevent use | Reacquire and repeat intake | SHA-256 and SHA-512 before use |
| Duplicate or reserved registration | Registry collision | Reject deterministically regardless of order | Remove conflict and restart | All order permutations |
| Late registration | Event after freeze | Reject current lifecycle | Correct installation and restart | Delayed-event tests |
| Reload changes selection | Staged differs from active | Retain active and report restart required | Restart resolves staged value | Reload then restart |
| External selection missing | Freeze cannot resolve provider | Server stays online, money unavailable, no fallback | Correct install or config and restart | Startup and status assertions |
| Invalid provider response | Validation or exception | Typed failure and one sanitized log, never zero or success | Correct provider and restart if required | Throwing and malformed fixtures |
| Invalid amount or metadata | Checked validation | Reject before provider call or state change | Correct input or metadata | Bounds, overflow, precision, locale, nonfinite tests |
| Optional class links in standard stack | Startup or scan | Block exit and restore isolation | Rebuild and rerun clean smoke | No-Pixelmon and no-Vault startup scans |
| Candidate bytes differ | Installed digest mismatch | Discard runtime result | Reinstall and recreate | Before and after hashes |
| Public API changes after review | Surface digest changes | Invalidate fixtures and downstream evidence | Repeat API review and affected integrations | API digest and fixture rerun |

## Verification Matrix

| Requirement or task | Static or unit | Integration | Real workflow or runtime | Negative and recovery | Evidence artifact |
| --- | --- | --- | --- | --- | --- |
| `P000-TASK-001` | Metadata, tasks, call graph, ownership | Trace persistence and config | Baseline tests and build | Drift and missing caller | Baseline manifest and call graph |
| `EXT-001` | Hash, archive, dependency, terms, security | Exact dev compile probe only with authorized artifact | Authoritative identity | Missing artifact remains blocked | Pixelmon evidence package |
| `EXT-003` | Signatures, inventory, values, capabilities, crash analysis | Capability-deficient fixture | Authorized safe query only | No receipt, retry, timeout, ambiguity | Capability matrix |
| `EXT-004`, `EXT-005` | Hashes, source, dependencies, terms, compatibility, security | Exact public API probe | Full lifecycle after `EXT-008` | Mismatch, missing components | Bridge and stack reviews |
| `CORE-REQ-001` | Versions, dependencies, client isolation | Compile applicable source sets | Same-byte client and server smoke | Old pin, unrelated upgrade, linkage, mismatch | Build, dependency, jar hashes |
| `CORE-REQ-002` | API, identifiers, metadata, capabilities, outcomes, threads | Independent fixtures | Registration and safe query on logical server | Duplicate, reserved, late, malformed, incompatible, lying, deficient, ambiguous | API and fixture reports |
| `CORE-REQ-003` | Config, default, validation, active and staged values | Registry resolution | First install, reload, shutdown, restart | Unknown, missing, late, deficient, duplicate, fallback | Config tests and server logs |
| `EXT-002` | Manifest and installed hashes | Safe query and refusal after `EXT-008` | Recreated Pixelmon environment | Wrong version, absence, missing capability, terms block | Manifest and logs or blocker |
| `EXT-006` | Manifest, hashes, isolation | Bridge registration after `EXT-008` | Recreated hybrid environment | Remove each external component | Manifest and logs or blocker |
| `P000-TASK-013` | Docs, links, API digest, schema, traceability | Rebuild documented fixtures | Operator restart and environment procedure | Secret, external byte, issue mutation, unrelated diff | Completion packet |

Fixtures use deterministic player IDs, request UUIDs, provider IDs, registration orders, metadata variants, capability combinations, amounts, and outcomes. Numeric cases include zero, one minor unit, negatives by domain, maximum accepted values, arithmetic overflow, precision boundaries, fractional and nonfinite external values where expressible, and locale independence. Capability cases cover every relevant combination of query, precheck, withdraw, deposit, receipt lookup, and idempotent retry. Config cases include absent legacy key, `internal`, fixture external, unknown, malformed, missing, late, reload, unchanged reload, and restart correction.

After the last implementation change, rerun task discovery, focused tests, all tests, applicable data validation, applicable GameTests, build, standard server smoke, standard client smoke, reload and restart, authorized exact environments, dependency and runtime classpath inspection, jar inspection, secret and external-byte scan, generated-output review, and complete diff inspection. A blocked runtime remains blocked; fixtures never substitute for it.

## Documentation, Operations, and Release

- Update user docs for `2.3.0`, Minecraft `1.21.1`, NeoForge `21.1.248`, same-jar installation, default provider, restart-only changes, no fallback, and strict capability refusal.
- Update maintainer docs and index for API ownership, registry freeze, selection lifecycle, threading, error containment, values, and phase boundaries.
- Document API version `1`, IDs, metadata, six capabilities, requests, outcomes, receipts, lookup, retry, ambiguity, threads, exceptions, and compatibility.
- Document the discovered config key, default `internal`, validation, active and staged values, status, restart, and no migration.
- Record exact Pixelmon provenance, hashes, bytecode, API, terms, development access, and safe refusal. Query evidence is not a mutation claim.
- Record exact bridge and hybrid candidate evidence and gaps without generic compatibility claims.
- Record environment manifests and terms status. Before `EXT-008`, document `eula=false` and no full runtime claim.
- Document missing, incompatible, deficient, failed, and late provider behavior and restart correction.
- Keep external bytes, credentials, account data, player data, raw proprietary logs, caches, and worlds untracked.
- Do not publish, tag, upload, announce, or mutate issue 66.

## Risks and Evidence Invalidation

| Risk | Prevention | Detection | Recovery | Evidence invalidated | Reverification |
| --- | --- | --- | --- | --- | --- |
| Dev artifact unavailable | Require exact official input | Public inventory and route status | Preserve blocker and obtain input | Compile and runtime | Repeat task 002 and dependents |
| Boolean mutation mistaken for idempotency | Separate capabilities and durable binding | No request key or receipt contract | Keep mutation unsupported | API, adapter, recovery claims | Repeat tasks 003 and 006 and fixtures |
| Local journal treated as external proof | Require provider-owned outcome evidence | Ambiguous crash result | Refuse or freeze contract | Transaction design | Repeat ambiguity review |
| Terms accepted without owner | Preserve `eula=false`; gate on `EXT-008` | Environment changes without owner record | Discard unauthorized result and recreate | Runtime evidence | Rerun after valid acceptance |
| Bridge or stack unsafe | Exact version, source, license, security review | Gap in version, hash, license, dependency | Keep stack external and blocked | Bridge and environment | Repeat tasks 004, 005, 012 |
| Caller omitted | Reconfirm CodeGraph blast radius | Later direct balance access found | Amend inventory in phase | API and Phase 001 handoff | Repeat task 001 and affected checks |
| API cannot express ambiguity | Exhaustive typed fixtures | Boolean, null, zero, or destructive retry appears | Restore strict contract | All API evidence | Repeat task 006 onward |
| Optional linkage | Isolated types and clean-stack scan | Startup links optional class | Restore isolation | Build, smoke, jar, environments | Repeat tasks 009 through 012 |
| Hot switch or fallback | Immutable active versus staged | Reload or failure changes active ID | Restore startup-only resolution | Selection evidence | Repeat config and restart matrix |
| External bytes change | Hash and manifest all inputs | Digest mismatch | Reacquire and recreate | Intake and runtime | Repeat all dependent runs |
| Unrelated dependency drift | Minimal diff and report comparison | Unexpected resolution change | Restore pins | Build and runtime | Repeat resolution and smoke |
| Protected data leaks | Sanitize and scan | Secret, private data, path, log, or external byte | Remove safely and rotate exposed secret | Affected packet and security review | Recollect and rescan |

## Phase Completion Packet

The packet, stored outside the protected plan set, contains:

- Integrated revision, branch history, pull request and check state, default-branch verification, and no release tag or publication.
- Baseline and post-change platform, dependency, metadata, task, and minimal-diff manifests.
- Complete economy caller, persistence, config, lifecycle, command, packet, shop, market, money-item, and test ownership record.
- Exact Pixelmon identity, hashes, archive, metadata, API and bytecode, class inventory, terms, access status, security, and capability conclusion.
- Exact bridge and hybrid identities, hashes, compatibility, provenance, terms, dependency, security, and blockers.
- API version `1` report and independent fixture results for current, malformed, duplicate, reserved, late, incompatible, throwing, lying, capability-deficient, and ambiguous providers.
- Config and runtime results for absent key, `internal`, fixture external, unknown, malformed, missing, late, staged reload, restart, and no fallback.
- Disposable environment manifests, hashes, isolation, and either owner-authorized logs or exact `EXT-008` blocker evidence with `eula=false`.
- Focused and full tests, data and GameTest disposition, build, dependency report, standard client and server logs, candidate hashes and contents, classpath, secret and external-byte scan, generated-output review, and diff inspection.
- Updated user, maintainer, index, API, config, integration, migration, security, and verification docs.
- Blocker ledger for `EXT-001` through `EXT-006` and `EXT-008`, affected gates, next action, and no downgraded guarantee.
- Local integrity proof that the authoring issue 66 record remained frozen and no live issue access, prohibited byte, data, output, publication, or tag entered the phase. Phase 003 owns the later remote-state confirmation.

## Next Transition

After `CORE-PHASE-000` is integrated according to repository policy, fetch and verify the resulting default-branch revision, reread the master and `phases/plan-phase-001.md` through EOF, and compare platform pins, API digest, registry, selection schema, capability matrix, prerequisite classifications, and packet with integrated bytes.

The exact next phase is `CORE-PHASE-001`. Its first unfinished task consumes the frozen API and economy call graph to implement the server-owned lifecycle and strict economy gate. Do not start Phase 001, route gameplay money, or stack a later branch before Phase 000 integration. Any changed platform pin, API surface, Pixelmon byte, stack byte, capability evidence, terms status, selection behavior, or failed standard startup returns execution to the corresponding Phase 000 task and invalidates dependent results.
