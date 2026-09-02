# Phase 001 Execution Plan

> **Plan ID:** PLAN-PHASE-001
> **Phase ID:** CORE-PHASE-001
> **Owner:** NeoForge client integration
> **Classification:** MANDATORY
> **Master plan:** [plan.md](../plan.md)
> **Phase sequence:** 001 of 007

## Purpose and Ownership

This phase independently verifies and integrates the owner-accepted correction for issue 22. The confirmed defect is specific to the NeoForge 1.21.1 client lifecycle: Minecraft runs the vanilla background pass after a FutureShops screen draws its custom content, blurring that content while widgets rendered later remain sharp. Commit `bfba91f7b0c51b03d07117c4f1851c38a98f6186` gives all 16 FutureShops screens one background policy intended to suppress only that unwanted pass.

This file owns the detailed execution blueprint for `CORE-REQ-003` and the phase-local evidence needed by `CORE-REQ-015`, `CORE-REQ-016`, `CORE-REQ-017`, and `CORE-REQ-020`. The master plan owns product scope, requirement meaning, the frozen phase topology, and final completion. `EXT-001` is a resolved historical traceability record, not an active dependency, acceptance gate, Windows test requirement, or blocker. Owner acceptance already exists. Phase closure now depends on fresh repository-controlled verification, correct integration into `1.21.1`, a signed phase tag, and issue 22 closure with exact evidence.

## Evidence-Based Entry State

| Evidence class | Area | Finding | Source or command | Freshness condition |
|---|---|---|---|---|
| VERIFIED | Root cause and owner disposition | The owner accepts the confirmed root cause and correction. Minecraft 1.21.1 performs the vanilla background pass after FutureShops custom rendering, so custom content is blurred while later widgets remain sharp | SRC-001, SRC-004, DEC-004, and the issue 22 record | Invalid only if fresh execution evidence contradicts the recorded lifecycle or shows a mandatory regression |
| VERIFIED | Candidate ancestry | Candidate commit `bfba91f7b0c51b03d07117c4f1851c38a98f6186` is retained on `envy/issue_22_neoforge` and derives from the recorded NeoForge intake head `247d8f6842bfa1f586e5b18a9aab67cabd3db89f` | `git rev-parse`, `git merge-base --is-ancestor`, and SRC-011 | Invalid if the branch is rewritten, the support head moves, or execution uses a different change |
| OBSERVED | Candidate implementation | The candidate introduces `AbstractShopScreen`, routes all 16 concrete `ShopScreenMarker` screens through it, overrides `renderBackground`, adds `ShopScreenBackgroundPolicyTest`, and changes the NeoForge product version from `2.2.0` to `2.2.1` | Diff from `247d8f6842bfa1f586e5b18a9aab67cabd3db89f` to `bfba91f7b0c51b03d07117c4f1851c38a98f6186` | Invalid if the candidate diff, concrete screen inventory, marker policy, version source, or upstream screen API changes |
| VERIFIED | Historical automated evidence | At `bfba91f7b0c51b03d07117c4f1851c38a98f6186`, the full unit suite and build passed under Java 21 | SRC-004 and SRC-011 | Historical only. Any code, test, build, dependency, or base-branch change requires fresh reruns |
| VERIFIED | Historical client and artifact evidence | A virtual-display client reached the main menu and the resulting artifact had SHA-256 `a2e0d924d5523f3bab1253158894b2d77c1ef47467f767c14e13a4853807cfdb` | SRC-004 and SRC-011 | Historical and insufficient for closure because no affected screen lifecycle was exercised. A rebuilt or merged artifact requires a new identity |
| OBSERVED | Toolchain and metadata | The NeoForge line pins Minecraft `1.21.1`, NeoForge `21.1.233`, ModDevGradle `2.0.141`, Java 21, and FutureShops `2.2.0`; the candidate changes only the product version among those boundaries | `gradle.properties`, `build.gradle`, generated NeoForge metadata, and SRC-009 | Invalid if build metadata, dependency resolution, wrapper, or support versions change |
| AVAILABLE | Historical prerequisite | `EXT-001` records the former issue 22 acceptance gate, now resolved by explicit owner acceptance | EXT-001 and DEC-004 | Retain for provenance only. It never becomes an active reporter or Windows gate again without an explicit Plan Creator revision |

Historical results are inputs, not substitutes for this phase's fresh evidence. `CORE-PHASE-000` must first provide the current issue packet, exact support and candidate heads, branch and pull request state, and support-line isolation proof.

## Scope Boundaries

### Included Scope

- `CORE-REQ-003`: independently inspect the accepted correction, rerun its focused screen-policy tests, full Java 21 suite, build, JAR inspection, and an actual client lifecycle for all 16 affected screens; integrate only into `1.21.1`; verify the merged revision; sign the phase tag; and close issue 22 with owner-accepted evidence.
- The phase-local subset of `CORE-REQ-015`: exact-revision focused, complete-suite, build, client-runtime, artifact, and post-merge verification.
- The phase-local subset of `CORE-REQ-016`: branch ancestry, pinned toolchain, loader and Minecraft compatibility, version metadata, and strict separation from Forge `1.20.1`.
- The phase-local subset of `CORE-REQ-017` and `CORE-REQ-020`: accurate NeoForge documentation and GitHub evidence, the authorized pull request into `1.21.1`, issue 22 closure, and the signed phase tag.

