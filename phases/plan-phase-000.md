# Phase 000 Execution Plan

> **Plan ID:** PLAN-PHASE-000
> **Phase ID:** CORE-PHASE-000
> **Owner:** Repository governance
> **Classification:** MANDATORY
> **Master plan:** [plan.md](../plan.md)
> **Phase sequence:** 000 of 007

## Purpose and Ownership

This phase establishes the execution control plane for the complete FutureShops defect closure effort. It proves the authoritative plan set, freezes an exact and sanitized repository and GitHub baseline, freezes and reads every current comment for issues 22, 25, 32, 33, and 34 before any triage action, brings those issues to a consistent evidence packet shape, installs the rolling duplicate-before-repair gate, proves support-line routing and version ownership, records owner-approved local runtime capacity, and makes the deterministic verification harness ready for later repair phases.

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
| OBSERVED | Issue 22 candidate | `envy/issue_22_neoforge` resolves to `bfba91f7b0c51b03d07117c4f1851c38a98f6186`, is one commit ahead of and contains `origin/1.21.1`, declares version `2.2.1`, and has explicit owner acceptance; independent verification, pull request integration, exact merged-revision proof, and closure remain | SRC-001, SRC-004, SRC-011, `git merge-base --is-ancestor`, `git rev-list --left-right --count`, and issue 22 | Invalidated by movement of the branch, a force push, a new issue comment, or movement of `origin/1.21.1` |
| OBSERVED | Open issue set | Issues 22, 25, 32, 33, and 34 are open. Issues 32, 33, and 34 still have `needs triage`; issue 25 records that the first attempted repair did not resolve the reported behavior | SRC-004 through SRC-008 and read-only `gh issue view` queries on 2026-09-01 | Invalidated by any issue edit, comment, label, transfer, closure, or new duplicate evidence |
| OBSERVED | Dependency maintenance | Pull request 28 is open against `1.20.1`, is separate from product defect scope, and must not be merged into a candidate merely to simplify baseline work | SRC-012 and `gh pr view 28` | Invalidated by pull request, dependency, check, merge, or base-branch changes |
| VERIFIED | GitHub prerequisite | EXT-005 is available and authorized; the active GitHub identity is `EnVisione`, the repository is public, and the default branch is `1.20.1` | SRC-001, EXT-005, `gh auth status`, and `gh repo view` | Invalidated by authentication, scope, repository ownership, visibility, or default-branch changes |
| OBSERVED | Forge verification surfaces | The Forge line defines `test`, `runData`, `runGameTestServer`, `build`, `runServer`, and `runClient`; `check` also enforces beta identity and packaged-dependency boundaries | SRC-011, `build.gradle`, and repository AGENTS instructions | Invalidated by build script, wrapper, source-set, or task graph changes |
| OBSERVED | NeoForge verification surfaces | The NeoForge line defines unit tests through ModDevGradle and run configurations for data, GameTest server, dedicated server, and client; exact task names and successful execution still require a clean baseline query | SRC-011 and `git show origin/1.21.1:build.gradle` | Invalidated by build script, wrapper, plugin, or task graph changes |
| OBSERVED | Historical prerequisite state | EXT-001 through EXT-004 are resolved or superseded traceability records, not active dependencies, blockers, evidence requests, or endpoint gates | SRC-001, DEC-004, DEC-007, and Master Sections 4 and 10 | Invalidated only by an explicit owner-approved plan revision; issue or reporter latency does not reactivate them |
| OBSERVED | Local runtime capacity | The 64 GB workstation is the default isolated dedicated-server and multiple-client environment; the 96 GB node1 host is an authorized temporary isolated-server fallback when it improves capacity or repeatability | SRC-001, SRC-014, and DEC-007 | Invalidated by a verified capacity or access change, which requires local rescheduling rather than an issue-level external blocker |

No observed baseline result is candidate proof. Any support ref movement after capture requires a new baseline record before dependent work starts.

## Scope Boundaries

### Included Scope

