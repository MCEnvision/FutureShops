# FutureShops 2.3.0 Strict External Economy Safety Plan

> **Plan ID:** PLAN-MASTER
> **Plan status:** VALIDATED WITH KNOWN EXTERNAL BLOCKER
> **Project state:** EXISTING
> **Planning subject:** FutureShops 2.3.0 strict external economy safety for Minecraft 1.21.1 NeoForge, with capability gated Pixelmon 9.4.0 and Vault bridge interoperability
> **Plan profile:** software_product

## 1. Project Identity

```text
Project: FutureShops 2.3.0 strict external economy safety
Requested artifact: authoritative_plan
Repository root: /mnt/hermes/projects/FutureShops
Starting branch: envy/plan-2.3.0-external-economy
Starting commit: 9048380881a638cb3ab1916d8cca49eb50d6bf3d
Authoritative remote:
origin
https://github.com/MCEnvision/FutureShops.git
Remote ref: origin/envy/plan-2.3.0-external-economy
Remote commit: 9048380881a638cb3ab1916d8cca49eb50d6bf3d
```

This existing repository evolves the observed FutureShops 2.2.1 implementation into an unpublished, validated 2.3.0 candidate for Minecraft 1.21.1 and NeoForge 21.1.248. Client and server use one FutureShops jar. External money is permitted only through the strict capability gate, durable write-ahead journal, item custody and claim escrow, and fail-closed lifecycle defined here. The four-phase topology remains CORE-PHASE-000 through CORE-PHASE-003. The immutable goal remains outside plan authoring scope and is not regenerated for this amendment.

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
| SRC-007 | owner_request | strict restart safe economy amendment and 3.0.0 issue guidance | current owner request and accepted design discussion | binding safety model, exact Pixelmon artifact inspection, native PlayerPartyStorage mixin implementation, separate Vault bridge and backend proof, issue 66 guidance, and unchanged unpublished endpoint |
| SRC-008 | owner_request | executable acquisition paths for exact development inputs, bridge, and backend | current prerequisite acquisition amendment and standing disposable-server EULA authorization | acquisition and proof work inside existing phases, separate test artifacts, and no repeated Minecraft EULA approval |
| SRC-009 | reference | officially linked Pixelmon development dependency recipe | https://github.com/EnvyWare/Pixelmon-MDK/blob/4309ac5fc79b6a167edfc922f055d1b4d2d56744/build.gradle | universal jar and interface injection dependency pattern, with exact 9.4.0 inputs verified independently of the template version |
| SRC-010 | reference | candidate hybrid and legacy economy sources | https://github.com/ElainAwa/PRTS-SERVER/releases/tag/v1.21.1-1.0.30 and https://github.com/EverNife/PixelmonEconomyBridge and https://github.com/EverNife/FinalEconomy | exact candidate acquisition and upstream provenance review, not a compatibility or atomicity claim |
| SRC-011 | reference | durable embedded transaction proof inputs | https://sqlite.org/atomiccommit.html and https://github.com/xerial/sqlite-jdbc | separately built backend proof, version and license review, and crash tests against one authoritative database |

After validation, the master owns global product scope and each registered phase plan owns only its detailed execution. Repository and runtime evidence may correct current-state claims but cannot weaken the target contract. `docs/plan/goal.md` remains untouched.

## 3. Purpose and Intended Outcome

FutureShops 2.3.0 must expose one public, versioned NeoForge economy provider contract, retain the built-in internal provider as the restart-only default, capability-gate an exact optional Pixelmon 9.4.0 adapter with a complete native `PlayerPartyStorage` transaction mixin, and allow a separately installed bridge to register `vault` without placing Bukkit or Vault code in FutureShops. When both mods are present, Phase 002 must inspect the exact Pixelmon implementation and load the mixin only for that exact version. The native path may mutate only an exact native account after the mixin adds a stable request identity, provider receipt, deduplication, and durable save contract. The `vault` path remains a separate adapter and may mutate only when its bridge and backend prove the same transaction contract, including the in-phase backend proof. One server-owned economy gate must place recoverable items in custody, persist transaction intent before any external effect, drain safely for orderly shutdown, recover before admitting writes after an unclean shutdown, and freeze rather than guess when an outcome cannot be proven. Operators must receive accurate fail-closed behavior, checksummed FutureShops recovery data, explicit manual escalation for unresolved uncertainty, reproducible evidence, one inspected unpublished artifact, and an updated open continuation issue for the 3.0.0 lines.

## 4. Evidence-Based Current State

| Area | Evidence class | Finding | Evidence |
| --- | --- | --- | --- |
| Repository baseline | OBSERVED | Version metadata identifies FutureShops 2.2.1, Minecraft 1.21.1, and NeoForge 21.1.233 before the planned compatibility update. | Repository revision 5fb749b2e6dbc791c8c3984216877ab90b904ee9 and build metadata inspection. |
| Economy boundary | OBSERVED | EconomyProvider, InternalEconomyProvider, and BalanceManager form the current economy boundary whose callers and persistence must be traced. | Source inspection at the pinned starting commit. |
| External artifacts | OBSERVED | The exact Pixelmon 9.4.0 jar, interface injection metadata, disposable Pixelmon runtime, and disposable hybrid runtime are acquired, hashed, and runnable. The separately installed bridge candidate and exact hybrid stack are also available. Native transaction proof, Vault atomicity proof, and third party terms remain separate gates. | EXT-001 through EXT-006 are available inputs for their phase-owned implementation and runtime proof. EXT-009 remains the only unresolved external permission review. |
| Public development route | VERIFIED | The officially linked MDK declares a universal jar and interface injection JSON through an artifact-only Ivy repository. The exact 9.4.0 endpoints responded HTTP 200 with lengths 400154994 and 126 bytes, the downloaded jar hash matches the recorded release hash, and an isolated Java 21 compile probe resolves the real economy and storage types on 2026-09-04. | SRC-009, exact jar and metadata hashes, archive inspection, `javap` signatures, and the isolated compile probe. This establishes development inputs, not mixin permission or transaction correctness. |
| Direct Pixelmon mutation capability | OBSERVED | The exact Pixelmon 9.4.0 runtime exposes balance, precheck, and boolean `add` and `take` operations, but no request identity, durable receipt, idempotent retry, or outcome lookup contract. | [`docs/verification/phase-000/baseline-2026-09-02.md`](../verification/phase-000/baseline-2026-09-02.md), exact runtime class inventory, and public API signature review. |
| Native Pixelmon target inspection | VERIFIED | The exact 9.4.0 artifact identifies `PlayerPartyStorage` as a native `BankAccount` with `pokeDollars`, `pixelDollars` NBT read and write, `add`, `take`, and save adapter and scheduler paths that Phase 002 can target narrowly. | Exact `PlayerPartyStorage`, `BankAccountProxy`, `StorageProxy`, `StorageSaveAdapter`, and `NBTStorageSaveAdapter` class and bytecode inspection. |
| Native Pixelmon transaction path | PROPOSED | Phase 002 must implement this mandatory optional mixin when FutureShops and Pixelmon are both present. The mixin adds request-aware mutations for exact native `PlayerPartyStorage` accounts, receipts beside `pixelDollars`, retry deduplication, and a durable save boundary. Custom and hybrid accounts remain unavailable. | Current owner request, accepted design discussion, and exact target inspection, with implementation and runtime evidence owned by `CORE-PHASE-002`. |
| Tracking capability | VERIFIED | Repository issue capability was authorized for the two-stage continuation issue lifecycle, and plan authoring created and read back issue 66. | EXT-007, DEC-015, and EVD-GH-001 preserve the duplicate search, creation response, issue 66 URL, and readback. |
| Protected goal | OBSERVED | Any existing goal is immutable and supplies no authority to change product scope. | Protected goal path inspection. |

Unresolved third party terms and missing transaction proof remain completion gates, not permission to substitute artifacts, guess APIs, weaken transaction guarantees, or claim compatibility. Minecraft EULA acceptance is already authorized and verified. The available exact runtimes do not prevent Phase 002 from inspecting the Pixelmon artifact, implementing the mixin, or exercising the Vault backend proof harness.

## 5. Product Contract and Profile Coverage

| Profile area | Status | Source | Contract location | Rationale |
| --- | --- | --- | --- | --- |
| inputs and outputs | covered | SRC-001 | Product Contract and Profile Coverage | covered by the updated strict external economy plan and its registered phase evidence |
| component architecture | covered | SRC-001 | Product Contract and Profile Coverage | covered by the updated strict external economy plan and its registered phase evidence |
| state and persistence | covered | SRC-007 | Product Contract and Profile Coverage | covered by the updated strict external economy plan and its registered phase evidence |
| failure taxonomy | covered | SRC-001 | Product Contract and Profile Coverage | covered by the updated strict external economy plan and its registered phase evidence |
| versioning | covered | SRC-001 | Product Contract and Profile Coverage | covered by the updated strict external economy plan and its registered phase evidence |
| security | covered | SRC-001 | Product Contract and Profile Coverage | covered by the updated strict external economy plan and its registered phase evidence |
| test system | external_prerequisite | EXT-001 | Product Contract and Profile Coverage | exact external runtime evidence remains an endpoint prerequisite |
| release lifecycle | covered | SRC-001 | Product Contract and Profile Coverage | covered by the updated strict external economy plan and its registered phase evidence |
| generalization | covered | SRC-001 | Product Contract and Profile Coverage | covered by the updated strict external economy plan and its registered phase evidence |
| determinism | covered | SRC-007 | Product Contract and Profile Coverage | covered by the updated strict external economy plan and its registered phase evidence |

## 6. Mandatory Scope

- CORE-REQ-001 through CORE-REQ-003 define the product target, public provider API, and restart-only provider selection.
- CORE-REQ-004 through CORE-REQ-016 define fail-closed lifecycle, orderly draining, unclean-start recovery, frozen uncertainty, server authority, exact values, write-ahead journaling, escrow, state ownership, every monetary surface, switching, bills, presentation, diagnostics, bounded cost, security, backup, and recovery.
- CORE-REQ-017 and CORE-REQ-018 define an exact native Pixelmon transaction adapter and a separate Vault bridge boundary. A tested safe refusal is mandatory whenever the exact account, durable receipt, or recovery capability is absent.
- CORE-REQ-019 through CORE-REQ-022 define complete validation, documentation, the unpublished artifact, and the two-stage continuation issue lifecycle.

Every declared CORE-REQ ID is mandatory. Stable identifiers must not be renumbered, reused, or silently removed.

## 7. Optional / Future Scope

