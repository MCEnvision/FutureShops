# Phase 003 Execution Plan

> **Plan ID:** PLAN-PHASE-003
> **Phase ID:** CORE-PHASE-003
> **Owner:** FutureShops repository
> **Classification:** MANDATORY
> **Master plan:** [plan.md](../plan.md)
> **Phase sequence:** 003 of 003

## Purpose and Ownership

This phase proves the integrated FutureShops `2.3.0` result, reconciles documentation to observed behavior, produces one inspected and hashed release candidate, and verifies the already open GitHub continuation issue. The artifact remains unpublished.

This phase owns `CORE-REQ-019` through `CORE-REQ-022`. The master owns product scope, locked decisions, the plan-wide Definition of Done, and publication exclusion. This file owns only final validation, documentation reconciliation, artifact identity, remote issue verification and update, and terminal evidence. It does not absorb defects from an earlier owner requirement, redefine compatibility, create an initial continuation issue, publish a release, or authorize future `3.0.0` work.

## Evidence-Based Entry State

| Evidence class | Area | Finding | Source or command | Freshness condition |
|---|---|---|---|---|
| VERIFIED | Phase sequence | `CORE-PHASE-002` must be integrated on the approved default branch before this phase starts. | Phase 002 completion packet, merged pull request, approved default branch revision | Any upstream source or integration change invalidates entry. |
| VERIFIED | External inputs | `EXT-001` through `EXT-006` identify exact reviewed artifacts and reproducible Pixelmon and hybrid environments. | Phase 000 and Phase 002 evidence packets | Any byte, version, license, configuration, or manifest change invalidates dependent proof. |
| VERIFIED | Integrated behavior | Requirements `CORE-REQ-001` through `CORE-REQ-018` have phase-owned evidence and no known mandatory defect. | Prior completion packets and requirement trace | A changed implementation, schema, config, dependency, or user-visible behavior invalidates affected evidence. |
| PROPOSED | Final candidate | The integrated source can produce one reproducible FutureShops `2.3.0` jar. | Clean checked-in wrapper build | Unknown until the final build and artifact inspection pass. |
| VERIFIED | Tracking issue | Plan authoring created the required open GitHub continuation issue after plan validation. Phase 003 must verify and update that issue, not create an initial issue. | Existing issue URL and number in authoring evidence | Repository, issue identity, state, milestone, labels, or body change requires readback. |
| UNKNOWN | Terminal evidence | `EVD-VER-001`, `EVD-VER-002`, and `EVD-ART-001` are not final until tied to one source commit and one candidate hash. | Phase 003 evidence store | Any candidate or source change invalidates final status. |

Phase entry stops if Phase 002 is not integrated, exact external environments cannot be reproduced, the existing issue cannot be identified, or a prior mandatory requirement lacks evidence. No later task may convert an entry failure into a documentation caveat.

## Scope Boundaries

### Included Scope

- `CORE-REQ-019`: execute the complete deterministic, runtime, recovery, security, dependency, packaging, and diff validation matrix.
- `CORE-REQ-020`: reconcile `README.md`, `DOCUMENTATION.md`, `docs/README.md`, and applicable focused guides with the exact validated behavior.
- `CORE-REQ-021`: build, inspect, hash, reinstall, and retain one unpublished `2.3.0` release candidate.
- `CORE-REQ-022`: verify `EXT-007`, search for duplicates, read back and update the already open continuation issue, and preserve its required scope, milestone, and labels.
- Audit every `CORE-REQ-001` through `CORE-REQ-022` acceptance criterion and every plan-wide Definition of Done item.

### Explicit Exclusions

- `NG-001` and `DEC-016`: no GitHub release, release tag, announcement, CurseForge, Modrinth, or other publication.
- `FUT-001` through `FUT-004`: no `3.0.0` maintenance, port, ATM, or additional provider implementation.
- No initial GitHub issue creation. Missing or unverifiable authoring issue evidence is an `EXT-007` blocker.
- No closing of the continuation issue during validation. It remains open until future owner acceptance of the completed `2.3.0` outcome authorizes closure.
- No weakening, waiver, or reassignment of an earlier requirement to make terminal evidence pass.

## Phase Contract

### CORE-PHASE-003 — Production Validation and Unpublished Delivery

