# Phase 000 Execution Plan

> **Plan ID:** PLAN-PHASE-000
> **Phase ID:** CORE-PHASE-000
> **Owner:** Repository governance and verification
> **Classification:** MANDATORY
> **Master plan:** [plan.md](../plan.md)
> **Phase sequence:** 000 of 007

## Purpose and Ownership

This phase establishes the execution control plane for the complete FutureShops defect closure effort. It proves the authoritative plan set, freezes an exact and sanitized repository and GitHub baseline, brings issues 22, 25, 32, 33, and 34 to a consistent evidence packet shape, requests every missing external input, installs the rolling duplicate-before-repair gate, proves support-line routing and version ownership, and makes the deterministic verification harness ready for later repair phases.

The canonical requirements are CORE-REQ-001, CORE-REQ-002, CORE-REQ-009, and CORE-REQ-016. The master plan owns product scope, owner decisions, global phase order, support-line versions, and completion authority. This file owns only the detailed execution of CORE-PHASE-000. It does not authorize defect repair, feature implementation, release publication, edits to either legacy plan, or work assigned to CORE-PHASE-001 through CORE-PHASE-007.

Phase execution must preserve the planning worktree and every pre-existing untracked or modified file. Baseline commands that can write generated output, caches, logs, or run directories must execute in clean isolated worktrees rooted at exact support-line revisions. Evidence from one support line must never be presented as evidence for the other.

## Evidence-Based Entry State

| Evidence class | Area | Finding | Source or command | Freshness condition |
|---|---|---|---|---|
| VERIFIED | Repository identity | The authoritative repository is `MCEnvision/FutureShops` at `/mnt/hermes/projects/FutureShops`, with `origin` set to `https://github.com/MCEnvision/FutureShops.git` | SRC-009, `git remote -v`, and `gh repo view MCEnvision/FutureShops` | Invalidated by a root, remote URL, owner, or repository rename change |
| VERIFIED | Starting revision | The planning branch `envy/polish_plan` and `origin/1.20.1` resolve to `c6709e12ca7084ee068b2497a577b8d47c12f6fd` | SRC-009, `git branch --show-current`, and `git rev-parse HEAD origin/1.20.1` | Invalidated by movement of either ref or a checkout change |
| OBSERVED | Secondary support revision | `origin/1.21.1` resolves to `247d8f6842bfa1f586e5b18a9aab67cabd3db89f` | SRC-009 and `git rev-parse origin/1.21.1` | Invalidated when the remote ref moves |
| VERIFIED | Legacy plan preservation | `FutureShops3-0Plan.MD` has SHA-256 `bb8d985a265c72d42d3ce39b05b0e4ab516549da1e5607fd1ed853f52685ac90`; `FutureShops3-1TradeOffersPlan.MD` has SHA-256 `d3ebf8948bf68efea34e81feacb1ab0c301efe3799372c740eb7504dd6042f64` | SRC-002, SRC-003, and `sha256sum` at authoring intake | Invalidated by any byte change to either file |
| OBSERVED | Planning worktree isolation | At authoring intake, `.github/pull_request_template.md`, `.github/workflows/ci.yml`, `.github/workflows/codeql.yml`, `plan.md`, and `run-data/` are untracked in the planning worktree | `git status --short --branch` on `envy/polish_plan` | Invalidated by any index, worktree, or untracked-file change; this is an ownership warning, not authorization to remove or stage files |
| VERIFIED | Forge line metadata | `origin/1.20.1` declares Minecraft 1.20.1, Forge 47.4.20, official 1.20.1 mappings, Java 17, Gradle 8.14.4, mod ID `futureshops`, and current version `3.0.0-beta.1` | SRC-009, `gradle.properties`, `build.gradle`, and `gradle/wrapper/gradle-wrapper.properties` at the exact ref | Invalidated by movement of `origin/1.20.1` or metadata changes |
| VERIFIED | NeoForge line metadata | `origin/1.21.1` declares Minecraft 1.21.1, NeoForge 21.1.233, ModDevGradle 2.0.141, Java 21, Gradle 8.8, mod ID `futureshops`, and current version `2.2.0` | SRC-009 and `git show origin/1.21.1:{gradle.properties,build.gradle,gradle/wrapper/gradle-wrapper.properties}` | Invalidated by movement of `origin/1.21.1` or metadata changes |
| OBSERVED | Issue 22 candidate | `envy/issue_22_neoforge` resolves to `bfba91f7b0c51b03d07117c4f1851c38a98f6186`, is one commit ahead of and contains `origin/1.21.1`, declares version `2.2.1`, and remains unmerged pending independent proof and EXT-001 | SRC-004, SRC-011, `git merge-base --is-ancestor`, `git rev-list --left-right --count`, and issue 22 | Invalidated by movement of the branch, a force push, a new issue comment, or movement of `origin/1.21.1` |
| OBSERVED | Open issue set | Issues 22, 25, 32, 33, and 34 are open. Issues 32, 33, and 34 still have `needs triage`; issue 25 records that the first attempted repair did not resolve the reported behavior | SRC-004 through SRC-008 and read-only `gh issue view` queries on 2026-09-01 | Invalidated by any issue edit, comment, label, transfer, closure, or new duplicate evidence |
| OBSERVED | Dependency maintenance | Pull request 28 is open against `1.20.1`, is separate from product defect scope, and must not be merged into a candidate merely to simplify baseline work | SRC-012 and `gh pr view 28` | Invalidated by pull request, dependency, check, merge, or base-branch changes |
| VERIFIED | GitHub prerequisite | The active GitHub identity is `EnVisione`, the repository is public, and the default branch is `1.20.1` | EXT-005, `gh auth status`, and `gh repo view` | Invalidated by authentication, scope, repository ownership, visibility, or default-branch changes |
| OBSERVED | Forge verification surfaces | The Forge line defines `test`, `runData`, `runGameTestServer`, `build`, `runServer`, and `runClient`; `check` also enforces beta identity and packaged-dependency boundaries | SRC-011, `build.gradle`, and repository AGENTS instructions | Invalidated by build script, wrapper, source-set, or task graph changes |
| OBSERVED | NeoForge verification surfaces | The NeoForge line defines unit tests through ModDevGradle and run configurations for data, GameTest server, dedicated server, and client; exact task names and successful execution still require a clean baseline query | SRC-011 and `git show origin/1.21.1:build.gradle` | Invalidated by build script, wrapper, plugin, or task graph changes |
| UNKNOWN | External evidence | EXT-001, EXT-002, EXT-003, and EXT-004 are unavailable or not yet proven at intake | Master Sections 4 and 10 | Updated only by exact evidence that satisfies the corresponding prerequisite contract |

No observed baseline result is candidate proof. Any support ref movement after capture requires a new baseline record before dependent work starts.

## Scope Boundaries

### Included Scope

- CORE-REQ-001. Validate one authoritative plan set, prove contiguous phase registration from CORE-PHASE-000 through CORE-PHASE-007, preserve the two legacy plans byte-for-byte, and remove ambiguity about unfinished-work authority without editing historical artifacts.
- CORE-REQ-002. Capture exact local and remote repository state, GitHub issue and pull request state, branch ancestry, versions, toolchains, configuration surfaces, tests, CI, dependency state, reproduction status, dirty files, and evidence gaps before repair.
- CORE-REQ-009. Define and exercise the rolling defect intake gate, including duplicate search, canonical issue creation or enrichment, confidential routing, requirement classification, failing evidence, and repair authorization state.
- CORE-REQ-016. Prove separate Forge and NeoForge source, work-branch, pull request, toolchain, version, migration, and evidence routes without integrating or repairing either line.
- EXT-005. Verify authenticated GitHub identity and access needed for issue, branch, pull request, check, and evidence synchronization.
- EXT-001 through EXT-004 intake. Post or refresh precise, sanitized evidence requests in the applicable issue records and record their unresolved status. This phase does not satisfy the external evidence itself.
- Deterministic harness readiness. Inventory exact tasks and fixtures, run non-repair baseline tests and builds at exact clean support refs, define runtime sentinels and stop conditions, and prove that later phases have isolated environments and reproducible evidence formats.