- CORE-REQ-001. Validate one authoritative plan set, prove contiguous phase registration from CORE-PHASE-000 through CORE-PHASE-007, preserve the two legacy plans byte-for-byte, and remove ambiguity about unfinished-work authority without editing historical artifacts.
- CORE-REQ-002. Capture exact local and remote repository state, GitHub issue and pull request state, branch ancestry, versions, toolchains, configuration surfaces, tests, CI, dependency state, reproduction status, dirty files, and evidence gaps before repair.
- CORE-REQ-009. Define and exercise the rolling defect intake gate, including duplicate search, canonical issue creation or enrichment, confidential routing, requirement classification, failing evidence, and repair authorization state.
- CORE-REQ-016. Prove separate Forge and NeoForge source, work-branch, pull request, toolchain, version, migration, and evidence routes without integrating or repairing either line.
- EXT-005. Verify authenticated GitHub identity and access needed for issue, branch, pull request, check, and evidence synchronization.
- Historical prerequisite reconciliation. Preserve EXT-001 through EXT-004 only as resolved or superseded traceability and route their successor proof into repository-controlled local campaigns. Do not post evidence requests or represent them as dependencies or blockers.
- Issue 22 readiness. Preserve explicit owner acceptance and the existing candidate evidence, then hand off fresh independent verification and integration into `1.21.1` as the first downstream action.
- Forge campaign readiness. Freeze the issue 25 beta-transition and current-state matrix, the issue 32 deterministic local corruption and non-destructive recovery campaign, and the issue 34 isolated dedicated-server and multiple-client campaign.
- Local capacity. Reserve the 64 GB workstation as the default runtime environment and record the 96 GB node1 temporary-server fallback without mixing artifacts, worlds, credentials, or evidence between hosts.
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

**Objective:** Produce a complete, exact-revision readiness packet that validates plan authority, freezes and reads every current scoped-issue comment before triage, establishes issue 22 owner-accepted evidence and executable local campaigns for issues 25, 32, and 34, makes rolling defect intake enforceable, proves isolated branch and version routes, and demonstrates usable deterministic verification harnesses without repairing any product defect.
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
- Freeze every scoped issue body, comment, event, attachment reference, and linked change through a captured timestamp or cursor before applying triage labels, dispositions, comments, or packet conclusions.
- Create or refresh one evidence packet per scoped issue and one rolling defect ledger for later findings.
- Reconcile EXT-001 through EXT-004 as historical only and define repository-controlled successor evidence for issue 22 verification, issue 25 compatibility, issue 32 corruption and recovery, and issue 34 local multiplayer.
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
5. `P000-TASK-005` locks issue 22 owner-accepted evidence and the executable issue 25, 32, and 34 local campaign contracts while preserving EXT-001 through EXT-004 as historical traceability only.
6. `P000-TASK-006` installs and dry-runs the rolling duplicate-before-repair workflow without repairing a defect.
7. `P000-TASK-007` proves support-line ancestry, work-branch, pull request, version, toolchain, migration, and cross-line isolation routes.
8. `P000-TASK-008` runs the Forge exact-ref baseline in a clean isolated worktree.
9. `P000-TASK-009` runs the NeoForge exact-ref baseline in a separate clean isolated worktree.
10. `P000-TASK-010` freezes deterministic fixture, runtime, failure, evidence, and invalidation contracts for downstream phases.
11. `P000-TASK-011` reconciles the phase completion packet and opens the exact CORE-PHASE-001 entry gate.

Tasks 004 and 007 may proceed in parallel after tasks 001 through 003 pass. Tasks 008 and 009 may run in parallel only in separate worktrees with independent Gradle user homes or otherwise proven cache isolation. Task 005 follows the frozen-comment packet creation so its matrices and campaign contracts cannot omit issue evidence. Task 006 follows the GitHub access baseline. Task 010 consumes both line baselines. Task 011 is strictly last.

**Required evidence**

- Valid plan-set report covering topology, registration, requirement ownership, sources, decisions, prerequisites, and phase transitions.
- Matching entry and exit hashes for both legacy plans.
- Exact Git and GitHub snapshot with revisions, branch ancestry, issue state, comments, pull requests, reviews, checks, workflows, rulesets, dependency state, and dirty files.
- Five complete issue evidence packets with a frozen pre-triage comment snapshot, explicit owner decisions, local campaign contracts, and historical EXT-001 through EXT-004 reconciliation.
- A recorded duplicate-search dry run and a reusable issue-before-repair decision record.
- Forge Java 17 and NeoForge Java 21 task inventories, exact baseline commands, results, logs, generated-diff checks, and failure classifications at the captured support refs.
- Support-line routing proof that prevents cross-loader changes and locks candidate versions without changing current metadata in this phase.
- Harness matrix naming fixtures, environments, sentinels, time bounds, conservation fields, sanitation rules, and evidence invalidation edges.

**Exit criteria**

