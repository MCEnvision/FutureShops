# FutureShops 2.3.0 External Economy Provider Plan

> **Plan ID:** PLAN-MASTER
> **Plan status:** VALIDATED WITH KNOWN EXTERNAL BLOCKER
> **Project state:** EXISTING
> **Planning subject:** FutureShops 2.3.0 external economy providers for Minecraft 1.21.1 NeoForge, with Pixelmon 9.4.0 support and a Vault bridge API
> **Plan profile:** software_product

## 1. Project Identity

```text
Project: FutureShops 2.3.0 external economy providers
Requested artifact: authoritative_plan
Repository root: /mnt/hermes/projects/FutureShops
Starting branch: envy/plan-2.3.0-external-economy
Starting commit: 1ccb0d9aa28bcafe59ffd902db48357a6898113d
Authoritative remote:
origin
https://github.com/MCEnvision/FutureShops.git
Remote ref: origin/envy/plan-2.3.0-external-economy
Remote commit: 1ccb0d9aa28bcafe59ffd902db48357a6898113d
```

This existing repository evolves the observed FutureShops 2.2.1 implementation into an unpublished, validated 2.3.0 candidate for Minecraft 1.21.1 and NeoForge 21.1.248. Client and server use one FutureShops jar. The four-phase topology is CORE-PHASE-000 through CORE-PHASE-003. The immutable goal remains outside plan authoring scope.

## 2. Planning Subject and Source Roles

The planning subject is the FutureShops 2.3.0 product change and its validation endpoint. Reference plans, repository audits, status evidence, and external documentation inform the contract but do not replace the authoritative plan.

| ID | Role | Subject | Source | Intended use |
| --- | --- | --- | --- | --- |
| SRC-001 | owner_request | FutureShops 2.3.0 external economy provider behavior and 3.0.0 follow-up issue | current owner request and approved defaults | binding scope, decisions, exclusions, compatibility target, and completion endpoint |
| SRC-002 | repository_evidence | FutureShops 2.2.1 NeoForge 1.21.1 implementation baseline | /mnt/hermes/projects/FutureShops at 5fb749b2e6dbc791c8c3984216877ab90b904ee9 | current versions, economy architecture, surfaces, packaging, tests, and branch identity |
| SRC-003 | reference | FutureShops 3.0.0 Forge 1.20.1 architecture and maintenance context | origin/1.20.1 plan.md and FutureShops3-0Plan.MD | reference wording and future issue scope only, never 2.3.0 implementation authority |
| SRC-004 | reference | official Pixelmon 9.4.0 release compatibility | https://pixelmonmod.com/downloads.php | Pixelmon 9.4.0, Minecraft 1.21.1, and NeoForge 21.1.248 compatibility evidence |
| SRC-005 | reference | NeoForge 1.21.1 lifecycle and inter-mod communication | https://docs.neoforged.net/docs/1.21.1/concepts/events/ | provider registration and startup lifecycle evidence |
| SRC-006 | reference | Vault API economy boundary | https://github.com/MilkBowl/VaultAPI and https://milkbowl.github.io/VaultAPI/overview-summary.html | separate bridge boundary and exact checked conversion evidence |

After validation, the master owns global product scope and each registered phase plan owns only its detailed execution. Repository and runtime evidence may correct current-state claims but cannot weaken the target contract. `docs/plan/goal.md` remains untouched.

## 3. Purpose and Intended Outcome

FutureShops 2.3.0 must expose one public, versioned NeoForge economy provider contract, retain the built-in internal provider as the restart-only default, bundle an exact optional Pixelmon 9.4.0 adapter, and allow a separately installed bridge to register vault without placing Bukkit or Vault code in FutureShops. Operators must receive fail-closed monetary behavior, exact checked values, durable recovery, accurate presentation, reproducible evidence, one inspected unpublished artifact, and the required open continuation issue.

## 4. Evidence-Based Current State

| Area | Evidence class | Finding | Evidence |
| --- | --- | --- | --- |
| Repository baseline | OBSERVED | Version metadata identifies FutureShops 2.2.1, Minecraft 1.21.1, and NeoForge 21.1.233 before the planned compatibility update. | Repository revision 5fb749b2e6dbc791c8c3984216877ab90b904ee9 and build metadata inspection. |
| Economy boundary | OBSERVED | EconomyProvider, InternalEconomyProvider, and BalanceManager form the current economy boundary whose callers and persistence must be traced. | Source inspection at the pinned starting commit. |
| External artifacts | UNKNOWN | Exact Pixelmon, bridge, hybrid stack, license, security, and disposable environment evidence remains unresolved. | EXT-001 through EXT-006 require recorded hashes, manifests, reviews, and runtime proof. |
| Tracking capability | VERIFIED | Repository issue capability was authorized for the two-stage continuation issue lifecycle, and plan authoring created and read back issue 66. | EXT-007, DEC-015, and EVD-GH-001 preserve the duplicate search, creation response, issue 66 URL, and readback. |
| Protected goal | OBSERVED | Any existing goal is immutable and supplies no authority to change product scope. | Protected goal path inspection. |

Unknown external evidence is a blocker, not permission to substitute artifacts, guess APIs, weaken transaction guarantees, or claim compatibility.

## 5. Product Contract and Profile Coverage

| Profile area | Status | Source | Contract location | Rationale |
| --- | --- | --- | --- | --- |
| inputs and outputs | covered | SRC-001 | Required behavior by surface | The plan defines provider configuration, APIs, commands, gameplay inputs, typed outcomes, UI states, artifacts, and tracking output. |
| component architecture | covered | SRC-002 | Architecture contract | The plan freezes orchestration, provider registry, internal provider, Pixelmon adapter, separate Vault bridge, and dependency direction. |
| state and persistence | covered | SRC-001 | State ownership and persistence | The plan separates provider balances from durable request, outcome, custody, claims, recovery, analytics, and internal state. |
| failure taxonomy | covered | SRC-001 | Failure semantics | The plan distinguishes missing, incompatible, failed, recovery-required, authorization, validation, and dependency failures. |
| versioning | covered | SRC-004 | Versioning and compatibility | The plan pins product, Minecraft, NeoForge, Pixelmon, public API, configuration, and persistence compatibility. |
| security | covered | SRC-001 | Security, privacy, and determinism | The plan covers untrusted inputs, permissions, class isolation, artifacts, secrets, logs, replay, and dependency risk. |
| test system | external_prerequisite | EXT-001 | Verification contract | Deterministic tests are planned, while exact Pixelmon and Vault environments are known external blockers for highest-fidelity proof. |
| release lifecycle | covered | SRC-001 | Documentation, operations, and release boundaries | The endpoint is one fully validated unpublished 2.3.0 artifact plus the actual 3.0.0 issue, with publication excluded. |
| generalization | covered | SRC-001 | Compatibility matrix | The plan distinguishes standard NeoForge, exact Pixelmon, exact reviewed hybrid stack, unsupported versions, and future adapters. |
| determinism | covered | SRC-001 | Transaction and determinism contract | Stable request identities, checked arithmetic, frozen selection, idempotent outcomes, and exact artifact evidence define repeatability. |

## 6. Mandatory Scope

- CORE-REQ-001 through CORE-REQ-003 define the product target, public provider API, and restart-only provider selection.
- CORE-REQ-004 through CORE-REQ-016 define fail-closed lifecycle, server authority, exact values, durable transactions, state ownership, every monetary surface, switching, bills, presentation, diagnostics, bounded cost, security, and recovery.
- CORE-REQ-017 and CORE-REQ-018 define the exact Pixelmon adapter and separate Vault bridge interoperability.
- CORE-REQ-019 through CORE-REQ-022 define complete validation, documentation, the unpublished artifact, and the two-stage continuation issue lifecycle.

Every declared CORE-REQ ID is mandatory. Stable identifiers must not be renumbered, reused, or silently removed.

## 7. Optional / Future Scope

All locked future scope is excluded from implementation and remains non-blocking for this plan except for creating and maintaining its required tracking issue.

| Future ID | Deferred subject | Boundary |
| --- | --- | --- |
| FUT-001 | Maintain the existing 3.0.0 beta on Forge 1.20.1 | Track in issue 66 created by plan authoring. Do not import that implementation into 2.3.0. |
| FUT-002 | Port 3.0.0 functionality to Minecraft 1.21.1 | Track in the same open issue 66 as a future effort. Product execution verifies and updates tracking but does not implement the port. |
| FUT-003 | ATM user interface and commands | Future release only. The 2.3.0 mutation policy must prevent a future ATM path from bypassing provider rules. |
| FUT-004 | Additional external economy adapters | No adapter beyond internal, Pixelmon 9.4.0, and separately validated vault interoperability is promised here. |

## 8. Non-Goals

| Non-goal ID | Excluded work |
| --- | --- |
| NG-001 | Publishing 2.3.0 to GitHub, CurseForge, Modrinth, or another distribution service. |
| NG-002 | Automatically copying, converting, reconciling, or merging balances between providers. |
| NG-003 | Shipping Bukkit, Vault, an economy plugin, a hybrid server, bridge code, or their APIs inside FutureShops. |
| NG-004 | Hot switching, late activation, automatic fallback, or provider priority selection. |
| NG-005 | Mirroring an external provider balance ledger inside FutureShops. |
| NG-006 | Supporting Pixelmon versions other than exactly 9.4.0 in this release. |
| NG-007 | Upgrading unrelated platform or dependency boundaries. |
| NG-008 | Adding telemetry, remote balance services, distributed market authority, or cross-server balance synchronization. |
| NG-009 | Weakening browsing, barter, custody, claims, or save compatibility for provider integration. |