### Explicit Exclusions

- Forge `1.20.1`, Forge `3.0.0-beta.2`, issues 25, 32, 33, and 34, and every Forge source, resource, build, and documentation change are excluded. `NG-005` prohibits cross-line transfer without independent scope.
- A general screen redesign, global blur option, shader or post-processing change, unrelated UI cleanup, protocol change, persistence migration, dependency upgrade, and platform upgrade are excluded.
- Full security, command, persistence, backend integration, and final candidate audits belong to later phases. This phase still rejects a finding that makes the issue 22 change unsafe or incompatible.
- Reporter acceptance, a Windows external environment, and any unresolved `EXT-001` gate are excluded by DEC-004. No external retest is required to close this phase or issue 22.
- `FUT-001` and `FUT-002`: no CurseForge, Modrinth, GitHub Release, public release tag, announcement, upload, or stable designation is authorized. The phase tag is an internal signed integration marker, not a public product release.
- A client launch that reaches only the main menu, source scanning alone, compilation alone, mocked screenshots, or testing fewer than all 16 affected screens cannot satisfy runtime closure.

## Phase Contract

### CORE-PHASE-001 — Verify and integrate the NeoForge issue 22 correction

**Objective:** Independently prove that the accepted background policy fixes the confirmed NeoForge 1.21.1 lifecycle defect on all 16 FutureShops screens without rendering or navigation regressions, merge it only into `1.21.1` as `2.2.1`, verify the exact merged revision, create the signed phase tag, and close issue 22 with the owner-accepted evidence.
**Owner:** NeoForge client integration
**Dependencies:** CORE-PHASE-000
**Canonical requirements:** CORE-REQ-003
**Documentation and release impact:** Reconcile NeoForge `2.2.1` metadata, the established README or porting notes changed by the candidate, issue 22, the phase pull request, project and milestone state, and the signed phase tag. Build evidence artifacts only. Do not publish a product release.
**Next transition:** CORE-PHASE-002

**Entry criteria**

- `CORE-PHASE-000` has completed its governance and baseline exit, including every current issue 22 comment, the resolved historical status of `EXT-001`, and current GitHub state.
- The exact latest approved `origin/1.21.1` head, candidate commit, work branch, dirty-worktree boundary, open pull request state, required checks, signing configuration, and version sources are recorded.
- Commit `bfba91f7b0c51b03d07117c4f1851c38a98f6186` remains recoverable and its diff can be applied or rebuilt from the current approved `1.21.1` head without touching `1.20.1`.
- Java 21 and the checked-in wrapper resolve the pinned NeoForge toolchain without platform upgrades.
- A disposable NeoForge client fixture can reach every one of the 16 affected screens with the permissions, server state, catalog state, history state, and modal inputs each route needs.
- EnVisione GitHub access can create the authorized phase pull request into `1.21.1`, inspect checks and private-review capability, merge through GitHub, verify the remote branch, push the signed tag, and close issue 22.

**Implementation scope**

- CORE-REQ-003 requires inspection of the accepted implementation and exact pinned lifecycle sources. Retain it only if fresh evidence confirms one bounded policy suppresses the unwanted vanilla pass without removing FutureShops custom backdrops or affecting unrelated screens.
- CORE-REQ-003 requires all 16 concrete screens to remain within the deterministic background-policy inventory and makes omission detectable by the focused test.
- CORE-REQ-003 requires preservation of widget, tooltip, text, item, focus, narration, mouse, keyboard, resize, parent-navigation, close, and reopen behavior in an actual client.
- CORE-REQ-003 and CORE-REQ-016 require exactly FutureShops `2.2.1` without changing Minecraft, NeoForge, ModDevGradle, Java, mappings, dependencies, protocol, schemas, identifiers, or the Forge line.
- CORE-REQ-003 requires integration only through one authorized pull request whose base is `1.21.1`. After GitHub merge, fetch and independently verify `origin/1.21.1`, run the required merged-revision checks, create and push the signed annotated phase tag `phase-001-neoforge-issue-22` on that merge commit, and close issue 22.

**Execution order**

1. `P001-TASK-001` advances CORE-REQ-003 by refreshing the exact branch, issue, candidate, environment, signing, and GitHub baseline.
2. `P001-TASK-002` advances CORE-REQ-003 by independently inspecting the confirmed 1.21.1 lifecycle and comparing it with the candidate policy.
3. `P001-TASK-003` advances CORE-REQ-003 by inventorying all 16 affected screens and auditing the candidate boundary and existing evidence.
4. `P001-TASK-004` advances CORE-REQ-003 by rerunning and strengthening the focused policy regression as required, including baseline failure and candidate pass.
5. `P001-TASK-005` advances CORE-REQ-003 by retaining the exact accepted commit unchanged and confirming its exact `2.2.1` metadata and affected documentation.
6. `P001-TASK-006` advances CORE-REQ-003 by running focused tests, the full suite, build, JAR inspection, and every affected screen lifecycle in an actual client.
7. `P001-TASK-007` advances CORE-REQ-003 by opening or updating the authorized phase pull request only into `1.21.1`, passing required checks and private review if the optional capability exists, then merging through GitHub.
8. `P001-TASK-008` advances CORE-REQ-003 by verifying the exact merged `origin/1.21.1` revision, rerunning required checks and client coverage, creating and pushing the signed phase tag, recording owner acceptance, and closing issue 22.
9. `P001-TASK-009` completes CORE-REQ-003 by assembling the completion packet and handing the exact integrated state to `CORE-PHASE-002`.