**Objective:** Produce reproducible proof for all mandatory requirements, one exact unpublished artifact, accurate documentation, and a verified open continuation issue.
**Owner:** FutureShops repository
**Dependencies:** CORE-PHASE-002, CORE-REQ-001, CORE-REQ-002, CORE-REQ-003, CORE-REQ-004, CORE-REQ-005, CORE-REQ-006, CORE-REQ-007, CORE-REQ-008, CORE-REQ-009, CORE-REQ-010, CORE-REQ-011, CORE-REQ-012, CORE-REQ-013, CORE-REQ-014, CORE-REQ-015, CORE-REQ-016, CORE-REQ-017, CORE-REQ-018, DEC-015, DEC-016, EXT-001, EXT-002, EXT-003, EXT-004, EXT-005, EXT-006, EXT-007
**Canonical requirements:** CORE-REQ-019, CORE-REQ-020, CORE-REQ-021, CORE-REQ-022
**Documentation and release impact:** Final user, API, maintainer, operator, migration, recovery, integration, compatibility, verification, and artifact documentation; no publication
**Next transition:** Plan-wide completion audit and owner-selected unpublished endpoint acceptance

**Entry criteria**

- Phase 002 is merged and its resulting default branch commit is the source baseline.
- Prior phase evidence identifies exact external artifacts, environment manifests, and phase artifact hashes.
- The open continuation issue, repository, milestone, and exact labels are discoverable through authenticated read access.
- The immutable goal file and protected plan set are unchanged except through authorized plan authoring.

**Implementation scope**

- CORE-REQ-019 validates the complete integrated system and routes failures only through the owning stable requirement.
- CORE-REQ-020 reconciles tracked documentation after observed behavior is final.
- CORE-REQ-021 produces and validates one artifact from the final verified source commit.
- CORE-REQ-022 verifies and updates the existing issue only after artifact acceptance gates pass.

**Execution order**

1. `P003-TASK-001` executes CORE-PHASE-003 by freezing the source revision, rereading all registered requirements, importing prior evidence, and recording invalidation boundaries.
2. `P003-TASK-002` verifies `EXT-007`, the existing issue identity, authenticated repository access, duplicate search, milestone, and labels without mutating or closing the issue.
3. `P003-TASK-003` runs focused and complete deterministic checks for `CORE-REQ-001` through `CORE-REQ-018`.
4. `P003-TASK-004` executes CORE-REQ-019 through standard NeoForge server, client, multiplayer, reconnect, lifecycle, and optional-isolation workflows.
5. `P003-TASK-005` executes CORE-REQ-019 through internal, fixture, Pixelmon, and `vault` failure, crash, idempotency, recovery, and surface matrices.
6. `P003-TASK-006` executes CORE-REQ-019 by auditing dependencies, security, secrets, licenses, generated output, jar boundaries, and the complete diff.
7. `P003-TASK-007` executes CORE-REQ-020 by reconciling and rehearsing all required documentation against the passing implementation and environments.
8. `P003-TASK-008` executes CORE-REQ-021 by building the final candidate from the verified commit and recording SHA 256, SHA 512, metadata, contents, and reproducibility evidence.
9. `P003-TASK-009` executes CORE-REQ-021 by installing the exact hashed candidate in every required environment and repeating artifact-dependent acceptance workflows.
10. `P003-TASK-010` executes CORE-REQ-019 by completing the requirement trace and every plan-wide Definition of Done check.
11. `P003-TASK-011` executes CORE-REQ-022 by searching again for duplicates, updating and reading back the existing continuation issue, and recording `EVD-GH-001` without closing it.
12. `P003-TASK-012` executes CORE-PHASE-003 by assembling the terminal completion packet and presenting the unpublished endpoint for owner acceptance.

**Required evidence**

- `EVD-VER-001`, `EVD-VER-002`, `EVD-ART-001`, and `EVD-GH-001` tied to exact revisions, artifacts, manifests, commands, dates, and sanitized evidence locations.
- Requirement trace, crash matrix, surface matrix, environment logs, documentation rehearsal, dependency report, jar listing, hashes, secret scan, and complete diff report.

**Exit criteria**

- Every mandatory requirement and every plan-wide Definition of Done item has fresh passing evidence.
- One exact candidate passes all environments and remains unpublished and untagged.
- The existing issue is verified by URL and readback, updated after candidate validation, and remains open pending future owner acceptance.
- No known mandatory phase-owned or upstream defect remains.

## Inputs and Upstream Contracts

