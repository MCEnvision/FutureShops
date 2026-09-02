# Phase 007 Execution Plan

> **Plan ID:** PLAN-PHASE-007
> **Phase ID:** CORE-PHASE-007
> **Owner:** Release readiness
> **Classification:** MANDATORY
> **Master plan:** [plan.md](../plan.md)
> **Phase sequence:** 007 of 007

## Purpose and Ownership

This phase proves the final Forge 1.20.1 and NeoForge 1.21.1 candidates against the complete product contract at exact unchanged revisions. It reconciles tracked documentation and repository state, repeats every required audit to convergence, closes or confirms closure of every scoped issue, and prepares two integrity-checked unpublished candidate artifacts.

The master plan owns scope, owner decisions, the twenty canonical requirements, phase topology, support-line boundaries, and the completion endpoint. This file owns only the dependency-ordered execution blueprint for `CORE-REQ-015`, `CORE-REQ-017`, `CORE-REQ-018`, `CORE-REQ-019`, and `CORE-REQ-020`. Earlier phases own their repairs and primary evidence. Phase 007 revalidates those contracts at the final candidate revisions.

`EXT-001`, `EXT-002`, `EXT-003`, and `EXT-004` are resolved or superseded historical traceability IDs only. They are not dependencies, acceptance gates, evidence requests, stop conditions, transition conditions, or completion alternatives. Issue-specific proof is generated locally from deterministic fixtures and controlled runtime environments under `DEC-007`. `EXT-005` remains the only active external prerequisite and is available and authorized for repository synchronization.

No outside impediment is known.

Completion requires every repository-controlled requirement to pass locally, every verified defect to be repaired through the issue-before-repair workflow, both exact support branches to be green, two complete audit passes to be clean at unchanged revisions and artifact hashes, every scoped issue to have correct final state, and both exact candidate packets to be complete. This phase authorizes internal signed phase tags as integration evidence. It does not authorize a GitHub Release, product release tag, artifact upload, CurseForge or Modrinth publication, stable designation, announcement, or other public release action.

## Evidence-Based Entry State

| Evidence class | Area | Finding | Source or command | Freshness condition |
|---|---|---|---|---|
| VERIFIED | Plan authority | The final phase owns `CORE-REQ-015`, `CORE-REQ-017`, `CORE-REQ-018`, `CORE-REQ-019`, and `CORE-REQ-020` and depends only on `CORE-PHASE-006` and `EXT-005` | `plan.md`, Sections 12 through 16 | Any authorized master or index revision requires rereading the complete plan set |
| VERIFIED | Historical prerequisite disposition | `EXT-001` through `EXT-004` are resolved or superseded historical records and have no executable role | `plan.md`, Section 10, `SRC-014`, and `DEC-007` | Any material Plan Creator revision |
| AVAILABLE | Local runtime capacity | The 64 GB workstation can host one isolated dedicated server and at least two independent client JVMs and profiles. The 96 GB node1 host can run the temporary isolated server when additional capacity or repeatability is useful | `SRC-014` and `DEC-007` | Host, memory, network-isolation, process, or fixture capability changes |
| AVAILABLE | Repository authority | Authenticated EnVisione access can inspect and update the authoritative repository, issues, pull requests, checks, milestones, Project state, wiki state, and internal phase tags | `EXT-005` | Identity, remote, permission, scope, or signing configuration changes |
| OBSERVED | Forge line | Forge targets Minecraft 1.20.1, Forge 47.4.20, Java 17, Mojang mappings, and Gradle 8.14.4. The locked final version is `3.0.0-beta.2` | `SRC-009`, Forge build metadata, and `DEC-005` | Any support-head, wrapper, plugin, dependency, mapping, metadata, source, resource, or configuration change |
| OBSERVED | NeoForge line | NeoForge targets Minecraft 1.21.1, NeoForge 21.1.233, Java 21, and ModDevGradle 2.0.141. The locked final version is `2.2.1` | `SRC-009`, NeoForge build metadata, and `DEC-005` | Any support-head, wrapper, plugin, dependency, mapping, metadata, source, resource, or configuration change |
| OBSERVED | Documentation surface | `README.md`, `DOCUMENTATION.md`, `docs/README.md`, focused configuration, market, ATM, backup, recovery, compatibility, audit, test, and beta-readiness guides form the tracked documentation surface | `SRC-010` and the current documentation tree | Any behavior, command, config, schema, compatibility, recovery, version, or document-layout change |
| ENTRY CONTRACT | Upstream completion | Phases 000 through 006 supply exact merged revisions, green checks, internal signed phase tags where required, issue records, local runtime packets, invalidation records, and no unresolved repository-owned defect or mandatory gate | Phase completion packets and fresh remote readback | Any late merge, reopened issue, failed check, tag mismatch, or material source change |
| PROPOSED | Candidate artifacts | The intended local artifacts are `futureshops-3.0.0-beta.2.jar` and `futureshops-2.2.1.jar`; neither is final evidence until built, inspected, hashed, and bound to frozen merged revisions | `CORE-REQ-018` and branch build metadata | Any candidate commit, metadata, dependency graph, build environment, or artifact-byte change |

No earlier phase packet, historical build, or previous audit remains final proof after a material change. Entry begins by resolving the actual merged support heads, upstream tag targets, issue states, and evidence invalidation graph.

## Scope Boundaries

### Included Scope

- `CORE-REQ-015`: run complete local verification for both exact candidates, including focused regressions, deterministic suites, applicable data generation and GameTests, builds, dedicated servers, clients, multiplayer, restart, reconnect, reload, rollback, corruption, recovery, fault injection, JAR inspection, and complete diff inspection.
- `CORE-REQ-017`: reconcile user, administrator, maintainer, configuration, migration, recovery, verification, candidate-readiness, issue, Project, milestone, and wiki-ready documentation with merged behavior.
- `CORE-REQ-018`: prepare and inspect exact local unpublished Forge `3.0.0-beta.2` and NeoForge `2.2.1` JARs with expanded metadata, archive inventories, source revision manifests, SHA-256, SHA-512, and checksum verification.
- `CORE-REQ-019`: run complete issue, security, privacy, command, permission, persistence, database, migration, recovery, integration, runtime, documentation, dependency, packaging, and release-readiness audits twice at unchanged candidate revisions and artifact hashes.
- `CORE-REQ-020`: verify correct closure of issues 22, 25, 32, 33, and 34 and every rolling audit issue, then reconcile pull requests, checks, milestones, Project items, branches, tags, and wiki state.
- Issue 22 final proof: accepted correction, correct merge into `1.21.1`, all 16 affected screen lifecycles, client and dedicated-server safety, exact merged-revision checks, artifact inspection, signed phase tag, and closed issue state.
- Issue 25 final proof: deterministic local beta-transition and current-state matrices. A supported-state defect must be filed and repaired. An owner-approved compatibility disposition may close the remaining unsupported beta state only after local evidence proves no supported-state defect remains.
- Issue 32 final proof: deterministic bounded corruption and fuzzing, ownership isolation, modded-item sentinels, unrelated-player-data sentinels, receipt and delivery-slot reconciliation, restart and reconnect, conservation, and repeated non-destructive recovery.
- Issues 33 and 34 final proof: exact bounded bulk-listing behavior and isolated dedicated-server plus at least two independent-client transaction, concurrency, conservation, restart, and reconnect behavior.
- Exact branch, pull request, merge, check, ancestry, and signed internal phase-tag evidence for every required integration step.