## 9. Owner Decisions

### DEC-001 — Product and platform target

**Status:** RESOLVED
**Selected choice:** FutureShops 2.3.0 on Minecraft 1.21.1 and NeoForge 21.1.248, with unrelated boundaries preserved.
**Rationale:** Pixelmon 9.4.0 compatibility requires the exact NeoForge update while unrelated pins remain stable.
**Affected requirements:** CORE-REQ-001, CORE-REQ-017, CORE-REQ-019, CORE-REQ-021
**Supersedes:** none

### DEC-002 — Packaging

**Status:** RESOLVED
**Selected choice:** The same FutureShops jar is installed on client and server, with optional integrations isolated.
**Rationale:** One artifact must remain safe on dedicated servers and clients without optional dependencies.
**Affected requirements:** CORE-REQ-001, CORE-REQ-015, CORE-REQ-017, CORE-REQ-021
**Supersedes:** none

### DEC-003 — Authority

**Status:** RESOLVED
**Selected choice:** The logical server owns provider selection, readiness, authorization, transactions, custody, claims, and outcomes.
**Rationale:** Monetary state and permissions require one authoritative mutation boundary.
**Affected requirements:** CORE-REQ-002, CORE-REQ-005, CORE-REQ-009
**Supersedes:** none

### DEC-004 — Integration boundary

**Status:** RESOLVED
**Selected choice:** A public NeoForge provider API, bundled internal and optional Pixelmon adapter, and a separate bridge registering vault.
**Rationale:** The core API supports exact integrations without embedding unrelated platforms.
**Affected requirements:** CORE-REQ-002, CORE-REQ-003, CORE-REQ-017, CORE-REQ-018
**Supersedes:** none

### DEC-005 — Provider selection

**Status:** RESOLVED
**Selected choice:** Internal is default and selection is restart-only without hot activation or fallback.
**Rationale:** Frozen startup selection prevents inconsistent state ownership.
**Affected requirements:** CORE-REQ-002, CORE-REQ-003, CORE-REQ-010
**Supersedes:** none

### DEC-006 — External provider failure

**Status:** RESOLVED
**Selected choice:** Server remains online, monetary mutations fail closed, browsing and pure barter remain available.
**Rationale:** Dependency failure must not corrupt monetary or unrelated marketplace state.
**Affected requirements:** CORE-REQ-002, CORE-REQ-003, CORE-REQ-004, CORE-REQ-012
**Supersedes:** none

### DEC-007 — Currency representation

**Status:** RESOLVED
**Selected choice:** The selected provider owns them and every accepted conversion is exact checked integer minor units.
**Rationale:** Exact provider metadata and checked values prevent loss and overflow.
**Affected requirements:** CORE-REQ-002, CORE-REQ-006, CORE-REQ-007, CORE-REQ-017, CORE-REQ-018
**Supersedes:** none

### DEC-008 — Balance migration

**Status:** RESOLVED
**Selected choice:** No. Internal and external balances remain independent and internal starting balance is internal-only.
**Rationale:** Provider selection does not authorize transferring or inventing value.
**Affected requirements:** CORE-REQ-008, CORE-REQ-010
**Supersedes:** none

### DEC-009 — Multi-leg protection

**Status:** RESOLVED
**Selected choice:** Durable request and leg identities, idempotent outcomes, recovery, compensation, and no mirrored balance ledger.
**Rationale:** Every retry and restart must converge without duplicate value movement.
**Affected requirements:** CORE-REQ-002, CORE-REQ-007, CORE-REQ-017, CORE-REQ-018
**Supersedes:** none

### DEC-010 — Physical money

**Status:** RESOLVED
**Selected choice:** Keep registrations and saves, disable all value mutation, and allow redemption only after returning to internal.
**Rationale:** Existing inventories remain compatible without bypassing an external provider.
**Affected requirements:** CORE-REQ-011
**Supersedes:** none

### DEC-011 — ATM scope

**Status:** RESOLVED
**Selected choice:** No ATM UI or command is added.
**Rationale:** ATM functionality is excluded from 2.3.0.
**Affected requirements:** CORE-REQ-011, CORE-REQ-012
**Supersedes:** none

### DEC-012 — Pixelmon integration

**Status:** RESOLVED
**Selected choice:** A bundled optional adapter for exactly Pixelmon 9.4.0, without bundling Pixelmon.
**Rationale:** Compatibility claims remain limited to reviewed exact artifacts.
**Affected requirements:** CORE-REQ-017
**Supersedes:** none

### DEC-013 — Vault support

**Status:** RESOLVED
**Selected choice:** A separately installed reviewed bridge registers vault; FutureShops contains no Bukkit or Vault dependency or reflection.
**Rationale:** The bridge owns hybrid-platform dependencies and lifecycle adaptation.
**Affected requirements:** CORE-REQ-002, CORE-REQ-018
**Supersedes:** none

### DEC-014 — Persisted external data

**Status:** RESOLVED
**Selected choice:** Only transaction facts, requests, outcomes, custody, claims, and confirmed analytics, never a mirrored external balance ledger.
**Rationale:** The external provider remains the sole balance authority.
**Affected requirements:** CORE-REQ-007, CORE-REQ-008, CORE-REQ-014
**Supersedes:** none

### DEC-015 — Continuation issue timing

**Status:** RESOLVED
**Selected choice:** Create and read back the actual issue after plan validation with the existing 3.0 beta maintenance milestone and enhancement, forge, neoforge, and ready labels.
**Rationale:** Initial tracking is authoring output; CORE-PHASE-003 later verifies and updates the same open issue after product validation.
**Affected requirements:** CORE-REQ-022
**Supersedes:** none

### DEC-016 — Publication

**Status:** RESOLVED
**Selected choice:** No publication, release tag, mod platform upload, or public artifact is authorized.
**Rationale:** The selected endpoint is a validated unpublished candidate and open tracking issue.
**Affected requirements:** CORE-REQ-021, CORE-REQ-022
**Supersedes:** none

## 10. External Prerequisites

| ID | Prerequisite | Affected requirements | Availability | Authorization | Required external action |
| --- | --- | --- | --- | --- | --- |
| EXT-001 | Official Pixelmon 9.4.0 runtime and development artifacts | CORE-REQ-001, CORE-REQ-017, CORE-REQ-019, CORE-REQ-021 | unknown | not_required | Acquire exact official artifacts and record version, source, hashes, compatibility, license provenance, archive review, and security review. |
| EXT-002 | Disposable exact Pixelmon 9.4.0 integration environment | CORE-REQ-017, CORE-REQ-019, CORE-REQ-021 | unknown | not_required | Provision the exact isolated runtime and record its reproducible manifest and sanitized logs. |
| EXT-003 | Pixelmon economy API feasibility proof | CORE-REQ-002, CORE-REQ-007, CORE-REQ-017 | unknown | not_required | Prove exact values, lifecycle, persistence, durable outcomes, recovery, and duplicate prevention against the reviewed API. |
| EXT-004 | Separately installed Vault bridge artifact | CORE-REQ-002, CORE-REQ-018, CORE-REQ-019, CORE-REQ-021 | unknown | not_required | Acquire and review the exact bridge artifact, source, hashes, compatibility, license provenance, and security boundary. |
| EXT-005 | Exact reviewed hybrid runtime, Vault, and economy plugin stack | CORE-REQ-018, CORE-REQ-019, CORE-REQ-021 | unknown | not_required | Select and review every exact artifact and record versions, sources, hashes, compatibility, licenses, and security conclusions. |
| EXT-006 | Disposable exact Vault bridge integration environment | CORE-REQ-018, CORE-REQ-019, CORE-REQ-021 | unknown | not_required | Provision the isolated exact hybrid environment and record its reproducible manifest and sanitized logs. |
| EXT-007 | GitHub repository tracking capabilities | CORE-REQ-022 | available | authorized | Preserve the completed authoring search, creation, and readback for issue 66, then verify and update that same open issue in CORE-PHASE-003 only after artifact validation. |

### EXT-001 — Official Pixelmon 9.4.0 runtime and development artifacts

**Kind:** artifact. **Required evidence:** Exact version, authoritative source, SHA-256, SHA-512, compatibility, license or terms, archive and dependency review, and security review. Missing evidence blocks CORE-PHASE-000 exit and the completion endpoint.

### EXT-002 — Disposable exact Pixelmon 9.4.0 integration environment

**Kind:** environment. **Required evidence:** Reproducible Minecraft 1.21.1, NeoForge 21.1.248, Pixelmon 9.4.0, and FutureShops artifact manifest, isolation statement, procedures, results, and sanitized logs. Missing evidence blocks Pixelmon runtime proof and completion.

### EXT-003 — Pixelmon economy API feasibility proof

**Kind:** other. **Required evidence:** Verified API signatures, exact value behavior, lifecycle, persistence, durable receipt or outcome design, failure behavior, recovery, and duplicate prevention. An inability to satisfy DEC-007 or DEC-009 requires product contract revision and blocks implementation.

### EXT-004 — Separately installed Vault bridge artifact

**Kind:** artifact. **Required evidence:** Exact version, authoritative source, SHA-256, SHA-512, compatibility, license or provenance, source or binary security review, and registration contract. Missing evidence blocks Vault interoperability and completion.

### EXT-005 — Exact reviewed hybrid runtime, Vault, and economy plugin stack