### Explicit Exclusions

- CORE-REQ-003 through CORE-REQ-008 and CORE-REQ-010 through CORE-REQ-015 are later-phase implementation or audit work. This phase may identify and file a defect, but it must not repair one.
- CORE-REQ-017 through CORE-REQ-020 are final reconciliation, candidate, repeated-audit, and closure work. Phase 000 creates inputs for them but cannot claim them complete.
- Issue 22 candidate integration belongs to CORE-PHASE-001. Phase 000 may inspect ancestry, diff scope, prior evidence, and branch metadata only.
- Issues 25, 32, and 34 reproduction and repair belong to CORE-PHASE-002. Phase 000 collects current reports, fixtures, and gaps only.
- Issue 33 implementation belongs to CORE-PHASE-003. Phase 000 records the owner-locked contract without designing a competing selector or modifying editor code.
- Security, command, persistence, and backend audits belong to CORE-PHASE-004 through CORE-PHASE-006. Phase 000 inventories their required harness inputs but does not perform or close the audits.
- FUT-001 through FUT-005 and NG-001 through NG-008 remain excluded. No artifact publication, stable designation, release creation, public tag, cross-line merge, destructive recovery, platform upgrade, or unrelated enhancement is authorized.
- Pull request 28 remains a separately assessed dependency change. It may be recorded as branch-health evidence but is not integrated by this phase.

## Phase Contract

### CORE-PHASE-000 — Governance, Baseline, Evidence, and Harness Readiness

**Objective:** Produce a complete, exact-revision readiness packet that validates plan authority, establishes current issue and repository facts, requests all missing external evidence, makes rolling defect intake enforceable, proves isolated branch and version routes, and demonstrates usable deterministic verification harnesses without repairing any product defect.
**Owner:** Repository governance
**Dependencies:** none
**Canonical requirements:** CORE-REQ-001, CORE-REQ-002, CORE-REQ-009, CORE-REQ-016
**Documentation and release impact:** The protected plan set and GitHub evidence records are reconciled. Legacy plans and ordinary product documentation are not edited. No release metadata is changed and no artifact is published.
**Next transition:** CORE-PHASE-001

**Entry criteria**

- `plan.md` is readable through EOF, declares plan schema 2, names CORE-PHASE-000 as active, and freezes the complete sequence CORE-PHASE-000 through CORE-PHASE-007.
- `plan.index.json` registers the master and exactly one phase file for every contiguous phase 000 through 007, and `plan.handoff.json` identifies CORE-PHASE-000 without redefining scope.
- The planning root resolves to `/mnt/hermes/projects/FutureShops`, `origin` resolves to `MCEnvision/FutureShops`, and the authenticated GitHub identity resolves to `EnVisione`.
- Both legacy plans exist and match the recorded entry hashes.
- Starting support refs and the issue 22 candidate ref are resolvable without modifying the planning worktree.
- Existing dirty and untracked state is recorded and treated as owner-controlled state.

**Implementation scope**

- CORE-REQ-001, CORE-REQ-002, CORE-REQ-009, and CORE-REQ-016 define the complete mandatory implementation boundary detailed below.

**Detailed implementation scope**

- Validate plan topology, requirement ownership, source traceability, prerequisite routing, and legacy-plan preservation.
- Capture exact Git, GitHub, build, dependency, issue, CI, test, configuration, and evidence state.
- Create or refresh one evidence packet per scoped issue and one rolling defect ledger for later findings.
- Request exact EXT evidence where absent, with privacy and non-destructive handling instructions.
- Prove branch ancestry and version routing for Forge `3.0.0-beta.2` on `1.20.1` and NeoForge `2.2.1` on `1.21.1`.
- Run and retain clean baseline verification at exact support refs without changing tracked support-line content.
- Freeze deterministic harness contracts, failure interpretation, invalidation rules, and downstream handoff data.

**Execution order**

- `P000-TASK-001` through `P000-TASK-011` execute the CORE-PHASE-000 task sequence in order.

**Detailed task sequence**

1. `P000-TASK-001` freezes repository provenance, dirty-state ownership, support refs, and legacy-plan hashes.
2. `P000-TASK-002` validates the complete authoritative plan set and requirement ownership.
3. `P000-TASK-003` captures the read-only GitHub and branch-governance baseline under EXT-005.
4. `P000-TASK-004` creates current defect evidence packets for issues 22, 25, 32, 33, and 34.
5. `P000-TASK-005` posts or refreshes precise EXT-001 through EXT-004 evidence requests and records blocker states.
6. `P000-TASK-006` installs and dry-runs the rolling duplicate-before-repair workflow without repairing a defect.
7. `P000-TASK-007` proves support-line ancestry, work-branch, pull request, version, toolchain, migration, and cross-line isolation routes.
8. `P000-TASK-008` runs the Forge exact-ref baseline in a clean isolated worktree.
9. `P000-TASK-009` runs the NeoForge exact-ref baseline in a separate clean isolated worktree.
10. `P000-TASK-010` freezes deterministic fixture, runtime, failure, evidence, and invalidation contracts for downstream phases.
11. `P000-TASK-011` reconciles the phase completion packet and opens the exact CORE-PHASE-001 entry gate.

Tasks 004 and 007 may proceed in parallel after tasks 001 through 003 pass. Tasks 008 and 009 may run in parallel only in separate worktrees with independent Gradle user homes or otherwise proven cache isolation. Task 005 follows packet creation so requests do not duplicate already supplied evidence. Task 006 follows the GitHub access baseline. Task 010 consumes both line baselines. Task 011 is strictly last.

**Required evidence**

- Valid plan-set report covering topology, registration, requirement ownership, sources, decisions, prerequisites, and phase transitions.
- Matching entry and exit hashes for both legacy plans.
- Exact Git and GitHub snapshot with revisions, branch ancestry, issue state, comments, pull requests, reviews, checks, workflows, rulesets, dependency state, and dirty files.
- Five complete issue evidence packets plus precise EXT requests and current blocker states.
- A recorded duplicate-search dry run and a reusable issue-before-repair decision record.
- Forge Java 17 and NeoForge Java 21 task inventories, exact baseline commands, results, logs, generated-diff checks, and failure classifications at the captured support refs.
- Support-line routing proof that prevents cross-loader changes and locks candidate versions without changing current metadata in this phase.
- Harness matrix naming fixtures, environments, sentinels, time bounds, conservation fields, sanitation rules, and evidence invalidation edges.

**Exit criteria**

- The plan set is structurally valid, canonical requirements have one phase owner, no registered phase is missing or duplicated, and the handoff points to CORE-PHASE-000.
- Both legacy plan hashes still match the entry values exactly.
- Every repository, GitHub, issue, branch, version, build, test, CI, dependency, configuration, and evidence finding is tied to an exact revision or timestamped query.
- Issues 22, 25, 32, 33, and 34 have current packets with no unclassified gap. Missing inputs are bound to EXT-001 through EXT-004 and precisely requested.
- The duplicate-before-repair gate is proven usable, including confidential vulnerability routing and material-scope escalation.
- The Forge and NeoForge routes are distinct, candidate versions are exact, pinned toolchains are preserved, and no cross-line integration has occurred.
- Required baseline tasks either pass at the exact ref or have a retained decisive failure and a canonical issue created before any later repair. Environment-only failures have an explicit prerequisite and rerun command and are not mislabeled as product defects.
- The downstream harness contract is executable without destructive data use or ambiguity about success, failure, rerun, and invalidation.
- No product defect repair, feature implementation, release publication, legacy-plan edit, or unrelated work has occurred.
- No known mandatory phase-owned governance, baseline, evidence-intake, routing, or harness-readiness defect remains.

