# FutureShops Defect Closure and Beta Candidate Polish Plan

> **Plan ID:** PLAN-MASTER
> **Plan status:** VALIDATED
> **Project state:** EXISTING
> **Planning subject:** FutureShops defect closure and beta candidate polish for Forge 1.20.1 and NeoForge 1.21.1
> **Plan profile:** software_product
> **Contract schema:** 2

## 1. Project Identity

```text
Project: FutureShops
Requested artifact: authoritative_plan
Repository root: /mnt/hermes/projects/FutureShops
Starting branch: envy/polish_plan
Starting commit: d978a1f79d5c50efee5d91ea8cace232ac542116
Authoritative remote:
origin
https://github.com/MCEnvision/FutureShops.git
Remote ref: origin/envy/polish_plan
Remote commit: d978a1f79d5c50efee5d91ea8cace232ac542116
Secondary support ref: origin/1.21.1
Secondary support commit at intake: 247d8f6842bfa1f586e5b18a9aab67cabd3db89f
```

The default support line is Forge 1.20.1. It uses Java 17, Minecraft 1.20.1, Forge 47.4.20, Gradle 8.14.4, official Mojang mappings, and the `futureshops` mod identifier. The separately maintained NeoForge 1.21.1 line uses Java 21, Minecraft 1.21.1, NeoForge 21.1.233, and ModDevGradle 2.0.141. The two support lines share product intent but do not share source compatibility, release versions, or integration branches.

## 2. Planning Subject and Source Roles

| ID | Role | Subject | Source | Intended use |
|---|---|---|---|---|
| SRC-001 | owner_request | Owner corrections for issues 22, 25, and 32 and local verification capacity | Current EnVy request on 2026-09-01 | Removes false external blockers, accepts issue 22, classifies issue 25 as beta compatibility work, and mandates local corruption and multiplayer testing |
| SRC-002 | existing_plan | Legacy FutureShops 3.0 contract | /mnt/hermes/projects/FutureShops/FutureShops3-0Plan.MD | Preserves economy, recovery, compatibility, and verification invariants |
| SRC-003 | existing_plan | Legacy FutureShops 3.1 trade offer contract | /mnt/hermes/projects/FutureShops/FutureShops3-1TradeOffersPlan.MD | Preserves implemented editor and catalog constraints |
| SRC-004 | requirements | Issue 22 comments and accepted NeoForge blur fix | https://github.com/MCEnvision/FutureShops/issues/22 and commit bfba91f7b0c51b03d07117c4f1851c38a98f6186 | Defines the confirmed root cause, fix, evidence, owner acceptance, 1.21.1 merge, and closure work |
| SRC-005 | requirements | Issue 25 body and every current comment | https://github.com/MCEnvision/FutureShops/issues/25 | Defines the beta update compatibility investigation without a reporter acceptance blocker |
| SRC-006 | requirements | Issue 32 body and every current comment | https://github.com/MCEnvision/FutureShops/issues/32 | Defines local destructive input generation, non destructive recovery, and fault isolation |
| SRC-007 | requirements | Issue 33 body and every current comment | https://github.com/MCEnvision/FutureShops/issues/33 | Defines the bounded bulk admin shop workflow |
| SRC-008 | requirements | Issue 34 body and every current comment | https://github.com/MCEnvision/FutureShops/issues/34 | Defines locally reproducible finite stock multiplayer behavior |
| SRC-009 | repository_evidence | Repository identity, branch topology, toolchains, and current versions | /mnt/hermes/projects/FutureShops and origin branches inspected on 2026-09-01 | Grounds branch isolation, versions, and exact runtime matrices |
| SRC-010 | status | Current user and operator documentation | README.md, DOCUMENTATION.md, and docs | Defines documentation surfaces |
| SRC-011 | audit_evidence | Current implementation, tests, CI, runtime tasks, and issue branch evidence | src, GitHub Actions, origin/1.20.1 at c6709e12ca7084ee068b2497a577b8d47c12f6fd, origin/1.21.1 at 247d8f6842bfa1f586e5b18a9aab67cabd3db89f, and envy/issue_22_neoforge at bfba91f7b0c51b03d07117c4f1851c38a98f6186 | Provides exact revision evidence to rerun during execution |
| SRC-012 | status | Open dependency pull request 28 | https://github.com/MCEnvision/FutureShops/pull/28 | Keeps dependency maintenance visible without expanding product scope |
| SRC-013 | requirements | Repository execution and safety rules | /mnt/hermes/projects/FutureShops/AGENTS.md | Defines branch, verification, GitHub, recovery, security, and documentation rules |
| SRC-014 | audit_evidence | Owner supplied local verification capacity | Current EnVy request stating 64 GB workstation and 96 GB node1 availability | Establishes controlled dedicated server and multiple client testing as internal executable work |

The planning subject is the FutureShops product across its two current support lines. Issue descriptions are requirements and evidence sources. Repository state, logs, tests, pull requests, and legacy completion records describe current state. None of those artifacts independently replaces the owner-approved product contract in this plan.

### Legacy plan mapping and superseded authority

`FutureShops3-0Plan.MD` and `FutureShops3-1TradeOffersPlan.MD` remain unchanged historical records. Their implemented behavior, safety invariants, schema decisions, and verification evidence remain applicable unless this plan explicitly replaces them. Their unfinished status, old phase sequences, beta numbering history, and completion authority are superseded by `PLAN-MASTER`. They must not be edited into progress diaries or treated as a competing active plan.

The following mapping preserves every top-level legacy subject without promoting completed work back into mandatory implementation scope.

| Legacy source and sections | Classification under this plan | Canonical destination |
|---|---|---|
| 3.0 supported branch policy, beta identity, publication history, and issue maintenance records | Historical branch and release evidence | CORE-REQ-002, CORE-REQ-016, CORE-REQ-018, CORE-REQ-019 |
| 3.0 non-negotiable requirements, target architecture, escrow risks, and shared escrow system | Preserved architecture and economic invariants | Section 11, CORE-REQ-010, CORE-REQ-012, CORE-REQ-014, CORE-REQ-015 |
| 3.0 existing flow migration and payment source selection | Implemented transaction flows subject to regression and integration audit | CORE-REQ-004, CORE-REQ-006, CORE-REQ-011, CORE-REQ-012, CORE-REQ-013, CORE-REQ-014 |
| 3.0 shared market interface, Auction House, Bazaar, configuration, module disable behavior, networking, permissions, and administration | Implemented components subject to security, command, persistence, and backend audits | CORE-REQ-010, CORE-REQ-011, CORE-REQ-012, CORE-REQ-013, CORE-REQ-015 |
| 3.0 persistence, migration, edge cases, community regression gate, testing, and implementation phases | Preserved evidence and hardening expectations, with the old roadmap superseded | CORE-REQ-005, CORE-REQ-009, CORE-REQ-012, CORE-REQ-014, CORE-REQ-015, CORE-REQ-019, CORE-REQ-020 |
| 3.0 recommended defaults and issue 23 ATM, refund, recovery, admin catalog, and legacy journal requirements | Preserved operator and recovery behavior subject to renewed audit | CORE-REQ-011, CORE-REQ-012, CORE-REQ-013, CORE-REQ-014, CORE-REQ-015 |
| 3.0 completion record | Historical status evidence only | SRC-002 and CORE-REQ-002 |
| 3.1 audited behavior, terminology, required combinations, normalized models, free, sell-only, barter, and bundle behavior | Implemented product behavior retained as regression surface | CORE-REQ-002, CORE-REQ-013, CORE-REQ-014, CORE-REQ-015 |
| 3.1 catalog schema, migration, configuration layout, and module authority | Preserved schema and compatibility contracts | CORE-REQ-007, CORE-REQ-008, CORE-REQ-012, CORE-REQ-013, CORE-REQ-016 |
| 3.1 escrow, atomic execution, cart, and networking | Preserved transaction and trust-boundary contracts | CORE-REQ-010, CORE-REQ-012, CORE-REQ-013, CORE-REQ-014, CORE-REQ-015 |
| 3.1 visitor interface, bulk sell, accessibility, administrator builder, quick creation, quick-add grid, validation, conflict handling, recovery, and keyboard behavior | Implemented UI and workflow baseline, with issue 33 as the only new bounded editor feature | CORE-REQ-007, CORE-REQ-008, CORE-REQ-013, CORE-REQ-015 |
| 3.1 Player Shop convergence, limits, adjacent features, implementation phases, verification, documentation, and approval decisions | Historical implementation and acceptance evidence, with the old roadmap superseded | CORE-REQ-001, CORE-REQ-002, CORE-REQ-015, CORE-REQ-017, CORE-REQ-019 |

## 3. Purpose and Intended Outcome

FutureShops must reach a trustworthy beta candidate state rather than accumulate isolated patches. Players must be able to use shop, market, inventory, currency, claim, and recovery workflows without losing data or value. Server owners must have predictable configuration, command permissions, diagnostics, migration, backup, and recovery behavior. Maintainers must be able to prove every fix at an exact revision and close an issue only after the required real-world evidence exists.

The primary workflows are:

1. A NeoForge 1.21.1 player opens FutureShops screens without unintended blur and can navigate normally.
2. A Forge 1.20.1 server loads existing shop catalogs and presents valid offers after upgrade, restart, and reload.
3. A Forge player buys from finite stock with money, with correct item, money, stock, escrow, claim, and persistence outcomes.
4. A player with affected persistence data reconnects and recovers without deletion, duplication, or loss.
5. An administrator selects many bounded registry items, previews their generated listings, applies one shared price and stock, and commits atomically with safe conflict handling.
6. An administrator uses every command through an explicit and correctly enforced permission and confirmation contract.
7. Operators can diagnose and recover market failures through documented, non-destructive procedures.
8. Maintainers repeatedly audit both exact candidate revisions until no known repository-owned defect remains.

The intended endpoint is two prepared, unpublished candidate artifacts: Forge `3.0.0-beta.2` on `1.20.1` and NeoForge `2.2.1` on `1.21.1`. Publication is a separate owner decision.

## 4. Evidence-Based Current State

