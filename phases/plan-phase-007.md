# Phase 007 Execution Plan

> **Plan ID:** PLAN-PHASE-007
> **Phase ID:** CORE-PHASE-007
> **Owner:** Final integration and release-readiness owner
> **Classification:** MANDATORY
> **Master plan:** [plan.md](../plan.md)
> **Phase sequence:** 007 of 007

## Purpose and Ownership

This phase proves that the exact Forge 1.20.1 and NeoForge 1.21.1 candidate revisions satisfy the complete product contract, reconciles documentation and repository tracking with merged behavior, obtains every mandatory external result, repeats the complete audit to convergence, closes eligible issues, and prepares two unpublished integrity-checked candidate artifacts.

The master plan owns product scope, the owner decisions, the global phase sequence, the twenty canonical requirements, the five external prerequisites, and the completion endpoint. This file owns only the dependency-ordered execution of the final phase. It does not move requirements from earlier phases or reopen completed implementation. It verifies earlier work as an input to CORE-REQ-015, CORE-REQ-017, CORE-REQ-018, CORE-REQ-019, and CORE-REQ-020.

Completion means that all CORE-REQ-001 through CORE-REQ-020 and EXT-001 through EXT-005 pass together at exact unchanged support-line revisions. It does not authorize a public release, a release record, a public tag, an artifact upload, a stable designation, or an announcement.

## Evidence-Based Entry State

| Evidence class | Area | Finding | Source or command | Freshness condition |
|---|---|---|---|---|
| VERIFIED | Plan authority | `plan.md` assigns the final phase CORE-REQ-015, CORE-REQ-017, CORE-REQ-018, CORE-REQ-019, and CORE-REQ-020 and names CORE-PHASE-006 plus EXT-001 through EXT-005 as dependencies | `plan.md`, Sections 6, 10, 12, 13, 14, 16, and 18 | Any authorized master-plan or index revision requires rereading and revalidation before execution |
| OBSERVED | Forge baseline | The intake Forge support line is `origin/1.20.1` at `c6709e12ca7084ee068b2497a577b8d47c12f6fd`, Java 17, Minecraft 1.20.1, Forge 47.4.20, Gradle 8.14.4, and version `3.0.0-beta.1` | SRC-009, `gradle.properties`, `build.gradle`, `settings.gradle`, and `src/main/resources/META-INF/mods.toml` | Any support-head, build metadata, wrapper, dependency, mapping, source, resource, or configuration change invalidates the observation |
| OBSERVED | NeoForge baseline | The intake NeoForge support line is `origin/1.21.1` at `247d8f6842bfa1f586e5b18a9aab67cabd3db89f`, Java 21, Minecraft 1.21.1, NeoForge 21.1.233, ModDevGradle 2.0.141, and version `2.2.0` | SRC-009 and the build and template metadata at `origin/1.21.1` | Any support-head, build metadata, dependency, mapping, source, template, resource, or configuration change invalidates the observation |
| OBSERVED | Forge verification surface | The Forge build defines `test`, `runData`, `runGameTestServer`, `build`, `runServer`, `runClient`, `verifyBetaReleaseIdentity`, and packaged-dependency validation | `build.gradle` and SRC-011 | Any task graph or build-script change requires rediscovery and command revalidation |
| OBSERVED | NeoForge verification surface | The NeoForge build defines Java 21 unit testing and `runData`, `runGameTestServer`, `build`, `runServer`, and `runClient` run profiles | `origin/1.21.1:build.gradle` and SRC-011 | Any task graph or build-script change requires rediscovery and command revalidation |
| OBSERVED | Documentation surfaces | The tracked user and operator surfaces include `README.md`, `DOCUMENTATION.md`, `docs/README.md`, configuration guides, market and ATM guides, backup and restore guidance, compatibility, regression-gap records, security dispositions, and beta release notes | SRC-010 and the current documentation tree | Any behavior, command, configuration, persistence, recovery, compatibility, version, or document-layout change invalidates the affected review |
| UNKNOWN | External acceptance | At intake, reporter acceptance for issues 22 and 25, exact issue 32 state proof, and a controlled Forge multiplayer result are unavailable | EXT-001 through EXT-004 | Only exact prerequisite evidence changes the corresponding state to VERIFIED |
| VERIFIED | GitHub authority at intake | EXT-005 identifies authenticated EnVisione access to `MCEnvision/FutureShops` as available and authorized | EXT-005 | Authentication, token scope, repository identity, remote ownership, or permission changes require immediate revalidation |
| PROPOSED | Final artifacts | The intended local candidates are Forge `3.0.0-beta.2` and NeoForge `2.2.1`; neither final artifact is proven at intake | DEC-001, DEC-005, CORE-REQ-018 | Only builds from the final exact merged revisions with metadata inspection and matching hashes make the artifacts VERIFIED |

No intake result, earlier phase packet, historical build, or previous audit is final evidence after a material revision change. Execution begins by resolving the current merged support heads and their evidence-invalidation records rather than assuming the intake revisions remain current.

## Scope Boundaries

### Included Scope

- CORE-REQ-015: execute and retain deterministic full-stack verification for both exact candidate revisions, including focused regressions, complete deterministic suites, applicable data generation and GameTests, builds, server and client smoke tests, multiplayer, restart, reconnect, reload, rollback, corruption, fault, JAR, and diff evidence.
- CORE-REQ-017: reconcile user, administrator, maintainer, configuration, migration, recovery, verification, candidate-readiness, GitHub, Project, milestone, and wiki-ready documentation with behavior that is merged and proven.
- CORE-REQ-018: prepare and inspect local unpublished Forge `3.0.0-beta.2` and NeoForge `2.2.1` JARs, metadata, source revision manifests, SHA-256 and SHA-512 records, and verification packets.
- CORE-REQ-019: repeat the issue, security, privacy, command, persistence, integration, runtime, documentation, dependency, and artifact audits until two consecutive complete passes at unchanged revisions produce no new repository-owned defect.
- CORE-REQ-020: close issues 22, 25, 32, 33, and 34 and every rolling audit issue only after exact merged evidence, green checks, and the applicable external gates pass; reconcile pull requests and tracking.
- Final plan-wide verification of CORE-REQ-001 through CORE-REQ-020, CORE-PHASE-000 through CORE-PHASE-007, EXT-001 through EXT-005, DEC-001 through DEC-006, and the master Definition of Done.

### Explicit Exclusions

- FUT-001 and NG-002: no CurseForge, Modrinth, GitHub Release, artifact upload, public release tag, or announcement.
- FUT-002: no stable promotion or stable-release claim.
- FUT-003: no unrelated enhancement or subsystem. A material new feature request requires `PLAN_REVISION_REQUIRED`.
- FUT-004 and NG-006: no expansion of issue 33 into fuzzy matching, arbitrary NBT paths, broad expressions, unconditional replacement, or another selector.
- FUT-005: no distributed live market state or direct external-storage listings without deterministic receipts.
- NG-003 and DEC-004: no deletion of player data, journals, checkpoints, ledgers, custody, claims, worlds, or relevant failure evidence.
- NG-004, NG-005, NG-007, and NG-008: no weakening of authoritative invariants, no cross-line merge, no lower-fidelity closure substitute, and no silent platform upgrade.
- No product repair is performed without returning a verified defect to the CORE-REQ-009 duplicate-before-repair loop and integrating the repair on the affected support line.

## Phase Contract

### CORE-PHASE-007 — Final Candidate Proof and Unpublished Artifact Preparation