- The plan set is structurally valid, canonical requirements have one phase owner, no registered phase is missing or duplicated, and the handoff points to CORE-PHASE-000.
- Both legacy plan hashes still match the entry values exactly.
- Every repository, GitHub, issue, branch, version, build, test, CI, dependency, configuration, and evidence finding is tied to an exact revision or timestamped query.
- Issues 22, 25, 32, 33, and 34 have current packets based on every issue comment frozen and read before triage, with no unclassified gap and no reporter artifact misclassified as an active prerequisite.
- Issue 22 records explicit owner acceptance and a fresh Phase 001 verification and integration route. Issue 25 has a complete beta-transition and current-state matrix contract. Issue 32 has a deterministic local corruption and non-destructive recovery campaign. Issue 34 has a local dedicated-server and at least two-client campaign assigned to the 64 GB workstation with the 96 GB node1 fallback.
- The duplicate-before-repair gate is proven usable, including confidential vulnerability routing and material-scope escalation.
- The Forge and NeoForge routes are distinct, candidate versions are exact, pinned toolchains are preserved, and no cross-line integration has occurred.
- Required baseline tasks either pass at the exact ref or have a retained decisive failure and a canonical issue created before any later repair. Local environment failures have an exact remediation record and rerun command and are not mislabeled as product defects or external issue blockers.
- The downstream harness contract is executable without destructive data use or ambiguity about success, failure, rerun, and invalidation.
- No product defect repair, feature implementation, release publication, legacy-plan edit, or unrelated work has occurred.
- No known mandatory phase-owned governance, baseline, evidence-intake, routing, or harness-readiness defect remains.

## Inputs and Upstream Contracts

| Input or contract | Provider | Required state | Validation | Failure behavior |
|---|---|---|---|---|
| `plan.md` | Plan Creator integration | Complete through EOF, schema 2, all requirements and phases frozen | Parse headings, IDs, dependencies, roadmap, and Definition of Done | Stop with exact plan conflict; do not infer or repair authority during execution |
| `plan.index.json` | Plan Creator integration | Registers `plan.md` and phases 000 through 007 exactly once with correct IDs, paths, owners, classifications, and dependencies | Deterministic manifest validation | Stop plan execution until the authoring integration is corrected |
| `plan.handoff.json` | Plan Creator integration | Names CORE-PHASE-000 and the first unfinished evidence gate without redefining requirements | Compare with master Section 19 and index | Stop on semantic conflict; digest drift alone is classified under master rules |
| Legacy plan pair | SRC-002 and SRC-003 | Present, readable, and byte-identical to entry hashes | `sha256sum FutureShops3-0Plan.MD FutureShops3-1TradeOffersPlan.MD` | Stop CORE-REQ-001; preserve files and report exact expected and observed hashes |
| Repository identity | SRC-009 | Exact root, remote, default branch, and support refs resolve | Git root, remote, `gh repo view`, and ref checks | Stop remote work on mismatch; do not attach to or mutate another repository |
| GitHub identity and access | EXT-005 | Active identity is EnVisione with read and authorized repository-write capabilities needed by the master | `gh auth status`, repository read query, and least-privilege capability checks | Continue local read-only baseline only; record EXT-005 as blocked and perform no remote mutation |
| Current issues and comments | SRC-004 through SRC-008 | Complete unsanitized data remains at its source; a frozen timestamped snapshot exists; execution output retains only sanitized necessary fields | Freeze and read all issue bodies, comments, links, labels, events, attachments, and related pull requests before any triage or mutation | Record query failure or privacy concern and stop triage for the affected issue; do not fabricate packet fields or post an evidence request |
| Support-line refs | SRC-009 | `origin/1.20.1`, `origin/1.21.1`, and issue 22 candidate ref resolve to exact commits | Fetch without merging, record object IDs, and verify ancestry | Stop the affected route on missing or rewritten history; preserve prior evidence |
| Build definitions | SRC-009 and SRC-011 | Wrapper, build files, task graph, source sets, and runtime configurations are readable at each exact ref | Wrapper checksum or validation, Java version, and `tasks` query | Classify setup versus repository failure before opening an issue |
| Owner decisions | DEC-001 through DEC-007 | Resolved and unchanged | Compare master decisions with packet templates, historical prerequisite treatment, local capacity, and routing | Stop on contradiction; never silently revise scope, versions, or evidence authority |

## Outputs and Downstream Contracts