| Area | Evidence class | Finding | Evidence |
|---|---|---|---|
| Repository identity | VERIFIED | The working repository and authoritative remote are FutureShops under `MCEnvision` | Intake validation at commit `c6709e12ca7084ee068b2497a577b8d47c12f6fd` against `/mnt/hermes/projects/FutureShops` and `origin` |
| Default support line | OBSERVED | `1.20.1` is the default branch and starts this plan at `c6709e12ca7084ee068b2497a577b8d47c12f6fd` | SRC-009 |
| Secondary support line | OBSERVED | `1.21.1` exists separately at intake commit `247d8f6842bfa1f586e5b18a9aab67cabd3db89f` | SRC-009 |
| Forge version | OBSERVED | The current Forge line identifies as `3.0.0-beta.1` | Build and mod metadata in SRC-009 |
| NeoForge version | OBSERVED | The current NeoForge line identifies as `2.2.0` | Branch metadata in SRC-009 |
| Issue 22 | OBSERVED | The owner accepts the documented root cause and correction at `bfba91f7b0c51b03d07117c4f1851c38a98f6186`; fresh independent evidence, required pull request integration into `1.21.1`, merged-revision verification, and issue closure remain | SRC-001, SRC-004, and SRC-011 |
| Issue 25 | OBSERVED | Every current issue comment is part of the evidence baseline. The first migration repair exists, and the remaining question is whether a supported current-state defect exists or the report crossed an unsupported intermediate beta transition | SRC-001 and SRC-005 |
| Issue 32 | UNKNOWN | The exact root cause is not proven, but local deterministic corruption, fuzzing, crash-cut, modded-NBT sentinel, ownership-isolation, and non-destructive recovery work is mandatory and executable without reporter data | SRC-001, SRC-006, and SRC-014 |
| Issue 33 | PROPOSED | The owner has fixed the scope and interaction contract; implementation evidence does not yet exist | SRC-007 and DEC-003 |
| Issue 34 | OBSERVED | Finite-stock money purchases reportedly fail while infinite-stock purchases succeed; the report and every comment define a locally executable dedicated-server and multiple-client reproduction matrix | SRC-008 and SRC-014 |
| Legacy 3.0 and 3.1 work | OBSERVED | Both legacy plans describe implemented behavior and historical verification, but neither is a current authoritative defect-closure plan | SRC-002 and SRC-003 |
| Verification baseline | OBSERVED | Unit, build, data, GameTest, server, and client tasks exist, but they have not all been rerun at future exact candidate revisions | SRC-011 and SRC-013 |
| Dependency maintenance | OBSERVED | Dependabot pull request 28 is open and separate from the product defect contract unless it becomes necessary for branch health | SRC-012 |
| Verification capacity | OBSERVED | The 64 GB workstation is authorized for isolated dedicated servers and multiple clients; the 96 GB node1 host is an authorized temporary-server fallback. Historical EXT-001 through EXT-004 are resolved or superseded traceability records, and EXT-005 is available | SRC-001, SRC-014, DEC-007, and EXT-001 through EXT-005 |

No future phase may convert an `UNKNOWN`, `OBSERVED`, or `PROPOSED` statement into `VERIFIED` without naming the exact revision, environment, procedure, and decisive result.

## 5. Product Contract and Profile Coverage

| Profile area | Status | Source | Contract location | Rationale |
|---|---|---|---|---|
| inputs and outputs | covered | SRC-004, SRC-005, SRC-006, SRC-007, SRC-008 | Inputs, outputs, and workflows | The plan defines issue evidence, fixtures, UI inputs, authoritative mutations, and evidence outputs. |
| component architecture | covered | SRC-002, SRC-003, SRC-009, SRC-011 | Architecture and component ownership | The plan assigns client, networking, catalog, transaction, persistence, command, and release ownership. |
| state and persistence | covered | SRC-002, SRC-005, SRC-006, SRC-008 | State, persistence, migration, and recovery | All stores, files, migrations, corruption paths, and recovery boundaries are audited. |
| failure taxonomy | covered | SRC-001, SRC-004, SRC-005, SRC-006, SRC-008 | Failure taxonomy and routing | The plan distinguishes validation, compatibility, readiness, corruption, transaction, network, and recovery failures. |
| versioning | covered | DEC-005, SRC-009 | Compatibility, versioning, and branch topology | Forge 1.20.1 and NeoForge 1.21.1 remain isolated with locked versions. |
| security | covered | DEC-006, SRC-002, SRC-013 | Security and privacy contract | The whole codebase security and privacy audit remains mandatory. |
| test system | covered | SRC-001, SRC-011, SRC-013, SRC-014 | Test system and evidence matrix | Local fault injection, dedicated server, multiple clients, restart, reconnect, and exact revision evidence replace false external blockers. |
| release lifecycle | covered | DEC-001, DEC-005, SRC-009 | Candidate integration and release readiness | The endpoint is merged verified beta candidates without publication. |
| generalization | covered | DEC-002, DEC-003, SRC-007 | Scope generalization and bounded extension rules | Verified defects roll into scope while issue 33 remains bounded. |
| determinism | covered | SRC-001, SRC-002, SRC-006, SRC-007, SRC-014 | Determinism, idempotency, and reproducibility | Exact revisions, request identities, fixtures, corruption seeds, recovery proofs, and candidate hashes are mandatory. |

### Inputs, outputs, and workflows

Sections 3, 6, 10, 11, and 12 define the issue comments, local fixtures, catalog selections, inventory and transaction data, commands, packets, user-visible responses, durable outcomes, compatibility dispositions, and candidate artifacts that enter or leave the product workflows.

### Architecture and component ownership

Section 11 defines canonical component ownership, dependency direction, authoritative state, interfaces, and trust boundaries for both support lines.

### State, persistence, migration, and recovery

Sections 11, 12, and 15 define durable and transient state, schemas, concurrency, migrations, compatibility, corruption handling, backup, restore, and non-destructive recovery.

### Failure taxonomy and routing

Sections 10, 11, and 17 classify validation, compatibility, readiness, corruption, transaction, network, recovery, repository-access, and publication failures, with required terminal behavior and operator evidence.

### Compatibility, versioning, and branch topology

Sections 9, 11, and 15 bind product, loader, branch, protocol, configuration, persistence, schema, and artifact versions without cross-line integration.

### Security and privacy contract

Sections 11, 12, 14, and 17 define untrusted inputs, permissions, privacy, secrets, destructive-action limits, dependency risk, abuse cases, and final security evidence.

### Test system and evidence matrix

Sections 12 and 14 define unit, integration, runtime, compatibility, migration, security, regression, local multiplayer, corruption, recovery, and artifact evidence at exact revisions.

### Candidate integration and release readiness

Sections 13, 15, 16, and 18 define build, packaging, integration, rollback, observability, documentation, candidate proof, and the explicit prohibition on publication.

### Scope generalization and bounded extension rules

Sections 7, 9, 11, and 12 apply the rolling defect workflow consistently while keeping issue 33 and unrelated future features inside their explicit bounds.

### Determinism, idempotency, and reproducibility

Sections 11, 12, 14, and 18 require stable request and item identities, checked integer values, atomic writes, repeatable fixtures, exact revisions, dual hashes, and unchanged-revision audit convergence.

## 6. Mandatory Scope

- CORE-REQ-001 — Migrate the two legacy plans into one authoritative, traceable plan set without rewriting legacy artifacts or reopening completed features.
- CORE-REQ-002 — Establish an exact issue, branch, test, CI, configuration, reproduction, and evidence baseline before repair.
- CORE-REQ-003 — Verify the accepted issue 22 background-pass fix, merge commit `bfba91f7b0c51b03d07117c4f1851c38a98f6186` only into `1.21.1` through the required pull request workflow, verify the merged revision, and close issue 22.
- CORE-REQ-004 — Resolve issue 25 through a local beta transition and current-state matrix, fixing any supported current repository defect or producing an evidence-backed owner-approved beta compatibility disposition and recovery documentation.
- CORE-REQ-005 — Resolve issue 32 through local deterministic corruption, fuzzing, crash cuts, modded NBT sentinels, ownership isolation, and non-destructive recovery without player-data deletion.
- CORE-REQ-006 — Resolve issue 34 on an isolated local dedicated Forge server with multiple clients, without duplication or value loss.
- CORE-REQ-007 — Implement the bounded KISS bulk admin shop listing workflow from issue 33.
- CORE-REQ-008 — Make bulk listing conflict handling explicit, atomic, and preservation-safe.
- CORE-REQ-009 — File, link, repair, verify, and converge every newly verified repository-owned defect.
- CORE-REQ-010 — Complete the security and privacy audit and repair every verified defect.
- CORE-REQ-011 — Audit every admin command and permission path and repair every verified defect.
- CORE-REQ-012 — Audit every persistence and database path and repair every verified defect non-destructively.
- CORE-REQ-013 — Audit backend integration and failure handling and repair every verified defect.
- CORE-REQ-014 — Preserve all economic, escrow, claims, authority, compatibility, and no-loss invariants.
- CORE-REQ-015 — Provide deterministic, exact-revision focused, unit, data, GameTest, build, JAR, server, client, local multiplayer, restart, reconnect, reload, rollback, corruption, and fault-injection verification.
- CORE-REQ-016 — Keep both support lines isolated, compatible, and correctly versioned.
- CORE-REQ-017 — Reconcile all affected documentation, operations, GitHub, and project tracking with verified behavior.
- CORE-REQ-018 — Prepare and inspect exact unpublished Forge `3.0.0-beta.2` and NeoForge `2.2.1` candidate artifacts with integrity evidence.
- CORE-REQ-019 — Repeat all mandatory audits at exact candidate revisions until no known repository-owned defect remains.
- CORE-REQ-020 — Close and complete tracking only after the owner-accepted disposition, merged change where required, local deterministic evidence, green exact-revision checks, and mandatory issue-comment evidence pass, without a reporter-acceptance prerequisite.

## 7. Optional / Future Scope

The locked disposition is `excluded`.

- FUT-001 — Publishing to CurseForge, Modrinth, or GitHub Releases and posting an announcement requires separate explicit authorization.
- FUT-002 — Promotion to a stable public release is excluded.
- FUT-003 — Material unrelated enhancements or new subsystems outside active issues, issue 33, or verified audit defects are excluded.
- FUT-004 — Fuzzy item matching, arbitrary NBT paths, broad tag expression languages, and unconditional bulk replacement are excluded.
- FUT-005 — Distributed live market state and direct external-storage listings without deterministic transaction receipts are excluded.

## 8. Non-Goals

- NG-001 — This Plan Creator pass does not implement product code, alter ordinary documentation, or perform releases.
- NG-002 — This plan does not authorize CurseForge, Modrinth, GitHub Release, announcement, or stable publication.
- NG-003 — No repair or verification may delete player data, journals, checkpoints, ledgers, custody, claims, or worlds.
- NG-004 — No UI workaround may weaken server authority, maintenance mode, readiness, escrow conservation, idempotency, claims access, permissions, or dedicated-server safety.
- NG-005 — Forge and NeoForge fixes must not cross support lines without a separately required compatible implementation.
- NG-006 — Issue 33 must not add fuzzy matching, arbitrary NBT query paths, unconditional replacement, or a second competing item selector.
- NG-007 — Compilation, mocks, or external suggestions alone do not authorize issue closure.
- NG-008 — Defect repair must not silently update pinned Minecraft, loader, Java, mappings, Gradle, or platform boundaries.