## Inputs and Upstream Contracts

| Input or contract | Provider | Required state | Validation | Failure behavior |
|---|---|---|---|---|
| `plan.md` | Plan Creator integration | Complete through EOF, schema 2, all requirements and phases frozen | Parse headings, IDs, dependencies, roadmap, and Definition of Done | Stop with exact plan conflict; do not infer or repair authority during execution |
| `plan.index.json` | Plan Creator integration | Registers `plan.md` and phases 000 through 007 exactly once with current digests | Deterministic manifest validation | Stop plan execution until the authoring integration is corrected |
| `plan.handoff.json` | Plan Creator integration | Names CORE-PHASE-000 and the first unfinished evidence gate without redefining requirements | Compare with master Section 19 and index | Stop on semantic conflict; digest drift alone is classified under master rules |
| Legacy plan pair | SRC-002 and SRC-003 | Present, readable, and byte-identical to entry hashes | `sha256sum FutureShops3-0Plan.MD FutureShops3-1TradeOffersPlan.MD` | Stop CORE-REQ-001; preserve files and report exact expected and observed hashes |
| Repository identity | SRC-009 | Exact root, remote, default branch, and support refs resolve | Git root, remote, `gh repo view`, and ref checks | Stop remote work on mismatch; do not attach to or mutate another repository |
| GitHub identity and access | EXT-005 | Active identity is EnVisione with read and authorized repository-write capabilities needed by the master | `gh auth status`, repository read query, and least-privilege capability checks | Continue local read-only baseline only; record EXT-005 as blocked and perform no remote mutation |
| Current issues and comments | SRC-004 through SRC-008 | Complete unsanitized data remains at its source; execution output retains only sanitized necessary fields | Read all issue bodies, comments, links, labels, events, and related pull requests | Record query failure or privacy concern; do not fabricate packet fields |
| Support-line refs | SRC-009 | `origin/1.20.1`, `origin/1.21.1`, and issue 22 candidate ref resolve to exact commits | Fetch without merging, record object IDs, and verify ancestry | Stop the affected route on missing or rewritten history; preserve prior evidence |
| Build definitions | SRC-009 and SRC-011 | Wrapper, build files, task graph, source sets, and runtime configurations are readable at each exact ref | Wrapper checksum or validation, Java version, and `tasks` query | Classify setup versus repository failure before opening an issue |
| Owner decisions | DEC-001 through DEC-006 | Resolved and unchanged | Compare master decisions with packet templates and routing | Stop on contradiction; never silently revise scope or versions |

## Outputs and Downstream Contracts

| Output or contract | Consumer | Guaranteed state | Compatibility or versioning | Evidence |
|---|---|---|---|---|
| Validated plan-set report | All later phases | One authoritative master, one contiguous phase set, one owner per requirement, unchanged legacy plans | Contract schema 2; any later material plan change triggers master change classification | Validation output, index digest check, and legacy hashes |
| Exact repository baseline | CORE-PHASE-001 through CORE-PHASE-007 | Repository, refs, dirty state, GitHub state, toolchains, CI, dependencies, and gaps tied to timestamps and commits | Ref movement invalidates only evidence that consumes the moved ref | Git and GitHub query record |
| Issue 22 packet | CORE-PHASE-001 | Candidate ancestry, affected environment, current evidence, missing EXT-001 fields, and closure gate are explicit | NeoForge 1.21.1 and candidate 2.2.1 only | Issue link, candidate commit, diff inventory, and request link |
| Forge issue packets | CORE-PHASE-002 and CORE-PHASE-003 | Issues 25, 32, 33, and 34 have exact behavior, environment, evidence, gaps, and acceptance routing | Forge 1.20.1 and candidate 3.0.0-beta.2 only | Issue links and packet reconciliation record |
| Rolling defect intake contract | CORE-PHASE-001 through CORE-PHASE-007 | Every new finding must be searched, classified, and filed before repair | Applies to both lines; confidential findings use private handling | Duplicate query, canonical record, classification, and repair gate fields |
| Support-line routing record | CORE-PHASE-001 through CORE-PHASE-007 | Correct base ref, candidate version, Java, loader, Gradle, branch ancestry, PR base, and merge boundary are explicit | No merge or cherry-pick across support lines without separately proven compatible scope | Ancestry graph, metadata snapshot, and branch protection query |
| Forge baseline packet | CORE-PHASE-002 through CORE-PHASE-007 | Exact-ref task results, logs, generated-diff state, and environment are reproducible | Java 17, Forge 47.4.20, Gradle 8.14.4, Minecraft 1.20.1 | Commands, exit codes, decisive output, ref, and tool versions |
| NeoForge baseline packet | CORE-PHASE-001 and CORE-PHASE-007 | Exact-ref task results, logs, generated-diff state, and environment are reproducible | Java 21, NeoForge 21.1.233, ModDevGradle 2.0.141, Gradle 8.8, Minecraft 1.21.1 | Commands, exit codes, decisive output, ref, and tool versions |
| Harness contract | All repair and audit phases | Fixtures, runtime states, fault modes, sanitation, stop conditions, conservation fields, and invalidation rules are deterministic | Line-specific APIs remain isolated; shared product invariants remain identical | Harness inventory and dry-run results |
| Phase 001 handoff | CORE-PHASE-001 | Latest `origin/1.21.1` head and issue 22 candidate state are freshly reconciled; phase-owned gates are closed | Target version remains 2.2.1 | Completion packet and explicit first action |

## Work Packages