| Output or contract | Consumer | Guaranteed state | Compatibility or versioning | Evidence |
|---|---|---|---|---|
| Validated plan-set report | All later phases | One authoritative master, one contiguous phase set, one owner per requirement, unchanged legacy plans | Contract schema 2; any later material plan change triggers master change classification | Validation output, index digest check, and legacy hashes |
| Exact repository baseline | CORE-PHASE-001 through CORE-PHASE-007 | Repository, refs, dirty state, GitHub state, toolchains, CI, dependencies, and gaps tied to timestamps and commits | Ref movement invalidates only evidence that consumes the moved ref | Git and GitHub query record |
| Issue 22 packet | CORE-PHASE-001 | Every comment, explicit owner acceptance, candidate ancestry, affected environment, prior evidence, independent rerun gate, pull request route, exact merged-revision gate, and closure conditions are explicit | NeoForge 1.21.1 and candidate 2.2.1 only | Frozen issue snapshot, candidate commit, diff inventory, owner acceptance, and Phase 001 first action |
| Forge issue packets | CORE-PHASE-002 and CORE-PHASE-003 | Issues 25, 32, 33, and 34 have every comment, exact behavior, environment, local fixtures, campaign matrix, acceptance routing, and evidence invalidation rules | Forge 1.20.1 and candidate 3.0.0-beta.2 only | Frozen issue snapshots, matrix and campaign contracts, local-capacity record, and packet reconciliation |
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
| P000-TASK-004 | CORE-REQ-002 | Freeze and read every current issue body, comment, event, attachment reference, and linked change before triage, then normalize five evidence packets without altering requirement meaning | P000-TASK-003, SRC-004 through SRC-008 | Immutable pre-triage snapshot and current packet for each issue | Issues 22, 25, 32, 33, 34 | Cursor or timestamp coverage, comment count and identity reconciliation, packet completeness, and source-link review |
| P000-TASK-005 | CORE-REQ-002, CORE-REQ-016 | Reconcile historical EXT-001 through EXT-004 and establish issue 22 accepted evidence plus local issue 25, 32, and 34 campaign contracts | P000-TASK-004, DEC-004, DEC-005, DEC-007, SRC-014 | Issue 22 verification handoff, issue 25 matrix, issue 32 campaign, issue 34 campaign, and local-capacity record | Issue packets, runtime harness, branch routes, workstation and node1 host boundary | Trace every owner decision and issue comment into a matrix row, fixture, runtime action, acceptance gate, or historical disposition; prove no active EXT-001 through EXT-004 dependency remains |
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
7. Master and phase files do not contradict the locked candidate versions, support branches, owner-accepted issue 22 evidence, local issue 25, 32, and 34 campaigns, historical-only treatment of EXT-001 through EXT-004, available EXT-005, publication exclusion, non-destructive recovery rules, or rolling defect policy.

Structural validation failure blocks execution before GitHub writes or baseline builds. Report the exact file, ID, duplicate or missing relation, expected value, and observed value.

### P000-TASK-003 Remote Baseline Fields

The GitHub baseline records repository identity and visibility, default branch, current support branch heads, protection or rulesets, allowed merge methods, required status checks, unresolved reviews, open pull requests and their bases, recent failing or cancelled workflows, reusable workflow revisions, Dependabot configuration and pull requests, dependency graph and alert availability, CodeQL and secret-scanning availability, milestones, repository Project linkage, labels, wiki state, and release state. For each scoped issue, freeze the body, every current comment, event, attachment reference, linked commit, and related pull request through one captured timestamp or pagination cursor. Record comment IDs, authors, update times, and counts. Read that complete frozen set before changing labels, posting comments, deciding triage, classifying compatibility, or drafting a repair route.

Repository features must be classified as configured, already configured, unavailable, or blocked by authentication. Missing optional features do not block the baseline. Missing required checks, branch isolation, or issue traceability is a repository-owned governance finding and follows P000-TASK-006 before repair. This task performs no broad baseline repair.

### P000-TASK-004 Evidence Packet Schema

Every scoped issue packet contains these required fields.

- Canonical issue number, URL, title, open or closed state, labels, author, last update time, frozen-through timestamp or cursor, comment IDs and count, and duplicate-search result.
- Affected support line, Minecraft version, loader version, FutureShops version, operating system, server or client role, mod list when relevant, and exact known-good or failing revision.
- Expected behavior, actual behavior, first known occurrence, reproducibility, frequency, severity, affected data, and privacy classification.
- Exact steps, required fixture, relevant sanitized configuration, state, logs, screenshots, transaction or request identities, and current evidence gaps.
- Suspected component boundary stated as a hypothesis, not a conclusion, until reproduced.
- Acceptance criteria, required regression fidelity, runtime environment, restart and reconnect needs, migration or recovery proof, owner disposition, and any historical prerequisite traceability IDs that must not become active dependencies.
- Candidate branch or commit, if one exists, with ancestry and changed-path inventory. Prior green results remain historical observations until rerun.
- Current blocker, next safe action, downstream phase owner, closure conditions, and links to later changes and proof.