### Explicit Exclusions

- `FUT-001` through `FUT-005` remain excluded.
- No product repair may precede duplicate search and the canonical issue or private advisory required by `CORE-REQ-009`.
- No cross-line merge, broad compatibility redesign, platform upgrade, loader substitution, new storage framework, or unrelated feature is authorized.
- No lower-fidelity source scan or integrated-server result substitutes for a required dedicated server, client, multiplayer, persistence, corruption, recovery, or JAR workflow.
- No test or recovery deletes or selectively restores player data, worlds, journals, checkpoints, ledgers, custody, claims, receipts, migration records, or relevant failure evidence.
- No GitHub Release, CurseForge or Modrinth upload, artifact attachment, product release tag, stable declaration, public download claim, or announcement is authorized.

## Phase Contract

### CORE-PHASE-007 — Final Candidate Proof and Unpublished Artifact Preparation

**Objective:** Freeze exact merged Forge and NeoForge candidates, complete all local deterministic and real-runtime proof, reconcile documentation and repository state, obtain two unchanged-revision clean audits, close every scoped issue correctly, and prepare exact integrity-checked unpublished artifacts without publication.
**Owner:** Release readiness
**Dependencies:** CORE-PHASE-006, EXT-005
**Canonical requirements:** CORE-REQ-015, CORE-REQ-017, CORE-REQ-018, CORE-REQ-019, CORE-REQ-020
**Documentation and release impact:** Reconcile tracked user and technical documentation, issue and Project state, wiki-ready material, candidate metadata, checksums, manifests, and internal phase tags. Publication remains excluded.
**Next transition:** Final plan-wide Definition of Done and owner-selected completion endpoint

**Entry criteria**

- `CORE-PHASE-006` has fully passed. Every required Phase 001 through Phase 006 repair is merged into the correct support branch, every required check is green, every required internal phase tag verifies, and no repository-owned defect or mandatory evidence gate remains unresolved.
- Fresh fetch and GitHub readback resolve one exact current `origin/1.20.1` head and one exact current `origin/1.21.1` head. Ancestry proves support-line isolation and contains every intended merge.
- Every scoped issue and rolling finding has a canonical record, affected line, accepted disposition, merged repair where required, focused regression, review result, and current invalidation state.
- Any remaining documentation or version metadata change is enumerated before freeze and assigned to an isolated line-specific Phase 007 branch created from the latest approved support head.
- `EXT-005`, remote identity, signing identity, required check names, merge policy, Project access, wiki access, and tag-push authority are verified.
- The local runtime harness, deterministic fixtures, clean worktrees, separate Java toolchains, isolated worlds, ports, profiles, logs, and evidence destinations are ready.

**Implementation scope**

- `CORE-REQ-015` executes the complete deterministic, integration, real-runtime, corruption, recovery, multiple-client, JAR, and final-diff verification surface for both exact support-line candidates.
- `CORE-REQ-017` reconciles every affected tracked document, issue, pull request, milestone, Project item, internal phase tag, and wiki-ready surface with exact merged behavior and prepared-unpublished status.
- `CORE-REQ-018` produces and inspects the exact local `futureshops-3.0.0-beta.2.jar` and `futureshops-2.2.1.jar` packets, expanded metadata, archive inventories, source manifests, SHA-256, SHA-512, and verified rehash results.
- `CORE-REQ-019` executes two complete issue, security, privacy, command, permission, persistence, database, integration, runtime, documentation, dependency, packaging, and release-readiness audits at unchanged candidate coordinates.
- `CORE-REQ-020` verifies the correct final state of issues 22, 25, 32, 33, 34 and every rolling finding, with exact branch, merge, check, tag, evidence, and tracking readback.
- `CORE-REQ-009` governs every newly verified finding. Duplicate search and the canonical issue or private advisory precede repair, which returns through correct-line merge, refreeze, and affected reruns.

**Execution order**

1. `P007-TASK-001` executes `CORE-REQ-015`, `CORE-REQ-019`, and `CORE-REQ-020` by revalidating plan authority, repository identity, `EXT-005`, support heads, upstream merges, internal phase tags, issue state, branch isolation, and evidence invalidation.
2. `P007-TASK-002` executes `CORE-REQ-019` and `CORE-REQ-020` by constructing the final traceability ledger for all twenty requirements, eight phases, fourteen sources, seven decisions, five prerequisite IDs, exclusions, scoped issues, and rolling findings.
3. `P007-TASK-003` executes `CORE-REQ-017` and `CORE-REQ-018` by reconciling Forge documentation and exact `3.0.0-beta.2` metadata on an isolated Forge Phase 007 branch and integrating it through the required pull request into `1.20.1`.
4. `P007-TASK-004` executes `CORE-REQ-017` and `CORE-REQ-018` by reconciling NeoForge documentation and exact `2.2.1` metadata on an isolated NeoForge Phase 007 branch and integrating it through the required pull request into `1.21.1`.
5. `P007-TASK-005` executes `CORE-REQ-015`, `CORE-REQ-018`, and `CORE-REQ-019` by freezing the two exact merged candidate revisions, dependency graphs, configuration and fixture revisions, and invalidating all evidence tied to earlier coordinates.
6. `P007-TASK-006` executes `CORE-REQ-015` and `CORE-REQ-019` by running the complete Forge Java 17 deterministic, integration, runtime, corruption, recovery, multiplayer, documentation, dependency, and packaging ladder.
7. `P007-TASK-007` executes `CORE-REQ-015` and `CORE-REQ-019` by running the complete NeoForge Java 21 deterministic, integration, runtime, screen-lifecycle, documentation, dependency, and packaging ladder.
8. `P007-TASK-008` executes `CORE-REQ-005`, `CORE-REQ-012`, `CORE-REQ-014`, `CORE-REQ-015`, and `CORE-REQ-020` by rerunning the full mandatory local issue 32 deterministic corruption, fuzz, ownership-isolation, persistence, receipt, conservation, and non-destructive recovery campaign.
9. `P007-TASK-009` executes `CORE-REQ-006`, `CORE-REQ-013`, `CORE-REQ-014`, and `CORE-REQ-015` by running the full isolated Forge dedicated-server and at least two independent-client integration, concurrency, lifecycle, failure, restart, reconnect, and conservation matrix.
10. `P007-TASK-010` executes `CORE-REQ-003`, `CORE-REQ-004`, `CORE-REQ-015`, and `CORE-REQ-020` by revalidating issue 22 accepted-fix closure and issue 25 local beta-transition, current-state, supported-state, and compatibility-disposition evidence at the exact candidates.
11. `P007-TASK-011` executes `CORE-REQ-018` by building, staging locally, inspecting, hashing, and sealing the exact two unpublished candidate integrity packets.
12. `P007-TASK-012` executes `CORE-REQ-019` by running complete convergence audit pass one across both unchanged candidate revisions and exact retained artifact bytes.
13. `P007-TASK-013` executes `CORE-REQ-009` and `CORE-REQ-019` by classifying every pass-one observation and routing each verified repository-owned defect through correct-line repair, merge, refreeze, and all affected reruns.
14. `P007-TASK-014` executes `CORE-REQ-019` by running complete convergence audit pass two with the same source, documentation, dependencies, fixtures, harnesses, issue scope, and artifact bytes as the clean pass one.
15. `P007-TASK-015` executes `CORE-REQ-017` and `CORE-REQ-020` by closing any eligible open issue, confirming prior closures, and reconciling pull requests, checks, milestones, Project items, branches, tags, evidence links, and wiki state.
16. `P007-TASK-016` executes `CORE-REQ-015`, `CORE-REQ-017`, `CORE-REQ-018`, `CORE-REQ-019`, and `CORE-REQ-020` by performing the final plan-wide Definition of Done and forbidden-action audit, creating and verifying the final signed internal phase tags, and sealing the completion packet.

