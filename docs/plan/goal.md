Objective:
Complete every mandatory requirement `CORE-REQ-001` through `CORE-REQ-022` and every stable-release gate in `/mnt/hermes/projects/FutureShops/docs/general/plan.md`. Successful completion is permitted only when sequential integration, artifact-to-commit binding, runtime verification, recovery proof, documentation, issue 66, and the final plan-wide audit pass with no known mandatory repository-owned defect.

Immediate checkpoint:
Active phase: CORE-PHASE-000
Active phase plan: /mnt/hermes/projects/FutureShops/docs/general/phases/plan-phase-000.md
Active phase entry action: P000-TASK-001

Execute `P000-TASK-001`. One bounded inspection ends as soon as each mandatory criterion is classified as implemented with valid evidence, incomplete, evidence invalidated or stale, or externally blocked. Immediately execute the first incomplete or stale-evidence criterion. The map is not a deliverable. Do not produce a narrative audit before implementation, do not stop after producing the map, and do not repeatedly rebuild the map against unchanged evidence. Do not repeat completed work.

Pull requests 67 and 68 merged Phases 000 and 001 into `1.21.1`. Phase 002 has no open pull request, and its completion record is not closed. Classify this evidence against exit criteria; it waives no gate.

Authoritative plan:
Plan: /mnt/hermes/projects/FutureShops/docs/general/plan.md
Plan SHA-256: c6b23d02feb52fb0bf220323eed5f1a46cf921939f9d7da94af23de819c3d99a
Plan manifest: /mnt/hermes/projects/FutureShops/docs/general/plan.index.json
Plan handoff: /mnt/hermes/projects/FutureShops/docs/general/plan.handoff.json
Plan set SHA-256: 6fac49c5c2541eda3c722b0610aad0d5dc35c87655a59edbbf397c1d66ea20b8
Phase plans directory: /mnt/hermes/projects/FutureShops/docs/general/phases
Completion endpoint: One fully validated and inspected unpublished FutureShops 2.3.0 jar for Minecraft 1.21.1 and NeoForge 21.1.248, proven for full internal behavior, a native exact Pixelmon 9.4.0 `PlayerPartyStorage` transaction-mixin path, and separate `vault` registration with mutation enabled only when a bridge and backend provide one durable balance and receipt transaction, otherwise refusing safely, plus the existing read-back GitHub issue 66 updated with implementation guidance for 3.0.0 Forge 1.20.1 and its future 1.21.1 port.

The complete registered plan set and both plan SHA-256 digests are creation-time provenance, not runtime locks.

Observed checkout branch: envy/phase-002-pixelmon-vault
Observed checkout commit: 6dead6dcd387ff5c17f62d68347c7541b022666d
Repository root: /mnt/hermes/projects/FutureShops

Authoritative remote:
origin
https://github.com/MCEnvision/FutureShops.git

Observed local default branch: 1.20.1
Observed local default-branch commit: c6709e12ca7084ee068b2497a577b8d47c12f6fd
Observed local remote-tracking ref: origin/1.20.1
Observed local remote-tracking commit: 78fad4069d778996c24ecf5acc5cbe0e1edea7a2
Current remote default-branch head: 78fad4069d778996c24ecf5acc5cbe0e1edea7a2
Remote-head evidence: git ls-remote read-only live remote evidence observed 2026-09-04
Authoritative working baseline: established
Applicable implementation branch: envy/phase-002-pixelmon-vault at 6dead6dcd387ff5c17f62d68347c7541b022666d
Applicable open pull request: none identified for envy/phase-002-pixelmon-vault at checkpoint

Execution behavior:
Verify the plan, repository identity, package metadata, and remote describe the same project. Verify `origin` is the intended repository, fetch `origin` without altering the remote, and verify the fetched remote-tracking ref equals the current remote default-branch head. Refresh and inspect without altering the remote. Classify the local default branch as equal, behind, ahead, or diverged; fast-forward only when safe. Do not reset, force, discard, or overwrite unexpected history.

Search local branches and remote branches and repository-wide open pull requests. Resume applicable active work; otherwise create a branch from the verified authoritative baseline. Do not invent a branch when an applicable active branch exists. Create or resume the implementation branch before modifying tracked files. Do not commit directly to the default branch. Use a safe fast-forward only when appropriate; later changes require authorized pull-request integration. Preserve the `1.21.1` phase line and do not retarget it to `1.20.1`.