## 9. Owner Decisions

### DEC-001 — Completion endpoint

**Status:** RESOLVED
**Selected choice:** All scoped and audit discovered repository defects are fixed or receive an evidence backed owner approved compatibility disposition, merged and locally verified as applicable, closed, and exact beta candidates prepared. Publication remains excluded.
**Rationale:** Completion means a trustworthy candidate state, not only locally compiling patches.
**Affected requirements:** CORE-REQ-003, CORE-REQ-004, CORE-REQ-005, CORE-REQ-006, CORE-REQ-007, CORE-REQ-008, CORE-REQ-009, CORE-REQ-010, CORE-REQ-011, CORE-REQ-012, CORE-REQ-013, CORE-REQ-014, CORE-REQ-015, CORE-REQ-016, CORE-REQ-017, CORE-REQ-018, CORE-REQ-019, CORE-REQ-020
**Supersedes:** Legacy completion and publication endpoints in SRC-002 and SRC-003

### DEC-002 — Rolling defect scope

**Status:** RESOLVED
**Selected choice:** Yes. Deduplicate and file every verified defect before repair, then rerun final audits until clean.
**Rationale:** The owner selected convergence on a clean known-defect set instead of freezing scope to the intake snapshot.
**Affected requirements:** CORE-REQ-002, CORE-REQ-009, CORE-REQ-010, CORE-REQ-011, CORE-REQ-012, CORE-REQ-013, CORE-REQ-019, CORE-REQ-020
**Supersedes:** None

### DEC-003 — Issue 33 KISS behavior

**Status:** RESOLVED
**Selected choice:** Reuse the searchable grid, allow optional exact canonical NBT, preview all generated listings, apply shared price and stock atomically, skip and report existing entries by default, and require explicit per item replacement.
**Rationale:** This gives administrators fast bulk entry without creating an ambiguous query or destructive replacement system.
**Affected requirements:** CORE-REQ-007, CORE-REQ-008
**Supersedes:** Any broader interpretation of issue 33

### DEC-004 — Issue evidence and closure

**Status:** RESOLVED
**Selected choice:** Issue comments are mandatory evidence. Reporter acceptance is not an endpoint prerequisite. Issue 22 has explicit owner acceptance. Issue 25 is evaluated under beta compatibility policy with local transition fixtures. Issues 32 and 34 require local deterministic fault and multiplayer reproduction. Missing reporter artifacts may improve a fixture but cannot be labeled an external blocker.
**Rationale:** Repository-controlled evidence and explicit owner decisions must drive closure without turning reporter latency into a false product blocker.
**Affected requirements:** CORE-REQ-002, CORE-REQ-003, CORE-REQ-004, CORE-REQ-005, CORE-REQ-006, CORE-REQ-015, CORE-REQ-020
**Supersedes:** The reporter-acceptance and missing-artifact blocker policy previously recorded under DEC-004

### DEC-005 — Versions and support branches

**Status:** RESOLVED
**Selected choice:** Forge work ships together as 3.0.0-beta.2 on 1.20.1. Issue 22 ships as 2.2.1 on 1.21.1. No cross branch merge.
**Rationale:** The two supported runtimes have independent compatibility and release histories.
**Affected requirements:** CORE-REQ-003, CORE-REQ-004, CORE-REQ-005, CORE-REQ-006, CORE-REQ-007, CORE-REQ-008, CORE-REQ-010, CORE-REQ-011, CORE-REQ-012, CORE-REQ-013, CORE-REQ-014, CORE-REQ-015, CORE-REQ-016, CORE-REQ-017, CORE-REQ-018, CORE-REQ-019
**Supersedes:** Any intermediate Forge beta numbering implied by historical messages

### DEC-006 — Mandatory audits

**Status:** RESOLVED
**Selected choice:** Security and privacy, every admin command, all persistence and database paths, and full backend integration and failure handling must be audited, repaired, and rerun clean.
**Rationale:** Known reports cross trust, persistence, transaction, and lifecycle boundaries and require systematic closure.
**Affected requirements:** CORE-REQ-009, CORE-REQ-010, CORE-REQ-011, CORE-REQ-012, CORE-REQ-013, CORE-REQ-014, CORE-REQ-015, CORE-REQ-019, CORE-REQ-020
**Supersedes:** None

### DEC-007 — Local runtime capacity

**Status:** RESOLVED
**Selected choice:** Use the 64 GB workstation for isolated dedicated servers and multiple clients by default. Use the 96 GB node1 host for a temporary isolated server when it improves capacity or repeatability. Controlled multiplayer is normal internal verification.
**Rationale:** The available local capacity makes corruption, crash, restart, reconnect, and multiplayer verification executable without an external environment dependency.
**Affected requirements:** CORE-REQ-005, CORE-REQ-006, CORE-REQ-012, CORE-REQ-013, CORE-REQ-015, CORE-REQ-019
**Supersedes:** The unavailable controlled-multiplayer assumption previously recorded under EXT-004

## 10. External Prerequisites

| ID | Prerequisite | Affected requirements | Availability | Authorization | Required external action |
|---|---|---|---|---|---|
| EXT-001 | Historical issue 22 acceptance gate, now resolved by explicit owner acceptance | CORE-REQ-003, CORE-REQ-020 | available | not required | Retain owner acceptance, existing evidence, and fresh merged-revision verification as historical traceability |
| EXT-002 | Historical issue 25 reporter gate, superseded by owner beta compatibility disposition and local transition testing | CORE-REQ-004, CORE-REQ-020 | available | not required | Retain every issue comment, execute the local beta transition matrix, and record a defect fix or owner-approved compatibility disposition |
| EXT-003 | Historical issue 32 evidence gate, superseded by mandatory local corruption and recovery generation | CORE-REQ-005, CORE-REQ-012, CORE-REQ-015, CORE-REQ-020 | available | authorized | Retain deterministic corruption seeds, ownership-level isolation, and non-destructive recovery proof |
| EXT-004 | Historical multiplayer environment gate, resolved by owner supplied local hardware | CORE-REQ-006, CORE-REQ-013, CORE-REQ-015, CORE-REQ-019 | available | authorized | Use the isolated 64 GB workstation environment or the authorized 96 GB node1 fallback and retain conservation evidence |
| EXT-005 | Authenticated EnVisione GitHub access | CORE-REQ-002, CORE-REQ-009, CORE-REQ-017, CORE-REQ-019, CORE-REQ-020 | available | authorized | Maintain issue, branch, pull request, check, evidence, and closure state under the authoritative repository |

### EXT-001 — Historical NeoForge issue 22 acceptance gate

**Kind:** other
**Mandatory for endpoint:** no
**Blocked-plan approval:** no

This historical gate is resolved by the owner's explicit acceptance of the root cause and correction at `bfba91f7b0c51b03d07117c4f1851c38a98f6186`. Retain that acceptance, the existing root-cause, test, client-smoke, and artifact-hash evidence, plus fresh independent verification at the exact merged `1.21.1` revision. Reporter acceptance is not an active dependency or endpoint gate.

### EXT-002 — Historical Forge issue 25 reporter gate

**Kind:** other
**Mandatory for endpoint:** no
**Blocked-plan approval:** no

This historical reporter gate is superseded by DEC-004. Every issue comment remains mandatory evidence. Execute a local beta transition and current-state matrix. Fix any supported current repository defect. If no supported current defect exists, produce an evidence-backed compatibility disposition, obtain owner approval, and document supported update and recovery behavior before closure. Reporter retest is not an active dependency or endpoint gate.

### EXT-003 — Historical issue 32 external-evidence gate

**Kind:** other
**Mandatory for endpoint:** no
**Blocked-plan approval:** no

This historical gate is superseded by mandatory local evidence generation. Use deterministic corruption seeds, fuzzing, crash cuts, modded NBT sentinels, owned-field mutation, unrelated-field preservation, restart, reconnect, repeated recovery, and exact fixture hashes to isolate the FutureShops-owned boundary. Missing reporter data is not a blocker and cannot weaken non-destructive recovery proof.

### EXT-004 — Historical controlled Forge multiplayer environment gate

**Kind:** other
**Mandatory for endpoint:** no
**Blocked-plan approval:** no

This historical environment gate is resolved by DEC-007. Use an isolated dedicated Forge 1.20.1 server and at least two independent clients on the 64 GB workstation by default. The 96 GB node1 host is an authorized temporary isolated-server fallback. Evidence must include exact revisions, fixtures, logs, balances, inventories, stock, request identities, claims, escrow, persistence, restart, reconnect, and a zero-delta conservation report.

### EXT-005 — Authenticated EnVisione GitHub access

**Kind:** other
**Mandatory for endpoint:** yes
**Blocked-plan approval:** no

Required evidence is that the authenticated identity is EnVisione, the remote is MCEnvision FutureShops, and issue and pull request state is synchronized at exact revisions. Every discovered defect must retain its duplicate search, issue, implementation, evidence, and closure links. Required branch and pull request checks must be green at each exact merged revision.

EXT-001 through EXT-004 remain only as historical traceability IDs and are not active dependencies, blockers, or endpoint gates. Their required evidence has moved into repository-controlled requirement and phase verification. EXT-005 is available and authorized; a later loss of that access blocks only the GitHub operations that consume it and must not be relabeled as an issue 22, 25, 32, or 34 evidence gap.

## 11. Architecture and Ownership Boundaries

### Support-line ownership

| Support line | Canonical branch | Candidate version | Toolchain | Scope |
|---|---|---|---|---|
| Forge | `1.20.1` | `3.0.0-beta.2` | Java 17, Forge 47.4.20, Gradle 8.14.4 | Issues 25, 32, 33, 34 and all applicable Forge audit repairs |
| NeoForge | `1.21.1` | `2.2.1` | Java 21, NeoForge 21.1.233, ModDevGradle 2.0.141 | Issue 22 and only independently required NeoForge audit corrections |

Each support-line change starts from its latest approved branch head, uses its own `envy/` work branch and pull request, passes that line's required checks, and merges only through GitHub. A fix may be ported only after proving the receiving line is affected and implementing it against that line's APIs and persistence contract. No merge or cherry-pick may accidentally transfer loader-specific build, registry, networking, or client code.