**Required evidence**

- Fresh inspection of the exact pinned Minecraft and NeoForge screen lifecycle, the FutureShops render order, and the bounded effect of `AbstractShopScreen.renderBackground`.
- Focused `ShopScreenBackgroundPolicyTest` execution that fails against the affected baseline and passes at the final branch and merged revisions, plus proof that it executes in the full suite.
- Java 21 results for the complete unit suite and `build` at the final phase branch and exact merged `1.21.1` revision.
- A real client lifecycle record for all 16 screens named below. Every record includes how the screen was reached, open and first render, sharp custom content, intentional backdrop behavior, widget and tooltip rendering, applicable input, resize or reinitialization, navigation or close, reopen, client log result, source revision, and artifact hash.
- Negative client controls showing that vanilla title, pause, inventory, and other non-FutureShops screens do not inherit the FutureShops policy.
- JAR filename, SHA-256, archive listing, internal NeoForge metadata, FutureShops `2.2.1`, Minecraft and loader ranges, required classes and resources, dependency inspection, and exclusion of Forge-only code, secrets, caches, logs, test output, and local paths.
- The authorized pull request base and head, required checks, private review result or recorded capability absence, merge commit, fetched `origin/1.21.1` containment, signed annotated phase tag and verified signature, and issue 22 closure comment.

**Exit criteria**

- Fresh inspection agrees with the confirmed root cause and shows that the exact accepted commit is the smallest correct NeoForge client policy. Any contradiction blocks integration and returns `PLAN_REVISION_REQUIRED` without changing the accepted commit.
- The focused policy test, complete Java 21 unit suite, build, JAR inspection, all 16 actual screen lifecycles, negative controls, and post-merge reruns pass at the exact required revisions.
- The implementation has no Forge `1.20.1`, common server, networking, persistence, registry, global rendering, protocol, schema, dependency, or pinned-platform effect.
- The artifact and generated metadata identify exactly FutureShops `2.2.1` for Minecraft `1.21.1` and the pinned NeoForge line.
- GitHub confirms the phase pull request targeted only `1.21.1`, all required checks passed, all evidence-backed review findings were resolved, and the merge completed through GitHub.
- Fresh remote inspection proves `origin/1.21.1` contains the merge. The signed annotated tag `phase-001-neoforge-issue-22` points to that exact merge commit and its signature verifies as EnVisione.
- Issue 22 contains the confirmed cause, explicit owner acceptance, candidate and merged revisions, focused, full-suite, build, all-screen client, artifact, pull request, check, review, merge, remote, and tag evidence, then is closed.
- No reporter acceptance or Windows external evidence is requested or required. `EXT-001` remains resolved historical traceability only.
- No known mandatory phase-owned defect remains.

## Inputs and Upstream Contracts

| Input or contract | Provider | Required state | Validation | Failure behavior |
|---|---|---|---|---|
| Phase 000 baseline packet | `CORE-PHASE-000` | Current issue 22 comments, exact branch heads, candidate identity, GitHub state, signing state, and resolved `EXT-001` classification are recorded | Compare packet revisions and remote state before mutation | Stop until stale baseline facts are refreshed; never restore a reporter gate |
| Approved NeoForge support head | `origin/1.21.1` | Exact approved revision with Minecraft 1.21.1, NeoForge 21.1.233, ModDevGradle 2.0.141, and Java 21 | Git ancestry and metadata inspection | Rebuild the phase branch from the current approved NeoForge head; do not use Forge or stack on another phase branch |
| Preserved candidate | SRC-011 | Commit `bfba91f7b0c51b03d07117c4f1851c38a98f6186`, its diff, tests, docs, version change, and historical artifact evidence remain inspectable | Object existence, parent, diff, test, documentation, and artifact record | Preserve the object. If the base moved, apply only this exact commit through the line-specific pull request and prove patch equality |
| Confirmed defect contract | SRC-001 and SRC-004 | Root cause and owner acceptance are locked, and every current issue comment is retained | Reconcile the phase packet with issue 22 | A material contradiction stops with `PLAN_REVISION_REQUIRED`; an ordinary verification finding stays inside this phase |
| Screen lifecycle contract | Pinned Minecraft and NeoForge dependencies | Exact lifecycle behavior can be inspected for the pinned runtime | Source or bytecode inspection plus affected client traces | If fresh evidence contradicts the accepted mechanism, preserve it, do not integrate or alter the accepted commit, and return `PLAN_REVISION_REQUIRED` |
| GitHub and signing authority | EXT-005 and repository governance | EnVisione identity, correct remote, merge authority, and registered signing key are available | Authentication, remote, signing configuration, and test signature checks | Stop remote integration or tagging at the failed gate without weakening local evidence |

## Outputs and Downstream Contracts