Tasks 003 and 004 may proceed concurrently only in separate line-specific worktrees. Tasks 006 and 007 may proceed concurrently only after Task 005 and only with separate Java installations, Gradle state, runtime roots, ports, profiles, worlds, logs, and evidence destinations. Tasks 008 through 010 may execute in bounded parallel after the applicable candidate ladder establishes a usable exact build. Tasks 011 through 016 remain sequential. Any material change returns execution to the earliest invalidated task and requires a new candidate freeze.

**Required evidence**

- Exact support-branch, work-branch, pull-request head, merge commit, ancestry, required-check, review, clean-tree, and internal tag target records.
- Complete issue, requirement, phase, source, decision, prerequisite-history, non-goal, and invalidation traceability.
- Complete local Forge and NeoForge command records and decisive results at exact frozen revisions.
- Dedicated server, client, at least two independent-client, restart, reconnect, reload, rollback, corruption, fuzz, recovery, failure, and conservation evidence with exact fixtures and environments.
- Complete security, privacy, command, permission, persistence, database, integration, runtime, documentation, dependency, packaging, and release-readiness matrices.
- Exact candidate JAR names, byte lengths, internal metadata, archive inventories, dependency boundaries, source manifests, SHA-256, SHA-512, and successful rehash results.
- Two timestamped complete audit packets with identical candidate commits and artifact hashes and zero new repository-owned defect.
- Correct final issue, pull request, milestone, Project, wiki, branch, check, and internal tag state.
- A negative publication record proving that no prohibited public release action occurred.

**Exit criteria**

- Every acceptance criterion for `CORE-REQ-001` through `CORE-REQ-020` has exact current evidence and every `CORE-PHASE-000` through `CORE-PHASE-007` exit gate passes.
- `EXT-005` remains verified. `EXT-001` through `EXT-004` remain historical only and do not appear in an executable gate.
- Issues 22, 25, 32, 33, and 34 and every rolling finding have correct merged evidence and final state.
- Issue 22 is closed after accepted-fix integration and exact merged `1.21.1` evidence. Issue 25 has local beta-transition and current-state proof plus a merged supported-state repair or an owner-approved compatibility disposition after proving no supported-state defect remains.
- Issue 32 passes the complete deterministic local corruption, fuzz, ownership-isolation, and repeated non-destructive recovery corpus. Issue 34 and the complete integration surface pass on one isolated dedicated server with at least two independent clients.
- Both support branches are green at the exact frozen candidate revisions, all required internal phase tags verify against their intended commits, and repository tracking agrees with those revisions.
- Two consecutive complete audits at unchanged revisions and unchanged artifact hashes find no new repository-owned defect, unclassified observation, stale check, or evidence gap.
- Exact local `futureshops-3.0.0-beta.2.jar` and `futureshops-2.2.1.jar` packets are complete and mutually consistent.
- No known repository-owned defect remains.
- No known external blocker remains, and `EXT-005` is verified available for final repository readback.
- No known mandatory phase-owned defect remains.
- Legacy plans remain byte-for-byte unchanged and all optional, future, and non-goal boundaries remain intact.
- No public release action has occurred.
- The owner-selected completion endpoint and plan-wide Definition of Done pass with exact evidence.

## Inputs and Upstream Contracts

| Input or contract | Provider | Required state | Validation | Failure behavior |
|---|---|---|---|---|
| Authoritative plan set | `plan.md` and `plan.index.json` | Valid, contiguous, and coherent through Phase 007 | Run plan-set validation and compare stable IDs, phase topology, and registered paths | Stop on a material contract conflict. Do not reinterpret the master locally |
| Legacy plans | `SRC-002` and `SRC-003` | Preserved byte for byte | Compare recorded baseline digests | Preserve the unexpected state and stop. Do not repair protected history |
| Forge integrated head | Phases 002 through 006 | All required Forge repairs, docs, checks, and issue states integrated into `origin/1.20.1` | Fetch, ancestry, PR, check, diff, issue, and required tag reconciliation | Finish correct-line integration before candidate freeze |
| NeoForge integrated head | Phase 001 and any later line-specific repair | Issue 22 and every independently applicable repair integrated into `origin/1.21.1` | Fetch, ancestry, PR, check, diff, issue, and `phase-001-neoforge-issue-22` tag reconciliation | Finish correct-line integration before candidate freeze |
| Security and command packet | `CORE-PHASE-004` | Complete exact-revision inventories, clean matrices, merged repairs, and verified `phase-004-security-command-audit` tag | Coverage, revision, issue, merge, check, and tag readback | Return stale rows to the owning matrix and rerun |
| Persistence and recovery packet | `CORE-PHASE-005` | Complete deterministic local audit, issue 32 corpus, merged repairs, and verified `phase-005-persistence-recovery` tag | Inventory, fixture, lineage, conservation, merge, check, and tag readback | Stop mutation testing on integrity mismatch and preserve the complete cohort |
| Backend integration packet | `CORE-PHASE-006` | Complete exact-revision local subsystem combinations, failure states, dedicated-server, and multiple-client proof with no unresolved defect or mandatory gate | Interface coverage, fixtures, runtime topology, merge, checks, and signed `phase-006-backend-integration` tag | Complete Phase 006 before entry. No partial handoff is accepted |
| Documentation inventory | `SRC-010` | All tracked surfaces and line-specific variants are mapped | Source, config, commands, tests, issues, and docs comparison | Repair inaccuracies through the correct line-specific branch before freeze |
| Local runtime environment | `SRC-014` and `DEC-007` | Workstation server plus clients, or node1 isolated server plus independent clients, with pinned worlds and profiles | Host manifest, process identities, ports, Java, artifact, world, config, profile, and fixture hashes | Repair or reschedule the local harness and rerun. Do not reduce fidelity |
| Repository authority | `EXT-005` | EnVisione identity, authoritative remote, issues, PRs, checks, Project, wiki, and tag access | Read-only preflight before mutation and exact readback after mutation | Stop remote mutation until the same authority is restored |

## Outputs and Downstream Contracts