### Runtime components and dependency direction

| Component | Authority and responsibilities | Permitted dependencies | Prohibited behavior |
|---|---|---|---|
| Client presentation | Screens, custom widgets, navigation state, local drafts, rendering, input, and server-supplied snapshots | Stable client DTOs and validated server responses | Authoritative money, stock, listing, permission, module, custody, or claim decisions |
| Network boundary | Payload schemas, protocol compatibility, direction, decoding bounds, route nonces, request identities, and response correlation | Client DTOs and authoritative server handlers | Trusting client identity, price, stock, permission, ownership, completion state, or unbounded data |
| Command boundary | Syntax, permission checks, confirmation gates, target resolution, feedback, audit context, and dispatch | Authoritative server services | Direct mutation that bypasses readiness, escrow, idempotency, validation, audit, or recovery |
| Shop and catalog services | Listing validation, catalog snapshots, conflict policy, bulk preview, atomic writes, reload, and availability | Registry validation, configuration, transaction services, persistence adapters | Partial bulk commits, silent replacement, invalid entries blocking unrelated safe recovery, or client-authored truth |
| Economy and transaction services | Checked integer totals, payment sources, stock reservations, request idempotency, transaction state, and compensation | Escrow, custody, provider adapters, claims, durable records | Floating-point authority, duplicated commits, silent fallback, or uncompensated partial mutation |
| Escrow, custody, and claims | Journal lineage, checkpoints, ledger, exact item custody, delivery proof, claims, recovery, and maintenance state | Versioned persistence and bounded schedulers | Discarding unknown data, expiring claims, deleting evidence, or resuming after failed verification |
| Market services | Auction House and Bazaar listings, orders, bids, matching, fees, expiry, claims, and module lifecycle | Economy, escrow, configuration, authoritative schedulers | Client-authoritative matching, hidden module mismatch, inaccessible claims, or state mutation while not ready |
| Persistence and configuration | SavedData, JSON, TOML, NBT, atomic replacement, migration, backup, reload, validation, and last-known-good state | Stable codecs and repository-defined storage roots | Arbitrary paths, partial non-atomic writes, silent schema loss, unsafe concurrent mutation, or destructive repair |
| Evidence and GitHub workflow | Duplicate search, issue creation, reproduction packet, implementation links, verification results, review, merge, and closure | Exact revisions and retained test artifacts | Repairing a newly discovered defect before filing it or closing without mandatory evidence |

Common and server initialization must not load client classes. The logical server owns all economic and marketplace state. Optional integrations remain isolated behind runtime checks and must not prevent startup when absent.

### Cross-phase interface contracts

1. **Defect evidence packet.** Every known or discovered defect has one canonical issue, affected support line and version, severity, prerequisites, exact reproduction or explicit evidence gap, expected and actual behavior, relevant sanitized state, suspected ownership boundary, acceptance criteria, and links to tests and changes.
2. **Duplicate-before-repair gate.** Before editing for an audit-discovered defect, search open and closed issues by behavior, component, exception, and identifier. Reuse and enrich a matching issue or create one new issue. Record the search result. Security vulnerabilities that require confidential handling use the repository's private vulnerability process rather than a public exploit description.
3. **Change evidence packet.** Each repair records the failing regression first where feasible, exact changed revision, affected schemas and interfaces, focused test result, complete line-specific verification, security implications, migration and rollback implications, and unresolved external evidence.
4. **Persistence inventory.** The persistence audit maintains a complete matrix of owner, path or SavedData identity, schema version, writer, reader, threading model, atomicity, integrity check, migration, backup, restore, corruption response, privacy class, and tests. The matrix is documentation and verification evidence, not a new runtime database.
5. **Command inventory.** The command audit maintains every literal command path, permission level, sender type, argument bounds, confirmation requirement, mutation service, idempotency behavior, success and error output, logging, and regression evidence.
6. **Candidate verification packet.** Each support line records exact source revision, toolchain, commands, decisive results, runtime environments, issue acceptance, open-blocker state, JAR identity, internal metadata, dependency contents, SHA-256, SHA-512, and source-revision manifest.

### State, persistence, and economic invariants

- Authoritative money values are signed integer minor units. Parsing and display may accept decimal user input, but all authoritative totals use checked integer arithmetic.
- Every value mutation runs on the logical server and carries a stable request UUID through validation, reservation, custody, journal, commit, delivery, claim, retry, and audit paths.
- The journal, checkpoints, ledger, custody, claims, maintenance state, and replay records form one recovery lineage. A repair may not sever or rewrite that lineage without a versioned compatible migration and recovery proof.
- Items and currency move through prepare, durable custody, commit, delivery, and durable claim or compensation. No phase may introduce a state in which both source and destination own value or neither can recover it.
- Claims do not expire and remain reachable while a module is frozen, draining, disabled, or recovering.
- Item identity uses registry identity plus canonical exact NBT only where exact NBT is explicitly selected. Unrelated modded inventory normalization must not invalidate or overwrite untouched slots.
- Configuration and catalog reloads validate a complete candidate snapshot and preserve the last valid snapshot on failure.
- Writes that replace files or catalog snapshots are atomic, bounded to the FutureShops storage root, crash-safe at the documented guarantee, and recoverable from a matching backup.
- Unknown or older data is migrated through an explicit version path or retained for recovery. It is never silently discarded.

### Failure taxonomy

| Failure class | Required behavior | Evidence route |
|---|---|---|
| Validation | Reject before mutation, identify the exact field, value class, and stable code | Focused test and UI or command result |
| Permission or identity | Reject before data access or mutation, log bounded actor and target context | Command or packet permission matrix |
| Readiness or module lifecycle | Route to an allowed view or actionable unavailable state without lying about server state | Restart, delayed readiness, enable, disable, and reconnect tests |
| Network protocol | Reject wrong side, malformed, oversized, stale, replayed, or unauthorized payloads | Codec, packet, and multiplayer tests |
| Inventory or stock conflict | Revalidate authoritative state, release reservations, and return a stable conflict without value loss | Concurrent and retry tests |
| Economy provider | Fail closed, retain recoverable custody, and expose a claim or retry path | Provider fault injection and conservation report |
| Persistence or integrity | Enter bounded recovery or maintenance, preserve evidence, and refuse unsafe mutation | Corruption, partial-write, restart, and recovery tests |
| Migration or reload | Preserve the last valid state, report exact source and field, and provide non-destructive remediation | Legacy fixtures and reload tests |
| External evidence | Keep the requirement and issue open, request exact evidence, and continue only independent work | EXT record and issue comment |
| Publication authorization | Stop before upload, announcement, tag publication, or stable designation | Owner authorization record, outside this plan |

### Issue 33 bounded design contract

The existing searchable item grid is the only bulk selector. It enumerates a bounded server-approved registry snapshot and supports search by the existing safe identifiers. Selection may use registry identity alone or one canonical exact NBT identity per selected item. The preview lists every generated listing, resolved display name and identifier, selected exact-NBT state, shared price, shared stock, and conflict action before any write.

The default conflict action is `skip`, reported per item. Replacement is opt-in per conflicting item and may change only fields owned by the bulk operation: selected item identity, exact-NBT selection, shared price, and shared stock. Unrelated descriptions, categories, permissions, schedules, bundles, limits, or metadata remain unchanged. Validation is all-or-nothing, and the catalog file and in-memory snapshot change atomically only after every selected operation passes. Failure leaves both unchanged and identifies every blocking item.

## 12. Requirements

### CORE-REQ-001 — Authoritative legacy plan migration

**Behavior:** `plan.md`, its registered phase plans, index, and deterministic handoff become the only authoritative unfinished-work contract while both legacy plans remain byte-for-byte unchanged and losslessly mapped.
**Owner:** Plan governance
**Contributors:** Documentation and verification
**Dependencies:** None
**Lifecycle stage:** readiness
**Production verification:** none
**Release impact:** none

**Acceptance criteria**

- Every top-level subject in each legacy plan maps to preserved architecture, current evidence, a mandatory requirement, or an explicit historical classification.
- No completed legacy feature becomes mandatory implementation merely because it is documented.
- No old roadmap, completion record, or beta history competes with this plan's authority.

**Required evidence**

- Byte comparison proves `FutureShops3-0Plan.MD` and `FutureShops3-1TradeOffersPlan.MD` did not change during plan execution.
- Plan validation proves one contiguous registered phase set and one canonical owner for every mandatory requirement.

### CORE-REQ-002 — Exact baseline and evidence inventory

**Behavior:** Before repair, the repository has an exact, sanitized baseline for support branches, versions, every current issue comment, configurations, local fixtures, candidate changes, tests, CI, dependency state, reproduction status, and evidence gaps.
**Owner:** Repository verification
**Contributors:** Issue triage, build system, runtime harness
**Dependencies:** CORE-REQ-001, EXT-005
**Lifecycle stage:** readiness
**Production verification:** nondestructive
**Release impact:** none

**Acceptance criteria**

- Issues 22, 25, 32, 33, and 34 each have an up-to-date defect evidence packet.
- Exact branch heads, toolchains, current versions, tests, CI, open reviews, dependency changes, and dirty local files are recorded without modifying unrelated work.
- Historical EXT-001 through EXT-004 are classified as resolved or superseded traceability, local verification capacity is recorded, and no reporter artifact is misclassified as an endpoint blocker.

**Required evidence**

- Read-only Git and GitHub preflight at the named starting revisions.
- Baseline execution of applicable focused tests and build tasks on each support line, with failures retained as evidence.
- Sanitized reproduction inventory and every current issue comment attached or linked from the relevant issue packets.

### CORE-REQ-003 — NeoForge issue 22 blur correction

**Behavior:** FutureShops screens on NeoForge 1.21.1 do not introduce unintended background blur and retain intended rendering and navigation. The owner-accepted root cause and correction at `bfba91f7b0c51b03d07117c4f1851c38a98f6186` are independently rerun, integrated only into `1.21.1` through the required pull request workflow as `2.2.1`, verified at the exact merged revision, and used to close issue 22.
**Owner:** NeoForge client presentation
**Contributors:** NeoForge build, runtime verification, issue triage
**Dependencies:** CORE-REQ-002, CORE-REQ-016
**Lifecycle stage:** change
**Production verification:** nondestructive
**Release impact:** none

**Acceptance criteria**

- The accepted candidate commit is independently reviewed and rerun against the affected screen lifecycle, retaining the correction only if it fixes the confirmed root cause without global rendering side effects.
- Unit, build, and client smoke evidence passes at the exact merged `1.21.1` revision.
- The correction merges only through the required pull request workflow into `1.21.1`, remote ancestry contains the merge, and issue 22 closes with the owner acceptance and exact merged-revision evidence recorded in its comments.