All locked future scope is excluded from implementation and remains non-blocking for this plan except for creating and maintaining its required tracking issue.

| Future ID | Deferred subject | Boundary |
| --- | --- | --- |
| FUT-001 | Add this strict external economy design and a transaction-aware Vault bridge or backend to the existing 3.0.0 beta on Forge 1.20.1 | Explain the design and implementation guidance in existing issue 66 only. Do not import or implement 3.0.0 code in this plan. |
| FUT-002 | Carry the same native Pixelmon and transaction-aware Vault design into a future 3.0.0 Minecraft 1.21.1 port | Explain the port requirement in the same open issue 66. Product execution updates tracking only and does not implement the port. |
| FUT-003 | ATM user interface and commands | Future release only. The 2.3.0 mutation policy must prevent a future ATM path from bypassing provider rules. |
| FUT-004 | Additional external economy adapters | No adapter beyond internal, Pixelmon 9.4.0, and separately validated vault interoperability is promised here. |

## 8. Non-Goals

| Non-goal ID | Excluded work |
| --- | --- |
| NG-001 | Publishing 2.3.0 to GitHub, CurseForge, Modrinth, or another distribution service. |
| NG-002 | Automatically copying, converting, reconciling, or merging balances between providers. |
| NG-003 | Shipping Pixelmon, Bukkit, Vault, an economy plugin, a hybrid server, a Vault bridge or backend, or their APIs inside FutureShops. A narrow optional mixin targeting the exact Pixelmon `9.4.0` native account is allowed, but no external runtime bytes are bundled. |
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
**Selected choice:** A public NeoForge provider API, bundled internal and optional Pixelmon adapter with a narrow native-account mixin, and a separate bridge registering `vault`.
**Rationale:** The core API supports exact integrations without embedding unrelated platforms. The Pixelmon mixin is limited to the reviewed native account, while Vault and its backend remain outside the FutureShops jar.
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
**Selected choice:** Durable root and leg identities, write-ahead intent, checksummed journal state, item custody and claims, capability-gated external effects, idempotent outcomes where supported, and no mirrored balance ledger.
**Rationale:** A retry or restart may proceed only when durable evidence proves it safe; otherwise the economy freezes without duplicating value or discarding custody.
**Affected requirements:** CORE-REQ-002, CORE-REQ-004, CORE-REQ-007, CORE-REQ-009, CORE-REQ-016, CORE-REQ-017, CORE-REQ-018
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
**Rationale:** This original packaging choice remains historical. DEC-018 defines the direct API limitation and the separately reviewed native-account transaction-mixin path without bundling Pixelmon.
**Affected requirements:** CORE-REQ-017
**Supersedes:** none
**Supersession history:** DEC-018 refines this resolved packaging choice by separating direct API refusal from the conditionally enabled native-account mixin path.

### DEC-013 — Vault support

**Status:** RESOLVED
**Selected choice:** A separately installed reviewed bridge registers `vault`; FutureShops contains no Bukkit or Vault dependency or reflection. Phase 002 must obtain and exercise a transaction-aware bridge or backend proof that persists the balance effect and provider receipt in one transaction before any `vault` mutation is enabled.
**Rationale:** The bridge owns hybrid-platform dependencies and lifecycle adaptation. Phase 002 owns the proof boundary and must produce a disposable transaction proof or a typed safe-refusal result. The currently observed PixelmonEconomyBridge and FinalEconomy path does not provide that atomic request-aware contract without modification. Production adaptation stays in issue 66; DEC-019 authorizes acquiring or constructing a separate Phase 002 test registrant and durable backend proof without waiting for that future production work.
**Affected requirements:** CORE-REQ-002, CORE-REQ-018
**Supersedes:** none

### DEC-014 — Persisted external data

**Status:** RESOLVED
**Selected choice:** Only transaction facts, write-ahead requests, outcomes, custody, claims, recovery state, clean-shutdown evidence, and confirmed analytics, never a mirrored external balance ledger.
**Rationale:** The external provider remains the sole balance authority.
**Affected requirements:** CORE-REQ-007, CORE-REQ-008, CORE-REQ-014, CORE-REQ-016
**Supersedes:** none

### DEC-015 — Continuation issue timing

**Status:** RESOLVED
**Selected choice:** Preserve the existing issue 66 created and read back by plan authoring, then update that issue only after 2.3.0 artifact validation with the strict economy gate, lifecycle, journal, escrow, native Pixelmon transaction-mixin contract, transaction-aware Vault bridge and backend contract, capability, recovery, backup, and provider limitation design for 3.0.0 Forge 1.20.1 and its future 1.21.1 port.
**Rationale:** This plan implements only 2.3.0 native Pixelmon behavior. The 3.0.0 lines receive one actionable design issue for the Vault backend or bridge and the future port, with no code, duplicate issue, early mutation, or closure.
**Affected requirements:** CORE-REQ-022
**Supersedes:** none

### DEC-016 — Publication

**Status:** RESOLVED
**Selected choice:** No publication, release tag, mod platform upload, or public artifact is authorized.
**Rationale:** The selected endpoint is a validated unpublished candidate and open tracking issue.
**Affected requirements:** CORE-REQ-021, CORE-REQ-022
**Supersedes:** none

### DEC-017 — Strict external transaction safety

**Status:** RESOLVED
**Selected choice:** FutureShops 2.3.0 has one strict production mode. It requires a server-owned economy gate, write-ahead transaction journal, durable item custody and claims, orderly `DRAINING`, startup `RECOVERING`, fail-closed `FROZEN`, provider capability gating, and external mutation only when the requested operation has a provable outcome and safe retry contract. It provides no unsafe or experimental override.
**Rationale:** FutureShops can protect its own items and records, but it cannot infer whether a fallible external ledger changed during a crash window. Refusing or freezing is safer than a duplicate debit, duplicate credit, fabricated refund, or balance rewrite.
**Affected requirements:** CORE-REQ-002, CORE-REQ-004, CORE-REQ-005, CORE-REQ-007, CORE-REQ-009, CORE-REQ-012, CORE-REQ-013, CORE-REQ-014, CORE-REQ-016, CORE-REQ-017, CORE-REQ-018, CORE-REQ-019, CORE-REQ-020, CORE-REQ-021
**Supersedes:** none

### DEC-018 — Pixelmon capability classification and native transaction path

**Status:** RESOLVED
**Selected choice:** Bundle an exact Pixelmon 9.4.0 capability adapter without bundling Pixelmon. During Phase 002, inspect the exact `PlayerPartyStorage` and storage save implementation, then implement a request-aware mixin loaded only when FutureShops and Pixelmon are both present. It must persist the FutureShops request UUID, operation, amount, and outcome receipt beside `pixelDollars`, deduplicate retries, and force a proven durable save. A custom or hybrid account remains mutation unavailable, while unscoped Pixelmon calls retain their native behavior and are never claimed as FutureShops transactions.
**Rationale:** The exact runtime exposes `PlayerPartyStorage` as a native `BankAccount` with `pokeDollars` serialized as `pixelDollars`, and exposes `add` and `take` calls plus save adapter and scheduler hooks. The mixin must use that exact implementation evidence, add request identity and receipt persistence without globally breaking Pixelmon's own calls, and refuse before FutureShops effects if the target or save boundary is not proven.
**Affected requirements:** CORE-REQ-002, CORE-REQ-003, CORE-REQ-007, CORE-REQ-012, CORE-REQ-017, CORE-REQ-019, CORE-REQ-020, CORE-REQ-021
**Supersedes:** DEC-012

### DEC-019 — Acquisition and proof work inside the existing phases

**Status:** RESOLVED
**Selected choice:** Execute the ordered public acquisition paths and compile probes for exact Pixelmon inputs. In Phase 002, obtain an existing compatible bridge or build a separate test registrant and durable backend proof using reviewed dependencies. These proof components run in the exact disposable hybrid environment and are never bundled or published with FutureShops. Production adaptation of the legacy PixelmonEconomyBridge and FinalEconomy stack remains issue 66 work for 3.0.0.
**Rationale:** Missing implementation and unattempted acquisition are executable work. A reachable runtime may also be a valid development input; a template version or missing sources archive does not decide that. A completed test backend proves only its own transaction boundary and cannot certify a different production economy.
**Affected requirements:** CORE-REQ-001, CORE-REQ-015, CORE-REQ-017, CORE-REQ-018, CORE-REQ-019, CORE-REQ-020, CORE-REQ-021, CORE-REQ-022
**Supersedes:** none

### DEC-020 — Standing Minecraft EULA authorization

**Status:** RESOLVED
**Selected choice:** Apply the owner's standing authorization to set and read back `eula=true` in every requested disposable Minecraft development and verification runtime. Do not request another consent phrase or treat a missing file or `eula=false` as missing consent. Review any separate third party license or development-use condition under EXT-009.
**Rationale:** Minecraft EULA acceptance is already authorized. It does not authorize unrelated licenses, account applications, redistribution, purchases, production mutations, or public exposure.
**Affected requirements:** CORE-REQ-017, CORE-REQ-018, CORE-REQ-019, CORE-REQ-021
**Supersedes:** none

## 10. External Prerequisites