**Objective:** Freeze exact merged Forge and NeoForge candidate revisions, satisfy all internal and external evidence gates, reconcile documentation and tracking, complete two unchanged-revision clean audit passes, close every eligible scoped issue, and prepare integrity-checked unpublished candidates without performing any publication action.
**Owner:** Release readiness
**Dependencies:** CORE-PHASE-006, CORE-REQ-001, CORE-REQ-002, CORE-REQ-003, CORE-REQ-004, CORE-REQ-005, CORE-REQ-006, CORE-REQ-007, CORE-REQ-008, CORE-REQ-009, CORE-REQ-010, CORE-REQ-011, CORE-REQ-012, CORE-REQ-013, CORE-REQ-014, CORE-REQ-016, DEC-001, DEC-002, DEC-003, DEC-004, DEC-005, DEC-006, EXT-001, EXT-002, EXT-003, EXT-004, EXT-005
**Canonical requirements:** CORE-REQ-015, CORE-REQ-017, CORE-REQ-018, CORE-REQ-019, CORE-REQ-020
**Documentation and release impact:** Reconcile `README.md`, `DOCUMENTATION.md`, `docs/README.md`, every behavior-affected focused guide, candidate release notes, GitHub issue and project state, and wiki-ready material. Prepare local candidate JARs and integrity evidence only. Publication remains forbidden.
**Next transition:** Final plan-wide completion

**Entry criteria**

- CORE-PHASE-006 has completed all source-controlled and deterministic integration work, and every repair from CORE-PHASE-001 through CORE-PHASE-006 is merged into the correct current support line.
- `origin/1.20.1` and `origin/1.21.1` each resolve to one exact merged head; ancestry proves no cross-line merge, unsupported loader transfer, or stacked unmerged phase work.
- Every phase repair has a canonical issue or confidential advisory, merged change, focused regression, affected-interface record, and current invalidation state.
- Every required phase pull request is merged through GitHub, its required checks and review threads are resolved, and no dependent implementation remains only on a work branch.
- Any documentation or locked version metadata still requiring change is enumerated before candidate freeze and assigned to a line-specific final phase branch rooted at that support line's latest merged head.
- EXT-005 is revalidated for EnVisione and `MCEnvision/FutureShops`. EXT-001 through EXT-004 have either exact supplied evidence ready for candidate validation or a visible blocker record. A blocker may permit nondependent preparation, but cannot satisfy phase exit.
- Evidence-invalidation records identify the last material change to each support line and every downstream result that must be rerun.
- The working trees used for candidate proof are isolated, clean, and do not include another line's build output, runtime state, credentials, caches, or user changes.

**Implementation scope**

- CORE-REQ-015, CORE-REQ-017, CORE-REQ-018, CORE-REQ-019, and CORE-REQ-020 define the complete mandatory implementation boundary detailed below.

**Detailed implementation scope**

- Complete line-specific documentation and version reconciliation before freezing final candidate revisions.
- Execute the exact ordered verification ladders and external evidence gates against the frozen revisions.
- Build, stage locally, inspect, hash, and manifest the two unpublished artifacts.
- Run the complete audit twice without source, configuration, dependency, documentation, issue-scope, or artifact changes between passes.
- Close only fully evidenced issues and synchronize pull request, Project, milestone, branch, wiki, and evidence records.
- Audit the complete master Definition of Done and record either completion or the exact unsatisfied gate.

**Execution order**

- `P007-TASK-001` through `P007-TASK-016` execute the CORE-PHASE-007 task sequence in order.

**Detailed task sequence**

1. `P007-TASK-001` revalidates plan authority, repository identity, GitHub authority, support-line heads, branch isolation, phase integration, and current evidence invalidation.
2. `P007-TASK-002` constructs the final traceability ledger for all twenty requirements, eight phases, thirteen sources, six owner decisions, five prerequisites, and every scoped or rolling issue.
3. `P007-TASK-003` reconciles Forge documentation and locked `3.0.0-beta.2` metadata on a line-specific branch, verifies the documentation diff, and integrates it through a green reviewed pull request.
4. `P007-TASK-004` reconciles NeoForge documentation and locked `2.2.1` metadata on a line-specific branch, verifies the documentation diff, and integrates it through a green reviewed pull request.
5. `P007-TASK-005` freezes the exact candidate revisions after all authorized source, metadata, and documentation merges and invalidates every result tied to an earlier revision.
6. `P007-TASK-006` executes the complete Forge Java 17 verification ladder in the required order.
7. `P007-TASK-007` executes the complete NeoForge Java 21 verification ladder in the required order.
8. `P007-TASK-008` validates EXT-003 non-destructive invalid-state recovery evidence at the exact Forge candidate.
9. `P007-TASK-009` validates EXT-004 dedicated multiplayer, conservation, restart, reconnect, and failure evidence at the exact Forge candidate.
10. `P007-TASK-010` validates EXT-001 and EXT-002 reporter acceptance against artifacts built from the exact candidate revisions.
11. `P007-TASK-011` prepares and inspects the two local unpublished candidate artifact packets and proves no prohibited release action occurred.
12. `P007-TASK-012` runs complete convergence audit pass one across both unchanged candidate revisions.
13. `P007-TASK-013` classifies every pass-one finding and either proves it is not a defect or returns each verified defect through CORE-REQ-009 before repair and refreeze.
14. `P007-TASK-014` runs complete convergence audit pass two at the same candidate revisions and requires an empty new-defect set.
15. `P007-TASK-015` closes eligible scoped and rolling issues and reconciles pull request, Project, milestone, check, branch, evidence, and wiki state.
16. `P007-TASK-016` performs the final plan-wide Definition of Done and forbidden-action audit and seals the phase completion packet.

Tasks 006 and 007 may execute in parallel only in separate clean worktrees with separate Java toolchains, build directories, runtime directories, and evidence destinations. Tasks 008 through 010 may collect independent external evidence concurrently, but tasks 011 through 016 consume all of those results and remain blocked until they pass. Within either support line, revision-changing work, verification, artifact preparation, audits, and closure are sequential.

**Required evidence**

- Exact merged commit hashes and ancestry for both support lines, clean-tree records, version and toolchain records, and line-specific pull request proof.
- Complete line-specific command logs and decisive results at the exact candidate hashes.
- Server, client, multiplayer, restart, reconnect, reload, rollback, recovery, corruption, and fault evidence with environments and fixtures identified.
- EXT-001 through EXT-005 results at the required fidelity.
- Source-to-document review, link and terminology checks, exact command, path, config, version, and recovery validation, and complete tracked diff inspection.
- Two timestamped complete audit packets at identical candidate hashes and artifact hashes with no new repository-owned defect.
- Exact JAR filenames, internal metadata, archive inventories, dependency-boundary inspection, source revision manifests, SHA-256, SHA-512, and local staging records.
- Final issue comments, merged pull requests, green required checks, synchronized milestones and Project items, and wiki state based only on merged documentation.
- A negative release-action record proving no release, tag, upload, publication, stable designation, or announcement occurred.

**Exit criteria**

- Every CORE-REQ-001 through CORE-REQ-020 acceptance criterion has exact current evidence and every CORE-PHASE-000 through CORE-PHASE-007 exit gate passes.
- EXT-001 through EXT-005 are VERIFIED. Unknown, unavailable, mocked, lower-fidelity, or revision-mismatched external evidence is not accepted.
- Issues 22, 25, 32, 33, 34, and every verified rolling audit defect are merged on the correct line, verified, and correctly closed.
- Both support branches report green required checks at the exact frozen candidate revisions, and all review and tracking state agrees with those revisions.
- Two complete consecutive audits at unchanged Forge and NeoForge revisions and unchanged candidate hashes find no new repository-owned defect.
- The local Forge `3.0.0-beta.2` and NeoForge `2.2.1` JARs, metadata, manifests, SHA-256, SHA-512, and verification packets are complete and mutually consistent.
- Legacy plans remain byte-for-byte unchanged; optional and future work remains excluded.
- No publication, release creation, public tag, upload, stable declaration, or announcement has occurred.
- The final phase verifies the owner-selected completion endpoint and plan-wide Definition of Done.
- No known mandatory phase-owned defect remains.

## Inputs and Upstream Contracts