**Kind:** group. **Required evidence:** Exact versions, authoritative sources, sizes, SHA-256, SHA-512, compatibility, licenses or provenance, archive and dependency review, and security conclusions for every artifact. Missing evidence blocks generic claims and completion.

### EXT-006 — Disposable exact Vault bridge integration environment

**Kind:** environment. **Required evidence:** Reproducible hybrid environment manifest, exact reviewed artifacts, exact FutureShops jar, procedures, results, sanitized logs, and production-data isolation. Missing evidence blocks Vault runtime proof and completion.

### EXT-007 — GitHub repository tracking capabilities

**Kind:** service. **Required evidence:** Authenticated EnVisione identity, repository identity, existing 3.0 beta maintenance milestone, required labels, duplicate search, creation result, issue 66 URL, and readback. Plan authoring created and read back issue 66 immediately after validation; CORE-PHASE-003 preserves that evidence without early GitHub access, then revalidates and updates issue 66 only after artifact validation without creating a replacement or closing it.

## 11. Architecture Contract and Ownership Boundaries

### Components and dependency direction

```text
client screens and presentation
            |
            v
server commands, packets, shops, carts, player shops, markets, public services
            |
            v
economy orchestration and durable transaction coordinator
            |
            v
public provider api and frozen provider registry
       |                    |                    |
       v                    v                    v
internal provider     pixelmon adapter     separate vault bridge
future shops data     bundled, optional    separately installed
```

All gameplay and public entry points depend on one economy orchestration boundary. They must not read or mutate `BalanceManager` directly once provider routing applies. The internal provider may own `BalanceManager`; an external provider may not use it as a shadow balance store. The bundled Pixelmon adapter depends on the public API and verified Pixelmon interfaces only. The separate bridge depends on the public API and its hybrid stack; FutureShops must not depend on bridge, Bukkit, Vault, or economy plugin classes.

### Provider registry and selection

The public API exposes a deterministic registration mechanism appropriate to NeoForge `1.21.1` lifecycle events. A provider registration includes a stable lowercase resource identifier, API compatibility version, factory or service boundary, currency metadata, lifecycle capability, query behavior, idempotent mutation behavior, and failure reporting.

`internal` is reserved for the built in provider. `vault` is reserved for the separately installed bridge contract. Duplicate identifiers, invalid identifiers, incompatible API versions, invalid metadata, registration outside the allowed window, or multiple ambiguous registrations must never resolve by load order. They produce a deterministic rejected registration and, when the rejected identifier is selected, an unavailable selected provider.

The registry freezes before monetary services become available. The configured identifier is resolved once for the server lifecycle. A late registration cannot activate until a clean restart. A config reload may report that a restart is required, but it cannot change the active provider.

### Trust boundaries

| Boundary | Trusted authority | Untrusted or fallible input | Required containment |
| --- | --- | --- | --- |
| Client to server | Logical server | Packet values, menu state, displayed balances, request identifiers supplied by clients | Recompute identity and permissions, validate bounds and current provider state, and generate or validate server owned request identity |
| Configuration | Validated server configuration snapshot | Provider identifier, prices, limits, and reload attempts | Validate before activation, retain last valid non selection settings, and require restart for provider changes |
| Provider API | FutureShops orchestration contract | Provider metadata, balances, outcomes, exceptions, latency, and readiness | Validate every response, use checked arithmetic, fail closed, and never treat an error as zero or success |
| Pixelmon | Verified adapter | Optional classes, version, API representation, lifecycle, persistence, and failure behavior | Isolate linkage, require exact `9.4.0`, reject incompatible or unavailable runtime, and preserve server startup |
| Hybrid and Vault | Separately installed bridge | Hybrid runtime, Vault services, plugin lifecycle, provider changes, and plugin exceptions | FutureShops sees only its provider API. Bridge failure makes `vault` unavailable without loading Bukkit or Vault classes in FutureShops |
| Persistence | Versioned FutureShops data | Interrupted writes, old records, unknown outcomes, provider changes, and corrupted metadata | Preserve data, block ambiguous mutations, recover by request ID, and require operator action when safe reconciliation is impossible |
| Build inputs | Pinned repository and reviewed artifacts | Downloaded jars, transitive dependencies, archives, licenses, and repositories | Verify provenance and hashes, inspect contents, avoid redistribution, and record exact tested inputs |

### Provider lifecycle state machine

The externally visible lifecycle is deterministic and server owned.

| State | Meaning | Monetary reads | Monetary mutations | Browsing | Pure barter | Transition rule |
| --- | --- | --- | --- | --- | --- | --- |
| `UNRESOLVED` | Registry or server context is not frozen | Unavailable, never zero | Rejected | Available when independent of provider values | Available | Startup only |
| `READY` | Selected provider passed compatibility, metadata, readiness, and recovery gates | Provider authoritative | Allowed through transaction coordinator | Available | Available | Reached only during startup resolution and recovery |
| `MISSING` | Selected identifier was not registered in time | Unavailable | Rejected | Available with clear status | Available | Requires restart after installation |
| `INCOMPATIBLE` | API, mod version, metadata, precision, or capability is incompatible | Unavailable | Rejected | Available with clear status | Available | Requires corrected installation and restart |
| `FAILED` | Provider threw, returned invalid data, failed readiness, or entered unsafe state | Unavailable unless a last confirmed historical value is explicitly labeled non authoritative | Rejected | Available with clear status | Available | No automatic internal fallback or hot recovery |
| `RECOVERY_REQUIRED` | One or more durable requests have an ambiguous or incomplete outcome | Only safe provider queries | Rejected except idempotent recovery and compensation operations | Available with clear status | Available | Exit only after deterministic reconciliation, normally on restart or operator recovery |
| `STOPPED` | Server lifecycle is stopping or unavailable | Unavailable | Rejected | Not applicable | Not applicable | Terminal for the current server lifecycle |

An external provider becoming available after `MISSING`, `INCOMPATIBLE`, or `FAILED` does not hot activate. A clean restart is required. Failure of one operation may transition the selected provider to `FAILED` or `RECOVERY_REQUIRED` according to whether safety can still be proven. The exact classification must be recorded and tested.

### Money and precision model

All authoritative money values are signed integer minor units. Transaction amounts, prices, fees, and item denominations must be nonnegative where their domain requires it. Arithmetic uses checked addition, subtraction, multiplication, aggregation, and conversion. Overflow, underflow, excessive precision, fractional minor units, non finite external values, locale dependent parsing, and lossy conversion are validation failures before any mutation or custody change.

Provider metadata owns the singular and plural currency display names and the number of decimal minor unit digits. Metadata is validated and frozen with provider selection. Screens, command output, analytics labels, and public snapshots use that metadata. A missing or invalid provider must be reported as unavailable, never formatted as a zero balance or silently displayed with internal currency metadata.

Existing configured integer prices retain their integer magnitude and are interpreted as minor units of the selected provider. FutureShops performs no exchange rate or precision migration. Documentation must require operators to review prices before changing providers.

### State ownership and persistence

| State | Owner with `internal` | Owner with external provider | Persistence rule |
| --- | --- | --- | --- |
| Player balance | FutureShops internal provider | Selected external provider | Never copy external balances into `BalanceManager` |
| Currency metadata | Internal provider metadata | Selected external provider metadata | Freeze for server lifecycle, record provider identity with transaction evidence |
| Root request and leg identifiers | FutureShops transaction coordinator | FutureShops transaction coordinator | Durable and stable across retry, restart, compensation, and claim delivery |
| Provider mutation outcome | Internal provider plus coordinator evidence | External provider receipt or query plus coordinator evidence | Persist request and outcome facts, not a balance ledger |
| Shop, listing, order, cart, and custody state | FutureShops | FutureShops | Bind every monetary transition to its request identity and confirmed result |
| Claims and offline proceeds | FutureShops delivery state, internal value through internal provider | FutureShops delivery state, external value through selected provider | Never discard. Failed delivery remains a durable claim or recoverable operation |
| Physical bills and money item data | FutureShops | FutureShops, registered but inactive | Preserve exact existing data and prevent external redemption or activation |
| Analytics | FutureShops confirmed event facts | FutureShops confirmed event facts | Record confirmed request outcomes and provider identity, never infer or mirror current external balances |

Persistent records introduced or changed by this release require an explicit schema version, stable serialized fields, defensive decoding, and migration tests. Unknown or newer data must not be silently discarded. Durable records bind to the originating provider identifier. A provider selection change never reassigns an unresolved request to a different provider.

### Transaction and determinism contract

Every monetary mutation has one server owned root request UUID. Each debit, credit, fee, refund, compensation, claim, and offline delivery leg has a deterministic child identity derived from the immutable root and leg role or an equivalently stable persisted UUID. Retries reuse the same identity. A logically new user action must never reuse a completed identity.

The provider contract must support idempotent mutation outcomes and durable outcome lookup, either directly or through an adapter owned receipt mechanism that can prove whether a mutation occurred. A timeout, process interruption, exception, or missing acknowledgement must never be guessed as success or failure. If the outcome cannot be proven, the transaction enters `RECOVERY_REQUIRED`, further monetary writes stop, and operator visible recovery evidence is produced.

Multi leg workflows must persist intent before the first external effect, persist every confirmed outcome, order custody and value movements to prevent loss, and use idempotent compensation for incomplete later legs. Recovery repeats only operations whose provider outcome contract makes repetition safe. Compensation has its own stable request identity and cannot run twice. A debit or credit must not be duplicated even across crash points, world save timing, reconnect, command retry, packet replay, or provider exception.