| ID | Prerequisite | Affected requirements | Availability | Authorization | Required external action |
| --- | --- | --- | --- | --- | --- |
| EXT-001 | Official Pixelmon 9.4.0 runtime and development artifacts | CORE-REQ-001, CORE-REQ-017, CORE-REQ-019, CORE-REQ-021 | available | not_required | Acquisition, hash, archive, API inspection, interface injection metadata review, and the isolated compile probe passed for the exact universal jar. Use it as the pinned input for Phase 002; review runtime mixin and other third party operations separately under EXT-009. |
| EXT-002 | Disposable exact Pixelmon 9.4.0 integration environment | CORE-REQ-017, CORE-REQ-019, CORE-REQ-021 | available | not_required | The exact Minecraft 1.21.1 and NeoForge 21.1.248 profile with Pixelmon 9.4.0 and FutureShops was provisioned with eula=true, reached `Done`, and produced sanitized startup and refusal logs. Phase-owned mutation and recovery evidence remains required. |
| EXT-003 | Pixelmon 9.4.0 artifact inspection and native mixin implementation evidence | CORE-REQ-002, CORE-REQ-007, CORE-REQ-017 | available | not_required | Inspect the exact Pixelmon implementation in Phase 002, record the native account and save paths, implement the request-aware `PlayerPartyStorage` mixin, and prove receipt persistence beside `pixelDollars`, retry deduplication, durable save acknowledgement, recovery, and duplicate prevention. The unmodified direct API remains a negative capability result. |
| EXT-004 | Separately installed Vault bridge artifact | CORE-REQ-002, CORE-REQ-018, CORE-REQ-019, CORE-REQ-021 | available | not_required | The exact separately installed bridge candidate is available and its identity and archive are recorded. Phase 002 must still build or obtain the FutureShops test registrant and durable backend proof, and must leave mutation disabled until that proof passes. An absent ready-made transaction-aware bridge is not an acquisition blocker. |
| EXT-005 | Exact reviewed hybrid runtime, Vault, and economy plugin stack | CORE-REQ-018, CORE-REQ-019, CORE-REQ-021 | available | not_required | The exact Pixelmon 9.4.0 hybrid stack with Vault and its economy components is pinned, hashed, launched, and recorded. Third party terms remain under EXT-009, and the separate one-transaction receipt proof remains Phase 002 work. Legacy backend refusal and proof-backend success are separate results. |
| EXT-006 | Disposable exact Vault bridge integration environment | CORE-REQ-018, CORE-REQ-019, CORE-REQ-021 | available | not_required | The isolated exact hybrid environment was provisioned with eula=true, loaded the pinned stack and FutureShops, reached `Done`, and produced sanitized logs. Phase-owned bridge registration, mutation, and recovery evidence remains required. |
| EXT-007 | GitHub repository tracking capabilities | CORE-REQ-022 | available | authorized | Preserve the completed authoring search, creation, and readback for issue 66, then verify and update that same open issue in CORE-PHASE-003 only after artifact validation. |
| EXT-008 | Standing Minecraft EULA acceptance for disposable runtime validation | CORE-REQ-017, CORE-REQ-018, CORE-REQ-019, CORE-REQ-021 | available | authorized | Apply DEC-020 by creating or updating the exact disposable runtime eula.txt and verifying eula=true before launch; keep acceptance files untracked. |
| EXT-009 | Third party artifact and development-use terms review | CORE-REQ-015, CORE-REQ-017, CORE-REQ-018, CORE-REQ-019, CORE-REQ-021 | unknown | not_required | Review the exact upstream terms for download, private compilation, runtime mixins, execution, modification, and redistribution separately. Use documented permitted routes; prepare a precise owner-delivered developer inquiry only when a required right or authenticated input remains unresolved after public checks. |

### EXT-001 — Official Pixelmon 9.4.0 runtime and development artifacts

**Kind:** artifact. **Availability evidence:** The exact universal jar is present at the pinned release hash, the 126 byte interface injection metadata is present, the archive is readable, `javap` identifies the economy and storage APIs, and an isolated Java 21 compile probe resolves `BankAccount`, `BankAccountProxy`, and `PlayerPartyStorage` against the real jar. **Remaining gate:** Exact third party terms and runtime mixin permission remain under EXT-009. Missing Javadoc, sources, a special development classifier, or a template already pinned to 9.4.0 does not fail this prerequisite. A failed exact compile or unresolved required permission blocks the affected integration until its route is resolved; it does not restart completed phases.

### EXT-002 — Disposable exact Pixelmon 9.4.0 integration environment

**Kind:** environment. **Availability evidence:** A reproducible isolated Minecraft 1.21.1, NeoForge 21.1.248, Pixelmon 9.4.0, and FutureShops profile has eula=true, reaches `Done`, and records sanitized startup and capability-refusal logs. **Remaining gate:** Native mutation, receipt, durable-save, and recovery matrices remain Phase 002 proof and completion work.

### EXT-003 — Pixelmon 9.4.0 artifact inspection and native mixin implementation evidence

**Kind:** other. **Required evidence:** Inspect exact Pixelmon 9.4.0 bytecode and record `PlayerPartyStorage` inheritance, the `pokeDollars` field, `pixelDollars` NBT read and write, `add` and `take` behavior, `BankAccountProxy` account classification, `StorageProxy` save adapter and scheduler paths, and the exact mixin target. Implement and prove the request-aware native mixin when both mods are present, receipt persistence beside `pixelDollars`, retry deduplication, durable save acknowledgement, failure behavior, recovery, and duplicate prevention. Unscoped Pixelmon calls retain native behavior and are not claimed as FutureShops transactions. The unmodified direct API remains a negative capability result.

### EXT-004 — Separately installed Vault bridge artifact

**Kind:** artifact. **Availability evidence:** The exact separately installed bridge candidate is available with recorded version, hash, archive, dependencies, and hybrid startup evidence. **Remaining gate:** Produce or obtain the separate FutureShops proof registrant through P002-TASK-006 and keep its declared mutation capabilities disabled until the paired backend passes durable receipt tests. Missing ready-made transaction-aware bridge code or atomicity evidence is Phase 002 implementation and verification work, not an acquisition blocker.

### EXT-005 — Exact reviewed hybrid runtime, Vault, and economy plugin stack

**Kind:** group. **Availability evidence:** Exact versions, sources, sizes, hashes, archive identities, and a successful isolated startup are recorded for the Pixelmon 9.4.0 hybrid stack, Vault, and its economy components. **Remaining gate:** Third party terms remain under EXT-009, and the separate proof backend must provide a real on-disk transaction and exact hybrid trace proving the balance change and receipt commit together. For an existing backend lacking that extension, require exact safe-refusal evidence rather than demanding it already implement the future 3.0.0 change. An in-memory fixture or a proof against another backend does not satisfy exact hybrid runtime verification.

### EXT-006 — Disposable exact Vault bridge integration environment

**Kind:** environment. **Availability evidence:** The isolated hybrid environment contains the pinned exact artifacts, FutureShops, and eula=true, reaches `Done`, and records sanitized logs with production data excluded. **Remaining gate:** Phase 002 bridge registration, mutation, receipt, and recovery proof remains required for endpoint completion.

### EXT-007 — GitHub repository tracking capabilities

**Kind:** service. **Required evidence:** Authenticated EnVisione identity, repository identity, existing 3.0 beta maintenance milestone, required labels, duplicate search, creation result, issue 66 URL, and readback. Plan authoring created and read back issue 66 immediately after validation; CORE-PHASE-003 preserves that evidence without early live access to issue 66, then revalidates and updates issue 66 only after artifact validation without creating a replacement or closing it.

### EXT-008 — Standing Minecraft EULA acceptance for disposable runtime validation

**Kind:** authorization. **Required evidence:** DEC-020, the exact disposable runtime path and runbook, artifact identities, intended operations, host and operator, execution interval, rollback and cleanup procedure, and readback of `eula=true` before launch. The existing owner authorization covers requested development and verification servers. A new disposable directory does not require renewed acceptance. Keep eula.txt and credentials outside tracked source.

### EXT-009 — Third party artifact and development-use terms review

**Kind:** other. **Required evidence:** Dated authoritative terms and license sources bound to each exact artifact or source revision, with conclusions separated by operation. An upstream example license does not license the Pixelmon runtime; a missing GitHub license badge does not by itself decide private execution rights. Inspect the actual license, release terms, and necessary operations. Do not assume that permission to compile implies permission to alter or redistribute. If runtime mixin permission or authenticated developer access remains unclear, prepare the exact question and source references for the owner rather than treating Minecraft EULA acceptance as missing. No unsolicited upstream message, account creation, application, purchase, or external code publication is authorized.

### Acquisition, construction, and proof policy

Phase 000 owns initial discovery and classification. Phase 002 resumes the first unfinished acquisition or implementation task from fresh evidence without reopening completed platform or API work. P002-TASK-013 resolves exact Pixelmon compile inputs, P002-TASK-014 resolves the exact disposable hybrid stack, and P002-TASK-006 obtains or builds the separate bridge and durable backend proof. Their missing outputs are not prerequisites to starting those tasks. Only consumption of a particular input waits for that input's review.

Use the official Pixelmon downloads page and its linked MDK dependency recipe first. The exact runtime file is identified by CurseForge file `8661427`; its previously recorded SHA-256 is `9020393f98382ae8794ef2694e7bec1984c1a0eca735ea3eea06e0cb151c61f2`. Validate the actual downloaded bytes, not just HTTP success or filenames. The MDK template is an integration recipe, not a requirement for a distinct development jar. Prefer the exact public universal jar and interface injection metadata with a successful compile probe. The authenticated developer application is a fallback only for a proven missing input or unresolved permission, not the default waiting point.

For Vault, first inspect an existing registrant against the public FutureShops API. PixelmonEconomyBridge bridges Pixelmon accounts to an economy and is not automatically a FutureShops provider. If no exact transaction-aware registrant is available, implement a separate test registrant and backend proof in Phase 002. Use a reviewed embedded transactional store, such as SQLite through a pinned reviewed JDBC driver, as the proof backend's sole ledger for synthetic accounts. Commit the balance effect and immutable request receipt in the same database transaction. Neither a local FutureShops receipt nor a database sidecar around another plugin's Vault call satisfies this rule.

The initial hybrid acquisition candidate is PRTS `v1.21.1-1.0.30`, whose recorded installer pins NeoForge `21.1.248`, together with Vault `1.7.3`. Revalidate upstream release identity, hashes, terms, dependencies, security, and runtime compatibility. Candidate selection is not a support claim. Legacy PixelmonEconomyBridge `1.1.6`, FinalEconomy `1.0.9`, and EverNifeCore `2.0.4.4` remain separate negative or future-adaptation candidates; their unresolved modification rights do not prevent a first party proof backend. No unreviewed older loader or experimental parallel runtime is a silent substitute.

Every acquisition attempt records source, exact target, outcome, missing component or right, and next permitted route. Try the authoritative release, officially linked dependency route, and an already owned hash-matching artifact before declaring an external artifact unavailable. Keep network retries bounded and retry only a changed route or diagnosis. Source implementation, failed compile probes, classloader integration, schema design, and crash tests are owned work. Unavailable authenticated access, unavailable exact external bytes after these routes, or a concretely unresolved third party permission is an external gate. Ordinary plan authoring does not assert that these inputs or proofs are already complete.

Retain only sanitized acquisition manifests, source and build recipes for first party proof fixtures, and required test results in the repository's test and verification documentation. Keep downloaded bytes, dependency caches, runtime directories, databases, and acceptance files disposable. Stop owned processes and remove exact test-created files after their final consumer, preserving shared caches and user data. Run headless servers on node-1 and any client verification on the verified laptop desktop and discrete GPU under the standing host policy.

## 11. Architecture Contract and Ownership Boundaries

### Components and dependency direction