Issue-specific minimums are as follows.

| Issue | Required packet emphasis | Evidence gap at intake | Phase owner after handoff |
|---|---|---|---|
| 22 | Every comment, explicit owner acceptance, affected screen lifecycle, intended backdrop, navigation, candidate commit, Java 21 client and build evidence, line-specific pull request, and merged-revision proof | Fresh independent verification and integration are Phase 001 work; EXT-001 is historical only | CORE-PHASE-001 |
| 25 | Every comment, supported current state, direct upgrade routes, relevant intermediate betas, first migration repair, malformed entries, removed registry items, startup, restart, reload, readiness, and recovery behavior | Complete local beta-transition and current-state matrix; EXT-002 is historical only | CORE-PHASE-002 |
| 32 | Every comment, deterministic corruption seeds, malformed and version-skewed fields, oversized and truncated state, duplicate and cross-mod state, crash cuts, modded NBT sentinels, owned-field isolation, restart, reconnect, and repeated recovery | Executable local corruption and non-destructive recovery campaign; EXT-003 is historical only | CORE-PHASE-002 |
| 33 | Owner-locked searchable grid, bounded registry set, exact canonical NBT option, shared decimal price and stock, preview, skip, explicit replace, preservation, atomic commit | Implementation evidence does not yet exist; external evidence is not a prerequisite | CORE-PHASE-003 |
| 34 | Every comment, finite versus infinite stock fixtures, payment source, balances, inventory, stock, request UUID, escrow, claims, diagnostics, concurrent clients, retry, disconnect, restart, and reconnect | Local dedicated Forge server and at least two independent clients on the 64 GB workstation, with the 96 GB node1 server fallback; EXT-004 is historical only | CORE-PHASE-002 |

### P000-TASK-005 Owner Disposition and Local Campaign Contracts

EXT-001 through EXT-004 are resolved or superseded historical traceability IDs. They must appear only where needed to explain why an earlier gate no longer controls execution. They are not phase dependencies, blockers, evidence-request destinations, reporter obligations, or endpoint gates. Do not post or refresh evidence requests for them. EXT-005 remains the only active external prerequisite and is available at intake.

| Issue | Phase 000 contract | Required downstream execution | Environment and safety boundary | Phase 000 proof |
|---|---|---|---|---|
| 22 | Bind every frozen comment, the owner-accepted root cause, commit `bfba91f7b0c51b03d07117c4f1851c38a98f6186`, prior regression, client smoke, build, and artifact evidence into one packet | CORE-PHASE-001 independently reruns the screen-lifecycle regression and Java 21 NeoForge checks, integrates only into `1.21.1` through the required pull request workflow, verifies the exact merged revision, and closes issue 22 | No cross-line transfer and no reporter acceptance gate. Current `origin/1.21.1` ancestry and diff scope are revalidated before integration | Owner acceptance source, frozen issue snapshot, candidate ancestry, changed paths, historical evidence inventory, exact first action, and no EXT-001 dependency |
| 25 | Define a row-complete beta-transition and current-state matrix covering supported current state, documented direct upgrade routes, relevant intermediate betas, the first migration repair, valid and malformed catalogs, removed registry items, startup, restart, reload, server snapshots, client routes, last-known-good preservation, and recovery | CORE-PHASE-002 executes every row locally, repairs any defect reachable from supported current state, or produces an evidence-backed owner-approved compatibility disposition for an unsupported intermediate transition with non-destructive recovery documentation | Synthetic or sanitized catalog and configuration fixtures only. Never delete data or test against the only world copy | Frozen comment-to-row traceability, fixture requirements, expected result and first-divergence fields, disposition rule, and no EXT-002 dependency |
| 32 | Define a deterministic corruption and recovery campaign with stable seeds and fixture hashes for malformed, truncated, oversized, old, newer, unknown, duplicate, cross-mod, partial-write, and crash-cut state; include modded NBT sentinels and field ownership assertions | CORE-PHASE-002 isolates the first FutureShops-owned boundary, repairs proven causes, and proves repeated restart, reconnect, transaction, and recovery without deletion, duplication, or unrelated-field change | Use generated isolated player and world copies. Stop mutation on an unexpected checksum, ownership, or conservation change. Preserve all evidence and restore one complete matching snapshot | Seed and hash schema, crash-point catalog, before-and-after field proof, unrelated sentinel contract, recovery stop conditions, workstation capacity, and no EXT-003 dependency |
| 34 | Define a finite-versus-infinite stock matrix for success, insufficient funds, stale stock, concurrent buyers, full inventory, provider failure, disconnect, retry, restart, and reconnect with exact request and conservation fields | CORE-PHASE-002 runs an isolated Forge 1.20.1 dedicated server with at least two independent clients, identifies the divergent path, repairs the authoritative transaction boundary, and produces zero-delta conservation evidence | Default to the 64 GB workstation. Use the authorized 96 GB node1 host only as a temporary isolated server fallback. Pin source revision, mods, configuration, world digest, client identities, memory allocation, and host role. Never use a live economy or unbacked world | Runnable topology, host selection and fallback rule, state capture schema, readiness and failure sentinels, conservation equation, restart and reconnect sequence, and no EXT-004 dependency |

