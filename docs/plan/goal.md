Objective:
Complete every mandatory requirement `CORE-REQ-001` through `CORE-REQ-022` and every stable-release gate in `/mnt/hermes/projects/FutureShops/docs/general/plan.md`. Successful completion is permitted only when sequential integration, artifact-to-commit binding, runtime verification, recovery proof, documentation, issue 66, and the final plan-wide audit pass with no known mandatory repository-owned defect.

Immediate checkpoint:
Active phase: CORE-PHASE-000
Active phase plan: /mnt/hermes/projects/FutureShops/docs/general/phases/plan-phase-000.md
Active phase entry action: P000-TASK-001

Execute `P000-TASK-001`. One bounded inspection ends as soon as each mandatory criterion is classified as implemented with valid evidence, incomplete, evidence invalidated or stale, or externally blocked. Immediately execute the first incomplete or stale-evidence criterion. The map is not a deliverable. Do not produce a narrative audit before implementation, do not stop after producing the map, and do not repeatedly rebuild the map against unchanged evidence.

Authoritative plan:
Plan: /mnt/hermes/projects/FutureShops/docs/general/plan.md
Plan SHA-256: 80544f24e6a8b615faf7eed8afafeae3cd21276facbf7c78289eb4c969435193
Plan manifest: /mnt/hermes/projects/FutureShops/docs/general/plan.index.json
Plan handoff: /mnt/hermes/projects/FutureShops/docs/general/plan.handoff.json
Plan set SHA-256: 9fa6cbb672eadbd7b15a7f14cd4f9d4b8b2583ec769d68bebb4cad910d80aff7
Phase plans directory: /mnt/hermes/projects/FutureShops/docs/general/phases
Completion endpoint: One fully validated and inspected unpublished FutureShops 2.3.0 jar for Minecraft 1.21.1 and NeoForge 21.1.248, proven against internal, exact Pixelmon 9.4.0, and one exact reviewed Vault bridge stack, plus the existing read-back GitHub issue 66 for 3.0.0 Forge maintenance and its future 1.21.1 port.

The complete registered plan set and both plan SHA-256 digests are creation-time provenance, not runtime locks.

Observed checkout branch: envy/plan-2.3.0-external-economy
Observed checkout commit: 1cdfd21cf726e90f660d167e07946476317e1fee
Repository root: /mnt/hermes/projects/FutureShops

Authoritative remote:
origin
https://github.com/MCEnvision/FutureShops.git

Observed local default branch: 1.20.1
Observed local default-branch commit: c6709e12ca7084ee068b2497a577b8d47c12f6fd
Observed local remote-tracking ref: origin/1.20.1
Observed local remote-tracking commit: 78fad4069d778996c24ecf5acc5cbe0e1edea7a2
Current remote default-branch head: 78fad4069d778996c24ecf5acc5cbe0e1edea7a2
Remote-head evidence: Read-only git ls-remote --symref origin HEAD observed on 2026-09-02 at 18:01:39Z.
Authoritative working baseline: established
Applicable implementation branch: none identified at checkpoint
Applicable open pull request: none identified at checkpoint

Execution behavior:
Verify the authoritative plan, repository identity, package metadata, and remote describe the same project. Verify `origin` is the intended repository; fetch `origin` without altering the remote, and refresh and inspect it without altering the remote. Verify the fetched remote-tracking ref equals the current remote default-branch head. Classify the local default branch as equal, behind, ahead, or diverged; fast-forward only when safe. Do not reset, force, discard, or overwrite unexpected history. Search local branches and remote branches and repository-wide open pull requests. Resume applicable active work; otherwise create a branch from the verified authoritative baseline. Do not invent a branch when an applicable active branch exists. Create or resume the implementation branch before modifying tracked files. Do not commit directly to the default branch. For the default branch, use a safe fast-forward only when appropriate; later changes require authorized pull-request integration.

Read the active phase plan blueprint, implement, test, audit, fix root causes, verify real behavior, and complete required evidence and phase exit criteria. Complete pull-request integration, verify the resulting default branch and signed phase tag before the next phase. Never stack phase branches. Reread the next contiguous phase file and continue remaining mandatory work under the same immutable goal. In the final phase, pass the plan-wide Definition of Done and final proof before success.