```text
client screens and presentation
            |
            v
server commands, packets, shops, carts, player shops, markets, public services
            |
            v
strict economy gate, write-ahead journal, escrow, claims, lifecycle coordinator
            |
            v
capability validator, public provider api, and frozen provider registry
       |                    |                    |
       v                    v                    v
internal provider     pixelmon adapter     separate vault bridge
future shops data     bundled, optional    separately installed
```

All gameplay and public entry points depend on one strict economy gate. The gate owns lifecycle admission, durable intent, escrow and claim ordering, capability checks, and outcome classification before it invokes a provider. Callers must not read or mutate `BalanceManager` directly once provider routing applies. The internal provider may own `BalanceManager`; an external provider may not use it as a shadow balance store. The bundled Pixelmon adapter depends on the public API and verified Pixelmon interfaces only. The separate bridge depends on the public API and its hybrid stack; FutureShops must not depend on bridge, Bukkit, Vault, or economy plugin classes.

### Provider registry and selection

The public API exposes a deterministic registration mechanism appropriate to NeoForge `1.21.1` lifecycle events. A provider registration includes a stable lowercase resource identifier, API compatibility version, factory or service boundary, currency metadata, lifecycle behavior, failure reporting, and an immutable capability declaration for authoritative balance query, precheck, withdraw, deposit, durable receipt lookup, and idempotent retry. Capabilities are verified rather than trusted. Each monetary surface declares its required capability set, and the strict gate rejects that surface before intent or custody when the selected provider cannot satisfy it.

`internal` is reserved for the built in provider. `vault` is reserved for the separately installed bridge contract. Duplicate identifiers, invalid identifiers, incompatible API versions, invalid metadata, registration outside the allowed window, or multiple ambiguous registrations must never resolve by load order. They produce a deterministic rejected registration and, when the rejected identifier is selected, an unavailable selected provider.

The registry freezes before monetary services become available. The configured identifier is resolved once for the server lifecycle. A late registration cannot activate until a clean restart. A config reload may report that a restart is required, but it cannot change the active provider.

### Trust boundaries

| Boundary | Trusted authority | Untrusted or fallible input | Required containment |
| --- | --- | --- | --- |
| Client to server | Logical server | Packet values, menu state, displayed balances, request identifiers supplied by clients | Recompute identity and permissions, validate bounds and current provider state, and generate or validate server owned request identity |
| Configuration | Validated server configuration snapshot | Provider identifier, prices, limits, and reload attempts | Validate before activation, retain last valid non selection settings, and require restart for provider changes |
| Provider API | FutureShops orchestration contract | Provider metadata, balances, outcomes, exceptions, latency, and readiness | Validate every response, use checked arithmetic, fail closed, and never treat an error as zero or success |
| Pixelmon | Verified adapter and exact native-account mixin | Optional classes, version, account implementation, API representation, lifecycle, persistence, and failure behavior | Inspect exact `9.4.0` bytecode, isolate linkage, load the mixin only when both mods are present, enable request-aware mutation only for native `PlayerPartyStorage` with a receipt beside `pixelDollars` and proven durable save, reject custom or hybrid accounts, and preserve server startup |
| Hybrid and Vault | Separately installed bridge | Hybrid runtime, Vault services, plugin lifecycle, provider changes, and plugin exceptions | FutureShops sees only its provider API. Bridge failure makes `vault` unavailable without loading Bukkit or Vault classes in FutureShops |
| Persistence | Versioned FutureShops data | Interrupted writes, old records, unknown outcomes, provider changes, and corrupted metadata | Preserve data, block ambiguous mutations, recover by request ID, and require operator action when safe reconciliation is impossible |
| Build inputs | Pinned repository and reviewed artifacts | Downloaded jars, transitive dependencies, archives, licenses, and repositories | Verify provenance and hashes, inspect contents, avoid redistribution, and record exact tested inputs |

### Provider lifecycle state machine

The externally visible lifecycle is deterministic and server owned.

| State | Meaning | Monetary reads | Monetary mutations | Browsing | Pure barter | Transition rule |
| --- | --- | --- | --- | --- | --- | --- |
| `UNRESOLVED` | Registry or server context is not frozen | Unavailable, never zero | Rejected | Available when independent of provider values | Available | Startup only |
| `READY` | Selected provider passed compatibility, metadata, capability, readiness, journal, and recovery gates | Provider authoritative | Allowed only through the strict gate for operations whose capabilities are proven | Available | Available | Reached only during startup resolution and successful recovery |
| `DRAINING` | Orderly stop or scheduled restart has begun | Safe reads only | New mutations rejected; bounded in-flight work may finish or persist a safe pending state | Available until server stop | Available until server stop | Flush journal, escrow, claims, and checksums before writing a clean-shutdown marker |
| `MISSING` | Selected identifier was not registered in time | Unavailable | Rejected | Available with clear status | Available | Requires restart after installation |
| `INCOMPATIBLE` | API, mod version, metadata, precision, or capability is incompatible | Unavailable | Rejected | Available with clear status | Available | Requires corrected installation and restart |
| `FAILED` | Provider threw, returned invalid data, failed readiness, or entered unsafe state | Unavailable unless a last confirmed historical value is explicitly labeled non authoritative | Rejected | Available with clear status | Available | No automatic internal fallback or hot recovery |
| `RECOVERING` | Startup found no valid clean-shutdown marker or found incomplete journal records | Only safe provider and receipt queries | Rejected except proven idempotent reconciliation | Available with clear status | Available | Enter before external readiness; exit to `READY` only when every record is proven |
| `FROZEN` | A journal record has an external effect whose result cannot be proven, or recovery integrity failed | Only safe diagnostic queries | All external mutations rejected; no retry, compensation, refund, or balance restore is automatic | Available with clear status | Available | Requires evidence-backed operator resolution or restoration of a complete matching backup |
| `STOPPED` | Server lifecycle is stopping or unavailable | Unavailable | Rejected | Not applicable | Not applicable | Terminal for the current server lifecycle |

`RECOVERY_REQUIRED` remains a stable typed result reason for callers and maps to lifecycle state `RECOVERING` or `FROZEN`; it is no longer an ambiguous active-state name. An external provider becoming available after `MISSING`, `INCOMPATIBLE`, or `FAILED` does not hot activate. A clean restart is required. Failure of one operation transitions to `FAILED` only when no effect could have occurred, to `RECOVERING` when durable lookup can decide it, and to `FROZEN` when safety cannot be proven. The exact classification must be recorded and tested.

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
| Write-ahead transaction state | FutureShops | FutureShops | Persist `PREPARED`, `EXTERNAL_PENDING`, `EXTERNAL_CONFIRMED`, `DELIVERED`, `CLAIMED`, `UNCERTAIN`, and `RESOLVED` transitions with integrity metadata before dependent effects |
| Receipt audit journal | FutureShops | FutureShops | Write one checksummed transition record under `world/data/futureshops/receipts` for each request state and provider result. It proves what FutureShops durably recorded, not that an external effect is safe to replay |
| Provider mutation outcome | Internal provider plus coordinator evidence | External provider receipt or query plus coordinator evidence | Persist request and outcome facts, not a balance ledger |
| Shop, listing, order, cart, and custody state | FutureShops | FutureShops | Bind every monetary transition to its request identity and confirmed result |
| Claims and offline proceeds | FutureShops delivery state, internal value through internal provider | FutureShops delivery state, external value through selected provider | Never discard. Failed delivery remains a durable claim or recoverable operation |
| Physical bills and money item data | FutureShops | FutureShops, registered but inactive | Preserve exact existing data and prevent external redemption or activation |
| Analytics | FutureShops confirmed event facts | FutureShops confirmed event facts | Record confirmed request outcomes and provider identity, never infer or mirror current external balances |
| Clean-shutdown marker and recovery checkpoint | FutureShops | FutureShops | Write only after draining, flushing journal, escrow, and claims, and proving no unresolved in-flight effect; validate before external readiness on next start |

Persistent records introduced or changed by this release require an explicit schema version, stable serialized fields, defensive decoding, and migration tests. Unknown or newer data must not be silently discarded. Durable records bind to the originating provider identifier. A provider selection change never reassigns an unresolved request to a different provider.

### Transaction and determinism contract

Every monetary mutation has one server owned root request UUID. Each debit, credit, fee, refund, compensation, claim, and offline delivery leg has a deterministic child identity derived from the immutable root and leg role or an equivalently stable persisted UUID. Retries reuse the same identity. A logically new user action must never reuse a completed identity.

Before an external effect, FutureShops persists a checksummed `PREPARED` record and matching receipt audit record under `world/data/futureshops/receipts` containing immutable request, provider, actor, amount, direction, required capability set, and any custody identity, then persists `EXTERNAL_PENDING` and its receipt audit record. Each later transition writes another checksummed receipt audit record with the provider result or recovery classification. The provider contract must prove a definitive outcome and safe retry through durable receipt lookup and idempotent identity, either directly or through an exact reviewed adapter mechanism. A timeout, process interruption, exception, missing acknowledgement, missing record, invalid checksum, or contradictory record must never be guessed as success or failure. If lookup can reconcile the result, startup enters `RECOVERING`; if the result remains unknowable or the local record cannot be trusted, the record becomes `UNCERTAIN`, lifecycle enters `FROZEN`, and no automatic retry, refund, compensation, or balance restoration occurs. Local receipt durability does not provide external atomicity or idempotency.

Multi leg workflows must persist intent before the first external effect, persist every confirmed outcome, and order custody and value movements to prevent loss. For a buy, FutureShops first secures the item or exact delivery entitlement in durable custody, then performs a proven debit, then delivers the item or creates a durable claim. For a sell, FutureShops first secures the exact sold item in durable custody, then performs a proven credit, then completes or claims delivery. Operations without an escrowable item, including player pay, require all value legs to expose the full strict capability set. Recovery repeats only operations whose provider contract proves repetition safe. Compensation has its own stable identity and cannot run twice.

The transaction record may contain provider identifier, root and child request IDs, operation type, amount, participants, direction, custody and claim identifiers, timestamps or monotonic sequence, journal state, required and observed capabilities, provider receipt, error classification, recovery state, and compensation relationship. The receipt audit record under `world/data/futureshops/receipts` may contain the same immutable request facts, local transition, provider identifier, provider receipt when returned, checksum, and recovery classification. It must not contain a periodically synchronized or independently mutable copy of player balances. Backups include the journal, receipt audit directory, escrow index, claims, request identities, integrity metadata, and clean-shutdown marker as one matching recovery set. They never treat a saved external balance as authoritative and never restore external money automatically.

### Failure semantics