**Required evidence**

- A regression test or deterministic screen lifecycle inspection that fails before and passes after the change.
- Java 21 NeoForge test, build, JAR inspection, and client smoke results.
- Issue 22 comments linking owner acceptance, commit `bfba91f7b0c51b03d07117c4f1851c38a98f6186`, the line-specific pull request, exact merge, green checks, and merged-revision verification.

### CORE-REQ-004 — Forge issue 25 beta compatibility and catalog disposition

**Behavior:** Every issue 25 comment is incorporated into a local beta transition and current-state matrix. Any supported current repository defect is repaired so valid catalogs remain available after startup, restart, and reload. If the failure exists only in an unsupported intermediate beta state, an evidence-backed compatibility disposition receives explicit owner approval and documents safe update and recovery behavior without destructive deletion.
**Owner:** Shop catalog service
**Contributors:** Configuration, migration, server networking, UI snapshots, issue triage
**Dependencies:** CORE-REQ-002, CORE-REQ-016
**Lifecycle stage:** change
**Production verification:** nondestructive
**Release impact:** migration

**Acceptance criteria**

- A deterministic matrix covers supported current state, documented direct upgrade routes, relevant intermediate betas, the first migration repair, malformed entries, removed registry items, startup, restart, and reload.
- Any defect reachable from a supported current state is repaired at its authoritative boundary with last-known-good behavior and actionable invalid-entry diagnostics.
- If no supported current defect is present, the evidence identifies the unsupported transition precisely, the owner explicitly approves the compatibility disposition, and recovery documentation gives a non-destructive route to a supported current state.

**Required evidence**

- Before-and-after catalog, migration, readiness, packet, and UI traces for every matrix row, plus all issue 25 comments.
- Focused migration and reload regressions plus dedicated-server and client tests.
- The merged repair evidence or owner-approved beta compatibility disposition, recovery documentation, and final issue 25 closure comment.

### CORE-REQ-005 — Forge issue 32 player-data integrity and recovery

**Behavior:** FutureShops cannot make player data unusable through its persistence, inventory proof, attachment, transaction, claim, or recovery paths. Local deterministic corruption, structured fuzzing, crash cuts, modded NBT sentinels, and ownership-isolation fixtures identify and repair any FutureShops-owned cause, and every generated affected state recovers without deleting player data.
**Owner:** Persistence recovery subsystem
**Contributors:** Transactions, inventory proof, networking, player lifecycle, issue triage
**Dependencies:** CORE-REQ-002, CORE-REQ-016
**Lifecycle stage:** change
**Production verification:** nondestructive
**Release impact:** migration

**Acceptance criteria**

- Deterministic seed-identified fixtures exercise malformed, truncated, oversized, old, newer, unknown, duplicate, cross-mod, partial-write, and crash-cut player and FutureShops state and identify the first FutureShops-owned boundary that can make data unusable.
- The fixed line reads and preserves compatible old state, repairs only FutureShops-owned data, and leaves unrelated player fields and modded inventory untouched.
- Restart, reconnect, repeated transaction, and recovery cycles do not recreate the invalid state, duplicate value, or require deletion.

**Required evidence**

- Seed, fixture hash, crash point, and before-and-after field-level proof with modded NBT sentinels showing owned fields changed only as intended and unrelated state remained semantically unchanged.
- Regression, fuzz, and crash-cut tests for serialization, player lifecycle, recovery, exact item proof, ownership isolation, and repeated restart.
- Dedicated-server and multiplayer reconnect evidence on the exact Forge candidate.

### CORE-REQ-006 — Forge issue 34 finite-stock money transaction correction

**Behavior:** A valid finite-stock money purchase follows the same authoritative transaction guarantees as infinite stock while atomically decrementing stock, charging money once, delivering once, and producing a durable claim on delivery failure.
**Owner:** Forge shop transaction service
**Contributors:** Stock reservation, economy provider, escrow, networking, persistence, diagnostics
**Dependencies:** CORE-REQ-002, CORE-REQ-016
**Lifecycle stage:** change
**Production verification:** nondestructive
**Release impact:** none

**Acceptance criteria**

- An isolated dedicated Forge server and at least two independent clients reproduce the finite-stock failure and prove why infinite stock follows a different successful path.
- Success, insufficient funds, stale stock, concurrent buyers, full inventory, provider failure, disconnect, retry, and restart preserve conservation and idempotency.
- Failures produce one bounded actionable record with request, listing, stock, transaction, and recovery context without exposing secrets or private NBT.

**Required evidence**

- Focused transaction, stock, escrow, replay, and persistence regressions.
- Local multiplayer trace and conservation report from the 64 GB workstation, or the authorized temporary 96 GB node1 server when used.
- Restart and reconnect verification at the exact Forge candidate revision.

### CORE-REQ-007 — KISS bulk admin listing creation

**Behavior:** An administrator can select multiple bounded registry items in the existing searchable grid, optionally attach one exact canonical NBT identity, set one shared decimal price and stock, review every generated listing, and commit them atomically.
**Owner:** Admin shop editor
**Contributors:** Networking, registry snapshot, validation, persistence, client presentation
**Dependencies:** CORE-REQ-002, CORE-REQ-016
**Lifecycle stage:** change
**Production verification:** nondestructive
**Release impact:** none

**Acceptance criteria**

- The existing grid remains the sole selector and never presents an unbounded or client-invented registry set.
- Price input displays and parses `1.00` as one currency unit while the server stores checked minor units; stock is bounded by the existing validated capacity rules.
- Preview exactly matches the proposed atomic write, including identifiers, exact-NBT state, values, and conflict actions.
- Invalid input, stale registry state, permission loss, disconnect, or write failure leaves the catalog and in-memory snapshot unchanged.

**Required evidence**

- Unit tests for selection, canonical NBT, decimal parsing, bounds, preview generation, and request validation.
- Client and dedicated-server workflow tests for selection, preview, commit, cancellation, permission change, and failed atomic write.
- JAR and translation inspection for the complete user-facing workflow.

### CORE-REQ-008 — Safe bulk conflict handling

**Behavior:** Existing listings are skipped and reported by default; replacement requires an explicit per-item choice and changes only item identity, exact-NBT selection, shared price, and shared stock while preserving unrelated fields.
**Owner:** Forge shop catalog service
**Contributors:** Admin editor, persistence, validation
**Dependencies:** CORE-REQ-007
**Lifecycle stage:** change
**Production verification:** nondestructive
**Release impact:** migration

**Acceptance criteria**

- Duplicate identity comparison is deterministic for registry-only and exact-NBT listings.
- The preview distinguishes create, skip, and explicit replace for every item.
- One blocking replacement or persistence failure rolls back the complete batch.
- Replacement preserves descriptions, categories, permissions, schedules, bundles, limits, and unknown compatible fields not owned by the bulk operation.

**Required evidence**

- Property and fixture tests for duplicate detection, field preservation, atomic rollback, retry, and deterministic ordering.
- Before-and-after catalog diff proving only owned fields changed.
- Runtime editor test with mixed create, skip, and replace selections.

### CORE-REQ-009 — Rolling defect intake and convergence

**Behavior:** Every newly verified repository-owned defect is deduplicated, filed before repair, linked to evidence and implementation, regression tested, and retained in the candidate closure set.
**Owner:** Repository issue workflow
**Contributors:** Every implementation and audit owner
**Dependencies:** CORE-REQ-002, EXT-005
**Lifecycle stage:** continuous
**Production verification:** nondestructive
**Release impact:** none

**Acceptance criteria**

- No audit-discovered defect receives a code repair before the duplicate search and issue record exist.
- Confidential exploitable findings use private vulnerability reporting and do not expose exploit details publicly.
- Material unrelated feature requests remain outside execution and trigger `PLAN_REVISION_REQUIRED` instead of silent scope growth.
- Final closure includes every issue added by this contract.

**Required evidence**

- GitHub query and issue links for every discovered defect.
- Traceability from each issue to failing evidence, change, regression, merged revision, and closure proof.
- Final issue inventory with no unclassified finding.

### CORE-REQ-010 — Security and privacy closure

**Behavior:** FutureShops has no known leak, exploitable backdoor, unsafe packet or command trust, secret exposure, arbitrary path access, unsafe deserialization, permission bypass, duplication path, privacy violation, or dependency packaging vulnerability within supported scope.
**Owner:** Security review
**Contributors:** Networking, commands, persistence, transactions, build and packaging
**Dependencies:** CORE-REQ-002, CORE-REQ-009
**Lifecycle stage:** change
**Production verification:** nondestructive
**Release impact:** none

**Acceptance criteria**

- Every packet, command, path, codec, NBT and JSON decoder, permission, transaction boundary, log, configuration input, optional dependency, and packaged dependency is inventoried and reviewed.
- Untrusted sizes, identities, registry values, paths, state transitions, targets, and authority are bounded and validated before mutation.
- Logs and public evidence exclude credentials, private raw player data, sensitive NBT, and exploit-enabling detail.
- Every verified finding follows CORE-REQ-009, is repaired, and is retested; the exact candidates rerun clean.

**Required evidence**

- Scoped threat model and trust-boundary matrix for both support lines.
- Static source and dependency inspection, packet and command abuse tests, path traversal and deserialization tests, permission tests, duplication and replay tests, and final JAR inspection.
- Clean final security review at each exact candidate revision or an explicit confidential blocker that prevents completion.

### CORE-REQ-011 — Admin command and permission closure

**Behavior:** Every FutureShops admin command and subcommand has correct registration, syntax, permission, sender and target validation, confirmation, server authority, idempotency, feedback, audit context, and recovery behavior.
**Owner:** Command subsystem
**Contributors:** Authoritative services, permissions, localization, documentation, security review
**Dependencies:** CORE-REQ-002, CORE-REQ-009, CORE-REQ-010
**Lifecycle stage:** change
**Production verification:** nondestructive
**Release impact:** none

**Acceptance criteria**

- The command inventory includes every literal path and exposes no unreviewed mutation route.
- Console, command block where supported, authorized player, unauthorized player, offline target, malformed input, repeated confirmation, stale state, and recovery-state behavior are explicit and tested.
- Destructive or irreversible mutations require the repository-defined confirmation and durable audit context; diagnostic commands remain non-mutating.
- User-facing output is localized, structured, precise, and does not leak private state.

**Required evidence**