| Input or contract | Provider | Required state | Validation | Failure behavior |
|---|---|---|---|---|
| Frozen master and plan index | PLAN-MASTER and Plan Creator integration audit | Valid, contiguous, authoritative, and unchanged during execution | Recompute the registered plan-set validation required by CORE-REQ-001 | Stop on an invalid or materially changed product contract; do not silently reinterpret it |
| Legacy plans | SRC-002 and SRC-003 | Byte-for-byte unchanged historical records | Compare recorded baseline hashes with current files | Stop and report the unexpected mutation; do not repair the protected history during this phase |
| Forge integrated support head | CORE-PHASE-002 through CORE-PHASE-006 | All applicable repairs and audits merged into current `origin/1.20.1` | Fetch, resolve hash, inspect ancestry, merged PRs, checks, diff, and version metadata | Do not freeze or build; complete the missing correct-line integration first |
| NeoForge integrated support head | CORE-PHASE-001 and applicable audit repairs | Issue 22 and any independently required NeoForge corrections merged into current `origin/1.21.1` | Fetch, resolve hash, inspect ancestry, merged PRs, checks, diff, and version metadata | Do not freeze or build; complete the missing correct-line integration first |
| Defect and change evidence packets | CORE-PHASE-000 through CORE-PHASE-006 | Every known repair has issue-before-repair traceability, exact focused proof, merge, and invalidation scope | Join issues, PRs, commits, test results, phase packets, and support-line ancestry | Reopen the evidence gate; do not infer missing proof from compilation or a closed issue |
| Security and command matrices | CORE-PHASE-004 | Complete and clean at its last exact revision, with all findings classified | Verify inventory coverage and revision applicability | Return stale or incomplete rows to their owning audit and invalidate downstream proof |
| Persistence and conservation matrices | CORE-PHASE-005 | Complete, non-destructive, clean, and tied to exact affected revisions | Verify schema, fixtures, recovery, backup, restore, and conservation records | Stop mutation testing on integrity mismatch and preserve the full recovery lineage |
| Backend integration matrix | CORE-PHASE-006 | Complete at exact integrated revisions, including lifecycle, failure, restart, reconnect, and multiplayer scope | Verify all named subsystem rows and EXT-004 dependencies | Keep CORE-REQ-013 and final closure blocked until the missing row passes |
| External prerequisite records | EXT-001 through EXT-005 | Exact environment, revision, procedure, and decisive result available | Apply each prerequisite's master-defined fidelity test | Keep the affected requirement and issue open; continue only work that does not consume the missing evidence |
| Documentation inventory | SRC-010 | Tracked layout known and mapped to affected behavior | Compare source, configuration, commands, tests, and merged behavior with every relevant document | Record and repair inaccuracies before candidate freeze through the correct support line |
| GitHub repository state | EXT-005 and SRC-013 | EnVisione identity, authoritative remote, required checks, issues, PRs, milestones, Project, and wiki accessible | Read-only preflight before mutation and final readback after each update | Stop remote mutation on identity, scope, repository, billing, or permission mismatch |

## Outputs and Downstream Contracts

| Output or contract | Consumer | Guaranteed state | Compatibility or versioning | Evidence |
|---|---|---|---|---|
| Forge candidate verification packet | Owner and later release authorization | Exact `1.20.1` merged revision has passed the full Java 17 ladder and all Forge external gates | Minecraft 1.20.1, Forge 47.4.20, version `3.0.0-beta.2`; no platform drift | Revision, commands, environments, logs, audit passes, issue results, JAR inventory, and hashes |
| NeoForge candidate verification packet | Owner and later release authorization | Exact `1.21.1` merged revision has passed the full Java 21 ladder and EXT-001 | Minecraft 1.21.1, NeoForge 21.1.233, version `2.2.1`; no cross-line merge | Revision, commands, environment, logs, audit passes, issue result, JAR inventory, and hashes |
| Documentation reconciliation packet | Users, operators, maintainers, support, and wiki | Tracked documentation describes merged candidate behavior and unpublished status only | Branch-specific compatibility and versions remain explicit | Source-to-doc matrix, link checks, diff, merged PRs, and wiki comparison |
| External acceptance packet | Issue closure governance | EXT-001 through EXT-004 contain exact, non-destructive, revision-bound results | Evidence is valid only for the named artifact hash and support revision | Reporter records, sanitized state proof, multiplayer trace, and conservation report |
| Convergence packets one and two | Final integration audit | Two complete audit passes at identical revisions and artifacts have empty new-defect sets | Any material change invalidates both as a pair | Timestamps, hashes, inventories, classifications, and green checks |
| Issue and tracking closure packet | Repository governance | All scoped and rolling issues, PRs, milestones, and Project items agree with merged evidence | GitHub text follows repository rules; branches remain historical unless owner requests deletion | Final issue comments, state readback, merged PRs, check runs, and Project reconciliation |
| Local unpublished candidate artifacts | Future owner-authorized release workflow | Two inspected JARs and integrity records exist locally without release-side effects | Exact locked versions and source revisions; hashes bind later decisions to these bytes | JARs, SHA-256, SHA-512, archive inventories, metadata, manifests, and local custody record |
| Plan-wide completion packet | Owner | Every requirement, prerequisite, phase gate, decision, exclusion, and Definition of Done item has a verdict and evidence link | No public release status is implied | Final traceability ledger and forbidden-action audit |

## Source, Decision, and Requirement Traceability

| Contract IDs | Phase use | Required proof |
|---|---|---|
| SRC-001, DEC-001, DEC-002 | Completion endpoint and rolling scope | Every scoped and discovered defect is classified, repaired where verified, closed where eligible, and absent from two clean passes |
| SRC-002, SRC-003 | Preserved architecture, invariants, and historical plans | Legacy hashes unchanged; retained behavior represented in regression and audit matrices without reopening old roadmaps |
| SRC-004, SRC-005, SRC-006, SRC-007, SRC-008 | Issues 22, 25, 32, 33, and 34 | Exact issue-specific evidence, correct-line merged fixes, required external results, and closure records |
| SRC-009, DEC-005 | Branch, toolchain, compatibility, and versions | Exact ancestry, pinned metadata, correct candidate filenames, and no cross-line contamination |
| SRC-010 | Documentation surfaces | Current source-to-document reconciliation and merged documentation evidence |
| SRC-011 | Tests, CI, runtime, and candidate implementation evidence | Full exact-revision verification ladder and retained decisive outputs |
| SRC-012 | Dependency maintenance | PR 28 and the resolved graphs are classified without silent scope or platform drift; current alerts and packaging are audited |
| SRC-013 | Execution, safety, GitHub, and documentation rules | Safe worktrees, correct branches, signed owner-authored changes where required, required checks, non-destructive verification, and clean final diff |
| DEC-003 | Bounded issue 33 behavior | Runtime and regression proof covers bounded selection, preview, shared values, skip, explicit replace, preservation, and atomicity only |
| DEC-004 | External evidence and closure | Missing evidence remains a blocker; issues 22 and 25 have reporter acceptance and issue 32 has exact state proof |
| DEC-006 | Mandatory audits | Security, privacy, command, persistence, database, recovery, and backend integration matrices rerun clean at both applicable exact revisions |
| CORE-REQ-001 through CORE-REQ-014, CORE-REQ-016 | Upstream product and governance contract | Every acceptance criterion remains satisfied after final changes and appears in the plan-wide ledger |
| CORE-REQ-015, CORE-REQ-017, CORE-REQ-018, CORE-REQ-019, CORE-REQ-020 | Phase-owned final contract | Full verification, documentation reconciliation, artifacts, convergence, and closure packets pass |
| EXT-001 through EXT-005 | Mandatory prerequisites | Every prerequisite has master-defined exact evidence and a VERIFIED final state |

## Work Packages