| Task ID | Requirement IDs | Work | Inputs and dependencies | Outputs | Affected components or interfaces | Verification |
|---|---|---|---|---|---|---|
| P000-TASK-001 | CORE-REQ-001, CORE-REQ-002 | Freeze root, remotes, refs, ancestry, dirty state, tool identity, and legacy hashes without changing owner files | SRC-002, SRC-003, SRC-009 | Provenance snapshot and isolation boundary | `.git`, legacy plans, planning worktree | Repeat queries and compare exact object IDs and hashes |
| P000-TASK-002 | CORE-REQ-001 | Validate master, index, handoff, and all registered phase files for completeness and non-conflict | P000-TASK-001 and complete plan set | Plan validation report | `plan.md`, `plan.index.json`, `plan.handoff.json`, `phases/plan-phase-000.md` through `phases/plan-phase-007.md` | Deterministic schema, ID, dependency, traceability, and digest validation |
| P000-TASK-003 | CORE-REQ-002, CORE-REQ-009, EXT-005 | Capture read-only GitHub repository, issue, PR, review, checks, CI, ruleset, dependency, milestone, Project, and security baseline | P000-TASK-001 and EXT-005 | Timestamped remote-state inventory | GitHub repository and support branches | Requery critical identities and compare API object IDs and revisions |
| P000-TASK-004 | CORE-REQ-002 | Normalize five current issue evidence packets without altering requirement meaning | P000-TASK-003, SRC-004 through SRC-008 | Current packet for each issue | Issues 22, 25, 32, 33, 34 | Packet completeness and source-link review |
| P000-TASK-005 | CORE-REQ-002, EXT-001, EXT-002, EXT-003, EXT-004 | Request only missing external evidence with exact, safe procedures and acceptance fields | P000-TASK-004 and DEC-004 | Request links and blocker records | Applicable GitHub issue discussions | Read back posted text, verify author mention and required fields, ensure no sensitive data is requested publicly |
| P000-TASK-006 | CORE-REQ-009 | Define and dry-run duplicate-before-repair and confidential-finding routing | P000-TASK-003 and DEC-002 | Reusable rolling-defect decision record | GitHub issue search and private vulnerability process | Search known issue 25 behavior and prove reuse rather than duplicate creation; no repair occurs |
| P000-TASK-007 | CORE-REQ-016 | Prove separate branch, version, toolchain, schema, work branch, PR base, merge, and evidence routes | P000-TASK-001 through P000-TASK-003, DEC-005 | Support-line routing record | `origin/1.20.1`, `origin/1.21.1`, `envy/issue_22_neoforge`, build metadata | Git ancestry, metadata, changed-path, remote settings, and required-check comparison |
| P000-TASK-008 | CORE-REQ-002, CORE-REQ-016 | Run Forge baseline and inspect all generated or packaged changes in an isolated exact-ref worktree | P000-TASK-001 and P000-TASK-007 | Forge baseline packet | Forge Gradle wrapper, tests, GameTests, data generation, build, server and client launchers | Exact commands, exit codes, logs, task report, JAR listing, and clean tracked diff |
| P000-TASK-009 | CORE-REQ-002, CORE-REQ-016 | Run NeoForge baseline and inspect all generated or packaged changes in a separate exact-ref worktree | P000-TASK-001 and P000-TASK-007 | NeoForge baseline packet | NeoForge Gradle wrapper, tests, data generation, GameTests, build, server and client launchers | Exact commands, exit codes, logs, task report, JAR listing, and clean tracked diff |
| P000-TASK-010 | CORE-REQ-002, CORE-REQ-009, CORE-REQ-016 | Freeze deterministic fixture, runtime, evidence, failure, sanitation, retention, and invalidation contracts | P000-TASK-004 through P000-TASK-009 | Executable harness contract | Test fixtures, isolated worlds, logs, GitHub evidence, candidate verification fields | Tabletop replay for each issue and later audit family; every required proof has an owner and rerun trigger |
| P000-TASK-011 | CORE-REQ-001, CORE-REQ-002, CORE-REQ-009, CORE-REQ-016 | Reconcile all phase evidence, recheck no-repair and legacy preservation boundaries, and issue the Phase 001 handoff | All earlier tasks | Phase completion packet and exact transition | Plan governance, GitHub records, line baselines | Final checksum, status, issue-packet, routing, and harness audit |

### P000-TASK-001 Ordering, Failure Handling, and Recovery

Capture `git status --short --branch`, `git diff --stat`, `git diff --cached --stat`, remotes, current branch, upstreams, tags relevant to both support lines, and exact object IDs before fetching. Fetch remote refs without checkout, merge, rebase, pruning, or force. Capture the same state after fetch so movement is explicit. Do not stage, clean, restore, delete, or relocate existing files. If the planning worktree changes during capture, restart only the provenance snapshot and retain both observations.

Hash both legacy plans before every later operation that could plausibly touch repository files and again in task 011. A mismatch is a phase stop. Recovery is restoration from verified repository history only after owner authorization; phase execution itself does not rewrite the legacy files.

### P000-TASK-002 Validation Rules

Validation must prove all of the following.

1. The index contains exactly `plan.md` and `phases/plan-phase-000.md` through `phases/plan-phase-007.md`, with no root-level phase files, gaps, duplicates, or unregistered phase plans.
2. CORE-REQ-001 through CORE-REQ-020 each have exactly one canonical phase assignment matching master Section 13.
3. Every phase dependency, next transition, prerequisite, source ID, decision ID, and requirement ID resolves.
4. CORE-PHASE-000 owns only CORE-REQ-001, CORE-REQ-002, CORE-REQ-009, and CORE-REQ-016.
5. Optional and future IDs retain `excluded` disposition, and no phase promotes them to implementation scope.
6. The handoff names CORE-PHASE-000 and its first unfinished gate. It does not treat a digest change as permission to rewrite the immutable goal.
7. Master and phase files do not contradict the locked candidate versions, support branches, external acceptance gates, publication exclusion, non-destructive recovery rules, or rolling defect policy.

Structural validation failure blocks execution before GitHub writes or baseline builds. Report the exact file, ID, duplicate or missing relation, expected value, and observed value.

### P000-TASK-003 Remote Baseline Fields

The GitHub baseline records repository identity and visibility, default branch, current support branch heads, protection or rulesets, allowed merge methods, required status checks, unresolved reviews, open pull requests and their bases, recent failing or cancelled workflows, reusable workflow revisions, Dependabot configuration and pull requests, dependency graph and alert availability, CodeQL and secret-scanning availability, milestones, repository Project linkage, labels, wiki state, and release state. Read every scoped issue body, comment, event, linked commit, and related pull request through the timestamp of capture.

Repository features must be classified as configured, already configured, unavailable, or blocked by authentication. Missing optional features do not block the baseline. Missing required checks, branch isolation, or issue traceability is a repository-owned governance finding and follows P000-TASK-006 before repair. This task performs no broad baseline repair.

### P000-TASK-004 Evidence Packet Schema

Every scoped issue packet contains these required fields.

- Canonical issue number, URL, title, open or closed state, labels, author, reporter contact route, last update time, and duplicate-search result.
- Affected support line, Minecraft version, loader version, FutureShops version, operating system, server or client role, mod list when relevant, and exact known-good or failing revision.
- Expected behavior, actual behavior, first known occurrence, reproducibility, frequency, severity, affected data, and privacy classification.
- Exact steps, required fixture, relevant sanitized configuration, state, logs, screenshots, transaction or request identities, and current evidence gaps.
- Suspected component boundary stated as a hypothesis, not a conclusion, until reproduced.
- Acceptance criteria, required regression fidelity, runtime environment, restart and reconnect needs, migration or recovery proof, and mandatory external prerequisite IDs.
- Candidate branch or commit, if one exists, with ancestry and changed-path inventory. Prior green results remain historical observations until rerun.
- Current blocker, next safe action, downstream phase owner, closure conditions, and links to later changes and proof.

Issue-specific minimums are as follows.

| Issue | Required packet emphasis | Evidence gap at intake | Phase owner after handoff |
|---|---|---|---|
| 22 | Affected Windows client, exact screens, blur lifecycle, intended backdrop, navigation, candidate commit, Java 21 client and build evidence | EXT-001 exact merged-candidate reporter acceptance | CORE-PHASE-001 |
| 25 | Preserved world copy, catalog and configuration inventory, startup and reload traces, first migration attempt, offer readiness, money and item availability | EXT-002 affected-world evidence and reporter acceptance | CORE-PHASE-002 |
| 32 | Preserved player and matching world context, exact invalid field or transition, two occurrences, unrelated inventory preservation, restart and reconnect behavior | EXT-003 exact invalid state or deterministic reproduction | CORE-PHASE-002 |
| 33 | Owner-locked searchable grid, bounded registry set, exact canonical NBT option, shared decimal price and stock, preview, skip, explicit replace, preservation, atomic commit | Implementation evidence does not yet exist; external evidence is not a prerequisite | CORE-PHASE-003 |
| 34 | Finite versus infinite stock fixtures, payment source, balances, inventory, stock, request UUID, escrow, claims, diagnostics, concurrent clients, retry and restart | EXT-004 controlled multiplayer environment and conservation evidence | CORE-PHASE-002 |