| Output or contract | Consumer | Guaranteed state | Compatibility or versioning | Evidence |
|---|---|---|---|---|
| Merged NeoForge correction | `CORE-PHASE-002`, `CORE-PHASE-007`, and NeoForge users | Issue 22 is fixed by one verified policy covering all 16 screens | Exactly FutureShops 2.2.1 on Minecraft 1.21.1 and NeoForge 21.1.233; no Forge change | Merge, remote containment, test, build, client, JAR, and issue evidence |
| Screen background policy contract | Future NeoForge screen work | Every concrete FutureShops marker screen participates in a tested policy that suppresses only the unwanted vanilla pass | No global option, cross-loader API, protocol, or persisted-schema change | Focused policy test, inventory, lifecycle inspection, and all-screen client matrix |
| Closed issue 22 packet | `CORE-PHASE-007` and repository closure governance | Owner acceptance and exact repository-controlled proof support closure | No reporter, Windows, or external acceptance dependency | Closure comment and retained phase packet |
| Signed phase tag | Sequential phase governance | One verified EnVisione signature marks the exact merged phase commit | `phase-001-neoforge-issue-22` is an integration tag, not a public release tag | Tag object, target commit, remote presence, and signature verification |
| Documentation delta | Maintainers and final documentation audit | NeoForge 2.2.1 and issue 22 behavior are described accurately without claiming publication | Established file layout and normal documentation language remain | Reviewed documentation diff on the merged revision |

## Work Packages

| Task ID | Requirement IDs | Work | Inputs and dependencies | Outputs | Affected components or interfaces | Verification |
|---|---|---|---|---|---|---|
| `P001-TASK-001` | CORE-REQ-003, CORE-REQ-016 | Refresh repository and GitHub state, verify EnVisione identity and signing, record `origin/1.21.1`, candidate and work branch heads, issue 22, pull requests, checks, dirty files, version sources, and resolved `EXT-001` history | `CORE-PHASE-000`, SRC-004, SRC-009, SRC-011, EXT-005 | Revision-bound phase baseline and drift decision | Git refs, issue packet, worktree, NeoForge metadata, signing configuration | Read-only Git and GitHub record, ancestry proof, metadata comparison, signature readiness, and explicit no-Forge boundary |
| `P001-TASK-002` | CORE-REQ-003 | Inspect exact pinned `Screen` lifecycle sources or bytecode and compare the accepted call order with baseline and candidate behavior. Confirm which pass blurs custom content and why later widgets remain sharp | `P001-TASK-001`, pinned dependencies | Fresh lifecycle inspection and candidate fit decision | Minecraft screen lifecycle, FutureShops custom render methods, `renderBackground` policy | Source or bytecode citations plus deterministic baseline and candidate trace |
| `P001-TASK-003` | CORE-REQ-003, CORE-REQ-016 | Inventory the 16 concrete screens, inheritance, custom backgrounds, parent routes, modal behavior, and any reliance on vanilla background rendering. Audit historical test, build, main-menu, and artifact evidence without treating it as current closure proof | `P001-TASK-002`, candidate diff | Complete screen matrix and retain or reject decision for the exact accepted commit | `AbstractShopScreen`, `ShopScreenMarker`, and the 16 concrete screens | Exact source inventory, no escaped screen, no non-shop policy reach, and no Forge, server, packet, persistence, or dependency diff |
| `P001-TASK-004` | CORE-REQ-003, CORE-REQ-015 | Run the focused `ShopScreenBackgroundPolicyTest` against the affected baseline and candidate. Strengthen it only if it can miss a concrete screen or the bounded policy, then prove it runs in the full suite | `P001-TASK-003` | Focused failing-before and passing-after regression | NeoForge unit-test source set and screen policy contract | Exact test commands, revisions, reports, complete 16-screen coverage, and full-suite inclusion |
| `P001-TASK-005` | CORE-REQ-003, CORE-REQ-016, CORE-REQ-017 | Retain the exact accepted commit `bfba91f7b0c51b03d07117c4f1851c38a98f6186` unchanged, remove any unrelated phase-branch drift outside that commit, preserve all 16 routes, confirm exactly `2.2.1`, and reconcile only the documentation already contained in the accepted commit | `P001-TASK-003`, `P001-TASK-004`, DEC-005 | Exact accepted implementation, test, metadata, and documentation diff | NeoForge client screen package, version property, generated metadata inputs, README and porting notes already changed by the accepted commit | Commit identity and diff equality, metadata expansion, documentation review, and proof of no additional product change and no `1.20.1` change |
| `P001-TASK-006` | CORE-REQ-003, CORE-REQ-015, CORE-REQ-016 | On the final phase branch, run the focused policy test, full Java 21 suite, build, JAR inspection, and actual client lifecycle for every one of the 16 screens and negative controls. Retain exact evidence and new artifact hash | `P001-TASK-005`, disposable client fixture | Pre-integration verification packet | Gradle test and build tasks, client runtime, packaged JAR | Exact commands, revision, Java version, decisive results, all-screen matrix, logs, artifact identity, archive and metadata inspection, and complete diff audit |
| `P001-TASK-007` | CORE-REQ-003, CORE-REQ-016, CORE-REQ-020 | Create or update the authorized phase pull request with base exactly `1.21.1` and product diff exactly equal to commit `bfba91f7b0c51b03d07117c4f1851c38a98f6186`, request one private independent review if the optional private review capability exists, resolve verified findings without altering the accepted commit, pass all required checks, and merge through GitHub with the required method | `P001-TASK-006`, repository branch and review rules | Merged pull request and exact merge commit | GitHub pull request, checks, review, conversations, and `1.21.1` branch | Base and head proof, exact accepted-commit diff, green required checks, review result or recorded capability absence, resolved findings, merge result, and no direct support-branch push |
| `P001-TASK-008` | CORE-REQ-003, CORE-REQ-015, CORE-REQ-016, CORE-REQ-020 | Fetch and verify the merged `origin/1.21.1`, rerun the focused test, full suite, build, JAR inspection, and all 16 client lifecycles at the exact merged revision, then create and push the signed annotated phase tag and close issue 22 with owner-accepted evidence | `P001-TASK-007`, EXT-005 | Verified remote integration, signed tag, and closed issue | Remote branch, merged worktree, client runtime, artifact, tag, issue 22, project and milestone | Ancestry, exact merged checks, all-screen matrix, tag signature and remote presence, closure comment, and closed issue state |
| `P001-TASK-009` | CORE-REQ-003, CORE-REQ-015, CORE-REQ-016, CORE-REQ-020 | Assemble the immutable completion packet outside the plan, reconcile tracking, and hand off only the verified merged NeoForge state to Phase 002 | `P001-TASK-001` through `P001-TASK-008` | Phase completion packet and transition declaration | Verification artifacts, issue, pull request, remote branch, tag, project and milestone | Packet audit against every exit criterion and no active `EXT-001` blocker |