| Task ID | Requirement IDs | Work | Inputs and dependencies | Outputs | Affected components or interfaces | Verification |
|---|---|---|---|---|---|---|
| P007-TASK-001 | CORE-REQ-015, CORE-REQ-019, CORE-REQ-020 | Perform final read-only repository and GitHub preflight, resolve merged heads, prove support-line isolation, and consolidate invalidation state | CORE-PHASE-006, EXT-005, SRC-009, SRC-013 | Exact entry-state and invalidation ledger | Git ancestry, support branches, PRs, checks, reviews, working trees | Hash, ancestry, auth, remote, branch, dirty-state, and required-check evidence |
| P007-TASK-002 | CORE-REQ-019, CORE-REQ-020 | Build the final contract and issue traceability ledger | P007-TASK-001, all phase packets, SRC-001 through SRC-013, DEC-001 through DEC-006 | One row per requirement, prerequisite, issue, phase, and exclusion | Plan evidence, issue workflow, confidential findings, external blockers | No missing, duplicated, unclassified, or stale row |
| P007-TASK-003 | CORE-REQ-017, CORE-REQ-018 | Reconcile Forge docs and exact candidate metadata, review, verify, and merge | Latest approved `1.20.1` head and documentation inventory | Merged Forge documentation and version state | `README.md`, `DOCUMENTATION.md`, `docs/README.md`, affected `docs/` guides, `gradle.properties`, mod metadata expansion, release notes | Source-to-doc review, link and literal checks, clean diff, green PR, merged head readback |
| P007-TASK-004 | CORE-REQ-017, CORE-REQ-018 | Reconcile NeoForge docs and exact candidate metadata, review, verify, and merge | Latest approved `1.21.1` head and documentation inventory | Merged NeoForge documentation and version state | Branch-applicable docs, `gradle.properties`, generated NeoForge metadata template | Source-to-doc review, link and literal checks, clean diff, green PR, merged head readback |
| P007-TASK-005 | CORE-REQ-015, CORE-REQ-018, CORE-REQ-019 | Freeze both final candidate hashes and reset downstream evidence | P007-TASK-003 and P007-TASK-004 | Immutable verification coordinates for this candidate attempt | Git heads, manifests, evidence ledger | Clean worktrees, exact hashes, version literals, no pending revision-changing work |
| P007-TASK-006 | CORE-REQ-015, CORE-REQ-019 | Run the Forge verification ladder | Frozen Forge hash, Java 17, phase matrices | Complete Forge deterministic and runtime packet | Forge build, tests, data, GameTests, server, client, multiplayer-dependent flows, JAR | Every applicable step passes in order with exact command and decisive result |
| P007-TASK-007 | CORE-REQ-015, CORE-REQ-019 | Run the NeoForge verification ladder | Frozen NeoForge hash, Java 21, phase matrices | Complete NeoForge deterministic and runtime packet | NeoForge build, tests, data, GameTests, server, client, issue 22 UI | Every applicable step passes in order with exact command and decisive result |
| P007-TASK-008 | CORE-REQ-015, CORE-REQ-020 | Prove issue 32 exact invalid-state recovery | EXT-003, frozen Forge hash, preserved isolated fixture | Sanitized before-and-after recovery packet | Player persistence, inventory proof, claims, restart, reconnect, repeated transaction | Exact cause, safe recovery, unrelated-state preservation, zero loss or duplication |
| P007-TASK-009 | CORE-REQ-015, CORE-REQ-019 | Prove controlled Forge multiplayer and conservation | EXT-004, frozen Forge hash, two independent clients | Multiplayer and conservation packet | Finite and infinite stock, economy, escrow, claims, failure, restart, reconnect | Required matrix passes with zero unexplained value delta |
| P007-TASK-010 | CORE-REQ-015, CORE-REQ-020 | Validate issue 22 and 25 reporter acceptance | EXT-001, EXT-002, exact candidate hashes and artifact hashes | Exact reporter acceptance records | NeoForge screen lifecycle; Forge catalog migration, readiness, reload | Environment, version, revision, hash, procedure, and decisive result match prerequisite |
| P007-TASK-011 | CORE-REQ-018 | Build and seal local candidate integrity packets | P007-TASK-006 through P007-TASK-010 | Two local unpublished JAR packets | JAR contents, metadata, dependencies, manifests, hashes, custody | Archive and metadata inspection, SHA-256 and SHA-512 verification, forbidden-action audit |
| P007-TASK-012 | CORE-REQ-019 | Execute convergence audit pass one | All exact candidate and external packets | Timestamped complete pass-one packet | Issues, security, privacy, commands, persistence, recovery, integration, runtime, docs, dependencies, artifacts | Every matrix rerun or proven unchanged and current; all findings classified |
| P007-TASK-013 | CORE-REQ-009, CORE-REQ-019 | Resolve pass-one findings through the rolling loop | P007-TASK-012, EXT-005 | Empty unresolved verified-defect set or a refrozen candidate attempt | Duplicate search, issues, repairs, PRs, regression evidence | No verified repair precedes issue record; any material repair returns to P007-TASK-003 or P007-TASK-004 and invalidates tasks 005 through 012 |
| P007-TASK-014 | CORE-REQ-019 | Execute convergence audit pass two without intervening change | Clean P007-TASK-012 and P007-TASK-013 result, unchanged hashes | Timestamped complete pass-two packet | Same full audit surface as pass one | Candidate commits and JAR hashes equal pass one, green checks remain green, no new repository-owned defect |
| P007-TASK-015 | CORE-REQ-017, CORE-REQ-020 | Close eligible issues and reconcile repository tracking and wiki | Both clean passes, EXT-001 through EXT-005, merged PRs | Correct final GitHub and wiki state | Issues, PRs, milestones, Project, branch records, wiki-ready docs | Readback proves exact evidence links, closure rules, green checks, and no unpublished behavior presented as released |
| P007-TASK-016 | CORE-REQ-015, CORE-REQ-017, CORE-REQ-018, CORE-REQ-019, CORE-REQ-020 | Audit the complete master Definition of Done and seal completion | P007-TASK-001 through P007-TASK-015 | Final completion packet or exact blocker | Entire plan set and endpoint | All twenty requirements, eight phases, five prerequisites, decisions, exclusions, artifacts, and forbidden actions receive evidence-backed verdicts |

### Ordering, Parallelism, and Rework

- Tasks 003 and 004 may proceed concurrently only because the support lines are isolated. Each must use its own latest approved head, worktree, toolchain, branch, pull request, and merge result.
- Candidate hashes are not frozen until all source, build metadata, tests, fixtures, documentation, and workflow changes needed for the endpoint have merged. A documentation-only commit still changes the exact revision and invalidates revision-bound audit and artifact evidence.
- Tasks 006 and 007 may run concurrently after Task 005. Runtime ports, worlds, configurations, logs, and build outputs must not overlap.
- External evidence collection may proceed while local verification runs, but evidence is accepted only if it names the final frozen revision and matching candidate artifact hash.
- A newly verified defect triggers duplicate search and issue creation before repair. The repair returns through the owning earlier audit contract, a line-specific reviewed PR, documentation reconciliation where affected, and a new candidate freeze. It invalidates both clean-audit passes and every downstream artifact or external result within its blast radius.
- A disproven concern needs source, test, or runtime evidence. Labeling a finding false without proof is not convergence.

## Exact Verification Order

### Forge 1.20.1 Candidate

The Forge candidate worktree must be detached or checked out at the exact merged `origin/1.20.1` candidate hash, clean, and configured for Java 17. Record `git rev-parse HEAD`, ancestry, clean status, Java version, wrapper version, dependency graph identity, `mod_version`, Minecraft version, Forge version, mapping version, and expected JAR name before executing checks.

Run and record these stages in order. A failed stage stops downstream candidate claims until repaired and rerun.