### P000-TASK-005 External Evidence Requests

Do not post a duplicate request when the issue already contains every required field. Otherwise post one concise lowercase GitHub comment under repository text rules, tag the issue author when evidence is unavailable, and read the comment back after submission. Never request credentials, private raw player data, a live production world, unsanitized NBT, or destructive reproduction.

| Prerequisite | Request destination | Exact requested evidence | Safety boundary | Blocking effect |
|---|---|---|---|---|
| EXT-001 | Issue 22 | Test the JAR built from the exact merged `1.21.1` candidate on the originally affected Windows setup; provide candidate revision, FutureShops version, Minecraft and NeoForge versions, operating system, affected screen names, navigation result, and a screenshot or concise blur result | No unrelated private client data or credentials | Issue 22 and final endpoint remain open; independent Phase 001 integration may proceed after internal gates |
| EXT-002 | Issue 25 | Provide a sanitized backup or exact inventory of catalog files, relevant FutureShops configuration, versions, startup and failure logs, and reproduction steps; later test the exact merged Forge candidate on a preserved copy through startup, restart, and catalog reload | Never test against the only world copy and never delete data | Issue 25 and final endpoint remain open; independent repairs may proceed only from valid preserved or equivalent fixtures |
| EXT-003 | Issue 32 | Provide a sanitized preserved affected state with matching FutureShops persistence context, or enough exact facts to build a deterministic equivalent fixture; identify the failing load or action without deleting or modifying the source | No public raw player data, sensitive NBT, UUID linkage, or production mutation; use a private transfer when needed | Root-cause claims, issue closure, and final endpoint remain blocked without exact proof |
| EXT-004 | Issue 34 and the execution environment record | Confirm access to an isolated Forge 1.20.1 dedicated server with at least two independent clients and ability to capture balances, inventories, stock, request IDs, claims, escrow, persisted state, and sanitized logs for success, rejection, disconnect, retry, restart, and reconnect | No live economy or unbacked world; stop on conservation mismatch | Controlled multiplayer dependent gates remain blocked, while unit and isolated integration work may continue |

Silence, an approximate reproduction, an older artifact, or a mock does not satisfy a prerequisite. Record the request URL and timestamp, not merely that a request was attempted.

### P000-TASK-006 Rolling Defect Gate

Before any later phase edits code for a newly verified finding, it must complete this sequence.

1. Record a sanitized finding statement, support line, affected revision, severity, component, observed behavior, and decisive evidence.
2. Search open and closed issues using behavior terms, exception text, stable identifiers, affected component, and version. Search private security records separately when disclosure would increase exploitability.
3. Classify the finding as an existing issue, a new repository-owned defect, a confidential vulnerability, an excluded unrelated enhancement, an environment failure, or a disproved concern.
4. Reuse and enrich the canonical issue when matched. Otherwise create exactly one issue before repair. Do not publicly disclose sensitive exploit details.
5. Bind the issue to acceptance criteria, phase, support line, failing evidence, regression level, prerequisites, and downstream invalidation edges.
6. Only then authorize a focused repair on the correct line. Link every change, test, runtime result, merged revision, and closure record back to the issue.

Dry-run the workflow against issue 25's already recorded unavailable-offer behavior. The correct result is reuse of issue 25, not a new issue and not a repair. A dry run must show the exact queries and matching evidence.

If a finding materially changes scope, endpoint, public behavior, cost, licensing, trust boundaries, destructive behavior, credentials, external communication, or irreversible remote state, stop with `PLAN_REVISION_REQUIRED`. If it is a verified defect within the rolling contract, file it and route it to the phase that owns the affected component. If discovered after that phase has passed, reopen the rolling loop at the earliest safe sequential point without stacking support-line branches.

### P000-TASK-007 Support-Line Routing Contract

| Route | Canonical base | Candidate version | Required toolchain | Work and integration boundary | Prohibited transfer |
|---|---|---|---|---|---|
| Forge | Latest approved `origin/1.20.1`, intake `c6709e12ca7084ee068b2497a577b8d47c12f6fd` | `3.0.0-beta.2` | Java 17, Minecraft 1.20.1, Forge 47.4.20, Gradle 8.14.4, official 1.20.1 mappings | One sequential `envy/` phase branch from the latest merged `1.20.1`; pull request base `1.20.1`; required checks and merge through GitHub | NeoForge build, registry, networking, client, metadata, or source changes without a separate affected-line proof |
| NeoForge | Latest approved `origin/1.21.1`, intake `247d8f6842bfa1f586e5b18a9aab67cabd3db89f` | `2.2.1` | Java 21, Minecraft 1.21.1, NeoForge 21.1.233, ModDevGradle 2.0.141, Gradle 8.8 | CORE-PHASE-001 branch derived from current `1.21.1`; candidate `envy/issue_22_neoforge` is inspected and integrated only after rebase or equivalent ancestry validation and all gates; pull request base `1.21.1` | ForgeGradle, Forge 1.20.1, Java 17, Forge persistence, or Forge-only audit changes |

The phase records actual work-branch names and object IDs when created by their owning phase. It does not create future phase branches. Before branch creation, fetch and verify that the intended base is the latest approved remote head and that no earlier dependent phase pull request remains unmerged. A support ref moving after baseline invalidates ancestry, metadata, CI, and baseline results for that line.

The routing record must compare persistent and configured formats touched by any proposed candidate. A changed schema, identifier, configuration key, protocol, or serialized field needs compatible read behavior, fixtures, backup guidance, and a rollback boundary in its owning later phase. No such change is performed here.

### P000-TASK-008 Forge Baseline Procedure

Use a clean isolated worktree detached at the captured `origin/1.20.1` object ID. Record `java -version`, wrapper distribution, Gradle version, task list, environment variables that affect Java or Gradle without printing secrets, and available disk and memory constraints. Do not copy the planning worktree's untracked files into the baseline worktree.

Run in this order, using `bash ./gradlew` and retaining exact exit codes and decisive output.

1. `bash ./gradlew tasks --all --no-daemon` to freeze task names.
2. `bash ./gradlew test --no-daemon`.
3. `bash ./gradlew runData --no-daemon` because data generation is part of the repository verification contract.
4. `bash ./gradlew runGameTestServer --no-daemon` because a Forge GameTest source set and server shop tests are present.
5. `bash ./gradlew build --no-daemon`, including `verifyBetaReleaseIdentity` and `verifyPackagedDependencyBoundary` through `check`.
6. Preflight `runServer` and `runClient` with bounded launch windows, documented readiness sentinels, graceful termination, and isolated run directories. This is harness readiness, not feature acceptance. If a display is required, record the virtual display implementation and dimensions.
7. Inspect the produced JAR name, manifest, `META-INF/mods.toml`, required resources, dependency boundary, and archive listing. Record the current baseline version as `3.0.0-beta.1`; do not rename it to the candidate version.
8. Run `git status --short`, tracked diff, generated-resource diff, and untracked output inventory in the isolated worktree. Generated differences are evidence to classify, not changes to copy into the planning worktree.

A dependency download or unavailable Java runtime is an environment blocker until reproduced with repository-controlled inputs. A deterministic compilation, test, data, GameTest, packaging, or startup failure at the exact clean ref is a candidate repository-owned defect and must pass P000-TASK-006 before repair. Do not change source or build metadata to make the baseline green.

### P000-TASK-009 NeoForge Baseline Procedure

Use a different clean isolated worktree detached at the captured `origin/1.21.1` object ID. Use Java 21 and the checked-in Gradle 8.8 wrapper. Query the exact task graph before assuming Forge task names map directly to ModDevGradle.