Tasks are dependency ordered. Read-only baseline checks and disposable fixture preparation may overlap. Candidate inspection precedes any code decision. The focused test and screen inventory must agree before implementation is retained. Pull request integration follows complete branch evidence. Issue closure and tagging follow exact merged-revision evidence. Any failed internal check stops integration or closure and remains recorded; no lower-fidelity result substitutes for the actual client lifecycle.

## Architecture and Implementation Boundaries

The correction belongs entirely to the NeoForge client presentation package `com.enviouse.futureshopsp.client.screen`. Concrete FutureShops screens depend on one shared background policy, which depends only on the pinned Minecraft client `Screen` API. Common initialization and dedicated-server paths must not load the policy class. The screen inventory contract must fail if a new concrete `ShopScreenMarker` screen escapes the shared policy.

The policy may suppress only the unwanted vanilla background pass. Existing screen render methods retain ownership of intentional full-screen dim surfaces, panels, cards, controls, text, items, hover states, and tooltips. Widget dispatch, tooltip layering, focus, narration, mouse and keyboard input, pause behavior, resize and reinitialization, parent return, and close behavior remain intact. The phase must not change global graphics settings, shaders, renderer state outside these screens, resources unrelated to the policy, mixins, or other Minecraft screens.

This phase has no server authority, networking, configuration, persistence, command, economy, escrow, or market change. It introduces no schema or migration. Version authority remains the NeoForge branch's canonical build property and generated metadata. Minecraft `1.21.1`, NeoForge `21.1.233`, ModDevGradle `2.0.141`, Java 21, mappings, GeckoLib, identifiers, resource namespace, protocol, and persisted schemas remain unchanged. Forge `1.20.1` must remain byte-for-byte outside this phase's diff.

The accepted commit is the only authorized product change in this phase. If its shared override is broader than the proven cause, a screen relies on vanilla background behavior, the test can miss a concrete screen, or fresh runtime evidence exposes a regression, do not alter or integrate it. Preserve the evidence and stop for an owner-authorized Plan Creator revision.

## Failure, Recovery, and Edge Cases