* Missing, late, incompatible, and failing external providers leave the Minecraft server online.
* All money containing operations fail before any unrelated item custody or market mutation when readiness is absent.
* Pure barter, defined as a transaction with no monetary amount, fee, deposit, withdrawal, balance, or currency item leg, remains available.
* Browsing, search, historical transaction evidence, and non mutating market views remain available with an explicit provider status.
* A provider exception is contained at the API boundary and logged once with actionable context. It must not crash the server, leak private data, or be converted into success.
* Provider unavailable is distinct from insufficient funds, invalid amount, permission denied, duplicate request, completed request, and recovery required.
* Claims and custody remain accessible and durable while monetary mutations are disabled. Claim delivery that requires an unavailable external credit remains pending rather than discarded.
* Rollback means transaction aware recovery or compensation. It never means directly rewriting an external balance from a mirrored value.
* Orderly restart enters `DRAINING`, rejects new money operations, completes or checkpoints bounded in-flight work, flushes durable state, and writes the clean marker last.
* Unclean startup or any unknown, incomplete, checksum-invalid, or contradictory receipt audit record enters `RECOVERING` before provider readiness. An unprovable outcome or untrusted local record enters `FROZEN`; operators see the request, provider, custody, and safe next action, and the server never performs an automatic external balance correction.

### Versioning and compatibility

The provider API has an explicit compatibility version independent of the product version. Public identifiers, required metadata, request semantics, outcomes, and lifecycle states are documented contracts. A provider built for an unsupported API version is `INCOMPATIBLE`. This release does not promise compatibility with unverified provider implementations.

Pixelmon compatibility is exactly Pixelmon `9.4.0` on Minecraft `1.21.1` and NeoForge `21.1.248`. Phase 002 must inspect the exact artifact before implementing the optional mixin. The adapter must not register for another Pixelmon version unless exact compatibility is proven in a future plan. Its classes must not link during ordinary startup when Pixelmon is absent. The unmodified direct API remains query and precheck capable but mutation unsafe. When both mods are present, the narrow mixin must add request-aware mutation only to an exact native `PlayerPartyStorage` account, with the request UUID, operation, amount, and outcome receipt persisted beside `pixelDollars`, retry deduplication, and a proven durable save boundary. A custom implementation or `BankAccountProxy` result such as a Vault account is never treated as native. If any mixin or persistence capability is absent, mutation-required surfaces remain unavailable without preventing safe queries or pure barter. Unscoped Pixelmon calls retain their native behavior and are not claimed as FutureShops transactions.

Vault interoperability is limited to the exact separately reviewed bridge and hybrid stack. Phase 002 must inspect the bridge and backend boundary and execute a transaction proof that persists the balance effect and provider receipt in one transaction, including interrupted commit and retry lookup cases, before any mutation is enabled. The currently observed PixelmonEconomyBridge and FinalEconomy path is not accepted without that modification; its production transaction-aware implementation is tracked in issue 66 for the 3.0.0 lines. FutureShops must run on standard NeoForge without Bukkit, Vault, the bridge, or any hybrid classes present.

### Security, privacy, and determinism

Provider identifiers, metadata, values, outcomes, receipts, configuration, packets, commands, item data, and saved records are untrusted until validated. Permissions and player identity are checked on the logical server. Request IDs arriving from an untrusted client cannot authorize replay or another player's transaction.

No credentials, access tokens, private raw player data, or proprietary logs may enter source, tests, documentation, artifacts, GitHub issues, or validation evidence. Artifact review records hashes and public provenance only. Logs use stable error categories, provider identifier, safe request identifier, and lifecycle state while omitting sensitive balance details when unnecessary.

Registration order, provider selection, request identity, checked arithmetic, state transitions, and recovery outcomes must be deterministic. Map iteration, mod load order, timing races, locale, client state, or provider discovery timing must not select a different provider or change transaction outcome.

## Required behavior by surface

| Surface | `internal` ready | External capability-ready | External unavailable, capability-incomplete, recovering, or frozen |
| --- | --- | --- | --- |
| Public balance query API | Read authoritative internal balance | Read authoritative selected provider balance | Return typed unavailable state, never zero fallback |
| Public mutation API | Route through coordinator and internal provider | Route through strict gate, journal, and selected provider only when required capabilities are proven | Reject before journal, custody, or provider mutation |
| Administrative balance query | Internal authoritative value | External authoritative value | Report unavailable with provider state |
| Administrative grant, set, remove, or equivalent | Idempotent internal mutation | Idempotent external mutation only if representable and supported by contract | Reject, never edit internal as fallback |
| Analytics and audit views | Confirmed internal request outcomes | Confirmed external request outcomes and provider identity | Preserve historical facts and label live provider unavailable, never show a mirrored current balance |
| Server shop purchase and sale | Internal value leg | Secure applicable item custody, persist intent, execute proven external value leg, then deliver or claim | Reject any money-containing trade before custody or provider call |
| Cart and checkout | Internal atomic workflow | External write-ahead multi-leg workflow with custody and claim conservation | Preserve cart, reject checkout before custody or value changes |
| Player shop purchase and proceeds | Internal debit and credit | External durable debit, custody, delivery, credit or claim, and proven compensation | Browsing remains, money-containing purchase rejected |
| Offline proceeds and claims | Durable internal credit or claim | Durable external credit receipt or pending claim | Keep claim pending and accessible |
| Player pay or transfer | Idempotent internal transfer | Idempotent external debit and credit workflow | Reject without partial debit |
| Deposit and withdrawal | Available under existing validated internal rules | Disabled | Disabled |
| Physical money item activation and redemption | Available under existing validated internal rules | Disabled, existing bills remain inert and retained | Disabled, existing bills remain inert and retained |
| Money item registration and save decoding | Registered | Registered | Registered |
| Fees, refunds, events, and rollback | Confirmed internal outcomes | Confirmed provider outcomes with idempotent compensation | No fabricated success event, preserve recoverable state |
| Lifecycle and reload | Start internal, keep restart-only selection, drain before stop | Start selected external only after capability and recovery gates, drain before stop | Stay online, recover before readiness, freeze uncertainty, and require a clean restart after installation correction |
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

**Behavior:** Define and publish within the mod jar a documented NeoForge provider API covering stable identifiers, compatibility version, deterministic registration, validated currency metadata, lifecycle readiness, authoritative balance queries, checked integer minor unit mutations, immutable capability declarations, durable request IDs, idempotent outcomes, receipt lookup, and outcome recovery. Reserve `internal` and `vault` semantics. Freeze the registry and verified capabilities before monetary readiness.
**Owner:** `CORE-PHASE-000`
**Contributors:** `CORE-PHASE-001`, `CORE-PHASE-002`, `CORE-PHASE-003`
**Dependencies:** DEC-003, DEC-004, DEC-005, DEC-006, DEC-007, DEC-009, DEC-013, DEC-017, DEC-018, EXT-003
**Lifecycle stage:** change
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- A provider can integrate without accessing internal packages; duplicate, late, malformed, and incompatible registrations are deterministic; capabilities cover balance, precheck, withdraw, deposit, durable receipt lookup, and idempotent retry; each surface refuses missing capabilities before mutation; the contract can express unavailable and ambiguous outcomes without using zero or boolean success as a substitute; API documentation specifies thread, lifecycle, error, idempotency, and compatibility rules.

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

### CORE-REQ-004 — fail closed provider lifecycle and restart control

**Behavior:** Implement and enforce `UNRESOLVED`, `READY`, `DRAINING`, `MISSING`, `INCOMPATIBLE`, `FAILED`, `RECOVERING`, `FROZEN`, and `STOPPED` at every query and mutation boundary. Orderly shutdown drains and flushes before writing a clean marker. Unclean startup recovers before readiness. Unprovable outcomes freeze external mutation. Never choose `internal` because a selected external provider failed.
**Owner:** `CORE-PHASE-001`
**Contributors:** `CORE-PHASE-002`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-002, CORE-REQ-003, DEC-006, DEC-009, DEC-017
**Lifecycle stage:** continuous
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- The server stays online; every new monetary mutation is rejected outside `READY`; `DRAINING` admits no new work and writes the clean marker last; `RECOVERING` precedes external readiness after unclean stop; `FROZEN` permits no automatic retry or balance correction; browsing, claims, custody access, and pure barter continue; unavailable reads are never zero.

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

### CORE-REQ-007 — write-ahead journal, escrow, and durable transaction coordination

**Behavior:** Give every mutation a durable root request UUID and every leg or compensation a stable child identity. Persist a checksummed write-ahead record before effects using `PREPARED`, `EXTERNAL_PENDING`, `EXTERNAL_CONFIRMED`, `DELIVERED`, `CLAIMED`, `UNCERTAIN`, and `RESOLVED` states, and write a matching durable receipt audit record for every transition under `world/data/futureshops/receipts`. The receipt audit journal records FutureShops intent, provider identity, returned outcome, and recovery facts; it is local evidence only and never proves that a Vault or Pixelmon effect is safe to replay. Secure recoverable items in durable custody before the corresponding external value leg, retain failed delivery as a claim, require provider idempotency and durable outcome lookup for enabled mutations, and never use a mirrored balance ledger, unsafe retry, automatic refund, or automatic balance restoration to resolve uncertainty. Unknown, incomplete, checksum-invalid, or contradictory receipt records force `RECOVERING` or `FROZEN` before monetary readiness.
**Owner:** `CORE-PHASE-001`
**Contributors:** `CORE-PHASE-002`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-002, CORE-REQ-004, CORE-REQ-006, DEC-009, DEC-014, DEC-017, EXT-003
**Lifecycle stage:** continuous
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- Intent, custody, and the matching receipt audit record under `world/data/futureshops/receipts` are durable before their dependent external effects; each logical leg changes value at most once; completed retries return the same outcome; a capability failure prevents the call; an ambiguous or unknown record becomes `UNCERTAIN` and forces `RECOVERING` or `FROZEN`; compensation executes at most once only after proof; custody and claims survive restart; the schema contains no external balance mirror; local receipt durability is never treated as external idempotency.

**Required evidence**

- State transition tests, receipt audit directory fixtures, crash matrix, journal fixtures, provider receipt evidence, recovery logs, backup comparison, and persistence schema review.

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

**Behavior:** Inventory and route all balance and mutation surfaces, including public APIs, administration, analytics, server shops, carts, player shops, offline proceeds, claims, pay, deposit, withdrawal, physical money items, fees, events, rollback, reload, startup, and shutdown. Any operation with a money leg uses the strict gate, surface-specific capability check, lifecycle admission, write-ahead journal, and applicable custody or claim ordering.
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

### CORE-REQ-016 — operational draining, backup, recovery, and rollback