| Input or contract | Provider | Required state | Validation | Failure behavior |
|---|---|---|---|---|
| Integrated source | `CORE-PHASE-002` | Approved default branch contains sequential phase results | Git and GitHub readback, source revision record | Stop and return to the missing integration gate. |
| Requirement evidence | Phases 000 through 002 | Complete, sanitized, and tied to exact inputs | Trace every acceptance criterion | Return failure to owning requirement and invalidate dependents. |
| External stacks | `EXT-001` through `EXT-006` | Exact reviewed bytes and reproducible manifests | Hash before every install and run | Discard mismatched runs and restore exact stack. |
| GitHub issue capability | `EXT-007` | Correct repository, authenticated owner access, existing milestone, exact labels, and identifiable open issue | Duplicate search and remote readback | Block completion; do not create a substitute or omit metadata. |
| Publication boundary | `DEC-016` | No release, tag, upload, announcement, or public artifact | Remote state and local artifact audit | Stop and report any unauthorized publication state. |

## Outputs and Downstream Contracts

| Output or contract | Consumer | Guaranteed state | Compatibility or versioning | Evidence |
|---|---|---|---|---|
| Validation record | Owner and maintainers | All mandatory checks map to exact source and artifacts | Invalid after any relevant input changes | `EVD-VER-001`, `EVD-VER-002` |
| Documentation set | Users, integrators, operators | Describes only validated `2.3.0` behavior and exact compatibility | Must change with behavior or artifact identity | Documentation diff and rehearsal |
| Release candidate | Owner | One inspected FutureShops `2.3.0` jar remains unpublished | Minecraft `1.21.1`, NeoForge `21.1.248` | `EVD-ART-001` |
| Continuation issue | Future maintainers | Existing open issue states both `3.0.0` Forge maintenance and future `1.21.1` port scope | Reference only, not `2.3.0` implementation authority | `EVD-GH-001` readback |

## Work Packages

| Task ID | Requirement IDs | Work | Inputs and dependencies | Outputs | Affected components or interfaces | Verification |
|---|---|---|---|---|---|---|
| `P003-TASK-001` | `CORE-REQ-019` | Freeze revision and construct the full acceptance and invalidation trace. | Integrated Phase 002, all prior packets | Validation baseline | Plan set, evidence store, Git state | Every criterion has an owner and planned proof. |
| `P003-TASK-002` | `CORE-REQ-022` | Verify `EXT-007` and existing issue identity without mutation. | Authoring issue evidence, authenticated GitHub | Capability and duplicate report | GitHub issue, milestone, labels | Remote readback matches exact repository and metadata. |
| `P003-TASK-003` | `CORE-REQ-019` | Run focused tests, all tests, applicable data validation and GameTests, then build. | Frozen source and commands | `EVD-VER-001` draft | Build, tests, generated resources | Every required command passes in order. |
| `P003-TASK-004` | `CORE-REQ-019` | Run clean server, client, multiplayer, reconnect, restart, and isolation workflows. | Phase artifact and standard NeoForge manifest | Standard runtime evidence | Common, client, network, lifecycle | Same bytes pass all standard workflows. |
| `P003-TASK-005` | `CORE-REQ-019` | Execute provider, surface, crash, retry, compensation, claim, and recovery matrices. | Exact external manifests and stable request fixtures | `EVD-VER-002` | Coordinator, persistence, adapters, external bridge | No duplicate effect, guessed outcome, fallback, loss, or unsafe mutation. |
| `P003-TASK-006` | `CORE-REQ-019`, `CORE-REQ-021` | Inspect dependency graphs, classpaths, source, jar, secrets, licenses, output, and diff. | Passing build and exact artifacts | Audit report | Build and packaged jar | No forbidden dependency, embedded artifact, secret, debug file, or unrelated change. |
| `P003-TASK-007` | `CORE-REQ-020` | Reconcile docs and rehearse commands, examples, migration, failure, restore, and recovery. | Passing behavior and exact manifests | Final documentation set | `README.md`, `DOCUMENTATION.md`, `docs/README.md`, focused guides | Links, claims, examples, and runbooks pass on disposable data. |
| `P003-TASK-008` | `CORE-REQ-021` | Clean build the candidate and record identity, metadata, hashes, and contents. | Final verified source and docs | `EVD-ART-001` candidate | Release candidate jar | Reproducible bytes and correct `2.3.0` metadata. |
| `P003-TASK-009` | `CORE-REQ-019`, `CORE-REQ-021` | Reinstall exact candidate in standard, Pixelmon, and hybrid environments. | Candidate hashes and exact manifests | Final artifact runtime proof | All supported environments | Installed hashes match and terminal workflows pass. |
| `P003-TASK-010` | `CORE-REQ-019` through `CORE-REQ-021` | Audit all requirements and Definition of Done items. | Final evidence and candidate | Signed-off trace matrix | Whole product contract | No missing, stale, contradictory, or lower-fidelity proof. |
| `P003-TASK-011` | `CORE-REQ-022` | Search duplicates, update existing issue, verify state, scope, milestone, labels, and links. | Passed tasks 009 and 010, `EXT-007` | `EVD-GH-001` | Existing GitHub issue | URL and readback prove correct open issue; no duplicate created. |
| `P003-TASK-012` | `CORE-REQ-019` through `CORE-REQ-022` | Assemble completion packet and owner acceptance handoff. | All prior tasks | Terminal packet | Evidence, candidate, issue link | Packet independently reproduces every terminal claim. |