The transaction record may contain provider identifier, root and child request IDs, operation type, amount, participants, timestamps or monotonic sequence, status, provider receipt, error classification, recovery state, and compensation relationship. It must not contain a periodically synchronized or independently mutable copy of player balances.

### Failure semantics

* Missing, late, incompatible, and failing external providers leave the Minecraft server online.
* All money containing operations fail before any unrelated item custody or market mutation when readiness is absent.
* Pure barter, defined as a transaction with no monetary amount, fee, deposit, withdrawal, balance, or currency item leg, remains available.
* Browsing, search, historical transaction evidence, and non mutating market views remain available with an explicit provider status.
* A provider exception is contained at the API boundary and logged once with actionable context. It must not crash the server, leak private data, or be converted into success.
* Provider unavailable is distinct from insufficient funds, invalid amount, permission denied, duplicate request, completed request, and recovery required.
* Claims and custody remain accessible and durable while monetary mutations are disabled. Claim delivery that requires an unavailable external credit remains pending rather than discarded.
* Rollback means transaction aware recovery or compensation. It never means directly rewriting an external balance from a mirrored value.

### Versioning and compatibility

The provider API has an explicit compatibility version independent of the product version. Public identifiers, required metadata, request semantics, outcomes, and lifecycle states are documented contracts. A provider built for an unsupported API version is `INCOMPATIBLE`. This release does not promise compatibility with unverified provider implementations.

Pixelmon compatibility is exactly Pixelmon `9.4.0` on Minecraft `1.21.1` and NeoForge `21.1.248`. The adapter must not register for another Pixelmon version unless exact compatibility is proven in a future plan. Its classes must not link during ordinary startup when Pixelmon is absent.

Vault interoperability is limited to the exact separately reviewed bridge and hybrid stack. The bridge registers `vault` through the public API. FutureShops must run on standard NeoForge without Bukkit, Vault, the bridge, or any hybrid classes present.

### Security, privacy, and determinism

Provider identifiers, metadata, values, outcomes, receipts, configuration, packets, commands, item data, and saved records are untrusted until validated. Permissions and player identity are checked on the logical server. Request IDs arriving from an untrusted client cannot authorize replay or another player's transaction.

No credentials, access tokens, private raw player data, or proprietary logs may enter source, tests, documentation, artifacts, GitHub issues, or validation evidence. Artifact review records hashes and public provenance only. Logs use stable error categories, provider identifier, safe request identifier, and lifecycle state while omitting sensitive balance details when unnecessary.

Registration order, provider selection, request identity, checked arithmetic, state transitions, and recovery outcomes must be deterministic. Map iteration, mod load order, timing races, locale, client state, or provider discovery timing must not select a different provider or change transaction outcome.

## Required behavior by surface

| Surface | `internal` ready | External ready | External unavailable or unsafe |
| --- | --- | --- | --- |
| Public balance query API | Read authoritative internal balance | Read authoritative selected provider balance | Return typed unavailable state, never zero fallback |
| Public mutation API | Route through coordinator and internal provider | Route through coordinator and selected provider | Reject before mutation |
| Administrative balance query | Internal authoritative value | External authoritative value | Report unavailable with provider state |
| Administrative grant, set, remove, or equivalent | Idempotent internal mutation | Idempotent external mutation only if representable and supported by contract | Reject, never edit internal as fallback |
| Analytics and audit views | Confirmed internal request outcomes | Confirmed external request outcomes and provider identity | Preserve historical facts and label live provider unavailable, never show a mirrored current balance |
| Server shop purchase and sale | Internal value leg | External value leg | Reject any money containing trade before item movement |
| Cart and checkout | Internal atomic workflow | External durable multi leg workflow | Preserve cart, reject checkout before custody or value changes |
| Player shop purchase and proceeds | Internal debit and credit | External durable debit, credit, claim, and compensation | Browsing remains, money containing purchase rejected |
| Offline proceeds and claims | Durable internal credit or claim | Durable external credit receipt or pending claim | Keep claim pending and accessible |
| Player pay or transfer | Idempotent internal transfer | Idempotent external debit and credit workflow | Reject without partial debit |
| Deposit and withdrawal | Available under existing validated internal rules | Disabled | Disabled |
| Physical money item activation and redemption | Available under existing validated internal rules | Disabled, existing bills remain inert and retained | Disabled, existing bills remain inert and retained |
| Money item registration and save decoding | Registered | Registered | Registered |
| Fees, refunds, events, and rollback | Confirmed internal outcomes | Confirmed provider outcomes with idempotent compensation | No fabricated success event, preserve recoverable state |
| Lifecycle and reload | Start internal, keep restart only selection | Start selected external, keep restart only selection | Stay online and fail closed until a clean restart after correction |
| Browsing and search | Available | Available | Available with explicit unavailable status |
| Pure barter | Available | Available without invoking provider | Available without invoking provider |
| ATM user interface or command | Absent | Absent | Absent |

No direct call site, legacy API, command, event handler, packet handler, GUI action, analytics path, or rollback path may bypass these rules.

## 12. Requirements

### CORE-REQ-001 — platform and dependency baseline

**Behavior:** Reconfirm the observed `2.2.1` baseline, change the product target to `2.3.0`, and pin NeoForge `21.1.248` while preserving Minecraft `1.21.1` and every unrelated repository pinned boundary. Use the same jar on client and server.
**Owner:** `CORE-PHASE-000`
**Contributors:** `CORE-PHASE-002`, `CORE-PHASE-003`
**Dependencies:** DEC-001, DEC-002, EXT-001, EXT-003
**Lifecycle stage:** change
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- Runtime and metadata consistently identify `2.3.0`; NeoForge is exactly `21.1.248`; no unrelated upgrade appears; a standard dedicated server does not load client classes; the jar is identical for client and server installation.

**Required evidence**

- `EVD-REP-001`, dependency report, build logs, server and client smoke logs, final jar contents, and `EVD-ART-001`.

### CORE-REQ-002 — public economy provider API

**Behavior:** Define and publish within the mod jar a documented NeoForge provider API covering stable identifiers, compatibility version, deterministic registration, validated currency metadata, lifecycle readiness, authoritative balance queries, checked integer minor unit mutations, durable request IDs, idempotent outcomes, and outcome recovery. Reserve `internal` and `vault` semantics. Freeze the registry before monetary readiness.
**Owner:** `CORE-PHASE-000`
**Contributors:** `CORE-PHASE-001`, `CORE-PHASE-002`, `CORE-PHASE-003`
**Dependencies:** DEC-003, DEC-004, DEC-005, DEC-006, DEC-007, DEC-009, DEC-013, EXT-003
**Lifecycle stage:** change
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- A provider can integrate without accessing internal packages; duplicate, late, malformed, and incompatible registrations are deterministic; the contract can express unavailable and ambiguous outcomes without using zero or boolean success as a substitute; API documentation specifies thread, lifecycle, error, idempotency, and compatibility rules.

**Required evidence**

- Public API source and documentation, compatibility fixtures, registration tests, API surface report, and approved public API contract review.

### CORE-REQ-003 — provider selection and restart semantics

**Behavior:** Add one validated server controlled provider selection with default `internal`. Resolve it once at startup after registration and before monetary service readiness. A later config change is staged for restart and never hot activates, hot switches, or falls back.
**Owner:** `CORE-PHASE-000`
**Contributors:** `CORE-PHASE-001`, `CORE-PHASE-002`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-002, DEC-004, DEC-005, DEC-006
**Lifecycle stage:** change
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- Existing installations remain on `internal`; selection is server authoritative; unknown or late external selection leaves the server online and monetary writes disabled; reload cannot change the active provider; operator output names the selected identifier and restart requirement.

**Required evidence**

- Config tests, dedicated server logs, reload and restart scenarios, and user documentation.

### CORE-REQ-004 — fail closed provider lifecycle

**Behavior:** Implement the declared lifecycle states and enforce them at every query and mutation boundary. Resolve, validate, recover, enter ready, contain runtime failure, reject late activation, and stop cleanly. Never choose `internal` because a selected external provider failed.
**Owner:** `CORE-PHASE-001`
**Contributors:** `CORE-PHASE-002`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-002, CORE-REQ-003, DEC-006
**Lifecycle stage:** continuous
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- The server stays online; every monetary mutation is rejected outside `READY`; browsing and pure barter continue; unavailable reads are never zero; failures cannot partially mutate FutureShops state; recovery required is distinguishable from provider failed.

**Required evidence**

- Lifecycle state tests, fault injection logs, dedicated server smoke evidence, and multiplayer UI evidence.

### CORE-REQ-005 — server authority and route enforcement

**Behavior:** Route every money query and mutation through a logical server orchestration boundary. Validate player identity, permission, selected provider, readiness, request identity, amount, product state, and custody before invoking a provider. Client snapshots and controls are never authoritative.
**Owner:** `CORE-PHASE-001`
**Contributors:** `CORE-PHASE-002`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-002, CORE-REQ-003, CORE-REQ-004, DEC-003
**Lifecycle stage:** continuous
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- No client controlled value or readiness decision authorizes a mutation; permission and ownership checks occur server side; all bypass attempts produce deterministic rejection and no provider call.

**Required evidence**

- Packet and command tests, API tests, multiplayer traces, and source boundary inspection.

### CORE-REQ-006 — values, metadata, and checked arithmetic