**Behavior:** Drain orderly restarts, reject new mutations, flush the checksummed journal, the durable receipt audit journal under `world/data/futureshops/receipts`, escrow index, claims, request identities, and recovery checkpoint, and write a clean-shutdown marker last. Backups and complete matching restores include the receipt audit journal with the FutureShops journal, custody, claims, and clean marker. On unclean startup, enter `RECOVERING`; classify every receipt record before readiness, reconcile only through proven provider receipts and idempotent identities, and enter `FROZEN` when an outcome, record integrity, or originating provider remains unknowable. Provide backup, restore, provider correction, operator resolution, selection rollback, and reconciliation procedures that preserve all data and never automatically retry, refund, or rewrite external balances from guesses, local receipt records, or backup snapshots.
**Owner:** `CORE-PHASE-001`
**Contributors:** `CORE-PHASE-002`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-004, CORE-REQ-007, CORE-REQ-008, CORE-REQ-010, CORE-REQ-013, DEC-017
**Lifecycle stage:** continuous
**Production verification:** nondestructive
**Release impact:** migration

**Acceptance criteria**

- A clean stop has a valid marker only after the FutureShops journal, receipt audit directory, custody, claims, request identities, and checkpoint are durably flushed and verified; an unclean stop or unknown receipt record enters `RECOVERING` before readiness and enters `FROZEN` when the external outcome or local integrity cannot be proven; restart neither duplicates value nor discards items, claims, or bills; unresolved ambiguity remains frozen with operator-visible evidence; selection rollback preserves independent balances; documentation prohibits deleting receipt records or journals or restoring external balances automatically.

**Required evidence**

- Crash and restore matrix, receipt audit directory integrity and backup hashes, recovery logs, and operator runbook validation.

### CORE-REQ-017 — bundled capability-gated Pixelmon adapter with native transaction mixin

**Behavior:** Bundle adapter code for exactly Pixelmon `9.4.0`, compiled and tested against reviewed exact development inputs under DEC-019, without bundling Pixelmon. Execute the official dependency and compile-probe route; an unchanged verified universal jar with required interface injection metadata may supply those inputs without a separate development bundle. In Phase 002, inspect `PlayerPartyStorage`, `BankAccountProxy`, `StorageProxy`, `StorageSaveAdapter`, and the exact NBT and save path before implementing a narrow optional mixin loaded only when FutureShops and Pixelmon are both present. The mixin must add a request-aware native entrypoint that carries the FutureShops request UUID, operation, and amount, persists an outcome receipt beside `pixelDollars`, deduplicates retries by that UUID, and forces a proven durable save or atomic replacement before reporting success. Keep the unmodified direct API mutation-refusing for FutureShops calls, while leaving unscoped Pixelmon calls at their native behavior. A custom account or a `BankAccountProxy` result such as a Vault account must not use the native path. If the exact target, account classification, receipt schema, save boundary, or recovery lookup is absent or contradictory, strict mode must reject before local journal, custody, inventory, Pixelmon, analytics, claim, or event effects. A local receipt audit record cannot replace the provider receipt stored with the native account.
**Owner:** `CORE-PHASE-002`
**Contributors:** `CORE-PHASE-000`, `CORE-PHASE-001`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-001, CORE-REQ-002, CORE-REQ-004, CORE-REQ-006, CORE-REQ-007, CORE-REQ-009, CORE-REQ-015, CORE-REQ-016, DEC-017, DEC-018, DEC-019, DEC-020, EXT-001, EXT-002, EXT-003, EXT-008, EXT-009
**Lifecycle stage:** change
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- Standard NeoForge starts without Pixelmon; exact Pixelmon starts on NeoForge `21.1.248`; Phase 002 records the exact class and save inspection; verified metadata, balance, and precheck behavior is exact; FutureShops direct calls refuse while the request-aware mixin is absent; the native mixin enables mutations only for an exact `PlayerPartyStorage` account with durable receipt and save proof; custom or hybrid accounts refuse before effects; unscoped Pixelmon calls retain native behavior; unsupported versions do not register; no Pixelmon artifact is packaged.

**Required evidence**

- `EVD-EXT-001` through `EVD-EXT-004`, exact Pixelmon class and bytecode inspection, mixin target record, adapter tests, environment manifest, runtime logs, native receipt and durable-save matrix, transaction recovery matrix, and jar contents.

### CORE-REQ-018 — separate Vault bridge interoperability

**Behavior:** Support a separately installed and reviewed hybrid bridge that registers provider identifier `vault` through the public API. In Phase 002, inspect the bridge and economy backend boundary and execute a disposable transaction proof that persists the balance effect and provider receipt in one transaction, injects interruption before and after commit, looks up the original request, and proves duplicate retry behavior. Validate its declared query, precheck, withdraw, deposit, durable receipt lookup, and idempotent retry capabilities against one exact hybrid runtime, Vault artifact, and economy plugin stack. The currently observed PixelmonEconomyBridge and FinalEconomy path does not satisfy that contract without modification, so that legacy stack remains safely refused. Under DEC-019, Phase 002 acquires a reviewed existing registrant or builds a separate test registrant and durable backend; its implementation is not an externally supplied prerequisite. FutureShops records each local request and returned provider outcome in the durable receipt audit journal under `world/data/futureshops/receipts`, but that record does not make a Vault boolean call atomic or safe to replay. Enable only surfaces whose capabilities are proven; otherwise fail closed with no generic Vault production support claim. Keep all bridge, Bukkit, Vault, hybrid, and plugin code and dependencies outside FutureShops. Production transaction-aware bridge and backend adaptation is issue 66 scope for the 3.0.0 Forge `1.20.1` beta and future `1.21.1` port. Phase 002 owns only the separate proof components and exact runtime verification described by DEC-019.
**Owner:** `CORE-PHASE-002`
**Contributors:** `CORE-PHASE-000`, `CORE-PHASE-001`, `CORE-PHASE-003`
**Dependencies:** CORE-REQ-002, CORE-REQ-004, CORE-REQ-007, CORE-REQ-009, CORE-REQ-015, CORE-REQ-016, DEC-017, DEC-019, DEC-020, EXT-004, EXT-005, EXT-006, EXT-008, EXT-009
**Lifecycle stage:** change
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- `vault` resolves only through the separate bridge; Phase 002 produces a transaction-proof result for the bridge or backend; every enabled mutation surface proves the exact capability set, one-transaction provider receipt contract, and crash recovery contract; the current unmodified hybrid stack remains refused; a missing, failed, or capability-incomplete stack leaves the server online and FutureShops fail closed; no Bukkit or Vault class, reflection string, dependency, service lookup, or bundled bridge appears in FutureShops; exact limitations are documented without claiming generic compatibility.

**Required evidence**

- `EVD-EXT-005` through `EVD-EXT-007`, bridge and backend transaction-proof fixture or exact implementation trace, interrupted-commit and retry lookup results, integration logs, dependency and jar scans, bridge registration tests, and environment manifest.

### CORE-REQ-019 — complete production validation

**Behavior:** Execute the complete deterministic and runtime matrix after all implementation and phase-owned documentation are integrated. Prove clean draining, receipt audit journal integrity under `world/data/futureshops/receipts`, unclean-start recovery, unknown-record classification, frozen ambiguity, escrow and claim conservation, no unsafe retries or balance restoration, and exact provider capability gating. Resolve failures through the owning phase requirement, rerun invalidated checks, and preserve sanitized evidence tied to the source commit and exact external artifacts.
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

**Behavior:** Update the root user documentation, maintainer documentation, documentation index, provider API reference, configuration guide, integration guides, migration guide, recovery runbook, compatibility matrix, and validation record to match implemented behavior only, including strict capabilities, lifecycle states, journal and escrow ordering, the `world/data/futureshops/receipts` receipt audit journal, clean marker, backup contents, unknown-record recovery, frozen operator workflow, the native Pixelmon receipt and durable-save contract, direct Pixelmon's mutation limitation, and the separate transaction-aware Vault bridge boundary.
**Owner:** `CORE-PHASE-003`
**Contributors:** `CORE-PHASE-000`, `CORE-PHASE-001`, `CORE-PHASE-002`
**Dependencies:** CORE-REQ-001, CORE-REQ-002, CORE-REQ-003, CORE-REQ-004, CORE-REQ-005, CORE-REQ-006, CORE-REQ-007, CORE-REQ-008, CORE-REQ-009, CORE-REQ-010, CORE-REQ-011, CORE-REQ-012, CORE-REQ-013, CORE-REQ-014, CORE-REQ-015, CORE-REQ-016, CORE-REQ-017, CORE-REQ-018, CORE-REQ-019
**Lifecycle stage:** post_change
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- A user can select `internal`, Pixelmon, or the separately installed `vault` provider correctly and can tell whether each surface is capability-enabled or safely refused; an integrator can implement the public API; an operator can drain, back up, diagnose, recover, or preserve a frozen request and an unknown receipt record without deleting data or restoring external balances; documentation clearly states the receipt audit path, its local-evidence limit, native Pixelmon receipt and durable-save rules, transaction-aware Vault bridge requirement, unknown-record recovery, no migration, restart-only selection, direct API limitation, external money item behavior, exact compatibility, and no ATM or publication.

**Required evidence**

- Documentation diff, link and example checks, runbook rehearsal, and final behavior cross check.

### CORE-REQ-021 — validated unpublished artifact

**Behavior:** Build one release candidate jar from the verified source commit, inspect its metadata and contents, calculate SHA 256 and SHA 512, associate strict safety and exact-stack validation evidence, and keep it unpublished.
**Owner:** `CORE-PHASE-003`
**Contributors:** `CORE-PHASE-000`, `CORE-PHASE-001`, `CORE-PHASE-002`
**Dependencies:** CORE-REQ-019, CORE-REQ-020, DEC-016
**Lifecycle stage:** post_change
**Production verification:** nondestructive
**Release impact:** stable release

**Acceptance criteria**

- One artifact identifies FutureShops `2.3.0`, targets the locked platform, contains the public API and native-account capability-gated Pixelmon adapter, excludes Pixelmon, Bukkit, Vault, and bridge artifacts, passes internal behavior plus native Pixelmon receipt and exact-stack capability or safe-refusal matrices, and is not published or tagged as a release.

**Required evidence**

- `EVD-ART-001`, artifact hashes, contents listing, environment manifests, and validation summary.

### CORE-REQ-022 — actual `3.0.0` continuation issue 66