| Output or contract | Consumer | Guaranteed state | Compatibility or versioning | Evidence |
|---|---|---|---|---|
| Forge candidate packet | Owner and any later separately authorized release workflow | Exact `1.20.1` merged revision passes every required local audit and runtime gate | Minecraft 1.20.1, Forge 47.4.20, Java 17, version `3.0.0-beta.2` | Commit, ancestry, commands, environment, JAR, metadata, manifests, hashes, audit pair |
| NeoForge candidate packet | Owner and any later separately authorized release workflow | Exact `1.21.1` merged revision passes every required local audit and screen lifecycle gate | Minecraft 1.21.1, NeoForge 21.1.233, Java 21, version `2.2.1` | Commit, ancestry, commands, environment, JAR, metadata, manifests, hashes, audit pair |
| Documentation reconciliation packet | Users, operators, maintainers, and wiki | Tracked docs describe only merged behavior and prepared unpublished candidates | Support-line behavior and compatibility remain explicit | Source-to-doc matrix, links, literal checks, merged PRs, wiki readback |
| Issue closure packet | Repository governance | Issues 22, 25, 32, 33, 34 and rolling findings match merged evidence | Owner-approved issue 25 compatibility disposition is retained when used | Final comments, commits, tests, runtime packets, closures, milestones, Project state |
| Clean audit pair | Final endpoint | Two complete audits at identical revisions and artifact hashes find zero new repository-owned defect | Any material change invalidates the pair | Timestamps, inventory versions, findings, hashes, green checks |
| Internal phase-tag packet | Sequential governance | Required signed internal tags identify exact verified phase commits | Internal evidence only, never a product version or release tag | Tag objects, target commits, signatures, remote presence |
| Plan-wide completion packet | Owner | Every requirement, phase, decision, exclusion, historical prerequisite, and forbidden action has evidence-backed final status | Prepared and unpublished only | Final traceability ledger and Definition of Done audit |

## Historical Prerequisite Disposition

| Stable ID | Final classification | Required Phase 007 handling |
|---|---|---|
| `EXT-001` | Historical resolved traceability for issue 22 | Retain the ID in provenance. Use accepted-fix merge and exact local `1.21.1` evidence for closure |
| `EXT-002` | Historical superseded traceability for issue 25 | Retain the ID in provenance. Use local beta-transition and current-state evidence plus the owner-approved compatibility disposition when applicable |
| `EXT-003` | Historical superseded traceability for issue 32 | Retain the ID in provenance. Use mandatory deterministic local corruption, fuzz, ownership-isolation, and recovery evidence |
| `EXT-004` | Historical resolved traceability for controlled multiplayer | Retain the ID in provenance. Use the authorized local dedicated-server and independent-client environment |
| `EXT-005` | Active, mandatory, available, and authorized | Verify EnVisione repository authority at entry and final readback |

The first four rows never enter task dependencies, failure routing, exit alternatives, or completion status. Their absence from new evidence cannot delay, qualify, or weaken local verification.

## Work Packages

| Task ID | Requirement IDs | Work | Inputs and dependencies | Outputs | Affected components or interfaces | Verification |
|---|---|---|---|---|---|---|
| `P007-TASK-001` | `CORE-REQ-015`, `CORE-REQ-019`, `CORE-REQ-020` | Perform final read-only plan, Git, GitHub, issue, check, branch, tag, and invalidation preflight | `CORE-PHASE-006`, `EXT-005`, `SRC-009`, `SRC-013` | Exact entry and invalidation ledger | Plan set, remotes, branches, PRs, issues, checks, tags, worktrees | Identity, auth, ancestry, target, signature, dirty state, required checks, no unresolved defect or mandatory gate |
| `P007-TASK-002` | `CORE-REQ-019`, `CORE-REQ-020` | Build final source, decision, requirement, phase, issue, finding, exclusion, and historical-ID traceability | Task 001 and every upstream packet | One complete final ledger | Plan set, issues, confidential records, evidence graph | No missing, duplicate, stale, conflicting, or unclassified row |
| `P007-TASK-003` | `CORE-REQ-017`, `CORE-REQ-018` | Reconcile Forge docs and exact `3.0.0-beta.2` metadata, verify, review, merge into `1.20.1`, and read back the merge | Latest approved Forge head and Task 002 | Exact merged Forge candidate source state | README, technical docs, focused guides, Gradle and mod metadata, candidate notes | Link and literal checks, build identity, clean diff, green PR, merged ancestry |
| `P007-TASK-004` | `CORE-REQ-017`, `CORE-REQ-018` | Reconcile NeoForge docs and exact `2.2.1` metadata, verify, review, merge into `1.21.1`, and read back the merge | Latest approved NeoForge head and Task 002 | Exact merged NeoForge candidate source state | Branch-applicable docs, Gradle and NeoForge metadata, candidate notes | Link and literal checks, build identity, clean diff, green PR, merged ancestry |
| `P007-TASK-005` | `CORE-REQ-015`, `CORE-REQ-018`, `CORE-REQ-019` | Freeze candidate commits, dependency graphs, configs, fixtures, harness versions, docs, and evidence coordinates | Tasks 003 and 004 | Immutable candidate-attempt manifest | Git heads, manifests, worktrees, dependency and fixture identities | Clean worktrees, exact hashes and versions, no pending revision-changing work |
| `P007-TASK-006` | `CORE-REQ-015`, `CORE-REQ-019` | Run complete Forge deterministic, audit, integration, runtime, recovery, dependency, and packaging ladder | Frozen Forge coordinates | Complete Forge exact-revision packet | All Forge source, resources, configs, stores, commands, networks, runtime, docs, JAR | Every applicable stage passes in order with exact command and result |
| `P007-TASK-007` | `CORE-REQ-015`, `CORE-REQ-019` | Run complete NeoForge deterministic, audit, screen, runtime, dependency, and packaging ladder | Frozen NeoForge coordinates | Complete NeoForge exact-revision packet | NeoForge source, screens, common/client boundary, resources, docs, JAR | Every applicable stage passes in order with exact command and result |
| `P007-TASK-008` | `CORE-REQ-005`, `CORE-REQ-012`, `CORE-REQ-014`, `CORE-REQ-015`, `CORE-REQ-020` | Rerun and extend the deterministic local issue 32 corpus | Tasks 005 and 006, Phase 002 and 005 corpora | Final local corruption and recovery packet | Player state, NBT, modded items, receipts, slots, claims, journals, persistence, recovery | Stable seeds, bounds, semantic diffs, isolation, restart, reconnect, repeat recovery, zero loss |
| `P007-TASK-009` | `CORE-REQ-006`, `CORE-REQ-013`, `CORE-REQ-014`, `CORE-REQ-015` | Run full isolated Forge server and at least two independent-client matrix | Tasks 005 and 006, `DEC-007` | Final integration, concurrency, lifecycle, and conservation packet | Shops, providers, escrow, Auction House, Bazaar, ATM, claims, packets, reload, recovery | Success, rejection, races, faults, disconnect, replay, restart, reconnect, exact zero unexplained delta |
| `P007-TASK-010` | `CORE-REQ-003`, `CORE-REQ-004`, `CORE-REQ-015`, `CORE-REQ-020` | Revalidate issue 22 and issue 25 final closure evidence | Tasks 005 through 007 and Phase 001 and 002 packets | Issue 22 accepted-fix packet and issue 25 local disposition packet | NeoForge screens and lifecycle, Forge catalogs, migration, readiness, reload, current supported state | Correct-line merge, exact local runtime, green checks, accepted issue 22 fix, supported-state issue 25 result, owner disposition when applicable |
| `P007-TASK-011` | `CORE-REQ-018` | Build and seal both local unpublished integrity packets | Tasks 006 through 010 | Exact JARs, metadata, inventories, manifests, SHA-256, SHA-512 | Build outputs and local evidence custody | Rebuild or select retained bytes, archive inspection, rehash, source binding, negative publication scan |
| `P007-TASK-012` | `CORE-REQ-019` | Execute complete convergence audit pass one | Tasks 001 through 011 | Timestamped pass-one packet | Issues, security, privacy, commands, persistence, database, integration, runtime, docs, dependencies, readiness | Every inventory row reruns or has exact unchanged proof, all observations classified |
| `P007-TASK-013` | `CORE-REQ-009`, `CORE-REQ-019` | Resolve pass-one observations and verified defects | Task 012 and `EXT-005` | Clean pass-one result or a new fully integrated candidate attempt | Issues, repairs, tests, docs, PRs, branches, evidence invalidation | Issue predates repair, correct-line merge, refreeze, all affected tasks rerun |
| `P007-TASK-014` | `CORE-REQ-019` | Execute complete convergence audit pass two without intervening material change | Clean Tasks 012 and 013 | Timestamped pass-two packet and clean audit pair | Same complete surface, commits, docs, dependencies, fixtures, harness, issues, and artifacts | Exact equality of coordinates and zero new repository-owned defect |
| `P007-TASK-015` | `CORE-REQ-017`, `CORE-REQ-020` | Confirm or complete issue closure and reconcile all repository tracking | Tasks 012 through 014 and `EXT-005` | Correct final repository and wiki state | Issues, PRs, checks, milestones, Project, branches, internal tags, wiki | Exact readback matches merged evidence and unpublished status |
| `P007-TASK-016` | `CORE-REQ-015`, `CORE-REQ-017`, `CORE-REQ-018`, `CORE-REQ-019`, `CORE-REQ-020` | Audit the complete Definition of Done, create final signed internal phase tags, and seal completion | Tasks 001 through 015 | Final completion packet or exact failed internal gate | Complete plan set and endpoint | Every requirement and exclusion passes, tag targets and signatures verify, no defect or publication exists |