Run in this order, substituting only task names confirmed by the task query.

1. `bash ./gradlew tasks --all --no-daemon`.
2. `bash ./gradlew test --no-daemon`.
3. Run the confirmed NeoForge data-generation task.
4. Run the confirmed NeoForge GameTest server task when present.
5. `bash ./gradlew build --no-daemon`.
6. Preflight the confirmed dedicated-server and client tasks with bounded launch windows, readiness sentinels, graceful termination, and isolated run directories. Record virtual display details when used.
7. Inspect the produced JAR name, manifest, generated NeoForge metadata, required resources, dependencies, and archive listing. Record the baseline version as `2.2.0`; do not substitute the issue 22 candidate artifact.
8. Inspect tracked, generated, and untracked differences and retain only evidence.

Then repeat only the minimal task and metadata inventory against `bfba91f7b0c51b03d07117c4f1851c38a98f6186` needed to prepare CORE-PHASE-001. Do not accept prior branch claims as fresh proof, do not merge the branch, and do not modify its source. Record that it declares `2.2.1`, its exact changed-path set, its one-commit ancestry relationship at intake, and every difference from the freshly observed `origin/1.21.1` head.

### P000-TASK-010 Deterministic Harness Contract

The downstream harness inventory must name, for each workflow, the support line, exact source ref, Java and wrapper versions, fixture origin and digest, isolated world or client state, required mods and configuration, action sequence, request UUIDs, expected state transitions, success sentinel, failure sentinel, timeout, graceful shutdown, captured logs and state, sanitation, conservation fields, cleanup that preserves evidence, and rerun triggers.

At minimum it covers these harness families.

- Plan, Git, GitHub, issue, branch, CI, dependency, metadata, and JAR inspection.
- Forge and NeoForge unit, source-contract, data-generation, GameTest, build, server, and client tasks.
- Issue 22 screen lifecycle and affected Windows reporter acceptance.
- Issue 25 catalog load, migration, last-known-good state, startup, restart, reload, server snapshot, and client route.
- Issue 32 serialization, player lifecycle, preserved state, exact item proof, repeated restart, reconnect, and non-destructive recovery.
- Issue 34 finite and infinite stock, payment, escrow, delivery, claim, concurrency, retry, disconnect, restart, reconnect, and conservation.
- Issue 33 selection, exact canonical NBT identity, decimal parsing, stock bounds, preview, create, skip, replace, field preservation, permission change, stale state, atomic write failure, cancellation, and retry.
- Later security, command, persistence, economic invariant, and backend integration matrices named in CORE-PHASE-004 through CORE-PHASE-006.

Test data must be synthetic or sanitized and versioned by content digest. Runtime tests use isolated copies and never the only world or player-data copy. Stable request identities must appear in both action and result evidence where value can move. Evidence records always include exact source revision, command, environment, start and end time, exit status, decisive result, and retained artifact location. A timeout is a failure or blocker, never a pass.

### P000-TASK-011 Completion Reconciliation

Requery support refs, issue timestamps, pull request state, and required checks. Compare them with task 001 and task 003. If a ref moved, rerun only the baseline and ancestry evidence that consumes it. If an issue changed, refresh its packet and external request classification. Rehash the legacy plans and validate the plan set again.

Compare every repository diff with the phase boundary. Any product source, ordinary product documentation, build metadata, legacy plan, support branch, release, or unrelated file change caused by this phase is a completion blocker. Preserve evidence and revert only phase-owned accidental changes through a reviewed safe operation; never overwrite pre-existing owner work.

The final record names the exact first CORE-PHASE-001 action: reread `plan.md` and `phases/plan-phase-001.md` through EOF, fetch without merging, resolve the current `origin/1.21.1` and `envy/issue_22_neoforge` object IDs, compare them with the Phase 000 routing packet, and stop for rebaseline if ancestry or diff scope changed.

## Architecture and Implementation Boundaries

Phase 000 changes governance and evidence state only. It does not change runtime architecture. The following boundaries govern every artifact it produces.

- The master plan is the only product contract. Phase packets describe observations and execution proof; they cannot redefine scope, acceptance, versions, or completion.
- Git is the source of exact revision and ancestry truth. GitHub is the source of issue, pull request, review, check, and remote repository state. Build metadata at the exact ref is the source of runtime versions and task behavior.
- Issue descriptions and comments are requirements and evidence, not proof that an implementation is correct. Historical test claims remain observed until independently rerun at the named revision.
- The Forge and NeoForge lines share product invariants but not loader APIs, source compatibility, build logic, runtime tasks, persistence assumptions, work branches, pull requests, or artifact identities.
- Planning worktree state is not a test fixture. Baseline builds use isolated exact-ref worktrees. Generated output, Gradle caches, run worlds, crash reports, and logs do not enter tracked output.
- Evidence packets expose only the minimum sanitized context. Secrets, credentials, raw private player data, private NBT, and exploit-enabling details never enter public issue records.
- The rolling issue gate is before repair. A later diff with no canonical issue for a newly discovered defect is invalid regardless of technical quality.
- Branch and version routing is fail closed. A patch with mixed loader-specific changes, the wrong PR base, an unproven schema transfer, or an unexpected pinned-version update cannot proceed.
- Baseline failures are classified by ownership. Network outage, unavailable JDK, missing display, or machine resource exhaustion is not automatically a product defect. A deterministic clean-ref failure caused by repository content is not dismissed as environment noise.
- No baseline task may mutate a live server, live economy, reporter world, or unique data copy. Runtime harness shutdown is graceful and evidence preserving.

## Failure, Recovery, and Edge Cases

| Scenario | Detection | Required behavior | Recovery or rollback | Regression proof |
|---|---|---|---|---|
| Plan index is missing, discontinuous, or contradicts the master | Manifest or semantic validation fails | Stop before remote writes and builds; name exact missing or conflicting IDs | Correct only through authorized Plan Creator integration | Rerun complete plan-set validation |
| Legacy plan hash changes | Entry or exit `sha256sum` mismatch | Stop CORE-REQ-001 and preserve observed file | Do not overwrite; obtain owner-authorized restoration from verified history | Both hashes match recorded entry values |
| Planning worktree changes during capture | Status or diff differs between snapshots | Preserve both snapshots and identify paths and ownership | Restart provenance capture without cleaning or resetting | Stable repeated snapshot |
| GitHub identity is not EnVisione or access is insufficient | Authentication or capability query fails | Perform no remote mutation; continue safe local reads only when identity is certain | Restore authorized authentication outside the phase, then requery | EXT-005 identity and capability proof |
| Support ref moves after baseline | Object ID differs on refresh | Invalidate ancestry, metadata, CI, test, and build results that consume the old ref | Create a new exact-ref baseline; retain old packet as historical | Repeated baseline at new ref |
| Candidate branch was rewritten or no longer descends from base | Merge-base or rev-list result changes | Block CORE-PHASE-001 handoff | Reconcile through Phase 001 branch procedure; no blind merge or cherry-pick | Fresh ancestry and diff inventory |
| Duplicate evidence request would be posted | Existing comment already contains every requested field | Link and reuse the existing request | Post only a focused delta request if fields are missing | Read-back comparison with prerequisite fields |
| Reporter provides private or unsafe evidence publicly | Comment contains raw sensitive data or exploit details | Stop copying or quoting it; use repository security and moderation procedures | Minimize exposure and route sanitized evidence privately | Sanitized packet with source link and no replicated sensitive content |
| External evidence remains unavailable | No qualifying response or environment exists | Keep prerequisite and issue open; continue only independent work | Reissue only when new context materially improves the request | Blocker record names exact missing evidence |
| Baseline task times out | Bounded runner expires without success sentinel | Record timeout as failure or environment blocker, never success | Gracefully terminate, preserve logs, classify cause, rerun after prerequisite correction | Same command reaches deterministic terminal result |
| Baseline generates tracked differences | `git status` or diff shows changes after runData or build | Retain diff as evidence and do not copy changes to planning worktree | Discard isolated worktree only after evidence retention; file a defect if repository-owned | Clean rerun or canonical issue link |
| Clean-ref test or build fails deterministically | Same exact command fails with decisive repository-owned error | Run duplicate search and file or enrich issue before repair | Route repair to owning later phase | Failing baseline packet plus canonical issue |
| Environment failure resembles product failure | JDK, download, display, permissions, disk, or memory signal | Classify separately and do not create misleading product issue | Restore environment prerequisite without source changes | Task rerun at same ref and documented environment |
| Cross-line file or dependency appears in a route | Diff, build metadata, or PR base includes wrong loader line | Stop route and prevent integration | Recreate work from correct base in owning phase; preserve evidence | Clean changed-path and metadata inspection |
| A new finding is a material enhancement, not a defect | Expected behavior requires new owner decision or scope | Stop with `PLAN_REVISION_REQUIRED` | Owner decides whether to invoke Plan Creator | No implementation occurs under current plan |
| Pull request 28 changes during baseline | PR head, dependency set, checks, or state changes | Refresh dependency record but keep it separate | No merge or dependency update under Phase 000 | Updated PR packet and unchanged product baseline |
| Issue packet source conflicts with repository evidence | Version, revision, component, or behavior differs | Record both, mark the field unresolved, and request exact proof | Do not choose a convenient narrative or start repair | Packet contains source links and resolution criterion |