Tasks are sequential where evidence depends on candidate identity. Independent deterministic checks may run in parallel only against the same frozen revision. A failure returns to its owning requirement, invalidates every dependent task result, and resumes from the earliest affected gate.

## Architecture and Implementation Boundaries

The logical server, provider API, coordinator, persistence, adapters, and client presentation remain owned by their earlier phases. Phase 003 changes implementation only to resolve a proven defect through the owning stable requirement. It never patches evidence, relaxes an assertion, substitutes a different external stack, or documents a failure as support.

Candidate identity is content based. Every runtime manifest records source commit, jar SHA 256 and SHA 512, Minecraft, NeoForge, Java, external artifact hashes, configuration, player count, expected result, actual result, and sanitized evidence location. No balance mirror, production player data, credential, private log, or mutable external state enters evidence.

## Failure, Recovery, and Edge Cases

| Scenario | Detection | Required behavior | Recovery or rollback | Regression proof |
|---|---|---|---|---|
| Required command fails | Nonzero exit or assertion failure | Stop dependent validation and identify owning requirement. | Fix on phase branch, rebuild, invalidate and rerun affected evidence. | Full sequence from earliest affected command. |
| Candidate bytes change | Hash or reproducibility mismatch | Discard all artifact-dependent proof. | Freeze new candidate and repeat tasks 006 through 012. | Matching hashes in every environment. |
| External stack drifts | Installed hash differs from manifest | Do not run or accept evidence. | Restore exact reviewed bytes or reopen prerequisite review. | Hash check followed by complete affected matrix. |
| Ambiguous or duplicate value effect | Receipt, balance delta, or journal conflict | Enter `RECOVERY_REQUIRED`; stop monetary writes. | Preserve data, recover by stable identity, repair owning requirement. | Full crash and idempotency matrix for provider and surfaces. |
| Documentation conflicts with runtime | Rehearsal or artifact cross-check differs | Treat documentation gate as failed. | Correct docs or behavior through the proper owner, then rerun. | Link, example, command, and runbook checks. |
| Existing issue is missing or inaccessible | Search or readback fails | Block completion; do not create a replacement in this phase. | Restore access or owner-authorized authoring evidence. | Duplicate search and exact remote readback. |
| Issue metadata drifts | Milestone, labels, scope, state, or links differ | Update only after candidate validation, then read back. | Restore exact metadata; keep issue open. | `EVD-GH-001` readback. |
| Publication is detected | Tag, release, upload, or public artifact exists | Stop and report contract violation. | Owner directs safe remediation; never publish further. | Remote and artifact publication audit. |

## Verification Matrix

| Requirement or task | Static or unit | Integration | Real workflow or runtime | Negative and recovery | Evidence artifact |
|---|---|---|---|---|---|
| `CORE-REQ-019` | Focused tests, all tests, build, scans | Full provider and surface matrices | Server, client, multiplayer, Pixelmon, hybrid | Crash, retry, reconnect, failure, compensation, restore | `EVD-VER-001`, `EVD-VER-002` |
| `CORE-REQ-020` | Link, identifier, version, config, and API checks | Documentation against final behavior | Operator install, selection, failure, backup, and recovery rehearsal | Invalid config, missing provider, ambiguous request, restore | Documentation diff and rehearsal record |
| `CORE-REQ-021` | Metadata, contents, dependency, secret, and hash checks | Exact bytes across all manifests | Candidate installed in every required environment | Hash mismatch, forbidden contents, clean rebuild | `EVD-ART-001` |
| `CORE-REQ-022` | Duplicate and metadata checks | Existing issue linked to validated endpoint | Authenticated GitHub readback | Missing capability, wrong state, metadata drift | `EVD-GH-001` |
| `P003-TASK-010` | Requirement trace audit | Cross-phase evidence reconciliation | Repeat any disputed terminal workflow | Invalidate stale or mismatched proof | Plan-wide completion checklist |