## Candidate Freeze and Runtime Topology

- Forge uses an isolated Phase 007 worktree rooted from the latest approved `origin/1.20.1`; NeoForge uses a separate worktree rooted from the latest approved `origin/1.21.1`. Each documentation or metadata change merges through its own support-line pull request before freeze.
- Task 005 records repository, branch, commit, merge ancestry, Java, wrapper, plugins, mappings, dependencies, configs, fixture hashes, harness revision, world identity, profile identities, and expected JAR filename.
- The 64 GB workstation is the default host for one isolated dedicated server and at least two independent client JVMs with distinct game directories, accounts or test identities, configuration roots, and logs.
- Node1 may host only the temporary isolated dedicated server when the 96 GB environment improves capacity or repeatability. Clients remain independent. The candidate commit, JAR hash, configuration, fixture, network route, clocks, and evidence format remain pinned.
- An integrated server, duplicated window over shared client state, source-only reasoning, mocked packet route, or reused mutated world does not satisfy required runtime proof.
- Each fault or corruption case starts from a fresh hash-identified copy. The only authoritative fixture is never mutated. Every failure retains the complete world and FutureShops state needed for diagnosis.

## Exact Verification Order

### Forge 1.20.1 Candidate

At the frozen `origin/1.20.1` merge commit with Java 17:

1. Record commit, ancestry, clean state, Java, Gradle, mappings, Minecraft, Forge, dependency graph, `mod_version`, config set, fixture set, and expected `futureshops-3.0.0-beta.2.jar`.
2. Run every issue-specific and audit-specific focused regression, including issues 25, 32, 33, 34 and every rolling defect.
3. Run `bash ./gradlew test`.
4. Run `bash ./gradlew runData`; compare output with the frozen revision and classify any drift before continuing.
5. Run `bash ./gradlew runGameTestServer` when registered GameTests are applicable. An evidence-backed absence is not a substitute for runtime rows that require a real server or clients.
6. Run `bash ./gradlew verifyBetaReleaseIdentity` and `bash ./gradlew build`.
7. Run a bounded `bash ./gradlew runServer` dedicated-server smoke with registration, configs, persistence open, replay, commands, markets, claims, and clean shutdown.
8. Run a bounded `bash ./gradlew runClient` client smoke with assets, translations, screens, inputs, navigation, authoritative snapshots, and clean exit.
9. Run the complete server plus at least two independent-client matrix for Server Shops, Player Shops, economy providers, physical currency, ATM, escrow, Auction House, Bazaar, claims, commands, permissions, reload, lifecycle, delayed readiness, concurrent requests, replay, disconnect, restart, and reconnect.
10. Run issue 32 deterministic bounded corruption, fuzz, ownership isolation, receipt, slot, claim, restart, reconnect, repeated recovery, and conservation proof.
11. Rehearse backup, one complete matching restore, supported rollback boundaries, malformed and older state, partial write, invalid reload, provider failure, and last-known-good behavior.
12. Rerun the complete security, privacy, command, permission, persistence, database, integration, runtime, documentation, dependency, packaging, and readiness matrices.
13. Inspect the complete diff, tracked and untracked state, generated resources, configs, logs, caches, worlds, secrets, local paths, debug output, test hooks, and unrelated files.
14. Inspect JAR entries, expanded `META-INF/mods.toml`, resources, mixin metadata, dependencies, forbidden classes, filename, internal version, and byte length. Compute and verify SHA-256 and SHA-512.
15. Confirm required GitHub checks are green on the exact candidate commit.

### NeoForge 1.21.1 Candidate

At the frozen `origin/1.21.1` merge commit with Java 21:

1. Record commit, ancestry, clean state, Java, Gradle, mappings, Minecraft, NeoForge, ModDevGradle, dependency graph, `mod_version`, config set, and expected `futureshops-2.2.1.jar`.
2. Run issue 22 focused regressions and deterministic lifecycle checks for all 16 FutureShops screens. Confirm every constructor, open, resize, scale, render, navigation, close, disconnect, and reconnect path and the intended background behavior.
3. Run every independently applicable NeoForge audit regression.
4. Run `bash ./gradlew test`.
5. Run `bash ./gradlew runData` when branch providers are applicable and classify unexpected drift.
6. Run `bash ./gradlew runGameTestServer` when applicable.
7. Run `bash ./gradlew build`.
8. Run a bounded `bash ./gradlew runServer` dedicated-server smoke proving common initialization never loads client-only classes and registration, config, persistence, networking, and shutdown remain sound.
9. Run a bounded `bash ./gradlew runClient` smoke and exercise all affected screens with local visual capture, navigation, resize, scale, stale response, disconnect, reconnect, and exit behavior.
10. Rerun every applicable security, privacy, command, permission, persistence, integration, runtime, documentation, dependency, packaging, and readiness row against the exact line.
11. Inspect the complete diff and state for cross-line contamination, generated output, secrets, logs, caches, local paths, debug output, and unrelated files.
12. Inspect JAR entries, expanded `META-INF/neoforge.mods.toml`, loader and Minecraft ranges, resources, translations, client separation, dependencies, filename, internal version, and byte length. Compute and verify SHA-256 and SHA-512.
13. Confirm required GitHub checks are green on the exact candidate commit.