## Verification Matrix

| Requirement or task | Static or unit | Integration | Real workflow or runtime | Negative and recovery | Evidence artifact |
|---|---|---|---|---|---|
| CORE-REQ-001, P000-TASK-001 | Legacy hashes and repository identity checks | Planning worktree and ref reconciliation | Not applicable because this is governance proof | Hash mismatch and dirty-state drift handling | Provenance snapshot and before-and-after hashes |
| CORE-REQ-001, P000-TASK-002 | Schema, ID, link, digest, assignment, and dependency validation | Master, index, handoff, and phase semantic comparison | Not applicable | Missing, duplicate, unregistered, or contradictory plan artifact fails closed | Plan validation report |
| CORE-REQ-002, P000-TASK-003 | GitHub query completeness and object ID checks | Git-to-GitHub branch and check reconciliation | Authenticated read-only repository preflight | Auth failure, missing capability, stale query, and remote mismatch | Timestamped GitHub baseline |
| CORE-REQ-002, P000-TASK-004 | Five packet schema completeness checks | Issue links, commits, configs, tests, and prerequisites reconcile | Reporter evidence remains external | Conflicting, private, absent, or stale evidence is marked, not inferred | Issue evidence packet set |
| CORE-REQ-002, P000-TASK-005 | Request text checked against EXT contracts | Posted comment read-back and issue linkage | Reporter or environment response is not required for phase exit | Duplicate request, silence, unsafe data, or lower-fidelity response handling | Comment URLs, timestamps, and blocker records |
| CORE-REQ-009, P000-TASK-006 | Duplicate query and classification schema | Known issue 25 dry run links existing evidence | No code repair workflow is run | Confidential, excluded, environment, and material-scope routes | Duplicate-before-repair dry-run record |
| CORE-REQ-016, P000-TASK-007 | Version, loader, Java, Gradle, metadata, and changed-path comparison | Git ancestry, PR base, checks, and migration ownership | No integration occurs | Wrong base, moved ref, mixed-loader diff, and silent dependency drift | Support-line routing record |
| CORE-REQ-002 and CORE-REQ-016, P000-TASK-008 | Forge task inventory, unit tests, build checks, metadata and JAR inspection | Data generation and GameTests | Bounded dedicated-server and client harness preflight | Timeout, generated drift, startup failure, and environment classification | Forge exact-ref baseline packet |
| CORE-REQ-002 and CORE-REQ-016, P000-TASK-009 | NeoForge task inventory, unit tests, build, metadata and JAR inspection | Confirmed data and GameTest tasks | Bounded dedicated-server and client harness preflight | Task-name mismatch, timeout, generated drift, startup failure, and environment classification | NeoForge exact-ref baseline packet |
| CORE-REQ-002, CORE-REQ-009, CORE-REQ-016, P000-TASK-010 | Harness field and fixture completeness | Tabletop state and evidence-flow replay | Launcher dry runs and environment readiness | Missing fixture, nondeterministic identity, unsafe data, conservation stop, and invalidation | Harness contract and readiness matrix |
| P000-TASK-011 | Final hashes, plan validation, status, and route comparison | Completion packet reconciliation | Fresh remote and issue snapshot | Ref movement, packet staleness, accidental change, or open phase-owned defect | Signed-off phase completion packet outside the plan set |

### Baseline Fixtures, Environments, and Expected Results

- Forge baseline fixture is the clean tree at the captured `origin/1.20.1` object ID with repository-provided tests, GameTests, resources, and default isolated run configuration. Java 17 and the checked-in Gradle 8.14.4 wrapper are mandatory.
- NeoForge baseline fixture is the clean tree at the captured `origin/1.21.1` object ID with repository-provided tests, resources, and ModDevGradle run configurations. Java 21 and the checked-in Gradle 8.8 wrapper are mandatory.
- The issue 22 candidate inventory uses the exact candidate object ID only after current ancestry is confirmed. It is not substituted for the NeoForge support baseline.
- Runtime smoke readiness means the launcher reaches a predefined healthy initialization sentinel without a crash, dedicated-server client-class load, unresolved required dependency, or immediate fatal configuration failure. Graceful termination after the sentinel is expected and recorded. It does not prove issue acceptance.
- A successful baseline command has exit code zero and the expected test or build summary. A launcher preflight has the named readiness sentinel, no decisive fatal error, and a recorded graceful stop. Missing output, cancellation, skipped required tests, or timeout is not success.
- After every write-capable Gradle task, inspect tracked and generated differences. A clean baseline must not silently alter committed source. Intentional generated-resource differences are recorded and routed, never copied automatically.

### Rerun Order

When baseline or harness evidence is invalidated, rerun in this order: provenance and ref capture, toolchain and task inventory, focused failed task, full unit test, applicable data generation, applicable GameTests, build, server preflight, client preflight, JAR inspection, diff inspection, GitHub check reconciliation, then packet refresh. Later phases use the stricter full order in the master verification strategy for changed code.

## Documentation, Operations, and Release

This phase does not edit `README.md`, `DOCUMENTATION.md`, `docs/README.md`, focused guides, configuration examples, release notes, or wiki content because it does not change product behavior. If baseline inspection proves those documents are already inaccurate, the discrepancy follows CORE-REQ-009 and is routed to CORE-PHASE-007 or the affected component phase before repair.

Operational outputs are the provenance snapshot, GitHub baseline, five issue evidence packets, external evidence request links, rolling defect intake contract, support-line routing record, Forge baseline packet, NeoForge baseline packet, harness readiness matrix, blocker inventory, and phase completion packet. Store them outside the protected plan set using existing repository and GitHub evidence conventions, and link them by stable URL, commit, check run, or retained artifact identity. Do not use this phase file as a progress diary.

No configuration, schema, migration, runtime monitoring, artifact version, release metadata, tag, release, CurseForge page, Modrinth page, GitHub Release, or announcement is changed. Baseline JARs are local verification outputs only. Candidate `3.0.0-beta.2` and `2.2.1` artifacts are not produced or published by this phase.