**Behavior:** Plan authoring searched for duplicates, created, and read back open GitHub issue 66 immediately after the integrated plan set passed validation and before the authoring pass returned. Issue 66 covers implementation of this strict design in the existing `3.0.0` Forge `1.20.1` beta and its future Minecraft `1.21.1` port. Its actionable design must explain the central economy gate, provider capabilities, `READY`, `DRAINING`, `RECOVERING`, and `FROZEN` behavior, write-ahead states and clean marker, the durable receipt audit journal at `world/data/futureshops/receipts`, its local-evidence limit, unknown-record recovery, buy and sell custody ordering, durable claims, backup scope, no unsafe retry or automatic external balance restoration, the native Pixelmon `PlayerPartyStorage` request-receipt and durable-save pattern, the separate transaction-aware Vault bridge or backend contract, and the limits of direct Pixelmon and Vault calls. It uses the existing `3.0` beta maintenance milestone and labels `enhancement`, `forge`, `neoforge`, and `ready`. Phases 000 through 002 preserve issue 66 unchanged and open. CORE-PHASE-003 records authoring evidence without early live access to issue 66; only after `CORE-REQ-019` and `CORE-REQ-021` pass does it search again, update, and read back the same issue 66. No 3.0.0 code is implemented by this plan, and no replacement issue is created.
**Owner:** `CORE-PHASE-003`
**Contributors:** `CORE-PHASE-000`, `CORE-PHASE-001`, `CORE-PHASE-002`
**Dependencies:** CORE-REQ-019, CORE-REQ-021, DEC-015, DEC-016, EXT-007
**Lifecycle stage:** post_change
**Production verification:** nondestructive
**Release impact:** none

**Acceptance criteria**

- Issue 66 URL and number exist from this plan authoring pass; no duplicate was created; both 3.0.0 Forge 1.20.1 implementation and future 1.21.1 port subjects are explicit; the strict design topics are complete enough to implement without copying 2.3.0 code; the required milestone and labels are attached; phases 000 through 002 keep issue 66 unchanged and open; `CORE-PHASE-003` performs no early live access to issue 66 and updates and reads back issue 66 only after artifact validation; issue 66 remains open until future owner acceptance.

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
| `CORE-PHASE-000` | [`phases/plan-phase-000.md`](phases/plan-phase-000.md) | Verified external prerequisites, pinned platform, frozen capability-aware public API, and restart-only selection contract | `CORE-REQ-001`, `CORE-REQ-002`, `CORE-REQ-003` | Master and complete registered plan set accepted, repository baseline reconfirmed | `EXT-001` through `EXT-006` are classified with exact evidence, EXT-008 applies standing DEC-020 authorization and EXT-009 separates third party permissions, the direct Pixelmon API's missing mutation capabilities produce a frozen safe-refusal contract, and the platform and public API acceptance gates pass |
| `CORE-PHASE-001` | [`phases/plan-phase-001.md`](phases/plan-phase-001.md) | Complete strict economy gate, lifecycle draining and recovery, write-ahead journal, escrow and claims, capability routing, migration safety, presentation, backup, and frozen recovery | `CORE-REQ-004`, `CORE-REQ-005`, `CORE-REQ-006`, `CORE-REQ-007`, `CORE-REQ-008`, `CORE-REQ-009`, `CORE-REQ-010`, `CORE-REQ-011`, `CORE-REQ-012`, `CORE-REQ-013`, `CORE-REQ-014`, `CORE-REQ-015`, `CORE-REQ-016` | `CORE-PHASE-000` integrated and its public contract stable | Every owned requirement passes deterministic, persistence, clean and unclean shutdown, escrow conservation, failure, server, client, multiplayer, backup, recovery, and frozen-state gates with internal and fixture external providers |
| `CORE-PHASE-002` | [`phases/plan-phase-002.md`](phases/plan-phase-002.md) | Native-account capability-gated Pixelmon `9.4.0` adapter and exact separate `vault` bridge interoperability | `CORE-REQ-017`, `CORE-REQ-018` | `CORE-PHASE-001` integrated; acquisition and construction start in P002-TASK-001, P002-TASK-013, P002-TASK-014, and P002-TASK-006 without requiring their outputs at entry; reviewed inputs and EXT-008 and EXT-009 apply before dependent use | The native Pixelmon account path proves request receipts, retry deduplication, and durable saves or refuses safely; the separate Vault stack proves one-transaction provider receipts or remains refused; both exact stacks pass lifecycle, journal, escrow, restart, recovery, isolation, and packaging matrices; standard NeoForge remains clean |
| `CORE-PHASE-003` | [`phases/plan-phase-003.md`](phases/plan-phase-003.md) | Final strict-safety validation, accurate documentation, validated unpublished artifact, and post-artifact verification and update of open issue 66 | `CORE-REQ-019`, `CORE-REQ-020`, `CORE-REQ-021`, `CORE-REQ-022` | `CORE-PHASE-002` integrated with complete external evidence and issue 66 identified by the authoring `EVD-GH-001` record | Plan-wide definition of done passes, exact artifact remains unpublished, and issue 66 is updated only after artifact validation with the 3.0.0 design, verified by URL and readback, and remains open |

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
10. Run restart and crash point recovery matrices with internal, fixture external, native Pixelmon, and `vault` providers.
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
| Pixelmon `9.4.0` | Absent, exact compatible native `PlayerPartyStorage`, custom or `BankAccountProxy` account, incompatible version, startup failure, runtime failure, exact conversion, request receipt persistence, duplicate and crash recovery |
| `vault` bridge | Absent bridge, absent Vault, absent economy plugin, exact complete stack, provider service loss, plugin failure, restart, duplicate and crash recovery |

#### Surface matrix

Every row in Required behavior by surface is tested under `internal` ready, external ready, external unavailable, and recovery required where meaningful. Tests prove both the expected result and the absence of forbidden side effects.

#### Crash and idempotency matrix

Each multi-leg flow is interrupted before intent persistence, after each write-ahead state, before each provider call, after provider effect but before local outcome persistence, after each confirmed leg, before and after custody movement, before and after claim creation, before and after compensation, while draining, and across clean and unclean shutdown. Restart and retry must produce one confirmed debit or credit per logical leg, recover through a durable receipt, or enter `FROZEN` with a typed `RECOVERY_REQUIRED` result and no guessed outcome.

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
| Minecraft `1.21.1`, NeoForge `21.1.248`, exact Pixelmon `9.4.0`, native-account Pixelmon provider selected | Required for verified queries, native request receipt and durable-save mutation proof or safe refusal, custom and hybrid account refusal, clean and unclean restart behavior, and isolation proof |
| Exact reviewed hybrid stack with separately installed bridge registering `vault` | Required for exact one-transaction receipt enablement or safe-refusal proof after external gates pass |
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

1. Preserve the current world, provider data, configuration, jar set, logs, and `world/data/futureshops/receipts`.
2. Identify the selected and originating provider identifiers and exact artifact versions.
3. Inspect the receipt audit record and linked journal, custody, and claim records by stable UUID without mutating balances manually.
4. Classify unknown or integrity-invalid receipt records as `RECOVERING` or `FROZEN` before admitting monetary writes.
5. Restore the missing exact provider stack when safe and retry only through idempotent recovery.
6. Run documented compensation only when the original outcome is proven.
7. Keep claims pending when delivery cannot be proven.
8. Restore a complete known matching backup when deterministic recovery cannot proceed.

## 16. Documentation, Operations, and Release Boundaries and Gates

Tracked documentation remains canonical. At minimum, the final documentation set must cover:

* User installation and supported version matrix.
* Provider selection, `internal` default, restart behavior, and unavailable states.
* Public API registration, metadata, threading, lifecycle, request, result, idempotency, recovery, and compatibility contracts.
* Exact Pixelmon `9.4.0` installation, native `PlayerPartyStorage` mixin requirements, and the fact that Pixelmon itself is not bundled.
* Exact reviewed hybrid stack and separately installed bridge requirements, including the one-transaction provider receipt contract and no claim of generic Vault compatibility.
* Currency precision, price interpretation, overflow rejection, and absence of automatic balance migration.
* Physical bill behavior under internal and external providers.
* Complete surface behavior for shops, carts, player shops, offline proceeds, claims, pay, administration, analytics, events, and rollback.
* Operator status, logs, the durable receipt audit journal at `world/data/futureshops/receipts`, backup, provider failure, unknown-record recovery, recovery required, restore, and selection rollback procedures.
* Security assumptions, artifact provenance, hashes, license conclusions, and optional dependency isolation.
* Verification commands, environment manifests, expected results, known limitations, and the unpublished artifact identity.
* The absence of an ATM interface or command and the exclusion of publication.

Release `2.3.0` ends at a validated artifact. No GitHub release, mod platform upload, release tag, announcement, or public download is authorized. The required GitHub issue is planning and tracking output, not release publication.

## 17. Risks and Failure Boundaries

| Risk ID | Risk | Impact | Required mitigation | Blocking condition |
| --- | --- | --- | --- | --- |
| `RISK-001` | Exact Pixelmon compile inputs or required use permission remain unresolved | Adapter compilation or runtime mixin verification cannot be completed | Execute P000-TASK-002 and P002-TASK-013, verify the universal jar and interface injection recipe, record the failed probe or exact missing right, and use the developer route only for that gap | Blocks affected compile or runtime proof after the acquisition paths are exhausted; missing sources or an older MDK template alone is not a blocker |
| `RISK-002` | Pixelmon API or native save hook cannot provide exact minor units or durable idempotent outcomes | Lossy values or duplicate money are possible | Phase 002 inspects the exact artifact and implements the narrow native `PlayerPartyStorage` mixin with request receipt beside `pixelDollars`, retry deduplication, and durable save proof. Keep direct calls refused and custom and hybrid accounts unavailable. | Blocks native Pixelmon mutation enablement until the phase implementation and evidence pass, but does not block adapter implementation or query and safe-refusal work |
| `RISK-003` | Hybrid bridge or exact stack is unavailable or unsafe | `vault` interoperability cannot be validated | Execute P002-TASK-014 and obtain or build the separate bridge and backend proof in P002-TASK-006; keep unsupported legacy stacks refused | External acquisition failure blocks affected runtime proof; missing first party proof code is implementation work |
| `RISK-004` | Existing direct `BalanceManager` access bypasses provider orchestration | External mode may mutate dormant internal balances or report false values | Complete call graph and surface matrix, enforce one route, add bypass tests | Blocks `CORE-REQ-009` |
| `RISK-005` | Crash occurs between an external effect and local outcome persistence | Duplicate or lost value may occur | Require durable provider outcome lookup or a proven native Pixelmon receipt or Vault backend transaction, enter recovery required on ambiguity | Blocks `CORE-REQ-007` |
| `RISK-006` | Different provider precision changes price meaning | Operator may unintentionally alter economy scale | No conversion, validate representation, require documented price review before selection | Blocks rollout until acknowledged in procedure |
| `RISK-007` | Optional classes link when Pixelmon or hybrid APIs are absent | Standard NeoForge startup crashes | Isolate source and class loading, use exact presence and version gates, test clean jar | Blocks `CORE-REQ-001` and integrations |
| `RISK-008` | Client controls remain active from a stale readiness snapshot | Users submit unsafe or confusing operations | Server revalidation, typed rejections, synchronized presentation, reconnect tests | Blocks `CORE-REQ-005` and `CORE-REQ-012` |
| `RISK-009` | Money item registration is removed to disable use | Existing saves or inventories corrupt | Retain registration and decoding, disable mutation behavior only | Blocks `CORE-REQ-011` |
| `RISK-010` | GitHub milestone, labels, capability, or issue 66 authoring evidence are missing | Required tracking cannot satisfy scope | Preserve the completed issue 66 creation and readback evidence, and revalidate and update issue 66 only after artifact validation in Phase 003 | Blocks plan completion; never create a replacement issue during product execution |
| `RISK-011` | Reference `3.0` work is mistaken for current implementation scope | Uncontrolled scope and version conflict | Keep reference only role explicit and freeze ownership here | Requires plan revision before any imported work |
| `RISK-012` | Validation artifact differs from the artifact installed in an external environment | Evidence does not prove the delivered bytes | Hash before installation and verify hashes in every environment | Blocks `CORE-REQ-021` |