A failed, flaky, timed-out, stale, or skipped mandatory result is not a pass. Repair the repository-owned defect or local harness, refreeze when revisions change, and rerun the complete affected dependency chain.

## Issue Closure Matrix

| Record | Final closure proof |
|---|---|
| Issue 22 | Accepted correction, correct merge into `1.21.1`, all 16 local screen lifecycle results, client-only isolation, dedicated-server safety, focused and full tests, build, JAR inspection, green checks, verified `phase-001-neoforge-issue-22` tag, and closed issue readback |
| Issue 25 | Local beta-transition fixtures, local current supported-state fixtures, startup, restart, reload, catalog preservation, offer readiness, last-known-good behavior, and green checks. Any supported-state defect is repaired and merged. A remaining unsupported beta state closes only with an owner-approved compatibility disposition after proving no supported-state defect remains |
| Issue 32 | Local deterministic bounded corruption and fuzz corpus, ownership isolation, modded item and unrelated player-data sentinels, receipts, delivery slots, claims, restart, reconnect, repeated non-destructive recovery, zero loss or duplication, and green checks |
| Issue 33 | Correct Forge merge and exact bounded selection, preview, shared price and stock, skip, explicit replace, preservation, atomic rollback, server, client, JAR, green checks, and verified `phase-003-forge-issue-33` tag |
| Issue 34 | Correct Forge merge, finite and infinite stock, isolated dedicated server, at least two independent clients, concurrent success and rejection, provider failure, full inventory, disconnect, replay, restart, reconnect, claims, exact conservation, and green checks |
| Rolling audit issue | Canonical record before repair, correct-line merged fix, focused regression, all invalidated evidence rerun, green checks, and exact final revision readback |

An issue closed by an earlier phase remains closed only if final exact-candidate proof confirms its acceptance and no later change invalidated it. Task 015 closes any remaining eligible issue only after both clean audit passes and reads back the final state. If a previously closed issue lacks current evidence, reopen or correct its state under normal repository governance and resume from the earliest invalidated task.

## Repeated Convergence Audit

Each pass independently audits both exact candidate revisions and the complete closure surface:

1. Issues, comments, private advisories, reviews, pull requests, commits, checks, branches, internal phase tags, milestones, Project items, Actions failures, dependency updates, alerts, and evidence links.
2. Security and privacy across commands, packets, permissions, paths, codecs, NBT, JSON, TOML, logs, player data, dependencies, JAR contents, replay, duplication, and confidential routing.
3. Every command and permission leaf for console, supported command block, authorized and unauthorized player, malformed input, offline target, stale confirmation, repeated request, recovery state, output, and audit context.
4. Persistence and database inventory, schemas, migrations, atomicity, journals, checkpoints, ledgers, custody, claims, receipts, player data, configs, catalogs, backup, restore, corruption, fuzz, restart, reconnect, recovery, idempotency, and conservation.
5. Backend integration for networking, readiness, Server Shops, Player Shops, providers, physical currency, escrow, Auction House, Bazaar, ATM, claims, reload, lifecycle, scheduler, failure, restart, reconnect, and multiple clients.
6. Local runtime and issue-specific matrices for issues 22, 25, 32, 33, and 34.
7. Documentation, links, commands, config keys, schemas, versions, compatibility, recovery, known limits, wiki state, and prepared-unpublished wording.
8. Dependency graphs, PR 28 disposition, pinned platform boundaries, workflow results, generated output, full diffs, JAR contents, manifests, checksums, and release-readiness boundaries.
9. Every observation as repaired defect, existing duplicate, confidential finding, excluded future work, or evidence-backed disproven concern. No row remains unclassified.
10. Candidate commits, documentation commits, dependency identities, fixture hashes, harness versions, JAR byte lengths and hashes, timestamps, audit inventory version, and new-defect count.

Pass one is clean only when no verified repository-owned defect or unclassified observation remains. Pass two starts without any intervening source, documentation, metadata, dependency, fixture, harness, config, issue-scope, or artifact change. Both passes must record identical candidate commits and artifact SHA-256 and SHA-512 values. Any material change invalidates the pair and returns execution to Task 003, 004, or 005 according to its earliest impact.

## Architecture and Implementation Boundaries

- Forge and NeoForge retain separate branches, worktrees, Java toolchains, loader APIs, histories, metadata, runtime roots, and artifacts.
- The logical server remains authoritative for balances, stock, items, listings, orders, custody, claims, permissions, request outcomes, and module lifecycle.
- Every value mutation retains checked integer minor units, stable request UUID and fingerprint, one durable terminal outcome, journal and custody lineage, idempotent replay, exact compensation or claim, and conservation.
- Claims remain durable and accessible while mutation modules are frozen, draining, disabled, recovering, restarting, or unavailable.
- Persistent and configuration state uses complete matching isolated cohorts, versioned schemas, last-known-good snapshots, atomic boundaries, and non-destructive recovery. Compatible unknown fields and unrelated player data remain unchanged.
- Test-only fault controls cannot be reachable in the production JAR. Runtime evidence cannot contain credentials, raw private player data, exploit-enabling details, or unrelated local paths.
- Documentation and metadata merge before freeze because they are revision-bearing candidate inputs.
- Repository issue, Project, wiki, and internal tag updates are evidence retention. They do not publish product artifacts or create a product release.

## Failure, Recovery, and Edge Cases