Fixtures use disposable worlds, players, products, shops, claims, bills, requests, and provider data. Reruns begin with focused tests, then all tests, applicable data and GameTests, build, standard runtime, multiplayer and reconnect, crash and recovery, exact external environments, packaging and security inspection, candidate reinstall, documentation rehearsal, and issue readback. Lower-fidelity proof never replaces an exact environment or recovery workflow.

## Documentation, Operations, and Release

Update existing tracked documentation only. `README.md` covers purpose, exact versions, installation, provider selection, external compatibility, user behavior, build and support. `DOCUMENTATION.md` remains the maintainer and architecture hub. `docs/README.md` indexes focused API, configuration, integration, migration, recovery, security, and verification material where those files exist or are required by `CORE-REQ-020`.

Documentation must state `internal` default, restart only selection, no fallback, exact minor units, no automatic migration, external money item restrictions, no ATM, claims and custody safety, exact Pixelmon and hybrid limits, optional dependency isolation, backup and recovery procedure, candidate hashes, and unpublished status. Rehearse operator procedures on disposable copies.

Do not publish, tag, announce, upload, or create a release. The existing continuation issue is tracking output, not publication. Update it only after final candidate validation; keep it open until future owner acceptance authorizes closure.

## Risks and Evidence Invalidation

| Risk | Prevention | Detection | Recovery | Evidence invalidated | Reverification |
|---|---|---|---|---|---|
| Upstream source changes | Freeze source revision before validation. | Git diff or commit mismatch | Reclassify change by owning requirement. | All dependent deterministic, runtime, docs, and artifact proof | Restart at task 001. |
| Different bytes reach an environment | Hash every install. | Manifest mismatch | Reinstall exact candidate. | Affected environment and terminal artifact proof | Repeat full affected environment. |
| Failure is waived as documentation | Require behavior proof before claims. | Trace or rehearsal mismatch | Fix owning requirement. | Related docs and completion evidence | Rerun owner gates and docs. |
| Sensitive data enters evidence | Disposable data, sanitization, scans. | Secret or privacy finding | Quarantine and regenerate. | Affected logs, docs, packet, and issue evidence | Reproduce and rescan. |
| Issue is duplicated or closed early | Reuse authoring issue and search before update. | Search and state readback | Stop, preserve canonical issue, resolve duplicate with owner direction. | `EVD-GH-001` and plan completion | Repeat search and readback. |
| Artifact is published | Enforce `DEC-016` and audit remote state. | Release, tag, upload, or public URL | Stop for owner remediation. | Entire terminal endpoint | Full publication audit after remediation. |

## Phase Completion Packet

The packet outside the protected plan set contains:

1. Integrated source commit, phase branch and pull request evidence, and proof of sequential phase integration.
2. Complete requirement trace for `CORE-REQ-001` through `CORE-REQ-022` and all twenty Definition of Done conditions.
3. `EVD-VER-001` with exact commands, results, environment, date, commit, and sanitized log locations.
4. `EVD-VER-002` with standard, multiplayer, restart, recovery, Pixelmon, and hybrid manifests and results.
5. `EVD-ART-001` with jar filename, byte size, source commit, SHA 256, SHA 512, metadata, contents, dependency and security inspection, and reproducibility result.
6. Proof that the same hashed candidate passed standard NeoForge, exact Pixelmon, and exact hybrid environments.
7. Documentation diff, link and example checks, compatibility cross-check, and disposable operator runbook rehearsal.
8. Dependency, license, classpath, bytecode, archive, secret, generated output, debug output, and complete Git diff reports.
9. Failure and evidence invalidation ledger showing every repair and required rerun.
10. `EVD-GH-001` with duplicate search, existing issue URL and number, post-update readback, exact milestone and labels, correct open state, and no publication promise or private data.
11. Proof that `docs/plan/goal.md` is byte for byte unchanged and no protected plan file became an execution diary.
12. Remote audit proving no release, tag, mod platform upload, announcement, or public candidate occurred.

Any failed command, stale evidence, hash mismatch, unverified exact stack, missing recovery proof, documentation discrepancy, forbidden jar content, sensitive evidence, inaccessible or incorrect issue, premature issue closure, or publication blocks completion.

## Next Transition

This is the final phase. After tasks 001 through 012 pass, perform the plan-wide completion audit against the master from top to bottom, verify the owner-selected endpoint is exactly one fully validated and unpublished FutureShops `2.3.0` artifact plus the verified open continuation issue, and present the completion packet for owner acceptance.

Do not create another phase, publish the artifact, create an initial issue, or close the existing issue as part of this transition. Issue closure occurs only after future owner acceptance of the completed `2.3.0` outcome. Until that acceptance and every terminal gate pass, report the plan as incomplete.