Every comment or owner statement must map to a matrix row, fixture field, acceptance gate, explicit compatibility disposition, or historical note. Unmapped comments block Phase 000 completion because triage would be incomplete. New reporter evidence may refine a local fixture after the frozen baseline, but its absence never blocks these campaigns or reactivates EXT-001 through EXT-004.

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
- Issue 22 owner-accepted screen lifecycle correction, exact candidate ancestry, independent regression rerun, line-specific pull request integration, exact merged-revision verification, and closure.
- Issue 25 beta-transition and current-state rows for catalog load, migration, last-known-good state, startup, restart, reload, server snapshot, client route, supported-state repair, and unsupported-intermediate compatibility disposition.
- Issue 32 deterministic seeded corruption, structured fuzzing, crash cuts, modded NBT sentinels, owned-field isolation, exact item proof, repeated restart, reconnect, transaction, and non-destructive recovery.
- Issue 34 finite and infinite stock, payment, escrow, delivery, claim, concurrency, retry, disconnect, restart, reconnect, and conservation on an isolated dedicated server with at least two clients, using the 64 GB workstation by default and the 96 GB node1 temporary-server fallback when needed.
- Issue 33 selection, exact canonical NBT identity, decimal parsing, stock bounds, preview, create, skip, replace, field preservation, permission change, stale state, atomic write failure, cancellation, and retry.
- Later security, command, persistence, economic invariant, and backend integration matrices named in CORE-PHASE-004 through CORE-PHASE-006.

Test data must be synthetic or sanitized and versioned by content digest. Runtime tests use isolated copies and never the only world or player-data copy. Stable request identities must appear in both action and result evidence where value can move. Evidence records always include exact source revision, command, environment, start and end time, exit status, decisive result, and retained artifact location. A timeout is a failure or blocker, never a pass.

### P000-TASK-011 Completion Reconciliation