1. Run every issue-specific and audit-specific focused regression, including issues 25, 32, 33, and 34 and every rolling defect. Prove pre-fix failure where feasible from retained earlier evidence and current candidate success.
2. Run `bash ./gradlew test`.
3. Run `bash ./gradlew runData` because the project exposes data generation; compare generated output with the frozen revision and classify any drift. Unexpected tracked drift is a finding, not an automatic writeback.
4. Run `bash ./gradlew runGameTestServer` when registered GameTests or the phase evidence matrix uses them. If the task has no executable tests, record the discovered absence and the exact higher-fidelity replacement; do not silently mark it passed.
5. Run `bash ./gradlew verifyBetaReleaseIdentity` and then `bash ./gradlew build`. Confirm packaged-dependency boundary checks execute and the artifact name is exactly `futureshops-3.0.0-beta.2.jar`.
6. Run `bash ./gradlew runServer` as a bounded dedicated-server smoke test. Capture clean common initialization, configuration, registration, persistence open and recovery, command registration, market readiness, and graceful shutdown. Use an isolated copied fixture or fresh test world, never unbacked production state.
7. Run `bash ./gradlew runClient` as a bounded client smoke test. Capture startup, assets, translations, shop and market entry, admin workflow, input, rendering, server snapshots, and graceful exit.
8. Execute the full Forge real-workflow matrix on the isolated dedicated server: issue 25 startup, restart, and reload; issue 32 recovery; issue 33 mixed create, skip, replace, cancel, stale permission, disconnect, and atomic-write failure; issue 34 finite and infinite stock; commands; ATM; claims; Server Shops; Player Shops; Auction House; Bazaar; module lifecycle; provider failure; and delayed readiness.
9. Execute at least two independent clients for EXT-004. Cover success, rejection, concurrent buyers, disconnect, retry, replay, full inventory, provider failure, restart, reconnect, claim delivery, and conservation.
10. Rehearse backup, complete matching restore, compatible rollback boundary, malformed and older state, corruption, partial write, interrupted recovery, invalid reload, and last-known-good behavior without deleting state.
11. Inspect the complete tracked diff from the prior approved Forge base, working-tree status, generated resources, runtime output boundaries, configuration examples, logs, caches, secrets, absolute paths, and unrelated files.
12. Inspect the JAR archive, expanded `META-INF/mods.toml`, resource namespace, mixin metadata, translations, required assets, dependencies, and forbidden packaged classes. Compute and verify SHA-256 and SHA-512.
13. Confirm the exact merged revision's required GitHub checks are green and match the locally verified commit.

### NeoForge 1.21.1 Candidate

The NeoForge candidate worktree must be detached or checked out at the exact merged `origin/1.21.1` candidate hash, clean, and configured for Java 21. Record `git rev-parse HEAD`, ancestry, clean status, Java version, wrapper version, dependency graph identity, `mod_version`, Minecraft version, NeoForge version, ModDevGradle version, mappings, and expected JAR name before executing checks.

Run and record these stages in order.

1. Run the issue 22 focused regression or deterministic screen-lifecycle inspection and every independently required NeoForge audit regression. Confirm client-only code remains outside common and dedicated-server initialization.
2. Run `bash ./gradlew test`.
3. Run `bash ./gradlew runData` when the branch's data providers are applicable; compare output with the frozen revision and classify unexpected drift.
4. Run `bash ./gradlew runGameTestServer` when registered GameTests or the evidence matrix uses them. Record an evidence-backed not-applicable result if the configured task has no relevant executable test.
5. Run `bash ./gradlew build`. Confirm the resulting artifact is exactly the locked `2.2.1` candidate and no local Maven publication task runs.
6. Run `bash ./gradlew runServer` as a bounded dedicated-server smoke test to prove common initialization does not load client classes and that configuration, registration, persistence, and graceful shutdown are sound.
7. Run `bash ./gradlew runClient` as a bounded client smoke test. Exercise every affected FutureShops screen, navigation, scale or resize behavior, intended background rendering, and exit behavior. Capture visual evidence without private data.
8. Exercise restart and reconnect where NeoForge state or navigation crosses the network. Confirm stale responses and loader-appropriate lifecycle behavior fail safely.
9. Validate EXT-001 in the originally affected Windows environment against the exact candidate revision and artifact hash. Local Linux smoke evidence cannot substitute for this result.
10. Inspect the complete diff, working-tree status, generated resources, build output boundary, dependency graph, secrets, caches, absolute paths, and unrelated files.
11. Inspect the JAR archive and expanded `META-INF/neoforge.mods.toml`, loader and Minecraft ranges, resources, translations, client separation, dependencies, and absence of development output. Compute and verify SHA-256 and SHA-512.
12. Confirm the exact merged revision's required GitHub checks are green and match the locally verified commit.

No stage may be reported as passed merely because a later stage passed. Skipped or unavailable checks require an explicit applicability decision and block every requirement that depends on them.

## External Evidence Gates

| Prerequisite | Exact gate | Acceptance test | Failure state |
|---|---|---|---|
| EXT-001 | Issue 22 reporter tests the exact NeoForge candidate in the affected Windows environment | Issue record names version `2.2.1`, candidate commit and JAR hash, Minecraft and NeoForge versions, operating system, affected screens, navigation result, and screenshot or concise confirmation | CORE-REQ-003, CORE-REQ-020, and final completion remain blocked; issue 22 stays open |
| EXT-002 | Issue 25 reporter supplies sanitized affected-world evidence and tests the exact Forge candidate on a preserved copy | Record names version `3.0.0-beta.2`, candidate commit and JAR hash, migrated catalog and config state, startup, restart, reload, offer availability, logs, and confirmation that no data was deleted | CORE-REQ-004, CORE-REQ-020, and final completion remain blocked; issue 25 stays open |
| EXT-003 | Preserved exact issue 32 state or deterministic equivalent reproduces and recovers non-destructively | Sanitized field-level before-and-after diff identifies the FutureShops-owned cause, proves compatible recovery across restart and reconnect, and proves unrelated player state and value unchanged | CORE-REQ-005, CORE-REQ-012, CORE-REQ-015, CORE-REQ-020, and final completion remain blocked; issue 32 stays open |
| EXT-004 | Exact Forge candidate runs on an isolated server with at least two independent clients | Logs and state snapshots cover required finite and infinite stock, success and failure, request IDs, balances, inventories, stock, escrow, claims, persistence, restart, and reconnect; conservation delta is zero | CORE-REQ-006, CORE-REQ-013, CORE-REQ-015, CORE-REQ-019, and final completion remain blocked |
| EXT-005 | EnVisione can read and update authoritative repository state | Authenticated identity, remote, issues, PRs, checks, Project, milestones, and closure readback are verified | Remote synchronization and final completion stop; no alternate identity or repository is used |

The executor must not upload candidate artifacts under this phase. Reporter evidence may be accepted only when the reporter independently builds the named exact revision or receives the exact bytes through a separately authorized non-public channel outside this execution. If no compliant path exists, preserve the candidate hash and request evidence; do not create a public attachment, release, tag, or upload to bypass the blocker.

## Documentation, Tracking, and Wiki Sequence

1. Derive a source-to-document matrix from all merged changes and audit outcomes. At minimum, inspect `README.md`, `DOCUMENTATION.md`, `docs/README.md`, `docs/backup-restore.md`, `docs/compatibility-matrix.md`, `docs/config-3.0-examples.md`, `docs/config-3.1-offers.md`, `docs/markets-guide.md`, `docs/physical-currency-atm.md`, `docs/community-bug-regression-test-gaps.md`, `docs/release-notes-3.0-beta.md`, affected schema or security records, and any NeoForge branch equivalent. Edit only documents whose content is affected.
2. Reconcile purpose, beta and unpublished status, supported lines, exact versions, installation, commands, permissions, configuration keys and bounds, bulk listing behavior, migration, persistence, backup, recovery, troubleshooting, known limits, verification, and artifact preparation against code and tests.
3. Preserve existing tracked layout and normal documentation language. Update `docs/README.md` links if a focused document is added or moved. Do not rewrite legacy plans or present planned behavior as implemented.
4. Validate every literal command, config key, path, version, loader, Minecraft version, Java version, schema, permission, recovery step, and expected result. Run link and terminology checks and inspect the documentation diff.
5. Merge documentation and version changes through the correct support-line pull request before freezing candidate revisions. Required checks and the private independent review, when available, must pass.
6. Prepare wiki updates from the merged tracked documentation only. Publish wiki changes only after the corresponding support-line documentation merge, then read back the wiki and prove it does not advertise an unpublished candidate as released or stable.
7. Reconcile issue, pull request, milestone, and Project state only after the evidence it describes exists. Public GitHub text follows repository lowercase and punctuation rules.
8. The candidate-readiness record may say prepared and unpublished. It must not include a release URL, publication date, download claim, public tag, platform upload, or announcement.