| Scenario | Detection | Required behavior | Recovery or rollback | Regression proof |
|---|---|---|---|---|
| Fresh lifecycle inspection contradicts the accepted mechanism | Exact pinned source, bytecode, or runtime trace differs materially | Preserve the contradiction and stop candidate acceptance | Restore the unmodified NeoForge baseline and return `PLAN_REVISION_REQUIRED` for the owner to decide whether the accepted exact-commit contract changes | New baseline and candidate lifecycle trace retained without integration |
| One of the 16 screens escapes the policy | Static inventory or actual client matrix finds direct `Screen` behavior or missing inheritance | Treat as a blocking issue 22 defect | Do not change or integrate the accepted commit. Preserve the evidence and return `PLAN_REVISION_REQUIRED` for an owner decision | Exact escaped-screen evidence and an unmodified candidate diff |
| Custom content still blurs or intentional backdrop disappears | All-screen visual matrix shows blurred content or missing dim surface | Block pull request integration | Keep the accepted commit unmodified, preserve captures and traces, and return `PLAN_REVISION_REQUIRED` for an owner decision | Before-and-after captures and interaction evidence for the affected screen retained without integration |
| Widget, tooltip, focus, narration, input, resize, navigation, close, or reopen regresses | Client checklist or log records incorrect behavior or render error | Block merge and retain exact trace | Keep the accepted commit unmodified and return `PLAN_REVISION_REQUIRED` for an owner decision | Failed lifecycle evidence and exact candidate diff retained without integration |
| Non-FutureShops screen changes | Negative controls show policy reach or changed background behavior | Reject the implementation as overbroad | Keep the accepted commit unmodified and return `PLAN_REVISION_REQUIRED` for an owner decision | Negative-control evidence and exact candidate diff retained without integration |
| Focused test, full suite, or build fails | Nonzero Gradle result or decisive compiler or test failure | Stop integration | Confirm whether the failure is environment-only. If the accepted product change requires alteration, return `PLAN_REVISION_REQUIRED` | Clean rerun for an environment-only failure, or retained decisive failure evidence without integration |
| Main menu launches but affected screens are not exercised | Evidence contains only startup or title-screen state | Treat runtime gate as missing | Prepare the required fixture and execute every named screen lifecycle | Sixteen complete screen records at the exact artifact revision |
| JAR metadata or contents drift | Filename, generated metadata, loader range, dependency list, or archive differs | Block merge and tag | Correct canonical version or remove unintended changes and rebuild | Fresh archive, metadata, hash, and client launch inspection |
| `1.21.1` moves or pull request base is wrong | Fetch, merge-base, or pull request metadata shows drift | Do not merge or retarget to Forge | Rebuild from latest approved `1.21.1`, reapply the bounded diff, and rerun all branch-bound evidence | New ancestry, checks, review, build, JAR, and all-screen packet |
| Required check or review finds a valid product defect | Failed check, unresolved conversation, or verified finding | Keep the pull request unmerged | Preserve the finding, leave the accepted commit unmodified, and return `PLAN_REVISION_REQUIRED` for an owner decision | Exact finding and unmodified candidate evidence |
| Private review capability is absent | Repository integration reports no supported private review path | Record capability absence as the owner-authorized nonblocking result | Continue with required deterministic checks | Capability record plus complete deterministic evidence |
| Merge changes the verified tree | Branch and merge tree comparison differs | Do not tag or close issue 22 | Rerun every affected test, build, client, JAR, documentation, and diff check on the merge | Complete merged-revision packet |
| Tag signing fails or tag points elsewhere | Signature verification or target comparison fails | Do not push the tag or transition | Repair EnVisione signing configuration without changing the merge, recreate the annotated tag, and verify locally | Verified tag signature, exact target, and remote tag object |
| Issue 22 is closed before evidence completes | Issue state is closed while any gate is missing | Reopen it and correct the closure record | Complete the missing internal evidence, then close with exact links | Closed issue state after all gates pass |
| Reporter or Windows evidence is mistakenly requested | Issue, pull request, or packet treats `EXT-001` as active | Remove the false gate and preserve it only as history | Reconcile with DEC-004 and owner acceptance | Final issue and packet review contains no external acceptance dependency |
| Sensitive or unrelated data enters evidence | Privacy and diff review finds credentials, private player data, local paths, logs, caches, or unrelated work | Stop publication of evidence and sanitize it | Remove or replace unsafe evidence and assess any exposure | Repeated privacy review and clean final diff |

## Verification Matrix

| Requirement or task | Static or unit | Integration | Real workflow or runtime | Negative and recovery | Evidence artifact |
|---|---|---|---|---|---|
| `P001-TASK-002` | Inspect pinned `Screen` API and FutureShops overrides | Compare baseline and candidate call order | Trace at least one affected screen before and after | Distinguish intentional custom dimming from unintended vanilla blur | Lifecycle report with exact sources, revisions, logs, and captures |
| `P001-TASK-003` | Inventory exactly 16 concrete marker screens and inheritance | Map each route, parent, and special render behavior | Prepare deterministic access to every screen | Identify any non-marker screen and prove no policy reach | Screen inventory and route matrix |
| `P001-TASK-004` | Run focused `ShopScreenBackgroundPolicyTest` before and after | Prove the policy test runs in the full suite | Correlate test assertions with actual client behavior | Remove or bypass policy in the baseline fixture to prove sensitivity | Exact commands, reports, and revision comparison |
| `P001-TASK-005` | Diff, dependency, and version inspection | Generated metadata agrees with `2.2.1` | Development client reports exact version | No Forge, server, protocol, schema, or global setting change | Source, metadata, and documentation diff |
| `P001-TASK-006` | Focused test and complete Java 21 unit suite | Clean Java 21 build and JAR inspection | All 16 screen lifecycles pass in the exact built client | Vanilla and non-FutureShops controls plus resize, reopen, and navigation checks | Test reports, build log, all-screen matrix, sanitized captures, client log, artifact hash, and archive report |
| `P001-TASK-007` | Required CI and final diff checks | Pull request base is only `1.21.1`; merge occurs through GitHub | Review evidence and artifact identity remain bound to branch revision | Any failed or unresolved check prevents merge | Pull request, check, review, conversation, and merge records |
| `P001-TASK-008` | Focused, full-suite, build, metadata, tag-signature, and ancestry proof | Fetched `origin/1.21.1` contains the merge and remote contains the tag | All 16 lifecycles pass on the exact merged artifact | Wrong tag target, stale remote, or premature issue closure fails the gate | Merged packet, tag object, signature, remote refs, issue comment, and closed issue state |
| CORE-REQ-003 | Every phase-local static, automated, build, metadata, GitHub, and signing gate passes | Correct support-line merge and remote verification pass | All 16 FutureShops screen lifecycles pass without blur or interaction regression | No cross-line change, global side effect, external acceptance gate, or unresolved defect remains | Complete phase packet and closed issue 22 |

