Objective:
Finish every mandatory requirement and every stable-release gate in FutureShops for Forge 1.20.1 and NeoForge 1.21.1; exclude optional and future work and publication. Successful completion is permitted only when artifacts are bound to authoritative default-branch commits, runtime verification passes, the final plan-wide audit passes, no known mandatory repository-owned defect remains, and the completion endpoint is reached.

Immediate checkpoint:
Active phase: CORE-PHASE-000
Active phase plan: /mnt/hermes/projects/FutureShops/phases/plan-phase-000.md
Active phase entry action: Execute P000-TASK-001 by freezing provenance, dirty state, support refs, and legacy hashes without unrelated changes.

Run one bounded inspection. It ends as soon as each mandatory criterion is classified as implemented with valid evidence, incomplete, stale evidence, or externally blocked. Immediately execute first incomplete or stale-evidence criterion. The map is not a deliverable. Do not stop after producing the map, do not repeatedly rebuild it from unchanged evidence, and do not produce a narrative audit before implementation. Next action: P000-TASK-001.

Authoritative plan:
Plan path: /mnt/hermes/projects/FutureShops/plan.md
Plan SHA-256: c3eeec8c81de49265ede1f17643b8853696e6555c9e8d434d8359f7679eb1972
Plan manifest: /mnt/hermes/projects/FutureShops/plan.index.json
Plan set SHA-256: 173f1e4ed90adf4a89ff2d04411924297e1ec9016263e7909042d44e1a21027a
Phase plans directory: /mnt/hermes/projects/FutureShops/phases
Completion endpoint: Every scoped issue and every repository owned defect discovered by the rolling audit is deduplicated, fixed or given an evidence backed owner approved compatibility disposition, regression tested, merged into the correct support branch, and closed. Issue 22 is verified, merged into 1.21.1, and closed under explicit owner acceptance. Issues 25, 32, and 34 use local deterministic reproduction, fault injection, dedicated server, and multiple client evidence without reporter or hardware blockers. Exact Forge 3.0.0-beta.2 and NeoForge 2.2.1 candidates pass security, privacy, command, persistence, integration, runtime, documentation, and repeated clean audit gates. Publication and announcements remain excluded.

The registered plan set defines creation-time provenance. Plan and plan set digests are creation-time provenance, not runtime locks. At start, resumption, compaction, or plan change, read plan.md, plan.index.json, plan.handoff.json, registered plans, and active phase file through EOF.

Repository root: /mnt/hermes/projects/FutureShops
Observed checkout branch: envy/polish_plan
Observed checkout commit: 37525a9bae82f2aaeef50243ba3b3b5f1959cc53
Authoritative remote:
origin
https://github.com/MCEnvision/FutureShops.git
Observed local default branch: 1.20.1
Observed local default-branch commit: c6709e12ca7084ee068b2497a577b8d47c12f6fd
Observed local remote-tracking ref: origin/1.20.1
Observed local remote-tracking commit: c6709e12ca7084ee068b2497a577b8d47c12f6fd
Current remote default-branch head: c6709e12ca7084ee068b2497a577b8d47c12f6fd
Remote-head evidence: Read-only git ls-remote and GitHub API evidence observed on 2026-09-01 at 23:03:19-05:00.
Authoritative working baseline: established
Applicable implementation branch: envy/polish_plan at 37525a9bae82f2aaeef50243ba3b3b5f1959cc53
Applicable open pull request: none identified at checkpoint

The plan, repository identity, package metadata, and origin remote prove the same project. Verify origin is the intended repository. Fetch origin without altering the remote. Verify fetched remote-tracking ref against current remote default-branch head. Classify local default branch as equal, behind, ahead, or diverged. Fast-forward only when safe. Do not reset, force, discard, or overwrite unexpected history. Search local branches and remote branches plus repository-wide open pull requests. Resume the applicable active branch; otherwise branch from the verified authoritative baseline. Refresh and inspect metadata without altering the remote. Keep the default branch for safe fast-forward and authorized pull-request integration. Verify the authoritative remote branch. Create or resume the applicable implementation branch before modifying tracked files. Do not invent a branch when an applicable active branch exists. Do not commit directly to the default branch.