## Candidate Integrity Artifacts

For each support line, create one locally retained candidate packet outside tracked source output. The packet must contain:

- The exact repository identity, support branch, source commit hash, parent and merge ancestry, clean-tree result, and build timestamp.
- Minecraft, loader, Java, Gradle Wrapper, build plugin, mapping, mod, and dependency versions.
- The exact executed commands in order, environment identifiers, decisive results, known skips with applicability rationale, and links or paths to retained sanitized logs.
- The candidate JAR with the locked filename and version.
- An archive entry inventory and extracted internal mod metadata.
- A dependency and packaged-boundary record, including absence of forbidden launcher-supplied classes, secrets, caches, logs, runtime worlds, local paths, and development-only output.
- A source revision manifest that binds repository, branch, commit, version, JAR filename, byte length, SHA-256, and SHA-512.
- Separate SHA-256 and SHA-512 checksum files or records and a verification result that rehashes the staged bytes.
- External prerequisite evidence and issue closure references applicable to that line.
- Convergence audit pass-one and pass-two identifiers.
- A custody statement that the artifacts remain local and unpublished.

Do not assume two independent builds are byte-for-byte reproducible unless the build has been proven reproducible. The required integrity contract binds the inspected bytes to the exact source and evidence packet. If later rebuilding produces different bytes, classify the difference, regenerate the packet for the retained candidate, and rerun every hash-bound external or audit result.

## Repeated Convergence Audit

Each complete pass audits both exact candidate revisions and the complete repository-owned closure surface:

1. Reconcile open and closed issues, comments, confidential advisories, review threads, pull requests, commits, checks, milestones, Project items, Dependabot PRs and alerts, Actions failures, and evidence links.
2. Rerun the security and privacy inventory over packets, commands, paths, codecs, NBT, JSON, TOML, logs, dependencies, JAR contents, permissions, replay, duplication, and private-data handling.
3. Rerun every command and permission inventory row, including console, authorized and unauthorized player, malformed input, stale state, confirmation, repeat, recovery, feedback, and audit context.
4. Rerun the persistence, migration, database, atomicity, corruption, backup, restore, restart, reconnect, recovery, compatibility, idempotency, and conservation matrices.
5. Rerun the backend integration and failure matrix for networking, readiness, Server Shops, Player Shops, economy providers, escrow, Auction House, Bazaar, ATM, claims, reload, lifecycle, restart, reconnect, and multiplayer where applicable to each line.
6. Reconcile runtime, visual, reporter, issue-specific, and external evidence with the exact candidate and artifact hashes.
7. Reconcile documentation, links, commands, configs, versions, compatibility, known limits, and wiki state with merged implementation.
8. Inspect dependency graphs, PR 28 disposition, pinned platform boundaries, workflow results, generated output, complete diffs, JARs, manifests, and checksums.
9. Classify every observation as a duplicate, repaired defect, confidential blocker, excluded future work, or disproven concern with evidence. There may be no unclassified observation.
10. Record the candidate commit hashes, JAR hashes, timestamps, audit inventory version, and new-defect count.

Pass two starts only after pass one has an empty verified-defect set and no pending material change. Both passes must use identical source commits, configuration and fixture revisions, dependency resolution contract, documentation revisions, artifact bytes, and issue scope. A green pass followed by a change is not a consecutive pair.

## Issue, Pull Request, and Tracking Closure

| Record | Closure gate |
|---|---|
| Issue 22 | Correct `1.21.1` merge, exact NeoForge regression and client evidence, green checks, and EXT-001 reporter acceptance |
| Issue 25 | Correct `1.20.1` merge, exact catalog migration, readiness, restart, reload evidence, green checks, and EXT-002 reporter acceptance |
| Issue 32 | Correct `1.20.1` merge, EXT-003 exact invalid-state proof, non-destructive repeated recovery, unrelated-state preservation, green checks, and no loss or duplication |
| Issue 33 | Correct `1.20.1` merge and exact bounded selection, preview, shared price and stock, skip, explicit replace, preservation, atomic rollback, client, server, and JAR evidence |
| Issue 34 | Correct `1.20.1` merge, focused finite-stock regression, EXT-004 controlled multiplayer, conservation, restart, reconnect, and green checks |
| Rolling audit issue | Duplicate-before-repair record, correct-line merged fix, focused regression, all invalidated downstream evidence rerun, green checks, and any issue-specific real-world gate |
| Phase or repair pull request | Merged through GitHub using the repository-required method, required checks green, independent private review completed when available, actionable threads resolved, and exact merge commit recorded |
| Milestone and Project item | State matches the issue and pull request evidence; no item is complete while its issue is externally blocked |
| Wiki | Content derives from merged tracked documentation and labels both candidates as prepared and unpublished |

Close issues only after both clean audit passes confirm that their evidence remains valid. Each final issue comment must link or identify the exact merged commit, version, relevant tests, runtime or reporter result, and retained evidence packet. If GitHub cannot accept the update, keep the issue open and record EXT-005 as unsatisfied.

Historical support and phase branches are not deleted unless the owner separately requests deletion. No tag is created or pushed under this phase; commit hashes and integrity manifests identify the candidates.

## Architecture and Implementation Boundaries

- Forge and NeoForge retain separate support branches, worktrees, Java toolchains, build systems, runtime directories, version histories, and candidate artifacts. No cross-line merge or unreviewed cherry-pick is permitted.
- The logical server remains authoritative for money, stock, listings, orders, custody, claims, permissions, request completion, and module lifecycle. Final verification must not accept client presentation as proof of authoritative state.
- Every value mutation remains bound to checked integer minor units, a stable request UUID, journal and custody lineage, idempotent replay, one terminal outcome, durable compensation or claim, and conservation.
- Claims remain accessible during disabled, frozen, draining, recovering, and restart states. A final audit failure may not be hidden by disabling a module.
- Persistence and configuration verification uses complete matching isolated copies, explicit schemas, atomic-write and last-known-good contracts, and non-destructive recovery. Unknown compatible data and unrelated player state are preserved.
- External inputs, issue attachments, logs, configs, NBT, paths, packets, and runtime state are treated as untrusted and sanitized before retention or GitHub use.
- Candidate preparation consumes exact merged revisions. Local artifact staging must not alter source, commit generated runtime output, leak credentials, or create remote state.
- Documentation is evidence-bearing output. It is merged before revision freeze and rechecked in both audit passes.
- GitHub closure is evidence retention, not product publication. Issue and Project updates are allowed by EXT-005; releases, uploads, tags, and announcements are not.

## Failure, Recovery, and Edge Cases