Requery support refs, issue timestamps, pull request state, and required checks. Compare them with task 001 and task 003. If a ref moved, rerun only the baseline and ancestry evidence that consumes it. If an issue changed, freeze and read the delta before refreshing its packet, historical prerequisite classification, and local campaign mappings. Rehash the legacy plans and validate the plan set again.

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
- The frozen issue snapshot precedes triage. No label, comment, disposition, compatibility conclusion, or repair route may be applied until every current scoped-issue comment through the captured cursor or timestamp has been read and reconciled.
- EXT-001 through EXT-004 are historical only. Execution cannot request them, depend on them, block on them, or use them to weaken repository-controlled local proof. EXT-005 is the available GitHub authority boundary.
- Local runtime capacity is evidence infrastructure, not an external dependency. The workstation and node1 runs remain isolated by exact revision, world digest, configuration, credentials, logs, and host role.
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
| Scoped issue comment is missed before triage | Frozen cursor, timestamp, comment IDs, or count do not reconcile | Stop triage and remote mutation for that issue | Requery through a new frozen boundary, read every missing comment, and rebuild affected packet conclusions | Complete comment inventory and packet traceability |
| A new comment arrives after the freeze | Issue update time or comment count changes | Preserve the prior snapshot, freeze the delta, and read it before dependent triage or handoff | Refresh only affected packet, matrix rows, and invalidated harness assumptions | Old and new cursors plus reconciled packet |
| Reporter provides private or unsafe evidence publicly | Comment contains raw sensitive data or exploit details | Stop copying or quoting it; use repository security and moderation procedures | Minimize exposure and route sanitized evidence privately | Sanitized packet with source link and no replicated sensitive content |
| Historical prerequisite is treated as active | Packet, dependency, issue comment, or blocker list names EXT-001 through EXT-004 as required future input | Stop the affected handoff and remove the false dependency through the authorized plan integration path | Rebuild packet against DEC-004 and DEC-007 without requesting reporter or hardware proof | Semantic scan shows historical-only references and repository-controlled successor evidence |
| Workstation capacity is temporarily insufficient | Memory, display, process, or timing telemetry violates the campaign budget | Preserve the run as failed environment evidence and do not weaken test fidelity | Move only the isolated dedicated server to the authorized 96 GB node1 fallback, recreate exact fixture and configuration digests, and rerun from setup | Matching topology record and complete rerun with host role declared |
| Baseline task times out | Bounded runner expires without success sentinel | Record timeout as failure or environment blocker, never success | Gracefully terminate, preserve logs, classify cause, rerun after prerequisite correction | Same command reaches deterministic terminal result |
| Baseline generates tracked differences | `git status` or diff shows changes after runData or build | Retain diff as evidence and do not copy changes to planning worktree | Discard isolated worktree only after evidence retention; file a defect if repository-owned | Clean rerun or canonical issue link |
| Clean-ref test or build fails deterministically | Same exact command fails with decisive repository-owned error | Run duplicate search and file or enrich issue before repair | Route repair to owning later phase | Failing baseline packet plus canonical issue |
| Environment failure resembles product failure | JDK, download, display, permissions, disk, or memory signal | Classify separately and do not create misleading product issue | Restore environment prerequisite without source changes | Task rerun at same ref and documented environment |
| Cross-line file or dependency appears in a route | Diff, build metadata, or PR base includes wrong loader line | Stop route and prevent integration | Recreate work from correct base in owning phase; preserve evidence | Clean changed-path and metadata inspection |
| A new finding is a material enhancement, not a defect | Expected behavior requires new owner decision or scope | Stop with `PLAN_REVISION_REQUIRED` | Owner decides whether to invoke Plan Creator | No implementation occurs under current plan |
| Pull request 28 changes during baseline | PR head, dependency set, checks, or state changes | Refresh dependency record but keep it separate | No merge or dependency update under Phase 000 | Updated PR packet and unchanged product baseline |
| Issue packet source conflicts with repository evidence | Version, revision, component, or behavior differs | Record both, mark the field unresolved, and define the exact local proof needed to resolve it | Do not choose a convenient narrative or start repair | Packet contains source links, local reproduction route, and resolution criterion |

## Verification Matrix

| Requirement or task | Static or unit | Integration | Real workflow or runtime | Negative and recovery | Evidence artifact |
|---|---|---|---|---|---|
| CORE-REQ-001, P000-TASK-001 | Legacy hashes and repository identity checks | Planning worktree and ref reconciliation | Not applicable because this is governance proof | Hash mismatch and dirty-state drift handling | Provenance snapshot and before-and-after hashes |
| CORE-REQ-001, P000-TASK-002 | Schema, ID, link, digest, assignment, and dependency validation | Master, index, handoff, and phase semantic comparison | Not applicable | Missing, duplicate, unregistered, or contradictory plan artifact fails closed | Plan validation report |
| CORE-REQ-002, P000-TASK-003 | GitHub query completeness and object ID checks | Git-to-GitHub branch and check reconciliation | Authenticated read-only repository preflight | Auth failure, missing capability, stale query, and remote mismatch | Timestamped GitHub baseline |
| CORE-REQ-002, P000-TASK-004 | Frozen cursor, comment ID and count, and five-packet schema completeness checks | Issue links, comments, commits, configs, tests, and decisions reconcile before triage | No reporter action is required | Missing, conflicting, private, or newly arrived evidence stops affected triage and refreshes the snapshot | Frozen issue snapshot and evidence packet set |
| CORE-REQ-002 and CORE-REQ-016, P000-TASK-005 | Comment-to-matrix traceability and historical prerequisite scan | Issue 22 handoff, issue 25 matrix, issue 32 corruption campaign, and issue 34 runtime campaign reconcile with DEC-004, DEC-005, and DEC-007 | Workstation topology dry run and node1 fallback specification | False EXT dependency, incomplete matrix, unsafe fixture, host contamination, or missing stop condition | Owner-disposition record, matrices, campaign contracts, and capacity record |
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

Operational outputs are the provenance snapshot, GitHub baseline, frozen pre-triage issue-comment inventory, five issue evidence packets, issue 22 owner-acceptance and integration handoff, issue 25 compatibility matrix, issue 32 corruption and recovery campaign, issue 34 local multiplayer campaign, local-capacity record, rolling defect intake contract, support-line routing record, Forge baseline packet, NeoForge baseline packet, harness readiness matrix, blocker inventory, and phase completion packet. Store them outside the protected plan set using existing repository and GitHub evidence conventions, and link them by stable URL, commit, check run, or retained artifact identity. Do not use this phase file as a progress diary.

