# Phase 000 readiness and preservation register

Observation date: 2026-09-24
Phase: CORE-PHASE-000
Task: P000-TASK-001
Requirement: CORE-REQ-001

## Project and revision identity

The working repository is `/mnt/hermes/projects/FutureShops` and its authenticated remote is `origin`, `https://github.com/MCEnvision/FutureShops.git`. The Forge integration base is branch `1.20.1/3.0.0-beta.2` at commit `78fad4069d778996c24ecf5acc5cbe0e1edea7a2`. The local checkout, local remote tracking ref, and live remote head all matched this commit before the phase branch was created.

The read only NeoForge donor is branch `1.21.1/2.4.1` at commit `cc2d69425beea2c93a4000e44d49381deba8eed2`. The new local phase branch is `envy/3.0.0-beta.3-phase-000`; its merge base is the exact Forge integration base and no remote branch with that name existed at observation time.

Package identity is FutureShops, mod id `futureshops`, Minecraft `1.20.1`, Forge `47.4.20`, Java `17`, and current source version `3.0.0-beta.2`. The pinned identity files were recorded as follows.

| File | SHA-256 |
| --- | --- |
| `gradle.properties` | `00845e2fbaa889f28c7e9fa8f6f53561e96752bb71e33dab7b797a1cd8601cb3` |
| `settings.gradle` | `8e11d8277be1628f651e936b7e73b585a1df29ff2b3922db1577f2bf2ffcc1b9` |
| `build.gradle` | `7fc6a174087a51f97cf769b114b4470a128a9cd96ec8493b9f52098e4829d73b` |

The configured signing identity is the registered EnVisione ED25519 key with fingerprint `SHA256:CE014W2Y8QMbKKspTiTQAJ37gK83TV3gup2el94DWb4`.

## Preserved pre-existing dirty state

The following 19 tracked entries existed before phase readiness evidence was created. They are user-owned cleanup and documentation changes, not Phase 000 implementation. They remain unmodified and unstaged.

```text
M DOCUMENTATION.md
D FutureShops3-0Plan.MD
D FutureShops3-1TradeOffersPlan.MD
M README.md
M docs/README.md
M docs/community_suggestions.md
M docs/plan/goal.md
M docs/redesign-nocturne-spec.md
D phases/plan-phase-000.md
D phases/plan-phase-001.md
D phases/plan-phase-002.md
D phases/plan-phase-003.md
D phases/plan-phase-004.md
D phases/plan-phase-005.md
D phases/plan-phase-006.md
D phases/plan-phase-007.md
D plan.handoff.json
D plan.index.json
D plan.md
```

The current authoritative plan set under `docs/general/` and the protected cursor under `docs/plan/active_phase.md` are preserved planning inputs. They are not counted as Phase 000 evidence ownership. No source, provider, mixin, configuration, resource, fixture, client, server, or donor file was changed by this task.

## GitHub intake

The matching phase milestone was created as milestone 10, `3.0.0-beta.3 phase 000 readiness`. The active branch protection rulesets are `mcenvision main protection` and `mcenvision release tag protection`. The default branch has no individual branch protection record, so the plan's required checks and merge procedure remain mandatory execution gates.

Issue 66, `add external economy providers to the 3.0.0 line`, remains open with labels `enhancement`, `ready`, `forge`, and `neoforge`; its current observation is 2026-09-10. Issue 9, `audit transitive dependency security alerts`, remains open with labels `dependencies`, `security`, and `maintenance`; its current observation is 2026-09-04. Neither issue is closed by readiness discovery.

Open Dependabot alerts at intake are 26, 27, 28, and 29. The other observed alerts are dismissed historical records and require the later reachability audit. The only open pull request is unrelated PR 78 from `envy/2.5.0-dynamic-pricing` into `1.21.1/2.4.1`; no applicable Forge pull request exists.

## Runtime and cleanup boundary

No Minecraft client, dedicated server, GameTest, build, runtime, watcher, or audio stream was launched during P000-TASK-001. Runtime discovery remains a later task. The authorized headless node and laptop client controls are prerequisites for later evidence, not acceptance evidence here. This report created one tracked evidence file only; no disposable resources or processes require teardown from this task.

## Readiness disposition

Identity, baseline ancestry, dirty-state preservation, donor pin, current issue intake, active ruleset names, alert intake, phase milestone, and sequential branch prerequisites are recorded. The phase remains open until P000-TASK-002 through P000-TASK-005 complete the exhaustive semantic ledger, route inventory, exact artifact intake, host discovery, validation, and common integration gate.