- Command tree snapshot and permission matrix compared with registration code.
- Focused command tests for every leaf and negative path, plus dedicated-server smoke execution.
- Updated command and recovery documentation linked to the exact merged revision.

### CORE-REQ-012 — Persistence, migration, and recovery closure

**Behavior:** Every FutureShops persistence surface has one documented owner, schema, reader, writer, atomicity, concurrency, integrity, migration, backup, restore, corruption, and recovery contract, and no known defect remains.
**Owner:** Persistence subsystem
**Contributors:** Escrow, markets, shops, economy, configuration, player lifecycle, security review
**Dependencies:** CORE-REQ-002, CORE-REQ-009, CORE-REQ-010, CORE-REQ-014
**Lifecycle stage:** change
**Production verification:** nondestructive
**Release impact:** migration

**Acceptance criteria**

- The inventory covers every escrow journal, checkpoint, ledger, custody store, claim store, SavedData object, inventory proof, NBT payload, JSON file, TOML file, migration, atomic write, backup, reload, concurrency, integrity, recovery, and corruption path.
- Crash, partial write, stale revision, duplicate replay, malformed data, missing registry entry, old schema, concurrent access, reload failure, and interrupted recovery preserve evidence and fail safely.
- Paths remain inside approved FutureShops roots, last-known-good snapshots survive invalid reloads, and recovery never deletes player or world state.
- Each verified defect follows CORE-REQ-009 and the exact candidate reruns the complete matrix clean.

**Required evidence**

- Complete persistence inventory and schema compatibility matrix grounded in source.
- Legacy and corrupted fixture tests, atomicity and restart tests, concurrency tests, backup and restore rehearsal, and conservation checks.
- Exact candidate dedicated-server restart and recovery logs with bounded actionable context.

### CORE-REQ-013 — Backend integration and failure-handling closure

**Behavior:** Client and server networking, module readiness, shops, economy, escrow, Auction House, Bazaar, ATM, claims, configuration reload, restart, reconnect, and multiplayer compose correctly in success and failure states.
**Owner:** Server integration architecture
**Contributors:** Client presentation, networking, all authoritative services, persistence, runtime verification
**Dependencies:** CORE-REQ-002, CORE-REQ-009, CORE-REQ-010, CORE-REQ-011, CORE-REQ-012, CORE-REQ-014
**Lifecycle stage:** change
**Production verification:** nondestructive
**Release impact:** none

**Acceptance criteria**

- The integration matrix covers every named subsystem, lifecycle state, and cross-component value transition on its supported branch.
- Delayed readiness, disabled modules, enable after restart, stale client snapshot, dropped or replayed request, disconnect, provider failure, full inventory, claim delivery, reload, shutdown, and recovery produce deterministic safe outcomes.
- Disabled modules do not advertise unusable navigation, enabled modules become usable from authoritative server state, and claims remain reachable independent of module availability.
- Every verified defect follows CORE-REQ-009 and the exact candidates rerun clean.

**Required evidence**

- Cross-component call and state-transition matrix with stable request and recovery identities.
- Focused integration tests, dedicated server and client smoke tests, and local multiple-client workflows under DEC-007.
- Restart, reconnect, delayed readiness, module toggle, ATM, Auction House, Bazaar, claim, and provider fault evidence.

### CORE-REQ-014 — Economic and recovery invariants

**Behavior:** Every repair preserves server authority, integer minor units, checked arithmetic, stable request UUID idempotency, custody conservation, durable accessible claims, backward-compatible persistence, fail-closed readiness, and zero silent item or currency loss.
**Owner:** Transaction architecture
**Contributors:** Every state-mutating component
**Dependencies:** CORE-REQ-002
**Lifecycle stage:** continuous
**Production verification:** nondestructive
**Release impact:** none

**Acceptance criteria**

- No code path authoritatively computes value through floating point or trusts client-authored price, stock, ownership, permission, or completion.
- Every mutation is idempotent across retry, reconnect, restart, and replay.
- Every partial failure either compensates safely or leaves durable recoverable custody and a reachable claim.
- Compatible unknown state is preserved and unrelated player inventory or data is never overwritten.

**Required evidence**

- Arithmetic boundary, idempotency, replay, concurrency, custody, claim, and recovery property tests.
- Conservation reports for every real transaction workflow and injected failure.
- Source review of every authoritative mutation entry point.

### CORE-REQ-015 — Deterministic full-stack verification

**Behavior:** Every changed support line and mandatory workflow is verified at the highest applicable fidelity at an exact revision with retained decisive results.
**Owner:** Verification system
**Contributors:** All component owners, runtime harness, reporters
**Dependencies:** CORE-REQ-003, CORE-REQ-004, CORE-REQ-005, CORE-REQ-006, CORE-REQ-007, CORE-REQ-008, CORE-REQ-009, CORE-REQ-010, CORE-REQ-011, CORE-REQ-012, CORE-REQ-013, CORE-REQ-014
**Lifecycle stage:** post_change
**Production verification:** nondestructive
**Release impact:** none

**Acceptance criteria**

- Focused regressions fail against the reproduced defect where feasible and pass at the candidate.
- Complete unit tests, data generation or validation, applicable GameTests, builds, dedicated-server smoke tests, client smoke tests, multiplayer, restart, reconnect, reload, rollback, corruption, and fault injection pass.
- Flaky, skipped, or failed evidence remains visible and blocks only the requirements that depend on it; workstation or node1 capacity is scheduled rather than treated as unavailable external evidence.
- No live or production verification mutates unbacked player or world state.

**Required evidence**

- Forge Java 17 results for `bash ./gradlew test`, applicable `runData`, applicable `runGameTestServer`, `build`, `runServer`, and `runClient` at the exact Forge candidate revision.
- NeoForge Java 21 results for its applicable test, build, data, server, and client tasks at the exact NeoForge candidate revision.
- Deterministic issue 32 corruption and recovery evidence, local controlled multiplayer evidence, exact JAR inspection, and complete diff review.

### CORE-REQ-016 — Support-line compatibility and version isolation

**Behavior:** Forge and NeoForge changes remain on their correct support branches, preserve pinned toolchains and compatibility, and use explicit migrations for any changed state or configuration.
**Owner:** Build governance
**Contributors:** Component owners, persistence, release verification
**Dependencies:** CORE-REQ-002
**Lifecycle stage:** continuous
**Production verification:** nondestructive
**Release impact:** migration

**Acceptance criteria**

- Forge work targets `1.20.1` and version `3.0.0-beta.2`; NeoForge issue 22 targets `1.21.1` and version `2.2.1`.
- Each work branch derives from the current approved head of its support line and integrates through a line-specific pull request and green checks.
- Pinned platform versions do not change unless separately authorized, and dependency pull request 28 remains separately assessed.
- Every schema or identifier change provides compatible read behavior, migration fixtures, backup guidance, and rollback boundaries.

**Required evidence**

- Git ancestry, branch, pull request, and merged revision proof for each line.
- Build metadata and JAR metadata inspection for exact versions and dependencies.
- Compatibility fixture and migration results for every changed persisted format.

### CORE-REQ-017 — Documentation and operational reconciliation

**Behavior:** User, administrator, maintainer, recovery, configuration, command, migration, test, and release-readiness documentation describes only verified candidate behavior and links to exact evidence.
**Owner:** Project documentation
**Contributors:** Every component owner, GitHub workflow
**Dependencies:** CORE-REQ-003, CORE-REQ-004, CORE-REQ-005, CORE-REQ-006, CORE-REQ-007, CORE-REQ-008, CORE-REQ-009, CORE-REQ-010, CORE-REQ-011, CORE-REQ-012, CORE-REQ-013, CORE-REQ-014, CORE-REQ-015, CORE-REQ-016, EXT-005
**Lifecycle stage:** post_change
**Production verification:** none
**Release impact:** none

**Acceptance criteria**

- `README.md`, `DOCUMENTATION.md`, `docs/README.md`, and each affected focused guide agree with code, configuration, commands, recovery, compatibility, and known limitations.
- Recovery guidance preserves complete matching snapshots and never recommends deleting player or market state.
- Issues, pull requests, milestones, project state, and wiki-ready tracked documentation reflect actual merged status without advertising unpublished behavior as released.

**Required evidence**

- Documentation link and terminology checks plus source-to-document behavior review.
- Exact command, config key, path, version, and recovery procedure verification.
- Final diff inspection proving no credentials, local paths, logs, caches, generated worlds, or unrelated edits entered tracked output.

### CORE-REQ-018 — Exact unpublished candidate artifacts

**Behavior:** The exact merged support-line revisions produce inspected Forge `3.0.0-beta.2` and NeoForge `2.2.1` candidate JARs and integrity records without uploading, publishing, announcing, or declaring stable status.
**Owner:** Release engineering
**Contributors:** Build system, verification, documentation
**Dependencies:** CORE-REQ-015, CORE-REQ-016, CORE-REQ-017
**Lifecycle stage:** post_change
**Production verification:** none
**Release impact:** none

**Acceptance criteria**

- JAR filenames and internal metadata use the exact locked versions and correct loader and Minecraft compatibility.
- JAR contents contain required resources and dependencies and exclude secrets, development output, caches, logs, and unrelated artifacts.
- Each candidate has SHA-256, SHA-512, source revision manifest, build toolchain record, and deterministic verification packet.
- No external release or announcement mutation occurs.

**Required evidence**

- Clean line-specific build result at each exact merged revision.
- Archive listing, metadata inspection, checksum verification, and source-revision manifest.
- Release authorization audit showing publication remains unexecuted.

### CORE-REQ-019 — Repeated clean candidate audit

**Behavior:** The complete issue, security, privacy, command, persistence, integration, runtime, documentation, dependency, and candidate-readiness audit repeats at exact candidate revisions until no known repository-owned defect remains.
**Owner:** Final integration audit
**Contributors:** Every audit and component owner
**Dependencies:** CORE-REQ-009, CORE-REQ-010, CORE-REQ-011, CORE-REQ-012, CORE-REQ-013, CORE-REQ-014, CORE-REQ-015, CORE-REQ-016, CORE-REQ-017, CORE-REQ-018, EXT-005
**Lifecycle stage:** post_change
**Production verification:** nondestructive
**Release impact:** none

**Acceptance criteria**

- Every audit matrix is rerun after the last material change that could invalidate it.
- Every finding is classified as duplicate, repaired defect, confidential blocker, excluded future work, or disproven concern with evidence.
- Any new verified defect reopens the rolling loop at CORE-REQ-009 and invalidates affected downstream evidence.
- Two consecutive final inventory passes at unchanged candidate revisions find no new repository-owned defect and all required checks remain green.

**Required evidence**