### Client fixture, all-screen inventory, and rerun order

The mandatory client inventory is:

1. `BalTopOverviewScreen`
2. `BalanceOverviewScreen`
3. `BarterScreen`
4. `CartScreen`
5. `DepartmentPickerScreen`
6. `FranchiseManagementScreen`
7. `ItemDetailScreen`
8. `LocalShopBrowserScreen`
9. `PlayerShopBarterScreen`
10. `PlayerShopBlockScreen`
11. `PlayerShopCartScreen`
12. `PlayerShopSellScreen`
13. `PromoEditorModalScreen`
14. `SettlementHistoryScreen`
15. `ShopMainScreen`
16. `TransactionHistoryScreen`

Use a disposable NeoForge 1.21.1 client profile with NeoForge 21.1.233, Java 21, the exact FutureShops artifact and required runtime dependencies, and a non-sensitive local world or isolated server fixture. Prepare roles, balances, catalogs, player-shop data, barter offers, carts, rankings, histories, departments, franchise data, and promo-editor state needed to reach every route naturally. Direct test-only routing is acceptable only if it invokes the same production screen initialization, render, input, resize, navigation, and close lifecycle as the real route and does not replace the actual client.

For each screen, record the route and prerequisite state, first render, expected sharp custom content, expected intentional dim region, widget and tooltip behavior, applicable keyboard and mouse input, resize or GUI-scale reinitialization, child or parent navigation, close, reopen, and absence of new FutureShops errors. Record operating system, graphics adapter and driver where available, window mode and size, GUI scale, resource packs, shader state, locale, source revision, artifact hash, and fixture identity. Use the same scene and settings for baseline and fixed comparisons where practical. No Windows-specific environment is required.

Verification order is fresh lifecycle inspection, focused before-and-after policy test, complete unit suite, applicable data or GameTest tasks only if the exact accepted diff affects those surfaces, clean Java 21 build, JAR inspection, all 16 actual client lifecycles, negative controls, and complete diff inspection. Any product change beyond the exact accepted commit blocks the phase and returns `PLAN_REVISION_REQUIRED`. After merge, fetch `origin/1.21.1` and repeat the focused test, full suite, build, JAR inspection, and all 16 screen lifecycles on the exact merged revision before tagging or closing issue 22.

Failures retain the exact command, revision, environment, screen route, fixture, decisive error, and affected evidence. Historical full-suite, build, main-menu, and artifact-hash evidence remains useful provenance but cannot replace merged-revision reruns or the all-screen lifecycle.

## Documentation, Operations, and Release

- Review the candidate `README.md` and `PORTING_NOTES.md` changes against final code and evidence. Retain only accurate NeoForge 1.21.1 setup, FutureShops `2.2.1` identity, bounded blur correction, and verification instructions. Do not describe the candidate as publicly released.
- Update established documentation paths rather than creating duplicates. Update `docs/README.md` only if a tracked document is added or moved.
- Update issue 22 with lowercase GitHub text that records the confirmed root cause, explicit owner acceptance, candidate and merged revisions, focused and full-suite results, build, all 16 client lifecycles, artifact identity, pull request, checks, private review state, merge, remote verification, and signed phase tag. Close it only after all internal evidence passes.
- Create or update one phase pull request whose base is exactly `1.21.1`. Link issue 22 and the phase evidence. Request one private independent review if supported. Never target `1.20.1`, `main`, or another branch.
- After merge and merged-revision verification, create the signed annotated tag `phase-001-neoforge-issue-22` on the exact merge commit and push only the tag. Verify its remote target and signature before transition.
- Use Java 21 and the checked-in wrapper. Keep disposable client state, screenshots containing local details, logs, caches, generated worlds, and downloaded artifacts outside tracked source unless an established sanitized evidence path explicitly owns them.
- No configuration, migration, backup, recovery, server-authority, or release-platform change is expected. A proven need for one is outside this phase and requires the appropriate contract decision.
- Artifact work is verification packaging only. This phase may record SHA-256 for identity. Final SHA-256 and SHA-512 candidate manifests remain owned by `CORE-PHASE-007` under `CORE-REQ-018`.
- Do not create a GitHub Release, publish to CurseForge or Modrinth, announce a release, or create a public product version tag.

## Risks and Evidence Invalidation