| Scenario | Detection | Required behavior | Recovery or rollback | Regression proof |
|---|---|---|---|---|
| Support head changes during verification | Remote hash or ancestry differs from frozen manifest | Stop and mark revision-bound evidence stale | Resolve the intentional change, refreeze both affected coordinates, and rerun downstream tasks | Hash equality at every packet boundary |
| Late documentation-only change | Candidate commit changes even if runtime code does not | Invalidate revision-bound audits and manifests | Merge the documentation change, freeze the new hash, and rerun revision and documentation dependent checks | Both final passes use the new identical hash |
| Cross-line contamination | Diff contains loader, API, metadata, or commits from the other support line | Reject the candidate | Revert through a reviewed line-specific change; never merge support lines to repair it | Ancestry and loader-specific diff review |
| External evidence missing or mismatched | Required environment, revision, artifact hash, procedure, or result absent | Keep prerequisite, requirement, issue, and endpoint open | Request the exact missing evidence and continue only independent work | Prerequisite-specific final record |
| Reporter silence | No EXT-001 or EXT-002 result | Do not infer acceptance | Keep the issue open with the precise request | Reporter-authored or otherwise attributable acceptance record |
| Corrupt or privacy-sensitive issue 32 evidence | Integrity fails or raw private data would be exposed | Stop ingestion and avoid public attachment | Preserve privately, sanitize a minimal deterministic fixture, or remain blocked | Sanitized field-level diff and recovery result |
| Conservation mismatch | Balance, item, stock, custody, claim, or journal delta is nonzero or unexplained | Freeze value mutation and fail the candidate | Preserve complete lineage and logs, restore one matching snapshot, file issue before repair | Repeated zero-delta conservation matrix |
| Partial or failed catalog write | File and in-memory state diverge, checksum changes unexpectedly, or reload rejects | Fail closed and retain prior state | Restore the complete matching catalog snapshot through documented recovery | Atomic-write, failure, reload, and last-known-good tests |
| Server or client smoke hangs | No readiness or graceful shutdown within the bounded harness window | Mark stage failed, preserve logs and process state | Stop the isolated process safely, diagnose through an issue if repository-owned, and rerun | Bounded clean startup and shutdown result |
| GameTest or data task unavailable | Task absent, no registered tests, or environment cannot run it | Record exact applicability; do not silently pass | Supply the master-allowed higher-fidelity evidence or keep dependent requirement blocked | Task discovery plus explicit replacement mapping |
| CI green on different commit | Check SHA differs from candidate SHA | Treat CI as irrelevant to the candidate | Trigger or await the required checks on the exact commit | GitHub check-suite SHA readback |
| Artifact metadata mismatch | Filename, internal version, loader range, Minecraft range, or source manifest disagrees | Reject and do not distribute candidate | Correct on line-specific branch, merge, refreeze, and rebuild | Metadata and manifest inspection |
| Hash mismatch after staging | Recomputed SHA-256 or SHA-512 differs | Quarantine the local copy and fail integrity gate | Recopy from the verified build output or rebuild, then regenerate all hash-bound evidence | Hash verification over retained bytes |
| New audit defect | Reproduction proves repository ownership | Reopen rolling scope; create or link issue before repair | Repair on correct line, merge, refreeze, and restart affected final tasks | Failing regression, fix, merged proof, and two new clean passes |
| Suspected issue is not a defect | Source or runtime evidence contradicts concern | Classify as disproven with evidence | No code change; retain rationale | Focused negative test or source trace |
| GitHub authentication or scope changes | Identity, remote, API, Project, wiki, or check readback fails | Stop remote mutations | Restore EnVisione access or remain EXT-005 blocked | Fresh auth and state readback |
| Release-side action detected | Release, tag, upload, stable label, or announcement appears in phase activity | Stop completion and report boundary breach | Do not perform further external mutation; preserve audit and request owner direction | Negative release-action audit after resolution |

## Verification Matrix

| Requirement or task | Static or unit | Integration | Real workflow or runtime | Negative and recovery | Evidence artifact |
|---|---|---|---|---|---|
| CORE-REQ-015, P007-TASK-006 | Forge focused tests, full `test`, data diff, GameTests, build, identity and packaging checks | Complete Forge component matrices | Dedicated server, client, multiplayer, restart, reconnect, reload | Corruption, rollback, provider failure, disconnect, replay, full inventory, invalid reload | Forge exact-revision verification packet |
| CORE-REQ-015, P007-TASK-007 | NeoForge issue 22 regression, full `test`, applicable data and GameTests, build | NeoForge client/common boundary and navigation | Dedicated server, client, affected Windows screen workflow | Stale response, resize, reconnect, missing client-only boundary | NeoForge exact-revision verification packet |
| CORE-REQ-017, P007-TASK-003, P007-TASK-004 | Links, terminology, literals, versions, commands, configs, paths | Source-to-document and branch-to-wiki comparison | Operator procedure rehearsal where commands or recovery changed | Stale, contradictory, unsafe, or prematurely released wording | Documentation reconciliation packet and merged PRs |
| CORE-REQ-018, P007-TASK-011 | Metadata, archive, dependency, secret, and source manifest inspection | Built bytes bind to exact revisions and verification packets | Local staging and rehash | Wrong version, unexpected entry, hash mismatch, forbidden release action | Two local JARs, archive inventories, SHA-256, SHA-512, manifests |
| CORE-REQ-019, P007-TASK-012 through P007-TASK-014 | Complete audit inventories and source review | Cross-audit traceability and unchanged-revision comparison | Runtime and external results reconciled | New defect reopens loop; unclassified item blocks pass | Two complete timestamped clean audit packets |
| CORE-REQ-020, P007-TASK-010, P007-TASK-015 | Issue evidence checklist | Merge, check, Project, milestone, and wiki consistency | Reporter acceptance and exact invalid-state proof | Blocked issues remain open; mismatched result rejected | Final issue comments and GitHub state readback |
| EXT-001 | Screen lifecycle proof | Exact NeoForge candidate | Reporter Windows test | Wrong environment or artifact blocks | Issue 22 acceptance record |
| EXT-002 | Catalog and migration evidence | Exact Forge candidate and preserved world copy | Reporter startup, restart, reload | Data deletion or incomplete fixture blocks | Issue 25 acceptance record |
| EXT-003 | Serialization and recovery regressions | Player, claim, transaction, persistence lifecycle | Repeated restart and reconnect | Private or unmatched state, unrelated-field change, loss, or duplication blocks | Sanitized before-and-after state packet |
| EXT-004 | Transaction, stock, replay, persistence tests | Dedicated server and two-client matrix | Success and failure across restart and reconnect | Any unexplained conservation or lifecycle state blocks | Logs, state snapshots, and conservation report |
| EXT-005 | Identity, remote, branch, issue, PR, and check inspection | Project, milestone, wiki, and closure synchronization | Final remote readback | Auth or scope mismatch blocks | GitHub preflight and final state packet |
| P007-TASK-016 | Master plan validation and traceability audit | All phase packets joined to endpoint | None beyond already required real workflows | Any missing requirement, prerequisite, exclusion, or forbidden-action proof blocks | Final plan-wide completion packet |

Fixtures must be copied, sanitized, versioned, and hash-identified. Runtime evidence must name operating system, Java, Minecraft, loader, FutureShops version, source commit, JAR hash, configuration and fixture revision, client count, procedure, expected result, actual result, and decisive logs. Lower-fidelity proof never replaces reporter acceptance, exact invalid-state recovery, controlled multiplayer, or real client and server smoke tests.

## Documentation, Operations, and Release

- Update `README.md` and `DOCUMENTATION.md` for exact candidate versions, supported branches, setup, build and runtime commands, verified behavior, recovery, compatibility, known limits, and prepared-but-unpublished status.
- Update `docs/README.md` navigation and only the focused guides affected by merged changes. Expected review surfaces include backup and restore, compatibility, configuration and offers, markets, ATM, regression gaps, security dispositions, and beta release notes.
- Recovery steps must require server shutdown where appropriate, one complete matching backup, preserved evidence, expected validation results, stop conditions, and non-destructive restore. They must never recommend deleting isolated player or market files.
- Candidate notes distinguish previous published beta behavior from the local `3.0.0-beta.2` and `2.2.1` candidates and must not provide a false download or release claim.
- Issue, PR, milestone, Project, and wiki content reflects merged, verified status and retains external blocker language until the gate passes.
- Prepare local artifacts, checksums, manifests, and verification packets only. Do not invoke publication tasks, create GitHub Releases, upload artifacts, create or push public tags, publish to mod platforms, mark stable, or announce.
- Any later release action must begin from separate explicit owner authorization and revalidate that the exact retained bytes and hashes remain the approved candidates.