| Scenario | Detection | Required behavior | Recovery or rollback | Regression proof |
|---|---|---|---|---|
| Support head changes after freeze | Remote commit or ancestry differs from manifest | Stop all downstream candidate claims | Classify change, refreeze, and rerun affected tasks | Hash equality at every packet boundary |
| Documentation or metadata changes late | Candidate commit differs even when runtime code is unchanged | Invalidate revision-bound proof | Merge correctly, refreeze, rebuild, and rerun | Both final passes use the new identical commit |
| Cross-line contamination | Diff or ancestry contains wrong loader, API, metadata, or commit | Reject the candidate | Correct through a reviewed line-specific PR | Ancestry, diff, build, runtime, and JAR proof |
| Local runtime capacity or process fails | Server or independent clients do not start, isolate, or complete within bounds | Fail the affected row | Repair or reschedule the harness, or move only the isolated server to node1 | Full same-fidelity rerun with pinned identities |
| Corruption corpus is unbounded or nondeterministic | Seed replay differs or bound is exceeded | Reject corpus result | Repair generator, minimize, and rerun from clean controls | Stable seeds, hashes, bounds, and repeat result |
| Ownership or unrelated state changes | Semantic diff crosses FutureShops-owned boundary | Freeze mutation and preserve cohort | File before repair, fix exact owner logic, restore one complete copy | Sentinels, receipts, slot proof, repeat recovery |
| Conservation mismatch | Any unexplained money, item, stock, custody, or claim delta | Fail candidate and stop value mutation | Preserve full lineage, restore one complete matching copy, file issue before repair | Repeated zero-delta workflow and global reports |
| Partial or failed write | Persistent and in-memory state diverge or acknowledgement precedes durability | Fail closed and retain prior state or custody | Correct environment or repair writer, then recover under original identity | Fault before and after every durability boundary |
| Server or client smoke hangs | Readiness or shutdown bound expires | Mark stage failed and preserve logs and state | Stop isolated process safely, diagnose, and rerun | Bounded clean startup, workflow, and shutdown |
| CI result targets another commit | Check suite SHA differs from candidate | Ignore the unrelated result | Run or await checks on exact commit | Required-check SHA readback |
| Candidate metadata differs | Filename, internal version, loader, Minecraft range, or manifest differs | Reject artifact | Correct on line-specific branch, merge, refreeze, rebuild | Archive and expanded metadata inspection |
| Candidate bytes change | Byte length or digest differs | Quarantine changed local copy | Rebuild or restore retained bytes, regenerate packet, rerun consumers | SHA-256 and SHA-512 rehash |
| New audit defect appears | Reproduction proves repository ownership | Reopen rolling scope | Create issue first, repair correct line, merge, refreeze, rerun | Failing regression, repair, merge, two new clean passes |
| Repository authority changes | Identity, remote, permission, check, Project, wiki, or tag readback fails | Stop remote mutations | Restore EnVisione authority and repeat preflight | Exact identity and remote-state readback |
| Prohibited release action appears | Release, upload, product tag, platform file, stable claim, or announcement exists from phase work | Stop completion and preserve audit | Request owner direction before any further release-side action | Clean forbidden-action audit after resolution |

## Verification Matrix

| Requirement or task | Static or unit | Integration | Real workflow or runtime | Negative and recovery | Evidence artifact |
|---|---|---|---|---|---|
| `CORE-REQ-015`, Task 006 | Focused tests, full `test`, data diff, GameTests, build, audit inventories | Complete Forge subsystem matrices | Dedicated server, client, at least two clients, restart, reconnect, reload | Corruption, fuzz, rollback, provider failure, replay, full inventory, invalid reload | Forge exact-revision packet |
| `CORE-REQ-015`, Task 007 | Issue 22 regressions, full `test`, applicable data and GameTests, build | NeoForge client/common boundary and screen navigation | Dedicated server, client, all 16 screen lifecycles, reconnect | Stale response, resize, scale, disconnect, missing client boundary | NeoForge exact-revision packet |
| Task 008 | Codec, NBT, receipt, slot, ownership, schema, and fuzz properties | Player, claim, transaction, journal, persistence lifecycle | Dedicated server, independent clients, login, logout, restart, reconnect | Corruption, truncation, partial write, bad checksum, ambiguous ownership, repeated recovery | Deterministic issue 32 packet |
| Task 009 | Packet, request, stock, provider, market, claim, and conservation properties | Full component combinations | Isolated dedicated server and at least two independent clients | Races, failures, disconnect, replay, restart, reconnect, zero-delta check | Full integration packet |
| Task 010 | Issue-specific focused tests and evidence checklist | Correct-line merge and lifecycle reconciliation | Local screen, beta-transition, current-state, restart, and reload workflows | Supported-state defect search and compatibility-boundary proof | Issue 22 and 25 closure packets |
| `CORE-REQ-017`, Tasks 003 and 004 | Link, terminology, command, config, path, schema, and version checks | Source-to-doc and branch-to-wiki comparison | Operator procedure rehearsal | Stale, unsafe, contradictory, or prematurely released wording | Documentation packet and merged PRs |
| `CORE-REQ-018`, Task 011 | Metadata, archive, dependency, secret, and manifest inspection | Built bytes bind to source and evidence | Local staging and rehash | Wrong version, unexpected entry, hash mismatch, publication-side effect | Two JAR packets and checksums |
| `CORE-REQ-019`, Tasks 012 through 014 | Complete inventory and source review | Cross-audit traceability and unchanged-coordinate comparison | All required local runtime evidence | New defect or unclassified row invalidates pass | Two complete clean audit packets |
| `CORE-REQ-020`, Task 015 | Issue evidence and branch/tag checklist | PR, check, Project, milestone, and wiki consistency | Exact final remote readback | Reopened, stale, or incorrectly closed record | Final repository-state packet |
| `EXT-005`, Tasks 001 and 015 | Identity, remote, branch, issue, PR, check, and tag inspection | Project and wiki synchronization | Final remote readback | Authority mismatch stops remote mutation | GitHub preflight and final readback |
| Task 016 | Master and phase traceability audit | All completion packets joined to endpoint | No new runtime beyond the already required matrices | Any missing requirement, exclusion, tag, defect, or publication proof fails | Final plan-wide completion packet |

## Documentation, Operations, and Release

- Update `README.md` and `DOCUMENTATION.md` for exact candidate versions, supported branches, prerequisites, build and run commands, verified behavior, configuration, compatibility, recovery, known limits, and prepared-unpublished status.
- Update `docs/README.md` navigation and every focused guide whose behavior, schema, command, config, migration, recovery, audit, verification, or compatibility contract changed.
- Keep recovery procedures non-destructive. They require the correct stop state, one complete matching backup cohort, preserved evidence, expected validation, refusal conditions, and post-restore checks.
- Candidate notes distinguish previously available versions from local `3.0.0-beta.2` and `2.2.1` candidates and make no download or availability claim.
- GitHub issue, pull request, milestone, Project, and wiki text reflects only merged and verified behavior. Wiki changes derive from merged tracked documentation.
- After Task 016 proves the final endpoint, create and push signed annotated internal tags `phase-007-forge-candidate-readiness` and `phase-007-neoforge-candidate-readiness` on the exact verified support-line candidate commits. Verify target, signature, and remote presence. These are internal integration markers, not product release tags.
- Local candidate packets remain outside tracked source and are not uploaded. A later release requires separate explicit owner authorization and fresh validation of the exact retained bytes.

## Risks and Evidence Invalidation