| Risk | Prevention | Detection | Recovery | Evidence invalidated | Reverification |
|---|---|---|---|---|---|
| Historical success is mistaken for current proof | Label prior Java 21, main-menu, and hash evidence as historical | Completion packet lacks fresh exact-revision results | Run the missing branch and merged checks | Test, build, runtime, artifact, and closure evidence | Rerun from the first missing gate |
| Screen inventory is incomplete | Bind static inventory and runtime matrix to the marker contract | Count differs from 16 or a route lacks evidence | Correct inventory and fixture before merge | Focused, client, JAR, and documentation evidence | Rerun focused policy test and all-screen matrix |
| Empty background override removes intended behavior | Explicitly verify custom dim ownership on every screen | Missing dim, transparent scene, z-order, or overlay defect | Keep the accepted commit unmodified, retain the failed trace, and return `PLAN_REVISION_REQUIRED` | All client and review evidence | No integration until an owner-approved revised contract exists |
| Shared base changes unrelated behavior | Keep dependency direction client-only and policy-scoped | Negative controls or navigation and input checks fail | Keep the accepted commit unmodified, retain the failed trace, and return `PLAN_REVISION_REQUIRED` | Unit, client, review, and artifact evidence | No integration until an owner-approved revised contract exists |
| Support head moves before merge | Refresh remote and pull request base before integration | Ancestry or base comparison differs | Rebuild from latest approved `1.21.1` and reapply only the bounded diff | All branch-bound evidence | Full pre-integration sequence on the new head |
| Late review or merge changes content | Compare verified branch tree and merge tree | Tree, metadata, dependency, or resource identity differs | Keep issue open and rerun downstream checks | Every changed downstream proof | Complete merged-revision sequence |
| Tag or signature is wrong | Pin exact merge before tag creation and verify EnVisione signing | Tag target or signature check fails | Delete only the unpushed local incorrect tag, recreate correctly, and verify before push | Tag and transition evidence | Tag target, signature, and remote verification |
| `EXT-001` is accidentally reactivated | State historical status in entry, scope, tasks, exit, and packet | Any workflow requests reporter or Windows approval | Remove the false dependency and reconcile with DEC-004 | Issue, pull request, packet, and closure evidence | Repeat evidence and dependency audit |
| Documentation overstates release status | Separate merged internal candidate from public release | Source-to-document and GitHub review finds publication claims | Correct before closure | Documentation and completion packet | Repeat documentation and issue review |
| Sensitive or unrelated material enters output | Use disposable data and inspect full diff and retained evidence | Privacy or repository review finds prohibited content | Sanitize evidence and remove unrelated tracked changes | Affected artifact, issue comment, or diff | Repeat privacy and final-diff review |
| Platform or dependency drift is bundled | Compare build and dependency metadata with approved `1.21.1` | Unexpected Minecraft, NeoForge, Java, ModDevGradle, mapping, dependency, or wrapper change | Remove drift and rebuild | Test, runtime, JAR, compatibility, review, and merge evidence | Full Java 21 verification sequence |

## Phase Completion Packet

The packet is retained outside this protected plan file and contains:

1. Exact approved starting `1.21.1` revision, candidate revision, final phase-branch revision, pull request, merge commit, fetched `origin/1.21.1` head, and ancestry proof.
2. Confirmed root-cause and owner-acceptance record, fresh pinned-lifecycle inspection, and explicit statement that `EXT-001` is historical resolved traceability only.
3. Candidate audit, complete 16-screen inventory, implementation decision, final diff, and proof of no Forge, server, network, persistence, global graphics, schema, dependency, or pinned-platform change.
4. Focused baseline-failure and final-pass policy results, complete Java 21 unit-suite result, build result, and any applicable data or GameTest result with grounded not-applicable rationale.
5. Actual client lifecycle records for all 16 screens, negative controls, sanitized captures, interaction checklist, client logs, fixture manifest, graphics and GUI settings, exact revisions, and artifact hashes.
6. JAR filename, SHA-256, archive listing, generated metadata, FutureShops `2.2.1`, loader and Minecraft ranges, class and resource inventory, dependency inspection, and exclusion audit. Retain historical SHA-256 `a2e0d924d5523f3bab1253158894b2d77c1ef47467f767c14e13a4853807cfdb` only as candidate provenance.
7. Authorized pull request details with base `1.21.1`, required checks, private independent review result or capability absence, resolved findings, merge method, and post-merge reruns.
8. Signed annotated tag `phase-001-neoforge-issue-22`, exact merge target, local signature verification, remote tag presence, and EnVisione identity.
9. Reconciled NeoForge documentation, issue 22 closure comment and closed state, project and milestone state, and explicit confirmation that no reporter or Windows gate was used.
10. One declaration: `CORE-PHASE-001 complete, transition to CORE-PHASE-002`, issued only after every exit gate above passes.

Any failed, skipped, flaky, stale, or unavailable mandatory internal evidence prevents phase exit. Private review capability may be recorded unavailable because the owner made it conditional. No other evidence gate has a lower-fidelity substitute.

## Next Transition

After the completion packet proves the full exit, reread `plan.md` and `phases/plan-phase-002.md` through EOF. Confirm through GitHub and a fresh fetch that the authorized pull request merged only into `1.21.1`, `origin/1.21.1` contains the merge, all required post-merge checks pass, the signed phase tag points to that exact commit, and issue 22 is closed with owner-accepted evidence. Then begin `CORE-PHASE-002` at its first unfinished entry gate.

Do not begin Phase 002 while the pull request is open, checks are pending or failed, the private-review decision is unresolved, the merge is absent from `origin/1.21.1`, any of the 16 screen lifecycles is unverified, tag signing or remote tag verification failed, or issue 22 remains open. Do not touch Forge `1.20.1` while completing this phase, and do not publish a public release.