**Behavior:** Make selected provider metadata authoritative for currency name and precision. Represent authoritative values as checked signed integer minor units and reject domain invalid amounts, overflow, underflow, fractional minor units, lossy conversions, invalid metadata, and unrepresentable external values before state mutation.
**Owner:** `CORE-PHASE-001`
**Contributors:** `CORE-PHASE-002`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-002, CORE-REQ-004, DEC-007
**Lifecycle stage:** continuous
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- All accepted conversions round trip exactly; every arithmetic overflow is rejected; displays and messages use selected metadata; unavailable provider state is not formatted as an internal currency; no authoritative floating point path remains.

**Required evidence**

- Boundary and property tests, provider adapter conversion tests, UI snapshots, and source scan for forbidden conversion paths.

### CORE-REQ-007 — durable idempotent transaction coordination

**Behavior:** Give every mutation a durable root request UUID and every leg or compensation a stable child identity. Persist intent, confirmed outcomes, recovery state, and compensation relationships. Require provider idempotency and durable outcome lookup. Recover after restart without duplicate debit or credit. Never use a mirrored balance ledger to resolve uncertainty.
**Owner:** `CORE-PHASE-001`
**Contributors:** `CORE-PHASE-002`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-002, CORE-REQ-004, CORE-REQ-006, DEC-009, DEC-014, EXT-003
**Lifecycle stage:** continuous
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- Each logical leg changes value at most once; completed retries return the same outcome; an ambiguous external result stops further monetary writes; compensation executes at most once; custody, claims, and value converge to a documented safe state; the persistent schema contains request facts but no external balance mirror.

**Required evidence**

- State transition tests, crash matrix, journal fixtures, provider receipt evidence, recovery logs, and persistence schema review.

### CORE-REQ-008 — authoritative state and analytics separation

**Behavior:** Keep internal balances owned by the internal provider and external balances owned only by the selected external provider. Persist FutureShops market, custody, claims, request, outcome, and confirmed analytics facts with provider identity. Remove or prevent direct external use of `BalanceManager`.
**Owner:** `CORE-PHASE-001`
**Contributors:** `CORE-PHASE-002`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-004, CORE-REQ-007, DEC-008, DEC-014
**Lifecycle stage:** continuous
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- No external balance is copied into an authoritative or periodically synchronized FutureShops field; analytics contains confirmed event facts only; unresolved records remain bound to their originating provider; internal balances remain dormant and unchanged while external is selected.

**Required evidence**

- Call graph, persistence fixtures, saved data inspection, analytics tests, and provider switch scenarios.

### CORE-REQ-009 — complete monetary surface routing

**Behavior:** Inventory and route all balance and mutation surfaces, including public APIs, administration, analytics, server shops, carts, player shops, offline proceeds, claims, pay, deposit, withdrawal, physical money items, fees, events, rollback, reload, startup, and shutdown. Any operation with a money leg uses provider readiness and transaction coordination.
**Owner:** `CORE-PHASE-001`
**Contributors:** `CORE-PHASE-002`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-004, CORE-REQ-005, CORE-REQ-006, CORE-REQ-007, CORE-REQ-008
**Lifecycle stage:** change
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- Every listed surface has verified behavior; no monetary mutation bypass remains; money containing flows fail before item or listing mutation when provider is unavailable; browsing and pure barter still work.

**Required evidence**

- Surface matrix, call graph, focused regression tests, architecture scan, server tests, and multiplayer tests.

### CORE-REQ-010 — provider switching and existing data

**Behavior:** Default existing and new installs to `internal`. Do not transfer balances. Preserve internal balances while external is selected, and expose them again only when a later restart selects `internal`. Interpret configured integer prices as the selected provider's minor units without conversion. Bind unresolved transactions to the provider that originated them. Apply internal starting balance only when the internal provider creates an eligible new balance.
**Owner:** `CORE-PHASE-001`
**Contributors:** `CORE-PHASE-002`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-003, CORE-REQ-007, CORE-REQ-008, DEC-008
**Lifecycle stage:** continuous
**Production verification:** nondestructive
**Release impact:** migration

**Acceptance criteria**

- No player balance is created, copied, converted, or overwritten by selection change; internal starting balance never seeds an external account; dormant internal data survives; unresolved external requests are not replayed against internal; operators receive price review and recovery guidance.

**Required evidence**

- Migration fixtures, world backup comparisons, provider switch logs, balance assertions, and migration documentation.

### CORE-REQ-011 — physical money and inactive mutations

**Behavior:** Preserve registration, decoding, inventory presence, and save compatibility for existing FutureShops bills and related data. When an external provider is selected, disable money activation, minting through gameplay surfaces, deposit, withdrawal, redemption, and any future ATM mutation. Reenable existing valid internal behavior only after a restart selects `internal`. Add no ATM interface or command.
**Owner:** `CORE-PHASE-001`
**Contributors:** `CORE-PHASE-002`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-003, CORE-REQ-004, CORE-REQ-009, CORE-REQ-010, DEC-010, DEC-011
**Lifecycle stage:** continuous
**Production verification:** nondestructive
**Release impact:** migration

**Acceptance criteria**

- No item is deleted or converted when external is selected; no external balance changes through a money item; existing bills redeem only under ready internal; save decoding remains compatible; no ATM UI or command exists.

**Required evidence**

- Item and migration tests, registry inspection, client and server smoke evidence, and source scan.

### CORE-REQ-012 — player and operator presentation

**Behavior:** Present selected currency metadata and provider lifecycle accurately in screens, command output, tooltips, and public snapshots. Disable money action controls when unsafe while keeping browsing and pure barter accessible. Provide clear, localized, accessible distinctions among unavailable provider, insufficient funds, invalid amount, permission denial, duplicate completion, and recovery required.
**Owner:** `CORE-PHASE-001`
**Contributors:** `CORE-PHASE-002`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-003, CORE-REQ-004, CORE-REQ-006, CORE-REQ-009
**Lifecycle stage:** change
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- Users cannot mistake unavailable for zero funds; disabled actions explain the server state; display precision matches provider metadata; stale client state cannot authorize actions; pure barter remains actionable; no client class loads on a dedicated server.

**Required evidence**

- Localization checks, UI screenshots or recordings, client logs, multiplayer scenarios, and dedicated server smoke evidence.

### CORE-REQ-013 — diagnostics and support evidence

**Behavior:** Emit concise structured diagnostics for provider selection, compatibility, readiness, state transitions, rejected mutation categories, ambiguous outcomes, recovery, and compensation. Provide operator visible status without exposing secrets or unnecessary private balance data. Avoid repeated hot path log spam.
**Owner:** `CORE-PHASE-001`
**Contributors:** `CORE-PHASE-002`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-004, CORE-REQ-007
**Lifecycle stage:** continuous
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- An operator can identify selected provider, state, safe next action, and correlated request without reading private data; repeated rejected ticks or screen refreshes do not spam logs; exceptions retain actionable context.

**Required evidence**

- Sanitized log set, privacy review, failure injection results, and troubleshooting documentation.

### CORE-REQ-014 — bounded integration cost

**Behavior:** Keep provider lookup outside hot paths, use the frozen selected service, avoid filesystem or network access during gameplay mutation, avoid full balance scans and balance shadow synchronization, and require bounded provider calls on the server execution model.
**Owner:** `CORE-PHASE-001`
**Contributors:** `CORE-PHASE-002`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-002, CORE-REQ-004, CORE-REQ-009, DEC-014
**Lifecycle stage:** continuous
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- No per tick provider discovery, disk scan, registry scan, or external balance synchronization exists; ordinary requests do work proportional to their transaction legs; an unavailable provider does not create a retry storm.

**Required evidence**

- Call path review, performance observations, log volume evidence, and source scan.

### CORE-REQ-015 — provider and artifact security

**Behavior:** Validate all API, packet, command, config, metadata, value, receipt, and persistence inputs. Contain provider exceptions. Preserve server permissions and identity. Review every external artifact for provenance, hashes, license, archive contents, dependency risks, and known security concerns before use. Keep optional implementation classes isolated.
**Owner:** `CORE-PHASE-001`
**Contributors:** `CORE-PHASE-002`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-002, CORE-REQ-004, CORE-REQ-005, CORE-REQ-007, EXT-001, EXT-004, EXT-005
**Lifecycle stage:** continuous
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- No untrusted value bypasses validation; no Bukkit or Vault dependency or reflection exists in FutureShops; Pixelmon absence cannot cause class loading failure; no credential or private raw log enters artifacts or evidence; unresolved artifact risk blocks the related integration.

**Required evidence**

- `EVD-EXT-002`, `EVD-EXT-005`, `EVD-EXT-006`, security review records, dependency reports, negative tests, and jar inspection.

### CORE-REQ-016 — operational recovery and rollback

**Behavior:** Recover incomplete requests by stable identity and originating provider. Keep claims and custody durable. Block unsafe mutations until ambiguity is resolved. Provide backup, restore, provider correction, selection rollback, and reconciliation procedures that preserve all data and do not rewrite external balances from guesses.
**Owner:** `CORE-PHASE-001`
**Contributors:** `CORE-PHASE-002`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-004, CORE-REQ-007, CORE-REQ-008, CORE-REQ-010, CORE-REQ-013
**Lifecycle stage:** continuous
**Production verification:** nondestructive
**Release impact:** migration

**Acceptance criteria**

- Restart neither duplicates value nor discards items, claims, or bills; recovery has a deterministic outcome or a clear safe blocker; selection rollback preserves both providers' independent balances; documentation prohibits deleting journals or balance data as a fix.

**Required evidence**

- Crash and restore matrix, backup hashes, recovery logs, and operator runbook validation.

### CORE-REQ-017 — bundled optional Pixelmon adapter