After compaction, resumption, detected plan changes, or transition, reread the current authoritative plan set. Identify and classify current plan changes; routine progress, evidence, status, clarification, and phase transition changes continue without owner input. Never stop solely because of a plan or handoff digest. `docs/plan/goal.md` is immutable and create-once. Never invoke Plan Creator or Goal Creator, or spawn their authors. Never refresh, rewrite, rebind, overwrite, or replace the saved goal at `docs/plan/goal.md`. Documentation changes do not substitute for implementation.

Guardrails and authority:
Decisions `DEC-001` through `DEC-016` are locked. Optional and future work `FUT-001` through `FUT-004` is excluded. Preserve completed and unrelated work, and use the authorized identity, signing, branch, review, and pull-request workflow. Keep credentials, private data, external artifacts, and secret-bearing files out of tracked content and evidence.

The baseline is 2.2.1, not 2.2.0. Do not substitute or generalize unreviewed external artifacts. Preserve issue 66 unchanged through phases 000 through 002; Phase 003 may search, update, and read it back only after artifact validation. Never duplicate the issue, publish, tag a release, upload, or announce the candidate. Escalate only a new material choice changing scope, endpoint, cost, licensing, public behavior, trust boundaries, destructive behavior, credentials, external communication, or irreversible remote state.

Verification and stopping:
Run focused tests, complete tests, applicable data generation and GameTests, build, server and client smoke tests, multiplayer and reconnect checks, persistence and migration fixtures, crash and recovery matrices, exact external environments, dependency and security review, documentation rehearsal, jar inspection, and complete diff inspection. Prove source commit, jar metadata, SHA-256, SHA-512, installed bytes, manifests, and runtime identity agree. Before integration or blocked reporting, run `git status`, `git diff --check`, and `git log`; reject unexplained generated, unrelated, temporary, or secret-bearing files, and verify the authoritative remote branch.

Never weaken, skip, disable, delete, or narrow valid tests; never suppress a valid failure; never ignore a required exit code; never reduce a required threshold; never mark a required check as allowed to fail; never add a production bypass solely for tests; never substitute mocked behavior for required real integration. If a test contradicts the plan or contract, prove the contradiction and replace it with equal or stronger coverage.

The approved blockers are: Official Pixelmon 9.4.0 runtime and development artifacts; Disposable exact Pixelmon 9.4.0 integration environment; Pixelmon economy API feasibility proof; Separately installed Vault bridge artifact; Exact reviewed hybrid runtime, Vault, and economy plugin stack; Disposable exact Vault bridge integration environment. When any prerequisite becomes available, perform its blocked operation and dependent verification. Use an approved bounded check; do not wait, sleep, or poll indefinitely. When independent mandatory work is complete, report the exact blocker immediately if required proof remains unavailable.

Permitted terminal states: `SUCCESS` only for the complete endpoint proof. `NOT COMPLETE — EXTERNALLY BLOCKED` is permitted only when an approved prerequisite is proven unavailable, all independent mandatory work is complete, and the record gives evidence, attempted operation, required external action, and remaining verification. `PLAN_REVISION_REQUIRED` applies only to a material product-contract change and reports affected stable IDs and the owner decision. `GOAL_REVISION_CONFLICT` applies only if the saved goal changed and reports the expected and observed goal digest. `OWNER_INPUT_REQUIRED — REPOSITORY MISMATCH` and `REPOSITORY_STATE_CONFLICT` protect repository safety. Before returning either repository state, attempt every safe non-destructive resolution available from repository metadata and remote evidence. Plan or handoff digest drift is never a stopping state. No other early stopping state is permitted.

Continuity:
Maintain a temporary ledger with revisions, active phase ID and file, completed phase gates, integration state, first unfinished task, evidence, next contiguous phase, blocker, and next action. Do not repeat completed work. Transitions update temporary internal continuity state only and never refresh the goal. The requirement map and ledger are temporary internal continuity state; do not commit or publish them or add them to `plan.md`, `status.md`, issues, pull requests, or repository documentation.

On failure, preserve decisive evidence, fix the smallest correct root cause, add regression coverage, rerun the narrow check, then affected higher-level gates. Do not rerun the same unchanged failing check more than twice without changing the code, configuration, environment, instrumentation, or diagnostic hypothesis. Resume from the recorded state without repeating completed work. Repository-owned defects, missing implementation, failed tests, uncertainty, difficulty, or phase completion are work, not external blockers or success.
