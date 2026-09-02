# Phase 000 issue snapshots

Captured 2026-09-01 during P000-TASK-004 with `gh issue view` from the authenticated `EnVisione` account. Bodies and comments were read only. The issue URLs below are the canonical sources. No private logs or credentials were copied into this evidence packet.

## Issue 22

Title: `bug report futureshops blur gui on neoforge 1.21.1`

State: open. Labels: `bug`, `in progress`, `priority medium`, `neoforge`. Assignee: `EnVisione`. Updated: 2026-08-08. URL: https://github.com/MCEnvision/FutureShops/issues/22

The body records the 2.2.0 Neoforge 1.21.1 blur reproduction and three Discord source links. It identifies a Windows 11 client without shaders, resource packs, or UI mods. The expected state is a clear readable shop GUI. The final comment records root cause and commit `bfba91f7b0c51b03d07117c4f1851c38a98f6186`, with a required affected Windows confirmation still outstanding.

Comments, in order:

1. `EnVisione`, 2026-08-02T23:26:32Z, checking the issue.
2. `EnVisione`, 2026-08-02T23:30:51Z, fix not yet verified.
3. `EnVisione`, 2026-08-08T19:25:31Z, root cause, fix branch, test results, and artifact checksum.

## Issue 25

Title: `server shop offers remain unavailable after updating`

State: open. Labels: `bug`, `in progress`, `priority high`, `forge`. Assignee: `EnVisione`. Updated: 2026-08-10. URL: https://github.com/MCEnvision/FutureShops/issues/25

The body records a Forge 1.20.1 single player update regression where buy and sell readiness remains unavailable. Acceptance requires a compatible world regression, a lifecycle or migration fix without bypassing escrow readiness, focused tests, build, dedicated server, client, reconnect, and world reopen verification. The last report says adding gunpowder as buy and sell still fails.

Comments, in order:

1. `EnVisione`, 2026-08-08T17:54:45Z, checking the issue.
2. `EnVisione`, 2026-08-08T17:59:44Z, fix not yet verified.
3. `EnVisione`, 2026-08-08T18:34:03Z, compatible world stock migration fix at commit `404c011`, tests, and beta artifact checksum.
4. `EnVisione`, 2026-08-08T18:39:51Z, Discord source link.
5. `EnVisione`, 2026-08-10T14:51:37Z, message delivery problem.
6. `EnVisione`, 2026-08-10T14:51:41Z, message delivery restored.
7. `EnVisione`, 2026-08-10T14:53:10Z, gunpowder buy and sell remain unavailable.

## Issue 32

Title: `player data becomes unusable and must be deleted from the server`

State: open. Labels: `bug`, `needs triage`, `priority high`, `forge`. No assignee. Updated: 2026-08-25. URL: https://github.com/MCEnvision/FutureShops/issues/32

The body records two reports on Minecraft 1.20.1 and FutureShops 3.0.0 where server player data became unusable. It requires preserving world, player data, FutureShops state, configs, and logs, identifying the preceding action, and proving that failed transactions and recovery do not corrupt unrelated modded data. No exact artifact, Forge version, action, logs, or preserved player file has been supplied.

Comments, in order:

1. `EnVisione`, 2026-08-25T18:40:28Z, checking the issue.
2. `EnVisione`, 2026-08-25T18:41:04Z, fix not yet verified.

## Issue 33

Title: `add bulk shop entries with shared price stock and nbt matching`

State: open. Labels: `enhancement`, `needs triage`. No assignee. Updated: 2026-09-01. URL: https://github.com/MCEnvision/FutureShops/issues/33

The body requests bounded NBT matching, a complete preview, one atomic confirmation, deterministic conflict handling, and exact persistence through reload and multiplayer. Matching semantics and conflict policy remain product questions. No implementation or reproduction comments have been added.

Comments, in order:

1. `EnVisione`, 2026-09-01T21:54:31Z, checking the issue.
2. `EnVisione`, 2026-09-01T21:55:12Z, fix not yet verified.

## Issue 34

Title: `finite stock money purchases fail while infinite stock succeeds`

State: open. Labels: `bug`, `needs triage`, `priority high`, `forge`. No assignee. Updated: 2026-09-01. URL: https://github.com/MCEnvision/FutureShops/issues/34

The body records a Forge 1.20.1 multiplayer beta 1 report where finite stock money purchases fail while infinite stock purchases succeed, with no actionable server or client diagnostic. Acceptance requires finite stock reservation, payment, delivery, decrement, rollback, persistence, reconnect, restart, and multiplayer verification. Exact item, price, stock, economy provider, and logs remain missing.

Comments, in order:

1. `EnVisione`, 2026-09-01T21:55:13Z, checking the issue.
2. `EnVisione`, 2026-09-01T21:55:57Z, fix not yet verified.