## Risks and Evidence Invalidation

| Risk | Prevention | Detection | Recovery | Evidence invalidated | Reverification |
|---|---|---|---|---|---|
| Stale revision evidence | Freeze exact hashes after all merges and stamp every packet | Hash mismatch at packet or GitHub boundary | Refreeze after intentional change | All downstream proof in the affected line | Repeat focused through artifact checks and both audits |
| External test uses different bytes | Bind requests and results to commit and JAR hashes | Reporter record or checksum mismatch | Obtain exact result on matching bytes | Corresponding EXT result and issue closure | Repeat prerequisite workflow |
| Cross-line contamination | Separate worktrees, ancestry checks, and line-specific PRs | Diff and build metadata inspection | Reviewed line-specific revert or correction | Affected line tests, docs, artifact, audits | Full affected-line ladder and both audits |
| Documentation advertises unreleased state | Prepared-and-unpublished language and post-merge wiki sequencing | Source-to-doc and wiki comparison | Correct and merge documentation before refreeze | Documentation, exact revision, wiki, both audits | Recheck links, literals, diff, wiki, and revision-bound proof |
| Dependency graph drifts | Record graph and keep PR 28 separate unless necessary | Lock, graph, Dependabot, or artifact difference | Classify and integrate only through authorized scope | Build, security, packaging, runtime, hashes | Full affected-line verification and security audits |
| Runtime fixture contamination | Immutable copies, separate worlds, configs, ports, and logs | Fixture hashes or unexpected state | Discard only the test copy after preserving failure evidence; recreate from source fixture | Runtime and conservation evidence | Repeat affected workflows |
| Sensitive evidence enters output | Minimize and sanitize before retention or GitHub use | Final diff, log, packet, and archive scan | Remove exposure where possible and route confidentially | Security, privacy, documentation, issue evidence | Repeat privacy audit and affected packet generation |
| Hidden rolling defect | Complete inventories and two independent passes | New observation or unexplained mismatch | CORE-REQ-009 issue-before-repair loop | Both clean passes and affected downstream evidence | Repair, refreeze, full affected ladders, two new passes |
| Flaky or skipped check | Repeatability and explicit applicability ledger | Non-deterministic result, skip marker, unavailable environment | Diagnose and file if repository-owned; remain blocked otherwise | Dependent requirement and clean audit | Stable rerun at exact revision |
| Candidate copy changes | Read-only custody and dual hashes | Size or digest mismatch | Quarantine and recreate packet | Artifact, external hash-bound evidence, audits | Reinspect and rehash; repeat consumers |
| GitHub state changes after closure | Final remote readback and check SHA binding | Reopen, failed rerun, new review, or Project drift | Reconcile evidence-backed state | CORE-REQ-017, CORE-REQ-020, final packet | Repeat tracking and final DoD audit |
| Prohibited release mutation | Explicit negative action checklist | Remote release, tag, platform file, or announcement appears | Stop and request owner direction | Completion endpoint | Repeat forbidden-action audit after externally resolved state |

## Phase Completion Packet

The completion packet is stored outside the protected plan set and contains, at minimum:

1. The validated master and phase index identity, unchanged legacy-plan hashes, and final traceability ledger for CORE-REQ-001 through CORE-REQ-020, CORE-PHASE-000 through CORE-PHASE-007, SRC-001 through SRC-013, DEC-001 through DEC-006, EXT-001 through EXT-005, FUT-001 through FUT-005, and NG-001 through NG-008.
2. Exact Forge and NeoForge support branch hashes, ancestry, merge commits, clean-tree records, final metadata, and line-specific pull request evidence.
3. Every focused, unit, data, GameTest, build, server, client, multiplayer, restart, reconnect, reload, rollback, corruption, recovery, fault, diff, dependency, security, and packaging result, including explicit applicability decisions.
4. EXT-001 reporter acceptance for issue 22, EXT-002 reporter acceptance for issue 25, EXT-003 issue 32 exact-state and safe-recovery proof, EXT-004 controlled multiplayer and conservation proof, and EXT-005 GitHub identity and final state proof.
5. Merged documentation commits, source-to-doc matrix, link and terminology results, exact literal checks, complete documentation diff, and post-merge wiki comparison.
6. Final closure records for issues 22, 25, 32, 33, 34, every rolling audit issue, all relevant pull requests, milestones, Project items, reviews, and required checks.
7. The exact local Forge `futureshops-3.0.0-beta.2.jar` and NeoForge `2.2.1` candidate JAR, their archive inventories, expanded metadata, byte lengths, source revision manifests, SHA-256, SHA-512, and checksum verification results.
8. Two complete timestamped convergence audit packets proving identical source and artifact hashes, green checks, complete classifications, and zero new repository-owned defects.
9. Recovery and rollback evidence for every changed schema or operational procedure, including complete matching backup and non-destructive restore proof.
10. A final negative scan for credentials, private raw logs, sensitive NBT, absolute local paths, caches, generated worlds, build output committed to source, unrelated edits, release records, public tags, uploads, platform publication, stable claims, and announcements.
11. A final endpoint verdict. `complete` is permitted only when every item passes. Otherwise the packet names the exact requirement, prerequisite, revision, environment, command or evidence gap, owning issue, and next authorized action without claiming phase or plan completion.

## Final Plan-Wide Definition of Done Audit

The owner-selected endpoint passes only if the final auditor can answer yes, with exact evidence, to every item below:

- Are all twenty canonical requirements satisfied at their required fidelity, including every carried requirement from earlier phases?
- Have all eight contiguous phase exit gates passed without treating an internal integration gate as final external acceptance?
- Are issues 22, 25, 32, 33, 34, and all rolling audit issues fixed on the correct line, merged, proven, and closed?
- Are EXT-001 and EXT-002 reporter acceptances present, EXT-003 exact-state recovery proven, EXT-004 controlled multiplayer proven, and EXT-005 repository state verified?
- Do both exact support revisions preserve their pinned loader, Minecraft, Java, build, mappings, schema, and compatibility boundaries and use exactly `3.0.0-beta.2` and `2.2.1`?
- Have security, privacy, commands, permissions, persistence, databases, migration, recovery, backend integration, runtime, documentation, dependencies, and artifacts been audited twice at unchanged revisions?
- Did both complete audit passes find no new repository-owned defect, with no unclassified finding or stale green check?
- Do the local candidate JARs, internal metadata, source manifests, SHA-256, SHA-512, external results, and verification packets all refer to the same exact bytes and revisions?
- Do documentation, issues, pull requests, milestones, Project items, wiki, checks, and branch records agree with merged candidate state and unpublished status?
- Are the legacy plans unchanged and all optional, future, and non-goal boundaries preserved?
- Has no CurseForge, Modrinth, GitHub Release, artifact upload, public tag, stable publication, or announcement occurred under this plan?

One no, unknown, unavailable, mismatched, skipped-without-justification, or lower-fidelity answer blocks completion. The phase remains externally blocked when a mandatory prerequisite is unavailable, even if every independent internal task passes.

## Next Transition

There is no later implementation phase. After `P007-TASK-016` passes, report final plan-wide completion with the exact Forge and NeoForge revisions, artifact hashes, external acceptance records, clean-audit pair, and unpublished status. Retain the completion packet for a later separately authorized release decision.

Do not start publication, create a release or tag, upload an artifact, declare stable status, or announce availability. If any completion gate fails, remain in CORE-PHASE-007, preserve the evidence, and resume at the earliest invalidated task after the blocker is resolved.