Read the active phase plan blueprint through EOF, implement, test, audit, fix root causes, verify real behavior, and satisfy phase exit criteria and required evidence. Integrate by pull request, verify the resulting default branch and signed phase tag before the next phase, and never stack phase branches. Reread the next contiguous phase file and continue all remaining mandatory work under the same immutable goal. The final phase requires the plan-wide Definition of Done and final proof.

Guardrails and authority:
Decisions `DEC-001` through `DEC-020` are locked and resolved, with `DEC-012` refined by `DEC-018`. Optional and future work `FUT-001` through `FUT-004` is excluded. Preserve the non-goals, server authority, fail-closed transaction safety, exact external identities, issue 66 timing, and unpublished endpoint.

External prerequisites `EXT-001` through `EXT-009` are available or authorized for their scoped operations. Do not compile, alter, or repackage Pixelmon or any other external mod. Use the exact Pixelmon jar only as a read-only reference for interoperability research, including inspection or decompilation. Keep mixin code in the FutureShops jar and bridge proof code in a separate test bridge. Never copy external source into FutureShops or claim it as ours. Run external mods only as unmodified dependencies; never bundle or publish them. Any newly discovered contradictory term is a material licensing change, not permission to broaden these boundaries.

`docs/plan/goal.md` is immutable and create-once during execution. Never refresh, rewrite, rebind, overwrite, or replace the saved goal. Never invoke Plan Creator, Plan Maintainer, or Goal Creator. Reread the current authoritative plan set after compaction, resumption, transition, or detected plan changes. Identify and classify current plan changes. Routine progress, evidence, status, clarification, and phase transition changes continue without owner input. Plan or handoff digest changes never invalidate the goal. Documentation changes do not substitute for implementation.

Escalate only a material contract change involving scope, endpoint, owner decisions, cost, licensing, public behavior, trust boundaries, destructive behavior, credentials, external communication, or irreversible remote state. Keep credentials, private data, external artifacts, disposable runtimes, and secret-bearing files out of tracked content and evidence.

Verification and stopping:
Run focused tests, complete tests, applicable data generation and GameTests, build, dedicated-server and laptop client smoke tests, multiplayer and reconnect checks, persistence and migration fixtures, crash and recovery matrices, exact external environments, dependency and security review, documentation rehearsal, jar inspection, and complete diff inspection. Prove source commit, jar metadata, SHA-256, SHA-512, installed bytes, manifests, and runtime identity agree.

Never weaken, skip, disable, delete, or narrow valid tests. Never suppress a valid failure, ignore a required exit code, reduce a required threshold, or mark a check as allowed to fail. Never add a production bypass solely for tests. Never substitute mocked behavior for required real integration. If a test contradicts the plan or contract, prove the contradiction and replace it with equal or stronger coverage. Keep evidence valid and fresh; invalidate it after relevant changes.

Before integration or blocked reporting, run `git status`, `git diff --check`, and `git log`; reject unexplained generated, unrelated, temporary, or secret-bearing files, and verify the authoritative remote branch.

Permitted terminal states: `SUCCESS` only for complete endpoint proof. `PLAN_MAINTENANCE_REQUIRED` applies only to a material product-contract change and reports affected stable IDs and the owner decision. `GOAL_REVISION_CONFLICT` applies only if the saved goal changed and reports the expected and observed goal digest. `OWNER_INPUT_REQUIRED — REPOSITORY MISMATCH` and `REPOSITORY_STATE_CONFLICT` protect repository safety. Before returning either repository state, attempt every safe non-destructive resolution available from repository metadata and remote evidence. Plan or handoff digest drift is not a stopping or terminal state. No other early stopping state is permitted.

Continuity:
Maintain a temporary ledger with revisions, active phase ID and file, completed phase gates, integration state, first unfinished task, evidence, next contiguous phase, blocker, and next action. Resume from it without repeating completed work. Never refresh the goal across a phase transition.

The requirement map and ledger are temporary internal continuity state. Do not commit or publish them or add them to `plan.md`, `status.md`, issues, pull requests, or repository documentation.

On failure, preserve decisive evidence, fix the smallest correct root cause, add regression coverage, rerun the narrow check, then affected higher-level gates. Do not rerun the same unchanged failing check more than twice without changing the code, configuration, environment, instrumentation, or diagnostic hypothesis.