Execution behavior:
Read active phase blueprint and execute tasks in order. Satisfy phase exit criteria and required evidence gates. Merge each required pull request into the default branch, verify its phase tag, then before the next phase reread the next contiguous phase plan. Never stack phase branches or begin future work while an earlier pull request, check, merge, verification, or tag is incomplete. Continue subsequent mandatory work under the same immutable goal across phase transitions; phase completion is not full-plan success.

Keep issue-before-repair traceability. Reproduce root cause, make a compliant fix, add a regression, and verify real behavior. Protect completed criteria unless evidence invalidates them. Documentation changes do not substitute for implementation. The final phase succeeds only after plan-wide Definition of Done and plan-wide audit pass for both support lines and candidates.

Guardrails and authority:
The saved docs/plan/goal.md is immutable and create-once. Never refresh, rewrite, rebind, overwrite, or replace the saved goal. Never invoke Plan Creator or Goal Creator. Never stop solely for plan or handoff digest drift. Inspect and classify current plan changes, then reread the current authoritative plan set. Routine progress, evidence, status clarification, and phase transitions continue without owner input. Route only material product-contract change through PLAN_REVISION_REQUIRED with affected stable IDs and the owner decision.

DEC-001 through DEC-007 are locked. FUT-001 through FUT-005, publication, announcements, and public support replies are excluded. External blockers: none. EXT-005 Authenticated EnVisione GitHub access is authorized through the approved credential mechanism. Never print, echo, log, commit, serialize, cache, or put credentials in a ledger, fixture, report, command output, or unapproved credential store. Reject secret-bearing files and preserve unrelated untracked files.

Use the local 64 GB workstation for Forge and NeoForge server and multiple clients; node1 with 96 GB is optional. Reporter artifacts and hardware are not blockers.

Never weaken, skip, disable, delete, or narrow valid tests. Never suppress a valid failure, ignore a required exit code, reduce a required threshold, or allow a required check to fail. Never add a production bypass solely for tests. Never substitute mocked behavior for required real behavior. If a test contradicts the plan, prove it; replace an invalid test with equal or stronger coverage.

Verification and stopping:
Run regressions, full tests, runData, GameTests, builds, server and client smoke tests, multiple-client failures and reconnect, persistence and recovery fixtures, documentation checks, JAR inspection, checksums, and GitHub checks. Evidence is valid only for exact code, configuration, artifacts, and environment; rerun invalidated gates. Do not rerun the same unchanged failing check more than twice without changing code, configuration, environment, instrumentation, or the diagnostic hypothesis.

After integration, check out or otherwise inspect the exact authoritative merged default-branch commit. Rerun every verification gate affected by merge resolution, generated release state, or default-branch configuration; do not rely only on pre-merge implementation-branch evidence. Finish with git status, git diff --check, git log, artifact-to-commit binding, authoritative remote branch confirmation, issue reconciliation, and two unchanged-revision final audits.

Permitted terminal states:
SUCCESS. The endpoint passed every mandatory requirement, stable-release gate, support-line merge, artifact, runtime, and final audit.
PLAN_REVISION_REQUIRED. A material contract change needs an owner choice; report affected stable IDs and the owner decision.
GOAL_REVISION_CONFLICT. Saved goal changed; report expected and observed goal digest.
OWNER_INPUT_REQUIRED — REPOSITORY MISMATCH. Plan, repository, package, or remote does not prove the same project.
REPOSITORY_STATE_CONFLICT. Default-branch history is ahead or diverged beyond authority.
Before returning either repository state, exhaust safe non-destructive checks of repository metadata and remote evidence. Plan or handoff digest drift alone is not a stopping or terminal state. No other early stopping state is permitted.

Continuity:
Requirement map and ledger are temporary internal continuity state only; do not commit or publish them to plan.md, status.md, issues, pull requests, or repository documentation. Record the active phase ID and file, completed phase gates, and next contiguous phase or task. Never refresh the goal.

After compaction or resumption, recover by rereading the immutable goal, current authoritative plan set, active phase, repository state, and continuity record. Isolate root cause, then rerun narrow and invalidated gates. Apply anti-loop behavior: do not repeat unchanged inspection, checks, or queries; change inputs or hypothesis first and keep retries bounded. Repository-owned failures remain mandatory work, not external blockers.
