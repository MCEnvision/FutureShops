# Phase 000 readiness completion packet

Observation date: 2026-09-24
Phase: CORE-PHASE-000
Requirement: CORE-REQ-001
Tasks: P000-TASK-001 through P000-TASK-005

## Deterministic validation

The packet was validated against the pinned Forge commit `78fad4069d778996c24ecf5acc5cbe0e1edea7a` and donor commit `cc2d69425beea2c93a4000e44d49381deba8eed2`.

| Check | Result |
| --- | ---: |
| Git path diff records | 1,923 |
| Semantic delta data rows | 1,923 |
| Donor history objects | 525 |
| Donor history data rows | 525 |
| Delta schema errors | 0 |
| History schema errors | 0 |
| Duplicate delta IDs | 0 |
| Duplicate history IDs | 0 |
| Unknown or incomplete applicable delta rows | 0 |
| Concrete monetary route records | 53 |
| Route records missing `Z0` or regression identity | 0 |
| `git diff --check` | pass |

The validation commands use exact object comparison, tab field counts, unique stable identifiers, nonempty owner fields, and the route refusal and regression columns. The path and history totals reconcile to Git directly. No component sample was substituted for the complete path or history set.

## Evidence packet

| Evidence | Purpose |
| --- | --- |
| `readiness-2026-09-24.md` | Identity, dirty-state fence, issue and ruleset intake, milestone, branch, and preservation register. |
| `semantic-delta-ledger.tsv` | One CORE-IF-001 row for every pinned path delta. |
| `history-delta-ledger.tsv` | One ownership and disposition row for every donor history object. |
| `semantic-delta-ledger.md` | Tree counts, disposition reconciliation, phase ownership, CodeGraph coverage, preservation checklist, and proof limits. |
| `monetary-route-inventory.md` | 53 concrete Forge monetary routes, provider and payment modes, value legs, custody, direct WAL lane, zero effect oracle, and later regression owner. |
| `pixelmon-artifact-intake.md` | Exact official candidate, hashes, manifest, loader, license, storage descriptors, provenance, and explicit unknown writer facts. |
| `host-and-cleanup-readiness.md` | Node and laptop anchors, Java candidates, Gradle task graph, graphical boundary, disposable resource registry, and cleanup result. |

## Issue and prerequisite reconciliation

Issue 66 and issue 9 remain open and are assigned to later acceptance gates. Four open Dependabot alerts were recorded at intake and remain a later dependency reachability task. Issue 79 records the reproducible Forge wrapper mode defect found during task graph discovery and is assigned to the Phase 001 CI task. The matching milestone is milestone 10, `3.0.0-beta.3 phase 000 readiness`. The new phase branch is `envy/3.0.0-beta.3-phase-000`, created from the exact Forge integration base with no stacked phase branch.

The Pixelmon candidate hash matched the prerequisite SHA-512. The current Modrinth version response returned null file hash fields, so the report preserves that discrepancy instead of calling the API hash declared. The exact CDN bytes independently matched the pinned prerequisite hash. Native writer atomicity and functional support remain unproven.

The node task graph identified server, GameTest, data, and client tasks. Current node Java is 25, while Java 17 is available on the authorized laptop. No Minecraft client, server, GameTest, renderer, world, audio stream, or watcher was launched. The laptop project candidate was empty; the mirrored checkout path requires refresh before Phase 010 and was not modified.

The required headless Gradle test gate was run after the evidence commit with `JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 bash ./gradlew --no-daemon --console=plain test`. It completed successfully in 45 seconds. The generated test report and build outputs were removed after the result was consumed. The project Gradle cache remains preserved as pre-existing shared project state.

## Preservation and cleanup result

The nineteen pre-existing tracked dirty entries remain unstaged and outside phase ownership. Only the phase 000 evidence files are new phase-owned files. Pixelmon and isolated Gradle scratch files were deleted after their final consumers. The exact Gradle problems report created during task graph discovery was deleted. No owned process remains. Existing ignored `.gradle` and `build` directories were preserved because ownership predated this bounded discovery and broad deletion is not authorized.

## Proof limit and phase state

Collection and validation satisfy the readiness evidence portion of `CORE-AC-001`. They do not prove product behavior, provider mutation, persistence atomicity, client behavior, or release readiness. The phase remains open until the common Section 16 integration gate completes: signed phase commit, pushed checked pull request into `1.20.1/3.0.0-beta.2`, required checks and independent review, GitHub merge commit, resulting base verification, signed phase tag, and final cleanup evidence. No production implementation or release artifact was created by Phase 000.