No configuration, schema, migration, runtime monitoring, artifact version, release metadata, tag, release, CurseForge page, Modrinth page, GitHub Release, or announcement is changed. Baseline JARs are local verification outputs only. Candidate `3.0.0-beta.2` and `2.2.1` artifacts are not produced or published by this phase.

Issue and other GitHub text created during execution follows repository lowercase and punctuation rules. Technical identifiers, literal paths, commands, versions, hashes, and links preserve required spelling. Phase 000 does not post evidence requests for EXT-001 through EXT-004. Any GitHub update needed for ordinary packet synchronization must follow the frozen-comment intake and duplicate-before-repair gates and must not expose private state.

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
| Issue snapshot or packet exposes private data | Retain only sanitized minimum fields and use confidential handling for sensitive state | Snapshot and public packet review | Stop propagation and use security or moderation procedures | Public packet and any copied evidence | Rebuild sanitized packet from source references without replicated sensitive content |
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
5. One complete and current evidence packet for each of issues 22, 25, 32, 33, and 34, including the frozen pre-triage comment inventory, issue 22 owner-acceptance and Phase 001 integration handoff, issue 25 beta-transition and current-state matrix, issue 32 deterministic local corruption and recovery campaign, and issue 34 local dedicated-server and multiple-client campaign with workstation and node1 routing.
6. A historical prerequisite reconciliation proving EXT-001 through EXT-004 are resolved or superseded traceability only, with no evidence request, active dependency, blocker, or endpoint gate, plus proof that EXT-005 is available and authorized.
7. The duplicate-before-repair dry run, classification taxonomy, confidential-routing rule, scope-escalation rule, and one canonical rolling issue inventory.
8. The Forge and NeoForge support-line routing records, including exact base revisions, candidate versions, Java, loader, Gradle, mappings where applicable, work-branch rules, PR bases, merge gates, schema transfer boundaries, and candidate branch ancestry.
9. Forge exact-ref baseline commands, Java and wrapper versions, task inventory, exit codes, decisive output, unit, data, GameTest, build, server and client readiness, JAR inspection, and post-task diff results.
10. NeoForge exact-ref baseline commands, Java and wrapper versions, task inventory, exit codes, decisive output, unit, data, GameTest, build, server and client readiness, JAR inspection, and post-task diff results.
11. Every baseline failure classified as repository-owned, local-environment-owned, EXT-005-blocked, or disproved, with a canonical issue link before any repair for repository-owned findings.
12. The deterministic harness matrix, fixture identities, environment requirements, runtime sentinels, time bounds, conservation fields, sanitation rules, retention links, failure stop conditions, and evidence invalidation graph.
13. Final proof that phase execution changed no product code, ordinary product documentation, legacy plan, support branch, version, release state, or unrelated owner file.
14. A concise blocker list that cannot name EXT-001 through EXT-004, reporter latency, or local hardware as blockers. No phase-owned governance, packet, routing, or harness action may remain unfinished.
15. The exact CORE-PHASE-001 first action, verify the owner-accepted issue 22 correction and integrate it only into `1.21.1` through the required pull request workflow, plus the current object IDs needed to execute it safely.

## Next Transition

Transition only to CORE-PHASE-001. Do not start CORE-PHASE-002 or any Forge repair branch from Phase 000.

The first downstream product action is issue 22 verification and integration into `1.21.1`. Before CORE-PHASE-001 performs any change, its owner must read `plan.md` and `phases/plan-phase-001.md` through EOF, consume the Phase 000 completion packet, fetch remote state without merging, and resolve the current object IDs for `origin/1.21.1` and `envy/issue_22_neoforge`. The owner must compare current ancestry, changed paths, version metadata, every frozen issue 22 comment, explicit owner acceptance, and baseline results with the Phase 000 record. Any movement or material diff change triggers the corresponding rebaseline and packet refresh before verification or integration.

CORE-PHASE-001 independently reruns the accepted root-cause regression, Java 21 NeoForge tests, build, JAR inspection, and client smoke proof, then integrates the correction only into `1.21.1` through its required pull request and checks, verifies the exact merged revision, and closes issue 22 with the owner acceptance and evidence links. EXT-001 is historical only and cannot delay or qualify that work. The NeoForge work remains isolated to `1.21.1`, targets exactly `2.2.1`, and cannot open the later Forge phase until Phase 001 is merged and its exit gate passes.