## 18. Definition of Done

The plan is complete only when every condition below is true.

The exact completion endpoint is: One fully validated and inspected unpublished FutureShops 2.3.0 jar for Minecraft 1.21.1 and NeoForge 21.1.248, proven for full internal behavior, a native exact Pixelmon 9.4.0 `PlayerPartyStorage` transaction-mixin path, and separate `vault` registration with mutation enabled only when a bridge and backend provide one durable balance and receipt transaction, otherwise refusing safely, plus the existing read-back GitHub issue 66 updated with implementation guidance for 3.0.0 Forge 1.20.1 and its future 1.21.1 port.

The exact Pixelmon 9.4.0 runtime and development inputs, disposable Pixelmon runtime, separately installed bridge candidate, exact reviewed hybrid stack, and disposable hybrid environment are available under EXT-001, EXT-002, EXT-004, EXT-005, and EXT-006, with hashes, eula=true readback, startup logs, and isolated API inspection recorded. Their native mutation, receipt, durable-save, bridge atomicity, and recovery checks remain Phase 002 verification gates. Third party artifact and development-use terms review remains unresolved under EXT-009. Execute DEC-019 acquisition and construction routes before classifying an external input as unavailable. EXT-008 is already authorized by DEC-020 and is not a missing-consent blocker. The native Pixelmon mixin and the in-phase Vault backend transaction proof are implementation work in CORE-PHASE-002, not prerequisites to begin that phase. If an exact runtime or required permission later becomes unavailable, dependent runtime scope is preserved and the result is **NOT COMPLETE — EXTERNALLY BLOCKED**. A negative capability result requires safe refusal rather than a false support claim. No substitute artifact, reduced verification, unsafe override, partial compatibility claim, or publication may bypass that state.

1. All four contiguous phases are integrated in order, and every mandatory requirement has traceable passing evidence.
2. FutureShops identifies as `2.3.0` on Minecraft `1.21.1` and NeoForge `21.1.248`, with no unrelated platform upgrade.
3. One public, documented NeoForge economy provider API is present and usable without internal implementation access.
4. `internal` remains the default, provider selection is restart only, and no missing, late, failing, or incompatible external provider can cause internal fallback.
5. The server remains online during external provider failure, all monetary mutations fail closed, browsing remains available, and pure barter remains available.
6. Provider metadata controls currency name and precision, all accepted values use exact checked integer minor units, and every surface verifies balance, precheck, withdraw, deposit, receipt lookup, and idempotent retry capabilities as applicable.
7. Every declared balance and mutation surface routes through the server-authoritative strict gate, lifecycle admission, write-ahead journal, and applicable custody boundary.
8. Multi-leg operations, retries, clean and unclean shutdowns, compensation, claims, and offline proceeds demonstrate no duplicate debit or credit, durable escrow conservation, and frozen refusal whenever an outcome cannot be proven. Every transition has a matching durable receipt audit record under `world/data/futureshops/receipts`, and unknown records force `RECOVERING` or `FROZEN`.
9. External balances are not mirrored into FutureShops, while request facts, receipt audit facts, custody, claims, and confirmed analytics remain durable. Local receipt audit records never substitute for external receipt lookup or idempotent retry.
10. No automatic balance migration occurs. Internal starting balance remains internal only. Provider and precision changes preserve independent data and require operator review.
11. With an external provider selected, money item activation, deposit, withdrawal, redemption, and future ATM mutations are disabled while registrations and existing bills remain safe. No ATM UI or command exists.
12. The bundled capability-gated Pixelmon adapter passes against exact official Pixelmon `9.4.0` artifacts, records the class and save inspection, reports the direct API's missing receipt and idempotency capabilities, enables mutations only through the exact native `PlayerPartyStorage` mixin with durable receipt and save proof, refuses custom and hybrid accounts, and the jar does not bundle Pixelmon.
13. The separately installed bridge registers `vault` and passes the Phase 002 backend transaction proof plus capability, one-transaction receipt, enablement or safe-refusal, journal, escrow, restart, and recovery checks against one exact reviewed hybrid stack, while FutureShops contains no Bukkit or Vault dependency, reflection, or bridge code. The current PixelmonEconomyBridge and FinalEconomy path remains refused unless modified to satisfy that contract.
14. Standard NeoForge client and dedicated server start from the same final jar without Pixelmon or hybrid components.
15. Focused tests, complete tests, applicable data and GameTests, build, server, client, multiplayer, restart, failure, recovery, dependency, security, jar, and diff gates pass.
16. User, API, maintainer, migration, integration, security, verification, and recovery documentation matches the validated behavior and exact artifacts.
17. `EVD-ART-001` identifies one reproducible, inspected, SHA 256 and SHA 512 hashed FutureShops `2.3.0` artifact that remains unpublished.
18. `EVD-GH-001` identifies open GitHub issue 66, created and read back immediately after plan validation, proves it remained unchanged and open through phases 000 through 002, and proves its post-artifact Phase 003 search, strict-design update, and readback for `3.0.0` Forge `1.20.1` and a future `1.21.1` port with the native Pixelmon receipt and durable-save pattern plus the transaction-aware Vault bridge or backend contract, the existing milestone, and labels. Issue 66 remains open, no 3.0.0 code is implemented here, and product execution never creates a replacement or duplicate.
19. `docs/plan/goal.md` is byte for byte unchanged.
20. No publication, release tag, mod platform upload, private data disclosure, credential use outside approved authentication, or unrelated source change occurred.

Passing internal tests without the exact Pixelmon and Vault environments is not completion. Issue 66 creation and authoring readback are completed planning evidence and may not be deferred, repeated, or replaced during product execution. Product completion also requires Phase 003 to update and read back issue 66 only after artifact validation.

## 19. Goal Creator Handoff

After the master, all four phase plans, plan index, and deterministic handoff pass validation, Goal Creator uses the following exact execution handoff without altering an existing `docs/plan/goal.md`.

```text
Mandatory boundary: CORE-REQ-001 through CORE-REQ-022 across CORE-PHASE-000 through CORE-PHASE-003, including the native Pixelmon transaction-mixin path, separate Vault bridge boundary, exact external integration evidence, the unpublished artifact, and the two-stage open issue lifecycle.
Optional/future disposition: excluded
Locked owner decisions: DEC-001, DEC-002, DEC-003, DEC-004, DEC-005, DEC-006, DEC-007, DEC-008, DEC-009, DEC-010, DEC-011, DEC-012 resolved and refined by DEC-018, DEC-013, DEC-014, DEC-015, DEC-016, DEC-017, DEC-018, DEC-019, DEC-020
Active phase: CORE-PHASE-000
Next executable action: Execute P000-TASK-001 to reconfirm repository identity, toolchain, product metadata, economy ownership, and the complete provider call graph before implementation.
Known failing checks: none at validated plan handoff; execution checks have not yet run.
Known external blockers: Third party artifact and development-use terms review under EXT-009 remains unresolved. Exact Pixelmon runtime and development inputs, disposable Pixelmon runtime, bridge candidate, reviewed hybrid stack, and disposable hybrid environment are available under EXT-001, EXT-002, EXT-004, EXT-005, and EXT-006. P002-TASK-013 and P002-TASK-014 record and consume those inputs; P002-TASK-006 obtains or builds the separate bridge and durable backend proof. Native mixin implementation, proof construction, and provisioning are executable work, not prerequisites to start themselves. EXT-008 is already authorized; set and verify eula=true under DEC-020 without another consent request.
Completion endpoint: One fully validated and inspected unpublished FutureShops 2.3.0 jar for Minecraft 1.21.1 and NeoForge 21.1.248, proven for full internal behavior, a native exact Pixelmon 9.4.0 `PlayerPartyStorage` transaction-mixin path, and separate `vault` registration with mutation enabled only when a bridge and backend provide one durable balance and receipt transaction, otherwise refusing safely, plus the existing read-back GitHub issue 66 updated with implementation guidance for 3.0.0 Forge 1.20.1 and its future 1.21.1 port.
Required evidence gates: Complete every requirement acceptance criterion and phase exit gate, classify EXT-001 through EXT-006 with exact evidence, inspect the Pixelmon 9.4.0 classes and save path, implement and prove the native PlayerPartyStorage receipt, deduplication, account classification, and durable-save path, execute the Phase 002 Vault bridge or backend transaction proof, apply EXT-008 and DEC-020 before launch and review exact third party operations under EXT-009, obtain and compile the public Pixelmon inputs, build the separate bridge and durable backend proof when needed, pass journal, escrow, draining, recovery, frozen, capability, deterministic, and runtime matrices, inspect and hash the unpublished jar, preserve the EVD-GH-001 authoring creation and readback for issue 66 through phases 000 through 002, and perform the Phase 003 search, update, and readback for that same open issue only after artifact validation.
```

Execution advances one phase at a time. It does not stack future phase work, rewrite the master as status, alter the immutable goal, or declare success before the plan-wide Definition of Done and exact endpoint are satisfied.