Issue and other GitHub text created during execution follows repository lowercase and punctuation rules. Technical identifiers, literal paths, commands, versions, hashes, and links preserve required spelling. Evidence requests are concise, non-destructive, privacy preserving, and precise enough for a reporter to know what qualifies.

## Risks and Evidence Invalidation

| Risk | Prevention | Detection | Recovery | Evidence invalidated | Reverification |
|---|---|---|---|---|---|
| Plan set changes after validation | Record manifest and digests and classify changes under master rules | Digest or semantic comparison | Reread complete master and all registered phases; do not rewrite goal | Plan validation and downstream handoff | Full P000-TASK-002 validation |
| Legacy plans are touched | Hash before and after phase operations | SHA-256 mismatch | Stop and preserve; restore only with authorization | CORE-REQ-001 completion | Rehash and remap validation |
| Remote ref movement | Pin object IDs and refresh before handoff | `git rev-parse` differs | Rebaseline affected line | Ancestry, metadata, build, runtime, CI, and routing for that line | P000-TASK-007 plus affected baseline task |
| Existing dirty work is overwritten or mistaken for baseline | Use isolated worktrees and record ownership | Planning status or content changes | Stop, preserve evidence, and recover only phase-owned changes | Provenance and any result contaminated by local files | P000-TASK-001 and affected task |
| GitHub state becomes stale | Timestamp every query and requery at completion | Updated issue, PR, review, check, or ruleset | Refresh only affected packet and dependencies | Affected issue or governance evidence | P000-TASK-003 through P000-TASK-006 as applicable |
| Prior claimed build evidence is accepted as current | Require independent exact-ref execution | Revision, environment, or command differs | Rerun at exact captured ref | Baseline or candidate preparation evidence | P000-TASK-008 or P000-TASK-009 |
| Baseline cache contamination | Separate worktrees and cache identity, record cache mode | Unexpected outputs or nonreproducible rerun | Clear only phase-owned isolated cache and rerun | Affected task results | From toolchain inventory through affected command |
| Missing Java, network, display, or resources is misfiled as product defect | Environment classification checklist and clean rerun | Setup-specific decisive signal | Correct environment without source edits | Failure classification | Same command and ref in corrected environment |
| Evidence request exposes private data | Ask for sanitized minimal evidence and private transfer for sensitive state | Public comment review | Stop propagation and use security or moderation channel | Public packet and any copied evidence | Rebuild sanitized packet |
| Duplicate issue is created | Search open and closed records before creation | Matching behavior, component, exception, or identifier | Link and close duplicate only under normal repository governance; preserve canonical record | Rolling intake trace | P000-TASK-006 search and classification |
| New repair begins before issue record | Enforce issue link as branch and change gate | Diff or branch has no canonical issue | Stop repair and create or link record before further edits | All repair evidence | Restart from failing evidence after issue creation |
| Forge and NeoForge evidence is mixed | Separate routes, worktrees, refs, toolchains, packets, and PR bases | Metadata, path, dependency, or loader mismatch | Discard mixed evidence and rerun correct line | Entire contaminated line packet | P000-TASK-007 and affected baseline |
| Candidate branch diverges while Phase 001 waits | Requery ancestry at transition | Merge-base or diff changes | Block transition and reconcile from latest `1.21.1` | Issue 22 candidate inventory and baseline dependency | Fresh ancestry, diff, and targeted baseline |
| Baseline build modifies generated resources | Isolated worktree and post-task diff | Tracked or generated difference | Retain evidence, do not propagate, and file if repository-owned | Clean-baseline claim | Rerun after later canonical repair |
| Late issue response changes reproduction facts | Link packets to issue update times | New comment or attachment | Refresh packet and downstream assumptions | Affected reproduction and harness fields | Packet reconciliation and impacted harness dry run |
| Dependency PR 28 merges or changes | Keep dependency record separate and monitor base head | PR or support ref changes | Rebaseline Forge and reassess dependency delta | Forge metadata, dependency, test, build, and JAR evidence | P000-TASK-003, P000-TASK-007, and P000-TASK-008 |

## Phase Completion Packet

CORE-PHASE-000 may close only when the completion packet contains all of the following outside this protected plan file.

1. The exact repository root, remote URL, authenticated GitHub identity, default branch, planning branch, captured support heads, candidate branch head, upstreams, and timestamped dirty-state inventory.
2. Entry and exit SHA-256 values for `FutureShops3-0Plan.MD` and `FutureShops3-1TradeOffersPlan.MD`, both matching the values recorded in this phase.
3. The complete plan validation result for `plan.md`, `plan.index.json`, `plan.handoff.json`, and phases 000 through 007, including requirement ownership and transition checks.
4. The read-only GitHub baseline with issues, comments, pull requests, reviews, checks, workflows, branch governance, dependencies, milestones, Project linkage, security feature availability, wiki, and release state.
5. One complete and current evidence packet for each of issues 22, 25, 32, 33, and 34.
6. Direct links and timestamps for every EXT-001 through EXT-004 evidence request that was required, plus current availability and exact blocking effect.
7. The duplicate-before-repair dry run, classification taxonomy, confidential-routing rule, scope-escalation rule, and one canonical rolling issue inventory.
8. The Forge and NeoForge support-line routing records, including exact base revisions, candidate versions, Java, loader, Gradle, mappings where applicable, work-branch rules, PR bases, merge gates, schema transfer boundaries, and candidate branch ancestry.
9. Forge exact-ref baseline commands, Java and wrapper versions, task inventory, exit codes, decisive output, unit, data, GameTest, build, server and client readiness, JAR inspection, and post-task diff results.
10. NeoForge exact-ref baseline commands, Java and wrapper versions, task inventory, exit codes, decisive output, unit, data, GameTest, build, server and client readiness, JAR inspection, and post-task diff results.
11. Every baseline failure classified as repository-owned, environment-owned, external-blocked, or disproved, with a canonical issue link before any repair for repository-owned findings.
12. The deterministic harness matrix, fixture identities, environment requirements, runtime sentinels, time bounds, conservation fields, sanitation rules, retention links, failure stop conditions, and evidence invalidation graph.
13. Final proof that phase execution changed no product code, ordinary product documentation, legacy plan, support branch, version, release state, or unrelated owner file.
14. A concise blocker list. EXT-001 through EXT-004 may remain open under the approved blocked-plan policy, but no phase-owned governance, packet, routing, or harness action may remain unfinished.
15. The exact CORE-PHASE-001 first action and current object IDs needed to execute it safely.

## Next Transition

Transition only to CORE-PHASE-001. Do not start CORE-PHASE-002 or any Forge repair branch from Phase 000.

Before CORE-PHASE-001 performs any change, its owner must read `plan.md` and `phases/plan-phase-001.md` through EOF, consume the Phase 000 completion packet, fetch remote state without merging, and resolve the current object IDs for `origin/1.21.1` and `envy/issue_22_neoforge`. The owner must compare current ancestry, changed paths, version metadata, issue 22 packet, baseline results, and EXT-001 status with the Phase 000 record. Any movement or material diff change triggers the corresponding rebaseline and packet refresh before implementation or integration.

CORE-PHASE-001 may proceed with independent root-cause and integration work when its internal gates pass even if EXT-001 remains unavailable, exactly as the master permits. It may not close CORE-REQ-003, close issue 22, claim final acceptance, or open a later support-line phase based on reporter silence. The NeoForge work remains isolated to `1.21.1`, targets exactly `2.2.1`, and integrates only through its correct pull request and required checks.