| Risk | Prevention | Detection | Recovery | Evidence invalidated | Reverification |
|---|---|---|---|---|---|
| Stale revision proof | Freeze only after all merges and stamp every packet | Commit or ancestry mismatch | Refreeze after intentional change | All downstream proof for affected line | Tasks 005 through 016 as affected |
| Cross-line merge or copied API | Separate worktrees and ancestry review | Wrong loader, metadata, or commit in diff or JAR | Correct line-specific PR | Affected line and shared audit rows | Full line ladder and both audits |
| Documentation advertises an unpublished candidate | Prepared-unpublished vocabulary and wiki readback | Download, release, stable, or availability claim | Correct and merge before refreeze | Docs, revision, artifact binding, audits | Tasks 003 or 004 onward |
| Dependency graph drifts | Freeze graph and platform pins | Resolution, lock, PR, alert, or JAR difference | Classify and integrate through issue workflow | Security, build, packaging, runtime, hashes | Full affected line and audits |
| Runtime fixtures contaminate each other | Immutable seeds and fresh copies | Fixture hash or unexpected state | Preserve failed copy and recreate from seed | Runtime, recovery, conservation | Complete affected matrix |
| Sensitive state enters evidence | Synthetic fixtures, minimization, sanitization | Privacy scan or review | Remove unsafe copy and follow security response | Evidence, issue, docs, packet | Sanitized recapture and privacy audit |
| Issue 25 disposition hides a supported defect | Separate beta-transition and current supported-state matrices | Supported fixture reproduces defect | File and repair before disposition | Issue 25, Forge proof, audits | Focused through full Forge ladder |
| Issue 32 corpus loses ownership isolation | Explicit FutureShops-owned map and sentinels | Unrelated semantic diff | Repair ownership logic and recreate corpus | Persistence, recovery, issue 32, audits | Tasks 008, 012 through 016 |
| Multiple-client test shares state | Independent profiles and process manifests | Shared game dir, identity, cache, or session | Recreate independent clients | Integration, concurrency, issue 34, audits | Task 009 and dependent tasks |
| Hidden rolling defect | Complete inventories and two passes | New reproducible repository-owned observation | Issue-before-repair loop | Both audits and affected downstream proof | Refreeze and two new passes |
| Candidate artifact changes after pass one | Read-only custody and dual hashes | Digest or byte-length mismatch | Rebuild or restore and inspect | Artifact packet and clean pair | Tasks 011 through 016 |
| Required check or issue changes after reconciliation | Final remote readback | New failure, reopen, review, or Project drift | Reconcile evidence-backed state | Requirements 017 and 020 | Tasks 015 and 016 |
| Product release boundary is crossed | Explicit local-only custody and forbidden-action audit | Release, upload, product tag, platform file, or announcement | Stop and request owner direction | Final endpoint | Repeat boundary audit after resolution |

## Phase Completion Packet

The completion packet is retained outside the protected plan set and contains:

1. Validated master and index identity, unchanged legacy-plan hashes, and final traceability for `CORE-REQ-001` through `CORE-REQ-020`, `CORE-PHASE-000` through `CORE-PHASE-007`, `SRC-001` through `SRC-014`, `DEC-001` through `DEC-007`, `EXT-001` through `EXT-005`, `FUT-001` through `FUT-005`, and `NG-001` through `NG-008`.
2. Exact Forge and NeoForge support heads, Phase 007 work branches, pull request heads, merge commits, ancestry, clean-tree results, metadata, required checks, review results, and branch readbacks.
3. Every required upstream internal phase tag, including `phase-001-neoforge-issue-22`, `phase-003-forge-issue-33`, `phase-004-security-command-audit`, `phase-005-persistence-recovery`, and `phase-006-backend-integration`, with its object, exact target, verified EnVisione signature, and remote presence.
4. Complete focused, unit, data, GameTest, build, server, client, multiple-client, restart, reconnect, reload, rollback, corruption, fuzz, recovery, fault, security, privacy, command, persistence, database, integration, dependency, diff, and packaging results.
5. Issue 22 accepted-fix and merged `1.21.1` proof; issue 25 local beta-transition, current-state, supported-state, and owner disposition proof when applicable; issue 32 deterministic local corpus; issue 33 bounded-workflow proof; and issue 34 local multiple-client proof.
6. Merged documentation commits, source-to-document matrix, link and terminology results, literal checks, complete documentation diff, operator procedure rehearsal, and wiki readback.
7. Final closure and tracking records for issues 22, 25, 32, 33, 34, every rolling issue, related pull requests, milestones, Project items, reviews, and required checks.
8. Exact local `futureshops-3.0.0-beta.2.jar` and `futureshops-2.2.1.jar`, archive inventories, expanded metadata, byte lengths, source revision manifests, SHA-256, SHA-512, and successful checksum verification.
9. Two timestamped complete convergence audits proving identical source, documentation, dependency, fixture, harness, issue-scope, and artifact coordinates and zero new repository-owned defect.
10. Final signed annotated tags `phase-007-forge-candidate-readiness` and `phase-007-neoforge-candidate-readiness`, exact targets, verified EnVisione signatures, and remote presence.
11. A final negative scan for credentials, private raw logs, sensitive NBT, absolute local paths, caches, generated worlds, committed build output, unrelated edits, GitHub Releases, product release tags, artifact uploads, platform publication, stable claims, and announcements.
12. Final endpoint verdict. Completion is permitted only when every item passes and no known repository-owned defect remains.

## Final Plan-Wide Definition of Done Audit

The final auditor must answer yes with exact evidence to every question:

- Are all twenty canonical requirements satisfied at their required fidelity?
- Have all eight contiguous phase exits passed with correct branch, pull request, merge, check, and required internal tag evidence?
- Are issues 22, 25, 32, 33, 34, and all rolling findings correctly repaired or dispositioned, merged where required, verified, and closed?
- Does issue 22 have accepted-fix and exact merged `1.21.1` proof?
- Does issue 25 have local beta-transition and current supported-state proof, with an owner-approved compatibility disposition only when no supported-state defect remains?
- Does issue 32 have deterministic bounded corruption, fuzz, ownership isolation, unrelated-state preservation, and repeated non-destructive recovery proof?
- Did the full isolated Forge server and at least two independent clients prove issues 34, subsystem integration, failure behavior, restart, reconnect, claims, replay, and conservation?
- Do both support lines preserve their pinned loader, Minecraft, Java, build, mapping, dependency, schema, and compatibility boundaries and use exactly `3.0.0-beta.2` and `2.2.1`?
- Did complete issue, security, privacy, command, permission, persistence, database, integration, runtime, documentation, dependency, packaging, and release-readiness audits pass twice at unchanged revisions and artifact hashes?
- Did both passes find zero new repository-owned defect and zero unclassified observation?
- Do both exact JARs, internal metadata, source manifests, SHA-256, SHA-512, verification packets, branch heads, and internal phase tags agree?
- Do documentation, issues, pull requests, checks, milestones, Project items, wiki, branches, and tags reflect merged candidate state and prepared-unpublished status?
- Are legacy plans unchanged and all optional, future, and non-goal boundaries preserved?
- Has no prohibited public release action occurred?

Any no, unknown, stale, mismatched, skipped mandatory, or lower-fidelity result fails the endpoint. Preserve evidence, repair the repository or local harness, and resume at the earliest invalidated task. There is no partial, alternative, or substitute completion state.

## Next Transition

There is no later implementation phase. After `P007-TASK-016` passes, report final plan-wide completion with exact Forge and NeoForge support commits, pull requests, merge commits, required checks, internal phase tags, JAR filenames, SHA-256 and SHA-512 values, issue states, clean-audit pair, known-defect count of zero, and prepared-unpublished status.

Retain the candidate packets for a later separately authorized release decision. Do not create a GitHub Release, product release tag, artifact upload, CurseForge or Modrinth file, stable declaration, download claim, or announcement under this phase.