- Timestamped exact-revision audit packets and GitHub issue inventory from both consecutive passes.
- Green CI and local deterministic verification on unchanged candidate revisions.
- Complete diff, dependency, JAR, documentation, and security inspection results.

### CORE-REQ-020 — Evidence-gated issue closure and retention

**Behavior:** A scoped issue is closed and its tracking marked complete only after its issue comments are incorporated, the owner-accepted disposition is satisfied, the correct merged fix exists where required, local deterministic evidence passes, and exact-revision checks are green. Reporter acceptance is not a prerequisite.
**Owner:** Repository closure governance
**Contributors:** Issue triage, component owners, reporters, verification
**Dependencies:** CORE-REQ-003, CORE-REQ-004, CORE-REQ-005, CORE-REQ-006, CORE-REQ-007, CORE-REQ-008, CORE-REQ-009, CORE-REQ-010, CORE-REQ-011, CORE-REQ-012, CORE-REQ-013, CORE-REQ-014, CORE-REQ-015, CORE-REQ-016, CORE-REQ-017, CORE-REQ-018, CORE-REQ-019, EXT-005
**Lifecycle stage:** retention
**Production verification:** nondestructive
**Release impact:** none

**Acceptance criteria**

- Issue 22 includes explicit owner acceptance, correct `1.21.1` pull request integration, exact merged-revision verification, and closure evidence.
- Issue 25 includes every current comment, the local transition and current-state matrix, and either a supported-state defect repair or an explicit owner-approved beta compatibility disposition with recovery documentation.
- Issue 32 includes deterministic corruption, fuzz, crash-cut, modded-NBT sentinel, ownership-isolation, and non-destructive recovery evidence.
- Issues 33 and 34 and every audit-discovered defect include merged change and exact regression evidence.
- Project, milestone, pull request, issue, branch, and evidence state agree, and no missing reporter artifact is represented as an external blocker.

**Required evidence**

- Final issue comments link exact merged commits where required, tests, local runtime evidence, versions, owner dispositions, and closure reasoning.
- GitHub confirms required checks and merges on each correct support branch.
- Retained candidate verification packets and blocker records support later release authorization without rerunning ambiguous work.

## 13. Phased Roadmap

The master owns this complete global sequence and requirement assignment. Each linked phase file will own the sole full phase declaration and detailed execution blueprint. No requirement may move between phases without an explicit Plan Creator revision.

| Phase ID | Objective | Owner | Dependencies | Canonical requirements | Entry summary | Exit summary | Next transition | Execution blueprint |
|---|---|---|---|---|---|---|---|---|
| CORE-PHASE-000 | Establish authoritative governance, exact baseline, complete issue-comment intake, rolling issue workflow, branch isolation, local runtime capacity, and deterministic harness contracts | Repository governance | None | CORE-REQ-001, CORE-REQ-002, CORE-REQ-009, CORE-REQ-016 | Validated intake, exact repository identity, unchanged legacy plans, resolved historical prerequisite state, and available EXT-005 exist | Plan set is valid, issue packets include every current comment, historical EXT-001 through EXT-004 are classified, branch and version routes are proven, and local deterministic harness work is ready | CORE-PHASE-001 | [Phase 000](phases/plan-phase-000.md) |
| CORE-PHASE-001 | Independently verify and integrate the owner-accepted NeoForge issue 22 correction as `2.2.1`, then close issue 22 | NeoForge client integration | CORE-PHASE-000 | CORE-REQ-003 | `1.21.1` baseline, accepted root cause, candidate commit, issue comments, and existing evidence are identified | Fresh evidence passes, the change merges only into `1.21.1` through the required pull request workflow, the exact merged revision passes, and issue 22 closes | CORE-PHASE-002 | [Phase 001](phases/plan-phase-001.md) |
| CORE-PHASE-002 | Resolve Forge issues 25, 32, and 34 through local beta-transition, corruption, recovery, and multiple-client evidence | Forge defect integration | CORE-PHASE-001 | CORE-REQ-004, CORE-REQ-005, CORE-REQ-006 | Forge baseline, every issue comment, deterministic fixture contracts, local hardware capacity, and authoritative subsystem maps exist | Issue 25 has a merged supported-state repair or owner-approved compatibility disposition, issue 32 has local ownership-isolated recovery proof, issue 34 has local multiplayer conservation proof, and every required repair is merged | CORE-PHASE-003 | [Phase 002](phases/plan-phase-002.md) |
| CORE-PHASE-003 | Deliver the bounded issue 33 bulk listing workflow with atomic conflict-safe catalog updates | Admin shop editor | CORE-PHASE-002 | CORE-REQ-007, CORE-REQ-008 | Existing grid, catalog model, validation, price, stock, and persistence contracts are verified | Selection, preview, shared values, skip, explicit replace, preservation, atomicity, and recovery pass at the merged Forge revision | CORE-PHASE-004 | [Phase 003](phases/plan-phase-003.md) |
| CORE-PHASE-004 | Audit and close security, privacy, and every admin command and permission path | Security review | CORE-PHASE-003 | CORE-REQ-010, CORE-REQ-011 | All entry points, command leaves, trust boundaries, dependencies, and packaging surfaces are inventoried | Every verified finding has an issue before repair, all repairs merge on the affected line, and exact-revision security and command matrices rerun clean | CORE-PHASE-005 | [Phase 004](phases/plan-phase-004.md) |
| CORE-PHASE-005 | Audit and close every persistence, migration, integrity, recovery, and conservation path | Persistence subsystem | CORE-PHASE-004 | CORE-REQ-012, CORE-REQ-014 | Security and command repairs are integrated and the complete persistence inventory can be frozen | Every repository-controlled persistence surface and economic invariant has deterministic local evidence, every finding is repaired through an issue, and non-destructive recovery reruns clean | CORE-PHASE-006 | [Phase 005](phases/plan-phase-005.md) |
| CORE-PHASE-006 | Audit and close cross-component backend integration and failure behavior | Server integration architecture | CORE-PHASE-005 | CORE-REQ-013 | Authoritative services, command paths, persistence, invariants, and local multiple-client capacity are integrated and individually clean | All named subsystem combinations and failure states pass exact-revision integration, runtime, restart, reconnect, and controlled multiplayer evidence | CORE-PHASE-007 | [Phase 006](phases/plan-phase-006.md) |
| CORE-PHASE-007 | Prove final candidates, reconcile documentation, repeat all audits, close issues, and prepare unpublished artifacts | Release readiness | CORE-PHASE-006, EXT-005 | CORE-REQ-015, CORE-REQ-017, CORE-REQ-018, CORE-REQ-019, CORE-REQ-020 | All repairs and approved dispositions are integrated on correct support lines and every evidence invalidation is current | Every requirement passes, EXT-005 remains verified, two unchanged-revision audits are clean, all scoped issues are correctly closed, branches are green, and exact unpublished candidate artifacts and integrity records satisfy the completion endpoint | Final plan-wide closure | [Phase 007](phases/plan-phase-007.md) |

## 14. Verification Strategy

| Requirement family | Unit and contract evidence | Integration evidence | Real behavior evidence | Security evidence | Artifact or runtime evidence |
|---|---|---|---|---|---|
| CORE-REQ-001, CORE-REQ-002, CORE-REQ-009, CORE-REQ-016 | Plan validation, branch and issue inventory assertions | GitHub and Git ancestry reconciliation | Evidence requests and duplicate-before-repair workflow | Sanitized evidence and confidential routing | Exact baseline revisions and unchanged legacy hashes |
| CORE-REQ-003 | Screen lifecycle and rendering regression | NeoForge client integration and line-specific PR | Owner-accepted fix rerun at exact merged revision | Client-only boundary review | Java 21 test, build, client smoke, JAR inspection, merge proof, and issue closure |
| CORE-REQ-004 | Catalog parse, migration, validation, readiness, and beta-transition tests | Config reload, server snapshot, and UI route tests | Local current-state, direct-update, and intermediate-beta startup, restart, and reload matrix | Path, parser, and permission review | Forge logs, merged fix or owner-approved compatibility disposition, and recovery documentation |
| CORE-REQ-005 | Serialization, exact item proof, fuzzing, crash-cut, sentinel, player lifecycle, and recovery tests | Transaction, claim, player save, ownership isolation, restart, and reconnect | Deterministic locally generated corrupt-state recovery | Private-data minimization and unsafe-data tests | Seeded before-and-after state diff and dedicated multiplayer runtime |
| CORE-REQ-006 | Stock, money, escrow, replay, and concurrency tests | Shop transaction and provider integration | Local finite-stock multiple-client matrix under DEC-007 | Duplication, replay, authority, and log privacy tests | Conservation report across retry and restart |
| CORE-REQ-007, CORE-REQ-008 | Selection, canonical NBT, decimal, preview, duplicate, preservation, and atomicity tests | Client-to-server request, catalog write, reload, and stale-state tests | Admin grid workflow with mixed create, skip, replace, cancel, and failure | Permission, bounds, NBT, path, and request-size tests | Forge client, server, data, build, and JAR inspection |
| CORE-REQ-010, CORE-REQ-011 | Packet, command, path, codec, permission, replay, and negative tests | Trust-boundary and command service tests | Console and player command matrix on dedicated server | Threat model, dependency, secret, privacy, and packaging audit | Exact-revision clean security packet |
| CORE-REQ-012, CORE-REQ-014 | Codec, migration, atomicity, concurrency, corruption, idempotency, and conservation tests | Full persistence and recovery lineage tests | Backup, restore, crash, restart, reconnect, and repeated recovery | Path boundary, deserialization, privacy, and tamper tests | Persistence inventory and recovery logs |
| CORE-REQ-013 | State-machine and payload contract tests | Complete named subsystem and failure matrix | Dedicated multiplayer, module lifecycle, ATM, claim, Auction House, and Bazaar workflows | Cross-boundary authority and replay review | Server and client smoke logs at exact candidates |
| CORE-REQ-015 through CORE-REQ-020 | Full deterministic suites and validation scripts | Both line-specific integration matrices | Owner dispositions, local multiplayer, restart, reconnect, corruption, and recovery | Repeated clean security and privacy audit | Exact JARs, metadata, checksums, manifests, green checks, and issue closure |

Verification order for a changed support line is focused regression, complete unit tests, data generation or validation when applicable, applicable GameTests, build, dedicated-server smoke, client smoke, multiplayer when state crosses the network, restart and reconnect, JAR inspection, complete diff inspection, and exact evidence retention. A later material change invalidates every downstream result it can affect and forces those results to rerun.