**Behavior:** Bundle adapter code for exactly Pixelmon `9.4.0`, compiled and tested against the reviewed development artifact. Register its provider only when exact compatibility, metadata, lifecycle, persistence, value conversion, and idempotent outcome requirements are satisfied. Do not bundle Pixelmon. Keep all optional references isolated from ordinary startup.
**Owner:** `CORE-PHASE-002`
**Contributors:** `CORE-PHASE-000`, `CORE-PHASE-001`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-001, CORE-REQ-002, CORE-REQ-004, CORE-REQ-006, CORE-REQ-007, CORE-REQ-009, CORE-REQ-015, CORE-REQ-016, EXT-001, EXT-002, EXT-003
**Lifecycle stage:** change
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- Standard NeoForge starts without Pixelmon; exact Pixelmon starts on NeoForge `21.1.248`; provider metadata and balances are authoritative and exact; every mutation is idempotent and recoverable; unsupported Pixelmon versions do not register; no Pixelmon artifact is packaged.

**Required evidence**

- `EVD-EXT-001` through `EVD-EXT-004`, adapter tests, environment manifest, runtime logs, transaction recovery matrix, and jar contents.

### CORE-REQ-018 — separate Vault bridge interoperability

**Behavior:** Support a separately installed and reviewed hybrid bridge that registers provider identifier `vault` through the public API. Validate it against one exact hybrid runtime, Vault artifact, and economy plugin stack. Keep all bridge, Bukkit, Vault, hybrid, and plugin code and dependencies outside FutureShops.
**Owner:** `CORE-PHASE-002`
**Contributors:** `CORE-PHASE-000`, `CORE-PHASE-001`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-002, CORE-REQ-004, CORE-REQ-007, CORE-REQ-009, CORE-REQ-015, CORE-REQ-016, EXT-004, EXT-005, EXT-006
**Lifecycle stage:** change
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- `vault` resolves only through the separate bridge; missing or failed stack leaves the server online and FutureShops fail closed; no Bukkit or Vault class, reflection string, dependency, service lookup, or bundled bridge appears in FutureShops; exact stack limitations are documented without claiming generic compatibility.

**Required evidence**

- `EVD-EXT-005` through `EVD-EXT-007`, integration logs, dependency and jar scans, bridge registration tests, and environment manifest.

### CORE-REQ-019 — complete production validation

**Behavior:** Execute the complete deterministic and runtime matrix after all implementation and phase-owned documentation are integrated. Resolve failures through the owning phase requirement, rerun invalidated checks, and preserve sanitized evidence tied to the source commit and exact external artifacts.
**Owner:** `CORE-PHASE-003`
**Contributors:** `CORE-PHASE-000`, `CORE-PHASE-001`, `CORE-PHASE-002`
**Dependencies:** CORE-REQ-001, CORE-REQ-002, CORE-REQ-003, CORE-REQ-004, CORE-REQ-005, CORE-REQ-006, CORE-REQ-007, CORE-REQ-008, CORE-REQ-009, CORE-REQ-010, CORE-REQ-011, CORE-REQ-012, CORE-REQ-013, CORE-REQ-014, CORE-REQ-015, CORE-REQ-016, CORE-REQ-017, CORE-REQ-018, EXT-001, EXT-002, EXT-003, EXT-004, EXT-005, EXT-006
**Lifecycle stage:** post_change
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- Every mandatory requirement has passing evidence; no required command fails; external environments use exact reviewed artifacts; no unverified behavior is described as working; results are reproducible from the recorded commit and manifests.

**Required evidence**

- `EVD-VER-001`, `EVD-VER-002`, requirement trace matrix, command logs, runtime manifests, and final diff report.

### CORE-REQ-020 — user, API, maintainer, and operator documentation

**Behavior:** Update the root user documentation, maintainer documentation, documentation index, provider API reference, configuration guide, integration guides, migration guide, recovery runbook, compatibility matrix, and validation record to match implemented behavior only.
**Owner:** `CORE-PHASE-003`
**Contributors:** `CORE-PHASE-000`, `CORE-PHASE-001`, `CORE-PHASE-002`
**Dependencies:** CORE-REQ-001, CORE-REQ-002, CORE-REQ-003, CORE-REQ-004, CORE-REQ-005, CORE-REQ-006, CORE-REQ-007, CORE-REQ-008, CORE-REQ-009, CORE-REQ-010, CORE-REQ-011, CORE-REQ-012, CORE-REQ-013, CORE-REQ-014, CORE-REQ-015, CORE-REQ-016, CORE-REQ-017, CORE-REQ-018, CORE-REQ-019
**Lifecycle stage:** post_change
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- A user can select `internal`, Pixelmon, or the separately installed `vault` provider correctly; an integrator can implement the public API; an operator can diagnose unavailable and recovery states without data deletion; documentation clearly states no migration, restart only selection, external money item behavior, exact compatibility, and no ATM or publication.

**Required evidence**

- Documentation diff, link and example checks, runbook rehearsal, and final behavior cross check.

### CORE-REQ-021 — validated unpublished artifact

**Behavior:** Build one release candidate jar from the verified source commit, inspect its metadata and contents, calculate SHA 256 and SHA 512, associate all validation evidence, and keep it unpublished.
**Owner:** `CORE-PHASE-003`
**Contributors:** `CORE-PHASE-000`, `CORE-PHASE-001`, `CORE-PHASE-002`
**Dependencies:** CORE-REQ-019, CORE-REQ-020, DEC-016
**Lifecycle stage:** post_change
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- One artifact identifies FutureShops `2.3.0`, targets the locked platform, contains the public API and bundled optional Pixelmon adapter, excludes Pixelmon, Bukkit, Vault, and the bridge artifacts, passes every required environment, and is not published or tagged as a release.

**Required evidence**

- `EVD-ART-001`, artifact hashes, contents listing, environment manifests, and validation summary.

### CORE-REQ-022 — actual `3.0.0` continuation issue 66

**Behavior:** Plan authoring searched for duplicates, created, and read back open GitHub issue 66 immediately after the integrated plan set passed validation and before the authoring pass returned. Issue 66 covers maintenance of the existing `3.0.0` Forge `1.20.1` beta and a future Minecraft `1.21.1` port, uses the existing `3.0` beta maintenance milestone and labels `enhancement`, `forge`, `neoforge`, and `ready`, and links reference context without making it `2.3.0` implementation scope. Phases 000 through 002 preserve issue 66 unchanged and open. CORE-PHASE-003 records the authoring evidence without early GitHub access; only after `CORE-REQ-019` and `CORE-REQ-021` pass does it search again, verify, update, and read back the same issue 66, which remains open until future owner acceptance. It never creates a replacement or duplicate issue.
**Owner:** `CORE-PHASE-003`
**Contributors:** `CORE-PHASE-000`, `CORE-PHASE-001`, `CORE-PHASE-002`
**Dependencies:** CORE-REQ-019, CORE-REQ-021, DEC-015, DEC-016, EXT-007
**Lifecycle stage:** post_change
**Production verification:** nondestructive
**Release impact:** none

**Acceptance criteria**

- Issue 66 URL and number exist from this plan authoring pass; issue 66 was created and read back immediately after plan validation and before authoring returned; no duplicate was created; both maintenance and port subjects are explicit; the required existing milestone and every required label are attached; phases 000 through 002 keep issue 66 unchanged and open; `CORE-PHASE-003` performs no early GitHub access and updates and reads back issue 66 only after artifact validation; issue 66 remains open until future owner acceptance.

**Required evidence**

- `EVD-GH-001` with the authoring duplicate search, issue 66 creation URL and readback, preservation evidence for phases 000 through 002, and the post-artifact Phase 003 duplicate search, update, readback, exact open state, milestone, labels, and links.

## Requirement ownership freeze

Each mandatory requirement has exactly one implementation owner. Later phases may verify an earlier requirement but may not silently absorb or redefine its scope.

| Requirement | Owning phase |
| --- | --- |
| `CORE-REQ-001` | `CORE-PHASE-000` |
| `CORE-REQ-002` | `CORE-PHASE-000` |
| `CORE-REQ-003` | `CORE-PHASE-000` |
| `CORE-REQ-004` | `CORE-PHASE-001` |
| `CORE-REQ-005` | `CORE-PHASE-001` |
| `CORE-REQ-006` | `CORE-PHASE-001` |
| `CORE-REQ-007` | `CORE-PHASE-001` |
| `CORE-REQ-008` | `CORE-PHASE-001` |
| `CORE-REQ-009` | `CORE-PHASE-001` |
| `CORE-REQ-010` | `CORE-PHASE-001` |
| `CORE-REQ-011` | `CORE-PHASE-001` |
| `CORE-REQ-012` | `CORE-PHASE-001` |
| `CORE-REQ-013` | `CORE-PHASE-001` |
| `CORE-REQ-014` | `CORE-PHASE-001` |
| `CORE-REQ-015` | `CORE-PHASE-001` |
| `CORE-REQ-016` | `CORE-PHASE-001` |
| `CORE-REQ-017` | `CORE-PHASE-002` |
| `CORE-REQ-018` | `CORE-PHASE-002` |
| `CORE-REQ-019` | `CORE-PHASE-003` |
| `CORE-REQ-020` | `CORE-PHASE-003` |
| `CORE-REQ-021` | `CORE-PHASE-003` |
| `CORE-REQ-022` | `CORE-PHASE-003` |

## 13. Phased Roadmap

Phases are sequential. A phase starts from the approved result of its predecessor. Detailed dependency ordered tasks, test cases, file scopes, and rollback steps belong in the linked phase file, not in this master.

