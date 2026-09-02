# Phase 000 deterministic harness contract

Captured 2026-09-01 for P000-TASK-010. This is an executable evidence contract for the remaining phases. It does not claim that downstream product repairs have passed.

## Common evidence fields

Every run records the support line, exact source revision, Java runtime, wrapper and Gradle version, fixture origin and SHA256 digest, isolated worktree and run directory, required mods and configuration, start and end time, command, exit status, readiness or test sentinel, failure sentinel, timeout, shutdown method, captured logs, state snapshots, sanitation result, and rerun trigger. Any value movement also records the request UUID, balance, inventory, stock, escrow, claims, and conservation equation before and after the action.

Synthetic fixtures are generated from a known seed and never use the only world or player data copy. Logs and snapshots are sanitized before public issue synchronization. Evidence is retained outside the plan set and linked by stable commit, issue, check, artifact, or digest. A timeout, missing output, skipped test, or unclassified fatal log is a failure, not a pass.

## Required harness families

| family | support line | action and sentinel | failure and rerun trigger |
| --- | --- | --- | --- |
| plan and repository audit | both | validate plan set, refs, ancestry, issue state, and JAR metadata; sentinel is matching hashes and complete inventory | changed ref, plan, issue, or dirty state reruns provenance and affected packets |
| Forge unit and GameTest | Forge 1.20.1 | `bash ./gradlew test`, `runData`, `runGameTestServer`, and `build`; sentinel is all tests and all five required GameTests passing | compile, test, generated drift, or package failure follows duplicate gate and reruns exact ref |
| NeoForge unit and GameTest | NeoForge 1.21.1 | confirmed ModDevGradle task inventory, unit tests, data task, GameTest task, and build; sentinel is at least one discovered GameTest with an explicit result | zero discovered functions is issue 35; any ref or task graph change reruns the baseline |
| server and client startup | each line | isolated server reaches `Done` and client reaches mod setup, sound engine, and texture initialization under xvfb; shutdown is bounded and evidence preserving | EULA, port, display, resource, or crash failure is classified and rerun only after its exact remediation |
| issue 22 screen lifecycle | NeoForge 1.21.1 | run the candidate regression against the exact screen set; sentinel is sharp custom backdrop and readable controls after background pass | changed candidate ancestry or screen path reruns candidate inventory, tests, client smoke, and pull request checks |
| issue 25 upgrade matrix | Forge 1.20.1 | load valid, migrated, malformed, missing registry, restart, reload, and intermediate beta fixtures; sentinel is ready authoritative catalog or explicit safe disposition | first divergence or changed catalog digest reruns the affected row and migration proof |
| issue 32 corruption and recovery | Forge 1.20.1 | seed malformed, truncated, oversized, old, newer, unknown, duplicate, cross mod, partial write, and crash cut state; sentinel is owned field recovery or durable claim with unchanged sentinels | unexpected checksum, ownership, or conservation change stops mutation and restores one complete matching snapshot |
| issue 34 finite stock multiplayer | Forge 1.20.1 | isolated dedicated server with two clients covers finite and infinite stock, funds, concurrency, inventory, provider failure, retry, disconnect, restart, and reconnect; sentinel is one transaction and one stock decrement | payment, delivery, rollback, or diagnostic mismatch reruns the smallest failing case with same request identity |
| issue 33 bulk selection | Forge 1.20.1 | bounded NBT selection, exact preview, price and stock apply, skip, replace, cancel, and atomic failure; sentinel is expected catalog digest | duplicate, invalid NBT, oversized result, stale state, or partial write reruns from the fixture digest |
| security and command audit | both | enumerate commands, packet routes, permissions, path validation, deserialization, escrow readiness, and admin operations; sentinel is authoritative denial or conserved mutation | new attack surface or unsafe path enters the rolling issue gate before repair |

## Isolation and shutdown

Forge baseline uses `/tmp/futureshops-forge-baseline.4PlFvM` with Gradle home `/tmp/futureshops-gradle-forge-baseline`. NeoForge baseline uses `/tmp/futureshops-neoforge-baseline.0nMvlX` with Gradle home `/tmp/futureshops-gradle-neoforge-baseline`. Candidate work uses its own worktree and Gradle home. Dedicated servers bind isolated ports and are terminated only after their readiness sentinel or a bounded timeout. Existing unrelated processes are not stopped.

The workstation is the default host. The authorized 96 GB node1 temporary server fallback is used only with pinned revision, isolated world, configuration, credentials, logs, and host role. No live economy is touched.

## Invalidation rules

Ref movement invalidates ancestry, metadata, build, runtime, and JAR evidence for that line. A changed issue body, comment, label, pull request, check, ruleset, dependency, or milestone invalidates its synchronized packet. A changed fixture digest invalidates all derived state and result claims. A product repair invalidates earlier passing evidence for its affected boundary. Retain old packets as historical records and rerun from the first affected gate.

## Status

The harness contract is ready for downstream phases. Forge exact baseline evidence is green. NeoForge GameTest evidence remains invalid until issue 35 is repaired or explicitly dispositioned. This contract does not authorize a product repair outside the owning phase.