No destructive production verification is authorized. Runtime testing uses isolated copies and generated fixtures. Failures remain recorded with exact commands, revisions, environments, seeds, crash points, and decisive errors.

## 15. Compatibility, Migration, Rollout, and Recovery

### Compatibility

- Forge 1.20.1 remains Java 17 and Forge 47.4.20 unless a separately approved contract changes it.
- NeoForge 1.21.1 remains Java 21 and NeoForge 21.1.233 unless separately approved.
- Network protocol changes must be versioned and must fail clearly on incompatible clients rather than misinterpret payloads.
- Stable resource locations, config keys, serialized names, command literals, permissions, and public APIs remain unchanged unless a requirement proves a migration is necessary.

### Migration

Every changed persisted or configured representation needs a reader compatibility table, deterministic legacy fixtures, explicit target schema, one-time or repeat-safe migration behavior, atomic replacement, backup point, failure behavior, and rollback boundary. Catalog, player, world, escrow, custody, claim, journal, checkpoint, ledger, NBT, JSON, and TOML migrations are all in scope. A migration failure keeps the last valid state and produces an actionable path and field without discarding unknown data.

### Integration order

1. Integrate NeoForge issue 22 only into `1.21.1` after its internal evidence passes.
2. Integrate Forge fixes sequentially through the phase branches rooted in the latest approved `1.20.1` head.
3. Do not begin a dependent Forge phase until the previous Forge phase is merged and `origin/1.20.1` contains the merge.
4. Use repository-controlled local evidence for issues 25, 32, and 34. Historical EXT-001 through EXT-004 do not delay phase transitions or final closure.
5. Build final candidates only from exact merged support-line revisions after all invalidated checks rerun.

### Rollback and recovery

Rollback means stopping the isolated server or test client, preserving the failed state and logs, and restoring one complete matching backup of world and FutureShops state. It does not mean deleting player data, individual journals, custody, claims, or selected files. A code rollback must respect the schema compatibility table; if a new schema cannot be read by the prior version, recovery uses the documented pre-migration snapshot.

Any unexpected maintenance mode, conservation mismatch, checksum failure, duplicate request result, partial catalog write, unusable player state, or inaccessible claim stops mutation testing. Preserve evidence, run non-mutating status and verification paths, and resume only through the documented verified recovery contract.

## 16. Documentation, Operations, and Release Gates

- Update root and technical documentation whenever behavior, commands, permissions, configuration, persistence, migration, recovery, testing, compatibility, or artifact identity changes.
- Keep issue and pull request text lowercase under repository rules while project documentation uses normal prose.
- Preserve the repository's tracked documentation layout and update `docs/README.md` links for any added focused document.
- Operator procedures must name exact backup scope, shutdown state, validation commands, expected output, failure stop conditions, and recovery without deletion.
- Every issue needs exact reproduction or blocker state, implementation link, verification link, affected version, candidate revision, and closure evidence.
- Every ready phase pull request needs deterministic checks and one private independent review if the optional private review capability exists. Merge through GitHub with the repository's required method and never push integration directly to a protected support branch.
- Candidate artifacts require correct metadata, dependency contents, SHA-256, SHA-512, source revision manifest, and verification packet.
- This plan does not authorize release creation, CurseForge or Modrinth upload, announcement, stable designation, or a public release tag.

## 17. Risks and Failure Boundaries

| Risk | Impact | Prevention | Detection | Recovery |
|---|---|---|---|---|
| Beta transition ambiguity | A supported current defect could be confused with unsupported intermediate beta state | Read every issue comment and run the complete local transition and current-state matrix | Matrix row, schema, version, and first-divergence comparison | Repair supported-state defects or obtain owner approval for an evidence-backed compatibility disposition and recovery guide |
| Local runtime fixture or capacity drift | Multiplayer or corruption evidence becomes non-reproducible | Pin exact seeds, fixture hashes, client count, memory allocation, revisions, and host role | Repeated workstation run and node1 fallback comparison when needed | Recreate the isolated fixture, rerun invalidated evidence, and never substitute live player data |
| Cross-branch contamination | Unsupported loader or build breakage | Separate branch ancestry, work branches, PRs, versions, and toolchains | Git diff, ancestry, metadata, and build inspection | Revert only the affected line through a reviewed change; do not merge lines |
| Player or world data damage | Data loss or unusable saves | Backups, isolated copies, versioned codecs, atomic writes, and no destructive testing | Fixture diffs, restart tests, integrity checks, and issue 32 evidence | Stop mutation, preserve all state, restore one complete matching snapshot, and repair non-destructively |
| Item or currency duplication or loss | Economy compromise | Server authority, checked arithmetic, UUID idempotency, custody, claims, and compensation | Conservation, replay, concurrency, and fault tests | Freeze mutation, preserve journal lineage, verify recovery, deliver or compensate once |
| Audit finding repaired without issue | Lost traceability and incomplete candidate inventory | Duplicate-before-repair gate in every phase | Diff-to-issue audit | Stop the repair, create or link the canonical record before continuing |
| Sensitive evidence leak | Player privacy or security exposure | Sanitize logs, redact private NBT and secrets, use private advisories for exploit details | Security review of issues, logs, artifacts, and diffs | Remove exposed data where possible, rotate affected secrets outside repository scope, and use confidential remediation |
| Partial bulk catalog update | Inconsistent offers or lost metadata | Full validation, immutable preview, field ownership, atomic write, and rollback | Mixed conflict and write-failure tests | Retain prior file and in-memory snapshot; report every blocking item |
| False module state after restart | Inaccessible or misleading marketplace UI | Server-authoritative readiness and explicit synchronization | Delayed readiness, enable, disable, restart, and reconnect tests | Preserve claims, reject mutation, resynchronize from server, and expose actionable state |
| Stale verification after late change | False candidate confidence | Evidence invalidation graph and repeated final audit | Revision comparison in verification packets | Rerun every affected downstream check and final clean pass |
| Dependency update changes platform behavior | Unplanned compatibility drift | Keep PR 28 separate and forbid silent platform changes | Dependency diff, lock and metadata review, CI | Defer or revert dependency change without altering defect scope |
| Publication without authority | Premature or incorrect public release | Explicit release exclusion and final transport check | Release, tag, CurseForge, Modrinth, and announcement audit | Stop before external mutation and request fresh owner authorization |

## 18. Definition of Done

**Completion endpoint:** Every scoped issue and every repository owned defect discovered by the rolling audit is deduplicated, fixed or given an evidence backed owner approved compatibility disposition, regression tested, merged into the correct support branch, and closed. Issue 22 is verified, merged into 1.21.1, and closed under explicit owner acceptance. Issues 25, 32, and 34 use local deterministic reproduction, fault injection, dedicated server, and multiple client evidence without reporter or hardware blockers. Exact Forge 3.0.0-beta.2 and NeoForge 2.2.1 candidates pass security, privacy, command, persistence, integration, runtime, documentation, and repeated clean audit gates. Publication and announcements remain excluded.

**Known external blocker routing:** None. EXT-001 through EXT-004 are resolved or superseded historical traceability IDs. EXT-005 is available and authorized at intake. A later loss of GitHub access must be reported as a new execution blocker for affected remote operations, not as a pre-existing endpoint blocker.

- Every CORE-REQ-001 through CORE-REQ-020 acceptance criterion has its required exact evidence.
- Every CORE-PHASE-000 through CORE-PHASE-007 exit gate passes with no known mandatory phase-owned defect.
- No known external blocker remains, and EXT-005 verifies final GitHub state.
- Issues 22, 25, 32, 33, and 34 and every rolling audit issue are fixed on the correct support line, merged, verified, and closed.
- EXT-001 through EXT-004 remain historically reconciled, all successor local evidence gates pass, and EXT-005 confirms final GitHub state.
- Security, privacy, command, persistence, database, recovery, backend integration, runtime, documentation, dependency, and artifact audits rerun at both exact candidate revisions.
- Two consecutive final audit passes at unchanged candidate revisions identify no new repository-owned defect.
- Forge `3.0.0-beta.2` and NeoForge `2.2.1` candidate JARs, metadata, source revision manifests, SHA-256, SHA-512, and verification packets are prepared and internally consistent.
- Required branch and pull request checks are green and all tracking state agrees with the exact merged revisions.
- Legacy plans remain unchanged and are retained as historical records.
- Optional and future work remains excluded.
- CurseForge, Modrinth, GitHub release publication, stable publication, and announcements have not occurred under this plan.
- No known external prerequisite blocks this plan at intake; any newly unavailable prerequisite remains visible and cannot be replaced by mocked or lower-fidelity evidence.

The single completion endpoint is reached only when every scoped and rolling repository-owned defect is closed with required evidence, both support lines are green at exact merged candidate revisions, both unpublished artifacts are prepared with integrity evidence, and the repeated final audit finds no remaining known repository-owned defect.

## 19. Goal Creator Handoff

```text
Mandatory boundary: CORE-REQ-001 through CORE-REQ-020 on the Forge 1.20.1 and NeoForge 1.21.1 support lines, including rolling verified defects and all mandatory audits.
Optional/future disposition: excluded
Locked owner decisions: DEC-001 through DEC-007.
Active phase: CORE-PHASE-000
Next executable action: Read phases/plan-phase-000.md through EOF, verify the exact starting repository and GitHub state without changing unrelated files, and execute its first unfinished evidence gate.
Known failing checks: None are asserted by plan authoring. Baseline and exact-candidate checks must be run and recorded by execution.
Known external blockers: none
Completion endpoint: Every scoped issue and every repository owned defect discovered by the rolling audit is deduplicated, fixed or given an evidence backed owner approved compatibility disposition, regression tested, merged into the correct support branch, and closed. Issue 22 is verified, merged into 1.21.1, and closed under explicit owner acceptance. Issues 25, 32, and 34 use local deterministic reproduction, fault injection, dedicated server, and multiple client evidence without reporter or hardware blockers. Exact Forge 3.0.0-beta.2 and NeoForge 2.2.1 candidates pass security, privacy, command, persistence, integration, runtime, documentation, and repeated clean audit gates. Publication and announcements remain excluded.
Required evidence gates: Valid plan set, every current issue comment, exact baseline, issue-before-repair traceability, line-specific deterministic tests and builds, server and client smoke tests, local multiple-client and recovery evidence, issue 22 owner acceptance and merged verification, issue 25 transition matrix and repair or owner-approved compatibility disposition, issue 32 deterministic corruption and recovery proof, clean security, command, persistence, and integration audits, documentation reconciliation, exact artifact inspection, checksums, green GitHub state, and two unchanged-revision final audits.
```