| Phase ID | Future execution blueprint | Outcome | Owned requirements | Entry gate | Exit gate |
| --- | --- | --- | --- | --- | --- |
| `CORE-PHASE-000` | [`phases/plan-phase-000.md`](phases/plan-phase-000.md) | Verified external prerequisites, pinned platform, frozen public API, and restart only selection contract | `CORE-REQ-001`, `CORE-REQ-002`, `CORE-REQ-003` | Master and complete registered plan set accepted, repository baseline reconfirmed | `EXT-001` through `EXT-006` are proven; the repository baseline and public API contract are frozen; platform and API acceptance pass; unresolved artifact, environment, or feasibility evidence blocks exit |
| `CORE-PHASE-001` | [`phases/plan-phase-001.md`](phases/plan-phase-001.md) | Complete server authoritative provider routing, fail closed lifecycle, durable transactions, surface coverage, migration safety, presentation, and recovery | `CORE-REQ-004`, `CORE-REQ-005`, `CORE-REQ-006`, `CORE-REQ-007`, `CORE-REQ-008`, `CORE-REQ-009`, `CORE-REQ-010`, `CORE-REQ-011`, `CORE-REQ-012`, `CORE-REQ-013`, `CORE-REQ-014`, `CORE-REQ-015`, `CORE-REQ-016` | `CORE-PHASE-000` integrated and its public contract stable | Every owned requirement passes focused deterministic, persistence, failure, server, client, and multiplayer gates with internal and fixture external providers |
| `CORE-PHASE-002` | [`phases/plan-phase-002.md`](phases/plan-phase-002.md) | Bundled optional Pixelmon `9.4.0` adapter and verified separate `vault` bridge interoperability | `CORE-REQ-017`, `CORE-REQ-018` | `CORE-PHASE-001` integrated; exact reviewed artifacts and disposable environments remain reproducible | Both exact external stacks pass the complete surface, lifecycle, idempotency, restart, recovery, isolation, and packaging matrices; standard NeoForge remains clean |
| `CORE-PHASE-003` | [`phases/plan-phase-003.md`](phases/plan-phase-003.md) | Final production validation, accurate documentation, validated unpublished artifact, and post-artifact verification and update of open issue 66 | `CORE-REQ-019`, `CORE-REQ-020`, `CORE-REQ-021`, `CORE-REQ-022` | `CORE-PHASE-002` integrated with complete external evidence and issue 66 identified by the authoring `EVD-GH-001` record | Plan wide definition of done passes, exact artifact remains unpublished, and issue 66 is updated only after artifact validation, verified by URL and readback, and remains open |

No future phase may start early. A failure in an earlier owned requirement returns work to that requirement's phase scope and invalidates affected downstream evidence.

## 14. Verification Contract and Strategy

### Verification order

1. Reconfirm repository commands and the absence or presence of formatting and static analysis tasks.
2. Run focused unit and regression tests for the changed requirement.
3. Run all unit tests.
4. Run generated data validation when providers or resources affect generated content.
5. Run applicable GameTests for world, inventory, persistence, shop, custody, claims, or multiplayer behavior.
6. Run the complete build using the checked in wrapper.
7. Run dedicated server smoke tests on standard NeoForge.
8. Run client smoke tests for screens, localization, currency formatting, disabled actions, and optional mod isolation.
9. Run multiplayer, reconnect, stale snapshot, delayed readiness, retry, and replay scenarios.
10. Run restart and crash point recovery matrices with internal, fixture external, Pixelmon, and `vault` providers.
11. Run the exact Pixelmon environment and exact hybrid bridge environment.
12. Inspect dependency graphs, runtime classpaths, the final jar, generated output, secrets, debug output, and the complete diff.
13. Reinstall the exact hashed candidate in every production validation environment.
14. Only after all prior product gates pass, search again, update, and read back authoring-created GitHub issue 66, and keep it open without creating a replacement or duplicate.

### Required test matrices

#### Provider matrix

| Provider case | Required states |
| --- | --- |
| Built in `internal` | New world, upgraded world, new player, existing player, restart, recovery, insufficient funds, numeric boundary |
| Fixture external | Ready, missing, late, incompatible API, invalid metadata, thrown query, thrown mutation, ambiguous outcome, duplicate request, recovery required |
| Pixelmon `9.4.0` | Absent, exact compatible, incompatible version, startup failure, runtime failure, exact conversion, duplicate and crash recovery |
| `vault` bridge | Absent bridge, absent Vault, absent economy plugin, exact complete stack, provider service loss, plugin failure, restart, duplicate and crash recovery |

#### Surface matrix

Every row in Required behavior by surface is tested under `internal` ready, external ready, external unavailable, and recovery required where meaningful. Tests prove both the expected result and the absence of forbidden side effects.

#### Crash and idempotency matrix

Each multi leg flow is interrupted before intent persistence, after intent persistence, before each provider call, after provider effect but before local outcome persistence, after each confirmed leg, before and after custody movement, before and after claim creation, before and after compensation, and during shutdown. Restart and retry must produce one confirmed debit or credit per logical leg, or a safe `RECOVERY_REQUIRED` blocker with no guessed outcome.

#### Packaging and isolation matrix

The final jar is scanned for Pixelmon artifacts, Bukkit and Vault classes, bridge classes, compile time Bukkit or Vault dependencies, reflection strings targeting Bukkit or Vault, accidental embedded jars, credentials, private paths, caches, logs, test worlds, and debug output. Bundled Pixelmon adapter classes are expected, but the Pixelmon runtime and development artifacts are excluded.

### Evidence quality

Evidence must identify the source commit, exact command or procedure, date, environment manifest, relevant artifact hashes, expected result, actual result, and sanitized log location. A passed test against an unrecorded external artifact is not valid production evidence. Manual evidence supplements but never replaces deterministic checks.

## 15. Compatibility, Migration, Rollout, and Recovery

### Compatibility matrix

| Environment | Support state for `2.3.0` |
| --- | --- |
| Minecraft `1.21.1`, NeoForge `21.1.248`, no external provider mods, `internal` selected | Required |
| Minecraft `1.21.1`, NeoForge `21.1.248`, Pixelmon absent, `internal` selected | Required |
| Minecraft `1.21.1`, NeoForge `21.1.248`, exact Pixelmon `9.4.0`, validated Pixelmon provider selected | Required after external gates pass |
| Exact reviewed hybrid stack with separately installed bridge registering `vault` | Required after external gates pass |
| NeoForge `21.1.233` | Not a `2.3.0` target |
| Pixelmon other than `9.4.0` | Unsupported and must not register as compatible |
| Generic unreviewed hybrid runtime, Vault version, bridge, or economy plugin | Unverified and not claimed |
| FutureShops jar on dedicated server and client | Same artifact required |

### Upgrade and provider selection

Before upgrade, operators stop the server and preserve a complete world and configuration backup. The default provider is `internal`, so an upgrade with no intentional config change retains internal economy behavior. Selecting an external provider requires exact dependency installation, reviewed compatibility, price review, and a restart. It does not transfer balances.

When switching from `internal` to external, internal balances and bills remain stored but inactive. When switching back to `internal`, the prior internal balances become authoritative again and valid existing bills may redeem. No value acquired externally is copied back. Switching between external providers likewise performs no transfer.

A selection change while a request is unresolved does not move that request. Recovery remains bound to its originating provider. If the originating provider cannot be restored and no durable outcome can prove safety, monetary mutations remain blocked and the operator follows recovery guidance.

### Rollout

Rollout for validation uses disposable copies, never the only production world. The sequence is standard NeoForge with `internal`, standard NeoForge with missing external selection, exact Pixelmon environment, exact hybrid environment, and final exact artifact replay. The artifact stays unpublished after successful validation.

### Rollback

Rollback means restoring one complete matching backup of world, config, mod set, and provider data after stopping the server. Do not delete FutureShops journals, claims, custody, money items, internal balances, or external plugin data. Do not install `2.2.1` over a world whose `2.3.0` schema was written unless downgrade compatibility is explicitly proven on a copy. A provider selection rollback is a restart based config change, not a balance migration.

### Recovery priorities

1. Preserve the current world, provider data, configuration, jar set, and logs.
2. Identify the selected and originating provider identifiers and exact artifact versions.
3. Inspect the request and outcome record by stable UUID without mutating balances manually.
4. Restore the missing exact provider stack when safe and retry only through idempotent recovery.
5. Run documented compensation only when the original outcome is proven.
6. Keep claims pending when delivery cannot be proven.
7. Restore a complete known matching backup when deterministic recovery cannot proceed.

## 16. Documentation, Operations, and Release Boundaries and Gates

Tracked documentation remains canonical. At minimum, the final documentation set must cover:

* User installation and supported version matrix.
* Provider selection, `internal` default, restart behavior, and unavailable states.
* Public API registration, metadata, threading, lifecycle, request, result, idempotency, recovery, and compatibility contracts.
* Exact Pixelmon `9.4.0` installation and the fact that Pixelmon itself is not bundled.
* Exact reviewed hybrid stack and separately installed bridge requirements, with no claim of generic Vault compatibility.
* Currency precision, price interpretation, overflow rejection, and absence of automatic balance migration.
* Physical bill behavior under internal and external providers.
* Complete surface behavior for shops, carts, player shops, offline proceeds, claims, pay, administration, analytics, events, and rollback.
* Operator status, logs, backup, provider failure, recovery required, restore, and selection rollback procedures.
* Security assumptions, artifact provenance, hashes, license conclusions, and optional dependency isolation.
* Verification commands, environment manifests, expected results, known limitations, and the unpublished artifact identity.
* The absence of an ATM interface or command and the exclusion of publication.

Release `2.3.0` ends at a validated artifact. No GitHub release, mod platform upload, release tag, announcement, or public download is authorized. The required GitHub issue is planning and tracking output, not release publication.

## 17. Risks and Failure Boundaries

| Risk ID | Risk | Impact | Required mitigation | Blocking condition |
| --- | --- | --- | --- | --- |
| `RISK-001` | Official Pixelmon development artifact or licensing terms are unavailable | Adapter cannot be lawfully compiled or verified | Obtain official artifact and written terms evidence, avoid redistribution | Blocks `CORE-PHASE-000` exit |
| `RISK-002` | Pixelmon API cannot provide exact minor units or durable idempotent outcomes | Lossy values or duplicate money are possible | Prove an adapter owned exact receipt and reconciliation design, otherwise request product contract revision | Blocks `CORE-REQ-017` |
| `RISK-003` | Hybrid bridge or exact stack is unavailable or unsafe | `vault` interoperability cannot be validated | Obtain reviewed exact artifacts and disposable environment, keep FutureShops boundary clean | Blocks `CORE-REQ-018` and final completion |
| `RISK-004` | Existing direct `BalanceManager` access bypasses provider orchestration | External mode may mutate dormant internal balances or report false values | Complete call graph and surface matrix, enforce one route, add bypass tests | Blocks `CORE-REQ-009` |
| `RISK-005` | Crash occurs between an external effect and local outcome persistence | Duplicate or lost value may occur | Require durable provider outcome lookup or proven adapter receipt semantics, enter recovery required on ambiguity | Blocks `CORE-REQ-007` |
| `RISK-006` | Different provider precision changes price meaning | Operator may unintentionally alter economy scale | No conversion, validate representation, require documented price review before selection | Blocks rollout until acknowledged in procedure |
| `RISK-007` | Optional classes link when Pixelmon or hybrid APIs are absent | Standard NeoForge startup crashes | Isolate source and class loading, use exact presence and version gates, test clean jar | Blocks `CORE-REQ-001` and integrations |
| `RISK-008` | Client controls remain active from a stale readiness snapshot | Users submit unsafe or confusing operations | Server revalidation, typed rejections, synchronized presentation, reconnect tests | Blocks `CORE-REQ-005` and `CORE-REQ-012` |
| `RISK-009` | Money item registration is removed to disable use | Existing saves or inventories corrupt | Retain registration and decoding, disable mutation behavior only | Blocks `CORE-REQ-011` |
| `RISK-010` | GitHub milestone, labels, capability, or issue 66 authoring evidence are missing | Required tracking cannot satisfy scope | Preserve the completed issue 66 creation and readback evidence, and revalidate and update issue 66 only after artifact validation in Phase 003 | Blocks plan completion; never create a replacement issue during product execution |
| `RISK-011` | Reference `3.0` work is mistaken for current implementation scope | Uncontrolled scope and version conflict | Keep reference only role explicit and freeze ownership here | Requires plan revision before any imported work |
| `RISK-012` | Validation artifact differs from the artifact installed in an external environment | Evidence does not prove the delivered bytes | Hash before installation and verify hashes in every environment | Blocks `CORE-REQ-021` |

## 18. Definition of Done

The plan is complete only when every condition below is true.

The exact completion endpoint is: One fully validated and inspected unpublished FutureShops 2.3.0 jar for Minecraft 1.21.1 and NeoForge 21.1.248, proven against internal, exact Pixelmon 9.4.0, and one exact reviewed Vault bridge stack, plus an actual read-back GitHub issue for 3.0.0 Forge maintenance and its future 1.21.1 port.

Official Pixelmon 9.4.0 runtime and development artifacts, Disposable exact Pixelmon 9.4.0 integration environment, Pixelmon economy API feasibility proof, Separately installed Vault bridge artifact, Exact reviewed hybrid runtime, Vault, and economy plugin stack, and Disposable exact Vault bridge integration environment are known external blockers. If any remains unavailable, dependent scope is preserved and the result is **NOT COMPLETE — EXTERNALLY BLOCKED**. No substitute artifact, reduced verification, partial compatibility claim, or publication may bypass that state.

1. All four contiguous phases are integrated in order, and every mandatory requirement has traceable passing evidence.
2. FutureShops identifies as `2.3.0` on Minecraft `1.21.1` and NeoForge `21.1.248`, with no unrelated platform upgrade.
3. One public, documented NeoForge economy provider API is present and usable without internal implementation access.
4. `internal` remains the default, provider selection is restart only, and no missing, late, failing, or incompatible external provider can cause internal fallback.
5. The server remains online during external provider failure, all monetary mutations fail closed, browsing remains available, and pure barter remains available.
6. Provider metadata controls currency name and precision, and all accepted values use exact checked integer minor units.
7. Every declared balance and mutation surface routes through the server authoritative provider and transaction boundaries.
8. Multi leg operations, retries, crashes, compensation, claims, and offline proceeds demonstrate no duplicate debit or credit with durable request and outcome evidence.
9. External balances are not mirrored into FutureShops, while request facts, custody, claims, and confirmed analytics remain durable.
10. No automatic balance migration occurs. Internal starting balance remains internal only. Provider and precision changes preserve independent data and require operator review.
11. With an external provider selected, money item activation, deposit, withdrawal, redemption, and future ATM mutations are disabled while registrations and existing bills remain safe. No ATM UI or command exists.
12. The bundled optional Pixelmon adapter passes against exact official Pixelmon `9.4.0` artifacts in the disposable environment, and the jar does not bundle Pixelmon.
13. The separately installed bridge registers `vault` and passes against one exact reviewed hybrid stack, while FutureShops contains no Bukkit or Vault dependency, reflection, or bridge code.
14. Standard NeoForge client and dedicated server start from the same final jar without Pixelmon or hybrid components.
15. Focused tests, complete tests, applicable data and GameTests, build, server, client, multiplayer, restart, failure, recovery, dependency, security, jar, and diff gates pass.
16. User, API, maintainer, migration, integration, security, verification, and recovery documentation matches the validated behavior and exact artifacts.
17. `EVD-ART-001` identifies one reproducible, inspected, SHA 256 and SHA 512 hashed FutureShops `2.3.0` artifact that remains unpublished.
18. `EVD-GH-001` identifies open GitHub issue 66, created and read back immediately after plan validation for `3.0.0` Forge `1.20.1` maintenance and a future `1.21.1` port, proves it remained unchanged and open through phases 000 through 002, and proves its post-artifact Phase 003 search, update, and readback with the existing `3.0` beta maintenance milestone and labels `enhancement`, `forge`, `neoforge`, and `ready`. Issue 66 remains open until future owner acceptance, and product execution never creates a replacement or duplicate.
19. `docs/plan/goal.md` is byte for byte unchanged.
20. No publication, release tag, mod platform upload, private data disclosure, credential use outside approved authentication, or unrelated source change occurred.

Passing internal tests without the exact Pixelmon and Vault environments is not completion. Issue 66 creation and authoring readback are completed planning evidence and may not be deferred, repeated, or replaced during product execution. Product completion also requires Phase 003 to update and read back issue 66 only after artifact validation.

## 19. Goal Creator Handoff

After the master, all four phase plans, plan index, and deterministic handoff pass validation, Goal Creator uses the following exact execution handoff without altering an existing `docs/plan/goal.md`.

```text
Mandatory boundary: CORE-REQ-001 through CORE-REQ-022 across CORE-PHASE-000 through CORE-PHASE-003, including exact external integration evidence, the unpublished artifact, and the two-stage open issue lifecycle.
Optional/future disposition: excluded
Locked owner decisions: DEC-001, DEC-002, DEC-003, DEC-004, DEC-005, DEC-006, DEC-007, DEC-008, DEC-009, DEC-010, DEC-011, DEC-012, DEC-013, DEC-014, DEC-015, DEC-016
Active phase: CORE-PHASE-000
Next executable action: Execute P000-TASK-001 to reconfirm repository identity, toolchain, product metadata, economy ownership, and the complete provider call graph before implementation.
Known failing checks: none at validated plan handoff; execution checks have not yet run.
Known external blockers: Official Pixelmon 9.4.0 runtime and development artifacts; Disposable exact Pixelmon 9.4.0 integration environment; Pixelmon economy API feasibility proof; Separately installed Vault bridge artifact; Exact reviewed hybrid runtime, Vault, and economy plugin stack; Disposable exact Vault bridge integration environment.
Completion endpoint: One fully validated and inspected unpublished FutureShops 2.3.0 jar for Minecraft 1.21.1 and NeoForge 21.1.248, proven against internal, exact Pixelmon 9.4.0, and one exact reviewed Vault bridge stack, plus an actual read-back GitHub issue for 3.0.0 Forge maintenance and its future 1.21.1 port.
Required evidence gates: Complete every requirement acceptance criterion and phase exit gate, resolve EXT-001 through EXT-006 with exact evidence, pass deterministic and runtime matrices, inspect and hash the unpublished jar, preserve the EVD-GH-001 authoring creation and readback for issue 66 through phases 000 through 002, and perform the Phase 003 search, update, and readback for that same open issue only after artifact validation.
```

Execution advances one phase at a time. It does not stack future phase work, rewrite the master as status, alter the immutable goal, or declare success before the plan-wide Definition of Done and exact endpoint are satisfied.
